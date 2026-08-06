package com.example.personalaibot.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.personalaibot.MainActivity
import com.example.personalaibot.automation.*
import com.example.personalaibot.createHttpClient
import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.tools.trading.TradingApiService
import com.example.personalaibot.tools.trading.SmcApiService
import com.example.personalaibot.tools.trading.AdvancedTradingEngine
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.example.personalaibot.db.JarvisDatabase
import com.example.personalaibot.db.JarvisDatabaseHolder
import com.example.personalaibot.db.AlertJob
import com.example.personalaibot.db.ScheduledTask
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Background automation/monitoring foreground service.
 *
 * IMPORTANT — foregroundServiceType MUST stay `specialUse` (see AndroidManifest).
 * The previous `dataSync` type carried a hard 6h limit on Android 14+ which
 * caused two cascading crashes whenever the user lost connectivity:
 *
 *   1. ForegroundServiceDidNotStopInTimeException — system kills FGS at the limit
 *   2. ForegroundServiceStartNotAllowedException — START_STICKY tries to
 *      restart while the type is still in cooldown, app dies again
 *
 * This rewrite also:
 *   • Catches ForegroundServiceStartNotAllowedException so we never crash from
 *     onStartCommand again, even if the platform changes its rules later.
 *   • Implements Service.onTimeout (API 35+) for graceful self-stop on the rare
 *     types that DO have a limit, instead of letting the framework kill us.
 *   • Stops the service when there are no active jobs/tasks (so we don't burn
 *     battery polling an empty SQLite table forever).
 *   • Backs off polling when the network is offline instead of hammering it
 *     every 60s.
 *   • Closes the HttpClients in onDestroy so OkHttp pools don't leak.
 *
 * 2026-07-30 — AI Wake-up + Scheduled Tasks:
 *   • เมื่อ alert เข้าเงื่อนไข ระบบ "ปลุก AI" (Gemini) มาสรุปบริบทแล้วค่อยแจ้งเตือน
 *     (ตั้งค่าได้: alert_ai_summary, alert_voice ใน AppSetting)
 *   • รองรับ ScheduledTask (one_time / daily) — ถึงเวลาแล้วปลุก AI มาทำตาม prompt
 *   • ใช้ DB ไฟล์เดียวกับแอปหลัก ("jarvis.db") เสมอ
 */
class JarvisAutomationService : Service() {

    private val CHANNEL_ID = "JarvisAutomationChannel"
    private val NOTIFICATION_ID = 99
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        /** ปุ่มบน notification ของ alert — "หยุดแจ้งเตือน" / "แจ้งเตือนซ้ำ"
         *  (จัดการโดย AlertActionReceiver ที่ประกาศใน manifest — ทำงานได้แม้ service ถูกฆ่า) */
        const val ACTION_ALERT_STOP = "com.example.personalaibot.action.ALERT_STOP"
        const val ACTION_ALERT_REPEAT = "com.example.personalaibot.action.ALERT_REPEAT"
        const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    private lateinit var database: JarvisDatabase
    private lateinit var automationManager: AutomationManager
    private lateinit var tradingApi: TradingApiService
    private lateinit var smcApi: SmcApiService
    private lateinit var evaluator: AutomationEvaluator
    private lateinit var advancedEngine: AdvancedTradingEngine
    private lateinit var indicatorProvider: IndicatorAlertProvider
    private lateinit var smcAlertProvider: SmcAlertProvider

    /** Client เฉพาะสำหรับปลุก AI (มี HTTP/1.1 fix ของ FRED ผ่าน createHttpClient) */
    private val geminiClient = createHttpClient()

    /** TTS สำหรับโหมดแจ้งเตือนด้วยเสียง (ตั้งค่า alert_voice) */
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false

    /** Cycles in a row that returned ANY network error — used for backoff. */
    private var consecutiveNetworkFailures = 0
    /** Cycles in a row with zero active jobs/tasks — after a few we self-stop. */
    private var emptyCycleCount = 0
    /** Has startForeground succeeded at least once for this Service instance? */
    private var foregroundStarted = false

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        // จำเป็นสำหรับ SmcApiService (TradingView websocket) — ถ้าไม่ install
        // deep_analysis_suite จะ timeout ค้าง ~13 วิ × 3 exchange ทุกครั้งที่เช็ค
        install(io.ktor.client.plugins.websocket.WebSockets) {
            pingIntervalMillis = 20_000
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize DB and Managers
        // สำคัญ: ต้องใช้ DB ไฟล์เดียวกับแอปหลัก (DatabaseDriverFactory = "jarvis.db")
        // เดิมชี้ไป "jarvis_bot.db" ทำให้ alert ที่ AI สร้างไม่เคยถูก background loop เช็ค
        val driver = AndroidSqliteDriver(JarvisDatabase.Schema, applicationContext, "jarvis.db")
        database = JarvisDatabase(driver)
        JarvisDatabaseHolder.install(database)
        automationManager = JarvisDatabaseHolder.getAutomationManager()
        tradingApi = TradingApiService(client)
        smcApi = SmcApiService(client)
        evaluator = AutomationEvaluator()
        advancedEngine = AdvancedTradingEngine(smcApi)
        indicatorProvider = IndicatorAlertProvider(smcApi)
        smcAlertProvider = SmcAlertProvider(smcApi)

        initTts()
        startLoop()
    }

    private fun initTts() {
        try {
            tts = android.speech.tts.TextToSpeech(applicationContext) { status ->
                ttsReady = status == android.speech.tts.TextToSpeech.SUCCESS
                if (ttsReady) {
                    val avail = tts?.setLanguage(java.util.Locale("th", "TH"))
                    if (avail == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
                        avail == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        tts?.language = java.util.Locale.getDefault()
                    }
                }
            }
        } catch (e: Exception) {
            logError("AutomationService", "TTS init failed: ${e.message}", e)
        }
    }

    private fun startLoop() {
        scope.launch {
            while (isActive) {
                val nextDelayMs = try {
                    runOneCycle()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logError("AutomationService", "Loop error: ${e.message}", e)
                    consecutiveNetworkFailures++
                    backoffDelayMs()
                }
                delay(nextDelayMs)
            }
        }
    }

    /** Runs one polling cycle and returns the suggested delay until the next. */
    private suspend fun runOneCycle(): Long {
        val jobs = database.jarvisDatabaseQueries.getAllActiveJobs().executeAsList()
        val tasks = database.jarvisDatabaseQueries.getAllActiveScheduledTasks().executeAsList()

        // No active jobs/tasks → don't keep a foreground notification alive forever.
        // Stop after 5 consecutive empty cycles so we don't immediately re-start
        // if the user is rapidly toggling alerts.
        if (jobs.isEmpty() && tasks.isEmpty()) {
            emptyCycleCount++
            if (emptyCycleCount >= 5) {
                logDebug("AutomationService", "No active jobs/tasks for $emptyCycleCount cycles — stopping self")
                stopSelf()
            }
            return 60_000L
        }
        emptyCycleCount = 0

        val now = Clock.System.now().toEpochMilliseconds()
        var anyNetworkErr = false
        for (job in jobs) {
            val intervalMillis = effectiveIntervalMs(job)
            if (now - job.last_run_at >= intervalMillis) {
                try {
                    checkJob(job)
                } catch (e: java.io.IOException) {
                    // Connection refused / unreachable — server offline. Mark
                    // for backoff but DON'T let one bad job take down the loop.
                    anyNetworkErr = true
                    logDebug("AutomationService", "Job ${job.name} network error: ${e.message}")
                } catch (e: Exception) {
                    logError("AutomationService", "Job ${job.name} failed: ${e.message}", e)
                }
            }
        }

        // ─── Scheduled Tasks (one_time / daily — ปลุก AI เมื่อถึงเวลา) ───
        processDueTasks(tasks, now)

        if (anyNetworkErr) {
            consecutiveNetworkFailures++
            return backoffDelayMs()
        }
        consecutiveNetworkFailures = 0
        // tick หลัก 30 วินาที — อ่าน SQLite อย่างเดียว ถูกมาก
        // แต่เปิดทางให้ job ที่ "ใกล้เป้า" เช็คถี่ระดับ 30 วิได้ (ดู effectiveIntervalMs)
        return 30_000L
    }

    /**
     * Adaptive Interval — ปรับความถี่เช็คตาม "ระยะห่างจากเป้า" (เฉพาะเงื่อนไขตัวเลข)
     *
     * เหตุผล: ทองวิ่งแรง การตั้ง 1 นาทีตลอดอาจไม่ทันจังหวะ แต่ยิง 30 วิตลอดวันเปลือง
     * จึงใช้ interval ที่ผู้ใช้ตั้งเป็นฐาน แล้ว "เร่ง" ให้ถี่ขึ้นเมื่อราคาเข้าใกล้เป้า:
     *
     *   ห่างเป้า < 0.1%  → เช็คทุก 30 วินาที (โซนเฝ้าระวังสูงสุด)
     *   ห่างเป้า < 0.5%  → เช็คทุก ≤ 1 นาที
     *   ไกลกว่านั้น     → ใช้ interval ที่ผู้ใช้ตั้งไว้ตามปกติ
     *
     * ไม่เคยช้ากว่าที่ผู้ใช้ตั้ง (safe by design)
     */
    private fun effectiveIntervalMs(job: AlertJob): Long {
        val baseMs = job.interval_minutes * 60_000L
        try {
            val condition = automationJson.decodeFromString(AutomationCondition.serializer(), job.condition_json)
            // เฉพาะตัวเลขเท่านั้น (EQ/CONTAINS ไม่มี notion ของระยะห่าง)
            if (condition.operator != ConditionOperator.GT && condition.operator != ConditionOperator.LT &&
                condition.operator != ConditionOperator.GTE && condition.operator != ConditionOperator.LTE
            ) return baseMs

            val target = condition.value.replace(",", "").replace("%", "").trim().toDoubleOrNull() ?: return baseMs
            val last = job.last_value?.replace(",", "")?.replace("%", "")?.trim()?.toDoubleOrNull() ?: return baseMs
            if (target == 0.0) return baseMs

            val distancePct = kotlin.math.abs(last - target) / kotlin.math.abs(target)
            return when {
                distancePct < 0.001 -> minOf(baseMs, 30_000L)   // ใกล้มาก → 30 วิ
                distancePct < 0.005 -> minOf(baseMs, 60_000L)   // ใกล้ → ≤ 1 นาที
                else -> baseMs
            }
        } catch (_: Exception) {
            return baseMs
        }
    }

    /** 60s → 2m → 4m → 8m → … capped at 15m. Resets on first success. */
    private fun backoffDelayMs(): Long {
        val pow = consecutiveNetworkFailures.coerceIn(0, 8)
        val ms = 60_000L * (1L shl pow)
        return ms.coerceAtMost(15 * 60_000L)
    }

    // ─── Alert Jobs (เงื่อนไข) ───────────────────────────────────────────────

    /**
     * ดึง TA พร้อม fallback หลาย exchange — ทดสอบจริงพบว่า TradingView scanner
     * ไม่มีบาง symbol ใน TVC (เช่น TVC:XAUUSD → HTTP 404) แต่มีใน OANDA/FX_IDC
     * (เหมือนที่ getBestEffortPrice ทำสำหรับราคา)
     */
    private suspend fun fetchTechnicalAnalysisWithFallback(job: AlertJob): Map<String, String> {
        // รองรับเลือก TF ด้วย suffix เช่น XAUUSD@15m (default 1h)
        val (baseSymbol, tf) = com.example.personalaibot.automation.IndicatorAlertProvider.splitSymbolAndTf(job.symbol)
        val candidates = when {
            job.exchange != null -> listOf(job.exchange!!)
            else -> {
                val s = baseSymbol.removeSuffix("=X")
                when {
                    s == "XAUUSD" || s == "XAGUSD" || s == "GOLD" || s == "SILVER" ->
                        listOf("OANDA", "FX_IDC", "TVC")
                    s.length == 6 && s.all { it.isLetter() } && !s.endsWith("USDT") ->
                        listOf("OANDA", "FX_IDC")
                    else -> listOf(tradingApi.resolveExchange(baseSymbol, null))
                }
            }
        }
        var lastResult: Map<String, String> = mapOf("error" to "no data")
        for (ex in candidates) {
            lastResult = tradingApi.getTechnicalAnalysis(baseSymbol, ex, tf)
            if (!lastResult.containsKey("error") && lastResult["close"] != "N/A") {
                logDebug("AutomationService", "TA fallback OK: $baseSymbol@$tf via $ex")
                return lastResult
            }
        }
        return lastResult
    }

    private suspend fun checkJob(job: AlertJob) {
        logDebug("AutomationService", "Checking job: ${job.name} for ${job.symbol}")

        // Decode condition first (need field name for logging regardless of fetch result)
        val condition = try {
            automationJson.decodeFromString(AutomationCondition.serializer(), job.condition_json)
        } catch (e: Exception) {
            logError("AutomationService", "Job ${job.name}: invalid condition_json: ${job.condition_json}")
            return
        }

        // 1. Fetch data based on tool_name
        val data = when (job.tool_name) {
            "trading_price" -> tradingApi.getBestEffortPrice(job.symbol)
            "trading_indicators" -> indicatorProvider.fetch(job.symbol)
            "trading_smc" -> smcAlertProvider.fetch(job.symbol)
            "trading_technical_analysis" -> fetchTechnicalAnalysisWithFallback(job)
            "trading_sentiment" -> tradingApi.getRedditSentiment(job.symbol).mapValues { it.value.toString() }
            "trading_fear_greed" -> tradingApi.getFearGreedIndex(1)
            "trading_crypto_overview" -> tradingApi.getCryptoGlobal()
            "trading_deep_analysis_suite" -> {
                // รองรับเลือก TF ด้วย suffix เช่น XAUUSD@15m (default 1h) เหมือน indicators/smc
                val (sym, tf) = com.example.personalaibot.automation.IndicatorAlertProvider.splitSymbolAndTf(job.symbol)
                val result = advancedEngine.analyze(sym, tf)
                if (result == null) emptyMap<String, String>()
                else mapOf(
                    "summaryScore" to result.summaryScore.toString(),
                    "lsdState" to result.lsdTrend.state,
                    "lsdConfluenceTF" to result.lsdTrend.confluenceTF.toString(),
                    "deltaLabel" to result.orderflow.deltaLabel,
                    "deltaValue" to result.orderflow.lastDelta.toString(),
                    "fiboScore" to (result.fiboStrength.maxOfOrNull { it.score }?.toString() ?: "0"),
                    "momentum" to result.momentum.signal,
                    "isSqueeze" to (if (result.momentum.isSqueeze) "1" else "0"),
                    "close" to result.currentPrice.toString()
                )
            }
            else -> {
                logDebug("AutomationService", "Job ${job.name}: tool '${job.tool_name}' ไม่รองรับใน background — ข้าม")
                emptyMap()
            }
        }

        val lastSample = data[condition.field]

        if (data.isEmpty() || data.containsKey("error")) {
            val errMsg = data["error"] ?: "empty"
            logDebug("AutomationService", "Job ${job.name} → ${condition.field}=ERR ($errMsg) | fetch failed")
            automationManager.recordJobCheckResult(job.id, "ERR($errMsg)")
            return
        }

        // Log ค่าที่ดึงมาได้ทุกครั้ง (ไม่ว่าจะ trigger หรือไม่)
        val sampleStr = lastSample ?: "N/A"
        logDebug("AutomationService", "Job ${job.name} → ${condition.field}=$sampleStr | condition=${condition.operator} ${condition.value} | met=${evaluator.evaluate(data, condition)}")
        automationManager.recordJobCheckResult(job.id, sampleStr)

        // --- Auto High Confluence Alert ---
        if (job.tool_name == "trading_deep_analysis_suite") {
             val score = data["summaryScore"]?.toDoubleOrNull() ?: 0.0
             if (score >= 85.0 && job.is_triggered == 0L) {
                 // แยกไปทำขนาน — ไม่บล็อก loop (AI call ใช้เวลา ~10 วิ)
                 scope.launch { fireJobAlert(job, "🌟 High Confluence ($score)", data) }
             }
        }

        // 2. Evaluate condition
        val isMet = evaluator.evaluate(data, condition)

        if (isMet) {
            if (job.is_triggered == 0L) {
                // Flipped to TRUE -> แจ้งเตือน!
                // mark ก่อนเสมอ (synchronous) กัน tick ถัดไปยิงซ้ำ แล้วค่อยปลุก AI แบบขนาน
                // — เดิมเรียก Gemini แบบ serial ใน loop ทำให้ job ถัดไปช้าไป ~10 วินาที
                automationManager.markTriggered(job.id, sampleStr)
                scope.launch {
                    try {
                        fireJobAlert(job, sampleStr, data)
                    } catch (e: Exception) {
                        logError("AutomationService", "fireJobAlert ${job.name} failed: ${e.message}", e)
                    }
                }
            }
        } else {
            if (job.is_triggered == 1L) {
                // Flipped to FALSE -> Reset so we can notify again later
                automationManager.resetTrigger(job.id)
            }
        }
    }


    // ─── Scheduled Tasks (ตามเวลา) ───────────────────────────────────────────

    private suspend fun processDueTasks(tasks: List<ScheduledTask>, nowMs: Long) {
        if (tasks.isEmpty()) return
        val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = local.date.toString()
        for (t in tasks) {
            try {
                when (t.schedule_type) {
                    "daily" -> {
                        val parts = t.time_hhmm?.split(":") ?: continue
                        val hh = parts.getOrNull(0)?.toIntOrNull() ?: continue
                        val mm = parts.getOrNull(1)?.toIntOrNull() ?: continue
                        val dueReached = local.hour > hh || (local.hour == hh && local.minute >= mm)
                        if (t.last_fired_date != today && dueReached) {
                            logDebug("AutomationService", "Firing daily task: ${t.name}")
                            fireScheduledTask(t)
                            automationManager.markScheduledTaskFired(t.id, today)
                        }
                    }
                    else -> { // one_time
                        if (t.run_at in 1..nowMs) {
                            logDebug("AutomationService", "Firing one-time task: ${t.name}")
                            fireScheduledTask(t)
                            automationManager.deactivateScheduledTask(t.id)
                        }
                    }
                }
            } catch (e: Exception) {
                logError("AutomationService", "Task ${t.name} failed: ${e.message}", e)
            }
        }
    }

    private suspend fun fireScheduledTask(task: ScheduledTask) {
        val aiText = generateAiText(task.prompt)
        val body = aiText ?: "ถึงเวลาแล้ว: ${task.prompt}"
        sendNotification("⏰ Jarvis: ${task.name}", body, (task.id + 100_000).toInt())
        if (settingEnabled("alert_voice", false)) speak(body)
    }

    // ─── AI Wake-up — ปลุก AI มาสรุปบริบทก่อนแจ้งเตือนผู้ใช้ ────────────────

    private suspend fun fireJobAlert(job: AlertJob, value: String, data: Map<String, String>) {
        val contextPrompt = buildString {
            appendLine("เหตุการณ์: การแจ้งเตือน '${job.name}' ของ ${job.symbol} เข้าเงื่อนไขแล้ว")
            appendLine("เงื่อนไขที่ตั้งไว้: ${job.condition_json}")
            appendLine("ค่าปัจจุบัน: $value")
            appendLine("ข้อมูลดิบ: ${data.entries.take(8).joinToString { "${it.key}=${it.value}" }}")
            appendLine()
            appendLine("ช่วยสรุปแจ้งผู้ใช้แบบสั้น 2-3 ประโยค ภาษาไทย ว่าเกิดอะไรขึ้น และมีข้อแนะนำสั้นๆ (ถ้าเหมาะสม)")
        }
        val aiText = if (settingEnabled("alert_ai_summary", true)) generateAiText(contextPrompt) else null
        val body = aiText ?: "${job.symbol} เข้าเงื่อนไขแล้ว! ค่าปัจจุบัน: $value"
        sendJobAlertNotification(job, body)
        if (settingEnabled("alert_voice", false)) speak(body)
    }

    /**
     * Notification ของ alert แบบมีปุ่มกดได้ 2 ปุ่ม (ทำหน้าที่เหมือน msg box):
     *   🛑 หยุดแจ้งเตือน  — ปิด job นี้ (is_active = 0)
     *   🔁 แจ้งเตือนซ้ำ   — รีเซ็ตสถานะ ให้ระบบเฝ้าดูและแจ้งใหม่เมื่อเข้าเงื่อนไขอีกครั้ง
     * (Android ไม่อนุญาตให้ background service เปิด dialog ลอยได้จริง
     *  ปุ่มบน notification คือทางที่ถูกต้องตาม platform)
     */
    private fun sendJobAlertNotification(job: AlertJob, body: String) {
        val notificationId = job.id.toInt()

        fun actionIntent(action: String): PendingIntent {
            // explicit intent ไปยัง AlertActionReceiver (manifest-declared)
            // — ทำงานได้แม้ service/แอปถูกฆ่า ไม่เหมือน dynamic receiver
            val intent = Intent(this, AlertActionReceiver::class.java).apply {
                setAction(action)
                putExtra(EXTRA_JOB_ID, job.id)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                this, (job.id * 10 + if (action == ACTION_ALERT_STOP) 1 else 2).toInt(),
                intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎯 Jarvis Alert: ${job.name}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🛑 หยุดแจ้งเตือน", actionIntent(ACTION_ALERT_STOP))
            .addAction(android.R.drawable.ic_menu_rotate, "🔁 แจ้งเตือนซ้ำ", actionIntent(ACTION_ALERT_REPEAT))
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    /** เรียก Gemini ด้วย key/model จาก AppSetting — คืน null ถ้าไม่มี key หรือเรียกไม่สำเร็จ */
    private suspend fun generateAiText(prompt: String): String? {
        val apiKey = setting("api_key")
        if (apiKey.isBlank()) {
            logDebug("AutomationService", "AI wake-up skipped — no api_key in settings")
            return null
        }
        val model = setting("model_name").ifBlank { "gemini-2.0-flash" }
        return try {
            GeminiService(geminiClient, apiKey, model).generateResponse(
                prompt = prompt,
                intentAddon = "คุณคือ JARVIS ผู้ช่วยส่วนตัว พูดสั้น กระชับ สุภาพ เป็นมิตร ใช้ภาษาไทยเป็นหลัก " +
                        "ตอบเป็นข้อความธรรมดาเท่านั้น ห้ามใช้ markdown ห้ามใส่หัวข้อหรือตาราง"
            ).trim().takeIf { it.isNotBlank() && !it.startsWith("⚠️") }
        } catch (e: Exception) {
            logError("AutomationService", "AI wake-up failed: ${e.message}", e)
            null
        }
    }

    // ─── Settings helpers (อ่านจาก AppSetting ใน DB เดียวกับแอป) ─────────────

    private fun setting(key: String): String =
        try {
            database.jarvisDatabaseQueries.getSetting(key).executeAsOneOrNull() ?: ""
        } catch (_: Exception) { "" }

    private fun settingEnabled(key: String, default: Boolean): Boolean {
        val v = setting(key)
        return if (v.isBlank()) default else v == "true"
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        try {
            tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "jarvis_alert")
        } catch (e: Exception) {
            logError("AutomationService", "TTS speak failed: ${e.message}", e)
        }
    }

    // ─── Notification ────────────────────────────────────────────────────────

    private fun sendNotification(title: String, body: String, notificationId: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-deliveries are normal (START_STICKY). Only build+attach the
        // foreground notification once per Service instance — calling
        // startForeground() repeatedly on a service the platform has put in
        // FGS cooldown is what threw ForegroundServiceStartNotAllowedException.
        if (!foregroundStarted) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Jarvis Automation Engine")
                .setContentText("เฝ้าติดตามตลาดให้คุณในเบื้องหลัง...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                foregroundStarted = true
            } catch (e: Exception) {
                // Most commonly:
                //   - ForegroundServiceStartNotAllowedException
                //     (cooldown after a previous timeout)
                //   - SecurityException for missing FGS permission
                // Either way we must NOT crash here. Log and stop quietly;
                // the JobScheduler/WorkManager retry path will pick us up
                // later when the platform allows.
                logError("AutomationService", "startForeground denied: ${e.message}", e)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // START_STICKY is fine for normal operation. The cooldown crash was
        // not caused by re-delivery — it was caused by repeating
        // startForeground while the FGS type was blocked. We now guard that
        // above, so START_STICKY is safe again.
        return START_STICKY
    }

    /**
     * API 35+ — called shortly before the framework would force-stop us due
     * to a foreground-service type time limit. Stop ourselves cleanly so the
     * user never sees ForegroundServiceDidNotStopInTimeException.
     *
     * For `specialUse` this is not currently invoked by the framework, but
     * implementing it costs nothing and protects us if the FGS type ever
     * changes back to a time-limited one.
     */
    override fun onTimeout(startId: Int) {
        logDebug("AutomationService", "onTimeout(startId=$startId) — stopping cleanly")
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf(startId)
    }

    /** Overload for FGS-type-aware variant on newer API levels. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        logDebug("AutomationService", "onTimeout(startId=$startId, fgsType=$fgsType) — stopping cleanly")
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf(startId)
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis Automation Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        try { client.close() } catch (_: Exception) {}
        try { geminiClient.close() } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

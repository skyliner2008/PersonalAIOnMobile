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
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json
import com.example.personalaibot.data.LiveSetupMessage
import com.example.personalaibot.data.LiveSetup
import com.example.personalaibot.data.LiveGenerationConfig
import com.example.personalaibot.data.LiveSpeechConfig
import com.example.personalaibot.data.LiveVoiceConfig
import com.example.personalaibot.data.LivePrebuiltVoiceConfig
import com.example.personalaibot.data.LiveSystemInstruction
import com.example.personalaibot.data.LivePart
import com.example.personalaibot.data.LiveClientContentMessage
import com.example.personalaibot.data.LiveContentWrapper
import com.example.personalaibot.data.LiveTurn
import com.example.personalaibot.data.LiveServerMessage
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
    private lateinit var smcFlowProvider: com.example.personalaibot.automation.SmcFlowAlertProvider
    private lateinit var strategySignalProvider: com.example.personalaibot.automation.StrategySignalProvider
    private lateinit var signalAlertProvider: com.example.personalaibot.automation.SignalAlertProvider

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
        smcFlowProvider = com.example.personalaibot.automation.SmcFlowAlertProvider(smcApi)
        strategySignalProvider = com.example.personalaibot.automation.StrategySignalProvider(smcApi)
        signalAlertProvider = com.example.personalaibot.automation.SignalAlertProvider(smcApi)

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

        // ─── Signal Outcome Tracker — เช็กทุก cycle ว่า signal ที่ยิงไป โดน TP/SL หรือยัง ───
        trackSignalOutcomes()

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

    // ─── Signal Outcome Tracker ──────────────────────────────────────────────

    /**
     * ติดตามผล signal ที่ alert ยิงออกไป (outcome=OPEN) — ทุก cycle:
     * ดึงแท่งเทียนของ symbol นั้น (cache ต่อ cycle) แล้วไล่แท่งหลังจุดสัญญาณ
     * ชน SL ก่อน = SL (-1R), ชน TP ก่อน = TP (+RR), แท่งเดียวชนทั้งคู่ถือว่า SL (conservative)
     */
    private suspend fun trackSignalOutcomes() {
        val open = automationManager.getOpenSignalAlerts()
        if (open.isEmpty()) return
        val candleCache = HashMap<String, List<com.example.personalaibot.tools.trading.Candle>>()
        for (rec in open) {
            try {
                val candles = candleCache.getOrPut(rec.symbol) {
                    val (sym, tf) = com.example.personalaibot.automation.IndicatorAlertProvider.splitSymbolAndTf(rec.symbol)
                    runCatching { smcApi.fetchCandlesWithSource(sym, tf, 300).candles }.getOrElse { emptyList() }
                }
                if (candles.isEmpty()) continue
                val isBuy = rec.side == "BUY"
                for (c in candles) {
                    if (c.timestamp <= rec.bar_time) continue // เฉพาะแท่งหลังจุดสัญญาณ
                    val hitSl = if (isBuy) c.low <= rec.sl else c.high >= rec.sl
                    val hitTp = if (isBuy) c.high >= rec.tp else c.low <= rec.tp
                    if (hitSl) {
                        automationManager.closeSignalAlert(rec.id, "SL", c.timestamp, rec.sl, -1.0)
                        break
                    }
                    if (hitTp) {
                        val rr = rec.rr ?: (kotlin.math.abs(rec.tp - rec.entry) / kotlin.math.abs(rec.entry - rec.sl))
                        automationManager.closeSignalAlert(rec.id, "TP", c.timestamp, rec.tp, rr)
                        break
                    }
                }
            } catch (e: Exception) {
                logError("AutomationService", "trackSignalOutcomes #${rec.id} failed: ${e.message}", e)
            }
        }
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
                return fillTaNullsFromLocal(baseSymbol, tf, lastResult)
            }
        }
        // scanner ล้มทุก exchange → ใช้ค่าที่คำนวณเองจากแท่งเทียนแทน (key แบบ scanner)
        val localOnly = fillTaNullsFromLocal(baseSymbol, tf, emptyMap())
        return if (localOnly.isNotEmpty()) {
            logDebug("AutomationService", "TA fallback to local indicators: $baseSymbol@$tf")
            localOnly
        } else lastResult
    }

    /** เติม key ที่ scanner คืน null/N/A ด้วยค่าที่คำนวณเองจากแท่งเทียน (indicatorProvider) */
    private suspend fun fillTaNullsFromLocal(symbol: String, tf: String, data: Map<String, String>): Map<String, String> {
        fun bad(v: String?) = v == null || v == "N/A" || v == "null"
        val local = runCatching { indicatorProvider.fetch("$symbol@$tf") }.getOrNull() ?: return data
        if (local.containsKey("error")) return data
        val out = data.toMutableMap()
        fun fill(key: String, localKey: String) {
            if (bad(out[key])) local[localKey]?.let { out[key] = it }
        }
        fill("close", "close"); fill("RSI", "rsi14"); fill("RSI[1]", "rsi14_prev")
        fill("MACD.macd", "macd"); fill("MACD.signal", "macd_signal"); fill("MACD.hist", "macd_hist")
        fill("Stoch.K", "stoch_k"); fill("Stoch.D", "stoch_d"); fill("CCI20", "cci20"); fill("AO", "ao")
        fill("EMA20", "ema20"); fill("EMA50", "ema50"); fill("EMA200", "ema200")
        fill("BB.upper", "bb_upper"); fill("BB.basis", "bb_basis"); fill("BB.lower", "bb_lower")
        fill("BB.width", "bb_width"); fill("ATR", "atr14")
        fill("ADX", "adx"); fill("ADX+DI", "di_plus"); fill("ADX-DI", "di_minus")
        return out
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
            "trading_smc_flow" -> smcFlowProvider.fetch(job.symbol)
            "trading_strategy_signal" -> strategySignalProvider.fetch(job.symbol)
            "trading_signal_alert" -> signalAlertProvider.fetch(job.symbol)
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
                        fireJobAlert(job, sampleStr, data, condition.delivery)
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
        pushToChat("⏰ **${task.name}**\n\n$body", """{"type":"scheduled_task","task_id":${task.id}}""")
        if (settingEnabled("alert_voice", false)) speakAlert(body)
    }

    // ─── AI Wake-up — ปลุก AI มาสรุปบริบทก่อนแจ้งเตือนผู้ใช้ ────────────────

    /** ส่งการ์ดเข้าแชท + พูดเสียงตามลำดับที่ถูกต้อง —
     *  ถ้าเปิดเสียง: พูดก่อน แล้ว callback onVoiceStart จะ push การ์ดพร้อม footer "เสียง: <engine ที่พูดจริง>"
     *  ถ้าปิดเสียง: push การ์ดทันที (ไม่มี footer) */
    private suspend fun deliverChatAndVoice(
        cardBody: String,
        metaFor: (String?) -> String,
        shortSpeech: String,
        fullSpeech: String
    ) {
        if (settingEnabled("alert_voice", false)) {
            val pushed = java.util.concurrent.atomic.AtomicBoolean(false)
            speakAlert(shortSpeech, fullSpeech) { engineLabel ->
                if (pushed.compareAndSet(false, true)) {
                    scope.launch { pushToChat(cardBody, metaFor(engineLabel)) }
                }
            }
            // กันพลาด: callback ไม่ถูกเรียก (เช่น text ว่าง) → push โดยไม่มี footer
            if (pushed.compareAndSet(false, true)) pushToChat(cardBody, metaFor(null))
        } else {
            pushToChat(cardBody, metaFor(null))
        }
    }

    private suspend fun fireJobAlert(job: AlertJob, value: String, data: Map<String, String>, delivery: String = "ai") {
        val isSignalAlert = job.tool_name == "trading_signal_alert"
        // ── Pipeline trace: ต้นรหัสการแจ้งเตือน — ค่าที่ trigger + config ที่จะใช้ทั้ง chain ──
        logDebug("AutomationService",
            "🔔 FIRE '${job.name}' [${job.tool_name}] symbol=${job.symbol} value=$value | " +
            "mode=$delivery aiSummary=${settingEnabled("alert_ai_summary", true)} " +
            "voice=${settingEnabled("alert_voice", false)}/${setting("alert_voice_engine").ifBlank { "device" }} " +
            "model=${setting("model_name").ifBlank { "gemini-2.0-flash" }}")

        // ── บันทึก signal ลงสถิติ (ทั้ง 2 โหมด) — tracker จะตามเช็ก TP/SL ทุก cycle ──
        if (isSignalAlert) {
            val side = data["signal_side"]
            val entry = data["signal_entry"]?.replace(",", "")?.toDoubleOrNull()
            val sl = data["signal_sl"]?.replace(",", "")?.toDoubleOrNull()
            val tp = data["signal_tp"]?.replace(",", "")?.toDoubleOrNull()
            val barTime = data["signal_bar_time"]?.toLongOrNull() ?: 0L
            if (side != null && entry != null && sl != null && tp != null && barTime > 0) {
                automationManager.recordSignalAlert(
                    jobId = job.id, symbol = job.symbol, side = side,
                    strategy = data["signal_strategy"] ?: "-", reason = data["signal_reason"],
                    entry = entry, sl = sl, tp = tp,
                    rr = data["signal_rr"]?.toDoubleOrNull(), barTime = barTime, delivery = delivery
                )
            }
        }

        // ── โหมดส่งตรง: ไม่เรียก AI (ประหยัดโทเคน) — notification + ส่งเข้าแชทโดยตรง ──
        if (delivery == "direct") {
            val body = if (isSignalAlert) {
                "📡 Signal ${data["signal_side"]} ${job.symbol} (${data["signal_strategy"]})\n" +
                    "เหตุผล: ${data["signal_reason"]}\n" +
                    "Entry: ${data["signal_entry"]} | SL: ${data["signal_sl"]} | TP: ${data["signal_tp"]} | RR 1:${data["signal_rr"]}"
            } else {
                "🎯 ${job.name}: ${job.symbol} เข้าเงื่อนไขแล้ว — ค่าปัจจุบัน: $value"
            }
            sendJobAlertNotification(job, body)
            // การ์ดแชท + เสียง — การ์ดจะมี footer บอก engine เสียงที่พูดจริง (push ตอนเสียงเริ่ม)
            deliverChatAndVoice(
                cardBody = if (isSignalAlert) buildSignalChatCard(job, data, null) else buildAlertChatCard(job, value, data, null),
                metaFor = { v ->
                    if (isSignalAlert) signalChatMeta(job, data, null, "signal_alert_direct", v)
                    else alertChatMeta(job, value, null, "signal_alert_direct", v)
                },
                shortSpeech = if (isSignalAlert) buildSignalSpeech(job, data) else buildAlertSpeech(job, value),
                fullSpeech = body)
            return
        }

        // ── โหมด AI: alert → AI quick-check → ผู้ใช้ ──
        val contextPrompt = if (isSignalAlert) buildString {
            // Signal Alert: payload ครบ (เหตุผล/Entry/SL/TP/context) → AI ทำ quick-check จากข้อมูลที่ให้เท่านั้น
            // (ห้ามวิเคราะห์เชิงลึกเพิ่ม — เป้าหมายคือตอบไวภายใน ~10 วินาที ทันจังหวะเข้าออเดอร์)
            appendLine("เหตุการณ์: ระบบ Signal Alert ตรวจพบสัญญาณเทรดใหม่ของ ${job.symbol}!")
            appendLine("สัญญาณ: ${data["signal_side"]} (กลยุทธ์: ${data["signal_strategy"]})")
            appendLine("เหตุผล/เงื่อนไขที่เกิดสัญญาณ: ${data["signal_reason"]}")
            appendLine("จุดเข้าออเดอร์: ${data["signal_entry"]} | Stop Loss: ${data["signal_sl"]} | Take Profit: ${data["signal_tp"]} (Risk:Reward ≈ 1:${data["signal_rr"]})")
            appendLine("บริบทกราฟโดยรวม: ${data["signal_context"]}")
            appendLine()
            appendLine("ช่วยแจ้งผู้ใช้ภาษาไทยแบบสั้น 3-4 ประโยค: (1) มีสัญญาณอะไรจากกลยุทธ์ไหน เพราะอะไร (2) จุดเข้า/SL/TP (3) quick-check จากบริบทที่ให้เท่านั้น ว่าสอดคล้องกับเทรนด์/โมเมนตัมไหม น่าสนใจหรือควรระวังอะไร — ใช้เฉพาะข้อมูลด้านบน ห้ามสมมติข้อมูลเพิ่ม")
        } else buildString {
            appendLine("เหตุการณ์: การแจ้งเตือน '${job.name}' ของ ${job.symbol} เข้าเงื่อนไขแล้ว")
            appendLine("เงื่อนไขที่ตั้งไว้: ${job.condition_json}")
            appendLine("ค่าปัจจุบัน: $value")
            appendLine("ข้อมูลดิบ: ${data.entries.take(8).joinToString { "${it.key}=${it.value}" }}")
            appendLine()
            appendLine("ช่วยสรุปแจ้งผู้ใช้แบบสั้น 2-3 ประโยค ภาษาไทย ว่าเกิดอะไรขึ้น และมีข้อแนะนำสั้นๆ (ถ้าเหมาะสม)")
        }
        val aiText = if (settingEnabled("alert_ai_summary", true)) {
            generateAiText(contextPrompt)
        } else {
            logDebug("AutomationService", "🧠 AI summary skipped — toggle alert_ai_summary ปิดอยู่")
            null
        }
        val body = aiText ?: if (isSignalAlert) {
            "📡 Signal ${data["signal_side"]} ${job.symbol} @ ${data["signal_entry"]} " +
                "(SL ${data["signal_sl"]} / TP ${data["signal_tp"]}) — ${data["signal_strategy"]}: ${data["signal_reason"]}"
        } else {
            "${job.symbol} เข้าเงื่อนไขแล้ว! ค่าปัจจุบัน: $value"
        }
        sendJobAlertNotification(job, body)
        // การ์ดแชท + เสียง — การ์ดจะมี footer บอก engine เสียงที่พูดจริง (push ตอนเสียงเริ่ม)
        deliverChatAndVoice(
            cardBody = if (isSignalAlert) buildSignalChatCard(job, data, aiText) else buildAlertChatCard(job, value, data, aiText),
            metaFor = { v ->
                if (isSignalAlert) signalChatMeta(job, data, aiText, "signal_alert_ai", v)
                else alertChatMeta(job, value, aiText, "signal_alert_ai", v)
            },
            shortSpeech = if (isSignalAlert) buildSignalSpeech(job, data) else buildAlertSpeech(job, value),
            fullSpeech = body)
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

    /** เรียก Gemini ด้วย key/model จาก AppSetting — คืน null ถ้าไม่มี key หรือเรียกไม่สำเร็จทุกโมเดล
     *  ไล่ fallback chain เหมือนฝั่งแชท: เดิมเรียกโมเดลเดียว → 404 (เช่น gemini-3.1-pro ไม่รองรับ
     *  generateContent) แล้วจบ ทำให้ notification ตกไปใช้ template สั้น (เคสจริงเครื่อง B 2026-08-15) */
    private suspend fun generateAiText(prompt: String): String? {
        val apiKey = setting("api_key")
        if (apiKey.isBlank()) {
            logDebug("AutomationService", "🧠 AI summary skipped — no api_key in settings")
            return null
        }
        val primary = setting("model_name").ifBlank { "gemini-2.0-flash" }
        val models = (listOf(primary) + com.example.personalaibot.data.ModelConfig.GEMINI_FALLBACK_MODELS).distinct()
        logDebug("AutomationService", "🧠 AI summary start — chain=${models.joinToString(" → ")}")
        for (m in models) {
            val t0 = System.currentTimeMillis()
            val text = try {
                GeminiService(geminiClient, apiKey, m).generateResponse(
                    prompt = prompt,
                    intentAddon = "คุณคือ JARVIS ผู้ช่วยส่วนตัว พูดสั้น กระชับ สุภาพ เป็นมิตร ใช้ภาษาไทยเป็นหลัก " +
                            "ตอบเป็นข้อความธรรมดาเท่านั้น ห้ามใช้ markdown ห้ามใส่หัวข้อหรือตาราง ห้ามใส่ code block หรือ chart"
                ).trim().takeIf { it.isNotBlank() && !it.startsWith("⚠️") }
                    ?.let { stripCodeFences(it) }
                    ?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                logError("AutomationService", "🧠 AI summary FAILED model=$m (${System.currentTimeMillis() - t0}ms): ${e.message}", e)
                null
            }
            if (text != null) {
                if (m != primary) {
                    // persist โมเดลที่ใช้ได้จริงกลับลง settings — รอบถัดไปจะไม่ชน 404/429 ซ้ำตัวเดิม
                    logDebug("AutomationService", "🧠 AI summary fallback model: $primary → $m (persist)")
                    runCatching { database.jarvisDatabaseQueries.insertSetting("model_name", m) }
                }
                logDebug("AutomationService", "🧠 AI summary OK model=$m (${System.currentTimeMillis() - t0}ms, ${text.length} chars)")
                return text
            }
            logDebug("AutomationService", "🧠 AI summary model $m failed → try next in fallback chain")
        }
        logError("AutomationService", "🧠 AI summary FAILED on all models (${models.size}) → ใช้ template body แทน", null)
        return null
    }

    /** ตัด code fence (```...```) ที่โมเดลแถมมาโดยไม่ได้สั่ง (เช่น ```chart {...}```) — กันข้อความดิบหลุดไปโชว์ในการ์ด/notification */
    private fun stripCodeFences(text: String): String =
        text.replace(Regex("```[\\s\\S]*?```"), " ")
            .replace("```", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

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

    /** ส่งข้อความเข้าแชท: persist ลง DB + แจ้ง UI ที่เปิดอยู่ผ่าน AlertChatBus (แสดงทันที ไม่ต้องเปิดแอปใหม่)
     *  log ผล bus emit ด้วย — emitted=false หมายถึงแชทไม่ได้เปิดอยู่ (ยัง persist ลง DB ปกติ) */
    private suspend fun pushToChat(body: String, metadata: String) {
        runCatching {
            com.example.personalaibot.memory.JarvisMemoryManager(database)
                .storeMessage("assistant", body, metadata = metadata)
            val emitted = com.example.personalaibot.memory.AlertChatBus.tryEmit("assistant", body, metadata)
            logDebug("AutomationService", "💬 pushToChat kind=${metadata.take(120)}… busEmitted=$emitted bodyLen=${body.length}")
        }.onFailure { logError("AutomationService", "💬 chat insert failed: ${it.message}", it) }
    }

    /** แปลง condition_json เป็นข้อความอ่านง่าย เช่น "price >= 4370" (ใช้ร่วมกันทั้งการ์ดและ metadata) */
    private fun conditionText(job: AlertJob): String = runCatching {
        val c = com.example.personalaibot.automation.automationJson
            .decodeFromString<com.example.personalaibot.automation.AutomationCondition>(job.condition_json)
        val op = when (c.operator) {
            com.example.personalaibot.automation.ConditionOperator.GT -> ">"
            com.example.personalaibot.automation.ConditionOperator.GTE -> ">="
            com.example.personalaibot.automation.ConditionOperator.LT -> "<"
            com.example.personalaibot.automation.ConditionOperator.LTE -> "<="
            com.example.personalaibot.automation.ConditionOperator.EQ -> "=="
            com.example.personalaibot.automation.ConditionOperator.CONTAINS -> "contains"
        }
        "${c.field} $op ${c.value}"
    }.getOrElse { job.condition_json }

    /** metadata โครงสร้างของ signal alert — MessageBubble อ่าน kind="signal" แล้ว render เป็นการ์ด 3D */
    private fun signalChatMeta(job: AlertJob, data: Map<String, String>, aiSummary: String?, type: String, voice: String? = null): String =
        kotlinx.serialization.json.buildJsonObject {
            put("type", type)
            put("kind", "signal")
            put("job_id", job.id)
            put("side", data["signal_side"] ?: "-")
            put("symbol", job.symbol)
            put("strategy", data["signal_strategy"] ?: "-")
            put("entry", data["signal_entry"] ?: "-")
            put("tp", data["signal_tp"] ?: "-")
            put("sl", data["signal_sl"] ?: "-")
            put("rr", data["signal_rr"] ?: "-")
            data["signal_atr"]?.let { put("atr", it) }
            put("reason", data["signal_reason"] ?: "-")
            aiSummary?.takeIf { it.isNotBlank() }?.let { put("summary", it) }
            voice?.let { put("voice", it) }
        }.toString()

    /** metadata โครงสร้างของ alert ทั่วไป (ราคา/indicator/ฯลฯ) — MessageBubble render เป็นการ์ด cyan */
    private fun alertChatMeta(job: AlertJob, value: String, aiSummary: String?, type: String, voice: String? = null): String =
        kotlinx.serialization.json.buildJsonObject {
            put("type", type)
            put("kind", "alert")
            put("job_id", job.id)
            put("name", job.name)
            put("symbol", job.symbol)
            put("condition", conditionText(job))
            put("current", value)
            aiSummary?.takeIf { it.isNotBlank() }?.let { put("summary", it) }
            voice?.let { put("voice", it) }
        }.toString()

    /**
     * การ์ดสัญญาณสำหรับแชท — header BUY🟢/SELL🔴 + ตาราง Entry/TP/SL/RR/ATR + เหตุผล
     * (MessageBubble รองรับ markdown table + **bold** อยู่แล้ว จึงใช้ format นี้ได้ทันที)
     * @param aiSummary ข้อความ quick-check จาก AI (โหมด ai เท่านั้น) — ใส่ต่อท้ายการ์ดถ้ามี
     */
    private fun buildSignalChatCard(job: AlertJob, data: Map<String, String>, aiSummary: String?): String {
        val side = data["signal_side"] ?: "-"
        val badge = if (side == "BUY") "🟢" else "🔴"
        return buildString {
            appendLine("$badge **สัญญาณ $side — ${job.symbol}**")
            appendLine("กลยุทธ์: ${data["signal_strategy"] ?: "-"}")
            appendLine()
            appendLine("| รายการ | ค่า |")
            appendLine("|---|---|")
            appendLine("| Entry | ${data["signal_entry"] ?: "-"} |")
            appendLine("| TP | ${data["signal_tp"] ?: "-"} |")
            appendLine("| SL | ${data["signal_sl"] ?: "-"} |")
            appendLine("| RR | 1:${data["signal_rr"] ?: "-"} |")
            data["signal_atr"]?.let { appendLine("| ATR14 | $it |") }
            appendLine()
            appendLine("**เหตุผล:** ${data["signal_reason"] ?: "-"}")
            if (!aiSummary.isNullOrBlank()) {
                appendLine()
                appendLine("**JARVIS quick-check:** $aiSummary")
            }
        }.trim()
    }

    /**
     * การ์ด alert ทั่วไปสำหรับแชท (ราคา/indicator/SMC/sentiment ฯลฯ) —
     * header + ตารางเงื่อนไข/ค่าปัจจุบัน + ข้อมูลประกอบจาก provider + quick-check (ถ้ามี)
     * แทนข้อความยาวติดกันที่อ่านยาก
     */
    private fun buildAlertChatCard(job: AlertJob, value: String, data: Map<String, String>, aiSummary: String?): String {
        val condText = conditionText(job)
        return buildString {
            appendLine("🎯 **Alert: ${job.name}**")
            appendLine(job.symbol)
            appendLine()
            appendLine("| รายการ | ค่า |")
            appendLine("|---|---|")
            appendLine("| เงื่อนไข | $condText |")
            appendLine("| ค่าปัจจุบัน | $value |")
            if (!aiSummary.isNullOrBlank()) {
                appendLine()
                appendLine("**JARVIS quick-check:** $aiSummary")
            }
        }.trim()
    }

    /** ข้อความพูดสั้นๆ สำหรับ signal alert — ลดเวลา synthesize/ฟังของ Gemini TTS (ข้อความยาว = ดีเลย์สูง) */
    private fun buildSignalSpeech(job: AlertJob, data: Map<String, String>): String {
        val sideTh = if (data["signal_side"] == "BUY") "ซื้อ" else "ขาย"
        val sym = job.symbol.substringBefore("@")
        val symTh = when (sym.uppercase()) {
            "XAUUSD" -> "ทองคำ"; "XAGUSD" -> "เงินแท่ง"
            "BTCUSDT", "BTCUSD" -> "บิทคอยน์"; "ETHUSDT", "ETHUSD" -> "อีเทอเรียม"
            else -> sym
        }
        val tfTh = when (job.symbol.substringAfter("@", "").uppercase()) {
            "5M", "M5" -> " 5 นาที"; "15M", "M15" -> " 15 นาที"; "30M", "M30" -> " 30 นาที"
            "1H", "H1" -> " 1 ชั่วโมง"; "4H", "H4" -> " 4 ชั่วโมง"; "1D", "D1" -> " รายวัน"
            else -> ""
        }
        return "สัญญาณ$sideTh $symTh$tfTh จากกลยุทธ์ ${data["signal_strategy"] ?: ""} " +
            "จุดเข้า ${data["signal_entry"] ?: "-"} สต็อปลอส ${data["signal_sl"] ?: "-"} เทคโพรฟิต ${data["signal_tp"] ?: "-"}"
    }

    /** ข้อความพูดสั้นๆ สำหรับ alert ทั่วไป — ประหยัดโควต้า Gemini TTS (คิดค่าตามตัวอักษร/จำกัดปริมาณ)
     *  เดิมพูด aiText เต็ม (~200+ ตัวอักษร) ทุก alert → เปลี่ยนเป็น template สั้น ~40-60 ตัวอักษร */
    private fun buildAlertSpeech(job: AlertJob, value: String): String {
        val sym = job.symbol.substringBefore("@")
        val symTh = when (sym.uppercase()) {
            "XAUUSD" -> "ทองคำ"; "XAGUSD" -> "เงินแท่ง"
            "BTCUSDT", "BTCUSD" -> "บิทคอยน์"; "ETHUSDT", "ETHUSD" -> "อีเทอเรียม"
            else -> sym
        }
        return "แจ้งเตือน ${job.name} $symTh เข้าเงื่อนไขแล้ว ค่าปัจจุบัน $value"
    }

    // ─── Natural Voice (Gemini TTS) — เสียงแจ้งเตือนแบบคน ไม่ใช่ TTS หุ่นยนต์ ────
    // เหตุผล: Android TTS (th-TH) บนเครื่องส่วนใหญ่สะกดคำอังกฤษทีละตัว (เช่น R-e-v-e-r-s-a-l)
    // และเสียงแข็ง — ใช้ Gemini TTS (gemini-2.5-flash-preview-tts) เป็นหลัก, fallback เป็น Android TTS

    // ─── Alert Voice Chain (2026-08-15 หลังทดสอบ 4 engines บน 2 เครื่อง) ───────
    // ตัด Gemini TTS one-shot ออก (ติดโควต้า + ดีเลย์คงที่ ~14 วิ) — เหลือ 2 โหมด:
    //   device → Android TTS ทันที/ไม่จำกัด
    //   live   → Gemini Live API chain → Android TTS
    // ค่าเก่าที่เคย persist ("ai"/"live31"/"live25") ถือเป็น "live" ทั้งหมด
    // chain เริ่มจากโมเดล Live ที่ผู้ใช้เลือกใน Settings (live_model_name) ก่อน แล้วค่อยไล่ตัวที่เหลือ
    // — กันสับสน: เสียงแจ้งเตือนจะเหมือนกับโหมด Live ที่คุยอยู่เสมอ

    private val LIVE_VOICE_MODELS = listOf(
        "gemini-2.5-flash-native-audio-preview-12-2025" to "Live 2.5 Native",
        "gemini-3.1-flash-live-preview" to "Live 3.1"
    )

    /** chain โมเดลเสียงแจ้งเตือน: โมเดล Live ที่ผู้ใช้เลือกขึ้นก่อน ตามด้วยตัวที่เหลือ */
    private fun liveVoiceChain(): List<Pair<String, String>> {
        val selected = setting("live_model_name").removePrefix("models/").ifBlank {
            com.example.personalaibot.data.ModelConfig.DEFAULT_LIVE_MODEL
        }
        val label = LIVE_VOICE_MODELS.firstOrNull { it.first == selected }?.second
            ?: selected // โมเดลนอกลิสต์ (เช่น preview ใหม่) — ใช้ชื่อดิบเป็น label
        return listOf(selected to label) + LIVE_VOICE_MODELS.filter { it.first != selected }
    }

    private fun isLiveEngine(engine: String): Boolean =
        engine == "live" || engine == "ai" || engine == "live31" || engine == "live25"

    /**
     * พูดข้อความแจ้งเตือน — device → Android TTS ทันที | live → ไล่ chain Live 2.5 Native → Live 3.1 → Android TTS
     * Live พูดข้อความยาวเต็ม (fullText), device/fallback พูดข้อความสั้น
     * @param onVoiceStart เรียกครั้งเดียวเมื่อรู้ว่า engine ไหนพูดจริง — ใช้ใส่ footer "เสียง:" ในการ์ดแชท
     */
    private suspend fun speakAlert(shortText: String, fullText: String? = null, onVoiceStart: (String) -> Unit = {}) {
        val engine = setting("alert_voice_engine").ifBlank { "device" }
        val live = isLiveEngine(engine)
        val short = sanitizeForSpeech(shortText)
        val full = sanitizeForSpeech(fullText ?: shortText)
        val notified = java.util.concurrent.atomic.AtomicBoolean(false)
        val notifyEngine = { label: String -> if (notified.compareAndSet(false, true)) onVoiceStart(label) }
        logDebug("AutomationService", "🔊 speakAlert engine=$engine ttsReady=$ttsReady")
        if (!live) {
            if (short.isBlank()) { notifyEngine("-"); return }
            speak(short)
            logDebug("AutomationService", "🔊 → Android TTS (device mode) spoken")
            notifyEngine("Android TTS")
            return
        }
        if (full.isBlank()) { notifyEngine("-"); return }
        val apiKey = setting("api_key")
        if (apiKey.isBlank()) {
            logDebug("AutomationService", "🔊 ไม่มี api_key → Android TTS")
            if (short.isNotBlank()) speak(short)
            notifyEngine("Android TTS (ไม่มี api_key)")
            return
        }
        // ใช้เสียง + identity เดียวกับ Live mode (voice_name จาก Settings + CORE_IDENTITY)
        // — เดิม hardcode Aoede + prompt สั้น ทำให้บุคลิกเสียงแจ้งเตือนต่างจากเสียง Live ที่ผู้ใช้เลือก
        val voiceName = setting("voice_name").ifBlank { "Aoede" }
        val chain = liveVoiceChain()
        logDebug("AutomationService", "🔊 Live chain: ${chain.joinToString(" → ") { it.second }} (ตามโมเดล Live ที่เลือกใน Settings)")
        for ((index, pair) in chain.withIndex()) {
            val (model, label) = pair
            val tag = if (index == 0) label else "$label (fallback)"
            val ok = try {
                speakViaLive(apiKey, model, full, voiceName) { notifyEngine(tag) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logError("AutomationService", "🔊 Live ($model) exception: ${e.message}", e)
                false
            }
            if (ok) return
            logDebug("AutomationService", "🔊 Live ($model) ไม่ได้เสียง → ลองตัวถัดไปใน chain")
        }
        logDebug("AutomationService", "🔊 Live chain พังทั้งหมด → Android TTS")
        if (short.isNotBlank()) speak(short)
        notifyEngine("Android TTS (fallback)")
    }

    /**
     * พูดผ่าน Gemini Live API (websocket BidiGenerateContent) แบบ one-shot —
     * เปิด session → ส่งข้อความเป็น clientContent → รับ audio chunk แล้วเล่นทันทีแบบ streaming
     * (ได้ยินเสียงตั้งแต่ chunk แรก ไม่ต้องรอ synthesize ครบเหมือน one-shot TTS — เหตุที่ Live ไม่ดีเลย์ในโหมดสนทนา)
     * คืน true ถ้าได้เสียงเล่นจริง
     */
    private suspend fun speakViaLive(
        apiKey: String, model: String, text: String,
        voiceName: String = "Aoede",
        onFirstAudio: () -> Unit = {}
    ): Boolean {
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val sampleRate = 24000
        val channel = android.media.AudioFormat.CHANNEL_OUT_MONO
        val encoding = android.media.AudioFormat.ENCODING_PCM_16BIT
        val minBuf = android.media.AudioTrack.getMinBufferSize(sampleRate, channel, encoding)
        val queue = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        var gotAudio = false
        var totalBytes = 0
        val t0 = System.currentTimeMillis()
        var firstChunkAt = 0L
        var track: android.media.AudioTrack? = null
        try {
            track = android.media.AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(encoding).setSampleRate(sampleRate).setChannelMask(channel)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, 64 * 1024))
                .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                .build()
            // playback loop แยก thread — เล่น chunk ทันทีที่มาถึง (streaming)
            val theTrack = track
            val player = scope.async(Dispatchers.IO) {
                try {
                    theTrack.play()
                    while (!done.get() || queue.isNotEmpty()) {
                        val chunk = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                        theTrack.write(chunk, 0, chunk.size)
                    }
                } catch (e: Exception) {
                    logError("AutomationService", "🔊 Live playback error: ${e.message}", e)
                }
            }
            withTimeoutOrNull(90_000) {
                try {
                    geminiClient.webSocket(url) {
                        val setup = LiveSetupMessage(setup = LiveSetup(
                            model = if (model.startsWith("models/")) model else "models/$model",
                            systemInstruction = LiveSystemInstruction(parts = listOf(
                                // identity เดียวกับ Live mode (JarvisPersona.CORE_IDENTITY) — เสียง/บุคลิก/การลงท้ายประโยคจะได้ตรงกับที่ผู้ใช้ตั้งไว้
                                LivePart(text = com.example.personalaibot.ai.JarvisPersona.CORE_IDENTITY),
                                LivePart(text = "[STRICT] หน้าที่ตอนนี้คืออ่านข้อความแจ้งเตือนที่ได้รับออกเสียงเป็นภาษาไทยตรงๆ ด้วยน้ำเสียงและบุคลิกข้างต้น ห้ามเพิ่มเติม ห้ามตอบโต้ ห้ามเรียกเครื่องมือ")
                            )),
                            generationConfig = LiveGenerationConfig(
                                responseModalities = listOf("AUDIO"),
                                speechConfig = LiveSpeechConfig(
                                    voiceConfig = LiveVoiceConfig(
                                        prebuiltVoiceConfig = LivePrebuiltVoiceConfig(voiceName = voiceName)
                                    )
                                )
                            )
                        ))
                        send(Frame.Text(json.encodeToString(LiveSetupMessage.serializer(), setup)))
                        var sentText = false
                        for (frame in incoming) {
                            val raw = when (frame) {
                                is Frame.Text -> frame.readText()
                                is Frame.Binary -> frame.readBytes().decodeToString()
                                else -> continue
                            }
                            val msg = try {
                                json.decodeFromString(LiveServerMessage.serializer(), raw)
                            } catch (_: Exception) { continue }
                            var shouldClose = false
                            msg.error?.let {
                                logError("AutomationService", "🔊 Live API error: ${it.message}", null)
                                shouldClose = true
                            }
                            if (msg.setupComplete != null && !sentText) {
                                sentText = true
                                logDebug("AutomationService", "🔊 Live session READY (${System.currentTimeMillis() - t0}ms) → ส่งข้อความ")
                                val cc = LiveClientContentMessage(clientContent = LiveContentWrapper(
                                    turns = listOf(LiveTurn(role = "user", parts = listOf(LivePart(text = text)))),
                                    turnComplete = true
                                ))
                                send(Frame.Text(json.encodeToString(LiveClientContentMessage.serializer(), cc)))
                                continue
                            }
                            val sc = msg.serverContent
                            if (sc != null) {
                                sc.modelTurn?.parts?.forEach { p ->
                                    p.inlineData?.takeIf { it.mimeType.contains("audio") }?.let { blob ->
                                        val pcmChunk = android.util.Base64.decode(blob.data, android.util.Base64.DEFAULT)
                                        if (firstChunkAt == 0L) {
                                            firstChunkAt = System.currentTimeMillis()
                                            logDebug("AutomationService", "🔊 Live first audio chunk (${firstChunkAt - t0}ms)")
                                            onFirstAudio()
                                        }
                                        queue.add(pcmChunk)
                                        totalBytes += pcmChunk.size
                                        gotAudio = true
                                    }
                                }
                                if (sc.turnComplete == true) shouldClose = true
                            }
                            if (shouldClose) { close(); break }
                        }
                        // server ปิดเอง (เช่น model ไม่ถูก → ปิดเงียบๆ) — log close reason เสมอ กัน debug ไม่เจอสาเหตุ
                        val reason = closeReason.await()
                        logDebug("AutomationService", "🔊 Live ws closed: code=${reason?.code} reason=${reason?.message} (${System.currentTimeMillis() - t0}ms, gotAudio=$gotAudio)")
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    logError("AutomationService", "🔊 Live session failed (${System.currentTimeMillis() - t0}ms): ${e.message}", e)
                }
            } ?: logDebug("AutomationService", "🔊 Live timeout 90s — ปิด session")
            done.set(true)
            player.await()
            if (gotAudio) {
                logDebug("AutomationService", "🔊 → Live ($model) OK $totalBytes bytes (${System.currentTimeMillis() - t0}ms, first chunk ${if (firstChunkAt > 0) "${firstChunkAt - t0}ms" else "-"})")
            }
            return gotAudio
        } finally {
            done.set(true)
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
        }
    }

    /** ตัด emoji/markdown ออกก่อนส่ง TTS (กันเครื่องอ่านชื่อ emoji หรือสะกดสัญลักษณ์) */
    private fun sanitizeForSpeech(text: String): String =
        text.replace(Regex("[*_#`|>~]"), " ")
            .replace(Regex("[\\p{So}\\p{Sk}]"), " ") // emoji/symbol อื่น
            .replace(Regex("\\s+"), " ")
            .trim()

    // (ลบ geminiTtsPcm/playPcmBlocking ออก 2026-08-15 — Gemini TTS one-shot ถูกตัดจากระบบ
    //  เพราะติดโควต้า + ดีเลย์คงที่ ~14 วิ; เสียง cloud ทั้งหมดย้ายไป Live API chain ใน speakAlert)

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

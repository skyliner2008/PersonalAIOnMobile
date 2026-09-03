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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
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

        /** one-shot announce ผลงานพื้นหลัง (backtest/optimize เสร็จ) — notification + เสียง + การ์ดแชท */
        const val ACTION_LONGTASK_ANNOUNCE = "com.example.personalaibot.action.LONGTASK_ANNOUNCE"
        const val EXTRA_ANNOUNCE_TITLE = "announce_title"
        const val EXTRA_ANNOUNCE_BODY = "announce_body"
        const val EXTRA_ANNOUNCE_META = "announce_meta"
        const val EXTRA_ANNOUNCE_SHORT = "announce_short"
        const val EXTRA_ANNOUNCE_FULL = "announce_full"
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

    // Per-poll-cycle cache for trading_signal_alert. BUY/SELL jobs for the same
    // symbol/timeframe must evaluate the exact same candle snapshot; fetching the
    // provider twice can duplicate expensive TradingView work and gate diagnostics.
    private val signalCycleCache = mutableMapOf<String, Map<String, String>>()

    /**
     * Live Alert Scheduler — serializes only the physical audio output, while the
     * signal/AI/notification pipeline stays concurrent. Requests are kept in a
     * small priority queue so a fresh signal is never blocked by an older queued
     * scheduled announcement. Higher timeframe signals get a slightly lower
     * priority than fast TF signals because M1/M5 are more time-sensitive.
     */
    private data class LiveAlertRequest(
        val priority: Int,
        val sequence: Long,
        val shortText: String,
        val fullText: String?,
        val liveSummary: Boolean,
        val timeframeMin: Int?,
        val result: CompletableDeferred<VoiceDeliveryResult>
    )

    private val liveAlertSchedulerMutex = Mutex()
    private val liveAlertSchedulerWake = Channel<Unit>(Channel.CONFLATED)
    private val liveAlertSchedulerQueue = mutableListOf<LiveAlertRequest>()
    private var liveAlertSchedulerSequence = 0L
    private var liveAlertSchedulerJob: Job? = null

    /** Watchdog after READY: if neither audio nor any transcript arrives, fail fast.
     *  14s (เดิม 8s) — จาก log 2026-08-25: Live 2.5 Native ที่ "สุขภาพดี" ส่ง first audio
     *  ช้าถึง 7.5–8.8s หลัง READY (17:34 = 7.5s, 17:47 = 7.8s รอดพอดี) ส่วน session ที่
     *  timeout ตายที่ 8.0–8.3s พอดีเป๊ะ → watchdog ฆ่ายิง session ที่กำลังจะตอบ
     *  เลยขยับให้พ้น p95 ของ first-audio latency ที่เห็นจริง */
    private val liveVoiceFirstOutputTimeoutMs = 14_000L
    /** Setup watchdog: READY must arrive promptly or the provider/network is considered unhealthy. */
    private val liveVoiceSetupTimeoutMs = 8_000L
    /** Hard ceiling for a one-shot Live alert session; long signal summaries can legitimately stream for ~60s. */
    private val liveVoiceSessionTimeoutMs = 22_000L
    /** Once audio has started, only fail when the stream stops making progress for this long. */
    private val liveVoiceAudioIdleTimeoutMs = 10_000L
    /** Circuit breaker for runaway Live alert generations. */
    /** Short-form Signal Alert cap. ~120 Thai chars keeps spoken alerts near 10–15s. */
    private val liveVoiceSummaryCharCap = 120
    /** Serialize alert-summary Gemini calls to prevent burst/429 and unbounded concurrent work. */
    private val alertAiMutex = Mutex()

    /**
     * Signal alert throttle: one alert per symbol+TF+side inside the cooldown window.
     * This is deliberately independent of job.is_triggered because each new candle creates
     * a new signal_id and would otherwise bypass the job-level dedup state.
     */
    private val signalAlertLastHandledAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val signalAlertLastHandledId = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val signalAlertCooldownMs = 90_000L

    /** Compact operational logging: emit a signal action only when its state changes. */
    private val signalLastLoggedAction = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Per-cycle cache diagnostics are summarized once instead of logging every HIT/MISS. */
    private val signalCycleFetched = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val signalCycleHits = java.util.concurrent.atomic.AtomicInteger(0)

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
        // getRunnableJobs = active ที่ยังไม่ TRIGGERED ค้าง (หรือเป็น signal alert ที่ re-arm เอง)
        // — job ทั่วไปที่แจ้งไปแล้วจะไม่เข้า loop อีก จนกว่าผู้ใช้จะกด 🔁 ซ้ำ/🗑 ลบ จาก notification หรือหน้า list
        val jobs = database.jarvisDatabaseQueries.getRunnableJobs().executeAsList()
        val tasks = database.jarvisDatabaseQueries.getAllActiveScheduledTasks().executeAsList()
        // Signal alerts are edge-triggered and BUY/SELL jobs may share the same
        // symbol@TF. Reset once per polling cycle so both sides see one coherent
        // market snapshot, while the next cycle can observe a newly closed candle.
        signalCycleCache.clear()
        signalCycleFetched.clear()
        signalCycleHits.set(0)

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

        if (signalCycleFetched.isNotEmpty() || signalCycleHits.get() > 0) {
            logDebug(
                "AutomationService",
                "SIGNAL_CACHE cycle symbolsFetched=${signalCycleFetched.size} cacheHits=${signalCycleHits.get()} " +
                    "symbols=${signalCycleFetched.sorted().joinToString(",") { it }}"
            )
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
        // Alert ทั่วไปที่ยิงไปแล้ว (is_triggered=1) → พักการเฝ้าดู รอผู้ใช้ตัดสินใจจากปุ่มบน notification
        // (🛑 หยุดแจ้งเตือน = ปิด job / 🔁 แจ้งเตือนซ้ำ = รีเซ็ตให้เฝ้าดูใหม่ ผ่าน AlertActionReceiver)
        // trading_signal_alert ใช้ signal_id เป็น dedup key: signal ใหม่ยิงได้ 1 ครั้ง,
        // signal_id เดิมจะถูก suppress แม้ is_triggered ของ job จะยังเป็น 1
        if (job.is_triggered == 1L && job.tool_name != "trading_signal_alert") {
            logDebug("AutomationService", "⏸ พัก '${job.name}' — TRIGGERED แล้ว รอผู้ใช้เลือก หยุด/ซ้ำ จาก notification")
            return
        }

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
            "trading_signal_alert" -> {
                signalCycleCache[job.symbol]?.also {
                    signalCycleHits.incrementAndGet()
                } ?: signalAlertProvider.fetch(job.symbol).also {
                    signalCycleCache[job.symbol] = it
                    signalCycleFetched.add(job.symbol)
                }
            }
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
            if (job.tool_name != "trading_signal_alert") {
                automationManager.recordJobCheckResult(job.id, "ERR($errMsg)")
            }
            return
        }

        // Compact event diagnostics: emit only state transitions for signal jobs;
        // routine polling remains silent so logcat stays useful for AI diagnostics.
        val sampleStr = lastSample ?: "N/A"
        val isMet = evaluator.evaluate(data, condition)

        // Trading signal alerts are edge-triggered by signal_bar_id, not by the
        // job's boolean is_triggered alone. This prevents a new candle/signal from
        // being suppressed by the previous candle while still deduplicating the
        // exact same signal_id.
        val signalId = if (job.tool_name == "trading_signal_alert") {
            when (condition.field) {
                "signal_buy", "signal_buy_id" -> data["signal_buy_id"]?.toLongOrNull() ?: 0L
                "signal_sell", "signal_sell_id" -> data["signal_sell_id"]?.toLongOrNull() ?: 0L
                else -> 0L
            }
        } else 0L
        val triggered = if (job.tool_name == "trading_signal_alert" && signalId > 0L) {
            job.is_triggered == 1L && job.last_value?.toLongOrNull() == signalId
        } else {
            job.is_triggered == 1L
        }

        // Job-level dedup only knows about the exact last signal_id. A new candle/strategy
        // can therefore bypass it even when the previous alert was only seconds ago.
        // Apply a short market-event throttle at symbol+TF+side level before FIRE.
        val signalThrottleKey = if (job.tool_name == "trading_signal_alert" && signalId > 0L) {
            job.symbol + "|" + condition.field + "|" + (data["signal_side"] ?: "UNKNOWN")
        } else null
        var throttledSignal = false
        if (isMet && signalThrottleKey != null && !triggered) {
            val now = System.currentTimeMillis()
            val lastAt = signalAlertLastHandledAt[signalThrottleKey] ?: 0L
            val lastId = signalAlertLastHandledId[signalThrottleKey] ?: 0L
            throttledSignal = signalId == lastId || (lastAt > 0L && now - lastAt < signalAlertCooldownMs)
            if (throttledSignal) {
                logDebug(
                    "AutomationService",
                    "SIGNAL_EVENT job=${job.name} symbol=${job.symbol} side=${data["signal_side"] ?: "UNKNOWN"} signal_id=$signalId strategy=${data["signal_strategy"] ?: "UNKNOWN"} action=SUPPRESS_COOLDOWN remaining=${if (lastAt > 0L) (signalAlertCooldownMs - (now - lastAt)).coerceAtLeast(0L) else 0L}ms"
                )
            } else {
                signalAlertLastHandledAt[signalThrottleKey] = now
                signalAlertLastHandledId[signalThrottleKey] = signalId
            }
        }
        val action = when {
            isMet && !triggered && !throttledSignal -> "FIRE"
            isMet && (triggered || throttledSignal) -> if (throttledSignal) "SUPPRESS_COOLDOWN" else "SUPPRESS_ALREADY_TRIGGERED"
            !isMet && job.is_triggered == 1L -> "RESET"
            else -> "WAIT"
        }
        // WAIT is routine polling noise; signal actions are deduplicated below.
        val signalLogKey = if (job.tool_name == "trading_signal_alert") "${job.symbol}|${condition.field}|$signalId" else null
        val previousSignalAction = if (signalLogKey != null && action != "WAIT") signalLastLoggedAction.put(signalLogKey, action) else null
        if (action != "WAIT" && (signalLogKey == null || previousSignalAction != action)) {
            val signalPart = if (job.tool_name == "trading_signal_alert") {
                " | signal_id=$signalId | job_triggered=${job.is_triggered}"
            } else ""
            logDebug(
                "AutomationService",
                "SIGNAL_EVENT job=${job.name} field=${condition.field} value=$sampleStr condition=${condition.operator}:${condition.value} met=${if (isMet) 1 else 0} triggered=${if (triggered) 1 else 0}$signalPart action=$action"
            )
        }
        // For trading_signal_alert, last_value is reserved for the last triggered signal_id.
        // Do not overwrite it with the boolean/sample value, otherwise a later cycle could
        // lose the signal-id dedup key due to the asynchronous recordJobCheckResult().
        if (job.tool_name != "trading_signal_alert") {
            automationManager.recordJobCheckResult(job.id, sampleStr)
        }

        // --- Auto High Confluence Alert ---
        if (job.tool_name == "trading_deep_analysis_suite") {
             val score = data["summaryScore"]?.toDoubleOrNull() ?: 0.0
             if (score >= 85.0 && job.is_triggered == 0L) {
                 // mark ก่อนยิง (กันยิงซ้ำทุก cycle ขณะ score ค้าง ≥85) แล้วพักรอผู้ใช้เลือกเหมือน alert ทั่วไป
                 automationManager.markTriggered(job.id, "🌟 $score")
                 // แยกไปทำขนาน — ไม่บล็อก loop (AI call ใช้เวลา ~10 วิ)
                 scope.launch { fireJobAlert(job, "🌟 High Confluence ($score)", data) }
                 return // ยิง confluence แล้ว ไม่ประเมินเงื่อนไขหลักซ้ำในรอบนี้ (กัน notification เบิ้ล)
             }
        }

        // 2. Apply the already-evaluated condition result.
        if (isMet) {
            if (!triggered && !throttledSignal) {
                // Signal alerts are keyed by signal_id; ordinary alerts keep the
                // existing boolean trigger semantics.
                val triggerValue = if (job.tool_name == "trading_signal_alert" && signalId > 0L) {
                    signalId.toString()
                } else {
                    sampleStr
                }
                // mark ก่อนเสมอ (synchronous) กัน tick ถัดไปยิงซ้ำ แล้วค่อยปลุก AI แบบขนาน
                // — เดิมเรียก Gemini แบบ serial ใน loop ทำให้ job ถัดไปช้าไป ~10 วินาที
                automationManager.markTriggered(job.id, triggerValue)
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

    private data class LiveSpeechResult(
        val ok: Boolean,
        val transcript: String?,
        val cause: String = "NONE"
    )

    private data class VoiceDeliveryResult(val engineLabel: String?, val summary: String?)

    /** ส่งการ์ดเข้าแชท + พูดเสียงตามลำดับที่ถูกต้อง —
     *  ถ้าเปิดเสียง: พูดก่อน แล้ว callback onVoiceStart จะ push การ์ดพร้อม footer "เสียง: <engine ที่พูดจริง>"
     *  ถ้าปิดเสียง: push การ์ดทันที (ไม่มี footer) */
    private suspend fun deliverChatAndVoice(
        cardBody: String,
        metaFor: (String?) -> String,
        shortSpeech: String,
        fullSpeech: String,
        liveSummary: Boolean = false,
        timeframeMin: Int? = null
    ) {
        if (settingEnabled("alert_voice", false)) {
            // Live Summary ต้องรอ output transcription จบก่อน push การ์ด เพื่อให้
            // Chat ใช้ summary เดียวกับเสียงจริงจาก Gemini Live ไม่ใช่ summary คนละโมเดล
            val result = speakAlert(shortSpeech, fullSpeech, liveSummary, timeframeMin)
            val finalBody = if (liveSummary && !result.summary.isNullOrBlank()) {
                if (cardBody.contains("**JARVIS Live Summary:**")) cardBody
                else "$cardBody\n\n**JARVIS Live Summary:** ${result.summary}"
            } else cardBody
            pushToChat(finalBody, metaFor(result.engineLabel))
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


        // ── AI Strategy Supervisor (Unified SMC เท่านั้น, โหมด ai) — gate ก่อนแจ้งผู้ใช้ ──
        // สัญญาณ + โครงสร้างตลาด 5TF (signal_mtf_context จาก MarketContextDigest) → APPROVE / VETO / ADJUST
        // VETO  = คัดออก ไม่แจ้งผู้ใช้ (shadow-record ไว้เปรียบเทียบ approved vs vetoed)
        // ADJUST = ปรับ SL/TP ตามโครงสร้าง (ผ่าน validation เท่านั้น ไม่งั้น fallback ค่าเดิม)
        var effData: Map<String, String> = data
        var supervisorNote: String? = null
        val isKeyzoneOnly = isSignalAlert && data["signal_side"] == null
        if (isSignalAlert && !isKeyzoneOnly && delivery != "direct" &&
            (data["signal_strategy"] ?: "").contains("Unified SMC")
        ) {
            val sup = runCatching { runStrategySupervisor(job, data) }
                .onFailure { logError("AutomationService", "🧑‍✈️ Supervisor error: ${it.message}", it) }
                .getOrNull()
            if (sup != null) {
                when (sup.decision) {
                    "VETO" -> {
                        logDebug("AutomationService",
                            "🧑‍✈️ Supervisor VETO ${data["signal_side"]} ${job.symbol} conf=${sup.confidence}% — ${sup.reasonTh} (ไม่แจ้งผู้ใช้)")
                        // shadow record: tracker ตามผลสัญญาณที่โดนคัดออก (delivery tag *_veto)
                        runCatching {
                            val side = data["signal_side"]
                            val entry = data["signal_entry"]?.replace(",", "")?.toDoubleOrNull()
                            val sl = data["signal_sl"]?.replace(",", "")?.toDoubleOrNull()
                            val tp = data["signal_tp"]?.replace(",", "")?.toDoubleOrNull()
                            val barTime = data["signal_bar_time"]?.toLongOrNull() ?: 0L
                            if (side != null && entry != null && sl != null && tp != null && barTime > 0) {
                                automationManager.recordSignalAlert(
                                    jobId = job.id, symbol = job.symbol, side = side,
                                    strategy = (data["signal_strategy"] ?: "-") + " [AI-VETO ${sup.confidence}%]",
                                    reason = sup.reasonTh, entry = entry, sl = sl, tp = tp,
                                    rr = data["signal_rr"]?.toDoubleOrNull(), barTime = barTime,
                                    delivery = "${delivery}_veto"
                                )
                            }
                        }
                        return
                    }
                    "ADJUST" -> {
                        val adj = validateSupervisorAdjustment(data, sup)
                        if (adj != null) {
                            logDebug("AutomationService",
                                "🧑‍✈️ Supervisor ADJUST ${job.symbol}: SL ${data["signal_sl"]}→${adj["signal_sl"]} TP ${data["signal_tp"]}→${adj["signal_tp"]} (RR 1:${adj["signal_rr"]}, conf=${sup.confidence}%)")
                            effData = data + adj + mapOf(
                                "signal_reason" to "${data["signal_reason"]} | Supervisor ปรับ SL/TP ตามโครงสร้าง",
                                "signal_supervisor" to "ADJUST ${sup.confidence}%"
                            )
                        } else {
                            logDebug("AutomationService", "🧑‍✈️ Supervisor ADJUST ไม่ผ่าน validation → fallback APPROVE ค่าเดิม (${job.symbol})")
                            effData = data + mapOf("signal_supervisor" to "APPROVE ${sup.confidence}%")
                        }
                        supervisorNote = "Supervisor (${sup.confidence}%): ${sup.reasonTh}"
                    }
                    else -> {
                        logDebug("AutomationService", "🧑‍✈️ Supervisor APPROVE ${data["signal_side"]} ${job.symbol} conf=${sup.confidence}%")
                        effData = data + mapOf("signal_supervisor" to "APPROVE ${sup.confidence}%")
                        supervisorNote = "Supervisor (${sup.confidence}%): ${sup.reasonTh}"
                    }
                }
            }
        }
        // ── บันทึก signal ลงสถิติ (ทั้ง 2 โหมด) — tracker จะตามเช็ก TP/SL ทุก cycle ──
        if (isSignalAlert) {
            val side = effData["signal_side"]
            val entry = effData["signal_entry"]?.replace(",", "")?.toDoubleOrNull()
            val sl = effData["signal_sl"]?.replace(",", "")?.toDoubleOrNull()
            val tp = effData["signal_tp"]?.replace(",", "")?.toDoubleOrNull()
            val barTime = effData["signal_bar_time"]?.toLongOrNull() ?: 0L
            if (side != null && entry != null && sl != null && tp != null && barTime > 0) {
                automationManager.recordSignalAlert(
                    jobId = job.id, symbol = job.symbol, side = side,
                    strategy = effData["signal_strategy"] ?: "-", reason = effData["signal_reason"],
                    entry = entry, sl = sl, tp = tp,
                    rr = effData["signal_rr"]?.toDoubleOrNull(), barTime = barTime, delivery = delivery
                )
            }
        }

        // ── โหมดส่งตรง: ไม่เรียก AI (ประหยัดโทเคน) — notification + ส่งเข้าแชทโดยตรง ──
        if (delivery == "direct") {
            val body = if (isSignalAlert) {
                "📡 Signal ${effData["signal_side"]} ${job.symbol} (${effData["signal_strategy"]})\n" +
                    "เหตุผล: ${effData["signal_reason"]}\n" +
                    "Entry: ${effData["signal_entry"]} | SL: ${effData["signal_sl"]} | TP: ${effData["signal_tp"]} | RR 1:${effData["signal_rr"]}"
            } else {
                "🎯 ${job.name}: ${job.symbol} เข้าเงื่อนไขแล้ว — ค่าปัจจุบัน: $value"
            }
            sendJobAlertNotification(job, body)
            // การ์ดแชท + เสียง — การ์ดจะมี footer บอก engine เสียงที่พูดจริง (push ตอนเสียงเริ่ม)
            deliverChatAndVoice(
                cardBody = when {
                    isKeyzoneOnly -> buildKeyzoneChatCard(job, effData, null)
                    isSignalAlert -> buildSignalChatCard(job, effData, null)
                    else -> buildAlertChatCard(job, value, effData, null)
                },
                metaFor = { v ->
                    when {
                        isKeyzoneOnly -> alertChatMeta(job, value, null, "signal_alert_direct", v)
                        isSignalAlert -> signalChatMeta(job, effData, null, "signal_alert_direct", v)
                        else -> alertChatMeta(job, value, null, "signal_alert_direct", v)
                    }
                },
                shortSpeech = if (isSignalAlert) buildSignalSpeech(job, data) else buildAlertSpeech(job, value),
                fullSpeech = body,
                timeframeMin = symbolTimeframeMin(job.symbol))
            return
        }

        // ── โหมด AI: ถ้าเป็น Signal + Live Voice ให้ Gemini Live ทำ Summary เอง ──
        // ไม่เรียก generateContent ก่อน Live อีกต่อไป เพราะจะทำให้เสียงต้องรอ Chat model timeout/fallback
        val useLiveSummary = isSignalAlert &&
            settingEnabled("alert_ai_summary", true) &&
            settingEnabled("alert_voice", false) &&
            isLiveEngine(setting("alert_voice_engine").ifBlank { "device" })

        val contextPrompt = if (isKeyzoneOnly) buildString {
            appendLine("เหตุการณ์: ราคา ${job.symbol} เคลื่อนไปแตะจุดสำคัญของโครงสร้างตลาด (ยังไม่มีสัญญาณเข้าเทรด)")
            appendLine("จุดที่แตะ: ${effData["signal_keyzone_desc"]}")
            appendLine("โครงสร้างตลาด 5 ไทม์เฟรม:")
            appendLine(effData["signal_mtf_context"]?.takeIf { it.isNotBlank() } ?: (effData["signal_context"] ?: "-"))
            appendLine()
            appendLine("ช่วยแจ้งผู้ใช้ภาษาไทยสั้น 2-3 ประโยค: ราคาแตะจุดไหน โครงสร้างรอบข้างเป็นอย่างไร และควรจับตาอะไร (ยังไม่มีสัญญาณเข้าเทรด) — ใช้เฉพาะข้อมูลที่ให้ ห้ามสมมติเพิ่ม")
        } else if (isSignalAlert) buildString {
            appendLine("เหตุการณ์: ระบบ Signal Alert ตรวจพบสัญญาณเทรดใหม่ของ ${job.symbol}!")
            appendLine("สัญญาณ: ${effData["signal_side"]} (กลยุทธ์: ${effData["signal_strategy"]})")
            appendLine("เหตุผล/เงื่อนไขที่เกิดสัญญาณ: ${effData["signal_reason"]}")
            appendLine("จุดเข้าออเดอร์: ${effData["signal_entry"]} | Stop Loss: ${effData["signal_sl"]} | Take Profit: ${effData["signal_tp"]} (Risk:Reward ≈ 1:${effData["signal_rr"]})")
            appendLine("บริบทกราฟโดยรวม: ${effData["signal_context"]}")
            appendLine()
            if (useLiveSummary) {
                appendLine("[LIVE SIGNAL SUMMARY]")
                appendLine("วิเคราะห์ข้อมูลที่ให้มาแล้วพูดสรุปเป็นภาษาไทยแบบธรรมชาติ ไม่ใช่การอ่านข้อความดิบ")
                appendLine("ต้องมีเพียง 2-3 ประโยค หรือราว 35-55 คำ และควรจบภายใน ~15 วินาที ห้ามขยายความเกินข้อมูลที่ให้มา")
                appendLine("ครอบคลุมเท่าที่ทำได้ในความยาวจำกัด: (1) Signal และ Strategy (2) Entry/SL/TP/RR (3) เหตุผลหรือ Context สำคัญเพียง 1 จุด และความเสี่ยงสั้นๆ")
                appendLine("ใช้เฉพาะข้อมูลที่ได้รับ ห้ามสมมติราคา/อินดิเคเตอร์/ข่าวหรือข้อมูลตลาดที่ไม่มีใน payload และห้ามรับประกันผลกำไร")
                appendLine("พูดเป็นบทวิเคราะห์ต่อเนื่อง ห้ามใช้ Markdown ตาราง bullet หรือหัวข้อแบบอ่านรายการ และห้ามพูดคำว่า 'ฉันกำลังคิด'")
            } else {
                appendLine("ช่วยแจ้งผู้ใช้ภาษาไทยแบบสั้น 3-4 ประโยค: (1) มีสัญญาณอะไรจากกลยุทธ์ไหน เพราะอะไร (2) จุดเข้า/SL/TP (3) quick-check จากบริบทที่ให้เท่านั้น ว่าสอดคล้องกับเทรนด์/โมเมนตัมไหม น่าสนใจหรือควรระวังอะไร — ใช้เฉพาะข้อมูลด้านบน ห้ามสมมติข้อมูลเพิ่ม")
            }
        } else buildString {
            appendLine("เหตุการณ์: การแจ้งเตือน '${job.name}' ของ ${job.symbol} เข้าเงื่อนไขแล้ว")
            appendLine("เงื่อนไขที่ตั้งไว้: ${job.condition_json}")
            appendLine("ค่าปัจจุบัน: $value")
            appendLine("ข้อมูลดิบ: ${effData.entries.take(8).joinToString { "${it.key}=${it.value}" }}")
            appendLine()
            appendLine("ช่วยสรุปแจ้งผู้ใช้แบบสั้น 2-3 ประโยค ภาษาไทย ว่าเกิดอะไรขึ้น และมีข้อแนะนำสั้นๆ (ถ้าเหมาะสม)")
        }

        val aiText = supervisorNote ?: if (!useLiveSummary && settingEnabled("alert_ai_summary", true)) {
            val waitStart = System.currentTimeMillis()
            alertAiMutex.lock()
            try {
                val waitedMs = System.currentTimeMillis() - waitStart
                if (waitedMs > 0L) {
                    logDebug("AutomationService", "🧠 AI summary queued — waited ${waitedMs}ms")
                }
                generateAiText(contextPrompt)
            } finally {
                alertAiMutex.unlock()
            }
        } else {
            if (useLiveSummary) {
                logDebug("AutomationService", "🧠 AI summary delegated to Gemini Live — skip standard Chat model")
            } else {
                logDebug("AutomationService", "🧠 AI summary skipped — toggle alert_ai_summary ปิดอยู่")
            }
            null
        }

        val body = aiText ?: if (isKeyzoneOnly) {
            "📍 ${job.symbol} แตะจุดสำคัญ: ${effData["signal_keyzone_desc"]?.ifBlank { "โครงสร้างตลาด" }}"
        } else if (isSignalAlert) {
            "📡 Signal ${effData["signal_side"]} ${job.symbol} @ ${effData["signal_entry"]} " +
                "(SL ${effData["signal_sl"]} / TP ${effData["signal_tp"]}) — ${effData["signal_strategy"]}: ${effData["signal_reason"]}"
        } else {
            "${job.symbol} เข้าเงื่อนไขแล้ว! ค่าปัจจุบัน: $value"
        }
        sendJobAlertNotification(job, body)
        deliverChatAndVoice(
            cardBody = when {
                isKeyzoneOnly -> buildKeyzoneChatCard(job, effData, aiText)
                isSignalAlert -> buildSignalChatCard(job, effData, aiText)
                else -> buildAlertChatCard(job, value, effData, aiText)
            },
            metaFor = { v ->
                when {
                    isKeyzoneOnly -> alertChatMeta(job, value, aiText, "signal_alert_ai", v)
                    isSignalAlert -> signalChatMeta(job, effData, aiText, "signal_alert_ai", v)
                    else -> alertChatMeta(job, value, aiText, "signal_alert_ai", v)
                }
            },
            shortSpeech = if (isSignalAlert) buildSignalSpeech(job, data) else buildAlertSpeech(job, value),
            fullSpeech = if (useLiveSummary) contextPrompt else body,
            liveSummary = useLiveSummary,
            timeframeMin = symbolTimeframeMin(job.symbol))
    }

    /**
     * Notification ของ alert แบบมีปุ่มกดได้ 2 ปุ่ม (ทำหน้าที่เหมือน msg box):
     *   🗑 ลบแจ้งเตือน   — ลบ job นี้ออกจากรายการถาวร
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
            .addAction(android.R.drawable.ic_menu_delete, "🗑 ลบแจ้งเตือน", actionIntent(ACTION_ALERT_STOP))
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
        // Alert summaries are time-critical. Keep a bounded fallback chain so a provider outage
        // cannot turn one notification into a minute-long cascade of sequential timeouts.
        val models = (listOf(primary) + com.example.personalaibot.data.ModelConfig.GEMINI_FALLBACK_MODELS)
            .distinct()
            .take(4)
        logDebug("AutomationService", "🧠 AI summary start — chain=${models.joinToString(" → ")}")
        for (m in models) {
            val t0 = System.currentTimeMillis()
            val text = try {
                // Alert AI is latency-sensitive: use one model per attempt, short timeout,
                // and disable GeminiService's 45s long-retry. The outer chain owns fallback.
                val service = GeminiService(geminiClient, apiKey, m).apply {
                    fallbackModelsOverride = emptyList()
                }
                service.generateResponse(
                    prompt = prompt,
                    intentAddon = "คุณคือ JARVIS ผู้ช่วยส่วนตัว พูดสั้น กระชับ สุภาพ เป็นมิตร ใช้ภาษาไทยเป็นหลัก " +
                            "ตอบเป็นข้อความธรรมดาเท่านั้น ห้ามใช้ markdown ห้ามใส่หัวข้อหรือตาราง ห้ามใส่ code block หรือ chart",
                    timeoutMs = 8_000,
                    retryLongerOnTimeout = false
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

    /** ดึง timeframe (นาที) จาก symbol รูปแบบ "XAUUSD@15m" — คืน null ถ้า parse ไม่ได้ */
    private fun symbolTimeframeMin(symbol: String): Int? {
        val tf = symbol.substringAfter('@', "").trim().lowercase()
        if (tf.length < 2) return null
        val n = tf.dropLast(1).toIntOrNull() ?: return null
        return when (tf.last()) {
            'm' -> n
            'h' -> n * 60
            'd' -> n * 1440
            'w' -> n * 10080
            else -> null
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
            data["signal_supervisor"]?.let { put("supervisor", it) }
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
        if (data["signal_side"] == null) {   // keyzone watch — ยังไม่มีสัญญาณเทรด
            val symK = job.symbol.substringBefore("@")
            val symThK = when (symK.uppercase()) {
                "XAUUSD" -> "ทองคำ"; "XAGUSD" -> "เงินแท่ง"
                "BTCUSDT", "BTCUSD" -> "บิทคอยน์"; "ETHUSDT", "ETHUSD" -> "อีเทอเรียม"
                else -> symK
            }
            return "ราคา $symThK เคลื่อนไปแตะจุดสำคัญของโครงสร้างตลาด โปรดติดตามกราฟ"
        }
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

    // ─── AI Strategy Supervisor (2026-08-28) ────────────────────────────────
    // Unified SMC ส่ง SIGNAL+CONTEXT(5TF) → AI ตัดสินก่อนแจ้งผู้ใช้:
    //   APPROVE = ผ่าน | VETO = คัดออก (shadow-record) | ADJUST = ปรับ SL/TP ตามโครงสร้าง
    // ออกแบบให้ engine คำนวณโครงสร้างแบบ deterministic (MarketContextDigest) —
    // AI แค่อ่าน digest แล้วตัดสิน ไม่ต้องเดาตัวเลขเอง (ลด hallucination + โทเคน)

    private data class SupervisorResult(
        val decision: String,                 // APPROVE | VETO | ADJUST
        val adjSl: Double?, val adjTp: Double?,
        val confidence: Int,                  // 0-100
        val reasonTh: String
    )

    /** เรียก AI ตัดสินสัญญาณ — คืน null ถ้า AI ล้มเหลว/ตอบผิดรูปแบบ (caller fallback เป็น flow เดิม) */
    private suspend fun runStrategySupervisor(job: AlertJob, data: Map<String, String>): SupervisorResult? {
        val prompt = buildString {
            appendLine("บทบาท: คุณคือ AI Strategy Supervisor — คัดกรองสัญญาณเทรดก่อนส่งถึงผู้ใช้ โดยเทียบสัญญาณกับโครงสร้างตลาด 5 ไทม์เฟรม")
            appendLine()
            appendLine("══ สัญญาณ ══")
            appendLine("${data["signal_side"]} ${job.symbol} | กลยุทธ์: ${data["signal_strategy"]}")
            appendLine("Entry ${data["signal_entry"]} | SL ${data["signal_sl"]} | TP ${data["signal_tp"]} | RR 1:${data["signal_rr"]} | ATR14 ${data["signal_atr"] ?: "-"}")
            appendLine("เงื่อนไขที่เกิดสัญญาณ: ${data["signal_reason"]}")
            appendLine()
            appendLine("══ โครงสร้างตลาด 5 ไทม์เฟรม (คำนวณ deterministic จากแท่งเทียนจริง) ══")
            appendLine(data["signal_mtf_context"]?.takeIf { it.isNotBlank() } ?: (data["signal_context"] ?: "-"))
            appendLine()
            appendLine("แนวทางตัดสิน:")
            appendLine("- VETO เมื่อสัญญาณขัดโครงสร้างหลักชัดเจน เช่น BUY ขณะ H1/H4 เป็น DOWN, ราคาติด Supply/แนวต้านสำคัญพอดี, หรือเข้า BUY โซน PREMIUM สุดขอบ / SELL โซน DISCOUNT สุดขอบ")
            appendLine("- ADJUST เมื่อสัญญาณสอดคล้องโครงสร้าง แต่ SL/TP วางไม่สอดคล้องกับระดับจริง — ย้ายไปอ้างอิงโครงสร้าง (BUY: SL ใต้ Demand/swing low ใกล้สุด, TP ใต้ Supply/swing high ใกล้สุด; SELL กลับด้าน) โดย RR ต้องไม่ต่ำกว่า 1.0")
            appendLine("- APPROVE เมื่อสัญญาณสอดคล้องและ SL/TP สมเหตุสมผลแล้ว")
            appendLine("ใช้เฉพาะข้อมูลด้านบน ห้ามสมมติข่าว/ตัวเลขอื่น และห้ามรับประกันผลกำไร")
            appendLine()
            appendLine("ตอบตามรูปแบบนี้เท่านั้น 5 บรรทัด ห้ามมีข้อความอื่น:")
            appendLine("DECISION: APPROVE หรือ VETO หรือ ADJUST")
            appendLine("ADJUST_SL: ราคาใหม่ หรือ -")
            appendLine("ADJUST_TP: ราคาใหม่ หรือ -")
            appendLine("CONFIDENCE: ตัวเลข 0-100")
            appendLine("REASON_TH: เหตุผลภาษาไทยสั้นๆ 1-2 ประโยค")
        }
        val waitStart = System.currentTimeMillis()
        alertAiMutex.lock()
        val raw = try {
            val waitedMs = System.currentTimeMillis() - waitStart
            if (waitedMs > 0L) logDebug("AutomationService", "🧑‍✈️ Supervisor queued — waited ${waitedMs}ms")
            generateAiText(prompt)
        } finally {
            alertAiMutex.unlock()
        } ?: return null
        // generateAiText บีบ whitespace เป็นบรรทัดเดียว (stripCodeFences) → parse ด้วย regex ไม่ใช่แยกบรรทัด
        val decisionRaw = Regex("DECISION:\\s*(\\w+)", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.uppercase() ?: return null
        val decision = when {
            decisionRaw.contains("VETO") -> "VETO"
            decisionRaw.contains("ADJUST") -> "ADJUST"
            else -> "APPROVE"
        }
        fun priceField(name: String): Double? =
            Regex("$name:\\s*([0-9][0-9,]*\\.?[0-9]*)", RegexOption.IGNORE_CASE).find(raw)
                ?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val conf = Regex("CONFIDENCE:\\s*(\\d{1,3})", RegexOption.IGNORE_CASE).find(raw)
            ?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100) ?: 50
        val reason = Regex("REASON_TH:\\s*(.+)$", RegexOption.IGNORE_CASE).find(raw)
            ?.groupValues?.get(1)?.trim()?.take(300)?.takeIf { it.isNotBlank() } ?: "ผ่านเกณฑ์ Supervisor"
        return SupervisorResult(decision, priceField("ADJUST_SL"), priceField("ADJUST_TP"), conf, reason)
    }

    /** ตรวจความถูกต้องของ SL/TP ที่ Supervisor เสนอ — ผ่านเท่านั้นถึงใช้แทนค่า engine */
    private fun validateSupervisorAdjustment(data: Map<String, String>, sup: SupervisorResult): Map<String, String>? {
        val entry = data["signal_entry"]?.replace(",", "")?.toDoubleOrNull() ?: return null
        val sl0 = data["signal_sl"]?.replace(",", "")?.toDoubleOrNull() ?: return null
        val tp0 = data["signal_tp"]?.replace(",", "")?.toDoubleOrNull() ?: return null
        val side = data["signal_side"] ?: return null
        val atr = data["signal_atr"]?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val sl = sup.adjSl ?: sl0
        val tp = sup.adjTp ?: tp0
        if (sl == sl0 && tp == tp0) return null                      // ไม่ได้เปลี่ยนอะไรจริง
        val sideOk = if (side == "BUY") sl < entry && tp > entry else sl > entry && tp < entry
        if (!sideOk) return null
        val risk = kotlin.math.abs(entry - sl)
        if (risk <= 0) return null
        val rr = kotlin.math.abs(tp - entry) / risk
        if (rr < 1.0) return null                                   // RR ต่ำกว่า 1 ไม่รับ
        if (atr > 0) {
            if (risk < 0.2 * atr || risk > 6.0 * atr) return null   // SL แคบ/กว้างผิดปกติ
            if (kotlin.math.abs(tp - entry) > 10.0 * atr) return null
        }
        fun fmt(v: Double) = if (kotlin.math.abs(v) >= 100) "%.2f".format(v) else "%.4f".format(v)
        return mapOf(
            "signal_sl" to fmt(sl),
            "signal_tp" to fmt(tp),
            "signal_rr" to "%.2f".format(rr)
        )
    }

    /** การ์ด "ราคาแตะจุดสำคัญ" — keyzone watch (ยังไม่มีสัญญาณเทรด) */
    private fun buildKeyzoneChatCard(job: AlertJob, data: Map<String, String>, aiSummary: String?): String =
        buildString {
            appendLine("📍 **${job.symbol} แตะจุดสำคัญของโครงสร้างตลาด**")
            appendLine()
            appendLine("**จุดที่แตะ:** ${data["signal_keyzone_desc"]?.ifBlank { "-" } ?: "-"}")
            val mtf = data["signal_mtf_context"]?.takeIf { it.isNotBlank() }
            if (mtf != null) {
                appendLine()
                appendLine(mtf)
            }
            if (!aiSummary.isNullOrBlank()) {
                appendLine()
                appendLine("**JARVIS quick-check:** $aiSummary")
            }
        }.trim()
    // ─── Natural Voice (Gemini TTS) — เสียงแจ้งเตือนแบบคน ไม่ใช่ TTS หุ่นยนต์ ────
    // เหตุผล: Android TTS (th-TH) บนเครื่องส่วนใหญ่สะกดคำอังกฤษทีละตัว (เช่น R-e-v-e-r-s-a-l)
    // และเสียงแข็ง — ใช้ Gemini TTS (gemini-2.5-flash-preview-tts) เป็นหลัก, fallback เป็น Android TTS

    // ─── Alert Voice Chain (2026-08-15 หลังทดสอบ 4 engines บน 2 เครื่อง) ───────
    // ตัด Gemini TTS one-shot ออก (ติดโควต้า + ดีเลย์คงที่ ~14 วิ) — เหลือ 2 โหมด:
    //   device → Android TTS ทันที/ไม่จำกัด
    //   live   → Gemini Live API chain → Android TTS
    // ค่าเก่าที่เคย persist ("ai"/"live31"/"live25") ถือเป็น "live" ทั้งหมด
    // chain เรียงตามความเสถียรที่วัดจริง: 2.5 Native (11/11) → 3.1 (TRANSCRIPT_ONLY 85%)
    // (2026-08-26 — เดิมเริ่มจากโมเดลที่ผู้ใช้เลือกใน Settings แต่ 3.1 มั่วเกินไปสำหรับ alert)

    private val LIVE_VOICE_MODELS = listOf(
        "gemini-2.5-flash-native-audio-preview-12-2025" to "Live 2.5 Native",
        "gemini-3.1-flash-live-preview" to "Live 3.1"
    )

    /** Live model ที่เพิ่งส่ง transcript สำเร็จแต่ไม่มี audio — ข้ามชั่วคราวเพื่อไม่ให้ทุก alert เสียเวลา fallback ซ้ำ */
    private val liveModelAudioCooldownUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val liveAudioFailureCooldownMs = 60_000L

    private fun isLiveModelAudioCoolingDown(model: String): Boolean =
        (liveModelAudioCooldownUntil[model] ?: 0L) > System.currentTimeMillis()

    /** chain โมเดลเสียงแจ้งเตือน: เริ่มจากโมเดลที่เสถียรสุดสำหรับ alert ก่อน
     *
     * 2026-08-26 — สลับให้ 2.5 Native ขึ้นก่อน 3.1: จาก log 06:24–10:03 (14 alert)
     * 3.1 ตอบ TRANSCRIPT_ONLY (text ล้วน ไม่มี audio) ถึง 12/14 (85%) ทำให้ทุก alert
     * เสียเวลา ~5–7s กับ 3.1 สองรอบ ก่อนจบที่ 2.5 เสมอ ส่วน 2.5 Native เมื่อได้ลอง
     * สำเร็จ 11/11 (100%) — จึงให้ตัวที่พร้อมสุดพูดก่อน แม้ 2.5 จะเป็นรุ่น deprecated
     * (ยังใช้งานได้จนกว่า Google จะปิดจริง) ส่วน 3.1 คงไว้เป็น fallback
     * หมายเหตุ: เสียงต่างกันเล็กน้อยจากโหมด Live chat ถ้าผู้ใช้เลือก 3.1 ไว้ — trade-off ที่ยอมรับ
     * เพื่อแลกกับ latency ของ alert
     *
     * ข้ามโมเดลที่เพิ่งตอบ transcript ได้โดยไม่มี audio ชั่วคราว เพื่อไม่ให้
     * Signal Alert ทุกครั้งเสียเวลาไปกับ fallback ที่รู้ว่าไม่พร้อม
     */
    private fun liveVoiceChain(): List<Pair<String, String>> {
        val selected = setting("live_model_name").removePrefix("models/").ifBlank {
            "gemini-3.1-flash-live-preview"
        }
        // ลำดับความเสถียรจากการวัดจริง: 2.5 Native ก่อน → 3.1 → โมเดลที่ผู้ใช้เลือก (ถ้าไม่ใช่ 2 ตัวนี้) ต่อท้าย
        val candidates = LIVE_VOICE_MODELS.map { it.first } +
            selected.takeIf { sel -> LIVE_VOICE_MODELS.none { it.first == sel } }.let { listOfNotNull(it) }
        val available = candidates.filterNot(::isLiveModelAudioCoolingDown)
        val effective = if (available.isNotEmpty()) available else candidates
        return effective.map { model ->
            val label = LIVE_VOICE_MODELS.firstOrNull { it.first == model }?.second ?: model
            model to label
        }
    }

    private fun isLiveEngine(engine: String): Boolean =
        engine == "live" || engine == "ai" || engine == "live31" || engine == "live25"

    /**
     * พูดข้อความแจ้งเตือน — device → Android TTS ทันที | live → ไล่ chain Live 2.5 Native → Live 3.1 → Android TTS
     * Live พูดข้อความยาวเต็ม (fullText), device/fallback พูดข้อความสั้น
     * @param onVoiceStart เรียกครั้งเดียวเมื่อรู้ว่า engine ไหนพูดจริง — ใช้ใส่ footer "เสียง:" ในการ์ดแชท
     */
    /**
     * Enqueue voice delivery without making the caller wait on a Live mutex.
     * The caller still awaits the delivery result so Live Summary chat cards keep
     * using the exact transcript produced by the audio session.
     */
    private suspend fun speakAlert(
        shortText: String,
        fullText: String? = null,
        liveSummary: Boolean = false,
        timeframeMin: Int? = null
    ): VoiceDeliveryResult {
        val engine = setting("alert_voice_engine").ifBlank { "device" }
        if (!isLiveEngine(engine)) {
            return speakAlertNow(shortText, fullText, liveSummary, timeframeMin)
        }

        val result = CompletableDeferred<VoiceDeliveryResult>()
        val priority = when {
            timeframeMin == null -> 10
            timeframeMin <= 1 -> 100
            timeframeMin <= 5 -> 90
            timeframeMin <= 15 -> 80
            timeframeMin <= 30 -> 70
            else -> 60
        }
        val request = liveAlertSchedulerMutex.withLock {
            liveAlertSchedulerSequence++
            LiveAlertRequest(
                priority = priority,
                sequence = liveAlertSchedulerSequence,
                shortText = shortText,
                fullText = fullText,
                liveSummary = liveSummary,
                timeframeMin = timeframeMin,
                result = result
            ).also { liveAlertSchedulerQueue.add(it) }
        }
        val queueSize = liveAlertSchedulerMutex.withLock { liveAlertSchedulerQueue.size }
        logDebug(
            "AutomationService",
            "🔊 Live Scheduler ENQUEUE priority=${request.priority} tf=${request.timeframeMin ?: "-"} " +
                "queue=$queueSize seq=${request.sequence}"
        )
        ensureLiveAlertScheduler()
        liveAlertSchedulerWake.trySend(Unit)
        return result.await()
    }

    private fun ensureLiveAlertScheduler() {
        if (liveAlertSchedulerJob?.isActive == true) return
        liveAlertSchedulerJob = scope.launch {
            while (isActive) {
                liveAlertSchedulerWake.receive()
                while (isActive) {
                    val request = liveAlertSchedulerMutex.withLock {
                        if (liveAlertSchedulerQueue.isEmpty()) null
                        else liveAlertSchedulerQueue.removeAt(
                            liveAlertSchedulerQueue.indices.maxWithOrNull(compareBy<Int> { liveAlertSchedulerQueue[it].priority }.thenByDescending { liveAlertSchedulerQueue[it].sequence }) ?: return@withLock null
                        )
                    } ?: break

                    val remaining = liveAlertSchedulerMutex.withLock { liveAlertSchedulerQueue.size }
                    logDebug(
                        "AutomationService",
                        "🔊 Live Scheduler DISPATCH priority=${request.priority} tf=${request.timeframeMin ?: "-"} " +
                            "remaining=$remaining seq=${request.sequence}"
                    )
                    try {
                        request.result.complete(
                            speakAlertNow(
                                request.shortText,
                                request.fullText,
                                request.liveSummary,
                                request.timeframeMin
                            )
                        )
                    } catch (e: CancellationException) {
                        request.result.cancel(e)
                        throw e
                    } catch (e: Exception) {
                        logError("AutomationService", "🔊 Live Scheduler request failed: ${e.message}", e)
                        request.result.complete(VoiceDeliveryResult("Android TTS (scheduler error)", null))
                    }
                }
            }
        }
    }

    private suspend fun speakAlertNow(
        shortText: String,
        fullText: String? = null,
        liveSummary: Boolean = false,
        timeframeMin: Int? = null
    ): VoiceDeliveryResult {
        val engine = setting("alert_voice_engine").ifBlank { "device" }
        val live = isLiveEngine(engine)
        val short = sanitizeForSpeech(shortText)
        val full = sanitizeForSpeech(fullText ?: shortText)
        logDebug("AutomationService", "🔊 speakAlert engine=$engine ttsReady=$ttsReady liveSummary=$liveSummary")
        if (!live) {
            if (short.isBlank()) return VoiceDeliveryResult("-", null)
            speak(short)
            logDebug("AutomationService", "🔊 → Android TTS (device mode) spoken")
            return VoiceDeliveryResult("Android TTS", null)
        }
        if (full.isBlank()) return VoiceDeliveryResult("-", null)
        val apiKey = setting("api_key")
        if (apiKey.isBlank()) {
            logDebug("AutomationService", "🔊 ไม่มี api_key → Android TTS")
            if (short.isNotBlank()) speak(short)
            return VoiceDeliveryResult("Android TTS (ไม่มี api_key)", null)
        }

        val voiceName = setting("voice_name").ifBlank { "Aoede" }
        val chain = liveVoiceChain()
        logDebug("AutomationService", "🔊 Live chain: ${chain.joinToString(" → ") { it.second }} (2.5 Native ก่อน — เสถียรสุดจากการวัดจริง)")

        // API key pool — โควต้า/ความจุของ Live API ผูกกับ key; หมุน key ทุก attempt
        // (เคสจริง 2026-08-26 02:00: 3.1 มั่ว 2 รอบ + 2.5 ตาย บน key เดียวกันหมด
        //  ถ้า session ตายเพราะ key นั้นติดเพดาน การเปลี่ยน key มีโอกาสรอดทันที)
        val apiKeys = buildList {
            add(apiKey)
            setting("gemini_api_keys").lines().map { it.trim() }
                .filter { it.isNotBlank() && it != apiKey && it !in this }
                .forEach { add(it) }
        }
        if (apiKeys.size > 1) {
            logDebug("AutomationService", "🔊 Live voice key pool = ${apiKeys.size} keys — หมุนทุก attempt")
        }
        var keyAttempt = 0

        // The scheduler already guarantees one Live session at a time. Keeping the
        // session implementation free of queue waiting is important: a second signal
        // is represented by a scheduler item instead of a coroutine parked on a mutex.
        logDebug("AutomationService", "🔊 Live Scheduler dispatch → session start")
        try {
            for ((index, pair) in chain.withIndex()) {
                val (model, label) = pair
                val tag = if (index == 0) label else "$label (fallback)"
                // โมเดล Live ตัว preview มั่วเป็นบาง turn: session config เดียวกันเป๊ะ
                // บางครั้งได้ audio ปกติ บางครั้งตอบ transcript ล้วนโดยไม่มี audio เลย
                // (log 2026-08-25: 3.1 TRANSCRIPT_ONLY 5 ครั้ง สลับกับ AUDIO_SUCCESS 2 ครั้ง)
                // → ถ้าเจอ TRANSCRIPT_ONLY (ตอบ text จบไวใน ~3s) ให้ retry โมเดลเดิมอีก 1 ครั้ง
                //   ด้วย session ใหม่ก่อน ค่อย fallback ไปโมเดลถัดไป — ถูกกว่าเสีย chain ทั้งรอบ
                var result: LiveSpeechResult
                var attempt = 0
                while (true) {
                    attempt++
                    val attemptKey = apiKeys[keyAttempt % apiKeys.size]
                    keyAttempt++
                    result = try {
                        speakViaLive(attemptKey, model, full, voiceName, liveSummary)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        logError("AutomationService", "🔊 Live ($model) exception: ${e.message}", e)
                        LiveSpeechResult(false, null)
                    }
                    if (result.ok) break
                    if (result.cause == "TRANSCRIPT_ONLY" && attempt < 2) {
                        logDebug(
                            "AutomationService",
                            "🔊 Live ($model) TRANSCRIPT_ONLY → retry โมเดลเดิมด้วย session ใหม่ (ครั้งที่ ${attempt + 1}/2)"
                        )
                        continue
                    }
                    break
                }
                if (result.ok) {
                    liveModelAudioCooldownUntil.remove(model)
                    return VoiceDeliveryResult(tag, result.transcript)
                }
                if (!result.transcript.isNullOrBlank()) {
                    liveModelAudioCooldownUntil[model] =
                        System.currentTimeMillis() + liveAudioFailureCooldownMs
                    logDebug(
                        "AutomationService",
                        "🔊 Live ($model) ได้ transcript แต่ไม่มี audio → cooldown ${liveAudioFailureCooldownMs / 1000}s"
                    )
                }
                logDebug(
                    "AutomationService",
                    "🔊 Live ($model) ไม่ได้เสียง → cause=${result.cause}; " +
                        "transcriptChars=${result.transcript?.length ?: 0} → ลองตัวถัดไปใน chain"
                )
            }
        } finally {
            // Physical audio serialization is owned by Live Alert Scheduler.
        }
        logDebug("AutomationService", "🔊 Live chain พังทั้งหมด → Android TTS")
        if (short.isNotBlank()) speak(short)
        return VoiceDeliveryResult("Android TTS (fallback)", null)
    }

    /**
     * พูดผ่าน Gemini Live API (websocket BidiGenerateContent) แบบ one-shot —
     * เปิด session → ส่งข้อความเป็น clientContent → รับ audio chunk แล้วเล่นทันทีแบบ streaming
     * (ได้ยินเสียงตั้งแต่ chunk แรก ไม่ต้องรอ synthesize ครบเหมือน one-shot TTS — เหตุที่ Live ไม่ดีเลย์ในโหมดสนทนา)
     * คืน true ถ้าได้เสียงเล่นจริง
     */
    private suspend fun speakViaLive(
        apiKey: String,
        model: String,
        text: String,
        voiceName: String = "Aoede",
        liveSummary: Boolean = false
    ): LiveSpeechResult {
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
        val transcriptBuilder = StringBuilder()
        var lastLoggedTranscriptChars = 0
        val t0 = System.currentTimeMillis()
        var setupAt = 0L
        var requestAt = 0L
        var firstChunkAt = 0L
        var firstTranscriptAt = 0L
        var turnCompleteAt = 0L
        var lastProgressAt = 0L
        var diagnosticPhase = "CONNECTING"
        var timeoutCause = "NONE"
        var apiErrorMessage: String? = null
        val sessionId = "${model.substringAfterLast('/').take(28)}-${t0 % 100000}"
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
            val theTrack = track
            val player = scope.async(Dispatchers.IO) {
                try {
                    theTrack.play()
                    while (!done.get() || queue.isNotEmpty()) {
                        val chunk = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                        theTrack.write(chunk, 0, chunk.size)
                    }
                    // The queue can be empty while AudioTrack still has PCM buffered.
                    // Releasing the track immediately can truncate the last syllables.
                    val expectedFrames = totalBytes / 2L // PCM 16-bit, mono
                    val drainDeadline = System.currentTimeMillis() + 4_000L
                    while (expectedFrames > 0L &&
                        (theTrack.playbackHeadPosition.toLong() and 0xFFFF_FFFFL) < expectedFrames &&
                        System.currentTimeMillis() < drainDeadline
                    ) {
                        delay(20L)
                    }
                } catch (e: Exception) {
                    logError("AutomationService", "🔊 Live playback error: ${e.message}", e)
                }
            }
            withTimeoutOrNull(liveVoiceSessionTimeoutMs) {
                try {
                    geminiClient.webSocket(url) {
                        val wsConnectedAt = System.currentTimeMillis()
                        diagnosticPhase = "SETUP_SENT"
                        logDebug(
                            "AutomationService",
                            "🔊 Live[$sessionId] WS_CONNECTED at=${wsConnectedAt - t0}ms → setup sent model=$model voice=$voiceName " +
                                "key=…${apiKey.takeLast(4)} timeout=${liveVoiceSessionTimeoutMs}ms"
                        )
                        val isLive31 = model.contains("3.1-flash-live")
                        val setup = LiveSetupMessage(setup = LiveSetup(
                            model = if (model.startsWith("models/")) model else "models/$model",
                            outputAudioTranscription = JsonObject(emptyMap()),
                            systemInstruction = LiveSystemInstruction(parts = listOf(
                                LivePart(text = com.example.personalaibot.ai.JarvisPersona.CORE_IDENTITY),
                                LivePart(text = if (liveSummary) {
                                    """
                                    [LIVE SIGNAL ALERT MODE]
                                    คุณกำลังทำหน้าที่เป็นเสียงแจ้งเตือนสัญญาณเทรดของ JARVIS ไม่ใช่นักวิเคราะห์แบบเต็มรูปแบบ
                                    วิเคราะห์ payload ที่ผู้ใช้ส่งมาแล้วพูดสรุปเป็นภาษาไทยให้กระชับ ชัดเจน และฟังจบเร็ว
                                    จำกัดคำตอบให้สั้นมาก: 1-2 ประโยค ประมาณ 20-35 คำ และไม่เกินราว 120 ตัวอักษรในภาษาไทยเท่าที่ทำได้ เพื่อให้เสียงแจ้งเตือนจบเร็ว
                                    ต้องกล่าวให้ครบเท่าที่ข้อมูลมี: Signal/Strategy และ Entry/SL/TP; ถ้าข้อมูลยาวเกิน ให้ตัด Context/Risk ก่อน แต่ห้ามตัดราคาและทิศทาง
                                    หากข้อมูลไม่ครบ ให้พูดเฉพาะข้อมูลที่มี ห้ามเดา ห้ามเพิ่มราคา อินดิเคเตอร์ ข่าว หรือเหตุการณ์ตลาด
                                    ห้ามรับประกันผลกำไร ไม่ต้องอธิบายเหตุผลเชิงลึก ไม่ต้องมีคำเกริ่น คำลงท้าย หรือคำเชิญให้ทำสิ่งอื่น
                                    จบคำตอบทันทีหลังข้อมูลสำคัญครบ
                                    พูดเป็นภาษาพูดต่อเนื่อง ไม่มี Markdown ไม่มีตาราง ไม่มี bullet และไม่พูดถึงกระบวนการคิดภายใน
                                    """.trimIndent()
                                } else {
                                    "[STRICT] หน้าที่ตอนนี้คืออ่านข้อความแจ้งเตือนที่ได้รับออกเสียงเป็นภาษาไทยตรงๆ ด้วยน้ำเสียงและบุคลิกข้างต้น ห้ามเพิ่มเติม ห้ามตอบโต้ ห้ามเรียกเครื่องมือ"
                                })
                            )),
                            generationConfig = LiveGenerationConfig(
                                responseModalities = listOf("AUDIO"),
                                thinkingConfig = if (isLive31 && liveSummary) {
                                    com.example.personalaibot.data.LiveThinkingConfig(thinkingLevel = "low", includeThoughts = false)
                                } else null,
                                speechConfig = LiveSpeechConfig(
                                    voiceConfig = LiveVoiceConfig(
                                        prebuiltVoiceConfig = LivePrebuiltVoiceConfig(voiceName = voiceName)
                                    )
                                )
                            )
                        ))
                        val setupJson = json.encodeToString(LiveSetupMessage.serializer(), setup)
                        // log setup จริงที่ส่งไป (ตัดย่อ) — ป้องกันเคส config หลุด/field ผิดแล้วไม่มีหลักฐาน
                        logDebug(
                            "AutomationService",
                            "🔊 Live[$sessionId] setupJson(${setupJson.length}) = ${setupJson.take(600)}"
                        )
                        send(Frame.Text(setupJson))
                        var sentText = false
                        var setupWatchdog: kotlinx.coroutines.Job? = launch(Dispatchers.IO) {
                            delay(liveVoiceSetupTimeoutMs)
                            if (!sentText) {
                                timeoutCause = "SETUP_TIMEOUT"
                                diagnosticPhase = "SETUP_TIMEOUT"
                                logDebug(
                                    "AutomationService",
                                    "🔊 Live[$sessionId] SETUP_TIMEOUT ${liveVoiceSetupTimeoutMs}ms → close; likely API/network (no setupComplete)"
                                )
                                close()
                            }
                        }
                        var audioWatchdog: kotlinx.coroutines.Job? = null
                        var audioIdleWatchdog: kotlinx.coroutines.Job? = null
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
                                apiErrorMessage = it.message
                                timeoutCause = "API_ERROR"
                                diagnosticPhase = "API_ERROR"
                                logError(
                                    "AutomationService",
                                    "🔊 Live[$sessionId] API_ERROR phase=$diagnosticPhase elapsed=${System.currentTimeMillis() - t0}ms message=${it.message}",
                                    null
                                )
                                shouldClose = true
                            }
                            if (msg.setupComplete != null && !sentText) {
                                sentText = true
                                setupAt = System.currentTimeMillis()
                                diagnosticPhase = "READY"
                                
                                setupWatchdog?.cancel()
                                logDebug("AutomationService", "🔊 Live[$sessionId] READY total=${setupAt - t0}ms afterWs=${setupAt - wsConnectedAt}ms → ส่ง ${if (liveSummary) "Signal Summary" else "ข้อความ"}")
                                lastProgressAt = System.currentTimeMillis()
                                audioWatchdog = launch(Dispatchers.IO) {
                                    delay(liveVoiceFirstOutputTimeoutMs)
                                    if (!gotAudio) {
                                        timeoutCause = if (transcriptBuilder.isNotEmpty()) "FIRST_AUDIO_TIMEOUT_TRANSCRIPT_ONLY" else "FIRST_AUDIO_TIMEOUT_NO_OUTPUT"
                                        diagnosticPhase = "FIRST_AUDIO_TIMEOUT"
                                        logDebug(
                                            "AutomationService",
                                            "🔊 Live[$sessionId] FIRST_AUDIO_TIMEOUT ${liveVoiceFirstOutputTimeoutMs}ms after READY; " +
                                                "transcriptChars=${transcriptBuilder.length}; " +
                                                "requestAge=${if (requestAt > 0) System.currentTimeMillis() - requestAt else -1}ms; " +
                                                "likely=${if (transcriptBuilder.isNotEmpty()) "API_AUDIO_OUTPUT_STALL" else "API_GENERATION_OR_NETWORK_STALL"} → close"
                                        )
                                        close()
                                    }
                                }
                                if (isLive31) {
                                    // Gemini 3.1 Live ใช้ realtimeInput สำหรับข้อความระหว่าง session
                                    val realtime = com.example.personalaibot.data.LiveRealtimeInputMessage(
                                        realtimeInput = com.example.personalaibot.data.LiveRealtimeInputData(text = text)
                                    )
                                    send(Frame.Text(json.encodeToString(com.example.personalaibot.data.LiveRealtimeInputMessage.serializer(), realtime)))
                                } else {
                                    val cc = LiveClientContentMessage(clientContent = LiveContentWrapper(
                                        turns = listOf(LiveTurn(role = "user", parts = listOf(LivePart(text = text)))),
                                        turnComplete = true
                                    ))
                                    send(Frame.Text(json.encodeToString(LiveClientContentMessage.serializer(), cc)))
                                }
                                requestAt = System.currentTimeMillis()
                                diagnosticPhase = "REQUEST_SENT"
                                logDebug(
                                    "AutomationService",
                                    "🔊 Live[$sessionId] REQUEST_SENT total=${requestAt - t0}ms afterReady=${requestAt - setupAt}ms payloadChars=${text.length} modality=AUDIO"
                                )
                                continue
                            }
                            val sc = msg.serverContent
                            if (sc != null) {
                                // Gemini 3.1 อาจส่ง audio + transcript ใน server event เดียวกัน
                                sc.modelTurn?.parts?.forEach { p ->
                                    p.inlineData?.takeIf { it.mimeType.contains("audio") }?.let { blob ->
                                        val pcmChunk = android.util.Base64.decode(blob.data, android.util.Base64.DEFAULT)
                                        if (firstChunkAt == 0L) {
                                            firstChunkAt = System.currentTimeMillis()
                                            lastProgressAt = firstChunkAt
                                            audioWatchdog?.cancel()
                                            audioIdleWatchdog?.cancel()
                                            audioIdleWatchdog = launch(Dispatchers.IO) {
                                                while (isActive && !done.get()) {
                                                    delay(2_000L)
                                                    val idleMs = System.currentTimeMillis() - lastProgressAt
                                                    if (gotAudio && idleMs >= liveVoiceAudioIdleTimeoutMs) {
                                                        timeoutCause = "AUDIO_STREAM_IDLE_TIMEOUT"
                                                        diagnosticPhase = "AUDIO_STREAM_STALLED"
                                                        logDebug(
                                                            "AutomationService",
                                                            "🔊 Live[$sessionId] AUDIO_STREAM_IDLE_TIMEOUT ${liveVoiceAudioIdleTimeoutMs}ms; " +
                                                                "lastProgress=${idleMs}ms audioBytes=$totalBytes transcriptChars=${transcriptBuilder.length} → close"
                                                        )
                                                        close()
                                                        break
                                                    }
                                                }
                                            }
                                            logDebug("AutomationService", "🔊 Live first audio chunk (${firstChunkAt - t0}ms)")
                                        }
                                        lastProgressAt = System.currentTimeMillis()
                                        queue.add(pcmChunk)
                                        totalBytes += pcmChunk.size
                                        gotAudio = true
                                        diagnosticPhase = "AUDIO_STREAMING"
                                    }
                                }
                                sc.outputTranscription?.text?.let { transcript ->
                                    if (transcript.isNotBlank()) {
                                        if (firstTranscriptAt == 0L) {
                                            firstTranscriptAt = System.currentTimeMillis()
                                            diagnosticPhase = if (gotAudio) "AUDIO_TRANSCRIPT" else "TRANSCRIPT_NO_AUDIO"
                                            logDebug(
                                                "AutomationService",
                                                "📝 Live[$sessionId] first transcript (${firstTranscriptAt - t0}ms, audio=$gotAudio)"
                                            )
                                        }
                                        transcriptBuilder.append(transcript)
                                        if (liveSummary && gotAudio && transcriptBuilder.length >= liveVoiceSummaryCharCap) {
                                            timeoutCause = "AUDIO_RESPONSE_LENGTH_CAP"
                                            diagnosticPhase = "AUDIO_LENGTH_CAPPED"
                                            logDebug("AutomationService", "🔊 Live[$sessionId] RESPONSE_LENGTH_CAP ${liveVoiceSummaryCharCap} chars; audioBytes=$totalBytes → close")
                                            shouldClose = true
                                        }
                                        lastProgressAt = System.currentTimeMillis()
                                        // Keep logcat readable: transcript is still accumulated in full,
                                        // but operational logs emit only meaningful progress checkpoints.
                                        val transcriptChars = transcriptBuilder.length
                                        if (transcriptChars - lastLoggedTranscriptChars >= 50 ||
                                            transcriptChars >= liveVoiceSummaryCharCap ||
                                            transcriptChars == transcript.length) {
                                            logDebug("AutomationService", "📝 Live transcript progress chars=$transcriptChars audio=$gotAudio")
                                            lastLoggedTranscriptChars = transcriptChars
                                        }
                                    }
                                }
                                if (sc.turnComplete == true) {
                                    audioIdleWatchdog?.cancel()
                                    turnCompleteAt = System.currentTimeMillis()
                                    diagnosticPhase = "TURN_COMPLETE"
                                    logDebug("AutomationService", "🔊 Live[$sessionId] turnComplete (${turnCompleteAt - t0}ms)")
                                    shouldClose = true
                                }
                            }
                            if (shouldClose) { close(); break }
                        }
                        setupWatchdog?.cancel()
                        audioWatchdog?.cancel()
                        audioIdleWatchdog?.cancel()
                        val reason = kotlinx.coroutines.withTimeoutOrNull(1_500L) { closeReason.await() }
                        if (reason == null) {
                            logDebug(
                                "AutomationService",
                                "🔊 Live[$sessionId] CLOSE_REASON_TIMEOUT 1500ms → force fallback " +
                                    "phase=$diagnosticPhase cause=$timeoutCause " +
                                    "(${System.currentTimeMillis() - t0}ms, gotAudio=$gotAudio, transcriptChars=${transcriptBuilder.length})"
                            )
                        } else {
                            logDebug(
                                "AutomationService",
                                "🔊 Live[$sessionId] ws closed: code=${reason.code} reason=${reason.message} " +
                                    "phase=$diagnosticPhase cause=$timeoutCause " +
                                    "(${System.currentTimeMillis() - t0}ms, setup=${if (setupAt > 0) setupAt - t0 else -1}ms, " +
                                    "request=${if (requestAt > 0) requestAt - t0 else -1}ms, " +
                                    "firstAudio=${if (firstChunkAt > 0) firstChunkAt - t0 else -1}ms, " +
                                    "firstTranscript=${if (firstTranscriptAt > 0) firstTranscriptAt - t0 else -1}ms, " +
                                    "turnComplete=${if (turnCompleteAt > 0) turnCompleteAt - t0 else -1}ms, " +
                                    "gotAudio=$gotAudio, transcriptChars=${transcriptBuilder.length}, apiError=${apiErrorMessage ?: "-"})"
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    logError("AutomationService", "🔊 Live session failed (${System.currentTimeMillis() - t0}ms): ${e.message}", e)
                }
            } ?: run {
                timeoutCause = "SESSION_MAX_DURATION"
                logDebug(
                    "AutomationService",
                    "🔊 Live[$sessionId] SESSION_MAX_DURATION ${liveVoiceSessionTimeoutMs}ms → force fallback; " +
                        "phase=$diagnosticPhase evidence=" +
                        when {
                            apiErrorMessage != null -> "API_ERROR"
                            setupAt == 0L -> "API_OR_NETWORK_BEFORE_SETUP"
                            !gotAudio && transcriptBuilder.isNotEmpty() -> "API_AUDIO_OUTPUT_STALL"
                            !gotAudio -> "API_GENERATION_OR_NETWORK_STALL"
                            gotAudio && turnCompleteAt == 0L -> "LONG_AUDIO_STREAM_NO_TURN_COMPLETE"
                            else -> "UNKNOWN"
                        } +
                        " setup=${if (setupAt > 0) setupAt - t0 else -1}ms request=${if (requestAt > 0) requestAt - t0 else -1}ms " +
                        "firstAudio=${if (firstChunkAt > 0) firstChunkAt - t0 else -1}ms transcript=${transcriptBuilder.length}"
                )
            }
            done.set(true)
            player.await()
            if (gotAudio) {
                logDebug(
                    "AutomationService",
                    "🔊 → Live ($model) AUDIO_DELIVERED $totalBytes bytes (${System.currentTimeMillis() - t0}ms, " +
                        "first chunk ${if (firstChunkAt > 0) "${firstChunkAt - t0}ms" else "-"}, " +
                        "summaryChars=${transcriptBuilder.length})"
                )
            }
            val finalCause = when {
                timeoutCause != "NONE" -> timeoutCause
                apiErrorMessage != null -> "API_ERROR"
                gotAudio -> "AUDIO_SUCCESS"
                transcriptBuilder.isNotEmpty() -> "TRANSCRIPT_ONLY"
                else -> diagnosticPhase
            }
            logDebug(
                "AutomationService",
                "🔊 Live[$sessionId] RESULT model=$model cause=$finalCause " +
                    "queueHandled=true setup=${if (setupAt > 0) setupAt - t0 else -1}ms " +
                    "request=${if (requestAt > 0) requestAt - t0 else -1}ms " +
                    "generation=${if (requestAt > 0) System.currentTimeMillis() - requestAt else -1}ms " +
                    "firstAudio=${if (firstChunkAt > 0) firstChunkAt - t0 else -1}ms " +
                    "firstTranscript=${if (firstTranscriptAt > 0) firstTranscriptAt - t0 else -1}ms " +
                    "turnComplete=${if (turnCompleteAt > 0) turnCompleteAt - t0 else -1}ms " +
                    "audioBytes=$totalBytes transcriptChars=${transcriptBuilder.length} apiError=${apiErrorMessage ?: "-"}"
            )
            return LiveSpeechResult(gotAudio, transcriptBuilder.toString().trim(), finalCause)
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
        // one-shot announce (ผลงานพื้นหลังเสร็จ เช่น backtest) — ไม่ต้องเข้า loop เฝ้าตลาด
        if (intent?.action == ACTION_LONGTASK_ANNOUNCE) {
            val title = intent.getStringExtra(EXTRA_ANNOUNCE_TITLE) ?: "งานพื้นหลังเสร็จแล้ว"
            val body = intent.getStringExtra(EXTRA_ANNOUNCE_BODY) ?: ""
            val meta = intent.getStringExtra(EXTRA_ANNOUNCE_META) ?: """{"type":"long_task"}"""
            val short = intent.getStringExtra(EXTRA_ANNOUNCE_SHORT) ?: title
            val full = intent.getStringExtra(EXTRA_ANNOUNCE_FULL) ?: short
            logDebug("AutomationService", "📣 LONGTASK_ANNOUNCE: $title (body=${body.length} chars)")
            scope.launch {
                runCatching {
                    sendNotification(title, short, 9000 + (System.currentTimeMillis() % 1000).toInt())
                    deliverChatAndVoice(
                        cardBody = body,
                        metaFor = { voice -> if (voice != null) meta.dropLast(1) + ",\"voice\":\"$voice\"}" else meta },
                        shortSpeech = short,
                        fullSpeech = full
                    )
                }.onFailure { logError("AutomationService", "announce failed: ${it.message}", it) }
                // ไม่มีงานเฝ้าตลาด → ปิดตัวเองหลังประกาศเสร็จ (ถ้ามี job อยู่ loop ปกติจัดการเอง)
            }
            // ยังต้อง startForeground ตามกฎ Android ถ้าถูก start แบบ foreground service — ไหลต่อไปด้านล่าง
        }

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

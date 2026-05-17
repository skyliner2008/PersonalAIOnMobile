package com.example.personalaibot.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.personalaibot.MainActivity
import com.example.personalaibot.automation.*
import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.tools.trading.TradingApiService
import com.example.personalaibot.tools.trading.SmcApiService
import com.example.personalaibot.tools.trading.AdvancedTradingEngine
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.example.personalaibot.db.JarvisDatabase
import com.example.personalaibot.db.AlertJob
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
 *   • Stops the service when there are no active jobs (so we don't burn battery
 *     polling an empty SQLite table forever).
 *   • Backs off polling when the network is offline instead of hammering it
 *     every 60s.
 *   • Closes the HttpClient in onDestroy so OkHttp pools don't leak.
 */
class JarvisAutomationService : Service() {

    private val CHANNEL_ID = "JarvisAutomationChannel"
    private val NOTIFICATION_ID = 99
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var database: JarvisDatabase
    private lateinit var automationManager: AutomationManager
    private lateinit var tradingApi: TradingApiService
    private lateinit var smcApi: SmcApiService
    private lateinit var evaluator: AutomationEvaluator
    private lateinit var advancedEngine: AdvancedTradingEngine

    /** Cycles in a row that returned ANY network error — used for backoff. */
    private var consecutiveNetworkFailures = 0
    /** Cycles in a row with zero active jobs — after a few we self-stop. */
    private var emptyCycleCount = 0
    /** Has startForeground succeeded at least once for this Service instance? */
    private var foregroundStarted = false

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize DB and Managers
        val driver = AndroidSqliteDriver(JarvisDatabase.Schema, applicationContext, "jarvis_bot.db")
        database = JarvisDatabase(driver)
        automationManager = AutomationManager(database)
        tradingApi = TradingApiService(client)
        smcApi = SmcApiService(client)
        evaluator = AutomationEvaluator()
        advancedEngine = AdvancedTradingEngine(smcApi)

        startLoop()
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

        // No active jobs → don't keep a foreground notification alive forever.
        // Stop after 5 consecutive empty cycles so we don't immediately re-start
        // if the user is rapidly toggling alerts.
        if (jobs.isEmpty()) {
            emptyCycleCount++
            if (emptyCycleCount >= 5) {
                logDebug("AutomationService", "No active jobs for $emptyCycleCount cycles — stopping self")
                stopSelf()
            }
            return 60_000L
        }
        emptyCycleCount = 0

        val now = Clock.System.now().toEpochMilliseconds()
        var anyNetworkErr = false
        for (job in jobs) {
            val intervalMillis = job.interval_minutes * 60 * 1000
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
        if (anyNetworkErr) {
            consecutiveNetworkFailures++
            return backoffDelayMs()
        }
        consecutiveNetworkFailures = 0
        return 60_000L
    }

    /** 60s → 2m → 4m → 8m → … capped at 15m. Resets on first success. */
    private fun backoffDelayMs(): Long {
        val pow = consecutiveNetworkFailures.coerceIn(0, 8)
        val ms = 60_000L * (1L shl pow)
        return ms.coerceAtMost(15 * 60_000L)
    }

    private suspend fun checkJob(job: AlertJob) {
        logDebug("AutomationService", "Checking job: ${job.name} for ${job.symbol}")

        // 1. Fetch data based on tool_name
        val data = when (job.tool_name) {
            "trading_price" -> tradingApi.getYahooPrice(job.symbol)
            "trading_technical_analysis" -> {
                val exchange = job.exchange ?: tradingApi.resolveExchange(job.symbol, null)
                tradingApi.getTechnicalAnalysis(job.symbol, exchange)
            }
            "trading_sentiment" -> tradingApi.getRedditSentiment(job.symbol).mapValues { it.value.toString() }
            "trading_deep_analysis_suite" -> {
                val result = advancedEngine.analyze(job.symbol, "1h") // Default to 1h for automation
                if (result == null) emptyMap<String, String>()
                else mapOf(
                    "summaryScore" to result.summaryScore.toString(),
                    "lsdState" to result.lsdTrend.state,
                    "deltaLabel" to result.orderflow.deltaLabel,
                    "fiboScore" to (result.fiboStrength.maxOfOrNull { it.score }?.toString() ?: "0"),
                    "momentum" to result.momentum.signal
                )
            }
            else -> emptyMap()
        }

        if (data.containsKey("error")) return

        // --- New Feature: Auto High Confluence Alert ---
        if (job.tool_name == "trading_deep_analysis_suite") {
             val score = data["summaryScore"]?.toDoubleOrNull() ?: 0.0
             if (score >= 85.0 && job.is_triggered == 0L) {
                 sendNotification(job, "🌟 High Confluence ($score)")
             }
        }

        // 2. Evaluate condition
        val condition = automationJson.decodeFromString(AutomationCondition.serializer(), job.condition_json)
        val isMet = evaluator.evaluate(data, condition)

        val lastSample = data[condition.field] ?: "N/A"

        if (isMet) {
            if (job.is_triggered == 0L) {
                // Flipped to TRUE -> Notify!
                sendNotification(job, lastSample)
                automationManager.markTriggered(job.id, lastSample)
            }
        } else {
            if (job.is_triggered == 1L) {
                // Flipped to FALSE -> Reset so we can notify again later
                automationManager.resetTrigger(job.id)
            }
        }
    }

    private fun sendNotification(job: AlertJob, value: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎯 Jarvis Alert: ${job.name}")
            .setContentText("${job.symbol} เข้าเงื่อนไขแล้ว! ค่าปัจจุบัน: $value")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(job.id.toInt(), notification)
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
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

package com.example.personalaibot.service

import android.app.*
import android.content.Intent
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

class JarvisAutomationService : Service() {

    private val CHANNEL_ID = "JarvisAutomationChannel"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private lateinit var database: JarvisDatabase
    private lateinit var automationManager: AutomationManager
    private lateinit var tradingApi: TradingApiService
    private lateinit var smcApi: SmcApiService
    private lateinit var evaluator: AutomationEvaluator
    private lateinit var advancedEngine: AdvancedTradingEngine
    
    // We'll use a simple HttpClient for the background service
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
                try {
                    val jobs = database.jarvisDatabaseQueries.getAllActiveJobs().executeAsList()
                    val now = Clock.System.now().toEpochMilliseconds()
                    
                    for (job in jobs) {
                        val intervalMillis = job.interval_minutes * 60 * 1000
                        if (now - job.last_run_at >= intervalMillis) {
                            checkJob(job)
                        }
                    }
                } catch (e: Exception) {
                    logDebug("AutomationService", "Loop error: ${e.message}")
                }
                delay(60_000) // Check every minute
            }
        }
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
                    "fiboScore" to (result.fiberStrength.maxOfOrNull { it.score }?.toString() ?: "0"),
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
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis Automation Engine")
            .setContentText("เฝ้าติดตามตลาดให้คุณในเบื้องหลัง...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(99, notification)
        return START_STICKY
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
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

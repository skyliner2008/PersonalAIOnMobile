package com.skyliner2008.jarvis.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import com.skyliner2008.jarvis.MainActivity
import com.skyliner2008.jarvis.automation.*
import com.skyliner2008.jarvis.data.GeminiService
import com.skyliner2008.jarvis.tools.trading.TradingApiService
import com.skyliner2008.jarvis.tools.trading.SmcApiService
import com.skyliner2008.jarvis.tools.trading.AdvancedTradingEngine
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import com.skyliner2008.jarvis.db.JarvisDatabase
import com.skyliner2008.jarvis.db.JarvisDatabaseHolder
import com.skyliner2008.jarvis.db.AlertJob
import com.skyliner2008.jarvis.db.ScheduledTask
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/**
 * Background automation/monitoring foreground service (Slim Orchestrator).
 *
 * Delegated Architecture:
 * - LiveVoiceAlertEngine: AudioTrack streaming, Gemini Live WebSockets, TTS, and WakeLock.
 * - TradingAlertEvaluator: Technical Analysis, SMC, Indicator evaluation, AI supervisor, and signal tracking.
 * - AlertPresentationFormatter: Chat cards, JSON metadata, condition translation, and speech text formatting.
 */
class JarvisAutomationService : Service() {

    private val CHANNEL_ID = "JarvisAutomationChannel"
    private val NOTIFICATION_ID = 99
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var foregroundStarted = false
    private val alertAiMutex = Mutex()

    companion object {
        const val ACTION_ALERT_STOP = "com.skyliner2008.jarvis.action.ALERT_STOP"
        const val ACTION_ALERT_REPEAT = "com.skyliner2008.jarvis.action.ALERT_REPEAT"
        const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        const val ACTION_LONGTASK_ANNOUNCE = "com.skyliner2008.jarvis.action.LONGTASK_ANNOUNCE"
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
    private lateinit var autoEvaluator: AutomationEvaluator
    private lateinit var advancedEngine: AdvancedTradingEngine
    private lateinit var indicatorProvider: IndicatorAlertProvider
    private lateinit var smcAlertProvider: SmcAlertProvider
    private lateinit var smcFlowProvider: SmcFlowAlertProvider
    private lateinit var strategySignalProvider: StrategySignalProvider
    private lateinit var signalAlertProvider: SignalAlertProvider

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(io.ktor.client.plugins.websocket.WebSockets) {
            pingIntervalMillis = 20_000
        }
    }

    // Delegated sub-engines
    private lateinit var evaluator: TradingAlertEvaluator
    private lateinit var voiceEngine: LiveVoiceAlertEngine

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val driver = AndroidSqliteDriver(JarvisDatabase.Schema, applicationContext, "jarvis.db")
        database = JarvisDatabase(driver)
        JarvisDatabaseHolder.install(database)
        automationManager = JarvisDatabaseHolder.getAutomationManager()
        tradingApi = TradingApiService(client)
        smcApi = SmcApiService(client)
        autoEvaluator = AutomationEvaluator()
        advancedEngine = AdvancedTradingEngine(smcApi)
        indicatorProvider = IndicatorAlertProvider(smcApi)
        smcAlertProvider = SmcAlertProvider(smcApi)
        smcFlowProvider = SmcFlowAlertProvider(smcApi)
        strategySignalProvider = StrategySignalProvider(smcApi)
        signalAlertProvider = SignalAlertProvider(smcApi)

        try {
            val deviceControlExecutor = com.skyliner2008.jarvis.tools.device.DeviceControlExecutor(applicationContext)
            com.skyliner2008.jarvis.tools.ToolExecutor.initDeviceExecutor(deviceControlExecutor)
        } catch (e: Exception) {
            logError("AutomationService", "Failed to init DeviceControlExecutor: ${e.message}", e)
        }

        evaluator = TradingAlertEvaluator(
            database = database,
            automationManager = automationManager,
            tradingApi = tradingApi,
            smcApi = smcApi,
            evaluator = autoEvaluator,
            advancedEngine = advancedEngine,
            indicatorProvider = indicatorProvider,
            smcAlertProvider = smcAlertProvider,
            smcFlowProvider = smcFlowProvider,
            strategySignalProvider = strategySignalProvider,
            signalAlertProvider = signalAlertProvider,
            scope = scope
        )

        voiceEngine = LiveVoiceAlertEngine(
            context = this,
            database = database,
            scope = scope
        )

        startLoop()
    }

    private fun isLiveEngine(engine: String): Boolean =
        engine == "live" || engine == "ai" || engine == "live31" || engine == "live25"

    private fun setting(key: String): String = runCatching {
        database.jarvisDatabaseQueries.getSetting(key).executeAsOneOrNull() ?: ""
    }.getOrDefault("")

    private fun settingEnabled(key: String, default: Boolean): Boolean {
        val v = setting(key)
        return if (v.isBlank()) default else (v == "true" || v == "1")
    }

    private fun startLoop() {
        scope.launch {
            while (isActive) {
                try {
                    val delayMs = runOneCycle()
                    delay(delayMs)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    logError("AutomationService", "runOneCycle loop uncaught error: ${e.message}", e)
                    delay(30_000L)
                }
            }
        }
    }

    private suspend fun runOneCycle(): Long {
        val jobs = database.jarvisDatabaseQueries.getRunnableJobs().executeAsList()
        val tasks = database.jarvisDatabaseQueries.getAllActiveScheduledTasks().executeAsList()
        evaluator.clearCycleCache()

        if (jobs.isEmpty() && tasks.isEmpty()) {
            evaluator.emptyCycleCount++
            if (evaluator.emptyCycleCount >= 5) {
                logDebug("AutomationService", "No active alert jobs or scheduled tasks. Stopping service.")
                stopSelf()
                return 60_000L
            }
            return 60_000L
        } else {
            evaluator.emptyCycleCount = 0
        }

        val now = Clock.System.now().toEpochMilliseconds()
        var anyNetworkErr = false
        for (job in jobs) {
            val intervalMillis = evaluator.effectiveIntervalMs(job)
            if (now - job.last_run_at >= intervalMillis) {
                try {
                    evaluator.checkJob(job, ::fireJobAlert)
                } catch (e: java.io.IOException) {
                    anyNetworkErr = true
                    logDebug("AutomationService", "Job ${job.name} network error: ${e.message}")
                } catch (e: Exception) {
                    logError("AutomationService", "Job ${job.name} failed: ${e.message}", e)
                }
            }
        }

        processDueTasks(tasks, now)
        evaluator.trackSignalOutcomes()

        if (anyNetworkErr) {
            evaluator.consecutiveNetworkFailures++
            return evaluator.backoffDelayMs()
        }
        evaluator.consecutiveNetworkFailures = 0
        return 30_000L
    }

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
        val aiText = evaluator.generateAiText(task.prompt)
        val body = aiText ?: "ถึงเวลาแล้ว: ${task.prompt}"
        sendNotification("⏰ Jarvis: ${task.name}", body, (task.id + 100_000).toInt())
        pushToChat("⏰ **${task.name}**\n\n$body", """{"type":"scheduled_task","task_id":${task.id}}""")
        if (settingEnabled("alert_voice", true)) voiceEngine.speakAlert(body)
    }

    private suspend fun deliverChatAndVoice(
        cardBody: String,
        metaFor: (String?, String?) -> String,
        shortSpeech: String,
        fullSpeech: String,
        liveSummary: Boolean = false,
        timeframeMin: Int? = null
    ) {
        if (settingEnabled("alert_voice", true)) {
            val result = voiceEngine.speakAlert(shortSpeech, fullSpeech, liveSummary, timeframeMin)
            val effectiveSummary = if (liveSummary && !result.summary.isNullOrBlank()) result.summary else null
            val finalBody = if (effectiveSummary != null) {
                if (cardBody.contains("**JARVIS Live Summary:**")) cardBody
                else "$cardBody\n\n**JARVIS Live Summary:** $effectiveSummary"
            } else cardBody
            pushToChat(finalBody, metaFor(result.engineLabel, effectiveSummary))
        } else {
            pushToChat(cardBody, metaFor(null, null))
        }
    }
    private suspend fun pushToChat(body: String, metadata: String) {
        runCatching {
            com.skyliner2008.jarvis.memory.JarvisMemoryManager(database)
                .storeMessage("assistant", body, metadata = metadata)
            val emitted = com.skyliner2008.jarvis.memory.AlertChatBus.tryEmit("assistant", body, metadata)
            logDebug("AutomationService", "💬 pushToChat kind=${metadata.take(120)}… busEmitted=$emitted bodyLen=${body.length}")
        }.onFailure { logError("AutomationService", "💬 chat insert failed: ${it.message}", it) }
    }

    private suspend fun fireJobAlert(job: AlertJob, value: String, data: Map<String, String>, delivery: String = "ai") {
        val isSignalAlert = job.tool_name == "trading_signal_alert"
        // ── Pipeline trace: ต้นรหัสการแจ้งเตือน — ค่าที่ trigger + config ที่จะใช้ทั้ง chain ──
        logDebug("AutomationService",
            "🔔 FIRE '${job.name}' [${job.tool_name}] symbol=${job.symbol} value=$value | " +
            "mode=$delivery aiSummary=${settingEnabled("alert_ai_summary", true)} " +
            "voice=${settingEnabled("alert_voice", true)}/${setting("alert_voice_engine").ifBlank { "device" }} " +
            "model=${setting("model_name").ifBlank { "gemini-2.0-flash" }}")


        // ── AI Strategy Supervisor (Unified SMC เท่านั้น, โหมด ai) — gate ก่อนแจ้งผู้ใช้ ──
        // สัญญาณ + โครงสร้างตลาด 5TF (signal_mtf_context จาก MarketContextDigest) → APPROVE / VETO / ADJUST
        // VETO  = คัดออก ไม่แจ้งผู้ใช้ (shadow-record ไว้เปรียบเทียบ approved vs vetoed)
        // ADJUST = ปรับ SL/TP ตามโครงสร้าง (ผ่าน validation เท่านั้น ไม่งั้น fallback ค่าเดิม)
        var effData: Map<String, String> = data
        var supervisorNote: String? = null
        val isAnticipationAlert = isSignalAlert && (effData["signal_anticipation"] == "1" || effData["signal_stage"] == "ANTICIPATION" || job.condition_json.contains("signal_anticipation"))
        val isKeyzoneOnly = isSignalAlert && !isAnticipationAlert && effData["signal_side"] == null
        if (isSignalAlert && !isKeyzoneOnly && !isAnticipationAlert && delivery != "direct" &&
            (data["signal_strategy"] ?: "").contains("Unified SMC")
        ) {
            val sup = runCatching { evaluator.runStrategySupervisor(job, data) }
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
                        val adj = evaluator.validateSupervisorAdjustment(data, sup)
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
            val body = when {
                isAnticipationAlert -> {
                    val side = effData["signal_anticipation_side"]?.ifBlank { "BUY" } ?: "BUY"
                    val zone = effData["signal_anticipation_zone"]?.ifBlank { "Keyzone" } ?: "Keyzone"
                    "⚡ คาดการณ์ $side ${job.symbol} ที่โซน $zone — ${effData["signal_anticipation_desc"] ?: "เฝ้าระวังการกลับตัว"}"
                }
                isSignalAlert -> {
                    "📡 Signal ${effData["signal_side"]} ${job.symbol} (${effData["signal_strategy"]})\n" +
                        "เหตุผล: ${effData["signal_reason"]}\n" +
                        "Entry: ${effData["signal_entry"]} | SL: ${effData["signal_sl"]} | TP: ${effData["signal_tp"]} | RR 1:${effData["signal_rr"]}"
                }
                else -> {
                    "🎯 ${job.name}: ${job.symbol} เข้าเงื่อนไขแล้ว — ค่าปัจจุบัน: $value"
                }
            }
            sendJobAlertNotification(job, body)
            // การ์ดแชท + เสียง — การ์ดจะมี footer บอก engine เสียงที่พูดจริง (push ตอนเสียงเริ่ม)
            deliverChatAndVoice(
                cardBody = when {
                    isAnticipationAlert -> AlertPresentationFormatter.buildAnticipationChatCard(job, effData, null)
                    isKeyzoneOnly -> AlertPresentationFormatter.buildKeyzoneChatCard(job, effData, null)
                    isSignalAlert -> AlertPresentationFormatter.buildSignalChatCard(job, effData, null)
                    else -> AlertPresentationFormatter.buildAlertChatCard(job, value, effData, null)
                },
                metaFor = { v, s ->
                    when {
                        isAnticipationAlert -> AlertPresentationFormatter.anticipationChatMeta(job, effData, s, "signal_alert_direct", v)
                        isKeyzoneOnly -> AlertPresentationFormatter.keyzoneChatMeta(job, effData, s, "signal_alert_direct", v)
                        isSignalAlert -> AlertPresentationFormatter.signalChatMeta(job, effData, s, "signal_alert_direct", v)
                        else -> AlertPresentationFormatter.alertChatMeta(job, value, s, "signal_alert_direct", v)
                    }
                },
                shortSpeech = when {
                    isAnticipationAlert -> AlertPresentationFormatter.buildAnticipationSpeech(job, effData)
                    isSignalAlert -> AlertPresentationFormatter.buildSignalSpeech(job, effData)
                    else -> AlertPresentationFormatter.buildAlertSpeech(job, value)
                },
                fullSpeech = body,
                timeframeMin = evaluator.symbolTimeframeMin(job.symbol))
            return
        }

        // ── โหมด AI: ถ้าเป็น Signal + Live Voice ให้ Gemini Live ทำ Summary เอง ──
        // ไม่เรียก generateContent ก่อน Live อีกต่อไป เพราะจะทำให้เสียงต้องรอ Chat model timeout/fallback
        val useLiveSummary = isSignalAlert &&
            settingEnabled("alert_ai_summary", true) &&
            settingEnabled("alert_voice", true) &&
            isLiveEngine(setting("alert_voice_engine").ifBlank { "device" })

        val contextPrompt = when {
            isAnticipationAlert -> buildString {
                val antSide = effData["signal_anticipation_side"]?.ifBlank { "BUY" } ?: "BUY"
                val antZone = effData["signal_anticipation_zone"]?.ifBlank { "Keyzone" } ?: "Keyzone"
                val antDesc = effData["signal_anticipation_desc"] ?: ""
                val antConf = effData["signal_anticipation_confidence"] ?: "75"
                val closePrice = effData["close"] ?: "-"
                appendLine("เหตุการณ์: ระบบ AI คาดการณ์สัญญาณล่วงหน้า (Anticipation / Pre-Signal) ของ ${job.symbol}")
                appendLine("ทิศทาง: $antSide (ความเชื่อมั่น: $antConf%) | โซนสำคัญ: $antZone (@ $closePrice)")
                appendLine("ปัจจัยที่เกิด: $antDesc")
                appendLine()
                if (useLiveSummary) {
                    appendLine("[LIVE ANTICIPATION SUMMARY]")
                    appendLine("กฎการพูดเสียงสด (Live Voice):")
                    appendLine("1. ไม่ต้องบอกค่าทางเทคนิค ตัวเลขทศนิยม หรือค่าอินดิเคเตอร์ยิบย่อย (เช่น ค่า RSI ละเอียด, สเปรด, ตัวเลข Fibonacci)")
                    appendLine("2. เน้นสรุปแนวโน้มทิศทาง ($antSide) และสิ่งที่ต้องจับตามอง (เช่น รอแท่งเทียนปิดยืนยัน หรือเฝ้าระวังการหลุดแนวรับต้าน) ให้เข้าใจทันที")
                    appendLine("3. พูดเป็นภาษาไทยธรรมชาติกระชับ 1-2 ประโยค จบสมบูรณ์ และลงท้ายด้วย ค่ะ เสมอ")
                } else {
                    appendLine("ช่วยแจ้งผู้ใช้ภาษาไทยสั้นๆ 1-2 ประโยค: สรุปแนวโน้มทิศทาง $antSide และสิ่งที่ต้องจับตามอง (ไม่ต้องบอกค่าเทคนิคยิบย่อย)")
                }
            }
            isKeyzoneOnly -> buildString {
                appendLine("เหตุการณ์: ราคา ${job.symbol} เคลื่อนไปแตะจุดสำคัญของโครงสร้างตลาด (ยังไม่มีสัญญาณเข้าเทรด)")
                appendLine("จุดที่แตะ: ${effData["signal_keyzone_desc"]}")
                appendLine("โครงสร้างตลาด 5 ไทม์เฟรม:")
                appendLine(effData["signal_mtf_context"]?.takeIf { it.isNotBlank() } ?: (effData["signal_context"] ?: "-"))
                appendLine()
                appendLine("ช่วยแจ้งผู้ใช้ภาษาไทยสั้น 2-3 ประโยค: ราคาแตะจุดไหน โครงสร้างรอบข้างเป็นอย่างไร และควรจับตาอะไร (ยังไม่มีสัญญาณเข้าเทรด) — ใช้เฉพาะข้อมูลที่ให้ ห้ามสมมติเพิ่ม")
            }
            isSignalAlert -> buildString {
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
            }
            else -> buildString {
                appendLine("เหตุการณ์: การแจ้งเตือน '${job.name}' ของ ${job.symbol} เข้าเงื่อนไขแล้ว")
                appendLine("เงื่อนไขที่ตั้งไว้: ${job.condition_json}")
                appendLine("ค่าปัจจุบัน: $value")
                appendLine("ข้อมูลดิบ: ${effData.entries.take(8).joinToString { "${it.key}=${it.value}" }}")
                appendLine()
                appendLine("ช่วยสรุปแจ้งผู้ใช้แบบสั้น 2-3 ประโยค ภาษาไทย ว่าเกิดอะไรขึ้น และมีข้อแนะนำสั้นๆ (ถ้าเหมาะสม)")
            }
        }

        val aiText = supervisorNote ?: if (!useLiveSummary && settingEnabled("alert_ai_summary", true)) {
            val waitStart = System.currentTimeMillis()
            alertAiMutex.lock()
            try {
                val waitedMs = System.currentTimeMillis() - waitStart
                if (waitedMs > 0L) {
                    logDebug("AutomationService", "🧠 AI summary queued — waited ${waitedMs}ms")
                }
                evaluator.generateAiText(contextPrompt)
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

        val body = aiText ?: when {
            isAnticipationAlert -> {
                val antSide = effData["signal_anticipation_side"]?.ifBlank { "BUY" } ?: "BUY"
                val antZone = effData["signal_anticipation_zone"]?.ifBlank { "Keyzone" } ?: "Keyzone"
                "⚡ คาดการณ์ $antSide ${job.symbol} ที่โซน $antZone — ${effData["signal_anticipation_desc"] ?: "เฝ้าระวังการกลับตัว"}"
            }
            isKeyzoneOnly -> {
                "📍 ${job.symbol} แตะจุดสำคัญ: ${effData["signal_keyzone_desc"]?.ifBlank { "โครงสร้างตลาด" }}"
            }
            isSignalAlert -> {
                "📡 Signal ${effData["signal_side"]} ${job.symbol} @ ${effData["signal_entry"]} " +
                    "(SL ${effData["signal_sl"]} / TP ${effData["signal_tp"]}) — ${effData["signal_strategy"]}: ${effData["signal_reason"]}"
            }
            else -> {
                "${job.symbol} เข้าเงื่อนไขแล้ว! ค่าปัจจุบัน: $value"
            }
        }
        sendJobAlertNotification(job, body)
        deliverChatAndVoice(
            cardBody = when {
                isAnticipationAlert -> AlertPresentationFormatter.buildAnticipationChatCard(job, effData, aiText)
                isKeyzoneOnly -> AlertPresentationFormatter.buildKeyzoneChatCard(job, effData, aiText)
                isSignalAlert -> AlertPresentationFormatter.buildSignalChatCard(job, effData, aiText)
                else -> AlertPresentationFormatter.buildAlertChatCard(job, value, effData, aiText)
            },
            metaFor = { v, s ->
                val finalSummary = s ?: aiText
                when {
                    isAnticipationAlert -> AlertPresentationFormatter.anticipationChatMeta(job, effData, finalSummary, "signal_alert_ai", v)
                    isKeyzoneOnly -> AlertPresentationFormatter.keyzoneChatMeta(job, effData, finalSummary, "signal_alert_ai", v)
                    isSignalAlert -> AlertPresentationFormatter.signalChatMeta(job, effData, finalSummary, "signal_alert_ai", v)
                    else -> AlertPresentationFormatter.alertChatMeta(job, value, finalSummary, "signal_alert_ai", v)
                }
            },
            shortSpeech = when {
                isAnticipationAlert -> AlertPresentationFormatter.buildAnticipationSpeech(job, effData)
                isSignalAlert -> AlertPresentationFormatter.buildSignalSpeech(job, effData)
                else -> AlertPresentationFormatter.buildAlertSpeech(job, value)
            },
            fullSpeech = if (useLiveSummary) contextPrompt else body,
            liveSummary = useLiveSummary,
            timeframeMin = evaluator.symbolTimeframeMin(job.symbol))
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
                        metaFor = { voice, _ -> if (voice != null) meta.dropLast(1) + ",\"voice\":\"$voice\"}" else meta },
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
        voiceEngine.destroy()
        evaluator.destroy()
        scope.cancel()
        try { client.close() } catch (_: Throwable) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

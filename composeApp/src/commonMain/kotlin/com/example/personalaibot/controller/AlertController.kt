package com.example.personalaibot.controller

import com.example.personalaibot.automation.AutomationManager
import com.example.personalaibot.db.JarvisDatabase
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase-5 refactor: แยก Alert / Scheduled Task settings + actions ออกจาก JarvisViewModel
 */
class AlertController(
    private val scope: CoroutineScope,
    private val database: JarvisDatabase,
    private val client: HttpClient,
    private val automationManager: AutomationManager
) {
    // ─── Alert / Scheduled Task notification settings (อ่าน-เขียน AppSetting) ──
    private val _alertAiSummaryEnabled = MutableStateFlow(true)
    val alertAiSummaryEnabled: StateFlow<Boolean> = _alertAiSummaryEnabled.asStateFlow()

    private val _alertVoiceEnabled = MutableStateFlow(true)
    val alertVoiceEnabled: StateFlow<Boolean> = _alertVoiceEnabled.asStateFlow()

    /** Engine เสียงแจ้งเตือน: "device" = Android TTS (ทันที ไม่จำกัด — default) | "live" = Gemini Live chain */
    private val _alertVoiceEngine = MutableStateFlow("device")
    val alertVoiceEngine: StateFlow<String> = _alertVoiceEngine.asStateFlow()

    val scheduledTasks = automationManager.scheduledTasks

    companion object {
        /** engine เสียงแจ้งเตือนที่รองรับ: device = Android TTS | live = Gemini Live chain (2.5 Native → 3.1 → เครื่อง)
         *  ค่าเก่า ai/live31/live25 (ก่อนรวมระบบ 2026-08-15) migrate เป็น "live" */
        private val ALERT_VOICE_ENGINES = setOf("device", "live")
        private val LEGACY_LIVE_ENGINES = setOf("ai", "live31", "live25")

        /** normalize ค่าจาก settings — legacy live engines → "live" */
        fun normalizeAlertVoiceEngine(saved: String?): String = when {
            saved == null -> "device"
            saved in ALERT_VOICE_ENGINES -> saved
            saved in LEGACY_LIVE_ENGINES -> "live"
            else -> "device"
        }
    }

    // ─── Alert Auto Test (🧪 ทดสอบดึงข้อมูลทุก tool) ─────────────────────

    private val _alertTestRunning = MutableStateFlow(false)
    val alertTestRunning: StateFlow<Boolean> = _alertTestRunning.asStateFlow()

    private val _alertTestStatus = MutableStateFlow("")
    val alertTestStatus: StateFlow<String> = _alertTestStatus.asStateFlow()

    private val _alertTestResults = MutableStateFlow<List<com.example.personalaibot.automation.AlertDataTester.TestResult>>(emptyList())
    val alertTestResults: StateFlow<List<com.example.personalaibot.automation.AlertDataTester.TestResult>> = _alertTestResults.asStateFlow()

    /** โหลดค่าสวิตช์การแจ้งเตือนจาก DB — เรียกจาก VM.loadSettings */
    suspend fun loadPersistedSettings() {
        // โหลดค่าสวิตช์การแจ้งเตือน — เดิมไม่ได้โหลดกลับ ทำให้ toggle แจ้งเตือนด้วยเสียงเด้งเป็น "ปิด" ทุกครั้งที่เปิดแอปใหม่
        _alertAiSummaryEnabled.value = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("alert_ai_summary").executeAsOneOrNull()?.let { it == "true" } ?: true
        }
        _alertVoiceEnabled.value = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("alert_voice").executeAsOneOrNull()?.let { it == "true" } ?: true
        }
        _alertVoiceEngine.value = withContext(Dispatchers.IO) {
            normalizeAlertVoiceEngine(database.jarvisDatabaseQueries.getSetting("alert_voice_engine").executeAsOneOrNull())
        }
    }

    fun setAlertAiSummaryEnabled(enabled: Boolean) {
        _alertAiSummaryEnabled.value = enabled
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("alert_ai_summary", enabled.toString())
        }
    }

    fun setAlertVoiceEnabled(enabled: Boolean) {
        _alertVoiceEnabled.value = enabled
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("alert_voice", enabled.toString())
        }
    }

    fun setAlertVoiceEngine(engine: String) {
        if (engine !in ALERT_VOICE_ENGINES) return
        _alertVoiceEngine.value = engine
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("alert_voice_engine", engine)
        }
    }

    fun runAlertDataTest() {
        if (_alertTestRunning.value) return
        _alertTestRunning.value = true
        _alertTestResults.value = emptyList()
        scope.launch(Dispatchers.IO) {
            try {
                com.example.personalaibot.automation.AlertDataTester(client).runFullTest("XAUUSD") { status, done ->
                    _alertTestStatus.value = status
                    _alertTestResults.value = done
                }
            } catch (e: Exception) {
                _alertTestStatus.value = "❌ ทดสอบล้มเหลว: ${e.message}"
            } finally {
                _alertTestRunning.value = false
            }
        }
    }

    fun createAlert(
        name: String,
        symbol: String,
        toolName: String,
        field: String,
        op: String,
        value: String,
        interval: Long,
        delivery: String = "ai"
    ) {
        // Signal Alert: แปลง signal_buy/signal_sell (>= 1) เป็น signal_*_id (> เวลาสร้าง)
        // เพื่อให้ยิงเฉพาะสัญญาณที่เกิด "หลัง" ตั้ง alert — กันเด้งทันทีจาก marker ที่เกิดก่อนสร้าง
        val isSignalSide = toolName == "trading_signal_alert" &&
            (field == "signal_buy" || field == "signal_sell")
        val effField = if (isSignalSide) "${field}_id" else field
        val effOp = if (isSignalSide) ">" else op
        val effValue = if (isSignalSide) {
            kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString()
        } else value
        val operator = when (effOp) {
            ">" -> com.example.personalaibot.automation.ConditionOperator.GT
            "<" -> com.example.personalaibot.automation.ConditionOperator.LT
            ">=" -> com.example.personalaibot.automation.ConditionOperator.GTE
            "<=" -> com.example.personalaibot.automation.ConditionOperator.LTE
            "==" -> com.example.personalaibot.automation.ConditionOperator.EQ
            "contains" -> com.example.personalaibot.automation.ConditionOperator.CONTAINS
            else -> com.example.personalaibot.automation.ConditionOperator.GTE
        }
        val condition = com.example.personalaibot.automation.AutomationCondition(
            field = effField,
            operator = operator,
            value = effValue,
            delivery = delivery
        )
        automationManager.registerJob(
            name = name,
            symbol = symbol,
            exchange = null,
            toolName = toolName,
            condition = condition,
            intervalMinutes = interval
        )
    }

    fun createScheduledTask(
        name: String,
        prompt: String,
        type: String,
        runAt: Long,
        hhmm: String?
    ) {
        automationManager.registerScheduledTask(
            name = name,
            prompt = prompt,
            scheduleType = type,
            runAt = runAt,
            timeHhmm = hhmm
        )
    }
}

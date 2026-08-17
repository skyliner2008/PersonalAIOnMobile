package com.example.personalaibot.automation

import com.example.personalaibot.db.JarvisDatabase
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class AutomationManager(private val database: JarvisDatabase) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _activeJobs = MutableStateFlow<List<com.example.personalaibot.db.AlertJob>>(emptyList())
    val activeJobs: StateFlow<List<com.example.personalaibot.db.AlertJob>> = _activeJobs.asStateFlow()

    private val _scheduledTasks = MutableStateFlow<List<com.example.personalaibot.db.ScheduledTask>>(emptyList())
    val scheduledTasks: StateFlow<List<com.example.personalaibot.db.ScheduledTask>> = _scheduledTasks.asStateFlow()

    init {
        refreshJobs()
    }

    fun refreshJobs() {
        scope.launch {
            val jobs = database.jarvisDatabaseQueries.getAllActiveJobs().executeAsList()
            _activeJobs.value = jobs
            val tasks = database.jarvisDatabaseQueries.getAllActiveScheduledTasks().executeAsList()
            _scheduledTasks.value = tasks
            logDebug("AutomationManager", "refreshJobs: emitted ${jobs.size} jobs, ${tasks.size} tasks")
        }
    }

    // ─── Scheduled Tasks (งานตามเวลา — ระบบเบื้องหลังจะปลุก AI เมื่อถึงเวลา) ──

    fun registerScheduledTask(
        name: String,
        prompt: String,
        scheduleType: String,      // "one_time" | "daily"
        runAt: Long,               // epoch millis (one_time; 0 สำหรับ daily)
        timeHhmm: String?          // "08:30" (daily)
    ) {
        scope.launch {
            database.jarvisDatabaseQueries.insertScheduledTask(
                name = name,
                prompt = prompt,
                schedule_type = scheduleType,
                run_at = runAt,
                time_hhmm = timeHhmm,
                created_at = Clock.System.now().toEpochMilliseconds()
            )
            logDebug("AutomationManager", "Registered scheduled task: $name ($scheduleType)")
            refreshJobs()
            wakeupAutomationService()
        }
    }

    fun deleteScheduledTask(id: Long) {
        scope.launch {
            database.jarvisDatabaseQueries.deleteScheduledTask(id)
            logDebug("AutomationManager", "Deleted scheduled task id: $id")
            refreshJobs()
        }
    }

    fun deactivateScheduledTask(id: Long) {
        scope.launch {
            database.jarvisDatabaseQueries.deactivateScheduledTask(id)
            refreshJobs()
        }
    }

    fun markScheduledTaskFired(id: Long, date: String) {
        scope.launch {
            database.jarvisDatabaseQueries.markScheduledTaskFired(date, id)
            refreshJobs()
        }
    }

    fun registerJob(
        name: String,
        symbol: String,
        exchange: String?,
        toolName: String,
        condition: AutomationCondition,
        intervalMinutes: Long
    ) {
        val conditionJson = automationJson.encodeToString(AutomationCondition.serializer(), condition)
        scope.launch {
            database.jarvisDatabaseQueries.insertAlertJob(
                name = name,
                symbol = symbol,
                exchange = exchange,
                tool_name = toolName,
                condition_json = conditionJson,
                interval_minutes = intervalMinutes,
                created_at = Clock.System.now().toEpochMilliseconds()
            )
            logDebug("AutomationManager", "Registered new job: $name for $symbol")
            refreshJobs()
            wakeupAutomationService()
        }
    }

    fun deleteJob(id: Long) {
        scope.launch {
            database.jarvisDatabaseQueries.deleteAlertJob(id)
            logDebug("AutomationManager", "Deleted job id: $id")
            refreshJobs()
        }
    }

    fun updateInterval(id: Long, interval: Long) {
        scope.launch {
            database.jarvisDatabaseQueries.updateAlertJobInterval(interval, id)
            refreshJobs()
        }
    }

    fun markTriggered(id: Long, value: String) {
        scope.launch {
            database.jarvisDatabaseQueries.updateAlertJobTriggerState(
                is_triggered = 1L,
                last_value = value,
                last_run_at = Clock.System.now().toEpochMilliseconds(),
                id = id
            )
            refreshJobs()
        }
    }

    fun resetTrigger(id: Long) {
        scope.launch {
            database.jarvisDatabaseQueries.updateAlertJobTriggerState(
                is_triggered = 0L,
                last_value = null,
                last_run_at = Clock.System.now().toEpochMilliseconds(),
                id = id
            )
            refreshJobs()
            wakeupAutomationService()
        }
    }

    /** เปลี่ยนชื่อ job — ชื่อนี้ถูกใช้ในหัว notification และบริบทที่ AI ใช้พูดแจ้งเตือน */
    fun renameJob(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            database.jarvisDatabaseQueries.updateAlertJobName(trimmed, id)
            logDebug("AutomationManager", "Job $id renamed to: $trimmed")
            refreshJobs()
        }
    }

    /** เปิด/ปิดงาน (หยุดแจ้งเตือนจากปุ่มบน notification หรือ UI) */
    fun setJobActive(id: Long, active: Boolean) {
        scope.launch {
            database.jarvisDatabaseQueries.updateAlertJobStatus(if (active) 1L else 0L, id)
            logDebug("AutomationManager", "Job $id active=$active")
            refreshJobs()
            if (active) wakeupAutomationService()
        }
    }

    /** แก้ไขเงื่อนไขของ job (เช่น เปลี่ยนค่าราคาเป้าหมาย) แล้วรีเซ็ต trigger ให้เริ่มเฝ้าดูใหม่ */
    fun updateCondition(id: Long, condition: AutomationCondition) {
        val conditionJson = automationJson.encodeToString(AutomationCondition.serializer(), condition)
        scope.launch {
            database.jarvisDatabaseQueries.updateAlertJobCondition(conditionJson, id)
            logDebug("AutomationManager", "Job $id condition updated: $conditionJson")
            refreshJobs()
            wakeupAutomationService()
        }
    }

    /** บันทึกผลการ check ล่าสุด (last_value + last_run_at) โดยไม่แตะ trigger state */
    fun recordJobCheckResult(id: Long, lastValue: String) {
        scope.launch {
            try {
                database.jarvisDatabaseQueries.updateAlertJobCheckResult(
                    last_value = lastValue,
                    last_run_at = Clock.System.now().toEpochMilliseconds(),
                    id = id
                )
                logDebug("AutomationManager", "Recorded check result for job $id: $lastValue")
                refreshJobs()
            } catch (e: Exception) {
                logError("AutomationManager", "Failed to record check result for job $id: ${e.message}", e)
            }
        }
    }

    // ─── Signal Alert Records (เก็บสถิติ signal ที่ยิงจริง + ผล TP/SL) ────────

    /** บันทึก signal ที่ alert ยิงออกไป — ข้ามถ้า job เดียวกันเคยบันทึกแท่งนี้แล้ว (กันซ้ำ) */
    fun recordSignalAlert(
        jobId: Long, symbol: String, side: String, strategy: String, reason: String?,
        entry: Double, sl: Double, tp: Double, rr: Double?, barTime: Long, delivery: String
    ) {
        scope.launch {
            try {
                val dup = database.jarvisDatabaseQueries
                    .getSignalAlertByJobBar(jobId = jobId, barTime = barTime)
                    .executeAsOneOrNull()
                if (dup != null) return@launch
                database.jarvisDatabaseQueries.insertSignalAlert(
                    job_id = jobId, symbol = symbol, side = side, strategy = strategy,
                    reason = reason, entry = entry, sl = sl, tp = tp, rr = rr,
                    bar_time = barTime, delivery = delivery,
                    created_at = Clock.System.now().toEpochMilliseconds()
                )
                logDebug("AutomationManager", "Signal recorded: $side $symbol @ $entry ($strategy)")
            } catch (e: Exception) {
                logError("AutomationManager", "recordSignalAlert failed: ${e.message}", e)
            }
        }
    }

    fun getOpenSignalAlerts(): List<com.example.personalaibot.db.SignalAlertRecord> =
        try { database.jarvisDatabaseQueries.getOpenSignalAlerts().executeAsList() }
        catch (_: Exception) { emptyList() }

    fun closeSignalAlert(id: Long, outcome: String, hitAt: Long, hitPrice: Double, resultR: Double) {
        scope.launch {
            try {
                database.jarvisDatabaseQueries.closeSignalAlert(
                    outcome = outcome, hitAt = hitAt, hitPrice = hitPrice, resultR = resultR, id = id
                )
                logDebug("AutomationManager", "Signal #$id closed: $outcome (${resultR}R)")
            } catch (e: Exception) {
                logError("AutomationManager", "closeSignalAlert failed: ${e.message}", e)
            }
        }
    }

    fun getSignalAlertsSince(sinceMs: Long): List<com.example.personalaibot.db.SignalAlertRecord> =
        try { database.jarvisDatabaseQueries.getSignalAlertsSince(sinceMs).executeAsList() }
        catch (_: Exception) { emptyList() }

    // ─── StrategyTuning (params จาก backtest optimizer/evolution) ───

    fun saveStrategyTuning(
        symbol: String, interval: String, kind: String,
        slMult: Double, tpMult: Double, score: Double?, grade: String?, source: String
    ) {
        try {
            database.jarvisDatabaseQueries.upsertStrategyTuning(
                symbol = symbol.uppercase(), interval = interval.lowercase(), kind = kind,
                sl_mult = slMult, tp_mult = tpMult, score = score, grade = grade, source = source,
                updated_at = Clock.System.now().toEpochMilliseconds()
            )
            logDebug("AutomationManager", "Tuning saved: $symbol/$interval/$kind sl=$slMult tp=$tpMult ($grade, $source)")
        } catch (e: Exception) {
            logError("AutomationManager", "saveStrategyTuning failed: ${e.message}", e)
        }
    }

    fun getStrategyTuning(symbol: String, interval: String, kind: String): com.example.personalaibot.db.StrategyTuning? =
        try {
            database.jarvisDatabaseQueries
                .getStrategyTuning(symbol.uppercase(), interval.lowercase(), kind)
                .executeAsOneOrNull()
        } catch (_: Exception) { null }

    fun getAllStrategyTunings(): List<com.example.personalaibot.db.StrategyTuning> =
        try { database.jarvisDatabaseQueries.getAllStrategyTunings().executeAsList() }
        catch (_: Exception) { emptyList() }

    // ─── OptimizationTrial (Adaptive Optimize learning memory) ───

    fun saveOptimizationTrial(
        symbol: String, interval: String, kind: String,
        slMult: Double, tpMult: Double, score: Double,
        expectancyR: Double?, profitFactor: Double?, trades: Int?,
        deltaVsBaseline: Double?, applied: Boolean, source: String
    ) {
        try {
            database.jarvisDatabaseQueries.insertOptimizationTrial(
                symbol = symbol.uppercase(), interval = interval.lowercase(), kind = kind,
                sl_mult = slMult, tp_mult = tpMult, score = score,
                expectancy_r = expectancyR, profit_factor = profitFactor,
                trades = trades?.toLong(), delta_vs_baseline = deltaVsBaseline,
                applied = if (applied) 1 else 0, source = source,
                created_at = Clock.System.now().toEpochMilliseconds()
            )
        } catch (e: Exception) {
            logError("AutomationManager", "saveOptimizationTrial failed: ${e.message}", e)
        }
    }

    fun getOptimizationTrials(symbol: String, interval: String, kind: String, limit: Int = 100): List<com.example.personalaibot.db.OptimizationTrial> =
        try {
            database.jarvisDatabaseQueries
                .getOptimizationTrials(symbol.uppercase(), interval.lowercase(), kind, limit.toLong())
                .executeAsList()
        } catch (_: Exception) { emptyList() }

    /** เก็บกำไรความจำ — เหลือเฉพาะ 200 รายการล่าสุดต่อ symbol/interval/kind */
    fun pruneOptimizationTrials(symbol: String, interval: String, kind: String) {
        try {
            val s = symbol.uppercase(); val i = interval.lowercase()
            database.jarvisDatabaseQueries.deleteOldOptimizationTrials(s, i, kind, s, i, kind)
        } catch (_: Exception) { }
    }

    // ─── Mix Strategy config (เก็บใน CoreMemory key mixcfg|SYMBOL|TF = "tsmom,trend,dc|3") ───

    fun setMixConfig(symbol: String, interval: String, kindsCsv: String, minVotes: Int) {
        try {
            database.jarvisDatabaseQueries.upsertCoreMemory(
                "mixcfg|${symbol.uppercase()}|${interval.lowercase()}",
                "$kindsCsv|$minVotes",
                Clock.System.now().toEpochMilliseconds()
            )
        } catch (e: Exception) {
            logError("AutomationManager", "setMixConfig failed: ${e.message}", e)
        }
    }

    /** คืน (kindsCsv, minVotes) หรือ null ถ้ายังไม่ตั้งค่า */
    fun getMixConfig(symbol: String, interval: String): Pair<String, Int>? =
        try {
            val v = database.jarvisDatabaseQueries
                .getCoreMemoryByKey("mixcfg|${symbol.uppercase()}|${interval.lowercase()}")
                .executeAsOneOrNull() ?: return null
            val parts = v.split("|")
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 3) else null
        } catch (_: Exception) { null }
}

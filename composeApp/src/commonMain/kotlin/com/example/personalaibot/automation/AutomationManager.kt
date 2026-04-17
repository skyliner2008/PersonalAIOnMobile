package com.example.personalaibot.automation

import com.example.personalaibot.db.JarvisDatabase
import com.example.personalaibot.logDebug
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

    init {
        refreshJobs()
    }

    fun refreshJobs() {
        scope.launch {
            val jobs = database.jarvisDatabaseQueries.getAllActiveJobs().executeAsList()
            _activeJobs.value = jobs
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
        }
    }
}

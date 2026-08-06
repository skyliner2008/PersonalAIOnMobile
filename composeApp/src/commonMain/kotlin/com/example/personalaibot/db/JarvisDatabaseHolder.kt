package com.example.personalaibot.db

import com.example.personalaibot.automation.AutomationManager

object JarvisDatabaseHolder {
    @Volatile
    private var _database: JarvisDatabase? = null

    @Volatile
    private var _automationManager: AutomationManager? = null

    val database: JarvisDatabase?
        get() = _database

    fun install(db: JarvisDatabase) {
        _database = db
    }

    /** Returns a singleton AutomationManager backed by the installed database.
     *  Must call [install] first. */
    fun getAutomationManager(): AutomationManager {
        val db = _database ?: throw IllegalStateException("Database not installed")
        return _automationManager ?: synchronized(this) {
            _automationManager ?: AutomationManager(db).also { _automationManager = it }
        }
    }
}

package com.example.personalaibot.automation

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import com.example.personalaibot.service.JarvisAutomationService

/**
 * Context holder สำหรับ platform hooks ใน commonMain
 * ตั้งค่าจาก MainActivity.onCreate
 */
object AndroidContextHolder {
    @Volatile
    var appContext: Context? = null
}

actual fun wakeupAutomationService() {
    val ctx = AndroidContextHolder.appContext ?: run {
        logDebug("AutomationWakeup", "wakeup requested but appContext is null — skipped")
        return
    }
    try {
        val intent = Intent(ctx, JarvisAutomationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
        logDebug("AutomationWakeup", "Woke up JarvisAutomationService")
    } catch (e: Exception) {
        logError("AutomationWakeup", "Failed to wake automation service: ${e.message}", e)
    }
}

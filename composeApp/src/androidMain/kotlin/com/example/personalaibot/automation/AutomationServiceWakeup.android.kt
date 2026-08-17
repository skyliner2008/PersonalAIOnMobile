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

/** one-shot announce ผลงานพื้นหลัง (backtest/optimize) — ส่ง intent เข้า service พร้อม payload */
actual fun announceLongTaskCompletion(
    title: String, cardBody: String, metaJson: String, shortSpeech: String, fullSpeech: String
) {
    val ctx = AndroidContextHolder.appContext ?: run {
        logDebug("AutomationWakeup", "announce requested but appContext is null — skipped")
        return
    }
    try {
        val intent = Intent(ctx, JarvisAutomationService::class.java).apply {
            action = JarvisAutomationService.ACTION_LONGTASK_ANNOUNCE
            putExtra(JarvisAutomationService.EXTRA_ANNOUNCE_TITLE, title)
            putExtra(JarvisAutomationService.EXTRA_ANNOUNCE_BODY, cardBody)
            putExtra(JarvisAutomationService.EXTRA_ANNOUNCE_META, metaJson)
            putExtra(JarvisAutomationService.EXTRA_ANNOUNCE_SHORT, shortSpeech)
            putExtra(JarvisAutomationService.EXTRA_ANNOUNCE_FULL, fullSpeech)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
        logDebug("AutomationWakeup", "📣 announce long-task: $title")
    } catch (e: Exception) {
        logError("AutomationWakeup", "announce failed: ${e.message}", e)
    }
}

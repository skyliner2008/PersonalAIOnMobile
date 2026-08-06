package com.example.personalaibot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError

/**
 * ตื่น JarvisAutomationService อีกครั้งหลังผู้ใช้รีบูทเครื่อง
 * หมายเหตุ: Android 12+ อาจปฏิเสธการ start FGS จากเบื้องหลังในบางสถานการณ์ —
 * service มี guard กัน crash อยู่แล้ว (จะ stopSelf เงียบๆ) และจะกลับมาทำงาน
 * เมื่อผู้ใช้เปิดแอปครั้งถัดไป
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        logDebug("BootReceiver", "BOOT_COMPLETED — restarting JarvisAutomationService")
        try {
            val svc = Intent(context, JarvisAutomationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        } catch (e: Exception) {
            logError("BootReceiver", "start service at boot denied: ${e.message}", e)
        }
    }
}

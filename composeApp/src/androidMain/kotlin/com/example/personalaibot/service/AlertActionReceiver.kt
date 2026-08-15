package com.example.personalaibot.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.personalaibot.db.JarvisDatabase
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import kotlinx.datetime.Clock
import kotlin.concurrent.thread

/**
 * Receiver สำหรับปุ่มบน notification ของ alert ("🗑 ลบแจ้งเตือน" / "🔁 แจ้งเตือนซ้ำ")
 *
 * ประกาศใน AndroidManifest (ไม่ใช่ dynamic receiver) เพื่อให้ทำงานได้แม้
 * JarvisAutomationService หรือตัวแอปถูกฆ่าไปแล้ว — ระบบจะปลุก receiver นี้ขึ้นมาเอง
 * เปิด jarvis.db ไฟล์เดียวกับแอปหลักเพื่ออัปเดตสถานะ job โดยตรง
 */
class AlertActionReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "JarvisAutomationChannel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getLongExtra(JarvisAutomationService.EXTRA_JOB_ID, -1L)
        if (jobId <= 0L) return
        val notifId = intent.getIntExtra(JarvisAutomationService.EXTRA_NOTIFICATION_ID, jobId.toInt())
        val action = intent.action ?: return

        logDebug("AlertActionReceiver", "Action=$action for job $jobId")

        val pendingResult = goAsync()
        thread {
            try {
                val driver = AndroidSqliteDriver(JarvisDatabase.Schema, context, "jarvis.db")
                try {
                    val db = JarvisDatabase(driver)
                    when (action) {
                        JarvisAutomationService.ACTION_ALERT_STOP -> {
                            // 🗑 ลบแจ้งเตือน — ลบ job ออกจากรายการถาวร (ประวัติ SignalAlertRecord ยังเก็บไว้)
                            db.jarvisDatabaseQueries.deleteAlertJob(jobId)
                        }
                        JarvisAutomationService.ACTION_ALERT_REPEAT -> {
                            // 🔁 แจ้งเตือนซ้ำ — รีเซ็ต trigger ให้เฝ้าดูและแจ้งใหม่เมื่อเข้าเงื่อนไข
                            db.jarvisDatabaseQueries.updateAlertJobTriggerState(
                                0L, null, Clock.System.now().toEpochMilliseconds(), jobId
                            )
                            // ปลุก service กลับมาด้วย — ถ้าเหลือแต่ job TRIGGERED ค้าง service จะ stopSelf ไปแล้ว
                            com.example.personalaibot.automation.wakeupAutomationService()
                        }
                    }
                } finally {
                    driver.close()
                }

                // รีเฟรช UI ถ้าแอปยังอยู่ (manager อาจยังไม่ได้ install ถ้าแอปถูกฆ่า — ข้ามเงียบๆ)
                try {
                    com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager().refreshJobs()
                } catch (_: Exception) {}

                // ปิด notification เดิม + ยืนยันให้ผู้ใช้เห็น
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.cancel(notifId)
                val (title, body) = when (action) {
                    JarvisAutomationService.ACTION_ALERT_STOP ->
                        "🗑 ลบแจ้งเตือนแล้ว" to "งานเฝ้าดูถูกลบออกจากรายการแล้ว — สร้างใหม่ได้ในหน้า Automation"
                    else ->
                        "🔁 เฝ้าดูต่อแล้ว" to "จะแจ้งเตือนอีกครั้งเมื่อราคาเข้าเงื่อนไข"
                }
                nm.notify(notifId + 500_000, NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setAutoCancel(true)
                    .build())

                logDebug("AlertActionReceiver", "Done: $action job $jobId")
            } catch (e: Exception) {
                logError("AlertActionReceiver", "Failed: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

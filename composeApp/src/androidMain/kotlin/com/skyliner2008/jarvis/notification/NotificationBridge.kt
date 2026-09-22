package com.skyliner2008.jarvis.notification

import com.skyliner2008.jarvis.service.JarvisNotificationListener

/**
 * NotificationBridge — สะพานเชื่อมระหว่าง JarvisNotificationListener กับระบบ JARVIS AI / Voice
 *
 * หน้าที่:
 *  1. แปลงรายการแจ้งเตือนขาเข้าเป็นข้อความสรุปสำหรับ AI และแชท
 *  2. จัดการคำสั่งตอบกลับ (Quick Reply) ผ่าน RemoteInput
 *  3. จัดเตรียมข้อความสังเคราะห์สำหรับอ่านออกเสียงด้วย TTS หรือ Live Voice
 */
object NotificationBridge {

    /**
     * อ่านแจ้งเตือนล่าสุดและจัดรูปแบบให้อ่านง่าย
     */
    fun readRecent(appFilter: String? = null, count: Int = 5): String {
        val listener = JarvisNotificationListener.instance
        if (listener == null) {
            return "⚠️ บริการ Notification Listener ยังไม่ทำงาน กรุณาเปิดสิทธิ์ 'เข้าถึงการแจ้งเตือน' ในตั้งค่าระบบก่อนนะคะ"
        }

        val notifications = listener.getRecentNotifications(appFilter, count)
        if (notifications.isEmpty()) {
            return if (!appFilter.isNullOrBlank()) {
                "📭 ไม่พบข้อความแจ้งเตือนใหม่จากแอป '$appFilter' ในขณะนี้ค่ะ"
            } else {
                "📭 ไม่พบข้อความแจ้งเตือนใหม่ในขณะนี้ค่ะ"
            }
        }

        val sb = StringBuilder()
        sb.append("📬 ตรวจพบข้อความแจ้งเตือนล่าสุด ${notifications.size} รายการ:\n")
        notifications.forEachIndexed { index, notif ->
            val timeAgo = formatTimeAgo(notif.postTime)
            val replyTag = if (notif.canReply) " [💬 ตอบกลับได้]" else ""
            sb.append("${index + 1}. [${notif.appName}] ${notif.title}: \"${notif.text}\" ($timeAgo)$replyTag\n")
        }
        sb.append("\n💡 คุณสามารถสั่ง \"ตอบว่า [ข้อความ]\" เพื่อพิมพ์ตอบกลับข้อความล่าสุดได้ทันทีค่ะ")
        return sb.toString().trimEnd()
    }

    /**
     * สั่งตอบกลับแจ้งเตือน
     */
    fun reply(message: String, notificationKey: String? = null): String {
        val listener = JarvisNotificationListener.instance
        if (listener == null) {
            return "⚠️ บริการ Notification Listener ยังไม่ทำงาน กรุณาเปิดสิทธิ์ในการตั้งค่าก่อนนะคะ"
        }

        val (success, resultMsg) = listener.replyToNotification(notificationKey, message)
        return resultMsg
    }

    /**
     * แปลงแจ้งเตือนเป็นข้อความกระชับสำหรับพูดออกเสียง (Driving Mode)
     */
    fun formatForDrivingSpeech(record: JarvisNotificationListener.NotificationRecord): String {
        val sender = if (record.title.isNotBlank()) "จากคุณ ${record.title}" else ""
        val app = record.appName
        val body = record.text
        return "มีข้อความใหม่ใน $app $sender ว่า: $body"
    }

    private fun formatTimeAgo(timeMs: Long): String {
        val diffSec = (System.currentTimeMillis() - timeMs) / 1000
        return when {
            diffSec < 60 -> "เมื่อสักครู่"
            diffSec < 3600 -> "${diffSec / 60} นาทีที่แล้ว"
            diffSec < 86400 -> "${diffSec / 3600} ชั่วโมงที่แล้ว"
            else -> "${diffSec / 86400} วันที่แล้ว"
        }
    }
}

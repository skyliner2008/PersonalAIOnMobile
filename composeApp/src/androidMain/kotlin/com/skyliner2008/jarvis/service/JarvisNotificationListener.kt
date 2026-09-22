package com.skyliner2008.jarvis.service

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.CopyOnWriteArrayList

/**
 * JarvisNotificationListener — ดักจับและประมวลผลการแจ้งเตือนขาเข้า (LINE, SMS, Messenger ฯลฯ)
 *
 * ความสามารถ:
 *  1. ดักอ่านข้อความแจ้งเตือนขาเข้าจากแอปแชทและ SMS
 *  2. สั่งตอบกลับ (RemoteInput Reply) อัตโนมัติโดยไม่ต้องเปิดแอป
 *  3. เข้าถึง MediaSessionManager เพื่ออ่าน Metadata เพลงที่กำลังเล่นอยู่ (Now Playing)
 *
 * ผู้ใช้ต้องอนุญาต Notification Access ในตั้งค่าระบบ Android:
 * Settings > Apps & notifications > Special app access > Notification access > เปิดให้ JARVIS
 */
class JarvisNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "JarvisNotifListener"

        @Volatile
        var instance: JarvisNotificationListener? = null
            private set

        /** ตรวจว่า NotificationListenerService กำลังรันอยู่หรือไม่ */
        fun isEnabled(): Boolean = instance != null

        /** ตรวจว่าผู้ใช้เปิดสิทธิ์ Notification Access ให้กับแอปนี้แล้วหรือยัง */
        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledPackages.contains(context.packageName)
        }

        /** แอปรับส่งข้อความที่รองรับการแจ้งเตือนและการตอบกลับ */
        val KNOWN_MESSAGING_APPS = mapOf(
            "jp.naver.line.android" to "LINE",
            "com.whatsapp" to "WhatsApp",
            "com.facebook.orca" to "Messenger",
            "org.telegram.messenger" to "Telegram",
            "com.google.android.apps.messaging" to "Messages (SMS)",
            "com.samsung.android.messaging" to "Samsung Messages (SMS)",
            "com.android.mms" to "SMS",
            "com.instagram.android" to "Instagram Direct",
            "com.discord" to "Discord"
        )

        /** รายชื่อ Package ของระบบและเบื้องหลังที่ต้องข้ามเสมอ (ห้ามอ่านเด็ดขาด) */
        val IGNORED_SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.gms",
            "com.google.android.googlequicksearchbox",
            "com.android.vending",
            "com.android.providers.downloads",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.dialer"
        )

        /** ตรวจสอบว่าเป็นแอปแชท/ส่งข้อความหรือไม่ */
        fun isMessagingApp(pkg: String): Boolean {
            val lower = pkg.lowercase()
            if (KNOWN_MESSAGING_APPS.containsKey(pkg)) return true
            return lower.contains("line") || lower.contains("whatsapp") ||
                   lower.contains("messenger") || lower.contains("telegram") ||
                   lower.contains("messaging") || lower.contains("sms") || lower.contains("mms")
        }

        /** Callback เมื่อมีแจ้งเตือนใหม่เข้า (สำหรับโหมดขับขี่ / Always Live อ่านออกเสียง) */
        var onNotificationPostedListener: ((NotificationRecord) -> Unit)? = null

        // Deduplication ป้องกันการประกาศซ้ำภายใน 10 วินาที
        private var lastAnnouncedKey: String = ""
        private var lastAnnouncedText: String = ""
        private var lastAnnouncedTime: Long = 0L
    }

    /**
     * โมเดลข้อมูลแจ้งเตือน
     */
    data class NotificationRecord(
        val key: String,
        val packageName: String,
        val appName: String,
        val title: String,
        val text: String,
        val postTime: Long,
        val canReply: Boolean,
        val sbn: StatusBarNotification
    )

    private val recentNotifications = CopyOnWriteArrayList<NotificationRecord>()
    private val maxHistorySize = 50

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "✅ NotificationListener connected and active")
        syncActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "⚠️ NotificationListener disconnected")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName

        // 1. ข้ามแจ้งเตือนของแอปเราเอง และ package ระบบ/เบื้องหลัง
        if (pkg == packageName || pkg in IGNORED_SYSTEM_PACKAGES) return

        // 2. ข้ามแจ้งเตือนประเภท Ongoing / Persistent (เช่น สถานะการชาร์จแบต, มีเดียเพลเยอร์, ดาวน์โหลดไฟล์)
        if (sbn.isOngoing) return

        val notification = sbn.notification ?: return

        // 3. ข้ามแจ้งเตือนที่มี Flag Ongoing หรือ Foreground Service
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return

        // 4. กรองเฉพาะ Messaging / Chat Apps หรือ Notification Category ที่เป็น MESSAGE
        val isCategoryMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.category == Notification.CATEGORY_MESSAGE
        } else false
        val isKnownMessaging = isMessagingApp(pkg)
        val canReply = hasReplyAction(notification)

        // ต้องเป็นแอปแชท/ส่งข้อความ หรือมีปุ่ม Reply
        if (!isKnownMessaging && !isCategoryMessage && !canReply) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?: ""

        // หากไม่มีข้อความ ข้ามไป
        if (title.isEmpty() && text.isEmpty()) return

        // ข้ามกลุ่มสรุป (Group Summary) ที่ไม่มีเนื้อหาข้อความจริง
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0 && text.isEmpty()) return

        val appName = KNOWN_MESSAGING_APPS[pkg] ?: getAppName(pkg)

        val record = NotificationRecord(
            key = sbn.key,
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            postTime = sbn.postTime,
            canReply = canReply,
            sbn = sbn
        )

        // อัปเดตรายการล่าสุด (แทนที่ตัวเดิมถ้า key ซ้ำ หรือเพิ่มหัวแถว)
        recentNotifications.removeAll { it.key == sbn.key }
        recentNotifications.add(0, record)
        while (recentNotifications.size > maxHistorySize) {
            recentNotifications.removeAt(recentNotifications.lastIndex)
        }

        Log.d(TAG, "📩 New messaging notification from [$appName] ($title): $text (canReply=$canReply)")

        // Deduplication: ป้องกันการประกาศซ้ำข้อความเดิมภายใน 10 วินาที
        val now = System.currentTimeMillis()
        if (sbn.key == lastAnnouncedKey && (now - lastAnnouncedTime < 10_000L)) {
            return
        }
        if (text == lastAnnouncedText && (now - lastAnnouncedTime < 10_000L)) {
            return
        }
        lastAnnouncedKey = sbn.key
        lastAnnouncedText = text
        lastAnnouncedTime = now

        // แจ้งเตือนผู้ฟัง (เฉพาะข้อความแชทจริง)
        onNotificationPostedListener?.invoke(record)
    }


    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        recentNotifications.removeAll { it.key == sbn.key }
    }

    /**
     * ดึงประวัติแจ้งเตือนล่าสุด
     * @param appFilter กรองเฉพาะแอป เช่น "line", "sms", "whatsapp" (ถ้าไม่ระบุจะดึงทุกแอป)
     * @param count จำนวนที่ต้องการ
     */
    fun getRecentNotifications(appFilter: String? = null, count: Int = 5): List<NotificationRecord> {
        val filterLower = appFilter?.lowercase()?.trim()
        val filtered = if (filterLower.isNullOrEmpty()) {
            recentNotifications
        } else {
            recentNotifications.filter {
                it.appName.lowercase().contains(filterLower) ||
                it.packageName.lowercase().contains(filterLower)
            }
        }
        return filtered.take(count)
    }

    /**
     * สั่งพิมพ์ตอบกลับแจ้งเตือนผ่าน RemoteInput Action
     * @param key คีย์แจ้งเตือน (ถ้าไม่ระบุจะตอบตัวล่าสุดที่รองรับการตอบ)
     * @param message ข้อความที่จะพิมพ์ตอบกลับ
     * @return Pair<สำเร็จหรือไม่, ข้อความรายงานผล>
     */
    fun replyToNotification(key: String?, message: String): Pair<Boolean, String> {
        if (message.isBlank()) {
            return false to "❌ ข้อความที่จะตอบว่างเปล่า"
        }

        // หา notification ที่จะตอบ
        val target = if (!key.isNullOrBlank()) {
            recentNotifications.firstOrNull { it.key == key }
                ?: activeNotifications?.firstOrNull { it.key == key }?.let { toRecord(it) }
        } else {
            // เอาแจ้งเตือนล่าสุดที่สามารถตอบกลับได้
            recentNotifications.firstOrNull { it.canReply }
        }

        if (target == null) {
            return false to "❌ ไม่พบข้อความแจ้งเตือนที่สามารถตอบกลับได้"
        }

        val notification = target.sbn.notification
        val actions = notification.actions ?: return false to "❌ การแจ้งเตือนนี้ไม่มีปุ่มตอบกลับ"

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                val intent = Intent()
                val bundle = Bundle()
                bundle.putCharSequence(remoteInput.resultKey, message)
                RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
                return try {
                    action.actionIntent.send(this, 0, intent)
                    Log.i(TAG, "💬 Successfully sent reply to [${target.appName}] (${target.title}): $message")
                    true to "💬 ส่งข้อความตอบกลับไปยัง ${target.appName} (${target.title}) เรียบร้อยแล้วค่ะ: \"$message\""
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to send reply intent: ${e.message}", e)
                    false to "❌ ไม่สามารถส่งข้อความตอบกลับได้: ${e.message}"
                }
            }
        }

        return false to "❌ ไม่พบช่องทางตอบกลับ (RemoteInput) ในการแจ้งเตือนของ ${target.appName}"
    }

    /**
     * ดึงรายการ MediaController ที่กำลัง Active เพื่ออ่าน Now Playing
     */
    fun getActiveMediaSessions(): List<MediaController> {
        val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return emptyList()
        return try {
            val component = ComponentName(this, JarvisNotificationListener::class.java)
            manager.getActiveSessions(component)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Cannot get active media sessions: ${e.message}")
            emptyList()
        }
    }

    private fun hasReplyAction(notification: Notification): Boolean {
        val actions = notification.actions ?: return false
        return actions.any { action ->
            action.remoteInputs != null && action.remoteInputs.isNotEmpty()
        }
    }

    private fun syncActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            for (sbn in active) {
                if (sbn.packageName != packageName) {
                    onNotificationPosted(sbn)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync active notifications: ${e.message}")
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    private fun toRecord(sbn: StatusBarNotification): NotificationRecord {
        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val pkg = sbn.packageName
        val appName = KNOWN_MESSAGING_APPS[pkg] ?: getAppName(pkg)
        return NotificationRecord(
            key = sbn.key,
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            postTime = sbn.postTime,
            canReply = hasReplyAction(sbn.notification),
            sbn = sbn
        )
    }
}

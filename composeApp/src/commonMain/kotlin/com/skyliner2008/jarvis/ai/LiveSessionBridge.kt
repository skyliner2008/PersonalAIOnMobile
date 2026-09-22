package com.skyliner2008.jarvis.ai

import kotlin.jvm.Volatile

/**
 * สะพานจาก background service (alert/hotword) ไปยัง Live voice session ที่ผู้ใช้เปิดคุยอยู่
 *
 * เมื่อ Live เปิดอยู่ ทุกเสียงที่ AI พูดต้องออกผ่าน session หลักเท่านั้น — เดิม alert เปิด WebSocket Live
 * เส้นที่สองพร้อม AudioTrack แยก ไมค์ของ session หลักจึงได้ยินเสียงแจ้งเตือนแล้วตอบซ้อน (review 2026-09-16)
 */
object LiveSessionBridge {

    @Volatile
    private var activeProvider: (() -> Boolean)? = null

    @Volatile
    private var deliverer: (suspend (String) -> Boolean)? = null

    fun register(isActive: () -> Boolean, deliver: suspend (String) -> Boolean) {
        activeProvider = isActive
        deliverer = deliver
    }

    fun unregister() {
        activeProvider = null
        deliverer = null
    }

    /** ผู้ใช้เปิด Live voice session อยู่ (ไมค์กำลังสตรีม) */
    fun isActive(): Boolean = runCatching { activeProvider?.invoke() == true }.getOrDefault(false)

    /** ส่งข้อความให้ Live model พูดแทนผู้ใช้ — false ถ้าไม่มี session หรือส่งไม่สำเร็จ (caller ต้อง fallback) */
    suspend fun deliver(text: String): Boolean {
        if (!isActive()) return false
        val send = deliverer ?: return false
        return try {
            send(text)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}

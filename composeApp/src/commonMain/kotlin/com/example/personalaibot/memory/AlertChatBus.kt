package com.example.personalaibot.memory

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * AlertChatBus — ช่องทางภายใน process เดียวกัน สำหรับส่งข้อความจาก background service
 * (JarvisAutomationService) เข้าแชทที่กำลังเปิดอยู่แบบ real-time
 *
 * เหตุที่ต้องมี: service แทรกข้อความลง SQLite ผ่าน JarvisMemoryManager.storeMessage()
 * แต่หน้าแชทอ่าน DB ครั้งเดียวตอน loadHistory() ตอนเปิดแอป — ข้อความที่มาถึงระหว่างเปิดแชทอยู่
 * เลยไม่แสดงจนกว่าจะเปิดแอปใหม่ (ปัญหา "alert ไม่เข้าแชท" 2026-08-15)
 *
 * หมายเหตุ: ใช้ร่วมกับ storeMessage เสมอ — bus มีไว้แสดงผลสด, DB มีไว้ persist
 * metadata เก็บ field โครงสร้างของ alert (side/entry/tp/sl/...) ให้ UI render เป็นการ์ด
 */
object AlertChatBus {

    data class ChatPush(val role: String, val content: String, val metadata: String? = null)

    val incoming = MutableSharedFlow<ChatPush>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** emit แบบไม่ block (เรียกจาก service ได้ทุก context) — คืน true ถ้ามีผู้รับ/buffer รับได้ */
    fun tryEmit(role: String, content: String, metadata: String? = null): Boolean =
        incoming.tryEmit(ChatPush(role, content, metadata))
}

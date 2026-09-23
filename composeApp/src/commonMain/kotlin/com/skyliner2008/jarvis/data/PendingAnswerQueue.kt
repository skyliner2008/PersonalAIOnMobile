package com.skyliner2008.jarvis.data

/**
 * คิวคำถามที่ยังไม่ได้ตอบใน Live session — ผู้ใช้ถามหลายเรื่องติดกันได้
 *
 * Gemini Live ตอบเฉพาะคำถามล่าสุด: ถ้าผู้ใช้พูดคำถามถัดไประหว่างที่ข้อก่อนยังไม่เสร็จ server จะตัดเทิร์นนั้นทิ้ง
 * และยกเลิก tool ที่กำลังรัน (logcat 2026-09-24 01:19: "วิเคราะห์ BTC 5 มิติ" → "ปฏิทินเศรษฐกิจ" → "ราคาทอง"
 * ได้คำตอบแค่ข้อสุดท้าย) — คิวนี้เก็บข้อที่ถูกตัด แล้วส่งให้โมเดลตอบต่อทีละข้อหลังตอบข้อปัจจุบันเสร็จ
 *
 * pure logic (ไม่มี I/O) — LiveGeminiService เป็นคนตัดสินจังหวะส่ง
 */
class PendingAnswerQueue(
    private val maxItems: Int = 3,
    private val ttlMs: Long = 120_000L
) {
    data class Item(
        val question: String,
        val toolName: String?,
        val result: String?,
        val addedAtMs: Long
    )

    private val items = ArrayDeque<Item>()

    val size: Int get() = items.size
    fun isEmpty(): Boolean = items.isEmpty()

    /** เทิร์นที่ถูกตัดก่อนโมเดลได้ตอบหรือเรียก tool */
    fun addUnanswered(question: String, nowMs: Long): Boolean {
        val q = question.trim()
        if (!looksLikeRequest(q) || items.any { same(it.question, q) }) return false
        push(Item(q, null, null, nowMs))
        return true
    }

    /** tool ที่ server ยกเลิกเพราะผู้ใช้ถามเรื่องอื่นต่อ — รันจนเสร็จแล้ว เก็บผลไว้ตอบทีหลัง */
    fun addToolResult(question: String, toolName: String, result: String, nowMs: Long) {
        val q = question.trim()
        // ข้อเดิมที่รอ "ยังไม่ได้ตอบ" ถูกแทนด้วยผลจริง
        items.removeAll { same(it.question, q) }
        push(Item(q.ifBlank { "(คำถามก่อนหน้า)" }, toolName, result, nowMs))
    }

    /**
     * ผู้ใช้พูดเทิร์นใหม่ — ถามซ้ำเองแล้วไม่ต้องตอบจากคิว, สั่งยกเลิกก็ล้างคิว
     */
    fun onUserTurn(text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        if (CANCEL_WORDS.any { t.contains(it) }) {
            items.clear()
            return
        }
        items.removeAll { same(it.question, t) }
    }

    /** ข้อถัดไปที่ยังไม่หมดอายุ (เอาออกจากคิว) */
    fun next(nowMs: Long): Item? {
        while (items.isNotEmpty()) {
            val item = items.removeFirst()
            if (nowMs - item.addedAtMs <= ttlMs) return item
        }
        return null
    }

    fun clear() = items.clear()

    /**
     * เทิร์นเพิ่งจบ — เก็บสิ่งที่ผู้ใช้ยังไม่ได้ยินเข้าคิว คืนจำนวนที่เข้าคิว
     * - ถูกตัดก่อน AI ได้พูดและก่อนเรียก tool → คำถามเข้าคิว (ให้โมเดลเรียก tool ใหม่เอง)
     * - tool ส่งผลถึงโมเดลแล้ว แต่ถูกตัดก่อน AI ได้พูด → ผลเข้าคิว (logcat 2026-09-24 01:34, 01:36)
     * AI พูดไปแล้วบางส่วน = ผู้ใช้ได้ยินแล้ว ไม่ตอบซ้ำ
     */
    fun onTurnEnded(
        userText: String?,
        interrupted: Boolean,
        modelSpoke: Boolean,
        toolCalls: Int,
        sentResults: List<Triple<String, String, String>>,
        nowMs: Long
    ): Int {
        if (!interrupted || modelSpoke) return 0
        if (toolCalls == 0 && !userText.isNullOrBlank()) return if (addUnanswered(userText, nowMs)) 1 else 0
        sentResults.forEach { (question, tool, result) -> addToolResult(question, tool, result, nowMs) }
        return sentResults.size
    }

    private fun push(item: Item) {
        items.addLast(item)
        while (items.size > maxItems) items.removeFirst()
    }

    companion object {
        private val CANCEL_WORDS = listOf("ไม่เอาแล้ว", "ยกเลิก", "หยุดก่อน", "พอแล้ว", "ไม่ต้องแล้ว")
        private val FILLERS = setOf("ครับ", "ค่ะ", "คะ", "โอเค", "ok", "อืม", "เอ่อ", "หยุด", "พอ", "เดี๋ยว", "เดี๋ยวก่อน", "ไม่เป็นไร")

        private fun normalize(s: String) = s.lowercase().filterNot { it.isWhitespace() }

        /** ถามซ้ำ: เหมือนกันหรือข้อหนึ่งครอบอีกข้อ (transcript อาจมีคำลงท้ายต่างกัน) */
        fun same(a: String, b: String): Boolean {
            val x = normalize(a)
            val y = normalize(b)
            if (x.isEmpty() || y.isEmpty()) return false
            return x == y || (x.length >= 6 && y.contains(x)) || (y.length >= 6 && x.contains(y))
        }

        /** ประโยคที่เป็นคำขอจริง ไม่ใช่คำรับ/คำอุทาน */
        fun looksLikeRequest(text: String): Boolean {
            val n = normalize(text)
            return n.length >= 6 && n !in FILLERS.map { normalize(it) }
        }

        fun toPrompt(item: Item): String = if (item.result != null) {
            "[PENDING QUESTION] ก่อนหน้านี้ผู้ใช้ถามว่า \"${item.question}\" แต่ถูกขัดเพราะถามเรื่องอื่นต่อ — " +
                "ผลจากเครื่องมือ ${item.toolName}: ${item.result.take(3000)}\n" +
                "ตอบคำถามนี้ต่อทันทีแบบสนทนา (เช่น \"ส่วนเรื่อง…\") ห้ามใช้ markdown ห้ามทักทาย"
        } else {
            "[PENDING QUESTION] ก่อนหน้านี้ผู้ใช้ถามว่า \"${item.question}\" แต่ยังไม่ได้ตอบเพราะถามเรื่องอื่นต่อ — " +
                "ตอบคำถามนี้ต่อทันที เรียกเครื่องมือที่ต้องใช้ได้ (เช่น \"ส่วนเรื่อง…\") ห้ามทักทาย"
        }
    }
}

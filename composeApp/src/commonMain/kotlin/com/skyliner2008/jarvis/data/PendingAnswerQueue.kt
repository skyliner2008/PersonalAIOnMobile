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
    private val maxItems: Int = 5,
    /**
     * ข้อที่ค้างเกินนี้ไม่ตอบแล้ว — เดิม 2 นาที แต่รายงานยาวหลายข้อต่อกัน (BTC 5 มิติ ~40 วิ/ข้อ) ทำให้ราคาทองหมดอายุก่อนถึงคิว
     * แล้วหายเงียบ (logcat 2026-10-02 14:56:22 → 14:58:23)
     */
    private val ttlMs: Long = 300_000L
) {
    data class Item(
        val question: String,
        val toolName: String?,
        val result: String?,
        val addedAtMs: Long,
        /** ส่งไปแล้วกี่ครั้ง — รายงานที่ถูกคำถามใหม่กลืนจะถูกส่งซ้ำ */
        val attempts: Int = 0,
        /** the tool call it came from (Avatar mode's task list: one pill per job, from running to told) */
        val key: String? = null
    )

    /** One thing in the queue as Avatar mode's task list shows it; [telling] = handed to the AI, being told now. */
    data class View(val toolName: String?, val question: String, val telling: Boolean, val addedAtMs: Long, val key: String? = null)

    private val items = ArrayDeque<Item>()

    /** What waits to be told, oldest first. */
    fun snapshot(): List<Item> = items.toList()

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
    fun addToolResult(question: String, toolName: String, result: String, nowMs: Long, key: String? = null) {
        val q = question.trim()
        // ผลเก่าของเรื่องเดียวกัน (tool + หัวผลเดียวกัน) ถูกแทนด้วยผลใหม่; ข้อ "ยังไม่ได้ตอบ" ของประโยคเดียวกันถูกแทนด้วยผลจริง
        // (ไม่ลบผลของ tool อื่นที่บังเอิญติดประโยคเดียวกัน — ถามรัว ประโยคล่าสุดอาจไม่ใช่ของ call นี้; ไม่ลบผลของ
        // tool เดียวกันคนละเรื่อง — ราคาทองกับราคา Bitcoin)
        items.removeAll { sameResult(it, toolName, result) || (it.toolName == null && same(it.question, q)) }
        push(Item(q.ifBlank { "(คำถามก่อนหน้า)" }, toolName, result, nowMs, key = key))
    }

    /**
     * ผล [result] ของ [toolName] ได้คำตอบด้วยเสียงแล้ว — ผลค้างของเรื่องเดียวกันไม่ต้องตอบซ้ำ
     * (เดิมดูแค่ชื่อ tool: ถามราคาทองแล้วถูกเสียงแทรก ผลเข้าคิว → ถาม Bitcoin ต่อ (trading_price เหมือนกัน)
     * → ผลทองถูกลบทิ้งไม่ได้รายงานเลย, logcat 2026-10-01 21:32–21:33)
     */
    fun onToolAnswered(toolName: String, result: String) {
        items.removeAll { sameResult(it, toolName, result) }
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

    /** เอาข้อที่ค้างเกิน [ttlMs] ออก คืนข้อที่เอาออก — ให้ log ไว้ ไม่หายเงียบ */
    fun expire(nowMs: Long): List<Item> {
        val old = items.filter { nowMs - it.addedAtMs > ttlMs }
        items.removeAll { nowMs - it.addedAtMs > ttlMs }
        return old
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
     * รายงานที่ส่งไปแล้วแต่ผู้ใช้พูดคำถามใหม่ทับก่อน AI ได้พูด — โมเดลตอบคำถามใหม่ รายงานหายเงียบ
     * (logcat 2026-09-24 03:25: ส่งรายงาน BTC ขณะผู้ใช้กำลังพูด "ปฏิทิน…" ที่ไมค์ในเครื่องจับไม่ได้)
     * → ใส่กลับหัวคิวให้ส่งใหม่หลังตอบข้อใหม่เสร็จ; ครบ [MAX_ATTEMPTS] แล้วเลิก คืน true ถ้าใส่กลับ
     */
    fun requeueFront(item: Item): Boolean {
        val next = item.copy(attempts = item.attempts + 1)
        if (next.attempts >= MAX_ATTEMPTS) return false
        // มีผลใหม่กว่าของเรื่องเดียวกันรออยู่แล้ว — ใช้ของใหม่
        if (item.toolName != null && items.any { sameResult(it, item.toolName, item.result) }) return false
        items.addFirst(next)
        while (items.size > maxItems) items.removeLast()
        return true
    }

    /**
     * เทิร์นเพิ่งจบ — เก็บสิ่งที่ผู้ใช้ยังไม่ได้ยินเข้าคิว คืนจำนวนที่เข้าคิว
     * - ถูกตัดก่อน AI ได้พูดและก่อนเรียก tool → คำถามเข้าคิว (ให้โมเดลเรียก tool ใหม่เอง)
     * - tool ส่งผลถึงโมเดลแล้ว แต่ถูกตัดก่อน AI ได้พูด → ผลเข้าคิว (logcat 2026-09-24 01:34, 01:36)
     * - คำตอบจากผล tool ที่ผู้ใช้ได้ยินไม่ถึง [LiveProtocol.REPORT_HEARD_MIN_MS] ([heardMs] = ได้ยินไปกี่ ms ก่อนถูกตัด) → ผลเข้าคิว
     *   เหมือนรายงานที่ถูกตัดเร็ว: งานเร็วของลูกน้องตอบในเทิร์นของมันเองแล้ว (LiveToolBridge QUICK_ANSWER_MS, 2026-10-03)
     *   — เดิมเป็นรายงานจากคิว ถูกพูดทับใน 3 วิแรกก็เล่าใหม่
     * AI พูดไปแล้วนานพอ (หรือไม่รู้ว่าได้ยินนานเท่าไร) = ผู้ใช้ได้ยินแล้ว ไม่ตอบซ้ำ
     */
    fun onTurnEnded(
        userText: String?,
        interrupted: Boolean,
        modelSpoke: Boolean,
        toolCalls: Int,
        sentResults: List<Triple<String, String, String>>,
        nowMs: Long,
        heardMs: Long? = null
    ): Int {
        if (!interrupted) return 0
        if (toolCalls == 0 && !userText.isNullOrBlank()) return if (!modelSpoke && addUnanswered(userText, nowMs)) 1 else 0
        if (modelSpoke && (heardMs == null || heardMs >= LiveProtocol.REPORT_HEARD_MIN_MS)) return 0
        // ผลเปลี่ยนหน้า/โหมดไม่ใช่คำถาม (logcat 2026-10-02 11:42:14: เข้าคิวเป็นคำถาม → แอปถามซ้ำ → AI รัน BTC ครั้งที่ 3)
        val reports = sentResults.filter { it.second !in LiveProtocol.UI_ONLY_TOOLS }
        reports.forEach { (question, tool, result) -> addToolResult(question, tool, result, nowMs) }
        return reports.size
    }

    /**
     * เทิร์นที่มีผล tool แต่ผู้ใช้ไม่ได้ยินผล ([LiveProtocol.resultsUntold]): AI พูดแค่ประโยคทักทายเปิดเซสชัน
     * (logcat 2026-10-01 16:24–16:25 โหมดอวตาร: ราคา BTC และอากาศหายเงียบ) หรือจบเทิร์นเงียบๆ (2026-10-02 13:01:06: ราคาทอง)
     * — เหมือนเทิร์นที่ถูกตัด: ผลเข้าคิวรายงานตอนเงียบ · ผลของ tool เปลี่ยนหน้าตา/โหมด ([LiveProtocol.UI_ONLY_TOOLS])
     * ไม่ใช่เรื่องที่ต้องรายงาน คืนจำนวนที่เข้าคิว
     */
    fun onResultsUntold(sentResults: List<Triple<String, String, String>>, nowMs: Long): Int {
        val reports = sentResults.filter { it.second !in LiveProtocol.UI_ONLY_TOOLS }
        reports.forEach { (question, tool, result) -> addToolResult(question, tool, result, nowMs) }
        return reports.size
    }

    private fun push(item: Item) {
        items.addLast(item)
        while (items.size > maxItems) items.removeFirst()
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        private val CANCEL_WORDS = listOf("ไม่เอาแล้ว", "ยกเลิก", "หยุดก่อน", "พอแล้ว", "ไม่ต้องแล้ว")
        private val FILLERS = setOf("ครับ", "ค่ะ", "คะ", "โอเค", "ok", "อืม", "เอ่อ", "หยุด", "พอ", "เดี๋ยว", "เดี๋ยวก่อน", "ไม่เป็นไร")

        private fun normalize(s: String) = s.lowercase().filterNot { it.isWhitespace() }

        /**
         * หัวของผล (บรรทัดแรก: "**XAUUSD=X** - Real-time Price", "⛅ สภาพอากาศ: ตำบล บานา…") — บอกว่าผลนี้เป็นเรื่องอะไร
         * แม่นกว่าประโยคที่ติดมากับ call (ถามรัว ประโยคล่าสุดอาจเป็นของคำถามอื่น)
         */
        fun subject(result: String?): String? =
            result?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }?.take(80)

        private fun sameResult(item: Item, toolName: String?, result: String?): Boolean =
            item.toolName != null && item.toolName == toolName && subject(item.result) == subject(result)

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
            // อ้างชื่อ tool เป็นหลัก — ประโยคที่ติดมาอาจเป็นของคำถามอื่นเมื่อถามรัว (logcat 02:07: ผล BTC ติดป้าย "ปฏิทิน"
            // โมเดลจึงพูดเรื่องปฏิทินซ้ำ)
            "[PENDING QUESTION] ผลจากเครื่องมือ ${item.toolName} ที่ผู้ใช้ขอไว้ก่อนหน้ายังไม่ได้ตอบด้วยเสียง " +
                "(ช่วงนั้นผู้ใช้พูดว่า \"${item.question}\") — ผล: ${item.result.take(3000)}\n" +
                "สรุปผลนี้ให้ผู้ใช้ฟังต่อทันทีแบบสนทนา (เช่น \"ส่วนเรื่อง…\") พูดเฉพาะเรื่องของผลนี้ ห้ามใช้ markdown ห้ามทักทาย"
        } else {
            "[PENDING QUESTION] ก่อนหน้านี้ผู้ใช้ถามว่า \"${item.question}\" แต่ยังไม่ได้ตอบเพราะถามเรื่องอื่นต่อ — " +
                "ตอบคำถามนี้ต่อทันที เรียกเครื่องมือที่ต้องใช้ได้ (เช่น \"ส่วนเรื่อง…\") ห้ามทักทาย"
        }
    }
}

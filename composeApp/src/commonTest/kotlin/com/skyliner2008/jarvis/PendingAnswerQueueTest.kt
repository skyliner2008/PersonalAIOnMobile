package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.data.PendingAnswerQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ลำดับจริงจาก logcat 2026-09-24 01:19: ถาม 3 ข้อติดกัน ได้คำตอบแค่ข้อสุดท้าย */
class PendingAnswerQueueTest {

    @Test
    fun `three questions in a row are answered in order after the latest`() {
        val q = PendingAnswerQueue()
        // 1. BTC ถูกตัดก่อนเรียก tool
        assertTrue(q.addUnanswered("วิเคราะห์ BTC 5 มิติ", nowMs = 0))
        // 2. ปฏิทินเรียก tool แล้ว server ยกเลิกเพราะผู้ใช้ถามราคาทองต่อ — tool รันจนเสร็จ
        q.addToolResult("ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้", "trading_macro_calendar", "📅 Economic Calendar…", nowMs = 1_000)
        // 3. ราคาทองตอบปกติ → ตอบข้อค้างทีละข้อ
        val first = q.next(nowMs = 5_000)!!
        assertEquals("วิเคราะห์ BTC 5 มิติ", first.question)
        assertNull(first.result)
        val second = q.next(nowMs = 6_000)!!
        assertEquals("trading_macro_calendar", second.toolName)
        assertTrue(PendingAnswerQueue.toPrompt(second).contains("📅 Economic Calendar"))
        assertNull(q.next(nowMs = 7_000))
    }

    @Test
    fun `re-asking the same question removes it from the queue`() {
        val q = PendingAnswerQueue()
        q.addUnanswered("วิเคราะห์ BTC 5 มิติ", nowMs = 0)
        q.onUserTurn("วิเคราะห์ BTC 5 มิติ ครับ")
        assertTrue(q.isEmpty())
    }

    @Test
    fun `cancel words clear the queue`() {
        val q = PendingAnswerQueue()
        q.addUnanswered("วิเคราะห์ BTC 5 มิติ", nowMs = 0)
        q.onUserTurn("ไม่เอาแล้ว")
        assertTrue(q.isEmpty())
    }

    @Test
    fun `fillers and short noise are not queued`() {
        val q = PendingAnswerQueue()
        assertFalse(q.addUnanswered("ครับ", nowMs = 0))
        assertFalse(q.addUnanswered("อืม", nowMs = 0))
        assertFalse(q.addUnanswered("โอเค", nowMs = 0))
        assertTrue(q.isEmpty())
    }

    @Test
    fun `tool result replaces the unanswered entry for the same question`() {
        val q = PendingAnswerQueue()
        q.addUnanswered("ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้", nowMs = 0)
        q.addToolResult("ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้", "trading_macro_calendar", "ผล", nowMs = 1)
        assertEquals(1, q.size)
        assertEquals("ผล", q.next(nowMs = 2)!!.result)
    }

    /** logcat 01:34: tool ส่งผลถึงโมเดลแล้ว แต่ผู้ใช้ถามข้อถัดไปทับก่อน AI ได้พูด — ถามสลับลำดับ 2 รอบ ได้แค่ข้อสุดท้าย */
    @Test
    fun `tool answers cut before the model spoke are queued in order`() {
        val q = PendingAnswerQueue()
        val btc = Triple("วิเคราะห์ BTC 5 มิติ", "trading_deep_analysis_suite", "Deep Analysis…")
        val cal = Triple("ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้", "trading_macro_calendar", "📅 Economic Calendar…")
        assertEquals(1, q.onTurnEnded(null, interrupted = true, modelSpoke = false, toolCalls = 1, sentResults = listOf(btc), nowMs = 0))
        assertEquals(1, q.onTurnEnded(null, interrupted = true, modelSpoke = false, toolCalls = 1, sentResults = listOf(cal), nowMs = 1))
        // ราคาทองตอบปกติ → ตอบข้อค้างตามลำดับที่ถาม
        assertEquals(0, q.onTurnEnded("ราคาทองคำปัจจุบันเท่าไหร่", interrupted = false, modelSpoke = true, toolCalls = 1,
            sentResults = listOf(Triple("ราคาทองคำปัจจุบันเท่าไหร่", "trading_price", "4286.74")), nowMs = 2))
        assertEquals("trading_deep_analysis_suite", q.next(3)!!.toolName)
        assertEquals("trading_macro_calendar", q.next(4)!!.toolName)
        assertNull(q.next(5))
    }

    @Test
    fun `answer the user already heard part of is not repeated`() {
        val q = PendingAnswerQueue()
        val n = q.onTurnEnded(null, interrupted = true, modelSpoke = true, toolCalls = 1,
            sentResults = listOf(Triple("วิเคราะห์ BTC 5 มิติ", "trading_deep_analysis_suite", "…")), nowMs = 0)
        assertEquals(0, n)
        assertTrue(q.isEmpty())
    }

    @Test
    fun `question cut before any tool call is queued without a result`() {
        val q = PendingAnswerQueue()
        assertEquals(1, q.onTurnEnded("วิเคราะห์ BTC 5 มิติ", interrupted = true, modelSpoke = false, toolCalls = 0,
            sentResults = emptyList(), nowMs = 0))
        assertNull(q.next(1)!!.result)
    }

    /** logcat 02:07: ผล BTC ติดป้ายประโยค "ปฏิทิน…" (ถามรัว) — ต้องไม่ลบผลปฏิทินที่รออยู่ และ prompt ต้องอ้าง tool */
    @Test
    fun `results of different tools with the same spoken sentence are both kept`() {
        val q = PendingAnswerQueue()
        q.addToolResult("ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้", "trading_macro_calendar", "📅", nowMs = 0)
        q.addToolResult("ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้", "trading_deep_analysis_suite", "Deep", nowMs = 1)
        assertEquals(2, q.size)
        val first = q.next(2)!!
        assertTrue(PendingAnswerQueue.toPrompt(first).contains("ผลจากเครื่องมือ trading_macro_calendar"))
    }

    @Test
    fun `a newer result of the same tool replaces the older one`() {
        val q = PendingAnswerQueue()
        q.addToolResult("ราคาทอง", "trading_price", "4290", nowMs = 0)
        q.addToolResult("ราคาทองตอนนี้", "trading_price", "4291", nowMs = 1)
        assertEquals(1, q.size)
        assertEquals("4291", q.next(2)!!.result)
    }

    @Test
    fun `tool answered later in a normal turn is dropped from the queue`() {
        val q = PendingAnswerQueue()
        q.addToolResult("ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้", "trading_macro_calendar", "📅", nowMs = 0)
        q.onToolAnswered("trading_macro_calendar")
        assertTrue(q.isEmpty())
    }

    /** logcat 03:25: รายงาน BTC ถูกส่งขณะผู้ใช้พูด "ปฏิทิน…" — โมเดลตอบข้อใหม่ รายงานต้องกลับมาส่งใหม่ */
    @Test
    fun `swallowed report goes back to the front and is retried at most three times`() {
        val q = PendingAnswerQueue()
        q.addToolResult("วิเคราะห์ BTC 5 มิติ", "trading_deep_analysis_suite", "Deep", nowMs = 0)
        q.addToolResult("ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้", "trading_macro_calendar", "📅", nowMs = 1)
        var btc = q.next(2)!!
        assertTrue(q.requeueFront(btc))
        btc = q.next(3)!!
        assertEquals("trading_deep_analysis_suite", btc.toolName) // กลับมาเป็นข้อแรก ก่อนปฏิทิน
        assertEquals(1, btc.attempts)
        assertTrue(q.requeueFront(btc))
        btc = q.next(4)!!
        assertFalse(q.requeueFront(btc)) // ครั้งที่ 3 แล้ว เลิก
        assertEquals("trading_macro_calendar", q.next(5)!!.toolName)
    }

    @Test
    fun `requeue does not override a newer result of the same tool`() {
        val q = PendingAnswerQueue()
        q.addToolResult("ราคาทอง", "trading_price", "4290", nowMs = 0)
        val old = q.next(1)!!
        q.addToolResult("ราคาทอง", "trading_price", "4291", nowMs = 2)
        assertFalse(q.requeueFront(old))
        assertEquals("4291", q.next(3)!!.result)
    }

    @Test
    fun `stale items expire`() {
        val q = PendingAnswerQueue(ttlMs = 1_000)
        q.addUnanswered("วิเคราะห์ BTC 5 มิติ", nowMs = 0)
        assertNull(q.next(nowMs = 5_000))
    }
}

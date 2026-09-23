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

    @Test
    fun `stale items expire`() {
        val q = PendingAnswerQueue(ttlMs = 1_000)
        q.addUnanswered("วิเคราะห์ BTC 5 มิติ", nowMs = 0)
        assertNull(q.next(nowMs = 5_000))
    }
}

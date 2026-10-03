package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.data.LiveProtocol
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

    /**
     * A quick job is answered in its own turn now (LiveToolBridge QUICK_ANSWER_MS) — an answer the user talks over
     * before hearing 3 s of it is told again later, as the report it used to be would have been.
     */
    @Test
    fun `answer cut within three seconds of its voice is told again later`() {
        val q = PendingAnswerQueue()
        val btc = Triple("วิเคราะห์ BTC 5 มิติ", "trading_deep_analysis_suite", "Deep Analysis…")
        assertEquals(1, q.onTurnEnded(null, interrupted = true, modelSpoke = true, toolCalls = 1,
            sentResults = listOf(btc), nowMs = 0, heardMs = 1_900))
        assertEquals("trading_deep_analysis_suite", q.next(1)!!.toolName)
        assertEquals(0, q.onTurnEnded(null, interrupted = true, modelSpoke = true, toolCalls = 1,
            sentResults = listOf(btc), nowMs = 2, heardMs = 7_000), "heard long enough")
        // a chat answer cut early is not a question to ask again
        assertEquals(0, q.onTurnEnded("เล่าเรื่องตลกหน่อย", interrupted = true, modelSpoke = true, toolCalls = 0,
            sentResults = emptyList(), nowMs = 3, heardMs = 800))
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
        q.addToolResult("ราคาทอง", "trading_price", gold("4290"), nowMs = 0)
        q.addToolResult("ราคาทองตอนนี้", "trading_price", gold("4291"), nowMs = 1)
        assertEquals(1, q.size)
        assertEquals(gold("4291"), q.next(2)!!.result)
    }

    @Test
    fun `tool answered later in a normal turn is dropped from the queue`() {
        val q = PendingAnswerQueue()
        q.addToolResult("ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้", "trading_macro_calendar", "📅", nowMs = 0)
        q.onToolAnswered("trading_macro_calendar", "📅")
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
        q.addToolResult("ราคาทอง", "trading_price", gold("4290"), nowMs = 0)
        val old = q.next(1)!!
        q.addToolResult("ราคาทอง", "trading_price", gold("4291"), nowMs = 2)
        assertFalse(q.requeueFront(old))
        assertEquals(gold("4291"), q.next(3)!!.result)
    }

    /** logcat 2026-10-01 16:24 โหมดอวตาร: "ราคา Bitcoin" → price + face change → AI said only the greeting */
    @Test
    fun `results answered with only a greeting are reported later, a face change is not`() {
        val q = PendingAnswerQueue()
        val queued = q.onResultsUntold(
            listOf(
                Triple("ราคา Bitcoin", "device_avatar_emotion", "🎭 ปรับ Avatar: อารมณ์=HAPPY Props=sparkles"),
                Triple("ราคา Bitcoin", "trading_price", "**BTC-USD** - Real-time Price\nPrice: 83751.7700 USD")
            ),
            nowMs = 0
        )
        assertEquals(1, queued)
        val item = q.next(nowMs = 3_000)!!
        assertEquals("trading_price", item.toolName)
        val prompt = PendingAnswerQueue.toPrompt(item)
        assertTrue(prompt.contains("83751.7700") && prompt.contains("ห้ามทักทาย"))
        assertNull(q.next(nowMs = 3_001))
    }

    /** logcat 2026-10-01 21:32–21:33: gold cut off and queued, then "ราคา Bitcoin" answered — gold was dropped */
    @Test
    fun `another subject of the same tool does not drop a queued result`() {
        val q = PendingAnswerQueue()
        q.addToolResult("ราคา ทองคำ ปัจจุบัน", "trading_price", gold("4167.0800"), nowMs = 0)
        val btc = "**BTC-USD** - Real-time Price\nPrice: 84200.0000 USD"
        q.onToolAnswered("trading_price", btc)
        assertEquals(1, q.size)
        q.addToolResult("ราคา Bitcoin", "trading_price", btc, nowMs = 1)      // cut off as well: both wait
        assertEquals(2, q.size)
        q.onToolAnswered("trading_price", gold("4168.1000"))                     // gold asked again and answered
        assertEquals(btc, q.next(nowMs = 2)!!.result)
        assertNull(q.next(nowMs = 3))
    }

    private fun gold(price: String) = "**XAUUSD=X** - Real-time Price\nPrice: $price USD"

    /** logcat 2026-10-02 11:42:14 (Avatar mode): a cut turn had BTC (handed to the assistant) and a face change */
    @Test
    fun `a face change in a cut turn is not a question to answer later`() {
        val q = PendingAnswerQueue()
        val face = Triple("วิเคราะห์ BTC 5 มิติ", "device_avatar_emotion", "🎭 ปรับ Avatar: อารมณ์=FOCUSED ท่าทาง=wobble เรียบร้อยแล้วค่ะบอส!")
        assertEquals(0, q.onTurnEnded("วิเคราะห์ BTC 5 มิติ", interrupted = true, modelSpoke = false, toolCalls = 2, sentResults = listOf(face), nowMs = 0))
        assertTrue(q.isEmpty())
        val gold = Triple("ราคาทองคำ", "trading_price", "**XAUUSD=X** - Real-time Price\nPrice: 4182.7600 USD")
        assertEquals(1, q.onTurnEnded("ราคาทองคำ", interrupted = true, modelSpoke = false, toolCalls = 2, sentResults = listOf(face, gold), nowMs = 0))
        assertEquals("trading_price", q.next(nowMs = 1)!!.toolName)
    }

    /** logcat 2026-10-02 11:42:09: the BTC report was cut 0.6 s in (the user asked again) and counted as told */
    @Test
    fun `a report cut off at its start has not reached the user`() {
        assertFalse(LiveProtocol.reportReachedUser(spoke = true, greetingOnly = false, cutAfterMs = 580, toolCalls = 0))
        assertTrue(LiveProtocol.reportReachedUser(spoke = true, greetingOnly = false, cutAfterMs = 12_000, toolCalls = 0))
        assertTrue(LiveProtocol.reportReachedUser(spoke = true, greetingOnly = false, cutAfterMs = null, toolCalls = 0))
        assertFalse(LiveProtocol.reportReachedUser(spoke = true, greetingOnly = true, cutAfterMs = null, toolCalls = 0))
        assertFalse(LiveProtocol.reportReachedUser(spoke = false, greetingOnly = false, cutAfterMs = null, toolCalls = 0))
        // the AI called a tool for it: it is being worked on (asking again made it call the tool twice, 2026-10-01 23:29:58)
        assertTrue(LiveProtocol.reportReachedUser(spoke = false, greetingOnly = false, cutAfterMs = 0, toolCalls = 1))
    }

    /** logcat 2026-10-02 11:42:46–47: a report sent 1.5 s after a tool result cut the turn the AI was about to speak */
    @Test
    fun `no report while the AI may still be answering`() {
        assertTrue(LiveProtocol.modelMayStillAnswer(turnOpen = true, serverQuietMs = 1_509))
        assertFalse(LiveProtocol.modelMayStillAnswer(turnOpen = true, serverQuietMs = 6_100))    // it is not going to answer
        assertFalse(LiveProtocol.modelMayStillAnswer(turnOpen = false, serverQuietMs = 1_509))   // its turn has ended
    }

    /** logcat 2026-10-02 13:01:06 (live): the gold price reached the AI, which ended its turn without a word */
    @Test
    fun `results of a turn that ended without a word are reported later`() {
        assertTrue(LiveProtocol.resultsUntold(modelText = null, interrupted = false, spoke = false))
        assertTrue(LiveProtocol.resultsUntold(modelText = "สวัสดีฮับบอส! วันนี้เล่นอะไรกันดีฮับ", interrupted = false, spoke = true))
        assertFalse(LiveProtocol.resultsUntold(modelText = "ราคาทองคำตอนนี้ 4189 ดอลลาร์ค่ะ", interrupted = false, spoke = true))
        assertFalse(LiveProtocol.resultsUntold(modelText = null, interrupted = false, spoke = true))   // a voice with no transcript
        assertFalse(LiveProtocol.resultsUntold(modelText = null, interrupted = true, spoke = false))   // a cut turn: onTurnEnded
        val q = PendingAnswerQueue()
        val gold = Triple("ราคา ทอง คำ ปัจจุบัน เท่า ไหร่", "trading_price", "**XAUUSD=X** - Real-time Price\nPrice: 4189.1000 USD")
        assertEquals(1, q.onResultsUntold(listOf(gold), nowMs = 0))
        assertEquals("trading_price", q.next(nowMs = 1)!!.toolName)
    }

    /**
     * logcat 2026-10-02 14:56–14:58 (Avatar mode): BTC 5 มิติ → ปฏิทิน → ราคาทอง in a row. The BTC report went out in the
     * 2.5 s pause before the gold question, the server cut the gold turn and told BTC, the app counted BTC as swallowed
     * and told it again — and gold, queued at 14:56:22.8, expired behind them at 14:58:23.
     */
    @Test
    fun `questions in a row get no report in their pauses, none told twice, none expired`() {
        // 14:56:20.1: 2.6 s after the user cut the AI, the mic had been quiet 2.56 s — wait for 5 s
        assertEquals(5_000L, LiveProtocol.localQuietNeededMs(sinceUserCutMs = 2_612))
        assertEquals(2_500L, LiveProtocol.localQuietNeededMs(sinceUserCutMs = 27_700))
        // 14:56:22.8: the cut turn was the user's gold question (it called tools) — wait for the next turn
        assertEquals(
            LiveProtocol.ReportOutcome.WAIT,
            LiveProtocol.reportOutcome(reached = true, userSpokeOver = true, usersTurnCut = true, waited = false)
        )
        // 14:57:11.6: the next turn told BTC, with no new words from the user since — told, not sent again
        assertEquals(
            LiveProtocol.ReportOutcome.TOLD,
            LiveProtocol.reportOutcome(reached = true, userSpokeOver = false, usersTurnCut = false, waited = true)
        )
        // had the user asked something new instead, it was not told
        assertEquals(
            LiveProtocol.ReportOutcome.NOT_TOLD,
            LiveProtocol.reportOutcome(reached = true, userSpokeOver = true, usersTurnCut = false, waited = true)
        )
        // gold waited 120.2 s behind two long reports: still there
        val q = PendingAnswerQueue()
        q.addToolResult("ราคาทองคำปัจจุบันเท่าไหร่", "trading_price", "**XAUUSD=X** - Real-time Price\nPrice: 4186.0200 USD", nowMs = 0)
        assertTrue(q.expire(nowMs = 120_200).isEmpty())
        assertEquals(1, q.expire(nowMs = 300_001).size)
        assertTrue(q.isEmpty())
    }

    @Test
    fun `stale items expire`() {
        val q = PendingAnswerQueue(ttlMs = 1_000)
        q.addUnanswered("วิเคราะห์ BTC 5 มิติ", nowMs = 0)
        assertNull(q.next(nowMs = 5_000))
    }
}

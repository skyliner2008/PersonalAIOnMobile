package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.data.LiveProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ข้อความจริงจาก logcat 2026-09-23 02:35–02:38 (AI ทักทายซ้ำหลังผล tool) */
class LiveGreetingHistoryTest {

    private val greetingReply = "สวัสดีค่ะบอส จาวิส พร้อม คุย แล้ว ค่ะ มี อะไร ให้จาวิส ช่วย วันนี้ ดีคะ"
    private val repeatedGreetingAnswer =
        "สวัสดีค่ะบอส จาวิส พร้อม คุย แล้ว ค่ะ มีอะไร ให้จาวิส ช่วยวันนี้ ดีคะ  ผลวิเคราะห์ ทองคำ ในไทม์เฟรม 15 นาที ตอนนี้ แนวโน้ม เป็นกลาง นะคะ"

    @Test
    fun `strips spaced greeting transcript and keeps the real answer`() {
        assertEquals("", LiveProtocol.stripSessionGreeting(greetingReply))
        assertEquals(
            "ผลวิเคราะห์ ทองคำ ในไทม์เฟรม 15 นาที ตอนนี้ แนวโน้ม เป็นกลาง นะคะ",
            LiveProtocol.stripSessionGreeting(repeatedGreetingAnswer)
        )
        assertEquals("", LiveProtocol.stripSessionGreeting("สวัสดีครับบอส จาวิสพร้อมคุยแล้วครับ มีอะไรให้จาวิสช่วยวันนี้ดีครับ"))
    }

    @Test
    fun `leaves normal answers untouched`() {
        val answer = "ผลวิเคราะห์ Sentiment Bitcoin ตอนนี้ อยู่ใน โซน Greed นะคะบอส"
        assertEquals(answer, LiveProtocol.stripSessionGreeting(answer))
        assertFalse(LiveProtocol.startsWithSessionGreeting(answer))
        // ขึ้นต้นด้วย "สวัสดี" แต่ไม่ใช่คำทักทายเปิดเซสชัน
        val hello = "สวัสดีตอนเช้าค่ะ วันนี้ทองเปิดตลาดที่ 4367"
        assertEquals(hello, LiveProtocol.stripSessionGreeting(hello))
    }

    @Test
    fun `long answer mentioning greeting words without the ending is kept`() {
        val long = "สวัสดีค่ะ ระบบพร้อมคุยเรื่องทองแล้ว " + "รายละเอียด ".repeat(20)
        assertEquals(long.trim(), LiveProtocol.stripSessionGreeting(long))
    }

    @Test
    fun `detects repeated greeting at turn start`() {
        assertTrue(LiveProtocol.startsWithSessionGreeting(repeatedGreetingAnswer))
        assertTrue(LiveProtocol.startsWithSessionGreeting(greetingReply))
    }

    /** logcat 2026-10-01 16:24–16:25 โหมดอวตาร: ถาม "ราคา Bitcoin" / "สภาพอากาศวันนี้เป็นยังไง" ได้แค่คำทักทาย */
    @Test
    fun `a reply that is only the avatar greeting is not an answer`() {
        assertTrue(LiveProtocol.isSessionGreetingOnly("สวัสดีฮับบอส! น้อง หุ่นยนต์ สัตว์เลี้ยง พร้อม เล่น ด้วยแล้ว ฮับ"))
        assertTrue(LiveProtocol.isSessionGreetingOnly("สวัสดีฮับบอส! น้องหุ่นยนต์สัตว์เลี้ยงพร้อมเล่นด้วยแล้วฮับ"))
        assertTrue(LiveProtocol.isSessionGreetingOnly(greetingReply))
        // a greeting in the model's own words (the personas no longer give one word for word)
        assertTrue(LiveProtocol.isSessionGreetingOnly("สวัสดีค่ะบอส จาวิสพร้อมช่วยแล้วค่ะ"))
        // a real answer, or a greeting with the answer after it, is an answer
        assertFalse(LiveProtocol.isSessionGreetingOnly("น้องจัดข้อมูลทองคำมาให้แล้วฮับ ราคาตอนนี้อยู่ที่สี่พันหนึ่งร้อยหกสิบเอ็ดดอลลาร์นะฮับบอส"))
        assertFalse(LiveProtocol.isSessionGreetingOnly(repeatedGreetingAnswer))
        assertFalse(LiveProtocol.isSessionGreetingOnly(""))
    }

    @Test
    fun `session history drops greetings on both sides and caps length`() {
        val turns = listOf(
            "user" to "สวัสดีจาวิส พร้อมคุยไหม",
            "model" to greetingReply,
            "user" to "settlement BTC",
            "model" to "ผลวิเคราะห์ Sentiment Bitcoin " + "ก".repeat(900),
            "model" to repeatedGreetingAnswer,
        )
        val history = LiveProtocol.buildSessionHistory(turns)
        assertFalse(history.contains("พร้อมคุย"), history)
        assertFalse(history.contains("พร้อม คุย"), history)
        assertTrue(history.contains("ผู้ใช้: settlement BTC"))
        assertTrue(history.contains("จาวิส: ผลวิเคราะห์ ทองคำ"))
        assertTrue(history.lines().all { it.length <= 520 }, "each turn is capped")
    }

    /** logcat 02:07: โมเดลพูดตัวเลขวิเคราะห์ BTC ของ session ก่อนซ้ำ โดยไม่เรียก tool */
    @Test
    fun `old AI answers are cut short so stale figures cannot be replayed`() {
        val old = "ผลวิเคราะห์ บิตคอยน์ แบบ ห้ามิติ มาแล้ว ค่ะบอส ภาพรวม ตอนนี้ มีคะแนน ความเชื่อมั่น อยู่ที่ เจ็ดสิบ เต็ม ร้อยนะคะ " +
            "โดยแนวโน้ม หลัก ยังเป็น ขาลง ค่ะ ราคา อยู่ใกล้ โซน ฟีโบนักชี สำคัญ ที่ระดับ ศูนย์จุดห้า ถึง ศูนย์จุดหก หนึ่งแปด"
        val history = LiveProtocol.buildSessionHistory(listOf("user" to "วิเคราะห์ BTC 5 มิติ", "model" to old))
        assertFalse(history.contains("ฟีโบนักชี"), history)
        assertTrue(history.contains("ผู้ใช้: วิเคราะห์ BTC 5 มิติ"))
        assertTrue(LiveProtocol.HISTORY_HEADER.contains("ข้อมูลเก่า"))
    }

    @Test
    fun `live transcripts are hidden from chat but tool reports are kept`() {
        assertTrue(LiveProtocol.isLiveTranscript("{\"mode\": \"live_voice\"}"))
        assertTrue(LiveProtocol.isLiveTranscript("{\"mode\": \"live_voice\", \"interrupted\": true}"))
        assertFalse(LiveProtocol.isLiveTranscript("{\"mode\": \"live_voice_tool_result\"}"))
        assertFalse(LiveProtocol.isLiveTranscript("{\"mode\": \"specialist_session\"}"))
        assertFalse(LiveProtocol.isLiveTranscript(null))
    }

    /** logcat 2026-10-01 18:19: the face change after the weather report was tagged "answer the weather question now" */
    @Test
    fun `only real answers are tagged as the answer to the user's question`() {
        val asked = "สภาพ อากาศ วัน นี้ เป็น ยัง ไง"
        val weather = LiveProtocol.tagToolResult("device_weather", asked, asked, "⛅ สภาพอากาศ: ตำบล บานา, ปัตตานี")
        assertTrue(weather.startsWith("[ANSWER FOR]") && weather.contains(asked), weather)
        val face = "🎭 ปรับ Avatar: อารมณ์=SAD ฉากหลัง=rainy Props=umbrella"
        assertEquals(face, LiveProtocol.tagToolResult("device_avatar_emotion", asked, asked, face))
        assertEquals("ok", LiveProtocol.tagToolResult("device_always_live", "เปิดโหมดสัตว์เลี้ยง", "เปิดโหมดสัตว์เลี้ยง", "ok"))
        // a result for an earlier question while the user has moved on
        assertTrue(LiveProtocol.tagToolResult("trading_price", "ราคาทอง", asked, "4161").startsWith("[PENDING QUESTION]"))
        assertEquals("r", LiveProtocol.tagToolResult("trading_price", "", asked, "r"))
        // handing a job to the assistant is not the answer: no "answer it in full now" on its one-line acknowledgement
        val ack = LiveProtocol.delegatedAck("trading_macro_calendar")
        assertEquals(ack, LiveProtocol.tagToolResult("trading_macro_calendar", "ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้", asked, ack))
    }

    @Test
    fun `session history keeps only the newest turns`() {
        val turns = (1..20).flatMap { listOf("user" to "คำถาม $it", "model" to "คำตอบ $it") }
        val history = LiveProtocol.buildSessionHistory(turns, maxTurns = 8)
        assertEquals(8, history.lines().size)
        assertTrue(history.endsWith("คำตอบ 20"))
        assertTrue(history.contains("ผู้ใช้: คำถาม 17"))
        assertFalse(history.contains("คำถาม 16"))
    }

    /**
     * logcat 2026-10-01 16:51: ประวัติของ session 16:24 (โหมดอวตาร) จบด้วย "ราคา Bitcoin" / "สภาพอากาศ…" ที่ได้แค่คำทักทาย
     * — กด live ใหม่ โมเดลดึง BTC กับอากาศมาพูดเองทันที 2 รอบ
     */
    @Test
    fun `questions that never got an answer are left out of a new session's history`() {
        val turns = listOf(
            "user" to "ราคา ทองคำ ปัจจุบัน",
            "model" to "น้องจัดข้อมูลทองคำมาให้แล้วฮับ ราคาตอนนี้อยู่ที่สี่พันหนึ่งร้อยหกสิบเอ็ดดอลลาร์นะฮับบอส",
            "user" to "ราคา Bitcoin",
            "model" to "สวัสดีฮับบอส! น้อง หุ่นยนต์ สัตว์เลี้ยง พร้อม เล่น ด้วยแล้ว ฮับ",
            "user" to "สภาพอากาศวันนี้เป็นยังไง",
            "model" to "สวัสดีฮับบอส! น้องหุ่นยนต์สัตว์เลี้ยงพร้อมเล่นด้วยแล้วฮับ",
        )
        val history = LiveProtocol.buildSessionHistory(turns)
        assertTrue(history.contains("ผู้ใช้: ราคา ทองคำ ปัจจุบัน"), history)
        assertFalse(history.contains("Bitcoin"), history)
        assertFalse(history.contains("สภาพอากาศ"), history)
        // a reconnect in the middle of a conversation keeps them: the latest one may still be waiting for its answer
        val reconnect = LiveProtocol.buildReconnectHistory("", turns)
        assertTrue(reconnect.contains("ผู้ใช้: สภาพอากาศวันนี้เป็นยังไง"), reconnect)
    }
}

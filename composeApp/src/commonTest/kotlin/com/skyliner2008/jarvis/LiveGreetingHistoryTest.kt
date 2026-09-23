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

    @Test
    fun `session history keeps only the newest turns`() {
        val turns = (1..20).map { "user" to "คำถาม $it" }
        val history = LiveProtocol.buildSessionHistory(turns, maxTurns = 8)
        assertEquals(8, history.lines().size)
        assertTrue(history.endsWith("คำถาม 20"))
        assertFalse(history.contains("คำถาม 12\n"))
    }
}

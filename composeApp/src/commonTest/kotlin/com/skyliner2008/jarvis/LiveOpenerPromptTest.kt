package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.data.LiveProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What Live is sent when it becomes ready (phone 2026-10-09 00:18): the session opener is the user's hello to answer
 * aloud — sent as an example of what to say, the model opened with "ยินดีด้วยค่ะ บอส เรียกจาวิส…". The other texts sent
 * the same way go as they are: wrapped as a greeting, a meeting question became a greeting.
 */
class LiveOpenerPromptTest {

    @Test
    fun `the openers are the user's hello for the model to answer aloud`() {
        listOf("สวัสดีจาวิส พร้อมคุยไหม", "สวัสดีฮับ พร้อมเล่นแล้ว").forEach { opener ->
            val sent = LiveProtocol.sessionOpenerPrompt(opener)
            assertTrue(sent.startsWith("[VOICE RULE] ผู้ใช้เพิ่งเปิดเซสชันแล้วทักว่า \"$opener\""), sent)
            assertTrue(sent.contains("ทักทายกลับ"), sent)
        }
    }

    @Test
    fun `a meeting question, the new-voice check and a ready-made rule go as they are`() {
        val question = "สรุปประเด็นที่คุยกันเมื่อกี้ให้หน่อย"
        assertEquals(question, LiveProtocol.sessionOpenerPrompt(question))
        val voice = "เปลี่ยนมาใช้เสียง Leda แล้ว ลองทักทายสั้นๆ ด้วยเสียงใหม่นี้"
        assertEquals(voice, LiveProtocol.sessionOpenerPrompt(voice))
        val ruled = "[VOICE RULE] พร้อมคุยไหม"
        assertEquals(ruled, LiveProtocol.sessionOpenerPrompt(ruled))
    }

    @Test
    fun `the wrapped opener is still left out of the session history`() {
        val history = LiveProtocol.buildSessionHistory(
            listOf(
                "user" to LiveProtocol.sessionOpenerPrompt("สวัสดีจาวิส พร้อมคุยไหม"),
                "model" to "สวัสดีค่ะบอส",
                "user" to "ราคาทองเท่าไหร่",
                "model" to "ทองอยู่ที่ 4,000 ดอลลาร์"
            )
        )
        assertTrue(!history.contains("พร้อมคุยไหม"), history)
        assertTrue(history.contains("ราคาทองเท่าไหร่"), history)
    }
}

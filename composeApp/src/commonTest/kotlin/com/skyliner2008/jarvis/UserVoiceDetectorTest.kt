package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.controller.UserVoiceDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Is the user talking? From the mic's level — logcat 2026-10-02 23:29–23:34 (Avatar mode "กำลังฟัง" in silence). */
class UserVoiceDetectorTest {
    private val d = UserVoiceDetector()

    /** a sample every 32 ms (a mic buffer): [level] for each one from [fromMs] to [toMs] */
    private fun feed(fromMs: Long, toMs: Long, suppressed: Boolean = false, level: (Long) -> Float): List<Pair<Long, Boolean>> {
        val out = ArrayList<Pair<Long, Boolean>>()
        var t = fromMs
        while (t <= toMs) {
            out += t to d.onLevel(level(t), t, suppressed)
            t += 32
        }
        return out
    }

    /** a quiet room: the level never sits still (noise suppression leaves 0.2–0.9 %) */
    private fun quiet(t: Long) = 0.002f + ((t * 7919) % 70) / 10_000f

    /** speech: syllables of 180 ms at 8–20 %, 60 ms dips between them */
    private fun speech(t: Long) = if (t % 240 < 180) 0.08f + (t % 7) * 0.02f else 0.015f

    @Test
    fun `talking ends 0,7 s after the voice stops even though the level keeps changing`() {
        feed(0, 2_000, level = ::quiet)
        val talk = feed(2_032, 3_500, level = ::speech)
        assertTrue(talk.any { it.second }, "talking while the user speaks")
        val after = feed(3_532, 8_000, level = ::quiet)
        assertFalse(after.last().second, "silence again: not talking")
        val endedAt = after.first { !it.second }.first
        assertTrue(endedAt - 3_500 <= 800, "ended ${endedAt - 3_500} ms after the voice — the hold runs out")
    }

    @Test
    fun `talking starts a quarter of a second into the voice`() {
        feed(0, 1_000, level = ::quiet)
        val talk = feed(1_024, 2_500, level = ::speech)
        val startedAt = talk.first { it.second }.first
        assertTrue(startedAt - 1_024 in 250..400, "started ${startedAt - 1_024} ms in")
    }

    @Test
    fun `a click, a knock or a cough is not talking`() {
        feed(0, 1_000, level = ::quiet)
        assertFalse(feed(1_024, 1_120) { 0.3f }.any { it.second }, "a 100 ms bang")
        assertFalse(feed(1_152, 3_000, level = ::quiet).any { it.second })
    }

    @Test
    fun `the robot's own sounds and the AI's voice are not the user`() {
        feed(0, 1_000, level = ::quiet)
        // logcat 23:31:13.367 ALARM → the mic at 7 % for the length of the sound
        assertFalse(feed(1_024, 2_400, suppressed = true) { 0.07f }.any { it.second })
        assertFalse(feed(2_432, 4_000, level = ::quiet).any { it.second }, "and nothing left over after it")
    }

    @Test
    fun `a steady ambience is the room's noise, a voice over it is talking`() {
        // rain on the stage at 6 % — above the bare threshold, all the time
        assertFalse(feed(0, 6_000) { 0.06f + (it % 5) * 0.002f }.drop(10).any { it.second }, "rain alone is not talking")
        val over = feed(6_032, 8_000) { if (it % 240 < 180) 0.30f else 0.06f }
        assertTrue(over.any { it.second }, "a voice three times louder than the rain")
    }

    @Test
    fun `reset forgets a voice half started`() {
        feed(0, 1_000, level = ::quiet)
        feed(1_024, 1_200, level = ::speech)
        d.reset()
        assertEquals(false, d.speaking)
        assertFalse(feed(1_232, 1_300, level = ::speech).any { it.second }, "the onset starts again")
    }
}

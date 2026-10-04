package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.wake.WakeAiFallback
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A wake the AI cannot analyse: a quiet event list at most every 30 minutes per asset, "the AI is down" once. */
class WakeAiFallbackTest {

    @AfterTest
    fun reset() = WakeAiFallback.resetForTesting()

    private val min = 60_000L

    @Test
    fun `one quiet list per asset per half hour, and the AI-down notice once`() {
        val t0 = 1_000_000L
        val first = WakeAiFallback.onAiFailed("XAUUSD", t0)
        assertTrue(first.send)
        assertTrue(first.sayAiDown)

        // wakes keep coming every few minutes: not sent
        val soon = WakeAiFallback.onAiFailed("XAUUSD", t0 + 10 * min)
        assertFalse(soon.send)
        assertEquals(10 * min, soon.sinceLastMs)

        // another asset gets its own list, without repeating the notice
        val btc = WakeAiFallback.onAiFailed("BTCUSDT", t0 + 11 * min)
        assertTrue(btc.send)
        assertFalse(btc.sayAiDown)

        // half an hour on: gold again, no notice
        val later = WakeAiFallback.onAiFailed("XAUUSD", t0 + 31 * min)
        assertTrue(later.send)
        assertFalse(later.sayAiDown)
    }

    @Test
    fun `after the AI answers, the next failure says it is down again`() {
        val t0 = 5_000_000L
        assertTrue(WakeAiFallback.onAiFailed("XAUUSD", t0).sayAiDown)
        WakeAiFallback.onAiAnswered()
        val again = WakeAiFallback.onAiFailed("XAUUSD", t0 + 40 * min)
        assertTrue(again.send)
        assertTrue(again.sayAiDown)
    }
}

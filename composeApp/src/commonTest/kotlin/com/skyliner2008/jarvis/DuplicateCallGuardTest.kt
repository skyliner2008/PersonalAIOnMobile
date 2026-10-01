package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.ai.DuplicateCallGuard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** logcat 2026-09-24 03:33: trading_price XAUUSD ถูกเรียก 7 ครั้งติดกันในเทิร์นเดียว */
class DuplicateCallGuardTest {
    private val gold = mapOf("symbol" to "XAUUSD=X")

    @Test
    fun `repeat in the same turn returns the previous result instead of running again`() {
        val g = DuplicateCallGuard()
        assertIs<DuplicateCallGuard.Decision.Run>(g.check("c1", "trading_price", gold, turn = 5, nowMs = 0))
        g.recordResult("c1", "Price: 4284.91", nowMs = 300)
        val d = g.check("c2", "trading_price", gold, turn = 5, nowMs = 850)
        assertIs<DuplicateCallGuard.Decision.Repeat>(d)
        assertEquals("Price: 4284.91", d.previousResult)
        assertEquals(300L, d.resultAtMs)
    }

    /** logcat 2026-10-01 18:19: weather reported, face changed, then weather called again and reported a second time */
    @Test
    fun `a repeat after the result was spoken is told it was reported, not to say it again`() {
        val g = DuplicateCallGuard()
        g.check("c1", "device_weather", emptyMap(), turn = 3, nowMs = 33_631)
        g.recordResult("c1", "⛅ สภาพอากาศ: ตำบล บานา, ปัตตานี", nowMs = 34_997)
        val d = g.check("c2", "device_weather", emptyMap(), turn = 3, nowMs = 45_770)
        assertIs<DuplicateCallGuard.Decision.Repeat>(d)
        val modelSpokeAt = 45_695L                       // "…จะได้เช็คอากาศให้ถูกฮับ"
        assertTrue(modelSpokeAt > d.resultAtMs)
        val note = DuplicateCallGuard.alreadyReportedNote("device_weather")
        assertTrue(note.contains("ห้ามพูดผลซ้ำ") && !note.contains("ให้พูดตอบผู้ใช้จากผลนี้ทันที"))
    }

    @Test
    fun `repeat while the first call is still running is told to wait`() {
        val g = DuplicateCallGuard()
        g.check("c1", "trading_deep_analysis_suite", mapOf("symbol" to "BTCUSDT"), turn = 1, nowMs = 0)
        assertIs<DuplicateCallGuard.Decision.StillRunning>(
            g.check("c2", "trading_deep_analysis_suite", mapOf("symbol" to "BTCUSDT"), turn = 1, nowMs = 500)
        )
    }

    @Test
    fun `a new user turn, other args or an expired window run normally`() {
        val g = DuplicateCallGuard(windowMs = 20_000)
        g.check("c1", "trading_price", gold, turn = 5, nowMs = 0)
        g.recordResult("c1", "x", nowMs = 50)
        assertIs<DuplicateCallGuard.Decision.Run>(g.check("c2", "trading_price", gold, turn = 6, nowMs = 100))
        assertIs<DuplicateCallGuard.Decision.Run>(g.check("c3", "trading_price", mapOf("symbol" to "BTCUSDT"), turn = 5, nowMs = 200))
        assertIs<DuplicateCallGuard.Decision.Run>(g.check("c4", "trading_price", gold, turn = 5, nowMs = 30_000))
    }

    @Test
    fun `argument order does not matter`() {
        val g = DuplicateCallGuard()
        g.check("c1", "trading_smc_analysis", mapOf("symbol" to "XAUUSD", "interval" to "15m"), turn = 1, nowMs = 0)
        g.recordResult("c1", "r", nowMs = 1)
        assertIs<DuplicateCallGuard.Decision.Repeat>(
            g.check("c2", "trading_smc_analysis", mapOf("interval" to "15m", "symbol" to "XAUUSD"), turn = 1, nowMs = 1)
        )
    }
}

package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.ai.DuplicateCallGuard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** logcat 2026-09-24 03:33: trading_price XAUUSD ถูกเรียก 7 ครั้งติดกันในเทิร์นเดียว */
class DuplicateCallGuardTest {
    private val gold = mapOf("symbol" to "XAUUSD=X")

    @Test
    fun `repeat in the same turn returns the previous result instead of running again`() {
        val g = DuplicateCallGuard()
        assertIs<DuplicateCallGuard.Decision.Run>(g.check("c1", "trading_price", gold, turn = 5, nowMs = 0))
        g.recordResult("c1", "Price: 4284.91")
        val d = g.check("c2", "trading_price", gold, turn = 5, nowMs = 850)
        assertIs<DuplicateCallGuard.Decision.Repeat>(d)
        assertEquals("Price: 4284.91", d.previousResult)
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
        g.recordResult("c1", "x")
        assertIs<DuplicateCallGuard.Decision.Run>(g.check("c2", "trading_price", gold, turn = 6, nowMs = 100))
        assertIs<DuplicateCallGuard.Decision.Run>(g.check("c3", "trading_price", mapOf("symbol" to "BTCUSDT"), turn = 5, nowMs = 200))
        assertIs<DuplicateCallGuard.Decision.Run>(g.check("c4", "trading_price", gold, turn = 5, nowMs = 30_000))
    }

    @Test
    fun `argument order does not matter`() {
        val g = DuplicateCallGuard()
        g.check("c1", "trading_smc_analysis", mapOf("symbol" to "XAUUSD", "interval" to "15m"), turn = 1, nowMs = 0)
        g.recordResult("c1", "r")
        assertIs<DuplicateCallGuard.Decision.Repeat>(
            g.check("c2", "trading_smc_analysis", mapOf("interval" to "15m", "symbol" to "XAUUSD"), turn = 1, nowMs = 1)
        )
    }
}

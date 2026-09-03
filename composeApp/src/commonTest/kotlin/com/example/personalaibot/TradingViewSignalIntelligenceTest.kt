package com.example.personalaibot

import com.example.personalaibot.automation.TradingViewSignalIntelligence
import com.example.personalaibot.tools.trading.Candle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TradingViewSignalIntelligenceTest {
    private fun candles(up: Boolean): List<Candle> = (0 until 300).map { i ->
        val base = if (up) 1800.0 + i * 2.0 else 2400.0 - i * 2.0
        Candle(base - 0.5, base + 2.0, base - 2.0, base, 1000.0, i.toLong() * 60_000L)
    }

    @Test
    fun uptrendProducesBuyTimeframe() {
        val signal = TradingViewSignalIntelligence.analyze("1h", candles(true))
        assertEquals(TradingViewSignalIntelligence.Decision.BUY, signal.decision)
        assertTrue(signal.score > 0)
    }

    @Test
    fun downtrendProducesSellTimeframe() {
        val signal = TradingViewSignalIntelligence.analyze("1h", candles(false))
        assertEquals(TradingViewSignalIntelligence.Decision.SELL, signal.decision)
        assertTrue(signal.score < 0)
    }

    @Test
    fun conflictingTimeframesAreGatedToWait() {
        val buy = TradingViewSignalIntelligence.analyze("1h", candles(true))
        val sell = TradingViewSignalIntelligence.analyze("4h", candles(false))
        val result = TradingViewSignalIntelligence.fuse(listOf(buy, sell))
        assertEquals(TradingViewSignalIntelligence.Decision.WAIT, result.decision)
        assertEquals("NO_CONFLUENCE", result.gate)
    }

    @Test
    fun outcomeEvaluationUsesForwardBars() {
        val series = (0 until 20).map { i -> Candle(i.toDouble(), i + 1.0, i - 1.0, i.toDouble(), 1.0, i.toLong()) }
        val outcome = TradingViewSignalIntelligence.evaluateOutcome(TradingViewSignalIntelligence.Decision.BUY, series, 5)
        assertTrue(outcome != null)
        assertEquals(5, outcome!!.barsForward)
        assertTrue(outcome.returnPct > 0.0)
    }

    @Test
    fun missingHigherTimeframeDoesNotDiluteAvailableWeight() {
        val buy15 = TradingViewSignalIntelligence.TimeframeSignal("15m", 2.0, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.WAIT, TradingViewSignalIntelligence.Decision.BUY, 100, 300)
        val buy1h = TradingViewSignalIntelligence.TimeframeSignal("1h", 2.0, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.WAIT, TradingViewSignalIntelligence.Decision.BUY, 100, 300)
        val result = TradingViewSignalIntelligence.fuse(listOf(buy15, buy1h), indicatorScore = 1.0)
        assertEquals(TradingViewSignalIntelligence.Decision.BUY, result.decision)
        assertEquals("PASS", result.gate)
    }

    @Test
    fun indicatorScoreParticipatesInDecisionButCannotOverrideMissingConfluence() {
        val wait = TradingViewSignalIntelligence.analyze("1h", candles(true)).copy(
            decision = TradingViewSignalIntelligence.Decision.WAIT,
            score = 0.0
        )
        val result = TradingViewSignalIntelligence.fuse(listOf(wait), indicatorScore = 1.0)
        assertEquals(TradingViewSignalIntelligence.Decision.WAIT, result.decision)
        assertEquals("NO_CONFLUENCE", result.gate)
    }
}

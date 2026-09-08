package com.example.personalaibot

import com.example.personalaibot.automation.SignalOutcomeTracker
import com.example.personalaibot.tools.trading.Candle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignalOutcomeTrackerTest {

    @Test
    fun buySignalHitsTakeProfit() {
        val entry = 100.0
        val sl = 95.0
        val tp = 110.0
        val rr = 2.0
        val now = 1000L

        val candles = listOf(
            Candle(open = 100.0, high = 102.0, low = 98.0, close = 101.0, volume = 100.0, timestamp = 1000L),
            Candle(open = 101.0, high = 105.0, low = 100.5, close = 104.0, volume = 100.0, timestamp = 1060L),
            Candle(open = 104.0, high = 111.0, low = 103.5, close = 110.5, volume = 100.0, timestamp = 1120L)
        )

        val outcome = SignalOutcomeTracker.evaluateSingleSignal(
            side = "BUY",
            entryPrice = entry,
            stopLoss = sl,
            takeProfit = tp,
            rr = rr,
            createdAt = now,
            forwardCandles = candles
        )

        assertEquals("WIN", outcome.status)
        assertEquals(tp, outcome.exitPrice)
        assertEquals(rr, outcome.pnlR)
        assertTrue(outcome.mfeR >= 2.0)
        assertTrue(outcome.maeR <= 0.5) // (100 - 98) / 5 = 0.4
    }

    @Test
    fun buySignalHitsStopLoss() {
        val entry = 100.0
        val sl = 95.0
        val tp = 110.0
        val rr = 2.0
        val now = 1000L

        val candles = listOf(
            Candle(open = 100.0, high = 101.0, low = 99.0, close = 99.5, volume = 100.0, timestamp = 1000L),
            Candle(open = 99.5, high = 100.0, low = 94.5, close = 94.8, volume = 100.0, timestamp = 1060L)
        )

        val outcome = SignalOutcomeTracker.evaluateSingleSignal(
            side = "BUY",
            entryPrice = entry,
            stopLoss = sl,
            takeProfit = tp,
            rr = rr,
            createdAt = now,
            forwardCandles = candles
        )

        assertEquals("LOSS", outcome.status)
        assertEquals(sl, outcome.exitPrice)
        assertEquals(-1.0, outcome.pnlR)
        assertTrue(outcome.maeR >= 1.0)
    }

    @Test
    fun conservativeCollisionResolvesToLoss() {
        // If high touches TP but low touches SL in the exact same bar, conservative risk management treats it as LOSS
        val entry = 100.0
        val sl = 95.0
        val tp = 110.0
        val rr = 2.0
        val now = 1000L

        val candles = listOf(
            Candle(open = 100.0, high = 112.0, low = 93.0, close = 105.0, volume = 100.0, timestamp = 1000L)
        )

        val outcome = SignalOutcomeTracker.evaluateSingleSignal(
            side = "BUY",
            entryPrice = entry,
            stopLoss = sl,
            takeProfit = tp,
            rr = rr,
            createdAt = now,
            forwardCandles = candles
        )

        assertEquals("LOSS", outcome.status)
        assertEquals(sl, outcome.exitPrice)
        assertEquals(-1.0, outcome.pnlR)
    }

    @Test
    fun sellSignalHitsTakeProfit() {
        val entry = 200.0
        val sl = 205.0
        val tp = 190.0
        val rr = 2.0
        val now = 1000L

        val candles = listOf(
            Candle(open = 200.0, high = 201.0, low = 197.0, close = 198.0, volume = 100.0, timestamp = 1000L),
            Candle(open = 198.0, high = 199.0, low = 189.0, close = 189.5, volume = 100.0, timestamp = 1060L)
        )

        val outcome = SignalOutcomeTracker.evaluateSingleSignal(
            side = "SELL",
            entryPrice = entry,
            stopLoss = sl,
            takeProfit = tp,
            rr = rr,
            createdAt = now,
            forwardCandles = candles
        )

        assertEquals("WIN", outcome.status)
        assertEquals(tp, outcome.exitPrice)
        assertEquals(rr, outcome.pnlR)
    }

    @Test
    fun expiredTimeoutAfterFiftyBars() {
        val entry = 100.0
        val sl = 90.0
        val tp = 120.0
        val rr = 2.0
        val now = 1000L

        // 50 calm bars lingering around 102.0
        val candles = (1..50).map { i ->
            Candle(
                open = 101.0,
                high = 103.0,
                low = 99.0,
                close = 102.0,
                volume = 100.0,
                timestamp = now + i * 60L
            )
        }

        val outcome = SignalOutcomeTracker.evaluateSingleSignal(
            side = "BUY",
            entryPrice = entry,
            stopLoss = sl,
            takeProfit = tp,
            rr = rr,
            createdAt = now,
            forwardCandles = candles
        )

        assertEquals("EXPIRED", outcome.status)
        assertEquals(102.0, outcome.exitPrice)
        // (102 - 100) / 10 = +0.2R
        assertEquals(0.2, outcome.pnlR ?: 0.0, 0.001)
        assertEquals(50L, outcome.barsHeld)
    }

    @Test
    fun confidenceAdjustmentHotAndColdStrategies() {
        // High win rate and positive R -> Boost +0.20
        val hotBoost = SignalOutcomeTracker.computeConfidenceAdjustment(
            totalSignals = 10,
            wins = 7,
            losses = 3,
            winRatePct = 70.0,
            avgR = 0.65
        )
        assertEquals(0.20, hotBoost)

        // Moderate performance -> Boost +0.10
        val moderateBoost = SignalOutcomeTracker.computeConfidenceAdjustment(
            totalSignals = 10,
            wins = 6,
            losses = 4,
            winRatePct = 60.0,
            avgR = 0.25
        )
        assertEquals(0.10, moderateBoost)

        // Severe losing streak -> Penalty -0.25
        val coldPenalty = SignalOutcomeTracker.computeConfidenceAdjustment(
            totalSignals = 10,
            wins = 2,
            losses = 8,
            winRatePct = 20.0,
            avgR = -0.50
        )
        assertEquals(-0.25, coldPenalty)

        // Mild losing streak -> Penalty -0.15
        val mildPenalty = SignalOutcomeTracker.computeConfidenceAdjustment(
            totalSignals = 10,
            wins = 3,
            losses = 7,
            winRatePct = 35.0,
            avgR = -0.10
        )
        assertEquals(-0.15, mildPenalty)

        // Balanced / neutral performance -> 0.0
        val neutral = SignalOutcomeTracker.computeConfidenceAdjustment(
            totalSignals = 10,
            wins = 5,
            losses = 5,
            winRatePct = 50.0,
            avgR = 0.05
        )
        assertEquals(0.0, neutral)

        // Insufficient sample size (< 3) -> 0.0
        val fewTrades = SignalOutcomeTracker.computeConfidenceAdjustment(
            totalSignals = 2,
            wins = 2,
            losses = 0,
            winRatePct = 100.0,
            avgR = 2.0
        )
        assertEquals(0.0, fewTrades)
    }
}

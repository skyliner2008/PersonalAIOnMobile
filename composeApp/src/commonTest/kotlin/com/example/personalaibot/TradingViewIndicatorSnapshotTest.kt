package com.example.personalaibot

import com.example.personalaibot.automation.TradingViewIndicatorSnapshot
import com.example.personalaibot.tools.trading.Candle
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TradingViewIndicatorSnapshotTest {
    @Test
    fun snapshot_exposes_complete_indicator_matrix_without_raw_ohlcv() {
        val candles = (0 until 260).map { i ->
            val close = 2300.0 + i * 1.2 + kotlin.math.sin(i / 7.0) * 8.0
            Candle(
                open = close - 1.0,
                high = close + 5.0,
                low = close - 5.0,
                close = close,
                volume = 1000.0 + (i % 20) * 25.0,
                timestamp = i.toLong() * 60_000L
            )
        }
        val s = TradingViewIndicatorSnapshot.calculate(candles)
        assertNotNull(s)
        assertTrue(s.ema20.isFinite())
        assertTrue(s.ema50.isFinite())
        assertTrue(s.ema200.isFinite())
        assertTrue(s.rsi14 in 0.0..100.0)
        assertTrue(s.adx14 >= 0.0)
        assertTrue(s.stochasticK in 0.0..100.0)
        assertTrue(s.stochasticD in 0.0..100.0)
        assertTrue(s.mfi14 in 0.0..100.0)
        assertTrue(s.volumeRatio20 >= 0.0)
        assertTrue(s.indicatorScore in -12..12)
    }

    @Test
    fun insufficient_candles_are_rejected() {
        val candles = (0 until 100).map { i ->
            Candle(2300.0, 2305.0, 2295.0, 2301.0, 1000.0, i.toLong())
        }
        assertTrue(TradingViewIndicatorSnapshot.calculate(candles) == null)
    }
}

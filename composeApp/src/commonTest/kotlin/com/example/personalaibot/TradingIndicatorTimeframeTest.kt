package com.example.personalaibot

import com.example.personalaibot.automation.IndicatorAlertProvider
import com.example.personalaibot.tools.trading.TaIndicators
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TradingIndicatorTimeframeTest {

    @Test
    fun testTimeframeNormalizationForAllRequiredTf() {
        val requiredTf = listOf("m1", "m5", "m15", "h1", "h4", "d1", "w1")
        val expectedNorm = listOf("1m", "5m", "15m", "1h", "4h", "1D", "1W")
        val expectedMt5  = listOf("M1", "M5", "M15", "H1", "H4", "D1", "W1")
        val expectedTv   = listOf("1",  "5",  "15",  "60", "240", "D",  "W")

        for (i in requiredTf.indices) {
            val tf = requiredTf[i]
            assertEquals(expectedNorm[i], TaIndicators.normalizeTimeframe(tf), "normalizeTimeframe for $tf")
            assertEquals(expectedMt5[i],  TaIndicators.toMt5Timeframe(tf),      "toMt5Timeframe for $tf")
            assertEquals(expectedTv[i],   TaIndicators.toTvResolution(tf),      "toTvResolution for $tf")

            // Test upper case versions like M1, M5, M15, H1, H4, D1, W1
            val upperTf = expectedMt5[i]
            assertEquals(expectedNorm[i], TaIndicators.normalizeTimeframe(upperTf), "normalizeTimeframe for $upperTf")
            assertEquals(expectedMt5[i],  TaIndicators.toMt5Timeframe(upperTf),      "toMt5Timeframe for $upperTf")
            assertEquals(expectedTv[i],   TaIndicators.toTvResolution(upperTf),      "toTvResolution for $upperTf")

            // Test alias versions like 1m, 5m, 15m, 1h, 4h, 1D, 1W
            val aliasTf = expectedNorm[i]
            assertEquals(expectedNorm[i], TaIndicators.normalizeTimeframe(aliasTf), "normalizeTimeframe for $aliasTf")
            assertEquals(expectedMt5[i],  TaIndicators.toMt5Timeframe(aliasTf),      "toMt5Timeframe for $aliasTf")
            assertEquals(expectedTv[i],   TaIndicators.toTvResolution(aliasTf),      "toTvResolution for $aliasTf")
        }
    }

    @Test
    fun testIndicatorAlertProviderSplitSymbolAndTf() {
        // Test standard MT5 format
        assertEquals("XAUUSD" to "1m",  IndicatorAlertProvider.splitSymbolAndTf("XAUUSD@m1"))
        assertEquals("XAUUSD" to "5m",  IndicatorAlertProvider.splitSymbolAndTf("XAUUSD@M5"))
        assertEquals("XAUUSD" to "15m", IndicatorAlertProvider.splitSymbolAndTf("XAUUSD@m15"))
        assertEquals("XAUUSD" to "1h",  IndicatorAlertProvider.splitSymbolAndTf("XAUUSD@H1"))
        assertEquals("XAUUSD" to "4h",  IndicatorAlertProvider.splitSymbolAndTf("XAUUSD@h4"))
        assertEquals("XAUUSD" to "1D",  IndicatorAlertProvider.splitSymbolAndTf("XAUUSD@D1"))
        assertEquals("XAUUSD" to "1W",  IndicatorAlertProvider.splitSymbolAndTf("XAUUSD@W1"))

        // Test TradingView format
        assertEquals("BTCUSDT" to "1m",  IndicatorAlertProvider.splitSymbolAndTf("BTCUSDT@1m"))
        assertEquals("BTCUSDT" to "5m",  IndicatorAlertProvider.splitSymbolAndTf("BTCUSDT@5m"))
        assertEquals("BTCUSDT" to "15m", IndicatorAlertProvider.splitSymbolAndTf("BTCUSDT@15m"))
        assertEquals("BTCUSDT" to "1h",  IndicatorAlertProvider.splitSymbolAndTf("BTCUSDT@1h"))
        assertEquals("BTCUSDT" to "4h",  IndicatorAlertProvider.splitSymbolAndTf("BTCUSDT@4h"))
        assertEquals("BTCUSDT" to "1D",  IndicatorAlertProvider.splitSymbolAndTf("BTCUSDT@1D"))
        assertEquals("BTCUSDT" to "1W",  IndicatorAlertProvider.splitSymbolAndTf("BTCUSDT@1W"))

        // Default fallback
        assertEquals("EURUSD" to "1h", IndicatorAlertProvider.splitSymbolAndTf("EURUSD"))
    }

    @Test
    fun testIndicatorCalculationsPureMath() {
        val count = 100
        val closes = (0 until count).map { i -> 2000.0 + i * 0.5 + kotlin.math.sin(i / 5.0) * 10.0 }
        val highs  = closes.map { it + 3.0 }
        val lows   = closes.map { it - 3.0 }

        // 1. RSI
        val rsiVal = TaIndicators.rsi(closes, 14)
        assertNotNull(rsiVal)
        assertTrue(rsiVal in 0.0..100.0, "RSI must be between 0 and 100, was $rsiVal")

        // 2. MACD
        val macdVal = TaIndicators.macd(closes, 12, 26, 9)
        assertNotNull(macdVal)
        assertTrue(macdVal.macd.isFinite(), "MACD line must be finite")
        assertTrue(macdVal.signal.isFinite(), "MACD signal must be finite")
        assertTrue(macdVal.hist.isFinite(), "MACD hist must be finite")
        assertEquals(macdVal.macd - macdVal.signal, macdVal.hist, 1e-6)

        // 3. Stochastic (14, 3, 3)
        val stochVal = TaIndicators.stochastic(closes, highs, lows, 14, 3, 3)
        assertNotNull(stochVal)
        assertTrue(stochVal.k in 0.0..100.0, "Stochastic %K must be between 0 and 100, was ${stochVal.k}")
        assertTrue(stochVal.d in 0.0..100.0, "Stochastic %D must be between 0 and 100, was ${stochVal.d}")

        // 4. ATR
        val atrVal = TaIndicators.atr(highs, lows, closes, 14)
        assertNotNull(atrVal)
        assertTrue(atrVal > 0.0, "ATR must be positive, was $atrVal")

        // 5. Bollinger Bands
        val bbVal = TaIndicators.bollingerBands(closes, 20, 2.0)
        assertNotNull(bbVal)
        assertTrue(bbVal.upper > bbVal.basis, "Upper band must be above basis")
        assertTrue(bbVal.lower < bbVal.basis, "Lower band must be below basis")
        assertTrue(bbVal.width > 0.0, "BB width must be positive")

        // 6. CCI
        val cciVal = TaIndicators.cci(highs, lows, closes, 20)
        assertNotNull(cciVal)
        assertTrue(cciVal.isFinite(), "CCI must be finite")

        // 7. ADX
        val adxVal = TaIndicators.adx(highs, lows, closes, 14)
        assertNotNull(adxVal)
        assertTrue(adxVal.adx >= 0.0, "ADX must be non-negative")
        assertTrue(adxVal.diPlus >= 0.0, "+DI must be non-negative")
        assertTrue(adxVal.diMinus >= 0.0, "-DI must be non-negative")

        // 8. Supertrend
        val stVal = TaIndicators.supertrend(highs, lows, closes, 10, 3.0)
        assertNotNull(stVal)
        assertTrue(stVal.value > 0.0, "Supertrend value must be positive")
    }
}

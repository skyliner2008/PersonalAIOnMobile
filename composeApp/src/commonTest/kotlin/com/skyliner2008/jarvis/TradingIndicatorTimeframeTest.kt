package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.IndicatorAlertProvider
import com.skyliner2008.jarvis.tools.trading.SmcApiService
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    /**
     * P0: ทุก TF ในทะเบียนต้องแปลงได้ครบทั้ง 4 รูปแบบ และไม่มีตัวไหน "ตกเงียบ" ไป 1h
     *
     * บัคเดิม: 6h/8h/12h ไม่มีใน toTvResolution → คืน "60" (1 ชม.),
     * และ 8h ไม่มีใน SmcApiService.intervalToMillis → คืน 3,600,000 ms
     * ผู้ใช้ขอ 8h แล้วได้ข้อมูล 1h โดยไม่มี error
     */
    @Test
    fun everyRegisteredTimeframeRoundTripsWithoutSilentFallback() {
        for (spec in TaIndicators.TIMEFRAMES) {
            // canonical / MT5 ต้องชี้กลับมาที่ spec เดิมเสมอ (idempotent round-trip)
            // หมายเหตุ: ไม่ทดสอบ canonical.uppercase() เพราะ "1m".uppercase() = "1M" = รายเดือน
            // — สองตัวนี้ต่างกันที่ตัวพิมพ์โดยเจตนา
            for (input in listOf(spec.canonical, spec.mt5, spec.mt5.lowercase())) {
                assertEquals(spec.canonical, TaIndicators.normalizeTimeframe(input), "normalize($input)")
                assertEquals(spec.mt5, TaIndicators.toMt5Timeframe(input), "mt5($input)")
                assertEquals(spec.tvResolution, TaIndicators.toTvResolution(input), "tv($input)")
                assertEquals(spec.millis, TaIndicators.timeframeMillis(input), "millis($input)")
            }
            // millis ต้องตรงกับ SmcApiService ที่ delegate มาใช้ทะเบียนเดียวกัน
            assertEquals(
                spec.millis,
                SmcApiService.intervalToMillis(spec.canonical),
                "SmcApiService.intervalToMillis(${spec.canonical})"
            )
        }
    }

    /** TF ที่เคยตกหล่น — ยืนยันเฉพาะเจาะจงว่าไม่กลายเป็น 1h อีก */
    @Test
    fun previouslyMissingTimeframesResolveCorrectly() {
        assertEquals("360", TaIndicators.toTvResolution("6h"))
        assertEquals("480", TaIndicators.toTvResolution("8h"))
        assertEquals("720", TaIndicators.toTvResolution("12h"))
        assertEquals(6 * 3_600_000L, SmcApiService.intervalToMillis("6h"))
        assertEquals(8 * 3_600_000L, SmcApiService.intervalToMillis("8h"))
        assertEquals(12 * 3_600_000L, SmcApiService.intervalToMillis("12h"))
        // "1m" ต้องเป็น 1 นาที ไม่ใช่รายเดือน (canonical รายเดือน "1M" lowercase ชนกันพอดี)
        assertEquals("1m", TaIndicators.normalizeTimeframe("1m"))
        assertEquals(60_000L, TaIndicators.timeframeMillis("1m"))
    }

    /**
     * P0: อินดิเคเตอร์ต้อง fail-closed — ข้อมูลไม่พอต้องคืน null ไม่ใช่ค่าประมาณ
     *
     * บัคเดิม: ema() คืน `data.last()` เมื่อแท่งไม่ครบ period
     * → EMA200 บน 15m (ระบบดึงมา 150 แท่ง) เท่ากับราคาปิดพอดีทุกครั้ง
     * แล้ว AI นำไปสรุปว่า "ราคาแตะ EMA200" ทั้งที่ไม่เคยคำนวณเลย
     */
    @Test
    fun movingAveragesReturnNullInsteadOfFabricatingValues() {
        val closes = (0 until 150).map { 2000.0 + it * 0.5 }
        val lastClose = closes.last()

        // ข้อมูลไม่พอ → ต้องเป็น null และต้องไม่เท่ากับราคาปิด
        assertNull(TaIndicators.ema(closes, 200), "EMA200 จาก 150 แท่งต้องเป็น null")
        assertNull(TaIndicators.sma(closes, 200), "SMA200 จาก 150 แท่งต้องเป็น null")
        assertNull(TaIndicators.ema(closes, 151))
        assertNull(TaIndicators.sma(closes, 151))
        assertNull(TaIndicators.ema(emptyList(), 20))
        assertNull(TaIndicators.sma(emptyList(), 20))
        assertNull(TaIndicators.ema(closes, 0))
        assertNull(TaIndicators.sma(closes, 0))

        // ข้อมูลพอดี/เกิน → ต้องคำนวณได้จริง
        val ema150 = TaIndicators.ema(closes, 150)
        assertNotNull(ema150)
        assertTrue(ema150.isFinite())
        assertTrue(ema150 != lastClose, "EMA ต้องไม่บังเอิญเท่าราคาปิด (บ่งชี้ว่ายัง fallback อยู่)")

        val sma20 = TaIndicators.sma(closes, 20)
        assertNotNull(sma20)
        // SMA20 ของ arithmetic series = ค่ากลางของ 20 ค่าสุดท้าย
        assertEquals(closes.takeLast(20).average(), sma20, 1e-9)
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

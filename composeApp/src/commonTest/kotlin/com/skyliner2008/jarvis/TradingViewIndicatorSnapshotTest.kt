package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.TradingViewIndicatorSnapshot
import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TradingViewIndicatorSnapshotTest {

    private fun candles(count: Int) = (0 until count).map { i ->
        val close = 2300.0 + i * 1.2 + kotlin.math.sin(i / 7.0) * 8.0
        Candle(
            open = close - 1.0,
            high = close + 5.0,
            low = close - 5.0,
            close = close,
            volume = 1000.0 + (i % 20) * 25.0,
            timestamp = i.toLong() * 3_600_000L,
            isClosed = true
        )
    }

    @Test
    fun snapshot_exposes_complete_indicator_matrix_without_raw_ohlcv() {
        val s = TradingViewIndicatorSnapshot.calculate(candles(TaIndicators.Warmup.FULL_SET))
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

    /**
     * snapshot ต้อง fail-closed ทั้งก้อน
     *
     * เดิมต้องการแค่ 220 แท่ง ซึ่งไม่พอให้ EMA200 ลู่เข้า และเมื่อคำนวณไม่ได้ก็ใส่ค่าปลอมแทน
     * (`rsi(...) ?: 50.0`, `adx(...) = Triple(0,0,0)`) แล้วส่งให้ AI เหมือนเป็นค่าจริง
     */
    @Test
    fun insufficient_candles_are_rejected() {
        assertNull(TradingViewIndicatorSnapshot.calculate(candles(100)))
        assertNull(TradingViewIndicatorSnapshot.calculate(candles(220)))
        assertNull(TradingViewIndicatorSnapshot.calculate(candles(TaIndicators.Warmup.FULL_SET - 1)))
        assertNotNull(TradingViewIndicatorSnapshot.calculate(candles(TaIndicators.Warmup.FULL_SET)))
    }

    /**
     * ทุกค่าใน snapshot ต้องตรงกับ TaIndicators เป๊ะ — ห้ามมีอิมพลีเมนต์ที่ 2
     *
     * บัคเดิมที่เทสต์นี้ล็อกไว้: `adx()` ภายใน snapshot คืน **DX ดิบ** ไม่ใช่ ADX ที่ smooth แล้ว
     * และ `supertrend()` เป็นแค่ ATR band รอบ mid ของแท่งล่าสุด ไม่ใช่ Supertrend
     * ทำให้เกณฑ์ `adx >= 25` เทียบกับตัวเลขคนละสเกลกับที่ tool อื่นใช้
     */
    @Test
    fun snapshot_values_match_central_indicator_library() {
        val c = candles(TaIndicators.Warmup.FULL_SET)
        val s = TradingViewIndicatorSnapshot.calculate(c)
        assertNotNull(s)

        val closes = c.map { it.close }
        val highs = c.map { it.high }
        val lows = c.map { it.low }
        val volumes = c.map { it.volume }

        assertEquals(TaIndicators.ema(closes, 20)!!, s.ema20, 1e-9)
        assertEquals(TaIndicators.ema(closes, 50)!!, s.ema50, 1e-9)
        assertEquals(TaIndicators.ema(closes, 200)!!, s.ema200, 1e-9)
        assertEquals(TaIndicators.rsi(closes, 14)!!, s.rsi14, 1e-9)

        val adx = TaIndicators.adx(highs, lows, closes, 14)!!
        assertEquals(adx.adx, s.adx14, 1e-9, "adx14 ต้องเป็น ADX ที่ smooth แล้ว ไม่ใช่ DX ดิบ")
        assertEquals(adx.diPlus, s.plusDi14, 1e-9)
        assertEquals(adx.diMinus, s.minusDi14, 1e-9)

        assertEquals(TaIndicators.atr(highs, lows, closes, 14)!!, s.atr14, 1e-9)
        assertEquals(TaIndicators.supertrend(highs, lows, closes, 10, 3.0)!!.value, s.supertrend, 1e-9)
        assertEquals(TaIndicators.mfi(highs, lows, closes, volumes, 14)!!, s.mfi14, 1e-9)
        assertEquals(TaIndicators.cci(highs, lows, closes, 20)!!, s.cci20, 1e-9)

        val bb = TaIndicators.bollingerBands(closes, 20, 2.0)!!
        assertEquals(bb.upper, s.bbUpper, 1e-9)
        assertEquals(bb.lower, s.bbLower, 1e-9)

        val macd = TaIndicators.macd(closes)!!
        assertEquals(macd.macd, s.macd, 1e-9)
        assertEquals(macd.signal, s.macdSignal, 1e-9)

        val stoch = TaIndicators.stochastic(closes, highs, lows)!!
        assertEquals(stoch.k, s.stochasticK, 1e-9)
        assertEquals(stoch.d, s.stochasticD, 1e-9)
    }
}

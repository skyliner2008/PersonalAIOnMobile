package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.tools.ToolExecutor
import com.skyliner2008.jarvis.tools.trading.NewsTone
import com.skyliner2008.jarvis.tools.trading.TvScannerColumns
import com.skyliner2008.jarvis.tools.trading.YahooSeries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tool results checked against independent data (2026-10-03): the pieces that gave wrong numbers. */
class ToolDataAccuracyTest {

    @Test
    fun `scanner columns ask for the timeframe the user asked for`() {
        // plain = daily; RSI|60 = 1h (BTCUSDT 2026-10-03: RSI 63.8 daily, RSI|60 49.4 — the 1h tool showed 63.8)
        assertEquals("|60", TvScannerColumns.suffix("1h"))
        assertEquals("|60", TvScannerColumns.suffix("H1"))
        assertEquals("", TvScannerColumns.suffix("1D"))
        assertEquals("", TvScannerColumns.suffix("D1"))
        assertEquals("|240", TvScannerColumns.suffix("4h"))
        assertEquals("|15", TvScannerColumns.suffix("15m"))
        assertEquals("|1", TvScannerColumns.suffix("1m"))
        assertEquals("|120", TvScannerColumns.suffix("2h"))
        assertEquals("|1W", TvScannerColumns.suffix("1W"))
        assertEquals("|1M", TvScannerColumns.suffix("1M"))
        // no such scanner timeframe: the caller uses its own candles
        assertNull(TvScannerColumns.suffix("3m"))
        assertNull(TvScannerColumns.suffix("8h"))
    }

    @Test
    fun `bollinger width is worked out from the bands`() {
        // BTCUSDT 1h 2026-10-03: 84308.64 / 84595.16 / 84881.68 → 0.677 %
        assertEquals(0.6774, TvScannerColumns.bbWidthPct(84881.68, 84308.64, 84595.16)!!, 1e-3)
        assertNull(TvScannerColumns.bbWidthPct(null, 1.0, 1.0))
        assertNull(TvScannerColumns.bbWidthPct(2.0, 1.0, 0.0))
    }

    @Test
    fun `headline tone counts whole words only`() {
        assertTrue(NewsTone.bullish("Bitcoin surges past 85,000 as ETF inflows resume"))
        assertTrue(NewsTone.bearish("Bitcoin, Ethereum, XRP pare earlier gains as sentiment turns negative"))
        assertTrue(NewsTone.bearish("Crypto market slides after Fed warning"))
        // the old substring test: "high" in highlight, "low" in follow / slow, "ath" in path, "buy" in buyback
        assertFalse(NewsTone.bullish("Highlights: the path to the next halving"))
        assertFalse(NewsTone.bearish("Follow the slow money"))
        assertFalse(NewsTone.bullish("Company announces buyback plan"))
    }

    @Test
    fun `correlation takes app symbols`() {
        assertEquals("BTC-USD", YahooSeries.symbolFor("BTCUSDT"))
        assertEquals("ETH-USD", YahooSeries.symbolFor("ethusdt"))
        assertEquals("GC=F", YahooSeries.symbolFor("XAUUSD"))
        assertEquals("EURUSD=X", YahooSeries.symbolFor("EURUSD"))
        assertEquals("^GSPC", YahooSeries.symbolFor("SPX"))
        assertEquals("BTC-USD", YahooSeries.symbolFor("BTCUSD"))
        // Yahoo forms pass through
        assertEquals("^GSPC", YahooSeries.symbolFor("^GSPC"))
        assertEquals("GC=F", YahooSeries.symbolFor("GC=F"))
        assertEquals("PTT.BK", YahooSeries.symbolFor("PTT.BK"))
    }

    @Test
    fun `correlation pairs returns of the same dates`() {
        // crypto trades every day, stocks skip the weekend (days 3 and 4)
        val crypto = mapOf(1L to 100.0, 2L to 110.0, 3L to 50.0, 4L to 200.0, 5L to 121.0)
        val stock = mapOf(1L to 10.0, 2L to 11.0, 5L to 12.1)
        val r = YahooSeries.alignedReturns(mapOf("BTC" to crypto, "SPX" to stock))
        // common dates 1, 2, 5: day 5 is compared with day 2 for both
        assertEquals(listOf(0.1, 0.1), r.getValue("BTC").map { kotlin.math.round(it * 1000) / 1000.0 })
        assertEquals(listOf(0.1, 0.1), r.getValue("SPX").map { kotlin.math.round(it * 1000) / 1000.0 })
    }

    @Test
    fun `a calculation does not read out floating point noise`() {
        assertEquals("62.75", ToolExecutor.formatCalcResult((4185.10 - 4160) * 2.5))
        assertEquals("3", ToolExecutor.formatCalcResult(3.0))
        assertEquals("0.3333333333", ToolExecutor.formatCalcResult(1.0 / 3.0))
        assertEquals("-12.5", ToolExecutor.formatCalcResult(-12.5))
        assertEquals("1.0E-9", ToolExecutor.formatCalcResult(1e-9))
    }
}

package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.SignalAlertProvider
import com.skyliner2008.jarvis.automation.TradingSignalMarketDataRouter
import com.skyliner2008.jarvis.automation.signalKindOf
import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.SmcApiService
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignalAlertProviderTest {

    @Test
    fun testSignalKindOf_extractsCorrectStrategyKindWithoutDroppingDigits() {
        assertEquals("MOM", signalKindOf("MOM▲"))
        assertEquals("MOM", signalKindOf("MOM▼"))
        assertEquals("REV", signalKindOf("REV▲"))
        assertEquals("REV", signalKindOf("REV▼"))
        assertEquals("TR", signalKindOf("TR▲"))
        assertEquals("DC", signalKindOf("DC▼"))
        assertEquals("52H", signalKindOf("52H▲"), "Numbers must not be stripped from 52H")
        assertEquals("3BR", signalKindOf("3BR▼"), "Numbers must not be stripped from 3BR")
        assertEquals("E", signalKindOf("E14/60▲"), "E prefix must normalize to E")
        assertEquals("UT", signalKindOf("UT▼"))
        assertEquals("VEYRA", signalKindOf("VEYRA▲"))
        assertEquals("BBSQ", signalKindOf("BBSQ▼"))
        assertEquals("FRSI", signalKindOf("FRSI▲"))
    }

    @Test
    fun testComputeTpSl_buyAndSellOrdersProduceLogicalLevels() {
        val provider = SignalAlertProvider(SmcApiService(HttpClient()))
        val baseCandle = Candle(
            open = 2000.0,
            high = 2015.0,
            low = 1985.0,
            close = 2005.0,
            volume = 100.0,
            timestamp = 1700000000000L
        )
        val candles = List(10) { baseCandle }
        val idx = 5
        val atr14 = 12.0
        val atr6 = 8.0

        for (kind in listOf("MOM", "REV", "TR", "DC")) {
            // BUY scenario
            val (buySl, buyTp) = provider.computeTpSl(kind, "BUY", candles, idx, atr14, atr6)
            assertTrue(buySl < baseCandle.close, "$kind BUY SL ($buySl) must be below entry (${baseCandle.close})")
            assertTrue(buyTp > baseCandle.close, "$kind BUY TP ($buyTp) must be above entry (${baseCandle.close})")

            // SELL scenario
            val (sellSl, sellTp) = provider.computeTpSl(kind, "SELL", candles, idx, atr14, atr6)
            assertTrue(sellSl > baseCandle.close, "$kind SELL SL ($sellSl) must be above entry (${baseCandle.close})")
            assertTrue(sellTp < baseCandle.close, "$kind SELL TP ($sellTp) must be below entry (${baseCandle.close})")
        }
    }

    @Test
    fun testTradingSignalMarketDataRouter_delegatesAccuratelyBasedOnLiveModeAndConnection() = runBlocking {
        var mt5Called = false
        val dummyCandle = Candle(open = 2000.0, high = 2005.0, low = 1995.0, close = 2002.0, volume = 50.0, timestamp = 1700000000000L)

        // Case 1: When liveMode is false (DEMO/PAPER), MT5 provider is bypassed
        TradingSignalMarketDataRouter.configure(
            liveModeProvider = { false },
            mt5ConnectedProvider = { true },
            mt5CandleProvider = { _, _, _ ->
                mt5Called = true
                List(65) { dummyCandle }
            }
        )
        val resDemo = TradingSignalMarketDataRouter.fetch("XAUUSD", "15m", 10) {
            listOf(dummyCandle)
        }
        assertEquals(false, mt5Called)
        assertEquals(1, resDemo.first.size)
        assertEquals("TRADINGVIEW", resDemo.second)
        assertEquals("DEMO_FORCES_TV", resDemo.third)

        // Case 2: When liveMode is true AND mt5Connected is true and candles >= 62, MT5_LIVE is returned
        mt5Called = false
        TradingSignalMarketDataRouter.configure(
            liveModeProvider = { true },
            mt5ConnectedProvider = { true },
            mt5CandleProvider = { _, _, _ ->
                mt5Called = true
                List(65) { dummyCandle }
            }
        )
        val resLive = TradingSignalMarketDataRouter.fetch("XAUUSD", "15m", 65) {
            emptyList()
        }
        assertEquals(true, mt5Called)
        assertEquals(65, resLive.first.size)
        assertEquals("MT5_LIVE", resLive.second)
        assertEquals("MT5_LIVE_CONNECTED", resLive.third)
    }

    @Test
    fun testStitchLiveBar_updatesForming15mBarWithLatestM1Data() {
        val provider = SignalAlertProvider(SmcApiService(HttpClient()))
        val bucketStart = 1700000000000L // Assume 15m bucket
        val closedCandle = Candle(open = 2490.0, high = 2495.0, low = 2488.0, close = 2492.0, volume = 40.0, timestamp = bucketStart - 900_000L)
        val forming15m = Candle(open = 2500.0, high = 2505.0, low = 2498.0, close = 2502.0, volume = 50.0, timestamp = bucketStart)
        val baseCandles = listOf(closedCandle, forming15m)

        val m1Bar1 = Candle(open = 2500.0, high = 2506.0, low = 2497.0, close = 2504.0, volume = 20.0, timestamp = bucketStart)
        val m1Bar2 = Candle(open = 2504.0, high = 2512.0, low = 2501.0, close = 2511.5, volume = 35.0, timestamp = bucketStart + 60_000L)
        val m1Candles = listOf(m1Bar1, m1Bar2)

        val stitched = provider.stitchLiveBar(baseCandles, m1Candles, "15m")
        assertEquals(2, stitched.size)
        val updatedLive = stitched.last()

        assertEquals(bucketStart, updatedLive.timestamp)
        assertEquals(2500.0, updatedLive.open, "Open should remain the initial bucket open")
        assertEquals(2512.0, updatedLive.high, "High should be updated to highest m1 high")
        assertEquals(2497.0, updatedLive.low, "Low should be updated to lowest m1 low")
        assertEquals(2511.5, updatedLive.close, "Close should be updated to latest m1 close")
        assertTrue(updatedLive.volume >= 50.0, "Volume should accumulate")
    }

    @Test
    fun testStitchLiveBar_appendsNewBucketWhen15mCandleLags() {
        val provider = SignalAlertProvider(SmcApiService(HttpClient()))
        val bucketStart = 1700000000000L
        val closedCandle = Candle(open = 2490.0, high = 2495.0, low = 2488.0, close = 2492.0, volume = 40.0, timestamp = bucketStart - 900_000L)
        val baseCandles = listOf(closedCandle)

        val m1Bar = Candle(open = 2500.0, high = 2508.0, low = 2496.0, close = 2507.0, volume = 25.0, timestamp = bucketStart + 120_000L)
        val stitched = provider.stitchLiveBar(baseCandles, listOf(m1Bar), "15m")

        assertEquals(2, stitched.size, "Should append newly forming bucket candle")
        val newBar = stitched.last()
        assertEquals(bucketStart, newBar.timestamp)
        assertEquals(2500.0, newBar.open)
        assertEquals(2508.0, newBar.high)
        assertEquals(2496.0, newBar.low)
        assertEquals(2507.0, newBar.close)
    }

    @Test
    fun testStitchLiveBar_preservesCandlesFor1mTimeframe() {
        val provider = SignalAlertProvider(SmcApiService(HttpClient()))
        val candle = Candle(open = 2500.0, high = 2505.0, low = 2495.0, close = 2502.0, volume = 10.0, timestamp = 1700000000000L)
        val base = listOf(candle)
        val stitched = provider.stitchLiveBar(base, base, "1m")
        assertEquals(base, stitched)
    }
}

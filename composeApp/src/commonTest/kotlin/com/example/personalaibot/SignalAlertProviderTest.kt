package com.example.personalaibot

import com.example.personalaibot.automation.SignalAlertProvider
import com.example.personalaibot.automation.TradingSignalMarketDataRouter
import com.example.personalaibot.automation.signalKindOf
import com.example.personalaibot.tools.trading.Candle
import com.example.personalaibot.tools.trading.SmcApiService
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
}

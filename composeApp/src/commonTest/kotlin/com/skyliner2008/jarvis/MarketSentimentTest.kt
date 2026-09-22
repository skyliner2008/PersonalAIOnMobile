package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.tools.trading.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarketSentimentTest {

    @Test
    fun testBinancePositioningDivergenceSignal() {
        val contrarianBullish = BinancePositioningSentiment(
            symbol = "BTCUSDT",
            retailLongAccountPct = 40.0,
            retailShortAccountPct = 60.0,
            retailLongShortRatio = 0.67,
            topTraderLongPositionPct = 70.0,
            topTraderShortPositionPct = 30.0,
            topTraderLongShortRatio = 2.33,
            takerBuySellRatio = 1.15,
            divergenceSignal = "🟢 Contrarian Bullish (รายย่อย Short หนัก / Smart Money ถือ Long ได้เปรียบ Short Squeeze)"
        )
        assertTrue(contrarianBullish.divergenceSignal.contains("Contrarian Bullish"))
        assertTrue(contrarianBullish.retailLongShortRatio < 1.0)
        assertTrue(contrarianBullish.topTraderLongShortRatio > 1.3)

        val contrarianBearish = BinancePositioningSentiment(
            symbol = "ETHUSDT",
            retailLongAccountPct = 68.0,
            retailShortAccountPct = 32.0,
            retailLongShortRatio = 2.12,
            topTraderLongPositionPct = 42.0,
            topTraderShortPositionPct = 58.0,
            topTraderLongShortRatio = 0.72,
            takerBuySellRatio = 0.85,
            divergenceSignal = "🔴 Contrarian Bearish (รายย่อยไล่ Long สูง / Smart Money ดัก Short เสี่ยงโดนทุบ Long Squeeze)"
        )
        assertTrue(contrarianBearish.divergenceSignal.contains("Contrarian Bearish"))
    }

    @Test
    fun testCnnFearAndGreedDataStructure() {
        val cnn = CnnFearAndGreedData(
            score = 34.0,
            rating = "fear",
            ratingThai = "วิตกกังวล (Fear)",
            previousClose = 33.7,
            previous1Week = 28.0,
            previous1Month = 54.6,
            previous1Year = 66.5,
            subIndicators = listOf(
                CnnSubIndicator("market_volatility_vix", "ดัชนีความผันผวน (VIX vs 50-day MA)", 50.0, "neutral"),
                CnnSubIndicator("safe_haven_demand", "ความต้องการสินทรัพย์ปลอดภัย (Stocks vs Bonds)", 60.0, "greed")
            )
        )
        assertEquals(34.0, cnn.score)
        assertEquals("fear", cnn.rating)
        assertEquals(2, cnn.subIndicators.size)
        assertEquals("neutral", cnn.subIndicators[0].rating)
    }

    @Test
    fun testTradingToolDomainRoutingForSentiment() {
        assertEquals(TradingToolRouter.Domain.SENTIMENT, TradingToolRouter.domainOf("trading_sentiment"))
        assertEquals(TradingToolRouter.Domain.SENTIMENT, TradingToolRouter.domainOf("trading_fear_greed"))
        assertEquals(TradingToolRouter.Domain.SENTIMENT, TradingToolRouter.domainOf("trading_news"))
    }

    @Test
    fun testMeterBarGeneration() {
        val bar0 = UnifiedCompositeSentiment.makeMeterBar(0.0, 10)
        assertEquals("[░░░░░░░░░░]", bar0)

        val bar50 = UnifiedCompositeSentiment.makeMeterBar(50.0, 10)
        assertEquals("[█████░░░░░]", bar50)

        val bar100 = UnifiedCompositeSentiment.makeMeterBar(100.0, 10)
        assertEquals("[██████████]", bar100)

        val bar78 = UnifiedCompositeSentiment.makeMeterBar(78.0, 10)
        assertEquals("[████████░░]", bar78)
    }

    @Test
    fun testScoreClassification() {
        val (labelExFear, thExFear, emojiExFear) = UnifiedCompositeSentiment.classifyScore(15.0)
        assertEquals("Extreme Fear", labelExFear)
        assertEquals("กลัวสุดขีด (Extreme Fear)", thExFear)
        assertEquals("🥶 🔴", emojiExFear)

        val (labelFear, thFear, _) = UnifiedCompositeSentiment.classifyScore(35.0)
        assertEquals("Fear", labelFear)
        assertEquals("วิตกกังวล (Fear)", thFear)

        val (labelNeutral, thNeutral, emojiNeutral) = UnifiedCompositeSentiment.classifyScore(50.0)
        assertEquals("Neutral", labelNeutral)
        assertEquals("เป็นกลาง (Neutral)", thNeutral)
        assertEquals("⚖️ 😐", emojiNeutral)

        val (labelGreed, thGreed, _) = UnifiedCompositeSentiment.classifyScore(65.0)
        assertEquals("Greed", labelGreed)
        assertEquals("เชื่อมั่น / โลภ (Greed)", thGreed)

        val (labelExGreed, thExGreed, emojiExGreed) = UnifiedCompositeSentiment.classifyScore(85.0)
        assertEquals("Extreme Greed", labelExGreed)
        assertEquals("โลภสุดขีด (Extreme Greed)", thExGreed)
        assertEquals("🔥 🟢", emojiExGreed)
    }

    @Test
    fun testUnifiedCompositeSentimentModel() {
        val pillars = listOf(
            SentimentPillar("News & Social", 60.0, 30, "Bullish bias"),
            SentimentPillar("Market F&G", 70.0, 25, "Greed"),
            SentimentPillar("Positioning", 80.0, 30, "Smart money long"),
            SentimentPillar("Technical", 65.0, 15, "Buy signal")
        )
        val composite = UnifiedCompositeSentiment(
            target = "BTC",
            isGlobalMacro = false,
            compositeScore = 69.25,
            compositeLabel = "GREED",
            labelThai = "เชื่อมั่น / โลภ (Greed)",
            emoji = "🟢 🚀",
            meterBar = "[███████░░░]",
            pillars = pillars,
            contrarianAlert = null
        )
        assertEquals("BTC", composite.target)
        assertEquals(4, composite.pillars.size)
        assertEquals(69.25, composite.compositeScore)
        assertEquals("GREED", composite.compositeLabel)
    }
}

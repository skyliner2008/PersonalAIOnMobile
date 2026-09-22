package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.tools.trading.TradingApiService
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assume
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ยิง API จริง (Binance Futures, CNN, Google News) — ไม่รันในชุดเทสต์ปกติ
 * เพราะผลขึ้นกับเน็ตและ rate limit ภายนอก; รันเองด้วย RUN_LIVE_TESTS=1
 */
class MarketSentimentLiveIntegrationTest {

    @Before
    fun requireLiveFlag() {
        Assume.assumeTrue("ตั้ง RUN_LIVE_TESTS=1 เพื่อรันเทสต์ที่ใช้เครือข่ายจริง", System.getenv("RUN_LIVE_TESTS") == "1")
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }

    private val api = TradingApiService(client)

    @Test
    fun testLiveBinancePositioningForBtc() = runBlocking {
        val positioning = api.getBinanceFuturesPositioning("BTCUSDT")
        assertNotNull(positioning, "Binance positioning should not be null for BTCUSDT")
        assertTrue(positioning.retailLongAccountPct > 0.0, "Retail Long % should be > 0")
        assertTrue(positioning.retailShortAccountPct > 0.0, "Retail Short % should be > 0")
        assertTrue(positioning.retailLongShortRatio > 0.0, "Retail Long/Short ratio should be > 0")
        assertTrue(positioning.topTraderLongPositionPct > 0.0, "Top Trader Long % should be > 0")
        assertTrue(positioning.divergenceSignal.isNotBlank(), "Divergence signal should be generated")
        println("BTC Positioning: retail=${positioning.retailLongShortRatio}, top=${positioning.topTraderLongShortRatio}, div=${positioning.divergenceSignal}")
    }

    @Test
    fun testLiveCnnFearAndGreed() = runBlocking {
        val cnn = api.getCnnStockFearAndGreed()
        assertNotNull(cnn, "CNN Fear & Greed should not be null")
        assertTrue(cnn.score in 0.0..100.0, "CNN score should be between 0 and 100")
        assertTrue(cnn.rating.isNotBlank(), "Rating should not be blank")
        assertTrue(cnn.subIndicators.isNotEmpty(), "Sub-indicators should not be empty")
        println("CNN Fear & Greed: score=${cnn.score}, rating=${cnn.rating}, subsCount=${cnn.subIndicators.size}")
    }

    @Test
    fun testLiveMultiSourceSentimentForAsset() = runBlocking {
        val sentiment = api.getRedditSentiment("BTC")
        assertNotNull(sentiment)
        assertTrue(sentiment.containsKey("sentiment_score"))
        assertTrue(sentiment.containsKey("sentiment_label"))
        println("BTC Sentiment: score=${sentiment["sentiment_score"]}, label=${sentiment["sentiment_label"]}, posts=${sentiment["posts_analyzed"]}")
    }

    @Test
    fun testLiveUnifiedCompositeSentimentForBtc() = runBlocking {
        val unified = api.getUnifiedCompositeSentiment("BTC")
        assertNotNull(unified)
        assertEquals("BTC", unified.target)
        assertTrue(unified.compositeScore in 0.0..100.0, "Composite score should be in 0..100")
        assertTrue(unified.meterBar.startsWith("[") && unified.meterBar.endsWith("]"), "Meter bar should be formatted")
        assertTrue(unified.pillars.isNotEmpty(), "Pillars should not be empty")
        println("BTC Unified Sentiment: score=${unified.compositeScore} ${unified.compositeLabel} ${unified.meterBar}")
        unified.pillars.forEach { println(" - ${it.name} (${it.weightPct}%): ${it.score} | ${it.detail}") }
    }

    @Test
    fun testLiveUnifiedCompositeSentimentGlobalMacro() = runBlocking {
        val unified = api.getUnifiedCompositeSentiment(null)
        assertNotNull(unified)
        assertTrue(unified.isGlobalMacro)
        assertTrue(unified.compositeScore in 0.0..100.0)
        println("Global Macro Sentiment: score=${unified.compositeScore} ${unified.compositeLabel} ${unified.meterBar}")
        unified.pillars.forEach { println(" - ${it.name} (${it.weightPct}%): ${it.score} | ${it.detail}") }
    }
}

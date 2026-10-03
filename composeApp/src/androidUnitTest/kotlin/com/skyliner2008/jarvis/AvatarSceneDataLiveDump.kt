package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.avatar.AvatarCards
import com.skyliner2008.jarvis.avatar.AvatarToolData
import com.skyliner2008.jarvis.data.GeminiService
import com.skyliner2008.jarvis.data.LiveToolCallContext
import com.skyliner2008.jarvis.tools.ToolCall
import com.skyliner2008.jarvis.tools.ToolExecutor
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assume
import org.junit.Before
import java.io.File
import kotlin.test.Test

/**
 * Calls the app's information tools for real and saves what Avatar mode would send the robot, so the
 * robot's info scenes can be rendered and checked with real data (pet_godot/tools/tool_frames.ps1):
 *
 *   RUN_LIVE_TESTS=1 SCENE_SAMPLES_DIR=<dir> gradlew :composeApp:testDebugUnitTest --tests "*AvatarSceneDataLiveDump*"
 *
 * Writes `<tool>.json` (the scene data, or the card text [AvatarCards] makes of the tool's text, as a JSON
 * string) and `<tool>.txt` (the text the AI gets). Network only — not part of the normal test run.
 */
class AvatarSceneDataLiveDump {

    @Before
    fun requireLiveFlag() {
        Assume.assumeTrue("ตั้ง RUN_LIVE_TESTS=1 เพื่อเรียก tool จริง", System.getenv("RUN_LIVE_TESTS") == "1")
    }

    private val calls: List<Pair<String, Map<String, String>>> = listOf(
        "trading_price" to mapOf("symbol" to "XAUUSD=X"),
        "trading_technical_analysis" to mapOf("symbol" to "BTCUSDT", "interval" to "1h"),
        "trading_deep_analysis_suite" to mapOf("symbol" to "BTCUSDT", "interval" to "1h"),
        "trading_multi_timeframe" to mapOf("symbol" to "BTCUSDT"),
        "trading_smc_analysis" to mapOf("symbol" to "XAUUSD", "interval" to "1h"),
        "trading_smc_flow" to mapOf("symbol" to "BTCUSDT", "interval" to "1h"),
        "trading_smc_structure" to mapOf("symbol" to "BTCUSDT", "interval" to "1h"),
        "trading_smc_liquidity" to mapOf("symbol" to "BTCUSDT", "interval" to "1h"),
        "trading_smc_orderblocks" to mapOf("symbol" to "BTCUSDT", "interval" to "1h"),
        "trading_smc_sweeps" to mapOf("symbol" to "BTCUSDT", "interval" to "1h"),
        "trading_elliot_modern_analysis" to mapOf("symbol" to "BTCUSDT", "interval" to "1h"),
        "trading_harmonic_scan" to mapOf("symbol" to "BTCUSDT", "interval" to "1h"),
        "trading_strategy_signal" to mapOf("symbol" to "BTCUSDT", "interval" to "1h"),
        "trading_combined" to mapOf("symbol" to "BTCUSDT", "interval" to "1h"),
        "trading_fundamental_analysis" to mapOf("symbol" to "AAPL"),
        "trading_macro_calendar" to mapOf("filter" to "all"),
        "trading_news" to mapOf("symbol" to "BTC"),
        "trading_sentiment" to mapOf("symbol" to "BTC"),
        "trading_fear_greed" to mapOf("market" to "crypto"),
        "trading_crypto_overview" to emptyMap(),
        "trading_market_snapshot" to emptyMap(),
        "trading_correlation_matrix" to mapOf("symbols" to "BTCUSDT,ETHUSDT,SOLUSDT,XRPUSDT"),
        "trading_top_gainers" to mapOf("exchange" to "BINANCE"),
        "trading_top_losers" to mapOf("exchange" to "BINANCE"),
        "trading_volume_breakout" to mapOf("exchange" to "BINANCE"),
        "trading_bollinger_scan" to mapOf("exchange" to "BINANCE"),
        "trading_overbought_scan" to mapOf("exchange" to "BINANCE"),
        "trading_oversold_scan" to mapOf("exchange" to "BINANCE"),
        "trading_position_sizing" to mapOf("symbol" to "XAUUSD", "entry" to "4180", "stop_loss" to "4165"),
        "calculate" to mapOf("expression" to "(4185.10 - 4160) * 2.5"),
        "convert_units" to mapOf("value" to "30", "from_unit" to "celsius", "to_unit" to "fahrenheit"),
        "get_current_datetime" to emptyMap(),
    )

    @Test
    fun dumpToolResults() = runBlocking {
        val client = HttpClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; coerceInputValues = true }) }
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        }
        ToolExecutor.init(client, GeminiService(client, System.getenv("GEMINI_API_KEY").orEmpty(), "gemini-3.1-flash-lite"))
        val out = File(System.getenv("SCENE_SAMPLES_DIR") ?: "build/scene_samples").apply { mkdirs() }
        val pretty = Json { prettyPrint = true }
        for ((tool, args) in calls) {
            val call = LiveToolCallContext()
            val result = withTimeoutOrNull(90_000) {
                withContext(call) { ToolExecutor.execute(ToolCall(tool, args)) }
            }
            if (result == null) {
                println("TIMEOUT $tool")
                continue
            }
            val data: JsonElement = AvatarToolData.of(call, tool) ?: JsonPrimitive(AvatarCards.text(result.result))
            File(out, "$tool.json").writeText(pretty.encodeToString(JsonElement.serializer(), data))
            File(out, "$tool.txt").writeText(result.result)
            println("${if (result.isError) "ERROR" else "OK"} $tool data=${if (data is JsonPrimitive) "text" else "map"} ${result.result.length} chars")
        }
        client.close()
    }
}

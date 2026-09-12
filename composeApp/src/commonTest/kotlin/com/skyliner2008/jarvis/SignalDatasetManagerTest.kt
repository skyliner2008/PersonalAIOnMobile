package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.SignalDatasetManager
import com.skyliner2008.jarvis.automation.SignalFeatureExtractor
import com.skyliner2008.jarvis.tools.trading.Candle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SignalDatasetManagerTest {

    private fun createCandleSeries(n: Int = 30, basePrice: Double = 2000.0): List<Candle> {
        return List(n) { i ->
            val p = basePrice + (i % 5) * 2.0
            Candle(
                open = p,
                high = p + 5.0,
                low = p - 5.0,
                close = p + 3.0,
                volume = 100.0 + i * 10.0,
                timestamp = 1700000000000L + i * 60000L
            )
        }
    }

    @Test
    fun testSignalFeatureExtractor_extractsValidFeatureJson() {
        val candles = createCandleSeries(40)
        val jsonStr = SignalFeatureExtractor.extractJson(
            candles = candles,
            sigIdx = candles.size - 2,
            symbol = "XAUUSD",
            interval = "15m",
            strategy = "VEYRA",
            side = "BUY"
        )

        assertNotNull(jsonStr)
        assertTrue(jsonStr.isNotBlank())

        val root = Json.parseToJsonElement(jsonStr).jsonObject
        assertTrue(root.containsKey("body_ratio"), "Should contain body_ratio")
        assertTrue(root.containsKey("candle_dir"), "Should contain candle_dir")

        // Verify key feature groups
        assertTrue(root.containsKey("body_ratio"), "Should contain body_ratio")
        assertTrue(root.containsKey("rsi14"), "Should contain rsi14")
        assertTrue(root.containsKey("fast_rsi5"), "Should contain fast_rsi5")
        assertTrue(root.containsKey("squeeze_state"), "Should contain squeeze_state")
        assertTrue(root.containsKey("veyra_score"), "Should contain veyra_score")
        assertTrue(root.containsKey("hour_utc"), "Should contain hour_utc")
    }

    @Test
    fun testSignalDatasetManager_exportEmptyDatasetGracefully() {
        val resultJson = SignalDatasetManager.exportDataset(
            symbol = "NONEXISTENT",
            format = "json"
        )
        assertNotNull(resultJson)
        assertEquals("json", resultJson.dataFormat)
        assertEquals(0, resultJson.totalCount)
        assertEquals("[]", resultJson.payload)

        val resultCsv = SignalDatasetManager.exportDataset(
            symbol = "NONEXISTENT",
            format = "csv"
        )
        assertNotNull(resultCsv)
        assertEquals("csv", resultCsv.dataFormat)
        assertTrue(resultCsv.payload.startsWith("signal_id,symbol,interval"))
    }

    @Test
    fun testSignalDatasetManager_importConfigurationParsesMalformedJsonGracefully() {
        val badJson = "{ invalid json string "
        val result = SignalDatasetManager.importConfiguration(badJson)
        assertEquals(false, result.success)
        assertTrue(result.messageTh.contains("รูปแบบ JSON ไม่ถูกต้อง"))
    }
}

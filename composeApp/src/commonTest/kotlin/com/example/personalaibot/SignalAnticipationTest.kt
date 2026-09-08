package com.example.personalaibot

import com.example.personalaibot.automation.SignalAlertProvider
import com.example.personalaibot.automation.smc.MarketContextDigest
import com.example.personalaibot.tools.trading.Candle
import com.example.personalaibot.tools.trading.SmcApiService
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignalAnticipationTest {

    private val provider = SignalAlertProvider(SmcApiService(HttpClient()))

    private fun createCandle(
        open: Double,
        high: Double,
        low: Double,
        close: Double,
        ts: Long = 1700000000000L
    ) = Candle(open = open, high = high, low = low, close = close, volume = 100.0, timestamp = ts)

    @Test
    fun testDetectAnticipation_keyzoneDemandProducesBuyAnticipation() {
        val candles = List(20) { i ->
            createCandle(2000.0, 2005.0, 1995.0, 2000.0, 1700000000000L + i * 60000L)
        }
        val digest = MarketContextDigest.Digest(
            symbol = "XAUUSD",
            price = 2000.0,
            tfLines = listOf(
                MarketContextDigest.TfLine("H4", 1, "BOS", 2000.0, 55.0, 1990.0, 10.0, 2020.0, 1980.0)
            ),
            levelsAbove = emptyList(),
            levelsBelow = listOf(MarketContextDigest.Level(1995.0, "BULLISH_OB")),
            premiumDiscount = "DISCOUNT",
            poc = 1998.0,
            vah = 2010.0,
            val_ = 1990.0,
            keyZoneHit = "Bullish Order Block Support (1995.00 - 2000.00)",
            text = "HTF Bullish Context"
        )

        val anticipation = provider.detectAnticipation(candles, candles.size - 2, 10.0, digest)
        assertNotNull(anticipation, "Anticipation should be detected when Keyzone Demand is hit")
        assertEquals("BUY", anticipation.side)
        assertEquals("KEYZONE_PROXIMITY", anticipation.setupType)
        assertTrue(anticipation.reason.contains("Bullish Zone"))
        assertTrue(anticipation.confidence >= 70)
    }

    @Test
    fun testDetectAnticipation_keyzoneSupplyProducesSellAnticipation() {
        val candles = List(20) { i ->
            createCandle(2050.0, 2055.0, 2045.0, 2050.0, 1700000000000L + i * 60000L)
        }
        val digest = MarketContextDigest.Digest(
            symbol = "XAUUSD",
            price = 2050.0,
            tfLines = listOf(
                MarketContextDigest.TfLine("H4", -1, "BOS", 2050.0, 45.0, 2060.0, 10.0, 2070.0, 2030.0)
            ),
            levelsAbove = listOf(MarketContextDigest.Level(2055.0, "BEARISH_OB")),
            levelsBelow = emptyList(),
            premiumDiscount = "PREMIUM",
            poc = 2052.0,
            vah = 2060.0,
            val_ = 2040.0,
            keyZoneHit = "Bearish Order Block Resistance (2050.00 - 2055.00)",
            text = "HTF Bearish Context"
        )

        val anticipation = provider.detectAnticipation(candles, candles.size - 2, 10.0, digest)
        assertNotNull(anticipation, "Anticipation should be detected when Keyzone Supply is hit")
        assertEquals("SELL", anticipation.side)
        assertEquals("KEYZONE_PROXIMITY", anticipation.setupType)
        assertTrue(anticipation.reason.contains("Bearish Zone"))
    }

    @Test
    fun testDetectAnticipation_intraBarWickSweepRejectionProducesAnticipation() {
        // Build 19 normal candles between 2000 and 2010
        val baseCandles = (0 until 19).map { i ->
            createCandle(2005.0, 2010.0, 2000.0, 2005.0, 1700000000000L + i * 60000L)
        }.toMutableList()

        // 20th candle: sweeps low to 1990 (below prior low 2000) and closes at 2007 with huge bottom wick
        val sweepCandle = createCandle(2002.0, 2008.0, 1990.0, 2007.0, 1700000000000L + 19 * 60000L)
        baseCandles.add(sweepCandle)

        val anticipation = provider.detectAnticipation(baseCandles, baseCandles.size - 2, 10.0, null)
        assertNotNull(anticipation, "Wick sweep rejection should trigger anticipation")
        assertEquals("BUY", anticipation.side)
        assertEquals("WICK_SWEEP_REJECTION", anticipation.setupType)
        assertTrue(anticipation.reason.contains("Wick Rejection"))
    }

    @Test
    fun testDetectAnticipation_normalCandleReturnsNull() {
        val candles = List(20) { i ->
            val offset = if (i % 2 == 0) 0.5 else -0.5
            createCandle(2000.0 + offset, 2002.0, 1998.0, 2000.0 - offset, 1700000000000L + i * 60000L)
        }
        val anticipation = provider.detectAnticipation(candles, candles.size - 2, 10.0, null)
        assertNull(anticipation, "Calm candle without keyzone or wick sweep should return null")
    }

    @Test
    fun testAlertCatalogAndPresets_supportAnticipation() {
        val catalog = com.example.personalaibot.automation.AlertFieldCatalog
        assertTrue(catalog.isFieldSupported("trading_signal_alert", "signal_anticipation"))
        assertTrue(catalog.isFieldSupported("trading_signal_alert", "signal_stage"))
        assertTrue(catalog.isFieldSupported("trading_signal_alert", "signal_anticipation_side"))
        assertTrue(catalog.isFieldSupported("trading_signal_alert", "signal_anticipation_zone"))

        val presets = com.example.personalaibot.ui.screen.ALERT_PRESETS
        val anticipationPreset = presets.firstOrNull { it.field == "signal_anticipation" }
        kotlin.test.assertNull(anticipationPreset, "ALERT_PRESETS should NOT include manual anticipation preset (managed exclusively by AI tool)")

        val tradingTools = com.example.personalaibot.tools.ToolRegistry.tvOnlyTradingFunctionNames
        assertTrue(tradingTools.contains("trading_signal_anticipation"), "ToolRegistry must register trading_signal_anticipation for AI")
    }

    @Test
    fun testParseAlertCardMeta_anticipationCard() {
        val metaJson = """{"kind":"anticipation","type":"signal_alert_ai","name":"Alert Anticipation","symbol":"XAUUSD@15m","side":"BUY","zone":"Demand 2000","desc":"Wick Rejection at Demand","confidence":"80","price":"2001.50","mtf":"H4: UP | M15: BOS","summary":"ระวังการทดสอบแนวต้าน","voice":"Live 2.5 Native"}"""
        val parsed = com.example.personalaibot.ui.components.parseAlertCardMeta(metaJson)
        assertTrue(parsed is com.example.personalaibot.ui.components.AlertCardMeta.Anticipation)
        assertEquals("BUY", parsed.side)
        assertEquals("XAUUSD@15m", parsed.symbol)
        assertEquals("Demand 2000", parsed.zone)
        assertEquals("80", parsed.confidence)
        assertEquals("2001.50", parsed.price)
        assertEquals("Wick Rejection at Demand", parsed.desc)
        assertEquals("H4: UP | M15: BOS", parsed.mtf)
        assertEquals("ระวังการทดสอบแนวต้าน", parsed.summary)
        assertEquals("Live 2.5 Native", parsed.voice)
    }

    @Test
    fun testParseAlertCardMeta_keyzoneCard() {
        val metaJson = """{"kind":"keyzone","type":"signal_alert_ai","name":"Keyzone Alert","symbol":"XAUUSD@1h","desc":"Bearish Order Block 2050","price":"2050.20","mtf":"D1: DOWN","summary":"ราคาชนแนวต้านสำคัญ"}"""
        val parsed = com.example.personalaibot.ui.components.parseAlertCardMeta(metaJson)
        assertTrue(parsed is com.example.personalaibot.ui.components.AlertCardMeta.Keyzone)
        assertEquals("XAUUSD@1h", parsed.symbol)
        assertEquals("Bearish Order Block 2050", parsed.desc)
        assertEquals("2050.20", parsed.price)
        assertEquals("D1: DOWN", parsed.mtf)
        assertEquals("ราคาชนแนวต้านสำคัญ", parsed.summary)
    }

    @Test
    fun testParseAlertCardMeta_legacyAlertWithSignalAnticipation() {
        // เคสประวัติเก่า: kind="alert", condition="signal_anticipation >= 1", current="1"
        val metaJson = """{"kind":"alert","type":"signal_alert_ai","name":"เตือนล่วงหน้า","symbol":"XAUUSD@15m","condition":"signal_anticipation >= 1","current":"1"}"""
        val rawContent = "⚡ คาดการณ์ BUY XAUUSD@15m ที่โซน Demand — เฝ้าระวังการกลับตัวจาก Wick Rejection"
        val parsed = com.example.personalaibot.ui.components.parseAlertCardMeta(metaJson, rawContent)
        assertTrue(parsed is com.example.personalaibot.ui.components.AlertCardMeta.Anticipation, "Legacy alert with signal_anticipation condition should fall back to Anticipation card")
        assertEquals("BUY", parsed.side)
        assertEquals("Demand Zone", parsed.zone)
        assertTrue(parsed.desc.contains("Wick Rejection") || parsed.desc.contains("เฝ้าระวัง"))
    }

    @Test
    fun testParseAlertCardMeta_genericAlertPreservesRawContent() {
        val metaJson = """{"kind":"alert","type":"alert","name":"RSI Alert","symbol":"BTCUSDT","condition":"rsi < 30","current":"25"}"""
        val rawContent = "🎯 **Alert: RSI Alert**\nBTCUSDT\n\n| รายการ | ค่า |\n|---|---|\n| เงื่อนไข | rsi < 30 |\n| ค่าปัจจุบัน | 25 |\n\n**JARVIS quick-check:** RSI Oversold มีโอกาสเด้งสั้น"
        val parsed = com.example.personalaibot.ui.components.parseAlertCardMeta(metaJson, rawContent)
        assertTrue(parsed is com.example.personalaibot.ui.components.AlertCardMeta.Generic)
        assertEquals("RSI Alert", parsed.name)
        assertEquals("BTCUSDT", parsed.symbol)
        assertEquals(rawContent, parsed.rawContent)
    }

    @Test
    fun testDetectAnticipation_emaNearGoldenCrossProducesBuyAnticipation() {
        // 75 แท่ง: 60 แท่งแรกอยู่ระดับ 2000.0
        // แท่ง 60..72 ปรับลงมาแถว 1985.0 ทำให้ EMA14 อยู่ต่ำกว่า EMA60
        // แท่ง 73..74 ดีดตัวขึ้นมาที่ 1998.0 ทำให้ EMA14 วิ่งพุ่งเข้าหา EMA60 (บีบตัวเข้าหาในระยะกระชั้นชิด)
        val list = mutableListOf<Candle>()
        for (i in 0 until 60) {
            list.add(createCandle(2000.0, 2002.0, 1998.0, 2000.0, 1700000000000L + i * 60000L))
        }
        for (i in 60 until 72) {
            list.add(createCandle(1985.0, 1987.0, 1983.0, 1985.0, 1700000000000L + i * 60000L))
        }
        // แท่ง 72: ปิด 1992.0
        list.add(createCandle(1986.0, 1994.0, 1985.0, 1992.0, 1700000000000L + 72 * 60000L))
        // แท่ง 73: ปิด 1995.0
        list.add(createCandle(1992.0, 1997.0, 1991.0, 1995.0, 1700000000000L + 73 * 60000L))
        // แท่ง 74 (Live): ปิด 1997.0 อยู่ในช่วง High/Low ของแท่ง 73 เพื่อไม่ให้เกิด Wick Sweep
        list.add(createCandle(1995.0, 1997.0, 1994.0, 1997.0, 1700000000000L + 74 * 60000L))

        val anticipation = provider.detectAnticipation(list, list.size - 2, 20.0, null)
        assertNotNull(anticipation, "EMA near cross should trigger anticipation")
        assertEquals("BUY", anticipation.side)
        assertEquals("EMA_NEAR_CROSS", anticipation.setupType)
        assertTrue(anticipation.reason.contains("Golden Cross"))
        assertEquals(76, anticipation.confidence)
    }

    @Test
    fun testDetectAnticipation_emaNearDeathCrossProducesSellAnticipation() {
        // 75 แท่ง: 60 แท่งแรกอยู่ระดับ 2000.0
        // แท่ง 60..72 ปรับขึ้นไปแถว 2015.0 ทำให้ EMA14 อยู่สูงกว่า EMA60
        // แท่ง 73..74 ทิ้งตัวลงมาแถว 2003.0 ทำให้ EMA14 วิ่งปักหัวลงเข้าหา EMA60
        val list = mutableListOf<Candle>()
        for (i in 0 until 60) {
            list.add(createCandle(2000.0, 2002.0, 1998.0, 2000.0, 1700000000000L + i * 60000L))
        }
        for (i in 60 until 72) {
            list.add(createCandle(2015.0, 2017.0, 2013.0, 2015.0, 1700000000000L + i * 60000L))
        }
        // แท่ง 72: ปิด 2008.0
        list.add(createCandle(2014.0, 2015.0, 2006.0, 2008.0, 1700000000000L + 72 * 60000L))
        // แท่ง 73: ปิด 2005.0
        list.add(createCandle(2008.0, 2009.0, 2003.0, 2005.0, 1700000000000L + 73 * 60000L))
        // แท่ง 74 (Live): ปิด 2003.0
        list.add(createCandle(2005.0, 2006.0, 2003.0, 2003.0, 1700000000000L + 74 * 60000L))

        val anticipation = provider.detectAnticipation(list, list.size - 2, 20.0, null)
        assertNotNull(anticipation, "EMA near death cross should trigger anticipation")
        assertEquals("SELL", anticipation.side)
        assertEquals("EMA_NEAR_CROSS", anticipation.setupType)
        assertTrue(anticipation.reason.contains("Death Cross"))
        assertEquals(76, anticipation.confidence)
    }

    @Test
    fun testAlertCatalogAndPresets_supportEma14_60FieldsAndPresets() {
        val catalog = com.example.personalaibot.automation.AlertFieldCatalog
        assertTrue(catalog.isFieldSupported("trading_signal_alert", "ema14_60_cross"))
        assertTrue(catalog.isFieldSupported("trading_signal_alert", "ema14_60_near_cross"))
        assertTrue(catalog.isFieldSupported("trading_signal_alert", "ema14_60_near_cross_side"))
        assertTrue(catalog.isFieldSupported("trading_signal_alert", "ema14_60_state"))
        assertTrue(catalog.isFieldSupported("trading_signal_alert", "ema14_60_spread"))
        assertTrue(catalog.isFieldSupported("trading_signal_alert", "ema14"))
        assertTrue(catalog.isFieldSupported("trading_signal_alert", "ema60"))

        assertTrue(catalog.isFieldSupported("trading_smc_flow", "ema14_60_near_cross"))
        assertTrue(catalog.isFieldSupported("trading_smc_flow", "ema14_60_near_cross_side"))

        val presets = com.example.personalaibot.ui.screen.ALERT_PRESETS
        assertNotNull(presets.firstOrNull { it.field == "ema14_60_near_cross" }, "Should have ema14_60_near_cross preset")
        assertNotNull(presets.firstOrNull { it.field == "ema14_60_cross" && it.value == "GOLDEN_CROSS" }, "Should have Golden Cross preset")
        assertNotNull(presets.firstOrNull { it.field == "ema14_60_cross" && it.value == "DEATH_CROSS" }, "Should have Death Cross preset")
    }

    @Test
    fun testAnticipationTimeframeDisplayAndParsing() {
        // 1. Verify splitSymbolAndTf correctly resolves default 1h when no suffix
        val (sym1, tf1) = com.example.personalaibot.automation.IndicatorAlertProvider.splitSymbolAndTf("XAUUSD")
        assertEquals("XAUUSD", sym1)
        assertEquals("1h", tf1)

        // 2. Verify splitSymbolAndTf correctly resolves explicit timeframes
        val (sym2, tf2) = com.example.personalaibot.automation.IndicatorAlertProvider.splitSymbolAndTf("XAUUSD@1h")
        assertEquals("XAUUSD", sym2)
        assertEquals("1h", tf2)

        val (sym3, tf3) = com.example.personalaibot.automation.IndicatorAlertProvider.splitSymbolAndTf("XAUUSD@15m")
        assertEquals("XAUUSD", sym3)
        assertEquals("15m", tf3)

        // 3. Verify anticipation card parsing parses symbol with timeframe
        val metaJson = """{"kind":"anticipation","type":"signal_alert_ai","name":"Anticipation XAUUSD [1H]","symbol":"XAUUSD@1h","side":"SELL","zone":"EMA Convergence: 4438.38 -> 4436.73","desc":"EMA14 บีบตัวเข้าหา EMA60","confidence":"76","price":"4424.04"}"""
        val parsed = com.example.personalaibot.ui.components.parseAlertCardMeta(metaJson)
        assertTrue(parsed is com.example.personalaibot.ui.components.AlertCardMeta.Anticipation)
        assertEquals("XAUUSD@1h", parsed.symbol)
        assertEquals("SELL", parsed.side)
        assertEquals("4424.04", parsed.price)

        val (cardSym, cardTf) = com.example.personalaibot.automation.IndicatorAlertProvider.splitSymbolAndTf(parsed.symbol)
        assertEquals("XAUUSD", cardSym)
        assertEquals("1h", cardTf)
    }

    @Test
    fun testAnticipationDefaultTimeframeIs15mAndSupportsMultiTf() = kotlinx.coroutines.runBlocking {
        var capturedAlertArgs: Map<String, String>? = null
        val mockDelegate = object : com.example.personalaibot.tools.SideEffectDelegate {
            override suspend fun onRememberFact(key: String, value: String, importance: String) {}
            override suspend fun onSetReminder(title: String, detail: String, whenStr: String, timestamp: Long) {}
            override suspend fun onDisplayReport(markdown: String, voiceSummary: String) {}
            override suspend fun onVisionToggle(active: Boolean) {}
            override suspend fun onVoiceChange(newVoice: String) {}
            override suspend fun onRecallMemory(query: String): String = ""
            override suspend fun onSaveDiagnosticReport(filename: String, content: String) {}
            override suspend fun onSaveAgentTool(filename: String, jsonContent: String) {}
            override suspend fun onReadAgentTool(filename: String): String = ""
            override suspend fun onDeleteAgentTool(filename: String): String = "OK"
            override suspend fun onUpdateIdentity(target: String, field: String, value: String): String = "OK"
            override suspend fun onManageAlerts(args: Map<String, String>): String {
                capturedAlertArgs = args
                return "OK"
            }
            override suspend fun onManageSchedule(args: Map<String, String>): String = "OK"
            override suspend fun onChartControl(args: Map<String, String>): String = "OK"
        }
        com.example.personalaibot.tools.ToolExecutor.setSideEffectDelegate(mockDelegate)

        // 1. When no timeframe is provided, default MUST be 15m
        val callDefault = com.example.personalaibot.tools.ToolCall(
            name = "trading_signal_anticipation",
            args = mapOf("action" to "create", "symbol" to "XAUUSD")
        )
        val resDefault = com.example.personalaibot.tools.ToolExecutor.execute(callDefault)
        assertNotNull(capturedAlertArgs)
        assertEquals("15m", capturedAlertArgs!!["timeframe"], "Default timeframe for anticipation MUST be 15m")
        assertEquals("XAUUSD", capturedAlertArgs!!["symbol"])
        assertTrue(resDefault.result.contains("TF: 15M"))

        // 2. When timeframe is explicitly specified (e.g. 5m, 1h, 4h)
        val call5m = com.example.personalaibot.tools.ToolCall(
            name = "trading_signal_anticipation",
            args = mapOf("action" to "create", "symbol" to "BTCUSDT", "timeframe" to "5m")
        )
        val res5m = com.example.personalaibot.tools.ToolExecutor.execute(call5m)
        assertEquals("5m", capturedAlertArgs!!["timeframe"])
        assertEquals("BTCUSDT", capturedAlertArgs!!["symbol"])
        assertTrue(res5m.result.contains("TF: 5M"))

        // 3. When timeframe is "all"
        val callAll = com.example.personalaibot.tools.ToolCall(
            name = "trading_signal_anticipation",
            args = mapOf("action" to "create", "symbol" to "ETHUSDT", "timeframe" to "all")
        )
        val resAll = com.example.personalaibot.tools.ToolExecutor.execute(callAll)
        assertEquals("all", capturedAlertArgs!!["timeframe"])
        assertTrue(resAll.result.contains("TF: ALL"))
    }
}

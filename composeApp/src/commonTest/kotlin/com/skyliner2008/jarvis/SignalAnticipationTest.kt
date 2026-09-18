package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.AlertFieldCatalog
import com.skyliner2008.jarvis.automation.wake.AnticipationEngine
import com.skyliner2008.jarvis.automation.wake.AnticipationToolActions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ระบบปลุก AI (คาดการณ์ล่วงหน้า) — สัญญาระหว่าง tool / alert catalog / การ์ดแชท
 * (ตรรกะการตรวจจับอยู่ใน WakeTriggerRegistryTest, การเรียนรู้/งบอยู่ใน WakeSystemTest)
 */
class SignalAnticipationTest {

    @Test
    fun anticipationIsSeparateToolFromSignalAlert() {
        // field เก่าต้องไม่อยู่ใน signal alert แล้ว — ระบบคาดการณ์แยกเป็น tool ของตัวเอง
        assertFalse(AlertFieldCatalog.isFieldSupported("trading_signal_alert", "signal_anticipation"))
        assertFalse(AlertFieldCatalog.isFieldSupported("trading_signal_alert", "signal_anticipation_side"))
        assertTrue(AlertFieldCatalog.isFieldSupported(AnticipationEngine.TOOL_NAME, AnticipationEngine.K_WAKE))
        assertTrue(AlertFieldCatalog.isFieldSupported(AnticipationEngine.TOOL_NAME, AnticipationEngine.K_EVENT_COUNT))
        assertTrue(AlertFieldCatalog.isNumericField(AnticipationEngine.TOOL_NAME, AnticipationEngine.K_WAKE))
        assertTrue(com.skyliner2008.jarvis.tools.ToolRegistry.tvOnlyTradingFunctionNames.contains("trading_signal_anticipation"))
        assertTrue(com.skyliner2008.jarvis.ui.screen.ALERT_PRESETS.none { it.field == "signal_anticipation" })
    }

    @Test
    fun createActionBuildsWakeAlertJobs() = kotlinx.coroutines.runBlocking {
        val captured = mutableListOf<Map<String, String>>()
        val actions = AnticipationToolActions(createAlert = { captured += it; "OK" }, scanner = null)

        actions.execute(mapOf("action" to "create", "symbol" to "xauusd"))
        assertEquals(1, captured.size)
        assertEquals("XAUUSD@15m", captured[0]["symbol"], "ไม่ระบุ TF ต้องใช้ 15m")
        assertEquals(AnticipationEngine.TOOL_NAME, captured[0]["tool_name"])
        assertEquals(AnticipationEngine.K_WAKE, captured[0]["condition_field"])
        assertEquals(">=", captured[0]["condition_operator"])

        captured.clear()
        actions.execute(mapOf("action" to "create", "symbol" to "BTCUSDT", "timeframe" to "all"))
        assertEquals(AnticipationToolActions.ALL_TF.map { "BTCUSDT@$it" }, captured.map { it["symbol"] })

        captured.clear()
        actions.execute(mapOf("action" to "create", "symbol" to "ETHUSDT@1h", "timeframe" to "M5,1H"))
        assertEquals(listOf("ETHUSDT@5m", "ETHUSDT@1h"), captured.map { it["symbol"] })
    }

    @Test
    fun configRejectsUnknownFactorIds() = kotlinx.coroutines.runBlocking {
        val actions = AnticipationToolActions(createAlert = { "OK" }, scanner = null)
        val res = actions.execute(mapOf("action" to "config", "add_factors" to "NOT_A_REAL_FACTOR"))
        assertTrue(res.startsWith("❌"), res)
        assertTrue(res.contains("NOT_A_REAL_FACTOR"))
    }

    @Test
    fun scanWithoutScannerFailsGracefully() = kotlinx.coroutines.runBlocking {
        val actions = AnticipationToolActions(createAlert = { "OK" }, scanner = null)
        val res = actions.execute(mapOf("action" to "scan", "symbol" to "XAUUSD"))
        assertTrue(res.contains("ยังไม่พร้อม"), res)
    }

    @Test
    fun scanRendersEngineOutput() = kotlinx.coroutines.runBlocking {
        val actions = AnticipationToolActions(createAlert = { "OK" }, scanner = { sym ->
            assertEquals("XAUUSD@15m", sym)
            mapOf(
                AnticipationEngine.K_CLOSE to "4012.50",
                AnticipationEngine.K_EVENTS to "• [M15] EMA ตัดขึ้น → ชี้ BUY",
                AnticipationEngine.K_STATES to "• [H1] อยู่ในกรอบ",
                AnticipationEngine.K_MTF to "H4: UP"
            )
        })
        val res = actions.execute(mapOf("action" to "scan", "symbol" to "XAUUSD"))
        assertTrue(res.contains("4012.50") && res.contains("EMA ตัดขึ้น") && res.contains("H4: UP"), res)
    }

    @Test
    fun testParseAlertCardMeta_anticipationCard() {
        val metaJson = """{"kind":"anticipation","type":"signal_alert_ai","name":"Alert Anticipation","symbol":"XAUUSD@15m","side":"BUY","zone":"Demand 2000","desc":"Wick Rejection at Demand","confidence":"80","price":"2001.50","mtf":"H4: UP | M15: BOS","summary":"ระวังการทดสอบแนวต้าน","voice":"Live 2.5 Native"}"""
        val parsed = com.skyliner2008.jarvis.ui.components.parseAlertCardMeta(metaJson)
        assertTrue(parsed is com.skyliner2008.jarvis.ui.components.AlertCardMeta.Anticipation)
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
        val parsed = com.skyliner2008.jarvis.ui.components.parseAlertCardMeta(metaJson)
        assertTrue(parsed is com.skyliner2008.jarvis.ui.components.AlertCardMeta.Keyzone)
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
        val parsed = com.skyliner2008.jarvis.ui.components.parseAlertCardMeta(metaJson, rawContent)
        assertTrue(parsed is com.skyliner2008.jarvis.ui.components.AlertCardMeta.Anticipation, "Legacy alert with signal_anticipation condition should fall back to Anticipation card")
        assertEquals("BUY", parsed.side)
        assertEquals("Demand Zone", parsed.zone)
        assertTrue(parsed.desc.contains("Wick Rejection") || parsed.desc.contains("เฝ้าระวัง"))
    }

    @Test
    fun testParseAlertCardMeta_genericAlertPreservesRawContent() {
        val metaJson = """{"kind":"alert","type":"alert","name":"RSI Alert","symbol":"BTCUSDT","condition":"rsi < 30","current":"25"}"""
        val rawContent = "🎯 **Alert: RSI Alert**\nBTCUSDT\n\n| รายการ | ค่า |\n|---|---|\n| เงื่อนไข | rsi < 30 |\n| ค่าปัจจุบัน | 25 |\n\n**JARVIS quick-check:** RSI Oversold มีโอกาสเด้งสั้น"
        val parsed = com.skyliner2008.jarvis.ui.components.parseAlertCardMeta(metaJson, rawContent)
        assertTrue(parsed is com.skyliner2008.jarvis.ui.components.AlertCardMeta.Generic)
        assertEquals("RSI Alert", parsed.name)
        assertEquals("BTCUSDT", parsed.symbol)
        assertEquals(rawContent, parsed.rawContent)
    }

    @Test
    fun testAnticipationTimeframeDisplayAndParsing() {
        // 1. Verify splitSymbolAndTf correctly resolves default 1h when no suffix
        val (sym1, tf1) = com.skyliner2008.jarvis.automation.IndicatorAlertProvider.splitSymbolAndTf("XAUUSD")
        assertEquals("XAUUSD", sym1)
        assertEquals("1h", tf1)

        // 2. Verify splitSymbolAndTf correctly resolves explicit timeframes
        val (sym2, tf2) = com.skyliner2008.jarvis.automation.IndicatorAlertProvider.splitSymbolAndTf("XAUUSD@1h")
        assertEquals("XAUUSD", sym2)
        assertEquals("1h", tf2)

        val (sym3, tf3) = com.skyliner2008.jarvis.automation.IndicatorAlertProvider.splitSymbolAndTf("XAUUSD@15m")
        assertEquals("XAUUSD", sym3)
        assertEquals("15m", tf3)

        // 3. Verify anticipation card parsing parses symbol with timeframe
        val metaJson = """{"kind":"anticipation","type":"signal_alert_ai","name":"Anticipation XAUUSD [1H]","symbol":"XAUUSD@1h","side":"SELL","zone":"EMA Convergence: 4438.38 -> 4436.73","desc":"EMA14 บีบตัวเข้าหา EMA60","confidence":"76","price":"4424.04"}"""
        val parsed = com.skyliner2008.jarvis.ui.components.parseAlertCardMeta(metaJson)
        assertTrue(parsed is com.skyliner2008.jarvis.ui.components.AlertCardMeta.Anticipation)
        assertEquals("XAUUSD@1h", parsed.symbol)
        assertEquals("SELL", parsed.side)
        assertEquals("4424.04", parsed.price)

        val (cardSym, cardTf) = com.skyliner2008.jarvis.automation.IndicatorAlertProvider.splitSymbolAndTf(parsed.symbol)
        assertEquals("XAUUSD", cardSym)
        assertEquals("1h", cardTf)
    }
}

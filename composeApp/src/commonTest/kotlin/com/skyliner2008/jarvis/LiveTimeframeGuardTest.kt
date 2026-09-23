package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.ai.LiveIntentMatchers
import com.skyliner2008.jarvis.data.LiveProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** logcat 2026-09-24 00:26: "วิเคราะห์ BTC 5 มิติ" → โมเดลเลือก interval=5m เอง → ถูกบล็อกทั้งคำขอ */
class LiveTimeframeGuardTest {

    @Test
    fun `timeframe the user did not ask for is not allowed as is`() {
        val args = mapOf("symbol" to "BTCUSDT", "interval" to "5m")
        assertFalse(LiveIntentMatchers.allowedTradingTimeframe(args, "วิเคราะห์ BTC 5 มิติ"))
    }

    @Test
    fun `minute timeframe the user asked for is allowed`() {
        val args = mapOf("symbol" to "BTCUSDT", "interval" to "5m")
        assertTrue(LiveIntentMatchers.allowedTradingTimeframe(args, "วิเคราะห์ BTC M5"))
        assertTrue(LiveIntentMatchers.allowedTradingTimeframe(args, "วิเคราะห์ BTC 5m"))
        assertTrue(LiveIntentMatchers.allowedTradingTimeframe(args, "วิเคราะห์ BTC 5 นาที"))
        // "5 มิติ" ไม่ใช่ timeframe
        assertFalse(LiveIntentMatchers.allowedTradingTimeframe(args, "วิเคราะห์ BTC 5 มิติ"))
    }

    @Test
    fun `unrequested timeframe is rewritten to 15m on the same key`() {
        assertEquals(
            mapOf("symbol" to "BTCUSDT", "interval" to "15m"),
            LiveIntentMatchers.withDefaultTimeframe(mapOf("symbol" to "BTCUSDT", "interval" to "5m"))
        )
        assertEquals(
            mapOf("symbol" to "XAUUSD", "timeframe" to "15m"),
            LiveIntentMatchers.withDefaultTimeframe(mapOf("symbol" to "XAUUSD", "timeframe" to "1d"))
        )
        val fromSymbol = LiveIntentMatchers.withDefaultTimeframe(mapOf("symbol" to "XAUUSD@m5"))
        assertEquals(mapOf("symbol" to "XAUUSD", "interval" to "15m"), fromSymbol)
        assertTrue(LiveIntentMatchers.allowedTradingTimeframe(fromSymbol, "วิเคราะห์ทอง"))
    }

    @Test
    fun `session history header marks past turns as already answered`() {
        assertTrue(LiveProtocol.HISTORY_HEADER.contains("ตอบไปแล้ว"))
        assertTrue(LiveProtocol.HISTORY_HEADER.endsWith("\n"))
        assertEquals(1, LiveProtocol.HISTORY_HEADER.count { it == '\n' })
    }
}

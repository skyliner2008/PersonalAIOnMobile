package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.wake.WakePrompt
import com.skyliner2008.jarvis.tools.trading.MarketHours
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** เวลาเปิด/ปิดตลาด (อิงเวลานิวยอร์ก รองรับ DST) + Risk:Reward ของคำตอบ AI */
class MarketHoursTest {

    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDateTime(y, mo, d, h, mi).toInstant(TimeZone.UTC).toEpochMilliseconds()

    @Test
    fun kinds() {
        assertEquals(MarketHours.Kind.METAL, MarketHours.kindOf("XAUUSD@15m"))
        assertEquals(MarketHours.Kind.METAL, MarketHours.kindOf("OANDA:XAGUSD"))
        assertEquals(MarketHours.Kind.FX, MarketHours.kindOf("EURUSD"))
        assertEquals(MarketHours.Kind.CRYPTO, MarketHours.kindOf("BTCUSDT@15m"))
        assertEquals(MarketHours.Kind.OTHER, MarketHours.kindOf("TVC:DXY"))
    }

    @Test
    fun weekendClosedForGoldAndFxButNotCrypto() {
        val sat = utc(2026, 9, 19, 12)                       // เสาร์
        val gold = MarketHours.status("XAUUSD", sat)
        assertFalse(gold.open)
        assertNotNull(gold.reasonTh)
        assertEquals(utc(2026, 9, 20, 22), gold.nextOpenMs)  // อาทิตย์ 18:00 NY (EDT) = 22:00 UTC
        assertEquals(utc(2026, 9, 20, 21), MarketHours.status("EURUSD", sat).nextOpenMs)  // FX เปิด 17:00 NY
        assertTrue(MarketHours.status("BTCUSDT", sat).open)
    }

    @Test
    fun fridayCloseAndPreCloseWindow() {
        val before = MarketHours.status("XAUUSD", utc(2026, 9, 18, 20, 30))  // ศุกร์ 16:30 NY
        assertTrue(before.open)
        assertEquals(30L, before.minutesToWeeklyClose)
        assertFalse(MarketHours.status("XAUUSD", utc(2026, 9, 18, 21, 0)).open)  // ศุกร์ 17:00 NY
    }

    @Test
    fun sundayOpenDiffersForFxAndGold() {
        val sun = utc(2026, 9, 20, 21, 30)                   // อาทิตย์ 17:30 NY
        assertTrue(MarketHours.status("EURUSD", sun).open)
        assertFalse(MarketHours.status("XAUUSD", sun).open)
        assertTrue(MarketHours.status("XAUUSD", utc(2026, 9, 20, 22, 5)).open)
    }

    @Test
    fun goldDailyBreak() {
        val tue = utc(2026, 9, 22, 21, 30)                   // อังคาร 17:30 NY
        val gold = MarketHours.status("XAUUSD", tue)
        assertFalse(gold.open)
        assertEquals(utc(2026, 9, 22, 22), gold.nextOpenMs)
        assertTrue(MarketHours.status("EURUSD", tue).open)
        assertTrue(MarketHours.status("XAUUSD", utc(2026, 9, 22, 22, 1)).open)
        assertNull(MarketHours.status("XAUUSD", utc(2026, 9, 22, 12)).minutesToWeeklyClose)
    }

    /** ฤดูหนาว (EST = UTC−5) — เวลาปิดใน UTC เลื่อนไป 1 ชั่วโมง */
    @Test
    fun winterTimeShiftsCloseByOneHour() {
        val fri = MarketHours.status("XAUUSD", utc(2026, 1, 9, 21, 30))     // ศุกร์ 16:30 NY (EST)
        assertTrue(fri.open)
        assertEquals(30L, fri.minutesToWeeklyClose)
        assertFalse(MarketHours.status("XAUUSD", utc(2026, 1, 9, 22, 0)).open)
    }

    // ─── Risk:Reward ─────────────────────────────────────────────────────────

    @Test
    fun rrFromAiLevels() {
        // เคสจริง BTC 2026-09-19 14:01: SELL @81072, SL 81370.57, TP 80844.58 → RR ≈ 0.76
        val v = WakePrompt.Verdict("NOTIFY", "SELL", 81370.57, 80844.58, 65, "r", "s")
        assertEquals(0.7617, v.rr(81072.0)!!, 1e-3)
        assertTrue(v.rr(81072.0)!! < WakePrompt.MIN_RR)
        assertNull(v.copy(bias = "NEUTRAL").rr(81072.0))
        assertNull(v.copy(levelTp = null).rr(81072.0))
    }

    @Test
    fun promptRequiresRrAtLeastOneToOne() {
        val prompt = WakePrompt.build("XAUUSD", mapOf("timeframe" to "15m", "close" to "4000"))
        assertTrue(prompt.contains("1:1"), "prompt ต้องบอกเกณฑ์ RR ขั้นต่ำ")
    }
}

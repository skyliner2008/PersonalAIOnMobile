package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.wake.AiViewTracker
import com.skyliner2008.jarvis.automation.wake.WakeVerdictSummary
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ผลการคาดการณ์ล่วงหน้าที่ผู้ใช้ถามผ่าน Live ได้ทุกโหมด (trading_signal_anticipation action=summary) —
 * "มีแจ้งเตือนกี่ครั้งแล้ว, skip กี่ครั้ง, skip เพราะอะไร, คาดการณ์ buy/sell กี่ครั้ง, เพราะเหตุผลอะไร" (2026-10-04)
 */
class WakeVerdictSummaryTest {
    private val tz = TimeZone.of("Asia/Bangkok")
    private val now = LocalDateTime(2026, 10, 4, 9, 0).toInstant(tz).toEpochMilliseconds()
    private val minute = 60_000L

    private fun view(
        minutesAgo: Long,
        decision: String,
        bias: String,
        reason: String?,
        status: String = "OPEN",
        sl: Double? = null,
        tp: Double? = null,
        resultR: Double? = null,
        mfe: Double? = null,
        move: Double? = null,
        symbol: String = "BTCUSDT",
        confidence: Int? = 0
    ) = AiViewTracker.View(
        signalId = "s$minutesAgo$decision$symbol", symbol = symbol, interval = "15m", decision = decision, bias = bias,
        confidence = confidence, reason = reason, price = 84_800.0, atr = 120.0, levelSl = sl, levelTp = tp,
        createdAt = now - minutesAgo * minute, status = status, resultR = resultR, moveAtr = move, mfeAtr = mfe
    )

    @Test
    fun `alerts and skips are counted, buy and sell split, with the real outcome and the reasons`() {
        val views = listOf(
            view(500, "NOTIFY", "BUY", "เบรกโครงสร้างขึ้นที่แนวรับ", "SL", sl = 84_700.0, tp = 85_000.0, resultR = -1.0, confidence = 70),
            view(400, "NOTIFY", "SELL", "ราคาไม่ผ่านแนวต้าน", "TP", sl = 84_900.0, tp = 84_600.0, resultR = 2.0, confidence = 65),
            view(300, "NOTIFY", "BUY", "ย่อตัวในขาขึ้น", sl = 84_700.0, tp = 85_000.0, confidence = 60),
            view(30, "SKIP", "NEUTRAL", "โครงสร้างระยะสั้นและระยะยาวขัดแย้งกันอย่างมีนัยสำคัญ", "CLOSED", mfe = 2.6, move = 1.2),
            view(20, "SKIP", "NEUTRAL", "โครงสร้างระยะสั้นและระยะยาวขัดแย้งกันอย่างมีนัยสำคัญ", "CLOSED", mfe = 3.9, move = 3.4),
            view(10, "SKIP", "NEUTRAL", "ตลาดเงียบผิดปกติและไร้ทิศทาง"),
            view(5, "SKIP", "SELL", "ระบบไม่แจ้ง: AI เสนอ SELL สวนเทรนด์ H4/H1/M15 ที่เรียงเป็นขาขึ้น — แรงขายเริ่มเข้า", confidence = 55)
        )
        val out = WakeVerdictSummary.build(views, "BTCUSDT", WakeVerdictSummary.period(null, now, tz), now, tz)
        assertTrue(out.contains("ปลุก AI 7 ครั้ง → แจ้งเตือน (NOTIFY) 3 ครั้ง · ไม่แจ้ง (SKIP) 4 ครั้ง"), out)
        // the asset by name — an answer that named none was spoken as gold (phone 2026-10-04 13:36)
        assertTrue(out.lines().first().contains("BTCUSDT (บิทคอยน์)"), out)
        assertTrue(out.contains("แจ้งเตือน 3 ครั้ง ทั้งหมดเป็น BTCUSDT (บิทคอยน์): คาดการณ์ BUY 2 · SELL 1"), out)
        assertFalse(out.contains("ทองคำ"), out)
        assertTrue(out.contains("ชน TP 1 · ชน SL 1 · หมดเวลา 0 · ยังติดตาม 1 · รวม +1.00R"), out)
        assertTrue(out.contains("SELL 65% @84,800") || out.contains("SELL 65% @84800"), out)
        assertTrue(out.contains("เหตุผล: ราคาไม่ผ่านแนวต้าน"), out)
        assertTrue(out.contains("เป็นกลาง 3 · เอียง BUY 0 · เอียง SELL 1"), out)
        assertTrue(out.contains("โครงสร้างแต่ละไทม์เฟรมขัดแย้งกัน 2 ครั้ง"), out)
        assertTrue(out.contains("ตลาดเงียบ ไร้ทิศทาง หรือยังไม่ยืนยัน 1 ครั้ง"), out)
        assertTrue(out.contains("ระบบตัดเพราะ AI เสนอสวนเทรนด์ใหญ่"), out)
        assertTrue(out.contains("\"โครงสร้างระยะสั้นและระยะยาวขัดแย้งกันอย่างมีนัยสำคัญ\" ×2"), out)
        // a swing past 2 ATR on the way (mfe 2.6) is not a missed move; a close 3.4 ATR away is
        assertTrue(out.contains("1 จาก 2 ครั้งที่วัดผลแล้ว (อาจพลาดโอกาส)"), out)
    }

    @Test
    fun `the newest alert is listed first, and a quiet period says there was none`() {
        val alerts = listOf(
            view(90, "NOTIFY", "BUY", "เก่า", sl = 84_700.0, tp = 85_000.0),
            view(10, "NOTIFY", "SELL", "ใหม่", sl = 84_900.0, tp = 84_600.0)
        )
        val out = WakeVerdictSummary.build(alerts, null, WakeVerdictSummary.period(null, now, tz), now, tz)
        assertTrue(out.indexOf("เหตุผล: ใหม่") < out.indexOf("เหตุผล: เก่า"), out)

        val quiet = listOf(
            view(3, "SKIP", "NEUTRAL", "ตลาดเงียบ"),
            view(2, "SKIP", "NEUTRAL", "ตลาดเงียบ", symbol = "XAUUSD"),
            view(1, "SKIP", "NEUTRAL", "ตลาดเงียบ")
        )
        val q = WakeVerdictSummary.build(quiet, null, WakeVerdictSummary.period(null, now, tz), now, tz)
        assertTrue(q.contains("ทุกสินทรัพย์ (BTCUSDT (บิทคอยน์), XAUUSD (ทองคำ))"), q)
        assertTrue(q.contains("แยกตามสินทรัพย์: BTCUSDT 2 · XAUUSD 1"), q)
        assertTrue(q.contains("ไม่มีการแจ้งเตือนในช่วงนี้"), q)
        assertTrue(WakeVerdictSummary.build(emptyList(), "XAUUSD", WakeVerdictSummary.period("today", now, tz), now, tz).startsWith("📭"))
    }

    @Test
    fun `period words map to the right start`() {
        assertEquals(LocalDateTime(2026, 10, 4, 0, 0).toInstant(tz).toEpochMilliseconds(), WakeVerdictSummary.period("today", now, tz).startMs)
        assertEquals(now - 86_400_000L, WakeVerdictSummary.period(null, now, tz).startMs)
        assertEquals(now - 86_400_000L, WakeVerdictSummary.period("24h", now, tz).startMs)
        assertEquals(now - 48 * 3_600_000L, WakeVerdictSummary.period("48h", now, tz).startMs)
        assertEquals(now - 7 * 86_400_000L, WakeVerdictSummary.period("7d", now, tz).startMs)
        assertEquals(now - 30 * 86_400_000L, WakeVerdictSummary.period("เดือน", now, tz).startMs)
        assertEquals(0L, WakeVerdictSummary.period("all", now, tz).startMs)
    }

    @Test
    fun `the names the user says find the stored symbols`() {
        assertTrue(WakeVerdictSummary.matchesSymbol("XAUUSD", "ทอง"))
        assertTrue(WakeVerdictSummary.matchesSymbol("XAUUSD", "gold"))
        assertTrue(WakeVerdictSummary.matchesSymbol("BTCUSDT", "BTC"))
        assertTrue(WakeVerdictSummary.matchesSymbol("BTCUSDT", "btcusdt@15m"))
        assertFalse(WakeVerdictSummary.matchesSymbol("BTCUSDT", "XAUUSD"))
        assertEquals("XAUUSD (ทองคำ)", WakeVerdictSummary.label("xauusd"))
        assertEquals("BTCUSDT (บิทคอยน์)", WakeVerdictSummary.label("BTCUSDT"))
        assertEquals("EURUSD", WakeVerdictSummary.label("EURUSD"))
    }

    @Test
    fun `times show the date only for other days`() {
        assertEquals("08:30", WakeVerdictSummary.time(now - 30 * minute, now, tz))
        assertEquals("03/10 23:00", WakeVerdictSummary.time(now - 10 * 60 * minute, now, tz))
    }
}

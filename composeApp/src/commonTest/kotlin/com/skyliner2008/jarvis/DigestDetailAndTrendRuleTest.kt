package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.smc.MarketContextDigest
import com.skyliner2008.jarvis.automation.wake.WakePrompt
import com.skyliner2008.jarvis.tools.trading.Candle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** รายละเอียดต่อ TF ในภาพตลาด (D1, แท่งเทียน, PDH/PWH) + กติกาห้ามแจ้งสวนเทรนด์ */
class DigestDetailAndTrendRuleTest {
    private val h = 3_600_000L
    private val day = 24 * h

    // ── กติกาสวนเทรนด์ ──────────────────────────────────────────────────────
    private fun v(bias: String, decision: String = "NOTIFY") =
        WakePrompt.Verdict(decision, bias, 1.0, 2.0, 65, "premium", "s")

    @Test
    fun counterTrendNotifyBecomesSkipButKeepsBias() {
        val out = WakePrompt.enforceTrend(v("SELL"), "UP")
        assertEquals("SKIP", out.decision)
        assertEquals("SELL", out.bias, "คงทิศไว้ให้ติดตามผลว่าถ้าแจ้งไปจะถูกไหม")
        assertTrue(out.reasonTh.contains("สวนเทรนด์") && out.reasonTh.contains("premium"))
        assertEquals("SKIP", WakePrompt.enforceTrend(v("BUY"), "DOWN").decision)
    }

    @Test
    fun withTrendOrUnalignedIsUntouched() {
        val buy = v("BUY")
        assertTrue(WakePrompt.enforceTrend(buy, "UP") === buy)
        val sell = v("SELL")
        assertTrue(WakePrompt.enforceTrend(sell, "") === sell)
        assertTrue(WakePrompt.enforceTrend(sell, null) === sell)
        val skip = v("SELL", "SKIP")
        assertTrue(WakePrompt.enforceTrend(skip, "UP") === skip)
    }

    @Test
    fun promptWarnsWhenAligned() {
        val p = WakePrompt.build("BTCUSDT", mapOf("timeframe" to "15m", "close" to "1", "wake_htf_align" to "UP"))
        assertTrue(p.contains("เรียงเป็นขาขึ้นทั้งหมด — ห้ามตอบ NOTIFY + SELL"))
        val none = WakePrompt.build("BTCUSDT", mapOf("timeframe" to "15m", "close" to "1"))
        assertFalse(none.contains("ห้ามตอบ NOTIFY + SELL"))
        assertTrue(none.contains("ห้ามแจ้งสวนเทรนด์"), "กติกาทั่วไปต้องอยู่เสมอ")
    }

    // ── แท่งเทียน ──────────────────────────────────────────────────────────
    @Test
    fun candleDescription() {
        // shooting star แดง: high 110 low 100 open 102 close 101 → ไส้บน 80%
        val star = MarketContextDigest.describeCandle(Candle(102.0, 110.0, 100.0, 101.0, 1.0), null, 5.0)
        assertTrue(star.startsWith("แดง 2.0×ATR") && star.contains("ไส้บน 80%"), star)
        // กลืนกิน: แท่งก่อนแดง 105→102 แท่งนี้เขียว 101→106
        val eng = MarketContextDigest.describeCandle(Candle(101.0, 106.5, 100.5, 106.0, 1.0), Candle(105.0, 105.5, 101.5, 102.0, 1.0), 5.0)
        assertTrue(eng.contains("ตัวตัน") && eng.contains("กลืนกินแท่งก่อน"), eng)
    }

    // ── D1 จาก H4 + PDH/PWH ─────────────────────────────────────────────────
    private fun h4Bars(startMs: Long, count: Int) = (0 until count).map { k ->
        val ts = startMs + k * 4 * h
        Candle(100.0 + k, 101.0 + k, 99.0 + k, 100.5 + k, 10.0, ts, true)
    }

    @Test
    fun dailyResampleFollowsMarketDayBoundary() {
        // เริ่มวันจันทร์ 2026-09-14 00:00 UTC (epoch day 20710 = จันทร์)
        val mon = 20_710L * day
        // คริปโต: วัน UTC → 6 แท่ง H4 ต่อวัน
        val crypto = MarketContextDigest.resampleDaily(h4Bars(mon, 12), 0)
        assertEquals(2, crypto.size)
        assertEquals(mon, crypto[0].timestamp)
        assertEquals(100.0, crypto[0].open); assertEquals(106.0, crypto[0].high); assertEquals(105.5, crypto[0].close)
        assertTrue(crypto.all { it.isClosed })
        // ทอง/FX (offset −2): วันเริ่ม 22:00 UTC → แท่ง 00:00 UTC อยู่ในวันที่เริ่ม 22:00 ของวันก่อน
        // ทอง OANDA ฤดูร้อน: แท่ง H4 เปิด 21:00, 01:00, …, 17:00 UTC (ตาม 17:00 นิวยอร์ก)
        // วันเทรดจันทร์ = อาทิตย์ 21:00 → จันทร์ 21:00 UTC (6 แท่ง) แล้วแท่งจันทร์ 21:00 เริ่มวันอังคาร
        val gold = MarketContextDigest.resampleDaily(h4Bars(mon - 3 * h, 7), -2)
        assertEquals(2, gold.size)
        assertEquals(mon - 2 * h, gold[0].timestamp)
        assertEquals(6.0 * 10, gold[0].volume, "แท่งอาทิตย์ 21:00 ถึงจันทร์ 17:00 UTC = วันเทรดเดียวกัน")
        assertTrue(gold[0].isClosed)
        assertEquals(1.0 * 10, gold[1].volume)
        assertFalse(gold[1].isClosed, "วันอังคารเพิ่งเริ่ม")
        // วันสุดท้ายปิดเมื่อแท่ง 17:00 UTC (แท่งสุดท้ายของวัน) ปิดแล้ว แม้ขอบวันตามชื่อคือ 22:00 UTC
        assertTrue(MarketContextDigest.resampleDaily(h4Bars(mon - 3 * h, 6), -2).single().isClosed)
    }

    @Test
    fun previousDayAndWeekHighLow() {
        val mon = 20_710L * day
        // 2 สัปดาห์เต็ม + 1 วันของสัปดาห์ที่ 3 (ยังไม่จบ)
        val d1 = MarketContextDigest.resampleDaily(h4Bars(mon - 7 * day, 6 * 15), 0)
        val (pdh, pdl) = assertNotNull(MarketContextDigest.previousHighLow(d1, weekly = false))
        assertEquals(d1.last { it.isClosed }.high, pdh); assertEquals(d1.last { it.isClosed }.low, pdl)
        val (pwh, pwl) = assertNotNull(MarketContextDigest.previousHighLow(d1, weekly = true))
        // สัปดาห์ก่อน = 14–20 ก.ย. (index วัน 7..13)
        assertEquals(d1.subList(7, 14).maxOf { it.high }, pwh)
        assertEquals(d1.subList(7, 14).minOf { it.low }, pwl)
    }

    @Test
    fun detailLinesReadable() {
        val t = MarketContextDigest.TfLine("H1", 1, "–", close = 100.0, rsi = 70.0, ema50 = 95.0, atr = 2.0,
            swingHigh = 104.0, swingLow = 96.0)
        val d = MarketContextDigest.TfDetail("HH+HL", 92.0, 98.0, 90.0, 62.0, null, 3.4, 4, 85.0, "เขียว 1.2×ATR ไส้บน 55%")
        val lines = MarketContextDigest.detailLines(t, d)
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("swing HH+HL") && lines[0].contains("(+2.0×ATR)") && lines[0].contains("(−2.0×ATR)") &&
            lines[0].contains("92% ของกรอบ 50 แท่ง") && lines[0].contains("เรียงขาขึ้น") && lines[0].contains("EMA200 +5.0×ATR"), lines[0])
        assertTrue(lines[1].contains("RSI 62→70") && lines[1].contains("+3.4×ATR") && lines[1].contains("เขียวติดกัน 4 แท่ง") &&
            lines[1].contains("ATR สูงกว่า 85%") && lines[1].contains("ไส้บน 55%"), lines[1])
    }
}

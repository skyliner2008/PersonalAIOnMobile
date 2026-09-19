package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.wake.AiViewTracker
import com.skyliner2008.jarvis.automation.wake.WakePrompt
import com.skyliner2008.jarvis.tools.trading.Candle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ติดตามผลมุมมองของ AI: ชน TP/SL, หมดเวลา, SKIP และข้อความที่ส่งกลับเข้า prompt */
class AiViewTrackerTest {
    private val tf = 900_000L
    private val t0 = 1_800_000_000_000L - (1_800_000_000_000L % 900_000L)   // ตรงรอบ 15 นาที

    private fun bar(i: Int, h: Double, l: Double, c: Double = (h + l) / 2) =
        Candle(c, h, l, c, 100.0, t0 + i * tf, true)

    private fun sell(created: Long = t0 + tf) = AiViewTracker.View(
        "BTC|15m|x", "BTCUSDT", "15m", "NOTIFY", "SELL", 65, "r",
        price = 100.0, atr = 2.0, levelSl = 103.0, levelTp = 96.0, createdAt = created
    )

    @Test
    fun sellHitsTakeProfit() {
        // แท่ง 1..3 เปิดหลังมุมมอง: ลงไปแตะ 96 ที่แท่งที่ 3
        val bars = listOf(bar(0, 101.0, 99.0), bar(1, 101.0, 98.0), bar(2, 100.0, 97.0), bar(3, 99.0, 95.5))
        val o = assertNotNull(AiViewTracker.evaluate(sell(), bars, tf))
        assertEquals("TP", o.status)
        assertEquals(4.0 / 3.0, o.resultR!!, 1e-9)   // RR = 4/3
        assertEquals(3, o.bars)
    }

    @Test
    fun sellHitsStopLossAndSameBarCountsAsStop() {
        val bars = listOf(bar(0, 101.0, 99.0), bar(1, 103.5, 99.0))
        assertEquals("SL", AiViewTracker.evaluate(sell(), bars, tf)!!.status)
        // แท่งเดียวชนทั้ง SL และ TP → นับ SL (อนุรักษ์นิยม)
        val both = listOf(bar(0, 101.0, 99.0), bar(1, 104.0, 95.0))
        val o = AiViewTracker.evaluate(sell(), both, tf)!!
        assertEquals("SL", o.status); assertEquals(-1.0, o.resultR)
    }

    @Test
    fun stillOpenReturnsNullThenExpires() {
        val flat = (0..10).map { bar(it, 101.0, 99.0) }
        assertNull(AiViewTracker.evaluate(sell(), flat, tf), "ยังไม่ครบเวลาและไม่ชนระดับ → ติดตามต่อ")
        val long = (0..AiViewTracker.MAX_TRACK_BARS + 1).map { bar(it, 101.0, 99.0, c = 99.0) }
        val o = assertNotNull(AiViewTracker.evaluate(sell(), long, tf))
        assertEquals("EXPIRED", o.status)
        assertEquals(1.0 / 3.0, o.resultR!!, 1e-9)    // ลงมา 1 จากราคา 100 เสี่ยง 3
    }

    /** มุมมองกลางแท่ง: high/low ก่อนเวลามุมมองต้องไม่ทำให้ชน SL ปลอม */
    @Test
    fun partialBarUsesM1AfterTheView() {
        val created = t0 + 5 * 60_000L                   // 5 นาทีหลังแท่ง 0 เปิด
        val primary = listOf(bar(0, 104.0, 99.0), bar(1, 101.0, 99.5))   // แท่ง 0 เคยขึ้นไป 104 (ก่อนมุมมอง)
        val m1 = (0 until 15).map { i ->
            val ts = t0 + i * 60_000L
            // ก่อนมุมมองราคาขึ้นไป 104 หลังมุมมองอยู่แค่ 100–101
            if (ts < created) Candle(103.0, 104.0, 102.0, 103.0, 1.0, ts, true) else Candle(100.5, 101.0, 100.0, 100.5, 1.0, ts, true)
        }
        val view = sell(created)
        assertNull(AiViewTracker.evaluate(view, primary, tf, m1), "M1 หลังมุมมองไม่ถึง SL → ยังเปิดอยู่")
        // ถ้าไม่มี M1 ครอบคลุม ใช้แท่งนั้นทั้งแท่ง (อนุรักษ์นิยม) → ชน SL
        assertEquals("SL", AiViewTracker.evaluate(view, primary, tf)!!.status)
    }

    @Test
    fun skipMeasuresMissedMove() {
        val skip = sell().copy(decision = "SKIP", bias = "NEUTRAL", levelSl = null, levelTp = null)
        val bars = (0..12).map { bar(it, 101.0 + it, 100.0 + it, c = 100.5 + it) }
        val o = assertNotNull(AiViewTracker.evaluate(skip, bars, tf))
        assertEquals("CLOSED", o.status)
        assertNull(o.resultR)
        assertTrue(o.moveAtr > 5, "หลัง SKIP ราคาวิ่งขึ้นแรง ต้องวัดได้ว่าพลาดโอกาส: ${o.moveAtr}")
    }

    @Test
    fun describeShowsLiveProgressAndOutcome() {
        val now = t0 + 40 * 60_000L
        val open = AiViewTracker.describe(sell(), 98.5, now)
        assertTrue(open.contains("NOTIFY SELL 65%") && open.contains("+0.50R") && open.contains("ไปตามทิศ"), open)
        val hit = AiViewTracker.describe(sell().copy(status = "TP", resultR = 1.33, bars = 3), null, now)
        assertTrue(hit.contains("ชน TP"), hit)
        val skip = AiViewTracker.describe(sell().copy(decision = "SKIP", bias = "NEUTRAL", reason = "หลักฐานขัดกัน"), 102.0, now)
        assertTrue(skip.contains("SKIP") && skip.contains("หลักฐานขัดกัน"), skip)
    }

    @Test
    fun trackRecordSummarisesClosedViews() {
        val v = sell()
        val rec = AiViewTracker.trackRecord(listOf(
            v.copy(status = "TP", resultR = 1.33), v.copy(status = "SL", resultR = -1.0),
            v.copy(status = "OPEN"), v.copy(decision = "SKIP", bias = "NEUTRAL", status = "CLOSED")
        ))
        assertNotNull(rec)
        assertTrue(rec.contains("2 ครั้ง") && rec.contains("ชน TP 1") && rec.contains("ชน SL 1"), rec)
        assertNull(AiViewTracker.trackRecord(listOf(v)), "ยังไม่มีมุมมองที่ติดตามจบ")
    }

    @Test
    fun promptIncludesPreviousViewsAndRules() {
        val prompt = WakePrompt.build("BTCUSDT", mapOf("timeframe" to "15m", "close" to "100", "wake_prev_views" to "• 10 นาทีที่แล้ว NOTIFY SELL"))
        assertTrue(prompt.contains("มุมมองที่คุณเคยให้") && prompt.contains("NOTIFY SELL"))
        assertTrue(prompt.contains("ทิศตรงข้าม"), "ต้องมีกติกาเรื่องการกลับทิศ")
    }
}

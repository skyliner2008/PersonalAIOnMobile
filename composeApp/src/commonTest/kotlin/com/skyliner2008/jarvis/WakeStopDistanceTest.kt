package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.wake.AiViewTracker
import com.skyliner2008.jarvis.automation.wake.WakePrompt
import com.skyliner2008.jarvis.automation.wake.WakeVerdictSummary
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * SL ห่างราคาอย่างน้อย 1 ATR — phone 2026-10-04 11:04: BUY BTCUSDT @84,844 ชน SL ใน 6 นาทีหลังราคาลงแค่ 36
 * (ATR M15 ~50) แล้ววิ่งขึ้น +214 · ผู้ใช้: "ปรับปรุงได้"
 */
class WakeStopDistanceTest {
    private val price = 84_844.0
    private val atr = 50.0

    private fun v(bias: String, sl: Double?, tp: Double?, decision: String = "NOTIFY") =
        WakePrompt.Verdict(decision, bias, sl, tp, 70, "ย่อตัวในขาขึ้นที่แนวรับ", "จับตาการยืนเหนือแนวรับ")

    @Test
    fun `a stop closer than 1 ATR is widened, and the alert goes out when RR still holds`() {
        val out = WakePrompt.enforceStopDistance(v("BUY", 84_815.0, 84_950.0), price, atr)
        assertEquals("NOTIFY", out.decision)
        assertEquals(84_794.0, out.levelSl)
        assertEquals(84_950.0, out.levelTp)
        assertTrue((out.rr(price) ?: 0.0) >= WakePrompt.MIN_RR)
        assertTrue(out.reasonTh.contains("ห่างราคาแค่ 0.6 ATR"), out.reasonTh)
        // the voice reads the summary: it says the stop moved
        assertTrue(out.summaryTh.contains("ระบบปรับจุดผิดทาง"), out.summaryTh)

        val sell = WakePrompt.enforceStopDistance(v("SELL", 84_860.0, 84_700.0), price, atr)
        assertEquals(84_894.0, sell.levelSl)
        assertTrue(sell.notify)
    }

    @Test
    fun `a widened stop that leaves RR under 1 to 1 is not sent`() {
        val out = WakePrompt.enforceStopDistance(v("BUY", 84_830.0, 84_880.0), price, atr)
        assertEquals("SKIP", out.decision)
        assertTrue(out.reasonTh.startsWith("ระบบไม่แจ้ง: SL"), out.reasonTh)
        assertTrue(out.reasonTh.contains("RR เหลือ 1:0.72"), out.reasonTh)
        // a SKIP caused by the stop rule is its own reason in the summary, not the counter-trend one
        val view = AiViewTracker.View("s", "BTCUSDT", "15m", "SKIP", "BUY", 70, out.reasonTh, price, atr, null, null, 0L)
        val text = WakeVerdictSummary.build(
            listOf(view), "BTCUSDT", WakeVerdictSummary.Period(0L, "ทั้งหมด"), 60_000L, TimeZone.UTC
        )
        assertTrue(text.contains("ระบบตัดเพราะ SL แคบกว่า 1 ATR"), text)
        assertFalse(text.contains("สวนเทรนด์ใหญ่"), text)
    }

    @Test
    fun `a stop that is far enough, a skip, no direction or no ATR is left alone`() {
        val far = v("BUY", 84_780.0, 84_950.0)
        assertSame(far, WakePrompt.enforceStopDistance(far, price, atr))
        val skip = v("BUY", 84_830.0, 84_950.0, decision = "SKIP")
        assertSame(skip, WakePrompt.enforceStopDistance(skip, price, atr))
        val neutral = v("NEUTRAL", null, null)
        assertSame(neutral, WakePrompt.enforceStopDistance(neutral, price, atr))
        val tight = v("BUY", 84_830.0, 84_950.0)
        assertSame(tight, WakePrompt.enforceStopDistance(tight, price, null))
        assertSame(tight, WakePrompt.enforceStopDistance(tight, price, 0.0))
    }

    @Test
    fun `without a target the stop is still widened`() {
        val out = WakePrompt.enforceStopDistance(v("BUY", 84_830.0, null), price, atr)
        assertTrue(out.notify)
        assertEquals(84_794.0, out.levelSl)
    }

    @Test
    fun `the prompt states the minimum stop distance with the ATR value`() {
        val p = WakePrompt.build("BTCUSDT", mapOf("timeframe" to "15m", "close" to "84844", "wake_atr" to "50.0"))
        assertTrue(p.contains("LEVEL_SL ต้องห่างราคาปัจจุบันอย่างน้อย 1 ATR ของ 15M"), p)
        assertTrue(p.contains("ATR14 = 50"), p)
    }
}

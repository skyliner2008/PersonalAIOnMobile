package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.wake.TriggerEvent
import com.skyliner2008.jarvis.automation.wake.WakeGovernor
import com.skyliner2008.jarvis.automation.wake.WakeLearningStore
import com.skyliner2008.jarvis.automation.wake.WakePrompt
import com.skyliner2008.jarvis.automation.wake.WakeSettings
import com.skyliner2008.jarvis.tools.trading.Candle
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ระบบปลุก AI — งบการปลุก (Governor), คณิตของการเรียนรู้, และการแยกคำตอบของ AI
 */
class WakeSystemTest {

    private val tfMs = 900_000L
    private val t0 = 1_700_000_000_000L

    @BeforeTest
    fun reset() = WakeGovernor.resetForTest()

    private fun ev(id: String, dir: String = "BUY") = TriggerEvent(id, "15m", "เหตุการณ์ $id", dir, 100.0)

    // ─── Governor ────────────────────────────────────────────────────────────

    @Test
    fun cooldownSuppressesSameTriggerSameDirection() {
        val key = "XAUUSD@15m"
        val first = WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), t0, tfMs)
        assertEquals(1, first.size)
        // แท่งถัดไป (อยู่ใน cooldown) → ไม่นับซ้ำ
        assertTrue(WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), t0 + tfMs, tfMs).isEmpty())
        // ทิศตรงข้าม = เหตุการณ์ใหม่
        assertEquals(1, WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS", "SELL")), t0 + tfMs, tfMs).size)
        // พ้น cooldown แล้ว → ยิงได้อีก
        val after = t0 + (WakeSettings.cooldownBars + 1) * tfMs
        assertEquals(1, WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), after, tfMs).size)
    }

    @Test
    fun oneWakePerBarAndHourlyBudget() {
        val key = "XAUUSD@15m"
        val d1 = WakeGovernor.decide(key, "XAUUSD", t0, t0)
        assertTrue(d1.allowed)
        WakeGovernor.register(key, "XAUUSD", t0, t0)
        assertFalse(WakeGovernor.decide(key, "XAUUSD", t0, t0 + 1_000).allowed, "แท่งเดิมต้องไม่ปลุกซ้ำ")

        // ใช้งบรายชั่วโมงให้หมด (คนละแท่ง)
        repeat(WakeSettings.hourlyBudget - 1) { i ->
            val bar = t0 + (i + 1) * tfMs
            assertTrue(WakeGovernor.decide(key, "XAUUSD", bar, t0 + i * 60_000L).allowed)
            WakeGovernor.register(key, "XAUUSD", bar, t0 + i * 60_000L)
        }
        val blocked = WakeGovernor.decide(key, "XAUUSD", t0 + 99 * tfMs, t0 + 30 * 60_000L)
        assertFalse(blocked.allowed)
        assertNotNull(blocked.reason)
        // สินทรัพย์อื่นยังมีงบของตัวเอง
        assertTrue(WakeGovernor.decide("BTCUSDT@15m", "BTCUSDT", t0, t0 + 30 * 60_000L).allowed)
        // ผ่านไปเกิน 1 ชม. งบกลับมา
        assertTrue(WakeGovernor.decide(key, "XAUUSD", t0 + 200 * tfMs, t0 + 2 * 3_600_000L).allowed)
    }

    // ─── Learning math ───────────────────────────────────────────────────────

    private fun bar(o: Double, h: Double, l: Double, c: Double, i: Int) =
        Candle(open = o, high = h, low = l, close = c, volume = 1.0, timestamp = t0 + i * tfMs, isClosed = true)

    @Test
    fun forwardReturnMeasuredInAtrUnits() {
        val window = listOf(bar(100.0, 104.0, 99.0, 103.0, 1), bar(103.0, 106.0, 97.0, 102.0, 2))
        val (rBuy, mfeBuy, maeBuy) = WakeLearningStore.measure("BUY", 100.0, 2.0, window)
        assertEquals(1.0, rBuy, 1e-9)     // (102-100)/2
        assertEquals(3.0, mfeBuy, 1e-9)   // (106-100)/2
        assertEquals(1.5, maeBuy, 1e-9)   // (100-97)/2

        val (rSell, mfeSell, maeSell) = WakeLearningStore.measure("SELL", 100.0, 2.0, window)
        assertEquals(-1.0, rSell, 1e-9)
        assertEquals(1.5, mfeSell, 1e-9)
        assertEquals(3.0, maeSell, 1e-9)

        // NEUTRAL = วัดขนาดการเคลื่อนที่ (ไม่มีทิศ)
        val (mag, range, _) = WakeLearningStore.measure("NEUTRAL", 100.0, 2.0, window)
        assertEquals(1.0, mag, 1e-9)
        assertEquals(3.0, range, 1e-9)
    }

    @Test
    fun contextBuckets() {
        assertEquals("ALIGNED", WakeLearningStore.mtfAlignOf("BUY", "UP", "UP"))
        assertEquals("CONFLICT", WakeLearningStore.mtfAlignOf("BUY", "DOWN", "DOWN"))
        assertEquals("PARTIAL", WakeLearningStore.mtfAlignOf("SELL", "DOWN", "UP"))
        assertEquals("UNKNOWN", WakeLearningStore.mtfAlignOf("NEUTRAL", "UP", "UP"))
        assertEquals("UNKNOWN", WakeLearningStore.mtfAlignOf("BUY", null, "UP"))

        assertEquals("TREND", WakeLearningStore.adxBucketOf(30.0))
        assertEquals("WEAK", WakeLearningStore.adxBucketOf(22.0))
        assertEquals("RANGE", WakeLearningStore.adxBucketOf(12.0))
        assertEquals("UNKNOWN", WakeLearningStore.adxBucketOf(null))

        assertEquals("HIGH", WakeLearningStore.volBucketOf(1.2))
        assertEquals("NORMAL", WakeLearningStore.volBucketOf(0.5))
        assertEquals("LOW", WakeLearningStore.volBucketOf(0.1))

        assertEquals("ASIA", WakeLearningStore.sessionOf(3))
        assertEquals("LONDON", WakeLearningStore.sessionOf(8))
        assertEquals("NY", WakeLearningStore.sessionOf(14))
        assertEquals("OFF", WakeLearningStore.sessionOf(22))
    }

    // ─── Prompt / verdict ────────────────────────────────────────────────────

    @Test
    fun promptCarriesNoPrefabTradePlan() {
        val prompt = WakePrompt.build("XAUUSD", mapOf(
            "timeframe" to "15m", "close" to "4012.5", "wake_event_count" to "2",
            "wake_events" to "• [M15] EMA ตัดขึ้น → ชี้ BUY", "mtf_context" to "H4: UP"
        ))
        assertTrue(prompt.contains("EMA ตัดขึ้น"))
        assertTrue(prompt.contains("H4: UP"))
        assertTrue(prompt.contains("DECISION"))
        assertFalse(prompt.contains("Entry:"), "ระบบต้องไม่ยัด Entry สำเร็จรูปให้ AI")
    }

    @Test
    fun parsesMultilineVerdict() {
        val v = assertNotNull(WakePrompt.parse("""
            DECISION: NOTIFY
            BIAS: BUY
            LEVEL_SL: 4,001.20
            LEVEL_TP: 4030
            CONFIDENCE: 72%
            REASON_TH: H4 ขาขึ้น M15 เพิ่ง BOS
            SUMMARY_TH: ราคายืนเหนือ OB ได้ ให้จับตาการย่อที่ 4005
        """.trimIndent()))
        assertTrue(v.notify)
        assertEquals("BUY", v.bias)
        assertEquals(4001.2, v.levelSl)
        assertEquals(4030.0, v.levelTp)
        assertEquals(72, v.confidence)
        assertEquals("ราคายืนเหนือ OB ได้ ให้จับตาการย่อที่ 4005", v.summaryTh)
    }

    @Test
    fun parsesSingleLineVerdictAndThaiWithColons() {
        val v = assertNotNull(WakePrompt.parse(
            "**DECISION:** SKIP BIAS: NEUTRAL LEVEL_SL: - LEVEL_TP: - CONFIDENCE: 40 " +
                "REASON_TH: swing: 4050 ยังไม่ชัด SUMMARY_TH: รอก่อน"
        ))
        assertFalse(v.notify)
        assertEquals("NEUTRAL", v.bias)
        assertNull(v.levelSl)
        assertEquals("swing: 4050 ยังไม่ชัด", v.reasonTh)
        assertEquals("รอก่อน", v.summaryTh)
    }

    @Test
    fun rejectsMalformedVerdict() {
        assertNull(WakePrompt.parse("ขอโทษค่ะ ไม่สามารถวิเคราะห์ได้"))
        assertNull(WakePrompt.parse("DECISION: MAYBE"))
    }

    // ─── Review round 2 ──────────────────────────────────────────────────────

    @Test
    fun previewScanDoesNotConsumeCooldown() {
        val key = "XAUUSD@15m"
        assertEquals(1, WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), t0, tfMs, commit = false).size)
        // การสแกนดูอย่างเดียวต้องไม่ทำให้ alert เบื้องหลังพลาดเหตุการณ์เดียวกัน
        assertEquals(1, WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), t0, tfMs).size)
    }

    @Test
    fun governorStateSurvivesRestart() {
        val key = "XAUUSD@15m"
        WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), t0, tfMs)
        WakeGovernor.register(key, "XAUUSD", t0, t0)
        val saved = WakeGovernor.exportState(key, t0, tfMs)

        WakeGovernor.resetForTest()          // จำลองบริการรีสตาร์ท
        WakeGovernor.seed(key, saved)
        assertFalse(WakeGovernor.decide(key, "XAUUSD", t0, t0 + 1_000).allowed, "แท่งที่ปลุกไปแล้วต้องไม่ถูกปลุกซ้ำหลังรีสตาร์ท")
        assertTrue(WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), t0 + tfMs, tfMs).isEmpty(), "cooldown ต้องคงอยู่")
    }

    @Test
    fun neutralOutcomeKeepsSign() {
        val down = listOf(bar(100.0, 101.0, 95.0, 96.0, 1))
        val (r, range, _) = WakeLearningStore.measure("NEUTRAL", 100.0, 2.0, down)
        assertEquals(-2.0, r, 1e-9, "ต้องเก็บทิศไว้ เพื่อวัดว่า AI มองทิศถูกไหม")
        assertEquals(2.5, range, 1e-9)
    }

    private fun closedSeries(fromIdx: Int, count: Int) = (fromIdx until fromIdx + count).map { bar(100.0, 101.0, 99.0, 100.0, it) }

    @Test
    fun forwardWindowStartsAfterDetection() {
        val bars = closedSeries(0, 40)                   // แท่ง i เปิดที่ t0 + i*tf
        val detectedMidBar = t0 + 10 * tfMs + 5 * 60_000L // ตรวจพบกลางแท่งที่ 10
        val w = WakeLearningStore.forwardWindow(bars, detectedMidBar, tfMs)
        assertTrue(w is WakeLearningStore.Window.Ready)
        // แท่งแรกที่นับ = แท่งที่ปิดหลังจุดตรวจพบ (แท่งที่ 10) — ไม่นับแท่งที่ปิดก่อน
        assertEquals(t0 + 10 * tfMs, w.bars.first().timestamp)
        assertEquals(WakeLearningStore.FORWARD_HORIZON_BARS, w.bars.size)

        // แถวรุ่นแรก: created_at = เวลาเปิดแท่งสัญญาณ → เริ่มนับแท่งถัดไป
        val legacy = WakeLearningStore.forwardWindow(bars, t0 + 10 * tfMs, tfMs) as WakeLearningStore.Window.Ready
        assertEquals(t0 + 11 * tfMs, legacy.bars.first().timestamp)

        // แท่งยังไม่ครบ
        assertEquals(WakeLearningStore.Window.NotYet, WakeLearningStore.forwardWindow(bars, t0 + 35 * tfMs + 1, tfMs))
        // ข้อมูลในมือเริ่มหลังจุดตรวจพบ (แอปปิดไปนาน) → วัดไม่ได้ ต้องปิดเป็น EXPIRED ไม่ใช่วัดผิดช่วง
        assertEquals(WakeLearningStore.Window.Uncovered, WakeLearningStore.forwardWindow(closedSeries(20, 40), t0 + 3 * tfMs + 1, tfMs))
    }

    @Test
    fun sanitizeDropsIllogicalLevels() {
        val base = WakePrompt.Verdict("NOTIFY", "BUY", 4010.0, 3990.0, 70, "r", "s")
        // BUY: SL อยู่เหนือราคา / TP อยู่ใต้ราคา = ผิดตรรกะ → ตัดทิ้ง
        val v = WakePrompt.sanitize(base, 4000.0)
        assertNull(v.levelSl); assertNull(v.levelTp)
        val ok = WakePrompt.sanitize(base.copy(levelSl = 3985.0, levelTp = 4030.0), 4000.0)
        assertEquals(3985.0, ok.levelSl); assertEquals(4030.0, ok.levelTp)
        // เลขผิดหลัก (ห่างเกิน 20%) → ตัดทิ้ง
        assertNull(WakePrompt.sanitize(base.copy(levelSl = 398.5), 4000.0).levelSl)
        val sell = WakePrompt.sanitize(WakePrompt.Verdict("NOTIFY", "SELL", 3990.0, 4020.0, 60, "r", "s"), 4000.0)
        assertNull(sell.levelSl); assertNull(sell.levelTp)
    }

    @Test
    fun wakeSystemPromptIsCompact() {
        // system prompt แชทของ JARVIS ยาวหลายหมื่นตัวอักษร — งานปลุกต้องใช้ตัวสั้นเฉพาะทาง
        assertTrue(WakePrompt.SYSTEM_PROMPT.length < 600, "${WakePrompt.SYSTEM_PROMPT.length}")
    }
}

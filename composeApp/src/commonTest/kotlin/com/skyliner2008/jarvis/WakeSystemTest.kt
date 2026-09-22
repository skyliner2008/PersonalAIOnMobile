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

    private fun ev(id: String, dir: String = "BUY", tf: String = "15m") = TriggerEvent(id, tf, "เหตุการณ์ $id", dir, 100.0)
    private fun bars(ts: Long) = mapOf("15m" to ts)

    // ─── Governor ────────────────────────────────────────────────────────────

    @Test
    fun cooldownSuppressesSameTriggerSameDirection() {
        val key = "XAUUSD@15m"
        val first = WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), bars(t0))
        assertEquals(1, first.size)
        // แท่งถัดไป (อยู่ใน cooldown) → ไม่นับซ้ำ
        assertTrue(WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), bars(t0 + tfMs)).isEmpty())
        // ทิศตรงข้าม = เหตุการณ์ใหม่
        assertEquals(1, WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS", "SELL")), bars(t0 + tfMs)).size)
        // พ้น cooldown แล้ว → ยิงได้อีก
        val after = t0 + (WakeSettings.cooldownBars + 1) * tfMs
        assertEquals(1, WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), bars(after)).size)
    }

    /** ปัจจัยเดียวกันคนละ TF = คนละเหตุการณ์ และ cooldown นับเป็นแท่งของ TF ที่เกิด */
    @Test
    fun cooldownIsPerFactorAndTimeframe() {
        val key = "BTCUSDT@15m"
        val ts = mapOf("1m" to t0, "5m" to t0, "15m" to t0, "1h" to t0)
        val both = WakeGovernor.freshEvents(key, listOf(ev("RSI_50_CROSS", tf = "5m"), ev("RSI_50_CROSS", tf = "1h")), ts)
        assertEquals(2, both.size, "RSI_50_CROSS บน M5 และ H1 ต้องนับแยกกัน")
        // 4 แท่ง M5 ต่อมา (20 นาที) พ้น cooldown ของ M5 แต่ยังอยู่ใน cooldown ของ H1
        val later = mapOf("5m" to t0 + 4 * 300_000L, "1h" to t0)
        val again = WakeGovernor.freshEvents(key, listOf(ev("RSI_50_CROSS", tf = "5m"), ev("RSI_50_CROSS", tf = "1h")), later)
        assertEquals(listOf("5m"), again.map { it.tf })
    }

    @Test
    fun minimumGapAndHourlyBudget() {
        val key = "XAUUSD@15m"
        assertTrue(WakeGovernor.decide(key, "XAUUSD", t0).allowed)
        WakeGovernor.register(key, "XAUUSD", t0)
        val gap = WakeSettings.minGapMinutes * 60_000L
        assertFalse(WakeGovernor.decide(key, "XAUUSD", t0 + gap - 1_000).allowed, "ต้องเว้นระยะขั้นต่ำ")
        // พ้นระยะห่างแล้วปลุกได้ แม้ยังอยู่ในแท่ง M15 เดิม (เดิมปลุกได้ครั้งเดียวต่อแท่ง)
        assertTrue(WakeGovernor.decide(key, "XAUUSD", t0 + gap).allowed)

        // ใช้งบรายชั่วโมงให้หมด
        repeat(WakeSettings.hourlyBudget - 1) { i ->
            val at = t0 + (i + 1) * gap
            assertTrue(WakeGovernor.decide(key, "XAUUSD", at).allowed, "ครั้งที่ ${i + 2}")
            WakeGovernor.register(key, "XAUUSD", at)
        }
        val blocked = WakeGovernor.decide(key, "XAUUSD", t0 + WakeSettings.hourlyBudget * gap)
        assertFalse(blocked.allowed)
        assertNotNull(blocked.reason)
        // สินทรัพย์อื่นยังมีงบของตัวเอง
        assertTrue(WakeGovernor.decide("BTCUSDT@15m", "BTCUSDT", t0 + 30 * 60_000L).allowed)
        // ผ่านไปเกิน 1 ชม. งบกลับมา
        assertTrue(WakeGovernor.decide(key, "XAUUSD", t0 + 2 * 3_600_000L).allowed)
    }

    /** เหตุการณ์ที่ติดระยะห่างไม่หาย — พกไปการปลุกครั้งถัดไป แต่ไม่เกิน CARRY_MS */
    @Test
    fun blockedEventsCarryToNextWake() {
        val key = "BTCUSDT@15m"
        WakeGovernor.defer(key, listOf(ev("LIQUIDITY_SWEEP_REJECTION", tf = "1m")), t0)
        WakeGovernor.defer(key, listOf(ev("LIQUIDITY_SWEEP_REJECTION", tf = "1m")), t0 + 30_000L)   // ซ้ำ ไม่เพิ่ม
        assertTrue(WakeGovernor.hasDeferred(key, t0 + 60_000L))
        val carried = WakeGovernor.takeDeferred(key, t0 + 60_000L)
        assertEquals(1, carried.size)
        assertEquals(t0, carried.single().second)
        assertTrue(WakeGovernor.takeDeferred(key, t0 + 61_000L).isEmpty(), "ดึงแล้วต้องล้าง")
        WakeGovernor.defer(key, listOf(ev("VOLUME_SPIKE", tf = "5m")), t0)
        assertFalse(WakeGovernor.hasDeferred(key, t0 + WakeGovernor.CARRY_MS + 1), "เก่าเกินไปหมดความหมาย")
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
        assertEquals(1, WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), bars(t0), commit = false).size)
        // การสแกนดูอย่างเดียวต้องไม่ทำให้ alert เบื้องหลังพลาดเหตุการณ์เดียวกัน
        assertEquals(1, WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), bars(t0)).size)
    }

    @Test
    fun governorStateSurvivesRestart() {
        val key = "XAUUSD@15m"
        WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS"), ev("EMA_CROSS", tf = "1h")), mapOf("15m" to t0, "1h" to t0))
        WakeGovernor.register(key, "XAUUSD", t0)
        val saved = WakeGovernor.exportState(key, t0 + 1_000)

        WakeGovernor.resetForTest()          // จำลองบริการรีสตาร์ท
        WakeGovernor.seed(key, saved)
        assertFalse(WakeGovernor.decide(key, "XAUUSD", t0 + 1_000).allowed, "การปลุกล่าสุดต้องคงอยู่หลังรีสตาร์ท")
        assertTrue(WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), bars(t0 + tfMs)).isEmpty(), "cooldown ต้องคงอยู่")
        assertTrue(WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS", tf = "1h")), mapOf("1h" to t0 + 3_600_000L)).isEmpty(),
            "cooldown ของ H1 ต้องคงอยู่")
    }

    /** memory รุ่นก่อน P16 ("ID|DIR@ts") = เกิดบน TF ของ job */
    @Test
    fun legacyCooldownMemoryMapsToJobTimeframe() {
        val key = "XAUUSD@15m"
        WakeGovernor.seed(key, mapOf("gov_fired" to "EMA_CROSS|BUY@$t0", "gov_last_wake" to "$t0"))
        assertTrue(WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS")), bars(t0 + tfMs)).isEmpty())
        assertEquals(1, WakeGovernor.freshEvents(key, listOf(ev("EMA_CROSS", tf = "5m")), mapOf("5m" to t0)).size)
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

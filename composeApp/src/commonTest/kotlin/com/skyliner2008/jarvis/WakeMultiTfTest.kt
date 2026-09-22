package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.wake.WakeContext
import com.skyliner2008.jarvis.automation.wake.WakeLearningStore
import com.skyliner2008.jarvis.automation.wake.WakeTfProfile
import com.skyliner2008.jarvis.automation.wake.WakeTriggerRegistry
import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ประเมินปัจจัยบนหลาย TF (P16): โปรไฟล์ TF ต้องตรงกับทะเบียน และเหตุการณ์ต้องติด TF ที่เกิดจริง */
class WakeMultiTfTest {

    private fun candles(count: Int, stepMs: Long, trend: Double, seed: Int): List<Candle> {
        var p = 2000.0
        val start = 1_800_000_000_000L - count * stepMs
        return (0 until count).map { i ->
            p += trend + sin((i + seed) / 5.0) * 3.0 + (if (i % 37 == 0) 9.0 else 0.0)
            Candle(p - 1.0, p + 4.0 + (i % 7), p - 4.0 - (i % 5), p, 1000.0 + (i % 13) * 60.0, start + i * stepMs, true)
        }
    }

    private fun ctx(): WakeContext {
        val n = TaIndicators.Warmup.FULL_SET
        return WakeContext(
            symbol = "BTCUSDT", primaryTf = "15m",
            m1 = candles(n, 60_000L, 0.1, 1), m5 = candles(n, 300_000L, -0.2, 7), m15 = candles(n, 900_000L, 0.4, 13),
            h1 = candles(n, 3_600_000L, -0.5, 21), h4 = candles(n, 14_400_000L, 0.6, 29),
            digest = null, nowMs = 1_800_000_000_000L
        )
    }

    @Test
    fun profileCoversEveryTriggerExactlyOnce() {
        val ids = WakeTriggerRegistry.ALL.map { it.id }.toSet()
        val unknownFixed = WakeTfProfile.FIXED - ids
        assertTrue(unknownFixed.isEmpty(), "FIXED มีชื่อที่ไม่อยู่ในทะเบียน: $unknownFixed")
        // ทุกปัจจัยที่ประเมินได้ทุก TF ต้องมีชุด TF เริ่มต้นที่กำหนดเอง (ไม่ตกไปใช้ค่า default แบบเงียบๆ)
        val perTf = ids - WakeTfProfile.FIXED
        val missing = perTf.filter { !WakeTfProfile.hasProfile(it) }
        assertTrue(missing.isEmpty(), "ปัจจัยที่ยังไม่มีโปรไฟล์ TF: $missing")
        // ทุกชุดต้องมี M15 (TF เดิมของระบบ — ไม่ทำให้ปัจจัยที่เคยปลุกได้เงียบไป)
        perTf.forEach { assertTrue(WakeTfProfile.M15 in WakeTfProfile.defaultWakeTfs(it), "$it ไม่มี M15") }
        assertEquals(ids.size, perTf.size + WakeTfProfile.FIXED.size)
    }

    @Test
    fun jobTimeframeAlwaysWakesAndDuplicatesAreRecordOnly() {
        assertTrue(WakeTfProfile.isDefaultWakeTf("CCI_100_CROSS", "15m", "15m"))
        assertFalse(WakeTfProfile.isDefaultWakeTf("CCI_100_CROSS", "1m", "15m"), "oscillator บน M1 = เก็บสถิติอย่างเดียว")
        assertTrue(WakeTfProfile.isDefaultWakeTf("CCI_100_CROSS", "30m", "30m"), "TF ของ job ปลุกได้เสมอ")
        assertTrue(WakeTfProfile.isDefaultWakeTf("LIQUIDITY_SWEEP_REJECTION", "1m", "15m"))
        assertFalse(WakeTfProfile.isDefaultWakeTf("HTF_CHOCH_H1", "1h", "15m"), "ซ้ำกับ CHOCH บน H1")
        assertTrue(WakeTfProfile.isDefaultWakeTf("DXY_MOVE", "1h", "15m"))
        // ไม่มีสถิติ (ไม่มีฐานข้อมูลในเทสต์) → ใช้ชุดเริ่มต้น
        assertEquals(WakeLearningStore.TfVerdict.WAKE, WakeLearningStore.tfVerdict("BOS", "5m", "15m"))
        assertEquals(WakeLearningStore.TfVerdict.RECORD, WakeLearningStore.tfVerdict("BOS", "1m", "15m"))
    }

    @Test
    fun fixedEventTimeframeIsNormalized() {
        assertEquals("15m", WakeTfProfile.normalizeEventTf("multi", "15m"), "\"multi\" ต้องไม่กลายเป็น 1h")
        assertEquals("4h", WakeTfProfile.normalizeEventTf("4h", "15m"))
        assertEquals("30m", WakeTfProfile.normalizeEventTf("?", "30m"))
    }

    @Test
    fun scanAllTfTagsEventsWithTheirTimeframe() {
        val c = ctx()
        val scan = WakeTriggerRegistry.scanAllTf(c, WakeTfProfile.EVAL_TFS)
        assertTrue(scan.errors.isEmpty(), "${scan.errors.take(5)}")
        val all = scan.events + scan.states
        val tfs = all.filter { !WakeTfProfile.isFixed(it.triggerId) }.map { it.tf }.toSet()
        assertTrue(tfs.size >= 4, "ควรมีเหตุการณ์จากหลาย TF: $tfs")
        assertTrue(all.all { it.tf in setOf("1m", "5m", "15m", "30m", "1h", "4h") }, "TF ไม่ถูกต้อง: ${all.map { it.tf }.toSet()}")
        // ผลบน TF หนึ่ง = ผลของการสแกนเดี่ยวเมื่อให้ TF นั้นเป็น TF หลัก (ใช้ Series ชุดเดียวกัน)
        for (tf in WakeTfProfile.EVAL_TFS) {
            val single = WakeTriggerRegistry.scan(c.forTf(tf)).let { it.events + it.states }
                .filter { !WakeTfProfile.isFixed(it.triggerId) }.map { it.triggerId to it.direction }.toSet()
            val multi = all.filter { it.tf == tf && !WakeTfProfile.isFixed(it.triggerId) }.map { it.triggerId to it.direction }.toSet()
            assertEquals(single, multi, "TF $tf")
        }
        // ปัจจัยตายตัวประเมินครั้งเดียว
        val fixedCounts = all.filter { WakeTfProfile.isFixed(it.triggerId) }.groupingBy { it.triggerId }.eachCount()
        assertTrue(fixedCounts.values.all { it == 1 }, "$fixedCounts")
    }

    @Test
    fun forTfSharesSeriesInstances() {
        val c = ctx()
        val h1 = c.forTf("1h")
        assertTrue(h1.primary === c.h1, "ต้องใช้ Series เดิม ไม่คำนวณอินดิเคเตอร์ใหม่")
        assertEquals("1h", h1.primaryTf)
        assertTrue(c.forTf("15m") === c)
    }
}

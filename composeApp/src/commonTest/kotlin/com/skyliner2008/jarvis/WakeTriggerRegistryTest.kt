package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.wake.EvidenceGroup
import com.skyliner2008.jarvis.automation.wake.TriggerKind
import com.skyliner2008.jarvis.automation.wake.WakeContext
import com.skyliner2008.jarvis.automation.wake.WakeTriggerRegistry
import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ทะเบียนตัวปลุก AI — ล็อกสัญญาหลักของระบบ
 *
 * หลักการ: ปัจจัยคือ "นาฬิกาปลุก" ไม่ใช่เทรดเดอร์
 * จึงห้ามคืน Entry/SL/TP หรือสรุปแทน AI
 */
class WakeTriggerRegistryTest {

    private fun candles(count: Int, trend: Double = 0.4, seed: Int = 0): List<Candle> {
        var p = 2000.0
        return (0 until count).map { i ->
            p += trend + sin((i + seed) / 6.0) * 3.0
            Candle(
                open = p - 1.0, high = p + 4.0, low = p - 4.0, close = p,
                volume = 1000.0 + (i % 13) * 40.0,
                timestamp = 1_700_000_000_000L + i * 900_000L,
                isClosed = true
            )
        }
    }

    private fun ctx(
        primaryTf: String = "15m",
        bars: Int = TaIndicators.Warmup.FULL_SET,
        trend: Double = 0.4
    ): WakeContext {
        val c = candles(bars, trend)
        return WakeContext(
            symbol = "XAUUSD",
            primaryTf = primaryTf,
            m1 = c, m5 = c, m15 = c, h1 = c, h4 = c,
            digest = null,
            nowMs = c.last().timestamp + 900_000L
        )
    }

    // ─── สัญญาของระบบ ────────────────────────────────────────────────────────

    @Test
    fun everyTriggerHasUniqueIdAndCompleteMetadata() {
        val ids = WakeTriggerRegistry.ALL.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "trigger id ซ้ำ: $ids")
        WakeTriggerRegistry.ALL.forEach { t ->
            assertTrue(t.id.isNotBlank(), "id ว่าง")
            assertTrue(t.name.isNotBlank(), "${t.id} ไม่มีชื่อ")
            assertTrue(t.minBars > 0, "${t.id} minBars ต้องมากกว่า 0")
            assertNotNull(WakeTriggerRegistry.find(t.id), "${t.id} หาไม่เจอในทะเบียน")
            // ค้นหาแบบไม่สนตัวพิมพ์ต้องได้ผลเดียวกัน
            assertEquals(t, WakeTriggerRegistry.find(t.id.lowercase()))
        }
    }

    /**
     * ครอบคลุมทุกกลุ่มหลักฐาน — ถ้ากลุ่มไหนไม่มีตัวแทน
     * แปลว่าระบบตาบอดกับเหตุการณ์ประเภทนั้นทั้งหมด
     */
    @Test
    fun registryCoversAllEvidenceGroups() {
        val covered = WakeTriggerRegistry.ALL.map { it.evidenceGroup }.toSet()
        val missing = EvidenceGroup.entries.filter { it !in covered }
        assertTrue(missing.isEmpty(), "กลุ่มหลักฐานที่ยังไม่มีตัวปลุก: $missing")
    }

    @Test
    fun registryHasBothEventAndStateTriggers() {
        assertTrue(WakeTriggerRegistry.events.isNotEmpty(), "ต้องมี trigger ประเภท EVENT")
        assertTrue(WakeTriggerRegistry.states.isNotEmpty(), "ต้องมี trigger ประเภท STATE")
        // EVENT + STATE ต้องรวมกันได้ทั้งหมด ไม่มีตัวตกหล่น
        assertEquals(
            WakeTriggerRegistry.ALL.size,
            WakeTriggerRegistry.events.size + WakeTriggerRegistry.states.size
        )
    }

    /**
     * หัวใจของการ reframe: trigger ห้ามสรุปแทน AI
     * TriggerEvent ต้องไม่มีสนาม entry/sl/tp — ตรวจผ่าน property ที่มีอยู่จริง
     */
    @Test
    fun triggerEventsCarryNoTradePlan() {
        val scan = WakeTriggerRegistry.scan(ctx())
        scan.all.forEach { e ->
            assertTrue(e.triggerId.isNotBlank())
            assertTrue(e.what.isNotBlank(), "${e.triggerId} ต้องอธิบายว่าเกิดอะไรขึ้น")
            assertTrue(
                e.direction in setOf("BUY", "SELL", "NEUTRAL"),
                "${e.triggerId} direction ไม่ถูกต้อง: ${e.direction}"
            )
        }
    }

    // ─── พฤติกรรมการตรวจจับ ──────────────────────────────────────────────────

    @Test
    fun triggersReturnNothingWhenDataInsufficient() {
        val tiny = ctx(bars = 20)
        val scan = WakeTriggerRegistry.scan(tiny)
        // แท่งน้อยเกินไป — ตัวที่ต้องการ warm-up ต้องไม่ยิง
        listOf("EMA_CROSS", "MACD_SIGNAL_CROSS", "RSI_EXIT_EXTREME", "GOLDEN_DEATH_CROSS", "ICHIMOKU_CLOUD_BREAK").forEach { id ->
            val t = assertNotNull(WakeTriggerRegistry.find(id), "ไม่มี $id")
            assertTrue(t.minBars > 20, "$id ต้องต้องการ warm-up มากกว่า 20 แท่ง")
            assertNull(t.detect(tiny), "$id ยิงทั้งที่ข้อมูลไม่พอ")
        }
        // และต้องไม่ crash
        assertTrue(scan.events.size + scan.states.size >= 0)
    }

    @Test
    fun scanRespectsEnabledFilter() {
        val c = ctx()
        val onlyEma = WakeTriggerRegistry.scan(c, enabledIds = setOf("EMA_CROSS"))
        assertTrue(
            onlyEma.all.all { it.triggerId == "EMA_CROSS" },
            "กรองแล้วยังมีตัวอื่นหลุดมา: ${onlyEma.all.map { it.triggerId }}"
        )
        val none = WakeTriggerRegistry.scan(c, enabledIds = emptySet())
        assertTrue(none.isEmpty, "กรองหมดแล้วต้องไม่มีอะไรเหลือ")
    }

    /** ตัวปลุกที่ใช้ digest ต้องไม่ยิงเมื่อไม่มี digest — ไม่ใช่เดาเอง */
    @Test
    fun digestBackedTriggersRequireDigest() {
        val c = ctx()  // digest = null
        listOf("KEY_LEVEL_TOUCH", "OB_TOUCH", "FVG_ENTER", "DEEP_SCORE_CROSS", "HARMONIC_COMPLETION",
            "DXY_MOVE", "YIELD_SPIKE", "HIGH_IMPACT_NEWS_SOON", "FEAR_GREED_EXTREME").forEach { id ->
            val t = assertNotNull(WakeTriggerRegistry.find(id), "ไม่มี $id")
            assertNull(t.detect(c), "$id ต้องไม่ยิงเมื่อไม่มีข้อมูลที่ตัวมันพึ่งพา")
        }
    }

    /** ใช้เฉพาะแท่งที่ปิดแล้ว — กันการยิงจากแท่งที่ยังก่อตัว (repainting) */
    @Test
    fun triggersIgnoreUnclosedBars() {
        val closed = candles(TaIndicators.Warmup.FULL_SET)
        val withForming = closed + closed.last().copy(
            timestamp = closed.last().timestamp + 900_000L,
            high = closed.last().high + 100.0,   // แท่งสดที่ยังไม่ปิด ราคาสุดขั้ว
            low = closed.last().low - 100.0,
            isClosed = false
        )
        val now = withForming.last().timestamp + 60_000L
        val base = WakeContext("XAUUSD", "15m", closed, closed, closed, closed, closed, null, nowMs = now)
        val withLive = WakeContext("XAUUSD", "15m", withForming, withForming, withForming, withForming, withForming, null, nowMs = now)

        // แท่งสดที่มีไส้ยาวผิดปกติต้องไม่ทำให้ผลเปลี่ยน เพราะ Series ใช้เฉพาะแท่งปิด
        // (ยกเว้นตัวที่ตั้งใจดูราคาสด เช่น ANOMALY — ตรวจเฉพาะหมวดที่อิงแท่งปิด)
        val closedOnly = WakeTriggerRegistry.ALL.filter {
            it.evidenceGroup in setOf(EvidenceGroup.LIQUIDITY_SWEEP, EvidenceGroup.TREND_MOMENTUM,
                EvidenceGroup.OSCILLATOR_EXTREME, EvidenceGroup.PRICE_ACTION, EvidenceGroup.STRUCTURE)
        }.map { it.id }.toSet()
        fun ids(c: WakeContext) = WakeTriggerRegistry.scan(c, closedOnly).all.map { it.triggerId to it.direction }.toSet()
        assertEquals(ids(base), ids(withLive))
    }

    /** ปัจจัยที่วัดเรื่องเดียวกันต้องอยู่กลุ่มเดียวกัน — กันนับหลักฐานซ้ำ */
    @Test
    fun correlatedTriggersShareEvidenceGroup() {
        fun t(id: String) = assertNotNull(WakeTriggerRegistry.find(id), "ไม่มี $id")
        assertEquals(EvidenceGroup.TREND_MOMENTUM, t("EMA_CROSS").evidenceGroup)
        assertEquals(EvidenceGroup.TREND_MOMENTUM, t("GOLDEN_DEATH_CROSS").evidenceGroup)
        assertEquals(EvidenceGroup.OSCILLATOR_EXTREME, t("MACD_SIGNAL_CROSS").evidenceGroup)
        assertEquals(EvidenceGroup.OSCILLATOR_EXTREME, t("RSI_DIVERGENCE").evidenceGroup)
        assertEquals(EvidenceGroup.MTF_DIVERGENCE, t("M5_CONFIRM_DIVERGENCE").evidenceGroup)
        assertEquals(EvidenceGroup.MTF_DIVERGENCE, t("HTF_CONFLICT").evidenceGroup)
        // ตัวคัดกรองการหลอกต้องเป็น STATE (เป็นบริบท ไม่ใช่เหตุให้ปลุก)
        assertEquals(TriggerKind.STATE, t("M5_CONFIRM_DIVERGENCE").kind)
        assertEquals(TriggerKind.STATE, t("HTF_CONFLICT").kind)
    }

    /** WakeContext ต้องหยิบแท่งของ TF ที่ขอได้ถูกต้อง */
    @Test
    fun wakeContextResolvesTimeframes() {
        val m1 = candles(10, seed = 1)
        val m5 = candles(11, seed = 2)
        val m15 = candles(12, seed = 3)
        val h1 = candles(13, seed = 4)
        val h4 = candles(14, seed = 5)
        val c = WakeContext("XAUUSD", "1h", m1, m5, m15, h1, h4, null, nowMs = 0L)
        assertEquals(10, c.series("1m").n)
        assertEquals(11, c.series("M5").n)
        assertEquals(12, c.series("15m").n)
        assertEquals(13, c.series("1h").n)
        assertEquals(14, c.series("H4").n)
        assertEquals(13, c.primary.n, "primaryTf=1h ต้องคืนแท่ง H1")
    }

    /** คลังต้องใหญ่พอให้ระบบเรียนรู้คัดกรองเอง (ผู้ใช้ต้องการ ~100+ ปัจจัย) */
    @Test
    fun registryIsComprehensive() {
        assertTrue(WakeTriggerRegistry.ALL.size >= 100, "มีเพียง ${WakeTriggerRegistry.ALL.size} ปัจจัย")
        EvidenceGroup.entries.forEach { g ->
            assertTrue(WakeTriggerRegistry.BY_GROUP[g].orEmpty().isNotEmpty(), "หมวด $g ว่าง")
        }
    }

    /** ทุก trigger ต้องไม่ throw กับข้อมูลหลายรูปแบบ (ขาขึ้น/ขาลง/ข้าง/ข้อมูลน้อย) */
    @Test
    fun scanNeverThrows() {
        listOf(0.4, -0.4, 0.0).forEach { tr ->
            listOf(5, 60, TaIndicators.Warmup.FULL_SET).forEach { n ->
                val r = WakeTriggerRegistry.scan(ctx(bars = n, trend = tr))
                assertTrue(r.errors.isEmpty(), "trend=$tr bars=$n errors=${r.errors}")
            }
        }
    }

    // ─── Review round 2 ──────────────────────────────────────────────────────

    @Test
    fun timeframeLabels() {
        assertEquals("MTF", com.skyliner2008.jarvis.automation.wake.tfLabel("multi"))
        assertEquals("ข่าว", com.skyliner2008.jarvis.automation.wake.tfLabel("event"))
        assertEquals("M30", com.skyliner2008.jarvis.automation.wake.tfLabel("30m"))
        assertEquals("H4", com.skyliner2008.jarvis.automation.wake.tfLabel("4h"))
        assertEquals("D1", com.skyliner2008.jarvis.automation.wake.tfLabel("1D"))
    }

    @Test
    fun priceFormatFitsEveryPriceScale() {
        fun f(v: Double) = com.skyliner2008.jarvis.automation.wake.formatPrice(v)
        assertEquals("4012.50", f(4012.5))
        assertEquals("1.08523", f(1.08523))
        assertEquals("30.10", f(30.1))
        assertEquals("0.00002", f(0.00002))   // เดิมแสดงเป็น 0.0000
    }

    @Test
    fun swingPivotsPointAtTheActualExtreme() {
        val s = ctx().primary
        val lag = com.skyliner2008.jarvis.automation.smc.UnifiedSmcSignals.SWING_L
        assertTrue(s.swingHighs.isNotEmpty())
        s.swingHighPivots.zip(s.swingHighs).forEach { (pivot, confirm) ->
            assertEquals(confirm.first - lag, pivot.first)
            assertEquals(s.bars[pivot.first].high, pivot.second, "ราคาของ pivot ต้องเป็น high ของแท่งนั้นจริง")
        }
    }

    /** 30m เป็น TF หลักได้จริง — เดิมถูกประเมินบน M15 เงียบๆ */
    @Test
    fun thirtyMinutePrimaryUsesItsOwnBars() {
        val c15 = candles(300)
        val c30 = candles(123, seed = 7)
        val c = WakeContext("XAUUSD", "30m", c15, c15, c15, c15, c15, null, nowMs = 0L, extra = mapOf("30m" to c30))
        assertEquals(123, c.primary.n)
        assertEquals("30m", c.primary.tf)
    }

    /**
     * PDH/PDL ต้องอ้างอิงวันก่อนหน้าของ "วันนี้" แม้ยังไม่มีแท่ง H1 ของวันนี้ปิด
     * (เดิมชั่วโมงแรกของวันได้ High ของเมื่อวานซืน)
     */
    @Test
    fun previousDayLevelsAtStartOfNewDay() {
        val day = 86_400_000L
        val d0 = 19_675L * day                     // เที่ยงคืน UTC
        val hour = 3_600_000L
        fun c(ts: Long, o: Double, h: Double, l: Double, cl: Double) = Candle(o, h, l, cl, 1000.0, ts, true)
        // วันที่ 1: high 100 · วันที่ 2 (= เมื่อวาน): high 110
        val h1 = (0 until 24).map { c(d0 + it * hour, 95.0, 100.0, 90.0, 95.0) } +
            (0 until 24).map { c(d0 + day + it * hour, 100.0, 110.0, 95.0, 100.0) }
        // M15: แท่ง 23:45 ของเมื่อวานไม่แตะ 110, แท่ง 00:00 ของวันนี้แตะ 110
        val m15 = (0 until 70).map { c(d0 + 2 * day - (70 - it) * 900_000L, 100.0, 105.0, 100.0, 102.0) } +
            c(d0 + 2 * day, 104.0, 111.0, 104.0, 108.0)
        val ctx = WakeContext(
            "BTCUSDT", "15m", m15, m15, m15, h1, h1, null,
            nowMs = d0 + 2 * day + 20 * 60_000L
        )
        val hit = WakeTriggerRegistry.find("PDH_PDL_TOUCH")!!.detect(ctx)
        assertNotNull(hit, "ต้องเห็นการแตะ High ของเมื่อวาน (110)")
        assertEquals(110.0, hit.referencePrice)
    }
}

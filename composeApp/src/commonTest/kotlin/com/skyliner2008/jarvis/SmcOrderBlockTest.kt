package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.smc.FvgDetection
import com.skyliner2008.jarvis.automation.smc.OrderBlockDetection
import com.skyliner2008.jarvis.automation.smc.SmcEngine
import com.skyliner2008.jarvis.tools.trading.Candle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Order Block ต้องเกิดได้แม้ FVG คู่ของมันอยู่เก่ากว่า 30 แท่ง
 *
 * บั๊กเดิม: [FvgDetection] ค้นหาแค่ 30 แท่งท้าย แต่ [OrderBlockDetection] ค้น swing ทั้งหน้าต่าง
 * และรับ OB เฉพาะที่มี FVG ห่าง ≤ 5 แท่ง → OB ที่เก่ากว่า 30 แท่งหา FVG คู่ไม่เจอ เลยไม่เกิด OB เลย
 * (บนแท่ง BTC จริง 300 แท่ง: OB = 0 ทุกครั้ง → setup แบบ OB, กำแพง OB, คะแนน confluence และระดับ OB ในภาพตลาด ตายทั้งหมด)
 */
class SmcOrderBlockTest {

    /**
     * ฉาก: ไซด์เวย์ → ยอด pivot ที่ 101 → ย่อลงทำแท่งแดงต่ำสุด (แท่ง OB) → พุ่งขึ้นทิ้ง FVG และปิดทะลุยอด
     * → เทรนด์ขึ้นยาวโดยไม่กลับมาแตะโซน OB อีก (OB จึงยัง active ตอนจบ แต่เก่ากว่า 30 แท่ง)
     */
    private fun bars(returnToZone: Boolean = false): List<Candle> {
        val out = mutableListOf<Candle>()
        var ts = 1_800_000_000_000L
        fun bar(o: Double, h: Double, l: Double, c: Double) {
            out += Candle(o, h, l, c, 1000.0, ts, true); ts += 900_000L
        }
        repeat(20) { i -> bar(100.0, 100.3 + (i % 3) * 0.02, 99.6, 100.1) }   // 0..19 ไซด์เวย์
        bar(100.2, 101.0, 100.0, 100.8)                                        // 20 ยอด pivot
        repeat(10) { bar(100.0, 100.4, 99.4, 99.6) }                           // 21..30 ย่อ
        bar(98.7, 98.9, 98.0, 98.1)                                            // 31 แท่งแดงต่ำสุด = OB (body 98.1–98.7)
        bar(98.75, 99.4, 98.72, 99.3)                                          // 32 ดีดขึ้น ไม่แตะโซน OB
        bar(100.5, 101.6, 100.4, 101.5)                                        // 33 ทิ้ง FVG (98.9 → 100.4) + ปิดทะลุ 101
        repeat(126) { i -> bar(101.6 + i * 0.1, 102.0 + i * 0.1, 101.4 + i * 0.1, 101.9 + i * 0.1) }  // 34..159 ขึ้นต่อ
        // ย่อกลับลงมาแตะโซน OB (98.1–98.7) แล้วเด้ง — OB ต้องกลายเป็น "ถูกแตะแล้ว"
        if (returnToZone) {
            repeat(20) { i -> bar(114.4 - i * 0.8, 114.6 - i * 0.8, 113.6 - i * 0.8, 113.7 - i * 0.8) }
            bar(98.6, 98.8, 98.3, 98.5)
            repeat(5) { i -> bar(98.6 + i, 99.2 + i, 98.4 + i, 99.1 + i) }
        }
        return out
    }

    @Test
    fun fvgSearchWindowMustCoverTheOrderBlockSearch() {
        val c = bars()
        // ค่าเริ่มต้นมองแค่ 30 แท่งท้าย — FVG ที่แท่ง 33 อยู่นอกสายตา
        val recent = FvgDetection.detect(c)
        assertTrue(recent.none { it.barIndex == 33 }, "FVG แท่ง 33 ไม่ควรอยู่ในหน้าต่าง 30 แท่งท้าย")
        assertEquals(emptyList(), OrderBlockDetection.detect(c, recent).first, "นี่คือบั๊กเดิม: ไม่มี OB เลย")

        // ค้นทั้งหน้าต่าง → เจอ FVG คู่ของ OB
        val full = FvgDetection.detect(c, lookback = c.size)
        assertTrue(full.any { it.isBull && it.barIndex == 33 }, "ต้องเจอ FVG ขาขึ้นที่แท่ง 33: ${full.map { it.barIndex }}")
        val bull = OrderBlockDetection.detect(c, full).first
        assertTrue(bull.isNotEmpty(), "ต้องเจอ Demand OB")
        val ob = bull.first { it.barIndex == 31 }
        assertTrue(ob.hasFvg && !ob.mitigated && !ob.invalidated, "OB แท่ง 31 ต้องยังไม่ถูกแตะ (ราคาไม่เคยกลับลงมา)")
        assertEquals(98.1, ob.bottom, 1e-9)
        assertEquals(98.7, ob.top, 1e-9)
    }

    /**
     * mitigation ต้องเริ่มนับ "หลังแท่งที่ทะลุโครงสร้าง" ไม่ใช่หลังแท่ง OB
     * (แท่งถัดจาก OB คือ impulse ที่สร้าง OB เอง — ถ้านับด้วย OB ทุกอันจะถูกแตะทันทีที่เกิด)
     */
    @Test
    fun mitigationCountsOnlyWhenPriceComesBackAfterTheBreak() {
        val fresh = bars()
        val obFresh = OrderBlockDetection.detect(fresh, FvgDetection.detect(fresh, lookback = fresh.size)).first
            .first { it.barIndex == 31 }
        assertTrue(!obFresh.mitigated, "ราคาไม่เคยกลับมา → ยัง active")
        assertTrue(obFresh.breakIndex > obFresh.barIndex, "ต้องรู้ว่าแท่งไหนคือแท่งทะลุ")

        val revisited = bars(returnToZone = true)
        val obUsed = OrderBlockDetection.detect(revisited, FvgDetection.detect(revisited, lookback = revisited.size)).first
            .first { it.barIndex == 31 }
        assertTrue(obUsed.mitigated, "ราคากลับลงมาแตะโซนแล้ว → ถูกแตะ")
    }

    @Test
    fun engineSnapshotExposesActiveOrderBlocks() {
        val snap = SmcEngine.buildSnapshot(bars(), "TEST", "15m")
        assertTrue(snap.bullObs.any { !it.mitigated }, "snapshot ต้องมี Demand OB ที่ยัง active")
        // FVG สำหรับเข้าเทรดยังเป็นของช่วงล่าสุดเหมือนเดิม (ไม่เปลี่ยนพฤติกรรมฝั่งสัญญาณ)
        assertTrue(snap.activeFvgs.none { it.barIndex == 33 })
    }
}

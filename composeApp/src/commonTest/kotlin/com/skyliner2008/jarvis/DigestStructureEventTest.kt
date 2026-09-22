package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.smc.MarketContextDigest
import com.skyliner2008.jarvis.automation.smc.UnifiedSmcSignals
import kotlin.test.Test
import kotlin.test.assertEquals

/** "ล่าสุด=" ในภาพ 5TF ต้องเป็นเหตุการณ์โครงสร้างที่ใหม่ที่สุดจริง (เดิมได้ CHoCH↑ ทุกครั้ง) */
class DigestStructureEventTest {
    private fun st(n: Int, chochUp: Set<Int> = emptySet(), chochDn: Set<Int> = emptySet(),
                   bosUp: Set<Int> = emptySet(), bosDn: Set<Int> = emptySet()) = UnifiedSmcSignals.Struct(
        IntArray(n), BooleanArray(n) { it in bosUp }, BooleanArray(n) { it in bosDn },
        BooleanArray(n) { it in chochUp }, BooleanArray(n) { it in chochDn },
        DoubleArray(n) { Double.NaN }, DoubleArray(n) { Double.NaN }
    )

    @Test
    fun noEventMeansDash() = assertEquals("–", MarketContextDigest.lastStructureEvent(st(50), 49))

    @Test
    fun newestEventWinsOverType() {
        // CHoCH↑ เมื่อ 8 แท่งก่อน แล้ว CHoCH↓ เมื่อ 3 แท่งก่อน → ต้องได้ CHoCH↓
        assertEquals("CHoCH↓ (3 แท่งก่อน)", MarketContextDigest.lastStructureEvent(st(50, chochUp = setOf(41), chochDn = setOf(46)), 49))
        assertEquals("BOS↑ (แท่งล่าสุด)", MarketContextDigest.lastStructureEvent(st(50, chochUp = setOf(45), bosUp = setOf(49)), 49))
    }

    @Test
    fun eventsOlderThanWindowAreIgnored() =
        assertEquals("–", MarketContextDigest.lastStructureEvent(st(50, chochUp = setOf(30)), 49))
}

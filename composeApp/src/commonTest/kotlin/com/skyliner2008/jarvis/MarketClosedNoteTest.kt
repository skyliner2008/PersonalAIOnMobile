package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.ui.screen.marketClosedNote
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A wake job set while its market is shut says so, with when scanning starts (Thai time) — not "รอเช็คครั้งแรก". */
class MarketClosedNoteTest {

    private fun at(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    @Test
    fun `gold at the weekend says when it opens`() {
        // Sat 2026-10-03 17:00 UTC (00:00 Sun Thai, as in the user's log) → opens Sun 18:00 New York = Mon 05:00 Thai
        assertEquals("⏸ ตลาดปิดสุดสัปดาห์ — เริ่มสแกนเมื่อเปิด จ. 05:00 น.", marketClosedNote("XAUUSD", at("2026-10-03T17:00:00Z")))
    }

    @Test
    fun `gold's daily break`() {
        // Tue 2026-10-06 21:30 UTC = 17:30 New York → opens 18:00 New York = Wed 05:00 Thai
        assertEquals("⏸ ตลาดพัก — เริ่มสแกนเมื่อเปิด พ. 05:00 น.", marketClosedNote("XAUUSD", at("2026-10-06T21:30:00Z")))
    }

    @Test
    fun `open markets and crypto have no note`() {
        assertNull(marketClosedNote("XAUUSD", at("2026-10-06T12:00:00Z")))
        assertNull(marketClosedNote("BTCUSDT", at("2026-10-03T17:00:00Z")))
    }
}

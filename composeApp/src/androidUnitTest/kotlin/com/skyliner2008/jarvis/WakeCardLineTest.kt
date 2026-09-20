package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.wake.AnticipationEngine
import com.skyliner2008.jarvis.service.AlertPresentationFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

/** บรรทัดเหตุการณ์บนการ์ด: ตัดรหัสปัจจัย/สถิติออก (ส่วนนั้นมีไว้ให้ AI อ่าน) แต่คงป้าย TF และอายุเหตุการณ์ */
class WakeCardLineTest {

    @Test
    fun stripsFactorIdAndPerTimeframeStats() {
        val events = listOf(
            "• [H1] EMA14 ตัด EMA60 ขึ้น (Golden Cross ระยะสั้น) → ชี้ BUY  (EMA_CROSS · สถิติ H1: n=12 avg +0.10R ไปตามทิศ 58%)",
            "• [M5] กวาดหลุด Low ของ session Asia (81,000.00) แล้วปิดกลับขึ้นมา → ชี้ BUY (เกิดเมื่อ 3 นาทีก่อน)  " +
                "(SESSION_LEVEL_SWEEP · สถิติ M5: ยังไม่มีสถิติ · ⬆️ สถิติบน TF นี้ดี)",
            "• [M15] แท่งแดงตัวใหญ่ 1.8×ATR → ชี้ SELL  (IMPULSE_CANDLE · สถิติ M15: n=40 avg -0.05R ไปตามทิศ 45% · ⬇️ สถิติบน TF นี้ไม่ดี)"
        ).joinToString("\n")

        assertEquals(
            listOf(
                "[H1] EMA14 ตัด EMA60 ขึ้น (Golden Cross ระยะสั้น) → ชี้ BUY",
                "[M5] กวาดหลุด Low ของ session Asia (81,000.00) แล้วปิดกลับขึ้นมา → ชี้ BUY (เกิดเมื่อ 3 นาทีก่อน)",
                "[M15] แท่งแดงตัวใหญ่ 1.8×ATR → ชี้ SELL"
            ),
            AlertPresentationFormatter.wakeEventLinesForUser(mapOf(AnticipationEngine.K_EVENTS to events))
        )
    }
}

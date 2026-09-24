package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.ui.graphics.Color

/**
 * EyeMetrics — ค่าคงที่กลางของการวาดดวงตา LOOI (สัดส่วนเทียบกับเส้นผ่านศูนย์กลางตา)
 *
 * เดิมค่าเหล่านี้กระจายและขัดกันหลายไฟล์ (blink floor 0.06/0.08/0.12/0.15, pupil travel 0.14/0.15, ...)
 * ทุก renderer และ [VirtualHeadKinematics] ต้องอ่านจากที่นี่ที่เดียว
 */
object EyeMetrics {
    /** ความสูงต่ำสุดของตาตอนหลับตา/กะพริบ (สัดส่วนของความสูงตาเต็ม) */
    const val BLINK_CLOSED = 0.06f

    /** ขีดล่างของการบีบ resting offset ตอนกะพริบ (กันเสี้ยวเงาหายหมดเมื่อตาแบน) */
    const val BLINK_OFFSET_FLOOR = 0.12f

    /** ตาขยิบถือว่า "ปิด" (วาดเป็นขีด) เมื่อ scaleY ต่ำกว่านี้ */
    const val WINK_CLOSED = 0.18f

    /** ความหนาของตาขีด (หลับ/ขยิบ/ครุ่นคิด) หน่วย dp */
    const val SLIT_THICKNESS_DP = 14f

    /** ระยะกลอกลูกตาสูงสุด (สัดส่วนของขนาดตา) */
    const val PUPIL_TRAVEL_X = 0.15f
    const val PUPIL_TRAVEL_Y = 0.11f

    /** ระยะเลื่อนเบ้าตาเมื่อหัวหัน/เงย (สัดส่วนของเส้นผ่านศูนย์กลางตา) */
    const val HEAD_SHIFT_X = 0.40f
    const val HEAD_SHIFT_Y = 0.30f

    /** มิติความลึกระหว่างลูกตากับเบ้าตาเมื่อหัวหัน */
    const val PARALLAX_X = 0.055f
    const val PARALLAX_Y = 0.038f

    /** ระยะเยื้องขณะพักของลูกตาเทียบเบ้าตา (ให้เห็นเสี้ยวเงาด้านล่าง-ขวา) */
    const val REST_OFFSET_X = 0.025f
    const val REST_OFFSET_Y = 0.065f

    /** ระดับปาก ต่ำกว่าแนวสายตา (สัดส่วนของความสูงตา) */
    const val MOUTH_Y = 0.75f

    /**
     * ค่าการขยับบางตัว (หายใจ, saccade) เดิมเขียนเป็นพิกเซลดิบ ซึ่งเล็ก/ใหญ่ต่างกันตามความหนาแน่นจอ
     * ตอนนี้ตีความเป็น dp คูณตัวนี้ — ได้ขนาดเท่าเดิมบนจอ ~2.75x และเท่ากันทุกจอ
     */
    const val RAW_PX_TO_DP = 0.36f

    /** สีเงาเบ้าตามาตรฐาน — ตรงกับ [getEyeDeepShadowColor] */
    val SHADOW_CYAN = Color(0xFF0A5C6B)
    val SHADOW_RED = Color(0xFF42000E)
}

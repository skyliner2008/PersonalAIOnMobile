package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * AvatarLayoutInfo — ค่ากลางโครงสร้างเรขาคณิตและทิศทางจอของ Pet Robot Avatar
 *
 * รวมการคำนวณ isLandscape, eyeDiameter, baseSpacing และพิกัดจุดสำคัญรอบใบหน้า
 * เพื่อให้ทุกเลเยอร์ (Background, Head, Props, Foreground) อ่านค่าเดียวกัน ไม่คำนวณซ้ำจนเพี้ยน
 */
@Immutable
data class AvatarLayoutInfo(
    val isLandscape: Boolean = false,
    val containerWidth: Float = 0f,
    val containerHeight: Float = 0f,
    val eyeDiameter: Float = 0f,
    val baseEyeW: Float = 0f,
    val baseEyeH: Float = 0f,
    val baseSpacing: Float = 0f,
    val cX: Float = 0f,
    val cY: Float = 0f,
    val foreheadY: Float = 0f,
    val mouthY: Float = 0f,
    val chinY: Float = 0f,
    val leftTempleX: Float = 0f,
    val rightTempleX: Float = 0f
)

/**
 * AvatarDetailLevel — ระดับความละเอียดของภาพพื้นหลังและพร็อพ
 * - SIMPLE: ใกล้เคียงชีท LOOI Moodset และเครื่องจริง (พื้นหลังดำสนิท OLED, พร็อพทรงไอคอนมินิมอล ไม่แสดงมือหุ่นยนต์/ควัน/เศษละออง)
 * - RICH: แสดงภาพเคลื่อนไหวเต็มรูปแบบ (ออร่าชีพจร, ละอองแสง, พร็อพพร้อมมือจับและแอนิเมชันเชิงลึก)
 */
enum class AvatarDetailLevel {
    SIMPLE,
    RICH
}

/**
 * CompositionLocal สำหรับส่งผ่าน AvatarDetailLevel ไปยังเลเยอร์ลูกทั้งหมด (ค่าเริ่มต้นคือ RICH)
 */
val LocalAvatarDetailLevel = compositionLocalOf {
    AvatarDetailLevel.RICH
}

/**
 * CompositionLocal สำหรับส่งผ่าน AvatarLayoutInfo ไปยังเลเยอร์ลูกทั้งหมด
 */
val LocalAvatarLayout = compositionLocalOf {
    AvatarLayoutInfo(isLandscape = false)
}

/**
 * คำนวณ AvatarLayoutInfo จากขนาดความกว้าง-ยาว และทิศทางจอ
 */
fun calculateAvatarLayout(
    width: Float,
    height: Float,
    isLandscapeOverride: Boolean? = null
): AvatarLayoutInfo {
    val isLandscape = isLandscapeOverride ?: (width > height)
    val cX = width * 0.5f

    // ตำแหน่งแนวตั้ง (Y Axis) ตามหลักการจุดตัด 9 ช่อง (Rule of Thirds):
    // แนวนอน (Landscape): เส้นระดับสายตาอยู่ที่ 42% ของความสูงจอ (สอดคล้องกับ RIVE_LANDSCAPE_EYE_LINE = 0.42f)
    // แนวตั้ง (Portrait): เส้นระดับสายตาอยู่ที่ 40% ของความสูงจอ (สอดคล้องกับ RIVE_PORTRAIT_EYE_LINE = 0.40f)
    // ทำให้ขอบบนของดวงตาอยู่ชิดแนวเส้น 1/3 (33.3%) พอดี และเหลือพื้นที่ด้านล่างสมดุลสำหรับปากและคาง
    val cY = if (isLandscape) height * 0.42f else height * 0.40f

    val eyeDiameter = if (isLandscape) {
        minOf(height * 0.50f, width * 0.28f)
    } else {
        minOf(width * 0.24f, height * 0.20f)
    }

    val baseEyeW = eyeDiameter
    val baseEyeH = eyeDiameter

    // ระยะห่างระหว่างดวงตา (ปรับเป็น 1.2 เท่าจากเดิม):
    // แนวนอน (Landscape): eyeDiameter * 0.72f (1.2x จากเดิม 0.60f)
    // แนวตั้ง (Portrait): width / 5f = 0.20f * width (1.2x จากเดิม width / 6f)
    val baseSpacing = if (isLandscape) {
        eyeDiameter * 0.72f
    } else {
        width / 5f
    }
    val foreheadY = cY - eyeDiameter * 0.65f
    val mouthY = cY + eyeDiameter * 0.58f
    val chinY = mouthY + eyeDiameter * 0.35f
    val leftTempleX = cX - baseSpacing - eyeDiameter * 0.55f
    val rightTempleX = cX + baseSpacing + eyeDiameter * 0.55f

    return AvatarLayoutInfo(
        isLandscape = isLandscape,
        containerWidth = width,
        containerHeight = height,
        eyeDiameter = eyeDiameter,
        baseEyeW = baseEyeW,
        baseEyeH = baseEyeH,
        baseSpacing = baseSpacing,
        cX = cX,
        cY = cY,
        foreheadY = foreheadY,
        mouthY = mouthY,
        chinY = chinY,
        leftTempleX = leftTempleX,
        rightTempleX = rightTempleX
    )
}

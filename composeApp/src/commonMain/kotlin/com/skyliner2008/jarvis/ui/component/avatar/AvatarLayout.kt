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
    val cY = height * 0.5f

    val eyeDiameter = if (isLandscape) {
        minOf(height * 0.52f, width * 0.28f)
    } else {
        minOf(width * 0.38f, height * 0.24f)
    }

    val baseEyeW = eyeDiameter
    val baseEyeH = eyeDiameter
    val baseSpacing = eyeDiameter * 0.65f
    val foreheadY = cY - eyeDiameter * 0.65f
    val mouthY = cY + eyeDiameter * 0.58f
    val chinY = mouthY + eyeDiameter * 0.35f
    val leftTempleX = cX - eyeDiameter * 0.85f
    val rightTempleX = cX + eyeDiameter * 0.85f

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

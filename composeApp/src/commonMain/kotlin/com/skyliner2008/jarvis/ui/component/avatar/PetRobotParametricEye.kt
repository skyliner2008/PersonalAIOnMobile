package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private const val PI_F = 3.1415927f

private inline fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * EyeShapeParams — พารามิเตอร์รูปทรงดวงตาแบบพาราเมตริกเต็มรูปแบบ (Full-Spectrum Parametric Eye Shape)
 *
 * แปลงทุกอารมณ์ให้เป็นตัวแปรต่อเนื่องชุดเดียว ทำให้สามารถมอร์ฟข้ามรูปทรง
 * จากวงกลม (Squircle) -> พระจันทร์เสี้ยว (Happy ⌒) -> โดมฐานเรียบ (Dome ⌒) -> ลิ่มเฉียง (Angry) -> ลูกศร (Chevron > <)
 * -> หัวใจ (Heart ♥) -> ดาวประกาย (Star ★) -> กากบาท (Cross X)
 * ได้อย่างนุ่มนวลไร้รอยต่อ 100% โดยไม่ต้องใช้การ crossfade ซ้อนภาพอีกต่อไป
 */
@Immutable
data class EyeShapeParams(
    val cornerRoundness: Float = 1f,   // 0 = เหลี่ยม, 1 = วงกลม/squircle เต็มที่
    val curvature: Float = 0f,         // 0 = ตรง, +1 = โค้งขึ้น (ยิ้ม ⌒), -1 = โค้งลง (เศร้า/หลับ)
    val slantDeg: Float = 0f,          // เอียงลิ่ม (โกรธ = ±16-24 deg)
    val chevronAmount: Float = 0f,     // 0 = เต็มรูป, 1 = ปลายแหลม > < (หัวเราะ/รังเกียจ)
    val openness: Float = 1f,          // 0 = หลับสนิท (แท่งบาง), 1 = ตาเปิดเต็มที่
    val widthRatio: Float = 1f,
    val heightRatio: Float = 1f,
    val heartAmount: Float = 0f,       // 0 = ปกติ, 1 = ทรงหัวใจ ♥
    val starAmount: Float = 0f,        // 0 = ปกติ, 1 = ทรงดาวประกาย 4 แฉก ★
    val crossAmount: Float = 0f,       // 0 = ปกติ, 1 = กากบาท X X
    val domeAmount: Float = 0f,        // 0 = ปกติ, 1 = ทรงโดมโค้งฐานเรียบ ⌒ (ตามรูปเครื่องจริง LOOI โหมด Love)
    val innerPinch: Float = 0f         // 0 = ปกติ, 1 = บีบขอบตาด้านใน
)

/**
 * แปลง AvatarEmotion เป็น EyeShapeParams ของข้างซ้ายหรือขวา
 * รองรับครบทุกอารมณ์แบบ 100% Full-Spectrum Parametric โดยไม่คืนค่า null อีกต่อไป
 */
fun AvatarEmotion.toEyeShapeParams(isLeft: Boolean): EyeShapeParams {
    return when (this) {
        AvatarEmotion.IDLE, AvatarEmotion.SPEAKING -> EyeShapeParams(
            cornerRoundness = 1.0f,
            curvature = 0.0f,
            slantDeg = 0.0f,
            chevronAmount = 0.0f,
            openness = 1.0f,
            widthRatio = 1.0f,
            heightRatio = 1.0f
        )
        AvatarEmotion.HAPPY -> EyeShapeParams(
            cornerRoundness = 0.8f,
            curvature = 1.0f,
            slantDeg = 0.0f,
            chevronAmount = 0.0f,
            openness = 0.72f,
            widthRatio = 1.05f,
            heightRatio = 0.85f
        )
        AvatarEmotion.COOKING, AvatarEmotion.PARTY -> EyeShapeParams(
            cornerRoundness = 0.8f,
            curvature = 0.95f,
            slantDeg = 0.0f,
            chevronAmount = 0.0f,
            openness = 0.75f,
            widthRatio = 1.05f,
            heightRatio = 0.85f
        )
        AvatarEmotion.ANGRY -> EyeShapeParams(
            cornerRoundness = 0.35f,
            curvature = 0.0f,
            slantDeg = 18.0f,
            chevronAmount = 0.0f,
            openness = 0.88f,
            widthRatio = 1.05f,
            heightRatio = 0.85f
        )
        AvatarEmotion.ENRAGED -> EyeShapeParams(
            cornerRoundness = 0.25f,
            curvature = 0.0f,
            slantDeg = 24.0f,
            chevronAmount = 0.0f,
            openness = 0.95f,
            widthRatio = 1.15f,
            heightRatio = 0.90f
        )
        AvatarEmotion.SLEEPING, AvatarEmotion.CAMERA_MODE -> EyeShapeParams(
            cornerRoundness = 1.0f,
            curvature = -0.05f,
            slantDeg = 0.0f,
            chevronAmount = 0.0f,
            openness = 0.15f,
            widthRatio = 0.95f,
            heightRatio = 0.20f
        )
        AvatarEmotion.BORED -> EyeShapeParams(
            cornerRoundness = 0.85f,
            curvature = -0.30f,
            slantDeg = -6.0f,
            chevronAmount = 0.0f,
            openness = 0.50f,
            widthRatio = 1.0f,
            heightRatio = 0.65f
        )
        AvatarEmotion.SAD -> EyeShapeParams(
            cornerRoundness = 0.85f,
            curvature = -0.85f,
            slantDeg = -12.0f,
            chevronAmount = 0.0f,
            openness = 0.65f,
            widthRatio = 1.0f,
            heightRatio = 0.75f
        )
        AvatarEmotion.CRYING -> EyeShapeParams(
            cornerRoundness = 0.85f,
            curvature = -0.95f,
            slantDeg = -14.0f,
            chevronAmount = 0.0f,
            openness = 0.60f,
            widthRatio = 1.0f,
            heightRatio = 0.70f
        )
        AvatarEmotion.SURPRISED -> EyeShapeParams(
            cornerRoundness = 1.0f,
            curvature = 0.0f,
            slantDeg = 0.0f,
            chevronAmount = 0.0f,
            openness = 1.25f,
            widthRatio = 1.15f,
            heightRatio = 1.25f
        )
        AvatarEmotion.LAUGHING, AvatarEmotion.DISGUSTED -> EyeShapeParams(
            cornerRoundness = 0.40f,
            curvature = 0.0f,
            slantDeg = 0.0f,
            chevronAmount = 1.0f,
            openness = 0.85f,
            widthRatio = 1.0f,
            heightRatio = 0.85f
        )
        AvatarEmotion.FOCUSED, AvatarEmotion.SNEAKY -> EyeShapeParams(
            cornerRoundness = 0.30f,
            curvature = 0.0f,
            slantDeg = 14.0f,
            chevronAmount = 0.0f,
            openness = 0.55f,
            widthRatio = 1.10f,
            heightRatio = 0.55f
        )
        AvatarEmotion.LISTENING -> EyeShapeParams(
            cornerRoundness = 1.0f,
            curvature = 0.0f,
            slantDeg = 0.0f,
            chevronAmount = 0.0f,
            openness = 1.10f,
            widthRatio = 1.05f,
            heightRatio = 1.10f
        )
        AvatarEmotion.THINKING -> if (isLeft) {
            EyeShapeParams(
                cornerRoundness = 1.0f,
                curvature = 0.0f,
                slantDeg = -4.0f,
                chevronAmount = 0.0f,
                openness = 0.80f,
                widthRatio = 0.80f,
                heightRatio = 0.75f
            )
        } else {
            EyeShapeParams(
                cornerRoundness = 1.0f,
                curvature = 0.0f,
                slantDeg = 4.0f,
                chevronAmount = 0.0f,
                openness = 1.05f,
                widthRatio = 1.05f,
                heightRatio = 1.05f
            )
        }
        AvatarEmotion.CONFUSED -> if (isLeft) {
            EyeShapeParams(
                cornerRoundness = 1.0f,
                curvature = 0.0f,
                slantDeg = 0.0f,
                chevronAmount = 0.0f,
                openness = 0.18f,
                widthRatio = 0.95f,
                heightRatio = 0.22f
            )
        } else {
            EyeShapeParams(
                cornerRoundness = 0.40f,
                curvature = 0.0f,
                slantDeg = 12.0f,
                chevronAmount = 0.0f,
                openness = 0.95f,
                widthRatio = 1.05f,
                heightRatio = 0.95f
            )
        }
        AvatarEmotion.WINK -> if (isLeft) {
            EyeShapeParams(
                cornerRoundness = 1.0f,
                curvature = 0.0f,
                slantDeg = 0.0f,
                chevronAmount = 0.0f,
                openness = 1.04f,
                widthRatio = 1.02f,
                heightRatio = 1.02f
            )
        } else {
            EyeShapeParams(
                cornerRoundness = 1.0f,
                curvature = 0.25f,
                slantDeg = 0.0f,
                chevronAmount = 0.0f,
                openness = 0.12f,
                widthRatio = 0.95f,
                heightRatio = 0.18f
            )
        }
        AvatarEmotion.SHY -> EyeShapeParams(
            cornerRoundness = 1.0f,
            curvature = 0.0f,
            slantDeg = 0.0f,
            chevronAmount = 0.0f,
            openness = 0.95f,
            widthRatio = 0.95f,
            heightRatio = 0.95f
        )
        AvatarEmotion.EATING, AvatarEmotion.DRINKING, AvatarEmotion.DIVING -> EyeShapeParams(
            cornerRoundness = 1.0f,
            curvature = 0.0f,
            slantDeg = 0.0f,
            chevronAmount = 0.0f,
            openness = 0.95f,
            widthRatio = 0.95f,
            heightRatio = 0.95f
        )
        AvatarEmotion.READING, AvatarEmotion.WORKING, AvatarEmotion.DETECTIVE, AvatarEmotion.ART_MODE,
        AvatarEmotion.SPACE, AvatarEmotion.EXHAUSTED, AvatarEmotion.SCIENTIST -> EyeShapeParams(
            cornerRoundness = 1.0f,
            curvature = 0.0f,
            slantDeg = 0.0f,
            chevronAmount = 0.0f,
            openness = 0.90f,
            widthRatio = 0.95f,
            heightRatio = 0.90f
        )
        AvatarEmotion.POUT -> EyeShapeParams(
            cornerRoundness = 0.85f,
            curvature = -0.35f,
            slantDeg = -8.0f,
            chevronAmount = 0.0f,
            openness = 0.60f,
            widthRatio = 1.0f,
            heightRatio = 0.70f
        )
        AvatarEmotion.MUSIC, AvatarEmotion.VR_MODE -> EyeShapeParams(
            cornerRoundness = 1.0f,
            curvature = 0.0f,
            slantDeg = 0.0f,
            chevronAmount = 0.0f,
            openness = 0.95f,
            widthRatio = 0.95f,
            heightRatio = 0.95f
        )
        // Authentic LOOI Love Mode (Dome Eye: ⌒ with flat bottom)
        AvatarEmotion.LOVE -> EyeShapeParams(
            cornerRoundness = 0.80f,
            domeAmount = 0.95f,
            curvature = 0.35f,
            openness = 0.92f,
            widthRatio = 1.05f,
            heightRatio = 0.95f
        )
        // Romantic Full Heart Eyes ♥
        AvatarEmotion.ROMANTIC -> EyeShapeParams(
            cornerRoundness = 0.80f,
            heartAmount = 1.0f,
            openness = 1.0f,
            widthRatio = 1.05f,
            heightRatio = 1.05f
        )
        // Dead Cross Eyes X X
        AvatarEmotion.DEAD -> EyeShapeParams(
            cornerRoundness = 0.30f,
            crossAmount = 1.0f,
            openness = 0.90f,
            widthRatio = 0.95f,
            heightRatio = 0.95f
        )
        // Excited 4-Point Sparkle Star Eyes ★
        AvatarEmotion.EXCITED -> EyeShapeParams(
            cornerRoundness = 0.35f,
            starAmount = 1.0f,
            openness = 1.05f,
            widthRatio = 1.05f,
            heightRatio = 1.05f
        )
        // Dizzy Cross/Spiral Eyes
        AvatarEmotion.DIZZY -> EyeShapeParams(
            cornerRoundness = 0.40f,
            crossAmount = 0.90f,
            openness = 0.85f,
            widthRatio = 0.92f,
            heightRatio = 0.92f
        )
        // Evil Slanted Eyes
        AvatarEmotion.EVIL -> EyeShapeParams(
            cornerRoundness = 0.30f,
            slantDeg = 20.0f,
            openness = 0.85f,
            widthRatio = 1.10f,
            heightRatio = 0.85f
        )
        // Puzzled / Confused Wave Eyes
        AvatarEmotion.PUZZLED -> if (isLeft) {
            EyeShapeParams(
                cornerRoundness = 0.70f,
                curvature = 0.25f,
                openness = 0.85f,
                widthRatio = 0.95f,
                heightRatio = 0.90f
            )
        } else {
            EyeShapeParams(
                cornerRoundness = 0.50f,
                curvature = -0.25f,
                slantDeg = 10.0f,
                openness = 0.90f,
                widthRatio = 1.05f,
                heightRatio = 0.95f
            )
        }
        // Sick Drooping Eyes
        AvatarEmotion.SICK -> EyeShapeParams(
            cornerRoundness = 0.85f,
            curvature = -0.60f,
            slantDeg = -10.0f,
            openness = 0.65f,
            widthRatio = 0.95f,
            heightRatio = 0.70f
        )
        // Rich Tall Squircle Eyes
        AvatarEmotion.RICH -> EyeShapeParams(
            cornerRoundness = 0.65f,
            openness = 1.05f,
            widthRatio = 0.95f,
            heightRatio = 1.05f
        )
        // Gaming / Traveling Attentive Squircle Eyes
        AvatarEmotion.GAMING, AvatarEmotion.TRAVELING -> EyeShapeParams(
            cornerRoundness = 0.95f,
            openness = 0.95f,
            widthRatio = 0.95f,
            heightRatio = 0.95f
        )
        // Cold Shivering Eyes
        AvatarEmotion.COLD -> EyeShapeParams(
            cornerRoundness = 0.80f,
            openness = 0.85f,
            widthRatio = 0.95f,
            heightRatio = 0.90f
        )
        // Hot Drooping Warm Eyes
        AvatarEmotion.HOT -> EyeShapeParams(
            cornerRoundness = 0.85f,
            curvature = -0.30f,
            slantDeg = -6.0f,
            openness = 0.65f,
            widthRatio = 1.0f,
            heightRatio = 0.75f
        )
        // Dreaming Sleepy Slit Eyes
        AvatarEmotion.DREAMING -> EyeShapeParams(
            cornerRoundness = 1.0f,
            curvature = -0.10f,
            openness = 0.18f,
            widthRatio = 0.95f,
            heightRatio = 0.22f
        )
        // Electric Sparkle Star Eyes
        AvatarEmotion.ELECTRIC -> EyeShapeParams(
            cornerRoundness = 0.35f,
            starAmount = 0.65f,
            openness = 0.90f,
            widthRatio = 1.0f,
            heightRatio = 0.90f
        )
        // Hero Confident Eyes
        AvatarEmotion.HERO -> EyeShapeParams(
            cornerRoundness = 0.75f,
            slantDeg = 8.0f,
            openness = 0.95f,
            widthRatio = 1.05f,
            heightRatio = 0.95f
        )
        // Glitched Digital Block Eyes
        AvatarEmotion.GLITCHED -> EyeShapeParams(
            cornerRoundness = 0.15f,
            openness = 0.70f,
            widthRatio = 1.15f,
            heightRatio = 0.65f
        )
        // Magic Sparkle Star Eyes
        AvatarEmotion.MAGIC -> EyeShapeParams(
            cornerRoundness = 0.60f,
            starAmount = 0.50f,
            openness = 0.95f,
            widthRatio = 1.0f,
            heightRatio = 0.95f
        )
        // Sporty Energetic Eyes
        AvatarEmotion.SPORTY -> EyeShapeParams(
            cornerRoundness = 0.60f,
            slantDeg = 10.0f,
            openness = 0.90f,
            widthRatio = 1.05f,
            heightRatio = 0.90f
        )
        // Scared Small Shivering Eyes
        AvatarEmotion.SCARED -> EyeShapeParams(
            cornerRoundness = 1.0f,
            openness = 0.60f,
            widthRatio = 0.60f,
            heightRatio = 0.60f
        )
        // Warrior Fierce Wedge Eyes
        AvatarEmotion.WARRIOR -> EyeShapeParams(
            cornerRoundness = 0.35f,
            slantDeg = 16.0f,
            openness = 0.85f,
            widthRatio = 1.05f,
            heightRatio = 0.85f
        )
        // Low Battery Tired Faint Slit Eyes
        AvatarEmotion.LOW_BATTERY -> EyeShapeParams(
            cornerRoundness = 0.90f,
            curvature = -0.20f,
            openness = 0.20f,
            widthRatio = 0.90f,
            heightRatio = 0.25f
        )
    }
}

/**
 * คำนวณแอนิเมชันสำหรับ EyeShapeParams อย่างต่อเนื่องด้วย timing 350ms FastOutSlowInEasing
 * ครอบคลุมพารามิเตอร์ครบทุกมิติ (Dome, Heart, Star, Cross, Pinch)
 */
@Composable
fun rememberAnimatedEyeShapeParams(
    targetParams: EyeShapeParams,
    label: String = "EyeShape"
): EyeShapeParams {
    val durationMs = 350
    val animSpec = tween<Float>(durationMs, easing = FastOutSlowInEasing)

    val cornerRoundness by animateFloatAsState(targetParams.cornerRoundness, animSpec, label = "${label}_roundness")
    val curvature by animateFloatAsState(targetParams.curvature, animSpec, label = "${label}_curvature")
    val slantDeg by animateFloatAsState(targetParams.slantDeg, animSpec, label = "${label}_slant")
    val chevronAmount by animateFloatAsState(targetParams.chevronAmount, animSpec, label = "${label}_chevron")
    val openness by animateFloatAsState(targetParams.openness, animSpec, label = "${label}_openness")
    val widthRatio by animateFloatAsState(targetParams.widthRatio, animSpec, label = "${label}_wRatio")
    val heightRatio by animateFloatAsState(targetParams.heightRatio, animSpec, label = "${label}_hRatio")
    val heartAmount by animateFloatAsState(targetParams.heartAmount, animSpec, label = "${label}_heart")
    val starAmount by animateFloatAsState(targetParams.starAmount, animSpec, label = "${label}_star")
    val crossAmount by animateFloatAsState(targetParams.crossAmount, animSpec, label = "${label}_cross")
    val domeAmount by animateFloatAsState(targetParams.domeAmount, animSpec, label = "${label}_dome")
    val innerPinch by animateFloatAsState(targetParams.innerPinch, animSpec, label = "${label}_pinch")

    return EyeShapeParams(
        cornerRoundness = cornerRoundness,
        curvature = curvature,
        slantDeg = slantDeg,
        chevronAmount = chevronAmount,
        openness = openness,
        widthRatio = widthRatio,
        heightRatio = heightRatio,
        heartAmount = heartAmount,
        starAmount = starAmount,
        crossAmount = crossAmount,
        domeAmount = domeAmount,
        innerPinch = innerPinch
    )
}

/**
 * สร้าง Path แบบพาราเมตริกเดี่ยวที่มอร์ฟรูปทรงตาได้อย่างต่อเนื่อง
 * รองรับการ interpolate ต่อเนื่องระหว่าง Squircle, Arch, Dome, Wedge, Chevron, Heart, Star และ Cross
 */
fun buildParametricEyePath(
    params: EyeShapeParams,
    centerX: Float,
    centerY: Float,
    baseW: Float,
    baseH: Float,
    isLeft: Boolean,
    gazeShiftX: Float = 0f,
    gazeShiftY: Float = 0f
): Path {
    val w = baseW * params.widthRatio
    val minThickness = 12f
    val h = maxOf(minThickness, baseH * params.heightRatio * params.openness)

    val hw = w * 0.5f
    val hh = h * 0.5f
    val cx = centerX + gazeShiftX
    val cy = centerY + gazeShiftY

    val pRound = params.cornerRoundness.coerceIn(0.05f, 1f)
    var r = minOf(hw, hh) * pRound

    val slantRad = params.slantDeg * (PI_F / 180f)
    val slantDelta = hw * sin(slantRad)
    // Left eye inner edge is right side (+); Right eye inner edge is left side (-)
    val innerDir = if (isLeft) 1f else -1f
    val topSlantL = -innerDir * slantDelta
    val topSlantR = innerDir * slantDelta

    val archTop = if (params.curvature > 0f) {
        -params.curvature * baseH * 0.52f
    } else {
        -params.curvature * baseH * 0.32f
    }
    val archBottom = if (params.curvature > 0f) {
        -params.curvature * (baseH * 0.52f - maxOf(14f, baseH * 0.24f * params.openness))
    } else {
        -params.curvature * baseH * 0.32f
    }

    val chev = params.chevronAmount.coerceIn(0f, 1f)
    val apexDir = if (isLeft) 1f else -1f

    // Morphing blend amounts
    val cross = params.crossAmount.coerceIn(0f, 1f)
    val star = params.starAmount.coerceIn(0f, 1f)
    val dome = params.domeAmount.coerceIn(0f, 1f)
    val heart = params.heartAmount.coerceIn(0f, 1f)
    val pinch = params.innerPinch.coerceIn(0f, 1f)

    if (cross > 0.05f || star > 0.05f) {
        r *= (1f - maxOf(cross, star) * 0.6f)
    }

    // Corner vertices (standard + chevron)
    var tlX = cx - hw + (if (apexDir < 0) hw * 0.25f * chev else -hw * 0.08f * chev)
    var tlY = cy - hh + topSlantL + (if (params.curvature > 0f) archTop * 0.20f else 0f)

    var trX = cx + hw - (if (apexDir > 0) hw * 0.25f * chev else -hw * 0.08f * chev)
    var trY = cy - hh + topSlantR + (if (params.curvature > 0f) archTop * 0.20f else 0f)

    var brX = cx + hw - (if (apexDir > 0) hw * 0.25f * chev else -hw * 0.08f * chev)
    var brY = cy + hh + (if (params.curvature > 0f) archBottom * 0.20f else 0f)

    var blX = cx - hw + (if (apexDir < 0) hw * 0.25f * chev else -hw * 0.08f * chev)
    var blY = cy + hh + (if (params.curvature > 0f) archBottom * 0.20f else 0f)

    // Side midpoint vertices
    var rMidX = if (chev > 0f) {
        if (apexDir > 0) cx + hw * (1f + 0.22f * chev) else cx + hw * (1f - 0.72f * chev)
    } else trX
    var rMidY = cy

    var lMidX = if (chev > 0f) {
        if (apexDir < 0) cx - hw * (1f + 0.22f * chev) else cx - hw * (1f - 0.72f * chev)
    } else tlX
    var lMidY = cy

    var topMidY = cy - hh + (topSlantL + topSlantR) * 0.5f + archTop
    var bottomMidY = cy + hh + archBottom

    // Star morphing: corners pinch inward, midpoints push outward
    if (star > 0.001f) {
        tlX = lerp(tlX, cx - hw * 0.24f, star)
        tlY = lerp(tlY, cy - hh * 0.24f, star)
        trX = lerp(trX, cx + hw * 0.24f, star)
        trY = lerp(trY, cy - hh * 0.24f, star)
        brX = lerp(brX, cx + hw * 0.24f, star)
        brY = lerp(brY, cy + hh * 0.24f, star)
        blX = lerp(blX, cx - hw * 0.24f, star)
        blY = lerp(blY, cy + hh * 0.24f, star)

        topMidY = lerp(topMidY, cy - hh * 1.18f, star)
        bottomMidY = lerp(bottomMidY, cy + hh * 1.18f, star)
        rMidX = lerp(rMidX, cx + hw * 1.18f, star)
        lMidX = lerp(lMidX, cx - hw * 1.18f, star)
    }

    // Cross morphing: midpoints pinch deeply inward towards center
    if (cross > 0.001f) {
        topMidY = lerp(topMidY, cy - hh * 0.14f, cross)
        bottomMidY = lerp(bottomMidY, cy + hh * 0.14f, cross)
        rMidX = lerp(rMidX, cx + hw * 0.14f, cross)
        lMidX = lerp(lMidX, cx - hw * 0.14f, cross)
    }

    // Dome morphing: top curves higher and rounds smoothly, bottom flattens
    if (dome > 0.001f) {
        topMidY = lerp(topMidY, cy - hh * 1.05f, dome)
        tlY = lerp(tlY, cy - hh * 0.45f, dome)
        trY = lerp(trY, cy - hh * 0.45f, dome)
        bottomMidY = lerp(bottomMidY, cy + hh, dome)
        blY = lerp(blY, cy + hh, dome)
        brY = lerp(brY, cy + hh, dome)
    }

    // Heart morphing: top cleft dips, bottom center tapers to point
    if (heart > 0.001f) {
        topMidY = lerp(topMidY, cy - hh * 0.18f, heart)
        bottomMidY = lerp(bottomMidY, cy + hh * 1.12f, heart)
        rMidX = lerp(rMidX, cx + hw * 1.08f, heart)
        lMidX = lerp(lMidX, cx - hw * 1.08f, heart)
        rMidY = lerp(rMidY, cy - hh * 0.15f, heart)
        lMidY = lerp(lMidY, cy - hh * 0.15f, heart)
        blX = lerp(blX, cx - hw * 0.28f, heart)
        brX = lerp(brX, cx + hw * 0.28f, heart)
        blY = lerp(blY, cy + hh * 0.85f, heart)
        brY = lerp(brY, cy + hh * 0.85f, heart)
    }

    // Inner pinch
    if (pinch > 0.001f) {
        if (isLeft) {
            rMidX -= hw * 0.35f * pinch
        } else {
            lMidX += hw * 0.35f * pinch
        }
    }

    return Path().apply {
        moveTo(tlX + r, tlY)

        // Top edge
        if (heart > 0.05f) {
            val leftLobeTopY = cy - hh * 1.05f
            val rightLobeTopY = cy - hh * 1.05f
            cubicTo(tlX, leftLobeTopY, cx - hw * 0.22f, leftLobeTopY, cx, topMidY)
            cubicTo(cx + hw * 0.22f, rightLobeTopY, trX, rightLobeTopY, trX - r, trY)
        } else if (star > 0.05f || cross > 0.05f) {
            lineTo(cx, topMidY)
            lineTo(trX - r, trY)
        } else {
            cubicTo(
                cx - hw * 0.45f, topMidY,
                cx + hw * 0.45f, topMidY,
                trX - r, trY
            )
        }

        // Top-Right corner / right edge
        if (chev > 0.05f && apexDir > 0) {
            lineTo(trX, trY)
            lineTo(rMidX, rMidY)
            lineTo(brX, brY)
        } else if (star > 0.05f || cross > 0.05f) {
            lineTo(trX, trY)
            lineTo(rMidX, rMidY)
            lineTo(brX, brY)
        } else {
            cubicTo(
                trX, trY,
                trX, trY + r,
                trX, trY + r
            )
            if (chev > 0.05f && apexDir < 0) {
                lineTo(rMidX, rMidY)
            }
            lineTo(brX, brY - r)
            cubicTo(
                brX, brY,
                brX - r, brY,
                brX - r, brY
            )
        }

        // Bottom edge
        if (heart > 0.05f) {
            cubicTo(brX, cy + hh * 0.85f, cx + hw * 0.22f, bottomMidY, cx, bottomMidY)
            cubicTo(cx - hw * 0.22f, bottomMidY, blX, cy + hh * 0.85f, blX + r, blY)
        } else if (star > 0.05f || cross > 0.05f) {
            lineTo(cx, bottomMidY)
            lineTo(blX + r, blY)
        } else {
            cubicTo(
                cx + hw * 0.45f, bottomMidY,
                cx - hw * 0.45f, bottomMidY,
                blX + r, blY
            )
        }

        // Bottom-Left corner / left edge
        if (chev > 0.05f && apexDir < 0) {
            lineTo(blX, blY)
            lineTo(lMidX, lMidY)
            lineTo(tlX, tlY)
        } else if (star > 0.05f || cross > 0.05f) {
            lineTo(blX, blY)
            lineTo(lMidX, lMidY)
            lineTo(tlX, tlY)
        } else {
            cubicTo(
                blX, blY,
                blX, blY - r,
                blX, blY - r
            )
            if (chev > 0.05f && apexDir > 0) {
                lineTo(lMidX, lMidY)
            }
            lineTo(tlX, tlY + r)
            cubicTo(
                tlX, tlY,
                tlX + r, tlY,
                tlX + r, tlY
            )
        }

        close()
    }
}

/**
 * วาดดวงตาพาราเมตริกด้วยโครงสร้าง 2 เลเยอร์ซ้อนเยื้องกัน (Authentic LOOI Dual-Layer Offset Engine)
 * ตรงตามต้นฉบับหุ่นยนต์ LOOI Robot:
 * 1. Base Layer (เลเยอร์ฐานด้านล่าง): สีย้อนแสงลึก/เงาลึก (เช่น Deep Royal Blue #0D25B9 สำหรับ Cyan)
 *    เยื้องลงและขวาตามการมอง (Offset X, Y) เผยให้เห็นขอบเงาเสี้ยวลึกด้านล่าง-ขวา
 * 2. Front Glow Layer (เลเยอร์หน้า): สีนีออนสว่างสดใส Solid Fill พร้อมรัศมีเรืองแสง (Bloom Halo) รอบนอก
 *    สะอาดตา ทรงพลังบนพื้นหลัง OLED ดำสนิท ไร้จุดสะท้อนแสงขาวปลอม
 */
fun DrawScope.drawParametricEye(
    color: Color,
    path: Path,
    shadowColor: Color? = null,
    shadowOffsetX: Float = 2.5f,
    shadowOffsetY: Float = 4.5f,
    glowRadiusDp: Dp = 6.dp,
    glowAlpha: Float = 0.30f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return

    val effShadowColor = (shadowColor ?: getEyeDeepShadowColor(color)).copy(
        alpha = (0.98f * effAlpha).coerceIn(0f, 1f)
    )

    // 1. Base Layer (เลเยอร์ฐานเงาลึกด้านล่าง เคลื่อนไหวเยื้องกันสร้างมิติชัดเจน)
    if (shadowOffsetX != 0f || shadowOffsetY != 0f) {
        withTransform({
            translate(shadowOffsetX, shadowOffsetY)
        }) {
            drawPath(path, effShadowColor)
        }
    }

    val glowPad = glowRadiusDp.toPx()
    val glowColor = color.copy(alpha = (glowAlpha * effAlpha).coerceIn(0f, 1f))

    // 2. Outer Glow Layer (ออร่าเรืองแสงละมุนแบบ OLED Bloom รอบขอบเลเยอร์หน้า)
    if (glowPad > 0f) {
        drawPath(
            path = path,
            color = glowColor.copy(alpha = glowColor.alpha * 0.35f),
            style = Stroke(width = glowPad * 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = path,
            color = glowColor,
            style = Stroke(width = glowPad * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // 3. Front Core Layer (เลเยอร์หน้านีออนแท้ สีสดใส คมชัด ไร้เกรเดียนต์และจุดขาวปลอม)
    drawPath(
        path = path,
        color = color.copy(alpha = 0.98f * effAlpha)
    )
}


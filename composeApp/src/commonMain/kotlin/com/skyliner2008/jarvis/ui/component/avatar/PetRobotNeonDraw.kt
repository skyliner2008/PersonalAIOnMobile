package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * ═════════════════════════════════════════════════════════════════════════════
 * LOOI ROBOT: FLAT NEON-GLOW 2-LAYER DRAWING ENGINE
 * ═════════════════════════════════════════════════════════════════════════════
 *
 * Designed to faithfully reproduce the "Looi Robot: Neon Cyan Style Moodset":
 * - Pure OLED Black background (#000000)
 * - 2-Layer Neon Rendering:
 *     1. Glow Layer: Outer blurred bloom (15-20% larger, alpha ~0.4-0.6, accentColor)
 *     2. Core Layer: Solid crisp shape (alpha ~0.98, strokeCap = Round for lines, no gradient)
 * - Zero radial gradient / Zero specular highlight dot (flat neon-glow icon + motion)
 * - Accent colors:
 *     - Cyan (default): Color(0xFF00F5FF)
 *     - Red (Angry / Evil / Enraged): Color(0xFFFF3B5C)
 *     - Pink (Love / Romantic / Shy): Color(0xFFFF4081)
 *     - Yellow / Gold (Star / Excited / Magic): Color(0xFFFFD700)
 *     - Mint (Sick / Poison): Color(0xFF64FFDA)
 *     - Grey Blue (Bored / Sleepy): Color(0xFF90A4AE)
 */

val NeonCyan = Color(0xFF00F5FF)
val NeonRed = Color(0xFFFF3B5C)
val NeonPink = Color(0xFFFF4081)
val NeonGold = Color(0xFFFFD700)
val NeonMint = Color(0xFF64FFDA)
val NeonGrey = Color(0xFF90A4AE)

// ─── 1. Reusable 2-Layer Neon Drawing Functions ──────────────────────────────

/**
 * วาด RoundRect สไตล์ Neon 2 ชั้น (Glow layer + Core layer)
 */
fun DrawScope.drawNeonRoundRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    cornerRadius: CornerRadius,
    glowRadiusDp: Dp = 10.dp,
    glowAlpha: Float = 0.5f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f || size.width <= 0f || size.height <= 0f) return
    val glowPad = glowRadiusDp.toPx()
    val glowColor = color.copy(alpha = (glowAlpha * effAlpha).coerceIn(0f, 1f))

    // 1. Glow Layer: Outer diffuse halo + main glow (~15-20% larger than core)
    val outerPad = glowPad * 1.4f
    drawRoundRect(
        color = glowColor.copy(alpha = glowColor.alpha * 0.35f),
        topLeft = Offset(topLeft.x - outerPad, topLeft.y - outerPad),
        size = Size(size.width + outerPad * 2f, size.height + outerPad * 2f),
        cornerRadius = CornerRadius(cornerRadius.x + outerPad, cornerRadius.y + outerPad)
    )
    drawRoundRect(
        color = glowColor,
        topLeft = Offset(topLeft.x - glowPad, topLeft.y - glowPad),
        size = Size(size.width + glowPad * 2f, size.height + glowPad * 2f),
        cornerRadius = CornerRadius(cornerRadius.x + glowPad, cornerRadius.y + glowPad)
    )

    // 2. Core Layer: Crisp solid fill (no gradient, solid neon)
    drawRoundRect(
        color = color.copy(alpha = 0.98f * effAlpha),
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius
    )
}

/**
 * Soft OLED bloom halo (radial falloff) behind a glowing eye — matches the diffuse
 * neon glow of the LOOI 40 Moodset sheet, instead of hard concentric stroke rings.
 * @param spread how far the halo extends beyond the shape, as a fraction of its radius
 */
fun DrawScope.drawSoftBloom(
    color: Color,
    center: Offset,
    size: Size,
    spread: Float = 0.55f,
    intensity: Float = 0.30f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha * intensity).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f || size.width <= 0f || size.height <= 0f) return
    val baseR = maxOf(size.width, size.height) / 2f
    val haloR = baseR * (1f + spread)
    val coreStop = (baseR / haloR).coerceIn(0.05f, 0.95f)
    val opaque = color.copy(alpha = 1f)
    val brush = Brush.radialGradient(
        colorStops = arrayOf(
            0f to opaque.copy(alpha = effAlpha),
            coreStop to opaque.copy(alpha = effAlpha * 0.55f),
            (coreStop + (1f - coreStop) * 0.35f) to opaque.copy(alpha = effAlpha * 0.14f),
            1f to Color.Transparent
        ),
        center = center,
        radius = haloR
    )
    // squashed (blinking) eyes get a squashed halo so the glow hugs the shape
    val sx = size.width / (baseR * 2f)
    val sy = size.height / (baseR * 2f)
    scale(scaleX = sx.coerceAtLeast(0.3f), scaleY = sy.coerceAtLeast(0.3f), pivot = center) {
        drawCircle(brush = brush, radius = haloR, center = center)
    }
}

/**
 * คำนวณสีเงาเข้ม (Deep Contrast Shadow Color) สำหรับมิติทรงกลม 2.5D ของดวงตา
 * อิงตามสีน้ำเงินครามเข้ม/Indigo ในภาพผลิตภัณฑ์จริง LOOI Robot (โหมด Normal / Idle)
 */
fun getEyeDeepShadowColor(baseColor: Color): Color {
    val r = baseColor.red
    val g = baseColor.green
    val b = baseColor.blue

    return when {
        // Cyan / Sky Blue (Default / Happy / Reading) -> Deep Teal lower rim (LOOI 40 Moodset sheet #0A5C6B)
        b > 0.7f && g > 0.6f && r < 0.4f -> Color(0xFF0A5C6B)
        // Red / Crimson (Angry / Enraged / Evil) -> Deep Burgundy / Dark Maroon
        r > 0.7f && g < 0.4f && b < 0.5f -> Color(0xFF42000E)
        // Pink (Love / Romantic / Shy) -> Deep Plum / Violet
        r > 0.7f && b > 0.4f && g < 0.5f -> Color(0xFF450624)
        // Yellow / Gold (Excited / Star) -> Deep Amber Bronze
        r > 0.7f && g > 0.6f && b < 0.3f -> Color(0xFF522800)
        // Mint / Green (Sick / Poison / Speaking) -> Deep Dark Teal / Forest Green
        g > 0.7f && r < 0.5f -> Color(0xFF003322)
        // Grey / Blue-Grey (Bored / Sleepy) -> Deep Slate
        else -> Color(0xFF141E28)
    }
}

/**
 * คำนวณสีไฮไลท์อ่อน (Top-Left Highlight Tint) สำหรับดวงตา
 */
fun getEyeHighlightColor(baseColor: Color): Color {
    return Color(
        red = (baseColor.red * 0.45f + 0.55f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.45f + 0.55f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.45f + 0.55f).coerceIn(0f, 1f),
        alpha = baseColor.alpha
    )
}

/**
 * วาดดวงตา RoundRect สไตล์ LOOI 2.5D (Glow Layer + 2.5D Spherical Gradient Core + Crescent Shadow + Specular Glint)
 */
fun DrawScope.drawNeonEyeRoundRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    cornerRadius: CornerRadius,
    glowRadiusDp: Dp = 10.dp,
    glowAlpha: Float = 0.52f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f || size.width <= 0f || size.height <= 0f) return
    val glowPad = glowRadiusDp.toPx()
    val glowColor = color.copy(alpha = (glowAlpha * effAlpha).coerceIn(0f, 1f))

    // 1. Glow Layer: Outer diffuse halo + main glow (~15-20% larger than core)
    val outerPad = glowPad * 1.4f
    drawRoundRect(
        color = glowColor.copy(alpha = glowColor.alpha * 0.35f),
        topLeft = Offset(topLeft.x - outerPad, topLeft.y - outerPad),
        size = Size(size.width + outerPad * 2f, size.height + outerPad * 2f),
        cornerRadius = CornerRadius(cornerRadius.x + outerPad, cornerRadius.y + outerPad)
    )
    drawRoundRect(
        color = glowColor,
        topLeft = Offset(topLeft.x - glowPad, topLeft.y - glowPad),
        size = Size(size.width + glowPad * 2f, size.height + glowPad * 2f),
        cornerRadius = CornerRadius(cornerRadius.x + glowPad, cornerRadius.y + glowPad)
    )

    // 2. Core Layer with 2.5D Spherical Gradient (Light Top-Left -> Base Color -> Deep Shadow Bottom-Right)
    val highlightColor = getEyeHighlightColor(color)
    val deepShadowColor = getEyeDeepShadowColor(color)
    val lightCenter = Offset(topLeft.x + size.width * 0.32f, topLeft.y + size.height * 0.28f)
    val maxDim = maxOf(size.width, size.height)

    val coreBrush = Brush.radialGradient(
        colorStops = arrayOf(
            0.00f to highlightColor.copy(alpha = 0.98f * effAlpha),
            0.35f to color.copy(alpha = 0.98f * effAlpha),
            0.70f to color.copy(alpha = 0.98f * effAlpha),
            1.00f to deepShadowColor.copy(alpha = 0.98f * effAlpha)
        ),
        center = lightCenter,
        radius = maxDim * 0.88f
    )
    drawRoundRect(
        brush = coreBrush,
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius
    )

    // 3. Crescent Shadow Arc on Bottom-Right (Deep indigo crescent as seen in LOOI product photo)
    val crescentBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color.Transparent,
            0.65f to Color.Transparent,
            1.0f to deepShadowColor.copy(alpha = 0.70f * effAlpha)
        ),
        startY = topLeft.y,
        endY = topLeft.y + size.height
    )
    drawRoundRect(
        brush = crescentBrush,
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius
    )

    // 4. Subtle Specular Highlight Glint at Top-Left
    val specW = size.width * 0.26f
    val specH = size.height * 0.16f
    val specTopLeft = Offset(topLeft.x + size.width * 0.18f, topLeft.y + size.height * 0.14f)
    drawOval(
        color = Color.White.copy(alpha = 0.22f * effAlpha),
        topLeft = specTopLeft,
        size = Size(specW, specH)
    )
}

/**
 * วาด Circle สไตล์ Neon 2 ชั้น (Glow layer + Core layer)
 */
fun DrawScope.drawNeonCircle(
    color: Color,
    center: Offset,
    radius: Float,
    glowRadiusDp: Dp = 8.dp,
    glowAlpha: Float = 0.5f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f || radius <= 0f) return
    val glowPad = glowRadiusDp.toPx()
    val glowColor = color.copy(alpha = (glowAlpha * effAlpha).coerceIn(0f, 1f))

    // Glow Layer
    val outerPad = glowPad * 1.4f
    drawCircle(
        color = glowColor.copy(alpha = glowColor.alpha * 0.35f),
        radius = radius + outerPad,
        center = center
    )
    drawCircle(
        color = glowColor,
        radius = radius + glowPad,
        center = center
    )

    // Core Layer
    drawCircle(
        color = color.copy(alpha = 0.98f * effAlpha),
        radius = radius,
        center = center
    )
}

/**
 * วาด Path/Stroke สไตล์ Neon 2 ชั้น (Glow layer + Core layer)
 */
fun DrawScope.drawNeonPath(
    path: Path,
    color: Color,
    strokeWidth: Float,
    glowRadiusDp: Dp = 8.dp,
    glowAlpha: Float = 0.5f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val glowPad = glowRadiusDp.toPx()
    val glowColor = color.copy(alpha = (glowAlpha * effAlpha).coerceIn(0f, 1f))

    // Glow Layer (Wider stroke with Round caps)
    drawPath(
        path = path,
        color = glowColor.copy(alpha = glowColor.alpha * 0.35f),
        style = Stroke(width = strokeWidth + glowPad * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = path,
        color = glowColor,
        style = Stroke(width = strokeWidth + glowPad, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Core Layer (Solid crisp stroke, strokeCap = Round)
    drawPath(
        path = path,
        color = color.copy(alpha = 0.98f * effAlpha),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * วาด Filled Path สไตล์ Neon 2 ชั้น (Glow layer + Core layer)
 */
fun DrawScope.drawNeonFilledPath(
    path: Path,
    color: Color,
    glowRadiusDp: Dp = 8.dp,
    glowAlpha: Float = 0.5f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val glowPad = glowRadiusDp.toPx()
    val glowColor = color.copy(alpha = (glowAlpha * effAlpha).coerceIn(0f, 1f))

    // Glow Layer (Expanded stroke border around the path)
    drawPath(
        path = path,
        color = glowColor.copy(alpha = glowColor.alpha * 0.35f),
        style = Stroke(width = glowPad * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = path,
        color = glowColor,
        style = Stroke(width = glowPad, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Core Layer (Solid fill)
    drawPath(
        path = path,
        color = color.copy(alpha = 0.98f * effAlpha)
    )
}

/**
 * วาด Arc สไตล์ Neon 2 ชั้น
 */
fun DrawScope.drawNeonArc(
    color: Color,
    startAngle: Float,
    sweepAngle: Float,
    topLeft: Offset,
    size: Size,
    strokeWidth: Float,
    glowRadiusDp: Dp = 8.dp,
    glowAlpha: Float = 0.5f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val glowPad = glowRadiusDp.toPx()
    val glowColor = color.copy(alpha = (glowAlpha * effAlpha).coerceIn(0f, 1f))

    // Glow Layer
    drawArc(
        color = glowColor.copy(alpha = glowColor.alpha * 0.35f),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(topLeft.x - glowPad * 0.5f, topLeft.y - glowPad * 0.5f),
        size = Size(size.width + glowPad, size.height + glowPad),
        style = Stroke(width = strokeWidth + glowPad * 1.5f, cap = StrokeCap.Round)
    )
    drawArc(
        color = glowColor,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth + glowPad, cap = StrokeCap.Round)
    )

    // Core Layer
    drawArc(
        color = color.copy(alpha = 0.98f * effAlpha),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

/**
 * วาด Line สไตล์ Neon 2 ชั้น
 */
fun DrawScope.drawNeonLine(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float,
    glowRadiusDp: Dp = 8.dp,
    glowAlpha: Float = 0.5f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val glowPad = glowRadiusDp.toPx()
    val glowColor = color.copy(alpha = (glowAlpha * effAlpha).coerceIn(0f, 1f))

    // Glow Layer
    drawLine(
        color = glowColor,
        start = start,
        end = end,
        strokeWidth = strokeWidth + glowPad,
        cap = StrokeCap.Round
    )

    // Core Layer
    drawLine(
        color = color.copy(alpha = 0.98f * effAlpha),
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

// ─── 2. Props: Heart, Tear, Sparkle, Swirl, Zzz, Sweat ───────────────────────

/**
 * วาด Neon Heart 2 ชั้น (Love / Romantic)
 */
fun DrawScope.drawNeonHeart(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float,
    glowRadiusDp: Dp = 8.dp,
    alpha: Float = 1f
) {
    if (alpha <= 0.005f || size <= 0f) return
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    val effColor = color.copy(alpha = effAlpha)
    val glowColor = effColor.copy(alpha = 0.5f * effAlpha)

    fun createHeartPath(scale: Float): Path = Path().apply {
        val w = size * scale
        val h = size * 0.95f * scale
        moveTo(centerX, centerY + h * 0.38f)
        cubicTo(
            centerX - w * 0.55f, centerY - h * 0.2f,
            centerX - w * 0.55f, centerY - h * 0.65f,
            centerX, centerY - h * 0.25f
        )
        cubicTo(
            centerX + w * 0.55f, centerY - h * 0.65f,
            centerX + w * 0.55f, centerY - h * 0.2f,
            centerX, centerY + h * 0.38f
        )
        close()
    }

    // Glow Layer (Expanded)
    drawPath(
        path = createHeartPath(1.18f),
        color = glowColor
    )
    // Core Layer (Crisp Fill)
    drawPath(
        path = createHeartPath(1.0f),
        color = effColor
    )
}

/**
 * วาด Neon Teardrop / Sweatdrop 2 ชั้น (Crying / Hot / Sweat)
 */
fun DrawScope.drawNeonTeardrop(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float,
    glowRadiusDp: Dp = 6.dp,
    alpha: Float = 1f
) {
    if (alpha <= 0.005f || size <= 0f) return
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    val effColor = color.copy(alpha = effAlpha)
    val glowColor = effColor.copy(alpha = 0.5f * effAlpha)

    fun createDropPath(scale: Float): Path = Path().apply {
        val s = size * scale
        moveTo(centerX, centerY - s * 0.65f)
        cubicTo(centerX - s * 0.55f, centerY, centerX - s * 0.55f, centerY + s * 0.55f, centerX, centerY + s * 0.55f)
        cubicTo(centerX + s * 0.55f, centerY + s * 0.55f, centerX + s * 0.55f, centerY, centerX, centerY - s * 0.65f)
        close()
    }

    // Glow Layer
    drawPath(createDropPath(1.2f), glowColor)
    // Core Layer
    drawPath(createDropPath(1.0f), effColor)
}

/**
 * วาด Neon 4-Point Star Sparkle 2 ชั้น (Excited / Magic)
 */
fun DrawScope.drawNeonSparkle(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float,
    rotationDeg: Float = 0f,
    glowRadiusDp: Dp = 6.dp,
    alpha: Float = 1f
) {
    if (alpha <= 0.005f || size <= 0f) return
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    val effColor = color.copy(alpha = effAlpha)
    val glowColor = effColor.copy(alpha = 0.5f * effAlpha)

    rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
        fun createSparklePath(scale: Float): Path = Path().apply {
            val s = size * scale
            val rOuter = s * 0.5f
            val rInner = s * 0.12f
            moveTo(centerX, centerY - rOuter)
            cubicTo(centerX + rInner, centerY - rInner, centerX + rInner, centerY - rInner, centerX + rOuter, centerY)
            cubicTo(centerX + rInner, centerY + rInner, centerX + rInner, centerY + rInner, centerX, centerY + rOuter)
            cubicTo(centerX - rInner, centerY + rInner, centerX - rInner, centerY + rInner, centerX - rOuter, centerY)
            cubicTo(centerX - rInner, centerY - rInner, centerX - rInner, centerY - rInner, centerX, centerY - rOuter)
            close()
        }
        // Glow Layer
        drawPath(createSparklePath(1.22f), glowColor)
        // Core Layer
        drawPath(createSparklePath(1.0f), effColor)
    }
}

/**
 * วาด Neon Z Letter 2 ชั้น (Sleepy / Dreaming)
 */
fun DrawScope.drawNeonZzz(
    color: Color,
    x: Float,
    y: Float,
    size: Float,
    strokeWidth: Float = 4.dp.toPx(),
    glowRadiusDp: Dp = 6.dp,
    alpha: Float = 1f
) {
    if (alpha <= 0.005f || size <= 0f) return
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    val effColor = color.copy(alpha = effAlpha)

    val zPath = Path().apply {
        moveTo(x - size / 2f, y - size / 2f)
        lineTo(x + size / 2f, y - size / 2f)
        lineTo(x - size / 2f, y + size / 2f)
        lineTo(x + size / 2f, y + size / 2f)
    }

    drawNeonPath(
        path = zPath,
        color = effColor,
        strokeWidth = strokeWidth,
        glowRadiusDp = glowRadiusDp,
        glowAlpha = 0.5f,
        alpha = effAlpha
    )
}

/**
 * วาด Neon Hypnotic Swirl Spiral 2 ชั้น (Sick / Dizzy)
 */
fun DrawScope.drawNeonSwirl(
    color: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    rotationDeg: Float,
    strokeWidth: Float = 5.dp.toPx(),
    glowRadiusDp: Dp = 7.dp,
    alpha: Float = 1f
) {
    if (alpha <= 0.005f || radius <= 0f) return
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    val effColor = color.copy(alpha = effAlpha)

    rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
        for (i in 1..3) {
            val r = radius * (i / 3.2f)
            val arcTopLeft = Offset(centerX - r, centerY - r)
            val arcSize = Size(r * 2f, r * 2f)
            drawNeonArc(
                color = effColor,
                startAngle = i * 65f,
                sweepAngle = 230f,
                topLeft = arcTopLeft,
                size = arcSize,
                strokeWidth = strokeWidth,
                glowRadiusDp = glowRadiusDp,
                glowAlpha = 0.5f,
                alpha = effAlpha
            )
        }
    }
}

// ─── 3. Particle Motion Layer State ──────────────────────────────────────────

/**
 * State สำหรับควบคุมการเคลื่อนไหวแบบอิสระของพร็อพ (Particle Motion Layer)
 * พร็อพฝั่งซ้ายและขวาจะใช้ Phase และ Duration ต่างกันเล็กน้อย เพื่อให้การเคลื่อนไหวเป็นธรรมชาติ
 */
data class PetParticleMotionState(
    // Love: floating mini hearts
    val heartFloatLeft: Float = 0f,
    val heartFloatRight: Float = 0f,
    val heartPulseLeft: Float = 1f,
    val heartPulseRight: Float = 1f,
    // Crying: dropping tears
    val tearFallLeft: Float = 0f,
    val tearFallRight: Float = 0f,
    // Sparkle: excited/magic stars
    val sparkleRotateLeft: Float = 0f,
    val sparkleRotateRight: Float = 0f,
    val sparkleScaleLeft: Float = 1f,
    val sparkleScaleRight: Float = 1f,
    // Swirl: sick/dizzy hypnotic rotation
    val swirlRotateLeft: Float = 0f,
    val swirlRotateRight: Float = 0f,
    // Sleepy: drifting Zzz
    val zzzRise1: Float = 0f,
    val zzzRise2: Float = 0f,
    // Hot/Exhausted: dripping sweat drops
    val sweatDripLeft: Float = 0f,
    val sweatDripRight: Float = 0f
)

/**
 * Composable สร้าง particle transitions อิสระซ้าย-ขวา
 */
@Composable
fun rememberPetParticleMotionState(): PetParticleMotionState {
    val transLeft = rememberInfiniteTransition(label = "pet_particles_left")
    val transRight = rememberInfiniteTransition(label = "pet_particles_right")

    // 1. Love hearts
    val heartFloatLeft by transLeft.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "heart_fl_l"
    )
    val heartFloatRight by transRight.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2150, easing = LinearEasing), RepeatMode.Restart),
        label = "heart_fl_r"
    )
    val heartPulseLeft by transLeft.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(750, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "heart_p_l"
    )
    val heartPulseRight by transRight.animateFloat(
        initialValue = 0.90f, targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(880, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "heart_p_r"
    )

    // 2. Tears
    val tearFallLeft by transLeft.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1350, easing = EaseInQuad), RepeatMode.Restart),
        label = "tear_f_l"
    )
    val tearFallRight by transRight.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1550, easing = EaseInQuad), RepeatMode.Restart),
        label = "tear_f_r"
    )

    // 3. Sparkles
    val sparkleRotateLeft by transLeft.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "spark_r_l"
    )
    val sparkleRotateRight by transRight.animateFloat(
        initialValue = 0f, targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(2700, easing = LinearEasing), RepeatMode.Restart),
        label = "spark_r_r"
    )
    val sparkleScaleLeft by transLeft.animateFloat(
        initialValue = 0.65f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutQuad), RepeatMode.Reverse),
        label = "spark_s_l"
    )
    val sparkleScaleRight by transRight.animateFloat(
        initialValue = 0.55f, targetValue = 1.30f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutQuad), RepeatMode.Reverse),
        label = "spark_s_r"
    )

    // 4. Sick/Dizzy Swirl
    val swirlRotateLeft by transLeft.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "swirl_r_l"
    )
    val swirlRotateRight by transRight.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "swirl_r_r"
    )

    // 5. Sleepy Zzz
    val zzzRise1 by transLeft.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "zzz_r_1"
    )
    val zzzRise2 by transRight.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2750, easing = LinearEasing), RepeatMode.Restart),
        label = "zzz_r_2"
    )

    // 6. Sweat Drops
    val sweatDripLeft by transLeft.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInCubic), RepeatMode.Restart),
        label = "sweat_d_l"
    )
    val sweatDripRight by transRight.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1650, easing = EaseInCubic), RepeatMode.Restart),
        label = "sweat_d_r"
    )

    return PetParticleMotionState(
        heartFloatLeft = heartFloatLeft,
        heartFloatRight = heartFloatRight,
        heartPulseLeft = heartPulseLeft,
        heartPulseRight = heartPulseRight,
        tearFallLeft = tearFallLeft,
        tearFallRight = tearFallRight,
        sparkleRotateLeft = sparkleRotateLeft,
        sparkleRotateRight = sparkleRotateRight,
        sparkleScaleLeft = sparkleScaleLeft,
        sparkleScaleRight = sparkleScaleRight,
        swirlRotateLeft = swirlRotateLeft,
        swirlRotateRight = swirlRotateRight,
        zzzRise1 = zzzRise1,
        zzzRise2 = zzzRise2,
        sweatDripLeft = sweatDripLeft,
        sweatDripRight = sweatDripRight
    )
}

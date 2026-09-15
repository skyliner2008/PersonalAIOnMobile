package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * PetRobotMoodsetDraw — Drawing functions for LOOI Robot: 30 Additional Moodset (Neon Cyan Style)
 *
 * All drawings follow the LOOI Robot Design Spec:
 * - Pure 2D graphic assets on solid black
 * - Neon cyan (#00F5FF) front elements with dark cyan (#004D6B) 2D shadow layer underneath
 * - 5dp shadow offset for shallow depth
 * - Alpha cross-fade support for continuous emotion transitions
 * - Full LivingMotionState procedural animation support
 */

// ─── 1. SICK MOOD ─────────────────────────────────────────────────────────────

internal fun DrawScope.drawSickMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val sickColor = Color(0xFF64FFDA).copy(alpha = effAlpha)
    val shadowColor = Color(0xFF004D40).copy(alpha = effAlpha)

    // Spiral droopy eyes with slow hypnotic rotation with phase offset between left & right
    val eyeR = baseEyeW * 0.45f
    val spiralRotLeft = living.loopSlow * 360f
    val spiralRotRight = -(living.loopSlow * 360f * 1.15f)
    drawSpiralEye(sickColor, leftEyeX, eyeY + 4.dp.toPx(), eyeR, spiralRotLeft, alpha = effAlpha)
    drawSpiralEye(sickColor, rightEyeX, eyeY + 4.dp.toPx(), eyeR, spiralRotRight, alpha = effAlpha)

    // Drooping mouth
    drawFrownArc(sickColor, centerX, eyeY + baseEyeH * 0.75f, 24.dp.toPx(), 8.dp.toPx(), alpha = effAlpha)

    // Thermometer sticking out of mouth angled to bottom-right with fever shiver
    val thermX = centerX + 10.dp.toPx()
    val thermY = eyeY + baseEyeH * 0.76f
    val thermLen = 54.dp.toPx()
    val thermH = 10.dp.toPx()
    val shiverAngle = sin(living.loopFast * 2f * PI.toFloat()) * 3.5f
    val thermAngle = 30f + shiverAngle

    rotate(thermAngle, pivot = Offset(thermX, thermY)) {
        // Shadow
        drawRoundRect(
            color = shadowColor,
            topLeft = Offset(thermX - 4.dp.toPx(), thermY - thermH / 2f + 3.dp.toPx()),
            size = Size(thermLen, thermH),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        )
        // White glass tube body
        drawRoundRect(
            color = Color.White.copy(alpha = 0.25f * effAlpha),
            topLeft = Offset(thermX - 4.dp.toPx(), thermY - thermH / 2f),
            size = Size(thermLen, thermH),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.9f * effAlpha),
            topLeft = Offset(thermX - 4.dp.toPx(), thermY - thermH / 2f),
            size = Size(thermLen, thermH),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
        // Measurement scale ticks
        for (i in 1..4) {
            val tickX = thermX + 8.dp.toPx() + i * 7.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = 0.8f * effAlpha),
                start = Offset(tickX, thermY - thermH / 2f + 2.dp.toPx()),
                end = Offset(tickX, thermY - thermH / 2f + 4.5.dp.toPx()),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        // Red mercury bulb & line with fever pulse
        val mercuryColor = Color(0xFFFF5252).copy(alpha = effAlpha)
        val bulbR = 7.dp.toPx() * living.pulse.coerceIn(0.95f, 1.15f)
        val bulbCenter = Offset(thermX + thermLen + 2.dp.toPx(), thermY)
        drawCircle(mercuryColor, radius = bulbR, center = bulbCenter)
        drawCircle(Color.White.copy(alpha = 0.6f * effAlpha), radius = bulbR * 0.35f, center = Offset(bulbCenter.x - 2.dp.toPx(), bulbCenter.y - 2.dp.toPx()))
        // Mercury level inside tube
        drawLine(
            color = mercuryColor,
            start = Offset(thermX + 8.dp.toPx(), thermY),
            end = Offset(thermX + thermLen - 2.dp.toPx(), thermY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

// ─── 2. RICH MOOD ─────────────────────────────────────────────────────────────

internal fun DrawScope.drawRichMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val goldColor = Color(0xFFFFD700).copy(alpha = effAlpha)
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)
    val cyanShadowColor = Color(0xFF004D6B).copy(alpha = effAlpha)
    val goldShadowColor = Color(0xFF664400).copy(alpha = effAlpha)

    val eyeW = baseEyeW * 1.05f
    val eyeH = baseEyeH * 1.05f
    val shadowOffsetY = 5.dp.toPx()

    // 1. Draw Left & Right Robot Eyes (Cyan squircle eyes with depth shadow)
    // Shadow layer
    drawRoundRect(
        color = cyanShadowColor,
        topLeft = Offset(leftEyeX - eyeW / 2f, eyeY - eyeH / 2f + shadowOffsetY),
        size = Size(eyeW, eyeH),
        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
    )
    drawRoundRect(
        color = cyanShadowColor,
        topLeft = Offset(rightEyeX - eyeW / 2f, eyeY - eyeH / 2f + shadowOffsetY),
        size = Size(eyeW, eyeH),
        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
    )
    // Core dark cyan visor fill
    drawRoundRect(
        color = Color(0xFF001A29).copy(alpha = 0.90f * effAlpha),
        topLeft = Offset(leftEyeX - eyeW / 2f, eyeY - eyeH / 2f),
        size = Size(eyeW, eyeH),
        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFF001A29).copy(alpha = 0.90f * effAlpha),
        topLeft = Offset(rightEyeX - eyeW / 2f, eyeY - eyeH / 2f),
        size = Size(eyeW, eyeH),
        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
    )
    // Neon cyan rim
    drawRoundRect(
        color = cyanColor.copy(alpha = 0.85f * effAlpha),
        topLeft = Offset(leftEyeX - eyeW / 2f, eyeY - eyeH / 2f),
        size = Size(eyeW, eyeH),
        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
        style = Stroke(width = 3.dp.toPx())
    )
    drawRoundRect(
        color = cyanColor.copy(alpha = 0.85f * effAlpha),
        topLeft = Offset(rightEyeX - eyeW / 2f, eyeY - eyeH / 2f),
        size = Size(eyeW, eyeH),
        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
        style = Stroke(width = 3.dp.toPx())
    )

    // 2. Bright Golden Dollar Sign ($ $) nested inside the eyes (ให้ $$ สีเหลืองทอง อยู่ในดวงตา)
    val dollarW = eyeW * 0.65f
    val dollarH = eyeH * 0.75f
    drawDollarSignEye(goldColor, goldShadowColor, leftEyeX, eyeY, dollarW, dollarH, effAlpha)
    drawDollarSignEye(goldColor, goldShadowColor, rightEyeX, eyeY, dollarW, dollarH, effAlpha)

    rotate(living.sparkleRot, pivot = Offset(leftEyeX + eyeW * 0.38f, eyeY - eyeH * 0.38f)) {
        drawLooiSparkleStar(goldColor, leftEyeX + eyeW * 0.38f, eyeY - eyeH * 0.38f, 15.dp.toPx(), effAlpha)
    }

    // Smiling mouth
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.65f, 20.dp.toPx(), 6.dp.toPx(), alpha = effAlpha)

    // Gold coins floating and bobbing on bottom-left (enlarged & detailed)
    val coinBob1 = sin(living.loopMedium * 2f * PI.toFloat()) * 4.dp.toPx()
    val coinBob2 = cos(living.loopMedium * 2f * PI.toFloat()) * 3.dp.toPx()
    drawGoldCoin(goldColor, leftEyeX - baseEyeW * 0.7f, eyeY + baseEyeH * 0.7f + coinBob1, 16.dp.toPx(), effAlpha)
    drawGoldCoin(goldColor, leftEyeX - baseEyeW * 0.3f, eyeY + baseEyeH * 0.95f + coinBob2, 13.dp.toPx(), effAlpha)

    // Money bag on bottom-right with subtle squash breathing (enlarged & detailed)
    val bagX = rightEyeX + baseEyeW * 0.65f
    val bagY = eyeY + baseEyeH * 0.65f
    val bagScaleY = 1f + 0.06f * sin(living.loopMedium * 2f * PI.toFloat())
    val bagScaleX = 1f / bagScaleY
    scale(scaleX = bagScaleX, scaleY = bagScaleY, pivot = Offset(bagX, bagY)) {
        drawMoneyBag(goldColor, bagX, bagY, 38.dp.toPx(), effAlpha)
    }
}

private fun DrawScope.drawDollarSignEye(
    color: Color, shadowColor: Color,
    cx: Float, cy: Float, w: Float, h: Float, alpha: Float
) {
    val shadowY = 4.dp.toPx()
    // Shadow S
    drawDollarS(shadowColor, cx, cy + shadowY, w, h)
    // Neon Front S
    drawDollarS(color, cx, cy, w, h)
}

private fun DrawScope.drawDollarS(color: Color, cx: Float, cy: Float, w: Float, h: Float) {
    val path = Path().apply {
        val hw = w * 0.45f
        val hh = h * 0.45f
        moveTo(cx + hw * 0.6f, cy - hh * 0.7f)
        cubicTo(cx + hw * 0.6f, cy - hh, cx - hw * 0.6f, cy - hh, cx - hw * 0.6f, cy - hh * 0.4f)
        cubicTo(cx - hw * 0.6f, cy, cx + hw * 0.6f, cy - hh * 0.1f, cx + hw * 0.6f, cy + hh * 0.4f)
        cubicTo(cx + hw * 0.6f, cy + hh, cx - hw * 0.6f, cy + hh, cx - hw * 0.6f, cy + hh * 0.7f)
    }
    drawPath(path, color, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
    // Vertical bars
    drawLine(color, Offset(cx, cy - h * 0.55f), Offset(cx, cy + h * 0.55f), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
}

private fun DrawScope.drawGoldCoin(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
    // Outer glow rim
    drawCircle(color.copy(alpha = 0.25f * alpha), radius = radius + 3.dp.toPx(), center = Offset(cx, cy))
    // Outer solid circle
    drawCircle(Color(0xFFFFB300).copy(alpha = 0.4f * alpha), radius = radius, center = Offset(cx, cy))
    drawCircle(color, radius = radius, center = Offset(cx, cy), style = Stroke(width = 2.5.dp.toPx()))
    // Inner milled ring
    drawCircle(color.copy(alpha = 0.85f * alpha), radius = radius * 0.65f, center = Offset(cx, cy), style = Stroke(width = 1.5.dp.toPx()))
    // Center dollar / star emblem
    drawCircle(color, radius = radius * 0.35f, center = Offset(cx, cy))
}

private fun DrawScope.drawMoneyBag(color: Color, cx: Float, cy: Float, size: Float, alpha: Float) {
    val bagPath = Path().apply {
        // Tied top neck ruffles
        moveTo(cx - size * 0.22f, cy - size * 0.5f)
        lineTo(cx + size * 0.22f, cy - size * 0.5f)
        lineTo(cx + size * 0.12f, cy - size * 0.35f)
        // Main pouch belly
        cubicTo(cx + size * 0.55f, cy - size * 0.2f, cx + size * 0.55f, cy + size * 0.45f, cx, cy + size * 0.5f)
        cubicTo(cx - size * 0.55f, cy + size * 0.45f, cx - size * 0.55f, cy - size * 0.2f, cx - size * 0.12f, cy - size * 0.35f)
        close()
    }
    // Shadow & fill
    drawPath(bagPath, Color(0xFFF57F17).copy(alpha = 0.35f * alpha))
    drawPath(bagPath, color, style = Stroke(width = 2.5.dp.toPx()))
    // Tied neck cord ribbon
    drawRoundRect(
        color = Color(0xFFFFD54F).copy(alpha = alpha),
        topLeft = Offset(cx - size * 0.15f, cy - size * 0.38f),
        size = Size(size * 0.3f, 4.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )
    // $ sign on belly
    val dollarW = size * 0.32f
    val dollarH = size * 0.42f
    val sCy = cy + size * 0.08f
    drawDollarS(color, cx, sCy, dollarW, dollarH)
}

// ─── 3. CRYING MOOD ───────────────────────────────────────────────────────────

internal fun DrawScope.drawCryingMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    tearProgress: Float = 0f,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = NeonCyan.copy(alpha = effAlpha)

    // Sad dome eyes (rounded top, slightly flatter bottom)
    val eyeW = baseEyeW * 0.82f
    val eyeH = baseEyeH * 0.75f * living.microBlinkFactor.coerceAtLeast(0.15f)

    for (eyeX in listOf(leftEyeX, rightEyeX)) {
        // Eye dome
        val domePath = Path().apply {
            val topR = eyeW * 0.5f
            moveTo(eyeX - eyeW * 0.5f, eyeY + eyeH * 0.2f)
            cubicTo(
                eyeX - eyeW * 0.5f, eyeY - eyeH * 0.5f,
                eyeX + eyeW * 0.5f, eyeY - eyeH * 0.5f,
                eyeX + eyeW * 0.5f, eyeY + eyeH * 0.2f
            )
            lineTo(eyeX - eyeW * 0.5f, eyeY + eyeH * 0.2f)
            close()
        }
        drawNeonFilledPath(domePath, cyanColor)

        // Cascading neon tear waterfall flowing downward (LOOI Reference Sheet 2 Cell 25)
        val streamProg = living.loopMedium
        val tearW = eyeW * 0.55f
        val waterfallBottom = eyeY + baseEyeH * 1.15f
        val waveOffset = sin(streamProg * 2f * PI.toFloat() + (if (eyeX == leftEyeX) 0f else 1.2f)) * 4.dp.toPx()

        val waterfallPath = Path().apply {
            moveTo(eyeX - tearW * 0.5f, eyeY + eyeH * 0.15f)
            // Left flowing wavy edge
            cubicTo(
                eyeX - tearW * 0.55f + waveOffset, eyeY + baseEyeH * 0.5f,
                eyeX - tearW * 0.45f - waveOffset, eyeY + baseEyeH * 0.85f,
                eyeX - tearW * 0.35f, waterfallBottom
            )
            // Bottom rounded tip
            quadraticTo(
                eyeX, waterfallBottom + 6.dp.toPx(),
                eyeX + tearW * 0.35f, waterfallBottom
            )
            // Right flowing wavy edge
            cubicTo(
                eyeX + tearW * 0.45f - waveOffset, eyeY + baseEyeH * 0.85f,
                eyeX + tearW * 0.55f + waveOffset, eyeY + baseEyeH * 0.5f,
                eyeX + tearW * 0.5f, eyeY + eyeH * 0.15f
            )
            close()
        }
        drawNeonFilledPath(waterfallPath, cyanColor)

        // Falling neon droplets below waterfall
        val dropProg = (streamProg * 1.5f + (if (eyeX == leftEyeX) 0f else 0.45f)) % 1f
        val dropY = waterfallBottom + 8.dp.toPx() + dropProg * 35.dp.toPx()
        val dropAlpha = (1f - dropProg * 0.6f) * effAlpha
        drawNeonTeardrop(cyanColor, eyeX, dropY, size = 10.dp.toPx(), alpha = dropAlpha)
    }

    // Sad frown mouth between the two streams
    val mouthY = eyeY + baseEyeH * 0.48f
    val mouthW = 18.dp.toPx()
    val mouthH = 8.dp.toPx()
    val sadMouthPath = Path().apply {
        moveTo(centerX - mouthW * 0.5f, mouthY + mouthH)
        quadraticTo(centerX, mouthY - 2.dp.toPx(), centerX + mouthW * 0.5f, mouthY + mouthH)
    }
    drawNeonPath(sadMouthPath, cyanColor, strokeWidth = 5.dp.toPx())
}

// ─── 4. READING MOOD ──────────────────────────────────────────────────────────

internal fun DrawScope.drawReadingMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Eyes scanning left to right across the page line-by-line with living readingScanX and micro-blink
    val readScanX = living.readingScanX
    val effectiveEyeH = baseEyeH * 0.85f * living.microBlinkFactor.coerceAtLeast(0.12f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY + 3.dp.toPx(), baseEyeW * 0.85f, effectiveEyeH, gazeX = readScanX, gazeY = 0.55f, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY + 3.dp.toPx(), baseEyeW * 0.85f, effectiveEyeH, gazeX = readScanX, gazeY = 0.55f, alpha = effAlpha)

    // Rounded rectangular glasses frame
    val glassW = baseEyeW * 1.25f
    val glassH = baseEyeH * 1.15f
    val glassRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
    val strokeWidth = 3.5.dp.toPx()

    // Left lens frame
    drawRoundRect(
        color = cyanColor,
        topLeft = Offset(leftEyeX - glassW / 2f, eyeY - glassH / 2f + 2.dp.toPx()),
        size = Size(glassW, glassH),
        cornerRadius = glassRadius,
        style = Stroke(width = strokeWidth)
    )
    // Right lens frame
    drawRoundRect(
        color = cyanColor,
        topLeft = Offset(rightEyeX - glassW / 2f, eyeY - glassH / 2f + 2.dp.toPx()),
        size = Size(glassW, glassH),
        cornerRadius = glassRadius,
        style = Stroke(width = strokeWidth)
    )
    // Arched Bridge between lenses
    val bridgePath = Path().apply {
        moveTo(leftEyeX + glassW / 2f, eyeY + 4.dp.toPx())
        quadraticTo(centerX, eyeY - 2.dp.toPx(), rightEyeX - glassW / 2f, eyeY + 4.dp.toPx())
    }
    drawPath(bridgePath, cyanColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

    // Gentle smile above book
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.56f, 18.dp.toPx(), 5.dp.toPx(), alpha = effAlpha)

    // Prominent Open Book at bottom center with pages, spine, text lines, and robot paws
    val bookY = eyeY + baseEyeH * 0.74f
    val bookW = 68.dp.toPx()
    val bookH = 32.dp.toPx()
    val flutter = sin(living.loopSlow * 2f * PI.toFloat()) * 2.dp.toPx()

    // Book cover back shadow
    drawRoundRect(
        color = Color(0xFF004D6B).copy(alpha = effAlpha),
        topLeft = Offset(centerX - bookW * 0.52f, bookY + 3.dp.toPx()),
        size = Size(bookW * 1.04f, bookH * 1.05f),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )

    // Left page
    val leftPage = Path().apply {
        moveTo(centerX, bookY + bookH * 0.15f)
        cubicTo(centerX - bookW * 0.25f, bookY - 4.dp.toPx() + flutter, centerX - bookW * 0.45f, bookY + 2.dp.toPx(), centerX - bookW * 0.5f, bookY + bookH * 0.2f)
        lineTo(centerX - bookW * 0.5f, bookY + bookH)
        cubicTo(centerX - bookW * 0.45f, bookY + bookH * 0.82f, centerX - bookW * 0.25f, bookY + bookH * 0.82f, centerX, bookY + bookH)
        close()
    }
    drawPath(leftPage, Color.White.copy(alpha = 0.92f * effAlpha))
    drawPath(leftPage, cyanColor, style = Stroke(width = 2.dp.toPx()))

    // Right page
    val rightPage = Path().apply {
        moveTo(centerX, bookY + bookH * 0.15f)
        cubicTo(centerX + bookW * 0.25f, bookY - 4.dp.toPx() - flutter, centerX + bookW * 0.45f, bookY + 2.dp.toPx(), centerX + bookW * 0.5f, bookY + bookH * 0.2f)
        lineTo(centerX + bookW * 0.5f, bookY + bookH)
        cubicTo(centerX + bookW * 0.45f, bookY + bookH * 0.82f, centerX + bookW * 0.25f, bookY + bookH * 0.82f, centerX, bookY + bookH)
        close()
    }
    drawPath(rightPage, Color.White.copy(alpha = 0.92f * effAlpha))
    drawPath(rightPage, cyanColor, style = Stroke(width = 2.dp.toPx()))

    // Center spine fold
    drawLine(
        color = Color(0xFF00838F).copy(alpha = effAlpha),
        start = Offset(centerX, bookY + bookH * 0.15f),
        end = Offset(centerX, bookY + bookH),
        strokeWidth = 2.5.dp.toPx()
    )

    // Bookmark ribbon hanging down
    val ribbonPath = Path().apply {
        moveTo(centerX - 2.dp.toPx(), bookY + bookH * 0.8f)
        lineTo(centerX + 2.dp.toPx(), bookY + bookH * 0.8f)
        lineTo(centerX + 3.dp.toPx(), bookY + bookH + 8.dp.toPx())
        lineTo(centerX, bookY + bookH + 5.dp.toPx())
        lineTo(centerX - 3.dp.toPx(), bookY + bookH + 8.dp.toPx())
        close()
    }
    drawPath(ribbonPath, Color(0xFFFF4081).copy(alpha = effAlpha))

    // Text lines on left & right pages
    val textLineColor = Color(0xFF90A4AE).copy(alpha = 0.7f * effAlpha)
    for (i in 0..2) {
        val lineY = bookY + bookH * 0.35f + i * 5.dp.toPx()
        // Left text line
        drawLine(textLineColor, Offset(centerX - bookW * 0.42f, lineY), Offset(centerX - bookW * 0.1f, lineY), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        // Right text line
        drawLine(textLineColor, Offset(centerX + bookW * 0.1f, lineY), Offset(centerX + bookW * 0.42f, lineY), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
    }

    // Cute robot paws holding the book on bottom corners
    val pawW = 12.dp.toPx()
    val pawH = 8.dp.toPx()
    drawRoundRect(
        color = cyanColor,
        topLeft = Offset(centerX - bookW * 0.5f - 2.dp.toPx(), bookY + bookH - pawH * 0.6f),
        size = Size(pawW, pawH),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
    drawRoundRect(
        color = cyanColor,
        topLeft = Offset(centerX + bookW * 0.5f - pawW + 2.dp.toPx(), bookY + bookH - pawH * 0.6f),
        size = Size(pawW, pawH),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
}

// ─── 5. GAMING MOOD ───────────────────────────────────────────────────────────

internal fun DrawScope.drawGamingMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Normal squircle eyes with micro blink
    val eyeH = baseEyeH * living.microBlinkFactor.coerceAtLeast(0.12f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY, baseEyeW, eyeH, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY, baseEyeW, eyeH, alpha = effAlpha)

    // Over-ear Gaming Headset fitting snugly around head
    val headW = (rightEyeX - leftEyeX) + baseEyeW * 1.15f
    val bounceY = sin(living.loopFast * 2f * PI.toFloat()) * 2.dp.toPx()
    val bandTopY = eyeY - baseEyeH * 0.55f + bounceY

    // Headband arc
    val bandPath = Path().apply {
        moveTo(centerX - headW * 0.5f, eyeY + bounceY)
        cubicTo(centerX - headW * 0.5f, bandTopY - 14.dp.toPx(), centerX + headW * 0.5f, bandTopY - 14.dp.toPx(), centerX + headW * 0.5f, eyeY + bounceY)
    }
    drawPath(bandPath, Color(0xFFCFD8DC).copy(alpha = effAlpha), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))

    // Earcups snug against outer eye boundaries
    val cupW = 16.dp.toPx()
    val cupH = baseEyeH * 0.85f
    val cupRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
    // Left earcup
    drawRoundRect(
        color = Color(0xFF37474F).copy(alpha = effAlpha),
        topLeft = Offset(centerX - headW * 0.5f - cupW * 0.5f, eyeY - cupH * 0.5f + bounceY),
        size = Size(cupW, cupH),
        cornerRadius = cupRadius
    )
    drawRoundRect(
        color = Color(0xFFFFD54F).copy(alpha = effAlpha),
        topLeft = Offset(centerX - headW * 0.5f - cupW * 0.15f, eyeY - cupH * 0.35f + bounceY),
        size = Size(cupW * 0.6f, cupH * 0.7f),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
    )
    // Right earcup
    drawRoundRect(
        color = Color(0xFF37474F).copy(alpha = effAlpha),
        topLeft = Offset(centerX + headW * 0.5f - cupW * 0.5f, eyeY - cupH * 0.5f + bounceY),
        size = Size(cupW, cupH),
        cornerRadius = cupRadius
    )
    drawRoundRect(
        color = Color(0xFFFFD54F).copy(alpha = effAlpha),
        topLeft = Offset(centerX + headW * 0.5f - cupW * 0.45f, eyeY - cupH * 0.35f + bounceY),
        size = Size(cupW * 0.6f, cupH * 0.7f),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
    )

    // Microphone boom on left earcup curving toward mouth
    val micPath = Path().apply {
        moveTo(centerX - headW * 0.5f, eyeY + cupH * 0.25f + bounceY)
        quadraticTo(centerX - headW * 0.35f, eyeY + baseEyeH * 0.55f, centerX - 12.dp.toPx(), eyeY + baseEyeH * 0.55f)
    }
    drawPath(micPath, Color(0xFF90A4AE).copy(alpha = effAlpha), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    drawCircle(Color(0xFF00E5FF).copy(alpha = effAlpha), radius = 3.dp.toPx(), center = Offset(centerX - 12.dp.toPx(), eyeY + baseEyeH * 0.55f))

    // Smiling mouth above controller
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.56f, 18.dp.toPx(), 5.dp.toPx(), alpha = effAlpha)

    // Gaming Controller (enlarged & detailed)
    val ctrlY = eyeY + baseEyeH * 0.78f
    drawGamingControllerIcon(cyanColor, centerX, ctrlY, 84.dp.toPx(), 48.dp.toPx(), effAlpha, living)
}

private fun DrawScope.drawGamingControllerIcon(
    color: Color, cx: Float, cy: Float, w: Float, h: Float, alpha: Float,
    living: LivingMotionState = LivingMotionState()
) {
    // Ergonomic controller body path
    val path = Path().apply {
        moveTo(cx - w * 0.32f, cy - h * 0.4f)
        cubicTo(cx - w * 0.15f, cy - h * 0.45f, cx + w * 0.15f, cy - h * 0.45f, cx + w * 0.32f, cy - h * 0.4f)
        // Right grip wing
        cubicTo(cx + w * 0.52f, cy - h * 0.35f, cx + w * 0.55f, cy + h * 0.45f, cx + w * 0.38f, cy + h * 0.5f)
        cubicTo(cx + w * 0.25f, cy + h * 0.52f, cx + w * 0.18f, cy + h * 0.2f, cx, cy + h * 0.2f)
        // Left grip wing
        cubicTo(cx - w * 0.18f, cy + h * 0.2f, cx - w * 0.25f, cy + h * 0.52f, cx - w * 0.38f, cy + h * 0.5f)
        cubicTo(cx - w * 0.55f, cy + h * 0.45f, cx - w * 0.52f, cy - h * 0.35f, cx - w * 0.32f, cy - h * 0.4f)
        close()
    }
    // Body fill & neon border
    drawPath(path, Color(0xFF263238).copy(alpha = 0.9f * alpha))
    drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))

    // D-Pad Cross on left
    val dpadCx = cx - w * 0.24f
    val dpadCy = cy - h * 0.05f
    val dpadBarW = 4.5.dp.toPx()
    val dpadBarL = 14.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(dpadCx - dpadBarW / 2f, dpadCy - dpadBarL / 2f),
        size = Size(dpadBarW, dpadBarL),
        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(dpadCx - dpadBarL / 2f, dpadCy - dpadBarW / 2f),
        size = Size(dpadBarL, dpadBarW),
        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
    )

    // Diamond 4 Action Buttons on right (Y Yellow, B Red, A Green, X Blue)
    val btnCx = cx + w * 0.24f
    val btnCy = cy - h * 0.05f
    val btnR = 2.5.dp.toPx()
    val btnOffset = 5.5.dp.toPx()
    // Top: Yellow Y
    drawCircle(Color(0xFFFFEB3B).copy(alpha = alpha), radius = btnR, center = Offset(btnCx, btnCy - btnOffset))
    // Right: Red B
    drawCircle(Color(0xFFFF5252).copy(alpha = alpha), radius = btnR, center = Offset(btnCx + btnOffset, btnCy))
    // Bottom: Green A
    drawCircle(Color(0xFF69F0AE).copy(alpha = alpha), radius = btnR, center = Offset(btnCx, btnCy + btnOffset))
    // Left: Blue X
    drawCircle(Color(0xFF40C4FF).copy(alpha = alpha), radius = btnR, center = Offset(btnCx - btnOffset, btnCy))

    // Dual analog thumbsticks in bottom center
    val stickR = 4.dp.toPx()
    drawCircle(Color(0xFF455A64).copy(alpha = alpha), radius = stickR, center = Offset(cx - 7.dp.toPx(), cy + 6.dp.toPx()))
    drawCircle(color, radius = stickR, center = Offset(cx - 7.dp.toPx(), cy + 6.dp.toPx()), style = Stroke(width = 1.2.dp.toPx()))

    drawCircle(Color(0xFF455A64).copy(alpha = alpha), radius = stickR, center = Offset(cx + 7.dp.toPx(), cy + 6.dp.toPx()))
    drawCircle(color, radius = stickR, center = Offset(cx + 7.dp.toPx(), cy + 6.dp.toPx()), style = Stroke(width = 1.2.dp.toPx()))
}

// ─── 6. TRAVELING MOOD ────────────────────────────────────────────────────────

internal fun DrawScope.drawTravelingMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Curious eyes with micro blink
    val eyeH = baseEyeH * living.microBlinkFactor.coerceAtLeast(0.12f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY + 4.dp.toPx(), baseEyeW, eyeH, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY + 4.dp.toPx(), baseEyeW, eyeH, alpha = effAlpha)

    // Safari Pith Helmet sitting right on forehead
    val hatY = eyeY - baseEyeH * 0.48f
    val crownW = (rightEyeX - leftEyeX) + baseEyeW * 0.9f
    val crownH = 34.dp.toPx()
    val brimW = (rightEyeX - leftEyeX) + baseEyeW * 1.65f

    val crownPath = Path().apply {
        moveTo(centerX - crownW * 0.48f, hatY)
        cubicTo(centerX - crownW * 0.42f, hatY - crownH, centerX + crownW * 0.42f, hatY - crownH, centerX + crownW * 0.48f, hatY)
        close()
    }
    val safariBeige = Color(0xFFD7CCC8).copy(alpha = effAlpha)
    drawPath(crownPath, safariBeige)
    drawPath(crownPath, Color(0xFF8D6E63).copy(alpha = effAlpha), style = Stroke(width = 2.dp.toPx()))

    // Leather hatband with gold buckle
    val bandH = 6.dp.toPx()
    drawRoundRect(
        color = Color(0xFF5D4037).copy(alpha = effAlpha),
        topLeft = Offset(centerX - crownW * 0.48f, hatY - bandH),
        size = Size(crownW * 0.96f, bandH),
        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
    )
    drawCircle(Color(0xFFFFD700).copy(alpha = effAlpha), radius = 2.5.dp.toPx(), center = Offset(centerX, hatY - bandH * 0.5f))

    // Curved Brim sitting across forehead
    val brimPath = Path().apply {
        moveTo(centerX - brimW * 0.5f, hatY + 6.dp.toPx())
        quadraticTo(centerX, hatY - 2.dp.toPx(), centerX + brimW * 0.5f, hatY + 6.dp.toPx())
        quadraticTo(centerX, hatY + 12.dp.toPx(), centerX - brimW * 0.5f, hatY + 6.dp.toPx())
        close()
    }
    drawPath(brimPath, safariBeige)
    drawPath(brimPath, Color(0xFF8D6E63).copy(alpha = effAlpha), style = Stroke(width = 2.5.dp.toPx()))

    // Friendly travel smile (LOOI Reference Sheet 2 Cell 29)
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.65f, 18.dp.toPx(), 5.dp.toPx(), alpha = effAlpha)

    // Dual Passports on bottom-left (Red tilted behind Blue — LOOI Reference Sheet 2 Cell 29)
    val passX = leftEyeX - baseEyeW * 0.55f
    val passY = eyeY + baseEyeH * 0.68f + sin(living.loopMedium * 2f * PI.toFloat()) * 2.dp.toPx()
    val passW = 24.dp.toPx()
    val passH = 34.dp.toPx()

    // 1. Red Passport (Tilted ~15 degrees behind)
    rotate(16f, pivot = Offset(passX + 10.dp.toPx(), passY + 10.dp.toPx())) {
        drawRoundRect(
            color = Color(0xFFC62828).copy(alpha = effAlpha),
            topLeft = Offset(passX + 6.dp.toPx(), passY - 2.dp.toPx()),
            size = Size(passW, passH),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFFEF9A9A).copy(alpha = effAlpha),
            topLeft = Offset(passX + 6.dp.toPx(), passY - 2.dp.toPx()),
            size = Size(passW, passH),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = 1.2.dp.toPx())
        )
        drawCircle(Color(0xFFFFD700).copy(alpha = effAlpha), radius = 3.5.dp.toPx(), center = Offset(passX + 6.dp.toPx() + passW * 0.5f, passY - 2.dp.toPx() + passH * 0.45f))
    }

    // 2. Blue Passport (Front)
    drawRoundRect(
        color = Color(0xFF1565C0).copy(alpha = effAlpha),
        topLeft = Offset(passX - passW * 0.5f, passY),
        size = Size(passW, passH),
        cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFF90CAF9).copy(alpha = effAlpha),
        topLeft = Offset(passX - passW * 0.5f, passY),
        size = Size(passW, passH),
        cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx())
    )
    // Gold emblem on front passport
    drawCircle(Color(0xFFFFD700).copy(alpha = effAlpha), radius = 4.dp.toPx(), center = Offset(passX, passY + passH * 0.45f))
    drawCircle(Color(0xFF1565C0).copy(alpha = effAlpha), radius = 1.8.dp.toPx(), center = Offset(passX, passY + passH * 0.45f))
    drawLine(Color(0xFFFFD700).copy(alpha = effAlpha), Offset(passX - 4.dp.toPx(), passY + 6.dp.toPx()), Offset(passX + 4.dp.toPx(), passY + 6.dp.toPx()), strokeWidth = 1.2.dp.toPx())

    // Globe icon on bottom-right (enlarged & detailed)
    val globeX = rightEyeX + baseEyeW * 0.55f
    val globeY = eyeY + baseEyeH * 0.70f
    val globeR = 20.dp.toPx()

    // Globe ocean sphere
    drawCircle(Color(0xFF00838F).copy(alpha = 0.4f * effAlpha), radius = globeR, center = Offset(globeX, globeY))
    drawCircle(cyanColor, radius = globeR, center = Offset(globeX, globeY), style = Stroke(width = 2.5.dp.toPx()))

    // Latitude & Longitude grid lines
    drawOval(
        color = cyanColor.copy(alpha = 0.6f * effAlpha),
        topLeft = Offset(globeX - globeR * 0.5f, globeY - globeR),
        size = Size(globeR, globeR * 2f),
        style = Stroke(width = 1.5.dp.toPx())
    )
    drawLine(cyanColor.copy(alpha = 0.6f * effAlpha), Offset(globeX - globeR, globeY), Offset(globeX + globeR, globeY), strokeWidth = 1.5.dp.toPx())

    // Stand C-arm and round base
    val standPath = Path().apply {
        moveTo(globeX, globeY - globeR - 4.dp.toPx())
        cubicTo(globeX + globeR + 6.dp.toPx(), globeY - globeR, globeX + globeR + 6.dp.toPx(), globeY + globeR, globeX, globeY + globeR + 4.dp.toPx())
        lineTo(globeX, globeY + globeR + 10.dp.toPx())
    }
    drawPath(standPath, Color(0xFFFFD700).copy(alpha = effAlpha), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    drawLine(Color(0xFFFFD700).copy(alpha = effAlpha), Offset(globeX - 8.dp.toPx(), globeY + globeR + 10.dp.toPx()), Offset(globeX + 8.dp.toPx(), globeY + globeR + 10.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
}

// ─── 7. WORKING MOOD ──────────────────────────────────────────────────────────

internal fun DrawScope.drawWorkingMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Serious focused eyes with micro blink
    val eyeH = baseEyeH * 0.9f * living.microBlinkFactor.coerceAtLeast(0.12f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY, baseEyeW * 0.9f, eyeH, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY, baseEyeW * 0.9f, eyeH, alpha = effAlpha)

    // Round wireframe glasses
    val r = baseEyeW * 0.58f
    drawCircle(cyanColor, radius = r, center = Offset(leftEyeX, eyeY), style = Stroke(width = 2.5.dp.toPx()))
    drawCircle(cyanColor, radius = r, center = Offset(rightEyeX, eyeY), style = Stroke(width = 2.5.dp.toPx()))
    // Bridge
    val bridgePath = Path().apply {
        moveTo(leftEyeX + r, eyeY)
        quadraticTo(centerX, eyeY - 4.dp.toPx(), rightEyeX - r, eyeY)
    }
    drawPath(bridgePath, cyanColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

    // Coffee mug on bottom-left (enlarged & detailed with handle and steam)
    val mugX = leftEyeX - baseEyeW * 0.65f
    val mugY = eyeY + baseEyeH * 0.66f
    val mugW = 22.dp.toPx()
    val mugH = 24.dp.toPx()

    // Ceramic mug body
    drawRoundRect(
        color = Color(0xFF8D6E63).copy(alpha = effAlpha),
        topLeft = Offset(mugX - mugW / 2f, mugY),
        size = Size(mugW, mugH),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFFD7CCC8).copy(alpha = effAlpha),
        topLeft = Offset(mugX - mugW / 2f, mugY),
        size = Size(mugW, mugH),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )
    // Dark coffee inside rim
    drawOval(
        color = Color(0xFF3E2723).copy(alpha = effAlpha),
        topLeft = Offset(mugX - mugW * 0.45f, mugY + 2.dp.toPx()),
        size = Size(mugW * 0.9f, 6.dp.toPx())
    )
    // Mug handle on left
    val handlePath = Path().apply {
        moveTo(mugX - mugW / 2f, mugY + 5.dp.toPx())
        cubicTo(mugX - mugW / 2f - 9.dp.toPx(), mugY + 5.dp.toPx(), mugX - mugW / 2f - 9.dp.toPx(), mugY + mugH - 5.dp.toPx(), mugX - mugW / 2f, mugY + mugH - 5.dp.toPx())
    }
    drawPath(handlePath, Color(0xFFD7CCC8).copy(alpha = effAlpha), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

    // Rising steam wisps
    val steamProg = living.loopMedium
    for (si in 0..1) {
        val sp = (steamProg + si * 0.5f) % 1f
        val sY = mugY - 4.dp.toPx() - (sp * 22.dp.toPx())
        val sX = mugX + sin((sp + si * 0.3f) * 2f * PI.toFloat()) * 4.dp.toPx()
        val sAlpha = (1f - sp) * 0.7f * effAlpha
        drawCircle(Color.White.copy(alpha = sAlpha), radius = 2.5.dp.toPx() * (1f + sp * 0.6f), center = Offset(sX, sY))
    }

    // Open Laptop on bottom-center/right with glowing terminal code screen
    val lapX = centerX + baseEyeW * 0.25f
    val lapY = eyeY + baseEyeH * 0.70f
    val lapBaseW = 56.dp.toPx()
    val lapBaseH = 12.dp.toPx()
    val lapScreenW = 50.dp.toPx()
    val lapScreenH = 32.dp.toPx()

    // Screen tilted up (dark bezel + glowing screen)
    val screenTopY = lapY - lapScreenH + 4.dp.toPx()
    drawRoundRect(
        color = Color(0xFF263238).copy(alpha = effAlpha),
        topLeft = Offset(lapX - lapScreenW / 2f, screenTopY),
        size = Size(lapScreenW, lapScreenH),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFF78909C).copy(alpha = effAlpha),
        topLeft = Offset(lapX - lapScreenW / 2f, screenTopY),
        size = Size(lapScreenW, lapScreenH),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )
    // Glowing cyan screen interior
    drawRoundRect(
        color = Color(0xFF004D6B).copy(alpha = 0.5f * effAlpha),
        topLeft = Offset(lapX - lapScreenW * 0.44f, screenTopY + 3.dp.toPx()),
        size = Size(lapScreenW * 0.88f, lapScreenH - 6.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )
    // Code lines on laptop screen
    val codeColors = listOf(cyanColor, Color(0xFF69F0AE), Color(0xFFFFD54F), cyanColor)
    for (i in 0..3) {
        val lineY = screenTopY + 7.dp.toPx() + i * 5.dp.toPx()
        val lineLen = when (i) {
            0 -> 18.dp.toPx()
            1 -> 28.dp.toPx()
            2 -> 22.dp.toPx()
            else -> 14.dp.toPx()
        }
        drawLine(
            color = codeColors[i].copy(alpha = 0.85f * effAlpha),
            start = Offset(lapX - lapScreenW * 0.38f, lineY),
            end = Offset(lapX - lapScreenW * 0.38f + lineLen, lineY),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
    // Blinking terminal cursor
    if (living.flickerPhase > 0.4f) {
        val cursorY = screenTopY + 22.dp.toPx()
        drawLine(
            color = Color(0xFF00E676).copy(alpha = effAlpha),
            start = Offset(lapX - lapScreenW * 0.38f + 17.dp.toPx(), cursorY - 1.5.dp.toPx()),
            end = Offset(lapX - lapScreenW * 0.38f + 17.dp.toPx(), cursorY + 2.5.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )
    }

    // Keyboard Base (perspective trapezoid)
    val baseTrapezoid = Path().apply {
        moveTo(lapX - lapScreenW * 0.48f, lapY + 2.dp.toPx())
        lineTo(lapX + lapScreenW * 0.48f, lapY + 2.dp.toPx())
        lineTo(lapX + lapBaseW * 0.5f, lapY + lapBaseH)
        lineTo(lapX - lapBaseW * 0.5f, lapY + lapBaseH)
        close()
    }
    drawPath(baseTrapezoid, Color(0xFF455A64).copy(alpha = effAlpha))
    drawPath(baseTrapezoid, Color(0xFF90A4AE).copy(alpha = effAlpha), style = Stroke(width = 1.5.dp.toPx()))
    // Trackpad
    drawRoundRect(
        color = Color(0xFF37474F).copy(alpha = effAlpha),
        topLeft = Offset(lapX - 8.dp.toPx(), lapY + lapBaseH - 5.dp.toPx()),
        size = Size(16.dp.toPx(), 4.dp.toPx()),
        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
    )

    // Friendly smiling mouth centered between eyes (Sheet 2 Cell 30)
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.55f, 20.dp.toPx(), 6.dp.toPx(), alpha = effAlpha)
}

// ─── 8. COLD MOOD ─────────────────────────────────────────────────────────────

internal fun DrawScope.drawColdMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    visorW: Float, visorH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val frostColor = Color(0xFF80DEEA).copy(alpha = effAlpha)
    val iceColor = Color(0xFFE0F7FA).copy(alpha = 0.9f * effAlpha)

    // Shivering chevron eyes > <
    drawExcitedEye(frostColor, leftEyeX, eyeY, baseEyeW * 0.85f, baseEyeH * 0.85f, isLeft = true, alpha = effAlpha)
    drawExcitedEye(frostColor, rightEyeX, eyeY, baseEyeW * 0.85f, baseEyeH * 0.85f, isLeft = false, alpha = effAlpha)

    // Shivering teeth chatter zigzag mouth
    val mouthY = eyeY + baseEyeH * 0.75f
    val chatterX = if (living.loopFast < 0.5f) 2.dp.toPx() else -2.dp.toPx()
    val zzPath = Path().apply {
        moveTo(centerX - 16.dp.toPx() + chatterX, mouthY)
        lineTo(centerX - 10.dp.toPx() + chatterX, mouthY - 4.dp.toPx())
        lineTo(centerX - 4.dp.toPx() + chatterX, mouthY + 4.dp.toPx())
        lineTo(centerX + 2.dp.toPx() + chatterX, mouthY - 4.dp.toPx())
        lineTo(centerX + 8.dp.toPx() + chatterX, mouthY + 4.dp.toPx())
        lineTo(centerX + 16.dp.toPx() + chatterX, mouthY)
    }
    drawPath(zzPath, frostColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Icicles hanging from top edge
    val topY = centerY - visorH * 0.48f
    val icicleW = visorW * 0.85f
    val icicleStep = 18.dp.toPx()
    var ix = centerX - icicleW * 0.45f
    var toggle = true
    val icePath = Path().apply {
        moveTo(ix, topY)
        while (ix <= centerX + icicleW * 0.45f) {
            val len = if (toggle) 18.dp.toPx() else 10.dp.toPx()
            lineTo(ix + icicleStep * 0.5f, topY + len)
            lineTo(ix + icicleStep, topY)
            ix += icicleStep
            toggle = !toggle
        }
    }
    drawPath(icePath, iceColor)

    // Icicles rising from bottom edge (Sheet 2 Cell 31)
    val bottomY = centerY + visorH * 0.48f
    var bx = centerX - icicleW * 0.45f
    var bToggle = true
    val bIcePath = Path().apply {
        moveTo(bx, bottomY)
        while (bx <= centerX + icicleW * 0.45f) {
            val len = if (bToggle) 18.dp.toPx() else 10.dp.toPx()
            lineTo(bx + icicleStep * 0.5f, bottomY - len)
            lineTo(bx + icicleStep, bottomY)
            bx += icicleStep
            bToggle = !bToggle
        }
    }
    drawPath(bIcePath, iceColor)

    // Drifting falling snowflakes with wind wobble
    for (i in 0 until 3) {
        val sProg = (living.loopMedium + i * 0.33f) % 1f
        val sY = topY + 10.dp.toPx() + sProg * (visorH * 0.7f)
        val sX = rightEyeX + baseEyeW * (0.65f + (i - 1) * 0.3f) + sin(sProg * 3f * PI.toFloat()) * 8.dp.toPx()
        val sAlpha = ((1f - sProg * 0.5f) * effAlpha).coerceIn(0f, 1f)
        drawSnowflake(iceColor.copy(alpha = sAlpha), sX, sY, 8.dp.toPx(), sAlpha)
    }
}

private fun DrawScope.drawSnowflake(color: Color, cx: Float, cy: Float, size: Float, alpha: Float) {
    for (angle in listOf(0f, 60f, 120f)) {
        rotate(angle, pivot = Offset(cx, cy)) {
            drawLine(color, Offset(cx - size, cy), Offset(cx + size, cy), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            // Branches
            val b = size * 0.4f
            drawLine(color, Offset(cx - size * 0.6f, cy - b), Offset(cx - size * 0.6f, cy + b), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color, Offset(cx + size * 0.6f, cy - b), Offset(cx + size * 0.6f, cy + b), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

// ─── 9. HOT MOOD ──────────────────────────────────────────────────────────────

internal fun DrawScope.drawHotMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val hotColor = Color(0xFFFF7043).copy(alpha = effAlpha)
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)
    val shadowColor = Color(0xFF004D6B).copy(alpha = effAlpha)

    // Solid melting puddle eyes with 3 dripping stalactites (Sheet 2 Cell 32)
    val dripPulse = sin(living.loopMedium * 2f * PI.toFloat()) * 3.dp.toPx()
    drawMeltingDropletEye(shadowColor, leftEyeX, eyeY + 5.dp.toPx(), baseEyeW * 0.95f, baseEyeH * 0.95f, dripPulse)
    drawMeltingDropletEye(cyanColor, leftEyeX, eyeY, baseEyeW * 0.95f, baseEyeH * 0.95f, dripPulse)

    drawMeltingDropletEye(shadowColor, rightEyeX, eyeY + 5.dp.toPx(), baseEyeW * 0.95f, baseEyeH * 0.95f, dripPulse)
    drawMeltingDropletEye(cyanColor, rightEyeX, eyeY, baseEyeW * 0.95f, baseEyeH * 0.95f, dripPulse)

    // Sad wavy drooping mouth (Sheet 2 Cell 32)
    val mouthY = eyeY + baseEyeH * 0.65f
    val mouthW = 22.dp.toPx()
    val mouthPath = Path().apply {
        moveTo(centerX - mouthW * 0.5f, mouthY)
        quadraticTo(centerX - mouthW * 0.2f, mouthY - 4.dp.toPx(), centerX, mouthY)
        quadraticTo(centerX + mouthW * 0.2f, mouthY + 4.dp.toPx(), centerX + mouthW * 0.5f, mouthY)
    }
    drawPath(mouthPath, cyanColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

    // Sweat drops swelling and dripping downward on upper left
    val dripProg = living.loopMedium
    val drip1Y = (eyeY - 8.dp.toPx()) + dripProg * 35.dp.toPx()
    val drip2Y = (eyeY + 4.dp.toPx()) + ((dripProg + 0.5f) % 1f) * 30.dp.toPx()
    drawSweatTeardrop(cyanColor.copy(alpha = (1f - dripProg * 0.6f) * effAlpha), leftEyeX - baseEyeW * 0.6f, drip1Y, 9.dp.toPx(), effAlpha)
    drawSweatTeardrop(cyanColor.copy(alpha = (1f - ((dripProg + 0.5f) % 1f) * 0.6f) * effAlpha), leftEyeX - baseEyeW * 0.85f, drip2Y, 7.dp.toPx(), effAlpha)

    // Blazing sun on top-right with pulsing radiant rays (enlarged & vibrant)
    val sunX = rightEyeX + baseEyeW * 0.7f
    val sunY = eyeY - baseEyeH * 0.70f
    val sunR = 18.dp.toPx() * living.pulse.coerceIn(0.92f, 1.12f)
    drawCircle(Color(0xFFFFB300).copy(alpha = effAlpha), radius = sunR, center = Offset(sunX, sunY))
    for (i in 0 until 8) {
        val angle = (i * 45f + living.sparkleRot * 0.25f) * (PI.toFloat() / 180f)
        val r1 = sunR + 4.dp.toPx()
        val r2 = sunR + 10.dp.toPx() * living.pulse.coerceIn(0.85f, 1.25f)
        drawLine(
            Color(0xFFFF8F00).copy(alpha = effAlpha),
            Offset(sunX + cos(angle) * r1, sunY + sin(angle) * r1),
            Offset(sunX + cos(angle) * r2, sunY + sin(angle) * r2),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    // Thermometer with boiling mercury on bottom-right (enlarged & clear)
    val thermX = rightEyeX + baseEyeW * 0.75f
    val thermY = eyeY + baseEyeH * 0.62f
    val tubeW = 12.dp.toPx()
    val tubeH = 36.dp.toPx()
    val bulbR = 8.dp.toPx()

    // Glass tube body outline & fill
    drawRoundRect(
        color = Color.White.copy(alpha = 0.2f * effAlpha),
        topLeft = Offset(thermX - tubeW / 2f, thermY - tubeH / 2f),
        size = Size(tubeW, tubeH),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.9f * effAlpha),
        topLeft = Offset(thermX - tubeW / 2f, thermY - tubeH / 2f),
        size = Size(tubeW, tubeH),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )
    // Red mercury bulb & boiling column
    val mercY = thermY + tubeH / 2f
    drawCircle(Color(0xFFFF1744).copy(alpha = effAlpha), radius = bulbR, center = Offset(thermX, mercY))
    drawCircle(Color.White.copy(alpha = 0.6f * effAlpha), radius = bulbR * 0.35f, center = Offset(thermX - 2.5.dp.toPx(), mercY - 2.5.dp.toPx()))
    // Boiling mercury line almost to top
    val boilPulse = sin(living.loopFast * 4f * PI.toFloat()) * 2.dp.toPx()
    drawLine(
        color = Color(0xFFFF1744).copy(alpha = effAlpha),
        start = Offset(thermX, mercY),
        end = Offset(thermX, thermY - tubeH / 2f + 5.dp.toPx() + boilPulse),
        strokeWidth = 5.dp.toPx(),
        cap = StrokeCap.Round
    )
    // Measurement ticks
    for (i in 1..4) {
        val tickY = thermY - tubeH / 2f + 6.dp.toPx() + i * 5.dp.toPx()
        drawLine(Color.White.copy(alpha = 0.8f * effAlpha), Offset(thermX + tubeW / 2f - 4.dp.toPx(), tickY), Offset(thermX + tubeW / 2f - 1.dp.toPx(), tickY), strokeWidth = 1.5.dp.toPx())
    }
}

private fun DrawScope.drawMeltingDropletEye(color: Color, cx: Float, cy: Float, w: Float, h: Float, dripPulse: Float = 0f) {
    val topDomeH = h * 0.45f
    val path = Path().apply {
        // Top rounded dome
        moveTo(cx - w * 0.45f, cy)
        cubicTo(cx - w * 0.45f, cy - topDomeH * 1.1f, cx + w * 0.45f, cy - topDomeH * 1.1f, cx + w * 0.45f, cy)
        // Right drip (shorter)
        cubicTo(cx + w * 0.45f, cy + h * 0.35f, cx + w * 0.35f, cy + h * 0.55f + dripPulse * 0.5f, cx + w * 0.28f, cy + h * 0.55f + dripPulse * 0.5f)
        cubicTo(cx + w * 0.22f, cy + h * 0.55f + dripPulse * 0.5f, cx + w * 0.20f, cy + h * 0.25f, cx + w * 0.15f, cy + h * 0.25f)
        // Center drip (longest)
        cubicTo(cx + w * 0.10f, cy + h * 0.5f, cx + w * 0.08f, cy + h * 0.75f + dripPulse, cx, cy + h * 0.75f + dripPulse)
        cubicTo(cx - w * 0.08f, cy + h * 0.75f + dripPulse, cx - w * 0.10f, cy + h * 0.5f, cx - w * 0.15f, cy + h * 0.25f)
        // Left drip (shorter)
        cubicTo(cx - w * 0.20f, cy + h * 0.25f, cx - w * 0.22f, cy + h * 0.55f + dripPulse * 0.5f, cx - w * 0.28f, cy + h * 0.55f + dripPulse * 0.5f)
        cubicTo(cx - w * 0.35f, cy + h * 0.55f + dripPulse * 0.5f, cx - w * 0.45f, cy + h * 0.35f, cx - w * 0.45f, cy)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawSweatTeardrop(color: Color, cx: Float, cy: Float, size: Float, alpha: Float) {
    drawNeonTeardrop(color, cx, cy, size, alpha = alpha)
}

// ─── 10. DETECTIVE MOOD ───────────────────────────────────────────────────────

internal fun DrawScope.drawDetectiveMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Narrow suspicious eyes with micro blink
    val eyeH = baseEyeH * 0.5f * living.microBlinkFactor.coerceAtLeast(0.15f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY + 4.dp.toPx(), baseEyeW * 0.9f, eyeH, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY + 4.dp.toPx(), baseEyeW * 0.9f, eyeH, alpha = effAlpha)

    // Smirk mouth between eyes (Sheet 2 Cell 33)
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.55f, 20.dp.toPx(), 5.dp.toPx(), alpha = effAlpha)

    // Fedora Hat resting naturally across forehead (tall crown + indented crease)
    val hatY = eyeY - baseEyeH * 0.48f
    val crownW = (rightEyeX - leftEyeX) + baseEyeW * 0.95f
    val crownH = 44.dp.toPx()
    val brimW = (rightEyeX - leftEyeX) + baseEyeW * 1.6f

    // Indented crown (pinched top crease)
    val crownPath = Path().apply {
        moveTo(centerX - crownW * 0.45f, hatY)
        cubicTo(centerX - crownW * 0.4f, hatY - crownH * 0.75f, centerX - 10.dp.toPx(), hatY - crownH * 0.9f, centerX, hatY - crownH * 0.78f)
        cubicTo(centerX + 10.dp.toPx(), hatY - crownH * 0.9f, centerX + crownW * 0.4f, hatY - crownH * 0.75f, centerX + crownW * 0.45f, hatY)
        close()
    }
    val fedoraColor = Color(0xFF37474F).copy(alpha = effAlpha)
    drawPath(crownPath, fedoraColor)
    drawPath(crownPath, Color(0xFF78909C).copy(alpha = effAlpha), style = Stroke(width = 2.dp.toPx()))

    // Black ribbon hatband with silver buckle
    val bandH = 7.dp.toPx()
    drawRoundRect(
        color = Color(0xFF212121).copy(alpha = effAlpha),
        topLeft = Offset(centerX - crownW * 0.45f, hatY - bandH),
        size = Size(crownW * 0.9f, bandH),
        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFFCFD8DC).copy(alpha = effAlpha),
        topLeft = Offset(centerX - 4.dp.toPx(), hatY - bandH - 1.dp.toPx()),
        size = Size(8.dp.toPx(), bandH + 2.dp.toPx()),
        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Curved Brim sitting across forehead
    val brimPath = Path().apply {
        moveTo(centerX - brimW * 0.5f, hatY + 5.dp.toPx())
        cubicTo(centerX - brimW * 0.2f, hatY - 2.dp.toPx(), centerX + brimW * 0.2f, hatY - 2.dp.toPx(), centerX + brimW * 0.5f, hatY + 5.dp.toPx())
        cubicTo(centerX + brimW * 0.2f, hatY + 12.dp.toPx(), centerX - brimW * 0.2f, hatY + 12.dp.toPx(), centerX - brimW * 0.5f, hatY + 5.dp.toPx())
        close()
    }
    drawPath(brimPath, fedoraColor)
    drawPath(brimPath, Color(0xFF78909C).copy(alpha = effAlpha), style = Stroke(width = 2.5.dp.toPx()))

    // Magnifying glass over right side with smooth left-right panning scan
    val magPanX = sin(living.loopSlow * 2f * PI.toFloat()) * 20.dp.toPx()
    val magX = rightEyeX + baseEyeW * 0.45f + magPanX
    val magY = eyeY + baseEyeH * 0.32f
    val magR = 26.dp.toPx()

    // Glass lens background
    drawCircle(Color(0xFF00E5FF).copy(alpha = 0.15f * effAlpha), radius = magR, center = Offset(magX, magY))
    // Metallic frame
    drawCircle(Color(0xFFCFD8DC).copy(alpha = effAlpha), radius = magR, center = Offset(magX, magY), style = Stroke(width = 3.5.dp.toPx()))

    // Glint line glides across lens
    val glintShift = (living.loopMedium - 0.5f) * magR * 1.2f
    drawLine(
        color = Color.White.copy(alpha = 0.8f * effAlpha),
        start = Offset(magX + glintShift - 8.dp.toPx(), magY - 8.dp.toPx()),
        end = Offset(magX + glintShift + 8.dp.toPx(), magY + 8.dp.toPx()),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round
    )
    // Sturdy Wooden handle angled down-right at 45 degrees
    val handleStart = Offset(magX + magR * 0.7f, magY + magR * 0.7f)
    val handleEnd = Offset(magX + magR * 0.7f + 22.dp.toPx(), magY + magR * 0.7f + 22.dp.toPx())
    drawLine(
        color = Color(0xFF5D4037).copy(alpha = effAlpha),
        start = handleStart,
        end = handleEnd,
        strokeWidth = 6.dp.toPx(),
        cap = StrokeCap.Round
    )
    // Gold connector ring on handle
    drawLine(
        color = Color(0xFFFFD700).copy(alpha = effAlpha),
        start = handleStart,
        end = Offset(handleStart.x + 4.dp.toPx(), handleStart.y + 4.dp.toPx()),
        strokeWidth = 7.dp.toPx(),
        cap = StrokeCap.Round
    )
}

// ─── 11. COOKING MOOD ─────────────────────────────────────────────────────────

internal fun DrawScope.drawCookingMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Happy eyes with micro blink
    val eyeH = baseEyeH * living.microBlinkFactor.coerceAtLeast(0.12f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY + 4.dp.toPx(), baseEyeW, eyeH, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY + 4.dp.toPx(), baseEyeW, eyeH, alpha = effAlpha)

    // Smiling mouth centered between eyes (Sheet 2 Cell 34)
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.55f, 20.dp.toPx(), 6.dp.toPx(), alpha = effAlpha)

    // White Chef Toque resting right on forehead (tall 3-lobe puffs)
    val hatY = eyeY - baseEyeH * 0.48f
    val baseBandW = (rightEyeX - leftEyeX) + baseEyeW * 0.7f
    val baseBandH = 12.dp.toPx()
    val toqueH = 54.dp.toPx()

    // Puffy cloud lobes expanding upward
    val toquePath = Path().apply {
        moveTo(centerX - baseBandW * 0.5f, hatY)
        lineTo(centerX + baseBandW * 0.5f, hatY)
        cubicTo(centerX + baseBandW * 0.65f, hatY - toqueH * 0.4f, centerX + baseBandW * 0.5f, hatY - toqueH * 0.85f, centerX + baseBandW * 0.2f, hatY - toqueH * 0.85f)
        cubicTo(centerX + 12.dp.toPx(), hatY - toqueH * 1.05f, centerX - 12.dp.toPx(), hatY - toqueH * 1.05f, centerX - baseBandW * 0.2f, hatY - toqueH * 0.85f)
        cubicTo(centerX - baseBandW * 0.5f, hatY - toqueH * 0.85f, centerX - baseBandW * 0.65f, hatY - toqueH * 0.4f, centerX - baseBandW * 0.5f, hatY)
        close()
    }
    val chefWhite = Color.White.copy(alpha = 0.95f * effAlpha)
    drawPath(toquePath, chefWhite)
    drawPath(toquePath, Color(0xFFB0BEC5).copy(alpha = effAlpha), style = Stroke(width = 2.dp.toPx()))

    // Base pleat band
    drawRoundRect(
        color = Color(0xFFECEFF1).copy(alpha = effAlpha),
        topLeft = Offset(centerX - baseBandW * 0.5f, hatY - baseBandH),
        size = Size(baseBandW, baseBandH),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFFB0BEC5).copy(alpha = effAlpha),
        topLeft = Offset(centerX - baseBandW * 0.5f, hatY - baseBandH),
        size = Size(baseBandW, baseBandH),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx())
    )
    // Vertical pleats on band
    for (i in 1..4) {
        val pleatX = centerX - baseBandW * 0.4f + i * (baseBandW * 0.16f)
        drawLine(Color(0xFFB0BEC5).copy(alpha = 0.8f * effAlpha), Offset(pleatX, hatY - baseBandH + 2.dp.toPx()), Offset(pleatX, hatY - 2.dp.toPx()), strokeWidth = 1.2.dp.toPx())
    }

    // Frying pan on bottom-right with Sunny-side up fried egg (enlarged & detailed)
    val panX = rightEyeX + baseEyeW * 0.48f
    val panY = eyeY + baseEyeH * 0.72f
    val panR = 24.dp.toPx()

    // Cast iron pan body
    drawCircle(Color(0xFF263238).copy(alpha = effAlpha), radius = panR, center = Offset(panX, panY))
    drawCircle(Color(0xFF78909C).copy(alpha = effAlpha), radius = panR, center = Offset(panX, panY), style = Stroke(width = 3.dp.toPx()))

    // Pan handle angled up-right
    val hStart = Offset(panX + panR * 0.7f, panY - panR * 0.7f)
    val hEnd = Offset(panX + panR * 0.7f + 20.dp.toPx(), panY - panR * 0.7f - 18.dp.toPx())
    drawLine(Color(0xFF37474F).copy(alpha = effAlpha), hStart, hEnd, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
    drawLine(Color(0xFF78909C).copy(alpha = effAlpha), hStart, hEnd, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

    // Sunny-Side-Up Fried Egg inside pan!
    val eggWhitePath = Path().apply {
        moveTo(panX - 12.dp.toPx(), panY)
        cubicTo(panX - 14.dp.toPx(), panY - 12.dp.toPx(), panX + 10.dp.toPx(), panY - 14.dp.toPx(), panX + 12.dp.toPx(), panY - 2.dp.toPx())
        cubicTo(panX + 15.dp.toPx(), panY + 10.dp.toPx(), panX - 2.dp.toPx(), panY + 14.dp.toPx(), panX - 12.dp.toPx(), panY)
        close()
    }
    drawPath(eggWhitePath, Color.White.copy(alpha = 0.95f * effAlpha))
    val yolkCenter = Offset(panX, panY - 1.dp.toPx())
    val yolkR = 7.dp.toPx()
    drawCircle(Color(0xFFFFB300).copy(alpha = effAlpha), radius = yolkR, center = yolkCenter)
    drawCircle(Color(0xFFFFD54F).copy(alpha = effAlpha), radius = yolkR * 0.5f, center = Offset(yolkCenter.x - 1.5.dp.toPx(), yolkCenter.y - 1.5.dp.toPx()))
    drawCircle(Color.White.copy(alpha = 0.85f * effAlpha), radius = 2.dp.toPx(), center = Offset(yolkCenter.x - 2.dp.toPx(), yolkCenter.y - 2.dp.toPx()))

    // Sizzling food oil sparks
    val sizzleY = abs(sin(living.loopFast * 2f * PI.toFloat())) * 5.dp.toPx()
    drawCircle(Color(0xFFFF9800).copy(alpha = effAlpha), radius = 2.dp.toPx(), center = Offset(panX - 8.dp.toPx(), panY - 8.dp.toPx() - sizzleY))
    drawCircle(Color(0xFFFFD54F).copy(alpha = effAlpha), radius = 2.dp.toPx(), center = Offset(panX + 8.dp.toPx(), panY + 8.dp.toPx() - (sizzleY * 0.7f)))

    // Aroma steam wisps
    val aromaProg = living.loopMedium
    val aromaY = panY - panR - 4.dp.toPx() - (aromaProg * 20.dp.toPx())
    val aromaX = panX + sin(aromaProg * 2f * PI.toFloat()) * 5.dp.toPx()
    drawCircle(Color.White.copy(alpha = (1f - aromaProg) * 0.7f * effAlpha), radius = 3.dp.toPx() * (1f + aromaProg * 0.5f), center = Offset(aromaX, aromaY))
}

// ─── 12. ART MODE ─────────────────────────────────────────────────────────────

internal fun DrawScope.drawArtMode(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Round happy eyes with micro blink
    val eyeH = baseEyeH * living.microBlinkFactor.coerceAtLeast(0.12f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY + 4.dp.toPx(), baseEyeW, eyeH, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY + 4.dp.toPx(), baseEyeW, eyeH, alpha = effAlpha)

    // Smiling mouth centered between eyes (Sheet 2 Cell 35)
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.55f, 20.dp.toPx(), 6.dp.toPx(), alpha = effAlpha)

    // Red Artist Beret tilted on left, sitting on forehead
    val beretY = eyeY - baseEyeH * 0.48f
    val beretW = (rightEyeX - leftEyeX) + baseEyeW * 1.5f
    val beretTilt = sin(living.loopSlow * 2f * PI.toFloat()) * 1.5f
    val beretPath = Path().apply {
        moveTo(centerX - beretW * 0.52f, beretY + 4.dp.toPx())
        cubicTo(centerX - beretW * 0.55f, beretY - 32.dp.toPx(), centerX + beretW * 0.05f, beretY - 36.dp.toPx(), centerX + beretW * 0.38f, beretY - 14.dp.toPx())
        cubicTo(centerX + beretW * 0.42f, beretY + 2.dp.toPx(), centerX - beretW * 0.1f, beretY + 8.dp.toPx(), centerX - beretW * 0.52f, beretY + 4.dp.toPx())
        close()
    }
    rotate(beretTilt, pivot = Offset(centerX, beretY)) {
        val beretRed = Color(0xFFD32F2F).copy(alpha = effAlpha)
        drawPath(beretPath, beretRed)
        drawPath(beretPath, Color(0xFFB71C1C).copy(alpha = effAlpha), style = Stroke(width = 2.dp.toPx()))
        // Little stem (cabillou) on top of beret
        drawLine(
            color = Color(0xFFB71C1C).copy(alpha = effAlpha),
            start = Offset(centerX - 8.dp.toPx(), beretY - 30.dp.toPx()),
            end = Offset(centerX - 8.dp.toPx(), beretY - 38.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    // Wooden Painter's Palette with 4 rich color dollops and paintbrush (enlarged & detailed)
    val palX = rightEyeX + baseEyeW * 0.5f
    val palY = eyeY + baseEyeH * 0.70f
    val palW = 54.dp.toPx()
    val palH = 38.dp.toPx()

    val palPath = Path().apply {
        moveTo(palX - palW * 0.45f, palY)
        cubicTo(palX - palW * 0.5f, palY - palH * 0.5f, palX + palW * 0.45f, palY - palH * 0.55f, palX + palW * 0.48f, palY - palH * 0.1f)
        cubicTo(palX + palW * 0.5f, palY + palH * 0.45f, palX + palW * 0.15f, palY + palH * 0.55f, palX - palW * 0.1f, palY + palH * 0.4f)
        cubicTo(palX - palW * 0.25f, palY + palH * 0.25f, palX - palW * 0.35f, palY + palH * 0.35f, palX - palW * 0.45f, palY)
        close()
    }
    drawPath(palPath, Color(0xFFBCAAA4).copy(alpha = effAlpha))
    drawPath(palPath, Color(0xFF8D6E63).copy(alpha = effAlpha), style = Stroke(width = 2.dp.toPx()))

    // Thumb hole at bottom right
    drawCircle(Color.Black, radius = 4.dp.toPx(), center = Offset(palX + palW * 0.25f, palY + palH * 0.15f))
    drawCircle(Color(0xFF8D6E63).copy(alpha = effAlpha), radius = 4.dp.toPx(), center = Offset(palX + palW * 0.25f, palY + palH * 0.15f), style = Stroke(width = 1.5.dp.toPx()))

    // 4 Bold Paint Dollops (Red, Yellow, Blue, Green)
    val pRadius = 3.5.dp.toPx() * living.pulse.coerceIn(0.9f, 1.15f)
    drawCircle(Color(0xFFFF1744).copy(alpha = effAlpha), radius = pRadius, center = Offset(palX - palW * 0.3f, palY - palH * 0.2f))
    drawCircle(Color(0xFFFFD700).copy(alpha = effAlpha), radius = pRadius, center = Offset(palX - palW * 0.08f, palY - palH * 0.35f))
    drawCircle(Color(0xFF2979FF).copy(alpha = effAlpha), radius = pRadius, center = Offset(palX + palW * 0.18f, palY - palH * 0.28f))
    drawCircle(Color(0xFF00E676).copy(alpha = effAlpha), radius = pRadius, center = Offset(palX - palW * 0.25f, palY + palH * 0.12f))

    // Paintbrush passing through palette
    val brushStart = Offset(palX + palW * 0.4f, palY + palH * 0.35f)
    val brushEnd = Offset(palX - palW * 0.2f, palY - palH * 0.45f)
    drawLine(Color(0xFF5D4037).copy(alpha = effAlpha), brushStart, brushEnd, strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
    val ferrulePos = Offset(palX - palW * 0.15f, palY - palH * 0.4f)
    drawCircle(Color(0xFFCFD8DC).copy(alpha = effAlpha), radius = 3.dp.toPx(), center = ferrulePos)
    drawCircle(Color(0xFF2979FF).copy(alpha = effAlpha), radius = 3.dp.toPx(), center = brushEnd)
}

// ─── 13. SPACE MOOD ───────────────────────────────────────────────────────────

internal fun DrawScope.drawSpaceMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Astronaut Helmet Outer Frame
    val helmW = (rightEyeX - leftEyeX) + baseEyeW * 2.5f
    val helmH = baseEyeH * 2.4f
    drawRoundRect(
        color = Color(0xFF78909C).copy(alpha = effAlpha),
        topLeft = Offset(centerX - helmW / 2f, eyeY - helmH / 2f),
        size = Size(helmW, helmH),
        cornerRadius = CornerRadius(helmW * 0.45f, helmH * 0.45f),
        style = Stroke(width = 3.dp.toPx())
    )

    // Visor dark background
    drawRoundRect(
        color = Color(0xFF1A237E).copy(alpha = 0.4f * effAlpha),
        topLeft = Offset(centerX - helmW * 0.45f, eyeY - helmH * 0.42f),
        size = Size(helmW * 0.9f, helmH * 0.84f),
        cornerRadius = CornerRadius(helmW * 0.4f, helmH * 0.4f)
    )

    // Eyes inside helmet with micro blink
    val eyeH = baseEyeH * 0.85f * living.microBlinkFactor.coerceAtLeast(0.12f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY, baseEyeW * 0.85f, eyeH, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY, baseEyeW * 0.85f, eyeH, alpha = effAlpha)

    // Smiling mouth inside helmet (Sheet 2 Cell 36)
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.50f, 18.dp.toPx(), 5.dp.toPx(), alpha = effAlpha)

    // Flying saucer UFO on top-right (enlarged & detailed)
    val ufoX = rightEyeX + baseEyeW * 0.75f
    val ufoY = eyeY - baseEyeH * 0.72f + sin(living.loopMedium * 2f * PI.toFloat()) * 4.dp.toPx()
    val ufoW = 48.dp.toPx()
    val ufoH = 18.dp.toPx()
    // Saucer disc
    drawOval(Color(0xFFCFD8DC).copy(alpha = effAlpha), topLeft = Offset(ufoX - ufoW / 2f, ufoY - ufoH / 2f), size = Size(ufoW, ufoH))
    drawOval(Color(0xFF78909C).copy(alpha = effAlpha), topLeft = Offset(ufoX - ufoW / 2f, ufoY - ufoH / 2f), size = Size(ufoW, ufoH), style = Stroke(width = 2.dp.toPx()))
    // Cyan cockpit dome
    drawArc(cyanColor, 180f, 180f, true, topLeft = Offset(ufoX - ufoW * 0.28f, ufoY - ufoH * 1.05f), size = Size(ufoW * 0.56f, ufoH * 1.3f))
    // Tiny cockpit glow
    drawCircle(Color.White.copy(alpha = 0.9f * effAlpha), radius = 2.5.dp.toPx(), center = Offset(ufoX - 3.dp.toPx(), ufoY - ufoH * 0.35f))
    // UFO beam dots underneath
    for (b in -1..1) {
        val dotX = ufoX + b * (ufoW * 0.24f)
        drawCircle(Color(0xFFFFEA00).copy(alpha = 0.85f * effAlpha), radius = 1.5.dp.toPx(), center = Offset(dotX, ufoY + ufoH * 0.35f))
    }

    // Orbiting stars
    val orbitA = helmW * 0.56f
    val orbitB = helmH * 0.35f
    val orbitAngle1 = living.loopSlow * 2f * PI.toFloat()
    val star1X = centerX + cos(orbitAngle1) * orbitA
    val star1Y = eyeY + sin(orbitAngle1) * orbitB
    rotate(living.sparkleRot, pivot = Offset(star1X, star1Y)) {
        drawLooiSparkleStar(Color(0xFFFFD700), star1X, star1Y, 10.dp.toPx(), effAlpha)
    }
}

// ─── 14. PARTY MOOD ───────────────────────────────────────────────────────────

internal fun DrawScope.drawPartyMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Happy upward curved eyes ^ ^
    drawHappyEye(cyanColor, leftEyeX, eyeY, baseEyeW, baseEyeH, alpha = effAlpha)
    drawHappyEye(cyanColor, rightEyeX, eyeY, baseEyeW, baseEyeH, alpha = effAlpha)

    // Party Cone Hat sitting on forehead/top of right eye (tilted & striped)
    val hatBaseX = rightEyeX + baseEyeW * 0.15f
    val hatBaseY = eyeY - baseEyeH * 0.48f
    val hatWobble = sin(living.loopMedium * 2f * PI.toFloat()) * 3f
    val hatW = 34.dp.toPx()
    val hatH = 46.dp.toPx()

    rotate(hatWobble, pivot = Offset(hatBaseX, hatBaseY)) {
        val conePath = Path().apply {
            moveTo(hatBaseX - hatW * 0.5f, hatBaseY)
            lineTo(hatBaseX + hatW * 0.5f, hatBaseY)
            lineTo(hatBaseX, hatBaseY - hatH)
            close()
        }
        drawPath(conePath, Color(0xFFFF4081).copy(alpha = effAlpha))

        // Diagonal festive stripes (Yellow & Cyan)
        val stripe1 = Path().apply {
            moveTo(hatBaseX - hatW * 0.35f, hatBaseY - hatH * 0.3f)
            lineTo(hatBaseX + hatW * 0.25f, hatBaseY - hatH * 0.5f)
            lineTo(hatBaseX + hatW * 0.15f, hatBaseY - hatH * 0.65f)
            lineTo(hatBaseX - hatW * 0.25f, hatBaseY - hatH * 0.45f)
            close()
        }
        drawPath(stripe1, Color(0xFFFFD700).copy(alpha = effAlpha))

        val stripe2 = Path().apply {
            moveTo(hatBaseX - hatW * 0.45f, hatBaseY - hatH * 0.1f)
            lineTo(hatBaseX + hatW * 0.4f, hatBaseY - hatH * 0.25f)
            lineTo(hatBaseX + hatW * 0.3f, hatBaseY - hatH * 0.38f)
            lineTo(hatBaseX - hatW * 0.4f, hatBaseY - hatH * 0.22f)
            close()
        }
        drawPath(stripe2, cyanColor)

        // Hat outline
        drawPath(conePath, Color.White.copy(alpha = 0.85f * effAlpha), style = Stroke(width = 2.dp.toPx()))

        // Fluffy pom-pom on top
        drawCircle(Color(0xFFFFD700).copy(alpha = effAlpha), radius = 5.dp.toPx(), center = Offset(hatBaseX, hatBaseY - hatH))
        drawCircle(Color.White.copy(alpha = 0.8f * effAlpha), radius = 2.5.dp.toPx(), center = Offset(hatBaseX - 1.5.dp.toPx(), hatBaseY - hatH - 1.5.dp.toPx()))
    }

    // Party Noisemaker horn at mouth level blowing outward to right (Sheet 2 Cell 37)
    val blowerY = eyeY + baseEyeH * 0.55f
    val hornExtension = living.partyHornProg // 0f (curled) to 1f (fully blown out)
    val blowerLength = 16.dp.toPx() + hornExtension * 38.dp.toPx()

    val hornPath = Path().apply {
        moveTo(centerX, blowerY - 3.dp.toPx())
        lineTo(centerX + blowerLength, blowerY - (4.dp.toPx() + hornExtension * 3.dp.toPx()))
        lineTo(centerX + blowerLength, blowerY + (4.dp.toPx() + hornExtension * 3.dp.toPx()))
        lineTo(centerX, blowerY + 3.dp.toPx())
        close()
    }
    drawPath(hornPath, Color(0xFFFFAB00).copy(alpha = effAlpha))
    drawPath(hornPath, Color.White.copy(alpha = 0.8f * effAlpha), style = Stroke(width = 1.5.dp.toPx()))

    // Curled paper roll at end of horn - uncurls as extended
    val curlCx = centerX + blowerLength + 3.dp.toPx()
    val curlCy = blowerY
    val curlRadius = (5.dp.toPx() * (1f - hornExtension * 0.45f)).coerceAtLeast(2.dp.toPx())
    drawCircle(Color(0xFFFF4081).copy(alpha = effAlpha), radius = curlRadius, center = Offset(curlCx, curlCy))
    drawCircle(Color(0xFFFFD700).copy(alpha = effAlpha), radius = curlRadius * 0.45f, center = Offset(curlCx, curlCy))

    // Dynamic fluttering confetti particles all around
    val colors = listOf(Color(0xFFFF4081), Color(0xFFFFD700), Color(0xFF00E5FF), Color(0xFF69F0AE), Color(0xFFE040FB))
    val xOffsets = listOf(-baseEyeW * 1.1f, -baseEyeW * 0.6f, baseEyeW * 0.7f, baseEyeW * 1.2f, -baseEyeW * 0.2f)
    for (i in 0 until 5) {
        val p = (living.loopMedium + i * 0.2f) % 1f
        val cX = centerX + xOffsets[i] + sin(p * 3f * PI.toFloat()) * 12.dp.toPx()
        val cY = (eyeY - baseEyeH * 0.6f) + p * (baseEyeH * 1.6f)
        val cRot = living.sparkleRot * (if (i % 2 == 0) 1f else -1f) + i * 45f
        rotate(cRot, pivot = Offset(cX, cY)) {
            drawRoundRect(
                colors[i % colors.size].copy(alpha = (1f - p * 0.3f) * effAlpha),
                topLeft = Offset(cX - 4.dp.toPx(), cY - 2.5.dp.toPx()),
                size = Size(8.dp.toPx(), 5.dp.toPx()),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
        }
    }
}

// ─── 15. DREAMING MOOD ────────────────────────────────────────────────────────

internal fun DrawScope.drawDreamingMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    zzzFloat: Float = 0f,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val calmCyan = Color(0xFF80DEEA).copy(alpha = effAlpha)

    // Closed calm curved eyes ⌒ ⌒
    drawSleepingEye(calmCyan, leftEyeX, eyeY, baseEyeW, baseEyeH, alpha = effAlpha)
    drawSleepingEye(calmCyan, rightEyeX, eyeY, baseEyeW, baseEyeH, alpha = effAlpha)

    // Smiling mouth centered between eyes (Sheet 2 Cell 38)
    drawSmileArc(calmCyan, centerX, eyeY + baseEyeH * 0.55f, 20.dp.toPx(), 6.dp.toPx(), alpha = effAlpha)

    // Fluffy dream cloud at top-left with gentle bobbing
    val cloudBobY = sin(living.loopSlow * 2f * PI.toFloat()) * 4.dp.toPx()
    drawDreamCloud(Color.White.copy(alpha = 0.85f * effAlpha), leftEyeX - baseEyeW * 0.5f, eyeY - baseEyeH * 0.8f + cloudBobY, 32.dp.toPx(), 18.dp.toPx(), effAlpha)

    // Fluffy dream cloud at bottom-right (Sheet 2 Cell 38)
    val cloud2BobY = cos(living.loopSlow * 2f * PI.toFloat()) * 3.dp.toPx()
    drawDreamCloud(Color.White.copy(alpha = 0.85f * effAlpha), rightEyeX + baseEyeW * 0.65f, eyeY + baseEyeH * 0.70f + cloud2BobY, 36.dp.toPx(), 20.dp.toPx(), effAlpha)

    // Star sparkles around
    rotate(living.sparkleRot, pivot = Offset(leftEyeX - baseEyeW * 0.7f, eyeY + baseEyeH * 0.3f)) {
        drawLooiSparkleStar(Color(0xFFFFD700), leftEyeX - baseEyeW * 0.7f, eyeY + baseEyeH * 0.3f, 8.dp.toPx(), effAlpha)
    }

    // Zzz floating on top-right smoothly along a curve
    for (i in 0..2) {
        val zProg = (living.loopMedium + i * 0.33f) % 1f
        val zX = rightEyeX + baseEyeW * 0.55f + (zProg * 35.dp.toPx()) + sin(zProg * 2f * PI.toFloat()) * 6.dp.toPx()
        val zY = eyeY - baseEyeH * 0.35f - (zProg * 45.dp.toPx())
        val zAlpha = (sin(zProg * PI.toFloat()) * effAlpha).coerceIn(0f, 1f)
        val zSize = (10.dp.toPx() + i * 3.dp.toPx()) * (0.8f + zProg * 0.4f)
        drawZLetter(calmCyan.copy(alpha = zAlpha), zX, zY, zSize)
    }
}

private fun DrawScope.drawDreamCloud(color: Color, cx: Float, cy: Float, w: Float, h: Float, alpha: Float) {
    drawCircle(color, radius = h * 0.55f, center = Offset(cx - w * 0.25f, cy))
    drawCircle(color, radius = h * 0.7f, center = Offset(cx, cy - h * 0.2f))
    drawCircle(color, radius = h * 0.5f, center = Offset(cx + w * 0.25f, cy))
}

// ─── 16. EXHAUSTED MOOD ───────────────────────────────────────────────────────

internal fun DrawScope.drawExhaustedMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Heavy panting heave offset
    val heaveY = living.pantHeaveY * 4.dp.toPx()

    // Heavy half-closed droopy eyes (droop more on exhale)
    val eyeDroop = baseEyeH * (0.42f + living.pantHeaveY * 0.08f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY + 4.dp.toPx() + heaveY, baseEyeW, eyeDroop, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY + 4.dp.toPx() + heaveY, baseEyeW, eyeDroop, alpha = effAlpha)

    // Heavy panting tongue hanging out mouth (enlarged & flapping in sync with breath cycle)
    val mouthY = eyeY + baseEyeH * 0.72f + heaveY
    drawFrownArc(cyanColor, centerX, mouthY, 28.dp.toPx(), 8.dp.toPx(), alpha = effAlpha)
    val pant = living.pantCycle * 8.dp.toPx()
    val tongueW = 18.dp.toPx()
    val tongueH = 20.dp.toPx() + pant * 1.4f
    val tongueRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
    drawRoundRect(
        color = Color(0xFFFF4081).copy(alpha = effAlpha),
        topLeft = Offset(centerX - tongueW / 2f, mouthY),
        size = Size(tongueW, tongueH),
        cornerRadius = tongueRadius
    )
    // Darker pink cleft line down center of tongue
    drawLine(
        color = Color(0xFFC2185B).copy(alpha = effAlpha),
        start = Offset(centerX, mouthY + 2.dp.toPx()),
        end = Offset(centerX, mouthY + tongueH - 6.dp.toPx()),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Sweat drop on right forehead dripping
    val sweatProg = living.loopMedium
    val sweatY = eyeY - baseEyeH * 0.5f + (sweatProg * 25.dp.toPx())
    drawSweatTeardrop(cyanColor.copy(alpha = (1f - sweatProg * 0.5f) * effAlpha), rightEyeX + baseEyeW * 0.5f, sweatY, 10.dp.toPx(), effAlpha)
}

// ─── 17. ELECTRIC MOOD ────────────────────────────────────────────────────────

internal fun DrawScope.drawElectricMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val electricColor = Color(0xFF00F5FF).copy(alpha = effAlpha)
    val shadowColor = Color(0xFF004D6B).copy(alpha = effAlpha)

    // Stepped arcade electric crackle jitter
    val stepIndex = (living.loopFast * 16f).toInt()
    val stepJitterX = ((stepIndex % 3) - 1) * 2.5.dp.toPx()
    val stepJitterY = (((stepIndex / 3) % 3) - 1) * 2.dp.toPx()
    val jitterW = baseEyeW * (if (stepIndex % 2 == 0) 1.05f else 0.92f)
    val jitterH = baseEyeH * (if (stepIndex % 3 == 0) 1.12f else 0.95f)

    drawLightningBolt(shadowColor, leftEyeX + stepJitterX, eyeY + 4.dp.toPx() + stepJitterY, jitterW, jitterH)
    drawLightningBolt(electricColor, leftEyeX + stepJitterX, eyeY + stepJitterY, jitterW, jitterH)

    drawLightningBolt(shadowColor, rightEyeX + stepJitterX, eyeY + 4.dp.toPx() + stepJitterY, jitterW, jitterH)
    drawLightningBolt(electricColor, rightEyeX + stepJitterX, eyeY + stepJitterY, jitterW, jitterH)

    // Crackling electric discharge sparks shooting around borders (Sheet 2 Cell 40)
    for (i in 0 until 6) {
        val angle = (i * 60f + living.sparkleRot * 0.4f) * (PI.toFloat() / 180f)
        val dist = baseEyeW * 1.35f
        val sx = centerX + cos(angle) * dist
        val sy = eyeY + sin(angle) * (baseEyeH * 0.85f)
        val sparkPath = Path().apply {
            moveTo(sx, sy)
            lineTo(sx + cos(angle + 0.3f) * 10.dp.toPx(), sy + sin(angle + 0.3f) * 10.dp.toPx())
            lineTo(sx + cos(angle - 0.2f) * 16.dp.toPx(), sy + sin(angle - 0.2f) * 16.dp.toPx())
            lineTo(sx + cos(angle + 0.1f) * 24.dp.toPx(), sy + sin(angle + 0.1f) * 24.dp.toPx())
        }
        drawPath(sparkPath, electricColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }

    // Rotating sparks
    rotate(living.sparkleRot, pivot = Offset(centerX, eyeY - baseEyeH * 0.6f)) {
        drawLooiSparkleStar(electricColor, centerX, eyeY - baseEyeH * 0.6f, 10.dp.toPx(), effAlpha)
    }
    rotate(-living.sparkleRot, pivot = Offset(leftEyeX - baseEyeW * 0.7f, eyeY)) {
        drawLooiSparkleStar(Color(0xFFFFEB3B).copy(alpha = effAlpha), leftEyeX - baseEyeW * 0.7f, eyeY, 8.dp.toPx(), effAlpha)
    }
    rotate(living.sparkleRot * 1.5f, pivot = Offset(rightEyeX + baseEyeW * 0.7f, eyeY)) {
        drawLooiSparkleStar(Color(0xFFFFEB3B).copy(alpha = effAlpha), rightEyeX + baseEyeW * 0.7f, eyeY, 8.dp.toPx(), effAlpha)
    }
}

private fun DrawScope.drawLightningBolt(color: Color, cx: Float, cy: Float, w: Float, h: Float) {
    val path = Path().apply {
        moveTo(cx + w * 0.15f, cy - h * 0.55f)
        lineTo(cx - w * 0.35f, cy)
        lineTo(cx + w * 0.05f, cy)
        lineTo(cx - w * 0.15f, cy + h * 0.55f)
        lineTo(cx + w * 0.35f, cy - 2.dp.toPx())
        lineTo(cx - w * 0.05f, cy - 2.dp.toPx())
        close()
    }
    drawPath(path, color)
}

// ─── 18. SNEAKY MOOD ──────────────────────────────────────────────────────────

internal fun DrawScope.drawSneakyMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Black bandit mask across visor
    val maskW = (rightEyeX - leftEyeX) + baseEyeW * 2.2f
    val maskH = baseEyeH * 1.35f
    val maskPath = Path().apply {
        moveTo(centerX - maskW * 0.55f, eyeY - maskH * 0.1f)
        cubicTo(centerX - maskW * 0.4f, eyeY - maskH * 0.5f, centerX + maskW * 0.4f, eyeY - maskH * 0.5f, centerX + maskW * 0.55f, eyeY - maskH * 0.1f)
        cubicTo(centerX + maskW * 0.5f, eyeY + maskH * 0.5f, centerX - maskW * 0.5f, eyeY + maskH * 0.5f, centerX - maskW * 0.55f, eyeY - maskH * 0.1f)
        close()
    }
    drawPath(maskPath, Color(0xFF263238).copy(alpha = 0.85f * effAlpha))
    drawPath(maskPath, Color(0xFF37474F).copy(alpha = effAlpha), style = Stroke(width = 2.dp.toPx()))

    // Tied ribbon knot and twin fluttering tails on right of mask (Sheet 2 Cell 41)
    val knotX = centerX + maskW * 0.46f
    val knotY = eyeY - maskH * 0.05f
    drawCircle(Color(0xFF263238).copy(alpha = effAlpha), radius = 5.dp.toPx(), center = Offset(knotX, knotY))
    drawCircle(Color(0xFF37474F).copy(alpha = effAlpha), radius = 5.dp.toPx(), center = Offset(knotX, knotY), style = Stroke(width = 1.5.dp.toPx()))
    val tailFlutter = sin(living.loopFast * 2f * PI.toFloat()) * 3.dp.toPx()
    val tailPath = Path().apply {
        moveTo(knotX, knotY)
        quadraticTo(knotX + 12.dp.toPx(), knotY - 6.dp.toPx() + tailFlutter, knotX + 22.dp.toPx(), knotY - 10.dp.toPx())
        moveTo(knotX, knotY)
        quadraticTo(knotX + 14.dp.toPx(), knotY + 8.dp.toPx() - tailFlutter, knotX + 26.dp.toPx(), knotY + 12.dp.toPx())
    }
    drawPath(tailPath, Color(0xFF263238).copy(alpha = effAlpha), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    drawPath(tailPath, Color(0xFF37474F).copy(alpha = effAlpha), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))

    // Eyes darting suspiciously sideways inside mask (periodic glance)
    val sneakyGazeX = 0.6f + (sin(living.loopMedium * 2f * PI.toFloat()) * 0.25f)
    val eyeH = baseEyeH * 0.55f * living.microBlinkFactor.coerceAtLeast(0.12f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY, baseEyeW * 0.8f, eyeH, gazeX = sneakyGazeX, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY, baseEyeW * 0.8f, eyeH, gazeX = sneakyGazeX, alpha = effAlpha)

    // Smirking small mouth
    val smirkY = eyeY + baseEyeH * 0.8f
    val smirkPath = Path().apply {
        moveTo(centerX - 8.dp.toPx(), smirkY)
        cubicTo(centerX, smirkY + 3.dp.toPx(), centerX + 10.dp.toPx(), smirkY - 2.dp.toPx(), centerX + 14.dp.toPx(), smirkY - 4.dp.toPx())
    }
    drawPath(smirkPath, cyanColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
}

// ─── 19. HERO MOOD ────────────────────────────────────────────────────────────

internal fun DrawScope.drawHeroMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Winged Superhero Domino Mask (Blue) with subtle light sweep
    val maskW = (rightEyeX - leftEyeX) + baseEyeW * 2.4f
    val maskH = baseEyeH * 1.4f
    val maskPath = Path().apply {
        moveTo(centerX - maskW * 0.5f, eyeY - maskH * 0.5f)
        lineTo(centerX - maskW * 0.15f, eyeY - maskH * 0.15f)
        lineTo(centerX, eyeY - maskH * 0.25f)
        lineTo(centerX + maskW * 0.15f, eyeY - maskH * 0.15f)
        lineTo(centerX + maskW * 0.5f, eyeY - maskH * 0.5f)
        lineTo(centerX + maskW * 0.4f, eyeY + maskH * 0.35f)
        lineTo(centerX, eyeY + maskH * 0.1f)
        lineTo(centerX - maskW * 0.4f, eyeY + maskH * 0.35f)
        close()
    }
    val heroBlue = Color(0xFF1565C0).copy(alpha = effAlpha)
    drawPath(maskPath, heroBlue)
    drawPath(maskPath, Color(0xFF42A5F5).copy(alpha = effAlpha), style = Stroke(width = 2.dp.toPx()))

    // Determined bright eyes inside mask with micro-blink
    val eyeH = baseEyeH * 0.85f * living.microBlinkFactor.coerceAtLeast(0.12f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY, baseEyeW * 0.85f, eyeH, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY, baseEyeW * 0.85f, eyeH, alpha = effAlpha)

    // Confident smiling mouth (Sheet 2 Cell 43)
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.55f, 20.dp.toPx(), 6.dp.toPx(), alpha = effAlpha)

    // Rotating golden sparkles around hero
    rotate(living.sparkleRot, pivot = Offset(leftEyeX - baseEyeW * 0.8f, eyeY - baseEyeH * 0.5f)) {
        drawLooiSparkleStar(Color(0xFFFFD700), leftEyeX - baseEyeW * 0.8f, eyeY - baseEyeH * 0.5f, 11.dp.toPx(), effAlpha)
    }
    rotate(-living.sparkleRot, pivot = Offset(rightEyeX + baseEyeW * 0.8f, eyeY - baseEyeH * 0.5f)) {
        drawLooiSparkleStar(Color(0xFFFFD700), rightEyeX + baseEyeW * 0.8f, eyeY - baseEyeH * 0.5f, 11.dp.toPx(), effAlpha)
    }
}

// ─── 20. GLITCHED MOOD ────────────────────────────────────────────────────────

internal fun DrawScope.drawGlitchedMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)
    val redColor = Color(0xFFFF1744).copy(alpha = 0.8f * effAlpha)

    // Base eyes with random-like micro digital displacement
    val jitterX = if (living.flickerPhase > 0.5f) 3.dp.toPx() else -3.dp.toPx()
    drawDualCircleEye(cyanColor, leftEyeX + jitterX, eyeY, baseEyeW, baseEyeH, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX + jitterX, eyeY, baseEyeW, baseEyeH, alpha = effAlpha)

    // Chromatic aberration / RGB shift offset slices jumping dynamically
    val sliceShift1 = sin(living.loopFast * 8f * PI.toFloat()) * 8.dp.toPx()
    val sliceShift2 = cos(living.loopFast * 8f * PI.toFloat()) * 8.dp.toPx()
    drawRoundRect(
        color = redColor,
        topLeft = Offset(leftEyeX - baseEyeW * 0.5f + sliceShift1, eyeY - 8.dp.toPx()),
        size = Size(baseEyeW, 6.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )
    drawRoundRect(
        color = redColor,
        topLeft = Offset(rightEyeX - baseEyeW * 0.5f + sliceShift2, eyeY + 4.dp.toPx()),
        size = Size(baseEyeW, 5.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )

    // Digital scanline bars moving vertically
    val barColor = Color.White.copy(alpha = 0.7f * effAlpha)
    val scanOffset = (living.loopFast * 24.dp.toPx())
    for (baseOffset in listOf(-20.dp.toPx(), -8.dp.toPx(), 4.dp.toPx(), 16.dp.toPx())) {
        val y = eyeY + ((baseOffset + scanOffset) % (baseEyeH * 1.5f)) - (baseEyeH * 0.75f)
        drawLine(
            color = barColor,
            start = Offset(leftEyeX - baseEyeW * 0.8f, y),
            end = Offset(rightEyeX + baseEyeW * 0.8f, y),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

// ─── 21. MAGIC MOOD ───────────────────────────────────────────────────────────

internal fun DrawScope.drawMagicMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Sparkling round eyes with micro blink
    val eyeH = baseEyeH * living.microBlinkFactor.coerceAtLeast(0.12f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY + 4.dp.toPx(), baseEyeW, eyeH, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY + 4.dp.toPx(), baseEyeW, eyeH, alpha = effAlpha)

    // Confident smiling mouth between eyes (Sheet 2 Cell 45)
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.55f, 20.dp.toPx(), 6.dp.toPx(), alpha = effAlpha)

    // Pointed Wizard Hat (Purple) sitting on forehead
    val hatBaseY = eyeY - baseEyeH * 0.48f
    val hatW = (rightEyeX - leftEyeX) + baseEyeW * 1.55f
    val hatH = 56.dp.toPx()

    // Curved brim across forehead
    val brimPath = Path().apply {
        moveTo(centerX - hatW * 0.5f, hatBaseY + 6.dp.toPx())
        cubicTo(centerX - hatW * 0.2f, hatBaseY - 3.dp.toPx(), centerX + hatW * 0.2f, hatBaseY - 3.dp.toPx(), centerX + hatW * 0.5f, hatBaseY + 6.dp.toPx())
        cubicTo(centerX + hatW * 0.2f, hatBaseY + 12.dp.toPx(), centerX - hatW * 0.2f, hatBaseY + 12.dp.toPx(), centerX - hatW * 0.5f, hatBaseY + 6.dp.toPx())
        close()
    }
    val purpleHat = Color(0xFF4A148C).copy(alpha = effAlpha)
    drawPath(brimPath, purpleHat)
    drawPath(brimPath, Color(0xFFAB47BC).copy(alpha = effAlpha), style = Stroke(width = 2.dp.toPx()))

    // Crooked Cone pointing to the right
    val coneW = hatW * 0.7f
    val wizCone = Path().apply {
        moveTo(centerX - coneW * 0.45f, hatBaseY + 2.dp.toPx())
        cubicTo(centerX - coneW * 0.35f, hatBaseY - hatH * 0.6f, centerX + coneW * 0.1f, hatBaseY - hatH * 0.75f, centerX + coneW * 0.35f, hatBaseY - hatH)
        cubicTo(centerX + coneW * 0.45f, hatBaseY - hatH * 0.9f, centerX + coneW * 0.3f, hatBaseY - hatH * 0.5f, centerX + coneW * 0.45f, hatBaseY + 2.dp.toPx())
        close()
    }
    drawPath(wizCone, purpleHat)
    drawPath(wizCone, Color(0xFFAB47BC).copy(alpha = effAlpha), style = Stroke(width = 2.dp.toPx()))

    // Golden hatband with stars
    val bandH = 7.dp.toPx()
    drawRoundRect(
        color = Color(0xFFFFD700).copy(alpha = effAlpha),
        topLeft = Offset(centerX - coneW * 0.44f, hatBaseY - bandH + 2.dp.toPx()),
        size = Size(coneW * 0.88f, bandH),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )
    drawLooiSparkleStar(cyanColor, centerX, hatBaseY - bandH * 0.5f + 2.dp.toPx(), 6.dp.toPx(), effAlpha)

    // Magic wand on bottom-right with casting arc motion and glowing star tip
    val wandX = rightEyeX + baseEyeW * 0.52f
    val wandY = eyeY + baseEyeH * 0.48f
    val wandAngle = living.wandArcAngle
    rotate(wandAngle, pivot = Offset(wandX, wandY)) {
        // Wand shaft
        val tipX = wandX - 10.dp.toPx()
        val tipY = wandY - 20.dp.toPx()
        val baseWandX = wandX + 16.dp.toPx()
        val baseWandY = wandY + 18.dp.toPx()
        drawLine(
            color = Color(0xFF5D4037).copy(alpha = effAlpha),
            start = Offset(baseWandX, baseWandY),
            end = Offset(tipX, tipY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Silver ferrule at tip
        drawCircle(Color(0xFFCFD8DC).copy(alpha = effAlpha), radius = 3.dp.toPx(), center = Offset(tipX, tipY))

        // Glowing Star tip emitting radiating sparkles scaled by casting pulse
        val starScale = 1f + 0.35f * abs(sin(wandAngle * PI.toFloat() / 30f))
        rotate(living.sparkleRot, pivot = Offset(tipX, tipY)) {
            drawLooiSparkleStar(Color(0xFFFFD700), tipX, tipY, 18.dp.toPx() * starScale, effAlpha)
            drawCircle(Color.White.copy(alpha = 0.8f * effAlpha), radius = 3.dp.toPx() * starScale, center = Offset(tipX, tipY))
        }
        rotate(-living.sparkleRot * 1.5f, pivot = Offset(tipX + 8.dp.toPx(), tipY - 8.dp.toPx())) {
            drawLooiSparkleStar(Color(0xFFE040FB), tipX + 8.dp.toPx(), tipY - 8.dp.toPx(), 9.dp.toPx() * starScale, effAlpha)
        }
    }
}

// ─── 22. SPORTY MOOD ──────────────────────────────────────────────────────────

internal fun DrawScope.drawSportyMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Striped Sweatband on forehead (Red / White / Blue) sitting right on forehead
    val bandW = (rightEyeX - leftEyeX) + baseEyeW * 1.25f
    val bandH = 14.dp.toPx()
    val bandY = eyeY - baseEyeH * 0.48f

    drawRoundRect(
        color = Color(0xFFD32F2F).copy(alpha = effAlpha),
        topLeft = Offset(centerX - bandW / 2f, bandY),
        size = Size(bandW, bandH * 0.35f),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
    )
    drawRoundRect(
        color = Color.White.copy(alpha = effAlpha),
        topLeft = Offset(centerX - bandW / 2f, bandY + bandH * 0.35f),
        size = Size(bandW, bandH * 0.3f),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFF1976D2).copy(alpha = effAlpha),
        topLeft = Offset(centerX - bandW / 2f, bandY + bandH * 0.65f),
        size = Size(bandW, bandH * 0.35f),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
    )

    // Determined eyes with micro blink
    val eyeH = baseEyeH * living.microBlinkFactor.coerceAtLeast(0.12f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY + 2.dp.toPx(), baseEyeW, eyeH, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY + 2.dp.toPx(), baseEyeW, eyeH, alpha = effAlpha)

    // Determined smiling mouth (Sheet 2 Cell 46)
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.55f, 20.dp.toPx(), 6.dp.toPx(), alpha = effAlpha)

    // Flying effort sweat droplets
    val sweatBob = sin(living.loopFast * 2f * PI.toFloat()) * 3.dp.toPx()
    drawCircle(cyanColor, radius = 3.dp.toPx(), center = Offset(centerX + baseEyeW * 0.8f, eyeY - baseEyeH * 0.35f + sweatBob))
    drawCircle(cyanColor.copy(alpha = 0.6f * effAlpha), radius = 2.dp.toPx(), center = Offset(centerX + baseEyeW * 0.95f, eyeY - baseEyeH * 0.45f + sweatBob))

    // Classic Black & White Soccer Ball ⚽ on bottom-left (Sheet 2 Cell 46)
    val ballX = leftEyeX - baseEyeW * 0.52f
    val ballY = eyeY + baseEyeH * 0.70f + sin(living.loopFast * 2f * PI.toFloat()) * 3.dp.toPx()
    val ballR = 20.dp.toPx()

    // White base circle
    drawCircle(Color.White.copy(alpha = effAlpha), radius = ballR, center = Offset(ballX, ballY))
    drawCircle(Color(0xFF263238).copy(alpha = effAlpha), radius = ballR, center = Offset(ballX, ballY), style = Stroke(width = 2.dp.toPx()))

    // Center black pentagon
    val pentagonPath = Path().apply {
        for (i in 0 until 5) {
            val angle = (i * 72f - 18f) * (PI.toFloat() / 180f)
            val px = ballX + cos(angle) * (ballR * 0.40f)
            val py = ballY + sin(angle) * (ballR * 0.40f)
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    drawPath(pentagonPath, Color(0xFF212121).copy(alpha = effAlpha))

    // Seams radiating from 5 pentagon vertices to edge patches
    for (i in 0 until 5) {
        val angle = (i * 72f - 18f) * (PI.toFloat() / 180f)
        val vX = ballX + cos(angle) * (ballR * 0.40f)
        val vY = ballY + sin(angle) * (ballR * 0.40f)
        val edgeX = ballX + cos(angle) * ballR
        val edgeY = ballY + sin(angle) * ballR
        drawLine(Color(0xFF212121).copy(alpha = effAlpha), Offset(vX, vY), Offset(edgeX, edgeY), strokeWidth = 1.5.dp.toPx())

        // Edge black triangular patches around perimeter
        val edgeAngle1 = angle - 0.22f
        val edgeAngle2 = angle + 0.22f
        val patchPath = Path().apply {
            moveTo(vX, vY)
            lineTo(ballX + cos(edgeAngle1) * ballR, ballY + sin(edgeAngle1) * ballR)
            lineTo(ballX + cos(edgeAngle2) * ballR, ballY + sin(edgeAngle2) * ballR)
            close()
        }
        drawPath(patchPath, Color(0xFF212121).copy(alpha = effAlpha))
    }
}

// ─── 23. SCIENTIST MOOD ───────────────────────────────────────────────────────

internal fun DrawScope.drawScientistMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Dual Round Lab Goggles (Sheet 2 Cell 47)
    val gogW = baseEyeW * 1.18f
    val gogH = baseEyeH * 1.15f
    // Left goggle lens frame
    drawRoundRect(
        color = Color(0xFF546E7A).copy(alpha = effAlpha),
        topLeft = Offset(leftEyeX - gogW * 0.5f, eyeY - gogH * 0.5f),
        size = Size(gogW, gogH),
        cornerRadius = CornerRadius(gogW * 0.45f, gogH * 0.45f),
        style = Stroke(width = 3.dp.toPx())
    )
    // Right goggle lens frame
    drawRoundRect(
        color = Color(0xFF546E7A).copy(alpha = effAlpha),
        topLeft = Offset(rightEyeX - gogW * 0.5f, eyeY - gogH * 0.5f),
        size = Size(gogW, gogH),
        cornerRadius = CornerRadius(gogW * 0.45f, gogH * 0.45f),
        style = Stroke(width = 3.dp.toPx())
    )
    // Connecting bridge over the nose
    val bridgeY = eyeY - 2.dp.toPx()
    drawLine(Color(0xFF546E7A).copy(alpha = effAlpha), Offset(leftEyeX + gogW * 0.5f, bridgeY), Offset(rightEyeX - gogW * 0.5f, bridgeY), strokeWidth = 3.5.dp.toPx())

    // Side elastic straps extending to head edges
    drawLine(Color(0xFF37474F).copy(alpha = effAlpha), Offset(leftEyeX - gogW * 0.5f, eyeY), Offset(leftEyeX - gogW * 0.5f - 16.dp.toPx(), eyeY), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
    drawLine(Color(0xFF37474F).copy(alpha = effAlpha), Offset(rightEyeX + gogW * 0.5f, eyeY), Offset(rightEyeX + gogW * 0.5f + 16.dp.toPx(), eyeY), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)

    // Eyes behind lenses with micro blink
    val eyeH = baseEyeH * 0.85f * living.microBlinkFactor.coerceAtLeast(0.12f)
    drawDualCircleEye(cyanColor, leftEyeX, eyeY, baseEyeW * 0.85f, eyeH, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX, eyeY, baseEyeW * 0.85f, eyeH, alpha = effAlpha)

    // Friendly smiling mouth (Sheet 2 Cell 47)
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.55f, 18.dp.toPx(), 5.dp.toPx(), alpha = effAlpha)

    // Chemistry Erlenmeyer Flask on bottom-right with bubbling green liquid (enlarged & detailed)
    val flaskX = rightEyeX + baseEyeW * 0.62f
    val flaskY = eyeY + baseEyeH * 0.68f
    val flaskW = 28.dp.toPx()
    val flaskH = 36.dp.toPx()

    // Conical flask path
    val flaskPath = Path().apply {
        moveTo(flaskX - flaskW * 0.22f, flaskY - flaskH * 0.5f)
        lineTo(flaskX + flaskW * 0.22f, flaskY - flaskH * 0.5f)
        lineTo(flaskX + flaskW * 0.22f, flaskY - flaskH * 0.15f)
        lineTo(flaskX + flaskW * 0.5f, flaskY + flaskH * 0.5f)
        lineTo(flaskX - flaskW * 0.5f, flaskY + flaskH * 0.5f)
        lineTo(flaskX - flaskW * 0.22f, flaskY - flaskH * 0.15f)
        close()
    }

    // Bubbling neon green liquid fill (bottom half)
    val liquidPath = Path().apply {
        moveTo(flaskX - flaskW * 0.38f, flaskY + flaskH * 0.1f)
        lineTo(flaskX + flaskW * 0.38f, flaskY + flaskH * 0.1f)
        lineTo(flaskX + flaskW * 0.48f, flaskY + flaskH * 0.48f)
        lineTo(flaskX - flaskW * 0.48f, flaskY + flaskH * 0.48f)
        close()
    }
    drawPath(liquidPath, Color(0xFF00E676).copy(alpha = 0.55f * effAlpha))

    // Flask glass outline
    drawPath(flaskPath, Color.White.copy(alpha = 0.9f * effAlpha), style = Stroke(width = 2.dp.toPx()))
    // Lip ring on neck
    drawLine(Color.White.copy(alpha = 0.9f * effAlpha), Offset(flaskX - flaskW * 0.28f, flaskY - flaskH * 0.5f), Offset(flaskX + flaskW * 0.28f, flaskY - flaskH * 0.5f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)

    // Measurement gradations on side of flask
    for (i in 0..2) {
        val gy = flaskY + flaskH * 0.15f + i * 5.dp.toPx()
        drawLine(Color.White.copy(alpha = 0.75f * effAlpha), Offset(flaskX - flaskW * 0.3f, gy), Offset(flaskX - flaskW * 0.18f, gy), strokeWidth = 1.5.dp.toPx())
    }

    // Rising effervescent animated bubbles
    for (i in 0 until 4) {
        val bProg = (living.loopFast + i * 0.25f) % 1f
        val bY = (flaskY + flaskH * 0.4f) - bProg * (flaskH * 0.65f)
        val bX = flaskX + (if (i % 2 == 0) 3.dp.toPx() else -3.dp.toPx())
        val bAlpha = ((1f - bProg * 0.6f) * effAlpha).coerceIn(0f, 1f)
        drawCircle(Color(0xFF69F0AE).copy(alpha = bAlpha), radius = 2.dp.toPx() * (1f + bProg * 0.5f), center = Offset(bX, bY))
    }
}

// ─── 24. SCARED MOOD ──────────────────────────────────────────────────────────

internal fun DrawScope.drawScaredMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Rapid terror trembling with living.shiverFast and nervously darting gaze
    val tremorX = living.shiverFastX
    val tremorY = living.shiverFastY
    val ghostGazeX = sin(living.loopSlow * 2f * PI.toFloat()) * 0.45f
    val eyeW = baseEyeW * 0.52f
    val eyeH = (baseEyeH * 0.52f) * (0.85f + 0.15f * living.microBlinkFactor)
    drawDualCircleEye(cyanColor, leftEyeX + tremorX, eyeY + tremorY, eyeW, eyeH, gazeX = ghostGazeX, alpha = effAlpha)
    drawDualCircleEye(cyanColor, rightEyeX + tremorX, eyeY + tremorY, eyeW, eyeH, gazeX = ghostGazeX, alpha = effAlpha)

    // Outer faint shudder circle pulsing
    val shudderPulse = 1f + sin(living.loopFast * 10f) * 0.15f
    drawCircle(cyanColor.copy(alpha = 0.25f * effAlpha), radius = baseEyeW * 0.6f * shudderPulse, center = Offset(leftEyeX + tremorX, eyeY + tremorY), style = Stroke(width = 1.5.dp.toPx()))
    drawCircle(cyanColor.copy(alpha = 0.25f * effAlpha), radius = baseEyeW * 0.6f * shudderPulse, center = Offset(rightEyeX + tremorX, eyeY + tremorY), style = Stroke(width = 1.5.dp.toPx()))

    // Shivering mouth with fast tremor and teeth chatter
    val mouthY = eyeY + baseEyeH * 0.65f + tremorY * 0.8f
    drawFrownArc(cyanColor, centerX + tremorX * 0.5f, mouthY, 14.dp.toPx(), 4.dp.toPx() + abs(sin(living.loopFast * 18f)) * 2.dp.toPx(), alpha = effAlpha)

    // Floating little ghost wisps bobbing vertically (enlarged & with cute eyes)
    val ghostBob1 = sin(living.loopSlow * 2f * PI.toFloat()) * 6.dp.toPx()
    val ghostBob2 = cos(living.loopSlow * 2f * PI.toFloat() + 1f) * 6.dp.toPx()
    drawGhostWisp(Color.White.copy(alpha = 0.8f * effAlpha), leftEyeX - baseEyeW * 0.72f, eyeY - baseEyeH * 0.35f + ghostBob1, 18.dp.toPx(), effAlpha)
    drawGhostWisp(Color.White.copy(alpha = 0.8f * effAlpha), rightEyeX + baseEyeW * 0.72f, eyeY - baseEyeH * 0.35f + ghostBob2, 18.dp.toPx(), effAlpha)
}

private fun DrawScope.drawGhostWisp(color: Color, cx: Float, cy: Float, size: Float, alpha: Float) {
    val path = Path().apply {
        moveTo(cx, cy - size * 0.5f)
        cubicTo(cx - size * 0.5f, cy - size * 0.2f, cx - size * 0.5f, cy + size * 0.35f, cx - size * 0.35f, cy + size * 0.45f)
        // Wavy tail at bottom
        quadraticTo(cx - size * 0.15f, cy + size * 0.35f, cx, cy + size * 0.45f)
        quadraticTo(cx + size * 0.15f, cy + size * 0.35f, cx + size * 0.35f, cy + size * 0.45f)
        cubicTo(cx + size * 0.5f, cy + size * 0.35f, cx + size * 0.5f, cy - size * 0.2f, cx, cy - size * 0.5f)
        close()
    }
    drawPath(path, color)
    // Tiny black ghost eyes
    drawCircle(Color(0xFF263238), radius = size * 0.08f, center = Offset(cx - size * 0.18f, cy - size * 0.1f))
    drawCircle(Color(0xFF263238), radius = size * 0.08f, center = Offset(cx + size * 0.18f, cy - size * 0.1f))
}

// ─── 25. WARRIOR MOOD ─────────────────────────────────────────────────────────

internal fun DrawScope.drawWarriorMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)

    // Red Headband sitting snugly on forehead
    val bandW = (rightEyeX - leftEyeX) + baseEyeW * 1.3f
    val bandH = 12.dp.toPx()
    val bandY = eyeY - baseEyeH * 0.48f

    drawRoundRect(
        color = Color(0xFFD32F2F).copy(alpha = effAlpha),
        topLeft = Offset(centerX - bandW / 2f, bandY),
        size = Size(bandW, bandH),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFFB71C1C).copy(alpha = effAlpha),
        topLeft = Offset(centerX - bandW / 2f, bandY),
        size = Size(bandW, bandH),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Golden circular crest (Maedate) on center forehead
    val crestCy = bandY + bandH * 0.5f
    drawCircle(Color(0xFFFFD700).copy(alpha = effAlpha), radius = 7.dp.toPx(), center = Offset(centerX, crestCy))
    drawCircle(Color(0xFFFFA000).copy(alpha = effAlpha), radius = 7.dp.toPx(), center = Offset(centerX, crestCy), style = Stroke(width = 1.5.dp.toPx()))
    drawCircle(Color(0xFFD32F2F).copy(alpha = effAlpha), radius = 2.5.dp.toPx(), center = Offset(centerX, crestCy))

    // Golden Kuwagata V-horns crest curving upward (Sheet 2 Cell 49)
    val hornPath = Path().apply {
        moveTo(centerX - 4.dp.toPx(), crestCy - 5.dp.toPx())
        cubicTo(centerX - 14.dp.toPx(), crestCy - 18.dp.toPx(), centerX - 28.dp.toPx(), crestCy - 28.dp.toPx(), centerX - 26.dp.toPx(), crestCy - 40.dp.toPx())
        cubicTo(centerX - 20.dp.toPx(), crestCy - 30.dp.toPx(), centerX - 8.dp.toPx(), crestCy - 20.dp.toPx(), centerX, crestCy - 10.dp.toPx())
        cubicTo(centerX + 8.dp.toPx(), crestCy - 20.dp.toPx(), centerX + 20.dp.toPx(), crestCy - 30.dp.toPx(), centerX + 26.dp.toPx(), crestCy - 40.dp.toPx())
        cubicTo(centerX + 28.dp.toPx(), crestCy - 28.dp.toPx(), centerX + 14.dp.toPx(), crestCy - 18.dp.toPx(), centerX + 4.dp.toPx(), crestCy - 5.dp.toPx())
        close()
    }
    drawPath(hornPath, Color(0xFFFFD700).copy(alpha = effAlpha))
    drawPath(hornPath, Color(0xFFFFA000).copy(alpha = effAlpha), style = Stroke(width = 1.5.dp.toPx()))

    // Side helmet neck guards (Fukikaeshi) on left & right
    val fukiW = 16.dp.toPx()
    val fukiH = 22.dp.toPx()
    val leftFlap = Path().apply {
        moveTo(centerX - bandW / 2f + 2.dp.toPx(), bandY + 4.dp.toPx())
        lineTo(centerX - bandW / 2f - fukiW, bandY + 10.dp.toPx())
        lineTo(centerX - bandW / 2f - fukiW * 0.8f, bandY + fukiH)
        lineTo(centerX - bandW / 2f + 4.dp.toPx(), bandY + bandH + 2.dp.toPx())
        close()
    }
    drawPath(leftFlap, Color(0xFF37474F).copy(alpha = effAlpha))
    drawPath(leftFlap, Color(0xFF78909C).copy(alpha = effAlpha), style = Stroke(width = 1.5.dp.toPx()))

    val rightFlap = Path().apply {
        moveTo(centerX + bandW / 2f - 2.dp.toPx(), bandY + 4.dp.toPx())
        lineTo(centerX + bandW / 2f + fukiW, bandY + 10.dp.toPx())
        lineTo(centerX + bandW / 2f + fukiW * 0.8f, bandY + fukiH)
        lineTo(centerX + bandW / 2f - 4.dp.toPx(), bandY + bandH + 2.dp.toPx())
        close()
    }
    drawPath(rightFlap, Color(0xFF37474F).copy(alpha = effAlpha))
    drawPath(rightFlap, Color(0xFF78909C).copy(alpha = effAlpha), style = Stroke(width = 1.5.dp.toPx()))

    // Headband tie ribbons fluttering in wind on left
    val ribbonFlutter1 = sin(living.loopFast * 2f * PI.toFloat()) * 5.dp.toPx()
    val ribbonFlutter2 = cos(living.loopFast * 2f * PI.toFloat()) * 5.dp.toPx()
    val ribbonPath = Path().apply {
        moveTo(centerX - bandW / 2f, bandY + 2.dp.toPx())
        quadraticTo(centerX - bandW / 2f - 14.dp.toPx(), bandY + 8.dp.toPx() + ribbonFlutter1, centerX - bandW / 2f - 28.dp.toPx(), bandY + 18.dp.toPx() + ribbonFlutter2)
        quadraticTo(centerX - bandW / 2f - 14.dp.toPx(), bandY + 14.dp.toPx() + ribbonFlutter1, centerX - bandW / 2f, bandY + bandH)
        close()
    }
    drawPath(ribbonPath, Color(0xFFD32F2F).copy(alpha = 0.95f * effAlpha))
    drawPath(ribbonPath, Color(0xFFB71C1C).copy(alpha = effAlpha), style = Stroke(width = 1.2.dp.toPx()))

    // Fierce narrow eyes with living micro-blink
    val blinkH = (baseEyeH * 0.7f) * (0.85f + 0.15f * living.microBlinkFactor)
    drawAngryEye(cyanColor, leftEyeX, eyeY + 2.dp.toPx(), baseEyeW, blinkH, isLeft = true, alpha = effAlpha)
    drawAngryEye(cyanColor, rightEyeX, eyeY + 2.dp.toPx(), baseEyeW, blinkH, isLeft = false, alpha = effAlpha)

    // Confident smiling mouth (Sheet 2 Cell 49)
    drawSmileArc(cyanColor, centerX, eyeY + baseEyeH * 0.55f, 20.dp.toPx(), 6.dp.toPx(), alpha = effAlpha)

    // Dual Katana Swords: One on each side held in hands (ดาบไปอยู่ ข้างละอัน เหมือนกำลังถือดาบ)
    val combatBob = sin(living.loopMedium * 2f * PI.toFloat()) * 3.dp.toPx()
    val swordLen = 54.dp.toPx()
    val bladeColor = Color(0xFFCFD8DC).copy(alpha = effAlpha)
    val bladeCoreColor = Color(0xFFECEFF1).copy(alpha = effAlpha)
    val guardColor = Color(0xFFFFD700).copy(alpha = effAlpha)
    val handleColor = Color(0xFF37474F).copy(alpha = effAlpha)

    // Left Sword (held on the left, angled outward-upward at ~25°)
    val leftHiltX = leftEyeX - baseEyeW * 0.68f
    val leftHiltY = eyeY + baseEyeH * 0.70f + combatBob
    val leftAngleRad = (-115f * PI / 180f).toFloat() // points up and slightly outward to the left
    val leftTipX = leftHiltX + cos(leftAngleRad) * swordLen
    val leftTipY = leftHiltY + sin(leftAngleRad) * swordLen
    val leftHandleStartX = leftHiltX - cos(leftAngleRad) * 14.dp.toPx()
    val leftHandleStartY = leftHiltY - sin(leftAngleRad) * 14.dp.toPx()

    // Right Sword (held on the right, angled outward-upward at ~25°)
    val rightHiltX = rightEyeX + baseEyeW * 0.68f
    val rightHiltY = eyeY + baseEyeH * 0.70f - combatBob
    val rightAngleRad = (-65f * PI / 180f).toFloat() // points up and slightly outward to the right
    val rightTipX = rightHiltX + cos(rightAngleRad) * swordLen
    val rightTipY = rightHiltY + sin(rightAngleRad) * swordLen
    val rightHandleStartX = rightHiltX - cos(rightAngleRad) * 14.dp.toPx()
    val rightHandleStartY = rightHiltY - sin(rightAngleRad) * 14.dp.toPx()

    // Draw Left Sword
    // Handle
    drawLine(handleColor, Offset(leftHandleStartX, leftHandleStartY), Offset(leftHiltX, leftHiltY), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
    // Tsuba (guard)
    drawCircle(guardColor, radius = 5.dp.toPx(), center = Offset(leftHiltX, leftHiltY))
    // Blade
    drawLine(bladeColor, Offset(leftHiltX, leftHiltY), Offset(leftTipX, leftTipY), strokeWidth = 3.8.dp.toPx(), cap = StrokeCap.Round)
    drawLine(bladeCoreColor, Offset(leftHiltX, leftHiltY), Offset(leftTipX, leftTipY), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
    // Cyber Robot Paw holding the sword hilt
    drawRobotPaw(center = Offset(leftHiltX, leftHiltY), radius = 10.dp.toPx(), angleDeg = -25f, alpha = effAlpha)

    // Draw Right Sword
    // Handle
    drawLine(handleColor, Offset(rightHandleStartX, rightHandleStartY), Offset(rightHiltX, rightHiltY), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
    // Tsuba (guard)
    drawCircle(guardColor, radius = 5.dp.toPx(), center = Offset(rightHiltX, rightHiltY))
    // Blade
    drawLine(bladeColor, Offset(rightHiltX, rightHiltY), Offset(rightTipX, rightTipY), strokeWidth = 3.8.dp.toPx(), cap = StrokeCap.Round)
    drawLine(bladeCoreColor, Offset(rightHiltX, rightHiltY), Offset(rightTipX, rightTipY), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
    // Cyber Robot Paw holding the sword hilt
    drawRobotPaw(center = Offset(rightHiltX, rightHiltY), radius = 10.dp.toPx(), angleDeg = 25f, alpha = effAlpha)

    // Glint stars slide along both blades using living.bladeShineProg
    val glintProg = living.bladeShineProg
    if (glintProg > 0f) {
        val glintDist = glintProg * swordLen * 0.85f
        val glintLeft = Offset(leftHiltX + cos(leftAngleRad) * glintDist, leftHiltY + sin(leftAngleRad) * glintDist)
        val glintRight = Offset(rightHiltX + cos(rightAngleRad) * glintDist, rightHiltY + sin(rightAngleRad) * glintDist)
        val glintAlpha = (sin(glintProg * PI.toFloat()) * effAlpha).coerceIn(0f, 1f)
        drawLooiSparkleStar(Color.White.copy(alpha = glintAlpha), glintLeft.x, glintLeft.y, 9.dp.toPx(), effAlpha)
        drawLooiSparkleStar(Color.White.copy(alpha = glintAlpha), glintRight.x, glintRight.y, 9.dp.toPx(), effAlpha)
    }
}

// ─── 26. LOW BATTERY MOOD ─────────────────────────────────────────────────────

internal fun DrawScope.drawLowBatteryMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val brownout = living.brownoutAlpha
    val dimCyan = Color(0xFF00838F).copy(alpha = effAlpha * brownout)
    val redPulse = 0.4f + 0.6f * living.pulse
    val redColor = Color(0xFFFF1744).copy(alpha = effAlpha * redPulse)

    // Tired dying half-shut eyes (drooping lids with intermittent brownout flicker)
    val droopH = baseEyeH * (0.26f * brownout + 0.05f * sin(living.loopSlow * 2f * PI.toFloat()))
    val droopDrop = (1f - brownout) * 3.dp.toPx()
    drawDualCircleEye(dimCyan, leftEyeX, eyeY + 6.dp.toPx() + droopDrop, baseEyeW, droopH, alpha = effAlpha * brownout)
    drawDualCircleEye(dimCyan, rightEyeX, eyeY + 6.dp.toPx() + droopDrop, baseEyeW, droopH, alpha = effAlpha * brownout)

    // Fading dark grey sparkle ✦ above left eye (Sheet 2 Cell 50)
    drawLooiSparkleStar(Color(0xFF455A64).copy(alpha = 0.75f * effAlpha), leftEyeX - baseEyeW * 0.15f, eyeY - baseEyeH * 0.75f, 13.dp.toPx(), effAlpha)

    // Red empty battery icon on top-right (enlarged & clear)
    val batX = rightEyeX + baseEyeW * 0.65f
    val batY = eyeY - baseEyeH * 0.45f
    val batW = 40.dp.toPx()
    val batH = 22.dp.toPx()

    // Battery body outline
    drawRoundRect(
        color = redColor,
        topLeft = Offset(batX - batW / 2f, batY - batH / 2f),
        size = Size(batW, batH),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        style = Stroke(width = 2.5.dp.toPx())
    )
    // Battery terminal nipple on right
    drawRoundRect(
        color = redColor,
        topLeft = Offset(batX + batW / 2f, batY - 4.dp.toPx()),
        size = Size(4.dp.toPx(), 8.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )

    // Critical 1-bar red level inside flashing
    if (living.flickerPhase > 0.3f) {
        drawRoundRect(
            color = Color(0xFFFF1744).copy(alpha = effAlpha),
            topLeft = Offset(batX - batW / 2f + 4.dp.toPx(), batY - batH / 2f + 4.dp.toPx()),
            size = Size(8.dp.toPx(), batH - 8.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }

    // Warning exclamation mark beside battery
    if (living.pulse > 0.5f) {
        val exX = batX - batW / 2f - 10.dp.toPx()
        drawLine(
            color = redColor,
            start = Offset(exX, batY - batH * 0.4f),
            end = Offset(exX, batY + batH * 0.1f),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(redColor, radius = 1.8.dp.toPx(), center = Offset(exX, batY + batH * 0.35f))
    }
}

// ─── ENHANCEMENTS FOR EXISTING MOODS ──────────────────────────────────────────

/**
 * Enhanced Thought Bubble for THINKING mood
 */
internal fun DrawScope.drawThoughtBubble(
    color: Color,
    centerX: Float, centerY: Float,
    size: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val effColor = color.copy(alpha = effAlpha)

    // Soft floating bob
    val bobY = sin(living.loopSlow * 2f * PI.toFloat()) * 3.dp.toPx()
    val effCenterY = centerY + bobY

    // Little connector circles
    drawCircle(effColor, radius = size * 0.12f, center = Offset(centerX - size * 0.3f, effCenterY + size * 0.4f))
    drawCircle(effColor, radius = size * 0.18f, center = Offset(centerX - size * 0.15f, effCenterY + size * 0.2f))

    // Main fluffy cloud with subtle breathing scale
    val cloudScale = 1f + living.pulse * 0.04f
    val cSize = size * cloudScale
    drawCircle(effColor, radius = cSize * 0.32f, center = Offset(centerX, effCenterY))
    drawCircle(effColor, radius = cSize * 0.28f, center = Offset(centerX + cSize * 0.3f, effCenterY - cSize * 0.05f))
    drawCircle(effColor, radius = cSize * 0.25f, center = Offset(centerX - cSize * 0.25f, effCenterY - cSize * 0.05f))
    drawCircle(effColor, radius = cSize * 0.22f, center = Offset(centerX + cSize * 0.15f, effCenterY - cSize * 0.25f))
    drawCircle(effColor, radius = cSize * 0.22f, center = Offset(centerX - cSize * 0.15f, effCenterY - cSize * 0.25f))

    // Three dots inside cycling brightness
    for (i in 0 until 3) {
        val dotAlpha = (0.25f + 0.45f * sin((living.loopMedium * 2f * PI.toFloat()) + i * 1.5f).coerceAtLeast(0f)) * effAlpha
        val dx = (i - 1) * cSize * 0.15f
        drawCircle(Color.Black.copy(alpha = dotAlpha), radius = cSize * 0.05f, center = Offset(centerX + dx, effCenterY - cSize * 0.05f))
    }
}

/**
 * Inverted Question Mark (¿) for CONFUSED mood
 */
internal fun DrawScope.drawInvertedQuestionMark(
    color: Color,
    centerX: Float,
    centerY: Float,
    height: Float,
    alpha: Float = 1f
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val effColor = color.copy(alpha = effAlpha)
    // Dot at top
    drawCircle(effColor, radius = 3.5f, center = Offset(centerX, centerY - height * 0.4f))
    // Curve at bottom
    val qPath = Path().apply {
        moveTo(centerX, centerY - height * 0.25f)
        lineTo(centerX, centerY)
        cubicTo(centerX, centerY + height * 0.25f, centerX + height * 0.3f, centerY + height * 0.25f, centerX + height * 0.3f, centerY + height * 0.4f)
        cubicTo(centerX + height * 0.3f, centerY + height * 0.55f, centerX - height * 0.3f, centerY + height * 0.55f, centerX - height * 0.3f, centerY + height * 0.4f)
    }
    drawPath(qPath, effColor, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
}

/**
 * Red Rose prop for LOVE / Romantic enhancement (detailed layered blossom, thorns & leaf)
 */
internal fun DrawScope.drawRoseProp(
    centerX: Float, centerY: Float,
    size: Float = 26.dp.toPx(),
    alpha: Float = 1f
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val roseRed = Color(0xFFFF1744).copy(alpha = effAlpha)
    val darkRed = Color(0xFFC2185B).copy(alpha = effAlpha)
    val stemGreen = Color(0xFF43A047).copy(alpha = effAlpha)

    // Curved green stem angled down-left
    val stemPath = Path().apply {
        moveTo(centerX, centerY)
        quadraticTo(centerX - size * 0.6f, centerY + size * 0.4f, centerX - size * 1.3f, centerY + size * 0.9f)
    }
    drawPath(stemPath, stemGreen, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

    // Small thorn on stem
    val thornPath = Path().apply {
        moveTo(centerX - size * 0.5f, centerY + size * 0.35f)
        lineTo(centerX - size * 0.65f, centerY + size * 0.45f)
        lineTo(centerX - size * 0.55f, centerY + size * 0.5f)
        close()
    }
    drawPath(thornPath, stemGreen)

    // Green serrated leaf
    val leafPath = Path().apply {
        moveTo(centerX - size * 0.7f, centerY + size * 0.5f)
        cubicTo(centerX - size * 1.1f, centerY + size * 0.3f, centerX - size * 1.3f, centerY + size * 0.6f, centerX - size * 0.9f, centerY + size * 0.7f)
        close()
    }
    drawPath(leafPath, stemGreen)
    drawPath(leafPath, Color(0xFF2E7D32).copy(alpha = effAlpha), style = Stroke(width = 1.2.dp.toPx()))

    // Green calyx / sepal under blossom
    val sepalPath = Path().apply {
        moveTo(centerX - size * 0.35f, centerY + size * 0.15f)
        lineTo(centerX, centerY + size * 0.28f)
        lineTo(centerX + size * 0.35f, centerY + size * 0.15f)
        lineTo(centerX, centerY)
        close()
    }
    drawPath(sepalPath, stemGreen)

    // Layered red rose blossom petals
    // Outer petal lobes
    drawCircle(roseRed, radius = size * 0.48f, center = Offset(centerX, centerY))
    drawCircle(darkRed, radius = size * 0.48f, center = Offset(centerX, centerY), style = Stroke(width = 1.8.dp.toPx()))

    // Inner petal arcs
    val innerPetal = Path().apply {
        moveTo(centerX - size * 0.25f, centerY - size * 0.1f)
        cubicTo(centerX - size * 0.3f, centerY - size * 0.35f, centerX + size * 0.3f, centerY - size * 0.35f, centerX + size * 0.25f, centerY - size * 0.1f)
        cubicTo(centerX + size * 0.25f, centerY + size * 0.25f, centerX - size * 0.25f, centerY + size * 0.25f, centerX - size * 0.25f, centerY - size * 0.1f)
        close()
    }
    drawPath(innerPetal, Color(0xFFFF5252).copy(alpha = effAlpha))

    // Center spiral bud core
    drawCircle(Color(0xFFD50000).copy(alpha = effAlpha), radius = size * 0.2f, center = Offset(centerX, centerY - 1.dp.toPx()))
    drawCircle(Color.White.copy(alpha = 0.6f * effAlpha), radius = 2.dp.toPx(), center = Offset(centerX - 1.5.dp.toPx(), centerY - 2.5.dp.toPx()))
}

// ─── 27. ROMANTIC MOOD (Cell 22: Romantic) ───────────────────────────────────

internal fun DrawScope.drawRomanticMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState(),
    includeEyes: Boolean = true
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)
    val pinkColor = Color(0xFFFF4081).copy(alpha = effAlpha)

    if (includeEyes) {
        val heartSize = baseEyeW * 1.15f * living.heartbeatScale
        // Left eye: Full heart eye ♥
        drawHeart(pinkColor, leftEyeX, eyeY, heartSize, alpha = effAlpha)

        // Right eye: Heart eye with flirty wink 😉
        val winkScaleY = living.winkEyeScaleY
        val isWinkClosed = winkScaleY <= 0.20f
        if (isWinkClosed) {
            drawSleepingEye(pinkColor, rightEyeX, eyeY, baseEyeW * 1.05f, barHeight = 14.dp.toPx(), alpha = effAlpha)
        } else {
            drawHeart(pinkColor, rightEyeX, eyeY, heartSize * winkScaleY.coerceAtLeast(0.35f), alpha = effAlpha)
        }

        // Star sparkle pop on wink
        if (living.winkSparkleScale > 0.05f) {
            drawLooiSparkleStar(Color(0xFFFFD700), rightEyeX + baseEyeW * 0.45f, eyeY - baseEyeH * 0.45f, 22.dp.toPx() * living.winkSparkleScale, effAlpha)
        }
    }

    // Pink Blush cheeks pulsing warmly
    val blushPulse = 0.7f + 0.3f * living.pulse
    drawLooiPinkBlush(leftEyeX - baseEyeW * 0.5f, eyeY + baseEyeH * 0.45f, alpha = effAlpha * blushPulse)
    drawLooiPinkBlush(rightEyeX + baseEyeW * 0.5f, eyeY + baseEyeH * 0.45f, alpha = effAlpha * blushPulse)

    // Pucker kiss mouth '3'
    val mouthY = eyeY + baseEyeH * 0.68f
    val puckerPath = Path().apply {
        moveTo(centerX - 8.dp.toPx(), mouthY - 6.dp.toPx())
        cubicTo(centerX - 1.dp.toPx(), mouthY - 6.dp.toPx(), centerX + 4.dp.toPx(), mouthY - 3.dp.toPx(), centerX - 1.dp.toPx(), mouthY)
        cubicTo(centerX + 4.dp.toPx(), mouthY + 3.dp.toPx(), centerX - 1.dp.toPx(), mouthY + 6.dp.toPx(), centerX - 8.dp.toPx(), mouthY + 6.dp.toPx())
    }
    drawPath(puckerPath, cyanColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))

    // Red Rose held in mouth gently swaying
    val roseAngle = sin(living.loopSlow * 2f * PI.toFloat()) * 4f
    val rosePivot = Offset(centerX + 6.dp.toPx(), mouthY)
    rotate(roseAngle, pivot = rosePivot) {
        drawRoseProp(centerX + 24.dp.toPx(), mouthY + 2.dp.toPx(), 26.dp.toPx(), effAlpha)
    }

    // Floating heart on top-right gently bobbing and beating
    val heartBob = sin(living.loopMedium * 2f * PI.toFloat()) * 5.dp.toPx()
    val heartPulse = 1f + living.pulse * 0.15f
    scale(heartPulse, heartPulse, pivot = Offset(rightEyeX + baseEyeW * 0.8f, eyeY - baseEyeH * 0.55f + heartBob)) {
        drawHeart(pinkColor, rightEyeX + baseEyeW * 0.8f, eyeY - baseEyeH * 0.55f + heartBob, 16.dp.toPx(), effAlpha)
    }
}

// ─── 28. CONFUSED MOOD (Cell 1: Confused - Wavy Eyes ~ ~) ──────────────────────

internal fun DrawScope.drawConfusedMood(
    centerX: Float, centerY: Float,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    baseEyeW: Float, baseEyeH: Float,
    alpha: Float = 1f,
    living: LivingMotionState = LivingMotionState()
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val cyanColor = Color(0xFF00F5FF).copy(alpha = effAlpha)
    val redColor = Color(0xFFFF5252).copy(alpha = effAlpha)

    // Dynamic undulating Wavy eyes ~ ~
    val wavePhase = living.loopMedium * 2f * PI.toFloat()
    fun drawWavyEye(cx: Float, cy: Float, phaseOffset: Float) {
        val w = baseEyeW * 0.9f
        val amp1 = 8.dp.toPx() + sin(wavePhase + phaseOffset) * 2.dp.toPx()
        val amp2 = 8.dp.toPx() - sin(wavePhase + phaseOffset) * 2.dp.toPx()
        val wavePath = Path().apply {
            moveTo(cx - w * 0.45f, cy)
            cubicTo(cx - w * 0.25f, cy - amp1, cx - w * 0.1f, cy - amp1, cx, cy)
            cubicTo(cx + w * 0.1f, cy + amp2, cx + w * 0.25f, cy + amp2, cx + w * 0.45f, cy)
        }
        drawPath(wavePath, Color(0xFF004D6B).copy(alpha = effAlpha), style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round))
        drawPath(wavePath, cyanColor, style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round))
    }

    drawWavyEye(leftEyeX, eyeY, 0f)
    drawWavyEye(rightEyeX, eyeY, PI.toFloat() * 0.5f)

    // Wobbling inverted ¿ on bottom left
    val qWobble1 = sin(living.loopSlow * 2f * PI.toFloat()) * 3.dp.toPx()
    drawInvertedQuestionMark(Color(0xFF80DEEA), leftEyeX - baseEyeW * 0.65f, eyeY + baseEyeH * 0.65f + qWobble1, baseEyeH * 0.45f, alpha = effAlpha)

    // Dual red ?? on top right bobbing
    val qWobble2 = cos(living.loopSlow * 2f * PI.toFloat()) * 3.dp.toPx()
    drawQuestionMark(redColor, rightEyeX + baseEyeW * 0.55f, eyeY - baseEyeH * 0.85f + qWobble2, baseEyeH * 0.65f, alpha = effAlpha)
    drawQuestionMark(redColor.copy(alpha = 0.85f * effAlpha), rightEyeX + baseEyeW * 0.85f, eyeY - baseEyeH * 0.65f + qWobble1, baseEyeH * 0.55f, alpha = effAlpha)

    // Wavy mouth rippling
    val mouthY = eyeY + baseEyeH * 0.7f
    val mouthW = 20.dp.toPx()
    val mouthAmp = 4.dp.toPx() + sin(wavePhase) * 1.5.dp.toPx()
    val mouthWave = Path().apply {
        moveTo(centerX - mouthW * 0.5f, mouthY)
        cubicTo(centerX - mouthW * 0.25f, mouthY - mouthAmp, centerX - mouthW * 0.1f, mouthY - mouthAmp, centerX, mouthY)
        cubicTo(centerX + mouthW * 0.1f, mouthY + mouthAmp, centerX + mouthW * 0.25f, mouthY + mouthAmp, centerX + mouthW * 0.5f, mouthY)
    }
    drawPath(mouthWave, cyanColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
}


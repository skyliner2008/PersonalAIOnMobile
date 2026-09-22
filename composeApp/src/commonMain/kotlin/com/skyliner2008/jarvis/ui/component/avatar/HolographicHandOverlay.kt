package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import com.skyliner2008.jarvis.pet.HoloHandGesture
import kotlin.math.*

/**
 * HolographicHandOverlay — มือโฮโลแกรม Wireframe สีฟ้าเรืองแสง
 *
 * ปรากฏเมื่อ User สัมผัส Pet ตามตำแหน่ง:
 * - STROKE: ฝ่ามือลูบจากซ้ายไปขวา (หน้าผาก)
 * - POKE: นิ้วชี้จิ้ม (แก้ม)
 * - CHIN_SCRATCH: นิ้วเกาคาง
 * - TICKLE: นิ้วกระดิก wiggle (แก้ม double-tap)
 * - PAT: ฝ่ามือตบเบาๆ (กลาง)
 * - WAVE: มือโบก bye
 *
 * สไตล์: Sci-Fi Wireframe สีฟ้า Cyan (#00F0FF) เรืองแสง
 * - เส้นขอบมือเรืองแสง (glow blur)
 * - Particle sparkle trail ตามปลายนิ้ว
 * - Alpha fade-in / action / fade-out animation
 * - Duration: 800ms - 1200ms per gesture
 */
@Composable
fun HolographicHandOverlay(
    gesture: HoloHandGesture?,
    touchPosition: Offset,
    isFromLeft: Boolean = false,
    triggerId: Long = 0L,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (gesture == null) return

    // ─── Animation State ──────────────────────────────────────────────────
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(gesture, touchPosition, triggerId) {
        animatable.snapTo(0f)
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = when (gesture) {
                    HoloHandGesture.STROKE -> 1200
                    HoloHandGesture.CHIN_SCRATCH -> 1100
                    HoloHandGesture.TICKLE -> 1000
                    HoloHandGesture.POKE -> 800
                    HoloHandGesture.PAT -> 900
                    HoloHandGesture.WAVE -> 1200
                },
                easing = LinearEasing
            )
        )
        onFinished()
    }

    val progress = animatable.value

    // Alpha: fade-in (0-20%), action (20-80%), fade-out (80-100%)
    val alpha = when {
        progress < 0.15f -> (progress / 0.15f) * 0.75f
        progress > 0.80f -> ((1f - progress) / 0.20f) * 0.75f
        else -> 0.75f
    }

    if (alpha <= 0.01f) return

    // ─── Sparkle Particles ────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "holoSparkle")
    val sparklePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "sparklePhase"
    )

    val holoColor = Color(0xFF00F0FF)
    val glowColor = Color(0xFF00F0FF).copy(alpha = 0.3f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = touchPosition.x.coerceIn(0f, size.width)
        val cy = touchPosition.y.coerceIn(0f, size.height)

        // Scale proportional to canvas size (calibrated for mobile screens)
        val handScale = minOf(size.width, size.height) * 0.0024f

        when (gesture) {
            HoloHandGesture.STROKE -> drawStrokeHand(cx, cy, progress, alpha, handScale, holoColor, glowColor, isFromLeft)
            HoloHandGesture.POKE -> drawPokeHand(cx, cy, progress, alpha, handScale, holoColor, glowColor, isFromLeft)
            HoloHandGesture.CHIN_SCRATCH -> drawChinScratchHand(cx, cy, progress, alpha, handScale, holoColor, glowColor)
            HoloHandGesture.TICKLE -> drawTickleHand(cx, cy, progress, alpha, handScale, holoColor, glowColor)
            HoloHandGesture.PAT -> drawPatHand(cx, cy, progress, alpha, handScale, holoColor, glowColor)
            HoloHandGesture.WAVE -> drawWaveHand(cx, cy, progress, alpha, handScale, holoColor, glowColor)
        }

        // ─── Sparkle Particles at fingertips ─────────────────────────────
        drawSparkles(cx, cy, alpha, sparklePhase, handScale, holoColor)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ─── HAND GESTURE DRAWING FUNCTIONS ─────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * STROKE — ฝ่ามือลูบไล้จากข้างหนึ่งไปอีกข้าง
 */
private fun DrawScope.drawStrokeHand(
    cx: Float, cy: Float, progress: Float, alpha: Float,
    scale: Float, color: Color, glow: Color, isFromLeft: Boolean
) {
    val handW = 120f * scale
    val handH = 80f * scale

    // Sweep motion: move hand left-to-right (or right-to-left)
    val actionProgress = ((progress - 0.15f) / 0.65f).coerceIn(0f, 1f)
    val sweepX = if (isFromLeft) {
        cx - handW + actionProgress * handW * 2f
    } else {
        cx + handW - actionProgress * handW * 2f
    }

    val effectiveColor = color.copy(alpha = alpha)
    val effectiveGlow = glow.copy(alpha = alpha * 0.5f)

    // Glow layer
    drawOval(
        color = effectiveGlow,
        topLeft = Offset(sweepX - handW * 0.6f, cy - handH * 0.5f),
        size = Size(handW * 1.2f, handH),
        style = Fill
    )

    // Open palm shape — 5 fingers spread
    val fingerLen = handH * 0.7f
    val fingerW = 6f * scale
    val startY = cy - handH * 0.3f
    val spacing = handW * 0.2f

    for (i in 0..4) {
        val fx = sweepX - handW * 0.4f + i * spacing
        val bendAngle = (i - 2) * 5f // Slight fan out
        val endY = startY - fingerLen * cos(Math.toRadians(bendAngle.toDouble())).toFloat()
        val endX = fx + fingerLen * sin(Math.toRadians(bendAngle.toDouble())).toFloat()

        // Glow
        drawLine(effectiveGlow, Offset(fx, startY), Offset(endX, endY), fingerW * 2.5f, StrokeCap.Round)
        // Wireframe
        drawLine(effectiveColor, Offset(fx, startY), Offset(endX, endY), fingerW, StrokeCap.Round)
    }

    // Palm base
    drawLine(
        effectiveColor,
        Offset(sweepX - handW * 0.4f, startY),
        Offset(sweepX + handW * 0.4f, startY),
        fingerW * 1.5f,
        StrokeCap.Round
    )
    // Wrist line
    drawLine(
        effectiveColor,
        Offset(sweepX - handW * 0.2f, startY + handH * 0.3f),
        Offset(sweepX + handW * 0.2f, startY + handH * 0.3f),
        fingerW,
        StrokeCap.Round
    )
}

/**
 * POKE — นิ้วชี้เดี่ยวจิ้ม (push-and-release)
 */
private fun DrawScope.drawPokeHand(
    cx: Float, cy: Float, progress: Float, alpha: Float,
    scale: Float, color: Color, glow: Color, isFromLeft: Boolean
) {
    val fingerLen = 100f * scale
    val fingerW = 7f * scale
    val effectiveColor = color.copy(alpha = alpha)
    val effectiveGlow = glow.copy(alpha = alpha * 0.5f)

    // Poke motion: finger extends toward target then retracts
    val actionProgress = ((progress - 0.15f) / 0.65f).coerceIn(0f, 1f)
    val pokeDepth = sin(actionProgress * PI.toFloat()) * fingerLen * 0.4f

    val dirX = if (isFromLeft) 1f else -1f
    val startX = cx + dirX * (fingerLen + 20f * scale) - dirX * pokeDepth
    val startY = cy + 10f * scale
    val tipX = cx + dirX * 10f * scale
    val tipY = cy

    // Glow ring at poke point
    if (actionProgress in 0.3f..0.7f) {
        val ringAlpha = sin((actionProgress - 0.3f) / 0.4f * PI.toFloat()) * alpha * 0.4f
        drawCircle(
            color = color.copy(alpha = ringAlpha),
            radius = 25f * scale,
            center = Offset(tipX, tipY),
            style = Stroke(width = 3f * scale)
        )
    }

    // Finger line (index finger pointing)
    drawLine(effectiveGlow, Offset(startX, startY), Offset(tipX, tipY), fingerW * 3f, StrokeCap.Round)
    drawLine(effectiveColor, Offset(startX, startY), Offset(tipX, tipY), fingerW, StrokeCap.Round)

    // Knuckle joint
    val midX = (startX + tipX) / 2f
    val midY = (startY + tipY) / 2f
    drawCircle(effectiveColor, 4f * scale, Offset(midX, midY))

    // Curled fingers behind
    for (i in 1..3) {
        val curlX = startX + dirX * (-15f * scale)
        val curlY = startY + (i - 2) * 12f * scale
        val curlEndX = curlX + dirX * (-20f * scale)
        val curlEndY = curlY + 8f * scale
        drawLine(
            effectiveColor.copy(alpha = alpha * 0.5f),
            Offset(curlX, curlY),
            Offset(curlEndX, curlEndY),
            fingerW * 0.7f,
            StrokeCap.Round
        )
    }
}

/**
 * CHIN_SCRATCH — นิ้วเกาคางแบบ loop
 */
private fun DrawScope.drawChinScratchHand(
    cx: Float, cy: Float, progress: Float, alpha: Float,
    scale: Float, color: Color, glow: Color
) {
    val effectiveColor = color.copy(alpha = alpha)
    val effectiveGlow = glow.copy(alpha = alpha * 0.5f)
    val fingerW = 6f * scale

    val actionProgress = ((progress - 0.15f) / 0.65f).coerceIn(0f, 1f)
    // Scratching motion: oscillate side-to-side
    val scratchOffset = sin(actionProgress * 4f * PI.toFloat()) * 15f * scale

    val baseX = cx + scratchOffset
    val baseY = cy + 20f * scale

    // 3 fingers close together (scratching gesture)
    for (i in 0..2) {
        val fx = baseX + (i - 1) * 12f * scale
        val fy = baseY
        val tipY = fy - 50f * scale
        val tipX = fx + scratchOffset * 0.3f

        // Curved finger (slight bend)
        val midY = (fy + tipY) / 2f
        val midX = fx + (i - 1) * 3f * scale

        drawLine(effectiveGlow, Offset(fx, fy), Offset(midX, midY), fingerW * 2.5f, StrokeCap.Round)
        drawLine(effectiveGlow, Offset(midX, midY), Offset(tipX, tipY), fingerW * 2.5f, StrokeCap.Round)
        drawLine(effectiveColor, Offset(fx, fy), Offset(midX, midY), fingerW, StrokeCap.Round)
        drawLine(effectiveColor, Offset(midX, midY), Offset(tipX, tipY), fingerW, StrokeCap.Round)

        // Fingertip dot
        drawCircle(effectiveColor, 3f * scale, Offset(tipX, tipY))
    }

    // Palm connection line
    drawLine(
        effectiveColor,
        Offset(baseX - 15f * scale, baseY),
        Offset(baseX + 15f * scale, baseY),
        fingerW,
        StrokeCap.Round
    )
}

/**
 * TICKLE — นิ้วกระดิก wiggle
 */
private fun DrawScope.drawTickleHand(
    cx: Float, cy: Float, progress: Float, alpha: Float,
    scale: Float, color: Color, glow: Color
) {
    val effectiveColor = color.copy(alpha = alpha)
    val effectiveGlow = glow.copy(alpha = alpha * 0.5f)
    val fingerW = 5f * scale

    val actionProgress = ((progress - 0.15f) / 0.65f).coerceIn(0f, 1f)

    // 5 fingers doing wave/wiggle motion
    val baseY = cy + 30f * scale
    for (i in 0..4) {
        val fx = cx + (i - 2) * 14f * scale
        // Each finger wiggles with different phase
        val wiggle = sin((actionProgress * 6f + i * 0.8f) * PI.toFloat()) * 8f * scale
        val fingerLen = 55f * scale + (2 - abs(i - 2)) * 10f * scale

        val tipX = fx + wiggle
        val tipY = baseY - fingerLen

        drawLine(effectiveGlow, Offset(fx, baseY), Offset(tipX, tipY), fingerW * 2.5f, StrokeCap.Round)
        drawLine(effectiveColor, Offset(fx, baseY), Offset(tipX, tipY), fingerW, StrokeCap.Round)
        drawCircle(effectiveColor, 2.5f * scale, Offset(tipX, tipY))
    }

    // Palm base arc
    drawLine(
        effectiveColor,
        Offset(cx - 30f * scale, baseY),
        Offset(cx + 30f * scale, baseY),
        fingerW * 1.2f,
        StrokeCap.Round
    )
}

/**
 * PAT — ฝ่ามือตบเบาๆ (pat-pat motion)
 */
private fun DrawScope.drawPatHand(
    cx: Float, cy: Float, progress: Float, alpha: Float,
    scale: Float, color: Color, glow: Color
) {
    val effectiveColor = color.copy(alpha = alpha)
    val effectiveGlow = glow.copy(alpha = alpha * 0.5f)
    val fingerW = 6f * scale

    val actionProgress = ((progress - 0.15f) / 0.65f).coerceIn(0f, 1f)
    // Pat-pat: bounce up and down twice
    val patBounce = sin(actionProgress * 2f * PI.toFloat()) * 20f * scale
    val handY = cy - 30f * scale + patBounce

    val handW = 80f * scale

    // Open palm (flat, fingers together for patting)
    for (i in 0..4) {
        val fx = cx + (i - 2) * 14f * scale
        val tipY = handY - 45f * scale

        drawLine(effectiveGlow, Offset(fx, handY), Offset(fx, tipY), fingerW * 2.5f, StrokeCap.Round)
        drawLine(effectiveColor, Offset(fx, handY), Offset(fx, tipY), fingerW, StrokeCap.Round)
    }

    // Palm
    drawLine(effectiveColor, Offset(cx - 30f * scale, handY), Offset(cx + 30f * scale, handY), fingerW * 1.5f, StrokeCap.Round)

    // Impact ring on pat
    if (patBounce > 10f * scale) {
        val ringAlpha = (patBounce / (20f * scale)) * alpha * 0.3f
        drawCircle(
            color.copy(alpha = ringAlpha),
            radius = 30f * scale,
            center = Offset(cx, cy),
            style = Stroke(width = 2f * scale)
        )
    }
}

/**
 * WAVE — มือโบก bye-bye
 */
private fun DrawScope.drawWaveHand(
    cx: Float, cy: Float, progress: Float, alpha: Float,
    scale: Float, color: Color, glow: Color
) {
    val effectiveColor = color.copy(alpha = alpha)
    val effectiveGlow = glow.copy(alpha = alpha * 0.5f)
    val fingerW = 6f * scale

    val actionProgress = ((progress - 0.15f) / 0.65f).coerceIn(0f, 1f)
    // Waving rotation
    val waveAngle = sin(actionProgress * 3f * PI.toFloat()) * 25f

    rotate(waveAngle, Offset(cx, cy + 40f * scale)) {
        val baseY = cy + 20f * scale

        for (i in 0..4) {
            val fx = cx + (i - 2) * 14f * scale
            val fingerLen = 55f * scale + (2 - abs(i - 2)) * 8f * scale
            val tipY = baseY - fingerLen

            drawLine(effectiveGlow, Offset(fx, baseY), Offset(fx, tipY), fingerW * 2.5f, StrokeCap.Round)
            drawLine(effectiveColor, Offset(fx, baseY), Offset(fx, tipY), fingerW, StrokeCap.Round)
            drawCircle(effectiveColor, 2.5f * scale, Offset(fx, tipY))
        }

        drawLine(effectiveColor, Offset(cx - 30f * scale, baseY), Offset(cx + 30f * scale, baseY), fingerW * 1.5f, StrokeCap.Round)
        // Wrist
        drawLine(effectiveColor, Offset(cx, baseY), Offset(cx, baseY + 30f * scale), fingerW * 1.2f, StrokeCap.Round)
    }
}

// ─── Sparkle Particles ────────────────────────────────────────────────────────

private fun DrawScope.drawSparkles(
    cx: Float, cy: Float, alpha: Float, phase: Float,
    scale: Float, color: Color
) {
    if (alpha < 0.1f) return

    val sparkleCount = 5
    val radius = 40f * scale

    for (i in 0 until sparkleCount) {
        val angle = Math.toRadians((phase + i * 72f).toDouble())
        val sx = cx + (radius * cos(angle)).toFloat()
        val sy = cy - 30f * scale + (radius * 0.5f * sin(angle)).toFloat()
        val sparkleAlpha = alpha * (0.3f + 0.4f * sin((phase + i * 45f) * PI.toFloat() / 180f))

        // Cross sparkle
        val sparkleSize = 4f * scale
        drawLine(color.copy(alpha = sparkleAlpha), Offset(sx - sparkleSize, sy), Offset(sx + sparkleSize, sy), 1.5f * scale, StrokeCap.Round)
        drawLine(color.copy(alpha = sparkleAlpha), Offset(sx, sy - sparkleSize), Offset(sx, sy + sparkleSize), 1.5f * scale, StrokeCap.Round)
    }
}

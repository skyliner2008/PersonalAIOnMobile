package com.example.personalaibot.ui.component.avatar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

/**
 * JarvisAvatar — Compose Canvas animated robot avatar
 *
 * วาดหุ่นยนต์สไตล์ 3D claymation ตามแบบ reference ของ user:
 * - หัวทรงกลม สีขาว พร้อม antenna
 * - หน้าจอ visor สีดำ เป็นตา
 * - หูฟังทั้งสองข้าง
 * - ตัวกลม พร้อมแขนสั้น
 *
 * ใช้ได้ทุกขนาด (48dp → 400dp) — scale-independent
 * ทำงานได้ทั้งใน Activity (Full Screen) และ Service overlay (Mini)
 *
 * @param state สถานะปัจจุบันของ avatar (อารมณ์, audio level, etc.)
 * @param modifier Modifier ที่กำหนดขนาด — ใช้ Modifier.size() จากภายนอก
 */
@Composable
fun JarvisAvatar(
    state: AvatarState,
    modifier: Modifier = Modifier
) {
    // ─── Animations ──────────────────────────────────────────────────────
    val breathingScale by rememberBreathingScale()
    val eyeBlink by rememberEyeBlink()
    val bodyBounce by rememberBodyBounce()
    val antennaWiggle by rememberAntennaWiggle()
    val shake by rememberShake()
    val slowSway by rememberSlowSway()
    val glowPulse by rememberGlowPulse()
    val mouthOpen by rememberMouthAnimation()
    val thinkingDots = rememberThinkingDots()
    val floatingHearts = rememberFloatingHearts()
    val floatingZzz = rememberFloatingZzz()
    val sparkles = rememberSparkles()
    val visualizerBars = rememberVisualizerBars(state.audioLevel)

    // ─── Colors ──────────────────────────────────────────────────────────
    val robotWhite = Color(0xFFF0F4F8)
    val robotLightGray = Color(0xFFD8DEE4)
    val robotVisor = Color(0xFF1A1A2E)
    val robotCyan = Color(0xFF00E5FF)
    val robotDarkCyan = Color(0xFF00838F)
    val heartPink = Color(0xFFFF69B4)
    val angryRed = Color(0xFFFF4444)
    val sleepBlue = Color(0xFF4A6FA5)
    val excitedGold = Color(0xFFFFD700)

    // Eye color based on emotion
    val eyeColor = when (state.emotion) {
        AvatarEmotion.ANGRY -> angryRed
        AvatarEmotion.LOVE -> heartPink
        AvatarEmotion.SLEEPING -> sleepBlue.copy(alpha = 0.5f)
        AvatarEmotion.EXCITED -> excitedGold
        else -> robotCyan
    }

    // Glow color
    val glowColor = when (state.emotion) {
        AvatarEmotion.ANGRY -> angryRed.copy(alpha = 0.15f)
        AvatarEmotion.LOVE -> heartPink.copy(alpha = 0.15f)
        AvatarEmotion.SLEEPING -> sleepBlue.copy(alpha = 0.1f)
        else -> robotCyan.copy(alpha = glowPulse * state.glowIntensity * 0.2f)
    }

    // Body motion
    val bodyOffsetY = when (state.emotion) {
        AvatarEmotion.SPEAKING, AvatarEmotion.HAPPY, AvatarEmotion.EXCITED -> bodyBounce
        AvatarEmotion.SAD -> slowSway * 0.3f
        else -> 0f
    }
    val bodyOffsetX = when (state.emotion) {
        AvatarEmotion.ANGRY -> shake
        AvatarEmotion.SAD -> slowSway
        else -> 0f
    }
    val scaleMultiplier = when (state.emotion) {
        AvatarEmotion.SLEEPING -> 0.98f
        else -> breathingScale
    }

    // Text measurer for drawing text on canvas
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f + bodyOffsetX
        val centerY = h / 2f + bodyOffsetY

        // Unit scale — everything is proportional
        val u = minOf(w, h)

        // Apply breathing scale
        scale(scaleMultiplier, pivot = Offset(w / 2f, h / 2f)) {

            // ═══════════════════════════════════════════════════════════════
            // 0. Ambient Glow (behind everything)
            // ═══════════════════════════════════════════════════════════════
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = Offset(centerX, centerY - u * 0.05f),
                    radius = u * 0.55f
                ),
                radius = u * 0.55f,
                center = Offset(centerX, centerY - u * 0.05f)
            )

            // ═══════════════════════════════════════════════════════════════
            // 1. Body (lower oval)
            // ═══════════════════════════════════════════════════════════════
            val bodyTop = centerY + u * 0.08f
            val bodyW = u * 0.35f
            val bodyH = u * 0.28f

            // Body shadow
            drawOval(
                color = Color.Black.copy(alpha = 0.15f),
                topLeft = Offset(centerX - bodyW / 2f + 2f, bodyTop + 2f),
                size = Size(bodyW, bodyH)
            )
            // Body main
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(robotWhite, robotLightGray),
                    startY = bodyTop,
                    endY = bodyTop + bodyH
                ),
                topLeft = Offset(centerX - bodyW / 2f, bodyTop),
                size = Size(bodyW, bodyH)
            )

            // ─── Arms ────────────────────────────────────────────────────
            val armW = u * 0.07f
            val armH = u * 0.14f
            val armY = bodyTop + u * 0.04f

            // Left arm
            val leftArmRotation = when (state.emotion) {
                AvatarEmotion.HAPPY, AvatarEmotion.EXCITED -> -20f
                AvatarEmotion.SAD -> 5f
                else -> -10f
            }
            rotate(leftArmRotation, pivot = Offset(centerX - bodyW / 2f, armY)) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(robotWhite, robotLightGray)
                    ),
                    topLeft = Offset(centerX - bodyW / 2f - armW * 0.7f, armY),
                    size = Size(armW, armH),
                    cornerRadius = CornerRadius(armW / 2f)
                )
            }

            // Right arm
            val rightArmRotation = when (state.emotion) {
                AvatarEmotion.HAPPY, AvatarEmotion.EXCITED -> 20f
                AvatarEmotion.SAD -> -5f
                else -> 10f
            }
            rotate(rightArmRotation, pivot = Offset(centerX + bodyW / 2f, armY)) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(robotWhite, robotLightGray)
                    ),
                    topLeft = Offset(centerX + bodyW / 2f - armW * 0.3f, armY),
                    size = Size(armW, armH),
                    cornerRadius = CornerRadius(armW / 2f)
                )
            }

            // ═══════════════════════════════════════════════════════════════
            // 2. Head (main oval)
            // ═══════════════════════════════════════════════════════════════
            val headW = u * 0.5f
            val headH = u * 0.42f
            val headCenterY = centerY - u * 0.1f

            // Head shadow
            drawOval(
                color = Color.Black.copy(alpha = 0.12f),
                topLeft = Offset(centerX - headW / 2f + 2f, headCenterY - headH / 2f + 2f),
                size = Size(headW, headH)
            )
            // Head main
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, robotWhite, robotLightGray),
                    center = Offset(centerX - headW * 0.1f, headCenterY - headH * 0.15f),
                    radius = headW * 0.7f
                ),
                topLeft = Offset(centerX - headW / 2f, headCenterY - headH / 2f),
                size = Size(headW, headH)
            )

            // ─── Antenna ─────────────────────────────────────────────────
            val antennaBaseY = headCenterY - headH / 2f
            val antennaTopY = antennaBaseY - u * 0.08f
            val antennaTilt = when (state.emotion) {
                AvatarEmotion.EXCITED -> antennaWiggle
                AvatarEmotion.LISTENING -> antennaWiggle * 0.3f
                else -> 0f
            }

            // Antenna stick
            drawLine(
                color = robotLightGray,
                start = Offset(centerX, antennaBaseY),
                end = Offset(centerX + antennaTilt, antennaTopY),
                strokeWidth = u * 0.02f,
                cap = StrokeCap.Round
            )
            // Antenna ball
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(robotCyan, robotDarkCyan),
                    center = Offset(centerX + antennaTilt, antennaTopY)
                ),
                radius = u * 0.025f,
                center = Offset(centerX + antennaTilt, antennaTopY)
            )
            // Antenna glow
            drawCircle(
                color = robotCyan.copy(alpha = glowPulse * 0.3f),
                radius = u * 0.04f,
                center = Offset(centerX + antennaTilt, antennaTopY)
            )

            // ─── Headphones ──────────────────────────────────────────────
            val hpW = u * 0.09f
            val hpH = u * 0.11f
            val hpY = headCenterY

            // Headphone band (arc over head)
            drawArc(
                color = robotLightGray,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(centerX - headW * 0.38f, headCenterY - headH * 0.45f),
                size = Size(headW * 0.76f, headH * 0.4f),
                style = Stroke(width = u * 0.02f, cap = StrokeCap.Round)
            )

            // Left ear cup
            drawOval(
                brush = Brush.verticalGradient(
                    listOf(robotLightGray, Color(0xFFB0B8C0))
                ),
                topLeft = Offset(centerX - headW / 2f - hpW * 0.3f, hpY - hpH / 2f),
                size = Size(hpW, hpH)
            )
            // Right ear cup
            drawOval(
                brush = Brush.verticalGradient(
                    listOf(robotLightGray, Color(0xFFB0B8C0))
                ),
                topLeft = Offset(centerX + headW / 2f - hpW * 0.7f, hpY - hpH / 2f),
                size = Size(hpW, hpH)
            )

            // ═══════════════════════════════════════════════════════════════
            // 3. Face Visor (rounded rect — the "screen")
            // ═══════════════════════════════════════════════════════════════
            val visorW = headW * 0.72f
            val visorH = headH * 0.55f
            val visorY = headCenterY - visorH * 0.35f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D0D1A), robotVisor, Color(0xFF12122B)),
                    startY = visorY,
                    endY = visorY + visorH
                ),
                topLeft = Offset(centerX - visorW / 2f, visorY),
                size = Size(visorW, visorH),
                cornerRadius = CornerRadius(visorH * 0.35f)
            )

            // Visor highlight (glossy reflection)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    start = Offset(centerX - visorW * 0.3f, visorY),
                    end = Offset(centerX + visorW * 0.3f, visorY + visorH * 0.5f)
                ),
                topLeft = Offset(centerX - visorW / 2f + visorW * 0.05f, visorY + visorH * 0.05f),
                size = Size(visorW * 0.4f, visorH * 0.4f),
                cornerRadius = CornerRadius(visorH * 0.3f)
            )

            // ═══════════════════════════════════════════════════════════════
            // 4. Eyes (change per emotion)
            // ═══════════════════════════════════════════════════════════════
            val eyeRadius = u * 0.04f
            val eyeY = visorY + visorH * 0.4f
            val eyeSpacing = visorW * 0.22f
            val leftEyeX = centerX - eyeSpacing
            val rightEyeX = centerX + eyeSpacing

            drawEyes(
                emotion = state.emotion,
                leftEyeX = leftEyeX,
                rightEyeX = rightEyeX,
                eyeY = eyeY,
                eyeRadius = eyeRadius,
                eyeColor = eyeColor,
                blinkFactor = eyeBlink,
                unit = u,
                textMeasurer = textMeasurer
            )

            // ═══════════════════════════════════════════════════════════════
            // 5. Mouth (below eyes, inside visor)
            // ═══════════════════════════════════════════════════════════════
            val mouthY = visorY + visorH * 0.72f
            val mouthWidth = visorW * 0.2f

            drawMouth(
                emotion = state.emotion,
                centerX = centerX,
                mouthY = mouthY,
                mouthWidth = mouthWidth,
                mouthOpen = if (state.isSpeaking) mouthOpen else 0f,
                eyeColor = eyeColor,
                unit = u
            )

            // ═══════════════════════════════════════════════════════════════
            // 6. Audio Visualizer Bars (Listening mode — inside visor bottom)
            // ═══════════════════════════════════════════════════════════════
            if (state.emotion == AvatarEmotion.LISTENING && state.audioLevel > 0.01f) {
                val barCount = 5
                val barWidth = visorW * 0.04f
                val barSpacing = visorW * 0.06f
                val totalBarsW = barCount * barWidth + (barCount - 1) * barSpacing
                val barsStartX = centerX - totalBarsW / 2f
                val barsY = visorY + visorH * 0.82f

                visualizerBars.forEachIndexed { i, barLevel ->
                    val barH = u * 0.06f * barLevel
                    drawRoundRect(
                        color = robotCyan.copy(alpha = 0.7f + barLevel * 0.3f),
                        topLeft = Offset(barsStartX + i * (barWidth + barSpacing), barsY - barH),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(barWidth / 2f)
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // 7. Thinking Dots (inside visor)
            // ═══════════════════════════════════════════════════════════════
            if (state.emotion == AvatarEmotion.THINKING) {
                val dotR = u * 0.018f
                val dotSpacing = u * 0.055f
                val dotY = visorY + visorH * 0.78f

                thinkingDots.forEachIndexed { i, dotAlpha ->
                    drawCircle(
                        color = robotCyan.copy(alpha = dotAlpha.value),
                        radius = dotR,
                        center = Offset(centerX + (i - 1) * dotSpacing, dotY)
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // 8. Floating Hearts (Love mode — above head)
            // ═══════════════════════════════════════════════════════════════
            if (state.emotion == AvatarEmotion.LOVE) {
                floatingHearts.forEach { heart ->
                    drawHeart(
                        center = Offset(
                            centerX + heart.xOffset,
                            headCenterY - headH / 2f + heart.yOffset
                        ),
                        size = u * 0.04f * heart.scale,
                        color = heartPink.copy(alpha = heart.alpha),
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // 9. Floating ZZZ (Sleeping mode)
            // ═══════════════════════════════════════════════════════════════
            if (state.emotion == AvatarEmotion.SLEEPING) {
                floatingZzz.forEach { zzz ->
                    rotate(zzz.rotation, pivot = Offset(
                        centerX + headW * 0.3f + zzz.xOffset,
                        headCenterY - headH * 0.3f + zzz.yOffset
                    )) {
                        val result = textMeasurer.measure(
                            text = AnnotatedString("Z"),
                            style = TextStyle(
                                color = sleepBlue.copy(alpha = zzz.alpha),
                                fontSize = (u * 0.04f * zzz.scale).sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        )
                        drawText(
                            textLayoutResult = result,
                            topLeft = Offset(
                                centerX + headW * 0.3f + zzz.xOffset - result.size.width / 2f,
                                headCenterY - headH * 0.3f + zzz.yOffset - result.size.height / 2f
                            )
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // 10. Sparkles (Happy / Excited)
            // ═══════════════════════════════════════════════════════════════
            if (state.emotion == AvatarEmotion.HAPPY || state.emotion == AvatarEmotion.EXCITED) {
                sparkles.forEach { s ->
                    drawStar(
                        center = Offset(centerX + s.x, headCenterY + s.y),
                        size = u * 0.02f * s.scale,
                        color = excitedGold.copy(alpha = s.alpha)
                    )
                }
            }
        } // end scale
    } // end Canvas
}

// ═════════════════════════════════════════════════════════════════════════════
// Helper Drawing Functions
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Draw eyes based on current emotion
 */
private fun DrawScope.drawEyes(
    emotion: AvatarEmotion,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    eyeRadius: Float, eyeColor: Color, blinkFactor: Float,
    unit: Float, textMeasurer: TextMeasurer
) {
    when (emotion) {
        AvatarEmotion.HAPPY -> {
            // ^_^ — arc eyes
            val arcWidth = eyeRadius * 2.5f
            drawArc(
                color = eyeColor,
                startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(leftEyeX - arcWidth / 2f, eyeY - eyeRadius * 0.8f),
                size = Size(arcWidth, eyeRadius * 1.6f),
                style = Stroke(width = unit * 0.015f, cap = StrokeCap.Round)
            )
            drawArc(
                color = eyeColor,
                startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(rightEyeX - arcWidth / 2f, eyeY - eyeRadius * 0.8f),
                size = Size(arcWidth, eyeRadius * 1.6f),
                style = Stroke(width = unit * 0.015f, cap = StrokeCap.Round)
            )
        }
        AvatarEmotion.SAD -> {
            // ╥_╥ — droopy eyes
            val arcWidth = eyeRadius * 2.2f
            drawArc(
                color = eyeColor,
                startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(leftEyeX - arcWidth / 2f, eyeY - eyeRadius),
                size = Size(arcWidth, eyeRadius * 2f),
                style = Stroke(width = unit * 0.015f, cap = StrokeCap.Round)
            )
            drawArc(
                color = eyeColor,
                startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(rightEyeX - arcWidth / 2f, eyeY - eyeRadius),
                size = Size(arcWidth, eyeRadius * 2f),
                style = Stroke(width = unit * 0.015f, cap = StrokeCap.Round)
            )
            // Tear drops
            drawCircle(eyeColor.copy(alpha = 0.5f), radius = unit * 0.01f,
                center = Offset(leftEyeX, eyeY + eyeRadius * 1.5f))
            drawCircle(eyeColor.copy(alpha = 0.5f), radius = unit * 0.01f,
                center = Offset(rightEyeX, eyeY + eyeRadius * 1.5f))
        }
        AvatarEmotion.ANGRY -> {
            // Angry eyebrows + smaller red eyes
            // Eyes
            drawCircle(eyeColor, radius = eyeRadius * 0.8f, center = Offset(leftEyeX, eyeY))
            drawCircle(eyeColor, radius = eyeRadius * 0.8f, center = Offset(rightEyeX, eyeY))
            // Eyebrows (angled lines)
            drawLine(
                color = eyeColor,
                start = Offset(leftEyeX - eyeRadius * 1.2f, eyeY - eyeRadius * 1.5f),
                end = Offset(leftEyeX + eyeRadius * 1.2f, eyeY - eyeRadius * 0.8f),
                strokeWidth = unit * 0.018f, cap = StrokeCap.Round
            )
            drawLine(
                color = eyeColor,
                start = Offset(rightEyeX + eyeRadius * 1.2f, eyeY - eyeRadius * 1.5f),
                end = Offset(rightEyeX - eyeRadius * 1.2f, eyeY - eyeRadius * 0.8f),
                strokeWidth = unit * 0.018f, cap = StrokeCap.Round
            )
        }
        AvatarEmotion.LOVE -> {
            // ♥_♥ — heart eyes
            drawHeart(Offset(leftEyeX, eyeY), eyeRadius * 1.2f, eyeColor)
            drawHeart(Offset(rightEyeX, eyeY), eyeRadius * 1.2f, eyeColor)
        }
        AvatarEmotion.SLEEPING -> {
            // ─_─ — closed eyes (horizontal lines)
            drawLine(
                color = eyeColor,
                start = Offset(leftEyeX - eyeRadius, eyeY),
                end = Offset(leftEyeX + eyeRadius, eyeY),
                strokeWidth = unit * 0.018f, cap = StrokeCap.Round
            )
            drawLine(
                color = eyeColor,
                start = Offset(rightEyeX - eyeRadius, eyeY),
                end = Offset(rightEyeX + eyeRadius, eyeY),
                strokeWidth = unit * 0.018f, cap = StrokeCap.Round
            )
        }
        AvatarEmotion.EXCITED -> {
            // ★_★ — star eyes
            drawStar(Offset(leftEyeX, eyeY), eyeRadius * 1.3f, eyeColor)
            drawStar(Offset(rightEyeX, eyeY), eyeRadius * 1.3f, eyeColor)
        }
        AvatarEmotion.LISTENING -> {
            // Big glowing eyes
            drawCircle(
                eyeColor.copy(alpha = 0.2f),
                radius = eyeRadius * 1.5f,
                center = Offset(leftEyeX, eyeY)
            )
            drawCircle(eyeColor, radius = eyeRadius * 1.1f, center = Offset(leftEyeX, eyeY))
            drawCircle(
                eyeColor.copy(alpha = 0.2f),
                radius = eyeRadius * 1.5f,
                center = Offset(rightEyeX, eyeY)
            )
            drawCircle(eyeColor, radius = eyeRadius * 1.1f, center = Offset(rightEyeX, eyeY))
            // Inner highlight
            drawCircle(Color.White.copy(alpha = 0.4f), radius = eyeRadius * 0.35f,
                center = Offset(leftEyeX - eyeRadius * 0.25f, eyeY - eyeRadius * 0.25f))
            drawCircle(Color.White.copy(alpha = 0.4f), radius = eyeRadius * 0.35f,
                center = Offset(rightEyeX - eyeRadius * 0.25f, eyeY - eyeRadius * 0.25f))
        }
        AvatarEmotion.THINKING -> {
            // One eye looking up-right, one squinting
            val lookOffset = eyeRadius * 0.3f
            drawCircle(eyeColor, radius = eyeRadius,
                center = Offset(leftEyeX + lookOffset, eyeY - lookOffset))
            // Right eye half closed
            drawArc(
                color = eyeColor,
                startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(rightEyeX - eyeRadius, eyeY - eyeRadius * 0.5f),
                size = Size(eyeRadius * 2f, eyeRadius * 1f),
                style = Stroke(width = unit * 0.015f, cap = StrokeCap.Round)
            )
        }
        else -> {
            // IDLE, SPEAKING — normal round eyes with blink
            val adjustedRadius = eyeRadius * blinkFactor
            if (adjustedRadius > 0.5f) {
                // Normal circles
                drawCircle(eyeColor, radius = adjustedRadius, center = Offset(leftEyeX, eyeY))
                drawCircle(eyeColor, radius = adjustedRadius, center = Offset(rightEyeX, eyeY))
                // Inner highlight (life-like)
                if (blinkFactor > 0.5f) {
                    drawCircle(Color.White.copy(alpha = 0.35f), radius = adjustedRadius * 0.3f,
                        center = Offset(leftEyeX - adjustedRadius * 0.2f, eyeY - adjustedRadius * 0.2f))
                    drawCircle(Color.White.copy(alpha = 0.35f), radius = adjustedRadius * 0.3f,
                        center = Offset(rightEyeX - adjustedRadius * 0.2f, eyeY - adjustedRadius * 0.2f))
                }
            } else {
                // Blink — thin lines
                drawLine(eyeColor,
                    Offset(leftEyeX - eyeRadius, eyeY), Offset(leftEyeX + eyeRadius, eyeY),
                    strokeWidth = unit * 0.01f, cap = StrokeCap.Round)
                drawLine(eyeColor,
                    Offset(rightEyeX - eyeRadius, eyeY), Offset(rightEyeX + eyeRadius, eyeY),
                    strokeWidth = unit * 0.01f, cap = StrokeCap.Round)
            }
        }
    }
}

/**
 * Draw mouth based on emotion
 */
private fun DrawScope.drawMouth(
    emotion: AvatarEmotion,
    centerX: Float, mouthY: Float, mouthWidth: Float,
    mouthOpen: Float, eyeColor: Color, unit: Float
) {
    when (emotion) {
        AvatarEmotion.HAPPY, AvatarEmotion.LOVE, AvatarEmotion.EXCITED -> {
            // Smile — upward arc
            drawArc(
                color = eyeColor.copy(alpha = 0.8f),
                startAngle = 10f, sweepAngle = 160f, useCenter = false,
                topLeft = Offset(centerX - mouthWidth / 2f, mouthY - mouthWidth * 0.3f),
                size = Size(mouthWidth, mouthWidth * 0.6f),
                style = Stroke(width = unit * 0.012f, cap = StrokeCap.Round)
            )
        }
        AvatarEmotion.SAD -> {
            // Frown — downward arc
            drawArc(
                color = eyeColor.copy(alpha = 0.7f),
                startAngle = 190f, sweepAngle = 160f, useCenter = false,
                topLeft = Offset(centerX - mouthWidth / 2f, mouthY - mouthWidth * 0.1f),
                size = Size(mouthWidth, mouthWidth * 0.5f),
                style = Stroke(width = unit * 0.012f, cap = StrokeCap.Round)
            )
        }
        AvatarEmotion.ANGRY -> {
            // Zigzag mouth
            val zw = mouthWidth * 0.4f
            val path = Path().apply {
                moveTo(centerX - mouthWidth * 0.4f, mouthY)
                lineTo(centerX - zw * 0.5f, mouthY + unit * 0.015f)
                lineTo(centerX, mouthY - unit * 0.01f)
                lineTo(centerX + zw * 0.5f, mouthY + unit * 0.015f)
                lineTo(centerX + mouthWidth * 0.4f, mouthY)
            }
            drawPath(path, color = eyeColor.copy(alpha = 0.8f),
                style = Stroke(width = unit * 0.012f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        AvatarEmotion.SPEAKING -> {
            // Open mouth — oval that pulses with mouthOpen
            val openH = unit * 0.03f * (0.3f + mouthOpen * 0.7f)
            val openW = mouthWidth * 0.5f * (0.6f + mouthOpen * 0.4f)
            drawOval(
                color = eyeColor.copy(alpha = 0.6f),
                topLeft = Offset(centerX - openW / 2f, mouthY - openH / 2f),
                size = Size(openW, openH)
            )
        }
        AvatarEmotion.SLEEPING, AvatarEmotion.THINKING -> {
            // No visible mouth
        }
        AvatarEmotion.LISTENING -> {
            // Small "o" mouth
            drawCircle(
                color = eyeColor.copy(alpha = 0.4f),
                radius = unit * 0.015f,
                center = Offset(centerX, mouthY)
            )
        }
        else -> {
            // IDLE — small dash
            drawLine(
                color = eyeColor.copy(alpha = 0.3f),
                start = Offset(centerX - mouthWidth * 0.15f, mouthY),
                end = Offset(centerX + mouthWidth * 0.15f, mouthY),
                strokeWidth = unit * 0.01f, cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Draw a heart shape
 */
private fun DrawScope.drawHeart(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        val s = size
        moveTo(center.x, center.y + s * 0.4f)
        // Left curve
        cubicTo(
            center.x - s * 1.2f, center.y - s * 0.2f,
            center.x - s * 0.6f, center.y - s * 1f,
            center.x, center.y - s * 0.3f
        )
        // Right curve
        cubicTo(
            center.x + s * 0.6f, center.y - s * 1f,
            center.x + s * 1.2f, center.y - s * 0.2f,
            center.x, center.y + s * 0.4f
        )
    }
    drawPath(path, color = color)
}

/**
 * Draw a 4-pointed star
 */
private fun DrawScope.drawStar(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        val points = 4
        val outerR = size
        val innerR = size * 0.4f
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outerR else innerR
            val angle = (i * 360f / (points * 2) - 90f) * (PI.toFloat() / 180f)
            val x = center.x + cos(angle) * r
            val y = center.y + sin(angle) * r
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path, color = color)
}

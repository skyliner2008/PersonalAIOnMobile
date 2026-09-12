package com.skyliner2008.jarvis.ui.component.avatar

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

    // ─── 3D Clay & Alive Animations ───────────────────────────────────────
    val drift = rememberFloatingDrift()
    val levitationY by rememberHoverLevitation()
    val headTilt by rememberHeadTilt(state.emotion, state.isSpeaking)
    val armMotion = rememberArmMotion(state.emotion, state.isSpeaking)
    val winkFactor by rememberWinkState()
    val reactorPulse by rememberReactorPulse()
    val dizzyAngle by if (state.isDizzy) {
        val inf = rememberInfiniteTransition(label = "dizzy")
        inf.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing)),
            label = "dizzy_rot"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    val isCompanionVisible = state.emotion == AvatarEmotion.THINKING || 
        (state.statusText != null && state.statusText.contains("กำลัง"))
    val companionBubble = rememberCompanionBubbleState(isCompanionVisible)

    // ─── Colors (3D Clay & Gloss Palette) ─────────────────────────────────
    val robotWhite = Color(0xFFFAFCFF)
    val robotLightClay = Color(0xFFE2E8F0)
    val robotClayMid = Color(0xFFCAD4DF)
    val robotClayShadow = Color(0xFFB0BDCC)
    val robotVisor = Color(0xFF0C101C)
    val robotVisorRim = Color(0xFF00E5FF)
    val earBlue = Color(0xFF29B6F6)
    val earBlueDark = Color(0xFF0288D1)
    val earBlueLight = Color(0xFF81D4FA)
    val heartPink = Color(0xFFFF69B4)
    val angryRed = Color(0xFFFF4444)
    val sleepBlue = Color(0xFF4A6FA5)
    val excitedGold = Color(0xFFFFD700)
    val robotCyan = Color(0xFF00E5FF)

    // Eye color based on emotion
    val eyeColor = when (state.emotion) {
        AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED -> angryRed
        AvatarEmotion.LOVE -> heartPink
        AvatarEmotion.SLEEPING -> sleepBlue.copy(alpha = 0.6f)
        AvatarEmotion.EXCITED -> excitedGold
        else -> robotCyan
    }

    // Glow color
    val glowColor = when (state.emotion) {
        AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED -> angryRed.copy(alpha = 0.35f)
        AvatarEmotion.LOVE -> heartPink.copy(alpha = 0.25f)
        AvatarEmotion.SLEEPING -> sleepBlue.copy(alpha = 0.12f)
        AvatarEmotion.EXCITED -> excitedGold.copy(alpha = 0.28f)
        else -> robotCyan.copy(alpha = (0.18f + state.audioLevel * 0.25f) * glowPulse)
    }

    // Body motion
    val bodyOffsetY = when (state.emotion) {
        AvatarEmotion.SPEAKING, AvatarEmotion.HAPPY, AvatarEmotion.EXCITED -> bodyBounce
        AvatarEmotion.SAD -> slowSway * 0.3f
        else -> 0f
    }
    val bodyOffsetX = when (state.emotion) {
        AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED -> shake
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
        val centerX = w / 2f + bodyOffsetX + drift.x
        val centerY = h / 2f + bodyOffsetY + levitationY + drift.y

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
                    radius = u * 0.6f
                ),
                radius = u * 0.6f,
                center = Offset(centerX, centerY - u * 0.05f)
            )

            // ═══════════════════════════════════════════════════════════════
            // 1. Body (3D Clay Egg/Sphere)
            // ═══════════════════════════════════════════════════════════════
            val bodyTop = centerY + u * 0.08f
            val bodyW = u * 0.36f
            val bodyH = u * 0.29f

            // Soft contact shadow underneath body
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(centerX, bodyTop + bodyH * 0.95f),
                    radius = bodyW * 0.45f
                ),
                topLeft = Offset(centerX - bodyW * 0.45f, bodyTop + bodyH * 0.7f),
                size = Size(bodyW * 0.9f, bodyH * 0.45f)
            )

            // Body drop shadow
            drawOval(
                color = Color.Black.copy(alpha = 0.12f),
                topLeft = Offset(centerX - bodyW / 2f + 2f, bodyTop + 4f),
                size = Size(bodyW, bodyH)
            )

            // Body main — 3D spherical clay shading
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, robotWhite, robotLightClay, robotClayMid, robotClayShadow),
                    center = Offset(centerX - bodyW * 0.14f, bodyTop + bodyH * 0.22f),
                    radius = bodyW * 0.75f
                ),
                topLeft = Offset(centerX - bodyW / 2f, bodyTop),
                size = Size(bodyW, bodyH)
            )

            // ─── 3D Arc Reactor (Chest Core) ──────────────────────────────
            val reactorCenterY = bodyTop + bodyH * 0.42f
            val reactorRadius = u * 0.042f

            // Metallic/clay outer bezel
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFE8EEF5), Color(0xFF909CAE)),
                    center = Offset(centerX, reactorCenterY),
                    radius = reactorRadius * 1.35f
                ),
                radius = reactorRadius * 1.25f,
                center = Offset(centerX, reactorCenterY)
            )
            // Inner groove ring
            drawCircle(
                color = Color(0xFF101322),
                radius = reactorRadius * 1.05f,
                center = Offset(centerX, reactorCenterY),
                style = Stroke(width = u * 0.005f)
            )
            // Glowing core
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White, eyeColor, eyeColor.copy(alpha = 0.2f)),
                    center = Offset(centerX, reactorCenterY),
                    radius = reactorRadius * (0.85f + reactorPulse * 0.15f)
                ),
                radius = reactorRadius,
                center = Offset(centerX, reactorCenterY)
            )
            // Core pulse soft ring
            drawCircle(
                color = eyeColor.copy(alpha = 0.35f * reactorPulse),
                radius = reactorRadius * (1.25f + reactorPulse * 0.25f),
                center = Offset(centerX, reactorCenterY),
                style = Stroke(width = u * 0.006f)
            )

            // ─── Arms (Waving Right Arm & Natural Left Arm) ─────────────────
            val armW = u * 0.075f
            val armH = u * 0.155f
            val armY = bodyTop + u * 0.035f

            // Left arm (Natural floating articulation)
            val leftPivot = Offset(centerX - bodyW / 2f + armW * 0.2f, armY + armW * 0.4f)
            rotate(armMotion.leftArmAngle, pivot = leftPivot) {
                // Arm cylinder
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(robotWhite, robotLightClay, robotClayMid)
                    ),
                    topLeft = Offset(centerX - bodyW / 2f - armW * 0.65f, armY),
                    size = Size(armW, armH),
                    cornerRadius = CornerRadius(armW / 2f)
                )
                // Cute round hand at bottom
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(robotWhite, robotClayMid),
                        center = Offset(centerX - bodyW / 2f - armW * 0.15f, armY + armH * 0.95f),
                        radius = armW * 0.55f
                    ),
                    radius = armW * 0.52f,
                    center = Offset(centerX - bodyW / 2f - armW * 0.15f, armY + armH * 0.95f)
                )
            }

            // Right arm (Waving hand gesture — Image 1)
            val rightPivot = Offset(centerX + bodyW / 2f - armW * 0.2f, armY + armW * 0.4f)
            rotate(armMotion.rightArmAngle, pivot = rightPivot) {
                // Arm cylinder
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(robotWhite, robotLightClay, robotClayMid)
                    ),
                    topLeft = Offset(centerX + bodyW / 2f - armW * 0.35f, armY),
                    size = Size(armW, armH),
                    cornerRadius = CornerRadius(armW / 2f)
                )
                // Cute round hand at tip with waving rotation
                val handCenter = Offset(centerX + bodyW / 2f + armW * 0.15f, armY + armH * 0.95f)
                rotate(armMotion.rightHandWaveAngle, pivot = handCenter) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White, robotWhite, robotClayMid),
                            center = handCenter,
                            radius = armW * 0.6f
                        ),
                        radius = armW * 0.55f,
                        center = handCenter
                    )
                    // Cute little thumb / finger cuff
                    drawCircle(
                        color = robotLightClay,
                        radius = armW * 0.22f,
                        center = Offset(handCenter.x - armW * 0.35f, handCenter.y - armW * 0.15f)
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // 2. Head Group (Head, Halo, Visor, Eyes, Mouth, Antenna, Headphones)
            //    with 3D Head Tilt
            // ═══════════════════════════════════════════════════════════════
            val headW = u * 0.5f
            val headH = u * 0.42f
            val headCenterY = centerY - u * 0.1f

            rotate(headTilt, pivot = Offset(centerX, headCenterY)) {

                // Head drop shadow
                drawOval(
                    color = Color.Black.copy(alpha = 0.14f),
                    topLeft = Offset(centerX - headW / 2f + 2f, headCenterY - headH / 2f + 4f),
                    size = Size(headW, headH)
                )
                // Head main — Pearlescent 3D spherical clay shading
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, robotWhite, robotLightClay, robotClayMid, robotClayShadow),
                        center = Offset(centerX - headW * 0.14f, headCenterY - headH * 0.18f),
                        radius = headW * 0.78f
                    ),
                    topLeft = Offset(centerX - headW / 2f, headCenterY - headH / 2f),
                    size = Size(headW, headH)
                )

                // ─── Antenna (Image 1) ───────────────────────────────────
                val antennaBaseY = headCenterY - headH / 2f
                val antennaTopY = antennaBaseY - u * 0.085f
                val antennaTilt = when (state.emotion) {
                    AvatarEmotion.EXCITED -> antennaWiggle
                    AvatarEmotion.LISTENING -> antennaWiggle * 0.3f
                    else -> 0f
                }

                // Antenna stalk
                drawLine(
                    color = robotLightClay,
                    start = Offset(centerX, antennaBaseY),
                    end = Offset(centerX + antennaTilt, antennaTopY),
                    strokeWidth = u * 0.018f,
                    cap = StrokeCap.Round
                )
                // Antenna ball glow
                drawCircle(
                    color = robotCyan.copy(alpha = glowPulse * 0.35f),
                    radius = u * 0.048f,
                    center = Offset(centerX + antennaTilt, antennaTopY)
                )
                // Antenna ball (Cyan sphere)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White, robotCyan, Color(0xFF0091EA)),
                        center = Offset(centerX + antennaTilt - u * 0.007f, antennaTopY - u * 0.007f),
                        radius = u * 0.028f
                    ),
                    radius = u * 0.028f,
                    center = Offset(centerX + antennaTilt, antennaTopY)
                )

                // ─── Headphones / Earcups (Sky Blue, Image 1) ─────────────
                val hpW = u * 0.095f
                val hpH = u * 0.125f
                val hpY = headCenterY

                // Headphone band (arc across top of head)
                drawArc(
                    color = robotLightClay,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(centerX - headW * 0.38f, headCenterY - headH * 0.46f),
                    size = Size(headW * 0.76f, headH * 0.42f),
                    style = Stroke(width = u * 0.02f, cap = StrokeCap.Round)
                )

                // Left ear cup (Sky-Blue 3D pad)
                drawOval(
                    brush = Brush.verticalGradient(
                        listOf(earBlueLight, earBlue, earBlueDark)
                    ),
                    topLeft = Offset(centerX - headW / 2f - hpW * 0.45f, hpY - hpH / 2f),
                    size = Size(hpW, hpH)
                )
                // Left ear cushion rim
                drawOval(
                    color = earBlueLight.copy(alpha = 0.7f),
                    topLeft = Offset(centerX - headW / 2f - hpW * 0.45f, hpY - hpH / 2f),
                    size = Size(hpW, hpH),
                    style = Stroke(width = u * 0.007f)
                )

                // Right ear cup (Sky-Blue 3D pad)
                drawOval(
                    brush = Brush.verticalGradient(
                        listOf(earBlueLight, earBlue, earBlueDark)
                    ),
                    topLeft = Offset(centerX + headW / 2f - hpW * 0.55f, hpY - hpH / 2f),
                    size = Size(hpW, hpH)
                )
                // Right ear cushion rim
                drawOval(
                    color = earBlueLight.copy(alpha = 0.7f),
                    topLeft = Offset(centerX + headW / 2f - hpW * 0.55f, hpY - hpH / 2f),
                    size = Size(hpW, hpH),
                    style = Stroke(width = u * 0.007f)
                )

                // ═══════════════════════════════════════════════════════════
                // 3. Face Visor (Clean Glossy Obsidian Glass, NO SCANLINE)
                // ═══════════════════════════════════════════════════════════
                val visorW = headW * 0.72f
                val visorH = headH * 0.55f
                val visorY = headCenterY - visorH * 0.35f

                // Visor main glass
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF080B14), robotVisor, Color(0xFF101424)),
                        startY = visorY,
                        endY = visorY + visorH
                    ),
                    topLeft = Offset(centerX - visorW / 2f, visorY),
                    size = Size(visorW, visorH),
                    cornerRadius = CornerRadius(visorH * 0.35f)
                )

                // Visor subtle cyber-rim border
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            robotVisorRim.copy(alpha = 0.35f),
                            Color.Transparent,
                            robotVisorRim.copy(alpha = 0.18f)
                        ),
                        startY = visorY,
                        endY = visorY + visorH
                    ),
                    topLeft = Offset(centerX - visorW / 2f, visorY),
                    size = Size(visorW, visorH),
                    cornerRadius = CornerRadius(visorH * 0.35f),
                    style = Stroke(width = u * 0.005f)
                )

                // Visor soft glossy glass reflection highlight (top-left)
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        start = Offset(centerX - visorW * 0.35f, visorY),
                        end = Offset(centerX + visorW * 0.25f, visorY + visorH * 0.45f)
                    ),
                    topLeft = Offset(centerX - visorW / 2f + visorW * 0.05f, visorY + visorH * 0.05f),
                    size = Size(visorW * 0.45f, visorH * 0.42f),
                    cornerRadius = CornerRadius(visorH * 0.3f)
                )

                // ═══════════════════════════════════════════════════════════
                // 4. Eyes (10 Expressions from Image 2 + Playful Wink + Dynamic Gaze)
                // ═══════════════════════════════════════════════════════════
                val eyeRadius = u * 0.04f
                val baseEyeY = visorY + visorH * 0.4f
                val eyeSpacing = visorW * 0.22f
                val gazeShiftX = (state.gazeOffsetX.coerceIn(-1f, 1f)) * eyeRadius * 0.75f
                val gazeShiftY = (state.gazeOffsetY.coerceIn(-1f, 1f)) * eyeRadius * 0.6f
                val leftEyeX = centerX - eyeSpacing + gazeShiftX
                val rightEyeX = centerX + eyeSpacing + gazeShiftX
                val eyeY = baseEyeY + gazeShiftY

                drawEyes(
                    emotion = state.emotion,
                    leftEyeX = leftEyeX,
                    rightEyeX = rightEyeX,
                    eyeY = eyeY,
                    eyeRadius = eyeRadius,
                    eyeColor = eyeColor,
                    blinkFactor = eyeBlink,
                    winkFactor = winkFactor,
                    isDizzy = state.isDizzy,
                    dizzyAngle = dizzyAngle,
                    unit = u,
                    textMeasurer = textMeasurer
                )

                // ═══════════════════════════════════════════════════════════
                // 5. Mouth (below eyes, inside visor)
                // ═══════════════════════════════════════════════════════════
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

                // ═══════════════════════════════════════════════════════════
                // 6. Audio Visualizer Bars (Listening mode — inside visor bottom)
                // ═══════════════════════════════════════════════════════════
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

                // ═══════════════════════════════════════════════════════════
                // 7. Thinking Dots (inside visor)
                // ═══════════════════════════════════════════════════════════
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
            } // end rotate headTilt

            // ═══════════════════════════════════════════════════════════════
            // 8. Floating Companion Thought Bubble / Tool Badge (Image 1)
            // ═══════════════════════════════════════════════════════════════
            if (companionBubble.alpha > 0.01f) {
                val bubbleW = u * 0.28f
                val bubbleH = u * 0.12f
                val bubbleX = centerX + headW * 0.36f
                val bubbleY = headCenterY - headH * 0.42f + companionBubble.yOffset

                // Speech bubble shadow
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.18f * companionBubble.alpha),
                    topLeft = Offset(bubbleX + 2f, bubbleY + 3f),
                    size = Size(bubbleW, bubbleH),
                    cornerRadius = CornerRadius(bubbleH * 0.45f)
                )
                // Speech bubble body (3D clay pill)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White, Color(0xFFF1F5F9))
                    ),
                    topLeft = Offset(bubbleX, bubbleY),
                    size = Size(bubbleW, bubbleH),
                    cornerRadius = CornerRadius(bubbleH * 0.45f)
                )
                // Bubble border
                drawRoundRect(
                    color = robotCyan.copy(alpha = 0.4f * companionBubble.alpha),
                    topLeft = Offset(bubbleX, bubbleY),
                    size = Size(bubbleW, bubbleH),
                    cornerRadius = CornerRadius(bubbleH * 0.45f),
                    style = Stroke(width = u * 0.005f)
                )
                // Bubble tail
                val tailPath = Path().apply {
                    moveTo(bubbleX + bubbleW * 0.2f, bubbleY + bubbleH)
                    lineTo(bubbleX + bubbleW * 0.05f, bubbleY + bubbleH + u * 0.03f)
                    lineTo(bubbleX + bubbleW * 0.35f, bubbleY + bubbleH)
                    close()
                }
                drawPath(tailPath, color = Color(0xFFF1F5F9).copy(alpha = companionBubble.alpha))

                // Inside bubble: 3 bouncing animated dots
                val bDotR = u * 0.015f
                val bSpacing = u * 0.045f
                val bCenterY = bubbleY + bubbleH / 2f
                val bCenterX = bubbleX + bubbleW / 2f

                for (idx in 0..2) {
                    val isDotUp = companionBubble.dotCycle == idx
                    val dotOffsetY = if (isDotUp) -u * 0.012f else 0f
                    val dotColor = if (isDotUp) robotCyan else Color(0xFF64748B)
                    drawCircle(
                        color = dotColor.copy(alpha = companionBubble.alpha),
                        radius = bDotR * (if (isDotUp) 1.25f else 1f),
                        center = Offset(bCenterX + (idx - 1) * bSpacing, bCenterY + dotOffsetY)
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // 9. Floating Hearts (Love mode — above head)
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
 * Draw eyes based on current emotion (10 visor expressions from reference images + playful wink)
 */
private fun DrawScope.drawEyes(
    emotion: AvatarEmotion,
    leftEyeX: Float, rightEyeX: Float, eyeY: Float,
    eyeRadius: Float, eyeColor: Color, blinkFactor: Float,
    winkFactor: Float,
    isDizzy: Boolean = false,
    dizzyAngle: Float = 0f,
    unit: Float, textMeasurer: TextMeasurer
) {
    if (isDizzy) {
        // Spiral / Dizzy eyes (@_@) rotating when shaken
        val r = eyeRadius * 1.25f
        rotate(dizzyAngle, Offset(leftEyeX, eyeY)) {
            drawArc(
                color = eyeColor,
                startAngle = 0f, sweepAngle = 290f, useCenter = false,
                topLeft = Offset(leftEyeX - r, eyeY - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = unit * 0.016f, cap = StrokeCap.Round)
            )
            drawCircle(eyeColor, radius = r * 0.35f, center = Offset(leftEyeX, eyeY))
        }
        rotate(-dizzyAngle, Offset(rightEyeX, eyeY)) {
            drawArc(
                color = eyeColor,
                startAngle = 0f, sweepAngle = 290f, useCenter = false,
                topLeft = Offset(rightEyeX - r, eyeY - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = unit * 0.016f, cap = StrokeCap.Round)
            )
            drawCircle(eyeColor, radius = r * 0.35f, center = Offset(rightEyeX, eyeY))
        }
        return
    }

    when (emotion) {
        AvatarEmotion.HAPPY -> {
            // ^_^ — Cheerful smiling upward arcs
            val arcW = eyeRadius * 2.4f
            val arcH = eyeRadius * 1.6f
            drawArc(
                color = eyeColor,
                startAngle = 195f, sweepAngle = 150f, useCenter = false,
                topLeft = Offset(leftEyeX - arcW / 2f, eyeY - arcH * 0.6f),
                size = Size(arcW, arcH),
                style = Stroke(width = unit * 0.016f, cap = StrokeCap.Round)
            )
            drawArc(
                color = eyeColor,
                startAngle = 195f, sweepAngle = 150f, useCenter = false,
                topLeft = Offset(rightEyeX - arcW / 2f, eyeY - arcH * 0.6f),
                size = Size(arcW, arcH),
                style = Stroke(width = unit * 0.016f, cap = StrokeCap.Round)
            )
        }
        AvatarEmotion.SAD -> {
            // ︵ ︵ — Droopy downward arcs + glowing teardrops
            val arcW = eyeRadius * 2.2f
            val arcH = eyeRadius * 1.6f
            drawArc(
                color = eyeColor,
                startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(leftEyeX - arcW / 2f, eyeY - arcH * 0.8f),
                size = Size(arcW, arcH),
                style = Stroke(width = unit * 0.016f, cap = StrokeCap.Round)
            )
            drawArc(
                color = eyeColor,
                startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(rightEyeX - arcW / 2f, eyeY - arcH * 0.8f),
                size = Size(arcW, arcH),
                style = Stroke(width = unit * 0.016f, cap = StrokeCap.Round)
            )
            // Luminous tear drops
            val tearR = unit * 0.012f
            drawCircle(eyeColor.copy(alpha = 0.75f), radius = tearR,
                center = Offset(leftEyeX + eyeRadius * 0.2f, eyeY + eyeRadius * 1.4f))
            drawCircle(eyeColor.copy(alpha = 0.75f), radius = tearR,
                center = Offset(rightEyeX - eyeRadius * 0.2f, eyeY + eyeRadius * 1.4f))
        }
        AvatarEmotion.ANGRY -> {
            // Sharp slanted eyes + angled furrowed eyebrows
            val eyeW = eyeRadius * 1.4f
            val eyeH = eyeRadius * 0.8f
            // Left eye
            drawOval(eyeColor, topLeft = Offset(leftEyeX - eyeW / 2f, eyeY - eyeH / 2f), size = Size(eyeW, eyeH))
            // Right eye
            drawOval(eyeColor, topLeft = Offset(rightEyeX - eyeW / 2f, eyeY - eyeH / 2f), size = Size(eyeW, eyeH))

            // Angled angry brows
            val browLen = eyeRadius * 1.4f
            val browThick = unit * 0.018f
            drawLine(
                color = eyeColor,
                start = Offset(leftEyeX - browLen * 0.8f, eyeY - eyeRadius * 1.4f),
                end = Offset(leftEyeX + browLen * 0.8f, eyeY - eyeRadius * 0.7f),
                strokeWidth = browThick, cap = StrokeCap.Round
            )
            drawLine(
                color = eyeColor,
                start = Offset(rightEyeX + browLen * 0.8f, eyeY - eyeRadius * 1.4f),
                end = Offset(rightEyeX - browLen * 0.8f, eyeY - eyeRadius * 0.7f),
                strokeWidth = browThick, cap = StrokeCap.Round
            )
        }
        AvatarEmotion.LOVE -> {
            // ♥ ♥ — Radiant heart eyes
            drawHeart(Offset(leftEyeX, eyeY), eyeRadius * 1.35f, eyeColor)
            drawHeart(Offset(rightEyeX, eyeY), eyeRadius * 1.35f, eyeColor)
            // Heart shine highlights
            drawCircle(Color.White.copy(alpha = 0.45f), radius = eyeRadius * 0.25f,
                center = Offset(leftEyeX - eyeRadius * 0.3f, eyeY - eyeRadius * 0.3f))
            drawCircle(Color.White.copy(alpha = 0.45f), radius = eyeRadius * 0.25f,
                center = Offset(rightEyeX - eyeRadius * 0.3f, eyeY - eyeRadius * 0.3f))
        }
        AvatarEmotion.SLEEPING -> {
            // ─ ─ — Peaceful horizontal closed eye slits
            val slitLen = eyeRadius * 1.1f
            drawLine(
                color = eyeColor.copy(alpha = 0.8f),
                start = Offset(leftEyeX - slitLen, eyeY),
                end = Offset(leftEyeX + slitLen, eyeY),
                strokeWidth = unit * 0.015f, cap = StrokeCap.Round
            )
            drawLine(
                color = eyeColor.copy(alpha = 0.8f),
                start = Offset(rightEyeX - slitLen, eyeY),
                end = Offset(rightEyeX + slitLen, eyeY),
                strokeWidth = unit * 0.015f, cap = StrokeCap.Round
            )
        }
        AvatarEmotion.EXCITED -> {
            // ★ ★ — 4-Pointed radiant golden star eyes
            drawStar(Offset(leftEyeX, eyeY), eyeRadius * 1.45f, eyeColor)
            drawStar(Offset(rightEyeX, eyeY), eyeRadius * 1.45f, eyeColor)
            // Inner white shine
            drawCircle(Color.White.copy(alpha = 0.6f), radius = eyeRadius * 0.3f, center = Offset(leftEyeX, eyeY))
            drawCircle(Color.White.copy(alpha = 0.6f), radius = eyeRadius * 0.3f, center = Offset(rightEyeX, eyeY))
        }
        AvatarEmotion.LISTENING -> {
            // Big curious luminous eyes with inner pupil
            val outerR = eyeRadius * 1.25f
            // Outer glow ring
            drawCircle(eyeColor.copy(alpha = 0.25f), radius = outerR * 1.3f, center = Offset(leftEyeX, eyeY))
            drawCircle(eyeColor.copy(alpha = 0.25f), radius = outerR * 1.3f, center = Offset(rightEyeX, eyeY))
            // Primary eye circle
            drawCircle(eyeColor, radius = outerR, center = Offset(leftEyeX, eyeY))
            drawCircle(eyeColor, radius = outerR, center = Offset(rightEyeX, eyeY))
            // Inner white pupil highlight
            drawCircle(Color.White, radius = outerR * 0.4f,
                center = Offset(leftEyeX - outerR * 0.25f, eyeY - outerR * 0.25f))
            drawCircle(Color.White, radius = outerR * 0.4f,
                center = Offset(rightEyeX - outerR * 0.25f, eyeY - outerR * 0.25f))
        }
        AvatarEmotion.THINKING -> {
            // Inquisitive asymmetric gaze: Left eye looks up-right, Right eye half-squints
            val lookOffsetX = eyeRadius * 0.3f
            val lookOffsetY = eyeRadius * 0.25f
            drawCircle(eyeColor, radius = eyeRadius * 1.05f,
                center = Offset(leftEyeX + lookOffsetX, eyeY - lookOffsetY))
            drawCircle(Color.White.copy(alpha = 0.5f), radius = eyeRadius * 0.3f,
                center = Offset(leftEyeX + lookOffsetX - eyeRadius * 0.2f, eyeY - lookOffsetY - eyeRadius * 0.2f))

            // Right eye squinting arc
            val arcW = eyeRadius * 2.2f
            val arcH = eyeRadius * 1.3f
            drawArc(
                color = eyeColor,
                startAngle = 195f, sweepAngle = 150f, useCenter = false,
                topLeft = Offset(rightEyeX - arcW / 2f, eyeY - arcH * 0.5f),
                size = Size(arcW, arcH),
                style = Stroke(width = unit * 0.016f, cap = StrokeCap.Round)
            )
        }
        else -> {
            // IDLE / SPEAKING — 3D Cute Capsule Pill Eyes (❚ ❚) with Playful Wink!
            val pillW = eyeRadius * 0.9f
            val isWinking = winkFactor > 0.05f

            // Left Eye: Always pill (with blink factor)
            val leftPillH = maxOf(eyeRadius * 1.6f * blinkFactor, unit * 0.01f)
            if (blinkFactor > 0.15f) {
                drawRoundRect(
                    color = eyeColor,
                    topLeft = Offset(leftEyeX - pillW / 2f, eyeY - leftPillH / 2f),
                    size = Size(pillW, leftPillH),
                    cornerRadius = CornerRadius(pillW / 2f)
                )
                // Left eye gleam dot
                drawCircle(
                    Color.White.copy(alpha = 0.45f * blinkFactor),
                    radius = pillW * 0.28f,
                    center = Offset(leftEyeX - pillW * 0.12f, eyeY - leftPillH * 0.22f)
                )
            } else {
                // Closed slit during quick blink
                drawLine(
                    color = eyeColor,
                    start = Offset(leftEyeX - pillW * 0.8f, eyeY),
                    end = Offset(leftEyeX + pillW * 0.8f, eyeY),
                    strokeWidth = unit * 0.012f, cap = StrokeCap.Round
                )
            }

            // Right Eye: Pill OR Wink Arc (^) when winking
            if (isWinking) {
                // Playful wink arc on right eye!
                val winkArcW = eyeRadius * 2.2f
                val winkArcH = eyeRadius * 1.5f
                drawArc(
                    color = eyeColor,
                    startAngle = 195f, sweepAngle = 150f, useCenter = false,
                    topLeft = Offset(rightEyeX - winkArcW / 2f, eyeY - winkArcH * 0.55f),
                    size = Size(winkArcW, winkArcH),
                    style = Stroke(width = unit * 0.016f, cap = StrokeCap.Round)
                )
            } else {
                val rightPillH = maxOf(eyeRadius * 1.6f * blinkFactor, unit * 0.01f)
                if (blinkFactor > 0.15f) {
                    drawRoundRect(
                        color = eyeColor,
                        topLeft = Offset(rightEyeX - pillW / 2f, eyeY - rightPillH / 2f),
                        size = Size(pillW, rightPillH),
                        cornerRadius = CornerRadius(pillW / 2f)
                    )
                    // Right eye gleam dot
                    drawCircle(
                        Color.White.copy(alpha = 0.45f * blinkFactor),
                        radius = pillW * 0.28f,
                        center = Offset(rightEyeX - pillW * 0.12f, eyeY - rightPillH * 0.22f)
                    )
                } else {
                    drawLine(
                        color = eyeColor,
                        start = Offset(rightEyeX - pillW * 0.8f, eyeY),
                        end = Offset(rightEyeX + pillW * 0.8f, eyeY),
                        strokeWidth = unit * 0.012f, cap = StrokeCap.Round
                    )
                }
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
        AvatarEmotion.HAPPY, AvatarEmotion.LOVE -> {
            // Smile — warm upward arc
            drawArc(
                color = eyeColor.copy(alpha = 0.85f),
                startAngle = 15f, sweepAngle = 150f, useCenter = false,
                topLeft = Offset(centerX - mouthWidth / 2f, mouthY - mouthWidth * 0.35f),
                size = Size(mouthWidth, mouthWidth * 0.7f),
                style = Stroke(width = unit * 0.013f, cap = StrokeCap.Round)
            )
        }
        AvatarEmotion.EXCITED -> {
            // Big Cheerful Open D-Smile (Image 2 style)
            val smileW = mouthWidth * 0.85f
            val smileH = mouthWidth * 0.55f
            val path = Path().apply {
                moveTo(centerX - smileW / 2f, mouthY - smileH * 0.2f)
                lineTo(centerX + smileW / 2f, mouthY - smileH * 0.2f)
                cubicTo(
                    centerX + smileW / 2f, mouthY + smileH,
                    centerX - smileW / 2f, mouthY + smileH,
                    centerX - smileW / 2f, mouthY - smileH * 0.2f
                )
                close()
            }
            drawPath(path, color = eyeColor.copy(alpha = 0.8f))
        }
        AvatarEmotion.SAD -> {
            // Frown — downward arc
            drawArc(
                color = eyeColor.copy(alpha = 0.75f),
                startAngle = 195f, sweepAngle = 150f, useCenter = false,
                topLeft = Offset(centerX - mouthWidth / 2f, mouthY - mouthWidth * 0.1f),
                size = Size(mouthWidth, mouthWidth * 0.55f),
                style = Stroke(width = unit * 0.013f, cap = StrokeCap.Round)
            )
        }
        AvatarEmotion.ANGRY -> {
            // Zigzag / tense mouth
            val zw = mouthWidth * 0.45f
            val path = Path().apply {
                moveTo(centerX - mouthWidth * 0.4f, mouthY)
                lineTo(centerX - zw * 0.5f, mouthY + unit * 0.015f)
                lineTo(centerX, mouthY - unit * 0.01f)
                lineTo(centerX + zw * 0.5f, mouthY + unit * 0.015f)
                lineTo(centerX + mouthWidth * 0.4f, mouthY)
            }
            drawPath(path, color = eyeColor.copy(alpha = 0.85f),
                style = Stroke(width = unit * 0.013f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        AvatarEmotion.SPEAKING -> {
            // Open mouth — glowing oval that pulses dynamically with speech volume
            val openH = unit * 0.035f * (0.35f + mouthOpen * 0.65f)
            val openW = mouthWidth * 0.55f * (0.65f + mouthOpen * 0.35f)
            drawOval(
                color = eyeColor.copy(alpha = 0.65f),
                topLeft = Offset(centerX - openW / 2f, mouthY - openH / 2f),
                size = Size(openW, openH)
            )
            drawOval(
                color = eyeColor,
                topLeft = Offset(centerX - openW / 2f, mouthY - openH / 2f),
                size = Size(openW, openH),
                style = Stroke(width = unit * 0.006f)
            )
        }
        AvatarEmotion.LISTENING -> {
            // Cute small "o" mouth
            drawCircle(
                color = eyeColor.copy(alpha = 0.5f),
                radius = unit * 0.016f,
                center = Offset(centerX, mouthY)
            )
            drawCircle(
                color = eyeColor.copy(alpha = 0.9f),
                radius = unit * 0.016f,
                center = Offset(centerX, mouthY),
                style = Stroke(width = unit * 0.005f)
            )
        }
        AvatarEmotion.SLEEPING, AvatarEmotion.THINKING -> {
            // Subtle tiny dash or hidden
            drawLine(
                color = eyeColor.copy(alpha = 0.2f),
                start = Offset(centerX - mouthWidth * 0.1f, mouthY),
                end = Offset(centerX + mouthWidth * 0.1f, mouthY),
                strokeWidth = unit * 0.008f, cap = StrokeCap.Round
            )
        }
        else -> {
            // IDLE — subtle friendly resting mouth arc
            drawArc(
                color = eyeColor.copy(alpha = 0.45f),
                startAngle = 25f, sweepAngle = 130f, useCenter = false,
                topLeft = Offset(centerX - mouthWidth * 0.25f, mouthY - mouthWidth * 0.1f),
                size = Size(mouthWidth * 0.5f, mouthWidth * 0.25f),
                style = Stroke(width = unit * 0.009f, cap = StrokeCap.Round)
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

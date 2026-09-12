package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * PetRobotHeadAvatar — Fullscreen Living Robot Head Avatar
 *
 * ออกแบบตามภาพอ้างอิงของแท้ (media_1789115340717.jpg):
 * 1. หัวหุ่นยนต์เซรามิกสีขาวทรงกลมมน นุ่มนวล 3D Lighting
 * 2. ปลั๊กหูฟังโลหะสีฟ้าเทา (Metallic Slate Ear Knobs) สองข้าง
 * 3. หน้ากากกระจกดำเงาวับ (Glossy Curved Visor) พร้อมแสงสะท้อนโค้ง (Specular Arc Reflection)
 * 4. หน้าจอดิจิทัล LED Dot Matrix แสดง 11 อารมณ์เป๊ะตามแบบ:
 *    - HAPPY: ตากลมยิ้ม + ปากยิ้ม
 *    - WINK: ตาขยิบข้างเดียว + ปากยิ้ม
 *    - SAD: คิ้วลู่ + ปากคว่ำ + หยดน้ำตากำลังหยด
 *    - ANGRY: ตาสายฟ้าสีแดงเฉียง + ปากฟันปลา
 *    - CONFUSED: เครื่องหมาย ? ตรงกลาง + ตากลม + ปากขีด
 *    - LOVE: ตาหัวใจสีชมพูแดงคู่ ♥ ♥ + ปากยิ้ม
 *    - SLEEPING: ตาหลับขีดเรียบ — — + ตัวอักษร z z z ลอยขึ้น
 *    - THINKING: ตาโตข้างเล็กข้าง คิ้วเลิกสูง + ไอคอนมือแตะคาง 🤔
 *    - EXCITED: ตาโค้งหยี ^^ + ปากอ้ากว้างยิ้มแฉ่ง
 *    - POUT: ตากลมมองข้าง + แก้มป่องสีชมพูแดงระเรื่อ + ปากบูด Hmph
 *    - DIZZY: ตาลายก้นหอยหมุนวนต่อเนื่อง @_@ + ปากหยักคลื่น เมื่อถูกเขย่า
 * 5. การขยับแบบมีชีวิต: หายใจลอยขึ้นลง (Breathing Bob), กะพริบตาธรรมชาติ, หันหัวเอียง, และกรอกสายตามองตาม
 */
@Composable
fun PetRobotHeadAvatar(
    state: AvatarState,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "pet_head_living")

    // ─── 1. Living Breathing Bobbing Animation ────────────────────────────────
    val breathingOffsetY by infinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_y"
    )

    // ─── 2. Natural Eye Blinking Animation ───────────────────────────────────
    val blinkProgress by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4200
                1f at 0
                1f at 3900
                0.08f at 4000 // blink shut
                0.08f at 4060
                1f at 4140 // open back
                1f at 4200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "eye_blink"
    )

    // ─── 3. Continuous Dizzy Spiral Eye Rotation ─────────────────────────────
    val dizzyAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dizzy_angle"
    )

    // ─── 4. Animated Floating Zzz for Sleeping ────────────────────────────────
    val zzzFloat by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "zzz_float"
    )

    // ─── 5. Animated Dropping Tear for Sad ────────────────────────────────────
    val tearProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "sad_tear"
    )

    // ─── 6. Effective Emotion (respecting dynamic eyeStyle override) ──────────
    val effectiveEmotion = when {
        state.isDizzy -> AvatarEmotion.DIZZY
        state.faceState.eyeStyle == EyeStyle.HEART -> AvatarEmotion.LOVE
        state.faceState.eyeStyle == EyeStyle.STAR -> AvatarEmotion.EXCITED
        state.faceState.eyeStyle == EyeStyle.WINK -> AvatarEmotion.WINK
        state.faceState.eyeStyle == EyeStyle.CROSS -> AvatarEmotion.ANGRY
        state.faceState.eyeStyle == EyeStyle.CRYING -> AvatarEmotion.SAD
        state.faceState.eyeStyle == EyeStyle.QUESTION -> AvatarEmotion.CONFUSED
        state.faceState.eyeStyle == EyeStyle.SPIRAL -> AvatarEmotion.DIZZY
        else -> state.emotion
    }

    // ─── 7. Head Tilt Smooth Animation (with gesture override) ────────────────
    val gestureTilt = state.faceState.gesture.headTiltOverride()
    val targetHeadTilt = when {
        gestureTilt != null -> gestureTilt
        state.isDizzy || effectiveEmotion == AvatarEmotion.DIZZY -> 4f * sin(breathingOffsetY * 0.5f)
        effectiveEmotion == AvatarEmotion.THINKING -> 5.5f
        effectiveEmotion == AvatarEmotion.CONFUSED -> -4.0f
        effectiveEmotion == AvatarEmotion.POUT -> -3.0f
        effectiveEmotion == AvatarEmotion.SURPRISED -> 0f  // Alert upright posture
        effectiveEmotion == AvatarEmotion.BORED -> 4.5f    // Lazy tired tilt
        effectiveEmotion == AvatarEmotion.ENRAGED -> 0f    // Rigid menacing battle posture
        else -> state.gazeOffsetX * 3.0f
    }
    val headTiltAnim by animateFloatAsState(
        targetValue = targetHeadTilt,
        animationSpec = tween(400, easing = EaseOutQuad),
        label = "head_tilt"
    )

    // ─── 8. Animated Vertical Shift for Mouth Balance ─────────────────────────
    // เมื่อมีปากปรากฏขึ้น (เช่น กำลังส่งเสียงพูด หรือแสดงอารมณ์ที่มีปาก) ดวงตาจะขยับขึ้นด้านบนเล็กน้อย (~8.5% ของขนาดตา)
    // เพื่อให้ความสมดุลกึ่งกลางของทั้งใบหน้า (ดวงตา + ปาก) อยู่ตรงกลางหน้าจอพอดี ไม่ค่อนไปข้างล่าง
    // และเมื่อไม่มีปาก (เช่น โหมด IDLE ตอนไม่ได้พูด) ดวงตาจะเลื่อนกลับมาอยู่ตรงกลางหน้าจอแท้จริง
    val isSpeaking = state.isSpeaking || effectiveEmotion == AvatarEmotion.SPEAKING
    val hasMouth = when {
        state.faceState.eyeTrick != EyeTrickState.NONE -> false
        effectiveEmotion == AvatarEmotion.IDLE -> isSpeaking
        else -> true
    }
    val mouthShiftProgress by animateFloatAsState(
        targetValue = if (hasMouth) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "mouth_shift"
    )

    // ─── 8. Screensaver Eye Trick Animation (Ping-Pong, Tired Bounce, Snooker Shot) ─
    val trickProgress = remember { Animatable(0f) }
    val currentTrick = state.faceState.eyeTrick
    LaunchedEffect(currentTrick) {
        if (currentTrick != EyeTrickState.NONE) {
            trickProgress.snapTo(0f)
            trickProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(4200, easing = LinearEasing)
            )
        } else {
            trickProgress.snapTo(0f)
        }
    }

    Canvas(modifier = modifier) {
        val canvasW = size.width
        val canvasH = size.height
        if (canvasW <= 0f || canvasH <= 0f) return@Canvas

        val centerX = canvasW / 2f
        val centerY = (canvasH / 2f) + breathingOffsetY

        // Calculate gaze displacement from Camera or Touch
        val maxGazeX = canvasW * 0.16f
        val maxGazeY = canvasH * 0.12f
        val gazeX = state.gazeOffsetX.coerceIn(-1f, 1f)
        val gazeY = state.gazeOffsetY.coerceIn(-1f, 1f)
        val gazeDisplacementX = gazeX * maxGazeX
        val gazeDisplacementY = gazeY * maxGazeY

        // Effective eye blink (do not blink when sleeping, wink, dizzy, or surprised)
        val effectiveBlink = when (effectiveEmotion) {
            AvatarEmotion.SLEEPING -> 0.10f
            AvatarEmotion.WINK -> 1f
            AvatarEmotion.DIZZY -> 1f
            AvatarEmotion.SURPRISED -> 1f  // Eyes wide open, no blink
            AvatarEmotion.ENRAGED -> 1f    // Wide glaring eyes, intense gaze
            AvatarEmotion.BORED -> blinkProgress.coerceIn(0.35f, 0.65f)  // Heavy half-closed lids
            else -> if (state.isDizzy) 1f else blinkProgress
        }

        // Apply Head/Face Tilt Rotation around center
        rotate(degrees = headTiltAnim, pivot = Offset(centerX, centerY)) {
            // ═══════════════════════════════════════════════════════════════
            // PURE OLED LIVING FACE (THE PHONE IS THE ROBOT'S HEAD)
            // ═══════════════════════════════════════════════════════════════

            // 1. Dynamic Eye Colors based on Emotion (Solid Vibrant Colors — Eilik / Dfree Reference)
            val eyeColor = when (effectiveEmotion) {
                AvatarEmotion.LISTENING -> Color(0xFF4EE2F5) // Solid Electric Cyan (#4EE2F5)
                AvatarEmotion.SPEAKING -> Color(0xFF00E676) // Mint Emerald
                AvatarEmotion.THINKING -> Color(0xFF4EE2F5) // Cyan
                AvatarEmotion.HAPPY -> Color(0xFF4EE2F5) // Cyan
                AvatarEmotion.EXCITED -> Color(0xFFFFD600) // Solar Amber Gold
                AvatarEmotion.LOVE -> Color(0xFFFF4081) // Hot Pink
                AvatarEmotion.ANGRY -> Color(0xFFFF1744) // Alert Flame Red
                AvatarEmotion.ENRAGED -> Color(0xFFFF1744) // Fiery Crimson Red (ตาขวางแดงเพลิง)
                AvatarEmotion.SAD -> Color(0xFF80D8FF) // Ice Slate Blue
                AvatarEmotion.SLEEPING -> Color(0xFF7986CB) // Lavender
                AvatarEmotion.CONFUSED -> Color(0xFF4EE2F5) // Cyan
                AvatarEmotion.DIZZY -> Color(0xFFE040FB) // Magenta Violet
                AvatarEmotion.WINK -> Color(0xFF4EE2F5)
                AvatarEmotion.POUT -> Color(0xFF4EE2F5)
                AvatarEmotion.SURPRISED -> Color(0xFFE0F7FA) // Bright Electric White-Cyan (ตกใจ)
                AvatarEmotion.BORED -> Color(0xFF90A4AE) // Cool Slate Muted Gray (เบื่อ)
                AvatarEmotion.IDLE -> Color(0xFF4EE2F5) // Solid Electric Cyan (#4EE2F5)
            }

            // 2. Dynamic Eye Dimensions (Large, Expressive Dual Circles — LOOI / Eilik Style)
            val isLandscape = canvasW > canvasH
            val eyeDiameter = if (isLandscape) {
                minOf(canvasH * 0.52f, canvasW * 0.28f)
            } else {
                minOf(canvasW * 0.38f, canvasH * 0.24f)
            }
            val baseEyeW = eyeDiameter
            val baseEyeH = eyeDiameter
            val baseSpacing = eyeDiameter * 0.65f

            // Gaze Perspective Distortion (เมื่อมองข้าง ตาข้างที่มองจะกระชับขึ้น)
            val leftEyeScaleX = if (gazeX < 0) 1f - 0.10f * abs(gazeX) else 1f + 0.05f * gazeX
            val rightEyeScaleX = if (gazeX > 0) 1f - 0.10f * gazeX else 1f + 0.05f * abs(gazeX)
            val spacingFactor = 1f - 0.06f * abs(gazeX)
            val dynamicSpacing = baseSpacing * spacingFactor

            val leftEyeCenterX = centerX + gazeDisplacementX - dynamicSpacing
            val rightEyeCenterX = centerX + gazeDisplacementX + dynamicSpacing

            // เมื่อมีปากปรากฏเข้ามา ดวงตาจะขยับขึ้นด้านบนเล็กน้อย (~8.5% ของขนาดตา) เพื่อจัดสัดส่วนใบหน้าให้สมดุลกึ่งกลางจอ
            val eyeMouthUpwardShift = eyeDiameter * 0.085f * mouthShiftProgress
            val eyeCenterY = centerY + gazeDisplacementY - eyeMouthUpwardShift

            // 4. Dynamic Eyebrows (Brows) - Shown conditionally for expressive emotions only
            val showEyebrows = when (effectiveEmotion) {
                AvatarEmotion.THINKING,
                AvatarEmotion.ANGRY,
                AvatarEmotion.ENRAGED,
                AvatarEmotion.CONFUSED,
                AvatarEmotion.SAD,
                AvatarEmotion.LISTENING,
                AvatarEmotion.SURPRISED,
                AvatarEmotion.BORED -> true
                else -> false
            } && state.faceState.eyeTrick == EyeTrickState.NONE

            if (showEyebrows) {
                val browW = baseEyeW * 0.95f
                val browH = 9.dp.toPx()
                val browBaseY = eyeCenterY - baseEyeH * 0.65f

                // Brow angles and offsets depending on emotion
                val (leftBrowAngle, rightBrowAngle, browOffsetY) = when (effectiveEmotion) {
                    AvatarEmotion.THINKING -> Triple(6f, -14f, -4.dp.toPx())
                    AvatarEmotion.ANGRY -> Triple(18f, -18f, 6.dp.toPx())
                    AvatarEmotion.ENRAGED -> Triple(24f, -24f, 8.dp.toPx()) // Deeply furrowed menacing V-brows
                    AvatarEmotion.SAD -> Triple(-15f, 15f, 2.dp.toPx())
                    AvatarEmotion.LISTENING -> Triple(0f, 0f, -12.dp.toPx()) // Raised in attention
                    AvatarEmotion.SURPRISED -> Triple(-6f, 6f, -14.dp.toPx()) // High arched up (shock!)
                    AvatarEmotion.BORED -> Triple(-8f, 8f, 6.dp.toPx()) // Flat drooping down (tired)
                    else -> Triple(0f, 0f, 0f)
                }

                // Draw Left Eyebrow
                rotate(degrees = leftBrowAngle, pivot = Offset(leftEyeCenterX, browBaseY + browOffsetY)) {
                    drawRoundRect(
                        color = eyeColor.copy(alpha = if (effectiveEmotion == AvatarEmotion.LISTENING) 0.95f else 0.65f),
                        topLeft = Offset(leftEyeCenterX - browW / 2f, browBaseY + browOffsetY - browH / 2f),
                        size = Size(browW, browH),
                        cornerRadius = CornerRadius(browH / 2f, browH / 2f)
                    )
                }

                // Draw Right Eyebrow
                rotate(degrees = rightBrowAngle, pivot = Offset(rightEyeCenterX, browBaseY + browOffsetY)) {
                    drawRoundRect(
                        color = eyeColor.copy(alpha = if (effectiveEmotion == AvatarEmotion.LISTENING) 0.95f else 0.65f),
                        topLeft = Offset(rightEyeCenterX - browW / 2f, browBaseY + browOffsetY - browH / 2f),
                        size = Size(browW, browH),
                        cornerRadius = CornerRadius(browH / 2f, browH / 2f)
                    )
                }
            }

            // 5. Render Active Emotion Eyes & Screensaver Tricks
            renderPetEmotion(
                emotion = effectiveEmotion,
                eyeTrick = state.faceState.eyeTrick,
                trickProgress = trickProgress.value,
                centerX = centerX + gazeDisplacementX,
                centerY = centerY + gazeDisplacementY,
                visorW = canvasW,
                visorH = canvasH,
                blinkFactor = effectiveBlink,
                dizzyAngle = dizzyAngle,
                zzzFloat = zzzFloat,
                tearProgress = tearProgress,
                leftEyeCenterX = leftEyeCenterX,
                rightEyeCenterX = rightEyeCenterX,
                eyeCenterY = eyeCenterY,
                baseEyeW = baseEyeW,
                baseEyeH = baseEyeH,
                leftEyeScaleX = leftEyeScaleX,
                rightEyeScaleX = rightEyeScaleX,
                gazeX = gazeX,
                gazeY = gazeY,
                eyeColor = eyeColor,
                audioLevel = state.audioLevel,
                isSpeaking = state.isSpeaking || effectiveEmotion == AvatarEmotion.SPEAKING
            )
        }
    }
}

/**
 * วาดดวงตา ปาก และองค์ประกอบดิจิทัล 11 อารมณ์เป๊ะตามแบบ LOOI Living Face
 * พร้อม Squash & Stretch physics และ Idle Screensaver Tricks
 */
private fun DrawScope.renderPetEmotion(
    emotion: AvatarEmotion,
    eyeTrick: EyeTrickState,
    trickProgress: Float,
    centerX: Float,
    centerY: Float,
    visorW: Float,
    visorH: Float,
    blinkFactor: Float,
    dizzyAngle: Float,
    zzzFloat: Float,
    tearProgress: Float,
    leftEyeCenterX: Float,
    rightEyeCenterX: Float,
    eyeCenterY: Float,
    baseEyeW: Float,
    baseEyeH: Float,
    leftEyeScaleX: Float,
    rightEyeScaleX: Float,
    gazeX: Float,
    gazeY: Float,
    eyeColor: Color,
    audioLevel: Float,
    isSpeaking: Boolean
) {
    val cyanLed = eyeColor
    val redLed = Color(0xFFFF1E46)
    val pinkLed = Color(0xFFFF4081)
    val mouthY = eyeCenterY + baseEyeH * 0.75f

    // ─── 0. Screensaver Idle Eye Tricks (Ping-Pong, Tired Bounce, Snooker Shot) ─
    if (eyeTrick != EyeTrickState.NONE) {
        val p = trickProgress.coerceIn(0f, 1f)
        when (eyeTrick) {
            EyeTrickState.PING_PONG_BOUNCE -> {
                val maxTravel = visorW * 0.35f
                val pingPongX: Float
                val squashX: Float
                val squashY: Float

                if (p < 0.25f) {
                    val sub = p / 0.25f
                    pingPongX = sub * maxTravel
                    squashX = if (sub > 0.8f) 0.55f else 1f + sub * 0.1f
                    squashY = if (sub > 0.8f) 1.35f else 1f / squashX
                } else if (p < 0.60f) {
                    val sub = (p - 0.25f) / 0.35f
                    pingPongX = maxTravel - sub * (maxTravel * 2f)
                    squashX = if (sub > 0.85f) 0.55f else 1.25f
                    squashY = 1f / squashX
                } else if (p < 0.85f) {
                    val sub = (p - 0.60f) / 0.25f
                    pingPongX = -maxTravel + sub * maxTravel
                    squashX = 1f + sin(sub * PI * 2f).toFloat() * 0.2f
                    squashY = 1f / squashX
                } else {
                    pingPongX = 0f
                    squashX = 1f
                    squashY = 1f
                }

                drawSquircleEye(cyanLed, leftEyeCenterX + pingPongX, eyeCenterY, baseEyeW * leftEyeScaleX, baseEyeH, squashX, squashY)
                drawSquircleEye(cyanLed, rightEyeCenterX + pingPongX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH, squashX, squashY)
            }
            EyeTrickState.TIRED_BOUNCE -> {
                if (p < 0.60f) {
                    val sub = p / 0.60f
                    val bounceH = abs(sin(sub * PI * 3f).toFloat())
                    val bounceY = -bounceH * 60.dp.toPx()
                    val squashY = if (bounceH < 0.25f) 0.65f else 1.25f
                    val squashX = 1f / squashY
                    drawSquircleEye(cyanLed, leftEyeCenterX, eyeCenterY + bounceY, baseEyeW * leftEyeScaleX, baseEyeH, squashX, squashY)
                    drawSquircleEye(cyanLed, rightEyeCenterX, eyeCenterY + bounceY, baseEyeW * rightEyeScaleX, baseEyeH, squashX, squashY)
                } else {
                    val sub = (p - 0.60f) / 0.40f
                    val tiredH = baseEyeH * (0.35f + 0.12f * sin(sub * PI * 4f).toFloat())
                    drawSquircleEye(cyanLed, leftEyeCenterX, eyeCenterY + 12.dp.toPx(), baseEyeW * 1.1f * leftEyeScaleX, tiredH)
                    drawSquircleEye(cyanLed, rightEyeCenterX, eyeCenterY + 12.dp.toPx(), baseEyeW * 1.1f * rightEyeScaleX, tiredH)
                    drawPuckerMouth(cyanLed, centerX, mouthY, 9.dp.toPx() + 3.dp.toPx() * sin(sub * PI * 4f).toFloat())
                }
            }
            EyeTrickState.SNOOKER_SHOT -> {
                if (p < 0.35f) {
                    val sub = p / 0.35f
                    val winkH = baseEyeH * (1f - sub * 0.75f)
                    drawSquircleEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW * leftEyeScaleX, winkH)
                    val pullbackX = -sub * 24.dp.toPx()
                    drawSquircleEye(cyanLed, rightEyeCenterX + pullbackX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH, 1.1f, 0.9f)
                } else if (p < 0.65f) {
                    val sub = (p - 0.35f) / 0.30f
                    val shotX = sub * (visorW * 0.35f)
                    val hitWall = sub > 0.85f
                    val squashX = if (hitWall) 0.55f else 1.35f
                    val squashY = 1f / squashX
                    drawSquircleEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW * 1.15f * leftEyeScaleX, baseEyeH * 1.15f)
                    drawSquircleEye(cyanLed, rightEyeCenterX + shotX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH, squashX, squashY)
                } else {
                    val sub = (p - 0.65f) / 0.35f
                    val reboundX = (visorW * 0.35f) * (1f - sub)
                    drawSquircleEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW * leftEyeScaleX, baseEyeH)
                    drawSquircleEye(cyanLed, rightEyeCenterX + reboundX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH)
                    drawSmileArc(cyanLed, centerX, mouthY, baseEyeW * 0.9f, 18.dp.toPx())
                }
            }
            EyeTrickState.NONE -> {}
        }
        return
    }

    when (emotion) {
        // ─── 1. IDLE (พักหน้าจอ สอดส่องสายตา วงกลม 2 ชั้นเหลื่อมซ้อนกัน สะอาดตา ไม่มีคิ้ว/ปาก) ───
        AvatarEmotion.IDLE -> {
            val blinkSquashX = if (blinkFactor < 1f) 1f + (1f - blinkFactor) * 0.18f else 1f
            val leftH = baseEyeH * blinkFactor.coerceAtLeast(0.08f)
            val rightH = baseEyeH * blinkFactor.coerceAtLeast(0.08f)
            val leftW = baseEyeW * leftEyeScaleX
            val rightW = baseEyeW * rightEyeScaleX

            // Dual Overlapping Circles (No inner sparkle, dynamic gaze parallax)
            drawDualCircleEye(cyanLed, leftEyeCenterX, eyeCenterY, leftW, leftH, gazeX, gazeY, blinkSquashX, 1f)
            drawDualCircleEye(cyanLed, rightEyeCenterX, eyeCenterY, rightW, rightH, gazeX, gazeY, blinkSquashX, 1f)

            // Idle State: ไม่มีปาก ยกเว้นขณะส่งเสียงพูด
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.6f, 32.dp.toPx(), audioLevel)
            }
        }

        // ─── 2. HAPPY (ยิ้มตาหยีโค้ง ⌒ ⌒ + ปากยิ้ม — สไตล์ Eilik / Dfree) ───────────
        AvatarEmotion.HAPPY -> {
            val leftW = baseEyeW * leftEyeScaleX
            val rightW = baseEyeW * rightEyeScaleX

            drawHappyEye(cyanLed, leftEyeCenterX, eyeCenterY, leftW, baseEyeH, gazeX, gazeY)
            drawHappyEye(cyanLed, rightEyeCenterX, eyeCenterY, rightW, baseEyeH, gazeX, gazeY)

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.5f, 28.dp.toPx(), audioLevel)
            } else {
                drawSmileArc(cyanLed, centerX, mouthY, baseEyeW * 1.1f, 22.dp.toPx())
            }
        }

        // ─── 3. LISTENING / SPEAKING ─────────────────────────────────────────
        AvatarEmotion.LISTENING, AvatarEmotion.SPEAKING -> {
            val leftH = baseEyeH * blinkFactor.coerceAtLeast(0.08f)
            val rightH = baseEyeH * blinkFactor.coerceAtLeast(0.08f)
            val leftW = baseEyeW * leftEyeScaleX
            val rightW = baseEyeW * rightEyeScaleX

            drawDualCircleEye(cyanLed, leftEyeCenterX, eyeCenterY, leftW, leftH, gazeX, gazeY)
            drawDualCircleEye(cyanLed, rightEyeCenterX, eyeCenterY, rightW, rightH, gazeX, gazeY)

            if (isSpeaking || emotion == AvatarEmotion.SPEAKING) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.5f, 28.dp.toPx(), audioLevel)
            } else {
                drawSmileArc(cyanLed, centerX, mouthY, baseEyeW * 1.0f, 20.dp.toPx())
            }
        }

        // ─── 4. WINK (ขยิบตาข้างเดียว ตาอีกข้างกลมโต — สไตล์ Eilik) ────────────
        AvatarEmotion.WINK -> {
            // Left Eye: Sleek Happy Wink Arc with royal blue shadow
            drawHappyEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW * 1.05f, baseEyeH, gazeX, gazeY)
            // Right Eye: Big Dual Circle Eye with gaze
            drawDualCircleEye(cyanLed, rightEyeCenterX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH, gazeX, gazeY)

            drawSmileArc(cyanLed, centerX, mouthY, baseEyeW * 1.1f, 22.dp.toPx())
        }

        // ─── 5. SAD (ร้องไห้ น้ำตาหยด) ────────────────────────────────────────
        AvatarEmotion.SAD -> {
            val eyeH = baseEyeH * 0.85f * blinkFactor.coerceAtLeast(0.15f)
            val eyeW = baseEyeW * 0.9f

            // Sad Drooping Circles with deep slate blue back disc
            drawDualCircleEye(Color(0xFF80D8FF), leftEyeCenterX, eyeCenterY, eyeW * leftEyeScaleX, eyeH, gazeX, gazeY)
            drawDualCircleEye(Color(0xFF80D8FF), rightEyeCenterX, eyeCenterY, eyeW * rightEyeScaleX, eyeH, gazeX, gazeY)

            // Animated Dropping Tear from Right Eye
            val tearY = eyeCenterY + baseEyeH * 0.5f + (tearProgress * 70.dp.toPx())
            val tearAlpha = (1f - tearProgress * 0.8f).coerceIn(0.2f, 1f)
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = tearAlpha),
                radius = 7.dp.toPx(),
                center = Offset(rightEyeCenterX + eyeW * 0.45f, tearY)
            )

            // Sad Inverted Frown Mouth
            drawFrownArc(Color(0xFF80D8FF), centerX, mouthY, baseEyeW * 1.1f, 22.dp.toPx())
        }

        // ─── 6. ANGRY (โกรธ ตาเฉียงคมดุดันสีแดงเพลิง — สไตล์ Eilik) ──────────────
        AvatarEmotion.ANGRY -> {
            drawAngryEye(redLed, leftEyeCenterX, eyeCenterY, baseEyeW * leftEyeScaleX, baseEyeH, true)
            drawAngryEye(redLed, rightEyeCenterX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH, false)

            // Angry Zigzag Mouth (ฟันปลา /\/\/\)
            val mouthW = baseEyeW * 1.3f
            val zigzagPath = Path().apply {
                val seg = mouthW / 4f
                moveTo(centerX - mouthW / 2f, mouthY)
                lineTo(centerX - mouthW / 2f + seg * 1f, mouthY - 8.dp.toPx())
                lineTo(centerX - mouthW / 2f + seg * 2f, mouthY + 8.dp.toPx())
                lineTo(centerX - mouthW / 2f + seg * 3f, mouthY - 8.dp.toPx())
                lineTo(centerX + mouthW / 2f, mouthY)
            }
            drawPath(zigzagPath, redLed, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
        }

        // ─── 7. CONFUSED (งง เครื่องหมายคำถาม + ปากแบน) ────────────────────────
        AvatarEmotion.CONFUSED -> {
            // Glowing Question Mark '?' Centered Above Eyes
            drawQuestionMark(cyanLed, centerX, eyeCenterY - baseEyeH * 0.75f, baseEyeH * 0.65f)

            // Left Eye: Squinted smaller circle
            drawDualCircleEye(cyanLed, leftEyeCenterX, eyeCenterY + 4.dp.toPx(), baseEyeW * 0.8f * leftEyeScaleX, baseEyeH * 0.7f, gazeX, gazeY)
            // Right Eye: Wide alert circle
            drawDualCircleEye(cyanLed, rightEyeCenterX, eyeCenterY - 4.dp.toPx(), baseEyeW * 1.15f * rightEyeScaleX, baseEyeH * 1.1f, gazeX, gazeY)

            // Flat Puzzled Mouth Bar (ปากแบน)
            val mouthW = baseEyeW * 0.9f
            drawLine(
                color = cyanLed,
                start = Offset(centerX - mouthW / 2f, mouthY),
                end = Offset(centerX + mouthW / 2f, mouthY),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // ─── 8. LOVE (ตามีความรักเป็นรูปหัวใจคู่) ──────────────────────────────
        AvatarEmotion.LOVE -> {
            val heartSize = baseEyeW * 1.35f
            drawHeart(pinkLed, leftEyeCenterX, eyeCenterY, heartSize)
            drawHeart(pinkLed, rightEyeCenterX, eyeCenterY, heartSize)

            drawSmileArc(pinkLed, centerX, mouthY, baseEyeW * 1.1f, 24.dp.toPx())
        }

        // ─── 9. SLEEPING (หลับตาพริ้มขีดมน — — พร้อมเงาลึก 3D + Zzz) ─────────────
        AvatarEmotion.SLEEPING -> {
            drawSleepingEye(eyeColor, leftEyeCenterX, eyeCenterY, baseEyeW)
            drawSleepingEye(eyeColor, rightEyeCenterX, eyeCenterY, baseEyeW)

            // Calm Flat Mouth
            val mouthW = baseEyeW * 0.65f
            drawLine(
                color = eyeColor,
                start = Offset(centerX - mouthW / 2f, mouthY),
                end = Offset(centerX + mouthW / 2f, mouthY),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Floating Animated Z Z Z
            val zStartX = rightEyeCenterX + baseEyeW * 0.65f
            val zStartY = eyeCenterY - baseEyeH * 0.35f
            for (i in 0..2) {
                val prog = (zzzFloat + (i * 0.33f)) % 1f
                val zX = zStartX + (prog * 60.dp.toPx())
                val zY = zStartY - (prog * 80.dp.toPx())
                val zAlpha = (sin(prog * PI).toFloat()).coerceIn(0.1f, 1f)
                val zScale = 0.8f + (i * 0.35f)
                drawZLetter(eyeColor.copy(alpha = zAlpha), zX, zY, 18.dp.toPx() * zScale)
            }
        }

        // ─── 10. THINKING (สงสัย เอามือแตะคาง + ปากแบน) ───────────────────────
        AvatarEmotion.THINKING -> {
            // Left Eye: Squinted
            drawDualCircleEye(eyeColor, leftEyeCenterX, eyeCenterY, baseEyeW * 0.8f * leftEyeScaleX, baseEyeH * 0.65f, gazeX, gazeY)
            // Right Eye: Open round eye
            drawDualCircleEye(eyeColor, rightEyeCenterX, eyeCenterY, baseEyeW * 1.05f * rightEyeScaleX, baseEyeH, gazeX, gazeY)

            // Hand on chin icon
            val handX = centerX + baseEyeW * 0.15f
            drawThinkingHand(eyeColor, handX, mouthY + 10.dp.toPx(), 45.dp.toPx())

            // Flat Mouth Bar (ปากแบน)
            val mouthW = baseEyeW * 0.8f
            drawLine(
                color = eyeColor,
                start = Offset(centerX - mouthW / 2f, mouthY),
                end = Offset(centerX + mouthW / 2f, mouthY),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // ─── 11. EXCITED (ตื่นเต้น ตา > < อ้าปากกว้าง — สไตล์ Eilik) ───────────
        AvatarEmotion.EXCITED -> {
            drawExcitedEye(eyeColor, leftEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, true)
            drawExcitedEye(eyeColor, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, false)

            // Wide Open Laughing Smile Mouth
            val mouthW = baseEyeW * 1.35f
            val mouthH = 32.dp.toPx()
            val mouthPath = Path().apply {
                moveTo(centerX - mouthW / 2f, mouthY)
                lineTo(centerX + mouthW / 2f, mouthY)
                arcTo(
                    rect = Rect(centerX - mouthW / 2f, mouthY - mouthH, centerX + mouthW / 2f, mouthY + mouthH),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
                close()
            }
            drawPath(mouthPath, eyeColor)
        }

        // ─── 12. POUT (หน้าบูด แก้มป่อง แดงระเรื่อ ปากจู๋ 'O') ──────────────────
        AvatarEmotion.POUT -> {
            // Side glancing dual circle eyes
            drawDualCircleEye(eyeColor, leftEyeCenterX, eyeCenterY, baseEyeW * 0.9f, baseEyeH * 0.9f, gazeX, gazeY)
            drawDualCircleEye(eyeColor, rightEyeCenterX, eyeCenterY, baseEyeW * 0.9f, baseEyeH * 0.9f, gazeX, gazeY)

            // Pink Blushing Cheek Patches
            val cheekRadius = 22.dp.toPx()
            val cheekY = eyeCenterY + baseEyeH * 0.45f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(pinkLed.copy(alpha = 0.75f), Color.Transparent),
                    center = Offset(leftEyeCenterX - baseEyeW * 0.55f, cheekY),
                    radius = cheekRadius
                ),
                radius = cheekRadius,
                center = Offset(leftEyeCenterX - baseEyeW * 0.55f, cheekY)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(pinkLed.copy(alpha = 0.75f), Color.Transparent),
                    center = Offset(rightEyeCenterX + baseEyeW * 0.55f, cheekY),
                    radius = cheekRadius
                ),
                radius = cheekRadius,
                center = Offset(rightEyeCenterX + baseEyeW * 0.55f, cheekY)
            )

            // ปากจู๋ (Pucker 'O' mouth)
            drawPuckerMouth(eyeColor, centerX, mouthY, 11.dp.toPx())
        }

        // ─── 13. DIZZY (วิงเวียน ตาลาย X X พร้อมเงาลึก 3D — สไตล์ Eilik) ─────────
        AvatarEmotion.DIZZY -> {
            drawCrossEye(eyeColor, leftEyeCenterX, eyeCenterY, baseEyeW * 0.85f)
            drawCrossEye(eyeColor, rightEyeCenterX, eyeCenterY, baseEyeW * 0.85f)

            // Wobbly Wave Mouth
            val mouthW = baseEyeW * 1.2f
            val wavePath = Path().apply {
                moveTo(centerX - mouthW / 2f, mouthY)
                val seg = mouthW / 4f
                cubicTo(
                    centerX - mouthW / 2f + seg * 0.5f, mouthY - 8.dp.toPx(),
                    centerX - mouthW / 2f + seg * 1.5f, mouthY + 8.dp.toPx(),
                    centerX, mouthY
                )
                cubicTo(
                    centerX + seg * 0.5f, mouthY - 8.dp.toPx(),
                    centerX + seg * 1.5f, mouthY + 8.dp.toPx(),
                    centerX + mouthW / 2f, mouthY
                )
            }
            drawPath(wavePath, eyeColor, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
        }

        // ─── 14. SURPRISED (ตกใจ: ตาเบิกกว้างสุด + ปากอ้า O — สไตล์ Eilik Shock) ───
        AvatarEmotion.SURPRISED -> {
            // Extra-large wide-open eyes (1.25x bigger)
            val surpriseW = baseEyeW * 1.25f
            val surpriseH = baseEyeH * 1.25f
            drawDualCircleEye(eyeColor, leftEyeCenterX, eyeCenterY, surpriseW * leftEyeScaleX, surpriseH, gazeX, gazeY)
            drawDualCircleEye(eyeColor, rightEyeCenterX, eyeCenterY, surpriseW * rightEyeScaleX, surpriseH, gazeX, gazeY)

            // Open round gasp mouth 'O' (larger than pout)
            drawPuckerMouth(eyeColor, centerX, mouthY, 18.dp.toPx())
        }

        // ─── 15. BORED (เบื่อ: ตาลู่ครึ่งปิด + ปากเฉย — สไตล์ Sleepy Vibes) ─────────
        AvatarEmotion.BORED -> {
            // Half-closed flat eyes (squished height, slightly wider)
            val boredW = baseEyeW * 1.05f
            val boredH = baseEyeH * 0.42f
            val boredYShift = 6.dp.toPx()
            drawDualCircleEye(eyeColor, leftEyeCenterX, eyeCenterY + boredYShift, boredW * leftEyeScaleX, boredH, gazeX, gazeY)
            drawDualCircleEye(eyeColor, rightEyeCenterX, eyeCenterY + boredYShift, boredW * rightEyeScaleX, boredH, gazeX, gazeY)

            // Eyelid line above each eye (emphasize sleepiness)
            val lidStroke = 4.dp.toPx()
            drawLine(
                color = eyeColor.copy(alpha = 0.5f),
                start = Offset(leftEyeCenterX - boredW * 0.55f, eyeCenterY + boredYShift - boredH * 0.5f),
                end = Offset(leftEyeCenterX + boredW * 0.55f, eyeCenterY + boredYShift - boredH * 0.5f),
                strokeWidth = lidStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = eyeColor.copy(alpha = 0.5f),
                start = Offset(rightEyeCenterX - boredW * 0.55f, eyeCenterY + boredYShift - boredH * 0.5f),
                end = Offset(rightEyeCenterX + boredW * 0.55f, eyeCenterY + boredYShift - boredH * 0.5f),
                strokeWidth = lidStroke,
                cap = StrokeCap.Round
            )

            // Small slanted flat-line mouth (bored sigh)
            drawLine(
                color = eyeColor,
                start = Offset(centerX - baseEyeW * 0.4f, mouthY - 3.dp.toPx()),
                end = Offset(centerX + baseEyeW * 0.4f, mouthY + 3.dp.toPx()),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // ─── 16. ENRAGED (โกรธจัด ตาขวางสีแดงเพลิง สู้กลับ พร้อมไอน้ำร้อน) ──────────
        AvatarEmotion.ENRAGED -> {
            val fierceRed = Color(0xFFFF1744)
            val darkCrimson = Color(0xFFB71C1C)
            val rageW = baseEyeW * 1.20f
            val rageH = baseEyeH * 0.95f

            // Red fiery aura glow behind eyes
            drawCircle(
                color = fierceRed.copy(alpha = 0.25f),
                radius = rageW * 0.95f,
                center = Offset(leftEyeCenterX, eyeCenterY)
            )
            drawCircle(
                color = fierceRed.copy(alpha = 0.25f),
                radius = rageW * 0.95f,
                center = Offset(rightEyeCenterX, eyeCenterY)
            )

            // Fierce Slanted Glaring Eyes (ตาขวางดุดัน)
            drawEnragedEye(fierceRed, darkCrimson, leftEyeCenterX, eyeCenterY, rageW * leftEyeScaleX, rageH, true)
            drawEnragedEye(fierceRed, darkCrimson, rightEyeCenterX, eyeCenterY, rageW * rightEyeScaleX, rageH, false)

            // Aggressive Sharp Zigzag Clenched Mouth (ฟันแหลมคม)
            val mouthW = baseEyeW * 1.45f
            val zigzagPath = Path().apply {
                val seg = mouthW / 6f
                moveTo(centerX - mouthW / 2f, mouthY)
                lineTo(centerX - mouthW / 2f + seg * 1f, mouthY - 10.dp.toPx())
                lineTo(centerX - mouthW / 2f + seg * 2f, mouthY + 10.dp.toPx())
                lineTo(centerX - mouthW / 2f + seg * 3f, mouthY - 10.dp.toPx())
                lineTo(centerX - mouthW / 2f + seg * 4f, mouthY + 10.dp.toPx())
                lineTo(centerX - mouthW / 2f + seg * 5f, mouthY - 10.dp.toPx())
                lineTo(centerX + mouthW / 2f, mouthY)
            }
            drawPath(zigzagPath, fierceRed, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Miter))

            // Steam / Heat Vapor Wisps rising from top corners
            val steamColor = Color.White.copy(alpha = 0.65f)
            drawSteamPuff(steamColor, centerX - baseEyeW * 1.5f, eyeCenterY - baseEyeH * 0.8f, 16.dp.toPx())
            drawSteamPuff(steamColor, centerX + baseEyeW * 1.5f, eyeCenterY - baseEyeH * 0.8f, 16.dp.toPx())
        }
    }
}

// ─── HELPER DRAW FUNCTIONS ───────────────────────────────────────────────────

/**
 * วาดดวงตาแบบ 2 วงกลมเหลื่อมซ้อนกัน (Dual Overlapping Circles — Eilik / Dfree Style)
 * ตรงตามภาพอ้างอิง:
 * - วงกลม 2 วงต่อหนึ่งดวงตา: เลเยอร์หลัง (Deep Royal Blue) และ เลเยอร์หน้า (Solid Bright Cyan)
 * - สีทึบ สะอาดตา ไม่มีประกายหรือเส้นสะท้อนด้านใน (Solid flat color, no inner sparkles)
 * - การมองตามทิศทาง (Gaze Tracking): ขยับตำแหน่งเหลื่อมซ้อนกันระหว่างวงกลมหน้าและหลัง
 *   เมื่อมองทิศทางใด วงกลมหน้าจะเลื่อนไปทิศนั้น และเปิดให้เห็นเสี้ยววงกลมสีน้ำเงินเข้มในทิศตรงข้าม
 */
private fun DrawScope.drawDualCircleEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    gazeX: Float = 0f,
    gazeY: Float = 0f,
    squashX: Float = 1f,
    squashY: Float = 1f,
    backColorOverride: Color? = null
) {
    val effW = width * squashX
    val effH = height * squashY
    val radius = effW / 2f

    // 1. เลเยอร์หลัง: สีน้ำเงินเข้มจัด (Deep Royal Blue #0012A8 ตามภาพอ้างอิง) หรือสีโทนลึกของอารมณ์นั้นๆ
    val backColor = backColorOverride ?: when {
        color == Color(0xFFFF1744) || color == Color(0xFFFF1E46) -> Color(0xFF660015) // Deep Red for Angry
        color == Color(0xFFFFD600) -> Color(0xFF774400) // Deep Bronze for Excited
        color == Color(0xFFFF4081) -> Color(0xFF770033) // Deep Crimson for Love
        color == Color(0xFF00E676) -> Color(0xFF004D25) // Deep Green for Speaking
        color == Color(0xFF80D8FF) -> Color(0xFF002266) // Deep Slate for Sad
        else -> Color(0xFF0012A8) // Deep Electric Royal Blue (#0012A8)
    }

    // 2. Parallax Overlap Shift (การขยับเหลื่อมซ้อนกันตามทิศทางการมอง)
    val maxShift = effW * 0.18f

    // วงกลมหน้าเลื่อนตามทิศทางการมอง (Gaze Vector)
    val frontOffsetX = gazeX * maxShift * 0.65f
    val frontOffsetY = gazeY * maxShift * 0.65f

    // วงกลมหลังเลื่อนในทิศตรงข้ามตาม Gaze Vector (เมื่อมองตรง gazeX=0, gazeY=0 วงกลมจะซ้อนตรงกลางพอดี 100%)
    val backOffsetX = -gazeX * maxShift * 0.35f
    val backOffsetY = -gazeY * maxShift * 0.35f

    val backCenterX = centerX + backOffsetX
    val backCenterY = centerY + backOffsetY
    val frontCenterX = centerX + frontOffsetX
    val frontCenterY = centerY + frontOffsetY

    // 3. วงกลมหลัง (Back Disc): สีน้ำเงินเข้มจัด ทึบ ขอบคมชัด ไม่มีประกาย
    drawOval(
        color = backColor,
        topLeft = Offset(backCenterX - effW / 2f, backCenterY - effH / 2f),
        size = Size(effW, effH)
    )

    // 4. วงกลมหน้า (Front Disc): สีฟ้าครามสว่างสดใส ทึบสนิท ไม่มีประกายหรือไฮไลท์ใดๆ ด้านใน
    drawOval(
        color = color,
        topLeft = Offset(frontCenterX - effW / 2f, frontCenterY - effH / 2f),
        size = Size(effW, effH)
    )
}

/**
 * ฟังก์ชันสำรองสำหรับลูกเล่น Screensaver Tricks
 */
private fun DrawScope.drawSquircleEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    squashX: Float = 1f,
    squashY: Float = 1f
) {
    drawDualCircleEye(
        color = color,
        centerX = centerX,
        centerY = centerY,
        width = width,
        height = height,
        gazeX = 0f,
        gazeY = 0f,
        squashX = squashX,
        squashY = squashY
    )
}

/**
 * วาดตายิ้มโค้งทรงพระจันทร์ครึ่งดวง (⌒) สไตล์ Eilik พร้อมเงาหลัง 3D สีน้ำเงินเข้ม
 */
private fun DrawScope.drawHappyEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    gazeX: Float = 0f,
    gazeY: Float = 0f
) {
    val arcW = width * 1.05f
    val arcH = height * 0.60f
    val strokeW = 16.dp.toPx()
    val shadowOffsetY = 5.dp.toPx() + gazeY * 3.dp.toPx()
    val shadowOffsetX = -gazeX * 3.dp.toPx()
    val shadowColor = Color(0xFF0012A8)

    // 1. Drop Shadow Under Happy Arc
    val shadowPath = Path().apply {
        moveTo(centerX + shadowOffsetX - arcW / 2f, centerY + arcH * 0.15f + shadowOffsetY)
        cubicTo(
            centerX + shadowOffsetX - arcW * 0.35f, centerY - arcH * 0.65f + shadowOffsetY,
            centerX + shadowOffsetX + arcW * 0.35f, centerY - arcH * 0.65f + shadowOffsetY,
            centerX + shadowOffsetX + arcW / 2f, centerY + arcH * 0.15f + shadowOffsetY
        )
    }
    drawPath(
        path = shadowPath,
        color = shadowColor,
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )

    // 2. Solid Happy Arc (⌒)
    val frontPath = Path().apply {
        moveTo(centerX - arcW / 2f, centerY + arcH * 0.15f)
        cubicTo(
            centerX - arcW * 0.35f, centerY - arcH * 0.65f,
            centerX + arcW * 0.35f, centerY - arcH * 0.65f,
            centerX + arcW / 2f, centerY + arcH * 0.15f
        )
    }
    drawPath(
        path = frontPath,
        color = color,
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )
}

/**
 * วาดตาหลับขีดมน (—) พร้อมเงาลึก 3D
 */
private fun DrawScope.drawSleepingEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float
) {
    val barW = width * 1.05f
    val strokeW = 14.dp.toPx()
    val shadowOffsetY = 5.dp.toPx()
    val shadowColor = Color(0xFF002244).copy(alpha = 0.85f)

    // Shadow
    drawLine(
        color = shadowColor,
        start = Offset(centerX - barW / 2f, centerY + shadowOffsetY),
        end = Offset(centerX + barW / 2f, centerY + shadowOffsetY),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
    // Core
    drawLine(
        color = color,
        start = Offset(centerX - barW / 2f, centerY),
        end = Offset(centerX + barW / 2f, centerY),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
}

/**
 * วาดตาตื่นเต้นทรงสามเหลี่ยมลูกศร (> <) สไตล์ Eilik
 */
private fun DrawScope.drawExcitedEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    isLeft: Boolean
) {
    val sizeW = width * 0.75f
    val sizeH = height * 0.70f
    val strokeW = 14.dp.toPx()
    val shadowOffsetY = 5.dp.toPx()
    val shadowColor = Color(0xFF002244).copy(alpha = 0.85f)

    val dir = if (isLeft) 1f else -1f // Left points right '>', Right points left '<'
    val path = Path().apply {
        moveTo(centerX - dir * sizeW * 0.45f, centerY - sizeH * 0.45f)
        lineTo(centerX + dir * sizeW * 0.45f, centerY)
        lineTo(centerX - dir * sizeW * 0.45f, centerY + sizeH * 0.45f)
    }

    val shadowPath = Path().apply {
        moveTo(centerX - dir * sizeW * 0.45f, centerY - sizeH * 0.45f + shadowOffsetY)
        lineTo(centerX + dir * sizeW * 0.45f, centerY + shadowOffsetY)
        lineTo(centerX - dir * sizeW * 0.45f, centerY + sizeH * 0.45f + shadowOffsetY)
    }

    drawPath(shadowPath, shadowColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * วาดตาโกรธเฉียงสีแดงเพลิงพร้อมเงาลึก 3D
 */
private fun DrawScope.drawAngryEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    isLeft: Boolean
) {
    val effW = width * 1.05f
    val effH = height * 0.85f
    val shadowOffsetY = 5.dp.toPx()
    val shadowColor = Color(0xFF330000).copy(alpha = 0.85f)
    val slant = if (isLeft) 14.dp.toPx() else -14.dp.toPx()

    val path = Path().apply {
        moveTo(centerX - effW / 2f, centerY - effH / 2f + (if (isLeft) 0f else slant.absoluteValue))
        lineTo(centerX + effW / 2f, centerY - effH / 2f + (if (isLeft) slant.absoluteValue else 0f))
        lineTo(centerX + effW / 2f - 6.dp.toPx(), centerY + effH / 2f)
        lineTo(centerX - effW / 2f + 6.dp.toPx(), centerY + effH / 2f)
        close()
    }
    val shadowPath = Path().apply {
        moveTo(centerX - effW / 2f, centerY - effH / 2f + (if (isLeft) 0f else slant.absoluteValue) + shadowOffsetY)
        lineTo(centerX + effW / 2f, centerY - effH / 2f + (if (isLeft) slant.absoluteValue else 0f) + shadowOffsetY)
        lineTo(centerX + effW / 2f - 6.dp.toPx(), centerY + effH / 2f + shadowOffsetY)
        lineTo(centerX - effW / 2f + 6.dp.toPx(), centerY + effH / 2f + shadowOffsetY)
        close()
    }

    drawPath(shadowPath, shadowColor)
    drawPath(path, color)
}

/**
 * วาดตา X X สำหรับอาการเวียนหัว สลบ มึนงง
 */
private fun DrawScope.drawCrossEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float
) {
    val strokeW = 13.dp.toPx()
    val arm = size * 0.42f
    val shadowOffsetY = 5.dp.toPx()
    val shadowColor = Color(0xFF220033).copy(alpha = 0.85f)

    // Shadow
    drawLine(shadowColor, Offset(centerX - arm, centerY - arm + shadowOffsetY), Offset(centerX + arm, centerY + arm + shadowOffsetY), strokeWidth = strokeW, cap = StrokeCap.Round)
    drawLine(shadowColor, Offset(centerX - arm, centerY + arm + shadowOffsetY), Offset(centerX + arm, centerY - arm + shadowOffsetY), strokeWidth = strokeW, cap = StrokeCap.Round)

    // Core
    drawLine(color, Offset(centerX - arm, centerY - arm), Offset(centerX + arm, centerY + arm), strokeWidth = strokeW, cap = StrokeCap.Round)
    drawLine(color, Offset(centerX - arm, centerY + arm), Offset(centerX + arm, centerY - arm), strokeWidth = strokeW, cap = StrokeCap.Round)
}

/**
 * วาดปากจู๋ (Pucker 'O' Mouth) น่ารัก สำหรับอารมณ์ Pout หรือเป่าลม
 */
private fun DrawScope.drawPuckerMouth(
    color: Color,
    centerX: Float,
    centerY: Float,
    radius: Float
) {
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(centerX, centerY),
        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
    )
}

/**
 * วาดคลื่นเสียงไมค์ 5 แท่ง (5-Bar Waveform Equalizer) เวลาหุ่นยนต์กำลังพูด
 */
private fun DrawScope.drawWaveformMouth(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    maxHeight: Float,
    audioLevel: Float
) {
    val barCount = 5
    val barWidth = 10.dp.toPx()
    val gap = 10.dp.toPx()
    val totalW = (barCount * barWidth) + ((barCount - 1) * gap)
    val startX = centerX - totalW / 2f

    val heights = floatArrayOf(0.45f, 0.85f, 1.0f, 0.75f, 0.5f)
    for (i in 0 until barCount) {
        val dynamicScale = (heights[i] * (0.6f + audioLevel * 0.7f)).coerceIn(0.25f, 1.2f)
        val bh = maxHeight * dynamicScale
        val bx = startX + i * (barWidth + gap)
        val by = centerY - bh / 2f

        // Glow
        drawRoundRect(
            color = color.copy(alpha = 0.35f),
            topLeft = Offset(bx - 3.dp.toPx(), by - 3.dp.toPx()),
            size = Size(barWidth + 6.dp.toPx(), bh + 6.dp.toPx()),
            cornerRadius = CornerRadius(barWidth, barWidth)
        )
        // Solid Bar
        drawRoundRect(
            color = color,
            topLeft = Offset(bx, by),
            size = Size(barWidth, bh),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )
    }
}

private fun DrawScope.drawGlowCircle(
    color: Color,
    center: Offset,
    radiusX: Float,
    radiusY: Float
) {
    // Outer Ambient Glow
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.55f), Color.Transparent),
            center = center,
            radius = radiusX * 1.7f
        ),
        topLeft = Offset(center.x - radiusX * 1.7f, center.y - radiusY * 1.7f),
        size = Size(radiusX * 3.4f, radiusY * 3.4f)
    )
    // Core Bright LED Dot
    drawOval(
        color = color,
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * 2f, radiusY * 2f)
    )
}

private fun DrawScope.drawSmileArc(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float
) {
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(centerX - width / 2f, centerY - height / 2f),
        size = Size(width, height),
        style = Stroke(width = 6f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawFrownArc(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float
) {
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(centerX - width / 2f, centerY - height / 2f),
        size = Size(width, height),
        style = Stroke(width = 5.5f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawHeart(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float
) {
    val heartPath = Path().apply {
        val w = size
        val h = size * 0.95f
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
    // Glow
    drawPath(heartPath, color.copy(alpha = 0.45f), style = Stroke(width = 8f, cap = StrokeCap.Round))
    // Core
    drawPath(heartPath, color)
}

private fun DrawScope.drawQuestionMark(
    color: Color,
    centerX: Float,
    centerY: Float,
    height: Float
) {
    val qPath = Path().apply {
        val w = height * 0.55f
        moveTo(centerX - w * 0.45f, centerY - height * 0.35f)
        cubicTo(
            centerX - w * 0.5f, centerY - height * 0.6f,
            centerX + w * 0.5f, centerY - height * 0.6f,
            centerX + w * 0.35f, centerY - height * 0.35f
        )
        cubicTo(
            centerX + w * 0.25f, centerY - height * 0.15f,
            centerX, centerY - height * 0.1f,
            centerX, centerY + height * 0.05f
        )
    }
    drawPath(qPath, color, style = Stroke(width = 6f, cap = StrokeCap.Round))
    drawCircle(color, radius = 3.5f, center = Offset(centerX, centerY + height * 0.22f))
}

private fun DrawScope.drawZLetter(
    color: Color,
    x: Float,
    y: Float,
    size: Float
) {
    val zPath = Path().apply {
        moveTo(x - size / 2f, y - size / 2f)
        lineTo(x + size / 2f, y - size / 2f)
        lineTo(x - size / 2f, y + size / 2f)
        lineTo(x + size / 2f, y + size / 2f)
    }
    drawPath(zPath, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
}

private fun DrawScope.drawThinkingHand(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float
) {
    // Stylized hand touching chin (Finger pointing up, fist curled below)
    val handPath = Path().apply {
        val r = size * 0.4f
        // Forefinger touching chin
        moveTo(centerX + r * 0.3f, centerY - r * 1.1f)
        lineTo(centerX + r * 0.3f, centerY - r * 0.2f)
        // Curled thumb and knuckles
        arcTo(
            rect = Rect(centerX - r, centerY - r * 0.5f, centerX + r * 0.8f, centerY + r * 0.8f),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 200f,
            forceMoveTo = false
        )
    }
    drawPath(handPath, color, style = Stroke(width = 5.5f, cap = StrokeCap.Round))
}

private fun DrawScope.drawSpiralEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    rotationDeg: Float
) {
    rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
        // Draw 3 concentric rotating spiral arcs
        for (i in 1..3) {
            val r = radius * (i / 3.2f)
            drawArc(
                color = color,
                startAngle = (i * 65f),
                sweepAngle = 230f,
                useCenter = false,
                topLeft = Offset(centerX - r, centerY - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = 5.5f, cap = StrokeCap.Round)
            )
        }
    }
}

private fun DrawScope.drawEnragedEye(
    color: Color,
    backColor: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    isLeft: Boolean
) {
    val effW = width * 1.10f
    val effH = height * 0.90f
    val slant = if (isLeft) 20.dp.toPx() else -20.dp.toPx()
    val shadowOffsetY = 6.dp.toPx()

    // Angry angular shape with steep inward tilt (ตาขวางดุดัน)
    val path = Path().apply {
        moveTo(centerX - effW / 2f, centerY - effH / 2f + (if (isLeft) 0f else slant.absoluteValue))
        lineTo(centerX + effW / 2f, centerY - effH / 2f + (if (isLeft) slant.absoluteValue else 0f))
        lineTo(centerX + effW * 0.35f, centerY + effH / 2f)
        lineTo(centerX - effW * 0.35f, centerY + effH / 2f)
        close()
    }
    val shadowPath = Path().apply {
        moveTo(centerX - effW / 2f, centerY - effH / 2f + (if (isLeft) 0f else slant.absoluteValue) + shadowOffsetY)
        lineTo(centerX + effW / 2f, centerY - effH / 2f + (if (isLeft) slant.absoluteValue else 0f) + shadowOffsetY)
        lineTo(centerX + effW * 0.35f, centerY + effH / 2f + shadowOffsetY)
        lineTo(centerX - effW * 0.35f, centerY + effH / 2f + shadowOffsetY)
        close()
    }

    drawPath(shadowPath, backColor)
    drawPath(path, color)

    // Inner glowing slit pupil
    val pupilW = effW * 0.22f
    val pupilH = effH * 0.70f
    drawOval(
        color = Color(0xFFFFEB3B),
        topLeft = Offset(centerX - pupilW / 2f, centerY - pupilH / 2f),
        size = Size(pupilW, pupilH)
    )
}

private fun DrawScope.drawSteamPuff(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float
) {
    val puffPath = Path().apply {
        moveTo(centerX, centerY)
        cubicTo(centerX - size * 0.4f, centerY - size * 0.5f, centerX - size * 0.2f, centerY - size * 1.1f, centerX + size * 0.1f, centerY - size * 1.4f)
        cubicTo(centerX + size * 0.5f, centerY - size * 1.1f, centerX + size * 0.4f, centerY - size * 0.5f, centerX, centerY)
    }
    drawPath(puffPath, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
}


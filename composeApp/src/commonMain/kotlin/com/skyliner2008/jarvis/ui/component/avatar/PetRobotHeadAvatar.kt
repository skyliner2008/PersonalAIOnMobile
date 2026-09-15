package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.animateColorAsState
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
import kotlinx.coroutines.launch
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
enum class AvatarEngineType {
    /** High-performance procedurally drawn Compose Canvas (zero binary dependency, 50+ LOOI moodsets) */
    COMPOSE_CANVAS,
    /** State-machine driven Rive Runtime (.riv binary) */
    RIVE_STATE_MACHINE
}

/**
 * @param riveReaction touch event (holographic hand) — drives the Rive `react` channel
 * @param isMissileBarrageActive fight-back barrage — drives the Rive AngryMissile story
 * @param lastShakeTime / lastFaceTime sensor timestamps for Rive ShakeAngry / Detected
 * The Canvas engine gets the same events through PetModeScreen's own overlays.
 */
@Composable
fun PetRobotHeadAvatar(
    state: AvatarState,
    modifier: Modifier = Modifier,
    engineType: AvatarEngineType = AvatarEngineType.COMPOSE_CANVAS,
    riveReaction: RiveReactionEvent? = null,
    isMissileBarrageActive: Boolean = false,
    lastShakeTime: Long = 0L,
    lastFaceTime: Long = 0L
) {
    if (engineType == AvatarEngineType.RIVE_STATE_MACHINE) {
        RivePetAvatar(
            state = state,
            modifier = modifier,
            reaction = riveReaction,
            isMissileBarrageActive = isMissileBarrageActive,
            lastShakeTime = lastShakeTime,
            lastFaceTime = lastFaceTime
        )
        return
    }

    val localLayout = LocalAvatarLayout.current
    val localDetailLevel = LocalAvatarDetailLevel.current
    val infinite = rememberInfiniteTransition(label = "pet_head_living")
    val living = rememberLivingMotionState()
    val particles = rememberPetParticleMotionState()

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

    // ─── 2. Natural Eye Blinking Morph Animation (Circle -> Slit line -> Circle)
    val naturalBlinkScaleY by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3800
                1f at 0
                1f at 3450
                0.06f at 3620 using FastOutSlowInEasing // ~170ms morph squash down
                0.06f at 3680
                1f at 3800 using FastOutSlowInEasing   // ~120ms morph open
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "natural_blink_morph"
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
    // shared with the Rive engine so both show the same face (RiveAvatarBinding.kt)
    val effectiveEmotion = state.effectiveEmotion()

    // ─── 7. Local Layout Integration ──────────────────────────────────────────
    // localLayout is defined above

    // ─── 8. Gaze & Face-Tracking Low-Pass Jitter Filtering ───────────────────
    val gazeStabilizer = remember { GazeStabilizer() }
    val (filteredGazeX, filteredGazeY) = remember(state.gazeOffsetX, state.gazeOffsetY) {
        gazeStabilizer.update(state.gazeOffsetX, state.gazeOffsetY)
    }

    val smoothedGazeX by animateFloatAsState(
        targetValue = filteredGazeX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "smoothed_gaze_x"
    )
    val smoothedGazeY by animateFloatAsState(
        targetValue = filteredGazeY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "smoothed_gaze_y"
    )

    // ─── 9. Head Tilt Smooth Animation (with gesture override & 350ms sync) ───
    val gestureTilt = state.faceState.gesture.headTiltOverride()
    val targetHeadTilt = when {
        gestureTilt != null -> gestureTilt
        state.isDizzy || effectiveEmotion == AvatarEmotion.DIZZY -> 4f * sin(breathingOffsetY * 0.5f)
        effectiveEmotion == AvatarEmotion.THINKING -> 5.5f
        effectiveEmotion == AvatarEmotion.CONFUSED -> -4.0f
        effectiveEmotion == AvatarEmotion.POUT -> -3.0f
        effectiveEmotion == AvatarEmotion.SURPRISED -> 0f  // Alert upright posture
        effectiveEmotion == AvatarEmotion.BORED -> 4.5f    // Lazy tired tilt
        effectiveEmotion == AvatarEmotion.ENRAGED || effectiveEmotion == AvatarEmotion.EVIL -> 0f
        else -> smoothedGazeX * 3.0f
    }
    val headTiltAnim by animateFloatAsState(
        targetValue = targetHeadTilt,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "head_tilt"
    )

    // ─── 10. Animated Vertical Shift for Mouth Balance ────────────────────────
    val isSpeaking = state.isSpeaking || effectiveEmotion == AvatarEmotion.SPEAKING
    val hasMouth = when {
        state.faceState.eyeTrick != EyeTrickState.NONE -> false
        effectiveEmotion == AvatarEmotion.IDLE ||
        effectiveEmotion == AvatarEmotion.HAPPY ||
        effectiveEmotion == AvatarEmotion.DEAD ||
        effectiveEmotion == AvatarEmotion.LAUGHING ||
        effectiveEmotion == AvatarEmotion.MUSIC ||
        effectiveEmotion == AvatarEmotion.VR_MODE ||
        effectiveEmotion == AvatarEmotion.DIVING ||
        effectiveEmotion == AvatarEmotion.FOCUSED ||
        effectiveEmotion == AvatarEmotion.SHY ||
        effectiveEmotion == AvatarEmotion.WINK ||
        effectiveEmotion == AvatarEmotion.CAMERA_MODE -> isSpeaking
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

    // ─── 11. Continuous Emotion Morph & Parametric Shapes ────────────────────
    var previousEmotion by remember { mutableStateOf(effectiveEmotion) }
    var displayedEmotion by remember { mutableStateOf(effectiveEmotion) }
    val emotionTransition = remember { Animatable(1f) }

    val currentTargetLeftParams = effectiveEmotion.toEyeShapeParams(isLeft = true)
    val currentTargetRightParams = effectiveEmotion.toEyeShapeParams(isLeft = false)

    val animatedLeftParams = rememberAnimatedEyeShapeParams(currentTargetLeftParams, label = "LeftEye")
    val animatedRightParams = rememberAnimatedEyeShapeParams(currentTargetRightParams, label = "RightEye")

    LaunchedEffect(effectiveEmotion) {
        if (effectiveEmotion != displayedEmotion) {
            previousEmotion = displayedEmotion
            displayedEmotion = effectiveEmotion
            // All emotions are 100% parametric: continuous morphing handled by rememberAnimatedEyeShapeParams
            emotionTransition.snapTo(1f)
        }
    }

    // Dynamic Eye Color Animation (Synchronized 350ms FastOutSlowInEasing)
    val targetEyeColor = when (displayedEmotion) {
        AvatarEmotion.SPEAKING -> Color(0xFF00E676)
        AvatarEmotion.LOVE, AvatarEmotion.ROMANTIC, AvatarEmotion.SHY -> NeonPink
        AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED, AvatarEmotion.EVIL -> NeonRed
        AvatarEmotion.SAD, AvatarEmotion.CRYING -> Color(0xFF80D8FF)
        AvatarEmotion.BORED, AvatarEmotion.SLEEPING -> NeonGrey
        AvatarEmotion.EXCITED -> NeonGold
        AvatarEmotion.DIZZY -> NeonMint
        else -> NeonCyan
    }
    val animatedEyeColor by animateColorAsState(
        targetValue = targetEyeColor,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "eyeColor"
    )

    // Morphing progress states (Synchronized 350ms FastOutSlowInEasing)
    val syncAnimSpec = tween<Float>(350, easing = FastOutSlowInEasing)
    val eyeSlantProgress by animateFloatAsState(
        targetValue = if (effectiveEmotion == AvatarEmotion.ANGRY || effectiveEmotion == AvatarEmotion.ENRAGED || effectiveEmotion == AvatarEmotion.EVIL) 1f else 0f,
        animationSpec = syncAnimSpec,
        label = "eyeSlant"
    )
    val fangsProgress by animateFloatAsState(
        targetValue = if (effectiveEmotion == AvatarEmotion.ANGRY || effectiveEmotion == AvatarEmotion.EVIL) 1f else 0f,
        animationSpec = syncAnimSpec,
        label = "fangs"
    )
    val angerVeinProgress by animateFloatAsState(
        targetValue = if (effectiveEmotion == AvatarEmotion.ANGRY) 1f else 0f,
        animationSpec = syncAnimSpec,
        label = "angerVein"
    )
    val sadProgress by animateFloatAsState(
        targetValue = if (effectiveEmotion == AvatarEmotion.SAD) 1f else 0f,
        animationSpec = syncAnimSpec,
        label = "sadProgress"
    )
    val sleepProgress by animateFloatAsState(
        targetValue = if (effectiveEmotion == AvatarEmotion.SLEEPING || effectiveEmotion == AvatarEmotion.CAMERA_MODE) 1f else 0f,
        animationSpec = syncAnimSpec,
        label = "sleepProgress"
    )
    val surpriseProgress by animateFloatAsState(
        targetValue = if (effectiveEmotion == AvatarEmotion.SURPRISED) 1f else 0f,
        animationSpec = syncAnimSpec,
        label = "surpriseProgress"
    )
    val happyProgress by animateFloatAsState(
        targetValue = if (effectiveEmotion == AvatarEmotion.HAPPY) 1f else 0f,
        animationSpec = syncAnimSpec,
        label = "happyProgress"
    )

    // ─── 12. Screensaver Eye Trick Animation (Ping-Pong, Tired Bounce, Snooker Shot) ─
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

    // Smooth 3D Rotation based on Gaze with Low-Pass Filter
    val targetRotationY = smoothedGazeX.coerceIn(-1f, 1f) * 35f // Max 35 degrees yaw
    val targetRotationX = -smoothedGazeY.coerceIn(-1f, 1f) * 20f // Max 20 degrees pitch
    val animatedRotationY by animateFloatAsState(targetValue = targetRotationY, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "3d_yaw")
    val animatedRotationX by animateFloatAsState(targetValue = targetRotationX, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "3d_pitch")

    Canvas(modifier = modifier.graphicsLayer {
        rotationY = animatedRotationY
        rotationX = animatedRotationX
        cameraDistance = 14f * density // Determines perspective strength. Lower = stronger perspective.
    }) {
        val canvasW = size.width
        val canvasH = size.height
        if (canvasW <= 0f || canvasH <= 0f) return@Canvas

        val centerX = canvasW / 2f
        val centerY = (canvasH / 2f) + breathingOffsetY + living.breathingOffsetY

        // Calculate gaze displacement from Camera or Touch + living Saccadic eye darting
        val maxGazeX = canvasW * 0.16f
        val maxGazeY = canvasH * 0.12f
        val gazeX = smoothedGazeX.coerceIn(-1f, 1f)
        val gazeY = smoothedGazeY.coerceIn(-1f, 1f)
        val gazeDisplacementX = gazeX * maxGazeX + living.saccadeOffsetX
        val gazeDisplacementY = gazeY * maxGazeY + living.saccadeOffsetY

        // Effective eye blink with natural morphing (circle -> flat slit -> circle)
        val naturalBlink = when (displayedEmotion) {
            AvatarEmotion.SLEEPING, AvatarEmotion.CAMERA_MODE -> 0.08f
            AvatarEmotion.WINK, AvatarEmotion.DIZZY, AvatarEmotion.DEAD, AvatarEmotion.LAUGHING, AvatarEmotion.DISGUSTED -> 1f
            AvatarEmotion.SURPRISED, AvatarEmotion.ENRAGED, AvatarEmotion.EVIL -> 1f
            AvatarEmotion.BORED -> naturalBlinkScaleY.coerceIn(0.35f, 0.65f)
            else -> if (state.isDizzy) 1f else (naturalBlinkScaleY * (0.85f + 0.15f * living.microBlinkFactor))
        }
        val effectiveBlink = naturalBlink.coerceIn(0.06f, 1.25f)

        // Apply Whole-Face Respiration Scaling (volume-conserving breathing)
        scale(
            scaleX = living.breathingScaleX,
            scaleY = living.breathingScaleY,
            pivot = Offset(centerX, centerY)
        ) {
            // Apply Head/Face Tilt Rotation around center with living organic sway
            rotate(degrees = headTiltAnim + living.headSwayDeg, pivot = Offset(centerX, centerY)) {
            // ═══════════════════════════════════════════════════════════════
            // PURE OLED LIVING FACE (LOOI ROBOT: 20 MOODSET - NEON CYAN STYLE)
            // ═══════════════════════════════════════════════════════════════

            val eyeColor = animatedEyeColor

            // 2. Dynamic Eye Dimensions (Large, Expressive Dual Circles — LOOI / Eilik Style)
            val layout = if (localLayout.eyeDiameter > 0f && localLayout.containerWidth > 0f) {
                localLayout
            } else {
                calculateAvatarLayout(canvasW, canvasH)
            }
            val isLandscape = layout.isLandscape
            val eyeDiameter = layout.eyeDiameter
            val baseEyeW = eyeDiameter * state.faceScaleFactor.coerceIn(0.85f, 1.30f)
            val baseEyeH = eyeDiameter * state.faceScaleFactor.coerceIn(0.85f, 1.30f)
            val baseSpacing = layout.baseSpacing

            // 3D Spherical Face Perspective Distortion is now handled by Modifier.graphicsLayer in the Canvas
            // Disable manual scaling to prevent double-scaling
            val leftEyeScaleX = 1f
            val rightEyeScaleX = 1f
            val leftEyeScaleY = 1f
            val rightEyeScaleY = 1f

            // Spherical Wrap-around Spacing (ระยะห่างตาดึงเข้าหากันเมื่อหันสุด เสมือนอยู่บนทรงกลม)
            val spacingFactor = 1f - 0.12f * abs(gazeX)
            val dynamicSpacing = baseSpacing * spacingFactor

            // Shift Center based on spherical curve
            val faceCenterX = centerX + gazeDisplacementX
            val leftEyeCenterX = faceCenterX - dynamicSpacing
            val rightEyeCenterX = faceCenterX + dynamicSpacing


            // เมื่อมีปากปรากฏเข้ามา ดวงตาจะขยับขึ้นด้านบนเล็กน้อย (~8.5% ของขนาดตา) เพื่อจัดสัดส่วนใบหน้าให้สมดุลกึ่งกลางจอ
            val eyeMouthUpwardShift = eyeDiameter * 0.085f * mouthShiftProgress
            val faceCenterY = centerY + gazeDisplacementY - eyeMouthUpwardShift
            val eyeCenterY = faceCenterY

            // 3. Disney Anticipation Squash & Stretch Physics
            val transitionProg = emotionTransition.value
            val anticipationSquashY = 1f - 0.08f * sin(transitionProg * PI.toFloat())
            val anticipationSquashX = 1f + 0.05f * sin(transitionProg * PI.toFloat())

            scale(scaleX = anticipationSquashX, scaleY = anticipationSquashY, pivot = Offset(faceCenterX, eyeCenterY)) {
                // 4. Dynamic Eyebrows (Brows) - Shown conditionally for expressive emotions only
                val showEyebrows = when (effectiveEmotion) {
                    AvatarEmotion.THINKING,
                    AvatarEmotion.ENRAGED,
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
                        AvatarEmotion.ANGRY -> Triple(18f * eyeSlantProgress, -18f * eyeSlantProgress, 6.dp.toPx() * eyeSlantProgress)
                        AvatarEmotion.ENRAGED -> Triple(24f, -24f, 8.dp.toPx()) // Deeply furrowed menacing V-brows
                        AvatarEmotion.SAD -> Triple(-15f * sadProgress, 15f * sadProgress, 2.dp.toPx() * sadProgress)
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

                // 5. Render Active Emotion Eyes & Screensaver Tricks with Seamless Transition
                if (transitionProg < 1f && previousEmotion != displayedEmotion) {
                    // Outgoing emotion fading out
                    renderPetEmotion(
                        emotion = previousEmotion,
                        eyeTrick = state.faceState.eyeTrick,
                        trickProgress = trickProgress.value,
                        centerX = faceCenterX,
                        centerY = faceCenterY,
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
                        isSpeaking = state.isSpeaking || previousEmotion == AvatarEmotion.SPEAKING,
                        alpha = (1f - transitionProg).coerceIn(0f, 1f),
                        eyeSlantProgress = eyeSlantProgress,
                        fangsProgress = fangsProgress,
                        angerVeinProgress = angerVeinProgress,
                        sadProgress = sadProgress,
                        sleepProgress = sleepProgress,
                        surpriseProgress = surpriseProgress,
                        happyProgress = happyProgress,
                        living = living,
                        particles = particles,
                        leftEyeParams = animatedLeftParams,
                        rightEyeParams = animatedRightParams,
                        detailLevel = localDetailLevel
                    )
                    // Incoming emotion fading in
                    renderPetEmotion(
                        emotion = displayedEmotion,
                        eyeTrick = state.faceState.eyeTrick,
                        trickProgress = trickProgress.value,
                        centerX = faceCenterX,
                        centerY = faceCenterY,
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
                        isSpeaking = state.isSpeaking || displayedEmotion == AvatarEmotion.SPEAKING,
                        alpha = transitionProg.coerceIn(0f, 1f),
                        eyeSlantProgress = eyeSlantProgress,
                        fangsProgress = fangsProgress,
                        angerVeinProgress = angerVeinProgress,
                        sadProgress = sadProgress,
                        sleepProgress = sleepProgress,
                        surpriseProgress = surpriseProgress,
                        happyProgress = happyProgress,
                        living = living,
                        particles = particles,
                        leftEyeParams = animatedLeftParams,
                        rightEyeParams = animatedRightParams,
                        detailLevel = localDetailLevel
                    )
                } else {
                    renderPetEmotion(
                        emotion = displayedEmotion,
                        eyeTrick = state.faceState.eyeTrick,
                        trickProgress = trickProgress.value,
                        centerX = faceCenterX,
                        centerY = faceCenterY,
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
                        isSpeaking = state.isSpeaking || displayedEmotion == AvatarEmotion.SPEAKING,
                        alpha = 1f,
                        eyeSlantProgress = eyeSlantProgress,
                        fangsProgress = fangsProgress,
                        angerVeinProgress = angerVeinProgress,
                        sadProgress = sadProgress,
                        sleepProgress = sleepProgress,
                        surpriseProgress = surpriseProgress,
                        happyProgress = happyProgress,
                        living = living,
                        particles = particles,
                        leftEyeParams = animatedLeftParams,
                        rightEyeParams = animatedRightParams,
                        detailLevel = localDetailLevel
                    )
                }

            }
        }
        }
    }
}

/**
 * อารมณ์ที่มีฟังก์ชันเรนเดอร์ดวงตาและใบหน้าเฉพาะตัว
 * ต้องไม่วาด parametric squircle eye ซ้อนทับด้านล่าง เพื่อป้องกันปัญหาเลเยอร์ตาซ้อนกัน 2 ชุด
 */
private val emotionsWithDedicatedEyeRenderer: Set<AvatarEmotion> = setOf(
    AvatarEmotion.HAPPY,
    AvatarEmotion.ANGRY,
    AvatarEmotion.SLEEPING,
    AvatarEmotion.CONFUSED,
    AvatarEmotion.EATING,
    AvatarEmotion.DRINKING,
    AvatarEmotion.WINK,
    AvatarEmotion.DEAD,
    AvatarEmotion.LAUGHING,
    AvatarEmotion.MUSIC,
    AvatarEmotion.VR_MODE,
    AvatarEmotion.DIVING,
    AvatarEmotion.EVIL,
    AvatarEmotion.FOCUSED,
    AvatarEmotion.EXCITED,
    AvatarEmotion.SHY,
    AvatarEmotion.SURPRISED,
    AvatarEmotion.DISGUSTED,
    AvatarEmotion.CAMERA_MODE,
    AvatarEmotion.SAD,
    AvatarEmotion.LOVE,
    AvatarEmotion.THINKING,
    AvatarEmotion.POUT,
    AvatarEmotion.ENRAGED,
    AvatarEmotion.PUZZLED,
    AvatarEmotion.SICK,
    AvatarEmotion.RICH,
    AvatarEmotion.CRYING,
    AvatarEmotion.READING,
    AvatarEmotion.GAMING,
    AvatarEmotion.TRAVELING,
    AvatarEmotion.WORKING,
    AvatarEmotion.COLD,
    AvatarEmotion.HOT,
    AvatarEmotion.DETECTIVE,
    AvatarEmotion.COOKING,
    AvatarEmotion.ART_MODE,
    AvatarEmotion.SPACE,
    AvatarEmotion.PARTY,
    AvatarEmotion.DREAMING,
    AvatarEmotion.EXHAUSTED,
    AvatarEmotion.ELECTRIC,
    AvatarEmotion.SNEAKY,
    AvatarEmotion.HERO,
    AvatarEmotion.GLITCHED,
    AvatarEmotion.MAGIC,
    AvatarEmotion.SPORTY,
    AvatarEmotion.SCIENTIST,
    AvatarEmotion.SCARED,
    AvatarEmotion.WARRIOR,
    AvatarEmotion.LOW_BATTERY,
    AvatarEmotion.ROMANTIC,
    AvatarEmotion.DIZZY,
    AvatarEmotion.BORED
)

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
    isSpeaking: Boolean,
    alpha: Float = 1f,
    eyeSlantProgress: Float = 1f,
    fangsProgress: Float = 1f,
    angerVeinProgress: Float = 1f,
    sadProgress: Float = 1f,
    sleepProgress: Float = 0f,
    surpriseProgress: Float = 0f,
    happyProgress: Float = 0f,
    living: LivingMotionState = LivingMotionState(),
    particles: PetParticleMotionState = PetParticleMotionState(),
    leftEyeParams: EyeShapeParams? = null,
    rightEyeParams: EyeShapeParams? = null,
    detailLevel: AvatarDetailLevel = AvatarDetailLevel.RICH
) {
    val effAlpha = alpha.coerceIn(0f, 1f)
    val cyanLed = eyeColor.copy(alpha = (eyeColor.alpha * effAlpha).coerceIn(0f, 1f))
    val redLed = Color(0xFFFF3B5C).copy(alpha = effAlpha)
    val pinkLed = Color(0xFFFF4081).copy(alpha = effAlpha)
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

                drawSquircleEye(cyanLed, leftEyeCenterX + pingPongX, eyeCenterY, baseEyeW * leftEyeScaleX, baseEyeH, squashX, squashY, alpha = effAlpha)
                drawSquircleEye(cyanLed, rightEyeCenterX + pingPongX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH, squashX, squashY, alpha = effAlpha)
            }
            EyeTrickState.TIRED_BOUNCE -> {
                if (p < 0.60f) {
                    val sub = p / 0.60f
                    val bounceH = abs(sin(sub * PI * 3f).toFloat())
                    val bounceY = -bounceH * 60.dp.toPx()
                    val squashY = if (bounceH < 0.25f) 0.65f else 1.25f
                    val squashX = 1f / squashY
                    drawSquircleEye(cyanLed, leftEyeCenterX, eyeCenterY + bounceY, baseEyeW * leftEyeScaleX, baseEyeH, squashX, squashY, alpha = effAlpha)
                    drawSquircleEye(cyanLed, rightEyeCenterX, eyeCenterY + bounceY, baseEyeW * rightEyeScaleX, baseEyeH, squashX, squashY, alpha = effAlpha)
                } else {
                    val sub = (p - 0.60f) / 0.40f
                    val tiredH = baseEyeH * (0.35f + 0.12f * sin(sub * PI * 4f).toFloat())
                    drawSquircleEye(cyanLed, leftEyeCenterX, eyeCenterY + 12.dp.toPx(), baseEyeW * 1.1f * leftEyeScaleX, tiredH, alpha = effAlpha)
                    drawSquircleEye(cyanLed, rightEyeCenterX, eyeCenterY + 12.dp.toPx(), baseEyeW * 1.1f * rightEyeScaleX, tiredH, alpha = effAlpha)
                    drawPuckerMouth(cyanLed, centerX, mouthY, 9.dp.toPx() + 3.dp.toPx() * sin(sub * PI * 4f).toFloat(), alpha = effAlpha)
                }
            }
            EyeTrickState.SNOOKER_SHOT -> {
                if (p < 0.35f) {
                    val sub = p / 0.35f
                    val winkH = baseEyeH * (1f - sub * 0.75f)
                    drawSquircleEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW * leftEyeScaleX, winkH, alpha = effAlpha)
                    val pullbackX = -sub * 24.dp.toPx()
                    drawSquircleEye(cyanLed, rightEyeCenterX + pullbackX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH, 1.1f, 0.9f, alpha = effAlpha)
                } else if (p < 0.65f) {
                    val sub = (p - 0.35f) / 0.30f
                    val shotX = sub * (visorW * 0.35f)
                    val hitWall = sub > 0.85f
                    val squashX = if (hitWall) 0.55f else 1.35f
                    val squashY = 1f / squashX
                    drawSquircleEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW * 1.15f * leftEyeScaleX, baseEyeH * 1.15f, alpha = effAlpha)
                    drawSquircleEye(cyanLed, rightEyeCenterX + shotX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH, squashX, squashY, alpha = effAlpha)
                } else {
                    val sub = (p - 0.65f) / 0.35f
                    val reboundX = (visorW * 0.35f) * (1f - sub)
                    drawSquircleEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW * leftEyeScaleX, baseEyeH, alpha = effAlpha)
                    drawSquircleEye(cyanLed, rightEyeCenterX + reboundX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH, alpha = effAlpha)
                    drawSmileArc(cyanLed, centerX, mouthY, baseEyeW * 0.9f, 18.dp.toPx(), alpha = effAlpha)
                }
            }
            EyeTrickState.NONE -> {}
        }
        return
    }

    val isParametric = (leftEyeParams != null && rightEyeParams != null)
    if (isParametric && emotion !in emotionsWithDedicatedEyeRenderer) {
        val lParams = leftEyeParams!!.copy(
            openness = (leftEyeParams.openness * blinkFactor).coerceIn(0.06f, 1.35f)
        )
        val rParams = rightEyeParams!!.copy(
            openness = (rightEyeParams.openness * blinkFactor).coerceIn(0.06f, 1.35f)
        )

        val leftEyePath = buildParametricEyePath(
            params = lParams,
            centerX = leftEyeCenterX,
            centerY = eyeCenterY,
            baseW = baseEyeW * leftEyeScaleX,
            baseH = baseEyeH,
            isLeft = true,
            gazeShiftX = gazeX * 4.dp.toPx(),
            gazeShiftY = gazeY * 3.dp.toPx()
        )
        val rightEyePath = buildParametricEyePath(
            params = rParams,
            centerX = rightEyeCenterX,
            centerY = eyeCenterY,
            baseW = baseEyeW * rightEyeScaleX,
            baseH = baseEyeH,
            isLeft = false,
            gazeShiftX = gazeX * 4.dp.toPx(),
            gazeShiftY = gazeY * 3.dp.toPx()
        )

        val shadowColor = getEyeDeepShadowColor(cyanLed)

        drawParametricEye(
            color = cyanLed,
            path = leftEyePath,
            shadowColor = shadowColor,
            shadowOffsetY = 4.5.dp.toPx(),
            alpha = effAlpha
        )
        drawParametricEye(
            color = cyanLed,
            path = rightEyePath,
            shadowColor = shadowColor,
            shadowOffsetY = 4.5.dp.toPx(),
            alpha = effAlpha
        )
    }

    when (emotion) {
        // ─── 1. IDLE / NORMAL (ดวงตา Squircle นีออนไซแอน 2D Shallow Depth สะอาดตา ไร้ปาก) ───
        AvatarEmotion.IDLE -> {
            if (!isParametric) {
                val blinkSquashX = if (blinkFactor < 1f) 1f + (1f - blinkFactor) * 0.18f else 1f
                val leftH = baseEyeH * blinkFactor.coerceAtLeast(0.08f)
                val rightH = baseEyeH * blinkFactor.coerceAtLeast(0.08f)
                val leftW = baseEyeW * leftEyeScaleX
                val rightW = baseEyeW * rightEyeScaleX

                drawDualCircleEye(cyanLed, leftEyeCenterX, eyeCenterY, leftW, leftH, gazeX, gazeY, blinkSquashX, 1f, alpha = effAlpha)
                drawDualCircleEye(cyanLed, rightEyeCenterX, eyeCenterY, rightW, rightH, gazeX, gazeY, blinkSquashX, 1f, alpha = effAlpha)
            }

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.6f, 32.dp.toPx(), audioLevel)
            }
        }

        // ─── 2. HAPPY (ยิ้มตาหยีโค้ง ⌒ ⌒ สไตล์ LOOI Minimalist + ปากโค้งยิ้ม) ─────────────────
        AvatarEmotion.HAPPY -> {
            val leftW = baseEyeW * leftEyeScaleX
            val rightW = baseEyeW * rightEyeScaleX

            drawHappyEye(cyanLed, leftEyeCenterX, eyeCenterY, leftW, baseEyeH, gazeX, gazeY, alpha = effAlpha)
            drawHappyEye(cyanLed, rightEyeCenterX, eyeCenterY, rightW, baseEyeH, gazeX, gazeY, alpha = effAlpha)

            // Upward curved smiling mouth ◡
            drawSmileArc(cyanLed, centerX, mouthY, baseEyeW * 0.9f, 18.dp.toPx(), alpha = effAlpha)

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.5f, 28.dp.toPx(), audioLevel)
            }
        }

        // ─── 3. ANGRY (ตาเฉียงสีแดง + ปากสีแดงแยกเขี้ยวขาว v v + เส้นเลือดปูด 💢) ───────────
        AvatarEmotion.ANGRY -> {
            val angryJitterX = living.angerJitterX * 1.5.dp.toPx()
            val angryJitterY = living.angerJitterY * 1.2.dp.toPx()
            val dynamicSlant = eyeSlantProgress * (1f + living.angerBrowSlant / 22f).coerceIn(0.75f, 1.35f)

            drawAngryEye(redLed, leftEyeCenterX + angryJitterX, eyeCenterY + angryJitterY, baseEyeW * leftEyeScaleX, baseEyeH, true, slantProgress = dynamicSlant, alpha = effAlpha)
            drawAngryEye(redLed, rightEyeCenterX - angryJitterX, eyeCenterY + angryJitterY, baseEyeW * rightEyeScaleX, baseEyeH, false, slantProgress = dynamicSlant, alpha = effAlpha)

            // Red grimace outline with sharp white fangs v v
            val angryMouthY = eyeCenterY + baseEyeH * 0.72f + angryJitterY * 0.5f
            drawFrownArc(redLed, centerX, angryMouthY, baseEyeW * 1.1f, 12.dp.toPx(), alpha = effAlpha)
            drawLooiFangs(centerX, angryMouthY + 2.dp.toPx(), 13.dp.toPx(), progress = 1f, alpha = effAlpha)

            val veinSize = 24.dp.toPx() * living.veinPulse
            drawLooiAngerVein(rightEyeCenterX + baseEyeW * 0.70f, eyeCenterY - baseEyeH * 0.65f, veinSize, progress = angerVeinProgress, alpha = effAlpha)

            if (isSpeaking) {
                drawWaveformMouth(redLed, centerX, mouthY, baseEyeW * 1.5f, 28.dp.toPx(), audioLevel)
            }
        }

        // ─── 4. SLEEPING (หลับตาขีดมน — — + ปากกรน o + Zzz ลอย) ───────────────────
        AvatarEmotion.SLEEPING -> {
            val sleepNodY = eyeCenterY + living.nodOffOffsetY * 1.dp.toPx()
            val sleepH = 14.dp.toPx() + (baseEyeH - 14.dp.toPx()) * (1f - sleepProgress)
            drawSleepingEye(cyanLed, leftEyeCenterX, sleepNodY, baseEyeW, barHeight = sleepH, alpha = effAlpha)
            drawSleepingEye(cyanLed, rightEyeCenterX, sleepNodY, baseEyeW, barHeight = sleepH, alpha = effAlpha)

            // Animated breathing / snoring mouth (o) expanding and contracting with sleep breath
            val snorePulse: Float = sin(living.loopSlow * 2f * PI.toFloat()) * 0.5f + 0.5f
            val snoreW: Float = 14.dp.toPx() + 8.dp.toPx() * snorePulse
            val snoreH: Float = 10.dp.toPx() + 6.dp.toPx() * snorePulse
            drawOval(
                color = cyanLed.copy(alpha = ((0.75f + 0.25f * snorePulse) * effAlpha).coerceIn(0f, 1f)),
                topLeft = Offset(centerX - snoreW / 2f, mouthY - snoreH / 2f),
                size = Size(snoreW, snoreH),
                style = Stroke(width = 3.dp.toPx())
            )

            val sleepNodY2 = eyeCenterY + living.nodOffOffsetY * 1.dp.toPx()
            val zStartX = rightEyeCenterX + baseEyeW * 0.65f
            val zStartY = sleepNodY2 - baseEyeH * 0.35f
            val prog1 = particles.zzzRise1
            val zWobble1 = sin(prog1 * 4f * PI.toFloat()) * 8.dp.toPx()
            val zX1 = zStartX + (prog1 * 55.dp.toPx()) + zWobble1
            val zY1 = zStartY - (prog1 * 75.dp.toPx())
            val zAlpha1 = (sin(prog1 * PI.toFloat()).coerceIn(0.1f, 1f) * effAlpha).coerceIn(0f, 1f)
            drawZLetter(cyanLed.copy(alpha = zAlpha1), zX1, zY1, 16.dp.toPx() * (0.8f + prog1 * 0.3f))

            val prog2 = particles.zzzRise2
            val zWobble2 = cos(prog2 * 4f * PI.toFloat()) * 8.dp.toPx()
            val zX2 = zStartX + 18.dp.toPx() + (prog2 * 45.dp.toPx()) + zWobble2
            val zY2 = zStartY - 20.dp.toPx() - (prog2 * 70.dp.toPx())
            val zAlpha2 = (sin(prog2 * PI.toFloat()).coerceIn(0.1f, 1f) * effAlpha).coerceIn(0f, 1f)
            drawZLetter(cyanLed.copy(alpha = zAlpha2), zX2, zY2, 22.dp.toPx() * (0.9f + prog2 * 0.3f))

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.4f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 5. CONFUSED / CURIOUS (ตาซ้ายขีด — ตาขวาลิ่มเอียง + เครื่องหมายคำถาม ? สีเหลืองบนขวา) ──
        AvatarEmotion.CONFUSED -> {
            drawSleepingEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW, alpha = effAlpha)
            drawLooiCuriousRightEye(cyanLed, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha)

            // Animated wobbling single yellow question mark at top right (LOOI Reference Sheet 1 Cell 5)
            val qBob = sin(living.loopSlow * 2f * PI.toFloat()) * 3.dp.toPx()
            drawQuestionMark(Color(0xFFFFD54F), rightEyeCenterX + baseEyeW * 0.55f, eyeCenterY - baseEyeH * 0.72f + qBob, baseEyeH * 0.58f, alpha = effAlpha)

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.4f, 26.dp.toPx(), audioLevel)
            }
        }


        // ─── 6. EATING (ยกเบอร์เกอร์จากด้านล่างขึ้นมากัดที่แนวปาก + เคี้ยวตุ้ยๆ) ────
        AvatarEmotion.EATING -> {
            val p = living.chewCycle // 0..1 over 2000ms
            val baseBurgerY = eyeCenterY + baseEyeH * 0.68f // แนวระดับปาก
            val lowerDistance = 36.dp.toPx() // ระยะเลื่อนลงไปด้านล่างก่อนยกขึ้นมากัด

            // คำนวณแอนิเมชัน 4 จังหวะ:
            // 1. ยกขึ้นมาแนวปาก (0.00..0.28)
            // 2. กัด Chomp! (0.28..0.44)
            // 3. ผ่อนลง (0.44..0.60)
            // 4. เคี้ยวตุ้ยๆ (0.60..1.00)
            val (currentBurgerY, eyeSquint, eatingGazeY) = when {
                p < 0.28f -> {
                    // กำลังยกเบอร์เกอร์ขึ้นมาจากด้านล่างมาถึงแนวปาก
                    val t = p / 0.28f
                    val smooth = (1f - cos(t * PI.toFloat())) / 2f
                    val y = baseBurgerY + lowerDistance * (1f - smooth)
                    val gaze = 0.52f - 0.14f * smooth
                    val squint = 0.95f + 0.05f * smooth
                    Triple(y, squint, gaze)
                }
                p < 0.44f -> {
                    // จังหวะกัด! (Chomp!) กดเบอร์เกอร์เข้าปาก ตาหยีเคี้ยวอร่อย
                    val t = (p - 0.28f) / 0.16f
                    val biteSquash = sin(t * PI.toFloat()) * 4.dp.toPx()
                    val y = baseBurgerY - biteSquash * 0.5f
                    val squint = 1f - sin(t * PI.toFloat()) * 0.26f // ตาหยีลงเวลากัด
                    val gaze = 0.38f
                    Triple(y, squint, gaze)
                }
                p < 0.60f -> {
                    // ดึงเบอร์เกอร์ลงกลับไปตำแหน่งพัก
                    val t = (p - 0.44f) / 0.16f
                    val smooth = (1f - cos(t * PI.toFloat())) / 2f
                    val y = baseBurgerY + lowerDistance * smooth
                    val squint = 0.74f + 0.24f * smooth
                    val gaze = 0.38f + 0.10f * smooth
                    Triple(y, squint, gaze)
                }
                else -> {
                    // พักเบอร์เกอร์ด้านล่าง แล้วเคี้ยวแก้มตุ่ย ตาเด้งเป็นจังหวะ ˘◡˘
                    val t = (p - 0.60f) / 0.40f
                    val bob = sin(t * 4f * PI.toFloat()) * 2.dp.toPx()
                    val y = baseBurgerY + lowerDistance + bob
                    val chewBounce = sin(t * 4f * PI.toFloat()) * 0.08f
                    val squint = 0.94f + chewBounce
                    val gaze = 0.44f + sin(t * 4f * PI.toFloat()) * 0.04f
                    Triple(y, squint, gaze)
                }
            }

            drawDualCircleEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW * leftEyeScaleX, baseEyeH * eyeSquint, gazeX, eatingGazeY, alpha = effAlpha)
            drawDualCircleEye(cyanLed, rightEyeCenterX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH * eyeSquint, gazeX, eatingGazeY, alpha = effAlpha)

            val burgerW = baseEyeW * 0.58f
            drawLooiBurgerWithHands(centerX, currentBurgerY, burgerW, p, living.loopFast, alpha = effAlpha, detailLevel = detailLevel)

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 12.dp.toPx(), baseEyeW * 1.0f, 20.dp.toPx(), audioLevel)
            }
        }

        // ─── 7. DRINKING (เอียงหน้า + เอียงแก้วเบียร์ระดับปาก ยกขึ้นดื่มสดชื่น) ──
        AvatarEmotion.DRINKING -> {
            val p = living.gulpCycle // 0..1 over 2400ms
            val baseBeerY = eyeCenterY + baseEyeH * 0.84f
            val beerW = baseEyeW * 0.46f
            val beerH = beerW * 1.36f

            // คำนวณแอนิเมชัน 4 จังหวะ:
            // 1. ยกแก้วขึ้นเอียงดื่ม + เอียงหน้า (0.00..0.26)
            // 2. จังหวะยกดื่ม + กลืนอึกๆ (0.26..0.68)
            // 3. วางแก้วลง + คืนหน้าตรง (0.68..0.84)
            // 4. สดชื่น พักดื่ม (0.84..1.00)
            val headTiltDeg: Float
            val glassTiltDeg: Float
            val glassLiftY: Float
            val eyeSquint: Float
            val drinkGazeX: Float
            val drinkGazeY: Float

            when {
                p < 0.26f -> {
                    val t = p / 0.26f
                    val smooth = (1f - cos(t * PI.toFloat())) / 2f
                    headTiltDeg = -7.5f * smooth
                    glassTiltDeg = 24.0f * smooth
                    glassLiftY = -10.dp.toPx() * smooth
                    eyeSquint = 1.0f
                    drinkGazeX = 0.18f + 0.08f * smooth
                    drinkGazeY = 0.42f + 0.08f * smooth
                }
                p < 0.68f -> {
                    val t = (p - 0.26f) / 0.42f
                    val wobble = sin(t * 6f * PI.toFloat()) * 1.5f
                    headTiltDeg = -7.5f - wobble * 0.4f
                    glassTiltDeg = 24.0f + wobble
                    glassLiftY = -10.dp.toPx()

                    // Swallow pulse (กลืนอึกๆ 2 ครั้ง)
                    val gulpProg = (t * 2.2f) % 1f
                    val swallowPulse = sin(gulpProg * PI.toFloat()).coerceAtLeast(0f) * 0.28f
                    eyeSquint = 1f - swallowPulse
                    drinkGazeX = 0.24f
                    drinkGazeY = 0.48f
                }
                p < 0.84f -> {
                    val t = (p - 0.68f) / 0.16f
                    val smooth = (1f - cos(t * PI.toFloat())) / 2f
                    headTiltDeg = -7.5f * (1f - smooth)
                    glassTiltDeg = 24.0f * (1f - smooth)
                    glassLiftY = -10.dp.toPx() * (1f - smooth)
                    eyeSquint = 0.82f + 0.18f * smooth
                    drinkGazeX = 0.24f * (1f - smooth) + 0.18f * smooth
                    drinkGazeY = 0.48f * (1f - smooth) + 0.42f * smooth
                }
                else -> {
                    headTiltDeg = 0f
                    glassTiltDeg = 0f
                    glassLiftY = 0f
                    eyeSquint = 1.0f
                    drinkGazeX = 0.18f
                    drinkGazeY = 0.42f
                }
            }

            // 1. วาดตาที่เอียงตามองศาหน้า (เหมือนเอียงหน้ายกดื่ม)
            rotate(degrees = headTiltDeg, pivot = Offset(centerX, eyeCenterY)) {
                drawDualCircleEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW * leftEyeScaleX, baseEyeH * eyeSquint, drinkGazeX, drinkGazeY, alpha = effAlpha)
                drawDualCircleEye(cyanLed, rightEyeCenterX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH * eyeSquint, -drinkGazeX, drinkGazeY, alpha = effAlpha)
            }

            // 2. วาดแก้วเบียร์ที่เอียงระดับปาก (Pivot อยู่ที่ขอบปากแก้วด้านบนซ้าย)
            val currentBeerY = baseBeerY + glassLiftY
            val glassTopY = currentBeerY - beerH * 0.45f
            val pivotX = centerX - beerW * 0.15f
            val pivotY = glassTopY + 2.dp.toPx()

            rotate(degrees = glassTiltDeg, pivot = Offset(pivotX, pivotY)) {
                drawLooiBeerSteinWithDrinking(centerX, currentBeerY, beerW, p, living.loopFast, alpha = effAlpha, detailLevel = detailLevel)
            }

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 14.dp.toPx(), baseEyeW * 1.0f, 20.dp.toPx(), audioLevel)
            }
        }

        // ─── 8. WINK (ตาซ้ายตอบรับ + ตาขวาขยิบตา 2 ครั้งติด + ประกายดาว ✦ Pop) ───────────
        AvatarEmotion.WINK -> {
            val winkScaleY = living.winkEyeScaleY
            val isWinkClosed = winkScaleY <= 0.18f
            val leftEyeReaction = if (isWinkClosed) 1.04f else 1.0f

            // ตาซ้ายเปิดกลมโต พร้อมขยับรับเล็กน้อยเวลากระพริบตาขวา
            drawDualCircleEye(
                color = cyanLed,
                centerX = leftEyeCenterX,
                centerY = eyeCenterY,
                width = baseEyeW * leftEyeScaleX * leftEyeReaction,
                height = baseEyeH * leftEyeReaction,
                gazeX = gazeX,
                gazeY = gazeY,
                alpha = effAlpha
            )

            // ตาขวาขยิบตา 2 ที: หรี่ปิดเป็นเส้นหลับตา (Sleeping bar) แล้วเบิกกว้างสลับกัน 2 จังหวะ
            if (isWinkClosed) {
                drawSleepingEye(
                    color = cyanLed,
                    centerX = rightEyeCenterX,
                    centerY = eyeCenterY,
                    width = baseEyeW * 1.04f,
                    barHeight = 14.dp.toPx(),
                    alpha = effAlpha
                )
            } else {
                val squashX = 1f + (1f - winkScaleY) * 0.14f
                val rightH = baseEyeH * winkScaleY.coerceAtLeast(0.12f)
                drawDualCircleEye(
                    color = cyanLed,
                    centerX = rightEyeCenterX,
                    centerY = eyeCenterY,
                    width = baseEyeW * rightEyeScaleX,
                    height = rightH,
                    gazeX = gazeX,
                    gazeY = gazeY,
                    squashX = squashX,
                    alpha = effAlpha
                )
            }

            // ประกายดาวสีทอง ✦ เด้ง Pop และหมุนเปล่งประกายช่วงที่ตาขวากระพริบขยิบตา (ตรงตาม Reference Cell 8)
            val sparkleScale = living.winkSparkleScale
            if (sparkleScale > 0.02f) {
                // ประกายดาวหลัก (Main 4-point Star) ที่มุมขวาบนของตาที่ขยิบ
                drawNeonSparkle(
                    color = Color(0xFFFFD700),
                    centerX = rightEyeCenterX + baseEyeW * 0.42f,
                    centerY = eyeCenterY - baseEyeH * 0.38f,
                    size = 30.dp.toPx() * sparkleScale,
                    rotationDeg = living.sparkleRot * 1.8f,
                    alpha = effAlpha * sparkleScale.coerceIn(0f, 1f)
                )

                // ประกายดาวดวงเล็กเสริม (Secondary Mini Star)
                drawNeonSparkle(
                    color = Color(0xFFFFEA00),
                    centerX = rightEyeCenterX + baseEyeW * 0.52f,
                    centerY = eyeCenterY - baseEyeH * 0.18f,
                    size = 14.dp.toPx() * sparkleScale,
                    rotationDeg = -living.sparkleRot * 2.2f,
                    alpha = effAlpha * sparkleScale.coerceIn(0f, 1f) * 0.85f
                )
            }

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.4f, 26.dp.toPx(), audioLevel)
            }
        }

        // ─── 9. DEAD (ตาลายกากบาท X X สีฟ้าไซแอน 2D Depth) ────────────────────
        AvatarEmotion.DEAD -> {
            drawCrossEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW * 0.90f, alpha = effAlpha)
            drawCrossEye(cyanLed, rightEyeCenterX, eyeCenterY, baseEyeW * 0.90f, alpha = effAlpha)

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.4f, 26.dp.toPx(), audioLevel)
            }
        }

        // ─── 10. LAUGHING (ตาหยีหัวเราะ > < + ตัวโยกตามจังหวะขำ) ────────────
        AvatarEmotion.LAUGHING -> {
            val laughY = eyeCenterY + living.laughBounceY * 1.dp.toPx()
            val laughSquash = living.laughSquint
            drawExcitedEye(cyanLed, leftEyeCenterX, laughY, baseEyeW * laughSquash, baseEyeH * (2f - laughSquash), true, alpha = effAlpha)
            drawExcitedEye(cyanLed, rightEyeCenterX, laughY, baseEyeW * laughSquash, baseEyeH * (2f - laughSquash), false, alpha = effAlpha)

            // Open laughing smile mouth
            drawSmileArc(cyanLed, centerX, mouthY + living.laughBounceY * 0.6f * 1.dp.toPx(), baseEyeW * 1.0f, 20.dp.toPx(), alpha = effAlpha)

            // Sparkle pops of joy
            val sparklePop = sin(living.loopFast * 2f * PI.toFloat()).coerceAtLeast(0f)
            if (sparklePop > 0.3f) {
                drawNeonSparkle(
                    Color(0xFFFFD54F),
                    rightEyeCenterX + baseEyeW * 0.65f,
                    laughY - baseEyeH * 0.45f,
                    18.dp.toPx() * sparklePop,
                    living.sparkleRot,
                    alpha = effAlpha
                )
            }

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + living.laughBounceY * 0.6f * 1.dp.toPx(), baseEyeW * 1.5f, 28.dp.toPx(), audioLevel)
            }
        }

        // ─── 11. MUSIC (ฟังเพลง: ตากลม Squircle + หูฟังครอบศีรษะ 🎧 + ตัวโน้ต 3-4 ตัวลอย) ───
        AvatarEmotion.MUSIC -> {
            val headSpan = (rightEyeCenterX - leftEyeCenterX) + baseEyeW * 1.05f
            val headH = baseEyeH * 1.05f
            val beatBob = sin(living.loopFast * 2f * PI.toFloat()) * 3.dp.toPx()

            drawDualCircleEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW * leftEyeScaleX, baseEyeH, gazeX, gazeY, alpha = effAlpha)
            drawDualCircleEye(cyanLed, rightEyeCenterX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH, gazeX, gazeY, alpha = effAlpha)

            drawLooiHeadphones(centerX, eyeCenterY + beatBob, headSpan, headH, alpha = effAlpha)

            // 4 Animated floating musical notes (♪, ♫, ♩) bobbing around head
            val noteColor = Color(0xFF80D8FF).copy(alpha = effAlpha)
            val noteOffsets = listOf(
                Triple(-baseEyeW * 0.85f, -baseEyeH * 0.70f, 0.0f),
                Triple(baseEyeW * 0.85f, -baseEyeH * 0.75f, 0.25f),
                Triple(-baseEyeW * 1.05f, 6.dp.toPx(), 0.55f),
                Triple(baseEyeW * 1.05f, 8.dp.toPx(), 0.80f)
            )
            for (idx in noteOffsets.indices) {
                val (nx, ny, phase) = noteOffsets[idx]
                val bob = sin((living.loopMedium + phase) * 2f * PI.toFloat()) * 6.dp.toPx()
                val isDouble = (idx % 2 == 1)
                drawNeonMusicNote(
                    color = noteColor,
                    centerX = centerX + nx,
                    centerY = eyeCenterY + ny + bob,
                    size = 18.dp.toPx(),
                    isDoubleNote = isDouble,
                    alpha = effAlpha
                )
            }

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.5f, 28.dp.toPx(), audioLevel)
            }
        }


        // ─── 12. VR_MODE (แว่น VR Vision Pro + ถังป๊อปคอร์นโรงหนัง + ลูกตาจางๆ ในแว่น) ───
        AvatarEmotion.VR_MODE -> {
            val visorSpan = (rightEyeCenterX - leftEyeCenterX) + baseEyeW * 1.25f
            val visorH = baseEyeH * 1.15f
            val vrBob = cos(living.loopSlow * 2f * PI.toFloat()) * 2.dp.toPx()
            val vrCenterY = eyeCenterY + vrBob

            // Futuristic VR Headset
            drawLooiVrHeadset(centerX, vrCenterY, visorSpan, visorH, alpha = effAlpha)

            // Faint translucent cyan eyes visible inside the dark VR visor (ลูกตาจางๆ อยู่ในแว่น)
            val vrEyeW = baseEyeW * 0.78f
            val vrEyeH = baseEyeH * 0.78f
            val vrEyeAlpha = 0.42f * effAlpha
            drawDualCircleEye(
                color = cyanLed,
                centerX = leftEyeCenterX,
                centerY = vrCenterY,
                width = vrEyeW * leftEyeScaleX,
                height = vrEyeH,
                gazeX = gazeX,
                gazeY = gazeY,
                alpha = vrEyeAlpha
            )
            drawDualCircleEye(
                color = cyanLed,
                centerX = rightEyeCenterX,
                centerY = vrCenterY,
                width = vrEyeW * rightEyeScaleX,
                height = vrEyeH,
                gazeX = gazeX,
                gazeY = gazeY,
                alpha = vrEyeAlpha
            )

            // Compact popcorn bucket at bottom right (Sheet 1 Cell 12)
            val popW = baseEyeW * 0.46f
            val popX = rightEyeCenterX + baseEyeW * 0.50f
            val popY = eyeCenterY + baseEyeH * 0.50f
            drawLooiPopcornBucket(
                centerX = popX,
                centerY = popY,
                mouthX = centerX,
                mouthY = mouthY,
                width = popW,
                popPhase = living.popPhase,
                chewCycle = living.chewCycle,
                alpha = effAlpha
            )

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.4f, 26.dp.toPx(), audioLevel)
            }
        }

        // ─── 13. DIVING (ดำน้ำ: แว่นหน้ากาก Snorkel Mask 🤿 + ท่อหายใจ + ฟองอากาศ) ─
        AvatarEmotion.DIVING -> {
            val dynamicSpacing = (rightEyeCenterX - leftEyeCenterX) / 2f
            val diveBob = sin(living.loopSlow * 2f * PI.toFloat()) * 3.dp.toPx()
            drawLooiSnorkelMask(centerX, eyeCenterY + diveBob, dynamicSpacing, baseEyeW, baseEyeH, alpha = effAlpha)

            drawDualCircleEye(cyanLed, leftEyeCenterX, eyeCenterY + diveBob, baseEyeW * leftEyeScaleX, baseEyeH, gazeX, gazeY, alpha = effAlpha)
            drawDualCircleEye(cyanLed, rightEyeCenterX, eyeCenterY + diveBob, baseEyeW * rightEyeScaleX, baseEyeH, gazeX, gazeY, alpha = effAlpha)

            // Dynamic rising bubbles
            for (i in 0 until 3) {
                val bProg = (living.loopFast + i * 0.33f) % 1f
                val bY = (eyeCenterY + baseEyeH * 0.6f) - bProg * (baseEyeH * 1.2f)
                val bX = centerX + (if (i % 2 == 0) baseEyeW * 0.9f else -baseEyeW * 0.85f)
                drawCircle(Color(0xFF80DEEA).copy(alpha = (1f - bProg) * 0.6f * effAlpha), radius = 3.dp.toPx(), center = Offset(bX, bY), style = Stroke(width = 1.dp.toPx()))
            }

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.4f, 26.dp.toPx(), audioLevel)
            }
        }

        // ─── 14. EVIL (ตัวร้าย: ตาเฉียงแดงเพลิง + เขี้ยวขาว + ไอคอนปิศาจม่วง 😈) ────
        AvatarEmotion.EVIL -> {
            val leftSlant = eyeSlantProgress * (1f + living.angerBrowSlant / 28f).coerceIn(0.85f, 1.25f)
            val rightSlant = eyeSlantProgress * (1f - living.angerBrowSlant / 35f).coerceIn(0.70f, 1.15f)
            drawAngryEye(redLed, leftEyeCenterX, eyeCenterY, baseEyeW * leftEyeScaleX, baseEyeH, true, slantProgress = leftSlant, alpha = effAlpha)
            drawAngryEye(redLed, rightEyeCenterX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH, false, slantProgress = rightSlant, alpha = effAlpha)

            drawLooiFangs(centerX, eyeCenterY + baseEyeH * 0.55f, 11.dp.toPx(), progress = fangsProgress, alpha = effAlpha)
            val devilBob = sin(living.loopMedium * 2f * PI.toFloat()) * 3.dp.toPx()
            val devilPulse = 1f + living.pulse * 0.08f
            drawLooiPurpleDevilIcon(centerX, eyeCenterY - baseEyeH * 0.90f + devilBob, 26.dp.toPx() * devilPulse, alpha = effAlpha)

            if (isSpeaking) {
                drawWaveformMouth(redLed, centerX, mouthY, baseEyeW * 1.5f, 28.dp.toPx(), audioLevel)
            }
        }

        // ─── 15. FOCUSED (มีสมาธิ/สแกน: ตารางแสกน กวาดขึ้นลง เหมือนกำลังแสกน) ──
        AvatarEmotion.FOCUSED -> {
            val gridSpan = (rightEyeCenterX - leftEyeCenterX) + baseEyeW * 2.2f
            drawLooiFocusdWedge(cyanLed, Color(0xFF004D6B), leftEyeCenterX, eyeCenterY, baseEyeW * leftEyeScaleX, baseEyeH, true, alpha = effAlpha)
            drawLooiFocusdWedge(cyanLed, Color(0xFF004D6B), rightEyeCenterX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH, false, alpha = effAlpha)

            // Dynamic up-and-down laser scanning beam & grid sweep (ตารางแสกน กวาดขึ้นลง เหมือนกำลังแสกน)
            val scanProg = sin(living.loopMedium * 2f * PI.toFloat()) * 0.5f + 0.5f
            val gridScanOffsetY = (scanProg - 0.5f) * 14.dp.toPx()
            val gridPulse = 1f + living.pulse * 0.05f
            drawLooiSynthwaveGrid(
                centerX = centerX,
                horizonY = eyeCenterY + baseEyeH * 0.55f + gridScanOffsetY,
                width = gridSpan * gridPulse,
                depth = 75.dp.toPx(),
                alpha = effAlpha,
                scanProgress = scanProg
            )

            // Intense horizontal neon laser beam sweeping up and down across eyes & face
            val scanBeamY = (eyeCenterY - baseEyeH * 0.65f) + scanProg * (baseEyeH * 1.45f)
            val beamW = gridSpan * 0.95f
            // Soft laser glow halo
            drawLine(
                color = cyanLed.copy(alpha = 0.35f * effAlpha),
                start = Offset(centerX - beamW / 2f, scanBeamY),
                end = Offset(centerX + beamW / 2f, scanBeamY),
                strokeWidth = 7.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Crisp core laser beam
            drawLine(
                color = Color.White.copy(alpha = 0.90f * effAlpha),
                start = Offset(centerX - beamW / 2f, scanBeamY),
                end = Offset(centerX + beamW / 2f, scanBeamY),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round
            )

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.4f, 26.dp.toPx(), audioLevel)
            }
        }

        // ─── 16. EXCITED (ตื่นเต้น: ตาครึ่งวงรี สีเหลือง เริ่มจากตาวงกลมสีเหลือง แล้วหดเป็นครึ่งวงกลม) ──────────
        AvatarEmotion.EXCITED -> {
            val yellowLed = Color(0xFFFFD700)
            val yellowShadow = Color(0xFF664400)

            // Cycle: Start from full yellow circle -> smoothly shrink/squash into half-circle -> hold -> spring back
            val excT = (living.loopSlow * 1.35f) % 1f
            val shrinkProgress = when {
                excT < 0.22f -> 0f // Full circle
                excT < 0.48f -> {
                    val sub = (excT - 0.22f) / 0.26f
                    sin(sub * (PI.toFloat() / 2f)) // Smoothly shrink to half-circle
                }
                excT < 0.80f -> 1f // Hold as half-circle
                else -> {
                    val sub = (excT - 0.80f) / 0.20f
                    1f - sin(sub * (PI.toFloat() / 2f)) // Spring back to full circle
                }
            }

            drawLooiExcitedEye(
                color = yellowLed,
                shadowColor = yellowShadow,
                centerX = leftEyeCenterX,
                centerY = eyeCenterY,
                width = baseEyeW * leftEyeScaleX,
                height = baseEyeH,
                isLeft = true,
                shrinkProgress = shrinkProgress,
                alpha = effAlpha
            )
            drawLooiExcitedEye(
                color = yellowLed,
                shadowColor = yellowShadow,
                centerX = rightEyeCenterX,
                centerY = eyeCenterY,
                width = baseEyeW * rightEyeScaleX,
                height = baseEyeH,
                isLeft = false,
                shrinkProgress = shrinkProgress,
                alpha = effAlpha
            )

            // Sparkle stars pop with extra brightness when eyes are squashed into half-circles
            val starScale = 0.85f + 0.35f * shrinkProgress
            rotate(living.sparkleRot, pivot = Offset(leftEyeCenterX - baseEyeW * 0.65f, eyeCenterY - baseEyeH * 0.75f)) {
                drawLooiSparkleStar(yellowLed, leftEyeCenterX - baseEyeW * 0.65f, eyeCenterY - baseEyeH * 0.75f, 24.dp.toPx() * starScale, alpha = effAlpha)
            }
            rotate(-living.sparkleRot, pivot = Offset(rightEyeCenterX + baseEyeW * 0.65f, eyeCenterY - baseEyeH * 0.75f)) {
                drawLooiSparkleStar(yellowLed, rightEyeCenterX + baseEyeW * 0.65f, eyeCenterY - baseEyeH * 0.75f, 24.dp.toPx() * starScale, alpha = effAlpha)
            }

            if (isSpeaking) {
                drawWaveformMouth(yellowLed, centerX, mouthY, baseEyeW * 1.5f, 28.dp.toPx(), audioLevel)
            }
        }

        // ─── 17. SHY (เขินอาย: ตากลม Squircle + รอยแก้มชมพูระเรื่อ /// บนแก้ม) ─────────
        AvatarEmotion.SHY -> {
            val shyGazeX = sin(living.loopSlow * PI.toFloat()) * 0.22f
            val shyGazeY = 0.35f + cos(living.loopSlow * PI.toFloat()) * 0.10f
            drawDualCircleEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW * leftEyeScaleX, baseEyeH, shyGazeX, shyGazeY, alpha = effAlpha)
            drawDualCircleEye(cyanLed, rightEyeCenterX, eyeCenterY, baseEyeW * rightEyeScaleX, baseEyeH, shyGazeX, shyGazeY, alpha = effAlpha)

            // Blush slashes moved down onto cheeks
            val blushPulse = 1f + sin(living.loopMedium * 2f * PI.toFloat()) * 0.15f
            val cheekY = eyeCenterY + baseEyeH * 0.82f
            drawLooiPinkBlush(leftEyeCenterX - baseEyeW * 0.10f, cheekY, 32.dp.toPx() * blushPulse, alpha = effAlpha)
            drawLooiPinkBlush(rightEyeCenterX + baseEyeW * 0.10f, cheekY, 32.dp.toPx() * blushPulse, alpha = effAlpha)

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.4f, 26.dp.toPx(), audioLevel)
            }
        }

        // ─── 18. SURPRISED / SHOCK (ตกใจ: ตาเบิกโต + เครื่องหมายตกใจใหญ่เด้งย่อขยาย !) ──
        AvatarEmotion.SURPRISED -> {
            val shockJitterX = sin(living.loopFast * 30f * PI.toFloat()) * 1.5.dp.toPx()
            val shockJitterY = cos(living.loopFast * 36f * PI.toFloat()) * 1.0.dp.toPx()
            val surpriseW = baseEyeW * (1f + 0.25f * surpriseProgress)
            val surpriseH = baseEyeH * (1f + 0.25f * surpriseProgress)
            drawDualCircleEye(cyanLed, leftEyeCenterX + shockJitterX, eyeCenterY + shockJitterY, surpriseW * leftEyeScaleX, surpriseH, gazeX, gazeY, alpha = effAlpha)
            drawDualCircleEye(cyanLed, rightEyeCenterX + shockJitterX, eyeCenterY + shockJitterY, surpriseW * rightEyeScaleX, surpriseH, gazeX, gazeY, alpha = effAlpha)

            // Large bouncing and scaling exclamation mark !
            val exclBounceScale = 1.35f + sin(living.loopFast * 6f * PI.toFloat()) * 0.25f
            val exclSize = 48.dp.toPx() * exclBounceScale
            val exclWobble = sin(living.loopFast * 12f * PI.toFloat()) * 4f
            rotate(exclWobble, pivot = Offset(rightEyeCenterX + baseEyeW * 0.80f, eyeCenterY - baseEyeH * 0.85f)) {
                drawLooiExclamationMark(rightEyeCenterX + baseEyeW * 0.80f, eyeCenterY - baseEyeH * 0.85f, exclSize, alpha = effAlpha)
            }

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.5f, 28.dp.toPx(), audioLevel)
            }
        }

        // ─── 19. DISGUSTED (รังเกียจ/เหม็น: ตาทั้ง 2 ข้าง ค่อยๆ เลื่อนเข้าหา + ถังขยะ 🗑️) ───
        AvatarEmotion.DISGUSTED -> {
            // Smoothly and gradually slide both eyes inward towards center (ตาทั้ง 2 ข้าง ค่อยๆเลื่อนเข้าหาหา)
            val cringeCycle = sin(living.loopMedium * 2f * PI.toFloat()) * 0.5f + 0.5f
            val inwardSquintX = 2.dp.toPx() + 14.dp.toPx() * cringeCycle
            val cringeJitterY = sin(living.loopFast * 24f * PI.toFloat()) * 1.2.dp.toPx()

            drawExcitedEye(cyanLed, leftEyeCenterX + inwardSquintX, eyeCenterY + cringeJitterY, baseEyeW * 0.92f, baseEyeH * 0.85f, true, alpha = effAlpha)
            drawExcitedEye(cyanLed, rightEyeCenterX - inwardSquintX, eyeCenterY + cringeJitterY, baseEyeW * 0.92f, baseEyeH * 0.85f, false, alpha = effAlpha)

            drawLooiTrashBin(centerX, eyeCenterY + baseEyeH * 0.70f, 36.dp.toPx(), alpha = effAlpha)
            drawLooiTrashIcon(rightEyeCenterX + baseEyeW * 0.75f, eyeCenterY - baseEyeH * 0.55f, 24.dp.toPx(), alpha = effAlpha)

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.4f, 26.dp.toPx(), audioLevel)
            }
        }

        // ─── 20. CAMERA_MODE (โหมดกล้อง: ตาเลนส์หมุนแฟลช + ตาเส้นเล็ง Viewfinder + ไอคอน 📷) ───
        AvatarEmotion.CAMERA_MODE -> {
            // Left eye: Camera aperture lens with rotating iris blades and flash ring
            drawCameraLensEye(cyanLed, leftEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, living.loopSlow * 360f, alpha = effAlpha)

            // Right eye: Camera viewfinder reticle [  ]
            drawCameraViewfinderEye(cyanLed, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha)

            // DSLR Camera icon on top-right
            drawLooiCameraIcon(centerX + baseEyeW * 0.75f, eyeCenterY - baseEyeH * 0.65f, 32.dp.toPx(), alpha = effAlpha)

            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.4f, 26.dp.toPx(), audioLevel)
            }
        }

        // ─── 21. LISTENING / SPEAKING (ยูทิลิตีระบบ: พร้อมรับคำสั่ง / กำลังพูด) ───
        AvatarEmotion.LISTENING, AvatarEmotion.SPEAKING -> {
            if (!isParametric) {
                val leftH = baseEyeH * blinkFactor.coerceAtLeast(0.08f)
                val rightH = baseEyeH * blinkFactor.coerceAtLeast(0.08f)
                val leftW = baseEyeW * leftEyeScaleX
                val rightW = baseEyeW * rightEyeScaleX

                drawDualCircleEye(cyanLed, leftEyeCenterX, eyeCenterY, leftW, leftH, gazeX, gazeY, alpha = effAlpha)
                drawDualCircleEye(cyanLed, rightEyeCenterX, eyeCenterY, rightW, rightH, gazeX, gazeY, alpha = effAlpha)
            }

            if (isSpeaking || emotion == AvatarEmotion.SPEAKING) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.5f, 28.dp.toPx(), audioLevel)
            }
        }

        // ─── 22. SAD (เศร้า: ดวงตาหรี่ลู่ + น้ำตาหยด) ──────────────────────────
        AvatarEmotion.SAD -> {
            val droopY = 7.dp.toPx() * sadProgress
            val tiltAngle = 8f * sadProgress
            val eyeH = baseEyeH * (1f - 0.15f * sadProgress) * blinkFactor.coerceAtLeast(0.15f)
            val eyeW = baseEyeW * 0.90f

            rotate(degrees = -tiltAngle, pivot = Offset(leftEyeCenterX, eyeCenterY + droopY)) {
                drawDualCircleEye(
                    color = Color(0xFF80D8FF),
                    centerX = leftEyeCenterX,
                    centerY = eyeCenterY + droopY,
                    width = eyeW * leftEyeScaleX,
                    height = eyeH,
                    gazeX = gazeX,
                    gazeY = gazeY,
                    backColorOverride = Color(0xFF003866),
                    alpha = effAlpha
                )
            }
            rotate(degrees = tiltAngle, pivot = Offset(rightEyeCenterX, eyeCenterY + droopY)) {
                drawDualCircleEye(
                    color = Color(0xFF80D8FF),
                    centerX = rightEyeCenterX,
                    centerY = eyeCenterY + droopY,
                    width = eyeW * rightEyeScaleX,
                    height = eyeH,
                    gazeX = gazeX,
                    gazeY = gazeY,
                    backColorOverride = Color(0xFF003866),
                    alpha = effAlpha
                )
            }

            val tearLeftY = eyeCenterY + droopY + baseEyeH * 0.45f + (particles.tearFallLeft * 70.dp.toPx())
            val tearLeftAlpha = ((1f - particles.tearFallLeft * 0.75f) * sadProgress * effAlpha).coerceIn(0f, 1f)
            if (tearLeftAlpha > 0.05f) {
                drawNeonTeardrop(
                    color = Color(0xFF00E5FF).copy(alpha = tearLeftAlpha),
                    centerX = leftEyeCenterX - eyeW * 0.40f,
                    centerY = tearLeftY,
                    size = 14.dp.toPx() * sadProgress,
                    alpha = tearLeftAlpha
                )
            }

            val tearRightY = eyeCenterY + droopY + baseEyeH * 0.45f + (particles.tearFallRight * 75.dp.toPx())
            val tearRightAlpha = ((1f - particles.tearFallRight * 0.75f) * sadProgress * effAlpha).coerceIn(0f, 1f)
            if (tearRightAlpha > 0.05f) {
                drawNeonTeardrop(
                    color = Color(0xFF00E5FF).copy(alpha = tearRightAlpha),
                    centerX = rightEyeCenterX + eyeW * 0.45f,
                    centerY = tearRightY,
                    size = 15.dp.toPx() * sadProgress,
                    alpha = tearRightAlpha
                )
            }

            if (sadProgress > 0.05f) {
                drawFrownArc(
                    color = Color(0xFF80D8FF).copy(alpha = (sadProgress * effAlpha).coerceIn(0f, 1f)),
                    centerX = centerX,
                    centerY = mouthY + droopY * 0.5f,
                    width = baseEyeW * 1.1f,
                    height = 22.dp.toPx() * sadProgress
                )
            }
        }

        // ─── 23. LOVE (ความรัก: ตามีหัวใจสีชมพู เต้นตึกตักเป็นจังหวะ) ────────
        AvatarEmotion.LOVE -> {
            val heartSize = baseEyeW * 1.35f * living.heartbeatScale
            drawHeart(pinkLed, leftEyeCenterX, eyeCenterY, heartSize, alpha = effAlpha)
            drawHeart(pinkLed, rightEyeCenterX, eyeCenterY, heartSize, alpha = effAlpha)

            drawSmileArc(pinkLed, centerX, mouthY, baseEyeW * 1.1f, 24.dp.toPx(), alpha = effAlpha)

            // Independent Left & Right Floating mini hearts
            val miniHeartsLeft = listOf(
                Pair(-baseEyeW * 0.72f, -baseEyeH * 0.45f),
                Pair(-baseEyeW * 0.88f, baseEyeH * 0.35f)
            )
            val miniHeartsRight = listOf(
                Pair(baseEyeW * 0.72f, -baseEyeH * 0.52f),
                Pair(baseEyeW * 0.88f, baseEyeH * 0.25f)
            )
            for (offset in miniHeartsLeft) {
                val bob = sin(particles.heartFloatLeft * 2f * PI.toFloat()) * 4.dp.toPx()
                val hx = leftEyeCenterX + offset.first
                val hy = eyeCenterY + offset.second + bob
                val hSize = 13.dp.toPx() * particles.heartPulseLeft
                drawNeonHeart(pinkLed.copy(alpha = 0.85f * effAlpha), hx, hy, hSize, alpha = effAlpha)
            }
            for (offset in miniHeartsRight) {
                val bob = sin(particles.heartFloatRight * 2f * PI.toFloat()) * 4.dp.toPx()
                val hx = rightEyeCenterX + offset.first
                val hy = eyeCenterY + offset.second + bob
                val hSize = 13.dp.toPx() * particles.heartPulseRight
                drawNeonHeart(pinkLed.copy(alpha = 0.85f * effAlpha), hx, hy, hSize, alpha = effAlpha)
            }
        }

        // ─── 24. THINKING (กำลังคิด: ขยับตา + ไอคอนมือแตะคาง + ฟองความคิด ... 💭) ────
        AvatarEmotion.THINKING -> {
            drawDualCircleEye(eyeColor, leftEyeCenterX, eyeCenterY, baseEyeW * 0.8f * leftEyeScaleX, baseEyeH * 0.65f, gazeX, gazeY, alpha = effAlpha)
            drawDualCircleEye(eyeColor, rightEyeCenterX, eyeCenterY, baseEyeW * 1.05f * rightEyeScaleX, baseEyeH, gazeX, gazeY, alpha = effAlpha)

            val handX = centerX + baseEyeW * 0.15f
            drawThinkingHand(eyeColor, handX, mouthY + 10.dp.toPx(), 45.dp.toPx(), alpha = effAlpha)

            // 3 cyan dots (...) thought symbol on top right (LOOI Reference Sheet 2 Cell 26)
            for (i in 0..2) {
                val dotX = rightEyeCenterX + baseEyeW * 0.55f + i * 11.dp.toPx()
                val dotY = eyeCenterY - baseEyeH * 0.78f - (if (i == 1) 3.dp.toPx() else 0f) + sin((living.loopMedium + i * 0.2f) * 2f * PI.toFloat()) * 2.5.dp.toPx()
                val dotAlpha = (0.7f + 0.3f * sin((living.loopFast + i * 0.3f) * 2f * PI.toFloat())) * effAlpha
                drawCircle(cyanLed.copy(alpha = dotAlpha), radius = 4.dp.toPx(), center = Offset(dotX, dotY))
                drawCircle(Color.White.copy(alpha = 0.8f * dotAlpha), radius = 1.5.dp.toPx(), center = Offset(dotX - 1.dp.toPx(), dotY - 1.dp.toPx()))
            }

            val mouthW = baseEyeW * 0.8f
            drawLine(
                color = eyeColor.copy(alpha = effAlpha),
                start = Offset(centerX - mouthW / 2f, mouthY),
                end = Offset(centerX + mouthW / 2f, mouthY),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // ─── 25. POUT (แก้มป่อง ปากจู๋) ───────────────────────────────────────
        AvatarEmotion.POUT -> {
            drawDualCircleEye(eyeColor, leftEyeCenterX, eyeCenterY, baseEyeW * 0.9f, baseEyeH * 0.9f, gazeX, gazeY, alpha = effAlpha)
            drawDualCircleEye(eyeColor, rightEyeCenterX, eyeCenterY, baseEyeW * 0.9f, baseEyeH * 0.9f, gazeX, gazeY, alpha = effAlpha)

            val cheekRadius = 22.dp.toPx()
            val cheekY = eyeCenterY + baseEyeH * 0.45f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(pinkLed.copy(alpha = 0.75f * effAlpha), Color.Transparent),
                    center = Offset(leftEyeCenterX - baseEyeW * 0.55f, cheekY),
                    radius = cheekRadius
                ),
                radius = cheekRadius,
                center = Offset(leftEyeCenterX - baseEyeW * 0.55f, cheekY)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(pinkLed.copy(alpha = 0.75f * effAlpha), Color.Transparent),
                    center = Offset(rightEyeCenterX + baseEyeW * 0.55f, cheekY),
                    radius = cheekRadius
                ),
                radius = cheekRadius,
                center = Offset(rightEyeCenterX + baseEyeW * 0.55f, cheekY)
            )

            drawPuckerMouth(eyeColor, centerX, mouthY, 11.dp.toPx(), alpha = effAlpha)
        }

        // ─── 26. DIZZY (มึนหัว ตาหมุน X X + ปากคลื่น) ─────────────────────────
        AvatarEmotion.DIZZY -> {
            drawCrossEye(eyeColor, leftEyeCenterX, eyeCenterY, baseEyeW * 0.85f, alpha = effAlpha)
            drawCrossEye(eyeColor, rightEyeCenterX, eyeCenterY, baseEyeW * 0.85f, alpha = effAlpha)

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
            drawPath(wavePath, eyeColor.copy(alpha = effAlpha), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
        }

        // ─── 27. BORED (เบื่อ หนังตาตก) ────────────────────────────────────────
        AvatarEmotion.BORED -> {
            val boredW = baseEyeW * 1.05f
            val boredH = baseEyeH * 0.42f
            val boredYShift = 6.dp.toPx()

            drawDualCircleEye(eyeColor, leftEyeCenterX, eyeCenterY + boredYShift, boredW * leftEyeScaleX, boredH, gazeX, gazeY, alpha = effAlpha)
            drawDualCircleEye(eyeColor, rightEyeCenterX, eyeCenterY + boredYShift, boredW * rightEyeScaleX, boredH, gazeX, gazeY, alpha = effAlpha)

            val lidStroke = 4.dp.toPx()
            drawLine(
                color = eyeColor.copy(alpha = 0.5f * effAlpha),
                start = Offset(leftEyeCenterX - boredW * 0.55f, eyeCenterY + boredYShift - boredH * 0.5f),
                end = Offset(leftEyeCenterX + boredW * 0.55f, eyeCenterY + boredYShift - boredH * 0.5f),
                strokeWidth = lidStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = eyeColor.copy(alpha = 0.5f * effAlpha),
                start = Offset(rightEyeCenterX - boredW * 0.55f, eyeCenterY + boredYShift - boredH * 0.5f),
                end = Offset(rightEyeCenterX + boredW * 0.55f, eyeCenterY + boredYShift - boredH * 0.5f),
                strokeWidth = lidStroke,
                cap = StrokeCap.Round
            )

            drawLine(
                color = eyeColor.copy(alpha = effAlpha),
                start = Offset(centerX - baseEyeW * 0.4f, mouthY - 3.dp.toPx()),
                end = Offset(centerX + baseEyeW * 0.4f, mouthY + 3.dp.toPx()),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // ─── 28. ENRAGED (โกรธจัด ตาขวางดุดันสีแดงเพลิง + ไอร้อน) ───────────────
        AvatarEmotion.ENRAGED -> {
            val fierceRed = Color(0xFFFF1744)
            val darkCrimson = Color(0xFFB71C1C)
            val rageW = baseEyeW * 1.20f
            val rageH = baseEyeH * 0.95f

            val ragePulse = 1f + living.pulse * 0.10f
            drawCircle(
                color = fierceRed.copy(alpha = 0.25f * effAlpha),
                radius = rageW * 0.95f * ragePulse,
                center = Offset(leftEyeCenterX, eyeCenterY)
            )
            drawCircle(
                color = fierceRed.copy(alpha = 0.25f * effAlpha),
                radius = rageW * 0.95f * ragePulse,
                center = Offset(rightEyeCenterX, eyeCenterY)
            )

            drawEnragedEye(fierceRed, darkCrimson, leftEyeCenterX, eyeCenterY, rageW * leftEyeScaleX, rageH, true, alpha = effAlpha)
            drawEnragedEye(fierceRed, darkCrimson, rightEyeCenterX, eyeCenterY, rageW * rightEyeScaleX, rageH, false, alpha = effAlpha)

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
            drawPath(zigzagPath, fierceRed.copy(alpha = effAlpha), style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Miter))

            val steamColor = Color.White.copy(alpha = 0.65f * effAlpha)
            val steamJitter = sin(living.loopFast * 20f) * 2.dp.toPx()
            drawSteamPuff(steamColor, centerX - baseEyeW * 1.5f + steamJitter, eyeCenterY - baseEyeH * 0.8f, 16.dp.toPx())
            drawSteamPuff(steamColor, centerX + baseEyeW * 1.5f - steamJitter, eyeCenterY - baseEyeH * 0.8f, 16.dp.toPx())
        }

        // ─── 21. PUZZLED (หน้าที่ 21: ตาสควีร์เคิลคลื่น ~ ~ + ปากคลื่น + เครื่องหมาย ¿ และ ??) ───
        AvatarEmotion.PUZZLED -> {
            drawConfusedMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.4f, 26.dp.toPx(), audioLevel)
            }
        }

        // ─── 29. SICK (ตาก้นหอยลู่ลง + ปรอทวัดไข้แก้ว) ──────────────────────────
        AvatarEmotion.SICK -> {
            drawSickMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 12.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 30. RICH (ตา $$ + ถุงเงิน + เหรียญทองร่วง) ────────────────────────
        AvatarEmotion.RICH -> {
            drawRichMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY, baseEyeW * 1.4f, 26.dp.toPx(), audioLevel)
            }
        }

        // ─── 31. CRYING (ตาเศร้า + น้ำตาไหลพราก) ──────────────────────────────
        AvatarEmotion.CRYING -> {
            drawCryingMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, tearProgress, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 32. READING (แว่นอ่านหนังสือสี่เหลี่ยม + หนังสือเปิด) ───────────────
        AvatarEmotion.READING -> {
            drawReadingMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 33. GAMING (หูฟังเกมมิ่ง + จอยคอนโทรลเลอร์) ────────────────────────
        AvatarEmotion.GAMING -> {
            drawGamingMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 34. TRAVELING (หมวกบัคเก็ต + พาสปอร์ต + ลูกโลก) ────────────────────
        AvatarEmotion.TRAVELING -> {
            drawTravelingMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 35. WORKING (แว่นกลม + แล็ปท็อป + กาแฟ) ───────────────────────────
        AvatarEmotion.WORKING -> {
            drawWorkingMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 36. COLD (ตาสั่นหนาวสะท้าน + น้ำแข็งย้อย + เกล็ดหิมะ) ──────────────
        AvatarEmotion.COLD -> {
            drawColdMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, visorW, visorH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 37. HOT (ตาลู่เหงื่อไหล + คลื่นความร้อน + พระอาทิตย์) ─────────────
        AvatarEmotion.HOT -> {
            drawHotMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 38. DETECTIVE (ตาหรี่สงสัย + หมวก Fedora + แว่นขยาย) ───────────────
        AvatarEmotion.DETECTIVE -> {
            drawDetectiveMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 39. COOKING (ตายิ้ม + หมวกเชฟ + กระทะ) ─────────────────────────────
        AvatarEmotion.COOKING -> {
            drawCookingMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 40. ART_MODE (ตากลม + หมวกเบเรต์แดง + จานสี) ──────────────────────
        AvatarEmotion.ART_MODE -> {
            drawArtMode(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 41. SPACE (หมวกนักบินอวกาศ + ดวงดาวโคจร) ──────────────────────────
        AvatarEmotion.SPACE -> {
            drawSpaceMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 42. PARTY (ตาโค้งยิ้ม + หมวกปาร์ตี้ + แตร + คอนเฟตติ) ─────────────
        AvatarEmotion.PARTY -> {
            drawPartyMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 43. DREAMING (ตาปิด + เมฆฝัน + Zzz) ───────────────────────────────
        AvatarEmotion.DREAMING -> {
            drawDreamingMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, zzzFloat, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 44. EXHAUSTED (ตาหนักลู่ลง + ลิ้นห้อย + หยดเหงื่อ) ─────────────────
        AvatarEmotion.EXHAUSTED -> {
            drawExhaustedMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 45. ELECTRIC (ตาสายฟ้าซิกแซก + ประกายไฟ ⚡) ────────────────────────
        AvatarEmotion.ELECTRIC -> {
            drawElectricMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 46. SNEAKY (หน้ากากโจร + ตาเหลือบข้าง + ปากยิ้มมุมปาก) ─────────────
        AvatarEmotion.SNEAKY -> {
            drawSneakyMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 47. HERO (หน้ากากซูเปอร์ฮีโร่ + ตาเปล่งประกาย) ───────────────────────
        AvatarEmotion.HERO -> {
            drawHeroMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 48. GLITCHED (ตาบิดเบี้ยว scanlines + RGB shift) ──────────────────
        AvatarEmotion.GLITCHED -> {
            drawGlitchedMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 49. MAGIC (หมวกพ่อมดม่วง + ไม้กายสิทธิ์ดาว + ประกายวิบวับ) ──────────
        AvatarEmotion.MAGIC -> {
            drawMagicMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 50. SPORTY (ผ้าคาดศีรษะสามสี + ลูกบาส) ────────────────────────────
        AvatarEmotion.SPORTY -> {
            drawSportyMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 51. SCIENTIST (แว่นตานิรภัยแล็บ + ขวดทดลองบีกเกอร์) ─────────────────
        AvatarEmotion.SCIENTIST -> {
            drawScientistMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 52. SCARED (ตากลมจิ๋วสั่นสะท้าน + วิญญาณหลอน) ──────────────────────
        AvatarEmotion.SCARED -> {
            drawScaredMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 53. WARRIOR (ผ้าคาดหัวสีแดงตราทอง + ดาบคู่) ────────────────────────
        AvatarEmotion.WARRIOR -> {
            drawWarriorMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 54. LOW_BATTERY (ตาอ่อนล้าใกล้ดับ + แบตเตอรี่สีแดงกะพริบ) ───────────
        AvatarEmotion.LOW_BATTERY -> {
            drawLowBatteryMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }

        // ─── 55. ROMANTIC (ตายิ้มหวาน + แก้มชมพูระเรื่อ + คาบดอกกุหลาบแดง 🌹 + ปากจูบ '3') ───
        AvatarEmotion.ROMANTIC -> {
            drawRomanticMood(centerX, centerY, leftEyeCenterX, rightEyeCenterX, eyeCenterY, baseEyeW, baseEyeH, alpha = effAlpha, living = living, includeEyes = true)
            if (isSpeaking) {
                drawWaveformMouth(cyanLed, centerX, mouthY + 10.dp.toPx(), baseEyeW * 1.3f, 24.dp.toPx(), audioLevel)
            }
        }
    }
}

// ─── HELPER DRAW FUNCTIONS ───────────────────────────────────────────────────

/**
 * วาดดวงตาแบบ 2D Squircle พร้อมเลเยอร์เงาสีเข้มด้านล่าง (Shallow 2D Depth — LOOI Robot Style)
 * ตรงตามภาพอ้างอิง LOOI ROBOT: 20 MOODSET (NEON CYAN STYLE):
 * - รูปทรง Squircle (สี่เหลี่ยมมุมโค้งมนสูง มนละมุน)
 * - เลเยอร์ล่าง (Bottom Layer): สี Dark Cyan (#004D6B) เยื้องลงมา 5dp สร้างมิติตื้น 2D Shallow Depth
 * - เลเยอร์บน (Front Layer): สีนีออนสว่างสดใส (#00F5FF) ทึบสนิท สะอาดตา
 * - ขอบเรืองแสงนุ่มนวล (Soft Glowing Edges)
 * - การมองตามทิศทาง (Gaze Tracking): ขยับตำแหน่งเลเยอร์หน้าตามทิศทางการมอง
 */
internal fun DrawScope.drawDualCircleEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    gazeX: Float = 0f,
    gazeY: Float = 0f,
    squashX: Float = 1f,
    squashY: Float = 1f,
    backColorOverride: Color? = null,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val effColor = color.copy(alpha = effAlpha)
    val effW = width * squashX
    val effH = height * squashY
    val cornerRadius = CornerRadius(effW * 0.46f, effH * 0.46f)

    val maxShift = 4.dp.toPx()
    val frontShiftX = gazeX * maxShift
    val frontShiftY = gazeY * maxShift

    val eyeTopLeft = Offset(centerX + frontShiftX - effW / 2f, centerY + frontShiftY - effH / 2f)
    val eyeSize = Size(effW, effH)

    // 2.5D Spherical Depth Neon-Glow Eye Drawing (LOOI Robot Physical Product & Moodset Style)
    // Layer 1: Glow layer (15-20% larger, alpha ~0.52, accentColor)
    // Layer 2: 2.5D Spherical Gradient Core (top-left highlight -> base color -> deep indigo crescent shadow)
    // Layer 3: Soft specular highlight glint at top-left
    drawNeonEyeRoundRect(
        color = effColor,
        topLeft = eyeTopLeft,
        size = eyeSize,
        cornerRadius = cornerRadius,
        glowRadiusDp = 10.dp,
        glowAlpha = 0.52f,
        alpha = effAlpha
    )
}

/**
 * ฟังก์ชันสำหรับวาดดวงตา Squircle
 */
internal fun DrawScope.drawSquircleEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    squashX: Float = 1f,
    squashY: Float = 1f,
    alpha: Float = 1f
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
        squashY = squashY,
        alpha = alpha
    )
}

/**
 * วาดตายิ้มโค้งทรงพระจันทร์ครึ่งดวง (⌒) สไตล์ LOOI Neon Cyan (2-Layer Flat Neon)
 */
internal fun DrawScope.drawHappyEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    gazeX: Float = 0f,
    gazeY: Float = 0f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val effColor = color.copy(alpha = effAlpha)
    val arcW = width * 1.0f
    val arcH = height * 0.55f
    val strokeW = 16.dp.toPx()

    val frontShiftX = gazeX * 3.dp.toPx()
    val frontShiftY = gazeY * 2.dp.toPx()

    val arcPath = Path().apply {
        moveTo(centerX + frontShiftX - arcW / 2f, centerY + arcH * 0.25f + frontShiftY)
        cubicTo(
            centerX + frontShiftX - arcW * 0.35f, centerY - arcH * 0.70f + frontShiftY,
            centerX + frontShiftX + arcW * 0.35f, centerY - arcH * 0.70f + frontShiftY,
            centerX + frontShiftX + arcW / 2f, centerY + arcH * 0.25f + frontShiftY
        )
    }

    drawNeonPath(
        path = arcPath,
        color = effColor,
        strokeWidth = strokeW,
        glowRadiusDp = 10.dp,
        glowAlpha = 0.52f,
        alpha = effAlpha
    )
}

/**
 * วาดตาหลับขีดมน (—) สไตล์ LOOI Neon Cyan (2-Layer Flat Neon)
 */
internal fun DrawScope.drawSleepingEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    barHeight: Float = 14.dp.toPx(),
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val effColor = color.copy(alpha = effAlpha)
    val barW = width * 0.95f
    val barH = barHeight
    val cornerRadius = CornerRadius(barH / 2f, barH / 2f)

    drawNeonRoundRect(
        color = effColor,
        topLeft = Offset(centerX - barW / 2f, centerY - barH / 2f),
        size = Size(barW, barH),
        cornerRadius = cornerRadius,
        glowRadiusDp = 8.dp,
        glowAlpha = 0.52f,
        alpha = effAlpha
    )
}

/**
 * วาดตาหัวเราะทรงสามเหลี่ยมลูกศร (> <) สไตล์ LOOI Neon Cyan + Shallow Depth
 */
internal fun DrawScope.drawExcitedEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    isLeft: Boolean,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val effColor = color.copy(alpha = effAlpha)
    val sizeW = width * 0.70f
    val sizeH = height * 0.65f
    val strokeW = 14.dp.toPx()
    val shadowOffsetY = 5.dp.toPx()
    val shadowColor = Color(0xFF004D6B).copy(alpha = effAlpha)

    val dir = if (isLeft) 1f else -1f
    fun buildChevronPath(offsetY: Float) = Path().apply {
        moveTo(centerX - dir * sizeW * 0.45f, centerY - sizeH * 0.45f + offsetY)
        lineTo(centerX + dir * sizeW * 0.45f, centerY + offsetY)
        lineTo(centerX - dir * sizeW * 0.45f, centerY + sizeH * 0.45f + offsetY)
    }

    // Glow
    drawPath(buildChevronPath(0f), effColor.copy(alpha = 0.20f * effAlpha), style = Stroke(width = strokeW + 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    // Shallow 2D Shadow Underneath
    drawPath(buildChevronPath(shadowOffsetY), shadowColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // Front Chevron
    drawPath(buildChevronPath(0f), effColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * วาดตาโกรธเฉียงสีแดงเพลิงพร้อมเงา Shallow 2D Depth
 */
internal fun DrawScope.drawAngryEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    isLeft: Boolean,
    slantProgress: Float = 1f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val effColor = color.copy(alpha = effAlpha)
    val effW = width * 1.05f
    val effH = height * 0.85f
    val shadowOffsetY = 5.dp.toPx()
    val shadowColor = Color(0xFF5C0018).copy(alpha = effAlpha)
    val maxSlant = 16.dp.toPx()
    val slant = (if (isLeft) maxSlant else -maxSlant) * slantProgress

    fun buildEyePath(offsetY: Float): Path = Path().apply {
        val leftX = centerX - effW / 2f
        val rightX = centerX + effW / 2f
        val topL = centerY - effH * 0.42f + (if (isLeft) 0f else slant.absoluteValue) + offsetY
        val topR = centerY - effH * 0.42f + (if (isLeft) slant.absoluteValue else 0f) + offsetY
        val bottomY = centerY + effH * 0.46f + offsetY

        moveTo(leftX, topL)
        lineTo(rightX, topR)
        cubicTo(
            rightX + 2.dp.toPx(), bottomY,
            leftX - 2.dp.toPx(), bottomY,
            leftX, topL
        )
        close()
    }

    // Glow
    drawPath(buildEyePath(0f), effColor.copy(alpha = 0.25f * effAlpha), style = Stroke(width = 8.dp.toPx(), join = StrokeJoin.Round))
    // Shallow 2D Shadow Underneath
    drawPath(buildEyePath(shadowOffsetY), shadowColor)
    // Front Eye
    drawPath(buildEyePath(0f), effColor)
}

/**
 * วาดตา X X สำหรับอาการสลบ Dead / มึนงง
 */
internal fun DrawScope.drawCrossEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val effColor = color.copy(alpha = effAlpha)
    val strokeW = 14.dp.toPx()
    val arm = size * 0.38f
    val shadowOffsetY = 5.dp.toPx()
    val shadowColor = Color(0xFF004D6B).copy(alpha = effAlpha)

    // Glow
    drawLine(effColor.copy(alpha = 0.18f * effAlpha), Offset(centerX - arm, centerY - arm), Offset(centerX + arm, centerY + arm), strokeWidth = strokeW + 8.dp.toPx(), cap = StrokeCap.Round)
    drawLine(effColor.copy(alpha = 0.18f * effAlpha), Offset(centerX - arm, centerY + arm), Offset(centerX + arm, centerY - arm), strokeWidth = strokeW + 8.dp.toPx(), cap = StrokeCap.Round)

    // Shallow 2D Shadow Underneath
    drawLine(shadowColor, Offset(centerX - arm, centerY - arm + shadowOffsetY), Offset(centerX + arm, centerY + arm + shadowOffsetY), strokeWidth = strokeW, cap = StrokeCap.Round)
    drawLine(shadowColor, Offset(centerX - arm, centerY + arm + shadowOffsetY), Offset(centerX + arm, centerY - arm + shadowOffsetY), strokeWidth = strokeW, cap = StrokeCap.Round)

    // Front Cross
    drawLine(effColor, Offset(centerX - arm, centerY - arm), Offset(centerX + arm, centerY + arm), strokeWidth = strokeW, cap = StrokeCap.Round)
    drawLine(effColor, Offset(centerX - arm, centerY + arm), Offset(centerX + arm, centerY - arm), strokeWidth = strokeW, cap = StrokeCap.Round)
}

/**
 * วาดตาขวาทรงลิ่มเอียง (Curious Right Eye)
 */
internal fun DrawScope.drawLooiCuriousRightEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    alpha: Float = 1f
) {
    rotate(degrees = 12f, pivot = Offset(centerX, centerY)) {
        val effW = width * 0.95f
        val effH = 16.dp.toPx()
        drawSleepingEye(color, centerX, centerY, effW, effH, alpha = alpha)
    }
}

/**
 * วาดเขี้ยวสีขาวคู่ (v v) สไตล์ LOOI Angry & Evil
 */
internal fun DrawScope.drawLooiFangs(
    centerX: Float,
    centerY: Float,
    size: Float = 10.dp.toPx(),
    progress: Float = 1f,
    alpha: Float = 1f
) {
    if (progress <= 0.01f || alpha <= 0.01f) return
    val effAlpha = (progress * alpha).coerceIn(0f, 1f)
    val slideY = (1f - progress) * -14.dp.toPx()
    val fangW = size * 0.8f * progress
    val fangH = size * 1.1f * progress
    val gap = size * 1.2f

    val leftPath = Path().apply {
        moveTo(centerX - gap / 2f - fangW / 2f, centerY + slideY)
        lineTo(centerX - gap / 2f + fangW / 2f, centerY + slideY)
        lineTo(centerX - gap / 2f, centerY + slideY + fangH)
        close()
    }
    val rightPath = Path().apply {
        moveTo(centerX + gap / 2f - fangW / 2f, centerY + slideY)
        lineTo(centerX + gap / 2f + fangW / 2f, centerY + slideY)
        lineTo(centerX + gap / 2f, centerY + slideY + fangH)
        close()
    }
    drawPath(leftPath, Color.White.copy(alpha = effAlpha))
    drawPath(rightPath, Color.White.copy(alpha = effAlpha))
}

/**
 * วาดเส้นเลือดปูดสีแดง (💢) ด้านบนขวา สไตล์ LOOI Angry
 */
internal fun DrawScope.drawLooiAngerVein(
    centerX: Float,
    centerY: Float,
    size: Float = 22.dp.toPx(),
    progress: Float = 1f,
    alpha: Float = 1f
) {
    if (progress <= 0.01f || alpha <= 0.01f) return
    val effAlpha = (progress * alpha).coerceIn(0f, 1f)
    val effSize = size * progress
    val red = Color(0xFFFF3B5C).copy(alpha = effAlpha)
    val strokeW = 3.5.dp.toPx() * progress
    val r = effSize / 2f
    drawArc(red, 0f, 90f, false, Offset(centerX, centerY), Size(r, r), style = Stroke(strokeW, cap = StrokeCap.Round))
    drawArc(red, 90f, 90f, false, Offset(centerX - r, centerY), Size(r, r), style = Stroke(strokeW, cap = StrokeCap.Round))
    drawArc(red, 180f, 90f, false, Offset(centerX - r, centerY - r), Size(r, r), style = Stroke(strokeW, cap = StrokeCap.Round))
    drawArc(red, 270f, 90f, false, Offset(centerX, centerY - r), Size(r, r), style = Stroke(strokeW, cap = StrokeCap.Round))
}

/**
 * วาดอุ้งมือกลหุ่นยนต์ LOOI (Robotic Cyber Paw / Clamp)
 */
internal fun DrawScope.drawRobotPaw(
    center: Offset,
    radius: Float = 14.dp.toPx(),
    angleDeg: Float = 0f,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    rotate(angleDeg, pivot = center) {
        // Outer metallic dark chassis
        drawCircle(
            color = Color(0xFF37474F).copy(alpha = effAlpha),
            radius = radius,
            center = center
        )
        // Inner glowing cyan LED pad
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.9f * effAlpha),
            radius = radius * 0.62f,
            center = center
        )
        // 3 Mechanical finger clamps
        val fingerR = radius * 0.35f
        val fingerDist = radius * 0.90f
        for (i in -1..1) {
            val fAngle = (i * 26f) * (PI.toFloat() / 180f)
            val fx = center.x + sin(fAngle) * fingerDist
            val fy = center.y - cos(fAngle) * fingerDist
            drawCircle(
                color = Color(0xFF90A4AE).copy(alpha = effAlpha),
                radius = fingerR,
                center = Offset(fx, fy)
            )
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.85f * effAlpha),
                radius = fingerR * 0.5f,
                center = Offset(fx, fy)
            )
        }
    }
}

/**
 * วาดเบอร์เกอร์ชิ้นโต (78-84dp) พร้อมอุ้งมือหุ่นยนต์จับสองข้าง รอยกัด ควันอุ่น และเศษขนมปังร่วง
 */
internal fun DrawScope.drawLooiBurgerWithHands(
    centerX: Float,
    centerY: Float,
    width: Float = 80.dp.toPx(),
    chewCycle: Float = 0f,
    loopFast: Float = 0f,
    alpha: Float = 1f,
    detailLevel: AvatarDetailLevel = AvatarDetailLevel.RICH
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val h = width * 0.72f
    val bunTopH = h * 0.38f

    // Chewing chomp squash and bob
    val chompSquash = if (chewCycle in 0.28f..0.44f) {
        sin((chewCycle - 0.28f) / 0.16f * PI.toFloat()) * 3.dp.toPx()
    } else {
        0f
    }
    val cy = centerY + chompSquash

    // ── 3. Falling Golden Bread Crumbs from munching (RICH detail only) ──
    if (detailLevel == AvatarDetailLevel.RICH && chewCycle in 0.30f..0.75f) {
        val crumbProg = ((chewCycle - 0.30f) / 0.45f).coerceIn(0f, 1f)
        val crumbOffsets = floatArrayOf(-0.25f, -0.05f, 0.18f, 0.32f)
        for (i in crumbOffsets.indices) {
            val cxC = centerX + width * crumbOffsets[i]
            val cyC = cy + h * 0.30f + crumbProg * (28.dp.toPx() + i * 4.dp.toPx())
            val crAlpha = ((1f - crumbProg) * 0.9f * effAlpha).coerceIn(0f, 1f)
            drawCircle(
                color = Color(0xFFFFD54F).copy(alpha = crAlpha),
                radius = 2.dp.toPx(),
                center = Offset(cxC, cyC)
            )
        }
    }

    // ── 4. Golden Toasted Top Bun ──
    drawArc(
        color = Color(0xFFFFB300).copy(alpha = effAlpha),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(centerX - width / 2f, cy - h / 2f),
        size = Size(width, bunTopH * 2f)
    )
    // Sesame seeds on top bun
    val seedOffsets = floatArrayOf(-0.30f, -0.15f, 0.05f, 0.22f, -0.05f)
    for (i in seedOffsets.indices) {
        val sx = centerX + width * seedOffsets[i]
        val sy = cy - h * 0.28f + kotlin.math.abs(seedOffsets[i]) * 8.dp.toPx()
        drawOval(
            color = Color(0xFFFFF9C4).copy(alpha = effAlpha),
            topLeft = Offset(sx - 2.5.dp.toPx(), sy - 1.5.dp.toPx()),
            size = Size(5.dp.toPx(), 2.8.dp.toPx())
        )
    }

    // ── 5. Crisp Fresh Wavy Lettuce ──
    val lettucePath = Path().apply {
        moveTo(centerX - width * 0.53f, cy - h * 0.02f)
        var lx = centerX - width * 0.53f
        val segW = width * 1.06f / 5f
        for (i in 0..4) {
            val midX = lx + segW / 2f
            val nxtX = lx + segW
            quadraticTo(midX, cy + h * 0.06f, nxtX, cy - h * 0.02f)
            lx = nxtX
        }
        close()
    }
    drawPath(lettucePath, Color(0xFF4CAF50).copy(alpha = effAlpha))

    // ── 6. Ripe Red Tomato Slices ──
    drawRoundRect(
        color = Color(0xFFFF5252).copy(alpha = effAlpha),
        topLeft = Offset(centerX - width * 0.46f, cy + h * 0.06f),
        size = Size(width * 0.92f, h * 0.12f),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )

    // ── 7. Dripping Melted Cheddar Cheese ──
    val cheesePath = Path().apply {
        moveTo(centerX - width * 0.48f, cy + h * 0.16f)
        lineTo(centerX + width * 0.48f, cy + h * 0.16f)
        lineTo(centerX + width * 0.30f, cy + h * 0.36f) // right drip
        lineTo(centerX + width * 0.15f, cy + h * 0.20f)
        lineTo(centerX - width * 0.10f, cy + h * 0.40f) // center drip
        lineTo(centerX - width * 0.25f, cy + h * 0.20f)
        close()
    }
    drawPath(cheesePath, Color(0xFFFFD54F).copy(alpha = effAlpha))

    // ── 8. Flame-Grilled Juicy Patty ──
    drawRoundRect(
        color = Color(0xFF4E342E).copy(alpha = effAlpha),
        topLeft = Offset(centerX - width * 0.50f, cy + h * 0.22f),
        size = Size(width, h * 0.26f),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
    )
    // Grill charcoal stripes
    drawLine(Color(0xFF2E1C14).copy(alpha = 0.6f * effAlpha), Offset(centerX - width * 0.3f, cy + h * 0.26f), Offset(centerX - width * 0.2f, cy + h * 0.44f), strokeWidth = 2.dp.toPx())
    drawLine(Color(0xFF2E1C14).copy(alpha = 0.6f * effAlpha), Offset(centerX, cy + h * 0.26f), Offset(centerX + width * 0.1f, cy + h * 0.44f), strokeWidth = 2.dp.toPx())

    // ── 9. Toasted Bottom Bun ──
    drawRoundRect(
        color = Color(0xFFFFA000).copy(alpha = effAlpha),
        topLeft = Offset(centerX - width * 0.46f, cy + h * 0.46f),
        size = Size(width * 0.92f, h * 0.22f),
        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
    )
}

/**
 * วาดปากกำลังเคี้ยวตุ้ยๆ (Chewing mouth)
 */
internal fun DrawScope.drawChewingMouth(
    cyanLed: Color,
    centerX: Float,
    mouthY: Float,
    baseEyeW: Float,
    chewCycle: Float = 0f,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)

    // Chewing Mouth Shape
    val mouthW = baseEyeW * 1.1f
    when {
        chewCycle < 0.32f -> {
            // Chomp! Mouth opens wide to bite
            val openH = 16.dp.toPx() * sin(chewCycle / 0.32f * PI.toFloat())
            val chompPath = Path().apply {
                moveTo(centerX - mouthW * 0.4f, mouthY - openH * 0.5f)
                quadraticTo(centerX, mouthY - openH * 0.9f, centerX + mouthW * 0.4f, mouthY - openH * 0.5f)
                quadraticTo(centerX, mouthY + openH * 0.9f, centerX - mouthW * 0.4f, mouthY - openH * 0.5f)
                close()
            }
            drawPath(chompPath, cyanLed.copy(alpha = 0.85f * effAlpha))
        }
        chewCycle in 0.32f..0.82f -> {
            // Munching & Chewing (เคี้ยวแก้มตุ่ย)
            val chewShift = sin((chewCycle - 0.32f) / 0.50f * 4f * PI.toFloat()) * 4.dp.toPx()
            val chewCurve = cos((chewCycle - 0.32f) / 0.50f * 4f * PI.toFloat()) * 3.dp.toPx()
            val chewPath = Path().apply {
                moveTo(centerX - mouthW * 0.42f + chewShift, mouthY)
                quadraticTo(centerX - mouthW * 0.15f, mouthY + chewCurve, centerX + chewShift * 0.5f, mouthY)
                quadraticTo(centerX + mouthW * 0.25f, mouthY - chewCurve, centerX + mouthW * 0.42f + chewShift, mouthY)
            }
            drawPath(chewPath, cyanLed.copy(alpha = effAlpha), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        }
        else -> {
            // Delicious smile ˘◡˘
            val smilePath = Path().apply {
                moveTo(centerX - mouthW * 0.38f, mouthY - 2.dp.toPx())
                quadraticTo(centerX, mouthY + 8.dp.toPx(), centerX + mouthW * 0.38f, mouthY - 2.dp.toPx())
            }
            drawPath(smilePath, cyanLed.copy(alpha = effAlpha), style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/**
 * วาดแก้วเบียร์ทรงไพนต์ (Pint Glass / Tumbler) ตรงกลางระหว่างดวงตา (Sheet 1 Cell 7)
 * แก้วทรงกระบอกสอบลงด้านล่าง ฟองเบียร์สีขาวพูนด้านบน ฟองก๊าซลอยขึ้น ประกายแสงสะท้อนแนวตั้ง
 */
internal fun DrawScope.drawLooiBeerSteinWithDrinking(
    centerX: Float,
    centerY: Float,
    width: Float = 54.dp.toPx(),
    gulpCycle: Float = 0f,
    loopFast: Float = 0f,
    alpha: Float = 1f,
    detailLevel: AvatarDetailLevel = AvatarDetailLevel.RICH
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val h = width * 1.36f

    // ── 1. Tapered Pint Tumbler Glass Body ──
    val topW = width
    val bottomW = width * 0.72f
    val glassTopY = centerY - h * 0.45f
    val glassBottomY = centerY + h * 0.50f

    // Golden Beer Fill inside tapered glass
    val beerPath = Path().apply {
        moveTo(centerX - topW * 0.46f, glassTopY + 4.dp.toPx())
        lineTo(centerX + topW * 0.46f, glassTopY + 4.dp.toPx())
        lineTo(centerX + bottomW * 0.46f, glassBottomY)
        lineTo(centerX - bottomW * 0.46f, glassBottomY)
        close()
    }
    drawPath(beerPath, Color(0xFFFFB300).copy(alpha = 0.95f * effAlpha))

    // Amber gradient depth in lower half
    val amberPath = Path().apply {
        moveTo(centerX - (topW * 0.46f + (bottomW * 0.46f - topW * 0.46f) * 0.5f), centerY + 2.dp.toPx())
        lineTo(centerX + (topW * 0.46f + (bottomW * 0.46f - topW * 0.46f) * 0.5f), centerY + 2.dp.toPx())
        lineTo(centerX + bottomW * 0.46f, glassBottomY)
        lineTo(centerX - bottomW * 0.46f, glassBottomY)
        close()
    }
    drawPath(amberPath, Color(0xFFFF8F00).copy(alpha = 0.55f * effAlpha))

    // ── 2. Streaming Carbonation Bubbles rising up (RICH detail only) ──
    if (detailLevel == AvatarDetailLevel.RICH) {
        for (i in 0..4) {
            val bp = ((loopFast + i * 0.2f) % 1f)
            val bx = centerX - bottomW * 0.35f + i * (bottomW * 0.18f)
            val by = (glassBottomY - 4.dp.toPx()) - bp * (h * 0.75f)
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * (1f - bp * 0.3f) * effAlpha),
                radius = (1.5f + (i % 2) * 1f).dp.toPx(),
                center = Offset(bx, by)
            )
        }
    }

    // ── 3. Thick Frothy White Foam Head on Top ──
    val foamH = h * 0.28f
    val foamY = glassTopY - foamH * 0.35f
    // Foam base oval
    drawOval(
        color = Color.White.copy(alpha = effAlpha),
        topLeft = Offset(centerX - topW * 0.52f, foamY),
        size = Size(topW * 1.04f, foamH * 0.8f)
    )
    // Fluffy rounded foam puffs
    drawCircle(Color.White.copy(alpha = effAlpha), radius = foamH * 0.45f, center = Offset(centerX - topW * 0.25f, foamY + foamH * 0.25f))
    drawCircle(Color.White.copy(alpha = effAlpha), radius = foamH * 0.52f, center = Offset(centerX, foamY + foamH * 0.15f))
    drawCircle(Color.White.copy(alpha = effAlpha), radius = foamH * 0.45f, center = Offset(centerX + topW * 0.25f, foamY + foamH * 0.25f))

    // Slight foam drip down the right side of the rim
    drawCircle(Color.White.copy(alpha = effAlpha), radius = 3.dp.toPx(), center = Offset(centerX + topW * 0.48f, glassTopY + 6.dp.toPx()))

    // ── 4. Glass Outline (Pint Tumbler) ──
    val glassOutline = Path().apply {
        moveTo(centerX - topW * 0.48f, glassTopY)
        lineTo(centerX + topW * 0.48f, glassTopY)
        lineTo(centerX + bottomW * 0.48f, glassBottomY)
        lineTo(centerX - bottomW * 0.48f, glassBottomY)
        close()
    }
    drawPath(glassOutline, Color.White.copy(alpha = 0.6f * effAlpha), style = Stroke(width = 2.dp.toPx()))

    // ── 5. Vertical Glass Highlights & Shimmer ──
    drawLine(
        color = Color.White.copy(alpha = 0.55f * effAlpha),
        start = Offset(centerX - topW * 0.35f, glassTopY + 8.dp.toPx()),
        end = Offset(centerX - bottomW * 0.35f, glassBottomY - 6.dp.toPx()),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round
    )
}

/**
 * Delegators สำหรับคงความเข้ากันได้
 */
internal fun DrawScope.drawLooiMiniBurger(
    centerX: Float,
    centerY: Float,
    width: Float = 76.dp.toPx(),
    alpha: Float = 1f
) = drawLooiBurgerWithHands(centerX, centerY, width, chewCycle = 0.5f, loopFast = 0.5f, alpha = alpha)

internal fun DrawScope.drawLooiMiniBeer(
    centerX: Float,
    centerY: Float,
    width: Float = 62.dp.toPx(),
    alpha: Float = 1f
) = drawLooiBeerSteinWithDrinking(centerX, centerY, width, gulpCycle = 0.5f, loopFast = 0.5f, alpha = alpha)

/**
 * วาดประกายดาว 4 แฉกสีทอง (✦)
 */
internal fun DrawScope.drawLooiSparkleStar(
    color: Color = Color(0xFFFFD700),
    centerX: Float,
    centerY: Float,
    size: Float = 24.dp.toPx(),
    alpha: Float = 1f
) {
    drawNeonSparkle(
        color = color,
        centerX = centerX,
        centerY = centerY,
        size = size,
        alpha = alpha
    )
}

/**
 * วาดหูฟังเพลงครอบหู Headphones สไตล์ LOOI Music (Sheet 1 Cell 11)
 */
internal fun DrawScope.drawLooiHeadphones(
    centerX: Float,
    centerY: Float,
    headW: Float,
    headH: Float,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val headbandColor = Color(0xFFCFD8DC).copy(alpha = effAlpha)
    val innerBandColor = Color(0xFF263238).copy(alpha = effAlpha)
    val earcupOuterColor = Color(0xFFECEFF1).copy(alpha = effAlpha)
    val earcupCushionColor = Color(0xFFFFD54F).copy(alpha = effAlpha)

    val earcupW = 16.dp.toPx()
    val earcupH = headH * 0.65f
    val earcupSpan = headW

    // 1. Headband arch - wraps snugly right above eyes
    val archW = earcupSpan * 0.96f
    val archH = headH * 0.68f
    val archTopY = centerY - archH * 0.85f
    drawArc(
        color = innerBandColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(centerX - archW / 2f, archTopY + 2.dp.toPx()),
        size = Size(archW, archH),
        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
    )
    drawArc(
        color = headbandColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(centerX - archW / 2f, archTopY),
        size = Size(archW, archH),
        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
    )

    // 2. Left Earcup - snug against left eye
    val leftX = centerX - earcupSpan / 2f
    drawRoundRect(
        color = earcupOuterColor,
        topLeft = Offset(leftX - earcupW / 2f, centerY - earcupH / 2f),
        size = Size(earcupW, earcupH),
        cornerRadius = CornerRadius(earcupW / 2f, earcupW / 2f)
    )
    drawRoundRect(
        color = earcupCushionColor,
        topLeft = Offset(leftX - earcupW * 0.25f, centerY - earcupH * 0.38f),
        size = Size(earcupW * 0.6f, earcupH * 0.76f),
        cornerRadius = CornerRadius(earcupW * 0.3f, earcupW * 0.3f)
    )

    // 3. Right Earcup - snug against right eye
    val rightX = centerX + earcupSpan / 2f
    drawRoundRect(
        color = earcupOuterColor,
        topLeft = Offset(rightX - earcupW / 2f, centerY - earcupH / 2f),
        size = Size(earcupW, earcupH),
        cornerRadius = CornerRadius(earcupW / 2f, earcupW / 2f)
    )
    drawRoundRect(
        color = earcupCushionColor,
        topLeft = Offset(rightX - earcupW * 0.35f, centerY - earcupH * 0.38f),
        size = Size(earcupW * 0.6f, earcupH * 0.76f),
        cornerRadius = CornerRadius(earcupW * 0.3f, earcupW * 0.3f)
    )
}

/**
 * วาดแว่นตา VR Headset ทรง Apple Vision Pro สไตล์ LOOI (Sheet 1 Cell 12)
 */
internal fun DrawScope.drawLooiVrHeadset(
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val visorW = width
    val visorH = height
    val halfW = visorW / 2f
    val halfH = visorH / 2f
    val r = visorH * 0.42f

    val visorPath = Path().apply {
        moveTo(centerX - halfW + r, centerY - halfH)
        lineTo(centerX + halfW - r, centerY - halfH)
        quadraticTo(centerX + halfW, centerY - halfH, centerX + halfW, centerY)
        quadraticTo(centerX + halfW, centerY + halfH, centerX + halfW - r, centerY + halfH)
        lineTo(centerX + 20.dp.toPx(), centerY + halfH)
        // Nose bridge cutout
        quadraticTo(centerX, centerY + halfH - 12.dp.toPx(), centerX - 20.dp.toPx(), centerY + halfH)
        lineTo(centerX - halfW + r, centerY + halfH)
        quadraticTo(centerX - halfW, centerY + halfH, centerX - halfW, centerY)
        quadraticTo(centerX - halfW, centerY - halfH, centerX - halfW + r, centerY - halfH)
        close()
    }

    // Outer white/silver metallic frame
    drawPath(
        path = visorPath,
        color = Color(0xFFEEEEEE).copy(alpha = effAlpha),
        style = Stroke(width = 4.dp.toPx())
    )

    // Dark glossy glass with purple & cyan reflection
    drawPath(
        path = visorPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF0B011D).copy(alpha = 0.94f * effAlpha),
                Color(0xFF23103D).copy(alpha = 0.88f * effAlpha),
                Color(0xFF00293B).copy(alpha = 0.94f * effAlpha)
            ),
            start = Offset(centerX - halfW, centerY - halfH),
            end = Offset(centerX + halfW, centerY + halfH)
        )
    )

    // Curved glare reflection streak
    drawArc(
        color = Color.White.copy(alpha = 0.22f * effAlpha),
        startAngle = 190f,
        sweepAngle = 160f,
        useCenter = false,
        topLeft = Offset(centerX - visorW * 0.40f, centerY - visorH * 0.42f),
        size = Size(visorW * 0.80f, visorH * 0.42f),
        style = Stroke(width = 2.dp.toPx())
    )
}

/**
 * วาดถังป๊อปคอร์นโรงหนัง (Cinema Popcorn Bucket) ที่มุมล่างขวาของแว่น VR (Sheet 1 Cell 12)
 * ถังสีแดงลายทางขาวขอบทอง เม็ดป๊อปคอร์นสีทองนุ่มฟูพูนถัง
 */
internal fun DrawScope.drawLooiPopcornBucket(
    centerX: Float,
    centerY: Float,
    mouthX: Float = 0f,
    mouthY: Float = 0f,
    width: Float = 58.dp.toPx(),
    popPhase: Float = 0f,
    chewCycle: Float = 0f,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val h = width * 1.15f

    // ── 1. Striped Cinema Bucket ──
    val bucketPath = Path().apply {
        moveTo(centerX - width * 0.50f, centerY)
        lineTo(centerX + width * 0.50f, centerY)
        lineTo(centerX + width * 0.36f, centerY + h)
        lineTo(centerX - width * 0.36f, centerY + h)
        close()
    }
    drawPath(bucketPath, Color(0xFFD32F2F).copy(alpha = effAlpha))

    // Crisp white vertical cinema stripes
    val stripeWidth = (width * 0.11f).coerceIn(4.dp.toPx(), 7.dp.toPx())
    drawLine(Color.White.copy(alpha = effAlpha), Offset(centerX - width * 0.25f, centerY), Offset(centerX - width * 0.18f, centerY + h), stripeWidth)
    drawLine(Color.White.copy(alpha = effAlpha), Offset(centerX, centerY), Offset(centerX, centerY + h), stripeWidth)
    drawLine(Color.White.copy(alpha = effAlpha), Offset(centerX + width * 0.25f, centerY), Offset(centerX + width * 0.18f, centerY + h), stripeWidth)

    // Bucket outer outline
    drawPath(bucketPath, Color(0xFFB71C1C).copy(alpha = effAlpha), style = Stroke(width = 1.5.dp.toPx()))

    // Gold decorative cinema band at the top rim
    drawRoundRect(
        color = Color(0xFFFFD54F).copy(alpha = effAlpha),
        topLeft = Offset(centerX - width * 0.52f, centerY - 2.dp.toPx()),
        size = Size(width * 1.04f, 5.5.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )

    // ── 2. Heaping Piles of Golden Buttery Popcorn Kernels ──
    val popCol = Color(0xFFFFF59D).copy(alpha = effAlpha)
    val popColDark = Color(0xFFFFEE58).copy(alpha = effAlpha)
    val kernelOffsets = floatArrayOf(-0.35f, -0.18f, 0.02f, 0.22f, -0.06f)
    val kernelRadius = width * 0.15f
    for (i in kernelOffsets.indices) {
        val px = centerX + width * kernelOffsets[i]
        val py = centerY - h * 0.06f - kotlin.math.abs(kernelOffsets[i]) * (h * 0.14f)
        drawCircle(popCol, radius = kernelRadius, center = Offset(px, py))
        drawCircle(popColDark, radius = kernelRadius * 0.65f, center = Offset(px, py))
    }
}

/**
 * Delegator สำหรับคงความเข้ากันได้
 */
internal fun DrawScope.drawLooiMiniPopcorn(
    centerX: Float,
    centerY: Float,
    width: Float = 58.dp.toPx(),
    alpha: Float = 1f
) = drawLooiPopcornBucket(centerX, centerY, centerX - width * 0.6f, centerY, width, popPhase = 0.5f, chewCycle = 0.5f, alpha = alpha)

/**
 * วาดหน้ากากดำน้ำ Scuba Diving Mask + ท่อหายใจ Snorkel (Sheet 1 Cell 13)
 */
internal fun DrawScope.drawLooiSnorkelMask(
    centerX: Float,
    centerY: Float,
    eyeSpacing: Float,
    baseEyeW: Float,
    baseEyeH: Float,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val rimColor = Color(0xFFFFCA28).copy(alpha = effAlpha) // Yellow/gold dive mask rim
    val maskW = (eyeSpacing * 2f) + (baseEyeW * 1.15f)
    val maskH = baseEyeH * 1.25f
    val halfW = maskW / 2f
    val halfH = maskH / 2f
    val r = maskH * 0.38f

    val gogglePath = Path().apply {
        moveTo(centerX - halfW + r, centerY - halfH)
        lineTo(centerX + halfW - r, centerY - halfH)
        quadraticTo(centerX + halfW, centerY - halfH, centerX + halfW, centerY)
        quadraticTo(centerX + halfW, centerY + halfH, centerX + halfW - r, centerY + halfH)
        lineTo(centerX + 16.dp.toPx(), centerY + halfH)
        // Nose bridge cutout
        quadraticTo(centerX, centerY + halfH - 10.dp.toPx(), centerX - 16.dp.toPx(), centerY + halfH)
        lineTo(centerX - halfW + r, centerY + halfH)
        quadraticTo(centerX - halfW, centerY + halfH, centerX - halfW, centerY)
        quadraticTo(centerX - halfW, centerY - halfH, centerX - halfW + r, centerY - halfH)
        close()
    }

    // Transparent aquatic cyan glass tint
    drawPath(
        path = gogglePath,
        color = Color(0xFF00E5FF).copy(alpha = 0.08f * effAlpha)
    )
    // Mask Frame Rim
    drawPath(
        path = gogglePath,
        color = rimColor,
        style = Stroke(width = 5.dp.toPx(), join = StrokeJoin.Round)
    )

    // Snorkel tube wrapping around right side
    val tubeStartX = centerX + halfW - 8.dp.toPx()
    val tubeStartY = centerY + halfH * 0.35f
    val tubeTopX = centerX + halfW + 14.dp.toPx()
    val tubeTopY = centerY - halfH * 0.75f
    val tubePath = Path().apply {
        moveTo(tubeStartX, tubeStartY)
        cubicTo(
            centerX + halfW + 18.dp.toPx(), tubeStartY + 8.dp.toPx(),
            centerX + halfW + 24.dp.toPx(), centerY,
            tubeTopX, tubeTopY
        )
    }
    drawPath(tubePath, rimColor, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))

    // Snorkel top valve (Orange cap)
    drawCircle(Color(0xFFFF9800).copy(alpha = effAlpha), 4.5.dp.toPx(), Offset(tubeTopX, tubeTopY))

    // Rising bubbles from snorkel tip
    drawCircle(Color(0xFF80D8FF).copy(alpha = 0.65f * effAlpha), 3.dp.toPx(), Offset(tubeTopX + 3.dp.toPx(), tubeTopY - 10.dp.toPx()), style = Stroke(width = 1.dp.toPx()))
    drawCircle(Color(0xFF80D8FF).copy(alpha = 0.5f * effAlpha), 2.dp.toPx(), Offset(tubeTopX - 2.dp.toPx(), tubeTopY - 18.dp.toPx()), style = Stroke(width = 1.dp.toPx()))
}

/**
 * วาดไอคอนปิศาจสีม่วง 😈 (Evil)
 */
internal fun DrawScope.drawLooiPurpleDevilIcon(
    centerX: Float,
    centerY: Float,
    size: Float = 22.dp.toPx(),
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val purple = Color(0xFFAB47BC).copy(alpha = effAlpha)
    val black = Color.Black.copy(alpha = effAlpha)
    val r = size / 2f
    drawCircle(purple, r, Offset(centerX, centerY))
    val leftHorn = Path().apply {
        moveTo(centerX - r * 0.7f, centerY - r * 0.4f)
        lineTo(centerX - r * 1.05f, centerY - r * 1.2f)
        lineTo(centerX - r * 0.3f, centerY - r * 0.8f)
        close()
    }
    val rightHorn = Path().apply {
        moveTo(centerX + r * 0.7f, centerY - r * 0.4f)
        lineTo(centerX + r * 1.05f, centerY - r * 1.2f)
        lineTo(centerX + r * 0.3f, centerY - r * 0.8f)
        close()
    }
    drawPath(leftHorn, purple)
    drawPath(rightHorn, purple)

    drawCircle(black, 1.8.dp.toPx(), Offset(centerX - r * 0.35f, centerY - r * 0.1f))
    drawCircle(black, 1.8.dp.toPx(), Offset(centerX + r * 0.35f, centerY - r * 0.1f))
    drawArc(black, 0f, 180f, false, Offset(centerX - r * 0.45f, centerY + r * 0.05f), Size(r * 0.9f, r * 0.45f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
}

/**
 * วาดตาทรงลิ่มคมกริบ (Focused)
 */
internal fun DrawScope.drawLooiFocusdWedge(
    color: Color,
    shadowColor: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    isLeft: Boolean,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effColor = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f))
    val effShadowColor = shadowColor.copy(alpha = (shadowColor.alpha * alpha).coerceIn(0f, 1f))
    val effW = width * 0.95f
    val effH = height * 0.35f
    val shadowOffsetY = 4.dp.toPx()

    fun makeWedge(offsetY: Float) = Path().apply {
        if (isLeft) {
            moveTo(centerX - effW / 2f, centerY + effH / 2f + offsetY)
            lineTo(centerX + effW / 2f, centerY - effH / 2f + offsetY)
            lineTo(centerX + effW / 2f, centerY + effH / 2f + offsetY)
            close()
        } else {
            moveTo(centerX + effW / 2f, centerY + effH / 2f + offsetY)
            lineTo(centerX - effW / 2f, centerY - effH / 2f + offsetY)
            lineTo(centerX - effW / 2f, centerY + effH / 2f + offsetY)
            close()
        }
    }
    drawPath(makeWedge(shadowOffsetY), effShadowColor)
    drawPath(makeWedge(0f), effColor)
}

/**
 * วาดตารางเลเซอร์ Synthwave Perspective Grid (Focused)
 */
internal fun DrawScope.drawLooiSynthwaveGrid(
    centerX: Float,
    horizonY: Float,
    width: Float,
    depth: Float,
    alpha: Float = 1f,
    scanProgress: Float = 0f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val gridColor = Color(0xFF00F5FF)
    val bottomY = horizonY + depth
    val horizonW = width * 0.35f
    val bottomW = width * 1.25f

    val lineRatios = floatArrayOf(0.12f, 0.28f, 0.48f, 0.72f, 1.0f)
    for (ratio in lineRatios) {
        val y = horizonY + depth * ratio
        val w = horizonW + (bottomW - horizonW) * ratio
        // Distance to active scan wave
        val distToScan = kotlin.math.abs(ratio - scanProgress)
        val scanHighlight = (1f - distToScan * 2.8f).coerceIn(0f, 1f)
        val lAlpha = ((0.25f + ratio * 0.45f + scanHighlight * 0.30f).coerceIn(0f, 0.95f) * effAlpha).coerceIn(0f, 1f)
        val lWidth = (1.5f + ratio * 1.5f + scanHighlight * 1.2f).dp.toPx()
        drawLine(
            color = if (scanHighlight > 0.35f) Color(0xFF80DEEA).copy(alpha = lAlpha) else gridColor.copy(alpha = lAlpha),
            start = Offset(centerX - w / 2f, y),
            end = Offset(centerX + w / 2f, y),
            strokeWidth = lWidth
        )
    }

    val vanishingCount = 7
    for (i in 0 until vanishingCount) {
        val frac = i.toFloat() / (vanishingCount - 1).toFloat()
        val startX = (centerX - horizonW / 2f) + horizonW * frac
        val endX = (centerX - bottomW / 2f) + bottomW * frac
        drawLine(
            color = gridColor.copy(alpha = 0.6f * effAlpha),
            start = Offset(startX, horizonY),
            end = Offset(endX, bottomY),
            strokeWidth = 1.8.dp.toPx()
        )
    }
}

/**
 * วาดตาทรงครึ่งวงรี/วงกลมหดเป็นครึ่งวงกลม (Excited: เริ่มจากตาวงกลมสีเหลือง แล้วหดเป็นครึ่งวงกลม)
 */
internal fun DrawScope.drawLooiExcitedEye(
    color: Color,
    shadowColor: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    isLeft: Boolean,
    shrinkProgress: Float = 1f,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effColor = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f))
    val effShadowColor = shadowColor.copy(alpha = (shadowColor.alpha * alpha).coerceIn(0f, 1f))
    val tilt = (if (isLeft) -16f else 16f) * shrinkProgress
    rotate(degrees = tilt, pivot = Offset(centerX, centerY)) {
        val effW = width * 0.90f
        val effH = height * (0.90f - 0.22f * shrinkProgress)
        val shadowOffsetY = 5.dp.toPx()

        fun makeEyePath(offsetY: Float) = Path().apply {
            val hw = effW / 2f
            val hh = effH / 2f
            val cy = centerY + offsetY
            if (shrinkProgress <= 0.02f) {
                // Full round oval / circle
                addOval(Rect(centerX - hw, cy - hh, centerX + hw, cy + hh))
            } else {
                val bottomFlatten = shrinkProgress.coerceIn(0f, 1f)
                moveTo(centerX - hw, cy)
                // Top half curve (convex dome arc)
                cubicTo(
                    centerX - hw, cy - hh * 1.18f,
                    centerX + hw, cy - hh * 1.18f,
                    centerX + hw, cy
                )
                // Bottom half: flattens from round curve to flat horizontal line
                if (bottomFlatten >= 0.92f) {
                    lineTo(centerX - hw, cy)
                } else {
                    val bottomControlY = cy + hh * (1.18f * (1f - bottomFlatten))
                    cubicTo(
                        centerX + hw, bottomControlY,
                        centerX - hw, bottomControlY,
                        centerX - hw, cy
                    )
                }
                close()
            }
        }

        drawPath(makeEyePath(shadowOffsetY), effShadowColor)
        drawPath(makeEyePath(0f), effColor)
    }
}

/**
 * วาดแก้มชมพูระเรื่อ /// (Shy)
 */
internal fun DrawScope.drawLooiPinkBlush(
    centerX: Float,
    centerY: Float,
    width: Float = 28.dp.toPx(),
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val blushColor = Color(0xFFFF80AB).copy(alpha = effAlpha)
    val strokeW = 3.dp.toPx()
    val spacing = width / 3f
    val len = 8.dp.toPx()

    for (i in -1..1) {
        val x = centerX + i * spacing
        drawLine(
            color = blushColor,
            start = Offset(x - len * 0.5f, centerY - len * 0.5f),
            end = Offset(x + len * 0.5f, centerY + len * 0.5f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

/**
 * วาดเครื่องหมายตกใจสีแดง ! (Shock)
 */
internal fun DrawScope.drawLooiExclamationMark(
    centerX: Float,
    centerY: Float,
    height: Float = 28.dp.toPx(),
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val red = Color(0xFFFF1744).copy(alpha = effAlpha)
    val barW = maxOf(5.dp.toPx(), height * 0.18f)
    val barH = height * 0.65f
    val dotRadius = maxOf(3.dp.toPx(), height * 0.11f)

    drawRoundRect(
        color = red,
        topLeft = Offset(centerX - barW / 2f, centerY - height / 2f),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
    )
    drawCircle(red, dotRadius, Offset(centerX, centerY + height / 2f - dotRadius))
}

/**
 * วาดถังขยะเปิดฝาด้านล่าง (Disgusted)
 */
internal fun DrawScope.drawLooiTrashBin(
    centerX: Float,
    centerY: Float,
    width: Float = 34.dp.toPx(),
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val silver = Color(0xFFCFD8DC).copy(alpha = effAlpha)
    val darkSilver = Color(0xFF78909C).copy(alpha = effAlpha)
    val h = width * 1.15f

    val binPath = Path().apply {
        moveTo(centerX - width * 0.45f, centerY)
        lineTo(centerX + width * 0.45f, centerY)
        lineTo(centerX + width * 0.35f, centerY + h)
        lineTo(centerX - width * 0.35f, centerY + h)
        close()
    }
    drawPath(binPath, silver)
    drawLine(darkSilver, Offset(centerX - width * 0.18f, centerY + 4.dp.toPx()), Offset(centerX - width * 0.15f, centerY + h - 4.dp.toPx()), 2.dp.toPx())
    drawLine(darkSilver, Offset(centerX, centerY + 4.dp.toPx()), Offset(centerX, centerY + h - 4.dp.toPx()), 2.dp.toPx())
    drawLine(darkSilver, Offset(centerX + width * 0.18f, centerY + 4.dp.toPx()), Offset(centerX + width * 0.15f, centerY + h - 4.dp.toPx()), 2.dp.toPx())

    rotate(degrees = -22f, pivot = Offset(centerX - width * 0.3f, centerY - 6.dp.toPx())) {
        drawRoundRect(
            color = silver,
            topLeft = Offset(centerX - width * 0.55f, centerY - 10.dp.toPx()),
            size = Size(width * 1.1f, 6.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        drawArc(
            color = darkSilver,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(centerX - 5.dp.toPx(), centerY - 16.dp.toPx()),
            size = Size(10.dp.toPx(), 8.dp.toPx()),
            style = Stroke(2.dp.toPx())
        )
    }
}

/**
 * วาดไอคอนถังขยะสีฟ้า 🗑️ (Disgusted)
 */
internal fun DrawScope.drawLooiTrashIcon(
    centerX: Float,
    centerY: Float,
    size: Float = 22.dp.toPx(),
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val cyan = Color(0xFF00F5FF).copy(alpha = effAlpha)
    val w = size * 0.75f
    val h = size * 0.85f

    drawRoundRect(cyan, Offset(centerX - w * 0.6f, centerY - h / 2f), Size(w * 1.2f, 3.dp.toPx()), CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()))
    drawRoundRect(cyan, Offset(centerX - w / 2f, centerY - h / 2f + 4.dp.toPx()), Size(w, h - 4.dp.toPx()), CornerRadius(3.dp.toPx(), 3.dp.toPx()), style = Stroke(2.dp.toPx()))
    drawLine(cyan, Offset(centerX - w * 0.2f, centerY - h * 0.15f), Offset(centerX - w * 0.2f, centerY + h * 0.3f), 1.5.dp.toPx())
    drawLine(cyan, Offset(centerX + w * 0.2f, centerY - h * 0.15f), Offset(centerX + w * 0.2f, centerY + h * 0.3f), 1.5.dp.toPx())
}

/**
 * วาดไอคอนกล้องถ่ายรูป 📷 (Camera Mode)
 */
internal fun DrawScope.drawLooiCameraIcon(
    centerX: Float,
    centerY: Float,
    size: Float = 26.dp.toPx(),
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val orange = Color(0xFFFF9800).copy(alpha = effAlpha)
    val black = Color.Black.copy(alpha = effAlpha)
    val white = Color.White.copy(alpha = 0.8f * effAlpha)
    val w = size
    val h = size * 0.72f

    drawRoundRect(
        color = orange,
        topLeft = Offset(centerX - w / 2f, centerY - h / 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
    drawRoundRect(
        color = orange,
        topLeft = Offset(centerX - w * 0.25f, centerY - h / 2f - 3.dp.toPx()),
        size = Size(w * 0.5f, 4.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )
    drawCircle(black, h * 0.32f, Offset(centerX, centerY + 1.dp.toPx()))
    drawCircle(white, h * 0.18f, Offset(centerX, centerY + 1.dp.toPx()), style = Stroke(2.dp.toPx()))
}

/**
 * วาดปากจู๋ (Pucker 'O' Mouth) น่ารัก สำหรับอารมณ์ Pout หรือเป่าลม
 */
internal fun DrawScope.drawPuckerMouth(
    color: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effColor = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f))
    drawCircle(
        color = effColor,
        radius = radius,
        center = Offset(centerX, centerY),
        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
    )
}

/**
 * วาดคลื่นเสียงไมค์ 5 แท่ง (5-Bar Waveform Equalizer) เวลาหุ่นยนต์กำลังพูด
 */
internal fun DrawScope.drawWaveformMouth(
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
            color = color.copy(alpha = 0.35f * color.alpha),
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

internal fun DrawScope.drawGlowCircle(
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

internal fun DrawScope.drawSmileArc(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    drawNeonArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        topLeft = Offset(centerX - width / 2f, centerY - height / 2f),
        size = Size(width, height),
        strokeWidth = 6f,
        glowRadiusDp = 7.dp,
        alpha = alpha
    )
}

internal fun DrawScope.drawFrownArc(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    drawNeonArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        topLeft = Offset(centerX - width / 2f, centerY - height / 2f),
        size = Size(width, height),
        strokeWidth = 5.5f,
        glowRadiusDp = 7.dp,
        alpha = alpha
    )
}

internal fun DrawScope.drawHeart(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float,
    alpha: Float = 1f
) {
    drawNeonHeart(color, centerX, centerY, size, glowRadiusDp = 8.dp, alpha = alpha)
}

internal fun DrawScope.drawQuestionMark(
    color: Color,
    centerX: Float,
    centerY: Float,
    height: Float,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    val effColor = color.copy(alpha = effAlpha)
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
    drawNeonPath(qPath, effColor, strokeWidth = 6f, glowRadiusDp = 7.dp, alpha = effAlpha)
    drawNeonCircle(effColor, center = Offset(centerX, centerY + height * 0.22f), radius = 3.5f, glowRadiusDp = 6.dp, alpha = effAlpha)
}

internal fun DrawScope.drawZLetter(
    color: Color,
    x: Float,
    y: Float,
    size: Float
) {
    drawNeonZzz(color, x, y, size)
}

internal fun DrawScope.drawThinkingHand(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    val effColor = color.copy(alpha = effAlpha)
    val handPath = Path().apply {
        val r = size * 0.4f
        moveTo(centerX + r * 0.3f, centerY - r * 1.1f)
        lineTo(centerX + r * 0.3f, centerY - r * 0.2f)
        arcTo(
            rect = Rect(centerX - r, centerY - r * 0.5f, centerX + r * 0.8f, centerY + r * 0.8f),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 200f,
            forceMoveTo = false
        )
    }
    drawNeonPath(handPath, effColor, strokeWidth = 5.5f, glowRadiusDp = 7.dp, alpha = effAlpha)
}

internal fun DrawScope.drawSpiralEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    rotationDeg: Float,
    alpha: Float = 1f
) {
    drawNeonSwirl(color, centerX, centerY, radius, rotationDeg, strokeWidth = 5.5f, glowRadiusDp = 8.dp, alpha = alpha)
}

internal fun DrawScope.drawEnragedEye(
    color: Color,
    backColor: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    isLeft: Boolean,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effColor = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f))
    val effBackColor = backColor.copy(alpha = (backColor.alpha * alpha).coerceIn(0f, 1f))
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

    drawPath(shadowPath, effBackColor)
    drawPath(path, effColor)

    // Inner glowing slit pupil
    val pupilW = effW * 0.22f
    val pupilH = effH * 0.70f
    drawOval(
        color = Color(0xFFFFEB3B).copy(alpha = effColor.alpha),
        topLeft = Offset(centerX - pupilW / 2f, centerY - pupilH / 2f),
        size = Size(pupilW, pupilH)
    )
}

internal fun DrawScope.drawSteamPuff(
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

/**
 * วาดตัวโน้ตดนตรีเดี่ยว ♪ หรือคู่ ♫ สไตล์นีออนสำหรับอารมณ์ MUSIC
 */
internal fun DrawScope.drawNeonMusicNote(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float = 18.dp.toPx(),
    isDoubleNote: Boolean = false,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val noteColor = color.copy(alpha = effAlpha)
    val headR = size * 0.22f
    val stemH = size * 0.85f
    val strokeW = 2.5.dp.toPx()

    if (!isDoubleNote) {
        // Single note ♪
        rotate(-20f, pivot = Offset(centerX, centerY + headR)) {
            drawOval(
                color = noteColor,
                topLeft = Offset(centerX - headR * 1.2f, centerY),
                size = Size(headR * 2.4f, headR * 1.6f)
            )
        }
        val stemX = centerX + headR * 0.9f
        drawLine(
            color = noteColor,
            start = Offset(stemX, centerY + headR * 0.5f),
            end = Offset(stemX, centerY - stemH),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        // Flag curve
        val flagPath = Path().apply {
            moveTo(stemX, centerY - stemH)
            cubicTo(
                stemX + size * 0.45f, centerY - stemH * 0.85f,
                stemX + size * 0.40f, centerY - stemH * 0.45f,
                stemX + size * 0.35f, centerY - stemH * 0.20f
            )
        }
        drawPath(flagPath, noteColor, style = Stroke(width = strokeW, cap = StrokeCap.Round))
    } else {
        // Beamed double note ♫
        val noteSpacing = size * 0.85f
        for (i in 0..1) {
            val hx = centerX - noteSpacing * 0.5f + i * noteSpacing
            rotate(-20f, pivot = Offset(hx, centerY + headR)) {
                drawOval(
                    color = noteColor,
                    topLeft = Offset(hx - headR * 1.2f, centerY),
                    size = Size(headR * 2.4f, headR * 1.6f)
                )
            }
            val stemX = hx + headR * 0.9f
            drawLine(
                color = noteColor,
                start = Offset(stemX, centerY + headR * 0.5f),
                end = Offset(stemX, centerY - stemH),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
        val stem1X = centerX - noteSpacing * 0.5f + headR * 0.9f
        val stem2X = centerX + noteSpacing * 0.5f + headR * 0.9f
        drawLine(
            color = noteColor,
            start = Offset(stem1X - 1.dp.toPx(), centerY - stemH),
            end = Offset(stem2X + 1.dp.toPx(), centerY - stemH + 2.dp.toPx()),
            strokeWidth = strokeW * 1.8f,
            cap = StrokeCap.Square
        )
    }
}

/**
 * วาดเลนส์กล้องพร้อมกลีบรูรับแสง (Aperture Iris Blades) และแสงแฟลช สำหรับ CAMERA_MODE
 */
internal fun DrawScope.drawCameraLensEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    irisRotationDeg: Float = 0f,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val cyanColor = color.copy(alpha = effAlpha)
    val r = minOf(width, height) * 0.45f

    // Outer lens barrel ring
    drawCircle(cyanColor, radius = r, center = Offset(centerX, centerY), style = Stroke(width = 3.5.dp.toPx()))
    // Inner lens rim
    drawCircle(cyanColor.copy(alpha = 0.45f * effAlpha), radius = r * 0.82f, center = Offset(centerX, centerY), style = Stroke(width = 1.5.dp.toPx()))

    // Rotating aperture blades (6 blades)
    rotate(irisRotationDeg, pivot = Offset(centerX, centerY)) {
        for (i in 0 until 6) {
            val angle = i * 60f * (PI.toFloat() / 180f)
            val bx = centerX + cos(angle) * (r * 0.78f)
            val by = centerY + sin(angle) * (r * 0.78f)
            val tx = centerX + cos(angle + 0.85f) * (r * 0.32f)
            val ty = centerY + sin(angle + 0.85f) * (r * 0.32f)
            drawLine(
                color = cyanColor.copy(alpha = 0.85f * effAlpha),
                start = Offset(bx, by),
                end = Offset(tx, ty),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
    // Center aperture opening
    drawCircle(Color.White.copy(alpha = 0.9f * effAlpha), radius = r * 0.20f, center = Offset(centerX, centerY))
    // Flash reflection glint
    drawCircle(Color.White.copy(alpha = 0.7f * effAlpha), radius = r * 0.08f, center = Offset(centerX - r * 0.35f, centerY - r * 0.35f))
}

/**
 * วาดเส้นเล็งกล้อง Viewfinder reticle [  ] สำหรับ CAMERA_MODE
 */
internal fun DrawScope.drawCameraViewfinderEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    alpha: Float = 1f
) {
    if (alpha <= 0.01f) return
    val effAlpha = alpha.coerceIn(0f, 1f)
    val cyanColor = color.copy(alpha = effAlpha)
    val hw = width * 0.44f
    val hh = height * 0.44f
    val bracketLen = hw * 0.35f
    val strokeW = 3.dp.toPx()

    // 4 Viewfinder corner brackets: [  ]
    // Top-Left corner
    drawLine(cyanColor, Offset(centerX - hw, centerY - hh + bracketLen), Offset(centerX - hw, centerY - hh), strokeW, StrokeCap.Round)
    drawLine(cyanColor, Offset(centerX - hw, centerY - hh), Offset(centerX - hw + bracketLen, centerY - hh), strokeW, StrokeCap.Round)

    // Top-Right corner
    drawLine(cyanColor, Offset(centerX + hw - bracketLen, centerY - hh), Offset(centerX + hw, centerY - hh), strokeW, StrokeCap.Round)
    drawLine(cyanColor, Offset(centerX + hw, centerY - hh), Offset(centerX + hw, centerY - hh + bracketLen), strokeW, StrokeCap.Round)

    // Bottom-Left corner
    drawLine(cyanColor, Offset(centerX - hw, centerY + hh - bracketLen), Offset(centerX - hw, centerY + hh), strokeW, StrokeCap.Round)
    drawLine(cyanColor, Offset(centerX - hw, centerY + hh), Offset(centerX - hw + bracketLen, centerY + hh), strokeW, StrokeCap.Round)

    // Bottom-Right corner
    drawLine(cyanColor, Offset(centerX + hw - bracketLen, centerY + hh), Offset(centerX + hw, centerY + hh), strokeW, StrokeCap.Round)
    drawLine(cyanColor, Offset(centerX + hw, centerY + hh), Offset(centerX + hw, centerY + hh - bracketLen), strokeW, StrokeCap.Round)

    // Center Crosshair reticle +
    val chLen = 6.dp.toPx()
    drawLine(cyanColor, Offset(centerX - chLen, centerY), Offset(centerX + chLen, centerY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    drawLine(cyanColor, Offset(centerX, centerY - chLen), Offset(centerX, centerY + chLen), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
}


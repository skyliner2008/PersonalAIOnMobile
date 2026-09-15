package com.skyliner2008.jarvis.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skyliner2008.jarvis.ui.component.avatar.AvatarLayoutInfo
import com.skyliner2008.jarvis.ui.component.avatar.GazeStabilizer
import com.skyliner2008.jarvis.ui.component.avatar.LocalAvatarLayout
import com.skyliner2008.jarvis.ui.component.avatar.calculateAvatarLayout
import com.skyliner2008.jarvis.camera.BoundingBoxOverlay
import com.skyliner2008.jarvis.camera.CameraPreviewView
import com.skyliner2008.jarvis.camera.DetectedObject
import com.skyliner2008.jarvis.camera.ObjectLabelTags
import com.skyliner2008.jarvis.camera.ScanLineEffect
import com.skyliner2008.jarvis.pet.AlwaysLiveProfile
import com.skyliner2008.jarvis.pet.HoloHandGesture
import com.skyliner2008.jarvis.pet.PetMemoryStore
import com.skyliner2008.jarvis.pet.PetModeController
import com.skyliner2008.jarvis.pet.PetNeedsDashboardContent
import com.skyliner2008.jarvis.pet.PetNeedsSidebarPanel
import com.skyliner2008.jarvis.pet.PetSettingsDialog
import com.skyliner2008.jarvis.pet.PetVisionProcessor
import com.skyliner2008.jarvis.pet.TouchZone
import com.skyliner2008.jarvis.sound.RobotSoundPlayer
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEngineType
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import com.skyliner2008.jarvis.ui.component.avatar.HolographicHandOverlay
import com.skyliner2008.jarvis.ui.component.avatar.MissileBarrageOverlay
import com.skyliner2008.jarvis.ui.component.avatar.PetBackgroundLayer
import com.skyliner2008.jarvis.ui.component.avatar.PetForegroundLayer
import com.skyliner2008.jarvis.ui.component.avatar.PetPropsOverlay
import com.skyliner2008.jarvis.ui.component.avatar.PetRobotHeadAvatar
import com.skyliner2008.jarvis.ui.component.avatar.RiveAvatarMapper
import com.skyliner2008.jarvis.ui.component.avatar.RiveReactionEvent
import com.skyliner2008.jarvis.ui.component.avatar.RiveRuntime
import com.skyliner2008.jarvis.ui.component.avatar.RiveSeq
import com.skyliner2008.jarvis.ui.component.avatar.rememberGestureModifier
import com.skyliner2008.jarvis.ui.theme.JarvisTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.math.absoluteValue

/**
 * PetModeContent — Virtual Desk Pet UI
 *
 * Fullscreen living robot head avatar with touch interaction zones,
 * eye scanner overlay, holographic hand gestures, cartoon missile barrage,
 * adaptive dialogue cards, and Tamagotchi needs dashboard.
 */
@Composable
internal fun PetModeContent(
    activeAvatarState: AvatarState,
    onUpdateActiveAvatarState: (AvatarState) -> Unit,
    petController: PetModeController,
    petVisionProcessor: PetVisionProcessor?,
    auraColor: Color,
    isLandscape: Boolean,
    screenMaxWidth: Dp,
    screenMaxHeight: Dp,
    activeToolName: String?,
    lastToolResult: Pair<String, String>?,
    onDismissToolCard: (() -> Unit)?,
    isDemoRunning: Boolean,
    onToggleDemo: (() -> Unit)?,
    isMuted: Boolean,
    detectedObjects: List<DetectedObject>,
    isCameraPipOpen: Boolean,
    onCloseCameraPip: () -> Unit,
    onToggleCameraPip: () -> Unit,
    isFrontCam: Boolean,
    onSwitchCamera: () -> Unit,
    lastLiveFrameSendTime: Long,
    onUpdateLastLiveFrameSendTime: (Long) -> Unit,
    onLiveVideoFrame: ((String) -> Unit)?,
    onSelectProfile: ((AlwaysLiveProfile) -> Unit)?,
    onEndLive: () -> Unit,
    currentTick: Long,
    lastFaceCoords: Pair<Float, Float>?,
    lastFaceTime: Long,
    lastShakeTime: Long,
    isMissileBarrageActive: Boolean,
    onMissileBarrageFinished: () -> Unit
) {
    var lastTouchAction by remember { mutableStateOf<String?>(null) }
    var lastTouchTime by remember { mutableStateOf(0L) }
    var showDebugHud by remember { mutableStateOf(false) }
    var isPetSettingsOpen by remember { mutableStateOf(false) }
    var isPetSidebarOpen by remember { mutableStateOf(false) }
    var holoGesture by remember { mutableStateOf<HoloHandGesture?>(null) }
    var holoPosition by remember { mutableStateOf(Offset.Zero) }
    var holoFromLeft by remember { mutableStateOf(false) }
    var holoTriggerId by remember { mutableStateOf(0L) }
    var screenShakeIntensity by remember { mutableStateOf(0f) }
    var avatarEngineType by remember {
        mutableStateOf(
            PetMemoryStore.loadAvatarEngine()
                ?.let { saved -> AvatarEngineType.entries.firstOrNull { it.name == saved } }
                ?: AvatarEngineType.COMPOSE_CANVAS
        )
    }

    // ─── Rive engine: same conditions as the Canvas pet ───────────────────
    // Rive draws the face and every prop / scene it has; the Compose layers keep
    // drawing whatever Rive has no equivalent for, so switching engines never
    // loses an AI-requested prop or background.
    val useRive = avatarEngineType == AvatarEngineType.RIVE_STATE_MACHINE && RiveRuntime.isActive
    val rivePlan = if (useRive) RiveAvatarMapper.plan(activeAvatarState) else null
    var riveReaction by remember { mutableStateOf<RiveReactionEvent?>(null) }

    // Touches raise a holographic hand; on Rive that becomes the `react` channel
    LaunchedEffect(holoTriggerId, useRive) {
        val gesture = holoGesture
        if (useRive && gesture != null && holoTriggerId != 0L) {
            riveReaction = RiveReactionEvent(gesture, holoFromLeft, holoTriggerId)
            holoGesture = null   // Rive plays its own hand; don't leave a Canvas one pending
        }
    }

    // Fight-back barrage: Rive plays the AngryMissile story instead of the overlay
    LaunchedEffect(isMissileBarrageActive, useRive) {
        if (useRive && isMissileBarrageActive) {
            delay(RiveSeq.ANGRY_MISSILE_MS)
            onMissileBarrageFinished()
        }
    }

    val isToolActive = activeToolName != null && activeToolName !in setOf(
        "device_avatar_emotion",
        "device_custom_prop",
        "device_pet_care",
        "device_always_live",
        "vision_activate"
    )
    val toolResultText = lastToolResult?.second

    val petDialogueText: String? = when {
        isToolActive -> {
            when {
                activeToolName == "search_web" -> "🔍 กำลังค้นหาข้อมูลจากอินเทอร์เน็ต..."
                activeToolName == "device_location" -> "📍 กำลังระบุพิกัด GPS และสถานที่ใกล้เคียง..."
                activeToolName == "device_weather" -> "🌤️ กำลังตรวจเช็คสภาพอากาศและพยากรณ์ล่วงหน้า..."
                activeToolName?.startsWith("trading_") == true -> "📊 กำลังวิเคราะห์ข้อมูลตลาด ($activeToolName)..."
                activeToolName?.startsWith("device_") == true -> "📱 กำลังดำเนินการบนอุปกรณ์ ($activeToolName)..."
                else -> "⚙️ กำลังประมวลผลเครื่องมือ ($activeToolName)..."
            }
        }
        !toolResultText.isNullOrBlank() -> toolResultText
        isDemoRunning && !activeAvatarState.faceState.speechText.isNullOrBlank() -> activeAvatarState.faceState.speechText
        else -> null
    }
    val hasMessage = !petDialogueText.isNullOrBlank()

    // ─── Auto-Dismiss Dialogue Card 10s After AI Finishes Speaking ───
    LaunchedEffect(activeAvatarState.isSpeaking, petDialogueText, lastTouchTime) {
        if (!petDialogueText.isNullOrBlank() && !activeAvatarState.isSpeaking) {
            delay(10_000L)
            onDismissToolCard?.invoke()
            onUpdateActiveAvatarState(
                activeAvatarState.copy(
                    statusText = null,
                    faceState = activeAvatarState.faceState.copy(speechText = null)
                )
            )
        }
    }

    // ─── Auto-Decay Contextual Props & Dynamic Background After 12s ───
    val currentProps = activeAvatarState.faceState.props
    LaunchedEffect(currentProps, isDemoRunning) {
        if (currentProps.isNotEmpty() && !isDemoRunning) {
            delay(12_000L)
            onUpdateActiveAvatarState(
                activeAvatarState.copy(
                    faceState = activeAvatarState.faceState.copy(
                        propsRaw = "",
                        backgroundName = "default"
                    )
                )
            )
        }
    }

    val targetFaceOffsetX = if (hasMessage) {
        if (isLandscape) -0.24f else 0f
    } else 0f

    val targetFaceOffsetY = 0f

    val targetFaceScale = if (hasMessage) {
        if (isLandscape) 0.82f else 1.0f
    } else 1.0f

    val animatedFaceOffsetX by animateFloatAsState(
        targetValue = targetFaceOffsetX,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "petFaceOffsetX"
    )
    val animatedFaceOffsetY by animateFloatAsState(
        targetValue = targetFaceOffsetY,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "petFaceOffsetY"
    )
    val animatedFaceScale by animateFloatAsState(
        targetValue = targetFaceScale,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow),
        label = "petFaceScale"
    )

    val petFaceContent = @Composable { faceModifier: Modifier ->
        BoxWithConstraints(modifier = faceModifier) {
            val density = LocalDensity.current
            val padDp = if (isLandscape) 12.dp else 24.dp
            val areaWidth = maxWidth
            val areaHeight = maxHeight
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val padPx = with(density) { padDp.toPx() } * 2f
            val faceWidthPx = (widthPx - padPx).coerceAtLeast(0f)
            val faceHeightPx = (heightPx - padPx).coerceAtLeast(0f)

            val layoutInfo = remember(faceWidthPx, faceHeightPx, isLandscape) {
                calculateAvatarLayout(faceWidthPx, faceHeightPx, isLandscape)
            }

            // Screen-level gaze stabilizer for 2.5D Parallax & Head-aligned Props
            val screenGazeStabilizer = remember { GazeStabilizer() }
            val (screenFilteredGazeX, screenFilteredGazeY) = remember(
                activeAvatarState.gazeOffsetX,
                activeAvatarState.gazeOffsetY
            ) {
                screenGazeStabilizer.update(activeAvatarState.gazeOffsetX, activeAvatarState.gazeOffsetY)
            }

            val screenSmoothedGazeX by animateFloatAsState(
                targetValue = screenFilteredGazeX,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                label = "screen_smoothed_gaze_x"
            )
            val screenSmoothedGazeY by animateFloatAsState(
                targetValue = screenFilteredGazeY,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                label = "screen_smoothed_gaze_y"
            )

            val targetRotationY = screenSmoothedGazeX.coerceIn(-1f, 1f) * 35f
            val targetRotationX = -screenSmoothedGazeY.coerceIn(-1f, 1f) * 20f
            val screenAnimatedRotationY by animateFloatAsState(
                targetValue = targetRotationY,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "screen_3d_yaw"
            )
            val screenAnimatedRotationX by animateFloatAsState(
                targetValue = targetRotationX,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "screen_3d_pitch"
            )

            CompositionLocalProvider(LocalAvatarLayout provides layoutInfo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                .pointerInput(isLandscape, animatedFaceOffsetX, animatedFaceOffsetY, animatedFaceScale) {
                    coroutineScope {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val boxW = size.width.toFloat()
                            val boxH = size.height.toFloat()
                            val headAspect = 1.32f
                            val maxW = boxW * 0.88f
                            val maxH = boxH * 0.88f
                            val (headW, headH) = if (maxW / maxH > headAspect) {
                                Pair(maxH * headAspect, maxH)
                            } else {
                                Pair(maxW, maxW / headAspect)
                            }
                            val cX = boxW / 2f + animatedFaceOffsetX * boxW
                            val cY = boxH / 2f + animatedFaceOffsetY * boxH

                            val normX = (down.position.x - cX) / (headW * 0.5f * animatedFaceScale)
                            val normY = (down.position.y - cY) / (headH * 0.5f * animatedFaceScale)

                            lastTouchAction = "🐾 ลากสายตา (Gaze)"
                            lastTouchTime = Clock.System.now().toEpochMilliseconds()
                            petController.onGazeTouch(normX, normY)

                            var totalDragY = 0f
                            var totalDragX = 0f
                            var isDrag = false

                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    val curNormX = (change.position.x - cX) / (headW * 0.5f * animatedFaceScale)
                                    val curNormY = (change.position.y - cY) / (headH * 0.5f * animatedFaceScale)
                                    petController.onGazeTouch(curNormX, curNormY)
                                    val delta = change.positionChange()
                                    totalDragX += delta.x
                                    totalDragY += delta.y
                                    if (totalDragX.absoluteValue > 18f || totalDragY.absoluteValue > 18f) {
                                        isDrag = true
                                        lastTouchAction = "🐾 ลากสายตา (Gaze)"
                                        lastTouchTime = Clock.System.now().toEpochMilliseconds()
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            // Reset gaze to center smoothly
                            petController.onGazeTouch(0f, 0f)

                            if (isDrag) {
                                if (normY < -0.28f && totalDragY > 15f) {
                                    lastTouchAction = "🐾 ลูบหน้าผาก (Pet Head)"
                                    lastTouchTime = Clock.System.now().toEpochMilliseconds()
                                    holoGesture = HoloHandGesture.STROKE
                                    holoPosition = down.position
                                    holoFromLeft = normX < 0
                                    holoTriggerId = Clock.System.now().toEpochMilliseconds()
                                    petController.onPetHead(TouchZone.FOREHEAD)
                                } else if (normY > 0.32f && totalDragY < -15f) {
                                    lastTouchAction = "🐾 เกาคาง (Chin Scratch)"
                                    lastTouchTime = Clock.System.now().toEpochMilliseconds()
                                    holoGesture = HoloHandGesture.CHIN_SCRATCH
                                    holoPosition = down.position
                                    holoTriggerId = Clock.System.now().toEpochMilliseconds()
                                    petController.onChinScratch()
                                } else if (normX > 0.60f && totalDragX < -30f) {
                                    isPetSidebarOpen = true
                                }
                            }
                        }
                    }
                }
                .pointerInput(isLandscape, animatedFaceOffsetX, animatedFaceOffsetY, animatedFaceScale) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val boxW = size.width.toFloat()
                            val boxH = size.height.toFloat()
                            val headAspect = 1.32f
                            val (headW, headH) = if ((boxW * 0.88f) / (boxH * 0.88f) > headAspect) {
                                Pair(boxH * 0.88f * headAspect, boxH * 0.88f)
                            } else {
                                Pair(boxW * 0.88f, (boxW * 0.88f) / headAspect)
                            }
                            val cX = boxW / 2f + animatedFaceOffsetX * boxW
                            val cY = boxH / 2f + animatedFaceOffsetY * boxH
                            val normX = (offset.x - cX) / (headW * 0.5f * animatedFaceScale)
                            if (normX.absoluteValue > 0.40f) {
                                lastTouchAction = "🐾 จั๊กจี้แก้ม (Tickle)"
                                lastTouchTime = Clock.System.now().toEpochMilliseconds()
                                holoGesture = HoloHandGesture.TICKLE
                                holoPosition = offset
                                holoTriggerId = Clock.System.now().toEpochMilliseconds()
                                val zone = if (normX < 0) TouchZone.LEFT_CHEEK else TouchZone.RIGHT_CHEEK
                                petController.onTickle(zone)
                            } else {
                                lastTouchAction = "🐾 แตะหัว 2 ครั้ง (Pet Head)"
                                lastTouchTime = Clock.System.now().toEpochMilliseconds()
                                holoGesture = HoloHandGesture.PAT
                                holoPosition = offset
                                holoTriggerId = Clock.System.now().toEpochMilliseconds()
                                petController.onPetHead(TouchZone.FOREHEAD)
                            }
                        },
                        onTap = { offset ->
                            val boxW = size.width.toFloat()
                            val boxH = size.height.toFloat()
                            val headAspect = 1.32f
                            val (headW, headH) = if ((boxW * 0.88f) / (boxH * 0.88f) > headAspect) {
                                Pair(boxH * 0.88f * headAspect, boxH * 0.88f)
                            } else {
                                Pair(boxW * 0.88f, (boxW * 0.88f) / headAspect)
                            }
                            val cX = boxW / 2f + animatedFaceOffsetX * boxW
                            val cY = boxH / 2f + animatedFaceOffsetY * boxH
                            val normX = (offset.x - cX) / (headW * 0.5f * animatedFaceScale)
                            val normY = (offset.y - cY) / (headH * 0.5f * animatedFaceScale)
                            if (hasMessage) {
                                onDismissToolCard?.invoke()
                                onUpdateActiveAvatarState(
                                    activeAvatarState.copy(
                                        statusText = null,
                                        faceState = activeAvatarState.faceState.copy(speechText = null)
                                    )
                                )
                            }
                            if (normX.absoluteValue > 0.40f) {
                                lastTouchAction = "🐾 จิ้มแก้ม (Poke)"
                                lastTouchTime = Clock.System.now().toEpochMilliseconds()
                                holoGesture = HoloHandGesture.POKE
                                holoPosition = offset
                                holoFromLeft = normX < 0
                                holoTriggerId = Clock.System.now().toEpochMilliseconds()
                                val zone = if (normX < 0) TouchZone.LEFT_CHEEK else TouchZone.RIGHT_CHEEK
                                petController.onPoke(zone)
                            } else if (normY < -0.30f) {
                                lastTouchAction = "🐾 ลูบหัว (Pet Head)"
                                lastTouchTime = Clock.System.now().toEpochMilliseconds()
                                holoGesture = HoloHandGesture.STROKE
                                holoPosition = offset
                                holoFromLeft = normX < 0
                                holoTriggerId = Clock.System.now().toEpochMilliseconds()
                                petController.onPetHead(TouchZone.FOREHEAD)
                            } else if (normY > 0.32f) {
                                lastTouchAction = "🐾 เกาคาง (Chin Scratch)"
                                lastTouchTime = Clock.System.now().toEpochMilliseconds()
                                holoGesture = HoloHandGesture.CHIN_SCRATCH
                                holoPosition = offset
                                holoTriggerId = Clock.System.now().toEpochMilliseconds()
                                petController.onChinScratch()
                            } else {
                                lastTouchAction = "🐾 แตะหน้าจอ (Tap)"
                                lastTouchTime = Clock.System.now().toEpochMilliseconds()
                                holoGesture = HoloHandGesture.PAT
                                holoPosition = offset
                                holoTriggerId = Clock.System.now().toEpochMilliseconds()
                                petController.onPat()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Layer 0: Dynamic Background Theme (2.5D Counter-Parallax)
            PetBackgroundLayer(
                theme = rivePlan?.composeBackground ?: activeAvatarState.faceState.backgroundTheme,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // overscan: the parallax shift must never uncover the layer's edge
                        // (that hard edge read as a box behind the pet)
                        scaleX = 1.12f
                        scaleY = 1.12f
                        translationX = -screenSmoothedGazeX * 14.dp.toPx()
                        translationY = -screenSmoothedGazeY * 10.dp.toPx()
                    }
            )

            // Dynamic screen shake offset during missile explosions
            val shakeX = if (screenShakeIntensity > 0.05f) {
                (kotlin.random.Random.nextFloat() * 2f - 1f) * screenShakeIntensity * 36f
            } else 0f
            val shakeY = if (screenShakeIntensity > 0.05f) {
                (kotlin.random.Random.nextFloat() * 2f - 1f) * screenShakeIntensity * 36f
            } else 0f

            // Face & Props Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = animatedFaceOffsetX * size.width + shakeX
                        translationY = animatedFaceOffsetY * size.height + shakeY
                        scaleX = animatedFaceScale
                        scaleY = animatedFaceScale
                    },
                contentAlignment = Alignment.Center
            ) {
                // Layer 1: Ambient Aura
                Box(
                    modifier = Modifier
                        // the aura sits behind the eyes, which the Rive layout moves up
                        .offset(y = if (useRive) areaHeight * ((if (isLandscape) RIVE_LANDSCAPE_EYE_LINE else RIVE_PORTRAIT_EYE_LINE) - 0.5f) else 0.dp)
                        .size(if (isLandscape) screenMaxHeight * 0.95f else screenMaxWidth * 0.95f)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    auraColor.copy(alpha = 0.22f),
                                    auraColor.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )

                // Layer 2: Robot Head Avatar
                val gestureModifier = rememberGestureModifier(activeAvatarState.faceState.gesture)
                // Rive: the 500x500 artboard is placed so its eye row lands on the upper
                // third of the pet area (portrait ~40% down, landscape ~42%) and fills more
                // of it -- no padding, and a little larger than the area (edges may crop).
                val riveSide = if (isLandscape) minOf(areaHeight * RIVE_LANDSCAPE_SCALE, areaWidth) else areaWidth * RIVE_PORTRAIT_SCALE
                val riveTop = areaHeight * (if (isLandscape) RIVE_LANDSCAPE_EYE_LINE else RIVE_PORTRAIT_EYE_LINE) - riveSide * RIVE_ARTBOARD_EYE_ROW
                val avatarModifier = if (useRive) {
                    Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.TopCenter, unbounded = true)
                        .offset(y = riveTop)
                        .requiredSize(riveSide)
                        .then(gestureModifier)
                } else {
                    Modifier
                        .fillMaxSize()
                        .then(gestureModifier)
                        .padding(if (isLandscape) 12.dp else 24.dp)
                }
                PetRobotHeadAvatar(
                    state = activeAvatarState,
                    modifier = avatarModifier,
                    engineType = avatarEngineType,
                    riveReaction = riveReaction,
                    isMissileBarrageActive = isMissileBarrageActive,
                    lastShakeTime = lastShakeTime,
                    lastFaceTime = lastFaceTime
                )

                // Layer 2.5: Eye Scanner & Cyber Radar Overlay
                PetEyeScannerOverlay(
                    isScanning = isCameraPipOpen,
                    isFrontCamera = isFrontCam,
                    detectedObjects = detectedObjects,
                    gazeX = activeAvatarState.gazeOffsetX,
                    gazeY = activeAvatarState.gazeOffsetY,
                    onFrameCapture = { jpegBase64, rawBytes ->
                        petVisionProcessor?.processFrame(rawBytes, isFrontCamera = isFrontCam)
                        val now = Clock.System.now().toEpochMilliseconds()
                        if (now - lastLiveFrameSendTime >= 900L) {
                            onUpdateLastLiveFrameSendTime(now)
                            onLiveVideoFrame?.invoke(jpegBase64)
                        }
                    },
                    onSwitchCamera = onSwitchCamera,
                    onClose = onCloseCameraPip,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(gestureModifier)
                        .padding(if (isLandscape) 12.dp else 24.dp)
                )

                // Layer 3: Props Overlay (2.5D Head-aligned 3D Rotation + Forward Parallax)
                PetPropsOverlay(
                    props = rivePlan?.composeProps ?: activeAvatarState.faceState.props,
                    customProps = activeAvatarState.faceState.customProps,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(gestureModifier)
                        .padding(if (isLandscape) 12.dp else 24.dp)
                        .graphicsLayer {
                            rotationY = screenAnimatedRotationY
                            rotationX = screenAnimatedRotationX
                            cameraDistance = 14f * density.density
                            translationX = screenSmoothedGazeX * 6.dp.toPx()
                        }
                )

                // Layer 3.5: Holographic Hand Overlay (Rive draws its own hands)
                if (!useRive) HolographicHandOverlay(
                    gesture = holoGesture,
                    touchPosition = holoPosition,
                    isFromLeft = holoFromLeft,
                    triggerId = holoTriggerId,
                    onFinished = { holoGesture = null },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Layer 3.6: Missile Barrage Overlay (Rive plays the AngryMissile story)
            if (!useRive) MissileBarrageOverlay(
                isActive = isMissileBarrageActive,
                onFinished = {
                    onMissileBarrageFinished()
                    screenShakeIntensity = 0f
                },
                onScreenShake = { intensity ->
                    screenShakeIntensity = intensity
                },
                modifier = Modifier.fillMaxSize()
            )

            // Layer 3.7: Dynamic Foreground Layer (Lens & Atmospheric 2.5D Accelerated Parallax)
            PetForegroundLayer(
                effect = rivePlan?.composeForeground ?: activeAvatarState.faceState.foregroundEffect,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = screenSmoothedGazeX * 24.dp.toPx()
                        translationY = screenSmoothedGazeY * 18.dp.toPx()
                    }
            )

            // Layer 4: Dialogue Card (Landscape only — in Portrait, displayed on top of dimmed bottom status panel)
            if (isLandscape) {
                AnimatedVisibility(
                    visible = hasMessage,
                    enter = slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn() + scaleIn(initialScale = 0.90f),
                    exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(250)) + fadeOut(),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    if (petDialogueText != null) {
                        PetDialogueCard(
                            text = petDialogueText,
                            isSpeaking = activeAvatarState.isSpeaking,
                            audioLevel = activeAvatarState.audioLevel,
                            auraColor = auraColor,
                            isLandscape = true,
                            onDismiss = {
                                onDismissToolCard?.invoke()
                                onUpdateActiveAvatarState(
                                    activeAvatarState.copy(
                                        statusText = null,
                                        faceState = activeAvatarState.faceState.copy(speechText = null)
                                    )
                                )
                            },
                            modifier = Modifier
                                .padding(end = 28.dp)
                                .widthIn(min = 280.dp, max = (screenMaxWidth * 0.44f).coerceAtLeast(300.dp))
                                .fillMaxHeight(0.72f)
                        )
                    }
                }
            }

            // Live Detection HUD
            val isTouchActive = (currentTick - lastTouchTime < 2500L)
            val isShakeActive = (currentTick - lastShakeTime < 3000L) || activeAvatarState.isDizzy
            val isFaceActive = (currentTick - lastFaceTime < 2500L) || detectedObjects.isNotEmpty()
            val isAudioActive = !isMuted && (activeAvatarState.audioLevel > 0.05f || activeAvatarState.isSpeaking)

            val touchText = if (isTouchActive) (lastTouchAction ?: "แตะหน้าจอ") else "สัมผัส: รอแตะ"
            val shakeText = if (isShakeActive) "Shake detected! (@_@)" else "เครื่องนิ่ง"
            val faceText = when {
                detectedObjects.isNotEmpty() -> detectedObjects.take(2).joinToString(" • ") { it.label }
                lastFaceCoords != null -> "Face (%.2f, %.2f)".format(lastFaceCoords.first, lastFaceCoords.second)
                else -> "สแกนภาพ..."
            }
            val audioText = when {
                isMuted -> "ไมค์ปิด"
                activeAvatarState.isSpeaking -> "[AI พูด] ตอบกลับ..."
                activeAvatarState.audioLevel > 0.05f -> "[LISTENING] ฟังเสียง ${(activeAvatarState.audioLevel * 100).toInt()}%"
                else -> "[LISTENING] พร้อมฟัง"
            }

            if (showDebugHud) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = if (isLandscape) 16.dp else 56.dp, start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PetDetectionBadge(icon = "🐾", label = touchText, isActive = isTouchActive, activeColor = Color(0xFF00E676))
                    PetDetectionBadge(icon = "🎭", label = audioText, isActive = isAudioActive, activeColor = if (activeAvatarState.isSpeaking) Color(0xFFD500F9) else Color(0xFFFFD600))
                    PetDetectionBadge(icon = "🌀", label = shakeText, isActive = isShakeActive, activeColor = Color(0xFFFF5722))
                    PetDetectionBadge(
                        icon = when {
                            detectedObjects.any { it.label.contains("Hand") || it.label.contains("Finger") } -> "🖐️"
                            detectedObjects.any { it.label.contains("Object") || it.label.contains("Item") || it.label.contains("Food") } -> "📦"
                            else -> "👀"
                        },
                        label = faceText,
                        isActive = isFaceActive,
                        activeColor = when {
                            detectedObjects.any { it.label.contains("Smile") } -> Color(0xFFFF4081)
                            detectedObjects.any { it.label.contains("Hand") || it.label.contains("Finger") } -> Color(0xFFFF9100)
                            detectedObjects.any { it.label.contains("Object") || it.label.contains("Item") } -> Color(0xFF00E5FF)
                            else -> JarvisTheme.Cyan
                        }
                    )
                }
            }

            // Power-Optimized Background Camera Preview:
            // Runs only when:
            // 1. petVisionProcessor is available
            // 2. Eye scanner is closed
            // 3. Pet is NOT sleeping (saves battery & respects sleep mode)
            val shouldRunBackgroundVision = petVisionProcessor != null &&
                    !isCameraPipOpen &&
                    activeAvatarState.emotion != AvatarEmotion.SLEEPING

            if (shouldRunBackgroundVision) {
                CameraPreviewView(
                    modifier = Modifier.size(1.dp).alpha(0.001f),
                    isFrontCamera = isFrontCam,
                    isActive = true,
                    onFrameCapture = { _, rawBytes ->
                        petVisionProcessor.processFrame(rawBytes, isFrontCamera = isFrontCam)
                    }
                )
            }

            // Top Controls Bar
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Demo Button
                    Surface(
                        color = if (isDemoRunning) Color(0xFFFF4081).copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.40f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isDemoRunning) Color(0xFFFF4081) else Color.White.copy(alpha = 0.20f)),
                        modifier = Modifier.clickable { onToggleDemo?.invoke() }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    isDemoRunning -> if (isLandscape) "⏹️ หยุดเดโม" else "⏹️ หยุด"
                                    else -> if (isLandscape) "🧪 ทดสอบเดโม" else "🧪 เดโม"
                                },
                                color = if (isDemoRunning) Color(0xFFFF80AB) else Color.White.copy(alpha = 0.88f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Eye Toggle Button
                    Surface(
                        color = if (isCameraPipOpen) JarvisTheme.Cyan.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.40f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isCameraPipOpen) JarvisTheme.Cyan else Color.White.copy(alpha = 0.20f)),
                        modifier = Modifier.clickable { onToggleCameraPip() }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (isCameraPipOpen) "👁️ หลับตา" else "👁️ ลืมตา",
                                color = if (isCameraPipOpen) JarvisTheme.Cyan else Color.White.copy(alpha = 0.88f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Status Sidebar Button (Landscape only)
                    if (isLandscape) {
                        Surface(
                            color = if (isPetSidebarOpen) JarvisTheme.Cyan.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.40f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (isPetSidebarOpen) JarvisTheme.Cyan else Color.White.copy(alpha = 0.20f)),
                            modifier = Modifier.clickable { isPetSidebarOpen = !isPetSidebarOpen }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "🐾 สถานะ",
                                    color = if (isPetSidebarOpen) JarvisTheme.Cyan else Color.White.copy(alpha = 0.88f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Settings Button
                    Surface(
                        color = if (isPetSettingsOpen) JarvisTheme.Cyan.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.40f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isPetSettingsOpen) JarvisTheme.Cyan else Color.White.copy(alpha = 0.20f)),
                        modifier = Modifier.clickable { isPetSettingsOpen = true }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "⚙️ ตั้งค่า",
                                color = if (isPetSettingsOpen) JarvisTheme.Cyan else Color.White.copy(alpha = 0.88f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Mode Selector (🚗 โหมดควบคุม)
                    Surface(
                        color = Color.Black.copy(alpha = 0.40f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                        modifier = Modifier.clickable { onSelectProfile?.invoke(AlwaysLiveProfile.CONTROL) }
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = if (isLandscape) 9.dp else 8.dp,
                                vertical = 6.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isLandscape) "🚗 โหมดควบคุม" else "🚗",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = if (isLandscape) 11.sp else 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Pinned Exit Button
                IconButton(
                    onClick = onEndLive,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Exit Pet Mode",
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
}

    val petNeedsForSidebar by petController.needsState.collectAsStateWithLifecycle()
    val petMemoryForSidebar by PetMemoryStore.memoryState.collectAsStateWithLifecycle()

    if (!isLandscape) {
        // ─── PORTRAIT: SPLIT-SCREEN ───
        // When dialogue card appears: status panel dims down, dialogue card displays on top of status panel.
        // When dialogue card disappears: status panel smoothly returns to full brightness.
        val statusContentAlpha by animateFloatAsState(
            targetValue = if (hasMessage) 0.08f else 1.0f,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "statusContentAlpha"
        )
        val statusScrimAlpha by animateFloatAsState(
            targetValue = if (hasMessage) 0.78f else 0.0f,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "statusScrimAlpha"
        )

        Column(modifier = Modifier.fillMaxSize()) {
            petFaceContent(
                Modifier
                    .weight(1.08f)
                    .fillMaxWidth()
            )

            Surface(
                modifier = Modifier
                    .weight(0.92f)
                    .fillMaxWidth(),
                color = Color(0xFF0D1117),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = if (hasMessage) 0.04f else 0.12f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Status Dashboard Content (Dims down when message shows)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = statusContentAlpha }
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 4.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                                .align(Alignment.CenterHorizontally)
                        )
                        PetNeedsDashboardContent(
                            needsState = petNeedsForSidebar,
                            memory = petMemoryForSidebar,
                            onFeed = { if (!hasMessage) petController.feedPet() },
                            onClean = { if (!hasMessage) petController.cleanPet() },
                            onPlay = { if (!hasMessage) petController.playWithPet() },
                            onSleep = { if (!hasMessage) petController.putToSleep() },
                            showHeader = false,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    // 2. Dim Scrim Overlay (Darkens status panel when message shows)
                    if (statusScrimAlpha > 0.005f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF06090E).copy(alpha = statusScrimAlpha))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onDismissToolCard?.invoke()
                                    onUpdateActiveAvatarState(
                                        activeAvatarState.copy(
                                            statusText = null,
                                            faceState = activeAvatarState.faceState.copy(speechText = null)
                                        )
                                    )
                                }
                        )
                    }

                    // 3. Dialogue Card (Displayed directly on top of dimmed status panel)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = hasMessage,
                        enter = slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(animationSpec = tween(260)) + scaleIn(initialScale = 0.94f),
                        exit = slideOutVertically(
                            targetOffsetY = { it / 3 },
                            animationSpec = tween(220)
                        ) + fadeOut(animationSpec = tween(200)),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        if (petDialogueText != null) {
                            PetDialogueCard(
                                text = petDialogueText,
                                isSpeaking = activeAvatarState.isSpeaking,
                                audioLevel = activeAvatarState.audioLevel,
                                auraColor = auraColor,
                                isLandscape = false,
                                onDismiss = {
                                    onDismissToolCard?.invoke()
                                    onUpdateActiveAvatarState(
                                        activeAvatarState.copy(
                                            statusText = null,
                                            faceState = activeAvatarState.faceState.copy(speechText = null)
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = screenMaxHeight * 0.38f)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // ─── LANDSCAPE: FULL-SCREEN PET + SLIDE-OUT STATUS SIDEBAR ───
        Box(modifier = Modifier.fillMaxSize()) {
            petFaceContent(Modifier.fillMaxSize())

            PetNeedsSidebarPanel(
                isOpen = isPetSidebarOpen,
                onClose = { isPetSidebarOpen = false },
                needsState = petNeedsForSidebar,
                memory = petMemoryForSidebar,
                onFeed = { petController.feedPet() },
                onClean = { petController.cleanPet() },
                onPlay = { petController.playWithPet() },
                onSleep = { petController.putToSleep() }
            )
        }
    }

    // ─── Pet Settings Modal Dialog ───
    if (isPetSettingsOpen) {
        val petNeeds by petController.needsState.collectAsStateWithLifecycle()
        val petFaceProfiles by petController.faceProfiles.collectAsStateWithLifecycle()
        val recognizedPerson by petController.currentRecognizedPerson.collectAsStateWithLifecycle()
        val screensaverDelay by petController.idleScreensaverDelaySeconds.collectAsStateWithLifecycle()

        PetSettingsDialog(
            onDismissRequest = { isPetSettingsOpen = false },
            showDebugHud = showDebugHud,
            onToggleDebugHud = { showDebugHud = it },
            avatarEngineType = avatarEngineType,
            onSelectAvatarEngine = {
                avatarEngineType = it
                PetMemoryStore.saveAvatarEngine(it.name)
            },
            screensaverDelaySeconds = screensaverDelay,
            onSetScreensaverDelay = { petController.setScreensaverDelay(it) },
            faceProfiles = petFaceProfiles,
            currentRecognizedPerson = recognizedPerson,
            hasDetectedFace = petController.lastDetectedLandmarks != null,
            onEnrollFace = { idx, name -> petController.enrollCurrentFace(idx, name) },
            onDeleteFace = { idx -> petController.deleteFaceProfile(idx) },
            onRenameFace = { idx, newName -> petController.renameFaceProfile(idx, newName) },
            needsState = petNeeds,
            onFeed = { petController.feedPet() },
            onClean = { petController.cleanPet() },
            onPlay = { petController.playWithPet() },
            onSleep = { petController.putToSleep() },
            currentTheme = activeAvatarState.faceState.backgroundTheme,
            onSelectTheme = { petController.setBackgroundTheme(it) },
            activeProps = activeAvatarState.faceState.props,
            onToggleProp = { petController.toggleProp(it) },
            onClearProps = { petController.clearProps() },
            customProps = activeAvatarState.faceState.customProps,
            onRemoveCustomProp = { petController.removeCustomProp(it) },
            onAddCustomProp = { petController.addCustomProp(it) }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// PetDialogueCard — LOOI-style Cyber Split Dialogue & Speech Card
// ═════════════════════════════════════════════════════════════════════════════
@Composable
internal fun PetDialogueCard(
    text: String,
    isSpeaking: Boolean,
    audioLevel: Float,
    auraColor: Color,
    isLandscape: Boolean,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0B1120).copy(alpha = 0.96f),
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    auraColor.copy(alpha = 0.90f),
                    auraColor.copy(alpha = 0.35f),
                    Color(0xFF334155).copy(alpha = 0.50f)
                )
            )
        ),
        shadowElevation = 20.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (isSpeaking) Color(0xFF00E676) else auraColor,
                                CircleShape
                            )
                    )
                    Text(
                        text = "LOOI • JARVIS",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSpeaking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            repeat(4) { i ->
                                val barHeight = (8 + (i * 4 + (audioLevel * 24f).toInt()) % 16).dp
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(barHeight)
                                        .background(Color(0xFF00E676), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }

                    if (onDismiss != null) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = if (isLandscape) 15.sp else 16.sp,
                    lineHeight = if (isLandscape) 22.sp else 24.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// PetDetectionBadge
// ═════════════════════════════════════════════════════════════════════════════
@Composable
internal fun PetDetectionBadge(
    icon: String,
    label: String,
    isActive: Boolean,
    activeColor: Color
) {
    Surface(
        color = if (isActive) activeColor.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.40f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isActive) activeColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.14f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(icon, fontSize = 11.sp)
            Text(
                text = label,
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// PetEyeScannerOverlay — Cybernetic Dual-Eye Viewfinder & Radar
// ═════════════════════════════════════════════════════════════════════════════
@Composable
internal fun PetEyeScannerOverlay(
    isScanning: Boolean,
    isFrontCamera: Boolean,
    detectedObjects: List<DetectedObject>,
    gazeX: Float = 0f,
    gazeY: Float = 0f,
    onFrameCapture: (jpegBase64: String, rawBytes: ByteArray) -> Unit,
    onSwitchCamera: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isScanning) return

    val infiniteTransition = rememberInfiniteTransition(label = "pet_eye_scanner")
    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_rot"
    )
    val apertureRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aperture_rot"
    )
    val radarPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_pulse"
    )

    BoxWithConstraints(modifier = modifier) {
        val widthDp = maxWidth
        val heightDp = maxHeight
        val isLandscape = widthDp > heightDp

        val eyeDiameter = if (isLandscape) {
            minOf(heightDp * 0.52f, widthDp * 0.28f)
        } else {
            minOf(widthDp * 0.38f, heightDp * 0.24f)
        }

        val baseSpacing = eyeDiameter * 0.65f
        val gazeDisplacementX = widthDp * (0.16f * gazeX.coerceIn(-1f, 1f))
        val gazeDisplacementY = heightDp * (0.12f * gazeY.coerceIn(-1f, 1f))
        val spacingFactor = 1f - 0.06f * kotlin.math.abs(gazeX)
        val dynamicSpacing = baseSpacing * spacingFactor

        val centerX = widthDp / 2f
        val centerY = heightDp / 2f

        val leftEyeCenterX = centerX + gazeDisplacementX - dynamicSpacing
        val rightEyeCenterX = centerX + gazeDisplacementX + dynamicSpacing
        val eyeCenterY = centerY + gazeDisplacementY

        // ─── LEFT EYE: Holographic Cyber Radar Scanner ───
        Box(
            modifier = Modifier
                .offset(
                    x = leftEyeCenterX - eyeDiameter / 2f,
                    y = eyeCenterY - eyeDiameter / 2f
                )
                .size(eyeDiameter)
                .clip(CircleShape)
                .background(Color(0xFF041422).copy(alpha = 0.94f))
                .border(
                    BorderStroke(
                        2.5.dp,
                        Brush.sweepGradient(
                            listOf(
                                JarvisTheme.Cyan,
                                Color(0xFF00E5FF),
                                Color(0xFF1DE9B6),
                                JarvisTheme.Cyan
                            )
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cX = size.width / 2f
                val cY = size.height / 2f
                val r = size.width / 2f

                drawCircle(color = JarvisTheme.Cyan.copy(alpha = 0.25f), radius = r * 0.33f, style = Stroke(width = 1.dp.toPx()))
                drawCircle(color = JarvisTheme.Cyan.copy(alpha = 0.38f), radius = r * 0.66f, style = Stroke(width = 1.dp.toPx()))
                drawCircle(color = JarvisTheme.Cyan.copy(alpha = 0.55f), radius = r * 0.96f, style = Stroke(width = 1.5.dp.toPx()))

                drawLine(
                    color = JarvisTheme.Cyan.copy(alpha = 0.32f),
                    start = Offset(0f, cY),
                    end = Offset(size.width, cY),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = JarvisTheme.Cyan.copy(alpha = 0.32f),
                    start = Offset(cX, 0f),
                    end = Offset(cX, size.height),
                    strokeWidth = 1.dp.toPx()
                )

                rotate(degrees = radarRotation, pivot = Offset(cX, cY)) {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(JarvisTheme.Cyan.copy(alpha = 0.05f), JarvisTheme.Cyan)
                        ),
                        start = Offset(cX, cY),
                        end = Offset(size.width, cY),
                        strokeWidth = 2.5.dp.toPx()
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                JarvisTheme.Cyan.copy(alpha = 0.30f)
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 45f,
                        useCenter = true,
                        size = Size(r * 2, r * 2),
                        topLeft = Offset(0f, 0f)
                    )
                }

                detectedObjects.take(4).forEachIndexed { idx, obj ->
                    val angleRad = (idx * 1.57f) + (radarRotation * kotlin.math.PI / 180f).toFloat()
                    val dist = r * (0.42f + (idx * 0.14f))
                    val bx = cX + kotlin.math.cos(angleRad) * dist
                    val by = cY + kotlin.math.sin(angleRad) * dist
                    drawCircle(
                        color = if (obj.isLocked) Color(0xFF00E676) else Color(0xFFFF4081),
                        radius = 3.5.dp.toPx() * radarPulse,
                        center = Offset(bx, by)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                Text(
                    text = "RADAR SCAN",
                    color = JarvisTheme.Cyan.copy(alpha = 0.75f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(2.dp))
                val lockedCount = detectedObjects.count { it.isLocked }
                Text(
                    text = when {
                        lockedCount > 0 -> "🔒 LOCKED $lockedCount"
                        detectedObjects.isNotEmpty() -> "TARGETS ${detectedObjects.size}"
                        else -> "SCANNING..."
                    },
                    color = if (lockedCount > 0) Color(0xFF00E676) else Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // ─── RIGHT EYE: Circular Cyber Optical Lens Viewfinder ───
        Box(
            modifier = Modifier
                .offset(
                    x = rightEyeCenterX - eyeDiameter / 2f,
                    y = eyeCenterY - eyeDiameter / 2f
                )
                .size(eyeDiameter)
                .clip(CircleShape)
                .background(Color.Black)
                .border(
                    BorderStroke(
                        2.5.dp,
                        Brush.sweepGradient(
                            listOf(
                                JarvisTheme.Cyan,
                                Color(0xFF00E5FF),
                                Color(0xFF80D8FF),
                                JarvisTheme.Cyan
                            )
                        )
                    ),
                    CircleShape
                )
        ) {
            CameraPreviewView(
                modifier = Modifier.fillMaxSize(),
                isFrontCamera = isFrontCamera,
                isActive = true,
                onFrameCapture = onFrameCapture
            )

            BoundingBoxOverlay(
                objects = detectedObjects,
                modifier = Modifier.fillMaxSize()
            )
            ObjectLabelTags(
                objects = detectedObjects,
                modifier = Modifier.fillMaxSize()
            )

            ScanLineEffect(
                isActive = true,
                modifier = Modifier.fillMaxSize()
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val cX = size.width / 2f
                val cY = size.height / 2f
                val r = size.width / 2f

                rotate(degrees = apertureRotation, pivot = Offset(cX, cY)) {
                    val angles = listOf(45f, 135f, 225f, 315f)
                    for (ang in angles) {
                        val rad = ang * kotlin.math.PI / 180.0
                        val x1 = cX + kotlin.math.cos(rad).toFloat() * (r - 1.dp.toPx())
                        val y1 = cY + kotlin.math.sin(rad).toFloat() * (r - 1.dp.toPx())
                        val x2 = cX + kotlin.math.cos(rad).toFloat() * (r - 8.dp.toPx())
                        val y2 = cY + kotlin.math.sin(rad).toFloat() * (r - 8.dp.toPx())
                        drawLine(
                            color = JarvisTheme.Cyan,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onSwitchCamera,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Switch Camera",
                        tint = JarvisTheme.Cyan,
                        modifier = Modifier.size(13.dp)
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close Camera Eye",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

/** Rive artboard placement on the pet screen (see PetRobotHeadAvatar call). */
private const val RIVE_ARTBOARD_EYE_ROW = 230f / 500f      // eye row inside the artboard
private const val RIVE_PORTRAIT_SCALE = 1.10f              // artboard side vs. area width
private const val RIVE_LANDSCAPE_SCALE = 1.15f             // artboard side vs. area height
private const val RIVE_PORTRAIT_EYE_LINE = 0.40f           // eye row, fraction of area height
private const val RIVE_LANDSCAPE_EYE_LINE = 0.42f

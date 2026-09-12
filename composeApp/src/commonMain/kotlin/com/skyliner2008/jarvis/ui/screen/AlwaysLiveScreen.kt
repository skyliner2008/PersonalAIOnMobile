package com.skyliner2008.jarvis.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.skyliner2008.jarvis.camera.BoundingBoxOverlay
import com.skyliner2008.jarvis.camera.CameraPreviewView
import com.skyliner2008.jarvis.camera.DetectedObject
import com.skyliner2008.jarvis.camera.ObjectLabelTags
import com.skyliner2008.jarvis.camera.ScanLineEffect
import com.skyliner2008.jarvis.pet.AlwaysLiveProfile
import com.skyliner2008.jarvis.pet.PetModeController
import com.skyliner2008.jarvis.pet.PetMotionBridge
import com.skyliner2008.jarvis.pet.PetVisionBridge
import com.skyliner2008.jarvis.sound.AmbientSoundPlayer
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import com.skyliner2008.jarvis.ui.component.avatar.JarvisAvatar
import com.skyliner2008.jarvis.ui.component.avatar.PetBackgroundLayer
import com.skyliner2008.jarvis.ui.component.avatar.PetPropsOverlay
import com.skyliner2008.jarvis.ui.component.avatar.PetRobotHeadAvatar
import com.skyliner2008.jarvis.ui.component.avatar.RobotFaceState
import com.skyliner2008.jarvis.ui.component.avatar.rememberGestureModifier
import com.skyliner2008.jarvis.pet.PetSettingsDialog
import com.skyliner2008.jarvis.pet.HoloHandGesture
import com.skyliner2008.jarvis.pet.TouchZone
import com.skyliner2008.jarvis.pet.PetNeedsSidebarPanel
import com.skyliner2008.jarvis.pet.PetNeedsDashboardContent
import com.skyliner2008.jarvis.pet.PetMemoryStore
import com.skyliner2008.jarvis.ui.component.avatar.HolographicHandOverlay
import com.skyliner2008.jarvis.ui.component.avatar.MissileBarrageOverlay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skyliner2008.jarvis.ui.theme.JarvisTheme
import kotlinx.coroutines.coroutineScope
import kotlin.math.*

/**
 * AlwaysLiveScreen — Full-screen AI Live mode
 *
 * แสดง JARVIS Robot Avatar เต็มจอ พร้อม:
 * - Animated audio visualizer ring รอบ avatar
 * - Dark gradient background ที่เปลี่ยนตาม emotion
 * - Status badge (LIVE / CONNECTING / ERROR)
 * - Controls: Mic, Camera, Minimize, End
 * - Keep screen on (ตั้งค่าจากภายนอก)
 *
 * @param avatarState สถานะปัจจุบันของ avatar
 * @param connectionState สถานะ WebSocket connection
 * @param isMuted microphone muted?
 * @param isCameraActive camera on?
 * @param onToggleMute toggle mic
 * @param onToggleCamera toggle camera
 * @param onMinimize ย่อเป็น mini floating mode
 * @param onEndLive จบ Always Live mode
 * @param currentTime เวลาปัจจุบัน (เช่น "14:30")
 */
@Composable
fun AlwaysLiveScreen(
    avatarState: AvatarState,
    connectionState: com.skyliner2008.jarvis.data.ConnectionState,
    isMuted: Boolean,
    isCameraActive: Boolean,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onMinimize: () -> Unit,
    onEndLive: () -> Unit,
    currentTime: String = "",
    currentProfile: AlwaysLiveProfile = AlwaysLiveProfile.CONTROL,
    onSelectProfile: ((AlwaysLiveProfile) -> Unit)? = null,
    onUpdateAvatarState: ((AvatarState) -> Unit)? = null,
    onLiveVideoFrame: ((String) -> Unit)? = null,
    isDemoRunning: Boolean = false,
    onToggleDemo: (() -> Unit)? = null,
    onSetImmersiveMode: ((Boolean) -> Unit)? = null,
    activeToolName: String? = null,
    lastToolResult: Pair<String, String>? = null,
    onDismissToolCard: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var activeAvatarState by remember { mutableStateOf(avatarState) }
    var lastObservedExternalFaceState by remember { mutableStateOf(avatarState.faceState) }

    // Live Detection HUD & Vision PIP state
    var detectedObjects by remember { mutableStateOf<List<DetectedObject>>(emptyList()) }
    var isCameraPipOpen by remember { mutableStateOf(false) }
    var isFrontCam by remember { mutableStateOf(true) }
    var lastLiveFrameSendTime by remember { mutableStateOf(0L) }

    var lastTouchAction by remember { mutableStateOf<String?>(null) }
    var lastTouchTime by remember { mutableStateOf(0L) }
    var lastShakeTime by remember { mutableStateOf(0L) }
    var lastFaceCoords by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var lastFaceTime by remember { mutableStateOf(0L) }
    var currentTick by remember { mutableStateOf(0L) }
    var showDebugHud by remember { mutableStateOf(false) }
    var isPetSettingsOpen by remember { mutableStateOf(false) }
    var isPetSidebarOpen by remember { mutableStateOf(false) }
    var holoGesture by remember { mutableStateOf<HoloHandGesture?>(null) }
    var holoPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var holoFromLeft by remember { mutableStateOf(false) }
    var holoTriggerId by remember { mutableStateOf(0L) }
    var isMissileBarrageActive by remember { mutableStateOf(false) }
    var screenShakeIntensity by remember { mutableStateOf(0f) }

    val petController = remember(scope) {
        PetModeController(
            scope = scope,
            avatarStateProvider = { activeAvatarState },
            onUpdateAvatarState = { updated ->
                activeAvatarState = updated
                onUpdateAvatarState?.invoke(updated)
            }
        )
    }

    LaunchedEffect(petController) {
        petController.onTriggerMissileBarrage = {
            isMissileBarrageActive = true
        }
    }

    // ─── Sound & Auto-Close Controller for Eye Scanner ───
    // 1. Play futuristic cyber scan radar sound whenever eye opens
    LaunchedEffect(isCameraPipOpen) {
        if (isCameraPipOpen) {
            com.skyliner2008.jarvis.sound.RobotSoundPlayer.playScanRadar()
        }
    }

    // 2. Safety timeout: close camera if open for > 25 seconds
    LaunchedEffect(isCameraPipOpen) {
        if (isCameraPipOpen) {
            kotlinx.coroutines.delay(25_000L)
            if (isCameraPipOpen) {
                isCameraPipOpen = false
                lastLiveFrameSendTime = 0L
                com.skyliner2008.jarvis.pet.PetVisionBridge.requestEyeOpen(false)
            }
        }
    }

    val petVisionProcessor = remember(currentProfile) {
        if (currentProfile == AlwaysLiveProfile.PET) {
            PetVisionBridge.createProcessor(
                onGazeDetected = { x, y ->
                    lastFaceCoords = Pair(x, y)
                    lastFaceTime = System.currentTimeMillis()
                    petController.onGazeTouch(x, y)
                },
                onIntruderDetected = { petController.triggerIntruderAlert() },
                onCopycatSuccess = { petController.onUserMimicSuccess() },
                getCopycatTarget = { petController.copycatTarget.value },
                isSentryActive = { petController.isSentryActive.value }
            )
        } else {
            null
        }
    }

    DisposableEffect(petVisionProcessor) {
        onDispose {
            petVisionProcessor?.release()
        }
    }

    DisposableEffect(currentProfile) {
        val isPet = (currentProfile == AlwaysLiveProfile.PET)
        if (isPet) {
            onSetImmersiveMode?.invoke(true)
            petController.start()
            PetMotionBridge.onShake = {
                lastShakeTime = System.currentTimeMillis()
                petController.onDizzy()
            }
            PetMotionBridge.onHeavyShake = {
                lastShakeTime = System.currentTimeMillis()
                petController.onHeavyShake()
            }
            PetMotionBridge.onBoatRocking = {
                petController.onBoatRocking()
            }
            PetMotionBridge.onTableThump = {
                petController.onTableThump()
            }
            PetMotionBridge.onFaceDown = { petController.onFaceDown() }
            PetMotionBridge.onFaceUp = { petController.onFaceUp() }
            PetVisionBridge.onObjectsDetected = { objects ->
                detectedObjects = objects
                if (objects.isNotEmpty()) {
                    lastFaceTime = System.currentTimeMillis()
                }
            }
            PetVisionBridge.onEyeOpenRequest = { open ->
                isCameraPipOpen = open
            }
        }
        onDispose {
            if (isPet) {
                onSetImmersiveMode?.invoke(false)
                petController.stop()
                AmbientSoundPlayer.stop()
                PetMotionBridge.onShake = null
                PetMotionBridge.onHeavyShake = null
                PetMotionBridge.onBoatRocking = null
                PetMotionBridge.onTableThump = null
                PetMotionBridge.onFaceDown = null
                PetMotionBridge.onFaceUp = null
                PetVisionBridge.onObjectsDetected = null
                PetVisionBridge.onEyeOpenRequest = null
            }
        }
    }

    // ─── Ambient Background Sound Control ──────────────────────────────────────
    LaunchedEffect(currentProfile, activeAvatarState.faceState.backgroundTheme) {
        if (currentProfile == AlwaysLiveProfile.PET) {
            AmbientSoundPlayer.setTheme(activeAvatarState.faceState.backgroundTheme)
        } else {
            AmbientSoundPlayer.stop()
        }
    }

    // Audio Ducking: Lower ambient volume while speaking
    LaunchedEffect(activeAvatarState.isSpeaking, activeAvatarState.audioLevel) {
        val isSpeaking = activeAvatarState.isSpeaking || activeAvatarState.audioLevel > 0.12f
        AmbientSoundPlayer.setDucking(isSpeaking)
    }

    // Loud noise detection: shouts, thumps, loud yelling -> pet surprised / sad
    LaunchedEffect(avatarState.audioLevel, activeAvatarState.isSpeaking) {
        if (currentProfile == AlwaysLiveProfile.PET && avatarState.audioLevel > 0.68f && !activeAvatarState.isSpeaking) {
            petController.onLoudNoise()
        }
    }

    LaunchedEffect(currentProfile) {
        if (currentProfile == AlwaysLiveProfile.PET) {
            while (true) {
                kotlinx.coroutines.delay(500)
                currentTick = System.currentTimeMillis()
            }
        }
    }

    LaunchedEffect(avatarState) {
        if (currentProfile == AlwaysLiveProfile.PET) {
            val hasExternalFaceChange = (avatarState.faceState != lastObservedExternalFaceState)
            if (hasExternalFaceChange) {
                lastObservedExternalFaceState = avatarState.faceState
            }
            val updatedFace = if (hasExternalFaceChange) {
                avatarState.faceState
            } else if (avatarState.faceState != RobotFaceState()) {
                avatarState.faceState
            } else {
                activeAvatarState.faceState
            }
            if (avatarState.isSpeaking) {
                activeAvatarState = activeAvatarState.copy(
                    isSpeaking = true,
                    audioLevel = avatarState.audioLevel,
                    emotion = AvatarEmotion.SPEAKING,
                    statusText = avatarState.statusText,
                    faceState = updatedFace
                )
                petController.notifyInteraction()
            } else {
                // When not speaking, update audio level for visual mouth reaction without clobbering pet emotions
                val currentEmotion = if (hasExternalFaceChange) {
                    updatedFace.emotion
                } else if (activeAvatarState.emotion == AvatarEmotion.SPEAKING) {
                    if (updatedFace.emotion != AvatarEmotion.IDLE) updatedFace.emotion else AvatarEmotion.IDLE
                } else {
                    activeAvatarState.emotion
                }
                activeAvatarState = activeAvatarState.copy(
                    isSpeaking = false,
                    audioLevel = avatarState.audioLevel,
                    emotion = currentEmotion,
                    statusText = if (hasExternalFaceChange) avatarState.statusText else activeAvatarState.statusText,
                    faceState = updatedFace
                )
                if (avatarState.audioLevel > 0.15f) {
                    petController.notifyInteraction()
                }
            }
        } else {
            activeAvatarState = avatarState
        }
    }

    val infinite = rememberInfiniteTransition(label = "always_live")

    // ─── Dynamic Ambient Gradient with Smooth Emotion Transitions ────────
    val targetBgColor1 = when (activeAvatarState.emotion) {
        AvatarEmotion.LISTENING -> Color(0xFF031A24)
        AvatarEmotion.SPEAKING -> Color(0xFF041D17)
        AvatarEmotion.THINKING -> Color(0xFF130726)
        AvatarEmotion.HAPPY, AvatarEmotion.WINK -> Color(0xFF041D1E)
        AvatarEmotion.EXCITED -> Color(0xFF211502)
        AvatarEmotion.LOVE -> Color(0xFF240718)
        AvatarEmotion.ANGRY -> Color(0xFF260505)
        AvatarEmotion.ENRAGED -> Color(0xFF3B0000)
        AvatarEmotion.SAD -> Color(0xFF070E1A)
        AvatarEmotion.SLEEPING -> Color(0xFF030514)
        AvatarEmotion.IDLE -> Color(0xFF060B1C)
        AvatarEmotion.CONFUSED -> Color(0xFF1A1303)
        AvatarEmotion.POUT -> Color(0xFF240E14)
        AvatarEmotion.DIZZY -> Color(0xFF1B072B)
        AvatarEmotion.SURPRISED -> Color(0xFF1F1D06)
        AvatarEmotion.BORED -> Color(0xFF0D1117)
    }
    val targetBgColor2 = when (activeAvatarState.emotion) {
        AvatarEmotion.LISTENING -> Color(0xFF063142)
        AvatarEmotion.SPEAKING -> Color(0xFF083B2F)
        AvatarEmotion.THINKING -> Color(0xFF220C42)
        AvatarEmotion.HAPPY, AvatarEmotion.WINK -> Color(0xFF0A3A36)
        AvatarEmotion.EXCITED -> Color(0xFF3D2704)
        AvatarEmotion.LOVE -> Color(0xFF3D0B27)
        AvatarEmotion.ANGRY -> Color(0xFF420A0A)
        AvatarEmotion.ENRAGED -> Color(0xFF5A0505)
        AvatarEmotion.SAD -> Color(0xFF0E1A33)
        AvatarEmotion.SLEEPING -> Color(0xFF060B24)
        AvatarEmotion.IDLE -> Color(0xFF0E1638)
        AvatarEmotion.CONFUSED -> Color(0xFF332005)
        AvatarEmotion.POUT -> Color(0xFF381020)
        AvatarEmotion.DIZZY -> Color(0xFF2B0C42)
        AvatarEmotion.SURPRISED -> Color(0xFF3D3A0A)
        AvatarEmotion.BORED -> Color(0xFF161E2E)
    }
    val targetBgColor3 = when (activeAvatarState.emotion) {
        AvatarEmotion.LISTENING -> Color(0xFF010D14)
        AvatarEmotion.SPEAKING -> Color(0xFF010E0B)
        AvatarEmotion.THINKING -> Color(0xFF07020E)
        AvatarEmotion.HAPPY, AvatarEmotion.WINK -> Color(0xFF020F0F)
        AvatarEmotion.EXCITED -> Color(0xFF0E0901)
        AvatarEmotion.LOVE -> Color(0xFF0F020A)
        AvatarEmotion.ANGRY -> Color(0xFF100101)
        AvatarEmotion.ENRAGED -> Color(0xFF220000)
        AvatarEmotion.SAD -> Color(0xFF03060C)
        AvatarEmotion.SLEEPING -> Color(0xFF010208)
        AvatarEmotion.IDLE -> Color(0xFF03040A)
        AvatarEmotion.CONFUSED -> Color(0xFF0E0701)
        AvatarEmotion.POUT -> Color(0xFF0F0308)
        AvatarEmotion.DIZZY -> Color(0xFF0B0212)
        AvatarEmotion.SURPRISED -> Color(0xFF121002)
        AvatarEmotion.BORED -> Color(0xFF080B10)
    }

    val animBg1 by animateColorAsState(targetBgColor1, animationSpec = tween(900, easing = EaseInOutCubic), label = "bg1")
    val animBg2 by animateColorAsState(targetBgColor2, animationSpec = tween(900, easing = EaseInOutCubic), label = "bg2")
    val animBg3 by animateColorAsState(targetBgColor3, animationSpec = tween(900, easing = EaseInOutCubic), label = "bg3")

    val auraColor = when (activeAvatarState.emotion) {
        AvatarEmotion.LISTENING -> Color(0xFF00F0FF) // Neon Aqua Cyan
        AvatarEmotion.SPEAKING -> Color(0xFF00E676) // Electric Emerald
        AvatarEmotion.THINKING -> Color(0xFFB388FF) // Cyber Violet
        AvatarEmotion.HAPPY, AvatarEmotion.WINK -> Color(0xFF00E5FF) // Sky Turquoise
        AvatarEmotion.EXCITED -> Color(0xFFFFD700) // Solar Amber Gold
        AvatarEmotion.LOVE, AvatarEmotion.POUT -> Color(0xFFFF4081) // Hot Neon Pink
        AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED -> Color(0xFFFF1744) // Alert Flame Red
        AvatarEmotion.SAD -> Color(0xFF80D8FF) // Ice Slate Blue
        AvatarEmotion.SLEEPING -> Color(0xFF7986CB) // Calm Lavender
        AvatarEmotion.IDLE -> Color(0xFF00F0FF) // Signature Jarvis Cyan
        AvatarEmotion.CONFUSED -> Color(0xFFFFAB00) // Amber
        AvatarEmotion.DIZZY -> Color(0xFFE040FB) // Magenta Violet
        AvatarEmotion.SURPRISED -> Color(0xFFE0F7FA) // Electric White-Cyan
        AvatarEmotion.BORED -> Color(0xFF90A4AE) // Slate Gray
    }

    // Mic pulsing
    val micScale by if (!isMuted) {
        infinite.animateFloat(
            initialValue = 1f, targetValue = 1.18f,
            animationSpec = infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse),
            label = "mic_pulse"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    // Ring rotation
    val ringRotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7500, easing = LinearEasing)),
        label = "ring_rotation"
    )

    // Ring pulse based on audio
    val ringPulse by infinite.animateFloat(
        initialValue = 0.97f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(550, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "ring_pulse"
    )

    // Ambient aura pulse
    val auraPulse by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "aura_pulse"
    )

    // Emotion status text
    val emotionText = activeAvatarState.statusText ?: when (activeAvatarState.emotion) {
        AvatarEmotion.IDLE -> "พร้อมรับคำสั่ง (Ready)"
        AvatarEmotion.LISTENING -> "กำลังฟัง... (Listening)"
        AvatarEmotion.THINKING -> "กำลังประมวลผล... (Thinking)"
        AvatarEmotion.SPEAKING -> "กำลังสนทนา... (Speaking)"
        AvatarEmotion.HAPPY -> "ยินดีให้บริการ 😊"
        AvatarEmotion.WINK -> "แอบขยิบตาให้คุณน้า 😉"
        AvatarEmotion.SAD -> "ต้องการความช่วยเหลือไหมคะ 😢"
        AvatarEmotion.ANGRY -> "ตรวจพบปัญหา 😤"
        AvatarEmotion.ENRAGED -> "โกรธจัด สู้กลับแล้วนะ! 😡💥"
        AvatarEmotion.CONFUSED -> "สงสัยจังเลยน้าา ❓"
        AvatarEmotion.LOVE -> "พร้อมดูแลคุณเสมอ 💕"
        AvatarEmotion.SLEEPING -> "โหมดสแตนด์บาย 💤"
        AvatarEmotion.EXCITED -> "ยอดเยี่ยมมาก! 🎉"
        AvatarEmotion.POUT -> "งอนนิดๆ แล้วน้า 😤"
        AvatarEmotion.DIZZY -> "ตาลาย เวียนหัวจังง @_@"
        AvatarEmotion.SURPRISED -> "ตกใจหมดเลยย! 😱"
        AvatarEmotion.BORED -> "เบื่อจังเลยย 😑"
    }

    val backgroundModifier = if (currentProfile == AlwaysLiveProfile.PET) {
        Modifier.background(Color.Black)
    } else {
        Modifier.background(Brush.verticalGradient(listOf(animBg1, animBg2, animBg3)))
    }
    val insetsModifier = if (currentProfile != AlwaysLiveProfile.PET) {
        Modifier.statusBarsPadding()
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier)
            .then(insetsModifier)
    ) {
        val screenMaxHeight = maxHeight
        val screenMaxWidth = maxWidth
        val isLandscape = screenMaxWidth > screenMaxHeight

        // Dynamic Sizing based on orientation & available space
        val avatarSize = if (isLandscape) {
            minOf(screenMaxHeight * 0.65f, 240.dp)
        } else {
            minOf(screenMaxWidth * 0.62f, 240.dp)
        }
        val visualizerSize = avatarSize * 1.48f

        if (currentProfile == AlwaysLiveProfile.PET) {
            // ═══════════════════════════════════════════════════════════════
            // VIRTUAL DESK PET: FULLSCREEN LIVING HEAD AVATAR (ZERO CLUTTER)
            // ═══════════════════════════════════════════════════════════════
            val isToolActive = activeToolName != null && activeToolName !in setOf(
                "device_avatar_emotion",
                "device_custom_prop",
                "device_always_live",
                "vision_activate"
            )
            val toolResultText = lastToolResult?.second

            // กล่องข้อความแสดงเฉพาะตอนที่มีการเรียกใช้ Tool / ผลลัพธ์ Tool หรือตอนรัน Emotion Demo เท่านั้น
            // การสนทนาทั่วไป (AI พูดคุย, รับฟัง, สแตนด์บาย) จะไม่แสดงกล่องข้อความ เพื่อให้เห็นหน้าตาหุ่นยนต์เต็มจอตรงกลาง
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
                    kotlinx.coroutines.delay(10_000L)
                    onDismissToolCard?.invoke()
                    activeAvatarState = activeAvatarState.copy(
                        statusText = null,
                        faceState = activeAvatarState.faceState.copy(speechText = null)
                    )
                }
            }

            // ─── Auto-Decay Contextual Props & Dynamic Background After 12s ───
            val currentProps = activeAvatarState.faceState.props
            LaunchedEffect(currentProps, isDemoRunning) {
                if (currentProps.isNotEmpty() && !isDemoRunning) {
                    kotlinx.coroutines.delay(12_000L)
                    activeAvatarState = activeAvatarState.copy(
                        faceState = activeAvatarState.faceState.copy(
                            propsRaw = "",
                            backgroundName = "default"
                        )
                    )
                }
            }

            val targetFaceOffsetX = if (hasMessage) {
                if (isLandscape) -0.24f else 0f
            } else 0f

            val targetFaceOffsetY = if (hasMessage) {
                if (isLandscape) 0f else -0.19f
            } else 0f

            val targetFaceScale = if (hasMessage) {
                if (isLandscape) 0.82f else 0.84f
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
                Box(
                    modifier = faceModifier
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
                                lastTouchTime = System.currentTimeMillis()
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
                                            lastTouchTime = System.currentTimeMillis()
                                        }
                                    }
                                } while (event.changes.any { it.pressed })

                                // Reset gaze to center smoothly
                                petController.onGazeTouch(0f, 0f)

                                if (isDrag) {
                                    if (normY < -0.28f && totalDragY > 15f) {
                                        // Forehead stroke down -> Love & Purr + Holo Hand STROKE
                                        lastTouchAction = "🐾 ลูบหน้าผาก (Pet Head)"
                                        lastTouchTime = System.currentTimeMillis()
                                        holoGesture = HoloHandGesture.STROKE
                                        holoPosition = down.position
                                        holoFromLeft = normX < 0
                                        holoTriggerId = System.currentTimeMillis()
                                        petController.onPetHead(TouchZone.FOREHEAD)
                                    } else if (normY > 0.32f && totalDragY < -15f) {
                                        // Chin scratch up -> Love & Purr + Holo Hand CHIN_SCRATCH
                                        lastTouchAction = "🐾 เกาคาง (Chin Scratch)"
                                        lastTouchTime = System.currentTimeMillis()
                                        holoGesture = HoloHandGesture.CHIN_SCRATCH
                                        holoPosition = down.position
                                        holoTriggerId = System.currentTimeMillis()
                                        petController.onChinScratch()
                                    } else if (normX > 0.60f && totalDragX < -30f) {
                                        // Swipe from right edge toward left -> Open Pet Needs Sidebar
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
                                val normY = (offset.y - cY) / (headH * 0.5f * animatedFaceScale)
                                if (normX.absoluteValue > 0.40f) {
                                    // Cheek double-tap -> Tickle & Giggle! + Holo Hand TICKLE
                                    lastTouchAction = "🐾 จั๊กจี้แก้ม (Tickle)"
                                    lastTouchTime = System.currentTimeMillis()
                                    holoGesture = HoloHandGesture.TICKLE
                                    holoPosition = offset
                                    holoTriggerId = System.currentTimeMillis()
                                    val zone = if (normX < 0) TouchZone.LEFT_CHEEK else TouchZone.RIGHT_CHEEK
                                    petController.onTickle(zone)
                                } else {
                                    lastTouchAction = "🐾 แตะหัว 2 ครั้ง (Pet Head)"
                                    lastTouchTime = System.currentTimeMillis()
                                    holoGesture = HoloHandGesture.PAT
                                    holoPosition = offset
                                    holoTriggerId = System.currentTimeMillis()
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
                                    activeAvatarState = activeAvatarState.copy(
                                        statusText = null,
                                        faceState = activeAvatarState.faceState.copy(speechText = null)
                                    )
                                }
                                if (normX.absoluteValue > 0.40f) {
                                    // Cheek tap -> Poke + Holo Hand POKE
                                    lastTouchAction = "🐾 จิ้มแก้ม (Poke)"
                                    lastTouchTime = System.currentTimeMillis()
                                    holoGesture = HoloHandGesture.POKE
                                    holoPosition = offset
                                    holoFromLeft = normX < 0
                                    holoTriggerId = System.currentTimeMillis()
                                    val zone = if (normX < 0) TouchZone.LEFT_CHEEK else TouchZone.RIGHT_CHEEK
                                    petController.onPoke(zone)
                                } else if (normY < -0.30f) {
                                    // Forehead tap -> Pet + Holo Hand STROKE
                                    lastTouchAction = "🐾 ลูบหัว (Pet Head)"
                                    lastTouchTime = System.currentTimeMillis()
                                    holoGesture = HoloHandGesture.STROKE
                                    holoPosition = offset
                                    holoFromLeft = normX < 0
                                    holoTriggerId = System.currentTimeMillis()
                                    petController.onPetHead(TouchZone.FOREHEAD)
                                } else if (normY > 0.32f) {
                                    // Chin tap -> Chin scratch
                                    lastTouchAction = "🐾 เกาคาง (Chin Scratch)"
                                    lastTouchTime = System.currentTimeMillis()
                                    holoGesture = HoloHandGesture.CHIN_SCRATCH
                                    holoPosition = offset
                                    holoTriggerId = System.currentTimeMillis()
                                    petController.onChinScratch()
                                } else {
                                    lastTouchAction = "🐾 แตะหน้าจอ (Tap)"
                                    lastTouchTime = System.currentTimeMillis()
                                    holoGesture = HoloHandGesture.PAT
                                    holoPosition = offset
                                    holoTriggerId = System.currentTimeMillis()
                                    petController.onPat()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // ─── Layer 0: Dynamic Background Theme (Rainy, Sunny, Night, Sakura, etc.) ───
                PetBackgroundLayer(
                    theme = activeAvatarState.faceState.backgroundTheme,
                    modifier = Modifier.fillMaxSize()
                )

                // Dynamic screen shake offset during missile explosions
                val shakeX = if (screenShakeIntensity > 0.05f) {
                    (kotlin.random.Random.nextFloat() * 2f - 1f) * screenShakeIntensity * 36f
                } else 0f
                val shakeY = if (screenShakeIntensity > 0.05f) {
                    (kotlin.random.Random.nextFloat() * 2f - 1f) * screenShakeIntensity * 36f
                } else 0f

                // ─── Face & Props Container (Animated Split-Screen Translation & Scale) ───
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
                    // ─── Layer 1: Background Ambient Aura ───
                    Box(
                        modifier = Modifier
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

                    // ─── Layer 2: Living Robot Head Avatar with Gesture Animation ───
                    val gestureModifier = rememberGestureModifier(activeAvatarState.faceState.gesture)
                    PetRobotHeadAvatar(
                        state = activeAvatarState,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(gestureModifier)
                            .padding(if (isLandscape) 12.dp else 24.dp)
                    )

                    // ─── Layer 2.5: Circular Eye Scanner & Cyber Radar Overlay ───
                    PetEyeScannerOverlay(
                        isScanning = isCameraPipOpen,
                        isFrontCamera = isFrontCam,
                        detectedObjects = detectedObjects,
                        gazeX = activeAvatarState.gazeOffsetX,
                        gazeY = activeAvatarState.gazeOffsetY,
                        onFrameCapture = { jpegBase64, rawBytes ->
                            petVisionProcessor?.processFrame(rawBytes, isFrontCamera = isFrontCam)
                            val now = System.currentTimeMillis()
                            if (now - lastLiveFrameSendTime >= 900L) {
                                lastLiveFrameSendTime = now
                                onLiveVideoFrame?.invoke(jpegBase64)
                            }
                        },
                        onSwitchCamera = { isFrontCam = !isFrontCam },
                        onClose = {
                            isCameraPipOpen = false
                            lastLiveFrameSendTime = 0L
                            com.skyliner2008.jarvis.pet.PetVisionBridge.requestEyeOpen(false)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(gestureModifier)
                            .padding(if (isLandscape) 12.dp else 24.dp)
                    )

                    // ─── Layer 3: Dynamic Props & Sticker Overlay (Following face) ───
                    PetPropsOverlay(
                        props = activeAvatarState.faceState.props,
                        customProps = activeAvatarState.faceState.customProps,
                        modifier = Modifier.fillMaxSize()
                    )

                    // ─── Layer 3.5: Holographic Hand Overlay (Direct touch interaction feedback) ───
                    HolographicHandOverlay(
                        gesture = holoGesture,
                        touchPosition = holoPosition,
                        isFromLeft = holoFromLeft,
                        triggerId = holoTriggerId,
                        onFinished = { holoGesture = null },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // ─── Layer 3.6: Cartoon Missile Barrage & Screen Explosions ───
                MissileBarrageOverlay(
                    isActive = isMissileBarrageActive,
                    onFinished = {
                        isMissileBarrageActive = false
                        screenShakeIntensity = 0f
                    },
                    onScreenShake = { intensity ->
                        screenShakeIntensity = intensity
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // ─── Layer 4: Adaptive Dialogue / Speech Card (LOOI Split Screen) ───
                AnimatedVisibility(
                    visible = hasMessage,
                    enter = if (isLandscape) {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn() + scaleIn(initialScale = 0.90f)
                    } else {
                        slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn() + scaleIn(initialScale = 0.90f)
                    },
                    exit = if (isLandscape) {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(250)
                        ) + fadeOut()
                    } else {
                        slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(250)
                        ) + fadeOut()
                    },
                    modifier = Modifier.align(
                        if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter
                    )
                ) {
                    if (petDialogueText != null) {
                        PetDialogueCard(
                            text = petDialogueText,
                            isSpeaking = activeAvatarState.isSpeaking,
                            audioLevel = activeAvatarState.audioLevel,
                            auraColor = auraColor,
                            isLandscape = isLandscape,
                            onDismiss = {
                                onDismissToolCard?.invoke()
                                activeAvatarState = activeAvatarState.copy(
                                    statusText = null,
                                    faceState = activeAvatarState.faceState.copy(speechText = null)
                                )
                            },
                            modifier = if (isLandscape) {
                                Modifier
                                    .padding(end = 28.dp)
                                    .widthIn(min = 280.dp, max = (screenMaxWidth * 0.44f).coerceAtLeast(300.dp))
                                    .fillMaxHeight(0.72f)
                            } else {
                                Modifier
                                    .padding(horizontal = 20.dp, vertical = 28.dp)
                                    .fillMaxWidth()
                                    .heightIn(max = screenMaxHeight * 0.36f)
                            }
                        )
                    }
                }

                // ─── Live Detection HUD: 🐾 Touch, 🎭 Audio, 🌀 Shake, 👀 Face ───
                val isTouchActive = (currentTick - lastTouchTime < 2500L)
                val isShakeActive = (currentTick - lastShakeTime < 3000L) || activeAvatarState.isDizzy
                val isFaceActive = (currentTick - lastFaceTime < 2500L) || detectedObjects.isNotEmpty()
                val isAudioActive = !isMuted && (activeAvatarState.audioLevel > 0.05f || activeAvatarState.isSpeaking)

                val touchText = if (isTouchActive) (lastTouchAction ?: "แตะหน้าจอ") else "สัมผัส: รอแตะ"
                val shakeText = if (isShakeActive) "Shake detected! (@_@)" else "เครื่องนิ่ง"
                val faceText = when {
                    detectedObjects.isNotEmpty() -> detectedObjects.take(2).joinToString(" • ") { it.label }
                    lastFaceCoords != null -> "Face (%.2f, %.2f)".format(lastFaceCoords!!.first, lastFaceCoords!!.second)
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
                        PetDetectionBadge(
                            icon = "🐾",
                            label = touchText,
                            isActive = isTouchActive,
                            activeColor = Color(0xFF00E676)
                        )
                        PetDetectionBadge(
                            icon = "🎭",
                            label = audioText,
                            isActive = isAudioActive,
                            activeColor = if (activeAvatarState.isSpeaking) Color(0xFFD500F9) else Color(0xFFFFD600)
                        )
                        PetDetectionBadge(
                            icon = "🌀",
                            label = shakeText,
                            isActive = isShakeActive,
                            activeColor = Color(0xFFFF5722)
                        )
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

                // ─── Hidden Background Camera Preview (Always Running when Eye Scanner is Closed) ───
                // Runs on-device Gaze Tracking & Desk Sentry with zero network/cloud token overhead when eyes are closed
                if (!isCameraPipOpen) {
                    CameraPreviewView(
                        modifier = Modifier.size(1.dp).alpha(0.001f),
                        isFrontCamera = isFrontCam,
                        isActive = true,
                        onFrameCapture = { _, rawBytes ->
                            petVisionProcessor?.processFrame(rawBytes, isFrontCamera = isFrontCam)
                        }
                    )
                }

                // ─── Top Controls Bar: Scrollable Tools + Pinned Close Button ───
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Horizontally scrollable container for utility buttons (Guarantees no text wrapping/squashing)
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Demo Button [🧪 ทดสอบเดโม] / [⏹️ หยุดเดโม]
                        Surface(
                            color = if (isDemoRunning) Color(0xFFFF4081).copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.40f),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDemoRunning) Color(0xFFFF4081) else Color.White.copy(alpha = 0.20f)
                            ),
                            modifier = Modifier.clickable {
                                onToggleDemo?.invoke()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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

                        // Eye Toggle Button (ลืมตา / หลับตา)
                        Surface(
                            color = if (isCameraPipOpen) JarvisTheme.Cyan.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.40f),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isCameraPipOpen) JarvisTheme.Cyan else Color.White.copy(alpha = 0.20f)
                            ),
                            modifier = Modifier.clickable {
                                isCameraPipOpen = !isCameraPipOpen
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (isCameraPipOpen) "👁️ หลับตา" else "👁️ ลืมตา",
                                    color = if (isCameraPipOpen) JarvisTheme.Cyan else Color.White.copy(alpha = 0.88f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Status Sidebar Button (🐾 สถานะน้อง) - เฉพาะโหมดแนวนอน (แนวตั้งมี Split Screen แดชบอร์ดถาวรอยู่แล้ว)
                        if (isLandscape) {
                            Surface(
                                color = if (isPetSidebarOpen) JarvisTheme.Cyan.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.40f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isPetSidebarOpen) JarvisTheme.Cyan else Color.White.copy(alpha = 0.20f)
                                ),
                                modifier = Modifier.clickable {
                                    isPetSidebarOpen = !isPetSidebarOpen
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🐾 สถานะ",
                                        color = if (isPetSidebarOpen) JarvisTheme.Cyan else Color.White.copy(alpha = 0.88f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Settings Button (⚙️ ตั้งค่าสัตว์เลี้ยง)
                        Surface(
                            color = if (isPetSettingsOpen) JarvisTheme.Cyan.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.40f),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isPetSettingsOpen) JarvisTheme.Cyan else Color.White.copy(alpha = 0.20f)
                            ),
                            modifier = Modifier.clickable {
                                isPetSettingsOpen = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                            modifier = Modifier.clickable {
                                onSelectProfile?.invoke(AlwaysLiveProfile.CONTROL)
                            }
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

                    // Pinned Exit Button (ALWAYS VISIBLE & ACCESSIBLE ON ALL SCREENS)
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

            val petNeedsForSidebar by petController.needsState.collectAsStateWithLifecycle()
            val petMemoryForSidebar by PetMemoryStore.memoryState.collectAsStateWithLifecycle()

            if (!isLandscape) {
                // ─── PORTRAIT: SPLIT-SCREEN (Upper: Pet Avatar, Lower: Permanent Status Dashboard) ───
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Subtle drag handle indicator
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
                                onFeed = { petController.feedPet() },
                                onClean = { petController.cleanPet() },
                                onPlay = { petController.playWithPet() },
                                onSleep = { petController.putToSleep() },
                                showHeader = false,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
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

            // ─── Pet Settings Modal Dialog (Shared across orientations) ───
            if (isPetSettingsOpen) {
                val petNeeds by petController.needsState.collectAsStateWithLifecycle()
                val petFaceProfiles by petController.faceProfiles.collectAsStateWithLifecycle()
                val recognizedPerson by petController.currentRecognizedPerson.collectAsStateWithLifecycle()
                val screensaverDelay by petController.idleScreensaverDelaySeconds.collectAsStateWithLifecycle()

                PetSettingsDialog(
                    onDismissRequest = { isPetSettingsOpen = false },
                    showDebugHud = showDebugHud,
                    onToggleDebugHud = { showDebugHud = it },
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
        } else {
            // ═══════════════════════════════════════════════════════════════
            // DRIVE & CONTROL MODE (3D Pearlescent Clay Robot Avatar + Live Controls)
            // ═══════════════════════════════════════════════════════════════
            if (!isLandscape) {
                // ─── PORTRAIT LAYOUT ───
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar
                    TopStatusRow(
                        connectionState = connectionState,
                        currentTime = currentTime,
                        onMinimize = onMinimize,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp)
                    )

                    // Profile Selector Row (Control vs Pet)
                    ProfileSelectorRow(
                        currentProfile = currentProfile,
                        onSelectProfile = onSelectProfile,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Center — Avatar + 36-bar Visualizer + Ambient Aura
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Pulsing Ambient Radial Aura behind avatar
                        Box(
                            modifier = Modifier
                                .size(visualizerSize * 1.1f * auraPulse)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            auraColor.copy(alpha = 0.22f + activeAvatarState.audioLevel * 0.25f),
                                            auraColor.copy(alpha = 0.06f),
                                            Color.Transparent
                                        )
                                    ),
                                    CircleShape
                                )
                        )

                        // 36-Bar High-Tech Audio Visualizer Ring
                        AudioVisualizerRing(
                            audioLevel = activeAvatarState.audioLevel,
                            emotion = activeAvatarState.emotion,
                            rotation = ringRotation,
                            pulse = ringPulse,
                            modifier = Modifier.size(visualizerSize)
                        )

                        // 3D Robot Avatar
                        JarvisAvatar(
                            state = activeAvatarState,
                            modifier = Modifier.size(avatarSize)
                        )
                    }

                    // Emotion Status Pill
                    StatusPill(
                        emotionText = emotionText,
                        emotion = activeAvatarState.emotion,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Drive & Control Mode Feature Card
                    DriveFeaturePanel(
                        auraColor = auraColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    // Optional Camera Preview in Drive/Control Mode when camera is enabled
                    if (isCameraActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            CameraPreviewView(
                                modifier = Modifier.fillMaxSize(),
                                isFrontCamera = true,
                                isActive = true,
                                onFrameCapture = { jpegBase64, _ ->
                                    val now = System.currentTimeMillis()
                                    if (now - lastLiveFrameSendTime >= 900L) {
                                        lastLiveFrameSendTime = now
                                        onLiveVideoFrame?.invoke(jpegBase64)
                                    }
                                }
                            )
                        }
                    }

                    // Bottom Controls
                    ControlsRow(
                        isMuted = isMuted,
                        isCameraActive = isCameraActive,
                        micScale = micScale,
                        onToggleMute = onToggleMute,
                        onToggleCamera = onToggleCamera,
                        onEndLive = onEndLive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 36.dp)
                            .navigationBarsPadding()
                    )
                }
            } else {
                // ─── LANDSCAPE LAYOUT (Two-Pane) ───
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Pane: Avatar & 36-bar Visualizer
                    Box(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(visualizerSize * 1.15f * auraPulse)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            auraColor.copy(alpha = 0.25f + activeAvatarState.audioLevel * 0.25f),
                                            auraColor.copy(alpha = 0.07f),
                                            Color.Transparent
                                        )
                                    ),
                                    CircleShape
                                )
                        )

                        AudioVisualizerRing(
                            audioLevel = activeAvatarState.audioLevel,
                            emotion = activeAvatarState.emotion,
                            rotation = ringRotation,
                            pulse = ringPulse,
                            modifier = Modifier.size(visualizerSize)
                        )

                        JarvisAvatar(
                            state = activeAvatarState,
                            modifier = Modifier.size(avatarSize)
                        )
                    }

                    // Right Pane: Top Bar, Telemetry Card, and Controls
                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Bar
                        TopStatusRow(
                            connectionState = connectionState,
                            currentTime = currentTime,
                            onMinimize = onMinimize,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Profile Selector Row
                        ProfileSelectorRow(
                            currentProfile = currentProfile,
                            onSelectProfile = onSelectProfile,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        // Cyber HUD Status Card
                        Surface(
                            color = JarvisTheme.Card.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                auraColor.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "🚗 DRIVE & CONTROL TELEMETRY",
                                    color = auraColor.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    emotionText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (activeAvatarState.audioLevel > 0.05f) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Mic Level: ${(activeAvatarState.audioLevel * 100).toInt()}%",
                                        color = JarvisTheme.Cyan.copy(alpha = 0.8f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Optional Camera Preview in Drive/Control Landscape Mode
                        if (isCameraActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                CameraPreviewView(
                                    modifier = Modifier.fillMaxSize(),
                                    isFrontCamera = true,
                                    isActive = true,
                                    onFrameCapture = { jpegBase64, _ ->
                                        val now = System.currentTimeMillis()
                                        if (now - lastLiveFrameSendTime >= 900L) {
                                            lastLiveFrameSendTime = now
                                            onLiveVideoFrame?.invoke(jpegBase64)
                                        }
                                    }
                                )
                            }
                        }

                        // Bottom Controls
                        ControlsRow(
                            isMuted = isMuted,
                            isCameraActive = isCameraActive,
                            micScale = micScale,
                            onToggleMute = onToggleMute,
                            onToggleCamera = onToggleCamera,
                            onEndLive = onEndLive,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Sub-Composables
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun TopStatusRow(
    connectionState: com.skyliner2008.jarvis.data.ConnectionState,
    currentTime: String,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (statusText, statusColor, statusBgColor) = when (connectionState) {
            is com.skyliner2008.jarvis.data.ConnectionState.Connected ->
                Triple("● LIVE", Color(0xFF69F0AE), Color(0xFF1B5E20).copy(alpha = 0.9f))
            is com.skyliner2008.jarvis.data.ConnectionState.Error ->
                Triple("● ERROR", Color(0xFFFF8A80), Color(0xFF5D1A1A).copy(alpha = 0.9f))
            is com.skyliner2008.jarvis.data.ConnectionState.Connecting,
            is com.skyliner2008.jarvis.data.ConnectionState.Reconnecting ->
                Triple("● CONNECTING", Color.White.copy(alpha = 0.55f), JarvisTheme.Card)
            else ->
                Triple("● OFFLINE", Color.White.copy(alpha = 0.4f), JarvisTheme.Card)
        }

        Surface(
            color = statusBgColor,
            shape = RoundedCornerShape(50),
            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor)
        ) {
            Text(
                statusText,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (currentTime.isNotEmpty()) {
            Text(
                currentTime,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        IconButton(
            onClick = onMinimize,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.Minimize,
                contentDescription = "Minimize to floating",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StatusPill(
    emotionText: String,
    emotion: AvatarEmotion,
    modifier: Modifier = Modifier
) {
    val pillColor = when (emotion) {
        AvatarEmotion.LISTENING -> JarvisTheme.Cyan
        AvatarEmotion.SPEAKING -> JarvisTheme.Green
        AvatarEmotion.THINKING -> Color(0xFFB388FF)
        AvatarEmotion.HAPPY -> Color(0xFF00E5FF)
        AvatarEmotion.EXCITED -> Color(0xFFFFD700)
        AvatarEmotion.LOVE -> JarvisTheme.HeartPink
        AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED -> JarvisTheme.Red
        AvatarEmotion.SAD -> Color(0xFF80D8FF)
        AvatarEmotion.SLEEPING -> Color(0xFF7986CB)
        AvatarEmotion.IDLE -> JarvisTheme.Cyan
        AvatarEmotion.WINK -> Color(0xFF00E5FF)
        AvatarEmotion.CONFUSED -> Color(0xFFFFB300)
        AvatarEmotion.POUT -> Color(0xFFFF4081)
        AvatarEmotion.DIZZY -> Color(0xFFCE93D8)
        AvatarEmotion.SURPRISED -> Color(0xFFE0F7FA)
        AvatarEmotion.BORED -> Color(0xFF90A4AE)
    }

    Surface(
        color = JarvisTheme.Card.copy(alpha = 0.45f),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            pillColor.copy(alpha = 0.45f)
        ),
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = emotionText,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "status_text",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        ) { text ->
            Text(
                text,
                color = pillColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp
            )
        }
    }
}

/**
 * PetDialogueCard — LOOI-style Cyber Split Dialogue & Speech Card
 * ออกแบบแนว Frosted Acrylic Glassmorphism พร้อมขอบนีออนเรืองแสง
 * แสดงข้อความสนทนา/ความรู้สึกของ AI พร้อมเอฟเฟกต์ visualizer ขนาดกะทัดรัด
 */
@Composable
private fun PetDialogueCard(
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
        color = Color(0xFF0B1120).copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    auraColor.copy(alpha = 0.90f),
                    auraColor.copy(alpha = 0.35f),
                    Color(0xFF334155).copy(alpha = 0.50f)
                )
            )
        ),
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth()
        ) {
            // Header Row: Robot Identity + Audio Activity Wave + Dismiss Button
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

            // Body: Speech / Thought text with auto-scrolling for longer text
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

@Composable
private fun ControlsRow(
    isMuted: Boolean,
    isCameraActive: Boolean,
    micScale: Float,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onEndLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Camera Toggle
        ControlButton(
            icon = if (isCameraActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
            label = "กล้อง",
            isActive = isCameraActive,
            activeColor = JarvisTheme.Cyan,
            onClick = onToggleCamera
        )

        // Mic Toggle (center, bigger)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(86.dp)
                .background(
                    if (isMuted) Color.White.copy(0.06f) else JarvisTheme.Red.copy(0.12f),
                    CircleShape
                )
        ) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(70.dp)
                    .background(
                        if (isMuted) JarvisTheme.Card else JarvisTheme.Red.copy(0.25f),
                        CircleShape
                    )
            ) {
                Icon(
                    if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mute",
                    modifier = Modifier
                        .scale(if (isMuted) 1f else micScale)
                        .size(36.dp),
                    tint = if (isMuted) Color.White.copy(0.5f) else JarvisTheme.Red
                )
            }
        }

        // End Button
        ControlButton(
            icon = Icons.Default.CallEnd,
            label = "End",
            isActive = true,
            activeColor = JarvisTheme.Red,
            onClick = onEndLive
        )
    }
}


// ═════════════════════════════════════════════════════════════════════════════
// 36-Bar High-Tech Radial Audio Visualizer Ring
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun AudioVisualizerRing(
    audioLevel: Float,
    emotion: AvatarEmotion,
    rotation: Float,
    pulse: Float,
    modifier: Modifier = Modifier
) {
    val ringColor = when (emotion) {
        AvatarEmotion.LISTENING -> Color(0xFF00F0FF) // Neon Cyan
        AvatarEmotion.SPEAKING -> Color(0xFF00E676) // Electric Emerald
        AvatarEmotion.THINKING -> Color(0xFFB388FF) // Cyber Violet
        AvatarEmotion.HAPPY -> Color(0xFF00E5FF) // Sky Turquoise
        AvatarEmotion.EXCITED -> Color(0xFFFFD700) // Solar Gold
        AvatarEmotion.LOVE -> Color(0xFFFF4081) // Hot Pink
        AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED -> Color(0xFFFF1744) // Alert Red
        AvatarEmotion.SAD -> Color(0xFF80D8FF) // Ice Slate Blue
        AvatarEmotion.SLEEPING -> Color(0xFF7986CB) // Calm Lavender
        AvatarEmotion.IDLE -> Color(0xFF00F0FF) // Signature Jarvis Cyan
        AvatarEmotion.WINK -> Color(0xFF00E5FF) // Sky Turquoise
        AvatarEmotion.CONFUSED -> Color(0xFFFFB300) // Amber
        AvatarEmotion.POUT -> Color(0xFFFF4081) // Hot Pink
        AvatarEmotion.DIZZY -> Color(0xFFCE93D8) // Dizzy Lilac
        AvatarEmotion.SURPRISED -> Color(0xFFE0F7FA) // Electric White-Cyan
        AvatarEmotion.BORED -> Color(0xFF90A4AE) // Slate Gray
    }

    val secondaryColor = when (emotion) {
        AvatarEmotion.LISTENING -> Color(0xFF00B0FF) // Deep Aqua
        AvatarEmotion.SPEAKING -> Color(0xFF69F0AE) // Mint Neon
        AvatarEmotion.THINKING -> Color(0xFF7C4DFF) // Deep Violet
        AvatarEmotion.HAPPY -> Color(0xFF1DE9B6) // Teal Emerald
        AvatarEmotion.EXCITED -> Color(0xFFFF9100) // Deep Amber
        AvatarEmotion.LOVE -> Color(0xFFFF80AB) // Soft Rose
        AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED -> Color(0xFFFF5252) // Coral Crimson
        AvatarEmotion.SAD -> Color(0xFF448AFF) // Cool Indigo
        AvatarEmotion.SLEEPING -> Color(0xFF3F51B5) // Midnight Blue
        AvatarEmotion.IDLE -> Color(0xFF7C4DFF) // Purple Cyber Accent
        AvatarEmotion.WINK -> Color(0xFF1DE9B6) // Teal Emerald
        AvatarEmotion.CONFUSED -> Color(0xFFFF9100) // Deep Amber
        AvatarEmotion.POUT -> Color(0xFFFF80AB) // Soft Rose
        AvatarEmotion.DIZZY -> Color(0xFFBA68C8) // Violet Spiral
        AvatarEmotion.SURPRISED -> Color(0xFF80DEEA) // Pale Cyan Accent
        AvatarEmotion.BORED -> Color(0xFF546E7A) // Deep Slate Gray
    }

    Canvas(modifier = modifier.scale(pulse)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minDim = size.minDimension
        val baseRadius = minDim / 2f

        // ─── 1. Outer Radial Glow ─────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    ringColor.copy(alpha = 0.08f + audioLevel * 0.15f),
                    secondaryColor.copy(alpha = 0.18f + audioLevel * 0.25f),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius
            ),
            radius = baseRadius,
            center = center
        )

        // ─── 2. Dual Rotating HUD Reticle Rings ───────────────────────────
        // Inner tech reticle (counter-clockwise)
        rotate(-rotation * 0.5f, pivot = center) {
            drawCircle(
                color = ringColor.copy(alpha = 0.25f),
                radius = baseRadius * 0.88f,
                center = center,
                style = Stroke(
                    width = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 16f), 0f)
                )
            )
        }

        // Outer tech reticle (clockwise)
        rotate(rotation * 0.7f, pivot = center) {
            drawCircle(
                color = secondaryColor.copy(alpha = 0.35f),
                radius = baseRadius * 0.98f,
                center = center,
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 24f), 0f)
                )
            )
        }

        // ─── 3. 36-Bar Radial Equalizer Bars ─────────────────────────────
        val barCount = 36
        val innerRadius = baseRadius * 0.72f
        val maxBarLength = baseRadius * 0.22f
        val baseBarLength = baseRadius * 0.04f

        for (i in 0 until barCount) {
            val angleDeg = i * (360f / barCount)
            val angleRad = (angleDeg * PI / 180f).toFloat()

            // Wave frequency harmonic per bar
            val harmonic = sin((i * 10f * (PI / 180f) * 3f + rotation * 0.08f).toFloat()).absoluteValue
            val dynamicLevel = (audioLevel * (0.55f + harmonic * 0.45f)).coerceIn(0f, 1f)
            val currentBarLength = baseBarLength + maxBarLength * dynamicLevel

            val cosA = cos(angleRad)
            val sinA = sin(angleRad)

            val startX = center.x + innerRadius * cosA
            val startY = center.y + innerRadius * sinA
            val endX = center.x + (innerRadius + currentBarLength) * cosA
            val endY = center.y + (innerRadius + currentBarLength) * sinA

            val barAlpha = (0.35f + dynamicLevel * 0.65f).coerceIn(0f, 1f)

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        ringColor.copy(alpha = barAlpha * 0.7f),
                        secondaryColor.copy(alpha = barAlpha),
                        Color.White.copy(alpha = barAlpha)
                    ),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY)
                ),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = (minDim * 0.011f).coerceIn(2.5f, 6f),
                cap = StrokeCap.Round
            )
        }

        // ─── 4. Cardinal Tech Dial Marks (0°, 90°, 180°, 270°) ────────────
        for (deg in listOf(0f, 90f, 180f, 270f)) {
            val rad = (deg * PI / 180f).toFloat()
            val tickR1 = baseRadius * 0.68f
            val tickR2 = baseRadius * 0.73f
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(center.x + tickR1 * cos(rad), center.y + tickR1 * sin(rad)),
                end = Offset(center.x + tickR2 * cos(rad), center.y + tickR2 * sin(rad)),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        }

        // ─── 5. Inner Core Base Circle ───────────────────────────────────
        drawCircle(
            color = ringColor.copy(alpha = 0.2f),
            radius = innerRadius * 0.98f,
            center = center,
            style = Stroke(width = 1.5f)
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Control Button
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (isActive) activeColor.copy(0.15f) else JarvisTheme.Card,
                    CircleShape
                )
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) activeColor else Color.White.copy(0.4f)
            )
        }
        Text(
            label,
            color = Color.White.copy(0.4f),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Always Live Profile Selector & Drive Mode Banner
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProfileSelectorRow(
    currentProfile: AlwaysLiveProfile,
    onSelectProfile: ((AlwaysLiveProfile) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = JarvisTheme.Card.copy(alpha = 0.5f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProfileSelectorItem(
                    label = "🚗 ขับขี่ / ควบคุม",
                    isSelected = currentProfile != AlwaysLiveProfile.PET,
                    activeColor = JarvisTheme.Cyan,
                    onClick = { onSelectProfile?.invoke(AlwaysLiveProfile.CONTROL) }
                )
                ProfileSelectorItem(
                    label = "🐾 สัตว์เลี้ยง",
                    isSelected = currentProfile == AlwaysLiveProfile.PET,
                    activeColor = Color(0xFFFF4081),
                    onClick = { onSelectProfile?.invoke(AlwaysLiveProfile.PET) }
                )
            }
        }
    }
}

@Composable
private fun ProfileSelectorItem(
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) activeColor.copy(alpha = 0.25f) else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, activeColor) else null,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun DriveFeaturePanel(
    auraColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = JarvisTheme.Card.copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9100).copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "🚗 SMART DRIVE & CONTROL",
                color = Color(0xFFFF9100),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "ระบบขับขี่และควบคุม รองรับคำสั่งเสียง แผนที่ YouTube และโทรศัพท์เต็มรูปแบบ",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PetDetectionBadge(
    icon: String,
    label: String,
    isActive: Boolean,
    activeColor: Color
) {
    Surface(
        color = if (isActive) activeColor.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.40f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
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

/**
 * PetEyeScannerOverlay — ภาพกล้องวงกลม Overlay แนบสนิทบนดวงตาสัตว์เลี้ยง
 *
 * สไตล์ Cybernetic Desk Pet:
 * - ตาขวา (Right Eye): Optical Lens Viewfinder ตัดกล้องทรงกลม (CircleShape)
 *   พร้อมกรอบนีออนเรืองแสง, วงแหวนเล็งเป้าหมายหมุนวน (Aperture Reticle), เส้นสแกน Scanline, และ AR Tags
 * - ตาซ้าย (Left Eye): Holographic Radar Scanner แสดงเรดาร์กวาด 360°,
 *   วงกลมศูนย์กลางเป้าหมาย, จุด Blip วัตถุที่ตรวจพบ, และสถานะ [SCANNING...] / [LOCKED]
 * - ปรับตำแหน่งและขนาดพอดีกับตาสัตว์เลี้ยงอัตโนมัติทั้งแนวตั้งและแนวนอน
 */
@Composable
private fun PetEyeScannerOverlay(
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
            // Radar Grid & Sweeping Beam Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cX = size.width / 2f
                val cY = size.height / 2f
                val r = size.width / 2f

                // Concentric circles
                drawCircle(color = JarvisTheme.Cyan.copy(alpha = 0.25f), radius = r * 0.33f, style = Stroke(width = 1.dp.toPx()))
                drawCircle(color = JarvisTheme.Cyan.copy(alpha = 0.38f), radius = r * 0.66f, style = Stroke(width = 1.dp.toPx()))
                drawCircle(color = JarvisTheme.Cyan.copy(alpha = 0.55f), radius = r * 0.96f, style = Stroke(width = 1.5.dp.toPx()))

                // Crosshair axes
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

                // 360° Rotating Radar Sweep Line
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

                // Plot detected object blips on radar
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

            // Central Status Overlay
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
            // Realtime Camera Feed
            CameraPreviewView(
                modifier = Modifier.fillMaxSize(),
                isFrontCamera = isFrontCamera,
                isActive = true,
                onFrameCapture = onFrameCapture
            )

            // AR Bounding Box & Label Overlay inside eye
            BoundingBoxOverlay(
                objects = detectedObjects,
                modifier = Modifier.fillMaxSize()
            )
            ObjectLabelTags(
                objects = detectedObjects,
                modifier = Modifier.fillMaxSize()
            )

            // High-Tech Animated Scanline Effect
            ScanLineEffect(
                isActive = true,
                modifier = Modifier.fillMaxSize()
            )

            // Sci-Fi Cyber Aperture Reticle Overlay
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

            // Quick Touch Controls on the Eye (Flip Camera & Close)
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

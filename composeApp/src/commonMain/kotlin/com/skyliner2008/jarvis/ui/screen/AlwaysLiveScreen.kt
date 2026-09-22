package com.skyliner2008.jarvis.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
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
import com.skyliner2008.jarvis.camera.DetectedObject
import com.skyliner2008.jarvis.data.ConnectionState
import com.skyliner2008.jarvis.pet.AlwaysLiveProfile
import com.skyliner2008.jarvis.pet.PetModeController
import com.skyliner2008.jarvis.pet.PetMotionBridge
import com.skyliner2008.jarvis.pet.PetVisionBridge
import com.skyliner2008.jarvis.sound.AmbientSoundPlayer
import com.skyliner2008.jarvis.sound.RobotSoundPlayer
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotionColors
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import com.skyliner2008.jarvis.ui.component.avatar.RobotFaceState
import com.skyliner2008.jarvis.ui.theme.JarvisTheme
import kotlinx.datetime.Clock
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

/** Emotions the voice session drives (not pet reactions). */
private val VOICE_SESSION_EMOTIONS = setOf(
    AvatarEmotion.IDLE, AvatarEmotion.LISTENING, AvatarEmotion.THINKING, AvatarEmotion.SPEAKING
)

/**
 * AlwaysLiveScreen — Full-screen AI Live mode
 *
 * แสดง JARVIS Robot Avatar เต็มจอ พร้อม:
 * - Animated audio visualizer ring รอบ avatar
 * - Dark gradient background ที่เปลี่ยนตาม emotion
 * - Status badge (LIVE / CONNECTING / ERROR)
 * - Controls: Mic, Camera, Minimize, End
 * - Profile Routing: Virtual Desk Pet (PET) vs Smart Drive & Control (CONTROL / DRIVE)
 */
@Composable
fun AlwaysLiveScreen(
    avatarState: AvatarState,
    connectionState: ConnectionState,
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
    BackHandler(enabled = true) {
        onEndLive()
    }
    val scope = rememberCoroutineScope()
    var activeAvatarState by remember { mutableStateOf(avatarState) }
    var lastObservedExternalEmotion by remember { mutableStateOf(avatarState.emotion) }
    var lastObservedExternalFaceState by remember { mutableStateOf(avatarState.faceState) }

    // Live Detection HUD & Vision PIP state
    var detectedObjects by remember { mutableStateOf<List<DetectedObject>>(emptyList()) }
    var isCameraPipOpen by remember { mutableStateOf(false) }
    var isFrontCam by remember { mutableStateOf(true) }
    var lastLiveFrameSendTime by remember { mutableStateOf(0L) }

    var lastShakeTime by remember { mutableStateOf(0L) }
    var lastFaceCoords by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var lastFaceTime by remember { mutableStateOf(0L) }
    var currentTick by remember { mutableStateOf(0L) }
    var isMissileBarrageActive by remember { mutableStateOf(false) }

    // True while the pet itself (touch, sensors, needs, scenes) owns the emotion.
    // Voice-session states arriving from the ViewModel (IDLE / LISTENING / THINKING /
    // SPEAKING flicker with the mic) must not overwrite a pet reaction until the
    // controller releases it by returning to IDLE.
    var petOwnsEmotion by remember { mutableStateOf(false) }

    val petController = remember(scope) {
        PetModeController(
            scope = scope,
            avatarStateProvider = { activeAvatarState },
            onUpdateAvatarState = { updated ->
                if (updated.emotion != activeAvatarState.emotion) {
                    petOwnsEmotion = updated.emotion !in VOICE_SESSION_EMOTIONS
                }
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

    // Sound & Auto-Close Controller for Eye Scanner
    LaunchedEffect(isCameraPipOpen) {
        if (isCameraPipOpen) {
            RobotSoundPlayer.playScanRadar()
        }
    }

    LaunchedEffect(isCameraPipOpen) {
        if (isCameraPipOpen) {
            kotlinx.coroutines.delay(25_000L)
            if (isCameraPipOpen) {
                isCameraPipOpen = false
                lastLiveFrameSendTime = 0L
                PetVisionBridge.requestEyeOpen(false)
            }
        }
    }

    val petVisionProcessor = remember(currentProfile) {
        if (currentProfile == AlwaysLiveProfile.PET) {
            PetVisionBridge.createProcessor(
                onGazeDetected = { x, y ->
                    lastFaceCoords = Pair(x, y)
                    lastFaceTime = Clock.System.now().toEpochMilliseconds()
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
        PetVisionBridge.onFaceDistanceDetected = { scale ->
            petController.onFaceDistance(scale)
        }
        PetVisionBridge.onFaceAbsenceTimeout = { emotion ->
            petController.onFaceAbsenceTimeout(emotion)
        }
        onDispose {
            PetVisionBridge.onFaceDistanceDetected = null
            PetVisionBridge.onFaceAbsenceTimeout = null
            petVisionProcessor?.release()
        }
    }

    DisposableEffect(currentProfile) {
        val isPet = (currentProfile == AlwaysLiveProfile.PET)
        if (isPet) {
            onSetImmersiveMode?.invoke(true)
            petController.start()
            PetMotionBridge.onShake = {
                lastShakeTime = Clock.System.now().toEpochMilliseconds()
                petController.onDizzy()
            }
            PetMotionBridge.onHeavyShake = {
                lastShakeTime = Clock.System.now().toEpochMilliseconds()
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
                    lastFaceTime = Clock.System.now().toEpochMilliseconds()
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

    // Ambient Background Sound Control
    val isScenePlaying = activeAvatarState.faceState.sceneName.isNotBlank()
    LaunchedEffect(currentProfile, activeAvatarState.faceState.backgroundTheme, isScenePlaying) {
        if (currentProfile == AlwaysLiveProfile.PET) {
            // a scene carries its own timed sound effects: no ambience loop under it
            // (the time-of-day NIGHT loop is a 4.4 kHz cricket chirp -- the rhythmic
            // beeping heard through the eating scene and most others)
            AmbientSoundPlayer.setTheme(
                if (isScenePlaying) com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme.DEFAULT
                else activeAvatarState.faceState.backgroundTheme
            )
        } else {
            AmbientSoundPlayer.stop()
        }
    }

    // Audio Ducking: Lower ambient volume while speaking
    LaunchedEffect(activeAvatarState.isSpeaking, activeAvatarState.audioLevel) {
        val isSpeaking = activeAvatarState.isSpeaking || activeAvatarState.audioLevel > 0.12f
        AmbientSoundPlayer.setDucking(isSpeaking)
    }

    // Loud noise detection: a SUDDEN spike (quiet -> very loud between two level
    // updates), not merely a high level — the pet lives in a Live session where the
    // user talks to it all the time, and every frame above the old 0.68 threshold used
    // to count as a new "loud noise" (4 frames = rage 100 = missiles).
    var previousMicLevel by remember { mutableStateOf(0f) }
    LaunchedEffect(avatarState.audioLevel, activeAvatarState.isSpeaking) {
        val level = avatarState.audioLevel
        val isSpike = level > 0.78f && previousMicLevel < 0.35f
        previousMicLevel = level
        if (currentProfile == AlwaysLiveProfile.PET && isSpike && !activeAvatarState.isSpeaking) {
            petController.onLoudNoise(level)
        }
    }

    LaunchedEffect(currentProfile) {
        if (currentProfile == AlwaysLiveProfile.PET) {
            while (true) {
                kotlinx.coroutines.delay(500)
                currentTick = Clock.System.now().toEpochMilliseconds()
            }
        }
    }

    LaunchedEffect(avatarState) {
        if (currentProfile == AlwaysLiveProfile.PET) {
            val hasFaceChange = (avatarState.faceState != lastObservedExternalFaceState)
            val hasEmotionChange = (avatarState.emotion != lastObservedExternalEmotion)
            if (hasFaceChange) {
                lastObservedExternalFaceState = avatarState.faceState
            }
            if (hasEmotionChange) {
                lastObservedExternalEmotion = avatarState.emotion
            }
            val hasExternalChange = hasFaceChange || hasEmotionChange
            // a mic / session state change alone never replaces a live pet reaction
            // (a tool face auto-resetting to IDLE also changes the face: the face is
            // applied, but a newer pet reaction keeps its emotion until it ends itself)
            val isVoiceStateOnly = hasEmotionChange &&
                    avatarState.emotion in VOICE_SESSION_EMOTIONS &&
                    avatarState.statusText?.startsWith("🎭") != true
            val keepPetEmotion = petOwnsEmotion && isVoiceStateOnly
            // the controller pushes every update up to the ViewModel, which sends it
            // straight back here: that echo is not an external change and must not
            // release the pet's ownership
            val isEchoOfPet = avatarState.emotion == activeAvatarState.emotion &&
                    avatarState.faceState == activeAvatarState.faceState
            if (hasEmotionChange) {
                com.skyliner2008.jarvis.logDebug(
                    "PetEmotionMerge",
                    "external=${avatarState.emotion} pet=${activeAvatarState.emotion} owns=$petOwnsEmotion keep=$keepPetEmotion echo=$isEchoOfPet"
                )
            }
            if (hasExternalChange && !keepPetEmotion && !isEchoOfPet) {
                petOwnsEmotion = false
            }

            val updatedFace = if (hasFaceChange) {
                avatarState.faceState
            } else if (avatarState.faceState != RobotFaceState()) {
                avatarState.faceState
            } else {
                activeAvatarState.faceState
            }

            // A specific non-default emotion is active if it's not normal IDLE/SPEAKING/LISTENING,
            // or if it's a catalog moodset test page (statusText starts with 🎭)
            val isSpecificEmotion = (avatarState.statusText?.startsWith("🎭") == true) ||
                    (avatarState.emotion != AvatarEmotion.IDLE &&
                     avatarState.emotion != AvatarEmotion.SPEAKING &&
                     avatarState.emotion != AvatarEmotion.LISTENING)

            if (avatarState.isSpeaking) {
                // If a specific moodset/emotion is active, PRESERVE IT and animate mouth on it!
                // Only fall back to AvatarEmotion.SPEAKING if there is no specific emotion.
                val speakingEmotion = when {
                    keepPetEmotion || petOwnsEmotion -> activeAvatarState.emotion
                    isSpecificEmotion -> avatarState.emotion
                    updatedFace.emotion != AvatarEmotion.IDLE -> updatedFace.emotion
                    else -> AvatarEmotion.SPEAKING
                }

                activeAvatarState = activeAvatarState.copy(
                    isSpeaking = true,
                    audioLevel = avatarState.audioLevel,
                    emotion = speakingEmotion,
                    statusText = avatarState.statusText,
                    faceState = updatedFace
                )
                if (!isSpecificEmotion) {
                    petController.notifyInteraction()
                }
            } else {
                val currentEmotion = when {
                    keepPetEmotion -> activeAvatarState.emotion
                    hasEmotionChange -> avatarState.emotion
                    hasFaceChange -> updatedFace.emotion
                    isSpecificEmotion -> avatarState.emotion
                    activeAvatarState.emotion == AvatarEmotion.SPEAKING -> {
                        if (updatedFace.emotion != AvatarEmotion.IDLE) updatedFace.emotion else AvatarEmotion.IDLE
                    }
                    else -> activeAvatarState.emotion
                }

                activeAvatarState = activeAvatarState.copy(
                    isSpeaking = false,
                    audioLevel = avatarState.audioLevel,
                    emotion = currentEmotion,
                    statusText = if (hasExternalChange) avatarState.statusText else activeAvatarState.statusText,
                    faceState = updatedFace
                )
                if (avatarState.audioLevel > 0.15f && !isSpecificEmotion) {
                    petController.notifyInteraction()
                }
            }
        } else {
            activeAvatarState = avatarState
        }
    }

    val infinite = rememberInfiniteTransition(label = "always_live")

    // Dynamic Ambient Gradient with Smooth Emotion Transitions
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
        AvatarEmotion.DEAD -> Color(0xFF070B14)
        AvatarEmotion.LAUGHING -> Color(0xFF041D1E)
        AvatarEmotion.MUSIC -> Color(0xFF041B26)
        AvatarEmotion.VR_MODE -> Color(0xFF100720)
        AvatarEmotion.DIVING -> Color(0xFF031620)
        AvatarEmotion.EVIL -> Color(0xFF2B0515)
        AvatarEmotion.FOCUSED -> Color(0xFF031A26)
        AvatarEmotion.SHY -> Color(0xFF240E1A)
        AvatarEmotion.DISGUSTED -> Color(0xFF12140D)
        AvatarEmotion.CAMERA_MODE -> Color(0xFF1A1208)
        AvatarEmotion.EATING -> Color(0xFF1F1206)
        AvatarEmotion.DRINKING -> Color(0xFF1F1604)
        // ─── 30 Additional Moodset ───
        AvatarEmotion.PUZZLED -> Color(0xFF031A26)
        AvatarEmotion.SICK -> Color(0xFF021F10)
        AvatarEmotion.RICH -> Color(0xFF261D02)
        AvatarEmotion.CRYING -> Color(0xFF03142B)
        AvatarEmotion.READING -> Color(0xFF120A24)
        AvatarEmotion.GAMING -> Color(0xFF021B24)
        AvatarEmotion.TRAVELING -> Color(0xFF021F15)
        AvatarEmotion.WORKING -> Color(0xFF0A182E)
        AvatarEmotion.COLD -> Color(0xFF021B24)
        AvatarEmotion.HOT -> Color(0xFF260D02)
        AvatarEmotion.DETECTIVE -> Color(0xFF1F1403)
        AvatarEmotion.COOKING -> Color(0xFF241203)
        AvatarEmotion.ART_MODE -> Color(0xFF240424)
        AvatarEmotion.SPACE -> Color(0xFF0A021F)
        AvatarEmotion.PARTY -> Color(0xFF240416)
        AvatarEmotion.DREAMING -> Color(0xFF06091F)
        AvatarEmotion.EXHAUSTED -> Color(0xFF0D1017)
        AvatarEmotion.ELECTRIC -> Color(0xFF021B24)
        AvatarEmotion.SNEAKY -> Color(0xFF0A0D12)
        AvatarEmotion.HERO -> Color(0xFF240404)
        AvatarEmotion.GLITCHED -> Color(0xFF240206)
        AvatarEmotion.MAGIC -> Color(0xFF1E0226)
        AvatarEmotion.SPORTY -> Color(0xFF240F02)
        AvatarEmotion.SCIENTIST -> Color(0xFF021F12)
        AvatarEmotion.SCARED -> Color(0xFF10141A)
        AvatarEmotion.WARRIOR -> Color(0xFF260502)
        AvatarEmotion.LOW_BATTERY -> Color(0xFF240202)
        AvatarEmotion.ROMANTIC -> Color(0xFF260517)
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
        AvatarEmotion.DEAD -> Color(0xFF0D162B)
        AvatarEmotion.LAUGHING -> Color(0xFF0A3A36)
        AvatarEmotion.MUSIC -> Color(0xFF08324A)
        AvatarEmotion.VR_MODE -> Color(0xFF240F45)
        AvatarEmotion.DIVING -> Color(0xFF082C3D)
        AvatarEmotion.EVIL -> Color(0xFF4A0822)
        AvatarEmotion.FOCUSED -> Color(0xFF08364C)
        AvatarEmotion.SHY -> Color(0xFF3B1228)
        AvatarEmotion.DISGUSTED -> Color(0xFF202615)
        AvatarEmotion.CAMERA_MODE -> Color(0xFF2E200C)
        AvatarEmotion.EATING -> Color(0xFF381F08)
        AvatarEmotion.DRINKING -> Color(0xFF382806)
        // ─── 30 Additional Moodset ───
        AvatarEmotion.PUZZLED -> Color(0xFF08364C)
        AvatarEmotion.SICK -> Color(0xFF053B1F)
        AvatarEmotion.RICH -> Color(0xFF453504)
        AvatarEmotion.CRYING -> Color(0xFF06254F)
        AvatarEmotion.READING -> Color(0xFF231445)
        AvatarEmotion.GAMING -> Color(0xFF063345)
        AvatarEmotion.TRAVELING -> Color(0xFF043B29)
        AvatarEmotion.WORKING -> Color(0xFF132D54)
        AvatarEmotion.COLD -> Color(0xFF08384A)
        AvatarEmotion.HOT -> Color(0xFF471A05)
        AvatarEmotion.DETECTIVE -> Color(0xFF382506)
        AvatarEmotion.COOKING -> Color(0xFF422106)
        AvatarEmotion.ART_MODE -> Color(0xFF420842)
        AvatarEmotion.SPACE -> Color(0xFF170642)
        AvatarEmotion.PARTY -> Color(0xFF420829)
        AvatarEmotion.DREAMING -> Color(0xFF0E153B)
        AvatarEmotion.EXHAUSTED -> Color(0xFF171E2B)
        AvatarEmotion.ELECTRIC -> Color(0xFF063547)
        AvatarEmotion.SNEAKY -> Color(0xFF131924)
        AvatarEmotion.HERO -> Color(0xFF420808)
        AvatarEmotion.GLITCHED -> Color(0xFF42050D)
        AvatarEmotion.MAGIC -> Color(0xFF380547)
        AvatarEmotion.SPORTY -> Color(0xFF421C04)
        AvatarEmotion.SCIENTIST -> Color(0xFF053D24)
        AvatarEmotion.SCARED -> Color(0xFF1F2633)
        AvatarEmotion.WARRIOR -> Color(0xFF470B04)
        AvatarEmotion.LOW_BATTERY -> Color(0xFF420404)
        AvatarEmotion.ROMANTIC -> Color(0xFF420827)
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
        AvatarEmotion.DEAD -> Color(0xFF03050C)
        AvatarEmotion.LAUGHING -> Color(0xFF020F0F)
        AvatarEmotion.MUSIC -> Color(0xFF020D15)
        AvatarEmotion.VR_MODE -> Color(0xFF080210)
        AvatarEmotion.DIVING -> Color(0xFF010C12)
        AvatarEmotion.EVIL -> Color(0xFF140108)
        AvatarEmotion.FOCUSED -> Color(0xFF020E15)
        AvatarEmotion.SHY -> Color(0xFF10030B)
        AvatarEmotion.DISGUSTED -> Color(0xFF080A04)
        AvatarEmotion.CAMERA_MODE -> Color(0xFF0D0802)
        AvatarEmotion.EATING -> Color(0xFF100802)
        AvatarEmotion.DRINKING -> Color(0xFF100C02)
        // ─── 30 Additional Moodset ───
        AvatarEmotion.PUZZLED -> Color(0xFF020E15)
        AvatarEmotion.SICK -> Color(0xFF011008)
        AvatarEmotion.RICH -> Color(0xFF120E01)
        AvatarEmotion.CRYING -> Color(0xFF010A14)
        AvatarEmotion.READING -> Color(0xFF090412)
        AvatarEmotion.GAMING -> Color(0xFF010E14)
        AvatarEmotion.TRAVELING -> Color(0xFF01100B)
        AvatarEmotion.WORKING -> Color(0xFF050B14)
        AvatarEmotion.COLD -> Color(0xFF010F14)
        AvatarEmotion.HOT -> Color(0xFF140701)
        AvatarEmotion.DETECTIVE -> Color(0xFF0F0A01)
        AvatarEmotion.COOKING -> Color(0xFF120901)
        AvatarEmotion.ART_MODE -> Color(0xFF120112)
        AvatarEmotion.SPACE -> Color(0xFF060112)
        AvatarEmotion.PARTY -> Color(0xFF12010B)
        AvatarEmotion.DREAMING -> Color(0xFF030510)
        AvatarEmotion.EXHAUSTED -> Color(0xFF06080D)
        AvatarEmotion.ELECTRIC -> Color(0xFF010E14)
        AvatarEmotion.SNEAKY -> Color(0xFF05070A)
        AvatarEmotion.HERO -> Color(0xFF120101)
        AvatarEmotion.GLITCHED -> Color(0xFF120103)
        AvatarEmotion.MAGIC -> Color(0xFF0E0114)
        AvatarEmotion.SPORTY -> Color(0xFF120801)
        AvatarEmotion.SCIENTIST -> Color(0xFF011009)
        AvatarEmotion.SCARED -> Color(0xFF0A0C10)
        AvatarEmotion.WARRIOR -> Color(0xFF140301)
        AvatarEmotion.LOW_BATTERY -> Color(0xFF120101)
        AvatarEmotion.ROMANTIC -> Color(0xFF14020C)
    }

    val animBg1 by animateColorAsState(targetBgColor1, animationSpec = tween(900, easing = EaseInOutCubic), label = "bg1")
    val animBg2 by animateColorAsState(targetBgColor2, animationSpec = tween(900, easing = EaseInOutCubic), label = "bg2")
    val animBg3 by animateColorAsState(targetBgColor3, animationSpec = tween(900, easing = EaseInOutCubic), label = "bg3")

    val auraColor = AvatarEmotionColors.primary(activeAvatarState.emotion)

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
        AvatarEmotion.DEAD -> "สลบ/หมดแรงแล้วว X X"
        AvatarEmotion.LAUGHING -> "ฮ่าๆ มีความสุขจังเลยย > <"
        AvatarEmotion.MUSIC -> "กำลังฟังเพลงเพลินๆ 🎧"
        AvatarEmotion.VR_MODE -> "โลกเสมือนจริง VR 🥽"
        AvatarEmotion.DIVING -> "กำลังดำน้ำบุ๋งๆ 🤿"
        AvatarEmotion.EVIL -> "แสบซนตัวร้าย คิคิ 😈"
        AvatarEmotion.FOCUSED -> "กำลังตั้งใจสแกนข้อมูล 🎯"
        AvatarEmotion.SHY -> "เขินจังเลยย ///"
        AvatarEmotion.DISGUSTED -> "แหวะ ไม่ชอบเลยย 🗑️"
        AvatarEmotion.CAMERA_MODE -> "ยิ้มหน่อยน้า 1 2 3 แชะ! 📷"
        AvatarEmotion.EATING -> "หม่ำๆ เบอร์เกอร์อร่อยจัง 🍔"
        AvatarEmotion.DRINKING -> "สดชื่นจัง ชนแก้ว! 🍺"
        // ─── 30 Additional Moodset ───
        AvatarEmotion.PUZZLED -> "สับสนจังเลยน้าา คลื่นมึนงง ~ ~ ❓"
        AvatarEmotion.SICK -> "ไม่สบายจังเลย วัดไข้แป๊บน้า 🌡️"
        AvatarEmotion.RICH -> "รวยเปย์หนักมาก รวยไม่ไหวแล้ว 💰✨"
        AvatarEmotion.CRYING -> "ฮือออ ร้องไห้หนักมากก 😭"
        AvatarEmotion.READING -> "กำลังอ่านหนังสือหาความรู้ 📖🤓"
        AvatarEmotion.GAMING -> "เล่นเกมมันส์มาก GG WP! 🎮🎧"
        AvatarEmotion.TRAVELING -> "ออกเดินทางท่องเที่ยวกันเถอะ ✈️🎒"
        AvatarEmotion.WORKING -> "กำลังตั้งใจทำงานอยู่น้า 💻☕"
        AvatarEmotion.COLD -> "หนาวจนสั่นสะท้านแล้วว 🥶❄️"
        AvatarEmotion.HOT -> "ร้อนตับแตก เหงื่อไหลเป็นน้ำ 🥵☀️"
        AvatarEmotion.DETECTIVE -> "มีความลับอะไรนะ ขอสืบหน่อย 🕵️‍♂️🔍"
        AvatarEmotion.COOKING -> "พร้อมทำอาหารอร่อยๆ ให้ทาน 👨‍🍳🍳"
        AvatarEmotion.ART_MODE -> "ปลดปล่อยพลังศิลปิน วาดรูปกัน 🎨🧑‍🎨"
        AvatarEmotion.SPACE -> "ท่องอวกาศสู่อนาคตอันไกลโพ้น 🚀🧑‍🚀"
        AvatarEmotion.PARTY -> "ปาร์ตี้เฉลิมฉลองกัน เย้! 🥳🎊"
        AvatarEmotion.DREAMING -> "กำลังฝันหวานอยู่น้าา ☁️🌙"
        AvatarEmotion.EXHAUSTED -> "หมดแรง ร่างทองไม่ไหวแล้ว 🫠😵"
        AvatarEmotion.ELECTRIC -> "พลังงานไฟฟ้าชาร์จเต็มพิกัด ⚡🔋"
        AvatarEmotion.SNEAKY -> "แอบย่องเงียบๆ ไม่มีใครเห็น 🦹‍♂️🤫"
        AvatarEmotion.HERO -> "ซูเปอร์ฮีโร่มาช่วยแล้ว! 🦸‍♂️✨"
        AvatarEmotion.GLITCHED -> "ระบบขัดข้อง ดิจิทัลกลิตช์ 👾⚡"
        AvatarEmotion.MAGIC -> "ร่ายเวทมนตร์ปิ๊งๆ เพี้ยง! 🧙‍♂️🪄"
        AvatarEmotion.SPORTY -> "ออกกำลังกาย สุขภาพแข็งแรง 🏀🏃"
        AvatarEmotion.SCIENTIST -> "กำลังทดลองสูตรลับในห้องแล็บ 🧪🔬"
        AvatarEmotion.SCARED -> "กลัวจังเลย ช่วยด้วยย 👻😱"
        AvatarEmotion.WARRIOR -> "นักรบพร้อมประจัญบาน! ⚔️🛡️"
        AvatarEmotion.LOW_BATTERY -> "แบตเตอรี่ใกล้หมด ช่วยชาร์จผมที 🪫⚠️"
        AvatarEmotion.ROMANTIC -> "โรแมนติก คาบดอกกุหลาบมาให้คุณ 🌹💋"
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

        val avatarSize = if (isLandscape) {
            minOf(screenMaxHeight * 0.65f, 240.dp)
        } else {
            minOf(screenMaxWidth * 0.62f, 240.dp)
        }
        val visualizerSize = avatarSize * 1.48f

        if (currentProfile == AlwaysLiveProfile.PET) {
            PetModeContent(
                activeAvatarState = activeAvatarState,
                onUpdateActiveAvatarState = { activeAvatarState = it },
                petController = petController,
                petVisionProcessor = petVisionProcessor,
                auraColor = auraColor,
                isLandscape = isLandscape,
                screenMaxWidth = screenMaxWidth,
                screenMaxHeight = screenMaxHeight,
                activeToolName = activeToolName,
                lastToolResult = lastToolResult,
                onDismissToolCard = onDismissToolCard,
                isDemoRunning = isDemoRunning,
                onToggleDemo = onToggleDemo,
                isMuted = isMuted,
                detectedObjects = detectedObjects,
                isCameraPipOpen = isCameraPipOpen,
                onCloseCameraPip = {
                    isCameraPipOpen = false
                    lastLiveFrameSendTime = 0L
                    PetVisionBridge.requestEyeOpen(false)
                },
                onToggleCameraPip = { isCameraPipOpen = !isCameraPipOpen },
                isFrontCam = isFrontCam,
                onSwitchCamera = { isFrontCam = !isFrontCam },
                lastLiveFrameSendTime = lastLiveFrameSendTime,
                onUpdateLastLiveFrameSendTime = { lastLiveFrameSendTime = it },
                onLiveVideoFrame = onLiveVideoFrame,
                onSelectProfile = onSelectProfile,
                onEndLive = onEndLive,
                currentTick = currentTick,
                lastFaceCoords = lastFaceCoords,
                lastFaceTime = lastFaceTime,
                lastShakeTime = lastShakeTime,
                isMissileBarrageActive = isMissileBarrageActive,
                onMissileBarrageFinished = { isMissileBarrageActive = false }
            )
        } else {
            DriveModeContent(
                activeAvatarState = activeAvatarState,
                connectionState = connectionState,
                currentTime = currentTime,
                currentProfile = currentProfile,
                onSelectProfile = onSelectProfile,
                auraColor = auraColor,
                emotionText = emotionText,
                isLandscape = isLandscape,
                avatarSize = avatarSize,
                visualizerSize = visualizerSize,
                auraPulse = auraPulse,
                ringRotation = ringRotation,
                ringPulse = ringPulse,
                micScale = micScale,
                isMuted = isMuted,
                isCameraActive = isCameraActive,
                onToggleMute = onToggleMute,
                onToggleCamera = onToggleCamera,
                onMinimize = onMinimize,
                onEndLive = onEndLive,
                onLiveVideoFrame = onLiveVideoFrame,
                lastLiveFrameSendTime = lastLiveFrameSendTime,
                onLastLiveFrameSendTimeChanged = { lastLiveFrameSendTime = it }
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Sub-Composables (Shared between Control/Drive Mode & General Live Views)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
internal fun TopStatusRow(
    connectionState: ConnectionState,
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
            is ConnectionState.Connected ->
                Triple("● LIVE", Color(0xFF69F0AE), Color(0xFF1B5E20).copy(alpha = 0.9f))
            is ConnectionState.Error ->
                Triple("● ERROR", Color(0xFFFF8A80), Color(0xFF5D1A1A).copy(alpha = 0.9f))
            is ConnectionState.Connecting,
            is ConnectionState.Reconnecting ->
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
internal fun StatusPill(
    emotionText: String,
    emotion: AvatarEmotion,
    modifier: Modifier = Modifier
) {
    val pillColor = AvatarEmotionColors.statusPill(emotion)

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

@Composable
internal fun ControlsRow(
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
internal fun AudioVisualizerRing(
    audioLevel: Float,
    emotion: AvatarEmotion,
    rotation: Float,
    pulse: Float,
    modifier: Modifier = Modifier
) {
    val ringColor = AvatarEmotionColors.primary(emotion)
    val secondaryColor = AvatarEmotionColors.secondary(emotion)

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
internal fun ControlButton(
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
internal fun ProfileSelectorRow(
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
internal fun ProfileSelectorItem(
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
internal fun DriveFeaturePanel(
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

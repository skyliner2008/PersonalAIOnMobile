package com.skyliner2008.jarvis.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skyliner2008.jarvis.avatar.AvatarActivity
import com.skyliner2008.jarvis.avatar.AvatarCards
import com.skyliner2008.jarvis.avatar.AvatarLiveFeed
import com.skyliner2008.jarvis.avatar.AvatarLookMapper
import com.skyliner2008.jarvis.avatar.AvatarSkits
import com.skyliner2008.jarvis.avatar.AvatarStage
import com.skyliner2008.jarvis.avatar.AvatarTasks
import com.skyliner2008.jarvis.avatar.ToolSceneSync
import com.skyliner2008.jarvis.avatar.ToolSceneWords
import com.skyliner2008.jarvis.camera.CameraPreviewView
import com.skyliner2008.jarvis.data.PendingAnswerQueue
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.avatar.ScreenDimmer
import com.skyliner2008.jarvis.pet.PetModeController
import com.skyliner2008.jarvis.pet.PetSelfCare
import com.skyliner2008.jarvis.pet.PetVisionProcessor
import com.skyliner2008.jarvis.pet.TouchZone
import com.skyliner2008.jarvis.sound.RobotSoundPlayer
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import com.skyliner2008.jarvis.ui.component.avatar.RobotSprite
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive

/**
 * Avatar mode — the Godot robot fullscreen, no buttons (Android back closes it). The pet brain
 * ([petController]) still owns the mood: touches on the robot, the camera's face tracking, shakes and
 * tilts (wired in AlwaysLiveScreen) and the AI's faces all go through it; this screen sends its state
 * to the robot, plays its skits, puts tool results on the robot's info scenes while the AI talks about
 * them ([ToolSceneSync]), shows it listening / thinking / at work between answers ([AvatarActivity],
 * from the live session's own state [voiceSession]), keeps the jobs the user waits to hear about on its task
 * list ([AvatarTasks]) and streams camera frames to the live session while the AI's eyes are open.
 */
@Composable
internal fun AvatarModeContent(
    activeAvatarState: AvatarState,
    voiceSession: AvatarState,
    petController: PetModeController,
    petVisionProcessor: PetVisionProcessor?,
    isAiVisionOn: Boolean,
    isFrontCam: Boolean,
    lastLiveFrameSendTime: Long,
    onUpdateLastLiveFrameSendTime: (Long) -> Unit,
    onLiveVideoFrame: ((String) -> Unit)?,
    onEndLive: () -> Unit,
    isMissileBarrageActive: Boolean,
    onMissileBarrageFinished: () -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val available = remember { AvatarStage.isAvailable }
    DisposableEffect(available) {
        if (available) AvatarStage.show()
        onDispose { if (available) AvatarStage.hide() }
    }
    val ready by AvatarStage.ready.collectAsState()
    val failed by AvatarStage.failed.collectAsState()
    val loadingAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (ready) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "avatar_loading_alpha"
    )
    val stateRef = rememberUpdatedState(activeAvatarState)
    val sessionRef = rememberUpdatedState(voiceSession)
    val endLive = rememberUpdatedState(onEndLive)
    val barrageDone = rememberUpdatedState(onMissileBarrageFinished)

    // ─── the look: emotion, eyes, gear, stage, lasting emote ───
    val look = remember(activeAvatarState.emotion, activeAvatarState.faceState, activeAvatarState.isDizzy) {
        AvatarLookMapper.look(activeAvatarState)
    }
    LaunchedEffect(look) { AvatarStage.sendState(look) }

    // a one-off effect as some emotions begin (confetti for a party …)
    var shownEmotion by remember { mutableStateOf<AvatarEmotion?>(null) }
    LaunchedEffect(activeAvatarState.emotion) {
        val e = activeAvatarState.emotion
        if (shownEmotion != null && e != shownEmotion) AvatarLookMapper.entryEffect(e)?.let { AvatarStage.effect(it) }
        shownEmotion = e
    }

    // gaze (app y grows downwards, the robot's upwards). A face that holds still is still there: say so
    // every second, or after app_bridge.gd LOOK_HOLD (2.5 s) the robot looks around by itself while the
    // user sits in front of it. (0, 0) is the camera's "nobody there": then it may look around.
    LaunchedEffect(activeAvatarState.gazeOffsetX, activeAvatarState.gazeOffsetY) {
        val x = activeAvatarState.gazeOffsetX
        val y = -activeAvatarState.gazeOffsetY
        AvatarStage.look(x, y)
        if (x != 0f || y != 0f) {
            while (true) {
                delay(1_000L)
                AvatarStage.look(x, y)
            }
        }
    }

    // the mouth follows the AI's voice (~15 updates a second)
    LaunchedEffect(Unit) {
        var lastSpeaking: Boolean? = null
        var lastSent = 0L
        snapshotFlow { stateRef.value.isSpeaking to stateRef.value.audioLevel }.collect { (speaking, level) ->
            val now = Clock.System.now().toEpochMilliseconds()
            if (speaking != lastSpeaking || (speaking && now - lastSent >= 66L)) {
                AvatarStage.voice(speaking, if (speaking) level else 0f)
                lastSpeaking = speaking
                lastSent = now
            }
        }
    }

    // the AI's face gestures and the idle screensaver trick become the robot's moves
    LaunchedEffect(activeAvatarState.faceState.gesture, activeAvatarState.faceState.sceneId) {
        AvatarLookMapper.gestureFidget(activeAvatarState.faceState.gesture)?.let { AvatarStage.fidget(it) }
    }
    LaunchedEffect(activeAvatarState.faceState.eyeTrick) {
        AvatarLookMapper.trickFidget(activeAvatarState.faceState.eyeTrick)?.let { AvatarStage.fidget(it) }
    }

    // skits the pet brain starts (its own, or the closest one for an old story scene); the scene's item goes with it
    // (the snack the robot eats: {"item": "pizza"})
    var playingSkit by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeAvatarState.faceState.sceneId, activeAvatarState.faceState.sceneName) {
        val sceneName = activeAvatarState.faceState.sceneName
        val skit = if (sceneName.isNotBlank()) AvatarSkits.forSceneName(sceneName) else null
        val playing = playingSkit
        val item = activeAvatarState.faceState.sceneItem
        when {
            skit != null -> {
                AvatarStage.playScene(skit.id, if (item.isNotBlank()) kotlinx.serialization.json.buildJsonObject {
                    put("item", JsonPrimitive(item))
                } else null)
                playingSkit = skit.id
            }
            // the scene ended, or a scene without a skit took over: its look shows at once (a tool's
            // info scene that took the stage meanwhile is not cut off)
            playing != null -> { AvatarStage.stopScene(playing); playingSkit = null }
        }
    }

    // fight back: the angry skit (rockets at the viewer) instead of the old missile overlay
    LaunchedEffect(isMissileBarrageActive) {
        if (isMissileBarrageActive) {
            AvatarStage.playScene(AvatarSkits.ANGRY.id)
            val started = Clock.System.now().toEpochMilliseconds()
            for (cue in AvatarSkits.ANGRY.cues) {
                val wait = cue.atMs - (Clock.System.now().toEpochMilliseconds() - started)
                if (wait > 0) delay(wait)
                RobotSoundPlayer.play(cue.sound)
            }
            delay((AvatarSkits.ANGRY.durationMs - (Clock.System.now().toEpochMilliseconds() - started)).coerceAtLeast(0L))
            barrageDone.value()
        }
    }

    // tool results on their info scenes (weather card, price, technical gauges, balance sheet …), following
    // the AI's voice: up while it talks about one, the next when it moves on, released when it stops.
    // Only results of this visit come (AvatarLiveFeed has no replay).
    val sceneSync = remember {
        ToolSceneSync(
            show = { AvatarStage.showTool(it.tool, it.data, hold = true) },
            release = { AvatarStage.releaseScene() }
        )
    }
    LaunchedEffect(sceneSync) {
        AvatarLiveFeed.events.collect { e ->
            val now = Clock.System.now().toEpochMilliseconds()
            when (e) {
                is AvatarLiveFeed.Result -> {
                    val data = e.data
                        ?: if (AvatarCards.wants(e.tool) && e.text.isNotBlank()) JsonPrimitive(AvatarCards.text(e.text)) else null
                    if (data == null) {
                        logDebug("AvatarStage", "tool ${e.tool}: no info scene for it")
                    } else {
                        logDebug("AvatarStage", "tool ${e.tool}: scene waits for the AI to ${if (e.later) "report it" else "talk about it"}")
                        val subject = PendingAnswerQueue.subject(e.text).orEmpty()
                        sceneSync.onResult(ToolSceneSync.Item(e.tool, data, ToolSceneWords.of(e.tool, data), now, e.later, subject), now)
                    }
                }
                is AvatarLiveFeed.Deferred -> {
                    logDebug("AvatarStage", "tool ${e.tool}: queued, its scene waits for the report")
                    sceneSync.onDeferred(e.tool, e.subject)
                }
                is AvatarLiveFeed.Report -> {
                    logDebug("AvatarStage", "tool ${e.tool}: handed to the AI, its scene comes with the first words")
                    sceneSync.onReport(e.tool, e.subject, now)
                }
            }
        }
    }
    LaunchedEffect(sceneSync) {
        AvatarLiveFeed.speech.collect { sceneSync.onSpeech(it.text, it.startMs, it.msPerUnit, Clock.System.now().toEpochMilliseconds()) }
    }
    LaunchedEffect(activeAvatarState.isSpeaking) {
        sceneSync.onSpeaking(activeAvatarState.isSpeaking, Clock.System.now().toEpochMilliseconds())
    }
    LaunchedEffect(sceneSync) {
        while (true) {
            delay(100L)
            sceneSync.tick(Clock.System.now().toEpochMilliseconds())
        }
    }

    // between answers: listening while the user talks, pondering until the AI answers, a "working" card while
    // a tool runs — never over a tool's scene (AvatarActivity)
    val activity = remember {
        AvatarActivity(
            play = { id, data -> AvatarStage.playScene(id, data) },
            stop = { AvatarStage.stopScene(it) },
            hear = { AvatarStage.hear(it) }
        )
    }
    LaunchedEffect(activity) {
        AvatarLiveFeed.heardAtMs.collect { if (it > 0L) activity.onHeard(it) }
    }
    // the jobs the user still waits to hear about (handed to the assistant, waiting to be told, being told)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.flow.combine(AvatarLiveFeed.jobs, AvatarLiveFeed.reports) { jobs, reports -> AvatarTasks.board(jobs, reports) }
            .collect { board ->
                logDebug("AvatarStage", "tasks ${board.joinToString { "${it.tool ?: "ask"}:${it.state.word}" }.ifEmpty { "-" }}")
                AvatarStage.tasks(AvatarTasks.json(board))
            }
    }
    LaunchedEffect(activity) {
        var last = AvatarActivity.Kind.NONE
        while (true) {
            delay(100L)
            val s = sessionRef.value
            val tool = AvatarTasks.foreground(AvatarLiveFeed.jobs.value)
            activity.update(
                nowMs = Clock.System.now().toEpochMilliseconds(),
                userTalking = s.isUserSpeaking,
                userLevel = s.audioLevel,
                aiSpeaking = s.isSpeaking,
                tool = tool,
                toolSceneUp = sceneSync.current != null,
                resultWaiting = sceneSync.hasWaiting
            )
            if (activity.current != last) {
                logDebug("AvatarStage", "activity ${last.name} → ${activity.current.name}${tool?.let { " (${it.tool})" } ?: ""}")
                last = activity.current
            }
            // the robot's idle life counts the quiet (PetSelfCare): a conversation starts it over, the user's voice wakes it
            petController.onAvatarTick(
                active = s.isUserSpeaking || s.isSpeaking || activity.current != AvatarActivity.Kind.NONE ||
                    sceneSync.current != null || AvatarLiveFeed.jobs.value.isNotEmpty() || AvatarLiveFeed.reports.value.isNotEmpty(),
                userVoice = s.isUserSpeaking
            )
        }
    }

    // the quiet on the screen (PetSelfCare): from 90 s the screen's eyelids sag and blink, at 120 s they shut and the
    // screen goes down to 1 %; a voice or a touch: the screen as bright as before and the eyelids open slowly
    DisposableEffect(Unit) {
        AvatarStage.eyelids(open = 1f, secs = 0f)
        onDispose {
            ScreenDimmer.restore()
            AvatarStage.eyelids(open = 1f, secs = 0f)
        }
    }
    LaunchedEffect(petController) {
        var last = PetSelfCare.Stage.AWAKE
        petController.idleStage.collectLatest { stage ->
            val from = last
            last = stage
            if (stage != from) logDebug("AvatarStage", "idle ${from.name} → ${stage.name}")
            when (stage) {
                PetSelfCare.Stage.AWAKE -> if (from != PetSelfCare.Stage.AWAKE) {
                    ScreenDimmer.restore()
                    AvatarStage.eyelids(open = 1f, secs = if (from == PetSelfCare.Stage.ASLEEP) 2.2f else 0.5f)
                }
                PetSelfCare.Stage.DROWSY -> Unit
                PetSelfCare.Stage.EYELIDS -> AvatarStage.eyelidsDrowsy(30f)
                PetSelfCare.Stage.ASLEEP -> {
                    AvatarStage.eyelids(open = 0f, secs = 1.5f)
                    delay(1_600L)                     // the eyelids shut first, then the screen dims
                    ScreenDimmer.dim(0.01f)
                }
            }
        }
    }

    // what the robot tells the user itself (the AI did not hear them, a fresh session is on its way)
    LaunchedEffect(Unit) {
        AvatarLiveFeed.notices.collect { AvatarStage.say(it, 3.5f) }
    }

    // the robot's events: touches go to the pet brain, back closes Avatar mode
    LaunchedEffect(petController) {
        AvatarStage.events.collect { e ->
            when (e.name) {
                "touch" -> petController.onAvatarTouch(e.text("kind"), e.text("zone"))
                "back" -> scope.launch { endLive.value() }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (!available || failed) {
            // no 3D stage (Android 10 or older, robot not packed, or its engine kept crashing): the robot's face
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RobotSprite(state = activeAvatarState, modifier = Modifier.size(220.dp))
                Text(
                    if (failed) "หุ่น 3D เปิดไม่ขึ้น — แสดงหน้าหุ่นแทน" else "หุ่น 3D ต้องใช้ Android 11 ขึ้นไป",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center
                )
            }
        } else if (loadingAlpha > 0f) {
            // Animated motion loading while 3D engine is booting:
            // The 2D RobotSprite floats, blinks, and syncs mouth immediately if AI greeting voice speaks!
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.alpha(loadingAlpha)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF00E5FF).copy(alpha = 0.15f), Color.Transparent)
                                ),
                                CircleShape
                            )
                    )
                    RobotSprite(state = activeAvatarState, modifier = Modifier.size(260.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF7EF7FF),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (activeAvatarState.isSpeaking) "กำลังพูดคุย (เตรียมเวที 3D)..." else "กำลังปลุกน้อง 3D...",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // the camera without a preview: face tracking for the gaze (not while asleep), and frames for the
        // live session while the AI's eyes are open
        val trackFaces = petVisionProcessor != null && activeAvatarState.emotion != AvatarEmotion.SLEEPING
        if (trackFaces || isAiVisionOn) {
            CameraPreviewView(
                modifier = Modifier.size(1.dp).alpha(0.001f).align(Alignment.TopStart).padding(0.dp),
                isFrontCamera = isFrontCam,
                isActive = true,
                onFrameCapture = { jpegBase64, rawBytes ->
                    petVisionProcessor?.processFrame(rawBytes, isFrontCamera = isFrontCam)
                    if (isAiVisionOn) {
                        val now = Clock.System.now().toEpochMilliseconds()
                        if (now - lastLiveFrameSendTime >= 900L) {
                            onUpdateLastLiveFrameSendTime(now)
                            onLiveVideoFrame?.invoke(jpegBase64)
                        }
                    }
                }
            )
        }
    }
}

/**
 * A touch on the robot (pet_godot app_bridge.gd: kind tap / double / stroke / scratch / hold / drag,
 * zone forehead / left_cheek / right_cheek / chin / face / body) as the pet brain's touch reaction.
 */
internal fun PetModeController.onAvatarTouch(kind: String, zone: String) {
    // any touch starts the quiet over; asleep or drowsy, the touch wakes it and that is all it does
    if (noteTouch()) return
    val cheek = if (zone == "left_cheek") TouchZone.LEFT_CHEEK else TouchZone.RIGHT_CHEEK
    val isCheek = zone == "left_cheek" || zone == "right_cheek"
    when (kind) {
        "tap" -> when {
            zone == "forehead" -> onPetHead(TouchZone.FOREHEAD)
            zone == "chin" -> onChinScratch()
            isCheek -> onPoke(cheek)
            zone == "face" || zone == "body" -> onPat()
        }
        "double" -> if (isCheek) onTickle(cheek) else onPetHead(TouchZone.FOREHEAD)
        "stroke", "hold" -> onPetHead(TouchZone.FOREHEAD)
        "scratch" -> onChinScratch()
        "drag" -> notifyInteraction()
    }
}

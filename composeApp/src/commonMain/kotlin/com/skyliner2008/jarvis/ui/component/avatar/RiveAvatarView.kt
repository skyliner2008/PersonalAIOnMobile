package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.skyliner2008.jarvis.sound.RobotSoundPlayer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlinx.datetime.Clock

/**
 * Renders the Rive pet (`R.raw.avatar`) and writes [inputs] into its view model.
 * On Android this is a RiveAnimationView bound with autoBind; where the native
 * runtime is unavailable it falls back to the Compose Canvas pet using [fallbackState].
 */
@Composable
expect fun RiveAvatarView(
    modifier: Modifier = Modifier,
    inputs: RiveAvatarInputs,
    fallbackState: AvatarState
)

/** Whether this platform ships the native Rive runtime at all. */
expect val isRiveRuntimeSupported: Boolean

/**
 * Runtime health of the Rive engine. When the native library fails to load the
 * Android view flips [failed] and falls back to Canvas; the pet screen then stops
 * routing props / backgrounds into Rive so nothing disappears.
 */
object RiveRuntime {
    var failed by mutableStateOf(false)

    val isActive: Boolean get() = isRiveRuntimeSupported && !failed
}

/**
 * The Rive pet driven by the same signals as the Canvas pet.
 *
 * [RiveAvatarMapper.plan] turns the current [state] into channel values; this
 * composable layers the time-based parts on top, because Rive stories and
 * reactions are one-shots the host has to fire and clear:
 *  - a touch ([reaction]) → `react`, cleared after its duration
 *  - a heavy shake that ended in anger ([lastShakeTime]) → `react = ShakeAngry`
 *  - becoming SURPRISED without a touch (loud noise, table thump) → `react = Startled`
 *  - a face appearing after an absence ([lastFaceTime]) → `state = Detected`
 *  - the missile barrage → `seq = AngryMissile`
 */
@Composable
fun RivePetAvatar(
    state: AvatarState,
    modifier: Modifier = Modifier,
    reaction: RiveReactionEvent? = null,
    isMissileBarrageActive: Boolean = false,
    lastShakeTime: Long = 0L,
    lastFaceTime: Long = 0L,
    /** เล่นเรื่องสั้นตามอารมณ์ (seq 82–100) และเรื่องว่างงานเอง */
    moodStoriesEnabled: Boolean = true
) {
    val latestState by rememberUpdatedState(state)
    var react by remember { mutableStateOf(RiveReact.NONE) }
    var reactToken by remember { mutableStateOf(0) }
    var lastTouchAt by remember { mutableStateOf(0L) }
    var isDetected by remember { mutableStateOf(false) }
    var previousFaceTime by remember { mutableStateOf(lastFaceTime) }

    suspend fun fire(kind: Int) {
        if (kind == RiveReact.NONE) return
        // write 0 for a frame first so the same reaction can replay from frame 0
        react = RiveReact.NONE
        withFrameNanos { }
        react = kind
        reactToken++
    }

    // Clearing lives in its own effect keyed by the token: whatever started the
    // reaction may be cancelled (the emotion moved on), the reaction still ends.
    LaunchedEffect(reactToken) {
        if (reactToken == 0) return@LaunchedEffect
        val kind = react
        delay(RiveReact.durationMs(kind))
        if (react == kind) react = RiveReact.NONE
    }

    LaunchedEffect(reaction?.id) {
        val event = reaction ?: return@LaunchedEffect
        lastTouchAt = Clock.System.now().toEpochMilliseconds()
        // the state machine updates the emotion in the same frame as the touch;
        // wait one frame so escalation (pout / angry) is visible here
        withFrameNanos { }
        fire(RiveAvatarMapper.reactFor(event.gesture, event.fromLeft, latestState.effectiveEmotion()))
    }

    val initialShakeTime = remember { lastShakeTime }
    LaunchedEffect(lastShakeTime) {
        if (lastShakeTime <= 0L || lastShakeTime == initialShakeTime) return@LaunchedEffect
        withFrameNanos { }
        val emotion = latestState.effectiveEmotion()
        if (emotion == AvatarEmotion.ANGRY || emotion == AvatarEmotion.ENRAGED) {
            fire(RiveReact.SHAKE_ANGRY)
        }
    }

    val effective = state.effectiveEmotion()
    var sawFirstEmotion by remember { mutableStateOf(false) }
    LaunchedEffect(effective) {
        val isFirst = !sawFirstEmotion
        sawFirstEmotion = true
        val sinceTouch = Clock.System.now().toEpochMilliseconds() - lastTouchAt
        // the Shocked mood story plays the startle itself when it is allowed to run
        if (!isFirst && effective == AvatarEmotion.SURPRISED && sinceTouch > 800L && !moodStoriesEnabled) {
            fire(RiveReact.STARTLED)
        }
    }

    // lastFaceTime ticks on every detected frame, so this effect restarts constantly:
    // it only raises the flag; a token-keyed effect lowers it
    var detectedToken by remember { mutableStateOf(0) }
    LaunchedEffect(lastFaceTime) {
        val gap = lastFaceTime - previousFaceTime
        previousFaceTime = lastFaceTime
        if (lastFaceTime > 0L && gap > 10_000L) {
            isDetected = true
            detectedToken++
        }
    }
    LaunchedEffect(detectedToken) {
        if (detectedToken == 0) return@LaunchedEffect
        delay(RiveState.DETECTED_MS)
        isDetected = false
    }

    // a new scene (even the same one again) must restart its story from frame 0:
    // hold seq at 0 for a frame whenever sceneId changes
    val sceneId = state.faceState.sceneId
    var sceneRestarting by remember { mutableStateOf(false) }
    LaunchedEffect(sceneId) {
        if (sceneId == 0) return@LaunchedEffect
        sceneRestarting = true
        withFrameNanos { }
        withFrameNanos { }
        sceneRestarting = false
    }

    // ---- jelly mood stories: a 7 s story when the mood changes, idle play when bored ----
    var moodSeq by remember { mutableStateOf(RiveSeq.NONE) }
    var lastMood by remember { mutableStateOf<RiveMoodStory?>(null) }
    val moodPlayedAt = remember { mutableMapOf<Int, Long>() }

    fun moodBlocked(): Boolean {
        val s = latestState
        return !moodStoriesEnabled || s.faceState.sceneName.isNotBlank() || isMissileBarrageActive ||
            s.isSpeaking || s.isDizzy
    }

    suspend fun playMood(story: RiveMoodStory) {
        // a touch reaction outranks a story in the rig; let it finish first
        var waited = 0L
        while (react != RiveReact.NONE && waited < 6_000L) {
            delay(100L); waited += 100L
        }
        if (moodBlocked()) return
        val now = Clock.System.now().toEpochMilliseconds()
        moodPlayedAt[story.seq] = now
        lastMood = story
        try {
            moodSeq = RiveSeq.NONE
            withFrameNanos { }
            moodSeq = story.seq
            coroutineScope {
                launch {
                    var elapsed = 0L
                    for (cue in story.cues) {
                        delay(cue.atMs - elapsed)
                        elapsed = cue.atMs
                        if (!moodBlocked()) RobotSoundPlayer.play(cue.sound)
                    }
                }
                delay(story.durationMs + RiveMoodStories.HOLD_MS)
            }
        } finally {
            if (moodSeq == story.seq) moodSeq = RiveSeq.NONE
        }
    }

    var sawFirstMood by remember { mutableStateOf(false) }
    LaunchedEffect(effective) {
        val isFirst = !sawFirstMood
        sawFirstMood = true
        if (isFirst) return@LaunchedEffect
        val now = Clock.System.now().toEpochMilliseconds()
        val variants = RiveMoodStories.variantsFor(effective).filter {
            now - (moodPlayedAt[it.seq] ?: 0L) > RiveMoodStories.REPEAT_COOLDOWN_MS
        }
        val story = RiveMoodStories.pick(variants, lastMood) ?: return@LaunchedEffect
        withFrameNanos { }            // the touch that changed the mood fires its react this frame
        playMood(story)
    }

    LaunchedEffect(effective == AvatarEmotion.IDLE) {
        if (effective != AvatarEmotion.IDLE) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(RiveMoodStories.IDLE_MIN_MS, RiveMoodStories.IDLE_MAX_MS))
            if (moodSeq == RiveSeq.NONE && react == RiveReact.NONE && !moodBlocked()) {
                RiveMoodStories.pick(RiveMoodStories.IDLE_VARIANTS, lastMood)?.let { playMood(it) }
            }
        }
    }

    val base = RiveAvatarMapper.plan(state).inputs

    // jelly: gaze and tilt follow an under-damped spring, so a look overshoots a
    // touch and settles back instead of snapping stiffly to the target
    val jelly = spring<Float>(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow)
    val gazeX by animateFloatAsState(base.gazeX, jelly, label = "rive_gaze_x")
    val gazeY by animateFloatAsState(base.gazeY, jelly, label = "rive_gaze_y")
    val tiltX by animateFloatAsState(base.tiltX, jelly, label = "rive_tilt_x")
    val tiltY by animateFloatAsState(base.tiltY, jelly, label = "rive_tilt_y")

    val inputs = base.copy(
        gazeX = gazeX.coerceIn(-1.15f, 1.15f),
        gazeY = gazeY.coerceIn(-1.15f, 1.15f),
        tiltX = tiltX.coerceIn(-1.15f, 1.15f),
        tiltY = tiltY.coerceIn(-1.15f, 1.15f),
        react = if (base.seq != RiveSeq.NONE) RiveReact.NONE else react,
        seq = when {
            sceneRestarting -> RiveSeq.NONE
            base.seq != RiveSeq.NONE -> base.seq
            isMissileBarrageActive -> RiveSeq.ANGRY_MISSILE
            moodSeq != RiveSeq.NONE -> moodSeq
            else -> RiveSeq.NONE
        },
        state = if (isDetected && base.state == RiveState.IDLE) RiveState.DETECTED else base.state
    )

    // distance scaling (face close to the camera → bigger eyes), like the Canvas pet
    val faceScale by animateFloatAsState(
        targetValue = state.faceScaleFactor.coerceIn(0.8f, 1.35f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rive_face_scale"
    )

    RiveAvatarView(
        modifier = modifier.graphicsLayer {
            scaleX = faceScale
            scaleY = faceScale
        },
        inputs = inputs,
        fallbackState = state
    )
}

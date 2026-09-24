package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.camera.BoundingBox
import com.skyliner2008.jarvis.camera.DetectedObject
import com.skyliner2008.jarvis.pet.PetModeController
import com.skyliner2008.jarvis.pet.PetMotionClassifier
import com.skyliner2008.jarvis.pet.PetMotionEvent
import com.skyliner2008.jarvis.pet.PetNeedsState
import com.skyliner2008.jarvis.pet.PetVisionTargetTracker
import com.skyliner2008.jarvis.ui.component.avatar.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the pet eye-drawing / motion audit (2026-09-24):
 * gaze anchors, loop continuity, slant limits, sensor classification and emotion timers.
 */
class PetEyeMotionAuditTest {

    // ─── 1. Eye anchors: gaze moves the whole eye on X and Y, sockets only follow the head ───

    private fun kinematics(gazeX: Float, gazeY: Float, yaw: Float = 0f, pitch: Float = 0f) =
        VirtualHeadKinematics.calculate(
            headCenterX = 500f, headCenterY = 400f, baseSpacing = 200f, eyeDiameter = 200f,
            headYaw = yaw, headPitch = pitch, pupilGazeX = gazeX, pupilGazeY = gazeY
        )

    @Test
    fun `lookCenter follows gaze on both axes while the socket stays put`() {
        val rest = kinematics(0f, 0f)
        val looking = kinematics(1f, 1f)

        assertEquals(rest.leftEye.backCenter, looking.leftEye.backCenter)
        assertEquals(rest.rightEye.backCenter, looking.rightEye.backCenter)

        val dx = looking.leftEye.lookCenter.x - rest.leftEye.lookCenter.x
        val dy = looking.leftEye.lookCenter.y - rest.leftEye.lookCenter.y
        assertEquals(200f * EyeMetrics.PUPIL_TRAVEL_X, dx, 0.01f)
        assertEquals(200f * EyeMetrics.PUPIL_TRAVEL_Y, dy, 0.01f)
    }

    @Test
    fun `lookCenter equals the socket at rest and front disc minus the resting offset`() {
        val rest = kinematics(0f, 0f)
        assertEquals(rest.leftEye.backCenter, rest.leftEye.lookCenter)

        val k = kinematics(0.4f, -0.6f, yaw = 0.3f, pitch = 0.2f)
        val restOffsetX = 200f * EyeMetrics.REST_OFFSET_X
        val restOffsetY = 200f * EyeMetrics.REST_OFFSET_Y
        assertEquals(k.leftEye.frontCenter.x + restOffsetX, k.leftEye.lookCenter.x, 0.01f)
        assertEquals(k.leftEye.frontCenter.y + restOffsetY, k.leftEye.lookCenter.y, 0.01f)
    }

    // ─── 2. Living motion: every derived cycle is continuous when its loop restarts ───

    private fun living(phase: Float) = livingMotionAt(
        breathPhase = 0f, headSwayDeg = 0f, saccadeTime = phase, microBlinkFactor = 1f,
        loopFast = phase, loopMedium = phase, loopSlow = phase, loopWink = phase,
        loopLaugh = phase, loopRead = phase, loopPant = phase, loopParty = phase,
        pulseRaw = 0f, sparkleRot = phase * 360f, flickerPhase = 1f,
        chewCycle = phase, gulpCycle = phase, popPhase = phase
    )

    @Test
    fun `living motion values match at loop start and loop end`() {
        val start = living(0f)
        val end = living(1f)
        val pairs = listOf(
            "laughBounceY" to (start.laughBounceY to end.laughBounceY),
            "laughSquint" to (start.laughSquint to end.laughSquint),
            "readingScanX" to (start.readingScanX to end.readingScanX),
            "pantHeaveY" to (start.pantHeaveY to end.pantHeaveY),
            "partyHornProg" to (start.partyHornProg to end.partyHornProg),
            "winkEyeScaleY" to (start.winkEyeScaleY to end.winkEyeScaleY),
            "winkSparkleScale" to (start.winkSparkleScale to end.winkSparkleScale),
            "heartbeatScale" to (start.heartbeatScale to end.heartbeatScale),
            "nodOffOffsetY" to (start.nodOffOffsetY to end.nodOffOffsetY),
            "angerJitterX" to (start.angerJitterX to end.angerJitterX),
            "shiverFastX" to (start.shiverFastX to end.shiverFastX),
            "saccadeOffsetX" to (start.saccadeOffsetX to end.saccadeOffsetX)
        )
        for ((name, v) in pairs) {
            assertEquals(v.first, v.second, 0.02f, "$name jumps when its loop restarts")
        }
    }

    @Test
    fun `loopFrac is continuous across whole cycles for any speed`() {
        for (speed in listOf(0.9f, 1.37f, -1.23f, 2.5f)) {
            for (n in listOf(1.0, 17.0, 12_345.0)) {
                val before = loopFrac(0.3f, n - 1e-4, speed)
                val after = loopFrac(0.3f, n + 1e-4, speed)
                val d = abs(before - after)
                assertTrue(minOf(d, 1f - d) < 0.01f, "speed=$speed n=$n jumped by $d")
            }
        }
    }

    // ─── 3. Eye shapes ───

    @Test
    fun `angry slant never exceeds 45 percent of the eye height`() {
        assertEquals(16f, angryEyeSlant(16f, eyeHeight = 100f, slantProgress = 1f), 0.001f)
        // small eye + over-driven twitch used to push the top edge below the bottom edge
        assertEquals(9f, angryEyeSlant(16f, eyeHeight = 20f, slantProgress = 1.35f), 0.001f)
        assertEquals(0f, angryEyeSlant(16f, eyeHeight = 20f, slantProgress = -0.5f), 0.001f)
    }

    // ─── 4. Face-state parsing ───

    @Test
    fun `eye trick names accept spaces dashes and mixed case`() {
        assertEquals(EyeTrickState.PING_PONG_BOUNCE, RobotFaceState(eyeTrickName = "ping pong bounce").eyeTrick)
        assertEquals(EyeTrickState.SNOOKER_SHOT, RobotFaceState(eyeTrickName = " Snooker-Shot ").eyeTrick)
        assertEquals(EyeTrickState.NONE, RobotFaceState(eyeTrickName = "not a trick").eyeTrick)
        assertEquals(GestureType.TILT_LEFT, RobotFaceState(gestureName = "tilt left").gesture)
    }

    @Test
    fun `every eye style resolves to an emotion and crying eyes show the crying face`() {
        for (style in EyeStyle.entries) {
            AvatarState(faceState = RobotFaceState(eyeStyleName = style.name.lowercase())).effectiveEmotion()
        }
        val crying = AvatarState(faceState = RobotFaceState(eyeStyleName = "crying"))
        assertEquals(AvatarEmotion.CRYING, crying.effectiveEmotion())
    }

    // ─── 5. Motion sensor classification ───

    private val g = PetMotionClassifier.GRAVITY

    @Test
    fun `one shake fires once and spikes during the cooldown are ignored`() {
        val c = PetMotionClassifier()
        val events = mutableListOf<PetMotionEvent>()
        // a vigorous shake: a spike every 60 ms for 1.2 s
        var t = 1_000L
        while (t <= 2_200L) {
            events += c.onSample(3f * g, 0f, 0f, t)
            t += 60L
        }
        val shakes = events.count { it == PetMotionEvent.SHAKE || it == PetMotionEvent.HEAVY_SHAKE }
        // one shake per ~0.96 s (2 spikes + 900 ms cooldown), not one every other sample
        assertTrue(shakes in 1..2, "expected 1-2 shakes, got $shakes: $events")
    }

    @Test
    fun `repeated shakes within the window escalate to a heavy shake`() {
        val c = PetMotionClassifier()
        val events = mutableListOf<PetMotionEvent>()
        for (burst in 0 until 3) {
            val t0 = 10_000L + burst * 1_200L
            events += c.onSample(3f * g, 0f, 0f, t0)
            events += c.onSample(3f * g, 0f, 0f, t0 + 50L)
        }
        assertEquals(listOf(PetMotionEvent.SHAKE, PetMotionEvent.SHAKE, PetMotionEvent.HEAVY_SHAKE), events)
    }

    @Test
    fun `a stale face-down start time cannot make a brief flip fire sleep`() {
        val c = PetMotionClassifier()
        // brief face-down, then held at a steep angle (between the face-down and face-up thresholds)
        assertTrue(c.onSample(0f, 0f, -9.5f, 0L).isEmpty())
        assertTrue(c.onSample(0f, 0f, -5f, 100L).isEmpty())
        assertTrue(c.onSample(0f, 0f, -5f, 5_000L).isEmpty())
        // a brief face-down much later must wait for its own hold time
        assertTrue(c.onSample(0f, 0f, -9.5f, 6_000L).isEmpty(), "fired immediately from a stale start time")
        assertTrue(c.onSample(0f, 0f, -9.5f, 6_200L).isEmpty())
        assertEquals(listOf(PetMotionEvent.FACE_DOWN), c.onSample(0f, 0f, -9.5f, 6_500L))
        assertEquals(listOf(PetMotionEvent.FACE_UP), c.onSample(0f, 0f, 9.8f, 7_000L))
    }

    @Test
    fun `table thump needs the phone to lie still first`() {
        val c = PetMotionClassifier()
        // lying still for 1 s at 1 G, then a sharp impulse
        var t = 0L
        while (t <= 1_000L) {
            assertTrue(c.onSample(0f, 0f, g, t).isEmpty())
            t += 60L
        }
        assertEquals(listOf(PetMotionEvent.TABLE_THUMP), c.onSample(0f, 0f, 2.3f * g, t))

        // a phone that was just moving (not still long enough) does not count
        val moving = PetMotionClassifier()
        moving.onSample(0f, 0f, g, 0L)
        moving.onSample(0f, 0f, g, 200L)
        assertTrue(moving.onSample(0f, 0f, 2.3f * g, 260L).none { it == PetMotionEvent.TABLE_THUMP })
    }

    // ─── 6. Needs & controller ───

    @Test
    fun `a long frozen gap decays like time away instead of hours awake`() {
        val start = PetNeedsState(satiety = 60f, energy = 60f, hygiene = 60f)
        val eightHours = 8 * 60 * 60 * 1000L
        val live = start.decayLive(eightHours)
        assertTrue(live.satiety >= PetNeedsState.OFFLINE_SATIETY_FLOOR, "satiety=${live.satiety}")
        assertTrue(live.hygiene >= PetNeedsState.OFFLINE_HYGIENE_FLOOR, "hygiene=${live.hygiene}")
        // a normal 20 s tick is unchanged
        assertEquals(start.decay(20_000L), start.decayLive(20_000L))
    }

    @Test
    fun `camera gaze updates keep the measured face distance`() {
        var state = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { state },
            onUpdateAvatarState = { state = it }
        )
        controller.onFaceDistance(1.2f)
        controller.onGazeTouch(0.3f, -0.2f)
        assertEquals(1.2f, state.faceScaleFactor)
        assertEquals(0.3f, state.gazeOffsetX)
        scope.cancel()
    }

    @Test
    fun `an older wink timer does not end a newer wink`() = runBlocking {
        var state = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { state },
            onUpdateAvatarState = { state = it }
        )
        controller.onWink()                 // reverts at ~2200 ms
        delay(1_500)
        controller.onWink()                 // reverts at ~3700 ms
        delay(1_000)                        // t ≈ 2500 ms: the first timer has fired
        assertEquals(AvatarEmotion.WINK, state.emotion)
        delay(1_600)                        // t ≈ 4100 ms
        assertEquals(AvatarEmotion.IDLE, state.emotion)
        scope.cancel()
    }

    // ─── 7. Vision tracker ───

    @Test
    fun `a face that jumps too far replaces its old track instead of duplicating it`() {
        val tracker = PetVisionTargetTracker()
        fun face(x: Float) = DetectedObject(label = "Face", confidence = 0.9f, boundingBox = BoundingBox(x, 0.3f, 0.2f, 0.2f), color = "#00FF00")
        tracker.processFrame(listOf(face(0.05f)), currentTimeMs = 0L)
        val out = tracker.processFrame(listOf(face(0.75f)), currentTimeMs = 100L)
        assertEquals(1, out.size, "two face boxes while the old track was still alive")
        assertEquals(0.75f, out.single().boundingBox!!.x, 0.001f)
    }
}

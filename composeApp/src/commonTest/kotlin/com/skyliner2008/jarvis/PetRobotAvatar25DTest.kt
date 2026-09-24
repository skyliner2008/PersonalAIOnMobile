package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.ui.component.avatar.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PetRobotAvatar25DTest — Unit tests for 2.5D Face-Tracking Avatar & Parametric Emotion Morphing
 */
class PetRobotAvatar25DTest {

    // ─── 1. GazeStabilizer Tests ─────────────────────────────────────────────

    @Test
    fun `GazeStabilizer suppresses small jitter noise below threshold`() {
        val stabilizer = GazeStabilizer(jitterThreshold = 0.035f)

        // Seed with steady coordinates until converged
        repeat(20) {
            stabilizer.update(0.30f, 0.40f, 0.25f, 1000L)
        }
        val initialX = stabilizer.smoothedX
        val initialY = stabilizer.smoothedY

        // Small jitter (±0.02, below threshold 0.035)
        val (jitteredX, jitteredY) = stabilizer.update(0.32f, 0.42f, 0.25f, 1080L)

        // Change should be heavily attenuated by low alpha (0.08)
        val diffX = kotlin.math.abs(jitteredX - initialX)
        val diffY = kotlin.math.abs(jitteredY - initialY)
        assertTrue(diffX < 0.005f, "Jitter on X should be suppressed, got diff $diffX")
        assertTrue(diffY < 0.005f, "Jitter on Y should be suppressed, got diff $diffY")
    }

    @Test
    fun `GazeStabilizer responds quickly to large intentional head movement`() {
        val stabilizer = GazeStabilizer(jitterThreshold = 0.035f)
        stabilizer.update(0.0f, 0.0f, 0.25f, 1000L)

        // Large movement (> 0.15)
        val (movedX, movedY) = stabilizer.update(0.80f, -0.60f, 0.25f, 1080L)

        // With alpha = 0.45, should move significantly towards target in a single step
        assertTrue(movedX > 0.30f, "Should move quickly towards target X, got $movedX")
        assertTrue(movedY < -0.20f, "Should move quickly towards target Y, got $movedY")
    }

    @Test
    fun `GazeStabilizer clamps smoothed output within minus 1 to 1`() {
        val stabilizer = GazeStabilizer()
        val (clampedX, clampedY) = stabilizer.update(2.5f, -3.2f, 0.25f, 1000L)
        assertTrue(clampedX <= 1.0f)
        assertTrue(clampedY >= -1.0f)
    }

    @Test
    fun `GazeStabilizer computes face distance scaling factor correctly`() {
        val stabilizer = GazeStabilizer()

        // Normal distance: faceWidthRatio <= 0.38f -> targetScale = 1.0f
        stabilizer.update(0f, 0f, faceWidthRatio = 0.22f, currentTimeMs = 1000L)
        assertEquals(1.0f, stabilizer.faceScaleFactor, 0.05f)

        // Close to camera: faceWidthRatio = 0.60f -> targetScale > 1.0f
        for (step in 1..10) {
            stabilizer.update(0f, 0f, faceWidthRatio = 0.60f, currentTimeMs = 1000L + step * 80L)
        }
        assertTrue(stabilizer.faceScaleFactor > 1.05f, "Face scale factor should expand when close to camera, got ${stabilizer.faceScaleFactor}")
    }

    @Test
    fun `GazeStabilizer tracks face absence timeout to BORED and SLEEPING with one-shot edge triggering`() {
        val stabilizer = GazeStabilizer(
            idleBoredTimeoutMs = 12_000L,
            idleSleepTimeoutMs = 25_000L
        )

        // Face seen at t = 1000L
        stabilizer.update(0.2f, -0.2f, 0.25f, 1000L)

        // No face at t = 6000L (5s elapsed < 12s) -> returns null
        val emotion5s = stabilizer.onNoFace(6000L)
        assertNull(emotion5s, "Should not trigger emotion before 12s timeout")

        // No face at t = 14000L (13s elapsed > 12s) -> returns BORED on edge
        val emotion13s = stabilizer.onNoFace(14000L)
        assertEquals(AvatarEmotion.BORED, emotion13s)

        // Next frame at t = 14080L -> must return null (do NOT spam BORED every frame)
        assertNull(stabilizer.onNoFace(14080L), "Should not spam BORED on subsequent frames")

        // No face at t = 27000L (26s elapsed > 25s) -> returns SLEEPING on edge
        val emotion26s = stabilizer.onNoFace(27000L)
        assertEquals(AvatarEmotion.SLEEPING, emotion26s)

        // Next frame at t = 27080L -> must return null (do NOT spam SLEEPING every frame)
        assertNull(stabilizer.onNoFace(27080L), "Should not spam SLEEPING on subsequent frames")

        // User interacts -> resetAbsenceTimer called at t = 30000L
        stabilizer.resetAbsenceTimer(30000L)
        assertNull(stabilizer.onNoFace(35000L), "After resetAbsenceTimer, should not trigger before timeout")

        // After new absence duration (t = 43000L, 13s since reset) -> returns BORED again
        assertEquals(AvatarEmotion.BORED, stabilizer.onNoFace(43000L))
    }

    @Test
    fun `GazeStabilizer reset restores default state`() {
        val stabilizer = GazeStabilizer()
        stabilizer.update(0.8f, 0.8f, 0.55f, 1000L)
        stabilizer.reset()

        assertEquals(0f, stabilizer.smoothedX)
        assertEquals(0f, stabilizer.smoothedY)
        assertEquals(1f, stabilizer.faceScaleFactor)
        assertNull(stabilizer.onNoFace(50000L), "After reset without seeing face, onNoFace should be null")
    }

    // ─── 2. AvatarLayoutInfo & calculateAvatarLayout Tests ───────────────────

    @Test
    fun `calculateAvatarLayout computes correct geometry for Landscape`() {
        val width = 800f
        val height = 480f
        val layout = calculateAvatarLayout(width, height)

        assertTrue(layout.isLandscape)
        assertEquals(400f, layout.cX)
        assertEquals(480f * 0.42f, layout.cY, 0.01f) // Rule of thirds eye line at 42%

        // In landscape: minOf(height * 0.50f, width * 0.28f) = minOf(240, 224) = 224f
        val expectedEyeDiameter = minOf(height * 0.50f, width * 0.28f)
        assertEquals(expectedEyeDiameter, layout.eyeDiameter, 0.01f)

        // Geometry positioning
        assertTrue(layout.foreheadY < layout.cY, "Forehead should be above center")
        assertTrue(layout.mouthY > layout.cY, "Mouth should be below center")
        assertTrue(layout.chinY > layout.mouthY, "Chin should be below mouth")
        assertTrue(layout.leftTempleX < layout.cX, "Left temple should be left of center")
        assertTrue(layout.rightTempleX > layout.cX, "Right temple should be right of center")
    }

    @Test
    fun `calculateAvatarLayout computes correct geometry for Portrait`() {
        val width = 360f
        val height = 720f
        val layout = calculateAvatarLayout(width, height)

        assertFalse(layout.isLandscape)
        assertEquals(180f, layout.cX)
        assertEquals(720f * 0.40f, layout.cY, 0.01f) // Rule of thirds eye line at 40%

        // In portrait: minOf(width * 0.24f, height * 0.20f)
        val expectedEyeDiameter = minOf(width * 0.24f, height * 0.20f)
        assertEquals(expectedEyeDiameter, layout.eyeDiameter, 0.01f)

        // 1.2x Spacing: baseSpacing = width / 5f (distance between eye centers = 0.40f * width)
        assertEquals(width / 5f, layout.baseSpacing, 0.01f)
        assertEquals(width * 0.30f, layout.cX - layout.baseSpacing, 0.01f)
        assertEquals(width * 0.70f, layout.cX + layout.baseSpacing, 0.01f)
    }

    @Test
    fun `calculateAvatarLayout respects isLandscapeOverride`() {
        val width = 800f
        val height = 480f
        val layoutForcedPortrait = calculateAvatarLayout(width, height, isLandscapeOverride = false)
        assertFalse(layoutForcedPortrait.isLandscape)
        assertEquals(width / 5f, layoutForcedPortrait.baseSpacing, 0.01f)

        val layoutLandscape = calculateAvatarLayout(width, height, isLandscapeOverride = true)
        assertTrue(layoutLandscape.isLandscape)
        assertEquals(layoutLandscape.eyeDiameter * 0.72f, layoutLandscape.baseSpacing, 0.01f)
    }

    // ─── 3. Eye colour Tests ─────────────────────────────────────────────────

    @Test
    fun `getEyeDeepShadowColor computes authentic deep shadow hues`() {
        // Cyan base LED -> deep teal lower rim (LOOI 40 Moodset sheet #0A5C6B)
        val cyanShadow = getEyeDeepShadowColor(androidx.compose.ui.graphics.Color(0xFF00F5FF))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0A5C6B), cyanShadow)

        // Red base LED -> deep burgundy crescent shadow
        val redShadow = getEyeDeepShadowColor(androidx.compose.ui.graphics.Color(0xFFFF3B5C))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF42000E), redShadow)

        // Pink base LED -> deep plum crescent shadow
        val pinkShadow = getEyeDeepShadowColor(androidx.compose.ui.graphics.Color(0xFFFF4081))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF450624), pinkShadow)

        // Gold base LED -> deep bronze crescent shadow
        val goldShadow = getEyeDeepShadowColor(androidx.compose.ui.graphics.Color(0xFFFFD700))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF522800), goldShadow)
    }

    // ─── 4. VirtualHeadKinematics Tests ──────────────────────────────────────

    @Test
    fun `VirtualHeadKinematics produces equal circle sizes for front and back discs`() {
        val eyeDiameter = 120f
        val result = VirtualHeadKinematics.calculate(
            headCenterX = 200f,
            headCenterY = 300f,
            baseSpacing = 60f,
            eyeDiameter = eyeDiameter,
            headYaw = 0.4f,
            headPitch = -0.3f,
            pupilGazeX = 0.8f,
            pupilGazeY = -0.5f,
            squashX = 1f,
            squashY = 1f
        )

        // Both eyes have equal discSize matching eyeDiameter
        assertEquals(eyeDiameter, result.leftEye.discSize.width)
        assertEquals(eyeDiameter, result.leftEye.discSize.height)
        assertEquals(eyeDiameter, result.rightEye.discSize.width)
        assertEquals(eyeDiameter, result.rightEye.discSize.height)
    }

    @Test
    fun `VirtualHeadKinematics back circle stays stationary when only pupil gaze moves`() {
        val headX = 200f
        val headY = 300f
        val spacing = 60f
        val eyeDiameter = 100f

        // Initial resting position (head still, gaze center)
        val rest = VirtualHeadKinematics.calculate(
            headCenterX = headX,
            headCenterY = headY,
            baseSpacing = spacing,
            eyeDiameter = eyeDiameter,
            headYaw = 0f,
            headPitch = 0f,
            pupilGazeX = 0f,
            pupilGazeY = 0f
        )

        // Pupil glances right and up, but head remains stationary
        val gazeDart = VirtualHeadKinematics.calculate(
            headCenterX = headX,
            headCenterY = headY,
            baseSpacing = spacing,
            eyeDiameter = eyeDiameter,
            headYaw = 0f,
            headPitch = 0f,
            pupilGazeX = 0.85f,
            pupilGazeY = -0.70f
        )

        // Back Circle (เบ้าตาบนหัวหุ่นยนต์): MUST NOT MOVE when only pupil glances!
        assertEquals(rest.leftEye.backCenter.x, gazeDart.leftEye.backCenter.x, 0.001f)
        assertEquals(rest.leftEye.backCenter.y, gazeDart.leftEye.backCenter.y, 0.001f)
        assertEquals(rest.rightEye.backCenter.x, gazeDart.rightEye.backCenter.x, 0.001f)
        assertEquals(rest.rightEye.backCenter.y, gazeDart.rightEye.backCenter.y, 0.001f)

        // Front Circle (ลูกตา): MUST MOVE towards gaze direction!
        assertTrue(gazeDart.leftEye.frontCenter.x > rest.leftEye.frontCenter.x, "Pupil should move right")
        assertTrue(gazeDart.leftEye.frontCenter.y < rest.leftEye.frontCenter.y, "Pupil should move up")
    }

    @Test
    fun `VirtualHeadKinematics back circle moves and eye spacing compresses when head turns`() {
        val headX = 200f
        val headY = 300f
        val spacing = 60f
        val eyeDiameter = 100f

        val rest = VirtualHeadKinematics.calculate(
            headCenterX = headX,
            headCenterY = headY,
            baseSpacing = spacing,
            eyeDiameter = eyeDiameter,
            headYaw = 0f,
            headPitch = 0f
        )

        // Head turns to the right (Yaw = 0.75f)
        val headTurnRight = VirtualHeadKinematics.calculate(
            headCenterX = headX,
            headCenterY = headY,
            baseSpacing = spacing,
            eyeDiameter = eyeDiameter,
            headYaw = 0.75f,
            headPitch = 0f
        )

        // Back Circles MUST move right along with the head!
        assertTrue(headTurnRight.leftEye.backCenter.x > rest.leftEye.backCenter.x, "Left socket should move right")
        assertTrue(headTurnRight.rightEye.backCenter.x > rest.rightEye.backCenter.x, "Right socket should move right")

        // Distance between eyes compresses due to 3D spherical curvature
        val restDistance = rest.rightEye.backCenter.x - rest.leftEye.backCenter.x
        val turnedDistance = headTurnRight.rightEye.backCenter.x - headTurnRight.leftEye.backCenter.x
        assertTrue(turnedDistance < restDistance, "Eye spacing should compress when turning on 3D curved face")
    }

    @Test
    fun `VirtualHeadKinematics resting offset exposes bottom-right crescent matching physical LOOI`() {
        val result = VirtualHeadKinematics.calculate(
            headCenterX = 200f,
            headCenterY = 300f,
            baseSpacing = 60f,
            eyeDiameter = 100f,
            headYaw = 0f,
            headPitch = 0f,
            pupilGazeX = 0f,
            pupilGazeY = 0f
        )

        // In resting pose: frontCenter is shifted up-left relative to backCenter
        // Therefore backCenter is down-right of frontCenter, exposing the blue crescent at bottom-right
        assertTrue(
            result.leftEye.backCenter.x > result.leftEye.frontCenter.x,
            "Back socket X should be to the right of front pupil"
        )
        assertTrue(
            result.leftEye.backCenter.y > result.leftEye.frontCenter.y,
            "Back socket Y should be below front pupil"
        )
    }
}

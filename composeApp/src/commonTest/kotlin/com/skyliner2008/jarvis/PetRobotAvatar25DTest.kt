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
        assertEquals(240f, layout.cY)

        // In landscape: minOf(height * 0.52f, width * 0.28f) = minOf(249.6, 224) = 224f
        val expectedEyeDiameter = minOf(height * 0.52f, width * 0.28f)
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
        assertEquals(360f, layout.cY)

        // In portrait: minOf(width * 0.38f, height * 0.24f) = minOf(136.8, 172.8) = 136.8f
        val expectedEyeDiameter = minOf(width * 0.38f, height * 0.24f)
        assertEquals(expectedEyeDiameter, layout.eyeDiameter, 0.01f)
    }

    @Test
    fun `calculateAvatarLayout respects isLandscapeOverride`() {
        val width = 800f
        val height = 480f
        val layoutForcedPortrait = calculateAvatarLayout(width, height, isLandscapeOverride = false)
        assertFalse(layoutForcedPortrait.isLandscape)
    }

    // ─── 3. PetRobotParametricEye Tests ──────────────────────────────────────

    @Test
    fun `toEyeShapeParams produces correct parameters for core emotions`() {
        // IDLE
        val idleLeft = AvatarEmotion.IDLE.toEyeShapeParams(isLeft = true)
        assertNotNull(idleLeft)
        assertEquals(1f, idleLeft.openness)
        assertEquals(0f, idleLeft.slantDeg)
        assertEquals(1f, idleLeft.cornerRoundness)

        // HAPPY
        val happyLeft = AvatarEmotion.HAPPY.toEyeShapeParams(isLeft = true)
        assertNotNull(happyLeft)
        assertEquals(1.0f, happyLeft.curvature)
        assertEquals(0.72f, happyLeft.openness)

        // ANGRY
        val angryLeft = AvatarEmotion.ANGRY.toEyeShapeParams(isLeft = true)
        assertNotNull(angryLeft)
        assertEquals(18.0f, angryLeft.slantDeg)
        assertEquals(0.35f, angryLeft.cornerRoundness)

        // SLEEPING
        val sleepLeft = AvatarEmotion.SLEEPING.toEyeShapeParams(isLeft = true)
        assertNotNull(sleepLeft)
        assertEquals(0.15f, sleepLeft.openness)
        assertEquals(-0.05f, sleepLeft.curvature)

        // SAD
        val sadLeft = AvatarEmotion.SAD.toEyeShapeParams(isLeft = true)
        assertNotNull(sadLeft)
        assertEquals(-12.0f, sadLeft.slantDeg)
        assertEquals(-0.85f, sadLeft.curvature)

        // SURPRISED
        val surpriseLeft = AvatarEmotion.SURPRISED.toEyeShapeParams(isLeft = true)
        assertNotNull(surpriseLeft)
        assertEquals(1.25f, surpriseLeft.openness)
        assertEquals(1.15f, surpriseLeft.widthRatio)
        assertEquals(1.25f, surpriseLeft.heightRatio)

        // BORED
        val boredLeft = AvatarEmotion.BORED.toEyeShapeParams(isLeft = true)
        assertNotNull(boredLeft)
        assertEquals(0.50f, boredLeft.openness)
        assertEquals(-0.30f, boredLeft.curvature)

        // WINK (left eye open, right eye closed)
        val winkLeft = AvatarEmotion.WINK.toEyeShapeParams(isLeft = true)
        val winkRight = AvatarEmotion.WINK.toEyeShapeParams(isLeft = false)
        assertNotNull(winkLeft)
        assertNotNull(winkRight)
        assertEquals(1.04f, winkLeft.openness)
        assertEquals(0.12f, winkRight.openness)
        assertEquals(0.25f, winkRight.curvature)
    }

    @Test
    fun `buildParametricEyePath handles various emotion params`() {
        val emotionsToTest = listOf(
            AvatarEmotion.IDLE,
            AvatarEmotion.HAPPY,
            AvatarEmotion.ANGRY,
            AvatarEmotion.SLEEPING,
            AvatarEmotion.SAD,
            AvatarEmotion.SURPRISED,
            AvatarEmotion.BORED
        )

        for (emotion in emotionsToTest) {
            val params = emotion.toEyeShapeParams(isLeft = true)
            assertNotNull(params)
            assertTrue(params.openness > 0f)
            assertTrue(params.widthRatio > 0f)
            assertTrue(params.heightRatio > 0f)
            try {
                val path = buildParametricEyePath(
                    params = params,
                    centerX = 100f,
                    centerY = 100f,
                    baseW = 80f,
                    baseH = 80f,
                    isLeft = true
                )
                assertNotNull(path)
            } catch (e: RuntimeException) {
                // Expected on local JVM unit test without mocked Android framework
                assertTrue(e.message?.contains("not mocked") == true)
            }
        }
    }

    @Test
    fun `all AvatarEmotion entries return non-null EyeShapeParams for both left and right eyes`() {
        for (emotion in AvatarEmotion.entries) {
            val leftParams = emotion.toEyeShapeParams(isLeft = true)
            val rightParams = emotion.toEyeShapeParams(isLeft = false)

            assertNotNull(leftParams, "Emotion $emotion left eye params must not be null")
            assertNotNull(rightParams, "Emotion $emotion right eye params must not be null")

            assertTrue(leftParams.openness > 0f, "Emotion $emotion left openness must be positive")
            assertTrue(rightParams.openness > 0f, "Emotion $emotion right openness must be positive")
            assertTrue(leftParams.widthRatio > 0f, "Emotion $emotion left widthRatio must be positive")
            assertTrue(rightParams.widthRatio > 0f, "Emotion $emotion right widthRatio must be positive")
            assertTrue(leftParams.heightRatio > 0f, "Emotion $emotion left heightRatio must be positive")
            assertTrue(rightParams.heightRatio > 0f, "Emotion $emotion right heightRatio must be positive")
        }
    }

    @Test
    fun `special eye shapes have correct parametric shape amounts`() {
        // LOVE: authentic LOOI dome eye (⌒ with flat bottom)
        val loveParams = AvatarEmotion.LOVE.toEyeShapeParams(isLeft = true)
        assertNotNull(loveParams)
        assertEquals(0.95f, loveParams.domeAmount)
        assertEquals(0.35f, loveParams.curvature)

        // ROMANTIC: full heart eyes ♥
        val romanticParams = AvatarEmotion.ROMANTIC.toEyeShapeParams(isLeft = true)
        assertNotNull(romanticParams)
        assertEquals(1.0f, romanticParams.heartAmount)

        // DEAD: cross eyes X X
        val deadParams = AvatarEmotion.DEAD.toEyeShapeParams(isLeft = true)
        assertNotNull(deadParams)
        assertEquals(1.0f, deadParams.crossAmount)

        // EXCITED: 4-point sparkle star eyes ★
        val excitedParams = AvatarEmotion.EXCITED.toEyeShapeParams(isLeft = true)
        assertNotNull(excitedParams)
        assertEquals(1.0f, excitedParams.starAmount)

        // DIZZY: cross/spiral eyes
        val dizzyParams = AvatarEmotion.DIZZY.toEyeShapeParams(isLeft = true)
        assertNotNull(dizzyParams)
        assertEquals(0.90f, dizzyParams.crossAmount)

        // EVIL: slanted menacing eyes
        val evilParams = AvatarEmotion.EVIL.toEyeShapeParams(isLeft = true)
        assertNotNull(evilParams)
        assertEquals(20.0f, evilParams.slantDeg)
    }

    @Test
    fun `getEyeDeepShadowColor computes authentic deep shadow hues`() {
        // Cyan base LED -> deep indigo crescent shadow
        val cyanShadow = getEyeDeepShadowColor(androidx.compose.ui.graphics.Color(0xFF00F5FF))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF071952), cyanShadow)

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
}

package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import com.skyliner2008.jarvis.pet.EnrollmentSampler
import com.skyliner2008.jarvis.pet.FaceIdentity
import com.skyliner2008.jarvis.pet.FaceRecognition
import com.skyliner2008.jarvis.pet.PetFaceProfile
import com.skyliner2008.jarvis.pet.PetModeController
import com.skyliner2008.jarvis.pet.PetVisionBridge
import com.skyliner2008.jarvis.pet.TurnedHeadSampler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Face identity by SFace embedding + the enrollment scan's sampling (2026-10-08 review of the Avatar work) */
class FaceRecognitionTest {

    private fun close(a: Float, b: Float, eps: Float = 1e-3f) = abs(a - b) <= eps

    @Test
    fun `alignment recovers a rotated, scaled and shifted face exactly`() {
        // the template moved by a known similarity: scale 2.5, 10°, shift (300, 120)
        val s = 2.5; val th = 10.0 * kotlin.math.PI / 180.0
        val a = (s * cos(th)).toFloat(); val b = (s * sin(th)).toFloat()
        val t = FaceRecognition.TEMPLATE_112
        val src = FloatArray(t.size)
        for (i in 0 until t.size / 2) {
            val x = t[2 * i]; val y = t[2 * i + 1]
            src[2 * i] = a * x - b * y + 300f
            src[2 * i + 1] = b * x + a * y + 120f
        }
        val m = assertNotNull(FaceRecognition.similarity(src))
        // the inverse transform maps the face back onto the template
        for (i in 0 until t.size / 2) {
            val x = m[0] * src[2 * i] - m[1] * src[2 * i + 1] + m[2]
            val y = m[1] * src[2 * i] + m[0] * src[2 * i + 1] + m[3]
            assertTrue(close(x, t[2 * i]) && close(y, t[2 * i + 1]), "point $i → ($x, $y)")
        }
        assertNull(FaceRecognition.similarity(FloatArray(10) { 5f }), "all points on one spot")
    }

    @Test
    fun `landmarks go in template order by image position, mirrored or not`() {
        val left = floatArrayOf(100f, 100f); val right = floatArrayOf(160f, 100f)
        val nose = floatArrayOf(130f, 130f)
        val mouthL = floatArrayOf(110f, 160f); val mouthR = floatArrayOf(150f, 160f)
        val expected = floatArrayOf(100f, 100f, 160f, 100f, 130f, 130f, 110f, 160f, 150f, 160f).toList()
        assertEquals(expected, FaceRecognition.templateOrder(left, right, nose, mouthL, mouthR).toList())
        // ML Kit's "left eye" is on the image's right in an unmirrored frame
        assertEquals(expected, FaceRecognition.templateOrder(right, left, nose, mouthR, mouthL).toList())
    }

    private fun unit(vararg v: Float) = FaceRecognition.normalize(v.toList() + List(FaceRecognition.EMBEDDING_SIZE - v.size) { 0f })

    private fun profile(slot: Int, name: String, embedding: List<Float> = emptyList(), ratios: List<Float> = listOf(0.4f, 1.2f, 0.9f, 0.8f)) =
        PetFaceProfile(slot, name, isEnrolled = true, landmarkRatios = ratios, embedding = embedding)

    @Test
    fun `the closest enrolled face above the threshold wins, others and old profiles do not`() {
        val boss = profile(0, "บอส", unit(1f, 0.1f))
        val friend = profile(1, "เพื่อน", unit(0.1f, 1f))
        val oldOne = profile(2, "เก่า")                    // saved by landmark ratios only
        val profiles = listOf(boss, friend, oldOne)

        val nearBoss = unit(0.95f, 0.2f, 0.05f)
        val (who, score) = assertNotNull(FaceRecognition.bestMatch(profiles, nearBoss))
        assertEquals("บอส", who.name)
        assertTrue(score >= FaceRecognition.SAME_PERSON_COSINE)

        // half-way between two people, and far from everyone: no match
        assertNull(FaceRecognition.bestMatch(profiles, unit(0f, 0f, 1f)))
        assertTrue(oldOne.isEnrolled && !oldOne.usesEmbedding)
        assertNull(FaceRecognition.bestMatch(listOf(oldOne), nearBoss))
    }

    @Test
    fun `cosine and the mean embedding`() {
        assertTrue(close(FaceRecognition.cosine(listOf(1f, 0f), listOf(2f, 0f)), 1f))
        assertTrue(close(FaceRecognition.cosine(listOf(1f, 0f), listOf(0f, 3f)), 0f))
        assertEquals(0f, FaceRecognition.cosine(listOf(1f), listOf(1f, 2f)))
        val mean = FaceRecognition.meanEmbedding(listOf(listOf(1f, 0f), listOf(0f, 1f)))
        assertTrue(close(mean[0], 0.7071f) && close(mean[1], 0.7071f))
    }

    // a unit vector at cosine c to the first axis, the rest of it along axis [other]
    private fun atCos(c: Float, other: Int = 2): List<Float> =
        List(FaceRecognition.EMBEDDING_SIZE) { i -> when (i) { 0 -> c; other -> sqrt(1f - c * c); else -> 0f } }

    @Test
    fun `a named face keeps its name through one poor embedding and lets go after two`() {
        val boss = profile(0, "บอส", unit(1f))
        val friend = profile(1, "เพื่อน", unit(0f, 1f))
        val profiles = listOf(boss, friend)
        val id = FaceIdentity(window = 1)

        assertEquals("บอส", id.offer(profiles, unit(1f, 0.1f), faceKey = 7).profile?.name)
        // 0.33: under the 0.363 needed to take a name, over the 0.30 that keeps it
        assertEquals("บอส", id.offer(profiles, atCos(0.33f), faceKey = 7).profile?.name)
        // far off once: still the boss (one miss); twice: nobody
        assertEquals("บอส", id.offer(profiles, atCos(0.1f), faceKey = 7).profile?.name)
        assertNull(id.offer(profiles, atCos(0.1f), faceKey = 7).profile)
        assertEquals(-1, id.currentSlot)
        // and 0.33 does not take the name back
        assertNull(id.offer(profiles, atCos(0.33f), faceKey = 7).profile)
    }

    @Test
    fun `another profile takes over only by a margin, and a new face starts over`() {
        val boss = profile(0, "บอส", unit(1f))
        val friend = profile(1, "เพื่อน", unit(0f, 1f))
        val profiles = listOf(boss, friend)
        val id = FaceIdentity(window = 1)
        fun mix(b: Float, f: Float) = List(FaceRecognition.EMBEDDING_SIZE) { i ->
            when (i) { 0 -> b; 1 -> f; 2 -> sqrt((1f - b * b - f * f).coerceAtLeast(0f)); else -> 0f }
        }

        assertEquals("บอส", id.offer(profiles, unit(1f), faceKey = 1).profile?.name)
        assertEquals("บอส", id.offer(profiles, mix(0.40f, 0.43f), faceKey = 1).profile?.name, "0.43 does not beat 0.40 by 0.05")
        assertEquals("เพื่อน", id.offer(profiles, mix(0.40f, 0.50f), faceKey = 1).profile?.name)

        // the face in front changed (another tracking id): its name is not inherited
        assertNull(id.offer(profiles, atCos(0.33f), faceKey = 2).profile)
    }

    @Test
    fun `the last embeddings are averaged before matching`() {
        val boss = profile(0, "บอส", unit(1f))
        val id = FaceIdentity(window = 3)
        id.offer(listOf(boss), unit(1f), faceKey = 1)
        id.offer(listOf(boss), unit(1f), faceKey = 1)
        // one embedding almost nothing like the boss, averaged with two good ones, still reads as the boss
        val answer = id.offer(listOf(boss), atCos(0.05f), faceKey = 1)
        assertEquals("บอส", answer.profile?.name)
        assertTrue(answer.cosine > 0.8f, "cos ${answer.cosine}")
    }

    @Test
    fun `a turned-head template matches a turned head`() {
        val turned = atCos(0f, other = 3)
        val boss = profile(0, "บอส", unit(1f)).copy(poseEmbeddings = listOf(turned))
        assertTrue(close(FaceRecognition.profileCosine(boss, turned), 1f))
        assertEquals("บอส", FaceRecognition.bestMatch(listOf(boss), turned)?.first?.name)
        assertNull(FaceRecognition.bestMatch(listOf(profile(0, "บอส", unit(1f))), turned), "frontal only: no match")
    }

    @Test
    fun `only a face the model can read is embedded`() {
        assertTrue(FaceRecognition.readable(yaw = 10f, pitch = 5f, eyeDistancePx = 40f))
        assertTrue(FaceRecognition.readable(yaw = -28f, pitch = 0f, eyeDistancePx = 40f))
        assertFalse(FaceRecognition.readable(yaw = 35f, pitch = 0f, eyeDistancePx = 40f), "turned away")
        assertFalse(FaceRecognition.readable(yaw = 0f, pitch = -30f, eyeDistancePx = 40f), "looking down")
        assertFalse(FaceRecognition.readable(yaw = 0f, pitch = 0f, eyeDistancePx = 20f), "too small")
    }

    @Test
    fun `the turned-head step keeps each side of the same person`() {
        val front = unit(1f)
        val step = TurnedHeadSampler(front, perSide = 2, minIntervalMs = 100L)
        val turnedBoss = atCos(0.7f)
        assertFalse(step.offer(turnedBoss, yaw = 5f, nowMs = 1_000), "still looking straight")
        assertTrue(step.offer(turnedBoss, yaw = 20f, nowMs = 1_000))
        assertFalse(step.offer(turnedBoss, yaw = 22f, nowMs = 1_050), "too soon")
        assertFalse(step.offer(atCos(0.1f), yaw = 20f, nowMs = 1_200), "someone else")
        assertTrue(step.offer(turnedBoss, yaw = 25f, nowMs = 1_300))
        assertFalse(step.offer(turnedBoss, yaw = 25f, nowMs = 1_500), "this side is full")
        assertTrue(step.offer(turnedBoss, yaw = -20f, nowMs = 1_600))
        assertFalse(step.done)
        assertTrue(step.offer(turnedBoss, yaw = -15f, nowMs = 1_800))
        assertTrue(step.done)
        assertEquals(4, step.samples().size)
    }

    @Test
    fun `turned-head templates are saved with the profile`() = runBlocking {
        var state = AvatarState()
        val controller = PetModeController(CoroutineScope(SupervisorJob()), { state }, { state = it })
        controller.start()
        try {
            controller.enrollFaceWithLandmarks(0, "บอส", listOf(0.4f, 1.2f, 0.9f, 0.8f), unit(1f), listOf(atCos(0.7f), listOf(1f)))
            val saved = controller.faceProfiles.value[0]
            assertEquals(1, saved.poseEmbeddings.size, "a wrong-sized template is dropped")
            assertTrue(close(FaceRecognition.cosine(saved.poseEmbeddings[0], atCos(0.7f)), 1f))
        } finally {
            controller.deleteFaceProfile(0)
            controller.stop()
        }
    }

    private fun sampler() = EnrollmentSampler(target = 4, minIntervalMs = 100L, sameFace = { mean, s ->
        FaceRecognition.cosine(mean, s) >= FaceRecognition.SAME_PERSON_COSINE
    }, lostResetMs = 1_000L, outliersToRestart = 3)

    @Test
    fun `a scan collects one person, frame by frame`() {
        val s = sampler()
        val boss = listOf(1f, 0.1f)
        assertEquals(EnrollmentSampler.Result.ADDED, s.offer(boss, 1_000, extra = listOf(0.4f)))
        assertEquals(EnrollmentSampler.Result.TOO_SOON, s.offer(boss, 1_050))
        assertEquals(EnrollmentSampler.Result.ADDED, s.offer(boss, 1_200, extra = listOf(0.6f)))
        assertEquals(EnrollmentSampler.Result.ADDED, s.offer(boss, 1_400, extra = listOf(0.4f)))
        assertEquals(EnrollmentSampler.Result.DONE, s.offer(boss, 1_600, extra = listOf(0.6f)))
        assertEquals(4, s.samples().size)
        assertEquals(listOf(0.5f), EnrollmentSampler.average(s.extras()))
    }

    @Test
    fun `a face that was gone for a while starts the scan over`() {
        val s = sampler()
        val boss = listOf(1f, 0.1f)
        s.offer(boss, 1_000); s.offer(boss, 1_200)
        // nobody usable in front of the camera for 1.5 s, then a face again: the old samples are dropped
        assertEquals(EnrollmentSampler.Result.RESTARTED, s.offer(listOf(0.1f, 1f), 2_700))
        assertEquals(1, s.count)
        assertEquals(1, s.restarts)
    }

    @Test
    fun `one odd frame is dropped, a different person staying in front starts over`() {
        val s = sampler()
        val boss = listOf(1f, 0.1f); val other = listOf(0.1f, 1f)
        s.offer(boss, 1_000); s.offer(boss, 1_200)
        assertEquals(EnrollmentSampler.Result.DROPPED, s.offer(other, 1_400))
        assertEquals(EnrollmentSampler.Result.ADDED, s.offer(boss, 1_600))
        assertEquals(3, s.count)
        assertEquals(EnrollmentSampler.Result.DROPPED, s.offer(other, 1_800))
        assertEquals(EnrollmentSampler.Result.DROPPED, s.offer(other, 2_000))
        assertEquals(EnrollmentSampler.Result.RESTARTED, s.offer(other, 2_200))
        assertEquals(0, s.count)
    }

    @Test
    fun `recognition goes by slot, so another name that starts the same is left alone`() = runBlocking {
        var state = AvatarState()
        val controller = PetModeController(CoroutineScope(SupervisorJob()), { state }, { state = it })
        controller.start()
        try {
            controller.enrollFaceWithLandmarks(0, "บอส", listOf(0.4f, 1.2f, 0.9f, 0.8f), unit(1f))
            controller.enrollFaceWithLandmarks(1, "บอสใหญ่", listOf(0.5f, 1.3f, 1.0f, 0.9f), unit(0f, 1f))
            assertTrue(controller.faceProfiles.value[0].usesEmbedding)
            PetVisionBridge.onFaceProfileMatched?.invoke("บอสใหญ่ (80%)", 1)
            assertEquals(1, controller.currentRecognizedSlot.value)

            controller.deleteFaceProfile(0)                       // "บอส" — a prefix of "บอสใหญ่"
            assertEquals("บอสใหญ่ (80%)", controller.currentRecognizedPerson.value)
            assertEquals(1, controller.currentRecognizedSlot.value)

            controller.renameFaceProfile(1, "พี่ใหญ่")
            assertEquals("พี่ใหญ่", controller.currentRecognizedPerson.value)
            controller.deleteFaceProfile(1)
            assertNull(controller.currentRecognizedPerson.value)
            assertEquals(-1, controller.currentRecognizedSlot.value)

            // "not enrolled yet" is a label, not a recognized person
            PetVisionBridge.onFaceProfileMatched?.invoke("ตรวจพบใบหน้า (ยังไม่ลงทะเบียน)", -1)
            assertEquals(-1, controller.currentRecognizedSlot.value)
        } finally {
            controller.stop()
        }
    }
}

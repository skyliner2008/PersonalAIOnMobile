package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import com.skyliner2008.jarvis.pet.EnrollmentSampler
import com.skyliner2008.jarvis.pet.FaceRecognition
import com.skyliner2008.jarvis.pet.PetFaceProfile
import com.skyliner2008.jarvis.pet.PetModeController
import com.skyliner2008.jarvis.pet.PetVisionBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
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

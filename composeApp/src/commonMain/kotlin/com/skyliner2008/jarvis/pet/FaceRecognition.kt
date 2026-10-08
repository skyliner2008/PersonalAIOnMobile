package com.skyliner2008.jarvis.pet

import kotlin.math.sqrt

/**
 * Face identity by embedding — SFace (OpenCV Zoo `face_recognition_sface_2021dec_int8.onnx`, Apache-2.0), run on the
 * phone by [com.skyliner2008.jarvis.pet.FaceEmbedder]. The face is aligned to a 112×112 crop from 5 ML Kit landmarks,
 * the model gives 128 numbers, and two faces are the same person when the cosine of their embeddings reaches
 * [SAME_PERSON_COSINE].
 *
 * It replaces matching on 4 landmark ratios (2026-10-08 review): two of those used the nose-to-mouth distance, which
 * changes when the person smiles or talks, so the owner read as a stranger mid-sentence and look-alikes could match.
 * The ratio matching stays as the fallback when the model cannot load.
 */
object FaceRecognition {
    /** OpenCV's same-person cosine for SFace (opencv_zoo models/face_recognition_sface/sface.py) */
    const val SAME_PERSON_COSINE = 0.363f
    const val EMBEDDING_SIZE = 128
    const val ALIGNED_SIZE = 112

    /**
     * The ArcFace / SFace 5-point template in a 112×112 crop (OpenCV FaceRecognizerSF.alignCrop), as x,y pairs:
     * the eye on the image's left, the eye on its right, the nose, the mouth corner on the left, on the right.
     */
    val TEMPLATE_112 = floatArrayOf(
        38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f, 41.5493f, 92.3655f, 70.7299f, 92.2041f
    )

    /**
     * ML Kit landmarks in the template's order. Sides by image x, not by ML Kit's left/right names, so a mirrored
     * front-camera frame lines up the same way. Each point is x,y.
     */
    fun templateOrder(eyeA: FloatArray, eyeB: FloatArray, nose: FloatArray, mouthA: FloatArray, mouthB: FloatArray): FloatArray {
        val (eyeL, eyeR) = if (eyeA[0] <= eyeB[0]) eyeA to eyeB else eyeB to eyeA
        val (mouthL, mouthR) = if (mouthA[0] <= mouthB[0]) mouthA to mouthB else mouthB to mouthA
        return floatArrayOf(eyeL[0], eyeL[1], eyeR[0], eyeR[1], nose[0], nose[1], mouthL[0], mouthL[1], mouthR[0], mouthR[1])
    }

    /**
     * Least-squares similarity transform (scale, rotation, shift — no mirroring) taking [src] points onto [dst],
     * both as x,y pairs. Returns [a, b, tx, ty] for x' = a·x − b·y + tx, y' = b·x + a·y + ty; null for degenerate points.
     */
    fun similarity(src: FloatArray, dst: FloatArray = TEMPLATE_112): FloatArray? {
        val n = src.size / 2
        if (n < 2 || src.size != dst.size) return null
        var smx = 0.0; var smy = 0.0; var dmx = 0.0; var dmy = 0.0
        for (i in 0 until n) {
            smx += src[2 * i]; smy += src[2 * i + 1]; dmx += dst[2 * i]; dmy += dst[2 * i + 1]
        }
        smx /= n; smy /= n; dmx /= n; dmy /= n
        var dot = 0.0; var cross = 0.0; var norm = 0.0
        for (i in 0 until n) {
            val sx = src[2 * i] - smx; val sy = src[2 * i + 1] - smy
            val dx = dst[2 * i] - dmx; val dy = dst[2 * i + 1] - dmy
            dot += sx * dx + sy * dy
            cross += sx * dy - sy * dx
            norm += sx * sx + sy * sy
        }
        if (norm < 1e-9) return null
        val a = dot / norm
        val b = cross / norm
        val tx = dmx - (a * smx - b * smy)
        val ty = dmy - (b * smx + a * smy)
        return floatArrayOf(a.toFloat(), b.toFloat(), tx.toFloat(), ty.toFloat())
    }

    fun normalize(v: List<Float>): List<Float> {
        val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return if (n > 1e-6f) v.map { it / n } else v
    }

    /** cosine of two embeddings (either may be unnormalized); 0 when sizes differ or one is empty */
    fun cosine(a: List<Float>, b: List<Float>): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]
        }
        return if (na < 1e-12 || nb < 1e-12) 0f else (dot / sqrt(na * nb)).toFloat()
    }

    /** the normalized mean of normalized embeddings — the template a person is enrolled with */
    fun meanEmbedding(samples: List<List<Float>>): List<Float> {
        if (samples.isEmpty()) return emptyList()
        val size = samples.first().size
        val sum = FloatArray(size)
        samples.forEach { s -> normalize(s).forEachIndexed { i, x -> if (i < size) sum[i] += x } }
        return normalize(sum.toList())
    }

    /** the enrolled profile closest to [embedding] at or above [threshold], with its cosine */
    fun bestMatch(
        profiles: List<PetFaceProfile>,
        embedding: List<Float>,
        threshold: Float = SAME_PERSON_COSINE
    ): Pair<PetFaceProfile, Float>? =
        profiles.filter { it.isEnrolled && it.usesEmbedding }
            .map { it to cosine(it.embedding, embedding) }
            .filter { it.second >= threshold }
            .maxByOrNull { it.second }
}

/**
 * The samples one enrollment scan keeps (2026-10-08 review): a scan used to average every frame it saw, so a face that
 * left mid-scan and someone else who stepped in were averaged into one template that matched neither. Now a gap with
 * no usable face starts over, a single odd frame is dropped, and a run of frames from a different face starts over.
 *
 * @param sameFace whether a new sample belongs to the face the samples so far describe (their mean)
 */
class EnrollmentSampler(
    val target: Int,
    private val minIntervalMs: Long,
    private val sameFace: (mean: List<Float>, sample: List<Float>) -> Boolean,
    private val lostResetMs: Long = 1_000L,
    private val outliersToRestart: Int = 3
) {
    enum class Result { ADDED, TOO_SOON, DROPPED, RESTARTED, DONE }

    private val samples = mutableListOf<List<Float>>()
    private val extras = mutableListOf<List<Float>>()
    private var lastAddedAt = 0L
    private var lastReadyAt = 0L
    private var outliers = 0

    val count: Int get() = samples.size
    var restarts: Int = 0
        private set

    private fun mean(): List<Float> {
        val size = samples.first().size
        return (0 until size).map { d -> samples.map { it[d] }.average().toFloat() }
    }

    private fun restart() {
        samples.clear()
        extras.clear()
        outliers = 0
        restarts++
    }

    /**
     * a frame where the face is usable ([sample] taken from it) at [nowMs]; [extra] is kept beside the sample and
     * dropped with it (the landmark ratios that go with an embedding)
     */
    fun offer(sample: List<Float>, nowMs: Long, extra: List<Float> = emptyList()): Result {
        if (samples.size >= target) return Result.DONE
        var restarted = false
        if (samples.isNotEmpty() && nowMs - lastReadyAt > lostResetMs) {
            restart(); restarted = true
        }
        lastReadyAt = nowMs
        if (nowMs - lastAddedAt < minIntervalMs) return if (restarted) Result.RESTARTED else Result.TOO_SOON
        if (samples.size >= 2 && !sameFace(mean(), sample)) {
            outliers++
            if (outliers >= outliersToRestart) {
                restart()
                return Result.RESTARTED
            }
            return Result.DROPPED
        }
        outliers = 0
        samples += sample
        extras += extra
        lastAddedAt = nowMs
        return when {
            samples.size >= target -> Result.DONE
            restarted -> Result.RESTARTED
            else -> Result.ADDED
        }
    }

    /** the samples (for averaging by the caller's own rule) */
    fun samples(): List<List<Float>> = samples.toList()

    fun extras(): List<List<Float>> = extras.toList()

    companion object {
        /** per-dimension mean of equal-length vectors (landmark ratios) */
        fun average(vectors: List<List<Float>>): List<Float> {
            val v = vectors.filter { it.isNotEmpty() }
            if (v.isEmpty()) return emptyList()
            return v.first().indices.map { d -> v.map { it[d] }.average().toFloat() }
        }

        /** RMS distance, as [PetFaceProfile.calculateDistance] */
        fun rms(a: List<Float>, b: List<Float>): Float {
            val n = minOf(a.size, b.size)
            if (n == 0) return Float.MAX_VALUE
            var sum = 0f
            for (i in 0 until n) { val d = a[i] - b[i]; sum += d * d }
            return sqrt(sum / n)
        }
    }
}

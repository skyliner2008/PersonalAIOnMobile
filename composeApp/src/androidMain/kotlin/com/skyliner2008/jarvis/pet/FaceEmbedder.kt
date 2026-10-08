package com.skyliner2008.jarvis.pet

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SFace on the phone ([FaceRecognition]): aligns a face to 112×112 from 5 landmarks and gives its 128-number
 * embedding. The model ships in the app (`assets/models/face_recognition_sface_2021dec_int8.onnx`, OpenCV Zoo,
 * Apache-2.0); nothing leaves the phone.
 *
 * Model input `data` [1,3,112,112] float RGB 0..255 (the graph subtracts 127.5 and scales by 1/128 itself);
 * output `fc1` [1,128]. One job at a time on its own thread — a frame that arrives while one runs is skipped.
 */
object FaceEmbedder {
    private const val TAG = "FaceEmbedder"
    private const val ASSET = "models/face_recognition_sface_2021dec_int8.onnx"
    private const val INPUT = "data"
    private const val SIZE = FaceRecognition.ALIGNED_SIZE

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "face-embedder").apply { isDaemon = true } }
    private val busy = AtomicBoolean(false)
    @Volatile private var session: OrtSession? = null
    @Volatile private var loading = false

    val isAvailable: Boolean get() = session != null

    /** load the model once, off the main thread (MainActivity.onCreate) */
    fun init(context: Context) {
        if (session != null || loading) return
        loading = true
        val app = context.applicationContext
        executor.execute {
            val t0 = System.currentTimeMillis()
            try {
                val bytes = app.assets.open(ASSET).use { it.readBytes() }
                val s = OrtEnvironment.getEnvironment().createSession(bytes, OrtSession.SessionOptions())
                // one run on a grey face: proves the runtime takes the model, and warms it up
                val probe = run(s, FloatArray(3 * SIZE * SIZE) { 128f })
                if (probe?.size != FaceRecognition.EMBEDDING_SIZE) error("output ${probe?.size} numbers, expected ${FaceRecognition.EMBEDDING_SIZE}")
                session = s
                logDebug(TAG, "✅ SFace loaded (${bytes.size / 1024} KB) in ${System.currentTimeMillis() - t0}ms — faces matched by embedding")
            } catch (e: Throwable) {
                logError(TAG, "SFace unavailable — face profiles fall back to landmark ratios: ${e.message}", e)
            } finally {
                loading = false
            }
        }
    }

    /**
     * Embed the face whose 5 template-ordered points ([FaceRecognition.templateOrder], in [bitmap] pixels) are
     * [points]; [onResult] runs on the embedder thread with the normalized embedding, or null.
     * The face is cut out here, on the caller's thread: the detector recycles its frame bitmap as soon as detection
     * completes, so the embedder thread only ever sees its own 112×112 pixels.
     * @return false when the model is not loaded or a job is still running (the frame is skipped)
     */
    fun embedAsync(bitmap: Bitmap, points: FloatArray, onResult: (List<Float>?) -> Unit): Boolean {
        val s = session ?: return false
        if (bitmap.isRecycled || !busy.compareAndSet(false, true)) return false
        val input = try {
            aligned(bitmap, points)
        } catch (e: Throwable) {
            logError(TAG, "face alignment failed: ${e.message}", e)
            null
        }
        if (input == null) {
            busy.set(false)
            return false
        }
        executor.execute {
            val t0 = System.currentTimeMillis()
            try {
                val out = run(s, input)
                val ms = System.currentTimeMillis() - t0
                if (ms > 250) logDebug(TAG, "slow embedding ${ms}ms")
                onResult(out?.let { FaceRecognition.normalize(it.toList()) })
            } catch (e: Throwable) {
                logError(TAG, "embedding failed: ${e.message}", e)
                onResult(null)
            } finally {
                busy.set(false)
            }
        }
        return true
    }

    /** the face warped onto the template → CHW float RGB 0..255 */
    private fun aligned(bitmap: Bitmap, points: FloatArray): FloatArray? {
        val t = FaceRecognition.similarity(points) ?: return null
        val matrix = Matrix().apply { setValues(floatArrayOf(t[0], -t[1], t[2], t[1], t[0], t[3], 0f, 0f, 1f)) }
        val crop = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(crop).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val px = IntArray(SIZE * SIZE)
        crop.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        crop.recycle()
        val plane = SIZE * SIZE
        val chw = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val c = px[i]
            chw[i] = ((c shr 16) and 0xFF).toFloat()
            chw[plane + i] = ((c shr 8) and 0xFF).toFloat()
            chw[2 * plane + i] = (c and 0xFF).toFloat()
        }
        return chw
    }

    private fun run(s: OrtSession, chw: FloatArray): FloatArray? {
        val env = OrtEnvironment.getEnvironment()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { tensor ->
            s.run(mapOf(INPUT to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = result.get(0).value as? Array<FloatArray>
                return out?.firstOrNull()
            }
        }
    }
}

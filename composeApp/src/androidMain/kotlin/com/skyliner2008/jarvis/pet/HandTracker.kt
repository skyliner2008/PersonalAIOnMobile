package com.skyliner2008.jarvis.pet

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * Hands on the phone ([HandPose]): MediaPipe Gesture Recognizer finds up to two hands, their 21 points and the
 * model's gesture. The model ships in the app (`assets/models/gesture_recognizer.task`, Google MediaPipe,
 * Apache-2.0); the frames never leave the phone.
 *
 * Runs in VIDEO mode — it follows a hand from frame to frame instead of searching the whole frame each time — on the
 * caller's thread (the camera thread), so the hands are ready before the frame is recycled.
 */
object HandTracker {
    private const val TAG = "HandTracker"
    private const val ASSET = "models/gesture_recognizer.task"
    private const val MAX_HANDS = 2

    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "hand-tracker-load").apply { isDaemon = true } }
    @Volatile private var recognizer: GestureRecognizer? = null
    @Volatile private var loading = false
    private var lastTimestampMs = 0L

    val isAvailable: Boolean get() = recognizer != null

    /** load the model once, off the main thread (MainActivity.onCreate) */
    fun init(context: Context) {
        if (recognizer != null || loading) return
        loading = true
        val app = context.applicationContext
        loader.execute {
            val t0 = System.currentTimeMillis()
            try {
                val bytes = app.assets.open(ASSET).use { it.readBytes() }
                val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
                val options = GestureRecognizer.GestureRecognizerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(buffer).build())
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumHands(MAX_HANDS)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build()
                val r = GestureRecognizer.createFromOptions(app, options)
                // one run on a blank frame: proves the runtime takes the model, and warms it up
                val probe = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                synchronized(this) { r.recognizeForVideo(BitmapImageBuilder(probe).build(), nextTimestamp()) }
                probe.recycle()
                recognizer = r
                logDebug(TAG, "✅ Hand model loaded (${bytes.size / 1024} KB) in ${System.currentTimeMillis() - t0}ms — hands and gestures by MediaPipe")
            } catch (e: Throwable) {
                logError(TAG, "Hand model unavailable — no hands or gestures are detected: ${e.message}", e)
            } finally {
                loading = false
            }
        }
    }

    /** the hands in [bitmap] (ARGB_8888), points in its pixels; null when the model is not loaded */
    fun detect(bitmap: Bitmap): List<HandSighting>? {
        val r = recognizer ?: return null
        if (bitmap.isRecycled) return null
        return try {
            val t0 = System.currentTimeMillis()
            val result = synchronized(this) { r.recognizeForVideo(BitmapImageBuilder(bitmap).build(), nextTimestamp()) }
            val ms = System.currentTimeMillis() - t0
            if (ms > 150) logDebug(TAG, "slow hand frame ${ms}ms")
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            result.landmarks().mapIndexed { i, landmarks ->
                val points = FloatArray(landmarks.size * 2)
                landmarks.forEachIndexed { j, p ->
                    points[2 * j] = p.x() * w
                    points[2 * j + 1] = p.y() * h
                }
                val gesture = result.gestures().getOrNull(i)?.firstOrNull()
                val side = result.handedness().getOrNull(i)?.firstOrNull()
                HandSighting(
                    points = points,
                    modelGesture = gesture?.categoryName() ?: "None",
                    gestureScore = gesture?.score() ?: 0f,
                    handScore = side?.score() ?: 0f,
                    side = side?.categoryName() ?: "?"
                )
            }.filter { it.points.size == 42 }
        } catch (e: Throwable) {
            logError(TAG, "hand frame failed: ${e.message}", e)
            null
        }
    }

    /** VIDEO mode wants every frame later than the one before */
    private fun nextTimestamp(): Long {
        val now = SystemClock.uptimeMillis()
        lastTimestampMs = if (now > lastTimestampMs) now else lastTimestampMs + 1
        return lastTimestampMs
    }
}

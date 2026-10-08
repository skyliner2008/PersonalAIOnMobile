package com.skyliner2008.jarvis.pet

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * Things on the phone ([Things]): MediaPipe Object Detector with EfficientDet-Lite0 int8 — the 80 COCO classes with
 * real scores. The model ships in the app (`assets/models/efficientdet_lite0.tflite`, Google MediaPipe, Apache-2.0);
 * the frames never leave the phone.
 *
 * Things hold still more than faces and hands do: the model runs at most every [INTERVAL_MS] on the caller's thread
 * (the camera thread) and the frames between get its last answer, so the face and hands keep their pace.
 */
object ObjectSpotter {
    private const val TAG = "ObjectSpotter"
    private const val ASSET = "models/efficientdet_lite0.tflite"
    private const val MAX_RESULTS = 5
    private const val INTERVAL_MS = 400L

    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "object-spotter-load").apply { isDaemon = true } }
    @Volatile private var detector: ObjectDetector? = null
    @Volatile private var loading = false
    private var lastRunAt = 0L
    private var last: List<ThingSighting> = emptyList()

    val isAvailable: Boolean get() = detector != null

    /** load the model once, off the main thread (MainActivity.onCreate) */
    fun init(context: Context) {
        if (detector != null || loading) return
        loading = true
        val app = context.applicationContext
        loader.execute {
            val t0 = System.currentTimeMillis()
            try {
                val bytes = app.assets.open(ASSET).use { it.readBytes() }
                val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
                val options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(buffer).build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setMaxResults(MAX_RESULTS)
                    .setScoreThreshold(Things.MIN_SCORE)
                    .setCategoryDenylist(Things.LEFT_OUT.toList())
                    .build()
                val d = ObjectDetector.createFromOptions(app, options)
                // one run on a blank frame: proves the runtime takes the model, and warms it up
                val probe = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                synchronized(this) { d.detect(BitmapImageBuilder(probe).build()) }
                probe.recycle()
                detector = d
                logDebug(TAG, "✅ Object model loaded (${bytes.size / 1024} KB) in ${System.currentTimeMillis() - t0}ms — 80 COCO things by EfficientDet-Lite0")
            } catch (e: Throwable) {
                logError(TAG, "Object model unavailable — no things are detected: ${e.message}", e)
            } finally {
                loading = false
            }
        }
    }

    /** the things in [bitmap] (ARGB_8888), boxes in its pixels; null when the model is not loaded */
    fun detect(bitmap: Bitmap): List<ThingSighting>? {
        val d = detector ?: return null
        val now = SystemClock.uptimeMillis()
        if (now - lastRunAt < INTERVAL_MS) return last
        if (bitmap.isRecycled) return null
        return try {
            val t0 = System.currentTimeMillis()
            val result = synchronized(this) { d.detect(BitmapImageBuilder(bitmap).build()) }
            val ms = System.currentTimeMillis() - t0
            if (ms > 150) logDebug(TAG, "slow object frame ${ms}ms")
            lastRunAt = now
            last = result.detections().mapNotNull { detection ->
                val category = detection.categories().firstOrNull() ?: return@mapNotNull null
                val box = detection.boundingBox()
                ThingSighting(category.categoryName(), category.score(), floatArrayOf(box.left, box.top, box.right, box.bottom))
            }.filter { Things.shows(it.name, it.score) }
            last
        } catch (e: Throwable) {
            logError(TAG, "object frame failed: ${e.message}", e)
            null
        }
    }
}

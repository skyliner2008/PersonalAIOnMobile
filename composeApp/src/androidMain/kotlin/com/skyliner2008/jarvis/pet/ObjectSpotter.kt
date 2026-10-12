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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Things on the phone ([Things]): MediaPipe Object Detector with EfficientDet-Lite0 int8 — the 80 COCO classes with
 * real scores. The model ships in the app (`assets/models/efficientdet_lite0.tflite`, Google MediaPipe, Apache-2.0);
 * the frames never leave the phone.
 *
 * Things hold still more than faces and hands do: the model runs at most every [INTERVAL_MS], on its own thread, on a
 * copy of the frame shrunk to [INPUT_MAX] (the model looks at 320×320 anyway), and every frame gets its latest answer.
 * It used to run on the camera thread: every 0.4 s one frame took 30–60 ms longer and the pet's gaze stuttered.
 */
object ObjectSpotter {
    private const val TAG = "ObjectSpotter"
    private const val ASSET = "models/efficientdet_lite0.tflite"
    private const val MAX_RESULTS = 5
    private const val INTERVAL_MS = 300L
    private const val INPUT_MAX = 320
    /** an answer older than this no longer describes the frame (the camera was off, the model stuck) */
    private const val STALE_MS = 1_500L

    // loads the model, then runs it — one job at a time
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "object-spotter").apply { isDaemon = true } }
    private val busy = AtomicBoolean(false)
    @Volatile private var detector: ObjectDetector? = null
    @Volatile private var loading = false
    @Volatile private var lastRunAt = 0L
    @Volatile private var last: List<ThingSighting> = emptyList()
    @Volatile private var lastAt = 0L
    // the latest run's candidates before [ThingStabilizer] — for the vision test screen and for hands holding things
    @Volatile private var lastRaw: List<ThingSighting> = emptyList()
    // used on the worker thread only
    private val stabilizer = ThingStabilizer()
    private var lastRawLogAt = 0L
    private var lastRawLogKey = ""

    /** what the model proposed and what stayed, for tuning from logcat: on a change, else every 5 s */
    private fun logRaw(raw: List<ThingSighting>, shown: List<ThingSighting>, now: Long) {
        val key = raw.map { it.name }.sorted().joinToString() + "|" + shown.map { it.name }.sorted().joinToString()
        if (raw.isEmpty() && shown.isEmpty()) {
            lastRawLogKey = ""
            return
        }
        if (key == lastRawLogKey && now - lastRawLogAt < 5_000L) return
        lastRawLogKey = key
        lastRawLogAt = now
        logDebug(
            TAG,
            "📦 raw: " + raw.joinToString { "${it.name} ${"%.2f".format(it.score)}" }.ifEmpty { "—" } +
                " → shown: " + shown.joinToString { "${it.name} ${"%.2f".format(it.score)}" }.ifEmpty { "—" }
        )
    }

    val isAvailable: Boolean get() = detector != null

    /** load the model once, off the main thread (MainActivity.onCreate) */
    fun init(context: Context) {
        if (detector != null || loading) return
        loading = true
        val app = context.applicationContext
        worker.execute {
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

    /**
     * The things in [bitmap] (ARGB_8888), boxes in its pixels — the latest answer, from a frame up to ~[INTERVAL_MS]
     * ago; a new run starts when one is due. Null when the model is not loaded.
     */
    fun detect(bitmap: Bitmap): List<ThingSighting>? {
        val d = detector ?: return null
        val now = SystemClock.uptimeMillis()
        if (now - lastRunAt >= INTERVAL_MS && !bitmap.isRecycled && busy.compareAndSet(false, true)) {
            lastRunAt = now
            start(d, bitmap)
        }
        return if (now - lastAt <= STALE_MS) last else emptyList()
    }

    /** the latest run's candidates (score ≥ [Things.MIN_SCORE]), shown or not; empty when stale */
    fun candidates(): List<ThingSighting> =
        if (SystemClock.uptimeMillis() - lastAt <= STALE_MS) lastRaw else emptyList()

    /** a small copy of the frame (the caller recycles its own) to the worker */
    private fun start(d: ObjectDetector, bitmap: Bitmap) {
        val scale = minOf(1f, INPUT_MAX.toFloat() / maxOf(bitmap.width, bitmap.height))
        val input = try {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                .let { if (it === bitmap) bitmap.copy(Bitmap.Config.ARGB_8888, false) else it }
        } catch (e: Throwable) {
            logError(TAG, "object frame copy failed: ${e.message}", e)
            busy.set(false)
            return
        }
        worker.execute {
            try {
                val t0 = System.currentTimeMillis()
                val result = synchronized(this) { d.detect(BitmapImageBuilder(input).build()) }
                val ms = System.currentTimeMillis() - t0
                if (ms > 150) logDebug(TAG, "slow object frame ${ms}ms")
                val raw = result.detections().mapNotNull { detection ->
                    val category = detection.categories().firstOrNull() ?: return@mapNotNull null
                    val box = detection.boundingBox()
                    ThingSighting(
                        category.categoryName(),
                        category.score(),
                        floatArrayOf(box.left / scale, box.top / scale, box.right / scale, box.bottom / scale)
                    )
                }.filter { Things.shows(it.name, it.score) }
                // a run's candidates show only once they stay ([ThingStabilizer]); a gap of over a second starts over
                val runAt = SystemClock.uptimeMillis()
                if (runAt - lastAt > STALE_MS) stabilizer.clear()
                lastRaw = raw
                last = stabilizer.offer(raw)
                lastAt = runAt
                logRaw(raw, last, runAt)
            } catch (e: Throwable) {
                logError(TAG, "object frame failed: ${e.message}", e)
            } finally {
                input.recycle()
                busy.set(false)
            }
        }
    }
}

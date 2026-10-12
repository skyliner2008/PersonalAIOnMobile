package com.skyliner2008.jarvis.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.skyliner2008.jarvis.pet.PetVisionDetector
import com.skyliner2008.jarvis.pet.PetVisionProcessor
import io.ktor.util.encodeBase64
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// ═══════════════════════════════════════════════════════════════════
// CameraPreviewView.android.kt
//
// ใช้ CameraX:
//   - Preview      ➔ แสดงภาพในหน้าจอ real-time
//   - ImageAnalysis ➔ ดัก JPEG frame ส่งให้ CameraAnalysisService
//
// Frame Rate: CameraX จะส่ง frame ตาม STRATEGY_KEEP_ONLY_LATEST
// Adaptive FPS จัดการที่ CameraAnalysisService แทน
// ═══════════════════════════════════════════════════════════════════

private val globalCameraBindingMutex = Mutex()

@Composable
actual fun CameraPreviewView(
    modifier: Modifier,
    onFrameCapture: (jpegBase64: String, rawBytes: ByteArray) -> Unit,
    isFrontCamera: Boolean,
    isActive: Boolean,
    visionProcessor: PetVisionProcessor?,
    jpegIntervalMs: Long?
) {
    val context = LocalContext.current
    // the analyzer is set once per bind: it reads the latest callback, processor and interval through these
    // (it used to keep the ones from the bind — a Live toggle or a new processor reached it only on a rebind)
    val currentOnFrameCapture by rememberUpdatedState(onFrameCapture)
    val currentVisionProcessor by rememberUpdatedState(visionProcessor)
    val currentJpegIntervalMs by rememberUpdatedState(jpegIntervalMs)
    val lastJpegAt = remember { AtomicLong(0L) }
    val lifecycleOwner = remember(context) {
        generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<androidx.lifecycle.LifecycleOwner>()
            .firstOrNull()
    } ?: LocalLifecycleOwner.current

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Stable use cases - remember them to avoid re-creation
    // Both Preview and ImageAnalysis MUST use the same aspect ratio
    // so the user and AI see exactly the same field of view.
    val resolutionSelector = remember {
        androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            )
            .build()
    }
    val previewUseCase = remember {
        Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
    }
    val imageAnalysisUseCase = remember {
        ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    // Get CameraProvider instance
    LaunchedEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
        }, ContextCompat.getMainExecutor(context))
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val orientation = configuration.orientation

    // Bind/unbind effect — Mutex-protected to prevent "Device 0 Conflicts"
    LaunchedEffect(cameraProvider, isActive, isFrontCamera, lifecycleOwner, orientation) {
        val provider = cameraProvider ?: return@LaunchedEffect

        // Debounce: let rapid Compose recompositions settle before touching hardware
        delay(80)

        globalCameraBindingMutex.withLock {
            withContext(Dispatchers.Main) {
                try {
                    provider.unbindAll()

                    if (!isActive) return@withContext

                    val targetRotation = when (orientation) {
                        android.content.res.Configuration.ORIENTATION_LANDSCAPE -> android.view.Surface.ROTATION_90
                        else -> android.view.Surface.ROTATION_0
                    }
                    previewUseCase.targetRotation = targetRotation
                    imageAnalysisUseCase.targetRotation = targetRotation

                    val cameraSelector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    imageAnalysisUseCase.clearAnalyzer()
                    imageAnalysisUseCase.setAnalyzer(cameraExecutor) { imageProxy ->
                        processFrame(
                            imageProxy,
                            isFrontCamera,
                            currentVisionProcessor,
                            currentJpegIntervalMs,
                            lastJpegAt,
                            currentOnFrameCapture
                        )
                    }

                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        previewUseCase,
                        imageAnalysisUseCase
                    )
                } catch (e: Exception) {
                    android.util.Log.e("CameraPreview", "Camera bind failed: ${e.message}")
                }
            }
        }
    }

    // Clean up on exit
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {}
        }
    }

    if (!isActive) return

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FIT_CENTER
                // Link the stable preview use-case to this view's surface
                previewUseCase.surfaceProvider = this.surfaceProvider
            }
        },
        update = {
            // Surface provider is already set in factory
        }
    )
}

// ─── Frame Processing ──────────────────────────────────────────────────────────

/**
 * One camera frame → the on-device vision (the upright Bitmap itself) and/or JPEG + Base64 for the callback.
 *
 * Only what someone takes is made (2026-10-09 review): every frame used to be converted, JPEG-encoded and Base64'd at
 * ~30 fps, and the pet's vision decoded the JPEG back into a Bitmap — while it takes at most ~12 frames a second and
 * Live one every 0.9 s. The q70 JPEG also blurred the face the recognizer embeds.
 */
private fun processFrame(
    imageProxy: ImageProxy,
    isFrontCamera: Boolean,
    vision: PetVisionProcessor?,
    jpegIntervalMs: Long?,
    lastJpegAt: AtomicLong,
    onFrameCapture: (jpegBase64: String, rawBytes: ByteArray) -> Unit
) {
    try {
        val now = SystemClock.uptimeMillis()
        val detector = vision as? PetVisionDetector
        val visionWants = vision?.wantsFrame() == true
        val jpegDue = jpegIntervalMs != null && now - lastJpegAt.get() >= jpegIntervalMs
        // a processor that takes only JPEG bytes (not the Android detector) still needs them
        val bytesForVision = visionWants && detector == null
        if (!jpegDue && !visionWants) return

        val frame = uprightFrame(imageProxy, isFrontCamera) ?: return
        var jpeg: ByteArray? = null
        if (jpegDue || bytesForVision) {
            jpeg = ByteArrayOutputStream().also { frame.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
        }
        if (jpegDue && jpeg != null && jpeg.isNotEmpty()) {
            lastJpegAt.set(now)
            onFrameCapture(jpeg.encodeBase64(), jpeg)
        }
        if (detector != null && visionWants) {
            // the detector takes the frame and recycles it once its models are done with it
            detector.processBitmap(frame, isFrontCamera = isFrontCamera, isAlreadyMirrored = isFrontCamera)
        } else {
            if (bytesForVision && jpeg != null) vision?.processFrame(jpeg, isFrontCamera)
            frame.recycle()
        }
    } catch (e: Exception) {
        android.util.Log.w("CameraPreview", "Frame processing error: ${e.message}")
    } finally {
        imageProxy.close()
    }
}

/**
 * ImageProxy ➔ Bitmap ตั้งตรง (หมุนตามเซนเซอร์, กลับด้านกล้องหน้า, ย่อด้านยาวสุดเหลือ 640px)
 * ใช้ CameraX imageProxy.toBitmap() แปลงทุก Format (YUV_420_888, JPEG, RGBA)
 */
private fun uprightFrame(imageProxy: ImageProxy, isFrontCamera: Boolean): Bitmap? {
    return try {
        val bitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
        val matrix = Matrix().apply {
            if (rotation != 0f) postRotate(rotation)
            // Mirror front camera around the center pivot
            if (isFrontCamera) postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)

            // Optimization: Scale down to max 640px to conserve CPU and streaming bandwidth
            val maxDim = maxOf(bitmap.width, bitmap.height)
            if (maxDim > 640) {
                val scale = 640f / maxDim.toFloat()
                postScale(scale, scale)
            }
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) bitmap.recycle()
        transformed
    } catch (e: Exception) {
        android.util.Log.w("CameraPreview", "frame conversion error: ${e.message}")
        null
    }
}

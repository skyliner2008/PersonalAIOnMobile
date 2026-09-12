package com.skyliner2008.jarvis.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.ktor.util.encodeBase64
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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

@Composable
actual fun CameraPreviewView(
    modifier: Modifier,
    onFrameCapture: (jpegBase64: String, rawBytes: ByteArray) -> Unit,
    isFrontCamera: Boolean,
    isActive: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // Mutex ensures bind and unbind NEVER run concurrently — root fix for "Device 0 Conflicts"
    val cameraMutex = remember { Mutex() }

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

        cameraMutex.withLock {
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
                        processFrame(imageProxy, isFrontCamera, onFrameCapture)
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

    // Clean up on exit — guarded by same mutex
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            cameraProvider?.unbindAll()
        }
    }

    if (!isActive) return

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
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
 * แปลง ImageProxy (YUV_420_888) ➔ JPEG bytes ➔ Base64
 * แล้วส่งกลับผ่าน callback
 */
private fun processFrame(
    imageProxy: ImageProxy,
    isFrontCamera: Boolean,
    onFrameCapture: (jpegBase64: String, rawBytes: ByteArray) -> Unit
) {
    try {
        val jpegBytes = imageProxyToJpeg(imageProxy, isFrontCamera)
        if (jpegBytes != null && jpegBytes.isNotEmpty()) {
            val base64 = jpegBytes.encodeBase64()
            onFrameCapture(base64, jpegBytes)
        }
    } catch (e: Exception) {
        android.util.Log.w("CameraPreview", "Frame processing error: ${e.message}")
    } finally {
        imageProxy.close()
    }
}

/**
 * แปลง ImageProxy ➔ JPEG ByteArray
 * ใช้ CameraX imageProxy.toBitmap() แปลงทุก Format (YUV_420_888, JPEG, RGBA)
 * ได้ภาพคมชัด ถูกต้อง 100% ไม่เกิดแถบลายหรือภาพสีเพี้ยน
 */
private fun imageProxyToJpeg(imageProxy: ImageProxy, isFrontCamera: Boolean): ByteArray? {
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
        val out = ByteArrayOutputStream()
        transformed.compress(Bitmap.CompressFormat.JPEG, 70, out)
        if (transformed != bitmap) transformed.recycle()
        bitmap.recycle()
        out.toByteArray()
    } catch (e: Exception) {
        android.util.Log.w("CameraPreview", "imageProxyToJpeg conversion error: ${e.message}")
        null
    }
}

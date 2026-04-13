package com.example.personalaibot.camera

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

    // Bind/unbind effect — Mutex-protected to prevent "Device 0 Conflicts"
    LaunchedEffect(cameraProvider, isActive, isFrontCamera, lifecycleOwner) {
        val provider = cameraProvider ?: return@LaunchedEffect

        // Debounce: let rapid Compose recompositions settle before touching hardware
        delay(80)

        cameraMutex.withLock {
            withContext(Dispatchers.Main) {
                try {
                    provider.unbindAll()

                    if (!isActive) return@withContext

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
                scaleType = PreviewView.ScaleType.FILL_CENTER
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
 * รองรับ YUV_420_888 (format ปกติของ CameraX) และ JPEG โดยตรง
 */
private fun imageProxyToJpeg(imageProxy: ImageProxy, isFrontCamera: Boolean): ByteArray? {
    return when (imageProxy.format) {
        ImageFormat.JPEG -> {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            bytes
        }
        ImageFormat.YUV_420_888 -> {
            yuvToJpeg(imageProxy, isFrontCamera)
        }
        else -> {
            bitmapToJpeg(imageProxy, isFrontCamera)
        }
    }
}

private fun yuvToJpeg(imageProxy: ImageProxy, isFrontCamera: Boolean): ByteArray? {
    val yPlane = imageProxy.planes[0]
    val uPlane = imageProxy.planes[1]
    val vPlane = imageProxy.planes[2]

    val ySize = yPlane.buffer.remaining()
    val uSize = uPlane.buffer.remaining()
    val vSize = vPlane.buffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yPlane.buffer.get(nv21, 0, ySize)
    vPlane.buffer.get(nv21, ySize, vSize)
    uPlane.buffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 85, out)

    val rawJpeg = out.toByteArray()

    // Apply rotation + mirror so AI sees the SAME orientation as the Preview
    val rotation = imageProxy.imageInfo.rotationDegrees
    if (rotation == 0 && !isFrontCamera) return rawJpeg // Fast path: no transform needed

    val bitmap = BitmapFactory.decodeByteArray(rawJpeg, 0, rawJpeg.size) ?: return rawJpeg
    val matrix = Matrix().apply {
        if (rotation != 0) postRotate(rotation.toFloat())
        if (isFrontCamera) postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
    }
    val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    val transformedOut = ByteArrayOutputStream()
    transformed.compress(Bitmap.CompressFormat.JPEG, 85, transformedOut)
    if (transformed != bitmap) transformed.recycle()
    bitmap.recycle()
    return transformedOut.toByteArray()
}

private fun bitmapToJpeg(imageProxy: ImageProxy, isFrontCamera: Boolean): ByteArray? {
    return try {
        val bitmap = imageProxy.toBitmap()
        val rotatedBitmap = if (isFrontCamera) {
            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f)
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            val matrix = Matrix().apply { postRotate(imageProxy.imageInfo.rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        val out = ByteArrayOutputStream()
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
        bitmap.recycle()
        out.toByteArray()
    } catch (e: Exception) {
        null
    }
}

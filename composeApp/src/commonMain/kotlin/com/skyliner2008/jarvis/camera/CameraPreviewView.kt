package com.skyliner2008.jarvis.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.skyliner2008.jarvis.pet.PetVisionProcessor

// ═══════════════════════════════════════════════════════════════════
// CameraPreviewView — expect/actual สำหรับ Camera Preview
//
// Android: ใช้ CameraX PreviewView + ImageAnalysis
// iOS:     placeholder (ยังไม่รองรับ)
// ═══════════════════════════════════════════════════════════════════

/**
 * แสดงภาพ preview จากกล้องจริง
 *
 * @param modifier          Compose modifier
 * @param onFrameCapture    callback เมื่อได้ JPEG frame ใหม่ (base64, rawBytes)
 * @param isFrontCamera     true = กล้องหน้า, false = กล้องหลัง
 * @param isActive          เปิด/ปิดกล้อง
 * @param visionProcessor   รับเฟรมไปตรวจจับบนเครื่อง (Android ส่ง Bitmap ให้ตรงๆ ไม่ผ่าน JPEG) เฉพาะเฟรมที่มันพร้อมรับ
 * @param jpegIntervalMs    เว้นระยะขั้นต่ำระหว่าง [onFrameCapture] แต่ละครั้ง; null = ไม่ทำ JPEG เลย (ใช้แค่ vision)
 */
@Composable
expect fun CameraPreviewView(
    modifier: Modifier = Modifier,
    onFrameCapture: (jpegBase64: String, rawBytes: ByteArray) -> Unit = { _, _ -> },
    isFrontCamera: Boolean = false,
    isActive: Boolean = true,
    visionProcessor: PetVisionProcessor? = null,
    jpegIntervalMs: Long? = 0L
)

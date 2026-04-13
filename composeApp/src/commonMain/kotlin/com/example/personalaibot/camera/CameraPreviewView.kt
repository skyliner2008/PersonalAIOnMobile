package com.example.personalaibot.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
 */
@Composable
expect fun CameraPreviewView(
    modifier: Modifier = Modifier,
    onFrameCapture: (jpegBase64: String, rawBytes: ByteArray) -> Unit = { _, _ -> },
    isFrontCamera: Boolean = false,
    isActive: Boolean = true
)

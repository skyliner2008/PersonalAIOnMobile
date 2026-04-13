package com.example.personalaibot.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun CameraPreviewView(
    modifier: Modifier,
    onFrameCapture: (jpegBase64: String, rawBytes: ByteArray) -> Unit,
    isFrontCamera: Boolean,
    isActive: Boolean
) {
    // iOS camera implementation — placeholder (not yet implemented)
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text("Camera not available on iOS", color = Color.White.copy(0.5f))
    }
}

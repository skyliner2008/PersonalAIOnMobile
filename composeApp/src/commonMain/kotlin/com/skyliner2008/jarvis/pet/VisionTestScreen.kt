package com.skyliner2008.jarvis.pet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skyliner2008.jarvis.camera.BoundingBoxOverlay
import com.skyliner2008.jarvis.camera.CameraPreviewView
import com.skyliner2008.jarvis.camera.ObjectLabelTags

/**
 * Vision test screen: the live camera with every Bounding Box the pet's vision makes — faces (ML Kit + SFace),
 * hands with their 21 points (MediaPipe Gesture Recognizer) and things (EfficientDet-Lite0) — to check by eye that
 * the models see what is really there.
 *
 * It runs the pet's own [processor] on the frames (the pet keeps reacting); [PetVisionBridge.onVisionDebug] carries
 * each frame's boxes. Two views:
 * - **ทั้งหมด**: everything the models proposed — every face and hand, every thing candidate (✓ shown, ✗ held back)
 * - **ที่น้องใช้**: what the pet acts on, after [PetVisionTargetTracker] (one face, one hand, one thing)
 */
@Composable
fun VisionTestScreen(
    processor: PetVisionProcessor?,
    isFrontCamera: Boolean,
    onSwitchCamera: (() -> Unit)?,
    onClose: () -> Unit,
    /** JPEG frames for the Live session while its eyes are open (null = none), at most one per [liveIntervalMs] */
    onLiveFrame: ((String) -> Unit)? = null,
    liveIntervalMs: Long? = null
) {
    var frame by remember { mutableStateOf<VisionDebugFrame?>(null) }
    var showAll by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        PetVisionBridge.onVisionDebug = { frame = it }
        onDispose { PetVisionBridge.onVisionDebug = null }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val f = frame
            // the frame's shape, so the preview fills it and the normalized boxes land where they belong
            val aspect = if (f != null && f.frameHeight > 0) f.frameWidth.toFloat() / f.frameHeight else 9f / 16f
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(aspect).align(Alignment.Center)
            ) {
                CameraPreviewView(
                    modifier = Modifier.fillMaxSize(),
                    isFrontCamera = isFrontCamera,
                    isActive = true,
                    visionProcessor = processor,
                    jpegIntervalMs = liveIntervalMs,
                    onFrameCapture = { jpegBase64, _ -> onLiveFrame?.invoke(jpegBase64) }
                )
                if (f != null) {
                    val objects = if (showAll) f.raw else f.tracked
                    BoundingBoxOverlay(objects = objects)
                    ObjectLabelTags(objects = objects)
                    if (showAll) HandSkeleton(f.handPoints)
                }
            }

            // ─── top: close, view, camera ───
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(38.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "ปิด", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = "🧪 ทดสอบการมองเห็น",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                ViewChip("ทั้งหมด", selected = showAll) { showAll = true }
                ViewChip("ที่น้องใช้", selected = !showAll) { showAll = false }
                if (onSwitchCamera != null) {
                    IconButton(
                        onClick = onSwitchCamera,
                        modifier = Modifier.size(38.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "สลับกล้อง", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ─── bottom: what each model said ───
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp)
                    .background(Color(0xFF0F172A).copy(alpha = 0.82f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val lines = f?.lines ?: listOf(if (processor == null) "⚠️ ไม่มีระบบตรวจจับ (เปิดในโหมด Avatar)" else "⏳ กำลังรอเฟรมแรก…")
                lines.forEach { Text(it, color = Color.White, fontSize = 11.sp) }
                Text(
                    text = if (showAll) {
                        "กรอบ: ฟ้า/เขียว = หน้า (เขียว = จำได้) · ส้ม = มือ + จุด 21 จุด · ม่วง ✓ = สิ่งของที่แสดง · เทา ✗ = โมเดลเดาแต่ยังไม่ผ่านตัวกรอง"
                    } else {
                        "กรอบที่น้องใช้จริงหลังตัวกรอง: หน้า 1 · มือ 1 · สิ่งของ 1 (🔒 = ล็อกเป้าแล้ว)"
                    },
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ViewChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color.Black else Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(if (selected) Color(0xFF00F0FF) else Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/** MediaPipe's hand connections: thumb, index, middle, ring, pinky and the palm */
private val HAND_BONES = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20
)

/** each hand's 21 points (normalized x0, y0, x1, y1 …) and the bones between them */
@Composable
private fun HandSkeleton(hands: List<FloatArray>) {
    if (hands.isEmpty()) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        hands.forEach { p ->
            if (p.size < 42) return@forEach
            fun at(i: Int) = Offset(p[2 * i] * size.width, p[2 * i + 1] * size.height)
            HAND_BONES.forEach { (a, b) -> drawLine(Color(0xFFFFD180), at(a), at(b), strokeWidth = 3f) }
            for (i in 0 until 21) {
                val tip = i == HandPose.THUMB_TIP || i == HandPose.INDEX_TIP || i == 12 || i == 16 || i == 20
                drawCircle(if (tip) Color(0xFFFF3D00) else Color.White, radius = if (tip) 6f else 4f, center = at(i))
            }
        }
    }
}

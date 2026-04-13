package com.example.personalaibot.camera

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.ui.theme.JarvisTheme

// ═══════════════════════════════════════════════════════════════════
// AROverlayEngine — Compose UI สำหรับแสดง AR annotations บนภาพกล้อง
//
// Features:
//   1. Bounding Box overlay สำหรับ Object Detection
//   2. Label tags แสดงชื่อวัตถุ + confidence %
//   3. Scene description overlay (มุมบน)
//   4. FPS + Motion indicator (มุมล่าง)
//   5. Animated scanning line effect
// ═══════════════════════════════════════════════════════════════════

/**
 * Overlay สำหรับ Bounding Boxes — วาดกรอบรอบวัตถุที่ตรวจจับได้
 */
@Composable
fun BoundingBoxOverlay(
    objects: List<DetectedObject>,
    modifier: Modifier = Modifier
) {
    if (objects.isEmpty()) return

    val infinite = rememberInfiniteTransition(label = "bbox_pulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height

        objects.forEach { obj ->
            val bbox = obj.boundingBox ?: return@forEach
            val color = parseColor(obj.color).copy(alpha = pulseAlpha)

            // วาดกรอบ bounding box
            val left = bbox.x * canvasW
            val top = bbox.y * canvasH
            val width = bbox.width * canvasW
            val height = bbox.height * canvasH

            // กรอบหลัก
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 3f)
            )

            // Corner markers (มุมทั้ง 4 หนาขึ้น)
            val cornerLen = minOf(width, height) * 0.15f
            val cornerStroke = Stroke(width = 5f)

            // Top-left corner
            drawLine(color, Offset(left, top), Offset(left + cornerLen, top), cornerStroke.width)
            drawLine(color, Offset(left, top), Offset(left, top + cornerLen), cornerStroke.width)

            // Top-right corner
            drawLine(color, Offset(left + width, top), Offset(left + width - cornerLen, top), cornerStroke.width)
            drawLine(color, Offset(left + width, top), Offset(left + width, top + cornerLen), cornerStroke.width)

            // Bottom-left corner
            drawLine(color, Offset(left, top + height), Offset(left + cornerLen, top + height), cornerStroke.width)
            drawLine(color, Offset(left, top + height), Offset(left, top + height - cornerLen), cornerStroke.width)

            // Bottom-right corner
            drawLine(color, Offset(left + width, top + height), Offset(left + width - cornerLen, top + height), cornerStroke.width)
            drawLine(color, Offset(left + width, top + height), Offset(left + width, top + height - cornerLen), cornerStroke.width)
        }
    }
}

/**
 * Label Tags — แสดงชื่อวัตถุบน Bounding Box
 */
@Composable
fun ObjectLabelTags(
    objects: List<DetectedObject>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        objects.forEach { obj ->
            val bbox = obj.boundingBox ?: return@forEach
            val color = parseColor(obj.color)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(
                        x = (bbox.x * 1000).dp / 10,   // approximate positioning
                        y = (bbox.y * 1000).dp / 10
                    )
            ) {
                Text(
                    text = "${obj.label} ${(obj.confidence * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(color.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Scene Info Overlay — แสดงข้อมูล scene/analysis ที่มุมบน
 */
@Composable
fun SceneInfoOverlay(
    labels: List<AnalysisLabel>,
    modifier: Modifier = Modifier
) {
    if (labels.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        labels.filter { it.category == LabelCategory.SCENE || it.category == LabelCategory.INFO }
            .take(3)
            .forEach { label ->
                Text(
                    text = label.text,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
            }

        // Warnings
        labels.filter { it.category == LabelCategory.WARNING }
            .forEach { label ->
                Text(
                    text = label.text,
                    color = JarvisTheme.Red,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
    }
}

/**
 * FPS + Status Indicator — แสดงที่มุมล่างซ้าย
 */
@Composable
fun CameraStatusOverlay(
    fps: Float,
    motionScore: Float,
    frameCount: Long,
    providerName: String,
    mode: CameraMode,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // FPS
        Text(
            text = "%.1f FPS".format(fps),
            color = when {
                fps >= 2f  -> Color(0xFF4CAF50) // Green
                fps >= 1f  -> JarvisTheme.Cyan
                else       -> Color(0xFFFF9800) // Orange
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        // Motion bar
        val motionPct = (motionScore * 100).toInt().coerceIn(0, 100)
        Text(
            text = "M:$motionPct%",
            color = Color.White.copy(0.7f),
            fontSize = 10.sp
        )

        // Provider badge
        Text(
            text = providerName,
            color = JarvisTheme.Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        // Mode
        Text(
            text = when (mode) {
                CameraMode.LIVE_STREAM   -> "LIVE"
                CameraMode.SNAPSHOT      -> "SNAP"
                CameraMode.OBJECT_DETECT -> "OD"
                CameraMode.AR_OVERLAY    -> "AR"
            },
            color = JarvisTheme.Purple,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        // Frame count
        Text(
            text = "#$frameCount",
            color = Color.White.copy(0.4f),
            fontSize = 9.sp
        )
    }
}

/**
 * Scanning Line Animation — เอฟเฟกต์เส้นสแกนเคลื่อนลงจากบนลงล่าง
 */
@Composable
fun ScanLineEffect(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isActive) return

    val infinite = rememberInfiniteTransition(label = "scan")
    val scanY by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "scanY"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val y = scanY * size.height
        drawLine(
            color = JarvisTheme.Cyan.copy(alpha = 0.4f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 2f
        )
        // Glow effect
        drawLine(
            color = JarvisTheme.Cyan.copy(alpha = 0.15f),
            start = Offset(0f, y - 10f),
            end = Offset(size.width, y - 10f),
            strokeWidth = 20f
        )
    }
}

// ─── Utility ─────────────────────────────────────────────────────

private fun parseColor(hex: String): Color {
    return try {
        val colorLong = hex.removePrefix("#").toLong(16)
        Color(
            red = ((colorLong shr 16) and 0xFF).toFloat() / 255f,
            green = ((colorLong shr 8) and 0xFF).toFloat() / 255f,
            blue = (colorLong and 0xFF).toFloat() / 255f,
            alpha = 1f
        )
    } catch (_: Exception) {
        JarvisTheme.Cyan
    }
}

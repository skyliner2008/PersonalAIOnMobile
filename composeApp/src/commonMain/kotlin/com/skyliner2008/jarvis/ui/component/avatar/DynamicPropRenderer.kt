package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * แปลง Hex String สีเป็น Compose Color แบบ Cross-Platform
 */
fun parseHexColor(hex: String, default: Color = Color(0xFF00E5FF)): Color {
    val clean = hex.trim().removePrefix("#")
    return try {
        when (clean.length) {
            6 -> Color(clean.toLong(16) or 0xFF000000)
            8 -> Color(clean.toLong(16))
            3 -> {
                val r = clean[0].toString().repeat(2)
                val g = clean[1].toString().repeat(2)
                val b = clean[2].toString().repeat(2)
                Color("$r$g$b".toLong(16) or 0xFF000000)
            }
            else -> default
        }
    } catch (_: Exception) {
        default
    }
}

/**
 * DynamicPropRenderer — คอมโพเนนต์วาดอุปกรณ์เสริมเวกเตอร์ SVG ไดนามิก
 * รองรับการ Parse SVG Path สดๆ พร้อมคำนวณ Auto-Fit Scale และตำแหน่งยึดบนใบหน้า
 */
@Composable
fun DynamicPropRenderer(
    prop: DynamicVectorProp,
    modifier: Modifier = Modifier
) {
    if (prop.svgPath.isBlank()) return

    // 1. Parse & Cache Path to prevent parsing overhead on 60/120 FPS frame loops
    val (path, bounds) = remember(prop.svgPath) {
        try {
            val p = PathParser().parsePathString(prop.svgPath).toNodes().toPath()
            val b = p.getBounds()
            Pair(p, b)
        } catch (_: Exception) {
            Pair(Path(), Rect.Zero)
        }
    }

    if (bounds.width <= 0f && bounds.height <= 0f) return

    val fillColor = remember(prop.fillColor) { parseHexColor(prop.fillColor) }
    val strokeColor = remember(prop.strokeColor) { prop.strokeColor?.let { parseHexColor(it) } }

    val infinite = rememberInfiniteTransition(label = "dynamic_prop_${prop.id}")

    // 2. Animation Drivers
    val bobOffset by if (prop.animation == DynamicPropAnimation.FLOAT_BOB) {
        infinite.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "bob"
        )
    } else {
        remember { androidx.compose.runtime.mutableStateOf(0f) }
    }

    val pulseScale by if (prop.animation == DynamicPropAnimation.PULSE) {
        infinite.animateFloat(
            initialValue = 0.90f,
            targetValue = 1.10f,
            animationSpec = infiniteRepeatable(tween(750, easing = EaseInOutCubic), RepeatMode.Reverse),
            label = "pulse"
        )
    } else {
        remember { androidx.compose.runtime.mutableStateOf(1f) }
    }

    val rotateAngle by if (prop.animation == DynamicPropAnimation.ROTATE_CONTINUOUS) {
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "rotate"
        )
    } else {
        remember { androidx.compose.runtime.mutableStateOf(0f) }
    }

    val swayAngle by if (prop.animation == DynamicPropAnimation.SWAY) {
        infinite.animateFloat(
            initialValue = -12f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "sway"
        )
    } else {
        remember { androidx.compose.runtime.mutableStateOf(0f) }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val layout = calculateAvatarLayout(size.width, size.height)
        val cX = layout.cX
        val cY = layout.cY
        val eyeDiameter = layout.eyeDiameter
        val baseSpacing = layout.baseSpacing

        val leftEyeCenterX = cX - baseSpacing
        val rightEyeCenterX = cX + baseSpacing
        val eyeCenterY = cY
        val mouthY = layout.mouthY

        // Anchor coordinate mapping based on PropPosition aligned to robot facial geometry
        val (anchorX, anchorY) = when (prop.position) {
            PropPosition.FOREHEAD -> Pair(cX, eyeCenterY - eyeDiameter * 0.72f)
            PropPosition.LEFT_EYE -> Pair(leftEyeCenterX, eyeCenterY)
            PropPosition.RIGHT_EYE -> Pair(rightEyeCenterX, eyeCenterY)
            PropPosition.CHEEKS -> Pair(cX, mouthY - eyeDiameter * 0.15f)
            PropPosition.CHIN -> Pair(cX, mouthY + eyeDiameter * 0.35f)
            PropPosition.FLOATING_LEFT -> Pair(leftEyeCenterX - eyeDiameter * 0.85f, eyeCenterY - eyeDiameter * 0.35f)
            PropPosition.FLOATING_RIGHT -> Pair(rightEyeCenterX + eyeDiameter * 0.85f, eyeCenterY - eyeDiameter * 0.35f)
        }

        val finalAnchorX = anchorX + (prop.offsetXRatio * layout.baseEyeW)
        val finalAnchorY = anchorY + (prop.offsetYRatio * layout.baseEyeH) + bobOffset.dp.toPx()

        // Auto-fit scale normalizer: scale bounds to prop.sizeDp or auto-fit robot eye diameter
        val targetSizePx = if (prop.sizeDp <= 0f) {
            when (prop.position) {
                PropPosition.LEFT_EYE, PropPosition.RIGHT_EYE -> eyeDiameter
                PropPosition.FOREHEAD, PropPosition.CHIN -> eyeDiameter * 0.9f
                else -> eyeDiameter * 0.75f
            }
        } else {
            prop.sizeDp.dp.toPx()
        }
        val maxBound = max(bounds.width, bounds.height).coerceAtLeast(1f)
        val baseScale = targetSizePx / maxBound
        val finalScale = baseScale * pulseScale

        val totalAngle = rotateAngle + swayAngle

        withTransform({
            translate(finalAnchorX, finalAnchorY)
            rotate(totalAngle, Offset.Zero)
            scale(finalScale, finalScale, Offset.Zero)
            translate(-bounds.center.x, -bounds.center.y)
        }) {
            drawPath(path, color = fillColor, style = Fill)
            if (strokeColor != null && prop.strokeWidth > 0f) {
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = prop.strokeWidth.dp.toPx() / finalScale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

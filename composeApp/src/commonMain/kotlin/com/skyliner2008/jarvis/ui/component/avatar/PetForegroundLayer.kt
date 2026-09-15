package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.*
import kotlin.random.Random

/**
 * PetForegroundLayer — คอมโพเนนต์ฉากหน้าไดนามิก (Foreground Atmospheric & Lens Layer)
 *
 * วางทับอยู่เหนือหุ่นยนต์และอุปกรณ์ (Layer 3.7) เพื่อสร้างมิติความลึก (2.5D Layered Depth & Parallax)
 * ให้ความรู้สึกเหมือนมองผ่านเลนส์กล้อง กระจกหน้ากาก หรือมีละอองบรรยากาศลอยอยู่หน้าหุ่นยนต์
 *
 * @param effect เอฟเฟกต์ฉากหน้าปัจจุบัน ([ForegroundEffect])
 * @param modifier Modifier สำหรับ Canvas
 */
@Composable
fun PetForegroundLayer(
    effect: ForegroundEffect,
    modifier: Modifier = Modifier
) {
    Crossfade(
        targetState = effect,
        modifier = modifier,
        animationSpec = tween(500, easing = EaseInOutCubic),
        label = "PetFgCrossfade"
    ) { currentEffect ->
        when (currentEffect) {
            ForegroundEffect.NONE -> { /* No foreground effect */ }
            ForegroundEffect.CYBER_HUD -> CyberHudForeground()
            ForegroundEffect.STAR_DUST -> StarDustForeground()
            ForegroundEffect.MAGIC_SPARKLES -> MagicSparklesForeground()
            ForegroundEffect.LENS_REFLECTION -> LensReflectionForeground()
            ForegroundEffect.FROST_VIGNETTE -> FrostVignetteForeground()
            ForegroundEffect.HEAT_DISTORTION -> HeatDistortionForeground()
            ForegroundEffect.RAIN_CONDENSATION -> RainCondensationForeground()
            ForegroundEffect.ELECTRIC_SPARKS -> ElectricSparksForeground()
            ForegroundEffect.FALLING_PETALS -> FallingPetalsForeground()
            ForegroundEffect.CONFETTI_TUMBLE -> ConfettiTumbleForeground()
            ForegroundEffect.GOLDEN_SHINE -> GoldenShineForeground()
            ForegroundEffect.HEART_ORBS -> HeartOrbsForeground()
            ForegroundEffect.BUBBLE_FLOAT -> BubbleFloatForeground()
            ForegroundEffect.ANAMORPHIC_FLARE -> AnamorphicFlareForeground()
            ForegroundEffect.CRT_SCANLINES -> CrtScanlinesForeground()
            ForegroundEffect.CAMERA_VIEWFINDER -> CameraViewfinderForeground()
        }
    }
}

private data class FgParticle(
    var x: Float,
    var y: Float,
    var speed: Float,
    var alpha: Float = 1f,
    var size: Float = 6f,
    var rot: Float = 0f,
    var extra: Float = 0f
)

/**
 * CYBER_HUD — แถบมุมกรอบ HUD ไซเบอร์เทค + เส้นสแกนแนวนอนบางเฉียบ
 */
@Composable
private fun CyberHudForeground() {
    val transition = rememberInfiniteTransition(label = "cyber_hud")
    val scanY by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "scan_y"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val pad = 16f
        val bracketLen = 32f
        val bracketW = 2.2f
        val cyan = Color(0xFF00F5FF)

        // Top-Left bracket
        drawLine(cyan.copy(alpha = 0.45f), Offset(pad, pad), Offset(pad + bracketLen, pad), bracketW)
        drawLine(cyan.copy(alpha = 0.45f), Offset(pad, pad), Offset(pad, pad + bracketLen), bracketW)

        // Top-Right bracket
        drawLine(cyan.copy(alpha = 0.45f), Offset(size.width - pad, pad), Offset(size.width - pad - bracketLen, pad), bracketW)
        drawLine(cyan.copy(alpha = 0.45f), Offset(size.width - pad, pad), Offset(size.width - pad, pad + bracketLen), bracketW)

        // Bottom-Left bracket
        drawLine(cyan.copy(alpha = 0.45f), Offset(pad, size.height - pad), Offset(pad + bracketLen, size.height - pad), bracketW)
        drawLine(cyan.copy(alpha = 0.45f), Offset(pad, size.height - pad), Offset(pad, size.height - pad - bracketLen), bracketW)

        // Bottom-Right bracket
        drawLine(cyan.copy(alpha = 0.45f), Offset(size.width - pad, size.height - pad), Offset(size.width - pad - bracketLen, size.height - pad), bracketW)
        drawLine(cyan.copy(alpha = 0.45f), Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - bracketLen), bracketW)

        // Subtle moving horizontal scanline
        val y = scanY * size.height
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, cyan.copy(alpha = 0.15f), Color.Transparent)),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.8f
        )
    }
}

/**
 * STAR_DUST — ละอองดวงดาวโบเก้ขนาดใหญ่ ลอยช้าๆ ในระยะใกล้หน้าเลนส์ (Foreground Depth)
 */
@Composable
private fun StarDustForeground() {
    val transition = rememberInfiniteTransition(label = "star_dust")
    val drift by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift"
    )
    val particles = remember {
        List(12) {
            FgParticle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.3f + 0.15f, Random.nextFloat() * 0.3f + 0.15f, Random.nextFloat() * 12f + 8f, 0f, Random.nextFloat() * 6.28f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val py = ((p.y - drift * p.speed) % 1f + 1f) % 1f * size.height
            val px = p.x * size.width + sin((drift * 2f * PI + p.extra).toDouble()).toFloat() * 18f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF80D8FF).copy(alpha = p.alpha), Color.Transparent),
                    center = Offset(px, py),
                    radius = p.size * 2f
                ),
                radius = p.size * 2f,
                center = Offset(px, py)
            )
        }
    }
}

/**
 * MAGIC_SPARKLES — ประกายดาว 4 แฉกเวทมนตร์ลอยฟุ้งด้านหน้า
 */
@Composable
private fun MagicSparklesForeground() {
    val transition = rememberInfiniteTransition(label = "magic_sparkles")
    val sparkProg by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "spark"
    )
    val sparkles = remember {
        List(14) {
            FgParticle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.5f + 0.3f, Random.nextFloat() * 0.6f + 0.3f, Random.nextFloat() * 8f + 6f, Random.nextFloat() * 360f, Random.nextFloat() * 6.28f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        sparkles.forEach { s ->
            val py = ((s.y - sparkProg * s.speed) % 1f + 1f) % 1f * size.height
            val px = s.x * size.width + sin((sparkProg * 4f * PI + s.extra).toDouble()).toFloat() * 15f
            val alpha = (s.alpha * sin((sparkProg * 2f * PI + s.extra).toDouble()).toFloat().absoluteValue).coerceIn(0f, 0.85f)
            val color = if (s.speed > 0.5f) Color(0xFFFFD54F) else Color(0xFFE040FB)
            drawSparkleCross(Offset(px, py), s.size, color.copy(alpha = alpha))
        }
    }
}

/**
 * LENS_REFLECTION — แสงสะท้อนกระจกเลนส์โค้ง (Curved Glass Visor Sheen)
 */
@Composable
private fun LensReflectionForeground() {
    val transition = rememberInfiniteTransition(label = "lens_reflection")
    val sheenX by transition.animateFloat(
        -0.3f, 1.3f,
        infiniteRepeatable(tween(5500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "sheen"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = sheenX * size.width
        val cy = size.height * 0.28f
        // Soft diagonal elliptical lens flare reflection
        rotate(25f, pivot = Offset(cx, cy)) {
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.045f), Color(0xFF00F5FF).copy(alpha = 0.02f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = size.width * 0.45f
                ),
                topLeft = Offset(cx - size.width * 0.45f, cy - size.height * 0.15f),
                size = Size(size.width * 0.9f, size.height * 0.30f)
            )
        }
    }
}

/**
 * FROST_VIGNETTE — เกล็ดน้ำแข็งจับขอบจอ 4 ด้าน + หิมะเม็ดใหญ่ลอยผ่านหน้ากล้อง
 */
@Composable
private fun FrostVignetteForeground() {
    val transition = rememberInfiniteTransition(label = "frost_vignette")
    val snowProg by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "snow"
    )
    val fgSnow = remember {
        List(8) {
            FgParticle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.8f + 0.6f, Random.nextFloat() * 0.4f + 0.2f, Random.nextFloat() * 14f + 8f, 0f, Random.nextFloat() * 6.28f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Corner icy vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0xFF80D8FF).copy(alpha = 0.06f), Color(0xFFE0F7FA).copy(alpha = 0.18f)),
                center = center,
                radius = size.maxDimension * 0.72f
            )
        )

        // Large out-of-focus foreground snowflakes
        fgSnow.forEach { s ->
            val py = (s.y + snowProg * s.speed) % 1f * size.height
            val px = (s.x * size.width + sin((py * 0.04f + s.extra).toDouble()).toFloat() * 22f) % size.width
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = s.alpha), Color.Transparent),
                    center = Offset(px, py),
                    radius = s.size
                ),
                radius = s.size,
                center = Offset(px, py)
            )
        }
    }
}

/**
 * HEAT_DISTORTION — สะเก็ดไฟอุ่นลอยผ่านหน้าเลนส์ (Warm Ember Particles)
 */
@Composable
private fun HeatDistortionForeground() {
    val transition = rememberInfiniteTransition(label = "heat_distortion")
    val riseProg by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "rise"
    )
    val embers = remember {
        List(10) {
            FgParticle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.5f + 0.5f, Random.nextFloat() * 0.5f + 0.3f, Random.nextFloat() * 10f + 6f, 0f, Random.nextFloat() * 6.28f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        embers.forEach { e ->
            val py = ((e.y - riseProg * e.speed) % 1f + 1f) % 1f * size.height
            val px = e.x * size.width + sin((py * 0.05f + e.extra).toDouble()).toFloat() * 18f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFAB00).copy(alpha = e.alpha), Color(0xFFFF3D00).copy(alpha = e.alpha * 0.4f), Color.Transparent),
                    center = Offset(px, py),
                    radius = e.size
                ),
                radius = e.size,
                center = Offset(px, py)
            )
        }
    }
}

/**
 * RAIN_CONDENSATION — หยดน้ำเกาะและไหลบนผิวกระจกหน้าจอ
 */
@Composable
private fun RainCondensationForeground() {
    val drops = remember {
        List(16) {
            FgParticle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.2f + 0.1f, Random.nextFloat() * 0.35f + 0.25f, Random.nextFloat() * 7f + 4f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drops.forEach { d ->
            val dx = d.x * size.width
            val dy = d.y * size.height
            drawOval(
                color = Color(0x66CFD8DC).copy(alpha = d.alpha),
                topLeft = Offset(dx - d.size * 0.4f, dy - d.size * 0.8f),
                size = Size(d.size * 0.8f, d.size * 1.6f)
            )
            // Specular droplet highlight
            drawCircle(
                color = Color.White.copy(alpha = d.alpha * 0.7f),
                radius = d.size * 0.22f,
                center = Offset(dx - d.size * 0.15f, dy - d.size * 0.4f)
            )
        }
    }
}

/**
 * ELECTRIC_SPARKS — ประกายสายฟ้าแลบตามขอบหน้าจอ
 */
@Composable
private fun ElectricSparksForeground() {
    val transition = rememberInfiniteTransition(label = "electric_sparks")
    val sparkPhase by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart),
        label = "spark"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (sparkPhase in 0.15f..0.35f || sparkPhase in 0.65f..0.85f) {
            val startCorner = if (sparkPhase < 0.5f) Offset(10f, size.height * 0.3f) else Offset(size.width - 10f, size.height * 0.7f)
            val dir = if (sparkPhase < 0.5f) 1f else -1f
            val bolt = Path().apply {
                moveTo(startCorner.x, startCorner.y)
                lineTo(startCorner.x + 24f * dir, startCorner.y - 18f)
                lineTo(startCorner.x + 14f * dir, startCorner.y - 28f)
                lineTo(startCorner.x + 38f * dir, startCorner.y - 48f)
            }
            drawPath(bolt, color = Color(0xFF00E5FF).copy(alpha = 0.85f), style = Stroke(2.5f, cap = StrokeCap.Round))
            drawPath(bolt, color = Color.White.copy(alpha = 0.95f), style = Stroke(1.2f, cap = StrokeCap.Round))
        }
    }
}

/**
 * FALLING_PETALS — กลีบซากุระแดง/ชมพูปลิวผ่านหน้ากล้องระยะใกล้ (Close Parallax)
 */
@Composable
private fun FallingPetalsForeground() {
    val transition = rememberInfiniteTransition(label = "falling_petals")
    val fallProg by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "fall"
    )
    val petals = remember {
        List(10) {
            FgParticle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.6f + 0.4f, Random.nextFloat() * 0.4f + 0.4f, Random.nextFloat() * 16f + 12f, Random.nextFloat() * 360f, Random.nextFloat() * 6.28f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        petals.forEach { p ->
            val py = (p.y + fallProg * p.speed) % 1f * size.height
            val px = p.x * size.width + sin((py * 0.03f + p.extra).toDouble()).toFloat() * 35f
            val pRot = p.rot + fallProg * 180f
            val squash = cos((fallProg * 4f * PI + p.extra).toDouble()).toFloat().absoluteValue
            rotate(pRot, pivot = Offset(px, py)) {
                drawOval(
                    color = Color(0xFFFF4081).copy(alpha = p.alpha * 0.75f),
                    topLeft = Offset(px - p.size / 2f, py - (p.size * squash) / 2f),
                    size = Size(p.size, p.size * squash)
                )
            }
        }
    }
}

/**
 * CONFETTI_TUMBLE — สะเก็ดฟอยล์สี 3D พลิ้วหมุนผ่านหน้ากล้อง
 */
@Composable
private fun ConfettiTumbleForeground() {
    val transition = rememberInfiniteTransition(label = "confetti_tumble")
    val fallProg by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "tumble"
    )
    val confetti = remember {
        val colors = listOf(Color(0xFFFF1744), Color(0xFF00E5FF), Color(0xFFFFEA00), Color(0xFF76FF03), Color(0xFFE040FB))
        List(18) {
            FgParticle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.6f + 0.4f, Random.nextFloat() * 0.3f + 0.6f, Random.nextFloat() * 12f + 8f, Random.nextFloat() * 360f, colors[it % colors.size].toArgb().toFloat())
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        confetti.forEach { c ->
            val py = (c.y + fallProg * c.speed) % 1f * size.height
            val px = c.x * size.width + sin((py * 0.04f).toDouble()).toFloat() * 25f
            val rot = c.rot + fallProg * 360f
            val squash = cos((rot * PI.toFloat() / 90f).toDouble()).toFloat().absoluteValue
            rotate(rot, pivot = Offset(px, py)) {
                drawRect(
                    color = Color(c.extra.toInt()).copy(alpha = c.alpha),
                    topLeft = Offset(px - c.size / 2f, py - (c.size * 0.6f * squash) / 2f),
                    size = Size(c.size, c.size * 0.6f * squash)
                )
            }
        }
    }
}

/**
 * GOLDEN_SHINE — ประกายเพชรและเหรียญทองแวววาว 4 แฉก
 */
@Composable
private fun GoldenShineForeground() {
    val transition = rememberInfiniteTransition(label = "golden_shine")
    val shineProg by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "shine"
    )
    val flares = remember {
        List(10) {
            FgParticle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.4f + 0.3f, Random.nextFloat() * 0.5f + 0.4f, Random.nextFloat() * 14f + 10f, 0f, Random.nextFloat() * 6.28f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        flares.forEach { f ->
            val fx = f.x * size.width
            val fy = f.y * size.height
            val alpha = (f.alpha * sin((shineProg * 2f * PI + f.extra).toDouble()).toFloat().absoluteValue).coerceIn(0f, 0.9f)
            drawSparkleCross(Offset(fx, fy), f.size, Color(0xFFFFD700).copy(alpha = alpha))
        }
    }
}

/**
 * HEART_ORBS — ลูกแก้วหัวใจโปร่งแสงลอยละล่องขึ้นหน้าจอ
 */
@Composable
private fun HeartOrbsForeground() {
    val transition = rememberInfiniteTransition(label = "heart_orbs")
    val riseProg by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Restart),
        label = "heart_rise"
    )
    val hearts = remember {
        List(12) {
            FgParticle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.5f + 0.3f, Random.nextFloat() * 0.35f + 0.25f, Random.nextFloat() * 14f + 10f, 0f, Random.nextFloat() * 6.28f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        hearts.forEach { h ->
            val hy = ((h.y - riseProg * h.speed) % 1f + 1f) % 1f * size.height
            val hx = h.x * size.width + sin((hy * 0.04f + h.extra).toDouble()).toFloat() * 16f
            drawPetHeart(Offset(hx, hy), h.size, Color(0xFFFF4081).copy(alpha = h.alpha * 0.6f))
        }
    }
}

/**
 * BUBBLE_FLOAT — ฟองอากาศโปร่งใสลอยผุดผ่านหน้าเลนส์
 */
@Composable
private fun BubbleFloatForeground() {
    val transition = rememberInfiniteTransition(label = "bubble_float")
    val bubbleProg by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2900, easing = LinearEasing), RepeatMode.Restart),
        label = "bubble"
    )
    val bubbles = remember {
        List(14) {
            FgParticle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.5f + 0.4f, Random.nextFloat() * 0.4f + 0.25f, Random.nextFloat() * 16f + 8f, 0f, Random.nextFloat() * 6.28f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        bubbles.forEach { b ->
            val by = ((b.y - bubbleProg * b.speed) % 1f + 1f) % 1f * size.height
            val bx = b.x * size.width + sin((by * 0.035f + b.extra).toDouble()).toFloat() * 15f

            // Bubble body
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = b.alpha * 0.35f),
                radius = b.size,
                center = Offset(bx, by),
                style = Stroke(width = 1.4f)
            )
            // Specular glint
            drawCircle(
                color = Color.White.copy(alpha = b.alpha * 0.7f),
                radius = b.size * 0.28f,
                center = Offset(bx - b.size * 0.35f, by - b.size * 0.35f)
            )
        }
    }
}

/**
 * ANAMORPHIC_FLARE — ลำแสงสะท้อนแนวนอนสีไซแอน (Anamorphic Streak)
 */
@Composable
private fun AnamorphicFlareForeground() {
    val transition = rememberInfiniteTransition(label = "anamorphic_flare")
    val pulse by transition.animateFloat(
        0.08f, 0.22f,
        infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "flare"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val y = size.height * 0.45f
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color(0xFF00F5FF).copy(alpha = pulse), Color.White.copy(alpha = pulse * 1.5f), Color(0xFF00F5FF).copy(alpha = pulse), Color.Transparent)
            ),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 2.4f
        )
    }
}

/**
 * CRT_SCANLINES — เส้นสแกนจอแก้ว CRT ยุค 90s พร้อมมืดขอบภาพ
 */
@Composable
private fun CrtScanlinesForeground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // CRT dark vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.65f)),
                center = center,
                radius = size.maxDimension * 0.7f
            )
        )

        // Scanline bars
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.22f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.2f
            )
            y += 4f
        }
    }
}

/**
 * CAMERA_VIEWFINDER — กรอบเล็งถ่ายภาพมืออาชีพ + จุดโฟกัส และ REC กะพริบ
 */
@Composable
private fun CameraViewfinderForeground() {
    val transition = rememberInfiniteTransition(label = "camera_viewfinder")
    val recBlink by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "rec"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val pad = 24f
        val len = 28f
        val w = 2.2f
        val white = Color.White.copy(alpha = 0.65f)

        // Viewfinder corners
        drawLine(white, Offset(pad, pad), Offset(pad + len, pad), w)
        drawLine(white, Offset(pad, pad), Offset(pad, pad + len), w)

        drawLine(white, Offset(size.width - pad, pad), Offset(size.width - pad - len, pad), w)
        drawLine(white, Offset(size.width - pad, pad), Offset(size.width - pad, pad + len), w)

        drawLine(white, Offset(pad, size.height - pad), Offset(pad + len, size.height - pad), w)
        drawLine(white, Offset(pad, size.height - pad), Offset(pad, size.height - pad - len), w)

        drawLine(white, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - len, size.height - pad), w)
        drawLine(white, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - len), w)

        // Center crosshair
        val cx = center.x
        val cy = center.y
        drawLine(white.copy(alpha = 0.4f), Offset(cx - 16f, cy), Offset(cx + 16f, cy), 1.5f)
        drawLine(white.copy(alpha = 0.4f), Offset(cx, cy - 16f), Offset(cx, cy + 16f), 1.5f)

        // Blinking Red REC Dot at top left
        val recAlpha = if (recBlink > 0.5f) 0.95f else 0.15f
        drawCircle(
            color = Color(0xFFFF1744).copy(alpha = recAlpha),
            radius = 5.5f,
            center = Offset(pad + 44f, pad + 14f)
        )
    }
}

/** Helper drawing 4-point sparkle star */
private fun DrawScope.drawSparkleCross(center: Offset, size: Float, color: Color) {
    drawLine(color, Offset(center.x - size, center.y), Offset(center.x + size, center.y), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(color, Offset(center.x, center.y - size), Offset(center.x, center.y + size), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawCircle(color, radius = size * 0.28f, center = center)
}

/** Helper drawing bezier heart */
private fun DrawScope.drawPetHeart(center: Offset, size: Float, color: Color) {
    val s = size
    val heartPath = Path().apply {
        moveTo(center.x, center.y + s * 0.5f)
        cubicTo(center.x - s * 1.1f, center.y - s * 0.2f, center.x - s * 0.6f, center.y - s * 0.95f, center.x, center.y - s * 0.3f)
        cubicTo(center.x + s * 0.6f, center.y - s * 0.95f, center.x + s * 1.1f, center.y - s * 0.2f, center.x, center.y + s * 0.5f)
        close()
    }
    drawPath(path = heartPath, color = color)
}

package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import kotlin.math.*
import kotlin.random.Random

/**
 * PetBackgroundLayer — คอมโพเนนต์ฉากหลังไดนามิกแอนิเมชันสำหรับ Virtual Desk Pet robot avatar
 * Dynamic animated background layer for the Virtual Desk Pet robot avatar.
 *
 * แสดงฉากหลังตาม [BackgroundTheme] พร้อม [Crossfade] เพื่อการสลับฉากที่ราบรื่น
 * วาดด้วย Canvas particles และควบคุมเฟรมด้วย [rememberInfiniteTransition]
 *
 * @param theme ธีมฉากหลังปัจจุบัน ([BackgroundTheme])
 * @param modifier Modifier สำหรับปรับแต่งคอมโพเนนต์
 */
@Composable
fun PetBackgroundLayer(
    theme: BackgroundTheme,
    modifier: Modifier = Modifier
) {
    Crossfade(
        targetState = theme,
        modifier = modifier,
        animationSpec = tween(600, easing = EaseInOutCubic),
        label = "PetBgCrossfade"
    ) { currentTheme ->
        when (currentTheme) {
            BackgroundTheme.DEFAULT -> DefaultBackground()
            BackgroundTheme.RAINY -> RainyBackground()
            BackgroundTheme.SUNNY -> SunnyBackground()
            BackgroundTheme.NIGHT -> NightBackground()
            BackgroundTheme.SAKURA -> SakuraBackground()
            BackgroundTheme.MATRIX -> MatrixBackground()
            BackgroundTheme.LOVE_BG -> LoveBackground()
            BackgroundTheme.THUNDER -> ThunderBackground()
        }
    }
}

/**
 * ข้อมูลอนุภาคสำหรับฉากหลังแอนิเมชัน (Particle data class)
 */
private data class Particle(
    var x: Float,
    var y: Float,
    var speed: Float,
    var alpha: Float = 1f,
    var size: Float = 3f,
    var extra: Float = 0f
)

/**
 * DEFAULT — โทนมืดสนิท (Pure Dark OLED Tone) ไม่มีฉากหลังไดนามิก เพื่อให้หน้าตาหุ่นยนต์ LOOI โดดเด่น ชัดเจน และประหยัดพลังงาน
 */
@Composable
private fun DefaultBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = Color(0xFF000000))
    }
}

/**
 * RAINY — พื้นหลังฟ้า-เทาแนวตั้ง (0xFF2C3E50 -> 0xFF1A252F) พร้อมเส้นฝนตก ~30 เส้น
 * Blue-gray vertical gradient with ~30 thin semi-transparent falling raindrops.
 */
@Composable
private fun RainyBackground() {
    val transition = rememberInfiniteTransition(label = "rainy_bg")
    val rainProgress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "rain"
    )
    val drops = remember {
        List(30) {
            Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.6f + 0.9f, Random.nextFloat() * 0.35f + 0.25f, Random.nextFloat() * 18f + 14f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF2C3E50), Color(0xFF1A252F))))
        drops.forEach { drop ->
            val y = ((drop.y + rainProgress * drop.speed) % 1f) * (size.height + drop.size) - drop.size
            val x = drop.x * size.width
            drawLine(
                color = Color(0xCCE0E6ED).copy(alpha = drop.alpha),
                start = Offset(x, y),
                end = Offset(x - 3f, y + drop.size),
                strokeWidth = 1.6f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * SUNNY — ไล่สีส้ม-เหลืองอบอุ่น (0xFFFFF3E0, 0xFFFFE082, 0xFFF57F17) พร้อมรัศมีแสงอาทิตย์หมุน
 * Warm yellow-orange radial gradient with pulsing sun rays radiating from top-center.
 */
@Composable
private fun SunnyBackground() {
    val transition = rememberInfiniteTransition(label = "sunny_bg")
    val rayRotation by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(36000, easing = LinearEasing), RepeatMode.Restart),
        label = "ray_rot"
    )
    val rayPulse by transition.animateFloat(
        0.12f, 0.28f,
        infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "ray_pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val sunCenter = Offset(size.width * 0.5f, 0f)
        val maxRadius = max(size.width, size.height) * 1.2f
        drawRect(brush = Brush.radialGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFFE082), Color(0xFFF57F17)), sunCenter, maxRadius))

        val rayCount = 12
        val sweepRad = (360f / rayCount / 2f) * (PI.toFloat() / 180f)
        for (i in 0 until rayCount) {
            val centerRad = (rayRotation + i * (360f / rayCount)) * (PI.toFloat() / 180f)
            val p1 = Offset(sunCenter.x + cos(centerRad - sweepRad * 0.5f) * maxRadius, sunCenter.y + sin(centerRad - sweepRad * 0.5f) * maxRadius)
            val p2 = Offset(sunCenter.x + cos(centerRad + sweepRad * 0.5f) * maxRadius, sunCenter.y + sin(centerRad + sweepRad * 0.5f) * maxRadius)
            val path = Path().apply { moveTo(sunCenter.x, sunCenter.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); close() }
            drawPath(path, color = Color(0xFFFFF9C4).copy(alpha = rayPulse))
        }
        drawCircle(Color.White.copy(alpha = 0.45f), radius = size.width * 0.25f, center = sunCenter)
        drawCircle(Color(0xFFFFF59D).copy(alpha = 0.3f), radius = size.width * 0.42f, center = sunCenter)
    }
}

/**
 * NIGHT — พื้นหลังกรมท่าเข้ม-ดำ (0xFF0D1B2A -> Black) พร้อมดวงดาวระยิบระยับ ~40 ดวง
 * Dark navy-to-black vertical gradient with ~40 twinkling stars.
 */
@Composable
private fun NightBackground() {
    val transition = rememberInfiniteTransition(label = "night_bg")
    val twinkleTime by transition.animateFloat(
        0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "twinkle"
    )
    val stars = remember {
        List(40) {
            Particle(Random.nextFloat(), Random.nextFloat() * 0.95f, Random.nextFloat() * 1.6f + 0.8f, Random.nextFloat() * 0.4f + 0.45f, Random.nextFloat() * 2f + 1f, Random.nextFloat() * 2f * PI.toFloat())
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF050B14), Color.Black)))
        stars.forEach { star ->
            val twinkle = sin(twinkleTime * star.speed + star.extra)
            val currentAlpha = (star.alpha + 0.35f * twinkle).coerceIn(0.1f, 1f)
            val pos = Offset(star.x * size.width, star.y * size.height)
            drawCircle(Color.White.copy(alpha = currentAlpha), radius = star.size, center = pos)
            if (star.size > 2.2f && currentAlpha > 0.65f) {
                val flare = star.size * 2.6f
                val flareAlpha = (currentAlpha - 0.5f).coerceAtLeast(0f)
                drawLine(Color.White.copy(alpha = flareAlpha), Offset(pos.x - flare, pos.y), Offset(pos.x + flare, pos.y), 1f)
                drawLine(Color.White.copy(alpha = flareAlpha), Offset(pos.x, pos.y - flare), Offset(pos.x, pos.y + flare), 1f)
            }
        }
    }
}

/**
 * SAKURA — ไล่สีชมพูอ่อน (0xFFFCE4EC -> 0xFFF8BBD0) พร้อมกลีบซากุระปลิวเฉียงและหมุนวน ~15 กลีบ
 * Soft pink vertical gradient with ~15 cherry blossom petals drifting diagonally with slow rotation.
 */
@Composable
private fun SakuraBackground() {
    val transition = rememberInfiniteTransition(label = "sakura_bg")
    val progress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "sakura"
    )
    val petals = remember {
        List(15) {
            Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.45f + 0.3f, Random.nextFloat() * 0.35f + 0.6f, Random.nextFloat() * 7f + 9f, Random.nextFloat() * 360f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.verticalGradient(listOf(Color(0xFFFCE4EC), Color(0xFFF8BBD0))))
        petals.forEach { petal ->
            val y = ((petal.y + progress * petal.speed) % 1f) * (size.height + petal.size * 3) - petal.size
            val sway = sin(progress * 2f * PI.toFloat() * 2.5f + petal.extra) * 24f
            val x = (petal.x * size.width + progress * size.width * 0.35f + sway) % size.width
            val angle = (petal.extra + progress * 360f * (if (petal.speed > 0.5f) 1f else -1f)) % 360f

            rotate(degrees = angle, pivot = Offset(x, y)) {
                drawOval(
                    color = Color(0xFFF06292).copy(alpha = petal.alpha),
                    topLeft = Offset(x - petal.size * 0.45f, y - petal.size * 0.75f),
                    size = Size(petal.size * 0.9f, petal.size * 1.5f)
                )
                drawOval(
                    color = Color(0xFFFFD1DC).copy(alpha = petal.alpha * 0.8f),
                    topLeft = Offset(x - petal.size * 0.25f, y - petal.size * 0.5f),
                    size = Size(petal.size * 0.5f, petal.size * 1f)
                )
            }
        }
    }
}

/**
 * MATRIX — พื้นหลังดำสนิท พร้อมหยดโค้ดดิจิทัลสีเขียวนีออน (0xFF00FF41) ไหลลงมา ~20 คอลัมน์
 * Solid black background with ~20 columns of falling neon-green digital stream dots.
 */
@Composable
private fun MatrixBackground() {
    val transition = rememberInfiniteTransition(label = "matrix_bg")
    val progress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "matrix"
    )
    val columns = remember {
        List(20) { index ->
            Particle((index + 0.5f) / 20f, Random.nextFloat(), Random.nextFloat() * 0.7f + 0.6f, 1f, Random.nextFloat() * 1.5f + 3f, Random.nextInt(5, 10).toFloat())
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = Color.Black)
        val dotSpacing = 14f
        columns.forEach { col ->
            val colX = col.x * size.width
            val headY = ((col.y + progress * col.speed) % 1f) * (size.height + 120f) - 60f
            val trailCount = col.extra.toInt()
            for (t in 0 until trailCount) {
                val dotY = headY - t * dotSpacing
                if (dotY < -10f || dotY > size.height + 10f) continue
                if (t == 0) {
                    drawCircle(Color(0xFFE8FFE8), radius = col.size * 1.2f, center = Offset(colX, dotY))
                } else {
                    val trailAlpha = (1f - (t.toFloat() / trailCount)).coerceIn(0.08f, 0.85f)
                    drawCircle(Color(0xFF00FF41).copy(alpha = trailAlpha), radius = col.size * (1f - t * 0.05f).coerceAtLeast(1.8f), center = Offset(colX, dotY))
                }
            }
        }
    }
}

/**
 * LOVE_BG — ไล่สีชมพู-แดง (0xFFFFCDD2 -> 0xFFE91E63) พร้อมหัวใจลอยขึ้นสู่ด้านบน ~8 ดวง
 * Radial gradient pink-red with ~8 floating heart shapes gently rising upward.
 */
@Composable
private fun LoveBackground() {
    val transition = rememberInfiniteTransition(label = "love_bg")
    val progress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(5500, easing = LinearEasing), RepeatMode.Restart),
        label = "love"
    )
    val hearts = remember {
        List(8) {
            Particle(Random.nextFloat() * 0.84f + 0.08f, Random.nextFloat(), Random.nextFloat() * 0.35f + 0.35f, Random.nextFloat() * 0.35f + 0.55f, Random.nextFloat() * 8f + 12f, Random.nextFloat() * 2f * PI.toFloat())
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val maxDim = max(size.width, size.height)
        drawRect(brush = Brush.radialGradient(listOf(Color(0xFFFFCDD2), Color(0xFFF06292), Color(0xFFE91E63)), center, maxDim * 0.85f))
        hearts.forEach { heart ->
            val y = ((heart.y - progress * heart.speed) % 1f + 1f) % 1f * (size.height + 60f) - 30f
            val sway = sin(progress * 2f * PI.toFloat() * 1.5f + heart.extra) * 16f
            drawPetHeart(Offset(heart.x * size.width + sway, y), heart.size, Color.White.copy(alpha = heart.alpha))
        }
    }
}

/**
 * THUNDER — พื้นหลังเทาพายุเข้ม (0xFF37474F -> 0xFF263238) พร้อมแสงแฟลชฟ้าแลบทุก 3-5 วินาที
 * Stormy vertical gradient with lightning flash alpha spikes every 3-5 seconds and heavy rain.
 */
@Composable
private fun ThunderBackground() {
    val transition = rememberInfiniteTransition(label = "thunder_bg")
    val flashAlpha by transition.animateFloat(
        0f, 0f,
        infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                0f at 0
                0f at 2800
                0.85f at 2860
                0.15f at 2920
                0.95f at 2970
                0.25f at 3060
                0f at 3200
                0f at 4000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "flash"
    )
    val rainProgress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Restart),
        label = "rain"
    )
    val rainDrops = remember {
        List(25) {
            Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.8f + 1.2f, Random.nextFloat() * 0.3f + 0.2f, Random.nextFloat() * 22f + 16f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF37474F), Color(0xFF263238))))
        rainDrops.forEach { drop ->
            val y = ((drop.y + rainProgress * drop.speed) % 1f) * (size.height + drop.size) - drop.size
            val x = drop.x * size.width
            drawLine(Color(0x99CFD8DC).copy(alpha = drop.alpha), Offset(x, y), Offset(x - 6f, y + drop.size), 1.8f, StrokeCap.Round)
        }

        if (flashAlpha > 0.02f) {
            drawRect(color = Color.White.copy(alpha = flashAlpha * 0.8f))
            if (flashAlpha > 0.5f) {
                val bolt = Path().apply {
                    val sx = size.width * 0.6f
                    moveTo(sx, 0f); lineTo(sx - 18f, size.height * 0.22f); lineTo(sx + 8f, size.height * 0.25f)
                    lineTo(sx - 25f, size.height * 0.48f); lineTo(sx - 5f, size.height * 0.52f); lineTo(sx - 32f, size.height * 0.8f)
                }
                drawPath(bolt, color = Color.White.copy(alpha = flashAlpha), style = Stroke(3.5f, cap = StrokeCap.Round))
                drawPath(bolt, color = Color(0xFF80D8FF).copy(alpha = flashAlpha * 0.6f), style = Stroke(7f, cap = StrokeCap.Round))
            }
        }
    }
}

/**
 * วาดรูปหัวใจ (Heart shape) ด้วย Path และ Bézier curves
 * Draws a bezier heart shape centered at [center].
 */
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

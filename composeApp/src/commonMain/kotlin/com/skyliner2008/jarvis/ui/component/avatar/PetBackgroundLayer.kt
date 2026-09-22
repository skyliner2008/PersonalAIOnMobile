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
            // ─── 50 Moodset Dynamic Environments ───
            BackgroundTheme.CYBER_GRID -> CyberGridBackground()
            BackgroundTheme.SPACE_NEBULA -> SpaceNebulaBackground()
            BackgroundTheme.MAGIC_MYSTIC -> MagicMysticBackground()
            BackgroundTheme.CINEMA_COZY -> CinemaCozyBackground()
            BackgroundTheme.WINTER_BLIZZARD -> WinterBlizzardBackground()
            BackgroundTheme.SUMMER_HEAT -> SummerHeatBackground()
            BackgroundTheme.WARRIOR_DOJO -> WarriorDojoBackground()
            BackgroundTheme.PARTY_CONFETTI -> PartyConfettiBackground()
            BackgroundTheme.GOLDEN_VAULT -> GoldenVaultBackground()
            BackgroundTheme.SICK_LAB -> SickLabBackground()
            BackgroundTheme.SPORTS_ARENA -> SportsArenaBackground()
            BackgroundTheme.LOW_POWER_CRT -> LowPowerCrtBackground()
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
 * DEFAULT — มืดสนิท OLED พร้อมออร่าหายใจแผ่วเบาเพื่อมิติความลึก (Subtle Organic Depth Aura)
 */
@Composable
private fun DefaultBackground() {
    val detailLevel = LocalAvatarDetailLevel.current
    val transition = rememberInfiniteTransition(label = "default_bg")
    val pulse by transition.animateFloat(
        initialValue = 0.03f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = Color(0xFF000000))
        if (detailLevel == AvatarDetailLevel.RICH) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00F5FF).copy(alpha = pulse),
                        Color(0xFF001A29).copy(alpha = pulse * 0.4f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.65f
                )
            )
        }
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

// ═════════════════════════════════════════════════════════════════════════════
// 50 MOODSET DYNAMIC BACKGROUND ENVIRONMENTS
// ═════════════════════════════════════════════════════════════════════════════

/**
 * CYBER_GRID — ไซเบอร์กริดมุมมอง 3D Perspective + ละอองข้อมูลดิจิทัล
 */
@Composable
private fun CyberGridBackground() {
    val transition = rememberInfiniteTransition(label = "cyber_grid")
    val gridProgress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "grid"
    )
    val particles = remember {
        List(20) {
            Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.4f + 0.2f, Random.nextFloat() * 0.4f + 0.2f, Random.nextFloat() * 2f + 1.5f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF020714), Color(0xFF000308))))

        val vpX = size.width / 2f
        val vpH = size.height * 0.38f

        // Perspective vertical rays
        for (i in -4..4) {
            val bottomX = vpX + i * (size.width * 0.18f)
            drawLine(
                color = Color(0xFF00F5FF).copy(alpha = 0.12f),
                start = Offset(vpX, vpH),
                end = Offset(bottomX, size.height),
                strokeWidth = 1.2f
            )
        }

        // Perspective moving horizontal lines
        for (i in 0..7) {
            val normY = ((i + gridProgress) / 8f).coerceIn(0f, 1f)
            val y = vpH + normY * normY * (size.height - vpH)
            val lineAlpha = (normY * 0.25f).coerceIn(0f, 0.28f)
            drawLine(
                color = Color(0xFF00F5FF).copy(alpha = lineAlpha),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.2f
            )
        }

        // Digital floating data particles
        particles.forEach { p ->
            val py = ((p.y - gridProgress * p.speed) % 1f + 1f) % 1f * size.height
            val px = p.x * size.width
            drawCircle(
                color = Color(0xFF00F5FF).copy(alpha = p.alpha * 0.7f),
                radius = p.size,
                center = Offset(px, py)
            )
        }
    }
}

/**
 * SPACE_NEBULA — ห้วงอวกาศลึก 2 ชั้น (ดวงดาวระยิบระยับ + กลุ่มก๊าซเนบิวลาจักรวาล)
 */
@Composable
private fun SpaceNebulaBackground() {
    val transition = rememberInfiniteTransition(label = "space_nebula")
    val pulse by transition.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(3600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "nebula_pulse"
    )
    val starRot by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(48000, easing = LinearEasing), RepeatMode.Restart),
        label = "star_rot"
    )
    val stars = remember {
        List(35) {
            Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.8f + 0.2f, Random.nextFloat() * 0.6f + 0.3f, Random.nextFloat() * 2.5f + 1f, Random.nextFloat())
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.radialGradient(
            colors = listOf(Color(0xFF0D0624), Color(0xFF04010E), Color(0xFF000000)),
            center = center,
            radius = size.maxDimension * 0.8f
        ))

        // Cosmic Nebula Clouds
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF6200EA).copy(alpha = 0.08f * pulse), Color(0xFF00E5FF).copy(alpha = 0.04f * pulse), Color.Transparent),
                center = Offset(size.width * 0.35f, size.height * 0.4f),
                radius = size.minDimension * 0.6f
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFF007F).copy(alpha = 0.06f * pulse), Color.Transparent),
                center = Offset(size.width * 0.7f, size.height * 0.6f),
                radius = size.minDimension * 0.5f
            )
        )

        // Twinkling stars
        stars.forEach { star ->
            val twinkle = (sin((starRot * 0.05f + star.extra * 10f).toDouble()).toFloat() * 0.4f + 0.6f)
            val sx = star.x * size.width
            val sy = star.y * size.height
            val color = if (star.speed > 0.6f) Color(0xFF80D8FF) else Color.White
            drawCircle(
                color = color.copy(alpha = (star.alpha * twinkle).coerceIn(0f, 1f)),
                radius = star.size,
                center = Offset(sx, sy)
            )
        }
    }
}

/**
 * MAGIC_MYSTIC — หมอกมนตราสีม่วง-อินดิโก + ละอองอักขระเวทมนตร์ลอยหมุนวน
 */
@Composable
private fun MagicMysticBackground() {
    val transition = rememberInfiniteTransition(label = "magic_mystic")
    val rot by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "rune_rot"
    )
    val runePulse by transition.animateFloat(
        0.1f, 0.28f,
        infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "rune_pulse"
    )
    val particles = remember {
        List(22) {
            Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.5f + 0.3f, Random.nextFloat() * 0.5f + 0.4f, Random.nextFloat() * 3f + 1.5f, Random.nextFloat() * 360f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1F0338), Color(0xFF090014), Color(0xFF000000)),
            center = center,
            radius = size.minDimension * 0.85f
        ))

        // Arcane rune circle in background
        val ringRadius = size.minDimension * 0.48f
        rotate(rot, pivot = center) {
            drawCircle(
                color = Color(0xFFBA68C8).copy(alpha = runePulse),
                radius = ringRadius,
                style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
            )
            drawCircle(
                color = Color(0xFFFFD54F).copy(alpha = runePulse * 0.7f),
                radius = ringRadius * 0.82f,
                style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 16f)))
            )
        }

        // Floating mystic sparkles
        particles.forEach { p ->
            val py = ((p.y - (rot / 360f) * p.speed) % 1f + 1f) % 1f * size.height
            val px = p.x * size.width + sin((py * 0.05f).toDouble()).toFloat() * 14f
            drawCircle(
                color = if (p.speed > 0.5f) Color(0xFFFFD54F) else Color(0xFFE040FB),
                radius = p.size,
                center = Offset(px, py)
            )
        }
    }
}

/**
 * CINEMA_COZY — บรรยากาศคาเฟ่/โรงหนัง แสงไฟโบเก้อบอุ่นนุ่มลึก
 */
@Composable
private fun CinemaCozyBackground() {
    val transition = rememberInfiniteTransition(label = "cinema_cozy")
    val breathe by transition.animateFloat(
        0.7f, 1.15f,
        infiniteRepeatable(tween(3200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "breathe"
    )
    val bokehList = remember {
        List(14) {
            Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.2f + 0.1f, Random.nextFloat() * 0.25f + 0.15f, Random.nextFloat() * 45f + 35f, Random.nextFloat())
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1A0E04), Color(0xFF080401), Color(0xFF000000)),
            center = center,
            radius = size.maxDimension * 0.75f
        ))

        // Warm out-of-focus bokeh lamps
        bokehList.forEach { b ->
            val bx = b.x * size.width
            val by = b.y * size.height
            val color = if (b.extra > 0.5f) Color(0xFFFFB300) else Color(0xFFFF7043)
            drawCircle(
                color = color.copy(alpha = (b.alpha * breathe).coerceIn(0f, 0.35f)),
                radius = b.size * breathe,
                center = Offset(bx, by)
            )
        }
    }
}

/**
 * WINTER_BLIZZARD — พายุหิมะโปรยปราย + ลมหนาวพัดเฉียง
 */
@Composable
private fun WinterBlizzardBackground() {
    val transition = rememberInfiniteTransition(label = "winter_blizzard")
    val snowProgress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "snow"
    )
    val flakes = remember {
        List(35) {
            Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.7f + 0.8f, Random.nextFloat() * 0.5f + 0.3f, Random.nextFloat() * 3.5f + 1.5f, Random.nextFloat() * 6.28f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF071524), Color(0xFF020912))))

        flakes.forEach { flake ->
            val prog = (flake.y + snowProgress * flake.speed) % 1f
            val fy = prog * size.height
            val fx = (flake.x * size.width + sin((prog * 4f * PI + flake.extra).toDouble()).toFloat() * 18f - prog * 28f) % size.width
            drawCircle(
                color = Color.White.copy(alpha = flake.alpha * 0.85f),
                radius = flake.size,
                center = Offset(if (fx < 0) fx + size.width else fx, fy)
            )
        }
    }
}

/**
 * SUMMER_HEAT — แสงแดดแผดเผา + รัศมีสุริยะและคลื่นไอความร้อน
 */
@Composable
private fun SummerHeatBackground() {
    val transition = rememberInfiniteTransition(label = "summer_heat")
    val sunRot by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(36000, easing = LinearEasing), RepeatMode.Restart),
        label = "sun_rot"
    )
    val heatShimmer by transition.animateFloat(
        0.85f, 1.15f,
        infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "heat"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.radialGradient(
            colors = listOf(Color(0xFF2E1000), Color(0xFF120400), Color(0xFF000000)),
            center = Offset(size.width * 0.5f, size.height * 0.25f),
            radius = size.maxDimension * 0.75f
        ))

        // Sunburst rays
        rotate(sunRot, pivot = Offset(size.width * 0.5f, size.height * 0.25f)) {
            val rayCount = 12
            val sweep = 360f / rayCount
            for (i in 0 until rayCount step 2) {
                drawArc(
                    color = Color(0xFFFFB300).copy(alpha = 0.05f * heatShimmer),
                    startAngle = i * sweep,
                    sweepAngle = sweep * 0.7f,
                    useCenter = true,
                    topLeft = Offset(size.width * 0.5f - size.maxDimension, size.height * 0.25f - size.maxDimension),
                    size = Size(size.maxDimension * 2f, size.maxDimension * 2f)
                )
            }
        }
    }
}

/**
 * WARRIOR_DOJO — โดโจซามูไร ท้องฟ้าสีชาดเลือดหมู + ประกายไฟลอยพวยพุ่ง
 */
@Composable
private fun WarriorDojoBackground() {
    val transition = rememberInfiniteTransition(label = "warrior_dojo")
    val fireProgress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "fire"
    )
    val sparks = remember {
        List(25) {
            Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.6f + 0.4f, Random.nextFloat() * 0.6f + 0.4f, Random.nextFloat() * 3f + 1f, Random.nextFloat() * 6.28f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF240306), Color(0xFF0B0002), Color(0xFF000000))))

        // Rising fire sparks & embers
        sparks.forEach { s ->
            val prog = ((s.y - fireProgress * s.speed) % 1f + 1f) % 1f
            val sy = prog * size.height
            val sx = s.x * size.width + sin((prog * 3f * PI + s.extra).toDouble()).toFloat() * 20f
            val color = if (s.speed > 0.6f) Color(0xFFFFAB00) else Color(0xFFFF3D00)
            drawCircle(
                color = color.copy(alpha = (s.alpha * (1f - prog * 0.6f)).coerceIn(0f, 1f)),
                radius = s.size,
                center = Offset(sx, sy)
            )
        }
    }
}

/**
 * PARTY_CONFETTI — งานสังสรรค์ฉลอง สปอตไลต์หลายเฉดสี
 */
@Composable
private fun PartyConfettiBackground() {
    val transition = rememberInfiniteTransition(label = "party_confetti")
    val spotRot by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "spot"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.radialGradient(
            colors = listOf(Color(0xFF140424), Color(0xFF05000C), Color(0xFF000000)),
            center = center,
            radius = size.maxDimension * 0.7f
        ))

        // Rotating colorful party spotlight cones
        val colors = listOf(Color(0xFF00E5FF), Color(0xFFFF007F), Color(0xFFFFEA00))
        for (i in colors.indices) {
            val angle = spotRot + i * 120f
            val rad = angle * PI.toFloat() / 180f
            val beamX = center.x + cos(rad.toDouble()).toFloat() * size.width * 0.4f
            val beamY = center.y + sin(rad.toDouble()).toFloat() * size.height * 0.4f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors[i].copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(beamX, beamY),
                    radius = size.minDimension * 0.45f
                )
            )
        }
    }
}

/**
 * GOLDEN_VAULT — คลังสมบัติทองคำ ประกายเหรียญทองและลำแสงรวย
 */
@Composable
private fun GoldenVaultBackground() {
    val transition = rememberInfiniteTransition(label = "golden_vault")
    val coinProgress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "coins"
    )
    val shinePulse by transition.animateFloat(
        0.05f, 0.18f,
        infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "shine"
    )
    val coins = remember {
        List(22) {
            Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.5f + 0.4f, Random.nextFloat() * 0.5f + 0.4f, Random.nextFloat() * 4f + 3f, Random.nextFloat() * 6.28f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.radialGradient(
            colors = listOf(Color(0xFF2E2002), Color(0xFF0E0A00), Color(0xFF000000)),
            center = center,
            radius = size.maxDimension * 0.7f
        ))

        // Center golden radiance
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFD54F).copy(alpha = shinePulse), Color.Transparent),
                center = center,
                radius = size.minDimension * 0.65f
            )
        )

        // Falling gold coin particles
        coins.forEach { coin ->
            val prog = (coin.y + coinProgress * coin.speed) % 1f
            val cy = prog * size.height
            val cx = coin.x * size.width
            val coinSquash = kotlin.math.abs(cos((prog * 8f * PI + coin.extra).toDouble())).toFloat()
            drawOval(
                color = Color(0xFFFFD700).copy(alpha = coin.alpha),
                topLeft = Offset(cx - coin.size, cy - coin.size * coinSquash),
                size = Size(coin.size * 2f, coin.size * 2f * coinSquash)
            )
        }
    }
}

/**
 * SICK_LAB — ห้องทดลองชีวเคมี แสงเรืองชีวภาพสีเขียวมรกต + ฟองเดือด
 */
@Composable
private fun SickLabBackground() {
    val transition = rememberInfiniteTransition(label = "sick_lab")
    val bubbleProgress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "bubbles"
    )
    val glowPulse by transition.animateFloat(
        0.06f, 0.16f,
        infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )
    val bubbles = remember {
        List(22) {
            Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.5f + 0.4f, Random.nextFloat() * 0.5f + 0.3f, Random.nextFloat() * 5f + 2f, Random.nextFloat() * 6.28f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.radialGradient(
            colors = listOf(Color(0xFF022413), Color(0xFF000D06), Color(0xFF000000)),
            center = Offset(size.width * 0.5f, size.height * 0.7f),
            radius = size.maxDimension * 0.7f
        ))

        // Green bio glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF00E676).copy(alpha = glowPulse), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.65f),
                radius = size.minDimension * 0.55f
            )
        )

        // Rising chemical bubbles
        bubbles.forEach { b ->
            val prog = ((b.y - bubbleProgress * b.speed) % 1f + 1f) % 1f
            val by = prog * size.height
            val bx = b.x * size.width + sin((prog * 4f * PI + b.extra).toDouble()).toFloat() * 12f
            drawCircle(
                color = Color(0xFF69F0AE).copy(alpha = b.alpha * 0.7f),
                radius = b.size,
                center = Offset(bx, by),
                style = Stroke(width = 1.2f)
            )
        }
    }
}

/**
 * SPORTS_ARENA — สนามกีฬา ลำแสงไฟสปอตไลต์ส่องตัดผ่านความมืด
 */
@Composable
private fun SportsArenaBackground() {
    val transition = rememberInfiniteTransition(label = "sports_arena")
    val sweep by transition.animateFloat(
        -15f, 15f,
        infiniteRepeatable(tween(4000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "sweep"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF04192B), Color(0xFF010A14), Color(0xFF000000))))

        // Stadium Left Floodlight Cone
        val leftLight = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width * 0.75f + sweep * 6f, size.height)
            lineTo(size.width * 0.45f + sweep * 6f, size.height)
            close()
        }
        drawPath(leftLight, brush = Brush.linearGradient(listOf(Color(0xFF00E5FF).copy(alpha = 0.08f), Color.Transparent)))

        // Stadium Right Floodlight Cone
        val rightLight = Path().apply {
            moveTo(size.width, 0f)
            lineTo(size.width * 0.25f - sweep * 6f, size.height)
            lineTo(size.width * 0.55f - sweep * 6f, size.height)
            close()
        }
        drawPath(rightLight, brush = Brush.linearGradient(listOf(Color(0xFF2979FF).copy(alpha = 0.08f), Color.Transparent)))
    }
}

/**
 * LOW_POWER_CRT — จอเรโทร CRT แบตเตอรี่วิกฤต ไฟเตือนกะพริบสีแดงหม่น
 */
@Composable
private fun LowPowerCrtBackground() {
    val transition = rememberInfiniteTransition(label = "low_power_crt")
    val redPulse by transition.animateFloat(
        0.04f, 0.18f,
        infiniteRepeatable(tween(1100, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "red_pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.radialGradient(
            colors = listOf(Color(0xFF260404).copy(alpha = redPulse * 2.2f), Color(0xFF080101), Color(0xFF000000)),
            center = center,
            radius = size.maxDimension * 0.65f
        ))

        // Faint horizontal CRT scan grid lines
        val lineSpacing = 6f
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.35f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.2f
            )
            y += lineSpacing
        }
    }
}

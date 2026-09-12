package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val PI_F = 3.1415927f
private const val TWO_PI_F = 6.2831855f

/**
 * PetPropsOverlay — เลเยอร์ซ้อนทับสำหรับแสดงอุปกรณ์เสริม/สติกเกอร์เคลื่อนไหวรอบหน้าหุ่นยนต์
 * รองรับคลังพร็อพกว่า 50+ ชนิด พร้อมแอนิเมชันเฉพาะตัวและขนาดที่ใหญ่โดดเด่น
 */
@Composable
fun PetPropsOverlay(
    props: List<PropType>,
    customProps: List<DynamicVectorProp> = emptyList(),
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        for (prop in props) {
            key(prop) {
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    RenderProp(prop)
                }
            }
        }

        // ─── Dynamic AI-generated SVG Vector Props ───
        for (customProp in customProps) {
            key(customProp.id.ifEmpty { customProp.name }) {
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    DynamicPropRenderer(prop = customProp)
                }
            }
        }
    }
}

@Composable
private fun RenderProp(prop: PropType) {
    when (prop) {
        // ─── อารมณ์/ความรู้สึก ───
        PropType.HEARTS -> HeartsProp()
        PropType.BROKEN_HEART -> BrokenHeartProp()
        PropType.SPARKLES -> SparklesProp()
        PropType.SWEAT_DROP -> SweatDropProp()
        PropType.QUESTION_MARK -> QuestionMarkProp()
        PropType.EXCLAMATION -> ExclamationProp()
        PropType.ZZZZZ -> ZzzzzProp()
        PropType.FIRE -> FireProp()
        PropType.ANGER_VEIN -> AngerVeinProp()
        PropType.TEARS -> TearsProp()
        PropType.DIZZY_STARS -> DizzyStarsProp()
        PropType.SKULL -> SkullProp()
        PropType.LIGHTBULB -> LightbulbProp()
        PropType.PARTY_POPPER -> PartyPopperProp()
        PropType.BALLOONS -> BalloonsProp()
        PropType.CROWN -> CrownProp()
        PropType.SUNGLASSES -> SunglassesProp()

        // ─── อาหาร/เครื่องดื่ม/กิจกรรม ───
        PropType.COFFEE -> CoffeeProp()
        PropType.TEA_CUP -> TeaCupProp()
        PropType.BEER -> BeerProp()
        PropType.PIZZA -> PizzaProp()
        PropType.BURGER -> BurgerProp()
        PropType.CAKE -> CakeProp()
        PropType.ICE_CREAM -> IceCreamProp()
        PropType.POPCORN -> PopcornProp()
        PropType.BOBA_TEA -> BobaTeaProp()
        PropType.GIFT -> GiftProp()
        PropType.GAMING_CONTROLLER -> GamingControllerProp()
        PropType.BOOK -> BookProp()
        PropType.MUSIC_NOTES -> MusicNotesProp()

        // ─── ธรรมชาติ/สภาพอากาศ/อวกาศ ───
        PropType.SUN -> SunProp()
        PropType.RAINBOW -> RainbowProp()
        PropType.UMBRELLA -> UmbrellaProp()
        PropType.CLOUD -> CloudProp()
        PropType.SNOW -> SnowProp()
        PropType.LIGHTNING -> LightningProp()
        PropType.LEAF -> LeafProp()
        PropType.CHERRY_BLOSSOM -> CherryBlossomProp()
        PropType.MOON_STARS -> MoonStarsProp()
        PropType.GHOST -> GhostProp()

        // ─── ไอที/เครื่องมือ/ความปลอดภัย ───
        PropType.ROCKET -> RocketProp()
        PropType.GEARS -> GearsProp()
        PropType.LAPTOP -> LaptopProp()
        PropType.BATTERY_CHARGING -> BatteryChargingProp()
        PropType.BATTERY_LOW -> BatteryLowProp()
        PropType.CLOCK_ALARM -> ClockAlarmProp()
        PropType.MAGNIFYING_GLASS -> MagnifyingGlassProp()
        PropType.SHIELD -> ShieldProp()
        PropType.WARNING_TRIANGLE -> WarningTriangleProp()
        PropType.SIREN_LIGHT -> SirenLightProp()
        PropType.CHECK_MARK -> CheckMarkProp()
        PropType.TARGET_RETICLE -> TargetReticleProp()
        PropType.CAT_PAW -> CatPawProp()
        PropType.GOLD_COIN -> GoldCoinProp()
        PropType.RAIN_DROPS -> RainDropsProp()
    }
}

// ─── 1. HEARTS PROP ──────────────────────────────────────────────────────────

/** แสดงหัวใจสีชมพู 3 ดวงลอยขึ้นจากด้านล่างพร้อมจางหาย (3 pink hearts floating upward with fade) */
@Composable
private fun HeartsProp() {
    val progress by rememberInfiniteTransition(label = "Hearts").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val pink = Color(0xFFFF4081)
        val timeOffsets = floatArrayOf(0f, 0.33f, 0.67f)
        val xOffsets = floatArrayOf(-0.12f, 0.08f, -0.02f)
        val sizes = floatArrayOf(20.dp.toPx(), 26.dp.toPx(), 18.dp.toPx())
        for (i in 0 until 3) {
            val p = (progress + timeOffsets[i]) % 1f
            val alpha = when { p < 0.15f -> p / 0.15f; p > 0.7f -> (1f - p) / 0.3f; else -> 1f }
            val hx = (size.width * 0.5f) + (size.width * xOffsets[i]) + sin(p * TWO_PI_F + i * 2f) * 14.dp.toPx()
            val hy = (size.height * 0.68f) - p * (size.height * 0.48f)
            val hs = sizes[i] * (0.8f + 0.2f * sin(p * PI_F))
            drawHeart(pink.copy(alpha = alpha), hx, hy, hs)
        }
    }
}

/** วาดรูปหัวใจด้วย Cubic Bezier Curve (Draws heart path with glow) */
private fun DrawScope.drawHeart(color: Color, cx: Float, cy: Float, size: Float) {
    val w = size
    val h = size * 0.95f
    val path = Path().apply {
        moveTo(cx, cy + h * 0.38f)
        cubicTo(cx - w * 0.55f, cy - h * 0.2f, cx - w * 0.55f, cy - h * 0.65f, cx, cy - h * 0.25f)
        cubicTo(cx + w * 0.55f, cy - h * 0.65f, cx + w * 0.55f, cy - h * 0.2f, cx, cy + h * 0.38f)
        close()
    }
    drawPath(path, color.copy(alpha = color.alpha * 0.35f), style = Stroke(6f, cap = StrokeCap.Round))
    drawPath(path, color)
}

// ─── 2. SPARKLES PROP ────────────────────────────────────────────────────────

/** แสดงประกายดาว 4 แฉกสีทอง 4 ดวงโคจรรอบหัว (4 gold 4-pointed stars orbiting around center) */
@Composable
private fun SparklesProp() {
    val orbit by rememberInfiniteTransition(label = "Sparkles").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val gold = Color(0xFFFFD700)
        val cx = size.width * 0.5f
        val cy = size.height * 0.48f
        val rx = size.width * 0.38f
        val ry = size.height * 0.28f
        for (i in 0 until 4) {
            val angle = (orbit * TWO_PI_F) + (i * PI_F / 2f)
            val sx = cx + rx * cos(angle)
            val sy = cy + ry * sin(angle)
            val pulse = 0.75f + 0.25f * sin(orbit * 4f * TWO_PI_F + i.toFloat())
            val r = 14.dp.toPx() * pulse
            val star = Path().apply {
                moveTo(sx, sy - r)
                quadraticTo(sx, sy, sx + r, sy)
                quadraticTo(sx, sy, sx, sy + r)
                quadraticTo(sx, sy, sx - r, sy)
                quadraticTo(sx, sy, sx, sy - r)
                close()
            }
            drawPath(star, gold.copy(alpha = 0.45f), style = Stroke(4f, cap = StrokeCap.Round))
            drawPath(star, gold)
            drawCircle(Color.White, radius = r * 0.22f, center = Offset(sx, sy))
        }
    }
}

// ─── 3. MUSIC NOTES PROP ─────────────────────────────────────────────────────

/** แสดงโน้ตดนตรีสีฟ้า 3 ตัวแกว่งซ้ายขวาและลอยขึ้น (3 cyan eighth notes swaying and rising) */
@Composable
private fun MusicNotesProp() {
    val progress by rememberInfiniteTransition(label = "Music").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cyan = Color(0xFF00F0FF)
        val timeOffsets = floatArrayOf(0f, 0.35f, 0.7f)
        val xStarts = floatArrayOf(size.width * 0.68f, size.width * 0.75f, size.width * 0.62f)
        for (i in 0 until 3) {
            val p = (progress + timeOffsets[i]) % 1f
            val alpha = when { p < 0.15f -> p / 0.15f; p > 0.75f -> (1f - p) / 0.25f; else -> 1f }
            val nx = xStarts[i] + sin(p * 3f * PI_F + i * 1.5f) * 12.dp.toPx()
            val ny = (size.height * 0.58f) - p * (size.height * 0.48f)
            val col = cyan.copy(alpha = alpha)
            val s = 16.dp.toPx()
            val r = s * 0.28f
            val stemH = s * 1.2f
            val stemX = nx + r * 0.7f

            rotate(degrees = -20f, pivot = Offset(nx, ny)) {
                drawOval(col, topLeft = Offset(nx - r, ny - r * 0.7f), size = Size(r * 2f, r * 1.4f))
            }
            drawLine(col, Offset(stemX, ny), Offset(stemX, ny - stemH), 3f, StrokeCap.Round)
            val flag = Path().apply {
                moveTo(stemX, ny - stemH)
                quadraticTo(stemX + s * 0.65f, ny - stemH * 0.65f, stemX + s * 0.4f, ny - stemH * 0.3f)
            }
            drawPath(flag, col, style = Stroke(3.2f, cap = StrokeCap.Round))
            drawCircle(col.copy(alpha = alpha * 0.3f), radius = r * 1.5f, center = Offset(nx, ny))
        }
    }
}

// ─── 4. QUESTION MARK PROP ───────────────────────────────────────────────────

/** แสดงเครื่องหมายคำถามเด้งดึ๋งเหนือหัว (Single bouncing cyan question mark above head) */
@Composable
private fun QuestionMarkProp() {
    val bounce by rememberInfiniteTransition(label = "Question").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cyan = Color(0xFF00F0FF)
        val cx = size.width * 0.5f
        val cy = (size.height * 0.12f) - bounce * 14.dp.toPx()
        val qh = 34.dp.toPx()
        val qw = qh * 0.55f
        val qPath = Path().apply {
            moveTo(cx - qw * 0.45f, cy - qh * 0.35f)
            cubicTo(cx - qw * 0.5f, cy - qh * 0.65f, cx + qw * 0.5f, cy - qh * 0.65f, cx + qw * 0.35f, cy - qh * 0.35f)
            cubicTo(cx + qw * 0.25f, cy - qh * 0.15f, cx, cy - qh * 0.08f, cx, cy + qh * 0.06f)
        }
        drawPath(qPath, cyan.copy(alpha = 0.4f), style = Stroke(8f, cap = StrokeCap.Round))
        drawPath(qPath, cyan, style = Stroke(4.5f, cap = StrokeCap.Round))
        val dotY = cy + qh * 0.26f
        drawCircle(cyan.copy(alpha = 0.4f), 6f, Offset(cx, dotY))
        drawCircle(cyan, 3.5f, Offset(cx, dotY))
    }
}

// ─── 5. EXCLAMATION PROP ─────────────────────────────────────────────────────

/** แสดงเครื่องหมายตกใจสีแดงสั่นไหวเหนือหัว (Single shaking red exclamation mark above head) */
@Composable
private fun ExclamationProp() {
    val t = rememberInfiniteTransition(label = "Exclamation")
    val shake by t.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(70, easing = LinearEasing), RepeatMode.Reverse),
        label = "s"
    )
    val pulse by t.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val red = Color(0xFFFF1E46)
        val cx = (size.width * 0.5f) + shake * 4.dp.toPx()
        val cy = size.height * 0.12f
        val barH = 30.dp.toPx() * pulse
        val barTop = cy - barH * 0.45f
        val barBottom = cy + barH * 0.1f

        drawLine(red.copy(alpha = 0.4f), Offset(cx, barTop), Offset(cx, barBottom), 10f, StrokeCap.Round)
        drawLine(red, Offset(cx, barTop), Offset(cx, barBottom), 5.5f, StrokeCap.Round)
        val dotY = cy + barH * 0.32f
        drawCircle(red.copy(alpha = 0.4f), 6.5f, Offset(cx, dotY))
        drawCircle(red, 4f, Offset(cx, dotY))
    }
}

// ─── 6. SWEAT DROP PROP ──────────────────────────────────────────────────────

/** แสดงหยดเหงื่อสีฟ้าไหลลงทางขวา (Single blue teardrop sliding down from top-right) */
@Composable
private fun SweatDropProp() {
    val progress by rememberInfiniteTransition(label = "Sweat").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing)),
        label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val blue = Color(0xFF42A5F5)
        val cx = size.width * 0.74f
        val cy = (size.height * 0.22f) + progress * 32.dp.toPx()
        val alpha = when { progress < 0.2f -> progress / 0.2f; progress > 0.75f -> (1f - progress) / 0.25f; else -> 1f }
        val dw = 14.dp.toPx()
        val dh = 22.dp.toPx()

        rotate(degrees = 15f, pivot = Offset(cx, cy)) {
            val drop = Path().apply {
                moveTo(cx, cy - dh * 0.5f)
                cubicTo(cx + dw * 0.55f, cy, cx + dw * 0.55f, cy + dh * 0.5f, cx, cy + dh * 0.5f)
                cubicTo(cx - dw * 0.55f, cy + dh * 0.5f, cx - dw * 0.55f, cy, cx, cy - dh * 0.5f)
                close()
            }
            drawPath(drop, blue.copy(alpha = alpha * 0.4f), style = Stroke(4f))
            drawPath(drop, blue.copy(alpha = alpha))
            drawCircle(Color.White.copy(alpha = alpha * 0.75f), dw * 0.15f, Offset(cx - dw * 0.18f, cy + dh * 0.18f))
        }
    }
}

// ─── 7. UMBRELLA PROP ────────────────────────────────────────────────────────

/** แสดงร่มสีฟ้าลอยกางเหนือหัว (Hovering cyan umbrella above head) */
@Composable
private fun UmbrellaProp() {
    val t = rememberInfiniteTransition(label = "Umbrella")
    val hover by t.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h"
    )
    val tilt by t.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "t"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cyan = Color(0xFF00F0FF)
        val cx = size.width * 0.5f
        val cy = (size.height * 0.1f) + hover * 6.dp.toPx()
        val dw = 54.dp.toPx()
        val dh = 22.dp.toPx()

        rotate(degrees = tilt, pivot = Offset(cx, cy)) {
            val sw = dw / 3f
            val canopy = Path().apply {
                moveTo(cx - dw / 2f, cy)
                cubicTo(cx - dw * 0.45f, cy - dh * 1.15f, cx + dw * 0.45f, cy - dh * 1.15f, cx + dw / 2f, cy)
                cubicTo(cx + dw / 2f - sw * 0.3f, cy - 3.dp.toPx(), cx + dw / 6f + sw * 0.3f, cy - 3.dp.toPx(), cx + dw / 6f, cy)
                cubicTo(cx + dw / 6f - sw * 0.3f, cy - 3.dp.toPx(), cx - dw / 6f + sw * 0.3f, cy - 3.dp.toPx(), cx - dw / 6f, cy)
                cubicTo(cx - dw / 6f - sw * 0.3f, cy - 3.dp.toPx(), cx - dw / 2f + sw * 0.3f, cy - 3.dp.toPx(), cx - dw / 2f, cy)
                close()
            }
            drawPath(canopy, cyan.copy(alpha = 0.25f))
            drawPath(canopy, cyan, style = Stroke(3.5f, cap = StrokeCap.Round))
            drawLine(cyan, Offset(cx, cy - dh * 0.88f), Offset(cx, cy - dh * 1.05f), 3f, StrokeCap.Round)
            val stemBottom = cy + 20.dp.toPx()
            drawLine(cyan, Offset(cx, cy - 1.dp.toPx()), Offset(cx, stemBottom), 3f, StrokeCap.Round)
            val hr = 5.dp.toPx()
            drawArc(cyan, 0f, 180f, false, Offset(cx - hr * 2f, stemBottom - hr), Size(hr * 2f, hr * 2f), style = Stroke(3f, cap = StrokeCap.Round))
        }
    }
}

// ─── 8. ZZZZZ PROP ───────────────────────────────────────────────────────────

/** แสดงสัญลักษณ์ Z 3 ตัวลอยเฉียงขึ้นขวา (3 cyan Z letters floating up-right) */
@Composable
private fun ZzzzzProp() {
    val progress by rememberInfiniteTransition(label = "Zzz").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cyan = Color(0xFF00F0FF)
        val timeOffsets = floatArrayOf(0f, 0.33f, 0.66f)
        val baseSizes = floatArrayOf(12.dp.toPx(), 18.dp.toPx(), 26.dp.toPx())
        val sx = size.width * 0.62f
        val sy = size.height * 0.32f
        val ex = size.width * 0.84f
        val ey = size.height * 0.1f

        for (i in 0 until 3) {
            val p = (progress + timeOffsets[i]) % 1f
            val alpha = when { p < 0.15f -> p / 0.15f; p > 0.75f -> (1f - p) / 0.25f; else -> 1f }
            val zx = sx + (ex - sx) * p + sin(p * TWO_PI_F + i) * 6.dp.toPx()
            val zy = sy + (ey - sy) * p
            val zs = baseSizes[i] * (0.85f + 0.15f * p)
            val half = zs / 2f
            val zPath = Path().apply {
                moveTo(zx - half, zy - half); lineTo(zx + half, zy - half)
                lineTo(zx - half, zy + half); lineTo(zx + half, zy + half)
            }
            drawPath(zPath, cyan.copy(alpha = alpha * 0.4f), style = Stroke(zs * 0.28f, cap = StrokeCap.Round))
            drawPath(zPath, cyan.copy(alpha = alpha), style = Stroke(zs * 0.16f, cap = StrokeCap.Round))
        }
    }
}

// ─── 9. FIRE PROP ────────────────────────────────────────────────────────────

/** แสดงเปลวไฟ 3 ลูกกะพริบสลับกันใต้หัว (3 flickering flame shapes at bottom) */
@Composable
private fun FireProp() {
    val t = rememberInfiniteTransition(label = "Fire")
    val f1 by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(140, easing = FastOutSlowInEasing), RepeatMode.Reverse), "f1")
    val f2 by t.animateFloat(1.15f, 0.82f, infiniteRepeatable(tween(190, easing = FastOutSlowInEasing), RepeatMode.Reverse), "f2")
    val f3 by t.animateFloat(0.88f, 1.2f, infiniteRepeatable(tween(160, easing = FastOutSlowInEasing), RepeatMode.Reverse), "f3")
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val baseY = size.height * 0.84f
        val cx = size.width * 0.5f
        drawFlame(cx, baseY, 22.dp.toPx(), 40.dp.toPx() * f1)
        drawFlame(cx - 18.dp.toPx(), baseY + 2.dp.toPx(), 16.dp.toPx(), 30.dp.toPx() * f2)
        drawFlame(cx + 18.dp.toPx(), baseY + 2.dp.toPx(), 16.dp.toPx(), 32.dp.toPx() * f3)
    }
}

/** วาดรูปเปลวไฟพร้อม gradient ส้ม-แดงและแกนในสีเหลือง (Draws flame shape with gradient and inner core) */
private fun DrawScope.drawFlame(cx: Float, baseY: Float, width: Float, height: Float) {
    val tipY = baseY - height
    val outer = Path().apply {
        moveTo(cx, tipY)
        cubicTo(cx + width * 0.55f, baseY - height * 0.5f, cx + width * 0.55f, baseY, cx, baseY)
        cubicTo(cx - width * 0.55f, baseY, cx - width * 0.55f, baseY - height * 0.5f, cx, tipY)
        close()
    }
    drawPath(outer, Brush.verticalGradient(listOf(Color(0xFFFFAB00), Color(0xFFFF5722)), startY = tipY, endY = baseY))

    val ih = height * 0.55f
    val iw = width * 0.55f
    val itip = baseY - ih
    val inner = Path().apply {
        moveTo(cx, itip)
        cubicTo(cx + iw * 0.55f, baseY - ih * 0.5f, cx + iw * 0.55f, baseY, cx, baseY)
        cubicTo(cx - iw * 0.55f, baseY, cx - iw * 0.55f, baseY - ih * 0.5f, cx, itip)
        close()
    }
    drawPath(inner, Brush.verticalGradient(listOf(Color(0xFFFFF176), Color(0xFFFFAB00)), startY = itip, endY = baseY))
}

// ─── 10. SNOW PROP ───────────────────────────────────────────────────────────

/** แสดงหิมะ 10 เม็ดโปรยปรายลงมาอย่างช้าๆ (10 white snowflake dots falling slowly) */
@Composable
private fun SnowProp() {
    val progress by rememberInfiniteTransition(label = "Snow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val snow = Color(0xCCFFFFFF)
        val xFrac = floatArrayOf(0.12f, 0.22f, 0.35f, 0.48f, 0.58f, 0.68f, 0.78f, 0.88f, 0.28f, 0.72f)
        val yOff = floatArrayOf(0.05f, 0.35f, 0.7f, 0.2f, 0.55f, 0.85f, 0.15f, 0.45f, 0.9f, 0.6f)
        val sizes = floatArrayOf(2.5f, 4f, 2f, 3.5f, 2.2f, 4.2f, 2f, 3f, 2.5f, 3.8f)

        for (i in 0 until 10) {
            val p = (progress + yOff[i]) % 1f
            val sy = p * size.height
            val sx = (xFrac[i] * size.width) + sin(p * TWO_PI_F * 2f + i.toFloat()) * 8.dp.toPx()
            val r = sizes[i].dp.toPx()
            drawCircle(Color(0x33FFFFFF), r * 1.8f, Offset(sx, sy))
            drawCircle(snow, r, Offset(sx, sy))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// พร็อพใหม่เพิ่มเติม (NEW PROPS)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun BrokenHeartProp() {
    val t = rememberInfiniteTransition(label = "BrokenHeart")
    val split by t.animateFloat(
        initialValue = 0f, targetValue = 12.dp.value,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse), label = "s"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.35f
        val hs = 48.dp.toPx()
        val red = Color(0xFFFF1744)

        val leftPath = Path().apply {
            moveTo(cx - split - hs * 0.05f, cy + hs * 0.35f)
            cubicTo(cx - split - hs * 0.55f, cy - hs * 0.2f, cx - split - hs * 0.55f, cy - hs * 0.65f, cx - split, cy - hs * 0.25f)
            lineTo(cx - split - hs * 0.08f, cy - hs * 0.1f)
            lineTo(cx - split + hs * 0.05f, cy + hs * 0.05f)
            lineTo(cx - split - hs * 0.05f, cy + hs * 0.2f)
            close()
        }
        drawPath(leftPath, red)

        val rightPath = Path().apply {
            moveTo(cx + split + hs * 0.05f, cy + hs * 0.35f)
            cubicTo(cx + split + hs * 0.55f, cy - hs * 0.2f, cx + split + hs * 0.55f, cy - hs * 0.65f, cx + split, cy - hs * 0.25f)
            lineTo(cx + split - hs * 0.08f, cy - hs * 0.1f)
            lineTo(cx + split + hs * 0.05f, cy + hs * 0.05f)
            lineTo(cx + split - hs * 0.05f, cy + hs * 0.2f)
            close()
        }
        drawPath(rightPath, red)
    }
}

@Composable
private fun AngerVeinProp() {
    val pulse by rememberInfiniteTransition(label = "Vein").animateFloat(
        initialValue = 0.85f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(250, easing = EaseInOutSine), RepeatMode.Reverse), label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.82f
        val cy = size.height * 0.22f
        val s = 28.dp.toPx() * pulse
        val red = Color(0xFFFF1744)

        val path = Path().apply {
            moveTo(cx - s, cy - s * 0.3f); quadraticTo(cx - s * 0.3f, cy - s * 0.3f, cx - s * 0.3f, cy - s)
            moveTo(cx + s * 0.3f, cy - s); quadraticTo(cx + s * 0.3f, cy - s * 0.3f, cx + s, cy - s * 0.3f)
            moveTo(cx + s, cy + s * 0.3f); quadraticTo(cx + s * 0.3f, cy + s * 0.3f, cx + s * 0.3f, cy + s)
            moveTo(cx - s * 0.3f, cy + s); quadraticTo(cx - s * 0.3f, cy + s * 0.3f, cx - s, cy + s * 0.3f)
        }
        drawPath(path, red, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun TearsProp() {
    val progress by rememberInfiniteTransition(label = "Tears").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cyan = Color(0xFF00E5FF)
        val leftEyeX = size.width * 0.35f
        val rightEyeX = size.width * 0.65f
        val startY = size.height * 0.50f
        val streamLen = size.height * 0.35f

        for (sideX in floatArrayOf(leftEyeX, rightEyeX)) {
            for (i in 0 until 3) {
                val p = (progress + i * 0.33f) % 1f
                val y = startY + p * streamLen
                val r = (8.dp.toPx() + i * 2.dp.toPx()) * (1f - p * 0.4f)
                val alpha = when { p < 0.15f -> p / 0.15f; p > 0.8f -> (1f - p) / 0.2f; else -> 1f }
                drawCircle(cyan.copy(alpha = alpha), radius = r, center = Offset(sideX, y))
            }
        }
    }
}

@Composable
private fun DizzyStarsProp() {
    val rot by rememberInfiniteTransition(label = "DizzyStars").animateFloat(
        initialValue = 0f, targetValue = TWO_PI_F,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "r"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val gold = Color(0xFFFFD700)
        val cx = size.width * 0.5f
        val cy = size.height * 0.22f
        val rx = size.width * 0.35f
        val ry = 24.dp.toPx()

        for (i in 0 until 3) {
            val angle = rot + i * (TWO_PI_F / 3f)
            val sx = cx + rx * cos(angle)
            val sy = cy + ry * sin(angle)
            val zAlpha = (sin(angle) + 1f) * 0.4f + 0.2f
            val r = 20.dp.toPx() * (0.8f + 0.2f * sin(angle))

            val star = Path().apply {
                for (step in 0 until 5) {
                    val a1 = step * TWO_PI_F / 5f - PI_F / 2f
                    val a2 = a1 + PI_F / 5f
                    val x1 = sx + r * cos(a1)
                    val y1 = sy + r * sin(a1)
                    val x2 = sx + r * 0.45f * cos(a2)
                    val y2 = sy + r * 0.45f * sin(a2)
                    if (step == 0) moveTo(x1, y1) else lineTo(x1, y1)
                    lineTo(x2, y2)
                }
                close()
            }
            drawPath(star, gold.copy(alpha = zAlpha))
        }
    }
}

@Composable
private fun SkullProp() {
    val floatY by rememberInfiniteTransition(label = "Skull").animateFloat(
        initialValue = -8.dp.value, targetValue = 8.dp.value,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse), label = "f"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.25f + floatY
        val gray = Color(0xFFCFD8DC)
        val r = 28.dp.toPx()

        drawCircle(gray, radius = r, center = Offset(cx, cy))
        drawRoundRect(
            gray,
            topLeft = Offset(cx - r * 0.6f, cy + r * 0.4f),
            size = Size(r * 1.2f, r * 0.7f),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        drawCircle(Color.Black, radius = r * 0.28f, center = Offset(cx - r * 0.38f, cy + r * 0.1f))
        drawCircle(Color.Black, radius = r * 0.28f, center = Offset(cx + r * 0.38f, cy + r * 0.1f))
    }
}

@Composable
private fun LightbulbProp() {
    val glow by rememberInfiniteTransition(label = "Bulb").animateFloat(
        initialValue = 0.8f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(500, easing = EaseInOutSine), RepeatMode.Reverse), label = "g"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.18f
        val yellow = Color(0xFFFFD600)
        val r = 26.dp.toPx()

        drawCircle(yellow.copy(alpha = 0.28f * glow), radius = r * 1.8f * glow, center = Offset(cx, cy))
        drawCircle(yellow, radius = r, center = Offset(cx, cy))
        drawRect(Color(0xFF90A4AE), topLeft = Offset(cx - r * 0.4f, cy + r * 0.7f), size = Size(r * 0.8f, r * 0.4f))
        for (step in 0 until 6) {
            val angle = step * PI_F / 5f - PI_F
            val x1 = cx + r * 1.3f * cos(angle)
            val y1 = cy + r * 1.3f * sin(angle)
            val x2 = cx + r * 1.7f * cos(angle)
            val y2 = cy + r * 1.7f * sin(angle)
            drawLine(yellow, Offset(x1, y1), Offset(x2, y2), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun PartyPopperProp() {
    val pop by rememberInfiniteTransition(label = "Popper").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseOutQuad)), label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val startX = size.width * 0.2f
        val startY = size.height * 0.75f
        val colors = listOf(Color(0xFFFF1744), Color(0xFFFFD700), Color(0xFF00E5FF), Color(0xFF76FF03), Color(0xFFE040FB))

        val cone = Path().apply {
            moveTo(startX, startY)
            lineTo(startX - 20.dp.toPx(), startY + 30.dp.toPx())
            lineTo(startX + 20.dp.toPx(), startY + 22.dp.toPx())
            close()
        }
        drawPath(cone, Color(0xFFFF9100))

        for (i in 0 until 16) {
            val angle = -PI_F * 0.15f - (i.toFloat() / 16f) * PI_F * 0.45f
            val dist = pop * (size.width * 0.65f) * (0.6f + (i % 5) * 0.1f)
            val px = startX + dist * cos(angle)
            val py = startY + dist * sin(angle) + pop * pop * 120.dp.toPx()
            val col = colors[i % colors.size]
            val alpha = (1f - pop).coerceIn(0f, 1f)
            drawCircle(col.copy(alpha = alpha), radius = 6.dp.toPx(), center = Offset(px, py))
        }
    }
}

@Composable
private fun BalloonsProp() {
    val sway by rememberInfiniteTransition(label = "Balloons").animateFloat(
        initialValue = -12.dp.value, targetValue = 12.dp.value,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse), label = "s"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val colors = listOf(Color(0xFFFF1744), Color(0xFF00E5FF), Color(0xFFFFD600))
        val xOffsets = floatArrayOf(-38.dp.toPx(), 0f, 38.dp.toPx())
        val yOffsets = floatArrayOf(-10.dp.toPx(), -35.dp.toPx(), -5.dp.toPx())
        val cx = size.width * 0.8f
        val cy = size.height * 0.35f

        for (i in 0 until 3) {
            val bx = cx + xOffsets[i] + sway * (i + 1) * 0.4f
            val by = cy + yOffsets[i]
            val r = 26.dp.toPx()

            drawLine(Color.White.copy(alpha = 0.5f), Offset(bx, by + r), Offset(cx, cy + 90.dp.toPx()), strokeWidth = 2.5f)
            drawOval(colors[i], topLeft = Offset(bx - r, by - r * 1.25f), size = Size(r * 2f, r * 2.5f))
            drawCircle(Color.White.copy(alpha = 0.6f), radius = r * 0.25f, center = Offset(bx - r * 0.35f, by - r * 0.6f))
        }
    }
}

@Composable
private fun CrownProp() {
    val bob by rememberInfiniteTransition(label = "Crown").animateFloat(
        initialValue = -6.dp.value, targetValue = 6.dp.value,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse), label = "b"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.16f + bob
        val gold = Color(0xFFFFD700)
        val w = 62.dp.toPx()
        val h = 36.dp.toPx()

        val crownPath = Path().apply {
            moveTo(cx - w * 0.5f, cy + h * 0.5f)
            lineTo(cx - w * 0.5f, cy - h * 0.4f)
            lineTo(cx - w * 0.25f, cy + h * 0.1f)
            lineTo(cx, cy - h * 0.5f)
            lineTo(cx + w * 0.25f, cy + h * 0.1f)
            lineTo(cx + w * 0.5f, cy - h * 0.4f)
            lineTo(cx + w * 0.5f, cy + h * 0.5f)
            close()
        }
        drawPath(crownPath, gold)
        drawCircle(Color(0xFFFF1744), radius = 4.5.dp.toPx(), center = Offset(cx - w * 0.5f, cy - h * 0.4f))
        drawCircle(Color(0xFF00E5FF), radius = 5.5.dp.toPx(), center = Offset(cx, cy - h * 0.5f))
        drawCircle(Color(0xFFFF1744), radius = 4.5.dp.toPx(), center = Offset(cx + w * 0.5f, cy - h * 0.4f))
    }
}

@Composable
private fun SunglassesProp() {
    val drop by rememberInfiniteTransition(label = "SunG").animateFloat(
        initialValue = -20.dp.value, targetValue = 0.dp.value,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseOutBounce), RepeatMode.Restart), label = "d"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.48f + drop
        val lensW = 68.dp.toPx()
        val lensH = 40.dp.toPx()
        val bridgeW = 18.dp.toPx()

        drawLine(Color.Black, Offset(cx - bridgeW * 0.5f, cy), Offset(cx + bridgeW * 0.5f, cy), strokeWidth = 6.dp.toPx())
        drawRoundRect(Color(0xFF1A1A1A), topLeft = Offset(cx - bridgeW * 0.5f - lensW, cy - lensH * 0.5f), size = Size(lensW, lensH), cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()))
        drawRoundRect(Color(0xFF1A1A1A), topLeft = Offset(cx + bridgeW * 0.5f, cy - lensH * 0.5f), size = Size(lensW, lensH), cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()))
        drawLine(Color.White.copy(alpha = 0.4f), Offset(cx + bridgeW * 0.5f + 8.dp.toPx(), cy - lensH * 0.3f), Offset(cx + bridgeW * 0.5f + 28.dp.toPx(), cy + lensH * 0.3f), strokeWidth = 3.dp.toPx())
    }
}

@Composable
private fun CoffeeProp() {
    val steam by rememberInfiniteTransition(label = "Steam").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "s"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.82f
        val cy = size.height * 0.72f
        val brown = Color(0xFF795548)
        val w = 46.dp.toPx()
        val h = 38.dp.toPx()

        drawRoundRect(brown, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h), cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()))
        drawArc(brown, 270f, 180f, false, topLeft = Offset(cx + w * 0.4f, cy - h * 0.35f), size = Size(16.dp.toPx(), h * 0.7f), style = Stroke(5.dp.toPx()))
        for (i in -1..1) {
            val sy = cy - h * 0.6f - steam * 28.dp.toPx()
            val sx = cx + i * 11.dp.toPx() + sin(steam * TWO_PI_F + i) * 6.dp.toPx()
            val alpha = (1f - steam).coerceIn(0f, 0.8f)
            drawLine(Color.White.copy(alpha = alpha), Offset(sx, sy), Offset(sx, sy - 14.dp.toPx()), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun TeaCupProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.82f
        val cy = size.height * 0.72f
        val green = Color(0xFF66BB6A)
        val w = 50.dp.toPx()
        val h = 28.dp.toPx()
        drawArc(green, 0f, 180f, true, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h * 2f))
        drawRoundRect(Color(0xFFE0E0E0), topLeft = Offset(cx - w * 0.65f, cy + h * 0.4f), size = Size(w * 1.3f, 7.dp.toPx()), cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()))
    }
}

@Composable
private fun BeerProp() {
    val froth by rememberInfiniteTransition(label = "Beer").animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse), label = "f"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.8f
        val cy = size.height * 0.7f
        val amber = Color(0xFFFFB300)
        val w = 40.dp.toPx()
        val h = 54.dp.toPx()

        drawRoundRect(amber, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h), cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()))
        drawArc(amber, 270f, 180f, false, topLeft = Offset(cx + w * 0.4f, cy - h * 0.25f), size = Size(18.dp.toPx(), h * 0.5f), style = Stroke(5.dp.toPx()))
        drawRoundRect(Color.White, topLeft = Offset(cx - w * 0.55f, cy - h * 0.5f - 8.dp.toPx() * froth), size = Size(w * 1.1f, 18.dp.toPx()), cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx()))
    }
}

@Composable
private fun PizzaProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.18f
        val cy = size.height * 0.72f
        val cheese = Color(0xFFFFD54F)
        val r = 40.dp.toPx()

        val slice = Path().apply {
            moveTo(cx, cy + r * 0.6f)
            lineTo(cx - r * 0.6f, cy - r * 0.6f)
            quadraticTo(cx, cy - r * 0.8f, cx + r * 0.6f, cy - r * 0.6f)
            close()
        }
        drawPath(slice, cheese)
        drawCircle(Color(0xFFD32F2F), radius = 6.dp.toPx(), center = Offset(cx - 9.dp.toPx(), cy - 7.dp.toPx()))
        drawCircle(Color(0xFFD32F2F), radius = 5.dp.toPx(), center = Offset(cx + 7.dp.toPx(), cy - 2.dp.toPx()))
    }
}

@Composable
private fun BurgerProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.18f
        val cy = size.height * 0.72f
        val bun = Color(0xFFFFB74D)
        val patty = Color(0xFF5D4037)
        val lettuce = Color(0xFF4CAF50)
        val w = 54.dp.toPx()

        drawArc(bun, 180f, 180f, true, topLeft = Offset(cx - w * 0.5f, cy - 22.dp.toPx()), size = Size(w, 26.dp.toPx()))
        drawRect(lettuce, topLeft = Offset(cx - w * 0.52f, cy - 7.dp.toPx()), size = Size(w * 1.04f, 5.dp.toPx()))
        drawRoundRect(patty, topLeft = Offset(cx - w * 0.5f, cy), size = Size(w, 9.dp.toPx()), cornerRadius = CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx()))
        drawRoundRect(bun, topLeft = Offset(cx - w * 0.48f, cy + 11.dp.toPx()), size = Size(w * 0.96f, 9.dp.toPx()), cornerRadius = CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx()))
    }
}

@Composable
private fun CakeProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.72f
        val pink = Color(0xFFF48FB1)
        val w = 56.dp.toPx()
        val h = 32.dp.toPx()

        drawRoundRect(pink, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h), cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()))
        drawRect(Color.White, topLeft = Offset(cx - 2.5.dp.toPx(), cy - h * 0.5f - 14.dp.toPx()), size = Size(5.dp.toPx(), 14.dp.toPx()))
        drawCircle(Color(0xFFFFD600), radius = 5.dp.toPx(), center = Offset(cx, cy - h * 0.5f - 16.dp.toPx()))
    }
}

@Composable
private fun IceCreamProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.82f
        val cy = size.height * 0.7f
        val coneCol = Color(0xFFFFB74D)
        val scoopCol = Color(0xFFFF4081)
        val r = 20.dp.toPx()

        drawCircle(scoopCol, radius = r, center = Offset(cx, cy - r * 0.4f))
        val cone = Path().apply {
            moveTo(cx - r * 0.9f, cy)
            lineTo(cx + r * 0.9f, cy)
            lineTo(cx, cy + r * 2.2f)
            close()
        }
        drawPath(cone, coneCol)
    }
}

@Composable
private fun PopcornProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.82f
        val cy = size.height * 0.7f
        val w = 40.dp.toPx()
        val h = 46.dp.toPx()

        val bucket = Path().apply {
            moveTo(cx - w * 0.6f, cy - h * 0.5f)
            lineTo(cx + w * 0.6f, cy - h * 0.5f)
            lineTo(cx + w * 0.4f, cy + h * 0.5f)
            lineTo(cx - w * 0.4f, cy + h * 0.5f)
            close()
        }
        drawPath(bucket, Color(0xFFD32F2F))
        drawLine(Color.White, Offset(cx - w * 0.15f, cy - h * 0.5f), Offset(cx - w * 0.1f, cy + h * 0.5f), strokeWidth = 7.dp.toPx())
        drawLine(Color.White, Offset(cx + w * 0.15f, cy - h * 0.5f), Offset(cx + w * 0.1f, cy + h * 0.5f), strokeWidth = 7.dp.toPx())
        drawCircle(Color(0xFFFFF9C4), radius = 9.dp.toPx(), center = Offset(cx - 9.dp.toPx(), cy - h * 0.55f))
        drawCircle(Color(0xFFFFF9C4), radius = 10.dp.toPx(), center = Offset(cx + 7.dp.toPx(), cy - h * 0.58f))
    }
}

@Composable
private fun BobaTeaProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.82f
        val cy = size.height * 0.7f
        val teaCol = Color(0xFFD7CCC8)
        val w = 38.dp.toPx()
        val h = 50.dp.toPx()

        drawRoundRect(teaCol, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h), cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()))
        for (x in -1..1) {
            drawCircle(Color(0xFF212121), radius = 4.5.dp.toPx(), center = Offset(cx + x * 9.dp.toPx(), cy + h * 0.35f))
        }
        drawLine(Color(0xFFFF4081), Offset(cx, cy - h * 0.4f), Offset(cx + 12.dp.toPx(), cy - h * 0.8f), strokeWidth = 4.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun GiftProp() {
    val bounce by rememberInfiniteTransition(label = "Gift").animateFloat(
        initialValue = -6.dp.value, targetValue = 6.dp.value,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse), label = "b"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.18f
        val cy = size.height * 0.72f + bounce
        val boxCol = Color(0xFF00E5FF)
        val ribbonCol = Color(0xFFFF1744)
        val s = 46.dp.toPx()

        drawRoundRect(boxCol, topLeft = Offset(cx - s * 0.5f, cy - s * 0.5f), size = Size(s, s), cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()))
        drawRect(ribbonCol, topLeft = Offset(cx - 4.5.dp.toPx(), cy - s * 0.5f), size = Size(9.dp.toPx(), s))
        drawRect(ribbonCol, topLeft = Offset(cx - s * 0.5f, cy - 4.5.dp.toPx()), size = Size(s, 9.dp.toPx()))
    }
}

@Composable
private fun GamingControllerProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.75f
        val bodyCol = Color(0xFF37474F)
        val btnCol = Color(0xFF00E5FF)
        val w = 60.dp.toPx()
        val h = 36.dp.toPx()

        drawRoundRect(bodyCol, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h), cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()))
        drawRect(Color.White, topLeft = Offset(cx - w * 0.35f, cy - 3.5.dp.toPx()), size = Size(12.dp.toPx(), 7.dp.toPx()))
        drawRect(Color.White, topLeft = Offset(cx - w * 0.35f + 2.5.dp.toPx(), cy - 6.dp.toPx()), size = Size(7.dp.toPx(), 12.dp.toPx()))
        drawCircle(btnCol, radius = 4.dp.toPx(), center = Offset(cx + w * 0.28f, cy - 4.dp.toPx()))
        drawCircle(Color(0xFFFF1744), radius = 4.dp.toPx(), center = Offset(cx + w * 0.28f + 9.dp.toPx(), cy + 4.dp.toPx()))
    }
}

@Composable
private fun BookProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.75f
        val pageCol = Color(0xFFECEFF1)
        val coverCol = Color(0xFF1E88E5)
        val w = 56.dp.toPx()
        val h = 34.dp.toPx()

        drawRoundRect(coverCol, topLeft = Offset(cx - w * 0.52f, cy - h * 0.48f), size = Size(w * 1.04f, h * 1.04f), cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        drawRect(pageCol, topLeft = Offset(cx - w * 0.48f, cy - h * 0.42f), size = Size(w * 0.46f, h * 0.84f))
        drawRect(pageCol, topLeft = Offset(cx + w * 0.02f, cy - h * 0.42f), size = Size(w * 0.46f, h * 0.84f))
    }
}

@Composable
private fun SunProp() {
    val rot by rememberInfiniteTransition(label = "Sun").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "r"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.15f
        val cy = size.height * 0.18f
        val yellow = Color(0xFFFFD600)
        val r = 28.dp.toPx()

        drawCircle(yellow, radius = r, center = Offset(cx, cy))
        for (i in 0 until 8) {
            val angle = (rot + i * 45f) * PI_F / 180f
            val x1 = cx + r * 1.25f * cos(angle)
            val y1 = cy + r * 1.25f * sin(angle)
            val x2 = cx + r * 1.65f * cos(angle)
            val y2 = cy + r * 1.65f * sin(angle)
            drawLine(yellow, Offset(x1, y1), Offset(x2, y2), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun RainbowProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.22f
        val colors = listOf(Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00), Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFF651FFF))
        val baseR = 75.dp.toPx()

        for ((idx, col) in colors.withIndex()) {
            val r = baseR + idx * 5.dp.toPx()
            drawArc(col, 180f, 180f, false, topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f), style = Stroke(5.dp.toPx()))
        }
    }
}

@Composable
private fun CloudProp() {
    val drift by rememberInfiniteTransition(label = "Cloud").animateFloat(
        initialValue = -15.dp.value, targetValue = 15.dp.value,
        animationSpec = infiniteRepeatable(tween(3500, easing = EaseInOutSine), RepeatMode.Reverse), label = "d"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.2f + drift
        val cy = size.height * 0.2f
        val white = Color(0xDDFFFFFF)
        val r = 20.dp.toPx()

        drawCircle(white, radius = r, center = Offset(cx, cy))
        drawCircle(white, radius = r * 1.35f, center = Offset(cx + r * 1.1f, cy - r * 0.3f))
        drawCircle(white, radius = r * 0.9f, center = Offset(cx + r * 2.2f, cy))
        drawRoundRect(white, topLeft = Offset(cx - r * 0.5f, cy), size = Size(r * 2.8f, r), cornerRadius = CornerRadius(r * 0.5f, r * 0.5f))
    }
}

@Composable
private fun LightningProp() {
    val flash by rememberInfiniteTransition(label = "Lightning").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "f"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        if (flash > 0.35f) return@Canvas
        val yellow = Color(0xFFFFEA00)
        val cx = size.width * 0.8f
        val startY = size.height * 0.1f

        val bolt = Path().apply {
            moveTo(cx, startY)
            lineTo(cx - 20.dp.toPx(), startY + 30.dp.toPx())
            lineTo(cx - 5.dp.toPx(), startY + 30.dp.toPx())
            lineTo(cx - 24.dp.toPx(), startY + 70.dp.toPx())
            lineTo(cx + 8.dp.toPx(), startY + 24.dp.toPx())
            lineTo(cx - 6.dp.toPx(), startY + 24.dp.toPx())
            close()
        }
        drawPath(bolt, yellow)
    }
}

@Composable
private fun LeafProp() {
    val prog by rememberInfiniteTransition(label = "Leaf").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "l"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val green = Color(0xFF66BB6A)
        val lx = (size.width * 0.3f) + sin(prog * TWO_PI_F) * 32.dp.toPx()
        val ly = prog * size.height
        val s = 26.dp.toPx()

        val leaf = Path().apply {
            moveTo(lx, ly - s)
            quadraticTo(lx + s * 0.8f, ly, lx, ly + s)
            quadraticTo(lx - s * 0.8f, ly, lx, ly - s)
            close()
        }
        drawPath(leaf, green)
    }
}

@Composable
private fun CherryBlossomProp() {
    val prog by rememberInfiniteTransition(label = "Sakura").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)), label = "s"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val pink = Color(0xFFF48FB1)
        for (i in 0 until 7) {
            val p = (prog + i * 0.16f) % 1f
            val sx = (size.width * (0.12f + i * 0.13f)) + sin(p * TWO_PI_F + i) * 22.dp.toPx()
            val sy = p * size.height
            drawCircle(pink.copy(alpha = 0.85f), radius = 7.dp.toPx(), center = Offset(sx, sy))
        }
    }
}

@Composable
private fun MoonStarsProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.82f
        val cy = size.height * 0.18f
        val yellow = Color(0xFFFFF176)
        val r = 28.dp.toPx()

        drawCircle(yellow, radius = r, center = Offset(cx, cy))
        drawCircle(Color(0xFF03070D), radius = r * 0.88f, center = Offset(cx + r * 0.45f, cy - r * 0.35f))
        drawCircle(Color.White, radius = 3.5.dp.toPx(), center = Offset(cx - 26.dp.toPx(), cy + 12.dp.toPx()))
    }
}

@Composable
private fun GhostProp() {
    val floatY by rememberInfiniteTransition(label = "Ghost").animateFloat(
        initialValue = -10.dp.value, targetValue = 10.dp.value,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse), label = "g"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.82f
        val cy = size.height * 0.35f + floatY
        val ghostCol = Color(0xE6FFFFFF)
        val w = 44.dp.toPx()
        val h = 54.dp.toPx()

        val ghost = Path().apply {
            moveTo(cx - w * 0.5f, cy + h * 0.5f)
            lineTo(cx - w * 0.5f, cy)
            cubicTo(cx - w * 0.5f, cy - h * 0.6f, cx + w * 0.5f, cy - h * 0.6f, cx + w * 0.5f, cy)
            lineTo(cx + w * 0.5f, cy + h * 0.5f)
            quadraticTo(cx + w * 0.25f, cy + h * 0.35f, cx, cy + h * 0.5f)
            quadraticTo(cx - w * 0.25f, cy + h * 0.35f, cx - w * 0.5f, cy + h * 0.5f)
            close()
        }
        drawPath(ghost, ghostCol)
        drawCircle(Color.Black, radius = 4.dp.toPx(), center = Offset(cx - 8.dp.toPx(), cy - 4.dp.toPx()))
        drawCircle(Color.Black, radius = 4.dp.toPx(), center = Offset(cx + 8.dp.toPx(), cy - 4.dp.toPx()))
    }
}

@Composable
private fun RocketProp() {
    val blast by rememberInfiniteTransition(label = "Rocket").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInQuad)), label = "b"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.82f
        val cy = size.height * 0.6f - blast * (size.height * 0.45f)
        val bodyCol = Color(0xFFECEFF1)
        val red = Color(0xFFFF1744)
        val w = 36.dp.toPx()
        val h = 56.dp.toPx()

        val rocket = Path().apply {
            moveTo(cx, cy - h * 0.6f)
            cubicTo(cx + w * 0.5f, cy - h * 0.2f, cx + w * 0.5f, cy + h * 0.4f, cx + w * 0.4f, cy + h * 0.5f)
            lineTo(cx - w * 0.4f, cy + h * 0.5f)
            cubicTo(cx - w * 0.5f, cy + h * 0.4f, cx - w * 0.5f, cy - h * 0.2f, cx, cy - h * 0.6f)
            close()
        }
        drawPath(rocket, bodyCol)
        drawCircle(Color(0xFF00E5FF), radius = 7.dp.toPx(), center = Offset(cx, cy - h * 0.1f))
        drawRect(red, topLeft = Offset(cx - w * 0.6f, cy + h * 0.3f), size = Size(w * 0.2f, h * 0.25f))
        drawRect(red, topLeft = Offset(cx + w * 0.4f, cy + h * 0.3f), size = Size(w * 0.2f, h * 0.25f))
    }
}

@Composable
private fun GearsProp() {
    val rot by rememberInfiniteTransition(label = "Gears").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "r"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.82f
        val cy = size.height * 0.22f
        val gearCol = Color(0xFF78909C)
        val r = 26.dp.toPx()

        drawCircle(gearCol, radius = r * 0.7f, center = Offset(cx, cy))
        drawCircle(Color.Black, radius = r * 0.25f, center = Offset(cx, cy))
        for (i in 0 until 8) {
            val angle = (rot + i * 45f) * PI_F / 180f
            val gx = cx + r * cos(angle)
            val gy = cy + r * sin(angle)
            drawCircle(gearCol, radius = 5.5.dp.toPx(), center = Offset(gx, gy))
        }
    }
}

@Composable
private fun LaptopProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.75f
        val gray = Color(0xFF455A64)
        val w = 60.dp.toPx()
        val h = 36.dp.toPx()

        drawRoundRect(gray, topLeft = Offset(cx - w * 0.5f, cy - h), size = Size(w, h), cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        drawRect(Color(0xFF00E5FF), topLeft = Offset(cx - w * 0.44f, cy - h + 3.dp.toPx()), size = Size(w * 0.88f, h - 6.dp.toPx()))
        drawRoundRect(Color(0xFF263238), topLeft = Offset(cx - w * 0.6f, cy), size = Size(w * 1.2f, 7.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))
    }
}

@Composable
private fun BatteryChargingProp() {
    val pulse by rememberInfiniteTransition(label = "BatC").animateFloat(
        initialValue = 0.8f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(400, easing = EaseInOutSine), RepeatMode.Reverse), label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.85f
        val cy = size.height * 0.16f
        val green = Color(0xFF00E676)
        val w = 50.dp.toPx()
        val h = 26.dp.toPx()

        drawRoundRect(green, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h), cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()), style = Stroke(3.dp.toPx()))
        drawRect(green, topLeft = Offset(cx + w * 0.5f, cy - 4.dp.toPx()), size = Size(4.dp.toPx(), 8.dp.toPx()))
        val bolt = Path().apply {
            moveTo(cx + 2.dp.toPx(), cy - 7.dp.toPx())
            lineTo(cx - 6.dp.toPx(), cy + 1.dp.toPx())
            lineTo(cx, cy + 1.dp.toPx())
            lineTo(cx - 2.dp.toPx(), cy + 7.dp.toPx())
            lineTo(cx + 6.dp.toPx(), cy - 1.dp.toPx())
            lineTo(cx, cy - 1.dp.toPx())
            close()
        }
        drawPath(bolt, green.copy(alpha = pulse))
    }
}

@Composable
private fun BatteryLowProp() {
    val blink by rememberInfiniteTransition(label = "BatL").animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = "b"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.85f
        val cy = size.height * 0.16f
        val red = Color(0xFFFF1744)
        val w = 50.dp.toPx()
        val h = 26.dp.toPx()

        drawRoundRect(red, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h), cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()), style = Stroke(3.dp.toPx()))
        drawRect(red, topLeft = Offset(cx + w * 0.5f, cy - 4.dp.toPx()), size = Size(4.dp.toPx(), 8.dp.toPx()))
        drawRect(red.copy(alpha = blink), topLeft = Offset(cx - w * 0.45f, cy - h * 0.35f), size = Size(w * 0.22f, h * 0.7f))
    }
}

@Composable
private fun ClockAlarmProp() {
    val shake by rememberInfiniteTransition(label = "Clock").animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(80, easing = LinearEasing), RepeatMode.Reverse), label = "s"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.82f
        val cy = size.height * 0.2f
        val r = 24.dp.toPx()
        val orange = Color(0xFFFF9100)

        drawCircle(orange, radius = 7.5.dp.toPx(), center = Offset(cx - r * 0.8f + shake, cy - r * 0.8f))
        drawCircle(orange, radius = 7.5.dp.toPx(), center = Offset(cx + r * 0.8f - shake, cy - r * 0.8f))
        drawCircle(orange, radius = r, center = Offset(cx, cy))
        drawCircle(Color.White, radius = r * 0.8f, center = Offset(cx, cy))
        drawLine(Color.Black, Offset(cx, cy), Offset(cx, cy - r * 0.5f), strokeWidth = 3.dp.toPx())
        drawLine(Color.Black, Offset(cx, cy), Offset(cx + r * 0.4f, cy), strokeWidth = 3.dp.toPx())
    }
}

@Composable
private fun MagnifyingGlassProp() {
    val scan by rememberInfiniteTransition(label = "Scan").animateFloat(
        initialValue = -15.dp.value, targetValue = 15.dp.value,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse), label = "s"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f + scan
        val cy = size.height * 0.45f
        val r = 26.dp.toPx()
        val cyan = Color(0xFF00E5FF)

        drawCircle(cyan, radius = r, center = Offset(cx, cy), style = Stroke(5.dp.toPx()))
        drawLine(Color(0xFF78909C), Offset(cx + r * 0.7f, cy + r * 0.7f), Offset(cx + r * 1.5f, cy + r * 1.5f), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun ShieldProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.16f
        val blue = Color(0xFF2979FF)
        val w = 50.dp.toPx()
        val h = 54.dp.toPx()

        val shield = Path().apply {
            moveTo(cx, cy - h * 0.5f)
            lineTo(cx + w * 0.5f, cy - h * 0.35f)
            cubicTo(cx + w * 0.5f, cy + h * 0.2f, cx, cy + h * 0.5f, cx, cy + h * 0.5f)
            cubicTo(cx, cy + h * 0.5f, cx - w * 0.5f, cy + h * 0.2f, cx - w * 0.5f, cy - h * 0.35f)
            close()
        }
        drawPath(shield, blue)
        drawPath(shield, Color.White.copy(alpha = 0.5f), style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun WarningTriangleProp() {
    val blink by rememberInfiniteTransition(label = "Warn").animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(300, easing = EaseInOutSine), RepeatMode.Reverse), label = "b"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.18f
        val amber = Color(0xFFFFAB00)
        val s = 36.dp.toPx()

        val tri = Path().apply {
            moveTo(cx, cy - s)
            lineTo(cx + s, cy + s * 0.7f)
            lineTo(cx - s, cy + s * 0.7f)
            close()
        }
        drawPath(tri, amber.copy(alpha = blink))
        drawLine(Color.Black, Offset(cx, cy - s * 0.3f), Offset(cx, cy + s * 0.2f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.Black, radius = 2.5.dp.toPx(), center = Offset(cx, cy + s * 0.45f))
    }
}

@Composable
private fun SirenLightProp() {
    val rot by rememberInfiniteTransition(label = "Siren").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing)), label = "r"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.15f
        val red = Color(0xFFFF1744)
        val r = 28.dp.toPx()

        drawArc(red.copy(alpha = 0.35f), rot, 90f, true, topLeft = Offset(cx - r * 2.5f, cy - r * 2.5f), size = Size(r * 5f, r * 5f))
        drawArc(red, 180f, 180f, true, topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f))
        drawRect(Color(0xFF37474F), topLeft = Offset(cx - r * 1.1f, cy), size = Size(r * 2.2f, 11.dp.toPx()))
    }
}

@Composable
private fun CheckMarkProp() {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.18f
        val green = Color(0xFF00E676)
        val r = 30.dp.toPx()

        drawCircle(green.copy(alpha = 0.25f), radius = r * 1.4f, center = Offset(cx, cy))
        drawCircle(green, radius = r, center = Offset(cx, cy))
        val check = Path().apply {
            moveTo(cx - r * 0.45f, cy)
            lineTo(cx - r * 0.1f, cy + r * 0.4f)
            lineTo(cx + r * 0.45f, cy - r * 0.35f)
        }
        drawPath(check, Color.White, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun TargetReticleProp() {
    val rot by rememberInfiniteTransition(label = "Reticle").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "r"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.48f
        val cyan = Color(0xFF00F0FF)
        val r = 80.dp.toPx()

        drawArc(cyan, rot, 60f, false, topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f), style = Stroke(3.5.dp.toPx()))
        drawArc(cyan, rot + 120f, 60f, false, topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f), style = Stroke(3.5.dp.toPx()))
        drawArc(cyan, rot + 240f, 60f, false, topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f), style = Stroke(3.5.dp.toPx()))
        drawLine(cyan.copy(alpha = 0.6f), Offset(cx - r * 1.15f, cy), Offset(cx - r * 0.7f, cy), strokeWidth = 2.5.dp.toPx())
        drawLine(cyan.copy(alpha = 0.6f), Offset(cx + r * 0.7f, cy), Offset(cx + r * 1.15f, cy), strokeWidth = 2.5.dp.toPx())
        drawLine(cyan.copy(alpha = 0.6f), Offset(cx, cy - r * 1.15f), Offset(cx, cy - r * 0.7f), strokeWidth = 2.5.dp.toPx())
        drawLine(cyan.copy(alpha = 0.6f), Offset(cx, cy + r * 0.7f), Offset(cx, cy + r * 1.15f), strokeWidth = 2.5.dp.toPx())
    }
}

@Composable
private fun CatPawProp() {
    val tap by rememberInfiniteTransition(label = "Paw").animateFloat(
        initialValue = 0.8f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(400, easing = EaseInOutSine), RepeatMode.Reverse), label = "t"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width * 0.82f
        val cy = size.height * 0.72f
        val pink = Color(0xFFFF80AB)
        val r = 22.dp.toPx() * tap

        drawCircle(pink, radius = r, center = Offset(cx, cy))
        drawCircle(pink, radius = r * 0.42f, center = Offset(cx - r * 0.95f, cy - r * 0.8f))
        drawCircle(pink, radius = r * 0.48f, center = Offset(cx - r * 0.35f, cy - r * 1.25f))
        drawCircle(pink, radius = r * 0.48f, center = Offset(cx + r * 0.35f, cy - r * 1.25f))
        drawCircle(pink, radius = r * 0.42f, center = Offset(cx + r * 0.95f, cy - r * 0.8f))
    }
}

@Composable
private fun GoldCoinProp() {
    val infinite = rememberInfiniteTransition(label = "GoldCoins")
    val progress by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "p"
    )
    val spin by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "s"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val goldMain = Color(0xFFFFD700)
        val goldDark = Color(0xFFFF9800)
        val goldLight = Color(0xFFFFF59D)

        val xOffsets = floatArrayOf(-0.25f, 0.22f, -0.08f, 0.32f)
        val timeOffsets = floatArrayOf(0f, 0.25f, 0.55f, 0.80f)
        val sizes = floatArrayOf(24.dp.toPx(), 20.dp.toPx(), 28.dp.toPx(), 18.dp.toPx())

        for (i in 0 until 4) {
            val p = (progress + timeOffsets[i]) % 1f
            val alpha = when { p < 0.15f -> p / 0.15f; p > 0.75f -> (1f - p) / 0.25f; else -> 1f }
            val cx = (size.width * 0.5f) + (size.width * xOffsets[i]) + sin(p * TWO_PI_F + i) * 16.dp.toPx()
            val cy = (size.height * 0.80f) - p * (size.height * 0.60f)
            val coinR = sizes[i]

            // 3D spinning coin width scale
            val spinScaleX = abs(cos((spin + i * 90f) * PI_F / 180f)).coerceAtLeast(0.18f)

            // Outer Gold Rim
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(goldLight.copy(alpha = alpha), goldDark.copy(alpha = alpha)),
                    center = Offset(cx, cy),
                    radius = coinR
                ),
                topLeft = Offset(cx - coinR * spinScaleX, cy - coinR),
                size = Size(coinR * 2f * spinScaleX, coinR * 2f)
            )
            // Inner Emboss
            drawOval(
                color = goldMain.copy(alpha = alpha),
                topLeft = Offset(cx - coinR * 0.75f * spinScaleX, cy - coinR * 0.75f),
                size = Size(coinR * 1.5f * spinScaleX, coinR * 1.5f)
            )

            // Sparkle Star around coin
            val sparkleP = (p * 2f) % 1f
            val sparkX = cx + cos(p * TWO_PI_F * 2f) * (coinR * 1.4f)
            val sparkY = cy + sin(p * TWO_PI_F * 2f) * (coinR * 1.4f)
            val sparkSize = 6.dp.toPx() * (1f - abs(sparkleP - 0.5f) * 2f).coerceAtLeast(0f)
            if (sparkSize > 1f) {
                drawLine(goldLight.copy(alpha = alpha), Offset(sparkX - sparkSize, sparkY), Offset(sparkX + sparkSize, sparkY), strokeWidth = 2.dp.toPx())
                drawLine(goldLight.copy(alpha = alpha), Offset(sparkX, sparkY - sparkSize), Offset(sparkX, sparkY + sparkSize), strokeWidth = 2.dp.toPx())
            }
        }
    }
}

@Composable
private fun RainDropsProp() {
    val progress by rememberInfiniteTransition(label = "Rain").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val rainColor = Color(0xFF40C4FF)
        val splashColor = Color(0xFF80D8FF)

        val dropCount = 12
        for (i in 0 until dropCount) {
            val seed = i * 0.083f
            val p = (progress + seed) % 1f
            val startX = size.width * (0.05f + (i * 0.08f) % 0.90f)
            val startY = -40.dp.toPx()
            val totalDistance = size.height * 0.95f

            val curX = startX - p * 30.dp.toPx() // slight wind slant
            val curY = startY + p * totalDistance
            val alpha = when { p < 0.1f -> p / 0.1f; p > 0.85f -> (1f - p) / 0.15f; else -> 0.75f }

            // Falling droplet streak
            val streakLen = 22.dp.toPx()
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(rainColor.copy(alpha = 0f), rainColor.copy(alpha = alpha)),
                    startY = curY - streakLen,
                    endY = curY
                ),
                start = Offset(curX + 6.dp.toPx(), curY - streakLen),
                end = Offset(curX, curY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Splash ripple when near bottom
            if (p > 0.82f) {
                val splashP = (p - 0.82f) / 0.18f
                val rippleR = splashP * 16.dp.toPx()
                val rippleAlpha = (1f - splashP) * 0.6f
                drawOval(
                    color = splashColor.copy(alpha = rippleAlpha),
                    topLeft = Offset(curX - rippleR, curY - rippleR * 0.35f),
                    size = Size(rippleR * 2f, rippleR * 0.7f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }
}


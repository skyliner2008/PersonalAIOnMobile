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

private typealias PetFaceGeometry = AvatarLayoutInfo

private fun calculateGeometry(width: Float, height: Float): AvatarLayoutInfo {
    return calculateAvatarLayout(width, height)
}


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
        PropType.HEADPHONES -> MusicNotesProp()
        PropType.VR_HEADSET -> GamingControllerProp()
        PropType.SNORKEL_MASK -> RainDropsProp()
        PropType.DEVIL_HORNS -> FireProp()
        PropType.SCANNER_GRID -> TargetReticleProp()
        PropType.TRASH_BIN -> WarningTriangleProp()
        PropType.CAMERA_ICON -> SparklesProp()
        // ─── LOOI 30 Additional Moodset Props ───
        PropType.THERMOMETER -> SweatDropProp()
        PropType.MONEY_BAG -> GoldCoinProp()
        PropType.DOLLAR_SIGN -> GoldCoinProp()
        PropType.THOUGHT_BUBBLE -> QuestionMarkProp()
        PropType.GLASSES_SQUARE -> SunglassesProp()
        PropType.GAMING_HEADSET -> GamingControllerProp()
        PropType.BUCKET_HAT -> SunglassesProp()
        PropType.PASSPORT -> BookProp()
        PropType.BACKPACK -> GiftProp()
        PropType.ICICLES -> SnowProp()
        PropType.HEATWAVES -> FireProp()
        PropType.FEDORA_HAT -> SunglassesProp()
        PropType.CHEF_HAT -> CakeProp()
        PropType.SPATULA -> BurgerProp()
        PropType.BERET -> SparklesProp()
        PropType.PALETTE -> RainbowProp()
        PropType.ASTRONAUT_HELMET -> RocketProp()
        PropType.PARTY_HAT -> PartyPopperProp()
        PropType.NOISEMAKER -> BalloonsProp()
        PropType.CONFETTI -> PartyPopperProp()
        PropType.DREAM_CLOUD -> CloudProp()
        PropType.LIGHTNING_EYES -> LightningProp()
        PropType.BANDIT_MASK -> SunglassesProp()
        PropType.ROSE -> HeartsProp()
        PropType.SUPERHERO_CAPE -> ShieldProp()
        PropType.SUPERHERO_MASK -> SunglassesProp()
        PropType.WIZARD_HAT -> SparklesProp()
        PropType.MAGIC_WAND -> SparklesProp()
        PropType.SWEATBAND -> FireProp()
        PropType.BASKETBALL -> GoldCoinProp()
        PropType.LAB_GOGGLES -> SunglassesProp()
        PropType.BEAKER -> BobaTeaProp()
        PropType.VIKING_HELMET -> CrownProp()
        PropType.SWORD -> ShieldProp()
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

/** แสดงเครื่องหมายคำถามเด้งดึ๋งเหนือดวงตา (Single bouncing cyan question mark above eyes) */
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
        val geo = calculateGeometry(size.width, size.height)
        val cyan = Color(0xFF00F0FF)
        val cx = geo.rightTempleX
        val cy = (geo.foreheadY - 14.dp.toPx()) - bounce * 14.dp.toPx()
        val qh = 44.dp.toPx()
        val qw = qh * 0.55f
        val qPath = Path().apply {
            moveTo(cx - qw * 0.45f, cy - qh * 0.35f)
            cubicTo(cx - qw * 0.5f, cy - qh * 0.65f, cx + qw * 0.5f, cy - qh * 0.65f, cx + qw * 0.35f, cy - qh * 0.35f)
            cubicTo(cx + qw * 0.25f, cy - qh * 0.15f, cx, cy - qh * 0.08f, cx, cy + qh * 0.06f)
        }
        drawPath(qPath, cyan.copy(alpha = 0.4f), style = Stroke(10f, cap = StrokeCap.Round))
        drawPath(qPath, cyan, style = Stroke(5.5f, cap = StrokeCap.Round))
        val dotY = cy + qh * 0.26f
        drawCircle(cyan.copy(alpha = 0.4f), 7.5f, Offset(cx, dotY))
        drawCircle(cyan, 4.5f, Offset(cx, dotY))
    }
}

// ─── 5. EXCLAMATION PROP ─────────────────────────────────────────────────────

/** แสดงเครื่องหมายตกใจสีแดงสั่นไหวเหนือดวงตา (Single shaking red exclamation mark above eyes) */
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
        val geo = calculateGeometry(size.width, size.height)
        val red = Color(0xFFFF1E46)
        val cx = geo.rightTempleX + shake * 4.dp.toPx()
        val cy = geo.foreheadY - 14.dp.toPx()
        val barH = 40.dp.toPx() * pulse
        val barTop = cy - barH * 0.45f
        val barBottom = cy + barH * 0.1f

        drawLine(red.copy(alpha = 0.4f), Offset(cx, barTop), Offset(cx, barBottom), 12f, StrokeCap.Round)
        drawLine(red, Offset(cx, barTop), Offset(cx, barBottom), 6.5f, StrokeCap.Round)
        val dotY = cy + barH * 0.32f
        drawCircle(red.copy(alpha = 0.4f), 7.5f, Offset(cx, dotY))
    }
}

// ─── 6. SWEAT DROP PROP ──────────────────────────────────────────────────────

/** แสดงหยดเหงื่อสีฟ้าไหลลงทางขวา (Single blue teardrop sliding down from pet temple) */
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
        val geo = calculateGeometry(size.width, size.height)
        val blue = Color(0xFF42A5F5)
        val cx = geo.rightTempleX + 16.dp.toPx()
        val cy = geo.foreheadY + progress * 32.dp.toPx()
        val alpha = when { progress < 0.2f -> progress / 0.2f; progress > 0.75f -> (1f - progress) / 0.25f; else -> 1f }
        val dw = 18.dp.toPx()
        val dh = 28.dp.toPx()

        rotate(degrees = 15f, pivot = Offset(cx, cy)) {
            val drop = Path().apply {
                moveTo(cx, cy - dh * 0.5f)
                cubicTo(cx + dw * 0.55f, cy, cx + dw * 0.55f, cy + dh * 0.5f, cx, cy + dh * 0.5f)
                cubicTo(cx - dw * 0.55f, cy + dh * 0.5f, cx - dw * 0.55f, cy, cx, cy - dh * 0.5f)
                close()
            }
            drawPath(drop, blue.copy(alpha = alpha * 0.4f), style = Stroke(4.dp.toPx()))
            drawPath(drop, blue.copy(alpha = alpha))
            drawCircle(Color.White.copy(alpha = alpha * 0.75f), dw * 0.18f, Offset(cx - dw * 0.18f, cy + dh * 0.18f))
        }
    }
}

// ─── 7. UMBRELLA PROP ────────────────────────────────────────────────────────

/** แสดงร่มกางกันฝนลอยอยู่เหนือดวงตาพอดี พร้อมเม็ดฝนตกกระทบกระเซ็น (Sheltering umbrella hovering right above eyes with raindrop ripples) */
@Composable
private fun UmbrellaProp() {
    val t = rememberInfiniteTransition(label = "Umbrella")
    val hover by t.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "h"
    )
    val tilt by t.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(tween(2600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "t"
    )
    val rainProg by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "r"
    )

    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)

        val cx = geo.cX
        val cy = geo.foreheadY + 12.dp.toPx() + hover * 5.dp.toPx()
        val dw = 175.dp.toPx()
        val dh = 72.dp.toPx()

        val cyanLight = Color(0xFF80D8FF)
        val cyanMain = Color(0xFF00B0FF)
        val cyanDark = Color(0xFF0277BD)

        rotate(degrees = tilt, pivot = Offset(cx, cy)) {
            val sw = dw / 3f

            // Umbrella Canopy Path
            val canopy = Path().apply {
                moveTo(cx - dw / 2f, cy)
                cubicTo(cx - dw * 0.45f, cy - dh * 1.15f, cx + dw * 0.45f, cy - dh * 1.15f, cx + dw / 2f, cy)
                cubicTo(cx + dw / 2f - sw * 0.3f, cy - 5.dp.toPx(), cx + dw / 6f + sw * 0.3f, cy - 5.dp.toPx(), cx + dw / 6f, cy)
                cubicTo(cx + dw / 6f - sw * 0.3f, cy - 5.dp.toPx(), cx - dw / 6f + sw * 0.3f, cy - 5.dp.toPx(), cx - dw / 6f, cy)
                cubicTo(cx - dw / 6f - sw * 0.3f, cy - 5.dp.toPx(), cx - dw / 2f + sw * 0.3f, cy - 5.dp.toPx(), cx - dw / 2f, cy)
                close()
            }

            // 1. Shaded Canopy Fill
            drawPath(
                canopy,
                brush = Brush.verticalGradient(
                    colors = listOf(cyanLight.copy(alpha = 0.90f), cyanMain.copy(alpha = 0.85f), cyanDark.copy(alpha = 0.90f)),
                    startY = cy - dh * 1.15f,
                    endY = cy
                )
            )

            // 2. Rib lines giving 3D curvature
            drawLine(
                Color.White.copy(alpha = 0.45f),
                start = Offset(cx, cy - dh * 1.10f),
                end = Offset(cx - dw / 6f, cy - 2.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                Color.White.copy(alpha = 0.45f),
                start = Offset(cx, cy - dh * 1.10f),
                end = Offset(cx + dw / 6f, cy - 2.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 3. Highlight gloss arc on canopy
            val highlightPath = Path().apply {
                moveTo(cx - dw * 0.35f, cy - dh * 0.65f)
                cubicTo(cx - dw * 0.2f, cy - dh * 1.0f, cx + dw * 0.2f, cy - dh * 1.0f, cx + dw * 0.35f, cy - dh * 0.65f)
            }
            drawPath(highlightPath, Color.White.copy(alpha = 0.40f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))

            // 4. Canopy Outline
            drawPath(canopy, Color.White.copy(alpha = 0.7f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

            // 5. Top Ferrite Spike
            drawLine(Color.White, Offset(cx, cy - dh * 1.10f), Offset(cx, cy - dh * 1.25f), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color(0xFFFFD600), radius = 3.5.dp.toPx(), center = Offset(cx, cy - dh * 1.25f))

            // 6. Umbrella Stem & Curved Handle (extends beside the eyes)
            val stemBottom = cy + 58.dp.toPx()
            drawLine(Color(0xFFCFD8DC), Offset(cx, cy - 2.dp.toPx()), Offset(cx, stemBottom), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            val hr = 8.dp.toPx()
            drawArc(
                color = Color(0xFF90A4AE),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - hr * 2f, stemBottom - hr),
                size = Size(hr * 2f, hr * 2f),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
            )

            // 7. Raindrops bouncing off canopy with splash ripples
            val splashOffsets = floatArrayOf(-0.35f, -0.10f, 0.18f, 0.32f)
            for (i in 0 until 4) {
                val p = (rainProg + i * 0.25f) % 1f
                val rx = cx + dw * splashOffsets[i]
                val ry = cy - dh * (0.85f - abs(splashOffsets[i]) * 0.7f)
                if (p < 0.5f) {
                    val dropY = ry - (1f - p * 2f) * 20.dp.toPx()
                    drawLine(
                        Color(0xFF80D8FF).copy(alpha = 0.8f),
                        Offset(rx + 2.dp.toPx(), dropY - 8.dp.toPx()),
                        Offset(rx, dropY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    val sp = (p - 0.5f) / 0.5f
                    val ripR = sp * 8.dp.toPx()
                    drawOval(
                        Color.White.copy(alpha = (1f - sp) * 0.7f),
                        topLeft = Offset(rx - ripR, ry - ripR * 0.4f),
                        size = Size(ripR * 2f, ripR * 0.8f),
                        style = Stroke(1.5.dp.toPx())
                    )
                }
            }
        }
    }
}

// ─── 8. ZZZZZ PROP ───────────────────────────────────────────────────────────

/** แสดงสัญลักษณ์ Z 3 ตัวลอยเฉียงขึ้นขวาจากหน้าผาก (3 cyan Z letters floating up-right from forehead) */
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
        val geo = calculateGeometry(size.width, size.height)
        val cyan = Color(0xFF00F0FF)
        val timeOffsets = floatArrayOf(0f, 0.33f, 0.66f)
        val baseSizes = floatArrayOf(16.dp.toPx(), 22.dp.toPx(), 30.dp.toPx())
        val sx = geo.rightTempleX + 10.dp.toPx()
        val sy = geo.foreheadY
        val ex = geo.rightTempleX + 38.dp.toPx()
        val ey = geo.foreheadY - 45.dp.toPx()

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

/** แสดงเปลวไฟ 3 ลูกกะพริบสลับกันใต้คาง (3 flickering flame shapes beneath chin) */
@Composable
private fun FireProp() {
    val t = rememberInfiniteTransition(label = "Fire")
    val f1 by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(140, easing = FastOutSlowInEasing), RepeatMode.Reverse), "f1")
    val f2 by t.animateFloat(1.15f, 0.82f, infiniteRepeatable(tween(190, easing = FastOutSlowInEasing), RepeatMode.Reverse), "f2")
    val f3 by t.animateFloat(0.88f, 1.2f, infiniteRepeatable(tween(160, easing = FastOutSlowInEasing), RepeatMode.Reverse), "f3")
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val baseY = geo.chinY + 24.dp.toPx()
        val cx = geo.cX
        drawFlame(cx, baseY, 32.dp.toPx(), 65.dp.toPx() * f1)
        drawFlame(cx - 26.dp.toPx(), baseY + 3.dp.toPx(), 24.dp.toPx(), 48.dp.toPx() * f2)
        drawFlame(cx + 26.dp.toPx(), baseY + 3.dp.toPx(), 24.dp.toPx(), 52.dp.toPx() * f3)
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
        initialValue = 0f, targetValue = 14.dp.value,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse), label = "s"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.foreheadY - 14.dp.toPx()
        val hs = 64.dp.toPx()
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
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.rightTempleX + 12.dp.toPx()
        val cy = geo.foreheadY - 10.dp.toPx()
        val s = 34.dp.toPx() * pulse
        val red = Color(0xFFFF1744)

        val path = Path().apply {
            moveTo(cx - s, cy - s * 0.3f); quadraticTo(cx - s * 0.3f, cy - s * 0.3f, cx - s * 0.3f, cy - s)
            moveTo(cx + s * 0.3f, cy - s); quadraticTo(cx + s * 0.3f, cy - s * 0.3f, cx + s, cy - s * 0.3f)
            moveTo(cx + s, cy + s * 0.3f); quadraticTo(cx + s * 0.3f, cy + s * 0.3f, cx + s * 0.3f, cy + s)
            moveTo(cx - s * 0.3f, cy + s); quadraticTo(cx - s * 0.3f, cy + s * 0.3f, cx - s, cy + s * 0.3f)
        }
        drawPath(path, red, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
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
        val geo = calculateGeometry(size.width, size.height)
        val cyan = Color(0xFF00E5FF)
        val leftEyeX = geo.cX - geo.eyeDiameter * 0.52f
        val rightEyeX = geo.cX + geo.eyeDiameter * 0.52f
        val startY = geo.cY + geo.eyeDiameter * 0.35f
        val streamLen = geo.eyeDiameter * 0.85f

        for (sideX in floatArrayOf(leftEyeX, rightEyeX)) {
            for (i in 0 until 3) {
                val p = (progress + i * 0.33f) % 1f
                val y = startY + p * streamLen
                val r = (10.dp.toPx() + i * 2.5.dp.toPx()) * (1f - p * 0.35f)
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
        val geo = calculateGeometry(size.width, size.height)
        val gold = Color(0xFFFFD700)
        val cx = geo.cX
        val cy = geo.foreheadY - 8.dp.toPx()
        val rx = geo.eyeDiameter * 0.95f
        val ry = 28.dp.toPx()

        for (i in 0 until 3) {
            val angle = rot + i * (TWO_PI_F / 3f)
            val sx = cx + rx * cos(angle)
            val sy = cy + ry * sin(angle)
            val zAlpha = (sin(angle) + 1f) * 0.4f + 0.2f
            val r = 24.dp.toPx() * (0.8f + 0.2f * sin(angle))

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
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.foreheadY - 12.dp.toPx() + floatY
        val gray = Color(0xFFCFD8DC)
        val r = 34.dp.toPx()

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
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.foreheadY - 14.dp.toPx()
        val yellow = Color(0xFFFFD600)
        val r = 30.dp.toPx()

        drawCircle(yellow.copy(alpha = 0.28f * glow), radius = r * 1.8f * glow, center = Offset(cx, cy))
        drawCircle(yellow, radius = r, center = Offset(cx, cy))
        drawRect(Color(0xFF90A4AE), topLeft = Offset(cx - r * 0.4f, cy + r * 0.7f), size = Size(r * 0.8f, r * 0.4f))
        for (step in 0 until 6) {
            val angle = step * PI_F / 5f - PI_F
            val x1 = cx + r * 1.3f * cos(angle)
            val y1 = cy + r * 1.3f * sin(angle)
            val x2 = cx + r * 1.7f * cos(angle)
            val y2 = cy + r * 1.7f * sin(angle)
            drawLine(yellow, Offset(x1, y1), Offset(x2, y2), strokeWidth = 4.5.dp.toPx(), cap = StrokeCap.Round)
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
    val infinite = rememberInfiniteTransition(label = "Crown")
    val bob by infinite.animateFloat(
        initialValue = -4.dp.value, targetValue = 4.dp.value,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse), label = "b"
    )
    val spin by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "s"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.foreheadY - 10.dp.toPx() + bob
        val w = 94.dp.toPx()
        val h = 54.dp.toPx()

        // 1. Velvet inner base
        val velvet = Path().apply {
            moveTo(cx - w * 0.42f, cy + h * 0.45f)
            cubicTo(cx - w * 0.2f, cy + h * 0.05f, cx + w * 0.2f, cy + h * 0.05f, cx + w * 0.42f, cy + h * 0.45f)
            close()
        }
        drawPath(velvet, Color(0xFFC2185B))

        // 2. 5-point Royal Golden Crown Path
        val crownPath = Path().apply {
            moveTo(cx - w * 0.48f, cy + h * 0.48f)
            lineTo(cx - w * 0.50f, cy - h * 0.30f) // left peak
            lineTo(cx - w * 0.25f, cy + h * 0.08f)
            lineTo(cx, cy - h * 0.52f) // central tall peak
            lineTo(cx + w * 0.25f, cy + h * 0.08f)
            lineTo(cx + w * 0.50f, cy - h * 0.30f) // right peak
            lineTo(cx + w * 0.48f, cy + h * 0.48f)
            close()
        }
        drawPath(
            crownPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFF176), Color(0xFFFFD700), Color(0xFFFF8F00)),
                startY = cy - h * 0.52f,
                endY = cy + h * 0.48f
            )
        )
        drawPath(crownPath, Color(0xFFFFA000), style = Stroke(2.5.dp.toPx()))

        // 3. Golden Rim Band with pearls
        drawRoundRect(
            color = Color(0xFFFFC107),
            topLeft = Offset(cx - w * 0.48f, cy + h * 0.38f),
            size = Size(w * 0.96f, h * 0.16f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        for (i in -2..2) {
            drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(cx + i * (w * 0.20f), cy + h * 0.46f))
        }

        // 4. Jewels on peaks
        drawCircle(Color(0xFF00E5FF), radius = 6.5.dp.toPx(), center = Offset(cx, cy - h * 0.52f)) // Sapphire
        drawCircle(Color(0xFFFF1744), radius = 5.dp.toPx(), center = Offset(cx - w * 0.50f, cy - h * 0.30f)) // Ruby left
        drawCircle(Color(0xFFFF1744), radius = 5.dp.toPx(), center = Offset(cx + w * 0.50f, cy - h * 0.30f)) // Ruby right

        // 5. Rotating Diamond Star Glint on central Sapphire
        val glintLen = 8.dp.toPx()
        val rad = spin * PI_F / 180f
        val gx = cos(rad) * glintLen
        val gy = sin(rad) * glintLen
        drawLine(Color.White, Offset(cx - gx, cy - h * 0.52f - gy), Offset(cx + gx, cy - h * 0.52f + gy), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, Offset(cx + gy, cy - h * 0.52f - gx), Offset(cx - gy, cy - h * 0.52f + gx), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun SunglassesProp() {
    val drop by rememberInfiniteTransition(label = "SunG").animateFloat(
        initialValue = -24.dp.value, targetValue = 0.dp.value,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseOutBounce), RepeatMode.Restart), label = "d"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.cY + drop
        val lensW = 76.dp.toPx()
        val lensH = 44.dp.toPx()
        val bridgeW = 18.dp.toPx()

        // Dark Bridge
        drawLine(Color(0xFF111111), Offset(cx - bridgeW * 0.5f, cy), Offset(cx + bridgeW * 0.5f, cy), strokeWidth = 7.dp.toPx())

        // Left & Right Lenses
        drawRoundRect(Color(0xFF151515), topLeft = Offset(cx - bridgeW * 0.5f - lensW, cy - lensH * 0.5f), size = Size(lensW, lensH), cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()))
        drawRoundRect(Color(0xFF151515), topLeft = Offset(cx + bridgeW * 0.5f, cy - lensH * 0.5f), size = Size(lensW, lensH), cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()))

        // Gloss Specular Glare (Cool diagonal white reflection lines)
        val glareLeft = cx - bridgeW * 0.5f - lensW
        drawLine(Color.White.copy(alpha = 0.5f), Offset(glareLeft + 10.dp.toPx(), cy - lensH * 0.35f), Offset(glareLeft + 32.dp.toPx(), cy + lensH * 0.35f), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
        val glareRight = cx + bridgeW * 0.5f
        drawLine(Color.White.copy(alpha = 0.5f), Offset(glareRight + 10.dp.toPx(), cy - lensH * 0.35f), Offset(glareRight + 32.dp.toPx(), cy + lensH * 0.35f), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun CoffeeProp() {
    val infinite = rememberInfiniteTransition(label = "Coffee")
    val steam by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "s"
    )
    val bob by infinite.animateFloat(
        initialValue = -3.dp.value, targetValue = 3.dp.value,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse), label = "b"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX + geo.eyeDiameter * 0.45f
        val cy = geo.mouthY + 8.dp.toPx() + bob
        val w = 62.dp.toPx()
        val h = 54.dp.toPx()

        val mugCol = Color(0xFF8D6E63)
        val coffeeCol = Color(0xFF3E2723)

        // Ceramic Mug Body
        drawRoundRect(mugCol, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h), cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()))
        // Coffee surface
        drawOval(coffeeCol, topLeft = Offset(cx - w * 0.44f, cy - h * 0.52f), size = Size(w * 0.88f, 16.dp.toPx()))
        // Foam swirl heart
        drawCircle(Color(0xFFD7CCC8), radius = 4.dp.toPx(), center = Offset(cx, cy - h * 0.45f))

        // Handle
        drawArc(mugCol, 270f, 180f, false, topLeft = Offset(cx + w * 0.38f, cy - h * 0.35f), size = Size(22.dp.toPx(), h * 0.7f), style = Stroke(6.dp.toPx()))

        // Rising Steam Wisps
        for (i in -1..1) {
            val sy = cy - h * 0.55f - steam * 36.dp.toPx()
            val sx = cx + i * 12.dp.toPx() + sin(steam * TWO_PI_F + i * 1.5f) * 8.dp.toPx()
            val alpha = (1f - steam).coerceIn(0f, 0.85f)
            drawLine(Color.White.copy(alpha = alpha), Offset(sx, sy), Offset(sx + 3.dp.toPx(), sy - 18.dp.toPx()), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun TeaCupProp() {
    val infinite = rememberInfiniteTransition(label = "Tea")
    val steam by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "s"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX + geo.eyeDiameter * 0.45f
        val cy = geo.mouthY + 8.dp.toPx()
        val w = 64.dp.toPx()
        val h = 36.dp.toPx()

        // Saucer
        drawRoundRect(Color(0xFFE0E0E0), topLeft = Offset(cx - w * 0.65f, cy + h * 0.4f), size = Size(w * 1.3f, 9.dp.toPx()), cornerRadius = CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx()))

        // Porcelain Cup
        val cupPath = Path().apply {
            moveTo(cx - w * 0.5f, cy - h * 0.4f)
            lineTo(cx + w * 0.5f, cy - h * 0.4f)
            cubicTo(cx + w * 0.45f, cy + h * 0.45f, cx - w * 0.45f, cy + h * 0.45f, cx - w * 0.5f, cy - h * 0.4f)
            close()
        }
        drawPath(cupPath, Color(0xFFF5F5F5))
        drawPath(cupPath, Color(0xFFB0BEC5), style = Stroke(2.dp.toPx()))

        // Green Tea
        drawOval(Color(0xFF81C784), topLeft = Offset(cx - w * 0.45f, cy - h * 0.42f), size = Size(w * 0.9f, 12.dp.toPx()))
        // Floating tea leaf
        drawCircle(Color(0xFF2E7D32), radius = 3.5.dp.toPx(), center = Offset(cx - 6.dp.toPx(), cy - h * 0.35f))

        // Handle
        drawArc(Color(0xFFE0E0E0), 270f, 180f, false, topLeft = Offset(cx + w * 0.4f, cy - h * 0.3f), size = Size(16.dp.toPx(), h * 0.6f), style = Stroke(4.dp.toPx()))

        // Steam
        val sy = cy - h * 0.5f - steam * 28.dp.toPx()
        val sx = cx + sin(steam * TWO_PI_F) * 6.dp.toPx()
        val alpha = (1f - steam).coerceIn(0f, 0.75f)
        drawLine(Color.White.copy(alpha = alpha), Offset(sx, sy), Offset(sx, sy - 14.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun BeerProp() {
    val infinite = rememberInfiniteTransition(label = "Beer")
    val tilt by infinite.animateFloat(
        initialValue = -24f, targetValue = -34f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOutSine), RepeatMode.Reverse), label = "t"
    )
    val froth by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(550, easing = EaseInOutSine), RepeatMode.Reverse), label = "f"
    )
    val bubbles by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "b"
    )
    val gulpPulse by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = EaseInOutSine), RepeatMode.Reverse), label = "g"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX + geo.eyeDiameter * 0.48f
        val cy = geo.mouthY + 8.dp.toPx()
        val w = 64.dp.toPx()
        val h = 80.dp.toPx()

        // ── Foam mustache at the mouth corner ──
        drawCircle(Color.White, 3.5.dp.toPx(), Offset(geo.cX + 12.dp.toPx(), geo.mouthY - 2.dp.toPx()))
        drawCircle(Color.White, 2.5.dp.toPx(), Offset(geo.cX + 18.dp.toPx(), geo.mouthY))

        // ── Throat pulse below mouth ──
        val pulseY = geo.mouthY + 16.dp.toPx() + gulpPulse * 5.dp.toPx()
        drawCircle(Color(0xFF00E5FF).copy(alpha = 0.35f * gulpPulse), 5.5.dp.toPx(), Offset(geo.cX, pulseY))

        rotate(degrees = tilt, pivot = Offset(cx, cy)) {
            val amber = Color(0xFFFFB300)

            // Handle
            drawArc(Color(0xFFFFD54F), 270f, 180f, false, topLeft = Offset(cx + w * 0.38f, cy - h * 0.3f), size = Size(24.dp.toPx(), h * 0.6f), style = Stroke(7.dp.toPx()))

            // Robot Paw gripping handle
            drawRobotPaw(Offset(cx + w * 0.54f, cy), radius = w * 0.17f, angleDeg = -90f)

            // Glass Mug
            drawRoundRect(amber, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h), cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha = 0.35f), topLeft = Offset(cx - w * 0.45f, cy - h * 0.45f), size = Size(w * 0.2f, h * 0.9f), cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()))

            // Rising Bubbles
            for (i in 0 until 4) {
                val bp = (bubbles + i * 0.25f) % 1f
                val bx = cx - w * 0.25f + i * (w * 0.18f)
                val by = (cy + h * 0.35f) - bp * (h * 0.7f)
                drawCircle(Color.White.copy(alpha = 0.85f), radius = 2.5.dp.toPx(), center = Offset(bx, by))
            }

            // Foaming Head Spill
        val foamH = 22.dp.toPx() * froth
            drawRoundRect(Color.White, topLeft = Offset(cx - w * 0.56f, cy - h * 0.5f - foamH * 0.65f), size = Size(w * 1.15f, foamH), cornerRadius = CornerRadius(foamH * 0.5f, foamH * 0.5f))
            drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(cx - w * 0.35f, cy - h * 0.42f))
        }
    }
}

@Composable
private fun PizzaProp() {
    val infinite = rememberInfiniteTransition(label = "Pizza")
    val tilt by infinite.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse), label = "t"
    )
    val cheeseDrip by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "d"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.mouthY + 12.dp.toPx()
        val r = 56.dp.toPx()

        rotate(degrees = tilt, pivot = Offset(cx, cy)) {
            // 1. Crust back
            drawArc(
                color = Color(0xFFD78B2A),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.85f, cy - r * 0.95f),
                size = Size(r * 1.7f, r * 0.6f),
                style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
            )

            // 2. Pizza Triangle Slice
            val slice = Path().apply {
                moveTo(cx, cy + r * 0.75f) // Tip pointing down
                lineTo(cx - r * 0.70f, cy - r * 0.55f)
                quadraticTo(cx, cy - r * 0.75f, cx + r * 0.70f, cy - r * 0.55f)
                close()
            }
            drawPath(
                slice,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFC107), Color(0xFFFFD54F), Color(0xFFFFE082)),
                    startY = cy - r * 0.75f,
                    endY = cy + r * 0.75f
                )
            )

            // 3. Pepperoni slices
            val pCol = Color(0xFFD32F2F)
            drawCircle(pCol, radius = 8.dp.toPx(), center = Offset(cx - 12.dp.toPx(), cy - 10.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.4f), radius = 2.5.dp.toPx(), center = Offset(cx - 14.dp.toPx(), cy - 12.dp.toPx()))
            drawCircle(pCol, radius = 7.5.dp.toPx(), center = Offset(cx + 14.dp.toPx(), cy - 4.dp.toPx()))
            drawCircle(pCol, radius = 6.dp.toPx(), center = Offset(cx - 2.dp.toPx(), cy + 12.dp.toPx()))

            // 4. Stretchy Gooey Cheese Drip from tip
            val dripY = cy + r * 0.75f + cheeseDrip * 16.dp.toPx()
            drawLine(Color(0xFFFFD54F), Offset(cx, cy + r * 0.75f), Offset(cx - 2.dp.toPx(), dripY), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color(0xFFFFD54F), radius = 3.5.dp.toPx() * (1f - cheeseDrip * 0.4f), center = Offset(cx - 2.dp.toPx(), dripY))
        }
    }
}

@Composable
private fun BurgerProp() {
    val infinite = rememberInfiniteTransition(label = "Burger")
    val chewY by infinite.animateFloat(
        initialValue = -4.dp.value, targetValue = 4.dp.value,
        animationSpec = infiniteRepeatable(tween(420, easing = EaseInOutSine), RepeatMode.Reverse), label = "c"
    )
    val steam by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "s"
    )
    val chewProg by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(840, easing = LinearEasing)), label = "cp"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.mouthY + 16.dp.toPx() + chewY
        val w = 84.dp.toPx()
        val h = 60.dp.toPx()

        // ── Cheeks rosy blush ──
        val cheekPulse = sin(chewProg * TWO_PI_F) * 0.15f
        val cheekR = 14.dp.toPx() * (1f + cheekPulse)
        drawCircle(Color(0xFFFF80AB).copy(alpha = 0.55f), cheekR, Offset(cx - geo.eyeDiameter * 0.85f, geo.mouthY - 10.dp.toPx()))
        drawCircle(Color(0xFFFF80AB).copy(alpha = 0.55f), cheekR, Offset(cx + geo.eyeDiameter * 0.85f, geo.mouthY - 10.dp.toPx()))

        // ── Robot Paws holding the burger ──
        val pawR = w * 0.16f
        drawRobotPaw(Offset(cx - w * 0.48f, cy + h * 0.10f), pawR, angleDeg = 65f)
        drawRobotPaw(Offset(cx + w * 0.48f, cy + h * 0.10f), pawR, angleDeg = -65f)

        val bunCol = Color(0xFFFFB300)
        val pattyCol = Color(0xFF4E342E)
        val lettuceCol = Color(0xFF4CAF50)
        val cheeseCol = Color(0xFFFFC107)
        val tomatoCol = Color(0xFFF44336)

        // 1. Top Bun
        drawArc(bunCol, 180f, 180f, true, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h * 0.6f))
        // Sesame Seeds
        val seedOffsets = floatArrayOf(-0.25f, -0.05f, 0.20f, -0.15f, 0.10f)
        for (i in seedOffsets.indices) {
            val sx = cx + w * seedOffsets[i]
            val sy = cy - h * 0.35f + abs(seedOffsets[i]) * 8.dp.toPx()
            drawOval(Color(0xFFFFF9C4), topLeft = Offset(sx - 2.dp.toPx(), sy - 1.dp.toPx()), size = Size(4.5.dp.toPx(), 2.5.dp.toPx()))
        }

        // 2. Tomato slices
        drawRoundRect(tomatoCol, topLeft = Offset(cx - w * 0.44f, cy - h * 0.08f), size = Size(w * 0.88f, 6.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()))

        // 3. Wavy Ruffled Lettuce
        drawRoundRect(lettuceCol, topLeft = Offset(cx - w * 0.52f, cy - h * 0.02f), size = Size(w * 1.04f, 7.dp.toPx()), cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()))

        // 4. Melted Cheese Slice (dripping points)
        val cheesePath = Path().apply {
            moveTo(cx - w * 0.46f, cy + h * 0.05f)
            lineTo(cx + w * 0.46f, cy + h * 0.05f)
            lineTo(cx + w * 0.25f, cy + h * 0.22f) // drip
            lineTo(cx, cy + h * 0.08f)
            lineTo(cx - w * 0.25f, cy + h * 0.24f) // drip
            close()
        }
        drawPath(cheesePath, cheeseCol)

        // 5. Juicy Grilled Patty
        drawRoundRect(pattyCol, topLeft = Offset(cx - w * 0.48f, cy + h * 0.10f), size = Size(w * 0.96f, 13.dp.toPx()), cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()))

        // 6. Bottom Toasted Bun
        drawRoundRect(bunCol, topLeft = Offset(cx - w * 0.46f, cy + h * 0.24f), size = Size(w * 0.92f, 12.dp.toPx()), cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()))

        // 7. Bite Mark Cutout
        drawCircle(Color.Black, radius = w * 0.14f, center = Offset(cx + w * 0.28f, cy - h * 0.26f))

        // 8. Falling Golden Bread Crumbs
        if (chewProg in 0.15f..0.75f) {
            val cp = ((chewProg - 0.15f) / 0.60f).coerceIn(0f, 1f)
            val cOffsets = floatArrayOf(-0.2f, 0.1f, 0.25f)
            for (i in cOffsets.indices) {
                val crX = cx + w * cOffsets[i]
                val crY = cy + h * 0.35f + cp * (22.dp.toPx() + i * 4.dp.toPx())
                drawCircle(Color(0xFFFFD54F).copy(alpha = 1f - cp), radius = 2.dp.toPx(), center = Offset(crX, crY))
            }
        }

        // 9. Warm Steam Wisps rising from fresh burger
        val sy = cy - h * 0.5f - steam * 24.dp.toPx()
        val alpha = (1f - steam).coerceIn(0f, 0.75f)
        drawLine(Color.White.copy(alpha = alpha), Offset(cx - 8.dp.toPx(), sy), Offset(cx - 6.dp.toPx(), sy - 12.dp.toPx()), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = alpha), Offset(cx + 8.dp.toPx(), sy + 4.dp.toPx()), Offset(cx + 10.dp.toPx(), sy - 8.dp.toPx()), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun CakeProp() {
    val infinite = rememberInfiniteTransition(label = "Cake")
    val flame by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(220, easing = EaseInOutSine), RepeatMode.Reverse), label = "f"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.mouthY + 12.dp.toPx()
        val w = 84.dp.toPx()
        val h = 52.dp.toPx()

        val pink = Color(0xFFF48FB1)
        val cream = Color(0xFFFFF9C4)

        // Cake Base Layer
        drawRoundRect(pink, topLeft = Offset(cx - w * 0.5f, cy - h * 0.4f), size = Size(w, h * 0.8f), cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()))
        // White Cream Layer
        drawRect(cream, topLeft = Offset(cx - w * 0.5f, cy - h * 0.05f), size = Size(w, 6.dp.toPx()))
        // Scalloped Frosting Drips along top
        for (i in -3..3) {
            drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(cx + i * (w * 0.15f), cy - h * 0.4f))
        }

        // Candle
        val candleW = 6.dp.toPx()
        val candleH = 18.dp.toPx()
        drawRoundRect(Color(0xFF00E5FF), topLeft = Offset(cx - candleW * 0.5f, cy - h * 0.4f - candleH), size = Size(candleW, candleH), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))

        // Dancing Candle Flame with Warm Halo
        val flameH = 10.dp.toPx() * flame
        drawCircle(Color(0xFFFFD600).copy(alpha = 0.35f), radius = 12.dp.toPx() * flame, center = Offset(cx, cy - h * 0.4f - candleH - flameH * 0.5f))
        drawOval(Color(0xFFFF9100), topLeft = Offset(cx - 3.5.dp.toPx(), cy - h * 0.4f - candleH - flameH), size = Size(7.dp.toPx(), flameH))
        drawCircle(Color(0xFFFFF59D), radius = 2.5.dp.toPx(), center = Offset(cx, cy - h * 0.4f - candleH - flameH * 0.35f))
    }
}

@Composable
private fun IceCreamProp() {
    val infinite = rememberInfiniteTransition(label = "IceCream")
    val hover by infinite.animateFloat(
        initialValue = -3.dp.value, targetValue = 3.dp.value,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutSine), RepeatMode.Reverse), label = "h"
    )
    val dripProg by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "d"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.mouthY + 8.dp.toPx() + hover
        val r = 26.dp.toPx()

        val coneCol = Color(0xFFFFB74D)
        val scoop1 = Color(0xFF80CBC4) // Mint scoop
        val scoop2 = Color(0xFFFF4081) // Strawberry scoop

        // Bottom Cone
        val cone = Path().apply {
            moveTo(cx - r * 0.85f, cy + r * 0.4f)
            lineTo(cx + r * 0.85f, cy + r * 0.4f)
            lineTo(cx, cy + r * 2.8f) // cone tip
            close()
        }
        drawPath(cone, coneCol)
        // Waffle cross-hatch lines
        drawLine(Color(0xFFE65100).copy(alpha = 0.4f), Offset(cx - r * 0.6f, cy + r * 0.9f), Offset(cx + r * 0.3f, cy + r * 2.1f), strokeWidth = 1.5.dp.toPx())
        drawLine(Color(0xFFE65100).copy(alpha = 0.4f), Offset(cx + r * 0.6f, cy + r * 0.9f), Offset(cx - r * 0.3f, cy + r * 2.1f), strokeWidth = 1.5.dp.toPx())

        // Bottom Mint Scoop
        drawCircle(scoop1, radius = r * 0.95f, center = Offset(cx, cy + r * 0.3f))
        // Top Strawberry Scoop
        drawCircle(scoop2, radius = r, center = Offset(cx, cy - r * 0.5f))

        // Red Cherry on top
        drawCircle(Color(0xFFD50000), radius = 7.dp.toPx(), center = Offset(cx + 4.dp.toPx(), cy - r * 1.5f))
        drawLine(Color(0xFF5D4037), Offset(cx + 4.dp.toPx(), cy - r * 1.5f), Offset(cx + 10.dp.toPx(), cy - r * 2.0f), strokeWidth = 2.dp.toPx())

        // Melting Ice Cream Drip
        val dropY = cy + r * 0.4f + dripProg * 28.dp.toPx()
        drawCircle(scoop1, radius = 3.dp.toPx() * (1f - dripProg * 0.3f), center = Offset(cx - r * 0.65f, dropY))
    }
}

@Composable
private fun PopcornProp() {
    val infinite = rememberInfiniteTransition(label = "Popcorn")
    val shake by infinite.animateFloat(
        initialValue = -2.5.dp.value, targetValue = 2.5.dp.value,
        animationSpec = infiniteRepeatable(tween(350, easing = EaseInOutSine), RepeatMode.Reverse), label = "s"
    )
    val popKernel by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing)), label = "p"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX + geo.eyeDiameter * 0.40f + shake
        val cy = geo.mouthY + 16.dp.toPx()
        val w = 64.dp.toPx()
        val h = 74.dp.toPx()

        // Robotic paw holding bucket
        drawRobotPaw(Offset(cx - w * 0.36f, cy + h * 0.48f), radius = w * 0.18f, angleDeg = 35f)

        // Red & White Striped Bucket
        val bucket = Path().apply {
            moveTo(cx - w * 0.5f, cy - h * 0.4f)
            lineTo(cx + w * 0.5f, cy - h * 0.4f)
            lineTo(cx + w * 0.35f, cy + h * 0.5f)
            lineTo(cx - w * 0.35f, cy + h * 0.5f)
            close()
        }
        drawPath(bucket, Color(0xFFD32F2F))
        // White stripes
        drawLine(Color.White, Offset(cx - w * 0.18f, cy - h * 0.4f), Offset(cx - w * 0.12f, cy + h * 0.5f), strokeWidth = 8.dp.toPx())
        drawLine(Color.White, Offset(cx + w * 0.18f, cy - h * 0.4f), Offset(cx + w * 0.12f, cy + h * 0.5f), strokeWidth = 8.dp.toPx())

        // Top rim gold band
        drawRoundRect(Color(0xFFFFD54F), topLeft = Offset(cx - w * 0.52f, cy - h * 0.42f), size = Size(w * 1.04f, 5.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))

        // Overflowing Popcorn Kernels atop bucket
        val popCol = Color(0xFFFFF59D)
        val popCornOffsets = floatArrayOf(-0.35f, -0.15f, 0.05f, 0.25f, -0.05f)
        for (i in popCornOffsets.indices) {
            val px = cx + w * popCornOffsets[i]
            val py = cy - h * 0.42f - abs(popCornOffsets[i]) * 6.dp.toPx()
            drawCircle(popCol, radius = 9.dp.toPx(), center = Offset(px, py))
            drawCircle(Color(0xFFFFEE58), radius = 6.5.dp.toPx(), center = Offset(px, py))
        }

        // Active Popping Kernels jumping in parabolic arc into mouth!
        val t = popKernel
        val startX = cx - w * 0.10f
        val startY = cy - h * 0.45f
        val arcX = startX + (geo.cX - startX) * t
        val arcY = startY + (geo.mouthY - startY) * t - sin(t * PI_F) * 32.dp.toPx()
        drawCircle(popCol, radius = (5.5f - t * 1.5f).dp.toPx(), center = Offset(arcX, arcY))
        drawCircle(Color(0xFFFFEE58), radius = (3.5f - t * 1f).dp.toPx(), center = Offset(arcX, arcY))
    }
}

@Composable
private fun BobaTeaProp() {
    val infinite = rememberInfiniteTransition(label = "Boba")
    val bob by infinite.animateFloat(
        initialValue = -3.dp.value, targetValue = 3.dp.value,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse), label = "b"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX + geo.eyeDiameter * 0.45f
        val cy = geo.mouthY + 8.dp.toPx() + bob
        val w = 52.dp.toPx()
        val h = 72.dp.toPx()

        val teaCol = Color(0xFFD7CCC8)
        val pearlCol = Color(0xFF212121)

        // Clear Cup
        drawRoundRect(teaCol, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h), cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()))

        // Dome Lid
        drawArc(Color.White.copy(alpha = 0.6f), 180f, 180f, true, topLeft = Offset(cx - w * 0.52f, cy - h * 0.62f), size = Size(w * 1.04f, 18.dp.toPx()))

        // Fat Straw
        drawLine(Color(0xFFFF4081), Offset(cx, cy - h * 0.3f), Offset(cx + 16.dp.toPx(), cy - h * 0.85f), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)

        // Boba Pearls at bottom
        for (r in 0..1) {
            for (c in -1..1) {
                drawCircle(pearlCol, radius = 5.dp.toPx(), center = Offset(cx + c * 13.dp.toPx() + r * 6.dp.toPx(), cy + h * (0.28f + r * 0.12f)))
            }
        }
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
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.leftTempleX - 12.dp.toPx()
        val cy = geo.mouthY + 12.dp.toPx() + bounce
        val boxCol = Color(0xFF00E5FF)
        val ribbonCol = Color(0xFFFF1744)
        val s = 62.dp.toPx()

        drawRoundRect(boxCol, topLeft = Offset(cx - s * 0.5f, cy - s * 0.5f), size = Size(s, s), cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()))
        drawRect(ribbonCol, topLeft = Offset(cx - 5.dp.toPx(), cy - s * 0.5f), size = Size(10.dp.toPx(), s))
        drawRect(ribbonCol, topLeft = Offset(cx - s * 0.5f, cy - 5.dp.toPx()), size = Size(s, 10.dp.toPx()))

        // Bow loops on top
        drawCircle(ribbonCol, radius = 7.dp.toPx(), center = Offset(cx - 8.dp.toPx(), cy - s * 0.5f - 4.dp.toPx()))
        drawCircle(ribbonCol, radius = 7.dp.toPx(), center = Offset(cx + 8.dp.toPx(), cy - s * 0.5f - 4.dp.toPx()))
    }
}

@Composable
private fun GamingControllerProp() {
    val infinite = rememberInfiniteTransition(label = "Gamepad")
    val bob by infinite.animateFloat(
        initialValue = -3.dp.value, targetValue = 3.dp.value,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse), label = "b"
    )
    val ledPulse by infinite.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse), label = "led"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.mouthY + 18.dp.toPx() + bob
        val w = 94.dp.toPx()
        val h = 56.dp.toPx()

        val bodyCol = Color(0xFF263238)
        val accentCol = Color(0xFF00E5FF)

        // Controller Body (Ergonomic twin grip)
        drawRoundRect(bodyCol, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h), cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()))

        // Central Breathing LED Lightbar
        drawRoundRect(accentCol.copy(alpha = ledPulse), topLeft = Offset(cx - 16.dp.toPx(), cy - h * 0.42f), size = Size(32.dp.toPx(), 5.dp.toPx()), cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()))

        // D-Pad on Left
        val dpadX = cx - w * 0.28f
        val dpadY = cy + 2.dp.toPx()
        drawRect(Color(0xFF78909C), topLeft = Offset(dpadX - 10.dp.toPx(), dpadY - 3.5.dp.toPx()), size = Size(20.dp.toPx(), 7.dp.toPx()))
        drawRect(Color(0xFF78909C), topLeft = Offset(dpadX - 3.5.dp.toPx(), dpadY - 10.dp.toPx()), size = Size(7.dp.toPx(), 20.dp.toPx()))

        // Action Buttons on Right (A, B, X, Y)
        val btnX = cx + w * 0.28f
        val btnY = cy + 2.dp.toPx()
        drawCircle(Color(0xFF00E676), radius = 4.dp.toPx(), center = Offset(btnX, btnY + 7.dp.toPx())) // A green
        drawCircle(Color(0xFFFF1744), radius = 4.dp.toPx(), center = Offset(btnX + 7.dp.toPx(), btnY)) // B red
        drawCircle(Color(0xFF2979FF), radius = 4.dp.toPx(), center = Offset(btnX - 7.dp.toPx(), btnY)) // X blue
        drawCircle(Color(0xFFFFEA00), radius = 4.dp.toPx(), center = Offset(btnX, btnY - 7.dp.toPx())) // Y yellow

        // Dual Thumbsticks
        drawCircle(Color(0xFF37474F), radius = 9.dp.toPx(), center = Offset(cx - 14.dp.toPx(), cy + 12.dp.toPx()))
        drawCircle(Color(0xFF546E7A), radius = 6.dp.toPx(), center = Offset(cx - 14.dp.toPx(), cy + 12.dp.toPx()))
        drawCircle(Color(0xFF37474F), radius = 9.dp.toPx(), center = Offset(cx + 14.dp.toPx(), cy + 12.dp.toPx()))
        drawCircle(Color(0xFF546E7A), radius = 6.dp.toPx(), center = Offset(cx + 14.dp.toPx(), cy + 12.dp.toPx()))
    }
}

@Composable
private fun BookProp() {
    val infinite = rememberInfiniteTransition(label = "Book")
    val flutter by infinite.animateFloat(
        initialValue = -2.dp.value, targetValue = 2.dp.value,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse), label = "f"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.mouthY + 16.dp.toPx()
        val pageCol = Color(0xFFFFFDE7)
        val coverCol = Color(0xFF1565C0)
        val w = 88.dp.toPx()
        val h = 52.dp.toPx()

        // Outer Hardcover
        drawRoundRect(coverCol, topLeft = Offset(cx - w * 0.52f, cy - h * 0.48f), size = Size(w * 1.04f, h * 1.04f), cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()))

        // Left Page
        val leftPage = Path().apply {
            moveTo(cx - 2.dp.toPx(), cy - h * 0.42f)
            lineTo(cx - w * 0.48f, cy - h * 0.42f)
            lineTo(cx - w * 0.48f, cy + h * 0.42f)
            lineTo(cx - 2.dp.toPx(), cy + h * 0.42f)
            close()
        }
        drawPath(leftPage, pageCol)

        // Right Page with fluttering corner
        val rightPage = Path().apply {
            moveTo(cx + 2.dp.toPx(), cy - h * 0.42f)
            lineTo(cx + w * 0.48f, cy - h * 0.42f + flutter)
            lineTo(cx + w * 0.48f, cy + h * 0.42f)
            lineTo(cx + 2.dp.toPx(), cy + h * 0.42f)
            close()
        }
        drawPath(rightPage, pageCol)

        // Faux Text Lines on Pages
        for (line in 0..3) {
            val ly = cy - h * 0.28f + line * 9.dp.toPx()
            drawLine(Color(0xFF90A4AE), Offset(cx - w * 0.42f, ly), Offset(cx - 8.dp.toPx(), ly), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color(0xFF90A4AE), Offset(cx + 8.dp.toPx(), ly), Offset(cx + w * 0.42f, ly), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }

        // Red Ribbon Bookmark
        val ribbon = Path().apply {
            moveTo(cx - 2.dp.toPx(), cy - h * 0.42f)
            lineTo(cx + 2.dp.toPx(), cy - h * 0.42f)
            lineTo(cx + 4.dp.toPx(), cy + h * 0.55f)
            lineTo(cx, cy + h * 0.48f)
            lineTo(cx - 4.dp.toPx(), cy + h * 0.55f)
            close()
        }
        drawPath(ribbon, Color(0xFFFF1744))
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
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.foreheadY - 14.dp.toPx()
        val colors = listOf(Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00), Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFF651FFF))
        val baseR = geo.eyeDiameter * 0.85f

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
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.rightTempleX + 26.dp.toPx()
        val cy = geo.foreheadY + floatY
        val ghostCol = Color(0xEEFFFFFF)
        val w = 54.dp.toPx()
        val h = 66.dp.toPx()

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
        // Cute black oval eyes & pink blush
        drawOval(Color.Black, topLeft = Offset(cx - 12.dp.toPx(), cy - 6.dp.toPx()), size = Size(6.dp.toPx(), 8.dp.toPx()))
        drawOval(Color.Black, topLeft = Offset(cx + 6.dp.toPx(), cy - 6.dp.toPx()), size = Size(6.dp.toPx(), 8.dp.toPx()))
        drawCircle(Color(0xFFFF80AB).copy(alpha = 0.6f), radius = 4.dp.toPx(), center = Offset(cx - 14.dp.toPx(), cy + 4.dp.toPx()))
        drawCircle(Color(0xFFFF80AB).copy(alpha = 0.6f), radius = 4.dp.toPx(), center = Offset(cx + 14.dp.toPx(), cy + 4.dp.toPx()))
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
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.rightTempleX + 28.dp.toPx()
        val cy = geo.mouthY + 20.dp.toPx() - blast * (geo.eyeDiameter * 1.8f)
        val bodyCol = Color(0xFFECEFF1)
        val red = Color(0xFFFF1744)
        val w = 44.dp.toPx()
        val h = 68.dp.toPx()

        val rocket = Path().apply {
            moveTo(cx, cy - h * 0.6f)
            cubicTo(cx + w * 0.5f, cy - h * 0.2f, cx + w * 0.5f, cy + h * 0.4f, cx + w * 0.4f, cy + h * 0.5f)
            lineTo(cx - w * 0.4f, cy + h * 0.5f)
            cubicTo(cx - w * 0.5f, cy + h * 0.4f, cx - w * 0.5f, cy - h * 0.2f, cx, cy - h * 0.6f)
            close()
        }
        drawPath(rocket, bodyCol)
        drawCircle(Color(0xFF00E5FF), radius = 8.dp.toPx(), center = Offset(cx, cy - h * 0.1f))
        drawRect(red, topLeft = Offset(cx - w * 0.6f, cy + h * 0.25f), size = Size(w * 0.22f, h * 0.28f))
        drawRect(red, topLeft = Offset(cx + w * 0.38f, cy + h * 0.25f), size = Size(w * 0.22f, h * 0.28f))

        // Exhaust Fire & smoke
        val fireH = (14.dp.toPx() + (blast * 10.dp.toPx()))
        val flame = Path().apply {
            moveTo(cx - 6.dp.toPx(), cy + h * 0.5f)
            lineTo(cx, cy + h * 0.5f + fireH)
            lineTo(cx + 6.dp.toPx(), cy + h * 0.5f)
            close()
        }
        drawPath(flame, Color(0xFFFF9100))
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
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.rightTempleX + 22.dp.toPx()
        val cy = geo.foreheadY
        val gearCol = Color(0xFF78909C)
        val goldCol = Color(0xFFFFB300)
        val r1 = 30.dp.toPx()

        // Large Gear
        drawCircle(gearCol, radius = r1 * 0.72f, center = Offset(cx, cy))
        drawCircle(Color.Black, radius = r1 * 0.28f, center = Offset(cx, cy))
        for (i in 0 until 8) {
            val angle = (rot + i * 45f) * PI_F / 180f
            val gx = cx + r1 * cos(angle)
            val gy = cy + r1 * sin(angle)
            drawCircle(gearCol, radius = 6.dp.toPx(), center = Offset(gx, gy))
        }

        // Small Counter-rotating Gear
        val cx2 = cx - 22.dp.toPx()
        val cy2 = cy + 26.dp.toPx()
        val r2 = 20.dp.toPx()
        drawCircle(goldCol, radius = r2 * 0.72f, center = Offset(cx2, cy2))
        drawCircle(Color.Black, radius = r2 * 0.28f, center = Offset(cx2, cy2))
        for (i in 0 until 6) {
            val angle = (-rot * 1.5f + i * 60f) * PI_F / 180f
            val gx = cx2 + r2 * cos(angle)
            val gy = cy2 + r2 * sin(angle)
            drawCircle(goldCol, radius = 4.5.dp.toPx(), center = Offset(gx, gy))
        }
    }
}

@Composable
private fun LaptopProp() {
    val infinite = rememberInfiniteTransition(label = "Laptop")
    val screenPulse by infinite.animateFloat(
        initialValue = 0.8f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse), label = "p"
    )
    val scanY by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "s"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.mouthY + 20.dp.toPx()
        val w = 105.dp.toPx()
        val h = 62.dp.toPx()

        // 1. Laptop Screen Lid (Dark Titanium metallic)
        drawRoundRect(
            Color(0xFF37474F),
            topLeft = Offset(cx - w * 0.46f, cy - h),
            size = Size(w * 0.92f, h),
            cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
        )

        // 2. High-res Cyber Screen
        val screenW = w * 0.84f
        val screenH = h * 0.82f
        val screenLeft = cx - screenW * 0.5f
        val screenTop = cy - h + 5.dp.toPx()
        drawRect(Color(0xFF001E28), topLeft = Offset(screenLeft, screenTop), size = Size(screenW, screenH))

        // Screen Glow & Terminal Code Lines
        val cyan = Color(0xFF00F0FF).copy(alpha = screenPulse)
        for (line in 0..4) {
            val ly = screenTop + 8.dp.toPx() + line * 7.dp.toPx()
            val lineLen = screenW * (0.35f + ((line * 17) % 55) / 100f)
            drawLine(cyan.copy(alpha = 0.65f), Offset(screenLeft + 8.dp.toPx(), ly), Offset(screenLeft + 8.dp.toPx() + lineLen, ly), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        }

        // Scanning Matrix Light Bar
        val curScan = screenTop + scanY * screenH
        drawLine(Color(0xFF00E676).copy(alpha = 0.45f), Offset(screenLeft + 4.dp.toPx(), curScan), Offset(screenLeft + screenW - 4.dp.toPx(), curScan), strokeWidth = 2.dp.toPx())

        // Webcam dot
        drawCircle(Color(0xFF00E5FF), radius = 1.5.dp.toPx(), center = Offset(cx, cy - h + 2.5.dp.toPx()))

        // 3. Laptop Keyboard Base (Slanted perspective)
        val baseH = 12.dp.toPx()
        val baseW = w
        drawRoundRect(
            Color(0xFF263238),
            topLeft = Offset(cx - baseW * 0.5f, cy),
            size = Size(baseW, baseH),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        // Trackpad & edge light
        drawRoundRect(
            Color(0xFF455A64),
            topLeft = Offset(cx - 14.dp.toPx(), cy + 3.dp.toPx()),
            size = Size(28.dp.toPx(), 6.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        drawLine(Color(0xFF00E5FF).copy(alpha = 0.7f), Offset(cx - baseW * 0.45f, cy), Offset(cx + baseW * 0.45f, cy), strokeWidth = 1.5.dp.toPx())
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
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.rightTempleX + 18.dp.toPx()
        val cy = geo.foreheadY - 8.dp.toPx()
        val green = Color(0xFF00E676)
        val w = 58.dp.toPx()
        val h = 30.dp.toPx()

        drawRoundRect(green, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h), cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()), style = Stroke(3.dp.toPx()))
        drawRect(green, topLeft = Offset(cx + w * 0.5f, cy - 4.5.dp.toPx()), size = Size(4.5.dp.toPx(), 9.dp.toPx()))

        // 3 charging bars
        for (b in 0..2) {
            val bx = cx - w * 0.42f + b * (w * 0.26f)
            drawRoundRect(green.copy(alpha = 0.85f), topLeft = Offset(bx, cy - h * 0.32f), size = Size(w * 0.20f, h * 0.64f), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))
        }

        val bolt = Path().apply {
            moveTo(cx + 3.dp.toPx(), cy - 9.dp.toPx())
            lineTo(cx - 7.dp.toPx(), cy + 2.dp.toPx())
            lineTo(cx, cy + 2.dp.toPx())
            lineTo(cx - 3.dp.toPx(), cy + 9.dp.toPx())
            lineTo(cx + 7.dp.toPx(), cy - 2.dp.toPx())
            lineTo(cx, cy - 2.dp.toPx())
            close()
        }
        drawPath(bolt, Color(0xFFFFF176).copy(alpha = pulse))
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
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.rightTempleX + 18.dp.toPx()
        val cy = geo.foreheadY - 8.dp.toPx()
        val red = Color(0xFFFF1744)
        val w = 58.dp.toPx()
        val h = 30.dp.toPx()

        drawRoundRect(red, topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f), size = Size(w, h), cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()), style = Stroke(3.dp.toPx()))
        drawRect(red, topLeft = Offset(cx + w * 0.5f, cy - 4.5.dp.toPx()), size = Size(4.5.dp.toPx(), 9.dp.toPx()))
        drawRect(red.copy(alpha = blink), topLeft = Offset(cx - w * 0.44f, cy - h * 0.35f), size = Size(w * 0.24f, h * 0.7f))
    }
}

@Composable
private fun ClockAlarmProp() {
    val shake by rememberInfiniteTransition(label = "Clock").animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(80, easing = LinearEasing), RepeatMode.Reverse), label = "s"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.rightTempleX + 22.dp.toPx()
        val cy = geo.foreheadY - 10.dp.toPx()
        val r = 30.dp.toPx()
        val orange = Color(0xFFFF9100)

        // Bells vibrating on top
        drawCircle(orange, radius = 9.dp.toPx(), center = Offset(cx - r * 0.8f + shake, cy - r * 0.8f))
        drawCircle(orange, radius = 9.dp.toPx(), center = Offset(cx + r * 0.8f - shake, cy - r * 0.8f))

        // Sound waves radiating out
        drawArc(orange.copy(alpha = 0.5f), -135f, 60f, false, topLeft = Offset(cx - r * 1.5f + shake, cy - r * 1.5f), size = Size(r * 3f, r * 3f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawArc(orange.copy(alpha = 0.5f), -105f, 60f, false, topLeft = Offset(cx - r * 1.5f - shake, cy - r * 1.5f), size = Size(r * 3f, r * 3f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

        // Dial
        drawCircle(orange, radius = r, center = Offset(cx, cy))
        drawCircle(Color.White, radius = r * 0.82f, center = Offset(cx, cy))
        drawLine(Color.Black, Offset(cx, cy), Offset(cx, cy - r * 0.55f), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.Black, Offset(cx, cy), Offset(cx + r * 0.45f, cy), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun MagnifyingGlassProp() {
    val scan by rememberInfiniteTransition(label = "Scan").animateFloat(
        initialValue = -25.dp.value, targetValue = 25.dp.value,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse), label = "s"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX + scan
        val cy = geo.cY
        val r = 38.dp.toPx()
        val cyan = Color(0xFF00E5FF)

        // Glass reflection sheen
        drawCircle(Color(0x2200E5FF), radius = r, center = Offset(cx, cy))
        drawCircle(cyan, radius = r, center = Offset(cx, cy), style = Stroke(6.dp.toPx()))

        // Specular glare arc
        drawArc(Color.White.copy(alpha = 0.6f), 200f, 60f, false, topLeft = Offset(cx - r * 0.82f, cy - r * 0.82f), size = Size(r * 1.64f, r * 1.64f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))

        // Sturdy handle
        drawLine(Color(0xFF90A4AE), Offset(cx + r * 0.7f, cy + r * 0.7f), Offset(cx + r * 1.6f, cy + r * 1.6f), strokeWidth = 9.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun ShieldProp() {
    val aura by rememberInfiniteTransition(label = "Shield").animateFloat(
        initialValue = 0.4f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse), label = "a"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.foreheadY - 12.dp.toPx()
        val blue = Color(0xFF1976D2)
        val gold = Color(0xFFFFD700)
        val w = 84.dp.toPx()
        val h = 92.dp.toPx()

        val shield = Path().apply {
            moveTo(cx, cy - h * 0.5f)
            lineTo(cx + w * 0.5f, cy - h * 0.35f)
            cubicTo(cx + w * 0.5f, cy + h * 0.2f, cx, cy + h * 0.5f, cx, cy + h * 0.5f)
            cubicTo(cx, cy + h * 0.5f, cx - w * 0.5f, cy + h * 0.2f, cx - w * 0.5f, cy - h * 0.35f)
            close()
        }

        // Defensive Pulse Halo
        drawPath(shield, blue.copy(alpha = aura * 0.35f), style = Stroke(10.dp.toPx()))
        // Shield Body with Gradient
        drawPath(
            shield,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF29B6F6), blue, Color(0xFF0D47A1)),
                startY = cy - h * 0.5f,
                endY = cy + h * 0.5f
            )
        )
        // Golden Heraldic Border
        drawPath(shield, gold, style = Stroke(4.dp.toPx()))

        // Central Star Emblem
        drawCircle(gold, radius = 10.dp.toPx(), center = Offset(cx, cy - h * 0.05f))
        drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(cx, cy - h * 0.05f))
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
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.foreheadY - 14.dp.toPx()
        val amber = Color(0xFFFFAB00)
        val s = 50.dp.toPx()

        val tri = Path().apply {
            moveTo(cx, cy - s)
            lineTo(cx + s, cy + s * 0.7f)
            lineTo(cx - s, cy + s * 0.7f)
            close()
        }
        drawPath(tri, amber.copy(alpha = blink * 0.3f), style = Stroke(8.dp.toPx()))
        drawPath(tri, amber.copy(alpha = blink))
        drawPath(tri, Color(0xFF212121), style = Stroke(3.5.dp.toPx()))

        // Exclamation inside
        drawLine(Color.Black, Offset(cx, cy - s * 0.35f), Offset(cx, cy + s * 0.2f), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.Black, radius = 3.5.dp.toPx(), center = Offset(cx, cy + s * 0.45f))
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
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.foreheadY - 10.dp.toPx()
        val red = Color(0xFFFF1744)
        val blue = Color(0xFF2979FF)
        val r = 34.dp.toPx()

        // Sweeping Dual Beacon Cones
        drawArc(red.copy(alpha = 0.4f), rot, 80f, true, topLeft = Offset(cx - r * 2.8f, cy - r * 2.8f), size = Size(r * 5.6f, r * 5.6f))
        drawArc(blue.copy(alpha = 0.4f), rot + 180f, 80f, true, topLeft = Offset(cx - r * 2.8f, cy - r * 2.8f), size = Size(r * 5.6f, r * 5.6f))

        // Siren Dome
        drawArc(red, 180f, 180f, true, topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f))
        // Metallic mount base
        drawRoundRect(Color(0xFF37474F), topLeft = Offset(cx - r * 1.15f, cy), size = Size(r * 2.3f, 13.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()))
    }
}

@Composable
private fun CheckMarkProp() {
    val bounce by rememberInfiniteTransition(label = "Check").animateFloat(
        initialValue = 0.9f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse), label = "b"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.foreheadY - 12.dp.toPx()
        val green = Color(0xFF00E676)
        val r = 40.dp.toPx() * bounce

        drawCircle(green.copy(alpha = 0.25f), radius = r * 1.4f, center = Offset(cx, cy))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF69F0AE), green, Color(0xFF00C853)),
                center = Offset(cx, cy),
                radius = r
            ),
            radius = r,
            center = Offset(cx, cy)
        )
        val check = Path().apply {
            moveTo(cx - r * 0.45f, cy)
            lineTo(cx - r * 0.1f, cy + r * 0.4f)
            lineTo(cx + r * 0.45f, cy - r * 0.35f)
        }
        drawPath(check, Color.White, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
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
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.cX
        val cy = geo.cY
        val cyan = Color(0xFF00F0FF)
        val r = 95.dp.toPx()

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
        initialValue = 0.85f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(400, easing = EaseInOutSine), RepeatMode.Reverse), label = "t"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geo = calculateGeometry(size.width, size.height)
        val cx = geo.rightTempleX + 22.dp.toPx()
        val cy = geo.mouthY + 12.dp.toPx()
        val pink = Color(0xFFFF80AB)
        val r = 26.dp.toPx() * tap

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


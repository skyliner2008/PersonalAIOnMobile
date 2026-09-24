package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * ═════════════════════════════════════════════════════════════════════════════
 * LOOI ROBOT: FLAT NEON-GLOW 2-LAYER DRAWING ENGINE
 * ═════════════════════════════════════════════════════════════════════════════
 *
 * Designed to faithfully reproduce the "Looi Robot: Neon Cyan Style Moodset":
 * - Pure OLED Black background (#000000)
 * - 2-Layer Neon Rendering:
 *     1. Glow Layer: Outer blurred bloom (15-20% larger, alpha ~0.4-0.6, accentColor)
 *     2. Core Layer: Solid crisp shape (alpha ~0.98, strokeCap = Round for lines, no gradient)
 * - Zero radial gradient / Zero specular highlight dot (flat neon-glow icon + motion)
 * - Accent colors:
 *     - Cyan (default): Color(0xFF00F5FF)
 *     - Red (Angry / Evil / Enraged): Color(0xFFFF3B5C)
 *     - Pink (Love / Romantic / Shy): Color(0xFFFF4081)
 *     - Yellow / Gold (Star / Excited / Magic): Color(0xFFFFD700)
 *     - Mint (Sick / Poison): Color(0xFF64FFDA)
 *     - Grey Blue (Bored / Sleepy): Color(0xFF90A4AE)
 */

val NeonCyan = Color(0xFF00F5FF)
val NeonRed = Color(0xFFFF3B5C)
val NeonPink = Color(0xFFFF4081)
val NeonGold = Color(0xFFFFD700)
val NeonMint = Color(0xFF64FFDA)
val NeonGrey = Color(0xFF90A4AE)

// ─── 1. Reusable 2-Layer Neon Drawing Functions ──────────────────────────────

/**
 * Soft OLED bloom halo (radial falloff) behind a glowing eye — matches the diffuse
 * neon glow of the LOOI 40 Moodset sheet, instead of hard concentric stroke rings.
 * @param spread how far the halo extends beyond the shape, as a fraction of its radius
 */
fun DrawScope.drawSoftBloom(
    color: Color,
    center: Offset,
    size: Size,
    spread: Float = 0.55f,
    intensity: Float = 0.30f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha * intensity).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f || size.width <= 0f || size.height <= 0f) return
    val baseR = maxOf(size.width, size.height) / 2f
    val haloR = baseR * (1f + spread)
    val brush = BloomBrushCache.get(color, effAlpha, spread)
    // The cached brush is drawn at a fixed reference radius around the origin and moved/scaled
    // into place, so the same brush (and its shader) is reused every frame.
    // Squashed (blinking) eyes get a squashed halo so the glow hugs the shape.
    val k = haloR / BloomBrushCache.REF_RADIUS
    val sx = (size.width / (baseR * 2f)).coerceAtLeast(0.3f) * k
    val sy = (size.height / (baseR * 2f)).coerceAtLeast(0.3f) * k
    translate(center.x, center.y) {
        scale(scaleX = sx, scaleY = sy, pivot = Offset.Zero) {
            drawCircle(brush = brush, radius = BloomBrushCache.REF_RADIUS, center = Offset.Zero)
        }
    }
}

/**
 * Small cache of bloom gradients keyed by (colour, alpha, spread). Alpha is quantised to 1/64
 * so a fading eye reuses a handful of brushes instead of allocating a new one each frame.
 */
private object BloomBrushCache {
    const val REF_RADIUS = 100f
    private const val MAX_ENTRIES = 48
    private val cache = LinkedHashMap<Long, Brush>(MAX_ENTRIES, 0.75f, true)

    fun get(color: Color, effAlpha: Float, spread: Float): Brush {
        val alphaStep = (effAlpha * 64f).roundToInt().coerceIn(1, 64)
        val spreadStep = (spread * 100f).roundToInt().coerceIn(0, 255)
        // sRGB Color packs ARGB in the upper 32 bits, so the lower bits are free for the rest of the key
        val key = color.copy(alpha = 1f).value.toLong() xor (alphaStep.toLong() shl 8) xor spreadStep.toLong()
        cache[key]?.let { return it }
        val a = alphaStep / 64f
        val coreStop = (1f / (1f + spread)).coerceIn(0.05f, 0.95f)
        val opaque = color.copy(alpha = 1f)
        val brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to opaque.copy(alpha = a),
                coreStop to opaque.copy(alpha = a * 0.55f),
                (coreStop + (1f - coreStop) * 0.35f) to opaque.copy(alpha = a * 0.14f),
                1f to Color.Transparent
            ),
            center = Offset.Zero,
            radius = REF_RADIUS
        )
        if (cache.size >= MAX_ENTRIES) cache.remove(cache.keys.first())
        cache[key] = brush
        return brush
    }
}

/**
 * คำนวณสีเงาเข้ม (Deep Contrast Shadow Color) สำหรับมิติทรงกลม 2.5D ของดวงตา
 * อิงตามสีน้ำเงินครามเข้ม/Indigo ในภาพผลิตภัณฑ์จริง LOOI Robot (โหมด Normal / Idle)
 */
fun getEyeDeepShadowColor(baseColor: Color): Color {
    val r = baseColor.red
    val g = baseColor.green
    val b = baseColor.blue

    return when {
        // Cyan / Sky Blue (Default / Happy / Reading) -> Deep Teal lower rim (LOOI 40 Moodset sheet #0A5C6B)
        b > 0.7f && g > 0.6f && r < 0.4f -> EyeMetrics.SHADOW_CYAN
        // Red / Crimson (Angry / Enraged / Evil) -> Deep Burgundy / Dark Maroon
        r > 0.7f && g < 0.4f && b < 0.5f -> EyeMetrics.SHADOW_RED
        // Pink (Love / Romantic / Shy) -> Deep Plum / Violet
        r > 0.7f && b > 0.4f && g < 0.5f -> Color(0xFF450624)
        // Yellow / Gold (Excited / Star) -> Deep Amber Bronze
        r > 0.7f && g > 0.6f && b < 0.3f -> Color(0xFF522800)
        // Mint / Green (Sick / Poison / Speaking) -> Deep Dark Teal / Forest Green
        g > 0.7f && r < 0.5f -> Color(0xFF003322)
        // Grey / Blue-Grey (Bored / Sleepy) -> Deep Slate
        else -> Color(0xFF141E28)
    }
}

/**
 * วาด Circle สไตล์ Neon 2 ชั้น (Glow layer + Core layer)
 */
fun DrawScope.drawNeonCircle(
    color: Color,
    center: Offset,
    radius: Float,
    glowRadiusDp: Dp = 8.dp,
    glowAlpha: Float = 0.5f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f || radius <= 0f) return
    val glowPad = glowRadiusDp.toPx()
    val glowColor = color.copy(alpha = (glowAlpha * effAlpha).coerceIn(0f, 1f))

    // Glow Layer
    val outerPad = glowPad * 1.4f
    drawCircle(
        color = glowColor.copy(alpha = glowColor.alpha * 0.35f),
        radius = radius + outerPad,
        center = center
    )
    drawCircle(
        color = glowColor,
        radius = radius + glowPad,
        center = center
    )

    // Core Layer
    drawCircle(
        color = color.copy(alpha = 0.98f * effAlpha),
        radius = radius,
        center = center
    )
}

/**
 * วาด Path/Stroke สไตล์ Neon 2 ชั้น (Glow layer + Core layer)
 */
fun DrawScope.drawNeonPath(
    path: Path,
    color: Color,
    strokeWidth: Float,
    glowRadiusDp: Dp = 8.dp,
    glowAlpha: Float = 0.5f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val glowPad = glowRadiusDp.toPx()
    val glowColor = color.copy(alpha = (glowAlpha * effAlpha).coerceIn(0f, 1f))

    // Glow Layer (Wider stroke with Round caps)
    drawPath(
        path = path,
        color = glowColor.copy(alpha = glowColor.alpha * 0.35f),
        style = Stroke(width = strokeWidth + glowPad * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = path,
        color = glowColor,
        style = Stroke(width = strokeWidth + glowPad, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Core Layer (Solid crisp stroke, strokeCap = Round)
    drawPath(
        path = path,
        color = color.copy(alpha = 0.98f * effAlpha),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * วาด Filled Path สไตล์ Neon 2 ชั้น (Glow layer + Core layer)
 */
fun DrawScope.drawNeonFilledPath(
    path: Path,
    color: Color,
    glowRadiusDp: Dp = 8.dp,
    glowAlpha: Float = 0.5f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val glowPad = glowRadiusDp.toPx()
    val glowColor = color.copy(alpha = (glowAlpha * effAlpha).coerceIn(0f, 1f))

    // Glow Layer (Expanded stroke border around the path)
    drawPath(
        path = path,
        color = glowColor.copy(alpha = glowColor.alpha * 0.35f),
        style = Stroke(width = glowPad * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = path,
        color = glowColor,
        style = Stroke(width = glowPad, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Core Layer (Solid fill)
    drawPath(
        path = path,
        color = color.copy(alpha = 0.98f * effAlpha)
    )
}

/**
 * วาด Arc สไตล์ Neon 2 ชั้น
 */
fun DrawScope.drawNeonArc(
    color: Color,
    startAngle: Float,
    sweepAngle: Float,
    topLeft: Offset,
    size: Size,
    strokeWidth: Float,
    glowRadiusDp: Dp = 8.dp,
    glowAlpha: Float = 0.5f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val glowPad = glowRadiusDp.toPx()
    val glowColor = color.copy(alpha = (glowAlpha * effAlpha).coerceIn(0f, 1f))

    // Glow Layer
    drawArc(
        color = glowColor.copy(alpha = glowColor.alpha * 0.35f),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(topLeft.x - glowPad * 0.5f, topLeft.y - glowPad * 0.5f),
        size = Size(size.width + glowPad, size.height + glowPad),
        style = Stroke(width = strokeWidth + glowPad * 1.5f, cap = StrokeCap.Round)
    )
    drawArc(
        color = glowColor,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth + glowPad, cap = StrokeCap.Round)
    )

    // Core Layer
    drawArc(
        color = color.copy(alpha = 0.98f * effAlpha),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

/**
 * วาด Line สไตล์ Neon 2 ชั้น
 */
fun DrawScope.drawNeonLine(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float,
    glowRadiusDp: Dp = 8.dp,
    glowAlpha: Float = 0.5f,
    alpha: Float = 1f
) {
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    if (effAlpha <= 0.005f) return
    val glowPad = glowRadiusDp.toPx()
    val glowColor = color.copy(alpha = (glowAlpha * effAlpha).coerceIn(0f, 1f))

    // Glow Layer
    drawLine(
        color = glowColor,
        start = start,
        end = end,
        strokeWidth = strokeWidth + glowPad,
        cap = StrokeCap.Round
    )

    // Core Layer
    drawLine(
        color = color.copy(alpha = 0.98f * effAlpha),
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

// ─── 2. Props: Heart, Tear, Sparkle, Swirl, Zzz, Sweat ───────────────────────

/**
 * วาด Neon Heart 2 ชั้น (Love / Romantic)
 */
fun DrawScope.drawNeonHeart(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float,
    glowRadiusDp: Dp = 8.dp,
    alpha: Float = 1f
) {
    if (alpha <= 0.005f || size <= 0f) return
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    val effColor = color.copy(alpha = effAlpha)
    val glowColor = effColor.copy(alpha = 0.5f * effAlpha)

    fun createHeartPath(scale: Float): Path = EyePaths.obtain().apply {
        val w = size * scale
        val h = size * 0.95f * scale
        moveTo(centerX, centerY + h * 0.38f)
        cubicTo(
            centerX - w * 0.55f, centerY - h * 0.2f,
            centerX - w * 0.55f, centerY - h * 0.65f,
            centerX, centerY - h * 0.25f
        )
        cubicTo(
            centerX + w * 0.55f, centerY - h * 0.65f,
            centerX + w * 0.55f, centerY - h * 0.2f,
            centerX, centerY + h * 0.38f
        )
        close()
    }

    // Glow Layer (Expanded)
    drawPath(
        path = createHeartPath(1.18f),
        color = glowColor
    )
    // Core Layer (Crisp Fill)
    drawPath(
        path = createHeartPath(1.0f),
        color = effColor
    )
}

/**
 * วาด Neon Teardrop / Sweatdrop 2 ชั้น (Crying / Hot / Sweat)
 */
fun DrawScope.drawNeonTeardrop(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float,
    glowRadiusDp: Dp = 6.dp,
    alpha: Float = 1f
) {
    if (alpha <= 0.005f || size <= 0f) return
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    val effColor = color.copy(alpha = effAlpha)
    val glowColor = effColor.copy(alpha = 0.5f * effAlpha)

    fun createDropPath(scale: Float): Path = EyePaths.obtain().apply {
        val s = size * scale
        moveTo(centerX, centerY - s * 0.65f)
        cubicTo(centerX - s * 0.55f, centerY, centerX - s * 0.55f, centerY + s * 0.55f, centerX, centerY + s * 0.55f)
        cubicTo(centerX + s * 0.55f, centerY + s * 0.55f, centerX + s * 0.55f, centerY, centerX, centerY - s * 0.65f)
        close()
    }

    // Glow Layer
    drawPath(createDropPath(1.2f), glowColor)
    // Core Layer
    drawPath(createDropPath(1.0f), effColor)
}

/**
 * วาด Neon 4-Point Star Sparkle 2 ชั้น (Excited / Magic)
 */
fun DrawScope.drawNeonSparkle(
    color: Color,
    centerX: Float,
    centerY: Float,
    size: Float,
    rotationDeg: Float = 0f,
    glowRadiusDp: Dp = 6.dp,
    alpha: Float = 1f
) {
    if (alpha <= 0.005f || size <= 0f) return
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    val effColor = color.copy(alpha = effAlpha)
    val glowColor = effColor.copy(alpha = 0.5f * effAlpha)

    rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
        fun createSparklePath(scale: Float): Path = EyePaths.obtain().apply {
            val s = size * scale
            val rOuter = s * 0.5f
            val rInner = s * 0.12f
            moveTo(centerX, centerY - rOuter)
            cubicTo(centerX + rInner, centerY - rInner, centerX + rInner, centerY - rInner, centerX + rOuter, centerY)
            cubicTo(centerX + rInner, centerY + rInner, centerX + rInner, centerY + rInner, centerX, centerY + rOuter)
            cubicTo(centerX - rInner, centerY + rInner, centerX - rInner, centerY + rInner, centerX - rOuter, centerY)
            cubicTo(centerX - rInner, centerY - rInner, centerX - rInner, centerY - rInner, centerX, centerY - rOuter)
            close()
        }
        // Glow Layer
        drawPath(createSparklePath(1.22f), glowColor)
        // Core Layer
        drawPath(createSparklePath(1.0f), effColor)
    }
}

/**
 * วาด Neon Z Letter 2 ชั้น (Sleepy / Dreaming)
 */
fun DrawScope.drawNeonZzz(
    color: Color,
    x: Float,
    y: Float,
    size: Float,
    strokeWidth: Float = 4.dp.toPx(),
    glowRadiusDp: Dp = 6.dp,
    alpha: Float = 1f
) {
    if (alpha <= 0.005f || size <= 0f) return
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    val effColor = color.copy(alpha = effAlpha)

    val zPath = EyePaths.obtain().apply {
        moveTo(x - size / 2f, y - size / 2f)
        lineTo(x + size / 2f, y - size / 2f)
        lineTo(x - size / 2f, y + size / 2f)
        lineTo(x + size / 2f, y + size / 2f)
    }

    drawNeonPath(
        path = zPath,
        color = effColor,
        strokeWidth = strokeWidth,
        glowRadiusDp = glowRadiusDp,
        glowAlpha = 0.5f,
        alpha = effAlpha
    )
}

/**
 * วาด Neon Hypnotic Swirl Spiral 2 ชั้น (Sick / Dizzy)
 */
fun DrawScope.drawNeonSwirl(
    color: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    rotationDeg: Float,
    strokeWidth: Float = 5.dp.toPx(),
    glowRadiusDp: Dp = 7.dp,
    alpha: Float = 1f
) {
    if (alpha <= 0.005f || radius <= 0f) return
    val effAlpha = (color.alpha * alpha).coerceIn(0f, 1f)
    val effColor = color.copy(alpha = effAlpha)

    rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
        for (i in 1..3) {
            val r = radius * (i / 3.2f)
            val arcTopLeft = Offset(centerX - r, centerY - r)
            val arcSize = Size(r * 2f, r * 2f)
            drawNeonArc(
                color = effColor,
                startAngle = i * 65f,
                sweepAngle = 230f,
                topLeft = arcTopLeft,
                size = arcSize,
                strokeWidth = strokeWidth,
                glowRadiusDp = glowRadiusDp,
                glowAlpha = 0.5f,
                alpha = effAlpha
            )
        }
    }
}

// ─── 3. Particle Motion Layer State ──────────────────────────────────────────

/**
 * State สำหรับควบคุมการเคลื่อนไหวแบบอิสระของพร็อพ (Particle Motion Layer)
 * พร็อพฝั่งซ้ายและขวาจะใช้ Phase และ Duration ต่างกันเล็กน้อย เพื่อให้การเคลื่อนไหวเป็นธรรมชาติ
 * (snapshot ของเฟรมเดียว — สร้างจาก [PetParticleMotionSource.snapshot] ภายใน draw phase)
 */
data class PetParticleMotionState(
    // Love: floating mini hearts
    val heartFloatLeft: Float = 0f,
    val heartFloatRight: Float = 0f,
    val heartPulseLeft: Float = 1f,
    val heartPulseRight: Float = 1f,
    // Crying: dropping tears
    val tearFallLeft: Float = 0f,
    val tearFallRight: Float = 0f,
    // Sleepy: drifting Zzz
    val zzzRise1: Float = 0f,
    val zzzRise2: Float = 0f
)

/**
 * ถือ State ของ particle animation โดยไม่อ่านค่าใน composition — เรียก [snapshot] ใน DrawScope
 */
@Stable
class PetParticleMotionSource internal constructor(
    private val heartFloatLeft: State<Float>,
    private val heartFloatRight: State<Float>,
    private val heartPulseLeft: State<Float>,
    private val heartPulseRight: State<Float>,
    private val tearFallLeft: State<Float>,
    private val tearFallRight: State<Float>,
    private val zzzRise1: State<Float>,
    private val zzzRise2: State<Float>
) {
    fun snapshot(): PetParticleMotionState = PetParticleMotionState(
        heartFloatLeft = heartFloatLeft.value,
        heartFloatRight = heartFloatRight.value,
        heartPulseLeft = heartPulseLeft.value,
        heartPulseRight = heartPulseRight.value,
        tearFallLeft = tearFallLeft.value,
        tearFallRight = tearFallRight.value,
        zzzRise1 = zzzRise1.value,
        zzzRise2 = zzzRise2.value
    )
}

/**
 * Composable สร้าง particle transitions อิสระซ้าย-ขวา
 */
@Composable
fun rememberPetParticleMotionSource(): PetParticleMotionSource {
    val transLeft = rememberInfiniteTransition(label = "pet_particles_left")
    val transRight = rememberInfiniteTransition(label = "pet_particles_right")

    // 1. Love hearts
    val heartFloatLeft = transLeft.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "heart_fl_l"
    )
    val heartFloatRight = transRight.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2150, easing = LinearEasing), RepeatMode.Restart),
        label = "heart_fl_r"
    )
    val heartPulseLeft = transLeft.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(750, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "heart_p_l"
    )
    val heartPulseRight = transRight.animateFloat(
        initialValue = 0.90f, targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(880, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "heart_p_r"
    )

    // 2. Tears
    val tearFallLeft = transLeft.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1350, easing = EaseInQuad), RepeatMode.Restart),
        label = "tear_f_l"
    )
    val tearFallRight = transRight.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1550, easing = EaseInQuad), RepeatMode.Restart),
        label = "tear_f_r"
    )

    // 3. Sleepy Zzz
    val zzzRise1 = transLeft.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "zzz_r_1"
    )
    val zzzRise2 = transRight.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2750, easing = LinearEasing), RepeatMode.Restart),
        label = "zzz_r_2"
    )

    return remember(transLeft, transRight) {
        PetParticleMotionSource(
            heartFloatLeft, heartFloatRight, heartPulseLeft, heartPulseRight,
            tearFallLeft, tearFallRight, zzzRise1, zzzRise2
        )
    }
}

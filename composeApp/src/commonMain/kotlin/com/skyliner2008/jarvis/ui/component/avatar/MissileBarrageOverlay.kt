package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import com.skyliner2008.jarvis.sound.RobotSoundPlayer
import kotlin.math.*
import kotlin.random.Random

/**
 * MissileTrajectory — ข้อมูลวิถีการบินของจรวดแต่ละลูก
 */
private data class MissileTrajectory(
    val id: Int,
    val startXRatio: Float,    // 0..1
    val startYRatio: Float,    // 0..1
    val targetXRatio: Float,   // จุดที่พุ่งชนหน้าจอ 0..1
    val targetYRatio: Float,
    val startAngleDeg: Float,
    val targetAngleDeg: Float,
    val delayMs: Int,
    val flightDurationMs: Int = 900
)

/**
 * ExplosionParticle — ข้อมูลสะเก็ดไฟระเบิด
 */
private data class ExplosionParticle(
    val origin: Offset,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val radius: Float,
    val spawnTimeFraction: Float
)

/**
 * MissileBarrageOverlay — เอฟเฟกต์จรวดมิสซายยิงถล่มหน้าจอ
 *
 * เมื่อ Pet โกรธจัด (ENRAGED):
 * 1. ยิงจรวดการ์ตูนลูกเล็กๆ 5 ลูก (ตามแบบภาพอ้างอิง: ลำตัวขาว หัวแดง ครีบแดง ไฟท้ายส้ม หน้าต่างฟ้า)
 * 2. จรวดพุ่งทะยานออกมาจากรอบตัวหุ่นยนต์ โค้งพุ่งตรงเข้าหาหน้าจอมือถือ (ขยายขนาดขึ้นแบบ 3D)
 * 3. เมื่อพุ่งชนหน้าจอ -> เกิดลูกไฟระเบิดตูมตาม + คลื่น Shockwave + จอสั่น (Screen Shake) + แสงวาบ
 */
@Composable
fun MissileBarrageOverlay(
    isActive: Boolean,
    onScreenShake: (intensity: Float) -> Unit = {},
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isActive) return

    val animTime = remember { Animatable(0f) }
    var explosionSoundPlayed by remember { mutableStateOf(false) }

    val trajectories = remember {
        listOf(
            // ปรับเริ่มยิงหลังจาก Pet แสดงสีหน้าโกรธ 1200ms พร้อมยืดเวลาการบินเป็น 1150-1250ms เพื่อให้เห็นจรวดบินชัดเจน
            MissileTrajectory(0, 0.25f, 0.55f, 0.20f, 0.28f, -60f, -45f, 1200, 1200),
            MissileTrajectory(1, 0.75f, 0.55f, 0.80f, 0.30f, -120f, -135f, 1450, 1200),
            MissileTrajectory(2, 0.20f, 0.45f, 0.40f, 0.65f, 20f, 40f, 1700, 1150),
            MissileTrajectory(3, 0.80f, 0.45f, 0.60f, 0.68f, 160f, 140f, 1950, 1150),
            MissileTrajectory(4, 0.50f, 0.35f, 0.50f, 0.45f, -90f, -90f, 2200, 1100)
        )
    }

    // Spark shrapnel particles
    val particles = remember {
        val list = mutableListOf<ExplosionParticle>()
        val rnd = Random(42)
        val colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFFF3D00))
        for (i in 0..45) {
            val angle = rnd.nextDouble(0.0, 2.0 * PI)
            val speed = rnd.nextDouble(180.0, 650.0).toFloat()
            val color = colors[rnd.nextInt(colors.size)]
            list.add(
                ExplosionParticle(
                    origin = Offset.Zero,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat(),
                    color = color,
                    radius = rnd.nextDouble(3.0, 7.0).toFloat(),
                    spawnTimeFraction = rnd.nextDouble(0.35, 0.65).toFloat()
                )
            )
        }
        list
    }

    LaunchedEffect(isActive) {
        explosionSoundPlayed = false
        animTime.snapTo(0f)
        animTime.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
        )
        onFinished()
    }

    val progress = animTime.value
    val totalDurationMs = 5000f
    val currentMs = progress * totalDurationMs

    // Trigger Screen Shake & Explosion Sound when first missile impacts (~2400ms)
    LaunchedEffect(currentMs) {
        if (currentMs >= 2400f && !explosionSoundPlayed) {
            explosionSoundPlayed = true
            RobotSoundPlayer.playExplosion()
        }
        if (currentMs in 2350f..3800f) {
            val impactDecay = ((3800f - currentMs) / 1450f).coerceIn(0f, 1f)
            onScreenShake(impactDecay * 18f)
        } else {
            onScreenShake(0f)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // ─── 1. Screen Blast Flash Overlay ─────────────────────────────────────
        if (currentMs in 2400f..3600f) {
            val flashProg = ((currentMs - 2400f) / 1200f).coerceIn(0f, 1f)
            val flashAlpha = (sin(flashProg * PI).toFloat() * 0.42f).coerceIn(0f, 0.45f)
            drawRect(
                color = Color(0xFFFF5722).copy(alpha = flashAlpha),
                size = size
            )
        }

        // ─── 2. Draw Each Missile ──────────────────────────────────────────────
        for (traj in trajectories) {
            val missileStartMs = traj.delayMs.toFloat()
            val missileEndMs = missileStartMs + traj.flightDurationMs.toFloat()

            if (currentMs in missileStartMs..missileEndMs) {
                val flightProg = ((currentMs - missileStartMs) / traj.flightDurationMs.toFloat()).coerceIn(0f, 1f)

                // 3D Scale: Missile scales up dramatically as it approaches the screen
                val scale = 0.40f + (flightProg * flightProg) * 1.50f

                // Interpolate Position with slight curve
                val sx = traj.startXRatio * w
                val sy = traj.startYRatio * h
                val tx = traj.targetXRatio * w
                val ty = traj.targetYRatio * h

                val currentX = sx + (tx - sx) * flightProg
                val currentY = sy + (ty - sy) * flightProg - sin(flightProg * PI).toFloat() * 40.dp.toPx()

                val currentAngle = traj.startAngleDeg + (traj.targetAngleDeg - traj.startAngleDeg) * flightProg

                // Draw Rocket Smoke Trail behind
                val trailCount = 5
                for (t in 1..trailCount) {
                    val trailProg = (flightProg - (t * 0.04f)).coerceIn(0f, 1f)
                    val trailX = sx + (tx - sx) * trailProg
                    val trailY = sy + (ty - sy) * trailProg
                    val trailRadius = (8.dp.toPx() * scale * (t * 0.6f)).coerceAtLeast(3f)
                    val trailAlpha = ((1f - (t.toFloat() / trailCount)) * 0.45f).coerceIn(0f, 1f)
                    drawCircle(
                        color = Color.White.copy(alpha = trailAlpha),
                        radius = trailRadius,
                        center = Offset(trailX, trailY)
                    )
                }

                // Draw Cartoon Rocket (Styled after user reference)
                withTransform({
                    translate(currentX, currentY)
                    rotate(currentAngle + 45f) // Rocket tip pointing 45 deg relative to flight vector
                    scale(scale, scale)
                }) {
                    drawCartoonRocket()
                }
            }

            // ─── 3. Draw Impact Explosion at Target Location ────────────────────
            if (currentMs > missileEndMs && currentMs < missileEndMs + 1400f) {
                val explProg = ((currentMs - missileEndMs) / 1400f).coerceIn(0f, 1f)
                val ex = traj.targetXRatio * w
                val ey = traj.targetYRatio * h
                drawScreenExplosion(ex, ey, explProg)
            }
        }

        // ─── 4. Flying Shrapnel Spark Particles ────────────────────────────────
        if (currentMs in 2400f..4200f) {
            val explTime = (currentMs - 2400f) / 1000f // seconds
            for (p in particles) {
                val px = (w * 0.5f) + (p.vx * explTime)
                val py = (h * 0.45f) + (p.vy * explTime) + (0.5f * 800f * explTime * explTime) // with gravity
                val pAlpha = (1f - (explTime / 1.6f)).coerceIn(0f, 1f)
                if (pAlpha > 0f) {
                    drawCircle(
                        color = p.color.copy(alpha = pAlpha),
                        radius = p.radius * (1f - explTime * 0.5f).coerceAtLeast(1f),
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}

/**
 * วาดจรวดการ์ตูนตามภาพอ้างอิงเป๊ะๆ (media_1789218973423.png)
 * - ลำตัวแคปซูลสีขาวเงา
 * - หัวแหลมสีแดงสด
 * - หน้าต่างกระจกกลมสีฟ้า
 * - ครีบปีกหางสีแดง 3 ปีก
 * - เปลวไฟพุ่งท้ายสีเหลืองทอง-ส้ม
 */
private fun DrawScope.drawCartoonRocket() {
    val rw = 32.dp.toPx()
    val rh = 64.dp.toPx()

    // ─── 1. Thruster Exhaust Flame (ไฟท้ายพุ่ง) ───────────────────────────
    val flamePath = Path().apply {
        moveTo(-rw * 0.28f, rh * 0.45f)
        quadraticTo(-rw * 0.50f, rh * 0.85f, 0f, rh * 1.15f)
        quadraticTo(rw * 0.50f, rh * 0.85f, rw * 0.28f, rh * 0.45f)
        close()
    }
    // Outer flame (Orange/Red)
    drawPath(flamePath, color = Color(0xFFFF5722))
    // Inner flame core (Yellow/White hot)
    val innerFlamePath = Path().apply {
        moveTo(-rw * 0.16f, rh * 0.45f)
        quadraticTo(-rw * 0.25f, rh * 0.75f, 0f, rh * 0.95f)
        quadraticTo(rw * 0.25f, rh * 0.75f, rw * 0.16f, rh * 0.45f)
        close()
    }
    drawPath(innerFlamePath, color = Color(0xFFFFEB3B))

    // ─── 2. Red Tail Fins (ครีบปีกข้าง 2 ข้าง + ครีบกลาง) ───────────────────
    // Left Fin
    val leftFin = Path().apply {
        moveTo(-rw * 0.42f, rh * 0.10f)
        quadraticTo(-rw * 0.85f, rh * 0.25f, -rw * 0.75f, rh * 0.52f)
        lineTo(-rw * 0.35f, rh * 0.40f)
        close()
    }
    drawPath(leftFin, color = Color(0xFFD32F2F))
    drawPath(leftFin, color = Color(0xFF212121), style = Stroke(width = 2.dp.toPx()))

    // Right Fin
    val rightFin = Path().apply {
        moveTo(rw * 0.42f, rh * 0.10f)
        quadraticTo(rw * 0.85f, rh * 0.25f, rw * 0.75f, rh * 0.52f)
        lineTo(rw * 0.35f, rh * 0.40f)
        close()
    }
    drawPath(rightFin, color = Color(0xFFD32F2F))
    drawPath(rightFin, color = Color(0xFF212121), style = Stroke(width = 2.dp.toPx()))

    // ─── 3. Main Rocket Body (ลำตัวขาวรูปทรงแคปซูล/ตอร์ปิโด) ───────────────
    val bodyPath = Path().apply {
        moveTo(0f, -rh * 0.55f) // Tip of nose
        quadraticTo(rw * 0.52f, -rh * 0.15f, rw * 0.42f, rh * 0.40f)
        lineTo(-rw * 0.42f, rh * 0.40f)
        quadraticTo(-rw * 0.52f, -rh * 0.15f, 0f, -rh * 0.55f)
        close()
    }
    drawPath(bodyPath, color = Color.White)

    // Red Base Ring
    drawRect(
        color = Color(0xFFD32F2F),
        topLeft = Offset(-rw * 0.42f, rh * 0.32f),
        size = Size(rw * 0.84f, rh * 0.08f)
    )

    // Red Nose Cone (หัวแหลมสีแดง)
    val nosePath = Path().apply {
        moveTo(0f, -rh * 0.55f)
        quadraticTo(rw * 0.40f, -rh * 0.28f, rw * 0.44f, -rh * 0.18f)
        lineTo(-rw * 0.44f, -rh * 0.18f)
        quadraticTo(-rw * 0.40f, -rh * 0.28f, 0f, -rh * 0.55f)
        close()
    }
    drawPath(nosePath, color = Color(0xFFE53935))

    // ─── 4. Circular Porthole Window (หน้าต่างฟ้ากลมพร้อมเงาสะท้อน) ───────
    val windowCenter = Offset(0f, rh * 0.02f)
    val windowRadius = rw * 0.26f

    // Window frame border
    drawCircle(color = Color(0xFF37474F), radius = windowRadius + 2.dp.toPx(), center = windowCenter)
    // Blue glass
    drawCircle(color = Color(0xFF00B0FF), radius = windowRadius, center = windowCenter)
    // Glass highlight shine
    drawCircle(
        color = Color.White.copy(alpha = 0.75f),
        radius = windowRadius * 0.35f,
        center = Offset(windowCenter.x - windowRadius * 0.35f, windowCenter.y - windowRadius * 0.35f)
    )

    // Two small body rivets / bolts
    drawCircle(color = Color(0xFF90A4AE), radius = 2.dp.toPx(), center = Offset(0f, rh * 0.18f))
    drawCircle(color = Color(0xFF90A4AE), radius = 2.dp.toPx(), center = Offset(0f, rh * 0.24f))

    // Black comic outline around entire rocket body
    drawPath(bodyPath, color = Color(0xFF212121), style = Stroke(width = 2.5.dp.toPx()))
}

/**
 * วาดเอฟเฟกต์ลูกไฟระเบิดบนหน้าจอ (Screen Explosion Shockwave & Fireball)
 */
private fun DrawScope.drawScreenExplosion(cx: Float, cy: Float, progress: Float) {
    if (progress >= 1f) return

    val maxRadius = 140.dp.toPx()
    val radius = maxRadius * sqrt(progress)
    val alpha = (1f - progress).coerceIn(0f, 1f)

    // 1. Shockwave blast ring
    drawCircle(
        color = Color.White.copy(alpha = alpha * 0.85f),
        radius = radius * 1.15f,
        center = Offset(cx, cy),
        style = Stroke(width = (6.dp.toPx() * (1f - progress)).coerceAtLeast(1f))
    )

    // 2. Outer smoke / dark orange cloud
    drawCircle(
        color = Color(0xFFFF5722).copy(alpha = alpha * 0.60f),
        radius = radius,
        center = Offset(cx, cy)
    )

    // 3. Middle fiery blast
    drawCircle(
        color = Color(0xFFFF9800).copy(alpha = alpha * 0.80f),
        radius = radius * 0.65f,
        center = Offset(cx, cy)
    )

    // 4. Inner white-hot core
    drawCircle(
        color = Color(0xFFFFFFFF).copy(alpha = alpha),
        radius = radius * 0.35f,
        center = Offset(cx, cy)
    )
}

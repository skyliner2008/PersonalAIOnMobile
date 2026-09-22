package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * PetGestureAnimations — Body language and gesture animation modifiers for Pet Robot Avatar
 *
 * ฟังก์ชัน Modifier สำหรับท่าทาง/ภาษากายของหัวหุ่นยนต์:
 * - IDLE: ลอยหายใจปกติ (ไม่มี transform พิเศษ คืนค่า Modifier เปล่า)
 * - TILT_LEFT / TILT_RIGHT: เอียงคอซ้าย-ขวาแบบสมูทด้วย animateFloatAsState (spring spec)
 * - BOUNCE: กระดอนขึ้นลงแบบวนลูปต่อเนื่อง
 * - JUMP: กระโดดขึ้นแบบวันช็อต (one-shot spring) สปริงตัวแล้วกลับสู่ท่าปกติ
 * - WOBBLE: โยกตัวส่ายซ้าย-ขวาต่อเนื่อง
 * - SHAKE: สั่นสะเทือนถี่อย่างรวดเร็ว (ตกใจ/สับสน)
 * - NOD: พยักหน้าขึ้นลงเป็นจังหวะพร้อมหยุดพัก
 */

/**
 * Returns a [Modifier.graphicsLayer] applying appropriate gesture animation transforms
 * based on the provided [gesture].
 *
 * สำหรับ [GestureType.IDLE] จะคืนค่า [Modifier] เปล่าเพื่อประสิทธิภาพสูงสุด
 *
 * @param gesture ท่าทางของหุ่นยนต์ เช่น BOUNCE, JUMP, TILT_LEFT, TILT_RIGHT, WOBBLE, SHAKE, NOD
 * @return [Modifier] พร้อมแอนิเมชัน graphicsLayer ตามท่าทาง
 */
@Composable
fun rememberGestureModifier(gesture: GestureType): Modifier = when (gesture) {
    GestureType.IDLE -> Modifier

    GestureType.TILT_LEFT, GestureType.TILT_RIGHT -> {
        val targetRotation = if (gesture == GestureType.TILT_LEFT) -12f else 12f
        val tiltRotation by animateFloatAsState(
            targetValue = targetRotation,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "gesture_tilt"
        )
        Modifier.graphicsLayer { rotationZ = tiltRotation }
    }

    GestureType.BOUNCE -> {
        val infiniteTransition = rememberInfiniteTransition(label = "gesture_bounce")
        val bounceY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -20f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 350, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bounce_y"
        )
        Modifier.graphicsLayer { translationY = bounceY }
    }

    GestureType.JUMP -> {
        val jumpY = remember { Animatable(0f) }
        LaunchedEffect(gesture) {
            jumpY.snapTo(-40f)
            jumpY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        Modifier.graphicsLayer { translationY = jumpY.value }
    }

    GestureType.WOBBLE -> {
        val infiniteTransition = rememberInfiniteTransition(label = "gesture_wobble")
        val wobbleZ by infiniteTransition.animateFloat(
            initialValue = -8f,
            targetValue = 8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 200, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "wobble_rotation"
        )
        Modifier.graphicsLayer { rotationZ = wobbleZ }
    }

    GestureType.SHAKE -> {
        val infiniteTransition = rememberInfiniteTransition(label = "gesture_shake")
        val shakeX by infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 60, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shake_x"
        )
        Modifier.graphicsLayer { translationX = shakeX }
    }

    GestureType.NOD -> {
        val infiniteTransition = rememberInfiniteTransition(label = "gesture_nod")
        val nodY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 600
                    0f at 0
                    -8f at 150
                    4f at 350
                    0f at 500
                    0f at 600
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "nod_y"
        )
        Modifier.graphicsLayer { translationY = nodY }
    }
}

/**
 * Returns the base head tilt angle override for a gesture, or null if the gesture
 * doesn't override the default head tilt behavior.
 *
 * คืนค่ามุมเอียงหัว (องศา) สำหรับท่าทางนั้นๆ หรือ null หากท่าทางนั้นไม่ได้แทนที่การเอียงหัวปกติ
 */
fun GestureType.headTiltOverride(): Float? = when (this) {
    GestureType.TILT_LEFT -> -12f
    GestureType.TILT_RIGHT -> 12f
    else -> null
}

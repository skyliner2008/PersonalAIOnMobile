package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * PetGestureAnimations — Body language and gesture animation modifiers for Pet Robot Avatar
 *
 * ฟังก์ชัน Modifier สำหรับท่าทาง/ภาษากายของหัวหุ่นยนต์:
 * - IDLE: ลอยหายใจปกติ (ไม่มี transform พิเศษ)
 * - TILT_LEFT / TILT_RIGHT: หมุนโดย PetRobotHeadAvatar เท่านั้น ([headTiltOverride]) — ที่นี่ไม่หมุนซ้ำ
 * - BOUNCE: กระดอนขึ้นลงแบบวนลูปต่อเนื่อง
 * - JUMP: กระโดดขึ้นแบบวันช็อต (one-shot spring) สปริงตัวแล้วกลับสู่ท่าปกติ — เล่นซ้ำเมื่อ replayKey เปลี่ยน
 * - WOBBLE: โยกตัวส่ายซ้าย-ขวาต่อเนื่อง
 * - SHAKE: สั่นสะเทือนถี่อย่างรวดเร็ว (ตกใจ/สับสน)
 * - NOD: พยักหน้าขึ้นลงเป็นจังหวะพร้อมหยุดพัก
 *
 * ทุกท่าถูก compose ไว้ตลอดและผสมด้วยน้ำหนัก 0..1 (fade 200ms) — เปลี่ยนท่าแล้วไม่กระตุกกลับ 0 ทันที
 */

/**
 * Returns a [Modifier.graphicsLayer] applying the gesture animation for [gesture].
 * All animated values are read inside the layer block, so gestures never recompose the caller.
 *
 * @param gesture ท่าทางของหุ่นยนต์ เช่น BOUNCE, JUMP, WOBBLE, SHAKE, NOD
 * @param replayKey เปลี่ยนค่านี้ (เช่น sceneId) เพื่อเล่นท่า one-shot อย่าง JUMP ซ้ำแม้ท่าเดิม
 */
@Composable
fun rememberGestureModifier(gesture: GestureType, replayKey: Any? = null): Modifier {
    val infinite = rememberInfiniteTransition(label = "gesture_loops")
    val bounceY = infinite.animateFloat(
        initialValue = 0f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce_y"
    )
    val wobbleZ = infinite.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobble_rotation"
    )
    val shakeX = infinite.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake_x"
    )
    val nodY = infinite.animateFloat(
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

    val weightSpec = tween<Float>(200, easing = FastOutSlowInEasing)
    val bounceWeight = animateFloatAsState(if (gesture == GestureType.BOUNCE) 1f else 0f, weightSpec, label = "bounce_w")
    val wobbleWeight = animateFloatAsState(if (gesture == GestureType.WOBBLE) 1f else 0f, weightSpec, label = "wobble_w")
    val shakeWeight = animateFloatAsState(if (gesture == GestureType.SHAKE) 1f else 0f, weightSpec, label = "shake_w")
    val nodWeight = animateFloatAsState(if (gesture == GestureType.NOD) 1f else 0f, weightSpec, label = "nod_w")

    val jumpY = remember { Animatable(0f) }
    LaunchedEffect(gesture, replayKey) {
        if (gesture == GestureType.JUMP) {
            jumpY.snapTo(-40f)
            jumpY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    return Modifier.graphicsLayer {
        // a loop is only read while its weight is non-zero, so an idle pet does not invalidate the layer
        fun weighted(value: State<Float>, weight: State<Float>): Float {
            val w = weight.value
            return if (w > 0.001f) value.value * w else 0f
        }
        translationY = weighted(bounceY, bounceWeight) + weighted(nodY, nodWeight) + jumpY.value
        translationX = weighted(shakeX, shakeWeight)
        rotationZ = weighted(wobbleZ, wobbleWeight)
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

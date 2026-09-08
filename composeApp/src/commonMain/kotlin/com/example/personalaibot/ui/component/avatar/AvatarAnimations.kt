package com.example.personalaibot.ui.component.avatar

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import kotlin.math.sin
import kotlin.random.Random

/**
 * AvatarAnimations — Compose InfiniteTransition based animation controllers
 *
 * ทุก animation ใช้ Compose Animation API (ไม่มี external dependency)
 * ออกแบบให้เบา — ไม่มี recomposition ที่ไม่จำเป็น
 */

// ─── Breathing / Idle Bob ────────────────────────────────────────────────────
@Composable
fun rememberBreathingScale(): State<Float> {
    val transition = rememberInfiniteTransition(label = "breathing")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )
}

// ─── Eye Blink ───────────────────────────────────────────────────────────────
/**
 * Returns eye openness (1.0 = fully open, 0.0 = fully closed)
 * Blinks every ~4 seconds for ~150ms
 */
@Composable
fun rememberEyeBlink(): State<Float> {
    val transition = rememberInfiniteTransition(label = "blink")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                1f at 0
                1f at 3700
                0.1f at 3800   // blink down
                0.1f at 3850   // stay closed
                1f at 3950     // open back
                1f at 4000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "eye_blink"
    )
}

// ─── Thinking Dots ───────────────────────────────────────────────────────────
/**
 * Returns 3 dot opacities that cycle sequentially
 */
@Composable
fun rememberThinkingDots(): List<State<Float>> {
    val transition = rememberInfiniteTransition(label = "thinking")
    return List(3) { index ->
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    val startDelay = index * 300
                    0.2f at 0
                    0.2f at startDelay
                    1.0f at startDelay + 200
                    0.2f at startDelay + 500
                    0.2f at 1200
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "thinking_dot_$index"
        )
    }
}

// ─── Audio Visualizer Bars ───────────────────────────────────────────────────
/**
 * Returns 5 bar heights (0..1) that oscillate at different frequencies
 * Combined with actual audioLevel for responsive visualization
 */
@Composable
fun rememberVisualizerBars(audioLevel: Float): List<Float> {
    val transition = rememberInfiniteTransition(label = "visualizer")
    val phases = listOf(0f, 0.7f, 0.3f, 0.9f, 0.5f)

    return List(5) { index ->
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 600 + index * 100,
                    easing = EaseInOutSine
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
        // Mix animated wave with actual audio level
        val wave = sin((animated + phases[index]) * Math.PI.toFloat()).coerceIn(0f, 1f)
        (wave * 0.3f + audioLevel * 0.7f).coerceIn(0.05f, 1f)
    }
}

// ─── Body Bounce (Speaking / Happy) ──────────────────────────────────────────
@Composable
fun rememberBodyBounce(): State<Float> {
    val transition = rememberInfiniteTransition(label = "bounce")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "body_bounce"
    )
}

// ─── Heart Float (Love Mode) ─────────────────────────────────────────────────
/**
 * Returns 3 hearts with (x_offset, y_offset, alpha, scale) for floating animation
 */
data class HeartParticle(
    val xOffset: Float,
    val yOffset: Float,
    val alpha: Float,
    val scale: Float
)

@Composable
fun rememberFloatingHearts(): List<HeartParticle> {
    val transition = rememberInfiniteTransition(label = "hearts")

    return List(3) { index ->
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2000 + index * 400,
                    delayMillis = index * 600,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "heart_$index"
        )
        val xWave = sin(progress * 3f * Math.PI.toFloat()) * 15f * (if (index % 2 == 0) 1 else -1)
        HeartParticle(
            xOffset = xWave,
            yOffset = -progress * 80f,
            alpha = (1f - progress).coerceIn(0f, 1f),
            scale = 0.5f + progress * 0.5f
        )
    }
}

// ─── ZZZ Float (Sleeping Mode) ───────────────────────────────────────────────
data class ZzzParticle(
    val xOffset: Float,
    val yOffset: Float,
    val alpha: Float,
    val scale: Float,
    val rotation: Float
)

@Composable
fun rememberFloatingZzz(): List<ZzzParticle> {
    val transition = rememberInfiniteTransition(label = "zzz")

    return List(3) { index ->
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 3000,
                    delayMillis = index * 900,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "zzz_$index"
        )
        ZzzParticle(
            xOffset = progress * 30f + index * 10f,
            yOffset = -progress * 60f,
            alpha = (1f - progress * 0.8f).coerceIn(0f, 1f),
            scale = 0.6f + index * 0.2f + progress * 0.3f,
            rotation = progress * 15f
        )
    }
}

// ─── Antenna Wiggle (Excited) ────────────────────────────────────────────────
@Composable
fun rememberAntennaWiggle(): State<Float> {
    val transition = rememberInfiniteTransition(label = "antenna")
    return transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "antenna_wiggle"
    )
}

// ─── Shake (Angry) ───────────────────────────────────────────────────────────
@Composable
fun rememberShake(): State<Float> {
    val transition = rememberInfiniteTransition(label = "shake")
    return transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake_offset"
    )
}

// ─── Slow Sway (Sad) ────────────────────────────────────────────────────────
@Composable
fun rememberSlowSway(): State<Float> {
    val transition = rememberInfiniteTransition(label = "sway")
    return transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway_offset"
    )
}

// ─── Glow Pulse ──────────────────────────────────────────────────────────────
@Composable
fun rememberGlowPulse(): State<Float> {
    val transition = rememberInfiniteTransition(label = "glow")
    return transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )
}

// ─── Mouth Open/Close (Speaking) ─────────────────────────────────────────────
@Composable
fun rememberMouthAnimation(): State<Float> {
    val transition = rememberInfiniteTransition(label = "mouth")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(250, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mouth_open"
    )
}

// ─── Sparkle (Happy / Excited) ───────────────────────────────────────────────
data class SparkleParticle(
    val x: Float, val y: Float, val alpha: Float, val scale: Float
)

@Composable
fun rememberSparkles(): List<SparkleParticle> {
    val transition = rememberInfiniteTransition(label = "sparkles")
    return List(4) { index ->
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1500,
                    delayMillis = index * 350,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "sparkle_$index"
        )
        val angle = (index * 90f + progress * 360f) * (Math.PI.toFloat() / 180f)
        val radius = 40f + progress * 20f
        SparkleParticle(
            x = kotlin.math.cos(angle) * radius,
            y = kotlin.math.sin(angle) * radius,
            alpha = (sin(progress * Math.PI.toFloat())).coerceIn(0f, 1f),
            scale = 0.3f + sin(progress * Math.PI.toFloat()) * 0.7f
        )
    }
}

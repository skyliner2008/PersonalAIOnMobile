package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs

/**
 * LivingMotionState — Centralized state for the Procedural Living Motion Engine.
 *
 * Provides real-time organic movement parameters for the LOOI Robot Avatar:
 * 1. Respiration: volume-conserving scale and vertical floating.
 * 2. Organic Sway: gentle subtle head roll.
 * 3. Saccadic Eye Glances: micro-darts of eye focus every few seconds.
 * 4. Micro-Blink: natural eye blinking across moodsets.
 * 5. Dynamic Drivers: loopFast, loopMedium, loopSlow, pulse, sparkleRot, flickerPhase.
 */
@Immutable
data class LivingMotionState(
    val breathingScaleX: Float = 1f,
    val breathingScaleY: Float = 1f,
    val breathingOffsetY: Float = 0f,
    val headSwayDeg: Float = 0f,
    val saccadeOffsetX: Float = 0f,
    val saccadeOffsetY: Float = 0f,
    val microBlinkFactor: Float = 1f,
    val loopFast: Float = 0f,     // 0..1 over ~600ms (fast vibration, bubbles, electricity)
    val loopMedium: Float = 0f,   // 0..1 over ~1400ms (swaying, tears, falling items, steam)
    val loopSlow: Float = 0f,     // 0..1 over ~3200ms (orbits, clouds, spinning items)
    val pulse: Float = 1f,        // 0.85..1.15 smooth sine pulse (~1600ms)
    val sparkleRot: Float = 0f,   // 0..360 continuous rotation
    val flickerPhase: Float = 1f, // 0 or 1 stepped flicker
    val chewCycle: Float = 0f,    // 0..1 over ~850ms (rhythmic chomp & chewing munch)
    val gulpCycle: Float = 0f,    // 0..1 over ~1300ms (tilted drinking, swallow & throat pulse)
    val popPhase: Float = 0f,     // 0..1 over ~700ms (popped popcorn kernel trajectory)

    // ─── Purposeful Emotional Gestures Drivers ─────────────────────────────────
    val angerJitterX: Float = 0f,    // High-frequency rage/fear micro-jitter X
    val angerJitterY: Float = 0f,    // High-frequency rage/fear micro-jitter Y
    val angerBrowSlant: Float = 0f,  // Brow slant dynamic twitch (-4.5..+4.5 degrees)
    val veinPulse: Float = 1f,       // Throbbing blood pressure vein pulse
    val heartbeatScale: Float = 1f,  // Lub-dub authentic double cardiac pulse (1.0..1.28)
    val laughBounceY: Float = 0f,    // Laughing convulsive belly bounce Y
    val laughSquint: Float = 1f,     // Laughing > < contraction
    val nodOffOffsetY: Float = 0f,   // Nod-off droop Y for sleeping
    val readingScanX: Float = 0f,    // Sawtooth left-to-right eye scan (-0.26..+0.26)
    val pantHeaveY: Float = 0f,      // Heavy exhaustion panting heave Y
    val pantCycle: Float = 0f,       // 0..1 over fast pant cycle
    val partyHornProg: Float = 0f,   // 0..1 unfurl/stretch of party horn
    val wandArcAngle: Float = 0f,    // Waving angle for magic wand
    val bladeShineProg: Float = 0f,  // 0..1 sweep across blade
    val brownoutAlpha: Float = 1f,   // Flickering brownout power for low battery
    val shiverFastX: Float = 0f,     // High-frequency shiver X for cold / scared
    val shiverFastY: Float = 0f,     // High-frequency shiver Y for cold / scared
    val winkEyeScaleY: Float = 1f,   // Dynamic wink eye scale Y (1f open -> 0.06f wink slit -> 1f open)
    val winkSparkleScale: Float = 0f // Sparkle burst scale (0f -> 1.3f when winking)
)

@Composable
fun rememberLivingMotionState(): LivingMotionState {
    val infinite = rememberInfiniteTransition(label = "living_motion_engine")

    // ─── 1. Organic Respiration (Volume-Conserving Breathing) ───────────────────
    // Period: 2600ms. Inhaling: height increases (+2.2%), width narrows slightly (-1.2%)
    val breathPhase by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "living_breath_phase"
    )
    val breathingScaleY = 1f + 0.022f * breathPhase
    val breathingScaleX = 1f - 0.012f * breathPhase
    val breathingOffsetY = -4.5f * breathPhase

    // ─── 2. Subtle Organic Head Sway ─────────────────────────────────────────────
    // Period: 3800ms. Roll: ±1.2 degrees
    val headSwayDeg by infinite.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "living_head_sway"
    )

    // ─── 3. Saccadic Eye Micro-Darting ──────────────────────────────────────────
    // Every 3600ms, eyes make a quick glance (100ms dart, holds for 600ms, then relaxes)
    val saccadeTime by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "living_saccade_time"
    )
    val (saccadeOffsetX, saccadeOffsetY) = remember(saccadeTime) {
        when {
            saccadeTime in 0.28f..0.45f -> {
                val t = ((saccadeTime - 0.28f) / 0.17f).coerceIn(0f, 1f)
                val ease = sin(t * PI.toFloat()).toFloat()
                Pair(5f * ease, -2.5f * ease)
            }
            saccadeTime in 0.72f..0.88f -> {
                val t = ((saccadeTime - 0.72f) / 0.16f).coerceIn(0f, 1f)
                val ease = sin(t * PI.toFloat()).toFloat()
                Pair(-4.5f * ease, 1.8f * ease)
            }
            else -> Pair(0f, 0f)
        }
    }

    // ─── 4. Natural Micro-Blink ──────────────────────────────────────────────────
    // Blinks every ~3.8s for ~130ms
    val microBlinkFactor by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3800
                1f at 0
                1f at 3550
                0.08f at 3630
                0.08f at 3680
                1f at 3760
                1f at 3800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "living_micro_blink"
    )

    // ─── 5. Dynamic Drivers for Props & Effects ──────────────────────────────────
    // Fast Loop (600ms): bubbles, electrical jitter, vibration
    val loopFast by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "living_loop_fast"
    )

    // Medium Loop (1400ms): sweat drops, floating items, tears, confetti
    val loopMedium by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "living_loop_medium"
    )

    // Slow Loop (3200ms): planet orbits, cloud drifting, rotating gears
    val loopSlow by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "living_loop_slow"
    )

    // Pulse (1600ms): Smooth 0.85f..1.15f sinusoid for glowing lights and veins
    val pulseRaw by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "living_pulse_raw"
    )
    val pulse = 1f + 0.15f * pulseRaw

    // Sparkle Rotation (2400ms): 0..360 degrees
    val sparkleRot by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "living_sparkle_rot"
    )

    // Flicker Phase (400ms stepped)
    val flickerPhase by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 400
                1f at 0
                0.25f at 100
                1f at 160
                0.4f at 280
                1f at 340
                1f at 400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "living_flicker_phase"
    )

    // ─── 6. Food & Drink Living Gestures Drivers ────────────────────────────────
    // Chew Cycle (2000ms): burger lift from below -> chomp bite -> lower & chew
    val chewCycle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "living_chew_cycle"
    )

    // Gulp Cycle (2400ms): head tilt & glass tip to mouth -> gulps -> lower & refresh
    val gulpCycle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "living_gulp_cycle"
    )

    // Popcorn Phase (700ms): popcorn burst and arc flight
    val popPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "living_pop_phase"
    )

    return remember(
        breathingScaleX, breathingScaleY, breathingOffsetY,
        headSwayDeg, saccadeOffsetX, saccadeOffsetY, microBlinkFactor,
        loopFast, loopMedium, loopSlow, pulse, sparkleRot, flickerPhase,
        chewCycle, gulpCycle, popPhase
    ) {
        // 1. Anger micro-jitter and brow slant twitch
        val angerJitterX = sin(loopFast * 24f * PI.toFloat()) * 2.2f
        val angerJitterY = cos(loopFast * 30f * PI.toFloat()) * 1.6f
        val angerBrowSlant = sin(loopMedium * 4f * PI.toFloat()) * 4.5f
        val veinPulse = 1f + sin(loopFast * 6f * PI.toFloat()).coerceAtLeast(0f) * 0.35f

        // 2. Love heartbeat thumping (Authentic lub-dub double beat)
        val loveT = loopMedium
        val heartbeatScale = when {
            loveT < 0.14f -> 1f + sin(loveT / 0.14f * PI.toFloat()) * 0.18f
            loveT < 0.22f -> 1f
            loveT < 0.40f -> 1f + sin((loveT - 0.22f) / 0.18f * PI.toFloat()) * 0.28f
            else -> 1f
        }

        // 3. Laughing convulsive belly bounce
        val laughT = (loopFast * 2.2f) % 1f
        val laughBounceY = -abs(sin(laughT * PI.toFloat())) * 4f
        val laughSquint = 1f + sin(laughT * PI.toFloat()) * 0.22f

        // 4. Sleepy nod-off droop
        val nodT = loopSlow
        val nodOffOffsetY = when {
            nodT < 0.65f -> sin(nodT / 0.65f * (PI.toFloat() / 2f)) * 7f
            nodT < 0.75f -> 7f * (1f - (nodT - 0.65f) / 0.10f)
            else -> 0f
        }

        // 5. Reading sawtooth left-to-right eye scan
        val readT = (loopSlow * 1.5f) % 1f
        val readingScanX = if (readT < 0.85f) {
            -0.26f + (readT / 0.85f) * 0.52f
        } else {
            0.26f - ((readT - 0.85f) / 0.15f) * 0.52f
        }

        // 6. Exhaustion panting heave
        val pantC = (loopFast * 1.6f) % 1f
        val pantHeaveY = -abs(sin(pantC * PI.toFloat())) * 3.5f

        // 7. Party horn unfurl & stretch
        val partyT = (loopMedium * 1.2f) % 1f
        val partyHornProg = when {
            partyT < 0.35f -> partyT / 0.35f
            partyT < 0.65f -> 1f + sin((partyT - 0.35f) / 0.30f * 4f * PI.toFloat()) * 0.08f
            else -> 1f - (partyT - 0.65f) / 0.35f
        }

        // 8. Magic wand waving arc
        val wandArcAngle = sin(loopMedium * 2f * PI.toFloat()) * 16f

        // 9. Low battery brownout flicker
        val brownoutAlpha = (flickerPhase * 0.65f + 0.35f) * (0.55f + 0.45f * sin(loopSlow * 2f * PI.toFloat()).coerceAtLeast(0f))

        // 10. Shiver for cold / scared
        val shiverFastX = sin(loopFast * 36f * PI.toFloat()) * 2.8f
        val shiverFastY = cos(loopFast * 40f * PI.toFloat()) * 1.5f

        // 11. Double-Wink gesture driver (~2400ms cycle: ขยิบตา 2 ที)
        val winkT = (loopSlow * 1.35f) % 1f
        val winkEyeScaleY = when {
            winkT < 0.15f -> 1f // Open
            winkT < 0.27f -> {
                // Wink 1: Closing
                val sub = (winkT - 0.15f) / 0.12f
                1f - (1f - 0.06f) * sin(sub * (PI.toFloat() / 2f))
            }
            winkT < 0.38f -> 0.06f // Wink 1: Held shut
            winkT < 0.48f -> {
                // Wink 1: Opening
                val sub = (winkT - 0.38f) / 0.10f
                0.06f + (1f - 0.06f) * (1f - cos(sub * (PI.toFloat() / 2f)))
            }
            winkT < 0.56f -> 1f // Brief bounce open
            winkT < 0.68f -> {
                // Wink 2: Closing
                val sub = (winkT - 0.56f) / 0.12f
                1f - (1f - 0.06f) * sin(sub * (PI.toFloat() / 2f))
            }
            winkT < 0.80f -> 0.06f // Wink 2: Held shut
            winkT < 0.90f -> {
                // Wink 2: Opening
                val sub = (winkT - 0.80f) / 0.10f
                0.06f + (1f - 0.06f) * (1f - cos(sub * (PI.toFloat() / 2f)))
            }
            else -> 1f // Rest open
        }
        val winkSparkleScale = if (winkT in 0.66f..0.84f) {
            val sub = (winkT - 0.66f) / 0.18f
            sin(sub * PI.toFloat()).coerceAtLeast(0f) * 1.45f
        } else if (winkT in 0.25f..0.40f) {
            val sub = (winkT - 0.25f) / 0.15f
            sin(sub * PI.toFloat()).coerceAtLeast(0f) * 0.75f
        } else {
            0f
        }

        LivingMotionState(
            breathingScaleX = breathingScaleX,
            breathingScaleY = breathingScaleY,
            breathingOffsetY = breathingOffsetY,
            headSwayDeg = headSwayDeg,
            saccadeOffsetX = saccadeOffsetX,
            saccadeOffsetY = saccadeOffsetY,
            microBlinkFactor = microBlinkFactor,
            loopFast = loopFast,
            loopMedium = loopMedium,
            loopSlow = loopSlow,
            pulse = pulse,
            sparkleRot = sparkleRot,
            flickerPhase = flickerPhase,
            chewCycle = chewCycle,
            gulpCycle = gulpCycle,
            popPhase = popPhase,
            angerJitterX = angerJitterX,
            angerJitterY = angerJitterY,
            angerBrowSlant = angerBrowSlant,
            veinPulse = veinPulse,
            heartbeatScale = heartbeatScale,
            laughBounceY = laughBounceY,
            laughSquint = laughSquint,
            nodOffOffsetY = nodOffOffsetY,
            readingScanX = readingScanX,
            pantHeaveY = pantHeaveY,
            pantCycle = pantC,
            partyHornProg = partyHornProg,
            wandArcAngle = wandArcAngle,
            bladeShineProg = loopMedium,
            brownoutAlpha = brownoutAlpha,
            shiverFastX = shiverFastX,
            shiverFastY = shiverFastY,
            winkEyeScaleY = winkEyeScaleY,
            winkSparkleScale = winkSparkleScale
        )
    }
}

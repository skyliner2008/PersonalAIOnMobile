package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.skyliner2008.jarvis.avatar.AvatarLookMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import personalaibot.composeapp.generated.resources.Res
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Godot robot's head and shoulders as pictures, for places the 3D engine doesn't run: the floating
 * overlay bubble, the Drive screen, Avatar mode on Android 10 and older. The frames come from the same
 * robot (pet_godot main.gd --sprites, written into composeResources/files/robot/ by
 * pet_godot/tools/export_to_app.ps1): one per face, a blink of each, three open mouths for talking.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun RobotSprite(
    state: AvatarState,
    modifier: Modifier = Modifier
) {
    val look = remember(state.emotion, state.faceState, state.isDizzy) { AvatarLookMapper.look(state) }
    val face = spriteFace(look.emotion, look.eyes)

    // blink every few seconds
    var blinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2800L, 5200L))
            blinking = true
            delay(130L)
            blinking = false
        }
    }
    // talking: the mouth follows the voice level (a rhythm when there is no level)
    var mouthStep by remember { mutableStateOf(0) }
    LaunchedEffect(state.isSpeaking) {
        mouthStep = 0
        while (state.isSpeaking) {
            delay(110L)
            mouthStep++
        }
    }
    val frame = when {
        state.isSpeaking -> {
            val level = state.audioLevel
            val open = if (level > 0.02f) (level * 3f).toInt().coerceIn(0, 3) else listOf(1, 2, 3, 2, 0)[mouthStep % 5]
            if (open == 0) "talk" else "talk_$open"
        }
        blinking -> "${face}_blink"
        else -> face
    }

    val frames = remember { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(frame) {
        if (frame !in frames) {
            runCatching {
                withContext(Dispatchers.Default) { Res.readBytes("files/robot/robot_$frame.webp").decodeToImageBitmap() }
            }.onSuccess { frames[frame] = it }
        }
    }
    // hold the last shown picture while a new one decodes (no flicker)
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    frames[frame]?.let { shown = it }

    // floating in place
    val t by rememberInfiniteTransition(label = "robot_float").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "robot_float_t"
    )
    Box(modifier) {
        shown?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = "JARVIS robot",
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize().graphicsLayer {
                    val s = sin(t * 2f * PI.toFloat())
                    translationY = s * size.height * 0.025f
                    scaleX = 1f - s * 0.012f
                    scaleY = 1f + s * 0.012f
                }
            )
        }
    }
}

/** Sprite name for the robot's look: dizzy / cry / squint eyes have their own, otherwise the emotion. */
internal fun spriteFace(emotion: String, eyes: String): String = when (eyes) {
    "dizzy", "cry", "squint" -> eyes
    else -> emotion
}

package com.skyliner2008.jarvis.ui.component.avatar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.RivePointerInputMode
import app.rive.ViewModelInstance
import app.rive.ViewModelSource
import app.rive.rememberArtboardResult
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorkerOrNull
import app.rive.rememberStateMachineResult
import app.rive.rememberViewModelInstanceResult
import com.skyliner2008.jarvis.R

private const val TAG = "RiveAvatarView"
private const val STATE_MACHINE = "State Machine 1"

actual val isRiveRuntimeSupported: Boolean = true

/**
 * Android Rive pet on the Compose-native `app.rive` API: a Rive worker loads `R.raw.avatar`,
 * the default artboard / "State Machine 1" run with the artboard's default `Avatar` view-model
 * instance, and [RiveAvatarInputs] are written into it (see rive_avatar/AVATAR_CONTRACT.md).
 *
 * Any failure (native library, worker, file, artboard, state machine or view model) flips
 * [RiveRuntime.failed] and the Compose Canvas pet is shown instead.
 */
@Composable
actual fun RiveAvatarView(
    modifier: Modifier,
    inputs: () -> RiveAvatarInputs,
    fallbackState: AvatarState
) {
    val context = LocalContext.current
    // Rive's auto initializer is removed in the library manifest: the native library must be
    // loaded with Rive.init before a worker can start (MainActivity also does this early).
    val ready = remember { initRive(context) }
    if (!ready || RiveRuntime.failed) {
        RiveFallback(modifier, fallbackState, "native library")
        return
    }

    val workerError = remember { mutableStateOf<Throwable?>(null) }
    val worker = rememberRiveWorkerOrNull(workerError)
    if (worker == null) {
        RiveFallback(modifier, fallbackState, "worker", workerError.value)
        return
    }

    val source = remember(context) { RiveFileSource.RawRes(R.raw.avatar, context.resources) }
    val file = when (val r = rememberRiveFile(source, worker)) {
        is Result.Success -> r.value
        is Result.Error -> { RiveFallback(modifier, fallbackState, "file", r.throwable); return }
        Result.Loading -> { Box(modifier); return }
    }
    val artboard = when (val r = rememberArtboardResult(file)) {
        is Result.Success -> r.value
        is Result.Error -> { RiveFallback(modifier, fallbackState, "artboard", r.throwable); return }
        Result.Loading -> { Box(modifier); return }
    }
    val stateMachine = when (val r = rememberStateMachineResult(artboard, STATE_MACHINE)) {
        is Result.Success -> r.value
        is Result.Error -> { RiveFallback(modifier, fallbackState, "state machine", r.throwable); return }
        Result.Loading -> { Box(modifier); return }
    }
    val vmiSource = remember(artboard) { ViewModelSource.DefaultForArtboard(artboard).defaultInstance() }
    val viewModel = when (val r = rememberViewModelInstanceResult(file, vmiSource)) {
        is Result.Success -> r.value
        is Result.Error -> { RiveFallback(modifier, fallbackState, "view model", r.throwable); return }
        Result.Loading -> { Box(modifier); return }
    }

    // gyro tilt -> tiltX / tiltY (the face slides and banks against the tilt)
    val tilt = rememberDeviceTilt(context)
    // jelly: the face follows the tilt on an under-damped spring (a little wobble)
    val tiltSpring = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)
    val tiltX = animateFloatAsState(tilt.first, tiltSpring, label = "rive_gyro_x")
    val tiltY = animateFloatAsState(tilt.second, tiltSpring, label = "rive_gyro_y")
    val latestInputs by rememberUpdatedState(inputs)

    // The input provider (with its gaze / tilt springs) and the gyro spring are read inside
    // snapshotFlow, so moving values only write to the view model — they never recompose this
    // composable or the Rive surface. As before the migration, the gyro drives tiltX / tiltY.
    LaunchedEffect(viewModel) {
        val writer = RiveInputWriter(viewModel)
        snapshotFlow {
            latestInputs().copy(
                tiltX = tiltX.value.coerceIn(-1.15f, 1.15f),
                tiltY = tiltY.value.coerceIn(-1.15f, 1.15f)
            )
        }.collect { writer.write(it) }
    }

    Rive(
        file = file,
        modifier = modifier,
        playing = true,
        artboard = artboard,
        stateMachine = stateMachine,
        viewModelInstance = viewModel,
        fit = Fit.Contain(),
        // The pet screen's Compose gesture detectors (poke / pat / chin / gaze drag) sit above
        // this view. Rive must not consume touches or no pet reaction fires on this engine.
        // The .riv has no Rive listeners of its own, so nothing is lost.
        pointerInputMode = RivePointerInputMode.PassThrough
    )
}

/** Marks the Rive engine as failed (once) and shows the Compose Canvas pet instead. */
@Composable
private fun RiveFallback(modifier: Modifier, fallbackState: AvatarState, reason: String, error: Throwable? = null) {
    SideEffect {
        if (!RiveRuntime.failed) {
            Log.e(TAG, "Rive unavailable ($reason) - showing the Canvas pet", error)
            RiveRuntime.failed = true
        }
    }
    PetRobotHeadAvatar(
        state = fallbackState,
        modifier = modifier,
        engineType = AvatarEngineType.COMPOSE_CANVAS
    )
}

private fun initRive(context: Context): Boolean = try {
    app.rive.runtime.kotlin.core.Rive.init(context.applicationContext)
    true
} catch (t: Throwable) {
    Log.e(TAG, "Rive native library initialization failed", t)
    RiveRuntime.failed = true
    false
}

/**
 * Writes [RiveAvatarInputs] into the view model, sending only values that changed so the
 * one-shot channels (`seq` / `react`) are never re-sent. A failing property is logged once and
 * skipped — it never crashes the pet screen.
 */
private class RiveInputWriter(private val vmi: ViewModelInstance) {
    private val lastNumbers = HashMap<String, Float>()
    private var lastSpeaking: Boolean? = null
    private val failed = HashSet<String>()

    fun write(inputs: RiveAvatarInputs) {
        setNumber("face", inputs.face.toFloat())
        setNumber("prop", inputs.prop.toFloat())
        setNumber("bg", inputs.bg.toFloat())
        setNumber("fg", inputs.fg.toFloat())
        setNumber("eyeAct", inputs.eyeAct.toFloat())
        setNumber("state", inputs.state.toFloat())
        setNumber("item", inputs.item.toFloat())
        setNumber("seq", inputs.seq.toFloat())
        setNumber("react", inputs.react.toFloat())
        setNumber("gazeX", inputs.gazeX)
        setNumber("gazeY", inputs.gazeY)
        setNumber("tiltX", inputs.tiltX)
        setNumber("tiltY", inputs.tiltY)
        setNumber("audioLevel", inputs.audioLevel)
        if (lastSpeaking != inputs.isSpeaking && "isSpeaking" !in failed) {
            try {
                vmi.setBoolean("isSpeaking", inputs.isSpeaking)
                lastSpeaking = inputs.isSpeaking
            } catch (t: Throwable) {
                failed += "isSpeaking"
                Log.w(TAG, "Failed to write 'isSpeaking'", t)
            }
        }
    }

    private fun setNumber(name: String, value: Float) {
        if (lastNumbers[name] == value || name in failed) return
        try {
            vmi.setNumber(name, value)
            lastNumbers[name] = value
        } catch (t: Throwable) {
            failed += name
            Log.w(TAG, "Failed to write '$name'", t)
        }
    }
}

/** Low-passed gravity direction as (tiltX, tiltY) in −1…1. */
@Composable
private fun rememberDeviceTilt(context: Context): Pair<Float, Float> {
    var tilt by remember { mutableStateOf(0f to 0f) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var fx = 0f
        var fz = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // roll: gravity along the screen's x axis; pitch: leaning the screen back / forward
                val x = (-event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                val z = (event.values[2] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                fx += (x - fx) * 0.12f
                fz += (z - fz) * 0.12f
                val next = (fx * 1.4f).coerceIn(-1f, 1f) to (fz * 0.8f).coerceIn(-1f, 1f)
                // only recompose on a visible change
                if (kotlin.math.abs(next.first - tilt.first) > 0.02f ||
                    kotlin.math.abs(next.second - tilt.second) > 0.02f
                ) {
                    tilt = next
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) {
            manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { manager?.unregisterListener(listener) }
    }
    return tilt
}

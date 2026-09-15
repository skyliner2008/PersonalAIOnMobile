package com.skyliner2008.jarvis.ui.component.avatar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelBooleanProperty
import app.rive.runtime.kotlin.core.ViewModelInstance
import app.rive.runtime.kotlin.core.ViewModelNumberProperty
import com.skyliner2008.jarvis.R

private const val TAG = "RiveAvatarView"
private const val STATE_MACHINE = "State Machine 1"

actual val isRiveRuntimeSupported: Boolean = true

/**
 * Android Rive pet: RiveAnimationView bound to the `Avatar` view model (autoBind),
 * written from [RiveAvatarInputs] (see rive_avatar/AVATAR_CONTRACT.md).
 * If the native library cannot load, flips [RiveRuntime.failed] and renders the
 * Compose Canvas pet instead.
 */
@Composable
actual fun RiveAvatarView(
    modifier: Modifier,
    inputs: RiveAvatarInputs,
    fallbackState: AvatarState
) {
    val context = LocalContext.current
    val ready = remember { initRive(context) }

    if (!ready || RiveRuntime.failed) {
        PetRobotHeadAvatar(
            state = fallbackState,
            modifier = modifier,
            engineType = AvatarEngineType.COMPOSE_CANVAS
        )
        return
    }

    // gyro tilt -> tiltX / tiltY (the face slides and banks against the tilt)
    val tilt = rememberDeviceTilt(context)
    // jelly: the face follows the tilt on an under-damped spring (a little wobble)
    val tiltSpring = androidx.compose.animation.core.spring<Float>(
        dampingRatio = 0.5f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow
    )
    val tiltX by androidx.compose.animation.core.animateFloatAsState(tilt.first, tiltSpring, label = "rive_gyro_x")
    val tiltY by androidx.compose.animation.core.animateFloatAsState(tilt.second, tiltSpring, label = "rive_gyro_y")
    val binder = remember { RiveViewModelBinder() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            try {
                RiveAnimationView(ctx).apply {
                    // The pet screen's Compose gesture detectors (poke / pat / chin / gaze drag)
                    // sit above this view. Without pass-through the view absorbs every touch
                    // and no pet reaction fires on the Rive engine (found on device). The .riv
                    // has no Rive listeners of its own, so nothing is lost.
                    touchPassThrough = true
                    setRiveResource(
                        resId = R.raw.avatar,
                        stateMachineName = STATE_MACHINE,
                        autoplay = true,
                        autoBind = true
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to create RiveAnimationView", t)
                RiveRuntime.failed = true
                android.view.View(ctx)
            }
        },
        update = { view ->
            if (view is RiveAnimationView) {
                binder.write(view, inputs.copy(tiltX = tiltX.coerceIn(-1.15f, 1.15f), tiltY = tiltY.coerceIn(-1.15f, 1.15f)))
            }
        },
        onRelease = { binder.clear() }
    )
}

private fun initRive(context: Context): Boolean = try {
    Rive.init(context.applicationContext)
    true
} catch (t: Throwable) {
    Log.e(TAG, "Rive native library initialization failed", t)
    RiveRuntime.failed = true
    false
}

/**
 * Looks the view-model properties up once and writes only values that changed,
 * so `seq` / `react` are never re-sent every recomposition.
 * `getNumberProperty` throws on an unknown name — any failure is logged once and
 * that property is skipped, never crashing the pet screen.
 */
private class RiveViewModelBinder {
    private var vmi: ViewModelInstance? = null
    private val numbers = HashMap<String, ViewModelNumberProperty?>()
    private var speaking: ViewModelBooleanProperty? = null
    private var speakingResolved = false
    private val lastNumbers = HashMap<String, Float>()
    private var lastSpeaking: Boolean? = null

    fun clear() {
        vmi = null
        numbers.clear()
        speaking = null
        speakingResolved = false
        lastNumbers.clear()
        lastSpeaking = null
    }

    fun write(view: RiveAnimationView, inputs: RiveAvatarInputs) {
        val instance = resolveInstance(view) ?: return
        try {
            if (!view.isPlaying) view.play()
        } catch (_: Throwable) {
        }
        setNumber(instance, "face", inputs.face.toFloat())
        setNumber(instance, "prop", inputs.prop.toFloat())
        setNumber(instance, "bg", inputs.bg.toFloat())
        setNumber(instance, "fg", inputs.fg.toFloat())
        setNumber(instance, "eyeAct", inputs.eyeAct.toFloat())
        setNumber(instance, "state", inputs.state.toFloat())
        setNumber(instance, "item", inputs.item.toFloat())
        setNumber(instance, "seq", inputs.seq.toFloat())
        setNumber(instance, "react", inputs.react.toFloat())
        setNumber(instance, "gazeX", inputs.gazeX)
        setNumber(instance, "gazeY", inputs.gazeY)
        setNumber(instance, "tiltX", inputs.tiltX)
        setNumber(instance, "tiltY", inputs.tiltY)
        setNumber(instance, "audioLevel", inputs.audioLevel)
        setSpeaking(instance, inputs.isSpeaking)
    }

    private fun resolveInstance(view: RiveAnimationView): ViewModelInstance? {
        val current = try {
            view.controller.stateMachines.firstOrNull()?.viewModelInstance
                ?: view.controller.activeArtboard?.viewModelInstance
        } catch (_: Throwable) {
            null
        }
        if (current !== vmi) {
            // a new file / artboard was bound: drop cached handles
            clear()
            vmi = current
        }
        return current
    }

    private fun setNumber(instance: ViewModelInstance, name: String, value: Float) {
        if (lastNumbers[name] == value) return
        val prop = if (numbers.containsKey(name)) numbers[name] else try {
            instance.getNumberProperty(name)
        } catch (t: Throwable) {
            Log.w(TAG, "View model has no number property '$name'", t)
            null
        }.also { numbers[name] = it }
        if (prop != null) {
            try {
                prop.value = value
                lastNumbers[name] = value
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to write '$name'", t)
            }
        }
    }

    private fun setSpeaking(instance: ViewModelInstance, value: Boolean) {
        if (lastSpeaking == value) return
        if (!speakingResolved) {
            speaking = try {
                instance.getBooleanProperty("isSpeaking")
            } catch (t: Throwable) {
                Log.w(TAG, "View model has no boolean property 'isSpeaking'", t)
                null
            }
            speakingResolved = true
        }
        speaking?.let {
            try {
                it.value = value
                lastSpeaking = value
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to write 'isSpeaking'", t)
            }
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

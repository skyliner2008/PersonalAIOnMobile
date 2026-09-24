package com.skyliner2008.jarvis.pet

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError

/**
 * PetMotionDetector — ตรวจจับการเคลื่อนไหวของอุปกรณ์สำหรับโหมดสัตว์เลี้ยง
 *
 * 1. Shake Detection: ตรวจจับเมื่อผู้ใช้หยิบมือถือขึ้นมาเขย่า -> สัตว์เลี้ยงมึนงง (Dizzy)
 * 2. Face-Down / Desk Flip Detection: ตรวจจับเมื่อคว่ำหน้าจอลงบนโต๊ะ -> สัตว์เลี้ยงหลับ (Sleep)
 *    และเมื่อหงายขึ้นมา -> สัตว์เลี้ยงตื่น (Wake up)
 */
class PetMotionDetector(
    private val context: Context,
    private val onShake: () -> Unit,
    private val onFaceDown: () -> Unit,
    private val onFaceUp: () -> Unit,
    private val onHeavyShake: (() -> Unit)? = null,
    private val onBoatRocking: (() -> Unit)? = null,
    private val onTableThump: (() -> Unit)? = null
) : SensorEventListener {

    companion object {
        private const val TAG = "PetMotionDetector"
    }

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isRunning = false

    /** All gesture logic lives in the pure (unit-tested) classifier; this class only wires the sensor. */
    private val classifier = PetMotionClassifier()

    fun start() {
        if (isRunning) {
            logDebug(TAG, "PetMotionDetector already running — skipping duplicate start()")
            return
        }
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                isRunning = true
                logDebug(TAG, "PetMotionDetector registered")
            }
        } catch (e: Exception) {
            logError(TAG, "Failed to start PetMotionDetector: ${e.message}")
        }
    }

    fun stop() {
        if (!isRunning && sensorManager == null) {
            return
        }
        isRunning = false
        try {
            sensorManager?.unregisterListener(this)
            logDebug(TAG, "PetMotionDetector stopped")
        } catch (e: Exception) {
            logError(TAG, "Failed to stop PetMotionDetector: ${e.message}")
        } finally {
            sensorManager = null
            accelerometer = null
            classifier.reset()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isRunning || event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        // SensorEvent.timestamp is monotonic (ns since boot) — wall-clock jumps cannot fake a gesture
        val timeMs = event.timestamp / 1_000_000L

        for (motion in classifier.onSample(x, y, z, timeMs)) {
            when (motion) {
                PetMotionEvent.HEAVY_SHAKE -> {
                    // เขย่ารัวๆ / เขย่าแรงมาก -> Heavy Shake (โกรธ)
                    logDebug(TAG, "⚡ Heavy shake detected!")
                    onHeavyShake?.invoke() ?: onShake()
                    PetMotionBridge.triggerHeavyShake()
                }
                PetMotionEvent.SHAKE -> {
                    // เขย่าธรรมดา -> มึนงง
                    logDebug(TAG, "🌀 Normal shake detected!")
                    onShake()
                    PetMotionBridge.triggerShake()
                }
                PetMotionEvent.BOAT_ROCKING -> {
                    logDebug(TAG, "⛵ Boat rocking motion detected! (Seasick / Dizzy)")
                    onBoatRocking?.invoke()
                    PetMotionBridge.triggerBoatRocking()
                }
                PetMotionEvent.TABLE_THUMP -> {
                    logDebug(TAG, "💥 Table thump impulse detected!")
                    onTableThump?.invoke()
                    PetMotionBridge.triggerTableThump()
                }
                PetMotionEvent.FACE_DOWN -> {
                    logDebug(TAG, "😴 Face-down on desk detected -> sleeping")
                    onFaceDown()
                    PetMotionBridge.triggerFaceDown()
                }
                PetMotionEvent.FACE_UP -> {
                    logDebug(TAG, "☀️ Face-up detected -> waking up")
                    onFaceUp()
                    PetMotionBridge.triggerFaceUp()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

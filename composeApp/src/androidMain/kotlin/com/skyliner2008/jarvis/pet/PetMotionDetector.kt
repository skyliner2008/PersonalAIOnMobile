package com.skyliner2008.jarvis.pet

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import kotlin.math.sqrt

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
        private const val SHAKE_THRESHOLD = 2.2f       // ~2.2G acceleration
        private const val SHAKE_SLOP_TIME_MS = 400L
        private const val HEAVY_SHAKE_WINDOW_MS = 4000L
        private const val BOAT_ROCKING_TILT = 3.2f     // m/s^2 on X-axis (~20 degree roll)
        private const val BOAT_ROCKING_WINDOW_MS = 4000L
        private const val FACE_DOWN_Z_THRESHOLD = -8.2f // m/s^2
    }

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    // Shake tracking
    private var lastShakeTime = 0L
    private var shakeCount = 0
    private val recentShakeTimestamps = mutableListOf<Long>()

    // Boat rocking (tilt left/right)
    private var lastTiltSide = 0 // -1: left, +1: right, 0: center
    private var tiltTransitions = 0
    private var tiltCycleStartTime = 0L
    private var lastBoatRockingTriggerTime = 0L

    // Table thump detection (sharp impulse while stationary)
    private var lastGForce = 1.0f
    private var stationaryFrames = 0
    private var lastThumpTriggerTime = 0L

    // Face-down tracking
    private var isCurrentlyFaceDown = false
    private var faceDownStartTime = 0L

    fun start() {
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                logDebug(TAG, "PetMotionDetector registered")
            }
        } catch (e: Exception) {
            logError(TAG, "Failed to start PetMotionDetector: ${e.message}")
        }
    }

    fun stop() {
        try {
            sensorManager?.unregisterListener(this)
            sensorManager = null
            accelerometer = null
            logDebug(TAG, "PetMotionDetector stopped")
        } catch (e: Exception) {
            logError(TAG, "Failed to stop PetMotionDetector: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val now = System.currentTimeMillis()

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH
        val gForce = kotlin.math.sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        // ─── 1. Shake & Heavy Shake Detection ────────────────────────────
        if (gForce > SHAKE_THRESHOLD) {
            if (lastShakeTime + SHAKE_SLOP_TIME_MS > now) {
                shakeCount++
                if (shakeCount >= 2) {
                    shakeCount = 0
                    lastShakeTime = now + 900L // Cooldown

                    // Record shake timestamp
                    recentShakeTimestamps.removeAll { now - it > HEAVY_SHAKE_WINDOW_MS }
                    recentShakeTimestamps.add(now)

                    if (recentShakeTimestamps.size >= 3) {
                        // เขย่ารัวๆ / เขย่าแรงมาก -> Heavy Shake (โกรธ)
                        logDebug(TAG, "⚡ Heavy shake detected! (${recentShakeTimestamps.size} shakes in window)")
                        onHeavyShake?.invoke() ?: onShake()
                        PetMotionBridge.triggerHeavyShake()
                    } else {
                        // เขย่าธรรมดา -> มึนงง
                        logDebug(TAG, "🌀 Normal shake detected! gForce=$gForce")
                        onShake()
                        PetMotionBridge.triggerShake()
                    }
                }
            } else {
                shakeCount = 1
                lastShakeTime = now
            }
        }

        // ─── 2. Boat Rocking Detection (เอียงไปมาเหมือนนั่งเรือ) ───────────
        // เกิดขึ้นเมื่อความเร่งรวมไม่กระชาก (gForce < 1.6f) แต่แกน X สลับซ้าย-ขวา
        if (gForce < 1.6f && gForce > 0.6f) {
            val currentSide = when {
                x < -BOAT_ROCKING_TILT -> -1 // เอียงซ้าย
                x > BOAT_ROCKING_TILT -> 1  // เอียงขวา
                else -> 0
            }

            if (currentSide != 0) {
                if (tiltCycleStartTime == 0L || now - tiltCycleStartTime > BOAT_ROCKING_WINDOW_MS) {
                    tiltCycleStartTime = now
                    tiltTransitions = 0
                    lastTiltSide = currentSide
                } else if (lastTiltSide != 0 && currentSide != lastTiltSide) {
                    tiltTransitions++
                    lastTiltSide = currentSide
                    if (tiltTransitions >= 3 && now - lastBoatRockingTriggerTime > 5000L) {
                        lastBoatRockingTriggerTime = now
                        tiltTransitions = 0
                        logDebug(TAG, "⛵ Boat rocking motion detected! (Seasick / Dizzy)")
                        onBoatRocking?.invoke()
                        PetMotionBridge.triggerBoatRocking()
                    }
                }
            }
        }

        // ─── 3. Table Thump Detection (ทุบโต๊ะ / แรงสะเทือนเฉียบพลัน) ──────
        // เครื่องวางนิ่ง (stationary ~1.0G) แล้วเกิด delta gForce กระแทกแรงฉับพลัน
        val deltaG = kotlin.math.abs(gForce - lastGForce)
        if (gForce in 0.88f..1.12f && deltaG < 0.15f) {
            stationaryFrames = (stationaryFrames + 1).coerceAtMost(50)
        } else {
            if (stationaryFrames >= 15 && deltaG > 1.25f && now - lastThumpTriggerTime > 3000L) {
                lastThumpTriggerTime = now
                stationaryFrames = 0
                logDebug(TAG, "💥 Table thump impulse detected! deltaG=$deltaG")
                onTableThump?.invoke()
                PetMotionBridge.triggerTableThump()
            }
            if (deltaG > 0.35f) {
                stationaryFrames = 0
            }
        }
        lastGForce = gForce

        // ─── 4. Face-Down / Table Flip Detection ─────────────────────────
        if (z < FACE_DOWN_Z_THRESHOLD) {
            // Lying flat face-down on table
            if (!isCurrentlyFaceDown) {
                if (faceDownStartTime == 0L) {
                    faceDownStartTime = now
                } else if (now - faceDownStartTime > 450L) {
                    isCurrentlyFaceDown = true
                    logDebug(TAG, "😴 Face-down on desk detected -> sleeping")
                    onFaceDown()
                    PetMotionBridge.triggerFaceDown()
                }
            }
        } else if (z > -3.0f) {
            // Flipped back up
            faceDownStartTime = 0L
            if (isCurrentlyFaceDown) {
                isCurrentlyFaceDown = false
                logDebug(TAG, "☀️ Face-up detected -> waking up")
                onFaceUp()
                PetMotionBridge.triggerFaceUp()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

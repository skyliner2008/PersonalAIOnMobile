package com.skyliner2008.jarvis.pet

import kotlin.math.abs
import kotlin.math.sqrt

/** Motion gestures recognised from the accelerometer. */
enum class PetMotionEvent {
    /** เขย่าธรรมดา -> มึนงง */
    SHAKE,
    /** เขย่ารัวๆ หลายครั้งในเวลาสั้น -> โกรธ */
    HEAVY_SHAKE,
    /** เอียงซ้าย-ขวาสลับไปมาเหมือนนั่งเรือ -> เมาเรือ */
    BOAT_ROCKING,
    /** วางนิ่งแล้วมีแรงกระแทกฉับพลัน -> ตกใจ */
    TABLE_THUMP,
    /** คว่ำหน้าจอลงโต๊ะ -> หลับ */
    FACE_DOWN,
    /** หงายกลับขึ้นมา -> ตื่น */
    FACE_UP
}

/**
 * PetMotionClassifier — แปลงค่า accelerometer (m/s²) เป็นท่าทางของเครื่อง
 *
 * Pure Kotlin (ไม่มี Android API) เพื่อให้ทดสอบได้ใน commonTest — PetMotionDetector (androidMain)
 * ส่งทุก sample เข้ามาพร้อมเวลาแบบ monotonic (SensorEvent.timestamp) แล้วส่งต่อ event ที่ได้
 * ทุกเกณฑ์เวลาเป็นมิลลิวินาที ไม่ขึ้นกับอัตรา sample ของเซนเซอร์
 */
class PetMotionClassifier {

    companion object {
        const val GRAVITY = 9.80665f
        const val SHAKE_THRESHOLD_G = 2.2f          // ~2.2G acceleration
        const val SHAKE_SLOP_TIME_MS = 400L         // two spikes this close form one shake
        const val SHAKE_COOLDOWN_MS = 900L          // ignore spikes for this long after a shake fires
        const val HEAVY_SHAKE_WINDOW_MS = 4000L
        const val HEAVY_SHAKE_COUNT = 3
        const val BOAT_ROCKING_TILT = 3.2f          // m/s² on X-axis (~20 degree roll)
        const val BOAT_ROCKING_WINDOW_MS = 4000L
        const val BOAT_ROCKING_COOLDOWN_MS = 5000L
        const val THUMP_MIN_STILL_MS = 900L         // must lie still this long before a thump counts
        const val THUMP_DELTA_G = 1.25f
        const val THUMP_COOLDOWN_MS = 3000L
        const val FACE_DOWN_Z = -8.2f               // m/s²
        const val FACE_UP_Z = -3.0f                 // m/s² — hysteresis: must come back above this to wake
        const val FACE_DOWN_HOLD_MS = 450L
    }

    // Shake tracking
    private var lastShakeSpikeTime = Long.MIN_VALUE / 2
    private var shakeCount = 0
    private var shakeCooldownUntil = Long.MIN_VALUE / 2
    private val recentShakeTimestamps = ArrayDeque<Long>()

    // Boat rocking (tilt left/right)
    private var lastTiltSide = 0 // -1: left, +1: right, 0: center
    private var tiltTransitions = 0
    private var tiltCycleStartTime = 0L
    private var lastBoatRockingTriggerTime = Long.MIN_VALUE / 2

    // Table thump detection (sharp impulse while stationary)
    private var lastGForce = 1.0f
    private var stillSinceMs = -1L
    private var lastThumpTriggerTime = Long.MIN_VALUE / 2

    // Face-down tracking
    var isFaceDown = false
        private set
    private var faceDownStartTime = -1L

    /** Feed one accelerometer sample; returns the gestures it completes (usually none). */
    fun onSample(x: Float, y: Float, z: Float, timeMs: Long): List<PetMotionEvent> {
        val events = ArrayList<PetMotionEvent>(1)
        val gX = x / GRAVITY
        val gY = y / GRAVITY
        val gZ = z / GRAVITY
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        // ─── 1. Shake & Heavy Shake ─────────────────────────────────────────
        if (gForce > SHAKE_THRESHOLD_G && timeMs >= shakeCooldownUntil) {
            if (timeMs - lastShakeSpikeTime <= SHAKE_SLOP_TIME_MS) {
                shakeCount++
            } else {
                shakeCount = 1
            }
            lastShakeSpikeTime = timeMs
            if (shakeCount >= 2) {
                shakeCount = 0
                shakeCooldownUntil = timeMs + SHAKE_COOLDOWN_MS
                while (recentShakeTimestamps.isNotEmpty() && timeMs - recentShakeTimestamps.first() > HEAVY_SHAKE_WINDOW_MS) {
                    recentShakeTimestamps.removeFirst()
                }
                recentShakeTimestamps.addLast(timeMs)
                events += if (recentShakeTimestamps.size >= HEAVY_SHAKE_COUNT) PetMotionEvent.HEAVY_SHAKE else PetMotionEvent.SHAKE
            }
        }

        // ─── 2. Boat Rocking (smooth tilt left/right, no jerks) ─────────────
        if (gForce < 1.6f && gForce > 0.6f) {
            val currentSide = when {
                x < -BOAT_ROCKING_TILT -> -1
                x > BOAT_ROCKING_TILT -> 1
                else -> 0
            }
            if (currentSide != 0) {
                if (tiltCycleStartTime == 0L || timeMs - tiltCycleStartTime > BOAT_ROCKING_WINDOW_MS) {
                    tiltCycleStartTime = timeMs
                    tiltTransitions = 0
                    lastTiltSide = currentSide
                } else if (lastTiltSide != 0 && currentSide != lastTiltSide) {
                    tiltTransitions++
                    lastTiltSide = currentSide
                    if (tiltTransitions >= 3 && timeMs - lastBoatRockingTriggerTime > BOAT_ROCKING_COOLDOWN_MS) {
                        lastBoatRockingTriggerTime = timeMs
                        tiltTransitions = 0
                        events += PetMotionEvent.BOAT_ROCKING
                    }
                }
            }
        }

        // ─── 3. Table Thump (lying still ~1G, then a sudden impulse) ────────
        val deltaG = abs(gForce - lastGForce)
        if (gForce in 0.88f..1.12f && deltaG < 0.15f) {
            if (stillSinceMs < 0L) stillSinceMs = timeMs
        } else {
            val stillFor = if (stillSinceMs < 0L) 0L else timeMs - stillSinceMs
            if (stillFor >= THUMP_MIN_STILL_MS && deltaG > THUMP_DELTA_G && timeMs - lastThumpTriggerTime > THUMP_COOLDOWN_MS) {
                lastThumpTriggerTime = timeMs
                stillSinceMs = -1L
                events += PetMotionEvent.TABLE_THUMP
            }
            if (deltaG > 0.35f) stillSinceMs = -1L
        }
        lastGForce = gForce

        // ─── 4. Face-Down / Face-Up ─────────────────────────────────────────
        if (z < FACE_DOWN_Z) {
            if (!isFaceDown) {
                if (faceDownStartTime < 0L) {
                    faceDownStartTime = timeMs
                } else if (timeMs - faceDownStartTime > FACE_DOWN_HOLD_MS) {
                    isFaceDown = true
                    events += PetMotionEvent.FACE_DOWN
                }
            }
        } else {
            // any sample that is not face-down ends the current hold, so a later brief
            // face-down cannot fire at once using a stale start time
            if (!isFaceDown) faceDownStartTime = -1L
            if (z > FACE_UP_Z) {
                faceDownStartTime = -1L
                if (isFaceDown) {
                    isFaceDown = false
                    events += PetMotionEvent.FACE_UP
                }
            }
        }

        return events
    }

    fun reset() {
        shakeCount = 0
        lastShakeSpikeTime = Long.MIN_VALUE / 2
        shakeCooldownUntil = Long.MIN_VALUE / 2
        recentShakeTimestamps.clear()
        lastTiltSide = 0
        tiltTransitions = 0
        tiltCycleStartTime = 0L
        lastGForce = 1.0f
        stillSinceMs = -1L
        isFaceDown = false
        faceDownStartTime = -1L
    }
}

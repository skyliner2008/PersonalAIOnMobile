package com.example.personalaibot.camera

import com.example.personalaibot.logDebug
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

// ═══════════════════════════════════════════════════════════════════
// AdaptiveFpsController — ปรับ FPS อัตโนมัติตามความเคลื่อนไหว
//
// Algorithm:
//   1. เปรียบเทียบ frame ปัจจุบันกับ frame ก่อนหน้า
//   2. ถ้าภาพเปลี่ยนมาก (motion สูง) → เพิ่ม FPS (สูงสุด 3)
//   3. ถ้าภาพนิ่ง (motion ต่ำ) → ลด FPS (ต่ำสุด 0.5)
//   4. ใช้ exponential moving average (EMA) เพื่อ smooth
//
// FPS Range: 0.5 - 3.0 (ค่าเริ่มต้น 1.0)
// ═══════════════════════════════════════════════════════════════════

class AdaptiveFpsController(
    private val minFps: Float = 0.5f,
    private val maxFps: Float = 3.0f,
    private val defaultFps: Float = 1.0f,
    private val emaAlpha: Float = 0.3f,       // Smoothing factor สำหรับ EMA
    private val motionThresholdLow: Float = 0.02f,   // ต่ำกว่านี้ = นิ่ง
    private val motionThresholdHigh: Float = 0.15f    // สูงกว่านี้ = เคลื่อนไหวมาก
) {

    private val _currentFps = MutableStateFlow(defaultFps)
    val currentFps: StateFlow<Float> = _currentFps.asStateFlow()

    private val _motionScore = MutableStateFlow(0f)
    val motionScore: StateFlow<Float> = _motionScore.asStateFlow()

    private val _frameCount = MutableStateFlow(0L)
    val frameCount: StateFlow<Long> = _frameCount.asStateFlow()

    private var lastFrameHash: Long = 0L
    private var emaMotion: Float = 0f
    private var lastFrameTimestamp: Long = 0L

    /**
     * คำนวณ delay ก่อนส่ง frame ถัดไป (milliseconds)
     */
    fun getFrameDelayMs(): Long {
        val fps = _currentFps.value.coerceIn(minFps, maxFps)
        return (1000f / fps).toLong()
    }

    /**
     * Suspend จนกว่าจะถึงเวลาส่ง frame ถัดไป
     */
    suspend fun awaitNextFrame() {
        val elapsed = Clock.System.now().toEpochMilliseconds() - lastFrameTimestamp
        val required = getFrameDelayMs()
        if (elapsed < required) {
            delay(required - elapsed)
        }
    }

    /**
     * อัปเดต motion score จาก frame ใหม่ และปรับ FPS
     *
     * ใช้ lightweight hash comparison แทน full pixel diff
     * เพื่อประหยัด CPU บนมือถือ
     *
     * @param jpegBytes raw JPEG bytes ของ frame ปัจจุบัน
     */
    fun onNewFrame(jpegBytes: ByteArray) {
        _frameCount.value++
        val now = Clock.System.now().toEpochMilliseconds()
        lastFrameTimestamp = now

        // ── Motion Detection ด้วย lightweight hash ──
        val currentHash = computeLightweightHash(jpegBytes)
        val rawMotion = if (lastFrameHash == 0L) 0f
                        else computeMotionScore(lastFrameHash, currentHash)
        lastFrameHash = currentHash

        // ── EMA Smoothing ──
        emaMotion = emaAlpha * rawMotion + (1f - emaAlpha) * emaMotion
        _motionScore.value = emaMotion

        // ── Adaptive FPS ──
        val targetFps = when {
            emaMotion < motionThresholdLow  -> minFps       // นิ่ง → ช้าลง
            emaMotion > motionThresholdHigh -> maxFps       // เคลื่อนไหวมาก → เร็วขึ้น
            else -> {
                // Linear interpolation ระหว่าง min-max
                val ratio = (emaMotion - motionThresholdLow) / (motionThresholdHigh - motionThresholdLow)
                minFps + ratio * (maxFps - minFps)
            }
        }

        // Smooth transition (ไม่กระโดดทันที)
        val smoothedFps = _currentFps.value * 0.7f + targetFps * 0.3f
        _currentFps.value = smoothedFps.coerceIn(minFps, maxFps)

        if (_frameCount.value % 10 == 0L) {
            logDebug("AdaptiveFPS",
                "FPS=%.1f motion=%.3f raw=%.3f frames=%d".format(
                    _currentFps.value, emaMotion, rawMotion, _frameCount.value
                )
            )
        }
    }

    /**
     * รีเซ็ตสถานะ (เมื่อเปิดกล้องใหม่)
     */
    fun reset() {
        _currentFps.value = defaultFps
        _motionScore.value = 0f
        _frameCount.value = 0
        lastFrameHash = 0L
        emaMotion = 0f
        lastFrameTimestamp = 0L
    }

    /**
     * บังคับตั้ง FPS (override adaptive mode)
     */
    fun setFixedFps(fps: Float) {
        _currentFps.value = fps.coerceIn(minFps, maxFps)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Lightweight Motion Detection
    //
    // ใช้ sampling-based hash แทน full pixel comparison
    // สุ่มตัวอย่าง bytes จาก JPEG data → คำนวณ hash
    // เปรียบเทียบ hamming distance ระหว่าง hash เก่าและใหม่
    // ═══════════════════════════════════════════════════════════════════

    private fun computeLightweightHash(data: ByteArray): Long {
        if (data.size < 64) return data.contentHashCode().toLong()

        // Sample 32 bytes ที่กระจายทั่ว frame
        var hash = 0L
        val step = data.size / 32
        for (i in 0 until 32) {
            val idx = (i * step).coerceAtMost(data.size - 1)
            hash = hash xor ((data[idx].toLong() and 0xFF) shl (i % 8 * 8))
        }
        return hash
    }

    private fun computeMotionScore(hash1: Long, hash2: Long): Float {
        // Hamming distance normalized to 0.0 - 1.0
        val xor = hash1 xor hash2
        val bitCount = countBits(xor)
        return bitCount.toFloat() / 64f  // 64 bits max
    }

    private fun countBits(value: Long): Int {
        var v = value
        var count = 0
        while (v != 0L) {
            count += (v and 1L).toInt()
            v = v ushr 1
        }
        return count
    }
}

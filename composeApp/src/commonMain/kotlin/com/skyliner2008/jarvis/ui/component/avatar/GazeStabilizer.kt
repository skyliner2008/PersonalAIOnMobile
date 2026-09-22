package com.skyliner2008.jarvis.ui.component.avatar

import kotlinx.datetime.Clock
import kotlin.math.abs

/**
 * GazeStabilizer — ตัวกรองพิกัดสายตาและใบหน้า (Low-Pass Filter & EMA)
 *
 * แก้ปัญหาพิกัดดิบจาก Face Detector สั่นกระตุกเฟรมต่อเฟรม (±3% jitter):
 * 1. Adaptive Exponential Moving Average: หากขยับเล็กน้อย (<3.5%) จะกรองอย่างหนักให้ภาพนิ่งสนิท
 *    หากขยับจริง (>4%) จะตอบสนองอย่างรวดเร็วเป็นธรรมชาติ
 * 2. Face Distance / Scale Factor: คำนวณขนาดใบหน้าเทียบกับจอ หากเข้าใกล้กล้อง avatar จะตาโตขึ้นเล็กน้อย
 * 3. Face Presence Tracker: ติดตามเวลาที่ใบหน้าหายไป เพื่อเปลี่ยนเข้าสู่ BORED หรือ SLEEPING อัตโนมัติ
 */
class GazeStabilizer(
    private val jitterThreshold: Float = 0.035f,
    private val idleBoredTimeoutMs: Long = 45_000L,
    private val idleSleepTimeoutMs: Long = 180_000L
) {
    var smoothedX: Float = 0f
        private set
    var smoothedY: Float = 0f
        private set
    var faceScaleFactor: Float = 1f
        private set

    private var lastFaceSeenTimeMs: Long = 0L
    private var isFacePresent: Boolean = false
    private var lastAbsenceEmotionNotified: AvatarEmotion? = null

    /**
     * อัปเดตพิกัดด้วยเฟรมใหม่พร้อมกรองสัญญาณรบกวน
     */
    fun update(
        rawNormX: Float,
        rawNormY: Float,
        faceWidthRatio: Float = 0.25f,
        currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()
    ): Pair<Float, Float> {
        isFacePresent = true
        lastFaceSeenTimeMs = currentTimeMs
        lastAbsenceEmotionNotified = null

        val diffX = abs(rawNormX - smoothedX)
        val diffY = abs(rawNormY - smoothedY)

        // Adaptive alpha: small movement -> heavy filter (low alpha), large movement -> fast response
        val alphaX = when {
            diffX < jitterThreshold -> 0.08f
            diffX < 0.15f -> 0.25f
            else -> 0.45f
        }

        val alphaY = when {
            diffY < jitterThreshold -> 0.08f
            diffY < 0.15f -> 0.25f
            else -> 0.45f
        }

        smoothedX += (rawNormX - smoothedX) * alphaX
        smoothedY += (rawNormY - smoothedY) * alphaY

        // Clamp to [-1f, 1f]
        smoothedX = smoothedX.coerceIn(-1f, 1f)
        smoothedY = smoothedY.coerceIn(-1f, 1f)

        // Distance scaling: curious reaction when user moves close to camera (>0.38 ratio)
        val targetScale = if (faceWidthRatio > 0.38f) {
            1f + ((faceWidthRatio - 0.38f) * 0.45f).coerceIn(0f, 0.22f)
        } else {
            1f
        }
        faceScaleFactor += (targetScale - faceScaleFactor) * 0.15f

        return Pair(smoothedX, smoothedY)
    }

    /**
     * เรียกเมื่อไม่มีการตรวจพบใบหน้าในเฟรม
     * คืนค่า AvatarEmotion ที่ควรปรับเปลี่ยน (BORED หรือ SLEEPING) หรือ null หากยังไม่ถึงเวลา
     * (One-shot Edge Triggered: จะแจ้งเตือนครั้งเดียวในแต่ละสถานะ ไม่ส่งซ้ำทุกเฟรม)
     */
    fun onNoFace(currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()): AvatarEmotion? {
        if (!isFacePresent || lastFaceSeenTimeMs == 0L) {
            // คืนค่าจุดมองกลับสู่กึ่งกลางอย่างนุ่มนวล
            smoothedX += (0f - smoothedX) * 0.08f
            smoothedY += (0f - smoothedY) * 0.08f
            faceScaleFactor += (1f - faceScaleFactor) * 0.08f
            return null
        }

        val elapsed = currentTimeMs - lastFaceSeenTimeMs

        // ค่อยๆ คืนสายตากลับสู่ศูนย์กลาง
        smoothedX += (0f - smoothedX) * 0.06f
        smoothedY += (0f - smoothedY) * 0.06f
        faceScaleFactor += (1f - faceScaleFactor) * 0.06f

        return when {
            elapsed >= idleSleepTimeoutMs -> {
                if (lastAbsenceEmotionNotified != AvatarEmotion.SLEEPING) {
                    lastAbsenceEmotionNotified = AvatarEmotion.SLEEPING
                    AvatarEmotion.SLEEPING
                } else {
                    null
                }
            }
            elapsed >= idleBoredTimeoutMs -> {
                if (lastAbsenceEmotionNotified == null) {
                    lastAbsenceEmotionNotified = AvatarEmotion.BORED
                    AvatarEmotion.BORED
                } else {
                    null
                }
            }
            else -> null
        }
    }

    /**
     * รีเซ็ต Absence Timer เมื่อมีการสัมผัส โต้ตอบ หรือพูดคุย
     */
    fun resetAbsenceTimer(currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()) {
        lastFaceSeenTimeMs = currentTimeMs
        lastAbsenceEmotionNotified = null
    }

    /**
     * รีเซ็ตสถานะกลับสู่ค่าเริ่มต้น
     */
    fun reset() {
        smoothedX = 0f
        smoothedY = 0f
        faceScaleFactor = 1f
        lastFaceSeenTimeMs = 0L
        isFacePresent = false
        lastAbsenceEmotionNotified = null
    }
}

package com.skyliner2008.jarvis.pet

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * PetFaceProfile — ข้อมูลโปรไฟล์ใบหน้าที่ลงทะเบียนจดจำไว้ (1 ใน 5 สล็อต)
 *
 * @param slotIndex ดัชนีสล็อต 0..4
 * @param name ชื่อเรียกของเจ้าของใบหน้า เช่น "บอส", "คุณแม่", "เพื่อน"
 * @param isEnrolled มีการลงทะเบียนใบหน้าแล้วหรือไม่
 * @param landmarkRatios สัดส่วนเรขาคณิตใบหน้า (Normalized Face Geometry Ratios):
 *   [0] = eyeDistance / faceWidth
 *   [1] = eyeDistance / noseToMouthDistance
 *   [2] = eyeToNoseDistance / noseToMouthDistance
 *   [3] = faceWidth / faceHeight
 * @param enrolledAt เวลาที่บันทึก (มิลลิวินาที)
 */
@Serializable
data class PetFaceProfile(
    val slotIndex: Int,
    val name: String,
    val isEnrolled: Boolean = false,
    val landmarkRatios: List<Float> = emptyList(),
    val enrolledAt: Long = 0L
) {
    companion object {
        /** ค่า Threshold สูงสุดของ Euclidean Distance ที่ถือว่าเป็นคนเดียวกัน */
        const val MATCH_THRESHOLD = 0.22f

        fun createDefaultSlots(): List<PetFaceProfile> {
            return listOf(
                PetFaceProfile(slotIndex = 0, name = "บอส (ฉันเอง)", isEnrolled = false),
                PetFaceProfile(slotIndex = 1, name = "เพื่อน 1", isEnrolled = false),
                PetFaceProfile(slotIndex = 2, name = "เพื่อน 2", isEnrolled = false),
                PetFaceProfile(slotIndex = 3, name = "เพื่อน 3", isEnrolled = false),
                PetFaceProfile(slotIndex = 4, name = "เพื่อน 4", isEnrolled = false)
            )
        }
    }

    /**
     * คำนวณ Euclidean distance กับ features ที่ส่งเข้ามา
     * ยิ่งค่าน้อย แปลว่ายิ่งเหมือนกันมาก
     */
    fun calculateDistance(targetFeatures: List<Float>): Float {
        if (!isEnrolled || landmarkRatios.isEmpty() || targetFeatures.isEmpty()) return Float.MAX_VALUE
        val count = minOf(landmarkRatios.size, targetFeatures.size)
        var sumSquares = 0f
        for (i in 0 until count) {
            val diff = landmarkRatios[i] - targetFeatures[i]
            sumSquares += diff * diff
        }
        return sqrt(sumSquares / count)
    }

    fun isMatch(targetFeatures: List<Float>): Boolean {
        return calculateDistance(targetFeatures) <= MATCH_THRESHOLD
    }
}

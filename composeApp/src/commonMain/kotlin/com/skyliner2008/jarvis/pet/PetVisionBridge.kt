package com.skyliner2008.jarvis.pet

import com.skyliner2008.jarvis.camera.DetectedObject
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion

/**
 * PetVisionProcessor — Interface สำหรับประมวลผลเฟรมภาพเพื่อตรวจจับใบหน้า สายตา และการแสดงอารมณ์
 */
interface PetVisionProcessor {
    /**
     * ประมวลผล JPEG frame bytes สำหรับตรวจจับใบหน้า
     */
    fun processFrame(rawBytes: ByteArray, isFrontCamera: Boolean = true)

    /**
     * คืนทรัพยากรเมื่อเลิกใช้งาน
     */
    fun release()
}

/**
 * PetVisionBridge — Cross-platform bridge สำหรับเชื่อมโยงกับ on-device ML Kit บน Android
 */
object PetVisionBridge {
    /**
     * Callback ส่งรายการวัตถุ/ใบหน้า (พร้อม Bounding Box) ที่ตรวจจับได้ไปยัง UI Overlay
     */
    var onObjectsDetected: ((List<DetectedObject>) -> Unit)? = null

    /**
     * Callback สำหรับเปิด/ปิด Camera Eye PIP preview
     */
    var onEyeOpenRequest: ((Boolean) -> Unit)? = null

    /**
     * Callback ตรวจจับท่าทางมือ (Hand Gestures) เช่น High-Five, OK, V-Sign ฯลฯ
     */
    var onHandGestureDetected: ((HandGesture) -> Unit)? = null

    /**
     * Callback เมื่อจับคู่ใบหน้าที่เห็นกับสล็อตโปรไฟล์ใบหน้าได้สำเร็จ
     */
    var onFaceProfileMatched: ((name: String, slotIndex: Int) -> Unit)? = null

    /**
     * Callback ส่ง Normalized Face Landmark Ratios ปัจจุบัน (สำหรับใช้ลงทะเบียนสล็อตใบหน้า)
     */
    var onCurrentFaceLandmarks: ((List<Float>) -> Unit)? = null

    /**
     * Provider ดึงรายชื่อ 5 Face Profiles ที่ลงทะเบียนไว้ เพื่อให้ Vision Engine ใช้เปรียบเทียบ
     */
    var registeredProfilesProvider: (() -> List<PetFaceProfile>)? = null

    /**
     * Callback แจ้งเตือนเมื่อต้องการเปิด/ปิดการสตรีมภาพกล้องเข้าสู่ AI session (Gemini Live)
     * สอดประสานกับ UI ตาแสกน ไม่ให้มีเฟรมภาพรั่วไหลเมื่อพับตาลง
     */
    var onAiVisionStreamToggle: ((Boolean) -> Unit)? = null

    /**
     * สั่งเปิดหรือปิดสายตาสัตว์เลี้ยง (Camera Preview Window)
     */
    fun requestEyeOpen(open: Boolean) {
        onEyeOpenRequest?.invoke(open)
        onAiVisionStreamToggle?.invoke(open)
    }

    var processorFactory: ((
        onGazeDetected: (normX: Float, normY: Float) -> Unit,
        onIntruderDetected: () -> Unit,
        onCopycatSuccess: () -> Unit,
        getCopycatTarget: () -> AvatarEmotion?,
        isSentryActive: () -> Boolean
    ) -> PetVisionProcessor)? = null

    fun createProcessor(
        onGazeDetected: (normX: Float, normY: Float) -> Unit,
        onIntruderDetected: () -> Unit,
        onCopycatSuccess: () -> Unit,
        getCopycatTarget: () -> AvatarEmotion?,
        isSentryActive: () -> Boolean
    ): PetVisionProcessor? {
        return processorFactory?.invoke(
            onGazeDetected,
            onIntruderDetected,
            onCopycatSuccess,
            getCopycatTarget,
            isSentryActive
        )
    }
}

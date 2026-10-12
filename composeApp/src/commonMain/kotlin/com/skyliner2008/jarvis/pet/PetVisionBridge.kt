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

    /** whether a frame now would be processed — the camera skips converting frames nobody takes */
    fun wantsFrame(): Boolean = true

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
     * Callback เมื่อจับคู่ใบหน้าที่เห็นกับสล็อตโปรไฟล์ใบหน้าได้สำเร็จ (หรือ null เมื่อไม่พบใบหน้า)
     */
    var onFaceProfileMatched: ((name: String?, slotIndex: Int) -> Unit)? = null

    /**
     * Callback ส่ง Normalized Face Landmark Ratios ปัจจุบัน (สำหรับใช้ลงทะเบียนสล็อตใบหน้า)
     */
    var onCurrentFaceLandmarks: ((List<Float>) -> Unit)? = null

    /**
     * Callback ส่งรายละเอียดไบโอเมตริกซ์ของใบหน้าแบบสด (สำหรับสแกนเนอร์ลงทะเบียนใบหน้าระดับสูง)
     */
    var onFaceBiometrics: ((FaceBiometricDetails) -> Unit)? = null

    /**
     * The vision test screen ([VisionTestScreen]): every frame's faces, hands and things before and after filtering.
     * Set only while the screen is open — the detector builds the frame only then.
     */
    var onVisionDebug: ((VisionDebugFrame) -> Unit)? = null

    /** whether the phone can recognize faces by embedding ([FaceRecognition]) — false = landmark-ratio matching */
    var faceEmbeddingAvailable: () -> Boolean = { false }

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
     * Callback ส่งสเกลระยะห่างของใบหน้า (Face Distance Scale Factor) สำหรับปรับขนาดดวงตาแบบ 2.5D
     */
    var onFaceDistanceDetected: ((Float) -> Unit)? = null

    /**
     * Callback แจ้งเตือนเมื่อใบหน้าหายไปจากระยะสายตาเกินเวลาที่กำหนด (>45s -> BORED, >180s -> SLEEPING)
     */
    var onFaceAbsenceTimeout: ((AvatarEmotion) -> Unit)? = null

    /**
     * Callback รีเซ็ต Absence Timer เมื่อมีการปฏิสัมพันธ์ (Touch, Voice, Care)
     */
    var onResetAbsence: (() -> Unit)? = null

    fun resetAbsenceTimer() {
        onResetAbsence?.invoke()
    }

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

/**
 * รายละเอียดไบโอเมตริกซ์ของใบหน้าแบบเรียลไทม์ (สำหรับสแกนเนอร์ลงทะเบียนและตรวจสอบจุดต่างของใบหน้า)
 */
data class FaceBiometricDetails(
    val isFaceDetected: Boolean = false,
    val isCentered: Boolean = false,
    val hasEyesDetected: Boolean = false,
    val hasNoseDetected: Boolean = false,
    val hasMouthDetected: Boolean = false,
    val landmarkRatios: List<Float> = emptyList(),
    val yawAngle: Float = 0f,
    val rollAngle: Float = 0f,
    val faceBounds: com.skyliner2008.jarvis.camera.BoundingBox? = null,
    /** the face's latest SFace embedding (normalized) and when it was taken — empty without the model */
    val embedding: List<Float> = emptyList(),
    val embeddingAtMs: Long = 0L,
    /** the head's yaw in the frame the embedding was taken from */
    val embeddingYaw: Float = 0f,
    /** the face sits in the middle of the frame, whichever way it is turned (isCentered also wants it straight) */
    val isPlaced: Boolean = false
)

/**
 * One processed frame for the vision test screen. Boxes and points are normalized to the frame as the screen shows it
 * (the front camera mirrored), the same as the pet's detection list.
 */
data class VisionDebugFrame(
    val frameWidth: Int,
    val frameHeight: Int,
    /** every face, every hand and every thing the models proposed — kept or not */
    val raw: List<com.skyliner2008.jarvis.camera.DetectedObject>,
    /** what the pet uses: after [PetVisionTargetTracker] (one face, one hand, one thing) */
    val tracked: List<com.skyliner2008.jarvis.camera.DetectedObject>,
    /** each hand's 21 points, x0, y0, x1, y1 … normalized */
    val handPoints: List<FloatArray>,
    /** one line per model, what it saw and decided */
    val lines: List<String>,
    /** frames processed per second */
    val fps: Float
)

package com.skyliner2008.jarvis.pet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.objects.DetectedObject as MlKitDetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.skyliner2008.jarvis.camera.BoundingBox
import com.skyliner2008.jarvis.camera.DetectedObject
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import kotlin.math.abs
import kotlin.math.hypot

/**
 * PetVisionDetector — ตรวจจับใบหน้า สายตา วัตถุ และมือ/นิ้ว ด้วย Google ML Kit & Computer Vision
 * สำหรับโหมดสัตว์เลี้ยง (Virtual Desk Pet):
 *
 * 1. Face & Expression Detection: ตรวจจับใบหน้า รอยยิ้ม และการขยิบตา
 * 2. Multi-Object Detection: ตรวจจับสิ่งของรอบตัวด้วย ML Kit Object Detection (Stream mode)
 * 3. Hand & Finger Detection: ตรวจจับมือ นิ้ว และท่าทาง (Palm, Finger Point, Peace Sign)
 * 4. Gaze Tracking: ส่งพิกัดใบหน้าให้ตาสัตว์เลี้ยงมองตาม
 * 5. Desk Sentry: แจ้งเตือนเมื่อมีคนแปลกหน้าเข้าใกล้โต๊ะ
 * 6. Copycat Game: ตรวจสอบความถูกต้องของการเลียนแบบหน้า
 */
class PetVisionDetector(
    private val onGazeDetected: (normX: Float, normY: Float) -> Unit,
    private val onIntruderDetected: () -> Unit,
    private val onCopycatSuccess: () -> Unit,
    private val getCopycatTarget: () -> AvatarEmotion?,
    private val isSentryActive: () -> Boolean,
    private val onObjectsDetected: ((List<DetectedObject>) -> Unit)? = null
) : PetVisionProcessor {

    companion object {
        private const val TAG = "PetVisionDetector"
        private const val SMILE_THRESHOLD = 0.65f
        private const val WINK_DIFF_THRESHOLD = 0.50f

        /**
         * เรียกครั้งเดียวตอนเริ่มต้นแอปเพื่อลงทะเบียน processorFactory กับ PetVisionBridge
         */
        fun installBridge() {
            PetVisionBridge.processorFactory = { onGaze, onIntruder, onCopycat, getTarget, isSentry ->
                PetVisionDetector(onGaze, onIntruder, onCopycat, getTarget, isSentry)
            }
            logDebug(TAG, "PetVisionBridge installed with ML Kit Face & Object Detection")
        }
    }

    private val faceOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setMinFaceSize(0.15f)
        .build()

    private val faceDetector: FaceDetector = FaceDetection.getClient(faceOptions)

    private val objectOptions = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableMultipleObjects()
        .enableClassification()
        .build()

    private val objectDetector: ObjectDetector = ObjectDetection.getClient(objectOptions)
    private val targetTracker = PetVisionTargetTracker()

    private var isProcessing = false
    private var lastProcessTime = 0L
    private var lastIntruderAlertTime = 0L
    private var lastGazeLogTime = 0L
    private var lastDetectionLogTime = 0L
    private var hasLoggedFirstFrame = false
    private var lastFaceSeenTime = 0L
    private var isGazeActive = false

    override fun processFrame(rawBytes: ByteArray, isFrontCamera: Boolean) {
        val now = System.currentTimeMillis()
        // Throttle processing to max ~12 fps (80ms) to conserve CPU/battery
        if (isProcessing || now - lastProcessTime < 80L) return

        if (!hasLoggedFirstFrame) {
            hasLoggedFirstFrame = true
            logDebug(TAG, "📸 First camera frame received for PetVision (bytes=${rawBytes.size}, isFrontCamera=$isFrontCamera)")
        }

        try {
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return
            processBitmap(bitmap, isFrontCamera = isFrontCamera, isAlreadyMirrored = isFrontCamera)
        } catch (e: Exception) {
            logError(TAG, "Error decoding rawBytes for PetVision: ${e.message}")
        }
    }

    fun processImageProxy(imageProxy: ImageProxy, isFrontCamera: Boolean = true) {
        val now = System.currentTimeMillis()
        if (isProcessing || now - lastProcessTime < 80L) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessing = true
        lastProcessTime = now

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        val faceTask = faceDetector.process(inputImage)
        val objectTask = objectDetector.process(inputImage)

        Tasks.whenAllComplete(faceTask, objectTask)
            .addOnSuccessListener {
                try {
                    val faces = if (faceTask.isSuccessful) faceTask.result ?: emptyList() else emptyList()
                    val mlKitObjects = if (objectTask.isSuccessful) objectTask.result ?: emptyList() else emptyList()
                    handleDetections(
                        faces = faces,
                        mlKitObjects = mlKitObjects,
                        handObjects = emptyList(),
                        imageWidth = inputImage.width,
                        imageHeight = inputImage.height,
                        isFrontCamera = isFrontCamera,
                        isAlreadyMirrored = false
                    )
                } catch (e: Exception) {
                    logError(TAG, "Error handling detections from proxy: ${e.message}", e)
                }
            }
            .addOnFailureListener { e ->
                logError(TAG, "Detection tasks failed on proxy: ${e.message}", e)
            }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    fun processBitmap(bitmap: Bitmap, isFrontCamera: Boolean = true, isAlreadyMirrored: Boolean = false) {
        isProcessing = true
        lastProcessTime = System.currentTimeMillis()

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val faceTask = faceDetector.process(inputImage)
        val objectTask = objectDetector.process(inputImage)

        Tasks.whenAllComplete(faceTask, objectTask)
            .addOnSuccessListener {
                try {
                    val faces = if (faceTask.isSuccessful) faceTask.result ?: emptyList() else emptyList()
                    val mlKitObjects = if (objectTask.isSuccessful) objectTask.result ?: emptyList() else emptyList()
                    val handObjects = detectHandRegions(bitmap, faces, bitmap.width, bitmap.height, isFrontCamera, isAlreadyMirrored)
                    handleDetections(
                        faces = faces,
                        mlKitObjects = mlKitObjects,
                        handObjects = handObjects,
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        isFrontCamera = isFrontCamera,
                        isAlreadyMirrored = isAlreadyMirrored
                    )
                } catch (e: Exception) {
                    logError(TAG, "Error handling detections: ${e.message}", e)
                }
            }
            .addOnFailureListener { e ->
                logError(TAG, "Detection tasks failed: ${e.message}", e)
            }
            .addOnCompleteListener {
                isProcessing = false
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
    }

    /**
     * ตรวจจับมือและนิ้วโดยวิเคราะห์กลุ่มพิกเซลสีผิว (Skin-tone clustering) นอกกรอบใบหน้า
     */
    private fun detectHandRegions(
        bitmap: Bitmap,
        faces: List<Face>,
        width: Int,
        height: Int,
        isFrontCamera: Boolean,
        isAlreadyMirrored: Boolean
    ): List<DetectedObject> {
        if (bitmap.isRecycled) return emptyList()

        val stepX = (width / 55).coerceAtLeast(3)
        val stepY = (height / 55).coerceAtLeast(3)

        // Expanded face rects to exclude face / hair AND neck / upper chest below chin
        val faceBounds = faces.map { face ->
            val b = face.boundingBox
            val padX = (b.width() * 0.25f).toInt()
            val padTop = (b.height() * 0.35f).toInt()
            val padBottom = (b.height() * 0.85f).toInt() // Covers neck down to collar
            android.graphics.Rect(
                (b.left - padX).coerceAtLeast(0),
                (b.top - padTop).coerceAtLeast(0),
                (b.right + padX).coerceAtMost(width),
                (b.bottom + padBottom).coerceAtMost(height)
            )
        }

        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        var skinCount = 0

        val hsv = FloatArray(3)

        // Sample grid across image
        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                // Skip pixels inside face and neck areas
                if (faceBounds.any { it.contains(x, y) }) continue

                val pixel = try {
                    bitmap.getPixel(x, y)
                } catch (_: Exception) {
                    continue
                }

                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Fast skin tone classification in RGB + HSV
                if (r > 65 && g > 40 && b > 20 && (r - g) > 10 && (r - b) > 12 && r > g && g >= b) {
                    AndroidColor.RGBToHSV(r, g, b, hsv)
                    val h = hsv[0]
                    val s = hsv[1]
                    val v = hsv[2]

                    val isSkinHue = (h in 0f..45f || h in 340f..360f) && (s in 0.15f..0.78f) && (v in 0.20f..0.95f)
                    if (isSkinHue) {
                        skinCount++
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
        }

        // Need at least ~25 skin sampled points to form a valid hand cluster
        if (skinCount < 25 || maxX <= minX || maxY <= minY) {
            handleGestureFrame(HandGesture.NONE, now = System.currentTimeMillis())
            return emptyList()
        }

        val clusterWidth = maxX - minX
        val clusterHeight = maxY - minY

        // Hand should have reasonable dimensions (at least ~8% of frame, max 80% to avoid wall false positives)
        if (clusterWidth < width * 0.08f || clusterHeight < height * 0.08f ||
            clusterWidth > width * 0.80f || clusterHeight > height * 0.80f
        ) {
            handleGestureFrame(HandGesture.NONE, now = System.currentTimeMillis())
            return emptyList()
        }

        // Check if hand is simply holding the phone at the bottom edge
        val isHoldingPhone = (minY > height * 0.65f) || (maxY >= height - (stepY * 2) && clusterHeight < height * 0.30f)

        // Check top 35% of hand cluster to classify gesture / fingers
        val topYCutoff = minY + (clusterHeight * 0.35f).toInt()
        var topSkinCount = 0
        var topMinX = width
        var topMaxX = 0

        for (y in minY until topYCutoff step stepY) {
            for (x in minX..maxX step stepX) {
                if (faceBounds.any { it.contains(x, y) }) continue
                val pixel = try { bitmap.getPixel(x, y) } catch (_: Exception) { continue }
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r > 65 && g > 40 && b > 20 && (r - g) > 10 && (r - b) > 12) {
                    topSkinCount++
                    if (x < topMinX) topMinX = x
                    if (x > topMaxX) topMaxX = x
                }
            }
        }

        val topSpread = if (topMaxX > topMinX) (topMaxX - topMinX).toFloat() / clusterWidth.toFloat() else 0f
        val topRatio = if (skinCount > 0) topSkinCount.toFloat() / skinCount.toFloat() else 0f

        // Check bottom 25% of hand cluster to detect downward thumb
        val bottomYCutoff = maxY - (clusterHeight * 0.25f).toInt()
        var bottomSkinCount = 0
        var bottomMinX = width
        var bottomMaxX = 0
        for (y in bottomYCutoff..maxY step stepY) {
            for (x in minX..maxX step stepX) {
                if (faceBounds.any { it.contains(x, y) }) continue
                val pixel = try { bitmap.getPixel(x, y) } catch (_: Exception) { continue }
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r > 65 && g > 40 && b > 20 && (r - g) > 10 && (r - b) > 12) {
                    bottomSkinCount++
                    if (x < bottomMinX) bottomMinX = x
                    if (x > bottomMaxX) bottomMaxX = x
                }
            }
        }
        val bottomSpread = if (bottomMaxX > bottomMinX) (bottomMaxX - bottomMinX).toFloat() / clusterWidth.toFloat() else 0f
        val bottomRatio = if (skinCount > 0) bottomSkinCount.toFloat() / skinCount.toFloat() else 0f

        val now = System.currentTimeMillis()
        val currentCentroidX = (minX + maxX) / 2f
        val handDeltaX = if (lastHandCentroidTime > 0L && (now - lastHandCentroidTime < 700L)) {
            abs(currentCentroidX - lastHandCentroidX)
        } else 0f
        lastHandCentroidX = currentCentroidX
        lastHandCentroidTime = now

        val isWaving = handDeltaX > (width * 0.10f)

        var detectedGesture = HandGesture.NONE
        var label = "Hand ✋"
        var color = "#FF9100"

        // Only evaluate gestures if hand is intentionally raised (not resting / holding phone)
        if (!isHoldingPhone && minY < height * 0.60f && skinCount >= 28) {
            when {
                // 1. BYE — โบกมือไปมา (waving hand with horizontal movement)
                isWaving && topSpread > 0.55f && clusterHeight >= clusterWidth * 0.75f -> {
                    detectedGesture = HandGesture.BYE
                    label = "${HandGesture.BYE.icon} ${HandGesture.BYE.displayNameTh}"
                    color = "#00E5FF"
                }
                // 2. HIGH-FIVE — ฝ่ามือเปิดกว้างเต็มที่ ชูนิ้วทั้ง 5 (upright open palm)
                topSpread > 0.75f && topRatio in 0.22f..0.45f &&
                clusterHeight.toFloat() in (clusterWidth * 0.85f)..(clusterWidth * 1.85f) &&
                skinCount >= 35 && minY < height * 0.55f -> {
                    detectedGesture = HandGesture.HIGH_FIVE
                    label = "${HandGesture.HIGH_FIVE.icon} ${HandGesture.HIGH_FIVE.displayNameTh}"
                    color = "#00E676" // Bright Green
                }
                // 3. V SIGN — ชู 2 นิ้วแยกจากกัน (Peace / Victory)
                topSpread in 0.35f..0.62f && topRatio in 0.14f..0.32f &&
                clusterHeight > clusterWidth * 1.15f -> {
                    detectedGesture = HandGesture.V_SIGN
                    label = "${HandGesture.V_SIGN.icon} ${HandGesture.V_SIGN.displayNameTh}"
                    color = "#FFD700" // Gold
                }
                // 4. NO — ส่ายหรือชูนิ้วเดียวแคบๆ (Narrow single index finger)
                topSpread in 0.05f..0.24f && topRatio in 0.08f..0.24f &&
                clusterHeight > clusterWidth * 1.55f && clusterWidth < width * 0.25f -> {
                    detectedGesture = HandGesture.NO
                    label = "${HandGesture.NO.icon} ${HandGesture.NO.displayNameTh}"
                    color = "#FF9100"
                }
                // 5. THUMBS UP — นิ้วโป้งยกชี้ขึ้นข้างเดียว (ด้านบนแคบปูดขึ้น ด้านล่างกว้าง)
                topSpread in 0.16f..0.38f && clusterHeight > clusterWidth * 1.15f &&
                topRatio < 0.20f && bottomSpread > 0.60f -> {
                    detectedGesture = HandGesture.THUMBS_UP
                    label = "${HandGesture.THUMBS_UP.icon} ${HandGesture.THUMBS_UP.displayNameTh}"
                    color = "#76FF03"
                }
                // 6. THUMBS DOWN — นิ้วโป้งคว่ำลงล่าง
                bottomSpread in 0.16f..0.38f && clusterHeight > clusterWidth * 1.15f &&
                bottomRatio < 0.20f && topSpread > 0.60f -> {
                    detectedGesture = HandGesture.THUMBS_DOWN
                    label = "${HandGesture.THUMBS_DOWN.icon} ${HandGesture.THUMBS_DOWN.displayNameTh}"
                    color = "#FF5252"
                }
                // 7. OK — นิ้วโป้งชนนิ้วชี้เป็นวงกลม
                topSpread in 0.40f..0.62f && topRatio in 0.18f..0.32f &&
                clusterWidth.toFloat() in (clusterHeight * 0.80f)..(clusterHeight * 1.25f) &&
                skinCount in 25..120 -> {
                    detectedGesture = HandGesture.OK
                    label = "${HandGesture.OK.icon} ${HandGesture.OK.displayNameTh}"
                    color = "#E040FB"
                }
                else -> {
                    detectedGesture = HandGesture.NONE
                    label = "Hand ✋"
                    color = "#FF9100"
                }
            }
        }

        // Process gesture confirmation debounce & edge latch
        handleGestureFrame(detectedGesture, now)

        // Normalize bounding box coordinates
        val nLeft = if (isFrontCamera && !isAlreadyMirrored) {
            (1f - (maxX.toFloat() / width.toFloat())).coerceIn(0f, 1f)
        } else {
            (minX.toFloat() / width.toFloat()).coerceIn(0f, 1f)
        }
        val nTop = (minY.toFloat() / height.toFloat()).coerceIn(0f, 1f)
        val nW = (clusterWidth.toFloat() / width.toFloat()).coerceIn(0.01f, 1f)
        val nH = (clusterHeight.toFloat() / height.toFloat()).coerceIn(0.01f, 1f)

        return listOf(
            DetectedObject(
                label = label,
                confidence = 0.90f,
                boundingBox = BoundingBox(x = nLeft, y = nTop, width = nW, height = nH),
                color = color
            )
        )
    }

    private var lastHandCentroidX: Float = 0f
    private var lastHandCentroidTime: Long = 0L
    private var lastGestureTriggerTime: Long = 0L
    private var candidateGesture: HandGesture = HandGesture.NONE
    private var candidateStreak: Int = 0
    private var activeLatchedGesture: HandGesture = HandGesture.NONE
    private var lastNoneGestureTime: Long = 0L

    /**
     * State machine ป้องกัน gesture รั่ว/ซ้ำซ้อน:
     * 1. Multi-Frame Confirmation: ต้องตรวจจับท่าเดิมติดต่อกันอย่างน้อย 3 เฟรม (~240ms)
     * 2. Edge-Triggered Latch: ยิง Event เพียงครั้งเดียวเมื่อตรวจพบท่าใหม่ ตราบใดที่ยังค้างท่าเดิมจะไม่ยิงซ้ำ
     * 3. Latch Release: ท่าเดิมจะปลดล็อกเมื่อเอามือลง (NONE) ติดต่อกันเกิน 800ms
     * 4. Cooldown Guard: เว้นระยะอย่างน้อย 3.5 วินาทีระหว่างการสั่งงาน
     */
    private fun handleGestureFrame(gesture: HandGesture, now: Long) {
        if (gesture != HandGesture.NONE) {
            lastNoneGestureTime = 0L
            if (gesture == candidateGesture) {
                candidateStreak++
            } else {
                candidateGesture = gesture
                candidateStreak = 1
            }

            if (candidateStreak >= 3) {
                val confirmed = candidateGesture
                val isNewGesture = (confirmed != activeLatchedGesture)
                val isCooldownPassed = (now - lastGestureTriggerTime > 3500L)

                if (isNewGesture && isCooldownPassed) {
                    activeLatchedGesture = confirmed
                    lastGestureTriggerTime = now
                    logDebug(TAG, "🖐️ [HandGesture] Confirmed & Triggered: ${confirmed.name}")
                    PetVisionBridge.onHandGestureDetected?.invoke(confirmed)
                }
            }
        } else {
            candidateGesture = HandGesture.NONE
            candidateStreak = 0

            if (activeLatchedGesture != HandGesture.NONE) {
                if (lastNoneGestureTime == 0L) {
                    lastNoneGestureTime = now
                } else if (now - lastNoneGestureTime > 800L) {
                    logDebug(TAG, "🖐️ [HandGesture] Latch released (hand lowered or neutral)")
                    activeLatchedGesture = HandGesture.NONE
                    lastNoneGestureTime = 0L
                }
            }
        }
    }

    private fun handleDetections(
        faces: List<Face>,
        mlKitObjects: List<MlKitDetectedObject>,
        handObjects: List<DetectedObject>,
        imageWidth: Int,
        imageHeight: Int,
        isFrontCamera: Boolean,
        isAlreadyMirrored: Boolean
    ) {
        val detectedList = mutableListOf<DetectedObject>()

        // ─── 1. Face Recognition & Expression Bounding Boxes ───────────────
        faces.forEach { face ->
            val smileProb = face.smilingProbability ?: -1f
            val leftEyeOpen = face.leftEyeOpenProbability ?: -1f
            val rightEyeOpen = face.rightEyeOpenProbability ?: -1f

            val isWinking = if (leftEyeOpen >= 0f && rightEyeOpen >= 0f) {
                val diff = abs(leftEyeOpen - rightEyeOpen)
                (diff >= WINK_DIFF_THRESHOLD) ||
                (leftEyeOpen > 0.60f && rightEyeOpen < 0.30f) ||
                (rightEyeOpen > 0.60f && leftEyeOpen < 0.30f)
            } else false

            val isSmiling = smileProb >= SMILE_THRESHOLD

            // Extract Landmark geometry for Face Recognition
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
            val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
            val mouth = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position

            val eyeDist = if (leftEye != null && rightEye != null) hypot(leftEye.x - rightEye.x, leftEye.y - rightEye.y) else face.boundingBox.width() * 0.42f
            val noseToMouth = if (nose != null && mouth != null) hypot(nose.x - mouth.x, nose.y - mouth.y) else face.boundingBox.height() * 0.28f
            val eyeToNose = if (leftEye != null && rightEye != null && nose != null) {
                val midX = (leftEye.x + rightEye.x) / 2f
                val midY = (leftEye.y + rightEye.y) / 2f
                hypot(midX - nose.x, midY - nose.y)
            } else face.boundingBox.height() * 0.30f

            val faceW = face.boundingBox.width().toFloat()
            val faceH = face.boundingBox.height().toFloat()

            val landmarkRatios = listOf(
                (eyeDist / faceW.coerceAtLeast(1f)).coerceIn(0.1f, 0.9f),
                (eyeDist / noseToMouth.coerceAtLeast(1f)).coerceIn(0.1f, 3.5f),
                (eyeToNose / noseToMouth.coerceAtLeast(1f)).coerceIn(0.1f, 3.5f),
                (faceW / faceH.coerceAtLeast(1f)).coerceIn(0.4f, 1.8f)
            )

            // Notify bridge of current landmark ratios for enrollment
            PetVisionBridge.onCurrentFaceLandmarks?.invoke(landmarkRatios)

            // Match against registered face profiles
            val profiles = PetVisionBridge.registeredProfilesProvider?.invoke() ?: emptyList()
            val matchedProfile = profiles.firstOrNull { it.isEnrolled && it.isMatch(landmarkRatios) }
            if (matchedProfile != null) {
                PetVisionBridge.onFaceProfileMatched?.invoke(matchedProfile.name, matchedProfile.slotIndex)
            }

            val faceDisplayName = matchedProfile?.name ?: "Boss"

            val label: String
            val color: String
            val conf: Float

            when {
                isWinking -> {
                    label = "$faceDisplayName Wink 😉"
                    color = "#FFD700" // Gold
                    conf = 0.95f
                }
                isSmiling -> {
                    val pct = (smileProb * 100).toInt()
                    label = "$faceDisplayName Smile 😊 $pct%"
                    color = "#FF4081" // Pink
                    conf = smileProb
                }
                else -> {
                    val idStr = face.trackingId?.let { " #$it" } ?: ""
                    label = "$faceDisplayName$idStr"
                    color = if (matchedProfile != null) "#76FF03" else "#00F0FF" // Neon green if recognized
                    conf = 0.90f
                }
            }

            val b = face.boundingBox
            val nLeft = if (isFrontCamera && !isAlreadyMirrored) {
                (1f - (b.right.toFloat() / imageWidth.toFloat())).coerceIn(0f, 1f)
            } else {
                (b.left.toFloat() / imageWidth.toFloat()).coerceIn(0f, 1f)
            }
            val nTop = (b.top.toFloat() / imageHeight.toFloat()).coerceIn(0f, 1f)
            val nW = (b.width().toFloat() / imageWidth.toFloat()).coerceIn(0.01f, 1f)
            val nH = (b.height().toFloat() / imageHeight.toFloat()).coerceIn(0.01f, 1f)

            detectedList.add(
                DetectedObject(
                    label = label,
                    confidence = conf,
                    boundingBox = BoundingBox(x = nLeft, y = nTop, width = nW, height = nH),
                    color = color
                )
            )
        }

        // ─── 2. Hand / Finger Detections ──────────────────────────────────
        detectedList.addAll(handObjects)

        // ─── 3. ML Kit Multi-Object Detections ─────────────────────────────
        mlKitObjects.forEach { obj ->
            val b = obj.boundingBox
            // Filter out objects that heavily overlap with face detections
            val isFaceOverlap = faces.any { face ->
                val fb = face.boundingBox
                val interLeft = maxOf(b.left, fb.left)
                val interTop = maxOf(b.top, fb.top)
                val interRight = minOf(b.right, fb.right)
                val interBottom = minOf(b.bottom, fb.bottom)
                if (interRight > interLeft && interBottom > interTop) {
                    val interArea = (interRight - interLeft) * (interBottom - interTop)
                    val objArea = b.width() * b.height()
                    interArea > (objArea * 0.45f)
                } else false
            }
            if (isFaceOverlap) return@forEach

            val primaryLabel = obj.labels.maxByOrNull { it.confidence }
            val (labelName, color) = when (primaryLabel?.text) {
                "Food" -> "Food / Drink ☕" to "#4CAF50"
                "Home good" -> "Object / Item 📱" to "#00E5FF"
                "Fashion good" -> "Accessory / Item 👓" to "#E040FB"
                "Plant" -> "Plant 🌱" to "#76FF03"
                "Place" -> "Place / Scenery 🏢" to "#40C4FF"
                else -> {
                    val idStr = obj.trackingId?.let { " #$it" } ?: ""
                    "Object$idStr 📦" to "#B388FF"
                }
            }
            val conf = primaryLabel?.confidence ?: 0.75f

            val nLeft = if (isFrontCamera && !isAlreadyMirrored) {
                (1f - (b.right.toFloat() / imageWidth.toFloat())).coerceIn(0f, 1f)
            } else {
                (b.left.toFloat() / imageWidth.toFloat()).coerceIn(0f, 1f)
            }
            val nTop = (b.top.toFloat() / imageHeight.toFloat()).coerceIn(0f, 1f)
            val nW = (b.width().toFloat() / imageWidth.toFloat()).coerceIn(0.01f, 1f)
            val nH = (b.height().toFloat() / imageHeight.toFloat()).coerceIn(0.01f, 1f)

            detectedList.add(
                DetectedObject(
                    label = labelName,
                    confidence = conf,
                    boundingBox = BoundingBox(x = nLeft, y = nTop, width = nW, height = nH),
                    color = color
                )
            )
        }

        // ─── Stabilized Target Tracking & Noise Suppression ───
        val faceBoxes = faces.map { f ->
            val b = f.boundingBox
            val nLeft = if (isFrontCamera && !isAlreadyMirrored) {
                (1f - (b.right.toFloat() / imageWidth.toFloat())).coerceIn(0f, 1f)
            } else {
                (b.left.toFloat() / imageWidth.toFloat()).coerceIn(0f, 1f)
            }
            val nTop = (b.top.toFloat() / imageHeight.toFloat()).coerceIn(0f, 1f)
            val nW = (b.width().toFloat() / imageWidth.toFloat()).coerceIn(0.01f, 1f)
            val nH = (b.height().toFloat() / imageHeight.toFloat()).coerceIn(0.01f, 1f)
            BoundingBox(nLeft, nTop, nW, nH)
        }

        val stabilizedList = targetTracker.processFrame(
            rawObjects = detectedList,
            faces = faceBoxes,
            currentTimeMs = System.currentTimeMillis()
        )

        onObjectsDetected?.invoke(stabilizedList)
        PetVisionBridge.onObjectsDetected?.invoke(stabilizedList)

        if (stabilizedList.isNotEmpty()) {
            val now = System.currentTimeMillis()
            if (now - lastDetectionLogTime > 2000L) {
                lastDetectionLogTime = now
                logDebug(TAG, "🎯 Detections: ${stabilizedList.joinToString(", ") { "${it.label} (${(it.confidence * 100).toInt()}%${if (it.isLocked) " 🔒" else ""})" }}")
            }
        }

        val now = System.currentTimeMillis()
        if (faces.isEmpty()) {
            if (isGazeActive && (now - lastFaceSeenTime > 1000L)) {
                isGazeActive = false
                logDebug(TAG, "👀 Face lost for >1000ms -> Resetting pet gaze to center (0f, 0f)")
                onGazeDetected(0f, 0f)
            }
            return
        }

        lastFaceSeenTime = now
        isGazeActive = true

        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return

        // ─── 4. Desk Sentry: Intruder Detection ───────────────────────────
        if (isSentryActive()) {
            if (now - lastIntruderAlertTime > 5000L) {
                lastIntruderAlertTime = now
                logDebug(TAG, "🚨 Sentry detected intruder face!")
                onIntruderDetected()
            }
        }

        // ─── 5. Gaze / Eye Tracking for 3D Pet Eyes ───────────────────────
        val faceBox = primaryFace.boundingBox
        val faceCenterX = faceBox.centerX().toFloat()
        val faceCenterY = faceBox.centerY().toFloat()

        val rawNormX = if (isFrontCamera && !isAlreadyMirrored) {
            1f - (2f * faceCenterX / imageWidth.toFloat())
        } else {
            (2f * faceCenterX / imageWidth.toFloat()) - 1f
        }
        val rawNormY = (2f * faceCenterY / imageHeight.toFloat()) - 1f

        // Desk / Stand angle calibration:
        // When the phone sits on a desk/stand, the user's face is naturally in the upper 20-35% of the frame (rawNormY ≈ -0.45f to -0.65f).
        // Calibrate neutral elevation to this natural desk viewing line so user sitting in front looks straight ahead (0, 0).
        val deskNeutralBiasY = -0.45f
        val calibratedY = (rawNormY - deskNeutralBiasY) * 1.35f
        val calibratedX = rawNormX * 1.25f

        // Deadzone: if user is sitting naturally in front within deadzone, snap to dead center (0f, 0f)
        val finalNormX = if (abs(calibratedX) < 0.12f) 0f else calibratedX.coerceIn(-1f, 1f)
        val finalNormY = if (abs(calibratedY) < 0.15f) 0f else calibratedY.coerceIn(-1f, 1f)

        if (now - lastGazeLogTime > 2000L) {
            lastGazeLogTime = now
            val gx = (finalNormX * 100).toInt() / 100f
            val gy = (finalNormY * 100).toInt() / 100f
            val rx = (rawNormX * 100).toInt() / 100f
            val ry = (rawNormY * 100).toInt() / 100f
            logDebug(TAG, "👀 Face tracked at (normX=$gx, normY=$gy) [raw:($rx, $ry)] -> Pet gaze updated")
        }

        onGazeDetected(finalNormX, finalNormY)

        // ─── 6. Copycat Mini-Game: Smile & Wink Verification ──────────────
        val currentChallenge = getCopycatTarget()
        if (currentChallenge != null) {
            val smileProb = primaryFace.smilingProbability ?: -1f
            val leftEyeOpen = primaryFace.leftEyeOpenProbability ?: -1f
            val rightEyeOpen = primaryFace.rightEyeOpenProbability ?: -1f

            when (currentChallenge) {
                AvatarEmotion.HAPPY -> {
                    if (smileProb >= SMILE_THRESHOLD) {
                        logDebug(TAG, "🎉 Copycat: User smile detected! ($smileProb)")
                        onCopycatSuccess()
                    }
                }
                AvatarEmotion.WINK -> {
                    if (leftEyeOpen >= 0f && rightEyeOpen >= 0f) {
                        val diff = abs(leftEyeOpen - rightEyeOpen)
                        val oneOpenOneClosed = (leftEyeOpen > 0.60f && rightEyeOpen < 0.30f) ||
                                               (rightEyeOpen > 0.60f && leftEyeOpen < 0.30f)
                        if (diff >= WINK_DIFF_THRESHOLD || oneOpenOneClosed) {
                            logDebug(TAG, "🎉 Copycat: User wink detected! (L:$leftEyeOpen, R:$rightEyeOpen)")
                            onCopycatSuccess()
                        }
                    }
                }
                else -> { /* other challenges */ }
            }
        }
    }

    override fun release() {
        try {
            candidateGesture = HandGesture.NONE
            candidateStreak = 0
            activeLatchedGesture = HandGesture.NONE
            lastNoneGestureTime = 0L
            lastFaceSeenTime = 0L
            isGazeActive = false
            targetTracker.clear()
            onObjectsDetected?.invoke(emptyList())
            PetVisionBridge.onObjectsDetected?.invoke(emptyList())
            faceDetector.close()
            objectDetector.close()
            logDebug(TAG, "PetVisionDetector closed")
        } catch (e: Exception) {
            logError(TAG, "Error closing detector: ${e.message}")
        }
    }
}

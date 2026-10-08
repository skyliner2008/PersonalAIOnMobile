package com.skyliner2008.jarvis.pet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.skyliner2008.jarvis.camera.BoundingBox
import com.skyliner2008.jarvis.camera.DetectedObject
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.GazeStabilizer
import kotlin.math.abs
import kotlin.math.hypot

/**
 * PetVisionDetector — ตรวจจับใบหน้า สายตา วัตถุ และมือ/นิ้ว ด้วย Google ML Kit & Computer Vision
 * สำหรับโหมดสัตว์เลี้ยง (Virtual Desk Pet):
 *
 * 1. Face & Expression Detection: ตรวจจับใบหน้า รอยยิ้ม และการขยิบตา
 * 2. Things: สิ่งของรอบตัว 80 ชนิดจาก EfficientDet-Lite0 ([ObjectSpotter] → [Things])
 * 3. Hands & Gestures: มือและท่ามือจาก MediaPipe Gesture Recognizer ([HandTracker] → [HandGestureReader])
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
        /** a wink holds this many frames in a row (one frame of uneven eyes is a blink caught half way) */
        private const val WINK_FRAMES = 2
        /** a gesture holds this many frames in a row before it counts */
        private const val CONFIRM_FRAMES = 3
        private val GESTURE_COLORS = mapOf(
            HandGesture.BYE to "#00E5FF",
            HandGesture.HIGH_FIVE to "#00E676",
            HandGesture.V_SIGN to "#FFD700",
            HandGesture.NO to "#FF9100",
            HandGesture.THUMBS_UP to "#76FF03",
            HandGesture.THUMBS_DOWN to "#FF5252",
            HandGesture.OK to "#E040FB"
        )
        /** at most one SFace embedding per this — the model runs ~20–60 ms on its own thread */
        private const val EMBED_INTERVAL_MS = 330L
        /** an embedding older than this no longer describes the face in front of the camera */
        private const val EMBED_FRESH_MS = 1_500L

        /**
         * เรียกครั้งเดียวตอนเริ่มต้นแอปเพื่อลงทะเบียน processorFactory กับ PetVisionBridge
         */
        fun installBridge() {
            PetVisionBridge.faceEmbeddingAvailable = { FaceEmbedder.isAvailable }
            PetVisionBridge.processorFactory = { onGaze, onIntruder, onCopycat, getTarget, isSentry ->
                PetVisionDetector(onGaze, onIntruder, onCopycat, getTarget, isSentry)
            }
            logDebug(TAG, "PetVisionBridge installed: ML Kit faces, MediaPipe hands and things")
        }
    }

    private val faceOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setMinFaceSize(0.15f)
        .build()

    private val faceDetector: FaceDetector = FaceDetection.getClient(faceOptions)

    private val targetTracker = PetVisionTargetTracker()
    private val handReader = HandGestureReader()
    private var winkFrames = 0
    private val gazeStabilizer = GazeStabilizer()
    private val gazeNeutral = com.skyliner2008.jarvis.ui.component.avatar.GazeNeutral()

    private var isProcessing = false
    private var lastProcessTime = 0L
    private var lastIntruderAlertTime = 0L
    private var lastGazeLogTime = 0L
    private var lastDetectionLogTime = 0L
    private var hasLoggedFirstFrame = false
    private var lastFaceSeenTime = 0L
    private var isGazeActive = false

    private var smoothedMatchScore = 0f
    private var lastMatchedSlot = -1
    private var lastProfileEmitTime = 0L

    // the primary face's latest SFace embedding ([FaceEmbedder]), refreshed every EMBED_INTERVAL_MS on its own thread
    @Volatile private var latestEmbedding: List<Float>? = null
    @Volatile private var latestEmbeddingAt = 0L
    private var lastEmbedRequestAt = 0L

    init {
        PetVisionBridge.onResetAbsence = {
            gazeStabilizer.resetAbsenceTimer()
            lastFaceSeenTime = System.currentTimeMillis()
        }
    }

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

        Tasks.whenAllComplete(faceTask)
            .addOnSuccessListener {
                try {
                    val faces = if (faceTask.isSuccessful) faceTask.result ?: emptyList() else emptyList()
                    handleDetections(
                        faces = faces,
                        things = null,
                        hands = null,
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

        // hands and things first, on this (camera) thread: the frame is recycled as soon as ML Kit is done with it
        val hands = HandTracker.detect(bitmap)
        val things = ObjectSpotter.detect(bitmap)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val faceTask = faceDetector.process(inputImage)

        Tasks.whenAllComplete(faceTask)
            .addOnSuccessListener {
                try {
                    val faces = if (faceTask.isSuccessful) faceTask.result ?: emptyList() else emptyList()
                    handleDetections(
                        faces = faces,
                        things = things,
                        hands = hands,
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        isFrontCamera = isFrontCamera,
                        isAlreadyMirrored = isAlreadyMirrored,
                        bitmap = bitmap
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
     * The main hand as a detection — "Hand " + its confirmed gesture, or what it is doing ([HandPose.poseLabel]) — and
     * the gesture's confirmation ([handleGestureFrame]). A gesture shows only once confirmed, the moment the pet
     * reacts: a single frame's guess used to show and then give way to another (👍 in the log, ✌️ triggered).
     */
    private fun handObjects(
        hands: List<HandSighting>?,
        width: Int,
        height: Int,
        isFrontCamera: Boolean,
        isAlreadyMirrored: Boolean,
        faceCovered: Boolean
    ): List<DetectedObject> {
        val now = System.currentTimeMillis()
        val reading = handReader.read(hands.orEmpty(), now)
        handleGestureFrame(reading?.gesture ?: HandGesture.NONE, now)
        logHands(hands.orEmpty(), reading, faceCovered, now)
        if (reading == null) return emptyList()

        val hand = reading.hand
        val confirmed = confirmedGesture()
        val label = if (confirmed != HandGesture.NONE) "Hand ${confirmed.icon} ${confirmed.displayNameTh}" else "Hand ${HandPose.poseLabel(hand)}"
        val confidence = if (confirmed != HandGesture.NONE && confirmed != HandGesture.OK) hand.gestureScore else hand.handScore
        val (l, t, r, b) = hand.bounds()
        val nLeft = if (isFrontCamera && !isAlreadyMirrored) (1f - r / width) else l / width
        return listOf(
            DetectedObject(
                label = label,
                confidence = confidence.coerceIn(0f, 1f),
                boundingBox = BoundingBox(
                    x = nLeft.coerceIn(0f, 1f),
                    y = (t / height).coerceIn(0f, 1f),
                    width = ((r - l) / width).coerceIn(0.01f, 1f),
                    height = ((b - t) / height).coerceIn(0.01f, 1f)
                ),
                color = GESTURE_COLORS[confirmed] ?: "#FF9100"
            )
        )
    }

    private var lastHandLogAt = 0L
    private var lastHandLogKey = ""

    /** what the hand model sees, for tuning from logcat: on a change, else every 3 s while hands are in view */
    private fun logHands(hands: List<HandSighting>, reading: HandGestureReader.Reading?, faceCovered: Boolean, now: Long) {
        if (hands.isEmpty()) {
            lastHandLogKey = ""
            return
        }
        val key = hands.joinToString { it.modelGesture } + "|${reading?.gesture}|$faceCovered"
        if (key == lastHandLogKey && now - lastHandLogAt < 3_000L) return
        lastHandLogKey = key
        lastHandLogAt = now
        logDebug(
            TAG,
            "✋ Hands: " + hands.joinToString { "${it.side} ${it.modelGesture} ${"%.2f".format(it.gestureScore)} (palm ${it.palm.toInt()}px)" } +
                " → ${reading?.gesture ?: HandGesture.NONE}" + if (faceCovered) " · มือบังหน้า: ไม่นับขยิบตา ไม่เทียบหน้า" else ""
        )
    }

    private var lastThingLogAt = 0L
    private var lastThingLogKey = ""

    /** what the object model sees, for tuning from logcat: on a change, else every 5 s while things are in view */
    private fun logThings(things: List<ThingSighting>, now: Long) {
        if (things.isEmpty()) {
            lastThingLogKey = ""
            return
        }
        val key = things.map { it.name }.sorted().joinToString()
        if (key == lastThingLogKey && now - lastThingLogAt < 5_000L) return
        lastThingLogKey = key
        lastThingLogAt = now
        logDebug(TAG, "📦 Things: " + things.joinToString { "${it.name} ${"%.2f".format(it.score)}" })
    }

    /** the gesture held for [CONFIRM_FRAMES] frames in a row, else NONE */
    private fun confirmedGesture(): HandGesture =
        if (candidateStreak >= CONFIRM_FRAMES) candidateGesture else HandGesture.NONE

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

            if (candidateStreak >= CONFIRM_FRAMES) {
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

    /** ask for the primary face's embedding when one is due (5 ML Kit landmarks → SFace on its own thread) */
    private fun requestEmbedding(face: Face, bitmap: Bitmap) {
        if (!FaceEmbedder.isAvailable) return
        val now = System.currentTimeMillis()
        if (now - lastEmbedRequestAt < EMBED_INTERVAL_MS) return
        fun point(type: Int) = face.getLandmark(type)?.position?.let { floatArrayOf(it.x, it.y) }
        val eyeA = point(FaceLandmark.LEFT_EYE) ?: return
        val eyeB = point(FaceLandmark.RIGHT_EYE) ?: return
        val nose = point(FaceLandmark.NOSE_BASE) ?: return
        val mouthA = point(FaceLandmark.MOUTH_LEFT) ?: return
        val mouthB = point(FaceLandmark.MOUTH_RIGHT) ?: return
        val points = FaceRecognition.templateOrder(eyeA, eyeB, nose, mouthA, mouthB)
        val started = FaceEmbedder.embedAsync(bitmap, points) { embedding ->
            if (embedding != null) {
                latestEmbedding = embedding
                latestEmbeddingAt = System.currentTimeMillis()
            }
        }
        if (started) lastEmbedRequestAt = now
    }

    private var lastScoreLogAt = 0L
    private var lastScoreLogSlot = -2

    /** every profile's cosine to the face in front, for checking recognition from logcat: on a new answer, else every 3 s */
    private fun logScore(profiles: List<PetFaceProfile>, embedding: List<Float>, matched: PetFaceProfile?, now: Long) {
        val slot = matched?.slotIndex ?: -1
        if (slot == lastScoreLogSlot && now - lastScoreLogAt < 3_000L) return
        lastScoreLogSlot = slot
        lastScoreLogAt = now
        val scores = profiles.map { it.name to FaceRecognition.cosine(it.embedding, embedding) }.sortedByDescending { it.second }
        logDebug(
            TAG,
            "🪪 SFace: " + (if (scores.isEmpty()) "ไม่มีโปรไฟล์ที่บันทึกด้วยโมเดล" else scores.joinToString { "${it.first} cos=${"%.2f".format(it.second)}" }) +
                " → ${matched?.name ?: "ไม่ตรงสล็อตใด"} (เกณฑ์ ${FaceRecognition.SAME_PERSON_COSINE})"
        )
    }

    private fun freshEmbedding(now: Long): List<Float>? =
        latestEmbedding?.takeIf { now - latestEmbeddingAt <= EMBED_FRESH_MS }

    /** report the match to the bridge: a new person at once, the same person's score smoothed every 0.8 s */
    private fun emitMatch(profile: PetFaceProfile?, rawPct: Int, noMatchLabel: String, nowTime: Long) {
        if (profile != null) {
            if (lastMatchedSlot != profile.slotIndex) {
                lastMatchedSlot = profile.slotIndex
                smoothedMatchScore = rawPct.toFloat()
                lastProfileEmitTime = nowTime
                PetVisionBridge.onFaceProfileMatched?.invoke("${profile.name} ($rawPct%)", profile.slotIndex)
            } else {
                smoothedMatchScore = (smoothedMatchScore * 0.82f) + (rawPct * 0.18f)
                if (nowTime - lastProfileEmitTime > 800L) {
                    lastProfileEmitTime = nowTime
                    val stableScore = (smoothedMatchScore.toInt() / 5) * 5
                    PetVisionBridge.onFaceProfileMatched?.invoke("${profile.name} ($stableScore%)", profile.slotIndex)
                }
            }
        } else {
            lastMatchedSlot = -1
            smoothedMatchScore = 0f
            if (nowTime - lastProfileEmitTime > 800L) {
                lastProfileEmitTime = nowTime
                PetVisionBridge.onFaceProfileMatched?.invoke(noMatchLabel, -1)
            }
        }
    }

    private fun handleDetections(
        faces: List<Face>,
        /** the object model's things ([ObjectSpotter]); null on the ImageProxy path or without the model */
        things: List<ThingSighting>?,
        /** the hand model's hands ([HandTracker]); null on the ImageProxy path or without the model */
        hands: List<HandSighting>?,
        imageWidth: Int,
        imageHeight: Int,
        isFrontCamera: Boolean,
        isAlreadyMirrored: Boolean,
        /** the frame (bitmap path) — for the face embedding; recycled once detection completes */
        bitmap: Bitmap? = null
    ) {
        val detectedList = mutableListOf<DetectedObject>()

        // the face the pet answers to: the biggest — only it is matched against the profiles (with several faces in
        // view each used to report its own match, and the last one won)
        val primary = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        // a hand over a face: ML Kit reads the eye behind it as closed (a wink), and the embedding is half hand
        val handList = hands.orEmpty()
        fun coveredByHand(face: Face): Boolean {
            val fb = face.boundingBox
            return handList.any { HandPose.covers(it, fb.left.toFloat(), fb.top.toFloat(), fb.right.toFloat(), fb.bottom.toFloat()) }
        }
        val primaryCovered = primary != null && coveredByHand(primary)
        if (primary != null && bitmap != null && !primaryCovered) requestEmbedding(primary, bitmap)

        // ─── 1. Face Recognition & Expression Bounding Boxes ───────────────
        faces.forEach { face ->
            val smileProb = face.smilingProbability ?: -1f
            val leftEyeOpen = face.leftEyeOpenProbability ?: -1f
            val rightEyeOpen = face.rightEyeOpenProbability ?: -1f

            val eyesDiffer = if (leftEyeOpen >= 0f && rightEyeOpen >= 0f) {
                val diff = abs(leftEyeOpen - rightEyeOpen)
                (diff >= WINK_DIFF_THRESHOLD) ||
                (leftEyeOpen > 0.60f && rightEyeOpen < 0.30f) ||
                (rightEyeOpen > 0.60f && leftEyeOpen < 0.30f)
            } else false
            // a wink is uneven eyes on an uncovered face, held WINK_FRAMES frames (the primary face's frames are counted)
            val covered = if (face === primary) primaryCovered else coveredByHand(face)
            if (face === primary) winkFrames = if (eyesDiffer && !covered) winkFrames + 1 else 0
            val isWinking = if (face === primary) winkFrames >= WINK_FRAMES else eyesDiffer && !covered

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
            if (face === primary) PetVisionBridge.onCurrentFaceLandmarks?.invoke(landmarkRatios)

            // Emit detailed FaceBiometrics for interactive stepped enrollment
            val hasEyes = leftEye != null && rightEye != null
            val hasNose = nose != null
            val hasMouth = mouth != null

            val faceCenterX = face.boundingBox.centerX().toFloat() / imageWidth.toFloat()
            val faceCenterY = face.boundingBox.centerY().toFloat() / imageHeight.toFloat()
            val isCentered = abs(faceCenterX - 0.5f) < 0.20f && abs(faceCenterY - 0.5f) < 0.25f
            val isLookingStraight = abs(face.headEulerAngleY) < 18f && abs(face.headEulerAngleZ) < 18f

            val biometrics = FaceBiometricDetails(
                isFaceDetected = true,
                isCentered = isCentered && isLookingStraight,
                hasEyesDetected = hasEyes,
                hasNoseDetected = hasNose,
                hasMouthDetected = hasMouth,
                landmarkRatios = landmarkRatios,
                yawAngle = face.headEulerAngleY,
                rollAngle = face.headEulerAngleZ,
                faceBounds = BoundingBox(
                    x = (face.boundingBox.left.toFloat() / imageWidth).coerceIn(0f, 1f),
                    y = (face.boundingBox.top.toFloat() / imageHeight).coerceIn(0f, 1f),
                    width = (face.boundingBox.width().toFloat() / imageWidth).coerceIn(0.01f, 1f),
                    height = (face.boundingBox.height().toFloat() / imageHeight).coerceIn(0.01f, 1f)
                ),
                embedding = if (face === primary) freshEmbedding(System.currentTimeMillis()).orEmpty() else emptyList(),
                embeddingAtMs = if (face === primary) latestEmbeddingAt else 0L
            )
            if (face === primary) PetVisionBridge.onFaceBiometrics?.invoke(biometrics)

            // Match against registered face profiles — the primary face only
            val profiles = PetVisionBridge.registeredProfilesProvider?.invoke() ?: emptyList()
            val enrolledProfiles = profiles.filter { it.isEnrolled }
            val nowTime = System.currentTimeMillis()
            var matchedProfile: PetFaceProfile? = null
            if (face === primary) {
                if (FaceEmbedder.isAvailable) {
                    // SFace: the same person when the embeddings' cosine reaches FaceRecognition.SAME_PERSON_COSINE
                    val withEmbedding = enrolledProfiles.filter { it.usesEmbedding }
                    val embedding = freshEmbedding(nowTime)
                    if (embedding != null) {
                        val best = FaceRecognition.bestMatch(withEmbedding, embedding)
                        matchedProfile = best?.first
                        logScore(withEmbedding, embedding, matchedProfile, nowTime)
                        emitMatch(
                            matchedProfile,
                            best?.let { (it.second * 100).toInt().coerceIn(0, 100) } ?: 0,
                            when {
                                enrolledProfiles.isEmpty() -> "ตรวจพบใบหน้า (ยังไม่ลงทะเบียน)"
                                withEmbedding.isEmpty() -> "ใบหน้าที่บันทึกไว้ต้องสแกนใหม่ (ระบบจดจำใหม่)"
                                else -> "บุคคลอื่น / ไม่ตรงกับสล็อตใด"
                            },
                            nowTime
                        )
                    } else {
                        // the first embedding is ~0.3 s away: keep the last answer rather than flash "someone else"
                        matchedProfile = enrolledProfiles.firstOrNull { it.slotIndex == lastMatchedSlot }
                    }
                } else {
                    // no model: the landmark ratios, closest profile within MATCH_THRESHOLD
                    val best = enrolledProfiles
                        .map { it to it.calculateDistance(landmarkRatios) }
                        .filter { it.second <= PetFaceProfile.MATCH_THRESHOLD }
                        .minByOrNull { it.second }
                    matchedProfile = best?.first
                    emitMatch(
                        matchedProfile,
                        best?.let { ((1f - it.second / PetFaceProfile.MATCH_THRESHOLD).coerceIn(0f, 1f) * 100).toInt() } ?: 0,
                        if (enrolledProfiles.isNotEmpty()) "บุคคลอื่น / ไม่ตรงกับสล็อตใด" else "ตรวจพบใบหน้า (ยังไม่ลงทะเบียน)",
                        nowTime
                    )
                }
            }

            val faceDisplayName = matchedProfile?.name ?: if (enrolledProfiles.isEmpty()) "Boss" else "Guest"

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
                    label = "$faceDisplayName Face$idStr"
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

        // ─── 2. Hands & Gestures ───────────────────────────────────────────
        detectedList.addAll(handObjects(hands, imageWidth, imageHeight, isFrontCamera, isAlreadyMirrored, primaryCovered))

        // ─── 3. Things (EfficientDet-Lite0, [ObjectSpotter]) ────────────────
        // held things stay: a phone or a cup in the hand overlaps the hand by nature
        val thingList = things.orEmpty()
        thingList.forEach { thing ->
            val (l, t, r, bt) = thing.box
            // a thing mostly on a face belongs to the face
            val isFaceOverlap = faces.any { face ->
                val fb = face.boundingBox
                val iw = minOf(r, fb.right.toFloat()) - maxOf(l, fb.left.toFloat())
                val ih = minOf(bt, fb.bottom.toFloat()) - maxOf(t, fb.top.toFloat())
                iw > 0f && ih > 0f && iw * ih > (r - l) * (bt - t) * 0.45f
            }
            if (isFaceOverlap) return@forEach
            val nLeft = if (isFrontCamera && !isAlreadyMirrored) 1f - r / imageWidth else l / imageWidth
            detectedList.add(
                DetectedObject(
                    label = Things.label(thing.name),
                    confidence = thing.score,
                    boundingBox = BoundingBox(
                        x = nLeft.coerceIn(0f, 1f),
                        y = (t / imageHeight).coerceIn(0f, 1f),
                        width = ((r - l) / imageWidth).coerceIn(0.01f, 1f),
                        height = ((bt - t) / imageHeight).coerceIn(0.01f, 1f)
                    ),
                    color = "#B388FF"
                )
            )
        }
        logThings(thingList, System.currentTimeMillis())

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
            lastMatchedSlot = -1
            smoothedMatchScore = 0f
            latestEmbedding = null
            winkFrames = 0
            PetVisionBridge.onCurrentFaceLandmarks?.invoke(emptyList())
            PetVisionBridge.onFaceBiometrics?.invoke(FaceBiometricDetails())
            PetVisionBridge.onFaceProfileMatched?.invoke(null, -1)
            val absenceEmotion = gazeStabilizer.onNoFace(now)
            if (isGazeActive && (now - lastFaceSeenTime > 1000L)) {
                isGazeActive = false
                logDebug(TAG, "👀 Face lost for >1000ms -> Resetting pet gaze to center (0f, 0f)")
                onGazeDetected(0f, 0f)
                PetVisionBridge.onFaceDistanceDetected?.invoke(1f)
            }
            if (absenceEmotion != null) {
                logDebug(TAG, "😴 Face absence timeout reached -> Triggering ${absenceEmotion.name}")
                PetVisionBridge.onFaceAbsenceTimeout?.invoke(absenceEmotion)
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

        // Up and down, "straight ahead" is wherever the user's face usually is (GazeNeutral): on a desk stand
        // the face sits high in the frame (≈ -0.45), held in the hand low (≈ +0.35). The old fixed desk line
        // pinned the gaze to the bottom edge when hand-held. Left and right: the camera is in the middle.
        val calibratedY = gazeNeutral.offsetY(rawNormY, now) * 1.35f
        val calibratedX = rawNormX * 1.25f

        // Deadzone: if user is sitting naturally in front within deadzone, snap to dead center (0f, 0f)
        val finalNormX = if (abs(calibratedX) < 0.12f) 0f else calibratedX.coerceIn(-1f, 1f)
        val finalNormY = if (abs(calibratedY) < 0.15f) 0f else calibratedY.coerceIn(-1f, 1f)

        // Pass through adaptive EMA GazeStabilizer (filters ±3% jitter & computes face distance scale)
        val faceRatio = faceBox.width().toFloat() / imageWidth.toFloat()
        val (stabilizedX, stabilizedY) = gazeStabilizer.update(finalNormX, finalNormY, faceRatio, now)

        if (now - lastGazeLogTime > 2000L) {
            lastGazeLogTime = now
            val gx = (stabilizedX * 100).toInt() / 100f
            val gy = (stabilizedY * 100).toInt() / 100f
            val rx = (rawNormX * 100).toInt() / 100f
            val ry = (rawNormY * 100).toInt() / 100f
            val ny = (gazeNeutral.y * 100).toInt() / 100f
            logDebug(TAG, "👀 Face tracked at (normX=$gx, normY=$gy) [raw:($rx, $ry), straightY=$ny, scale=${gazeStabilizer.faceScaleFactor}] -> Pet gaze updated")
        }

        onGazeDetected(stabilizedX, stabilizedY)
        PetVisionBridge.onFaceDistanceDetected?.invoke(gazeStabilizer.faceScaleFactor)

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
                    if (!primaryCovered && leftEyeOpen >= 0f && rightEyeOpen >= 0f) {
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
            handReader.clear()
            winkFrames = 0
            lastFaceSeenTime = 0L
            isGazeActive = false
            targetTracker.clear()
            latestEmbedding = null
            PetVisionBridge.onCurrentFaceLandmarks?.invoke(emptyList())
            PetVisionBridge.onFaceBiometrics?.invoke(FaceBiometricDetails())
            PetVisionBridge.onFaceProfileMatched?.invoke(null, -1)
            onObjectsDetected?.invoke(emptyList())
            PetVisionBridge.onObjectsDetected?.invoke(emptyList())
            PetVisionBridge.onResetAbsence = null
            faceDetector.close()
            logDebug(TAG, "PetVisionDetector closed")
        } catch (e: Exception) {
            logError(TAG, "Error closing detector: ${e.message}")
        }
    }
}

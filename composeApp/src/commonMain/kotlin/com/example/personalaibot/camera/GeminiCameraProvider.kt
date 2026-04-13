package com.example.personalaibot.camera

import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.data.InlineData
import com.example.personalaibot.data.LiveGeminiService
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.float

// ═══════════════════════════════════════════════════════════════════
// GeminiCameraProvider — รองรับ 2 โหมด:
//   1. Live Stream   → ส่ง frame ผ่าน WebSocket (Gemini 3.1 Flash Live)
//   2. Snapshot/OD   → ส่ง base64 ผ่าน REST API (Gemini 2.5/3.1 Flash)
//
// Models:
//   - gemini-3.1-flash-live-preview  (Live WebSocket)
//   - gemini-2.5-flash               (REST generateContent)
//   - gemini-3.1-flash               (REST generateContent)
//   - gemini-3.1-pro                 (REST generateContent)
// ═══════════════════════════════════════════════════════════════════

class GeminiCameraProvider(
    private val geminiService: GeminiService
) : CameraProvider {

    var onLiveFrameReady: (suspend (String) -> Unit)? = null

    companion object {
        private val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }

    override val providerType: CameraProviderType = CameraProviderType.GEMINI_LIVE
    override val supportsLiveStream: Boolean = true
    override val supportsObjectDetection: Boolean = true

    private val _state = MutableStateFlow<CameraProviderState>(CameraProviderState.Idle)
    override val state: StateFlow<CameraProviderState> = _state.asStateFlow()

    private var config: CameraProviderConfig? = null

    override suspend fun initialize(config: CameraProviderConfig) {
        this.config = config
        _state.value = CameraProviderState.Ready
        logDebug("GeminiCamera", "Initialized with model: ${config.modelName}")
    }

    /**
     * วิเคราะห์ภาพ 1 frame ผ่าน Gemini REST API (generateContent)
     * ใช้สำหรับ Snapshot mode หรือ Object Detection
     */
    override suspend fun analyzeFrame(jpegBase64: String, prompt: String): CameraAnalysisResult {
        val startTime = Clock.System.now().toEpochMilliseconds()
        _state.value = CameraProviderState.Analyzing

        return try {
            val analysisPrompt = buildAnalysisPrompt(prompt, detectObjects = false)

            val response = geminiService.generateResponse(
                prompt = analysisPrompt,
                enableGrounding = false
            )

            val now = Clock.System.now().toEpochMilliseconds()
            _state.value = CameraProviderState.Ready

            CameraAnalysisResult(
                provider = providerType,
                description = response,
                labels = extractLabels(response),
                processingTimeMs = now - startTime,
                timestamp = now
            )
        } catch (e: Exception) {
            logError("GeminiCamera", "analyzeFrame failed", e)
            _state.value = CameraProviderState.Error(e.message ?: "Unknown error")
            CameraAnalysisResult(
                provider = providerType,
                description = "Error: ${e.message}",
                processingTimeMs = Clock.System.now().toEpochMilliseconds() - startTime,
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    /**
     * ส่ง frame ผ่าน Gemini Live WebSocket (realtimeInput.video)
     * ใช้สำหรับ Live Stream mode ร่วมกับ Voice
     * รันผ่าน callback ไปยัง session หลัก
     */
    override suspend fun sendLiveFrame(jpegBase64: String) {
        try {
            onLiveFrameReady?.invoke(jpegBase64)
        } catch (e: Exception) {
            logError("GeminiCamera", "sendLiveFrame failed", e)
        }
    }

    /**
     * Object Detection ผ่าน Gemini Vision API
     * ใช้ structured prompt เพื่อให้ Gemini ตอบเป็น JSON ของ bounding boxes
     */
    override suspend fun detectObjects(jpegBase64: String): List<DetectedObject> {
        _state.value = CameraProviderState.Analyzing

        return try {
            val prompt = """Analyze this image and detect all visible objects.
                |Return ONLY a JSON array with this exact format (no markdown, no extra text):
                |[{"label":"object name","confidence":0.95,"x":0.1,"y":0.2,"w":0.3,"h":0.4}]
                |Where x,y,w,h are normalized 0.0-1.0 relative to image dimensions.
                |Detect at most 10 objects. Include confidence 0.0-1.0.""".trimMargin()

            val response = geminiService.generateResponse(
                prompt = prompt,
                enableGrounding = false
            )

            _state.value = CameraProviderState.Ready
            parseObjectDetectionResponse(response)
        } catch (e: Exception) {
            logError("GeminiCamera", "detectObjects failed", e)
            _state.value = CameraProviderState.Ready
            emptyList()
        }
    }

    override suspend fun release() {
        _state.value = CameraProviderState.Idle
        config = null
    }

    // ─── Private helpers ─────────────────────────────────────────────

    private fun buildAnalysisPrompt(userPrompt: String, detectObjects: Boolean): String {
        val lang = config?.language ?: "th"
        val langInstruction = if (lang == "th") "ตอบเป็นภาษาไทย" else "Answer in English"

        return buildString {
            append("คุณคือ JARVIS Camera Vision — ระบบวิเคราะห์ภาพ real-time อัจฉริยะ\n")
            append("$langInstruction\n\n")
            if (userPrompt.isNotBlank()) {
                append("คำถามของผู้ใช้: $userPrompt\n\n")
            }
            append("วิเคราะห์ภาพนี้อย่างละเอียด:\n")
            append("1. อธิบายสิ่งที่เห็นในภาพ (วัตถุ, คน, สถานที่, ข้อความ)\n")
            append("2. ระบุ context หรือสถานการณ์\n")
            append("3. หากมีข้อความในภาพ ให้อ่านออกมา (OCR)\n")
            append("4. หากเป็นกราฟ/chart ให้วิเคราะห์ข้อมูล\n")
            if (detectObjects) {
                append("5. ระบุวัตถุทั้งหมดพร้อมตำแหน่ง\n")
            }
        }
    }

    private fun extractLabels(response: String): List<AnalysisLabel> {
        val labels = mutableListOf<AnalysisLabel>()

        // Extract scene/context
        if (response.length > 20) {
            val summary = response.take(80).substringBefore("\n").trim()
            if (summary.isNotBlank()) {
                labels.add(AnalysisLabel(summary, LabelCategory.SCENE, LabelPosition.TOP_LEFT))
            }
        }

        // Detect warnings
        val warningKeywords = listOf("ระวัง", "อันตราย", "warning", "danger", "caution")
        if (warningKeywords.any { response.lowercase().contains(it) }) {
            labels.add(AnalysisLabel("⚠️ Warning detected", LabelCategory.WARNING, LabelPosition.TOP_CENTER))
        }

        return labels
    }

    private fun parseObjectDetectionResponse(response: String): List<DetectedObject> {
        return try {
            val jsonStr = response
                .replace("```json", "").replace("```", "")
                .trim()
                .let { if (it.startsWith("[")) it else "[$it]" }

            val jsonArray = jsonParser.parseToJsonElement(jsonStr).jsonArray

            jsonArray.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val label = obj["label"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val conf = obj["confidence"]?.jsonPrimitive?.float ?: 0.8f
                    val x = obj["x"]?.jsonPrimitive?.float ?: 0f
                    val y = obj["y"]?.jsonPrimitive?.float ?: 0f
                    val w = obj["w"]?.jsonPrimitive?.float ?: 0f
                    val h = obj["h"]?.jsonPrimitive?.float ?: 0f

                    DetectedObject(
                        label = label,
                        confidence = conf,
                        boundingBox = if (w > 0 && h > 0) BoundingBox(x, y, w, h) else null
                    )
                } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            logError("GeminiCamera", "parseObjectDetection failed: ${e.message}")
            emptyList()
        }
    }
}

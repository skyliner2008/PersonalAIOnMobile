package com.example.personalaibot.camera

import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

// ═══════════════════════════════════════════════════════════════════
// OpenAICameraProvider — GPT-4o / GPT-4.1 Vision API
//
// ใช้ Chat Completions API พร้อม image_url (base64 data URI)
// Endpoint: https://api.openai.com/v1/chat/completions
//
// Supported Models:
//   - gpt-4o           (omni — text + image + audio)
//   - gpt-4o-mini      (lightweight vision)
//   - gpt-4.1          (latest flagship)
//   - gpt-4.1-mini     (balanced)
// ═══════════════════════════════════════════════════════════════════

class OpenAICameraProvider(
    private val client: HttpClient
) : CameraProvider {

    override val providerType: CameraProviderType = CameraProviderType.OPENAI_GPT4O
    override val supportsLiveStream: Boolean = false  // OpenAI ไม่มี live WebSocket สำหรับ vision
    override val supportsObjectDetection: Boolean = true

    private val _state = MutableStateFlow<CameraProviderState>(CameraProviderState.Idle)
    override val state: StateFlow<CameraProviderState> = _state.asStateFlow()

    private var config: CameraProviderConfig? = null
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
    }

    override suspend fun initialize(config: CameraProviderConfig) {
        this.config = config
        _state.value = CameraProviderState.Ready
        logDebug("OpenAICamera", "Initialized with model: ${config.modelName}")
    }

    /**
     * วิเคราะห์ภาพ 1 frame ผ่าน OpenAI Chat Completions API
     * ส่งภาพเป็น base64 data URI ใน image_url
     */
    override suspend fun analyzeFrame(jpegBase64: String, prompt: String): CameraAnalysisResult {
        val cfg = config ?: return errorResult("Not initialized")
        val startTime = Clock.System.now().toEpochMilliseconds()
        _state.value = CameraProviderState.Analyzing

        return try {
            val analysisPrompt = buildPrompt(prompt, detectObjects = false)
            val requestBody = buildVisionRequest(cfg, analysisPrompt, jpegBase64)

            val response = client.post(API_URL) {
                header("Authorization", "Bearer ${cfg.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            if (!response.status.isSuccess()) {
                val err = response.bodyAsText()
                logError("OpenAICamera", "API Error ${response.status}: $err")
                _state.value = CameraProviderState.Error("API Error: ${response.status}")
                return errorResult("API Error ${response.status.value}: $err")
            }

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val content = body["choices"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content ?: "No response"

            val now = Clock.System.now().toEpochMilliseconds()
            _state.value = CameraProviderState.Ready

            CameraAnalysisResult(
                provider = providerType,
                description = content,
                labels = extractLabels(content),
                processingTimeMs = now - startTime,
                timestamp = now
            )
        } catch (e: Exception) {
            logError("OpenAICamera", "analyzeFrame failed", e)
            _state.value = CameraProviderState.Error(e.message ?: "Unknown")
            errorResult(e.message ?: "Unknown error")
        }
    }

    override suspend fun sendLiveFrame(jpegBase64: String) {
        // OpenAI ไม่รองรับ Live streaming → ใช้ analyzeFrame แทน
        logDebug("OpenAICamera", "Live stream not supported — use analyzeFrame()")
    }

    /**
     * Object Detection ผ่าน GPT-4o structured output
     */
    override suspend fun detectObjects(jpegBase64: String): List<DetectedObject> {
        val cfg = config ?: return emptyList()
        _state.value = CameraProviderState.Analyzing

        return try {
            val prompt = """Detect all visible objects in this image.
                |Return ONLY a JSON array (no markdown):
                |[{"label":"name","confidence":0.95,"x":0.1,"y":0.2,"w":0.3,"h":0.4}]
                |x,y,w,h are normalized 0.0-1.0. Max 10 objects.""".trimMargin()

            val requestBody = buildVisionRequest(cfg, prompt, jpegBase64)
            val response = client.post(API_URL) {
                header("Authorization", "Bearer ${cfg.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val content = body["choices"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content ?: "[]"

            _state.value = CameraProviderState.Ready
            parseObjectDetectionJson(content)
        } catch (e: Exception) {
            logError("OpenAICamera", "detectObjects failed", e)
            _state.value = CameraProviderState.Ready
            emptyList()
        }
    }

    override suspend fun release() {
        _state.value = CameraProviderState.Idle
        config = null
    }

    // ─── Private helpers ─────────────────────────────────────────────

    private fun buildVisionRequest(cfg: CameraProviderConfig, prompt: String, jpegBase64: String): JsonObject {
        return buildJsonObject {
            put("model", cfg.modelName.ifBlank { "gpt-4o" })
            put("max_tokens", cfg.maxTokens)
            put("temperature", cfg.temperature)
            put("messages", buildJsonArray {
                // System message
                add(buildJsonObject {
                    put("role", "system")
                    put("content", "คุณคือ JARVIS Camera Vision — ระบบวิเคราะห์ภาพ real-time. ตอบกระชับ ชัดเจน ภาษาไทย")
                })
                // User message with image
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", "data:image/jpeg;base64,$jpegBase64")
                                put("detail", "auto")
                            })
                        })
                    })
                })
            })
        }
    }

    private fun buildPrompt(userPrompt: String, detectObjects: Boolean): String {
        return buildString {
            if (userPrompt.isNotBlank()) {
                append("$userPrompt\n\n")
            }
            append("วิเคราะห์ภาพนี้อย่างละเอียด: อธิบายสิ่งที่เห็น, context, ข้อความ (OCR), กราฟ/chart")
            if (detectObjects) append("\nระบุวัตถุทั้งหมดพร้อมตำแหน่ง")
        }
    }

    private fun extractLabels(response: String): List<AnalysisLabel> {
        val labels = mutableListOf<AnalysisLabel>()
        val summary = response.take(80).substringBefore("\n").trim()
        if (summary.isNotBlank()) {
            labels.add(AnalysisLabel(summary, LabelCategory.SCENE, LabelPosition.TOP_LEFT))
        }
        return labels
    }

    private fun parseObjectDetectionJson(content: String): List<DetectedObject> {
        return try {
            val cleaned = content.replace("```json", "").replace("```", "").trim()
            val arr = json.parseToJsonElement(cleaned).jsonArray
            arr.mapNotNull { el ->
                try {
                    val obj = el.jsonObject
                    DetectedObject(
                        label = obj["label"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        confidence = obj["confidence"]?.jsonPrimitive?.float ?: 0.8f,
                        boundingBox = BoundingBox(
                            x = obj["x"]?.jsonPrimitive?.float ?: 0f,
                            y = obj["y"]?.jsonPrimitive?.float ?: 0f,
                            width = obj["w"]?.jsonPrimitive?.float ?: 0f,
                            height = obj["h"]?.jsonPrimitive?.float ?: 0f
                        )
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun errorResult(msg: String) = CameraAnalysisResult(
        provider = providerType,
        description = "Error: $msg",
        timestamp = Clock.System.now().toEpochMilliseconds()
    )
}

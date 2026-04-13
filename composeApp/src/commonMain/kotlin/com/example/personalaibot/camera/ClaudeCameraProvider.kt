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
// ClaudeCameraProvider — Anthropic Claude Vision API
//
// ใช้ Messages API พร้อม image content block (base64)
// Endpoint: https://api.anthropic.com/v1/messages
//
// Supported Models:
//   - claude-sonnet-4-6       (fast + accurate vision)
//   - claude-opus-4-6         (most capable vision)
//   - claude-haiku-4-5        (fastest, lower cost)
// ═══════════════════════════════════════════════════════════════════

class ClaudeCameraProvider(
    private val client: HttpClient
) : CameraProvider {

    override val providerType: CameraProviderType = CameraProviderType.CLAUDE_SONNET
    override val supportsLiveStream: Boolean = false  // Claude ไม่มี live WebSocket
    override val supportsObjectDetection: Boolean = true

    private val _state = MutableStateFlow<CameraProviderState>(CameraProviderState.Idle)
    override val state: StateFlow<CameraProviderState> = _state.asStateFlow()

    private var config: CameraProviderConfig? = null
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
    }

    override suspend fun initialize(config: CameraProviderConfig) {
        this.config = config
        _state.value = CameraProviderState.Ready
        logDebug("ClaudeCamera", "Initialized with model: ${config.modelName}")
    }

    /**
     * วิเคราะห์ภาพ 1 frame ผ่าน Claude Messages API
     * ส่งภาพเป็น base64 ใน image content block
     */
    override suspend fun analyzeFrame(jpegBase64: String, prompt: String): CameraAnalysisResult {
        val cfg = config ?: return errorResult("Not initialized")
        val startTime = Clock.System.now().toEpochMilliseconds()
        _state.value = CameraProviderState.Analyzing

        return try {
            val analysisPrompt = buildPrompt(prompt)
            val requestBody = buildMessagesRequest(cfg, analysisPrompt, jpegBase64)

            val response = client.post(API_URL) {
                header("x-api-key", cfg.apiKey)
                header("anthropic-version", API_VERSION)
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            if (!response.status.isSuccess()) {
                val err = response.bodyAsText()
                logError("ClaudeCamera", "API Error ${response.status}: $err")
                _state.value = CameraProviderState.Error("API Error: ${response.status}")
                return errorResult("API Error ${response.status.value}")
            }

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val content = body["content"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.content ?: "No response"

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
            logError("ClaudeCamera", "analyzeFrame failed", e)
            _state.value = CameraProviderState.Error(e.message ?: "Unknown")
            errorResult(e.message ?: "Unknown error")
        }
    }

    override suspend fun sendLiveFrame(jpegBase64: String) {
        logDebug("ClaudeCamera", "Live stream not supported — use analyzeFrame()")
    }

    override suspend fun detectObjects(jpegBase64: String): List<DetectedObject> {
        val cfg = config ?: return emptyList()
        _state.value = CameraProviderState.Analyzing

        return try {
            val prompt = """Detect all visible objects in this image.
                |Return ONLY a JSON array (no markdown, no explanation):
                |[{"label":"name","confidence":0.95,"x":0.1,"y":0.2,"w":0.3,"h":0.4}]
                |x,y,w,h are normalized 0.0-1.0. Max 10 objects.""".trimMargin()

            val requestBody = buildMessagesRequest(cfg, prompt, jpegBase64)
            val response = client.post(API_URL) {
                header("x-api-key", cfg.apiKey)
                header("anthropic-version", API_VERSION)
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val content = body["content"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.content ?: "[]"

            _state.value = CameraProviderState.Ready
            parseObjectDetectionJson(content)
        } catch (e: Exception) {
            logError("ClaudeCamera", "detectObjects failed", e)
            _state.value = CameraProviderState.Ready
            emptyList()
        }
    }

    override suspend fun release() {
        _state.value = CameraProviderState.Idle
        config = null
    }

    // ─── Private helpers ─────────────────────────────────────────────

    private fun buildMessagesRequest(cfg: CameraProviderConfig, prompt: String, jpegBase64: String): JsonObject {
        return buildJsonObject {
            put("model", cfg.modelName.ifBlank { "claude-sonnet-4-6" })
            put("max_tokens", cfg.maxTokens)
            put("temperature", cfg.temperature)
            put("system", "คุณคือ JARVIS Camera Vision — ระบบวิเคราะห์ภาพ real-time. ตอบกระชับ ชัดเจน ภาษาไทย")
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        // Image block (base64)
                        add(buildJsonObject {
                            put("type", "image")
                            put("source", buildJsonObject {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", jpegBase64)
                            })
                        })
                        // Text block
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        })
                    })
                })
            })
        }
    }

    private fun buildPrompt(userPrompt: String): String {
        return buildString {
            if (userPrompt.isNotBlank()) {
                append("$userPrompt\n\n")
            }
            append("วิเคราะห์ภาพนี้อย่างละเอียด: อธิบายสิ่งที่เห็น, context, ข้อความ (OCR), กราฟ/chart")
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

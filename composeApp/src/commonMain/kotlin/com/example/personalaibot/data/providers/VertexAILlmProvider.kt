package com.example.personalaibot.data.providers

import com.example.personalaibot.data.ConversationTurn
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * VertexAILlmProvider — เรียก Google Cloud Vertex AI ผ่าน Server Proxy
 *
 * เนื่องจาก Vertex AI ใช้ ADC (Application Default Credentials)
 * ซึ่งทำงานบน server เท่านั้น — mobile app จึงเรียกผ่าน mt5-core-server proxy
 *
 * Endpoints ที่ใช้:
 *   POST {serverUrl}/api/vertex/generate    — Text generation
 *   POST {serverUrl}/api/vertex/embed       — Embedding
 *   GET  {serverUrl}/api/vertex/models      — List models
 *   GET  {serverUrl}/api/vertex/status      — Check availability
 *
 * @since 2026-05-13
 */
class VertexAILlmProvider(
    private val client: HttpClient,
    private val serverBaseUrl: String,
    private val clientToken: String,
    private val directApiKey: String? = null,
    private val projectId: String? = null,
    private val location: String = "us-central1"
) : LlmProvider {

    override val providerId = "vertexai"
    override val displayName = if (directApiKey.isNullOrBlank()) "Vertex AI (via Server)" else "Vertex AI (Direct)"
    override val supportsStreaming = false
    override val supportsFunctionCalling = false

    private val isDirect = !directApiKey.isNullOrBlank() && !projectId.isNullOrBlank()

    private fun getUrl(action: String, model: String): String {
        return if (isDirect) {
            // Direct call to GCP Vertex AI using API Key
            "https://$location-aiplatform.googleapis.com/v1/projects/$projectId/locations/$location/publishers/google/models/$model:$action?key=$directApiKey"
        } else {
            // Call via Server Proxy
            "$serverBaseUrl/api/vertex/$action"
        }
    }

    private fun authHeaders(): Map<String, String> {
        return if (isDirect) {
            mapOf("Content-Type" to "application/json")
        } else {
            mapOf(
                "Authorization" to "Bearer $clientToken",
                "Content-Type" to "application/json"
            )
        }
    }

    override fun generateStream(
        messages: List<LlmMessage>,
        options: LlmOptions,
    ): Flow<LlmChunk> = flow {
        val result = generate(messages, options)
        emit(LlmChunk(text = result.text))
    }

    override suspend fun generate(
        messages: List<LlmMessage>,
        options: LlmOptions,
    ): LlmResult {
        val prompt = messages.lastOrNull()?.content ?: ""
        val model = options.model ?: "gemini-2.0-flash-lite"
        val url = getUrl("generateContent", model)

        return try {
            val response = client.post(url) {
                headers {
                    authHeaders().forEach { (k, v) -> append(k, v) }
                }
                contentType(ContentType.Application.Json)
                val body = buildJsonObject {
                    if (isDirect) {
                        putJsonArray("contents") {
                            addJsonObject {
                                put("role", "user")
                                putJsonArray("parts") { addJsonObject { put("text", prompt) } }
                            }
                        }
                        options.systemPrompt?.let {
                            putJsonObject("systemInstruction") {
                                put("role", "system")
                                putJsonArray("parts") { addJsonObject { put("text", it) } }
                            }
                        }
                    } else {
                        put("model", model)
                        put("prompt", prompt)
                        options.systemPrompt?.let { put("systemPrompt", it) }
                    }
                    putJsonObject("generationConfig") {
                        put("temperature", options.temperature.toDouble())
                        options.maxTokens?.let { put("maxOutputTokens", it) }
                    }
                }
                setBody(body.toString())
            }

            if (!response.status.isSuccess()) {
                val errBody = response.bodyAsText()
                logError("VertexAI", "Generate failed: HTTP ${response.status.value}: $errBody")
                return LlmResult(text = "⚠️ Vertex AI Error: ${response.status.value}", modelUsed = model)
            }

            val json = Json { ignoreUnknownKeys = true }
            val respText: String = response.body()
            val respObj = json.parseToJsonElement(respText).jsonObject
            
            val text = if (isDirect) {
                respObj["candidates"]?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                    ?.joinToString("") ?: ""
            } else {
                respObj["text"]?.jsonPrimitive?.content ?: ""
            }

            LlmResult(
                text = text,
                modelUsed = model,
                promptTokens = respObj["usageMetadata"]?.jsonObject?.get("promptTokenCount")?.jsonPrimitive?.intOrNull,
                completionTokens = respObj["usageMetadata"]?.jsonObject?.get("candidatesTokenCount")?.jsonPrimitive?.intOrNull,
            )
        } catch (e: Exception) {
            logError("VertexAI", "Generate request failed", e)
            LlmResult(text = "⚠️ Vertex AI Error: ${e.message}", modelUsed = model)
        }
    }

    private fun getFallbackModels(): List<LlmModelInfo> {
        return listOf(
            LlmModelInfo("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite (Vertex)", supportsVision = true),
            LlmModelInfo("gemini-2.0-flash-exp", "Gemini 2.0 Flash (Vertex)", supportsVision = true),
            LlmModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro (Vertex)", supportsVision = true),
            LlmModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash (Vertex)", supportsVision = true)
        )
    }

    override suspend fun listModels(apiKey: String): List<LlmModelInfo> {
        if (isDirect) {
            // Direct mode returns a standard set of common Vertex models to avoid complex listing
            return getFallbackModels()
        }
        // Proxy mode: try to list models from server
        return try {
            val response = client.get("$serverBaseUrl/api/vertex/models") {
                headers {
                    authHeaders().forEach { (k, v) -> append(k, v) }
                }
            }
            if (!response.status.isSuccess()) return getFallbackModels()
            val json = Json { ignoreUnknownKeys = true }
            val respText: String = response.body()
            val respObj = json.parseToJsonElement(respText).jsonObject
            val data = respObj["models"]?.jsonArray ?: return getFallbackModels()
            val list = data.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: id
                LlmModelInfo(id = id, displayName = name)
            }
            if (list.isEmpty()) getFallbackModels() else list
        } catch (e: Exception) {
            logError("VertexAI", "Failed to list models from proxy: ${e.message}")
            getFallbackModels()
        }
    }

    /**
     * ตรวจสอบว่า Vertex AI (server proxy) พร้อมใช้งานหรือไม่
     */
    override suspend fun isAvailable(): Boolean {
        return try {
            val response = client.get("$serverBaseUrl/api/vertex/status") {
                headers {
                    authHeaders().forEach { (k, v) -> append(k, v) }
                }
            }
            if (!response.status.isSuccess()) return false
            val json = Json { ignoreUnknownKeys = true }
            val respText: String = response.body()
            val respObj = json.parseToJsonElement(respText).jsonObject
            respObj["available"]?.jsonPrimitive?.boolean ?: false
        } catch (e: Exception) {
            logDebug("VertexAI", "Availability check failed: ${e.message}")
            false
        }
    }
}

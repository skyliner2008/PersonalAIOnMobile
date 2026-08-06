package com.example.personalaibot.data.embedding

import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GeminiEmbeddingProvider — Cloud embedding ผ่าน Gemini REST API
 *
 * Model cascade (try in order):
 *   1) gemini-embedding-001  → 3072 native dims, Matryoshka-truncated to 768
 *   2) text-embedding-004    → 768 native dims (fallback when (1) is gated)
 *
 * Returns vector that is ALWAYS L2-normalized to 768 dims via fitToTargetDimension(),
 * so callers can mix it with any other provider (local ONNX 384, OpenAI 1536, …)
 * inside the same vector store and have cosine similarity work uniformly.
 *
 * รองรับ 100+ ภาษารวมภาษาไทย — ต้องมี internet + API key
 *
 * @since 2026-04-29 (last-fix 2026-05-03 — drop deprecated embedding-001 → 404)
 */
class GeminiEmbeddingProvider(
    private val client: HttpClient,
    apiKey: String
) : EmbeddingProvider {

    /**
     * API key เป็น mutable — Orchestrator.updateConfig() จะเรียก updateApiKey()
     * ทุกครั้งที่ผู้ใช้บันทึก key ใหม่ (เดิม key ถูก capture ตอน init = "" ทำให้
     * cloud embedding ไม่เคยทำงานหลังผู้ใช้ใส่ key ทีหลัง)
     */
    @Volatile
    private var currentApiKey: String = apiKey

    fun updateApiKey(newKey: String) {
        currentApiKey = newKey
    }

    override val providerId = "gemini"
    override val displayName = "Gemini (Cloud)"
    override val isLocal = false
    /** After fitToTargetDimension we always return 768 dims regardless of model. */
    override val nativeDimensions = 768

    /** Models tried in order — first that returns a non-empty vector wins. */
    private val modelCascade = listOf(
        "gemini-embedding-001",   // 3072 dims (truncate)
        "text-embedding-004"      // 768 dims (no truncate)
    )

    @Serializable
    private data class EmbeddingContent(
        val parts: List<EmbeddingPart>
    )

    @Serializable
    private data class EmbeddingPart(val text: String)

    @Serializable
    private data class EmbedRequest(
        val content: EmbeddingContent,
        val taskType: String? = null
    )

    @Serializable
    private data class EmbedResponse(
        val embedding: EmbedValues = EmbedValues()
    )

    @Serializable
    private data class EmbedValues(val values: List<Float> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun embed(text: String, taskType: String): List<Float> {
        if (currentApiKey.isBlank() || text.isBlank()) return emptyList()
        for (model in modelCascade) {
            val raw = tryEmbed(model, text, taskType)
            if (raw.isNotEmpty()) {
                logDebug("GeminiEmbedding", "OK model=$model nativeDims=${raw.size}")
                return raw.fitToTargetDimension(nativeDimensions)
            }
        }
        return emptyList()
    }

    private suspend fun tryEmbed(model: String, text: String, taskType: String): List<Float> {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:embedContent?key=$currentApiKey"
            val res = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(EmbedRequest.serializer(), EmbedRequest(
                    content = EmbeddingContent(parts = listOf(EmbeddingPart(text = text))),
                    taskType = taskType
                )))
            }
            if (res.status.isSuccess()) {
                val resp: EmbedResponse = json.decodeFromString(res.body<String>())
                resp.embedding.values
            } else {
                val body = try { res.body<String>().take(240) } catch (_: Exception) { "" }
                logError("GeminiEmbedding", "model=$model HTTP ${res.status.value}: $body")
                emptyList()
            }
        } catch (e: Exception) {
            logError("GeminiEmbedding", "model=$model failed: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun isAvailable(): Boolean {
        return currentApiKey.isNotBlank()
    }
}

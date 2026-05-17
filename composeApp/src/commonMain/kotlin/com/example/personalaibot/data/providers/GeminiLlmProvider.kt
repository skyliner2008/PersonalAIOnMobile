package com.example.personalaibot.data.providers

import com.example.personalaibot.data.ConversationTurn
import com.example.personalaibot.data.GeminiModel
import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * GeminiLlmProvider — Wrapper ที่ delegate ไปยัง GeminiService เดิม
 *
 * เราใช้ GeminiService เดิมเป็น implementation เพราะมันมี logic ที่ซับซ้อน
 * (SSE parsing, function calling, grounding, Live Voice) ที่ไม่ควร rewrite
 *
 * Provider นี้ทำให้ Gemini เข้ากับ LlmProvider interface ได้
 * สำหรับ model listing และ basic generation
 *
 * @since 2026-04-29
 */
class GeminiLlmProvider(
    private val geminiService: GeminiService,
    private val client: HttpClient
) : LlmProvider {

    override val providerId = "gemini"
    override val displayName = "Gemini (Google)"
    override val supportsStreaming = true
    override val supportsFunctionCalling = true

    override fun generateStream(
        messages: List<LlmMessage>,
        options: LlmOptions
    ): Flow<LlmChunk> {
        // Convert LlmMessages to ConversationTurns for GeminiService
        val history = messages.dropLast(1).map { msg ->
            ConversationTurn(
                role = if (msg.role == "assistant") "model" else msg.role,
                content = msg.content
            )
        }
        val lastMessage = messages.lastOrNull()?.content ?: ""

        return geminiService.generateResponseFlow(
            prompt = lastMessage,
            history = history,
            intentAddon = options.systemPrompt ?: "",
            coreContext = ""
        ).map { chunk -> LlmChunk(text = chunk) }
    }

    override suspend fun generate(
        messages: List<LlmMessage>,
        options: LlmOptions
    ): LlmResult {
        val history = messages.dropLast(1).map { msg ->
            ConversationTurn(
                role = if (msg.role == "assistant") "model" else msg.role,
                content = msg.content
            )
        }
        val lastMessage = messages.lastOrNull()?.content ?: ""

        val text = geminiService.generateResponse(
            prompt = lastMessage,
            history = history,
            intentAddon = options.systemPrompt ?: "",
            coreContext = ""
        )
        return LlmResult(text = text, modelUsed = "gemini")
    }

    /**
     * ดึงรายการ Gemini models — ใช้ apiKey ที่ส่งเข้ามาตรงๆ (ไม่ผ่าน GeminiService)
     * เพื่อให้ Settings UI สามารถ list models ก่อนกด Save ได้
     */
    override suspend fun listModels(apiKey: String): List<LlmModelInfo> {
        val keyToUse = apiKey.ifBlank { return emptyList() }
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$keyToUse"
            val response = client.get(url)
            val respText: String = response.body()
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val modelList = json.decodeFromString<com.example.personalaibot.data.ModelListResponse>(respText)
            modelList.models.filter {
                it.supportedGenerationMethods?.contains("generateContent") == true
            }.map { model ->
                LlmModelInfo(
                    id = model.name.removePrefix("models/"),
                    displayName = model.displayName ?: model.name,
                    contextLength = model.inputTokenLimit ?: 0,
                    supportsFunctions = com.example.personalaibot.data.ModelConfig.supportsNativeTools(model.name),
                    supportsVision = model.name.contains("vision") || model.name.contains("pro") || model.name.contains("flash")
                )
            }
        } catch (e: Exception) {
            logError("GeminiLlm", "Failed to list models: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun isAvailable(): Boolean = true // Gemini is always registered with a key
}

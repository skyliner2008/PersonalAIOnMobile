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
            val mappedList = modelList.models.filter {
                // รวม models ที่รองรับ generateContent (chat ปกติ) หรือ bidiGenerateContent (live)
                val methods = it.supportedGenerationMethods ?: emptyList()
                methods.contains("generateContent") || methods.contains("bidiGenerateContent")
            }.map { model ->
                val methods = model.supportedGenerationMethods ?: emptyList()
                val nameLC = model.name.lowercase().removePrefix("models/")
                // ตรวจ live capability: ใช้ API field เป็นหลัก + fallback keyword
                val isLive = methods.contains("bidiGenerateContent")
                    || nameLC.contains("live")
                    || nameLC.contains("native-audio")
                    || nameLC.contains("realtime")
                LlmModelInfo(
                    id = model.name.removePrefix("models/"),
                    displayName = model.displayName ?: model.name,
                    contextLength = model.inputTokenLimit ?: 0,
                    supportsFunctions = com.example.personalaibot.data.ModelConfig.supportsNativeTools(model.name),
                    supportsVision = nameLC.contains("vision") || nameLC.contains("pro") || nameLC.contains("flash"),
                    supportsLive = isLive
                )
            }
            
            // Inject Preview Models ที่ /v1beta/models มักไม่คืนมา
            // ⚠️ MAINTENANCE: รายการนี้ hardcode — ตรวจสอบกับ Gemini API docs เป็นระยะ
            // (models ที่ถูก deprecate จะยังโผล่ใน list แต่เรียกใช้จริงไม่ได้)
            val knownPreviews = listOf(
                LlmModelInfo("gemini-3.1-flash-live-preview", "Gemini 3.1 Flash Live Preview", supportsVision = true, supportsLive = true),
                LlmModelInfo("gemini-2.5-flash-native-audio-preview-09-2025", "Gemini 2.5 Flash Native Audio", supportsVision = true, supportsLive = true),
                LlmModelInfo("gemini-3.5-live-translate-preview", "Gemini 3.5 Live Translate Preview", supportsVision = false, supportsLive = true)
            )
            val existingIds = mappedList.map { it.id }.toSet()
            mappedList + knownPreviews.filter { it.id !in existingIds }
        } catch (e: Exception) {
            logError("GeminiLlm", "Failed to list models: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun isAvailable(): Boolean = true // Gemini is always registered with a key
}

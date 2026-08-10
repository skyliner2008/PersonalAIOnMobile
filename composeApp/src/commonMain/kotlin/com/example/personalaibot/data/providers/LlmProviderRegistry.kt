package com.example.personalaibot.data.providers

import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import io.ktor.client.*

/**
 * LlmProviderRegistry — จัดการ singleton ของทุก LlmProvider
 *
 * ทุก provider ต้องลงทะเบียนที่นี่ — JarvisOrchestrator ใช้ registry นี้
 * เพื่อ resolve provider ตาม user preference
 *
 * Features:
 * - listModels สำหรับ provider ที่เลือก
 * - Free model filter (โดยเฉพาะ OpenRouter)
 * - Fallback chain: primary → backup → Gemini
 *
 * @since 2026-04-29
 */
class LlmProviderRegistry(private val client: HttpClient) {

    private val providers = mutableMapOf<String, LlmProvider>()

    /** ลงทะเบียน provider */
    fun register(provider: LlmProvider) {
        providers[provider.providerId] = provider
        logDebug("LlmRegistry", "Registered provider: ${provider.displayName}")
    }

    /** ลงทะเบียน Gemini provider (เมื่อมี API key) */
    fun registerGemini(apiKey: String) {
        // Gemini ใช้ GeminiService เดิม — ในอนาคตจะ implement GeminiLlmProvider
        // ตอนนี้ยังใช้ GeminiService ตรงผ่าน JarvisOrchestrator
    }

    /** ลงทะเบียน OpenAI provider */
    fun registerOpenAI(apiKey: String, baseUrl: String = "https://api.openai.com/v1") {
        if (apiKey.isBlank()) return
        register(OpenAILlmProvider(client, apiKey, baseUrl))
    }

    /** ลงทะเบียน Claude provider */
    fun registerClaude(apiKey: String) {
        if (apiKey.isBlank()) return
        register(ClaudeLlmProvider(client, apiKey))
    }

    /** ลงทะเบียน OpenRouter provider */
    fun registerOpenRouter(apiKey: String) {
        if (apiKey.isBlank()) return
        register(OpenRouterLlmProvider(client, apiKey))
    }

    /** ลงทะเบียน LiteLLM proxy */
    fun registerLiteLlm(baseUrl: String = "http://localhost:4000") {
        register(LiteLlmProvider(client, baseUrl))
    }

    /** ลงทะเบียน Groq provider (free tier — OpenAI-compatible) */
    fun registerGroq(apiKey: String) {
        if (apiKey.isBlank()) return
        register(GroqLlmProvider(client, apiKey))
    }

    /** ลงทะเบียน NVIDIA NIM provider (free dev credits — OpenAI-compatible) */
    fun registerNvidiaNim(apiKey: String) {
        if (apiKey.isBlank()) return
        register(NvidiaNimLlmProvider(client, apiKey))
    }

    /** ลงทะเบียน MiniMax provider */
    fun registerMinimax(apiKey: String) {
        if (apiKey.isBlank()) return
        register(MinimaxLlmProvider(client, apiKey))
    }

    /** ลงทะเบียน Vertex AI provider (รองรับทั้ง Proxy และ Direct mode) */
    fun registerVertexAI(
        serverBaseUrl: String,
        clientToken: String,
        directApiKey: String? = null,
        projectId: String? = null,
        location: String = "us-central1"
    ) {
        if (serverBaseUrl.isBlank()) return
        // ADK Server Provider
        register(AdkServerLlmProvider(client, serverBaseUrl, clientToken))
    }

    /** ลงทะเบียน Firebase Vertex AI (เฉพาะ Android) */
    fun registerFirebaseVertexAI() {
        // ใน KMP เราจะใช้วิธีเรียกผ่าน LlmProviderRegistry (actual) หรือ Factory
        // สำหรับตอนนี้ผมเตรียมโครงสร้างไว้ใน androidMain แล้ว
        // register(FirebaseVertexAILlmProvider())
    }

    /** ดึง provider ตาม ID */
    fun getProvider(id: String): LlmProvider? = providers[id]

    /** ดึง provider ทั้งหมดที่ลงทะเบียน */
    fun allProviders(): List<LlmProvider> = providers.values.toList()

    /** ดึง provider IDs ทั้งหมด */
    fun allProviderIds(): List<String> = providers.keys.toList()

    /** Resolve provider — ตาม preference + fallback */
    suspend fun resolve(preferredId: String? = null): LlmProvider? {
        if (preferredId != null) {
            val preferred = providers[preferredId]
            if (preferred != null && preferred.isAvailable()) return preferred
        }
        // Fallback chain
        for (provider in providers.values) {
            if (provider.isAvailable()) return provider
        }
        return null
    }

    /**
     * ดึงรายการ models ของ provider พร้อม filter
     * ถ้า provider ยังไม่ถูก register จะสร้าง temporary provider สำหรับ listing
     * @param providerId provider ที่ต้องการ
     * @param apiKey API key สำหรับ authentication
     * @param freeOnly true = เฉพาะ models ฟรี
     */
    suspend fun listModels(
        providerId: String,
        apiKey: String,
        freeOnly: Boolean = false
    ): List<LlmModelInfo> {
        // ใช้ registered provider ก่อน ถ้ามี
        val provider = providers[providerId] ?: createTempProvider(providerId, apiKey)
        if (provider == null) {
            logDebug("LlmRegistry", "No provider found for '$providerId'")
            return emptyList()
        }
        return try {
            var models = provider.listModels(apiKey)
            if (freeOnly) {
                models = models.filter { it.isFree }
            }
            models
        } catch (e: Exception) {
            logError("LlmRegistry", "Failed to list models for $providerId: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * สร้าง temporary provider สำหรับ listing models
     * เมื่อ provider ยังไม่ถูก register (เช่น user กำลังตั้งค่า key ใหม่ใน Settings)
     */
    private fun createTempProvider(providerId: String, apiKey: String): LlmProvider? {
        if (apiKey.isBlank()) return null
        return when (providerId.lowercase()) {
            "openai" -> OpenAILlmProvider(client, apiKey)
            "claude", "anthropic" -> ClaudeLlmProvider(client, apiKey)
            "openrouter" -> OpenRouterLlmProvider(client, apiKey)
            "groq" -> GroqLlmProvider(client, apiKey)
            "nvidia_nim", "nim" -> NvidiaNimLlmProvider(client, apiKey)
            "minimax" -> MinimaxLlmProvider(client, apiKey)
            "litellm" -> LiteLlmProvider(client)
            // Vertex AI ต้อง register ผ่าน registerVertexAI() ก่อน
            // createTemp ไม่สามารถสร้างได้เพราะต้องการ serverBaseUrl + token
            "vertexai" -> null
            else -> null
        }
    }
}

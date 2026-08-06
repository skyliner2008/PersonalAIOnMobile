package com.example.personalaibot.data.providers

import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.content
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * FirebaseVertexAILlmProvider — ใช้ Firebase Vertex AI SDK โดยตรงบน Android
 * ข้อดี: ปลอดภัยที่สุด (App Check), ไม่ต้องมี Server, ฟรีค่าจัดการ
 *
 * ใช้ com.google.firebase:firebase-vertexai ผ่าน Firebase BoM
 */
class FirebaseVertexAILlmProvider : LlmProvider {
    override val providerId = "firebase_vertexai"
    override val displayName = "Vertex AI (Firebase)"
    override val supportsStreaming = true
    override val supportsFunctionCalling = false // Firebase SDK supports it, but we simplify for now

    override fun generateStream(
        messages: List<LlmMessage>,
        options: LlmOptions
    ): Flow<LlmChunk> = flow {
        val modelName = options.model ?: "gemini-1.5-flash"
        val generativeModel = Firebase.vertexAI.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                temperature = options.temperature
                maxOutputTokens = options.maxTokens ?: 8192
            },
            systemInstruction = options.systemPrompt?.let { prompt ->
                content("system") { text(prompt) }
            }
        )

        val history = messages.dropLast(1).map { msg ->
            content(if (msg.role == "user") "user" else "model") { text(msg.content) }
        }
        val chat = generativeModel.startChat(history)
        val lastMsg = messages.lastOrNull()?.content ?: ""

        chat.sendMessageStream(lastMsg).collect { response ->
            emit(LlmChunk(text = response.text ?: ""))
        }
    }

    override suspend fun generate(
        messages: List<LlmMessage>,
        options: LlmOptions
    ): LlmResult {
        val modelName = options.model ?: "gemini-1.5-flash"
        val generativeModel = Firebase.vertexAI.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                temperature = options.temperature
                maxOutputTokens = options.maxTokens ?: 8192
            },
            systemInstruction = options.systemPrompt?.let { prompt ->
                content("system") { text(prompt) }
            }
        )

        val history = messages.dropLast(1).map { msg ->
            content(if (msg.role == "user") "user" else "model") { text(msg.content) }
        }
        val chat = generativeModel.startChat(history)
        val lastMsg = messages.lastOrNull()?.content ?: ""

        val response = chat.sendMessage(lastMsg)
        return LlmResult(
            text = response.text ?: "",
            modelUsed = modelName,
            promptTokens = response.usageMetadata?.promptTokenCount?.toInt(),
            completionTokens = response.usageMetadata?.candidatesTokenCount?.toInt()
        )
    }

    override suspend fun listModels(apiKey: String): List<LlmModelInfo> {
        // Firebase Vertex AI doesn't have a list models API — return common models
        return listOf(
            LlmModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", supportsVision = true),
            LlmModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", supportsVision = true),
            LlmModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", supportsVision = true)
        )
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            // Check if Firebase is initialized
            Firebase.vertexAI
            true
        } catch (_: Exception) {
            false
        }
    }
}

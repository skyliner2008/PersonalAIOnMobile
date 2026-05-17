package com.example.personalaibot.data.providers

import com.example.personalaibot.data.models.LlmChunk
import com.example.personalaibot.data.models.LlmMessage
import com.example.personalaibot.data.models.LlmModelInfo
import com.example.personalaibot.data.models.LlmOptions
import com.example.personalaibot.data.models.LlmResult
import com.google.firebase.vertexai.VertexAI
import com.google.firebase.vertexai.type.content
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * FirebaseVertexAILlmProvider — ใช้ Firebase Vertex AI SDK โดยตรงบน Android
 * ข้อดี: ปลอดภัยที่สุด (App Check), ไม่ต้องมี Server, ฟรีค่าจัดการ
 */
class FirebaseVertexAILlmProvider : LlmProvider {
    override val providerId = "firebase_vertexai"
    override val displayName = "Vertex AI (Firebase)"
    override val supportsStreaming = true
    override val supportsFunctionCalling = false // Firebase SDK support it, but we simplify for now

    override fun generateStream(
        messages: List<LlmMessage>,
        options: LlmOptions
    ): Flow<LlmChunk> = flow {
        val modelName = options.model ?: "gemini-1.5-flash"
        val vertexAI = VertexAI(modelName)
        val generativeModel = vertexAI.generativeModel(
            generationConfig = generationConfig {
                temperature = options.temperature.toFloat()
                maxOutputTokens = options.maxTokens
            },
            systemInstruction = options.systemPrompt?.let { content { text(it) } }
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
        val vertexAI = VertexAI(modelName)
        val generativeModel = vertexAI.generativeModel(
            generationConfig = generationConfig {
                temperature = options.temperature.toFloat()
                maxOutputTokens = options.maxTokens
            },
            systemInstruction = options.systemPrompt?.let { content { text(it) } }
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
        return listOf(
            LlmModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", supportsVision = true),
            LlmModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", supportsVision = true),
            LlmModelInfo("gemini-2.0-flash-exp", "Gemini 2.0 Flash (Exp)", supportsVision = true)
        )
    }
}

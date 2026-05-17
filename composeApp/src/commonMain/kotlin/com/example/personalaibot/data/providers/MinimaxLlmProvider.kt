package com.example.personalaibot.data.providers

import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.plugins.timeout
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/**
 * MinimaxLlmProvider — OpenAI-compatible Chat API via MiniMax
 *
 * Supports: abab6.5s, abab6.5, MiniMax-M2.7, etc.
 * Features: SSE Streaming + Function Calling
 *
 * @since 2026-05-04
 */
class MinimaxLlmProvider(
    private val client: HttpClient,
    private val apiKey: String
) : LlmProvider {

    override val providerId = "minimax"
    override val displayName = "MiniMax"
    override val supportsStreaming = true
    override val supportsFunctionCalling = true

    private val baseUrl = "https://api.minimax.io/v1"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Serialize an LlmMessage in OpenAI-compatible format. */
    private fun JsonObjectBuilder.putOpenAiMessage(msg: LlmMessage) {
        put("role", msg.role)
        if (msg.role == "tool") {
            msg.toolCallId?.let { put("tool_call_id", it) }
            put("content", msg.content)
            return
        }
        if (msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty()) {
            put("content", msg.content)
            putJsonArray("tool_calls") {
                msg.toolCalls.forEach { tc ->
                    addJsonObject {
                        put("id", tc.id)
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", tc.name)
                            put("arguments", tc.arguments)
                        }
                    }
                }
            }
            return
        }
        put("content", msg.content)
    }

    override fun generateStream(
        messages: List<LlmMessage>,
        options: LlmOptions
    ): Flow<LlmChunk> = flow {
        val model = options.model ?: "abab6.5s-chat"
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("temperature", options.temperature.toDouble())
            options.maxTokens?.let { put("max_tokens", it) }

            putJsonArray("messages") {
                options.systemPrompt?.let { sys ->
                    addJsonObject {
                        put("role", "system")
                        put("content", sys)
                    }
                }
                messages.forEach { msg ->
                    addJsonObject { putOpenAiMessage(msg) }
                }
            }

            // Tools support
            options.tools?.let { tools ->
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", json.parseToJsonElement(tool.parametersJson))
                            }
                        }
                    }
                }
            }
        }

        try {
            client.preparePost("$baseUrl/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
                timeout { requestTimeoutMillis = options.timeoutMs }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errBody = try { response.bodyAsText() } catch (_: Exception) { "Unknown error" }
                    logError("MiniMax", "API Error ${response.status.value}: $errBody")
                    emit(LlmChunk(text = "⚠️ MiniMax Error ${response.status.value}: $errBody"))
                    return@execute
                }
                val channel = response.bodyAsChannel()
                val toolCallAccum = mutableMapOf<Int, Triple<String, String, StringBuilder>>()

                while (!channel.isClosedForRead) {
                    val line = try { channel.readUTF8Line()?.trim() } catch (e: Exception) { null } ?: break
                    if (line.isBlank()) continue

                    if (line.startsWith("data: ")) {
                        val dataStr = line.removePrefix("data: ").trim()
                        if (dataStr == "[DONE]") break
                        if (dataStr.isBlank()) continue

                        try {
                            val data = json.parseToJsonElement(dataStr).jsonObject
                            val choices = data["choices"]?.jsonArray
                            if (choices.isNullOrEmpty()) continue

                            val choice = choices[0].jsonObject
                            val delta = choice["delta"]?.jsonObject ?: continue

                            // 1. Text delta
                            val content = delta["content"]?.jsonPrimitive?.content
                            if (!content.isNullOrEmpty()) {
                                emit(LlmChunk(text = content))
                            }

                            // 2. Tool calls delta
                            val toolCalls = delta["tool_calls"]?.jsonArray
                            toolCalls?.forEach { tcElement ->
                                val tc = tcElement.jsonObject
                                val index = tc["index"]?.jsonPrimitive?.int ?: 0
                                val id = tc["id"]?.jsonPrimitive?.content
                                val function = tc["function"]?.jsonObject
                                val name = function?.get("name")?.jsonPrimitive?.content
                                val argsDelta = function?.get("arguments")?.jsonPrimitive?.content ?: ""

                                val existing = toolCallAccum[index]
                                if (existing == null) {
                                    toolCallAccum[index] = Triple(id ?: "", name ?: "", StringBuilder(argsDelta))
                                } else {
                                    existing.third.append(argsDelta)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Flush tool calls
                if (toolCallAccum.isNotEmpty()) {
                    val finalCalls = toolCallAccum.values.map { (id, name, args) ->
                        LlmToolCall(id, name, args.toString())
                    }
                    emit(LlmChunk(toolCalls = finalCalls))
                }
            }
        } catch (e: Exception) {
            logError("MiniMax", "Stream failed: ${e.message}")
            emit(LlmChunk(text = "⚠️ MiniMax Connection Error: ${e.message}"))
        }
    }

    override suspend fun listModels(): List<LlmModel> {
        return try {
            val response = client.get("$baseUrl/models") {
                header("Authorization", "Bearer $apiKey")
            }
            if (response.status.isSuccess()) {
                val body: JsonObject = response.body()
                val data = body["data"]?.jsonArray ?: return fallbackModels()
                data.mapNotNull {
                    val m = it.jsonObject
                    val id = m["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    LlmModel(id, id)
                }
            } else fallbackModels()
        } catch (e: Exception) {
            fallbackModels()
        }
    }

    private fun fallbackModels() = listOf(
        LlmModel("abab6.5s-chat", "abab6.5s-chat"),
        LlmModel("abab6.5-chat", "abab6.5-chat"),
        LlmModel("MiniMax-M2.7", "MiniMax-M2.7"),
        LlmModel("MiniMax-M2.5", "MiniMax-M2.5")
    )

    override fun isAvailable(): Boolean = apiKey.isNotBlank()
}

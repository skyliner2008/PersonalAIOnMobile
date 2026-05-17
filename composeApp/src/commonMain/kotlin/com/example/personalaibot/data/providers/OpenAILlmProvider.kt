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
 * OpenAILlmProvider — OpenAI Chat Completions API
 *
 * รองรับ: GPT-4o, GPT-4o-mini, o1, o3, etc.
 * Features: SSE streaming + function/tool calling
 *
 * @since 2026-04-29
 */
class OpenAILlmProvider(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1"
) : LlmProvider {

    override val providerId = "openai"
    override val displayName = "OpenAI"
    override val supportsStreaming = true
    override val supportsFunctionCalling = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Serialize an LlmMessage in OpenAI Chat-Completions format, preserving tool_calls / tool_call_id. */
    private fun JsonObjectBuilder.putOpenAiMessage(msg: LlmMessage) {
        put("role", msg.role)
        if (msg.role == "tool") {
            // tool messages MUST carry tool_call_id and a string content
            msg.toolCallId?.let { put("tool_call_id", it) }
            put("content", msg.content)
            return
        }
        // assistant messages may carry tool_calls; content may be empty in that case
        if (msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty()) {
            // OpenAI accepts content=null when tool_calls present; we pass empty string for safety
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
        val model = options.model ?: "gpt-4o-mini"
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("temperature", options.temperature.toDouble())
            options.maxTokens?.let { put("max_tokens", it) }
            putJsonArray("messages") {
                options.systemPrompt?.let { sys ->
                    addJsonObject { put("role", "system"); put("content", sys) }
                }
                messages.forEach { msg ->
                    addJsonObject { putOpenAiMessage(msg) }
                }
            }
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

        client.preparePost("$baseUrl/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
            timeout { requestTimeoutMillis = options.timeoutMs }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errBody = try { response.bodyAsText() } catch (_: Exception) { "Unknown error" }
                logError("OpenAI", "API Error ${response.status.value}: $errBody")
                emit(LlmChunk(text = "⚠️ OpenAI Error ${response.status.value}: $errBody"))
                return@execute
            }
            val channel = response.bodyAsChannel()
            var dataCount = 0
            // ── Accumulate streaming tool_calls deltas by index ──
            val toolCallAccum = mutableMapOf<Int, Triple<String, String, StringBuilder>>()

            while (!channel.isClosedForRead) {
                val line = try { channel.readUTF8Line()?.trim() } catch (e: Exception) {
                    logError("OpenAI", "Read line error: ${e.message}")
                    null
                } ?: break
                if (line.isBlank()) continue
                logDebug("OpenAI", "Stream line: $line")

                if (line.startsWith("data: ")) {
                    val dataStr = line.removePrefix("data: ").trim()
                    if (dataStr == "[DONE]") break
                    if (dataStr.isBlank()) continue
                    try {
                        val data = json.parseToJsonElement(dataStr).jsonObject

                        // Check for error in JSON
                        data["error"]?.jsonObject?.let { err ->
                            val msg = err["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                            logError("OpenAI", "SSE Error: $msg")
                            emit(LlmChunk(text = "⚠️ OpenAI Error: $msg"))
                            return@execute
                        }

                        val firstChoice = data["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        val delta = firstChoice?.get("delta")?.jsonObject
                        val content = delta?.get("content")?.jsonPrimitive?.contentOrNull
                        val finish = firstChoice?.get("finish_reason")?.jsonPrimitive?.contentOrNull

                        // Accumulate Tool Call deltas by index (don't emit yet)
                        val toolCallsArr = delta?.get("tool_calls")?.jsonArray
                        if (toolCallsArr != null) {
                            toolCallsArr.forEach { tc ->
                                try {
                                    val tcObj = tc.jsonObject
                                    val idx = tcObj["index"]?.jsonPrimitive?.intOrNull ?: 0
                                    val id = tcObj["id"]?.jsonPrimitive?.contentOrNull
                                    val funcObj = tcObj["function"]?.jsonObject
                                    val name = funcObj?.get("name")?.jsonPrimitive?.contentOrNull
                                    val argsPart = funcObj?.get("arguments")?.jsonPrimitive?.contentOrNull
                                    val existing = toolCallAccum[idx]
                                    if (existing != null) {
                                        if (argsPart != null) existing.third.append(argsPart)
                                        toolCallAccum[idx] = Triple(
                                            id ?: existing.first,
                                            name ?: existing.second,
                                            existing.third
                                        )
                                    } else {
                                        toolCallAccum[idx] = Triple(
                                            id ?: "",
                                            name ?: "",
                                            StringBuilder(argsPart ?: "")
                                        )
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        // Emit text/finishReason chunks (tool_calls emitted after loop)
                        if (!content.isNullOrEmpty() || finish != null) {
                            dataCount++
                            emit(LlmChunk(
                                text = content ?: "",
                                finishReason = if (toolCallAccum.isEmpty()) finish else null
                            ))
                        }
                    } catch (e: Exception) {
                        logError("OpenAI", "Parse error: ${e.message} in line: $line")
                    }
                }
            }
            // Emit accumulated tool calls as a single complete batch
            if (toolCallAccum.isNotEmpty()) {
                val completedCalls = toolCallAccum.entries.sortedBy { it.key }.map { (_, v) ->
                    LlmToolCall(id = v.first, name = v.second, arguments = v.third.toString())
                }
                logDebug("OpenAI", "Emitting ${completedCalls.size} accumulated tool call(s): ${completedCalls.map { it.name }}")
                dataCount++
                emit(LlmChunk(text = "", toolCalls = completedCalls, finishReason = "tool_calls"))
            }
            if (dataCount == 0) {
                logError("OpenAI", "No valid data received from stream")
                emit(LlmChunk(text = "⚠️ ไม่ได้รับข้อมูลจาก OpenAI (Empty Data Stream)"))
            }
        }
    }

    override suspend fun generate(messages: List<LlmMessage>, options: LlmOptions): LlmResult {
        val model = options.model ?: "gpt-4o-mini"
        val body = buildJsonObject {
            put("model", model); put("stream", false)
            put("temperature", options.temperature.toDouble())
            options.maxTokens?.let { put("max_tokens", it) }
            putJsonArray("messages") {
                options.systemPrompt?.let { addJsonObject { put("role", "system"); put("content", it) } }
                messages.forEach { msg -> addJsonObject { putOpenAiMessage(msg) } }
            }
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
        val resp = client.post("$baseUrl/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val respJson = json.parseToJsonElement(resp.body<String>()).jsonObject
        val choice = respJson["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val message = choice?.get("message")?.jsonObject
        val finishReason = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull
        val toolCallsArr = message?.get("tool_calls")?.jsonArray
        val toolCalls = toolCallsArr?.mapNotNull { tc ->
            try {
                val tcObj = tc.jsonObject
                val id = tcObj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                val funcObj = tcObj["function"]?.jsonObject
                val name = funcObj?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
                val args = funcObj?.get("arguments")?.jsonPrimitive?.contentOrNull ?: ""
                LlmToolCall(id = id, name = name, arguments = args)
            } catch (_: Exception) { null }
        }?.takeIf { it.isNotEmpty() }
        val usage = respJson["usage"]?.jsonObject
        return LlmResult(
            text = message?.get("content")?.jsonPrimitive?.contentOrNull ?: "",
            toolCalls = toolCalls,
            finishReason = finishReason,
            modelUsed = model,
            promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull,
            completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull
        )
    }

    override suspend fun listModels(apiKey: String): List<LlmModelInfo> {
        return try {
            val resp = client.get("$baseUrl/models") {
                header("Authorization", "Bearer $apiKey")
            }
            val respText = resp.body<String>()
            val respJson = json.parseToJsonElement(respText).jsonObject

            // Check for error response first
            respJson["error"]?.jsonObject?.let { errorObj ->
                val errorMsg = errorObj["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                val errorCode = errorObj["code"]?.jsonPrimitive?.contentOrNull
                logError("OpenAI", "listModels error (${errorCode}): $errorMsg")
                // Throw exception so ApiKeyTester can catch it properly
                throw Exception("API Error: $errorMsg")
            }

            val data = respJson["data"]?.jsonArray
            if (data == null) {
                logError("OpenAI", "listModels: 'data' field missing from response: ${respText.take(500)}")
                return emptyList()
            }
            logDebug("OpenAI", "listModels: received ${data.size} models from API")
            data.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                // Exclude non-chat models (image, audio, tts, whisper, moderation, embedding)
                // Keep: gpt-*, o1-*, o3-*, o4-*, chatgpt-*, codex-* and any future chat models
                val isNonChat = id.startsWith("dall-e") ||
                    id.startsWith("gpt-image") ||
                    id.startsWith("gpt-realtime") ||
                    id.startsWith("gpt-audio") ||
                    id.startsWith("whisper") ||
                    id.startsWith("tts") ||
                    id.startsWith("text-embedding") ||
                    id.startsWith("text-moderation") ||
                    id.contains("-embedding-") ||
                    id.contains("-tts-") ||
                    id.startsWith("omni-moderation")
                if (isNonChat) return@mapNotNull null
                LlmModelInfo(
                    id = id, displayName = id,
                    supportsFunctions = id.startsWith("gpt") || id.startsWith("o") || id.startsWith("chatgpt") || id.startsWith("codex")
                )
            }.sortedBy { it.id }
        } catch (e: Exception) {
            logError("OpenAI", "Failed to list models: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun isAvailable(): Boolean = apiKey.isNotBlank()
}

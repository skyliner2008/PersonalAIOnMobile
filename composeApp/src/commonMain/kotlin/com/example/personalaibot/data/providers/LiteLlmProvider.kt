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
 * LiteLlmProvider — OpenAI-compatible proxy สำหรับ Ollama/local models
 *
 * ใช้ LiteLLM Proxy ที่ติดตั้งบน server เพื่อเข้าถึง local models
 * (Ollama, vLLM, etc.) ผ่าน OpenAI-compatible API
 *
 * @since 2026-04-29
 */
class LiteLlmProvider(
    private val client: HttpClient,
    private val baseUrl: String = "http://localhost:4000"
) : LlmProvider {

    override val providerId = "litellm"
    override val displayName = "LiteLLM (Proxy)"
    override val supportsStreaming = true
    override val supportsFunctionCalling = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Serialize an LlmMessage in OpenAI-compatible format (LiteLLM proxies OpenAI schema). */
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
        val model = options.model ?: "ollama/llama3.2"
        val body = buildJsonObject {
            put("model", model); put("stream", true)
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

        client.preparePost("$baseUrl/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
            timeout { requestTimeoutMillis = options.timeoutMs }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errBody = try { response.bodyAsText() } catch (_: Exception) { "Unknown error" }
                logError("LiteLLM", "API Error ${response.status.value}: $errBody")
                emit(LlmChunk(text = "⚠️ LiteLLM Error ${response.status.value}: $errBody"))
                return@execute
            }
            val channel = response.bodyAsChannel()
            var lineCount = 0
            // ── Accumulate streaming tool_calls deltas by index ──
            val toolCallAccum = mutableMapOf<Int, Triple<String, String, StringBuilder>>()

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line()?.trim() ?: break
                lineCount++
                if (line.startsWith("data: ")) {
                    val dataStr = line.removePrefix("data: ").trim()
                    if (dataStr == "[DONE]") break
                    if (dataStr.isBlank()) continue
                    try {
                        val data = json.parseToJsonElement(dataStr).jsonObject
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
                            emit(LlmChunk(
                                text = content ?: "",
                                finishReason = if (toolCallAccum.isEmpty()) finish else null
                            ))
                        }
                    } catch (_: Exception) {}
                }
            }
            // Emit accumulated tool calls as a single complete batch
            if (toolCallAccum.isNotEmpty()) {
                val completedCalls = toolCallAccum.entries.sortedBy { it.key }.map { (_, v) ->
                    LlmToolCall(id = v.first, name = v.second, arguments = v.third.toString())
                }
                logDebug("LiteLLM", "Emitting ${completedCalls.size} accumulated tool call(s): ${completedCalls.map { it.name }}")
                emit(LlmChunk(text = "", toolCalls = completedCalls, finishReason = "tool_calls"))
            }
            if (lineCount == 0) {
                emit(LlmChunk(text = "⚠️ ไม่ได้รับข้อมูลจาก LiteLLM (Empty Stream)"))
            }
        }
    }

    override suspend fun generate(messages: List<LlmMessage>, options: LlmOptions): LlmResult {
        val model = options.model ?: "ollama/llama3.2"
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
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val respJson = json.parseToJsonElement(resp.body<String>()).jsonObject
        val choice = respJson["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val message = choice?.get("message")?.jsonObject
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
            finishReason = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull,
            modelUsed = model,
            promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull,
            completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull
        )
    }

    override suspend fun listModels(apiKey: String): List<LlmModelInfo> {
        return try {
            val resp = client.get("$baseUrl/models")
            val respJson = json.parseToJsonElement(resp.body<String>()).jsonObject
            val data = respJson["data"]?.jsonArray ?: return emptyList()
            data.mapNotNull { el ->
                val id = el.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                LlmModelInfo(id = id, displayName = id, isFree = true)
            }
        } catch (e: Exception) {
            logError("LiteLLM", "Failed to list models: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            val resp = client.get("$baseUrl/health")
            resp.status.isSuccess()
        } catch (_: Exception) { false }
    }
}

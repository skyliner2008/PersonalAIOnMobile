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
 * ClaudeLlmProvider — Anthropic Messages API
 *
 * รองรับ: Claude 4 Sonnet, Claude 4 Opus, Haiku, etc.
 * Features: SSE streaming + tool_use
 *
 * @since 2026-04-29
 */
class ClaudeLlmProvider(
    private val client: HttpClient,
    private val apiKey: String
) : LlmProvider {

    override val providerId = "claude"
    override val displayName = "Claude (Anthropic)"
    override val supportsStreaming = true
    override val supportsFunctionCalling = true

    private val baseUrl = "https://api.anthropic.com/v1"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Convert generic LlmMessage list into Anthropic-native message format.
     * Anthropic uses content blocks instead of OpenAI's `tool_calls` / `tool` role.
     *
     * - assistant + toolCalls → assistant message with `tool_use` blocks
     * - role="tool" → user message with `tool_result` block (tool_use_id = toolCallId)
     */
    private fun JsonArrayBuilder.appendAnthropicMessages(messages: List<LlmMessage>) {
        // Collapse consecutive `tool` results into a single user turn (Anthropic requires alternation).
        val pending = mutableListOf<LlmMessage>()
        fun flush() {
            if (pending.isEmpty()) return
            // All tool results
            if (pending.all { it.role == "tool" }) {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        pending.forEach { tr ->
                            addJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", tr.toolCallId ?: "")
                                put("content", tr.content)
                            }
                        }
                    }
                }
            } else {
                pending.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.role == "assistant") "assistant" else "user")
                        if (msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty()) {
                            putJsonArray("content") {
                                if (msg.content.isNotBlank()) {
                                    addJsonObject {
                                        put("type", "text")
                                        put("text", msg.content)
                                    }
                                }
                                msg.toolCalls.forEach { tc ->
                                    addJsonObject {
                                        put("type", "tool_use")
                                        put("id", tc.id)
                                        put("name", tc.name)
                                        put("input", try {
                                            json.parseToJsonElement(tc.arguments.ifBlank { "{}" })
                                        } catch (_: Exception) {
                                            buildJsonObject {}
                                        })
                                    }
                                }
                            }
                        } else {
                            put("content", msg.content)
                        }
                    }
                }
            }
            pending.clear()
        }
        messages.forEach { msg ->
            if (msg.role == "tool") {
                pending.add(msg)
            } else {
                flush()
                pending.add(msg)
                flush()
            }
        }
        flush()
    }

    override fun generateStream(
        messages: List<LlmMessage>,
        options: LlmOptions
    ): Flow<LlmChunk> = flow {
        val model = options.model ?: "claude-sonnet-4-20250514"
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("max_tokens", options.maxTokens ?: 4096)
            put("temperature", options.temperature.toDouble())
            
            // --- Prompt Caching (Anthropic Beta) ---
            options.systemPrompt?.let { 
                putJsonArray("system") {
                    addJsonObject {
                        put("type", "text")
                        put("text", it)
                        putJsonObject("cache_control") {
                            put("type", "ephemeral")
                        }
                    }
                }
            }

            putJsonArray("messages") {
                appendAnthropicMessages(messages)
            }
            options.tools?.let { tools ->
                putJsonArray("tools") {
                    tools.forEachIndexed { index, tool ->
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", json.parseToJsonElement(tool.parametersJson))
                            // Cache the last tool to ensure the tool definitions are cached as part of the prefix
                            if (index == tools.size - 1) {
                                putJsonObject("cache_control") {
                                    put("type", "ephemeral")
                                }
                            }
                        }
                    }
                }
            }
        }

        client.preparePost("$baseUrl/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
            timeout { requestTimeoutMillis = options.timeoutMs }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errBody = try { response.bodyAsText() } catch (_: Exception) { "Unknown error" }
                logError("Claude", "API Error ${response.status.value}: $errBody")
                emit(LlmChunk(text = "⚠️ Claude Error ${response.status.value}: $errBody"))
                return@execute
            }
            val channel = response.bodyAsChannel()
            var dataCount = 0
            // Tool-use accumulation: index → (id, name, partial JSON)
            val toolBuffers = mutableMapOf<Int, ToolBuffer>()
            var stopReason: String? = null
            while (!channel.isClosedForRead) {
                val line = try { channel.readUTF8Line()?.trim() } catch (e: Exception) {
                    logError("Claude", "Read line error: ${e.message}")
                    null
                } ?: break
                if (line.isBlank()) continue
                logDebug("Claude", "Stream line: $line")

                if (line.startsWith("data: ")) {
                    try {
                        val dataStr = line.removePrefix("data: ")
                        if (dataStr.isBlank()) continue
                        val data = json.parseToJsonElement(dataStr).jsonObject
                        val type = data["type"]?.jsonPrimitive?.contentOrNull

                        // Check for error in JSON (Claude format)
                        if (type == "error") {
                            val errObj = data["error"]?.jsonObject
                            val msg = errObj?.get("message")?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                            logError("Claude", "SSE Error: $msg")
                            emit(LlmChunk(text = "⚠️ Claude Error: $msg"))
                            return@execute
                        }

                        when (type) {
                            "content_block_start" -> {
                                val idx = data["index"]?.jsonPrimitive?.intOrNull ?: -1
                                val block = data["content_block"]?.jsonObject
                                val blockType = block?.get("type")?.jsonPrimitive?.contentOrNull
                                if (blockType == "tool_use") {
                                    val id = block["id"]?.jsonPrimitive?.contentOrNull ?: ""
                                    val name = block["name"]?.jsonPrimitive?.contentOrNull ?: ""
                                    toolBuffers[idx] = ToolBuffer(id = id, name = name, json = StringBuilder())
                                }
                            }
                            "content_block_delta" -> {
                                val idx = data["index"]?.jsonPrimitive?.intOrNull ?: -1
                                val delta = data["delta"]?.jsonObject
                                val deltaType = delta?.get("type")?.jsonPrimitive?.contentOrNull
                                when (deltaType) {
                                    "text_delta" -> {
                                        val text = delta["text"]?.jsonPrimitive?.contentOrNull
                                        if (!text.isNullOrEmpty()) {
                                            dataCount++
                                            emit(LlmChunk(text = text))
                                        }
                                    }
                                    "input_json_delta" -> {
                                        val partial = delta["partial_json"]?.jsonPrimitive?.contentOrNull
                                        if (!partial.isNullOrEmpty()) {
                                            toolBuffers[idx]?.json?.append(partial)
                                        }
                                    }
                                }
                            }
                            "content_block_stop" -> {
                                val idx = data["index"]?.jsonPrimitive?.intOrNull ?: -1
                                toolBuffers[idx]?.let { buf ->
                                    val args = buf.json.toString().ifBlank { "{}" }
                                    dataCount++
                                    emit(LlmChunk(toolCalls = listOf(
                                        LlmToolCall(id = buf.id, name = buf.name, arguments = args)
                                    )))
                                }
                            }
                            "message_delta" -> {
                                val delta = data["delta"]?.jsonObject
                                stopReason = delta?.get("stop_reason")?.jsonPrimitive?.contentOrNull ?: stopReason
                            }
                            "message_stop" -> {
                                dataCount++
                                emit(LlmChunk(finishReason = stopReason ?: "stop"))
                            }
                        }
                    } catch (e: Exception) {
                        logError("Claude", "Parse error: ${e.message} in line: $line")
                    }
                }
            }
            if (dataCount == 0) {
                logError("Claude", "No valid data received from stream")
                emit(LlmChunk(text = "⚠️ ไม่ได้รับข้อมูลจาก Claude (Empty Data Stream)"))
            }
        }
    }

    /** Per-block buffer used while streaming Claude tool_use content. */
    private data class ToolBuffer(val id: String, val name: String, val json: StringBuilder)

    override suspend fun generate(messages: List<LlmMessage>, options: LlmOptions): LlmResult {
        val model = options.model ?: "claude-sonnet-4-20250514"
        val body = buildJsonObject {
            put("model", model); put("max_tokens", options.maxTokens ?: 4096)
            put("temperature", options.temperature.toDouble())
            options.systemPrompt?.let { put("system", it) }
            putJsonArray("messages") {
                appendAnthropicMessages(messages)
            }
            options.tools?.let { tools ->
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", json.parseToJsonElement(tool.parametersJson))
                        }
                    }
                }
            }
        }
        val resp = client.post("$baseUrl/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val respJson = json.parseToJsonElement(resp.body<String>()).jsonObject
        val contentArr = respJson["content"]?.jsonArray ?: emptyList()
        val textBuilder = StringBuilder()
        val toolCalls = mutableListOf<LlmToolCall>()
        contentArr.forEach { el ->
            val obj = el.jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "text" -> obj["text"]?.jsonPrimitive?.contentOrNull?.let { textBuilder.append(it) }
                "tool_use" -> {
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val input = obj["input"]?.toString() ?: "{}"
                    toolCalls.add(LlmToolCall(id = id, name = name, arguments = input))
                }
            }
        }
        val usage = respJson["usage"]?.jsonObject
        val stopReason = respJson["stop_reason"]?.jsonPrimitive?.contentOrNull
        return LlmResult(
            text = textBuilder.toString(),
            toolCalls = if (toolCalls.isEmpty()) null else toolCalls,
            finishReason = stopReason,
            modelUsed = model,
            promptTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull,
            completionTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull
        )
    }

    override suspend fun listModels(apiKey: String): List<LlmModelInfo> {
        // Anthropic มี GET /v1/models API แล้ว (2024+) — ลองดึงของจริงก่อน
        // เพื่อให้รายการ models อัปเดตตามบัญชีผู้ใช้เสมอ
        // ถ้า API ล้มเหลว (key เก่า/network) → fallback เป็น static list
        val fromApi = fetchModelsFromApi(apiKey)
        if (fromApi.isNotEmpty()) return fromApi

        logDebug("Claude", "Models API unavailable — falling back to static list")
        return fallbackModels
    }

    /** ดึงรายการ models จาก Anthropic Models API (GET /v1/models) */
    private suspend fun fetchModelsFromApi(apiKey: String): List<LlmModelInfo> {
        return try {
            val response = client.get("$baseUrl/models") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
            }
            if (!response.status.isSuccess()) {
                logError("Claude", "Models API error ${response.status.value}")
                return emptyList()
            }
            val respJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val data = respJson["data"]?.jsonArray ?: return emptyList()
            data.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val name = obj["display_name"]?.jsonPrimitive?.contentOrNull ?: id
                    LlmModelInfo(
                        id = id,
                        displayName = name,
                        contextLength = 200000,
                        supportsFunctions = true,
                        supportsVision = true
                    )
                } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            logError("Claude", "Failed to fetch models: ${e.message}")
            emptyList()
        }
    }

    /** Static fallback — ใช้เมื่อ Models API ไม่พร้อม */
    private val fallbackModels = listOf(
        LlmModelInfo("claude-sonnet-4-20250514", "Claude Sonnet 4", 200000, supportsFunctions = true),
        LlmModelInfo("claude-haiku-4-5-20251001", "Claude Haiku 4.5", 200000, supportsFunctions = true),
        LlmModelInfo("claude-opus-4-20250514", "Claude Opus 4", 200000, supportsFunctions = true),
        LlmModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", 200000, supportsFunctions = true),
    )

    override suspend fun isAvailable(): Boolean = apiKey.isNotBlank()
}

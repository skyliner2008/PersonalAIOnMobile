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
 * OpenRouterLlmProvider — OpenAI-compatible Chat API ผ่าน OpenRouter
 *
 * Features:
 * - รองรับ 200+ models จากทุก provider (OpenAI, Claude, Mistral, Google, etc.)
 * - Free model filter: ดึง pricing จาก API → กรอง models ฟรีได้
 * - SSE Streaming
 * - Function/Tool calling (ผ่าน OpenAI-compatible format)
 * - listModels พร้อม pricing data
 *
 * @since 2026-04-29
 */
class OpenRouterLlmProvider(
    private val client: HttpClient,
    private val apiKey: String
) : LlmProvider {

    override val providerId = "openrouter"
    override val displayName = "OpenRouter"
    override val supportsStreaming = true
    override val supportsFunctionCalling = true

    private val baseUrl = "https://openrouter.ai/api/v1"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Serialize an LlmMessage in OpenAI-compatible format, preserving tool_calls / tool_call_id. */
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
        val model = options.model ?: "openai/gpt-4o-mini"
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

        client.preparePost("$baseUrl/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            header("HTTP-Referer", "https://personalaibot.app")
            header("X-Title", "PersonalAIBot")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
            timeout { requestTimeoutMillis = options.timeoutMs }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errBody = try { response.bodyAsText() } catch (_: Exception) { "Unknown error" }
                logError("OpenRouter", "API Error ${response.status.value}: $errBody")
                emit(LlmChunk(text = "⚠️ OpenRouter Error ${response.status.value}: $errBody"))
                return@execute
            }
            val channel = response.bodyAsChannel()
            var dataCount = 0
            // ── Accumulate streaming tool_calls deltas by index ──
            // OpenAI streaming sends tool_calls as partial deltas:
            //   chunk 1: {index:0, id:"call_xxx", function:{name:"foo", arguments:""}}
            //   chunk 2: {index:0, function:{arguments:"{\"sy"}}
            //   chunk N: {index:0, function:{arguments:"}"}}
            // We must merge them by index before emitting.
            val toolCallAccum = mutableMapOf<Int, Triple<String, String, StringBuilder>>()

            while (!channel.isClosedForRead) {
                val line = try { channel.readUTF8Line()?.trim() } catch (e: Exception) {
                    logError("OpenRouter", "Read line error: ${e.message}")
                    null
                } ?: break
                if (line.isBlank()) continue

                logDebug("OpenRouter", "Stream line: $line")

                if (line.startsWith("data: ")) {
                    val dataStr = line.removePrefix("data: ").trim()
                    if (dataStr == "[DONE]") {
                        logDebug("OpenRouter", "Stream [DONE] reached")
                        break
                    }
                    if (dataStr.isBlank()) continue

                    try {
                        val data = json.parseToJsonElement(dataStr).jsonObject

                        // Check for API-level error in SSE
                        val error = data["error"]?.jsonObject
                        if (error != null) {
                            val errMsg = error["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                            logError("OpenRouter", "SSE Error: $errMsg")
                            emit(LlmChunk(text = "⚠️ OpenRouter Error: $errMsg"))
                            return@execute
                        }

                        val choices = data["choices"]?.jsonArray
                        if (choices == null || choices.isEmpty()) continue

                        val firstChoice = choices[0].jsonObject
                        val delta = firstChoice["delta"]?.jsonObject
                        val content = delta?.get("content")?.jsonPrimitive?.contentOrNull
                        val finishReason = firstChoice["finish_reason"]?.jsonPrimitive?.contentOrNull

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
                        if (!content.isNullOrEmpty() || finishReason != null) {
                            dataCount++
                            emit(LlmChunk(
                                text = content ?: "",
                                finishReason = if (toolCallAccum.isEmpty()) finishReason else null
                            ))
                        }
                    } catch (e: Exception) {
                        logError("OpenRouter", "Parse error: ${e.message} in line: $line")
                    }
                }
            }
            // Emit accumulated tool calls as a single complete batch
            if (toolCallAccum.isNotEmpty()) {
                val completedCalls = toolCallAccum.entries.sortedBy { it.key }.map { (_, v) ->
                    LlmToolCall(id = v.first, name = v.second, arguments = v.third.toString())
                }
                logDebug("OpenRouter", "Emitting ${completedCalls.size} accumulated tool call(s): ${completedCalls.map { it.name }}")
                dataCount++
                emit(LlmChunk(text = "", toolCalls = completedCalls, finishReason = "tool_calls"))
            }
            if (dataCount == 0) {
                logError("OpenRouter", "No valid data chunks received from stream")
                emit(LlmChunk(text = "⚠️ ไม่ได้รับข้อมูลที่ถูกต้องจาก OpenRouter (Empty Data Stream)"))
            }
        }
    }

    override suspend fun generate(
        messages: List<LlmMessage>,
        options: LlmOptions
    ): LlmResult {
        val model = options.model ?: "openai/gpt-4o-mini"
        val body = buildJsonObject {
            put("model", model)
            put("stream", false)
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

        val response = client.post("$baseUrl/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            header("HTTP-Referer", "https://personalaibot.app")
            header("X-Title", "PersonalAIBot")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }

        val respJson = json.parseToJsonElement(response.body<String>()).jsonObject
        val choice = respJson["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val message = choice?.get("message")?.jsonObject
        val text = message?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
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
            text = text,
            toolCalls = toolCalls,
            modelUsed = model,
            promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull,
            completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull,
            finishReason = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull
        )
    }

    /**
     * ดึงรายการ models จาก OpenRouter — พร้อม pricing data สำหรับ free filter
     */
    override suspend fun listModels(apiKey: String): List<LlmModelInfo> {
        return try {
            val response = client.get("$baseUrl/models") {
                header("Authorization", "Bearer $apiKey")
            }
            val respJson = json.parseToJsonElement(response.body<String>()).jsonObject
            val data = respJson["data"]?.jsonArray ?: return emptyList()

            data.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: id
                    val contextLen = obj["context_length"]?.jsonPrimitive?.intOrNull

                    // Parse pricing — OpenRouter returns $/token as string
                    val pricingObj = obj["pricing"]?.jsonObject
                    val promptPrice = pricingObj?.get("prompt")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
                    val completionPrice = pricingObj?.get("completion")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
                    // Convert from $/token to $/1M tokens
                    val promptPer1M = promptPrice * 1_000_000
                    val completionPer1M = completionPrice * 1_000_000
                    val isFree = promptPrice == 0.0 && completionPrice == 0.0

                    // อ่าน capability จริงจาก API แทนการเดาจากชื่อ:
                    // - supported_parameters มี "tools" → รองรับ function calling
                    // - architecture.input_modalities มี "image" → รองรับ vision
                    val supportedParams = obj["supported_parameters"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    val inputModalities = obj["architecture"]?.jsonObject
                        ?.get("input_modalities")?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    val supportsTools = if (supportedParams.isNotEmpty()) {
                        "tools" in supportedParams
                    } else {
                        // fallback heuristic สำหรับ API เวอร์ชันเก่าที่ไม่มี field นี้
                        id.contains("gpt") || id.contains("claude") || id.contains("gemini")
                    }

                    LlmModelInfo(
                        id = id,
                        displayName = name,
                        contextLength = contextLen,
                        isFree = isFree,
                        pricing = LlmPricing(
                            promptPer1M = promptPer1M,
                            completionPer1M = completionPer1M
                        ),
                        supportsFunctions = supportsTools,
                        supportsVision = "image" in inputModalities
                    )
                } catch (e: Exception) {
                    logError("OpenRouter", "Failed to parse model: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logError("OpenRouter", "Failed to list models: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun isAvailable(): Boolean = apiKey.isNotBlank()
}

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
 * OpenAiCompatLlmProvider — Generic OpenAI-compatible Chat Completions provider
 * สำหรับ provider ฟรีที่ใช้ API แบบ OpenAI มาตรฐาน (Bearer key + /chat/completions + /models)
 *
 * ใช้กับ:
 * - Groq   (https://api.groq.com/openai/v1) — free tier: llama-3.1-8b-instant RPD 14.4K,
 *           llama-3.3-70b-versatile RPD 1K, openai/gpt-oss-120b/20b RPD 1K (ดู console.groq.com/docs/rate-limits)
 * - NVIDIA NIM (https://integrate.api.nvidia.com/v1) — free dev credits, key prefix nvapi-
 *
 * 2026-08-08 — เพิ่มตาม request: ตัด provider ที่ไม่มี free tier ออก เหลือ Gemini/OpenRouter + Groq/NIM
 */
open class OpenAiCompatLlmProvider(
    private val client: HttpClient,
    private val apiKey: String,
    override val providerId: String,
    override val displayName: String,
    private val baseUrl: String,
    private val defaultModel: String,
    /** true = ทุก model ของ provider นี้ถือว่าฟรี (free tier) — ไม่ต้องอ่าน pricing */
    private val allFree: Boolean = true,
    /** filter model id ที่ไม่ใช่ chat model ออกจาก listModels (เช่น whisper, prompt-guard, embedding) */
    private val excludeModelPattern: Regex? = null,
) : LlmProvider {

    override val supportsStreaming = true
    override val supportsFunctionCalling = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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
        val body = buildJsonObject {
            put("model", options.model ?: defaultModel)
            put("stream", true)
            put("temperature", options.temperature.toDouble())
            options.maxTokens?.let { put("max_tokens", it) }
            putJsonArray("messages") {
                options.systemPrompt?.let { sys ->
                    addJsonObject { put("role", "system"); put("content", sys) }
                }
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
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
            timeout { requestTimeoutMillis = options.timeoutMs }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errBody = com.example.personalaibot.sanitizeSensitive(
                    try { response.bodyAsText() } catch (_: Exception) { "Unknown error" }.take(400)
                )
                logError(displayName, "API Error ${response.status.value}: $errBody")
                emit(LlmChunk(text = "⚠️ $displayName Error ${response.status.value}: $errBody"))
                return@execute
            }
            val channel = response.bodyAsChannel()
            var dataCount = 0
            // Accumulate streaming tool_calls deltas by index (OpenAI streaming format)
            val toolCallAccum = mutableMapOf<Int, Triple<String, String, StringBuilder>>()

            while (!channel.isClosedForRead) {
                val line = try { channel.readUTF8Line()?.trim() } catch (e: Exception) {
                    logError(displayName, "Read line error: ${e.message}")
                    null
                } ?: break
                if (line.isBlank() || !line.startsWith("data: ")) continue

                val dataStr = line.removePrefix("data: ").trim()
                if (dataStr == "[DONE]") break
                if (dataStr.isBlank()) continue

                try {
                    val data = json.parseToJsonElement(dataStr).jsonObject
                    val error = data["error"]?.jsonObject
                    if (error != null) {
                        val errMsg = error["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                        logError(displayName, "SSE Error: $errMsg")
                        emit(LlmChunk(text = "⚠️ $displayName Error: $errMsg"))
                        return@execute
                    }
                    val choices = data["choices"]?.jsonArray
                    if (choices == null || choices.isEmpty()) continue
                    val firstChoice = choices[0].jsonObject
                    val delta = firstChoice["delta"]?.jsonObject
                    val content = delta?.get("content")?.jsonPrimitive?.contentOrNull
                    val finishReason = firstChoice["finish_reason"]?.jsonPrimitive?.contentOrNull

                    delta?.get("tool_calls")?.jsonArray?.forEach { tc ->
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
                                toolCallAccum[idx] = Triple(id ?: existing.first, name ?: existing.second, existing.third)
                            } else {
                                toolCallAccum[idx] = Triple(id ?: "", name ?: "", StringBuilder(argsPart ?: ""))
                            }
                        } catch (_: Exception) {}
                    }

                    if (!content.isNullOrEmpty() || finishReason != null) {
                        dataCount++
                        emit(LlmChunk(
                            text = content ?: "",
                            finishReason = if (toolCallAccum.isEmpty()) finishReason else null
                        ))
                    }
                } catch (e: Exception) {
                    logError(displayName, "Parse error: ${e.message}")
                }
            }
            if (toolCallAccum.isNotEmpty()) {
                val completedCalls = toolCallAccum.entries.sortedBy { it.key }.map { (_, v) ->
                    LlmToolCall(id = v.first, name = v.second, arguments = v.third.toString())
                }
                dataCount++
                emit(LlmChunk(text = "", toolCalls = completedCalls, finishReason = "tool_calls"))
            }
            if (dataCount == 0) {
                logError(displayName, "No valid data chunks received from stream")
                emit(LlmChunk(text = "⚠️ ไม่ได้รับข้อมูลที่ถูกต้องจาก $displayName (Empty Data Stream)"))
            }
        }
    }

    override suspend fun generate(
        messages: List<LlmMessage>,
        options: LlmOptions
    ): LlmResult {
        val model = options.model ?: defaultModel
        val body = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("temperature", options.temperature.toDouble())
            options.maxTokens?.let { put("max_tokens", it) }
            putJsonArray("messages") {
                options.systemPrompt?.let { sys ->
                    addJsonObject { put("role", "system"); put("content", sys) }
                }
                messages.forEach { msg -> addJsonObject { putOpenAiMessage(msg) } }
            }
            // tools ด้วย — จำเป็นสำหรับ ModelAutoTester (ทดสอบว่าโมเดลเรียก tool ได้จริงไหม)
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
            contentType(ContentType.Application.Json)
            setBody(body.toString())
            timeout { requestTimeoutMillis = options.timeoutMs }
        }

        val respJson = json.parseToJsonElement(response.body<String>()).jsonObject
        val choice = respJson["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val message = choice?.get("message")?.jsonObject
        val text = message?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
        val toolCalls = message?.get("tool_calls")?.jsonArray?.mapNotNull { tc ->
            try {
                val tcObj = tc.jsonObject
                val funcObj = tcObj["function"]?.jsonObject
                LlmToolCall(
                    id = tcObj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    name = funcObj?.get("name")?.jsonPrimitive?.contentOrNull ?: "",
                    arguments = funcObj?.get("arguments")?.jsonPrimitive?.contentOrNull ?: ""
                )
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

    /** GET /models — OpenAI-compatible list (ทั้ง Groq และ NIM คืน {data:[{id,...}]}) */
    override suspend fun listModels(apiKey: String): List<LlmModelInfo> {
        // ใช้ key ของ provider เองก่อน — param อาจเป็น key ของ provider อื่น
        // (เคสจริง: cross-provider fallback เรียก listModels ผ่าน registry ซึ่งส่ง gemini key มาให้)
        val effectiveKey = this.apiKey.ifBlank { apiKey }
        return try {
            val response = client.get("$baseUrl/models") {
                header("Authorization", "Bearer $effectiveKey")
                timeout { requestTimeoutMillis = 15_000 }
            }
            if (!response.status.isSuccess()) {
                logError(displayName, "listModels HTTP ${response.status.value}")
                return emptyList()
            }
            val respJson = json.parseToJsonElement(response.body<String>()).jsonObject
            val data = respJson["data"]?.jsonArray ?: return emptyList()

            data.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    if (excludeModelPattern != null && excludeModelPattern.containsMatchIn(id.lowercase())) {
                        return@mapNotNull null
                    }
                    LlmModelInfo(
                        id = id,
                        displayName = id,
                        contextLength = obj["context_window"]?.jsonPrimitive?.intOrNull,
                        isFree = allFree,
                        supportsFunctions = true,
                        supportsVision = id.lowercase().let { it.contains("vision") || it.contains("scout") || it.contains("maverick") }
                    )
                } catch (e: Exception) {
                    logError(displayName, "Failed to parse model: ${e.message}")
                    null
                }
            }.sortedBy { it.id }
        } catch (e: Exception) {
            logError(displayName, "Failed to list models: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun isAvailable(): Boolean = apiKey.isNotBlank()
}

/** Groq — https://api.groq.com/openai/v1 (docs: console.groq.com/docs) */
class GroqLlmProvider(client: HttpClient, apiKey: String) : OpenAiCompatLlmProvider(
    client = client,
    apiKey = apiKey,
    providerId = "groq",
    displayName = "Groq",
    baseUrl = "https://api.groq.com/openai/v1",
    defaultModel = "llama-3.1-8b-instant", // free tier RPD 14.4K — เยอะสุดของ Groq
    allFree = true,
    // ตัด model ที่ไม่ใช่ chat หรือใช้ tool ไม่ได้: whisper/prompt-guard (safety), orpheus/canopylabs (TTS ต้อง accept terms),
    // gpt-oss-safeguard (safety classifier — วนเรียก tool ซ้ำจนตอบว่าง เคสจริง 2026-08-08), tts/playai (audio)
    excludeModelPattern = Regex("whisper|prompt-guard|tts|playai|orpheus|canopylabs|safeguard")
)

/** NVIDIA NIM — https://integrate.api.nvidia.com/v1 (free dev credits, key nvapi-…) */
class NvidiaNimLlmProvider(client: HttpClient, apiKey: String) : OpenAiCompatLlmProvider(
    client = client,
    apiKey = apiKey,
    providerId = "nvidia_nim",
    displayName = "NVIDIA NIM",
    baseUrl = "https://integrate.api.nvidia.com/v1",
    defaultModel = "meta/llama-3.1-8b-instruct",
    allFree = true,
    // ตัด model ที่ไม่ใช่ chat: embedding/rerank/retrieval/audio/image-gen
    excludeModelPattern = Regex("embed|rerank|retriev|whisper|audio|parakeet|canary|stable-diffusion|flux|sdxl|cosmos")
)

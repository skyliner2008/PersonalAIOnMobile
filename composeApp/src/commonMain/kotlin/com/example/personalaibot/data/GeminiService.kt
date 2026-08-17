package com.example.personalaibot.data

import com.example.personalaibot.tools.ToolExecutor
import com.example.personalaibot.tools.ToolCall
import com.example.personalaibot.tools.ToolArgParser
import com.example.personalaibot.data.embedding.fitToTargetDimension
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import com.example.personalaibot.tools.ToolRegistry
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// ─── Request / Response Models ──────────────────────────────────────────────

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val system_instruction: GeminiContent? = null,
    val tools: List<JsonObject>? = null,
    val tool_config: JsonObject? = null,
    val generationConfig: GenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val parts: List<Part> = emptyList(),
    val role: String? = null
)

@Serializable
data class Part(
    val text: String? = null,
    val inline_data: InlineData? = null,
    val function_call: FunctionCall? = null,
    val function_response: FunctionResponse? = null
)

@Serializable
data class InlineData(
    val mime_type: String,
    val data: String
)

@Serializable
data class FunctionCall(
    val name: String,
    val args: JsonObject
)

@Serializable
data class FunctionResponse(
    val name: String,
    val response: JsonObject
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String>? = null
)

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>? = null,
    val usageMetadata: UsageMetadata? = null
)

@Serializable
data class UsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null
)

@Serializable
data class Candidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
    val safetyRatings: List<SafetyRating>? = null
)

@Serializable
data class SafetyRating(
    val category: String,
    val probability: String,
    val blocked: Boolean? = null
)

// ─── Function Calling Models (Chat mode) ───────────────────────────────────

private data class DetectedFunctionCall(
    val name: String,
    val args: Map<String, String>
)

@Serializable
data class ModelListResponse(val models: List<GeminiModel>)

@Serializable
data class GeminiModel(
    val name: String,
    val baseModelId: String? = null,
    val version: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
    val supportedGenerationMethods: List<String>? = null
)

@Serializable
data class EmbeddingRequest(
    val content: GeminiContent,
    val taskType: String? = null,
    val title: String? = null
)

@Serializable
data class EmbeddingResponse(val embedding: EmbeddingValue)

@Serializable
data class EmbeddingValue(val values: List<Float>)

data class ConversationTurn(
    val role: String,
    val content: String
)

// ─── Jarvis System Prompt ──────────────────────────────────────────────────
// ย้ายไปรวมศูนย์ที่ com.example.personalaibot.ai.JarvisPersona (2026-07-29)
// — ใช้ getter เพื่อให้ identity ที่ผู้ใช้/AI ปรับแต่งมีผลทันทีทุก request
private val JARVIS_SYSTEM_PROMPT: String
    get() = com.example.personalaibot.ai.JarvisPersona.CHAT_SYSTEM_PROMPT

// ─── GeminiService ──────────────────────────────────────────────────────────

class GeminiService(
    private val client: HttpClient,
    private var apiKey: String,
    private var modelName: String
) {
    private val json = Json { ignoreUnknownKeys = true }
    // Keep tool-call traces out of the visible chat; tool outputs should go back to the model, not the user.
    private val showToolRequestInChat = false
    private val showToolResultInChat = false
    // Tool set constants moved to ToolRegistry for cross-provider reuse

    /**
     * Fallback chain ที่ user ตั้งเองใน Settings (null = ใช้ default ของ ModelConfig)
     * เรียงลำดับสำคัญ — จะไล่ลองจากตัวแรกที่ยังไม่เคยลองในรอบนั้น
     */
    var fallbackModelsOverride: List<String>? = null

    /**
     * Gemini API keys สำหรับ rotation (multi free-tier accounts)
     * เมื่อ key ปัจจุบันติดลิมิต → ลอง key ถัดไปใน list ก่อน แล้วค่อยสลับโมเดล
     */
    var apiKeysOverride: List<String>? = null

    /**
     * เซ็ตเมื่อ Gemini ตายทั้ง chain (ทุก key + ทุกโมเดล) ในรอบล่าสุด
     * Orchestrator ใช้ flag นี้ตัดสินใจสลับไป cross-provider fallback (Groq/NIM/OpenRouter)
     * null = รอบล่าสุดสำเร็จหรือยังไม่เคยล้ม
     */
    var lastFatalError: String? = null

    /**
     * เรียกเมื่อ fallback สลับ key/โมเดลแล้วตอบสำเร็จ — ให้ caller persist ค่าที่ใช้ได้จริงลง settings
     * (กันเคสเปิดแอปใหม่แล้วกลับไปเริ่มที่ key/โมเดลเดิมที่ติดลิมิต)
     */
    var onWorkingConfigChanged: ((model: String, apiKey: String) -> Unit)? = null

    /** สลับไปใช้ key ใหม่ (ใช้ภายใน fallback rotation เท่านั้น — ไม่ persist) */
    private fun rotateApiKey(newKey: String) {
        logDebug("GeminiService", "API key rotation: → ${com.example.personalaibot.maskApiKey(newKey)}")
        apiKey = newKey
    }

    fun updateConfig(newApiKey: String, newModelName: String) {
        if (newModelName.contains("gemini", ignoreCase = true) || !newModelName.contains("/")) {
            val masked = if (newApiKey.length > 8) {
                newApiKey.take(4) + "..." + newApiKey.takeLast(4)
            } else if (newApiKey.isNotBlank()) {
                "****"
            } else "BLANK"
            com.example.personalaibot.logDebug("GeminiService", "Updating Gemini config: model=$newModelName, key=$masked")
        }
        apiKey = newApiKey
        modelName = newModelName
    }

    private fun cleanModelName(): String =
        if (modelName.startsWith("models/")) modelName else "models/$modelName"

    // Moved to TradingIntentUtility for cross-provider consistency
    private fun generateContentUrl(): String {
        val model = cleanModelName()
        return "https://generativelanguage.googleapis.com/v1beta/$model:generateContent?key=$apiKey"
    }

    private fun streamGenerateContentUrl(): String {
        val model = cleanModelName()
        return "https://generativelanguage.googleapis.com/v1beta/$model:streamGenerateContent?alt=sse&key=$apiKey"
    }

    private fun listModelsUrl(): String =
        "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"

    /**
     * Cascade-aware embedding URL builder.
     * `embedding-001` is deprecated and 404s on most projects since 2026-04;
     * `gemini-embedding-001` (3072 dim) and `text-embedding-004` (768 dim)
     * are the supported v1beta models.
     */
    private fun embedContentUrl(model: String = "gemini-embedding-001"): String =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:embedContent?key=$apiKey"

    private fun buildRequestJson(
        userMessage: String,
        history: List<ConversationTurn> = emptyList(),
        intentAddon: String = "",
        coreContext: String = "",
        enableGrounding: Boolean = false,
        includeFunctionTools: Boolean = false,
        extraContents: List<JsonObject> = emptyList(),
        forceTool: Boolean = false,
        fileData: List<InlineData> = emptyList(),
        allowedFunctionNames: Set<String>? = null,
        toolPolicyLabel: String? = null,
        temperature: Float = 0.7f,
        excludeCameraTools: Boolean = false
    ): JsonObject {
        // --- Context Pruning (Token Saving) ---
        val maxCoreContextChars = 15000
        val prunedCoreContext = if (coreContext.length > maxCoreContextChars) {
            coreContext.take(maxCoreContextChars) + "\n...[Memories truncated to save tokens]..."
        } else coreContext

        val systemPrompt = buildString {
            append(JARVIS_SYSTEM_PROMPT)
            if (prunedCoreContext.isNotBlank()) { append("\n\n"); append(prunedCoreContext) }
            if (intentAddon.isNotBlank()) { append("\n\n"); append(intentAddon) }
            if (includeFunctionTools) {
                append("\n\n[REMINDER] กรุณาใช้ Tool สำหรับข้อมูลที่ต้องการความแม่นยำและเป็นปัจจุบัน ห้ามตอบจากความจำเครื่อง (Internal Memory) เด็ดขาด")
            }
            if (!allowedFunctionNames.isNullOrEmpty()) {
                append("\n\n[TRADING TOOL POLICY]")
                toolPolicyLabel?.let { append("\n- Active mode: $it.") }
                append("\n- If this is a trading task, use only these function names: ${allowedFunctionNames.joinToString(", ")}.")
                if (toolPolicyLabel == "MT5-only broker mode") {
                    append("\n- Use broker data ONLY. Do not call TradingView (TV) derived tools (e.g., trading_price, trading_smc_analysis).")
                    append("\n- Use 'trading_mt5_analyze' for all technical and SMC analysis from the broker.")
                } else if (toolPolicyLabel == "Deep Confluence Suite mode") {
                    append("\n- Use Deep Analysis Suite to combine Multi-Agent and SMC analysis.")
                } else {
                    append("\n- Default to TV-only SMC flow for high-level technical analysis.")
                }
            }
        }

        val contentsArray = buildJsonArray {
            // Prune history to last 20 turns
            val prunedHistory = if (history.size > 20) history.takeLast(20) else history
            prunedHistory.forEach { turn ->
                add(buildJsonObject {
                    put("role", turn.role)
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", turn.content) }) })
                })
            }
            add(buildJsonObject {
                put("role", "user")
                put("parts", buildJsonArray { 
                    // Add files first (recommended by Google for better context)
                    fileData.forEach { file ->
                        add(buildJsonObject {
                            put("inline_data", buildJsonObject {
                                put("mime_type", file.mime_type)
                                put("data", file.data)
                            })
                        })
                    }
                    // Add text prompt
                    add(buildJsonObject { put("text", userMessage) }) 
                })
            })
            extraContents.forEach { add(it) }
        }

        val toolsArray: JsonArray? = when {
            includeFunctionTools -> buildJsonArray {
                val allDecls = ToolRegistry.getGeminiTool().functionDeclarations
                var decls = if (allowedFunctionNames.isNullOrEmpty()) {
                    allDecls
                } else {
                    // Include non-trading tools + allowed trading tools
                    allDecls.filter { !com.example.personalaibot.tools.ToolRegistry.isTradingTool(it.name) || it.name in allowedFunctionNames }
                }
                // มีไฟล์แนบจากผู้ใช้ → ซ่อน camera tools (กัน model เผลอเรียก camera_analyze_scene
                // ทั้งที่รูปอยู่ใน inline_data แล้ว — เคสจริงที่ user เจอ)
                if (excludeCameraTools) {
                    decls = decls.filter { !com.example.personalaibot.tools.ToolRegistry.isCameraTool(it.name) }
                }
                if (decls.isNotEmpty()) {
                    add(buildJsonObject {
                        put("function_declarations", buildJsonArray {
                            decls.forEach { decl ->
                                add(buildJsonObject {
                                    put("name", decl.name)
                                    put("description", decl.description)
                                    decl.parameters?.let { params ->
                                        put("parameters", buildJsonObject {
                                            put("type", params.type)
                                            put("properties", buildJsonObject {
                                                params.properties.forEach { (propName, prop) ->
                                                    put(propName, buildJsonObject {
                                                        put("type", prop.type)
                                                        put("description", prop.description)
                                                        prop.enum?.let { enumList ->
                                                            put("enum", buildJsonArray { enumList.forEach { add(it) } })
                                                        }
                                                    })
                                                }
                                            })
                                            if (params.required.isNotEmpty()) {
                                                put("required", buildJsonArray { params.required.forEach { add(it) } })
                                            }
                                        })
                                    }
                                })
                            }
                        })
                    })
                }
            }
            enableGrounding -> buildJsonArray {
                add(buildJsonObject { put("googleSearch", buildJsonObject {}) })
            }
            else -> null
        }

        return buildJsonObject {
            put("contents", contentsArray)
            put("system_instruction", buildJsonObject {
                put("role", "system")
                put("parts", buildJsonArray { add(buildJsonObject { put("text", systemPrompt) }) })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", temperature)
                put("maxOutputTokens", 8192)
            })
            if (forceTool && includeFunctionTools) {
                put("tool_config", buildJsonObject {
                    put("function_calling_config", buildJsonObject {
                        put("mode", "ANY")
                    })
                })
            }
            toolsArray?.let { put("tools", it) }
        }
    }

    /**
     * ดึงค่าจาก functionCall ทั้งหมดจาก JSON Response
     */
    private fun extractFunctionCallsFromParts(parts: JsonArray): List<DetectedFunctionCall> {
        val results = mutableListOf<DetectedFunctionCall>()
        for (part in parts) {
            val partObj = part.jsonObject
            val fc = (partObj["functionCall"] ?: partObj["function_call"])?.jsonObject ?: continue
            val name = fc["name"]?.jsonPrimitive?.content ?: continue
            val argsObj = fc["args"]?.jsonObject ?: JsonObject(emptyMap())

            // ใช้ parser กลางตัวเดียวกับทุก provider path
            val args = ToolArgParser.fromJsonObject(argsObj)
            results.add(DetectedFunctionCall(name, args))
        }
        return results
    }

    /**
     * generateResponseWithTools — Multi-turn Tool Orchestration (Recursive Loop)
     */
    fun generateResponseWithTools(
        prompt: String,
        history: List<ConversationTurn> = emptyList(),
        intentAddon: String = "",
        coreContext: String = "",
        enableGrounding: Boolean = false,
        initialFiles: List<InlineData> = emptyList()
    ): Flow<String> = flow {
        if (apiKey.isBlank()) {
            logError("GeminiService", "Chat failed: API Key is blank")
            emit("⚠️ ไม่สามารถเชื่อมต่อ Gemini ได้: กรุณาตรวจสอบ API Key ใน Settings และกด 'บันทึกการตั้งค่า' ก่อนใช้งาน")
            return@flow
        }

        val toolHistory = mutableListOf<JsonObject>()
        // seed ไฟล์แนบจากผู้ใช้ (รูป/PDF/DOCX) — จะถูกส่งพร้อม request แรกและล้างหลังส่ง
        val pendingFiles = initialFiles.toMutableList()
        val hasAttachments = initialFiles.isNotEmpty()

        // มีไฟล์แนบ → บอก model ชัดๆ ว่าให้ดูไฟล์แนบตรงๆ ห้ามเรียก camera tools
        // + ซ่อน camera tools ออกจาก spec (ผ่าน excludeCameraTools)
        val effectiveIntentAddon = if (hasAttachments) intentAddon + """
            |
            |[USER ATTACHMENTS]: ผู้ใช้แนบไฟล์ ${initialFiles.size} ไฟล์มากับข้อความนี้ (ส่งเป็น inline_data ในข้อความแล้ว)
            |- ให้วิเคราะห์/อ่าน/สรุปจากไฟล์แนบโดยตรงเท่านั้น — มองเห็นเนื้อหาได้เลยโดยไม่ต้องเรียก tool ใดๆ
            |- ห้ามเรียก camera tools (ไฟล์แนบไม่เกี่ยวกับกล้อง)
        """.trimMargin() else intentAddon
        var round = 1
        val maxRounds = 10
        var lastToolCallDetected = false
        lastFatalError = null // reset fatal flag ทุกรอบใหม่ — Orchestrator อ่านหลัง collect จบ

        // จำค่าเริ่มต้น — ถ้า fallback สลับ key/โมเดลแล้วสำเร็จ จะแจ้ง caller persist ลง settings
        val startModel = modelName
        val startKey = apiKey
        var workingConfigReported = false

        // ─── Model fallback chain (free tier) ──────────────────────────────
        // เมื่อโมเดลหลักติด 429/503/timeout → สลับไปใช้โมเดลสำรองตามลำดับใน ModelConfig
        val triedModels = mutableSetOf(cleanModelName().removePrefix("models/"))

        // ─── API key rotation (multi free-tier accounts) ───────────────────
        // ลอง key ถัดไปก่อน (โมเดลเดิม) ก่อนจะสลับโมเดล — key แต่ละอันมีโควต้าแยกกัน
        val triedKeys = mutableSetOf(apiKey)

        fun trySwitchFallbackKey(): String? {
            val chain = apiKeysOverride ?: return null
            val next = chain.firstOrNull { it.isNotBlank() && it !in triedKeys } ?: return null
            triedKeys.add(next)
            rotateApiKey(next)
            return next
        }

        fun trySwitchFallbackModel(): String? {
            val chain = fallbackModelsOverride?.takeIf { it.isNotEmpty() } ?: ModelConfig.GEMINI_FALLBACK_MODELS
            val next = chain.firstOrNull { it !in triedModels }
                ?: return null
            val old = modelName
            modelName = next
            triedModels.add(next)
            logDebug("GeminiService", "Model fallback: $old → $next")
            return next
        }

        try {
            // Policy กลางตัวเดียวกับทุก provider path (JarvisOrchestrator / LiveToolBridge)
            val policy = com.example.personalaibot.ai.TradingToolPolicy.evaluate(prompt, intentAddon)
            val allowedTradingFunctions = policy.allowedTradingToolNames
            val toolPolicyLabel = policy.policyLabel

            while (round <= maxRounds) {
                logDebug("GeminiService", "Tool Loop: Round $round")

                val financialKeywords = listOf(
                    "market", "sector", "crypto", "btc", "aapl", "gold",
                    "xau", "forex", "stock", "trading", "price"
                )
                val forceTool = financialKeywords.any { prompt.contains(it, ignoreCase = true) }

                // ลด temperature ในรอบที่ 2+ เพื่อป้องกัน hallucination
                // รอบแรก (tool selection) ใช้ 0.7, รอบหลัง (สรุปผล) ใช้ 0.4
                val roundTemperature = if (round == 1) 0.7f else 0.4f

                val requestBody = buildRequestJson(
                    userMessage = prompt,
                    history = history,
                    intentAddon = effectiveIntentAddon,
                    coreContext = coreContext,
                    enableGrounding = enableGrounding,
                    includeFunctionTools = true,
                    forceTool = forceTool && round == 1, // บังคับเฉพาะรอบแรก
                    extraContents = toolHistory,
                    fileData = pendingFiles,
                    allowedFunctionNames = allowedTradingFunctions,
                    toolPolicyLabel = toolPolicyLabel,
                    temperature = roundTemperature,
                    excludeCameraTools = hasAttachments
                )

                // Clear pending files after sending them
                pendingFiles.clear()

                var foundFunctionCall = false
                val currentRoundFunctionCalls = mutableListOf<DetectedFunctionCall>()
                val accumulatedModelParts = mutableListOf<JsonElement>()
                // Buffer text ระหว่าง streaming — ถ้ามี function call ในรอบนี้ ข้อความจะถูกทิ้ง
                // เพราะเป็น "ความคิด" ของ model ก่อนได้ข้อมูลจริง (อาจ hallucinate ตัวเลข)
                val textBuffer = StringBuilder()
                var emittedAnyText = false // Round 2+ stream ตรง ไม่ผ่าน buffer — ใช้ flag นี้กัน log "Empty response" หลอก
                var lastFinishReason: String? = null
                var modelFailed = false

                try {
                    client.preparePost(streamGenerateContentUrl()) {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody.toString())
                        timeout { requestTimeoutMillis = 90_000 }
                    }.execute { httpResponse ->
                        if (!httpResponse.status.isSuccess()) {
                            val err = httpResponse.bodyAsText()
                            logError("GeminiService", "API Error ${httpResponse.status.value} (model=$modelName): ${com.example.personalaibot.sanitizeSensitive(err.take(500))}")
                            if (httpResponse.status.value in listOf(429, 500, 503)) {
                                // ลิมิต/เซิร์ฟเวอร์ล้ม — ให้สลับโมเดลสำรองแล้วลองใหม่
                                modelFailed = true
                            } else {
                                emit("⚠️ API Error ${httpResponse.status.value}: ${err.take(300)}")
                            }
                            return@execute
                        }

                    val channel = httpResponse.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        val trimmed = line.trim()
                        if (trimmed.startsWith("data: ")) {
                            val jsonStr = trimmed.removePrefix("data: ").trim()
                            if (jsonStr.isBlank() || jsonStr == "[DONE]") continue

                            try {
                                val root = json.parseToJsonElement(jsonStr).jsonObject
                                val candidates = root["candidates"]?.jsonArray
                                if (candidates == null) {
                                    // ไม่มี candidates — เช่น promptFeedback block หรือ response ว่าง (สาเหตุ empty response ที่เคยไม่มี log)
                                    logDebug("GeminiService", "SSE chunk without candidates (model=$modelName): ${com.example.personalaibot.sanitizeSensitive(jsonStr.take(300))}")
                                }
                                val content = candidates?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                                val parts = content?.get("parts")?.jsonArray

                                parts?.forEach { part ->
                                    val partObj = part.jsonObject
                                    accumulatedModelParts.add(part)

                                    // 1. Buffer text (จะ emit ต่อเมื่อไม่มี function call ในรอบนี้)
                                    // แต่ถ้าอยู่ใน Round 2+ (มี toolHistory) ให้ stream ทันที เพื่อไม่ให้ UI ดูเหมือนค้าง (Dead UI)
                                    val text = partObj["text"]?.jsonPrimitive?.content
                                    if (!text.isNullOrEmpty()) {
                                        if (toolHistory.isNotEmpty()) {
                                            emit(text)
                                            emittedAnyText = true
                                        } else {
                                            textBuffer.append(text)
                                        }
                                    }

                                    // 2. Function call detection
                                    if (partObj.containsKey("functionCall") || partObj.containsKey("function_call")) {
                                        foundFunctionCall = true
                                    }
                                }

                                // Check for safety/finish reasons
                                candidates?.firstOrNull()?.jsonObject?.get("finishReason")?.jsonPrimitive?.contentOrNull?.let { reason ->
                                    lastFinishReason = reason
                                }
                                if (content == null) {
                                    lastFinishReason?.let { reason ->
                                        when {
                                            reason == "STOP" || reason == "NONE" -> {}
                                            // โมเดลส่ง function call เพี้ยน (เจอจริงตอนสรุปผลยาว) — ไม่โชว์ข้อความดิบให้ผู้ใช้
                                            // ถ้ายังไม่ได้ emit text อะไรเลย ให้ถือเป็น failure → เข้า fallback/retry
                                            reason == "MALFORMED_FUNCTION_CALL" -> {
                                                logDebug("GeminiService", "MALFORMED_FUNCTION_CALL (model=$modelName round=$round emittedAnyText=$emittedAnyText) — suppress raw warning")
                                                if (!emittedAnyText && textBuffer.isEmpty()) modelFailed = true
                                            }
                                            else -> emit("\n⚠️ Response interrupted: $reason")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logDebug("GeminiService", "⚠️ SSE Parsing skip: ${e.message}")
                            }
                        }
                    }
                }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Timeout / network error — ให้สลับโมเดลสำรองแล้วลองใหม่ (เคสจริง: flash-lite หมดลิมิตแล้วค้าง 90s)
                    logError("GeminiService", "Stream request failed (model=$modelName, round=$round)", e)
                    modelFailed = true
                }

                // ─── Response ว่างเปล่า (ไม่มี text ไม่มี tool call) = model hiccup ───
                // เคสจริง: gemini-2.5-flash ตอบ finishReason=STOP parts=0 → ผู้ใช้เห็นแชทว่าง
                // ถือเป็น failure ให้เข้าสู่ fallback chain (key ถัดไป → โมเดลถัดไป) เหมือนกรณีลิมิต
                if (!modelFailed && !foundFunctionCall && textBuffer.isEmpty() && !emittedAnyText) {
                    logDebug("GeminiService", "Empty model response in round $round — model=$modelName finishReason=$lastFinishReason parts=${accumulatedModelParts.size} → treat as failure, try fallback")
                    modelFailed = true
                }

                // ─── Fallback อัตโนมัติ เมื่อหลักติดลิมิต/ล่ม/ตอบว่าง ──────
                if (modelFailed) {
                    // 1) ลอง API key ถัดไปก่อน (โมเดลเดิม — key ใหม่ = โควต้าใหม่)
                    val nextKey = trySwitchFallbackKey()
                    if (nextKey != null) {
                        emit("\n🔄 key เดิมติดลิมิต — สลับไปใช้ key ถัดไป (${com.example.personalaibot.maskApiKey(nextKey)}) อัตโนมัติ\n")
                        continue
                    }
                    // 2) key หมดแล้ว → สลับโมเดลสำรอง
                    val next = trySwitchFallbackModel()
                    if (next != null) {
                        emit("\n🔄 โมเดลเดิมมีปัญหา (ลิมิต/ไม่ตอบสนอง) — สลับไปใช้ `$next` อัตโนมัติ\n")
                        continue // retry round เดิมด้วยโมเดลใหม่
                    } else {
                        logError("GeminiService", "All fallback models failed: $triedModels")
                        lastFatalError = "All Gemini keys+models failed: keys=${triedKeys.size} models=$triedModels"
                        emit("⚠️ โมเดล Gemini ทุกตัวที่ลอง (${triedModels.joinToString(", ")}) ใช้ไม่ได้ชั่วคราว — อาจหมดลิมิต free tier หรือเน็ตมีปัญหา ลองใหม่อีกครั้งภายหลัง")
                        break
                    }
                }

                // ─── Fallback สำเร็จ → persist ค่าที่ใช้ได้จริงกลับลง settings ──
                // กันเคสเปิดแอป/แชทใหม่แล้วกลับไปเริ่มที่ key/โมเดลเดิมที่ติดลิมิต (user request)
                if (!workingConfigReported && (modelName != startModel || apiKey != startKey)
                    && (foundFunctionCall || textBuffer.isNotEmpty() || emittedAnyText)) {
                    workingConfigReported = true
                    logDebug("GeminiService", "Fallback config works — persist: model=$modelName key=${com.example.personalaibot.maskApiKey(apiKey)}")
                    onWorkingConfigChanged?.invoke(modelName, apiKey)
                }

                // Log สาเหตุเมื่อ response ว่างเปล่าจริง (ไม่มี tool call และไม่มี text ทั้ง buffer/stream)
                if (!foundFunctionCall && textBuffer.isEmpty() && !emittedAnyText) {
                    logDebug("GeminiService", "Empty model response in round $round — model=$modelName finishReason=$lastFinishReason parts=${accumulatedModelParts.size}")
                }

                // Emit buffered text เฉพาะเมื่อไม่มี function call (= final answer)
                // ถ้ามี function call → text เป็นแค่ "thinking" ที่อาจมีตัวเลขหลอน → ทิ้ง
                if (!foundFunctionCall && textBuffer.isNotEmpty()) {
                    emit(textBuffer.toString())
                } else if (foundFunctionCall && textBuffer.isNotEmpty()) {
                    logDebug("GeminiService", "Discarded pre-tool text (${textBuffer.length} chars) to prevent hallucination")
                }

                if (foundFunctionCall) {
                    lastToolCallDetected = true
                    // Extract all calls from the accumulated parts
                    val fcs = extractFunctionCallsFromParts(JsonArray(accumulatedModelParts))
                    currentRoundFunctionCalls.addAll(fcs)
                    logDebug("GeminiService", "Final tools to execute in Round $round: ${currentRoundFunctionCalls.map { it.name }}")

                    // Execute tools
                    val toolResponseParts = mutableListOf<JsonElement>()
                    for (fc in currentRoundFunctionCalls) {
                        logDebug("GeminiService", "Tool Request: ${fc.name}(${fc.args})")

                        // Strict MT5 mode: ซ่อนผลลัพธ์ TV tools (policy เดียวกับทุก provider path)
                        if (policy.shouldSuppressToolResult(fc.name)) {
                            logDebug("GeminiService", "Strict MT5 Mode: Suppressing TV tool result for ${fc.name}")
                            toolResponseParts.add(buildJsonObject {
                                put("functionResponse", buildJsonObject {
                                    put("name", fc.name)
                                    put("response", buildJsonObject {
                                        put("result", policy.suppressedResultMessage)
                                    })
                                })
                            })
                            continue
                        }

                        val toolResult = try {
                            ToolExecutor.execute(ToolCall(fc.name, fc.args), coreContext)
                        } catch (e: Exception) {
                            com.example.personalaibot.tools.ToolResult(fc.name, "Error: ${e.message}", true)
                        }

                        logDebug("GeminiService", "Tool Result: ${sanitizeToolResultForLog(toolResult.result)}")
                        if (showToolResultInChat) {
                            emit("\n\n${sanitizeToolResultForChat(toolResult.result)}\n")
                        }

                        // Intercept Binary Files for Native Processing
                        if (toolResult.result.startsWith("GEMINI_FILE::")) {
                            try {
                                val mime = toolResult.result.substringAfter("mime=").substringBefore("::data=")
                                val base64 = toolResult.result.substringAfter("::data=")
                                pendingFiles.add(InlineData(mime, base64))

                                toolResponseParts.add(buildJsonObject {
                                    put("functionResponse", buildJsonObject {
                                        put("name", fc.name)
                                        put("response", buildJsonObject {
                                            put("result", "Binary file ($mime) detected and attached for multi-modal analysis. Please analyze its content in the next turn.")
                                        })
                                    })
                                })
                            } catch (e: Exception) {
                                toolResponseParts.add(buildJsonObject {
                                    put("functionResponse", buildJsonObject {
                                        put("name", fc.name)
                                        put("response", buildJsonObject {
                                            put("result", "Error parsing binary file data: ${e.message}")
                                        })
                                    })
                                })
                            }
                        } else {
                            // Truncate large tool results to prevent "Request Entity Too Large"
                            val truncatedResult = truncateToolResult(toolResult.result)
                            toolResponseParts.add(buildJsonObject {
                                put("functionResponse", buildJsonObject {
                                    put("name", fc.name)
                                    put("response", buildJsonObject {
                                        put("result", truncatedResult)
                                    })
                                })
                            })
                        }
                    }

                    // Update History for next round
                    toolHistory.add(buildJsonObject {
                        put("role", "model")
                        put("parts", JsonArray(accumulatedModelParts))
                    })
                    toolHistory.add(buildJsonObject {
                        put("role", "user")
                        put("parts", JsonArray(toolResponseParts))
                    })

                    if (currentRoundFunctionCalls.isEmpty()) {
                        logDebug("GeminiService", "Warning: Function call detected but no valid tools remained after filtering. Breaking loop.")
                        break
                    }
                    round++
                } else {
                    lastToolCallDetected = false
                    // No more function calls, exit loop
                    break
                }
            }

            // Force Final Summary if max rounds reached
            if (round > maxRounds && lastToolCallDetected) {
                logDebug("GeminiService", "Max rounds reached. Forcing final summary.")
                emit("\n\n(ระบบ: วิเคราะห์ข้อมูลครบถ้วนแล้ว กำลังสรุปผล...)\n")

                val finalRequestBody = buildRequestJson(
                    userMessage = prompt,
                    history = history,
                    intentAddon = effectiveIntentAddon,
                    coreContext = coreContext,
                    enableGrounding = enableGrounding,
                    includeFunctionTools = false, // Disable tools to force text response
                    extraContents = toolHistory,
                    fileData = pendingFiles,
                    allowedFunctionNames = null
                )

                client.preparePost(streamGenerateContentUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(finalRequestBody.toString())
                }.execute { httpResponse ->
                    if (httpResponse.status.isSuccess()) {
                        val channel = httpResponse.bodyAsChannel()
                        while (!channel.isClosedForRead) {
                            val line = channel.readUTF8Line() ?: break
                            val trimmed = line.trim()
                            if (trimmed.startsWith("data: ")) {
                                val jsonStr = trimmed.removePrefix("data: ").trim()
                                if (jsonStr.isBlank() || jsonStr == "[DONE]") continue
                                try {
                                    val root = json.parseToJsonElement(jsonStr).jsonObject
                                    val candidates = root["candidates"]?.jsonArray
                                    val content = candidates?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                                    val parts = content?.get("parts")?.jsonArray
                                    parts?.forEach { part ->
                                        val text = part.jsonObject["text"]?.jsonPrimitive?.content
                                        if (!text.isNullOrEmpty()) emit(text)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            logError("GeminiService", "Multi-round orchestration error", e)
            generateResponseFlow(prompt, history, intentAddon, coreContext, enableGrounding).collect { emit(it) }
        }
    }

    private fun truncateToolResult(result: String, maxChars: Int = 10000): String {
        if (result.length <= maxChars) return result
        return result.take(maxChars) + "\n...[Truncated for context limit]..."
    }

    private fun sanitizeToolResultForLog(result: String): String {
        val singleLine = result.replace("\n", "\\n")
        return if (singleLine.length > 1200) {
            singleLine.take(1200) + "...[truncated]"
        } else {
            singleLine
        }
    }

    private fun sanitizeToolResultForChat(result: String): String {
        if (!looksLikeJsonPayload(result)) return result
        return buildString {
            append("Tool completed successfully.")
            append("\n")
            append("Raw structured payload was hidden from chat to keep the conversation readable.")
        }
    }

    private fun looksLikeJsonPayload(result: String): Boolean {
        val trimmed = result.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return true
        val payload = trimmed.substringAfter('\n', "")
        val payloadTrimmed = payload.trim()
        return payloadTrimmed.startsWith("{") || payloadTrimmed.startsWith("[")
    }

    suspend fun listModels(): List<GeminiModel> {
        if (apiKey.isBlank()) return emptyList()
        return try {
            val response = client.get(listModelsUrl())
            val modelList: ModelListResponse = response.body()
            modelList.models.filter {
                it.supportedGenerationMethods?.contains("generateContent") == true
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun generateResponse(
        prompt: String,
        history: List<ConversationTurn> = emptyList(),
        intentAddon: String = "",
        coreContext: String = "",
        enableGrounding: Boolean = false,
        timeoutMs: Long = 20_000,
        attempt: Int = 0
    ): String {
        if (apiKey.isBlank()) return "⚠️ กรุณาตั้งค่า API Key ใน Settings ก่อนใช้งาน"

        // ── Fallback chain (เทียบ streaming path): หมุน key ก่อน → ค่อยสลับโมเดล → persist ค่าที่ใช้ได้จริง ──
        // เดิม non-stream path ไม่มี fallback เลย โมเดลเดียวติด 429 ก็ error ซ้ำทุกครั้ง (เช่น evolution reflection)
        val startModel = modelName
        val startKey = apiKey
        val triedModels = mutableSetOf(cleanModelName().removePrefix("models/"))
        val triedKeys = mutableSetOf(apiKey)

        fun switchKey(): Boolean {
            val chain = apiKeysOverride ?: return false
            val next = chain.firstOrNull { it.isNotBlank() && it !in triedKeys } ?: return false
            triedKeys.add(next); rotateApiKey(next); return true
        }
        fun switchModel(): Boolean {
            val chain = fallbackModelsOverride?.takeIf { it.isNotEmpty() } ?: ModelConfig.GEMINI_FALLBACK_MODELS
            val next = chain.firstOrNull { it !in triedModels } ?: return false
            logDebug("GeminiService", "Model fallback (non-stream): $modelName → $next")
            triedModels.add(next); modelName = next; return true
        }

        var timeout = timeoutMs
        var retriedLonger = false
        while (true) {
            try {
                val res = client.post(generateContentUrl()) {
                    contentType(ContentType.Application.Json)
                    timeout { requestTimeoutMillis = timeout }
                    val prunedHistory = if (history.size > 20) history.takeLast(20) else history
                    val contents = prunedHistory.map { turn ->
                        buildJsonObject {
                            put("role", turn.role)
                            put("parts", buildJsonArray { add(buildJsonObject { put("text", turn.content) }) })
                        }
                    } + buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) })
                    }
                    setBody(buildJsonObject {
                        put("contents", buildJsonArray { contents.forEach { add(it) } })
                        put("systemInstruction", buildJsonObject {
                            put("role", "system")
                            put("parts", buildJsonArray { add(buildJsonObject { put("text", JARVIS_SYSTEM_PROMPT + "\n\n" + coreContext + "\n\n" + intentAddon) }) })
                        })
                    })
                }
                if (res.status.isSuccess()) {
                    val resp: GeminiResponse = res.body()
                    val text = extractAllTextFromResp(resp).ifBlank { "⚠️ No response" }
                    // persist ค่าที่ใช้ได้จริง — กันรอบถัดไปกลับไปเริ่มที่ key/โมเดลที่ติดลิมิต
                    if (modelName != startModel || apiKey != startKey) {
                        logDebug("GeminiService", "Non-stream fallback works — persist: model=$modelName key=${com.example.personalaibot.maskApiKey(apiKey)}")
                        onWorkingConfigChanged?.invoke(modelName, apiKey)
                    }
                    return text
                }
                val code = res.status.value
                val errBody = com.example.personalaibot.sanitizeSensitive(res.bodyAsText())
                logError("GeminiService", "API Error $code (model=$modelName): ${errBody.take(300)}")
                if (code in listOf(429, 500, 503)) {
                    if (switchKey() || switchModel()) continue
                    return "⚠️ Error $code (ลองทุก key+โมเดลใน chain แล้วไม่สำเร็จ)"
                }
                return "⚠️ Error $code"
            } catch (e: Exception) {
                logError("GeminiService", "Generate response failed (model=$modelName, timeout=${timeout}ms)", e)
                // retry 1 ครั้งด้วย timeout นานขึ้น (transient) แล้วค่อยหมุน key/โมเดล
                if (!retriedLonger) { retriedLonger = true; timeout = 45_000; continue }
                if (switchKey() || switchModel()) { retriedLonger = false; timeout = timeoutMs; continue }
                return "⚠️ Error: ${com.example.personalaibot.sanitizeSensitive(e.message ?: "unknown").take(300)}"
            }
        }
    }

    fun generateResponseFlow(
        prompt: String,
        history: List<ConversationTurn> = emptyList(),
        intentAddon: String = "",
        coreContext: String = "",
        enableGrounding: Boolean = false,
        fileData: List<InlineData> = emptyList()
    ): Flow<String> = flow {
        if (apiKey.isBlank()) {
            emit("⚠️ กรุณาตั้งค่า API Key ใน Settings ก่อนใช้งาน")
            return@flow
        }
        try {
            client.preparePost(streamGenerateContentUrl()) {
                contentType(ContentType.Application.Json)
                setBody(buildRequestJson(prompt, history, intentAddon, coreContext, enableGrounding, fileData = fileData))
            }.execute { res ->
                val channel = res.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val jsonStr = line.removePrefix("data: ").trim()
                        if (jsonStr == "[DONE]") continue
                        try {
                            val chunk = json.decodeFromString<GeminiResponse>(jsonStr)
                            val text = extractAllTextFromResp(chunk)
                            if (text.isNotEmpty()) emit(text)
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            logError("GeminiService", "Generate flow failed", e)
            emit("⚠️ Error: ${e.message}")
        }
    }

    /**
     * วิเคราะห์ไฟล์แบบ Native ผ่าน Gemini API
     */
    fun generateResponseWithFile(
        prompt: String,
        mimeType: String,
        base64Data: String
    ): Flow<String> = generateResponseFlow(
        prompt = prompt,
        fileData = listOf(InlineData(mimeType, base64Data))
    )

    private fun extractAllTextFromResp(response: GeminiResponse): String = buildString {
        response.candidates?.forEach { it.content?.parts?.forEach { part -> part.text?.let { append(it) } } }
    }

    /**
     * ดึงค่าเวกเตอร์ (Embeddings) สำหรับข้อความเพื่อใช้ทำ Semantic Search / RAG
     */
    suspend fun embedText(text: String, taskType: String? = "RETRIEVAL_DOCUMENT"): List<Float> {
        if (apiKey.isBlank() || text.isBlank()) return emptyList()
        // Cascade: try gemini-embedding-001 (3072d → truncate) → text-embedding-004 (768d).
        val cascade = listOf("gemini-embedding-001", "text-embedding-004")
        for (model in cascade) {
            val raw = tryEmbedWith(model, text, taskType)
            if (raw.isNotEmpty()) {
                // Matryoshka-truncate (or pad) to 768 + L2-norm so all callers
                // get a uniform vector regardless of the model used.
                return raw.fitToTargetDimension(768)
            }
        }
        return emptyList()
    }

    private suspend fun tryEmbedWith(model: String, text: String, taskType: String?): List<Float> {
        return try {
            val res = client.post(embedContentUrl(model)) {
                contentType(ContentType.Application.Json)
                setBody(EmbeddingRequest(
                    content = GeminiContent(parts = listOf(Part(text = text))),
                    taskType = taskType
                ))
            }
            if (res.status.isSuccess()) {
                val resp: EmbeddingResponse = res.body()
                resp.embedding.values
            } else {
                val err = res.bodyAsText()
                logError("GeminiService", "Embedding model=$model Error ${res.status}: $err")
                emptyList()
            }
        } catch (e: Exception) {
            logError("GeminiService", "Embedding model=$model failed", e)
            emptyList()
        }
    }
}

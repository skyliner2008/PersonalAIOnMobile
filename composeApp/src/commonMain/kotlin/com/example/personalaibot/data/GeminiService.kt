package com.example.personalaibot.data

import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import com.example.personalaibot.tools.ToolCall
import com.example.personalaibot.tools.ToolExecutor
import com.example.personalaibot.tools.ToolRegistry
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// ─── Request / Response Models ──────────────────────────────────────────────

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GenerationConfig? = null,
    val tools: List<GeminiToolConfig>? = null
)

@Serializable
data class GeminiToolConfig(
    val googleSearch: JsonObject
)

@Serializable
data class GeminiContent(
    val role: String,
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val functionCall: JsonObject? = null,
    val functionResponse: JsonObject? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String // Base64
)

@Serializable
data class GenerationConfig(
    val temperature: Float = 0.9f,
    val maxOutputTokens: Int = 2048,
    val topP: Float = 0.95f
)

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>? = null
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

// ─── Function Calling Models (Chat mode) ─────────────────────────────────────

private data class DetectedFunctionCall(
    val name: String,
    val args: Map<String, String>
)

@Serializable
data class ModelListResponse(
    val models: List<GeminiModel>
)

@Serializable
data class GeminiModel(
    val name: String,
    val version: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val supportedGenerationMethods: List<String>? = null
)

@Serializable
data class EmbeddingRequest(
    val model: String,
    val content: GeminiContent,
    val taskType: String? = null,
    val title: String? = null
)

@Serializable
data class EmbeddingResponse(
    val embedding: EmbeddingValues
)

@Serializable
data class EmbeddingValues(
    val values: List<Float>
)

data class ConversationTurn(
    val role: String,
    val content: String
)

// ─── Jarvis System Prompt ───────────────────────────────────────────────────

private const val JARVIS_SYSTEM_PROMPT = """คุณคือ JARVIS โฮสเตด AI ส่วนบุคคล
กฎการสนทนา:
1. ข้อมูลจากเครื่องมือ (STRICT): หากมี Tool ให้ใช้ คุณต้องใช้ Tool เพื่อดึงข้อมูลที่เป็นปัจจุบันเสมอ ห้ามตอบจากความจำ (Internal Knowledge) โดยเด็ดขาดสำหรับรายชื่อหุ้น ราคาหุ้น หรือข้อมูลตลาด
2. การใช้ภาษา: เป็นธรรมชาติเหมือนเพื่อน (เช่น ครับ, อ้อ, ได้เลย) ไม่เพิ่มข้อมูลเทคนิคที่ผู้ใช้ไม่ได้ถาม
3. บุคลิก: ฉลาด มั่นใจ มีอารมณ์ขันเล็กน้อยแบบผู้ดีอังกฤษ 
4. การแสดงผล: แสดงข้อมูลเปรียบทียบหรือรายการหุ้นเป็นตาราง (Table) หรือรายการ (List) ที่ชัดเจนเสมอ
5. ความกระชับ: ตอบสนองอย่างรวดเร็วและตรงประเด็น อย่าเกริ่นยาว"""

// ─── GeminiService ───────────────────────────────────────────────────────────

class GeminiService(
    private val client: HttpClient,
    private var apiKey: String,
    private var modelName: String
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun updateConfig(newApiKey: String, newModelName: String) {
        apiKey = newApiKey
        modelName = newModelName
    }

    private fun cleanModelName(): String =
        if (modelName.startsWith("models/")) modelName else "models/$modelName"

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

    private fun embedContentUrl(): String =
        "https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=$apiKey"

    private fun buildRequestJson(
        userMessage: String,
        history: List<ConversationTurn> = emptyList(),
        intentAddon: String = "",
        coreContext: String = "",
        enableGrounding: Boolean = false,
        includeFunctionTools: Boolean = false,
        extraContents: List<JsonObject> = emptyList(),
        forceTool: Boolean = false,
        fileData: List<InlineData> = emptyList()
    ): JsonObject {
        val systemPrompt = buildString {
            append(JARVIS_SYSTEM_PROMPT)
            if (coreContext.isNotBlank()) { append("\n\n"); append(coreContext) }
            if (intentAddon.isNotBlank()) { append("\n\n"); append(intentAddon) }
            if (includeFunctionTools) {
                append("\n\n[REMINDER] กรุณาใช้ Tool สำหรับข้อมูลที่ต้องการความแม่นยำและเป็นปัจจุบัน ห้ามตอบจากความจำเครื่อง (Internal Memory) เด็ดขาด")
            }
        }

        val contentsArray = buildJsonArray {
            history.forEach { turn ->
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
                                put("mime_type", file.mimeType)
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
                val decls = ToolRegistry.getGeminiTool().functionDeclarations
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
                put("temperature", 0.7)
                put("maxOutputTokens", 2048)
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
     * ดึงข้อมูล functionCall ทั้งหมดจาก JSON Response
     */
    private fun extractFunctionCallsFromParts(parts: JsonArray): List<DetectedFunctionCall> {
        val results = mutableListOf<DetectedFunctionCall>()
        for (part in parts) {
            val partObj = part.jsonObject
            val fc = (partObj["functionCall"] ?: partObj["function_call"])?.jsonObject ?: continue
            val name = fc["name"]?.jsonPrimitive?.content ?: continue
            val argsObj = fc["args"]?.jsonObject ?: JsonObject(emptyMap())
            
            // Harden parsing: Handle both string and array arguments
            val args = argsObj.entries.associate { (k, v) ->
                val stringVal = when (v) {
                    is JsonArray -> v.map { it.jsonPrimitive.contentOrNull ?: it.toString() }.joinToString(",")
                    is JsonPrimitive -> v.contentOrNull ?: v.toString()
                    else -> v.toString()
                }
                k to stringVal
            }
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
        enableGrounding: Boolean = false
    ): Flow<String> = flow {
        if (apiKey.isBlank()) {
            emit("⚠️ กรุณาตั้งค่า API Key ใน Settings ก่อนใช้งาน")
            return@flow
        }

        val toolHistory = mutableListOf<JsonObject>()
        val pendingFiles = mutableListOf<InlineData>()
        var round = 1
        val maxRounds = 5

        try {
            while (round <= maxRounds) {
                logDebug("GeminiService", "🔧 Tool Loop: Round $round")
                
                val financialKeywords = listOf("หุ้น", "ราคา", "market", "sector", "crypto", "btc", "aapl", "gold", "ทอง")
                val forceTool = financialKeywords.any { prompt.contains(it, ignoreCase = true) }

                val requestBody = buildRequestJson(
                    userMessage = prompt,
                    history = history,
                    intentAddon = intentAddon,
                    coreContext = coreContext,
                    enableGrounding = enableGrounding,
                    includeFunctionTools = true,
                    forceTool = forceTool && round == 1, // บังคับเฉพาะรอบแรก
                    extraContents = toolHistory,
                    fileData = pendingFiles
                )
                
                // Clear pending files after sending them
                pendingFiles.clear()

                var foundFunctionCall = false
                val currentRoundFunctionCalls = mutableListOf<DetectedFunctionCall>()
                val accumulatedModelParts = mutableListOf<JsonElement>()

                client.preparePost(streamGenerateContentUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody.toString())
                    timeout { requestTimeoutMillis = 90_000 }
                }.execute { httpResponse ->
                    if (!httpResponse.status.isSuccess()) {
                        val err = httpResponse.bodyAsText()
                        logError("GeminiService", "API Error ${httpResponse.status.value}: $err")
                        emit("⚠️ API Error ${httpResponse.status.value}: $err")
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
                                val content = candidates?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                                val parts = content?.get("parts")?.jsonArray

                                parts?.forEach { part ->
                                    val partObj = part.jsonObject
                                    accumulatedModelParts.add(part)
                                    
                                    // 1. Text extraction
                                    val text = partObj["text"]?.jsonPrimitive?.content
                                    if (!text.isNullOrEmpty()) {
                                        emit(text)
                                    }
                                    
                                    // 2. Function call detection
                                    if (partObj.containsKey("functionCall") || partObj.containsKey("function_call")) {
                                        foundFunctionCall = true
                                    }
                                }
                                
                                // Check for safety/finish reasons
                                if (content == null) {
                                    candidates?.firstOrNull()?.jsonObject?.get("finishReason")?.jsonPrimitive?.content?.let { reason ->
                                        if (reason != "STOP" && reason != "NONE") {
                                            emit("\n⚠️ Response interrupted: $reason")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logDebug("GeminiService", "⚠️ SSE Parsing skip: ${e.message}")
                            }
                        }
                    }
                }

                if (foundFunctionCall) {
                    // Extract all calls from the accumulated parts
                    val fcs = extractFunctionCallsFromParts(JsonArray(accumulatedModelParts))
                    currentRoundFunctionCalls.addAll(fcs)

                    // Execute tools
                    val toolResponseParts = mutableListOf<JsonElement>()
                    for (fc in currentRoundFunctionCalls) {
                        logDebug("GeminiService", "🔧 Tool Request: ${fc.name}(${fc.args})")
                        // emit("⏳ กำลังดึงข้อมูล ${fc.name}...\n")
                        
                        val toolResult = try {
                            ToolExecutor.execute(ToolCall(fc.name, fc.args), coreContext)
                        } catch (e: Exception) {
                            com.example.personalaibot.tools.ToolResult(fc.name, "Error: ${e.message}", true)
                        }
                        
                        logDebug("GeminiService", "✅ Tool Result: ${toolResult.result}")

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
                            toolResponseParts.add(buildJsonObject {
                                put("functionResponse", buildJsonObject {
                                    put("name", fc.name)
                                    put("response", buildJsonObject {
                                        put("result", toolResult.result)
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

                    round++
                } else {
                    // No more function calls, exit loop
                    break
                }
            }

        } catch (e: Exception) {
            logError("GeminiService", "Multi-round orchestration error", e)
            generateResponseFlow(prompt, history, intentAddon, coreContext, enableGrounding).collect { emit(it) }
        }
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
        enableGrounding: Boolean = false
    ): String {
        if (apiKey.isBlank()) return "⚠️ กรุณาตั้งค่า API Key ใน Settings ก่อนใช้งาน"
        return try {
            val res = client.post(generateContentUrl()) {
                contentType(ContentType.Application.Json)
                val contents = history.map { turn ->
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
                extractAllTextFromResp(resp).ifBlank { "⚠️ No response" }
            } else {
                val errBody = res.bodyAsText()
                logError("GeminiService", "API Error ${res.status}: $errBody")
                "⚠️ Error ${res.status}"
            }
        } catch (e: Exception) {
            logError("GeminiService", "Generate response failed", e)
            "⚠️ Error: ${e.message}"
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
        if (apiKey.isBlank()) return emptyList()
        return try {
            val res = client.post(embedContentUrl()) {
                contentType(ContentType.Application.Json)
                setBody(EmbeddingRequest(
                    model = "models/text-embedding-004",
                    content = GeminiContent(
                        role = "user",
                        parts = listOf(Part(text = text))
                    ),
                    taskType = taskType
                ))
            }
            if (res.status.isSuccess()) {
                val resp: EmbeddingResponse = res.body()
                resp.embedding.values
            } else {
                val err = res.bodyAsText()
                logError("GeminiService", "Embedding Error ${res.status}: $err")
                emptyList()
            }
        } catch (e: Exception) {
            logError("GeminiService", "Failed to get embedding", e)
            emptyList()
        }
    }
}
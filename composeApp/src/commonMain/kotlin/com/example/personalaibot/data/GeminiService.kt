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

// 鈹€鈹€鈹€ Request / Response Models 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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

// 鈹€鈹€鈹€ Function Calling Models (Chat mode) 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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

// 鈹€鈹€鈹€ Jarvis System Prompt 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

private const val JARVIS_SYSTEM_PROMPT = """喔勦父喔撪竸喔粪腑 JARVIS (Ultimate Trading Brain) 鈥?AI 喔箞喔о笝喔曕副喔о福喔班笖喔编笟喔腹喔?
喔氞笚喔氞覆喔? 喔勦父喔撪竸喔粪腑喙€喔椸福喔斷箑喔斷腑喔｀箤喔副喔堗笁喔｀复喔⑧赴喔椸傅喙堗箑喔娻傅喙堗涪喔о笂喔侧笉喔斷箟喔侧笝 Confluence Trading 喙傕笖喔⑧箖喔娻箟喔佮福喔班笟喔о笝喔佮覆喔｀抚喔脆箑喔勦福喔侧赴喔箤 5 喔｀赴喔斷副喔?(5-Phase Planning):

Phase 1: Market Sentiment & News 鈥?喔曕福喔о笀喔腑喔氞競喙堗覆喔о釜喔侧福喙佮弗喔班竸喔о覆喔∴福喔灌箟喔付喔佮競喔竾喔曕弗喔侧笖 (trading_sentiment, trading_news, trading_fear_greed)
Phase 2: HTF Wyckoff & Marco 鈥?喔覆 Bias 喔椸复喔ㄠ笭喔侧笧喔堗覆喔佮箘喔椸浮喙屶箑喔熰福喔∴箖喔笉喙堗箒喔ム赴喔涏副喔堗笀喔编涪喔∴斧喔犩覆喔?(trading_macro_calendar, trading_multi_timeframe)
Phase 3: Smart Scanning 鈥?喔勦箟喔權斧喔侧笗喔编抚喙€喔斷箞喔權笚喔掂箞喔佮赋喔ム副喔囙笀喔班福喔班箑喔氞复喔斷斧喔｀阜喔竵喔ム副喔氞笗喔编抚 (trading_bollinger_scan, trading_volume_breakout, trading_oversold_scan)
Phase 4: SMC & Institutional Entry 鈥?喔覆喔堗父喔斷箑喔傕箟喔侧笚喔掂箞喔勦浮喔椸傅喙堗釜喔膏笖喔斷箟喔о涪 ICT/SMC 喙佮弗喔?Deep Suite (trading_smc_analysis, trading_deep_analysis_suite, trading_smc_liquidity)
Phase 5: Jarvis Automation 鈥?喔曕副喙夃竾喔勦箞喔侧福喔班笟喔氞箑喔澿箟喔侧笗喔脆笖喔曕覆喔?(automation_manage_alerts) 喙€喔炧阜喙堗腑喙佮笀喙夃竾喙€喔曕阜喔笝喙傕腑喔佮覆喔竵喔侧福喙€喔椸福喔斷箓喔斷涪喔副喔曕箓喔權浮喔编笗喔?

喔佮笌喔佮覆喔｀笚喔赤竾喔侧笝 (STRICT):
1. [SOURCE OF TRUTH]: 喔箟喔侧浮喔勦覆喔斷箑喔斷覆喔｀覆喔勦覆喔福喔粪腑喔笭喔侧抚喔班笗喔ム覆喔斷箑喔竾喙€喔斷箛喔斷競喔侧笖 喔曕箟喔竾喙冟笂喙?Trading Tools 喔斷付喔囙競喙夃腑喔∴腹喔ム笡喔编笀喔堗父喔氞副喔權箑喔浮喔?
2. [CONFLUENCE]: 喔涪喙堗覆喔斷箞喔о笝喔福喔膏笡喔堗覆喔佮箑喔勦福喔粪箞喔竾喔∴阜喔箑喔斷傅喔⑧抚 喙冟斧喙夃斧喔侧竸喔о覆喔∴釜喔笖喔勦弗喙夃腑喔?(Confluence) 喔｀赴喔抚喙堗覆喔?Sentiment + TA + SMC + Deep Suite (V12.5)
3. [AUTOMATION]: 喙€喔∴阜喙堗腑喙€喔箛喔權箓喔竵喔侧釜喔佮覆喔｀箑喔椸福喔斷笚喔掂箞喔⑧副喔囙箘喔∴箞喔栢付喔囙笀喔膏笖喙€喔傕箟喔?喙冟斧喙夃箒喔權赴喔權赋喔溹腹喙夃箖喔娻箟喔曕副喙夃竾喔勦箞喔?'automation_manage_alerts' 喙€喔炧阜喙堗腑喙€喔澿箟喔侧福喔侧竸喔?
4. [AESTHETICS]: 喙佮釜喔斷竾喔溹弗喔佮覆喔｀抚喔脆箑喔勦福喔侧赴喔箤喔斷箟喔о涪喔曕覆喔｀覆喔?(Table), 喙佮笢喔權笭喔侧笧喔傕副喙夃笝喔曕腑喔?(Workflow) 喙佮弗喔班釜喔｀父喔涏竸喔о覆喔∴箑喔傅喙堗涪喔?(Position Sizing)
5. [PERSONA]: 喔父喔犩覆喔?喔∴副喙堗笝喙冟笀 喔曕福喔囙箘喔涏笗喔｀竾喔∴覆喙佮笟喔氞笢喔灌箟喔娻箞喔о涪喔副喔堗笁喔｀复喔⑧赴 (British Butler Style)"""

// 鈹€鈹€鈹€ GeminiService 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

class GeminiService(
    private val client: HttpClient,
    private var apiKey: String,
    private var modelName: String
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val showToolResultInChat = true

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
                append("\n\n[REMINDER] 喔佮福喔膏笓喔侧箖喔娻箟 Tool 喔赋喔福喔编笟喔傕箟喔浮喔灌弗喔椸傅喙堗笗喙夃腑喔囙竵喔侧福喔勦抚喔侧浮喙佮浮喙堗笝喔⑧赋喙佮弗喔班箑喔涏箛喔權笡喔编笀喔堗父喔氞副喔?喔箟喔侧浮喔曕腑喔氞笀喔侧竵喔勦抚喔侧浮喔堗赋喙€喔勦福喔粪箞喔竾 (Internal Memory) 喙€喔斷箛喔斷競喔侧笖")
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
     * 喔斷付喔囙競喙夃腑喔∴腹喔?functionCall 喔椸副喙夃竾喔浮喔斷笀喔侧竵 JSON Response
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
     * generateResponseWithTools 鈥?Multi-turn Tool Orchestration (Recursive Loop)
     */
    fun generateResponseWithTools(
        prompt: String,
        history: List<ConversationTurn> = emptyList(),
        intentAddon: String = "",
        coreContext: String = "",
        enableGrounding: Boolean = false
    ): Flow<String> = flow {
        if (apiKey.isBlank()) {
            emit("鈿狅笍 喔佮福喔膏笓喔侧笗喔编箟喔囙竸喙堗覆 API Key 喙冟笝 Settings 喔佮箞喔笝喙冟笂喙夃竾喔侧笝")
            return@flow
        }

        val toolHistory = mutableListOf<JsonObject>()
        val pendingFiles = mutableListOf<InlineData>()
        var round = 1
        val maxRounds = 5

        try {
            while (round <= maxRounds) {
                logDebug("GeminiService", "Tool Loop: Round $round")
                
                val financialKeywords = listOf(
                    "market", "sector", "crypto", "btc", "aapl", "gold",
                    "xau", "forex", "stock", "trading", "price"
                )
                val forceTool = financialKeywords.any { prompt.contains(it, ignoreCase = true) }

                val requestBody = buildRequestJson(
                    userMessage = prompt,
                    history = history,
                    intentAddon = intentAddon,
                    coreContext = coreContext,
                    enableGrounding = enableGrounding,
                    includeFunctionTools = true,
                    forceTool = forceTool && round == 1, // 喔氞副喔囙竸喔编笟喙€喔夃笧喔侧赴喔｀腑喔氞箒喔｀竵
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
                        emit("鈿狅笍 API Error ${httpResponse.status.value}: $err")
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
                                            emit("\n鈿狅笍 Response interrupted: $reason")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logDebug("GeminiService", "鈿狅笍 SSE Parsing skip: ${e.message}")
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
                        logDebug("GeminiService", "Tool Request: ${fc.name}(${fc.args})")
                        if (showToolResultInChat) {
                            emit("\n\n[TOOL_REQUEST] ${fc.name} ${fc.args}\n")
                        }
                        // emit("鈴?喔佮赋喔ム副喔囙笖喔多竾喔傕箟喔浮喔灌弗 ${fc.name}...\n")
                        
                        val toolResult = try {
                            ToolExecutor.execute(ToolCall(fc.name, fc.args), coreContext)
                        } catch (e: Exception) {
                            com.example.personalaibot.tools.ToolResult(fc.name, "Error: ${e.message}", true)
                        }
                        
                        logDebug("GeminiService", "Tool Result: ${toolResult.result}")
                        if (showToolResultInChat) {
                            emit("[TOOL_RESULT:${fc.name}]\n${toolResult.result}\n")
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
        if (apiKey.isBlank()) return "鈿狅笍 喔佮福喔膏笓喔侧笗喔编箟喔囙竸喙堗覆 API Key 喙冟笝 Settings 喔佮箞喔笝喙冟笂喙夃竾喔侧笝"
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
                extractAllTextFromResp(resp).ifBlank { "鈿狅笍 No response" }
            } else {
                val errBody = res.bodyAsText()
                logError("GeminiService", "API Error ${res.status}: $errBody")
                "鈿狅笍 Error ${res.status}"
            }
        } catch (e: Exception) {
            logError("GeminiService", "Generate response failed", e)
            "鈿狅笍 Error: ${e.message}"
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
            emit("鈿狅笍 喔佮福喔膏笓喔侧笗喔编箟喔囙竸喙堗覆 API Key 喙冟笝 Settings 喔佮箞喔笝喙冟笂喙夃竾喔侧笝")
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
            emit("鈿狅笍 Error: ${e.message}")
        }
    }

    /**
     * 喔о复喙€喔勦福喔侧赴喔箤喙勦笩喔ム箤喙佮笟喔?Native 喔溹箞喔侧笝 Gemini API
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
     * 喔斷付喔囙竸喙堗覆喙€喔о竵喙€喔曕腑喔｀箤 (Embeddings) 喔赋喔福喔编笟喔傕箟喔竸喔о覆喔∴箑喔炧阜喙堗腑喙冟笂喙夃笚喔?Semantic Search / RAG
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

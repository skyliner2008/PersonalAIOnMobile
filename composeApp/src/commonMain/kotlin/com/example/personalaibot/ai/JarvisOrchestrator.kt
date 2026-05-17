package com.example.personalaibot.ai

import com.example.personalaibot.data.ConversationTurn
import com.example.personalaibot.data.GeminiModel
import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.data.LiveGeminiService
import com.example.personalaibot.data.embedding.EmbeddingProviderRegistry
import com.example.personalaibot.data.providers.*
import com.example.personalaibot.memory.JarvisMemoryManager
import com.example.personalaibot.tools.ToolRegistry
import io.ktor.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class JarvisOrchestrator(
    private val client: HttpClient,
    private val memoryManager: JarvisMemoryManager,
    private var apiKey: String,
    private var modelName: String,
    private var liveModelName: String,
    private val automationManager: com.example.personalaibot.automation.AutomationManager,
    private val fileHandler: (suspend (String, Map<String, String>) -> String)? = null
) : com.example.personalaibot.tools.SideEffectDelegate {
    private val geminiService = GeminiService(client, apiKey, modelName)
    private val liveService   = LiveGeminiService(client, apiKey, liveModelName, memoryManager)
    private val planner       = JarvisPlanner(geminiService)
    val embeddingRegistry = EmbeddingProviderRegistry(client, apiKey)
    
    // ── Multi-Provider Registry ──
    val llmRegistry = LlmProviderRegistry(client).apply {
        register(GeminiLlmProvider(geminiService, client))
    }
    
    // Trading API for Diagnostic
    private val tradingApi    = com.example.personalaibot.tools.trading.TradingApiService(client)
    private val diagnosticManager = com.example.personalaibot.diagnostic.DiagnosticManager(client, tradingApi, automationManager)

    init {
        // เชื่อม HttpClient + GeminiService + AutomationManager เข้ากับ TradingToolExecutor
        com.example.personalaibot.tools.ToolExecutor.init(client, geminiService)
        // เชื่อม File handler (Platform specific)
        fileHandler?.let { com.example.personalaibot.tools.ToolExecutor.initFileHandler(it) }
        
        // เชื่อมต่อ Side-effect Delegate
        com.example.personalaibot.tools.ToolExecutor.setSideEffectDelegate(this)
    }

    private val toolBridge = LiveToolBridge(
        liveService    = liveService,
        geminiService  = geminiService,
        memoryManager  = memoryManager,
        scope          = CoroutineScope(Dispatchers.IO)
    )

    val audioOutputFlow: Flow<ByteArray> = liveService.audioOutputFlow
    val textOutputFlow: Flow<com.example.personalaibot.data.LiveGeminiService.LiveTextUpdate> = liveService.textOutputFlow
    val activeToolName: StateFlow<String?> = toolBridge.activeToolName

    fun getGeminiService() = geminiService

    fun updateConfig(
        newApiKey: String,
        newModelName: String,
        newLiveModelName: String,
        newVoiceName: String = "Aoede"
    ) {
        apiKey        = newApiKey
        modelName     = newModelName
        liveModelName = newLiveModelName
        geminiService.updateConfig(newApiKey, newModelName)
        liveService.updateConfig(newApiKey, newLiveModelName, newVoiceName)
    }

    fun setAiVisionToggle(onToggle: (Boolean) -> Unit) {
        this.visionToggleCallback = onToggle
        toolBridge.onAiVisionToggle = onToggle
    }

    fun setVoiceChangeHandler(onVoiceChange: (String) -> Unit) {
        this.voiceChangeCallback = onVoiceChange
        toolBridge.onVoiceChange = onVoiceChange
    }

    suspend fun listAvailableModels(): List<GeminiModel> =
        geminiService.listModels()

    /**
     * ดึงรายการ models จาก provider ใดก็ได้
     * @param providerId provider ID เช่น "gemini", "openai", "openrouter"
     * @param freeOnly true = เฉพาะ models ฟรี (เหมาะกับ OpenRouter)
     */
    suspend fun listModelsForProvider(
        providerId: String,
        freeOnly: Boolean = false,
        apiKeyOverride: String? = null
    ): List<LlmModelInfo> {
        val keyToUse = apiKeyOverride ?: apiKey
        return llmRegistry.listModels(providerId, keyToUse, freeOnly)
    }

    /** อัพเดท provider keys (เรียกจาก Settings) */
    fun updateProviderKeys(
        openaiKey: String = "",
        claudeKey: String = "",
        openRouterKey: String = "",
        minimaxKey: String = "",
        liteLlmUrl: String = ""
    ) {
        if (openaiKey.isNotBlank()) llmRegistry.registerOpenAI(openaiKey)
        if (claudeKey.isNotBlank()) llmRegistry.registerClaude(claudeKey)
        if (openRouterKey.isNotBlank()) llmRegistry.registerOpenRouter(openRouterKey)
        if (minimaxKey.isNotBlank()) llmRegistry.registerMinimax(minimaxKey)
        if (liteLlmUrl.isNotBlank()) llmRegistry.registerLiteLlm(liteLlmUrl)
    }

    fun chatWithHistory(
        text: String,
        historySnapshot: List<Pair<String, String>> = emptyList(),
        coreContext: String = ""
    ): Flow<String> {
        val providerId = if (modelName.contains("/")) modelName.substringBefore("/") else "gemini"
        com.example.personalaibot.logDebug("Orchestrator", "Chat Request: modelName='$modelName', resolvedProviderId='$providerId', internalApiKeyLength=${apiKey.length}")

        if (providerId == "gemini") {
            com.example.personalaibot.logDebug("Orchestrator", "Routing to GeminiService")
            val history = historySnapshot.map { (role, content) ->
                ConversationTurn(role = role, content = content)
            }
            val intent = IntentClassifier.classify(text)
            return geminiService.generateResponseWithTools(
                prompt = text,
                history = history,
                intentAddon = IntentClassifier.getSystemPromptAddon(intent),
                coreContext = coreContext,
                enableGrounding = false
            )
        } else {
            com.example.personalaibot.logDebug("Orchestrator", "Routing to external provider: $providerId")
            return chatWithExternalProvider(providerId, text, historySnapshot, coreContext)
        }
    }

    private fun chatWithExternalProvider(
        providerId: String,
        text: String,
        historySnapshot: List<Pair<String, String>>,
        coreContext: String
    ): Flow<String> = kotlinx.coroutines.flow.flow {
        com.example.personalaibot.logDebug("Orchestrator", "External Chat: providerId=$providerId, model=$modelName")
        val provider = llmRegistry.getProvider(providerId)
        if (provider == null) {
            val msg = "⚠️ Provider '$providerId' ไม่พร้อมใช้งาน (ยังไม่ถูกลงทะเบียน) กรุณาใส่ API Key ใน Settings แล้วกด 'บันทึกการตั้งค่า' ก่อนใช้งาน"
            com.example.personalaibot.logError("Orchestrator", "Provider '$providerId' not found in registry. Registered: ${llmRegistry.allProviderIds()}")
            emit(msg)
            return@flow
        }
        if (!provider.isAvailable()) {
            val msg = "⚠️ Provider '$providerId' มีข้อมูลไม่ครบถ้วน (เช่น ขาด API Key) กรุณาตรวจสอบใน Settings แล้วกด 'บันทึกการตั้งค่า'"
            com.example.personalaibot.logError("Orchestrator", "Provider '$providerId' is not available (isAvailable=false)")
            emit(msg)
            return@flow
        }

        com.example.personalaibot.logDebug("Orchestrator", "Using provider: ${provider.displayName}")
        val messages = mutableListOf<LlmMessage>()
        val mt5Mode = com.example.personalaibot.ai.TradingIntentUtility.isMt5Prompt(text)

        // --- Context Pruning (Token Saving) ---
        // 1. Truncate coreContext (long-term memories) to prevent context bloat
        val maxCoreContextChars = 15000
        val prunedCoreContext = if (coreContext.length > maxCoreContextChars) {
            coreContext.take(maxCoreContextChars) + "\n...[Memories truncated to save tokens]..."
        } else coreContext

        val systemPrompt = buildString {
            appendLine("คุณคือ JARVIS (Ultimate Trading Brain) — AI ส่วนตัวระดับสูง")
            appendLine("Context: $prunedCoreContext")
            if (mt5Mode) {
                appendLine("\n[IMPORTANT: STRICT MT5 MODE ACTIVE]")
                appendLine("- User explicitly asked for MT5/Broker data.")
                appendLine("- Use 'trading_mt5_analyze' for all technical and SMC analysis (FVG, OB, Structure).")
                appendLine("- DO NOT use TradingView-derived tools (e.g., trading_smc_analysis, trading_price).")
            }
        }

        // 2. Prune history to last 20 turns (approx 10 user/assistant turns)
        val maxHistoryTurns = 20
        val prunedHistory = if (historySnapshot.size > maxHistoryTurns) {
            com.example.personalaibot.logDebug("Orchestrator", "Pruning history from ${historySnapshot.size} to $maxHistoryTurns turns")
            historySnapshot.takeLast(maxHistoryTurns)
        } else historySnapshot

        prunedHistory.forEach { (role, content) ->
            if (content.isNotBlank()) {
                val mappedRole = if (role == "model") "assistant" else role
                // Filter out system role from messages for providers that use top-level system field (like Claude)
                if (mappedRole != "system") {
                    messages.add(LlmMessage(mappedRole, content))
                }
            }
        }
        messages.add(LlmMessage("user", text))

        // Per-round sanitizer — rebuilt each round so newly-added tool/assistant turns are visible.
        // For Claude, the provider now handles tool_use/tool_result content blocks natively,
        // so we only need to (a) drop leading non-user turns, and (b) collapse consecutive
        // *plain* same-role text turns. We must NOT collapse tool turns, or tool_call_id is lost.
        fun sanitizeForProvider(src: List<LlmMessage>): List<LlmMessage> {
            if (providerId != "claude") return src
            val collapsed = mutableListOf<LlmMessage>()
            src.forEach { m ->
                val last = collapsed.lastOrNull()
                val canMerge = last != null
                    && last.role == m.role
                    && last.role != "tool"
                    && last.toolCalls.isNullOrEmpty()
                    && m.toolCalls.isNullOrEmpty()
                if (canMerge) {
                    collapsed.removeAt(collapsed.size - 1)
                    collapsed.add(LlmMessage(m.role, (last!!.content + "\n" + m.content).trim()))
                } else {
                    collapsed.add(m)
                }
            }
            while (collapsed.isNotEmpty() && collapsed.first().role != "user") {
                collapsed.removeAt(0)
            }
            return collapsed
        }

        var round = 1
        val maxRounds = 5
        while (round <= maxRounds) {
            val actualModel = modelName.substringAfter("/")
            com.example.personalaibot.logDebug("Orchestrator", "Round $round starting stream for model: $actualModel")
            val options = LlmOptions(
                model = actualModel,
                systemPrompt = systemPrompt,
                tools = if (provider.supportsFunctionCalling) {
                    val tradingPrompt = com.example.personalaibot.ai.TradingIntentUtility.isTradingPrompt(text)
                    val smcPrompt     = com.example.personalaibot.ai.TradingIntentUtility.isSmcPrompt(text)
                    val mt5Prompt     = com.example.personalaibot.ai.TradingIntentUtility.isMt5Prompt(text)
                    val deepPrompt    = com.example.personalaibot.ai.TradingIntentUtility.isDeepAnalysisPrompt(text)

                    val allowedNames = if (tradingPrompt || mt5Prompt || deepPrompt || smcPrompt) {
                        ToolRegistry.tvOnlyTradingFunctionNames + ToolRegistry.mt5OnlyTradingFunctionNames
                    } else null

                    ToolRegistry.getGeminiTool().functionDeclarations
                        .filter { allowedNames == null || !ToolRegistry.isTradingTool(it.name) || it.name in allowedNames }
                        .map { decl ->
                            LlmToolSpec(
                                name = decl.name,
                                description = decl.description,
                                parametersJson = ToolRegistry.toJsonSchema(decl.parameters)
                            )
                        }
                } else null
            )

            var toolCallsDetected: List<LlmToolCall>? = null
            var fullResponse = ""
            val roundMessages = sanitizeForProvider(messages)

            try {
                // Buffer text ระหว่าง streaming — ถ้ามี tool call ในรอบนี้
                // ข้อความ pre-tool จะถูกทิ้ง เพราะอาจมีตัวเลขหลอน
                val textBuffer = StringBuilder()

                provider.generateStream(roundMessages, options).collect { chunk ->
                    if (chunk.text.isNotEmpty()) {
                        fullResponse += chunk.text
                        textBuffer.append(chunk.text)
                    }
                    if (!chunk.toolCalls.isNullOrEmpty()) {
                        toolCallsDetected = (toolCallsDetected ?: emptyList()) + chunk.toolCalls
                    }
                }

                // Emit text เฉพาะเมื่อไม่มี tool call (= final answer)
                if (toolCallsDetected.isNullOrEmpty() && textBuffer.isNotEmpty()) {
                    emit(textBuffer.toString())
                } else if (!toolCallsDetected.isNullOrEmpty() && textBuffer.isNotEmpty()) {
                    com.example.personalaibot.logDebug("Orchestrator", "Discarded pre-tool text (${textBuffer.length} chars) to prevent hallucination")
                }

                com.example.personalaibot.logDebug("Orchestrator", "Round $round finished. Total chars: ${fullResponse.length}, Tools: ${toolCallsDetected?.size ?: 0}")
            } catch (e: Exception) {
                com.example.personalaibot.logError("Orchestrator", "Streaming error in round $round: ${e.message}", e)
                emit("⚠️ เกิดข้อผิดพลาดในการรับข้อมูลจาก $providerId: ${e.message}")
                break
            }

            if (toolCallsDetected.isNullOrEmpty()) {
                // No tools, we are done
                messages.add(LlmMessage("assistant", fullResponse))
                break
            }

            // Execute Tools
            messages.add(LlmMessage("assistant", fullResponse, toolCalls = toolCallsDetected))
            
            for (toolCall in toolCallsDetected!!) {
                emit("\n🔔 [TOOL]: ${toolCall.name}...")
                val result = try {
                    val argsMap = try {
                        kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(toolCall.arguments)
                    } catch (_: Exception) {
                        emptyMap<String, String>()
                    }
                    com.example.personalaibot.tools.ToolExecutor.execute(
                        com.example.personalaibot.tools.ToolCall(toolCall.name, argsMap),
                        coreContext
                    )
                } catch (e: Exception) {
                    com.example.personalaibot.tools.ToolResult(toolCall.name, "Error: ${e.message}", true)
                }

                val mt5Prompt = com.example.personalaibot.ai.TradingIntentUtility.isMt5Prompt(text)
                val deepPrompt = com.example.personalaibot.ai.TradingIntentUtility.isDeepAnalysisPrompt(text)
                
                if (mt5Prompt && !deepPrompt && toolCall.name in ToolRegistry.tvOnlyTradingFunctionNames) {
                    com.example.personalaibot.logDebug("Orchestrator", "Strict MT5 Mode: Suppressing TV tool result for ${toolCall.name}")
                    messages.add(LlmMessage(
                        role = "tool",
                        content = "This tool result was suppressed because strict MT5 mode is active. Please use trading_mt5_analyze for broker-specific analysis.",
                        toolCallId = toolCall.id
                    ))
                    continue
                }

                // Truncate tool results for external providers to save tokens
                val maxToolResultChars = 8000
                val truncatedResult = if (result.result.length > maxToolResultChars) {
                    result.result.take(maxToolResultChars) + "\n...[Truncated to save tokens]..."
                } else result.result

                messages.add(LlmMessage(
                    role = "tool",
                    content = truncatedResult,
                    toolCallId = toolCall.id
                ))
                // Note: don't emit "Done" — tool progress is suppressed by JarvisVM filter
            }
            
            round++
        }
    }

    suspend fun startLiveVoiceSessionWithMemory(coreContext: String, historyContext: String = "") {
        toolBridge.startCollecting(memoryContextProvider = { coreContext })

        val toolsForSetup = if (supportsNativeTools(liveModelName)) {
            ToolRegistry.getGeminiTool()
        } else {
            null
        }

        liveService.connectAndListen(tools = toolsForSetup, historyContext = historyContext, coreContext = coreContext)
    }

    suspend fun sendLiveAudioChunk(base64Pcm: String) =
        liveService.sendAudioChunk(base64Pcm)

    suspend fun sendLiveCameraFrame(base64Jpeg: String) =
        liveService.sendImageChunk(base64Jpeg)

    suspend fun endLiveVoiceSession() {
        liveService.disconnect()
    }

    suspend fun performSleepCycle(): Boolean {
        // ใช้ EmbeddingProvider สำหรับ embedding + GeminiService สำหรับ LLM consolidation
        val provider = embeddingRegistry.resolve()
        return memoryManager.performSleepCycle(provider, geminiService)
    }

    private fun supportsNativeTools(model: String): Boolean =
        com.example.personalaibot.data.ModelConfig.supportsNativeTools(model)

    // ─── Tool interception helpers ────────────────────────────────────────────

    /**
     * ค้นหาข้อมูลผ่าน Gemini พร้อม prompt ที่ระบุว่าให้ search
     * (Gemini จะใช้ Google Search grounding ถ้าเปิดอยู่)
     */
    suspend fun searchWithGrounding(query: String): String {
        return geminiService.generateResponse(
            prompt = "ค้นหาข้อมูลล่าสุดเกี่ยวกับ: $query\nให้สรุปผลลัพธ์อย่างกระชับ ตอบเป็นภาษาไทย",
            intentAddon = "คุณต้องค้นหาข้อมูลให้ครบถ้วนและตอบอย่างถูกต้อง อ้างอิงแหล่งข้อมูลถ้ามี"
        )
    }

    /** แปลข้อความผ่าน LLM */
    suspend fun translateText(text: String, targetLang: String): String {
        return geminiService.generateResponse(
            prompt = "แปลข้อความต่อไปนี้เป็นภาษา $targetLang:\n\n$text",
            intentAddon = "ให้แปลอย่างธรรมชาติ ไม่ต้องอธิบายเพิ่มเติม ตอบเฉพาะคำแปลเท่านั้น"
        )
    }

    /** สรุปข้อความผ่าน LLM */
    suspend fun summarizeText(text: String, length: String): String {
        val lengthPrompt = when (length.lowercase()) {
            "short"    -> "สรุปให้สั้นมาก 1-2 ประโยค"
            "detailed" -> "สรุปอย่างละเอียดพร้อมจุดสำคัญ"
            else       -> "สรุปให้กระชับ 3-5 ประโยค"
        }
        return geminiService.generateResponse(
            prompt = "$lengthPrompt:\n\n$text",
            intentAddon = "ให้สรุปเนื้อหาอย่างครบถ้วน ไม่ต้องใส่คำนำ ตอบเฉพาะสรุปเท่านั้น"
        )
    }

    // ─── SideEffectDelegate Implementation ──────────────────────────────────────

    override suspend fun onRecallMemory(query: String): String {
        val provider = embeddingRegistry.resolve()
        val facts = memoryManager.searchRelevantFacts(
            embeddingProvider = provider,
            query = query,
            limit = 5
        )
        if (facts.isEmpty()) return "ขออภัย ฉันยังไม่มีข้อมูลเกี่ยวกับเรื่องนี้ในความทรงจำ"
        return "พบข้อมูลที่เกี่ยวข้องดังนี้:\n" + facts.joinToString("\n- ", prefix = "- ")
    }

    override suspend fun onRememberFact(key: String, value: String, importance: String) {
        memoryManager.setCoreMemory(key, value)
        // Also archive as a long-term fact with embeddings
        val provider = embeddingRegistry.resolve()
        memoryManager.archiveFactWithEmbedding(
            embeddingProvider = provider,
            content = "$key: $value",
            sourceRole = "system",
            importance = if (importance.lowercase() == "high") 0.9f else 0.5f
        )
        val imp = when (importance.lowercase()) {
            "high" -> 0.9f; "low" -> 0.3f; else -> 0.6f
        }
        memoryManager.archiveFact(value, "model", imp)
        com.example.personalaibot.logDebug("Orchestrator", "SideEffect: Remembered $key = $value")
    }

    override suspend fun onSetReminder(title: String, detail: String, whenStr: String, timestamp: Long) {
        val reminderContent = "📌 Reminder: $title — $detail (เมื่อ: $whenStr)"
        memoryManager.archiveFact(reminderContent, "system", 0.95f)
        com.example.personalaibot.logDebug("Orchestrator", "SideEffect: Reminder set: $title")
    }

    override suspend fun onDisplayReport(markdown: String, voiceSummary: String) {
        // จะถูกจัดการผ่าน textOutputFlow ของ liveService (ถ้าจำเป็น)
        // หรือส่งผ่าน Event ไปที่ UI
    }

    override suspend fun onVisionToggle(active: Boolean) {
        visionToggleCallback?.invoke(active)
    }

    override suspend fun onVoiceChange(newVoice: String) {
        voiceChangeCallback?.invoke(newVoice)
    }

    override suspend fun onSaveDiagnosticReport(filename: String, content: String) {
        // บันทึกรายงานลงในโฟลเดอร์ Obsidian Wiki สำหรับการตรวจสอบย้อนหลัง
        val path = ".obsidian-wiki/Diagnostic/$filename"
        fileHandler?.invoke("file_write", mapOf("path" to path, "content" to content))
        com.example.personalaibot.logDebug("Orchestrator", "Diagnostic report saved: $path")
    }

    override suspend fun onSaveAgentTool(filename: String, jsonContent: String) {
        // บันทึกเครื่องมือที่ Agent สร้างลงในโฟลเดอร์ custom_agent_tools
        val path = "custom_agent_tools/$filename"
        fileHandler?.invoke("file_write", mapOf("path" to path, "content" to jsonContent))
        com.example.personalaibot.logDebug("Orchestrator", "New Agent Tool saved: $path")
    }

    private var visionToggleCallback: ((Boolean) -> Unit)? = null
    private var voiceChangeCallback: ((String) -> Unit)? = null
}

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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

        // เชื่อม System tools (diagnostics / connectivity / create custom tool)
        com.example.personalaibot.tools.ToolExecutor.initSystemExecutor(
            com.example.personalaibot.tools.system.SystemToolExecutor(diagnosticManager, this)
        )

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
        // cloud embedding ต้องได้ key ใหม่ด้วย — ไม่งั้น semantic memory เงียบทั้งระบบ
        embeddingRegistry.updateGeminiKey(newApiKey)
    }

    fun setAiVisionToggle(onToggle: (Boolean) -> Unit) {
        this.visionToggleCallback = onToggle
        toolBridge.onAiVisionToggle = onToggle
    }

    fun setVoiceChangeHandler(onVoiceChange: (String) -> Unit) {
        this.voiceChangeCallback = onVoiceChange
        toolBridge.onVoiceChange = onVoiceChange
    }

    /** แจ้งเตือนเมื่อ Live generation ถูกขัดจังหวะ (VAD/user) — ViewModel ใช้ flush คิวเสียงค้างเล่น */
    fun setLiveInterruptionHandler(handler: () -> Unit) {
        liveService.onInterrupted = handler
    }

    /** fallback เมื่อ turn ไม่มีเสียงออกเลย (model ตอบ text ล้วน) — ViewModel ใช้ TTS พูดแทน */
    fun setLiveNoAudioFallback(handler: (String) -> Unit) {
        liveService.onTurnWithoutAudio = handler
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
        val policy = TradingToolPolicy.evaluate(text)

        // --- Context Pruning (Token Saving) ---
        // 1. Truncate coreContext (long-term memories) to prevent context bloat
        val maxCoreContextChars = 15000
        val prunedCoreContext = if (coreContext.length > maxCoreContextChars) {
            coreContext.take(maxCoreContextChars) + "\n...[Memories truncated to save tokens]..."
        } else coreContext

        val systemPrompt = buildString {
            // ใช้ persona กลางตัวเดียวกับทุก path — กัน external providers สับสนตัวตน
            appendLine(JarvisPersona.EXTERNAL_SYSTEM_PROMPT)
            if (prunedCoreContext.isNotBlank()) appendLine("Context: $prunedCoreContext")
            append(policy.strictMt5SystemPromptAddon())
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
                    ToolRegistry.getGeminiTool().functionDeclarations
                        .filter { policy.isToolAllowed(it.name) }
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

                // Parse args ด้วย parser กลาง — ถ้า JSON เพี้ยน ให้คืน error เข้า loop
                // แทนการ execute ด้วย args ว่าง (กัน tool ทำงานผิดพลาดเงียบๆ)
                val argsMap = com.example.personalaibot.tools.ToolArgParser.parse(toolCall.arguments)
                if (argsMap == null) {
                    com.example.personalaibot.logError("Orchestrator", "Tool args parse failed for ${toolCall.name}: ${toolCall.arguments.take(200)}")
                    messages.add(LlmMessage(
                        role = "tool",
                        content = "Error: invalid tool arguments JSON. Please re-issue the tool call with a valid JSON object of string key/value pairs.",
                        toolCallId = toolCall.id
                    ))
                    continue
                }

                val result = try {
                    com.example.personalaibot.tools.ToolExecutor.execute(
                        com.example.personalaibot.tools.ToolCall(toolCall.name, argsMap),
                        coreContext
                    )
                } catch (e: Exception) {
                    com.example.personalaibot.tools.ToolResult(toolCall.name, "Error: ${e.message}", true)
                }

                if (policy.shouldSuppressToolResult(toolCall.name)) {
                    com.example.personalaibot.logDebug("Orchestrator", "Strict MT5 Mode: Suppressing TV tool result for ${toolCall.name}")
                    messages.add(LlmMessage(
                        role = "tool",
                        content = policy.suppressedResultMessage,
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
        // GraphRAG retrieval — ดึงความสัมพันธ์จาก knowledge graph ประกอบ (Layer 4)
        val graphContext = try { memoryManager.getGraphContext(query) } catch (_: Exception) { "" }

        if (facts.isEmpty() && graphContext.isBlank()) {
            return "ขออภัย ฉันยังไม่มีข้อมูลเกี่ยวกับเรื่องนี้ในความทรงจำ"
        }
        return buildString {
            if (facts.isNotEmpty()) {
                appendLine("พบข้อมูลที่เกี่ยวข้องดังนี้:")
                appendLine(facts.joinToString("\n- ", prefix = "- "))
            }
            if (graphContext.isNotBlank()) {
                appendLine()
                appendLine("ความสัมพันธ์ที่เกี่ยวข้อง (Knowledge Graph):")
                append(graphContext)
            }
        }.trim()
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
        // Live path: LiveToolBridge emit เข้าแชทผ่าน liveService.emitTextToChat อยู่แล้ว
        // Text chat path: ส่งผ่าน callback ไปที่ ViewModel เพื่อ append ข้อความรายงานในแชท
        displayReportCallback?.invoke(markdown)
        com.example.personalaibot.logDebug("Orchestrator", "SideEffect: Display report (${markdown.length} chars)")
    }

    fun setDisplayReportHandler(onDisplay: (String) -> Unit) {
        this.displayReportCallback = onDisplay
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

    /**
     * โหลด custom tools ทั้งหมดจากโฟลเดอร์ custom_agent_tools (ไฟล์ .json) กลับเข้า ToolRegistry
     * เรียกตอน app start (จาก JarvisViewModel) — ทำให้ tool ที่ Agent เคยสร้างใช้งานได้ข้าม session
     */
    suspend fun loadCustomTools() {
        val handler = fileHandler ?: return
        try {
            val listing = handler.invoke("file_list", mapOf("path" to "custom_agent_tools"))
            if (listing.startsWith("Error") || listing.startsWith("ไม่พบ") || listing.startsWith("โฟลเดอร์ว่าง")) return

            // รูปแบบบรรทัดจาก FileToolExecutor: "[FILE] name.json (123 B)"
            val jsonFiles = listing.lines()
                .map { it.trim() }
                .filter { it.startsWith("[FILE]") && it.contains(".json") }
                .mapNotNull { line ->
                    val raw = line.removePrefix("[FILE]").trim()
                    raw.substringBefore(" (").trim().takeIf { it.endsWith(".json") }
                }

            var loaded = 0
            jsonFiles.forEach { filename ->
                try {
                    val content = handler.invoke("file_read", mapOf("path" to "custom_agent_tools/$filename"))
                    if (content.startsWith("Error") || content.startsWith("ไม่พบ")) return@forEach
                    val obj = kotlinx.serialization.json.Json.parseToJsonElement(content).jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: name
                    val addon = obj["systemPromptAddon"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val keywords = obj["triggerKeywords"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                    com.example.personalaibot.tools.ToolRegistry.registerCustomTool(
                        com.example.personalaibot.tools.FunctionDeclaration(
                            name = name,
                            description = "[CUSTOM] $desc",
                            parameters = null
                        )
                    )
                    com.example.personalaibot.tools.ToolRegistry.registerSkill(
                        com.example.personalaibot.tools.SkillDescriptor(
                            name = name,
                            description = desc,
                            systemPromptAddon = addon,
                            triggerKeywords = keywords,
                            author = obj["author"]?.jsonPrimitive?.contentOrNull ?: "Jarvis Agent"
                        )
                    )
                    loaded++
                } catch (e: Exception) {
                    com.example.personalaibot.logError("Orchestrator", "Failed to load custom tool: $filename", e)
                }
            }
            if (loaded > 0) {
                com.example.personalaibot.logDebug("Orchestrator", "Loaded $loaded custom tool(s) from custom_agent_tools/")
            }
        } catch (e: Exception) {
            com.example.personalaibot.logError("Orchestrator", "loadCustomTools failed", e)
        }
    }

    override suspend fun onManageAlerts(args: Map<String, String>): String {
        val action = args["action"]?.lowercase()?.trim()
            ?: return "❌ ต้องระบุ action (create หรือ delete)"

        return when (action) {
            "create" -> {
                val name = args["name"]?.trim().takeUnless { it.isNullOrBlank() }
                    ?: "Alert ${args["symbol"] ?: ""}".trim()
                val symbol = args["symbol"]?.trim()?.uppercase()
                    ?: return "❌ ต้องระบุ symbol ที่ต้องการเฝ้าดู (เช่น XAUUSD, BTCUSDT)"
                val toolName = args["tool_name"]?.trim().takeUnless { it.isNullOrBlank() } ?: "trading_price"
                val field = args["condition_field"]?.trim().takeUnless { it.isNullOrBlank() } ?: "price"
                val operator = when (args["condition_operator"]?.trim()) {
                    ">"  -> com.example.personalaibot.automation.ConditionOperator.GT
                    "<"  -> com.example.personalaibot.automation.ConditionOperator.LT
                    ">=" -> com.example.personalaibot.automation.ConditionOperator.GTE
                    "<=" -> com.example.personalaibot.automation.ConditionOperator.LTE
                    "==", "=" -> com.example.personalaibot.automation.ConditionOperator.EQ
                    "contains" -> com.example.personalaibot.automation.ConditionOperator.CONTAINS
                    else -> com.example.personalaibot.automation.ConditionOperator.GTE
                }
                val value = args["condition_value"]?.trim()
                    ?: return "❌ ต้องระบุ condition_value (ค่าเปรียบเทียบ เช่น 4800)"
                val interval = args["interval_minutes"]?.toLongOrNull()?.coerceIn(1L, 1440L) ?: 15L

                // จำกัดให้ตั้งได้เฉพาะ tool/field ที่ background engine ดึงค่าได้จริง
                if (!com.example.personalaibot.automation.AlertFieldCatalog.isFieldSupported(toolName, field)) {
                    return "❌ ไม่รองรับ tool_name='$toolName' กับ field='$field' — ตั้งได้เฉพาะ:\n" +
                            com.example.personalaibot.automation.AlertFieldCatalog.describeForAi()
                }
                // ฟิลด์ข้อความ (direction, signal, lsdState ฯลฯ) เปรียบเทียบ >,< ไม่ได้
                if (!com.example.personalaibot.automation.AlertFieldCatalog.isNumericField(toolName, field) &&
                    operator !in listOf(com.example.personalaibot.automation.ConditionOperator.EQ,
                                        com.example.personalaibot.automation.ConditionOperator.CONTAINS)
                ) {
                    return "❌ field '$field' เป็นข้อความ (เช่น BULLISH, STRONG BUY) — ใช้ได้เฉพาะ operator == หรือ contains เท่านั้น"
                }

                automationManager.registerJob(
                    name = name,
                    symbol = symbol,
                    exchange = null,
                    toolName = toolName,
                    condition = com.example.personalaibot.automation.AutomationCondition(field, operator, value),
                    intervalMinutes = interval
                )
                "✅ สร้างการแจ้งเตือน '$name' แล้ว — เฝ้าดู $symbol ($field ${args["condition_operator"] ?: ">="} $value) ทุก $interval นาที"
            }
            "delete" -> {
                val id = args["alert_id"]?.toLongOrNull()
                    ?: return "❌ ต้องระบุ alert_id ของการแจ้งเตือนที่ต้องการลบ"
                automationManager.deleteJob(id)
                "✅ ลบการแจ้งเตือน ID $id แล้ว"
            }
            "update" -> {
                val id = args["alert_id"]?.toLongOrNull()
                    ?: return "❌ ต้องระบุ alert_id ของการแจ้งเตือนที่ต้องการแก้ไข"
                val newValue = args["condition_value"]?.trim()
                    ?: return "❌ ต้องระบุ condition_value ใหม่ (เช่น ราคาเป้าหมายใหม่)"
                val job = automationManager.activeJobs.value.firstOrNull { it.id == id }
                    ?: return "❌ ไม่พบการแจ้งเตือน ID $id (หรือถูกปิดไปแล้ว)"
                val old = try {
                    com.example.personalaibot.automation.automationJson.decodeFromString(
                        com.example.personalaibot.automation.AutomationCondition.serializer(), job.condition_json
                    )
                } catch (_: Exception) {
                    return "❌ เงื่อนไขเดิมของ alert ID $id เสียหาย — ลบแล้วสร้างใหม่แทน"
                }
                val newOp = when (args["condition_operator"]?.trim()) {
                    ">"  -> com.example.personalaibot.automation.ConditionOperator.GT
                    "<"  -> com.example.personalaibot.automation.ConditionOperator.LT
                    ">=" -> com.example.personalaibot.automation.ConditionOperator.GTE
                    "<=" -> com.example.personalaibot.automation.ConditionOperator.LTE
                    "==", "=" -> com.example.personalaibot.automation.ConditionOperator.EQ
                    "contains" -> com.example.personalaibot.automation.ConditionOperator.CONTAINS
                    else -> old.operator
                }
                automationManager.updateCondition(id, old.copy(operator = newOp, value = newValue))
                "✅ แก้ไขการแจ้งเตือน '${job.name}' (ID $id) แล้ว — ${old.field} จาก ${old.value} → $newValue (ระบบรีเซ็ตสถานะและเริ่มเฝ้าดูใหม่)"
            }
            "list" -> {
                val jobs = automationManager.activeJobs.value
                if (jobs.isEmpty()) return "📭 ยังไม่มีการแจ้งเตือนที่ active อยู่"
                buildString {
                    appendLine("📋 **การแจ้งเตือนที่กำลังทำงาน (${jobs.size} รายการ):**")
                    jobs.forEach { j ->
                        val state = if (j.is_triggered == 1L) "🔔 เข้าเงื่อนไขแล้ว" else "⏳ เฝ้าดูอยู่"
                        appendLine("- ID ${j.id}: ${j.name} — ${j.symbol} (${j.tool_name}) ทุก ${j.interval_minutes} นาที | $state | ค่าล่าสุด: ${j.last_value ?: "-"}")
                    }
                }.trim()
            }
            "rename" -> {
                val id = args["alert_id"]?.toLongOrNull()
                    ?: return "❌ ต้องระบุ alert_id ของการแจ้งเตือนที่ต้องการเปลี่ยนชื่อ (ดู ID จาก action=list)"
                val newName = args["name"]?.trim().takeUnless { it.isNullOrBlank() }
                    ?: return "❌ ต้องระบุ name ใหม่ — ชื่อนี้จะใช้ในหัว notification และเป็นชื่อที่ฉันใช้พูดตอนแจ้งเตือน"
                val job = automationManager.activeJobs.value.firstOrNull { it.id == id }
                    ?: return "❌ ไม่พบการแจ้งเตือน ID $id (หรือถูกปิดไปแล้ว)"
                automationManager.renameJob(id, newName)
                "✅ เปลี่ยนชื่อการแจ้งเตือน ID $id จาก '${job.name}' → '$newName' แล้ว"
            }
            else -> "❌ ไม่รู้จัก action '$action' — ใช้ create, update, delete, rename หรือ list"
        }
    }

    override suspend fun onManageSchedule(args: Map<String, String>): String {
        val action = args["action"]?.lowercase()?.trim()
            ?: return "❌ ต้องระบุ action (create, delete หรือ list)"

        return when (action) {
            "create" -> {
                val name = args["name"]?.trim().takeUnless { it.isNullOrBlank() } ?: "Scheduled Task"
                val prompt = args["prompt"]?.trim().takeUnless { it.isNullOrBlank() }
                    ?: return "❌ ต้องระบุ prompt (คำสั่งที่จะให้ AI ทำเมื่อถึงเวลา)"
                val type = args["schedule_type"]?.lowercase()?.trim() ?: "one_time"

                when (type) {
                    "daily" -> {
                        val hhmm = args["time_hhmm"]?.trim()
                            ?: return "❌ daily ต้องระบุ time_hhmm (เช่น 08:00, 20:30)"
                        if (!Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(hhmm)) {
                            return "❌ time_hhmm รูปแบบไม่ถูกต้อง: '$hhmm' (ต้องเป็น HH:mm เช่น 08:00)"
                        }
                        val normalized = hhmm.padStart(5, '0')
                        automationManager.registerScheduledTask(name, prompt, "daily", 0L, normalized)
                        "✅ สร้างงานประจำวัน '$name' แล้ว — ระบบจะปลุกฉันมาทำ \"$prompt\" ทุกวันเวลา $normalized น."
                    }
                    else -> {
                        val now = kotlinx.datetime.Clock.System.now()
                        val runAtMs: Long? = args["in_minutes"]?.toLongOrNull()?.let { mins ->
                            if (mins <= 0) null
                            else now.plus(mins, kotlinx.datetime.DateTimeUnit.MINUTE).toEpochMilliseconds()
                        } ?: args["run_at"]?.trim()?.let { raw ->
                            try {
                                val iso = raw.replace(" ", "T").take(16)
                                kotlinx.datetime.LocalDateTime.parse(iso)
                                    .toInstant(kotlinx.datetime.TimeZone.currentSystemDefault())
                                    .toEpochMilliseconds()
                            } catch (_: Exception) { null }
                        }
                        if (runAtMs == null) {
                            return "❌ one_time ต้องระบุ run_at (เช่น 2026-07-30 20:00) หรือ in_minutes (เช่น 30)"
                        }
                        val whenText = kotlinx.datetime.Instant.fromEpochMilliseconds(runAtMs)
                            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                        automationManager.registerScheduledTask(name, prompt, "one_time", runAtMs, null)
                        "✅ สร้างงานครั้งเดียว '$name' แล้ว — ระบบจะปลุกฉันมาทำ \"$prompt\" วันที่ ${whenText.date} เวลา ${whenText.hour.toString().padStart(2, '0')}:${whenText.minute.toString().padStart(2, '0')} น."
                    }
                }
            }
            "delete" -> {
                val id = args["task_id"]?.toLongOrNull()
                    ?: return "❌ ต้องระบุ task_id ของงานที่ต้องการลบ"
                automationManager.deleteScheduledTask(id)
                "✅ ลบงานตามเวลา ID $id แล้ว"
            }
            "list" -> {
                val tasks = automationManager.scheduledTasks.value
                if (tasks.isEmpty()) return "📭 ยังไม่มีงานตามเวลาที่ active อยู่"
                buildString {
                    appendLine("📋 **งานตามเวลาที่กำลังทำงาน (${tasks.size} รายการ):**")
                    tasks.forEach { t ->
                        val whenDesc = if (t.schedule_type == "daily") "ทุกวัน ${t.time_hhmm} น."
                        else {
                            val lt = kotlinx.datetime.Instant.fromEpochMilliseconds(t.run_at)
                                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                            "ครั้งเดียว ${lt.date} ${lt.hour.toString().padStart(2, '0')}:${lt.minute.toString().padStart(2, '0')} น."
                        }
                        appendLine("- ID ${t.id}: ${t.name} — $whenDesc | คำสั่ง: \"${t.prompt}\"")
                    }
                }.trim()
            }
            else -> "❌ ไม่รู้จัก action '$action' — ใช้ create, delete หรือ list"
        }
    }

    override suspend fun onUpdateIdentity(target: String, field: String, value: String): String {
        val updated = JarvisPersona.updateIdentityField(target, field, value)
            ?: return "❌ ไม่รู้จัก target/field: '$target.$field' (agent: name/creature/vibe/gender | user: name/call_name/notes)"

        // persist ลง Core Memory — โหลดกลับทุกครั้งที่เปิดแอป
        JarvisPersona.toCoreMemoryMap(updated).forEach { (k, v) ->
            memoryManager.setCoreMemory(k, v)
        }
        com.example.personalaibot.logDebug("Orchestrator", "Identity updated: $target.$field = $value")

        val label = when (target.lowercase()) {
            "agent" -> "ตัวตนของฉัน (${JarvisPersona.identity.agentName})"
            else -> "ข้อมูลของ${JarvisPersona.identity.userCallName}"
        }
        return "✅ อัปเดต $label เรียบร้อย: $field = \"$value\" — มีผลทันทีทุก provider (Chat/Live/External)"
    }

    private var visionToggleCallback: ((Boolean) -> Unit)? = null
    private var voiceChangeCallback: ((String) -> Unit)? = null
    private var displayReportCallback: ((String) -> Unit)? = null
}

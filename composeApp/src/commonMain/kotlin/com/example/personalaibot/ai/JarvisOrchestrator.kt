package com.example.personalaibot.ai

import com.example.personalaibot.data.ConversationTurn
import com.example.personalaibot.data.GeminiModel
import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.data.GeminiVoiceProfiles
import com.example.personalaibot.data.VoiceGender
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface ChatStreamEvent {
    data class Text(val content: String) : ChatStreamEvent
    data class ToolStarted(val toolName: String) : ChatStreamEvent
    data class ToolResult(
        val toolName: String,
        val result: String,
        val isError: Boolean = false
    ) : ChatStreamEvent
    data class System(val message: String) : ChatStreamEvent
}

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

    /** ผล auto-test: providerId → set ของ model id ที่เทสผ่านจริง (ว่าง = ยังไม่เคยเทส → ใช้ heuristic) */
    private var testedChatOkModels: Map<String, Set<String>> = emptyMap()
    private var testedToolsOkModels: Map<String, Set<String>> = emptyMap()

    fun updateModelCaps(caps: Map<String, com.example.personalaibot.data.providers.ModelAutoTester.ModelCapability>) {
        fun group(predicate: (com.example.personalaibot.data.providers.ModelAutoTester.ModelCapability) -> Boolean) =
            caps.filterValues(predicate).keys
                .map { it.substringBefore("/") to it.substringAfter("/") }
                .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
        testedChatOkModels = group { it.chatOk }
        testedToolsOkModels = group { it.chatOk && it.toolsOk }
        com.example.personalaibot.logDebug("Orchestrator", "Model caps loaded: tools-ok = ${testedToolsOkModels.mapValues { it.value.size }}")
    }

    /** ตั้ง fallback chain ของ Gemini จาก Settings (empty = กลับไปใช้ default ModelConfig) */
    fun updateGeminiFallbackChain(models: List<String>) {
        geminiService.fallbackModelsOverride = models.ifEmpty { null }
        com.example.personalaibot.logDebug("Orchestrator", "Gemini fallback chain = ${models.ifEmpty { com.example.personalaibot.data.ModelConfig.GEMINI_FALLBACK_MODELS }}")
    }    /** ตั้ง list Gemini API keys สำหรับ rotation เมื่อ key ปัจจุบันติดลิมิต (multi free-tier accounts) */
    fun updateGeminiApiKeys(keys: List<String>) {
        val normalized = keys.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        geminiService.apiKeysOverride = normalized
        liveService.updateLiveApiKeys(normalized)
        com.example.personalaibot.logDebug("Orchestrator", "Gemini API keys for rotation: ${normalized.size} key(s) — Live pool synchronized")
    }

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
        // Live has its own persistent-WebSocket rotation pool; keep it synchronized with
        // the same multi-key settings used by GeminiService.
        geminiService.apiKeysOverride?.let { liveService.updateLiveApiKeys(it) }
        liveService.updateLiveModelChain(
            listOf(
                newLiveModelName,
                "gemini-2.5-flash-native-audio-preview-12-2025",
                "gemini-2.0-flash-exp"
            )
        )
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
        liteLlmUrl: String = "",
        groqKey: String = "",
        nvidiaNimKey: String = ""
    ) {
        if (openaiKey.isNotBlank()) llmRegistry.registerOpenAI(openaiKey)
        if (claudeKey.isNotBlank()) llmRegistry.registerClaude(claudeKey)
        if (openRouterKey.isNotBlank()) llmRegistry.registerOpenRouter(openRouterKey)
        if (minimaxKey.isNotBlank()) llmRegistry.registerMinimax(minimaxKey)
        if (liteLlmUrl.isNotBlank()) llmRegistry.registerLiteLlm(liteLlmUrl)
        if (groqKey.isNotBlank()) llmRegistry.registerGroq(groqKey)
        if (nvidiaNimKey.isNotBlank()) llmRegistry.registerNvidiaNim(nvidiaNimKey)
    }

    fun chatWithHistory(
        text: String,
        historySnapshot: List<Pair<String, String>> = emptyList(),
        coreContext: String = "",
        attachments: List<com.example.personalaibot.data.InlineData> = emptyList()
    ): Flow<ChatStreamEvent> {
        val providerId = if (modelName.contains("/")) modelName.substringBefore("/") else "gemini"
        com.example.personalaibot.logDebug("Orchestrator", "Chat Request: modelName='$modelName', resolvedProviderId='$providerId', internalApiKeyLength=${apiKey.length}, attachments=${attachments.size}")

        if (providerId == "gemini") {
            com.example.personalaibot.logDebug("Orchestrator", "Routing to GeminiService")
            val history = historySnapshot.map { (role, content) ->
                ConversationTurn(role = role, content = content)
            }
            val intent = IntentClassifier.classify(text)
            return kotlinx.coroutines.flow.flow {
                geminiService.generateResponseWithTools(
                    prompt = text,
                    history = history,
                    intentAddon = IntentClassifier.getSystemPromptAddon(intent),
                    coreContext = coreContext,
                    enableGrounding = false,
                    initialFiles = attachments
                ).collect { emit(it) }

                // ─── Cross-provider fallback (ชั้นสุดท้าย) ──────────────────
                // Gemini ตายทั้ง chain (ทุก key + ทุกโมเดล) → ไล่ provider ฟรีอื่น
                // หมายเหตุ: Live mode ไม่ได้ใช้ path นี้ — LiveToolBridge execute tools เองตรงๆ
                if (geminiService.lastFatalError != null) {
                    com.example.personalaibot.logError("Orchestrator", "Gemini fatal — starting cross-provider fallback: ${geminiService.lastFatalError}")
                    emit(ChatStreamEvent.System("🌐 Gemini ใช้ไม่ได้ทั้งหมด — กำลังสลับไป provider สำรอง…"))
                    chatWithCrossProviderFallback(text, historySnapshot, coreContext).collect { emit(ChatStreamEvent.Text(it)) }
                }
            }
        } else {
            if (attachments.isNotEmpty()) {
                com.example.personalaibot.logDebug("Orchestrator", "⚠️ attachments ignored — external provider path does not support inline files yet")
            }
            com.example.personalaibot.logDebug("Orchestrator", "Routing to external provider: $providerId")
            return chatWithExternalProvider(providerId, text, historySnapshot, coreContext)
                .map { ChatStreamEvent.Text(it) }
        }
    }

    /**
     * Cross-provider fallback (ชั้นสุดท้ายเมื่อ Gemini ตายทั้ง chain)
     * ไล่ provider ฟรีตามลำดับ: Groq → NVIDIA NIM → OpenRouter → MiniMax
     * เงื่อนไขเลือก model: ต้องรองรับ function calling (tools ของเราใช้ได้ต่อ) และฟรี
     * — buffer ผลลัพธ์ทั้งก้อนก่อน emit เพื่อตรวจว่าสำเร็จจริง ไม่งั้นไล่ provider ถัดไป
     */
    private fun chatWithCrossProviderFallback(
        text: String,
        historySnapshot: List<Pair<String, String>>,
        coreContext: String
    ): Flow<String> = kotlinx.coroutines.flow.flow {
        // ไล่ provider ฟรีตามลำดับ (2026-08-08: ตัด NIM ออก — โมเดลส่วนใหญ่ช้า/404)
        val order = listOf("groq", "openrouter", "minimax")
        var answered = false

        for (pid in order) {
            val provider = llmRegistry.getProvider(pid) ?: continue
            if (!provider.isAvailable()) continue

            // candidate models: รองรับ tools + ฟรี — ลองทีละตัวสูงสุด 3 ตัว
            // เคสจริง: NIM /models คืนโมเดลที่ account ไม่มีสิทธิ์เรียก (404) ต้องไล่ตัวถัดไป
            val candidates = try {
                val models = llmRegistry.listModels(pid, apiKey, freeOnly = true)
                val toolModels = models.filter { it.supportsFunctions }
                val allIds = (toolModels.ifEmpty { models }).map { it.id }.distinct()
                val testedChat = testedChatOkModels[pid]
                if (testedChat != null) {
                    // เคย auto-test แล้ว → ใช้เฉพาะตัวที่เทสผ่านจริง: tools-ok ก่อน ตามด้วย chat-only
                    val toolsOk = testedToolsOkModels[pid].orEmpty()
                    val tested = allIds.filter { it in testedChat }
                    (tested.filter { it in toolsOk } + tested.filter { it !in toolsOk }).take(3)
                } else {
                    // ยังไม่เคยเทส → heuristic: โมเดลที่พิสูจน์แล้ว (Groq 2026-08-08) + free ตัวดังของ OpenRouter
                    val preferred = listOf(
                        "llama-3.3-70b-versatile", "qwen3.6", "llama-3.3-70b", "qwen3",
                        "deepseek", "gemini", "llama", "qwen", "mistral"
                    )
                    allIds.sortedBy { id -> preferred.indexOfFirst { id.contains(it, ignoreCase = true) }.let { if (it < 0) Int.MAX_VALUE else it } }
                        .take(3)
                }
            } catch (e: Exception) {
                com.example.personalaibot.logError("Orchestrator", "Fallback: listModels $pid failed: ${e.message}")
                emptyList()
            }
            if (candidates.isEmpty()) {
                com.example.personalaibot.logDebug("Orchestrator", "Fallback: no usable model for $pid — skip")
                continue
            }

            for (modelId in candidates) {
                com.example.personalaibot.logDebug("Orchestrator", "Fallback: trying $pid/$modelId")
                val buf = StringBuilder()
                var failed = false
                try {
                    chatWithExternalProvider(pid, text, historySnapshot, coreContext, modelOverride = modelId)
                        .collect { chunk ->
                            if (chunk.contains("⚠️")) failed = true
                            buf.append(chunk)
                        }
                } catch (e: Exception) {
                    failed = true
                    com.example.personalaibot.logError("Orchestrator", "Fallback: $pid/$modelId threw: ${e.message}")
                }

                if (!failed && buf.isNotBlank()) {
                    emit("🔄 Gemini ใช้ไม่ได้ — ใช้ `$pid/$modelId` ตอบแทนชั่วคราว\n")
                    emit(buf.toString())
                    answered = true
                    break
                }
                com.example.personalaibot.logError("Orchestrator", "Fallback: $pid/$modelId failed — trying next model")
            }
            if (answered) break
        }

        if (!answered) {
            emit("⚠️ Provider สำรองทั้งหมดใช้ไม่ได้ในตอนนี้ — กรุณาตรวจสอบ API keys (Gemini/Groq/NIM/OpenRouter) ใน Settings หรือลองใหม่ภายหลัง")
        }
    }

    private fun chatWithExternalProvider(
        providerId: String,
        text: String,
        historySnapshot: List<Pair<String, String>>,
        coreContext: String,
        modelOverride: String? = null
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
        // ลดจาก 15000 → 6000: เคสจริง Groq free tier TPM 8K ได้ 413 เพราะ payload ใหญ่เกิน
        val maxCoreContextChars = 6000
        val prunedCoreContext = if (coreContext.length > maxCoreContextChars) {
            coreContext.take(maxCoreContextChars) + "\n...[Memories truncated to save tokens]..."
        } else coreContext

        val systemPrompt = buildString {
            // ใช้ persona กลางตัวเดียวกับทุก path — กัน external providers สับสนตัวตน
            appendLine(JarvisPersona.EXTERNAL_SYSTEM_PROMPT)
            if (prunedCoreContext.isNotBlank()) appendLine("Context: $prunedCoreContext")
            append(policy.signalAlertSystemPromptAddon())
            append(policy.strictMt5SystemPromptAddon())
            if (policy.isTradingContext && !policy.mt5Mode) {
                // กัน model ตอบว่า "ไม่มีข้อมูล/ต้องต่อ MT5" ทั้งที่ TV tools ใช้ได้โดยไม่ต้อง MT5
                // (เคสจริง: qwen/llama บน Groq ปฏิเสธดึงราคาเพราะคิดว่าต้องมี MT5 เท่านั้น)
                appendLine()
                appendLine("[TRADING TOOLS พร้อมใช้ — ไม่ต้องมี MT5]")
                appendLine("- คุณมี tools: trading_price, trading_technical_analysis, trading_indicators, trading_smc_analysis, trading_market_snapshot, trading_news — ดึงข้อมูลสดจาก TradingView ได้ทันที")
                appendLine("- เมื่อผู้ใช้ถามราคา/กราฟ/วิเคราะห์ ให้เรียก trading_price หรือ trading_technical_analysis ก่อนเสมอ")
                appendLine("- ห้ามตอบว่า 'ไม่มีข้อมูล' หรือ 'ต้องเชื่อมต่อ MT5' ถ้ายังไม่ได้เรียก tool เด็ดขาด")
            }
        }

        // 2. Prune history to last 10 turns (เดิม 20 — ลด payload สำหรับ free-tier TPM ต่ำ)
        val maxHistoryTurns = 10
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
        // Degraded retry: ถ้ารอบแรกเจอ error (413 payload ใหญ่ / 404 model / timeout)
        // → ลองใหม่ครั้งเดียวแบบประหยัด token: ไม่ส่ง tools + history เหลือ 3 turns
        // เคสจริง: Groq free tier TPM 8K ได้ 413 เพราะ tools schema ใหญ่
        var degraded = false
        // 429 transient backoff: Groq แจ้ง "Please try again in X s" — ควรรอแล้วลองใหม่ ไม่ใช่ degrade ทิ้ง
        var rateLimitRetries = 0
        // กัน model วนเรียก tool เดิมซ้ำ (เคสจริง: gpt-oss เรียก search_web ซ้ำ 5 รอบจนตอบว่าง)
        var lastToolSignature: String? = null
        while (round <= maxRounds) {
            val actualModel = (modelOverride ?: modelName).substringAfter("/")
            com.example.personalaibot.logDebug("Orchestrator", "Round $round starting stream for model: $actualModel (degraded=$degraded)")
            val options = LlmOptions(
                model = actualModel,
                systemPrompt = systemPrompt,
                tools = if (provider.supportsFunctionCalling && !degraded) {
                    // กัน model หลุดไปเรียก search_web ตอนถามราคา/กราฟ (เคสจริง: llama-3.3-70b/qwen
                    // บน Groq ถามราคา XAUUSD แล้วเรียก search_web ซ้ำแทน trading tools จนตอบมั่ว)
                    val decls = ToolRegistry.getGeminiTool().functionDeclarations
                        .filter { policy.isToolAllowed(it.name) }
                        .filter { decl -> !(policy.isTradingContext && decl.name == "search_web") }
                        // MT5 tools ส่งเฉพาะตอน strict MT5 mode เท่านั้น — ปกติ MT5 ไม่ได้ pair
                        // ส่งไปก็เปลือง schema tokens + model อาจหลุดไปเรียก (เคสจริง: trading_mt5_analyze หลุด)
                        .filter { decl -> policy.mt5Mode || decl.name !in ToolRegistry.mt5OnlyTradingFunctionNames }
                    // สำคัญ: trading context ต้องให้ trading tools ขึ้นก่อน — registry เรียง builtin
                    // (calculate/recall_memory/...) มาก่อน take(12) เลยตัด trading tools ทิ้งหมด
                    // model ไม่เห็น trading_price จนหลุดไปเรียก tool มั่ว (เคสจริง Groq 2026-08-08)
                    (if (policy.isTradingContext) {
                        decls.sortedByDescending { if (ToolRegistry.isTradingTool(it.name)) 1 else 0 }
                    } else decls)
                        .take(12) // จำกัดจำนวน tools — schema ใหญ่เกินจะชน TPM ของ free tier
                        .map { decl ->
                            LlmToolSpec(
                                name = decl.name,
                                description = decl.description.take(200), // ตัด description ยาวๆ ประหยัด token
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
                    // 429 transient: Groq บอก "try again in X s" → รอแล้วลองรอบเดิม (สูงสุด 2 ครั้ง) ก่อนค่อย degrade
                    val retryMatch = Regex("try again in ([\\d.]+)s", RegexOption.IGNORE_CASE).find(textBuffer.toString())
                    if (textBuffer.startsWith("⚠️") && retryMatch != null && rateLimitRetries < 2) {
                        rateLimitRetries++
                        val waitSec = retryMatch.groupValues[1].toDoubleOrNull() ?: 2.0
                        com.example.personalaibot.logDebug("Orchestrator", "429 transient — waiting ${waitSec + 1.0}s then retry (attempt $rateLimitRetries)")
                        emit("\n⏳ ติด rate limit ชั่วคราว — รอ ${(waitSec + 1.0).toInt()} วิ แล้วลองใหม่\n")
                        kotlinx.coroutines.delay(((waitSec + 1.0) * 1000).toLong())
                        continue
                    }
                    // รอบแรกได้ error กลับมา (413/404/429 ฯลฯ) → degraded retry: ตัด tools + history สั้นลง แล้วลองใหม่
                    if (!degraded && round == 1 && textBuffer.startsWith("⚠️")) {
                        degraded = true
                        while (messages.size > 3) messages.removeAt(0)
                        com.example.personalaibot.logError("Orchestrator", "Provider error in round 1 — degraded retry (no tools, short history): ${textBuffer.toString().take(200)}")
                        emit("\n🔄 โมเดลตอบ error — ลองใหม่แบบประหยัดโควต้า (ตัด tools/history)\n")
                        continue
                    }
                    emit(textBuffer.toString())
                } else if (!toolCallsDetected.isNullOrEmpty() && textBuffer.isNotEmpty()) {
                    com.example.personalaibot.logDebug("Orchestrator", "Discarded pre-tool text (${textBuffer.length} chars) to prevent hallucination")
                }

                // รอบว่างเปล่า (0 ตัวอักษร ไม่มี tool call) → degraded retry ครั้งเดียว กัน Empty response เงียบๆ
                if (toolCallsDetected.isNullOrEmpty() && textBuffer.isEmpty()) {
                    if (!degraded) {
                        degraded = true
                        while (messages.size > 3) messages.removeAt(0)
                        com.example.personalaibot.logError("Orchestrator", "Empty round — degraded retry")
                        emit("\n🔄 โมเดลไม่ตอบ — ลองใหม่แบบประหยัดโควต้า\n")
                        continue
                    }
                    com.example.personalaibot.logError("Orchestrator", "Empty round even after degraded — giving up")
                    break
                }

                com.example.personalaibot.logDebug("Orchestrator", "Round $round finished. Total chars: ${fullResponse.length}, Tools: ${toolCallsDetected?.size ?: 0}")
            } catch (e: Exception) {
                com.example.personalaibot.logError("Orchestrator", "Streaming error in round $round: ${e.message}", e)
                // timeout/network error รอบแรก → degraded retry ครั้งเดียวก่อนยอมแพ้
                if (!degraded && round == 1) {
                    degraded = true
                    while (messages.size > 3) messages.removeAt(0)
                    emit("\n🔄 เชื่อมต่อมีปัญหา — ลองใหม่แบบประหยัดโควต้า\n")
                    continue
                }
                emit("⚠️ เกิดข้อผิดพลาดในการรับข้อมูลจาก $providerId: ${e.message}")
                break
            }

            if (toolCallsDetected.isNullOrEmpty()) {
                // No tools, we are done
                messages.add(LlmMessage("assistant", fullResponse))
                break
            }

            // Execute Tools
            // กัน model วนเรียก tool เดิมซ้ำ (เคสจริง: gpt-oss เรียก search_web ซ้ำทุกรอบจนหมด maxRounds แล้วตอบว่าง)
            val toolSignature = toolCallsDetected!!.joinToString("|") { "${it.name}:${it.arguments.take(100)}" }
            if (toolSignature == lastToolSignature) {
                com.example.personalaibot.logError("Orchestrator", "Tool call loop detected ($toolSignature) — forcing final answer without tools")
                degraded = true // tools=null ในรอบถัดไป (messages ยังเก็บผล tool ไว้ — ตัด history ไม่ได้ เดี๋ยวผล tool หาย)
                messages.add(LlmMessage("assistant", fullResponse, toolCalls = toolCallsDetected))
                toolCallsDetected.forEach { tc ->
                    messages.add(LlmMessage(role = "tool", content = "Error: เรียก tool นี้ซ้ำหลายครั้งแล้ว — ให้สรุปคำตอบจากข้อมูลที่มีอยู่แล้วทันที ห้ามเรียก tool เพิ่ม", toolCallId = tc.id))
                }
                emit("\n🔄 โมเดลเรียก tool ซ้ำ — บังคับสรุปคำตอบจากข้อมูลที่มี\n")
                round++
                continue
            }
            lastToolSignature = toolSignature
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

                // log preview ผล tool — ใช้ตรวจกรณี model ตอบตัวเลขเพี้ยนทั้งที่เรียก tool ถูก
                // (เคสจริง: llama-3.3-70b ตอบ XAUUSD=1,950 ทั้งที่ tool คืน 4,3xx — ไม่มี log ผล tool เลยหาสาเหตุไม่ได้)
                com.example.personalaibot.logDebug("Orchestrator",
                    "Tool result [${toolCall.name}] ${result.result.length} chars: ${result.result.replace("\n", " ").take(300)}")

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

    /** สถานะการเชื่อมต่อ Live session — ใช้แสดง "กำลังเชื่อมต่อ…" ในแชทกันเคส 3.1 READY ช้า 7–15 วิ */
    val liveConnectionState: StateFlow<com.example.personalaibot.data.ConnectionState> = liveService.connectionState

    /** ส่งข้อความแทรกเข้า Live session (trigger turn ใหม่ เช่นหลังเปลี่ยนเสียง/เปิดกล้อง) */
    suspend fun sendLiveClientText(text: String) = liveService.sendClientText(text)

    /**
     * ส่งข้อความผ่าน realtimeInput — model จะตอบเองทันที (เหมือน user พูดเข้ามา)
     * ⚠️ ห้ามใช้ sendClientText สำหรับเหตุการณ์ที่ต้องการให้ model พูดเอง —
     * clientContent ขณะ audio streaming เป็นแค่ context (พิสูจน์แล้วจากเคส voice-change greeting 2026-08-09)
     */
    suspend fun sendLiveRealtimeText(text: String) = liveService.sendRealtimeText(text)

    /** Long-task delivery: wait through a transient Live reconnect before falling back to notification/TTS. */
    suspend fun sendLiveRealtimeTextWhenReady(text: String, timeoutMs: Long = 30_000L) =
        liveService.sendRealtimeTextWhenReady(text, timeoutMs)

    /** ตั้งข้อความให้ AI พูดทักอัตโนมัติทันทีที่ Live session READY ครั้งถัดไป */
    fun setLiveGreetingOnReady(text: String?) {
        liveService.pendingGreetingOnReady = text
    }

    /** ตั้ง greeting เฉพาะเมื่อยังไม่มีของเดิมค้างอยู่ (กันทับ greeting สำคัญ เช่นยืนยันเปลี่ยนเสียง) */
    fun setLiveGreetingOnReadyIfAbsent(text: String) {
        if (liveService.pendingGreetingOnReady == null) liveService.pendingGreetingOnReady = text
    }

    /**
     * ผูก Voice Profile เข้ากับ Agent Identity — เปลี่ยนเสียงแล้วเพศ/น้ำเสียง/คำลงท้ายเปลี่ยนตาม
     * persist ลง Core Memory ทันที (จำข้าม session)
     */
    suspend fun applyVoiceIdentity(voiceName: String): Boolean {
        val profile = GeminiVoiceProfiles.findByName(voiceName) ?: return false
        val genderTh = if (profile.gender == VoiceGender.FEMALE) "หญิง" else "ชาย"
        JarvisPersona.updateIdentityField("agent", "gender", genderTh)
        JarvisPersona.updateIdentityField("agent", "vibe", "${profile.tone} (โปรไฟล์เสียง ${profile.name})")
        // persist เฉพาะ 2 ค่าที่เปลี่ยนจริง — ห้ามเขียน map ทั้งก้อนเพราะจะทับ user identity ที่ตั้งไว้
        memoryManager.setCoreMemory("agent_gender", genderTh)
        memoryManager.setCoreMemory("agent_vibe", "${profile.tone} (โปรไฟล์เสียง ${profile.name})")
        com.example.personalaibot.logDebug("Orchestrator", "Voice identity applied: ${profile.name} → gender=$genderTh, vibe=${profile.tone}")
        return true
    }

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

    override suspend fun onChartControl(args: Map<String, String>): String {
        com.example.personalaibot.logDebug("Orchestrator", "SideEffect: Chart control ${args["action"]} ${args["layout"] ?: args["overlay"] ?: args["symbol"] ?: ""}")
        return chartControlCallback?.invoke(args)
            ?: "⚠️ ระบบกราฟยังไม่พร้อมใช้งาน"
    }

    fun setChartControlHandler(handler: (Map<String, String>) -> String) {
        this.chartControlCallback = handler
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

    override suspend fun onReadAgentTool(filename: String): String {
        val handler = fileHandler ?: return "Error: ยังไม่ได้เชื่อมต่อ file system"
        return handler.invoke("file_read", mapOf("path" to "custom_agent_tools/$filename"))
    }

    override suspend fun onDeleteAgentTool(filename: String): String {
        val handler = fileHandler ?: return "Error: ยังไม่ได้เชื่อมต่อ file system"
        val result = handler.invoke("file_delete", mapOf("path" to "custom_agent_tools/$filename"))
        com.example.personalaibot.logDebug("Orchestrator", "Agent Tool deleted: custom_agent_tools/$filename → $result")
        return result
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

                    val execType = obj["executionType"]?.jsonPrimitive?.contentOrNull ?: "prompt"

                    // Parse parameters if present in JSON
                    val paramsObj = obj["parameters"]?.jsonObject
                    val parsedParams = if (paramsObj != null) {
                        val propsObj = paramsObj["properties"]?.jsonObject ?: emptyMap()
                        val reqList = paramsObj["required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                        val props = propsObj.mapValues { (_, v) ->
                            val pObj = v.jsonObject
                            val pType = pObj["type"]?.jsonPrimitive?.contentOrNull ?: "STRING"
                            val pDesc = pObj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                            com.example.personalaibot.tools.ParameterProperty(pType, pDesc)
                        }
                        com.example.personalaibot.tools.FunctionParameters(type = "OBJECT", properties = props, required = reqList)
                    } else null

                    com.example.personalaibot.tools.ToolRegistry.registerCustomTool(
                        com.example.personalaibot.tools.FunctionDeclaration(
                            name = name,
                            description = "[CUSTOM] $desc",
                            parameters = parsedParams
                        )
                    )
                    com.example.personalaibot.tools.ToolRegistry.registerSkill(
                        com.example.personalaibot.tools.SkillDescriptor(
                            name = name,
                            description = desc,
                            systemPromptAddon = addon,
                            triggerKeywords = keywords,
                            author = obj["author"]?.jsonPrimitive?.contentOrNull ?: "Jarvis Agent",
                            parameters = parsedParams,
                            executionType = execType
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
                val toolNameRaw = args["tool_name"]?.trim().takeUnless { it.isNullOrBlank() } ?: "trading_price"
                val fieldRaw = args["condition_field"]?.trim().takeUnless { it.isNullOrBlank() } ?: "price"
                val operatorRaw = when (args["condition_operator"]?.trim()) {
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
                val interval = args["interval_minutes"]?.toLongOrNull()?.coerceIn(1L, 1440L) ?: 1L
                val rawTimeframe = args["timeframe"]?.trim()?.lowercase()
                val supportedTimeframes = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w")
                val requestedTimeframe = if (rawTimeframe != null && rawTimeframe != "all" && !rawTimeframe.contains("ทุก")) {
                    com.example.personalaibot.tools.trading.TaIndicators.normalizeTimeframe(rawTimeframe)
                } else rawTimeframe
                val timeframeTargets = when {
                    requestedTimeframe == "all" || requestedTimeframe == "ทุก" || requestedTimeframe == "ทุก timeframe" -> supportedTimeframes
                    requestedTimeframe.isNullOrBlank() -> listOf(com.example.personalaibot.automation.IndicatorAlertProvider.splitSymbolAndTf(symbol).second.lowercase())
                    else -> listOf(requestedTimeframe)
                }.map { com.example.personalaibot.tools.trading.TaIndicators.normalizeTimeframe(it) }
                 .filter { it in supportedTimeframes }.distinct()
                if (timeframeTargets.isEmpty()) {
                    return "❌ timeframe '$rawTimeframe' ไม่รองรับ — ใช้ 1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w หรือ all"
                }
                val delivery = when (args["delivery"]?.trim()?.lowercase()) {
                    "direct" -> "direct"   // ส่ง notification+แชทโดยตรง ไม่ผ่าน AI (ประหยัดโทเคน)
                    else -> "ai"           // default: alert → AI quick-check → ผู้ใช้
                }

                // Normalize: AI บางเครื่อง/บางโมเดลเลือก trading_technical_analysis.signal (STRONG BUY/SELL)
                // แทน trading_signal_alert (signal_buy/sell) — แปลงอัตโนมัติให้ผลลัพธ์ตรงกันทุกเครื่อง
                // (payload ครบ Entry/SL/TP/RR/ATR + baseline กันสัญญาณเก่าเด้ง)
                val isTaTradeSignal = toolNameRaw == "trading_technical_analysis" && fieldRaw == "signal" &&
                    value.uppercase().let { it.contains("BUY") || it.contains("SELL") }
                val toolName: String
                val field: String
                val operator: com.example.personalaibot.automation.ConditionOperator
                if (isTaTradeSignal) {
                    toolName = "trading_signal_alert"
                    field = if (value.uppercase().contains("BUY")) "signal_buy" else "signal_sell"
                    operator = com.example.personalaibot.automation.ConditionOperator.GTE
                    com.example.personalaibot.logDebug("Orchestrator", "Normalized TA signal alert → trading_signal_alert/$field")
                } else {
                    toolName = toolNameRaw
                    field = fieldRaw
                    operator = operatorRaw
                }

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

                // Signal Alert: แปลง signal_buy/signal_sell (>= 1) เป็น signal_*_id (> เวลาสร้าง)
                // ยิงเฉพาะสัญญาณที่เกิด "หลัง" ตั้ง alert — กันเด้งทันทีจาก marker เก่าที่ค้างอยู่
                val isSignalSide = toolName == "trading_signal_alert" &&
                    (field == "signal_buy" || field == "signal_sell")
                val effField = if (isSignalSide) "${field}_id" else field
                val effOperator = if (isSignalSide) com.example.personalaibot.automation.ConditionOperator.GT else operator
                val effValue = if (isSignalSide) {
                    kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString()
                } else value

                val (symbolBase, embeddedTf) = com.example.personalaibot.automation.IndicatorAlertProvider.splitSymbolAndTf(symbol)
                val effectiveTargets = if (requestedTimeframe.isNullOrBlank()) {
                    listOf(embeddedTf.lowercase())
                } else timeframeTargets
                val created = mutableListOf<String>()
                val skipped = mutableListOf<String>()
                effectiveTargets.forEach { tf ->
                    val targetSymbol = if (toolName == "trading_signal_alert") {
                        if (tf == "1h") symbolBase else "$symbolBase@$tf"
                    } else {
                        if (requestedTimeframe.isNullOrBlank()) symbol else "$symbolBase@$tf"
                    }
                    val duplicate = automationManager.activeJobs.value.any { existing ->
                        existing.tool_name == toolName && existing.symbol.equals(targetSymbol, ignoreCase = true) &&
                            runCatching {
                                val c = com.example.personalaibot.automation.automationJson.decodeFromString(
                                    com.example.personalaibot.automation.AutomationCondition.serializer(), existing.condition_json
                                )
                                c.field == effField && c.operator == effOperator
                            }.getOrDefault(false)
                    }
                    if (duplicate) {
                        skipped += tf
                    } else {
                        val targetName = if (effectiveTargets.size == 1) name else "$name [$tf]"
                        automationManager.registerJob(
                            name = targetName,
                            symbol = targetSymbol,
                            exchange = null,
                            toolName = toolName,
                            condition = com.example.personalaibot.automation.AutomationCondition(effField, effOperator, effValue, delivery),
                            intervalMinutes = interval
                        )
                        created += tf
                    }
                }
                val deliveryDesc = if (delivery == "direct") "โหมดส่งตรง (notification+แชท ไม่ผ่าน AI)" else "โหมด AI วิเคราะห์ก่อนแจ้ง"
                val tfDesc = if (effectiveTargets.size == 1) effectiveTargets.first() else "${effectiveTargets.size} timeframe (${effectiveTargets.joinToString(", ")})"
                val skipDesc = if (skipped.isNotEmpty()) " | มีอยู่แล้ว: ${skipped.joinToString(", ")}" else ""
                "✅ ตั้งการแจ้งเตือน '$name' แล้ว — $symbolBase/$tfDesc ($field ${args["condition_operator"] ?: ">="} $value) ทุก $interval นาที | สร้างใหม่ ${created.size} รายการ$skipDesc | $deliveryDesc"
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
    private var chartControlCallback: ((Map<String, String>) -> String)? = null
    private var displayReportCallback: ((String) -> Unit)? = null
}

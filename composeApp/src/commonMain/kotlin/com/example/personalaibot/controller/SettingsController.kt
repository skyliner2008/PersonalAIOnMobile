package com.example.personalaibot.controller

import com.example.personalaibot.ai.JarvisOrchestrator
import com.example.personalaibot.camera.CameraAnalysisService
import com.example.personalaibot.camera.CameraProviderType
import com.example.personalaibot.data.GeminiModel
import com.example.personalaibot.db.JarvisDatabase
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import com.example.personalaibot.memory.JarvisMemoryManager
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase-5 refactor: แยก Settings (API keys, โมเดล, เสียง, identity, provider keys)
 * ออกจาก JarvisViewModel — coupling กับ autoTrading ผ่าน lambda preferFreeSync
 */
class SettingsController(
    private val scope: CoroutineScope,
    private val database: JarvisDatabase,
    private val memoryManager: JarvisMemoryManager,
    private val orchestrator: JarvisOrchestrator,
    private val cameraService: CameraAnalysisService,
    private val client: HttpClient,
    /** sync ค่า preferFreeOnly เข้า AutoTradingViewModel (lazy ใน VM) */
    private val preferFreeSync: (Boolean) -> Unit
) {
    private val defaultMainModel = com.example.personalaibot.data.ModelConfig.DEFAULT_MAIN_MODEL
    private val defaultLiveModel = com.example.personalaibot.data.ModelConfig.DEFAULT_LIVE_MODEL
    private val defaultVoiceName = "Aoede" // Female (Soothing)

    // List of old/deprecated models to auto-migrate from
    private val deprecatedLiveModels = listOf(
        "gemini-2.0-flash-live-001",
        "gemini-1.5-flash-latest",
        "gemini-live-preview",
        "gemini-2.5-flash-native-audio-preview-12-2025",
        "gemini-2.5-flash-native-audio-preview"
    )

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(defaultMainModel)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _liveModelName = MutableStateFlow(defaultLiveModel)
    val liveModelName: StateFlow<String> = _liveModelName.asStateFlow()

    private val _voiceName = MutableStateFlow(defaultVoiceName)
    val voiceName: StateFlow<String> = _voiceName.asStateFlow()

    private val _availableModels = MutableStateFlow<List<GeminiModel>>(emptyList())
    val availableModels: StateFlow<List<GeminiModel>> = _availableModels.asStateFlow()

    private val _floatingWidgetEnabled = MutableStateFlow(false)
    val floatingWidgetEnabled: StateFlow<Boolean> = _floatingWidgetEnabled.asStateFlow()

    // ─── External API Keys (OpenAI, Claude, OpenRouter) ─────────────────────────
    private val _openaiApiKey = MutableStateFlow("")
    val openaiApiKey: StateFlow<String> = _openaiApiKey.asStateFlow()

    private val _claudeApiKey = MutableStateFlow("")
    val claudeApiKey: StateFlow<String> = _claudeApiKey.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow("")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _minimaxApiKey = MutableStateFlow("")
    val minimaxApiKey: StateFlow<String> = _minimaxApiKey.asStateFlow()

    private val _groqApiKey = MutableStateFlow("")
    val groqApiKey: StateFlow<String> = _groqApiKey.asStateFlow()

    private val _nvidiaNimApiKey = MutableStateFlow("")
    val nvidiaNimApiKey: StateFlow<String> = _nvidiaNimApiKey.asStateFlow()

    /** Gemini fallback chain ที่ user ตั้งเอง (เรียงลำดับ) — empty = ใช้ default ModelConfig */
    private val _geminiFallbackModels = MutableStateFlow<List<String>>(emptyList())
    val geminiFallbackModels: StateFlow<List<String>> = _geminiFallbackModels.asStateFlow()

    /** ค่า toggle "Show free models only" ใน Settings — persist เพราะเดิมเด้งกลับเป็นไม่ติ๊กทุกครั้ง */
    private val _showFreeModelsOnly = MutableStateFlow(false)
    val showFreeModelsOnly: StateFlow<Boolean> = _showFreeModelsOnly.asStateFlow()

    /** ผล auto-test โมเดล ("provider/model" → capability) — persist ใน setting "model_caps_v1" */
    private val _modelCaps = MutableStateFlow<Map<String, com.example.personalaibot.data.providers.ModelAutoTester.ModelCapability>>(emptyMap())
    val modelCaps: StateFlow<Map<String, com.example.personalaibot.data.providers.ModelAutoTester.ModelCapability>> = _modelCaps.asStateFlow()

    /** Gemini API keys ทั้งหมด (multi free-tier accounts) — ใช้ rotate เมื่อ key ปัจจุบันติดลิมิต */
    private val _geminiApiKeys = MutableStateFlow<List<String>>(emptyList())
    val geminiApiKeys: StateFlow<List<String>> = _geminiApiKeys.asStateFlow()

    /** โหลด settings ทั้งหมดจาก DB + wire orchestrator/camera — เรียกจาก VM.loadSettings */
    suspend fun loadPersistedSettings() {
        val savedKey = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("api_key").executeAsOneOrNull() ?: ""
        }
        val savedModel = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("model_name").executeAsOneOrNull()
                ?: defaultMainModel
        }
        var savedLiveModel = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("live_model_name").executeAsOneOrNull()
                ?: defaultLiveModel
        }
        val savedVoiceName = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("voice_name").executeAsOneOrNull()
                ?: defaultVoiceName
        }
        val savedWidgetEnabled = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("floating_widget_enabled").executeAsOneOrNull() == "true"
        }
        val savedPreferFreeOnly = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("prefer_free_only").executeAsOneOrNull() == "true"
        }
        preferFreeSync(savedPreferFreeOnly)

        // Auto-migration logic for deprecated live models
        if (deprecatedLiveModels.contains(savedLiveModel)) {
            logDebug("JarvisVM", "Migrating deprecated live model '$savedLiveModel' to '$defaultLiveModel'")
            savedLiveModel = defaultLiveModel
            // Save the corrected model back to the database
            withContext(Dispatchers.IO) {
                database.jarvisDatabaseQueries.insertSetting("live_model_name", savedLiveModel)
            }
        }

        // Load external API keys
        val savedOpenaiKey = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("openai_api_key").executeAsOneOrNull() ?: ""
        }
        val savedClaudeKey = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("claude_api_key").executeAsOneOrNull() ?: ""
        }
        val savedOpenRouterKey = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("openrouter_api_key").executeAsOneOrNull() ?: ""
        }
        val savedMinimaxKey = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("minimax_api_key").executeAsOneOrNull() ?: ""
        }
        val savedGroqKey = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("groq_api_key").executeAsOneOrNull() ?: ""
        }
        val savedNvidiaNimKey = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("nvidia_nim_api_key").executeAsOneOrNull() ?: ""
        }
        // Fallback chain ที่ user ตั้งเอง (comma-separated) — empty = ใช้ default ModelConfig
        val savedGeminiFallback = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("gemini_fallback_models").executeAsOneOrNull() ?: ""
        }
        _geminiFallbackModels.value = savedGeminiFallback.split(",").map { it.trim() }.filter { it.isNotBlank() }
        orchestrator.updateGeminiFallbackChain(_geminiFallbackModels.value)
        // Gemini multi-key: โหลด list + merge primary key ไว้หัว chain เสมอ (rotation จะไล่จาก key ปัจจุบัน)
        val savedGeminiApiKeys = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("gemini_api_keys").executeAsOneOrNull() ?: ""
        }
        _geminiApiKeys.value = savedGeminiApiKeys.lines().map { it.trim() }.filter { it.isNotBlank() }
        orchestrator.updateGeminiApiKeys(listOf(savedKey) + _geminiApiKeys.value.filter { it != savedKey })
        // ผล auto-test โมเดล (Groq/NIM) จากรอบก่อน — ใช้ซ่อนโมเดลที่ใช้ไม่ได้จาก list
        val savedModelCaps = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("model_caps_v1").executeAsOneOrNull() ?: ""
        }
        _modelCaps.value = com.example.personalaibot.data.providers.ModelAutoTester.parseCaps(savedModelCaps)
        // ส่งผลเทสเข้า orchestrator — cross-provider fallback จะเลือกเฉพาะโมเดลที่เทสผ่านจริง
        orchestrator.updateModelCaps(_modelCaps.value)
        // toggle "Show free models only" — เดิมไม่ได้โหลดกลับ เด้งเป็นไม่ติ๊กทุกครั้ง
        _showFreeModelsOnly.value = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("show_free_models_only").executeAsOneOrNull()?.let { it == "true" } ?: false
        }

        _apiKey.value = savedKey
        _selectedModel.value = savedModel
        _liveModelName.value = savedLiveModel
        _voiceName.value = savedVoiceName
        _floatingWidgetEnabled.value = savedWidgetEnabled
        _openaiApiKey.value = savedOpenaiKey
        _claudeApiKey.value = savedClaudeKey
        _openRouterApiKey.value = savedOpenRouterKey
        _minimaxApiKey.value = savedMinimaxKey
        _groqApiKey.value = savedGroqKey
        _nvidiaNimApiKey.value = savedNvidiaNimKey
        orchestrator.updateConfig(savedKey, savedModel, savedLiveModel, savedVoiceName)

        // Persist ค่า key/โมเดลที่ fallback สลับแล้วใช้งานได้จริงกลับลง settings
        // — รอบถัดไป/เปิดแอปใหม่จะเริ่มจากตัวที่ใช้ได้ ไม่วนกลับไปตัวที่ติดลิมิต
        orchestrator.getGeminiService().onWorkingConfigChanged = { workingModel, workingKey ->
            logDebug("JarvisVM", "Persist working fallback config: model=$workingModel key=${com.example.personalaibot.maskApiKey(workingKey)}")
            _selectedModel.value = workingModel
            _apiKey.value = workingKey
            scope.launch(Dispatchers.IO) {
                database.jarvisDatabaseQueries.insertSetting("model_name", workingModel)
                database.jarvisDatabaseQueries.insertSetting("api_key", workingKey)
            }
        }

        // Register external providers on startup
        orchestrator.updateProviderKeys(
            openaiKey = savedOpenaiKey,
            claudeKey = savedClaudeKey,
            openRouterKey = savedOpenRouterKey,
            minimaxKey = savedMinimaxKey,
            groqKey = savedGroqKey,
            nvidiaNimKey = savedNvidiaNimKey
        )

        // Initialize camera service keys
        cameraService.updateProviderKeys(
            geminiApiKey = savedKey,
            openaiApiKey = savedOpenaiKey,
            claudeApiKey = savedClaudeKey
        )

        if (savedKey.isNotBlank()) {
            fetchModels()
        }
    }

    /** เลือกโปรไฟล์เสียงจาก Settings — ผูก identity + persist + (ถ้ากำลัง live) restart session พร้อมยืนยันเสียง */
    fun selectVoiceProfile(voice: String) {
        scope.launch {
            orchestrator.applyVoiceIdentity(voice)
            updateSettings(_apiKey.value, _selectedModel.value, _liveModelName.value, voice)
        }
    }

    fun updateSettings(key: String, model: String, liveModel: String = _liveModelName.value, voice: String = _voiceName.value, preferFree: Boolean = false) {
        scope.launch {
            _apiKey.value = key
            _selectedModel.value = model
            _liveModelName.value = liveModel
            _voiceName.value = voice
            withContext(Dispatchers.IO) {
                database.jarvisDatabaseQueries.insertSetting("api_key", key)
                database.jarvisDatabaseQueries.insertSetting("model_name", model)
                database.jarvisDatabaseQueries.insertSetting("live_model_name", liveModel)
                database.jarvisDatabaseQueries.insertSetting("voice_name", voice)
                database.jarvisDatabaseQueries.insertSetting("prefer_free_only", if (preferFree) "true" else "false")
            }
            orchestrator.updateConfig(key, model, liveModel, voice)
            // primary key เปลี่ยน → อัปเดต rotation chain ให้เริ่มจาก key ใหม่
            orchestrator.updateGeminiApiKeys(listOf(key) + _geminiApiKeys.value.filter { it != key })
            preferFreeSync(preferFree)

            // Sync vision provider ตาม provider หลักของแชท (review หมวด 11)
            // — เปลี่ยนโมเดลหลักแล้วกล้อง/vision ควรตามไปใช้ผู้ให้บริการเดียวกันอัตโนมัติ
            val providerId = if (model.contains("/")) model.substringBefore("/") else "gemini"
            val visionType = when (providerId.lowercase()) {
                "openai" -> CameraProviderType.OPENAI_GPT4O
                "claude", "anthropic" -> CameraProviderType.CLAUDE_SONNET
                else -> CameraProviderType.GEMINI_LIVE
            }
            runCatching { cameraService.switchProvider(visionType) }
                .onFailure { logDebug("JarvisVM", "Vision provider sync skipped: ${it.message}") }

            fetchModels()
        }
    }

    fun setFloatingWidgetEnabled(enabled: Boolean) {
        _floatingWidgetEnabled.value = enabled
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("floating_widget_enabled", enabled.toString())
        }
    }

    private fun fetchModels() {
        scope.launch {
            try {
                val models = orchestrator.listAvailableModels()
                if (models.isNotEmpty()) {
                    _availableModels.value = models
                }
            } catch (e: Exception) {
                logError("JarvisVM", "Failed to fetch models", e)
            }
        }
    }

    suspend fun getModelsForProvider(providerId: String, freeOnly: Boolean = false, apiKeyOverride: String? = null): List<com.example.personalaibot.data.providers.LlmModelInfo> {
        val keyToUse = apiKeyOverride ?: when (providerId.lowercase()) {
            "gemini" -> _apiKey.value
            "openai" -> _openaiApiKey.value
            "claude", "anthropic" -> _claudeApiKey.value
            "openrouter" -> _openRouterApiKey.value
            "minimax" -> _minimaxApiKey.value
            "groq" -> _groqApiKey.value
            "nvidia_nim", "nim" -> _nvidiaNimApiKey.value
            else -> ""
        }
        return try {
            val models = orchestrator.listModelsForProvider(providerId, freeOnly, keyToUse)
            // ใช้ผล auto-test (ถ้ามี): ซ่อนโมเดลที่ chat ไม่ผ่าน + แก้ tag 🔧 ตามผลเทสจริง
            val caps = _modelCaps.value
            if (caps.isEmpty()) models else models.mapNotNull { m ->
                when (val cap = caps["$providerId/${m.id}"]) {
                    null -> m // ยังไม่เคยเทส → แสดงตามเดิม
                    else -> if (!cap.chatOk) null else m.copy(supportsFunctions = cap.toolsOk)
                }
            }
        } catch (e: Exception) {
            logError("JarvisVM", "Failed to fetch models for $providerId", e)
            emptyList()
        }
    }

    /**
     * 2026-04-30 (P1.3) — return only models that can drive a Live / multi-modal
     * session.  Replaces the keyword-based filter ("live"/"flash"/"pro") that
     * was hard-coded in SettingsDialog and missed legitimate live-capable
     * models that don't follow that naming pattern.  Falls back to the keyword
     * heuristic when capability flags aren't populated by the provider.
     */
    suspend fun getLiveCapableModels(providerId: String = "gemini", apiKeyOverride: String? = null): List<com.example.personalaibot.data.providers.LlmModelInfo> {
        val all = getModelsForProvider(providerId, freeOnly = false, apiKeyOverride)
        val liveByCapability = all.filter { it.supportsVision }
        if (liveByCapability.isNotEmpty()) return liveByCapability
        // Fallback heuristic for providers that don't expose capability flags
        return all.filter { m ->
            val n = (m.id + " " + m.displayName).lowercase()
            n.contains("live") || n.contains("realtime") || n.contains("flash") || n.contains("vision") || n.contains("multimodal")
        }
    }

    /**
     * 2026-04-30 (P1.1) — verify an API key by hitting the provider's listModels
     * endpoint.  Surfaces real auth/network errors with friendly messages.
     * The Settings UI uses this to show ✓ / ✗ next to each key field.
     */
    suspend fun testApiKey(
        providerId: String,
        apiKeyOverride: String? = null,
    ): com.example.personalaibot.data.providers.ApiKeyTester.TestResult {
        val key = apiKeyOverride ?: when (providerId.lowercase()) {
            "gemini" -> _apiKey.value
            "openai" -> _openaiApiKey.value
            "claude", "anthropic" -> _claudeApiKey.value
            "openrouter" -> _openRouterApiKey.value
            "minimax" -> _minimaxApiKey.value
            "groq" -> _groqApiKey.value
            "nvidia_nim", "nim" -> _nvidiaNimApiKey.value
            else -> ""
        }
        val result = com.example.personalaibot.data.providers.ApiKeyTester.test(client, providerId, key)

        // ถ้า Test สำเร็จ ให้อัพเดท Orchestrator ทันทีเพื่อให้ใช้งานได้เลย (In-memory)
        if (result.ok && key.isNotBlank()) {
            when (providerId.lowercase()) {
                "gemini" -> orchestrator.updateConfig(key, _selectedModel.value, _liveModelName.value, _voiceName.value)
                "openai" -> orchestrator.updateProviderKeys(openaiKey = key)
                "claude", "anthropic" -> orchestrator.updateProviderKeys(claudeKey = key)
                "openrouter" -> orchestrator.updateProviderKeys(openRouterKey = key)
                "minimax" -> orchestrator.updateProviderKeys(minimaxKey = key)
                "groq" -> orchestrator.updateProviderKeys(groqKey = key)
                "nvidia_nim", "nim" -> orchestrator.updateProviderKeys(nvidiaNimKey = key)
            }
        }

        return result
    }

    fun setShowFreeModelsOnly(v: Boolean) {
        _showFreeModelsOnly.value = v
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("show_free_models_only", if (v) "true" else "false")
        }
    }

    /**
     * Auto-test ทุก model ของ provider (Groq/NIM) — ไล่เทส chat + tool calling ทีละตัว
     * ผล persist ถาวร: model ที่ chat ไม่ผ่านจะถูกซ่อนจาก list, ที่ tools ไม่ผ่านจะไม่มี tag 🔧
     */
    suspend fun autoTestProviderModels(
        providerId: String,
        onProgress: (current: Int, total: Int, modelId: String) -> Unit = { _, _, _ -> }
    ): Int {
        val key = when (providerId) {
            "groq" -> _groqApiKey.value
            "openrouter" -> _openRouterApiKey.value
            else -> return 0
        }
        if (key.isBlank()) return 0
        val provider = when (providerId) {
            "groq" -> com.example.personalaibot.data.providers.GroqLlmProvider(client, key)
            else -> com.example.personalaibot.data.providers.OpenRouterLlmProvider(client, key)
        }
        val results = com.example.personalaibot.data.providers.ModelAutoTester.testAllModels(
            client, provider, key, freeOnly = (providerId == "openrouter"), onProgress = onProgress
        )
        val merged = _modelCaps.value.toMutableMap()
        results.forEach { (id, cap) -> merged["$providerId/$id"] = cap }
        _modelCaps.value = merged
        orchestrator.updateModelCaps(merged)
        withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting(
                "model_caps_v1",
                com.example.personalaibot.data.providers.ModelAutoTester.serializeCaps(merged)
            )
        }
        return results.count { it.value.chatOk }
    }

    private fun persistGeminiApiKeys(keys: List<String>) {
        scope.launch {
            _geminiApiKeys.value = keys
            withContext(Dispatchers.IO) {
                database.jarvisDatabaseQueries.insertSetting("gemini_api_keys", keys.joinToString("\n"))
            }
            orchestrator.updateGeminiApiKeys(listOf(_apiKey.value) + keys.filter { it != _apiKey.value })
        }
    }

    fun addGeminiApiKey(key: String) {
        val k = key.trim()
        if (k.isBlank() || k in _geminiApiKeys.value) return
        persistGeminiApiKeys(_geminiApiKeys.value + k)
    }

    fun removeGeminiApiKey(key: String) {
        persistGeminiApiKeys(_geminiApiKeys.value - key)
    }

    fun editGeminiApiKey(oldKey: String, newKey: String) {
        val k = newKey.trim()
        if (k.isBlank()) return
        persistGeminiApiKeys(_geminiApiKeys.value.map { if (it == oldKey) k else it })
    }

    /** บันทึก fallback chain (เรียงลำดับสำคัญ) — ส่ง list ว่างเพื่อกลับไปใช้ default */
    fun updateGeminiFallbackModels(models: List<String>) {
        scope.launch {
            _geminiFallbackModels.value = models
            withContext(Dispatchers.IO) {
                database.jarvisDatabaseQueries.insertSetting("gemini_fallback_models", models.joinToString(","))
            }
            orchestrator.updateGeminiFallbackChain(models)
        }
    }

    fun updateExternalApiKeys(
        openai: String = _openaiApiKey.value,
        claude: String = _claudeApiKey.value,
        openRouter: String = _openRouterApiKey.value,
        minimax: String = _minimaxApiKey.value,
        groq: String = _groqApiKey.value,
        nvidiaNim: String = _nvidiaNimApiKey.value
    ) {
        scope.launch {
            _openaiApiKey.value = openai
            _claudeApiKey.value = claude
            _openRouterApiKey.value = openRouter
            _minimaxApiKey.value = minimax
            _groqApiKey.value = groq
            _nvidiaNimApiKey.value = nvidiaNim
            withContext(Dispatchers.IO) {
                database.jarvisDatabaseQueries.insertSetting("openai_api_key", openai)
                database.jarvisDatabaseQueries.insertSetting("claude_api_key", claude)
                database.jarvisDatabaseQueries.insertSetting("openrouter_api_key", openRouter)
                database.jarvisDatabaseQueries.insertSetting("minimax_api_key", minimax)
                database.jarvisDatabaseQueries.insertSetting("groq_api_key", groq)
                database.jarvisDatabaseQueries.insertSetting("nvidia_nim_api_key", nvidiaNim)
            }
            // Update camera service with new keys
            cameraService.updateProviderKeys(
                geminiApiKey = _apiKey.value,
                openaiApiKey = openai,
                claudeApiKey = claude
            )
            // Register providers in LlmProviderRegistry
            orchestrator.updateProviderKeys(
                openaiKey = openai,
                claudeKey = claude,
                openRouterKey = openRouter,
                minimaxKey = minimax,
                groqKey = groq,
                nvidiaNimKey = nvidiaNim
            )
        }
    }

    fun updateIdentity(
        agentName: String,
        agentCreature: String,
        agentVibe: String,
        agentGender: String,
        userName: String,
        userCallName: String,
        userNotes: String,
    ) {
        val config = com.example.personalaibot.ai.JarvisPersona.IdentityConfig(
            agentName = agentName,
            agentCreature = agentCreature,
            agentVibe = agentVibe,
            agentGender = agentGender,
            userName = userName,
            userCallName = userCallName,
            userNotes = userNotes
        )
        com.example.personalaibot.ai.JarvisPersona.updateIdentity(config)
        // persist ลง Core Memory — เดิมแก้เฉพาะ in-memory ทำให้เปิดแอปใหม่แล้ว identity กลับเป็นค่า default
        scope.launch(Dispatchers.IO) {
            com.example.personalaibot.ai.JarvisPersona.toCoreMemoryMap(config).forEach { (k, v) ->
                runCatching { memoryManager.setCoreMemory(k, v) }
            }
        }
    }
}

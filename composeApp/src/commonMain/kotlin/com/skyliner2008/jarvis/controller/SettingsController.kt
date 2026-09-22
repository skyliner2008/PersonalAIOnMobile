package com.skyliner2008.jarvis.controller

import com.skyliner2008.jarvis.ai.JarvisOrchestrator
import com.skyliner2008.jarvis.camera.CameraAnalysisService
import com.skyliner2008.jarvis.camera.CameraProviderType
import com.skyliner2008.jarvis.data.GeminiModel
import com.skyliner2008.jarvis.db.JarvisDatabase
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import com.skyliner2008.jarvis.memory.JarvisMemoryManager
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
    private val defaultMainModel = com.skyliner2008.jarvis.data.ModelConfig.DEFAULT_MAIN_MODEL
    private val defaultLiveModel = com.skyliner2008.jarvis.data.ModelConfig.DEFAULT_LIVE_MODEL
    private val defaultVoiceName = "Aoede" // Female (Soothing)

    // โมเดล Live ที่ปิดบริการ/ไม่อยู่ใน console แล้ว — ย้ายค่าที่ผู้ใช้เคยเซฟไว้ไปยัง DEFAULT_LIVE_MODEL
    // (2026-09-16: `native-audio-preview-12-2025` ถูกเอาออกจากลิสต์นี้ — ตอนนี้คือ "2.5 Flash Native Audio Dialog"
    //  ที่ยังใช้ได้บน free tier และมีโควตา TPM สูงสุด จึงเป็นตัวสำรองที่ดี ไม่ใช่ตัวตกยุค)
    private val deprecatedLiveModels = listOf(
        "gemini-2.0-flash-live-001",   // shutdown 2025-12-09
        "gemini-live-2.5-flash-preview", // shutdown 2025-12-09
        "gemini-2.0-flash-exp",        // ไม่ใช่ Live model แล้ว
        "gemini-1.5-flash-latest",
        "gemini-live-preview",
        "gemini-2.5-flash-native-audio-preview",
        "gemini-2.5-flash-native-audio-latest",
        "gemini-2.5-flash-native-audio-preview-09-2025"
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
    private val _modelCaps = MutableStateFlow<Map<String, com.skyliner2008.jarvis.data.providers.ModelAutoTester.ModelCapability>>(emptyMap())
    val modelCaps: StateFlow<Map<String, com.skyliner2008.jarvis.data.providers.ModelAutoTester.ModelCapability>> = _modelCaps.asStateFlow()

    /** Gemini API keys ทั้งหมด (multi free-tier accounts) — ใช้ rotate เมื่อ key ปัจจุบันติดลิมิต */
    private val _geminiApiKeys = MutableStateFlow<List<String>>(emptyList())
    val geminiApiKeys: StateFlow<List<String>> = _geminiApiKeys.asStateFlow()

    /** โหลด settings ทั้งหมดจาก DB + wire orchestrator/camera — เรียกจาก VM.loadSettings */
    suspend fun loadPersistedSettings() {
        val savedKey = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("api_key").executeAsOneOrNull() ?: ""
        }
        var savedModel = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("model_name").executeAsOneOrNull()
                ?: defaultMainModel
        }

        // โหลด cached model names เพื่อใช้ตรวจสอบความถูกต้องของโมเดลทันทีตั้งแต่เริ่มบูต (Cold-start validation)
        val cachedGeminiModelsStr = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("cached_gemini_models").executeAsOneOrNull() ?: ""
        }
        if (cachedGeminiModelsStr.isNotBlank()) {
            val cachedList = cachedGeminiModelsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (cachedList.isNotEmpty()) {
                val pseudoModels = cachedList.map {
                    com.skyliner2008.jarvis.data.GeminiModel(
                        name = "models/$it",
                        supportedGenerationMethods = listOf("generateContent")
                    )
                }
                com.skyliner2008.jarvis.data.ModelConfig.updateAvailableModels(pseudoModels)
            }
        }

        // Auto-migration logic: ตรวจสอบโมเดลที่เลิกใช้งาน/ติด 404 (เช่น gemini-3.1-pro)
        val deprecatedMainModels = setOf(
            "gemini-3.1-pro",
            "gemini-1.5-pro",
            "gemini-1.5-pro-latest",
            "gemini-1.0-pro"
        )
        // free tier: แชทใช้ตระกูล flash-lite (500 req/วัน) เท่านั้น — ตระกูล flash มีแค่ 20 req/วัน
        // ค่าที่เคยบันทึกไว้ (เช่น gemini-3.6-flash) จึงถูกย้ายมาเป็น flash-lite อัตโนมัติ
        val needsChatModelMigration = deprecatedMainModels.contains(savedModel) ||
            com.skyliner2008.jarvis.data.ModelConfig.isModelDead(savedModel) ||
            !com.skyliner2008.jarvis.data.ModelConfig.isChatAllowedModel(savedModel)
        if (needsChatModelMigration) {
            val best = com.skyliner2008.jarvis.data.ModelConfig.getFallbackChain().first()
            logDebug("JarvisVM", "Migrating chat model '$savedModel' → '$best' (free tier: flash-lite only)")
            savedModel = best
            withContext(Dispatchers.IO) {
                database.jarvisDatabaseQueries.insertSetting("model_name", savedModel)
            }
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

        // อัปเกรดครั้งเดียวไปยังโมเดล Live รุ่นใหม่สุดของ project (2026-09-16: gemini-3.8-live)
        // ทำครั้งเดียวและจำไว้ใน DB — หลังจากนี้ผู้ใช้เลือกรุ่นไหนใน Settings ก็เคารพค่านั้นตลอด
        // ถ้ารุ่นใหม่ต่อไม่ติด ระบบจะ fallback ไปรุ่นถัดใน chain เองภายใน 6 วินาที แล้ว promote ตัวที่ใช้ได้จริง
        val liveUpgradeFlag = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("live_model_upgraded_2026_09_16").executeAsOneOrNull()
        }
        if (liveUpgradeFlag != "true") {
            if (savedLiveModel != defaultLiveModel) {
                logDebug("JarvisVM", "One-time live model upgrade: '$savedLiveModel' → '$defaultLiveModel'")
                savedLiveModel = defaultLiveModel
                withContext(Dispatchers.IO) {
                    database.jarvisDatabaseQueries.insertSetting("live_model_name", savedLiveModel)
                }
            }
            withContext(Dispatchers.IO) {
                database.jarvisDatabaseQueries.insertSetting("live_model_upgraded_2026_09_16", "true")
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
        _modelCaps.value = com.skyliner2008.jarvis.data.providers.ModelAutoTester.parseCaps(savedModelCaps)
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
            logDebug("JarvisVM", "Persist working fallback config: model=$workingModel key=${com.skyliner2008.jarvis.maskApiKey(workingKey)}")
            _selectedModel.value = workingModel
            _apiKey.value = workingKey
            scope.launch(Dispatchers.IO) {
                database.jarvisDatabaseQueries.insertSetting("model_name", workingModel)
                database.jarvisDatabaseQueries.insertSetting("api_key", workingKey)
            }
        }

        // Wire 404 self-healing: เมื่อโมเดลติด 404 (ไม่มีจริงใน API) ให้สั่ง refresh รายชื่อโมเดลทันที
        orchestrator.getGeminiService().onModelNotFound = { deadModel ->
            logDebug("JarvisVM", "Model '$deadModel' returned 404 NOT_FOUND. Triggering dynamic model list refresh.")
            refreshModels()
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

    /** อัปเดตและบันทึกโมเดล Live ที่ได้รับการ promote เมื่อเชื่อมต่อสำเร็จ โดยไม่กระทบ state อื่น */
    fun updateLiveModelSilently(newModel: String) {
        val clean = newModel.removePrefix("models/").trim()
        if (clean.isBlank() || _liveModelName.value == clean) return
        _liveModelName.value = clean
        scope.launch(Dispatchers.IO) {
            try {
                database.jarvisDatabaseQueries.insertSetting("live_model_name", clean)
                logDebug("Settings", "Persisted promoted live model to DB: $clean")
            } catch (e: Exception) {
                logError("Settings", "Failed to persist live model: ${e.message}", e)
            }
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
                    // อัปเดต ModelConfig แบบ dynamic เพื่อให้ระบบมีคลังโมเดลที่ใช้งานได้จริงจาก Google API เสมอ
                    com.skyliner2008.jarvis.data.ModelConfig.updateAvailableModels(models)

                    // แคชรายชื่อโมเดลลงฐานข้อมูล เพื่อให้บูตเครื่องครั้งถัดไปตรวจสอบได้ทันทีก่อนต่อเน็ต (Cold-start)
                    val modelNames = models.map { it.name.removePrefix("models/").trim() }.distinct()
                    withContext(Dispatchers.IO) {
                        database.jarvisDatabaseQueries.insertSetting("cached_gemini_models", modelNames.joinToString(","))
                    }

                    // Auto-validate & Migrate: ถ้าโมเดลที่เลือกไว้ในปัจจุบันไม่มีใน API (เช่น gemini-3.1-pro)
                    // ให้ย้ายไปโมเดล Flash ตัวใหม่ล่าสุดที่พร้อมใช้งานโดยอัตโนมัติ
                    val currentModel = _selectedModel.value
                    if (currentModel.isNotBlank() && !currentModel.contains("/") && !com.skyliner2008.jarvis.data.ModelConfig.isModelSupported(currentModel)) {
                        val replacement = com.skyliner2008.jarvis.data.ModelConfig.getBestActiveModel()
                        logDebug("JarvisVM", "Current model '$currentModel' not found in active API models. Auto-migrating to '$replacement'")
                        _selectedModel.value = replacement
                        withContext(Dispatchers.IO) {
                            database.jarvisDatabaseQueries.insertSetting("model_name", replacement)
                        }
                        orchestrator.updateConfig(_apiKey.value, replacement, _liveModelName.value, _voiceName.value)
                    }

                    // อัปเดต fallback chain ให้ใช้โมเดลจริงที่ active จาก API
                    val dynamicChain = com.skyliner2008.jarvis.data.ModelConfig.getFallbackChain(_selectedModel.value)
                    orchestrator.updateGeminiFallbackChain(dynamicChain)
                }
            } catch (e: Exception) {
                logError("JarvisVM", "Failed to fetch models", e)
            }
        }
    }

    /** สั่ง refresh รายชื่อโมเดลจาก API สด */
    fun refreshModels() {
        fetchModels()
    }

    suspend fun getModelsForProvider(providerId: String, freeOnly: Boolean = false, apiKeyOverride: String? = null): List<com.skyliner2008.jarvis.data.providers.LlmModelInfo> {
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
    suspend fun getLiveCapableModels(providerId: String = "gemini", apiKeyOverride: String? = null): List<com.skyliner2008.jarvis.data.providers.LlmModelInfo> {
        val all = getModelsForProvider(providerId, freeOnly = false, apiKeyOverride)
        val liveByCapability = all.filter { it.supportsLive }
        if (liveByCapability.isNotEmpty()) return liveByCapability
        // Fallback heuristic for providers that don't expose capability flags
        // (transcribe / live-translate เปิด WebSocket ได้แต่คุยเป็นผู้ช่วยไม่ได้ — ตัดออกที่นี่ด้วย)
        val fallback = all.filter { m ->
            com.skyliner2008.jarvis.data.ModelConfig.isConversationalLiveModel(m.id) ||
                com.skyliner2008.jarvis.data.ModelConfig.isConversationalLiveModel(m.displayName)
        }
        if (fallback.isNotEmpty()) return fallback
        // Default safe live models if nothing matched — อิงลิสต์กลางที่ ModelConfig ดูแล
        return com.skyliner2008.jarvis.data.ModelConfig.getLiveFallbackChain().map { id ->
            com.skyliner2008.jarvis.data.providers.LlmModelInfo(
                id = id,
                displayName = com.skyliner2008.jarvis.data.ModelConfig.displayNameFor(id),
                supportsLive = true,
                supportsVision = true
            )
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
    ): com.skyliner2008.jarvis.data.providers.ApiKeyTester.TestResult {
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
        val result = com.skyliner2008.jarvis.data.providers.ApiKeyTester.test(client, providerId, key)

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
            "groq" -> com.skyliner2008.jarvis.data.providers.GroqLlmProvider(client, key)
            else -> com.skyliner2008.jarvis.data.providers.OpenRouterLlmProvider(client, key)
        }
        val results = com.skyliner2008.jarvis.data.providers.ModelAutoTester.testAllModels(
            client, provider, key, freeOnly = (providerId == "openrouter"), onProgress = onProgress
        )
        val merged = _modelCaps.value.toMutableMap()
        results.forEach { (id, cap) -> merged["$providerId/$id"] = cap }
        _modelCaps.value = merged
        orchestrator.updateModelCaps(merged)
        withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting(
                "model_caps_v1",
                com.skyliner2008.jarvis.data.providers.ModelAutoTester.serializeCaps(merged)
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
        val config = com.skyliner2008.jarvis.ai.JarvisPersona.IdentityConfig(
            agentName = agentName,
            agentCreature = agentCreature,
            agentVibe = agentVibe,
            agentGender = agentGender,
            userName = userName,
            userCallName = userCallName,
            userNotes = userNotes
        )
        com.skyliner2008.jarvis.ai.JarvisPersona.updateIdentity(config)
        // persist ลง Core Memory — เดิมแก้เฉพาะ in-memory ทำให้เปิดแอปใหม่แล้ว identity กลับเป็นค่า default
        scope.launch(Dispatchers.IO) {
            com.skyliner2008.jarvis.ai.JarvisPersona.toCoreMemoryMap(config).forEach { (k, v) ->
                runCatching { memoryManager.setCoreMemory(k, v) }
            }
        }
    }
}

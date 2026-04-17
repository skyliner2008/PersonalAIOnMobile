package com.example.personalaibot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalaibot.ai.JarvisOrchestrator
import com.example.personalaibot.camera.CameraAnalysisService
import com.example.personalaibot.camera.CameraMode
import com.example.personalaibot.camera.CameraProviderType
import com.example.personalaibot.data.GeminiModel
import com.example.personalaibot.db.DatabaseDriverFactory
import com.example.personalaibot.db.createDatabase
import com.example.personalaibot.memory.JarvisMemoryManager
import com.example.personalaibot.voice.PcmAudioEngine
import com.example.personalaibot.voice.VoiceManager
import com.example.personalaibot.automation.AutomationManager
import com.example.personalaibot.tools.ToolExecutor
import com.example.personalaibot.tools.camera.CameraToolExecutor
import io.ktor.util.encodeBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Message(val role: String, val content: String, val isStatic: Boolean = false)

class JarvisViewModel(
    driverFactory: DatabaseDriverFactory,
    private val voiceManager: VoiceManager,
    private val fileHandler: (suspend (String, Map<String, String>) -> String)? = null
) : ViewModel() {

    private val database = createDatabase(driverFactory)
    private val memoryManager = JarvisMemoryManager(database)
    val automationManager = AutomationManager(database)
    private val client = createHttpClient()

    private val defaultMainModel = com.example.personalaibot.data.ModelConfig.DEFAULT_MAIN_MODEL
    private val defaultLiveModel = com.example.personalaibot.data.ModelConfig.DEFAULT_LIVE_MODEL

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

    private val _availableModels = MutableStateFlow<List<GeminiModel>>(emptyList())
    val availableModels: StateFlow<List<GeminiModel>> = _availableModels.asStateFlow()

    private val orchestrator = JarvisOrchestrator(
        client = client,
        memoryManager = memoryManager,
        apiKey = "",
        modelName = defaultMainModel,
        liveModelName = defaultLiveModel,
        automationManager = automationManager,
        fileHandler = fileHandler
    )

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private val _isSleeping = MutableStateFlow(false)
    val isSleeping: StateFlow<Boolean> = _isSleeping.asStateFlow()

    private val _floatingWidgetEnabled = MutableStateFlow(false)
    val floatingWidgetEnabled: StateFlow<Boolean> = _floatingWidgetEnabled.asStateFlow()

    private val maxContextTurns = 10
    
    // 鈹€鈹€鈹€ Camera Analysis System 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    val cameraService = CameraAnalysisService(client)

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    // 鈹€鈹€鈹€ Automation System 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    val activeJobs = automationManager.activeJobs

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _showCameraScreen = MutableStateFlow(false)
    val showCameraScreen: StateFlow<Boolean> = _showCameraScreen.asStateFlow()

    private var cameraFrameJob: Job? = null
    
    private val _isUserSpeaking = MutableStateFlow(false)
    val isUserSpeaking: StateFlow<Boolean> = _isUserSpeaking.asStateFlow()

    private val _isAiVisionRequested = MutableStateFlow(false)
    val isAiVisionRequested: StateFlow<Boolean> = _isAiVisionRequested.asStateFlow()

    // 鈹€鈹€鈹€ Charting System (V15.0) 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    private val _showChart = MutableStateFlow(false)
    val showChart: StateFlow<Boolean> = _showChart.asStateFlow()

    private val _chartSymbol = MutableStateFlow("XAUUSD")
    val chartSymbol: StateFlow<String> = _chartSymbol.asStateFlow()

    private val _chartCandles = MutableStateFlow<List<com.example.personalaibot.tools.trading.Candle>>(emptyList())
    val chartCandles: StateFlow<List<com.example.personalaibot.tools.trading.Candle>> = _chartCandles.asStateFlow()

    private val _chartSmcResult = MutableStateFlow<com.example.personalaibot.tools.trading.SmcAnalysisResult?>(null)
    val chartSmcResult: StateFlow<com.example.personalaibot.tools.trading.SmcAnalysisResult?> = _chartSmcResult.asStateFlow()
    
    private val _chartInterval = MutableStateFlow("1h")
    val chartInterval: StateFlow<String> = _chartInterval.asStateFlow()

    private val _chartLocale = MutableStateFlow("th_TH")
    val chartLocale: StateFlow<String> = _chartLocale.asStateFlow()

    private val _chartHideSideToolbar = MutableStateFlow(false)
    val chartHideSideToolbar: StateFlow<Boolean> = _chartHideSideToolbar.asStateFlow()
    
    private val _chartRefreshToken = MutableStateFlow(0L)
    val chartRefreshToken: StateFlow<Long> = _chartRefreshToken.asStateFlow()

    private var visionTimeoutJob: Job? = null
    
    /** 喙€喔佮箛喔氞竸喔赤釜喔编箞喔囙釜喔膏笖喔椸箟喔侧涪喔傕腑喔囙笢喔灌箟喙冟笂喙夃笚喔掂箞喔⑧副喔囙笚喔赤箘喔∴箞喙€喔福喙囙笀喔曕腑喔權箑喔涏弗喔掂箞喔⑧笝喙€喔傅喔⑧竾 喙€喔炧阜喙堗腑喔權赋喙勦笡喔副喙堗竾 AI 喔曕箞喔笚喔编笝喔椸傅喔椸傅喙堗箑喔娻阜喙堗腑喔∴笗喙堗腑喙冟斧喔∴箞 */
    private var pendingCommandAfterVoiceChange: String? = null

    private val pcmAudioEngine = PcmAudioEngine()

    private val speechThreshold = 0.05f // Volume threshold for "Speaking" state

    init {
        // Bridge camera frames to the unified Live session in the orchestrator
        cameraService.onLiveFrameReady = { jpegBase64 ->
            orchestrator.sendLiveCameraFrame(jpegBase64)
        }

        // Initialize Camera Tool Executor
        ToolExecutor.initCameraExecutor(CameraToolExecutor(cameraService))

        // Bridge speech state to camera service for Adaptive Vision (Token Saving)
        pcmAudioEngine.onVolumeChanged = { volume ->
            val speaking = volume > speechThreshold
            if (speaking != _isUserSpeaking.value) {
                _isUserSpeaking.value = speaking
                cameraService.isUserSpeaking = speaking
            }
        }

        // Bridge AI vision request to camera service with safety timeout
        orchestrator.setAiVisionToggle { active ->
            if (active) {
                _isAiVisionRequested.value = true
                cameraService.isAiVisionRequested = true
                
                // Auto-stop AI-Vision flag after 60s safety timeout
                visionTimeoutJob?.cancel()
                visionTimeoutJob = viewModelScope.launch {
                    delay(60000)
                    _isAiVisionRequested.value = false
                    cameraService.isAiVisionRequested = false
                    // Only stop service if manual preview is also off
                    if (!_isCameraActive.value) {
                        cameraService.stop()
                    }
                }
                cameraService.start()
            } else {
                visionTimeoutJob?.cancel()
                _isAiVisionRequested.value = false
                cameraService.isAiVisionRequested = false
                // Only stop service if manual preview is also off
                if (!_isCameraActive.value) {
                    cameraService.stop()
                }
            }
        }

        // --- AI-Controlled Voice Change ---
        orchestrator.setVoiceChangeHandler { newVoice ->
            viewModelScope.launch {
                // 喙€喔佮箛喔氞竸喔赤笘喔侧浮喔ム箞喔侧釜喔膏笖喔傕腑喔囙笢喔灌箟喙冟笂喙夃箘喔о箟 (喔栢箟喔侧浮喔? 喙€喔炧阜喙堗腑喔權赋喙勦笡喔涏箟喔笝喙冟斧喙?session 喙冟斧喔∴箞
                val lastMsg = _messages.value.lastOrNull { it.role == "user" }?.content
                pendingCommandAfterVoiceChange = lastMsg
                
                logDebug("JarvisVM", "馃攧 Voice change requested: $newVoice. Task cached: $lastMsg")
                
                _voiceName.value = newVoice
                updateSettings(_apiKey.value, _selectedModel.value, _liveModelName.value, newVoice)
                
                // Immediate Apply: Restart session if active
                if (_isListening.value) {
                    stopVoiceInput()
                    delay(800) // 喙€喔炧复喙堗浮 delay 喙€喔ム箛喔佮笝喙夃腑喔⑧箑喔炧阜喙堗腑喙冟斧喙夃福喔班笟喔氞箑喔勦弗喔掂涪喔｀箤 resources 喙佮弗喔班笟喔编笝喔椸付喔佮竸喔о覆喔∴笀喔赤箘喔斷箟喔椸副喔?
                    startVoiceInput()
                }
            }
        }

        viewModelScope.launch {
            loadSettings()
        }

        viewModelScope.launch {
            isAiVisionRequested.collect { requested ->
                cameraService.isAiVisionRequested = requested
            }
        }

        // 鈹€鈹€鈹€ Charting Sync (V15.0) 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
        viewModelScope.launch {
            com.example.personalaibot.tools.trading.ChartStateManager.currentSymbol.collect { 
                _chartSymbol.value = it 
            }
        }
        viewModelScope.launch {
            com.example.personalaibot.tools.trading.ChartStateManager.currentCandles.collect { 
                _chartCandles.value = it 
            }
        }
        viewModelScope.launch {
            com.example.personalaibot.tools.trading.ChartStateManager.currentSmcResult.collect { 
                _chartSmcResult.value = it 
            }
        }
    }

    private val defaultVoiceName = "Aoede" // Female (Soothing)
    
    private val _voiceName = MutableStateFlow(defaultVoiceName)
    val voiceName: StateFlow<String> = _voiceName.asStateFlow()

    private suspend fun loadSettings() {
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

        _apiKey.value = savedKey
        _selectedModel.value = savedModel
        _liveModelName.value = savedLiveModel
        _voiceName.value = savedVoiceName
        _floatingWidgetEnabled.value = savedWidgetEnabled
        _openaiApiKey.value = savedOpenaiKey
        _claudeApiKey.value = savedClaudeKey
        orchestrator.updateConfig(savedKey, savedModel, savedLiveModel, savedVoiceName)

        // Initialize camera service keys
        cameraService.updateProviderKeys(
            geminiApiKey = savedKey,
            openaiApiKey = savedOpenaiKey,
            claudeApiKey = savedClaudeKey
        )

        if (savedKey.isNotBlank()) {
            fetchModels()
            
            // Auto-backfill existing facts with embeddings for Semantic Search
            viewModelScope.launch {
                val count = memoryManager.backfillEmbeddings(orchestrator.getGeminiService())
                if (count > 0) {
                    logDebug("JarvisVM", "鉁?Semantic backfill complete: Indexed $count facts")
                }
            }
        }
        loadHistory()
    }

    fun updateSettings(key: String, model: String, liveModel: String = _liveModelName.value, voice: String = _voiceName.value) {
        viewModelScope.launch {
            _apiKey.value = key
            _selectedModel.value = model
            _liveModelName.value = liveModel
            _voiceName.value = voice
            withContext(Dispatchers.IO) {
                database.jarvisDatabaseQueries.insertSetting("api_key", key)
                database.jarvisDatabaseQueries.insertSetting("model_name", model)
                database.jarvisDatabaseQueries.insertSetting("live_model_name", liveModel)
                database.jarvisDatabaseQueries.insertSetting("voice_name", voice)
            }
            orchestrator.updateConfig(key, model, liveModel, voice)
            fetchModels()
        }
    }

    fun setFloatingWidgetEnabled(enabled: Boolean) {
        _floatingWidgetEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("floating_widget_enabled", enabled.toString())
        }
    }

    private fun fetchModels() {
        viewModelScope.launch {
            try {
                val models = orchestrator.listAvailableModels()
                if (models.isNotEmpty()) {
                    _availableModels.value = models
                }
            } catch (e: Exception) {
                logError("JarvisVM", "Failed to fetch models", e)
                // You might want to show an error to the user here
            }
        }
    }

    private suspend fun loadHistory() {
        val history = memoryManager.getRecentHistory(20).reversed()
        _messages.value = history.map { Message(it.role, it.content) }
    }

    fun sendMessage(text: String, speakResponse: Boolean = false) {
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                _messages.value = _messages.value + Message("user", text)
                memoryManager.storeMessage("user", text)
                _isTyping.value = true
                val historySnapshot = buildHistorySnapshot()
                val coreContext = memoryManager.buildCoreMemoryContext()
                _messages.value = _messages.value + Message("model", "")
                var currentAiMessage = ""
                val responseFlow = orchestrator.chatWithHistory(text, historySnapshot, coreContext)

                responseFlow.collect { chunk ->
                    // Log tool execution indicators
                    if (chunk.startsWith("鈴?喔佮赋喔ム副喔囙笖喔多竾喔傕箟喔浮喔灌弗")) {
                        logDebug("JarvisVM", "[Chat] Tool call detected: $chunk")
                    }
                    currentAiMessage += chunk
                    val currentList = _messages.value.toMutableList()
                    if (currentList.isNotEmpty()) {
                        currentList[currentList.size - 1] = Message("model", currentAiMessage)
                        _messages.value = currentList
                    }
                }
                logDebug("JarvisVM", "[Chat] Response complete (${currentAiMessage.length} chars)")

                if (currentAiMessage.isNotBlank()) {
                    memoryManager.storeMessage("model", currentAiMessage)
                    memoryManager.updateKnowledgeGraph("User: $text\nJARVIS: $currentAiMessage")
                    memoryManager.extractAndUpdateCoreMemory(text, currentAiMessage)
                }
                _isTyping.value = false
                if (speakResponse && currentAiMessage.isNotBlank() && voiceManager.isAvailable()) {
                    voiceManager.speak(currentAiMessage, null)
                }
            } catch (e: Exception) {
                logError("JarvisVM", "Error in sendMessage", e)
                _isTyping.value = false
                _messages.value = _messages.value + Message("model", "鈿狅笍 喔傕腑喔笭喔编涪 喙€喔佮复喔斷競喙夃腑喔溹复喔斷笧喔ム覆喔斷笚喔侧竾喙€喔椸竸喔權复喔? ${e.message}")
            }
        }
    }


    // 鈹€鈹€鈹€ External API Keys (OpenAI, Claude) 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    private val _openaiApiKey = MutableStateFlow("")
    val openaiApiKey: StateFlow<String> = _openaiApiKey.asStateFlow()

    private val _claudeApiKey = MutableStateFlow("")
    val claudeApiKey: StateFlow<String> = _claudeApiKey.asStateFlow()

    fun updateExternalApiKeys(openai: String, claude: String) {
        viewModelScope.launch {
            _openaiApiKey.value = openai
            _claudeApiKey.value = claude
            withContext(Dispatchers.IO) {
                database.jarvisDatabaseQueries.insertSetting("openai_api_key", openai)
                database.jarvisDatabaseQueries.insertSetting("claude_api_key", claude)
            }
            // Update camera service with new keys
            cameraService.updateProviderKeys(
                geminiApiKey = _apiKey.value,
                openaiApiKey = openai,
                claudeApiKey = claude
            )
        }
    }

    fun toggleCamera() {
        val newState = !_isCameraActive.value
        _isCameraActive.value = newState
        if (newState) {
            startCameraAnalysis()
        } else {
            // Only stop if AI is not also using it
            if (!_isAiVisionRequested.value) {
                stopCameraAnalysis()
            }
        }
    }

    fun switchCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
        logDebug("JARVIS_VM", "Switching camera (Front: ${_isFrontCamera.value})")
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        logDebug("JARVIS_VM", "Microphone muted: ${_isMuted.value}")
    }

    fun openCameraScreen() {
        _showCameraScreen.value = true
        // Initialize camera service with current API keys
        viewModelScope.launch {
            cameraService.updateProviderKeys(
                geminiApiKey = _apiKey.value,
                openaiApiKey = _openaiApiKey.value,
                claudeApiKey = _claudeApiKey.value
            )
        }
    }

    fun closeCameraScreen() {
        _showCameraScreen.value = false
        stopCameraAnalysis()
    }

    fun switchCameraProvider(provider: CameraProviderType) {
        viewModelScope.launch {
            cameraService.switchProvider(provider)
        }
    }

    fun switchCameraMode(mode: CameraMode) {
        viewModelScope.launch {
            cameraService.switchMode(mode)
        }
    }

    fun startCameraAnalysis() {
        cameraService.start()
    }

    fun stopCameraAnalysis() {
        cameraService.stop()
        cameraFrameJob?.cancel()
        cameraFrameJob = null
    }

    /**
     * 喙€喔｀傅喔⑧竵喔堗覆喔?platform-specific camera callback 喙€喔∴阜喙堗腑喙勦笖喙夃箑喔熰福喔∴箖喔浮喙?
     * @param jpegBase64 喔犩覆喔?JPEG 喙冟笝喔｀腹喔?base64
     * @param rawBytes raw JPEG bytes 喔赋喔福喔编笟 motion detection
     */
    fun onCameraFrame(jpegBase64: String, rawBytes: ByteArray? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            cameraService.onCameraFrame(jpegBase64, rawBytes)
        }
    }

    /**
     * 喔栢箞喔侧涪喔犩覆喔?Snapshot 喙佮弗喙夃抚喔о复喙€喔勦福喔侧赴喔箤喔椸副喔權笚喔?
     */
    fun captureAndAnalyze(jpegBase64: String) {
        viewModelScope.launch(Dispatchers.IO) {
            cameraService.captureAndAnalyze(jpegBase64)
        }
    }

    // 鈹€鈹€鈹€ Live Voice (Gemini Multimodal Live) 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€


    /** 喔娻阜喙堗腑 tool 喔椸傅喙堗竵喔赤弗喔编竾 execute 喔涪喔灌箞 鈥?expose 喙勦笡喔⑧副喔?UI */
    val activeToolName: StateFlow<String?> = orchestrator.activeToolName

    private var liveSessionJob: kotlinx.coroutines.Job? = null

    fun startVoiceInput() {
        if (_isListening.value) return
        _isListening.value = true
        _voiceError.value = null
        _isMuted.value = false // Start unmuted
        logDebug("JARVIS_VM", "Starting Live Voice Input")

        liveSessionJob?.cancel()
        liveSessionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 喔斷付喔?core memory context 喙€喔炧阜喙堗腑喙冟斧喙?tool bridge 喙冟笂喙?
                val coreContext = memoryManager.buildCoreMemoryContext()
                
                // 2. 喔斷付喔囙笡喔｀赴喔о副喔曕复喔佮覆喔｀釜喔權笚喔權覆喔副喙夃笝喙?喙€喔炧阜喙堗腑喔箞喔囙箖喔箟 Live Session 喔｀腹喙夃笟喔｀复喔氞笚
                val historySnapshot = messages.value.takeLast(10).joinToString("\n") { 
                    "${if (it.role == "user") "User" else "JARVIS"}: ${it.content}" 
                }

                    // 3. 喙€喔涏复喔?Live session 喔炧福喙夃腑喔?tool bridge (Path A + Path B auto-detected)
                launch {
                    logDebug("JARVIS_VM", "Connecting Live session (with memory context)...")
                    
                    // 喔覆喔佮浮喔掂竾喔侧笝喔勦箟喔侧竾喔堗覆喔佮竵喔侧福喙€喔涏弗喔掂箞喔⑧笝喙€喔傅喔⑧竾 喙冟斧喙夃笁喔掂笖 Prompt 喙€喔傕箟喔侧箘喔涏笟喔竵 AI 喙冟笝喔氞福喔｀笚喔编笖喙佮福喔佮競喔竾喔涏福喔班抚喔编笗喔?
                    val contextToSubmit = if (!pendingCommandAfterVoiceChange.isNullOrBlank()) {
                        val task = pendingCommandAfterVoiceChange
                        pendingCommandAfterVoiceChange = null // Clear 喔椸副喔權笚喔掂箑喔炧阜喙堗腑喔涏箟喔竾喔佮副喔權竵喔侧福 loop
                        "SYSTEM_INFO: 喔勦父喔撪箑喔炧复喙堗竾喙€喔涏弗喔掂箞喔⑧笝喙€喔傅喔⑧竾喔赋喙€喔｀箛喔?喔囙覆喔權笗喙堗腑喙勦笡喔椸傅喙堗竸喔膏笓喔曕箟喔竾喔椸赋喔椸副喔權笚喔掂竸喔粪腑: $task. 喙傕笡喔｀笖喔斷赋喙€喔權复喔權竵喔侧福喔曕箞喔箒喔ム赴喙佮笀喙夃竾喔溹腹喙夃箖喔娻箟喔斷箟喔о涪喙€喔傅喔⑧竾喙冟斧喔∴箞喔傕腑喔囙竸喔膏笓."
                    } else {
                        historySnapshot
                    }
                    
                    orchestrator.startLiveVoiceSessionWithMemory(coreContext, contextToSubmit)
                }

                // 4. Collect audio output 鈫?speaker
                launch {
                    orchestrator.audioOutputFlow.collect { pcmBytes ->
                        pcmAudioEngine.playAudio(pcmBytes)
                    }
                }

                // 5. Collect text output 鈫?Chat UI
                launch {
                    orchestrator.textOutputFlow.collect { update ->
                        withContext(Dispatchers.Main) {
                            val msgList = _messages.value.toMutableList()
                            
                            if (update.replace && msgList.isNotEmpty() && 
                                msgList.last().role == update.role && !msgList.last().isStatic) {
                                // Replacement mode: update the last message content ONLY IF it's not static
                                val last = msgList.last()
                                msgList[msgList.size - 1] = last.copy(content = update.text, isStatic = update.isStatic)
                            } else if (update.append && msgList.isNotEmpty() && 
                                       msgList.last().role == update.role && !msgList.last().isStatic) {
                                // Append mode: add to last message content ONLY IF it's not static
                                val last = msgList.last()
                                msgList[msgList.size - 1] = last.copy(content = last.content + update.text, isStatic = update.isStatic)
                            } else {
                                // New message box (for new role, or if last box was static/report)
                                msgList.add(Message(update.role, update.text, isStatic = update.isStatic))
                            }
                            _messages.value = msgList
                        }
                    }
                }

                // 6. Start mic recording 鈫?stream to Live model
                logDebug("JARVIS_VM", "Microphone starting...")
                var frameCount = 0
                pcmAudioEngine.startRecording { bytes ->
                    if (!_isMuted.value) {
                        frameCount++
                        // SILENCED: Heavy log
                        // if (frameCount % 50 == 0) {
                        //    logDebug("JARVIS_VM", "馃帣锔?Transmitting audio chunk #$frameCount (${bytes.size} bytes)")
                        // }
                        val base64 = bytes.encodeBase64()
                        viewModelScope.launch(Dispatchers.IO) {
                            orchestrator.sendLiveAudioChunk(base64)
                        }
                    }
                }

            } catch (e: Exception) {
                logError("JARVIS_VM", "Live Voice Error", e)
                _isListening.value = false
                _voiceError.value = "Live mode error: ${e.message}"
            }
        }
    }

    fun stopVoiceInput() {
        logDebug("JARVIS_VM", "Stopping Live Voice Input")
        _isListening.value = false
        _isMuted.value = false
        pcmAudioEngine.stopRecording()
        liveSessionJob?.cancel()
        liveSessionJob = null
        viewModelScope.launch(Dispatchers.IO) {
            orchestrator.endLiveVoiceSession()
        }
    }

    fun clearVoiceError() {
        _voiceError.value = null
    }

    fun clearChat() {
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.deleteAllMessages()
            withContext(Dispatchers.Main) {
                _messages.value = emptyList()
            }
        }
    }

    // 鈹€鈹€鈹€ Charting Actions 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    
    fun openChart(symbol: String, candles: List<com.example.personalaibot.tools.trading.Candle>, smc: com.example.personalaibot.tools.trading.SmcAnalysisResult? = null) {
        _chartSymbol.value = symbol
        _chartCandles.value = candles
        _chartSmcResult.value = smc
        _showChart.value = true
        _chartRefreshToken.value = _chartRefreshToken.value + 1
    }

    fun openChart() {
        _showChart.value = true
        _chartRefreshToken.value = _chartRefreshToken.value + 1
    }

    fun updateChartSymbol(symbol: String) {
        val normalized = normalizeChartSymbol(symbol)
        if (normalized.isBlank()) return
        _chartSymbol.value = normalized
        _chartRefreshToken.value = _chartRefreshToken.value + 1
    }

    fun updateChartInterval(interval: String) {
        val normalized = normalizeChartInterval(interval)
        if (_chartInterval.value == normalized) return
        _chartInterval.value = normalized
        _chartRefreshToken.value = _chartRefreshToken.value + 1
    }

    fun refreshChart() {
        _chartRefreshToken.value = _chartRefreshToken.value + 1
    }

    fun updateChartLocale(locale: String) {
        _chartLocale.value = if (locale.lowercase().startsWith("th")) "th_TH" else "en"
        _chartRefreshToken.value = _chartRefreshToken.value + 1
    }

    fun setChartHideSideToolbar(hidden: Boolean) {
        _chartHideSideToolbar.value = hidden
        _chartRefreshToken.value = _chartRefreshToken.value + 1
    }

    fun closeChart() {
        _showChart.value = false
    }

    fun handleChartCapture(base64Image: String) {
        logDebug("JarvisVM", "Chart captured! Processing with Gemini Vision...")
        // Phase 4 logic will go here: submit to orchestrator for visual analysis
        sendMessage("喔о复喙€喔勦福喔侧赴喔箤喔犩覆喔炧竵喔｀覆喔熰笝喔掂箟喙冟斧喙夃斧喔權箞喔涪喔勦福喔编笟 (Capture 喔堗覆喔佮福喔班笟喔氞笀喔侧福喙屶抚喔脆釜)")
        viewModelScope.launch {
            cameraService.onLiveFrameReady?.invoke(base64Image) // Wrap in launch for suspend call
        }
    }

    private fun normalizeChartInterval(interval: String): String {
        val normalized = interval.trim().lowercase()
        return when (normalized) {
            "1m", "5m", "15m", "30m", "1h", "4h", "1d" -> normalized
            "d" -> "1d"
            else -> "1h"
        }
    }

    private fun normalizeChartSymbol(symbol: String): String {
        return symbol
            .trim()
            .uppercase()
            .replace(" ", "")
    }

    fun triggerSleepCycle() {
        if (_isSleeping.value) return
        _isSleeping.value = true
        viewModelScope.launch {
            val success = orchestrator.performSleepCycle()
            if (success) {
                // 喙傕斧喔ム笖喔涏福喔班抚喔编笗喔脆箖喔浮喙?喙€喔炧福喔侧赴喔傕箟喔竸喔о覆喔∴箑喔佮箞喔侧笘喔灌竵喔ム笟喙勦笡喔｀抚喔∴涪喔笖喙佮弗喙夃抚
                loadHistory()
            }
            _isSleeping.value = false
        }
    }

    private fun buildHistorySnapshot(): List<Pair<String, String>> {
        val current = _messages.value
        val withoutLatest = if (current.isNotEmpty()) current.dropLast(1) else current
        val maxMessages = maxContextTurns * 2
        return withoutLatest
            .takeLast(maxMessages)
            .map { Pair(it.role, it.content) }
            .filter { it.second.isNotBlank() }
    }

    override fun onCleared() {
        super.onCleared()
        stopCameraAnalysis()
        cameraService.release()
        pcmAudioEngine.release()
        voiceManager.shutdown()
        client.close()
    }
}

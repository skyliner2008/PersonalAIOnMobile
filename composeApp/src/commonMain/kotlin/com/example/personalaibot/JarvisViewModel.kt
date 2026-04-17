package com.example.personalaibot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalaibot.ai.JarvisOrchestrator
import com.example.personalaibot.camera.CameraAnalysisService
import com.example.personalaibot.camera.CameraMode
import com.example.personalaibot.camera.CameraProviderType
import com.example.personalaibot.data.GeminiModel
import com.example.personalaibot.db.DatabaseDriverFactory
import com.example.personalaibot.db.JarvisDatabaseHolder
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
    
    // ─── Camera Analysis System ──────────────────────────────────────────────────
    val cameraService = CameraAnalysisService(client)

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    // ─── Automation System ───────────────────────────────────────────────────────
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

    // ─── Charting System (V15.0) ─────────────────────────────────────────────────
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
    
    /** เก็บข้อความสั่งสุดท้ายของผู้ใช้ที่ยังทำไม่เสร็จตอนเปลี่ยนเสียง เพื่อนำไปสั่ง AI ต่อทันทีที่เชื่อมต่อใหม่ */
    private var pendingCommandAfterVoiceChange: String? = null

    private val pcmAudioEngine = PcmAudioEngine()

    private val speechThreshold = 0.05f // Volume threshold for "Speaking" state

    init {
        JarvisDatabaseHolder.install(database)

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
                // เก็บคำถามล่าสุดของผู้ใช้ไว้ (ถ้ามี) เพื่อนำไปป้อนให้ session ใหม่
                val lastMsg = _messages.value.lastOrNull { it.role == "user" }?.content
                pendingCommandAfterVoiceChange = lastMsg
                
                logDebug("JarvisVM", "🔔 Voice change requested: $newVoice. Task cached: $lastMsg")
                
                _voiceName.value = newVoice
                updateSettings(_apiKey.value, _selectedModel.value, _liveModelName.value, newVoice)
                
                // Immediate Apply: Restart session if active
                if (_isListening.value) {
                    stopVoiceInput()
                    delay(800) // เพิ่ม delay เล็กน้อยเพื่อให้ระบบเคลียร์ resources และบันทึกความจำได้ทัน
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

        // ─── Charting Sync (V15.0) ───────────────────────────────────────────────────
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
                    logDebug("JarvisVM", "✅ Semantic backfill complete: Indexed $count facts")
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
                    // Hide tool-request/progress traces from chat; keep only user-facing content.
                    val isToolRequestNoise =
                        chunk.contains("กำลังดึงข้อมูล") ||
                        chunk.contains("[TOOL_REQUEST]") ||
                        chunk.contains("Tool call detected") ||
                        chunk.contains("🔔")
                    if (isToolRequestNoise) {
                        logDebug("JarvisVM", "[Chat] Suppressed tool-request chunk: $chunk")
                        return@collect
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
                _messages.value = _messages.value + Message("model", "⚠️ ขออภัย เกิดข้อผิดพลาดทางเทคนิค: ${e.message}")
            }
        }
    }


    // ─── External API Keys (OpenAI, Claude) ──────────────────────────────────────
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
     * เรียกจาก platform-specific camera callback เมื่อได้รับเฟรมใหม่
     * @param jpegBase64 ภาพ JPEG ในรูป base64
     * @param rawBytes raw JPEG bytes สำหรับ motion detection
     */
    fun onCameraFrame(jpegBase64: String, rawBytes: ByteArray? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            cameraService.onCameraFrame(jpegBase64, rawBytes)
        }
    }

    /**
     * ถ่ายภาพ Snapshot แล้ววิเคราะห์ทันที
     */
    fun captureAndAnalyze(jpegBase64: String) {
        viewModelScope.launch(Dispatchers.IO) {
            cameraService.captureAndAnalyze(jpegBase64)
        }
    }

    // ─── Live Voice (Gemini Multimodal Live) ───────────────────────────


    /** ชื่อ tool ที่กำลัง execute อยู่ — expose ไปยัง UI */
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
                // 1. Build core memory context for live session/tool bridge
                val coreContext = memoryManager.buildCoreMemoryContext()
                
                // 2. ดึงประวัติการสนทนาสั้นๆ เพื่อส่งให้ Live Session รู้บริบท
                val historySnapshot = messages.value.takeLast(10).joinToString("\n") { 
                    "${if (it.role == "user") "User" else "JARVIS"}: ${it.content}" 
                }

                    // 3. เปิด Live session พร้อม tool bridge (Path A + Path B auto-detected)
                launch {
                    logDebug("JARVIS_VM", "Connecting Live session (with memory context)...")
                    
                    // If we just changed voice, send cached user task as startup context.
                    val contextToSubmit = if (!pendingCommandAfterVoiceChange.isNullOrBlank()) {
                        val task = pendingCommandAfterVoiceChange
                        pendingCommandAfterVoiceChange = null // Clear to avoid loop
                        "SYSTEM_INFO: คุณแจ้งเปลี่ยนเสียงสำเร็จแล้ว งานต่อไปที่คุณต้องทำทันทีคือ: $task. โปรดดำเนินการต่อและแจ้งผู้ใช้ด้วยเสียงใหม่ของคุณ."
                    } else {
                        historySnapshot
                    }
                    
                    orchestrator.startLiveVoiceSessionWithMemory(coreContext, contextToSubmit)
                }

                // 4. Collect audio output -> speaker
                launch {
                    orchestrator.audioOutputFlow.collect { pcmBytes ->
                        pcmAudioEngine.playAudio(pcmBytes)
                    }
                }

                // 5. Collect text output -> Chat UI
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

                // 6. Start mic recording -> stream to Live model
                logDebug("JARVIS_VM", "Microphone starting...")
                var frameCount = 0
                pcmAudioEngine.startRecording { bytes ->
                    if (!_isMuted.value) {
                        frameCount++
                        // SILENCED: Heavy log
                        // if (frameCount % 50 == 0) {
                        //    logDebug("JARVIS_VM", "🎤 Transmitting audio chunk #$frameCount (${bytes.size} bytes)")
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

    // ─── Charting Actions ─────────────────────────────────────────────────────────────
    
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
        sendMessage("วิเคราะห์ภาพกราฟนี้ให้หน่อยครับ (Capture จากระบบจาร์วิส)")
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
                // โหลดประวัติใหม่เพราะข้อความเก่าถูกลบไปรวมยอดแล้ว
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

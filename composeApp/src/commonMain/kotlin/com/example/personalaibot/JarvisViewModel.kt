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

data class Message(val role: String, val content: String)

class JarvisViewModel(
    driverFactory: DatabaseDriverFactory,
    private val voiceManager: VoiceManager,
    private val fileHandler: (suspend (String, Map<String, String>) -> String)? = null
) : ViewModel() {

    private val database = createDatabase(driverFactory)
    private val memoryManager = JarvisMemoryManager(database)
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
    
    // ─── Camera Analysis System ──────────────────────────────────────────────
    val cameraService = CameraAnalysisService(client)

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _showCameraScreen = MutableStateFlow(false)
    val showCameraScreen: StateFlow<Boolean> = _showCameraScreen.asStateFlow()

    private var cameraFrameJob: Job? = null
    
    private val _isUserSpeaking = MutableStateFlow(false)
    val isUserSpeaking: StateFlow<Boolean> = _isUserSpeaking.asStateFlow()

    private val _isAiVisionRequested = MutableStateFlow(false)
    val isAiVisionRequested: StateFlow<Boolean> = _isAiVisionRequested.asStateFlow()

    private var visionTimeoutJob: Job? = null
    
    /** เก็บคำสั่งสุดท้ายของผู้ใช้ที่ยังทำไม่เสร็จตอนเปลี่ยนเสียง เพื่อนำไปสั่ง AI ต่อทันทีที่เชื่อมต่อใหม่ */
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
                // เก็บคำถามล่าสุดของผู้ใช้ไว้ (ถ้ามี) เพื่อนำไปป้อนให้ session ใหม่
                val lastMsg = _messages.value.lastOrNull { it.role == "user" }?.content
                pendingCommandAfterVoiceChange = lastMsg
                
                logDebug("JarvisVM", "🔄 Voice change requested: $newVoice. Task cached: $lastMsg")
                
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
                if (chunk.startsWith("⏳ กำลังดึงข้อมูล")) {
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

            // ── Intercept tool result flags ──
            currentAiMessage = interceptToolResults(currentAiMessage)

            if (currentAiMessage.isNotBlank()) {
                memoryManager.storeMessage("model", currentAiMessage)
                memoryManager.updateKnowledgeGraph("User: $text\nJARVIS: $currentAiMessage")
                memoryManager.extractAndUpdateCoreMemory(text, currentAiMessage)
            }
            _isTyping.value = false
            if (speakResponse && currentAiMessage.isNotBlank() && voiceManager.isAvailable()) {
                voiceManager.speak(currentAiMessage, null)
            }
        }
    }

    /**
     * ตรวจจับ flag patterns จาก ToolExecutor และดำเนินการตาม
     * - REMEMBER_FACT::key=...::value=... → บันทึกลง core memory
     * - SET_REMINDER::title=...          → บันทึกเป็น archival memory (importance สูง)
     * - WEB_SEARCH_REQUEST::query=...    → ส่งค้นหาผ่าน Gemini (grounding)
     * - TRANSLATE_REQUEST::text=...      → ส่งแปลผ่าน LLM
     * - SUMMARIZE_REQUEST::text=...      → ส่งสรุปผ่าน LLM
     */
    private suspend fun interceptToolResults(response: String): String {
        var result = response

        // ── REMEMBER_FACT ──
        val rememberRegex = Regex("REMEMBER_FACT::key=(.+?)::value=(.+?)::importance=(.+?)(?:\\s|$)")
        rememberRegex.findAll(response).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            val importance = match.groupValues[3]
            memoryManager.setCoreMemory(key, value)
            val imp = when (importance.lowercase()) {
                "high" -> 0.9f; "low" -> 0.3f; else -> 0.6f
            }
            memoryManager.archiveFact(value, "model", imp)
            logDebug("ToolIntercept", "Remembered: $key = $value (importance=$importance)")
        }

        // ── SET_REMINDER ──
        val reminderRegex = Regex("SET_REMINDER::title=(.+?)::detail=(.+?)::when=(.+?)::ts=(\\d+)")
        reminderRegex.findAll(response).forEach { match ->
            val title = match.groupValues[1]
            val detail = match.groupValues[2]
            val whenStr = match.groupValues[3]
            val reminderContent = "📌 Reminder: $title — $detail (เมื่อ: $whenStr)"
            memoryManager.archiveFact(reminderContent, "system", 0.95f)
            logDebug("ToolIntercept", "Reminder set: $title")
        }

        // ── WEB_SEARCH_REQUEST → ส่งให้ Gemini ค้นหาจริง ──
        val searchRegex = Regex("WEB_SEARCH_REQUEST::query=(.+?)(?:\\s|$)")
        searchRegex.findAll(response).forEach { match ->
            val query = match.groupValues[1].trim()
            try {
                val searchResult = orchestrator.searchWithGrounding(query)
                result = result.replace(match.value, "[ผลการค้นหา] $searchResult")
            } catch (e: Exception) {
                logError("ToolIntercept", "Web search failed", e)
            }
        }

        // ── TRANSLATE_REQUEST → ส่งแปลผ่าน LLM ──
        val translateRegex = Regex("TRANSLATE_REQUEST::text=(.+?)::to=(.+?)(?:\\s|$)")
        translateRegex.findAll(response).forEach { match ->
            val text = match.groupValues[1].trim()
            val targetLang = match.groupValues[2].trim()
            try {
                val translated = orchestrator.translateText(text, targetLang)
                result = result.replace(match.value, translated)
            } catch (e: Exception) {
                logError("ToolIntercept", "Translation failed", e)
            }
        }

        // ── SUMMARIZE_REQUEST → ส่งสรุปผ่าน LLM ──
        val summarizeRegex = Regex("SUMMARIZE_REQUEST::length=(.+?)::text=(.+?)(?:\\s|$)")
        summarizeRegex.findAll(response).forEach { match ->
            val length = match.groupValues[1].trim()
            val text = match.groupValues[2].trim()
            try {
                val summary = orchestrator.summarizeText(text, length)
                result = result.replace(match.value, summary)
            } catch (e: Exception) {
                logError("ToolIntercept", "Summarization failed", e)
            }
        }

        return result
    }

    // ─── External API Keys (OpenAI, Claude) ────────────────────────────────
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
     * เรียกจาก platform-specific camera callback เมื่อได้เฟรมใหม่
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

    // ─── Live Voice (Gemini Multimodal Live) ─────────────────────────────────


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
                // 1. ดึง core memory context เพื่อให้ tool bridge ใช้
                val coreContext = memoryManager.buildCoreMemoryContext()
                
                // 2. ดึงประวัติการสนทนาสั้นๆ เพื่อส่งให้ Live Session รู้บริบท
                val historySnapshot = messages.value.takeLast(10).joinToString("\n") { 
                    "${if (it.role == "user") "User" else "JARVIS"}: ${it.content}" 
                }

                    // 3. เปิด Live session พร้อม tool bridge (Path A + Path B auto-detected)
                launch {
                    logDebug("JARVIS_VM", "Connecting Live session (with memory context)...")
                    
                    // หากมีงานค้างจากการเปลี่ยนเสียง ให้ฉีด Prompt เข้าไปบอก AI ในบรรทัดแรกของประวัติ
                    val contextToSubmit = if (!pendingCommandAfterVoiceChange.isNullOrBlank()) {
                        val task = pendingCommandAfterVoiceChange
                        pendingCommandAfterVoiceChange = null // Clear ทันทีเพื่อป้องกันการ loop
                        "SYSTEM_INFO: คุณเพิ่งเปลี่ยนเสียงสำเร็จ งานต่อไปที่คุณต้องทำทันทีคือ: $task. โปรดดำเนินการต่อและแจ้งผู้ใช้ด้วยเสียงใหม่ของคุณ."
                    } else {
                        historySnapshot
                    }
                    
                    orchestrator.startLiveVoiceSessionWithMemory(coreContext, contextToSubmit)
                }

                // 4. Collect audio output → speaker
                launch {
                    orchestrator.audioOutputFlow.collect { pcmBytes ->
                        pcmAudioEngine.playAudio(pcmBytes)
                    }
                }

                // 5. Collect text output → Chat UI
                launch {
                    orchestrator.textOutputFlow.collect { update ->
                        withContext(Dispatchers.Main) {
                            val msgList = _messages.value.toMutableList()
                            // ถ้าสั่งให้ append และข้อความล่าสุดคือบทบาทเดียวกัน ให้เขียนต่อที่เดิม
                            if (update.append && msgList.isNotEmpty() && msgList.last().role == update.role) {
                                val last = msgList.last()
                                msgList[msgList.size - 1] = last.copy(content = last.content + update.text)
                            } else {
                                // ถ้าเป็นงานใหม่ (append = false) หรือบทบาทเปลี่ยน ให้ขึ้นกล่องใหม่ทันที
                                msgList.add(Message(update.role, update.text))
                            }
                            _messages.value = msgList
                        }
                    }
                }

                // 6. Start mic recording → stream to Live model
                logDebug("JARVIS_VM", "Microphone starting...")
                var frameCount = 0
                pcmAudioEngine.startRecording { bytes ->
                    if (!_isMuted.value) {
                        frameCount++
                        // SILENCED: Heavy log
                        // if (frameCount % 50 == 0) {
                        //    logDebug("JARVIS_VM", "🎙️ Transmitting audio chunk #$frameCount (${bytes.size} bytes)")
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

    fun triggerSleepCycle() {
        if (_isSleeping.value) return
        _isSleeping.value = true
        viewModelScope.launch {
            val success = orchestrator.performSleepCycle()
            if (success) {
                // โหลดประวัติใหม่ เพราะข้อความเก่าถูกลบไปรวมยอดแล้ว
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

package com.example.personalaibot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalaibot.ai.JarvisOrchestrator
import com.example.personalaibot.data.GeminiModel
import com.example.personalaibot.db.DatabaseDriverFactory
import com.example.personalaibot.db.createDatabase
import com.example.personalaibot.memory.JarvisMemoryManager
import com.example.personalaibot.voice.PcmAudioEngine
import com.example.personalaibot.voice.VoiceManager
import io.ktor.util.encodeBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Message(val role: String, val content: String)

class JarvisViewModel(
    driverFactory: DatabaseDriverFactory,
    private val voiceManager: VoiceManager
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

    private val orchestrator = JarvisOrchestrator(client, memoryManager, "", defaultMainModel, defaultLiveModel)

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

    init {
        viewModelScope.launch {
            loadSettings()
            loadHistory()
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

        _apiKey.value = savedKey
        _selectedModel.value = savedModel
        _liveModelName.value = savedLiveModel
        _voiceName.value = savedVoiceName
        _floatingWidgetEnabled.value = savedWidgetEnabled
        orchestrator.updateConfig(savedKey, savedModel, savedLiveModel, savedVoiceName)

        if (savedKey.isNotBlank()) {
            fetchModels()
        }
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

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    fun toggleCamera() {
        _isCameraActive.value = !_isCameraActive.value
    }

    // ─── Live Voice (Gemini Multimodal Live) ─────────────────────────────────

    private val pcmAudioEngine = PcmAudioEngine()

    /** ชื่อ tool ที่กำลัง execute อยู่ — expose ไปยัง UI */
    val activeToolName: StateFlow<String?> = orchestrator.activeToolName

    private var liveSessionJob: kotlinx.coroutines.Job? = null

    fun startVoiceInput() {
        if (_isListening.value) return
        _isListening.value = true
        _voiceError.value = null
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
                    orchestrator.startLiveVoiceSessionWithMemory(coreContext, historySnapshot)
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
                pcmAudioEngine.startRecording { bytes ->
                    val base64 = bytes.encodeBase64()
                    viewModelScope.launch(Dispatchers.IO) {
                        orchestrator.sendLiveAudioChunk(base64)
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
        pcmAudioEngine.release()
        voiceManager.shutdown()
        client.close()
    }
}

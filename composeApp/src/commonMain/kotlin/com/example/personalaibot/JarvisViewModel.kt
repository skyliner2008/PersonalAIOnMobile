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
import com.example.personalaibot.tools.trading.auto.AutoTradingViewModel
import com.example.personalaibot.tools.ToolExecutor
import com.example.personalaibot.tools.ToolCall
import com.example.personalaibot.tools.camera.CameraToolExecutor
import com.example.personalaibot.tools.trading.AiTrackingInsight
import com.example.personalaibot.tools.trading.Mt5AccountInfo
import com.example.personalaibot.tools.trading.Mt5SymbolInfo
import com.example.personalaibot.tools.trading.Mt5TerminalService
import com.example.personalaibot.tools.trading.Mt5TradeItem
import com.example.personalaibot.tools.trading.SmcApiService
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.util.encodeBase64
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.random.Random

data class Message(val role: String, val content: String, val isStatic: Boolean = false)

data class Mt5ClientRuntimeInfo(
    val id: String,
    val name: String,
    val exePath: String,
    val running: Boolean,
    val pids: List<Long>
)

class JarvisViewModel(
    private val driverFactory: DatabaseDriverFactory,
    private val voiceManager: VoiceManager,
    private val fileHandler: (suspend (String, Map<String, String>) -> String)? = null,
    private val onDownloadLocalModel: (suspend (com.example.personalaibot.data.embedding.LocalOnnxEmbeddingProvider, (Float) -> Unit, Boolean) -> Unit)? = null
) : ViewModel() {
    private val defaultMt5BridgeBaseUrl =
        "https://parallelepipedonal-katy-nondeprecatingly.ngrok-free.dev"

    private val database = createDatabase(driverFactory).also { JarvisDatabaseHolder.install(it) }
    private val memoryManager = JarvisMemoryManager(database)
    val automationManager = JarvisDatabaseHolder.getAutomationManager()
    private val client = createHttpClient()
    val autoTrading by lazy { AutoTradingViewModel(
        scope = viewModelScope,
        client = client,
        bridgeBaseUrlProvider = { _mt5BridgeBaseUrl.value },
        authTokenProvider = { _mt5AuthToken.value },
        pairingStatusProvider = { _mt5PairingStatus.value }
    ) }
    private val mt5TerminalService = Mt5TerminalService(client)
    private val smcApiService = SmcApiService(client)
    private val plainJson = Json { ignoreUnknownKeys = true }

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

    // ─── Alert / Scheduled Task notification settings (อ่าน-เขียน AppSetting) ──
    private val _alertAiSummaryEnabled = MutableStateFlow(true)
    val alertAiSummaryEnabled: StateFlow<Boolean> = _alertAiSummaryEnabled.asStateFlow()

    private val _alertVoiceEnabled = MutableStateFlow(false)
    val alertVoiceEnabled: StateFlow<Boolean> = _alertVoiceEnabled.asStateFlow()

    val scheduledTasks = automationManager.scheduledTasks

    fun setAlertAiSummaryEnabled(enabled: Boolean) {
        _alertAiSummaryEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("alert_ai_summary", enabled.toString())
        }
    }

    fun setAlertVoiceEnabled(enabled: Boolean) {
        _alertVoiceEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("alert_voice", enabled.toString())
        }
    }

    // ─── Alert Auto Test (🧪 ทดสอบดึงข้อมูลทุก tool) ─────────────────────

    private val _alertTestRunning = MutableStateFlow(false)
    val alertTestRunning: StateFlow<Boolean> = _alertTestRunning.asStateFlow()

    private val _alertTestStatus = MutableStateFlow("")
    val alertTestStatus: StateFlow<String> = _alertTestStatus.asStateFlow()

    private val _alertTestResults = MutableStateFlow<List<com.example.personalaibot.automation.AlertDataTester.TestResult>>(emptyList())
    val alertTestResults: StateFlow<List<com.example.personalaibot.automation.AlertDataTester.TestResult>> = _alertTestResults.asStateFlow()

    fun runAlertDataTest() {
        if (_alertTestRunning.value) return
        _alertTestRunning.value = true
        _alertTestResults.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.personalaibot.automation.AlertDataTester(client).runFullTest("XAUUSD") { status, done ->
                    _alertTestStatus.value = status
                    _alertTestResults.value = done
                }
            } catch (e: Exception) {
                _alertTestStatus.value = "❌ ทดสอบล้มเหลว: ${e.message}"
            } finally {
                _alertTestRunning.value = false
            }
        }
    }

    fun createAlert(
        name: String,
        symbol: String,
        toolName: String,
        field: String,
        op: String,
        value: String,
        interval: Long
    ) {
        val operator = when (op) {
            ">" -> com.example.personalaibot.automation.ConditionOperator.GT
            "<" -> com.example.personalaibot.automation.ConditionOperator.LT
            ">=" -> com.example.personalaibot.automation.ConditionOperator.GTE
            "<=" -> com.example.personalaibot.automation.ConditionOperator.LTE
            "==" -> com.example.personalaibot.automation.ConditionOperator.EQ
            "contains" -> com.example.personalaibot.automation.ConditionOperator.CONTAINS
            else -> com.example.personalaibot.automation.ConditionOperator.GTE
        }
        val condition = com.example.personalaibot.automation.AutomationCondition(
            field = field,
            operator = operator,
            value = value
        )
        automationManager.registerJob(
            name = name,
            symbol = symbol,
            exchange = null,
            toolName = toolName,
            condition = condition,
            intervalMinutes = interval
        )
    }

    fun createScheduledTask(
        name: String,
        prompt: String,
        type: String,
        runAt: Long,
        hhmm: String?
    ) {
        automationManager.registerScheduledTask(
            name = name,
            prompt = prompt,
            scheduleType = type,
            runAt = runAt,
            timeHhmm = hhmm
        )
    }

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

    init {
        // Auto-check and initialize local ONNX if files exist on disk.
        // After init, if the model loaded successfully, mark progress = 2f
        // so the Settings UI shows "✓ Model Ready" instead of the
        // "Download Local Model" button on every cold start.
        viewModelScope.launch {
            try {
                onDownloadLocalModel?.invoke(orchestrator.embeddingRegistry.localOnnx, {}, true)
                if (orchestrator.embeddingRegistry.localOnnx.isAvailable()) {
                    _modelDownloadProgress.value = 2f
                    logDebug("JarvisVM", "Local ONNX model already on device — marked Ready")
                }
            } catch (e: Exception) {
                logError("JarvisVM", "Local model startup check failed", e)
            }
        }
        // โหลด custom tools ที่ Agent เคยสร้าง (custom_agent_tools/*.json) กลับเข้า ToolRegistry
        // — เดิม loadCustomTools() ไม่มี caller เลย ทำให้ tool ที่ AI สร้างหายทุกครั้งที่ปิดแอป
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                orchestrator.loadCustomTools()
            } catch (e: Exception) {
                logError("JarvisVM", "loadCustomTools startup failed", e)
            }
        }
    }

    private val maxContextTurns = 10
    
    private val _modelDownloadProgress = MutableStateFlow(-1f) // -1 = not downloading, 0..1 = progress, 2 = complete
    val modelDownloadProgress: StateFlow<Float> = _modelDownloadProgress.asStateFlow()
    
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

    // ─── MT5 Trading Terminal ─────────────────────────────────────────────
    private val _showTradingTerminal = MutableStateFlow(false)
    val showTradingTerminal: StateFlow<Boolean> = _showTradingTerminal.asStateFlow()

    private val _mt5BridgeBaseUrl = MutableStateFlow(defaultMt5BridgeBaseUrl)
    val mt5BridgeBaseUrl: StateFlow<String> = _mt5BridgeBaseUrl.asStateFlow()

    private val _mt5AuthToken = MutableStateFlow("")
    val mt5AuthToken: StateFlow<String> = _mt5AuthToken.asStateFlow()

    private val _mt5PairingStatus = MutableStateFlow("NOT_CONNECTED")
    val mt5PairingStatus: StateFlow<String> = _mt5PairingStatus.asStateFlow()

    private val _mt5IsSyncing = MutableStateFlow(false)
    val mt5IsSyncing: StateFlow<Boolean> = _mt5IsSyncing.asStateFlow()

    private val _mt5LastSyncAt = MutableStateFlow(0L)
    val mt5LastSyncAt: StateFlow<Long> = _mt5LastSyncAt.asStateFlow()

    // 2026-04-30 (P6) — server reachability flag.  False after the most recent
    // fetch attempt failed, true after a successful round-trip.  When false
    // the cached MT5 view is still rendered (no blank screen) and an offline
    // banner is shown above the screens via TradingTerminalScreen.
    private val _mt5ServerOnline = MutableStateFlow(true)
    val mt5ServerOnline: StateFlow<Boolean> = _mt5ServerOnline.asStateFlow()

    // 2026-04-30 (P6) — true while the very first cache load on boot is still
    // in flight.  Screens use this to show a small skeleton instead of an
    // empty state when the DB hasn't been read yet.
    private val _mt5CacheLoading = MutableStateFlow(true)
    val mt5CacheLoading: StateFlow<Boolean> = _mt5CacheLoading.asStateFlow()

    private val _mt5Error = MutableStateFlow<String?>(null)
    val mt5Error: StateFlow<String?> = _mt5Error.asStateFlow()

    private val _mt5ActionResult = MutableStateFlow<String?>(null)
    val mt5ActionResult: StateFlow<String?> = _mt5ActionResult.asStateFlow()

    private val _mt5Account = MutableStateFlow<Mt5AccountInfo?>(null)
    val mt5Account: StateFlow<Mt5AccountInfo?> = _mt5Account.asStateFlow()

    private val _mt5Symbols = MutableStateFlow<List<Mt5SymbolInfo>>(emptyList())
    val mt5Symbols: StateFlow<List<Mt5SymbolInfo>> = _mt5Symbols.asStateFlow()

    private val _mt5Positions = MutableStateFlow<List<Mt5TradeItem>>(emptyList())
    val mt5Positions: StateFlow<List<Mt5TradeItem>> = _mt5Positions.asStateFlow()

    private val _mt5Orders = MutableStateFlow<List<Mt5TradeItem>>(emptyList())
    val mt5Orders: StateFlow<List<Mt5TradeItem>> = _mt5Orders.asStateFlow()

    private val _mt5Deals = MutableStateFlow<List<Mt5TradeItem>>(emptyList())
    val mt5Deals: StateFlow<List<Mt5TradeItem>> = _mt5Deals.asStateFlow()

    private val _mt5Clients = MutableStateFlow<List<Mt5ClientRuntimeInfo>>(emptyList())
    val mt5Clients: StateFlow<List<Mt5ClientRuntimeInfo>> = _mt5Clients.asStateFlow()

    private val _mt5ClientsLoading = MutableStateFlow(false)
    val mt5ClientsLoading: StateFlow<Boolean> = _mt5ClientsLoading.asStateFlow()

    private val _mt5SelectedClientExe = MutableStateFlow("")
    val mt5SelectedClientExe: StateFlow<String> = _mt5SelectedClientExe.asStateFlow()

    private val _mt5TerminalFeed = MutableStateFlow<List<String>>(emptyList())
    val mt5TerminalFeed: StateFlow<List<String>> = _mt5TerminalFeed.asStateFlow()

    private val _mt5DefaultLot = MutableStateFlow("0.01")
    val mt5DefaultLot: StateFlow<String> = _mt5DefaultLot.asStateFlow()

    private val _mt5DefaultTpPoints = MutableStateFlow("300")
    val mt5DefaultTpPoints: StateFlow<String> = _mt5DefaultTpPoints.asStateFlow()

    private val _mt5DefaultSlPoints = MutableStateFlow("200")
    val mt5DefaultSlPoints: StateFlow<String> = _mt5DefaultSlPoints.asStateFlow()

    private val _mt5MaxDdPercent = MutableStateFlow("10")
    val mt5MaxDdPercent: StateFlow<String> = _mt5MaxDdPercent.asStateFlow()

    private val _aiTrackingActive = MutableStateFlow(false)
    val aiTrackingActive: StateFlow<Boolean> = _aiTrackingActive.asStateFlow()

    private val _aiTrackingIntervalSec = MutableStateFlow(8)
    val aiTrackingIntervalSec: StateFlow<Int> = _aiTrackingIntervalSec.asStateFlow()

    private val _aiTrackingWatchlist = MutableStateFlow("XAUUSD")
    val aiTrackingWatchlist: StateFlow<String> = _aiTrackingWatchlist.asStateFlow()

    private val _aiTrackingInsights = MutableStateFlow<List<AiTrackingInsight>>(emptyList())
    val aiTrackingInsights: StateFlow<List<AiTrackingInsight>> = _aiTrackingInsights.asStateFlow()

    private val _aiTrackingFeed = MutableStateFlow<List<String>>(emptyList())
    val aiTrackingFeed: StateFlow<List<String>> = _aiTrackingFeed.asStateFlow()

    private var visionTimeoutJob: Job? = null
    private var aiTrackingJob: Job? = null
    private var mt5AutoSyncJob: Job? = null
    private var mt5RealtimeJob: Job? = null
    private var mt5SnapshotRevision: String = ""

    private val pcmAudioEngine = PcmAudioEngine()

    private val speechThreshold = 0.05f // Volume threshold for "Speaking" state

    init {
        // Bridge camera frames to the unified Live session in the orchestrator

        // Bridge camera frames to the unified Live session in the orchestrator
        cameraService.onLiveFrameReady = { jpegBase64 ->
            orchestrator.sendLiveCameraFrame(jpegBase64)
        }

        // Initialize Camera Tool Executor
        ToolExecutor.initCameraExecutor(CameraToolExecutor(cameraService))
        ToolExecutor.setMt5RuntimeConfigProvider {
            com.example.personalaibot.tools.ToolExecutor.Mt5RuntimeConfig(
                bridgeBaseUrl = _mt5BridgeBaseUrl.value,
                authToken = _mt5AuthToken.value,
                pairingStatus = _mt5PairingStatus.value
            )
        }

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
                logDebug("JarvisVM", "🔔 Voice change requested: $newVoice")
                
                _voiceName.value = newVoice
                updateSettings(_apiKey.value, _selectedModel.value, _liveModelName.value, newVoice)
                // ผูกเสียงเข้ากับ identity (เพศ/น้ำเสียง/คำลงท้าย) + persist ลง Core Memory
                orchestrator.applyVoiceIdentity(newVoice)

                // Immediate Apply: Restart session if active
                if (_isListening.value) {
                    stopVoiceInput()
                    delay(800) // เพิ่ม delay เล็กน้อยเพื่อให้ระบบเคลียร์ resources และบันทึกความจำได้ทัน
                    // ตั้ง greeting ให้ AI พูดยืนยันเสียงใหม่อัตโนมัติทันทีที่ session READY (ผูกกับ event ไม่ใช่ timer)
                    orchestrator.setLiveGreetingOnReady("[SYSTEM] คุณเพิ่งเปลี่ยนเสียงเป็น $newVoice เรียบร้อยแล้ว โปรดพูดทักยืนยันเสียงใหม่สั้นๆ 1 ประโยคเท่านั้น (เช่น 'เปลี่ยนเสียงเป็น $newVoice เรียบร้อยแล้วครับ/ค่ะ') ไม่ต้องทำงานอื่นต่อ")
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
        val savedMt5BridgeBaseUrl = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("mt5_bridge_base_url").executeAsOneOrNull()
                ?: defaultMt5BridgeBaseUrl
        }
        val savedMt5AuthToken = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("mt5_auth_token").executeAsOneOrNull()
                ?: ""
        }
        val savedPreferFreeOnly = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("prefer_free_only").executeAsOneOrNull() == "true"
        }
        autoTrading.updatePreferFreeOnly(savedPreferFreeOnly)

        val savedAiTrackingIntervalSec = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("ai_tracking_interval_sec").executeAsOneOrNull()
                ?.toIntOrNull() ?: 8
        }
        val savedAiTrackingWatchlist = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("ai_tracking_watchlist").executeAsOneOrNull()
                ?: "XAUUSD"
        }
        val savedMt5DefaultLot = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("mt5_default_lot").executeAsOneOrNull() ?: "0.01"
        }
        val savedMt5DefaultTpPoints = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("mt5_default_tp_points").executeAsOneOrNull() ?: "300"
        }
        val savedMt5DefaultSlPoints = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("mt5_default_sl_points").executeAsOneOrNull() ?: "200"
        }
        val savedMt5MaxDdPercent = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("mt5_max_dd_percent").executeAsOneOrNull() ?: "10"
        }
        val savedMt5SelectedClientExe = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("mt5_selected_client_exe").executeAsOneOrNull() ?: ""
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
        // โหลดค่าสวิตช์การแจ้งเตือน — เดิมไม่ได้โหลดกลับ ทำให้ toggle แจ้งเตือนด้วยเสียงเด้งเป็น "ปิด" ทุกครั้งที่เปิดแอปใหม่
        val savedAlertAiSummary = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("alert_ai_summary").executeAsOneOrNull()?.let { it == "true" } ?: true
        }
        val savedAlertVoice = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("alert_voice").executeAsOneOrNull()?.let { it == "true" } ?: false
        }
        // toggle "Show free models only" — เดิมไม่ได้โหลดกลับ เด้งเป็นไม่ติ๊กทุกครั้ง
        _showFreeModelsOnly.value = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("show_free_models_only").executeAsOneOrNull()?.let { it == "true" } ?: false
        }

        _apiKey.value = savedKey
        _alertAiSummaryEnabled.value = savedAlertAiSummary
        _alertVoiceEnabled.value = savedAlertVoice
        _selectedModel.value = savedModel
        _liveModelName.value = savedLiveModel
        _voiceName.value = savedVoiceName
        _floatingWidgetEnabled.value = savedWidgetEnabled
        _mt5BridgeBaseUrl.value = savedMt5BridgeBaseUrl
        _mt5AuthToken.value = savedMt5AuthToken
        _mt5PairingStatus.value = if (savedMt5AuthToken.isBlank()) "NOT_CONNECTED" else "TOKEN_READY"
        _aiTrackingIntervalSec.value = savedAiTrackingIntervalSec.coerceIn(3, 120)
        _aiTrackingWatchlist.value = savedAiTrackingWatchlist
        _mt5DefaultLot.value = savedMt5DefaultLot
        _mt5DefaultTpPoints.value = savedMt5DefaultTpPoints
        _mt5DefaultSlPoints.value = savedMt5DefaultSlPoints
        _mt5MaxDdPercent.value = savedMt5MaxDdPercent
        _mt5SelectedClientExe.value = savedMt5SelectedClientExe
        _openaiApiKey.value = savedOpenaiKey
        _claudeApiKey.value = savedClaudeKey
        _openRouterApiKey.value = savedOpenRouterKey
        _minimaxApiKey.value = savedMinimaxKey
        _groqApiKey.value = savedGroqKey
        _nvidiaNimApiKey.value = savedNvidiaNimKey
        orchestrator.updateConfig(savedKey, savedModel, savedLiveModel, savedVoiceName)

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

            // Auto-backfill temporarily disabled to prevent startup API errors
            /*
            viewModelScope.launch {
                val count = memoryManager.backfillEmbeddings(orchestrator.getGeminiService())
                if (count > 0) {
                    logDebug("JarvisVM", "✅ Semantic backfill complete: Indexed $count facts")
                }
            }
            */
        }
        loadHistory()
        loadMt5FromDatabase()
        if (savedMt5AuthToken.isNotBlank()) {
            refreshMt5PairingStatus()
            startMt5RealtimeChannel()
        }
    }

    /** เลือกโปรไฟล์เสียงจาก Settings — ผูก identity + persist + (ถ้ากำลัง live) restart session พร้อมยืนยันเสียง */
    fun selectVoiceProfile(voice: String) {
        viewModelScope.launch {
            orchestrator.applyVoiceIdentity(voice)
            updateSettings(_apiKey.value, _selectedModel.value, _liveModelName.value, voice)
        }
    }

    fun updateSettings(key: String, model: String, liveModel: String = _liveModelName.value, voice: String = _voiceName.value, preferFree: Boolean = false) {
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
                database.jarvisDatabaseQueries.insertSetting("prefer_free_only", if (preferFree) "true" else "false")
            }
            orchestrator.updateConfig(key, model, liveModel, voice)
            // primary key เปลี่ยน → อัปเดต rotation chain ให้เริ่มจาก key ใหม่
            orchestrator.updateGeminiApiKeys(listOf(key) + _geminiApiKeys.value.filter { it != key })
            autoTrading.updatePreferFreeOnly(preferFree)

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

    private suspend fun loadHistory() {
        val history = memoryManager.getRecentHistory(20).reversed()
        // ซ่อนข้อความฝั่ง user ที่มาจาก live voice (transcription) — ตอนคุยสดไม่แสดง เปิดแอปใหม่ก็ไม่ควรโผล่
        _messages.value = history
            .filterNot { it.role == "user" && it.metadata?.contains("live_voice") == true }
            .map { Message(it.role, it.content) }
    }

    fun sendMessage(
        text: String,
        speakResponse: Boolean = false,
        attachments: List<com.example.personalaibot.ui.ChatAttachment> = emptyList()
    ) {
        if (text.isBlank() && attachments.isEmpty()) return

        viewModelScope.launch {
            try {
                // แยกไฟล์ text (ฝังเข้า prompt) กับ binary (ส่งเป็น inline_data)
                val textAtts = attachments.filter { it.isText }
                val binaryAtts = attachments.filterNot { it.isText }

                val displayText = buildString {
                    append(text)
                    if (attachments.isNotEmpty()) {
                        append("\n📎 แนบ: ")
                        append(attachments.joinToString(", ") { it.name })
                    }
                }
                _messages.value = _messages.value + Message("user", displayText)
                memoryManager.storeMessage("user", displayText)

                // prompt จริงที่ส่งให้ AI — ฝังเนื้อหาไฟล์ text ไว้ใน block ชัดเจน
                val promptForAi = buildString {
                    append(text)
                    textAtts.forEach { att ->
                        append("\n\n[ไฟล์แนบ: ${att.name}]\n```\n")
                        append(att.textContent ?: "")
                        append("\n```")
                    }
                }
                val inlineFiles = binaryAtts.mapNotNull { att ->
                    att.base64?.let { com.example.personalaibot.data.InlineData(att.mimeType, it) }
                }

                _isTyping.value = true
                val historySnapshot = buildHistorySnapshot()
                val coreContext = buildRuntimeCoreContext()
                _messages.value = _messages.value + Message("model", "")
                var currentAiMessage = ""

                logDebug("JarvisVM", "[Chat] Sending: model=${_selectedModel.value}, apiKey=${maskApiKey(_apiKey.value)}, attachments=${attachments.size} (binary=${inlineFiles.size})")
                val responseFlow = orchestrator.chatWithHistory(promptForAi, historySnapshot, coreContext, inlineFiles)

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
                // log เนื้อคำตอบจริงด้วย (preview 800 ตัวอักษร) — เดิมมีแค่จำนวน chars ตรวจพฤติกรรมโมเดลไม่ได้
                logDebug("JarvisVM", "[Chat] Response complete (${currentAiMessage.length} chars)\n>>> ${currentAiMessage.take(800)}")

                // ถ้า response ว่างเปล่า แสดง fallback message
                if (currentAiMessage.isBlank()) {
                    val model = _selectedModel.value
                    val providerId = if (model.contains("/")) model.substringBefore("/") else "gemini"
                    val fallbackMsg = "⚠️ ไม่ได้รับ response จาก $providerId\n" +
                        "• ตรวจสอบว่า API Key ถูกต้องและกด Save แล้ว\n" +
                        "• Model: $model\n" +
                        "• ดู Logcat (tag: Orchestrator) สำหรับรายละเอียด"
                    val currentList = _messages.value.toMutableList()
                    if (currentList.isNotEmpty()) {
                        currentList[currentList.size - 1] = Message("model", fallbackMsg)
                        _messages.value = currentList
                    }
                    logError("JarvisVM", "[Chat] Empty response — model=$model, apiKey=${maskApiKey(_apiKey.value)}")
                } else {
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

    fun setShowFreeModelsOnly(v: Boolean) {
        _showFreeModelsOnly.value = v
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("show_free_models_only", if (v) "true" else "false")
        }
    }

    /** ผล auto-test โมเดล ("provider/model" → capability) — persist ใน setting "model_caps_v1" */
    private val _modelCaps = MutableStateFlow<Map<String, com.example.personalaibot.data.providers.ModelAutoTester.ModelCapability>>(emptyMap())
    val modelCaps: StateFlow<Map<String, com.example.personalaibot.data.providers.ModelAutoTester.ModelCapability>> = _modelCaps.asStateFlow()

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

    /** Gemini API keys ทั้งหมด (multi free-tier accounts) — ใช้ rotate เมื่อ key ปัจจุบันติดลิมิต */
    private val _geminiApiKeys = MutableStateFlow<List<String>>(emptyList())
    val geminiApiKeys: StateFlow<List<String>> = _geminiApiKeys.asStateFlow()

    private fun persistGeminiApiKeys(keys: List<String>) {
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        com.example.personalaibot.ai.JarvisPersona.updateIdentity(
            com.example.personalaibot.ai.JarvisPersona.IdentityConfig(
                agentName = agentName,
                agentCreature = agentCreature,
                agentVibe = agentVibe,
                agentGender = agentGender,
                userName = userName,
                userCallName = userCallName,
                userNotes = userNotes
            )
        )
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
                val coreContext = buildRuntimeCoreContext()
                
                // 2. ดึงประวัติการสนทนาสั้นๆ เพื่อส่งให้ Live Session รู้บริบท
                val historySnapshot = messages.value.takeLast(10).joinToString("\n") { 
                    "${if (it.role == "user") "User" else "JARVIS"}: ${it.content}" 
                }

                    // 3. เปิด Live session พร้อม tool bridge (Path A + Path B auto-detected)
                launch {
                    logDebug("JARVIS_VM", "Connecting Live session (with memory context)...")
                    orchestrator.startLiveVoiceSessionWithMemory(coreContext, historySnapshot)
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

    fun openTradingTerminal() {
        _showTradingTerminal.value = true
        refreshMt5Clients()
        if (_mt5PairingStatus.value != "APPROVED" && _mt5AuthToken.value.isNotBlank()) {
            refreshMt5PairingStatus()
        }
        if (_mt5PairingStatus.value == "APPROVED") {
            startMt5RealtimeChannel()
            if (_mt5LastSyncAt.value == 0L) {
                refreshMt5Terminal()
            }
        } else if (_mt5AuthToken.value.isBlank() && _mt5LastSyncAt.value == 0L) {
            refreshMt5Terminal()
        }
    }

    fun closeTradingTerminal() {
        _showTradingTerminal.value = false
        stopMt5AutoSync()
    }

    fun clearMt5ActionResult() {
        _mt5ActionResult.value = null
    }

    fun clearMt5Error() {
        _mt5Error.value = null
    }

    fun updateMt5BridgeBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        if (normalized.isBlank()) return
        _mt5BridgeBaseUrl.value = normalized
        mt5SnapshotRevision = ""
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_bridge_base_url", normalized)
        }
    }

    fun updateMt5AuthToken(token: String) {
        val normalized = token.trim()
        _mt5AuthToken.value = normalized
        _mt5PairingStatus.value = if (normalized.isBlank()) "NOT_CONNECTED" else "TOKEN_READY"
        mt5SnapshotRevision = ""
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_auth_token", normalized)
        }
    }

    fun connectMt5Terminal(serverUrl: String) {
        val normalized = serverUrl.trim().trimEnd('/')
        if (normalized.isBlank()) return
        viewModelScope.launch {
            _mt5Error.value = null
            _mt5ActionResult.value = null
            updateMt5BridgeBaseUrl(normalized)
            appendMt5TerminalLine("connect_request url=$normalized")

            val token = if (_mt5AuthToken.value.isNotBlank()) {
                _mt5AuthToken.value
            } else {
                val generated = generateMt5ClientToken()
                updateMt5AuthToken(generated)
                generated
            }

            _mt5PairingStatus.value = "PAIRING_REQUEST_SENT"
            val pairUrl = "${normalizeServerRoot(_mt5BridgeBaseUrl.value)}/api/auth/pair/request"
            try {
                val response = client.post(pairUrl) {
                    header(HttpHeaders.ContentType, "application/json")
                    setBody(
                        buildJsonObject {
                            put("token", token)
                            put("deviceId", "android-${token.take(12)}")
                            put("deviceName", "PersonalAIBot Android")
                            put("appVersion", "composeApp")
                        }.toString()
                    )
                }
                val body = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    _mt5PairingStatus.value = "PAIRING_FAILED"
                    _mt5Error.value = "Pair request failed (${response.status.value}): $body"
                    return@launch
                }

                val root = runCatching { plainJson.parseToJsonElement(body).jsonObject }.getOrNull()
                val approved = root?.get("approved")?.jsonPrimitive?.content?.equals("true", ignoreCase = true) == true
                val status = root?.get("status")?.jsonPrimitive?.content.orEmpty()

                if (approved || status.equals("APPROVED", ignoreCase = true)) {
                    _mt5PairingStatus.value = "APPROVED"
                    _mt5ActionResult.value = "MT5 connected and approved"
                    appendMt5TerminalLine("pairing=APPROVED")
                    startMt5RealtimeChannel()
                    refreshMt5Terminal()
                } else {
                    _mt5PairingStatus.value = "PENDING_APPROVAL"
                    _mt5ActionResult.value = "Pair request sent. Please approve this device in server dashboard."
                    appendMt5TerminalLine("pairing=PENDING_APPROVAL")
                }
            } catch (e: Exception) {
                _mt5PairingStatus.value = "PAIRING_FAILED"
                _mt5Error.value = "Connect failed: ${e.message}"
                appendMt5TerminalLine("connect_failed ${e.message}")
            }
        }
    }

    fun disconnectMt5Terminal() {
        stopMt5AutoSync()
        stopMt5RealtimeChannel()
        _mt5PairingStatus.value = "NOT_CONNECTED"
        mt5SnapshotRevision = ""
        _mt5ActionResult.value = "Disconnected"
        appendMt5TerminalLine("disconnected")
    }

    fun refreshMt5PairingStatus() {
        val token = _mt5AuthToken.value.trim()
        if (token.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val url = "${normalizeServerRoot(_mt5BridgeBaseUrl.value)}/api/auth/pair/status?token=$token"
                val body = client.get(url).bodyAsText()
                val root = plainJson.parseToJsonElement(body).jsonObject
                val approved = root["approved"]?.jsonPrimitive?.content?.equals("true", ignoreCase = true) == true
                val status = root["status"]?.jsonPrimitive?.content.orEmpty()
                if (approved || status.equals("APPROVED", ignoreCase = true)) {
                    _mt5PairingStatus.value = "APPROVED"
                    startMt5RealtimeChannel()
                } else if (status.isNotBlank()) {
                    _mt5PairingStatus.value = status
                }
            }
        }
    }

    fun toggleMt5Connection(serverUrl: String) {
        if (_mt5PairingStatus.value == "APPROVED") {
            disconnectMt5Terminal()
        } else {
            connectMt5Terminal(serverUrl)
        }
    }

    fun selectMt5ClientExe(exePath: String) {
        _mt5SelectedClientExe.value = exePath
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_selected_client_exe", exePath)
        }
    }

    fun refreshMt5Clients() {
        if (_mt5ClientsLoading.value) return
        viewModelScope.launch {
            _mt5ClientsLoading.value = true
            runCatching {
                val endpoint = "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/clients"
                val body = client.get(endpoint) {
                    if (_mt5AuthToken.value.isNotBlank()) {
                        header(HttpHeaders.Authorization, "Bearer ${_mt5AuthToken.value.trim()}")
                    }
                }.bodyAsText()
                val root = plainJson.parseToJsonElement(body).jsonObject
                val rows = root["data"]?.jsonObject?.get("rows")?.jsonArray.orEmpty()
                val mapped = rows.mapNotNull { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                    val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
                    val exePath = obj["exePath"]?.jsonPrimitive?.content.orEmpty()
                    if (exePath.isBlank()) return@mapNotNull null
                    val running = obj["running"]?.jsonPrimitive?.content?.equals("true", ignoreCase = true) == true
                    val pids = obj["pids"]?.jsonArray.orEmpty()
                        .mapNotNull { it.jsonPrimitive.content.toLongOrNull() }
                    Mt5ClientRuntimeInfo(
                        id = id,
                        name = if (name.isBlank()) exePath else name,
                        exePath = exePath,
                        running = running,
                        pids = pids
                    )
                }
                _mt5Clients.value = mapped
                if (_mt5SelectedClientExe.value.isBlank() && mapped.isNotEmpty()) {
                    selectMt5ClientExe(mapped.first().exePath)
                }
                appendMt5TerminalLine("clients_refresh count=${mapped.size}")
            }.onFailure { e ->
                _mt5Error.value = "MT5 client list failed: ${e.message}"
                appendMt5TerminalLine("clients_refresh_failed ${e.message}")
            }
            _mt5ClientsLoading.value = false
        }
    }

    fun startSelectedMt5Client() {
        val exePath = _mt5SelectedClientExe.value.trim()
        if (exePath.isBlank()) {
            _mt5Error.value = "Please select MT5 client first"
            return
        }
        viewModelScope.launch {
            runCatching {
                val endpoint = "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/clients/start"
                client.post(endpoint) {
                    header(HttpHeaders.ContentType, "application/json")
                    if (_mt5AuthToken.value.isNotBlank()) {
                        header(HttpHeaders.Authorization, "Bearer ${_mt5AuthToken.value.trim()}")
                    }
                    setBody(buildJsonObject { put("exePath", exePath) }.toString())
                }.bodyAsText()
                _mt5ActionResult.value = "Started MT5 client"
                appendMt5TerminalLine("client_start $exePath")
                delay(1200)
                refreshMt5Clients()
            }.onFailure { e ->
                _mt5Error.value = "Start MT5 client failed: ${e.message}"
                appendMt5TerminalLine("client_start_failed ${e.message}")
            }
        }
    }

    fun stopSelectedMt5Client() {
        val selected = _mt5Clients.value.find { it.exePath == _mt5SelectedClientExe.value }
        if (selected == null || selected.pids.isEmpty()) {
            _mt5ActionResult.value = "Selected MT5 client is not running"
            return
        }
        val pid = selected.pids.first()
        viewModelScope.launch {
            runCatching {
                val endpoint = "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/clients/stop"
                client.post(endpoint) {
                    header(HttpHeaders.ContentType, "application/json")
                    if (_mt5AuthToken.value.isNotBlank()) {
                        header(HttpHeaders.Authorization, "Bearer ${_mt5AuthToken.value.trim()}")
                    }
                    setBody(buildJsonObject { put("pid", pid) }.toString())
                }.bodyAsText()
                _mt5ActionResult.value = "Stopped MT5 client (pid=$pid)"
                appendMt5TerminalLine("client_stop pid=$pid")
                delay(800)
                refreshMt5Clients()
            }.onFailure { e ->
                _mt5Error.value = "Stop MT5 client failed: ${e.message}"
                appendMt5TerminalLine("client_stop_failed ${e.message}")
            }
        }
    }

    fun refreshMt5Terminal(historyLimit: Int = 200, waitMs: Int = 0) {
        if (_mt5IsSyncing.value) return
        if (_mt5PairingStatus.value != "APPROVED") {
            if (_mt5AuthToken.value.isNotBlank()) {
                refreshMt5PairingStatus()
                appendMt5TerminalLine("sync_skipped waiting_for_approval status=${_mt5PairingStatus.value}")
            } else {
                appendMt5TerminalLine("sync_skipped not_connected")
            }
            return
        }
        viewModelScope.launch {
            _mt5IsSyncing.value = true
            _mt5Error.value = null
            logDebug("JarvisVM", "MT5 refresh start base=${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)} status=${_mt5PairingStatus.value}")
            try {
                val delta = mt5TerminalService.fetchSnapshotDelta(
                    baseUrl = normalizeMt5ApiBase(_mt5BridgeBaseUrl.value),
                    authToken = _mt5AuthToken.value,
                    historyLimit = historyLimit,
                    sinceRevision = mt5SnapshotRevision,
                    waitMs = waitMs
                )
                if (delta.revision.isNotBlank()) {
                    mt5SnapshotRevision = delta.revision
                }
                _mt5PairingStatus.value = "APPROVED"

                // 2026-04-30 (P6) — successful round-trip: server is reachable.
                _mt5ServerOnline.value = true

                if (!delta.changed || delta.snapshot == null) {
                    if (delta.syncedAt > 0L) _mt5LastSyncAt.value = delta.syncedAt
                    appendMt5TerminalLine("sync_no_change revision=${if (mt5SnapshotRevision.isBlank()) "n/a" else mt5SnapshotRevision}")
                    logDebug("JarvisVM", "MT5 refresh no-change revision=${if (mt5SnapshotRevision.isBlank()) "n/a" else mt5SnapshotRevision}")
                    return@launch
                }

                val snapshot = delta.snapshot
                val changedSectionsRaw = delta.changedSections
                val changedSections = if (changedSectionsRaw.isEmpty()) {
                    setOf("account", "symbols", "positions", "orders", "history")
                } else {
                    changedSectionsRaw
                }
                val merged = applyMt5SnapshotUpdate(snapshot, changedSections)
                refreshMt5ServerFeed()
                appendMt5TerminalLine(
                    "sync_ok changed=${changedSections.joinToString(",")} positions=${merged.positions.size} orders=${merged.orders.size} deals=${merged.deals.size}"
                )
                logDebug(
                    "JarvisVM",
                    "MT5 refresh ok changed=${changedSections.joinToString(",")} positions=${merged.positions.size} orders=${merged.orders.size} deals=${merged.deals.size}"
                )
            } catch (e: Exception) {
                _mt5Error.value = "MT5 sync failed: ${e.message}"
                // 2026-04-30 (P6) — fetch failed.  Mark server as offline; UI
                // shows the cached view + amber banner instead of a blank
                // screen.  We still try to hydrate from the local cache so
                // the user sees the last-known state.
                _mt5ServerOnline.value = false
                if (e.message?.contains("unavailable", ignoreCase = true) == true ||
                    e.message?.contains("failed", ignoreCase = true) == true) {
                    _mt5PairingStatus.value = "BRIDGE_OFFLINE"
                }
                loadMt5FromDatabase()
                appendMt5TerminalLine("sync_failed ${e.message}")
                logDebug("JarvisVM", "MT5 refresh failed: ${e.message} -> status=${_mt5PairingStatus.value}")
            } finally {
                _mt5IsSyncing.value = false
            }
        }
    }

    fun placeMt5Order(
        action: String,
        symbol: String,
        volume: String,
        sl: String = "",
        tp: String = "",
        comment: String = ""
    ) {
        viewModelScope.launch {
            val args = mutableMapOf(
                "action" to action.uppercase(),
                "symbol" to symbol.uppercase().trim(),
                "volume" to volume.trim(),
                "endpoint" to "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/order"
            )
            if (sl.isNotBlank()) args["sl"] = sl.trim()
            if (tp.isNotBlank()) args["tp"] = tp.trim()
            if (comment.isNotBlank()) args["comment"] = comment.trim()
            if (_mt5AuthToken.value.isNotBlank()) args["token"] = _mt5AuthToken.value.trim()

            val result = ToolExecutor.execute(ToolCall("trading_mt5_order", args))
            _mt5ActionResult.value = result.result
            if (result.isError) _mt5Error.value = result.result
            appendMt5TerminalLine("order ${action.uppercase()} ${symbol.uppercase().trim()} volume=${volume.trim()} result=${if (result.isError) "error" else "ok"}")
            refreshMt5Terminal()
        }
    }

    fun closeMt5Position(symbol: String = "", ticket: String = "") {
        viewModelScope.launch {
            val args = mutableMapOf(
                "endpoint" to "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/close"
            )
            if (symbol.isNotBlank()) args["symbol"] = symbol.uppercase().trim()
            if (ticket.isNotBlank()) args["ticket"] = ticket.trim()
            if (_mt5AuthToken.value.isNotBlank()) args["token"] = _mt5AuthToken.value.trim()

            val result = ToolExecutor.execute(ToolCall("trading_mt5_close_position", args))
            _mt5ActionResult.value = result.result
            if (result.isError) _mt5Error.value = result.result
            appendMt5TerminalLine("close symbol=${symbol.uppercase().trim()} ticket=${ticket.trim()} result=${if (result.isError) "error" else "ok"}")
            refreshMt5Terminal()
        }
    }

    fun closeMt5AllPositions(side: String = "ALL") {
        val normalizedSide = side.uppercase()
        viewModelScope.launch {
            val rows = _mt5Positions.value.filter {
                when (normalizedSide) {
                    "BUY" -> it.side.contains("BUY", ignoreCase = true)
                    "SELL" -> it.side.contains("SELL", ignoreCase = true)
                    else -> true
                }
            }
            if (rows.isEmpty()) {
                _mt5ActionResult.value = "No positions to close for $normalizedSide"
                return@launch
            }
            rows.forEach { row ->
                closeMt5Position(row.symbol, row.ticket)
                delay(120)
            }
        }
    }

    /**
     * แก้ไข SL / TP ของ position ที่เปิดอยู่ผ่าน tool trading_mt5_modify_position
     * sl หรือ tp ถ้าปล่อยว่าง → คงค่าเดิมใน bridge
     */
    fun modifyMt5Position(symbol: String, ticket: String, sl: String = "", tp: String = "") {
        viewModelScope.launch {
            val args = mutableMapOf(
                "endpoint" to "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/modify"
            )
            if (symbol.isNotBlank()) args["symbol"] = symbol.uppercase().trim()
            if (ticket.isNotBlank()) args["ticket"] = ticket.trim()
            if (sl.isNotBlank()) args["sl"] = sl.trim()
            if (tp.isNotBlank()) args["tp"] = tp.trim()
            if (_mt5AuthToken.value.isNotBlank()) args["token"] = _mt5AuthToken.value.trim()

            val result = ToolExecutor.execute(ToolCall("trading_mt5_modify_position", args))
            _mt5ActionResult.value = result.result
            if (result.isError) _mt5Error.value = result.result
            appendMt5TerminalLine(
                "modify symbol=${symbol.uppercase().trim()} ticket=${ticket.trim()} sl=${sl.trim()} tp=${tp.trim()} result=${if (result.isError) "error" else "ok"}"
            )
            refreshMt5Terminal()
        }
    }

    /**
     * Break-Even: เลื่อน SL ไปที่ราคาเปิดของทุก position ที่กำลังกำไร
     */
    fun setMt5BreakEvenAll() {
        viewModelScope.launch {
            val rows = _mt5Positions.value.filter { it.profit > 0.0 && it.priceOpen > 0.0 }
            if (rows.isEmpty()) {
                _mt5ActionResult.value = "ไม่มี position ที่กำไรอยู่ตอนนี้"
                appendMt5TerminalLine("break_even noop (no profitable positions)")
                return@launch
            }
            appendMt5TerminalLine("break_even start count=${rows.size}")
            rows.forEach { row ->
                modifyMt5Position(
                    symbol = row.symbol,
                    ticket = row.ticket,
                    sl = row.priceOpen.toString(),
                    tp = ""
                )
                delay(150)
            }
            _mt5ActionResult.value = "Break-even ส่งคำสั่งสำเร็จ ${rows.size} ตำแหน่ง"
        }
    }

    /**
     * Backward-compat stub — เรียก modifyMt5Position โดยไม่ส่ง sl/tp
     * (คงไว้เผื่อมีจุดที่ยังเรียกเก่าอยู่)
     */
    @Deprecated("ใช้ modifyMt5Position แทน")
    fun editMt5PositionNotSupported(symbol: String, ticket: String) {
        _mt5ActionResult.value = "กรุณาระบุ SL/TP ใหม่ก่อน ($symbol/$ticket)"
        appendMt5TerminalLine("edit_noop symbol=$symbol ticket=$ticket")
    }

    fun setMt5DefaultLot(value: String) {
        _mt5DefaultLot.value = value
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_default_lot", value)
        }
    }

    fun setMt5DefaultTpPoints(value: String) {
        _mt5DefaultTpPoints.value = value
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_default_tp_points", value)
        }
    }

    fun setMt5DefaultSlPoints(value: String) {
        _mt5DefaultSlPoints.value = value
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_default_sl_points", value)
        }
    }

    fun setMt5MaxDdPercent(value: String) {
        _mt5MaxDdPercent.value = value
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_max_dd_percent", value)
        }
    }

    private fun startMt5AutoSync() {
        // Kept for compatibility with existing calls; realtime channel is primary flow.
        startMt5RealtimeChannel()
    }

    private fun stopMt5AutoSync() {
        mt5AutoSyncJob?.cancel()
        mt5AutoSyncJob = null
    }

    private fun startMt5RealtimeChannel() {
        if (mt5RealtimeJob?.isActive == true) return
        if (_mt5PairingStatus.value != "APPROVED") return
        if (_mt5AuthToken.value.isBlank()) return

        mt5RealtimeJob = viewModelScope.launch(Dispatchers.IO) {
            appendMt5TerminalLine("realtime_connecting")
            while (_mt5AuthToken.value.isNotBlank() && _mt5PairingStatus.value == "APPROVED") {
                try {
                    val wsUrl = buildMt5WsUrl(normalizeServerRoot(_mt5BridgeBaseUrl.value))
                    val session = client.webSocketSession {
                        url(wsUrl)
                        header(HttpHeaders.Authorization, "Bearer ${_mt5AuthToken.value.trim()}")
                    }
                    appendMt5TerminalLine("realtime_connected")
                    session.send(Frame.Text("""{"type":"force_snapshot"}"""))

                    val heartbeat = launch {
                        while (true) {
                            delay(15_000)
                            session.send(Frame.Text("""{"type":"ping"}"""))
                        }
                    }

                    for (frame in session.incoming) {
                        if (frame is Frame.Text) {
                            handleMt5RealtimeMessage(frame.readText())
                        }
                    }
                    heartbeat.cancel()
                } catch (e: Exception) {
                    appendMt5TerminalLine("realtime_disconnected ${e.message}")
                    logDebug("JarvisVM", "MT5 realtime disconnected: ${e.message}")
                }
                delay(2500)
            }
        }
    }

    private fun stopMt5RealtimeChannel() {
        mt5RealtimeJob?.cancel()
        mt5RealtimeJob = null
        appendMt5TerminalLine("realtime_stopped")
    }

    private suspend fun handleMt5RealtimeMessage(text: String) {
        val root = runCatching { plainJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val type = root["type"]?.jsonPrimitive?.content.orEmpty().lowercase()
        when (type) {
            "hello" -> {
                appendMt5TerminalLine("realtime_hello")
            }
            "pong" -> {
                // ignore
            }
            "error" -> {
                val message = root["message"]?.jsonPrimitive?.content.orEmpty()
                if (message.isNotBlank()) {
                    _mt5Error.value = message
                    appendMt5TerminalLine("realtime_error $message")
                }
            }
            "snapshot_delta" -> {
                val delta = mt5TerminalService.parseSnapshotDeltaElement(root) ?: return
                if (delta.revision.isNotBlank()) {
                    mt5SnapshotRevision = delta.revision
                }
                if (!delta.changed || delta.snapshot == null) return

                val changedSections = if (delta.changedSections.isEmpty()) {
                    setOf("account", "symbols", "positions", "orders", "history")
                } else {
                    delta.changedSections
                }
                val merged = applyMt5SnapshotUpdate(delta.snapshot, changedSections)
                refreshMt5ServerFeed()
                appendMt5TerminalLine(
                    "realtime_update changed=${changedSections.joinToString(",")} positions=${merged.positions.size} orders=${merged.orders.size} deals=${merged.deals.size}"
                )
            }
        }
    }

    private fun buildMt5WsUrl(serverRoot: String): String {
        val root = serverRoot.trim().trimEnd('/')
        val wsRoot = when {
            root.startsWith("https://", ignoreCase = true) -> "wss://${root.removePrefix("https://")}"
            root.startsWith("http://", ignoreCase = true) -> "ws://${root.removePrefix("http://")}"
            root.startsWith("wss://", ignoreCase = true) || root.startsWith("ws://", ignoreCase = true) -> root
            else -> "ws://$root"
        }
        return "$wsRoot/ws/mt5"
    }

    private fun appendMt5TerminalLine(line: String) {
        val stamp = kotlinx.datetime.Clock.System.now().toString()
        _mt5TerminalFeed.value = (_mt5TerminalFeed.value + "$stamp  $line").takeLast(300)
    }

    private suspend fun refreshMt5ServerFeed() {
        val endpoint = "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/trade-actions"
        runCatching {
            val text = client.get(endpoint) {
                if (_mt5AuthToken.value.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer ${_mt5AuthToken.value.trim()}")
                }
            }.bodyAsText()
            val root = plainJson.parseToJsonElement(text).jsonObject
            val rows = root["data"]?.jsonObject?.get("rows")?.jsonArray.orEmpty()
            val lines = rows.take(30).mapNotNull { el ->
                val obj = el.jsonObject
                val ts = obj["created_at"]?.jsonPrimitive?.content.orEmpty()
                val action = obj["action_type"]?.jsonPrimitive?.content.orEmpty()
                val status = obj["status"]?.jsonPrimitive?.content.orEmpty()
                val symbol = obj["symbol"]?.jsonPrimitive?.content.orEmpty()
                val ticket = obj["ticket"]?.jsonPrimitive?.content.orEmpty()
                if (action.isBlank()) null else "$ts  [$action/$status] $symbol ${if (ticket.isBlank()) "" else "ticket=$ticket"}".trim()
            }.distinct()
            if (lines.isNotEmpty()) {
                _mt5TerminalFeed.value = lines
            }
        }
    }

    private data class Mt5MergedSnapshot(
        val account: Mt5AccountInfo?,
        val symbols: List<Mt5SymbolInfo>,
        val positions: List<Mt5TradeItem>,
        val orders: List<Mt5TradeItem>,
        val deals: List<Mt5TradeItem>
    )

    private suspend fun applyMt5SnapshotUpdate(
        snapshot: com.example.personalaibot.tools.trading.Mt5TerminalSnapshot,
        changedSections: Set<String>
    ): Mt5MergedSnapshot {
        val mergedAccount = if (changedSections.contains("account")) snapshot.account else _mt5Account.value
        val mergedSymbols = if (changedSections.contains("symbols")) snapshot.symbols else _mt5Symbols.value
        val mergedPositions = if (changedSections.contains("positions")) snapshot.positions else _mt5Positions.value
        val mergedOrders = if (changedSections.contains("orders")) snapshot.orders else _mt5Orders.value
        val mergedDeals = if (changedSections.contains("history")) snapshot.deals else _mt5Deals.value

        if (changedSections.contains("account")) _mt5Account.value = mergedAccount
        if (changedSections.contains("symbols")) _mt5Symbols.value = mergedSymbols
        if (changedSections.contains("positions")) _mt5Positions.value = mergedPositions
        if (changedSections.contains("orders")) _mt5Orders.value = mergedOrders
        if (changedSections.contains("history")) _mt5Deals.value = mergedDeals
        _mt5LastSyncAt.value = snapshot.syncedAt

        persistMt5Snapshot(
            com.example.personalaibot.tools.trading.Mt5TerminalSnapshot(
                account = mergedAccount,
                symbols = mergedSymbols,
                positions = mergedPositions,
                orders = mergedOrders,
                deals = mergedDeals,
                syncedAt = snapshot.syncedAt
            ),
            changedSections = changedSections
        )
        return Mt5MergedSnapshot(
            account = mergedAccount,
            symbols = mergedSymbols,
            positions = mergedPositions,
            orders = mergedOrders,
            deals = mergedDeals
        )
    }

    /**
     * 2026-04-30 (P6) — delegates to [Mt5LocalCache.saveSnapshot] which mirrors
     * the same SQLDelight inserts but is reusable from non-ViewModel callers
     * (e.g. AutoTradingEngine background sync).  Persistence is in a single
     * place now, easier to evolve.
     */
    private suspend fun persistMt5Snapshot(
        snapshot: com.example.personalaibot.tools.trading.Mt5TerminalSnapshot,
        changedSections: Set<String> = setOf("account", "symbols", "positions", "orders", "history")
    ) {
        com.example.personalaibot.tools.trading.Mt5LocalCache.saveSnapshot(snapshot, changedSections)
    }

    /**
     * 2026-04-30 (P6) — replaced inline DB reads with [Mt5LocalCache.loadSnapshot]
     * so the same cache layer can be reused from any screen.  Behaviour
     * preserved: on first paint we hydrate the StateFlows from local DB so the
     * UI shows the *latest known* MT5 view even if the bridge / core-server
     * is offline.  `_mt5CacheLoading` is set false at the end so screens can
     * stop the skeleton.
     */
    private suspend fun loadMt5FromDatabase() {
        withContext(Dispatchers.IO) {
            val q = database.jarvisDatabaseQueries
            val accountRow = q.getLatestMt5Account().executeAsOneOrNull()
            val symbolRows = q.getMt5Symbols().executeAsList()
            val positionRows = q.getRecentMt5TradeRecordsByType("POSITION", 200).executeAsList()
            val orderRows = q.getRecentMt5TradeRecordsByType("ORDER", 200).executeAsList()
            val dealRows = q.getRecentMt5TradeRecordsByType("DEAL", 400).executeAsList()

            withContext(Dispatchers.Main) {
                _mt5Account.value = accountRow?.let {
                    Mt5AccountInfo(
                        login = it.login ?: "",
                        accountName = it.account_name ?: "",
                        server = it.server ?: "",
                        currency = it.currency ?: "USD",
                        leverage = (it.leverage ?: 0L).toInt(),
                        balance = it.balance ?: 0.0,
                        equity = it.equity ?: 0.0,
                        margin = it.margin ?: 0.0,
                        freeMargin = it.free_margin ?: 0.0,
                        payloadJson = it.payload_json ?: "",
                        updatedAt = it.updated_at
                    )
                }

                _mt5Symbols.value = symbolRows.map {
                    Mt5SymbolInfo(
                        symbol = it.symbol,
                        description = it.description ?: "",
                        digits = (it.digits ?: 0L).toInt(),
                        point = it.point ?: 0.0,
                        tradeMode = it.trade_mode ?: "",
                        bid = it.bid ?: 0.0,
                        ask = it.ask ?: 0.0,
                        spread = it.spread ?: 0.0,
                        payloadJson = it.payload_json ?: "",
                        updatedAt = it.updated_at
                    )
                }

                fun mapTradeRows(rows: List<com.example.personalaibot.db.Mt5TradeRecord>): List<Mt5TradeItem> {
                    return rows.map {
                        Mt5TradeItem(
                            recordType = it.record_type,
                            ticket = it.ticket,
                            positionTicket = it.position_ticket ?: "",
                            symbol = it.symbol,
                            side = it.side ?: "",
                            volume = it.volume ?: 0.0,
                            priceOpen = it.price_open ?: 0.0,
                            priceCurrent = it.price_current ?: 0.0,
                            profit = it.profit ?: 0.0,
                            swap = it.swap ?: 0.0,
                            commission = it.commission ?: 0.0,
                            sl = it.sl ?: 0.0,
                            tp = it.tp ?: 0.0,
                            state = it.state ?: "",
                            comment = it.comment ?: "",
                            eventTime = it.event_time ?: 0L,
                            payloadJson = it.payload_json ?: "",
                            syncedAt = it.synced_at
                        )
                    }
                }

                _mt5Positions.value = mapTradeRows(positionRows)
                _mt5Orders.value = mapTradeRows(orderRows)
                _mt5Deals.value = mapTradeRows(dealRows)
                _mt5LastSyncAt.value = listOf(
                    accountRow?.updated_at ?: 0L,
                    symbolRows.maxOfOrNull { it.updated_at } ?: 0L,
                    positionRows.maxOfOrNull { it.synced_at } ?: 0L,
                    orderRows.maxOfOrNull { it.synced_at } ?: 0L,
                    dealRows.maxOfOrNull { it.synced_at } ?: 0L
                ).maxOrNull() ?: 0L
                // 2026-04-30 (P6) — first cache hydration is done.  Screens
                // can now drop the skeleton even if the network never replies.
                _mt5CacheLoading.value = false
            }
        }
    }

    fun updateAiTrackingIntervalSec(seconds: Int) {
        val value = seconds.coerceIn(3, 120)
        _aiTrackingIntervalSec.value = value
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("ai_tracking_interval_sec", value.toString())
        }
    }

    fun updateAiTrackingWatchlist(input: String) {
        _aiTrackingWatchlist.value = input
        viewModelScope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("ai_tracking_watchlist", input)
        }
    }

    fun startAiTracking() {
        if (_aiTrackingActive.value) return
        _aiTrackingActive.value = true
        _mt5Error.value = null
        aiTrackingJob?.cancel()
        aiTrackingJob = viewModelScope.launch(Dispatchers.IO) {
            while (_aiTrackingActive.value) {
                try {
                    val snapshot = mt5TerminalService.fetchSnapshot(
                        baseUrl = normalizeMt5ApiBase(_mt5BridgeBaseUrl.value),
                        authToken = _mt5AuthToken.value,
                        historyLimit = 300
                    )
                    withContext(Dispatchers.Main) {
                        _mt5Account.value = snapshot.account
                        _mt5Symbols.value = snapshot.symbols
                        _mt5Positions.value = snapshot.positions
                        _mt5Orders.value = snapshot.orders
                        _mt5Deals.value = snapshot.deals
                        _mt5LastSyncAt.value = snapshot.syncedAt
                    }
                    persistMt5Snapshot(snapshot)

                    val trackedSymbols = buildTrackedSymbols(snapshot)
                    val insights = trackedSymbols.mapNotNull { symbol ->
                        runCatching { analyzeSymbolRealtime(symbol, snapshot) }.getOrNull()
                    }
                    withContext(Dispatchers.Main) {
                        _aiTrackingInsights.value = insights.sortedBy { it.symbol }
                        appendAiTrackingFeed(
                            "AI Tracking: synced ${insights.size} symbols @ ${snapshot.syncedAt}"
                        )
                    }
                    database.jarvisDatabaseQueries.insertTradeSyncSnapshot(
                        source = "ai_tracking",
                        payload_json = insights.joinToString(prefix = "[", postfix = "]") {
                            """{"symbol":"${it.symbol}","price":${it.lastPrice},"bias":"${it.bias}","history_ok":${it.canFetchHistory},"indicators_ok":${it.indicatorsReady}}"""
                        },
                        synced_at = snapshot.syncedAt
                    )
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _mt5Error.value = "AI tracking error: ${e.message}"
                        appendAiTrackingFeed("AI Tracking error: ${e.message}")
                    }
                }
                delay((_aiTrackingIntervalSec.value.coerceIn(3, 120) * 1000L))
            }
        }
    }

    fun stopAiTracking() {
        _aiTrackingActive.value = false
        aiTrackingJob?.cancel()
        aiTrackingJob = null
        appendAiTrackingFeed("AI Tracking stopped")
    }

    private fun appendAiTrackingFeed(line: String) {
        val updated = (_aiTrackingFeed.value + line).takeLast(120)
        _aiTrackingFeed.value = updated
    }

    private fun buildTrackedSymbols(snapshot: com.example.personalaibot.tools.trading.Mt5TerminalSnapshot): List<String> {
        val fromPositions = snapshot.positions.map { it.symbol.uppercase() }
        val fromOrders = snapshot.orders.map { it.symbol.uppercase() }
        val fromWatchlist = _aiTrackingWatchlist.value
            .split(",", ";", "\n", " ")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
        return (fromPositions + fromOrders + fromWatchlist)
            .distinct()
            .take(12)
            .ifEmpty { listOf("XAUUSD") }
    }

    private suspend fun analyzeSymbolRealtime(
        symbol: String,
        snapshot: com.example.personalaibot.tools.trading.Mt5TerminalSnapshot
    ): AiTrackingInsight {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val positions = snapshot.positions.filter { it.symbol.equals(symbol, ignoreCase = true) }
        val orders = snapshot.orders.filter { it.symbol.equals(symbol, ignoreCase = true) }

        val f1 = smcApiService.fetchCandlesWithSource(symbol, "1m", 200)
        val f5 = smcApiService.fetchCandlesWithSource(symbol, "5m", 200)
        val f15 = smcApiService.fetchCandlesWithSource(symbol, "15m", 200)
        val candles15 = f15.candles

        val lastPrice = when {
            f1.candles.isNotEmpty() -> f1.candles.last().close
            f5.candles.isNotEmpty() -> f5.candles.last().close
            f15.candles.isNotEmpty() -> f15.candles.last().close
            else -> 0.0
        }

        val canFetchHistory = f1.candles.size >= 60 || f5.candles.size >= 60 || f15.candles.size >= 60

        val ema20 = ema(candles15.map { it.close }, 20)
        val ema50 = ema(candles15.map { it.close }, 50)
        val rsi14 = rsi(candles15.map { it.close }, 14)
        val atr14 = if (candles15.size >= 20) smcApiService.calcATR(candles15, 14) else null
        val indicatorsReady = ema20 != null && ema50 != null && rsi14 != null && atr14 != null

        val bias = when {
            !indicatorsReady -> "DATA_PENDING"
            ema20!! > ema50!! && rsi14!! >= 55.0 -> "BULLISH"
            ema20 < ema50 && rsi14 <= 45.0 -> "BEARISH"
            else -> "NEUTRAL"
        }

        val totalPos = positions.size.coerceAtLeast(1)
        val slCoverage = positions.count { it.sl > 0.0 }.toDouble() / totalPos.toDouble() * 100.0
        val tpCoverage = positions.count { it.tp > 0.0 }.toDouble() / totalPos.toDouble() * 100.0

        return AiTrackingInsight(
            symbol = symbol,
            lastPrice = lastPrice,
            positionCount = positions.size,
            orderCount = orders.size,
            slCoveragePct = if (positions.isNotEmpty()) slCoverage else 100.0,
            tpCoveragePct = if (positions.isNotEmpty()) tpCoverage else 100.0,
            candles1m = f1.candles.size,
            candles5m = f5.candles.size,
            candles15m = f15.candles.size,
            canFetchHistory = canFetchHistory,
            indicatorsReady = indicatorsReady,
            ema20 = ema20,
            ema50 = ema50,
            rsi14 = rsi14,
            atr14 = atr14,
            bias = bias,
            candleSourceSummary = "1m:${f1.source}, 5m:${f5.source}, 15m:${f15.source}",
            updatedAt = now
        )
    }

    private fun ema(values: List<Double>, period: Int): Double? {
        if (values.size < period || period <= 1) return null
        var out = values.take(period).average()
        val k = 2.0 / (period + 1.0)
        for (i in period until values.size) {
            out = values[i] * k + out * (1.0 - k)
        }
        return out
    }

    private fun rsi(values: List<Double>, period: Int): Double? {
        if (values.size <= period) return null
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val diff = values[i] - values[i - 1]
            if (diff >= 0) gain += diff else loss += -diff
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        for (i in period + 1 until values.size) {
            val diff = values[i] - values[i - 1]
            val g = if (diff > 0) diff else 0.0
            val l = if (diff < 0) -diff else 0.0
            avgGain = ((avgGain * (period - 1)) + g) / period
            avgLoss = ((avgLoss * (period - 1)) + l) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun normalizeServerRoot(rawUrl: String): String {
        val url = rawUrl.trim().trimEnd('/')
        return when {
            url.endsWith("/api/mt5", ignoreCase = true) -> url.removeSuffix("/api/mt5")
            url.endsWith("/mt5", ignoreCase = true) -> url.removeSuffix("/mt5")
            else -> url
        }
    }

    private suspend fun buildRuntimeCoreContext(): String {
        val memoryCore = memoryManager.buildCoreMemoryContext()
        val serverRoot = normalizeServerRoot(_mt5BridgeBaseUrl.value)
        val mt5ApiBase = normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)
        val hasToken = _mt5AuthToken.value.isNotBlank()
        val paired = _mt5PairingStatus.value == "APPROVED" && hasToken
        val runtime = buildString {
            appendLine("")
            appendLine("[MT5_RUNTIME_CONTEXT]")
            appendLine("mt5_paired=$paired")
            appendLine("mt5_pairing_status=${_mt5PairingStatus.value}")
            appendLine("mt5_server_root=$serverRoot")
            appendLine("mt5_api_base=$mt5ApiBase")
            appendLine("mt5_has_auth_token=$hasToken")
            appendLine("mt5_last_sync_at=${_mt5LastSyncAt.value}")
            appendLine("mt5_positions_count=${_mt5Positions.value.size}")
            appendLine("mt5_orders_count=${_mt5Orders.value.size}")
            appendLine("mt5_symbols_cached=${_mt5Symbols.value.size}")
            appendLine("When user asks to place/close MT5 orders, use trading_mt5_order/trading_mt5_close_position directly without asking for endpoint or token.")
            appendLine("If mt5_paired=false, first tell user to connect/approve in MT5 dashboard.")
        }
        return memoryCore + runtime
    }

    private fun normalizeMt5ApiBase(rawUrl: String): String {
        val url = rawUrl.trim().trimEnd('/')
        return when {
            url.endsWith("/api/mt5", ignoreCase = true) -> url
            url.endsWith("/mt5", ignoreCase = true) -> url
            else -> "$url/api/mt5"
        }
    }

    private fun generateMt5ClientToken(): String {
        val bytes = Random.Default.nextBytes(24)
        val hex = bytes.joinToString("") { b -> ((b.toInt() and 0xFF).toString(16)).padStart(2, '0') }
        return "pab_${hex}_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
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
        val recentMsgs = withoutLatest
            .takeLast(maxMessages)
            .map { Pair(it.role, it.content) }
            .filter { it.second.isNotBlank() }

        // ป้องกัน Input Tokens ล้น (เช่น Claude Haiku limit 50K tokens)
        // จำกัด history ให้ไม่เกิน 20,000 characters (ประมาณ 5,000-7,000 tokens)
        val maxChars = 20000
        var totalChars = 0
        val result = mutableListOf<Pair<String, String>>()
        for (msg in recentMsgs.reversed()) {
            if (totalChars + msg.second.length > maxChars && result.isNotEmpty()) {
                break
            }
            totalChars += msg.second.length
            result.add(0, msg) // ใส่ที่หัวเพื่อให้ลำดับเก่า -> ใหม่เหมือนเดิม
        }
        return result
    }

    fun downloadLocalModel() {
        if (_modelDownloadProgress.value >= 0f && _modelDownloadProgress.value < 1f) return
        val downloader = onDownloadLocalModel ?: return

        viewModelScope.launch {
            try {
                _modelDownloadProgress.value = 0f
                logDebug("JarvisVM", "Starting model download via platform downloader")

                downloader(orchestrator.embeddingRegistry.localOnnx, { progress ->
                    _modelDownloadProgress.value = progress
                }, false)

                // Don't claim "Complete" until the OrtSession is actually loaded.
                // The downloader suspend-returns only after downloadModelFiles()
                // + loadModel() finish, so isAvailable() is the source of truth.
                val ready = orchestrator.embeddingRegistry.localOnnx.isAvailable()
                _modelDownloadProgress.value = if (ready) 2f else -1f
                logDebug("JarvisVM", "Model download finished (ready=$ready)")
            } catch (e: Exception) {
                logError("JarvisVM", "Download failed", e)
                _modelDownloadProgress.value = -1f
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAiTracking()
        stopMt5RealtimeChannel()
        stopCameraAnalysis()
        cameraService.release()
        pcmAudioEngine.release()
        voiceManager.shutdown()
        client.close()
    }
}

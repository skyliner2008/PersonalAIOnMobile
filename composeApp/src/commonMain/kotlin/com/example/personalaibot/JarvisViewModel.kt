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

data class Message(val role: String, val content: String, val isStatic: Boolean = false, val metadata: String? = null)

// Phase-1 refactor: ย้ายไป controller/Mt5Controller.kt — typealias คง import เดิมของ UI ไว้
typealias Mt5ClientRuntimeInfo = com.example.personalaibot.controller.Mt5ClientRuntimeInfo

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
    // Phase-5 refactor: Alert/ScheduledTask → controller
    val alert = com.example.personalaibot.controller.AlertController(viewModelScope, database, client, automationManager)
    private val mt5TerminalService = Mt5TerminalService(client)
    private val smcApiService = SmcApiService(client)
    // Phase-1 refactor: MT5/AI-Tracking ทั้งหมดย้ายไป controller — VM มี forwarders คง API เดิม
    val mt5 = com.example.personalaibot.controller.Mt5Controller(
        scope = viewModelScope,
        client = client,
        database = database,
        mt5TerminalService = mt5TerminalService,
        smcApiService = smcApiService,
        defaultBridgeBaseUrl = defaultMt5BridgeBaseUrl
    )
    // Phase-2 refactor: Chart/Dashboard ทั้งหมดย้ายไป controller — VM มี forwarders คง API เดิม
    val chart = com.example.personalaibot.controller.ChartController(viewModelScope, database, smcApiService)
    val autoTrading by lazy { AutoTradingViewModel(
        scope = viewModelScope,
        client = client,
        bridgeBaseUrlProvider = { mt5.mt5BridgeBaseUrl.value },
        authTokenProvider = { mt5.mt5AuthToken.value },
        pairingStatusProvider = { mt5.mt5PairingStatus.value }
    ) }
    private val plainJson = Json { ignoreUnknownKeys = true }

    // Phase-5 refactor: settings state → SettingsController (forwarders คง API เดิม)
    val apiKey: StateFlow<String> get() = settings.apiKey

    // ─── Alert / Scheduled Task → AlertController (forwarders คง API เดิม) ───
    val alertAiSummaryEnabled: StateFlow<Boolean> get() = alert.alertAiSummaryEnabled
    val alertVoiceEnabled: StateFlow<Boolean> get() = alert.alertVoiceEnabled
    val alertVoiceEngine: StateFlow<String> get() = alert.alertVoiceEngine
    val scheduledTasks get() = alert.scheduledTasks
    val alertTestRunning: StateFlow<Boolean> get() = alert.alertTestRunning
    val alertTestStatus: StateFlow<String> get() = alert.alertTestStatus
    val alertTestResults: StateFlow<List<com.example.personalaibot.automation.AlertDataTester.TestResult>> get() = alert.alertTestResults

    fun setAlertAiSummaryEnabled(enabled: Boolean) = alert.setAlertAiSummaryEnabled(enabled)
    fun setAlertVoiceEnabled(enabled: Boolean) = alert.setAlertVoiceEnabled(enabled)
    fun setAlertVoiceEngine(engine: String) = alert.setAlertVoiceEngine(engine)
    fun runAlertDataTest() = alert.runAlertDataTest()
    fun createAlert(name: String, symbol: String, toolName: String, field: String, op: String, value: String, interval: Long, delivery: String = "ai") = alert.createAlert(name, symbol, toolName, field, op, value, interval, delivery)
    fun createScheduledTask(name: String, prompt: String, type: String, runAt: Long, hhmm: String?) = alert.createScheduledTask(name, prompt, type, runAt, hhmm)

    val selectedModel: StateFlow<String> get() = settings.selectedModel
    val liveModelName: StateFlow<String> get() = settings.liveModelName
    val availableModels: StateFlow<List<GeminiModel>> get() = settings.availableModels

    private val orchestrator = JarvisOrchestrator(
        client = client,
        memoryManager = memoryManager,
        apiKey = "",
        modelName = com.example.personalaibot.data.ModelConfig.DEFAULT_MAIN_MODEL,
        liveModelName = com.example.personalaibot.data.ModelConfig.DEFAULT_LIVE_MODEL,
        automationManager = automationManager,
        fileHandler = fileHandler
    )

    // Phase-4 refactor: Chat pipeline ย้ายไป controller — VM มี forwarders คง API เดิม
    val chat = com.example.personalaibot.controller.ChatController(
        scope = viewModelScope,
        database = database,
        memoryManager = memoryManager,
        orchestrator = orchestrator,
        voiceManager = voiceManager,
        selectedModelProvider = { settings.selectedModel.value },
        apiKeyProvider = { settings.apiKey.value },
        coreContextProvider = { buildRuntimeCoreContext() },
        sleepCycleTrigger = { triggerSleepCycle() }
    )
    val messages: StateFlow<List<Message>> get() = chat.messages
    val isTyping: StateFlow<Boolean> get() = chat.isTyping

    // Phase-3 refactor: voice state → VoiceController (forwarders คง API เดิม)
    val isListening: StateFlow<Boolean> get() = voice.isListening
    val voiceError: StateFlow<String?> get() = voice.voiceError
    val liveConnectionState: StateFlow<com.example.personalaibot.data.ConnectionState> get() = voice.liveConnectionState

    private val _isSleeping = MutableStateFlow(false)
    val isSleeping: StateFlow<Boolean> = _isSleeping.asStateFlow()

    val floatingWidgetEnabled: StateFlow<Boolean> get() = settings.floatingWidgetEnabled

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

    private val _modelDownloadProgress = MutableStateFlow(-1f) // -1 = not downloading, 0..1 = progress, 2 = complete
    val modelDownloadProgress: StateFlow<Float> = _modelDownloadProgress.asStateFlow()
    
    // ─── Camera Analysis System ──────────────────────────────────────────────────
    val cameraService = CameraAnalysisService(client)
    // Phase-5 refactor: Settings → controller
    val settings = com.example.personalaibot.controller.SettingsController(
        scope = viewModelScope,
        database = database,
        memoryManager = memoryManager,
        orchestrator = orchestrator,
        cameraService = cameraService,
        client = client,
        preferFreeSync = { autoTrading.updatePreferFreeOnly(it) }
    )

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    // ─── Automation System ───────────────────────────────────────────────────────
    val activeJobs = automationManager.activeJobs

    val isMuted: StateFlow<Boolean> get() = voice.isMuted

    private val _showCameraScreen = MutableStateFlow(false)
    val showCameraScreen: StateFlow<Boolean> = _showCameraScreen.asStateFlow()

    private var cameraFrameJob: Job? = null
    
    private val _isUserSpeaking = MutableStateFlow(false)
    val isUserSpeaking: StateFlow<Boolean> = _isUserSpeaking.asStateFlow()

    private val _isAiVisionRequested = MutableStateFlow(false)
    val isAiVisionRequested: StateFlow<Boolean> = _isAiVisionRequested.asStateFlow()

    // ─── Charting System → ChartController (forwarders คง API เดิม) ───
    val showChart: StateFlow<Boolean> get() = chart.showChart
    val chartSymbol: StateFlow<String> get() = chart.chartSymbol
    val chartCandles: StateFlow<List<com.example.personalaibot.tools.trading.Candle>> get() = chart.chartCandles
    val chartSmcResult: StateFlow<com.example.personalaibot.tools.trading.SmcAnalysisResult?> get() = chart.chartSmcResult
    val chartSignalMarkers: StateFlow<List<com.example.personalaibot.automation.SignalMarkerProvider.SignalMarker>> get() = chart.chartSignalMarkers
    val chartInterval: StateFlow<String> get() = chart.chartInterval
    val chartLocale: StateFlow<String> get() = chart.chartLocale
    val chartHideSideToolbar: StateFlow<Boolean> get() = chart.chartHideSideToolbar
    val chartRefreshToken: StateFlow<Long> get() = chart.chartRefreshToken
    val chartViewMode: StateFlow<String> get() = chart.chartViewMode
    val chartLayout: StateFlow<String> get() = chart.chartLayout
    val chartOverlays: StateFlow<Set<String>> get() = chart.chartOverlays
    val chartDataLoading: StateFlow<Boolean> get() = chart.chartDataLoading
    val chartCardCache get() = chart.chartCardCache

    // ─── MT5 Trading Terminal ─────────────────────────────────────────────
    // ─── forwarders → Mt5Controller (คง public API เดิมให้ UI ไม่ต้องแก้) ───
    val showTradingTerminal: StateFlow<Boolean> get() = mt5.showTradingTerminal
    val mt5BridgeBaseUrl: StateFlow<String> get() = mt5.mt5BridgeBaseUrl
    val mt5AuthToken: StateFlow<String> get() = mt5.mt5AuthToken
    val mt5PairingStatus: StateFlow<String> get() = mt5.mt5PairingStatus
    val mt5IsSyncing: StateFlow<Boolean> get() = mt5.mt5IsSyncing
    val mt5LastSyncAt: StateFlow<Long> get() = mt5.mt5LastSyncAt
    val mt5ServerOnline: StateFlow<Boolean> get() = mt5.mt5ServerOnline
    val mt5CacheLoading: StateFlow<Boolean> get() = mt5.mt5CacheLoading
    val mt5Error: StateFlow<String?> get() = mt5.mt5Error
    val mt5ActionResult: StateFlow<String?> get() = mt5.mt5ActionResult
    val mt5Account: StateFlow<Mt5AccountInfo?> get() = mt5.mt5Account
    val mt5Symbols: StateFlow<List<Mt5SymbolInfo>> get() = mt5.mt5Symbols
    val mt5Positions: StateFlow<List<Mt5TradeItem>> get() = mt5.mt5Positions
    val mt5Orders: StateFlow<List<Mt5TradeItem>> get() = mt5.mt5Orders
    val mt5Deals: StateFlow<List<Mt5TradeItem>> get() = mt5.mt5Deals
    val mt5Clients: StateFlow<List<Mt5ClientRuntimeInfo>> get() = mt5.mt5Clients
    val mt5ClientsLoading: StateFlow<Boolean> get() = mt5.mt5ClientsLoading
    val mt5SelectedClientExe: StateFlow<String> get() = mt5.mt5SelectedClientExe
    val mt5TerminalFeed: StateFlow<List<String>> get() = mt5.mt5TerminalFeed
    val mt5DefaultLot: StateFlow<String> get() = mt5.mt5DefaultLot
    val mt5DefaultTpPoints: StateFlow<String> get() = mt5.mt5DefaultTpPoints
    val mt5DefaultSlPoints: StateFlow<String> get() = mt5.mt5DefaultSlPoints
    val mt5MaxDdPercent: StateFlow<String> get() = mt5.mt5MaxDdPercent
    val aiTrackingActive: StateFlow<Boolean> get() = mt5.aiTrackingActive
    val aiTrackingIntervalSec: StateFlow<Int> get() = mt5.aiTrackingIntervalSec
    val aiTrackingWatchlist: StateFlow<String> get() = mt5.aiTrackingWatchlist
    val aiTrackingInsights: StateFlow<List<AiTrackingInsight>> get() = mt5.aiTrackingInsights
    val aiTrackingFeed: StateFlow<List<String>> get() = mt5.aiTrackingFeed

    fun openTradingTerminal() = mt5.openTradingTerminal()
    fun closeTradingTerminal() = mt5.closeTradingTerminal()
    fun clearMt5ActionResult() = mt5.clearMt5ActionResult()
    fun clearMt5Error() = mt5.clearMt5Error()
    fun updateMt5BridgeBaseUrl(url: String) = mt5.updateMt5BridgeBaseUrl(url)
    fun updateMt5AuthToken(token: String) = mt5.updateMt5AuthToken(token)
    fun connectMt5Terminal(serverUrl: String) = mt5.connectMt5Terminal(serverUrl)
    fun disconnectMt5Terminal() = mt5.disconnectMt5Terminal()
    fun refreshMt5PairingStatus() = mt5.refreshMt5PairingStatus()
    fun toggleMt5Connection(serverUrl: String) = mt5.toggleMt5Connection(serverUrl)
    fun selectMt5ClientExe(exePath: String) = mt5.selectMt5ClientExe(exePath)
    fun refreshMt5Clients() = mt5.refreshMt5Clients()
    fun startSelectedMt5Client() = mt5.startSelectedMt5Client()
    fun stopSelectedMt5Client() = mt5.stopSelectedMt5Client()
    fun refreshMt5Terminal(historyLimit: Int = 200, waitMs: Int = 0) = mt5.refreshMt5Terminal(historyLimit, waitMs)
    fun placeMt5Order(action: String, symbol: String, volume: String, sl: String = "", tp: String = "", comment: String = "") = mt5.placeMt5Order(action, symbol, volume, sl, tp, comment)
    fun closeMt5Position(symbol: String = "", ticket: String = "") = mt5.closeMt5Position(symbol, ticket)
    fun closeMt5AllPositions(side: String = "ALL") = mt5.closeMt5AllPositions(side)
    fun modifyMt5Position(symbol: String, ticket: String, sl: String = "", tp: String = "") = mt5.modifyMt5Position(symbol, ticket, sl, tp)
    fun setMt5BreakEvenAll() = mt5.setMt5BreakEvenAll()
    fun setMt5DefaultLot(value: String) = mt5.setMt5DefaultLot(value)
    fun setMt5DefaultTpPoints(value: String) = mt5.setMt5DefaultTpPoints(value)
    fun setMt5DefaultSlPoints(value: String) = mt5.setMt5DefaultSlPoints(value)
    fun setMt5MaxDdPercent(value: String) = mt5.setMt5MaxDdPercent(value)
    fun updateAiTrackingIntervalSec(seconds: Int) = mt5.updateAiTrackingIntervalSec(seconds)
    fun updateAiTrackingWatchlist(input: String) = mt5.updateAiTrackingWatchlist(input)
    fun startAiTracking() = mt5.startAiTracking()
    fun stopAiTracking() = mt5.stopAiTracking()

    private var visionTimeoutJob: Job? = null

    // Phase-3 refactor: Live Voice ย้ายไป controller — VM มี forwarders คง API เดิม
    val voice = com.example.personalaibot.controller.VoiceController(
        scope = viewModelScope,
        orchestrator = orchestrator,
        voiceManager = voiceManager,
        messages = chat.messagesMutable,
        coreContextProvider = { buildRuntimeCoreContext() },
        onUserSpeakingChanged = { speaking ->
            if (speaking != _isUserSpeaking.value) {
                _isUserSpeaking.value = speaking
                cameraService.isUserSpeaking = speaking
            }
        }
    )

    init {
        // Track winning live model; do NOT silently overwrite user's DB settings during transient runtime fallback
        orchestrator.onLiveModelChanged = { winningModel ->
            logDebug("JarvisVM", "Live session active with model: $winningModel")
        }

        // Bridge camera frames to the unified Live session in the orchestrator
        cameraService.onLiveFrameReady = { jpegBase64 ->
            orchestrator.sendLiveCameraFrame(jpegBase64)
        }

        // Initialize Camera Tool Executor
        ToolExecutor.initCameraExecutor(CameraToolExecutor(cameraService))
        ToolExecutor.setMt5RuntimeConfigProvider { mt5.currentRuntimeConfig() }
        com.example.personalaibot.automation.TradingSignalMarketDataRouter.configure(
            liveModeProvider = { com.example.personalaibot.tools.trading.auto.AutoTradingConfigStore.load().enableLiveTrading },
            mt5ConnectedProvider = {
                val runtime = mt5.currentRuntimeConfig()
                runtime.pairingStatus == "APPROVED" && runtime.authToken.isNotBlank()
            },
            mt5CandleProvider = { symbol, timeframe, count -> mt5.fetchCandlesForSignal(symbol, timeframe, count) }
        )

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
                
                updateSettings(apiKey.value, selectedModel.value, liveModelName.value, newVoice)
                // ผูกเสียงเข้ากับ identity (เพศ/น้ำเสียง/คำลงท้าย) + persist ลง Core Memory
                orchestrator.applyVoiceIdentity(newVoice)

                // Immediate Apply: Restart session if active
                if (voice.isListening.value) {
                    stopVoiceInput()
                    delay(800) // เพิ่ม delay เล็กน้อยเพื่อให้ระบบเคลียร์ resources และบันทึกความจำได้ทัน
                    // ตั้ง greeting ให้ AI พูดยืนยันเสียงใหม่อัตโนมัติทันทีที่ session READY (ภาษาไทยล้วน ไม่มี [SYSTEM])
                    orchestrator.setLiveGreetingOnReady("เปลี่ยนมาใช้เสียง $newVoice แล้ว ลองทักทายสั้นๆ ด้วยเสียงใหม่นี้")
                    startVoiceInput()
                }
            }
        }

        // --- AI-Controlled Chart Dashboard ---
        orchestrator.setChartControlHandler { args ->
            val result = applyChartControl(args)
            // Live mode: โมเดลเสียงตอบเป็นเสียงอย่างเดียว (ห้าม markdown) จึงไม่มี ```chart fence
            // → สร้างการ์ดกราฟให้เองเมื่อสั่ง open ระหว่าง live session (chat mode โมเดลแนบ fence มาเอง)
            if (args["action"]?.trim()?.lowercase() == "open" && voice.isListening.value) {
                val fence = buildString {
                    append("```chart\n")
                    append("""{"symbol":"${chart.chartSymbol.value}","interval":"${chart.chartInterval.value}","layout":"${chart.chartLayout.value}"""")
                    if (chart.chartOverlays.value.isNotEmpty()) {
                        append(""","overlays":[""")
                        append(chart.chartOverlays.value.joinToString(",") { "\"$it\"" })
                        append("]")
                    }
                    append("}\n```")
                }
                chat.messagesMutable.value = chat.messagesMutable.value + Message("model", fence)
                // persist ลง DB ด้วย — ไม่งั้นปิด/เปิดแอปใหม่แล้วการ์ดกราฟที่สั่งผ่าน live จะหาย
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { memoryManager.storeMessage("model", fence) }
                }
            }
            result
        }

        viewModelScope.launch {
            loadSettings()
        }

        viewModelScope.launch {
            isAiVisionRequested.collect { requested ->
                cameraService.isAiVisionRequested = requested
            }
        }
        // ChartStateManager collectors ย้ายไป ChartController.init แล้ว (Phase-2)
    }

    val voiceName: StateFlow<String> get() = settings.voiceName

    private suspend fun loadSettings() {
        // Phase-5 refactor: settings/alert/chart/mt5 โหลดใน controller ของตัวเอง
        settings.loadPersistedSettings()
        alert.loadPersistedSettings()
        chart.loadPersistedSettings()
        // Phase-1 refactor: MT5/AI-Tracking settings โหลดใน Mt5Controller.loadPersistedSettings()
        val savedMt5AuthToken = mt5.loadPersistedSettings()
        chat.loadHistory()
        // รับข้อความจาก background service (alert/scheduled task) แบบ real-time —
        // เดิม service ลง DB อย่างเดียว แชทที่เปิดอยู่ไม่เห็นจนกว่าจะเปิดแอปใหม่
        viewModelScope.launch {
            com.example.personalaibot.memory.AlertChatBus.incoming.collect { push ->
                chat.messagesMutable.value = chat.messagesMutable.value + Message(push.role, push.content, metadata = push.metadata)
            }
        }
        // รับผลงานพื้นหลัง (backtest/optimize/evolve จาก LongTaskRunner) เมื่อเสร็จ:
        //  - live เปิดอยู่ → การ์ดลงแชท + ส่งเข้า live session ให้ AI พูดสรุปเอง
        //  - live ปิดอยู่ → ส่งต่อให้ automation service ประกาศ (notification + เสียง + การ์ดแชท)
        viewModelScope.launch {
            com.example.personalaibot.automation.backtest.LongTaskRunner.completions.collect { c ->
                logDebug("JarvisVM", "📦 LongTask เสร็จ: ${c.title} (ok=${c.ok}, live=${voice.isListening.value})")
                // log ผลลัพธ์เต็มใต้ tag JarvisVM (tag ที่ logcat ของผู้ใช้จับอยู่แล้ว)
                // — tag "Backtest"/"LongTask" ไม่ขึ้นใน capture ของผู้ใช้ (capture filter บาง tag)
                // logDebug แบ่ง chunk 3500 bytes ให้อัตโนมัติ body ยาวก็ครบ
                if (c.ok) logDebug("JarvisVM", "📦 ผลลัพธ์เต็ม [${c.title}]:\n${c.chatBody}")
                if (voice.isListening.value) {
                    // การ์ดรายละเอียดลงแชทก่อน แล้วให้ live model พูดสรุป
                    runCatching {
                        memoryManager.storeMessage("assistant", c.chatBody, metadata = c.chatMeta)
                        com.example.personalaibot.memory.AlertChatBus.tryEmit("assistant", c.chatBody, c.chatMeta)
                    }
                    val sentToLive = runCatching {
                        // realtimeInput — model ตอบเองได้ขณะ stream audio (clientContent จะเงียบ — พิสูจน์แล้ว)
                        orchestrator.sendLiveRealtimeTextWhenReady(
                            "[SYSTEM] งานพื้นหลังเสร็จแล้ว: ${c.title}\nสรุปผล: ${c.speech}\n" +
                                "โปรดพูดแจ้งผู้ใช้แบบสนทนา 2-4 ประโยคว่างานเสร็จแล้วและผลเป็นอย่างไร " +
                                "(มีการ์ดรายละเอียดลงในแชทแล้ว ไม่ต้องอ่านตาราง/ตัวเลขยาวๆ)"
                        )
                    }.getOrElse {
                        logError("JarvisVM", "ส่งผลเข้า live ไม่สำเร็จ: ${it.message}", it)
                        false
                    }
                    if (!sentToLive) {
                        // live เปิดค้างแต่ session ตาย/กำลัง reconnect (เช่นโดน GoAway) — กันผลหายเงียบๆ
                        logDebug("JarvisVM", "📦 live session ไม่พร้อม — fallback ประกาศผลผ่าน notification/เสียงแทน")
                        com.example.personalaibot.automation.announceLongTaskCompletion(
                            title = "✅ ${c.title}",
                            cardBody = c.chatBody,
                            metaJson = c.chatMeta,
                            shortSpeech = c.speech,
                            fullSpeech = c.speech
                        )
                    }
                } else {
                    com.example.personalaibot.automation.announceLongTaskCompletion(
                        title = "✅ ${c.title}",
                        cardBody = c.chatBody,
                        metaJson = c.chatMeta,
                        shortSpeech = c.speech,
                        fullSpeech = c.speech
                    )
                }
            }
        }
        // โหลด Agent/User Identity กลับจาก Core Memory — เดิม loadFromCoreMemory ไม่มี caller
        // ทำให้เปิดแอปใหม่แล้ว identity กลับเป็นค่า default ("ผู้ใช้") ทุกครั้ง
        runCatching {
            val coreMap = memoryManager.getCoreMemoryMap()
            if (coreMap.isNotEmpty()) {
                com.example.personalaibot.ai.JarvisPersona.loadFromCoreMemory(coreMap)
                logDebug("JarvisVM", "Identity loaded from Core Memory: user=${com.example.personalaibot.ai.JarvisPersona.identity.userName}, agent=${com.example.personalaibot.ai.JarvisPersona.identity.agentName}")
            }
        }.onFailure { logError("JarvisVM", "Identity load from Core Memory failed", it) }
        mt5.loadMt5FromDatabase()
        if (savedMt5AuthToken.isNotBlank()) {
            mt5.refreshMt5PairingStatus()
            mt5.startMt5RealtimeChannel()
        }
    }

    // ─── Settings function forwarders → SettingsController ───
    fun selectVoiceProfile(voice: String) = settings.selectVoiceProfile(voice)
    fun updateSettings(key: String, model: String, liveModel: String = settings.liveModelName.value, voice: String = settings.voiceName.value, preferFree: Boolean = false) = settings.updateSettings(key, model, liveModel, voice, preferFree)
    fun setFloatingWidgetEnabled(enabled: Boolean) = settings.setFloatingWidgetEnabled(enabled)
    suspend fun getModelsForProvider(providerId: String, freeOnly: Boolean = false, apiKeyOverride: String? = null): List<com.example.personalaibot.data.providers.LlmModelInfo> = settings.getModelsForProvider(providerId, freeOnly, apiKeyOverride)
    suspend fun getLiveCapableModels(providerId: String = "gemini", apiKeyOverride: String? = null): List<com.example.personalaibot.data.providers.LlmModelInfo> = settings.getLiveCapableModels(providerId, apiKeyOverride)
    suspend fun testApiKey(providerId: String, apiKeyOverride: String? = null): com.example.personalaibot.data.providers.ApiKeyTester.TestResult = settings.testApiKey(providerId, apiKeyOverride)

    // ─── Chat forwarders → ChatController ───
    fun sendMessage(text: String, speakResponse: Boolean = false, attachments: List<com.example.personalaibot.ui.ChatAttachment> = emptyList()) = chat.sendMessage(text, speakResponse, attachments)


    // ─── Settings state forwarders → SettingsController ───
    val openaiApiKey: StateFlow<String> get() = settings.openaiApiKey
    val claudeApiKey: StateFlow<String> get() = settings.claudeApiKey
    val openRouterApiKey: StateFlow<String> get() = settings.openRouterApiKey
    val minimaxApiKey: StateFlow<String> get() = settings.minimaxApiKey
    val groqApiKey: StateFlow<String> get() = settings.groqApiKey
    val nvidiaNimApiKey: StateFlow<String> get() = settings.nvidiaNimApiKey
    val geminiFallbackModels: StateFlow<List<String>> get() = settings.geminiFallbackModels
    val showFreeModelsOnly: StateFlow<Boolean> get() = settings.showFreeModelsOnly
    val modelCaps: StateFlow<Map<String, com.example.personalaibot.data.providers.ModelAutoTester.ModelCapability>> get() = settings.modelCaps
    val geminiApiKeys: StateFlow<List<String>> get() = settings.geminiApiKeys

    fun setShowFreeModelsOnly(v: Boolean) = settings.setShowFreeModelsOnly(v)
    suspend fun autoTestProviderModels(providerId: String, onProgress: (current: Int, total: Int, modelId: String) -> Unit = { _, _, _ -> }): Int = settings.autoTestProviderModels(providerId, onProgress)
    fun addGeminiApiKey(key: String) = settings.addGeminiApiKey(key)
    fun removeGeminiApiKey(key: String) = settings.removeGeminiApiKey(key)
    fun editGeminiApiKey(oldKey: String, newKey: String) = settings.editGeminiApiKey(oldKey, newKey)
    fun updateGeminiFallbackModels(models: List<String>) = settings.updateGeminiFallbackModels(models)
    fun updateExternalApiKeys(openai: String = settings.openaiApiKey.value, claude: String = settings.claudeApiKey.value, openRouter: String = settings.openRouterApiKey.value, minimax: String = settings.minimaxApiKey.value, groq: String = settings.groqApiKey.value, nvidiaNim: String = settings.nvidiaNimApiKey.value) = settings.updateExternalApiKeys(openai, claude, openRouter, minimax, groq, nvidiaNim)
    fun updateIdentity(agentName: String, agentCreature: String, agentVibe: String, agentGender: String, userName: String, userCallName: String, userNotes: String) = settings.updateIdentity(agentName, agentCreature, agentVibe, agentGender, userName, userCallName, userNotes)

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

    fun toggleMute() = voice.toggleMute()

    fun openCameraScreen() {
        _showCameraScreen.value = true
        // Initialize camera service with current API keys
        viewModelScope.launch {
            cameraService.updateProviderKeys(
                geminiApiKey = settings.apiKey.value,
                openaiApiKey = settings.openaiApiKey.value,
                claudeApiKey = settings.claudeApiKey.value
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

    // ─── Voice function forwarders → VoiceController ───
    fun startVoiceInput() = voice.startVoiceInput()
    fun stopVoiceInput() = voice.stopVoiceInput()
    fun clearVoiceError() = voice.clearVoiceError()

    fun clearChat() = chat.clearChat()

    // ─── Charting Actions ─────────────────────────────────────────────────────────────
    
    // ─── Chart function forwarders → ChartController ───
    fun openChart(symbol: String, candles: List<com.example.personalaibot.tools.trading.Candle>, smc: com.example.personalaibot.tools.trading.SmcAnalysisResult? = null) = chart.openChart(symbol, candles, smc)
    fun openChart() = chart.openChart()
    fun updateChartSymbol(symbol: String) = chart.updateChartSymbol(symbol)
    fun updateChartInterval(interval: String) = chart.updateChartInterval(interval)
    fun refreshChart() = chart.refreshChart()
    fun updateChartLocale(locale: String) = chart.updateChartLocale(locale)
    fun setChartHideSideToolbar(hidden: Boolean) = chart.setChartHideSideToolbar(hidden)
    fun setChartViewMode(mode: String) = chart.setChartViewMode(mode)
    fun setChartLayout(layout: String) = chart.setChartLayout(layout)
    fun toggleChartOverlay(name: String) = chart.toggleChartOverlay(name)
    fun refreshChartCandles(force: Boolean = false) = chart.refreshChartCandles(force)
    fun ensureChartCardData(symbol: String, interval: String, needsSmc: Boolean, needsMarkers: Boolean = false) = chart.ensureChartCardData(symbol, interval, needsSmc, needsMarkers)
    fun openChartWithConfig(symbol: String, interval: String, layout: String, overlays: Set<String>) = chart.openChartWithConfig(symbol, interval, layout, overlays)
    fun closeChart() = chart.closeChart()
    fun applyChartControl(args: Map<String, String>): String = chart.applyChartControl(args)

    fun handleChartCapture(base64Image: String) {
        logDebug("JarvisVM", "Chart captured! Processing with Gemini Vision...")
        // Phase 4 logic will go here: submit to orchestrator for visual analysis
        sendMessage("วิเคราะห์ภาพกราฟนี้ให้หน่อยครับ (Capture จากระบบจาร์วิส)")
        viewModelScope.launch {
            cameraService.onLiveFrameReady?.invoke(base64Image) // Wrap in launch for suspend call
        }
    }

    private suspend fun buildRuntimeCoreContext(): String {
        // Phase-1 refactor: MT5 runtime context ย้ายไป Mt5Controller.runtimeContextSnippet()
        return memoryManager.buildCoreMemoryContext() + mt5.runtimeContextSnippet()
    }

    fun triggerSleepCycle() {
        if (_isSleeping.value) return
        _isSleeping.value = true
        viewModelScope.launch {
            val success = orchestrator.performSleepCycle()
            if (success) {
                // โหลดประวัติใหม่เพราะข้อความเก่าถูกลบไปรวมยอดแล้ว
                chat.loadHistory()
            }
            _isSleeping.value = false
        }
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
        mt5.shutdown()
        stopCameraAnalysis()
        cameraService.release()
        voice.shutdown()
        voiceManager.shutdown()
        client.close()
    }
}

package com.example.personalaibot.camera

import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.data.LiveGeminiService
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import io.ktor.client.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

// ═══════════════════════════════════════════════════════════════════
// CameraAnalysisService — Orchestrator สำหรับระบบกล้อง Real-time
//
// จัดการ:
//   1. Multi-Provider routing (Gemini / OpenAI / Claude)
//   2. Adaptive FPS control
//   3. AR Overlay data aggregation
//   4. Live Stream + Snapshot + Object Detection modes
//
// Architecture:
//   CameraX (Android) → JPEG Base64 → CameraAnalysisService
//     → Route ไปยัง active CameraProvider
//     → ผลลัพธ์ → UI (AR overlay / chat bubble)
// ═══════════════════════════════════════════════════════════════════

class CameraAnalysisService(
    private val client: HttpClient
) {
    // ─── Providers (สร้าง lazily เมื่อมี API key) ───────────────────

    private var geminiService: GeminiService? = null
    // REMOVED: Managed by Orchestrator for unified session
    // private var liveService: LiveGeminiService? = null

    private var geminiProvider: GeminiCameraProvider? = null
    private val openAiProvider = OpenAICameraProvider(client)
    private val claudeProvider = ClaudeCameraProvider(client)

    /** Callback สำหรับส่ง frame เข้าสู่ Live session หลัก */
    var onLiveFrameReady: (suspend (String) -> Unit)? = null

    /**
     * อัพเดท API key ทั้งหมด — เรียกจาก ViewModel ตอน init / settings change
     */
    fun updateProviderKeys(geminiApiKey: String, openaiApiKey: String, claudeApiKey: String) {
        updateApiKeys(gemini = geminiApiKey, openAi = openaiApiKey, claude = claudeApiKey)

        // Rebuild Gemini provider if key changed
        if (geminiApiKey.isNotBlank()) {
            val gs = GeminiService(client, geminiApiKey, "gemini-2.5-flash")
            geminiService = gs
            
            val gp = GeminiCameraProvider(gs)
            gp.onLiveFrameReady = { frame -> onLiveFrameReady?.invoke(frame) }
            geminiProvider = gp
        }
    }

    fun release() {
        providers.values.toSet().forEach {
            try { /* providers released in stop() */ } catch (_: Exception) {}
        }
    }

    private val providers: Map<CameraProviderType, CameraProvider>
        get() = buildMap {
            geminiProvider?.let { gp ->
                put(CameraProviderType.GEMINI_LIVE, gp)
                put(CameraProviderType.GEMINI_FLASH, gp)
            }
            put(CameraProviderType.OPENAI_GPT4O, openAiProvider)
            put(CameraProviderType.OPENAI_GPT41, openAiProvider)
            put(CameraProviderType.CLAUDE_SONNET, claudeProvider)
            put(CameraProviderType.CLAUDE_OPUS, claudeProvider)
        }

    // ─── State ───────────────────────────────────────────────────────

    private val _activeProvider = MutableStateFlow(CameraProviderType.GEMINI_LIVE)
    val activeProvider: StateFlow<CameraProviderType> = _activeProvider.asStateFlow()

    private val _cameraMode = MutableStateFlow(CameraMode.LIVE_STREAM)
    val cameraMode: StateFlow<CameraMode> = _cameraMode.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _latestResult = MutableStateFlow<CameraAnalysisResult?>(null)
    val latestResult: StateFlow<CameraAnalysisResult?> = _latestResult.asStateFlow()

    private val _detectedObjects = MutableStateFlow<List<DetectedObject>>(emptyList())
    val detectedObjects: StateFlow<List<DetectedObject>> = _detectedObjects.asStateFlow()

    private val _overlayLabels = MutableStateFlow<List<AnalysisLabel>>(emptyList())
    val overlayLabels: StateFlow<List<AnalysisLabel>> = _overlayLabels.asStateFlow()

    private val _analysisHistory = MutableStateFlow<List<CameraAnalysisResult>>(emptyList())
    val analysisHistory: StateFlow<List<CameraAnalysisResult>> = _analysisHistory.asStateFlow()

    val fpsController = AdaptiveFpsController()
    private var lastLiveFrameTime = 0L
    private var lastFrameCache: String? = null // Cache for Tool consumption (Snapshot)
    private var burstCount = 0 // นับจำนวนเฟรมที่ส่งแบบรัวในช่วงเริ่มต้น

    var isUserSpeaking = false
    var isAiVisionRequested = false

    // API Keys (ตั้งค่าจาก Settings)
    private var geminiApiKey: String = ""
    private var openAiApiKey: String = ""
    private var claudeApiKey: String = ""

    // ─── Configuration ───────────────────────────────────────────────

    fun updateApiKeys(gemini: String = "", openAi: String = "", claude: String = "") {
        if (gemini.isNotBlank()) geminiApiKey = gemini
        if (openAi.isNotBlank()) openAiApiKey = openAi
        if (claude.isNotBlank()) claudeApiKey = claude
    }

    /**
     * เปลี่ยน active provider
     */
    suspend fun switchProvider(type: CameraProviderType) {
        _activeProvider.value = type
        val provider = providers[type] ?: return

        val (apiKey, model) = when (type) {
            CameraProviderType.GEMINI_LIVE  -> geminiApiKey to "gemini-3.1-flash-live-preview"
            CameraProviderType.GEMINI_FLASH -> geminiApiKey to "gemini-2.5-flash"
            CameraProviderType.OPENAI_GPT4O -> openAiApiKey to "gpt-4o"
            CameraProviderType.OPENAI_GPT41 -> openAiApiKey to "gpt-4.1"
            CameraProviderType.CLAUDE_SONNET -> claudeApiKey to "claude-sonnet-4-6"
            CameraProviderType.CLAUDE_OPUS  -> claudeApiKey to "claude-opus-4-6"
        }

        provider.initialize(CameraProviderConfig(
            apiKey = apiKey,
            modelName = model,
            language = "th"
        ))

        logDebug("CameraService", "Switched to provider: ${type.displayName}")
    }

    /**
     * เปลี่ยนโหมดกล้อง
     */
    fun switchMode(mode: CameraMode) {
        _cameraMode.value = mode
        logDebug("CameraService", "Camera mode: $mode")
    }

    // ─── Camera Operations ───────────────────────────────────────────

    val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * เริ่มระบบกล้อง — Guard: ไม่เรียกซ้ำถ้าทำงานอยู่แล้ว
     */
    fun start() {
        if (_isActive.value) {
            logDebug("CameraService", "Camera already active, skipping redundant start()")
            return
        }
        _isActive.value = true
        fpsController.reset()
        serviceScope.launch { switchProvider(_activeProvider.value) }
        logDebug("CameraService", "Camera started: ${_activeProvider.value.displayName} @ ${_cameraMode.value}")
    }

    /**
     * หยุดระบบกล้อง
     */
    fun stop() {
        _isActive.value = false
        isAiVisionRequested = false
        lastLiveFrameTime = 0L // Reset framing interval
        serviceScope.launch {
            providers.values.toSet().forEach { it.release() }
        }
        fpsController.reset()
        _detectedObjects.value = emptyList()
        _overlayLabels.value = emptyList()
        logDebug("CameraService", "Camera stopped")
    }

    /**
     * ส่ง frame จากกล้อง — เป็น entry point หลัก
     * route ไปตาม mode + provider อัตโนมัติ
     *
     * @param jpegBase64 ภาพ JPEG เข้ารหัส Base64
     * @param rawBytes   raw JPEG bytes (สำหรับ motion detection)
     */
    suspend fun onCameraFrame(jpegBase64: String, rawBytes: ByteArray?) {
        if (!_isActive.value) return
        lastFrameCache = jpegBase64 // Always cache the freshest frame for Tool/Snapshot fallback

        // 1. Adaptive FPS update
        rawBytes?.let { fpsController.onNewFrame(it) }

        // 2. Route ตาม mode (พร้อม Throttling สำหรับ Live View)
        when (_cameraMode.value) {
            CameraMode.LIVE_STREAM   -> {
                val now = Clock.System.now().toEpochMilliseconds()

                // Privacy-First Vision Logic:
                // Only transmit frames IF AND ONLY IF the AI or user explicitly enabled AI Vision (Eye Open 👁️)
                
                val interval = if (isAiVisionRequested) {
                    fpsController.getFrameDelayMs()
                } else {
                    Long.MAX_VALUE // 0 FPS (Blocked)
                }

                if (interval != Long.MAX_VALUE && (now - lastLiveFrameTime) >= interval) {
                    lastLiveFrameTime = now
                    handleLiveStream(jpegBase64)
                }
            }
            CameraMode.SNAPSHOT      -> { /* ไม่ส่งอัตโนมัติ — รอ user กด capture */ }
            CameraMode.OBJECT_DETECT -> handleObjectDetection(jpegBase64)
            CameraMode.AR_OVERLAY    -> handleArOverlay(jpegBase64)
        }
    }

    /**
     * Snapshot mode — กดถ่ายภาพแยกวิเคราะห์
     */
    suspend fun captureAndAnalyze(jpegBase64: String, prompt: String = ""): CameraAnalysisResult {
        val provider = getActiveProvider() ?: return errorResult("No active provider")

        // If passed frame is empty/blank, fallback to the latest cached frame
        val targetFrame = if (jpegBase64.isBlank()) lastFrameCache ?: "" else jpegBase64
        
        if (targetFrame.isBlank()) {
            return errorResult("No image available. Please ensure the camera is active.")
        }

        val result = provider.analyzeFrame(targetFrame, prompt)
        _latestResult.value = result
        addToHistory(result)

        // Update overlay labels
        _overlayLabels.value = result.labels

        return result
    }

    // ─── Internal Handlers ───────────────────────────────────────────

    private suspend fun handleLiveStream(jpegBase64: String) {
        val provider = getActiveProvider() ?: return

        if (provider.supportsLiveStream) {
            // Gemini Live → ส่ง frame ผ่าน WebSocket (ไม่ต้องรอ response)
            provider.sendLiveFrame(jpegBase64)
        } else {
            // OpenAI / Claude → analyzeFrame (snapshot style ที่ FPS ต่ำ)
            val result = provider.analyzeFrame(jpegBase64)
            _latestResult.value = result
            _overlayLabels.value = result.labels
            addToHistory(result)
        }
    }

    private suspend fun handleObjectDetection(jpegBase64: String) {
        val provider = getActiveProvider() ?: return

        try {
            val objects = provider.detectObjects(jpegBase64)
            _detectedObjects.value = objects

            // สร้าง labels จาก detected objects
            val labels = objects.map { obj ->
                AnalysisLabel(
                    text = "${obj.label} (${(obj.confidence * 100).toInt()}%)",
                    category = LabelCategory.OBJECT,
                    position = LabelPosition.TOP_LEFT
                )
            }
            _overlayLabels.value = labels
        } catch (e: Exception) {
            logError("CameraService", "Object detection error", e)
        }
    }

    private suspend fun handleArOverlay(jpegBase64: String) {
        val provider = getActiveProvider() ?: return

        try {
            // ทำทั้ง analysis + object detection พร้อมกัน
            val result = provider.analyzeFrame(jpegBase64, "วิเคราะห์ภาพ + ระบุวัตถุพร้อมตำแหน่ง")
            val objects = provider.detectObjects(jpegBase64)

            _latestResult.value = result
            _detectedObjects.value = objects

            // Merge labels
            val allLabels = result.labels + objects.map { obj ->
                AnalysisLabel(
                    text = "${obj.label} ${(obj.confidence * 100).toInt()}%",
                    category = LabelCategory.OBJECT
                )
            }
            _overlayLabels.value = allLabels
            addToHistory(result)
        } catch (e: Exception) {
            logError("CameraService", "AR overlay error", e)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun getActiveProvider(): CameraProvider? {
        return providers[_activeProvider.value]
    }

    private fun addToHistory(result: CameraAnalysisResult) {
        val current = _analysisHistory.value.toMutableList()
        current.add(0, result)
        if (current.size > 20) current.removeLast() // Keep last 20
        _analysisHistory.value = current
    }

    private fun errorResult(msg: String) = CameraAnalysisResult(
        provider = _activeProvider.value,
        description = "Error: $msg",
        timestamp = Clock.System.now().toEpochMilliseconds()
    )

    /**
     * ดึง provider ทั้งหมดที่ตั้งค่า API key แล้ว
     */
    fun getAvailableProviders(): List<CameraProviderType> {
        val available = mutableListOf<CameraProviderType>()
        if (geminiApiKey.isNotBlank()) {
            available.add(CameraProviderType.GEMINI_LIVE)
            available.add(CameraProviderType.GEMINI_FLASH)
        }
        if (openAiApiKey.isNotBlank()) {
            available.add(CameraProviderType.OPENAI_GPT4O)
            available.add(CameraProviderType.OPENAI_GPT41)
        }
        if (claudeApiKey.isNotBlank()) {
            available.add(CameraProviderType.CLAUDE_SONNET)
            available.add(CameraProviderType.CLAUDE_OPUS)
        }
        return available
    }
}

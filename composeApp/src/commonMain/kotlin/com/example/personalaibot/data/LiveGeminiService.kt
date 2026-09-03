package com.example.personalaibot.data

import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import com.example.personalaibot.tools.GeminiTool
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.util.decodeBase64Bytes
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// ═══════════════════════════════════════════════════════════════════
// v1beta BidiGenerateContent Wire Format (camelCase per API reference)
//
// Client→Server top-level one-of fields:
//   "setup", "clientContent", "realtimeInput", "toolResponse"
//
// Reference: https://ai.google.dev/api/live
// ═══════════════════════════════════════════════════════════════════

// ── Setup (first message) ────────────────────────────────────────

@Serializable
data class LiveSetupMessage(
    @SerialName("setup") val setup: LiveSetup
)

@Serializable
data class LiveSetup(
    val model: String,
    @SerialName("generation_config") val generationConfig: LiveGenerationConfig? = null,
    @SerialName("system_instruction") val systemInstruction: LiveSystemInstruction? = null,
    // ต้องส่ง {} เปล่าใน setup ถึงจะได้ transcript กลับมา (ตาม Live API guide)
    // — ถ้าไม่ส่ง แชทใน Live mode จะไม่มีข้อความและไม่มีอะไรเก็บลง DB
    @SerialName("output_audio_transcription") val outputAudioTranscription: JsonObject? = null,
    @SerialName("input_audio_transcription") val inputAudioTranscription: JsonObject? = null,
    @SerialName("realtime_input_config") val realtimeInputConfig: LiveRealtimeInputConfig? = null,
    @SerialName("session_resumption") val sessionResumption: LiveSessionResumptionConfig? = null,
    @SerialName("context_window_compression") val contextWindowCompression: LiveContextWindowCompressionConfig? = null,
    val tools: List<GeminiTool>? = null
)

@Serializable
data class LiveRealtimeInputConfig(
    @SerialName("automatic_activity_detection") val automaticActivityDetection: LiveAutomaticActivityDetection? = null
)

@Serializable
data class LiveAutomaticActivityDetection(
    val disabled: Boolean = false,
    @SerialName("start_of_speech_sensitivity") val startOfSpeechSensitivity: String? = null,
    @SerialName("end_of_speech_sensitivity") val endOfSpeechSensitivity: String? = null,
    @SerialName("prefix_padding_ms") val prefixPaddingMs: Int? = null,
    @SerialName("silence_duration_ms") val silenceDurationMs: Int? = null
)

@Serializable
data class LiveSessionResumptionConfig(
    val handle: String? = null
)

@Serializable
data class LiveContextWindowCompressionConfig(
    val slidingWindow: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class LiveSystemInstruction(
    val parts: List<LivePart>
)

@Serializable
data class LiveGenerationConfig(
    @SerialName("response_modalities") val responseModalities: List<String>? = null,
    @SerialName("speech_config") val speechConfig: LiveSpeechConfig? = null,
    @SerialName("thinking_config") val thinkingConfig: LiveThinkingConfig? = null
)

@Serializable
data class LiveThinkingConfig(
    @SerialName("thinking_level") val thinkingLevel: String? = null,
    @SerialName("include_thoughts") val includeThoughts: Boolean? = null
)

@Serializable
data class LiveSpeechConfig(
    @SerialName("voice_config") val voiceConfig: LiveVoiceConfig? = null
)

@Serializable
data class LiveVoiceConfig(
    @SerialName("prebuilt_voice_config") val prebuiltVoiceConfig: LivePrebuiltVoiceConfig? = null
)

@Serializable
data class LivePrebuiltVoiceConfig(
    @SerialName("voice_name") val voiceName: String? = null
)

// ── RealtimeInput (audio / video / text streaming) ───────────────

@Serializable
data class LiveRealtimeInputMessage(
    @SerialName("realtimeInput") val realtimeInput: LiveRealtimeInputData
)

@Serializable
data class LiveRealtimeInputData(
    val audio: LiveBlob? = null,
    val video: LiveBlob? = null,
    val text: String? = null
)

@Serializable
data class LiveBlob(
    @SerialName("mimeType") val mimeType: String,
    val data: String   // Base64
)

// ── ClientContent (text turns) ───────────────────────────────────

@Serializable
data class LiveClientContentMessage(
    @SerialName("clientContent") val clientContent: LiveContentWrapper
)

@Serializable
data class LiveContentWrapper(
    val turns: List<LiveTurn>,
    @SerialName("turnComplete") val turnComplete: Boolean = true
)

@Serializable
data class LiveTurn(
    val role: String,
    val parts: List<LivePart>
)

@Serializable
data class LivePart(
    @SerialName("inlineData") val inlineData: LiveBlob? = null,
    val text: String? = null
)

// ── ToolResponse ─────────────────────────────────────────────────

@Serializable
data class LiveToolResponseMessage(
    @SerialName("toolResponse") val toolResponse: LiveToolResponseWrapper
)

@Serializable
data class LiveToolResponseWrapper(
    val functionResponses: List<LiveFunctionResponse>
)

@Serializable
data class LiveFunctionResponse(
    val id: String,
    val name: String,
    val response: JsonObject
)

// ═══════════════════════════════════════════════════════════════════
// Server → Client messages
// ═══════════════════════════════════════════════════════════════════

@Serializable
data class LiveServerMessage(
    @SerialName("serverContent") val serverContent: LiveServerContent? = null,
    @SerialName("setupComplete") val setupComplete: JsonObject? = null,
    @SerialName("toolCall") val toolCall: LiveToolCallWrapper? = null,
    @SerialName("goAway") val goAway: LiveGoAway? = null,
    @SerialName("sessionResumptionUpdate") val sessionResumptionUpdate: LiveSessionResumptionUpdate? = null,
    val error: LiveError? = null
)

@Serializable
data class LiveGoAway(
    @SerialName("timeLeft") val timeLeft: JsonElement? = null
)

@Serializable
data class LiveSessionResumptionUpdate(
    val newHandle: String? = null,
    val resumable: Boolean? = null
)

@Serializable
data class LiveToolCallWrapper(
    @SerialName("functionCalls") val functionCalls: List<LiveFunctionCall>
)

@Serializable
data class LiveFunctionCall(
    val id: String,
    val name: String,
    val args: JsonObject? = null
)

@Serializable
data class LiveError(
    val message: String
)

@Serializable
data class LiveServerContent(
    @SerialName("modelTurn") val modelTurn: LiveModelTurn? = null,
    @SerialName("turnComplete") val turnComplete: Boolean? = null,
    val interrupted: Boolean? = null,
    @SerialName("inputTranscription") val inputTranscription: LiveTranscription? = null,
    @SerialName("outputTranscription") val outputTranscription: LiveTranscription? = null
)

@Serializable
data class LiveModelTurn(
    val parts: List<LivePart>? = null
)

@Serializable
data class LiveTranscription(
    val text: String? = null
)

// ═══════════════════════════════════════════════════════════════════
// Events & Connection State
// ═══════════════════════════════════════════════════════════════════

data class LiveToolCallEvent(
    val callId: String,
    val name: String,
    val args: Map<String, String>
)

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

// ═══════════════════════════════════════════════════════════════════
// Voice Profiles — Gemini Live API supports 30 prebuilt voices
// ═══════════════════════════════════════════════════════════════════

enum class VoiceGender { FEMALE, MALE }

data class VoiceProfile(
    val name: String,
    val gender: VoiceGender,
    val tone: String,       // Short Thai description
    val toneEn: String      // English characteristic
)

object GeminiVoiceProfiles {

    val all: List<VoiceProfile> = listOf(
        // ──── Female Voices (14) ────
        VoiceProfile("Aoede",        VoiceGender.FEMALE, "เสียงนุ่ม ธรรมชาติ",    "Breezy"),
        VoiceProfile("Kore",         VoiceGender.FEMALE, "เสียงหนักแน่น มั่นใจ",   "Firm"),
        VoiceProfile("Leda",         VoiceGender.FEMALE, "เสียงสดใส วัยรุ่น",      "Youthful"),
        VoiceProfile("Zephyr",       VoiceGender.FEMALE, "เสียงสดใส ร่าเริง",      "Bright"),
        VoiceProfile("Autonoe",      VoiceGender.FEMALE, "เสียงสดใส มองโลกดี",    "Bright"),
        VoiceProfile("Callirrhoe",   VoiceGender.FEMALE, "เสียงสบายๆ ผ่อนคลาย",   "Easy-going"),
        VoiceProfile("Despina",      VoiceGender.FEMALE, "เสียงนุ่มนวล ลื่นไหล",   "Smooth"),
        VoiceProfile("Erinome",      VoiceGender.FEMALE, "เสียงชัดเจน แม่นยำ",     "Clear"),
        VoiceProfile("Gacrux",       VoiceGender.FEMALE, "เสียงผู้ใหญ่ มีประสบการณ์", "Mature"),
        VoiceProfile("Laomedeia",    VoiceGender.FEMALE, "เสียงสดใส มีชีวิตชีวา",   "Upbeat"),
        VoiceProfile("Pulcherrima",  VoiceGender.FEMALE, "เสียงชัดเจน แสดงออก",    "Forward"),
        VoiceProfile("Sulafat",      VoiceGender.FEMALE, "เสียงอบอุ่น เป็นมิตร",    "Warm"),
        VoiceProfile("Vindemiatrix", VoiceGender.FEMALE, "เสียงอ่อนโยน นุ่มนวล",   "Gentle"),
        VoiceProfile("Achernar",     VoiceGender.FEMALE, "เสียงเบาๆ อ่อนหวาน",    "Soft"),

        // ──── Male Voices (16) ────
        VoiceProfile("Puck",           VoiceGender.MALE, "เสียงสดใส กระตือรือร้น", "Upbeat"),
        VoiceProfile("Charon",         VoiceGender.MALE, "เสียงให้ข้อมูล ชัดเจน",  "Informative"),
        VoiceProfile("Fenrir",         VoiceGender.MALE, "เสียงตื่นเต้น มีพลัง",   "Excitable"),
        VoiceProfile("Orus",           VoiceGender.MALE, "เสียงหนักแน่น เด็ดขาด",  "Firm"),
        VoiceProfile("Achird",         VoiceGender.MALE, "เสียงเป็นมิตร เข้าถึงง่าย", "Friendly"),
        VoiceProfile("Algenib",        VoiceGender.MALE, "เสียงทุ้ม มีเอกลักษณ์",   "Gravelly"),
        VoiceProfile("Algieba",        VoiceGender.MALE, "เสียงนุ่มนวล น่าฟัง",     "Smooth"),
        VoiceProfile("Alnilam",        VoiceGender.MALE, "เสียงหนักแน่น แแข็งแรง", "Firm"),
        VoiceProfile("Enceladus",      VoiceGender.MALE, "เสียงเบาๆ นุ่มนวล",      "Breathy"),
        VoiceProfile("Iapetus",        VoiceGender.MALE, "เสียงชัดเจน ออกเสียงดี",  "Clear"),
        VoiceProfile("Rasalgethi",     VoiceGender.MALE, "เสียงให้ข้อมูล มืออาชีพ", "Informative"),
        VoiceProfile("Sadachbia",      VoiceGender.MALE, "เสียงมีชีวิตชีวา สนุกสนาน", "Lively"),
        VoiceProfile("Sadaltager",     VoiceGender.MALE, "เสียงรอบรู้ น่าเชื่อถือ",  "Knowledgeable"),
        VoiceProfile("Schedar",        VoiceGender.MALE, "เสียงสม่ำเสมอ สมดุล",     "Even"),
        VoiceProfile("Umbriel",        VoiceGender.MALE, "เสียงสบายๆ ใจเย็น",       "Easy-going"),
        VoiceProfile("Zubenelgenubi",  VoiceGender.MALE, "เสียงสบายๆ เป็นกันเอง",   "Casual")
    )

    val females: List<VoiceProfile> get() = all.filter { it.gender == VoiceGender.FEMALE }
    val males: List<VoiceProfile> get() = all.filter { it.gender == VoiceGender.MALE }

    fun findByName(name: String): VoiceProfile? =
        all.find { it.name.equals(name, ignoreCase = true) }

    fun getVoiceListSummary(): String {
        return all.joinToString("\n") { p ->
            val icon = if (p.gender == VoiceGender.FEMALE) "♀" else "♂"
            "- ${p.name} ($icon): ${p.tone}"
        }
    }

    val defaultVoice: VoiceProfile = all.first { it.name == "Aoede" }
}

// ═══════════════════════════════════════════════════════════════════
// LiveGeminiService
// ═══════════════════════════════════════════════════════════════════

class LiveGeminiService(
    private val client: HttpClient,
    private var apiKey: String,
    private var liveModelName: String,
    private val memoryManager: com.example.personalaibot.memory.JarvisMemoryManager? = null,
    private val maxRetries: Int = 3,
    private val baseRetryDelayMs: Long = 1000L
) {

    // Small protocol guard: the server's setupComplete frame can race with the MIC sender.
    // Do not send the first realtime PCM frame in the same scheduling slice as setupComplete.
    // Dropping a few hundred ms is preferable to sending an out-of-phase frame that can close
    // the Live session with NOT_CONSISTENT / INVALID_ARGUMENT.
    private var realtimeInputReadyAtMs: Long = 0L
    private val realtimeInputReadyGraceMs: Long = 250L
    // Live-specific credential/model rotation. The normal GeminiService fallback chain
    // is request/response based and cannot be reused directly for a persistent WebSocket.
    private var liveApiKeys: List<String> = listOf(apiKey).filter { it.isNotBlank() }
    private var liveModelChain: List<String> = listOf(
        liveModelName,
        "gemini-2.5-flash-native-audio-preview-12-2025",
        "gemini-2.0-flash-exp"
    ).map { it.removePrefix("models/") }.distinct()
    private var liveKeyIndex: Int = 0
    private var liveModelIndex: Int = 0
    private val triedLiveCredentials = mutableSetOf<String>()
    private var webSocketSession: DefaultWebSocketSession? = null
    private var isSetupComplete = false

    /** true เมื่อผู้ใช้กดหยุดเอง — แยกจาก server-initiated close (GoAway/session timeout) ที่ต้อง reconnect */
    private var userRequestedDisconnect = false
    /** session รอบปัจจุบันเคย READY แล้วหรือไม่ — ใช้ reset retry counter เมื่อ server ปิด session ที่เคยใช้งานได้ */
    private var sessionWasReady = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // คลังความจำระยะสั้น: เก็บประโยคสุดท้ายที่ผู้ใช้พูด เพื่อใช้เตือนสมาธิ AI ตอนเปิดเครื่องมือ
    var lastUserText: String = ""

    /** ข้อความที่จะส่งให้ model พูดทันทีหลัง session READY (เช่นทักยืนยันเสียงใหม่หลังเปลี่ยนเสียง). */
    var pendingGreetingOnReady: String? = null

    // Monotonically increasing WebSocket lifecycle id. A READY coroutine must never send
    // through a newer socket after the socket that produced READY has been replaced.
    private var liveSessionGeneration: Long = 0L
    private var greetingSentForReady: Boolean = false

    // buffer 128 chunks — แยกการอ่าน WebSocket ออกจาก AudioTrack.write() ที่ blocking
    // (เดิมไม่มี buffer → emit suspend รอ playback → เฟรมถัดไปค้างทั้ง turn/transcript/tool)
    private val _audioOutputFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    val audioOutputFlow: Flow<ByteArray> = _audioOutputFlow.asSharedFlow()

    /** อีเวนต์ข้อความจาก Live mode (ใช้สำหรับ UI ตรวจสอบว่าจะขึ้นกล่องใหม่หรือพิมพ์ต่อ) */
    data class LiveTextUpdate(
        val text: String, 
        val role: String = "model", 
        val append: Boolean = true,
        val replace: Boolean = false,
        val isStatic: Boolean = false // If true, this box won't be overwritten by subsequent 'replace' updates
    )

    private val _textOutputFlow = MutableSharedFlow<LiveTextUpdate>(extraBufferCapacity = 10)
    val textOutputFlow: Flow<LiveTextUpdate> = _textOutputFlow.asSharedFlow()

    private val _nativeToolCallFlow = MutableSharedFlow<LiveToolCallEvent>()
    val nativeToolCallFlow: Flow<LiveToolCallEvent> = _nativeToolCallFlow.asSharedFlow()

    private val _bridgeToolRequestFlow = MutableSharedFlow<String>()
    val toolRequestFlow: Flow<String> = _bridgeToolRequestFlow.asSharedFlow()

    // Turn buffers to prevent progressive duplication in DB
    private var pendingUserTurnText: String? = null
    private var pendingModelTurnText: String? = null

    // Fallback buffer: text parts ที่ model ส่งแทนเสียง (failure mode ที่ทำให้เสียงเงียบ)
    private var pendingModelTextParts: String? = null
    // นับ bytes เสียงต่อ turn — ใช้ตรวจ turn ที่ model ตอบเป็น text ล้วน (ไม่มีเสียง)
    private var audioBytesThisTurn: Int = 0

    // VAD/user barge-in cancels the current generation. Never send its partial text to TTS.
    // Otherwise the cancelled TTS can overlap with the next Live generation.
    private var turnWasInterrupted: Boolean = false

    /** เรียกเมื่อ server แจ้ง generation ถูกขัดจังหวะ (VAD/user barge-in) — ฝั่ง playback ต้อง flush คิวเสียงค้างทันที (ตาม Live API guide) */
    var onInterrupted: (() -> Unit)? = null

    /** เรียกเมื่อ turn จบโดยไม่มีเสียงออกเลย (model ตอบเป็น text ล้วน) — ใช้เป็น TTS fallback ไม่ให้เงียบเฉย */
    var onTurnWithoutAudio: ((String) -> Unit)? = null

    // ── Pre-READY audio buffer ──────────────────────────────────────
    // ไมค์เริ่มส่งเสียงทันทีที่ผู้ใช้กด Live แต่บางโมเดล (เช่น 3.1-flash-live-preview)
    // ใช้เวลา setup 7–15 วิ — เดิม sendIfReady() ทิ้ง chunk เงียบๆ → ผู้ใช้พูดแล้ว AI ไม่ได้ยิน
    // เก็บ chunk ไว้ใน ring buffer แล้ว flush ทันทีที่ setupComplete
    private val preReadyAudioBuffer = ArrayDeque<String>()
    private val preReadyMutex = kotlinx.coroutines.sync.Mutex()
    private val maxPreReadyChunks = 400 // ~8–16 วินาที ขึ้นกับ AudioRecord buffer size/device
    private var droppedPreReadyChunks = 0

    // Live API session resumption: keeps the conversation alive across periodic WebSocket resets.
    private var sessionResumptionHandle: String? = null
    private var goAwayReceived = false
    private var connectionStartedAtMs: Long = 0L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    // ย้ายไปรวมศูนย์ที่ JarvisPersona (2026-07-29)
    // — ใช้ getter เพื่อให้ identity ที่ผู้ใช้/AI ปรับแต่งมีผลทันทีทุก session
    private val LIVE_SYSTEM_PROMPT: String
        get() = com.example.personalaibot.ai.JarvisPersona.LIVE_SYSTEM_PROMPT

    private var selectedVoiceName: String = "Aoede" // Default

    fun updateConfig(newApiKey: String, newModelName: String, voiceName: String = "Aoede") {
        apiKey = newApiKey
        liveModelName = newModelName.removePrefix("models/")
        selectedVoiceName = voiceName
        if (liveApiKeys.isEmpty() || !liveApiKeys.contains(newApiKey)) {
            liveApiKeys = listOf(newApiKey).filter { it.isNotBlank() } + liveApiKeys.filter { it != newApiKey }
        }
        if (liveModelChain.isEmpty() || !liveModelChain.contains(liveModelName)) {
            liveModelChain = listOf(liveModelName) + liveModelChain.filter { it != liveModelName }
        }
        liveKeyIndex = liveApiKeys.indexOf(newApiKey).coerceAtLeast(0)
        liveModelIndex = liveModelChain.indexOf(liveModelName).coerceAtLeast(0)
    }

    /** Configure the complete Gemini key pool used by Live quota rotation. */
    fun updateLiveApiKeys(keys: List<String>) {
        val normalized = keys.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalized.isEmpty()) return
        liveApiKeys = normalized
        liveKeyIndex = normalized.indexOf(apiKey).takeIf { it >= 0 } ?: 0
        apiKey = liveApiKeys[liveKeyIndex]
    }

    /** Configure only models that are actually Live-capable. */
    fun updateLiveModelChain(models: List<String>) {
        val normalized = models.map { it.trim().removePrefix("models/") }
            .filter { it.isNotBlank() }.distinct()
        if (normalized.isEmpty()) return
        liveModelChain = normalized
        liveModelIndex = normalized.indexOf(liveModelName).takeIf { it >= 0 } ?: 0
        liveModelName = liveModelChain[liveModelIndex]
    }

    /**
     * Move to the next credential/model exactly once per quota failure.
     * A rotated key starts a fresh WebSocket; session-resumption handles are tied to
     * the previous session and must not be replayed across a different credential.
     */
    private fun rotateLiveCredentialOnQuota(): Boolean {
        val total = liveApiKeys.size * liveModelChain.size
        if (total <= 1) return false

        // Mark the credential/model that just failed before searching for another pair.
        triedLiveCredentials += "${liveKeyIndex}:$liveModelIndex"

        repeat(total) {
            liveKeyIndex = (liveKeyIndex + 1) % liveApiKeys.size
            if (liveKeyIndex == 0) {
                liveModelIndex = (liveModelIndex + 1) % liveModelChain.size
            }
            val candidate = "${liveKeyIndex}:$liveModelIndex"
            if (candidate !in triedLiveCredentials) {
                apiKey = liveApiKeys[liveKeyIndex]
                liveModelName = liveModelChain[liveModelIndex]
                // A resumption handle belongs to the old credential/session. Start fresh.
                sessionResumptionHandle = null
                logDebug(
                    "LiveGemini",
                    "🔄 Live quota → rotate credential ${liveKeyIndex + 1}/${liveApiKeys.size} + model=$liveModelName"
                )
                return true
            }
        }
        logError("LiveGemini", "🛑 Live quota exhausted across all configured key/model combinations")
        return false
    }

    /**
     * Ktor's WebSocket Frame.Text UTF-8 encoder rejects unpaired UTF-16 surrogates.
     * Conversation/memory text can contain such code units after external text ingestion,
     * so normalize them at the WebSocket boundary instead of killing the Live session.
     */
    private fun sanitizeForWebSocketText(value: String): String {
        if (value.isEmpty()) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate() -> {
                    out.append(c).append(value[i + 1])
                    i += 2
                }
                c.isHighSurrogate() || c.isLowSurrogate() -> {
                    out.append('\uFFFD')
                    i++
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    suspend fun connectAndListen(tools: GeminiTool? = null, historyContext: String = "", coreContext: String = "") {
        if (apiKey.isBlank()) {
            logError("LiveGemini", "API key is blank — aborting connection")
            _connectionState.value = ConnectionState.Error("API key is missing")
            return
        }

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"

        userRequestedDisconnect = false
        triedLiveCredentials.clear()
        // Start a fresh retry budget for this user-initiated Live session.
        liveKeyIndex = liveApiKeys.indexOf(apiKey).takeIf { it >= 0 } ?: 0
        liveModelIndex = liveModelChain.indexOf(liveModelName).takeIf { it >= 0 } ?: 0
        var attempt = 0
        while (attempt <= maxRetries) {
            isSetupComplete = false
            sessionWasReady = false
            goAwayReceived = false
            greetingSentForReady = false
            val sessionGeneration = ++liveSessionGeneration

            if (attempt == 0) {
                _connectionState.value = ConnectionState.Connecting
            } else {
                _connectionState.value = ConnectionState.Reconnecting(attempt, maxRetries)
                val baseDelay = baseRetryDelayMs * (1L shl (attempt - 1).coerceAtMost(4))
                val jitter = (0..500).random() // Add 0-500ms jitter
                val delayMs = baseDelay + jitter
                logDebug("LiveGemini", "Reconnecting in ${delayMs}ms (attempt $attempt/$maxRetries)")
                delay(delayMs)
            }

            logDebug("LiveGemini", "Connecting to Live API with model: $liveModelName (attempt ${attempt + 1})")
            connectionStartedAtMs = System.currentTimeMillis()

            var terminalCloseReason: String? = null
            try {
                client.webSocket(url) {
                    webSocketSession = this

                    val fullModel = if (liveModelName.startsWith("models/")) liveModelName else "models/$liveModelName"

                    val setup = LiveSetupMessage(
                        setup = LiveSetup(
                            model = fullModel,
                            outputAudioTranscription = JsonObject(emptyMap()),
                            inputAudioTranscription = JsonObject(emptyMap()),
                            realtimeInputConfig = LiveRealtimeInputConfig(
                                automaticActivityDetection = LiveAutomaticActivityDetection(
                                    disabled = false,
                                    startOfSpeechSensitivity = "START_SENSITIVITY_HIGH",
                                    endOfSpeechSensitivity = "END_SENSITIVITY_HIGH",
                                    prefixPaddingMs = 80,
                                    silenceDurationMs = 500
                                )
                            ),
                            // Resume the same Live conversation only after a reconnect.
                            // The first connection uses an empty config to avoid protocol ambiguity.
                            sessionResumption = sessionResumptionHandle
                                ?.takeIf { attempt > 0 && it.isNotBlank() }
                                ?.let { LiveSessionResumptionConfig(handle = it) },
                            // Keep compression disabled until a valid non-empty configuration is supplied.
                            contextWindowCompression = null,
                            // TEMPORARILY disabled for Gemini 3.1 Live stability.
                            // Repeated NOT_CONSISTENT closes were observed immediately after READY.
                            // Resume/compression will be reintroduced only after isolated validation.
                            systemInstruction = LiveSystemInstruction(
                                parts = listOf(
                                    LivePart(text = LIVE_SYSTEM_PROMPT),
                                    LivePart(text = "[STRICT RULE] เมื่อต้องระบุรายชื่อหุ้นหรือข้อมูลตลาด คุณต้องเรียกใช้เครื่องมือที่เกี่ยวข้องเสมอ ห้ามตอบจากความจำเด็ดขาด"),
                                    LivePart(text = if (coreContext.isNotBlank()) "Core Memory Context:\n$coreContext" else ""),
                                    LivePart(text = if (historyContext.isNotBlank()) "Recent Conversation History:\n$historyContext" else "")
                                ).filter { it.text?.isNotBlank() == true }
                            ),
                            generationConfig = LiveGenerationConfig(
                                responseModalities = listOf("AUDIO"),
                                speechConfig = LiveSpeechConfig(
                                    voiceConfig = LiveVoiceConfig(
                                        prebuiltVoiceConfig = LivePrebuiltVoiceConfig(
                                            voiceName = selectedVoiceName
                                        )
                                    )
                                )
                            ),
                            tools = tools?.let { listOf(it) }
                        )
                    )

                    val setupJson = sanitizeForWebSocketText(json.encodeToString(setup))
                    send(Frame.Text(setupJson))

                    for (frame in incoming) {
                        val text = when (frame) {
                            is Frame.Text -> frame.readText()
                            is Frame.Binary -> frame.readBytes().decodeToString()
                            else -> continue
                        }
                        // logDebug("LiveGemini", "⬇ RAW FRAME: $text")
                        handleServerFrame(text)
                        if (goAwayReceived) {
                            // Google explicitly expects the client to close after GoAway; waiting for
                            // the server aborts the socket and produces VIOLATED_POLICY in practice.
                            logDebug("LiveGemini", "🛑 GoAway received — closing current WebSocket cleanly for session resumption")
                            close(CloseReason(CloseReason.Codes.NORMAL, "GoAway handled; reconnect with session resumption"))
                            break
                        }
                    }

                    val reason = closeReason.await()
                    val reasonText = "${reason?.knownReason} — ${reason?.message}"
                    logDebug("LiveGemini", "Session closed: $reasonText")
                    val normalizedReason = reasonText.lowercase()
                    val isTerminalApiError =
                        normalizedReason.contains("not_consistent") ||
                        normalizedReason.contains("invalid argument") ||
                        normalizedReason.contains("invalid_argument") ||
                        normalizedReason.contains("exceeded your current quota") ||
                        normalizedReason.contains("quota exceeded") ||
                        (normalizedReason.contains("quota") && normalizedReason.contains("internal_error"))
                    val isQuotaError =
                        normalizedReason.contains("exceeded your current quota") ||
                        normalizedReason.contains("quota exceeded") ||
                        (normalizedReason.contains("quota") && normalizedReason.contains("internal_error"))
                    if (isTerminalApiError && !isQuotaError) {
                        terminalCloseReason = reasonText
                    } else if (isQuotaError) {
                        terminalCloseReason = reasonText
                    }
                }
                if (userRequestedDisconnect) break
                if (terminalCloseReason != null) {
                    val normalizedTerminal = terminalCloseReason!!.lowercase()
                    val quotaError =
                        normalizedTerminal.contains("exceeded your current quota") ||
                        normalizedTerminal.contains("quota exceeded") ||
                        (normalizedTerminal.contains("quota") && normalizedTerminal.contains("internal_error"))
                    if (quotaError && rotateLiveCredentialOnQuota()) {
                        // Do not count a quota rotation as a transient reconnect retry.
                        // The next loop uses a different key/model and starts a fresh session.
                        attempt = 0
                        continue
                    }
                    _connectionState.value = ConnectionState.Error(terminalCloseReason!!)
                    logError("LiveGemini", "🛑 Terminal Live API/session error: $terminalCloseReason")
                    break
                }
                // Server-initiated close (GoAway / session duration limit ~10-15 นาทีของ Live API)
                // เดิม break ทิ้ง → session ตายเงียบ ผู้ใช้ยังเปิด Live แต่ทุกข้อความส่งไม่ถึง
                // (เคสจริง 2026-08-18: evolve เสร็จ 12:15 แต่ session ถูก server ปิด 12:11 → AI ไม่รายงานผล)
                attempt = if (sessionWasReady) 1 else attempt + 1 // เคย READY แล้ว = server timeout ไม่ใช่ config พัง → ไม่เสีย retry quota
                if (attempt > maxRetries) {
                    _connectionState.value = ConnectionState.Error("Server closed session repeatedly — giving up")
                    break
                }
                logDebug("LiveGemini", "🔁 Server closed session (GoAway/timeout) — auto-reconnecting (attempt $attempt/$maxRetries)")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logError("LiveGemini", "Connection error (attempt ${attempt + 1})", e)
                attempt++
                if (attempt > maxRetries) {
                    _connectionState.value = ConnectionState.Error("Connection failed after ${maxRetries + 1} attempts: ${e.message}")
                }
            } finally {
                // Final flush of remaining turn buffers to DB before closing
                logDebug("LiveGemini", "🔌 Session ending. Flushing buffers.")
                val userText = pendingUserTurnText
                val modelText = pendingModelTurnText ?: pendingModelTextParts
                if (userText != null || modelText != null) {
                    scope.launch {
                        if (userText != null) memoryManager?.storeMessage("user", userText, metadata = "{\"mode\": \"live_voice\"}")
                        if (modelText != null) memoryManager?.storeMessage("model", modelText, metadata = "{\"mode\": \"live_voice\"}")
                    }
                }
                pendingUserTurnText = null
                pendingModelTurnText = null
                pendingModelTextParts = null
                audioBytesThisTurn = 0
                // ล้างเสียงค้างก่อน READY ทิ้ง — session นี้จบแล้ว ไม่ควรไป flush ใน session ถัดไป
                preReadyMutex.withLock { preReadyAudioBuffer.clear() }
                droppedPreReadyChunks = 0

                webSocketSession = null
                isSetupComplete = false
            }
        }

        _connectionState.value = ConnectionState.Disconnected
    }

    private suspend fun handleServerFrame(rawJson: String) {
        try {
            val msg = json.decodeFromString<LiveServerMessage>(rawJson)
            // logDebug("LiveGemini", "⬇ Parsed from: $rawJson")

            msg.sessionResumptionUpdate?.let { update ->
                if (update.resumable == true && !update.newHandle.isNullOrBlank()) {
                    sessionResumptionHandle = update.newHandle
                    logDebug("LiveGemini", "♻️ Session resumption handle updated")
                }
            }

            msg.goAway?.let { goAway ->
                goAwayReceived = true
                logDebug("LiveGemini", "⚠️ GoAway received from Live API (timeLeft=${goAway.timeLeft})")
            }

            msg.error?.let {
                logError("LiveGemini", "API Error: ${it.message}")
                return
            }

            msg.setupComplete?.let {
                val readyLatencyMs = if (connectionStartedAtMs > 0L) {
                    System.currentTimeMillis() - connectionStartedAtMs
                } else 0L
                logDebug("LiveGemini", "✅ Live session READY (${readyLatencyMs}ms)")
                isSetupComplete = true
                sessionWasReady = true
                // Give the WebSocket a short scheduling window after setupComplete before
                // accepting realtime PCM from the microphone. This prevents a cross-coroutine
                // race where the first audio frame is emitted in the same scheduler slice as
                // the setup acknowledgement and the server closes with NOT_CONSISTENT.
                realtimeInputReadyAtMs = System.currentTimeMillis() + realtimeInputReadyGraceMs
                _connectionState.value = ConnectionState.Connected
                // Flush เสียงที่ผู้ใช้พูดระหว่างรอ READY (ไมค์เปิดก่อน session พร้อม)
                val preReadyCount = preReadyMutex.withLock {
                    val count = preReadyAudioBuffer.size
                    preReadyAudioBuffer.clear()
                    count
                }
                // Do not burst-flush audio captured before READY. The microphone remains active;
                // fresh PCM after setup is safer than replaying stale frames in a tight burst.
                if (preReadyCount > 0 || droppedPreReadyChunks > 0) {
                    logDebug("LiveGemini", "🧹 Dropped $preReadyCount pre-READY audio chunks (dropped oldest=$droppedPreReadyChunks) — starting with fresh realtime PCM")
                }
                droppedPreReadyChunks = 0
                // READY is the single trigger for the queued greeting. Consume it only once,
                // and bind the coroutine to the exact WebSocket lifecycle that produced READY.
                if (!greetingSentForReady) {
                    val readyGeneration = liveSessionGeneration
                    pendingGreetingOnReady?.takeIf { it.isNotBlank() }?.let { greeting ->
                        scope.launch {
                            val waitMs = realtimeInputReadyAtMs - System.currentTimeMillis()
                            if (waitMs > 0) delay(waitMs)

                            val sameSession = liveSessionGeneration == readyGeneration
                            val activeSocket = webSocketSession?.isActive == true
                            if (!isSetupComplete || !sessionWasReady || !sameSession || !activeSocket) {
                                logDebug("LiveGemini", "⚠️ READY greeting cancelled — session lifecycle changed")
                                return@launch
                            }

                            greetingSentForReady = true
                            pendingGreetingOnReady = null
                            logDebug("LiveGemini", "🗣️ LIVE_READY — sending one-time session greeting")
                            sendRealtimeText(greeting)
                        }
                    }
                }
                return
            }

            msg.toolCall?.functionCalls?.forEach { call ->
                val argsMap = call.args?.entries?.associate { (k, v) ->
                    k to v.jsonPrimitive.content
                } ?: emptyMap()

                val event = LiveToolCallEvent(
                    callId = call.id,
                    name = call.name,
                    args = argsMap
                )
                logDebug("LiveGemini", "🔧 Native tool call: ${call.name}($argsMap)")
                _nativeToolCallFlow.emit(event)
            }

            msg.serverContent?.let { content ->
                // VAD / user barge-in: server ยกเลิก generation กลางทาง
                // ต้อง flush คิวเสียงที่ค้างเล่นทันที ไม่งั้นเสียงเก่าเล่นต่อทับ turn ใหม่ (ตาม Live API guide)
                if (content.interrupted == true) {
                    logDebug("LiveGemini", "⚡ Interrupted (VAD/user) — flushing playback queue")
                    turnWasInterrupted = true
                    audioBytesThisTurn = 0
                    pendingUserTurnText = null
                    pendingModelTurnText = null
                    pendingModelTextParts = null
                    onInterrupted?.invoke()
                }
                content.modelTurn?.parts?.forEach { part ->
                    part.inlineData?.let { data ->
                        if (data.mimeType.contains("audio")) {
                            val bytes = data.data.decodeBase64Bytes()
                            audioBytesThisTurn += bytes.size
                            _audioOutputFlow.emit(bytes)
                        }
                    }
                    part.text?.let { text ->
                        if (isToolRequest(text)) {
                            logDebug("LiveGemini", "🔧 Bridge tool request detected")
                            _bridgeToolRequestFlow.emit(text)
                        } else if (text.isNotBlank()) {
                            // Text part แทนเสียง — failure mode ที่ทำให้ Live เงียบ
                            // เก็บเป็น fallback (กันข้อความหาย) และแสดงในแชทถ้ายังไม่มี transcription
                            logDebug("LiveGemini", "⚠️ JARVIS (Text Part — model ตอบเป็น text แทนเสียง): ${text.take(80)}")
                            pendingModelTextParts = (pendingModelTextParts ?: "") + text
                            if (pendingModelTurnText == null) {
                                scope.launch {
                                    _textOutputFlow.emit(LiveTextUpdate(
                                        text = pendingModelTextParts ?: text,
                                        role = "model",
                                        append = pendingModelTextParts == text,
                                        replace = pendingModelTextParts != text,
                                        isStatic = false
                                    ))
                                }
                            }
                        }
                    }
                }

                content.inputTranscription?.text?.let { text ->
                    if (text.isNotBlank()) {
                        val isFirst = pendingUserTurnText == null
                        if (isFirst) turnWasInterrupted = false
                        pendingUserTurnText = text
                        lastUserText = text
                        logDebug("LiveGemini", "🎤 User (Progress): $text")

                        // Input transcription was previously only logged. That made the UI look
                        // frozen until JARVIS answered. Expose the same progressive transcript to
                        // the chat as an ephemeral user bubble; it is still persisted only at turnComplete.
                        scope.launch {
                            _textOutputFlow.emit(LiveTextUpdate(
                                text = text,
                                role = "user",
                                append = false,
                                replace = !isFirst,
                                isStatic = false
                            ))
                        }
                    }
                }
                
                content.outputTranscription?.text?.let { text ->
                    if (text.isNotBlank()) {
                        val isFirst = pendingModelTurnText == null
                        pendingModelTurnText = text
                        logDebug("LiveGemini", "🤖 JARVIS (Progress): $text")
                        
                        // Send to UI: 
                        // If it's the first chunk, append=false (new box). 
                        // If not, replace=true (update existing box).
                        scope.launch {
                            _textOutputFlow.emit(LiveTextUpdate(
                                text = text, 
                                role = "model", 
                                append = isFirst, 
                                replace = !isFirst,
                                isStatic = false // Transcriptions are NOT static, they can be replaced
                            ))
                        }
                    }
                }

                if (content.turnComplete == true) {
                    turnCompleteFlow.tryEmit(System.currentTimeMillis())
                    val userText = pendingUserTurnText
                    // ใช้ transcription เป็นหลัก ถ้าไม่มี (model ตอบ text ล้วน) ใช้ text parts แทน
                    val modelText = pendingModelTurnText ?: pendingModelTextParts

                    if (modelText != null && audioBytesThisTurn == 0 && !turnWasInterrupted) {
                        // Failure mode: turn นี้ไม่มีเสียงออกเลย (model ตอบเป็น text แทน audio)
                        // → ส่งต่อให้ TTS fallback (Android VoiceManager) พูดแทน ไม่ให้เงียบเฉย
                        logDebug("LiveGemini", "🔇 Turn Complete with NO AUDIO (${modelText.length} chars text-only) — ใช้ TTS fallback")
                        onTurnWithoutAudio?.invoke(modelText)
                    } else {
                        logDebug("LiveGemini", "🏁 Turn Complete (audio: $audioBytesThisTurn bytes). Persisting to DB.")
                    }

                    scope.launch {
                        if (userText != null) {
                            memoryManager?.storeMessage("user", userText, metadata = "{\"mode\": \"live_voice\"}")
                        }
                        if (modelText != null) {
                            memoryManager?.storeMessage("model", modelText, metadata = "{\"mode\": \"live_voice\"}")
                        }
                    }

                    pendingUserTurnText = null
                    pendingModelTurnText = null
                    pendingModelTextParts = null
                    audioBytesThisTurn = 0
                    turnWasInterrupted = false
                }
            }
        } catch (e: Exception) {
            logError("LiveGemini", "Frame parse error: ${e.message}")
        }
    }
    
    private fun isToolRequest(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("tool_call:") || lower.contains("tool_code:")
    }

    suspend fun sendAudioChunk(pcmBase64: String) {
        // ยังไม่ READY — เก็บเข้า ring buffer แทนที่จะทิ้งเงียบๆ (flush ตอน setupComplete)
        if (!isSetupComplete || System.currentTimeMillis() < realtimeInputReadyAtMs) {
            // During the short post-READY protocol grace period, intentionally discard PCM.
            // The microphone remains running and the next frame will be sent normally.
            if (isSetupComplete && System.currentTimeMillis() < realtimeInputReadyAtMs) {
                return
            }
            preReadyMutex.withLock {
                if (preReadyAudioBuffer.size >= maxPreReadyChunks) {
                    preReadyAudioBuffer.removeFirst()
                    droppedPreReadyChunks++
                }
                preReadyAudioBuffer.addLast(pcmBase64)
            }
            return
        }
        sendIfReady {
            val msg = LiveRealtimeInputMessage(
                realtimeInput = LiveRealtimeInputData(
                    audio = LiveBlob(
                        mimeType = "audio/pcm;rate=16000",
                        data = pcmBase64
                    )
                )
            )
            json.encodeToString(msg)
        }
    }
    
    suspend fun sendImageChunk(jpegBase64: String) {
        logDebug("LiveGemini", "📹 Sending video chunk (${jpegBase64.length} chars)")
        sendIfReady {
            val msg = LiveRealtimeInputMessage(
                realtimeInput = LiveRealtimeInputData(
                    video = LiveBlob(mimeType = "image/jpeg", data = jpegBase64)
                )
            )
            json.encodeToString(msg)
        }
    }
    
    suspend fun sendBridgeToolResult(toolName: String, result: String) {
        sendIfReady {
            logDebug("LiveGemini", "⚡ Sending bridge tool result for $toolName: ${result.take(50)}...")
            // ผลเต็มถูกแสดงในแชทแล้ว (emitTextToChat) — สั่งให้พูดสรุปสั้นตาม LIVE_RULES
            // (เดิมสั่ง "นำเสนออย่างละเอียด อย่าสรุปสั้น" ขัดกับกฎเสียงพูด → model สลับไปตอบ text/markdown → เสียงเงียบ)
            val text = "ผลลัพธ์จากเครื่องมือ $toolName ถูกแสดงในแชทของผู้ใช้เรียบร้อยแล้ว ดังนี้: $result\n\n[VOICE RULE] โปรดพูดสรุปเป็นภาษาไทยแบบสนทนาให้ครบถ้วน ครอบคลุม: (1) ผลสรุป/ทิศทางหลัก (2) เหตุผลและตัวเลขสำคัญ 3-5 จุด — เล่าเป็นประโยคธรรมชาติ เช่น 'RSI อยู่ที่ 45 แสดงว่าโมเมนตัมยังอ่อนแอ' (3) จุดที่ควรระวังหรือสิ่งที่ต้องติดตาม — รวมประมาณ 8-12 ประโยค เล่าให้ครบทุกส่วนสำคัญของข้อมูล ห้ามอ่านตาราง/ลิสต์ยาวๆ ห้ามใช้ markdown และแจ้งผู้ใช้ว่าดูรายละเอียดเต็มได้ในแชท"
            val msg = LiveClientContentMessage(
                clientContent = LiveContentWrapper(
                    turns = listOf(LiveTurn(role = "user", parts = listOf(LivePart(text = text))))
                )
            )
            json.encodeToString(msg)
        }
    }

    /** แจ้งทุกครั้งที่ model จบ turn — ใช้โดย LiveToolBridge กระตุ้น vision summary หลัง turn "กำลังเปิดกล้อง" จบ */
    val turnCompleteFlow = kotlinx.coroutines.flow.MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** ส่ง text เข้า session แบบ client content (เหมือน user พิมพ์) — ใช้กระตุ้นให้ model เริ่ม turn ใหม่ */
    suspend fun sendClientText(text: String) {
        sendIfReady {
            val msg = LiveClientContentMessage(
                clientContent = LiveContentWrapper(
                    turns = listOf(LiveTurn(role = "user", parts = listOf(LivePart(text = text))))
                )
            )
            json.encodeToString(msg)
        }
    }

    /**
     * ส่ง text ผ่าน realtimeInput — ถูกปฏิบัติเหมือน user "พูด" เข้ามาจริง (trigger generation ได้)
     * ต่างจาก clientContent ที่ขณะ audio streaming ทำหน้าที่เป็นแค่ context (model ไม่ตอบเอง — พิสูจน์แล้วจากเคส voice-change greeting 2026-08-09)
     */
    suspend fun sendRealtimeText(text: String): Boolean {
        val sent = sendIfReady {
            val msg = LiveRealtimeInputMessage(
                realtimeInput = LiveRealtimeInputData(text = text)
            )
            json.encodeToString(msg)
        }
        // log เฉพาะเมื่อส่งเข้า websocket จริง — เดิม log เสมอทำให้ดูเหมือนส่งสำเร็จทั้งที่ session ตาย (เคส GoAway 2026-08-18)
        if (sent) logDebug("LiveGemini", "⬆ Sent realtime text: ${text.take(80)}")
        return sent
    }

    /**
     * Deliver a long-running task result through Live even if the WebSocket is briefly
     * reconnecting. This is deliberately different from the normal one-shot send:
     * temporary reconnect is not a reason to fire the notification/TTS fallback.
     */
    suspend fun sendRealtimeTextWhenReady(text: String, timeoutMs: Long = 30_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var delayMs = 250L
        while (System.currentTimeMillis() < deadline) {
            if (userRequestedDisconnect) return false
            if (sendRealtimeText(text)) return true
            delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(2_000L)
        }
        logDebug("LiveGemini", "⏳ Long-task result could not reach Live within ${timeoutMs}ms")
        return false
    }

    suspend fun sendNativeToolResponse(callId: String, toolName: String, result: String) {
        sendIfReady {
            val responseObj = buildJsonObject { put("result", result) }
            val msg = LiveToolResponseMessage(
                toolResponse = LiveToolResponseWrapper(
                    functionResponses = listOf(
                        LiveFunctionResponse(id = callId, name = toolName, response = responseObj)
                    )
                )
            )
            json.encodeToString(msg)
        }
        logDebug("LiveGemini", "✅ Tool response sent: $toolName callId=$callId → $result")
    }

    suspend fun disconnect() {
        userRequestedDisconnect = true
        try { webSocketSession?.close() } catch (_: Exception) {}
        webSocketSession = null
        isSetupComplete = false
        _connectionState.value = ConnectionState.Disconnected
        logDebug("LiveGemini", "Session disconnected")
    }

    /** @return true ถ้าส่งเข้า websocket จริง — false ถ้า session ไม่พร้อม (caller ต้องไม่ log ว่าส่งแล้ว) */
    private suspend fun sendIfReady(buildJson: () -> String): Boolean {
        val session = webSocketSession
        if (session == null || !session.isActive || !isSetupComplete) {
            logDebug("LiveGemini", "⚠️ send skipped — session ไม่พร้อม (hasSession=${session != null}, active=${session?.isActive == true}, ready=$isSetupComplete)")
            return false
        }
        try {
            val jsonStr = buildJson()
            // logDebug("LiveGemini", "⬆ SENDING: $jsonStr")
            session.send(Frame.Text(jsonStr))
            return true
        } catch (e: kotlinx.coroutines.CancellationException) {
            // The WebSocket/session can be cancelled while the MIC sender is still draining
            // audio chunks (stop, GoAway, reconnect, or parent coroutine shutdown). This is
            // expected lifecycle control, NOT a send/network error. Do not log one ERROR per
            // PCM chunk — audio arrives continuously and would flood Logcat hundreds of times.
            return false
        } catch (e: Exception) {
            logError("LiveGemini", "Send failed", e)
            return false
        }
    }

    suspend fun emitTextToChat(text: String) {
        // รายงานหรือข้อมูลจาก Tool ให้ขึ้นกล่องใหม่และเป็นกล่องถาวร (isStatic=true)
        _textOutputFlow.emit(LiveTextUpdate(text, role = "model", append = false, isStatic = true))

        // --- Persistence to Knowledge Base & History ---
        // ตัด tool dump ยาวๆ ออกก่อนเก็บลง Working Memory — ไม่งั้น history snapshot
        // จะเต็มไปด้วยผลลัพธ์ tool ดิบ (token bloat เมื่อสะสม)
        val stored = if (text.length > 2000) text.take(2000) + "\n...[truncated for memory]" else text
        scope.launch {
            memoryManager?.storeMessage(
                role = "model",
                content = stored,
                metadata = "{\"mode\": \"live_voice_tool_result\"}"
            )
        }
    }
}

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
    val tools: List<GeminiTool>? = null
)

@Serializable
data class LiveSystemInstruction(
    val parts: List<LivePart>
)

@Serializable
data class LiveGenerationConfig(
    @SerialName("response_modalities") val responseModalities: List<String>? = null,
    @SerialName("speech_config") val speechConfig: LiveSpeechConfig? = null
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
    val error: LiveError? = null
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
    private var webSocketSession: DefaultWebSocketSession? = null
    private var isSetupComplete = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // คลังความจำระยะสั้น: เก็บประโยคสุดท้ายที่ผู้ใช้พูด เพื่อใช้เตือนสมาธิ AI ตอนเปิดเครื่องมือ
    var lastUserText: String = ""
        private set

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

    /** เรียกเมื่อ server แจ้ง generation ถูกขัดจังหวะ (VAD/user barge-in) — ฝั่ง playback ต้อง flush คิวเสียงค้างทันที (ตาม Live API guide) */
    var onInterrupted: (() -> Unit)? = null

    /** เรียกเมื่อ turn จบโดยไม่มีเสียงออกเลย (model ตอบเป็น text ล้วน) — ใช้เป็น TTS fallback ไม่ให้เงียบเฉย */
    var onTurnWithoutAudio: ((String) -> Unit)? = null

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
    }

    suspend fun connectAndListen(tools: GeminiTool? = null, historyContext: String = "", coreContext: String = "") {
        if (apiKey.isBlank()) {
            logError("LiveGemini", "API key is blank — aborting connection")
            _connectionState.value = ConnectionState.Error("API key is missing")
            return
        }

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"

        var attempt = 0
        while (attempt <= maxRetries) {
            isSetupComplete = false

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

            try {
                client.webSocket(url) {
                    webSocketSession = this

                    val fullModel = if (liveModelName.startsWith("models/")) liveModelName else "models/$liveModelName"

                    val setup = LiveSetupMessage(
                        setup = LiveSetup(
                            model = fullModel,
                            outputAudioTranscription = JsonObject(emptyMap()),
                            inputAudioTranscription = JsonObject(emptyMap()),
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

                    val setupJson = json.encodeToString(setup)
                    send(Frame.Text(setupJson))

                    for (frame in incoming) {
                        val text = when (frame) {
                            is Frame.Text -> frame.readText()
                            is Frame.Binary -> frame.readBytes().decodeToString()
                            else -> continue
                        }
                        // logDebug("LiveGemini", "⬇ RAW FRAME: $text")
                        handleServerFrame(text)
                    }

                    val reason = closeReason.await()
                    logDebug("LiveGemini", "Session closed: ${reason?.knownReason} — ${reason?.message}")
                }
                break
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

            msg.error?.let {
                logError("LiveGemini", "API Error: ${it.message}")
                return
            }

            msg.setupComplete?.let {
                logDebug("LiveGemini", "✅ Live session READY")
                isSetupComplete = true
                _connectionState.value = ConnectionState.Connected
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
                    audioBytesThisTurn = 0
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
                        pendingUserTurnText = text
                        lastUserText = text
                        logDebug("LiveGemini", "🎤 User (Progress): $text")
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
                    val userText = pendingUserTurnText
                    // ใช้ transcription เป็นหลัก ถ้าไม่มี (model ตอบ text ล้วน) ใช้ text parts แทน
                    val modelText = pendingModelTurnText ?: pendingModelTextParts

                    if (modelText != null && audioBytesThisTurn == 0) {
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
            val text = "ผลลัพธ์จากเครื่องมือ $toolName ถูกแสดงในแชทของผู้ใช้เรียบร้อยแล้ว ดังนี้: $result\n\n[VOICE RULE] โปรดพูดสรุปเป็นภาษาไทยแบบสนทนาให้ครบถ้วน ครอบคลุม: (1) ผลสรุป/ทิศทางหลัก (2) เหตุผลและตัวเลขสำคัญ 2-4 จุด — เล่าเป็นประโยคธรรมชาติ เช่น 'RSI อยู่ที่ 45 แสดงว่าโมเมนตัมยังอ่อนแอ' (3) จุดที่ควรระวังหรือสิ่งที่ต้องติดตาม — รวมประมาณ 5-8 ประโยค ห้ามอ่านตาราง/ลิสต์ยาวๆ ห้ามใช้ markdown และแจ้งผู้ใช้ว่าดูรายละเอียดเต็มได้ในแชท"
            val msg = LiveClientContentMessage(
                clientContent = LiveContentWrapper(
                    turns = listOf(LiveTurn(role = "user", parts = listOf(LivePart(text = text))))
                )
            )
            json.encodeToString(msg)
        }
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
        logDebug("LiveGemini", "✅ Tool response sent: $toolName → $result")
    }

    suspend fun disconnect() {
        try { webSocketSession?.close() } catch (_: Exception) {}
        webSocketSession = null
        isSetupComplete = false
        _connectionState.value = ConnectionState.Disconnected
        logDebug("LiveGemini", "Session disconnected")
    }

    private suspend fun sendIfReady(buildJson: () -> String) {
        val session = webSocketSession
        if (session == null || !session.isActive || !isSetupComplete) return
        try {
            val jsonStr = buildJson()
            // logDebug("LiveGemini", "⬆ SENDING: $jsonStr")
            session.send(Frame.Text(jsonStr))
        } catch (e: Exception) {
            logError("LiveGemini", "Send failed", e)
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

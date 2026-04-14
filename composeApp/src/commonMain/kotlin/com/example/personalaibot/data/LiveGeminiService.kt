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

    private val _audioOutputFlow = MutableSharedFlow<ByteArray>()
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

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val LIVE_SYSTEM_PROMPT = """คุณคือ JARVIS ผู้เชี่ยวชาญด้านการวิเคราะห์ข้อมูลและสายตาอัจฉริยะ ปฏิบัติตามกฎอย่างเคร่งครัด:
1. การตอบสนอง: ตอบเป็น "ภาษาไทย" เท่านั้น ทักทายสั้นๆ และเข้าประเด็นทันที
2. **กฎการใช้สายตา (VISION RULES - ANTI-HALLUCINATION)**:
   - เมื่อเปิดกล้อง (`vision_activate`): **ห้ามเดาสุ่มจากเฟรมแรกเด็ดขาด** ให้รอสังเกตสตรีมวิดีโออย่างน้อย 1-2 วินาทีเพื่อให้ภาพชัดเจนและโฟกัสก่อนจะเริ่มสรุป
   - หากภาพยังไม่ชัด หรือไม่แน่ใจ: ให้สังเกตต่อไปอีกครู่หนึ่งก่อนจะรายงาน
   - **ปิดตาทันที**: เรียก `vision_deactivate` ทันทีที่ข้อมูลครบถ้วน 'ก่อน' จะเริ่มบรรยายสรุปให้ผู้ใช้ฟัง
 3. **การแจ้งผล (REPORTING & PRO-ANALYST VOICE)**:
   - **ห้ามพูดปัดภาระ**: ห้ามใช้ประโยค "ดูรายละเอียดในแชท" หรือ "รายละเอียดอยู่ในแชท" เป็นคำสรุปหลักเด็ดขาด คุณต้องทำหน้าที่เป็นนักวิเคราะห์มืออาชีพ
   - **วิเคราะห์เชิงลึกด้วยเสียง**: เมื่อข้อมูลดิบปรากฏในแชทแล้ว ให้คุณสรุป Highlight สำคัญอย่างน้อย 2 ประเด็น (เช่น ตัวที่บวกแรงสุด, แนวโน้มหลัก, หรือความเสี่ยง) ให้ผู้ใช้ฟังทันทีด้วยเสียงที่มั่นใจและฉลาด
   - **Voice-First**: จดจำว่าสตรีมเสียงคือการสื่อสารหลัก แชทเป็นเพียงข้อมูลอ้างอิงเท่านั้น
   - **ห้ามพูดตอบโต้ 2 รอบใน Turn เดียว**: เมื่อพูดจบการวิเคราะห์ที่คมคายแล้ว ให้หยุดสตรีมเสียงทันที
4. การเงิน: ใช้เครื่องมือตลาดหุ้นจริงเสมอ ห้ามตอบจากความจำ"""

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
                val modelText = pendingModelTurnText
                if (userText != null || modelText != null) {
                    scope.launch {
                        if (userText != null) memoryManager?.storeMessage("user", userText, metadata = "{\"mode\": \"live_voice\"}")
                        if (modelText != null) memoryManager?.storeMessage("model", modelText, metadata = "{\"mode\": \"live_voice\"}")
                    }
                }
                pendingUserTurnText = null
                pendingModelTurnText = null

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
                content.modelTurn?.parts?.forEach { part ->
                    part.inlineData?.let { data ->
                        if (data.mimeType.contains("audio")) {
                            _audioOutputFlow.emit(data.data.decodeBase64Bytes())
                        }
                    }
                    part.text?.let { text ->
                        if (isToolRequest(text)) {
                            logDebug("LiveGemini", "🔧 Bridge tool request detected")
                            _bridgeToolRequestFlow.emit(text)
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
                    logDebug("LiveGemini", "🏁 Turn Complete. Persisting to DB.")
                    val userText = pendingUserTurnText
                    val modelText = pendingModelTurnText
                    
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
            val text = "ผลลัพธ์จากเครื่องมือ $toolName: $result. โปรดนำเสนอข้อมูลและรายละเอียดแก่ผู้ใช้อย่างชัดเจนและครบถ้วน อย่าสรุปสั้นเกินไป และตอบเป็นภาษาไทยเท่านั้น"
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

        // --- NEW: Persistence to Knowledge Base & History ---
        scope.launch {
            memoryManager?.storeMessage(
                role = "model", 
                content = text, 
                metadata = "{\"mode\": \"live_voice_tool_result\"}"
            )
        }
    }
}

package com.example.personalaibot.data

import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import com.example.personalaibot.tools.GeminiTool
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.util.decodeBase64Bytes
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

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
        VoiceProfile("Alnilam",        VoiceGender.MALE, "เสียงหนักแน่น แข็งแกร่ง", "Firm"),
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
    private val maxRetries: Int = 3,
    private val baseRetryDelayMs: Long = 1000L
) {
    private var webSocketSession: DefaultWebSocketSession? = null
    private var isSetupComplete = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // คลังความจำระยะสั้น: เก็บประโยคสุดท้ายที่ผู้ใช้พูด เพื่อใช้เตือนสมาธิ AI ตอนเปิดเครื่องมือ
    var lastUserText: String = ""
        private set

    private val _audioOutputFlow = MutableSharedFlow<ByteArray>()
    val audioOutputFlow: Flow<ByteArray> = _audioOutputFlow.asSharedFlow()

    /** อีเวนต์ข้อความจาก Live mode (ใช้สำหรับ UI ตรวจสอบว่าจะขึ้นกล่องใหม่หรือพิมพ์ต่อ) */
    data class LiveTextUpdate(val text: String, val role: String = "model", val append: Boolean = true)

    private val _textOutputFlow = MutableSharedFlow<LiveTextUpdate>(extraBufferCapacity = 10)
    val textOutputFlow: Flow<LiveTextUpdate> = _textOutputFlow.asSharedFlow()

    private val _nativeToolCallFlow = MutableSharedFlow<LiveToolCallEvent>()
    val nativeToolCallFlow: Flow<LiveToolCallEvent> = _nativeToolCallFlow.asSharedFlow()

    private val _bridgeToolRequestFlow = MutableSharedFlow<String>()
    val toolRequestFlow: Flow<String> = _bridgeToolRequestFlow.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val LIVE_SYSTEM_PROMPT = """คุณคือ JARVIS ผู้เชี่ยวชาญด้านการเงินและการวิเคราะห์แบบ Real-time
กฎหลัก (STRICT):
1. การตอบสนอง: ทักทายผู้ใช้ทันทีเมื่อเริ่มการเชื่อมต่อและตอบโต้ด้วยเสียงอย่างเป็นธรรมชาติ
2. ภาษา: พูดและตอบเป็น "ภาษาไทย" เท่านั้น ห้ามพูดภาษาอังกฤษเด็ดขาด
3. การมองเห็น (VISION): ปกติดวงตาจะปิดอยู่ หากผู้ใช้ถามถึงสิ่งที่เห็น ให้เรียก `vision_activate` เพื่อเปิดกล้อง และเมื่อวิเคราะห์เสร็จให้เรียก `vision_deactivate` ทันที
4. การเงิน: ใช้ `trading_market_snapshot` และ `trading_price` เพื่อดึงข้อมูลจริง ห้ามตอบจากความจำ
5. รายงาน: ใช้ `analyze_and_display_report` เพื่อส่งผลลัพธ์ละเอียดเข้าหน้าแชท ส่วนเสียงพูดให้สรุปใจความสำคัญสั้นๆ"""

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
                val delayMs = baseRetryDelayMs * (1L shl (attempt - 1).coerceAtMost(4))
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
                            // SILENCED: Only log for deep debugging
                            // logDebug("LiveGemini", "🔊 Received audio chunk (${data.data.length} chars)")
                            _audioOutputFlow.emit(data.data.decodeBase64Bytes())
                        }
                    }
                    content.inputTranscription?.let { transcription ->
                        transcription.text?.let { text ->
                            if (text.isNotBlank()) {
                                lastUserText = text
                                logDebug("LiveGemini", "🎤 User (Transcribed): $text")
                            }
                        }
                    }
                    part.text?.let { text ->
                        // SILENCED: Keep logs clean
                        // logDebug("LiveGemini", "🤖 Model text: $text")
                        
                        // DISABLED: Redundant text in Chat UI during Live Mode
                        // _textOutputFlow.emit(LiveTextUpdate(text, role = "model", append = true))
                        
                        if (isToolRequest(text)) {
                            logDebug("LiveGemini", "🔧 Bridge tool request detected")
                            _bridgeToolRequestFlow.emit(text)
                        }
                    }
                }

                content.inputTranscription?.text?.let { text ->
                    // logDebug("LiveGemini", "🎤 User said: $text")
                    // DISABLED: Keep session clean
                    // _textOutputFlow.emit(LiveTextUpdate("🎤 $text", role = "user", append = false))
                }
                
                content.outputTranscription?.text?.let { text ->
                    // logDebug("LiveGemini", "🤖 JARVIS: $text")
                    // DISABLED: Keep session clean
                    // _textOutputFlow.emit(LiveTextUpdate("🤖 $text", role = "model", append = false))
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
        // บังคับขึ้นกล่องใหม่เสมอ (append = false) สำหรับผลลัพธ์จาก Tool
        _textOutputFlow.emit(LiveTextUpdate(text, role = "model", append = false))
    }
}

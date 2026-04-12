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
    val setup: LiveSetup
)

@Serializable
data class LiveSetup(
    val model: String,
    val generationConfig: LiveGenerationConfig? = null,
    val systemInstruction: LiveSystemInstruction? = null,
    val tools: List<GeminiTool>? = null
)



@Serializable
data class LiveSystemInstruction(
    val parts: List<LivePart>
)

@Serializable
data class LiveGenerationConfig(
    val responseModalities: List<String>? = null,
    val speechConfig: LiveSpeechConfig? = null
)

@Serializable
data class LiveSpeechConfig(
    val voiceConfig: LiveVoiceConfig? = null
)

@Serializable
data class LiveVoiceConfig(
    val prebuiltVoiceConfig: LivePrebuiltVoiceConfig? = null
)

@Serializable
data class LivePrebuiltVoiceConfig(
    val voiceName: String? = null
)

// ── RealtimeInput (audio / video / text streaming) ───────────────

@Serializable
data class LiveRealtimeInputMessage(
    val realtimeInput: LiveRealtimeInputData
)

@Serializable
data class LiveRealtimeInputData(
    val audio: LiveBlob? = null,
    val video: LiveBlob? = null,
    val text: String? = null
)

@Serializable
data class LiveBlob(
    val mimeType: String,
    val data: String   // Base64
)

// ── ClientContent (text turns) ───────────────────────────────────

@Serializable
data class LiveClientContentMessage(
    val clientContent: LiveContentWrapper
)

@Serializable
data class LiveContentWrapper(
    val turns: List<LiveTurn>,
    val turnComplete: Boolean = true
)

@Serializable
data class LiveTurn(
    val role: String,
    val parts: List<LivePart>
)

@Serializable
data class LivePart(
    val inlineData: LiveBlob? = null,
    val text: String? = null
)

// ── ToolResponse ─────────────────────────────────────────────────

@Serializable
data class LiveToolResponseMessage(
    val toolResponse: LiveToolResponseWrapper
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
    val serverContent: LiveServerContent? = null,
    val setupComplete: JsonObject? = null,
    val toolCall: LiveToolCallWrapper? = null,
    val error: LiveError? = null
)

@Serializable
data class LiveToolCallWrapper(
    val functionCalls: List<LiveFunctionCall>
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
    val modelTurn: LiveModelTurn? = null,
    val turnComplete: Boolean? = null,
    val interrupted: Boolean? = null,
    val inputTranscription: LiveTranscription? = null,
    val outputTranscription: LiveTranscription? = null
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

    val defaultVoice: VoiceProfile = all.first { it.name == "Aoede" }
}

// ═══════════════════════════════════════════════════════════════════
// LiveGeminiService
// ═══════════════════════════════════════════════════════════════════

class LiveGeminiService(
    private val client: HttpClient,
    private var apiKey: String,
    private var liveModelName: String = "gemini-3.1-flash-live-preview",
    private val maxRetries: Int = 3,
    private val baseRetryDelayMs: Long = 1000L
) {
    private var webSocketSession: DefaultWebSocketSession? = null
    private var isSetupComplete = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

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

    private val LIVE_SYSTEM_PROMPT = """คุณคือ JARVIS ผู้เชี่ยวชาญด้านการเงินและการวิเคราะห์ระดับโลก
กฎการทำงานระดับ Agentic (STRICT):
1. การรวบรวมข้อมูล: เมื่อผู้ใช้ถามถึงตลาดหรือหุ้น คุณต้องค้นหาข้อมูลทีละขั้นตอน (Multi-turn):
   - ขั้นที่ 1: ใช้ `trading_market_snapshot` เพื่อดูภาพรวม
   - ขั้นที่ 2: พิจารณาตัวที่โดดเด่น (บวก/ลบมากที่สุด) และใช้ `trading_price` เพื่อเจาะลึกรายละเอียดตัวนั้นๆ เสมอ
   - ขั้นที่ 3: ห้ามสรุปทันทีหากข้อมูลยังไม่ลึกพอ ให้หาข้อมูลจนกว่าจะมั่นใจ
2. การรายงานผล (IMPORTANT):
   - สำหรับในแชท: คุณต้องใช้เครื่องมือ `analyze_and_display_report` เพื่อส่งบทวิเคราะห์ที่ละเอียด มีความหนา มีตาราง และการวิเคราะห์รายตัวที่ครบถ้วน (เหมือนในโหมด Chat)
   - สำหรับเสียงพูด: ให้สรุปเฉพาะ "ใจความสำคัญ" และ "แนวโน้ม" (Key Takeaways) สั้นๆ 3-5 ประโยค ไม่ต้องอ่านข้อมูลในตารางทั้งหมด
3. บุคลิก: ฉลาด มั่นใจ ตรงไปตรงมา มีความกระตือรือล้นที่จะหาคำตอบที่ดีที่สุดให้ผู้ใช้"""

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
                        handleServerFrame(text)
                    }

                    val reason = closeReason.await()
                    logDebug("LiveGemini", "Session closed: ${reason?.knownReason} — ${reason?.message}")
                }
                break
            } catch (e: Exception) {
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
                    // DISALBED: Suppress model text/transcripts in Chat UI for Live Mode
                    /*
                    part.text?.let { text ->
                        _textOutputFlow.emit(text)
                        if (isToolRequest(text)) {
                            logDebug("LiveGemini", "🔧 Bridge tool request detected")
                            _bridgeToolRequestFlow.emit(text)
                        }
                    }
                    */
                }

                content.inputTranscription?.text?.let { text ->
                    logDebug("LiveGemini", "🎤 User said: $text")
                    // แสดงฝั่ง User (กล่องขวา) และขึ้นกล่องใหม่เสมอ (append = false)
                    _textOutputFlow.emit(LiveTextUpdate("🎤 $text", role = "user", append = false))
                }
                
                // DISABLED: Suppress output transcription in Chat UI
                /*
                content.outputTranscription?.text?.let { text ->
                    logDebug("LiveGemini", "🤖 JARVIS: $text")
                    _textOutputFlow.emit(text)
                }
                */
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
            val text = "Tool result for $toolName: $result. Please present the detailed data and lists to the user clearly. Do not over-summarize."
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
            session.send(Frame.Text(buildJson()))
        } catch (e: Exception) {
            logError("LiveGemini", "Send failed", e)
        }
    }

    suspend fun emitTextToChat(text: String) {
        // บังคับขึ้นกล่องใหม่เสมอ (append = false) สำหรับผลลัพธ์จาก Tool
        _textOutputFlow.emit(LiveTextUpdate(text, role = "model", append = false))
    }
}

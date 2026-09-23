package com.skyliner2008.jarvis.data

import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import com.skyliner2008.jarvis.tools.GeminiTool
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
    /**
     * เริ่มบีบอัดเมื่อ context โตถึงจำนวนนี้
     *
     * [สำคัญ] Live API คิดโทเคนแบบทบต้น — **คิดทั้ง context ที่สะสมอยู่ใหม่ทุก turn**
     * (ดู Live API best practices: "Past tokens are re-processed and accounted for in each new turn")
     * และเก็บประวัติเสียงเป็น audio token ซึ่งหนักกว่าข้อความมาก
     *
     * ด้วย TPM 65K ถ้าไม่จำกัด context จะโตจนกินโควตาหมดเอง:
     *   context 20K → 1 turn ≈ 21K tokens → เหลือแค่ ~3 turn/นาที
     *   context 50K → 1 turn ≈ 51K tokens → เหลือ ~1 turn/นาที
     */
    @SerialName("triggerTokens") val triggerTokens: Long? = null,
    // ต้องไม่มี default — encodeDefaults=false จะตัด field ทิ้ง แล้วเหลือ {} ซึ่งไม่ใช่ config ที่ถูกต้อง
    @SerialName("slidingWindow") val slidingWindow: JsonObject
) {
    companion object {
        /** จำนวนโทเคนที่คงไว้หลังบีบอัด */
        const val TARGET_TOKENS = 8_000L

        /** เริ่มบีบอัดเมื่อถึงจำนวนนี้ */
        const val TRIGGER_TOKENS = 25_000L

        /**
         * sliding window พร้อมเพดานที่ชัดเจน — ยืดอายุ session และคุมต้นทุนต่อ turn
         *
         * เดิมส่ง `slidingWindow: {}` เปล่าๆ ไม่มี trigger/target
         * → context โตไปเรื่อยจนชนเพดานโมเดล และค่าใช้จ่ายต่อ turn สูงขึ้นตลอดการสนทนา
         */
        fun default() = LiveContextWindowCompressionConfig(
            triggerTokens = TRIGGER_TOKENS,
            slidingWindow = JsonObject(mapOf("targetTokens" to JsonPrimitive(TARGET_TOKENS)))
        )

        /** config เดิม (sliding window ล้วน) — ใช้เป็น fallback เมื่อ server ปฏิเสธ config แบบมีพารามิเตอร์ */
        fun bare() = LiveContextWindowCompressionConfig(slidingWindow = JsonObject(emptyMap()))
    }
}

@Serializable
data class LiveSystemInstruction(
    val parts: List<LivePart>
)

@Serializable
data class LiveGenerationConfig(
    @SerialName("response_modalities") val responseModalities: List<String>? = null,
    /** โหมดแปลภาษาเท่านั้น (gemini-3.5-live-translate-preview) */
    @SerialName("translationConfig") val translationConfig: LiveTranslationConfig? = null,
    @SerialName("speech_config") val speechConfig: LiveSpeechConfig? = null,
    @SerialName("thinking_config") val thinkingConfig: LiveThinkingConfig? = null
)

/** ตั้งค่าโหมดแปลภาษาแบบเรียลไทม์ (Live Translate) */
@Serializable
data class LiveTranslationConfig(
    @SerialName("targetLanguageCode") val targetLanguageCode: String,
    /** true = ถ้าผู้พูดพูดภาษาปลายทางอยู่แล้วให้พูดตาม, false = เงียบ */
    @SerialName("echoTargetLanguage") val echoTargetLanguage: Boolean = false
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
    @SerialName("voiceName") val voiceName: String? = null
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
    val text: String? = null,
    /** แจ้ง server ว่าไมค์หยุดสตรีม (เช่น mute) ให้ flush เสียงที่ค้างใน VAD */
    @SerialName("audioStreamEnd") val audioStreamEnd: Boolean? = null
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
    @SerialName("toolCallCancellation") val toolCallCancellation: LiveToolCallCancellation? = null,
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

/** server ยกเลิก function call ที่ยังไม่ตอบ (เช่น ผู้ใช้พูดแทรก) — client ต้องไม่ส่ง response ของ id เหล่านี้ */
@Serializable
data class LiveToolCallCancellation(
    val ids: List<String> = emptyList()
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
    val args: Map<String, String>,
    /** WebSocket lifecycle ที่ออก call นี้ — response ต้องส่งกลับ session เดิมเท่านั้น */
    val sessionGeneration: Long = 0L
)

/** PCM ของ model พร้อม epoch — chunk ที่ epoch เก่ากว่าปัจจุบัน (ก่อน barge-in) ต้องทิ้ง ไม่เล่นต่อ */
class LiveAudioChunk(
    val epoch: Long,
    val pcm: ByteArray
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
    private val memoryManager: com.skyliner2008.jarvis.memory.JarvisMemoryManager? = null,
    private val maxRetries: Int = 3,
    private val baseRetryDelayMs: Long = 1000L
) {

    // Small protocol guard: the server's setupComplete frame can race with the MIC sender.
    // Do not send the first realtime PCM frame in the same scheduling slice as setupComplete.
    // Dropping a few hundred ms is preferable to sending an out-of-phase frame that can close
    // the Live session with NOT_CONSISTENT / INVALID_ARGUMENT.
    @kotlin.jvm.Volatile
    private var realtimeInputReadyAtMs: Long = 0L
    private val realtimeInputReadyGraceMs: Long = 250L
    /** คาบต่ำสุดของ log handle resumption — server อัปเดตทุก 1-2 วินาที */
    private val resumptionLogIntervalMs: Long = 60_000L
    // Live-specific credential/model rotation. The normal GeminiService fallback chain
    // is request/response based and cannot be reused directly for a persistent WebSocket.
    private var configuredLiveModelName: String = liveModelName.removePrefix("models/")
    private var liveApiKeys: List<String> = listOf(apiKey).filter { it.isNotBlank() }
    private var liveModelChain: List<String> = com.skyliner2008.jarvis.data.ModelConfig.getLiveFallbackChain(liveModelName)
    private var liveKeyIndex: Int = 0
    private var liveModelIndex: Int = 0
    private val triedLiveCredentials = mutableSetOf<String>()
    // ฟิลด์ด้านล่างถูกอ่าน/เขียนจากหลาย coroutine (WS reader, mic sender, tool bridge) — ต้อง @Volatile
    @kotlin.jvm.Volatile
    private var webSocketSession: DefaultWebSocketSession? = null
    @kotlin.jvm.Volatile
    private var isSetupComplete = false

    /** true เมื่อผู้ใช้กดหยุดเอง — แยกจาก server-initiated close (GoAway/session timeout) ที่ต้อง reconnect */
    @kotlin.jvm.Volatile
    private var userRequestedDisconnect = false
    /** session รอบปัจจุบันเคย READY แล้วหรือไม่ — ใช้ reset retry counter เมื่อ server ปิด session ที่เคยใช้งานได้ */
    @kotlin.jvm.Volatile
    private var sessionWasReady = false

    /** เพิ่มทุกครั้งที่เรียก connectAndListen — loop เก่าที่ยังปิดตัวไม่เสร็จต้องไม่ทับสถานะของ loop ใหม่ */
    @kotlin.jvm.Volatile
    private var connectCallId: Long = 0L

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // คลังความจำระยะสั้น: เก็บประโยคสุดท้ายที่ผู้ใช้พูด เพื่อใช้เตือนสมาธิ AI ตอนเปิดเครื่องมือ
    /** ประโยคของผู้ใช้ใน turn ล่าสุด (รวมทุกชิ้นของ transcription แล้ว) — guard ต่างๆ ใช้ตัดสินคำขอ */
    @kotlin.jvm.Volatile
    var lastUserText: String = ""
    /** เพิ่มขึ้นทุกครั้งที่ผู้ใช้เริ่มพูด turn ใหม่ — ใช้นับ tool call ต่อ turn */
    @kotlin.jvm.Volatile
    var userTurnSerial: Int = 0
        private set

    /**
     * Live API ส่ง transcription มาเป็น "ชิ้น" (เช่น "ราคา", " ทอง", " ตอนนี้") ไม่ใช่ประโยคสะสม —
     * เดิมโค้ดเขียนทับด้วยชิ้นล่าสุด ทำให้แชท/ประวัติ/guard เห็นแค่คำท้ายๆ ของประโยค
     */
    private fun mergeTranscript(previous: String?, chunk: String): String =
        LiveProtocol.mergeTranscript(previous, chunk)

    /** ประโยคของผู้ใช้ใน turn ปัจจุบันถูกส่งออกทาง userTurnFinalFlow แล้วหรือยัง */
    private var userTurnFinalized = true

    private val _userTurnFinalFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /**
     * ประโยคของผู้ใช้ที่ "พูดจบแล้ว" หนึ่งครั้งต่อ turn — ส่งเมื่อ model เริ่มตอบ (audio/text/tool) หรือ turn จบ
     * คำสั่งลัดฝั่งเครื่องต้องใช้ flow นี้ ไม่ใช่ transcript ระหว่างพูด (ที่ยิงซ้ำทุกชิ้น)
     */
    val userTurnFinalFlow: Flow<String> = _userTurnFinalFlow.asSharedFlow()

    /** เวลาที่ผู้ใช้พูดจบ turn ล่าสุด — ใช้วัด latency ก่อนเสียงแรกของ model (ข้อมูลสำหรับปรับ thinking level) */
    @kotlin.jvm.Volatile
    private var userTurnFinalAtMs: Long = 0L

    /**
     * เวลาของ transcription ชิ้นสุดท้ายที่ผู้ใช้พูด — ใกล้เคียง "พูดจบ" จริงที่สุด
     * เดิมวัด latency จาก userTurnFinalAtMs ซึ่งตั้งตอน model เริ่มตอบ จึงได้ 0ms บ้าง
     * และถ้า session ก่อนหน้าค้างค่าไว้จะได้เลขเพี้ยนหลักหมื่น ms (log 2026-09-16: 67258ms)
     */
    @kotlin.jvm.Volatile
    private var lastUserSpeechAtMs: Long = 0L

    /** log handle ของ session resumption แบบมีคาบ — server ส่งทุก 1-2 วิ ทำให้ logcat ท่วม */
    @kotlin.jvm.Volatile
    private var lastResumptionLogAtMs: Long = 0L

    private suspend fun finalizeUserTurn() {
        if (userTurnFinalized) return
        userTurnFinalized = true
        userTurnFinalAtMs = System.currentTimeMillis()
        val text = pendingUserTurnText?.trim().orEmpty()
        if (text.isNotBlank()) _userTurnFinalFlow.emit(text)
    }

    /** turn ล่าสุดของ session นี้ — ใช้เป็นบริบทตอน reconnect ที่ไม่มี resumption handle */
    private val recentTurns = ArrayDeque<Pair<String, String>>()
    private val maxRecentTurns = 12

    private fun rememberTurn(role: String, text: String?) {
        val clean = text?.trim().orEmpty()
        if (clean.isBlank()) return
        synchronized(recentTurns) {
            recentTurns.addLast(role to clean.take(600))
            while (recentTurns.size > maxRecentTurns) recentTurns.removeFirst()
        }
    }

    /** ข้อความที่จะส่งให้ model พูดทันทีหลัง session READY (เช่นทักยืนยันเสียงใหม่หลังเปลี่ยนเสียง). */
    @kotlin.jvm.Volatile
    var pendingGreetingOnReady: String? = null

    /** session พร้อมรับ realtime input จริง (setupComplete แล้วและ socket ยังเปิด) */
    val isReady: Boolean
        get() = isSetupComplete && webSocketSession?.isActive == true

    /** แจ้งเตือนเมื่อมีโมเดล Live ที่พร้อมใช้งานจริง (setupComplete สำเร็จ) เพื่อให้บันทึกจำข้าม session */
    var onLiveModelPromoted: ((String) -> Unit)? = null

    // Monotonically increasing WebSocket lifecycle id. A READY coroutine must never send
    // through a newer socket after the socket that produced READY has been replaced.
    @kotlin.jvm.Volatile
    private var liveSessionGeneration: Long = 0L
    private var greetingSentForReady: Boolean = false

    /** WebSocket lifecycle ปัจจุบัน — tool bridge ใช้ตรวจว่า response ยังส่งกลับ session เดิมได้ */
    val currentSessionGeneration: Long get() = liveSessionGeneration

    /**
     * เพิ่มทุกครั้งที่ generation ถูกขัดจังหวะ — chunk ที่ค้างใน buffer ด้วย epoch เก่าต้องถูกทิ้ง
     * (เดิม stopPlaying() ล้างแค่ AudioTrack แต่ chunk ใน SharedFlow ยังถูกเขียนลงลำโพงต่อ)
     */
    @kotlin.jvm.Volatile
    var audioEpoch: Long = 0L
        private set

    // buffer ใหญ่พอสำหรับคำตอบยาว (~หลายสิบวินาที) — server ส่งเสียงเร็วกว่า realtime และ AudioTrack.write() blocking
    // buffer เล็ก (128) ทำให้ emit suspend → ลูปอ่าน WebSocket หยุด → interrupted/transcript/tool ค้างตาม
    private val _audioOutputFlow = MutableSharedFlow<LiveAudioChunk>(extraBufferCapacity = 4096)
    val audioOutputFlow: Flow<LiveAudioChunk> = _audioOutputFlow.asSharedFlow()

    /** อีเวนต์ข้อความจาก Live mode (ใช้สำหรับ UI ตรวจสอบว่าจะขึ้นกล่องใหม่หรือพิมพ์ต่อ) */
    data class LiveTextUpdate(
        val text: String, 
        val role: String = "model", 
        val append: Boolean = true,
        val replace: Boolean = false,
        val isStatic: Boolean = false // If true, this box won't be overwritten by subsequent 'replace' updates
    )

    // emit ตรงจาก handleServerFrame ตามลำดับเฟรม (เดิม scope.launch ต่อ update → ลำดับสลับได้)
    private val _textOutputFlow = MutableSharedFlow<LiveTextUpdate>(extraBufferCapacity = 256)
    val textOutputFlow: Flow<LiveTextUpdate> = _textOutputFlow.asSharedFlow()

    // เดิมไม่มี buffer → emit suspend จน tool ก่อนหน้ารันเสร็จ → ลูปอ่าน WebSocket หยุดทั้งหมดระหว่างรัน tool
    private val _nativeToolCallFlow = MutableSharedFlow<LiveToolCallEvent>(extraBufferCapacity = 64)
    val nativeToolCallFlow: Flow<LiveToolCallEvent> = _nativeToolCallFlow.asSharedFlow()

    private val _toolCallCancellationFlow = MutableSharedFlow<List<String>>(extraBufferCapacity = 16)
    /** id ของ function call ที่ server ยกเลิก — bridge ต้องหยุดงานและไม่ส่ง response */
    val toolCallCancellationFlow: Flow<List<String>> = _toolCallCancellationFlow.asSharedFlow()

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

    // ── Pre-READY audio ─────────────────────────────────────────────
    // เสียงไมค์ก่อน setupComplete ถูกทิ้งโดยตั้งใจ: การ burst-flush เสียงเก่าหลัง READY เคยทำให้ session
    // ปิดด้วย NOT_CONSISTENT (เดิมเก็บ ring buffer 400 chunk แต่สุดท้ายก็ clear ทิ้งตอน READY อยู่ดี)
    // UI ต้องบอกผู้ใช้ให้รอ AI ทักก่อนพูด — ไม่ใช่บอกว่าเสียงถูกเก็บไว้
    @kotlin.jvm.Volatile
    private var droppedPreReadyChunks = 0
    private var sentAudioChunks: Long = 0L

    // Live API session resumption: keeps the conversation alive across periodic WebSocket resets.
    // ต้องส่ง sessionResumption ใน setup ตั้งแต่ครั้งแรก server ถึงจะส่ง SessionResumptionUpdate มา
    // (เดิมส่ง null ครั้งแรก → ไม่เคยได้ handle → บริบทหายทุก GoAway)
    @kotlin.jvm.Volatile
    private var sessionResumptionHandle: String? = null
    /** ปิดตัวเองถ้า server ปฏิเสธ setup ที่มี sessionResumption (self-healing — เคยเจอ NOT_CONSISTENT กับ 3.1) */
    private var sessionResumptionEnabled = true
    /** context window compression (sliding window) — ยืดอายุ session; ปิดตัวเองถ้า server ปฏิเสธ setup */
    private var contextCompressionEnabled = true

    /**
     * ส่ง triggerTokens/targetTokens ไปด้วยหรือไม่
     * ถ้า server ปฏิเสธจะลดเหลือ sliding window ล้วน ก่อนจะปิด compression ทั้งหมด
     */
    private var compressionTuningEnabled = true
    private var goAwayReceived = false
    private var connectionStartedAtMs: Long = 0L

    /** รีเซ็ต session resumption handle เพื่อให้การเปลี่ยนโหมด (เช่น Pet <-> Assistant) ไม่ดึงบริบทโหมดเก่ากลับมา */
    fun resetSessionResumption() {
        sessionResumptionHandle = null
        logDebug("LiveGemini", "🔄 Session resumption handle reset for clean persona transition")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    // ย้ายไปรวมศูนย์ที่ JarvisPersona (2026-07-29)
    // — ใช้ getter เพื่อให้ identity ที่ผู้ใช้/AI ปรับแต่งมีผลทันทีทุก session
    private val LIVE_SYSTEM_PROMPT: String
        get() = com.skyliner2008.jarvis.ai.JarvisPersona.LIVE_SYSTEM_PROMPT

    private var selectedVoiceName: String = "Aoede" // Default
    /** ระดับการคิดก่อนตอบของ Live รุ่น 3.x — minimal/low/medium/high (ค่าเริ่มต้นของ API คือ minimal) */
    var liveThinkingLevel: String = "low"

    /**
     * โมเดลที่ปฏิเสธ `thinking_config` (NOT_CONSISTENT — Thinking level is not supported for this model)
     * ยืนยันจาก log 2026-09-16: `gemini-3.8-live` ไม่รับ แต่ `gemini-3.1-flash-live-preview`
     * และ `gemini-3.8-live-extended-thinking` รับ — จำไว้แล้วไม่ส่งซ้ำใน session ถัดไป
     */
    private val thinkingUnsupportedModels = mutableSetOf<String>()

    /**
     * callId → คำถามของผู้ใช้ตอนที่ tool ถูกเรียก
     * ผู้ใช้ยิงคำถามซ้อนกันได้เร็วกว่าที่ tool จะเสร็จ พอผลกลับมาโมเดลมักตอบเฉพาะคำถามล่าสุด
     * แล้วทิ้งคำถามก่อนหน้าไปเงียบๆ (log 2026-09-17 00:13: 3.8-live ตอบแค่ราคาทอง ทิ้ง 5 มิติ + ปฏิทิน)
     * จึงติดป้ายกำกับไปกับผลลัพธ์ว่าอันนี้เป็นคำตอบของคำถามไหน
     */
    private val toolCallQuestions = mutableMapOf<String, String>()

    private fun supportsThinkingLevel(model: String): Boolean {
        val clean = model.removePrefix("models/")
        return clean.startsWith("gemini-3") && !thinkingUnsupportedModels.contains(clean)
    }

    fun updateConfig(newApiKey: String, newModelName: String, voiceName: String = "Aoede") {
        apiKey = newApiKey
        configuredLiveModelName = newModelName.removePrefix("models/")
        liveModelName = configuredLiveModelName
        selectedVoiceName = voiceName
        if (liveApiKeys.isEmpty() || !liveApiKeys.contains(newApiKey)) {
            liveApiKeys = listOf(newApiKey).filter { it.isNotBlank() } + liveApiKeys.filter { it != newApiKey }
        }
        liveModelChain = com.skyliner2008.jarvis.data.ModelConfig.getLiveFallbackChain(liveModelName)
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

    private fun isRemoteSocketCloseException(e: Throwable): Boolean {
        val className = e::class.simpleName ?: ""
        val msg = e.message ?: ""
        val causeClassName = e.cause?.let { it::class.simpleName } ?: ""
        val causeMsg = e.cause?.message ?: ""
        return className.contains("EOFException") ||
                className.contains("SocketClosed") ||
                className.contains("ClosedReceiveChannelException") ||
                className.contains("SocketException") ||
                causeClassName.contains("EOFException") ||
                causeClassName.contains("SocketClosed") ||
                causeClassName.contains("SocketException") ||
                msg.contains("EOF", ignoreCase = true) ||
                msg.contains("unexpected end of stream", ignoreCase = true) ||
                msg.contains("Connection reset", ignoreCase = true) ||
                msg.contains("Software caused connection abort", ignoreCase = true) ||
                msg.contains("Socket closed", ignoreCase = true) ||
                msg.contains("Channel was closed", ignoreCase = true) ||
                causeMsg.contains("EOF", ignoreCase = true) ||
                causeMsg.contains("Connection reset", ignoreCase = true) ||
                causeMsg.contains("unexpected end of stream", ignoreCase = true)
    }

    suspend fun connectAndListen(tools: GeminiTool? = null, historyContext: String = "", coreContext: String = "") {
        if (apiKey.isBlank()) {
            logError("LiveGemini", "API key is blank — aborting connection")
            _connectionState.value = ConnectionState.Error("API key is missing")
            return
        }

        val callId = ++connectCallId
        userRequestedDisconnect = false
        triedLiveCredentials.clear()
        // session ที่ผู้ใช้เริ่มใหม่ต้องไม่ resume บทสนทนาของ session ก่อน (persona/voice อาจเปลี่ยนแล้ว)
        sessionResumptionHandle = null
        synchronized(recentTurns) { recentTurns.clear() }
        userTurnFinalized = true
        // Always start attempt 1 with the user's explicitly configured model
        liveModelName = configuredLiveModelName
        // Refresh fallback chain using latest dynamic models & promotions
        liveModelChain = com.skyliner2008.jarvis.data.ModelConfig.getLiveFallbackChain(liveModelName)
        // Start a fresh retry budget for this user-initiated Live session.
        liveKeyIndex = liveApiKeys.indexOf(apiKey).takeIf { it >= 0 } ?: 0
        liveModelIndex = liveModelChain.indexOf(liveModelName).takeIf { it >= 0 } ?: 0
        var attempt = 0
        var fallbackCount = 0
        /** ลองโมเดลเดิมซ้ำกี่ครั้งเมื่อโดนโควตา/ลิมิตต่อนาที ก่อนยอมสลับไปโมเดลสำรอง */
        val maxQuotaRetries = 2
        var quotaRetryCount = 0
        while (attempt <= maxRetries) {
            isSetupComplete = false
            sessionWasReady = false
            goAwayReceived = false
            greetingSentForReady = false
            // ตัวจับเวลาเป็นของ session — ไม่รีเซ็ตแล้ว latency ของ session ใหม่จะนับต่อจากของเก่า
            lastUserSpeechAtMs = 0L
            userTurnFinalAtMs = 0L
            lastResumptionLogAtMs = 0L
            toolCallQuestions.clear()
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
            var timedOutWaitingForSetup = false
            // URL ต้องสร้างใหม่ทุก attempt — rotateLiveCredentialOnQuota() เปลี่ยน apiKey ระหว่าง loop
            val url = LiveProtocol.buildUrl(apiKey)
            val resumeHandle = sessionResumptionHandle?.takeIf { attempt > 0 && it.isNotBlank() }
            val sentResumptionConfig = sessionResumptionEnabled
            val sentCompressionConfig = contextCompressionEnabled
            val sentThinkingConfig = supportsThinkingLevel(liveModelName)
            // reconnect ที่ไม่มี handle (server ไม่ได้ส่งมา หรือ resumption ถูกปิด) → ใส่ turn ล่าสุดของ session นี้เป็นบริบทแทน
            val effectiveHistory = if (attempt > 0 && resumeHandle == null) {
                LiveProtocol.buildReconnectHistory(historyContext, synchronized(recentTurns) { recentTurns.toList() })
            } else historyContext
            try {
                client.webSocket(url) {
                    webSocketSession = this

                    // READY ของ gemini-3.1-flash-live-preview วัดได้ ~0.8–1.5 วิ (changelog 2026-09-09)
                    // 6 วิ เผื่อ handshake บนเน็ตมือถือ — ถ้าเกินถือว่า endpoint ค้าง ให้ fallback
                    val setupWatchdog = launch {
                        delay(6000L)
                        if (!isSetupComplete) {
                            logDebug("LiveGemini", "⏱️ setupComplete timeout (6000ms) for model $liveModelName — terminating WebSocket to trigger immediate fallback")
                            timedOutWaitingForSetup = true
                            com.skyliner2008.jarvis.data.ModelConfig.penalizeLiveModel(liveModelName)
                            this@webSocket.cancel(kotlinx.coroutines.CancellationException("setupComplete timeout"))
                        }
                    }

                    val fullModel = if (liveModelName.startsWith("models/")) liveModelName else "models/$liveModelName"

                    val setup = LiveSetupMessage(
                        setup = LiveSetup(
                            model = fullModel,
                            outputAudioTranscription = JsonObject(emptyMap()),
                            inputAudioTranscription = buildJsonObject {
                                putJsonArray("languageCodes") {
                                    add("th-TH")
                                    add("en-US")
                                }
                            },
                            realtimeInputConfig = LiveRealtimeInputConfig(
                                automaticActivityDetection = LiveAutomaticActivityDetection(
                                    disabled = false,
                                    // START_SENSITIVITY_LOW + padding 500ms — เดิมไวเกินจนเสียงรบกวน/ลมหายใจ
                                    // นับเป็น "ผู้ใช้พูดแทรก" ระหว่างรอผล tool แล้ว server ทิ้งคำตอบทั้ง turn
                                    // (log 2026-09-16: Interrupted 0.8 วิหลัง tool response → ไม่พูดผลเครื่องมือแรก)
                                    startOfSpeechSensitivity = "START_SENSITIVITY_LOW",
                                    prefixPaddingMs = 500,
                                    silenceDurationMs = 1200
                                )
                            ),
                            // ส่ง {} ครั้งแรกเพื่อเปิดรับ SessionResumptionUpdate และส่ง handle ตอน reconnect
                            // ถ้า server ปฏิเสธ (NOT_CONSISTENT/INVALID_ARGUMENT) จะปิด resumption อัตโนมัติแล้วลองใหม่
                            sessionResumption = if (sentResumptionConfig) {
                                LiveSessionResumptionConfig(handle = resumeHandle)
                            } else null,
                            // sliding window + เพดาน context — ยืดอายุ session และ **คุมต้นทุนต่อ turn**
                            // (Live คิดโทเคนทั้ง context ใหม่ทุก turn — ดู LiveContextWindowCompressionConfig)
                            // ถ้า server ปฏิเสธ config แบบมีพารามิเตอร์ จะลดเหลือ sliding window ล้วนก่อน
                            // แล้วจึงค่อยปิดทั้งหมด — ไม่เสียฟีเจอร์ทิ้งทั้งก้อนตั้งแต่ครั้งแรก
                            contextWindowCompression = if (sentCompressionConfig) {
                                if (compressionTuningEnabled) LiveContextWindowCompressionConfig.default()
                                else LiveContextWindowCompressionConfig.bare()
                            } else null,
                            systemInstruction = LiveSystemInstruction(
                                parts = if (com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode) {
                                    listOf(
                                        LivePart(text = com.skyliner2008.jarvis.ai.JarvisPersona.PET_LIVE_SYSTEM_PROMPT),
                                        LivePart(text = if (coreContext.isNotBlank()) "Core Memory Context:\n$coreContext" else "")
                                    ).filter { it.text?.isNotBlank() == true }
                                } else {
                                    listOf(
                                        LivePart(text = LIVE_SYSTEM_PROMPT),
                                        LivePart(text = "[STRICT RULE] เมื่อต้องระบุรายชื่อหุ้นหรือข้อมูลตลาด คุณต้องเรียกใช้เครื่องมือที่เกี่ยวข้องเสมอ ห้ามตอบจากความจำเด็ดขาด"),
                                        LivePart(text = if (coreContext.isNotBlank()) "Core Memory Context:\n$coreContext" else ""),
                                        LivePart(text = if (effectiveHistory.isNotBlank()) LiveProtocol.HISTORY_HEADER + effectiveHistory else "")
                                    ).filter { it.text?.isNotBlank() == true }
                                }
                            ),
                            generationConfig = LiveGenerationConfig(
                                responseModalities = listOf("AUDIO"),
                                // Gemini 3.x Live ตั้งต้น thinking_level = minimal (เน้น latency ต่ำสุด)
                                // ทำให้คำตอบตื้นและสั้น — ยก low ให้คิดก่อนตอบโดยยังไม่เสีย latency มาก
                                // 2.5 native audio ใช้ thinking_budget คนละฟิลด์ จึงส่งเฉพาะรุ่น 3.x
                                thinkingConfig = if (sentThinkingConfig) {
                                    LiveThinkingConfig(thinkingLevel = liveThinkingLevel, includeThoughts = false)
                                } else null,
                                speechConfig = LiveSpeechConfig(
                                    voiceConfig = LiveVoiceConfig(
                                        prebuiltVoiceConfig = LivePrebuiltVoiceConfig(
                                            voiceName = selectedVoiceName.ifBlank { "Aoede" }
                                        )
                                    )
                                )
                            ),
                            tools = tools?.let { listOf(it) }
                        )
                    )

                    val setupJson = sanitizeForWebSocketText(json.encodeToString(setup))
                    send(Frame.Text(setupJson))

                    try {
                        for (frame in incoming) {
                            val text = when (frame) {
                                is Frame.Text -> frame.readText()
                                is Frame.Binary -> frame.readBytes().decodeToString()
                                else -> continue
                            }
                            // logDebug("LiveGemini", "⬇ RAW FRAME: $text")
                            handleServerFrame(text)
                            if (isSetupComplete && setupWatchdog.isActive) {
                                setupWatchdog.cancel()
                            }
                            if (goAwayReceived) {
                                // Google explicitly expects the client to close after GoAway; waiting for
                                // the server aborts the socket and produces VIOLATED_POLICY in practice.
                                logDebug("LiveGemini", "🛑 GoAway received — closing current WebSocket cleanly for session resumption")
                                close(CloseReason(CloseReason.Codes.NORMAL, "GoAway handled; reconnect with session resumption"))
                                break
                            }
                        }
                    } finally {
                        setupWatchdog.cancel()
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
                if (sessionWasReady) {
                    fallbackCount = 0
                    quotaRetryCount = 0
                }
                if (terminalCloseReason != null) {
                    val normalizedTerminal = terminalCloseReason!!.lowercase()
                    val quotaError =
                        normalizedTerminal.contains("exceeded your current quota") ||
                        normalizedTerminal.contains("quota exceeded") ||
                        (normalizedTerminal.contains("quota") && normalizedTerminal.contains("internal_error"))
                    // เช็ค thinking ก่อนเสมอ — server บอกสาเหตุมาตรงๆ ถ้าไปปิด compression/resumption ก่อน
                    // จะโทษผิดตัวแล้วเสียฟีเจอร์ทั้งสองไปฟรีๆ (log 2026-09-16: 3.8-live ถูก rotate ทิ้งทั้งที่ใช้ได้)
                    if (!quotaError && sentThinkingConfig && normalizedTerminal.contains("thinking")) {
                        thinkingUnsupportedModels.add(liveModelName.removePrefix("models/"))
                        logDebug("LiveGemini", "🧯 $liveModelName ไม่รองรับ thinking_level — ปิดสำหรับโมเดลนี้แล้วลองใหม่")
                        attempt = 0
                        continue
                    }
                    // ลดระดับ compression ทีละขั้นก่อนปิดทิ้ง — เพดาน context มีค่ากับ TPM มาก
                    // (ถ้าปิดหมด context จะโตอิสระ แล้วต้นทุนต่อ turn พุ่งจนกิน TPM 65K หมด)
                    if (!quotaError && sentCompressionConfig && compressionTuningEnabled) {
                        compressionTuningEnabled = false
                        logDebug("LiveGemini", "🧯 contextWindowCompression แบบมีพารามิเตอร์ถูกปฏิเสธ ($terminalCloseReason) — ลดเหลือ sliding window ล้วนแล้วลองใหม่")
                        attempt = 0
                        continue
                    }
                    if (!quotaError && sentCompressionConfig && contextCompressionEnabled) {
                        // ปิด feature ที่เพิ่งเปิดทีละอย่างก่อนโทษโมเดล — compression ก่อน แล้วค่อย resumption
                        contextCompressionEnabled = false
                        logDebug("LiveGemini", "🧯 Setup with contextWindowCompression rejected ($terminalCloseReason) — disabling compression and retrying $liveModelName")
                        attempt = 0
                        continue
                    }
                    if (!quotaError && sentResumptionConfig && sessionResumptionEnabled) {
                        // Protocol rejection with sessionResumption in setup — disable it for this
                        // service lifetime and retry the same model once before rotating models.
                        sessionResumptionEnabled = false
                        sessionResumptionHandle = null
                        logDebug("LiveGemini", "🧯 Setup with sessionResumption rejected ($terminalCloseReason) — disabling resumption and retrying $liveModelName")
                        attempt = 0
                        continue
                    }
                    // ข้อความ "exceeded your current quota" ครอบคลุมทั้งโควตารายวันหมด และลิมิตต่อนาที (RPM/TPM)
                    // การสลับโมเดลทันทีทำให้ตกไปอยู่โมเดลสำรองที่เรียก tool ได้แย่กว่า ทั้งที่รออีก 2 วินาทีก็ผ่าน
                    // (พบจริง 2026-09-20: สลับโหมดทำให้เปิด session 3 ครั้งใน 13 วินาที → โดนลิมิตต่อนาที
                    //  แล้วตกไป extended-thinking ซึ่งตอบว่า "เปิดแอปไม่ได้")
                    if (quotaError && quotaRetryCount < maxQuotaRetries) {
                        quotaRetryCount++
                        val waitMs = 2_000L * quotaRetryCount
                        logDebug("LiveGemini", "⏳ Live quota/rate limit — รออีก ${waitMs}ms แล้วลอง $liveModelName ซ้ำ ($quotaRetryCount/$maxQuotaRetries)")
                        delay(waitMs)
                        attempt = 0
                        continue
                    }
                    if (quotaError && rotateLiveCredentialOnQuota()) {
                        // แจ้งผู้ใช้ให้เห็นในแชท — เดิมเงียบสนิท ผู้ใช้เห็นแค่ JARVIS ตอบว่า "ทำไม่ได้"
                        // โดยไม่รู้ว่าถูกสลับไปโมเดลสำรองแล้ว (พบจากทดสอบจริง 2026-09-20)
                        emitTextToChat(
                            "⚠️ Gemini ปฏิเสธด้วยเหตุผลโควตา/ลิมิตต่อนาที แม้ลองซ้ำแล้ว $maxQuotaRetries ครั้ง — " +
                                "สลับไปโมเดลสำรอง `$liveModelName` ให้อัตโนมัติ\n" +
                                "โมเดลสำรองอาจเรียกเครื่องมือควบคุมเครื่อง (เปิดแอป/แตะปุ่ม/อ่านจอ) ได้ไม่ครบเท่าเดิม " +
                                "ถ้าสั่งงานแล้วจาวิสบอกว่าทำไม่ได้ ให้เว้นสักครู่แล้วเปิดโหมดใหม่อีกครั้ง"
                        )
                        // Do not count a quota rotation as a transient reconnect retry.
                        // The next loop uses a different key/model and starts a fresh session.
                        attempt = 0
                        continue
                    }
                    if (!sessionWasReady && liveModelChain.size > 1 && fallbackCount < liveModelChain.size) {
                        fallbackCount++
                        val prevModel = liveModelName
                        liveModelIndex = (liveModelIndex + 1) % liveModelChain.size
                        liveModelName = liveModelChain[liveModelIndex]
                        sessionResumptionHandle = null
                        logDebug("LiveGemini", "🔄 Live setup rejected ($terminalCloseReason) with $prevModel — rotating to fallback model: $liveModelName ($fallbackCount/${liveModelChain.size})")
                        attempt = 0
                        continue
                    }
                    _connectionState.value = ConnectionState.Error(terminalCloseReason!!)
                    logError("LiveGemini", "🛑 Terminal Live API/session error: $terminalCloseReason")
                    break
                }
                if (!sessionWasReady && liveModelChain.size > 1 && fallbackCount < liveModelChain.size) {
                    fallbackCount++
                    val prevModel = liveModelName
                    liveModelIndex = (liveModelIndex + 1) % liveModelChain.size
                    liveModelName = liveModelChain[liveModelIndex]
                    sessionResumptionHandle = null
                    logDebug("LiveGemini", "🔄 Live session setup timeout/closed before READY with $prevModel — rotating to fallback model: $liveModelName ($fallbackCount/${liveModelChain.size})")
                    attempt = 0
                    continue
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
                if (e is kotlinx.coroutines.CancellationException) {
                    if (timedOutWaitingForSetup) {
                        logDebug("LiveGemini", "⏱️ setupComplete timeout caught — falling back immediately without handshake delay")
                        if (!sessionWasReady && liveModelChain.size > 1 && fallbackCount < liveModelChain.size) {
                            fallbackCount++
                            val prevModel = liveModelName
                            liveModelIndex = (liveModelIndex + 1) % liveModelChain.size
                            liveModelName = liveModelChain[liveModelIndex]
                            sessionResumptionHandle = null
                            logDebug("LiveGemini", "🔄 Live session setup timeout before READY with $prevModel — rotating to fallback model: $liveModelName ($fallbackCount/${liveModelChain.size})")
                            attempt = 0
                            continue
                        }
                    }
                    throw e
                }
                val remoteClose = isRemoteSocketCloseException(e)
                if (sessionWasReady && remoteClose) {
                    // Server-initiated TCP/EOF or unexpected socket closure on an already established Live session
                    logDebug("LiveGemini", "🔌 Remote server closed connection (${e::class.simpleName ?: e.message ?: "EOF"}) — auto-reconnecting (attempt ${if (sessionWasReady) 1 else attempt + 1}/$maxRetries)")
                    attempt = if (sessionWasReady) 1 else attempt + 1
                    if (attempt > 1) {
                        sessionResumptionHandle = null
                    }
                } else {
                    // ห้ามส่ง throwable ตรงๆ — message ของ handshake exception มี URL ที่มี ?key= อยู่
                    logError("LiveGemini", "Connection error (attempt ${attempt + 1}): ${e::class.simpleName}: ${LiveProtocol.redactSecrets(e.message)}")
                    // เน็ตหลุด/DNS ไม่ออก ไม่ใช่ความผิดของโมเดล — เดิมวนสลับจนหมด chain ภายใน 50ms
                    // แล้วไปค้างอยู่กับโมเดลที่แย่กว่าเดิมหลังเน็ตกลับมา (log 2026-09-17 00:17)
                    val networkDown = isNetworkUnreachableException(e)
                    if (!networkDown && !sessionWasReady && liveModelChain.size > 1 && fallbackCount < liveModelChain.size) {
                        fallbackCount++
                        val prevModel = liveModelName
                        liveModelIndex = (liveModelIndex + 1) % liveModelChain.size
                        liveModelName = liveModelChain[liveModelIndex]
                        sessionResumptionHandle = null
                        logDebug("LiveGemini", "🔄 Live connection error with $prevModel — rotating to fallback model: $liveModelName ($fallbackCount/${liveModelChain.size})")
                        attempt = 0
                        continue
                    }
                    attempt++
                    if (attempt > 1) {
                        sessionResumptionHandle = null
                    }
                    if (attempt > maxRetries) {
                        _connectionState.value = ConnectionState.Error("Connection failed after ${maxRetries + 1} attempts: ${LiveProtocol.redactSecrets(e.message)}")
                    }
                }
            } finally {
                // loop เก่า (ถูก cancel ตอนผู้ใช้ restart) อาจมาถึงตรงนี้หลัง loop ใหม่เปิด socket แล้ว —
                // ต้องไม่ล้าง socket/turn buffer ของ session ใหม่
                if (sessionGeneration == liveSessionGeneration) {
                    // Final flush of remaining turn buffers to DB before closing
                    logDebug("LiveGemini", "🔌 Session ending. Flushing buffers.")
                    val userText = pendingUserTurnText
                    val modelText = pendingModelTurnText ?: pendingModelTextParts
                    if (userText != null || modelText != null) {
                        rememberTurn("user", userText)
                        rememberTurn("model", modelText)
                        scope.launch {
                            if (userText != null) memoryManager?.storeMessage("user", userText, metadata = "{\"mode\": \"live_voice\"}")
                            if (modelText != null) memoryManager?.storeMessage("model", modelText, metadata = "{\"mode\": \"live_voice\"}")
                        }
                    }
                    pendingUserTurnText = null
                    pendingModelTurnText = null
                    pendingModelTextParts = null
                    audioBytesThisTurn = 0
                    userTurnFinalized = true
                    droppedPreReadyChunks = 0

                    webSocketSession = null
                    isSetupComplete = false
                }
            }
        }

        if (callId == connectCallId) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    internal suspend fun handleServerFrame(rawJson: String) {
        try {
            val msg = json.decodeFromString<LiveServerMessage>(rawJson)
            // logDebug("LiveGemini", "⬇ Parsed from: $rawJson")

            msg.sessionResumptionUpdate?.let { update ->
                if (update.resumable == true && !update.newHandle.isNullOrBlank()) {
                    sessionResumptionHandle = update.newHandle
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastResumptionLogAtMs >= resumptionLogIntervalMs) {
                        lastResumptionLogAtMs = nowMs
                        logDebug("LiveGemini", "♻️ Session resumption handle updated")
                    }
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
                logDebug("LiveGemini", "✅ Live session READY model=$liveModelName voice=${selectedVoiceName.ifBlank { "Aoede" }} (${readyLatencyMs}ms, resumption=${if (sessionResumptionEnabled) "on" else "off"})")
                isSetupComplete = true
                sessionWasReady = true
                com.skyliner2008.jarvis.data.ModelConfig.promoteHealthyLiveModel(liveModelName)
                onLiveModelPromoted?.invoke(liveModelName)
                // Give the WebSocket a short scheduling window after setupComplete before
                // accepting realtime PCM from the microphone. This prevents a cross-coroutine
                // race where the first audio frame is emitted in the same scheduler slice as
                // the setup acknowledgement and the server closes with NOT_CONSISTENT.
                realtimeInputReadyAtMs = System.currentTimeMillis() + realtimeInputReadyGraceMs
                _connectionState.value = ConnectionState.Connected
                if (droppedPreReadyChunks > 0) {
                    logDebug("LiveGemini", "🧹 Dropped $droppedPreReadyChunks pre-READY mic chunks — starting with fresh realtime PCM")
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

            msg.toolCallCancellation?.ids?.takeIf { it.isNotEmpty() }?.let { ids ->
                logDebug("LiveGemini", "🚫 Tool call cancelled by server: $ids")
                _toolCallCancellationFlow.emit(ids)
            }

            msg.toolCall?.functionCalls?.takeIf { it.isNotEmpty() }?.let { calls ->
                finalizeUserTurn()
                val generation = liveSessionGeneration
                calls.forEach { call ->
                    val event = LiveToolCallEvent(
                        callId = call.id,
                        name = call.name,
                        args = LiveProtocol.parseToolArgs(call.args),
                        sessionGeneration = generation
                    )
                    logDebug("LiveGemini", "🔧 Native tool call: ${call.name}(${event.args})")
                    val askedFor = lastUserText.trim()
                    if (call.id != null && askedFor.isNotBlank()) {
                        if (toolCallQuestions.size > 32) toolCallQuestions.clear()
                        toolCallQuestions[call.id] = askedFor
                    }
                    _nativeToolCallFlow.emit(event)
                }
            }

            msg.serverContent?.let { content ->
                // VAD / user barge-in: server ยกเลิก generation กลางทาง
                // ต้อง flush คิวเสียงที่ค้างเล่นทันที ไม่งั้นเสียงเก่าเล่นต่อทับ turn ใหม่ (ตาม Live API guide)
                if (content.interrupted == true) {
                    audioEpoch++
                    logDebug("LiveGemini", "⚡ Interrupted (VAD/user) — flushing playback queue (audioEpoch=$audioEpoch)")
                    turnWasInterrupted = true
                    audioBytesThisTurn = 0
                    val partialModel = pendingModelTurnText ?: pendingModelTextParts
                    if (userTurnFinalized) {
                        // ข้อความผู้ใช้ที่ค้างอยู่เป็นของ turn ที่ถูกขัด — เก็บลงประวัติ (เดิมทิ้งไปเลย)
                        val interruptedUser = pendingUserTurnText
                        rememberTurn("user", interruptedUser)
                        rememberTurn("model", partialModel)
                        if (interruptedUser != null || partialModel != null) {
                            scope.launch {
                                if (interruptedUser != null) memoryManager?.storeMessage("user", interruptedUser, metadata = "{\"mode\": \"live_voice\"}")
                                if (partialModel != null) memoryManager?.storeMessage("model", partialModel, metadata = "{\"mode\": \"live_voice\", \"interrupted\": true}")
                            }
                        }
                        pendingUserTurnText = null
                    }
                    // ถ้ายังไม่ finalize แปลว่า pendingUserTurnText คือประโยคใหม่ที่ผู้ใช้กำลังพูดแทรก — เก็บไว้
                    pendingModelTurnText = null
                    pendingModelTextParts = null
                    onInterrupted?.invoke()
                }
                content.modelTurn?.parts?.forEach { part ->
                    part.inlineData?.let { data ->
                        if (data.mimeType.contains("audio")) {
                            finalizeUserTurn()
                            val bytes = data.data.decodeBase64Bytes()
                            if (audioBytesThisTurn == 0 && lastUserSpeechAtMs > 0L) {
                                logDebug(
                                    "LiveGemini",
                                    "⏱️ First audio ${System.currentTimeMillis() - lastUserSpeechAtMs}ms after user speech (model=$liveModelName)"
                                )
                            }
                            audioBytesThisTurn += bytes.size
                            _audioOutputFlow.emit(LiveAudioChunk(audioEpoch, bytes))
                        }
                    }
                    part.text?.let { text ->
                        if (isToolRequest(text)) {
                            logDebug("LiveGemini", "🔧 Bridge tool request detected")
                            finalizeUserTurn()
                            _bridgeToolRequestFlow.emit(text)
                        } else if (text.isNotBlank()) {
                            // Text part แทนเสียง — failure mode ที่ทำให้ Live เงียบ
                            // เก็บเป็น fallback (กันข้อความหาย) และแสดงในแชทถ้ายังไม่มี transcription
                            logDebug("LiveGemini", "⚠️ JARVIS (Text Part — model ตอบเป็น text แทนเสียง): ${text.take(80)}")
                            finalizeUserTurn()
                            val isFirstPart = pendingModelTextParts == null
                            val merged = (pendingModelTextParts ?: "") + text
                            pendingModelTextParts = merged
                            if (pendingModelTurnText == null) {
                                _textOutputFlow.emit(LiveTextUpdate(
                                    text = merged,
                                    role = "model",
                                    append = false,
                                    replace = !isFirstPart,
                                    isStatic = false
                                ))
                            }
                        }
                    }
                }

                content.inputTranscription?.text?.let { chunk ->
                    if (chunk.isNotBlank()) {
                        val isFirst = pendingUserTurnText == null
                        if (isFirst) {
                            turnWasInterrupted = false
                            userTurnSerial++
                            userTurnFinalized = false
                        }
                        val text = mergeTranscript(pendingUserTurnText, chunk)
                        pendingUserTurnText = text
                        lastUserText = text.trim()
                        lastUserSpeechAtMs = System.currentTimeMillis()
                        // log เฉพาะชิ้นที่เพิ่มเข้ามา — เดิม log ข้อความสะสมทุกชิ้น ทำให้ logcat ยาวเป็นสิบบรรทัดต่อประโยค
                        logDebug("LiveGemini", "🎤 User +\"${chunk.trim()}\"")

                        // Progressive transcript → ephemeral user bubble (persisted only at turnComplete)
                        _textOutputFlow.emit(LiveTextUpdate(
                            text = text,
                            role = "user",
                            append = false,
                            replace = !isFirst,
                            isStatic = false
                        ))
                    }
                }

                content.outputTranscription?.text?.let { chunk ->
                    if (chunk.isNotBlank()) {
                        finalizeUserTurn()
                        val isFirst = pendingModelTurnText == null
                        val text = mergeTranscript(pendingModelTurnText, chunk)
                        pendingModelTurnText = text
                        logDebug("LiveGemini", "🤖 JARVIS +\"${chunk.trim()}\"")

                        // First chunk → new bubble; later chunks → replace that bubble.
                        // (เดิม append=isFirst ทำให้ turn ใหม่ไปต่อท้าย bubble ของ turn ก่อน แล้ว replace ทับจนข้อความเก่าหาย)
                        _textOutputFlow.emit(LiveTextUpdate(
                            text = text,
                            role = "model",
                            append = false,
                            replace = !isFirst,
                            isStatic = false // Transcriptions are NOT static, they can be replaced
                        ))
                    }
                }

                if (content.turnComplete == true) {
                    finalizeUserTurn()
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
                        logDebug(
                            "LiveGemini",
                            "🏁 Turn Complete (audio: $audioBytesThisTurn bytes) | user=\"${userText?.take(120) ?: "-"}\" | jarvis=\"${modelText?.take(160) ?: "-"}\""
                        )
                    }
                    // มีคำถามจากผู้ใช้แต่ AI ขึ้นต้นด้วยคำทักทายเปิดเซสชัน = ทักทายซ้ำ (log ไว้วัดผลการแก้ 2026-09-23)
                    if (!userText.isNullOrBlank() && modelText != null && LiveProtocol.startsWithSessionGreeting(modelText)) {
                        logDebug("LiveGemini", "⚠️ ทักทายซ้ำกลางบทสนทนา (model=$liveModelName) — user=\"${userText.take(60)}\"")
                    }

                    rememberTurn("user", userText)
                    rememberTurn("model", modelText)
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("LiveGemini", "Frame parse error: ${e.message}")
        }
    }
    
    private fun isToolRequest(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("tool_call:") || lower.contains("tool_code:")
    }

    suspend fun sendAudioChunk(pcmBase64: String) {
        val session = webSocketSession
        val isSessionActive = session != null && session.isActive
        if (!isSessionActive && isSetupComplete) {
            isSetupComplete = false
        }

        // ยังไม่ READY / socket ไม่ active / อยู่ในช่วง grace หลัง READY — ทิ้ง PCM (ไมค์ยังทำงาน เฟรมถัดไปส่งปกติ)
        if (!isSetupComplete || !isSessionActive || System.currentTimeMillis() < realtimeInputReadyAtMs) {
            if (!isSetupComplete) droppedPreReadyChunks++
            return
        }
        sentAudioChunks++
        if (sentAudioChunks % 250L == 0L) {
            logDebug("LiveGemini", "🎤 Audio chunks streaming to WebSocket (#$sentAudioChunks)")
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
    
    private var sentVideoChunks: Long = 0L

    suspend fun sendImageChunk(jpegBase64: String) {
        // log ทุกเฟรมทำให้ logcat ท่วมตอนเปิดกล้อง (หลายเฟรมต่อวินาที) — log เป็นช่วงพอ
        sentVideoChunks++
        if (sentVideoChunks % 30L == 1L) {
            logDebug("LiveGemini", "📹 Video chunks streaming (#$sentVideoChunks, ${jpegBase64.length} chars/frame)")
        }
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

    /**
     * @param sessionGeneration generation ที่ออก call นี้ — ถ้า socket ถูกเปลี่ยนไปแล้ว (reconnect ระหว่างรัน tool)
     *   ห้ามส่ง toolResponse ของ id เก่าเข้า session ใหม่ (server ไม่รู้จัก id และอาจปิด session)
     * @return true ถ้าส่งเข้า session เดิมสำเร็จ — false ให้ caller ส่งผลทางอื่น (เช่น realtime text)
     */
    suspend fun sendNativeToolResponse(
        callId: String,
        toolName: String,
        result: String,
        sessionGeneration: Long? = null
    ): Boolean {
        if (sessionGeneration != null && sessionGeneration != liveSessionGeneration) {
            logDebug("LiveGemini", "⚠️ Tool response dropped: $toolName callId=$callId belongs to session $sessionGeneration (current=$liveSessionGeneration)")
            return false
        }
        // ติดป้ายทุกครั้งว่าผลก้อนนี้เป็นคำตอบของคำถามไหน — ถ้าไม่ติด โมเดลมักปิดเทิร์นไปแล้ว
        // ตอนผลกลับมา แล้วปล่อยผ่านเงียบๆ (log 2026-09-17 00:30: ผล 5 มิติถูกทิ้งจนผู้ใช้ถามซ้ำ)
        val askedFor = toolCallQuestions.remove(callId)?.trim().orEmpty()
        val taggedResult = when {
            askedFor.isBlank() -> result
            askedFor != lastUserText.trim() ->
                "[PENDING QUESTION] ผลนี้เป็นคำตอบของคำถามก่อนหน้าที่ผู้ใช้ถามว่า: " + askedFor +
                    " — คำถามนี้ยังไม่ได้ตอบ ต้องพูดตอบให้ครบด้วย ห้ามข้ามไปตอบเฉพาะคำถามล่าสุด" + LiveProtocol.NO_GREETING_NOTE + "\n\n" + result
            else ->
                "[ANSWER FOR] ผลนี้เป็นคำตอบของคำถามที่ผู้ใช้ถามว่า: " + askedFor +
                    " — ต้องพูดตอบคำถามนี้ให้ครบทันที ห้ามเงียบหรือรอให้ผู้ใช้ถามซ้ำ" + LiveProtocol.NO_GREETING_NOTE + "\n\n" + result
        }
        val sent = sendIfReady {
            val responseObj = buildJsonObject { put("result", taggedResult) }
            val msg = LiveToolResponseMessage(
                toolResponse = LiveToolResponseWrapper(
                    functionResponses = listOf(
                        LiveFunctionResponse(id = callId, name = toolName, response = responseObj)
                    )
                )
            )
            json.encodeToString(msg)
        }
        if (sent) {
            logDebug("LiveGemini", "✅ Tool response sent: $toolName callId=$callId → ${result.take(200)}")
        } else {
            logDebug("LiveGemini", "⚠️ Tool response NOT sent (session not ready): $toolName callId=$callId")
        }
        return sent
    }

    /** เน็ตไม่ถึงปลายทาง (DNS/route หาย) — ไม่เกี่ยวกับโมเดลที่เลือก จึงห้ามสลับโมเดลทิ้ง */
    private fun isNetworkUnreachableException(e: Throwable): Boolean {
        val name = e::class.simpleName.orEmpty()
        val msg = e.message.orEmpty().lowercase()
        return name.contains("UnknownHost") ||
            name.contains("NoRouteToHost") ||
            name.contains("ConnectException") ||
            msg.contains("unable to resolve host") ||
            msg.contains("no address associated with hostname") ||
            msg.contains("network is unreachable")
    }

    /** แจ้ง server ว่าไมค์หยุดสตรีมชั่วคราว (mute / TTS fallback) ให้ VAD ปิดท้าย utterance ที่ค้าง */
    suspend fun sendAudioStreamEnd(): Boolean = sendIfReady {
        json.encodeToString(LiveRealtimeInputMessage(realtimeInput = LiveRealtimeInputData(audioStreamEnd = true)))
    }

    suspend fun disconnect() {
        userRequestedDisconnect = true
        try { webSocketSession?.close() } catch (_: Exception) {}
        webSocketSession = null
        isSetupComplete = false
        _connectionState.value = ConnectionState.Disconnected
        logDebug("LiveGemini", "Session disconnected")
    }

    private var lastSendSkippedLogMs: Long = 0L

    /** @return true ถ้าส่งเข้า websocket จริง — false ถ้า session ไม่พร้อม (caller ต้องไม่ log ว่าส่งแล้ว) */
    private suspend fun sendIfReady(buildJson: () -> String): Boolean {
        val session = webSocketSession
        val isSessionActive = session != null && session.isActive
        if (!isSessionActive || !isSetupComplete) {
            if (!isSessionActive && isSetupComplete) {
                isSetupComplete = false
            }
            val now = System.currentTimeMillis()
            if (now - lastSendSkippedLogMs > 3000L) {
                lastSendSkippedLogMs = now
                logDebug("LiveGemini", "⚠️ send skipped — session ไม่พร้อม (hasSession=${session != null}, active=$isSessionActive, ready=$isSetupComplete)")
            }
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
        // บันทึกเนื้อหารายงานฉบับเต็มลง Working Memory เพื่อให้ผู้ใช้เปิดแอปใหม่แล้วอ่านย้อนหลังได้ครบถ้วน
        scope.launch {
            memoryManager?.storeMessage(
                role = "model",
                content = text,
                metadata = "{\"mode\": \"live_voice_tool_result\"}"
            )
        }
    }
}

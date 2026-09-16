package com.skyliner2008.jarvis.data

import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.util.decodeBase64Bytes
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.jvm.Volatile

/**
 * LiveSpecialistService — session สำหรับโมเดล Live "เฉพาะทาง" ที่ไม่ใช่ผู้ช่วยสนทนา
 *
 * ความสามารถจริงตามเอกสาร Gemini (ตรวจ 2026-09-16):
 *
 * โหมดประชุม — gemini-3.5-transcribe-live
 *  - ถอดเสียงเรียลไทม์ 85+ ภาษา ตรวจภาษาเองได้ รองรับสลับภาษากลางประโยค
 *  - โหมด SMART ตัดคำฟุ่มเฟือย จัดวรรคตอน/ย่อหน้าให้อ่านง่าย
 *  - custom vocabulary ได้ถึง 1,000 คำ (ผลดีที่สุดราว 100 คำ)
 *  - ไม่รองรับ speaker diarization และไม่รองรับ timestamp ระดับคำ (มีเฉพาะโหมดไฟล์)
 *  - หนึ่ง session ยาวได้ไม่เกิน 10 นาที จึงต้องต่อ session ใหม่ระหว่างทาง
 *
 * โหมดแปลภาษา — gemini-3.5-live-translate-preview
 *  - แปลเสียงเป็นเสียง 70+ ภาษา แบบสตรีมต่อเนื่อง ไม่รอจบประโยค
 *  - ได้ transcript ทั้งต้นทางและคำแปล
 *  - รับเฉพาะเสียง ไม่รองรับ tool และไม่รองรับ system instruction
 *
 * ทั้งสองโหมดแยกจาก session ผู้ช่วย (LiveGeminiService) โดยสิ้นเชิง — คนละ WebSocket คนละหน้าจอ
 */
class LiveSpecialistService(private val client: HttpClient) {

    enum class Mode { MEETING, TRANSLATE }

    /** ที่มาของข้อความ: SOURCE = สิ่งที่ได้ยิน, TRANSLATED = คำแปล, ASSISTANT = คำตอบของผู้ช่วยในหน้าประชุม */
    enum class TranscriptKind { SOURCE, TRANSLATED, ASSISTANT }

    data class TranscriptEvent(
        val kind: TranscriptKind,
        val text: String,
        /** true = ปิดท่อนแล้ว (ขึ้นย่อหน้าใหม่ได้) */
        val segmentClosed: Boolean = false
    )

    sealed class State {
        data object Idle : State()
        data object Connecting : State()
        data object Listening : State()
        /** กำลังต่อ session ใหม่เพราะใกล้ชนเพดาน 10 นาทีของ transcribe-live */
        data object Rotating : State()
        data class Error(val message: String) : State()
    }

    /** เพดานของ transcribe-live คือ 10 นาทีต่อ session — ต่อใหม่ก่อนถึงเพดาน */
    private val meetingSessionRotateMs = 8L * 60_000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _transcripts = MutableSharedFlow<TranscriptEvent>(extraBufferCapacity = 256)
    val transcripts: Flow<TranscriptEvent> = _transcripts.asSharedFlow()

    /** เสียงแปลจากโมเดล (โหมดแปลภาษา) */
    private val _audioOut = MutableSharedFlow<ByteArray>(extraBufferCapacity = 2048)
    val audioOut: Flow<ByteArray> = _audioOut.asSharedFlow()

    @Volatile
    private var session: io.ktor.websocket.DefaultWebSocketSession? = null

    @Volatile
    private var isSetupComplete = false

    @Volatile
    private var stopRequested = false

    @Volatile
    internal var currentMode: Mode? = null

    /** เวลาเริ่มต่อ session ปัจจุบัน — ใช้วัด latency ตอน READY */
    @Volatile
    private var sessionStartedAtMs: Long = 0L

    /** นับเฟรมเสียงที่ส่งไป เพื่อ log เป็นช่วง ไม่ใช่ทุกเฟรม */
    @Volatile
    private var audioFramesSent: Long = 0L

    /** จำนวนตัวอักษรของ transcript ในท่อนปัจจุบัน — log ตอนปิดท่อน */
    @Volatile
    private var sourceCharsThisSegment: Int = 0

    @Volatile
    private var translatedCharsThisSegment: Int = 0

    /** จำนวน session ที่ต่อไปแล้วในรอบนี้ (โหมดประชุมต่อใหม่ทุก 8 นาที) */
    @Volatile
    var sessionCount: Int = 0
        private set

    /**
     * เปิด session แล้ววนรับผลจนกว่าจะเรียก [stop]
     * โหมดประชุมจะต่อ session ใหม่อัตโนมัติก่อนชนเพดาน 10 นาที
     */
    suspend fun run(
        mode: Mode,
        apiKey: String,
        targetLanguageCode: String = "en",
        echoTargetLanguage: Boolean = false,
        languageCodes: List<String> = emptyList(),
        customVocabulary: List<String> = emptyList()
    ) {
        if (apiKey.isBlank()) {
            _state.value = State.Error("ยังไม่ได้ตั้งค่า API Key")
            return
        }
        stopRequested = false
        sessionCount = 0
        audioFramesSent = 0L
        currentMode = mode
        val model = when (mode) {
            Mode.MEETING -> ModelConfig.TRANSCRIBE_LIVE_MODEL
            Mode.TRANSLATE -> ModelConfig.TRANSLATE_LIVE_MODEL
        }

        logDebug(
            "LiveSpecialist",
            "▶ Start ${mode.name} model=$model" +
                if (mode == Mode.TRANSLATE) " target=$targetLanguageCode echo=$echoTargetLanguage"
                else " languages=${languageCodes.joinToString("/").ifBlank { "auto" }} vocab=${customVocabulary.size}"
        )
        while (!stopRequested) {
            sessionCount++
            _state.value = if (sessionCount == 1) State.Connecting else State.Rotating
            isSetupComplete = false
            val startedAt = System.currentTimeMillis()
            sessionStartedAtMs = startedAt
            try {
                client.webSocket(LiveProtocol.buildUrl(apiKey)) {
                    session = this
                    // ปิด session ตามเวลาแม้ไม่มีเสียงเข้ามาเลย — เดิมเช็คเวลาเฉพาะตอนมีเฟรมเข้า
                    // ห้องประชุมที่เงียบยาวจึงอาจค้างจนชนเพดาน 10 นาทีของโมเดลแล้วโดนตัดกลางคัน
                    val rotateWatchdog = if (mode == Mode.MEETING) launch {
                        delay(meetingSessionRotateMs)
                        logDebug("LiveSpecialist", "Rotate watchdog fired — closing meeting session at 8 minutes")
                        runCatching { close() }
                    } else null
                    send(
                        Frame.Text(
                            buildSetup(mode, model, targetLanguageCode, echoTargetLanguage, languageCodes, customVocabulary)
                        )
                    )

                    try {
                    for (frame in incoming) {
                        if (stopRequested) break
                        val raw = when (frame) {
                            is Frame.Text -> frame.readText()
                            is Frame.Binary -> frame.readBytes().decodeToString()
                            else -> continue
                        }
                        handleFrame(raw)
                        // ต่อ session ใหม่ก่อนชนเพดาน 10 นาทีของ transcribe-live
                        if (mode == Mode.MEETING && System.currentTimeMillis() - startedAt > meetingSessionRotateMs) {
                            logDebug("LiveSpecialist", "Rotating meeting session before the 10-minute cap")
                            break
                        }
                    }
                    } finally {
                        rotateWatchdog?.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("LiveSpecialist", "Session error (${mode.name}): ${LiveProtocol.redactSecrets(e.message)}")
                if (!stopRequested) {
                    _state.value = State.Error(LiveProtocol.redactSecrets(e.message).ifBlank { "เชื่อมต่อไม่สำเร็จ" })
                    delay(1_500)
                }
            } finally {
                session = null
                isSetupComplete = false
            }
            if (stopRequested) break
            // ปิดท่อนข้อความก่อนต่อ session ใหม่ เพื่อไม่ให้ประโยคคาบเกี่ยวกัน
            _transcripts.emit(TranscriptEvent(TranscriptKind.SOURCE, "", segmentClosed = true))
            _transcripts.emit(TranscriptEvent(TranscriptKind.TRANSLATED, "", segmentClosed = true))
        }
        _state.value = State.Idle
    }

    /** setup message ของแต่ละโหมด — แยกออกมาเพื่อทดสอบรูปแบบ JSON ได้ */
    fun buildSetup(
        mode: Mode,
        model: String,
        targetLanguageCode: String,
        echoTargetLanguage: Boolean,
        languageCodes: List<String>,
        customVocabulary: List<String>
    ): String {
        val transcriptionConfig: JsonObject = buildJsonObject {
            // languageCodes ว่าง = ให้โมเดลตรวจภาษาเอง (รองรับสลับภาษากลางประโยค)
            putJsonArray("languageCodes") { languageCodes.forEach { add(it) } }
            if (mode == Mode.MEETING) {
                // SMART = ตัดคำฟุ่มเฟือย จัดวรรคตอน (VERBATIM = ถอดดิบทุกคำ)
                put("mode", "SMART")
                if (customVocabulary.isNotEmpty()) {
                    putJsonArray("customVocabulary") { customVocabulary.take(1000).forEach { add(it) } }
                }
            }
        }

        val setup = when (mode) {
            Mode.MEETING -> LiveSetupMessage(
                setup = LiveSetup(
                    model = "models/$model",
                    inputAudioTranscription = transcriptionConfig,
                    generationConfig = LiveGenerationConfig(responseModalities = listOf("TEXT"))
                )
            )
            Mode.TRANSLATE -> LiveSetupMessage(
                setup = LiveSetup(
                    model = "models/$model",
                    inputAudioTranscription = JsonObject(emptyMap()),
                    outputAudioTranscription = JsonObject(emptyMap()),
                    generationConfig = LiveGenerationConfig(
                        responseModalities = listOf("AUDIO"),
                        translationConfig = LiveTranslationConfig(
                            targetLanguageCode = targetLanguageCode,
                            echoTargetLanguage = echoTargetLanguage
                        )
                    )
                )
            )
        }
        return json.encodeToString(setup)
    }

    internal suspend fun handleFrame(raw: String) {
        val msg = try {
            json.decodeFromString<LiveServerMessage>(raw)
        } catch (e: Exception) {
            logError("LiveSpecialist", "Frame parse error: ${e.message}")
            return
        }

        msg.error?.let {
            logError("LiveSpecialist", "API error: ${it.message}")
            _state.value = State.Error(it.message)
            return
        }

        if (msg.setupComplete != null) {
            isSetupComplete = true
            _state.value = State.Listening
            val readyMs = System.currentTimeMillis() - sessionStartedAtMs
            logDebug("LiveSpecialist", "✅ READY ${currentMode?.name ?: "?"} session #$sessionCount (${readyMs}ms)")
            return
        }

        val content = msg.serverContent ?: return

        content.inputTranscription?.text?.takeIf { it.isNotBlank() }?.let {
            sourceCharsThisSegment += it.length
            logDebug("LiveSpecialist", "🎤 heard +" + preview(it))
            _transcripts.emit(TranscriptEvent(TranscriptKind.SOURCE, it))
        }
        content.outputTranscription?.text?.takeIf { it.isNotBlank() }?.let {
            translatedCharsThisSegment += it.length
            logDebug("LiveSpecialist", "🌐 translated +" + preview(it))
            _transcripts.emit(TranscriptEvent(TranscriptKind.TRANSLATED, it))
        }
        content.modelTurn?.parts?.forEach { part ->
            part.inlineData?.takeIf { it.mimeType.contains("audio") }?.let {
                _audioOut.emit(it.data.decodeBase64Bytes())
            }
            // โหมดประชุมรับข้อความจาก inputTranscription เป็นช่องทางหลัก — ถ้ารับ modelTurn.text ด้วยจะได้ข้อความซ้ำ
            if (currentMode != Mode.MEETING) {
                part.text?.takeIf { it.isNotBlank() }?.let {
                    _transcripts.emit(TranscriptEvent(TranscriptKind.SOURCE, it))
                }
            }
        }
        if (content.turnComplete == true) {
            logDebug(
                "LiveSpecialist",
                "🏁 Segment closed — source=$sourceCharsThisSegment chars, translated=$translatedCharsThisSegment chars"
            )
            sourceCharsThisSegment = 0
            translatedCharsThisSegment = 0
            // ปิดทั้งสองฝั่ง — เดิมปิดเฉพาะ SOURCE ทำให้คำแปลทั้งหมดถูกต่อกันเป็นย่อหน้าเดียวยาวๆ
            _transcripts.emit(TranscriptEvent(TranscriptKind.SOURCE, "", segmentClosed = true))
            _transcripts.emit(TranscriptEvent(TranscriptKind.TRANSLATED, "", segmentClosed = true))
        }
    }


    /**
     * ตัวอย่างข้อความสำหรับ log — โมเดลถอดเสียงส่งข้อความมาเป็นก้อนใหญ่หลักพันตัวอักษร
     * logcat ตัดที่ ~4000 ไบต์แล้วหั่นเป็นหลายบรรทัด ทำให้อ่านย้อนยากและบางท่อนหายไปจาก log
     * (ข้อความเต็มไม่ได้หาย — ถูกส่งเข้าบทประชุมครบเสมอ) จึง log แค่ต้นข้อความกับความยาว
     */
    private fun preview(text: String): String {
        val clean = text.trim().replace("\n", " ")
        return if (clean.length <= 100) clean + " (" + clean.length + " chars)"
        else clean.take(100) + "… (" + clean.length + " chars)"
    }

    /** ส่งเสียงไมค์ (PCM 16k base64) — ระหว่างกดพัก controller จะไม่เรียกเมธอดนี้ */
    suspend fun sendAudio(pcmBase64: String) {
        val s = session ?: return
        if (!isSetupComplete || !s.isActive) return
        audioFramesSent++
        if (audioFramesSent % 250L == 0L) {
            logDebug("LiveSpecialist", "🎙 Audio streaming to specialist (#$audioFramesSent)")
        }
        try {
            s.send(
                Frame.Text(
                    json.encodeToString(
                        LiveRealtimeInputMessage(
                            realtimeInput = LiveRealtimeInputData(
                                audio = LiveBlob(mimeType = "audio/pcm;rate=16000", data = pcmBase64)
                            )
                        )
                    )
                )
            )
        } catch (_: CancellationException) {
            // lifecycle ปกติ ไม่ใช่ error
        } catch (e: Exception) {
            logError("LiveSpecialist", "send audio failed: ${e.message}")
        }
    }

    /** แจ้ง server ว่าหยุดส่งเสียงชั่วคราว (กดพัก) */
    suspend fun sendAudioStreamEnd() {
        val s = session ?: return
        if (!isSetupComplete || !s.isActive) return
        runCatching {
            s.send(
                Frame.Text(
                    json.encodeToString(
                        LiveRealtimeInputMessage(realtimeInput = LiveRealtimeInputData(audioStreamEnd = true))
                    )
                )
            )
        }
    }

    suspend fun stop() {
        stopRequested = true
        currentMode = null
        runCatching { session?.close() }
        session = null
        isSetupComplete = false
        _state.value = State.Idle
    }
}

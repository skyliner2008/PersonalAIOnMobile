package com.skyliner2008.jarvis.controller

import com.skyliner2008.jarvis.data.LiveSpecialistService
import com.skyliner2008.jarvis.data.MeetingArchive
import com.skyliner2008.jarvis.data.MeetingRecord
import kotlinx.datetime.toLocalDateTime
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import com.skyliner2008.jarvis.voice.PcmAudioEngine
import io.ktor.util.encodeBase64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * SpecialistSessionController — คุมโหมดประชุม (ถอดเสียง) และโหมดแปลภาษา
 *
 * แยกจากผู้ช่วยเสียง (VoiceController) ทั้งหมด: คนละ WebSocket คนละหน้าจอ
 * แต่ **ใช้ไมค์ตัวเดียวกันของเครื่อง** จึงต้องสลับกันถือไมค์ให้ชัดเจน:
 * - ระหว่างบันทึก/แปล → ไมค์เป็นของ controller นี้
 * - ระหว่าง "ถามด้วยเสียง" → pause() ปล่อยไมค์ให้ session ผู้ช่วย แล้ว resume() ยึดคืน
 */
class SpecialistSessionController(
    private val scope: CoroutineScope,
    private val service: LiveSpecialistService,
    private val apiKeyProvider: () -> String,
    /** ถาม/สรุปด้วยโมเดลแชท (flash-lite) — คืนข้อความตอบ */
    private val summarizer: suspend (String) -> String,
    /** ปิด Live session ของผู้ช่วย เพื่อคืนไมค์ให้โหมดนี้ */
    private val stopAssistantSession: () -> Unit,
    /** ส่งบันทึก/สรุปเข้าห้องแชทเมื่อจบงาน */
    private val pushToChat: (String) -> Unit,
    /** เปิด Live session ผู้ช่วยพร้อมคำถามแรก (ใช้ตอน "ถามด้วยเสียง" ระหว่างประชุม) */
    private val startAssistantWithPrompt: (String) -> Unit = {},
    /** ข้อความที่ผู้ช่วยพูด (transcript สะสมของ turn ปัจจุบัน) */
    private val assistantAnswerFlow: Flow<String> = emptyFlow(),
    /** ผู้ช่วยพูดจบ turn — ใช้ปิดคำตอบแต่ละรอบ (ถามด้วยเสียงหลายรอบได้) */
    private val assistantTurnCompleteFlow: Flow<Long> = emptyFlow(),
    /** ประโยคที่ผู้ใช้พูดถามผู้ช่วย (ระหว่างถามด้วยเสียง) */
    private val assistantUserQuestionFlow: Flow<String> = emptyFlow(),
    /** อ่านบันทึกการประชุมที่เก็บไว้ (JSON ก้อนเดียวจาก settings) */
    private val archiveLoad: suspend () -> String = { "" },
    /** เขียนบันทึกการประชุมกลับลงที่เก็บ */
    private val archiveSave: suspend (String) -> Unit = {}
) {
    data class Segment(
        val kind: LiveSpecialistService.TranscriptKind,
        val text: String,
        val startedAtMs: Long
    )

    private val _mode = MutableStateFlow<LiveSpecialistService.Mode?>(null)
    val mode: StateFlow<LiveSpecialistService.Mode?> = _mode.asStateFlow()

    val state: StateFlow<LiveSpecialistService.State> = service.state

    private val _segments = MutableStateFlow<List<Segment>>(emptyList())
    val segments: StateFlow<List<Segment>> = _segments.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _summary = MutableStateFlow<String?>(null)
    val summary: StateFlow<String?> = _summary.asStateFlow()

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()

    /** รายการบันทึกการประชุมที่เก็บไว้ (ใหม่สุดอยู่บน) — แสดงในแท็บ "สรุปประชุม" */
    private val _records = MutableStateFlow<List<MeetingRecord>>(emptyList())
    val records: StateFlow<List<MeetingRecord>> = _records.asStateFlow()

    /** id ของบันทึกที่เพิ่งสรุปเสร็จ — หน้าจอใช้เด้งไปแท็บสรุปให้อัตโนมัติ */
    private val _lastSavedRecordId = MutableStateFlow<Long?>(null)
    val lastSavedRecordId: StateFlow<Long?> = _lastSavedRecordId.asStateFlow()

    private val _isAsking = MutableStateFlow(false)
    /** กำลังถาม AI แบบพิมพ์อยู่ */
    val isAsking: StateFlow<Boolean> = _isAsking.asStateFlow()

    private val _voiceAskActive = MutableStateFlow(false)
    /** กำลังพักบันทึกเพื่อคุยกับผู้ช่วยด้วยเสียงอยู่ */
    val voiceAskActive: StateFlow<Boolean> = _voiceAskActive.asStateFlow()

    /** ภาษาปลายทางของโหมดแปล (BCP-47) */
    private val _targetLanguage = MutableStateFlow("en")
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    /** true = ถ้าผู้พูดพูดภาษาปลายทางอยู่แล้ว ให้โมเดลพูดตาม (ค่าเริ่มต้นคือเงียบ) */
    private val _echoTargetLanguage = MutableStateFlow(false)
    val echoTargetLanguage: StateFlow<Boolean> = _echoTargetLanguage.asStateFlow()

    private val pcmAudioEngine = PcmAudioEngine()
    private var sessionJob: Job? = null
    private var voiceAskJob: Job? = null
    private var micChannel: Channel<String>? = null

    // นาฬิกา: นับเฉพาะช่วงที่บันทึกจริง (พักแล้วต้องไม่เดินต่อ และห้ามกระโดดหลังเลิกพัก)
    private var activeSinceMs: Long = 0L
    private var accumulatedActiveMs: Long = 0L

    /** เวลาเริ่มบันทึกจริง (นาฬิกาเครื่อง) — ใช้ตั้งชื่อบันทึกและเป็น id */
    private var startedAtWallClockMs: Long = 0L

    /** โหลดรายการบันทึกจากที่เก็บ (เรียกตอนเปิดหน้าประชุม) */
    fun loadRecords() {
        scope.launch(Dispatchers.IO) {
            val list = MeetingArchive.decode(runCatching { archiveLoad() }.getOrDefault(""))
            _records.value = list.sortedByDescending { it.id }
            logDebug("Specialist", "📚 Loaded ${list.size} meeting record(s)")
        }
    }

    fun deleteRecord(id: Long) {
        scope.launch(Dispatchers.IO) {
            val remaining = _records.value.filterNot { it.id == id }
            _records.value = remaining
            runCatching { archiveSave(MeetingArchive.encode(remaining)) }
            if (_lastSavedRecordId.value == id) _lastSavedRecordId.value = null
            logDebug("Specialist", "🗑 Deleted meeting record $id (${remaining.size} left)")
        }
    }

    /** ให้ผู้ช่วยอ่านสรุปให้ฟัง — เปิด Live session ผู้ช่วยพร้อมข้อความสรุป */
    fun readRecordAloud(id: Long) {
        val record = _records.value.firstOrNull { it.id == id } ?: return
        logDebug("Specialist", "🔊 Read aloud: ${record.title} (${record.summary.length} chars)")
        startAssistantWithPrompt(MeetingArchive.buildReadAloudPrompt(record))
    }

    /** หยุดอ่าน (ปิด session ผู้ช่วย) */
    fun stopReadAloud() = stopAssistantSession()

    private suspend fun saveRecord(record: MeetingRecord) {
        val updated = (listOf(record) + _records.value).sortedByDescending { it.id }
        _records.value = updated
        runCatching { archiveSave(MeetingArchive.encode(updated)) }
            .onFailure { logError("Specialist", "บันทึกสรุปการประชุมไม่สำเร็จ: ${it.message}", it) }
        _lastSavedRecordId.value = record.id
        logDebug("Specialist", "💾 Saved meeting record ${record.title} (${updated.size} total)")
    }

    fun setTargetLanguage(code: String) {
        _targetLanguage.value = code
    }

    fun setEchoTargetLanguage(enabled: Boolean) {
        _echoTargetLanguage.value = enabled
    }

    fun startMeeting(customVocabulary: List<String> = emptyList()) =
        start(LiveSpecialistService.Mode.MEETING, customVocabulary)

    fun startTranslate() = start(LiveSpecialistService.Mode.TRANSLATE, emptyList())

    private fun start(mode: LiveSpecialistService.Mode, customVocabulary: List<String>) {
        if (_mode.value != null) return // กำลังทำงานอยู่แล้ว
        if (apiKeyProvider().isBlank()) {
            appendSegment(
                LiveSpecialistService.TranscriptKind.ASSISTANT,
                "⚠️ ยังไม่ได้ตั้งค่า Gemini API Key ใน Settings — เริ่มไม่ได้"
            )
            return
        }
        stopAssistantSession()
        _mode.value = mode
        _segments.value = emptyList()
        _summary.value = null
        _isPaused.value = false
        _elapsedMs.value = 0L
        accumulatedActiveMs = 0L
        activeSinceMs = System.currentTimeMillis()
        startedAtWallClockMs = activeSinceMs

        val previous = sessionJob
        sessionJob = scope.launch(Dispatchers.IO) {
            // session ก่อนหน้าอาจยังปิดไม่เสร็จ (กดปิดแล้วเปิดโหมดใหม่ทันที) — รอให้จบก่อนเสมอ
            previous?.cancelAndJoin()

            // 1) session กับโมเดลเฉพาะทาง
            launch {
                service.run(
                    mode = mode,
                    apiKey = apiKeyProvider(),
                    targetLanguageCode = _targetLanguage.value,
                    echoTargetLanguage = _echoTargetLanguage.value,
                    customVocabulary = customVocabulary
                )
            }

            // 2) รวบรวมข้อความเป็นย่อหน้า
            launch {
                service.transcripts.collect { event -> appendTranscript(event) }
            }

            // 3) เสียงคำแปล (เฉพาะโหมดแปล)
            if (mode == LiveSpecialistService.Mode.TRANSLATE) {
                launch {
                    service.audioOut.collect { pcm -> pcmAudioEngine.playAudio(pcm) }
                }
            }

            // 4) นาฬิกา — นับเฉพาะเวลาที่บันทึกจริง
            launch {
                while (isActive) {
                    delay(500)
                    _elapsedMs.value = currentActiveMs()
                }
            }

            // 5) ไมค์ → คิว → session
            val channel = Channel<String>(capacity = 100)
            micChannel = channel
            launch {
                for (chunk in channel) service.sendAudio(chunk)
            }
            startMic()
            logDebug(
                "Specialist",
                "▶ Started ${mode.name}" +
                    if (mode == LiveSpecialistService.Mode.TRANSLATE) " → ${_targetLanguage.value}" else ""
            )
        }
    }

    /** ชื่อบันทึกรูปแบบ "วัน/เดือน/ปี-เวลา" ตามเวลาเครื่อง */
    private fun meetingTitle(startedAtMs: Long): String {
        val at = if (startedAtMs > 0L) startedAtMs else System.currentTimeMillis()
        val local = kotlinx.datetime.Instant.fromEpochMilliseconds(at)
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        fun two(v: Int) = if (v < 10) "0$v" else "$v"
        return "${two(local.dayOfMonth)}/${two(local.monthNumber)}/${local.year}-${two(local.hour)}:${two(local.minute)}"
    }

    private fun currentActiveMs(): Long =
        accumulatedActiveMs +
            if (!_isPaused.value && activeSinceMs > 0L) System.currentTimeMillis() - activeSinceMs else 0L

    /** เริ่ม/คืนการถือไมค์ให้โหมดนี้ */
    private fun startMic() {
        val channel = micChannel ?: return
        pcmAudioEngine.onVolumeChanged = { volume -> _audioLevel.value = volume.coerceIn(0f, 1f) }
        pcmAudioEngine.startRecording { bytes ->
            if (!_isPaused.value) {
                val base64 = bytes.encodeBase64()
                if (channel.trySend(base64).isFailure) {
                    channel.tryReceive()
                    channel.trySend(base64)
                }
            }
        }
    }

    private fun appendSegment(kind: LiveSpecialistService.TranscriptKind, text: String) {
        if (text.isBlank()) return
        _segments.update { it + Segment(kind, text, System.currentTimeMillis()) }
    }

    private fun appendTranscript(event: LiveSpecialistService.TranscriptEvent) {
        if (event.segmentClosed) {
            openSegmentKinds.remove(event.kind)
            return
        }
        // ใช้ update{} เพราะมีหลาย coroutine เขียนพร้อมกันได้ (transcript + คำถาม/คำตอบผู้ช่วย)
        _segments.update { current ->
            val last = current.lastOrNull()
            if (last != null && last.kind == event.kind && openSegmentKinds.contains(last.kind)) {
                current.dropLast(1) + last.copy(text = mergeText(last.text, event.text))
            } else {
                openSegmentKinds.add(event.kind)
                current + Segment(event.kind, event.text.trim(), System.currentTimeMillis())
            }
        }
    }

    /** kind ที่ยังเขียนต่อในย่อหน้าเดิมได้ */
    private val openSegmentKinds = mutableSetOf<LiveSpecialistService.TranscriptKind>()

    private fun mergeText(previous: String, chunk: String): String = when {
        previous.isEmpty() -> chunk.trim()
        chunk.startsWith(previous) -> chunk
        else -> (previous + chunk).replace(Regex("\\s{2,}"), " ")
    }

    /**
     * พัก — **ปล่อยไมค์จริงๆ** ไม่ใช่แค่หยุดส่ง
     * เพราะ Android ให้แอปถือไมค์ได้ทีละทาง ถ้ายังอัดค้างไว้ session ผู้ช่วยจะได้ยินแต่ความเงียบ
     */
    fun pause() {
        if (_isPaused.value) return
        accumulatedActiveMs = currentActiveMs()
        _isPaused.value = true
        _audioLevel.value = 0f
        pcmAudioEngine.stopRecording()
        scope.launch(Dispatchers.IO) { service.sendAudioStreamEnd() }
        logDebug("Specialist", "Paused — mic released")
    }

    fun resume() {
        if (!_isPaused.value) return
        _isPaused.value = false
        activeSinceMs = System.currentTimeMillis()
        startMic()
        logDebug("Specialist", "Resumed — mic re-acquired")
    }

    /** หยุดบันทึก — โหมดประชุมจะสรุปด้วยโมเดลแชทแล้วส่งเข้าห้องแชท */
    fun stop(summarize: Boolean = true) {
        val wasMeeting = _mode.value == LiveSpecialistService.Mode.MEETING
        val wasRunning = _mode.value != null
        if (_voiceAskActive.value) endVoiceAsk(resumeRecording = false)
        val transcript = transcriptText()
        pcmAudioEngine.stopRecording()
        pcmAudioEngine.stopPlaying()
        micChannel?.close()
        micChannel = null
        _isPaused.value = false
        _audioLevel.value = 0f
        accumulatedActiveMs = currentActiveMs()
        activeSinceMs = 0L
        val activeMsAtStop = accumulatedActiveMs
        val job = sessionJob
        sessionJob = null
        scope.launch(Dispatchers.IO) {
            service.stop()
            job?.cancel()
        }
        _mode.value = null

        logDebug(
            "Specialist",
            "■ Stopped ${if (wasMeeting) "MEETING" else "TRANSLATE"} — " +
                "${accumulatedActiveMs / 1000}s active, ${_segments.value.size} segment(s), " +
                "${transcript.length} chars, summarize=$summarize"
        )
        if (!wasRunning || !summarize || transcript.isBlank()) return
        if (wasMeeting) {
            _isSummarizing.value = true
            scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    summarizer(
                        "นี่คือบันทึกคำต่อคำจากการประชุม โปรดสรุปเป็นภาษาไทยในรูปแบบ markdown:\n" +
                            "1. สรุปย่อ 3-5 บรรทัด\n2. ประเด็นสำคัญเป็นหัวข้อ\n3. สิ่งที่ต้องทำต่อ (ใครทำอะไร ถ้าระบุได้)\n4. คำถาม/ข้อสงสัยที่ยังค้าง\n\n" +
                            "บันทึก:\n$transcript"
                    )
                }.getOrElse { e ->
                    logError("Specialist", "สรุปการประชุมไม่สำเร็จ: ${e.message}", e)
                    "⚠️ สรุปอัตโนมัติไม่สำเร็จ: ${e.message}"
                }
                logDebug("Specialist", "📝 Summary ready (${result.length} chars from ${transcript.length} chars)")
                _summary.value = result
                _isSummarizing.value = false
                // เก็บไว้ในแท็บ "สรุปประชุม" แทนการยิงเข้าห้องแชทหลัก (แชทจะได้ไม่ปนกับบันทึกงาน)
                saveRecord(
                    MeetingRecord(
                        id = startedAtWallClockMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        title = meetingTitle(startedAtWallClockMs),
                        durationMs = activeMsAtStop,
                        summary = result,
                        transcript = transcript
                    )
                )
            }
        } else {
            pushToChat("🌐 **บันทึกการแปลภาษา**\n\n$transcript")
        }
    }

    /** ข้อความทั้งหมดในรูปแบบอ่านง่าย (ใช้สรุป/บันทึกลงแชท) */
    fun transcriptText(): String = _segments.value.joinToString("\n") { seg ->
        when (seg.kind) {
            LiveSpecialistService.TranscriptKind.SOURCE -> seg.text
            LiveSpecialistService.TranscriptKind.TRANSLATED -> "→ ${seg.text}"
            // คำถาม/คำตอบระหว่างประชุม ติดป้ายไว้ให้ชัดว่าไม่ได้มาจากเสียงในห้อง
            LiveSpecialistService.TranscriptKind.ASSISTANT -> "[AI] ${seg.text}"
        }
    }.trim()

    /** บทประชุมล้วน (ไม่รวมถาม-ตอบกับผู้ช่วย) — ใช้เป็นบริบทของคำถาม */
    private fun meetingContext(): String = _segments.value
        .filter { it.kind != LiveSpecialistService.TranscriptKind.ASSISTANT }
        .joinToString("\n") { it.text }
        .trim()

    fun clearTranscript() {
        _segments.value = emptyList()
        _summary.value = null
    }

    // ─── ถามแบบพิมพ์ (ตอบเป็นข้อความ บันทึกเดินต่อ) ─────────────────

    /**
     * ถาม AI ระหว่างประชุมด้วยการพิมพ์ — ส่ง "บทประชุมจนถึงตอนนี้" ไปเป็นบริบทด้วย
     *
     * โมเดลถอดเสียงไม่ได้คิดหรือจำอะไร (แปลงเสียงเป็นข้อความอย่างเดียว) และ session ผู้ช่วยก็คนละเส้น
     * คำถามจึงต้องแนบบทสนทนาไปด้วย โมเดลแชท (flash-lite, context 1M) ถึงจะรู้ว่ากำลังคุยเรื่องอะไรอยู่
     */
    fun askDuringSession(question: String) {
        val q = question.trim()
        if (q.isBlank() || _isAsking.value) return
        val context = meetingContext()
        logDebug("Specialist", "❓ Ask (typed): ${q.length} chars question, ${context.length} chars context")
        _isAsking.value = true
        appendSegment(LiveSpecialistService.TranscriptKind.ASSISTANT, "❓ $q")
        scope.launch(Dispatchers.IO) {
            val answer = runCatching { summarizer(buildAskPrompt(q, context)) }
                .getOrElse { e ->
                    logError("Specialist", "ถาม AI ระหว่างประชุมไม่สำเร็จ: ${e.message}", e)
                    "⚠️ ตอบไม่สำเร็จ: ${e.message}"
                }
            logDebug("Specialist", "💬 Answer (typed): ${answer.length} chars")
            appendSegment(LiveSpecialistService.TranscriptKind.ASSISTANT, answer)
            _isAsking.value = false
        }
    }

    // ─── ถามด้วยเสียง (พักบันทึก → ผู้ช่วยตอบเป็นเสียง → เลิกพัก) ─────

    private var latestAssistantAnswer: String = ""

    /**
     * ถามด้วยเสียง: พักบันทึก (ปล่อยไมค์) → เปิด Live session ผู้ช่วย → ผู้ช่วยตอบเป็นเสียง
     *
     * ระหว่างนี้ไมค์เป็นของผู้ช่วย คุยโต้ตอบต่อได้หลายรอบ (ผู้ช่วยใช้ tool ได้ครบ)
     * เสียงผู้ช่วยไม่ปนเข้าบทประชุมเพราะโมเดลถอดเสียงถูกพักไว้
     * เมื่อคุยจบให้กด "เลิกพัก" ([endVoiceAsk]) ระบบจะปิด session ผู้ช่วยและกลับมาบันทึกต่อ
     */
    fun askByVoice(question: String) {
        if (_mode.value != LiveSpecialistService.Mode.MEETING || _voiceAskActive.value) return
        val q = question.trim()
        pause()
        _voiceAskActive.value = true
        latestAssistantAnswer = ""
        appendSegment(
            LiveSpecialistService.TranscriptKind.ASSISTANT,
            if (q.isBlank()) "🎤 พักบันทึก — คุยกับผู้ช่วยด้วยเสียง" else "🎤 ถามด้วยเสียง: $q"
        )

        // greeting-on-ready ของ session ผู้ช่วยถูกใช้เป็น "คำถามแรก" — พอ READY ผู้ช่วยจะตอบทันที
        startAssistantWithPrompt(buildVoiceAskPrompt(q, meetingContext()))

        voiceAskJob?.cancel()
        voiceAskJob = scope.launch {
            // ข้อความที่ผู้ช่วยกำลังพูด (สะสมทั้ง turn)
            launch {
                assistantAnswerFlow.collect { answer ->
                    if (answer.isNotBlank()) latestAssistantAnswer = answer
                }
            }
            // ผู้ช่วยพูดจบ turn → ปิดคำตอบรอบนั้น (ถามต่อได้อีกหลายรอบ)
            launch {
                assistantTurnCompleteFlow.collect {
                    val answer = latestAssistantAnswer.trim()
                    latestAssistantAnswer = ""
                    appendSegment(LiveSpecialistService.TranscriptKind.ASSISTANT, answer)
                }
            }
            // คำถามที่ผู้ใช้พูดออกไป (ให้เห็นบทสนทนาครบในหน้าประชุม)
            launch {
                assistantUserQuestionFlow.collect { spoken ->
                    appendSegment(LiveSpecialistService.TranscriptKind.ASSISTANT, "🎤 $spoken")
                }
            }
        }
    }

    /** เลิกพัก: ปิด session ผู้ช่วย เก็บคำตอบที่ค้าง แล้วกลับมาถอดเสียงต่อ */
    fun endVoiceAsk(resumeRecording: Boolean = true) {
        if (!_voiceAskActive.value) return
        _voiceAskActive.value = false
        voiceAskJob?.cancel()
        voiceAskJob = null
        stopAssistantSession()
        appendSegment(LiveSpecialistService.TranscriptKind.ASSISTANT, latestAssistantAnswer.trim())
        latestAssistantAnswer = ""
        if (resumeRecording) resume()
        logDebug("Specialist", "Voice ask finished (resume=$resumeRecording)")
    }

    fun shutdown() {
        pcmAudioEngine.release()
    }

    companion object {
        /** จำกัดบริบทที่ส่งไปกับคำถาม — เก็บช่วงท้ายไว้เพราะเป็นเรื่องที่กำลังคุยกันอยู่ */
        const val ASK_CONTEXT_MAX_CHARS = 20_000

        private fun clipContext(transcript: String): String =
            if (transcript.length > ASK_CONTEXT_MAX_CHARS) {
                "…(ตัดช่วงต้นออก)…\n" + transcript.takeLast(ASK_CONTEXT_MAX_CHARS)
            } else transcript

        /** คำถามด้วยเสียง — ผู้ช่วยตอบออกลำโพง จึงสั่งให้พูดสั้นและห้ามใช้ markdown */
        fun buildVoiceAskPrompt(question: String, transcript: String): String = buildString {
            appendLine("[โหมดที่ปรึกษาระหว่างประชุม] ผู้ใช้กำลังประชุมอยู่และพักการบันทึกไว้ชั่วคราวเพื่อคุยกับคุณด้วยเสียง")
            appendLine("ด้านล่างคือบทถอดเสียงของการประชุมจนถึงตอนนี้ ให้ยึดบริบทนี้เป็นหลักในการตอบ")
            appendLine("ถ้าบทประชุมไม่มีข้อมูลที่จำเป็น ให้บอกตรงๆ ว่ายังไม่ได้พูดถึงในที่ประชุม แล้วค่อยแนะนำสั้นๆ")
            appendLine("ตอบเป็นภาษาไทยแบบพูดคุย กระชับ 2-5 ประโยค ห้ามใช้ markdown เพราะคำตอบจะถูกอ่านออกเสียง")
            appendLine("หลังตอบจบให้รอผู้ใช้ถามต่อได้ ไม่ต้องทักทายยาว")
            appendLine()
            appendLine("=== บทประชุมจนถึงตอนนี้ ===")
            appendLine(clipContext(transcript).ifBlank { "(ยังไม่มีบทสนทนา)" })
            appendLine("=== จบบทประชุม ===")
            appendLine()
            if (question.isBlank()) {
                append("ผู้ใช้เปิดไมค์เพื่อถามด้วยเสียง — ทักสั้นๆ 1 ประโยคว่าพร้อมตอบเรื่องที่ประชุมอยู่ แล้วรอคำถาม")
            } else {
                append("คำถามของผู้ใช้: ")
                append(question)
            }
        }

        fun buildAskPrompt(question: String, transcript: String): String = buildString {
            appendLine("คุณกำลังนั่งฟังการประชุมอยู่กับผู้ใช้ ด้านล่างคือบทถอดเสียงของการประชุมนี้จนถึงตอนนี้")
            appendLine("ให้ตอบคำถามโดยยึดบริบทจากบทประชุมเป็นหลัก (เช่น สเปกงาน ขนาดอาคาร ตัวเลขที่พูดถึงไปแล้ว)")
            appendLine("ถ้าบทประชุมไม่มีข้อมูลที่จำเป็น ให้บอกตรงๆ ว่าในที่ประชุมยังไม่ได้พูดถึง แล้วค่อยให้คำแนะนำทั่วไปสั้นๆ")
            appendLine("ตอบเป็นภาษาไทย กระชับ ตรงประเด็น ไม่เกิน 6 บรรทัด")
            appendLine()
            appendLine("=== บทประชุมจนถึงตอนนี้ ===")
            appendLine(clipContext(transcript).ifBlank { "(ยังไม่มีบทสนทนา)" })
            appendLine("=== จบบทประชุม ===")
            appendLine()
            append("คำถามของผู้ใช้: ")
            append(question)
        }
    }
}

package com.skyliner2008.jarvis.controller

import com.skyliner2008.jarvis.Message
import com.skyliner2008.jarvis.ai.JarvisOrchestrator
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import com.skyliner2008.jarvis.voice.PcmAudioEngine
import com.skyliner2008.jarvis.voice.VoiceManager
import io.ktor.util.encodeBase64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase-3 refactor: แยก Live Voice (mic stream, audio out, mute, error) ออกจาก JarvisViewModel
 * coupling กับแชท/หน่วยความจำผ่าน lambda ที่ VM ส่งให้ — controller ไม่รู้จัก VM โดยตรง
 */
class VoiceController(
    private val scope: CoroutineScope,
    private val orchestrator: JarvisOrchestrator,
    private val voiceManager: VoiceManager,
    /** แชทปัจจุบัน (text output ของ live จะอัปเดตลงนี้) */
    private val messages: MutableStateFlow<List<Message>>,
    /** core memory + MT5 runtime context สำหรับเปิด live session */
    private val coreContextProvider: suspend () -> String,
    /** bridge สถานะ "ผู้ใช้กำลังพูด" ให้ VM/camera (Adaptive Vision) */
    private val onUserSpeakingChanged: (Boolean) -> Unit
) {
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _isAiSpeaking = MutableStateFlow(false)
    val isAiSpeaking: StateFlow<Boolean> = _isAiSpeaking.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    /** Real Live session readiness. Connected is emitted only after the Live setup handshake completes. */
    val liveConnectionState: StateFlow<com.skyliner2008.jarvis.data.ConnectionState> = orchestrator.liveConnectionState

    private val pcmAudioEngine = PcmAudioEngine()
    private val speechThreshold = 0.05f // Volume threshold for "Speaking" state

    var onStartDemo: (() -> Unit)? = null
    var onStopDemo: (() -> Unit)? = null
    var onTestEmotion: ((com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion?, String?) -> Unit)? = null

    private var liveSessionJob: kotlinx.coroutines.Job? = null

    /** คิวเสียงไมค์แบบ bounded — กัน launch-per-chunk สะสมจนเสียงส่งช้า (เคยวัดได้เสียงตกค้าง 48 วิ 2026-08-18) */
    private var liveMicChannel: kotlinx.coroutines.channels.Channel<String>? = null
    private var lastAlwaysLiveTriggerTime = 0L

    init {
        // Bridge speech state to camera service for Adaptive Vision (Token Saving) and UI audio level
        pcmAudioEngine.onVolumeChanged = { volume ->
            if (!_isAiSpeaking.value) {
                _audioLevel.value = volume.coerceIn(0f, 1f)
            }
            val speaking = volume > speechThreshold
            onUserSpeakingChanged(speaking)
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        logDebug("JARVIS_VM", "Microphone muted: ${_isMuted.value}")
    }

    fun startVoiceInput() {
        if (_isListening.value) return
        _isListening.value = true
        _voiceError.value = null
        _isMuted.value = false // Start unmuted
        logDebug("JARVIS_VM", "Starting Live Voice Input")

        // ตั้งค่า Robot Voice DSP และคำทักทายตาม Persona ปัจจุบัน
        val isPet = com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode
        pcmAudioEngine.isRobotVoiceEnabled = isPet

        // ทักทายยืนยันความพร้อม: ผู้ใช้จะได้รู้ทันทีว่า session READY แล้วคุยได้
        // สำคัญมาก: ต้องใช้ภาษาไทยล้วน "สวัสดีจาวิส พร้อมคุยไหม"
        // ห้ามส่งตัวอักษรภาษาอังกฤษ "JARVIS" เด็ดขาด เพราะจะกระตุ้นให้ Gemini Live Acoustic Model
        // สลับไปใช้สำเนียงฝรั่ง (English accent / phonetics) ทำให้พูดไม่ชัดและติดสำเนียงต่างชาติ
        val defaultGreeting = if (isPet) {
            "สวัสดีฮับ พร้อมเล่นแล้ว"
        } else {
            "สวัสดีจาวิส พร้อมคุยไหม"
        }
        orchestrator.setLiveGreetingOnReady(defaultGreeting)

        // Barge-in: ผู้ใช้พูดแทรก (VAD interrupt) ต้อง flush คิวเสียง AI ที่ค้างเล่นทันที
        // ไม่งั้นเสียงเก่าเล่นต่อทับ turn ใหม่ — handler มีใน LiveGeminiService/orchestrator แต่ไม่เคยถูก wire (review 2026-08-18)
        orchestrator.setLiveInterruptionHandler {
            pcmAudioEngine.stopPlaying()
        }

        // Turn ที่ model ตอบเป็น text ล้วน (ไม่มีเสียงออกเลย) → ใช้ Android TTS พูดแทน กัน AI เงียบเฉย
        // สำคัญ: ต้อง mute mic ชั่วคราวขณะ TTS พูด เพื่อป้องกันไมค์อัดเสียงลำโพงตัวเองแล้วส่งกลับไปหา AI ทำให้เกิดลูปพูดซ้ำ 2 รอบ
        orchestrator.setLiveNoAudioFallback { text ->
            if (voiceManager.isAvailable()) {
                _isMuted.value = true
                voiceManager.speak(text) {
                    _isMuted.value = false
                }
            }
        }

        liveSessionJob?.cancel()
        liveSessionJob = scope.launch(Dispatchers.IO) {
            try {
                // 1. Build core memory context for live session/tool bridge
                val coreContext = coreContextProvider()

                // 2. ดึงประวัติการสนทนาสั้นๆ เพื่อส่งให้ Live Session รู้บริบท (ใน Pet Mode ไม่ส่งประวัติ เพื่อป้องกัน context bleed และคำสั่งโหมดตกค้าง)
                val historySnapshot = if (com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode) {
                    ""
                } else {
                    messages.value
                        .filter { msg ->
                            val c = msg.content.trim()
                            !c.contains("พร้อมคุยไหม") &&
                            !c.contains("LIVE READY") &&
                            !c.contains("กำลังเชื่อมต่อ Live session") &&
                            !c.contains("โหมดสัตว์เลี้ยง") &&
                            !c.contains("โหมดควบคุม") &&
                            !c.contains("โหมดขับขี่")
                        }
                        .takeLast(8).joinToString("\n") {
                            "${if (it.role == "user") "ผู้ใช้" else "จาวิส"}: ${it.content}"
                        }
                }

                // 3. เปิด Live session พร้อม tool bridge (Path A + Path B auto-detected)
                launch {
                    logDebug("JARVIS_VM", "Connecting Live session (with memory context)...")
                    orchestrator.startLiveVoiceSessionWithMemory(coreContext, historySnapshot)
                }

                // 3b. ถ้า READY ช้ากว่า 2.5 วิ (เช่น gemini-3.1-flash-live-preview ใช้ 7–15 วิ)
                // แจ้งสถานะในแชทให้ผู้ใช้รู้ว่ายังเชื่อมต่อไม่เสร็จ — เสียงที่พูดช่วงนี้ถูก buffer ไว้แล้ว ไม่หาย
                launch {
                    kotlinx.coroutines.delay(2500)
                    if (orchestrator.liveConnectionState.value !is com.skyliner2008.jarvis.data.ConnectionState.Connected && _isListening.value) {
                        withContext(Dispatchers.Main) {
                            messages.value = messages.value + Message(
                                "model",
                                "⏳ กำลังเชื่อมต่อ Live session… เมื่อ AI ทักกลับมาแปลว่าพร้อมแล้ว (เสียงที่พูดระหว่างนี้ถูกเก็บไว้ให้อัตโนมัติ)",
                                isStatic = true
                            )
                        }
                    }
                }

                // 3c. Observable UI transition: Connected is emitted only by setupComplete,
                // not by session-resumption handle updates.
                launch {
                    orchestrator.liveConnectionState.collect { state ->
                        if (state is com.skyliner2008.jarvis.data.ConnectionState.Connected && _isListening.value) {
                            withContext(Dispatchers.Main) {
                                messages.value = messages.value + Message(
                                    "model",
                                    "🟢 LIVE READY — พร้อมคุยแล้วค่ะ",
                                    isStatic = true
                                )
                            }
                        }
                    }
                }

                // 4. Collect audio output -> speaker & UI visualizer
                launch {
                    var playbackFinishJob: kotlinx.coroutines.Job? = null
                    orchestrator.setLiveInterruptionHandler {
                        playbackFinishJob?.cancel()
                        _isAiSpeaking.value = false
                        _audioLevel.value = 0f
                        pcmAudioEngine.stopPlaying()
                    }
                    orchestrator.audioOutputFlow.collect { pcmBytes ->
                        if (!_isAiSpeaking.value && pcmAudioEngine.isRobotVoiceEnabled) {
                            runCatching { com.skyliner2008.jarvis.sound.RobotSoundPlayer.playChirpStart() }
                        }
                        _isAiSpeaking.value = true
                        val rms = calculatePcmRms(pcmBytes)
                        _audioLevel.value = (rms * 2.5f).coerceIn(0f, 1f)
                        pcmAudioEngine.playAudio(pcmBytes)

                        val chunkDurationMs = (pcmBytes.size * 1000L) / (24000 * 2)
                        playbackFinishJob?.cancel()
                        playbackFinishJob = launch {
                            // Hold speaking state for chunk duration + 850ms hangover to prevent flapping
                            kotlinx.coroutines.delay(chunkDurationMs + 850L)
                            _isAiSpeaking.value = false
                            _audioLevel.value = 0f
                            if (pcmAudioEngine.isRobotVoiceEnabled) {
                                runCatching { com.skyliner2008.jarvis.sound.RobotSoundPlayer.playChirpEnd() }
                            }
                        }
                    }
                }

                // 5. Collect text output -> Chat UI
                launch {
                    orchestrator.textOutputFlow.collect { update ->
                        withContext(Dispatchers.Main) {
                            val msgList = messages.value.toMutableList()

                            if (update.replace && msgList.isNotEmpty() &&
                                msgList.last().role == update.role && !msgList.last().isStatic) {
                                // Replacement mode: update the last message content ONLY IF it's not static
                                val last = msgList.last()
                                msgList[msgList.size - 1] = last.copy(content = update.text, isStatic = update.isStatic)
                            } else if (update.append && msgList.isNotEmpty() &&
                                       msgList.last().role == update.role && !msgList.last().isStatic) {
                                // Append mode: add to last message content ONLY IF it's not static
                                val last = msgList.last()
                                msgList[msgList.size - 1] = last.copy(content = last.content + update.text, isStatic = update.isStatic)
                            } else {
                                // New message box (for new role, or if last box was static/report)
                                msgList.add(Message(update.role, update.text, isStatic = update.isStatic))
                            }
                            messages.value = msgList

                            // Fast-path local trigger for Avatar emotion/demo when user speaks
                            if (update.role == "user") {
                                val lower = update.text.lowercase().trim()
                                val isDemo = lower.contains("ทดสอบเดโม") || lower.contains("เดโม") ||
                                    lower.contains("demo") || lower.contains("ทดสอบระบบ") ||
                                    lower.contains("ทดสอบหุ่นยนต์") || lower.contains("โชว์หุ่นยนต์") ||
                                    lower.contains("แสดงเดโม") || lower.contains("แสดงอารมณ์ทั้งหมด") ||
                                    lower.contains("โชว์อารมณ์") || lower.contains("ทดสอบอารมณ์") ||
                                    lower.contains("avatar demo")
                                val isReset = lower.contains("หยุดเดโม") || lower.contains("หยุดทดสอบ") ||
                                    lower.contains("รีเซ็ต") || lower.contains("avatar reset") ||
                                    lower.contains("กลับสู่โหมดปกติ") || lower.contains("โหมดปกติ")
                                if (isDemo) {
                                    onStartDemo?.invoke()
                                } else if (isReset) {
                                    onStopDemo?.invoke() ?: onTestEmotion?.invoke(null, null)
                                } else if (lower.contains("ทำหน้า") || lower.contains("สีหน้า") || lower.contains("ยิ้มหน่อย")) {
                                    val emo = when {
                                        lower.contains("ดีใจ") || lower.contains("ยิ้ม") || lower.contains("มีความสุข") ->
                                            com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.HAPPY
                                        lower.contains("ตื่นเต้น") || lower.contains("ดาว") ->
                                            com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.EXCITED
                                        lower.contains("รัก") || lower.contains("หัวใจ") ->
                                            com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.LOVE
                                        lower.contains("โกรธ") || lower.contains("โมโห") ->
                                            com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.ANGRY
                                        lower.contains("เศร้า") || lower.contains("เสียใจ") || lower.contains("ร้องไห้") ->
                                            com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SAD
                                        lower.contains("ง่วง") || lower.contains("นอน") || lower.contains("หลับ") ->
                                            com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SLEEPING
                                        lower.contains("กำลังคิด") || lower.contains("คิด") || lower.contains("สงสัย") ->
                                            com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.THINKING
                                        else -> null
                                    }
                                    if (emo != null) {
                                        onTestEmotion?.invoke(emo, "🧪 [VOICE] สั่งเปลี่ยนเป็น ${emo.name}")
                                    }
                                }

                                // Fast-path local trigger for Always Live (โหมดควบคุม / โหมดขับขี่ / โหมดรถยนต์ / โหมดสัตว์เลี้ยง)
                                val isControlOrDriveOn = lower.contains("โหมดควบคุม") || lower.contains("โหมดขับขี่") || lower.contains("โหมดรถยนต์") || lower.contains("โหมดสัตว์เลี้ยง") ||
                                    lower.contains("เปิดโหมดควบคุม") || lower.contains("เปิดโหมดขับขี่") || lower.contains("เปิดโหมดรถยนต์") || lower.contains("เปิดโหมดสัตว์เลี้ยง") ||
                                    lower.contains("เข้าโหมดควบคุม") || lower.contains("เข้าโหมดขับขี่") || lower.contains("เข้าโหมดรถยนต์") || lower.contains("เข้าโหมดสัตว์เลี้ยง") ||
                                    lower.contains("โหมดแก้เบื่อ") || lower.contains("pet mode")
                                val isControlOrDriveOff = lower.contains("ปิดโหมดควบคุม") || lower.contains("ปิดโหมดขับขี่") || lower.contains("ปิดโหมดรถยนต์") || lower.contains("ปิดโหมดสัตว์เลี้ยง") ||
                                    lower.contains("ออกจากโหมดควบคุม") || lower.contains("ออกจากโหมดขับขี่") || lower.contains("ออกจากโหมดรถยนต์") || lower.contains("ออกจากโหมดสัตว์เลี้ยง")

                                if (isControlOrDriveOn && !isControlOrDriveOff) {
                                    val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                    if (now - lastAlwaysLiveTriggerTime > 1500L) {
                                        lastAlwaysLiveTriggerTime = now
                                        val mode = when {
                                            lower.contains("สัตว์เลี้ยง") || lower.contains("แก้เบื่อ") || lower.contains("pet") -> "pet"
                                            lower.contains("ขับขี่") || lower.contains("รถยนต์") -> "drive"
                                            else -> "control"
                                        }
                                        scope.launch {
                                            com.skyliner2008.jarvis.tools.ToolExecutor.execute(
                                                com.skyliner2008.jarvis.tools.ToolCall("device_always_live", mapOf("action" to "on", "mode" to mode))
                                            )
                                        }
                                    }
                                } else if (isControlOrDriveOff) {
                                    val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                    if (now - lastAlwaysLiveTriggerTime > 1500L) {
                                        lastAlwaysLiveTriggerTime = now
                                        val mode = when {
                                            lower.contains("สัตว์เลี้ยง") || lower.contains("แก้เบื่อ") || lower.contains("pet") -> "pet"
                                            lower.contains("ขับขี่") || lower.contains("รถยนต์") -> "drive"
                                            else -> "control"
                                        }
                                        scope.launch {
                                            com.skyliner2008.jarvis.tools.ToolExecutor.execute(
                                                com.skyliner2008.jarvis.tools.ToolCall("device_always_live", mapOf("action" to "off", "mode" to mode))
                                            )
                                        }
                                    }
                                }

                                // Fast-path local trigger for Pet Vision Eye / Camera (ลืมตา / เปิดกล้อง / นี่คืออะไร / ดูนี่ / หลับตา / ปิดกล้อง)
                                val isEyeOpenCmd = lower.contains("ลืมตา") || lower.contains("เปิดกล้อง") || lower.contains("เปิดตา") ||
                                    lower.contains("นี่คืออะไร") || lower.contains("นี้คืออะไร") || lower.contains("นี่อะไร") || lower.contains("นี้อะไร") ||
                                    lower.contains("อะไรนี่") || lower.contains("อะไรนี้") ||
                                    lower.contains("ดูนี่") || lower.contains("ดูนี้") || lower.contains("ดูอันนี้") || lower.contains("มองอันนี้") ||
                                    lower.contains("ช่วยดู") || lower.contains("ดูหน่อย") || lower.contains("มองหน่อย") || lower.contains("มองซิ") ||
                                    lower.contains("มองดู") || lower.contains("ส่องดู") || lower.contains("ส่องหน่อย") || lower.contains("ตรวจดู") ||
                                    lower.contains("อ่านนี่") || lower.contains("อ่านตรงนี้") || lower.contains("อ่านข้อความ") ||
                                    lower.contains("เห็นมั้ย") || lower.contains("เห็นไหม") || lower.contains("เห็นอะไร") ||
                                    lower.contains("กี่นิ้ว") || lower.contains("ชูกี่นิ้ว") || lower.contains("ชูนิ้ว") || lower.contains("โชว์กี่นิ้ว") ||
                                    lower.contains("ดูมาอีก") || lower.contains("ดูอีก") || lower.contains("ดูใหม่") ||
                                    lower.contains("อันนี้กี่นิ้ว") || lower.contains("อันนี้คืออะไร") || lower.contains("อันนี้อะไร") ||
                                    lower.contains("ถืออยู่") || lower.contains("ถืออะไร") || lower.contains("ถืออะไรอยู่") ||
                                    lower.contains("สีอะไร") || lower.contains("ตัวอะไร") || lower.contains("ท่าอะไร") ||
                                    lower.contains("what is this") || lower.contains("what's this") || lower.contains("look at this") ||
                                    lower.contains("see this") || lower.contains("open camera") || lower.contains("open your eyes") ||
                                    lower.contains("how many fingers")

                                val isEyeCloseCmd = lower.contains("หลับตา") || lower.contains("ปิดกล้อง") || lower.contains("ปิดตา") ||
                                    lower.contains("พอแล้ว") || lower.contains("หยุดดู") ||
                                    lower.contains("close camera") || lower.contains("close your eyes")

                                if (isEyeOpenCmd && !isEyeCloseCmd) {
                                    com.skyliner2008.jarvis.pet.PetVisionBridge.requestEyeOpen(true)
                                } else if (isEyeCloseCmd) {
                                    com.skyliner2008.jarvis.pet.PetVisionBridge.requestEyeOpen(false)
                                }

                                // Fast-path local trigger for Notification Quick Reply
                                val isReplyCmd = lower.startsWith("ตอบว่า") || lower.startsWith("ตอบไลน์ว่า") ||
                                    lower.startsWith("reply ว่า") || lower.startsWith("ส่งข้อความตอบว่า")
                                if (isReplyCmd) {
                                    val replyMsg = update.text.substringAfter("ว่า").trim()
                                    if (replyMsg.isNotBlank()) {
                                        scope.launch {
                                            com.skyliner2008.jarvis.tools.ToolExecutor.execute(
                                                com.skyliner2008.jarvis.tools.ToolCall("device_notification_reply", mapOf("message" to replyMsg))
                                            )
                                        }
                                    }
                                }

                                // Fast-path local trigger for Now Playing check
                                val isNowPlayingCmd = lower.contains("เพลงอะไรกำลังเล่น") || lower.contains("ตอนนี้เล่นเพลงอะไร") ||
                                    lower.contains("เช็คเพลง")
                                if (isNowPlayingCmd) {
                                    scope.launch {
                                        com.skyliner2008.jarvis.tools.ToolExecutor.execute(
                                            com.skyliner2008.jarvis.tools.ToolCall("device_media_control", mapOf("action" to "now_playing"))
                                        )
                                    }
                                }

                                // Fast-path local trigger for Notification Read
                                val isReadNotifCmd = lower.contains("อ่านไลน์") || lower.contains("อ่านข้อความ") ||
                                    lower.contains("มีข้อความใหม่ไหม") || lower.contains("ใครทักมา")
                                if (isReadNotifCmd) {
                                    val appFilter = if (lower.contains("ไลน์") || lower.contains("line")) "line" else null
                                    scope.launch {
                                        val args = if (appFilter != null) mapOf("app_filter" to appFilter) else emptyMap()
                                        com.skyliner2008.jarvis.tools.ToolExecutor.execute(
                                            com.skyliner2008.jarvis.tools.ToolCall("device_notification_read", args)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Start mic recording -> stream to Live model
                // ใช้ Channel bounded + sender ตัวเดียว แทน launch-per-chunk —
                // เดิมทุก chunk (~50/วิ) spawn coroutine ใหม่ ถ้า send ช้ากว่าจะสะสมเป็นพัน
                // เสียงถึง server ช้าไปเรื่อยๆ (เคยวัดได้ 48 วิ) → ตอนนี้คิวเต็มให้ทิ้งตัวเก่าสุด เหลือล่าสุดเสมอ
                logDebug("LiveGemini", "Microphone starting...")
                val micChannel = kotlinx.coroutines.channels.Channel<String>(capacity = 100) // ~2-3 วินาที
                liveMicChannel = micChannel
                launch(Dispatchers.IO) {
                    for (chunk in micChannel) {
                        orchestrator.sendLiveAudioChunk(chunk)
                    }
                }
                var frameCount = 0
                var droppedOld = 0
                pcmAudioEngine.startRecording { bytes ->
                    if (!_isMuted.value) {
                        frameCount++
                        if (frameCount % 250 == 0) {
                            logDebug("LiveGemini", "🎤 Mic streaming alive (frame #$frameCount, droppedOld=$droppedOld)")
                        }
                        val base64 = bytes.encodeBase64()
                        if (micChannel.trySend(base64).isFailure) {
                            micChannel.tryReceive() // คิวเต็ม = ส่งไม่ทัน → ทิ้งเสียงเก่าสุด เก็บเสียงล่าสุด
                            droppedOld++
                            if (droppedOld % 50 == 1) {
                                logDebug("LiveGemini", "⚠️ Mic buffer saturated — dropped $droppedOld old audio chunks")
                            }
                            micChannel.trySend(base64)
                        }
                    }
                }

            } catch (e: Exception) {
                logError("JARVIS_VM", "Live Voice Error", e)
                _isListening.value = false
                _voiceError.value = "Live mode error: ${e.message}"
            }
        }
    }

    fun stopVoiceInput() {
        logDebug("JARVIS_VM", "Stopping Live Voice Input")
        _isListening.value = false
        _isMuted.value = false
        _isAiSpeaking.value = false
        _audioLevel.value = 0f
        pcmAudioEngine.isRobotVoiceEnabled = false
        pcmAudioEngine.stopRecording()
        liveMicChannel?.close()
        liveMicChannel = null
        liveSessionJob?.cancel()
        liveSessionJob = null
        scope.launch(Dispatchers.IO) {
            orchestrator.endLiveVoiceSession()
        }
    }

    /** รีสตาร์ท Live Voice Session เพื่อเชื่อมต่อ WebSocket ใหม่ด้วย Setup Parameters ของ Persona ใหม่ */
    suspend fun restartVoiceSession() {
        stopVoiceInput()
        kotlinx.coroutines.delay(200)
        startVoiceInput()
    }

    fun clearVoiceError() {
        _voiceError.value = null
    }

    /**
     * ประกาศแจ้งเตือนข้อความใหม่ (Driving / Always Live Mode)
     * หาก Live session กำลังเชื่อมต่ออยู่ จะส่งเข้า Gemini Live โดยตรงเพื่อให้ AI สรุปและพูดด้วยเสียงเป็นธรรมชาติ
     * หลีกเลี่ยง Android Offline TTS ที่ฟังดูเป็นหุ่นยนต์ และป้องกันปัญหาเสียงสะท้อนเข้าไมค์
     */
    fun announceNotification(appName: String, sender: String, content: String) {
        scope.launch {
            val isLiveReady = liveConnectionState.value is com.skyliner2008.jarvis.data.ConnectionState.Connected
            if (isLiveReady) {
                val senderText = if (sender.isNotBlank()) "โดยคุณ $sender" else ""
                val prompt = "[แจ้งเตือนข้อความใหม่]: มีข้อความใหม่จาก $appName $senderText ว่า: \"$content\" (โปรดแจ้งเตือนผู้ใช้สั้นๆ 1 ประโยคอย่างเป็นธรรมชาติ ห้ามใช้ markdown)"
                val sent = orchestrator.sendLiveRealtimeText(prompt)
                if (sent) return@launch
            }
            // Fallback ไปใช้ Offline TTS เฉพาะกรณีที่ Live session ไม่ได้เชื่อมต่ออยู่
            if (voiceManager.isAvailable()) {
                val senderPart = if (sender.isNotBlank()) "จากคุณ $sender" else ""
                val speech = "มีข้อความใหม่ใน $appName $senderPart ว่า: $content"
                val prevMuted = _isMuted.value
                _isMuted.value = true
                voiceManager.speak(speech) {
                    _isMuted.value = prevMuted
                }
            }
        }
    }

    private fun calculatePcmRms(buffer: ByteArray): Float {
        val sampleCount = buffer.size / 2
        if (sampleCount <= 0) return 0f
        var sum = 0.0
        for (i in 0 until sampleCount) {
            val sample = ((buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)).toShort()
            sum += sample.toDouble() * sample.toDouble()
        }
        val rms = kotlin.math.sqrt(sum / sampleCount)
        return (rms / 32768.0).toFloat()
    }

    /** เปิด/ปิดโหมดเสียงหุ่นยนต์ (Robot Voice Filter & Pitch) */
    fun setRobotVoiceEnabled(enabled: Boolean) {
        pcmAudioEngine.isRobotVoiceEnabled = enabled
    }

    /** cleanup — เรียกจาก ViewModel.onCleared */
    fun shutdown() {
        pcmAudioEngine.release()
    }
}

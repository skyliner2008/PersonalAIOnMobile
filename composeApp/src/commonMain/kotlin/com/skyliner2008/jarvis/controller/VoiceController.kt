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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    var onPlayMoodsetPage: ((Int) -> Unit)? = null
    var onPlayAllMoodsets: (() -> Unit)? = null

    private var liveSessionJob: Job? = null

    /** disconnect ของ session ก่อนหน้า — session ใหม่ต้องรอให้เสร็จก่อน ไม่งั้น disconnect ที่มาช้าจะปิด socket ใหม่ */
    private var disconnectJob: Job? = null

    /** hangover หลัง chunk เสียงสุดท้าย — ยกเลิกเมื่อถูกขัดจังหวะ */
    private var playbackFinishJob: Job? = null

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
        setMicMuted(!_isMuted.value)
        logDebug("JARVIS_VM", "Microphone muted: ${_isMuted.value}")
    }

    /** ปิด/เปิดไมค์ — ตอนปิดส่ง audioStreamEnd ให้ server VAD ปิดท้าย utterance ที่ค้าง */
    private fun setMicMuted(muted: Boolean) {
        val wasMuted = _isMuted.value
        _isMuted.value = muted
        if (muted && !wasMuted && _isListening.value) {
            scope.launch(Dispatchers.IO) {
                runCatching { orchestrator.sendLiveAudioStreamEnd() }
            }
        }
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
        // ไม่ทับ greeting ที่ตั้งไว้ก่อน (เช่นยืนยันเสียงใหม่หลังเปลี่ยนเสียง)
        orchestrator.setLiveGreetingOnReadyIfAbsent(defaultGreeting)

        // Barge-in: ผู้ใช้พูดแทรก (VAD interrupt) → flush เสียง AI ที่ค้างเล่นทันที
        // chunk ที่ค้างใน flow ถูกทิ้งด้วย audio epoch ใน collector ด้านล่าง
        orchestrator.setLiveInterruptionHandler {
            playbackFinishJob?.cancel()
            _isAiSpeaking.value = false
            _audioLevel.value = 0f
            pcmAudioEngine.stopPlaying()
        }

        // Turn ที่ model ตอบเป็น text ล้วน (ไม่มีเสียงออกเลย) → ใช้ Android TTS พูดแทน กัน AI เงียบเฉย
        // สำคัญ: ต้อง mute mic ชั่วคราวขณะ TTS พูด เพื่อป้องกันไมค์อัดเสียงลำโพงตัวเองแล้วส่งกลับไปหา AI
        // และคืนค่าเดิมหลังพูดจบ (เดิมตั้ง false เสมอ ทับการปิดไมค์ของผู้ใช้)
        orchestrator.setLiveNoAudioFallback { text ->
            if (voiceManager.isAvailable()) {
                val prevMuted = _isMuted.value
                setMicMuted(true)
                voiceManager.speak(text) {
                    _isMuted.value = prevMuted
                }
            }
        }

        val previousSessionJob = liveSessionJob
        val previousDisconnectJob = disconnectJob
        liveSessionJob = scope.launch(Dispatchers.IO) {
            try {
                // 0. รอ session ก่อนหน้าปิดสนิท — restart เร็ว (เปลี่ยนเสียง/สลับโหมด) เคยทำให้
                //    disconnect() ที่มาช้าไปปิด socket ใหม่และตั้ง userRequestedDisconnect ทำให้ไม่ reconnect เอง
                previousSessionJob?.cancelAndJoin()
                previousDisconnectJob?.join()

                // 1. Build core memory context for live session/tool bridge
                val coreContext = coreContextProvider()

                // 2. ดึงประวัติการสนทนาสั้นๆ เพื่อส่งให้ Live Session รู้บริบท (ใน Pet Mode ไม่ส่งประวัติ เพื่อป้องกัน context bleed และคำสั่งโหมดตกค้าง)
                val historySnapshot = if (com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode) {
                    ""
                } else {
                    // ตัดข้อความสถานะ, ผล tool ดิบ (markdown ยาว — คำตอบที่พูดจริงอยู่ข้อความถัดไปแล้ว)
                    // และคำทักทายเปิดเซสชันของทั้งสองฝั่ง (ดู LiveProtocol.buildSessionHistory)
                    val turns = messages.value
                        .filter { msg ->
                            val c = msg.content.trim()
                            !msg.isStatic &&
                            msg.metadata?.contains("live_voice_tool_result") != true &&
                            !c.contains("LIVE READY") &&
                            !c.contains("กำลังเชื่อมต่อ Live session") &&
                            !c.contains("โหมดสัตว์เลี้ยง") &&
                            !c.contains("โหมดควบคุม") &&
                            !c.contains("โหมดขับขี่")
                        }
                        .map { it.role to it.content }
                    com.skyliner2008.jarvis.data.LiveProtocol.buildSessionHistory(turns)
                }

                // 3. เปิด Live session พร้อม tool bridge (Path A + Path B auto-detected)
                launch {
                    logDebug("JARVIS_VM", "Connecting Live session (with memory context)...")
                    orchestrator.startLiveVoiceSessionWithMemory(coreContext, historySnapshot)
                }

                // 3b. ถ้า READY ช้ากว่า 2.5 วิ แจ้งสถานะในแชท
                // เสียงก่อน READY ถูกทิ้งโดยตั้งใจ (burst-flush เคยทำให้ session ปิด) → บอกให้รอ AI ทักก่อนพูด
                launch {
                    kotlinx.coroutines.delay(2500)
                    if (orchestrator.liveConnectionState.value !is com.skyliner2008.jarvis.data.ConnectionState.Connected && _isListening.value) {
                        withContext(Dispatchers.Main) {
                            messages.value = messages.value + Message(
                                "model",
                                "⏳ กำลังเชื่อมต่อ Live session… รอให้ AI ทักก่อนแล้วค่อยพูดนะคะ",
                                isStatic = true
                            )
                        }
                    }
                }

                // 3c. Observable UI transition: Connected is emitted only by setupComplete.
                // แสดง READY ครั้งเดียวต่อการกดเริ่ม — reconnect หลัง GoAway ไม่ต้องขึ้นซ้ำในแชท
                launch {
                    var readyAnnounced = false
                    orchestrator.liveConnectionState.collect { state ->
                        if (!_isListening.value) return@collect
                        when (state) {
                            is com.skyliner2008.jarvis.data.ConnectionState.Connected -> {
                                if (readyAnnounced) {
                                    logDebug("JARVIS_VM", "🔁 Live session reconnected")
                                } else {
                                    readyAnnounced = true
                                    withContext(Dispatchers.Main) {
                                        messages.value = messages.value + Message(
                                            "model",
                                            "🟢 LIVE READY — พร้อมคุยแล้วค่ะ",
                                            isStatic = true
                                        )
                                    }
                                }
                            }
                            // service เลิกพยายามต่อแล้ว — เดิมไมค์ยังอัดต่อทั้งที่ไม่มีใครรับ
                            is com.skyliner2008.jarvis.data.ConnectionState.Error ->
                                endSessionAfterFailure(state.message)
                            // Disconnected ระหว่างที่ผู้ใช้ยังเปิดไมค์อยู่ = retry loop จบแล้ว (ไม่ใช่การกดหยุดเอง)
                            is com.skyliner2008.jarvis.data.ConnectionState.Disconnected ->
                                if (readyAnnounced) endSessionAfterFailure(null)
                            else -> Unit
                        }
                    }
                }

                // 4. Collect audio output -> speaker & UI visualizer
                launch {
                    orchestrator.audioOutputFlow.collect { chunk ->
                        // chunk ของ generation ที่ถูกขัดจังหวะไปแล้ว — ทิ้ง ไม่เล่นทับ turn ใหม่
                        if (chunk.epoch != orchestrator.currentLiveAudioEpoch) return@collect
                        val pcmBytes = chunk.pcm
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
                // ห้องแชทแสดงเฉพาะ "เนื้อหาสำคัญ" (รายงาน/ผล tool ที่ส่งมาแบบ isStatic) เท่านั้น
                // ไม่แสดงคำพูดสดของ AI/ผู้ใช้ — เสียงคือช่องทางหลัก ส่วนแชทไว้เก็บรายงานที่อ่านย้อนหลังได้
                // (transcript ยังถูกบันทึกลง memory/DB ตามปกติ)
                launch {
                    orchestrator.textOutputFlow.collect { update ->
                        if (!update.isStatic) return@collect
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
                        }
                    }
                }

                // 5b. คำสั่งลัดฝั่งเครื่อง — ตรวจจากประโยคที่ "พูดจบแล้ว" ครั้งเดียวต่อ turn
                // (เดิมตรวจทุกชิ้นของ transcript ระหว่างพูด → คำสั่งยิงซ้ำ, ส่งข้อความตอบกลับเป็นท่อนๆ)
                launch {
                    orchestrator.liveUserTurnFinalFlow.collect { text ->
                        withContext(Dispatchers.Main) { handleLocalCommands(text) }
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

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("JARVIS_VM", "Live Voice Error", e)
                _isListening.value = false
                _voiceError.value = "Live mode error: ${e.message}"
            }
        }
    }

    /** Live session จบเองโดยผู้ใช้ไม่ได้สั่งหยุด — แจ้งในแชทแล้วปล่อยไมค์ */
    private suspend fun endSessionAfterFailure(errorMessage: String?) {
        if (!_isListening.value) return
        logError("JARVIS_VM", "Live session ended unexpectedly: ${errorMessage ?: "disconnected"}")
        errorMessage?.let { _voiceError.value = it }
        val detail = errorMessage?.let { ": $it" } ?: ""
        withContext(Dispatchers.Main) {
            messages.value = messages.value + Message(
                "model",
                "🔴 Live session หยุดทำงาน$detail — กดไมค์อีกครั้งเพื่อเริ่มใหม่ได้เลยค่ะ",
                isStatic = true
            )
        }
        // เรียกผ่าน scope ของ VM: stopVoiceInput() cancel liveSessionJob ซึ่งเป็น parent ของ collector นี้
        scope.launch { stopVoiceInput() }
    }

    /** คำสั่งลัดที่ทำงานทันทีโดยไม่รอ model (avatar / always live / กล้อง) — idempotent ทั้งหมด */
    private fun handleLocalCommands(text: String) {
        val command = LiveLocalCommandParser.parse(text)

        when (val avatar = command.avatar) {
            LiveLocalCommandParser.AvatarCommand.PlayAll -> onPlayAllMoodsets?.invoke() ?: onStartDemo?.invoke()
            is LiveLocalCommandParser.AvatarCommand.Page -> onPlayMoodsetPage?.invoke(avatar.page)
            LiveLocalCommandParser.AvatarCommand.Demo -> onStartDemo?.invoke()
            LiveLocalCommandParser.AvatarCommand.Reset -> onStopDemo?.invoke() ?: onTestEmotion?.invoke(null, null)
            is LiveLocalCommandParser.AvatarCommand.Emotion ->
                onTestEmotion?.invoke(avatar.emotion, "🧪 [VOICE] สั่งเปลี่ยนเป็น ${avatar.emotion.name}")
            null -> Unit
        }

        command.alwaysLive?.let { alwaysLive ->
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            if (now - lastAlwaysLiveTriggerTime > 1500L) {
                lastAlwaysLiveTriggerTime = now
                scope.launch {
                    com.skyliner2008.jarvis.tools.ToolExecutor.execute(
                        com.skyliner2008.jarvis.tools.ToolCall(
                            "device_always_live",
                            mapOf("action" to if (alwaysLive.turnOn) "on" else "off", "mode" to alwaysLive.mode)
                        )
                    )
                }
            }
        }

        command.eyeOpen?.let { open ->
            com.skyliner2008.jarvis.pet.PetVisionBridge.requestEyeOpen(open)
        }
        // หมายเหตุ: ไม่มีคำสั่งลัดส่งข้อความตอบกลับ/อ่านแจ้งเตือน/เช็คเพลงอีกต่อไป —
        // ผลของคำสั่งอ่านถูกทิ้งเงียบๆ (ไม่มีใครรับผล) และคำสั่งตอบกลับซ้ำซ้อนกับ tool ที่ model เรียกเอง
    }

    fun stopVoiceInput() {
        logDebug("JARVIS_VM", "Stopping Live Voice Input")
        _isListening.value = false
        _isMuted.value = false
        _isAiSpeaking.value = false
        _audioLevel.value = 0f
        playbackFinishJob?.cancel()
        pcmAudioEngine.isRobotVoiceEnabled = false
        pcmAudioEngine.stopRecording()
        // ตัดเสียง AI ที่ค้างอยู่ใน AudioTrack — ผู้ใช้กดหยุดแล้วต้องเงียบทันที
        pcmAudioEngine.stopPlaying()
        liveMicChannel?.close()
        liveMicChannel = null
        liveSessionJob?.cancel()
        disconnectJob = scope.launch(Dispatchers.IO) {
            orchestrator.endLiveVoiceSession()
        }
    }

    /** รีสตาร์ท Live Voice Session เพื่อเชื่อมต่อ WebSocket ใหม่ด้วย Setup Parameters ของ Persona ใหม่ */
    suspend fun restartVoiceSession() {
        if (!_isListening.value) return // ผู้ใช้ปิด Live ไปแล้ว — ห้ามปลุก session ขึ้นมาใหม่
        stopVoiceInput()
        disconnectJob?.join()
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
            // ถ้าผู้ใช้เปิด Live อยู่ ให้ AI พูดเอง — รอสั้นๆ เผื่อกำลัง reconnect (เดิมเช็คแค่ Connected ตอนนั้น
            // แล้วตกไป TTS ทันที ทำให้เสียงหุ่นยนต์แทรกกลางบทสนทนา)
            if (_isListening.value) {
                val senderText = if (sender.isNotBlank()) "โดยคุณ $sender" else ""
                val prompt = "[แจ้งเตือนข้อความใหม่]: มีข้อความใหม่จาก $appName $senderText ว่า: \"$content\" (โปรดแจ้งเตือนผู้ใช้สั้นๆ 1 ประโยคอย่างเป็นธรรมชาติ ห้ามใช้ markdown)"
                val sent = orchestrator.sendLiveRealtimeTextWhenReady(prompt, timeoutMs = 5_000L)
                if (sent) return@launch
            }
            // Fallback ไปใช้ Offline TTS เฉพาะกรณีที่ Live session ไม่ได้เชื่อมต่ออยู่
            if (voiceManager.isAvailable()) {
                val senderPart = if (sender.isNotBlank()) "จากคุณ $sender" else ""
                val speech = "มีข้อความใหม่ใน $appName $senderPart ว่า: $content"
                val prevMuted = _isMuted.value
                setMicMuted(true)
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

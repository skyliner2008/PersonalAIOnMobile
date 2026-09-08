package com.example.personalaibot.controller

import com.example.personalaibot.Message
import com.example.personalaibot.ai.JarvisOrchestrator
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import com.example.personalaibot.voice.PcmAudioEngine
import com.example.personalaibot.voice.VoiceManager
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

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    /** Real Live session readiness. Connected is emitted only after the Live setup handshake completes. */
    val liveConnectionState: StateFlow<com.example.personalaibot.data.ConnectionState> = orchestrator.liveConnectionState

    private val pcmAudioEngine = PcmAudioEngine()
    private val speechThreshold = 0.05f // Volume threshold for "Speaking" state

    private var liveSessionJob: kotlinx.coroutines.Job? = null

    /** คิวเสียงไมค์แบบ bounded — กัน launch-per-chunk สะสมจนเสียงส่งช้า (เคยวัดได้เสียงตกค้าง 48 วิ 2026-08-18) */
    private var liveMicChannel: kotlinx.coroutines.channels.Channel<String>? = null

    init {
        // Bridge speech state to camera service for Adaptive Vision (Token Saving)
        pcmAudioEngine.onVolumeChanged = { volume ->
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

        // ทักทายยืนยันความพร้อม: ผู้ใช้จะได้รู้ทันทีว่า session READY แล้วคุยได้
        // สำคัญมาก: ต้องใช้ภาษาไทยล้วน "สวัสดีจาวิส พร้อมคุยไหม"
        // ห้ามส่งตัวอักษรภาษาอังกฤษ "JARVIS" เด็ดขาด เพราะจะกระตุ้นให้ Gemini Live Acoustic Model
        // สลับไปใช้สำเนียงฝรั่ง (English accent / phonetics) ทำให้พูดไม่ชัดและติดสำเนียงต่างชาติ
        orchestrator.setLiveGreetingOnReadyIfAbsent("สวัสดีจาวิส พร้อมคุยไหม")

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

                // 2. ดึงประวัติการสนทนาสั้นๆ เพื่อส่งให้ Live Session รู้บริบท (กรองคำทักทายและข้อความระบบออกเพื่อรักษาสำเนียงไทยแท้)
                val historySnapshot = messages.value
                    .filter { msg ->
                        val c = msg.content.trim()
                        !c.contains("พร้อมคุยไหม") &&
                        !c.contains("LIVE READY") &&
                        !c.contains("กำลังเชื่อมต่อ Live session")
                    }
                    .takeLast(8).joinToString("\n") {
                        "${if (it.role == "user") "ผู้ใช้" else "จาวิส"}: ${it.content}"
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
                    if (orchestrator.liveConnectionState.value !is com.example.personalaibot.data.ConnectionState.Connected && _isListening.value) {
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
                        if (state is com.example.personalaibot.data.ConnectionState.Connected && _isListening.value) {
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

                // 4. Collect audio output -> speaker
                launch {
                    orchestrator.audioOutputFlow.collect { pcmBytes ->
                        pcmAudioEngine.playAudio(pcmBytes)
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
                        }
                    }
                }

                // 6. Start mic recording -> stream to Live model
                // ใช้ Channel bounded + sender ตัวเดียว แทน launch-per-chunk —
                // เดิมทุก chunk (~50/วิ) spawn coroutine ใหม่ ถ้า send ช้ากว่าจะสะสมเป็นพัน
                // เสียงถึง server ช้าไปเรื่อยๆ (เคยวัดได้ 48 วิ) → ตอนนี้คิวเต็มให้ทิ้งตัวเก่าสุด เหลือล่าสุดเสมอ
                logDebug("JARVIS_VM", "Microphone starting...")
                val micChannel = kotlinx.coroutines.channels.Channel<String>(capacity = 50) // ~1 วินาที
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
                            logDebug("JARVIS_VM", "🎤 Mic streaming alive (frame #$frameCount, droppedOld=$droppedOld)")
                        }
                        val base64 = bytes.encodeBase64()
                        if (micChannel.trySend(base64).isFailure) {
                            micChannel.tryReceive() // คิวเต็ม = ส่งไม่ทัน → ทิ้งเสียงเก่าสุด เก็บเสียงล่าสุด
                            droppedOld++
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
        pcmAudioEngine.stopRecording()
        liveMicChannel?.close()
        liveMicChannel = null
        liveSessionJob?.cancel()
        liveSessionJob = null
        scope.launch(Dispatchers.IO) {
            orchestrator.endLiveVoiceSession()
        }
    }

    fun clearVoiceError() {
        _voiceError.value = null
    }

    /** cleanup — เรียกจาก ViewModel.onCleared */
    fun shutdown() {
        pcmAudioEngine.release()
    }
}

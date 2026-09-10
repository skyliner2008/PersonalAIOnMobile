package com.skyliner2008.jarvis.service

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Base64
import com.skyliner2008.jarvis.createHttpClient
import com.skyliner2008.jarvis.ai.JarvisPersona
import com.skyliner2008.jarvis.data.*
import com.skyliner2008.jarvis.db.JarvisDatabase
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class LiveVoiceAlertEngine(
    private val context: Context,
    private val database: JarvisDatabase,
    private val scope: CoroutineScope
) {
    private val geminiClient = createHttpClient()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val liveAlertSchedulerMutex = Mutex()
    private val liveAlertSchedulerWake = Channel<Unit>(Channel.CONFLATED)
    private val liveAlertSchedulerQueue = mutableListOf<LiveAlertRequest>()
    private var liveAlertSchedulerSequence = 0L
    private var liveAlertSchedulerJob: Job? = null

    private val liveVoiceFirstOutputTimeoutMs = 14_000L
    private val liveVoiceSetupTimeoutMs = 8_000L
    private val liveVoiceSessionTimeoutMs = 35_000L
    private val liveVoiceAudioIdleTimeoutMs = 10_000L
    private val liveVoiceSummaryCharCap = 350

    private val liveModelCooldownMap = ConcurrentHashMap<String, Long>()
    private val liveModelAudioCooldownUntil = ConcurrentHashMap<String, Long>()
    private val liveAudioFailureCooldownMs = 60_000L

    init {
        initTts()
    }

    private fun setting(key: String): String = runCatching {
        database.jarvisDatabaseQueries.getSetting(key).executeAsOneOrNull() ?: ""
    }.getOrDefault("")

    private fun settingEnabled(key: String, default: Boolean): Boolean {
        val v = setting(key)
        return if (v.isBlank()) default else (v == "true" || v == "1")
    }

    data class LiveAlertRequest(
        val priority: Int,
        val sequence: Long,
        val shortText: String,
        val fullText: String?,
        val liveSummary: Boolean,
        val timeframeMin: Int?,
        val result: CompletableDeferred<VoiceDeliveryResult>
    )

    data class LiveSpeechResult(
        val ok: Boolean,
        val transcript: String?,
        val cause: String = "NONE"
    )

    data class VoiceDeliveryResult(
        val engineLabel: String,
        val summary: String?
    )

    private fun initTts() {
        try {
            tts = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == android.speech.tts.TextToSpeech.SUCCESS
                if (ttsReady) {
                    try {
                        val attrs = android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        tts?.setAudioAttributes(attrs)
                    } catch (e: Exception) {
                        logError("AutomationService", "Failed to set AudioAttributes: ${e.message}", e)
                    }
                    val avail = tts?.setLanguage(java.util.Locale("th", "TH"))
                    if (avail == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
                        avail == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        tts?.language = java.util.Locale.getDefault()
                    }
                }
            }
        } catch (e: Exception) {
            logError("AutomationService", "TTS init failed: ${e.message}", e)
        }
    }


    private suspend fun speak(text: String) {
        if (tts == null) {
            initTts()
        }
        if (!ttsReady) {
            val deadline = System.currentTimeMillis() + 1500L
            while (!ttsReady && System.currentTimeMillis() < deadline) {
                delay(100L)
            }
        }
        if (!ttsReady) {
            logError("AutomationService", "TTS speak dropped — ttsReady is false after waiting", null)
            return
        }
        try {
            val params = android.os.Bundle().apply {
                putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "jarvis_alert_${System.currentTimeMillis()}")
            logDebug("AutomationService", "🔊 Android TTS speak called: '$text'")
            val estimatedDurationMs = (text.length * 120L).coerceIn(2000L, 8000L)
            delay(estimatedDurationMs)
        } catch (e: Exception) {
            logError("AutomationService", "TTS speak failed: ${e.message}", e)
        }
    }


    private val LIVE_VOICE_MODELS = listOf(
        "gemini-2.5-flash-native-audio-preview-09-2025" to "Live 2.5 Native (09-2025)",
        "gemini-2.5-flash-native-audio-latest" to "Live 2.5 Native Latest",
        "gemini-3.1-flash-live-preview" to "Live 3.1"
    )


    private fun isLiveModelAudioCoolingDown(model: String): Boolean =
        (liveModelAudioCooldownUntil[model] ?: 0L) > System.currentTimeMillis()


    private fun liveVoiceChain(): List<Pair<String, String>> {
        val selected = setting("live_model_name").removePrefix("models/").ifBlank {
            com.skyliner2008.jarvis.data.ModelConfig.DEFAULT_LIVE_MODEL
        }
        val candidates = com.skyliner2008.jarvis.data.ModelConfig.getLiveFallbackChain(selected)
        val available = candidates.filterNot(::isLiveModelAudioCoolingDown)
        val effective = if (available.isNotEmpty()) available else candidates
        return effective.map { model ->
            val label = LIVE_VOICE_MODELS.firstOrNull { it.first == model }?.second ?: model.substringAfterLast('/')
            model to label
        }
    }


    private fun isLiveEngine(engine: String): Boolean =
        engine == "live" || engine == "ai" || engine == "live31" || engine == "live25"


    suspend fun speakAlert(
        shortText: String,
        fullText: String? = null,
        liveSummary: Boolean = false,
        timeframeMin: Int? = null
    ): VoiceDeliveryResult {
        val engine = setting("alert_voice_engine").ifBlank { "device" }
        if (!isLiveEngine(engine)) {
            return speakAlertNow(shortText, fullText, liveSummary, timeframeMin)
        }

        val result = CompletableDeferred<VoiceDeliveryResult>()
        val priority = when {
            timeframeMin == null -> 10
            timeframeMin <= 1 -> 100
            timeframeMin <= 5 -> 90
            timeframeMin <= 15 -> 80
            timeframeMin <= 30 -> 70
            else -> 60
        }
        val request = liveAlertSchedulerMutex.withLock {
            liveAlertSchedulerSequence++
            LiveAlertRequest(
                priority = priority,
                sequence = liveAlertSchedulerSequence,
                shortText = shortText,
                fullText = fullText,
                liveSummary = liveSummary,
                timeframeMin = timeframeMin,
                result = result
            ).also { liveAlertSchedulerQueue.add(it) }
        }
        val queueSize = liveAlertSchedulerMutex.withLock { liveAlertSchedulerQueue.size }
        logDebug(
            "AutomationService",
            "🔊 Live Scheduler ENQUEUE priority=${request.priority} tf=${request.timeframeMin ?: "-"} " +
                "queue=$queueSize seq=${request.sequence}"
        )
        ensureLiveAlertScheduler()
        liveAlertSchedulerWake.trySend(Unit)
        return result.await()
    }


    private fun ensureLiveAlertScheduler() {
        if (liveAlertSchedulerJob?.isActive == true) return
        liveAlertSchedulerJob = scope.launch {
            while (isActive) {
                liveAlertSchedulerWake.receive()
                while (isActive) {
                    val request = liveAlertSchedulerMutex.withLock {
                        if (liveAlertSchedulerQueue.isEmpty()) null
                        else liveAlertSchedulerQueue.removeAt(
                            liveAlertSchedulerQueue.indices.maxWithOrNull(compareBy<Int> { liveAlertSchedulerQueue[it].priority }.thenByDescending { liveAlertSchedulerQueue[it].sequence }) ?: return@withLock null
                        )
                    } ?: break

                    val remaining = liveAlertSchedulerMutex.withLock { liveAlertSchedulerQueue.size }
                    logDebug(
                        "AutomationService",
                        "🔊 Live Scheduler DISPATCH priority=${request.priority} tf=${request.timeframeMin ?: "-"} " +
                            "remaining=$remaining seq=${request.sequence}"
                    )
                    try {
                        request.result.complete(
                            speakAlertNow(
                                request.shortText,
                                request.fullText,
                                request.liveSummary,
                                request.timeframeMin
                            )
                        )
                    } catch (e: CancellationException) {
                        request.result.cancel(e)
                        throw e
                    } catch (e: Exception) {
                        logError("AutomationService", "🔊 Live Scheduler request failed: ${e.message}", e)
                        request.result.complete(VoiceDeliveryResult("Android TTS (scheduler error)", null))
                    }
                }
            }
        }
    }


    private suspend fun speakAlertNow(
        shortText: String,
        fullText: String? = null,
        liveSummary: Boolean = false,
        timeframeMin: Int? = null
    ): VoiceDeliveryResult {
        // ปลุกหน้าจอให้ติดขึ้นมาเพื่อให้ผู้ใช้เห็นการแจ้งเตือนทันที
        AlwaysLiveManager.getInstanceOrNull()?.wakeScreen()

        // ขอ WakeLock ชั่วคราวป้องกัน CPU หลับระหว่างสังเคราะห์/สตรีมเสียงแจ้งเตือน
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "personalaibot:alert_voice")
        wakeLock?.acquire(30_000L)

        val engine = setting("alert_voice_engine").ifBlank { "device" }
        val live = isLiveEngine(engine)
        val short = AlertPresentationFormatter.sanitizeForSpeech(shortText)
        val full = AlertPresentationFormatter.sanitizeForSpeech(fullText ?: shortText)
        logDebug("AutomationService", "🔊 speakAlert engine=$engine ttsReady=$ttsReady liveSummary=$liveSummary")
        AlwaysLiveManager.getInstanceOrNull()?.let { mgr ->
            mgr.onAiStateChanged("speaking")
            val sentimentText = if (full.isBlank()) short else full
            mgr.detectSentiment(sentimentText)
        }
        try {
            if (!live) {
                if (short.isBlank()) return VoiceDeliveryResult("-", null)
                speak(short)
                logDebug("AutomationService", "🔊 → Android TTS (device mode) spoken")
                return VoiceDeliveryResult("Android TTS", null)
            }
            if (full.isBlank()) return VoiceDeliveryResult("-", null)
            val apiKey = setting("api_key")
            if (apiKey.isBlank()) {
                logDebug("AutomationService", "🔊 ไม่มี api_key → Android TTS")
                if (short.isNotBlank()) speak(short)
                return VoiceDeliveryResult("Android TTS (ไม่มี api_key)", null)
            }

        val voiceName = setting("voice_name").ifBlank { "Aoede" }
        val chain = liveVoiceChain()
        logDebug("AutomationService", "🔊 Live chain: ${chain.joinToString(" → ") { it.second }} (2.5 Native ก่อน — เสถียรสุดจากการวัดจริง)")

        // API key pool — โควต้า/ความจุของ Live API ผูกกับ key; หมุน key ทุก attempt
        // (เคสจริง 2026-08-26 02:00: 3.1 มั่ว 2 รอบ + 2.5 ตาย บน key เดียวกันหมด
        //  ถ้า session ตายเพราะ key นั้นติดเพดาน การเปลี่ยน key มีโอกาสรอดทันที)
        val apiKeys = buildList {
            add(apiKey)
            setting("gemini_api_keys").lines().map { it.trim() }
                .filter { it.isNotBlank() && it != apiKey && it !in this }
                .forEach { add(it) }
        }
        if (apiKeys.size > 1) {
            logDebug("AutomationService", "🔊 Live voice key pool = ${apiKeys.size} keys — หมุนทุก attempt")
        }
        var keyAttempt = 0

        // The scheduler already guarantees one Live session at a time. Keeping the
        // session implementation free of queue waiting is important: a second signal
        // is represented by a scheduler item instead of a coroutine parked on a mutex.
        logDebug("AutomationService", "🔊 Live Scheduler dispatch → session start")
        try {
            for ((index, pair) in chain.withIndex()) {
                val (model, label) = pair
                val tag = if (index == 0) label else "$label (fallback)"
                // โมเดล Live ตัว preview มั่วเป็นบาง turn: session config เดียวกันเป๊ะ
                // บางครั้งได้ audio ปกติ บางครั้งตอบ transcript ล้วนโดยไม่มี audio เลย
                // (log 2026-08-25: 3.1 TRANSCRIPT_ONLY 5 ครั้ง สลับกับ AUDIO_SUCCESS 2 ครั้ง)
                // → ถ้าเจอ TRANSCRIPT_ONLY (ตอบ text จบไวใน ~3s) ให้ retry โมเดลเดิมอีก 1 ครั้ง
                //   ด้วย session ใหม่ก่อน ค่อย fallback ไปโมเดลถัดไป — ถูกกว่าเสีย chain ทั้งรอบ
                var result: LiveSpeechResult
                var attempt = 0
                while (true) {
                    attempt++
                    val attemptKey = apiKeys[keyAttempt % apiKeys.size]
                    keyAttempt++
                    result = try {
                        speakViaLive(attemptKey, model, full, voiceName, liveSummary)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        logError("AutomationService", "🔊 Live ($model) exception: ${e.message}", e)
                        LiveSpeechResult(false, null)
                    }
                    if (result.ok) break
                    if (result.cause == "TRANSCRIPT_ONLY" && attempt < 2) {
                        logDebug(
                            "AutomationService",
                            "🔊 Live ($model) TRANSCRIPT_ONLY → retry โมเดลเดิมด้วย session ใหม่ (ครั้งที่ ${attempt + 1}/2)"
                        )
                        continue
                    }
                    break
                }
                if (result.ok) {
                    liveModelAudioCooldownUntil.remove(model)
                    return VoiceDeliveryResult(tag, result.transcript)
                }
                if (!result.transcript.isNullOrBlank()) {
                    liveModelAudioCooldownUntil[model] =
                        System.currentTimeMillis() + liveAudioFailureCooldownMs
                    logDebug(
                        "AutomationService",
                        "🔊 Live ($model) ได้ transcript แต่ไม่มี audio → cooldown ${liveAudioFailureCooldownMs / 1000}s"
                    )
                }
                logDebug(
                    "AutomationService",
                    "🔊 Live ($model) ไม่ได้เสียง → cause=${result.cause}; " +
                        "transcriptChars=${result.transcript?.length ?: 0} → ลองตัวถัดไปใน chain"
                )
            }
        } finally {
            // Physical audio serialization is owned by Live Alert Scheduler.
        }
        logDebug("AutomationService", "🔊 Live chain พังทั้งหมด → Android TTS")
        if (short.isNotBlank()) speak(short)
        return VoiceDeliveryResult("Android TTS (fallback)", null)
        } finally {
            AlwaysLiveManager.getInstanceOrNull()?.onAiStateChanged("idle")
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (_: Exception) {}
        }
    }


    private suspend fun speakViaLive(
        apiKey: String,
        model: String,
        text: String,
        voiceName: String = "Aoede",
        liveSummary: Boolean = false
    ): LiveSpeechResult {
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val sampleRate = 24000
        val channel = android.media.AudioFormat.CHANNEL_OUT_MONO
        val encoding = android.media.AudioFormat.ENCODING_PCM_16BIT
        val minBuf = android.media.AudioTrack.getMinBufferSize(sampleRate, channel, encoding)
        val queue = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        var gotAudio = false
        var totalBytes = 0
        val transcriptBuilder = StringBuilder()
        var lastLoggedTranscriptChars = 0
        val t0 = System.currentTimeMillis()
        var setupAt = 0L
        var requestAt = 0L
        var firstChunkAt = 0L
        var firstTranscriptAt = 0L
        var turnCompleteAt = 0L
        var lastProgressAt = 0L
        var diagnosticPhase = "CONNECTING"
        var timeoutCause = "NONE"
        var apiErrorMessage: String? = null
        val sessionId = "${model.substringAfterLast('/').take(28)}-${t0 % 100000}"
        var track: android.media.AudioTrack? = null
        try {
            track = android.media.AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(encoding).setSampleRate(sampleRate).setChannelMask(channel)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, 64 * 1024))
                .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                .build()
            val theTrack = track
            val player = scope.async(Dispatchers.IO) {
                try {
                    theTrack.play()
                    while (!done.get() || queue.isNotEmpty()) {
                        val chunk = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                        theTrack.write(chunk, 0, chunk.size)
                    }
                    // The queue can be empty while AudioTrack still has PCM buffered.
                    // Releasing the track immediately can truncate the last syllables.
                    val expectedFrames = totalBytes / 2L // PCM 16-bit, mono
                    val drainDeadline = System.currentTimeMillis() + 4_000L
                    while (expectedFrames > 0L &&
                        (theTrack.playbackHeadPosition.toLong() and 0xFFFF_FFFFL) < expectedFrames &&
                        System.currentTimeMillis() < drainDeadline
                    ) {
                        delay(20L)
                    }
                } catch (e: Exception) {
                    logError("AutomationService", "🔊 Live playback error: ${e.message}", e)
                }
            }
            withTimeoutOrNull(liveVoiceSessionTimeoutMs) {
                try {
                    geminiClient.webSocket(url) {
                        val wsConnectedAt = System.currentTimeMillis()
                        diagnosticPhase = "SETUP_SENT"
                        logDebug(
                            "AutomationService",
                            "🔊 Live[$sessionId] WS_CONNECTED at=${wsConnectedAt - t0}ms → setup sent model=$model voice=$voiceName " +
                                "key=…${apiKey.takeLast(4)} timeout=${liveVoiceSessionTimeoutMs}ms"
                        )
                        val isLive31 = model.contains("3.1-flash-live")
                        val setup = LiveSetupMessage(setup = LiveSetup(
                            model = if (model.startsWith("models/")) model else "models/$model",
                            outputAudioTranscription = JsonObject(emptyMap()),
                            systemInstruction = LiveSystemInstruction(parts = listOf(
                                LivePart(text = com.skyliner2008.jarvis.ai.JarvisPersona.CORE_IDENTITY),
                                LivePart(text = if (liveSummary) {
                                    """
                                    [LIVE SIGNAL ALERT MODE]
                                    คุณกำลังทำหน้าที่เป็นเสียงแจ้งเตือนสัญญาณเทรดของ JARVIS ไม่ใช่นักวิเคราะห์แบบเต็มรูปแบบ
                                    วิเคราะห์ payload ที่ผู้ใช้ส่งมาแล้วพูดสรุปเป็นภาษาไทยให้กระชับ ชัดเจน และฟังจบเร็ว
                                    จำกัดคำตอบให้กระชับ: 1-2 ประโยค ประมาณ 25-40 คำ พูดสรุปให้จบประโยคอย่างสมบูรณ์ และลงท้ายด้วย ค่ะ เสมอ เพื่อให้เสียงแจ้งเตือนชัดเจนและเป็นธรรมชาติ
                                    ต้องกล่าวให้ครบเท่าที่ข้อมูลมี: Signal/Strategy และ Entry/SL/TP; ถ้าข้อมูลยาวเกิน ให้ตัด Context/Risk ก่อน แต่ห้ามตัดราคาและทิศทาง
                                    หากข้อมูลไม่ครบ ให้พูดเฉพาะข้อมูลที่มี ห้ามเดา ห้ามเพิ่มราคา อินดิเคเตอร์ ข่าว หรือเหตุการณ์ตลาด
                                    ห้ามรับประกันผลกำไร ไม่ต้องอธิบายเหตุผลเชิงลึก ไม่ต้องมีคำเกริ่น คำลงท้าย หรือคำเชิญให้ทำสิ่งอื่น
                                    จบคำตอบทันทีหลังข้อมูลสำคัญครบ
                                    พูดเป็นภาษาพูดต่อเนื่อง ไม่มี Markdown ไม่มีตาราง ไม่มี bullet และไม่พูดถึงกระบวนการคิดภายใน
                                    """.trimIndent()
                                } else {
                                    "[STRICT] หน้าที่ตอนนี้คืออ่านข้อความแจ้งเตือนที่ได้รับออกเสียงเป็นภาษาไทยตรงๆ ด้วยน้ำเสียงและบุคลิกข้างต้น ห้ามเพิ่มเติม ห้ามตอบโต้ ห้ามเรียกเครื่องมือ"
                                })
                            )),
                            generationConfig = LiveGenerationConfig(
                                responseModalities = listOf("AUDIO"),
                                thinkingConfig = if (isLive31 && liveSummary) {
                                    com.skyliner2008.jarvis.data.LiveThinkingConfig(thinkingLevel = "low", includeThoughts = false)
                                } else null,
                                speechConfig = LiveSpeechConfig(
                                    voiceConfig = LiveVoiceConfig(
                                        prebuiltVoiceConfig = LivePrebuiltVoiceConfig(voiceName = voiceName)
                                    )
                                )
                            )
                        ))
                        val setupJson = json.encodeToString(LiveSetupMessage.serializer(), setup)
                        // log setup จริงที่ส่งไป (ตัดย่อ) — ป้องกันเคส config หลุด/field ผิดแล้วไม่มีหลักฐาน
                        logDebug(
                            "AutomationService",
                            "🔊 Live[$sessionId] setupJson(${setupJson.length}) = ${setupJson.take(600)}"
                        )
                        send(Frame.Text(setupJson))
                        var sentText = false
                        var setupWatchdog: kotlinx.coroutines.Job? = launch(Dispatchers.IO) {
                            delay(liveVoiceSetupTimeoutMs)
                            if (!sentText) {
                                timeoutCause = "SETUP_TIMEOUT"
                                diagnosticPhase = "SETUP_TIMEOUT"
                                logDebug(
                                    "AutomationService",
                                    "🔊 Live[$sessionId] SETUP_TIMEOUT ${liveVoiceSetupTimeoutMs}ms → close; likely API/network (no setupComplete)"
                                )
                                close()
                            }
                        }
                        var audioWatchdog: kotlinx.coroutines.Job? = null
                        var audioIdleWatchdog: kotlinx.coroutines.Job? = null
                        for (frame in incoming) {
                            val raw = when (frame) {
                                is Frame.Text -> frame.readText()
                                is Frame.Binary -> frame.readBytes().decodeToString()
                                else -> continue
                            }
                            val msg = try {
                                json.decodeFromString(LiveServerMessage.serializer(), raw)
                            } catch (_: Exception) { continue }
                            var shouldClose = false
                            msg.error?.let {
                                apiErrorMessage = it.message
                                timeoutCause = "API_ERROR"
                                diagnosticPhase = "API_ERROR"
                                logError(
                                    "AutomationService",
                                    "🔊 Live[$sessionId] API_ERROR phase=$diagnosticPhase elapsed=${System.currentTimeMillis() - t0}ms message=${it.message}",
                                    null
                                )
                                shouldClose = true
                            }
                            if (msg.setupComplete != null && !sentText) {
                                sentText = true
                                setupAt = System.currentTimeMillis()
                                diagnosticPhase = "READY"
                                
                                setupWatchdog?.cancel()
                                logDebug("AutomationService", "🔊 Live[$sessionId] READY total=${setupAt - t0}ms afterWs=${setupAt - wsConnectedAt}ms → ส่ง ${if (liveSummary) "Signal Summary" else "ข้อความ"}")
                                lastProgressAt = System.currentTimeMillis()
                                audioWatchdog = launch(Dispatchers.IO) {
                                    delay(liveVoiceFirstOutputTimeoutMs)
                                    if (!gotAudio) {
                                        timeoutCause = if (transcriptBuilder.isNotEmpty()) "FIRST_AUDIO_TIMEOUT_TRANSCRIPT_ONLY" else "FIRST_AUDIO_TIMEOUT_NO_OUTPUT"
                                        diagnosticPhase = "FIRST_AUDIO_TIMEOUT"
                                        logDebug(
                                            "AutomationService",
                                            "🔊 Live[$sessionId] FIRST_AUDIO_TIMEOUT ${liveVoiceFirstOutputTimeoutMs}ms after READY; " +
                                                "transcriptChars=${transcriptBuilder.length}; " +
                                                "requestAge=${if (requestAt > 0) System.currentTimeMillis() - requestAt else -1}ms; " +
                                                "likely=${if (transcriptBuilder.isNotEmpty()) "API_AUDIO_OUTPUT_STALL" else "API_GENERATION_OR_NETWORK_STALL"} → close"
                                        )
                                        close()
                                    }
                                }
                                if (isLive31) {
                                    // Gemini 3.1 Live ใช้ realtimeInput สำหรับข้อความระหว่าง session
                                    val realtime = com.skyliner2008.jarvis.data.LiveRealtimeInputMessage(
                                        realtimeInput = com.skyliner2008.jarvis.data.LiveRealtimeInputData(text = text)
                                    )
                                    send(Frame.Text(json.encodeToString(com.skyliner2008.jarvis.data.LiveRealtimeInputMessage.serializer(), realtime)))
                                } else {
                                    val cc = LiveClientContentMessage(clientContent = LiveContentWrapper(
                                        turns = listOf(LiveTurn(role = "user", parts = listOf(LivePart(text = text)))),
                                        turnComplete = true
                                    ))
                                    send(Frame.Text(json.encodeToString(LiveClientContentMessage.serializer(), cc)))
                                }
                                requestAt = System.currentTimeMillis()
                                diagnosticPhase = "REQUEST_SENT"
                                logDebug(
                                    "AutomationService",
                                    "🔊 Live[$sessionId] REQUEST_SENT total=${requestAt - t0}ms afterReady=${requestAt - setupAt}ms payloadChars=${text.length} modality=AUDIO"
                                )
                                continue
                            }
                            val sc = msg.serverContent
                            if (sc != null) {
                                // Gemini 3.1 อาจส่ง audio + transcript ใน server event เดียวกัน
                                sc.modelTurn?.parts?.forEach { p ->
                                    p.inlineData?.takeIf { it.mimeType.contains("audio") }?.let { blob ->
                                        val pcmChunk = android.util.Base64.decode(blob.data, android.util.Base64.DEFAULT)
                                        if (firstChunkAt == 0L) {
                                            firstChunkAt = System.currentTimeMillis()
                                            lastProgressAt = firstChunkAt
                                            audioWatchdog?.cancel()
                                            audioIdleWatchdog?.cancel()
                                            audioIdleWatchdog = launch(Dispatchers.IO) {
                                                while (isActive && !done.get()) {
                                                    delay(2_000L)
                                                    val idleMs = System.currentTimeMillis() - lastProgressAt
                                                    if (gotAudio && idleMs >= liveVoiceAudioIdleTimeoutMs) {
                                                        timeoutCause = "AUDIO_STREAM_IDLE_TIMEOUT"
                                                        diagnosticPhase = "AUDIO_STREAM_STALLED"
                                                        logDebug(
                                                            "AutomationService",
                                                            "🔊 Live[$sessionId] AUDIO_STREAM_IDLE_TIMEOUT ${liveVoiceAudioIdleTimeoutMs}ms; " +
                                                                "lastProgress=${idleMs}ms audioBytes=$totalBytes transcriptChars=${transcriptBuilder.length} → close"
                                                        )
                                                        close()
                                                        break
                                                    }
                                                }
                                            }
                                            logDebug("AutomationService", "🔊 Live first audio chunk (${firstChunkAt - t0}ms)")
                                        }
                                        lastProgressAt = System.currentTimeMillis()
                                        queue.add(pcmChunk)
                                        totalBytes += pcmChunk.size
                                        gotAudio = true
                                        diagnosticPhase = "AUDIO_STREAMING"
                                    }
                                }
                                sc.outputTranscription?.text?.let { transcript ->
                                    if (transcript.isNotBlank()) {
                                        if (firstTranscriptAt == 0L) {
                                            firstTranscriptAt = System.currentTimeMillis()
                                            diagnosticPhase = if (gotAudio) "AUDIO_TRANSCRIPT" else "TRANSCRIPT_NO_AUDIO"
                                            logDebug(
                                                "AutomationService",
                                                "📝 Live[$sessionId] first transcript (${firstTranscriptAt - t0}ms, audio=$gotAudio)"
                                            )
                                        }
                                        transcriptBuilder.append(transcript)
                                        if (liveSummary && gotAudio && transcriptBuilder.length >= liveVoiceSummaryCharCap) {
                                            timeoutCause = "AUDIO_RESPONSE_LENGTH_CAP"
                                            diagnosticPhase = "AUDIO_LENGTH_CAPPED"
                                            logDebug("AutomationService", "🔊 Live[$sessionId] RESPONSE_LENGTH_CAP ${liveVoiceSummaryCharCap} chars; audioBytes=$totalBytes → close")
                                            shouldClose = true
                                        }
                                        lastProgressAt = System.currentTimeMillis()
                                        // Keep logcat readable: transcript is still accumulated in full,
                                        // but operational logs emit only meaningful progress checkpoints.
                                        val transcriptChars = transcriptBuilder.length
                                        if (transcriptChars - lastLoggedTranscriptChars >= 50 ||
                                            transcriptChars >= liveVoiceSummaryCharCap ||
                                            transcriptChars == transcript.length) {
                                            logDebug("AutomationService", "📝 Live transcript progress chars=$transcriptChars audio=$gotAudio")
                                            lastLoggedTranscriptChars = transcriptChars
                                        }
                                    }
                                }
                                if (sc.turnComplete == true) {
                                    audioIdleWatchdog?.cancel()
                                    turnCompleteAt = System.currentTimeMillis()
                                    diagnosticPhase = "TURN_COMPLETE"
                                    logDebug("AutomationService", "🔊 Live[$sessionId] turnComplete (${turnCompleteAt - t0}ms)")
                                    shouldClose = true
                                }
                            }
                            if (shouldClose) { close(); break }
                        }
                        setupWatchdog?.cancel()
                        audioWatchdog?.cancel()
                        audioIdleWatchdog?.cancel()
                        val reason = kotlinx.coroutines.withTimeoutOrNull(1_500L) { closeReason.await() }
                        if (reason == null) {
                            logDebug(
                                "AutomationService",
                                "🔊 Live[$sessionId] CLOSE_REASON_TIMEOUT 1500ms → force fallback " +
                                    "phase=$diagnosticPhase cause=$timeoutCause " +
                                    "(${System.currentTimeMillis() - t0}ms, gotAudio=$gotAudio, transcriptChars=${transcriptBuilder.length})"
                            )
                        } else {
                            logDebug(
                                "AutomationService",
                                "🔊 Live[$sessionId] ws closed: code=${reason.code} reason=${reason.message} " +
                                    "phase=$diagnosticPhase cause=$timeoutCause " +
                                    "(${System.currentTimeMillis() - t0}ms, setup=${if (setupAt > 0) setupAt - t0 else -1}ms, " +
                                    "request=${if (requestAt > 0) requestAt - t0 else -1}ms, " +
                                    "firstAudio=${if (firstChunkAt > 0) firstChunkAt - t0 else -1}ms, " +
                                    "firstTranscript=${if (firstTranscriptAt > 0) firstTranscriptAt - t0 else -1}ms, " +
                                    "turnComplete=${if (turnCompleteAt > 0) turnCompleteAt - t0 else -1}ms, " +
                                    "gotAudio=$gotAudio, transcriptChars=${transcriptBuilder.length}, apiError=${apiErrorMessage ?: "-"})"
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    logError("AutomationService", "🔊 Live session failed (${System.currentTimeMillis() - t0}ms): ${e.message}", e)
                }
            } ?: run {
                timeoutCause = "SESSION_MAX_DURATION"
                logDebug(
                    "AutomationService",
                    "🔊 Live[$sessionId] SESSION_MAX_DURATION ${liveVoiceSessionTimeoutMs}ms → force fallback; " +
                        "phase=$diagnosticPhase evidence=" +
                        when {
                            apiErrorMessage != null -> "API_ERROR"
                            setupAt == 0L -> "API_OR_NETWORK_BEFORE_SETUP"
                            !gotAudio && transcriptBuilder.isNotEmpty() -> "API_AUDIO_OUTPUT_STALL"
                            !gotAudio -> "API_GENERATION_OR_NETWORK_STALL"
                            gotAudio && turnCompleteAt == 0L -> "LONG_AUDIO_STREAM_NO_TURN_COMPLETE"
                            else -> "UNKNOWN"
                        } +
                        " setup=${if (setupAt > 0) setupAt - t0 else -1}ms request=${if (requestAt > 0) requestAt - t0 else -1}ms " +
                        "firstAudio=${if (firstChunkAt > 0) firstChunkAt - t0 else -1}ms transcript=${transcriptBuilder.length}"
                )
            }
            done.set(true)
            player.await()
            if (gotAudio) {
                logDebug(
                    "AutomationService",
                    "🔊 → Live ($model) AUDIO_DELIVERED $totalBytes bytes (${System.currentTimeMillis() - t0}ms, " +
                        "first chunk ${if (firstChunkAt > 0) "${firstChunkAt - t0}ms" else "-"}, " +
                        "summaryChars=${transcriptBuilder.length})"
                )
            }
            val finalCause = when {
                timeoutCause != "NONE" -> timeoutCause
                apiErrorMessage != null -> "API_ERROR"
                gotAudio -> "AUDIO_SUCCESS"
                transcriptBuilder.isNotEmpty() -> "TRANSCRIPT_ONLY"
                else -> diagnosticPhase
            }
            logDebug(
                "AutomationService",
                "🔊 Live[$sessionId] RESULT model=$model cause=$finalCause " +
                    "queueHandled=true setup=${if (setupAt > 0) setupAt - t0 else -1}ms " +
                    "request=${if (requestAt > 0) requestAt - t0 else -1}ms " +
                    "generation=${if (requestAt > 0) System.currentTimeMillis() - requestAt else -1}ms " +
                    "firstAudio=${if (firstChunkAt > 0) firstChunkAt - t0 else -1}ms " +
                    "firstTranscript=${if (firstTranscriptAt > 0) firstTranscriptAt - t0 else -1}ms " +
                    "turnComplete=${if (turnCompleteAt > 0) turnCompleteAt - t0 else -1}ms " +
                    "audioBytes=$totalBytes transcriptChars=${transcriptBuilder.length} apiError=${apiErrorMessage ?: "-"}"
            )
            return LiveSpeechResult(gotAudio, transcriptBuilder.toString().trim(), finalCause)
        } finally {
            done.set(true)
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
        }
    }


    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        try { geminiClient.close() } catch (_: Throwable) {}
    }
}

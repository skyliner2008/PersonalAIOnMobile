package com.example.personalaibot.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * HotwordDetector — Wake-on-call detection engine
 *
 * Low-power audio monitoring approach:
 * 1. ใช้ AudioRecord ที่ sample rate ต่ำ (8000 Hz, MONO) เพื่อประหยัดแบต
 * 2. คำนวณ RMS energy → ถ้าเกิน threshold = มีเสียงพูด
 * 3. เมื่อตรวจจับเสียงพูด → trigger callback (ให้ caller ตัดสินใจว่าจะ process ต่อยังไง)
 *
 * Battery-Aware Design:
 * - Duty cycle: ฟังเป็นช่วง (configurable: listenMs / pauseMs)
 * - ไม่ process ถ้า RMS ต่ำกว่า threshold
 * - ใช้ coroutine ไม่ block thread
 *
 * Note: นี่คือ Energy-based Voice Activity Detection (VAD) ไม่ใช่ keyword spotting
 * สำหรับ "hotword" จริงๆ ต้องใช้ ML model (เช่น Porcupine) แต่ energy-based approach
 * เพียงพอสำหรับ trigger → ส่งไปถาม Gemini ว่าผู้ใช้เรียกชื่อ AI หรือเปล่า
 */
class HotwordDetector(
    private val context: Context,
    private val onVoiceDetected: () -> Unit
) {
    companion object {
        const val TAG = "HotwordDetector"
        const val SAMPLE_RATE = 8000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        /** RMS threshold for "voice detected" — calibrate per device */
        const val DEFAULT_RMS_THRESHOLD = 1500.0
        /** Minimum duration of voice to trigger (ms) — prevents false positives from clicks */
        const val MIN_VOICE_DURATION_MS = 500L
        /** Cooldown between triggers (ms) — prevent rapid re-triggering */
        const val COOLDOWN_MS = 3000L
    }

    // ─── Configuration ───────────────────────────────────────────────────
    data class Config(
        val rmsThreshold: Double = DEFAULT_RMS_THRESHOLD,
        val listenDurationMs: Long = 2000,
        val pauseDurationMs: Long = 1000,
        val minVoiceDurationMs: Long = MIN_VOICE_DURATION_MS,
        val cooldownMs: Long = COOLDOWN_MS
    )

    var config = Config()
        set(value) {
            field = value
            Log.d(TAG, "Config updated: $value")
        }

    // ─── State ───────────────────────────────────────────────────────────
    enum class DetectorState { IDLE, LISTENING, PAUSED, STOPPED }

    private val _state = MutableStateFlow(DetectorState.IDLE)
    val state: StateFlow<DetectorState> = _state.asStateFlow()

    private val _currentRms = MutableStateFlow(0.0)
    val currentRms: StateFlow<Double> = _currentRms.asStateFlow()

    private var detectorJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var lastTriggerTime = 0L
    private var voiceStartTime = 0L
    private var isVoiceActive = false

    // ═══════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Start hotword detection loop
     * Uses duty cycle: listen for [listenDurationMs] → pause for [pauseDurationMs] → repeat
     */
    fun start(scope: CoroutineScope) {
        if (_state.value == DetectorState.LISTENING || _state.value == DetectorState.PAUSED) {
            Log.w(TAG, "Already running, ignoring start()")
            return
        }

        if (!hasMicPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission not granted, cannot start")
            return
        }

        Log.i(TAG, "Starting hotword detection (duty cycle: ${config.listenDurationMs}ms on / ${config.pauseDurationMs}ms off)")

        detectorJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    // ─── Listen phase ─────────────────────────────────
                    _state.value = DetectorState.LISTENING
                    listenForVoice(config.listenDurationMs)

                    if (!isActive) break

                    // ─── Pause phase ──────────────────────────────────
                    if (config.pauseDurationMs > 0) {
                        _state.value = DetectorState.PAUSED
                        delay(config.pauseDurationMs)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Detection loop cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Detection loop error", e)
            } finally {
                releaseAudioRecord()
                _state.value = DetectorState.STOPPED
            }
        }
    }

    /**
     * Stop hotword detection
     */
    fun stop() {
        Log.i(TAG, "Stopping hotword detection")
        detectorJob?.cancel()
        detectorJob = null
        releaseAudioRecord()
        _state.value = DetectorState.IDLE
        _currentRms.value = 0.0
    }

    /**
     * Check if running
     */
    fun isRunning(): Boolean {
        return _state.value == DetectorState.LISTENING || _state.value == DetectorState.PAUSED
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal — Audio Processing
    // ═══════════════════════════════════════════════════════════════════════

    private suspend fun listenForVoice(durationMs: Long) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                releaseAudioRecord()
                return
            }

            audioRecord?.startRecording()

            val buffer = ShortArray(bufferSize / 2)
            val endTime = System.currentTimeMillis() + durationMs

            while (System.currentTimeMillis() < endTime && currentCoroutineContext().isActive) {
                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (readCount > 0) {
                    val rms = calculateRms(buffer, readCount)
                    _currentRms.value = rms

                    processRms(rms)
                }
            }

            try {
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord?.stop()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Safe catch: audioRecord.stop() failed", e)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception — RECORD_AUDIO permission revoked?", e)
        } catch (e: Exception) {
            Log.e(TAG, "Audio recording error", e)
        } finally {
            releaseAudioRecord()
        }
    }

    /**
     * Process RMS value — detect sustained voice above threshold
     */
    private fun processRms(rms: Double) {
        val now = System.currentTimeMillis()

        if (rms >= config.rmsThreshold) {
            if (!isVoiceActive) {
                // Voice just started
                isVoiceActive = true
                voiceStartTime = now
            } else {
                // Voice ongoing — check if sustained long enough
                val voiceDuration = now - voiceStartTime
                if (voiceDuration >= config.minVoiceDurationMs) {
                    // Check cooldown
                    if (now - lastTriggerTime >= config.cooldownMs) {
                        Log.i(TAG, "Voice detected! RMS=${"%.0f".format(rms)} duration=${voiceDuration}ms")
                        lastTriggerTime = now
                        isVoiceActive = false
                        onVoiceDetected()
                    }
                }
            }
        } else {
            // Below threshold — reset
            isVoiceActive = false
        }
    }

    /**
     * Calculate Root Mean Square of audio buffer
     */
    private fun calculateRms(buffer: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            sum += buffer[i].toDouble() * buffer[i].toDouble()
        }
        return sqrt(sum / count)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal — Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private fun releaseAudioRecord() {
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    try { it.stop() } catch (_: Exception) {}
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}

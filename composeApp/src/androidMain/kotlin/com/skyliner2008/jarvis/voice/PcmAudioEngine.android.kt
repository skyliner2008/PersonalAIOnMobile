package com.skyliner2008.jarvis.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

actual class PcmAudioEngine {
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    // อ่านจาก JarvisMicThread / เขียนจาก main — ต้อง @Volatile ไม่งั้น thread อาจไม่เห็นค่า false และวน read ต่อ
    @Volatile
    private var isRecording = false
    private var micThread: Thread? = null
    actual var onVolumeChanged: ((Float) -> Unit)? = null

    actual var isRobotVoiceEnabled: Boolean = false
        set(value) {
            field = value
            updatePlaybackParameters()
        }

    private var robotCarrierPhase = 0.0
    private val robotCombBuffer = FloatArray(48) // ~500Hz metallic chamber resonance at 24kHz
    private var robotCombIndex = 0

    init {
        logDebug("PcmAudio", "Initializing AudioTrack (Speaker)")
        val sampleRate = 24000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minPlaySize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        val usage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes.USAGE_VOICE_COMMUNICATION
        } else {
            AudioAttributes.USAGE_MEDIA
        }

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes((minPlaySize * 8).coerceAtLeast(1024 * 16))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                
            audioTrack?.play()
            updatePlaybackParameters()
            logDebug("PcmAudio", "AudioTrack Ready")
        } catch (e: Exception) {
            logError("PcmAudio", "Failed to init AudioTrack", e)
        }
    }

    actual fun startRecording(onAudioData: (ByteArray) -> Unit) {
        if (isRecording) {
            logDebug("PcmAudio", "Already recording, skipping start")
            return
        }
        
        logDebug("PcmAudio", "Starting Microphone...")
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minRecSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        
        try {
            // VOICE_COMMUNICATION is highly recommended for echo cancellation in two-way voice chat
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                minRecSize
            )
            
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                logError("PcmAudio", "AudioRecord initialization failed!")
                return
            }

            // Attempt to apply Acoustic Echo Canceling
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(record.audioSessionId)
                aec?.enabled = true
                logDebug("PcmAudio", "AEC enabled: ${aec?.enabled == true}")
            } else {
                logDebug("PcmAudio", "AEC not available on this device")
            }

            // Attempt to apply Noise Suppression
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(record.audioSessionId)
                ns?.enabled = true
                logDebug("PcmAudio", "NoiseSuppressor enabled: ${ns?.enabled == true}")
            }

            audioRecord = record
            record.startRecording()
            isRecording = true
            logDebug("PcmAudio", "MIC_STARTED successfully")
            
            micThread = thread(name = "JarvisMicThread") {
                val buffer = ByteArray(minRecSize)
                while (isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0 && isRecording) {
                        onAudioData(buffer.copyOf(read))
                        
                        // Compute RMS volume for Speech Detection
                        val volume = calculateRMS(buffer, read)
                        onVolumeChanged?.invoke(volume)
                    }
                }
                logDebug("PcmAudio", "Mic thread stopped")
            }
        } catch (e: SecurityException) {
            logError("PcmAudio", "Mic permission denied", e)
        } catch (e: Exception) {
            logError("PcmAudio", "Audio record error", e)
        }
    }

    actual fun stopRecording() {
        logDebug("PcmAudio", "Stopping Mic...")
        isRecording = false
        try {
            // stop() ปลุก read() ที่ blocking อยู่ → รอ mic thread ออกจาก loop ก่อน release
            // (เดิม release ขณะ thread ยังอยู่ใน read() ของ AudioRecord ตัวเดียวกัน)
            audioRecord?.stop()
            micThread?.let { t ->
                if (t !== Thread.currentThread()) t.join(300)
            }
            audioRecord?.release()
            aec?.release()
            ns?.release()
        } catch (e: Exception) {
            logError("PcmAudio", "Error stopping mic", e)
        }
        micThread = null
        audioRecord = null
        aec = null
        ns = null
    }

    private fun updatePlaybackParameters() {
        try {
            val track = audioTrack ?: return
            if (isRobotVoiceEnabled) {
                val params = PlaybackParams().apply {
                    pitch = 1.28f
                    speed = 1.04f
                }
                track.playbackParams = params
                logDebug("PcmAudio", "🤖 Robot Voice PlaybackParams applied: pitch=1.28, speed=1.04")
            } else {
                val params = PlaybackParams().apply {
                    pitch = 1.0f
                    speed = 1.0f
                }
                track.playbackParams = params
                logDebug("PcmAudio", "Standard Voice PlaybackParams restored: pitch=1.0")
            }
        } catch (e: Exception) {
            logError("PcmAudio", "Failed to update PlaybackParams: ${e.message}")
        }
    }

    private fun applyRobotDsp(pcmBytes: ByteArray): ByteArray {
        val outBytes = ByteArray(pcmBytes.size)
        val numSamples = pcmBytes.size / 2
        val phaseStep = 2.0 * PI * 72.0 / 24000.0

        for (i in 0 until numSamples) {
            val byteIdx = i * 2
            val low = pcmBytes[byteIdx].toInt() and 0xFF
            val high = pcmBytes[byteIdx + 1].toInt()
            val raw = (high shl 8) or low
            val sample = raw.toShort()
            val x = sample / 32768.0f

            // 1. Ring modulation with 72Hz carrier
            robotCarrierPhase += phaseStep
            if (robotCarrierPhase >= 2.0 * PI) {
                robotCarrierPhase -= 2.0 * PI
            }
            val carrier = sin(robotCarrierPhase).toFloat()
            // 55% dry + 45% ring-modulated (retains clear speech intelligibility with rich metallic robot timbre)
            val ringMod = x * (0.55f + 0.45f * carrier)

            // 2. Robot Chassis Resonator (Feedforward comb filter)
            val delayed = robotCombBuffer[robotCombIndex]
            robotCombBuffer[robotCombIndex] = x
            robotCombIndex = (robotCombIndex + 1) % robotCombBuffer.size
            val withResonance = ringMod + delayed * 0.35f

            // 3. Soft analog saturation (warm synth crunch)
            val saturated = (withResonance * 1.12f).coerceIn(-1.0f, 1.0f)
            val sOut = (saturated * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()

            val sInt = sOut.toInt()
            outBytes[byteIdx] = (sInt and 0xFF).toByte()
            outBytes[byteIdx + 1] = ((sInt shr 8) and 0xFF).toByte()
        }

        return outBytes
    }

    actual fun playAudio(pcmBytes: ByteArray) {
        try {
            val bufferToWrite = if (isRobotVoiceEnabled) {
                applyRobotDsp(pcmBytes)
            } else {
                pcmBytes
            }
            audioTrack?.write(bufferToWrite, 0, bufferToWrite.size)
        } catch (e: Exception) {
            logError("PcmAudio", "AudioTrack write error", e)
        }
    }
    
    actual fun stopPlaying() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            // กลับสู่สถานะพร้อมเล่นทันที — ไม่งั้น chunk ถัดไปหลัง flush (เช่นหลัง interrupted) จะไม่มีเสียง
            audioTrack?.play()
            robotCombBuffer.fill(0f)
            robotCombIndex = 0
        } catch (e: Exception) {
            logError("PcmAudio", "stopPlaying error", e)
        }
    }

    actual fun release() {
        stopRecording()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    private fun calculateRMS(buffer: ByteArray, size: Int): Float {
        var sum = 0.0
        for (i in 0 until size / 2) {
            val sample = ((buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)).toShort()
            sum += sample.toDouble() * sample.toDouble()
        }
        val rms = Math.sqrt(sum / (size / 2))
        return (rms / 32768.0).toFloat() // Normalized 0.0 to 1.0
    }
}

package com.example.personalaibot.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlin.concurrent.thread

actual class PcmAudioEngine {
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var isRecording = false

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
            
            thread(name = "JarvisMicThread") {
                val buffer = ByteArray(minRecSize)
                while (isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        onAudioData(buffer.copyOf(read))
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
            audioRecord?.stop()
            audioRecord?.release()
            aec?.release()
            ns?.release()
        } catch (e: Exception) {
            logError("PcmAudio", "Error stopping mic", e)
        }
        audioRecord = null
        aec = null
        ns = null
    }

    actual fun playAudio(pcmBytes: ByteArray) {
        audioTrack?.write(pcmBytes, 0, pcmBytes.size)
    }
    
    actual fun stopPlaying() {
        audioTrack?.pause()
        audioTrack?.flush()
    }

    actual fun release() {
        stopRecording()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}

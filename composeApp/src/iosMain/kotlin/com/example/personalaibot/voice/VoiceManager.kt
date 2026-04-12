package com.example.personalaibot.voice

/**
 * iOS VoiceManager — Stub implementation
 *
 * TODO Phase 3: ใช้ AVFoundation + Speech framework
 *   - AVAudioEngine สำหรับ mic recording
 *   - SFSpeechRecognizer สำหรับ STT
 *   - AVSpeechSynthesizer สำหรับ TTS
 *
 * ต้องเพิ่ม permissions ใน Info.plist:
 *   - NSMicrophoneUsageDescription
 *   - NSSpeechRecognitionUsageDescription
 */
actual class VoiceManager {

    actual val isListening: Boolean = false

    actual fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        onError("Voice input ยังไม่รองรับบน iOS ในเวอร์ชันนี้")
    }

    actual fun stopListening() {
        // stub
    }

    actual fun speak(text: String, onDone: (() -> Unit)?) {
        // stub — on iOS TTS can be added via AVSpeechSynthesizer
        onDone?.invoke()
    }

    actual fun isAvailable(): Boolean = false

    actual fun shutdown() {
        // stub
    }
}

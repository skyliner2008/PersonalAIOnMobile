package com.example.personalaibot.voice

actual class PcmAudioEngine {
    actual fun startRecording(onAudioData: (ByteArray) -> Unit) {}
    actual fun stopRecording() {}
    actual fun playAudio(pcmBytes: ByteArray) {}
    actual fun stopPlaying() {}
    actual fun release() {}
}

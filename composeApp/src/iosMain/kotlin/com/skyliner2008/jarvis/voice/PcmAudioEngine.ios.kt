package com.skyliner2008.jarvis.voice

actual class PcmAudioEngine {
    actual var onVolumeChanged: ((Float) -> Unit)? = null
    actual var isRobotVoiceEnabled: Boolean = false
    actual fun startRecording(onAudioData: (ByteArray) -> Unit) {}
    actual fun stopRecording() {}
    actual fun playAudio(pcmBytes: ByteArray) {}
    actual fun stopPlaying() {}
    actual fun release() {}
}

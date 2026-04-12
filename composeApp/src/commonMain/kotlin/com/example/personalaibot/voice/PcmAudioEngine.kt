package com.example.personalaibot.voice

/**
 * Interface สำหรับสตรีมเสียงดิบๆ (PCM 16-bit) 
 * ส่งเข้า Gemini Live (Recording) และเล่นจาก Gemini Live (Playing)
 */
expect class PcmAudioEngine() {
    fun startRecording(onAudioData: (ByteArray) -> Unit)
    fun stopRecording()
    fun playAudio(pcmBytes: ByteArray)
    fun stopPlaying()
    fun release()
}

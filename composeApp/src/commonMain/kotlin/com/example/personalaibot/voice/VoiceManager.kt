package com.example.personalaibot.voice

/**
 * Platform-agnostic voice interface สำหรับ Speech-to-Text และ Text-to-Speech
 * - Android: ใช้ SpeechRecognizer + TextToSpeech (Android SDK)
 * - iOS: stub — พร้อมขยายด้วย AVFoundation ในอนาคต
 *
 * สร้าง instance ใน platform code (MainActivity / MainViewController)
 * แล้วส่งผ่าน App() → JarvisViewModel
 */
expect class VoiceManager {
    /** true เมื่อกำลังฟังเสียงอยู่ */
    val isListening: Boolean

    /**
     * เริ่มฟังเสียงจาก microphone
     * @param onResult callback เมื่อได้ข้อความ (STT result)
     * @param onError  callback เมื่อเกิด error
     */
    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit)

    /** หยุดฟัง (ก่อน timeout) */
    fun stopListening()

    /**
     * อ่านข้อความด้วยเสียง (TTS)
     * @param text   ข้อความที่ต้องการพูด
     * @param onDone callback เมื่อพูดจบ (optional)
     */
    fun speak(text: String, onDone: (() -> Unit)?)

    /** ตรวจว่า device รองรับ Voice features หรือไม่ */
    fun isAvailable(): Boolean

    /** ปล่อย resources เมื่อไม่ใช้งาน */
    fun shutdown()
}

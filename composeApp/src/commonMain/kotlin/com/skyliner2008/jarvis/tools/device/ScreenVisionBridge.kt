package com.skyliner2008.jarvis.tools.device

/**
 * ScreenVisionBridge — สะพานส่ง "ภาพหน้าจอจริง" จาก Accessibility Service (androidMain)
 * เข้าสู่ Gemini Live session ผ่าน pipeline เดียวกับเฟรมกล้อง
 *
 * ใช้เมื่อ a11y tree อ่านไม่ออก เช่น แผนที่ใน Google Maps, WebView, เกม, กราฟ
 *
 * 2026-09-20 — Phase B: ให้ AI เห็นหน้าจอจริง ไม่ใช่แค่ข้อความ
 */
object ScreenVisionBridge {

    /** ติดตั้งโดย JarvisViewModel — ส่ง JPEG base64 เข้า Live session */
    var sendFrame: (suspend (String) -> Unit)? = null

    val isAvailable: Boolean get() = sendFrame != null

    suspend fun send(jpegBase64: String): Boolean {
        val sender = sendFrame ?: return false
        sender(jpegBase64)
        return true
    }
}

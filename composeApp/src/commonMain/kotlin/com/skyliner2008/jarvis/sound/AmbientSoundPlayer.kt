package com.skyliner2008.jarvis.sound

import com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme

/**
 * AmbientSoundPlayer — Cross-platform bridge สำหรับควบคุมเสียงบรรยากาศพื้นหลัง (Background Ambient FX)
 *
 * รองรับการเปลี่ยนธีมเสียงตาม BackgroundTheme (ฝนตก, กลางคืน, แดดส่อง, พายุ, ซากุระ, Matrix ฯลฯ)
 * พร้อมระบบ Audio Ducking (ลดเสียงลงอัตโนมัติเมื่อ AI หรือผู้ใช้กำลังพูด)
 */
object AmbientSoundPlayer {

    /** Callback handler ที่เชื่อมกับ Platform implementation (เช่น AmbientSoundEngine บน Android) */
    var onThemeChanged: ((BackgroundTheme) -> Unit)? = null
    var onDuckingChanged: ((Boolean) -> Unit)? = null
    var onStop: (() -> Unit)? = null

    private var currentTheme: BackgroundTheme = BackgroundTheme.DEFAULT
    private var isDucked: Boolean = false
    private var isMuted: Boolean = false

    fun setTheme(theme: BackgroundTheme) {
        if (currentTheme == theme && !isMuted) return
        currentTheme = theme
        if (!isMuted) {
            onThemeChanged?.invoke(theme)
        }
    }

    fun setDucking(ducked: Boolean) {
        if (isDucked == ducked) return
        isDucked = ducked
        onDuckingChanged?.invoke(ducked)
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            onStop?.invoke()
        } else {
            onThemeChanged?.invoke(currentTheme)
        }
    }

    fun stop() {
        currentTheme = BackgroundTheme.DEFAULT
        onStop?.invoke()
    }
}

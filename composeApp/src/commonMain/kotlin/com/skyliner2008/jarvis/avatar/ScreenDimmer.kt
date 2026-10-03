package com.skyliner2008.jarvis.avatar

import com.skyliner2008.jarvis.logDebug

/**
 * The phone's screen brightness while Avatar mode's robot sleeps (PetSelfCare: 2 minutes without a voice or a
 * touch) — the app's own window only: the system setting is never touched, and leaving the app or [restore] gives
 * the screen back its own brightness. The platform sets [handler]: a level 0..1, or null = the system's own.
 */
object ScreenDimmer {
    @kotlin.jvm.Volatile
    var handler: ((Float?) -> Unit)? = null

    @kotlin.jvm.Volatile
    var dimmed: Boolean = false
        private set

    /** Down to [level] (0.01 = 1 %). */
    fun dim(level: Float) {
        dimmed = true
        logDebug("ScreenDimmer", "🌙 screen → ${(level * 100).toInt()}%")
        handler?.invoke(level.coerceIn(0.01f, 1f))
    }

    /** As bright as before (no-op when not dimmed). */
    fun restore() {
        if (!dimmed) return
        dimmed = false
        logDebug("ScreenDimmer", "☀️ screen → its own brightness")
        handler?.invoke(null)
    }
}

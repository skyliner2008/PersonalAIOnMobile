package com.skyliner2008.jarvis.pet

import com.skyliner2008.jarvis.logDebug

/**
 * PetMotionBridge — เชื่อมโยงสัญญาณเซนเซอร์จาก PetMotionDetector (androidMain)
 * ไปยัง PetModeController / AlwaysLiveScreen (commonMain)
 */
object PetMotionBridge {
    private const val TAG = "PetMotionBridge"

    var onShake: (() -> Unit)? = null
    var onHeavyShake: (() -> Unit)? = null
    var onBoatRocking: (() -> Unit)? = null
    var onTableThump: (() -> Unit)? = null
    var onFaceDown: (() -> Unit)? = null
    var onFaceUp: (() -> Unit)? = null

    fun triggerShake() {
        logDebug(TAG, "🌀 triggerShake() dispatched to listener")
        onShake?.invoke()
    }

    fun triggerHeavyShake() {
        logDebug(TAG, "⚡ triggerHeavyShake() dispatched to listener (เขย่าแรง/รัว)")
        onHeavyShake?.invoke()
    }

    fun triggerBoatRocking() {
        logDebug(TAG, "⛵ triggerBoatRocking() dispatched to listener (เอียงไปมาเหมือนนั่งเรือ)")
        onBoatRocking?.invoke()
    }

    fun triggerTableThump() {
        logDebug(TAG, "💥 triggerTableThump() dispatched to listener (ทุบโต๊ะ/กระแทก)")
        onTableThump?.invoke()
    }

    fun triggerFaceDown() {
        logDebug(TAG, "😴 triggerFaceDown() dispatched to listener")
        onFaceDown?.invoke()
    }

    fun triggerFaceUp() {
        logDebug(TAG, "☀️ triggerFaceUp() dispatched to listener")
        onFaceUp?.invoke()
    }
}

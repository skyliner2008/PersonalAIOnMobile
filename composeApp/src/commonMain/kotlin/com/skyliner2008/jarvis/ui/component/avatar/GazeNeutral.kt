package com.skyliner2008.jarvis.ui.component.avatar

/**
 * Where "looking straight at the user" is, up and down the camera frame (-1 top .. 1 bottom).
 *
 * The camera sits above the screen and the way the phone is held moves the face a lot: held in the
 * hand at reading distance the face is in the lower part of the frame (y ≈ +0.35, level with the pet's
 * eyes), on a desk stand it is in the upper part (≈ -0.45). One fixed line pins the pet's eyes to an
 * edge in the other case (log 2026-10-01: hand-held, the old desk line kept the gaze at 0.99 — looking
 * at the floor the whole time).
 *
 * So the first face after a while away is "straight ahead"; after that the line follows the face
 * slowly ([settleMs]), so a quick move still turns the pet's eyes while a new way of holding the phone
 * becomes straight ahead again within a few seconds. Left and right need none of this: the camera is
 * in the middle of the phone.
 */
class GazeNeutral(
    private val settleMs: Long = 8_000L,
    private val awayMs: Long = 5_000L,
    initialY: Float = -0.45f
) {
    var y: Float = initialY
        private set
    private var lastSeenMs: Long? = null

    /** The face's height in the frame relative to straight ahead (negative = above, positive = below). */
    fun offsetY(rawY: Float, nowMs: Long): Float {
        val last = lastSeenMs
        if (last == null || nowMs - last > awayMs) {
            y = rawY
        } else {
            val dt = (nowMs - last).coerceIn(0L, 1_000L).toFloat()
            y += (rawY - y) * (dt / (settleMs + dt))
        }
        lastSeenMs = nowMs
        return rawY - y
    }
}

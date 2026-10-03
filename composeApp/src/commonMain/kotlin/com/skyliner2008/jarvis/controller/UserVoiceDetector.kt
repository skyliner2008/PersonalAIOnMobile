package com.skyliner2008.jarvis.controller

/**
 * Is the user talking to the AI? Decided from the mic's level, one sample per buffer (~30 a second):
 * - loud: above [threshold] and [floorFactor]× above the room's own noise (the quietest level of the last
 *   [floorWindowMs] — a fan, rain or a looping ambience raises it);
 * - talking starts after [onsetMs] of loud samples (gaps up to [gapMs]): a click, a knock or a cough is not talking;
 * - it holds [holdMs] after the last loud sample (pauses between syllables), then ends;
 * - nothing counts while the AI's voice or the robot's own sounds play: the mic hears the speaker.
 *
 * Before (App.kt): a 700 ms delay inside an effect keyed on the level. The level changes with every buffer, so each
 * change restarted the delay and it never ran out while the mic was on — the robot's "กำลังฟัง" stayed on in silence
 * until the AI spoke, and its own ALARM / CONFUSED sounds turned it on (logcat 2026-10-02 23:31:13: 57 s, 23:32:25:
 * over 2 minutes, "User speaking: true | Mic: 0%").
 *
 * Not thread-safe: one caller (the mic thread).
 */
class UserVoiceDetector(
    private val threshold: Float = 0.035f,
    private val onsetMs: Long = 250L,
    private val gapMs: Long = 300L,
    private val holdMs: Long = 700L,
    private val floorWindowMs: Long = 2_000L,
    private val floorFactor: Float = 3f,
) {
    var speaking = false
        private set

    private var burstStartMs = -1L
    private var lastLoudMs = -1L
    private val recent = ArrayDeque<Pair<Long, Float>>()

    /**
     * One level sample (0..1) at [nowMs]; [suppressed] = the AI's voice or the robot's own sound plays now.
     * Returns [speaking].
     */
    fun onLevel(level: Float, nowMs: Long, suppressed: Boolean): Boolean {
        if (suppressed) {
            reset()
            return false
        }
        while (recent.isNotEmpty() && recent.first().first < nowMs - floorWindowMs) recent.removeFirst()
        val floor = recent.minOfOrNull { it.second } ?: 0f
        recent.addLast(nowMs to level)
        val loud = level > threshold && level > floor * floorFactor
        if (loud) {
            if (burstStartMs < 0 || nowMs - lastLoudMs > gapMs) burstStartMs = nowMs
            lastLoudMs = nowMs
            if (!speaking && nowMs - burstStartMs >= onsetMs) speaking = true
        }
        if (lastLoudMs >= 0 && nowMs - lastLoudMs > holdMs) speaking = false
        if (!speaking && burstStartMs >= 0 && nowMs - lastLoudMs > gapMs) burstStartMs = -1L
        return speaking
    }

    /** The mic stopped, or something else plays: start over. */
    fun reset() {
        speaking = false
        burstStartMs = -1L
        lastLoudMs = -1L
        recent.clear()
    }
}

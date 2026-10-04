package com.skyliner2008.jarvis.automation.wake

/**
 * What a wake does when the AI cannot analyse it: no Live worker and no chat model answered.
 *
 * It used to go out as a full alert every time: a notification, a chat card and the voice, at the wake rate (up to
 * 40 an hour), although the AI skips most wakes. Now (the user's decision, 2026-10-03) it sends a quiet list of the
 * events: no voice and a silent notification, at most once per [GAP_MS] per asset. The first one says the AI is
 * down, once, until the AI answers a wake again.
 */
object WakeAiFallback {
    const val GAP_MS = 30 * 60_000L

    const val NOTICE_TH = "⚠️ ตอนนี้ AI วิเคราะห์การปลุกไม่ได้ (Live worker และโมเดลแชทไม่ตอบ) — ระหว่างนี้แจ้งเป็นรายการเหตุการณ์แบบเงียบ " +
        "ไม่มีเสียง ไม่เกิน 1 ครั้ง/30 นาที/สินทรัพย์"

    /**
     * [send]: post the quiet list now; [sayAiDown]: add [NOTICE_TH]; [sinceLastMs]: since the last quiet list of this
     * asset (null = none yet).
     */
    data class Decision(val send: Boolean, val sayAiDown: Boolean, val sinceLastMs: Long?)

    private val lastSent = mutableMapOf<String, Long>()
    private var noticeShown = false
    private val lock = Any()

    /** The AI did not answer a wake of [asset] (the symbol without its timeframe). */
    fun onAiFailed(asset: String, nowMs: Long): Decision = synchronized(lock) {
        val last = lastSent[asset]
        if (last != null && nowMs - last < GAP_MS) return Decision(send = false, sayAiDown = false, sinceLastMs = nowMs - last)
        lastSent[asset] = nowMs
        val say = !noticeShown
        noticeShown = true
        Decision(send = true, sayAiDown = say, sinceLastMs = last?.let { nowMs - it })
    }

    /** The AI answered a wake: the next failure says it is down again. */
    fun onAiAnswered() = synchronized(lock) { noticeShown = false }

    internal fun resetForTesting() = synchronized(lock) {
        lastSent.clear()
        noticeShown = false
    }
}

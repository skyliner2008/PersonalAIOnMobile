package com.skyliner2008.jarvis.pet

/**
 * The robot's life between conversations in Avatar mode. There are no care buttons since the Godot robot replaced
 * the pet screen (2026-10-02), yet its needs kept falling: hungry, it turned angry with an alarm every minute and
 * nobody could feed it (logcat 2026-10-03 00:16:37, 00:17:42). Now it sees to itself, on the user's timeline
 * (2026-10-03): when nobody talks to it or touches it
 * - 30 s: it plays by itself — or eats when hungry, takes a bath when dirty ([act]);
 * - 60 s: drowsy — it nods off, its head drops, its eyes close;
 * - 90 s: the screen's eyelids sag from top and bottom, open and shut;
 * - 120 s: shut — it sleeps, and the phone's screen goes down to 1 %.
 * The user's voice or a touch wakes it: the screen as bright as before, the eyelids open slowly, it gets up.
 *
 * Pure logic: [PetModeController] keeps the time and does what this decides.
 */
class PetSelfCare(
    val playAfterMs: Long = 30_000L,
    val drowsyAfterMs: Long = 60_000L,
    val eyelidsAfterMs: Long = 90_000L,
    val asleepAfterMs: Long = 120_000L,
) {
    enum class Act { EAT, BATHE, PLAY }

    enum class Stage { AWAKE, DROWSY, EYELIDS, ASLEEP }

    private val lastAt = mutableMapOf<Act, Long>()
    private var sleptAtMs: Long? = null

    /**
     * What it does with itself [playAfterMs] into a quiet stretch: eats when hungry, bathes when dirty, otherwise
     * plays — null when it is too tired even to play.
     */
    fun act(needs: PetNeedsState): Act? = when {
        needs.satiety < EAT_BELOW -> Act.EAT
        needs.hygiene < BATHE_BELOW -> Act.BATHE
        needs.energy >= PLAY_ENERGY -> Act.PLAY
        else -> null
    }

    /**
     * The stage after [stage] (held for [stageForMs]) once it has been quiet for [quietForMs], one step at a time:
     * a stage that started late (a scene was still on) still lasts a while before the next ([MIN_DROWSY_MS],
     * [MIN_EYELIDS_MS]). Asleep it stays asleep — only the user's voice or a touch wakes it.
     */
    fun nextStage(stage: Stage, quietForMs: Long, stageForMs: Long): Stage = when (stage) {
        Stage.AWAKE -> if (quietForMs >= drowsyAfterMs) Stage.DROWSY else Stage.AWAKE
        Stage.DROWSY -> if (quietForMs >= eyelidsAfterMs && stageForMs >= MIN_DROWSY_MS) Stage.EYELIDS else Stage.DROWSY
        Stage.EYELIDS -> if (quietForMs >= asleepAfterMs && stageForMs >= MIN_EYELIDS_MS) Stage.ASLEEP else Stage.EYELIDS
        Stage.ASLEEP -> Stage.ASLEEP
    }

    /** [act] started at [nowMs] (its own, or one the user asked for), for [recent]. */
    fun done(act: Act, nowMs: Long) {
        lastAt[act] = nowMs
    }

    /** It fell asleep at [nowMs], for [recent]. */
    fun slept(nowMs: Long) {
        sleptAtMs = nowMs
    }

    /** What it did in the last three hours, newest first ("กิน เมื่อกี้ · นอน 40 นาทีก่อน"), for the AI; "" = nothing. */
    fun recent(nowMs: Long): String =
        (lastAt.map { (act, at) -> WORDS.getValue(act) to at } + listOfNotNull(sleptAtMs?.let { "นอน" to it }))
            .filter { nowMs - it.second < RECENT_MS }
            .sortedByDescending { it.second }
            .joinToString(" · ") { (word, at) ->
                val ago = nowMs - at
                if (ago < 60_000L) "$word เมื่อกี้" else "$word ${ago / 60_000L} นาทีก่อน"
            }

    companion object {
        const val EAT_BELOW = 35f
        const val BATHE_BELOW = 35f
        /** too tired to play below this */
        const val PLAY_ENERGY = 25f
        /** drowsy at least this long before the eyelids, the eyelids at least this long before it sleeps */
        const val MIN_DROWSY_MS = 15_000L
        const val MIN_EYELIDS_MS = 20_000L
        private const val RECENT_MS = 3 * 3_600_000L
        private val WORDS = mapOf(Act.EAT to "กิน", Act.BATHE to "อาบน้ำ", Act.PLAY to "เล่น")
    }
}

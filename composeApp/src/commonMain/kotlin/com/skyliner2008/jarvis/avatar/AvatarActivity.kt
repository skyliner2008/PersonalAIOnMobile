package com.skyliner2008.jarvis.avatar

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the robot does between answers in Avatar mode (pet_godot scenes that loop until stopped):
 * - `listening` while the user talks to the AI: it leans in, sound waves leave its head and a meter follows
 *   the user's voice ([hear]);
 * - `pondering` when the user has finished, until the AI answers: it looks up, lights circle its head;
 * - `working` while a tool the AI waits on runs ([AvatarTasks.foreground]: not a job handed to the assistant —
 *   those are on the robot's task list): it holds up a card "กำลังดึงราคา… XAUUSD=X" with a spinner, and keeps
 *   it up after the tool is done until the AI starts talking (and a moment more when a result is waiting, so
 *   the result's own scene takes over from the card instead of the robot walking home first).
 *
 * A tool's scene on the stage ([ToolSceneSync]) always wins: none of these start over it. Listening comes
 * before working (the user talks over a running tool: they are heard), working before pondering.
 *
 * Not thread-safe: one caller (Avatar mode, main thread), [update] about ten times a second.
 */
class AvatarActivity(
    /** start scene `id` with `data` (null: none); the same id again with data while it plays updates it */
    private val play: (id: String, data: JsonObject?) -> Unit,
    /** end scene `id` if it is still the one playing */
    private val stop: (id: String) -> Unit,
    /** the user's voice level 0..1 while listening */
    private val hear: (level: Float) -> Unit,
    /** the user must talk this long before the robot listens (a cough, a clatter) */
    private val listenAfterMs: Long = 300L,
    /** a turn this long is a question worth pondering over */
    private val ponderAfterTalkMs: Long = 800L,
    /** pondering ends after this when the server heard no words (the mic picked up a noise) */
    private val unheardPonderMs: Long = 3_500L,
    /** pondering ends after this anyway */
    private val ponderMs: Long = 10_000L,
    /** the working card stays this long after the tool is done while the AI is still silent */
    private val afterToolMs: Long = 8_000L,
    /** the AI talks, a result waits: the card stays this long for the result's scene to take over */
    private val handoffMs: Long = 2_500L,
    private val hearEveryMs: Long = 80L,
) {
    enum class Kind(val sceneId: String) { NONE(""), LISTENING("listening"), PONDERING("pondering"), WORKING("working") }

    var current = Kind.NONE
        private set

    private var talking = false
    private var talkSinceMs = 0L
    private var talkEndMs = -1L
    private var talkLongEnough = false
    private var heardAtMs = -1L
    private var speaking = false
    private var speakSinceMs = 0L
    private var run: AvatarLiveFeed.ToolRun? = null
    private var shownRun: AvatarLiveFeed.ToolRun? = null
    private var runEndMs = -1L
    private var lastHearMs = 0L

    /** The server heard the user's words at [atMs] (its transcript grew). */
    fun onHeard(atMs: Long) {
        heardAtMs = maxOf(heardAtMs, atMs)
    }

    /**
     * The state of the session now: [userTalking] (the mic hears the user, the AI silent) at [userLevel];
     * [aiSpeaking]; the tool at work [tool] (null: none); a tool's scene is on the stage ([toolSceneUp]);
     * a tool's result waits for the AI to talk about it ([resultWaiting]).
     */
    fun update(
        nowMs: Long,
        userTalking: Boolean,
        userLevel: Float,
        aiSpeaking: Boolean,
        tool: AvatarLiveFeed.ToolRun?,
        toolSceneUp: Boolean,
        resultWaiting: Boolean,
    ) {
        if (userTalking && !talking) talkSinceMs = nowMs
        if (!userTalking && talking) {
            talkEndMs = nowMs
            talkLongEnough = nowMs - talkSinceMs >= ponderAfterTalkMs
        }
        talking = userTalking
        if (aiSpeaking && !speaking) speakSinceMs = nowMs
        speaking = aiSpeaking
        if (tool != null) {
            run = tool
            runEndMs = -1L
        } else if (run != null && runEndMs < 0) {
            runEndMs = nowMs
        }

        val want = when {
            toolSceneUp -> Kind.NONE
            talking && !aiSpeaking && nowMs - talkSinceMs >= listenAfterMs -> Kind.LISTENING
            working(nowMs, tool, resultWaiting) -> Kind.WORKING
            pondering(nowMs, tool) -> Kind.PONDERING
            else -> Kind.NONE
        }
        if (want != Kind.WORKING && tool == null && runEndMs >= 0) run = null   // that tool's card is done
        switchTo(want, nowMs)
        if (current == Kind.LISTENING && nowMs - lastHearMs >= hearEveryMs) {
            lastHearMs = nowMs
            hear(userLevel.coerceIn(0f, 1f))
        }
    }

    private fun working(nowMs: Long, tool: AvatarLiveFeed.ToolRun?, resultWaiting: Boolean): Boolean {
        if (tool != null) return true
        if (run == null || runEndMs < 0) return false
        if (!speaking) return nowMs - runEndMs < afterToolMs
        // the AI talks about the result: its scene takes over from the card ([ToolSceneSync])
        return resultWaiting && nowMs - speakSinceMs < handoffMs
    }

    private fun pondering(nowMs: Long, tool: AvatarLiveFeed.ToolRun?): Boolean {
        if (speaking || tool != null || talking || talkEndMs < 0 || !talkLongEnough) return false
        val since = nowMs - talkEndMs
        val heard = heardAtMs >= talkSinceMs
        return since < (if (heard) ponderMs else unheardPonderMs)
    }

    private fun switchTo(want: Kind, nowMs: Long) {
        if (want == current) {
            // another tool started while the card is up: the card shows it (Godot updates the playing scene)
            if (want == Kind.WORKING && run != null && run != shownRun) {
                shownRun = run
                play(Kind.WORKING.sceneId, workData(run!!))
            }
            return
        }
        val was = current
        current = want
        when (want) {
            Kind.NONE -> stop(was.sceneId)
            Kind.WORKING -> {
                shownRun = run
                play(want.sceneId, run?.let(::workData))
            }
            else -> {
                if (want == Kind.LISTENING) lastHearMs = 0L
                play(want.sceneId, null)
            }
        }
        // a pondering that ended (the AI answered, a tool started) is not taken up again for the same turn
        if (was == Kind.PONDERING && want != Kind.LISTENING) talkLongEnough = false
    }

    private fun workData(run: AvatarLiveFeed.ToolRun) = JsonObject(mapOf(
        "tool" to JsonPrimitive(run.tool),
        "args" to JsonObject(run.args.mapValues { JsonPrimitive(it.value) })
    ))

    companion object {
        /** Tools whose run shows the working card: the ones that answer (a card or a scene of their own). */
        fun showsWork(tool: String): Boolean =
            AvatarCards.wants(tool) || tool == "device_weather" || tool == "device_battery_status"
    }
}

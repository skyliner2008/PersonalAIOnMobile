package com.skyliner2008.jarvis.avatar

import com.skyliner2008.jarvis.data.PendingAnswerQueue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement

/**
 * What Avatar mode follows of the live AI session: what happens to each tool result and the AI's words with
 * when they will be heard ([ToolSceneSync]); the tool calls at work, the report queue ([AvatarTasks]: the robot's
 * task list) and the user's words as the server hears them ([AvatarActivity]). Events only reach a screen that is
 * listening — an old result is never replayed on the next visit.
 */
object AvatarLiveFeed {
    /** What happens to a tool result, in the order it happens: it comes back, the queue takes it, the app hands it over. */
    sealed interface Event

    /**
     * A finished tool call: [data] = what its info scene shows (null: none — a text tool's [text] may go on a card).
     * [later]: the AI tells it later, from the report queue — a job handed to the assistant, or a call the user
     * talked over.
     */
    data class Result(val tool: String, val data: JsonElement?, val text: String, val later: Boolean = false) : Event

    /** The report queue took a result ([tool], the result's first line) to tell later: the AI is not telling it now. */
    data class Deferred(val tool: String, val subject: String) : Event

    /** The app handed the AI a queued result to tell now: [tool] and the result's first line. */
    data class Report(val tool: String, val subject: String) : Event

    /**
     * The AI's words so far in its current answer. Its voice started (or starts) at [startMs] and says a unit of
     * `LiveProtocol.speechUnits` in [msPerUnit] ms: the text and the voice arrive seconds before the voice is heard
     * (logcat 2026-10-02 09:48: all the text in 5 s, the voice took 19 s to play).
     */
    data class Speech(val text: String, val startMs: Long, val msPerUnit: Double)

    /** A tool and its [args] (the symbol, the place …): what the working card shows. */
    data class ToolRun(val tool: String, val args: Map<String, String>)

    /**
     * A tool call at work ([id] = the call's id), for the question the user asked ([question]). [delegated]: handed to
     * the assistant, told later from the report queue; [cancelled]: the server dropped the call (the user talked over
     * it) — it runs on and its result is told later too.
     */
    data class Job(
        val id: String,
        val tool: String,
        val args: Map<String, String>,
        val question: String,
        val delegated: Boolean,
        val cancelled: Boolean,
        val startedAtMs: Long,
    )

    // one flow, so a result is always seen before the queue takes it
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _speech = MutableSharedFlow<Speech>(extraBufferCapacity = 64)
    val speech: SharedFlow<Speech> = _speech.asSharedFlow()

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    /** Every tool call at work, oldest first. */
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private val _reports = MutableStateFlow<List<PendingAnswerQueue.View>>(emptyList())
    /** The report queue: the report being told first, then what waits to be told. */
    val reports: StateFlow<List<PendingAnswerQueue.View>> = _reports.asStateFlow()

    private val _heardAtMs = MutableStateFlow(0L)
    /** When the server last heard words from the user (its transcript of the user's voice grew). */
    val heardAtMs: StateFlow<Long> = _heardAtMs.asStateFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Something the robot tells the user itself, in a speech bubble (the AI did not hear them, a fresh session …). */
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    fun notice(text: String) {
        _notices.tryEmit(text)
    }

    fun jobs(list: List<Job>) {
        _jobs.value = list
    }

    fun reports(list: List<PendingAnswerQueue.View>) {
        if (_reports.value != list) _reports.value = list
    }

    fun heard(atMs: Long) {
        _heardAtMs.value = atMs
    }

    fun result(tool: String, data: JsonElement?, text: String, later: Boolean = false) {
        _events.tryEmit(Result(tool, data, text, later))
    }

    fun deferred(tool: String, subject: String) {
        _events.tryEmit(Deferred(tool, subject))
    }

    fun report(tool: String, subject: String) {
        _events.tryEmit(Report(tool, subject))
    }

    fun speech(text: String, startMs: Long, msPerUnit: Double) {
        _speech.tryEmit(Speech(text, startMs, msPerUnit))
    }
}

package com.skyliner2008.jarvis.avatar

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonElement

/**
 * What Avatar mode follows of the live AI session ([ToolSceneSync]): what happens to each tool result, and
 * the AI's words with when they will be heard. Events only reach a screen that is listening — an old result
 * is never replayed on the next visit.
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

    // one flow, so a result is always seen before the queue takes it
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _speech = MutableSharedFlow<Speech>(extraBufferCapacity = 64)
    val speech: SharedFlow<Speech> = _speech.asSharedFlow()

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

package com.skyliner2008.jarvis.avatar

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonElement

/**
 * What Avatar mode follows of the live AI session ([ToolSceneSync]): each tool call's result as it comes
 * back, the AI's words with the moment they will be heard, and the queued results the app hands the AI to
 * tell. Events only reach a screen that is listening — an old result is never replayed on the next visit.
 */
object AvatarLiveFeed {
    /**
     * A finished tool call: [data] = what its info scene shows (null: none — a text tool's [text] may go on a card).
     * [later]: the AI tells it later, from the report queue — a job handed to the assistant, or a call the user
     * talked over.
     */
    data class Result(val tool: String, val data: JsonElement?, val text: String, val later: Boolean = false)

    /** The app handed the AI a queued result to tell now: [tool] and the result's first line. */
    data class Report(val tool: String, val subject: String)

    /**
     * The AI's words so far in its current answer, and when the last of them will be heard. The text arrives
     * seconds before the voice plays it (logcat 2026-10-01 21:37: all of it in 3.8 s, the voice took 14.3 s).
     */
    data class Speech(val text: String, val heardAtMs: Long)

    private val _results = MutableSharedFlow<Result>(extraBufferCapacity = 16)
    val results: SharedFlow<Result> = _results.asSharedFlow()

    private val _speech = MutableSharedFlow<Speech>(extraBufferCapacity = 64)
    val speech: SharedFlow<Speech> = _speech.asSharedFlow()

    private val _reports = MutableSharedFlow<Report>(extraBufferCapacity = 8)
    val reports: SharedFlow<Report> = _reports.asSharedFlow()

    fun result(tool: String, data: JsonElement?, text: String, later: Boolean = false) {
        _results.tryEmit(Result(tool, data, text, later))
    }

    fun speech(text: String, heardAtMs: Long) {
        _speech.tryEmit(Speech(text, heardAtMs))
    }

    fun report(tool: String, subject: String) {
        _reports.tryEmit(Report(tool, subject))
    }
}

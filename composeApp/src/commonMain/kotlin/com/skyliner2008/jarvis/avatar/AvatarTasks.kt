package com.skyliner2008.jarvis.avatar

import com.skyliner2008.jarvis.data.PendingAnswerQueue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The robot's task list in Avatar mode: the jobs the AI took on that the user is still waiting to hear about.
 * - running: handed to the assistant (or a call the server dropped when the user talked over it), still at work;
 * - ready: done, waiting in the report queue for a quiet moment;
 * - telling: handed to the AI to tell now.
 *
 * Questions asked in a row are answered out of order and late, and the "รับทราบ" for each is cut by the next
 * question (simulation of logcat 2026-10-02 15:26: the BTC analysis was ready at 5.8 s, told at 27 s, and its
 * acknowledgement was heard for 0.14 s). Without the list the user cannot tell a job taken on from one lost.
 * A call the AI waits on before it talks (the weather, a price) is not listed: the working card shows it.
 */
object AvatarTasks {
    enum class State(val word: String) { RUNNING("running"), READY("ready"), TELLING("telling") }

    data class Task(
        /** the tool call's id while it runs and after (one pill per job on the robot, from running to told) */
        val id: String,
        /** null: a question cut before the AI called a tool — the app asks it again */
        val tool: String?,
        val args: Map<String, String>,
        /** what the user asked (the list's label) */
        val question: String,
        val state: State,
        val sinceMs: Long,
    )

    fun board(jobs: List<AvatarLiveFeed.Job>, reports: List<PendingAnswerQueue.View>): List<Task> {
        val queued = reports.filter { it.toolName == null || AvatarActivity.showsWork(it.toolName) }.map { r ->
            Task(r.key ?: "${r.toolName ?: "ask"}@${r.addedAtMs}", r.toolName, emptyMap(), r.question,
                if (r.telling) State.TELLING else State.READY, r.addedAtMs)
        }
        val running = jobs.filter { (it.delegated || it.cancelled) && AvatarActivity.showsWork(it.tool) }
            // its result reached the queue a moment before the job's end was published
            .filterNot { j -> queued.any { it.id == j.id || (it.tool == j.tool && PendingAnswerQueue.same(it.question, j.question)) } }
            .map { Task(it.id, it.tool, it.args, it.question, State.RUNNING, it.startedAtMs) }
        return (running + queued).sortedBy { it.sinceMs }
    }

    /** The call the AI waits on before it talks (not handed over, not dropped): the robot's working card shows it. */
    fun foreground(jobs: List<AvatarLiveFeed.Job>): AvatarLiveFeed.ToolRun? =
        jobs.lastOrNull { !it.delegated && !it.cancelled && AvatarActivity.showsWork(it.tool) }
            ?.let { AvatarLiveFeed.ToolRun(it.tool, it.args) }

    /** The list as the robot takes it (pet_godot app_bridge "tasks"). */
    fun json(tasks: List<Task>): JsonArray = buildJsonArray {
        tasks.forEach { t ->
            add(buildJsonObject {
                put("id", t.id)
                t.tool?.let { put("tool", it) }
                put("args", buildJsonObject { t.args.forEach { (k, v) -> put(k, v) } })
                put("question", t.question)
                put("state", t.state.word)
            })
        }
    }
}

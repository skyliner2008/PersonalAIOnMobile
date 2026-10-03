package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.avatar.AvatarLiveFeed
import com.skyliner2008.jarvis.avatar.AvatarTasks
import com.skyliner2008.jarvis.data.PendingAnswerQueue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The robot's task list in Avatar mode: jobs taken on, waiting to be told, being told. */
class AvatarTasksTest {
    private fun job(id: String, tool: String, question: String, at: Long, delegated: Boolean = true, cancelled: Boolean = false) =
        AvatarLiveFeed.Job(id, tool, mapOf("symbol" to "BTCUSDT"), question, delegated, cancelled, at)

    private fun report(tool: String?, question: String, at: Long, telling: Boolean = false) =
        PendingAnswerQueue.View(tool, question, telling, at)

    @Test
    fun `jobs handed to the assistant are listed, a call the AI waits on is not`() {
        val board = AvatarTasks.board(
            listOf(
                job("fc_1", "trading_deep_analysis_suite", "วิเคราะห์ BTC 5 มิติ", 100),
                job("fc_2", "trading_price", "ราคาทองคำ", 200, delegated = false),
                job("fc_3", "device_avatar_emotion", "ราคาทองคำ", 210, delegated = false),
            ),
            emptyList()
        )
        assertEquals(listOf("trading_deep_analysis_suite" to AvatarTasks.State.RUNNING), board.map { it.tool to it.state })
        assertEquals("trading_price", AvatarTasks.foreground(listOf(job("fc_2", "trading_price", "", 200, delegated = false)))?.tool)
    }

    @Test
    fun `a call the server dropped runs on in the background - listed, not the working card`() {
        val jobs = listOf(job("fc_1", "device_weather", "สภาพอากาศวันนี้", 100, delegated = false, cancelled = true))
        assertEquals(AvatarTasks.State.RUNNING, AvatarTasks.board(jobs, emptyList()).single().state)
        assertNull(AvatarTasks.foreground(jobs))
    }

    @Test
    fun `the queue shows what waits and what is being told, oldest first`() {
        val board = AvatarTasks.board(
            emptyList(),
            listOf(
                report("trading_deep_analysis_suite", "วิเคราะห์ BTC 5 มิติ", 100, telling = true),
                report("trading_macro_calendar", "ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้", 200),
                report(null, "ราคาน้ำมันวันนี้", 300),
            )
        )
        assertEquals(
            listOf(AvatarTasks.State.TELLING, AvatarTasks.State.READY, AvatarTasks.State.READY),
            board.map { it.state }
        )
        assertEquals(listOf("trading_deep_analysis_suite", "trading_macro_calendar", null), board.map { it.tool })
    }

    @Test
    fun `a job whose result just reached the queue shows once`() {
        val board = AvatarTasks.board(
            listOf(job("fc_1", "trading_deep_analysis_suite", "วิเคราะห์ BTC 5 มิติ", 100)),
            listOf(report("trading_deep_analysis_suite", "วิเคราะห์ BTC 5 มิติ", 150))
        )
        assertEquals(listOf(AvatarTasks.State.READY), board.map { it.state })
    }

    @Test
    fun `the robot gets id, tool, args, question and state`() {
        val item = AvatarTasks.json(AvatarTasks.board(listOf(job("fc_1", "trading_deep_analysis_suite", "วิเคราะห์ BTC 5 มิติ", 100)), emptyList()))
            .single() as JsonObject
        assertEquals(JsonPrimitive("fc_1"), item["id"])
        assertEquals(JsonPrimitive("trading_deep_analysis_suite"), item["tool"])
        assertEquals(JsonPrimitive("วิเคราะห์ BTC 5 มิติ"), item["question"])
        assertEquals(JsonPrimitive("running"), item["state"])
        assertTrue((item["args"] as JsonObject).containsKey("symbol"))
    }
}

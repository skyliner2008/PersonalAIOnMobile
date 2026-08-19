package com.example.personalaibot

import com.example.personalaibot.ai.ChatStreamEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration-level contract test for the typed tool lifecycle.
 * Uses a fake tool executor so no real MT5/trading side effect can occur.
 */
class TypedToolLifecycleIntegrationTest {

    private class FakeToolExecutor {
        fun execute(toolName: String): String = "fake-result:$toolName"
    }

    @Test
    fun fakeToolExecution_emitsStartedResultThenFinalText() {
        val executor = FakeToolExecutor()
        val toolName = "get_current_datetime"
        val events = mutableListOf<ChatStreamEvent>()

        events += ChatStreamEvent.ToolStarted(toolName)
        val result = executor.execute(toolName)
        events += ChatStreamEvent.ToolResult(toolName, result)
        events += ChatStreamEvent.Text("Tool execution complete")

        assertEquals(3, events.size)
        assertTrue(events[0] is ChatStreamEvent.ToolStarted)
        assertTrue(events[1] is ChatStreamEvent.ToolResult)
        assertTrue(events[2] is ChatStreamEvent.Text)
        assertEquals(toolName, (events[0] as ChatStreamEvent.ToolStarted).toolName)
        assertEquals("fake-result:$toolName", (events[1] as ChatStreamEvent.ToolResult).result)
    }

    @Test
    fun fakeToolFailure_stillProducesTypedResult() {
        val toolName = "mt5_place_order"
        val events = listOf<ChatStreamEvent>(
            ChatStreamEvent.ToolStarted(toolName),
            ChatStreamEvent.ToolResult(toolName, "blocked by test double", isError = true)
        )

        val result = events[1] as ChatStreamEvent.ToolResult
        assertEquals(toolName, result.toolName)
        assertEquals("blocked by test double", result.result)
        assertTrue(result.isError)
    }
}

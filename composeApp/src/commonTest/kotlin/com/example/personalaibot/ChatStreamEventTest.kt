package com.example.personalaibot.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatStreamEventTest {

    @Test
    fun toolLifecycle_preservesStartedThenResultThenTextOrder() {
        val events = listOf(
            ChatStreamEvent.ToolStarted("get_market_data"),
            ChatStreamEvent.ToolResult("get_market_data", "XAUUSD: 3350.20"),
            ChatStreamEvent.Text("ข้อมูลตลาดพร้อมแล้ว")
        )

        assertTrue(events[0] is ChatStreamEvent.ToolStarted)
        assertTrue(events[1] is ChatStreamEvent.ToolResult)
        assertTrue(events[2] is ChatStreamEvent.Text)
        assertEquals("get_market_data", (events[0] as ChatStreamEvent.ToolStarted).toolName)
        assertEquals("get_market_data", (events[1] as ChatStreamEvent.ToolResult).toolName)
        assertEquals("ข้อมูลตลาดพร้อมแล้ว", (events[2] as ChatStreamEvent.Text).content)
    }

    @Test
    fun toolResult_canRepresentFailureWithoutChangingEventType() {
        val event = ChatStreamEvent.ToolResult("mt5_order", "Permission denied", isError = true)

        assertEquals("mt5_order", event.toolName)
        assertEquals("Permission denied", event.result)
        assertTrue(event.isError)
    }

    @Test
    fun textEvent_doesNotLookLikeToolNoise() {
        val event = ChatStreamEvent.Text("[TOOL_REQUEST] เป็นข้อความที่ผู้ใช้ถามถึง")

        assertEquals(ChatStreamEvent.Text::class, event::class)
        assertEquals("[TOOL_REQUEST] เป็นข้อความที่ผู้ใช้ถามถึง", event.content)
    }
}

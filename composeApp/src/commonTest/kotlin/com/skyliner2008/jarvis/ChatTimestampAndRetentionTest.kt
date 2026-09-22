package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.ui.components.formatMessageTime
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatTimestampAndRetentionTest {

    @Test
    fun message_defaultTimestampIsRecent() {
        val before = Clock.System.now().toEpochMilliseconds()
        val msg = Message("user", "สวัสดีครับ")
        val after = Clock.System.now().toEpochMilliseconds()

        assertTrue(msg.timestamp in before..after)
        assertEquals("user", msg.role)
        assertEquals("สวัสดีครับ", msg.content)
    }

    @Test
    fun formatMessageTime_formatsCorrectly() {
        // Test with known epoch ms
        val now = Clock.System.now().toEpochMilliseconds()
        val formatted = formatMessageTime(now)

        assertTrue(formatted.matches(Regex("^\\d{2}:\\d{2}$")), "Expected HH:mm format but got: $formatted")

        val ldt = Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.currentSystemDefault())
        val expected = "${ldt.hour.toString().padStart(2, '0')}:${ldt.minute.toString().padStart(2, '0')}"
        assertEquals(expected, formatted)
    }

    @Test
    fun formatMessageTime_handlesInvalidTimestampGracefully() {
        assertEquals("", formatMessageTime(0L))
        assertEquals("", formatMessageTime(-100L))
    }

    @Test
    fun retentionFilter_retainsUnder24HoursAndExcludesOver24Hours() {
        val now = Clock.System.now().toEpochMilliseconds()
        val oneHourAgo = now - 1 * 60 * 60 * 1000L
        val twentyThreeHoursAgo = now - 23 * 60 * 60 * 1000L
        val twentyFourHoursAndOneMinAgo = now - (24 * 60 + 1) * 60 * 1000L
        val twoDaysAgo = now - 48 * 60 * 60 * 1000L

        val msgFresh = Message("user", "Fresh message", timestamp = now)
        val msg1h = Message("model", "1 hour ago", timestamp = oneHourAgo)
        val msg23h = Message("user", "23 hours ago", timestamp = twentyThreeHoursAgo)
        val msgExpired = Message("model", "Expired 24h+ ago", timestamp = twentyFourHoursAndOneMinAgo)
        val msgOld = Message("user", "2 days ago", timestamp = twoDaysAgo)

        val allMessages = listOf(msgOld, msgExpired, msg23h, msg1h, msgFresh)

        val cutoff = now - 24 * 60 * 60 * 1000L
        val visibleMessages = allMessages.filter { it.timestamp >= cutoff }

        assertEquals(3, visibleMessages.size)
        assertTrue(visibleMessages.contains(msg23h))
        assertTrue(visibleMessages.contains(msg1h))
        assertTrue(visibleMessages.contains(msgFresh))
        assertFalse(visibleMessages.contains(msgExpired))
        assertFalse(visibleMessages.contains(msgOld))
    }
}

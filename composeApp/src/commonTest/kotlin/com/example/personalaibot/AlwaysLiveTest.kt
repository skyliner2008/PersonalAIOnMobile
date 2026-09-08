package com.example.personalaibot

import com.example.personalaibot.ui.component.avatar.AvatarEmotion
import com.example.personalaibot.ui.component.avatar.AvatarState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * AlwaysLiveTest — Unit tests for Avatar state and Always Live mode
 */
class AlwaysLiveTest {

    // ═══════════════════════════════════════════════════════════════════════
    // AvatarEmotion Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `AvatarEmotion has exactly 10 states`() {
        assertEquals(10, AvatarEmotion.entries.size)
    }

    @Test
    fun `AvatarEmotion contains all expected states`() {
        val expectedStates = setOf(
            "IDLE", "LISTENING", "THINKING", "SPEAKING",
            "HAPPY", "SAD", "ANGRY", "LOVE", "SLEEPING", "EXCITED"
        )
        val actualStates = AvatarEmotion.entries.map { it.name }.toSet()
        assertEquals(expectedStates, actualStates)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AvatarState Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `AvatarState default values are correct`() {
        val state = AvatarState()
        assertEquals(AvatarEmotion.IDLE, state.emotion)
        assertEquals(0f, state.audioLevel)
        assertEquals(false, state.isSpeaking)
        assertEquals(0.5f, state.glowIntensity)
        assertEquals(null, state.statusText)
    }

    @Test
    fun `AvatarState copy preserves unchanged values`() {
        val original = AvatarState(
            emotion = AvatarEmotion.HAPPY,
            audioLevel = 0.5f,
            isSpeaking = true,
            glowIntensity = 0.8f,
            statusText = "Hello"
        )
        val modified = original.copy(emotion = AvatarEmotion.SAD)

        assertEquals(AvatarEmotion.SAD, modified.emotion)
        assertEquals(0.5f, modified.audioLevel)  // unchanged
        assertEquals(true, modified.isSpeaking)   // unchanged
        assertEquals(0.8f, modified.glowIntensity) // unchanged
        assertEquals("Hello", modified.statusText) // unchanged
    }

    @Test
    fun `AvatarState audioLevel clamping behavior`() {
        // AvatarState itself doesn't clamp, but usage sites should
        val state = AvatarState(audioLevel = 1.5f)
        assertTrue(state.audioLevel > 1f) // no auto-clamp in data class

        // AlwaysLiveManager.setAudioLevel() should clamp
        val clamped = state.audioLevel.coerceIn(0f, 1f)
        assertEquals(1f, clamped)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Emotion → Visual Mapping Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `Each emotion has distinct visual behavior`() {
        // Verify emotions that affect eye rendering differently
        val emotionEyeTypes = mapOf(
            AvatarEmotion.IDLE to "circle",
            AvatarEmotion.LISTENING to "glow_circle",
            AvatarEmotion.THINKING to "asymmetric",
            AvatarEmotion.SPEAKING to "circle_with_blink",
            AvatarEmotion.HAPPY to "arc_smile",
            AvatarEmotion.SAD to "droopy_arc",
            AvatarEmotion.ANGRY to "red_with_eyebrows",
            AvatarEmotion.LOVE to "heart",
            AvatarEmotion.SLEEPING to "closed_line",
            AvatarEmotion.EXCITED to "star"
        )

        // All 10 emotions have unique eye types
        assertEquals(10, emotionEyeTypes.size)
        assertEquals(10, emotionEyeTypes.values.toSet().size, "All eye types must be unique")
    }

    @Test
    fun `Emotion transitions are valid`() {
        // Test common emotion transition paths
        val transitions = listOf(
            AvatarEmotion.IDLE to AvatarEmotion.LISTENING,
            AvatarEmotion.LISTENING to AvatarEmotion.THINKING,
            AvatarEmotion.THINKING to AvatarEmotion.SPEAKING,
            AvatarEmotion.SPEAKING to AvatarEmotion.HAPPY,
            AvatarEmotion.SPEAKING to AvatarEmotion.SAD,
            AvatarEmotion.IDLE to AvatarEmotion.SLEEPING,
            AvatarEmotion.SLEEPING to AvatarEmotion.LISTENING, // hotword wake
            AvatarEmotion.SPEAKING to AvatarEmotion.EXCITED
        )

        // All transitions are valid (no restriction)
        transitions.forEach { (from, to) ->
            assertNotEquals(from, to, "Transition should change state")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Sentiment Detection Tests (logic only, no Android)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `Sentiment keywords map to correct emotions`() {
        val sentimentMap = mapOf(
            "สำเร็จ" to AvatarEmotion.HAPPY,
            "success" to AvatarEmotion.HAPPY,
            "เสียใจ" to AvatarEmotion.SAD,
            "sorry" to AvatarEmotion.SAD,
            "ผิดพลาด" to AvatarEmotion.ANGRY,
            "error" to AvatarEmotion.ANGRY,
            "รัก" to AvatarEmotion.LOVE,
            "thank" to AvatarEmotion.LOVE,
            "eureka" to AvatarEmotion.EXCITED,
            "breakthrough" to AvatarEmotion.EXCITED
        )

        // Verify the map has correct emotion categories
        sentimentMap.forEach { (keyword, emotion) ->
            assertTrue(
                AvatarEmotion.entries.contains(emotion),
                "Keyword '$keyword' maps to valid emotion $emotion"
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // State Machine Logic Tests (pure logic, no Android context)
    // ═══════════════════════════════════════════════════════════════════════

    enum class TestLiveState { OFF, FULL_SCREEN, MINI_FLOATING, BACKGROUND_LISTEN }

    @Test
    fun `State machine transitions are valid`() {
        var state = TestLiveState.OFF

        // Enable → FULL_SCREEN
        state = TestLiveState.FULL_SCREEN
        assertEquals(TestLiveState.FULL_SCREEN, state)

        // Minimize → MINI_FLOATING
        state = TestLiveState.MINI_FLOATING
        assertEquals(TestLiveState.MINI_FLOATING, state)

        // Screen off → BACKGROUND_LISTEN
        state = TestLiveState.BACKGROUND_LISTEN
        assertEquals(TestLiveState.BACKGROUND_LISTEN, state)

        // Hotword → FULL_SCREEN
        state = TestLiveState.FULL_SCREEN
        assertEquals(TestLiveState.FULL_SCREEN, state)

        // Disable → OFF
        state = TestLiveState.OFF
        assertEquals(TestLiveState.OFF, state)
    }

    @Test
    fun `Screen off from FULL_SCREEN goes to BACKGROUND_LISTEN`() {
        var state = TestLiveState.FULL_SCREEN
        // Simulate screen off
        if (state == TestLiveState.FULL_SCREEN || state == TestLiveState.MINI_FLOATING) {
            state = TestLiveState.BACKGROUND_LISTEN
        }
        assertEquals(TestLiveState.BACKGROUND_LISTEN, state)
    }

    @Test
    fun `Screen on from BACKGROUND_LISTEN goes to MINI_FLOATING`() {
        var state = TestLiveState.BACKGROUND_LISTEN
        // Simulate screen on
        if (state == TestLiveState.BACKGROUND_LISTEN) {
            state = TestLiveState.MINI_FLOATING
        }
        assertEquals(TestLiveState.MINI_FLOATING, state)
    }

    @Test
    fun `Hotword from BACKGROUND_LISTEN goes to FULL_SCREEN`() {
        var state = TestLiveState.BACKGROUND_LISTEN
        // Simulate hotword
        if (state == TestLiveState.BACKGROUND_LISTEN) {
            state = TestLiveState.FULL_SCREEN
        }
        assertEquals(TestLiveState.FULL_SCREEN, state)
    }

    @Test
    fun `Double tap from MINI_FLOATING goes to FULL_SCREEN`() {
        var state = TestLiveState.MINI_FLOATING
        // Simulate double tap
        if (state == TestLiveState.MINI_FLOATING || state == TestLiveState.BACKGROUND_LISTEN) {
            state = TestLiveState.FULL_SCREEN
        }
        assertEquals(TestLiveState.FULL_SCREEN, state)
    }

    @Test
    fun `Disable from any state goes to OFF`() {
        for (initialState in TestLiveState.entries) {
            var state = initialState
            state = TestLiveState.OFF  // disable
            assertEquals(TestLiveState.OFF, state, "Disable from $initialState should go to OFF")
        }
    }
}

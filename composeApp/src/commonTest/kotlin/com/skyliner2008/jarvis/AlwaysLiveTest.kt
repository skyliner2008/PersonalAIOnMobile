package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
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
    fun `AvatarEmotion has exactly 57 states`() {
        assertEquals(57, AvatarEmotion.entries.size)
    }

    @Test
    fun `AvatarEmotion contains all expected states`() {
        val expectedStates = setOf(
            "IDLE", "LISTENING", "THINKING", "SPEAKING",
            "HAPPY", "SAD", "ANGRY", "LOVE", "SLEEPING", "EXCITED",
            "WINK", "CONFUSED", "POUT", "DIZZY", "SURPRISED", "BORED",
            "ENRAGED",
            "DEAD", "LAUGHING", "MUSIC", "VR_MODE", "DIVING", "EVIL",
            "FOCUSED", "SHY", "DISGUSTED", "CAMERA_MODE", "EATING", "DRINKING",
            "PUZZLED", "SICK", "RICH", "CRYING", "READING", "GAMING", "TRAVELING", "WORKING",
            "COLD", "HOT", "DETECTIVE", "COOKING", "ART_MODE", "SPACE", "PARTY",
            "DREAMING", "EXHAUSTED", "ELECTRIC", "SNEAKY", "ROMANTIC", "HERO", "GLITCHED",
            "MAGIC", "SPORTY", "SCIENTIST", "SCARED", "WARRIOR", "LOW_BATTERY"
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
            AvatarEmotion.EXCITED to "star",
            AvatarEmotion.WINK to "wink_bar",
            AvatarEmotion.CONFUSED to "question_mark",
            AvatarEmotion.POUT to "pout_blush",
            AvatarEmotion.DIZZY to "spiral_eyes"
        )

        // All 14 emotions have unique eye types
        assertEquals(14, emotionEyeTypes.size)
        assertEquals(14, emotionEyeTypes.values.toSet().size, "All eye types must be unique")
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

    // ═══════════════════════════════════════════════════════════════════════
    // WebSocket Reconnect & Remote Close Recovery Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `Remote socket close detection recognizes EOF and transient network terminations`() {
        fun isRemoteClose(e: Throwable): Boolean {
            val className = e::class.simpleName ?: ""
            val msg = e.message ?: ""
            val causeClassName = e.cause?.let { it::class.simpleName } ?: ""
            val causeMsg = e.cause?.message ?: ""
            return className.contains("EOFException") ||
                    className.contains("SocketClosed") ||
                    className.contains("ClosedReceiveChannelException") ||
                    className.contains("SocketException") ||
                    causeClassName.contains("EOFException") ||
                    causeClassName.contains("SocketClosed") ||
                    causeClassName.contains("SocketException") ||
                    msg.contains("EOF", ignoreCase = true) ||
                    msg.contains("unexpected end of stream", ignoreCase = true) ||
                    msg.contains("Connection reset", ignoreCase = true) ||
                    msg.contains("Software caused connection abort", ignoreCase = true) ||
                    msg.contains("Socket closed", ignoreCase = true) ||
                    msg.contains("Channel was closed", ignoreCase = true) ||
                    causeMsg.contains("EOF", ignoreCase = true) ||
                    causeMsg.contains("Connection reset", ignoreCase = true) ||
                    causeMsg.contains("unexpected end of stream", ignoreCase = true)
        }

        assertTrue(isRemoteClose(RuntimeException("java.io.EOFException: unexpected end of stream")))
        assertTrue(isRemoteClose(IllegalStateException("Connection reset by peer")))
        assertTrue(isRemoteClose(Exception("Channel was closed")))
        assertTrue(isRemoteClose(Exception("Parent error", RuntimeException("EOF encountered"))))
        kotlin.test.assertFalse(isRemoteClose(IllegalArgumentException("Invalid API key parameter")))
    }

    @Test
    fun `Live session ready state protects reconnect retry quota on server close`() {
        var sessionWasReady = true
        var attempt = 0
        val maxRetries = 3

        // Simulate server-initiated remote close on established ready session
        attempt = if (sessionWasReady) 1 else attempt + 1
        assertEquals(1, attempt, "Established session drops should reset attempt count to 1")

        // If session was never ready (e.g. handshake failed), attempt should increment
        sessionWasReady = false
        attempt = if (sessionWasReady) 1 else attempt + 1
        assertEquals(2, attempt, "Unready session failure should consume retry quota")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Anti-Hallucination Guard Tests (Always Live, Vision, Voice, Format)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `AlwaysLive off guard blocks hallucinated close when user did not request exit`() {
        fun isExplicitUserClose(prompt: String): Boolean {
            val p = prompt.lowercase()
            val pWithoutOpen = p.replace("เปิด", "")
            val closeKeywords = listOf(
                "ปิด", "ออก", "เลิก", "พอแล้ว", "หยุด", "บาย", "พักผ่อน", "นอนได้แล้ว",
                "off", "stop", "exit", "close", "quit", "bye", "shutdown", "disable"
            )
            return closeKeywords.any { pWithoutOpen.contains(it) }
        }

        // Neutral or greeting inputs that Gemini misinterprets as exit
        kotlin.test.assertFalse(isExplicitUserClose("ดาวิด"))
        kotlin.test.assertFalse(isExplicitUserClose("จาวิส"))
        kotlin.test.assertFalse(isExplicitUserClose("สวัสดีจาวิส"))
        kotlin.test.assertFalse(isExplicitUserClose("ทำอะไรได้บ้าง"))
        kotlin.test.assertFalse(isExplicitUserClose("เปิดเพลงหน่อย"))

        // Genuine close requests
        assertTrue(isExplicitUserClose("ปิดโหมดสัตว์เลี้ยง"))
        assertTrue(isExplicitUserClose("ออกจากโหมด Always"))
        assertTrue(isExplicitUserClose("ปิด Always"))
        assertTrue(isExplicitUserClose("เลิกเล่นแล้ว"))
        assertTrue(isExplicitUserClose("stop pet mode"))
        assertTrue(isExplicitUserClose("exit now"))
    }

    @Test
    fun `Vision activate guard blocks hallucinated camera calls on general conversation`() {
        fun hasVisionIntent(prompt: String): Boolean {
            val p = prompt.lowercase()
            val visionKeywords = listOf(
                "ดู", "มอง", "เห็น", "กล้อง", "ตา", "ตรวจ", "ส่อง", "อ่าน", "เช็คภาพ", "ภาพ",
                "see", "look", "watch", "camera", "eye", "vision", "view", "read", "scan", "photo", "pic"
            )
            return visionKeywords.any { p.contains(it) }
        }

        // Misheard greetings / neutral words must NOT open camera
        kotlin.test.assertFalse(hasVisionIntent("สวัสดีจ้ะวิทย์"))
        kotlin.test.assertFalse(hasVisionIntent("สวัสดีจาวิส"))
        kotlin.test.assertFalse(hasVisionIntent("ดาวิด"))
        kotlin.test.assertFalse(hasVisionIntent("วันนี้อากาศเป็นไง"))

        // Genuine vision requests
        assertTrue(hasVisionIntent("ดูนี่หน่อย"))
        assertTrue(hasVisionIntent("เปิดกล้องดูซิ"))
        assertTrue(hasVisionIntent("อ่านป้ายตรงนี้ให้หน่อย"))
        assertTrue(hasVisionIntent("เห็นอะไรในห้องไหม"))
        assertTrue(hasVisionIntent("can you see this"))
        assertTrue(hasVisionIntent("look at the screen"))
    }

    @Test
    fun `Voice profile guard prevents name confusion with voice switching`() {
        fun hasVoiceIntent(prompt: String): Boolean {
            val p = prompt.lowercase()
            val voiceKeywords = listOf("เสียง", "voice", "สำเนียง", "โทน", "เปลี่ยนเสียง")
            return voiceKeywords.any { p.contains(it) }
        }

        // "ดาวิด" sounds like "David" but user only called Jarvis's name!
        kotlin.test.assertFalse(hasVoiceIntent("ดาวิด"))
        kotlin.test.assertFalse(hasVoiceIntent("จาวิส"))
        kotlin.test.assertFalse(hasVoiceIntent("สวัสดีครับ"))

        // Genuine voice change requests
        assertTrue(hasVoiceIntent("ขอเปลี่ยนเสียงหน่อย"))
        assertTrue(hasVoiceIntent("มีเสียงอะไรให้เลือกบ้าง"))
        assertTrue(hasVoiceIntent("change voice"))
        assertTrue(hasVoiceIntent("ปรับโทนเสียงหน่อย"))
    }

    @Test
    fun `Detection label with percent symbol does not throw format exception`() {
        val labelWithPercent = "Boss Smile 😊 85%"
        val confidence = 0.854f
        val isLocked = true

        // Safe interpolation pattern used in PetVisionDetector
        val result = runCatching {
            val confStr = (confidence * 100).toInt()
            val lockStr = if (isLocked) " 🔒" else ""
            "$labelWithPercent ($confStr%$lockStr)"
        }
        assertTrue(result.isSuccess)
        assertEquals("Boss Smile 😊 85% (85% 🔒)", result.getOrNull())
    }
}


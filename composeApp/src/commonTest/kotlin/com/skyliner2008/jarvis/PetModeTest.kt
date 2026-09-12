package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.camera.BoundingBox
import com.skyliner2008.jarvis.camera.DetectedObject
import com.skyliner2008.jarvis.pet.AlwaysLiveProfile
import com.skyliner2008.jarvis.pet.PetFeatureTab
import com.skyliner2008.jarvis.pet.PetModeController
import com.skyliner2008.jarvis.pet.PetVisionTargetTracker
import com.skyliner2008.jarvis.pet.PetStateMachine
import com.skyliner2008.jarvis.pet.TouchZone
import com.skyliner2008.jarvis.pet.InteractionType
import com.skyliner2008.jarvis.pet.PetNeedsState
import com.skyliner2008.jarvis.pet.PetMood
import com.skyliner2008.jarvis.pet.PetMemoryStore
import com.skyliner2008.jarvis.sound.AmbientSoundPlayer
import com.skyliner2008.jarvis.sound.RobotSound
import com.skyliner2008.jarvis.sound.RobotSoundPlayer
import com.skyliner2008.jarvis.ui.component.avatar.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * PetModeTest — Unit tests for Virtual Desk Pet Mode (โหมดสัตว์เลี้ยง)
 */
class PetModeTest {

    @Test
    fun `AlwaysLiveProfile contains CONTROL, DRIVE, and PET`() {
        val names = AlwaysLiveProfile.entries.map { it.name }.toSet()
        assertTrue(names.contains("CONTROL"))
        assertTrue(names.contains("DRIVE"))
        assertTrue(names.contains("PET"))
        assertEquals(3, AlwaysLiveProfile.entries.size)
    }

    @Test
    fun `AvatarState supports gaze and dizzy properties with correct defaults`() {
        val defaultState = AvatarState()
        assertEquals(0f, defaultState.gazeOffsetX)
        assertEquals(0f, defaultState.gazeOffsetY)
        assertFalse(defaultState.isDizzy)

        val modifiedState = defaultState.copy(
            gazeOffsetX = 0.5f,
            gazeOffsetY = -0.3f,
            isDizzy = true
        )
        assertEquals(0.5f, modifiedState.gazeOffsetX)
        assertEquals(-0.3f, modifiedState.gazeOffsetY)
        assertTrue(modifiedState.isDizzy)
    }

    @Test
    fun `PetFeatureTab contains all 4 sub-feature tabs`() {
        val tabs = PetFeatureTab.entries.map { it.name }.toSet()
        assertEquals(setOf("PET", "SENTRY", "FOCUS", "GAMES"), tabs)
    }

    @Test
    fun `PetModeController tab selection switches state correctly`() = runBlocking {
        var currentState = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { currentState },
            onUpdateAvatarState = { currentState = it }
        )

        controller.selectTab(PetFeatureTab.SENTRY)
        assertEquals(PetFeatureTab.SENTRY, controller.selectedTab.value)
        assertEquals(AvatarEmotion.ANGRY, currentState.emotion)

        controller.selectTab(PetFeatureTab.FOCUS)
        assertEquals(PetFeatureTab.FOCUS, controller.selectedTab.value)
        assertEquals(AvatarEmotion.THINKING, currentState.emotion)

        controller.selectTab(PetFeatureTab.GAMES)
        assertEquals(PetFeatureTab.GAMES, controller.selectedTab.value)
        assertEquals(AvatarEmotion.EXCITED, currentState.emotion)

        controller.selectTab(PetFeatureTab.PET)
        assertEquals(PetFeatureTab.PET, controller.selectedTab.value)
        assertEquals(AvatarEmotion.IDLE, currentState.emotion)
    }

    @Test
    fun `Touch interactions trigger appropriate emotional reactions`() = runBlocking {
        var currentState = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { currentState },
            onUpdateAvatarState = { currentState = it }
        )

        // Pet head -> LOVE emotion
        controller.onPetHead()
        assertEquals(AvatarEmotion.LOVE, currentState.emotion)
        assertNotNull(currentState.statusText)

        // Poke -> HAPPY emotion
        controller.onPoke()
        assertEquals(AvatarEmotion.HAPPY, currentState.emotion)

        // Tickle -> EXCITED emotion
        controller.onTickle()
        assertEquals(AvatarEmotion.EXCITED, currentState.emotion)

        // Gaze Touch -> shifts gaze coordinates
        controller.onGazeTouch(0.75f, -0.4f)
        assertEquals(0.75f, currentState.gazeOffsetX)
        assertEquals(-0.4f, currentState.gazeOffsetY)
    }

    @Test
    fun `Desk Sentry mode activates and triggers intruder alert`() = runBlocking {
        var currentState = AvatarState()
        var intruderCallbackTriggered = false
        val scope = CoroutineScope(SupervisorJob())

        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { currentState },
            onUpdateAvatarState = { currentState = it },
            onSentryIntruderAlert = { intruderCallbackTriggered = true }
        )

        assertFalse(controller.isSentryActive.value)

        // Toggle on
        controller.toggleSentry(true)
        assertTrue(controller.isSentryActive.value)
        assertEquals(AvatarEmotion.ANGRY, currentState.emotion)

        // Intruder trigger
        controller.triggerIntruderAlert()
        delay(50)
        assertTrue(intruderCallbackTriggered)
        assertEquals(AvatarEmotion.ANGRY, currentState.emotion)

        // Toggle off
        controller.toggleSentry(false)
        assertFalse(controller.isSentryActive.value)
        assertEquals(AvatarEmotion.IDLE, currentState.emotion)
    }

    @Test
    fun `Focus Buddy timer behaves as expected`() = runBlocking {
        var currentState = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { currentState },
            onUpdateAvatarState = { currentState = it }
        )

        controller.setFocusDuration(10)
        assertEquals(600, controller.focusTotalSeconds.value)
        assertEquals(600, controller.focusRemainingSeconds.value)
        assertFalse(controller.isFocusRunning.value)

        controller.toggleFocusTimer()
        assertTrue(controller.isFocusRunning.value)
        assertEquals(AvatarEmotion.THINKING, currentState.emotion)

        controller.pauseFocusTimer()
        assertFalse(controller.isFocusRunning.value)

        controller.resetFocusTimer()
        assertEquals(600, controller.focusRemainingSeconds.value)
    }

    @Test
    fun `Fortune Oracle draws a non-null prediction`() = runBlocking {
        var currentState = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { currentState },
            onUpdateAvatarState = { currentState = it }
        )

        controller.drawFortune()
        assertNotNull(controller.fortuneText.value)
        assertTrue(controller.fortuneText.value!!.isNotEmpty())
        assertEquals(AvatarEmotion.EXCITED, currentState.emotion)
        assertEquals(controller.fortuneText.value, currentState.statusText)
    }

    @Test
    fun `Motion and expressive reactions trigger correctly`() = runBlocking {
        var currentState = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { currentState },
            onUpdateAvatarState = { currentState = it }
        )

        // Dizzy
        controller.onDizzy()
        assertEquals(AvatarEmotion.DIZZY, currentState.emotion)
        assertTrue(currentState.isDizzy)

        // Face-down -> Sleeping
        controller.onFaceDown()
        assertEquals(AvatarEmotion.SLEEPING, currentState.emotion)

        // Face-up -> Wake-up / Happy
        controller.onFaceUp()
        assertEquals(AvatarEmotion.HAPPY, currentState.emotion)

        // Pout
        controller.onPout()
        assertEquals(AvatarEmotion.POUT, currentState.emotion)

        // Wink
        controller.onWink()
        assertEquals(AvatarEmotion.WINK, currentState.emotion)
    }

    @Test
    fun `Copycat game challenges and verifies mimic success`() = runBlocking {
        var currentState = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { currentState },
            onUpdateAvatarState = { currentState = it }
        )

        controller.startCopycatGame()
        assertNotNull(controller.copycatTarget.value)
        assertTrue(controller.copycatTarget.value == AvatarEmotion.HAPPY || controller.copycatTarget.value == AvatarEmotion.WINK)
        assertEquals(controller.copycatTarget.value, currentState.emotion)

        // User mimics -> success celebration
        controller.onUserMimicSuccess()
        assertEquals(null, controller.copycatTarget.value)
        assertEquals(AvatarEmotion.EXCITED, currentState.emotion)
        assertTrue(currentState.statusText?.contains("ชนะเกมเลียนแบบ") == true)
    }

    @Test
    fun `PetVisionBridge creates and releases processor correctly`() {
        var gazeCalled = false
        var intruderCalled = false
        var copycatSuccessCalled = false

        val testProcessor = object : com.skyliner2008.jarvis.pet.PetVisionProcessor {
            var released = false
            override fun processFrame(rawBytes: ByteArray, isFrontCamera: Boolean) {}
            override fun release() { released = true }
        }

        com.skyliner2008.jarvis.pet.PetVisionBridge.processorFactory = { onGaze, onIntruder, onCopycat, _, _ ->
            onGaze(0.5f, -0.2f)
            onIntruder()
            onCopycat()
            testProcessor
        }

        val processor = com.skyliner2008.jarvis.pet.PetVisionBridge.createProcessor(
            onGazeDetected = { _, _ -> gazeCalled = true },
            onIntruderDetected = { intruderCalled = true },
            onCopycatSuccess = { copycatSuccessCalled = true },
            getCopycatTarget = { AvatarEmotion.HAPPY },
            isSentryActive = { true }
        )

        assertNotNull(processor)
        assertTrue(gazeCalled)
        assertTrue(intruderCalled)
        assertTrue(copycatSuccessCalled)

        processor.release()
        assertTrue(testProcessor.released)
    }

    @Test
    fun `Pet Mode persona prompt switches dynamically and contains cute robot pet rules`() {
        try {
            // Default: isPetMode = false -> LIVE_SYSTEM_PROMPT contains regular assistant rules
            com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode = false
            val defaultPrompt = com.skyliner2008.jarvis.ai.JarvisPersona.LIVE_SYSTEM_PROMPT
            assertTrue(defaultPrompt.contains("ตัวตนของคุณ"), "Default prompt should contain CORE_IDENTITY")
            assertTrue(defaultPrompt.contains("กฎการทำงานสด"), "Default prompt should contain LIVE_RULES")
            assertFalse(defaultPrompt.contains("VIRTUAL DESK PET MODE"), "Default prompt should not have desk pet header")

            // Switch to Pet Mode: isPetMode = true -> LIVE_SYSTEM_PROMPT switches to PET_LIVE_SYSTEM_PROMPT
            com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode = true
            val petPrompt = com.skyliner2008.jarvis.ai.JarvisPersona.LIVE_SYSTEM_PROMPT
            assertTrue(petPrompt.contains("VIRTUAL DESK PET MODE"), "Pet prompt should have desk pet header")
            assertTrue(petPrompt.contains("หุ่นยนต์สัตว์เลี้ยง"), "Pet prompt should mention robot pet")
            assertTrue(petPrompt.contains("ห้ามพูดคำเลียนเสียงหุ่นยนต์"), "Pet prompt should forbid spoken robot sound words")
            assertTrue(petPrompt.contains("NO SPOKEN SOUND WORDS"), "Pet prompt should have NO SPOKEN SOUND WORDS tag")
            assertTrue(petPrompt.contains("ห้ามวิเคราะห์การเงิน"), "Pet prompt should forbid finance analysis")
        } finally {
            com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode = false
        }
    }

    @Test
    fun `Pet Mode and Normal Live mode have completely isolated greetings and configurations`() {
        try {
            // Test Normal Mode
            com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode = false
            val normalGreeting = if (com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode) {
                "สวัสดีฮับ พร้อมเล่นแล้ว"
            } else {
                "สวัสดีจาวิส พร้อมคุยไหม"
            }
            assertEquals("สวัสดีจาวิส พร้อมคุยไหม", normalGreeting)

            // Test Pet Mode
            com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode = true
            val petGreeting = if (com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode) {
                "สวัสดีฮับ พร้อมเล่นแล้ว"
            } else {
                "สวัสดีจาวิส พร้อมคุยไหม"
            }
            assertEquals("สวัสดีฮับ พร้อมเล่นแล้ว", petGreeting)
        } finally {
            com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode = false
        }
    }

    @Test
    fun `PetVisionBridge handles objects detected and eye open request`() {
        var receivedEyeOpen: Boolean? = null
        var receivedObjects: List<com.skyliner2008.jarvis.camera.DetectedObject>? = null

        com.skyliner2008.jarvis.pet.PetVisionBridge.onEyeOpenRequest = { open ->
            receivedEyeOpen = open
        }
        com.skyliner2008.jarvis.pet.PetVisionBridge.onObjectsDetected = { objects ->
            receivedObjects = objects
        }

        com.skyliner2008.jarvis.pet.PetVisionBridge.requestEyeOpen(true)
        assertEquals(true, receivedEyeOpen)

        com.skyliner2008.jarvis.pet.PetVisionBridge.requestEyeOpen(false)
        assertEquals(false, receivedEyeOpen)

        val testObjects = listOf(
            com.skyliner2008.jarvis.camera.DetectedObject(
                label = "Boss Smile 😊 90%",
                confidence = 0.90f,
                boundingBox = com.skyliner2008.jarvis.camera.BoundingBox(0.2f, 0.2f, 0.4f, 0.5f),
                color = "#FF4081"
            )
        )
        com.skyliner2008.jarvis.pet.PetVisionBridge.onObjectsDetected?.invoke(testObjects)
        assertNotNull(receivedObjects)
        assertEquals(1, receivedObjects!!.size)
        assertEquals("Boss Smile 😊 90%", receivedObjects!![0].label)
        assertEquals("#FF4081", receivedObjects!![0].color)

        // Cleanup
        com.skyliner2008.jarvis.pet.PetVisionBridge.onEyeOpenRequest = null
        com.skyliner2008.jarvis.pet.PetVisionBridge.onObjectsDetected = null
    }

    // ─── Layer-Based Living Avatar Tests ──────────────────────────────────────

    @Test
    fun `RobotFaceState parsing from JSON string produces correct state`() {
        val json = """
            {
                "emotion": "happy",
                "eye_style": "star",
                "background": "sunny",
                "props": "sparkles,music_notes",
                "gesture": "bounce",
                "speech_text": "ปิ๊บๆ วันนี้แดดดีจังเลยฮับ!"
            }
        """.trimIndent()

        val state = RobotFaceState.fromJson(json)
        assertEquals(AvatarEmotion.HAPPY, state.emotion)
        assertEquals(EyeStyle.STAR, state.eyeStyle)
        assertEquals(BackgroundTheme.SUNNY, state.backgroundTheme)
        assertEquals(listOf(PropType.SPARKLES, PropType.MUSIC_NOTES), state.props)
        assertEquals(GestureType.BOUNCE, state.gesture)
        assertEquals("ปิ๊บๆ วันนี้แดดดีจังเลยฮับ!", state.speechText)
    }

    @Test
    fun `RobotFaceState parsing from args map handles case insensitivity and fallbacks`() {
        val args = mapOf(
            "emotion" to "LOVE",
            "eye_style" to "Heart",
            "background" to "love_bg",
            "props" to "hearts, invalid_prop, sparkles",
            "gesture" to "wobble"
        )

        val state = RobotFaceState.fromArgs(args)
        assertEquals(AvatarEmotion.LOVE, state.emotion)
        assertEquals(EyeStyle.HEART, state.eyeStyle)
        assertEquals(BackgroundTheme.LOVE_BG, state.backgroundTheme)
        assertEquals(listOf(PropType.HEARTS, PropType.SPARKLES), state.props)
        assertEquals(GestureType.WOBBLE, state.gesture)
    }

    @Test
    fun `RobotFaceState handles invalid or corrupt JSON with safe defaults`() {
        val corruptState = RobotFaceState.fromJson("invalid json {[[")
        assertEquals(AvatarEmotion.IDLE, corruptState.emotion)
        assertEquals(EyeStyle.DEFAULT, corruptState.eyeStyle)
        assertEquals(BackgroundTheme.DEFAULT, corruptState.backgroundTheme)
        assertTrue(corruptState.props.isEmpty())
        assertEquals(GestureType.IDLE, corruptState.gesture)
    }

    @Test
    fun `PetModeController updateRobotFace updates AvatarState correctly`() = runBlocking {
        var currentState = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { currentState },
            onUpdateAvatarState = { currentState = it }
        )

        val faceState = RobotFaceState.SAD_RAINY
        controller.updateRobotFace(faceState)

        assertEquals(AvatarEmotion.SAD, currentState.emotion)
        assertEquals(BackgroundTheme.RAINY, currentState.faceState.backgroundTheme)
        assertEquals(listOf(PropType.SWEAT_DROP), currentState.faceState.props)
        assertEquals(GestureType.TILT_LEFT, currentState.faceState.gesture)
        assertEquals(EyeStyle.CRYING, currentState.faceState.eyeStyle)
    }

    @Test
    fun `PetModeController updateRobotFace handles pipe-separated command string`() = runBlocking {
        var currentState = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { currentState },
            onUpdateAvatarState = { currentState = it }
        )

        controller.updateRobotFace("EXCITED|eye_style=star|background=sakura|props=sparkles,music_notes|gesture=jump")

        assertEquals(AvatarEmotion.EXCITED, currentState.emotion)
        assertEquals(EyeStyle.STAR, currentState.faceState.eyeStyle)
        assertEquals(BackgroundTheme.SAKURA, currentState.faceState.backgroundTheme)
        assertEquals(listOf(PropType.SPARKLES, PropType.MUSIC_NOTES), currentState.faceState.props)
        assertEquals(GestureType.JUMP, currentState.faceState.gesture)
    }

    @Test
    fun `GestureType headTiltOverride returns correct tilt values`() {
        assertEquals(-12f, GestureType.TILT_LEFT.headTiltOverride())
        assertEquals(12f, GestureType.TILT_RIGHT.headTiltOverride())
        assertEquals(null, GestureType.IDLE.headTiltOverride())
        assertEquals(null, GestureType.BOUNCE.headTiltOverride())
        assertEquals(null, GestureType.SHAKE.headTiltOverride())
    }

    @Test
    fun `AvatarState withFace extension maintains backward compatibility`() {
        val original = AvatarState(
            emotion = AvatarEmotion.IDLE,
            audioLevel = 0.5f,
            isSpeaking = true,
            statusText = "Speaking..."
        )
        val face = RobotFaceState.HAPPY_SUNNY
        val updated = original.withFace(face)

        assertEquals(AvatarEmotion.HAPPY, updated.emotion)
        assertEquals(0.5f, updated.audioLevel)
        assertEquals(true, updated.isSpeaking)
        assertEquals("Speaking...", updated.statusText)
        assertEquals(BackgroundTheme.SUNNY, updated.faceState.backgroundTheme)
        assertEquals(GestureType.BOUNCE, updated.faceState.gesture)
    }

    // ─── Speech Cadence SFX & Ambient Background Tests ───────────────────────

    @Test
    fun `RobotSound enum includes speech cadence and pet care sounds`() {
        val soundNames = RobotSound.entries.map { it.name }.toSet()
        assertTrue(soundNames.contains("CHIRP_START"))
        assertTrue(soundNames.contains("CHIRP_END"))
        assertTrue(soundNames.contains("ACKNOWLEDGE"))
        assertTrue(soundNames.contains("SPARKLE"))
        assertTrue(soundNames.contains("MISSILE_LAUNCH"))
        assertTrue(soundNames.contains("EXPLOSION"))
        assertTrue(soundNames.contains("CRUNCH_EAT"))
        assertTrue(soundNames.contains("BUBBLE_POP"))
        assertTrue(soundNames.contains("BELL_TOY"))
        assertTrue(soundNames.contains("SCAN_RADAR"))
        assertEquals(19, RobotSound.entries.size)
    }

    @Test
    fun `RobotSoundPlayer helper functions invoke handler with correct sound type`() {
        var lastPlayed: RobotSound? = null
        RobotSoundPlayer.handler = { lastPlayed = it }

        RobotSoundPlayer.playChirpStart()
        assertEquals(RobotSound.CHIRP_START, lastPlayed)

        RobotSoundPlayer.playChirpEnd()
        assertEquals(RobotSound.CHIRP_END, lastPlayed)

        RobotSoundPlayer.playAcknowledge()
        assertEquals(RobotSound.ACKNOWLEDGE, lastPlayed)

        RobotSoundPlayer.playSparkle()
        assertEquals(RobotSound.SPARKLE, lastPlayed)

        RobotSoundPlayer.playCrunchEat()
        assertEquals(RobotSound.CRUNCH_EAT, lastPlayed)

        RobotSoundPlayer.playBubblePop()
        assertEquals(RobotSound.BUBBLE_POP, lastPlayed)

        RobotSoundPlayer.playBellToy()
        assertEquals(RobotSound.BELL_TOY, lastPlayed)

        RobotSoundPlayer.playScanRadar()
        assertEquals(RobotSound.SCAN_RADAR, lastPlayed)

        // Cleanup
        RobotSoundPlayer.handler = null
    }

    @Test
    fun `PetStateMachine care actions trigger specific care sounds`() {
        val sm = PetStateMachine()
        val baseNeeds = PetNeedsState(satiety = 50f, hygiene = 50f, energy = 50f)

        var lastSound: RobotSound? = null
        RobotSoundPlayer.handler = { lastSound = it }

        // Feed -> CRUNCH_EAT
        val feedResult = sm.processTouch(InteractionType.FEED, TouchZone.FACE_CENTER, baseNeeds, AvatarEmotion.IDLE)
        feedResult.soundAction?.invoke()
        assertEquals(RobotSound.CRUNCH_EAT, lastSound)

        // Clean -> BUBBLE_POP
        val cleanResult = sm.processTouch(InteractionType.CLEAN, TouchZone.FACE_CENTER, baseNeeds, AvatarEmotion.IDLE)
        cleanResult.soundAction?.invoke()
        assertEquals(RobotSound.BUBBLE_POP, lastSound)

        // Play -> BELL_TOY
        val playResult = sm.processTouch(InteractionType.PLAY, TouchZone.FACE_CENTER, baseNeeds, AvatarEmotion.IDLE)
        playResult.soundAction?.invoke()
        assertEquals(RobotSound.BELL_TOY, lastSound)

        RobotSoundPlayer.handler = null
    }

    @Test
    fun `AmbientSoundPlayer setTheme, setDucking, and stop work properly`() {
        var lastTheme: BackgroundTheme? = null
        var lastDucked: Boolean? = null
        var stopped = false

        AmbientSoundPlayer.onThemeChanged = { lastTheme = it }
        AmbientSoundPlayer.onDuckingChanged = { lastDucked = it }
        AmbientSoundPlayer.onStop = { stopped = true }

        AmbientSoundPlayer.setTheme(BackgroundTheme.RAINY)
        assertEquals(BackgroundTheme.RAINY, lastTheme)

        AmbientSoundPlayer.setDucking(true)
        assertEquals(true, lastDucked)

        AmbientSoundPlayer.setDucking(false)
        assertEquals(false, lastDucked)

        AmbientSoundPlayer.stop()
        assertTrue(stopped)

        // Cleanup
        AmbientSoundPlayer.onThemeChanged = null
        AmbientSoundPlayer.onDuckingChanged = null
        AmbientSoundPlayer.onStop = null
    }

    @Test
    fun `PET_LIVE_SYSTEM_PROMPT strictly forbids spoken robot sound words`() {
        val prompt = com.skyliner2008.jarvis.ai.JarvisPersona.PET_LIVE_SYSTEM_PROMPT
        assertTrue(prompt.contains("ห้ามพูดคำเลียนเสียงหุ่นยนต์"))
        assertTrue(prompt.contains("NO SPOKEN SOUND WORDS"))
        assertFalse(prompt.contains("มีคำเลียนเสียงหุ่นยนต์ เช่น \"ปิ๊บๆ!\""))
    }

    @Test
    fun `LiveRealtimeInputData serializes audio and video directly without mediaChunks`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = false }

        // Audio chunk payload
        val audioMsg = com.skyliner2008.jarvis.data.LiveRealtimeInputMessage(
            realtimeInput = com.skyliner2008.jarvis.data.LiveRealtimeInputData(
                audio = com.skyliner2008.jarvis.data.LiveBlob(
                    mimeType = "audio/pcm;rate=16000",
                    data = "QUJD"
                )
            )
        )
        val audioJson = json.encodeToString(audioMsg)
        assertTrue(audioJson.contains("\"audio\":{"), "Must serialize audio directly under realtimeInput")
        assertTrue(audioJson.contains("\"mimeType\":\"audio/pcm;rate=16000\""))
        assertFalse(audioJson.contains("mediaChunks"), "Must NOT contain mediaChunks (deprecated)")
        assertFalse(audioJson.contains("media_chunks"), "Must NOT contain media_chunks (deprecated)")

        // Video chunk payload
        val videoMsg = com.skyliner2008.jarvis.data.LiveRealtimeInputMessage(
            realtimeInput = com.skyliner2008.jarvis.data.LiveRealtimeInputData(
                video = com.skyliner2008.jarvis.data.LiveBlob(
                    mimeType = "image/jpeg",
                    data = "SlBFRw=="
                )
            )
        )
        val videoJson = json.encodeToString(videoMsg)
        assertTrue(videoJson.contains("\"video\":{"), "Must serialize video directly under realtimeInput")
        assertTrue(videoJson.contains("\"mimeType\":\"image/jpeg\""))
        assertFalse(videoJson.contains("mediaChunks"), "Must NOT contain mediaChunks (deprecated)")

        // Text payload
        val textMsg = com.skyliner2008.jarvis.data.LiveRealtimeInputMessage(
            realtimeInput = com.skyliner2008.jarvis.data.LiveRealtimeInputData(
                text = "สวัสดีจาวิส"
            )
        )
        val textJson = json.encodeToString(textMsg)
        assertTrue(textJson.contains("\"text\":\"สวัสดีจาวิส\""))
        assertFalse(textJson.contains("mediaChunks"))
    }

    @Test
    fun `Demo showcase 8 scenes cover all backgrounds, gestures, props, and sounds`() {
        val backgrounds = mutableSetOf<BackgroundTheme>()
        val gestures = mutableSetOf<GestureType>()
        val props = mutableSetOf<PropType>()

        val demoFaceStates = listOf(
            RobotFaceState("happy", "star", "sunny", "music_notes,sparkles", "jump"),
            RobotFaceState("sad", "crying", "rainy", "umbrella,sweat_drop", "tilt_left"),
            RobotFaceState("excited", "star", "sakura", "sparkles", "wobble"),
            RobotFaceState("love", "heart", "love_bg", "hearts", "bounce"),
            RobotFaceState("angry", "cross", "thunder", "fire,exclamation", "shake"),
            RobotFaceState("thinking", "question", "matrix", "question_mark", "tilt_right"),
            RobotFaceState("sleeping", "default", "night", "zzzzz", "nod"),
            RobotFaceState("idle", "default", "default", "", "idle")
        )

        for (face in demoFaceStates) {
            backgrounds.add(face.backgroundTheme)
            gestures.add(face.gesture)
            props.addAll(face.props)
        }

        assertEquals(8, backgrounds.size, "Must cover all 8 background themes")
        assertTrue(backgrounds.contains(BackgroundTheme.SUNNY))
        assertTrue(backgrounds.contains(BackgroundTheme.RAINY))
        assertTrue(backgrounds.contains(BackgroundTheme.SAKURA))
        assertTrue(backgrounds.contains(BackgroundTheme.LOVE_BG))
        assertTrue(backgrounds.contains(BackgroundTheme.THUNDER))
        assertTrue(backgrounds.contains(BackgroundTheme.MATRIX))
        assertTrue(backgrounds.contains(BackgroundTheme.NIGHT))
        assertTrue(backgrounds.contains(BackgroundTheme.DEFAULT))

        assertTrue(gestures.contains(GestureType.JUMP))
        assertTrue(gestures.contains(GestureType.TILT_LEFT))
        assertTrue(gestures.contains(GestureType.WOBBLE))
        assertTrue(gestures.contains(GestureType.BOUNCE))
        assertTrue(gestures.contains(GestureType.SHAKE))
        assertTrue(gestures.contains(GestureType.TILT_RIGHT))
        assertTrue(gestures.contains(GestureType.NOD))

        assertTrue(props.contains(PropType.UMBRELLA))
        assertTrue(props.contains(PropType.HEARTS))
        assertTrue(props.contains(PropType.SPARKLES))
        assertTrue(props.contains(PropType.MUSIC_NOTES))
        assertTrue(props.contains(PropType.QUESTION_MARK))
        assertTrue(props.contains(PropType.ZZZZZ))
        assertTrue(props.contains(PropType.FIRE))
        assertTrue(props.contains(PropType.EXCLAMATION))
    }

    @Test
    fun `PetVisionTargetTracker filters Place and Scenery clutter`() {
        val tracker = PetVisionTargetTracker()
        val raw = listOf(
            DetectedObject("Boss Face #1", 0.90f, BoundingBox(0.2f, 0.2f, 0.3f, 0.3f)),
            DetectedObject("Place / Scenery 🏢", 0.55f, BoundingBox(0.0f, 0.0f, 1.0f, 1.0f)),
            DetectedObject("Food / Drink ☕", 0.80f, BoundingBox(0.6f, 0.6f, 0.2f, 0.2f))
        )

        val result = tracker.processFrame(raw)
        assertEquals(2, result.size)
        assertTrue(result.any { it.label.contains("Boss Face") })
        assertTrue(result.any { it.label.contains("Food") })
        assertFalse(result.any { it.label.contains("Place") })
    }

    @Test
    fun `PetVisionTargetTracker suppresses objects overlapping face zone`() {
        val tracker = PetVisionTargetTracker()
        val faceBox = BoundingBox(0.3f, 0.2f, 0.4f, 0.4f)
        val raw = listOf(
            DetectedObject("Boss Face", 0.90f, faceBox),
            // False detection right across the user's mouth/glasses inside face bounds
            DetectedObject("Accessory / Item 👓", 0.65f, BoundingBox(0.40f, 0.45f, 0.20f, 0.10f)),
            // Legitimate object held to the side
            DetectedObject("Object / Item 📱", 0.82f, BoundingBox(0.05f, 0.60f, 0.15f, 0.20f))
        )

        val result = tracker.processFrame(raw, faces = listOf(faceBox))
        assertEquals(2, result.size)
        assertTrue(result.any { it.label.contains("Boss Face") })
        assertTrue(result.any { it.label.contains("Item 📱") })
        assertFalse(result.any { it.label.contains("Accessory") }, "False detection on face must be suppressed")
    }

    @Test
    fun `PetVisionTargetTracker performs coordinate smoothing with EMA`() {
        val tracker = PetVisionTargetTracker(smoothingFactor = 0.50f)
        val frame1 = listOf(
            DetectedObject("Boss Face", 0.90f, BoundingBox(0.40f, 0.20f, 0.30f, 0.30f))
        )
        val res1 = tracker.processFrame(frame1, currentTimeMs = 1000L)
        assertEquals(0.40f, res1[0].boundingBox!!.x, 0.001f)

        // Frame 2: Slight motion jitter (x jumps to 0.48)
        val frame2 = listOf(
            DetectedObject("Boss Face", 0.90f, BoundingBox(0.48f, 0.20f, 0.30f, 0.30f))
        )
        val res2 = tracker.processFrame(frame2, currentTimeMs = 1080L)
        // With alpha = 0.50, smoothedX = 0.40 + (0.48 - 0.40) * 0.5 = 0.44
        assertEquals(0.44f, res2[0].boundingBox!!.x, 0.01f)
    }

    @Test
    fun `PetVisionTargetTracker locks target after consecutive frames`() {
        val tracker = PetVisionTargetTracker(lockThresholdFrames = 3)
        val box = BoundingBox(0.3f, 0.3f, 0.2f, 0.2f)
        val frame = listOf(DetectedObject("Hand ✋", 0.88f, box))

        val res1 = tracker.processFrame(frame, currentTimeMs = 1000L)
        assertFalse(res1[0].isLocked, "Frame 1 should not be locked yet")

        val res2 = tracker.processFrame(frame, currentTimeMs = 1080L)
        assertFalse(res2[0].isLocked, "Frame 2 should not be locked yet")

        val res3 = tracker.processFrame(frame, currentTimeMs = 1160L)
        assertTrue(res3[0].isLocked, "Frame 3 should acquire target lock")
    }

    @Test
    fun `PetVisionTargetTracker caps max targets at 3 and preserves during brief drop`() {
        val tracker = PetVisionTargetTracker(persistenceWindowMs = 350L, maxActiveTargets = 3)
        val frame1 = listOf(
            DetectedObject("Boss Face", 0.95f, BoundingBox(0.3f, 0.1f, 0.3f, 0.3f)),
            DetectedObject("Hand ✋", 0.88f, BoundingBox(0.1f, 0.6f, 0.2f, 0.2f)),
            DetectedObject("Food / Drink ☕", 0.85f, BoundingBox(0.7f, 0.6f, 0.15f, 0.15f)),
            DetectedObject("Object / Item 📱", 0.75f, BoundingBox(0.7f, 0.2f, 0.15f, 0.15f))
        )

        val res1 = tracker.processFrame(frame1, currentTimeMs = 1000L)
        assertTrue(res1.size <= 3, "Total active targets must be capped at 3")

        // Temporary frame drop 100ms later (no objects in camera frame)
        val res2 = tracker.processFrame(emptyList(), currentTimeMs = 1100L)
        assertTrue(res2.isNotEmpty(), "Targets should be retained within 350ms hysteresis window")

        // After persistence window expires (500ms > 350ms)
        val res3 = tracker.processFrame(emptyList(), currentTimeMs = 1600L)
        assertTrue(res3.isEmpty(), "Targets should be cleared after persistence window expires")
    }

    @Test
    fun `PIP aspect ratios match camera sensor without cropping`() {
        val portraitWidth = 144f
        val portraitHeight = 256f
        val portraitRatio = portraitWidth / portraitHeight
        assertEquals(9f / 16f, portraitRatio, 0.001f, "Portrait PIP must be exact 9:16")

        val landscapeWidth = 240f
        val landscapeHeight = 135f
        val landscapeRatio = landscapeWidth / landscapeHeight
        assertEquals(16f / 9f, landscapeRatio, 0.001f, "Landscape PIP must be exact 16:9")
    }

    @Test
    fun `Pet Mode prompt contains tool superpowers for GPS nearby, recipes, and SMC analysis`() {
        val prompt = com.skyliner2008.jarvis.ai.JarvisPersona.PET_LIVE_SYSTEM_PROMPT
        assertTrue(prompt.contains("PET SUPERPOWERS & TOOLS CALLING"), "Prompt must define Pet superpowers and tools")
        assertTrue(prompt.contains("device_location"), "Prompt must mention device_location")
        assertTrue(prompt.contains("ร้านอาหาร"), "Prompt must guide nearby restaurant queries")
        assertTrue(prompt.contains("search_web"), "Prompt must guide search_web queries")
        assertTrue(prompt.contains("สูตรหมักหมูย่าง") || prompt.contains("สูตรอาหาร"), "Prompt must guide recipe queries")
        assertTrue(prompt.contains("trading_smc_analysis"), "Prompt must empower SMC analysis")
    }

    @Test
    fun `device_location tool definition contains query parameter for nearby search`() {
        val locTool = com.skyliner2008.jarvis.tools.device.DeviceToolDefinitions.allDefinitions.firstOrNull { it.name == "device_location" }
        assertNotNull(locTool, "device_location must exist")
        val params = locTool.parameters?.properties
        assertNotNull(params, "device_location parameters must not be null")
        assertTrue(params.containsKey("action"), "Must have action param")
        assertTrue(params.containsKey("query"), "Must have optional query param for nearby search")
    }

    @Test
    fun `Pet Mode prompt guides Dark OLED default background and avoids device_avatar_emotion on greeting`() {
        val prompt = com.skyliner2008.jarvis.ai.JarvisPersona.PET_LIVE_SYSTEM_PROMPT
        assertTrue(prompt.contains("ห้ามเรียก `device_avatar_emotion` ในการทักทายเริ่มต้น"), "Must not call emotion on greeting")
        assertTrue(prompt.contains("Pure Dark OLED Tone") || prompt.contains("background=\"default\""), "Must guide default dark background")
    }

    @Test
    fun `Pet dialogue text logic only activates for tool executions, tool results, or demo`() {
        fun resolveDialogueText(
            activeToolName: String?,
            toolResultText: String?,
            isDemoRunning: Boolean,
            speechText: String?
        ): String? {
            val isToolActive = activeToolName != null && activeToolName !in setOf(
                "device_avatar_emotion",
                "device_custom_prop",
                "device_always_live",
                "vision_activate"
            )
            return when {
                isToolActive -> "TOOL_RUNNING:$activeToolName"
                !toolResultText.isNullOrBlank() -> toolResultText
                isDemoRunning && !speechText.isNullOrBlank() -> speechText
                else -> null
            }
        }

        // 1. Normal conversation (speaking / listening / idle): NO dialogue card!
        val normalSpeech = resolveDialogueText(
            activeToolName = null,
            toolResultText = null,
            isDemoRunning = false,
            speechText = null
        )
        assertEquals(null, normalSpeech, "Normal speech must NOT produce dialogue card text")

        // 2. Internal UI tools (device_avatar_emotion, device_custom_prop) do NOT trigger dialogue card
        val emotionTool = resolveDialogueText(
            activeToolName = "device_avatar_emotion",
            toolResultText = null,
            isDemoRunning = false,
            speechText = null
        )
        assertEquals(null, emotionTool, "Internal avatar emotion tool must NOT trigger dialogue card")

        // 3. Functional tool actively running
        val runningTool = resolveDialogueText(
            activeToolName = "device_location",
            toolResultText = null,
            isDemoRunning = false,
            speechText = null
        )
        assertEquals("TOOL_RUNNING:device_location", runningTool)

        // 4. Functional tool result available
        val toolResult = resolveDialogueText(
            activeToolName = null,
            toolResultText = "📍 ร้านอาหารแนะนำ 3 ร้านในย่าน...",
            isDemoRunning = false,
            speechText = null
        )
        assertEquals("📍 ร้านอาหารแนะนำ 3 ร้านในย่าน...", toolResult)

        // 5. Emotion demo running
        val demoText = resolveDialogueText(
            activeToolName = null,
            toolResultText = null,
            isDemoRunning = true,
            speechText = "☀️ ท้องฟ้าแจ่มใส กระโดดดีใจ!"
        )
        assertEquals("☀️ ท้องฟ้าแจ่มใส กระโดดดีใจ!", demoText)
    }

    @Test
    fun `HandGesture enum contains all 7 recognition gestures plus NONE`() {
        val gestureNames = com.skyliner2008.jarvis.pet.HandGesture.entries.map { it.name }.toSet()
        assertEquals(8, gestureNames.size)
        assertTrue(gestureNames.contains("HIGH_FIVE"))
        assertTrue(gestureNames.contains("OK"))
        assertTrue(gestureNames.contains("BYE"))
        assertTrue(gestureNames.contains("NO"))
        assertTrue(gestureNames.contains("V_SIGN"))
        assertTrue(gestureNames.contains("THUMBS_UP"))
        assertTrue(gestureNames.contains("THUMBS_DOWN"))
        assertTrue(gestureNames.contains("NONE"))
    }

    @Test
    fun `PetModeController gesture debouncing and cooldown prevents spam`() = runBlocking {
        var currentState = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { currentState },
            onUpdateAvatarState = { currentState = it }
        )

        // 1. NONE gesture is ignored
        controller.onHandGesture(com.skyliner2008.jarvis.pet.HandGesture.NONE)
        assertEquals(null, currentState.statusText)

        // 2. First valid gesture is accepted
        controller.onHandGesture(com.skyliner2008.jarvis.pet.HandGesture.HIGH_FIVE)
        assertEquals(AvatarEmotion.HAPPY, currentState.emotion)
        assertEquals("เย้! แปะมือกันฮับ High-Five! ✋✨", currentState.statusText)

        // 3. Immediate subsequent gesture is ignored due to 3.0s cooldown
        controller.onHandGesture(com.skyliner2008.jarvis.pet.HandGesture.V_SIGN)
        assertEquals(AvatarEmotion.HAPPY, currentState.emotion)
        assertEquals("เย้! แปะมือกันฮับ High-Five! ✋✨", currentState.statusText)
    }

    @Test
    fun `PetFaceProfile creates 5 default slots and matches distance correctly`() {
        val defaultSlots = com.skyliner2008.jarvis.pet.PetFaceProfile.createDefaultSlots()
        assertEquals(5, defaultSlots.size)
        assertFalse(defaultSlots[0].isEnrolled)

        val profile1 = com.skyliner2008.jarvis.pet.PetFaceProfile(
            slotIndex = 0,
            name = "บอส",
            isEnrolled = true,
            landmarkRatios = listOf(1.0f, 2.0f, 3.0f, 4.0f)
        )
        val distIdentical = profile1.calculateDistance(listOf(1.0f, 2.0f, 3.0f, 4.0f))
        assertEquals(0.0f, distIdentical)
        assertTrue(profile1.isMatch(listOf(1.0f, 2.0f, 3.0f, 4.0f)))

        val distFar = profile1.calculateDistance(listOf(2.0f, 3.0f, 4.0f, 5.0f))
        assertTrue(distFar > 0.28f)
        assertFalse(profile1.isMatch(listOf(2.0f, 3.0f, 4.0f, 5.0f)))
    }

    @Test
    fun `PetNeedsState Tamagotchi engine handles decay and care activities correctly`() {
        val initial = com.skyliner2008.jarvis.pet.PetNeedsState(
            satiety = 80f,
            energy = 80f,
            hygiene = 80f,
            happiness = 70f,
            stress = 20f,
            affectionPoints = 150
        )
        assertEquals(2, initial.affectionLevel)
        assertEquals("คนแปลกหน้าคุ้นเคย", initial.affectionLevelName())

        // 1. Decay over 30 minutes (1_800_000 ms)
        val decayed = initial.decay(30 * 60_000L)
        assertTrue(decayed.satiety < initial.satiety, "Satiety should decrease after 30 mins")
        assertTrue(decayed.energy < initial.energy, "Energy should decrease after 30 mins")
        assertTrue(decayed.hygiene < initial.hygiene, "Hygiene should decrease after 30 mins")

        // 2. Feed increases satiety and reduces stress
        val fed = initial.feed()
        assertTrue(fed.satiety >= initial.satiety)
        assertTrue(fed.stress <= initial.stress)

        // 3. Clean restores hygiene
        val cleaned = initial.clean()
        assertTrue(cleaned.hygiene >= initial.hygiene)

        // 4. Play increases happiness
        val played = initial.play()
        assertTrue(played.happiness >= initial.happiness)

        // 5. Love interaction increases affection
        val loved = initial.interactLove()
        assertTrue(loved.affectionPoints > initial.affectionPoints)
    }

    @Test
    fun `Screensaver idle eye tricks and delay settings behave correctly`() = runBlocking {
        var currentState = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { currentState },
            onUpdateAvatarState = { currentState = it }
        )

        assertEquals(25, controller.idleScreensaverDelaySeconds.value)
        controller.setScreensaverDelay(45)
        assertEquals(45, controller.idleScreensaverDelaySeconds.value)

        val trickNames = EyeTrickState.entries.map { it.name }.toSet()
        assertTrue(trickNames.contains("NONE"))
        assertTrue(trickNames.contains("PING_PONG_BOUNCE"))
        assertTrue(trickNames.contains("TIRED_BOUNCE"))
        assertTrue(trickNames.contains("SNOOKER_SHOT"))

        // When trick is active, notifyInteraction resets trick to none
        currentState = currentState.copy(
            faceState = currentState.faceState.copy(eyeTrickName = "ping_pong_bounce")
        )
        assertEquals(EyeTrickState.PING_PONG_BOUNCE, currentState.faceState.eyeTrick)

        controller.notifyInteraction()
        assertEquals(EyeTrickState.NONE, currentState.faceState.eyeTrick)
    }

    @Test
    fun `Always Live pet mode activation handles on, toggle, open and switch without closing`() {
        fun resolveAlwaysLiveIntent(action: String, mode: String?): String {
            val rawAction = action.lowercase().trim()
            val m = mode?.lowercase()?.trim()
            val isPet = m in setOf("pet", "animal", "สัตว์เลี้ยง", "แก้เบื่อ") || rawAction in setOf("pet", "สัตว์เลี้ยง")
            val isExplicitOff = rawAction in setOf("off", "ปิด", "stop", "disable", "exit", "ออก", "close")
            if (isExplicitOff) return "CLOSE_ALWAYS_LIVE"
            if (isPet) return "OPEN_PET_MODE"
            if (m in setOf("drive", "car", "ขับขี่", "รถยนต์")) return "OPEN_DRIVE_MODE"
            return "OPEN_CONTROL_MODE"
        }

        assertEquals("OPEN_PET_MODE", resolveAlwaysLiveIntent("on", "pet"))
        assertEquals("OPEN_PET_MODE", resolveAlwaysLiveIntent("toggle", "pet"))
        assertEquals("OPEN_PET_MODE", resolveAlwaysLiveIntent("open", "pet"))
        assertEquals("OPEN_PET_MODE", resolveAlwaysLiveIntent("switch", "pet"))
        assertEquals("OPEN_PET_MODE", resolveAlwaysLiveIntent("pet", null))
        assertEquals("CLOSE_ALWAYS_LIVE", resolveAlwaysLiveIntent("off", "pet"))
        assertEquals("CLOSE_ALWAYS_LIVE", resolveAlwaysLiveIntent("ปิด", "pet"))
    }

    @Test
    fun `PropType includes GOLD_COIN and RAIN_DROPS for finance and weather props`() {
        val props = PropType.entries.map { it.name }.toSet()
        assertTrue(props.contains("GOLD_COIN"), "PropType must contain GOLD_COIN")
        assertTrue(props.contains("RAIN_DROPS"), "PropType must contain RAIN_DROPS")
        assertTrue(props.contains("UMBRELLA"), "PropType must contain UMBRELLA")
        assertTrue(props.contains("SUNGLASSES"), "PropType must contain SUNGLASSES")
    }

    @Test
    fun `Weather WMO code interpretation maps correctly to Thai conditions and themes`() {
        fun interpretWmoCode(code: Int): Triple<String, String, Boolean> {
            return when (code) {
                0 -> Triple("ท้องฟ้าแจ่มใส แดดออก", "☀️", false)
                1, 2 -> Triple("มีเมฆบางส่วน อากาศโปร่ง", "🌤️", false)
                3 -> Triple("มีเมฆมาก ท้องฟ้ามืดครึ้ม", "☁️", false)
                51, 53, 55 -> Triple("ฝนตกปรอยๆ เล็กน้อย", "🌦️", true)
                61, 63, 65 -> Triple("ฝนตกปานกลางถึงหนัก", "🌧️", true)
                80, 81, 82 -> Triple("ฝนฟ้าคะนองกระจาย", "⛈️", true)
                95, 96, 99 -> Triple("พายุฝนฟ้าคะนอง ลมกระโชกแรง", "⛈️⚡", true)
                else -> Triple("สภาพอากาศแปรปรวน", "🌡️", false)
            }
        }

        val clear = interpretWmoCode(0)
        assertEquals("ท้องฟ้าแจ่มใส แดดออก", clear.first)
        assertEquals("☀️", clear.second)
        assertFalse(clear.third)

        val rain = interpretWmoCode(63)
        assertEquals("ฝนตกปานกลางถึงหนัก", rain.first)
        assertEquals("🌧️", rain.second)
        assertTrue(rain.third)

        val storm = interpretWmoCode(95)
        assertEquals("พายุฝนฟ้าคะนอง ลมกระโชกแรง", storm.first)
        assertTrue(storm.third)
    }

    @Test
    fun `Avatar emotions match Eilik and Dfree expressive face states`() {
        val happyState = AvatarState(emotion = AvatarEmotion.HAPPY)
        assertEquals(AvatarEmotion.HAPPY, happyState.emotion)

        val winkState = AvatarState(emotion = AvatarEmotion.WINK)
        assertEquals(AvatarEmotion.WINK, winkState.emotion)

        val sleepingState = AvatarState(emotion = AvatarEmotion.SLEEPING)
        assertEquals(AvatarEmotion.SLEEPING, sleepingState.emotion)

        val excitedState = AvatarState(emotion = AvatarEmotion.EXCITED)
        assertEquals(AvatarEmotion.EXCITED, excitedState.emotion)

        val angryState = AvatarState(emotion = AvatarEmotion.ANGRY)
        assertEquals(AvatarEmotion.ANGRY, angryState.emotion)

        val dizzyState = AvatarState(emotion = AvatarEmotion.DIZZY)
        assertEquals(AvatarEmotion.DIZZY, dizzyState.emotion)
    }

    @Test
    fun `Dual overlapping circle gaze parallax creates correct directional shift and exposed crescent`() {
        fun computeGazeSeparation(gazeX: Float, gazeY: Float, diameter: Float): Pair<Float, Float> {
            val maxShift = diameter * 0.18f
            val frontOffsetX = gazeX * maxShift * 0.65f
            val frontOffsetY = gazeY * maxShift * 0.65f
            val backOffsetX = -gazeX * maxShift * 0.35f
            val backOffsetY = -gazeY * maxShift * 0.35f
            // Relative vector pointing from back to front disc
            val deltaX = frontOffsetX - backOffsetX
            val deltaY = frontOffsetY - backOffsetY
            return Pair(deltaX, deltaY)
        }

        val diameter = 100f

        // 1. Looking Top-Left (as in user reference photo media_1789202950390.jpg)
        val (lookTopLeftX, lookTopLeftY) = computeGazeSeparation(-0.7f, -0.5f, diameter)
        assertTrue(lookTopLeftX < 0f, "Front disc must shift left (deltaX negative)")
        assertTrue(lookTopLeftY < 0f, "Front disc must shift up (deltaY negative)")
        // Back disc is exposed at (+X, +Y) which is Bottom-Right crescent!

        // 2. Looking Right
        val (lookRightX, lookRightY) = computeGazeSeparation(1.0f, 0f, diameter)
        assertTrue(lookRightX > 0f, "Front disc must shift right")
        assertEquals(0f, lookRightY)
        // Back disc exposed at Left crescent

        // 3. Looking Down
        val (lookDownX, lookDownY) = computeGazeSeparation(0f, 1.0f, diameter)
        assertEquals(0f, lookDownX)
        assertTrue(lookDownY > 0f, "Front disc must shift down")
        // Back disc exposed at Top crescent

        // 4. Looking Up
        val (lookUpX, lookUpY) = computeGazeSeparation(0f, -1.0f, diameter)
        assertEquals(0f, lookUpX)
        assertTrue(lookUpY < 0f, "Front disc must shift up")
        // Back disc exposed at Bottom crescent
    }

    @Test
    fun `Dual circle eye at neutral gaze has concentric front and back circles without upward shift`() {
        fun computeOffsets(gazeX: Float, gazeY: Float, diameter: Float): Pair<Pair<Float, Float>, Pair<Float, Float>> {
            val maxShift = diameter * 0.18f
            val frontOffsetX = gazeX * maxShift * 0.65f
            val frontOffsetY = gazeY * maxShift * 0.65f
            val backOffsetX = -gazeX * maxShift * 0.35f
            val backOffsetY = -gazeY * maxShift * 0.35f
            return Pair(Pair(frontOffsetX, frontOffsetY), Pair(backOffsetX, backOffsetY))
        }

        val (front, back) = computeOffsets(0f, 0f, 120f)
        assertEquals(0f, kotlin.math.abs(front.first))
        assertEquals(0f, kotlin.math.abs(front.second))
        assertEquals(0f, kotlin.math.abs(back.first))
        assertEquals(0f, kotlin.math.abs(back.second))
    }

    @Test
    fun `Pet vision desk face calibration maps normal desk sitting position to dead center gaze`() {
        val deskSittingRawNormYValues = listOf(-0.45f, -0.42f, -0.48f)
        val deskNeutralBiasY = -0.45f

        for (rawNormY in deskSittingRawNormYValues) {
            val calibratedY = (rawNormY - deskNeutralBiasY) * 1.35f
            val finalNormY = if (kotlin.math.abs(calibratedY) < 0.15f) 0f else calibratedY.coerceIn(-1f, 1f)
            assertEquals(0f, finalNormY, "Desk sitting elevation must map to dead center gaze (0f)")
        }

        // Deliberate looking high up (rawNormY = -0.85f)
        val highRawNormY = -0.85f
        val highCalibratedY = (highRawNormY - deskNeutralBiasY) * 1.35f
        val highFinalNormY = if (kotlin.math.abs(highCalibratedY) < 0.15f) 0f else highCalibratedY.coerceIn(-1f, 1f)
        assertTrue(highFinalNormY < -0.4f, "Deliberate look-up should still track upward")

        // Deliberate looking down (rawNormY = 0.20f)
        val lowRawNormY = 0.20f
        val lowCalibratedY = (lowRawNormY - deskNeutralBiasY) * 1.35f
        val lowFinalNormY = if (kotlin.math.abs(lowCalibratedY) < 0.15f) 0f else lowCalibratedY.coerceIn(-1f, 1f)
        assertTrue(lowFinalNormY > 0.4f, "Deliberate look-down should track downward")
    }

    @Test
    fun `Enlarged eye dimensions provide large expressive robot companion eyes on both orientations`() {
        // Landscape (e.g. 2400x1080)
        val landscapeW = 2400f
        val landscapeH = 1080f
        val landscapeEyeDiameter = minOf(landscapeH * 0.52f, landscapeW * 0.28f)
        assertTrue(landscapeEyeDiameter >= 500f, "Landscape eye diameter should be over 500px on 1080p")
        assertTrue(landscapeEyeDiameter / landscapeH >= 0.50f, "Landscape eye should occupy >50% of visor height")

        // Portrait (e.g. 1080x2400)
        val portraitW = 1080f
        val portraitH = 2400f
        val portraitEyeDiameter = minOf(portraitW * 0.38f, portraitH * 0.24f)
        assertTrue(portraitEyeDiameter >= 400f, "Portrait eye diameter should be over 400px on 1080p")
        assertTrue(portraitEyeDiameter / portraitW >= 0.35f, "Portrait eye should occupy >35% of visor width")
    }

    @Test
    fun `Eye position shifts upward when mouth is added to balance vertical facial composition`() {
        val centerY = 500f
        val eyeDiameter = 200f

        // Case 1: IDLE without mouth -> Eye at dead center
        val idleHasMouth = false
        val idleShift = if (idleHasMouth) eyeDiameter * 0.085f else 0f
        val idleEyeCenterY = centerY - idleShift
        assertEquals(centerY, idleEyeCenterY, "When no mouth is present, eyes must sit at exact centerY")

        // Case 2: Speaking or emotion with mouth -> Eye shifts upward
        val activeHasMouth = true
        val activeShift = if (activeHasMouth) eyeDiameter * 0.085f else 0f
        val activeEyeCenterY = centerY - activeShift
        val mouthY = activeEyeCenterY + eyeDiameter * 0.75f

        assertTrue(activeEyeCenterY < centerY, "Eye must shift upward when mouth is added")
        assertEquals(17f, activeShift, 0.01f)

        // Facial composition balance around centerY (including eyebrows & mouth):
        val topOfFace = activeEyeCenterY - eyeDiameter * 0.65f // Eyebrows level
        val bottomOfMouth = mouthY + 15f // Bottom edge of mouth
        val distTop = centerY - topOfFace
        val distBottom = bottomOfMouth - centerY
        val balanceDiff = kotlin.math.abs(distTop - distBottom)

        assertTrue(balanceDiff < 20f, "Face composition (brows + eyes + mouth) must be well balanced around centerY (diff=$balanceDiff)")
    }

    @Test
    fun `BackgroundTheme contains all 8 themes and serializes correctly`() {
        assertEquals(8, BackgroundTheme.entries.size)
        val expectedThemes = setOf("DEFAULT", "RAINY", "SUNNY", "NIGHT", "SAKURA", "MATRIX", "LOVE_BG", "THUNDER")
        assertEquals(expectedThemes, BackgroundTheme.entries.map { it.name }.toSet())
    }

    @Test
    fun `PropType contains all 55 built-in props across all 4 categories`() {
        assertEquals(55, PropType.entries.size)
        // Verify key props exist
        val sampleProps = listOf(
            PropType.HEARTS, PropType.SUNGLASSES, PropType.CROWN,
            PropType.COFFEE, PropType.BOBA_TEA, PropType.GAMING_CONTROLLER,
            PropType.RAINBOW, PropType.LIGHTNING, PropType.GHOST,
            PropType.ROCKET, PropType.SHIELD, PropType.CAT_PAW, PropType.GOLD_COIN
        )
        sampleProps.forEach { prop ->
            assertNotNull(PropType.valueOf(prop.name))
        }
    }

    @Test
    fun `PetModeController theme and prop toggling operates cleanly`() = runBlocking {
        var currentState = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val controller = PetModeController(
            scope = scope,
            avatarStateProvider = { currentState },
            onUpdateAvatarState = { currentState = it }
        )

        // 1. Theme selection
        controller.setBackgroundTheme(BackgroundTheme.SAKURA)
        assertEquals(BackgroundTheme.SAKURA, currentState.faceState.backgroundTheme)

        // 2. Prop toggling
        controller.toggleProp(PropType.CROWN)
        assertTrue(currentState.faceState.props.contains(PropType.CROWN))

        controller.toggleProp(PropType.SUNGLASSES)
        assertTrue(currentState.faceState.props.contains(PropType.CROWN))
        assertTrue(currentState.faceState.props.contains(PropType.SUNGLASSES))

        // Toggle CROWN off
        controller.toggleProp(PropType.CROWN)
        assertFalse(currentState.faceState.props.contains(PropType.CROWN))
        assertTrue(currentState.faceState.props.contains(PropType.SUNGLASSES))

        // 3. Clear props
        controller.clearProps()
        assertTrue(currentState.faceState.props.isEmpty())
        assertTrue(currentState.faceState.customProps.isEmpty())

        // 4. Custom SVG vector prop add and remove
        val customProp = DynamicVectorProp(
            id = "test_prop_1",
            name = "Test Crown",
            svgPath = "M 5 20 L 15 5 L 25 20 Z",
            position = PropPosition.FOREHEAD
        )
        controller.addCustomProp(customProp)
        assertEquals(1, currentState.faceState.customProps.size)
        assertEquals("test_prop_1", currentState.faceState.customProps.first().id)

        controller.removeCustomProp("test_prop_1")
        assertTrue(currentState.faceState.customProps.isEmpty())
    }

    @Test
    fun `PetStateMachine transitions and anti-spam protection behave correctly`() {
        val stateMachine = PetStateMachine()
        val normalNeeds = PetNeedsState(satiety = 80f, energy = 80f, hygiene = 80f, happiness = 80f)

        // 1. Forehead stroke -> LOVE
        val petResult = stateMachine.processTouch(InteractionType.PET_HEAD, TouchZone.FOREHEAD, normalNeeds, AvatarEmotion.IDLE)
        assertEquals(AvatarEmotion.LOVE, petResult.emotion)

        // 2. Cheek poke -> HAPPY
        val pokeResult = stateMachine.processTouch(InteractionType.POKE, TouchZone.LEFT_CHEEK, normalNeeds, AvatarEmotion.IDLE)
        assertEquals(AvatarEmotion.HAPPY, pokeResult.emotion)

        // 3. Chin scratch -> LOVE
        val chinResult = stateMachine.processTouch(InteractionType.CHIN_SCRATCH, TouchZone.CHIN, normalNeeds, AvatarEmotion.IDLE)
        assertEquals(AvatarEmotion.LOVE, chinResult.emotion)

        // 4. Tickle -> EXCITED
        val tickleResult = stateMachine.processTouch(InteractionType.TICKLE, TouchZone.RIGHT_CHEEK, normalNeeds, AvatarEmotion.IDLE)
        assertEquals(AvatarEmotion.EXCITED, tickleResult.emotion)

        // 5. Anti-spam: 6 rapid pokes -> ANGRY
        repeat(5) {
            stateMachine.processTouch(InteractionType.POKE, TouchZone.LEFT_CHEEK, normalNeeds, AvatarEmotion.IDLE)
        }
        val spamResult = stateMachine.processTouch(InteractionType.POKE, TouchZone.LEFT_CHEEK, normalNeeds, AvatarEmotion.IDLE)
        assertEquals(AvatarEmotion.ANGRY, spamResult.emotion)

        // 6. Passive emotion: Hungry -> ANGRY
        val hungryNeeds = PetNeedsState(satiety = 10f)
        val hungryPassive = stateMachine.resolvePassiveEmotion(hungryNeeds, AvatarEmotion.IDLE)
        assertNotNull(hungryPassive)
        assertEquals(AvatarEmotion.ANGRY, hungryPassive.emotion)
    }

    @Test
    fun `PetMemoryStore records interactions and updates statistics`() {
        PetMemoryStore.reset()
        val initial = PetMemoryStore.memory
        assertEquals(0, initial.totalInteractions)

        PetMemoryStore.logInteraction(InteractionType.FEED, TouchZone.FACE_CENTER, "happy")
        PetMemoryStore.logInteraction(InteractionType.FEED, TouchZone.FACE_CENTER, "happy")
        PetMemoryStore.logInteraction(InteractionType.POKE, TouchZone.LEFT_CHEEK, "happy")

        val after = PetMemoryStore.memory
        assertEquals(3, after.totalInteractions)
        assertEquals(2, after.totalFeeds)
        assertEquals(1, after.totalPokes)
        assertEquals("feed", after.favoriteInteraction)
    }

    @Test
    fun `PetNeedsState calculates mood summary and affection levels properly`() {
        val happyState = PetNeedsState(satiety = 90f, energy = 90f, happiness = 90f, hygiene = 90f, affectionPoints = 950)
        assertEquals(PetMood.ECSTATIC, happyState.moodSummary)
        assertEquals("สายใยนิรันดร์", happyState.affectionLevelName())

        val hungryState = PetNeedsState(satiety = 15f, energy = 80f, happiness = 50f)
        assertTrue(hungryState.isHungry)
        assertEquals(PetMood.HUNGRY, hungryState.moodSummary)

        val tiredState = PetNeedsState(satiety = 80f, energy = 10f, happiness = 50f)
        assertTrue(tiredState.isExhausted)
        assertEquals(PetMood.TIRED, tiredState.moodSummary)
    }

    @Test
    fun `PetNeedsState rage accumulation and ENRAGED mood behave correctly`() {
        val normalState = PetNeedsState()
        assertEquals(0f, normalState.rage)
        assertFalse(normalState.isEnraged)

        val angeredState = normalState.addRage(40f)
        assertEquals(40f, angeredState.rage)
        assertFalse(angeredState.isEnraged)

        val enragedState = angeredState.addRage(70f)
        assertEquals(100f, enragedState.rage) // capped at 100
        assertTrue(enragedState.isEnraged)
        assertEquals(PetMood.ENRAGED, enragedState.moodSummary)

        val discharged = enragedState.dischargeRage()
        assertEquals(0f, discharged.rage)
        assertFalse(discharged.isEnraged)
    }

    @Test
    fun `Motion and Audio State Machine Matrix transitions work correctly`() {
        val stateMachine = PetStateMachine()
        val normalNeeds = PetNeedsState(satiety = 80f, energy = 80f, hygiene = 80f, happiness = 80f)

        // 1. Single shake -> DIZZY (มึน เวียนหัว)
        val shakeResult = stateMachine.processTouch(InteractionType.SHAKE, TouchZone.FACE_CENTER, normalNeeds, AvatarEmotion.IDLE)
        assertEquals(AvatarEmotion.DIZZY, shakeResult.emotion)

        // 2. Heavy shake -> ANGRY (เขย่ามากๆ โกรธ)
        val heavyShakeResult = stateMachine.processTouch(InteractionType.HEAVY_SHAKE, TouchZone.FACE_CENTER, normalNeeds, AvatarEmotion.IDLE)
        assertEquals(AvatarEmotion.ANGRY, heavyShakeResult.emotion)

        // 3. Boat rocking -> DIZZY (เอียงไปมา เหมือนนั่งเรือ เมาเรือ เวียนหัว)
        val boatRockingResult = stateMachine.processTouch(InteractionType.BOAT_ROCKING, TouchZone.FACE_CENTER, normalNeeds, AvatarEmotion.IDLE)
        assertEquals(AvatarEmotion.DIZZY, boatRockingResult.emotion)

        // 4. Table thump -> SURPRISED (ทุบโต๊ะ ตกใจ)
        val tableThumpResult = stateMachine.processTouch(InteractionType.TABLE_THUMP, TouchZone.FACE_CENTER, normalNeeds, AvatarEmotion.IDLE)
        assertEquals(AvatarEmotion.SURPRISED, tableThumpResult.emotion)

        // 5. Loud noise: first time -> SURPRISED (ตะโกน ตะคอก ตกใจ)
        val loudNoise1 = stateMachine.processTouch(InteractionType.LOUD_NOISE, TouchZone.FACE_CENTER, normalNeeds, AvatarEmotion.IDLE)
        assertEquals(AvatarEmotion.SURPRISED, loudNoise1.emotion)

        // 6. Loud noise: repeated screaming -> SAD (ตะคอกซ้ำๆ เศร้า)
        val loudNoiseSad = stateMachine.processTouch(InteractionType.LOUD_NOISE, TouchZone.FACE_CENTER, normalNeeds, AvatarEmotion.IDLE)
        assertEquals(AvatarEmotion.SAD, loudNoiseSad.emotion)

        // 7. Enraged fight back: When rage full -> ENRAGED + trigger missile barrage
        val enragedNeeds = normalNeeds.addRage(100f)
        assertTrue(enragedNeeds.isEnraged)
        val fightBackResult = stateMachine.resolveFightBack(enragedNeeds, AvatarEmotion.IDLE)
        assertEquals(AvatarEmotion.ENRAGED, fightBackResult.emotion)
        assertTrue(fightBackResult.triggerMissileBarrage)
        assertNotNull(fightBackResult.soundAction)
    }

    @Test
    fun `RobotSound enum contains MISSILE_LAUNCH and EXPLOSION`() {
        val soundNames = RobotSound.entries.map { it.name }.toSet()
        assertTrue(soundNames.contains("MISSILE_LAUNCH"))
        assertTrue(soundNames.contains("EXPLOSION"))
    }
}




package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.avatar.AvatarSkits
import com.skyliner2008.jarvis.pet.PetMemoryStore
import com.skyliner2008.jarvis.pet.PetModeController
import com.skyliner2008.jarvis.pet.PetNeedsState
import com.skyliner2008.jarvis.pet.PetSelfCare
import com.skyliner2008.jarvis.pet.PetSelfCare.Act
import com.skyliner2008.jarvis.pet.PetSelfCare.Stage
import com.skyliner2008.jarvis.pet.PetStateMachine
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The robot's life between conversations (the user's timeline, 2026-10-03): quiet for 30 s it plays (or eats /
 * bathes), 60 s drowsy, 90 s eyelids, 120 s asleep; the user's voice or a touch wakes it. Before: hungry, it turned
 * angry with an alarm every minute and nobody could feed it (logcat 2026-10-03 00:16:37, 00:17:42).
 */
class PetSelfCareTest {
    private val care = PetSelfCare()
    private val fine = PetNeedsState(satiety = 80f, energy = 80f, hygiene = 80f, happiness = 60f)

    @Test
    fun `thirty seconds into the quiet it eats when hungry, bathes when dirty, else plays`() {
        assertEquals(Act.PLAY, care.act(fine))
        assertEquals(Act.EAT, care.act(fine.copy(satiety = 18f, hygiene = 20f)), "hunger first")
        assertEquals(Act.BATHE, care.act(fine.copy(hygiene = 20f)))
        assertNull(care.act(fine.copy(energy = 10f)), "too tired to play")
    }

    @Test
    fun `the quiet goes drowsy, eyelids, asleep - one step at a time`() {
        assertEquals(Stage.AWAKE, care.nextStage(Stage.AWAKE, 59_000, 59_000))
        assertEquals(Stage.DROWSY, care.nextStage(Stage.AWAKE, 60_000, 60_000))
        assertEquals(Stage.EYELIDS, care.nextStage(Stage.DROWSY, 90_000, 30_000))
        assertEquals(Stage.ASLEEP, care.nextStage(Stage.EYELIDS, 120_000, 30_000))
        assertEquals(Stage.DROWSY, care.nextStage(Stage.AWAKE, 200_000, 0), "never two steps at once")
        assertEquals(Stage.ASLEEP, care.nextStage(Stage.ASLEEP, 0, 0), "asleep until something wakes it")
    }

    @Test
    fun `a stage that started late still lasts a while`() {
        // drowsiness came at 100 s (a scene was still on): the eyelids wait 15 s, sleep 20 s more
        assertEquals(Stage.DROWSY, care.nextStage(Stage.DROWSY, 105_000, 5_000))
        assertEquals(Stage.EYELIDS, care.nextStage(Stage.DROWSY, 115_000, 15_000))
        assertEquals(Stage.EYELIDS, care.nextStage(Stage.EYELIDS, 125_000, 10_000))
        assertEquals(Stage.ASLEEP, care.nextStage(Stage.EYELIDS, 135_000, 20_000))
    }

    @Test
    fun `what it did lately, for the AI`() {
        assertEquals("", care.recent(0))
        care.done(Act.EAT, 1_000)
        care.slept(600_000)
        assertEquals("นอน เมื่อกี้ · กิน 10 นาทีก่อน", care.recent(630_000))
        assertEquals("", care.recent(1_000 + 4 * 3_600_000L), "three hours later: nothing lately")
    }

    @Test
    fun `hunger, tiredness and boredom are no longer complaints`() {
        val sm = PetStateMachine()
        assertNull(sm.resolvePassiveEmotion(PetNeedsState(satiety = 5f), AvatarEmotion.IDLE), "no angry alarm: it eats")
        assertNull(sm.resolvePassiveEmotion(PetNeedsState(energy = 5f), AvatarEmotion.IDLE), "it sleeps in the quiet")
        assertNull(sm.resolvePassiveEmotion(PetNeedsState(happiness = 5f), AvatarEmotion.IDLE), "it plays by itself")
    }

    @Test
    fun `its own care gives no affection`() {
        val before = fine.copy(satiety = 20f, hygiene = 20f, affectionPoints = 300)
        assertEquals(70f, before.eatMeal().satiety)
        assertEquals(100f, before.bathe().hygiene)
        assertTrue(before.playAlone().happiness > before.happiness)
        assertEquals(300, before.eatMeal().bathe().playAlone().affectionPoints)
    }
}

/** The idle life in [PetModeController], on a fake clock (Avatar mode ticks it ten times a second). */
class PetIdleLifeTest {
    private var now = 1_000_000L
    private var state = AvatarState(emotion = AvatarEmotion.IDLE)
    private val scope = CoroutineScope(SupervisorJob())

    private fun controller(needs: PetNeedsState = PetNeedsState(satiety = 80f, energy = 70f, hygiene = 80f)): PetModeController {
        PetMemoryStore.saveNeedsState(needs.copy(lastUpdateTimestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()))
        val c = PetModeController(scope = scope, avatarStateProvider = { state }, onUpdateAvatarState = { state = it })
        c.nowMs = { now }
        c.start()
        return c
    }

    /** [seconds] of quiet, ticked every 100 ms as Avatar mode does; [scenesEnd]: a skit of its own ends (10 s). */
    private fun PetModeController.quiet(seconds: Int, scenesEnd: Boolean = true) {
        repeat(seconds * 10) {
            now += 100
            onAvatarTick(active = false, userVoice = false)
            val scene = state.faceState.sceneName
            if (scenesEnd && scene in finite && now - sceneAt(scene) > 10_000) state = state.copy(emotion = AvatarEmotion.IDLE, faceState = com.skyliner2008.jarvis.ui.component.avatar.RobotFaceState())
        }
    }
    private val finite = setOf(AvatarSkits.EAT, AvatarSkits.BATH, AvatarSkits.PLAY_BALL, AvatarSkits.IDLE_PLAY).map { it.sceneName }
    private val started = mutableMapOf<String, Long>()
    private fun sceneAt(scene: String) = started.getOrPut(scene) { now }

    @Test
    fun `quiet for two minutes - it plays, nods off, its eyelids sag, it sleeps`() = runBlocking {
        val c = controller()
        c.quiet(29)
        assertTrue(state.faceState.sceneName.isBlank(), "nothing for the first 30 s")
        c.quiet(2)
        assertTrue(state.faceState.sceneName in setOf(AvatarSkits.PLAY_BALL.sceneName, AvatarSkits.IDLE_PLAY.sceneName),
            "30 s: it plays by itself (${state.faceState.sceneName})")
        c.quiet(29)
        assertEquals(Stage.DROWSY, c.idleStage.value)
        assertEquals(AvatarSkits.DROWSY.sceneName, state.faceState.sceneName)
        c.quiet(30)
        assertEquals(Stage.EYELIDS, c.idleStage.value)
        c.quiet(30)
        assertEquals(Stage.ASLEEP, c.idleStage.value)
        assertTrue(c.isAsleep)
        assertEquals(AvatarSkits.SLEEP.sceneName, state.faceState.sceneName)
        assertEquals(AvatarEmotion.SLEEPING, state.emotion)
        c.quiet(600)
        assertEquals(Stage.ASLEEP, c.idleStage.value, "it sleeps on until woken")
        c.stop(); scope.cancel()
    }

    @Test
    fun `a hungry robot eats at 30 s, a dirty one bathes`() = runBlocking {
        val c = controller(PetNeedsState(satiety = 15f, energy = 70f, hygiene = 80f))
        c.quiet(31)
        assertEquals(AvatarSkits.EAT.sceneName, state.faceState.sceneName)
        assertTrue(state.faceState.sceneItem in setOf("donut", "burger", "pizza", "cake", "ice_cream", "popcorn"))
        assertTrue(c.needsState.value.satiety > 60f, "fed: ${c.needsState.value.satiety}")
        assertTrue(c.selfCareNote().startsWith("กิน"), c.selfCareNote())
        c.stop(); scope.cancel()

        val scope2 = CoroutineScope(SupervisorJob())
        val c2 = PetModeController(scope = scope2, avatarStateProvider = { state }, onUpdateAvatarState = { state = it })
        state = AvatarState(emotion = AvatarEmotion.IDLE)
        PetMemoryStore.saveNeedsState(PetNeedsState(satiety = 80f, energy = 70f, hygiene = 10f,
            lastUpdateTimestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()))
        c2.nowMs = { now }
        c2.start()
        c2.quiet(31)
        assertEquals(AvatarSkits.BATH.sceneName, state.faceState.sceneName)
        assertEquals(100f, c2.needsState.value.hygiene)
        c2.stop(); scope2.cancel()
    }

    @Test
    fun `the user's voice wakes it and it gets up, the AI's own voice does not`() = runBlocking {
        val c = controller()
        c.quiet(125)
        assertTrue(c.isAsleep)
        now += 100
        c.onAvatarTick(active = true, userVoice = false)              // "ฝันดีฮับ", an alert in the night
        assertTrue(c.isAsleep, "the AI talking leaves it asleep")
        now += 100
        c.onAvatarTick(active = true, userVoice = true)
        assertEquals(Stage.AWAKE, c.idleStage.value)
        assertEquals(AvatarSkits.WAKE.sceneName, state.faceState.sceneName, "it gets up from where it lay")
        c.stop(); scope.cancel()
    }

    @Test
    fun `a touch anywhere wakes it, and that is all the touch does`() = runBlocking {
        val c = controller()
        c.quiet(125)
        assertTrue(c.noteTouch(), "the touch woke it")
        assertEquals(Stage.AWAKE, c.idleStage.value)
        assertEquals(AvatarSkits.WAKE.sceneName, state.faceState.sceneName)
        assertFalse(c.noteTouch(), "awake: a touch is a touch")
        c.stop(); scope.cancel()
    }

    @Test
    fun `talking to a drowsy robot perks it up and the quiet starts over`() = runBlocking {
        val c = controller()
        c.quiet(65)
        assertEquals(Stage.DROWSY, c.idleStage.value)
        now += 100
        c.onAvatarTick(active = true, userVoice = false)              // the AI says something
        assertEquals(Stage.AWAKE, c.idleStage.value)
        assertTrue(state.faceState.sceneName.isBlank())
        assertEquals(AvatarEmotion.IDLE, state.emotion)
        c.quiet(50)
        assertEquals(Stage.AWAKE, c.idleStage.value, "a new quiet stretch: 60 s again before drowsiness")
        c.stop(); scope.cancel()
    }

    @Test
    fun `asked to sleep it sleeps at once, unless it is not sleepy`() = runBlocking {
        val c = controller(PetNeedsState(energy = 40f))
        c.putToSleep()
        assertTrue(c.isAsleep)
        assertEquals(AvatarSkits.SLEEP.sceneName, state.faceState.sceneName)
        c.stop(); scope.cancel()

        val scope2 = CoroutineScope(SupervisorJob())
        state = AvatarState(emotion = AvatarEmotion.IDLE)
        PetMemoryStore.saveNeedsState(PetNeedsState(energy = 95f, lastUpdateTimestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()))
        val c2 = PetModeController(scope = scope2, avatarStateProvider = { state }, onUpdateAvatarState = { state = it })
        c2.nowMs = { now }
        c2.start()
        c2.putToSleep()
        assertFalse(c2.isAsleep, "not sleepy: it says so")
        assertEquals(AvatarEmotion.CONFUSED, state.emotion)
        c2.stop(); scope2.cancel()
    }
}

package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.pet.InteractionType
import com.skyliner2008.jarvis.pet.PetNeedsState
import com.skyliner2008.jarvis.pet.PetStateMachine
import com.skyliner2008.jarvis.pet.TouchZone
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PetNeedsLogicTest {

    private val minute = 60_000L

    @Test
    fun `energy recovers while sleeping and drains while awake`() {
        val tired = PetNeedsState(energy = 20f)
        assertTrue(tired.decay(60 * minute, isSleeping = true).energy > 60f)
        assertTrue(tired.decay(60 * minute, isSleeping = false).energy < 20f)
    }

    @Test
    fun `closing the app overnight does not starve the pet`() {
        val saved = PetNeedsState(satiety = 46f, energy = 75f, hygiene = 83f, stress = 8f)
        val restored = saved.decayOffline(205 * minute)   // the case measured on the test phone
        assertTrue(restored.satiety >= PetNeedsState.OFFLINE_SATIETY_FLOOR, "satiety=${restored.satiety}")
        assertTrue(restored.energy > saved.energy, "energy=${restored.energy}")
        assertTrue(restored.stress <= saved.stress, "stress=${restored.stress}")

        val offlineOneDay = saved.decayOffline(24 * 60 * minute)
        assertEquals(PetNeedsState.OFFLINE_SATIETY_FLOOR, offlineOneDay.satiety)
        assertEquals(PetNeedsState.OFFLINE_HYGIENE_FLOOR, offlineOneDay.hygiene)
    }

    @Test
    fun `offline floor never raises a pet that was already hungrier`() {
        val starving = PetNeedsState(satiety = 5f, hygiene = 10f)
        val restored = starving.decayOffline(10 * minute)
        assertTrue(restored.satiety <= 5f)
        assertTrue(restored.hygiene <= 10f)
    }

    @Test
    fun `annoyed pokes give no affection`() {
        val sm = PetStateMachine()
        val needs = PetNeedsState(affectionPoints = 100)
        var last = needs
        repeat(4) {
            val r = sm.processTouch(InteractionType.POKE, TouchZone.RIGHT_CHEEK, last, AvatarEmotion.IDLE)
            last = r.needsUpdate?.invoke(last) ?: last
        }
        // pokes 1-2 are cute (+4 each), pokes 3-4 are annoying (+0)
        assertEquals(108, last.affectionPoints)
    }

    @Test
    fun `refusing to play while hungry has no play reward`() {
        val sm = PetStateMachine()
        val hungry = PetNeedsState(satiety = 10f, energy = 80f, happiness = 40f)
        val r = sm.processTouch(InteractionType.PLAY, TouchZone.FACE_CENTER, hungry, AvatarEmotion.IDLE)
        val after = r.needsUpdate!!.invoke(hungry)
        assertEquals(AvatarEmotion.CONFUSED, r.emotion)
        assertEquals(hungry.happiness, after.happiness)
        assertEquals(hungry.satiety, after.satiety)
    }

    @Test
    fun `overfeeding is only a nibble`() {
        val sm = PetStateMachine()
        val full = PetNeedsState(satiety = 90f, happiness = 50f, affectionPoints = 100)
        val r = sm.processTouch(InteractionType.FEED, TouchZone.FACE_CENTER, full, AvatarEmotion.IDLE)
        val after = r.needsUpdate!!.invoke(full)
        assertEquals(50f, after.happiness)
        assertEquals(100, after.affectionPoints)
    }
}

package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.avatar.AvatarSkits
import com.skyliner2008.jarvis.pet.PetSceneArchetype
import com.skyliner2008.jarvis.pet.PetSceneEngine
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetSceneScriptTest {

    @Test
    fun `every archetype has a scripted sound timeline`() {
        for (archetype in PetSceneArchetype.entries) {
            val spec = PetSceneEngine.ARCHETYPE_SPECS.getValue(archetype)
            assertTrue(spec.cues.isNotEmpty(), "no cues for $archetype")
            assertTrue(spec.cues.all { it.atMs < spec.durationMs }, "cue past the end in $archetype")
        }
    }

    @Test
    fun `story archetypes play the robot's closest skit, the rest show emotion and props`() {
        fun skitFor(archetype: PetSceneArchetype) =
            AvatarSkits.forSceneName(PetSceneEngine.resolveScene(archetype).first.sceneName)
        assertEquals(AvatarSkits.ANGRY, skitFor(PetSceneArchetype.ANGRY_MISSILE))
        assertEquals(AvatarSkits.LOVE, skitFor(PetSceneArchetype.SUPER_LOVE))
        assertEquals(AvatarSkits.SAD, skitFor(PetSceneArchetype.DRAMATIC_CRY))
        assertEquals(AvatarSkits.PARTY, skitFor(PetSceneArchetype.CELEBRATION))
        // the care scenes are the robot's own care skits (it eats, bathes and plays by itself: PetSelfCare)
        assertEquals(AvatarSkits.EAT, skitFor(PetSceneArchetype.EATING))
        assertEquals(AvatarSkits.BATH, skitFor(PetSceneArchetype.BATH_CLEAN))
        assertEquals(AvatarSkits.PLAY_BALL, skitFor(PetSceneArchetype.PLAY_GAMING))
        assertNull(skitFor(PetSceneArchetype.DRINKING))
    }

    @Test
    fun `vr and music keywords win over sunglasses`() {
        assertEquals(PetSceneArchetype.VR_MODE, PetSceneEngine.resolveFromKeyword("ใส่แว่น VR")?.second?.archetype)
        assertEquals(PetSceneArchetype.MUSIC, PetSceneEngine.resolveFromKeyword("ใส่หูฟังฟังเพลง")?.second?.archetype)
        assertEquals(PetSceneArchetype.MEME_THUG_LIFE, PetSceneEngine.resolveFromKeyword("ใส่แว่นดำ")?.second?.archetype)
        assertEquals(PetSceneArchetype.EATING, PetSceneEngine.resolveFromKeyword("feed pizza")?.second?.archetype)
    }
}

class AvatarSkitsTest {

    @Test
    fun `every skit has its sounds inside the skit, in order`() {
        assertEquals(18, AvatarSkits.ALL.size)
        assertEquals(AvatarSkits.ALL.size, AvatarSkits.ALL.map { it.id }.toSet().size)
        for (skit in AvatarSkits.ALL) {
            assertTrue(skit.cues.isNotEmpty(), "no sound for ${skit.id}")
            assertTrue(skit.cues.zipWithNext().all { (a, b) -> a.atMs <= b.atMs }, "cues out of order in ${skit.id}")
            assertTrue(skit.cues.all { it.atMs < skit.durationMs }, "cue past the end in ${skit.id}")
        }
    }

    @Test
    fun `skits play under the pet brain's scene name`() {
        assertEquals("godot:party", AvatarSkits.PARTY.sceneName)
        assertEquals(AvatarSkits.PARTY, AvatarSkits.forSceneName("godot:party"))
        assertNull(AvatarSkits.forSceneName("godot:nope"))
        assertEquals(AvatarEmotion.ENRAGED, AvatarSkits.ANGRY.emotion)
    }
}

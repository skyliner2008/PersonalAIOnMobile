package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.pet.PetSceneArchetype
import com.skyliner2008.jarvis.pet.PetSceneEngine
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme
import com.skyliner2008.jarvis.ui.component.avatar.PropType
import com.skyliner2008.jarvis.ui.component.avatar.RiveAvatarMapper
import com.skyliner2008.jarvis.ui.component.avatar.RiveSeq
import com.skyliner2008.jarvis.ui.component.avatar.withFace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PetSceneScriptTest {

    @Test
    fun `every archetype has a Rive story and a scripted sound timeline`() {
        for (archetype in PetSceneArchetype.entries) {
            assertNotNull(RiveSeq.forScene(archetype.name.lowercase()), "no story for $archetype")
            val spec = PetSceneEngine.ARCHETYPE_SPECS.getValue(archetype)
            assertTrue(spec.cues.isNotEmpty(), "no cues for $archetype")
            assertTrue(spec.cues.all { it.atMs < spec.durationMs }, "cue past the end in $archetype")
        }
    }

    @Test
    fun `scene state drives seq and item and hides compose overlays`() {
        val (face, _) = PetSceneEngine.resolveScene(PetSceneArchetype.DRINKING, PropType.BOBA_TEA)
        val plan = RiveAvatarMapper.plan(AvatarState().withFace(face))
        assertEquals(RiveSeq.SCENE_DRINKING, plan.inputs.seq)
        assertEquals(1, plan.inputs.item)
        assertTrue(plan.composeProps.isEmpty())
        assertEquals(BackgroundTheme.DEFAULT, plan.composeBackground)
    }

    @Test
    fun `vr and music keywords win over sunglasses`() {
        assertEquals(PetSceneArchetype.VR_MODE, PetSceneEngine.resolveFromKeyword("ใส่แว่น VR")?.second?.archetype)
        assertEquals(PetSceneArchetype.MUSIC, PetSceneEngine.resolveFromKeyword("ใส่หูฟังฟังเพลง")?.second?.archetype)
        assertEquals(PetSceneArchetype.MEME_THUG_LIFE, PetSceneEngine.resolveFromKeyword("ใส่แว่นดำ")?.second?.archetype)
        assertEquals(PetSceneArchetype.EATING, PetSceneEngine.resolveFromKeyword("feed pizza")?.second?.archetype)
    }
}

class RiveMoodStoriesTest {

    @Test
    fun `mood stories cover seq 82 to 100 with sounds inside the story`() {
        val all = com.skyliner2008.jarvis.ui.component.avatar.RiveMoodStories.ALL
        assertEquals((82..100).toList(), all.map { it.seq })
        for (story in all) {
            assertTrue(story.cues.isNotEmpty(), "no sound for ${story.name}")
            assertTrue(story.cues.zipWithNext().all { (a, b) -> a.atMs <= b.atMs }, "cues out of order in ${story.name}")
            assertTrue(story.cues.all { it.atMs < story.durationMs }, "cue past the end in ${story.name}")
        }
    }

    @Test
    fun `emotions pick their mood story and never repeat the last variant`() {
        val m = com.skyliner2008.jarvis.ui.component.avatar.RiveMoodStories
        assertEquals(listOf(m.SHOCKED), m.variantsFor(com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SURPRISED))
        assertEquals(listOf(m.CURIOUS), m.variantsFor(com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.CONFUSED))
        assertTrue(m.variantsFor(com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.LISTENING).isEmpty())
        assertTrue(m.variantsFor(com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SPEAKING).isEmpty())
        repeat(20) { assertTrue(m.pick(m.variantsFor(com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.ANGRY), m.FUMING) == m.GLITCH) }
    }
}

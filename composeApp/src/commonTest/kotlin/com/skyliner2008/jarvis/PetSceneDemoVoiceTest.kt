package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.avatar.AvatarSkits
import com.skyliner2008.jarvis.pet.PetModeController
import com.skyliner2008.jarvis.pet.PetSceneArchetype
import com.skyliner2008.jarvis.pet.PetSceneEngine
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** ปุ่มเดโม่และคำสั่งเสียงเล่นฉากต้องหาเจอทุกฉากและเล่นจนจบ */
class PetSceneDemoVoiceTest {

    private fun controller(): Triple<PetModeController, () -> AvatarState, CoroutineScope> {
        var state = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val c = PetModeController(scope = scope, avatarStateProvider = { state }, onUpdateAvatarState = { state = it })
        return Triple(c, { state }, scope)
    }

    @Test
    fun `every scene name the voice tool advertises resolves to its scene`() {
        val toolNames = mapOf(
            "eating" to PetSceneArchetype.EATING, "drinking" to PetSceneArchetype.DRINKING,
            "bath_clean" to PetSceneArchetype.BATH_CLEAN, "gaming" to PetSceneArchetype.PLAY_GAMING,
            "study_work" to PetSceneArchetype.STUDY_WORK, "thug_life" to PetSceneArchetype.MEME_THUG_LIFE,
            "rich" to PetSceneArchetype.MEME_RICH, "royal" to PetSceneArchetype.MEME_ROYAL,
            "fire" to PetSceneArchetype.COMEDY_FIRE, "thunder" to PetSceneArchetype.COMEDY_THUNDER,
            "soul_out" to PetSceneArchetype.COMEDY_SOUL_OUT, "angry_missile" to PetSceneArchetype.ANGRY_MISSILE,
            "super_love" to PetSceneArchetype.SUPER_LOVE, "cry" to PetSceneArchetype.DRAMATIC_CRY,
            "celebrate" to PetSceneArchetype.CELEBRATION, "rain_umbrella" to PetSceneArchetype.RAIN_UMBRELLA,
            "vr_mode" to PetSceneArchetype.VR_MODE, "music" to PetSceneArchetype.MUSIC
        )
        for ((name, expected) in toolNames) {
            assertEquals(expected, PetSceneEngine.resolveFromKeyword(name)?.second?.archetype, "scene '$name'")
        }
    }

    @Test
    fun `thai voice phrases pick the intended scene`() {
        val phrases = mapOf(
            "อาบน้ำให้หน่อย" to PetSceneArchetype.BATH_CLEAN,
            "ดื่มน้ำหน่อย" to PetSceneArchetype.DRINKING,
            "ร้องไห้น้ำตาไหล" to PetSceneArchetype.DRAMATIC_CRY,
            "สวมมงกุฎเป็นราชา" to PetSceneArchetype.MEME_ROYAL,
            "เหนื่อยจนวิญญาณหลุด" to PetSceneArchetype.COMEDY_SOUL_OUT,
            "โดนไฟฟ้าช็อต" to PetSceneArchetype.COMEDY_THUNDER,
            "ไฟไหม้ตูด" to PetSceneArchetype.COMEDY_FIRE,
            "ใส่แว่น VR" to PetSceneArchetype.VR_MODE,
            "ฟังเพลงหน่อย" to PetSceneArchetype.MUSIC,
            "ใส่แว่นดำเท่ๆ" to PetSceneArchetype.MEME_THUG_LIFE,
            "กินพิซซ่า" to PetSceneArchetype.EATING
        )
        for ((text, expected) in phrases) {
            assertEquals(expected, PetSceneEngine.resolveFromKeyword(text)?.second?.archetype, "phrase '$text'")
        }
    }

    @Test
    fun `skits are reachable by id and by voice`() {
        for (skit in AvatarSkits.ALL) {
            assertEquals(skit, AvatarSkits.resolveKeyword(skit.id), skit.id)
            for (kw in skit.keywords) {
                assertEquals(skit, AvatarSkits.resolveKeyword("เล่น $kw หน่อย"), "keyword '$kw'")
            }
        }
        assertEquals(AvatarSkits.PARTY, AvatarSkits.resolveKeyword("ฉลองปาร์ตี้กันเลย"))
        assertEquals(AvatarSkits.DIZZY, AvatarSkits.resolveKeyword("มึนหัวไปหมดแล้ว"))
    }

    @Test
    fun `voice keyword plays a skit or a scene`() = runBlocking {
        val (c, state, scope) = controller()
        assertEquals("ปาร์ตี้", c.playSceneByNameOrKeyword("ปาร์ตี้"))
        assertEquals(AvatarSkits.PARTY, AvatarSkits.forSceneName(state().faceState.sceneName))
        val firstId = state().faceState.sceneId

        assertNotNull(c.playSceneByNameOrKeyword("vr_mode"))
        assertEquals("vr_mode", state().faceState.sceneName)
        assertNotEquals(firstId, state().faceState.sceneId, "a new scene starts over")
        assertEquals(null, c.playSceneByNameOrKeyword("xyz-not-a-scene"))
        scope.cancel()
    }

    @Test
    fun `a scene keeps playing through touches and ends by itself`() = runBlocking {
        val (c, state, scope) = controller()
        c.playScene(PetSceneArchetype.MEME_RICH, durationMs = 600L, label = "🎬 1/23 · test")
        assertEquals("meme_rich", state().faceState.sceneName)
        assertEquals("🎬 1/23 · test", state().faceState.speechText)

        delay(200L)
        c.onPoke(com.skyliner2008.jarvis.pet.TouchZone.FACE_CENTER)
        assertEquals("meme_rich", state().faceState.sceneName, "a poke must not cut the scene")

        delay(700L)
        assertTrue(state().faceState.sceneName.isBlank(), "the scene ends after its duration")
        assertEquals(AvatarEmotion.IDLE, state().emotion)
        scope.cancel()
    }

    @Test
    fun `stopping the demo stops the skit at once`() = runBlocking {
        val (c, state, scope) = controller()
        c.playSkit(AvatarSkits.DIZZY, label = "🎬 test")
        assertTrue(c.isScenePlaying)
        c.stopScene()
        assertTrue(state().faceState.sceneName.isBlank())
        scope.cancel()
    }

    @Test
    fun `showcase covers every skit and every archetype without one, once`() {
        val archetypes = PetSceneArchetype.entries.map { it.name.lowercase() }
            .filter { AvatarSkits.forSceneName(it) == null }
        val names = AvatarSkits.ALL.map { it.sceneName } + archetypes
        assertEquals(23, names.size)
        assertEquals(names.size, names.toSet().size)
    }
}

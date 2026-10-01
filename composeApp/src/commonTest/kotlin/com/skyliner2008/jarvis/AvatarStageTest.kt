package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.avatar.AvatarLook
import com.skyliner2008.jarvis.avatar.AvatarStage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The commands the app sends the Godot robot (pet_godot/scripts/app_bridge.gd, scenes/SCHEMA.md "App bridge"). */
class AvatarStageTest {
    private fun sent(block: () -> Unit): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        val before = AvatarStage.platformSend
        AvatarStage.platformSend = { out += Json.parseToJsonElement(it).jsonObject }
        try {
            block()
        } finally {
            AvatarStage.platformSend = before
        }
        return out
    }

    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.content

    @Test
    fun `stop names the skit so a tool's scene that took over keeps playing`() {
        val (named, any) = sent { AvatarStage.stopScene("party"); AvatarStage.stopScene() }
        assertEquals("stop", named.text("t"))
        assertEquals("party", named.text("id"))
        assertEquals("stop", any.text("t"))
        assertFalse(any.containsKey("id"), "no id = whatever plays")
    }

    @Test
    fun `a new engine gets the current look first, and a closed stage forgets it`() {
        val look = AvatarLook(emotion = "happy", eyes = "", items = listOf("crown"), background = "night", emote = "heart")
        val first = sent { AvatarStage.sendState(look); AvatarStage.onEngineAttached() }
        assertEquals(2, first.size)
        assertEquals(first[0], first[1])
        assertEquals("state", first[1].text("t"))
        assertEquals("crown", first[1]["items"].toString().trim('[', ']', '"'))
        val afterHide = sent { AvatarStage.hide(); AvatarStage.onEngineAttached() }
        assertTrue(afterHide.isEmpty(), "the next visit starts from its own look")
    }

    @Test
    fun `gaze and voice stay in range`() {
        val (look, voice) = sent { AvatarStage.look(3f, -2f); AvatarStage.voice(true, 1.7f) }
        assertEquals(1f, look["x"]!!.jsonPrimitive.float)
        assertEquals(-1f, look["y"]!!.jsonPrimitive.float)
        assertEquals(1f, voice["level"]!!.jsonPrimitive.float)
    }

    @Test
    fun `ready comes from the engine and goes when it stops`() {
        AvatarStage.dispatchEvent("ready", "{}")
        assertTrue(AvatarStage.ready.value)
        AvatarStage.onEngineStopped()
        assertFalse(AvatarStage.ready.value)
        AvatarStage.dispatchEvent("touch", "not json")          // a bad payload is an empty one, not a crash
        assertFalse(AvatarStage.ready.value)
    }
}

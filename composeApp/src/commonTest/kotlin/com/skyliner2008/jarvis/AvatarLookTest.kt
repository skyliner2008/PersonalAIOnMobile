package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.avatar.AvatarLookMapper
import com.skyliner2008.jarvis.avatar.AvatarToolData
import com.skyliner2008.jarvis.pet.PetModeController
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import com.skyliner2008.jarvis.ui.component.avatar.RobotFaceState
import com.skyliner2008.jarvis.ui.component.avatar.spriteFace
import com.skyliner2008.jarvis.ui.component.avatar.withFace
import com.skyliner2008.jarvis.ui.screen.onAvatarTouch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The app's avatar state in the Godot robot's words (pet_godot/scripts/app_bridge.gd "state"). */
class AvatarLookTest {
    // what the robot has (pet_character.gd EMOTIONS / EYES, robot_meta.json accessories, main.gd BACKGROUNDS)
    private val robotEmotions = setOf("normal", "happy", "sad", "wink", "love", "surprised", "sleepy", "angry", "talk")
    private val robotEyes = setOf("", "normal", "happy", "sad", "wink", "love", "surprised", "sleepy", "angry", "dizzy", "cry", "squint")
    private val robotItems = setOf(
        "crown", "party_hat", "wizard_hat", "chef_hat", "sunglasses", "headphones", "backpack", "propeller_cap",
        "cat_ears", "halo", "devil_horns", "viking_helmet", "astronaut_helmet", "fedora", "vr_headset", "jetpack", "cape",
        "cannon_L", "cannon_R", "laser_L", "laser_R", "flashlight_L", "flashlight_R", "shield_L", "shield_R"
    )
    private val robotStages = setOf("", "black", "studio", "night", "sunny", "cyber", "sakura", "space", "underwater",
        "city", "sunset", "snow", "candy", "hangar")
    private val robotEmotes = setOf("", "heart", "question", "exclaim", "zzz", "note", "sparkle", "star", "dots", "bolt",
        "sweat", "anger", "dizzy", "battery_low", "rain_cloud")

    @Test
    fun `every app emotion has a look the robot can show`() {
        assertEquals(AvatarEmotion.entries.toSet(), AvatarLookMapper.mappedEmotions)
        for (emotion in AvatarEmotion.entries) {
            val look = AvatarLookMapper.look(AvatarState(emotion = emotion))
            assertTrue(look.emotion in robotEmotions, "$emotion -> ${look.emotion}")
            assertTrue(look.eyes in robotEyes, "$emotion eyes ${look.eyes}")
            assertTrue(look.items.all { it in robotItems }, "$emotion items ${look.items}")
            assertTrue(look.background in robotStages, "$emotion stage ${look.background}")
            assertTrue(look.emote in robotEmotes, "$emotion emote ${look.emote}")
        }
    }

    @Test
    fun `the AI's props, eyes and backdrop go on top of the emotion`() {
        val face = RobotFaceState(emotionName = "happy", propsRaw = "crown,sunglasses,hearts", backgroundName = "sakura", eyeStyleName = "heart")
        val look = AvatarLookMapper.look(AvatarState().withFace(face))
        assertEquals("happy", look.emotion)
        assertEquals("love", look.eyes)
        assertEquals(listOf("crown", "sunglasses"), look.items)
        assertEquals("sakura", look.background)
        assertEquals("heart", look.emote)
    }

    @Test
    fun `a dizzy pet shows dizzy eyes and stars`() {
        val look = AvatarLookMapper.look(AvatarState(emotion = AvatarEmotion.HAPPY, isDizzy = true))
        assertEquals("dizzy", look.eyes)
        assertEquals("dizzy", look.emote)
    }

    @Test
    fun `robot touches reach the pet brain`() {
        var state = AvatarState()
        val scope = CoroutineScope(SupervisorJob())
        val c = PetModeController(scope = scope, avatarStateProvider = { state }, onUpdateAvatarState = { state = it })
        c.onAvatarTouch("tap", "forehead")
        assertNotNull(state.statusText, "a pat on the head gets a reaction")
        state = AvatarState()
        c.onAvatarTouch("double", "left_cheek")
        assertNotNull(state.statusText, "a tickle gets a reaction")
        scope.cancel()
    }

    @Test
    fun `sprites have a face for every look`() {
        assertEquals("dizzy", spriteFace("normal", "dizzy"))
        assertEquals("happy", spriteFace("happy", "love"))
        assertEquals("talk", spriteFace("talk", ""))
    }

    /** logcat 2026-10-01 21:38: "ราคาหุ้น Tesla ราคาหุ้น Microsoft" — two trading_price calls at once, one card */
    @Test
    fun `tool data stays with the live call that made it`() = kotlinx.coroutines.runBlocking {
        val tesla = com.skyliner2008.jarvis.data.LiveToolCallContext()
        val microsoft = com.skyliner2008.jarvis.data.LiveToolCallContext()
        kotlinx.coroutines.withContext(tesla) { AvatarToolData.publish("trading_price", mapOf("symbol" to "TSLA", "price" to "354.4220")) }
        kotlinx.coroutines.withContext(microsoft) { AvatarToolData.publish("trading_price", mapOf("symbol" to "MSFT", "price" to "517.7920")) }
        assertEquals("TSLA", (AvatarToolData.of(tesla, "trading_price") as JsonObject)["symbol"]?.jsonPrimitive?.content)
        assertEquals("MSFT", (AvatarToolData.of(microsoft, "trading_price") as JsonObject)["symbol"]?.jsonPrimitive?.content)
        assertNull(AvatarToolData.of(tesla, "device_weather"))
        val movers = com.skyliner2008.jarvis.data.LiveToolCallContext()
        kotlinx.coroutines.withContext(movers) {
            AvatarToolData.publishRows("trading_top_gainers", listOf(mapOf("symbol" to "BTCUSDT", "change" to "5.1")))
        }
        assertEquals(1, (AvatarToolData.of(movers, "trading_top_gainers") as JsonArray).size)
        // outside a live call (chat) nothing keeps it, and nothing breaks
        AvatarToolData.publish("device_battery_status", mapOf("level" to 42))
    }
}

package com.skyliner2008.jarvis.avatar

import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme
import com.skyliner2008.jarvis.ui.component.avatar.EyeStyle
import com.skyliner2008.jarvis.ui.component.avatar.EyeTrickState
import com.skyliner2008.jarvis.ui.component.avatar.ForegroundEffect
import com.skyliner2008.jarvis.ui.component.avatar.GestureType
import com.skyliner2008.jarvis.ui.component.avatar.PropType

/**
 * What the Godot robot shows for an [AvatarState], in the robot's own words
 * (pet_godot/scripts/app_bridge.gd "state" command):
 *
 * - [emotion]: normal / happy / sad / wink / love / surprised / sleepy / angry / talk
 * - [eyes]: "" (the emotion's own) or normal / happy / sad / wink / love / surprised / sleepy / angry / dizzy / cry / squint
 * - [items]: gear it wears (pet_godot/assets/robot_meta.json accessories)
 * - [background]: "" (the main black stage) or a stage backdrop
 * - [emote]: a symbol that keeps coming over its head ("" = none): heart / question / exclaim / zzz / note /
 *   sparkle / star / dots / bolt / sweat / anger, or dizzy (stars) / battery_low / rain_cloud
 */
data class AvatarLook(
    val emotion: String = "normal",
    val eyes: String = "",
    val items: List<String> = emptyList(),
    val background: String = "",
    val emote: String = ""
)

object AvatarLookMapper {

    private data class Face(
        val emotion: String,
        val eyes: String = "",
        val items: List<String> = emptyList(),
        val background: String = "",
        val emote: String = ""
    )

    /** Every app emotion on the robot (the robot has 9 bodies, 11 eyes, 17 wearables, 13 stages). */
    private val FACES: Map<AvatarEmotion, Face> = mapOf(
        AvatarEmotion.IDLE to Face("normal"),
        AvatarEmotion.LISTENING to Face("normal"),
        AvatarEmotion.THINKING to Face("normal", "squint", emote = "dots"),
        AvatarEmotion.SPEAKING to Face("talk"),
        AvatarEmotion.HAPPY to Face("happy"),
        AvatarEmotion.SAD to Face("sad"),
        AvatarEmotion.ANGRY to Face("angry", emote = "anger"),
        AvatarEmotion.LOVE to Face("love", emote = "heart"),
        AvatarEmotion.SLEEPING to Face("sleepy", emote = "zzz"),
        AvatarEmotion.EXCITED to Face("happy", emote = "sparkle"),
        AvatarEmotion.WINK to Face("wink"),
        AvatarEmotion.CONFUSED to Face("normal", "squint", emote = "question"),
        AvatarEmotion.POUT to Face("angry", "squint"),
        AvatarEmotion.DIZZY to Face("normal", "dizzy", emote = "dizzy"),
        AvatarEmotion.SURPRISED to Face("surprised", emote = "exclaim"),
        AvatarEmotion.BORED to Face("sleepy", "squint", emote = "dots"),
        AvatarEmotion.ENRAGED to Face("angry", emote = "anger"),
        AvatarEmotion.DEAD to Face("sad", "dizzy"),
        AvatarEmotion.LAUGHING to Face("happy", emote = "sparkle"),
        AvatarEmotion.MUSIC to Face("happy", items = listOf("headphones"), emote = "note"),
        AvatarEmotion.VR_MODE to Face("normal", items = listOf("vr_headset"), background = "cyber"),
        AvatarEmotion.DIVING to Face("happy", items = listOf("astronaut_helmet"), background = "underwater"),
        AvatarEmotion.EVIL to Face("angry", "squint", items = listOf("devil_horns")),
        AvatarEmotion.FOCUSED to Face("normal", "squint"),
        AvatarEmotion.SHY to Face("love", "happy", emote = "heart"),
        AvatarEmotion.DISGUSTED to Face("sad", "squint", emote = "sweat"),
        AvatarEmotion.CAMERA_MODE to Face("happy", emote = "sparkle"),
        AvatarEmotion.EATING to Face("happy", emote = "heart"),
        AvatarEmotion.DRINKING to Face("happy", emote = "note"),
        AvatarEmotion.PUZZLED to Face("normal", "squint", emote = "question"),
        AvatarEmotion.SICK to Face("sad", emote = "sweat"),
        AvatarEmotion.RICH to Face("happy", items = listOf("crown"), emote = "star"),
        AvatarEmotion.CRYING to Face("sad", "cry"),
        AvatarEmotion.READING to Face("normal", "squint"),
        AvatarEmotion.GAMING to Face("happy", items = listOf("headphones"), background = "cyber"),
        AvatarEmotion.TRAVELING to Face("happy", items = listOf("backpack"), background = "sunny"),
        AvatarEmotion.WORKING to Face("normal", items = listOf("headphones")),
        AvatarEmotion.COLD to Face("sad", background = "snow"),
        AvatarEmotion.HOT to Face("sad", background = "sunny", emote = "sweat"),
        AvatarEmotion.DETECTIVE to Face("normal", "squint", items = listOf("fedora"), emote = "question"),
        AvatarEmotion.COOKING to Face("happy", items = listOf("chef_hat")),
        AvatarEmotion.ART_MODE to Face("happy", background = "candy", emote = "sparkle"),
        AvatarEmotion.SPACE to Face("happy", items = listOf("astronaut_helmet"), background = "space"),
        AvatarEmotion.PARTY to Face("happy", items = listOf("party_hat"), emote = "sparkle"),
        AvatarEmotion.DREAMING to Face("sleepy", background = "night", emote = "zzz"),
        AvatarEmotion.EXHAUSTED to Face("sleepy", emote = "battery_low"),
        AvatarEmotion.ELECTRIC to Face("surprised", background = "cyber", emote = "bolt"),
        AvatarEmotion.SNEAKY to Face("normal", "squint", emote = "dots"),
        AvatarEmotion.ROMANTIC to Face("love", background = "sakura", emote = "heart"),
        AvatarEmotion.HERO to Face("happy", items = listOf("cape"), background = "city", emote = "star"),
        AvatarEmotion.GLITCHED to Face("surprised", "dizzy", background = "cyber", emote = "bolt"),
        AvatarEmotion.MAGIC to Face("happy", items = listOf("wizard_hat"), background = "night", emote = "sparkle"),
        AvatarEmotion.SPORTY to Face("happy", items = listOf("propeller_cap"), background = "sunny"),
        AvatarEmotion.SCIENTIST to Face("normal", "squint", background = "hangar"),
        AvatarEmotion.SCARED to Face("surprised", emote = "sweat"),
        AvatarEmotion.WARRIOR to Face("angry", items = listOf("viking_helmet", "shield_L")),
        AvatarEmotion.LOW_BATTERY to Face("sleepy", emote = "battery_low")
    )

    /** Props the AI puts on the face that the robot can wear. */
    private val PROP_ITEMS: Map<PropType, String> = mapOf(
        PropType.CROWN to "crown",
        PropType.SUNGLASSES to "sunglasses",
        PropType.GLASSES_SQUARE to "sunglasses",
        PropType.LAB_GOGGLES to "sunglasses",
        PropType.HEADPHONES to "headphones",
        PropType.GAMING_HEADSET to "headphones",
        PropType.VR_HEADSET to "vr_headset",
        PropType.SNORKEL_MASK to "astronaut_helmet",
        PropType.ASTRONAUT_HELMET to "astronaut_helmet",
        PropType.DEVIL_HORNS to "devil_horns",
        PropType.FEDORA_HAT to "fedora",
        PropType.BUCKET_HAT to "fedora",
        PropType.CHEF_HAT to "chef_hat",
        PropType.PARTY_HAT to "party_hat",
        PropType.SUPERHERO_CAPE to "cape",
        PropType.WIZARD_HAT to "wizard_hat",
        PropType.VIKING_HELMET to "viking_helmet",
        PropType.BACKPACK to "backpack",
        PropType.PASSPORT to "backpack",
        PropType.CAT_PAW to "cat_ears",
        PropType.SHIELD to "shield_L",
        PropType.ROCKET to "jetpack"
    )

    /** Props that are really a feeling over the head. */
    private val PROP_EMOTES: Map<PropType, String> = mapOf(
        PropType.HEARTS to "heart",
        PropType.ROSE to "heart",
        PropType.SPARKLES to "sparkle",
        PropType.PARTY_POPPER to "sparkle",
        PropType.CONFETTI to "sparkle",
        PropType.SWEAT_DROP to "sweat",
        PropType.HEATWAVES to "sweat",
        PropType.QUESTION_MARK to "question",
        PropType.MAGNIFYING_GLASS to "question",
        PropType.EXCLAMATION to "exclaim",
        PropType.WARNING_TRIANGLE to "exclaim",
        PropType.SIREN_LIGHT to "exclaim",
        PropType.ZZZZZ to "zzz",
        PropType.DREAM_CLOUD to "zzz",
        PropType.ANGER_VEIN to "anger",
        PropType.FIRE to "anger",
        PropType.DIZZY_STARS to "dizzy",
        PropType.MUSIC_NOTES to "note",
        PropType.LIGHTNING to "bolt",
        PropType.LIGHTNING_EYES to "bolt",
        PropType.BATTERY_LOW to "battery_low",
        PropType.CLOUD to "rain_cloud",
        PropType.RAIN_DROPS to "rain_cloud",
        PropType.UMBRELLA to "rain_cloud",
        PropType.THOUGHT_BUBBLE to "dots",
        PropType.MOON_STARS to "star",
        PropType.GOLD_COIN to "star",
        PropType.MONEY_BAG to "star",
        PropType.DOLLAR_SIGN to "star"
    )

    private val BACKGROUNDS: Map<BackgroundTheme, String> = mapOf(
        BackgroundTheme.RAINY to "night",
        BackgroundTheme.SUNNY to "sunny",
        BackgroundTheme.NIGHT to "night",
        BackgroundTheme.SAKURA to "sakura",
        BackgroundTheme.MATRIX to "cyber",
        BackgroundTheme.LOVE_BG to "candy",
        BackgroundTheme.THUNDER to "night",
        BackgroundTheme.CYBER_GRID to "cyber",
        BackgroundTheme.SPACE_NEBULA to "space",
        BackgroundTheme.MAGIC_MYSTIC to "night",
        BackgroundTheme.CINEMA_COZY to "city",
        BackgroundTheme.WINTER_BLIZZARD to "snow",
        BackgroundTheme.SUMMER_HEAT to "sunny",
        BackgroundTheme.WARRIOR_DOJO to "hangar",
        BackgroundTheme.PARTY_CONFETTI to "candy",
        BackgroundTheme.GOLDEN_VAULT to "studio",
        BackgroundTheme.SICK_LAB to "hangar",
        BackgroundTheme.SPORTS_ARENA to "studio"
    )

    private val FOREGROUND_EMOTES: Map<ForegroundEffect, String> = mapOf(
        ForegroundEffect.STAR_DUST to "star",
        ForegroundEffect.MAGIC_SPARKLES to "sparkle",
        ForegroundEffect.ELECTRIC_SPARKS to "bolt",
        ForegroundEffect.FALLING_PETALS to "heart",
        ForegroundEffect.CONFETTI_TUMBLE to "sparkle",
        ForegroundEffect.GOLDEN_SHINE to "star",
        ForegroundEffect.HEART_ORBS to "heart",
        ForegroundEffect.RAIN_CONDENSATION to "rain_cloud"
    )

    private val EYE_STYLES: Map<EyeStyle, String> = mapOf(
        EyeStyle.WINK to "wink",
        EyeStyle.HEART to "love",
        EyeStyle.CRYING to "cry",
        EyeStyle.STAR to "surprised",
        EyeStyle.QUESTION to "squint",
        EyeStyle.CROSS to "dizzy",
        EyeStyle.SPIRAL to "dizzy",
        EyeStyle.HAPPY to "happy",
        EyeStyle.ANGRY to "angry",
        EyeStyle.SLEEPY to "sleepy",
        EyeStyle.CURIOUS to "surprised",
        EyeStyle.LAUGHING to "happy",
        EyeStyle.FOCUSED to "squint",
        EyeStyle.EXCITED to "happy",
        EyeStyle.SHY to "happy",
        EyeStyle.SHOCK to "surprised"
    )

    /** The robot's look for the app's state: the emotion's face, then the AI's props and backdrop on top. */
    fun look(state: AvatarState): AvatarLook {
        val face = FACES[state.emotion] ?: Face("normal")
        val fs = state.faceState
        val props = fs.props
        val eyes = when {
            state.isDizzy -> "dizzy"
            fs.eyeStyle != EyeStyle.DEFAULT -> EYE_STYLES[fs.eyeStyle] ?: face.eyes
            else -> face.eyes
        }
        val items = (face.items + props.mapNotNull { PROP_ITEMS[it] }).distinct()
        val background = BACKGROUNDS[fs.backgroundTheme] ?: face.background
        val emote = when {
            state.isDizzy -> "dizzy"
            else -> props.firstNotNullOfOrNull { PROP_EMOTES[it] }
                ?: face.emote.ifEmpty { FOREGROUND_EMOTES[fs.foregroundEffect] ?: "" }
        }
        return AvatarLook(face.emotion, eyes, items, background, emote)
    }

    /** A one-off effect as an emotion starts (confetti for a party …), or null. */
    fun entryEffect(emotion: AvatarEmotion): String? = when (emotion) {
        AvatarEmotion.PARTY, AvatarEmotion.EXCITED -> "confetti"
        AvatarEmotion.CAMERA_MODE -> "flash"
        AvatarEmotion.LOVE, AvatarEmotion.ROMANTIC -> "big_heart"
        AvatarEmotion.LOW_BATTERY -> "battery"
        else -> null
    }

    /** The robot's move for a face gesture the AI asked for, or null. */
    fun gestureFidget(gesture: GestureType): String? = when (gesture) {
        GestureType.TILT_LEFT, GestureType.TILT_RIGHT -> "tilt"
        GestureType.BOUNCE, GestureType.JUMP -> "hop"
        GestureType.WOBBLE, GestureType.SHAKE -> "wiggle"
        GestureType.NOD -> "nod"
        GestureType.IDLE -> null
    }

    /** The idle screensaver trick (the 2D eyes' ping-pong etc.) becomes one of the robot's cheeky moves. */
    fun trickFidget(trick: EyeTrickState): String? = when (trick) {
        EyeTrickState.NONE -> null
        EyeTrickState.PING_PONG_BOUNCE -> "peek"
        EyeTrickState.TIRED_BOUNCE -> "wiggle"
        EyeTrickState.SNOOKER_SHOT -> "spin"
    }

    /** Every emotion the mapper knows (tests: all 58 are covered). */
    internal val mappedEmotions: Set<AvatarEmotion> get() = FACES.keys
}

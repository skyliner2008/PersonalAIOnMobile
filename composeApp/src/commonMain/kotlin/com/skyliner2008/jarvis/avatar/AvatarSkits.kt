package com.skyliner2008.jarvis.avatar

import com.skyliner2008.jarvis.pet.SceneCue
import com.skyliner2008.jarvis.sound.RobotSound
import com.skyliner2008.jarvis.sound.RobotSound.*
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion

/**
 * A short skit the Godot robot plays (pet_godot/scenes/<id>.json, 10 s meme scenes). The app plays
 * its sound cues on the same timeline (the engine has no sound of its own).
 */
data class AvatarSkit(
    val id: String,
    val nameTh: String,
    /** the pet's emotion while it plays (the app's state, e.g. for the persona) */
    val emotion: AvatarEmotion,
    val durationMs: Long,
    /** voice / text words that ask for it (lower case) */
    val keywords: List<String>,
    val cues: List<SceneCue>
) {
    /** RobotFaceState.sceneName while it plays */
    val sceneName: String get() = SCENE_PREFIX + id

    companion object {
        const val SCENE_PREFIX = "godot:"
    }
}

object AvatarSkits {
    private fun cues(vararg c: Pair<Long, RobotSound>) = c.map { SceneCue(it.first, it.second) }

    // cue times follow the beats in the scene files (t seconds -> ms)
    val IDLE_PLAY = AvatarSkit("idle_play", "ว่างงาน เล่นคนเดียว", AvatarEmotion.IDLE, 10_000,
        listOf("ว่างงาน", "เล่นคนเดียว", "idle_play", "play alone"),
        cues(1500L to CONFUSED, 2600L to HAPPY, 2700L to POP, 3000L to WHOOSH, 3200L to MELODY,
            6100L to WHOOSH, 7000L to GIGGLE, 7800L to SPARKLE))
    val BORED = AvatarSkit("bored", "เบื่อ", AvatarEmotion.BORED, 10_000,
        listOf("เบื่อ", "bored"),
        cues(1200L to YAWN, 2600L to WHOOSH, 4800L to WHOOSH, 6680L to POP, 7080L to POP, 7700L to SURPRISE))
    // missile words ("ยิงจรวด", "missile") stay with the ANGRY_MISSILE scene, which plays this skit
    val ANGRY = AvatarSkit("angry", "โกรธ ยิงจรวด", AvatarEmotion.ENRAGED, 10_000,
        listOf("โกรธ", "angry"),
        cues(0L to ALARM, 1000L to ALARM, 1100L to SIZZLE, 2300L to WHOOSH, 3200L to POWER_UP,
            3900L to MISSILE_LAUNCH, 4400L to MISSILE_LAUNCH, 5000L to MISSILE_LAUNCH, 5900L to EXPLOSION,
            6500L to SIZZLE))
    val LOVE = AvatarSkit("love", "ความรัก", AvatarEmotion.LOVE, 10_000,
        listOf("ความรัก", "ตกหลุมรัก", "love"),
        cues(0L to HEARTBEAT, 1000L to HEARTBEAT, 2000L to PURR, 3600L to BUBBLE_POP, 4400L to POP,
            4600L to GIGGLE, 6400L to HAPPY, 7400L to PURR))
    val THINKING = AvatarSkit("thinking", "กำลังคิด", AvatarEmotion.THINKING, 10_000,
        listOf("กำลังคิด", "ครุ่นคิด", "ใช้ความคิด", "thinking"),
        cues(300L to CONFUSED, 1000L to TYPING, 2000L to TYPING, 4400L to CONFUSED, 6300L to SPARKLE,
            6350L to HAPPY, 7300L to WHOOSH, 8200L to SPARKLE))
    val CURIOUS = AvatarSkit("curious", "สงสัย", AvatarEmotion.CONFUSED, 10_000,
        listOf("สงสัย", "ค้นหา", "curious"),
        cues(0L to CONFUSED, 2400L to SURPRISE, 3400L to SCAN_RADAR, 3480L to POP, 4000L to SURPRISE,
            5000L to CONFUSED, 8200L to GIGGLE))
    val SLEEPY = AvatarSkit("sleepy", "ง่วงนอน", AvatarEmotion.SLEEPING, 10_000,
        listOf("ง่วงนอน", "ง่วง", "sleepy"),
        cues(200L to YAWN, 1200L to BUBBLE_POP, 2600L to SNORE, 4400L to SURPRISE, 6200L to YAWN, 6600L to SNORE))
    val SAD = AvatarSkit("sad", "เศร้า", AvatarEmotion.SAD, 10_000,
        listOf("เศร้า", "ร้องไห้", "sad", "cry"),
        cues(0L to SURPRISE, 800L to RAIN, 1600L to SOB, 3600L to SOB, 5400L to SURPRISE, 5900L to HAPPY,
            6100L to SPARKLE, 7400L to PURR))
    val LOW_BATTERY = AvatarSkit("low_battery", "แบตใกล้หมด", AvatarEmotion.LOW_BATTERY, 10_000,
        listOf("แบตใกล้หมด", "แบตหมด", "low battery", "low_battery"),
        cues(0L to ALARM, 1600L to YAWN, 3900L to POWER_UP, 4000L to ZAP, 5400L to HAPPY, 6300L to WHOOSH,
            8900L to SPARKLE))
    val PARTY = AvatarSkit("party", "ปาร์ตี้", AvatarEmotion.PARTY, 10_000,
        listOf("ปาร์ตี้", "ฉลอง", "เต้น", "party", "dance"),
        cues(0L to FANFARE, 200L to MELODY, 2700L to POP, 3500L to POP, 4400L to MELODY, 8100L to HAPPY,
            8300L to FANFARE))
    val HERO = AvatarSkit("hero", "ฮีโร่", AvatarEmotion.HERO, 10_000,
        listOf("ฮีโร่", "ซูเปอร์ฮีโร่", "hero"),
        cues(0L to FANFARE, 600L to POWER_UP, 1200L to WHOOSH, 4300L to WHOOSH, 6350L to EXPLOSION,
            6700L to HAPPY, 7200L to POWER_UP, 7800L to ZAP, 8200L to SPARKLE))
    val DIZZY = AvatarSkit("dizzy", "มึนหัว", AvatarEmotion.DIZZY, 10_000,
        listOf("มึนหัว", "เวียนหัว", "มึน", "dizzy"),
        cues(0L to WHOOSH, 1700L to CONFUSED, 4250L to POP, 4300L to SURPRISE, 7200L to HAPPY, 7300L to GIGGLE))

    val ALL = listOf(IDLE_PLAY, BORED, ANGRY, LOVE, THINKING, CURIOUS, SLEEPY, SAD, LOW_BATTERY, PARTY, HERO, DIZZY)

    fun forId(id: String): AvatarSkit? = ALL.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }

    /** A skit from a voice / text request: its id first, then its words (the longest word wins). */
    fun resolveKeyword(text: String): AvatarSkit? {
        val clean = text.trim().lowercase()
        if (clean.isEmpty()) return null
        forId(clean.replace(" ", "_"))?.let { return it }
        return ALL.flatMap { skit -> skit.keywords.map { it to skit } }
            .filter { (kw, _) -> clean.contains(kw) }
            .maxByOrNull { (kw, _) -> kw.length }
            ?.second
    }

    /**
     * The skit the robot plays for a scene the pet brain started (RobotFaceState.sceneName): a skit of
     * its own ("godot:<id>"), or the closest skit for one of the old story scenes; null = no skit, the
     * robot just shows the scene's emotion and props.
     */
    fun forSceneName(sceneName: String): AvatarSkit? {
        if (sceneName.startsWith(AvatarSkit.SCENE_PREFIX)) return forId(sceneName.removePrefix(AvatarSkit.SCENE_PREFIX))
        return when (sceneName.lowercase()) {
            "angry_missile" -> ANGRY
            "super_love" -> LOVE
            "dramatic_cry" -> SAD
            "celebration", "meme_thug_life" -> PARTY
            "comedy_soul_out" -> LOW_BATTERY
            "comedy_thunder" -> DIZZY
            else -> null
        }
    }
}

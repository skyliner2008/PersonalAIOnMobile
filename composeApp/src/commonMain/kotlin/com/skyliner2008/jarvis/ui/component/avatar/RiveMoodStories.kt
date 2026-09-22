package com.skyliner2008.jarvis.ui.component.avatar

import com.skyliner2008.jarvis.pet.SceneCue
import com.skyliner2008.jarvis.sound.RobotSound
import com.skyliner2008.jarvis.sound.RobotSound.*

/**
 * Jelly-eye mood stories in `avatar.riv` (seq 82–100) — บทอยู่ใน
 * `.obsidian-wiki/02_Components/Pet_Mood_Scripts.md`.
 *
 * เมื่ออารมณ์ของสัตว์เลี้ยงเปลี่ยน Engine Rive จะเล่นเรื่องสั้น 7 วินาทีของอารมณ์นั้น
 * (สุ่มจากหลายแบบถ้ามี) พร้อมเสียงตามไทม์ไลน์ แล้วกลับไปแสดงหน้าของอารมณ์ตามปกติ
 * ตอนว่างนานๆ จะสุ่มเล่นเรื่อง "ว่างงาน" เอง
 */
data class RiveMoodStory(
    val name: String,
    val seq: Int,
    val cues: List<SceneCue>,
    val durationMs: Long = RiveMoodStories.STORY_MS,
    val nameTh: String = name,
    /** อารมณ์ที่ตั้งให้สัตว์เลี้ยงระหว่างเล่น (Engine Canvas ใช้แสดงแทน) */
    val emotion: AvatarEmotion = AvatarEmotion.IDLE,
    /** คำสั่งเสียง/ข้อความที่เรียกเรื่องนี้ (ตัวพิมพ์เล็ก) */
    val keywords: List<String> = emptyList()
)

object RiveMoodStories {
    const val STORY_MS = 7000L
    /** ค้างเฟรมสุดท้ายไว้สักพักก่อนคืนหน้าอารมณ์ปกติ */
    const val HOLD_MS = 1500L
    /** เรื่องเดิมจะไม่เล่นซ้ำภายในช่วงนี้ (อารมณ์เด้งไปมาเร็วๆ) */
    const val REPEAT_COOLDOWN_MS = 20_000L
    const val IDLE_MIN_MS = 30_000L
    const val IDLE_MAX_MS = 50_000L

    private fun cues(vararg c: Pair<Long, RobotSound>) = c.map { SceneCue(it.first, it.second) }

    val IDLE_BALL = RiveMoodStory("MoodIdleBall", 82, cues(
        2000L to WHOOSH, 2667L to GAME_BLIP, 3267L to GAME_BLIP, 3867L to GAME_BLIP, 4467L to GAME_BLIP,
        5000L to GAME_BLIP, 5050L to WHOOSH, 5833L to YAWN),
        nameTh = "ว่างงาน: เล่นลูกบอล", emotion = AvatarEmotion.IDLE, keywords = listOf("ลูกบอล", "เล่นบอล", "โหม่งบอล", "ball", "idle_ball"))
    val YOYO = RiveMoodStory("MoodYoYo", 83, cues(
        500L to WHOOSH, 3200L to GAME_BLIP, 3600L to GAME_BLIP, 4000L to GAME_BLIP, 4400L to GAME_BLIP,
        4800L to GAME_BLIP, 5200L to WHOOSH, 5600L to POP, 5700L to SURPRISE, 6267L to HAPPY),
        nameTh = "ว่างงาน: โยโย่", emotion = AvatarEmotion.IDLE, keywords = listOf("โยโย่", "yoyo", "yo-yo", "yo_yo"))
    val STARS = RiveMoodStory("MoodStars", 84, cues(
        500L to SPARKLE, 2500L to SPARKLE, 4167L to POP, 5533L to WHOOSH, 5750L to PURR),
        nameTh = "ว่างงาน: นับดาว", emotion = AvatarEmotion.IDLE, keywords = listOf("นับดาว", "ดูดาว", "star_count", "counting stars", "stars"))
    val SHOCKED = RiveMoodStory("MoodShocked", 85, cues(
        2000L to WHOOSH, 2200L to SURPRISE, 2367L to POP, 4083L to ALARM, 5567L to WHOOSH, 6033L to POP,
        6333L to CONFUSED),
        nameTh = "ตกใจ / ช็อก", emotion = AvatarEmotion.SURPRISED, keywords = listOf("ตกใจ", "ช็อก", "แมงมุม", "shock", "spider", "scared"))
    val SAD = RiveMoodStory("MoodSad", 86, cues(
        1000L to SOB, 3000L to RAIN, 5067L to POP, 6067L to POP, 6167L to SOB),
        nameTh = "เศร้า", emotion = AvatarEmotion.SAD, keywords = listOf("เศร้า", "หงอย", "น้ำตาคลอ", "mood_sad"))
    val CURIOUS = RiveMoodStory("MoodCurious", 87, cues(
        200L to CONFUSED, 2067L to WHOOSH, 3100L to POP, 3333L to SCAN_RADAR, 5200L to BUBBLE_POP,
        5600L to BUBBLE_POP, 6200L to SPARKLE, 6433L to HAPPY),
        nameTh = "สงสัย / ค้นหา", emotion = AvatarEmotion.CONFUSED, keywords = listOf("สงสัย", "แว่นขยาย", "ค้นหา", "curious", "magnifier"))
    val ANGRY_MISSILE = RiveMoodStory("MoodAngryMissile", 88, cues(
        0L to ALARM, 1000L to ALARM, 2333L to POWER_UP, 3167L to MISSILE_LAUNCH, 3583L to MISSILE_LAUNCH,
        4000L to MISSILE_LAUNCH, 4417L to MISSILE_LAUNCH, 5000L to EXPLOSION, 5333L to EXPLOSION, 5667L to ZAP),
        nameTh = "โกรธ: ขีปนาวุธ", emotion = AvatarEmotion.ENRAGED, keywords = listOf("ขีปนาวุธ", "red_glare", "angry_missile_mood"))
    val FUMING = RiveMoodStory("MoodFuming", 89, cues(
        0L to ALARM, 1667L to SIZZLE, 3000L to POWER_UP, 5000L to EXPLOSION, 5100L to SIZZLE, 6000L to SIZZLE),
        nameTh = "โกรธ: ควันออกหู", emotion = AvatarEmotion.ANGRY, keywords = listOf("ควันออกหู", "หัวร้อน", "fuming", "fume"))
    val GLITCH = RiveMoodStory("MoodGlitch", 90, cues(
        167L to ZAP, 867L to ZAP, 1500L to POP, 1967L to ZAP, 3100L to ALARM, 5200L to EXPLOSION, 5667L to ZAP),
        nameTh = "โกรธ: กลิตช์", emotion = AvatarEmotion.ANGRY, keywords = listOf("กลิตช์", "ระบบรวน", "glitch", "furious"))
    val THINKING = RiveMoodStory("MoodThinking", 91, cues(
        333L to CONFUSED, 1000L to TYPING, 2000L to TYPING, 3167L to SCAN_RADAR, 5000L to POP, 5200L to SPARKLE),
        nameTh = "กำลังใช้ความคิด", emotion = AvatarEmotion.THINKING, keywords = listOf("ครุ่นคิด", "ใช้ความคิด", "คิดออก", "thinking"))
    val HAPPY_STORY = RiveMoodStory("MoodHappy", 92, cues(
        0L to HAPPY, 1000L to SPARKLE, 3067L to MELODY, 5500L to POP, 6033L to HAPPY),
        nameTh = "มีความสุข", emotion = AvatarEmotion.HAPPY, keywords = listOf("มีความสุข", "ดีใจ", "happy"))
    val IN_LOVE = RiveMoodStory("MoodInLove", 93, cues(
        0L to HEARTBEAT, 1000L to HEARTBEAT, 2000L to HEARTBEAT, 3067L to WHOOSH, 3667L to BUBBLE_POP,
        4333L to BUBBLE_POP, 5067L to PURR),
        nameTh = "มีความรัก", emotion = AvatarEmotion.LOVE, keywords = listOf("ตกหลุมรัก", "มีความรัก", "in love", "in_love", "inlove"))
    val GLAD = RiveMoodStory("MoodGlad", 94, cues(
        0L to SPARKLE, 3100L to POP, 3200L to FANFARE, 5000L to HAPPY, 5667L to SPARKLE),
        nameTh = "ยินดี", emotion = AvatarEmotion.EXCITED, keywords = listOf("ปลื้ม", "glad"))
    val AWESOME = RiveMoodStory("MoodAwesome", 95, cues(
        333L to WHOOSH, 1067L to POP, 1500L to SPARKLE, 3167L to WHOOSH, 3533L to POP, 3833L to FANFARE,
        5000L to MELODY),
        nameTh = "เยี่ยมยอด", emotion = AvatarEmotion.WINK, keywords = listOf("เยี่ยม", "สุดยอด", "นิ้วโป้ง", "awesome", "thumbs up"))
    val SHY_STORY = RiveMoodStory("MoodShy", 96, cues(
        167L to BUBBLE_POP, 667L to PURR, 3167L to CONFUSED, 5100L to WHOOSH, 5500L to POP, 6167L to HAPPY),
        nameTh = "เขิน", emotion = AvatarEmotion.SHY, keywords = listOf("เขิน", "shy"))
    val EMBARRASSED = RiveMoodStory("MoodEmbarrassed", 97, cues(
        0L to SURPRISE, 500L to WHOOSH, 3167L to POP, 3567L to POP, 5083L to WHOOSH, 5500L to RAIN,
        6500L to BUBBLE_POP),
        nameTh = "อาย", emotion = AvatarEmotion.SHY, keywords = listOf("อาย", "ขายหน้า", "embarrass"))
    val SHOW_OFF = RiveMoodStory("MoodShowOff", 98, cues(
        333L to WHOOSH, 1067L to POP, 1333L to SPARKLE, 3167L to WHOOSH, 3267L to SPARKLE, 3567L to WHOOSH,
        3967L to WHOOSH, 4367L to WHOOSH, 5000L to POWER_UP, 5667L to MELODY),
        nameTh = "เก๊กท่า", emotion = AvatarEmotion.SNEAKY, keywords = listOf("เก๊ก", "เก็ก", "show off", "show_off", "showoff"))
    val LISTENING_STORY = RiveMoodStory("MoodListening", 99, cues(
        333L to WHOOSH, 1100L to POP, 1667L to MELODY, 3000L to MELODY, 4333L to MELODY, 5667L to MELODY),
        nameTh = "กำลังฟัง", emotion = AvatarEmotion.MUSIC, keywords = listOf("กำลังฟัง", "listening"))
    val ARROGANT = RiveMoodStory("MoodArrogant", 100, cues(
        500L to WHOOSH, 3167L to SPARKLE, 3567L to POP, 3833L to WHOOSH, 5233L to WHOOSH, 5500L to CONFUSED,
        6000L to POP),
        nameTh = "เย่อหยิ่ง", emotion = AvatarEmotion.POUT, keywords = listOf("เย่อหยิ่ง", "หยิ่ง", "เชิด", "arrogant"))

    val ALL = listOf(IDLE_BALL, YOYO, STARS, SHOCKED, SAD, CURIOUS, ANGRY_MISSILE, FUMING, GLITCH, THINKING,
        HAPPY_STORY, IN_LOVE, GLAD, AWESOME, SHY_STORY, EMBARRASSED, SHOW_OFF, LISTENING_STORY, ARROGANT)

    val IDLE_VARIANTS = listOf(IDLE_BALL, YOYO, STARS)

    /**
     * เรื่องที่เล่นได้เมื่อเข้าอารมณ์นี้ — ว่าง = แสดงหน้าปกติอย่างเดียว
     * (LISTENING / SPEAKING / SLEEPING ของเสียงสดไม่เล่นเรื่อง เพื่อไม่ทับการคุยจริง)
     */
    fun variantsFor(emotion: AvatarEmotion): List<RiveMoodStory> = when (emotion) {
        AvatarEmotion.BORED -> IDLE_VARIANTS
        AvatarEmotion.SURPRISED, AvatarEmotion.SCARED -> listOf(SHOCKED)
        AvatarEmotion.SAD, AvatarEmotion.CRYING -> listOf(SAD)
        AvatarEmotion.CONFUSED, AvatarEmotion.PUZZLED -> listOf(CURIOUS)
        AvatarEmotion.ANGRY -> listOf(FUMING, GLITCH)
        AvatarEmotion.ENRAGED -> listOf(ANGRY_MISSILE, GLITCH)
        AvatarEmotion.THINKING -> listOf(THINKING)
        AvatarEmotion.HAPPY, AvatarEmotion.LAUGHING -> listOf(HAPPY_STORY)
        AvatarEmotion.LOVE, AvatarEmotion.ROMANTIC -> listOf(IN_LOVE)
        AvatarEmotion.EXCITED -> listOf(GLAD)
        AvatarEmotion.WINK -> listOf(AWESOME, SHOW_OFF)
        AvatarEmotion.SNEAKY -> listOf(SHOW_OFF)
        AvatarEmotion.SHY -> listOf(SHY_STORY, EMBARRASSED)
        AvatarEmotion.MUSIC -> listOf(LISTENING_STORY)
        AvatarEmotion.POUT -> listOf(ARROGANT)
        else -> emptyList()
    }

    /** เรื่องจากชื่อ (เช่น "MoodYoYo", ไม่สนตัวพิมพ์) */
    fun forName(name: String): RiveMoodStory? = ALL.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    /** เรื่องจากคำสั่งเสียง/ข้อความ — ชื่อเรื่องตรงตัวก่อน แล้วค่อยคำสำคัญ (คำที่ยาวกว่าชนะ) */
    fun resolveKeyword(text: String): RiveMoodStory? {
        val clean = text.trim().lowercase()
        if (clean.isEmpty()) return null
        forName(clean.replace(" ", ""))?.let { return it }
        return ALL.flatMap { story -> story.keywords.map { it to story } }
            .filter { (kw, _) -> clean.contains(kw) }
            .maxByOrNull { (kw, _) -> kw.length }
            ?.second
    }

    /** สุ่มแบบ ไม่ซ้ำกับเรื่องล่าสุดถ้ามีตัวเลือกอื่น */
    fun pick(variants: List<RiveMoodStory>, last: RiveMoodStory?, random: kotlin.random.Random = kotlin.random.Random): RiveMoodStory? {
        if (variants.isEmpty()) return null
        val pool = variants.filter { it != last }.ifEmpty { variants }
        return pool[random.nextInt(pool.size)]
    }
}

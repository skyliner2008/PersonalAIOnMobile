package com.skyliner2008.jarvis.pet

import com.skyliner2008.jarvis.sound.RobotSound
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme
import com.skyliner2008.jarvis.ui.component.avatar.EyeStyle
import com.skyliner2008.jarvis.ui.component.avatar.GestureType
import com.skyliner2008.jarvis.ui.component.avatar.PropType
import com.skyliner2008.jarvis.ui.component.avatar.RobotFaceState
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

/**
 * PetSceneArchetype — ต้นแบบฉากอัจฉริยะ 15+ รูปแบบ ครอบคลุม 4 หมวดหมู่
 */
enum class PetSceneArchetype(val displayNameTh: String, val category: String) {
    // ─── 1. หมวดกิจกรรมในชีวิตประจำวัน (Activity Scenes) ───
    EATING("กินอาหารแสนอร่อย", "กิจกรรม"),
    DRINKING("จิบเครื่องดื่มชื่นใจ", "กิจกรรม"),
    BATH_CLEAN("อาบน้ำฟองสบู่", "กิจกรรม"),
    PLAY_GAMING("เล่นเกมสนุกสนาน", "กิจกรรม"),
    STUDY_WORK("อ่านหนังสือ / ทำงาน", "กิจกรรม"),
    RAIN_UMBRELLA("กางร่มกันฝน", "กิจกรรม"),

    // ─── 2. หมวดมีมไวรัล (Viral Meme Scenes) ───
    MEME_THUG_LIFE("แว่นดำ Thug Life (Deal With It)", "มีม"),
    MEME_RICH("เศรษฐีคริปโตเงินล้นจอ", "มีม"),
    MEME_ROYAL("ราชา / เจ้าหญิงมงกุฎทอง", "มีม"),

    // ─── 3. หมวดตลกขบขัน (Comedy Scenes) ───
    COMEDY_FIRE("ไฟไหม้ตูดวิ่งพล่าน", "ตลก"),
    COMEDY_THUNDER("โดนฟ้าผ่าตัวสั่น", "ตลก"),
    COMEDY_SOUL_OUT("เหนื่อยจนวิญญาณหลุด", "ตลก"),

    // ─── 4. หมวดอารมณ์ขั้นสุด (Dramatic Emotion Scenes) ───
    ANGRY_MISSILE("โกรธจัด ยิงจรวดถล่มหน้าจอ", "อารมณ์"),
    SUPER_LOVE("ปิ๊งรักคลั่งรักหัวใจพุ่ง", "อารมณ์"),
    DRAMATIC_CRY("ร้องไห้น้ำตานองอกหัก", "อารมณ์"),
    CELEBRATION("งานเลี้ยงเฉลิมฉลอง", "อารมณ์"),

    // ─── 5. หมวดความบันเทิง (Entertainment Scenes) ───
    VR_MODE("ใส่แว่น VR ท่องโลกเสมือน", "บันเทิง"),
    MUSIC("ฟังเพลงโยกหัว", "บันเทิง")
}

/** เสียงประกอบฉากที่เล่น ณ เวลา [atMs] นับจากเริ่มฉาก (ตรงกับบทใน Pet_Scene_Scripts.md) */
data class SceneCue(val atMs: Long, val sound: RobotSound)

/**
 * PetSceneSpec — รายละเอียดการจัดฉาก แสง สี เสียง ท่าทาง และไอเทม
 */
data class PetSceneSpec(
    val archetype: PetSceneArchetype,
    val nameTh: String,
    val emotion: AvatarEmotion,
    val eyeStyle: EyeStyle = EyeStyle.DEFAULT,
    val gesture: GestureType = GestureType.BOUNCE,
    val defaultProps: List<PropType>,
    val itemPool: List<PropType> = emptyList(),
    val sound: RobotSound,
    val background: BackgroundTheme? = null, // null = ปรับตามเวลาจริง (Time-Aware)
    val durationMs: Long = 4000L,
    val triggerMissileBarrage: Boolean = false,
    /** เสียงตามไทม์ไลน์ของบท; ว่าง = เล่น [sound] ครั้งเดียวตอนเริ่ม */
    val cues: List<SceneCue> = emptyList()
)

/**
 * PetSceneEngine — ตัวควบคุมและกำกับฉากอัจฉริยะ (Smart Scene Director)
 */
object PetSceneEngine {

    /**
     * คลัง Spec ของฉากทั้ง 15 รูปแบบ
     */
    private val BASE_SPECS: Map<PetSceneArchetype, PetSceneSpec> = mapOf(
        PetSceneArchetype.EATING to PetSceneSpec(
            archetype = PetSceneArchetype.EATING,
            nameTh = "กินอาหารแสนอร่อย",
            emotion = AvatarEmotion.HAPPY,
            eyeStyle = EyeStyle.DEFAULT,
            gesture = GestureType.BOUNCE,
            defaultProps = listOf(PropType.BURGER),
            itemPool = listOf(PropType.BURGER, PropType.PIZZA, PropType.CAKE, PropType.ICE_CREAM, PropType.POPCORN),
            sound = RobotSound.CRUNCH_EAT,
            durationMs = 4200L
        ),
        PetSceneArchetype.DRINKING to PetSceneSpec(
            archetype = PetSceneArchetype.DRINKING,
            nameTh = "จิบเครื่องดื่มชื่นใจ",
            emotion = AvatarEmotion.HAPPY,
            eyeStyle = EyeStyle.WINK,
            gesture = GestureType.IDLE,
            defaultProps = listOf(PropType.COFFEE),
            itemPool = listOf(PropType.COFFEE, PropType.BOBA_TEA, PropType.TEA_CUP, PropType.BEER),
            sound = RobotSound.PURR,
            durationMs = 3800L
        ),
        PetSceneArchetype.BATH_CLEAN to PetSceneSpec(
            archetype = PetSceneArchetype.BATH_CLEAN,
            nameTh = "อาบน้ำฟองสบู่",
            emotion = AvatarEmotion.HAPPY,
            eyeStyle = EyeStyle.DEFAULT,
            gesture = GestureType.BOUNCE,
            defaultProps = listOf(PropType.SWEAT_DROP, PropType.SPARKLES),
            sound = RobotSound.BUBBLE_POP,
            background = BackgroundTheme.RAINY,
            durationMs = 4000L
        ),
        PetSceneArchetype.PLAY_GAMING to PetSceneSpec(
            archetype = PetSceneArchetype.PLAY_GAMING,
            nameTh = "เล่นเกมสนุกสนาน",
            emotion = AvatarEmotion.EXCITED,
            eyeStyle = EyeStyle.STAR,
            gesture = GestureType.JUMP,
            defaultProps = listOf(PropType.GAMING_CONTROLLER, PropType.SPARKLES),
            sound = RobotSound.BELL_TOY,
            durationMs = 4000L
        ),
        PetSceneArchetype.STUDY_WORK to PetSceneSpec(
            archetype = PetSceneArchetype.STUDY_WORK,
            nameTh = "อ่านหนังสือ / ทำงาน",
            emotion = AvatarEmotion.THINKING,
            eyeStyle = EyeStyle.DEFAULT,
            gesture = GestureType.TILT_RIGHT,
            defaultProps = listOf(PropType.LAPTOP),
            itemPool = listOf(PropType.LAPTOP, PropType.BOOK, PropType.GEARS),
            sound = RobotSound.ACKNOWLEDGE,
            durationMs = 3800L
        ),
        PetSceneArchetype.RAIN_UMBRELLA to PetSceneSpec(
            archetype = PetSceneArchetype.RAIN_UMBRELLA,
            nameTh = "กางร่มกันฝนแสนน่ารัก",
            emotion = AvatarEmotion.HAPPY,
            eyeStyle = EyeStyle.DEFAULT,
            gesture = GestureType.IDLE,
            defaultProps = listOf(PropType.UMBRELLA, PropType.RAIN_DROPS),
            sound = RobotSound.PURR,
            background = BackgroundTheme.RAINY,
            durationMs = 4500L
        ),
        PetSceneArchetype.MEME_THUG_LIFE to PetSceneSpec(
            archetype = PetSceneArchetype.MEME_THUG_LIFE,
            nameTh = "แว่นดำ Thug Life (Deal With It)",
            emotion = AvatarEmotion.HAPPY,
            eyeStyle = EyeStyle.WINK,
            gesture = GestureType.IDLE,
            defaultProps = listOf(PropType.SUNGLASSES),
            sound = RobotSound.ACKNOWLEDGE,
            durationMs = 4200L
        ),
        PetSceneArchetype.MEME_RICH to PetSceneSpec(
            archetype = PetSceneArchetype.MEME_RICH,
            nameTh = "เศรษฐีคริปโตเงินล้นจอ",
            emotion = AvatarEmotion.EXCITED,
            eyeStyle = EyeStyle.STAR,
            gesture = GestureType.BOUNCE,
            defaultProps = listOf(PropType.GOLD_COIN, PropType.SPARKLES),
            sound = RobotSound.SPARKLE,
            durationMs = 4000L
        ),
        PetSceneArchetype.MEME_ROYAL to PetSceneSpec(
            archetype = PetSceneArchetype.MEME_ROYAL,
            nameTh = "ราชา / เจ้าหญิงมงกุฎทอง",
            emotion = AvatarEmotion.LOVE,
            eyeStyle = EyeStyle.DEFAULT,
            gesture = GestureType.NOD,
            defaultProps = listOf(PropType.CROWN, PropType.PARTY_POPPER),
            sound = RobotSound.SPARKLE,
            background = BackgroundTheme.SAKURA,
            durationMs = 4000L
        ),
        PetSceneArchetype.COMEDY_FIRE to PetSceneSpec(
            archetype = PetSceneArchetype.COMEDY_FIRE,
            nameTh = "ไฟไหม้ตูดวิ่งพล่าน",
            emotion = AvatarEmotion.SURPRISED,
            eyeStyle = EyeStyle.CROSS,
            gesture = GestureType.SHAKE,
            defaultProps = listOf(PropType.FIRE),
            sound = RobotSound.ALARM,
            durationMs = 4000L
        ),
        PetSceneArchetype.COMEDY_THUNDER to PetSceneSpec(
            archetype = PetSceneArchetype.COMEDY_THUNDER,
            nameTh = "โดนฟ้าผ่าตัวสั่น",
            emotion = AvatarEmotion.CONFUSED,
            eyeStyle = EyeStyle.SPIRAL,
            gesture = GestureType.SHAKE,
            defaultProps = listOf(PropType.LIGHTNING),
            sound = RobotSound.EXPLOSION,
            background = BackgroundTheme.THUNDER,
            durationMs = 4200L
        ),
        PetSceneArchetype.COMEDY_SOUL_OUT to PetSceneSpec(
            archetype = PetSceneArchetype.COMEDY_SOUL_OUT,
            nameTh = "เหนื่อยจนวิญญาณหลุด",
            emotion = AvatarEmotion.SAD,
            eyeStyle = EyeStyle.DEFAULT,
            gesture = GestureType.IDLE,
            defaultProps = listOf(PropType.SKULL, PropType.SWEAT_DROP),
            sound = RobotSound.YAWN,
            durationMs = 4000L
        ),
        PetSceneArchetype.ANGRY_MISSILE to PetSceneSpec(
            archetype = PetSceneArchetype.ANGRY_MISSILE,
            nameTh = "โกรธจัด ยิงจรวดถล่มหน้าจอ",
            emotion = AvatarEmotion.ANGRY,
            eyeStyle = EyeStyle.DEFAULT,
            gesture = GestureType.SHAKE,
            defaultProps = emptyList(),
            sound = RobotSound.MISSILE_LAUNCH,
            durationMs = 5000L,
            triggerMissileBarrage = true
        ),
        PetSceneArchetype.SUPER_LOVE to PetSceneSpec(
            archetype = PetSceneArchetype.SUPER_LOVE,
            nameTh = "ปิ๊งรักคลั่งรักหัวใจพุ่ง",
            emotion = AvatarEmotion.LOVE,
            eyeStyle = EyeStyle.HEART,
            gesture = GestureType.WOBBLE,
            defaultProps = listOf(PropType.HEARTS),
            sound = RobotSound.PURR,
            background = BackgroundTheme.LOVE_BG,
            durationMs = 4200L
        ),
        PetSceneArchetype.DRAMATIC_CRY to PetSceneSpec(
            archetype = PetSceneArchetype.DRAMATIC_CRY,
            nameTh = "ร้องไห้น้ำตานองอกหัก",
            emotion = AvatarEmotion.SAD,
            eyeStyle = EyeStyle.CRYING,
            gesture = GestureType.TILT_LEFT,
            defaultProps = listOf(PropType.TEARS, PropType.BROKEN_HEART),
            sound = RobotSound.CONFUSED,
            background = BackgroundTheme.RAINY,
            durationMs = 4000L
        ),
        PetSceneArchetype.CELEBRATION to PetSceneSpec(
            archetype = PetSceneArchetype.CELEBRATION,
            nameTh = "งานเลี้ยงเฉลิมฉลอง",
            emotion = AvatarEmotion.EXCITED,
            eyeStyle = EyeStyle.DEFAULT,
            gesture = GestureType.JUMP,
            defaultProps = listOf(PropType.PARTY_POPPER, PropType.BALLOONS),
            sound = RobotSound.HAPPY,
            background = BackgroundTheme.SAKURA,
            durationMs = 4500L
        ),
        PetSceneArchetype.VR_MODE to PetSceneSpec(
            archetype = PetSceneArchetype.VR_MODE,
            nameTh = "ใส่แว่น VR ท่องโลกเสมือน",
            emotion = AvatarEmotion.VR_MODE,
            eyeStyle = EyeStyle.DEFAULT,
            gesture = GestureType.IDLE,
            defaultProps = listOf(PropType.VR_HEADSET),
            sound = RobotSound.POWER_UP
        ),
        PetSceneArchetype.MUSIC to PetSceneSpec(
            archetype = PetSceneArchetype.MUSIC,
            nameTh = "ฟังเพลงโยกหัว",
            emotion = AvatarEmotion.MUSIC,
            eyeStyle = EyeStyle.DEFAULT,
            gesture = GestureType.WOBBLE,
            defaultProps = listOf(PropType.HEADPHONES, PropType.MUSIC_NOTES),
            sound = RobotSound.MELODY
        )
    )

    private fun cues(vararg c: Pair<Long, RobotSound>) = c.map { SceneCue(it.first, it.second) }

    /** ความยาวของ story ใน avatar.riv + ช่วงค้างสั้นๆ ก่อนคืนสภาพ */
    private const val SCENE_TAIL_MS = 300L

    /** (ความยาวบท ms, เสียงตามไทม์ไลน์) ต่อฉาก — ต้องตรงกับ SCENE_STORIES ใน build_scene.py */
    private val SCRIPTS: Map<PetSceneArchetype, Pair<Long, List<SceneCue>>> = mapOf(
        PetSceneArchetype.EATING to (8000L to cues(
            600L to RobotSound.WHOOSH, 1400L to RobotSound.SPARKLE, 2400L to RobotSound.POP,
            2900L to RobotSound.CRUNCH_EAT, 3700L to RobotSound.CRUNCH_EAT, 4500L to RobotSound.CRUNCH_EAT,
            5600L to RobotSound.PURR, 6600L to RobotSound.HAPPY)),
        PetSceneArchetype.DRINKING to (7500L to cues(
            500L to RobotSound.WHOOSH, 1300L to RobotSound.POP, 2000L to RobotSound.SLURP,
            3300L to RobotSound.SLURP, 5000L to RobotSound.SPARKLE, 5800L to RobotSound.PURR)),
        PetSceneArchetype.BATH_CLEAN to (8000L to cues(
            600L to RobotSound.WHOOSH, 1200L to RobotSound.RAIN, 2000L to RobotSound.BUBBLE_POP,
            2900L to RobotSound.BUBBLE_POP, 3800L to RobotSound.BUBBLE_POP, 5000L to RobotSound.SPARKLE,
            6000L to RobotSound.HAPPY)),
        PetSceneArchetype.PLAY_GAMING to (8500L to cues(
            500L to RobotSound.WHOOSH, 1200L to RobotSound.POWER_UP, 2000L to RobotSound.GAME_BLIP,
            2700L to RobotSound.GAME_BLIP, 3500L to RobotSound.GAME_BLIP, 4300L to RobotSound.GAME_BLIP,
            5400L to RobotSound.SURPRISE, 6200L to RobotSound.FANFARE, 7200L to RobotSound.HAPPY)),
        PetSceneArchetype.STUDY_WORK to (8000L to cues(
            600L to RobotSound.WHOOSH, 1300L to RobotSound.TYPING, 2200L to RobotSound.TYPING,
            3100L to RobotSound.TYPING, 4300L to RobotSound.CONFUSED, 5300L to RobotSound.SPARKLE,
            6000L to RobotSound.ACKNOWLEDGE)),
        PetSceneArchetype.RAIN_UMBRELLA to (8000L to cues(
            600L to RobotSound.WHOOSH, 1400L to RobotSound.RAIN, 2400L to RobotSound.CONFUSED,
            3400L to RobotSound.POP, 4200L to RobotSound.RAIN, 5400L to RobotSound.PURR,
            7000L to RobotSound.SPARKLE)),
        PetSceneArchetype.MEME_THUG_LIFE to (7500L to cues(
            1200L to RobotSound.WHOOSH, 2400L to RobotSound.POP, 3000L to RobotSound.COIN,
            4200L to RobotSound.MELODY)),
        PetSceneArchetype.MEME_RICH to (8000L to cues(
            600L to RobotSound.COIN, 1400L to RobotSound.COIN, 1700L to RobotSound.COIN,
            2400L to RobotSound.SPARKLE, 3200L to RobotSound.COIN, 4000L to RobotSound.FANFARE,
            6600L to RobotSound.HAPPY)),
        PetSceneArchetype.MEME_ROYAL to (8000L to cues(
            600L to RobotSound.POWER_UP, 1400L to RobotSound.SPARKLE, 3000L to RobotSound.POP,
            3600L to RobotSound.FANFARE, 4600L to RobotSound.MELODY)),
        PetSceneArchetype.COMEDY_FIRE to (7500L to cues(
            800L to RobotSound.SIZZLE, 1600L to RobotSound.SIZZLE, 2200L to RobotSound.ALARM,
            3400L to RobotSound.SURPRISE, 5400L to RobotSound.RAIN, 6200L to RobotSound.CONFUSED)),
        PetSceneArchetype.COMEDY_THUNDER to (7500L to cues(
            600L to RobotSound.WHOOSH, 1600L to RobotSound.ZAP, 2400L to RobotSound.EXPLOSION,
            2500L to RobotSound.ZAP, 3200L to RobotSound.ZAP, 4000L to RobotSound.ZAP,
            5000L to RobotSound.CONFUSED)),
        PetSceneArchetype.COMEDY_SOUL_OUT to (8000L to cues(
            0L to RobotSound.YAWN, 2000L to RobotSound.GHOST, 3400L to RobotSound.GHOST,
            5400L to RobotSound.WHOOSH, 6200L to RobotSound.CONFUSED)),
        PetSceneArchetype.ANGRY_MISSILE to (8700L to cues(
            1300L to RobotSound.ALARM, 2800L to RobotSound.ALARM, 4000L to RobotSound.MISSILE_LAUNCH,
            4300L to RobotSound.MISSILE_LAUNCH, 4600L to RobotSound.MISSILE_LAUNCH,
            5000L to RobotSound.EXPLOSION, 6000L to RobotSound.EXPLOSION)),
        PetSceneArchetype.SUPER_LOVE to (8000L to cues(
            1400L to RobotSound.WHOOSH, 1800L to RobotSound.POP, 2200L to RobotSound.HEARTBEAT,
            3200L to RobotSound.HEARTBEAT, 4200L to RobotSound.PURR, 5200L to RobotSound.HEARTBEAT,
            6400L to RobotSound.SPARKLE)),
        PetSceneArchetype.DRAMATIC_CRY to (8500L to cues(
            800L to RobotSound.POP, 1800L to RobotSound.CONFUSED, 2600L to RobotSound.SOB,
            4000L to RobotSound.SOB, 4400L to RobotSound.RAIN, 5600L to RobotSound.SOB,
            7200L to RobotSound.PURR)),
        PetSceneArchetype.CELEBRATION to (8500L to cues(
            600L to RobotSound.WHOOSH, 1000L to RobotSound.POP, 2400L to RobotSound.EXPLOSION,
            3000L to RobotSound.FANFARE, 4200L to RobotSound.MELODY, 6800L to RobotSound.HAPPY)),
        PetSceneArchetype.VR_MODE to (9000L to cues(
            1400L to RobotSound.WHOOSH, 2800L to RobotSound.POP, 3200L to RobotSound.POWER_UP,
            4000L to RobotSound.SPARKLE, 5000L to RobotSound.MELODY, 6600L to RobotSound.SPARKLE,
            8400L to RobotSound.HAPPY)),
        PetSceneArchetype.MUSIC to (8500L to cues(
            600L to RobotSound.WHOOSH, 1600L to RobotSound.POP, 2200L to RobotSound.MELODY,
            3800L to RobotSound.MELODY, 5400L to RobotSound.MELODY, 7800L to RobotSound.HAPPY))
    )

    /** คลัง Spec ของทุกฉาก พร้อมความยาวและเสียงตามบท */
    val ARCHETYPE_SPECS: Map<PetSceneArchetype, PetSceneSpec> = BASE_SPECS.mapValues { (archetype, spec) ->
        val script = SCRIPTS[archetype] ?: return@mapValues spec
        spec.copy(durationMs = script.first + SCENE_TAIL_MS, cues = script.second)
    }

    /**
     * คำนวณธีมฉากหลังตามเวลาจริง (Time-Aware Context)
     * - 06:00 - 16:59: แดดจ้ากลางวัน (SUNNY)
     * - 17:00 - 19:59: แดดร่มลมตก / พระอาทิตย์ตกซากุระ (SAKURA)
     * - 20:00 - 05:59: ท้องฟ้ายามดึกดาวกะพริบ (NIGHT)
     */
    fun resolveSmartBackground(overrideTheme: BackgroundTheme? = null, currentHour: Int? = null): BackgroundTheme {
        if (overrideTheme != null && overrideTheme != BackgroundTheme.DEFAULT) {
            return overrideTheme
        }
        val hour = currentHour ?: try {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            now.hour
        } catch (_: Exception) {
            12
        }

        return when (hour) {
            in 6..16 -> BackgroundTheme.SUNNY
            in 17..19 -> BackgroundTheme.SAKURA
            else -> BackgroundTheme.NIGHT
        }
    }

    /**
     * สร้างสถานะ RobotFaceState จาก Scene Archetype:
     * - สุ่มไอเทมจาก Pool หากไม่ได้ระบุไอเทมเจาะจง
     * - สลับฉากหลังตามเวลาจริง (หรือใช้ธีมเฉพาะฉาก)
     * - คืนค่าพร้อมระยะเวลาแสดงผล durationMs
     */
    fun resolveScene(
        archetype: PetSceneArchetype,
        specificProp: PropType? = null,
        currentHour: Int? = null
    ): Pair<RobotFaceState, PetSceneSpec> {
        val spec = ARCHETYPE_SPECS[archetype] ?: ARCHETYPE_SPECS.getValue(PetSceneArchetype.EATING)

        // 1. Resolve Props (Specific or Smart Random from Pool)
        val selectedProps: List<PropType> = when {
            specificProp != null -> listOf(specificProp)
            spec.itemPool.isNotEmpty() -> {
                val rndIndex = Random.nextInt(spec.itemPool.size)
                listOf(spec.itemPool[rndIndex])
            }
            else -> spec.defaultProps
        }

        // 2. Resolve Background
        val bgTheme = resolveSmartBackground(spec.background, currentHour)

        // 3. Assemble RobotFaceState
        val faceState = RobotFaceState(
            emotionName = spec.emotion.name.lowercase(),
            eyeStyleName = spec.eyeStyle.name.lowercase(),
            backgroundName = bgTheme.name.lowercase(),
            propsRaw = selectedProps.joinToString(",") { it.name.lowercase() },
            gestureName = spec.gesture.name.lowercase(),
            sceneName = spec.archetype.name.lowercase(),
            sceneItem = selectedProps.firstOrNull()?.name?.lowercase() ?: ""
        )

        return Pair(faceState, spec)
    }

    /**
     * ตรวจจับ PropType ที่ระบุเจาะจงจากข้อความ เช่น เบอร์เกอร์, พิซซ่า, กาแฟ, ร่ม
     */
    fun detectSpecificPropFromText(text: String): PropType? {
        val clean = text.trim().lowercase()
        return when {
            clean.contains("เบอร์เกอร์") || clean.contains("burger") -> PropType.BURGER
            clean.contains("พิซซ่า") || clean.contains("pizza") -> PropType.PIZZA
            clean.contains("เค้ก") || clean.contains("cake") -> PropType.CAKE
            clean.contains("ไอศกรีม") || clean.contains("ไอติม") || clean.contains("ice cream") -> PropType.ICE_CREAM
            clean.contains("ป๊อปคอร์น") || clean.contains("popcorn") -> PropType.POPCORN
            clean.contains("กาแฟ") || clean.contains("coffee") -> PropType.COFFEE
            clean.contains("ชานม") || clean.contains("boba") || clean.contains("bubble tea") -> PropType.BOBA_TEA
            clean.contains("น้ำชา") || clean.contains("ชาเขียว") || Regex("(?<!รา)ชา").containsMatchIn(clean) || clean.contains("tea") -> PropType.TEA_CUP
            clean.contains("เบียร์") || clean.contains("beer") -> PropType.BEER
            clean.contains("ร่ม") || clean.contains("umbrella") -> PropType.UMBRELLA
            clean.contains("แว่น") || clean.contains("sunglasses") || clean.contains("glasses") || clean.contains("thug") -> PropType.SUNGLASSES
            clean.contains("มงกุฎ") || clean.contains("crown") -> PropType.CROWN
            clean.contains("จอย") || clean.contains("controller") -> PropType.GAMING_CONTROLLER
            clean.contains("แล็ปท็อป") || clean.contains("laptop") || clean.contains("โน้ตบุ๊ก") -> PropType.LAPTOP
            clean.contains("หนังสือ") || clean.contains("book") -> PropType.BOOK
            clean.contains("เหรียญ") || clean.contains("ทองคำ") || clean.contains("coin") || clean.contains("gold") -> PropType.GOLD_COIN
            else -> null
        }
    }

    /**
     * Smart Keyword & Alias Resolver:
     * รู้จำคำพูดภาษาไทยและภาษาอังกฤษ เพื่อเลือก Archetype และ Specific Prop ที่ถูกต้องทันที
     * รองรับ specificProp เพื่อป้องกันการสุ่มตกไอเทมผิดเมื่อผู้ใช้สั่งอาหาร/ของเจาะจง
     */
    fun resolveFromKeyword(
        keyword: String,
        specificProp: PropType? = null,
        currentHour: Int? = null
    ): Pair<RobotFaceState, PetSceneSpec>? {
        val clean = keyword.trim().lowercase()
        val detected = specificProp ?: detectSpecificPropFromText(clean)

        // 0. แว่น VR / ฟังเพลง (ต้องมาก่อน "แว่น" ที่เป็นแว่นดำ)
        if (clean.contains("vr") || clean.contains("virtual reality") || clean.contains("โลกเสมือน") || clean.contains("เมตาเวิร์ส")) {
            return resolveScene(PetSceneArchetype.VR_MODE, PropType.VR_HEADSET, currentHour)
        }
        if (clean.contains("ฟังเพลง") || clean.contains("หูฟัง") || clean.contains("เพลง") || clean.contains("music") ||
            clean.contains("headphone") || clean.contains("song")
        ) {
            return resolveScene(PetSceneArchetype.MUSIC, PropType.HEADPHONES, currentHour)
        }

        // 1. แว่นตา / Thug life / Sunglasses
        if (detected == PropType.SUNGLASSES || clean.contains("แว่น") || clean.contains("glasses") || clean.contains("sunglasses") || clean.contains("thug")) {
            return resolveScene(PetSceneArchetype.MEME_THUG_LIFE, PropType.SUNGLASSES, currentHour)
        }

        // 2. ร่ม / ฝนตก / Umbrella
        if (detected == PropType.UMBRELLA || clean.contains("ร่ม") || clean.contains("umbrella") || clean.contains("ฝน") || clean.contains("rain")) {
            return resolveScene(PetSceneArchetype.RAIN_UMBRELLA, null, currentHour)
        }

        // 2.5 อาบน้ำ (ก่อนเครื่องดื่ม: "อาบน้ำ" มีคำว่า "น้ำ")
        if (clean.contains("อาบน้ำ") || clean.contains("ฟองสบู่") || clean.contains("ถูสบู่") || clean.contains("สระผม") ||
            clean.contains("bath") || clean.contains("clean") || clean.contains("shower")
        ) {
            return resolveScene(PetSceneArchetype.BATH_CLEAN, null, currentHour)
        }

        // 3. กาแฟ / ดื่ม / เครื่องดื่ม
        if (detected in setOf(PropType.COFFEE, PropType.BOBA_TEA, PropType.TEA_CUP, PropType.BEER)) {
            return resolveScene(PetSceneArchetype.DRINKING, detected, currentHour)
        }
        if (clean.contains("กาแฟ") || clean.contains("coffee")) {
            return resolveScene(PetSceneArchetype.DRINKING, PropType.COFFEE, currentHour)
        }
        if (clean.contains("ชานม") || clean.contains("boba") || clean.contains("bubble tea")) {
            return resolveScene(PetSceneArchetype.DRINKING, PropType.BOBA_TEA, currentHour)
        }
        if (clean.contains("น้ำชา") || clean.contains("ชาเขียว") || clean.contains("tea")) {
            return resolveScene(PetSceneArchetype.DRINKING, PropType.TEA_CUP, currentHour)
        }
        if (clean.contains("เบียร์") || clean.contains("beer")) {
            return resolveScene(PetSceneArchetype.DRINKING, PropType.BEER, currentHour)
        }
        if (clean.contains("ดื่ม") || clean.contains("drink") || clean.contains("หิวน้ำ") ||
            (clean.contains("น้ำ") && !clean.contains("น้ำตา") && !clean.contains("น้ำท่วม"))
        ) {
            return resolveScene(PetSceneArchetype.DRINKING, detected, currentHour)
        }

        // 4. อาหาร / กิน
        if (detected in setOf(PropType.BURGER, PropType.PIZZA, PropType.CAKE, PropType.ICE_CREAM, PropType.POPCORN)) {
            return resolveScene(PetSceneArchetype.EATING, detected, currentHour)
        }
        if (clean.contains("พิซซ่า") || clean.contains("pizza")) {
            return resolveScene(PetSceneArchetype.EATING, PropType.PIZZA, currentHour)
        }
        if (clean.contains("เบอร์เกอร์") || clean.contains("burger")) {
            return resolveScene(PetSceneArchetype.EATING, PropType.BURGER, currentHour)
        }
        if (clean.contains("เค้ก") || clean.contains("cake")) {
            return resolveScene(PetSceneArchetype.EATING, PropType.CAKE, currentHour)
        }
        if (clean.contains("ไอศกรีม") || clean.contains("ไอติม") || clean.contains("ice cream")) {
            return resolveScene(PetSceneArchetype.EATING, PropType.ICE_CREAM, currentHour)
        }
        if (clean.contains("ป๊อปคอร์น") || clean.contains("popcorn")) {
            return resolveScene(PetSceneArchetype.EATING, PropType.POPCORN, currentHour)
        }
        if (clean.contains("อาหาร") || clean.contains("กิน") || clean.contains("หิว") || clean.contains("feed") || clean.contains("eat")) {
            return resolveScene(PetSceneArchetype.EATING, detected, currentHour)
        }

        // 6. จรวด / ยิงจรวด / โกรธ
        if (clean.contains("จรวด") || clean.contains("มิสซาย") || clean.contains("missile") || clean.contains("ถล่ม") || clean.contains("ยิง")) {
            return resolveScene(PetSceneArchetype.ANGRY_MISSILE, null, currentHour)
        }

        // 7. รวย / เงิน / คริปโต
        if (detected == PropType.GOLD_COIN || clean.contains("รวย") || clean.contains("เงิน") || clean.contains("คริปโต") || clean.contains("เศรษฐี") || clean.contains("rich") || clean.contains("coin") || clean.contains("gold") || clean.contains("crypto")) {
            return resolveScene(PetSceneArchetype.MEME_RICH, PropType.GOLD_COIN, currentHour)
        }

        // 8. มงกุฎ / เจ้าหญิง / ราชา
        if (detected == PropType.CROWN || clean.contains("มงกุฎ") || clean.contains("ราชา") || clean.contains("เจ้าหญิง") || clean.contains("ราชินี") || clean.contains("crown") || clean.contains("king") || clean.contains("queen") || clean.contains("royal")) {
            return resolveScene(PetSceneArchetype.MEME_ROYAL, PropType.CROWN, currentHour)
        }

        // 9. ไฟไหม้
        if ((clean.contains("ไฟ") && !clean.contains("ไฟฟ้า")) || clean.contains("fire") || clean.contains("flame")) {
            return resolveScene(PetSceneArchetype.COMEDY_FIRE, PropType.FIRE, currentHour)
        }

        // 10. ฟ้าผ่า
        if (clean.contains("ฟ้าผ่า") || clean.contains("ไฟฟ้า") || clean.contains("ช็อต") || clean.contains("thunder") || clean.contains("lightning")) {
            return resolveScene(PetSceneArchetype.COMEDY_THUNDER, PropType.LIGHTNING, currentHour)
        }

        // 10.5 เหนื่อยจนวิญญาณหลุด
        if (clean.contains("วิญญาณ") || clean.contains("soul") || clean.contains("เหนื่อยจน") || clean.contains("หมดแรง")) {
            return resolveScene(PetSceneArchetype.COMEDY_SOUL_OUT, PropType.SKULL, currentHour)
        }

        // 11. เล่นเกม
        if (detected == PropType.GAMING_CONTROLLER || clean.contains("เกม") || clean.contains("เล่นเกม") || clean.contains("game") || clean.contains("gaming")) {
            return resolveScene(PetSceneArchetype.PLAY_GAMING, PropType.GAMING_CONTROLLER, currentHour)
        }

        // 12. ทำงาน / เรียน
        if (detected == PropType.LAPTOP || detected == PropType.BOOK || clean.contains("ทำงาน") || clean.contains("อ่านหนังสือ") || clean.contains("เรียน") || clean.contains("study") || clean.contains("work")) {
            return resolveScene(PetSceneArchetype.STUDY_WORK, detected, currentHour)
        }

        // 13. ความรัก / อ้อน
        if (clean.contains("รัก") || clean.contains("หัวใจ") || clean.contains("อ้อน") || clean.contains("love") || clean.contains("heart")) {
            return resolveScene(PetSceneArchetype.SUPER_LOVE, PropType.HEARTS, currentHour)
        }

        // 14. ร้องไห้ / เสียใจ
        if (clean.contains("ร้องไห้") || clean.contains("เสียใจ") || clean.contains("น้ำตา") || clean.contains("อกหัก") || clean.contains("cry") || clean.contains("sad")) {
            return resolveScene(PetSceneArchetype.DRAMATIC_CRY, PropType.TEARS, currentHour)
        }

        // 15. ฉลอง / ปาร์ตี้
        if (clean.contains("ฉลอง") || clean.contains("ปาร์ตี้") || clean.contains("ยินดี") || clean.contains("party") || clean.contains("celebrate")) {
            return resolveScene(PetSceneArchetype.CELEBRATION, PropType.PARTY_POPPER, currentHour)
        }

        // Direct enum name match fallback
        val matchedEnum = PetSceneArchetype.entries.firstOrNull { it.name.equals(clean.replace(" ", "_"), ignoreCase = true) }
        return if (matchedEnum != null) resolveScene(matchedEnum, detected, currentHour) else null
    }
}

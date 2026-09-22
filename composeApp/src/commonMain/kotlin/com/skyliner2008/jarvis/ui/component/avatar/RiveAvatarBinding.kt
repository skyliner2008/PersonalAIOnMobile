package com.skyliner2008.jarvis.ui.component.avatar

import com.skyliner2008.jarvis.pet.HoloHandGesture

/**
 * RiveAvatarBinding — แปลงสถานะของสัตว์เลี้ยง (ชุดเดียวกับที่ Engine Compose Canvas ใช้)
 * ไปเป็นค่า View Model ของ `rive_avatar` (ดู rive_avatar/AVATAR_CONTRACT.md)
 *
 * แหล่งข้อมูลเดียวกันทั้งสอง Engine:
 *  - [AvatarState.emotion] + `faceState.eyeStyle` (override แบบเดียวกับ Canvas) → face / prop / bg / fg
 *  - `faceState.props` / `backgroundTheme` / `foregroundEffect` จาก AI → ช่อง Rive ถ้ามีของเทียบเท่า
 *    ส่วนที่ Rive ไม่มี จะคืนกลับเป็น [RiveLayerPlan.composeProps] ฯลฯ ให้ชั้น Compose วาดต่อ
 *  - LISTENING / THINKING / SPEAKING / SLEEPING / DIZZY / eye tricks → ช่อง `state`
 *  - มือโฮโลแกรมจากการสัมผัส (poke / pat / chin) → ช่อง `react`
 *  - Missile barrage → story `seq = AngryMissile`
 *
 * ดัชนีทุกตัวตรงกับ `rive_avatar/presets.json` — เพิ่มของใหม่ต่อท้ายเท่านั้น
 */

/** `face` channel */
object RiveFace {
    const val NEUTRAL = 0; const val HAPPY = 1; const val ANGRY = 2; const val SLEEPY = 3
    const val CURIOUS = 4; const val WINK = 5; const val DEAD = 6; const val LAUGHING = 7
    const val EVIL = 8; const val FOCUSED = 9; const val EXCITED = 10; const val SHY = 11
    const val SHOCK = 12; const val DISGUSTED = 13; const val LOVE = 14; const val CRYING = 15
    const val SAD = 16; const val THINKING = 17; const val LISTENING = 18; const val CONFUSED = 19
    const val POUT = 20; const val DIZZY = 21; const val BORED = 22; const val SCARED = 23
    const val TIRED = 24; const val BLANK = 25; const val MONEY = 26; const val GRIN = 27
    const val SMUG = 28; const val DETERMINED = 29; const val CALM = 30; const val RAGE = 31
    const val ONE_EYE = 32; const val RECOGNIZED = 33; const val STARRY = 34
}

/** `prop` channel */
object RiveProp {
    const val NONE = 0; const val ZZZ = 1; const val QUESTION = 2; const val BURGER = 3
    const val BEER = 4; const val SPARKLE = 5; const val HEADPHONES = 6; const val VR = 7
    const val SNORKEL = 8; const val DEVIL = 9; const val SPARKLES = 10; const val BLUSH = 11
    const val EXCLAIM = 12; const val TRASH = 13; const val CAMERA = 14; const val ANGRY_MARK = 15
    const val HEARTS = 16; const val TEARS = 17; const val THINK_DOTS = 18; const val SOUND_WAVE = 19
    const val QUESTIONS = 20; const val STEAM = 21; const val DIZZY_STARS = 22; const val SWEAT = 23
    const val SHIVER = 24; const val MEDAL = 25; const val BATTERY_LOW = 26; const val MIC = 27
    const val SUNGLASSES = 28; const val MISSILES = 29; const val SL_TAG = 30; const val TURRETS = 31
    const val SNORE = 32; const val BULB = 37; const val BOLT = 38; const val ALARM_CLOCK = 48
    const val WARNING = 52
}

/** `bg` channel */
object RiveBg {
    const val NONE = 0; const val GRID = 1; const val BOKEH_HEARTS = 2; const val CHART_UP = 3
    const val CHART_DOWN = 4; const val RED_ALERT = 5; const val GOLD_GLOW = 6; const val BURST_RAYS = 7
    const val SHATTER_RED = 8; const val DEEP_BLUE = 9; const val SPOTLIGHT = 10
}

/** `fg` channel */
object RiveFg {
    const val NONE = 0; const val WATER = 1; const val EXPLOSION = 2; const val CRACKS = 3
    const val LOWER_THIRD = 4; const val CONFETTI = 5; const val MONEY_RAIN = 9; const val SMOKE = 10
}

/** `state` channel — operational loops */
object RiveState {
    const val OFF = 0; const val IDLE = 1; const val READY = 2; const val LISTENING = 3
    const val THINKING = 4; const val SPEAKING = 5; const val DETECTED = 6; const val SCANNING = 7
    const val STANDBY = 8; const val WAIT_CMD = 9; const val WORKING = 10; const val TILT_L = 11
    const val TILT_R = 12; const val DIZZY = 13; const val PLAY_BOUNCE = 14; const val PLAY_CHASE = 15
    const val PLAY_SPIN = 16; const val PLAY_PEEK = 17; const val PLAY_WIGGLE = 18; const val ASLEEP = 19

    const val DETECTED_MS = 4333L
}

/** `react` channel — one-shot touch / shock reactions (durations from presets.json) */
object RiveReact {
    const val NONE = 0; const val HEAD_PAT = 1; const val POKE_L = 2; const val POKE_R = 3
    const val POKE_ANNOYED = 4; const val POKE_ANGRY = 5; const val CHIN_SCRATCH = 6
    const val STARTLED = 7; const val SHAKE_ANGRY = 8

    fun durationMs(react: Int): Long = when (react) {
        HEAD_PAT -> 4000L
        POKE_L, POKE_R -> 3000L
        POKE_ANNOYED -> 3333L
        POKE_ANGRY -> 4667L
        CHIN_SCRATCH -> 4333L
        STARTLED -> 3500L
        SHAKE_ANGRY -> 5500L
        else -> 0L
    }
}

/** `seq` channel — one-shot stories */
object RiveSeq {
    const val NONE = 0
    const val ANGRY_MISSILE = 33
    const val ANGRY_MISSILE_MS = 8667L

    // animated pet scenes (Pet_Scene_Scripts.md) — one story per PetSceneArchetype
    const val SCENE_EATING = 65; const val SCENE_DRINKING = 66; const val SCENE_BATH = 67
    const val SCENE_GAMING = 68; const val SCENE_STUDY = 69; const val SCENE_RAIN = 70
    const val SCENE_THUG = 71; const val SCENE_RICH = 72; const val SCENE_ROYAL = 73
    const val SCENE_FIRE = 74; const val SCENE_THUNDER = 75; const val SCENE_SOUL_OUT = 76
    const val SCENE_SUPER_LOVE = 77; const val SCENE_CRY = 78; const val SCENE_CELEBRATE = 79
    const val SCENE_VR = 80; const val SCENE_MUSIC = 81

    /** story ของฉาก จากชื่อ PetSceneArchetype (ไม่สนตัวพิมพ์) — null ถ้าไม่ใช่ฉากที่รู้จัก */
    fun forScene(sceneName: String): Int? = when (sceneName.uppercase()) {
        "EATING" -> SCENE_EATING
        "DRINKING" -> SCENE_DRINKING
        "BATH_CLEAN" -> SCENE_BATH
        "PLAY_GAMING" -> SCENE_GAMING
        "STUDY_WORK" -> SCENE_STUDY
        "RAIN_UMBRELLA" -> SCENE_RAIN
        "MEME_THUG_LIFE" -> SCENE_THUG
        "MEME_RICH" -> SCENE_RICH
        "MEME_ROYAL" -> SCENE_ROYAL
        "COMEDY_FIRE" -> SCENE_FIRE
        "COMEDY_THUNDER" -> SCENE_THUNDER
        "COMEDY_SOUL_OUT" -> SCENE_SOUL_OUT
        "ANGRY_MISSILE" -> ANGRY_MISSILE
        "SUPER_LOVE" -> SCENE_SUPER_LOVE
        "DRAMATIC_CRY" -> SCENE_CRY
        "CELEBRATION" -> SCENE_CELEBRATE
        "VR_MODE" -> SCENE_VR
        "MUSIC" -> SCENE_MUSIC
        else -> RiveMoodStories.forName(sceneName)?.seq
    }
}

/** `item` channel — ของที่สลับได้ในฉาก (อาหาร / เครื่องดื่ม / อุปกรณ์) */
object RiveItem {
    fun forProp(prop: PropType?): Int = when (prop) {
        PropType.BURGER, PropType.COFFEE, PropType.LAPTOP -> 0
        PropType.PIZZA, PropType.BOBA_TEA, PropType.BOOK -> 1
        PropType.CAKE, PropType.TEA_CUP -> 2
        PropType.ICE_CREAM, PropType.BEER -> 3
        PropType.POPCORN -> 4
        else -> 0
    }
}

/** ค่าที่เขียนลง View Model `Avatar` ของไฟล์ .riv */
data class RiveAvatarInputs(
    val face: Int = RiveFace.NEUTRAL,
    val prop: Int = RiveProp.NONE,
    val bg: Int = RiveBg.NONE,
    val fg: Int = RiveFg.NONE,
    val eyeAct: Int = 0,
    val state: Int = RiveState.IDLE,
    val react: Int = RiveReact.NONE,
    val seq: Int = RiveSeq.NONE,
    val item: Int = 0,
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val audioLevel: Float = 0f,
    val isSpeaking: Boolean = false
)

/**
 * แผนการวาดเมื่อใช้ Engine Rive: ส่วนที่ Rive วาดเอง + ส่วนที่ต้องให้ชั้น Compose วาดทับ/รองต่อ
 * (props / ฉากหลัง / ฉากหน้า ที่ไฟล์ Rive ยังไม่มีของเทียบเท่า)
 */
data class RiveLayerPlan(
    val inputs: RiveAvatarInputs,
    val composeProps: List<PropType>,
    val composeBackground: BackgroundTheme,
    val composeForeground: ForegroundEffect
)

/** เหตุการณ์สัมผัสที่ทำให้มือโฮโลแกรมโผล่ (id เปลี่ยนทุกครั้งที่แตะ) */
data class RiveReactionEvent(
    val gesture: HoloHandGesture,
    val fromLeft: Boolean,
    val id: Long
)

/**
 * อารมณ์ที่แสดงจริง — override จาก `faceState.eyeStyle` และ `isDizzy`
 * ใช้ร่วมกันทั้ง Compose Canvas และ Rive เพื่อให้สองเอนจินแสดงหน้าเดียวกันเสมอ
 */
fun AvatarState.effectiveEmotion(): AvatarEmotion = when {
    isDizzy -> AvatarEmotion.DIZZY
    faceState.eyeStyle == EyeStyle.HEART -> AvatarEmotion.LOVE
    faceState.eyeStyle == EyeStyle.STAR -> AvatarEmotion.EXCITED
    faceState.eyeStyle == EyeStyle.WINK -> AvatarEmotion.WINK
    faceState.eyeStyle == EyeStyle.CROSS -> AvatarEmotion.DEAD
    faceState.eyeStyle == EyeStyle.CRYING -> AvatarEmotion.SAD
    faceState.eyeStyle == EyeStyle.QUESTION -> AvatarEmotion.CONFUSED
    faceState.eyeStyle == EyeStyle.SPIRAL -> AvatarEmotion.DIZZY
    faceState.eyeStyle == EyeStyle.HAPPY -> AvatarEmotion.HAPPY
    faceState.eyeStyle == EyeStyle.ANGRY -> AvatarEmotion.ANGRY
    faceState.eyeStyle == EyeStyle.SLEEPY -> AvatarEmotion.SLEEPING
    faceState.eyeStyle == EyeStyle.CURIOUS -> AvatarEmotion.CONFUSED
    faceState.eyeStyle == EyeStyle.LAUGHING -> AvatarEmotion.LAUGHING
    faceState.eyeStyle == EyeStyle.FOCUSED -> AvatarEmotion.FOCUSED
    faceState.eyeStyle == EyeStyle.EXCITED -> AvatarEmotion.EXCITED
    faceState.eyeStyle == EyeStyle.SHY -> AvatarEmotion.SHY
    faceState.eyeStyle == EyeStyle.SHOCK -> AvatarEmotion.SURPRISED
    else -> emotion
}

object RiveAvatarMapper {

    /**
     * หน้าตาของแต่ละอารมณ์ใน Rive
     * @param extras อุปกรณ์ประจำอารมณ์ที่ Canvas วาดไว้ในหัว แต่ Rive ไม่มี — ให้ Compose วาดแทน
     */
    private data class Look(
        val face: Int,
        val prop: Int = RiveProp.NONE,
        val bg: Int = RiveBg.NONE,
        val fg: Int = RiveFg.NONE,
        val extras: List<PropType> = emptyList()
    )

    private fun lookOf(emotion: AvatarEmotion): Look = when (emotion) {
        AvatarEmotion.IDLE -> Look(RiveFace.NEUTRAL)
        AvatarEmotion.LISTENING -> Look(RiveFace.LISTENING, RiveProp.SOUND_WAVE)
        AvatarEmotion.THINKING -> Look(RiveFace.THINKING, RiveProp.THINK_DOTS)
        AvatarEmotion.SPEAKING -> Look(RiveFace.NEUTRAL)
        AvatarEmotion.HAPPY -> Look(RiveFace.HAPPY)
        AvatarEmotion.SAD -> Look(RiveFace.SAD)
        AvatarEmotion.ANGRY -> Look(RiveFace.ANGRY, RiveProp.ANGRY_MARK)
        AvatarEmotion.LOVE -> Look(RiveFace.LOVE, RiveProp.HEARTS, RiveBg.BOKEH_HEARTS)
        AvatarEmotion.SLEEPING -> Look(RiveFace.SLEEPY, RiveProp.ZZZ)
        AvatarEmotion.EXCITED -> Look(RiveFace.EXCITED, RiveProp.SPARKLES)
        AvatarEmotion.WINK -> Look(RiveFace.WINK, RiveProp.SPARKLE)
        AvatarEmotion.CONFUSED -> Look(RiveFace.CONFUSED, RiveProp.QUESTIONS)
        AvatarEmotion.POUT -> Look(RiveFace.POUT, RiveProp.STEAM)
        AvatarEmotion.DIZZY -> Look(RiveFace.DIZZY, RiveProp.DIZZY_STARS)
        AvatarEmotion.SURPRISED -> Look(RiveFace.SHOCK, RiveProp.EXCLAIM)
        AvatarEmotion.BORED -> Look(RiveFace.BORED)
        AvatarEmotion.ENRAGED -> Look(RiveFace.RAGE, RiveProp.ANGRY_MARK, RiveBg.RED_ALERT)
        AvatarEmotion.DEAD -> Look(RiveFace.DEAD)
        AvatarEmotion.LAUGHING -> Look(RiveFace.LAUGHING)
        AvatarEmotion.MUSIC -> Look(RiveFace.CALM, RiveProp.HEADPHONES, extras = listOf(PropType.MUSIC_NOTES))
        AvatarEmotion.VR_MODE -> Look(RiveFace.BLANK, RiveProp.VR)
        AvatarEmotion.DIVING -> Look(RiveFace.NEUTRAL, RiveProp.SNORKEL, RiveBg.DEEP_BLUE)
        AvatarEmotion.EVIL -> Look(RiveFace.EVIL, RiveProp.DEVIL)
        AvatarEmotion.FOCUSED -> Look(RiveFace.FOCUSED, bg = RiveBg.GRID)
        AvatarEmotion.SHY -> Look(RiveFace.SHY, RiveProp.BLUSH)
        AvatarEmotion.DISGUSTED -> Look(RiveFace.DISGUSTED, RiveProp.TRASH)
        AvatarEmotion.CAMERA_MODE -> Look(RiveFace.SLEEPY, RiveProp.CAMERA)
        AvatarEmotion.EATING -> Look(RiveFace.NEUTRAL, RiveProp.BURGER)
        AvatarEmotion.DRINKING -> Look(RiveFace.NEUTRAL, RiveProp.BEER)
        AvatarEmotion.PUZZLED -> Look(RiveFace.CONFUSED, RiveProp.QUESTIONS)
        AvatarEmotion.SICK -> Look(RiveFace.TIRED, RiveProp.SWEAT, extras = listOf(PropType.THERMOMETER))
        AvatarEmotion.RICH -> Look(RiveFace.MONEY, bg = RiveBg.GOLD_GLOW, fg = RiveFg.MONEY_RAIN)
        AvatarEmotion.CRYING -> Look(RiveFace.CRYING, RiveProp.TEARS)
        AvatarEmotion.READING -> Look(RiveFace.CALM, extras = listOf(PropType.GLASSES_SQUARE, PropType.BOOK))
        AvatarEmotion.GAMING -> Look(RiveFace.FOCUSED, extras = listOf(PropType.GAMING_HEADSET, PropType.GAMING_CONTROLLER))
        AvatarEmotion.TRAVELING -> Look(RiveFace.HAPPY, extras = listOf(PropType.BUCKET_HAT, PropType.PASSPORT, PropType.BACKPACK))
        AvatarEmotion.WORKING -> Look(RiveFace.FOCUSED, extras = listOf(PropType.GLASSES_SQUARE, PropType.LAPTOP, PropType.COFFEE))
        AvatarEmotion.COLD -> Look(RiveFace.SCARED, RiveProp.SHIVER, extras = listOf(PropType.ICICLES, PropType.SNOW))
        AvatarEmotion.HOT -> Look(RiveFace.TIRED, RiveProp.SWEAT, extras = listOf(PropType.HEATWAVES))
        AvatarEmotion.DETECTIVE -> Look(RiveFace.SMUG, extras = listOf(PropType.FEDORA_HAT, PropType.MAGNIFYING_GLASS))
        AvatarEmotion.COOKING -> Look(RiveFace.HAPPY, extras = listOf(PropType.CHEF_HAT, PropType.SPATULA))
        AvatarEmotion.ART_MODE -> Look(RiveFace.NEUTRAL, extras = listOf(PropType.BERET, PropType.PALETTE))
        AvatarEmotion.SPACE -> Look(RiveFace.NEUTRAL, extras = listOf(PropType.ASTRONAUT_HELMET, PropType.MOON_STARS))
        AvatarEmotion.PARTY -> Look(RiveFace.LAUGHING, bg = RiveBg.BOKEH_HEARTS, fg = RiveFg.CONFETTI, extras = listOf(PropType.PARTY_HAT))
        AvatarEmotion.DREAMING -> Look(RiveFace.SLEEPY, RiveProp.ZZZ, extras = listOf(PropType.DREAM_CLOUD))
        AvatarEmotion.EXHAUSTED -> Look(RiveFace.TIRED, RiveProp.SWEAT)
        AvatarEmotion.ELECTRIC -> Look(RiveFace.DETERMINED, RiveProp.BOLT)
        AvatarEmotion.SNEAKY -> Look(RiveFace.SMUG, extras = listOf(PropType.BANDIT_MASK))
        AvatarEmotion.ROMANTIC -> Look(RiveFace.LOVE, RiveProp.HEARTS, RiveBg.BOKEH_HEARTS, extras = listOf(PropType.ROSE))
        AvatarEmotion.HERO -> Look(RiveFace.DETERMINED, extras = listOf(PropType.SUPERHERO_MASK, PropType.SUPERHERO_CAPE))
        AvatarEmotion.GLITCHED -> Look(RiveFace.DIZZY, fg = RiveFg.SMOKE)
        AvatarEmotion.MAGIC -> Look(RiveFace.EXCITED, RiveProp.SPARKLES, extras = listOf(PropType.WIZARD_HAT, PropType.MAGIC_WAND))
        AvatarEmotion.SPORTY -> Look(RiveFace.DETERMINED, extras = listOf(PropType.SWEATBAND, PropType.BASKETBALL))
        AvatarEmotion.SCIENTIST -> Look(RiveFace.FOCUSED, extras = listOf(PropType.LAB_GOGGLES, PropType.BEAKER))
        AvatarEmotion.SCARED -> Look(RiveFace.SCARED, RiveProp.SHIVER)
        AvatarEmotion.WARRIOR -> Look(RiveFace.DETERMINED, extras = listOf(PropType.VIKING_HELMET, PropType.SWORD))
        AvatarEmotion.LOW_BATTERY -> Look(RiveFace.TIRED, RiveProp.BATTERY_LOW)
    }

    /** props จาก AI ที่ Rive มีของเทียบเท่า (แสดงได้ทีละชิ้น) */
    fun riveProp(prop: PropType): Int? = when (prop) {
        PropType.HEARTS -> RiveProp.HEARTS
        PropType.SPARKLES -> RiveProp.SPARKLES
        PropType.SWEAT_DROP -> RiveProp.SWEAT
        PropType.QUESTION_MARK -> RiveProp.QUESTION
        PropType.EXCLAMATION -> RiveProp.EXCLAIM
        PropType.ZZZZZ -> RiveProp.ZZZ
        PropType.ANGER_VEIN -> RiveProp.ANGRY_MARK
        PropType.TEARS -> RiveProp.TEARS
        PropType.DIZZY_STARS -> RiveProp.DIZZY_STARS
        PropType.LIGHTBULB -> RiveProp.BULB
        PropType.SUNGLASSES -> RiveProp.SUNGLASSES
        PropType.BEER -> RiveProp.BEER
        PropType.BURGER -> RiveProp.BURGER
        PropType.BATTERY_CHARGING -> RiveProp.BOLT
        PropType.BATTERY_LOW -> RiveProp.BATTERY_LOW
        PropType.CLOCK_ALARM -> RiveProp.ALARM_CLOCK
        PropType.WARNING_TRIANGLE -> RiveProp.WARNING
        PropType.HEADPHONES -> RiveProp.HEADPHONES
        PropType.VR_HEADSET -> RiveProp.VR
        PropType.SNORKEL_MASK -> RiveProp.SNORKEL
        PropType.DEVIL_HORNS -> RiveProp.DEVIL
        PropType.TRASH_BIN -> RiveProp.TRASH
        PropType.CAMERA_ICON -> RiveProp.CAMERA
        PropType.THOUGHT_BUBBLE -> RiveProp.THINK_DOTS
        else -> null
    }

    /** ฉากหลังจาก AI ที่ Rive มีของเทียบเท่า */
    fun riveBackground(theme: BackgroundTheme): Int? = when (theme) {
        BackgroundTheme.LOVE_BG -> RiveBg.BOKEH_HEARTS
        BackgroundTheme.CYBER_GRID -> RiveBg.GRID
        BackgroundTheme.GOLDEN_VAULT -> RiveBg.GOLD_GLOW
        BackgroundTheme.SPORTS_ARENA -> RiveBg.SPOTLIGHT
        BackgroundTheme.LOW_POWER_CRT -> RiveBg.RED_ALERT
        else -> null
    }

    /** ฉากหน้าจาก AI ที่ Rive มีของเทียบเท่า */
    fun riveForeground(effect: ForegroundEffect): Int? = when (effect) {
        ForegroundEffect.CONFETTI_TUMBLE -> RiveFg.CONFETTI
        else -> null
    }

    /** ลูกเล่นตาตอนพักหน้าจอ → state self-play ของ Rive */
    private fun eyeTrickState(trick: EyeTrickState): Int? = when (trick) {
        EyeTrickState.PING_PONG_BOUNCE -> RiveState.PLAY_BOUNCE
        EyeTrickState.TIRED_BOUNCE -> RiveState.PLAY_PEEK
        EyeTrickState.SNOOKER_SHOT -> RiveState.PLAY_CHASE
        EyeTrickState.NONE -> null
    }

    /**
     * สร้างแผนการวาดจากสถานะปัจจุบัน (pure function — ไม่มี timer)
     * react / seq / Detected ถูกซ้อนทับภายหลังโดยตัวขับเวลาใน Compose
     */
    fun plan(state: AvatarState, tiltX: Float = 0f, tiltY: Float = 0f): RiveLayerPlan {
        val emotion = state.effectiveEmotion()
        val look = lookOf(emotion)
        val face = state.faceState

        // an animated scene: the story in the .riv draws every prop / backdrop itself,
        // so the Compose overlay stays empty for the whole scene
        val sceneSeq = face.sceneName.takeIf { it.isNotBlank() }?.let { RiveSeq.forScene(it) }
        if (sceneSeq != null) {
            val sceneItem = PropType.entries.firstOrNull { it.name.equals(face.sceneItem, ignoreCase = true) }
            return RiveLayerPlan(
                inputs = RiveAvatarInputs(
                    face = look.face,
                    seq = sceneSeq,
                    item = RiveItem.forProp(sceneItem),
                    gazeX = state.gazeOffsetX.coerceIn(-1f, 1f),
                    gazeY = state.gazeOffsetY.coerceIn(-1f, 1f),
                    tiltX = tiltX.coerceIn(-1f, 1f),
                    tiltY = tiltY.coerceIn(-1f, 1f),
                    audioLevel = state.audioLevel.coerceIn(0f, 1f),
                    isSpeaking = state.isSpeaking
                ),
                composeProps = emptyList(),
                composeBackground = BackgroundTheme.DEFAULT,
                composeForeground = ForegroundEffect.NONE
            )
        }

        // AI props: the first one Rive can draw replaces the emotion's own prop;
        // everything else stays with the Compose overlay
        val aiProps = face.props
        val riveAiProp = aiProps.firstNotNullOfOrNull { riveProp(it) }
        val usedAiProp = aiProps.firstOrNull { riveProp(it) != null }
        val composeProps = (aiProps.filter { it != usedAiProp } + look.extras).distinct()

        val aiBg = face.backgroundTheme
        val riveAiBg = if (aiBg != BackgroundTheme.DEFAULT) riveBackground(aiBg) else null
        val composeBg = if (aiBg != BackgroundTheme.DEFAULT && riveAiBg == null) aiBg else BackgroundTheme.DEFAULT
        val bg = when {
            riveAiBg != null -> riveAiBg
            composeBg != BackgroundTheme.DEFAULT -> RiveBg.NONE     // Compose owns the scene
            else -> look.bg
        }

        val aiFg = face.foregroundEffect
        val riveAiFg = if (aiFg != ForegroundEffect.NONE) riveForeground(aiFg) else null
        val composeFg = if (aiFg != ForegroundEffect.NONE && riveAiFg == null) aiFg else ForegroundEffect.NONE
        val fg = when {
            riveAiFg != null -> riveAiFg
            composeFg != ForegroundEffect.NONE -> RiveFg.NONE
            else -> look.fg
        }

        val speaking = state.isSpeaking || emotion == AvatarEmotion.SPEAKING
        val riveState = eyeTrickState(face.eyeTrick) ?: when {
            state.isDizzy || emotion == AvatarEmotion.DIZZY -> RiveState.DIZZY
            emotion == AvatarEmotion.SLEEPING -> RiveState.ASLEEP
            emotion == AvatarEmotion.LISTENING -> RiveState.LISTENING
            emotion == AvatarEmotion.THINKING -> RiveState.THINKING
            speaking -> RiveState.SPEAKING
            emotion == AvatarEmotion.BORED -> RiveState.STANDBY
            face.gesture == GestureType.TILT_LEFT -> RiveState.TILT_L
            face.gesture == GestureType.TILT_RIGHT -> RiveState.TILT_R
            else -> RiveState.IDLE
        }

        return RiveLayerPlan(
            inputs = RiveAvatarInputs(
                // the LOOI bulb IS the right eye: pair it with the one-eyed face so it never sits over an eye
                face = if ((riveAiProp ?: look.prop) == RiveProp.BULB) RiveFace.ONE_EYE else look.face,
                prop = riveAiProp ?: look.prop,
                bg = bg,
                fg = fg,
                state = riveState,
                gazeX = state.gazeOffsetX.coerceIn(-1f, 1f),
                gazeY = state.gazeOffsetY.coerceIn(-1f, 1f),
                tiltX = tiltX.coerceIn(-1f, 1f),
                tiltY = tiltY.coerceIn(-1f, 1f),
                audioLevel = state.audioLevel.coerceIn(0f, 1f),
                isSpeaking = speaking
            ),
            composeProps = composeProps,
            composeBackground = composeBg,
            composeForeground = composeFg
        )
    }

    /**
     * เลือกท่า react จากมือโฮโลแกรม + อารมณ์ที่ state machine ตัดสินหลังการแตะ
     * (จิ้มจนงอน → PokeAnnoyed, จิ้มจนโกรธ → PokeAngry — ตรงกับ PetStateMachine.resolvePoke)
     */
    fun reactFor(gesture: HoloHandGesture, fromLeft: Boolean, resultingEmotion: AvatarEmotion): Int = when (gesture) {
        HoloHandGesture.STROKE, HoloHandGesture.PAT -> RiveReact.HEAD_PAT
        HoloHandGesture.CHIN_SCRATCH -> RiveReact.CHIN_SCRATCH
        HoloHandGesture.POKE, HoloHandGesture.TICKLE -> when (resultingEmotion) {
            AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED -> RiveReact.POKE_ANGRY
            AvatarEmotion.POUT -> RiveReact.POKE_ANNOYED
            else -> if (fromLeft) RiveReact.POKE_L else RiveReact.POKE_R
        }
        else -> RiveReact.HEAD_PAT
    }
}

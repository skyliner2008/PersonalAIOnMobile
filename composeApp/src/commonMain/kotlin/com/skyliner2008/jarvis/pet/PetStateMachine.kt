package com.skyliner2008.jarvis.pet

import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.sound.RobotSoundPlayer
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion

/**
 * TouchZone — โซนสัมผัสบนใบหน้า Pet (normalized coordinates)
 */
enum class TouchZone {
    FOREHEAD,       // normY < -0.30
    LEFT_CHEEK,     // normX < -0.40
    RIGHT_CHEEK,    // normX > 0.40
    CHIN,           // normY > 0.32
    FACE_CENTER,    // center area
    OUTSIDE         // นอกใบหน้า
}

/**
 * InteractionType — ประเภทการปฏิสัมพันธ์กับ Pet
 */
enum class InteractionType {
    POKE,           // จิ้ม (single tap)
    PET_HEAD,       // ลูบหัว (drag on forehead)
    CHIN_SCRATCH,   // เกาคาง (drag on chin)
    TICKLE,         // จั๊กจี้ (double tap cheek)
    FEED,           // ให้อาหาร
    CLEAN,          // อาบน้ำ
    PLAY,           // ชวนเล่น
    SLEEP,          // ให้นอน
    SHAKE,          // เขย่า (accelerometer)
    HEAVY_SHAKE,    // เขย่าแรง / เขย่ารัวๆ
    BOAT_ROCKING,   // เอียงไปมาเหมือนนั่งเรือ (เรือโคลงเคลง เมาคลื่น)
    TABLE_THUMP,    // ทุบโต๊ะ / แรงกระแทก shock impulse
    LOUD_NOISE,     // ทำเสียงดัง / ตะโกน / ตะคอก
    FIGHT_BACK,     // ระเบิดความโกรธ สู้กลับ ยิงจรวด
    WAKE_UP,        // ปลุก
    PAT             // ตบเบาๆ (tap center)
}

/**
 * HoloHandGesture — ประเภทท่ามือโฮโลแกรมที่จะแสดงผล
 */
enum class HoloHandGesture {
    PAT,            // ฝ่ามือเปิดแบน ตบเบาๆ
    POKE,           // นิ้วชี้เดี่ยวจิ้ม
    STROKE,         // ฝ่ามือลูบไล้ (sweep left-right)
    CHIN_SCRATCH,   // 2-3 นิ้วงอเกา
    TICKLE,         // นิ้วกระดิก wiggle
    WAVE            // มือโบก bye-bye
}

/**
 * EmotionTransitionResult — ผลลัพธ์จาก State Machine
 */
data class EmotionTransitionResult(
    val emotion: AvatarEmotion,
    val statusText: String,
    val holoGesture: HoloHandGesture? = null,
    val soundAction: (() -> Unit)? = null,
    val needsUpdate: ((PetNeedsState) -> PetNeedsState)? = null,
    val triggerMissileBarrage: Boolean = false,
    val durationMs: Long = 2500L
)

/**
 * InteractionLog — บันทึกประวัติ interaction สำหรับ tracking ความถี่
 */
data class InteractionLog(
    val type: InteractionType,
    val zone: TouchZone,
    val timestamp: Long
)

/**
 * PetStateMachine — State Machine Matrix ที่เชื่อมโยง needs × events → emotions
 *
 * อัลกอริทึม:
 * 1. รับ event (touch/action) + zone
 * 2. ตรวจสอบ interaction frequency (anti-spam)
 * 3. ดู PetNeedsState ปัจจุบัน (หิว? เหนื่อย? เครียด?)
 * 4. ดู personality traits (ไฮเปอร์ vs สงบ, ขี้อ้อน vs อิสระ)
 * 5. คำนวณ → ผลลัพธ์อารมณ์ + มือโฮโลแกรม + เสียง
 */
class PetStateMachine {
    companion object {
        private const val TAG = "PetStateMachine"

        // ─── Anti-Spam Thresholds ─────────────────────────────────────────
        private const val POKE_SPAM_COUNT = 5          // จิ้ม > 5 ครั้งใน WINDOW → โกรธ
        private const val POKE_SPAM_WINDOW_MS = 30_000L
        private const val TICKLE_SPAM_COUNT = 3        // จั๊กจี้ > 3 ครั้งใน WINDOW → เหนื่อย
        private const val TICKLE_SPAM_WINDOW_MS = 20_000L
        private const val PET_BONUS_COUNT = 3          // ลูบ > 3 ครั้งใน WINDOW → affection 2x
        private const val PET_BONUS_WINDOW_MS = 60_000L

        // ─── Emotion Decay Durations ──────────────────────────────────────
        const val ANGRY_DECAY_MS = 30_000L             // ANGRY → POUT → IDLE
        const val SAD_DECAY_MS = 60_000L               // SAD → IDLE
        const val BORED_TO_SLEEP_MS = 120_000L         // BORED → SLEEPING
        const val EXCITED_DECAY_MS = 15_000L           // EXCITED → HAPPY → IDLE
        const val LOVE_DECAY_MS = 20_000L              // LOVE → HAPPY → IDLE
        const val SURPRISED_DECAY_MS = 3_000L          // SURPRISED → (context)
    }

    // ─── Interaction History Ring Buffer ───────────────────────────────────
    private val interactionHistory = mutableListOf<InteractionLog>()
    private val maxHistorySize = 50

    /**
     * บันทึก interaction ใหม่
     */
    fun logInteraction(type: InteractionType, zone: TouchZone = TouchZone.FACE_CENTER) {
        val entry = InteractionLog(type, zone, System.currentTimeMillis())
        interactionHistory.add(entry)
        if (interactionHistory.size > maxHistorySize) {
            interactionHistory.removeAt(0)
        }
    }

    /**
     * นับ interaction ชนิดที่กำหนดภายใน window เวลา
     */
    fun countRecentInteractions(type: InteractionType, windowMs: Long): Int {
        val cutoff = System.currentTimeMillis() - windowMs
        return interactionHistory.count { it.type == type && it.timestamp > cutoff }
    }

    /**
     * คำนวณ TouchZone จาก normalized coordinates
     */
    fun resolveTouchZone(normX: Float, normY: Float): TouchZone = when {
        normY < -0.30f -> TouchZone.FOREHEAD
        normY > 0.32f -> TouchZone.CHIN
        normX < -0.40f -> TouchZone.LEFT_CHEEK
        normX > 0.40f -> TouchZone.RIGHT_CHEEK
        normX * normX + normY * normY < 1.5f -> TouchZone.FACE_CENTER
        else -> TouchZone.OUTSIDE
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ─── CORE STATE MACHINE: Process Events ─────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ประมวลผล touch event → คำนวณอารมณ์ + มือโฮโลแกรม + เสียง
     */
    fun processTouch(
        type: InteractionType,
        zone: TouchZone,
        needs: PetNeedsState,
        currentEmotion: AvatarEmotion
    ): EmotionTransitionResult {
        logInteraction(type, zone)
        logDebug(TAG, "🎯 processTouch: type=$type, zone=$zone, currentEmotion=$currentEmotion")

        return when (type) {
            InteractionType.PET_HEAD -> resolvePetHead(zone, needs, currentEmotion)
            InteractionType.POKE -> resolvePoke(zone, needs, currentEmotion)
            InteractionType.CHIN_SCRATCH -> resolveChinScratch(needs, currentEmotion)
            InteractionType.TICKLE -> resolveTickle(needs, currentEmotion)
            InteractionType.PAT -> resolvePat(needs, currentEmotion)
            InteractionType.FEED -> resolveFeed(needs, currentEmotion)
            InteractionType.CLEAN -> resolveClean(needs, currentEmotion)
            InteractionType.PLAY -> resolvePlay(needs, currentEmotion)
            InteractionType.SLEEP -> resolveSleep(needs, currentEmotion)
            InteractionType.SHAKE -> resolveShake(needs, currentEmotion)
            InteractionType.HEAVY_SHAKE -> resolveHeavyShake(needs, currentEmotion)
            InteractionType.BOAT_ROCKING -> resolveBoatRocking(needs, currentEmotion)
            InteractionType.TABLE_THUMP -> resolveTableThump(needs, currentEmotion)
            InteractionType.LOUD_NOISE -> resolveLoudNoise(needs, currentEmotion)
            InteractionType.FIGHT_BACK -> resolveFightBack(needs, currentEmotion)
            InteractionType.WAKE_UP -> resolveWakeUp(needs, currentEmotion)
        }
    }

    /**
     * คำนวณอารมณ์อัตโนมัติจากสถานะ needs (เรียกทุก ~20 วินาที)
     */
    fun resolvePassiveEmotion(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult? {
        // อย่าขัดจังหวะ emotion ที่กำลังแสดงอยู่ (ยกเว้น IDLE, BORED)
        if (currentEmotion != AvatarEmotion.IDLE && currentEmotion != AvatarEmotion.BORED) {
            return null
        }

        return when {
            // หิวจัดมาก (โมโหหิว) → โกรธ
            needs.satiety < 20f -> EmotionTransitionResult(
                emotion = AvatarEmotion.ANGRY,
                statusText = "หิวจัดแล้วน้าา!! ให้อาหารน้องหน่อยยย! 🍖😡",
                soundAction = { RobotSoundPlayer.playAlarm() },
                durationMs = 5000L
            )
            // สภาพแย่รวม → เศร้า
            needs.satiety < 25f && needs.hygiene < 30f && needs.stress > 50f -> EmotionTransitionResult(
                emotion = AvatarEmotion.SAD,
                statusText = "น้องไม่สบายเลย... หิวด้วย สกปรกด้วย เครียดด้วย 😢",
                soundAction = { RobotSoundPlayer.playConfused() },
                durationMs = 8000L
            )
            // เหนื่อยมาก → หลับเอง
            needs.energy < 15f -> EmotionTransitionResult(
                emotion = AvatarEmotion.SLEEPING,
                statusText = "เหนื่อยมากก... หลับก่อนน้าา Zzz 💤",
                soundAction = { RobotSoundPlayer.playYawn() },
                durationMs = Long.MAX_VALUE
            )
            // เครียดสูง → งอน
            needs.stress > 70f -> EmotionTransitionResult(
                emotion = AvatarEmotion.POUT,
                statusText = "เครียดจังเลย... อย่าทิ้งน้องไว้คนเดียวน้าา 😤",
                soundAction = { RobotSoundPlayer.playConfused() },
                durationMs = 5000L
            )
            // ไม่มีใครเล่นด้วย + ไม่มีความสุข → เบื่อ
            needs.happiness < 25f && countRecentInteractions(InteractionType.PAT, 300_000L) == 0
                    && countRecentInteractions(InteractionType.PET_HEAD, 300_000L) == 0 -> EmotionTransitionResult(
                emotion = AvatarEmotion.BORED,
                statusText = "เบื่ออออ ไม่มีใครมาเล่นด้วยเลยย 😑💭",
                soundAction = { RobotSoundPlayer.playYawn() },
                durationMs = 10000L
            )
            // สบายดีทุกอย่าง → null (ให้คง IDLE ต่อไป)
            else -> null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ─── INDIVIDUAL EVENT RESOLVERS ─────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    private fun resolvePetHead(zone: TouchZone, needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        val petCount = countRecentInteractions(InteractionType.PET_HEAD, PET_BONUS_WINDOW_MS)
        val bonusAffection = if (petCount >= PET_BONUS_COUNT) 2 else 1

        // ถ้ากำลังหลับ → ตกใจแล้วค่อยๆ สบาย
        if (currentEmotion == AvatarEmotion.SLEEPING) {
            return EmotionTransitionResult(
                emotion = AvatarEmotion.SURPRISED,
                statusText = "ฮึ!? ใ-ใครมาลูบหัวน้า!? 😳",
                holoGesture = HoloHandGesture.STROKE,
                soundAction = { RobotSoundPlayer.playSurprise() },
                needsUpdate = { it.interactLove().copy(
                    affectionPoints = (it.affectionPoints + 2 * bonusAffection).coerceAtMost(1000)
                ) },
                durationMs = SURPRISED_DECAY_MS
            )
        }

        return when {
            needs.happiness > 50f -> EmotionTransitionResult(
                emotion = AvatarEmotion.LOVE,
                statusText = if (petCount >= PET_BONUS_COUNT)
                    "งุ้ยยย ลูบเยอะจัง รักบอสมากเลยย ♥♥♥"
                else "งุ้ยย อบอุ่นจัง สบายจังเลยย ♥",
                holoGesture = HoloHandGesture.STROKE,
                soundAction = { RobotSoundPlayer.playPurr() },
                needsUpdate = { it.interactLove().copy(
                    affectionPoints = (it.affectionPoints + 4 * bonusAffection).coerceAtMost(1000)
                ) },
                durationMs = 2800L
            )
            needs.happiness < 30f -> EmotionTransitionResult(
                emotion = AvatarEmotion.HAPPY,
                statusText = "ขอบคุณที่มาลูบหัวน้า... น้องดีใจ 🥺✨",
                holoGesture = HoloHandGesture.STROKE,
                soundAction = { RobotSoundPlayer.playHappy() },
                needsUpdate = { it.interactLove().copy(
                    affectionPoints = (it.affectionPoints + 6 * bonusAffection).coerceAtMost(1000)
                ) },
                durationMs = 2800L
            )
            else -> EmotionTransitionResult(
                emotion = AvatarEmotion.LOVE,
                statusText = "ฟี้ๆ สบายจังค่ะเจ้านาย ♥",
                holoGesture = HoloHandGesture.STROKE,
                soundAction = { RobotSoundPlayer.playPurr() },
                needsUpdate = { it.interactLove() },
                durationMs = 2800L
            )
        }
    }

    private fun resolvePoke(zone: TouchZone, needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        val pokeCount = countRecentInteractions(InteractionType.POKE, POKE_SPAM_WINDOW_MS)

        // ปลุกตอนหลับ → ตกใจ แล้วโกรธ
        if (currentEmotion == AvatarEmotion.SLEEPING) {
            return EmotionTransitionResult(
                emotion = AvatarEmotion.SURPRISED,
                statusText = "ตกใจจจจ!! จิ้มปลุกทำไมนะะ!? 😱💢",
                holoGesture = HoloHandGesture.POKE,
                soundAction = { RobotSoundPlayer.playSurprise() },
                needsUpdate = { it.copy(
                    stress = (it.stress + 15f).coerceIn(0f, 100f),
                    happiness = (it.happiness - 10f).coerceIn(0f, 100f)
                ) },
                durationMs = SURPRISED_DECAY_MS
            )
        }

        // จิ้มมากเกินไป → โกรธ!
        if (pokeCount >= POKE_SPAM_COUNT) {
            return EmotionTransitionResult(
                emotion = AvatarEmotion.ANGRY,
                statusText = "อ้ายย! จิ้มมากไปแล้วน้าา หยุดเดี๋ยวนี้!! 😡💢",
                holoGesture = HoloHandGesture.POKE,
                soundAction = { RobotSoundPlayer.playAlarm() },
                needsUpdate = { it.copy(
                    stress = (it.stress + 20f).coerceIn(0f, 100f),
                    happiness = (it.happiness - 15f).coerceIn(0f, 100f)
                ) },
                durationMs = ANGRY_DECAY_MS
            )
        }

        // จิ้มปกติ
        val cheekSide = if (zone == TouchZone.LEFT_CHEEK) "ซ้าย" else "ขวา"
        return when {
            needs.isStressed -> EmotionTransitionResult(
                emotion = AvatarEmotion.POUT,
                statusText = "ม่ายยย อย่าจิ้มตอนเครียดน้าา 😤",
                holoGesture = HoloHandGesture.POKE,
                soundAction = { RobotSoundPlayer.playConfused() },
                needsUpdate = { it.copy(stress = (it.stress + 5f).coerceIn(0f, 100f)) },
                durationMs = 2500L
            )
            pokeCount >= 3 -> EmotionTransitionResult(
                emotion = AvatarEmotion.POUT,
                statusText = "จิ้มเยอะจังง เขินแล้วน้าา! 😤✨",
                holoGesture = HoloHandGesture.POKE,
                soundAction = { RobotSoundPlayer.playConfused() },
                needsUpdate = { it.interactLove() },
                durationMs = 2000L
            )
            else -> EmotionTransitionResult(
                emotion = AvatarEmotion.HAPPY,
                statusText = "ฮิๆ จิ้มแก้ม${cheekSide}ทำไมคะ เขินนะ ^_^",
                holoGesture = HoloHandGesture.POKE,
                soundAction = { RobotSoundPlayer.playHappy() },
                needsUpdate = { it.interactLove() },
                durationMs = 2000L
            )
        }
    }

    private fun resolveChinScratch(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        if (currentEmotion == AvatarEmotion.SLEEPING) {
            return EmotionTransitionResult(
                emotion = AvatarEmotion.HAPPY,
                statusText = "อืมม... ฝันดีจังเลย... (ยังหลับอยู่แต่ยิ้ม) 😊💤",
                holoGesture = HoloHandGesture.CHIN_SCRATCH,
                soundAction = { RobotSoundPlayer.playPurr() },
                needsUpdate = { it.interactLove() },
                durationMs = 2500L
            )
        }

        return EmotionTransitionResult(
            emotion = AvatarEmotion.LOVE,
            statusText = "เอียงคอซบมือให้เกาๆ... สบายมากเลยฮับ ♥♥",
            holoGesture = HoloHandGesture.CHIN_SCRATCH,
            soundAction = { RobotSoundPlayer.playPurr() },
            needsUpdate = { it.interactLove().copy(
                happiness = (it.happiness + 10f).coerceIn(0f, 100f),
                stress = (it.stress - 8f).coerceIn(0f, 100f)
            ) },
            durationMs = 3000L
        )
    }

    private fun resolveTickle(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        val tickleCount = countRecentInteractions(InteractionType.TICKLE, TICKLE_SPAM_WINDOW_MS)

        if (currentEmotion == AvatarEmotion.SLEEPING) {
            return EmotionTransitionResult(
                emotion = AvatarEmotion.SURPRISED,
                statusText = "อ๊าาา!! จั๊กจี้ตอนหลับ!! ตื่นเลยย! 😱🤣",
                holoGesture = HoloHandGesture.TICKLE,
                soundAction = { RobotSoundPlayer.playSurprise() },
                needsUpdate = { it.copy(
                    stress = (it.stress + 10f).coerceIn(0f, 100f)
                ) },
                durationMs = SURPRISED_DECAY_MS
            )
        }

        // จั๊กจี้มากเกินไป → เหนื่อย
        if (tickleCount >= TICKLE_SPAM_COUNT) {
            return EmotionTransitionResult(
                emotion = AvatarEmotion.POUT,
                statusText = "เหนื่อยแล้วนะะ หยุดจั๊กจี้ซะทีนะะ! 😤💦",
                holoGesture = HoloHandGesture.TICKLE,
                soundAction = { RobotSoundPlayer.playConfused() },
                needsUpdate = { it.copy(
                    energy = (it.energy - 8f).coerceIn(0f, 100f),
                    stress = (it.stress + 10f).coerceIn(0f, 100f)
                ) },
                durationMs = 2500L
            )
        }

        return when {
            needs.energy > 40f -> EmotionTransitionResult(
                emotion = AvatarEmotion.EXCITED,
                statusText = "ฮ่าๆๆ อย่าจั๊กจี้น้าา ดิ้นไม่หยุดแล้วว! ✨🤣",
                holoGesture = HoloHandGesture.TICKLE,
                soundAction = { RobotSoundPlayer.playGiggle() },
                needsUpdate = { it.interactLove().copy(
                    energy = (it.energy - 5f).coerceIn(0f, 100f)
                ) },
                durationMs = 2200L
            )
            else -> EmotionTransitionResult(
                emotion = AvatarEmotion.POUT,
                statusText = "เหนื่อยอยู่นะ จะจั๊กจี้ทำไมม 🥺",
                holoGesture = HoloHandGesture.TICKLE,
                soundAction = { RobotSoundPlayer.playConfused() },
                needsUpdate = { it.copy(
                    energy = (it.energy - 3f).coerceIn(0f, 100f)
                ) },
                durationMs = 2000L
            )
        }
    }

    private fun resolvePat(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        return EmotionTransitionResult(
            emotion = AvatarEmotion.HAPPY,
            statusText = "ตบเบาๆ น่ารักจัง ขอบคุณน้า ^_^",
            holoGesture = HoloHandGesture.PAT,
            soundAction = { RobotSoundPlayer.playHappy() },
            needsUpdate = { it.interactLove() },
            durationMs = 2000L
        )
    }

    // ─── Care Actions ─────────────────────────────────────────────────────

    private fun resolveFeed(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        return when {
            needs.satiety < 30f -> EmotionTransitionResult(
                emotion = AvatarEmotion.EXCITED,
                statusText = "เย้ๆๆ!! อาหารมาแล้วว!! งั่มๆๆ อร่อยมากกก! 🍖✨🎉",
                soundAction = { RobotSoundPlayer.playCrunchEat() },
                needsUpdate = { it.feed() },
                durationMs = 3000L
            )
            needs.satiety > 80f -> EmotionTransitionResult(
                emotion = AvatarEmotion.CONFUSED,
                statusText = "อิ่มแล้วนะฮับ... กินอีกก็ท้องแตกนะ 🤔🍖",
                soundAction = { RobotSoundPlayer.playConfused() },
                needsUpdate = { it.feed() },
                durationMs = 2500L
            )
            else -> EmotionTransitionResult(
                emotion = AvatarEmotion.HAPPY,
                statusText = "งั่มๆๆ อร่อยมากเลยฮับ! ขอบคุณนะฮับ 🍖✨",
                soundAction = { RobotSoundPlayer.playCrunchEat() },
                needsUpdate = { it.feed() },
                durationMs = 2500L
            )
        }
    }

    private fun resolveClean(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        return when {
            needs.hygiene < 30f -> EmotionTransitionResult(
                emotion = AvatarEmotion.HAPPY,
                statusText = "อาบน้ำสะอาดแล้ว! หอมฟุ้งเลยย ✨🧼🎉",
                soundAction = { RobotSoundPlayer.playBubblePop() },
                needsUpdate = { it.clean() },
                durationMs = 2500L
            )
            needs.hygiene > 80f -> EmotionTransitionResult(
                emotion = AvatarEmotion.POUT,
                statusText = "สะอาดอยู่แล้วนะ! ไม่ต้องอาบอีกก็ได้น้าา 😤🧼",
                soundAction = { RobotSoundPlayer.playConfused() },
                needsUpdate = { it.clean() },
                durationMs = 2500L
            )
            else -> EmotionTransitionResult(
                emotion = AvatarEmotion.LOVE,
                statusText = "ตัวหอมฟุ้ง สะอาดสดชื่นแล้วฮับบอส 🧼✨",
                soundAction = { RobotSoundPlayer.playBubblePop() },
                needsUpdate = { it.clean() },
                durationMs = 2500L
            )
        }
    }

    private fun resolvePlay(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        return when {
            needs.energy < 20f -> EmotionTransitionResult(
                emotion = AvatarEmotion.POUT,
                statusText = "เหนื่อยมาก... ให้พักก่อนได้ป่าว 🥺💤",
                soundAction = { RobotSoundPlayer.playYawn() },
                needsUpdate = { it.copy(
                    stress = (it.stress + 5f).coerceIn(0f, 100f)
                ) },
                durationMs = 2500L
            )
            needs.isHungry -> EmotionTransitionResult(
                emotion = AvatarEmotion.CONFUSED,
                statusText = "หิวอยู่นะ ให้กินก่อนค่อยเล่นได้ป่ะ 🍖🤔",
                soundAction = { RobotSoundPlayer.playConfused() },
                needsUpdate = { it.play() },
                durationMs = 2500L
            )
            else -> EmotionTransitionResult(
                emotion = AvatarEmotion.EXCITED,
                statusText = "เย้ๆๆ สนุกที่สุดเลยย ดิ้นไม่หยุดแล้วว 🎾🎉",
                soundAction = { RobotSoundPlayer.playBellToy() },
                needsUpdate = { it.play() },
                durationMs = 2500L
            )
        }
    }

    private fun resolveSleep(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        return when {
            currentEmotion == AvatarEmotion.SLEEPING -> EmotionTransitionResult(
                emotion = AvatarEmotion.SLEEPING,
                statusText = "หลับอยู่แล้วน้า... Zzz 💤",
                soundAction = { RobotSoundPlayer.playSnore() },
                durationMs = Long.MAX_VALUE
            )
            needs.energy > 80f -> EmotionTransitionResult(
                emotion = AvatarEmotion.CONFUSED,
                statusText = "ยังไม่ง่วงเลยนะ! พลังงานเหลืออีกเยอะ! ⚡🤔",
                soundAction = { RobotSoundPlayer.playConfused() },
                durationMs = 2500L
            )
            else -> EmotionTransitionResult(
                emotion = AvatarEmotion.SLEEPING,
                statusText = "หาวว... ง่วงจริงด้วย นอนพักก่อนน้า Zzz 💤",
                soundAction = { RobotSoundPlayer.playYawn() },
                needsUpdate = { it.rest(10f) },
                durationMs = Long.MAX_VALUE
            )
        }
    }

    fun resolveShake(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        if (currentEmotion == AvatarEmotion.SLEEPING) {
            return EmotionTransitionResult(
                emotion = AvatarEmotion.SURPRISED,
                statusText = "แ-แผ่นดินไหวเหรอ!? ตกใจจจจ!! 😱🌀",
                soundAction = { RobotSoundPlayer.playSurprise() },
                needsUpdate = { it.copy(
                    stress = (it.stress + 20f).coerceIn(0f, 100f),
                    happiness = (it.happiness - 10f).coerceIn(0f, 100f)
                ) },
                durationMs = SURPRISED_DECAY_MS
            )
        }

        // ตรวจจับการเขย่าซ้ำต่อเนื่องใน 10 วินาที
        val recentShakes = countRecentInteractions(InteractionType.SHAKE, 10_000L) +
                countRecentInteractions(InteractionType.HEAVY_SHAKE, 10_000L)

        val newRage = (needs.rage + 18f).coerceIn(0f, 100f)
        if (newRage >= 100f || (recentShakes >= 4 && needs.stress > 50f)) {
            return resolveFightBack(needs, currentEmotion)
        }

        if (recentShakes >= 2) {
            return EmotionTransitionResult(
                emotion = AvatarEmotion.ANGRY,
                statusText = "เขย่าอยู่นั่นแหละ มึนหัวจะแย่แล้วน้าา! 😡🌀",
                soundAction = { RobotSoundPlayer.playAlarm() },
                needsUpdate = { it.copy(
                    stress = (it.stress + 15f).coerceIn(0f, 100f),
                    rage = newRage
                ) },
                durationMs = 3500L
            )
        }

        return EmotionTransitionResult(
            emotion = AvatarEmotion.DIZZY,
            statusText = "โอ๊ยย เวียนหัวจังง! @_@ 🌀",
            soundAction = { RobotSoundPlayer.playConfused() },
            needsUpdate = { it.copy(
                stress = (it.stress + 8f).coerceIn(0f, 100f),
                rage = newRage
            ) },
            durationMs = 3500L
        )
    }

    fun resolveHeavyShake(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        val newRage = (needs.rage + 35f).coerceIn(0f, 100f)
        if (newRage >= 100f) {
            return resolveFightBack(needs, currentEmotion)
        }

        return EmotionTransitionResult(
            emotion = AvatarEmotion.ANGRY,
            statusText = "อย่าเขย่าแรงขนาดนั้นสิ! โกรธแล้วน้าาา! 💢😡🌀",
            soundAction = { RobotSoundPlayer.playAlarm() },
            needsUpdate = { it.copy(
                stress = (it.stress + 20f).coerceIn(0f, 100f),
                happiness = (it.happiness - 15f).coerceIn(0f, 100f),
                rage = newRage
            ) },
            durationMs = 4000L
        )
    }

    fun resolveBoatRocking(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        return EmotionTransitionResult(
            emotion = AvatarEmotion.DIZZY,
            statusText = "อึก... เมาคลื่น... เหมือนนั่งเรือเลยยย @_@ ⛵🤢",
            soundAction = { RobotSoundPlayer.playConfused() },
            needsUpdate = { it.copy(
                stress = (it.stress + 8f).coerceIn(0f, 100f),
                happiness = (it.happiness - 5f).coerceIn(0f, 100f)
            ) },
            durationMs = 4500L
        )
    }

    fun resolveTableThump(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        val newRage = (needs.rage + 15f).coerceIn(0f, 100f)
        return EmotionTransitionResult(
            emotion = AvatarEmotion.SURPRISED,
            statusText = "สะดุ้งหมดเลยยย! ทุบโต๊ะทำไมเนี่ยยย! 😱💥",
            soundAction = { RobotSoundPlayer.playSurprise() },
            needsUpdate = { it.copy(
                stress = (it.stress + 18f).coerceIn(0f, 100f),
                happiness = (it.happiness - 10f).coerceIn(0f, 100f),
                rage = newRage
            ) },
            durationMs = 3000L
        )
    }

    fun resolveLoudNoise(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        val recentLoud = countRecentInteractions(InteractionType.LOUD_NOISE, 20_000L)
        val newRage = (needs.rage + 25f).coerceIn(0f, 100f)

        if (newRage >= 100f) {
            return resolveFightBack(needs, currentEmotion)
        }

        return if (recentLoud <= 1) {
            // ครั้งแรก: ตกใจ
            EmotionTransitionResult(
                emotion = AvatarEmotion.SURPRISED,
                statusText = "อุ๊ย! เสียงดังจัง ตกใจหมดเลยยย! 🙉⚡",
                soundAction = { RobotSoundPlayer.playSurprise() },
                needsUpdate = { it.copy(
                    stress = (it.stress + 12f).coerceIn(0f, 100f),
                    rage = (it.rage + 15f).coerceIn(0f, 100f)
                ) },
                durationMs = 2500L
            )
        } else {
            // ตะคอกซ้ำๆ: เศร้า ร้องไห้
            EmotionTransitionResult(
                emotion = AvatarEmotion.SAD,
                statusText = "ฮืออ... ตะคอกใส่ทำไม อย่าดุน้องสิ 😢💔",
                soundAction = { RobotSoundPlayer.playConfused() },
                needsUpdate = { it.copy(
                    stress = (it.stress + 22f).coerceIn(0f, 100f),
                    happiness = (it.happiness - 20f).coerceIn(0f, 100f),
                    rage = newRage
                ) },
                durationMs = 4500L
            )
        }
    }

    fun resolveFightBack(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        logDebug(TAG, "🚀 FIGHT BACK! Enraged Pet launching missiles at user screen!")
        return EmotionTransitionResult(
            emotion = AvatarEmotion.ENRAGED,
            statusText = "ทนไม่ไหวแล้วน้าาา! โดนมิสซายไปซะเลยยย! 🚀💥🔥",
            soundAction = {
                RobotSoundPlayer.playMissileLaunch()
            },
            triggerMissileBarrage = true,
            needsUpdate = { it.dischargeRage() },
            durationMs = 5500L
        )
    }

    private fun resolveWakeUp(needs: PetNeedsState, currentEmotion: AvatarEmotion): EmotionTransitionResult {
        if (currentEmotion != AvatarEmotion.SLEEPING) {
            return EmotionTransitionResult(
                emotion = AvatarEmotion.CONFUSED,
                statusText = "ตื่นอยู่แล้วนะ? 🤔",
                soundAction = { RobotSoundPlayer.playConfused() },
                durationMs = 1500L
            )
        }

        // ถ้าหลับได้นาน → ตื่นแล้วดีใจ, ถ้าหลับไม่นาน → โกรธ
        return if (needs.energy > 50f) {
            EmotionTransitionResult(
                emotion = AvatarEmotion.HAPPY,
                statusText = "ตื่นแล้วค่าา! หลับสบายมาก คิดถึงจังเลย ✨😊",
                soundAction = { RobotSoundPlayer.playWakeUp() },
                durationMs = 2500L
            )
        } else {
            EmotionTransitionResult(
                emotion = AvatarEmotion.ANGRY,
                statusText = "ยังง่วงอยู่น้าา! ปลุกทำไมม! 😡💢",
                soundAction = { RobotSoundPlayer.playAlarm() },
                needsUpdate = { it.copy(
                    stress = (it.stress + 15f).coerceIn(0f, 100f)
                ) },
                durationMs = 3000L
            )
        }
    }

    /**
     * รีเซ็ตประวัติ interaction ทั้งหมด
     */
    fun clearHistory() {
        interactionHistory.clear()
    }

    /**
     * ดึงประวัติ interaction ล่าสุด (สำหรับ PetMemory)
     */
    fun getRecentHistory(count: Int = 20): List<InteractionLog> {
        return interactionHistory.takeLast(count)
    }
}

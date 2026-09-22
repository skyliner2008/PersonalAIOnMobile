package com.skyliner2008.jarvis.sound

/**
 * RobotSound — ประเภทเสียงหุ่นยนต์ที่ commonMain สามารถสั่งเล่นได้
 */
enum class RobotSound {
    HAPPY,
    PURR,
    SURPRISE,
    CONFUSED,
    ALARM,
    YAWN,
    GIGGLE,
    WAKE_UP,
    SNORE,
    /** เสียงบี๊บขึ้นต้นประโยคเมื่อ AI เริ่มพูด (Cute 2-tone rising chirp) */
    CHIRP_START,
    /** เสียงปิ๊บปิดท้ายประโยคเมื่อ AI พูดเสร็จ (Soft gentle descending pip) */
    CHIRP_END,
    /** เสียงรับทราบ/ตรวจจับวัตถุ (Crisp blip) */
    ACKNOWLEDGE,
    /** เสียงประกายวิ้งๆ (Sparkle chime) */
    SPARKLE,
    /** เสียงจรวดมิสซายยิงพุ่งออกไป (Whooshing ascending rocket launch) */
    MISSILE_LAUNCH,
    /** เสียงระเบิดตูม (Deep cinematic blast & explosion rumble) */
    EXPLOSION,
    /** เสียงเคี้ยวอาหารกรุบกรอบ (Crunch/Munch) เมื่อให้อาหาร 🍖 */
    CRUNCH_EAT,
    /** เสียงฟองสบู่แตกเปาะแปะ (Bubble Pops) เมื่ออาบน้ำ 🧼 */
    BUBBLE_POP,
    /** เสียงกระดิ่ง/ลูกบอลของเล่น (Bell Toy chime) เมื่อชวนเล่น 🎾 */
    BELL_TOY,
    /** เสียงเปิดตาแสกนเรดาร์ไซไฟไฮเทค (Futuristic cyber radar scanner sweep) 👁️ */
    SCAN_RADAR,

    // ─── Scene storytelling cues (Pet_Scene_Scripts.md) ───
    /** ของเคลื่อนเข้าฉาก */ WHOOSH,
    /** ของถึงที่ / สวมเข้าที่ */ POP,
    /** ซดเครื่องดื่ม */ SLURP,
    /** เหรียญทอง */ COIN,
    /** ไฟฟ้าช็อต */ ZAP,
    /** ไฟลุกฉ่า */ SIZZLE,
    /** ฝนตก / สายน้ำ */ RAIN,
    /** เสียงเกม 8-bit */ GAME_BLIP,
    /** พิมพ์ / พลิกหน้า */ TYPING,
    /** สะอื้น */ SOB,
    /** แตรชัยชนะ */ FANFARE,
    /** บูตเครื่อง / แสงส่อง */ POWER_UP,
    /** ทำนองสั้นๆ */ MELODY,
    /** หัวใจเต้น */ HEARTBEAT,
    /** วิญญาณลอย */ GHOST
}

/**
 * RobotSoundPlayer — Cross-platform bridge สำหรับเล่นเสียงเอฟเฟกต์หุ่นยนต์
 */
object RobotSoundPlayer {
    var handler: ((RobotSound) -> Unit)? = null

    fun play(sound: RobotSound) {
        handler?.invoke(sound)
    }

    fun playHappy() = play(RobotSound.HAPPY)
    fun playPurr() = play(RobotSound.PURR)
    fun playSurprise() = play(RobotSound.SURPRISE)
    fun playConfused() = play(RobotSound.CONFUSED)
    fun playAlarm() = play(RobotSound.ALARM)
    fun playYawn() = play(RobotSound.YAWN)
    fun playGiggle() = play(RobotSound.GIGGLE)
    fun playWakeUp() = play(RobotSound.WAKE_UP)
    fun playSnore() = play(RobotSound.SNORE)
    fun playChirpStart() = play(RobotSound.CHIRP_START)
    fun playChirpEnd() = play(RobotSound.CHIRP_END)
    fun playAcknowledge() = play(RobotSound.ACKNOWLEDGE)
    fun playSparkle() = play(RobotSound.SPARKLE)
    fun playMissileLaunch() = play(RobotSound.MISSILE_LAUNCH)
    fun playExplosion() = play(RobotSound.EXPLOSION)
    fun playCrunchEat() = play(RobotSound.CRUNCH_EAT)
    fun playBubblePop() = play(RobotSound.BUBBLE_POP)
    fun playBellToy() = play(RobotSound.BELL_TOY)
    fun playScanRadar() = play(RobotSound.SCAN_RADAR)
}


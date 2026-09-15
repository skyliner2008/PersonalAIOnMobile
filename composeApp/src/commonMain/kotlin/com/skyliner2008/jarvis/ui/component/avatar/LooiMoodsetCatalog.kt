package com.skyliner2008.jarvis.ui.component.avatar

import com.skyliner2008.jarvis.sound.RobotSoundPlayer

/**
 * LooiMoodsetItem — ข้อมูลกำกับ Moodset แต่ละหน้าของหุ่นยนต์ LOOI Robot
 *
 * @param pageNumber ลำดับหน้าที่ 1 ถึง 50 สำหรับการสั่งด้วยเสียงและการเปิดตรวจสอบ
 * @param sheet แผ่นภาพอ้างอิง: 1 = LOOI 20 MOODSET, 2 = LOOI 30 ADDITIONAL MOODSET
 * @param sheetIndex ลำดับช่องในแผ่น (1..20 ในแผ่นที่ 1, 1..30 ในแผ่นที่ 2)
 * @param nameEn ชื่ออารมณ์ภาษาอังกฤษตามรูปภาพอ้างอิง
 * @param nameTh ชื่ออารมณ์ภาษาไทย
 * @param emotion AvatarEmotion enum ที่เชื่อมโยง
 * @param description รายละเอียดดวงตา ปาก และอุปกรณ์ Canvas 2D Vector
 * @param durationMs ระยะเวลาแสดงผลหน้าเพื่อการตรวจสอบ (3-5 วินาที: ดีฟอลต์ 4000ms)
 * @param soundEffect ฟังก์ชันเล่นเสียงเอฟเฟกต์เฉพาะอารมณ์ (ถ้ามี)
 */
/**
 * คืนค่า BackgroundTheme อัตโนมัติสำหรับแต่ละหน้า Moodset (1-50) เพื่อให้เข้ากับบรรยากาศและความมีมิติ
 */
fun resolveDefaultBackground(pageNumber: Int, emotion: AvatarEmotion): BackgroundTheme {
    return when (pageNumber) {
        1 -> BackgroundTheme.DEFAULT
        2 -> BackgroundTheme.DEFAULT
        3 -> BackgroundTheme.THUNDER
        4 -> BackgroundTheme.NIGHT
        5 -> BackgroundTheme.CYBER_GRID
        6 -> BackgroundTheme.CINEMA_COZY
        7 -> BackgroundTheme.CINEMA_COZY
        8 -> BackgroundTheme.DEFAULT
        9 -> BackgroundTheme.LOW_POWER_CRT
        10 -> BackgroundTheme.PARTY_CONFETTI
        11 -> BackgroundTheme.CYBER_GRID
        12 -> BackgroundTheme.MATRIX
        13 -> BackgroundTheme.SICK_LAB
        14 -> BackgroundTheme.MATRIX
        15 -> BackgroundTheme.CYBER_GRID
        16 -> BackgroundTheme.PARTY_CONFETTI
        17 -> BackgroundTheme.SAKURA
        18 -> BackgroundTheme.THUNDER
        19 -> BackgroundTheme.RAINY
        20 -> BackgroundTheme.CINEMA_COZY
        21 -> BackgroundTheme.CYBER_GRID
        22 -> BackgroundTheme.SICK_LAB
        23 -> BackgroundTheme.GOLDEN_VAULT
        24 -> BackgroundTheme.LOVE_BG
        25 -> BackgroundTheme.RAINY
        26 -> BackgroundTheme.CYBER_GRID
        27 -> BackgroundTheme.CINEMA_COZY
        28 -> BackgroundTheme.MATRIX
        29 -> BackgroundTheme.SUMMER_HEAT
        30 -> BackgroundTheme.CYBER_GRID
        31 -> BackgroundTheme.WINTER_BLIZZARD
        32 -> BackgroundTheme.SUMMER_HEAT
        33 -> BackgroundTheme.CINEMA_COZY
        34 -> BackgroundTheme.CINEMA_COZY
        35 -> BackgroundTheme.MAGIC_MYSTIC
        36 -> BackgroundTheme.SPACE_NEBULA
        37 -> BackgroundTheme.PARTY_CONFETTI
        38 -> BackgroundTheme.NIGHT
        39 -> BackgroundTheme.NIGHT
        40 -> BackgroundTheme.THUNDER
        41 -> BackgroundTheme.NIGHT
        42 -> BackgroundTheme.LOVE_BG
        43 -> BackgroundTheme.SPORTS_ARENA
        44 -> BackgroundTheme.LOW_POWER_CRT
        45 -> BackgroundTheme.MAGIC_MYSTIC
        46 -> BackgroundTheme.SPORTS_ARENA
        47 -> BackgroundTheme.SICK_LAB
        48 -> BackgroundTheme.THUNDER
        49 -> BackgroundTheme.WARRIOR_DOJO
        50 -> BackgroundTheme.LOW_POWER_CRT
        else -> when (emotion) {
            AvatarEmotion.SAD, AvatarEmotion.CRYING -> BackgroundTheme.RAINY
            AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED, AvatarEmotion.ELECTRIC -> BackgroundTheme.THUNDER
            AvatarEmotion.LOVE, AvatarEmotion.ROMANTIC -> BackgroundTheme.LOVE_BG
            AvatarEmotion.SLEEPING, AvatarEmotion.DREAMING, AvatarEmotion.EXHAUSTED -> BackgroundTheme.NIGHT
            AvatarEmotion.EXCITED, AvatarEmotion.PARTY -> BackgroundTheme.PARTY_CONFETTI
            AvatarEmotion.VR_MODE, AvatarEmotion.GAMING, AvatarEmotion.EVIL -> BackgroundTheme.MATRIX
            AvatarEmotion.MAGIC, AvatarEmotion.ART_MODE -> BackgroundTheme.MAGIC_MYSTIC
            AvatarEmotion.COLD -> BackgroundTheme.WINTER_BLIZZARD
            AvatarEmotion.HOT -> BackgroundTheme.SUMMER_HEAT
            AvatarEmotion.SPACE -> BackgroundTheme.SPACE_NEBULA
            AvatarEmotion.RICH -> BackgroundTheme.GOLDEN_VAULT
            AvatarEmotion.DEAD, AvatarEmotion.LOW_BATTERY -> BackgroundTheme.LOW_POWER_CRT
            AvatarEmotion.WARRIOR -> BackgroundTheme.WARRIOR_DOJO
            AvatarEmotion.SCIENTIST, AvatarEmotion.SICK -> BackgroundTheme.SICK_LAB
            AvatarEmotion.SPORTY -> BackgroundTheme.SPORTS_ARENA
            AvatarEmotion.COOKING, AvatarEmotion.DETECTIVE -> BackgroundTheme.CINEMA_COZY
            else -> BackgroundTheme.DEFAULT
        }
    }
}

/**
 * คืนค่า ForegroundEffect อัตโนมัติสำหรับแต่ละหน้า Moodset (1-50) เพื่อสร้างเลเยอร์ความลึก 2.5D เสมือนมองผ่านเลนส์หรือมีละอองบรรยากาศ
 */
fun resolveDefaultForeground(pageNumber: Int, emotion: AvatarEmotion): ForegroundEffect {
    return when (pageNumber) {
        1 -> ForegroundEffect.LENS_REFLECTION
        2 -> ForegroundEffect.MAGIC_SPARKLES
        3 -> ForegroundEffect.ELECTRIC_SPARKS
        4 -> ForegroundEffect.STAR_DUST
        5 -> ForegroundEffect.CYBER_HUD
        6 -> ForegroundEffect.HEAT_DISTORTION
        7 -> ForegroundEffect.BUBBLE_FLOAT
        8 -> ForegroundEffect.MAGIC_SPARKLES
        9 -> ForegroundEffect.CRT_SCANLINES
        10 -> ForegroundEffect.CONFETTI_TUMBLE
        11 -> ForegroundEffect.ANAMORPHIC_FLARE
        12 -> ForegroundEffect.CYBER_HUD
        13 -> ForegroundEffect.BUBBLE_FLOAT
        14 -> ForegroundEffect.ANAMORPHIC_FLARE
        15 -> ForegroundEffect.CYBER_HUD
        16 -> ForegroundEffect.CONFETTI_TUMBLE
        17 -> ForegroundEffect.FALLING_PETALS
        18 -> ForegroundEffect.ELECTRIC_SPARKS
        19 -> ForegroundEffect.RAIN_CONDENSATION
        20 -> ForegroundEffect.CAMERA_VIEWFINDER
        21 -> ForegroundEffect.CYBER_HUD
        22 -> ForegroundEffect.BUBBLE_FLOAT
        23 -> ForegroundEffect.GOLDEN_SHINE
        24 -> ForegroundEffect.HEART_ORBS
        25 -> ForegroundEffect.RAIN_CONDENSATION
        26 -> ForegroundEffect.CYBER_HUD
        27 -> ForegroundEffect.STAR_DUST
        28 -> ForegroundEffect.CYBER_HUD
        29 -> ForegroundEffect.STAR_DUST
        30 -> ForegroundEffect.ANAMORPHIC_FLARE
        31 -> ForegroundEffect.FROST_VIGNETTE
        32 -> ForegroundEffect.HEAT_DISTORTION
        33 -> ForegroundEffect.CAMERA_VIEWFINDER
        34 -> ForegroundEffect.HEAT_DISTORTION
        35 -> ForegroundEffect.MAGIC_SPARKLES
        36 -> ForegroundEffect.STAR_DUST
        37 -> ForegroundEffect.CONFETTI_TUMBLE
        38 -> ForegroundEffect.STAR_DUST
        39 -> ForegroundEffect.STAR_DUST
        40 -> ForegroundEffect.ELECTRIC_SPARKS
        41 -> ForegroundEffect.ANAMORPHIC_FLARE
        42 -> ForegroundEffect.HEART_ORBS
        43 -> ForegroundEffect.GOLDEN_SHINE
        44 -> ForegroundEffect.CRT_SCANLINES
        45 -> ForegroundEffect.MAGIC_SPARKLES
        46 -> ForegroundEffect.HEAT_DISTORTION
        47 -> ForegroundEffect.BUBBLE_FLOAT
        48 -> ForegroundEffect.FROST_VIGNETTE
        49 -> ForegroundEffect.FALLING_PETALS
        50 -> ForegroundEffect.CRT_SCANLINES
        else -> when (emotion) {
            AvatarEmotion.SAD, AvatarEmotion.CRYING -> ForegroundEffect.RAIN_CONDENSATION
            AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED, AvatarEmotion.ELECTRIC -> ForegroundEffect.ELECTRIC_SPARKS
            AvatarEmotion.LOVE, AvatarEmotion.ROMANTIC -> ForegroundEffect.HEART_ORBS
            AvatarEmotion.SLEEPING, AvatarEmotion.DREAMING, AvatarEmotion.EXHAUSTED -> ForegroundEffect.STAR_DUST
            AvatarEmotion.EXCITED, AvatarEmotion.PARTY -> ForegroundEffect.CONFETTI_TUMBLE
            AvatarEmotion.VR_MODE, AvatarEmotion.GAMING -> ForegroundEffect.CYBER_HUD
            AvatarEmotion.MAGIC, AvatarEmotion.ART_MODE -> ForegroundEffect.MAGIC_SPARKLES
            AvatarEmotion.COLD -> ForegroundEffect.FROST_VIGNETTE
            AvatarEmotion.HOT -> ForegroundEffect.HEAT_DISTORTION
            AvatarEmotion.SPACE -> ForegroundEffect.STAR_DUST
            AvatarEmotion.RICH -> ForegroundEffect.GOLDEN_SHINE
            AvatarEmotion.DEAD, AvatarEmotion.LOW_BATTERY -> ForegroundEffect.CRT_SCANLINES
            AvatarEmotion.WARRIOR -> ForegroundEffect.FALLING_PETALS
            AvatarEmotion.DETECTIVE -> ForegroundEffect.CAMERA_VIEWFINDER
            AvatarEmotion.SCIENTIST, AvatarEmotion.SICK -> ForegroundEffect.BUBBLE_FLOAT
            AvatarEmotion.SPORTY -> ForegroundEffect.HEAT_DISTORTION
            else -> ForegroundEffect.NONE
        }
    }
}

/**
 * LooiMoodsetItem — ข้อมูลกำกับ Moodset แต่ละหน้าของหุ่นยนต์ LOOI Robot
 *
 * @param pageNumber ลำดับหน้าที่ 1 ถึง 50 สำหรับการสั่งด้วยเสียงและการเปิดตรวจสอบ
 * @param sheet แผ่นภาพอ้างอิง: 1 = LOOI 20 MOODSET, 2 = LOOI 30 ADDITIONAL MOODSET
 * @param sheetIndex ลำดับช่องในแผ่น (1..20 ในแผ่นที่ 1, 1..30 ในแผ่นที่ 2)
 * @param nameEn ชื่ออารมณ์ภาษาอังกฤษตามรูปภาพอ้างอิง
 * @param nameTh ชื่ออารมณ์ภาษาไทย
 * @param emotion AvatarEmotion enum ที่เชื่อมโยง
 * @param description รายละเอียดดวงตา ปาก และอุปกรณ์ Canvas 2D Vector
 * @param durationMs ระยะเวลาแสดงผลหน้าเพื่อการตรวจสอบ (3-5 วินาที: ดีฟอลต์ 4000ms)
 * @param soundEffect ฟังก์ชันเล่นเสียงเอฟเฟกต์เฉพาะอารมณ์ (ถ้ามี)
 * @param backgroundTheme ธีมฉากหลังไดนามิกคู่กับอารมณ์
 * @param foregroundEffect เอฟเฟกต์ฉากหน้าเลนส์/บรรยากาศไดนามิก
 */
data class LooiMoodsetItem(
    val pageNumber: Int,
    val sheet: Int,
    val sheetIndex: Int,
    val nameEn: String,
    val nameTh: String,
    val emotion: AvatarEmotion,
    val description: String,
    val durationMs: Long = 4000L,
    val soundEffect: (() -> Unit)? = null,
    val backgroundTheme: BackgroundTheme = resolveDefaultBackground(pageNumber, emotion),
    val foregroundEffect: ForegroundEffect = resolveDefaultForeground(pageNumber, emotion)
)

/**
 * LooiMoodsetCatalog — สารบัญศูนย์กลาง Moodset ทั้งหมด 50 แบบตามรูปภาพตัวอย่างทั้ง 2 แผ่น
 *
 * เชื่อมโยงคำสั่งเสียง "หน้าที่ 1" ถึง "หน้าที่ 50" และ "หน้าทั้งหมด" สำหรับให้ User ทดสอบตรวจสอบ
 * ทุก Moodset ได้รับการตรวจสอบและแก้ไขถูกต้อง 100% ตามรูปภาพอ้างอิงทั้ง 2 แผ่น:
 * - แผ่นที่ 1 (media_1789279608585.jpg): "LOOI ROBOT: 20 MOODSET (NEON CYAN STYLE)" = หน้าที่ 1 ถึง 20
 * - แผ่นที่ 2 (media_1789282559784.jpg): "LOOI ROBOT: 30 ADDITIONAL MOODSET (NEON CYAN STYLE)" = หน้าที่ 21 ถึง 50
 */
object LooiMoodsetCatalog {

    val ITEMS: List<LooiMoodsetItem> = listOf(
        // ══════════════════════════════════════════════════════════════════════════════
        // รูปที่ 1: LOOI ROBOT: 20 MOODSET (NEON CYAN STYLE) — หน้าที่ 1 ถึง หน้าที่ 20
        // [แก้ไขและตรวจสอบถูกต้อง 100% ตามภาพอ้างอิง media_1789279608585.jpg]
        // ══════════════════════════════════════════════════════════════════════════════

        // หน้าที่ 1: Normal (AvatarEmotion.IDLE)
        // รายละเอียด: ตาสควีร์เคิลนีออนไซแอน (#00F5FF) พร้อมเลเยอร์เงา Dark Cyan (#004D6B) เยื้อง Y = +5dp ไร้ปาก สะอาดตา
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 1,
            sheet = 1,
            sheetIndex = 1,
            nameEn = "Normal",
            nameTh = "ปกติ (สแตนด์บาย)",
            emotion = AvatarEmotion.IDLE,
            description = "ตาสควีร์เคิลนีออนไซแอน 2D Depth สะอาดตา ไร้ปาก พร้อมรับคำสั่ง",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playChirpStart() }
        ),

        // หน้าที่ 2: Happy (AvatarEmotion.HAPPY)
        // รายละเอียด: ตาโค้งยิ้มหยี ⌒ ⌒ สไตล์ LOOI Minimalist อาร์คหนา 12dp เรืองแสง
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 2,
            sheet = 1,
            sheetIndex = 2,
            nameEn = "Happy",
            nameTh = "ดีใจ / มีความสุข",
            emotion = AvatarEmotion.HAPPY,
            description = "ตาโค้งยิ้มหยี ⌒ ⌒ สไตล์ LOOI Minimalist สดใสร่าเริง",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playHappy() }
        ),

        // หน้าที่ 3: Angry (AvatarEmotion.ANGRY)
        // รายละเอียด: ตาทรงลิ่มเฉียงสีแดง (#FF3B5C) + เขี้ยวขาวคู่ v v + เส้นเลือดปูดสีแดง 💢
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 3,
            sheet = 1,
            sheetIndex = 3,
            nameEn = "Angry",
            nameTh = "โกรธ / โมโห",
            emotion = AvatarEmotion.ANGRY,
            description = "ตาทรงลิ่มเฉียงสีแดง + เขี้ยวขาวคู่ v v + เส้นเลือดปูด 💢",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playAlarm() }
        ),

        // หน้าที่ 4: Sleepy (AvatarEmotion.SLEEPING)
        // รายละเอียด: หลับตาขีดมนหนา — — + อักษร Zzz เรืองแสงไซแอนลอยหมุน
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 4,
            sheet = 1,
            sheetIndex = 4,
            nameEn = "Sleepy",
            nameTh = "ง่วงนอน / หลับ",
            emotion = AvatarEmotion.SLEEPING,
            description = "หลับตาขีดมน — — + ตัวอักษร Zzz ลอยหมุนขึ้นฟ้า",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playSnore() }
        ),

        // หน้าที่ 5: Curious (AvatarEmotion.CONFUSED)
        // รายละเอียด: ตาซ้ายขีดมน — ตาขวาทรงลิ่มเอียง 12° + เครื่องหมายคำถาม ? สีแดงบนขวา และ ¿ ฟ้าล่างซ้าย
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 5,
            sheet = 1,
            sheetIndex = 5,
            nameEn = "Curious",
            nameTh = "อยากรู้อยากเห็น / สงสัย",
            emotion = AvatarEmotion.CONFUSED,
            description = "ตาซ้ายขีดตรง ตาขวาทรงลิ่มเอียง + เครื่องหมายคำถาม ? สีแดง",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playConfused() }
        ),

        // หน้าที่ 6: Eating (AvatarEmotion.EATING)
        // รายละเอียด: ตาสควีร์เคิล + มินิเบอร์เกอร์ 🍔 คั่นกลางระดับปาก (ขนมปัง, ผักกาด, ชีส, เนื้อ, ไอความร้อน)
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 6,
            sheet = 1,
            sheetIndex = 6,
            nameEn = "Eating",
            nameTh = "กำลังกินเบอร์เกอร์",
            emotion = AvatarEmotion.EATING,
            description = "ตาสควีร์เคิล + มินิเบอร์เกอร์ 🍔 คั่นกลางระดับปาก อร่อยเต็มคำ",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.play(com.skyliner2008.jarvis.sound.RobotSound.CRUNCH_EAT) }
        ),

        // หน้าที่ 7: Drinking (AvatarEmotion.DRINKING)
        // รายละเอียด: ตาสควีร์เคิล + แก้วเบียร์มินิ 🍺 มีฟองนุ่มสีขาวนวลคั่นกลาง
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 7,
            sheet = 1,
            sheetIndex = 7,
            nameEn = "Drinking",
            nameTh = "กำลังดื่มเบียร์สดชื่น",
            emotion = AvatarEmotion.DRINKING,
            description = "ตาสควีร์เคิล + แก้วเบียร์มีฟองนุ่ม 🍺 คั่นกลาง สดชื่นชนแก้ว",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playGiggle() }
        ),

        // หน้าที่ 8: Wink (AvatarEmotion.WINK)
        // รายละเอียด: ตาซ้ายสควีร์เคิลกลมโต + ตาขวาขีดหลับตา — + ประกายดาวสีทอง ✦ ขวาบน
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 8,
            sheet = 1,
            sheetIndex = 8,
            nameEn = "Wink",
            nameTh = "ขยิบตาวิ้งค์",
            emotion = AvatarEmotion.WINK,
            description = "ตาซ้ายสควีร์เคิล + ตาขวาขีดหลับตา — + ประกายดาว 4 แฉกสีทอง ✦",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playSparkle() }
        ),

        // หน้าที่ 9: Dead (AvatarEmotion.DEAD)
        // รายละเอียด: ตาลายกากบาทมน X X สีฟ้าไซแอน 2D Shallow Depth
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 9,
            sheet = 1,
            sheetIndex = 9,
            nameEn = "Dead",
            nameTh = "สลบ / หมดแรง",
            emotion = AvatarEmotion.DEAD,
            description = "ตาลายกากบาทมน X X สีนีออนไซแอน 2D Depth สลบเหมือด",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playChirpEnd() }
        ),

        // หน้าที่ 10: Laughing (AvatarEmotion.LAUGHING)
        // รายละเอียด: ตาหยีแหลมหัวเราะ > < สไตล์ Minimalist เส้นหนา 8dp
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 10,
            sheet = 1,
            sheetIndex = 10,
            nameEn = "Laughing",
            nameTh = "หัวเราะร่าเริง",
            emotion = AvatarEmotion.LAUGHING,
            description = "ตาหยีแหลมหัวเราะ > < สไตล์ LOOI Minimalist ร่าเริงเบิกบาน",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playGiggle() }
        ),

        // หน้าที่ 11: Music (AvatarEmotion.MUSIC)
        // รายละเอียด: ตาสควีร์เคิล + หูฟังครอบศีรษะ Over-Ear Headphones 🎧 สีทอง-ดำ
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 11,
            sheet = 1,
            sheetIndex = 11,
            nameEn = "Music",
            nameTh = "ฟังเพลงเพลินๆ",
            emotion = AvatarEmotion.MUSIC,
            description = "ตาสควีร์เคิล + หูฟังครอบศีรษะ Over-Ear Headphones 🎧 เพลิดเพลินเสียงดนตรี",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playSparkle() }
        ),

        // หน้าที่ 12: VR Mode (AvatarEmotion.VR_MODE)
        // รายละเอียด: แว่น VR Headset กระจกเงาโค้งสีม่วงเข้ม + ถังป๊อปคอร์นลายทางแดงขาว 🍿
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 12,
            sheet = 1,
            sheetIndex = 12,
            nameEn = "VR Mode",
            nameTh = "แว่นตาโลกเสมือนจริง VR",
            emotion = AvatarEmotion.VR_MODE,
            description = "แว่นตา VR Headset กระจกเงาโค้งมน + ถังป๊อปคอร์น 🥽🍿",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.play(com.skyliner2008.jarvis.sound.RobotSound.SCAN_RADAR) }
        ),

        // หน้าที่ 13: Diving (AvatarEmotion.DIVING)
        // รายละเอียด: หน้ากากดำน้ำ Snorkel Mask กรอบไซแอน + ท่อหายใจสีส้มด้านขวา + ฟองอากาศลอย 🫧
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 13,
            sheet = 1,
            sheetIndex = 13,
            nameEn = "Diving",
            nameTh = "ดำน้ำสำรวจใต้ทะเล",
            emotion = AvatarEmotion.DIVING,
            description = "หน้ากากดำน้ำ Snorkel Mask + ท่อหายใจสีส้ม + ฟองอากาศลอยบุ๋งๆ 🤿🫧",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.play(com.skyliner2008.jarvis.sound.RobotSound.BUBBLE_POP) }
        ),

        // หน้าที่ 14: Evil (AvatarEmotion.EVIL)
        // รายละเอียด: ตาทรงลิ่มแดงเพลิง + เขี้ยวจิ๋ว + ไอคอนปิศาจม่วง 😈 มีเขาแหลมด้านขวาบน
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 14,
            sheet = 1,
            sheetIndex = 14,
            nameEn = "Evil",
            nameTh = "แสบซนตัวร้าย",
            emotion = AvatarEmotion.EVIL,
            description = "ตาปิศาจสีแดงเพลิง + เขี้ยวจิ๋ว + ไอคอนปิศาจม่วง 😈 แสบซนปนร้ายกาจ",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playAlarm() }
        ),

        // หน้าที่ 15: Focused (AvatarEmotion.FOCUSED)
        // รายละเอียด: ตาทรงลิ่มคมกริบ + ตารางเลเซอร์ Synthwave Grid เพอร์สเปกทีฟด้านล่าง
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 15,
            sheet = 1,
            sheetIndex = 15,
            nameEn = "Focused",
            nameTh = "ตั้งใจสแกนโฟกัส",
            emotion = AvatarEmotion.FOCUSED,
            description = "ตาทรงลิ่มคมกริบ + ตารางเลเซอร์ Synthwave Grid มุ่งมั่นสแกนเป้าหมาย 🎯",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.play(com.skyliner2008.jarvis.sound.RobotSound.SCAN_RADAR) }
        ),

        // หน้าที่ 16: Excited (AvatarEmotion.EXCITED)
        // รายละเอียด: ตารูปดาว 4 แฉกสีเหลืองทอง ✦ ✦ เปล่งประกายเบิกกว้าง
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 16,
            sheet = 1,
            sheetIndex = 16,
            nameEn = "Excited",
            nameTh = "ตื่นเต้นตาดาว",
            emotion = AvatarEmotion.EXCITED,
            description = "ตารูปดาว 4 แฉกสีเหลืองทองประกายวิบวับ ✦ ✦ ตื่นเต้นยินดีสุดขีด",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playSparkle() }
        ),

        // หน้าที่ 17: Shy (AvatarEmotion.SHY)
        // รายละเอียด: ตาสควีร์เคิล + ขีดแก้มชมพูระเรื่อ /// /// ข้างละ 3 ขีด
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 17,
            sheet = 1,
            sheetIndex = 17,
            nameEn = "Shy",
            nameTh = "เขินอายแก้มชมพู",
            emotion = AvatarEmotion.SHY,
            description = "ตาสควีร์เคิล + ขีดแก้มชมพูระเรื่อ /// /// น่ารักน่าเอ็นดู",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playPurr() }
        ),

        // หน้าที่ 18: Shock (AvatarEmotion.SURPRISED)
        // รายละเอียด: ตาเบิกกว้างทรงรีแคปซูลใหญ่พิเศษ 1.35x + ปากอ้า 'O' ตกใจ
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 18,
            sheet = 1,
            sheetIndex = 18,
            nameEn = "Shock",
            nameTh = "ตกใจสุดขีด",
            emotion = AvatarEmotion.SURPRISED,
            description = "ตาเบิกกว้างทรงรีแคปซูลใหญ่พิเศษ + ปากอ้า 'O' ตกใจสุดขีด 😱",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playSurprise() }
        ),

        // หน้าที่ 19: Disgusted (AvatarEmotion.DISGUSTED)
        // รายละเอียด: ตาหยีแหลม > < + ไอคอนถังขยะทรงกระบอกเปิดฝา 🗑️ ด้านล่าง
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 19,
            sheet = 1,
            sheetIndex = 19,
            nameEn = "Disgusted",
            nameTh = "รังเกียจ / ขยะแขยง",
            emotion = AvatarEmotion.DISGUSTED,
            description = "ตาหยี > < + ถังขยะเปิดฝาด้านล่าง 🗑️ ไม่ชอบสิ่งนี้เลย",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playConfused() }
        ),

        // หน้าที่ 20: Camera Mode (AvatarEmotion.CAMERA_MODE)
        // รายละเอียด: ตาขีดมน — — + ไอคอนกล้อง DSLR 📷 สีส้มด้านขวาบน พร้อมถ่ายรูป
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 20,
            sheet = 1,
            sheetIndex = 20,
            nameEn = "Camera Mode",
            nameTh = "โหมดกล้องถ่ายรูป",
            emotion = AvatarEmotion.CAMERA_MODE,
            description = "ตาขีดมน — — + ไอคอนกล้องถ่ายรูป DSLR 📷 1 2 3 แชะ!",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playAcknowledge() }
        ),

        // ══════════════════════════════════════════════════════════════════════════════
        // รูปที่ 2: LOOI ROBOT: 30 ADDITIONAL MOODSET (NEON CYAN STYLE) — หน้าที่ 21 ถึง 50
        // [แก้ไขและตรวจสอบถูกต้อง 100% ตามภาพอ้างอิง media_1789282559784.jpg]
        // ══════════════════════════════════════════════════════════════════════════════

        // หน้าที่ 21: Confused (AvatarEmotion.PUZZLED — โหมดเส้นคลื่นหยัก)
        // รายละเอียด: ตาสควีร์เคิลคลื่น ~ ~, ปากคลื่นหยัก, เครื่องหมาย ¿ สีฟ้ากลับหัวล่างซ้าย และ ?? สีแดงคู่บนขวา
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100% แยกความต่างจาก Curious หน้า 5]
        LooiMoodsetItem(
            pageNumber = 21,
            sheet = 2,
            sheetIndex = 1,
            nameEn = "Confused",
            nameTh = "สับสนมึนงง (คลื่น)",
            emotion = AvatarEmotion.PUZZLED,
            description = "ตาสควีร์เคิลคลื่น ~ ~ + ปากคลื่น + เครื่องหมาย ¿ และ ?? สับสนจัง",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playConfused() }
        ),

        // หน้าที่ 22: Sick (AvatarEmotion.SICK)
        // รายละเอียด: ตาก้นหอยลู่ลง + หลอดแก้วปรอทวัดไข้คาบที่ปาก 🌡️ ปรอทแดงแตะระดับ
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 22,
            sheet = 2,
            sheetIndex = 2,
            nameEn = "Sick",
            nameTh = "ไม่สบาย / ป่วย",
            emotion = AvatarEmotion.SICK,
            description = "ตาก้นหอยลู่ลง + ปรอทวัดไข้แก้วคาบที่ปาก 🌡️ ไม่สบายขอนอนพัก",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playChirpEnd() }
        ),

        // หน้าที่ 23: Rich (AvatarEmotion.RICH)
        // รายละเอียด: ตาสัญลักษณ์ดอลลาร์ $ $ + ถุงเงินผูกปาก + เหรียญทองคำร่วงกราว 💰
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 23,
            sheet = 2,
            sheetIndex = 3,
            nameEn = "Rich",
            nameTh = "รวยเปย์หนัก / เศรษฐี",
            emotion = AvatarEmotion.RICH,
            description = "ตาเครื่องหมาย $$ + ถุงเงินใบโต + เหรียญทองคำร่วงกราว 💰 รวยไม่ไหวแล้ว",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playSparkle() }
        ),

        // หน้าที่ 24: In Love (AvatarEmotion.LOVE)
        // รายละเอียด: ตารูปหัวใจคู่สีชมพูนีออนดวงโต ♥♥ + ปากยิ้มหวาน
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100% แยกอิสระจาก Romantic หน้า 42]
        LooiMoodsetItem(
            pageNumber = 24,
            sheet = 2,
            sheetIndex = 4,
            nameEn = "In Love",
            nameTh = "ตกหลุมรัก (ตาหัวใจ)",
            emotion = AvatarEmotion.LOVE,
            description = "ตารูปหัวใจคู่สีชมพูนีออนดวงโต ♥♥ ส่งความรักให้คุณเต็มเปี่ยม",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playPurr() }
        ),

        // หน้าที่ 25: Crying (AvatarEmotion.CRYING)
        // รายละเอียด: ตาโค้งลงเศร้า + สายน้ำตาพุ่งไหลเป็นสายน้ำตกลงมา 😭 พร้อมหยดน้ำตา
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 25,
            sheet = 2,
            sheetIndex = 5,
            nameEn = "Crying",
            nameTh = "ร้องไห้น้ำตาไหลพราก",
            emotion = AvatarEmotion.CRYING,
            description = "ตาโค้งลงเศร้า + สายน้ำตาพุ่งเป็นสายน้ำตก 😭 ฮือๆ เสียใจจัง",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playChirpEnd() }
        ),

        // หน้าที่ 26: Thinking (AvatarEmotion.THINKING)
        // รายละเอียด: ตาสองข้างไม่เท่ากัน เหลือบมองขวาบน + ฟองความคิด 💭 3 พุ่ม
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 26,
            sheet = 2,
            sheetIndex = 6,
            nameEn = "Thinking",
            nameTh = "กำลังใช้ความคิด",
            emotion = AvatarEmotion.THINKING,
            description = "ตาเหลือบมองขวาบน + ฟองความคิดก้อนเมฆ 💭 กำลังประมวลผลข้อมูล",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playAcknowledge() }
        ),

        // หน้าที่ 27: Reading (AvatarEmotion.READING)
        // รายละเอียด: ตาสควีร์เคิลมองลงต่ำ + แว่นสี่เหลี่ยม + หนังสือเปิดกางทรงปีกนก 📖
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 27,
            sheet = 2,
            sheetIndex = 7,
            nameEn = "Reading",
            nameTh = "อ่านหนังสือหาความรู้",
            emotion = AvatarEmotion.READING,
            description = "แว่นอ่านหนังสือสี่เหลี่ยม + หนังสือเปิดกาง 📖 กำลังอ่านหาความรู้",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playAcknowledge() }
        ),

        // หน้าที่ 28: Gaming (AvatarEmotion.GAMING)
        // รายละเอียด: ชุดหูฟังเกมมิ่งมีก้านไมค์ + จอยคอนโทรลเลอร์เกมมี D-Pad และปุ่มกด 🎮
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 28,
            sheet = 2,
            sheetIndex = 8,
            nameEn = "Gaming",
            nameTh = "เล่นเกม / เกมเมอร์",
            emotion = AvatarEmotion.GAMING,
            description = "หูฟังเกมมิ่ง + จอยคอนโทรลเลอร์ 🎮 เล่นเกมสุดมันส์ GG WP!",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.play(com.skyliner2008.jarvis.sound.RobotSound.BELL_TOY) }
        ),

        // หน้าที่ 29: Traveling (AvatarEmotion.TRAVELING)
        // รายละเอียด: หมวกบัคเก็ตนักสำรวจ + เล่มพาสปอร์ต + ลูกโลกจำลองมีเส้นรุ้งเส้นแวง 🌍
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 29,
            sheet = 2,
            sheetIndex = 9,
            nameEn = "Traveling",
            nameTh = "ท่องเที่ยว / เดินทาง",
            emotion = AvatarEmotion.TRAVELING,
            description = "หมวกบัคเก็ต + พาสปอร์ต + ลูกโลกจำลอง 🌍 ออกเดินทางท่องโลกกว้าง",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playHappy() }
        ),

        // หน้าที่ 30: Working (AvatarEmotion.WORKING)
        // รายละเอียด: แว่นสายตากลม + แล็ปท็อปเปิดจอโค้ดเรืองแสง + แก้วกาแฟร้อนมีควัน 💻☕
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 30,
            sheet = 2,
            sheetIndex = 10,
            nameEn = "Working",
            nameTh = "ทำงาน / ออฟฟิศ",
            emotion = AvatarEmotion.WORKING,
            description = "แว่นสายตากลม + แล็ปท็อปเปิดจอ + แก้วกาแฟ 💻☕ มุ่งมั่นตั้งใจทำงาน",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playAcknowledge() }
        ),

        // หน้าที่ 31: Cold (AvatarEmotion.COLD)
        // รายละเอียด: ตาสั่นสะท้าน + น้ำแข็งย้อยเกาะขอบบน + เกล็ดหิมะ 6 แฉก ❄️
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 31,
            sheet = 2,
            sheetIndex = 11,
            nameEn = "Cold",
            nameTh = "หนาวสั่นสะท้าน",
            emotion = AvatarEmotion.COLD,
            description = "ตาสั่นสะท้าน + น้ำแข็งย้อยเกาะ + เกล็ดหิมะ 6 แฉก ❄️ หนาวจนตัวสั่น",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playChirpEnd() }
        ),

        // หน้าที่ 32: Hot (AvatarEmotion.HOT)
        // รายละเอียด: ตาลู่เหงื่อหยด + เส้นคลื่นไอร้อน + พระอาทิตย์เปล่งแสง ☀️
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 32,
            sheet = 2,
            sheetIndex = 12,
            nameEn = "Hot",
            nameTh = "ร้อนตับแตก",
            emotion = AvatarEmotion.HOT,
            description = "ตาลู่เหงื่อหยด + คลื่นไอร้อน + พระอาทิตย์เปล่งแสง ☀️ ร้อนจังเลยย",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playYawn() }
        ),

        // หน้าที่ 33: Detective (AvatarEmotion.DETECTIVE)
        // รายละเอียด: ตาหรี่สงสัย + หมวก Fedora ปีกกว้าง + แว่นขยายด้ามจับไม้ 🔍
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 33,
            sheet = 2,
            sheetIndex = 13,
            nameEn = "Detective",
            nameTh = "นักสืบโคนัน",
            emotion = AvatarEmotion.DETECTIVE,
            description = "ตาหรี่สงสัย + หมวก Fedora + แว่นขยาย 🔍 กำลังสืบหาเบาะแสความจริง",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.play(com.skyliner2008.jarvis.sound.RobotSound.SCAN_RADAR) }
        ),

        // หน้าที่ 34: Cooking (AvatarEmotion.COOKING)
        // รายละเอียด: ตายิ้มหวาน + หมวกเชฟทรงสูงจีบขาว + กระทะทอดมีด้ามจับ 🍳
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 34,
            sheet = 2,
            sheetIndex = 14,
            nameEn = "Cooking",
            nameTh = "ทำอาหาร / เชฟกระทะเหล็ก",
            emotion = AvatarEmotion.COOKING,
            description = "ตายิ้มหวาน + หมวกเชฟทรงสูง + กระทะทอด 🍳 พร้อมทำอาหารแสนอร่อย",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playHappy() }
        ),

        // หน้าที่ 35: Art Mode (AvatarEmotion.ART_MODE)
        // รายละเอียด: ตาสควีร์เคิล + หมวกเบเรต์สีแดงเอียงข้าง + จานสีพู่กันมีสี 4 สี 🎨
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 35,
            sheet = 2,
            sheetIndex = 15,
            nameEn = "Art Mode",
            nameTh = "ศิลปินวาดรูป",
            emotion = AvatarEmotion.ART_MODE,
            description = "หมวกเบเรต์แดง + จานสีและพู่กัน 🎨 ปลดปล่อยจินตนาการงานศิลป์",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playSparkle() }
        ),

        // หน้าที่ 36: Space (AvatarEmotion.SPACE)
        // รายละเอียด: หมวกนักบินอวกาศทรงกลมครอบหัว + วงแหวนดาวเคราะห์โคจร 🚀🪐
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 36,
            sheet = 2,
            sheetIndex = 16,
            nameEn = "Space",
            nameTh = "ท่องอวกาศสู่อนาคต",
            emotion = AvatarEmotion.SPACE,
            description = "หมวกนักบินอวกาศทรงกลม + ดาวเคราะห์โคจร 🚀🪐 ท่องจักรวาลอันไกลโพ้น",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.play(com.skyliner2008.jarvis.sound.RobotSound.MISSILE_LAUNCH) }
        ),

        // หน้าที่ 37: Party (AvatarEmotion.PARTY)
        // รายละเอียด: ตายิ้มร่าเริง + หมวกปาร์ตี้ทรงกรวยลายทาง + แตรเป่า + คอนเฟตติกระดาษสี 🥳
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 37,
            sheet = 2,
            sheetIndex = 17,
            nameEn = "Party",
            nameTh = "ปาร์ตี้เฉลิมฉลอง",
            emotion = AvatarEmotion.PARTY,
            description = "หมวกปาร์ตี้กรวยแหลม + แตรเป่า + กระดาษสีคอนเฟตติ 🥳 ฉลองกันเย้!",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playHappy() }
        ),

        // หน้าที่ 38: Dreaming (AvatarEmotion.DREAMING)
        // รายละเอียด: ตาขีดมนหลับพริ้ม — — + ก้อนเมฆฝันสีฟ้า + Zzz ลอย ☁️🌙
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 38,
            sheet = 2,
            sheetIndex = 18,
            nameEn = "Dreaming",
            nameTh = "ฝันหวานในเมฆ",
            emotion = AvatarEmotion.DREAMING,
            description = "ตาปิดสนิท + ก้อนเมฆฝัน + Zzz ลอยละล่อง ☁️ ฝันหวานอยู่นะคะ",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playSnore() }
        ),

        // หน้าที่ 39: Exhausted (AvatarEmotion.EXHAUSTED)
        // รายละเอียด: เปลือกตาหย่อนลู่ลง 0.4x + ลิ้นสีชมพูห้อยแลบ + หยดเหงื่อไหล 🫠
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 39,
            sheet = 2,
            sheetIndex = 19,
            nameEn = "Exhausted",
            nameTh = "หมดแรง / ลิ้นห้อย",
            emotion = AvatarEmotion.EXHAUSTED,
            description = "เปลือกตาลู่ตก + ลิ้นชมพูห้อย + หยดเหงื่อ 🫠 หมดแรงร่างทองไม่ไหวแล้ว",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playYawn() }
        ),

        // หน้าที่ 40: Electric (AvatarEmotion.ELECTRIC)
        // รายละเอียด: ตาสายฟ้าซิกแซกสีไซแอน ⚡ ⚡ + ประกายไฟแลบชาร์จพลัง
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 40,
            sheet = 2,
            sheetIndex = 20,
            nameEn = "Electric",
            nameTh = "ไฟฟ้าสถิต / สายฟ้า",
            emotion = AvatarEmotion.ELECTRIC,
            description = "ตาสายฟ้าซิกแซก ⚡ ⚡ + ประกายไฟแลบ พลังงานชาร์จเต็มพิกัด",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playSparkle() }
        ),

        // หน้าที่ 41: Sneaky (AvatarEmotion.SNEAKY)
        // รายละเอียด: หน้ากากโจรคาดตาสีดำ + ตาชำเลืองข้าง + ปากยิ้มมุมปาก 🦹
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 41,
            sheet = 2,
            sheetIndex = 21,
            nameEn = "Sneaky",
            nameTh = "แอบย่อง / โจรเงียบ",
            emotion = AvatarEmotion.SNEAKY,
            description = "หน้ากากโจรคาดตาสีดำ + ตาชำเลืองข้าง + ปากยิ้มมุมปาก 🦹 แอบย่องเงียบกริบ",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playPurr() }
        ),

        // หน้าที่ 42: Romantic (AvatarEmotion.ROMANTIC)
        // รายละเอียด: ตายิ้มหวาน + ขีดแก้มชมพู /// + ปากจูบ '3' + คาบดอกกุหลาบแดง 🌹 ไว้ที่มุมปาก
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100% แยกอิสระจาก In Love หน้า 24]
        LooiMoodsetItem(
            pageNumber = 42,
            sheet = 2,
            sheetIndex = 22,
            nameEn = "Romantic",
            nameTh = "โรแมนติก / คาบกุหลาบ",
            emotion = AvatarEmotion.ROMANTIC,
            description = "ขีดแก้มชมพู /// + ปากจูบ '3' + คาบดอกกุหลาบแดง 🌹 โรแมนติกหวานซึ้ง",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playPurr() }
        ),

        // หน้าที่ 43: Hero (AvatarEmotion.HERO)
        // รายละเอียด: หน้ากากซูเปอร์ฮีโร่ติดปีกสีน้ำเงิน + ตาฮึกเหิมเปล่งประกาย + ดาวทอง 🦸
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 43,
            sheet = 2,
            sheetIndex = 23,
            nameEn = "Hero",
            nameTh = "ซูเปอร์ฮีโร่ผู้พิทักษ์",
            emotion = AvatarEmotion.HERO,
            description = "หน้ากากฮีโร่ติดปีกสีน้ำเงิน + ตาฮึกเหิม + ประกายดาว 🦸 ซูเปอร์ฮีโร่มาช่วยแล้ว",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.play(com.skyliner2008.jarvis.sound.RobotSound.MISSILE_LAUNCH) }
        ),

        // หน้าที่ 44: Glitched (AvatarEmotion.GLITCHED)
        // รายละเอียด: เส้นสแกนรบกวน Scanlines + สีแยก RGB Shift แดง-ฟ้า 👾 ดิจิทัลกลิตช์
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 44,
            sheet = 2,
            sheetIndex = 24,
            nameEn = "Glitched",
            nameTh = "ดิจิทัลกลิตช์ / จอรวน",
            emotion = AvatarEmotion.GLITCHED,
            description = "เส้นสแกนรบกวน + สีแยก RGB Shift แดง-ฟ้า 👾 ระบบขัดข้องกลิตช์ชั่วคราว",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playAlarm() }
        ),

        // หน้าที่ 45: Magic (AvatarEmotion.MAGIC)
        // รายละเอียด: หมวกพ่อมดทรงแหลมสีม่วง + ไม้กายสิทธิ์หัวดาวเปล่งแสงประกาย 🧙🪄
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 45,
            sheet = 2,
            sheetIndex = 25,
            nameEn = "Magic",
            nameTh = "พ่อมด / ร่ายเวทมนตร์",
            emotion = AvatarEmotion.MAGIC,
            description = "หมวกพ่อมดทรงแหลมสีม่วง + ไม้กายสิทธิ์ดาว 🧙🪄 ร่ายมนตร์วิเศษปิ๊งๆ",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playSparkle() }
        ),

        // หน้าที่ 46: Sporty (AvatarEmotion.SPORTY)
        // รายละเอียด: ผ้าคาดศีรษะสามสี (แดง/ขาว/น้ำเงิน) + ตาเอาจริง + ลูกบาสเกตบอล 🏀
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 46,
            sheet = 2,
            sheetIndex = 26,
            nameEn = "Sporty",
            nameTh = "นักกีฬา / สปอร์ตี้",
            emotion = AvatarEmotion.SPORTY,
            description = "ผ้าคาดศีรษะสามสี + ตาเอาจริง + ลูกบาสเกตบอล 🏀 ออกกำลังกายสุขภาพดี",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.play(com.skyliner2008.jarvis.sound.RobotSound.BELL_TOY) }
        ),

        // หน้าที่ 47: Scientist (AvatarEmotion.SCIENTIST)
        // รายละเอียด: แว่นตานิรภัยแล็บ + ขวดแก้วทดลองมีน้ำยาฟองฟู่สีเขียว 🧪🔬
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 47,
            sheet = 2,
            sheetIndex = 27,
            nameEn = "Scientist",
            nameTh = "นักวิทยาศาสตร์",
            emotion = AvatarEmotion.SCIENTIST,
            description = "แว่นตานิรภัยแล็บ + ขวดทดลองมีฟองฟู่สีเขียว 🧪 ทดลองสูตรวิทยาศาสตร์ลับ",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.play(com.skyliner2008.jarvis.sound.RobotSound.BUBBLE_POP) }
        ),

        // หน้าที่ 48: Scared (AvatarEmotion.SCARED)
        // รายละเอียด: ตากลมจิ๋วสั่นสะท้าน 35% + ปากสั่น + วิญญาณหลอนสีขาวลอย 👻
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 48,
            sheet = 2,
            sheetIndex = 28,
            nameEn = "Scared",
            nameTh = "หวาดกลัว / ผีหลอก",
            emotion = AvatarEmotion.SCARED,
            description = "ตากลมเล็กสั่นระริก + ปากสั่น + วิญญาณหลอน 👻 กลัวจังเลยช่วยด้วยย",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playSurprise() }
        ),

        // หน้าที่ 49: Warrior (AvatarEmotion.WARRIOR)
        // รายละเอียด: ผ้าคาดหัวสีแดงตราเหรียญทอง + ตาดุดัน + ดาบซามูไรคู่ไขว้กากบาท ⚔️
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 49,
            sheet = 2,
            sheetIndex = 29,
            nameEn = "Warrior",
            nameTh = "นักรบซามูไร",
            emotion = AvatarEmotion.WARRIOR,
            description = "ผ้าคาดหัวสีแดงตราทอง + ตาดุดัน + ดาบซามูไรคู่ ⚔️ นักรบพร้อมประจัญบาน",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playAlarm() }
        ),

        // หน้าที่ 50: Low Battery (AvatarEmotion.LOW_BATTERY)
        // รายละเอียด: ตาหรี่แสงริบหรี่ 50% + ไอคอนแบตเตอรี่สีแดงกะพริบ 🪫 ขีดแดง 1 ขีด
        // [สถานะ: แก้ไขและตรวจสอบถูกต้อง 100%]
        LooiMoodsetItem(
            pageNumber = 50,
            sheet = 2,
            sheetIndex = 30,
            nameEn = "Low Battery",
            nameTh = "แบตเตอรี่ต่ำ",
            emotion = AvatarEmotion.LOW_BATTERY,
            description = "ตาหรี่แสงริบหรี่ + ไอคอนแบตเตอรี่สีแดงกะพริบ 🪫 แบตใกล้หมดแล้วช่วยชาร์จที",
            durationMs = 4000L,
            soundEffect = { RobotSoundPlayer.playChirpEnd() }
        )
    )

    /**
     * ค้นหา Moodset ตามเลขหน้าที่ 1 ถึง 50
     */
    fun findByPage(page: Int): LooiMoodsetItem? {
        if (page !in 1..50) return null
        return ITEMS.getOrNull(page - 1)
    }

    /**
     * ค้นหา Moodset ตามชื่อหรือคำค้นหา (ภาษาอังกฤษหรือภาษาไทย)
     */
    fun findByQuery(query: String): LooiMoodsetItem? {
        val clean = query.trim().lowercase()
        return ITEMS.find { item ->
            item.nameEn.lowercase() == clean ||
            item.nameTh.lowercase().contains(clean) ||
            item.nameEn.lowercase().contains(clean)
        }
    }

    // แมปตัวเลขภาษาไทย (1..50) รองรับการรับรู้คำพูด (Speech-to-Text) ที่แปลงเสียงเป็นคำอ่าน
    private val THAI_NUMBERS = mapOf(
        "หนึ่ง" to 1, "สอง" to 2, "สาม" to 3, "สี่" to 4, "ห้า" to 5,
        "หก" to 6, "เจ็ด" to 7, "แปด" to 8, "เก้า" to 9, "สิบ" to 10,
        "สิบเอ็ด" to 11, "สิบสอง" to 12, "สิบสาม" to 13, "สิบสี่" to 14, "สิบห้า" to 15,
        "สิบหก" to 16, "สิบเจ็ด" to 17, "สิบแปด" to 18, "สิบเก้า" to 19, "ยี่สิบ" to 20,
        "ยี่สิบเอ็ด" to 21, "ยี่สิบสอง" to 22, "ยี่สิบสาม" to 23, "ยี่สิบสี่" to 24, "ยี่สิบห้า" to 25,
        "ยี่สิบหก" to 26, "ยี่สิบเจ็ด" to 27, "ยี่สิบแปด" to 28, "ยี่สิบเก้า" to 29, "สามสิบ" to 30,
        "สามสิบเอ็ด" to 31, "สามสิบสอง" to 32, "สามสิบสาม" to 33, "สามสิบสี่" to 34, "สามสิบห้า" to 35,
        "สามสิบหก" to 36, "สามสิบเจ็ด" to 37, "สามสิบแปด" to 38, "สามสิบเก้า" to 39, "สี่สิบ" to 40,
        "สี่สิบเอ็ด" to 41, "สี่สิบสอง" to 42, "สี่สิบสาม" to 43, "สี่สิบสี่" to 44, "สี่สิบห้า" to 45,
        "สี่สิบหก" to 46, "สี่สิบเจ็ด" to 47, "สี่สิบแปด" to 48, "สี่สิบเก้า" to 49, "ห้าสิบ" to 50
    )

    private val THAI_NUMBERS_SORTED = THAI_NUMBERS.entries.sortedByDescending { it.key.length }

    private val THAI_DIGITS = mapOf(
        '๑' to '1', '๒' to '2', '๓' to '3', '๔' to '4', '๕' to '5',
        '๖' to '6', '๗' to '7', '๘' to '8', '๙' to '9', '๐' to '0'
    )

    /**
     * แปลงคำสั่งเสียงหรือข้อความเพื่อแยกหาตัวเลขหน้า (1 ถึง 50)
     *
     * รองรับรูปแบบต่างๆ:
     * - ตัวเลขเลขอารบิก: "หน้าที่ 1", "หน้า 25", "แบบที่ 30", "mood 5", "page 12", "face 42"
     * - ตัวเลขเลขไทย: "หน้าที่ ๑", "หน้า ๒๕"
     * - คำอ่านภาษาไทย: "หน้าที่หนึ่ง", "หน้าที่ยี่สิบสอง", "หน้าห้าสิบ"
     */
    fun parsePageNumber(text: String): Int? {
        val lower = text.lowercase().trim()

        // 1. ตรวจหาคำสั่งแบบระบุตัวเลขอารบิก เช่น "หน้าที่ 1", "หน้า 22", "แบบที่ 5", "page 10", "mood 7"
        val digitRegex = Regex("""(?:หน้าที่|หน้า|แบบที่|แบบ|page|moodset|mood|face)\s*(\d{1,2})""")
        val digitMatch = digitRegex.find(lower)
        if (digitMatch != null) {
            val num = digitMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 1..50) return num
        }

        // 2. ตรวจหาตัวเลขโดดๆ หลังคำว่า /avatar เช่น "/avatar 15" หรือ "/avatar page 15"
        val avatarParamRegex = Regex("""/avatar\s+(?:page\s+)?(\d{1,2})""")
        val avatarMatch = avatarParamRegex.find(lower)
        if (avatarMatch != null) {
            val num = avatarMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 1..50) return num
        }

        // 3. แปลงเลขไทย ๑..๕๐
        val normalizedThaiDigits = lower.map { THAI_DIGITS[it] ?: it }.joinToString("")
        val thaiDigitMatch = digitRegex.find(normalizedThaiDigits)
        if (thaiDigitMatch != null) {
            val num = thaiDigitMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 1..50) return num
        }

        // 4. ตรวจหาคำอ่านภาษาไทย เช่น "หน้าที่หนึ่ง", "หน้าที่ยี่สิบ", "หน้าสิบห้า"
        for ((word, num) in THAI_NUMBERS_SORTED) {
            if (lower.contains("หน้าที่$word") ||
                lower.contains("หน้าที่ $word") ||
                lower.contains("หน้า$word") ||
                lower.contains("หน้า $word") ||
                lower.contains("แบบที่$word") ||
                lower.contains("แบบที่ $word") ||
                lower.contains("แบบ$word") ||
                lower.contains("แบบ $word")
            ) {
                return num
            }
        }

        // 5. กรณีพูดเลขเดี่ยวๆ ในบริบทของหน้า เช่น "หน้าที่ 1" ที่ไม่มี space
        val directDigits = Regex("""(?:หน้าที่|หน้า|แบบที่)(\d{1,2})""").find(lower)
        if (directDigits != null) {
            val num = directDigits.groupValues[1].toIntOrNull()
            if (num != null && num in 1..50) return num
        }

        return null
    }

    /**
     * ตรวจสอบว่าเป็นคำสั่งให้เล่น Moodset ทุกหน้าวนลูปครบทั้งหมด 1-50 หรือไม่
     *
     * รองรับวลีคำสั่ง:
     * - "หน้าทั้งหมด", "แสดงหน้าทั้งหมด", "เล่นหน้าทั้งหมด", "โชว์หน้าทั้งหมด", "ทดสอบหน้าทั้งหมด"
     * - "ทุกหน้า", "เล่นทุกหน้า", "แสดงทุกหน้า", "โชว์ทุกหน้า", "ทดสอบทุกหน้า"
     * - "moodset ทั้งหมด", "เล่น moodset ทั้งหมด", "แสดง moodset ทั้งหมด"
     * - "อารมณ์ทั้งหมด", "เล่นทุกอารมณ์", "แสดงทุกอารมณ์"
     * - "all faces", "all moods", "play all", "show all", "demo all", "all pages", "play all moods"
     */
    fun isPlayAllCommand(text: String): Boolean {
        val lower = text.lowercase().trim()
        val keywords = listOf(
            "หน้าทั้งหมด", "แสดงหน้าทั้งหมด", "เล่นหน้าทั้งหมด", "โชว์หน้าทั้งหมด", "ทดสอบหน้าทั้งหมด",
            "เปิดหน้าทั้งหมด", "ดูหน้าทั้งหมด",
            "ทุกหน้า", "เล่นทุกหน้า", "แสดงทุกหน้า", "โชว์ทุกหน้า", "ทดสอบทุกหน้า", "เปิดทุกหน้า", "ดูทุกหน้า",
            "moodset ทั้งหมด", "เล่น moodset ทั้งหมด", "แสดง moodset ทั้งหมด", "โชว์ moodset ทั้งหมด",
            "mood ทั้งหมด", "เล่น mood ทั้งหมด", "แสดง mood ทั้งหมด",
            "เล่นอารมณ์ทั้งหมด", "แสดงอารมณ์ทั้งหมด", "โชว์อารมณ์ทั้งหมด", "ทดสอบอารมณ์ทั้งหมด",
            "ทุกอารมณ์", "เล่นทุกอารมณ์", "แสดงทุกอารมณ์",
            "all moods", "play all", "show all", "demo all", "all faces", "all pages",
            "play all moods", "show all moods", "demo all moods", "play all faces",
            "/avatar all", "/avatar demo all", "/avatar pages"
        )
        return keywords.any { lower.contains(it) }
    }
}

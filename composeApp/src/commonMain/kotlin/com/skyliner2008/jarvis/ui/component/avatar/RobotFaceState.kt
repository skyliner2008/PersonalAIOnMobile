package com.skyliner2008.jarvis.ui.component.avatar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * EyeStyle — รูปแบบดวงตาบน LED Visor
 * ใช้ร่วมกับ AvatarEmotion เพื่อให้ AI กำหนดรูปตาได้อิสระจากอารมณ์
 */
enum class EyeStyle {
    /** ดวงตากลม LED สีฟ้าปกติ */
    DEFAULT,
    /** ขยิบตาข้างซ้าย (ปิดตาข้าง เปิดตาข้าง) */
    WINK,
    /** ตารูปหัวใจ ♥ ♥ สีชมพู */
    HEART,
    /** ตาร้องไห้ น้ำตาหยดแอนิเมชัน */
    CRYING,
    /** ตาดาว ★ ★ สีทอง (ตื่นเต้น/ทึ่ง) */
    STAR,
    /** ตาเครื่องหมายคำถาม ? */
    QUESTION,
    /** ตากากบาท X X สีแดง */
    CROSS,
    /** ตาก้นหอยหมุน @_@ */
    SPIRAL,
    /** ตายิ้มโค้งมน ⌒ ⌒ สไตล์ Happy */
    HAPPY,
    /** ตาเฉียงดุดันสีแดงเพลิง สไตล์ Angry */
    ANGRY,
    /** ตาขีดนอน — — สไตล์ Sleepy */
    SLEEPY,
    /** ตาเลิ่กลั่กข้างขีดข้างลิ่ม ? สไตล์ Curious */
    CURIOUS,
    /** ตาลูกศร > < สไตล์ Laughing */
    LAUGHING,
    /** ตาทรงลิ่มเฉียงคมกริบ สไตล์ Focused */
    FOCUSED,
    /** ตาทรงพัดเฉียงออกด้านนอก สไตล์ Excited */
    EXCITED,
    /** ตากลม Squircle พร้อมแก้มชมพู สไตล์ Shy */
    SHY,
    /** ตาเบิกกว้างพิเศษ 1.25x ! สไตล์ Shock */
    SHOCK
}

/**
 * BackgroundTheme — ธีมฉากหลังรอบหัวหุ่นยนต์ เปลี่ยนบรรยากาศตามอารมณ์/บริบท
 */
enum class BackgroundTheme {
    /** Ambient dark OLED */
    DEFAULT,
    /** ฝนตก: ฟ้าเทา + เม็ดฝนตก canvas particles */
    RAINY,
    /** แดดจ้า: Gradient เหลือง-ส้ม + รัศมีแสง pulse */
    SUNNY,
    /** กลางคืน: น้ำเงินเข้ม-ดำ + ดาวกะพริบ */
    NIGHT,
    /** ซากุระ: ชมพูอ่อน + กลีบซากุระหล่น */
    SAKURA,
    /** Matrix: เขียว-ดำ + ตัวอักษร rain */
    MATRIX,
    /** ความรัก: ชมพู-แดง + หัวใจลอย */
    LOVE_BG,
    /** พายุฟ้าผ่า: เทาเข้ม + flash */
    THUNDER,
    // ─── 50 Moodset Dynamic Environments ───
    /** ไซเบอร์กริด 3D Perspective + ละอองข้อมูล */
    CYBER_GRID,
    /** อวกาศเนบิวลาลึก 2 ชั้น + ดาวระยิบระยับ */
    SPACE_NEBULA,
    /** หมอกมนตราสีม่วง + อักขระเวทมนตร์หมุนวน */
    MAGIC_MYSTIC,
    /** โรงหนัง/คาเฟ่ บรรยากาศไฟโบเก้อบอุ่น */
    CINEMA_COZY,
    /** พายุหิมะพัดโปรยปราย + ลมหนาว */
    WINTER_BLIZZARD,
    /** แสงแดดแผดจ้า + คลื่นไอความร้อน */
    SUMMER_HEAT,
    /** ซามูไรโดโจ ท้องฟ้าชาด + ประกายไฟลอย */
    WARRIOR_DOJO,
    /** ปาร์ตี้แสงสี + ริบบิ้นฉลอง */
    PARTY_CONFETTI,
    /** คลังสมบัติทองคำ แสงออร่าทองอร่าม */
    GOLDEN_VAULT,
    /** ห้องทดลองเคมีเรืองแสงเขียว + ฟองเดือด */
    SICK_LAB,
    /** สนามกีฬาสปอตไลต์ส่องสว่าง */
    SPORTS_ARENA,
    /** จอแก้ว CRT แบตต่ำ ไฟกะพริบเตือนสีแดง */
    LOW_POWER_CRT
}

/**
 * ForegroundEffect — เอฟเฟกต์ฉากหน้า/ชั้นหน้าเลนส์ เพิ่มมิติความลึกแบบ 2.5D (Parallax & Atmospheric Depth)
 */
enum class ForegroundEffect {
    /** ไม่มีเอฟเฟกต์หน้า */
    NONE,
    /** แถบกรอบไซเบอร์ HUD + ฝุ่นดิจิทัล */
    CYBER_HUD,
    /** ละอองละอองดวงดาวโบเก้ลอยใกล้เลนส์ */
    STAR_DUST,
    /** ประกายดาวเวทมนตร์ลอยฟุ้งข้างหน้า */
    MAGIC_SPARKLES,
    /** แสงสะท้อนผิวกระจกโค้งแบบแว่น VR/หน้ากาก */
    LENS_REFLECTION,
    /** เกล็ดน้ำแข็งเกาะขอบเลนส์ + หิมะเม็ดใหญ่ลอยผ่าน */
    FROST_VIGNETTE,
    /** คลื่นไอความร้อนระอุ + สะเก็ดไฟลอยใกล้เลนส์ */
    HEAT_DISTORTION,
    /** หยดน้ำเกาะกระจกหน้าจอ */
    RAIN_CONDENSATION,
    /** ประกายประกายสายฟ้าแลบตามขอบหน้าจอ */
    ELECTRIC_SPARKS,
    /** กลีบซากุระสีแดง/ชมพูปลิวผ่านหน้ากล้อง */
    FALLING_PETALS,
    /** สะเก็ดกระดาษฟอยล์สี 3D พลิ้วหมุนผ่านหน้า */
    CONFETTI_TUMBLE,
    /** แสงประกายเพชร/เหรียญทองแวววาว */
    GOLDEN_SHINE,
    /** ลูกแก้วหัวใจฟุ้งลอยขึ้นหน้าจอ */
    HEART_ORBS,
    /** ฟองอากาศลอยผุดผ่านหน้าเลนส์ */
    BUBBLE_FLOAT,
    /** ลำแสงสะท้อนเลนส์แนวนอน (Anamorphic Streak) */
    ANAMORPHIC_FLARE,
    /** เส้นสแกน CRT + ขอบสีเหลื่อม RGB */
    CRT_SCANLINES,
    /** กรอบเล็งกล้องถ่ายรูป Viewfinder + จุดโฟกัส */
    CAMERA_VIEWFINDER
}

/**
 * PropType — อุปกรณ์เสริม/สติกเกอร์ลอยรอบหุ่นยนต์ (คลัง 50+ ชนิด)
 * AI สามารถส่งเป็น List<PropType> หรือ string คั่นด้วยจุลภาค ให้แสดงพร้อมกันได้อิสระ
 */
enum class PropType {
    // ─── หมวดอารมณ์/ความรู้สึก (Mood & Reactions) ───
    /** ♥ หัวใจสีชมพูลอยขึ้น */
    HEARTS,
    /** 💔 หัวใจแตกสลาย (เศร้า/อกหัก) */
    BROKEN_HEART,
    /** ✨ ประกายดาววิบวับ */
    SPARKLES,
    /** 💧 หยดเหงื่อไหล (เขิน/ประหม่า) */
    SWEAT_DROP,
    /** ❓ เครื่องหมายคำถาม (สงสัย/ฉงน) */
    QUESTION_MARK,
    /** ❗ เครื่องหมายตกใจ (ตื่นตัว/แจ้งเตือน) */
    EXCLAMATION,
    /** 💤 Zzz ลอยขึ้น (หลับ/สแตนด์บาย) */
    ZZZZZ,
    /** 🔥 เปลวไฟลุกโชน (ร้อนแรง/พลังเต็มเปี่ยม) */
    FIRE,
    /** 💢 เส้นเลือดปูดสัญลักษณ์ความโกรธ */
    ANGER_VEIN,
    /** 😭 น้ำตาไหลพราก (เศร้า/ซาบซึ้ง) */
    TEARS,
    /** 💫 ดาวหมุนวนรอบหัว (ตาลาย/มึน) */
    DIZZY_STARS,
    /** 💀 กะโหลก (เหนื่อยล้าจนวิญญาณหลุด) */
    SKULL,
    /** 💡 หลอดไฟไอเดียปิ๊งแว๊บ */
    LIGHTBULB,
    /** 🎉 พลุกระดาษเฉลิมฉลอง (ยินดี/สำเร็จ) */
    PARTY_POPPER,
    /** 🎈 ลูกโป่งหลากสีลอยขึ้น */
    BALLOONS,
    /** 👑 มงกุฎทองคำลอยเหนือหัว */
    CROWN,
    /** 😎 แว่นกันแดดสีดำสุดเท่ */
    SUNGLASSES,

    // ─── หมวดอาหาร/เครื่องดื่ม/กิจกรรม (Food & Daily Life) ───
    /** ☕ แก้วกาแฟควันฉุย (เช้า/ทำงาน) */
    COFFEE,
    /** 🍵 ถ้วยชาร้อนผ่อนคลาย */
    TEA_CUP,
    /** 🍺 แก้วเบียร์ชนแก้ว Cheers */
    BEER,
    /** 🍕 ถาดพิซซ่าชีสยืด */
    PIZZA,
    /** 🍔 เบอร์เกอร์แสนอร่อย */
    BURGER,
    /** 🎂 เค้กวันเกิดพร้อมเทียน */
    CAKE,
    /** 🍦 ไอศกรีมโคนหวานเย็น */
    ICE_CREAM,
    /** 🍿 ถังป๊อปคอร์นดูหนัง */
    POPCORN,
    /** 🧋 ชานมไข่มุก */
    BOBA_TEA,
    /** 🎁 กล่องของขวัญผูกโบ */
    GIFT,
    /** 🎮 จอยเกมคอนโทรลเลอร์ */
    GAMING_CONTROLLER,
    /** 📖 หนังสือเปิดกาง (เรียนรู้/อ่าน) */
    BOOK,
    /** ♪♫ โน้ตดนตรีลอยพลิ้ว */
    MUSIC_NOTES,

    // ─── หมวดธรรมชาติ/สภาพอากาศ/อวกาศ (Nature & Weather) ───
    /** ☀️ พระอาทิตย์ส่องแสงเจิดจ้า */
    SUN,
    /** 🌈 สายรุ้งโค้งสดใส */
    RAINBOW,
    /** ☂️ ร่มกันฝนลอยกาง */
    UMBRELLA,
    /** ☁️ ก้อนเมฆนุ่มลอยเอื่อย */
    CLOUD,
    /** ❄️ เกล็ดหิมะโปรยปราย */
    SNOW,
    /** ⚡ สายฟ้าฟาดแลบแปลบปลาบ */
    LIGHTNING,
    /** 🍃 ใบไม้ปลิวไหวตามลม */
    LEAF,
    /** 🌸 กลีบดอกซากุระร่วงหล่น */
    CHERRY_BLOSSOM,
    /** 🌙 พระจันทร์เสี้ยวและดวงดาว */
    MOON_STARS,
    /** 👻 ผีน้อยลอยหลอนน่ารัก */
    GHOST,

    // ─── หมวดไอที/เครื่องมือ/ความปลอดภัย (Tech & Tools) ───
    /** 🚀 จรวดพุ่งทะยานสู่ฟ้า (รวดเร็ว/ล้ำยุค) */
    ROCKET,
    /** ⚙️ ฟันเฟืองกลไกหมุนขบกัน (ประมวลผล) */
    GEARS,
    /** 💻 หน้าจอคอมพิวเตอร์แล็ปท็อป */
    LAPTOP,
    /** ⚡🔋 แบตเตอรี่กำลังชาร์จไฟ */
    BATTERY_CHARGING,
    /** 🪫 แบตเตอรี่สีแดงใกล้หมด */
    BATTERY_LOW,
    /** ⏰ นาฬิกาปลุกสั่นเตือน */
    CLOCK_ALARM,
    /** 🔍 แว่นขยายสแกนค้นหา */
    MAGNIFYING_GLASS,
    /** 🛡️ โล่เกราะป้องกันไซเบอร์ */
    SHIELD,
    /** ⚠️ ป้ายสามเหลี่ยมเตือนภัย */
    WARNING_TRIANGLE,
    /** 🚨 ไซเรนไฟหมุนฉุกเฉิน */
    SIREN_LIGHT,
    /** ✅ เครื่องหมายถูกสีเขียว (สำเร็จ/เรียบร้อย) */
    CHECK_MARK,
    /** 🎯 เป้าเล็งสแกนโฟกัสเป้าหมาย */
    TARGET_RETICLE,
    /** 🐾 รอยเท้าแมวน่ารักตะปบ */
    CAT_PAW,
    /** 🪙 เหรียญทองคำ/ก้อนทองคำลอยประกายวิบวับ */
    GOLD_COIN,
    /** 💧 หยดน้ำฝนโปรยปรายลงมา */
    RAIN_DROPS,
    /** 🎧 หูฟังครอบศีรษะ Over-Ear Headphones (ฟังเพลง) */
    HEADPHONES,
    /** 🥽 แว่นตา VR Headset กระจกโค้งล้ำยุค */
    VR_HEADSET,
    /** 🤿 หน้ากากดำน้ำ Snorkel Mask พร้อมท่อหายใจ */
    SNORKEL_MASK,
    /** 😈 ไอคอนปิศาจ/เขาปิศาจ */
    DEVIL_HORNS,
    /** 🌐 ตารางเลเซอร์ Synthwave Perspective Grid */
    SCANNER_GRID,
    /** 🗑️ ถังขยะเปิดฝาพร้อมไอคอนถังขยะ */
    TRASH_BIN,
    /** 📷 กล้องถ่ายรูปดิจิทัล DSLR */
    CAMERA_ICON,

    // ─── หมวด LOOI Robot 30 Additional Moodset Props ───
    /** 🌡️ ปรอทวัดไข้แก้ว (โหมดป่วย) */
    THERMOMETER,
    /** 💰 ถุงเงินดอลลาร์ทองคำ */
    MONEY_BAG,
    /** 💲 เครื่องหมายดอลลาร์ $ */
    DOLLAR_SIGN,
    /** 💭 ฟองความคิด (โหมดคิด) */
    THOUGHT_BUBBLE,
    /** 👓 แว่นตาทรงเหลี่ยมมนอ่านหนังสือ */
    GLASSES_SQUARE,
    /** 🎧 หูฟังเกมมิ่งพร้อมไมค์ก้าน */
    GAMING_HEADSET,
    /** 👒 หมวกบัคเก็ตนักเดินทาง */
    BUCKET_HAT,
    /** 🛂 พาสปอร์ตเดินทาง */
    PASSPORT,
    /** 🎒 เป้สะพายหลัง */
    BACKPACK,
    /** 🧊 น้ำแข็งย้อยเกาะขอบหน้าจอ */
    ICICLES,
    /** ♨️ คลื่นความร้อนระอุ */
    HEATWAVES,
    /** 🎩 หมวกนักสืบทรงเฟโดร่า */
    FEDORA_HAT,
    /** 👨‍🍳 หมวกกุ๊กเชฟสีขาว */
    CHEF_HAT,
    /** 🍳 ตะหลิวทำอาหาร */
    SPATULA,
    /** 🧑‍🎨 หมวกเบเรต์สีแดง */
    BERET,
    /** 🎨 จานสีศิลปะพร้อมสีแต้ม */
    PALETTE,
    /** 🧑‍🚀 หมวกนักบินอวกาศมีกระจกสะท้อน */
    ASTRONAUT_HELMET,
    /** 🥳 หมวกปาร์ตี้ทรงกรวยลายทาง */
    PARTY_HAT,
    /** 🪈 แตรปาร์ตี้เป่าลม Noisemaker */
    NOISEMAKER,
    /** 🎊 กระดาษโปรยปราย Confetti */
    CONFETTI,
    /** ☁️ ก้อนเมฆความฝันนุ่มฟู */
    DREAM_CLOUD,
    /** ⚡ ดวงตาสายฟ้าแลบแปลบปลาบ */
    LIGHTNING_EYES,
    /** 🦹 หน้ากากโจรคาดตาสีดำ */
    BANDIT_MASK,
    /** 🌹 ดอกกุหลาบสีแดงสด */
    ROSE,
    /** 🦸 ผ้าคลุมซูเปอร์ฮีโร่ */
    SUPERHERO_CAPE,
    /** 🦸‍♂️ หน้ากากฮีโร่คาดตาสีน้ำเงิน */
    SUPERHERO_MASK,
    /** 🧙 หมวกพ่อมดปลายแหลมสีม่วง */
    WIZARD_HAT,
    /** 🪄 ไม้กายสิทธิ์หัวดาว */
    MAGIC_WAND,
    /** 🏃 ผ้าคาดศีรษะออกกำลังกาย */
    SWEATBAND,
    /** 🏀 ลูกบาสเกตบอล */
    BASKETBALL,
    /** 🥽 แว่นตานิรภัยทดลองแล็บ */
    LAB_GOGGLES,
    /** 🧪 ขวดแก้วทดลองบีกเกอร์มีฟองฟู่ */
    BEAKER,
    /** 🪖 หมวกเกราะนักรบไวกิ้งมีเขา */
    VIKING_HELMET,
    /** ⚔️ ดาบสั้นไขว้พร้อมสู้ */
    SWORD
}

/**
 * GestureType — ท่าทาง/ภาษากายของหัวหุ่นยนต์
 */
enum class GestureType {
    /** ลอยหายใจปกติ */
    IDLE,
    /** เอียงซ้าย */
    TILT_LEFT,
    /** เอียงขวา */
    TILT_RIGHT,
    /** กระดอนขึ้นลง สนุกสนาน */
    BOUNCE,
    /** กระโดดขึ้น สปริง */
    JUMP,
    /** โยกตัวซ้ายขวา */
    WOBBLE,
    /** สั่นสะเทือนเร็ว */
    SHAKE,
    /** พยักหน้า */
    NOD
}

/**
 * EyeTrickState — ลูกเล่นตากระโดด/พักหน้าจอ (Screensaver Eye Tricks)
 */
enum class EyeTrickState {
    /** ปกติ: สอดส่องสายตามองตามวัตถุ/ผู้ใช้ */
    NONE,
    /** บีบตัวเข้าหาขอบจอแล้วยิงสลับขอบเด้งดึ๋งไปมา ชนขอบจนมึน */
    PING_PONG_BOUNCE,
    /** เด้งดึ๋งขึ้นๆ ลงๆ ยืดหดอย่างรวดเร็วแล้วเหนื่อยหอบ */
    TIRED_BOUNCE,
    /** หรี่ตา 1 ข้างแล้วยิงอีกข้างพุ่งชนขอบเหมือนแท่งสนุ๊กเกอร์ */
    SNOOKER_SHOT
}

/**
 * RobotFaceState — สถานะจำลองใบหน้าหุ่นยนต์แบบสมบูรณ์
 * รองรับการ Serialize/Deserialize กับ JSON หรือ Command String จาก AI / Tool
 *
 * ตัวอย่าง JSON:
 * ```json
 * {
 *   "emotion": "happy",
 *   "eye_style": "star",
 *   "background": "sunny",
 *   "props": "sparkles,music_notes",
 *   "gesture": "bounce"
 * }
 * ```
 */
@Serializable
data class RobotFaceState(
    @SerialName("emotion") val emotionName: String = "idle",
    @SerialName("eye_style") val eyeStyleName: String = "default",
    @SerialName("background") val backgroundName: String = "default",
    @SerialName("props") val propsRaw: String = "",
    @SerialName("gesture") val gestureName: String = "idle",
    @SerialName("foreground") val foregroundName: String = "none",
    @SerialName("speech_text") val speechText: String? = null,
    @SerialName("custom_props") val customProps: List<DynamicVectorProp> = emptyList(),
    @SerialName("eye_trick") val eyeTrickName: String = "none",
    /** ฉากเรื่องราว (PetSceneArchetype name, lowercase) — Rive เล่น story ของฉากนี้ */
    @SerialName("scene") val sceneName: String = "",
    /** ไอเทมหลักของฉาก (PropType name, lowercase) เช่น pizza / boba_tea / book */
    @SerialName("scene_item") val sceneItem: String = "",
    /** เพิ่มขึ้นทุกครั้งที่เริ่มฉาก เพื่อให้เล่นฉากเดิมซ้ำได้ */
    @SerialName("scene_id") val sceneId: Int = 0
) {
    /** Parse eye trick name → EyeTrickState */
    val eyeTrick: EyeTrickState
        get() = try {
            EyeTrickState.valueOf(eyeTrickName.uppercase())
        } catch (_: Exception) {
            EyeTrickState.NONE
        }

    /** Parse emotion name → AvatarEmotion enum (safe fallback to IDLE) */
    val emotion: AvatarEmotion
        get() = try {
            val clean = emotionName.uppercase().trim().replace(" ", "_")
            when (clean) {
                "NORMAL" -> AvatarEmotion.IDLE
                "CURIOUS" -> AvatarEmotion.CONFUSED
                "SHOCK" -> AvatarEmotion.SURPRISED
                "LAUGH" -> AvatarEmotion.LAUGHING
                "CAMERA" -> AvatarEmotion.CAMERA_MODE
                "VR" -> AvatarEmotion.VR_MODE
                "DEVIL" -> AvatarEmotion.EVIL
                "GRID" -> AvatarEmotion.FOCUSED
                "BLUSH" -> AvatarEmotion.SHY
                "TRASH" -> AvatarEmotion.DISGUSTED
                "HEADPHONES" -> AvatarEmotion.MUSIC
                // 30 Additional Moodset aliases
                "PUZZLED", "CONFUSED_WAVY" -> AvatarEmotion.PUZZLED
                "IN_LOVE" -> AvatarEmotion.LOVE
                "ROMANTIC", "ROSE" -> AvatarEmotion.ROMANTIC
                "SLEEPY" -> AvatarEmotion.SLEEPING
                "CRY" -> AvatarEmotion.CRYING
                "GAME" -> AvatarEmotion.GAMING
                "TRAVEL" -> AvatarEmotion.TRAVELING
                "WORK" -> AvatarEmotion.WORKING
                "FREEZING" -> AvatarEmotion.COLD
                "SWEAT", "WARM" -> AvatarEmotion.HOT
                "CHEF", "COOK" -> AvatarEmotion.COOKING
                "ART" -> AvatarEmotion.ART_MODE
                "ASTRONAUT" -> AvatarEmotion.SPACE
                "CELEBRATION" -> AvatarEmotion.PARTY
                "DREAM" -> AvatarEmotion.DREAMING
                "TIRED", "FATIGUE" -> AvatarEmotion.EXHAUSTED
                "ZAP", "LIGHTNING" -> AvatarEmotion.ELECTRIC
                "THIEF", "NINJA" -> AvatarEmotion.SNEAKY
                "SUPERHERO" -> AvatarEmotion.HERO
                "GLITCH" -> AvatarEmotion.GLITCHED
                "WIZARD", "MAGE" -> AvatarEmotion.MAGIC
                "SPORTS", "ATHLETE" -> AvatarEmotion.SPORTY
                "SCIENCE", "LAB" -> AvatarEmotion.SCIENTIST
                "FEAR", "AFRAID" -> AvatarEmotion.SCARED
                "GLADIATOR", "VIKING" -> AvatarEmotion.WARRIOR
                "BATTERY_LOW", "DYING", "EMPTY_BATTERY" -> AvatarEmotion.LOW_BATTERY
                else -> AvatarEmotion.valueOf(clean)
            }
        } catch (_: Exception) {
            AvatarEmotion.IDLE
        }

    /** Parse eye style name → EyeStyle enum (safe fallback to DEFAULT) */
    val eyeStyle: EyeStyle
        get() = try {
            val clean = eyeStyleName.uppercase().trim().replace(" ", "_")
            when (clean) {
                "NORMAL", "ROUND", "SQUIRCLE" -> EyeStyle.DEFAULT
                "X", "XX" -> EyeStyle.CROSS
                "CHEVRON" -> EyeStyle.LAUGHING
                "ARC", "UPWARD_ARC" -> EyeStyle.HAPPY
                "LINE", "BAR" -> EyeStyle.SLEEPY
                "WEDGE" -> EyeStyle.FOCUSED
                "FAN" -> EyeStyle.EXCITED
                "BLUSH" -> EyeStyle.SHY
                "WIDE" -> EyeStyle.SHOCK
                else -> EyeStyle.valueOf(clean)
            }
        } catch (_: Exception) {
            EyeStyle.DEFAULT
        }

    /** Parse background theme name → BackgroundTheme enum (safe fallback to DEFAULT) */
    val backgroundTheme: BackgroundTheme
        get() = try {
            val name = backgroundName.uppercase().replace(" ", "_")
            BackgroundTheme.valueOf(name)
        } catch (_: Exception) {
            BackgroundTheme.DEFAULT
        }

    /** Parse foreground effect name → ForegroundEffect enum (safe fallback to NONE) */
    val foregroundEffect: ForegroundEffect
        get() = try {
            val name = foregroundName.uppercase().replace(" ", "_")
            ForegroundEffect.valueOf(name)
        } catch (_: Exception) {
            ForegroundEffect.NONE
        }

    /** Parse comma-separated props string → List<PropType> พร้อม Smart Alias & Synonym Resolver */
    val props: List<PropType>
        get() = propsRaw
            .split(",")
            .mapNotNull { raw ->
                resolvePropType(raw)
            }

    /** Parse gesture name → GestureType enum (safe fallback to IDLE) */
    val gesture: GestureType
        get() = try {
            GestureType.valueOf(gestureName.uppercase())
        } catch (_: Exception) {
            GestureType.IDLE
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Parse JSON string → RobotFaceState (safe, returns default on failure) */
        fun fromJson(jsonString: String): RobotFaceState {
            return try {
                json.decodeFromString<RobotFaceState>(jsonString)
            } catch (_: Exception) {
                RobotFaceState()
            }
        }

        /** Build from tool call arguments map */
        fun fromArgs(args: Map<String, String>): RobotFaceState {
            val sceneArg = args["scene"] ?: args["scene_name"]
            if (!sceneArg.isNullOrBlank()) {
                val resolved = com.skyliner2008.jarvis.pet.PetSceneEngine.resolveFromKeyword(sceneArg)
                if (resolved != null) {
                    return resolved.first
                }
            }

            val customPropList = mutableListOf<DynamicVectorProp>()
            val svgPath = args["svg_path"]
            if (!svgPath.isNullOrBlank()) {
                val position = try {
                    PropPosition.valueOf(args["prop_position"]?.uppercase() ?: "FOREHEAD")
                } catch (_: Exception) {
                    PropPosition.FOREHEAD
                }
                val anim = try {
                    DynamicPropAnimation.valueOf(args["prop_anim"]?.uppercase() ?: "FLOAT_BOB")
                } catch (_: Exception) {
                    DynamicPropAnimation.FLOAT_BOB
                }
                customPropList.add(
                    DynamicVectorProp(
                        id = args["prop_name"] ?: "custom_prop",
                        name = args["prop_name"] ?: "custom_prop",
                        svgPath = svgPath,
                        fillColor = args["prop_color"] ?: "#00E5FF",
                        strokeColor = args["prop_stroke"],
                        strokeWidth = args["prop_stroke_width"]?.toFloatOrNull() ?: 2f,
                        position = position,
                        sizeDp = args["prop_size"]?.toFloatOrNull() ?: 48f,
                        animation = anim
                    )
                )
            }
            val customPropsJson = args["custom_props"]
            if (!customPropsJson.isNullOrBlank()) {
                try {
                    val parsed = json.decodeFromString<List<DynamicVectorProp>>(customPropsJson)
                    customPropList.addAll(parsed)
                } catch (_: Exception) {}
            }

            return RobotFaceState(
                emotionName = args["emotion"] ?: "idle",
                eyeStyleName = args["eye_style"] ?: "default",
                backgroundName = args["background"] ?: "default",
                propsRaw = args["props"] ?: "",
                gestureName = args["gesture"] ?: "idle",
                foregroundName = args["foreground"] ?: "none",
                speechText = args["speech_text"],
                customProps = customPropList,
                eyeTrickName = args["eye_trick"] ?: "none"
            )
        }

        /** Smart Prop Resolver รองรับคำพ้องภาษาไทยและอังกฤษ */
        fun resolvePropType(raw: String): PropType? {
            val clean = raw.trim().lowercase()
            if (clean.isBlank()) return null
            return when {
                clean in listOf("glasses", "sunglasses", "shades", "แว่น", "แว่นตา", "แว่นดำ", "แว่นกันแดด") -> PropType.SUNGLASSES
                clean in listOf("crown", "มงกุฎ") -> PropType.CROWN
                clean in listOf("coffee", "กาแฟ") -> PropType.COFFEE
                clean in listOf("boba", "boba_tea", "ชานม", "ชานมไข่มุก") -> PropType.BOBA_TEA
                clean in listOf("tea", "tea_cup", "ชา", "ชาร้อน") -> PropType.TEA_CUP
                clean in listOf("beer", "เบียร์") -> PropType.BEER
                clean in listOf("pizza", "พิซซ่า") -> PropType.PIZZA
                clean in listOf("burger", "เบอร์เกอร์") -> PropType.BURGER
                clean in listOf("cake", "เค้ก") -> PropType.CAKE
                clean in listOf("ice_cream", "ice cream", "ไอศกรีม", "ไอติม") -> PropType.ICE_CREAM
                clean in listOf("popcorn", "ป๊อปคอร์น") -> PropType.POPCORN
                clean in listOf("heart", "hearts", "love", "หัวใจ") -> PropType.HEARTS
                clean in listOf("broken_heart", "หัวใจสลาย") -> PropType.BROKEN_HEART
                clean in listOf("sparkle", "sparkles", "ประกาย", "ดาว") -> PropType.SPARKLES
                clean in listOf("fire", "flame", "ไฟ", "เปลวไฟ") -> PropType.FIRE
                clean in listOf("party_popper", "party", "พลุ", "ปาร์ตี้") -> PropType.PARTY_POPPER
                clean in listOf("balloon", "balloons", "ลูกโป่ง") -> PropType.BALLOONS
                clean in listOf("cat", "cat_paw", "แมว", "อุ้งเท้าแมว") -> PropType.CAT_PAW
                clean in listOf("gold", "coin", "gold_coin", "เหรียญ", "เงิน") -> PropType.GOLD_COIN
                clean in listOf("game", "controller", "gaming_controller", "จอยเกม") -> PropType.GAMING_CONTROLLER
                clean in listOf("laptop", "คอม", "แล็ปท็อป") -> PropType.LAPTOP
                clean in listOf("book", "หนังสือ") -> PropType.BOOK
                clean in listOf("music", "music_notes", "ดนตรี", "โน้ตเพลง") -> PropType.MUSIC_NOTES
                clean in listOf("rain", "rain_drops", "ฝน") -> PropType.RAIN_DROPS
                clean in listOf("umbrella", "ร่ม") -> PropType.UMBRELLA
                clean in listOf("tear", "tears", "น้ำตา") -> PropType.TEARS
                clean in listOf("skull", "กะโหลก", "หัวกะโหลก") -> PropType.SKULL
                clean in listOf("sweat", "sweat_drop", "เหงื่อ") -> PropType.SWEAT_DROP
                clean in listOf("question", "question_mark", "คำถาม") -> PropType.QUESTION_MARK
                clean in listOf("exclamation", "ตกใจ") -> PropType.EXCLAMATION
                clean in listOf("sleep", "zzzzz", "หลับ") -> PropType.ZZZZZ
                clean in listOf("dizzy", "dizzy_stars", "ตาลาย") -> PropType.DIZZY_STARS
                clean in listOf("lightbulb", "หลอดไฟ") -> PropType.LIGHTBULB
                clean in listOf("sun", "พระอาทิตย์") -> PropType.SUN
                clean in listOf("rainbow", "สายรุ้ง") -> PropType.RAINBOW
                clean in listOf("cloud", "เมฆ") -> PropType.CLOUD
                clean in listOf("snow", "หิมะ") -> PropType.SNOW
                clean in listOf("lightning", "ฟ้าผ่า", "สายฟ้า") -> PropType.LIGHTNING
                clean in listOf("leaf", "ใบไม้") -> PropType.LEAF
                clean in listOf("sakura", "cherry_blossom", "ซากุระ") -> PropType.CHERRY_BLOSSOM
                clean in listOf("moon", "moon_stars", "พระจันทร์") -> PropType.MOON_STARS
                clean in listOf("ghost", "ผี") -> PropType.GHOST
                clean in listOf("rocket", "จรวด") -> PropType.ROCKET
                clean in listOf("gear", "gears", "ฟันเฟือง") -> PropType.GEARS
                clean in listOf("shield", "โล่") -> PropType.SHIELD
                clean in listOf("headphones", "headphone", "หูฟัง", "ฟังเพลง") -> PropType.HEADPHONES
                clean in listOf("vr", "vr_headset", "แว่นvr", "แว่น vr") -> PropType.VR_HEADSET
                clean in listOf("snorkel", "snorkel_mask", "diving", "หน้ากากดำน้ำ") -> PropType.SNORKEL_MASK
                clean in listOf("devil", "devil_horns", "evil", "ปิศาจ") -> PropType.DEVIL_HORNS
                clean in listOf("grid", "scanner_grid", "ตาราง", "เลเซอร์") -> PropType.SCANNER_GRID
                clean in listOf("trash", "trash_bin", "ขยะ", "ถังขยะ") -> PropType.TRASH_BIN
                clean in listOf("camera", "camera_icon", "กล้อง", "ถ่ายรูป") -> PropType.CAMERA_ICON
                // ─── 30 Additional Moodset Prop Aliases ───
                clean in listOf("thermometer", "ปรอท", "วัดไข้", "ปรอทวัดไข้") -> PropType.THERMOMETER
                clean in listOf("money_bag", "moneybag", "ถุงเงิน", "ถุงตังค์") -> PropType.MONEY_BAG
                clean in listOf("dollar", "dollar_sign", "ดอลลาร์", "เงินดอลลาร์") -> PropType.DOLLAR_SIGN
                clean in listOf("thought_bubble", "ฟองความคิด", "คิด") -> PropType.THOUGHT_BUBBLE
                clean in listOf("glasses_square", "square_glasses", "แว่นเหลี่ยม", "แว่นอ่านหนังสือ") -> PropType.GLASSES_SQUARE
                clean in listOf("gaming_headset", "headset", "หูฟังเกมมิ่ง") -> PropType.GAMING_HEADSET
                clean in listOf("bucket_hat", "หมวกบัคเก็ต", "หมวกเดินป่า") -> PropType.BUCKET_HAT
                clean in listOf("passport", "พาสปอร์ต", "หนังสือเดินทาง") -> PropType.PASSPORT
                clean in listOf("backpack", "เป้", "กระเป๋าเป้") -> PropType.BACKPACK
                clean in listOf("icicles", "icicle", "น้ำแข็งย้อย", "น้ำแข็งเกาะ") -> PropType.ICICLES
                clean in listOf("heatwaves", "heatwave", "คลื่นความร้อน", "ไอร้อน") -> PropType.HEATWAVES
                clean in listOf("fedora", "fedora_hat", "หมวกนักสืบ", "หมวกเฟโดร่า") -> PropType.FEDORA_HAT
                clean in listOf("chef_hat", "หมวกเชฟ", "หมวกกุ๊ก") -> PropType.CHEF_HAT
                clean in listOf("spatula", "ตะหลิว", "กระทะ") -> PropType.SPATULA
                clean in listOf("beret", "หมวกเบเรต์", "หมวกศิลปิน") -> PropType.BERET
                clean in listOf("palette", "จานสี", "ถาดสี") -> PropType.PALETTE
                clean in listOf("astronaut_helmet", "หมวกอวกาศ", "หมวกนักบินอวกาศ") -> PropType.ASTRONAUT_HELMET
                clean in listOf("party_hat", "หมวกปาร์ตี้") -> PropType.PARTY_HAT
                clean in listOf("noisemaker", "แตรปาร์ตี้", "แตร") -> PropType.NOISEMAKER
                clean in listOf("confetti", "คอนเฟตติ", "กระดาษสี") -> PropType.CONFETTI
                clean in listOf("dream_cloud", "เมฆฝัน", "ก้อนเมฆฝัน") -> PropType.DREAM_CLOUD
                clean in listOf("lightning_eyes", "ตาสายฟ้า") -> PropType.LIGHTNING_EYES
                clean in listOf("bandit_mask", "หน้ากากโจร", "ผ้าปิดตาโจร") -> PropType.BANDIT_MASK
                clean in listOf("rose", "ดอกกุหลาบ", "กุหลาบ") -> PropType.ROSE
                clean in listOf("superhero_cape", "cape", "ผ้าคลุม", "ผ้าคลุมฮีโร่") -> PropType.SUPERHERO_CAPE
                clean in listOf("superhero_mask", "hero_mask", "หน้ากากฮีโร่") -> PropType.SUPERHERO_MASK
                clean in listOf("wizard_hat", "หมวกพ่อมด", "หมวกแม่มด") -> PropType.WIZARD_HAT
                clean in listOf("magic_wand", "wand", "ไม้กายสิทธิ์") -> PropType.MAGIC_WAND
                clean in listOf("sweatband", "ผ้าคาดหัว", "ผ้าคาดผม") -> PropType.SWEATBAND
                clean in listOf("basketball", "บาส", "ลูกบาส", "บาสเกตบอล") -> PropType.BASKETBALL
                clean in listOf("lab_goggles", "แว่นแล็บ", "แว่นทดลอง") -> PropType.LAB_GOGGLES
                clean in listOf("beaker", "บีกเกอร์", "ขวดทดลอง") -> PropType.BEAKER
                clean in listOf("viking_helmet", "หมวกไวกิ้ง", "หมวกนักรบ") -> PropType.VIKING_HELMET
                clean in listOf("sword", "ดาบ", "ดาบคู่") -> PropType.SWORD
                else -> try {
                    PropType.valueOf(clean.uppercase().replace(" ", "_"))
                } catch (_: Exception) {
                    null
                }
            }
        }

        /** Create preset states for common scenarios */
        val HAPPY_SUNNY = RobotFaceState("happy", "default", "sunny", "sparkles,music_notes", "bounce")
        val SAD_RAINY = RobotFaceState("sad", "crying", "rainy", "sweat_drop", "tilt_left")
        val LOVE_HEARTS = RobotFaceState("love", "heart", "love_bg", "hearts,sparkles", "wobble")
        val ANGRY_THUNDER = RobotFaceState("angry", "cross", "thunder", "fire,exclamation", "shake")
        val SLEEPING_NIGHT = RobotFaceState("sleeping", "default", "night", "zzzzz", "idle")
        val THINKING_DEFAULT = RobotFaceState("thinking", "question", "default", "question_mark", "tilt_right")
        val EXCITED_SAKURA = RobotFaceState("excited", "star", "sakura", "sparkles,music_notes", "jump")
    }
}

/**
 * Extension: merge RobotFaceState into existing AvatarState (backward compatible)
 */
fun AvatarState.withFace(face: RobotFaceState): AvatarState = copy(
    emotion = face.emotion,
    faceState = face
)

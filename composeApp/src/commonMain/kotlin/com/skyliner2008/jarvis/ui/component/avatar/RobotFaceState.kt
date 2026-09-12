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
    SPIRAL
}

/**
 * BackgroundTheme — ธีมฉากหลังรอบหัวหุ่นยนต์ เปลี่ยนบรรยากาศตามอารมณ์/บริบท
 */
enum class BackgroundTheme {
    /** Ambient gradient ม่วง-ดำ เดิม */
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
    THUNDER
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
    RAIN_DROPS
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
    @SerialName("speech_text") val speechText: String? = null,
    @SerialName("custom_props") val customProps: List<DynamicVectorProp> = emptyList(),
    @SerialName("eye_trick") val eyeTrickName: String = "none"
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
            AvatarEmotion.valueOf(emotionName.uppercase())
        } catch (_: Exception) {
            AvatarEmotion.IDLE
        }

    /** Parse eye style name → EyeStyle enum (safe fallback to DEFAULT) */
    val eyeStyle: EyeStyle
        get() = try {
            EyeStyle.valueOf(eyeStyleName.uppercase())
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

    /** Parse comma-separated props string → List<PropType> */
    val props: List<PropType>
        get() = propsRaw
            .split(",")
            .mapNotNull { raw ->
                try {
                    PropType.valueOf(raw.trim().uppercase())
                } catch (_: Exception) {
                    null
                }
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
                speechText = args["speech_text"],
                customProps = customPropList,
                eyeTrickName = args["eye_trick"] ?: "none"
            )
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

package com.skyliner2008.jarvis.tools.device

import com.skyliner2008.jarvis.tools.FunctionDeclaration
import com.skyliner2008.jarvis.tools.FunctionParameters
import com.skyliner2008.jarvis.tools.ParameterProperty

private val AVATAR_EMOTIONS = com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.entries.map { it.name.lowercase() }
private val AVATAR_EYE_STYLES = com.skyliner2008.jarvis.ui.component.avatar.EyeStyle.entries.map { it.name.lowercase() }
private val PROP_POSITIONS = com.skyliner2008.jarvis.ui.component.avatar.PropPosition.entries.map { it.name.lowercase() }
private val PROP_ANIMATIONS = com.skyliner2008.jarvis.ui.component.avatar.DynamicPropAnimation.entries.map { it.name.lowercase() }
private val AVATAR_BACKGROUNDS = com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme.entries.map { it.name.lowercase() }

/**
 * DeviceToolDefinitions — Function declarations สำหรับ device control tools ทั้งหมด
 *
 * AI (Gemini) จะเห็น tool เหล่านี้ใน function calling และเลือกเรียกตามคำสั่งผู้ใช้
 * จัดกลุ่มเป็น 4 หมวด: Hardware / App / Screen / System
 *
 * 2026-09-06 — Initial implementation
 */
object DeviceToolDefinitions {

    val allDefinitions: List<FunctionDeclaration> = listOf(
        // ═══ Hardware Controls ═══
        FunctionDeclaration(
            name = "device_flashlight",
            description = "เปิด/ปิดไฟฉาย (flashlight/torch). ใช้เมื่อผู้ใช้สั่ง 'เปิดไฟฉาย', 'ปิดไฟฉาย', 'turn on flashlight'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "Action to perform", enum = listOf("on", "off", "toggle"))
                ),
                required = listOf("action")
            )
        ),
        FunctionDeclaration(
            name = "device_volume",
            description = "ควบคุมระดับเสียงมือถือ — เพิ่ม/ลด/เงียบ/สั่น/ตั้งระดับ. ใช้เมื่อผู้ใช้สั่ง 'เพิ่มเสียง', 'ลดเสียง', 'เงียบ', 'เปิดเสียง', 'ตั้งเสียงที่ 50%'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "Action", enum = listOf("up", "down", "mute", "unmute", "vibrate", "silent", "normal", "set", "max", "status")),
                    "stream" to ParameterProperty("STRING", "Audio stream type (default: media)", enum = listOf("media", "ring", "notification", "alarm", "system", "call")),
                    "level" to ParameterProperty("NUMBER", "Volume level 0-100 (only for action=set)")
                ),
                required = listOf("action")
            )
        ),
        FunctionDeclaration(
            name = "device_brightness",
            description = "ปรับความสว่างหน้าจอ. ใช้เมื่อผู้ใช้สั่ง 'เพิ่มความสว่าง', 'ลดความสว่าง', 'ตั้งความสว่างที่ 80%'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "Action", enum = listOf("set", "auto")),
                    "level" to ParameterProperty("NUMBER", "Brightness level 0-100 (for action=set)")
                ),
                required = listOf("action")
            )
        ),
        FunctionDeclaration(
            name = "device_media_control",
            description = "ควบคุมเพลงและสื่อ — เล่น/หยุด/ข้าม/ย้อน, ตรวจสอบเพลงที่กำลังเล่นอยู่ (now_playing), หรือค้นหาและเปิดเล่นเพลงบน YouTube/YouTube Music/Spotify (search_play). ใช้เมื่อผู้ใช้สั่ง 'เล่นเพลง', 'หยุดเพลง', 'ข้ามเพลง', 'เพลงอะไรกำลังเล่นอยู่', 'เปิดเพลง... บน YouTube', 'เปิดเพลง... ใน Spotify'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty(
                        type = "STRING",
                        description = "Action: 'play', 'pause', 'toggle', 'next', 'previous', 'stop', 'now_playing' (เช็คชื่อเพลง/ศิลปินที่กำลังเล่น), 'search_play' (ค้นหาและเล่นเพลง)",
                        enum = listOf("play", "pause", "toggle", "next", "previous", "stop", "now_playing", "search_play")
                    ),
                    "query" to ParameterProperty("STRING", "ชื่อเพลง ศิลปิน หรือคำค้นหา (เฉพาะ action=search_play)"),
                    "app" to ParameterProperty("STRING", "แอปที่ต้องการเล่น (optional): 'youtube', 'youtube_music', 'spotify', 'auto'", enum = listOf("youtube", "youtube_music", "spotify", "auto"))
                ),
                required = listOf("action")
            )
        ),
        FunctionDeclaration(
            name = "device_always_live",
            description = "เปิดหรือปิดโหมด Always AI Live (โหมดควบคุม, โหมดขับขี่, โหมดรถยนต์, โหมดสัตว์เลี้ยง — หน้าจอเต็มจอพร้อม animated 3D Robot Avatar, คลื่นเสียง visualizer, และระบบค้างหน้าจอเฝ้ารับคำสั่งตลอดเวลา เหมาะสำหรับตั้งไว้ในรถหรือขณะขับขี่ หรือตั้งโต๊ะเป็นสัตว์เลี้ยงดิจิทัล). ใช้เมื่อผู้ใช้สั่ง 'โหมดควบคุม', 'โหมดขับขี่', 'โหมดรถยนต์', 'โหมดสัตว์เลี้ยง', 'เปิดโหมดสัตว์เลี้ยง', 'เข้าโหมดสัตว์เลี้ยง', 'เปิดโหมดควบคุม', 'เปิดโหมดขับขี่', 'เปิดโหมดรถยนต์', 'เข้าโหมดควบคุม', 'เข้าโหมดขับขี่', 'เข้าโหมดรถยนต์', 'เปิดโหมด Always', 'เข้าโหมด Always', 'เปิด Always', 'ปิดโหมด Always', 'ปิดโหมดสัตว์เลี้ยง', 'ปิดโหมดควบคุม', 'ปิดโหมดขับขี่', 'ปิดโหมดรถยนต์', 'ออกจากโหมดควบคุม', 'ออกจากโหมด Always', 'ปิด Always'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty(
                        type = "STRING",
                        description = "Action: 'on' (เปิดหรือสลับเข้าโหมด Always / โหมดควบคุม / โหมดขับขี่ / โหมดรถยนต์ / โหมดสัตว์เลี้ยง — ใช้ 'on' เสมอเมื่อผู้ใช้สั่งเปิดหรือเปลี่ยนโหมด), 'off' (ปิดโหมด Always / โหมดควบคุม / โหมดสัตว์เลี้ยง — ใช้เมื่อสั่งปิดเท่านั้น), 'toggle' (สลับสถานะเปิด/ปิดทั่วไป)",
                        enum = listOf("on", "off", "toggle")
                    ),
                    "mode" to ParameterProperty(
                        type = "STRING",
                        description = "รูปแบบโหมดเสริม (optional): 'control' (โหมดควบคุมทั่วไป), 'drive' (โหมดขับขี่), 'car' (โหมดรถยนต์), 'pet' (โหมดสัตว์เลี้ยงตั้งโต๊ะ)",
                        enum = listOf("control", "drive", "car", "pet")
                    )
                ),
                required = listOf("action")
            )
        ),
        FunctionDeclaration(
            name = "device_avatar_emotion",
            description = "ควบคุมการแสดงสีหน้า แววตา ฉากหลัง อุปกรณ์เสริม (Props) ท่าทาง (Gesture) อารมณ์ และชุดสีของ Robot Avatar บนหน้าจอ. รองรับสารบัญ LOOI Robot Moodset ทั้งหมด 50 หน้า (แผ่นที่ 1: หน้าที่ 1 ถึง 20, แผ่นที่ 2: หน้าที่ 21 ถึง 50 เช่น หน้าที่ 10 = Laughing, หน้าที่ 11 = Music, หน้าที่ 12 = VR Mode, หน้าที่ 13 = Diving, หน้าที่ 22 = Sick, หน้าที่ 23 = Rich) โดยเมื่อผู้ใช้สั่ง 'หน้าที่ 1' ถึง 'หน้าที่ 50' หรือ 'หน้า 11' หรือ 'แบบที่ 11' ให้เรียก action='page', page='11' เสมอ (ระบบมีครบทั้ง 50 หน้า ห้ามบอกว่าไม่มีหน้าที่ 11 หรือมีแค่ 10 หน้าเด็ดขาด) และเมื่อสั่ง 'หน้าทั้งหมด' หรือ 'ทุกหน้า' ให้เรียก action='all'. นอกจากนี้ยังรองรับ Smart Scenes (action='scene'), ตั้งค่าอารมณ์เฉพาะ (action='set') และรีเซ็ต (action='reset'). ห้ามสับสนกับอารมณ์ตลาดหุ้น!",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty(
                        type = "STRING",
                        description = "Action: 'page' (แสดง Moodset เฉพาะหน้าที่ 1 ถึง 50 สำหรับการตรวจสอบ 3-5 วินาที), 'all' (เล่นแสดง Moodset ครบทั้งหมด 50 หน้าวนลูปแบบละ 4 วินาที), 'scene' (เล่นฉากสำเร็จรูป เช่น eating, drinking, thug_life, royal, angry_missile), 'set' (ตั้งอารมณ์/พร็อพ/ฉากหลังเฉพาะ), 'demo' (เล่นแสดงทุกหน้า), 'reset' (กลับสู่โหมดตรวจจับอัตโนมัติ)",
                        enum = listOf("page", "all", "scene", "set", "demo", "reset")
                    ),
                    "page" to ParameterProperty(
                        type = "STRING",
                        description = "ลำดับหน้าที่ต้องการแสดงสำหรับตรวจสอบ (หน้าที่ 1 ถึง หน้าที่ 50 ตามสารบัญ LOOI Robot เช่น '1', '10', '11', '50')"
                    ),
                    "scene" to ParameterProperty(
                        type = "STRING",
                        description = "ชื่อฉากอัจฉริยะ (Smart Scene): 'eating' (กินอาหาร/สุ่มของกิน), 'drinking' (ดื่มเครื่องดื่ม/กาแฟ/ชา/ชานม), 'bath_clean' (อาบน้ำฟองสบู่), 'gaming' (เล่นเกม), 'study_work' (อ่านหนังสือ/ทำงาน), 'thug_life' (ใส่แว่นตาดำสุดเท่ Deal with it), 'rich' (เศรษฐีคริปโตเหรียญทองคำ), 'royal' (สวมมงกุฎทองคำ), 'fire' (ไฟไหม้ตูด), 'thunder' (โดนฟ้าผ่า), 'soul_out' (วิญญาณหลุด), 'angry_missile' (โกรธยิงจรวดถล่มหน้าจอ), 'super_love' (ปิ๊งรักหัวใจพุ่ง), 'cry' (ร้องไห้น้ำตาท่วม), 'celebrate' (ปาร์ตี้ฉลอง), 'rain_umbrella' (กางร่มกันฝน), 'vr_mode' (ใส่แว่น VR), 'music' (ฟังเพลง). เรื่องสั้นตามอารมณ์ (ตาเยลลี่): 'MoodIdleBall' (เล่นลูกบอล), 'MoodYoYo' (โยโย่), 'MoodStars' (นับดาว), 'MoodShocked' (ตกใจ), 'MoodSad' (เศร้า), 'MoodCurious' (สงสัย/แว่นขยาย), 'MoodAngryMissile' (โกรธยิงขีปนาวุธ), 'MoodFuming' (ควันออกหู), 'MoodGlitch' (โกรธกลิตช์), 'MoodThinking' (ครุ่นคิด), 'MoodHappy' (มีความสุข), 'MoodInLove' (ตกหลุมรัก), 'MoodGlad' (ยินดี), 'MoodAwesome' (เยี่ยมยอด), 'MoodShy' (เขิน), 'MoodEmbarrassed' (อาย), 'MoodShowOff' (เก๊กท่า), 'MoodListening' (กำลังฟัง), 'MoodArrogant' (เย่อหยิ่ง). ส่งชื่อฉากตามนี้ใน scene พร้อม action='scene' — ฉากจะเล่นจนจบเอง ห้ามเรียกซ้ำระหว่างเล่น"
                    ),
                    "emotion" to ParameterProperty(
                        type = "STRING",
                        description = "อารมณ์ (ครบทุกแบบที่ Avatar รองรับ): idle, happy, excited, love, angry, sad, sleeping, listening, thinking, speaking, wink, confused, pout, dizzy, surprised, bored, enraged, dead, laughing, music, vr_mode, diving, evil, focused, shy, disgusted, camera_mode, eating, drinking, puzzled, sick, rich, crying, reading, gaming, traveling, working, cold, hot, detective, cooking, art_mode, space, party, dreaming, exhausted, electric, sneaky, romantic, hero, glitched, magic, sporty, scientist, scared, warrior, low_battery",
                        enum = AVATAR_EMOTIONS
                    ),
                    "eye_style" to ParameterProperty(
                        type = "STRING",
                        description = "รูปแบบดวงตา (optional): default, wink, heart, crying, star, question, cross, spiral, happy, angry, sleepy, curious, laughing, focused, excited, shy, shock",
                        enum = AVATAR_EYE_STYLES
                    ),
                    "background" to ParameterProperty(
                        type = "STRING",
                        description = "ธีมฉากหลัง (optional): default, rainy, sunny, night, sakura, matrix, love_bg, thunder, cyber_grid, space_nebula, magic_mystic, cinema_cozy, winter_blizzard, summer_heat, warrior_dojo, party_confetti, golden_vault, sick_lab, sports_arena, low_power_crt",
                        enum = AVATAR_BACKGROUNDS
                    ),
                    "props" to ParameterProperty(
                        type = "STRING",
                        description = "อุปกรณ์เสริม/สติกเกอร์สำเร็จรูป 55 ชนิด (optional เช่น 'sunglasses', 'crown', 'coffee', 'pizza', 'burger', 'hearts', 'sparkles', 'fire', 'gold_coin', 'party_popper', 'gaming_controller', 'boba_tea', 'cake', 'ice_cream', 'popcorn', 'cat_paw', 'laptop', 'book', 'umbrella', 'lightning', 'skull' ฯลฯ) ระบุคั่นด้วยจุลภาคได้"
                    ),
                    "gesture" to ParameterProperty(
                        type = "STRING",
                        description = "ท่าทาง/ภาษากาย (optional): idle, tilt_left, tilt_right, bounce, jump, wobble, shake, nod",
                        enum = listOf("idle", "tilt_left", "tilt_right", "bounce", "jump", "wobble", "shake", "nod")
                    )
                ),
                required = listOf("action")
            )
        ),

        FunctionDeclaration(
            name = "device_pet_care",
            description = "ดูแลสัตว์เลี้ยงดิจิทัล (โหมดสัตว์เลี้ยง) ผ่านระบบค่าสถานะจริง (ความอิ่ม พลังงาน ความสะอาด ความสุข ความเครียด) — ต่างจาก device_avatar_emotion ที่แค่เปลี่ยนหน้าตา. ใช้เมื่อผู้ใช้สั่ง 'ให้อาหารน้อง', 'ป้อนข้าว', 'อาบน้ำให้น้อง', 'เล่นกับน้อง', 'พาน้องนอน', 'ปลุกน้อง', 'น้องหิวไหม', 'น้องเป็นยังไงบ้าง', 'ดูค่าสถานะ'. ใช้ได้เฉพาะตอนอยู่ในโหมดสัตว์เลี้ยง.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty(
                        type = "STRING",
                        description = "feed = ให้อาหาร, clean = อาบน้ำ, play = เล่นด้วย, sleep = พานอน, wake = ปลุก, status = อ่านค่าสถานะปัจจุบัน",
                        enum = listOf("feed", "clean", "play", "sleep", "wake", "status")
                    ),
                    "food" to ParameterProperty("STRING", "อาหารที่จะให้ (optional, เฉพาะ feed) เช่น 'burger', 'pizza', 'coffee', 'cake', 'ice_cream', 'boba_tea'")
                ),
                required = listOf("action")
            )
        ),
        FunctionDeclaration(
            name = "device_custom_prop",
            description = "สวม/ถอดอุปกรณ์เสริมบนหัวสัตว์เลี้ยง: หยิบจากคลังสำเร็จรูป (name เช่น 'crown', 'sunglasses', 'chef_hat') หรือเสกอุปกรณ์ใหม่จากรูปวาดเวกเตอร์ SVG (svg_path) แล้วจำไว้ใช้ครั้งหน้า. ใช้เมื่อผู้ใช้สั่ง 'เสก...', 'วาด...ใส่หัว', 'ใส่...', 'ถอด...', 'ถอดทั้งหมด'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "add = สวม, remove = ถอดชิ้นที่ระบุ, clear = ถอดทั้งหมด", enum = listOf("add", "remove", "clear")),
                    "name" to ParameterProperty("STRING", "ชื่ออุปกรณ์ เช่น 'crown', 'sunglasses', 'wizard_hat' หรือชื่อของชิ้นที่เสกเอง"),
                    "svg_path" to ParameterProperty("STRING", "(optional) SVG path data ของอุปกรณ์ที่เสกใหม่ (พิกัดใดก็ได้ ระบบย่อขยายให้พอดีหัวอัตโนมัติ) เช่น 'M12 2L15 8H9Z'"),
                    "color" to ParameterProperty("STRING", "(optional) สีเติม hex เช่น '#FFD700'"),
                    "position" to ParameterProperty("STRING", "(optional) ตำแหน่ง", enum = PROP_POSITIONS),
                    "animation" to ParameterProperty("STRING", "(optional) แอนิเมชัน", enum = PROP_ANIMATIONS),
                    "size" to ParameterProperty("STRING", "(optional) ขนาด dp เช่น '48'")
                ),
                required = listOf("action")
            )
        ),

        // ═══ App Launcher ═══
        FunctionDeclaration(
            name = "device_open_app",
            description = "เปิดแอปพลิเคชันบนมือถือตามชื่อ. รองรับทั้งชื่อไทยและอังกฤษ เช่น 'เปิด Google Maps', 'เปิดไลน์', 'เปิดตั้งค่า'. รู้จักแอปยอดนิยมกว่า 40 แอป.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "app_name" to ParameterProperty("STRING", "ชื่อแอป เช่น 'Google Maps', 'YouTube', 'LINE', 'ตั้งค่า', 'กล้อง'"),
                    "package_name" to ParameterProperty("STRING", "Package name (optional, ถ้ารู้) เช่น 'com.google.android.apps.maps'")
                ),
                required = emptyList()
            )
        ),
        FunctionDeclaration(
            name = "device_navigate",
            description = "เปิด Google Maps เพื่อดูพิกัด/ค้นหาสถานที่ หรือ นำทางไปยังจุดหมาย. รองรับ 2 รูปแบบ: 1. ดูตำแหน่ง (action='view' - default) ใช้เมื่อสั่ง 'เปิดแผนที่ดู...', 'เปิด location...', 'ค้นหา...ในแผนที่', 'เปิดแผนที่ไป...' จะแสดงหมุดสถานที่โดยไม่คำนวณเส้นทางขับรถ (ไม่ error ข้ามประเทศ) 2. นำทาง (action='navigate') ใช้เมื่อสั่ง 'นำทางไป...', 'เริ่มนำทาง', 'พาขับรถไป...'",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "destination" to ParameterProperty("STRING", "ชื่อสถานที่ พิกัด หรือจุดหมาย เช่น 'นิวยอร์ก', 'ญี่ปุ่น', 'Central World', 'สยามพารากอน'"),
                    "action" to ParameterProperty(
                        type = "STRING",
                        description = "รูปแบบ: 'view' = เปิดแผนที่ดูพิกัด/ค้นหาสถานที่ (default), 'navigate' = เริ่มโหมดนำทางแบบเลี้ยวต่อเลี้ยว",
                        enum = listOf("view", "navigate")
                    ),
                    "mode" to ParameterProperty("STRING", "โหมดการเดินทาง (เฉพาะ action=navigate)", enum = listOf("drive", "walk", "bike", "transit"))
                ),
                required = listOf("destination")
            )
        ),
        FunctionDeclaration(
            name = "device_send_email",
            description = "ร่างอีเมลใหม่ในแอป Gmail/Email. ใช้เมื่อผู้ใช้สั่ง 'ส่งอีเมลถึง...', 'ร่างอีเมล...', 'เขียนอีเมล...'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "to" to ParameterProperty("STRING", "อีเมลผู้รับ เช่น 'boss@company.com'"),
                    "subject" to ParameterProperty("STRING", "หัวข้ออีเมล"),
                    "body" to ParameterProperty("STRING", "เนื้อหาอีเมล")
                ),
                required = emptyList()
            )
        ),
        FunctionDeclaration(
            name = "device_add_calendar",
            description = "เพิ่มนัดหมาย/กิจกรรมในปฏิทิน Google Calendar. ใช้เมื่อผู้ใช้สั่ง 'เพิ่มนัดหมาย', 'ใส่ปฏิทิน', 'ลงตาราง', 'จดนัด'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "title" to ParameterProperty("STRING", "หัวข้อนัดหมาย"),
                    "description" to ParameterProperty("STRING", "รายละเอียดเพิ่มเติม"),
                    "location" to ParameterProperty("STRING", "สถานที่"),
                    "begin_time" to ParameterProperty("STRING", "เวลาเริ่ม (epoch milliseconds)"),
                    "end_time" to ParameterProperty("STRING", "เวลาสิ้นสุด (epoch milliseconds)")
                ),
                required = listOf("title")
            )
        ),
        FunctionDeclaration(
            name = "device_make_call",
            description = "เปิดแอปโทรศัพท์พร้อมเบอร์ที่ต้องการ (ผู้ใช้กดโทรเอง). ใช้เมื่อผู้ใช้สั่ง 'โทรหา...', 'โทรไป...', 'กดเบอร์...'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "number" to ParameterProperty("STRING", "เบอร์โทรศัพท์ เช่น '0812345678', '+66812345678'")
                ),
                required = listOf("number")
            )
        ),
        FunctionDeclaration(
            name = "device_send_sms",
            description = "ร่างข้อความ SMS ในแอป Messages (ผู้ใช้กดส่งเอง). ใช้เมื่อผู้ใช้สั่ง 'ส่ง SMS ไป...', 'ส่งข้อความ...', 'ร่าง SMS...'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "number" to ParameterProperty("STRING", "เบอร์ผู้รับ"),
                    "message" to ParameterProperty("STRING", "เนื้อหาข้อความ")
                ),
                required = listOf("number")
            )
        ),
        FunctionDeclaration(
            name = "device_set_alarm",
            description = "ตั้งนาฬิกาปลุก. ใช้เมื่อผู้ใช้สั่ง 'ตั้งปลุก...', 'ปลุกตอน...', 'ตั้งนาฬิกาปลุก...'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "hour" to ParameterProperty("NUMBER", "ชั่วโมง (0-23)"),
                    "minute" to ParameterProperty("NUMBER", "นาที (0-59, default 0)"),
                    "message" to ParameterProperty("STRING", "ข้อความแสดงเมื่อปลุก")
                ),
                required = listOf("hour")
            )
        ),
        FunctionDeclaration(
            name = "device_open_url",
            description = "เปิด URL ในเบราว์เซอร์. ใช้เมื่อผู้ใช้สั่ง 'เปิดเว็บ...', 'เปิดลิงก์...', 'ไปที่เว็บ...'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "url" to ParameterProperty("STRING", "URL ที่ต้องการเปิด เช่น 'google.com', 'https://example.com'")
                ),
                required = listOf("url")
            )
        ),
        FunctionDeclaration(
            name = "device_search_web",
            description = "ค้นหาข้อมูลบน Google ผ่านเบราว์เซอร์. ใช้เมื่อผู้ใช้สั่ง 'ค้นหา...', 'เสิร์ช...', 'หาข้อมูล...'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "query" to ParameterProperty("STRING", "คำค้นหา")
                ),
                required = listOf("query")
            )
        ),

        // ═══ Screen Interaction (Accessibility) ═══
        FunctionDeclaration(
            name = "device_read_screen",
            description = "อ่านข้อมูล/ข้อความทั้งหมดที่แสดงบนหน้าจอปัจจุบัน (ต้องเปิด Accessibility Service). ใช้เมื่อผู้ใช้สั่ง 'อ่านหน้าจอ', 'หน้าจอมีอะไร', 'ดูหน้าจอให้หน่อย'. คืนรายการ UI elements (ข้อความ ปุ่ม ช่องพิมพ์ รวมถึงปุ่มไอคอนที่ไม่มีข้อความ) จากทุกหน้าต่างรวม dialog/popup โดยแต่ละรายการมีพิกัดกึ่งกลาง @(x,y) — ใช้ก่อน device_tap เสมอเมื่อไม่แน่ใจว่าปุ่มอยู่ตรงไหน และอ่านซ้ำหลังกดเพื่อยืนยันว่าหน้าจอเปลี่ยนแล้ว.",
            parameters = null
        ),
        FunctionDeclaration(
            name = "device_tap",
            description = "แตะ/คลิกที่ตำแหน่งหรือปุ่มบนหน้าจอ (ต้องเปิด Accessibility). ใช้เมื่อผู้ใช้สั่ง 'แตะปุ่ม...', 'กดที่...', 'คลิก...'. ปุ่มที่มีข้อความใช้ text; ปุ่มไอคอนที่ไม่มีข้อความ หรือเมื่อแตะด้วย text ไม่สำเร็จ ให้ใช้ x,y จาก @(x,y) ใน device_read_screen.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "text" to ParameterProperty("STRING", "ข้อความบนปุ่ม/element ที่ต้องการแตะ เช่น 'ตกลง', 'Submit', 'ยืนยัน'"),
                    "view_id" to ParameterProperty("STRING", "Resource ID ของ view (optional) — ใช้ค่า id: จาก device_read_screen ได้เลย เช่น 'send'"),
                    "x" to ParameterProperty("NUMBER", "พิกัด X (px) จาก @(x,y) ใน device_read_screen (optional, ใช้คู่กับ y)"),
                    "y" to ParameterProperty("NUMBER", "พิกัด Y (px) จาก @(x,y) ใน device_read_screen (optional, ใช้คู่กับ x)")
                ),
                required = emptyList()
            )
        ),
        FunctionDeclaration(
            name = "device_type_text",
            description = "พิมพ์ข้อความลงในช่อง input ที่เปิดอยู่บนหน้าจอ (ต้องเปิด Accessibility). ใช้เมื่อผู้ใช้สั่ง 'พิมพ์...', 'ใส่ข้อความ...', 'เขียน...'. ถ้าผู้ใช้ต้องการให้ส่งหรือค้นหาทันที ให้ใส่ submit=true (กดปุ่ม Enter/ส่ง บนคีย์บอร์ด) ไม่งั้นข้อความจะค้างอยู่ในช่องพิมพ์เฉยๆ.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "text" to ParameterProperty("STRING", "ข้อความที่ต้องการพิมพ์"),
                    "clear" to ParameterProperty("BOOLEAN", "ล้างข้อความเดิมก่อนพิมพ์ (default: false)"),
                    "submit" to ParameterProperty("BOOLEAN", "กดปุ่มส่ง/ค้นหา/Enter บนคีย์บอร์ดหลังพิมพ์เสร็จ (default: false, ต้อง Android 11+)")
                ),
                required = listOf("text")
            )
        ),
        FunctionDeclaration(
            name = "device_gesture",
            description = "ท่าทางบนหน้าจอขั้นสูง (ต้องเปิด Accessibility): กดค้าง (long_press) เพื่อเปิดเมนูบริบท/เลือกข้อความ, ปัดซ้าย-ขวา-บน-ล่าง (swipe) เช่น เปลี่ยนแท็บ ปัดลบรายการ เลื่อนสตอรี่, และลากวัตถุ (drag). ใช้เมื่อผู้ใช้สั่ง 'กดค้างที่...', 'ปัดซ้าย', 'ปัดขวา', 'เลื่อนไปแท็บถัดไป', 'ลาก...ไป...'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "ชนิดท่าทาง", enum = listOf("long_press", "swipe", "drag")),
                    "direction" to ParameterProperty("STRING", "ทิศที่นิ้วลากไป (เฉพาะ action=swipe)", enum = listOf("up", "down", "left", "right")),
                    "x" to ParameterProperty("NUMBER", "พิกัด X จุดเริ่ม (สำหรับ long_press และ drag) จาก @(x,y) ใน device_read_screen"),
                    "y" to ParameterProperty("NUMBER", "พิกัด Y จุดเริ่ม (สำหรับ long_press และ drag)"),
                    "to_x" to ParameterProperty("NUMBER", "พิกัด X ปลายทาง (เฉพาะ action=drag)"),
                    "to_y" to ParameterProperty("NUMBER", "พิกัด Y ปลายทาง (เฉพาะ action=drag)"),
                    "duration_ms" to ParameterProperty("NUMBER", "ระยะเวลาของท่าทาง (ms) — long_press default 800, swipe 350, drag 500")
                ),
                required = listOf("action")
            )
        ),
        FunctionDeclaration(
            name = "device_screenshot",
            description = "จับภาพหน้าจอจริงส่งเข้ามาให้ 'ดูด้วยตา' (ต้องเปิด Accessibility + Android 11 ขึ้นไป). ใช้เมื่อ device_read_screen อ่านข้อความไม่ได้หรือไม่พอ เช่น แผนที่ Google Maps, รูปภาพ, วิดีโอ, กราฟ, เกม, WebView หรือเมื่อผู้ใช้สั่ง 'แคปหน้าจอดูหน่อย', 'ดูภาพบนหน้าจอ', 'บนจอมีรูปอะไร', 'ส่องหน้าจอดูซิ', 'ดูหน้าจอให้หน่อยว่าเป็นยังไง', 'บนแผนที่เห็นอะไรบ้าง', 'อ่านกราฟบนจอ'. ต่างจาก vision_activate ที่เปิดกล้องมองโลกภายนอก — tool นี้ดู 'หน้าจอมือถือ' ที่ผู้ใช้กำลังเห็น. หลังเรียกแล้วให้ดูภาพล่าสุดที่เข้ามาแล้วตอบสิ่งที่เห็นจริง ห้ามเดา. หากต้องการพิกัดปุ่มเพื่อกดต่อ ให้ใช้ device_read_screen (ภาพไม่ให้พิกัด).",
            parameters = null
        ),
        FunctionDeclaration(
            name = "device_scroll",
            description = "เลื่อนหน้าจอขึ้น/ลง (ต้องเปิด Accessibility). ใช้เมื่อผู้ใช้สั่ง 'เลื่อนลง', 'เลื่อนขึ้น', 'scroll down'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "direction" to ParameterProperty("STRING", "ทิศทาง", enum = listOf("down", "up"))
                ),
                required = listOf("direction")
            )
        ),
        FunctionDeclaration(
            name = "device_press_button",
            description = "กดปุ่มระบบ Android — กลับ, หน้าหลัก, แอปล่าสุด, แจ้งเตือน, ตั้งค่าด่วน, จับภาพหน้าจอ, ล็อคจอ/พักหน้าจอ/สั่งให้พัก, ปลุก/เปิดหน้าจอ. ใช้เมื่อผู้ใช้สั่ง 'กดกลับ', 'กลับหน้าหลัก', 'เปิดแจ้งเตือน', 'จับภาพหน้าจอ', 'ล็อคหน้าจอ', 'พักหน้าจอ', 'สั่งให้พัก', 'สลีป', 'เปิดหน้าจอ', 'เปิดจอ', 'ปลุกหน้าจอ', 'ตื่น'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "button" to ParameterProperty("STRING", "ชื่อปุ่ม", enum = listOf("back", "home", "recent", "notifications", "quick_settings", "screenshot", "lock", "wake"))
                ),
                required = listOf("button")
            )
        ),
        FunctionDeclaration(
            name = "device_get_app_info",
            description = "ดูว่าผู้ใช้กำลังเปิดแอปอะไรอยู่ (ต้องเปิด Accessibility). ใช้เมื่อผู้ใช้ถาม 'เปิดอะไรอยู่', 'แอปอะไร'.",
            parameters = null
        ),

        // ═══ System Info ═══
        FunctionDeclaration(
            name = "device_battery_status",
            description = "ตรวจสอบสถานะแบตเตอรี่. ใช้เมื่อผู้ใช้ถาม 'แบตเหลือเท่าไหร่', 'ชาร์จอยู่ไหม'.",
            parameters = null
        ),
        FunctionDeclaration(
            name = "device_wifi_status",
            description = "ตรวจสอบสถานะ WiFi และเครือข่ายที่เชื่อมต่อ. ใช้เมื่อผู้ใช้ถาม 'WiFi เปิดไหม', 'เชื่อมต่อ WiFi อะไรอยู่'.",
            parameters = null
        ),

        // ═══ Smart Notifications (Driving Mode) ═══
        FunctionDeclaration(
            name = "device_notification_read",
            description = "อ่านข้อความแจ้งเตือนล่าสุดที่เข้ามาในเครื่อง (LINE, SMS, WhatsApp, Messenger ฯลฯ). ใช้เมื่อผู้ใช้สั่ง 'อ่านข้อความ', 'มีข้อความใหม่ไหม', 'ใครทักมา', 'อ่านไลน์', 'มีแจ้งเตือนอะไรบ้าง'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "app_filter" to ParameterProperty("STRING", "กรองเฉพาะชื่อแอป เช่น 'line', 'sms', 'whatsapp' (optional)"),
                    "count" to ParameterProperty("NUMBER", "จำนวนแจ้งเตือนที่ต้องการอ่าน (default: 5)")
                ),
                required = emptyList()
            )
        ),
        FunctionDeclaration(
            name = "device_notification_reply",
            description = "พิมพ์ข้อความตอบกลับแจ้งเตือนล่าสุด (เช่น ตอบ LINE, ตอบ SMS, ตอบ WhatsApp) โดยตรงผ่านระบบแจ้งเตือนโดยไม่ต้องเปิดแอป. ใช้เมื่อผู้ใช้สั่ง 'ตอบว่า...', 'ตอบไลน์ว่า...', 'reply ว่า...'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "message" to ParameterProperty("STRING", "เนื้อหาข้อความที่ต้องการพิมพ์ตอบกลับ"),
                    "notification_key" to ParameterProperty("STRING", "คีย์ของการแจ้งเตือนที่ต้องการตอบ (optional, หากไม่ระบุจะตอบข้อความล่าสุดที่ตอบกลับได้)")
                ),
                required = listOf("message")
            )
        ),

        // ═══ Location & GPS ═══
        FunctionDeclaration(
            name = "device_location",
            description = "อ่านพิกัดตำแหน่งปัจจุบันและที่อยู่จาก GPS สำหรับการนำทาง การค้นหาสถานที่ใกล้เคียง หรือตอบคำถามผู้ใช้. ใช้เมื่อผู้ใช้ถาม 'ตอนนี้อยู่ที่ไหน', 'พิกัดปัจจุบัน', 'เช็คตำแหน่ง', 'พิกัด GPS' หรือค้นหาสถานที่ใกล้เคียง เช่น 'มีร้านอาหารแถวนี้อะไรบ้าง', 'แนะนำร้านอาหารแถวนี้', 'คาเฟ่ใกล้ฉัน', 'ปั๊มน้ำมันใกล้ๆ'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "Action: 'get_current' (อ่านพิกัดและที่อยู่ปัจจุบัน), 'status' (ตรวจสิทธิ์ GPS)", enum = listOf("get_current", "status")),
                    "query" to ParameterProperty("STRING", "คำค้นหาสถานที่ใกล้เคียง (optional) เช่น 'ร้านอาหาร', 'คาเฟ่', 'ปั๊มน้ำมัน', 'ร้านสะดวกซื้อ', 'โรงพยาบาล' เพื่อค้นหาสถานที่จริงรอบพิกัดปัจจุบัน")
                ),
                required = listOf("action")
            )
        ),

        // ═══ Weather & Forecast ═══
        FunctionDeclaration(
            name = "device_weather",
            description = "ตรวจสอบสภาพอากาศ อุณหภูมิ พยากรณ์อากาศ ปริมาณฝน ความชื้น และสภาพอากาศปัจจุบันโดยอ้างอิงพิกัด GPS จริงของเครื่องผู้ใช้ (หรือระบุชื่อเมือง/จังหวัด/ประเทศ). ใช้เมื่อผู้ใช้ถาม 'สภาพอากาศวันนี้', 'ฝนจะตกไหม', 'อากาศเป็นไงบ้าง', 'วันนี้ร้อนไหม', 'สภาพอากาศที่...', 'เช็คสภาพอากาศ', 'พยากรณ์อากาศ' ห้ามใช้ search_web กับคำถามสภาพอากาศเด็ดขาด ให้ใช้ tool นี้เสมอ!",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "location" to ParameterProperty("STRING", "ชื่อเมือง จังหวัด หรือประเทศที่ต้องการตรวจ (optional หากไม่ระบุจะดึงพิกัด GPS ปัจจุบันของผู้ใช้ เช่น 'กรุงเทพ', 'เชียงใหม่', 'Tokyo')"),
                    "latitude" to ParameterProperty("NUMBER", "พิกัดละติจูด (optional)"),
                    "longitude" to ParameterProperty("NUMBER", "พิกัดลองจิจูด (optional)")
                ),
                required = emptyList()
            )
        )
    )
}

package com.skyliner2008.jarvis.tools.device

import com.skyliner2008.jarvis.tools.FunctionDeclaration
import com.skyliner2008.jarvis.tools.FunctionParameters
import com.skyliner2008.jarvis.tools.ParameterProperty

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
            description = "ควบคุมการแสดงสีหน้า แววตา ฉากหลัง อุปกรณ์เสริม (Props) ท่าทาง (Gesture) อารมณ์ และชุดสีของ Robot Avatar บนหน้าจอ. รองรับ Layer-based rendering: Background Theme, Eye Style, Props/Stickers Overlay และ Body Language Gesture. ใช้เมื่อผู้ใช้สั่ง 'เดโม่อารมณ์', 'แสดงอารมณ์ทั้งหมด', 'ทำหน้าดีใจ', 'ทำหน้าตื่นเต้น', 'ทำหน้ารัก', 'ทำหน้าโกรธ', 'ทำหน้าเศร้า', 'ทำหน้าหลับ', 'ทำหน้าคิด', 'รีเซ็ตอารมณ์', 'avatar demo', 'avatar happy' ฯลฯ. คำสั่งเหล่านี้เกี่ยวกับใบหน้าของหุ่นยนต์บนจอ ห้ามสับสนกับอารมณ์ตลาดหุ้นหรือ Fear & Greed!",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty(
                        type = "STRING",
                        description = "Action: 'demo' (เริ่มเล่นวนลูปครบทั้ง 10 อารมณ์), 'set' (ตั้งอารมณ์เฉพาะ พร้อม background/props/gesture), 'reset' (กลับสู่โหมดตรวจจับอัตโนมัติ)",
                        enum = listOf("demo", "set", "reset")
                    ),
                    "emotion" to ParameterProperty(
                        type = "STRING",
                        description = "อารมณ์: happy, excited, love, angry, sad, sleeping, listening, thinking, speaking, idle, wink, confused, pout, dizzy",
                        enum = listOf("happy", "excited", "love", "angry", "sad", "sleeping", "listening", "thinking", "speaking", "idle", "wink", "confused", "pout", "dizzy")
                    ),
                    "eye_style" to ParameterProperty(
                        type = "STRING",
                        description = "รูปแบบดวงตา (optional): default, wink, heart, crying, star, question, cross, spiral",
                        enum = listOf("default", "wink", "heart", "crying", "star", "question", "cross", "spiral")
                    ),
                    "background" to ParameterProperty(
                        type = "STRING",
                        description = "ธีมฉากหลัง (optional): default, rainy, sunny, night, sakura, matrix, love_bg, thunder",
                        enum = listOf("default", "rainy", "sunny", "night", "sakura", "matrix", "love_bg", "thunder")
                    ),
                    "props" to ParameterProperty(
                        type = "STRING",
                        description = "อุปกรณ์เสริม/สติกเกอร์ (optional, comma-separated): umbrella, question_mark, sweat_drop, hearts, music_notes, sparkles, zzzzz, exclamation, fire, snow"
                    ),
                    "gesture" to ParameterProperty(
                        type = "STRING",
                        description = "ท่าทาง/ภาษากาย (optional): idle, tilt_left, tilt_right, bounce, jump, wobble, shake, nod",
                        enum = listOf("idle", "tilt_left", "tilt_right", "bounce", "jump", "wobble", "shake", "nod")
                    ),
                    "svg_path" to ParameterProperty("STRING", "SVG Path Data String สำหรับเสกพร็อพเวกเตอร์แบบ Custom (optional เช่น 'M12 2 C8 2 4 6 4 10 L20 10 Z')"),
                    "prop_name" to ParameterProperty("STRING", "ชื่อพร็อพแบบ Custom (optional เช่น 'cowboy_hat', 'crown')"),
                    "prop_color" to ParameterProperty("STRING", "สีเติม Hex ของ Custom prop (optional เช่น '#FFD700')"),
                    "prop_position" to ParameterProperty("STRING", "ตำแหน่ง Custom prop (optional): forehead, left_eye, right_eye, cheeks, chin, floating_left, floating_right")
                ),
                required = listOf("action")
            )
        ),

        // ═══ Dynamic Vector Prop (Custom SVG Magic) ═══
        FunctionDeclaration(
            name = "device_custom_prop",
            description = "สร้าง สวมใส่ นำกลับมาใช้ซ้ำ หรือถอดอุปกรณ์เสริมเวกเตอร์ SVG (Dynamic SVG Vector Prop) บนใบหน้าของหุ่นยนต์แบบสดๆ และบันทึกถาวรในคลัง SQLite เมื่อสร้างแล้วสามารถหยิบมาใส่ซ้ำได้โดยระบุแค่ชื่อ (name) โดยไม่ต้องส่ง svg_path ซ้ำ! AI สามารถคิดเองเลือกเองสร้างเองตามบริบทบทสนทนา เช่น หมวกโจรสลัด หมวกเชฟ แว่นตาเลนส์เดียว (monocle) ฯลฯ",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty(
                        type = "STRING",
                        description = "การกระทำ: 'add' (สร้าง/สวมใส่พร็อพใหม่ หรือหยิบพร็อพเดิมในคลังมาใส่ซ้ำ), 'remove' (ถอดพร็อพออกจากหน้า), 'delete' (ลบออกจากคลังถาวร), 'clear' (ล้างพร็อพทั้งหมด)",
                        enum = listOf("add", "remove", "delete", "clear")
                    ),
                    "name" to ParameterProperty("STRING", "ชื่อของพร็อพ เช่น 'pirate_eyepatch', 'detective_monocle', 'chef_hat', 'cowboy_hat'"),
                    "svg_path" to ParameterProperty("STRING", "SVG Path Data String มาตรฐาน (M, L, C, Z, ฯลฯ) เช่น 'M12 2 C8 2 4 6 4 10 L20 10 Z' (จำเป็นเมื่อสร้างพร็อพใหม่ หากเป็นพร็อพที่เคยสร้างไว้แล้วในคลังสามารถเว้นว่างได้)"),
                    "color" to ParameterProperty("STRING", "รหัสสีเติม Hex (optional เช่น '#FFD700', '#FF5722', '#00E5FF') ค่าเริ่มต้นคือ #FFD700"),
                    "stroke_color" to ParameterProperty("STRING", "รหัสสีเส้นขอบ Hex (optional เช่น '#FFFFFF', '#000000')"),
                    "stroke_width" to ParameterProperty("NUMBER", "ความหนาของเส้นขอบ (optional ค่าเริ่มต้น 0f)"),
                    "position" to ParameterProperty(
                        type = "STRING",
                        description = "ตำแหน่งยึดบนใบหน้า: 'forehead' (หน้าผาก/หัว), 'left_eye' (ตาซ้าย - สำหรับแว่นตา/ผ้าปิดตา/monocle), 'right_eye' (ตาขวา), 'cheeks' (แก้ม/หนวด), 'chin' (คาง/ปาก), 'floating_left' (ลอยด้านซ้าย), 'floating_right' (ลอยด้านขวา)",
                        enum = listOf("forehead", "left_eye", "right_eye", "cheeks", "chin", "floating_left", "floating_right")
                    ),
                    "size" to ParameterProperty("NUMBER", "ขนาดแสดงผลเป็น dp หรือระบุ 0 เพื่อให้ระบบ Auto-Fit เท่ากับเส้นผ่านศูนย์กลางดวงตาของหุ่นยนต์ 1:1 พอดี (เช่น สำหรับ monocle/eyepatch/glasses)"),
                    "animation" to ParameterProperty(
                        type = "STRING",
                        description = "แอนิเมชัน: 'float_bob' (ลอยขึ้นลงเบาๆ), 'pulse' (เต้นตุบๆ ย่อขยาย), 'rotate' (หมุนต่อเนื่อง), 'sway' (โยกแกว่งไปมา), 'static' (อยู่นิ่งๆ)",
                        enum = listOf("float_bob", "pulse", "rotate", "sway", "static")
                    )
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
            description = "อ่านข้อมูล/ข้อความทั้งหมดที่แสดงบนหน้าจอปัจจุบัน (ต้องเปิด Accessibility Service). ใช้เมื่อผู้ใช้สั่ง 'อ่านหน้าจอ', 'หน้าจอมีอะไร', 'ดูหน้าจอให้หน่อย'. คืนรายการ UI elements ทั้งหมดรวมถึงข้อความ ปุ่ม และ interactive elements.",
            parameters = null
        ),
        FunctionDeclaration(
            name = "device_tap",
            description = "แตะ/คลิกที่ตำแหน่งหรือปุ่มบนหน้าจอ (ต้องเปิด Accessibility). ใช้เมื่อผู้ใช้สั่ง 'แตะปุ่ม...', 'กดที่...', 'คลิก...'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "text" to ParameterProperty("STRING", "ข้อความบนปุ่ม/element ที่ต้องการแตะ เช่น 'ตกลง', 'Submit', 'ยืนยัน'"),
                    "view_id" to ParameterProperty("STRING", "Resource ID ของ view (optional, ถ้ารู้)"),
                    "x" to ParameterProperty("NUMBER", "พิกัด X บนหน้าจอ (optional, ใช้คู่กับ y)"),
                    "y" to ParameterProperty("NUMBER", "พิกัด Y บนหน้าจอ (optional, ใช้คู่กับ x)")
                ),
                required = emptyList()
            )
        ),
        FunctionDeclaration(
            name = "device_type_text",
            description = "พิมพ์ข้อความลงในช่อง input ที่เปิดอยู่บนหน้าจอ (ต้องเปิด Accessibility). ใช้เมื่อผู้ใช้สั่ง 'พิมพ์...', 'ใส่ข้อความ...', 'เขียน...'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "text" to ParameterProperty("STRING", "ข้อความที่ต้องการพิมพ์"),
                    "clear" to ParameterProperty("BOOLEAN", "ล้างข้อความเดิมก่อนพิมพ์ (default: false)")
                ),
                required = listOf("text")
            )
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

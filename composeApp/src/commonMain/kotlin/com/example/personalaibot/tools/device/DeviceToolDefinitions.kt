package com.example.personalaibot.tools.device

import com.example.personalaibot.tools.FunctionDeclaration
import com.example.personalaibot.tools.FunctionParameters
import com.example.personalaibot.tools.ParameterProperty

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
            description = "ควบคุมเพลง/สื่อที่กำลังเล่น — เล่น/หยุด/ข้าม/ย้อน. ใช้เมื่อผู้ใช้สั่ง 'เล่นเพลง', 'หยุดเพลง', 'ข้ามเพลง', 'ย้อนเพลง', 'เพลงถัดไป'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "Action", enum = listOf("play", "pause", "toggle", "next", "previous", "stop"))
                ),
                required = listOf("action")
            )
        ),
        FunctionDeclaration(
            name = "device_always_live",
            description = "เปิดหรือปิดโหมด Always AI Live (โหมดควบคุม — หน้าจอเต็มจอพร้อม animated 3D Robot Avatar, คลื่นเสียง visualizer, และระบบค้างหน้าจอเฝ้ารับคำสั่งตลอดเวลา). ใช้เมื่อผู้ใช้สั่ง 'เปิดโหมด Always', 'เปิดโหมดควบคุม', 'เข้าโหมด Always', 'เปิด Always', 'ปิดโหมด Always', 'ปิดโหมดควบคุม', 'ออกจากโหมด Always', 'ปิด Always'.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty(
                        type = "STRING",
                        description = "Action: 'on' (เปิดโหมด Always/โหมดควบคุม), 'off' (ปิดโหมด Always/โหมดควบคุม), 'toggle' (สลับสถานะ)",
                        enum = listOf("on", "off", "toggle")
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
        )
    )
}

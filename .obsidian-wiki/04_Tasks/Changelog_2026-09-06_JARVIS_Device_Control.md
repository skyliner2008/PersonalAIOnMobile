---
title: JARVIS Mobile Device Control via Voice & Accessibility Service
category: Tasks
tags: [jarvis, android, device-control, accessibility, voice-control, tools]
sources: [JarvisAccessibilityService.kt, DeviceControlExecutor.kt, DeviceToolDefinitions.kt, ToolExecutor.kt, ToolRegistry.kt, JarvisPersona.kt, MainActivity.kt]
created: 2026-09-06
updated: 2026-09-06
---

# 📱 JARVIS Mobile Device Control — ควบคุมมือถือทั้งเครื่องผ่านคำสั่งเสียง

## 1. บทนำและเป้าหมาย
ยกระดับ PersonalAIBot จากผู้ช่วย AI ที่โต้ตอบได้และรันเบื้องหลัง/แสดง Floating Widget ทับแอปอื่น ไปสู่การเป็น **"AI ที่ควบคุมมือถือทั้งเครื่องแทนผู้ใช้ (Full Device Control)"** ผ่านคำสั่งเสียงแบบ Real-time โดยไม่ต้องสัมผัสหน้าจอ

## 2. ความสามารถที่เพิ่มขึ้น (4 หมวด 18 เครื่องมือ)

### 2.1 Hardware Controls (ควบคุมฮาร์ดแวร์โดยตรง)
- `device_flashlight`: เปิด/ปิด/สลับไฟฉาย (`on`, `off`, `toggle`)
- `device_volume`: ปรับระดับเสียง (เพิ่ม, ลด, เงียบ, สั่น, ปกติ, เสียงเต็ม, ตั้งค่า 0-100%) รองรับทุก Audio Stream (media, ring, notification, alarm, call)
- `device_brightness`: ปรับความสว่างหน้าจอ (0-100% หรืออัตโนมัติ) พร้อมระบบเปิดหน้าขอ `WRITE_SETTINGS` หากยังไม่ได้รับอนุญาต
- `device_media_control`: ควบคุมเครื่องเล่นเพลง/มีเดีย (เล่น, หยุด, สลับเล่น/หยุด, ข้ามเพลง, ย้อนเพลง, หยุดทั้งหมด)
- `device_always_live`: สั่งเปิด/ปิด/สลับโหมด Always AI Live (โหมดควบคุม) ผ่านคำสั่งเสียง เช่น "เปิดโหมด Always", "เปิดโหมดควบคุม", "ปิดโหมด Always", "ปิดโหมดควบคุม" แสดง 3D Robot Avatar เต็มจอพร้อมระบบค้างหน้าจอเปิดกว้างตลอดเวลา

### 2.2 App Launcher (เปิดแอปและสั่งงานภายนอก)
- `device_open_app`: เปิดแอปพลิเคชันใดๆ ในเครื่องตามชื่อไทย/อังกฤษ (รู้จักกว่า 40+ แอปยอดนิยม และค้นหาจาก installed apps)
- `device_navigate`: เปิด Google Maps พร้อมนำทางไปยังจุดหมายปลายทางทันที (รองรับโหมดขับรถ, เดิน, ปั่นจักรยาน, ขนส่งสาธารณะ)
- `device_send_email`: ร่างอีเมลใหม่ใน Gmail/Email พร้อมผู้รับ, หัวข้อ, เนื้อหา
- `device_add_calendar`: เพิ่มนัดหมายใน Google Calendar พร้อมเวลาเริ่ม/สิ้นสุด สถานที่ และรายละเอียด
- `device_make_call`: เปิดแอปโทรศัพท์พร้อมพิมพ์เบอร์ให้ผู้ใช้กดยืนยัน (Intent-based เพื่อความปลอดภัย)
- `device_send_sms`: ร่างข้อความ SMS พร้อมเบอร์ผู้รับในแอปข้อความ
- `device_set_alarm`: ตั้งนาฬิกาปลุกระบุชั่วโมง, นาที, และข้อความ
- `device_open_url`: เปิดเว็บเพจในเบราว์เซอร์
- `device_search_web`: ค้นหาข้อมูลบน Google ผ่านเบราว์เซอร์

### 2.3 Screen Interaction & Accessibility (อ่านจอและสั่งคลิกแทนผู้ใช้)
*ต้องเปิด Accessibility Service ในการตั้งค่า Android (Settings > Accessibility > JARVIS)*
- `device_read_screen`: สแกนและอ่านข้อความ/องค์ประกอบ UI ทั้งหมดบนหน้าจอปัจจุบัน ส่งกลับให้ AI วิเคราะห์
- `device_tap`: แตะหรือคลิกปุ่มบนหน้าจอตามข้อความ (`text`), Resource ID (`view_id`), หรือพิกัดหน้าจอ (`x`, `y`)
- `device_type_text`: พิมพ์ข้อความลงในช่อง input/text field ที่โฟกัสอยู่ (พร้อมตัวเลือกล้างข้อความเดิม)
- `device_scroll`: เลื่อนหน้าจอขึ้นหรือลง (`up`, `down`)
- `device_press_button`: กดปุ่มระบบ Android (Back, Home, Recent Apps, Notifications, Quick Settings, Screenshot, Lock Screen)
- `device_get_app_info`: ตรวจสอบว่าผู้ใช้กำลังเปิดแอปและ Activity ใดอยู่

### 2.4 System Info (ตรวจสอบสถานะระบบ)
- `device_battery_status`: ตรวจสอบระดับเปอร์เซ็นต์แบตเตอรี่และสถานะการชาร์จ
- `device_wifi_status`: ตรวจสอบสถานะการเชื่อมต่อ WiFi, SSID, และความแรงสัญญาณ

---

## 3. สถาปัตยกรรมระบบ (Architecture)

```
[ผู้ใช้สั่งเสียง / แชท]
        ↓
[Gemini Live / Gemini Flash] 
        ↓ Function Calling
[ToolRegistry] (หมวด "📱 Device Control" 17 เครื่องมือ)
        ↓
[ToolExecutor] (commonMain)
        ↓ Interface `DeviceControlHandler`
[DeviceControlExecutor] (androidMain)
   ├── Android APIs (CameraManager, AudioManager, Settings, Intents)
   └── [JarvisAccessibilityService] (AccessibilityService)
           ├── Global Actions (Back / Home / Recents)
           ├── Screen Reader (UI Tree Traversal)
           └── Node Actions (Click / SetText / Scroll)
```

1. **`JarvisAccessibilityService.kt`** (`androidMain`):
   - Service ทางการของ Android สำหรับเข้าถึงและควบคุม UI ข้ามแอป
   - กำหนดค่าใน `accessibility_service_config.xml` (typeAllMask, canRetrieveWindowContent, canPerformGestures)
2. **`DeviceControlHandler.kt`** (`commonMain`):
   - Decoupled interface ใน Pure Kotlin สำหรับเรียกใช้จาก KMP `ToolExecutor`
3. **`DeviceToolDefinitions.kt`** (`commonMain`):
   - นิยาม Function Declarations ทั้ง 17 ตัวสำหรับ Gemini Function Calling
4. **`DeviceControlExecutor.kt`** (`androidMain`):
   - ดำเนินการควบคุมระดับ OS และเชื่อมต่อไปยัง AccessibilityService
5. **`MainActivity.kt` & `JarvisAutomationService.kt`**:
   - เชื่อมต่อ `DeviceControlExecutor` เข้าสู่ `ToolExecutor.initDeviceExecutor` ทั้งในหน้า Foreground และ Background Service
   - เพิ่มรายการ "ควบคุมเครื่อง (Accessibility)" ใน Setup Checklist ให้ผู้ใช้กดเปิดสวิตช์ได้ในคลิกเดียว
   - เพิ่ม `setKeepScreenOn()` และ `wakeAndTurnScreenOn()` ปลุกหน้าจอและค้างจอขณะคุย
6. **`JarvisPersona.kt`**:
   - เพิ่ม `DEVICE_CONTROL_RULES` ใน System Prompt ทั้งสำหรับข้อความ (`CHAT_SYSTEM_PROMPT`) และเสียงสด (`LIVE_SYSTEM_PROMPT`)
   - ระบุชัดเจนว่า "พักหน้าจอ", "สั่งให้พัก", "สลีป", "ล็อคหน้าจอ" เรียก `device_press_button(button="lock")`

---

## 4. Always AI Live Mode & Screen Control Lifecycle

1. **Keep Screen Awake (3-Layer Protection หน้าจอติดค้างตลอดเวลาข้ามทุกแอป)**:
   - **Layer 1 (OS Level WakeLock)**: `AlwaysLiveManager` ถือ `PowerManager.SCREEN_BRIGHT_WAKE_LOCK` ตราบใดที่อยู่ใน Live Mode บังคับให้หน้าจอสว่างติดตลอดเวลา แม้จะเปิดแอปอื่นอย่าง Google Maps หรือ YouTube มาบัง
   - **Layer 2 (Floating Overlay Window)**: หน้าต่าง Floating Widget มี `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` ลอยทับแอปอื่น
   - **Layer 3 (Activity Window)**: `MainActivity` มี `FLAG_KEEP_SCREEN_ON` ขณะเปิดแอป
   - **การสั่งให้พัก**: เมื่อผู้ใช้สั่ง "พักหน้าจอ", "สั่งให้พัก", "ล็อคหน้าจอ" → AI เรียก `device_press_button(button="lock")` ซึ่งจะปลด WakeLock, ปลดสถานะค้างจอ, ปรับโหมดเข้าสู่ `BACKGROUND_LISTEN`, และสั่งล็อคเครื่องผ่าน Accessibility Service ทันที

2. **Auto-Minimize to Floating Widget when Opening Other Apps**:
   - เมื่อ AI เรียก `device_open_app` หรือ `device_navigate` หรือเมื่อ `MainActivity.onPause()` เกิดขึ้น:
     ระบบจะสลับเข้าสู่ `AlwaysLiveState.MINI_FLOATING` และเปิด Floating Widget ให้อัตโนมัติ ทำให้ผู้ใช้เห็นหุ่นยนต์ AI ลอยอยู่บนแอปอื่น และจอไม่ดับ

3. **Screen Wake-on-Call (ปลุกหน้าจอสว่างอัตโนมัติเมื่อเรียก AI)**:
   - เมื่อตรวจพบเสียงเรียก Hotword ขณะจอดับ → `AlwaysLiveManager` จะกระตุ้น WakeLock ด้วย `PowerManager.ACQUIRE_CAUSES_WAKEUP`
   - พร้อมเรียก `MainActivity.instance?.wakeAndTurnScreenOn()` ซึ่งใช้ `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, และ `KeyguardManager.requestDismissKeyguard()` ทำให้หน้าจอติดสว่างและแสดงขึ้นมาทันทีโดยไม่ต้องกดปุ่ม Power

4. **Android 11+ Package Visibility & Android 14 Background Service Fix**:
   - เพิ่ม `QUERY_ALL_PACKAGES` และ Fallback Intent 3 ระดับ
   - เปลี่ยน `FloatingWidgetService` foregroundServiceType เป็น `specialUse` (`floating_robot_avatar`) ตามข้อกำหนด Android 14+ เพื่อป้องกัน OS ตัด Process ขณะทำงานเบื้องหลัง
   - ป้องกัน `HotwordDetector.stop()` ด้วย `recordingState` check ป้องกัน `IllegalStateException` จาก `AudioRecord`

5. **Voice-Controlled Always AI Live (เปิด/ปิดโหมด Always / โหมดควบคุมด้วยเสียง)**:
   - สั่ง "เปิดโหมด Always" หรือ "เปิดโหมดควบคุม" → AI เรียก `device_always_live(action="on")` ปลุกหน้าจอ ขยายเป็น Full Screen พร้อม 3D Robot Avatar และคลื่นเสียง visualizer โดยค้างหน้าจอเปิดกว้างตลอดเวลา
   - สั่ง "ปิดโหมด Always" หรือ "ปิดโหมดควบคุม" → AI เรียก `device_always_live(action="off")` ย่อออกจากโหมด Full Screen กลับสู่หน้าปกติอย่างราบรื่นโดยไม่ขัดจังหวะการพูด

6. **Android 13+ Restricted Settings (การตั้งค่าที่ถูกจำกัดใน Accessibility Service)**:
   - **สาเหตุ**: ใน Android 13/14/15 Google มีระบบรักษาความปลอดภัยที่จะบล็อก Accessibility Service สำหรับแอปที่ไม่ได้ติดตั้งผ่าน Google Play Store (Sideload / Debug APK) และขึ้นว่า "แอปถูกปฏิเสธไม่ให้เข้าถึง" (Restricted setting)
   - **วิธีปลดล็อกผ่านหน้าจอมือถือ**: เข้า **การตั้งค่า (Settings) > แอป (Apps) > ดูแอปทั้งหมด > Personal AI Bot > แตะจุด 3 จุดที่มุมขวาบน (⋮) > เลือก "อนุญาตการตั้งค่าที่ถูกจำกัด" (Allow restricted settings)** จากนั้นสแกนนิ้วเพื่อยืนยัน แล้วจะสามารถเปิด Accessibility Service ได้ตามปกติ
   - **วิธีปลดล็อกผ่านคำสั่ง ADB**:
     `adb shell appops set com.example.personalaibot ACCESS_RESTRICTED_SETTINGS allow`
     `adb shell settings put secure enabled_accessibility_services com.example.personalaibot/com.example.personalaibot.service.JarvisAccessibilityService`

7. **Google Maps Smart Intent: ดูพิกัดสถานที่ (View Location) vs นำทางจริง (Turn-by-Turn Navigation)**:
   - **ปัญหาเดิม**: เมื่อผู้ใช้สั่ง "เปิดแผนที่ไปญี่ปุ่น", "ไปที่นิวยอร์ก", "เปิดแผนที่ไปกรุงเทพ" ระบบสั่ง `google.navigation:q=...` ซึ่งเป็นการบังคับโหมดนำทาง GPS ขับรถแบบเลี้ยวต่อเลี้ยว ทำให้สถานที่ข้ามประเทศ/ข้ามทวีปคำนวณเส้นทางไม่ได้และขึ้นกล่องข้อความเตือน "ไม่พบเส้นทาง"
   - **การปรับปรุง**:
     - เพิ่มพารามิเตอร์ `action` ใน `device_navigate`:
       - `action="view"` (ค่าเริ่มต้น): เปิด Google Maps ดูตำแหน่งสถานที่/เมือง/ประเทศ หรือค้นหาพิกัดโดยตรง ด้วย Android URI `geo:0,0?q=<destination>` แสดงหมุดและรายละเอียดสถานที่ทันทีทั่วโลก โดยไม่เริ่มคำนวณเส้นทางขับรถ
       - `action="navigate"`: เปิดโหมดนำทาง GPS เลี้ยวต่อเลี้ยวด้วย `google.navigation:q=<destination>&mode=<mode>`
     - ปรับปรุง `JarvisPersona.kt` (ทั้ง `DEVICE_CONTROL_RULES` และ `LIVE_RULES` ข้อ 13):
       - เมื่อผู้ใช้สั่ง "เปิดแผนที่...", "ดูแผนที่...", "เปิด location...", "เปิดพิกัด..." หรือสั่งไปยังสถานที่/เมือง/ประเทศ โดยไม่มีคำว่า "นำทาง" $\to$ เรียก `device_navigate(destination="...", action="view")` เสมอ
       - เมื่อผู้ใช้สั่ง "นำทางไป...", "เริ่มนำทาง..." หรือ "พาขับรถไป..." $\to$ เรียก `device_navigate(destination="...", action="navigate", mode="drive")`
     - เพิ่ม Fallback Intent ไปยัง `https://www.google.com/maps/search/?api=1&query=...` ในกรณีที่แอป Maps มีปัญหา

---

## 5. การทดสอบและการรับรอง (Verification)
- **Unit Tests**: `DeviceControlTest.kt` และ `AlwaysLiveTest.kt` ผ่าน 100%
- **Gradle Build**: คอมไพล์ Kotlin Android Debug และรัน `testDebugUnitTest` ผ่านฉลุยโดยไม่มีข้อผิดพลาด
- **Physical Device Test (Samsung Android 14)**: ทดสอบเปิดพิกัด `geo:0,0?q=New+York` ด้วย ADB สำเร็จ หมุดปักกลางนิวยอร์ก ข้อมูลเมืองขึ้นครบถ้วนโดยไม่มี error "ไม่พบเส้นทาง" พร้อม Mini Avatar ลอยทับอย่างถูกต้อง

---
title: Changelog 2026-09-08 Voice Alert Delivery & Background Screen Wakeup
category: architecture
tags: [automation, voice-alert, tts, live-voice, background-service, wakelock]
sources: [JarvisAutomationService.kt, AlertController.kt, JarvisOrchestrator.kt, ToolExecutor.kt, AutomationManager.kt]
created: 2026-09-08
updated: 2026-09-08
---

# Changelog 2026-09-08: Voice Alert Delivery & Background Screen Wakeup Architecture

## ปัญหาที่พบ (Problem Statement)
ผู้ใช้รายงานว่า: *"มีการแจ้งในแชท แต่ไม่มีการเด้งเตือนเป็นเสียงพูด"*
จาก Logcat:
```text
🔔 FIRE 'Anticipation XAUUSD [15M]' [trading_signal_alert] symbol=XAUUSD@15m value=1 | mode=ai aiSummary=true voice=false/device model=gemini-3.6-flash
💬 pushToChat kind={"type":"signal_alert_ai","kind":"anticipation","job_id":5,...}
```
ระบบยิง Alert และส่งการ์ดเข้าแชท แต่ไม่มีเสียงพูดแจ้งเตือน เนื่องจาก:
1. `alert_voice` ใน `AlertController.kt` และ fallback SQLite `AppSetting` มีค่าเริ่มต้นเป็น `false`
2. ฟังก์ชัน `deliverChatAndVoice()` ใน `JarvisAutomationService.kt` เมื่อเห็นว่า `settingEnabled("alert_voice", false)` เป็น `false` จะข้าม `speakAlert()` แล้ววิ่งตรงเข้า `pushToChat()` ทันที
3. เมื่อสร้าง Alert ผ่านคำสั่งเสียง Live Voice เช่น `trading_signal_anticipation` ระบบไม่ได้เปิดสวิตช์เสียงให้
4. ขณะหน้าจอปิดหรืออยู่ในโหมด Doze ในเวลากลางคืน ไม่มีการปลุกหน้าจอ (`wakeScreen()`) และไม่มี `WakeLock` ระหว่างเล่นเสียง

---

## การเปลี่ยนแปลงเชิงสถาปัตยกรรม (Architectural Changes)

### 1. ปรับค่าเริ่มต้น `alert_voice` เป็น `true` ทั่วทั้งระบบ
- `AlertController.kt`:
  - `_alertVoiceEnabled = MutableStateFlow(true)`
  - `loadPersistedSettings()`: `getSetting("alert_voice") ?: true`
- `JarvisAutomationService.kt`:
  - ปรับ fallback ของ `settingEnabled("alert_voice", true)` ในทุกจุด (ScheduledTasks, deliverChatAndVoice, fireJobAlert, useLiveSummary)

### 2. Auto-Activation เมื่อสร้าง Alert จากคำสั่งเสียงและ Anticipation
- `AutomationManager.kt`:
  - เพิ่มเมธอด `setAlertVoiceEnabled(enabled: Boolean)` สำหรับ persist ค่าลง SQLite
- `JarvisOrchestrator.kt`:
  - ใน `onManageAlerts` (action == "create"): ตรวจสอบหาก `voice != "false"` ให้สั่ง `automationManager.setAlertVoiceEnabled(true)` และแสดงสถานะ `(เปิดเสียงพูดเตือน)` ในข้อความตอบกลับ
- `ToolExecutor.kt`:
  - ใน `executeSignalAnticipation`: แนบ `"voice" to (args["voice"] ?: "true")` เข้าไปใน `alertArgs`
- `TradingToolDefinitions.kt`:
  - บันทึกพารามิเตอร์ `voice` ในคำอธิบายฟังก์ชันของ `trading_signal_anticipation` และ `automation_manage_alerts`

### 3. Background Screen Wakeup & CPU WakeLock
- `speakAlertNow()`:
  - เรียก `AlwaysLiveManager.getInstanceOrNull()?.wakeScreen()` ทันทีเมื่อ Alert เข้า เพื่อเปิดหน้าจอและแสดงหน้าต่างให้ผู้ใช้มองเห็น
  - ขอ `PowerManager.PARTIAL_WAKE_LOCK` ชั่วคราว 30 วินาที เพื่อป้องกัน Android Doze ตัดการทำงานของ CPU ขณะสังเคราะห์เสียงหรือสตรีมเสียง Live ผ่าน WebSocket

### 4. Android TTS AudioAttributes & Ready Watchdog
- `initTts()`:
  - กำหนด `AudioAttributes`: `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` และ `CONTENT_TYPE_SPEECH` เพื่อให้เสียงพูดดังชัดเจนในทุกสถานะ ไม่ถูกลดทอนด้วยระดับเสียงทั่วไป
- `speak(text: String)`:
  - หาก `!ttsReady` จะรอการ initialize สูงสุด 1,500ms (100ms polling) ป้องกันการ drop ข้อความเสียงทิ้ง
  - หน่วงเวลาตามความยาวประโยคเพื่อให้ `WakeLock` ค้างไว้จนกว่า TTS จะเปล่งเสียงจนจบ

### 5. Dynamic Live Voice Fallback Chain
- `liveVoiceChain()`:
  - สลับจากการใช้รายการโมเดล hardcode ไปเรียก `ModelConfig.getLiveFallbackChain()` เชื่อมต่อกับระบบ Dynamic Model Synchronization

---

## ผลการทดสอบ (Verification)
1. **Unit Test**: รัน `:composeApp:testDebugUnitTest` ผ่าน 100%
2. **Device Deployment**: ติดตั้ง `PersonalAIBot-debug.apk` ลงอุปกรณ์ Samsung Galaxy S22 Ultra (SM-S908E) สำเร็จ
3. **Logcat Validation**:
   - กระบวนการ `JarvisAutomationService` (PID 31403) เริ่มต้น AudioTrack, TTS Engine, และติดตาม `Anticipation XAUUSD [15M]` อย่างสมบูรณ์
   - ทดสอบจำลอง Broadcast `ACTION_ALERT_REPEAT` ทำงานถูกต้อง

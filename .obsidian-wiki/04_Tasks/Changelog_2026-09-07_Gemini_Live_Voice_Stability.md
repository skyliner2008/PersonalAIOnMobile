# Changelog: Gemini Live Voice & Connection Stability Optimization

**Date**: 2026-09-07  
**Category**: Core AI / Voice / Live WebSocket  
**Target Files**:
- `ModelConfig.kt`
- `JarvisPersona.kt`
- `VoiceController.kt`
- `JarvisOrchestrator.kt`
- `LiveGeminiService.kt`
- `DynamicModelTest.kt`

---

## 1. ปัญหาและสาเหตุที่พบ (Root Cause Analysis)

### 1.1 ปัญหาการเชื่อมต่อ Live API รอนาน หรือเชื่อมต่อไม่ติด
- **สาเหตุ**:
  - เมื่อใช้ `gemini-3.1-flash-live-preview` หาก Google API มีปัญหา latency หรือ WebSocket handshake เกิดค้าง (Stall) ตัวโค้ดเดิมไม่มี Watchdog คอยจับเวลา `setupComplete`
  - หาก `gemini-3.1-flash-live-preview` เกิดข้อผิดพลาดตอน Setup หรือ Handshake ระบบเดิมไม่ได้สลับไปยังโมเดลสำรอง (เช่น `gemini-2.5-flash-native-audio`) โดยอัตโนมัติ ทำให้ผู้ใช้ต้องเข้าไปกดเปลี่ยนโมเดลในหน้าตั้งค่าเอง

### 1.2 ปัญหาน้ำเสียงเปลี่ยน พูดไม่ชัด สำเนียงเพี้ยนเหมือนฝรั่ง
- **สาเหตุ**:
  - ตอนเริ่มเซสชัน Live ระบบส่งข้อความข้อความเปิด: `สวัสดีJARVIS พร้อมคุยไหม`
  - ตัวอักษรภาษาอังกฤษ `JARVIS` ไปกระตุ้นโมเดลการถอดรหัสเสียง (Multimodal Acoustic Synthesizer) ของ Gemini ให้เปลี่ยนโหมด Prosody ไปเป็นสำเนียงภาษาอังกฤษแบบชาวต่างชาติพูดไทย (Foreign Accent)
  - คำสั่งทักทายเดิมไม่ได้ล็อกประโยคคำตอบ ทำให้โมเดลแต่งประโยคเอง มีทั้งแต่งเป็นบริติชบัตเลอร์ (`DEFAULT_AGENT_VIBE = "(British Butler Style)"`), แต่งเรื่องตลาดหุ้น, หรือพูดประโยคยาวไม่คงที่

---

## 2. รายละเอียดการปรับปรุงและแก้ไข (Implementation Details)

### 2.1 Multi-Tier Automated Fallback Chain (`ModelConfig.kt`, `LiveGeminiService.kt`)
- เพิ่ม `ModelConfig.getLiveFallbackChain(primaryModel)`:
  - ลำดับที่ 1: `gemini-3.1-flash-live-preview` (Primary Preview Model)
  - ลำดับที่ 2: `gemini-2.5-flash-native-audio-preview-12-2025` (Stable Fallback)
  - ลำดับที่ 3: `gemini-2.5-flash-native-audio-latest`
- ระบบตรวจสอบคัดกรองโมเดลที่ติด Blacklist (Dead Models) ออกจาก Chain อัตโนมัติ
- ใน `LiveGeminiService`:
  - หากเชื่อมต่อหรือ Setup โมเดลแรกไม่สำเร็จ หรือถูกปฏิเสธด้วย Terminal Setup Error หรือเกิด Timeout ระบบจะหมุน (Rotate) ไปยังโมเดลถัดไปใน Fallback Chain ทันทีโดยไม่ต้องให้ผู้ใช้เปลี่ยนค่าเอง

### 2.2 7-Second Setup Watchdog (`LiveGeminiService.kt`)
- เพิ่ม Coroutine Watchdog ตรวจสอบสัญญาณ `setupComplete` จาก Server:
  - หากภายใน 7,000ms ยังไม่ได้รับ `setupComplete` Watchdog จะทำการปิด WebSocket session ด้วยเหตุผล `"setupComplete timeout"`
  - ทริกเกอร์ให้หมุนโมเดลไปยัง fallback ถัดไปทันที ช่วยป้องกันการค้างรอนาน 15–30 วินาที

### 2.3 Acoustic Synthesizer Purity & Native Thai Accent Shield (`VoiceController.kt`, `JarvisPersona.kt`)
- **ตัดคำว่า JARVIS ภาษาอังกฤษออกจาก Prompt เริ่มต้น**:
  - เปลี่ยนจาก `"สวัสดีJARVIS พร้อมคุยไหม"` เป็นภาษาไทยล้วน `"สวัสดีจาวิส พร้อมคุยไหม"`
  - ป้องกัน Acoustic Synthesizer ของ Gemini ถูกกระตุ้นด้วยโทเค็นตัวอักษรภาษาอังกฤษ
- **ปรับแต่ง System Prompt สำหรับ Live Mode (`JarvisPersona.kt`)**:
  - ปรับ `DEFAULT_AGENT_VIBE` เป็น `"สุภาพ มั่นใจ อบอุ่น เป็นธรรมชาติ แบบผู้ช่วยส่วนตัวอัจฉริยะ"`
  - กำหนดกฎเข้มงวด `THAI ARTICULATION & ACCENT`: ห้ามพูดติดสำเนียงต่างชาติ/ฝรั่ง ห้ามดัดลิ้น ชื่อตัวเองในภาษาพูดคือ "จาวิส" เท่านั้น
  - ล็อกประโยคทักทายมาตรฐานตายตัว (Fixed Session Greeting) คำต่อคำ:
    `"สวัสดีค่ะนายท่าน จาวิสพร้อมคุยแล้วค่ะ มีอะไรให้จาวิสช่วยวันนี้ดีคะ"` (ผูกกับ `userCallName` และอนุภาคคำลงท้ายตามเพศของผู้ช่วย)
  - สั่งห้ามพูดเรื่องการเทรด ห้ามพูดเรื่องตลาดหุ้น หรือแต่งประโยคเองในเทิร์นทักทายเปิดเซสชัน

---

## 3. ผลการทดสอบ (Verification)
- **Unit Tests**:
  - รัน `DynamicModelTest.kt` ผ่าน 100% ครอบคลุมทั้ง `testLiveFallbackChain_PrioritizesPrimaryAndExcludesDead` และ `testLiveSystemPrompt_ContainsConsistentThaiGreeting`
  - รัน `:composeApp:testDebugUnitTest` ผ่านครบทั้ง 170 เทสต์
- **Build & Device Installation**:
  - คอมไพล์และติดตั้ง `:composeApp:installDebug` ไปยังเครื่อง `SM-S908E` สำเร็จ
  - ตรวจสอบผ่าน ADB Logcat พบการตรวจจับโมเดล Chat 25 ตัว และ Live 5 ตัว และระบบพร้อมทำงาน

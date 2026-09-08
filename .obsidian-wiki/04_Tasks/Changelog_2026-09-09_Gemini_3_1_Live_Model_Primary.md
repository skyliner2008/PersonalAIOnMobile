---
title: "Changelog 2026-09-09: Gemini 3.1 Flash Live Primary Model & Model Switch Fix"
category: "Changelog"
tags: ["gemini-live", "model-config", "voice-latency", "settings", "bugfix"]
sources: ["Android Logcat", "ModelConfig.kt", "SettingsController.kt", "LiveGeminiService.kt", "JarvisViewModel.kt"]
created: "2026-09-09"
updated: "2026-09-09"
---

# Changelog 2026-09-09: Gemini 3.1 Flash Live Primary Model & Model Switch Fix

## 1. Context & Objectives
จากการทดสอบ Live Voice ของผู้ใช้กับหลายโมเดล:
- `gemini-2.5-flash-native-audio-preview-09-2025`: ทำงานค่อนข้างช้า
- `gemini-2.5-flash-native-audio-latest`: ทำงานช้า พูดซ้ำ และมีข้อความภาษาอังกฤษหลุดออกมา
- `gemini-2.5-flash-native-audio-preview-12-2025`: ทำงานช้า พูดซ้ำ และเกิดข้อผิดพลาด text token
- `gemini-3.1-flash-live-preview`: **ทำงานได้รวดเร็วและดีที่สุด** (Latency READY ~835ms, สำเนียงภาษาไทยเป็นธรรมชาติ, รองรับ Native Tool Calls แม่นยำ)

เป้าหมาย:
1. ตั้งค่า `gemini-3.1-flash-live-preview` เป็นโมเดลหลัก (Default Live Model)
2. แก้ไขบั๊กที่โมเดล Live สลับกลับไปเป็น `gemini-2.5-flash-native-audio-preview-09-2025` เองโดยอัตโนมัติ

## 2. Root Cause Analysis
จากการวิเคราะห์เชิงลึก พบสาเหตุ 4 จุดที่เกี่ยวข้องกัน:
1. **`deprecatedLiveModels` ใน `SettingsController.kt`**:
   `"gemini-3.1-flash-live-preview"` เคยถูกใส่ไว้ในลิสต์ `deprecatedLiveModels` ทำให้ทุกครั้งที่แอพบูต (`loadPersistedSettings`) โค้ดมองว่า 3.1 ตกยุคและเขียนทับ SQLite ด้วยโมเดลสำรองเก่าทันที
2. **Silent Persistence ใน `JarvisViewModel.kt`**:
   เมื่อเกิด fallback ชั่วคราว (เช่น ติดโควต้าหรือเน็ตสะดุดชั่วขณะ) `orchestrator.onLiveModelChanged` เคยเรียก `settings.updateLiveModelSilently(winningModel)` ซึ่งนำโมเดล fallback ไปเซฟทับฐานข้อมูลจริงอย่างถาวร
3. **Session Reconnection เผลอใช้ Fallback ข้ามรอบ**:
   ใน `LiveGeminiService.kt` เมื่อมีการ fallback ตัวแปร `liveModelName` ไม่ได้ถูกรีเซ็ตกลับเป็นโมเดลที่ผู้ใช้เลือก (`configuredLiveModelName`) เมื่อเริ่มรอบใหม่
4. **Watchdog Timeout 3500ms กระชั้นเกินไป + Penalty 15 นาที**:
   ในเน็ตมือถือ การเชื่อมต่อ WebSocket handshake อาจใช้เวลา 3-4 วินาที การตัด timeout ที่ 3.5 วินาทีทำให้โมเดลถูกลงโทษ (`penalizeLiveModel`) นานถึง 15 นาที และข้ามไปใช้ fallback อื่น

## 3. Implementation Details
1. **`ModelConfig.kt`**:
   - `DEFAULT_LIVE_MODEL = "gemini-3.1-flash-live-preview"`
   - จัดลำดับ `liveCandidates` และ `SEED_LIVE_MODELS` ให้ `gemini-3.1-flash-live-preview` อยู่อันดับ 1
   - ปรับลดเวลา `penalizeLiveModel` จาก 15 นาที เหลือ 60 วินาที
   - เพิ่ม `resetForTesting()` สำหรับ Unit Test
2. **`SettingsController.kt`**:
   - นำ `"gemini-3.1-flash-live-preview"` ออกจาก `deprecatedLiveModels`
   - เพิ่ม `"gemini-2.5-flash-native-audio-preview-09-2025"` ลงใน `deprecatedLiveModels` เพื่อ auto-migrate เครื่องที่เคยค้างอยู่ที่โมเดลเก่าให้ขึ้นเป็น 3.1 ทันที
3. **`JarvisViewModel.kt`**:
   - ยกเลิกการเรียก `settings.updateLiveModelSilently(winningModel)` ตอน runtime fallback เพื่อคงค่าที่ผู้ใช้เลือกไว้ในฐานข้อมูลอย่างถาวร
4. **`LiveGeminiService.kt`**:
   - เพิ่ม `configuredLiveModelName` เพื่อให้แน่ใจว่า Attempt 1 ของทุก session การกดคุยจะเริ่มด้วยโมเดลหลักที่ผู้ใช้เลือกเสมอ
   - ปรับ watchdog timeout จาก 3500ms เป็น 6000ms เพื่อความเสถียรบนเครือข่ายมือถือ
5. **`DynamicModelTest.kt`**:
   - อัปเดตเทสให้ตรวจสอบ `DEFAULT_LIVE_MODEL == "gemini-3.1-flash-live-preview"`
   - เพิ่ม `@BeforeTest` และ `@AfterTest` เพื่อ reset สถานะ `ModelConfig`

## 4. Verification
- รัน Gradle Unit Tests ผ่านทั้งหมด
- ตรวจสอบ Fallback Chain และการทำงานร่วมกับระบบเสียงไทย

---
title: Changelog 2026-09-08 Live Voice Connection Latency Optimization & Dynamic Self-Healing
category: architecture
tags: [live-voice, latency, websocket, gemini-live, self-healing, model-config]
sources: [LiveGeminiService.kt, ModelConfig.kt, SettingsController.kt, JarvisOrchestrator.kt, JarvisViewModel.kt, DynamicModelTest.kt]
created: 2026-09-08
updated: 2026-09-08
---

# Changelog 2026-09-08: Live Voice Connection Latency Optimization & Dynamic Self-Healing

## ปัญหาที่พบ (Problem Statement)
ผู้ใช้รายงานว่า: *"ปัญหาต่อไป การ Connecting Live session บางครั้งนาน กว่าจะ เชื่อมต่อได้"*
จาก Logcat ผู้ใช้เมื่อกดเปิดไมค์ Live Voice:
```text
2026-09-08 16:48:08.594 Starting Live Voice Input
2026-09-08 16:48:08.610 Connecting to Live API with model: gemini-3.1-flash-live-preview (attempt 1)
2026-09-08 16:48:16.202 ⏱️ setupComplete timeout (7000ms) for model gemini-3.1-flash-live-preview — closing WebSocket to trigger fallback
2026-09-08 16:48:23.429 Session closed: NORMAL — 
2026-09-08 16:48:23.431 🔄 Rotating to fallback model: gemini-2.5-flash-native-audio-preview-12-2025 (1/5)
2026-09-08 16:48:30.997 ⏱️ setupComplete timeout (7000ms) for model gemini-2.5-flash-native-audio-preview-12-2025...
2026-09-08 16:48:35.413 Session closed: NORMAL — 
2026-09-08 16:48:35.416 🔄 Rotating to fallback model: gemini-2.5-flash-native-audio-latest (2/5)
2026-09-08 16:48:42.648 ⏱️ setupComplete timeout (7000ms) for model gemini-2.5-flash-native-audio-latest...
2026-09-08 16:48:44.001 Session closed: NORMAL — 
2026-09-08 16:48:44.004 🔄 Rotating to fallback model: gemini-2.5-flash-native-audio-preview-09-2025 (3/5)
2026-09-08 16:48:45.531 ✅ Live session READY (1526ms)
```
**วิเคราะห์สาเหตุ**:
1. **Model Chain เรียงผิด**: โมเดลตัวที่ต่อได้เร็วที่สุดคือ `gemini-2.5-flash-native-audio-preview-09-2025` (ใช้เวลาแค่ 1526ms) แต่ระบบดันเอา `gemini-3.1-flash-live-preview` และ `12-2025` ซึ่งเป็น preview endpoints ที่มักค้าง/ล้นของ Google มาเป็นตัวแรก
2. **Ktor WebSocket Close Handshake Stalling**: เมื่อ Watchdog จับ timeout แล้วสั่ง `close()` แทนที่ socket จะตัดทันที มันกลับต้องรอ server ส่ง 2-way close frame ตอบกลับ ทำให้ค้างไปอีก 7.2 วินาทีต่อ 1 โมเดล
3. **ไม่มีการจำโมเดลที่ใช้งานได้จริง (No Promotion/Persistence)**: เมื่อโมเดล `09-2025` เชื่อมต่อผ่านแล้ว ระบบไม่ได้บันทึกเลื่อนลำดับขึ้นมาเป็นเบอร์ 1 ใน SQLite/Memory ทำให้พอกดเปิดไมค์ใหม่อีกครั้ง ก็ต้องวนลูปไปรอ 3 โมเดลแรกที่ timeout ซ้ำใหม่อีก 37 วินาที

---

## การเปลี่ยนแปลงเชิงสถาปัตยกรรม (Architectural Changes)

### 1. กำหนด `gemini-2.5-flash-native-audio-preview-09-2025` เป็นโมเดลหลักอันดับ 1
- `ModelConfig.kt`:
  - `DEFAULT_LIVE_MODEL = "gemini-2.5-flash-native-audio-preview-09-2025"`
  - `SEED_LIVE_MODELS` เรียง `09-2025` ขึ้นอันดับ 1 และตัด `12-2025` ที่ deprecated ทิ้ง
  - `updateAvailableModels()` กรองเอา `09-2025` ขึ้นนำก่อนเสมอ
- `SettingsController.kt`:
  - เพิ่ม `"gemini-3.1-flash-live-preview"` และ `"12-2025"` ลงใน `deprecatedLiveModels` เพื่อให้แอปทำการ Migrate ค่าที่เคยบันทึกไว้ในฐานข้อมูลเก่าของผู้ใช้ มาเป็น `09-2025` โดยอัตโนมัติเมื่อเปิดแอป

### 2. ลด Watchdog Timeout เหลือ 3500ms และตัด Socket ทันทีใน <1ms
- `LiveGeminiService.kt`:
  - ปรับ `delay(7000L)` ลงเหลือ `delay(3500L)` (เพราะโมเดลปกติใช้เวลาเพียง 1-2 วินาที)
  - เมื่อ timeout สั่ง `this@webSocket.cancel(CancellationException("setupComplete timeout"))` เพื่อตัดการเชื่อมต่อทันทีโดยไม่ต้องรอ server ส่ง close frame (ลดเวลาค้างจาก 7.2s เหลือ <1ms)
  - ดักจับ `CancellationException` เฉพาะเคส `timedOutWaitingForSetup` เพื่อสั่งสลับ fallback ไปโมเดลถัดไปทันที

### 3. ระบบ Auto-Promotion และ Temporary Penalty (Self-Healing)
- `ModelConfig.kt`:
  - `promoteHealthyLiveModel(modelName)`: เมื่อโมเดลใดเชื่อมต่อสำเร็จ (`setupComplete`) จะถูกเลื่อนขึ้นเป็นอันดับ 1 ของ fallback chain
  - `penalizeLiveModel(modelName, durationMs = 15m)`: เมื่อโมเดลใดเกิด timeout จะถูกลงโทษเป็นเวลา 15 นาที โดยถูกย้ายไปอยู่ท้ายคิวสุด เพื่อไม่ให้ผู้ใช้ต้องรอซ้ำ
  - `getLiveFallbackChain()`: เรียงลำดับ โมเดลที่ขอ -> โมเดลที่ถูก promote -> โมเดล active ปกติ -> โมเดลที่ติด penalty
- `JarvisOrchestrator.kt` & `SettingsController.kt`:
  - ผูก `onLiveModelPromoted` เพื่อบันทึกชื่อโมเดลที่ใช้งานได้จริงลงในฐานข้อมูล SQLite (`live_model_name`) แบบ background ทันที

---

## ผลการทดสอบ (Verification)
1. **Unit Tests**:
   - `DynamicModelTest` รันผ่าน 100%:
     - `testDefaultLiveModel_IsFast092025` (PASS)
     - `testPromoteHealthyLiveModel` (PASS)
     - `testPenalizeLiveModel_DemotesToEnd` (PASS)
2. **Device Deployment**:
   - ติดตั้งลงบน Samsung Galaxy S22 Ultra (SM-S908E)
   - Logcat ยืนยันการ auto-migration:
     `D JarvisVM: Migrating deprecated live model 'gemini-3.1-flash-live-preview' to 'gemini-2.5-flash-native-audio-preview-09-2025'`
   - การเชื่อมต่อ Live Voice ครั้งต่อไปต่อติดในเวลา ~1.5 วินาทีทันที

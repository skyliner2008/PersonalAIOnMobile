---
title: Dynamic Gemini Model Registry & Live API Model Discovery
category: Tasks
tags: [jarvis, gemini, model-registry, api-error-404, fallback, self-healing]
sources: [ModelConfig.kt, SettingsController.kt, GeminiService.kt, GeminiLlmProvider.kt, JarvisAutomationService.kt, MarketTechnicalToolHandler.kt]
created: 2026-09-07
updated: 2026-09-07
---

# 🚀 Dynamic Gemini Model Registry & Self-Healing Model Discovery

## 1. ปัญหาและสาเหตุที่พบ (Root Cause Analysis)
1. **API Error 404 (Model Not Found)**:
   - ผู้ใช้พบ error: `API Error 404 (model=gemini-3.1-pro): models/gemini-3.1-pro is not found for API version v1beta, or is not supported for generateContent. Call ModelService.ListModels to see the list of available models and their supported methods.`
   - จากการตรวจสอบ API สดของ Google (`/v1beta/models`) พบว่า Google ไม่มีโมเดลชื่อ `gemini-3.1-pro` มีเพียง `gemini-3.1-pro-preview` เท่านั้น
   - การที่โมเดลถูก hardcode หรือบันทึกค้างไว้ใน Database ทำให้แอปพยายามส่ง request ด้วยโมเดลที่ไม่มีอยู่จริงซ้ำๆ
2. **Timeout Cascade ใน Voice Session**:
   - เมื่อ `gemini-3.1-pro` เกิด 404 ระบบทำการ fallback ไปยัง `gemini-3.6-flash` แต่ในคำสั่งที่ซับซ้อน เช่น `trading_macro_calendar` ตัว prompt ยาวและ timeout เริ่มต้น 20s หมดลง ระบบพยายาม retry ด้วย timeout 45s สะสมเวลาค้างกว่า 54 วินาที ทำให้การโต้ตอบด้วยเสียงสะดุด

## 2. สถาปัตยกรรม Dynamic Model Registry (ปลอด Hardcoded Model)

### 2.1 Dynamic Synchronization กับ Google `ModelService.ListModels`
- ดึงรายการโมเดลจริงผ่าน Google Gemini API (`/v1beta/models?key=$apiKey`) อัตโนมัติเมื่อเริ่มแอปหรือเมื่อผู้ใช้เปิดหน้า Settings
- จัดเก็บแคชโมเดลที่ใช้งานได้จริงลงใน SQLite Database (`cached_gemini_models`) ทำให้ตอน Cold Start แอปเปิดมาพร้อมรายชื่อโมเดลล่าสุดทันทีโดยไม่ต้องรอ network call

### 2.2 Dynamic Capability Filtering & Flash Priority Ranking
- กรองเฉพาะโมเดลที่รองรับ `generateContent` เท่านั้น
- คัดกรองโมเดลที่ไม่ใช่ Generative Chat ออกอัตโนมัติ เช่น `text-embedding-004`, `imagen-3.0`, `robotics`, `aqa`, `tts`
- **Smart Sorting**: เรียงลำดับโมเดลตระกูล **Flash / Flash-Lite** ที่มี Token Quota สูงและ Latency ต่ำที่สุดขึ้นก่อน เรียงตาม Semantic Version จากใหม่ไปเก่า (เช่น `gemini-3.8-flash` → `gemini-3.7-flash` → `gemini-3.6-flash` → `gemini-3.5-flash-lite` → ...) ตามด้วยโมเดล Pro

### 2.3 Instant 404 Blacklisting & Self-Healing Loop
- หาก API ตอบกลับ HTTP 404 `NOT_FOUND`:
  1. บันทึกโมเดลนั้นเข้าสู่ **Dead Model Blacklist** ทันทีด้วย `ModelConfig.markModelDead(modelName)`
  2. กรองโมเดลที่ตายออกจาก Fallback Chain แบบ Real-time ทันที เพื่อไม่ให้เกิด 404 ซ้ำ
  3. แจ้งเตือนผ่าน Callback `onModelNotFound` เพื่อให้ `SettingsController` เรียก `refreshModels()` ดึงรายการใหม่จาก Google API
  4. Auto-Migrate การตั้งค่า `selectedModel` ใน Database ให้ข้ามไปยัง Flash Model ที่ดีที่สุดและใช้งานได้จริง (`ModelConfig.getBestActiveModel()`)

### 2.4 Voice & Tool Latency Shield
- ใน `MarketTechnicalToolHandler.executeMacroCalendar()` ปรับ timeout เป็น `12,000ms` พร้อมตั้ง `retryLongerOnTimeout = false` เพื่อไม่ให้บล็อกการทำงานของ Voice Session นานเกินไป และ fallback ไปยังโมเดลถัดไปทันทีหากเกิดปัญหา

## 3. ไฟล์ที่มีการแก้ไข (Modified Files)
- `composeApp/src/commonMain/kotlin/com/example/personalaibot/data/ModelConfig.kt`: เปลี่ยนเป็น Dynamic Registry พร้อมฟังก์ชัน `updateAvailableModels()`, `markModelDead()`, `isModelDead()`, `getFallbackChain()`, `getBestActiveModel()`
- `composeApp/src/commonMain/kotlin/com/example/personalaibot/controller/SettingsController.kt`: เพิ่ม cold-start validation, auto-migration จากโมเดลที่ตาย, แคชโมเดลลง SQLite, และ listener `onModelNotFound`
- `composeApp/src/commonMain/kotlin/com/example/personalaibot/data/GeminiService.kt`: เพิ่ม callback `onModelNotFound`, ดักจับ HTTP 404 ทั้งใน streaming และ non-streaming fallback chain
- `composeApp/src/commonMain/kotlin/com/example/personalaibot/data/providers/GeminiLlmProvider.kt`: ซิงก์ registry เมื่อ listModels และกรอง dead models ออก
- `composeApp/src/androidMain/kotlin/com/example/personalaibot/service/JarvisAutomationService.kt`: ปรับ `generateAiText` ให้ใช้ `ModelConfig.getFallbackChain()`
- `composeApp/src/commonMain/kotlin/com/example/personalaibot/tools/trading/MarketTechnicalToolHandler.kt`: ปรับ timeout ให้เร็วและปิด retryLonger ใน calendar analysis

## 4. ผลการทดสอบ (Verification)
- เขียน Unit Test ใหม่: `composeApp/src/commonTest/kotlin/com/example/personalaibot/DynamicModelTest.kt`
  - ตรวจสอบการคัดกรองโมเดลที่ไม่เกี่ยวข้อง (Embedding, Imagen) ออก
  - ตรวจสอบการเรียง Flash Version สูงสุดไว้หน้า Pro Models
  - ตรวจสอบ Blacklisting เมื่อโมเดลติด 404 และการสร้าง Fallback Chain ที่มีเฉพาะโมเดลที่มีชีวิต

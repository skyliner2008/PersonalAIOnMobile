---
name: Bugfix Pass 2026-04-14
description: Bug audit + fixes for PersonalAIBot — tool routing, resource leaks, parse safety
type: project
originSessionId: 01dd3ad3-9b09-4e5d-a4ff-acd6d2b13862
---
Ran full audit (Explore agent) on PersonalAIBot KMP codebase. Fixed 10 issues on 2026-04-14.

**Key fixes applied:**

1. `ToolExecutor.kt` — added handler for `analyze_and_display_report` (was falling into executeCustomSkill → "skill not found") และแก้ evalMath regex เพื่อไม่ให้แทน "e" ใน scientific notation/คำทั่วไป
2. `CameraToolExecutor.kt` — เพิ่ม handler สำหรับ `vision_activate`, `vision_deactivate`, `voice_get_profiles`, `voice_set_profile` (ก่อนหน้านี้คืน "Unknown camera tool" ใน Chat mode)
3. `SmcApiService.kt` — bounds check + logging ใน fetchCandlesFromBinance; JsonNull-safe parsing ใน Yahoo
4. `JarvisViewModel.kt` — close `HttpClient` ใน `onCleared()` (resource leak)
5. `LiveGeminiService.kt` — explicit `webSocketSession.close()` ใน finally block
6. `TradingApiService.kt` — JsonPrimitive safe-cast ใน scanTradingView และ getTechnicalAnalysis
7. `JarvisMemoryManager.kt` — clamp limit ใน getRecentHistory (DoS ป้องกัน)

**Why:** เจอ bug หลายจุดจากการรันออดิท โดยเฉพาะ tool routing ที่ fall-through แบบเงียบๆ และ resource leak

**How to apply:** ถ้าเพิ่ม tool ใหม่ใน ToolRegistry ต้องอัพเดต ToolExecutor.execute() หรือ CameraToolExecutor.execute() ให้มี case รองรับเสมอ

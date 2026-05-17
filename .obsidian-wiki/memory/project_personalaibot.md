---
name: PersonalAIBot Project
description: โปรเจก Kotlin Multiplatform (KMP) — Personal AI Bot คล้าย Jarvis — มี Trading + SMC Tools
type: project
---

PersonalAIBot เป็น KMP project (Android + iOS) ที่ใช้ Gemini Function Calling

**Architecture:**
- `tools/ToolRegistry.kt` — ลงทะเบียน tool definitions ทั้งหมด
- `tools/ToolExecutor.kt` — dispatch tool calls ไปยัง executor ต่างๆ
- `tools/trading/TradingToolExecutor.kt` — execute trading + SMC tools
- `tools/trading/TradingApiService.kt` — HTTP calls (Yahoo Finance, TradingView, Reddit)
- `tools/trading/SmcApiService.kt` — SMC algorithms (Binance klines)

**Why:** Self-hosted Jarvis-like platform สำหรับ personal use

**How to apply:** เมื่อเพิ่ม tool ใหม่ต้องอัพเดต: ToolDefinitions → ToolExecutor → ToolRegistry

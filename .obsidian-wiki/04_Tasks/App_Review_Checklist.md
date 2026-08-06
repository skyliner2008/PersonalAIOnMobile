# 📋 App Review Checklist — ตรวจสอบ/ปรับปรุงทีละหมวด

> เอกสารติดตามการ review โค้ดทั้งแอป แบ่งเป็นหมวดย่อยตามโครงสร้าง package จริง
> เมื่อตรวจ/แก้หมวดไหนเสร็จ ให้ติ๊ก ✅ + ใส่วันที่ + ลิงก์ไปยัง entry ใน [[log]]
> อัปเดตล่าสุด: 2026-08-03

## วิธีใช้
- ทำงานทีละ 1 หมวด ตามลำดับความสำคัญ (หรือบอสเลือกหมวดเองได้)
- ทุกหมวดมี "ขอบเขตไฟล์" ไว้เปิดตรวจ และ "ประเด็นที่ต้องดู" เป็น checklist ย่อย
- แก้เสร็จต้องคอมไพล์ผ่าน + บันทึกลง [[log]] + อัปเดตสถานะในไฟล์นี้

---

## ✅ หมวดที่ตรวจและแก้ไขเสร็จแล้ว

### 1. Provider / ผู้ให้บริการ LLM ✅ (2026-07-30)
- **ขอบเขต**: `data/providers/*` (Gemini, OpenRouter, OpenAI, Claude, Minimax, Vertex, LiteLlm, ADK), `ModelConfig.kt`, Settings provider UX
- **สิ่งที่ทำแล้ว**: ตรวจรายชื่อโมเดลล่าสุดของแต่ละ provider, การเลือก/สลับ/คัดกรองโมเดล (live / chat / free model OpenRouter), ปรับ UX การตั้งค่า provider ให้ถูกต้องและใช้งานสะดวก
- **บันทึก**: ดู [[log]] entry วันที่ 2026-07-29/30 (Provider + UX Settings)

### 2. System Prompt / ตัวตน AI ✅ (2026-07-30)
- **ขอบเขต**: `ai/JarvisPersona.kt`, system prompt ทุกจุดที่ประกอบ, CORE_IDENTITY
- **สิ่งที่ทำแล้ว**: แก้ AI สับสนตัวตน/คำพูดไม่นิ่ง + สร้าง **CORE_IDENTITY Setting แบบปรับแต่งได้** (Agent Identity: Name/Creature/Vibe/Gender + User Identity: Name/What to call/Notes) — user แก้เองผ่านเมนู input ได้ และ AI แก้ให้ได้เมื่อ user สั่ง
- **บันทึก**: ดู [[log]] entry วันที่ 2026-07-30 (CORE_IDENTITY)

### 3. Trading Tool ✅ (2026-07-30)
- **ขอบเขต**: `tools/trading/*` (TradingToolExecutor, SmcToolExecutor, TradingApiService, SmcApiService, AdvancedTradingEngine, AutoTradingEngine, ModernTechnicalApiService, Mt5TerminalService, TaIndicators)
- **สิ่งที่ทำแล้ว**: ตรวจ tool ทั้งหมด 79 tools, เพิ่ม tool ข้อมูลเศรษฐกิจสหรัฐ (**FRED API** — แก้ `stream was reset: INTERNAL_ERROR` จนใช้งานได้), เพิ่ม **ปฏิทินเศรษฐกิจเรียลไทม์** (ฟรี ไม่ต้อง API key)
- **ต่อยอด (2026-08-03)**: แก้ระบบข่าว `trading_news` — ขยาย 3 → 6 แหล่ง (Google News ค้นตรงสินทรัพย์ + Yahoo/CNBC/MarketWatch/Investing.com/Cointelegraph), แก้ feed ตาย + HTML หลุด + description ซ้ำ — ทดสอบเครื่องจริงผ่าน 3 รอบ (ข่าวทองตรง, เสียงพูดครบ) ดู [[log]] entry ข่าว 2026-08-03
- **บันทึก**: ดู [[log]] entry วันที่ 2026-07-30 (FRED + Economic Calendar + Trading Tools audit)

### 4. Memory System (4 Layers + Embedding) ✅ (2026-07-30)
- **ขอบเขต**: `memory/JarvisMemoryManager.kt`, `data/embedding/*` (GeminiEmbeddingProvider, LocalOnnxEmbeddingProvider, EmbeddingProviderRegistry), sqldelight schema/migrations
- **สิ่งที่ทำแล้ว**: แก้ 7 จุด — B1 edge ซ้ำ (migration v3 + UNIQUE index), B2 heuristic ชน identity key, B3 GraphRAG write-only → เพิ่ม `getGraphContext()`, B4 auto sleep cycle ที่ 200 ข้อความ, E1 Gemini embedding API key ไม่อัปเดต, E2 Tokenizer รองรับ Unigram (SentencePiece/Viterbi), E3 ONNX inputs dynamic + minor fixes
- **รอทดสอบเครื่องจริง**: local ONNX ภาษาไทย, recall_memory แสดง graph context, auto sleep cycle, migration v3 บน install เก่า
- **บันทึก**: ดู [[log]] entry "Memory System Fix Pack"

---

## ⬜ หมวดที่รอตรวจสอบ (เรียงตามความสำคัญที่แนะนำ)

### 5. Tool System / การสร้าง Tool ⬜
- **ขอบเขต**: `tools/ToolRegistry.kt`, `ToolExecutor.kt`, `ToolDefinition.kt`, `ToolArgParser.kt`, `SideEffectDelegate.kt`
- **ประเด็นที่ต้องดู**:
  - [ ] singleton mutable state ของ ToolRegistry/ToolExecutor — thread safety
  - [ ] **AI สร้าง tool ใหม่เองไม่ได้** (user รายงาน 2026-07-30 ตอน 01:28) — ต้องวางระบบ dynamic tool creation / script tool
  - [ ] ตรวจ 79 tools ใน [[catalogue]] ว่าลงทะเบียนและเรียกใช้ครบจริง
  - [ ] JSON injection / arg parsing edge cases
- **ที่มา**: user request 2026-07-30 "ตรวจสอบระบบ tool ... ai agent ยังสร้าง tool เองไม่ได้"
- **ความคืบหน้าที่เกี่ยวข้อง (2026-08-03)**: เพิ่มหมวด tool ใหม่ "📚 Strategy Library" (3 tools: strategy_list/search/explain) — คลังกลยุทธ์ Quantpedia 60 แบบในเครื่อง ดู [[log]] entry 2026-08-03

### 6. Orchestrator / Intent Routing ⬜
- **ขอบเขต**: `ai/JarvisOrchestrator.kt`, `IntentClassifier.kt`, `TradingIntentUtility.kt`, `TradingToolPolicy.kt`, `LiveToolBridge.kt`
- **ประเด็นที่ต้องดู**:
  - [ ] **JarvisPlanner dead code** — สร้างใน Orchestrator แต่ไม่มี caller (ลบหรือใช้ให้จริง)
  - [ ] IntentClassifier keyword-weighted scoring — tie-break ไม่ deterministic
  - [ ] TradingIntentUtility ซ้ำซ้อนกับ IntentClassifier — ควรรวมเป็นตัวเดียว
  - [ ] tool calling loop — max iterations, error recovery
- **ที่มา**: [[ai_subsystem_review_2026-07-29]]

### 7. Live Mode / Voice ✅ (2026-07-30)
- **ขอบเขต**: `data/LiveGeminiService.kt`, `voice/VoiceManager.kt`, `voice/PcmAudioEngine.kt`, `ui/screen/LiveModePanel.kt`
- **สิ่งที่ทำแล้ว**: แก้เสียงตอบไม่หมด/เงียบ 5 จุด — F1 คืน transcription config ใน setup, F2 handle `interrupted` → flush playback, F3 audio flow มี buffer (แยก network/playback), F4 แก้ bridge instruction ขัด LIVE_RULES, F5 TTS fallback เมื่อ turn ไม่มีเสียง + `stopPlaying()` resume หลัง flush
- **ทดสอบเครื่องจริงแล้ว (2026-08-03)**: เสียงตอบครบทุก turn ยืนยันจาก log round5 + news_check2/3 (audio 1.5-1.7MB/turn) + ปรับ VOICE RULE เป็น 5-8 ประโยคมีสาระ (แก้ตอบสั้นเกิน) — ยังไม่ได้เทส barge-in ทับเสียงเก่าโดยตรง
- **บันทึก**: ดู [[log]] entry "Live Voice Fix รอบ 2 (หมวด 7)"

### 8. Scheduled Tasks / Automation ✅ (2026-08-03)
- **ขอบเขต**: `automation/AutomationManager.kt`, `AutomationEvaluator.kt`, `AutomationModels.kt`, `ui/screen/AutomationScreen.kt`, `tools/trading/TradingToolDefinitions.kt`, `JarvisViewModel.kt`, `App.kt`
- **สิ่งที่ทำแล้ว**:
  - ✅ เพิ่ม `automation_manage_alerts` และ `automation_manage_schedule` เข้า `TradingToolDefinitions.kt` ให้ Gemini function calling เห็น
  - ✅ แก้ `App.kt` ส่ง `scheduledTasks`, `alertAiSummary`, `alertVoice` และ callbacks `onCreateAlert` / `onCreateScheduledTask` เข้า `AutomationScreen`
  - ✅ เพิ่ม `createAlert()` ใน `JarvisViewModel.kt` — map operator string (`>`, `<`, `>=`, `<=`, `==`, `contains`) → `ConditionOperator` → `automationManager.registerJob()`
  - ✅ เพิ่ม `createScheduledTask()` ใน `JarvisViewModel.kt` — parse ISO datetime → epoch millis → `automationManager.registerScheduledTask()`
  - ✅ UI `AutomationScreen` รองรับ dialog สร้าง Alert + Scheduled Task โดยตรง (สร้างไว้แล้วตั้งแต่ก่อนหน้า)
  - ✅ flow ครบวงจร: AI เขียนเงื่อนไข → `AutomationManager` รันเบื้องหลัง → ตรงเงื่อนไข → ปลุก AI (`JarvisAutomationService`) → แจ้งเตือน user (msgbox + TTS ตั้งค่าได้)
- **ตัวอย่าง use case ที่ใช้งานได้**: "ติดตามราคาทองคำ แจ้งเตือนเมื่อ 4100$" — AI เรียก `automation_manage_alerts` → app ลงทะเบียน alert → background loop เช็คราคาทุก N นาที → ถึงเงื่อนไข → AI สรุปบริบท → แจ้งเตือน + เสียงพูด (ถ้าเปิด)
- **ทดสอบเครื่องจริง**: ✅ Build ผ่าน (`compileDebugKotlin` สำเร็จ 2026-08-03) — `JarvisAutomationService` ถูก start จาก `MainActivity` แล้ว
- **บันทึก**: ดู [[log]] entry "Scheduled Tasks / Automation" 2026-08-03
- **ขอบเขต**: `automation/AutomationManager.kt`, `AutomationEvaluator.kt`, `AutomationModels.kt`, `ui/screen/AutomationScreen.kt`
- **ประเด็นที่ต้องดู**:
  - [ ] flow "AI เขียนเงื่อนไข → app รันเบื้องหลัง → ตรงเงื่อนไข → ปลุก AI ตรวจ → แจ้งเตือน user" ครบวงจรหรือยัง
  - [ ] ตัวอย่าง use case: "ติดตามราคาทองคำ แจ้งเตือนเมื่อ 4100$"
  - [ ] ช่องทางแจ้งเตือน: msgbox / เสียงพูด (ตั้งค่าได้)
  - [ ] ทบทวน [[06_Virtual_Price_Alert_Roadmap]] ว่าทำไปถึงไหน
- **ที่มา**: user request 2026-07-30 ตอน 03:06

### 9. File Tools / Security ⬜
- **ขอบเขต**: `tools/file/FileToolDefinitions.kt`, FileToolExecutor (androidMain)
- **ประเด็นที่ต้องดู**:
  - [ ] path-traversal guard (`canMutatePath`) ครอบคลุมทุก mutation หรือไม่
  - [ ] **JSON Injection ที่เคยพบ** (จาก review 2026-07-29) — แก้แล้วหรือยัง
  - [ ] scope สิทธิ์เข้าถึงไฟล์ชัดเจน
- **ที่มา**: [[ai_subsystem_review_2026-07-29]]

### 10. System Tools ⬜
- **ขอบเขต**: `tools/system/SystemToolExecutor.kt`
- **ประเด็นที่ต้องดู**:
  - [ ] แต่ละ system tool ทำงานถูกต้อง (clipboard, notification, device info, ฯลฯ)
  - [ ] permission handling บน Android เวอร์ชันต่างๆ

### 11. Camera / Vision ⬜
- **ขอบเขต**: `camera/*` (CameraProvider + Gemini/OpenAI/Claude providers, CameraAnalysisService, AROverlayEngine, AdaptiveFpsController), `tools/camera/CameraToolExecutor.kt`
- **ประเด็นที่ต้องดู**:
  - [ ] vision provider switch ตาม provider หลักหรือไม่
  - [ ] อ้างอิงเอกสาร [[vision_system]]

### 12. UI / UX ทั่วไป ⬜
- **ขอบเขต**: `ui/screen/*` (ChatInputBar, JarvisTopBar, SettingsDialog, ToolListScreen, TradingChartScreen, TradingTerminalScreen, AutoTradingScreen, SymbolPickerDialog)
- **ประเด็นที่ต้องดู**:
  - [ ] state flow ผ่าน [[JarvisViewModel]] ไม่ leak/block
  - [ ] ความสม่ำเสมอของ settings ทุกหมวด
  - [ ] responsive/edge-to-edge

### 13. Database / Storage ⬜
- **ขอบเขต**: `commonMain/sqldelight/*` (JarvisDatabase.sq + migrations), `data/GeminiService.kt` ส่วน persistence
- **ประเด็นที่ต้องดู**:
  - [ ] dead queries ที่เหลือ (`updateArchivalAccess`, `getArchivalRecent` — access_count ไม่เคยถูกใช้)
  - [ ] migration path รวม v1→v2→v3 ทดสอบบน install เก่า

### 14. mt5-core-server / Analytics Scripts ⬜
- **ขอบเขต**: `mt5-core-server/` (Script analytics: `npm run analyze`, `advanced_analytics`, ฯลฯ)
- **ประเด็นที่ต้องดู**:
  - [ ] สคริปต์ analytics ทุกตัวรันได้และผลลัพธ์ถูกต้อง
  - [ ] การเชื่อม app ↔ server
  - [ ] สร้าง skill/tool/script เพิ่มหากจำเป็น (ตาม AGENTS.md)

### 15. Diagnostic / Logging ⬜
- **ขอบเขต**: `diagnostic/DiagnosticManager.kt`, logging ทั้งแอป
- **ประเด็นที่ต้องดู**:
  - [ ] log เพียงพอต่อการ debug ปัญหาบนเครื่องจริง (เช่นเคส FRED INTERNAL_ERROR)
  - [ ] sensitive data ไม่หลุดเข้า log

---

## สรุปความคืบหน้า

| หมวด | สถานะ | วันที่ |
|---|---|---|
| 1. Provider | ✅ เสร็จ | 2026-07-30 |
| 2. System Prompt / ตัวตน AI | ✅ เสร็จ | 2026-07-30 |
| 3. Trading Tool | ✅ เสร็จ (+ข่าว 6 แหล่ง เทสผ่าน) | 2026-08-03 |
| 4. Memory | ✅ เสร็จ (รอเทสเครื่องจริง) | 2026-07-30 |
| 5. Tool System / สร้าง Tool | ⬜ รอ | — |
| 6. Orchestrator / Intent | ⬜ รอ | — |
| 7. Live Mode / Voice | ✅ เสร็จ (เทสเครื่องจริงผ่านแล้ว) | 2026-08-03 |
| 8. Scheduled Tasks | ✅ เสร็จ (build verify ผ่าน) | 2026-08-03 |
| 9. File Tools / Security | ⬜ รอ | — |
| 10. System Tools | ⬜ รอ | — |
| 11. Camera / Vision | ⬜ รอ | — |
| 12. UI/UX | ⬜ รอ | — |
| 13. Database / Storage | ⬜ รอ | — |
| 14. mt5-core-server | ⬜ รอ | — |
| 15. Diagnostic / Logging | ⬜ รอ | — |

**Links**: [[log]] | [[Current_Tasks]] | [[ai_subsystem_review_2026-07-29]] | [[catalogue]] | [[schema]]

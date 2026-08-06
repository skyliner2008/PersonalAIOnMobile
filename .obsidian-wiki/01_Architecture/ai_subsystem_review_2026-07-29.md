# AI Subsystem Code Review — 2026-07-29

ขอบเขต: ระบบ AI ในแอปมือถือ (Kotlin Multiplatform, `composeApp/src/commonMain` + `androidMain`)
ไฟล์ที่ตรวจ: `ai/*`, `memory/JarvisMemoryManager`, `tools/ToolExecutor`, `tools/ToolRegistry`, `tools/file/*`, `data/GeminiService` (tool loop), `tools/trading/*`

---

## 1. สถาปัตยกรรมปัจจุบัน (ยืนยันแล้ว)

```
User ──► JarvisViewModel ──► JarvisOrchestrator ─┬─► GeminiService (tool loop ของตัวเอง)
                                                 ├─► External Providers (tool loop ของตัวเอง)
                                                 └─► LiveGeminiService ──► LiveToolBridge (Path A native / Path B regex router)
ToolExecutor (object singleton, mutable) ──► TradingToolExecutor / FileToolExecutor(Android) / CameraToolExecutor / SideEffectDelegate
JarvisMemoryManager: Core / Working / Archival+Embedding / GraphRAG / SleepCycle
```

### ยืนยันปัญหาที่ user ระบุไว้
- **JarvisPlanner = dead code จริง** — สร้างใน `JarvisOrchestrator.kt:29` แต่ไม่มี call site ของ `planner.execute(...)` ที่ใดใน codebase
- **Intent classification ซ้ำ 2 ชั้น** — `IntentClassifier` (weighted scoring) กับ `TradingIntentUtility` (keyword filter) ทำงานแยกกันและถูกเรียกซ้ำใน ≥ 3 จุด (Orchestrator L169/243-246/322-323, GeminiService L400-403)
- **Tool filter logic ซ้ำ 3 จุด** — Orchestrator L248-253, L325-333, GeminiService L405-416 + L531-538 (แต่ละจุดมี semantics ต่างกันเล็กน้อย → แก้ที่เดียวไม่มีผลที่อื่น)
- **Market analysis กระจาย** — `TradingApiService` (661 บรรทัด), `SmcApiService` (1665 บรรทัด), `AdvancedTradingEngine`, `ModernTechnicalApiService` — indicator logic (RSI/BB/EMA) น่าจะ implement ซ้ำหลายที่ ไม่มี shared TA library

---

## 2. Findings — เรียงตามความรุนแรง

### 🔴 Critical — Security

**F1. `file_write` ไม่มี path guard เลย** (`androidMain/.../FileToolExecutor.kt:137-147`)
- `canMutatePath()` มีเฉพาะใน `executeDelete` และ `executeMove` เท่านั้น
- `executeWrite` เขียนได้ทุก path ที่ app มีสิทธิ์ (รวมถึง path นอก allowed roots, เช่น overwrite config ของแอปอื่นใน shared storage)
- `executeRead` (L128-135) ก็ไม่มี guard — LLM อ่านไฟล์อะไรก็ได้ในเครื่อง
- แนวทางแก้: บังคับ `isPathInAllowedRoots()` กับทุก op (read/write/list/analyze ด้วย) และเพิ่ม allowlist นามสกุลไฟล์สำหรับ write

**F2. Tool args parse แบบเงียบๆ ล้มเหลว** (`JarvisOrchestrator.kt:309-313`)
```kotlin
val argsMap = try {
    Json.decodeFromString<Map<String, String>>(toolCall.arguments)
} catch (_: Exception) { emptyMap() }  // ← malformed/nested JSON → args หายหมดโดยไม่มี warning
```
- Tool ถูก execute ด้วย args ว่าง → เช่น `mt5_place_order` อาจทำงานผิดพลาดเงียบๆ
- `GeminiService.extractFunctionCallsFromParts` (L363-371) parse ดีกว่า (รองรับ JsonArray/JsonPrimitive) → 2 path มี robustness ไม่เท่ากัน
- แนวทางแก้: ใช้ parser ตัวเดียวกัน (ย้ายของ GeminiService มาเป็น shared `ToolArgParser`) และเมื่อ parse ไม่ได้ให้คืน error กลับเข้า tool loop แทน emptyMap

### 🟠 High — Correctness

**F3. `IntentClassifier` substring matching ผิดพลาด**
- `contains("import")` match "important", `contains("error")` match "terror", `contains("trade")` match "trademark" → intent เพี้ยน
- tie-break: `maxByOrNull` คืน enum ตัวแรก (CODE ชนะ RESEARCH เสมอเมื่อคะแนนเท่ากัน) — deterministic แต่ arbitrary
- แนวทางแก้: ใช้ word-boundary regex (`\b`) สำหรับภาษาอังกฤษ + เพิ่ม weight ตาม specificity ของ keyword

**F4. `LiveToolBridge` Path B parse JSON ด้วย regex** (`LiveToolBridge.kt:171-179`)
- `"args"\s*:\s*\{([^}]*)\}` แตกเมื่อ args มี nested object
- args regex รับเฉพาะ string value → number/boolean/array หาย
- แนวทางแก้: parse ด้วย `kotlinx.serialization` จริง (Json.parseToJsonElement) แล้วดึง field

**F5. `recall_memory` 2 ระบบไม่ตรงกัน**
- `ToolExecutor.executeRecallMemory` (L218-230): substring match ธรรมดาใน memoryContext
- `SideEffectDelegate.onRecallMemory` (Orchestrator L420-429): embedding semantic search — **แต่ไม่มีใครเรียก** เพราะ ToolExecutor ไม่ได้ delegate recall ไปหา
- ผล: tool path ไม่เคยใช้ embedding search เลย
- แนวทางแก้: route `recall_memory` ผ่าน `_sideEffectDelegate?.onRecallMemory()` ใน ToolExecutor

**F6. Tool loop 3 ตัว ไม่เท่ากัน**
| | Gemini (GeminiService) | External (Orchestrator) | Live (LiveToolBridge) |
|---|---|---|---|
| maxRounds | 10 | 5 | 1 (fire-and-forget) |
| pre-tool text discard | ✅ | ✅ | n/a |
| tool result truncation | ❌ | ✅ 8000 chars | ❌ |
| args parser | hardened | fragile | regex |
| temperature decay | ✅ 0.7→0.4 | ❌ | n/a |

### 🟡 Medium — Design

**F7. `ToolExecutor` เป็น mutable singleton object**
- `init()` ถูกเรียกจาก `JarvisOrchestrator.init` — ถ้าสร้าง Orchestrator หลายตัว (เช่น re-login) จะ race/replace executor กลาง
- `ToolRegistry` ก็เป็น object ที่มี `_customTools`/`_skills` mutable map ไม่มี synchronization
- แนวทางแก้: ทำเป็น class แล้ว inject ผ่าน Orchestrator; custom tool map ใช้ `ConcurrentHashMap` หรือ Mutex

**F8. Memory layer ที่มี overhead แฝง**
- `searchRelevantFacts`: โหลด archival facts **ทั้งหมด** + cosine ใน Kotlin = O(N) ทุก query (L237) — ยังโอเคที่ขนาดเล็ก แต่ต้องมี paging/ANN เมื่อ facts หลักพัน
- dead code: loop L252-255 (`forEach` ว่าง พร้อม comment "(In a real app, we'd find the ID)")
- `extractName` regex อาจ false positive ("ผม ว่า" → name="ว่า") — ควรมี blacklist คำสามัญ
- SleepCycle ลบ messages หลัง consolidate (L619-623) — ถ้า LLM ตอบ JSON เพี้ยนและ decode fail → catch แล้วไม่ลบ (ดี) แต่ถ้า decode ผ่านแต่ content หลอน → ข้อมูลดิบหายถาวร ควรเก็บ transcript เป็น archive file ก่อนลบ

**F9. JarvisPlanner** — ลบทิ้ง หรือ wire จริง
- ตัวเลือก A: ลบ class + field `planner` ออก (ลด dead weight)
- ตัวเลือก B: wire เข้า `chatWithHistory` แทนการเรียก `generateResponseWithTools` ตรงๆ — แต่ต้องทำให้ chain prompts รองรับ tool calling ด้วย (ตอนนี้ planner ใช้ `generateResponseFlow` ธรรมดา ไม่มี tools)

**F10. Trading tool filter ควรเป็น module เดียว**
- สร้าง `TradingToolPolicy` object: input = user text → output = (allowedToolNames, policyLabel, suppressTvResults)
- ให้ GeminiService, Orchestrator, LiveToolBridge เรียกใช้ร่วมกัน → ลบ logic ซ้ำ 3 จุด และ semantics จะตรงกันเสมอ

### 🔵 Low — Hygiene
- `JarvisOrchestrator.chatWithHistory`: provider routing ผูกกับ `modelName.contains("/")` — fragile, ควรมี explicit `providerId` field แยกจาก model name
- `updateConfig` ไม่ re-create `toolBridge`/registry — apiKey เปลี่ยนแล้ว `ToolExecutor.init` ไม่ถูกเรียกซ้ำ (TradingToolExecutor ยังถือ GeminiService เดิม ซึ่งอัปเดต key แล้วก็โอเค แต่ควร audit)
- ไฟล์หลายไฟล์มี mixed CRLF/LF line endings (`FileToolExecutor.kt`, `SmcApiService.kt`) — ควร normalize
- `IntentClassifier.patterns` กับ `TradingIntentUtility.keywords` ควรรวมเป็น keyword source เดียว

---

## 3. แผนปรับปรุงที่แนะนำ (ตามลำดับความคุ้ม)

> ✅ **สถานะ: implement ครบแล้วเมื่อ 2026-07-29** (ดูรายละเอียดใน `00_System/log.md`)
> คอมไพล์ผ่าน `:composeApp:compileDebugKotlinAndroid`
> ข้อ 6 (ToolExecutor → injectable class) ทำเฉพาะ doc note + thread-safety ของ ToolRegistry —
> full DI refactor เลื่อนไว้เมื่อต้องสร้าง Orchestrator หลาย instance จริง
> ข้อ 10 (paging/ANN สำหรับ archival search) เลื่อนไว้เมื่อจำนวน facts โตเป็นหลักพัน

1. **(Security) ใส่ path guard ให้ file_write/file_read** — งานเล็ก ผลกระทบสูงสุด [F1] ✅
2. **(Robustness) รวม ToolArgParser + คืน error เมื่อ parse fail** [F2] ✅
3. **(Memory) route recall_memory ผ่าน embedding search** [F5] ✅
4. **(Refactor) สร้าง `TradingToolPolicy` รวม filter logic 3 จุด** [F10] ✅
5. **(Refactor) ตัดสินใจ JarvisPlanner: ลบ หรือ wire** [F9] ✅ (เลือกลบ)
6. **(Refactor) ToolExecutor/ToolRegistry จาก object → injectable class** [F7] 🟡 (ทำ thread-safety แล้ว, DI เลื่อนไว้)
7. **(Accuracy) IntentClassifier word-boundary + weighting** [F3] ✅
8. **(Long-term) รวม TA indicator library กลาง (RSI/EMA/BB/ATR) ให้ TradingApiService/SmcApiService/AdvancedTradingEngine ใช้ร่วมกัน** ✅ (สร้าง `TaIndicators` แล้ว — เหลือ migrate จุดอื่นเมื่อมีโอกาส)
9. **(Long-term) ทำ tool loop กลางตัวเดียว (ToolCallingLoop) ที่ทั้ง 3 provider path ใช้ร่วมกัน** [F6] 🟡 (รวม policy/parser แล้ว; loop ยังแยก 3 ตัว)
10. **(Long-term) archival search: เพิ่ม paging + เก็บ normalized vector ล่วงหน้า** [F8] ⏳ (เลื่อนไว้)

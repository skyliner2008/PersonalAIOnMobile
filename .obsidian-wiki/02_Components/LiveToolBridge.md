# 🌉 LiveToolBridge

`LiveToolBridge.kt` ทำหน้าที่เป็น "ตัวจัดคิวเครื่องมือ" (Tool Orchestrator) สำหรับโหมด Live (Multimodal) โดยเฉพาะ

## 🎯 หน้าที่หลัก
1. **Tool Redirection**: แยกการทำงานระหว่าง Path A (Native) และ Path B (Bridge)
2. **Context Injection**: เชื่อมโยงบริบทจากคำพูดล่าสุดของผู้ใช้เข้ากับเครื่องมือ
3. **UI Hooking**: ควบคุมสถานะกล้อง (`vision_activate/deactivate`) และการเปลี่ยนเสียง

## ⚙️ การประมวลผล (Processing Paths)
- **Path A (Native)**: ใช้ Gemini Function Calling ดั้งเดิม (เช่น สั่งเปิดกล้อง)
  - แต่ละ call รันใน job ของตัวเอง (tool ช้าไม่บล็อก call อื่น) และถูกยกเลิกได้จาก `toolCallCancellation`
  - ส่งผลผ่าน `respond(event, text, deliverIfStale)` — ถ้า session เปลี่ยนระหว่างรัน tool จะส่งผลเป็น realtime text เข้า session ใหม่แทน (ผลไม่หายเงียบ)
- **Path B (Bridge)**: ใช้ Model Text-to-Intent (เช่น "ช่วยสรุปไฟล์นี้หน่อย")

## 🛡️ Guards (`LiveIntentMatchers`)
- Keyword matchers ย้ายไป `ai/LiveIntentMatchers.kt` (มี unit test)
- Redirect guard ทุกตัวผ่าน `canRedirectMisroutedTool(tool, prompt)` = tool อยู่ในกลุ่มที่ model มักเรียกผิด **และคำขอไม่ใช่คำถามเทรด** (กันเคส "ราคาทองจะไปที่ 2400" เปิด Google Maps)
- Navigation ต้องมีคำกริยาเดินทางชัดเจน (ไม่ใช้ "ไปที่" เดี่ยวๆ)
- Trading AI Profile: TF เริ่มต้น M15/H1/H4 แต่ **อนุญาต D1/W1 เมื่อผู้ใช้ระบุเอง**; counter ต่อ turn อยู่ใต้ mutex
- guard ทุกตัวใน `handleNativeToolCall` ตัดสินจาก **คำถามของ call นั้น** (`CallOrigin.question` ← `liveService.currentQuestion` ตอนเรียก)
  ไม่ใช่ประโยคล่าสุดของผู้ใช้ — คำถามค้างที่แอปถามโมเดลซ้ำ ("วิเคราะห์ BTC 5 มิติ") หลังผู้ใช้ถาม "ราคาทองคำ…" เคยถูกตัดสินเป็นคำถามราคา
  → profile guard บล็อก deep analysis → รายงานว่า "คำถามนี้เจาะจงเกินไป ใช้ตัวอื่นแทน" (logcat 2026-10-02 11:42:46, 12:57:44)
- `device_notification_reply` ข้อความเดิมภายใน 20 วินาทีถูกบล็อก (กันส่งซ้ำ)

- **เทิร์นทักทายเปิดเซสชัน**: tool call ก่อนผู้ใช้พูดอะไรใน session (`liveService.inGreetingTurn`) ไม่รัน ตอบ
  `LiveProtocol.GREETING_TURN_TOOL_NOTE` — เดิมโมเดลดึงอากาศ/BTC จากประวัติเองแล้วเปิดหน้าเต็มจอ Drive (logcat 2026-10-01 16:51)

- **ผลทีละ call ให้โหมดอวตาร**: หลังรัน tool (ที่ไม่ใช่ UI) ส่ง `AvatarLiveFeed.result(tool, AvatarToolData.of(call, tool), text)` —
  ข้อมูลฉากอยู่ใน `LiveToolCallContext` ของ call นั้น · ส่งต่อ `liveService.speechMarks` → `AvatarLiveFeed.speech` (แทน `lastToolResult` เดิม)
  · `later = true` เมื่อ call ถูกส่งให้ลูกน้อง (`delegatedCallIds`) หรือถูก server ยกเลิก — ผลนั้น AI เล่าทีหลังจากคิว · ส่งต่อ
  `liveService.reportsSent` → `AvatarLiveFeed.report` (ฉากขึ้นตอน AI เริ่มเล่า ไม่ใช่ตอนพูดรับงาน) · `liveService.resultsDeferred`
  → `AvatarLiveFeed.deferred` · `speechMarks` ส่งต่อเป็น `AvatarLiveFeed.speech(text, startMs, msPerUnit)`

## 🗣️ ข้อความกำกับเสียง (`[VOICE RULE - …]` ต่อท้ายผล tool)
- **ห้ามมีแค่ "ตอบรับสั้นๆ น่ารัก"** ในผลของ tool ที่โมเดลเรียกประกอบ — โมเดลจะหยิบประโยคทักทายเปิดเซสชันมาพูดแทนผลจริง
- `device_avatar_emotion` (ตั้งสีหน้า) ใช้ `avatarEmotionVoiceRule` ที่เดียว และต้อง **เป็นกลาง ไม่สั่งให้พูดอะไร** — โมเดลเรียกได้ทั้งก่อนพูด
  (คู่กับ tool ข้อมูล) และหลังพูดจบ: "ตอบรับสั้นๆ น่ารัก" → พูดคำทักทายแทนผลราคา (16:24) · "ถ้ามีผล tool อื่นให้ตอบเรื่องนั้น" → เปลี่ยนหน้า
  หลังรายงานอากาศแล้วตอบอากาศซ้ำ (18:19) · ตอนนี้: ท่าทางประกอบ ไม่ต้องพูดถึง, ผู้ใช้ขอเองค่อยตอบรับ 1 ประโยค, ห้ามพูดซ้ำ/เรียกซ้ำ/ถามต่อ/ทักทาย
- **เรียก tool เดิมซ้ำในเทิร์นเดียว** (`DuplicateCallGuard`): ยังไม่พูดหลังได้ผล → `repeatNote` (ส่งผลเดิม "ให้พูดตอบจากผลนี้ทันที");
  พูดไปแล้ว (`liveService.lastModelSpeechAtMs > resultAtMs`) → `alreadyReportedNote` ("รายงานไปแล้ว ห้ามพูดซ้ำ จบเทิร์นได้เลย")
- `[VOICE RULE - PET WEATHER]` / `PET LOCATION` ขึ้นต้นด้วย "เริ่มที่เนื้อหาเลย (ห้ามทักทาย)"
- รายชื่อ tool ที่ผลคือการเปลี่ยน UI/โหมด (`uiOnlyTools`) ใช้ `LiveProtocol.UI_ONLY_TOOLS` ร่วมกับคิวรายงานของ [[LiveGeminiService]]
- ต้นทางของ call (`CallOrigin`) ใช้ `liveService.currentQuestion` — งานที่ทำให้คำถามค้างที่แอปถามซ้ำ ติดป้ายคำถามนั้น ไม่ใช่ประโยคล่าสุด

## 👁️ การจัดการสายตา (Vision Logic)
มีการป้องกันความผิดพลาด (Anti-Hallucination) โดยการบังคับฉีด Prompt ให้ AI รอสังเกตการณ์ 1-2 วินาทีหลังเปิดกล้อง

---
**Links**: [[overview]] | [[LiveGeminiService]] | [[Changelog_2026-09-16_Live_Voice_Review_Fixes]] | [[index]]

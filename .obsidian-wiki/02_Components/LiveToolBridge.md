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
- **งานที่กำลังทำ ตาม call id** (2026-10-02): `jobs` (call id → `AvatarLiveFeed.Job`: tool, args, คำถามของ call, ส่งลูกน้องไหม,
  ถูก server ยกเลิกไหม) เพิ่มตอนเริ่มรันจริง (หลัง guard) ลบใน `finally` ของทั้งสองทาง · `toolCallCancellation` → ติด `cancelled` ·
  ส่ง `AvatarLiveFeed.jobs` ทุกครั้งที่เปลี่ยน + ภาพคิวรายงาน `liveService.reportSnapshot()` ทุก 0.25 วิ และทันทีตอนงานจบ (ก่อนลบงาน
  — ไม่งั้นเม็ดงานบนจอหุ่นหายแวบ) · เดิมเป็นช่องเดียว "tool ล่าสุด" (`activeToolName`) → call เปลี่ยนหน้าที่มาพร้อมกันทับงาน 5 มิติ
  การ์ดทำงานไม่ขึ้นเลย (เครื่องจำลอง log 15:26) · `stopCollecting()` หยุดตามดู session (ใช้ในเครื่องจำลอง)
- **ข้อค้างที่แอปถามซ้ำไม่ส่งลูกน้อง**: tool ที่ปกติส่งลูกน้อง แต่ถูกเรียกในเทิร์นที่โมเดลตอบข้อค้าง (`liveService.answeringReport`:
  แอปส่ง `[PENDING QUESTION]` แล้วผู้ใช้ยังไม่ได้พูด) → รันในเทิร์นนั้น (log `🧾`) — ไม่มีใครรอพูด · เดิมได้ "รับทราบ…" แล้วรออีกรอบ
- **หัวหน้าตอบงานเร็วเอง** (2026-10-03, ผู้ใช้อนุมัติ): tool ของลูกน้อง (`LiveIntentMatchers.isDelegatedTool`) รันทันทีเหมือน call ปกติ
  แล้ว `handOverIfSlow` รอ `QUICK_ANSWER_MS` (1.5 วิ): เสร็จทัน → ผลเป็นคำตอบในเทิร์นนั้น (log `⚡ … หัวหน้าตอบเอง`) · ไม่ทัน →
  ส่งต่อลูกน้อง: ตอบ `DELEGATED:` + งานย้ายจากการ์ดทำงานไปรายการงาน (`jobHandedOver`) ผลเข้าคิวรายงาน (log `👷 ส่งต่อ … ยังไม่เสร็จใน
  1500 ms`) · ตัดสินครั้งเดียวใต้ mutex (`answerNow`) — ผลที่เสร็จพอดีกับเส้น 1.5 วิไม่ถูกตอบสองครั้ง · ระหว่างรอ server ยกเลิกได้
  (ผู้ใช้ถามข้อต่อไป) → ไม่ตอบ id นั้น ผลเข้าคิวเหมือน call ที่ถูกยกเลิก · error ของงานลูกน้อง → "ดึงข้อมูลไม่สำเร็จ" (ตอบหรือเข้าคิว) ·
  เดิมตอบรับทันทีทุกงาน: ปฏิทิน (0.3 วิ) 5 มิติ (1.2 วิ) ก็ต้องฟัง "รับทราบ" แล้วรอรายงานอีก 4–6 วิ · คำตอบที่ผู้ใช้พูดทับก่อนได้ยิน
  3 วิ → ผลเข้าคิวเล่าใหม่ (ดู [[LiveGeminiService]]) · เครื่องจำลอง 9 สถานการณ์ (`AvatarMultiTaskSimTest`: ถามเว้นจังหวะไม่มี "รับทราบ"
  เลย, งานช้า 4 วิส่งต่อที่ 1.5 วิ, คำตอบเร็วถูกพูดทับ 1.5 วิแล้วเล่าใหม่)
- **หุ่นบอกเมื่อ server ไม่ได้ยิน** (2026-10-03): `startCollecting` ฟัง `liveService.deafUtterances` (ครั้งที่ 1–2 → "ไม่ได้ยินเลยฮับ
  พูดอีกทีได้ไหม" · ครั้งที่ 4 ขึ้นไป → `DEAF_STILL_NOTICE`) และ `deafReconnects` (ครั้งที่ 3: เปิด session ใหม่ / เปิดแล้วยังไม่ได้ยิน)
  → `AvatarLiveFeed.notice` → บอลลูนของหุ่น
  (ดู [[LiveGeminiService]] "session ที่ไม่ได้ยินเสียง")

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

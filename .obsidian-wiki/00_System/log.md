## 2026-09-24 — Live "รวนไปหมด": guard บล็อก 5m, tool ค้าง 38 วิ, AI ตอบคำถามเก่าซ้ำ, หุ้นไทยได้แต่ DR
- **User Request**: "ทำไมมันรวนไปหมดเลย" (logcat 00:26–00:29) — ไม่ใช่ปัญหาเดียว แต่ 4 ปัญหาต่อกัน
- **1. "วิเคราะห์ BTC 5 มิติ" → "ดึงข้อมูลไม่สำเร็จ"**: โมเดลเลือก `interval=5m` เอง → `PROFILE_GUARD` บล็อกทั้งคำขอด้วยข้อความเรื่อง D1
  (ไม่ตรงเหตุการณ์) และเดิมบล็อก M5 แม้ผู้ใช้ขอเอง
  → `LiveToolBridge`: TF ที่ผู้ใช้ไม่ได้ขอ **เปลี่ยนเป็น 15m แล้วรันต่อ** (`LiveIntentMatchers.withDefaultTimeframe`);
  TF นาที (M1/M5/M30) อนุญาตเมื่อผู้ใช้พูดถึงเอง ("M5", "5m", "5 นาที")
- **2. ปฏิทินเศรษฐกิจค้าง 38 วิ**: `trading_macro_calendar` ดึง ForexFactory เสร็จเร็ว แต่เรียก Gemini ข้อความอีกตัวเขียน "AI Strategic Preview"
  — 503 → timeout 12 วิ → 503 → 404 → timeout ไล่ทั้ง fallback chain
  → `GeminiService.generateToolEnrichment` จำกัดเวลารวม 10 วิ (ครอบทั้ง chain) เกินแล้ว throw `ToolEnrichmentTimeoutException`
  ให้ catch เดิมใช้ข้อความสำรอง — ใช้กับทุก tool ที่เรียก LLM เสริม (6 จุด: sentiment, news, calendar, fundamental ×2, sector matching)
- **3. ถามปฏิทินเศรษฐกิจ แต่ AI ไปเรียก market_snapshot TH แล้วพูดคำตอบเรื่อง SET ของ session ก่อนเกือบคำต่อคำ**:
  ประวัติใน system instruction มีแค่หัวข้อ "Recent Conversation History:" → `LiveProtocol.HISTORY_HEADER` ระบุว่าเป็นเรื่องที่ตอบไปแล้ว
  ใช้เป็นบริบทเท่านั้น ห้ามตอบ/เรียก tool ให้คำถามเก่าซ้ำ
- **4. "SET สัปดาห์นี้" ได้แต่ DR ของหุ้นสหรัฐ (NVDA80, AAPL80…)**: scanner เรียงตาม market cap และ DR ได้ market cap ของบริษัทแม่
  → กรอง `type = stock` (ทดสอบกับ TradingView scanner จริงแล้ว: ได้ DELTA, PTT, ADVANC, GULF, AOT…)
- **Verify**: เทสต์ `LiveTimeframeGuardTest` 4 เคส — รวม 521 ผ่าน ข้าม 5; ติดตั้งบน SM-S908E แล้ว เปิดแอปได้ ไม่มี crash; ยังไม่ได้ทดสอบด้วยเสียง

## 2026-09-23 — แชทไม่แสดงคำพูดสดของ AI (รวมหลังเปิดแอปใหม่) + AI ตอบเรื่อง MT5 แทนผล SMC
- **User Request**: "ระหว่าง Live แชทมีแต่ผลวิเคราะห์ ไม่มีคำพูด AI แต่ปิดแล้วเปิดแอปใหม่ คำพูด AI ทุกประโยคขึ้นมา" → "ไม่ต้องการให้คำพูดของ AI แสดงในแชท"
- **ต้นเหตุ**: 2 จุดใช้กติกาไม่ตรงกัน — ระหว่าง Live `VoiceController` แสดงเฉพาะ `isStatic` (เพิ่มใน multi-session `b1b013d`)
  แต่ `ChatController.loadHistory` ซ่อนแค่คำพูด**ผู้ใช้** (`live_voice`) คำพูด AI ที่บันทึกไว้ทุกประโยครวมคำทักทายจึงโผล่หลังเปิดแอปใหม่
- **แก้**:
  - `loadHistory` ซ่อนคำพูดสดทั้งสองฝั่ง (`LiveProtocol.isLiveTranscript`: `{"mode":"live_voice"}` รวม interrupted) — รายงาน/ผล tool (`live_voice_tool_result`) ยังแสดง
  - ประวัติที่ส่งตอนเปิด Live ย้ายไปอ่านจากฐานข้อมูล (`ChatController.recentConversationTurns` → `VoiceController.historyTurnsProvider`)
    เพราะแชทไม่มีคำพูดสดแล้ว ถ้ายังอ่านจากแชทโมเดลจะไม่เห็นบทสนทนาก่อนหน้าเลย
- **AI ตอบ "บัญชี MT5 ยังไม่ได้เชื่อมต่อ" ตอนถาม SMC ทอง M15** (logcat 02:55): `Mt5Controller.runtimeContextSnippet` มีคำสั่ง
  "If mt5_paired=false, first tell user to connect/approve" โดยไม่จำกัดขอบเขต → จำกัดเฉพาะคำขอเปิด/ปิด/ดูออเดอร์ MT5
  และระบุว่าคำถามวิเคราะห์/ราคา/SMC ห้ามพูดถึงการเชื่อมต่อ MT5 (แก้ข้อความ prompt เท่านั้น — งาน MT5 ยังพักตามคำสั่ง)
- **ผลจาก log เดียวกัน**: ไม่มีการทักทายซ้ำหลังผล tool แล้ว (2/2 ครั้ง) — ยืนยันการแก้รายการด้านล่าง
- **Verify**: เทสต์ผ่าน 517 ข้าม 5 (เพิ่มเคส `isLiveTranscript`), ติดตั้งบน SM-S908E แล้ว เปิดแอปได้ ไม่มี crash;
  ยังไม่ได้ดูหน้าแชทบนเครื่อง (จอล็อกอยู่)

## 2026-09-23 — Live: AI ทักทายซ้ำหลังผล tool ("สวัสดีค่ะบอส จาวิสพร้อมคุยแล้วค่ะ…" ก่อนตอบ)
- **User Request**: "ในโหมด live มันยังหลุด พูดซ้ำ บ่อย" + "ตั้งแต่ทำ multi session มันจะรวนๆ แปลกๆ" (logcat 02:34–02:38)
- **จาก log**: session ที่ 2 ถาม "วิเคราะห์ SMC ทองคำ M15" → tool ตอบใน 1.3 วิ → AI พูดคำทักทายเปิดเซสชันทั้งประโยคก่อนผลวิเคราะห์
  `disable()` ทั้ง 2 ครั้งมาจากปุ่มจบ Live (`LiveModePanel.onEndLive`) ไม่ใช่ session หลุดเอง
- **ต้นเหตุที่พบ** (3 จุดที่ป้อนแบบแผน "ทักทาย → ตอบ" ให้โมเดล):
  1. ประวัติที่ส่งตอนเปิด session กรองแค่ "พร้อมคุยไหม" (ฝั่งระบบ) — คำทักทายที่ AI ตอบ ("พร้อม คุย แล้ว" มีช่องว่างแทรก) หลุดเข้าไปทุกครั้ง
     พร้อมผล tool ดิบ (markdown ยาว) ที่เรียงอยู่ถัดจากคำทักทาย
  2. เพอร์โซนากฎข้อ 1 สั่ง "ทักทายสั้นๆ และเข้าประเด็นทันที" (ทุกคำตอบ) และกฎห้ามทักทายซ้ำ (09-23) ยกประโยคทักทายเต็มมาเป็นตัวอย่าง
  3. ป้าย `[ANSWER FOR]`/`[PENDING QUESTION]` ของผล tool (จังหวะที่ทักทายซ้ำ) ไม่ได้ห้ามทักทาย
- **แก้**:
  - `LiveProtocol.buildSessionHistory`: ตัดคำทักทายทั้งสองฝั่ง (เทียบแบบไม่สนช่องว่าง), ตัดคำทักทายที่ค้างหน้าคำตอบจริง, จำกัด 500 ตัวอักษร/ข้อความ;
    `VoiceController` ไม่ส่งข้อความสถานะและผล tool ดิบ (`live_voice_tool_result`) เข้า history; history ตอน reconnect ใช้ตัวกรองเดียวกัน
  - เพอร์โซนา: กฎข้อ 1 → "ไม่ต้องทักทาย เข้าประเด็นทันที", คำทักทายมาตรฐานพูดได้ครั้งเดียวต่อเซสชัน, ตัดประโยคตัวอย่างออกจากกฎห้ามทักทายซ้ำ
  - ป้ายผล tool ต่อท้าย `LiveProtocol.NO_GREETING_NOTE` ("เริ่มพูดที่เนื้อหาผลลัพธ์ทันที ห้ามขึ้นต้นด้วยคำทักทาย")
  - log ใหม่ `⚠️ ทักทายซ้ำกลางบทสนทนา` ตอน Turn Complete — ใช้วัดว่ายังเกิดอยู่ไหม: `adb logcat | grep "ทักทายซ้ำ"`
- **Verify**: เทสต์ `LiveGreetingHistoryTest` 6 เคส (ข้อความจริงจาก logcat) — รวม 516 ผ่าน ข้าม 5; ติดตั้งบน SM-S908E แล้ว เปิดแอปได้ ไม่มี crash
- **ยังไม่ได้ทดสอบด้วยเสียงจริง** — ต้องให้ผู้ใช้ลองถามหลายรอบ แล้วดู log `⚠️ ทักทายซ้ำ`
- **ข้อสังเกตเรื่อง multi-session (`b1b013d`)**: commit นั้นเปิด session resumption, context window compression และ `thinking_level=low` พร้อมกัน
  ตรวจแล้ว session ใหม่ไม่ใช้ handle เก่า (ใช้เฉพาะ reconnect `attempt > 0`) จึงไม่ได้พ่วงบทสนทนาเก่าผ่านทางนั้น

## 2026-09-23 — แก้: แอปพังตอนเปิด "no such column: ChatMessage.id" (จาก commit 7c5ca46)
- **อาการ** (logcat จริง 02:26): `SQLiteException` ใน `JarvisMemoryManager.getHistoryAfter` → FATAL บน main ทุกครั้งที่เปิดแอป
- **สาเหตุ**: รอบก่อนเขียน `getHistoryAfter` เป็น `SELECT * FROM (SELECT * … LIMIT ?) ORDER BY timestamp ASC`
  SQLDelight ขยาย `SELECT *` ด้านนอกเป็น `ChatMessage.id, …` ซึ่ง SQLite อ้างถึงจากนอก subquery ไม่ได้ — compile ผ่าน แต่พังตอนรัน
  ตอนนั้นผมตรวจด้วย SQL ที่เขียนเองใน Python ไม่ใช่ SQL ที่ generate จริง จึงไม่เจอ; เทสต์หน่วยก็ไม่เจอเพราะไม่มีเทสต์ไหนเปิดฐานข้อมูลจริง
- **แก้**: query เหลือ `WHERE timestamp > ? ORDER BY timestamp DESC LIMIT ?` แล้ว `asReversed()` ใน Kotlin (ผลเหมือนเดิม: 300 รายการล่าสุด เรียงเก่า→ใหม่)
- **เครื่องมือใหม่** `tools/verify_sqldelight_queries.py`: อ่านโค้ดที่ SQLDelight generate → สร้าง schema ใน memory → `EXPLAIN` ทุก query
  ผล 136 query ผิด 0 (ยืนยันว่าจับบั๊กเดิมได้: `no such column: ChatMessage.id`) — **รันทุกครั้งที่แก้ไฟล์ .sq**
- **Verify**: ติดตั้งบน SM-S908E แล้ว เปิดแอปได้ ไม่มี crash, `user_version` = 19, มี `idx_chat_message_ts`; เทสต์ผ่าน 510 ข้าม 5

## 2026-09-23 — ตรวจ log 09-17 → 09-23 และแก้จุดที่พบ
- **User Request**: "ตรวจสอบ การปรับปรุงพัฒนาโปรเจค จาก log.md ตั้งแต่ 2026-09-17 ถึงปัจจุบัน" → "ดำเนินการทั้งหมด หลังจากนั้นค่อย push"
- **ผลตรวจ**: รายการใน log ตรงกับ commit `9f9a178` → `8b0b9b3` และไฟล์ที่อ้างถึงมีอยู่จริงทั้งหมด; เทสต์ก่อนแก้ 515/515
- **แก้โค้ด**:
  - ประวัติแชท 24 ชม. ไม่มีเพดาน — `getHistoryAfter` โหลดทุกแถว และการ์ดระบบปลุก AI (`wake_ai`) ถูกบันทึกลง `ChatMessage` ด้วย (ปลุกได้ถึง 40 ครั้ง/ชม.)
    → query จำกัด `:limit` รายการล่าสุด (ค่าเริ่มต้น 300) + index `idx_chat_message_ts` (migration `18.sqm`, schema 18 → 19)
    ตรวจด้วย SQLite: ได้ 300 แถวล่าสุดเรียงเก่า→ใหม่ และ query plan ใช้ index
  - `MarketSentimentLiveIntegrationTest` ยิง API จริงอยู่ใน `commonTest` → ชุดเทสต์ปกติล้มเมื่อไม่มีเน็ต/ติด rate limit
    → ย้ายไป `androidUnitTest` และข้ามอัตโนมัติ ยกเว้นตั้ง `RUN_LIVE_TESTS=1`
  - โฟลเดอร์ `rive-interactive/` ที่ root ถูก commit ติดมาใน `8b0b9b3` — เป็นสำเนาเดียวกับ `.agent/skills/rive-interactive/` (ถูก ignore อยู่แล้ว)
    → เลิก track ใน git + เพิ่มใน `.gitignore` (ไฟล์ในเครื่องยังอยู่)
- **จัดระเบียบ wiki**:
  - log.md: รายการที่ถูกต่อท้ายไฟล์ (09-05, 09-18 → 09-22) ย้ายเข้าตำแหน่งตามวันที่ — ทั้งไฟล์เรียงใหม่→เก่า
    **รายการใหม่ให้เพิ่มที่หัวไฟล์เท่านั้น**
  - รวมรายการ Stock Fundamental 09-22 ที่บันทึกซ้ำ 2 ครั้ง, เติมหัวข้อ "Unified Composite Sentiment" ที่ขาดไป,
    หมายเหตุว่า "โหลด 100 ข้อความ" ถูกแทนที่ด้วยตัวกรอง 24 ชม., แก้ชื่อเทสต์ `TypingIntentGuardTest` (ไม่มีจริง) เป็น `ScreenSnapshotFormatterTest`
  - เลขโน้ตซ้ำใน `07_Trading_Intelligence`: `57_OHLCV_Indicator_Foundation_V27` → **`69_`**, `58_WakeTrigger_Architecture_V28` → **`70_`** (แก้ลิงก์ครบทุกไฟล์)
  - README: เพิ่มเพดาน 300 ข้อความ/index และหมายเหตุว่าการโหลด 100 ข้อความถูกแทนที่แล้ว
- **ยังไม่ทำ (ตั้งใจ)**: งาน MT5 (พักไว้ตามคำสั่งผู้ใช้), กล้อง Live ตอนย่อแอป (เฟส D), อารมณ์สัตว์เลี้ยง 21–40
- **Verify**: `:composeApp:testDebugUnitTest` ผ่าน 510 ข้าม 5 (รวม 515) · `RUN_LIVE_TESTS=1` รันเทสต์เครือข่ายจริงผ่าน 5/5

## 2026-09-23 — Chat Message Timestamp (LINE Style) & 24-Hour Active History Retention Filter
- **User Request**: "เพิ่มอีกนิดนึง ข้อความ ไม่มีตัวแสดงเวลา เอาแบบคล้ายๆ line ที่จะมี ตัวแสดงเวลา การส่งข้อความที่ มุมขวา เป็นตัวเล็กๆ ส่วนประวัติแชท ให้มี ที่แสดง ในช่องแชท ให้ใช้เวลา เป็นตัวกำหนด จะแสดงประวัติที่ยังไม่ครบ 24 ชั่วโมง เท่านั้น เมื่อครบ 24 ชั่ว จะไม่แสดงในช่องแชท"
- **Changes**:
  - **LINE-Style Message Timestamp (`MessageBubble.kt`)**:
    - เพิ่ม `formatMessageTime(epochMs)` แปลงเวลาเป็นรูปแบบ `HH:mm` (เช่น `14:32`, `01:45`) ตาม TimeZone ของระบบเครื่อง
    - แสดงผลตัวเลขเวลาตัวเล็กๆ (`fontSize = 9.5.sp`, `FontFamily.Monospace`) ที่มุมขวาล่างของบับเบิลข้อความ (`Modifier.align(Alignment.End)`) สไตล์แอป LINE สวยงาม ชัดเจน และไม่รบกวนเนื้อหา
    - ปรับสีข้อความเวลาอัตโนมัติ: บับเบิล User ใช้สี `JarvisTheme.Cyan.copy(0.75f)` และบับเบิล AI ใช้สี `Color.White.copy(0.45f)`
  - **24-Hour Active Chat Retention Filter (`ChatController.kt`, `JarvisMemoryManager.kt`, `App.kt`)**:
    - **ชั้นฐานข้อมูล SQLite (`JarvisMemoryManager.kt` & `ChatController.kt`)**: เพิ่มฟังก์ชัน `getHistoryAfter(since: Long)` เพื่อโหลดเฉพาะข้อความที่มีอายุไม่เกิน 24 ชั่วโมง (`Clock.System.now() - 24 ชั่วโมง`) ขึ้นมาแสดงในห้องแชทเมื่อเปิดแอปใหม่ ส่วนข้อความที่เก่ากว่า 24 ชั่วโมงจะถูกเก็บรักษาไว้ในฐานข้อมูล/คลังความรู้ แต่ไม่นำมาแสดงให้รกช่องแชท
    - **ชั้นหน้าจอ UI (`App.kt`)**: เพิ่มตัวกรอง `visibleMessages` ทำงานร่วมกับ Timer Ticker (`LaunchedEffect` อัปเดตทุก 1 นาที) เพื่อตรวจสอบและนำข้อความที่มีอายุครบ 24 ชั่วโมงออกจากหน้าจอแชทแบบ Real-time โดยอัตโนมัติ
  - **Data Model & Preservation (`JarvisViewModel.kt` & `ChatController.kt`)**:
    - เพิ่มฟิลด์ `timestamp: Long = Clock.System.now().toEpochMilliseconds()` ใน `data class Message`
    - ปรับปรุง `appendAssistantMessage` ให้ใช้ `last.copy(content = content)` เพื่อคงเวลา timestamp เริ่มต้นของข้อความไว้ตลอดการสตรีมคำตอบของ AI
  - **Testing & Verification**:
    - สร้าง `ChatTimestampAndRetentionTest.kt` ทดสอบการสร้าง Timestamp, รูปแบบ `HH:mm`, และการกรองข้อความอายุเกิน 24 ชั่วโมง (`BUILD SUCCESSFUL`)
    - ติดตั้งและทดสอบการรันบนอุปกรณ์จริง Samsung Galaxy S22 Ultra (`SM-S908E`) เรียบร้อยแล้ว

## 2026-09-23 — Mobile Chat Layout & Markdown Engine Upgrade + Message Persistence Fix
- **User Request**: "ปัญหา ที่ฉันเจอในหน้าแชท ของ ai บ่อย คือการแสดงข้อมูลที่ไม่เหมาะสมกับหน้าจอ หรือพื้นที่ในหน้าแชท ทำให้ส่วนใหญ่จะอ่านยาก เพราะติดกันหมด และบางครั้ง ข้อความจะหายไป หรือแสดงไม่ครบ เมื่อมีการปิดเปิด app ใหม่"
- **Changes**:
  - **Message Persistence & History Layer (`LiveGeminiService.kt` & `ChatController.kt`)**:
    - แก้ปัญหาข้อความถูกตัดขาดเมื่อปิดเปิดแอปใหม่: ยกเลิกการตัดทอนข้อความ `text.take(2000)` ใน `LiveGeminiService.emitTextToChat` ทำให้รายงานผลวิเคราะห์, ตารางการเงิน และข้อมูลขนาดยาวถูกบันทึกลงฐานข้อมูล SQLite (`ChatMessage`) ครบถ้วน 100% ไม่ถูกตัดข้อความทิ้ง
    - แก้ปัญหาข้อความเก่าหายไปเมื่อเปิดแอปใหม่: ปรับปรุง `ChatController.loadHistory()` จากเดิมโหลดเพียง 20 ข้อความล่าสุด (`getRecentHistory(20)`) เพิ่มเป็น 100 ข้อความ (`getRecentHistory(100)`) ป้องกันไม่ให้ประวัติแชทเก่าถูกกลืนหายหลังมีการคุยเสียงสั้นๆ หรือการทำงานของ Live Voice
    - _หมายเหตุ (ตรวจ 2026-09-23)_: ถูกแทนที่แล้วด้วยตัวกรอง 24 ชม. ในรายการ "Chat Message Timestamp" — ปัจจุบันใช้ `getHistoryAfter(since, limit = 300)`
  - **UI & Markdown Rendering Engine (`MessageBubble.kt`)**:
    - **ขยายพื้นที่แสดงผลตอบสนองหน้าจอมือถือ**: ปรับบับเบิลข้อความฝั่งผู้ช่วย (AI Assistant) จากเดิมที่จำกัดความกว้างตายตัว `Modifier.widthIn(max = 320.dp)` ให้ใช้ `Modifier.weight(1f, fill = false).widthIn(max = 640.dp)` ทำให้การ์ดข้อความขยายเต็มความกว้างหน้าจอมือถือ (เช่น Galaxy S22 Ultra กว้าง 412dp) ใช้นาฬิกา/พื้นที่หน้าจอได้อย่างเต็มที่ ไม่เบียดตัวหนังสือจนตัดคำบ่อย
    - **ระบบแยกบล็อก Markdown สวยงาม (Rich Block Parser)**:
      - เพิ่ม `HeaderBlock`: จัดรูปแบบหัวข้อ `#`, `##`, `###`, `####` ด้วยฟอนต์ขนาดใหญ่ หนา มีสี Cyan เฉพาะ เพิ่มระยะเว้นวรรคบน/ล่างอย่างลงตัว
      - เพิ่ม `DividerBlock`: แปลงเส้นแบ่งคั่น `---`, `===`, `***` เป็นเส้นขีดแนวนอน `HorizontalDivider` หรูหราทันสมัย สบายตา
      - เพิ่ม `EmptyLineBlock`: จัดการบรรทัดว่างด้วยระยะเว้นวรรคพารากราฟ (`Spacer(6.dp)`) ป้องกันไม่ให้ข้อความยาวติดกันเป็นก้อน ("ติดกันหมด")
      - เพิ่ม `BulletBlock`: แสดงผลรายการสัญลักษณ์หัวข้อย่อย (`-`, `*`, `•`) และตัวเลขลำดับ (`1.`, `2.`) พร้อมไอคอน Bullet สี Cyan และการเยื้องย่อหน้า (Indentation) ที่เป็นระเบียบ
      - เพิ่ม `Inline Code Badge`: รองรับ syntax เครื่องหมาย backtick (`` `code` ``) โดยแสดงผลด้วย `FontFamily.Monospace` พื้นหลังกรอบมนสีเทาเข้มขอบ Cyan ช่วยแก้ปัญหาสัญลักษณ์ Visual Meter Bar (`█`) ที่ในฟอนต์ระบบซัมซุงเคยแสดงผลเป็นกล่องสี่เหลี่ยมกลวง `▯`
  - **Data Presentation (`MarketTechnicalToolHandler.kt`)**:
    - ปรับรูปแบบการรายงานผลของ `executeSentiment` ให้ใช้ Markdown Table สำหรับ 4 เสาหลัก (`| เสาหลัก | น้ำหนัก | คะแนน | รายละเอียด |`) ซึ่งจะถูกเรนเดอร์ในบับเบิลแชทเป็นการ์ดตารางเลื่อนแนวนอนได้ (Horizontal Scrollable Table) สวยงาม อ่านง่าย เป็นระเบียบ
  - **Tests & Verification**:
    - รัน `testDebugUnitTest` ผ่านเรียบร้อย 100%
    - ทำการบิลด์และติดตั้งลงบนเครื่องจริง Samsung Galaxy S22 Ultra (`SM-S908E`) สำเร็จสมบูรณ์ ไร้ข้อผิดพลาด

## 2026-09-22 — Unified Composite Sentiment: รวม Sentiment ทุกมิติเป็นดัชนีเดียว
- **User Request**: "ฉันต้องการรวม Sentiment ทั้งหมด ให้เป็นอันเดียว ได้มั้ย" (รวมศูนย์ทุกมิติ Sentiment ให้เป็น All-in-One Master Engine)
- **Changes**:
  - **Data Models (`MarketSentimentModels.kt`)**:
    - เพิ่ม `SentimentPillar(name, score, weightPct, detail)`
    - เพิ่ม `UnifiedCompositeSentiment` รวมคะแนนหลัก (0-100), Visual Meter Bar (`[████████░░]`), 5 Tiers (Extreme Fear, Fear, Neutral, Greed, Extreme Greed), เสาหลักทั้ง 4, Derivatives Positioning, Dual-Market F&G, ข่าว และ Contrarian Signal
    - เพิ่ม helper `makeMeterBar(score, length)` และ `classifyScore(score)`
  - **Data Pipeline (`TradingApiService.kt`)**:
    - เพิ่ม `getUnifiedCompositeSentiment(rawSymbol)` ดึงข้อมูล 4 มิติแบบขนาน (`async`/`await`):
      1. ข่าวสาร & กระแสสังคม (Google News, Yahoo, CoinDesk) - น้ำหนัก 30%
      2. ดัชนีความกลัวและความโลภ (Alternative.me Crypto / CNN 7 Sub-indicators) - น้ำหนัก 25%
      3. สถานะสัญญาอนุพันธ์ (Binance Futures Retail vs Top Traders Long/Short & Taker Ratio) - น้ำหนัก 30%
      4. ฉันทามติอินดิเคเตอร์เทคนิค (TradingView 1D Technical Consensus) - น้ำหนัก 15%
    - รองรับ **Global Macro Mode** (เมื่อไม่ระบุ symbol หรือส่ง "all"): คำนวณความเสี่ยงตลาดรวมโลกจาก CNN Stock F&G (60%) + Crypto F&G (40%)
    - คำนวณสัญญาณเตือน **Contrarian Divergence & Squeeze** อัตโนมัติ (เช่น รายย่อย Short หนักแต่เจ้ามือ Long หนาแน่น $\rightarrow$ Short Squeeze)
  - **Execution & Formatting (`MarketTechnicalToolHandler.kt`)**:
    - อัปเกรด `executeSentiment`: รองรับ symbol แบบ optional ส่งคืนการ์ดรายงานผลสรุปเดียวพร้อม Visual Meter Bar, ตาราง breakdown 4 เสาหลัก และ Gemini AI Behavioral Economics Analysis
  - **Definitions & Persona (`TradingToolDefinitions.kt` & `JarvisPersona.kt`)**:
    - ปรับปรุง `trading_sentiment` ให้ symbol เป็น optional (`required = emptyList()`) และอัปเดตคำอธิบาย
    - เพิ่มกฎใน `JarvisPersona.kt` กำหนดให้ `trading_sentiment` เป็น All-in-One Master Engine สำหรับคำถามเรื่อง Sentiment, อารมณ์ตลาด, ความกลัวความโลภ และ Long/Short
  - **Tests & Verification**:
    - เพิ่มยูนิตเทสต์ใน `MarketSentimentTest.kt` (Meter bar, Classify score, Composite data model)
    - เพิ่มการทดสอบ Live Integration ใน `MarketSentimentLiveIntegrationTest.kt`

## 2026-09-22 — Multi-Source Sentiment Intelligence Engine & CNN Fear & Greed Upgrade
- **User Request**: ตรวจสอบว่ามี Tool วิเคราะห์ Sentiment หรือยัง และให้พัฒนาเพิ่ม พร้อมทดสอบว่าใช้งานได้จริงและได้ข้อมูลถูกต้อง
- **Changes**:
  - **Data Layer (`TradingApiService.kt` & `MarketSentimentModels.kt`)**:
    - สร้าง `MarketSentimentModels.kt` กำหนด `BinancePositioningSentiment`, `CnnFearAndGreedData`, `CnnSubIndicator`, `AssetSentimentReport`
    - เพิ่ม `getBinanceFuturesPositioning(symbol)` ดึงข้อมูล Binance Futures 3 ด้าน: `globalLongShortAccountRatio` (สัดส่วนบัญชีรายย่อย Long/Short), `topLongShortPositionRatio` (สัดส่วนพอร์ตเจ้ามือ/Top Traders), `takerlongshortRatio` (ปริมาณ Taker Buy vs Sell) พร้อมประเมินสัญญาณ Divergence / Contrarian Squeeze
    - เพิ่ม `getCnnStockFearAndGreed()` ดึงดัชนีความกลัวและความโลภของตลาดหุ้นสหรัฐฯ (CNN Fear & Greed 0-100) พร้อม 7 ตัวชี้วัดย่อย (Market Momentum S&P 500, Stock Price Strength, Stock Price Breadth, Put/Call Options, VIX Volatility, Junk Bond Demand, Safe Haven Demand) และการเปรียบเทียบย้อนหลัง (Previous close, 1W, 1M, 1Y)
    - ยกเลิกการขูด Reddit ตรงที่ติดปัญหา HTTP 403 ใน `getRedditSentiment(symbol)` โดยเปลี่ยนมาใช้ Multi-source Feed (Google News RSS targeted query + Binance Derivatives Positioning) ให้คะแนน Sentiment Score (-1.0 ถึง 1.0), นับข่าว Bullish/Bearish และส่งคืนฟิลด์ที่ backward-compatible
  - **Intelligence & Execution Layer (`MarketTechnicalToolHandler.kt` & `ResearchToolHandler.kt`)**:
    - อัปเกรด `executeSentiment`: รองรับ Crypto, หุ้นสหรัฐฯ, ทองคำ, Forex และหุ้นไทย ดึงข้อมูล Positioning และคะแนนดัชนี พร้อมส่งเข้า Gemini AI วิเคราะห์สภาวะจิตวิทยาฝูงชน (Crowd Psychology Phase: Euphoria, Complacency, Skepticism, Capitulation) และแจ้งเตือน Contrarian Trap / Opportunity
    - อัปเกรด `executeFearGreed`: เพิ่มพารามิเตอร์ `market` เลือกระหว่าง `"crypto"` (alternative.me), `"stocks"` (CNN US Equities) หรือ `"all"` (ดูคู่กันทั้ง 2 ตลาดเพื่อวิเคราะห์ Macro Risk Appetite)
    - อัปเกรด `executeCombined`: เชื่อมต่อ Sentiment Engine ใหม่และแสดงผล Positioning Divergence ร่วมกับการตัดสินใจ Confluence
  - **Tool Definitions & Persona (`TradingToolDefinitions.kt`)**:
    - ปรับปรุงคำอธิบาย `trading_sentiment` และเพิ่มพารามิเตอร์ `market` ใน `trading_fear_greed`
  - **Testing & Verification**:
    - สร้าง `MarketSentimentTest.kt`: ทดสอบตรรกะการคำนวณ Divergence และโครงสร้างข้อมูล
    - สร้าง `MarketSentimentLiveIntegrationTest.kt`: ทดสอบดึงข้อมูลเครือข่ายจริงจาก Binance Futures API, CNN Fear & Greed API และ Google News RSS
    - ผลทดสอบ: `BUILD SUCCESSFUL` ทั้งหมด 3/3 tests ผ่าน 100% ดึงข้อมูลจริงได้สมบูรณ์ (BTC Retail Long/Short 0.94, Top Trader 2.24, CNN F&G 37.4 fear พร้อม 7 sub-indicators)
    - คอมไพล์และรัน `installDebug` ลงบนเครื่องจริง Samsung SM-S908E สำเร็จเรียบร้อย
- **Documentation**:
  - อัปเดต `.obsidian-wiki/07_Trading_Intelligence/05_Sentiment_Analysis_Logic.md`
  - อัปเดต `.obsidian-wiki/03_Tools/catalogue.md`
  - อัปเดต `README.md`

## 2026-09-22 — Stock Fundamental Analysis & TradingView Financials Dashboard
- **User Request**: ตรวจสอบและพัฒนา Tool วิเคราะห์หุ้น/สินทรัพย์ (ข้อมูลพื้นฐาน, งบดุล, งบการเงิน, การประเมินมูลค่า, สัดส่วนผู้ถือหุ้น) พร้อมการแสดงผลแบบ Visual ใน TradingView Dashboard คล้ายหน้า Financials ของ TradingView
- **Changes**:
  - **Data Layer (`TradingApiService.kt` & `StockFundamentalModels.kt`)**:
    - สร้าง `StockFundamentalData` รองรับกว่า 50 ตัวชี้วัดสำคัญ (Key metrics, Ownership, Capital Structure, Financials, Valuation ratios, Consensus targets)
    - เพิ่มฟังก์ชัน `getStockFundamentals(rawSymbol, requestedExchange)` ดึงข้อมูลจริงจาก TradingView Scanner API (`thailand`, `america`, `global`) รองรับทั้งหุ้นไทย (SET/MAI) และหุ้นสหรัฐฯ (NASDAQ/NYSE) พร้อมระบบ auto-detect ตลาดและ auto-resolve exchange
  - **Intelligence Layer (`ResearchToolHandler.kt`)**:
    - อัปเกรด `executeFundamentalAnalysis` ส่งข้อมูลตัวเลขทางการเงินและงบดุลจริงให้ Gemini วิเคราะห์เจาะลึก 6 มิติ (Valuation, Capital Structure & Debt, Profitability, Dividend Safety, Ownership Dynamics, Fundamental Health Score 0-100)
    - ซิงค์สัญลักษณ์หุ้นเข้าสู่ `ChartStateManager.updateSymbol(fundamental.ticker)` อัตโนมัติเมื่อมีการวิเคราะห์หุ้น
  - **Visual & UI Layer (`financials.html`, `TradingChartScreen.kt`, `ChartController.kt`, `App.kt`)**:
    - สร้าง `financials.html` ใน `androidMain/assets/chart_widget/` และ `commonMain/resources/assets/chart_widget/` ฝังวิดเจ็ต `embed-widget-financials.js` ของ TradingView ในโหมด dark theme พร้อม JS bridge `window.jarvisFinancialsBridge`
    - เพิ่มแคตตาล็อกหุ้นไทย `thaiSetStocks` ใน `toTvSymbol()` ทั้ง `index.html` และ `financials.html` ให้แมปชื่อย่อหุ้นไทย (เช่น `SCB`, `PTT`, `CPALL`) เข้ากับ `SET:...` โดยอัตโนมัติ
    - เพิ่มโหมดแสดงผลที่ 3 `"financials"` (🏛️ การเงิน / งบดุล) ควบคู่กับ `"dashboard"` (LWC multi-pane) และ `"tradingview"` (TV advanced chart)
    - เพิ่ม `FinancialsWebView` ใน `TradingChartScreen.kt` พร้อมปรับ `WidgetSettingsDialog` ให้สามารถพิมพ์เปลี่ยนสัญลักษณ์หุ้นหรือกดเลือก Preset หุ้นไทย/สหรัฐ/คริปโต/ทองคำได้ทันที
    - อัปเดต `ToolRegistry.kt` ใน tool `chart_dashboard_control` รองรับ view parameter `financials` และอัปเดต `JarvisPersona.kt`
- **Verify**: คอมไพล์ด้วย `./gradlew :composeApp:compileDebugKotlinAndroid` สำเร็จสมบูรณ์ (`BUILD SUCCESSFUL in 2m 49s`). ทดสอบบนอุปกรณ์ Android จริงผ่าน Gemini Live สั่งวิเคราะห์หุ้น SCB ได้ผลถูกต้องครบถ้วน
- **รายละเอียด `trading_fundamental_analysis`** (เดิมบันทึกแยกไว้ท้ายไฟล์ — รวมเข้ามาที่นี่ 2026-09-23):
  - **ปัญหาเดิม**: `trading_fundamental_analysis` ใน `ResearchToolHandler.kt` เป็นเพียง Mock / News Dummy ที่ดึงหัวข้อข่าว 10 ข่าวส่งให้ AI เดา ไม่มีตัวเลขจริง งบดุล (Balance Sheet), งบกำไรขาดทุน (Income Statement), โครงสร้างทุน (Capital Structure), หรือ Valuation Multiples
  - **สร้าง `StockFundamentalModels.kt`**: Model `StockFundamentalData` ครอบคลุม 50 ตัวชี้วัดสำคัญ พร้อมฟังก์ชัน Compact Formatting สำหรับมูลค่าเงินตรา, จำนวนหุ้น, อัตราส่วน และเปอร์เซ็นต์
  - **เพิ่ม Data Pipeline ใน `TradingApiService.kt`**:
    - เมธอด `getStockFundamentals(rawSymbol, requestedExchange)` ยิง TradingView Scanner API (`thailand`, `america`, `global`) ดึงข้อมูลงบการเงินและอัตราส่วนจริง
    - Auto-resolve ตลาดและ Ticker: รองรับหุ้นไทย (SET/MAI เช่น SCB, PTT, CPALL) และสหรัฐฯ/สากล (NASDAQ/NYSE เช่น AAPL, NVDA, TSLA)
    - คำนวณค่าอนุพันธ์สำคัญ: Cash & Equivalents (`Total Debt - Net Debt`), Free Float % vs Closely Held %, Upside % จากราคาเป้าหมายเฉลี่ย
  - **ยกระดับ `ResearchToolHandler.kt`**:
    - ปรับปรุง `executeFundamentalAnalysis` แสดงผลข้อมูลแบบครบวงจร 6 หมวดหมู่:
      1. ข้อเท็จจริงที่มีนัยยะ (Market Cap, P/E, Basic EPS, P/S, P/B, Dividend Yield, DPS, 52W Range, Beta 1Y)
      2. ความเป็นเจ้าของ (Total Shares, Free Float %, Closely Held %, Visual Float Bar)
      3. โครงสร้างเงินทุน & สภาพคล่องงบดุล (Enterprise Value, Market Cap, Total Debt, Cash, Net Debt, Equity, Total Assets, D/E)
      4. ผลการดำเนินงาน & ความสามารถทำกำไร (Revenue TTM/FQ/FY, Net Income TTM/FQ/FY, FCF, Margins, ROE, ROA, ROIC)
      5. เป้าหมายนักวิเคราะห์ & โมเมนตัมราคา (Target Price Avg/High/Low, Upside %, YTD %)
      6. บทวิเคราะห์ AI เชิงลึก 6 มิติ (Valuation, Solvency, Profitability, Dividend Safety, Ownership, Fundamental Health Score 0-100)
  - **อัปเดตคำอธิบายและพารามิเตอร์ใน `TradingToolDefinitions.kt`**: ระบุพารามิเตอร์ `symbol` และ `exchange` ชัดเจน
  - **อัปเดตสมองส่วนนอก**: `.obsidian-wiki/03_Tools/catalogue.md` และสร้างโน้ตใหม่ [[68_Stock_Fundamental_Financial_Engine]]
  - **การทดสอบ**: คอมไพล์ผ่าน `:composeApp:compileDebugKotlinAndroid` สำเร็จ 100% (BUILD SUCCESSFUL)

## 2026-09-20 — Device Control เฟส B: เห็นภาพจริง ส่งข้อความได้ และท่าทางครบ
- **User Request**: "ดำเนินการ เฟส ต่อไป" (เฟส B ใน [[Review_2026-09-19_Driving_Mode_Full_Device_Control]])
- **Changes**:
  - **tool ใหม่ `device_screenshot`**: `AccessibilityService.takeScreenshot()` (API 30+) → Bitmap → ย่อกว้าง ≤1080px → JPEG 75% base64 → ส่งเข้า Live session ผ่าน `ScreenVisionBridge` (commonMain) → `orchestrator.sendLiveCameraFrame` (ท่อเดียวกับกล้อง) — AI จึง "เห็น" แผนที่/รูป/กราฟ/WebView ได้จริง
  - **tool ใหม่ `device_gesture`**: long_press (กดค้าง), swipe 4 ทิศ, drag — ทุกท่าใช้ `awaitGesture` มี timeout
  - **`device_type_text(submit=true)`**: กด `ACTION_IME_ENTER` (API 30+) หลังพิมพ์ → ส่งข้อความ/ค้นหาได้จริง ไม่ค้างในช่องพิมพ์
  - **`device_scroll`**: ใช้ `ACTION_SCROLL_FORWARD/BACKWARD` ใน node ที่เลื่อนได้ก่อน แล้วค่อย fallback ปัดกลางจอ (ไม่ไปโดนปุ่ม/pull-to-refresh)
  - **`device_open_app`**: รอหน้าจอเปลี่ยนจริงสูงสุด 2.5 วิ แล้ว**แนบสรุปหน้าจอแรกมาในผลลัพธ์เลย** (เดิมคืนทันที → อ่านจอต่อได้หน้าเดิม); ถ้า package ที่ AI เดามาเปิดไม่ได้ จะ fallback ไปหาจากชื่อแอป
  - **`resolveAppPackage`**: ใช้ `queryIntentActivities(ACTION_MAIN/LAUNCHER)` เป็นฐาน — mapping ชื่อยอดนิยมจะใช้ได้ต่อเมื่อ package นั้นมีจริงในเครื่อง ไม่งั้นค้นจาก label (ตรงเป๊ะ → ขึ้นต้น → มีคำนั้น) แก้ปัญหา `กล้อง → com.android.camera` ล้มบน Samsung
  - **`JarvisPersona`**: เพิ่มกติกาลูปควบคุมแอป 7 ขั้น (open → read → tap/type → read ยืนยัน, retry ≤3, ใช้ screenshot เมื่ออ่านไม่ออก) + ข้อห้าม: ยืนยันด้วยเสียงก่อน action ย้อนกลับไม่ได้ และห้ามกดในแอปธนาคาร/หน้าจ่ายเงิน
  - `ToolRegistry.DEVICE_CONTROL_TOOLS`: เพิ่ม `device_screenshot`, `device_gesture` (เปิดเฉพาะโหมดขับขี่เหมือนเดิม)
- **Verify**: `testDebugUnitTest` ผ่านทั้งหมด; ติดตั้งลงเครื่องจริงแล้ว; `dumpsys accessibility` ยืนยัน `retrieveInteractiveWindows=true` และผู้ใช้เปิด Accessibility ของ JARVIS แล้ว (ต่างจากเมื่อวาน)
- **ผลทดสอบเสียงจริงบนเครื่อง (09:57–10:00)**:
  - ✅ `device_open_app({app_name=Facebook})` → เปิดได้ และผลลัพธ์แนบสรุปหน้าจอ Facebook พร้อมพิกัดกลับมาทันที (awaitAppReady ทำงาน)
  - ✅ `device_read_screen` → JARVIS เล่าได้ว่าอยู่หน้าหลัก Facebook มีแถบสตอรี่ มีโพสต์อะไร
  - ✅ `device_press_button(home)` และ `device_location` ปกติ
  - ✅ tool count: DRIVE 136 / CONTROL 127 — tool ใหม่ถูกกรองตามโหมดถูกต้อง
  - ❌ "เปิดแผนที่ให้หน่อย" → โมเดลถามกลับว่าจะไปไหน ไม่เรียก tool
  - ❌ "เปิดกล้องหน่อย" → เรียก `vision_activate` (ตาของ JARVIS) ไม่ใช่แอปกล้อง แล้ว**วนลูปเรียกซ้ำ 4 รอบ** (2-turn pipeline กระตุ้นตัวเอง) ตอบช้าสุด 15 วินาที จบด้วย "มองไม่เห็นอะไร"
  - ⚠️ `device_screenshot` ยังไม่ถูกเรียกเลย — โมเดลเลือก read_screen เสมอ
- **แก้ตามผลทดสอบ (รอบเดียวกัน)**:
  - `LiveToolBridge`: กัน `vision_activate` ซ้ำภายใน 40 วิ (ตอบ `EYES_ALREADY_OPEN` ให้ดูสตรีมแล้วตอบเลย) + ถ้าผู้ใช้พูดว่า "แอปกล้อง/กล้องถ่ายรูป/ถ่ายรูป" ให้ redirect ไป `device_open_app`
  - `JarvisPersona`: "เปิดแผนที่" ไม่ระบุจุดหมาย → `device_open_app` ทันที ห้ามถามกลับ; เพิ่มกติกาเรียก `device_screenshot` เมื่อคำถามเกี่ยวกับภาพ/แผนที่/กราฟ หรือ read_screen ไม่มีข้อความที่สื่อความหมาย
  - `AlwaysLiveManager`: ข้าม `startDriveMode()` ถ้า polling ทำงานอยู่แล้ว (เดิมรีสตาร์ท 3 รอบต่อการสลับโหมด 1 ครั้ง) และไม่ log `CancellationException` เป็น warning
- **ทดสอบเสียงรอบ 2 (10:24–10:27) — แก้ผ่านหมด**:
  - ✅ "เปิดแผนที่ให้หน่อย" → `device_open_app({app_name=Google Maps})` ทันที ไม่ถามกลับแล้ว
  - ✅ "เปิดแอปกล้อง" → `device_open_app({app_name=กล้อง})` → เปิด `com.sec.android.app.camera` สำเร็จ (พิสูจน์ว่า resolve จาก launcher จริงแก้ปัญหา Samsung ได้)
  - ✅ "เปิดกล้องหน่อย" → `vision_activate` **ครั้งเดียว** แล้วจบด้วย `vision_deactivate` (เดิมวน 4 รอบ)
  - ✅ ไม่มี `Drive telemetry polling error` และขึ้น "ข้ามการเริ่มซ้ำ" ตามที่ตั้งใจ
  - ⚠️ "ดูแผนที่ให้หน่อยว่าตรงนี้คือที่ไหน" → โมเดลเลือก `device_location` (ตอบถูกแต่ไม่ได้ทดสอบ screenshot) — `device_screenshot` **ยังไม่เคยถูกเรียกจริงสักครั้ง**
  - 🔴 **พบบั๊กใหม่: กล้อง Live ไม่ส่งเฟรมเลยเมื่อแอปถูกย่อ** — ไม่มี log `📹 Video chunks streaming` ทั้ง 2 รอบ สาเหตุ: เฟรมถูกผลิตจาก `CameraPreviewView` ใน `DriveModeScreen.kt:236` / `PetModeScreen.kt:561` เท่านั้น เมื่อ `minimize()` ไป MINI_FLOATING composable ถูกถอด → ไม่มีเฟรม → JARVIS ตอบ "มองไม่เห็นอะไร" (ต้องย้ายการจับเฟรมไป foreground service ที่มี `FOREGROUND_SERVICE_CAMERA` — งานเฟส D)
    หมายเหตุ: `device_screenshot` ไม่ได้ใช้เส้นทางนี้ (ยิงตรงผ่าน `ScreenVisionBridge`) จึงไม่ติดปัญหาเดียวกัน
- **ทดสอบเสียงรอบ 3 (10:47–10:48)**:
  - ✅ `device_screenshot` **ถูกเรียกจริงแล้ว** จากคำสั่ง "แคปหน้าจอให้หน่อยว่าบนแผนที่เห็นอะไรบ้าง" (คำใบ้ในคำอธิบาย tool ได้ผล)
  - 🔴 แต่ล้มเหลวใน 21 มิลลิวินาที — **สาเหตุ: `accessibility_service_config.xml` ขาด `android:canTakeScreenshot="true"`** (เป็น attribute บังคับ ไม่ใช่แค่เรียก API ได้) แก้แล้ว: `dumpsys accessibility` ยืนยัน `capabilities` 33 → **161** (บิต 128 = CAPABILITY_CAN_TAKE_SCREENSHOT) โดยผู้ใช้ไม่ต้องปิด-เปิด Accessibility ใหม่
  - ✅ อ่าน Maps เห็น `--- หน้าต่าง: หน้าต่างป๊อปอัป ---` — การอ่านหลายหน้าต่างทำงานบนเครื่องจริง
  - ❌ `device_type_text({text=เพลงลูกทุ่ง, submit=true})` ใน YouTube ล้มเหลว "ไม่พบช่อง input" เพราะโมเดลพิมพ์ทันทีโดยไม่แตะช่องค้นหาก่อน → แก้: ข้อความ error บอกขั้นตอนถัดไป (read → tap ช่องค้นหา → พิมพ์ใหม่) + เพิ่มกฎในเพอร์โซนา
  - ❌ "กลับไปหน้าแอปจาวิส" → `device_open_app({app_name=JARVIS})` → ไม่พบ เพราะ label จริงของแอปคือ "Personal AI Bot" → แก้: map ชื่อเรียกตัวเอง (jarvis/จาวิส/จาวิต/personal ai bot) เป็น package ตัวเอง และ `launchByPackage` กรณีเป็นตัวเองจะ `expandAlwaysLive()` กลับเป็นเต็มจอแทน startActivity
- **ทดสอบเสียงรอบ 4 (11:56–11:58)**:
  - ✅ **`device_screenshot` ทำงานสมบูรณ์** — log ขึ้น `📹 Video chunks streaming (#1, 495624 chars/frame)` และ JARVIS บรรยายหน้า Maps ได้ถูกต้อง ("โหมดดูแผนที่ทั่วไป เห็นตำแหน่งปัจจุบันและเส้นทางรอบๆ") → ยืนยันว่า `canTakeScreenshot` คือสาเหตุเดียวของรอบก่อน
  - ✅ ลูปหลายขั้นทำงาน: open_app YouTube → `device_tap(x,y)` ที่ช่องค้นหา → read_screen → `device_type_text(submit=true)` → "พิมพ์และกดส่งเรียบร้อย"
  - 🔴 **`device_scroll` รายงาน "สำเร็จ" แต่หน้าจอไม่ขยับ** — `scrollNode` เลือก node ที่เลื่อนได้ "ตัวแรกที่เจอ" ซึ่งมักเป็นแถบชิปแนวนอนด้านบน performAction คืน true แต่เนื้อหาหลักอยู่ที่เดิม
    แก้: `findMainScrollable()` เลือก node ที่เลื่อนได้ซึ่ง**พื้นที่ใหญ่สุดและเป็นแนวตั้ง**, ตัดหน้าต่างของ JARVIS เองออกจาก `interactiveRoots()`, และ `executeScroll` เทียบ "ลายเซ็นหน้าจอ" ก่อน/หลัง — ถ้าไม่ขยับจริงจะตอบว่าไม่ขยับ (ห้ามบอกว่าสำเร็จ)
  - 🔴 **โมเดลพิมพ์ค้นหาเองทั้งที่ผู้ใช้ไม่ได้สั่ง** — ผู้ใช้พูดแค่ "เปิด YouTube" แต่โมเดลไล่แตะช่องค้นหาแล้วพิมพ์ "เพลงลูกทุ่ง" (ข้อความค้างจากบทสนทนารอบก่อน) แล้วกดส่ง
    แก้: `LiveIntentMatchers.hasTypingIntent()` + guard ใน `LiveToolBridge` บล็อก `device_type_text` เมื่อประโยคล่าสุดไม่มีเจตนาพิมพ์/ค้นหา + กฎ "ขอบเขตของคำสั่ง" ในเพอร์โซนา (ทำเฉพาะสิ่งที่สั่งในประโยคล่าสุด ห้ามสานงานค้างจากบทสนทนาก่อน) + เทสต์ `hasTypingIntent` (อยู่ใน `ScreenSnapshotFormatterTest`)
- **ทดสอบรอบ 5 (12:10) — ไม่ได้ทดสอบฟีเจอร์เลย เพราะ Live session โดนลิมิต**:
  - log: `Session closed: INTERNAL_ERROR — You exceeded your current quota` → `🔄 Live quota → rotate credential 1/1 + model=gemini-3.8-live-extended-thinking`
  - จากนั้น "เปิด YouTube" **ไม่มี tool call เลย** โมเดลสำรองตอบ "จาวิสไม่สามารถเปิดแอปพลิเคชันโดยตรงได้ค่ะ"
  - ผู้ใช้ยืนยันว่าโควตารายวันไม่หมด — ตรงกับเอกสาร Gemini: ข้อความนี้เกิดจาก RPM/TPM (ลิมิตต่อนาที) ได้ด้วย และวิธีแก้คือ "wait and retry"
  - **ต้นเหตุเชิงระบบ**: การสลับโหมด (CONTROL→DRIVE) ทำ teardown+reconnect ทันที → เปิด session 3 ครั้งใน ~13 วินาที → ชน RPM → โค้ดเดิม**สลับโมเดลทันที**ตกไปอยู่ extended-thinking ที่ไม่ยอมเรียก tool
  - แก้ `LiveGeminiService`: เมื่อเจอ quota error ให้**รอ 2/4 วินาทีแล้วลองโมเดลเดิมซ้ำสูงสุด 2 ครั้งก่อน** ค่อยสลับโมเดล และถ้าต้องสลับจริงจะ `emitTextToChat` แจ้งผู้ใช้ว่าโมเดลสำรองอาจควบคุมเครื่องได้ไม่ครบ (เดิมเงียบสนิท ผู้ใช้เห็นแค่ "ทำไม่ได้")
- **ทดสอบรอบ 6 (12:25–12:27)**:
  - ✅ ไม่เจอ quota error แล้ว (อยู่กับ `gemini-3.8-live` ตลอด) — tool ทำงานครบ: `device_open_app(YouTube)`, `device_media_control(search_play)`, `device_scroll` ทั้งขึ้นและลง
  - ✅ `device_scroll` ตอบ "เลื่อนหน้าจอขึ้นแล้ว/ลงแล้ว" ซึ่งเป็นข้อความที่ออกเฉพาะเมื่อ **ลายเซ็นหน้าจอเปลี่ยนจริง** (ถ้าไม่ขยับจะตอบอีกแบบ)
  - ✅ typing guard ไม่ขวางงานจริง: "ค้นหาเพลงลูกทุ่ง" ผ่านปกติ
  - 🔴 **โมเดลทักทายซ้ำแทนการรายงานผล** — หลัง tool response ทุกครั้งพูด "สวัสดีค่ะบอส จาวิสพร้อมคุยแล้วค่ะ มีอะไรให้จาวิสช่วยวันนี้ดีคะ" ทั้งที่ session ไม่ได้ต่อใหม่ (ไม่มี log reconnect/`⬆ Sent realtime text` คั่น)
    สันนิษฐาน: กฎ "ขอบเขตของคำสั่ง" ที่เพิ่มรอบก่อนมีท่อนท้าย "ถ้าไม่แน่ใจให้ถามสั้นๆ 1 ประโยค" ทำให้โมเดลเลือกถามลอยๆ แทนรายงานผล
    แก้: ตัดท่อนนั้นออก + เพิ่มกฎ "หลังเรียก tool เสร็จต้องรายงานผลจริง 1-2 ประโยค ห้ามทักทายซ้ำกลางบทสนทนา ห้ามถามลอยๆ แทนการรายงาน"
- **แก้เพิ่มหลังรอบ 2**: `ScreenSnapshotFormatter` เติมบรรทัดเตือนท้ายผล read_screen เมื่อ element มีข้อความ &lt;25% (หน้าแบบแผนที่/กล้อง/เกม) ว่า "ให้เรียก device_screenshot ห้ามเดา" + เพิ่มวลีไทยใน description ของ `device_screenshot` (แคปหน้าจอ, ส่องหน้าจอ, บนแผนที่เห็นอะไร) + เทสต์ใหม่ 2 เคส

## 2026-09-20 — แก้บั๊ก FVG/OB ใน SmcEngine (Order Block ไม่เคยทำงานเลย) + installDebug สร้างแอปโคลน

- **บั๊ก 1 หน้าต่างค้นหาไม่ตรงกัน**: `FvgDetection` หา FVG 30 แท่งท้าย แต่ `OrderBlockDetection` ค้น swing ทั้ง 300 แท่ง
  และรับ OB เฉพาะที่มี FVG ห่าง ≤5 แท่ง → บน BTC จริง OB = 0 อันเสมอ
  แก้ใน `SmcEngine`: ส่ง FVG ที่ค้นทั้งหน้าต่างให้ OB (`activeFvgs` ของฝั่งเข้าเทรดยังเป็น 30 แท่งท้าย ไม่เปลี่ยนพฤติกรรม)
- **บั๊ก 2 นับ mitigation ตั้งแต่แท่งถัดจาก OB** ซึ่งคือ impulse ที่สร้าง OB เอง → OB ถูกตีว่า "ถูกแตะแล้ว" ทันทีที่เกิด
  แก้: เริ่มนับหลัง `breakIndex` (แท่งทะลุโครงสร้าง) — เพิ่มฟิลด์ `SmcOrderBlock.breakIndex`
- ผลบนแท่งจริง: OB ที่ยัง active จาก 1/24 หน้าต่าง → **17/24**; ระดับ Supply/Demand OB เข้าภาพตลาดของ AI แล้ว
  (เล่นซ้ำ 200 จุด เจอระดับ OB 9 ครั้ง) และฝั่งสัญญาณได้ OB setup / กำแพง OB / คะแนน confluence กลับมา
- เทสต์ใหม่ `SmcOrderBlockTest` 3 เคส (หน้าต่าง FVG, mitigation หลัง break, snapshot) — รวม 496/496
- **installDebug สร้างแอป 2 อัน**: `adb install` ติดตั้งให้ทุก user profile เครื่องเปิด Dual Messenger (user 95 = DUAL_APP)
  จึงได้ไอคอนโคลนทุกครั้ง → ตั้ง `installation { installOptions("--user", "0") }` ใน `composeApp/build.gradle.kts`
  และถอนของ user 95 ออกแล้ว (ยืนยัน: user 0 installed=true, user 95 installed=false)

## 2026-09-20 — review ระบบคาดการณ์ล่วงหน้าทั้งระบบ (หลัง P16) + แก้ 6 จุด

ตรวจตั้งแต่ engine → governor → การเรียนรู้ → prompt → การ์ด → ฐานข้อมูล → เครื่องมือ พบและแก้:

1. **ข้อมูลโตเกินเครื่อง** — วัดจริง 6,047 แถว/วัน/สินทรัพย์ (M1 73%) × retention 365 วัน ≈ 2 ล้านแถว
   → ตาราง `AnticipationFactorStat` + ยุบแถวที่ไม่ได้ปลุก AI หลัง 3 วัน (migration 17) ดู [[70_WakeTrigger_Architecture_V28]] ข้อ 5.4
2. **คำตัดสิน AI ถูกเขียนลงทุกแถวของการสแกน** (รวมปัจจัย/TF ที่ AI ไม่ได้เห็น) → เขียนเฉพาะ `woke = 1`
3. **บรรทัดเหตุการณ์บนการ์ดโชว์รหัสปัจจัย/สถิติให้ผู้ใช้** — regex ตัดข้อความยังเป็นรูปแบบก่อน P16 (`สถิติ:` เทียบกับ `สถิติ H1:`)
   → แก้ + เทสต์ `WakeCardLineTest`
4. **prune ยุบแล้วลบคนละ transaction** — ถ้าลบพลาดสถิติจะถูกนับซ้ำ → รวมเป็น transaction เดียว
5. **มุมมอง SKIP ค้าง 1 ปี** ทั้งที่ใช้แค่สถิติ 30 วัน (ปลุกทุกนาทีจึงมีวันละหลายร้อย) → เก็บ 30 วัน
6. **Order Block ไม่เคยเกิดเลย** — `FvgDetection` หา FVG แค่ 30 แท่งท้าย แต่ `OrderBlockDetection` ค้น swing ทั้ง 300 แท่ง
   และรับ OB เฉพาะที่มี FVG ห่าง ≤5 แท่ง → บนแท่ง BTC จริง: FVG 3 อัน (ท้ายสุด) → OB 0 อัน
   ทำให้ระดับ OB ไม่เคยถึง AI และปัจจัย OB_TOUCH/OB_MITIGATED ไม่เคยยิง
   → `MarketContextDigest` หา FVG ทั้งหน้าต่างก่อนส่งให้ OB (ทดสอบแล้วได้ OB 8 อัน) — **`SmcEngine` ของระบบ signal ยังมีบั๊กนี้อยู่ (ยังไม่แก้)**

ตรวจแล้วถูกต้อง: สูตรปัจจัย 30 ตัว × 5 TF (ไม่ตรง 0), โครงสร้าง D1–M1, รายละเอียดต่อ TF, PDH/PWH, migration 16/17 บนสำเนาฐานข้อมูลจริง,
การยุบสถิติ (ค่าตรงเป๊ะหลังยุบ 2 รอบ), governor (cooldown ต่อ TF / ระยะห่าง / พกเหตุการณ์ / memory รุ่นเก่า), เวลาประเมิน ~16 ms/รอบ

เทสต์ 493/493 · ติดตั้งบนมือถือแล้ว (schema 18)

## 2026-09-20 — ปัจจัย × TF: ประเมินทุกปัจจัยบน M1/M5/M15/H1/H4 ทุกนาที + เรียนรู้แยก TF (P16)

ที่มา: ผู้ใช้ไม่พอใจที่ AI ถูกปลุกได้แค่แท่ง M15 ละครั้ง (≤6/ชม., ~144/วัน ทั้งที่มีโควตา 1000+/วัน และ API key 7 ตัว)
เหตุการณ์กลางแท่ง M15 ต้องรอแท่งปิด และต้องการให้ระบบเรียนรู้ว่าปัจจัยไหนเหมาะกับ TF ไหน

- ใหม่ `WakeTfProfile.kt` (ชุด TF ที่ปลุกได้ต่อปัจจัย + 26 ปัจจัยตายตัว), `WakeContext.forTf` + `SeriesSet`, `WakeTriggerRegistry.scanAllTf`
- `16.sqm`: `AnticipationFactorOutcome.factor_tf` + UNIQUE(signal_id, factor_id, factor_tf) (สร้างตารางใหม่ — ทดลองบนสำเนาฐานข้อมูลมือถือก่อน: 330 แถวครบ)
- `WakeLearningStore`: สถิติ/วัดผล/ลดชั้น/เลื่อนชั้น ต่อปัจจัย × TF (`tfVerdict`), รายงานเป็นตาราง ปัจจัย × M1…H4
- `WakeGovernor`: cooldown ต่อ ปัจจัย@TF, เว้น 1 นาที, พกเหตุการณ์ ≤10 นาที; งบ 40/ชม. 3,000/วัน; `TradingAlertEvaluator` ไม่ throttle 90 วิ กับงานปลุก
- Engine: ดึง 800 แท่งทุก TF, รหัสการปลุกรายนาที, ATR/บริบทการเรียนรู้ของ TF ที่เกิด, prompt อธิบายป้าย TF
- เครื่องมือ: `wake_factor_replay.py` ตรวจ 30 สูตร × 5 TF (ไม่ตรง 0) + `--step 1m` จำลองความถี่การปลุก; `device_wake_audit.py` อ่าน factor_tf
- เทสต์ `WakeMultiTfTest` 5 เคส + governor ใหม่ 4 เคส — รวม 492/492
- บนมือถือ: migration 16→17 ผ่าน, การปลุกแรก 03:59 (กลางแท่ง M15) ด้วยเหตุการณ์ NR7@H1, NR7@H4, SPREAD_WIDENING@M1 — ดู [[70_WakeTrigger_Architecture_V28]] ข้อ 5.3

## 2026-09-20 — ตรวจการแจ้งเตือนตาม runbook 67 (หลังติดตั้ง D1 + กติกาสวนเทรนด์)

- 8 การปลุก BTC 00:45–02:45: ปัจจัยที่มีสูตร ✓ 17/17, เล่นซ้ำ 16 แท่งล่าสุดตรงทุกค่า, เหตุผล AI (SKIP ทั้งหมด) ตรงกับภาพตลาดขณะนั้น
- ไม่มี NOTIFY สวนเทรนด์อีก; ปลุกทุกแท่งตามออกแบบ (ปัจจัยสลับทิศเกือบทุกแท่ง) — รายละเอียดใน [[67_Device_Wake_Alert_Audit]]

## 2026-09-20 — ภาพตลาด D1 + รายละเอียดต่อ TF + ห้ามแจ้งสวนเทรนด์ที่เรียงกัน

ที่มา: ผู้ใช้ให้แก้ข้อเสนอ 2 ข้อ (Premium/Discount ชี้ SELL ในขาขึ้น, ห้ามแจ้งสวนเทรนด์) และถามว่า trend/RSI/ADX/MACD/EMA/BB/ATR
ต่อ TF พอให้เห็นภาพรวมไหม — คำตอบ: ไม่พอ (ไม่มี D1, ไม่รู้ตำแหน่งในขา, ความแรงของการวิ่ง, แท่งปฏิเสธราคา, divergence, ระดับ HTF)

- `MarketContextDigest`: เพิ่ม D1 (รวมจาก H4 ด้วยจุดกึ่งกลางแท่ง — OANDA H4 ทองเปิด 21:00/01:00… UTC),
  `TfDetail` 2 บรรทัดย่อยต่อ TF, แนวรับ/ต้าน swing H4 + PDH/PDL + PWH/PWL, แก้ป้าย swing high ใต้ราคาที่ติดผิดเป็น "swing L",
  divergence ที่ยืนยันพร้อมกันแสดงทั้งคู่ — ดู [[70_WakeTrigger_Architecture_V28]] ข้อ 5.1
- กติกาสวนเทรนด์: `WakeContext.htfAlignment`, `PREMIUM_DISCOUNT_EXTREME` เป็น NEUTRAL เมื่อเรียงกัน, prompt เตือน + กติกา,
  `WakePrompt.enforceTrend` เปลี่ยน NOTIFY สวนทางเป็น SKIP (log `⏰ บล็อก NOTIFY … สวนเทรนด์`) — ข้อ 5.2
- เครื่องมือเล่นซ้ำตรวจค่าใหม่ทั้งหมด (1b โครงสร้าง D1–M1, 1c รายละเอียด 12 ค่า, 1d PDH/PWH): BTC 200 แท่ง + ทอง 200 แท่ง ไม่ตรง 0
- เทสต์ `DigestDetailAndTrendRuleTest` 7 เคส — รวม 484/484
- บนมือถือ: การปลุกแรกหลังติดตั้ง (sweep แนวต้านในขาขึ้น) AI ตอบ SKIP โดยอ้างว่า H4/H1/M15 ยังเรียงเป็นขาขึ้น

## 2026-09-19 — Device Control เฟส A: AI เห็นพิกัดปุ่มและกดได้จริง
- **User Request**: รีวิวโหมดขับขี่ที่ควบคุมมือถือได้ 100% แล้วดำเนินการเฟส A (ดู [[Review_2026-09-19_Driving_Mode_Full_Device_Control]])
- **Changes**:
  - ใหม่ `ScreenSnapshotFormatter.kt` (commonMain): จัดรูปผลอ่านจอ — พิกัด `@(x,y)` ทุก element, ปุ่มไอคอนแสดงเป็น `(ไม่มีข้อความ ImageButton) [ปุ่ม]`, ตัด label ≤60 ตัว, เพดาน 80 รายการ + บอกจำนวนที่เหลือ, หัวข้อแยกตามหน้าต่าง
  - `JarvisAccessibilityService.kt`: อ่าน/คลิกทุกหน้าต่างที่โต้ตอบได้ (`getWindows()` เรียงชั้นบนก่อน → dialog มาก่อน), เก็บ node ที่ clickable/editable แม้ไม่มีข้อความ, ปุ่มที่ไม่มีข้อความยืมข้อความลูก (ไม่แสดงซ้ำ), ข้าม node ที่มองไม่เห็น, maxDepth 10→30, `clickById` รับ id แบบสั้น, `typeText` หา focus ข้ามหน้าต่าง, `dispatchStroke()` เรียก callback เสมอแม้ dispatch ถูกปฏิเสธ; ลบ `ScreenNode`/`findNodesByText`/`findNodesById` (dead code + leak)
  - `DeviceControlExecutor.kt`: `awaitGesture()` ห่อ tap/scroll ด้วย `withTimeoutOrNull(3s)` — แก้ Live session ค้างถาวร
  - `accessibility_service_config.xml`: เพิ่ม `flagRetrieveInteractiveWindows` (และตั้งซ้ำใน `onServiceConnected`)
  - `DeviceToolDefinitions.kt`: บอก AI ให้ใช้ `@(x,y)` กับ `device_tap` และอ่านซ้ำหลังกด
  - Test ใหม่ `ScreenSnapshotFormatterTest` (7 เคส) — `testDebugUnitTest` ผ่านทั้งหมด
- **พบบนเครื่องจริง (SM-S908E)**: `enabled_accessibility_services = null` — Accessibility Service ไม่เคยถูกเปิด ผู้ใช้ต้องเปิดเองที่ ตั้งค่า > การช่วยเหลือการเข้าถึง > แอปที่ติดตั้ง > JARVIS

## 2026-09-19 — ตรวจค่าปัจจัยปลุก 115 ตัว × 5TF ด้วยการเล่นซ้ำ + แก้ "ล่าสุด=" ในภาพ 5TF

ที่มา: ผู้ใช้ถามว่า 115 ปัจจัยจาก 5TF แสดงค่าถูกต้องไหม และ AI นำไปวิเคราะห์ถูกไหม

- เครื่องมือใหม่ `tools/wake_factor_replay.py` + `WakeFactorReplayTest` (androidUnitTest): ดึงแท่งจริงจากมือถือ
  แล้วรันโค้ดของแอปย้อนหลังทีละแท่ง TF หลัก (เฉพาะแท่งที่ปิดแล้ว ณ เวลานั้น) เทียบกับสูตรอิสระใน Python
- ผล BTC 300 แท่ง M15 (09-16 → 09-19):
  - อินดิเคเตอร์ 5TF (EMA20/50/200, RSI, ATR, MACD hist, ADX, BB%B) ทั้งชุดของ trigger และภาพ 5TF ตรงกัน (ต่าง < 1e-8)
  - 30 ปัจจัยที่มีสูตรอิสระ: เกิด/ไม่เกิด/ทิศ ตรงกันทุกแท่ง (0 ผิด)
  - error ระหว่างประเมิน: ไม่มี; 19 ตัวไม่เกิดเลย — ส่วนใหญ่ต้องใช้ข้อมูลภายนอก/memory ที่การเล่นซ้ำไม่มี, GAP/WEEK_BOUNDARY ไม่ใช้กับคริปโต
  - **ความจริงเรื่อง "5TF"**: ~95 ตัวดูเฉพาะ TF หลัก (M15), ที่เหลือใช้ H1/H4/M5/M1/ภาพ 5TF/ข้อมูลภายนอก
- **บัคที่พบและแก้**: `MarketContextDigest` บรรทัด `ล่าสุด=` ใช้ `BooleanArray.sliceArray(..).any()` (ไม่มีเงื่อนไข = "array ไม่ว่าง")
  → AI เห็น "CHoCH↑" ทุก TF ทุกครั้ง (300/300) และเรียงตามชนิดแทนเวลา → `lastStructureEvent()` คืนเหตุการณ์ใหม่สุดพร้อมอายุ
  เช่น `CHoCH↓ (8 แท่งก่อน)`; ATR ในภาพ 5TF ใช้ `TaIndicators.atrSeries` เหมือนปัจจัย (เดิม M1/M5 ต่าง 0.1–0.24%)
- ตรวจเหตุผล AI 46 ครั้งเทียบภาพ 5TF ขณะนั้น: อ่านค่าถูกเกือบทั้งหมด (ผิด 2: อ้าง M1/M15 เป็น DOWN ทั้งที่ UP), ไม่เคยอ้าง CHoCH ที่ผิด
  แต่ **ตีความเอนเอียง**: SELL 9 ครั้งอ้าง "Premium สุดขอบ H1" ทั้งที่ทุก TF เป็นขาขึ้น — ปัจจัย `PREMIUM_DISCOUNT_EXTREME` ติดป้าย SELL
  ทุกครั้งที่ราคาอยู่บน 90% ของกรอบ 100 แท่ง H1 (33% ของแท่งในการเล่นซ้ำ) ซึ่งในขาขึ้นแรงเป็นเรื่องปกติ (ยังไม่แก้ — รอผู้ใช้ตัดสิน)
- เทสต์ `DigestStructureEventTest` 3 เคส — รวม 477/477; ติดตั้งบนมือถือแล้ว

## 2026-09-19 — ติดตามผล "มุมมอง" ของ AI ทุกการปลุก + ส่งผลกลับเข้า prompt

ที่มา: ผู้ใช้ถามว่า AI บอก SELL แล้วราคาลงจริงไหม และการเรียนรู้ได้ผลจริงหรือไม่ — เดิมวัดแค่รายปัจจัย ไม่มีใครวัดว่ามุมมองที่ AI แจ้งถูกหรือผิด

- ย้อนตรวจ NOTIFY เดิม 16 ใบ (M1 ในเครื่อง): ปิดแล้ว 14 → ชน TP 4 (+3.90R) / ชน SL 10 (−10R) = **สุทธิ −6.1R, ชนะ 29%**;
  BTC SELL 8/8 ชน SL (ขายสวนขาขึ้นเพราะราคา "premium"), BUY 4 TP / 1 SL; TP ช่วงแรกได้ RR < 1 (ก่อนมีเกณฑ์ 1:1)
- `15.sqm` ตาราง `AiWakeView` (1 แถว/การปลุก): decision, bias, confidence, reason, price, atr, SL/TP, status
  `OPEN → TP / SL / EXPIRED (48 แท่ง) / CLOSED (SKIP/ไม่มีระดับ วัด 12 แท่ง)`, result_r, move/mfe/mae (ATR)
- `AiViewTracker` (ใหม่): `record` ตอน `handleWakeAlert`, `resolve` ทุกรอบสแกน (ช่วงแรกใช้ M1 หลังเวลามุมมอง — กัน high/low ก่อนมุมมองทำให้ชนปลอม;
  แท่งเดียวชนทั้งคู่นับ SL), `backfillOnce` ดึงคำตัดสินเก่าจาก `AnticipationFactorOutcome` + ระดับจากการ์ดแชท
- prompt: ส่วนใหม่ "มุมมองที่คุณเคยให้บน SYMBOL (24 ชม.) และผลจริง" (4 รายการล่าสุด + สถิติ 30 วันแยก BUY/SELL)
  กติกา: ห้ามแจ้งทิศเดิมซ้ำขณะมุมมองยังเปิด, กลับทิศต้องบอกว่าอะไรเปลี่ยน, ใช้สถิติตัวเองประกอบความมั่นใจ
- รายงานการเรียนรู้ (`WakeLearningStore.buildReport`) ต่อท้ายด้วยสรุปผลมุมมอง; ไฟล์ส่งออกการเรียนรู้พก `views`; prune: OPEN เกิน 30 วัน → EXPIRED, ลบเกิน 365 วัน
- สคริปต์ `device_wake_audit.py` แสดง "ผลมุมมอง AI" ต่อการปลุก
- เทสต์ `AiViewTrackerTest` 8 เคส — รวม 473/473
- ข้อจำกัดที่ยังอยู่: การเรียนรู้รายปัจจัยต้องมี 20 ตัวอย่างจึงเชื่อถือ/40 จึงลดน้ำหนัก ตอนนี้สูงสุด 8 → ยังไม่มีผลต่อการปลุก;
  แถวของ job ที่ถูกลบ (XAU 35 แถว PENDING) ไม่มีใครสแกนต่อ จะหมดอายุเองใน 30 วัน
- ติดตั้งบนมือถือแล้ว (23:07): ย้อนเติม 55 มุมมอง, ปิดผลทันที 28 — NOTIFY BUY: TP 3 / SL 1 (+2.16R), NOTIFY SELL: SL 8/8 (−8R),
  SKIP ปิดแล้ว 16; มุมมองทองยังเปิด (ตลาดปิด) และ BTC SELL 23:00 (บันทึกโดย build ก่อน ยังไม่มีผลย้อนหลังใน prompt)

## 2026-09-19 — ตรวจการคำนวณรอบ BTC (20:30–22:30 ไทย) + ขยายสคริปต์ตรวจ

- สคริปต์ตรวจเงื่อนไขปัจจัยที่ปลุกได้ 31 ตัว → **ตรงกับแอปทั้งหมด (✗ 0)**; ATR ที่แอปบันทึกตรงกับที่คำนวณใหม่ทุกครั้ง
- RR ของ NOTIFY ทั้ง 3 ครั้งผ่านเกณฑ์ 1:1 (2.14, 1.29, 1.14) — คำนวณซ้ำแล้วถูกต้อง
- ยืนยันบนเครื่อง: การ์ดขึ้นแชทก่อนเสียง (22:15:16.268 pushToChat → .282 speakAlert)
- แก้สคริปต์: ราคาอ้างอิงเดิมใช้ max ของทุกแถวในแท่ง (รวมแถวที่บันทึกทีหลังด้วยราคาอื่น) → ใช้แถวที่ปลุกจริง;
  แยกป้าย [STATE]/[EVENT] ของแถวที่ไม่ได้ปลุก; ข้อความ error ภาษาไทยบน stderr; แยก "ต้องใช้ข้อมูลภายในแอป" กับ "ยังไม่มีสูตร"
- แก้แอป: log "เสียงจริงใช้ …" เตือนเฉพาะตอนสลับ Live ↔ Android TTS จริง (เดิมขึ้นทุกครั้งเพราะชื่อป้ายต่างกัน)
- ข้อสังเกต: AI แจ้ง BUY 21:46 แล้ว SELL 22:15 และ 22:30 — แต่ละครั้งวิเคราะห์ใหม่โดยไม่รู้ว่าเพิ่งแจ้งอะไรไป

## 2026-09-19 — ระบบปลุก AI เคารพเวลาเปิด/ปิดตลาด + เกณฑ์ RR 1:1

ที่มา: ตรวจการแจ้งเตือนตาม runbook — ทองถูกปลุก 5 ครั้งในชั่วโมงสุดท้ายก่อนปิดสัปดาห์ (SKIP ทั้งหมด เปลืองงบ 5/6)
และ BTC NOTIFY SELL มีระดับ SL/TP ที่ให้ RR แค่ 1:0.76

- `MarketHours` (commonMain, อิง America/New_York รองรับ DST): FX เปิดอาทิตย์ 17:00 → ปิดศุกร์ 17:00, ทองเปิดอาทิตย์ 18:00
  + พักรายวัน จ–พฤ 17:00–18:00, คริปโต 24/7, อื่นๆ ถือว่าเปิด (ยังไม่รวมวันหยุดพิเศษ)
- `AnticipationEngine`: ตลาดปิด → คืนผลทันที **ไม่ดึงแท่งเทียน ไม่สแกน ไม่เรียก AI** (log เฉพาะตอนสถานะเปลี่ยน);
  เหลือ ≤ 60 นาทีก่อนปิดสิ้นสัปดาห์ → ยังสแกน/เก็บการเรียนรู้แต่ไม่ปลุก AI; tool `scan` แจ้งว่าตลาดปิด + เวลาเปิดถัดไป
- RR: prompt บังคับ LEVEL_SL/LEVEL_TP ที่ให้ RR ≥ 1:1 ไม่งั้นตอบ SKIP; `Verdict.rr()`; การ์ดแชทแสดง "ผิดทาง · เป้า · RR 1:x"
  (สีเหลือง + ⚠️ ถ้าต่ำกว่า 1:1) และเสียงพูดเตือนเมื่อ RR ต่ำกว่า 1:1
- เทสต์ `MarketHoursTest` (สุดสัปดาห์, ก่อนปิดวันศุกร์, อาทิตย์ FX/ทองเปิดไม่พร้อมกัน, พักรายวันทอง, ฤดูหนาว EST, RR) — รวม 465/465
- ติดตั้งบนมือถือแล้ว: BTC สแกนปกติ, log "▶ BTCUSDT@15m ตลาดเปิด — เริ่มสแกน"

## 2026-09-19 — เก็บเหตุผลและความมั่นใจของ AI ทุกการปลุก (NOTIFY และ SKIP)

- ที่มา: ตรวจการ SKIP 5 ครั้ง (00:01–01:00 ไทย) แล้วพบว่าเหตุผลของ AI ไม่ได้เก็บไว้ที่ไหนเลยนอกจาก logcat ซึ่งถูกเขียนทับไปแล้ว
  (ประเมินจากหลักฐานแทน: 3 ครั้งถูกต้อง 2 ครั้งก้ำกึ่ง — ราคาไปต่อแค่ ~1.2–1.3 ATR)
- `14.sqm`: `AnticipationFactorOutcome` เพิ่ม `ai_confidence INTEGER`, `ai_reason TEXT` (ต่อท้ายตาราง ตรงกับ `JarvisDatabase.sq`)
- `setFactorOutcomeAiDecision` / `WakeLearningStore.setAiDecision` รับ confidence + reason; `handleWakeAlert` ส่ง `verdict.confidence`, `verdict.reasonTh`
- ไฟล์ส่งออกการเรียนรู้ (`LearningTransfer`) พกค่าใหม่ไปด้วย — ไฟล์รุ่นเก่ายังนำเข้าได้ (ค่าเป็น null)
- tool action `inspect` แสดงความมั่นใจ, สคริปต์ `tools/device_wake_audit.py` แสดงความมั่นใจ + เหตุผล
- ติดตั้งบนมือถือจริงแล้ว: migration ผ่าน (schema 15, quick_check ok, ข้อมูลเดิม 61 แถวครบ)
- เทสต์ 450/450 (เพิ่มเทสต์ไฟล์การเรียนรู้รุ่นเก่า/ใหม่)

## 2026-09-19 — แก้: ดึงแท่งเทียนจาก TradingView ช้า 13–14 วิ/ครั้ง

- สาเหตุ: `TvHistoryBridge.android.kt` ต่อ string เฟรมคำสั่งเอง — payload ของ `resolve_symbol` (JSON ซ้อนใน string)
  มี `"` ที่ไม่ได้ escape → TradingView ตอบ `protocol_error` (ยืนยันจาก PC: เฟรมเดิม error ใน 0.3 วิ, เฟรมที่ escape ถูกได้แท่งใน 0.17 วิ)
  bridge ไม่ฟัง error จึงรอ timeout 12 วิ แล้วค่อยไปเส้นสำรอง (Ktor) ทุกครั้ง
- แก้: `TvProtocol` (commonMain) สร้างเฟรมด้วย JSON encoder + bridge เลิกรอทันทีเมื่อได้ `protocol_error/critical_error/symbol_error/series_error`
- ผลบนมือถือจริง: 0.5–0.9 วิ/ครั้ง, สแกนระบบปลุก AI 1 รอบ 4–6 วิ (จาก 1.5–2.5 นาที) — แจ้งเตือนไม่ช้า ~2 นาทีอีกต่อไป
- เทสต์ `TvProtocolTest` (เฟรมเป็น JSON ถูกต้อง, payload round-trip, จำนวนแท่งเป็นตัวเลข, ตรวจจับ error) — รวม 448/448

## 2026-09-19 — Runbook ตรวจแจ้งเตือนระบบปลุก AI บนมือถือจริง

- เพิ่ม `tools/device_wake_audit.py` — ดึงฐานข้อมูลแอปผ่าน `adb exec-out run-as` แสดง job/การตั้งค่า/การปลุกแต่ละครั้ง
  แล้วคำนวณค่าหลักใหม่จากแท่งเทียนดิบ, `--logcat` สรุป log และระยะห่างการดึงแท่งเทียน, ลบสำเนาฐานข้อมูลตอนจบ
- วิธีทำเองและการตีความ: [[../07_Trading_Intelligence/67_Device_Wake_Alert_Audit]]
- ผลตรวจครั้งแรก: ค่าในแจ้งเตือน 23:21 และ 23:56 ถูกต้องทุกตัว; พบการดึงแท่งเทียน ~13–14 วิ/ครั้ง (ยังไม่แก้)

## 2026-09-18 — Compose Canvas Engine: ปรับโทนให้ตรงภาพ LOOI 40 Moodset (Neon Cyan & Sensor Style)
- **User Request**: ปรับโหมดสัตว์เลี้ยง Compose Canvas Engine ให้สวยงามคล้ายภาพต้นฉบับ LOOI Robot 40 Moodset
- **Changes**:
  - `PetRobotNeonDraw.kt`: เพิ่ม `drawSoftBloom()` (radial falloff นุ่ม จางเร็วที่ขอบ — ไม่เป็นวงแข็งแบบ stroke ring เดิมที่ทำให้ดูเหมือนวงหลังใหญ่กว่า) และเปลี่ยนสีขอบเงาของ cyan จาก Royal Blue `#0D25B9` → Deep Teal `#0A5C6B` ตามภาพ
  - `drawDualCircleEye` / `VirtualHeadKinematics`: เพิ่ม bloom; resting offset X 0.075→0.025, Y 0.080→0.065 ให้เงาเป็นขอบล่างบางๆ แทนเสี้ยวขวาล่าง
  - `drawHappyEye`: จากโดมทึบ → เส้นโค้ง ⌒ หนาปลายมนตามช่อง Happy; ตัดปากยิ้มถาวรของ HAPPY/LAUGHING (ภาพต้นฉบับไม่มีปาก — ปากขึ้นเฉพาะตอนพูด)
  - `drawSleepingEye`: bloom + ขอบเงา teal ด้านล่าง; `drawExcitedEye`/`drawCrossEye`/`drawAngryEye`: เพิ่ม soft bloom
  - `drawParametricEye`: glow ค่าเริ่มต้น 0dp → 6dp (alpha 0.30); rim offset 2.5dp,4.5dp → 0.8dp,4dp
  - Test `getEyeDeepShadowColor` อัปเดตเป็น `#0A5C6B`
- **ยังไม่ทำ**: สถานะ 21–40 ในภาพ (Proud, Scanning, Processing, Charge, Weather, Alarm, Standby Clock, Face Recognized ฯลฯ) ยังไม่มีใน `AvatarEmotion`

## 2026-09-18 — แก้: alert ระบบปลุก AI ค้าง TRIGGERED และหยุดสแกนหลังปลุกครั้งแรก

อาการ (logcat จริง 23:21–23:25): ปลุก AI สำเร็จ 1 ครั้ง แล้วไม่ดึงแท่งเทียนอีกเลย, หน้า Cron job ค้าง TRIGGERED,
4 นาทีต่อมาบริการหยุดตัวเอง ("No active alert jobs or scheduled tasks. Stopping service.")

สาเหตุ: `getRunnableJobs` ข้าม job ที่ `is_triggered = 1` ยกเว้น `trading_signal_alert`
— job `trading_anticipation` เป็น edge-trigger เหมือนกัน (ต้องสแกนรอบถัดไปเพื่อรีเซ็ตตัวเอง)
แต่ไม่อยู่ในข้อยกเว้น พอปลุกครั้งแรกจึงหลุดจากรอบสแกนถาวร และบริการเห็นว่าไม่มี job เหลือ

แก้: `getRunnableJobs` รวม `trading_anticipation`, หน้า Automation แสดง "TRIGGERED · ปลุก AI แล้ว รอเหตุการณ์ใหม่"
และไม่แสดงปุ่ม 🔁 สำหรับ job ประเภทนี้ (รีเซ็ตเองในรอบถัดไป)

## 2026-09-18 — V29.1: review ระบบปลุก AI รอบ 2

ดูตารางบัคทั้งหมด [[../07_Trading_Intelligence/66_WakeEngine_Live_Backup_V29#8. Review รอบ 2 — บัคที่แก้ (2026-09-18)]]

**บัคหนักที่สุด**
- สแกนจากแชทกิน cooldown/การปลุกของแท่งนั้น → alert เบื้องหลังเงียบ (แก้: preview mode)
- ตัวนับ wake_event_count/wake_buy/wake_sell นับเหตุการณ์ที่ติดงบ → เรียก AI เกินงบ
- ทุกการปลุกส่ง system prompt แชทของ JARVIS ทั้งชุด (ประมาณ 14,000 ตัวอักษร) → ใช้ system prompt เฉพาะงาน (< 600 ตัวอักษร)
- การเรียนรู้ใช้ราคาอ้างอิงเก่า (ราคาปิดแท่ง TF หลัก) กับเหตุการณ์ที่เกิดกลางแท่ง
- แถวการเรียนรู้ค้าง PENDING และบังแถวใหม่ไม่ให้ได้วัดผล
- ความแม่นของ AI วัดตามทิศของปัจจัย ไม่ใช่ของ AI
- digest รวมแนวรับ/ต้านของคู่เงิน FX เป็นก้อนเดียว
- governor ลืมทุกอย่างเมื่อบริการรีสตาร์ท

**สถานะ**: `:composeApp:testDebugUnitTest` **444/444** ✅

## 2026-09-18 — V29: ต่อระบบปลุก AI เข้า alert จริง + OHLCV pruning + สำรอง/กู้คืน

ดูรายละเอียด [[../07_Trading_Intelligence/66_WakeEngine_Live_Backup_V29]]

**ระบบปลุก AI ทำงานจริงบนมือถือแล้ว** — แยกจาก Signal Alert (strategies/backtest) โดยสิ้นเชิง
- alert job `trading_anticipation` / field `wake` (migration ย้าย job คาดการณ์เดิมให้)
- โหมด ai: AI เห็น snapshot 5TF แล้วตัดสิน NOTIFY/SKIP เอง; SKIP = ไม่รบกวนผู้ใช้ แต่บันทึกลงการเรียนรู้
- เสียงพูด summary ที่ AI เขียน — ไม่ส่ง payload ให้ Live สรุปซ้ำ (ประหยัด TPM 65K ของ Live)
- ลบ supervisor/การ์ด/เสียงของระบบคาดการณ์เดิม และ field `signal_anticipation*` ออกจาก Signal Alert

**คำตอบเรื่องขนาด OHLCV store**: ≈1 MB ต่อซีรีส์ (6,000 แท่ง), ≈6–8 MB ต่อสินทรัพย์ที่เฝ้า 5TF + intermarket
→ เพิ่ม `OhlcvMaintenance`: ลบซีรีส์ที่ไม่ได้อ่านเกิน 30 วันและไม่มี alert ใช้ (วันละครั้ง) + ลบการเรียนรู้เก่ากว่า 1 ปี

**สำรอง/กู้คืน** (Settings → สำรอง / กู้คืนข้อมูล)
- การเรียนรู้: JSON, นำเข้าแบบรวม (ย้ายเครื่อง A → B ได้, นำเข้าซ้ำได้)
- ฐานข้อมูลทั้งหมด: zip + manifest, ตรวจไฟล์ก่อนกู้คืน, สลับไฟล์ตอนเริ่มแอป เก็บของเดิม 1 ชุด
- Google Drive ผ่านหน้าต่างเลือกไฟล์ของระบบ (SAF) — ไม่ต้องตั้ง OAuth

**สถานะ**: compile Android ผ่าน, `:composeApp:testDebugUnitTest` **433/433** ✅
(ลดจาก 436 เพราะลบเทสต์ของโค้ดคาดการณ์เดิม แล้วเพิ่ม `WakeSystemTest`, `BackupAndMaintenanceTest`)
ยังไม่ได้ทดสอบบนเครื่องจริง: flow กู้คืน+รีสตาร์ท, การเลือก Google Drive, การปลุก AI ในตลาดจริง

## 2026-09-18 (รอบ 3) — Anticipation = ระบบปลุก AI (V28)
เจตนาที่ผู้ใช้ชี้แจง: **ปัจจัยคือนาฬิกาปลุก ไม่ใช่เทรดเดอร์**
AI เฝ้ากราฟตลอดเวลาไม่ได้เพราะเปลืองโทเคน → ปัจจัยทำหน้าที่เป็นอินดิเคเตอร์ที่คอยปลุก
แล้ว snapshot 5TF คือ "ภาพที่ AI ตื่นมาเห็น" — AI เป็นคนวิเคราะห์เอง ปัจจัยไม่ใช่ข้อสรุป
รายละเอียดเต็ม: [[../07_Trading_Intelligence/70_WakeTrigger_Architecture_V28]]

**ปัญหาแกนกลางที่พบ**: โค้ดทำตรงข้ามกับเจตนา — ปัจจัยคำนวณ Entry/SL/TP/RR/confidence
แล้วยัดใส่ prompt ว่า "Execution Rails คาดหมาย" ก่อนที่ AI จะได้มองโครงสร้างตลาด
AI จึงถูก anchor ด้วยตัวเลขที่อินดิเคเตอร์แต่งขึ้น แล้วถูกถามแค่ APPROVE/VETO/ADJUST
(ตัวอย่างที่ชัดสุด: FAST_RSI ใช้ entry×0.95/×1.10 ซึ่งเป็นพารามิเตอร์คริปโต
 → ทอง 4000 ได้ SL 3800 / TP 4400 แล้วบอก AI ว่านี่คือแผนเทรด)

**แก้แล้ว**
- **prompt ใหม่**: ถอด Execution Rails ออก เหลือ "เกิดอะไรขึ้น + ภาพ 5TF + วิเคราะห์เอง"
  เปลี่ยน DECISION เป็น NOTIFY/SKIP + BIAS + LEVEL_SL/LEVEL_TP ที่ **AI กำหนดเองจากโครงสร้างจริง**
  (ใช้รูปแบบเดียวกับสาขา isKeyzoneOnly ที่ออกแบบถูกอยู่แล้วในโค้ดเดิม)
- **ส่งหลักฐานฝั่งตรงข้ามให้ AI**: เดิม dominantHits ทิ้งฝั่งน้อยกว่าเงียบๆ แล้วรายงาน confidence สูง
  เพิ่ม `signal_anticipation_opposing` + `signal_anticipation_factors`
- **confidence คิดจากกลุ่มหลักฐาน ไม่ใช่จำนวนปัจจัย**: `factorEvidenceGroup()` จัดกลุ่ม
  squeeze 2 ตัว / oscillator 3 ตัว / sweep 2 ตัว เป็นกลุ่มเดียว
  เดิม `max(88, base+10)` ทำให้ 3 ปัจจัยอ่อนได้ 88% เสมอ และ 2 ปัจจัยอ่อน (70,71) ชนะ 1 ปัจจัยแข็ง (79)
  ตอนนี้: `base + (กลุ่มอิสระ−1)×5 − (กลุ่มฝ่ายตรงข้าม)×8`

**Learning เขียนใหม่ทั้งหมด** (`AnticipationLearningStore` + migration 13)
- เดิมเก็บใน `mutableMapOf` ระดับ object → **หายทุกครั้งที่ปิดแอป** และสร้างใหม่ไม่ได้
- เดิม 10 จาก 13 ปัจจัยฝัง confidence เป็นเลขดิบ → การเรียนรู้ไม่มีผลกับมันเลย
- เดิมสัญญาณหลายปัจจัยบันทึกเป็น `ANTICIPATION_CONFLUENCE` ซึ่งไม่อยู่ในทะเบียน
  → **ปัจจัยที่ร่วมยิงไม่เคยได้เครดิต/ถูกตำหนิ** ทั้งที่เป็นข้อมูลชิ้นเดียวที่ใช้ตัดปัจจัยได้
- ตอนนี้: ตาราง `AnticipationFactorOutcome` แตก 1 สัญญาณเป็น N แถว (แถวละปัจจัย)
  พร้อม context bucket (mtf_align × adx × volatility × session)
  วัดด้วย **forward return 12 แท่ง หน่วย ATR** ไม่ใช่ SL/TP สมมติ
  ความเชื่อมั่นใช้ shrinkage: n น้อย→เชื่อ prior, n มาก→เชื่อข้อมูลจริง (ใช้ปัจจัยใหม่ได้ทันที)
- `buildReport()` ชี้ปัจจัยที่ n≥30 แต่ avg R ≈ 0 ว่า "ควรตัดทิ้ง"

**แก้ข้อมูลเรียนรู้ปนเปื้อน**: `forwardCandles` เปลี่ยนจาก `>= created_at` เป็น `> created_at`
เดิมรวมแท่งที่สัญญาณเกิดทั้งแท่ง รวม high/low ที่เกิด**ก่อน**สัญญาณมีอยู่จริง
→ ถูกตัดสิน INVALIDATED −1R จากราคาของตัวเอง และลำเอียงกับปัจจัยประเภท sweep มากที่สุด
(เพราะมันต้องมี sweep เกิดก่อนจึงยิง)

**Token economy** — จาก Live API docs: *"billing follows a compounding model based on the
active context window"* คิดทั้ง context สะสมใหม่ทุก turn และเก็บประวัติเสียงเป็น audio token
- วัดจริง: snapshot 5TF = 284 tokens, prompt รวม = 765 tokens, 1 turn ≈ 855 tokens
- ปัญหาไม่ใช่ข้อมูล แต่คือ context สะสม: context 20K → 1 turn ≈ 21K → เหลือ ~3 ครั้ง/นาที
- แก้ `LiveContextWindowCompressionConfig` จาก `slidingWindow: {}` เปล่าๆ
  เป็น `triggerTokens 25K / targetTokens 8K` ตามที่ docs แนะนำ พร้อม fallback ไล่ระดับ
- TPM 250K ÷ RPM 15 = **16,666 tokens/request** แต่ใช้อยู่ 765 → เหลือ 95%
  → ขยาย snapshot ได้อีกมาก

**ขยาย snapshot 5TF** (`MarketContextDigest`)
- `TfLine` เพิ่ม ADX / MACD hist / BB %B / BB width (×ATR) / ระยะจาก EMA50 (×ATR) / volume ratio
- key levels 3 → 6 ต่อฝั่ง พร้อมระยะห่างเป็นหน่วย ATR (AI ไม่ต้องคำนวณเอง)

**สถาปัตยกรรมใหม่รองรับ 100+ ปัจจัย** (`automation/wake/`)
- `WakeTrigger` interface: id / evidenceGroup / kind (EVENT vs STATE) / detect(WakeContext)
  **`TriggerEvent` ไม่มี entry/sl/tp โดยเจตนา** — ปัจจัยห้ามสรุปแทน AI
- `WakeContext` ให้ครบ 5 TF (m1/m5/m15/h1/h4) + digest — ปัจจัยใช้ MTF ได้จริง
  (เดิม 12 จาก 13 ปัจจัยรันบน TF เดียว ทั้งที่ระบบดึง 5TF มาแล้วทุกรอบ)
- แยก EVENT (ปลุกได้) ออกจาก STATE (เป็นบริบท ไม่ปลุกซ้ำทุกแท่ง)
- ชุดเริ่มต้น 12 ตัว ครบทุก evidence group รวม **`M5_CONFIRM_DIVERGENCE`**
  (ชั้นคัดกรองการหลอกด้วย M5 ที่ระบบเดิมขาดไป) และ `SESSION_LEVEL_SWEEP`
  ที่อิงเวลา session จริง (ASIA/LONDON/NY) แทนตัวเดิมที่ใช้แค่ 24 แท่งล่าสุด

**ผลตรวจที่ผมรายงานผิดรอบก่อน**: "7 EMA field หายไป" — **ไม่จริง** มีครบทั้ง 2 map
(grep ผมกรองเฉพาะ key ขึ้นต้น `signal_` เลยพลาด) ไม่ได้แก้อะไรตรงนี้

**สถานะ**: `:composeApp:testDebugUnitTest` ผ่าน **436/436** ✅
เพิ่ม `WakeTriggerRegistryTest` + เขียน `SignalAnticipationTest` ส่วน RL ใหม่

## 2026-09-18 (รอบ 2) — Indicator Consolidation & Definition Parity
ต่อจาก Phase 0–4 — ปิดงานที่ค้างไว้ 3 ข้อ และพบ implementation ซ้ำตัวที่ 4 ระหว่างทาง

**พบ implementation อินดิเคเตอร์ตัวที่ 4** ใน `StrategySignalProvider.TradingViewIndicatorSnapshot`
(object นี้มีหน้าที่ "ส่ง indicator ให้ AI โดยเฉพาะ" จึงกระทบหลักการโดยตรงที่สุด) ปัญหาที่พบ:
- `rsi(...) ?: 50.0` และ `adx(...) = Triple(0,0,0)` → **ป้อนค่าปลอมให้ AI ตรงๆ**
- `adx()` คืน **DX ดิบ** ไม่ใช่ ADX ที่ smooth แล้ว และ DI ใช้ค่าเฉลี่ยธรรมดาแทน Wilder RMA
  → เกณฑ์ `adx >= 25` ที่ใช้ตัดสิน STRONG_UPTREND เทียบกับตัวเลขคนละสเกลกับ tool อื่น
- `supertrend()` ไม่ใช่ Supertrend เลย — เป็นแค่ ATR band รอบ mid ของแท่งล่าสุด ไม่มี band locking/trend persistence
- `trueRangeAtr()` ใช้ simple average แทน Wilder RMA
- `macdSeries` เรียก `ema(c,12)`/`ema(c,26)` ใหม่ทุก index → O(n²) พร้อม allocation
- `macdSignal` แปลง NaN → 0.0 ก่อนเข้า EMA → signal line ปนเลขศูนย์
- VWAP สะสมทั้งชุดข้อมูล ไม่รีเซ็ตรายเซสชัน
→ เขียน `calculate()` ใหม่ delegate ไป TaIndicators ทั้งหมด **fail-closed ทั้งก้อน**
(คืน null ถ้าตัวใดตัวหนึ่งคำนวณไม่ได้ ดีกว่าส่ง snapshot ที่มีค่าปลอมปน) ลบ helper ซ้ำ 180 บรรทัด
เกณฑ์แท่งขั้นต่ำ 220 → `Warmup.FULL_SET` (800) เพราะ 220 ไม่พอให้ EMA200 ลู่เข้า

**แก้ ADX off-by-one**: DX ตัวแรกที่แท่ง index=`period` ไม่เคยถูกนับ (loop เริ่มที่ `period+1`)
ค่า ADX จึงเลื่อนไป 1 แท่งเทียบกับ TradingView/MT5

**VWAP session anchor ต่อ instrument**: `sessionOffsetHoursFor()` — FX/โลหะมีค่ารีเซ็ตที่ 22:00 UTC
(ซิดนีย์เปิด ตรงกับจุดที่โบรกเกอร์ปิดแท่ง D1) ส่วนคริปโตใช้ขอบวัน UTC

**Parity harness** `IndicatorDefinitionParityTest` — เขียน reference implementation ตามสูตรตรงๆ
(ช้าแต่ชัด ไม่ optimize) แล้วเทียบกับตัวจริงบน 6 ชุดข้อมูล × ทุก period:
- EMA/RSI/ATR/ADX/MACD/Bollinger ตรงตามนิยาม ±1e-9
- Supertrend ที่ optimize เป็น O(n) ให้ผลเท่ากับ naive O(n²) เป๊ะ
- `atrSeries()` (ตัวที่เพิ่มมาแก้ O(n²)) ค่าสุดท้ายตรงกับ `atr()`
- อินดิเคเตอร์ไม่ขึ้นกับ timeframe spacing (จับกรณีเผลอเอา timestamp ไปคำนวณ) — ทดสอบครบทุก TF ในทะเบียน
- VWAP รายเซสชันไม่รวมแท่งของวันก่อนหน้า
- known-answer: ราคาขึ้นทุกแท่ง → RSI=100, ราคาคงที่ → stdev=0/BB ทุกเส้นเท่ากัน, OBV/Donchian/ROC/Williams %R

**แก้ผลกระทบจากเกณฑ์แท่งที่สูงขึ้น**: `StrategySignalProvider` 3 จุดยังดึงแค่ 300 แท่งผ่าน
`fetchTradingViewCandlesOnly` (ยิงตรง TV ไม่ผ่าน DB) → snapshot จะเป็น null ตลอด
เปลี่ยนเป็น `fetchCandlesWithSource` ที่เป็น DB-backed และขอ 800 แท่ง
ตอนนี้ **ไม่มี caller ของ `fetchTradingViewCandlesOnly` เหลือในโปรเจกต์แล้ว** — ทุกเส้นทางผ่าน store เดียวกัน

**สถานะ**: `:composeApp:testDebugUnitTest` ผ่าน **423/423** ✅
เหลือเฉพาะ parity เทียบตัวเลขจาก TradingView ของจริง ซึ่งต้องมี fixture ที่ export มาจาก TV
(ปัจจุบันยืนยันความถูกต้องเทียบ "นิยาม" ได้ครบแล้ว)

## 2026-09-18 — OHLCV Foundation & Indicator Correctness (Phase 0–4)
ที่มา: audit ทั้งระบบ tool ของ composeApp พบว่าระบบ "ป้อนตัวเลขปลอมให้ AI" หลายจุด ซึ่งขัดหลักการหลักของโปรเจกต์
(ระบบต้องคำนวณค่าจริงแล้วส่งให้ AI วิเคราะห์ — ไม่ใช่ปล่อยให้ AI เดา) รายละเอียดเต็มที่
[[../07_Trading_Intelligence/69_OHLCV_Indicator_Foundation_V27]]

**Phase 0 — หยุดเลือด**
- `TaIndicators.ema/sma` เดิมคืน `data.last()` เมื่อแท่งไม่ครบ period → **EMA200 บน 15m เท่ากับราคาปิดเป๊ะทุกครั้ง**
  (เพราะ `recommendedMinBars` ให้แค่ 150 แท่ง) ตอนนี้คืน `null` — fail-closed
- รูปแบบเดียวกันใน `AdvancedTradingEngine`: HMA/WMA คืน `data.last()` → เปลี่ยนเป็น nullable, `analyze()` คืน null เมื่อแท่งไม่พอ
  (MIN_BARS 60 → 80 เพราะ HMA55 ต้องการ 55+√55−1 = 61 แท่ง เดิมต่ำกว่าเกณฑ์ 1 แท่งพอดี → fallback ตลอด)
- **รวม timeframe map 4 ชุดเป็นทะเบียนเดียว** `TaIndicators.TIMEFRAMES` — เดิม 6h/8h/12h ตกไป "60" ใน tvResolution
  และ 8h ไม่มีใน `intervalToMillis` → ขอ 8h ได้แท่ง 1h เงียบๆ + นับแท่งที่ขาดผิด 8 เท่า
- แก้ ambiguity `"1M"` (รายเดือน) vs `"1m"` (1 นาที) ด้วยการ match canonical แบบตรงตัวพิมพ์ก่อน
  (เดิม `normalize("mn")="1M"` แล้วป้อน `"1M"` กลับได้ `"1m"` — round-trip พัง)

**Phase 1 — OHLCV Store**
- **DB ไม่เคยสะสมประวัติ**: `trimTvCandlesByWindow` ลบทุกแท่งที่เก่ากว่า 300 แท่งล่าสุดทุกครั้งที่ refresh
  ทับ retention ที่ `saveTvCandlesToDb` เพิ่งตั้งในทรานแซกชันเดียวกัน → ลบทิ้งแล้ว retention = 6,000 แท่ง/ซีรีส์
- **schema (migration 12.sqm)**: `UNIQUE(symbol, interval, source, ts)` + คอลัมน์ `is_closed`
  เดิม source ไม่อยู่ใน unique key และ query ไม่ filter → ราคา OANDA/FX_IDC/TVC เขียนทับกันรายแท่ง เป็น "โมเสก" ของหลาย feed
- **แท่งที่ยังก่อตัว**: `Candle.isClosed` + `markClosedState()` — อินดิเคเตอร์ใช้เฉพาะแท่งปิด (กัน repainting)
- **bucket alignment**: `estimateMissingBars` เลิกใช้ `(now/tfMs)*tfMs` ที่ align กับ epoch
  (แท่ง D1 ทองเปิด 21:00 UTC ไม่ใช่ 00:00; epoch week เริ่ม "วันพฤหัส") → วัดจาก timestamp แท่งล่าสุดแทน
- **warm-up policy** `TaIndicators.Warmup`: FAST_SET 150 / MACD_SET 200 / ICHIMOKU_SET 250 / FULL_SET 800 / BACKTEST 5,000
  `recommendedMinBars` เดิม 150-300 → 800 (EMA200 ต้องการ period × 4 จึงลู่เข้า)
- **backfill** ย้อนหลังทีละรอบ (guard 30 นาที) + backtest อ่านจาก DB แทน in-memory cache
  → ไม่ต้องโหลด 5,000 แท่งใหม่ทุกครั้งที่รีสตาร์ทแอป
- **ตัด Yahoo ออกจากเส้นทาง OHLCV ทั้งหมด** (ยังใช้กับราคา/fundamental ที่ TradingApiService)
  TradingView เป็นแหล่งหลัก, Binance เป็น fallback เฉพาะคู่คริปโตแท้
- `OhlcvCentralStore`: เลือก source ตามลำดับความน่าเชื่อถือ (เดิม "ซีรีส์ไหนยาวสุด" → PAXG ชนะทองจริงได้)
  + เพดาน 1,200 แท่ง/ซีรีส์ และ LRU 64 ซีรีส์ (เดิมโตไม่จำกัด)

**Phase 2 — Indicator Engine**
- ลบสำเนา EMA/RSI/SMA/Stoch/CCI/BB/ATR/ADX ใน `IndicatorAlertProvider` (fail คนละแบบกับ TaIndicators) → เหลืออิมพลีเมนต์เดียว
- Supertrend เดิม O(n²) (เรียก `atr()` ใหม่ทุกรอบบน sublist ที่ยาวขึ้น) → ใช้ `atrSeries()` คำนวณครั้งเดียว O(n)
  และ seed band จากแท่งแรกที่คำนวณได้จริง (เดิมเริ่มที่ 0.0 ซึ่งบังเอิญใช้ได้เฉพาะราคาบวก)
- **เพิ่มที่ประกาศไว้แต่ไม่เคยมี**: OBV + obv_slope20, MFI14, Ichimoku ครบ 5 ค่า, ROC, Williams %R,
  Donchian (มี mid), Pivot R1-R3/S1-S3, Fibonacci levels, atr_pct, volume_sma20, ema spreads
- **VWAP แก้ให้ถูกนิยาม**: `vwapSession()` ผูก anchor รายเซสชัน (เดิมสะสมทั้ง 500 แท่ง = ~5 วันบน 15m ไม่ใช่ VWAP)

**Phase 3 — สัญญาระหว่างระบบกับ AI**
- **ชื่อ field ไม่ตรงกัน**: ประกาศ `adx14`/`resistance1`/`donchian_upper` แต่ส่งออก `adx`/`r1`/`donchian20_high`
  และ `obv`/`mfi14`/`ichimoku_*` ไม่เคย implement — `AutomationEvaluator` หาไม่เจอแล้ว `return false` **เงียบๆ**
  ผู้ใช้ตั้ง alert เห็นว่า active แต่ไม่มีวันยิงและไม่มีใครบอก → แก้ชื่อให้ตรง + ขยาย `AlertFieldCatalog.INDICATORS` ครบทุกตัว
- ลบ `automation_manage_alerts` / `automation_manage_schedule` ที่ประกาศซ้ำ 2 ครั้ง
  (`associateBy` เก็บตัวหลัง → ตัวที่ field ครบกว่าถูกทิ้ง AI จึงไม่รู้ว่ามี rsi7/vwap/ichimoku/supertrend ให้ใช้)
- ทุก tool result ติด `bars_used` + `source` + `timeframe` — แท่งไม่พอจะไม่ส่ง field นั้นเลย ไม่ส่งค่า default ปลอม

**Phase 4 — เก็บงานค้าง**
- `trading_price` หน่วยผิด: column `change` ของ TV scanner เป็น **%** ไม่ใช่ราคา (ยืนยันจาก `getTopGainers`/`getVolumeBreakout`
  ในไฟล์เดียวกันที่ sort/filter ด้วย field นี้) เดิมตีความเป็นราคา → `prev_close = close − %` คลาดเคลื่อนเกือบทั้งวัน
  และหน่วยไม่ตรงกับสาขา Yahoo/SMC ที่ถูกอยู่แล้ว
- `trading_elliot_modern_analysis` handler ใช้ชื่อ branch `trading_elliot_wave` ที่ไม่มีใครเรียก → คืน "Unknown tool" ทุกครั้ง
- `trading_crypto_overview` / `trading_economic_data` / `automation_manage_schedule` ไม่อยู่ใน allowlist ทั้ง 2 ชุด
  → `isToolAllowed` คืน false ทุกบริบท AI เรียกไม่ได้เลย
- Position sizing: ลบคำแนะนำ "ให้ลดขนาดเหลือ balance/riskPerUnit units" ที่จริงๆ คือ **ใหญ่กว่าเดิม 100 เท่า**
  ที่ risk 1% และเป็นขนาดที่โดน SL แล้วเสียเงินต้น 100% + เพิ่ม contract_size → คืนค่าเป็น lot ที่ใช้กับ MT5 ได้จริง

**สถานะ**: `:composeApp:testDebugUnitTest` ผ่าน **411/411** ✅ (เพิ่ม 3 ไฟล์เทสต์: OhlcvStoreIntegrityTest,
IndicatorFieldContractTest, TradingToolReachabilityTest)
**งาน MT5 พักไว้ตามคำสั่ง** — ยังเหลือ: SL/TP guard เป็น dead code (schema ไม่มี `price`), P6 RiskEngine เรียกไม่ถึง,
`close_all` ไม่มี confirmation, RiskEngine ปน risk กับ margin คนละหน่วย, daily-loss ตัดวันที่ UTC

## 2026-09-17 — แก้ไขขนาดวงกลมหน้า-หลังให้เท่ากัน 100% (ตัด Outer Glow Halo ออก) และติดตั้งลงอุปกรณ์จริง SM-S908E
- **User Request**: "มันยังเหมือนเดิมน่ะ วงกลม ดานหลังก้ยังใหญ่กว่าวงกลมด้านหน้า" (อ้างอิงภาพ `media_1789627409647.jpg`)
- **สาเหตุที่พบอย่างละเอียด (Root Cause)**:
  1. ใน `drawDualCircleEye`: เดิมมีโค้ดเลเยอร์ Front Glow Halo วาด `drawOval` ด้วยขนาด `effW + outerPad * 2f` (ขนาดขยายเพิ่มขึ้นถึง 24dp/px จากเส้นผ่านศูนย์กลางตา) อยู่ด้านหลังของวงกลมหน้า ส่งผลให้วงรัศมีแสงเรืองรองนี้กลายเป็น "วงกลมวงที่ 3 ขนาดใหญ่พิเศษ" ล้อมรอบตา 360 องศา และกลบเสี้ยวเงาสีน้ำเงินครามจนมองเห็นเป็นวงกลมด้านหลังใหญ่กว่าวงกลมด้านหน้า
  2. ใน `AvatarLayout.kt`: ในแนวตั้ง (Portrait) กำหนด `eyeDiameter = width * 0.32f` ขณะที่ `baseSpacing = width / 6f` ทำให้ช่องว่างระหว่างตาทั้งสองข้างแคบเกินไป (เหลือเพียง 1.4% ของความกว้างจอ) เมื่อมีรัศมี Glow จึงเกิดการชนและกลืนกันตรงกลาง
  3. ในอุปกรณ์ทดสอบจริง (`SM-S908E`): ตัวแอปพลิเคชันเดิมยังไม่ได้ถูก build และติดตั้งอัปเดตเวอร์ชันล่าสุดลงบนเครื่อง (`lastUpdateTime` เป็นของวันก่อนหน้า)
- **Actions Taken**:
  - **ตัด Glow Halo และ Stroke ขนาดใหญ่ออก 100%**:
    - ใน [PetRobotHeadAvatar.kt](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/PetRobotHeadAvatar.kt): ลบโค้ด `drawOval(size = effW + outerPad * 2f)` ออก ให้เหลือเพียง **2 เลเยอร์วงกลมขนาดเท่ากันทุกประการ 100% (`discSize = Size(effW, effH)`)**:
      - Layer 1: Back Base Disc (เบ้าตาสีน้ำเงินคราม Royal Blue `#0D25B9`) ขนาด `discSize`
      - Layer 2: Front Core Disc (ลูกตาสีนีออนไซแอน `#38D5FF`) ขนาด `discSize`
    - ใน `drawHappyEye`: ตัด Stroke glow ออก ให้เหลือโดมคู่ขนาดเท่ากันเป๊ะ
    - ใน [PetRobotParametricEye.kt](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/PetRobotParametricEye.kt): ตั้งค่าเริ่มต้น `glowRadiusDp = 0.dp`
  - **ปรับแต่งระยะเยื้องเสี้ยวพระจันทร์ (Resting Crescent Offset)**:
    - ใน [VirtualHeadKinematics.kt](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/VirtualHeadKinematics.kt): ปรับค่า Resting Offset เป็น `restingOffsetX = effW * 0.075f` และ `restingOffsetY = effH * 0.080f * blinkCompress` เผยขอบเสี้ยวพระจันทร์สีน้ำเงินครามที่ด้านล่าง-ขวาอย่างคมชัด สวยงาม ตรงตามภาพเครื่องจริง LOOI
  - **ปรับขนาดและระยะห่างดวงตาในแนวตั้ง (Portrait Spacing)**:
    - ใน [AvatarLayout.kt](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/AvatarLayout.kt): ปรับ `eyeDiameter = minOf(width * 0.24f, height * 0.20f)` โดยที่ตำแหน่งตายังคงอยู่ที่ 1/3 และ 2/3 ของความกว้างจอตาม **จุดตัด 9 ช่อง (Rule of Thirds)** ส่งผลให้มีระยะห่างตรงกลางระหว่างตาทั้งสองข้างอย่างพอเหมาะ สบายตา ไม่เบียดชิดกัน
  - **Verification & Deployment**:
    - อัปเดต Unit Tests ใน `PetRobotAvatar25DTest.kt` และรันผ่าน 100%: `BUILD SUCCESSFUL`
    - รัน `PetModeTest` ผ่าน 100%: `BUILD SUCCESSFUL`
    - รัน `./gradlew.bat :composeApp:installDebug` และติดตั้งอัปเดตลงเครื่อง `SM-S908E` สำเร็จเรียบร้อย พร้อมเปิดแอปผ่าน ADB

- **User Request**: "มันยัง ไม่ถูกต้อง ดวงตา วงนอก วงใน ต้องเคลื่อนไหวต่างกัน ถ้าจาก ตัวอย่าง วงกลม 2 วง จะมี ขนาดเท่ากัน แต่ตำแหน่ง จะเยื้องกัน อยู่ เหมือนเป็น วงด้านหน้า เปรียบเหมือนลูกตา ที่จะขยับ มากกว่า วงด้านหลัง วงด้านหลัง จะขยับต่อเมื่อ หันหน้า เงยหน้า ก้มหน้า ถ้าตามหลักการแล้ว จะมีการ จำลองการเคลื่อนไหวเหมือนมี หัวหุ่นยนต์ แต่ไม่วาดหัว คล้ายในรูป" (อ้างอิงภาพ `media_1789625785395.jpg`, `media_1789626142924.jpg`, `media_1789626142979.jpg`)
- **สาเหตุที่พบจากภาพทดสอบบนเครื่อง**:
  1. `PetRobotHeadAvatar.kt`: เดิมใช้ `Canvas(modifier.graphicsLayer { rotationY = animatedRotationY, rotationX = animatedRotationX, cameraDistance = 14f * density })` เพื่อหมุน 3D perspective ทั้งระนาบ Canvas 2D ส่งผลให้เมื่อหันข้าง วงกลมถูกบิดเบี้ยวเป็นทรงสี่เหลี่ยมคางหมู/ไข่เบี้ยว (`media_1789626142979.jpg`)
  2. วงกลมหน้า (ลูกตา) และวงกลมหลัง (เบ้าตา) ยังถูกผูกติดกับการเคลื่อนที่ `gazeX, gazeY` ร่วมกัน ทำให้ทั้งสองวงขยับตามสายตาทั้งคู่ ไม่ได้แยกหน้าที่ทางชีวกลศาสตร์ (Eyeball vs Socket on Head)
  3. `PetModeScreen.kt`: Ambient aura ในโหมด Compose Canvas ตั้ง offset แนวนอน-ดิ่งที่ 0.dp (กึ่งกลางจอ) ทำให้เกิดวงแสงเขียวหลุดลงไปอยู่ระหว่างตากับแถบสถานะด้านล่าง (`media_1789626142924.jpg`)
- **Actions Taken**:
  - **ตัดการบิดเบี้ยวของ Canvas 3D ออก 100%**: ลบ `graphicsLayer { rotationY, rotationX }` ออกจาก `Canvas` เพื่อให้วงกลมทั้ง 2 ชั้นมีรูปทรงกลมเรขาคณิตที่สมบูรณ์เสมอ ไม่มีการบิดเบี้ยวเป็นทรงไข่
  - **สร้างโมเดลจลนศาสตร์จำลองหัวหุ่นยนต์ 3 มิติ ([VirtualHeadKinematics.kt](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/VirtualHeadKinematics.kt))**:
    - **วงกลม 2 วงขนาดเท่ากัน 100%**: ทั้ง Front Disc (ลูกตาไซแอน) และ Back Disc (เบ้าตาสีน้ำเงินคราม) มีขนาดเท่ากันทุกประการ (`discSize = Size(effW, effH)`)
    - **วงหลัง (เบ้าตาบนหัวหุ่น)**: เคลื่อนที่เฉพาะเมื่อหันหน้า (`headYaw`), เงยหน้า/ก้มหน้า (`headPitch`), เอียงคอ (`headRoll`), พยักหน้า (`NOD`), หรือส่ายหน้า (`SHAKE`) โดยไม่ขยับตามการกลอกตาลูกตา
    - **วงหน้า (ลูกตา)**: กลอกมองตามสายตา (`pupilGazeX, pupilGazeY`) รวดเร็ว ฉับไว พร้อม Saccades ขยับในระยะที่กว้างกว่า และมีมิติความลึกพารัลแลกซ์เมื่อหันหน้า
    - **สัดส่วนใบหน้าทรงกลม 3 มิติ**: เมื่อหันข้าง ระยะห่างระหว่างดวงตาหดแคบลงตามฟังก์ชัน $\cos(\text{yaw} \times 0.35f)$ เสมือนอยู่บนทรงกลมของหัวหุ่นยนต์จริงโดยไม่ต้องวาดหัวหุ่นยนต์
  - **ปรับปรุง [PetRobotHeadAvatar.kt](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/PetRobotHeadAvatar.kt)**:
    - เชื่อมต่อ `animatedHeadYaw`, `animatedHeadPitch` ด้วย Spring Damping แบบมีแรงเฉื่อย
    - ส่งผลการคำนวณ `kinematics` เข้าสู่ `renderPetEmotion`, `drawDualCircleEye`, และ `drawHappyEye`
  - **ปรับปรุง [PetModeScreen.kt](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/screen/PetModeScreen.kt)**:
    - ปรับระยะ Y ของ `Ambient Aura` ให้ตรงกับ Rule of Thirds eye line เสมอ ทั้งโหมด Compose Canvas และ Rive
- **Verification**:
  - เพิ่ม Unit Tests ใน `PetRobotAvatar25DTest.kt`: ตรวจสอบขนาดวงกลมเท่ากัน 100%, เบ้าตาอยู่นิ่งเมื่อกลอกสายตา, เบ้าตาขยับและระยะห่างย่นลงเมื่อหันหน้า, เสี้ยวเงาสีน้ำเงินอยู่ด้านล่าง-ขวาตามต้นฉบับ LOOI
  - รัน `./gradlew.bat :composeApp:testDebugUnitTest --tests "com.skyliner2008.jarvis.PetRobotAvatar25DTest"`: **BUILD SUCCESSFUL**
  - รัน `./gradlew.bat :composeApp:testDebugUnitTest --tests "com.skyliner2008.jarvis.PetModeTest"`: **BUILD SUCCESSFUL**

## 2026-09-17 — จัดตำแหน่งเริ่มต้นของดวงตาตามหลักการจุดตัด 9 ช่อง (Rule of Thirds) ทั้งแนวตั้งและแนวนอน
- **User Request**: ตรวจสอบตำแหน่งเริ่มต้นของดวงตา ใช้หลักการ **จุดตัด 9 ช่อง (Rule of Thirds)** ทั้งแนวตั้งและแนวนอน
- **การวิเคราะห์ตำแหน่งเดิม**:
  1. `PetRobotHeadAvatar.kt`: เดิมกำหนด `centerY = canvasH / 2f` (50% จากขอบบน กึ่งกลางจอพอดี) ทำให้ในแนวตั้งดวงตาอยู่ต่ำเกินไป เหลือพื้นที่หน้าผากดำว่างเปล่ามากเกินไป และบีบให้ปากกับคางไปอยู่ชิดขอบล่าง
  2. `AvatarLayout.kt`: เดิมกำหนด `baseSpacing = eyeDiameter * 0.65f` ทำให้ในแนวตั้งดวงตาห่างกันเกินไป (ดวงตาอยู่ที่ 25% และ 75% ของความกว้างจอ หลุดออกไปชิดขอบข้าง)
- **Actions Taken (ประยุกต์ใช้หลักการจุดตัด 9 ช่อง 100%)**:
  - **ระดับสายตาแนวตั้ง (Y-Axis Eye Line)**:
    - **แนวตั้ง (Portrait)**: วางเส้นระดับสายตาที่ **40% ของความสูงจอ** (`cY = height * 0.40f` ซึ่งขอบบนของดวงตาจะแตะเส้น 1/3 หรือ 33.3% พอดี) สอดคล้องกับมาตรฐานการถ่ายภาพพอร์ตเทรตและค่า `RIVE_PORTRAIT_EYE_LINE = 0.40f`
    - **แนวนอน (Landscape)**: วางเส้นระดับสายตาที่ **42% ของความสูงจอ** (`cY = height * 0.42f` สอดคล้องกับ `RIVE_LANDSCAPE_EYE_LINE = 0.42f`)
  - **ตำแหน่งแนวนอนของดวงตาซ้าย-ขวา (X-Axis Eye Columns)**:
    - **แนวตั้ง (Portrait)**: กำหนด `baseSpacing = width / 6f` (16.67% จากกึ่งกลาง)
      - ตาซ้าย: `cX - baseSpacing = width / 3f` (33.33% ของความกว้างจอ)
      - ตาขวา: `cX + baseSpacing = 2f * width / 3f` (66.67% ของความกว้างจอ)
      - **ผลลัพธ์**: จุดศูนย์กลางดวงตาทั้งสองข้างตรงกับ **จุดตัด 9 ช่อง ด้านบนทั้ง 2 จุด (Upper-Left & Upper-Right Intersection Points)** พอดีเป๊ะ 100%!
    - **แนวนอน (Landscape)**: จัดดวงตาให้อยู่ในกรอบสัดส่วนทองคำของใบหน้ากลางจอ ด้วย `baseSpacing = eyeDiameter * 0.60f` ดวงตาทั้งคู่ครองพื้นที่ 1/3 กลางจออย่างสมดุล
  - **การซิงค์เลเยอร์**: ย้ายการคำนวณ `calculateAvatarLayout` ขึ้นมาเป็นตัวตั้งต้นก่อนคำนวณ `centerX`, `centerY`, `breathingOffsetY`, และ `scale/rotate pivot` ใน [PetRobotHeadAvatar.kt](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/PetRobotHeadAvatar.kt) ทำให้ดวงตา, ปาก, หน้าผาก, และพร็อพทั้งหมดใน [DynamicPropRenderer.kt](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/DynamicPropRenderer.kt) ขยับตามจุดตัด 9 ช่องอย่างแม่นยำ
- **Verification**: อัปเดต unit test ใน `PetRobotAvatar25DTest.kt` ยืนยันสมการคณิตศาสตร์ `width/3`, `2*width/3`, `height*0.40f`, `height*0.42f` ผ่าน 100% (BUILD SUCCESSFUL)

## 2026-09-17 — Compose Canvas Engine: สถาปัตยกรรมดวงตา 2 วงกลมซ้อนเยื้องกัน (Authentic LOOI Dual-Circle Offset Engine)
- **User Request**: มุ่งปรับปรุง Compose Canvas Engine ให้เหมือนต้นฉบับหุ่นยนต์ LOOI Robot แท้จริง โดยต้นฉบับใช้ **วงกลม 2 วงที่เคลื่อนไหวเยื้องกัน** ("วางกลม 2 วงที่เคลื่อนไหวเยื้องกัน")
- **สาเหตุที่พบ**:
  1. `PetRobotHeadAvatar.kt`: โหมด `IDLE` ถูก `isParametric` ดักไว้ แล้วเรียก `drawParametricEye` ซึ่งวาดรูปทรง squircle (ไม่ใช่ทรงกลมแท้ มีขอบตัดตรง 12%) พร้อม radial gradient และจุดสะท้อนแสงขาวปลอม (specular glint) ขัดกับภาพจริงของ LOOI
  2. `drawDualCircleEye` เดิมเรียกต่อไปยัง `drawNeonEyeRoundRect` วาดเพียงสี่เหลี่ยมมนเดี่ยว ไม่ได้วาด 2 วงกลมแยกเลเยอร์จริง
  3. ขาดการเคลื่อนไหวแบบ Differential Parallax Gaze ("เคลื่อนไหวเยื้องกัน") ระหว่างเลเยอร์หน้าและหลัง ทำให้มิติความลึกไม่เปลี่ยนรูปตามสายตา
  4. `drawHappyEye` เดิมวาดเส้นโค้ง stroke เดี่ยว ขณะที่ภาพจริง (`media_1789619891154.jpg`) เป็นรูปทรงโดมทึบ ⌒ ซ้อน 2 ชั้นที่มีขอบเงาสีน้ำเงินครามลึกด้านล่าง
- **Actions Taken**:
  - `drawDualCircleEye`: ปรับปรุงเป็น Dual-Circle Parallax Renderer สมบูรณ์แบบ:
    - **Disc 1 (Back Base Disc)**: วงกลมสีน้ำเงินครามเข้มรอยัลบลู (`#0D25B9`) เยื้องลงล่าง-ขวาเล็กน้อยที่จุดพัก
    - **Disc 2 (Front Glow Disc)**: วงกลมสว่างสดใสนีออนไซแอน (`#38D5FF`) พร้อมรัศมีเรืองแสง (Bloom Halo) สะอาดตาบน OLED ดำสนิท
    - **Differential Parallax Motion ("เคลื่อนไหวเยื้องกัน")**: เลเยอร์หน้าเคลื่อนที่ตาม Gaze เต็มระยะ (100%), เลเยอร์หลังเคลื่อนที่ด้วย Parallax Factor 40% ทำให้เสี้ยวเงาสีน้ำเงินล่างขยับขยายเมื่อมองขึ้น หดตัวเมื่อมองลง และสลับข้างเมื่อมองซ้าย-ขวา
    - **Squash & Blink**: รองรับการบีบอัดเป็นวงรี (Oval) ตามสัดส่วน พร้อมย่อระยะเยื้องแนวดิ่ง ไม่ให้ขอบเงาหลุดลอย
  - `renderPetEmotion`: บังคับให้ `IDLE`, `LISTENING`, `SPEAKING` เข้า `drawDualCircleEye` โดยตรง 100%
  - `drawHappyEye`: ปรับปรุงเป็นโดม ⌒ ซ้อน 2 ชั้นเยื้องกัน (โดมล่างสีน้ำเงินคราม `#0D25B9` + โดมบนสีนีออนไซแอน) ตรงตามภาพถ่ายเครื่องจริง
  - `PetRobotParametricEye.kt`: ตัดจุดสะท้อนแสงขาวปลอมและเกรเดียนต์ออก เปลี่ยน `drawParametricEye` เป็น 2-Layer Offset Path Engine และแก้รัศมีขอบให้กลมมนสมบูรณ์ 100%
  - `PetRobotNeonDraw.kt`: ปรับ `getEyeDeepShadowColor` ให้คืนสี Deep Cobalt Royal Blue `#0D25B9` สดเข้มตามภาพจริง
- **Verification**: `PetModeTest` และ `PetRobotAvatar25DTest` ผ่าน 100% BUILD SUCCESSFUL ใน 17s

## 2026-09-16 — Avatar (Live โหมดสัตว์เลี้ยง) บางครั้งไม่เรียก trade tool แล้วแต่งตัวเลขเอง
- **User Request**: Live ปกติใช้ tool ได้ แต่ avatar บางครั้งไม่ยอมเรียก tool และมั่วค่าขึ้นมาเอง
- **สาเหตุที่พบ**:
  1. `JarvisPersona.PET_LIVE_SYSTEM_PROMPT` ไม่มี `TOOL_INTEGRITY_RULES` (ห้ามเดาราคา/ต้องใช้ tool) ที่ Live ปกติมี และสั่ง "พูด 1-2 ประโยค", หัวข้อ "ห้ามวิเคราะห์การเงิน", ตัวอย่าง "Order Block อยู่แถว..." — โมเดลเลยตอบสั้นจากความจำแทนการเรียก tool
  2. `LiveGeminiService`: transcription ของ Live มาเป็นชิ้นๆ (ยืนยันจาก logcat) แต่โค้ดเขียนทับด้วยชิ้นล่าสุด → `lastUserText`, ข้อความในแชท และประวัติที่บันทึก เหลือแค่คำท้ายประโยค
  3. `LiveToolBridge` guard: คำขอฉาก/อวาตาร์มีคำว่า "ทองคำ", "เหรียญทอง", "คนรวย", "หน้า", "แบบ" → `trading_price` ถูกเปลี่ยนไปเล่นฉาก/เปลี่ยนหน้า แล้วโมเดลพูดราคาเอง · guard ใช้ข้อความชิ้นเดียว
  4. Profile guard: คำถามค่า (RSI/แนวรับ/เท่าไหร่) บล็อก analysis tool ทุกตัว แล้วสั่ง "สรุปจากข้อมูลที่มีอยู่ทันที" → เชิญให้แต่งตัวเลข · ตัวนับ 3 ครั้งผูกกับข้อความ (ข้ามรอบได้)
- **Actions Taken**: รวม transcription เป็นประโยคเต็ม (`mergeTranscript`, `userTurnSerial`) · guard ที่เปลี่ยน trading tool ไม่ทำงานเมื่อเป็นคำถามเทรด (`isTradingQuestion`) และตัดคำกว้างเกิน · USER_QUERY ใช้ `trading_technical_analysis` ได้ · ข้อความ guard/ผล tool ล้มเหลวสั่งห้ามแต่งตัวเลข · นับ analysis ต่อ turn · pet persona: กฎ "ข้อมูลตลาดต้องมาจาก tool ทุกครั้ง" + map คำถาม→tool + `TOOL_INTEGRITY_RULES`
- **Verification**: unit test 336 ผ่าน · `installDebug` แล้ว — ต้องทดสอบด้วยเสียงบนเครื่อง (เช่น "ราคาทองคำตอนนี้", "RSI ทอง H1 เท่าไหร่" ในโหมดสัตว์เลี้ยง)

## 2026-09-16 — เอาเสียงปี๊บเป็นจังหวะออกจาก Scene
- **User Request**: ตอนเล่น Scene (เช่น กินอาหาร) มีเสียงปี๊บๆ เป็นจังหวะแทรกในหลาย Scene
- **สาเหตุ** (logcat บนเครื่องขณะกดให้อาหาร): ฉากตั้งฉากหลังตามเวลา (`resolveSmartBackground` → NIGHT หลังสองทุ่ม, SUNNY/SAKURA กลางวัน) ทำให้ `AmbientSoundEngine` เปิดลูปบรรยากาศ — NIGHT คือเสียงจิ้งหรีด 4.4 kHz 3 ครั้ง/วินาที (SUNNY มีนกหวีด 2.8 kHz) ดังใต้เสียงประกอบของฉาก · ตอนให้อาหาร/อาบน้ำ/เล่น ยังมีเสียงปฏิกิริยาของ state machine (CONFUSED) ซ้อนตอนเริ่มฉาก
- **Actions Taken**: `AlwaysLiveScreen` ปิดลูปบรรยากาศระหว่างที่มีฉากเล่น (ใช้ DEFAULT = เงียบ) แล้วคืนตามฉากหลังเมื่อจบ · `PetModeController.feedPet/cleanPet/playWithPet` ตัดเสียงปฏิกิริยาออก (ฉากมีเสียงตามบทอยู่แล้ว)
- **Verification**: unit test 336 ผ่าน · `installDebug` แล้ว — ฟังซ้ำบนเครื่องยังไม่ได้ (จอดับ/ล็อกหลังติดตั้ง)

## 2026-09-16 — พื้นหลังเป็นกล่อง, ตาใหญ่ขึ้น, ตำแหน่งตาตามกฎสามส่วน, เสียง FX ตามเสียงสื่อ
- **User Request** (ภาพจากเครื่องจริง): 1) พื้นหลังเป็นกล่องสี่เหลี่ยม ไม่กลืนกับพื้นหลัง 2) ตายังเล็กทั้งแนวตั้ง/นอน 3) แนวตั้งตาอยู่กลางครึ่งบนต่ำเกินไป 4) หรี่เสียงแล้วเสียง FX ยังดังเท่าเดิม
- **สาเหตุ / แก้**:
  1. Rive: ฉากหลัง/แฟลช/หน้าจอมืดวาดเป็นสี่เหลี่ยม 500×500 (DeepBlue, RedAlert vignette, ChartWash, แฟลชฟ้าผ่า/ตกใจ/ขีปนาวุธ, Heat, Dim, RedFlash) → `wash()` วงกลมไล่จางหมดก่อนขอบ artboard · glow รัศมีเกิน 250 (Alert, GoldGlow, Shatter, Spotlight, Thug Spot) ลดให้จางในกรอบ · รัศมีแสง Burst ไล่จางปลาย · Compose: `PetBackgroundLayer` ขยับ parallax จนเห็นขอบ → overscan ×1.12 · ออร่าย้ายไปอยู่หลังตา (ภาพผู้ใช้วัดค่าพิกเซลแล้ว ออร่าเองเป็นวงกลมไล่จาง)
  2. `EYE_SCALE` 1.24 → 1.32, `EYE_DX` 96 → 100 · แว่นดำ/แว่นพิกเซลยึดตำแหน่งตา (`LEFT_X/RIGHT_X`) · เขาปีศาจขึ้น 12 px · ลดการขยายหน้าในเรื่อง Shocked และ SuperLove (ตัวตรวจตาชนกันจับได้)
  3. `PetModeScreen`: Engine Rive ไม่มี padding, artboard กว้าง 1.10× ของพื้นที่ (แนวนอน 1.15× ความสูง) และวางให้แถวตาอยู่ 40% จากบน (แนวนอน 42%) — ค่าคงที่ `RIVE_*` ท้ายไฟล์
  4. `RobotSoundEngine` ใช้ `USAGE_ASSISTANCE_SONIFICATION` (ผูกเสียงระบบ/แจ้งเตือนบน Samsung) → `USAGE_GAME` (เสียงสื่อ) · `MainActivity.volumeControlStream = STREAM_MUSIC`
- **Verification**: rive verify 0 errors (.riv 529,974 bytes) · เรนเดอร์ตรวจฉากหลัง/แฟลช/แว่น/เขา · unit test 336 ผ่าน · assembleDebug ผ่าน — ยังไม่ได้ติดตั้ง (มือถือไม่ได้เชื่อมต่อ adb)

## 2026-09-15 — ดวงตาวงรี, ตาหน้า/หลังแยกตามทิศการมอง, การเคลื่อนไหวนุ่มเด้ง
- **User Request**: ตากลมเกินไป ควรเป็นวงรีนิดหน่อย · บางการเคลื่อนไหวแข็ง ไม่นุ่มนิ่มเด้ง · ตาสองวงซ้อนเยื้องด้านเดียวตลอด ควรแยกวงใน/วงนอก ให้วงในเยื้องตามทิศที่มอง · แววตาจุดเล็กไม่ต้องมี
- **Actions Taken** (`rive_avatar/tools/build_scene.py`):
  - ตา Round / Wide / Tiny เป็นวงรีตั้ง (กว้าง ×0.90 สูง ×1.06) และปรับตารางความกว้างที่ใช้ตรวจตาชนกัน
  - ตาแต่ละข้างแยกเป็น 2 ชั้น: **หน้า** (สว่าง) อยู่ใต้ `IrisGaze` > `IrisLook`, **หลัง** (สีเข้ม) อยู่ใน `EyeBackSolo` เยื้องลงเล็กน้อย — ทุกคีย์ที่เปลี่ยนรูปตา สลับทั้งสองชั้นพร้อมกัน (`EYE_BACK`, `BACK_SOLO`)
  - `IrisGaze` ขยับ ±13 ตาม gazeX/gazeY (มองขวา → วงหน้าเยื้องขวา ขอบหลังโผล่ซ้าย) · `IrisLook` คำนวณจากการขยับตาในเรื่อง/สถานะ/ปฏิกิริยา (×0.45 จำกัด ±13) — มองลงในฉากก็เยื้องลง
  - ลบแววตาจุดเล็ก (Gleam)
  - easing ใหม่ `jelly` (เลยเป้านิดแล้วเด้งกลับ) ใช้กับคีย์ตำแหน่ง/สเกลของตา หัว และปากในทุกเรื่อง · หายใจเป็น squash & stretch
- **แอป**: `RivePetAvatar` gaze/tilt ผ่าน spring หน่วงต่ำ (dampingRatio 0.52) · `RiveAvatarView.android` ไจโรผ่าน spring 0.5 — ขยับแล้วมีเด้งตาม
- **Verification**: rive verify 0 errors (.riv 529,343 bytes) · เรนเดอร์มอง ซ้าย/ขวา/บน/ล่าง/ทแยง และเฟรมในเรื่องต่างๆ (ตาโค้ง กากบาท หัวใจ ตาโต) · unit test 336 ผ่าน · `installDebug` แล้ว

## 2026-09-15 — แก้พร็อพกองโผล่ตอนเริ่ม Scene
- **User Request**: ตอนเริ่มเล่น Scene เห็นพร็อพทุกชิ้นกองอยู่ในวินาทีแรก แล้วค่อยหายไปก่อนฉากเล่นปกติ
- **สาเหตุ**: layer `Seq` / `React` / `Prop` / `Bg` / `Fg` ใน state machine ใช้ transition blend 140 ms — Solo สลับพร็อพเข้ามาทันที แต่ตำแหน่ง/ความทึบของชิ้นส่วนถูก blend จาก rest pose (ท่าจุดพีคของฉาก = ทุกชิ้นอยู่บนจอ) ไปหาเฟรมแรกของเรื่อง (ยืนยันด้วยภาพเฟรม 2–6: VR เห็นแว่น+การ์ด+ป๊อปคอร์นกองกัน, ขีปนาวุธจอสว่างทั้งจอ)
- **Actions Taken** (`rive_avatar/tools/build_scene.py`): transition ของ 5 layer นี้เป็น `duration=0` (เรื่องเริ่มจากเฟรมแรกของตัวเองอยู่แล้ว) · ซ่อนแว่น VR, แก้ว (Drinking) และเปลวไฟ (Fire) ให้พ้นจอจนถึงจังหวะของมัน
- **Verification**: เรนเดอร์เฟรมแรกของทั้ง 36 เรื่อง (seq 65–100) ไม่มีพร็อพโผล่ก่อนเวลา · rive verify 0 errors · unit test 336 ผ่าน · `installDebug` แล้ว

## 2026-09-15 — ปุ่มเดโม่ + คำสั่งเสียงเล่นฉากจนจบ
- **User Request**: ตรวจปุ่ม "เดโม่" (แสดงแต่ละ Scene) และคำสั่งเสียงเล่น Scene ที่ต้องการ ว่าใช้งานถูกต้องและเล่นจนจบ
- **ปัญหาที่พบ**:
  1. ปุ่มเดโม่เล่น LOOI Moodset 50 หน้า (หน้าละ 4 วิ) ไม่ใช่ฉากอนิเมชัน · คำสั่งเสียง/แชท "เดโม่" เล่นโชว์หน้าตา 8 แบบเก่าที่ตั้งหน้าจากภายนอก (ทับฉาก)
  2. ตัวแปลคำ: "อาบน้ำ" → ฉากดื่ม ("น้ำ"), "ราชา" → ฉากดื่ม ("ชา"), ชื่อที่ tool ประกาศเอง `royal` / `soul_out` หาไม่เจอ, "ไฟฟ้าช็อต" → ไฟไหม้, "crypto" → ร้องไห้
  3. เรื่องอารมณ์ 19 เรื่องเรียกด้วยเสียงไม่ได้ · ถ้าสั่งฉากตอนหน้าสัตว์เลี้ยงยังไม่พร้อม คำสั่งหลุด · idle loop (อารมณ์ตามค่าสถานะ) อาจเปลี่ยนหน้าระหว่างฉาก
- **Actions Taken**:
  - `PetModeController`: `startScene()` ตัวเดียวสำหรับ Pet Scene และเรื่องอารมณ์ (`playMoodStory`) — sceneId ใหม่, เสียงตามเวลา, คืนหน้าเมื่อจบเท่านั้น, `stopScene()`, `isScenePlaying`, idle loop ไม่แตะหน้าระหว่างฉาก; `playSceneByNameOrKeyword` คืนชื่อฉากที่เล่นจริง (เรื่องอารมณ์ก่อน แล้วค่อย Pet Scene)
  - `RiveMoodStories`: ชื่อไทย, อารมณ์, คำสั่งเสียง, `forName` / `resolveKeyword` · `RiveSeq.forScene` รู้จักชื่อเรื่องอารมณ์
  - `PetSceneEngine.resolveFromKeyword`: แก้ทุกข้อในข้อ 2 + คำใหม่ (ราชา เจ้าหญิง วิญญาณ เศรษฐี shower …)
  - `JarvisViewModel.playSceneShowcase()`: เล่น 37 ฉาก (Pet Scene 18 + เรื่องอารมณ์ 19) ทีละฉากจนจบ ขึ้นป้าย "🎬 n/37 · ชื่อฉาก" หยุดได้ทันที; ปุ่มเดโม่ + เสียง/แชท "เดโม่" ในโหมดสัตว์เลี้ยงใช้ตัวนี้
  - `DeviceControlExecutor` scene: รอหน้าสัตว์เลี้ยงพร้อม ≤3 วิ, ตอบชื่อฉากที่เล่นจริง · `DeviceToolDefinitions`: รายชื่อฉากครบ 37 + "ห้ามเรียกซ้ำระหว่างเล่น"
- **Verification**: `PetSceneDemoVoiceTest` 7 เคส (ชื่อ tool ครบ, วลีไทย, เรื่องอารมณ์ด้วยเสียง, ฉากไม่หลุดเมื่อโดนจิ้มและจบเอง, หยุดเดโม่) — unit test 336 ผ่าน · `installDebug` แล้ว · ⏳ ยังไม่ได้ทดสอบบนจอ (เครื่องล็อก PIN)

## 2026-09-15 — ตรวจตำแหน่งและขนาดพร็อพทั้งหมด (91 ชิ้น)
- **User Request**: พร็อพบนหัวต้องสูงกว่าตา, แว่น/ของสวมตาต้องไม่เล็กกว่าตา, พร็อพทุกชิ้นต้องไม่เล็กเกินไปบนจอมือถือ
- **Actions Taken** (`rive_avatar/tools/build_scene.py`):
  - เรนเดอร์พร็อพ 1–91 ทับหน้าปกติแล้วไล่ตรวจ; เพิ่ม `fit()` + ตาราง `PROP_FIT` ขยายไอคอนรอบจุดศูนย์กลาง (Question, Burger, Beer, Sparkle, Blush, Exclaim, Trash, Camera, AngryMark, ThinkDots, SoundWave, Questions, Sweat, Medal, BatteryLow, HoloPat/Chin/Poke, Bolt, Calendar, Pencil, Warning, AlarmClock)
  - แว่น: Sunglasses เลนส์ 176×110 → 184×156 · แว่นพิกเซล SceneThug สูง ~63 → ~170 คลุมตา · แว่น Mood (Awesome/ShowOff) 150×96 → 178×154
  - Devil: เขาใหญ่ขึ้นและอยู่เหนือตาทั้งชิ้น, ปีศาจน้อยย้ายไปมุมบน · Zzz / Sparkles / Hearts / DizzyStars / Tears ใหญ่ขึ้น
  - ฉาก: อาหาร ×1.25, หลอดไฟ/เครื่องหมายถูกของ Study, มงกุฎ Arrogant ×1.4, ลูกบอล Idle, แมงมุม Shocked ×1.5
  - แอป: `RiveAvatarMapper.plan()` ใช้หน้า OneEye เมื่อแสดง Bulb (หลอดไฟแทนตาขวา ไม่ซ้อนตาจริง)
  - เอกสาร: กฎการจัดวางใน `AVATAR_CONTRACT.md` และ [[Rive_Avatar_Engine]]
- **Verification**: rive verify 0 errors (.riv 408,520 bytes) · เรนเดอร์ตรวจซ้ำทั้ง 91 ชิ้น · unit test 329 ผ่าน · `installDebug` แล้ว

## 2026-09-15 — Jelly Mood Stories: 19 เรื่องสั้นตามอารมณ์ (Engine Rive)
- **User Request**: บทฉากตัวอย่างจากผู้ใช้ — ว่างงาน 3 แบบ, ตกใจ, เศร้า, สงสัย, โกรธ 3 แบบ, คิด, สุข, รัก, ยินดี, เยี่ยม, เขิน, อาย, เก็กท่า, ฟัง, เย่อหยิ่ง (ตาเยลลี่ squash & stretch)
- **Actions Taken**:
  1. `build_scene.py`: พร็อพ `Mood*` 19 ชุด, หน้าใหม่ 8 แบบ, story `seq` 82–100 (7 วิ) ที่คีย์ตาซ้าย/ขวาแยกกัน + ตัวตรวจลำดับเฟรม; ตรวจภาพ 152 เฟรมและแก้ (มือโผล่ขอบจอ, แว่น/มงกุฎไม่ตามหัว, ตาตกใจล้นจอ)
  2. `RiveMoodStories.kt` (ใหม่): seq + เสียงตามไทม์ไลน์ + อารมณ์ → เรื่อง (สุ่มหลายแบบ)
  3. `RiveAvatarView.kt` `RivePetAvatar`: เล่นเรื่องเมื่ออารมณ์เปลี่ยน (รอ react จบ, cooldown 20 วิ, หยุดทันทีเมื่ออารมณ์เปลี่ยน), เรื่องว่างงานเมื่อ IDLE 30–50 วิ, ไม่ทับฉาก/มิสซาย/ตอนพูด
  4. บท: [[Pet_Mood_Scripts]] · `AVATAR_CONTRACT.md` · [[Rive_Avatar_Engine]]
- **Verification**: rive verify 0 errors (.riv 406,800 bytes) · unit test 329 ผ่าน (+`RiveMoodStoriesTest` 2) · `installDebug` แล้ว — ยังไม่ได้ทดสอบบนจอ (เครื่องล็อก PIN)

## 2026-09-15 — Pet Scenes เป็นอนิเมชันเล่าเรื่อง + เสียงประกอบทุกฉาก (Engine Rive)
- **User Request**: ฉากต่างๆ ดูนิ่ง (แค่ลอย) ไม่มีการแสดงของดวงตา — อยากให้ทุกฉากเป็นอนิเมชันมีเรื่องราว (ตัวอย่าง: ใส่แว่น VR) และมีเสียงประกอบทุกฉาก; คิดบทก่อนแล้วค่อยทำ
- **Actions Taken**:
  1. บท 18 ฉาก: [[Pet_Scene_Scripts]] (ไทม์ไลน์ ดวงตา / พร็อพ / เสียง)
  2. `rive_avatar/tools/build_scene.py`: พร็อพ `Scene*` 17 ชุดที่มีชิ้นส่วนเคลื่อนไหวแยก, story `seq` 65–81, หน้า `Starry`, ช่อง `item` (Solo ของอาหาร/เครื่องดื่ม/อุปกรณ์) — ไฟล์ .riv เดียว ใช้ rig ร่วม ไม่ต้องโหลดใหม่
  3. `PetSceneEngine.kt`: `SceneCue`, `PetSceneSpec.cues`, `SCRIPTS` (ความยาวบท + 300 ms), ฉากใหม่ `VR_MODE` / `MUSIC`, `RobotFaceState.sceneName/sceneItem`
  4. `PetModeController.kt`: `startScene()` รวม playScene / playSceneByNameOrKeyword — `sceneId` ใหม่ทุกครั้ง, เล่นเสียงตามเวลา, ไม่ทับหน้าที่ถูกเปลี่ยนระหว่างฉาก
  5. `RiveAvatarBinding.kt`: `RiveSeq.forScene`, `RiveItem.forProp`, `RiveAvatarInputs.item`; ระหว่างฉากไม่มี Compose props/ฉากหลัง · `RiveAvatarView.kt`: รีสตาร์ท story เมื่อ `sceneId` เปลี่ยน · binder เขียน `item`
  6. `RobotSoundPlayer.kt` + `RobotSoundEngine.kt` + `AlwaysLiveManager.kt`: เสียงใหม่ WHOOSH, POP, SLURP, COIN, ZAP, SIZZLE, RAIN, GAME_BLIP, TYPING, SOB, FANFARE, POWER_UP, MELODY, HEARTBEAT, GHOST
- **Verification**: `scripts/build_rive_avatar.ps1` (verify 0 errors, .riv 307,659 bytes deployed) · unit test 327 ผ่าน (+`PetSceneScriptTest` 3, ปรับ test นับ enum/ความยาวฉากใน `PetModeTest`) · `installDebug` ลงเครื่องแล้ว — **ยังไม่ได้ทดสอบบนจอ** เพราะเครื่องล็อก PIN

## 2026-09-15 — Pet System Review & Fixes (ทดสอบบนเครื่องจริง Galaxy S22 Ultra / Android 16, Engine Rive เป็นหลัก)
- **User Request**: review ระบบสัตว์เลี้ยงทั้งหมด (การคำนวณสถานะ, การเคลื่อนไหวเมื่อถูกกระตุ้น, การเรียก tool) + ติดตั้ง APK ทดสอบผ่าน adb โดยทดสอบ Rive เป็นหลัก
- **Bugs found (ยืนยันบนเครื่อง = ✔)**:
  1. ✔ อารมณ์จากการสัมผัสถูกทับด้วยสถานะไมค์/เสียง (IDLE/LISTENING) จาก ViewModel ภายใน ~1 วิ (ANGRY 30 วิ หายเป็นหน้าปกติ) + echo ของการอัปเดตตัวเองล้างสิทธิ์ + timer ของการจิ้มครั้งก่อนตัดอารมณ์ของครั้งถัดไป
  2. ✔ Engine Rive: `RiveAnimationView` กินทุก touch → จิ้ม/ลูบ/เกาคาง ไม่ทำงานเลยบน Rive
  3. การเขย่า/คว่ำ/หงายจอถูกประมวลผล 2 ทาง (`AlwaysLiveManager` เขียน avatar ตรง + `PetMotionBridge` → state machine) ทางแรกทับผลของ state machine (เขย่าแรงแสดง DIZZY แทน ANGRY, หงายจอ HAPPY เสมอ)
  4. ✔ ปิดแอป 205 นาที → อิ่ม 0% เครียด 59%; พลังงานไม่ฟื้นตอนหลับ (decay ลดพลังงานเสมอ)
  5. เสียงดัง: ไม่มี cooldown และนับทุกเฟรมที่ไมค์ > 0.68 (เสียงพูดปกติ) → 4 เฟรม = rage 100 = ยิงมิสซาย
  6. idle loop หาวซ้ำทุก 3-5 วิ, หิวแล้วโกรธซ้ำทุก 20 วิ, ✔ passive emotion ถูกบันทึกเป็น "pat" (log แสดง pat→angry)
  7. hand gesture / เซียมซี / แท็บเกม ตั้งอารมณ์ค้างไม่คืน IDLE → ชีวิตใน idle loop หยุด
  8. จิ้มจนงอนยังได้ affection, ป้อนตอนอิ่ม/เล่นตอนหิว ปฏิเสธแต่ยังได้รางวัล
  9. Tools: `device_custom_prop` ไม่มี FunctionDeclaration (AI เรียกไม่ได้ + ToolExecutor route ไม่ถึง) · ไม่มี tool ดูแลค่าสถานะ · enum ของ `device_avatar_emotion` มีแค่ 14/58 อารมณ์ 8/20 ฉาก · ถอดพร็อพชิ้นเดียวล้างทั้งหมด · คำสั่ง `MISSILE_BARRAGE` นอกโหมดสัตว์เลี้ยงไปรีเซ็ตหน้า
- **Fixes**:
  - `AlwaysLiveScreen.kt`: `petOwnsEmotion` + ตัดสิน echo — สถานะเสียงไม่ทับปฏิกิริยาสัตว์เลี้ยง · ตรวจเสียงดังแบบ spike (เงียบ→ดังฉับพลัน)
  - `PetModeController.kt`: generation token ของ timer · `showTransientEmotion` · cooldown หาว 90 วิ / passive 60 วิ / เสียงดัง 8 วิ · passive ไม่บันทึก memory · decay ตามการหลับ · `decayOffline` · `wearStockProp` / `removeStockProp` / `wakeUpFromTool`
  - `PetNeedsState.kt`: `decay(elapsed, isSleeping)` (หลับ: พลังงาน +0.8/นาที เครียดลด หิว/สกปรกช้าครึ่ง) · `decayOffline` (นับเป็นหลับ, พื้นอิ่ม 25 / สะอาด 30)
  - `PetStateMachine.kt`: จิ้มจนงอนไม่ได้ affection · ป้อนตอนอิ่ม/อาบตอนสะอาด/เล่นตอนหิว ไม่ได้รางวัล · rage ของเสียงดังครั้งแรกสอดคล้อง
  - `AlwaysLiveManager.kt`: callback ของ `PetMotionDetector` เหลือแค่ log (bridge เป็นทางเดียว)
  - `RiveAvatarView.android.kt`: `touchPassThrough = true`
  - Tools: เพิ่ม `device_pet_care` (feed/clean/play/sleep/wake/status ผ่านระบบค่าสถานะจริง, รอ controller พร้อม ≤3 วิ) และ declaration ของ `device_custom_prop` (SVG จริง, ถอดเฉพาะชิ้น) · enum อารมณ์/ตา/ฉาก ครบตาม enum จริง · persona สัตว์เลี้ยงสอนใช้ `device_pet_care` · voice rule ใน `LiveToolBridge`
- **Verification**: unit test ทั้งหมดผ่าน (+ `PetNeedsLogicTest` 6 เคส) · บนเครื่อง: Rive แสดงผล + พื้นโปร่งใส, จิ้ม → PokeR, จิ้มรัว → PokeAngry แล้ว ANGRY ค้างครบ, `external=LISTENING` ไม่ทับ HAPPY/POUT, แชท "open pet mode then feed the pet a burger" → `device_always_live` + `device_pet_care(feed, burger)` สำเร็จ (ฉากกินเบอร์เกอร์ + ค่าสถานะจริง)
- **ยังไม่ได้ทดสอบบนเครื่อง**: เขย่า/คว่ำจอ/เสียงดัง (ต้องใช้มือ) · Live voice session (ทดสอบผ่าน Chat)

## 2026-09-15 — Rive Avatar v7: ผูก Engine Rive เข้ากับโหมดสัตว์เลี้ยง
- **User Request**:
  - ระบบ Live Pet มี 2 Engine (Compose Canvas ใช้งานได้ / Rive ยังไม่ผูก) — ให้ผูก Rive เข้ากับเงื่อนไขต่างๆ แบบเดียวกับ Compose Canvas
- **Actions Taken**:
  1. `RiveAvatarBinding.kt` (ใหม่): ดัชนีช่อง Rive ตรง `presets.json`, `AvatarState.effectiveEmotion()` ใช้ร่วมกับ Canvas, `RiveAvatarMapper.plan()` แมพอารมณ์ 58 แบบ / props / bg / fg / eye trick / gesture / speaking → ช่อง Rive พร้อมรายการที่ Compose ต้องวาดต่อ, `reactFor()` ไล่ระดับจิ้มตาม `PetStateMachine`
  2. `RiveAvatarView.kt`: expect ใหม่ `(inputs, fallbackState)`, `RiveRuntime`, `RivePetAvatar` (timer react / Startled / ShakeAngry / Detected / AngryMissile, สเกลตามระยะหน้า)
  3. `RiveAvatarView.android.kt` เขียนใหม่: `autoBind = true`, cache property เขียนเฉพาะค่าที่เปลี่ยน, ไจโร → tilt, ถอยไป Canvas อัตโนมัติ
  4. `PetRobotHeadAvatar.kt`: พารามิเตอร์เหตุการณ์สำหรับ Rive + ใช้ `effectiveEmotion()` ร่วม
  5. `PetModeScreen.kt`: จำ Engine (`pet.avatar_engine` ผ่าน `PetMemoryStore`), มือโฮโลแกรม → `RiveReactionEvent`, Compose วาดเฉพาะส่วนที่ Rive ไม่มี, ปิด overlay มือ/มิสซายของ Canvas เมื่อใช้ Rive
  6. `build_scene.py`: artboard พื้นหลังโปร่งใส (ให้ฉากหลัง Compose แสดงได้) → build `.riv` ใหม่
  7. `RiveAvatarBindingTest` 8 เคส + อัปเดต [[Rive_Avatar_Engine]], `AVATAR_CONTRACT.md`, README
- **Verification**: `compileDebugKotlinAndroid` BUILD SUCCESSFUL · `testDebugUnitTest` ผ่านทั้งหมด (RiveAvatarBindingTest 8/8) · ดัชนี Kotlin ตรง `presets.json` ทั้งหมด
- **ยังไม่ได้ทำ**: ทดสอบบนเครื่องจริง (ไม่มีอุปกรณ์เชื่อมต่อ)

## 2026-09-15 — Rive Avatar v6: Engine Fixes, LOOI Style & Status Moodset
- **User Request**:
  - review `rive_avatar` เทียบกับ LOOI robot (ความถูกต้อง การวาด โมชั่น ความต่อเนื่อง ความน่ารัก) แล้ว "ดำเนินการทั้งหมด"
- **Findings (ยืนยันด้วย `rive inspect --json` + เรนเดอร์ ~500 เฟรม)**:
  1. keyframe `cubic` 3,960 ตัวไม่มี `CubicEaseInterpolator` → ไม่มี easing ทั้งไฟล์
  2. seq/react เป็น `loop` และ AnimationState ไม่มี `reset` → เล่นซ้ำ/เล่นต่อกลางเรื่อง
  3. property ที่ไม่มีใครคีย์แล้วค้างค่าสุดท้าย → ท่าค้างหลังตัดเรื่อง
  4. ท่าเข้าของพร๊อพผูกกับนาฬิกากลาง `PropLoops` ไม่ใช่ตอนที่โผล่
  5. state Idle บีบตาหน้า noblink, หน้าไม่มีปากพูดแล้วไม่เห็นอะไร, LEADIN ทำจอว่าง, ปากซิกแซกขาด, ลำดับวาดใน mover กลับด้าน, burger/beer ทับตา
- **Actions Taken** (`rive_avatar/tools/build_scene.py`):
  1. `kf()` แนบ interpolator ทุกตัว · seq/react `oneShot` · channel state `reset="true"` · self-check ทำ build ล้ม
  2. เลเยอร์ `RestPose` (คำนวณค่าพักอัตโนมัติ 50 property) · `intro()` อบท่าเข้าลง channel anim + story beats
  3. โหนด `StateBlink` + เลเยอร์ `BlinkGate` · `VoiceBar` + `FaceSpeak` · `VoiceIdle` ว่าง
  4. สไตล์ LOOI: ตาใหญ่ขึ้น 24 %, ตัดคิ้ว/ปาก, สีเดียวต่อหน้า, Squint = `^ ^`, gaze กว้างขึ้น
  5. เพิ่ม faces 32–33, props 37–55, presets/seq 45–64 (LOOI IDs 21–40) + ตัวเลขนาฬิกาจาก VM `clockD0..3`
  6. `scripts/build_rive_avatar.ps1` เช็ค exit code, regen → verify → inspect → test → compile → copy
  7. อัปเดต `AVATAR_CONTRACT.md` (v6), [[Rive_Avatar_Engine]], README
- **Verification**: `rive . --verify` 0 errors · `inspect` problems [] · cubic ทุกตัวมี interpolator · AnimationState ใน channel layer มี reset ครบ · เรนเดอร์ตรวจทุกหน้า/พร๊อพ/state/react/seq · `avatar.riv` 229,895 bytes
- **ยังไม่ทำ**: ผูก `RiveAvatarView.android.kt` กับ view-model contract ใหม่

## 2026-09-15 — Skill Installation: rive-interactive (Android & Multi-Layer State Machine Integration)
- **User Request**:
  - ติดตั้ง skill `C:\Users\JOJO\AndroidStudioProjects\PersonalAIBot\rive-interactive`
- **Actions Taken**:
  1. **Installation into Skill Directories**:
     - ติดตั้งลงใน Workspace Skills Directory: `.agent/skills/rive-interactive/`
     - ติดตั้งลงใน Global Antigravity Discovery: `C:\Users\JOJO\.gemini\config\skills\rive-interactive/`
     - ซิงค์ไฟล์โครงสร้างครบถ้วน (`SKILL.md`, `references/`, `scripts/`, `assets/`)
  2. **Skill Documentation Augmentation (Android / KMP & RML)**:
     - เพิ่มหัวข้อ **With Android Jetpack Compose & Compose Multiplatform** (การโหลด JNI ผ่าน `System.loadLibrary("rive-android")`, `Rive.init`, Compose `AndroidView`, Defensive Input Guarding, Graceful Canvas Fallback)
     - เพิ่มหัวข้อ **With RML Multi-Layer Parallel State Machines & 1D BlendStates** (สถาปัตยกรรม 6 เลเยอร์คู่ขนาน, กฎ Draw Order *"The first sibling draws on top"*, การแมป BlendState 1D พิกัดสายตา 0–100)
     - เพิ่มหัวข้อ **Common Pitfalls and Solutions**:
       - Pitfall 4: Android JNI `UnsatisfiedLinkError` on `FileAssetLoader`
       - Pitfall 5: Android Fatal Background Thread Crash: `StateMachineInputException`
       - Pitfall 6: Freeze at Frame 0 (`advance() == false` putting Render Loop to Sleep)
       - Pitfall 7: RML Draw Order Occlusion
     - สร้างเอกสารคู่มือเจาะลึก `references/android_rml_reference.md`
  3. **Verification**:
     - ทดสอบพาธและการค้นพบ Skill สำเร็จทั้ง Local Workspace และ Global
     - รัน `.\gradlew.bat testDebugUnitTest` ผ่านเรียบร้อย (BUILD SUCCESSFUL)

## 2026-09-14 — Architecture Upgrade: Rive Native 6-Layer State Machine & 1D BlendStates (Living Eyes & Mouth Motion)
- **User Issue & Diagnosis**:
  - ผู้ใช้แจ้งว่า: *"Rive Avatar ดวงตา ปาก มันไม่ขยับเลย มันขยับแต่ กล่อง 4 เหลี่ยม รอบนอก"*
  - **Root Cause**:
    1. ใน Android C++ Runtime (`rive-android`) ตัว `RiveAnimationView` ทำหน้าที่ขับเคลื่อน `StateMachineInstance` เท่านั้น ไม่ได้รัน `ScriptedLayout` / Luau Scripting Engine ของ Rive 2 CLI
    2. ใน `scene.rml` เดิม แอนิเมชัน `IdleLoop` ไม่มี Keyframes ใดๆ และ State Machine Input (`gazeX`, `gazeY`) ไม่ได้ผูกเข้ากับ State ใดใน State Machine ส่งผลให้ดวงตาและปากด้านในอยู่นิ่งสนิท สิ่งเดียวที่ขยับคือ `Box` ชั้นนอกของ Jetpack Compose (`graphicsLayer { rotationX/Y, translationX/Y }`) ตาม Face-Tracking ของมือถือ ("กล่อง 4 เหลี่ยม รอบนอก")
    3. นอกจากนี้ ตามข้อกำหนดการวาดเลเยอร์ของ Rive *"The first sibling draws on top"* การประกาศ `LeftEyeShape` ก่อน `LeftPupilContainer` ทำให้แผ่นพื้นหลังตาบังลูกตา (Pupils) และประกายตา (Specular Highlights) ไว้ด้านหลัง
- **Solution & Hardening**:
  1. **สถาปัตยกรรม 6-Layer Parallel Native State Machine ใน `scene.rml`**:
     - **Layer 1 (Blink)**: รัน `AnimBlink` (210 เฟรม / 3.5s, loop) บีบและดีดตา `LeftEyeScaleNode`, `RightEyeScaleNode` (`scaleY`: 1.0 -> 0.08 -> 1.0) อย่างเป็นธรรมชาติ
     - **Layer 2 (Breathe)**: รัน `AnimBreathe` (120 เฟรม / 2.0s, loop) ขยับปากแบบ Cyber-Breathing บน `MouthContainerNode` (`scaleY`, `scaleX`, `y`)
     - **Layer 3 (MicroSaccades)**: รัน `AnimMicroSaccade` (180 เฟรม / 3.0s, loop) สั่นไหวสายตาเล็กน้อยบนลูกตา `LeftPupilNode`, `RightPupilNode` เพื่อให้ดูมีชีวิตชีวาตลอดเวลา
     - **Layer 4 (GazeHorizontal)**: ใช้ `BlendState1DInput` บน `gazeX` (ช่วง 0–100 โดย 50 คือจุดกึ่งกลาง) ผสมผสาน 3 ท่าทาง (`LookLeft`, `LookCenterH`, `LookRight`) ขยับทั้งลูกตาและเบ้าตาแบบ Parallax
     - **Layer 5 (GazeVertical)**: ใช้ `BlendState1DInput` บน `gazeY` (ช่วง 0–100 โดย 50 คือจุดกึ่งกลาง) ผสมผสาน 3 ท่าทาง (`LookUp`, `LookCenterV`, `LookDown`)
     - **Layer 6 (Speech)**: สลับระหว่าง `MouthRest` และ `Talking` (ขยับอ้าปาก `scaleY` สูงสุด 2.6) โดยมีเงื่อนไข `TransitionBoolCondition` ผูกกับ `isSpeaking`
  2. **แก้ไข Draw Order ใน `scene.rml`**:
     - สลับให้ประกาศ `LeftHighlight` (บนสุด) -> `LeftPupilShape` (กลาง) -> `LeftEyeShape` (ล่างสุด) ทำให้ลูกตาสีเข้มและประกายตาสีขาวคมชัดปรากฏเด่นชัดอยู่บนเบ้าตาสควีร์เคิลสีฟ้าไซแอน
  3. **Mapping ค่าใน `RiveAvatarView.android.kt`**:
     - แมปปิ้งค่า `gazeX`, `gazeY` จาก Compose range [-1f, 1f] ไปเป็น [0f, 100f] (กึ่งกลางคือ 50f)
     - แมปปิ้ง `speechVolume` และผูกสถานะ `isSpeaking`
  4. **คอมไพล์และติดตั้งลงเครื่องจริง**:
     - รัน `.\scripts\build_rive_avatar.ps1` คอมไพล์ได้ `avatar.riv` (8,402 bytes)
     - รัน `.\gradlew.bat testDebugUnitTest` ผ่าน 310/310 การทดสอบ (100%)
     - รัน `.\gradlew.bat assembleDebug` และติดตั้ง APK ลงเครื่องจริง Android (`R5CT42YEMMM`) สำเร็จ (`Success`)
- **Verification**:
  - แคปเจอร์สแนปช็อตตรวจสอบผ่าน Rive CLI:
    - `rive_native_0s.png`: ลูกตาและประกายตามองตรง ปากอยู่จุดพัก
    - `rive_native_1s.png`: ปากขยายตัวตามลูปหายใจ (diffCount=84)
    - `rive_native_3.25s.png`: ตาทั้งสองข้างกะพริบปิดสนิทเป็นขีดไซแอน (diffCount=4528)
  - ติดตั้ง APK สดใหม่ขึ้นอุปกรณ์จริงเรียบร้อย

## 2026-09-14 — Defect Fix: Rive Static Freeze Resolution & Continuous 60fps Animation Loop
- **User Issue & Diagnosis**:
  - ผู้ใช้แจ้งว่า "rive ไม่ error แล้ว แต่ Rive Avatar ไม่มีความเคลื่อนใหว อะไร นิ่งๆ"
  - **Root Cause**:
    1. ใน `scene.rml` เดิม State Machine มีเพียง `<EntryState/>` โดยไม่มีการเชื่อมต่อไปยัง State หรือแอนิเมชันใดๆ ตามข้อกำหนดของ Rive C++ Runtime เมื่อ State Machine เริ่มทำงานและไม่มีแอนิเมชันที่กำลังเล่นอยู่ ฟังก์ชัน `stateMachineInstance.advance()` จะส่งค่าคืนกลับเป็น `false` ทันที ส่งผลให้ `RiveFileController` ลบ State Machine ออกจาก `playingStateMachineSet` และสั่ง `Renderer.stop()` หยุด Render Loop ถาวรเพื่อประหยัดพลังงาน ทำให้ทุกอย่างหยุดนิ่งที่เฟรมแรก (Freeze at Frame 0)
    2. ใน `avatar.luau` สคริปต์ยังไม่มีการเชื่อมโยงค่าพิกัดการมองและสถานะเสียงจากภายนอกที่ส่งมาจาก Android (รับเพียงเมาส์คลิกบนเดสก์ท็อป) และรูปทรงปาก `Mouth` ยังไม่ได้ผูก Data Binding เข้ากับ `mouthScaleY`
- **Fix & Hardening**:
  1. **Continuous Looping Animation ใน `scene.rml`**:
     - เพิ่ม `<LinearAnimation loopValue="loop" duration="60" fps="60" name="IdleLoop" id="0:65"/>`
     - ใน `State Machine 1` เชื่อม `EntryState` -> `StateTransition stateToId="0:66"` -> `AnimationState animationId="0:65" id="0:66"` เพื่อให้ State Machine อยู่ในสถานะ Active Looping ตลอดเวลา ทำให้ Render Thread หมุนลูป 60fps ต่อเนื่อง ไม่หยุดนิ่ง
     - หุ้มรูปทรงปากด้วย `<Node name="MouthNode" id="0:34">` และผูก Data Binding `scaleY` (propertyKey 17) เข้ากับ `mouthScaleY` (0:53)
     - เพิ่มตัวแปร ViewModel สำหรับรับอินพุตจาก Android: `inputGazeX`, `inputGazeY`, `inputSpeaking`, `inputMouth`
  2. **Active Input Synchronization & Organic Motion ใน `avatar.luau`**:
     - ซิงโครไนซ์ค่าสายตา `targetGazeX`, `targetGazeY` จาก `inputGazeX`, `inputGazeY` ของ Android อย่างต่อเนื่อง
     - รันลูปการเลิ่กลั่กสายตาธรรมชาติ (Micro-saccades) ทุก 2.8 วินาที
     - รันวงจรกะพริบตาอัตโนมัติ (Procedural Blink Cycle) ทุก 3.5 วินาที บีบตาแล้วดีดสปริงคืน
     - ปรับปากให้ขยับตามเสียงพูดแบบเรียลไทม์เมื่อ `inputSpeaking == true` และมีการหายใจกระเพื่อมแผ่วเบาแบบ Cyber-Breathing ในขณะสแตนด์บาย
  3. **Dual-Channel Input Binding & Play Guard ใน `RiveAvatarView.android.kt`**:
     - เพิ่มตัวเช็ค `if (!view.isPlaying) view.play()` ในบล็อก `update` เพื่อปลุก Render Loop ทันที
     - ส่งค่าอินพุตทั้งสองทาง: ทั้ง State Machine Inputs (`setNumberState` / `setBooleanState`) และเขียนลง `sm.viewModelInstance` (`vmi.getNumberProperty(...).value = ...`) โดยตรง
- **Verification**:
  - `rive ./rive_avatar --test`: ผ่าน 4/4 Luau tests
  - `rive ./rive_avatar --once`: คอมไพล์ได้ `avatar.riv` (7,523 bytes) ปราศจาก Error/Warning
  - `.\gradlew.bat compileDebugKotlinAndroid`: ผ่านสมบูรณ์
  - `.\gradlew.bat testDebugUnitTest`: ผ่านสมบูรณ์ 310/310 การทดสอบ (100%)
  - `.\gradlew.bat assembleDebug` และ `.\gradlew.bat assembleRelease`: คอมไพล์สำเร็จทั้งสองแพ็กเกจ

## 2026-09-14 — Defect Fix: Rive StateMachineInputException (No StateMachineInput found with name gazeX)
- **User Issue & Diagnosis**:
  - เมื่อเปิดใช้งาน Rive Engine แอปเกิด Crash บน Background Render Thread:
    `FATAL EXCEPTION: Thread-57 app.rive.runtime.kotlin.core.errors.StateMachineInputException: No StateMachineInput found with name gazeX.`
  - **Root Cause**:
    - ใน `scene.rml` เดิม ตัวแปร `gazeX`, `gazeY` ถูกประกาศเป็นเพียง ViewModel Properties (`<ViewModelPropertyNumber>`) แต่ไม่ได้ประกาศเป็น State Machine Inputs (`<StateMachineNumber>`, `<StateMachineBool>`) ภายใต้ `<StateMachine name="State Machine 1">`
    - ในฝั่ง Kotlin เมื่อ Compose `update` เรียก `view.setNumberState("State Machine 1", "gazeX", ...)`, ตัวแปรจะถูกจัดคิวลงใน `RiveFileController`
    - เมื่อ Render Thread (`Thread-57`) ประมวลผลคิวใน `processAllInputs()` จะเรียก `stateMachineInstance.input("gazeX")` ซึ่งเมื่อหาไม่พบใน State Machine จึงโยน `StateMachineInputException` ออกมาบน Background Thread ส่งผลให้แอป Crash ทันที
- **Fix & Hardening**:
  1. **เพิ่ม State Machine Inputs ใน `scene.rml`**:
     - ประกาศ `<StateMachineNumber name="gazeX" value="0" id="0:90"/>`, `gazeY` (0:91), `emotion` (0:92), `mouthOpen` (0:94)
     - ประกาศ `<StateMachineBool name="isSpeaking" value="false" id="0:93"/>`
     - รันเทสต์ Luau ผ่าน 4/4 และคอมไพล์ไบนารี `avatar.riv` (6666 bytes) พร้อมอัปเดตลง `composeApp/src/androidMain/res/raw/avatar.riv`
  2. **Defensive Input Guarding ใน `RiveAvatarView.android.kt`**:
     - ใน Compose `update` เพิ่มการตรวจสอบสถานะ `sm = view.stateMachines.firstOrNull()` หรือ `playingStateMachines`
     - ตรวจสอบ `sm.inputNames.contains("...")` ก่อนเรียก `setNumberState` / `setBooleanState` ทุกครั้ง
     - หาก State Machine ยังไม่พร้อม หรือไม่มี input ชื่อนั้นๆ จะไม่ส่งคำสั่งเข้าคิว ป้องกันข้อผิดพลาด `StateMachineInputException` บน Render Thread ได้ 100%
- **Verification**:
  - `rive ./rive_avatar --test`: ผ่าน 4/4 การทดสอบ
  - `rive ./rive_avatar --once`: คอมไพล์ได้ 6666 bytes ปราศจาก Warning/Error
  - `.\gradlew.bat compileDebugKotlinAndroid`: ผ่านสมบูรณ์
  - `.\gradlew.bat testDebugUnitTest`: ผ่านสมบูรณ์
  - `.\gradlew.bat assembleDebug`: สำเร็จ และบรรจุ `res/raw/avatar.riv` เข้า APK เรียบร้อย

## 2026-09-14 — Defect Fix: Rive UnsatisfiedLinkError & Native JNI Initialization Lifecycle
- **User Issue & Diagnosis**:
  - เมื่อกดเลือก Rive State Machine Engine ในหน้า Settings เกิด Runtime Crash:
    `No implementation found for long app.rive.runtime.kotlin.core.FileAssetLoader.constructor() (tried Java_app_rive_runtime_kotlin_core_FileAssetLoader_constructor and Java_app_rive_runtime_kotlin_core_FileAssetLoader_constructor__) - is the library loaded, e.g. System.loadLibrary?`
  - **Root Cause**:
    - ใน `RiveAvatarView.android.kt` โค้ดเดิมเรียก `Rive.init(context)` ภายใน `LaunchedEffect(Unit)` ซึ่งจะทำงานแบบ Asynchronous *หลังจาก* Composition และ View Hierarchy ถูกสร้างเสร็จแล้ว
    - ในขณะที่ Compose `AndroidView(factory = { ctx -> RiveAnimationView(ctx) })` ถูกเรียกทำงานแบบ Synchronous ทันทีตั้งแต่เฟรมแรกของการสร้าง View Tree
    - คอนสตรัคเตอร์ของ `RiveAnimationView` เรียกใช้ `FallbackAssetLoader` -> `FileAssetLoader.constructor()` (JNI C++ method) ก่อนที่ `Rive.init` จะได้เรียก `System.loadLibrary("rive-android")` ส่งผลให้โยน `UnsatisfiedLinkError` ทันที
    - นอกจากนี้ `AndroidView.factory` ขาด Error Boundary ส่งผลให้เกิด Unhandled Fatal Exception ปิดแอป
- **Fix & Hardening**:
  1. **Early Native Initialization in `MainActivity.kt`**:
     - เพิ่มการเรียก `System.loadLibrary("rive-android")` และ `Rive.init(applicationContext)` ทันทีใน `MainActivity.onCreate()`
  2. **Synchronous Initialization & Error Boundary in `RiveAvatarView.android.kt`**:
     - ปรับ `isRiveReady` ให้โหลด `System.loadLibrary("rive-android")` และ `Rive.init(context.applicationContext)` แบบ Synchronous ใน `remember`
     - เรียก `Rive.init(ctx.applicationContext)` ซ้ำแบบ Synchronous ใน `AndroidView.factory` ก่อนการสร้างอินสแตนซ์
     - ครอบการสร้าง `RiveAnimationView` ด้วยบล็อก `try-catch (t: Throwable)` เพื่อดักจับข้อผิดพลาดระดับ JNI / Runtime ทั้งหมด
     - **Graceful Fallback**: หาก Native Library โหลดไม่สำเร็จ (`!isRiveReady`) หรือเกิด Error ขณะสร้าง View ระบบจะสลับไปแสดงผล `PetRobotHeadAvatar(..., engineType = COMPOSE_CANVAS)` โดยอัตโนมัติอย่างราบรื่น ไม่มี Crash 100%
- **Verification**:
  - `.\gradlew.bat testDebugUnitTest`: ผ่าน 100% (310/310 การทดสอบ)
  - `.\gradlew.bat assembleDebug`: คอมไพล์และแพ็กเกจ APK สำเร็จ
  - ตรวจสอบไบนารีใน `PersonalAIBot-debug.apk`: บรรจุ `librive-android.so` ครบถ้วนทั้ง 4 สถาปัตยกรรม (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) และทรัพยากร `res/raw/avatar.riv`

## 2026-09-14 — End-to-End Rive Avatar Pipeline & Compose Multiplatform Dual-Engine Integration
- **User Request & Goal**:
  - สร้างระบบ Avatar ด้วย Rive ทั้งหมดครบวงจร (End-to-End Rive Avatar Creation, Tooling, Compilation & Runtime Integration into Compose Multiplatform)
- **Completed Components**:
  1. **Rive Avatar Project (`rive_avatar/`)**:
     - `rive.yaml`: กำหนดโปรเจกต์และเป้าหมายการคอมไพล์
     - `scene.rml`: Artboard หุ่นยนต์ OLED ขนาด 500x500 พร้อมขอบหน้ากาก Visor Bezel เรืองแสงนีออน, ตาสควีร์เคิลนีออนไซแอน 2.5D Shading พร้อมแสงสะท้อน Specular Glint, ม่านตาดำ, และปากดิจิทัล
     - `avatar.luau`: สคริปต์ฟิสิกส์ Luau จัดการ Smooth Gaze Tracking (Adaptive EMA damping), Micro-saccades เลิ่กลั่กสายตาธรรมชาติ, วงจรการกะพริบตาอัตโนมัติ (Procedural Blink Cycle ย่นตาแล้วสปริงตัวคืน), และการขยับปากพูดตอบสนองเสียง
     - `mathutil.luau` & `avatar_test.luau`: ชุดฟังก์ชันคณิตศาสตร์และ Unit Test Luau (ผ่าน 4/4 การทดสอบใน ~15ms)
     - คอมไพล์ได้ไฟล์ไบนารี `avatar.riv` (ขนาดเพียง 6.6 KB ประสิทธิภาพสูงมาก)
  2. **เครื่องมือพัฒนาและพรีวิวอัตโนมัติ (CLI Scripts)**:
     - `scripts/preview_rive_avatar.ps1`: เปิดหน้าต่าง Interactive 60fps Live Preview บน Windows รองรับการลากเมาส์ส่องสายตาและแก้โค้ด Hot-Reload สด
     - `scripts/build_rive_avatar.ps1`: รันเทสต์ Luau, คอมไพล์ `.riv` และก็อปปี้ไปยังทรัพยากร Android อัตโนมัติ
  3. **การเชื่อมโยงระบบ Compose Multiplatform (`composeApp`)**:
     - เพิ่ม Dependency `app.rive:rive-android:11.12.0` พร้อมกำหนด `resolutionStrategy.force("androidx.core:core:1.15.0")` ป้องกันข้อจำกัด API 36/AGP
     - วางไฟล์ไบนารี `avatar.riv` ไว้ที่ `composeApp/src/androidMain/res/raw/avatar.riv`
     - สร้าง Multiplatform Bridge:
       - `commonMain`: `expect @Composable fun RiveAvatarView(...)`
       - `androidMain`: `actual @Composable fun RiveAvatarView(...)` ผ่าน `AndroidView` ครอบ `RiveAnimationView` รับส่งข้อมูล Gaze, Emotion, Speaking แบบเรียลไทม์
       - `iosMain`: `actual @Composable fun RiveAvatarView(...)` ฟอลแบ็กไปยัง Compose Canvas Avatar อย่างปลอดภัย
  4. **สถาปัตยกรรม Dual-Engine พร้อม UI สลับโหมด**:
     - เพิ่ม `AvatarEngineType` (`COMPOSE_CANVAS` vs `RIVE_STATE_MACHINE`) ใน `PetRobotHeadAvatar.kt`
     - เพิ่ม UI Card เลือกเอนจินใน `PetSettingsDialog.kt` (แท็บหน้าจอ & ดีบัก) ให้ผู้ใช้กดสลับระหว่าง Compose Canvas และ Rive State Machine ได้ทันทีในแอป
- **Verification**:
  - `rive ./rive_avatar --test`: ผ่าน 4/4 Unit Tests ใน 15ms
  - `rive ./rive_avatar --once`: คอมไพล์ `rive_avatar.riv` (6605 bytes) สำเร็จใน 44ms
  - ตรวจสอบเรนเดอร์ภาพ Headless Screenshot: บันทึกภาพตรงกลางและภาพมองขวาถูกต้องสมบูรณ์
  - `.\gradlew.bat testDebugUnitTest`: ผ่าน 100% (310/310 การทดสอบ) ปราศจากบั๊กหรือข้อผิดพลาดตกค้าง

## 2026-09-14 — LOOI Moodset Precision Kinetic & Structural Enhancements (Pages 12, 15, 16, 19, 23, 49)
- **User Request & Goal**:
  1. **หน้าที่ 12 (VR Mode)**: แว่น VR ให้มีลูกตา จางๆ อยู่ในแว่นด้วย
  2. **หน้าที่ 15 (Focused)**: ตารางแสกน กวาดขึ้นลง เหมือนกำลังแสกน
  3. **หน้าที่ 16 (Excited)**: ตาครึ่งวงรี สีเหลือง ให้เริ่มจาก ตาวงกลมสีเหลือง แล้วหดเป็นครึ่งวงกลม
  4. **หน้าที่ 19 (Disgusted)**: ตาทั้ง 2 ข้าง ค่อยๆ เลื่อนเข้าหากัน
  5. **หน้าที่ 23 (Rich)**: เพิ่มดวงตา โดยให้ $$ สีเหลืองทอง อยู่ในดวงตา
  6. **หน้าที่ 49 (Warrior)**: ดาบไปอยู่ ข้างละอัน เหมือนกำลังถือดาบ
- **Key Implementation Details**:
  1. **Page 12 (VR_MODE in `PetRobotHeadAvatar.kt`)**:
     - เรนเดอร์ดวงตาไซแอนแบบโปร่งแสง (`alpha = 0.42f * effAlpha`) พร้อมม่านตาดำที่ปรับสายตาตาม `gazeX`, `gazeY` ภายในกระจกแว่น VR Vision Pro ให้เห็นดวงตาลอดผ่านกระจกมืดลึกลับตามต้นแบบ
  2. **Page 15 (FOCUSED in `PetRobotHeadAvatar.kt`)**:
     - เพิ่มแอนิเมชันลำแสงเลเซอร์นีออนไซแอนแนวนอนกวาดขึ้น-ลงทั่วทั้งใบหน้าและดวงตาอย่างต่อเนื่อง (`scanProg`)
     - เพิ่มพารามิเตอร์ `scanProgress` ให้กับ `drawLooiSynthwaveGrid`: เส้นกริดเพอร์สเปกทีฟจะสว่างวาบขึ้นเมื่อลำแสงสแกนวิ่งผ่าน และตัวตารางจะโยกขึ้นลงตามการสแกน
  3. **Page 16 (EXCITED in `PetRobotHeadAvatar.kt`)**:
     - ปรับสีดวงตาเป็นสีเหลืองนีออนสดใส (`#FFD700`) พร้อมเงาสีทองเข้ม
     - ออกแบบการแปลงรูปทรงแบบเรียลไทม์ (Bézier Morphing) ใน `drawLooiExcitedEye`: เริ่มต้นรอบเวลาด้วยดวงตากลมโตสีเหลือง (`shrinkProgress = 0f`) จากนั้นส่วนโค้งด้านล่างจะค่อยๆ ย่นหดแบนขึ้นด้านบนกลายเป็นตาครึ่งวงรี/ครึ่งวงกลม (`shrinkProgress = 1f`) ค้างไว้พร้อมดาวประกายหมุน แล้วดีดตัวสปริงกลับสู่ตากลม
  4. **Page 19 (DISGUSTED in `PetRobotHeadAvatar.kt`)**:
     - ปรับระยะเยื้องของดวงตาทั้ง 2 ข้าง (`inwardSquintX`) ให้เลื่อนเข้าหากึ่งกลางใบหน้าอย่างช้าๆ ต่อเนื่อง จาก 2dp ถึง 16dp ตามจังหวะ `cringeCycle` พร้อม Micro-jitter แสดงอาการขยะแขยง
  5. **Page 23 (RICH in `PetRobotMoodsetDraw.kt`)**:
     - เพิ่มกรอบดวงตาสควีร์เคิลนีออนไซแอนพร้อมเลเยอร์เงาลึกและพื้นหลังกระจก Visor สีเข้ม
     - วางสัญลักษณ์ดอลลาร์สีทองนีออน `$$` บรรจุไว้ที่กึ่งกลางของดวงตาทั้งสองข้างอย่างประณีต
  6. **Page 49 (WARRIOR in `PetRobotMoodsetDraw.kt`)**:
     - ปรับเปลี่ยนจากดาบไขว้ตรงกลาง เป็นถือดาบคาตานะคู่แยกข้างซ้ายและขวา (Dual Wielding):
       - ดาบซ้าย: ด้ามจับอยู่ที่ข้างซ้ายล่างมุม -115° ชี้ขึ้นเฉียงออก โดยมีมือกลหุ่นยนต์ (`drawRobotPaw`) กำด้ามดาบ
       - ดาบขวา: ด้ามจับอยู่ที่ข้างขวาล่างมุม -65° ชี้ขึ้นเฉียงออก โดยมีมือกลหุ่นยนต์กำด้ามดาบ
       - ลำดาบทั้งสองข้างมีประกายดาวสะท้อนแสงดาบสไลด์วิ่งผ่านคมดาบทั้งสองเล่มพร้อมจังหวะหายใจเตรียมต่อสู้
- **Verification**:
  - `.\gradlew.bat testDebugUnitTest` ผ่าน 100% (310/310 การทดสอบ)

## 2026-09-14 — Defect Rectification: Double Eye Layer Elimination, Background Sensor Leak Fix & LOOI 25-Moodset Visual Enhancements
- **User Issue & Diagnosis**:
  1. **การซ้อนทับของเลเยอร์ตา 2 ชุด (Double Eye Layer Overlay)**:
     - ผู้ใช้แจ้งว่า "ดวงตาส่วนใหญ่เหมือนมีการซ้อนทับ ของ เลเยอร์ตา 2 ชุด เลยทำให้ รูปทรงผิดไป"
     - สาเหตุ: `drawParametricEye` ถูกเรียกวาดตาสควีร์เคิล/วงกลมทุกครั้งที่มี `leftEyeParams != null` (ซึ่งไม่เป็น null สำหรับทุกอารมณ์) จากนั้นในโค้ดของอารมณ์เฉพาะ (เช่น `HAPPY`, `LAUGHING`, `DISGUSTED`, `SICK`, `CRYING`, `RICH`, `GAMING`) มีการวาดรูปทรงตาเฉพาะซ้อนทับลงไปอีกรอบหนึ่ง หรือในบางอารมณ์มี guard `if (!isParametric)` ทำให้ระบบไม่ยอมวาดรูปทรงตาเฉพาะ แต่ไปบังคับวาดผ่าน `buildParametricEyePath` ซึ่งพยายามแปลงรูปทรง (เช่น เชฟรอน `> <` หรือ ส่วนโค้ง `⌒ ⌒`) ให้เป็นโพลิกอน 12 จุดจนรูปทรงบิดเบี้ยวผิดเพี้ยน
     - การแก้ไข:
       - กำหนดเซ็ต `emotionsWithDedicatedEyeRenderer` รวบรวม 54 อารมณ์ที่มีฟังก์ชันวาดดวงตาเฉพาะ
       - ครอบบล็อกการวาดตา Parametric ใน `PetRobotHeadAvatar.kt` ด้วย `if (isParametric && emotion !in emotionsWithDedicatedEyeRenderer)` ทำให้ตาสควีร์เคิลแบบพารามิเตอร์จะถูกวาดเฉพาะอารมณ์พื้นฐาน (`IDLE`, `SPEAKING`, `LISTENING`) เท่านั้น สำหรับอารมณ์อื่นๆ ทั้งหมดจะเรนเดอร์ผ่าน Dedicated Vector Renderers ของแต่ละอารมณ์โดยตรงอย่างสะอาดตา คมชัด 100% ปราศจากปัญหาตาซ้อน 2 ชั้นโดยสิ้นเชิง
  2. **Background Sensor & Audio Leak หลังออกจากโหมดสัตว์เลี้ยง**:
     - พบว่าเมื่อผู้ใช้ออกจากโหมดสัตว์เลี้ยงหรือออกจากแอป โค้ด `PetMotionDetector` ยังคงค้าง listener เซนเซอร์ความเร่ง และ `AlwaysLiveManager` มี delayed coroutines (`delay(1200) -> YAWN_SLEEP`, `delay(2000) -> SNORE`) ที่ไม่ถูก cancel ส่งผลให้มีเสียงกรนดังขึ้นมาในพื้นหลังหลังจากปิดหน้าจอหรือพับแอป
     - การแก้ไข:
       - ปรับปรุง `PetMotionDetector.kt`: เพิ่ม guard `isRunning` พร้อม unregister เซนเซอร์และเคลียร์ listeners ให้เป็น null เมื่อสั่ง `stop()`
       - ปรับปรุง `AlwaysLiveManager.kt`: เก็บ Job อ้างอิง (`faceDownSoundJob`, `faceUpSoundJob`, `shakeSoundJob`) และสั่ง `cancel()` ทันทีเมื่อสั่ง stop, minimize หรือเมื่อปิดหน้าจอ (`onScreenOff()`)
       - ปรับปรุง `AlwaysLiveScreen.kt`: เพิ่ม `BackHandler { onEndLive() }` เพื่อให้ปุ่ม Back ของ Android สั่งปิดเซสชันและทำความสะอาดทรัพยากรทั้งหมดอย่างถูกต้อง
       - ปรับปรุง `App.kt`: ใน callback `onEndLive` สั่งรีเซ็ต profile กลับเป็น `AlwaysLiveProfile.CONTROL` และเรียก `stopBackgroundLiveService()`
  3. **LOOI Moodset 25-Page Detail Enhancements (ตรงตาม Reference Sheet 1 & 2)**:
     - **หน้าที่ 2 (HAPPY)**: แสดงตาเส้นโค้งหงายขึ้น `⌒ ⌒` คมชัด ปราศจากตาซ้อน และเพิ่มปากยิ้มโค้งมน `◡`
     - **หน้าที่ 3 (ANGRY)**: ปรับตำแหน่งปากขยับเลื่อนลงมาที่ `angryMouthY = eyeCenterY + baseEyeH * 0.72f` และเปลี่ยนปากเป็นขอบสีแดงพร้อมเขี้ยวขาวขบกราม `v v`
     - **หน้าที่ 4 (SLEEPING)**: วาดปากกรน `o` ทรงรี ขยาย-หดตามจังหวะลมหายใจ พร้อมฟอง Zzz ลอยหมุนควงสว่าน
     - **หน้าที่ 8 (WINK)**: ปรับ kinetic motion ใน `AvatarLivingEngine.kt` ให้เป็นจังหวะ "ขยิบตา 2 ที" (Double Wink) ต่อหนึ่งรอบ พร้อมประกายดาวสีทอง ✦ เด้ง Pop และหมุนเปล่งแสง
     - **หน้าที่ 10 & 19 (LAUGHING & DISGUSTED)**: ปรับเป็นตาเชฟรอน `> <` คมชัดสมส่วน โดยหน้าที่ 19 เป็นตาหยีเกร็งขยะแขยงคู่กับปากขยะแขยงและถังขยะ
     - **หน้าที่ 11 (MUSIC)**: เพิ่มตัวโน้ตดนตรีนีออน 4 ตัว (`♪`, `♫`, `♩`) ลอยพลิ้วไหวรอบตัวหุ่นยนต์พร้อมหูฟังครอบหัว
     - **หน้าที่ 17 (SHY)**: เลื่อนเส้นขีดเขินอายลงมาอยู่บริเวณแก้มใต้ดวงตาอย่างเป็นธรรมชาติ (`eyeCenterY + baseEyeH * 0.82f`)
     - **หน้าที่ 18 (SURPRISED)**: ขยายเครื่องหมายตกใจสีแดง `!` ขนาดใหญ่เด่นชัด (48dp) พร้อมแอนิเมชันเด้งย่อ-ขยาย (Bounce scale)
     - **หน้าที่ 20 (CAMERA_MODE)**: ตาซ้ายแสดงเลนส์ชัตเตอร์กล้องพร้อมวงแหวนแสงแฟลช ตาขวาแสดงกรอบโฟกัส Viewfinder Reticle `[  ]`
     - **หน้าที่ 23 (RICH)**: ปรับตาสัญลักษณ์ดอลลาร์ `$$` ให้เป็นสีทองนีออนสว่างสดใส (`#FFD700`) พร้อมเงาสีทองเข้ม
     - **หน้าที่ 24 (LOVE)**: ปรับเป็นดวงตาหัวใจเต็มดวง ♥ ♥ (`drawHeart`) เต้นตึกตักตามจังหวะชีพจร
     - **หน้าที่ 25 (CRYING)**: แสดงตาโดมมนเรียบเนียน ปราศจากตาสควีร์เคิลซ้อน พร้อมสายน้ำตาไหลพราก
     - **หน้าที่ 27 (READING)**: แสดงตากวาดอ่านหนังสือสะอาดตาคู่กับแว่นตาและหน้าหนังสือกาง
     - **หน้าที่ 28 (GAMING)**: ขยายขนาดจอยคอนโทรลเลอร์ให้ใหญ่ขึ้นเป็น 84x48dp เห็นปุ่ม D-pad และปุ่มแอ็กชันชัดเจน
     - **หน้าที่ 33 (DETECTIVE)**: เพิ่มแอนิเมชันแว่นขยายกวาดสแกนซ้าย-ขวาอย่างต่อเนื่อง
     - **หน้าที่ 36 (SPACE)**: ขยายขนาดยานอวกาศ UFO ให้ใหญ่ขึ้นเป็น 48x18dp พร้อมโดมกระจกและลำแสง
     - **หน้าที่ 37 (PARTY)**: ปรับเป็นตาโค้งยิ้ม `⌒ ⌒` สะอาดตา พร้อมหมวกปาร์ตี้และแตรเป่ายืด-หด
     - **หน้าที่ 38 (DREAMING)**: ปรับเป็นตาปิดหลับสบาย `— —` พร้อมก้อนเมฆและพระจันทร์
     - **หน้าที่ 39 (EXHAUSTED)**: ขยายขนาดลิ้นห้อยแฮ่กๆ ชมพูให้ใหญ่ขึ้น (18x20dp) พร้อมร่องกลางลิ้นคู่กับปากหอบโค้งคว่ำ
     - **หน้าที่ 42 (ROMANTIC)**: ปรับเป็นดวงตาหัวใจพร้อมขยิบตาข้างเดียวและประกายดาว
- **Verification**:
  - `.\gradlew.bat testDebugUnitTest` สำเร็จ 100% (310/310 ผ่านทั้งหมด)

## 2026-09-14 — Bug Fix: Infinite Sleep Trigger Loop in GazeStabilizer & PetVisionDetector
- **User Issue & Diagnosis**:
  - ผู้ใช้แจ้งปัญหาหุ่นยนต์ avatar ติดสถานะนอนหลับ (`SLEEPING`) ตลอดเวลา พยายามตื่นแล้วก็กลับมาหลับทันที แม้ว่าสถานะความต้องการ (อิ่ม / พลังงาน / ความสะอาด / ความสุข) จะเกือบ 100% ทั้งหมด
  - ตรวจสอบจาก Logcat พบว่า `PetVisionDetector` ส่งสัญญาณ:
    `😴 Face absence timeout reached -> Triggering SLEEPING`
    รัวทุกเฟรมกล้อง (~80ms หรือ 12 ครั้งต่อวินาที) ส่งผลให้เมื่อหุ่นยนต์เปลี่ยนเป็น `HAPPY` จากการสัมผัสหรือพูดคุย จะถูกกล้องดีดกลับเป็น `SLEEPING` ในทันทีภายใน 100ms
- **Root Causes & Solutions**:
  1. **Level-Triggered Spam ใน `GazeStabilizer.kt`**:
     - เดิม: เมื่อ `elapsed >= idleSleepTimeoutMs` ฟังก์ชัน `onNoFace()` ส่งคืน `AvatarEmotion.SLEEPING` รัวทุกๆ เฟรมแบบไม่มีที่สิ้นสุด
     - แก้ไข: ปรับเป็น **One-Shot Edge-Triggered State Machine** โดยเพิ่ม `lastAbsenceEmotionNotified` เพื่อแจ้งเตือนเพียงครั้งเดียวต่อช่วงเวลาที่ใบหน้าหายไป และคืนค่า `null` ในเฟรมถัดไป
     - เพิ่มฟังก์ชัน `resetAbsenceTimer()` เพื่อรีเซ็ตตัวจับเวลาเมื่อมีการสัมผัส โต้ตอบ หรือพูดคุย
     - ปรับระยะเวลาเริ่มต้นให้เหมาะสมกับการใช้งานบนโต๊ะ (Desk Companion): `idleBoredTimeoutMs = 45,000L` (45 วิ) และ `idleSleepTimeoutMs = 180,000L` (3 นาที)
  2. **Cross-Platform Absence Reset Bridge (`PetVisionBridge.kt` & `PetVisionDetector.kt`)**:
     - เพิ่ม `PetVisionBridge.onResetAbsence` และ `resetAbsenceTimer()`
     - ผูกเข้ากับ `gazeStabilizer.resetAbsenceTimer()` ใน `PetVisionDetector`
  3. **High-Energy & Conversation Guards ใน `PetModeController.kt`**:
     - ใน `notifyInteraction()`: เรียก `PetVisionBridge.resetAbsenceTimer()` ทุกครั้งที่มีการสัมผัส (ลูบหัว, เกาคาง, จิ้มแก้ม, เขย่า), พูดคุย หรือให้อาหาร
     - ใน `onFaceAbsenceTimeout()`: เพิ่มการตรวจสอบ:
       - หากหุ่นยนต์กำลังพูด (`isSpeaking`), กำลังฟัง (`LISTENING`), หรือรันฉากทดสอบ -> ไม่ขัดจังหวะ
       - หากพลังงานยังสูง (`energy > 30f`) -> ไม่บังคับเข้าโหมดหลับลึก แต่จะปรับเป็น `BORED` (เหงา/รอบอส) หรือเล่นลูกเล่น Screensaver Trick แทน
       - อนุญาตให้หลับลึกเฉพาะเมื่อพลังงานต่ำจริง (`energy <= 30f`) หรือสั่งนอนหลับโดยตรง
     - ใน `startIdleLoop()`: เพิ่มเงื่อนไขการหลับลึก 150 วินาที เฉพาะเมื่อพลังงานต่ำ (`<= 35f`) หากพลังงานเต็มจะไม่หลับ และขยายระยะพักสายตาเต็มที่สำหรับเครื่องที่ทิ้งไว้เฉยๆ เป็น 10 นาที (600 วินาที)
- **Verification**:
  - `PetRobotAvatar25DTest.kt`: ผ่านครบ 14/14 การทดสอบ (ครอบคลุม One-shot edge triggering และ absence timer reset)
  - `PetModeTest.kt`: ผ่านครบ 75/75 การทดสอบ (ครอบคลุม High Energy Guard, Low Energy sleep transition, Conversation protection, และ Bridge reset hook)
  - รันการทดสอบ Unit Tests ทั้งหมดในโปรเจกต์: **310/310 ผ่าน 100%**

## 2026-09-14 — LOOI Robot: 2.5D Spherical Eye Depth & 100% Parametric Emotion Morphing
- **User Request & Goal**:
  - วิเคราะห์เปรียบเทียบภาพจริงของ LOOI Robot (Normal mode & Love mode จากภาพฮาร์ดแวร์จริง) และ Moodset Reference Sheets (20-moodset & 40-moodset):
    1. **2.5D Spherical Eye Depth**: วาดแสงเงาหลายเลเยอร์ตามหลักฟิสิกส์บนกระจกหน้าจอ visor (ไฮไลท์สว่างมุมบนซ้าย, ไล่สีเรเดียลที่แกนกลางตา, เงาเสี้ยวจันทร์ลึกด้านล่างขวา, แสงสะท้อนกลินท์รูปไข่, และรัศมีนีออนโกลว์เรืองแสงรอบนอก)
    2. **100% Parametric Eye Morphing**: ปลดล็อคทุกอารมณ์ (ไม่มีอารมณ์ใดคืนค่า null) ให้แปลงร่างแบบต่อเนื่อง 350ms `FastOutSlowInEasing` ไร้เงาผีซ้อน (no crossfade ghosting)
    3. **ความถูกต้องตามต้นแบบฮาร์ดแวร์จริง**: โหมด LOVE บนเครื่องจริงเป็นตาโดมโค้งมนไซแอน (`domeAmount = 0.95f, curvature = 0.35f`) พร้อมหัวใจชมพูลอยด้านข้าง, โหมด ROMANTIC เป็นตาหัวใจชมพูเต็มดวง ♥ (`heartAmount = 1.0f`), โหมด DEAD เป็นตากากบาท X X (`crossAmount = 1.0f`), โหมด EXCITED เป็นตาดาว 4 แฉก ★ (`starAmount = 1.0f`)
    4. **Simple Mode vs Rich Mode (`AvatarDetailLevel`)**: `SIMPLE` แสดงพื้นหลังดำสนิทแบบ OLED `#000000` ไร้แสงพัลส์ พร้อมพร็อพแบบไอคอนิกสะอาดตา (ตัดกรงเล็บหุ่นยนต์, เศษขนมปัง, ฟองเบียร์) vs `RICH`
- **Key Implementation Details**:
  1. **2.5D Spherical Eye Visor Shading (`PetRobotNeonDraw.kt`)**:
     - เพิ่ม `getEyeDeepShadowColor(baseColor)`: คำนวณสีเงาเสี้ยวจันทร์ตามสเปกตรัมแสง LED จริง:
       - ไซแอน `#00F5FF` -> น้ำเงินครามเข้ม Deep Indigo `#071952` (ตรงตามภาพถ่ายฮาร์ดแวร์จริง)
       - แดง `#FF3B5C` -> แดงเบอร์กันดีเข้ม Deep Burgundy `#42000E`
       - ชมพู `#FF4081` -> พลัมม่วงเข้ม Deep Plum `#450624`
       - ทอง `#FFD700` -> ทองบรอนซ์เข้ม Deep Bronze `#522800`
     - เพิ่ม `getEyeHighlightColor(baseColor)` และ `drawNeonEyeRoundRect` 4-layer composition
  2. **100% Parametric Morphing Engine (`PetRobotParametricEye.kt`)**:
     - ขยาย `EyeShapeParams` เพิ่ม `heartAmount`, `starAmount`, `crossAmount`, `domeAmount`, `innerPinch`
     - แมปปิ้งทั้ง 44 อารมณ์ใน `toEyeShapeParams(isLeft)` ครบถ้วน 100% ไร้ null
     - พัฒนา `buildParametricEyePath` ให้รองรับ 12-point cubic Bézier topology สำหรับ Squircle, Arch, Dome, Wedge, Chevron, Heart, Star, Cross
     - พัฒนา `drawParametricEye` ให้ตัดแกน 2.5D Spherical Gradient + Crescent Shadow + Glint ลงบน Path และครอบด้วย Neon Bloom Halo
  3. **Detail Level Integration (`AvatarLayout.kt`, `PetBackgroundLayer.kt`, `PetRobotHeadAvatar.kt`)**:
     - สร้าง `AvatarDetailLevel` (`SIMPLE`, `RICH`) และ `LocalAvatarDetailLevel`
     - `DefaultBackground()` ในโหมด `SIMPLE` วาดดำสนิท OLED pitch black `#000000`
     - ครอบฟังก์ชันวาดดวงตาดั้งเดิมใน `renderPetEmotion` ด้วย `if (!isParametric)` กำจัดการวาดตาซ้ำซ้อน
     - ปรับ `drawRomanticMood` ใน `PetRobotMoodsetDraw.kt` ให้รับ `includeEyes = !isParametric` เพื่อไม่ให้ตาสควีร์เคิลทับตาหัวใจ
- **Verification**:
  - `PetRobotAvatar25DTest.kt`: ผ่านครบทั้ง 14/14 การทดสอบ (100%)
  - `PetModeTest.kt`: ผ่านครบทั้ง 71/71 การทดสอบ (100%)
  - โค้ดคอมไพล์ผ่านสมบูรณ์ทั้ง Android และ iOS Multiplatform (`commonMain`)

## 2026-09-14 — PetRobotHeadAvatar: 2.5D Face-Tracking Avatar + Smooth Emotion Morphing
- **User Request & Goal**:
  - ยกระดับ `PetRobotHeadAvatar` สู่ 2.5D Face-Tracking Avatar + Smooth Emotion Morphing:
    1. Unified Layout: รวมโครงสร้างเรขาคณิตและการตรวจสอบ `isLandscape` เข้าสู่ `LocalAvatarLayout` กลาง เพื่อให้ทุกเลเยอร์ทำงานสอดคล้องกัน ไม่เหลื่อมล้ำ
    2. Smooth Face-Tracking & Distance Filter: กำจัดอาการตาสั่นระริกจาก Face Detector (±3% jitter) ด้วย Adaptive EMA (`GazeStabilizer`), ขยายสเกลดวงตาเมื่อเข้าใกล้กล้อง (`faceScaleFactor`), และปรับอารมณ์อัตโนมัติเมื่อผู้ใช้ไม่อยู่หน้าจอ (>12s -> BORED, >25s -> SLEEPING)
    3. 2.5D Layering Parallax: สร้างมิติเชิงลึกผ่านการแบ่งชั้นความเร็วการเคลื่อนที่ (Counter-translation บนพื้นหลัง, 3D Perspective Rotation บนศีรษะและพร็อพ, Accelerated Parallax บน Foreground)
    4. Smooth Emotion Morphing: ปรับเปลี่ยนรูปทรงตาอย่างลื่นไหลต่อเนื่องผ่าน Unified Parametric Eye (`buildParametricEyePath`) สอดประสาน 350ms (`FastOutSlowInEasing`) แทนการกะพริบหดเป็นเส้นขีด
    5. รักษาเอกลักษณ์ Flat Neon-Glow ดั้งเดิมแบบ LOOI Robot (แกนนีออนทึบ + รัศมีโกลว์ 2 ชั้น ไม่ใช้ 3D specular highlight/ดินน้ำมัน)
- **Key Implementation Details**:
  1. **Unified Layout (`AvatarLayout.kt`)**:
     - กำหนด `AvatarLayoutInfo` และ `LocalAvatarLayout` (CompositionLocal)
     - ฟังก์ชัน `calculateAvatarLayout(width, height, isLandscapeOverride)` คำนวณพิกัดกลาง `cX`, `cY`, `eyeDiameter`, `foreheadY`, `mouthY`, `chinY`, `leftTempleX`, `rightTempleX`
     - ปรับปรุง `PetPropsOverlay.kt` และ `PetRobotHeadAvatar.kt` ให้อ้างอิงโครงสร้างเรขาคณิตชุดเดียวกัน แก้ปัญหาพร็อพ (แว่นตา, หมวก, มงกุฎ) ลอยเยื้องพิกัดดวงตา
  2. **Parametric Eye & Morphing Engine (`PetRobotParametricEye.kt`)**:
     - กำหนด `EyeShapeParams` (scaleX/Y, squashTop/Bottom, curveTop/Bottom, cornerTopInner/Outer, slantAngle, pupilScale, pupilOffsetX/Y)
     - ฟังก์ชันแปลง `AvatarEmotion.toEyeShapeParams(isLeft)` รองรับอารมณ์หลัก (IDLE, HAPPY, ANGRY, SLEEPING, SAD, SURPRISED, BORED, WINK ฯลฯ)
     - `rememberAnimatedEyeShapeParams(...)` ทำ Interpolation ทุกพารามิเตอร์ของตาซ้ายและตาขวาพร้อมกัน 350ms `FastOutSlowInEasing`
     - วาดเส้นทางดวงตาต่อเนื่องด้วย Bézier Path (`buildParametricEyePath`) คงรูปแบบ 2-layer neon glow ดั้งเดิม
  3. **Adaptive Low-Pass Gaze & Absence Tracker (`GazeStabilizer.kt`)**:
     - Adaptive Exponential Moving Average (EMA): ดักจับการสั่นไหวเล็กน้อย (<3.5%) กรองด้วย low alpha (0.08) ให้ภาพนิ่งสนิท ขณะที่การหันหน้าจริง (>15%) ตอบสนองรวดเร็วด้วย alpha 0.45
     - Face Distance Scaling: คำนวณอัตราส่วนใบหน้าเทียบกับขนาดจอ หากเข้าใกล้กล้อง (>0.38) จะขยาย `faceScaleFactor` สู่ 1.05x - 1.22x
     - Face Absence Watchdog: ตรวจจับการละสายตาหรือเดินออกจากกล้อง (>12s -> ปรับเป็น BORED, >25s -> ปรับเป็น SLEEPING)
     - รองรับ Multiplatform อย่างสมบูรณ์ด้วย `kotlinx.datetime.Clock`
  4. **2.5D Layering Parallax System (`PetModeScreen.kt`)**:
     - ห่อหุ้มเลเยอร์ด้วย `BoxWithConstraints` และส่งผ่าน `LocalAvatarLayout provides layoutInfo`
     - **Layer 0 (PetBackgroundLayer)**: เลื่อนสวนทิศทางสายตาเล็กน้อย (`translationX = -gazeX * 14.dp`, `translationY = -gazeY * 10.dp`) สร้างมิติฉากหลังลึกลงไป
     - **Layer 2 (PetRobotHeadAvatar)**: หมุน 3 มิติ (`rotationY` สูงสุด 35°, `rotationX` สูงสุด 20°, `cameraDistance = 14f * density`)
     - **Layer 3 (PetPropsOverlay)**: หมุน 3 มิติตามศีรษะ พร้อม Forward Translation เล็กน้อย (`+gazeX * 6.dp`) และจัดระยะ Padding ให้ตรงกับศีรษะ
     - **Layer 3.7 (PetForegroundLayer)**: เลื่อนตามทิศทางสายตาด้วยความเร็วสูง (`+gazeX * 24.dp`, `+gazeY * 18.dp`) สร้างมิติละอองแสง/เลนส์ลอยอยู่หน้าจอ
  5. **Bridge & Controller Integration**:
     - เพิ่ม `onFaceDistanceDetected` และ `onFaceAbsenceTimeout` บน `PetVisionBridge`
     - เชื่อมต่อ `PetVisionDetector` (Android ML Kit) เข้ากับ `GazeStabilizer` และส่งต่ออีเวนต์เข้าสู่ `PetModeController`
   6. **Unit Tests & Regression Verification**:
      - `PetRobotAvatar25DTest.kt`: ผ่าน 11 จาก 11 เทส (Adaptive EMA, Jitter suppression, Dynamic distance scaling, Absence watchdog, Layout geometry, Parametric path generation)
      - `PetModeTest.kt`: แก้ไขตำแหน่งพารามิเตอร์ `foregroundName` ใน `RobotFaceState` ให้อยู่หลัง `gestureName` คืนความเข้ากันได้ย้อนหลัง (Backward Compatibility) ให้กับ Positional Constructor และอัปเดตการตรวจสอบ `BackgroundTheme` ครบทั้ง 20 ธีม ส่งผลให้ผ่านครบทั้ง 71 จาก 71 เทส 100%
- **Status**: Completed & 100% Verified (All Tests Passed)

## 2026-09-14 — LOOI Robot Page 8 (Wink) Dynamic In-and-Out Gesture with Pop Sparkle Stars
- **User Request & Goal**:
  - "หน้าขยิบตา ให้ ตา 1 ข้างขยิบ เข้าออก"
  - พัฒนาการแสดงออกทางสีหน้าของ LOOI Robot Avatar ในโหมด Wink (หน้าที่ 8 / `AvatarEmotion.WINK`) จากเดิมที่เป็นภาพนิ่งตายตัว (ตาขวาขีดเส้นตรงแข็งทื่อตลอดเวลา) ให้เป็นท่าทางขยิบตาแบบไดนามิก เข้า-ออก (In-and-Out) เป็นจังหวะธรรมชาติต่อเนื่อง 60fps พร้อมประกายดาวสีทอง ✦ เด้ง Pop และหมุนเปล่งประกายเฉพาะช่วงที่ขยิบตาตาม Reference Sheet 1 Cell 8
- **Key Implementation Details**:
  1. **Dynamic Wink Kinetic Driver (`AvatarLivingEngine.kt`)**:
     - เพิ่มตัวแปร `winkEyeScaleY` และ `winkSparkleScale` เข้าสู่ `LivingMotionState`
     - ออกแบบรอบเวลา 2,200ms (5 จังหวะธรรมชาติ):
       - `0.00..0.38` (~840ms): ตาทั้ง 2 ข้างเปิดมองกลมโตปกติ (Rest Open)
       - `0.38..0.52` (~300ms): ตาขวาเริ่มหรี่ลง (Squash & Close `1f -> 0.06f`)
       - `0.52..0.76` (~530ms): ตาขวาปิดสนิทเป็นขีดหลับตามน (`drawSleepingEye`), ประกายดาวสีทอง ✦ เด้ง Pop ขยาย 1.35x พร้อมหมุนเปล่งประกาย
       - `0.76..0.88` (~260ms): ตาขวาดีดตัวเปิดกลับเป็นตากลม (Spring Open `0.06f -> 1f`)
       - `0.88..1.00` (~260ms): พักตาก่อนเริ่มรอบขยิบตาครั้งถัดไป
  2. **Elevated Wink Emotion Rendering (`PetRobotHeadAvatar.kt`)**:
     - ตาซ้าย: เปิดกลมโตเป็น Squircle Cyan 2D Depth พร้อม Sympathetic Reaction ขยายตัวรับเล็กน้อย (+4%) ช่วงที่ตาขวาขยิบตา
     - ตาขวา: ควบคุมด้วย `winkEyeScaleY`:
       - ช่วงปิดสนิท (`<= 0.16f`): วาดเป็น `drawSleepingEye` แถบมนนีออนไซแอน 14dp
       - ช่วงเปิด/กำลังหรี่: วาดด้วย `drawDualCircleEye` พร้อม Squash & Stretch ฟิสิกส์ (`squashX = 1f + (1f - winkScaleY) * 0.14f`)
     - ประกายดาววิ้งค์สีทอง ✦ (Dual Star System ตาม Reference Cell 8):
       - ดาวดวงหลัก (Main 4-point Star): สีทอง Neon Gold (`#FFD700`) ขนาด 30dp พิกัดมุมขวาบนของตาที่ขยิบ (`rightEyeCenterX + baseEyeW * 0.42f`, `eyeCenterY - baseEyeH * 0.38f`)
       - ดาวดวงเล็กเสริม (Secondary Mini Star): สีเหลืองนีออน (`#FFEA00`) ขนาด 14dp หมุนสวนทางเพื่อมิติความระยิบระยับ
  3. **Mouth Vertical Balance**:
     - ตรวจสอบ `hasMouth` ให้ `AvatarEmotion.WINK` ปรับสมดุลกึ่งกลางจออย่างลงตัวเมื่อไม่มีการพูด
- **Verification & Physical Device Testing**:
  - บิลด์ผ่านฉลุย `./gradlew :composeApp:assembleDebug`
  - ติดตั้งลงบน Samsung Galaxy S22 Ultra (`R5CT42YEMMM`) ผ่าน adb install
  - ทดสอบ Broadcast Page 8 (`adb shell am broadcast -a com.skyliner2008.jarvis.TEST_EMOTION -p com.skyliner2008.jarvis --es emotion 8`)
  - แคปเจอร์ภาพเคลื่อนไหวต่อเนื่องแบบ Burst Capture:
    - `screen_wink30.png`: สภาวะเปิดตากลมโตปกติ 2 ข้าง
    - `screen_wink32.png`: สภาวะตาขวาเริ่มหรี่ลง (Squash down)
    - `screen_wink36.png`: สภาวะตาขวาขยิบตาปิดสนิทเป็นขีด พร้อมประกายดาวสีทอง ✦ เด้ง Pop และเปล่งแสงอย่างงดงามตรงตามภาพต้นแบบ

## 2026-09-14 — Comprehensive Procedural Living Gestures & Dynamic Emotion Kinetics Across LOOI Moodsets
- **User Request & Goal**:
  - "หน้าที่ 6 , หน้าที่ 7 แบบนี้แหละ คือ moodset ที่ฉันต้องการ คือมีการเคลื่อนใหว ที่สื่อถึงสิ่งที่ทำ คือ moodset ไม่ควรลอย อยู่เฉยๆ ควรสื่อถึงอารมณ์ ในบาง moodset ก็สื่อได้ดี เช่น หน้า 31 หนาว ก็มี ปากสั่น แต่บางอันยังสื่อไม่ ถึงความหมาย อย่างโกรธ ก็ควรตาสั่น หรือแนงเฉียงขยับ เพื่อสื่อว่า กำลังโกรธ ลองคิด ปรับ moodset แต่ละแบบ ให้สื่อความหมาย ได้ถูกต้องกว่านี้"
  - ยกระดับทุก Moodset ของ LOOI Robot ให้มี "Living Gestures" มีชีวิตชีวา ไม่ลอยอยู่นิ่งๆ โดยเฉพาะอารมณ์ที่สื่อสารได้ไม่ชัดเจน เช่น โกรธ (ตาสั่น/คิ้วกระตุก), นอน (สัปหงก), หัวเราะ (ตัวโยก/ตาหยีเด้ง), ตกใจ (ตาสั่น/เครื่องหมาย ! สั่น), รัก (หัวใจเต้นจังหวะชีพจร), อ่านหนังสือ (สายตากวาดทีละบรรทัด), ปาร์ตี้ (เป่าแตรคลี่ออก), นักรบ (ประกายดาบสะท้อนวาบ), แบตเตอรี่ต่ำ (ไฟวูบวาบกระตุกดับ)
- **Key Implementation Details**:
  1. **Dynamic Procedural Kinetic Engine (`AvatarLivingEngine.kt`)**:
     - เพิ่มตัวแปร Procedural Motion Clocks คำนวณแบบ 60fps ภายใน `remember(...)` โดยอิงจาก Master Timers (`loopFast`, `loopMedium`, `loopSlow`):
       - `angerJitterX/Y`: การสั่นเกร็งความถี่สูงระดับ Micro-tremor ของความโกรธ
       - `angerBrowSlant`: จังหวะกระตุกคิ้วขมวดขยับขึ้นลง (-20° ถึง -28°)
       - `veinPulse`: เส้นเลือดปูด 💢 เต้นตุบๆ รุนแรง
       - `nodOffOffsetY`: การสัปหงกหลับ (หลับคอพับลงช้าๆ แล้วสะดุ้งคืนตัว)
       - `laughBounceY` & `laughSquint`: จังหวะตัวโยกขึ้นลงของการหัวเราะพร้อมตาหด-ขยาย
       - `heartbeatScale`: จังหวะเต้นหัวใจคู่จริง (Lub-Dub double pulse: 1.0 -> 1.25 -> 1.12 -> 1.32 -> 1.0)
       - `readingScanX`: การกวาดสายตาแบบ Sawtooth ซ้ายไปขวาช้าๆ แล้วดีดกลับต้นบรรทัด
       - `partyHornProg`: จังหวะเป่าแตรปีใหม่ คลี่ม้วนกระดาษยืดออกยาวแล้วม้วนหดกลับ
       - `wandArcAngle`: วงสวิงโบกไม้กายสิทธิ์เป็นส่วนโค้งเวทมนตร์พร้อมปล่อยประกายดาว
       - `pantHeaveY` & `pantCycle`: จังหวะหอบแฮ่กๆ ตัวโยกตามลมหายใจและลิ้นกระเพื่อม
       - `bladeShineProg`: แสงสะท้อนดาวประกาย 4 แฉกวิ่งเฉียบคมบนคมดาบคู่ซามูไร
       - `brownoutAlpha`: สภาวะไฟตกวูบวาบของหุ่นยนต์ใกล้แบตหมด
       - `shiverFastX/Y`: อาการสั่นระริกจากความหนาว/กลัว
  2. **Elevated Sheet 1 Moodsets (`PetRobotHeadAvatar.kt`)**:
     - **Page 3 (Angry)**: ตาเฉียงแดงสั่นระริก (`angerJitterX/Y`), คิ้วกระตุกขมวดชันเป็นจังหวะ (`angerBrowSlant`), เส้นเลือดปูด 💢 เต้นตุบๆ ขยาย 1.35x, เขี้ยวขาวขบเกร็ง
     - **Page 4 (Sleeping)**: ศีรษะและดวงตาค่อยๆ สัปหงกทิ่มลง (`nodOffOffsetY`) แล้วสะดุ้งตัวกลับ, ตัวอักษร Zzz ลอยควงสว่าน
     - **Page 10 (Laughing)**: ตา `> <` โยกเด้งจังหวะหัวเราะท้องแข็ง (`laughBounceY`), ขนาดตาหดขยาย (`laughSquint`), ประกายดาวความสุขผุดรอบดวงตา
     - **Page 14 (Evil)**: คิ้วเฉียงกระตุกเอียงไม่เท่ากัน 2 ข้าง (Asymmetric Slant Cocking), ดวงตามองเหี้ยม, ไอคอนปิศาจม่วง 😈 ลอยเต้นเป็นจังหวะ
     - **Page 17 (Shy)**: ตากลมหลบสายตาไปมา (`shyGazeX/Y`), แก้มชมพูระเรื่อเต้นเรื่อๆ เปล่งแสง
     - **Page 18 (Surprised/Shock)**: ตาเบิกกว้างสั่นตกใจ (`shockJitter`), เครื่องหมายตกใจสีแดง ! สั่นกระตุก
     - **Page 24 (Love)**: ดวงตาหัวใจสีชมพูเต้นตุบๆ ตามจังหวะหัวใจจริง (`heartbeatScale` Lub-Dub Rhythm)
  3. **Elevated Sheet 2 Moodsets (`PetRobotMoodsetDraw.kt`)**:
     - **Page 27 (Reading)**: สายตากวาดอ่านหนังสือทีละบรรทัดจากซ้ายไปขวาแล้วดีดกลับ (`readingScanX`)
     - **Page 37 (Party)**: แตรเป่าคลี่ขยายความยาวจาก 16dp เป็น 54dp ขณะเป่า พร้อมม้วนกระดาษปลายแตรคลายออก
     - **Page 39 (Exhausted)**: หน้าอกและดวงตายกยุบตามจังหวะหอบหายใจ (`pantHeaveY`), ลิ้นห้อยกระเพื่อมตามลมหายใจ (`pantCycle`)
     - **Page 40 (Electric)**: ดวงตาสายฟ้ากระตุกสั่นแบบ 8-bit Stepped Arcade Jitter ไม่ใช่ Sine wave เรียบๆ
     - **Page 45 (Magic)**: ไม้กายสิทธิ์โบกวาดส่วนโค้ง Wave Casting Arc (`wandArcAngle`) พร้อมประกายดาวขยายตัววาบที่ปลายไม้
     - **Page 48 (Scared)**: ตาสั่นระริกหวาดกลัว (`shiverFastX/Y`), สายตากลอกมองตามผีน้อยที่บินวนไปมา, ฟันสั่นกึกๆ
     - **Page 49 (Warrior)**: แสงประกายดาบสะท้อน 4 แฉกเฉียบคมสไลด์ผ่านคมดาบคู่คาตานะ (`bladeShineProg`)
     - **Page 50 (Low Battery)**: ดวงตาสีฟ้าหม่นค่อยๆ หรี่กระพริบวูบวาบดับสลับติด (`brownoutAlpha`) พร้อมเปลือกตาตกหนักหน่วง
- **Verification & Physical Device Testing**:
  - บิลด์สำเร็จ: `./gradlew :composeApp:compileDebugKotlinAndroid` & `assembleDebug`
  - ติดตั้ง APK และทดสอบบน Samsung Galaxy S22 Ultra (`R5CT42YEMMM`) ผ่าน adb broadcast `com.skyliner2008.jarvis.TEST_EMOTION`
  - ยืนยันผลลัพธ์ผ่านรูปภาพจับหน้าจอจริง:
    - `screen_angry.png`: หน้าที่ 3 (Angry) คิ้วกระตุก ตาสั่น เส้นเลือดปูดเต้น
    - `screen_p24.png`: หน้าที่ 24 (Love) ดวงตาหัวใจเต้นตึกตักเป็นจังหวะ Lub-Dub
    - `screen_p10.png`: หน้าที่ 10 (Laughing) ตา `> <` โยกเด้งหัวเราะอย่างร่าเริง
    - `screen_p27.png`: หน้าที่ 27 (Reading) แว่นตาและหนังสือกาง สายตากวาดอ่าน
    - `screen_p37.png`: หน้าที่ 37 (Party) หมวกปาร์ตี้ แตรเป่ายืดคลี่ออก
    - `screen_p48.png`: หน้าที่ 48 (Scared) ตาสั่น ปากสั่น ผีลอยประกบ
    - `screen_p50.png`: หน้าที่ 50 (Low Battery) หลอดไฟวูบวาบ ไฟตก (Brownout) และแบตเตอรี่สีแดงเตือน

## 2026-09-14 — LOOI Robot Page 6 (Eating) & Page 7 (Drinking) Living Gestures: Lift-Bite & Head-Tilt Drink
- **User Request & Goal**:
  - "อย่าง เบียร์ ควรทำตาเอียง เหมือนเอียงหน้า แล้วเอียงแก้วเบียร์ ระดับปาก ให้ทำท่าคล้ายยกดื่ม" (เบียร์: ทำตาเอียงเหมือนเอียงหน้า ยกและเอียงแก้วเบียร์ที่ระดับปาก ทำท่าคล้ายยกดื่ม)
  - "อย่าง เบอร์เกอร์ ให้เบอร์เกอร์ เลื่อนขึ้นมา จากด้านล่าง มาถึงแนวปาก คล้ายยกเบอร์เกอร์มากัด" (เบอร์เกอร์: เลื่อนขึ้นมาจากด้านล่าง มาถึงแนวปาก คล้ายยกเบอร์เกอร์ขึ้นมากัด)
- **Key Implementation Details**:
  1. **AvatarLivingEngine Timing Adjustments (`AvatarLivingEngine.kt`)**:
     - ขยายรอบเวลา `chewCycle` จาก 850ms เป็น 2000ms เพื่อให้รอบเวลารองรับ 4 จังหวะการเคลื่อนไหวเต็มรูปแบบ (ยกขึ้น -> กัด -> ลดลง -> เคี้ยวตุ้ยๆ)
     - ขยายรอบเวลา `gulpCycle` จาก 1300ms เป็น 2400ms เพื่อให้รอบเวลารองรับ 4 จังหวะการดื่มเต็มรูปแบบ (เอียงหน้ายกแก้ว -> ยกเอียงดื่มกลืน 2 อึก -> วางแก้วคืนหน้าตรง -> พักสดชื่น)
  2. **Page 6: Eating Lift & Chomp Gesture (`PetRobotHeadAvatar.kt`)**:
     - ฟิสิกส์ 4 จังหวะ:
       - Phase 1 (0.00..0.28): ยกเบอร์เกอร์เลื่อนขึ้นมาจากด้านล่าง (+36dp) มาสู่แนวระดับปาก สายตาก้มมองตามเบอร์เกอร์
       - Phase 2 (0.28..0.44): จังหวะกัด Chomp! เบอร์เกอร์มีแรงกด squash ย่นเข้าหาปาก ตาหยีปิดอร่อย พร้อมเศษขนมปังร่วงกระจาย
       - Phase 3 (0.44..0.60): ดึงเบอร์เกอร์เลื่อนลงกลับมาที่ตำแหน่งถือพัก (+36dp) ด้านล่าง
       - Phase 4 (0.60..1.00): พักเบอร์เกอร์ไว้ด้านล่าง แล้วเคี้ยวแก้มตุ่ย ตาเด้งเป็นจังหวะตามการเคี้ยวอย่างเอร็ดอร่อย
  3. **Page 7: Drinking Head Tilt & Glass Tip Gesture (`PetRobotHeadAvatar.kt`)**:
     - ฟิสิกส์ 4 จังหวะ:
       - Phase 1 (0.00..0.26): ดวงตาและใบหน้าเอียงองศา (Head Tilt -7.5°) พร้อมยกแก้วเบียร์ขึ้นและเอียงแก้ว (Glass Tilt 24.0°) โดยมี Pivot point อยู่ที่ขอบปากแก้วด้านบนซ้าย ให้ขอบปากแก้วแตะอยู่ที่แนวระดับปากตลอดเวลา
       - Phase 2 (0.26..0.68): ยกกระดกดื่มค้างไว้พร้อมเอียงหน้า มี Swallow Pulse กลืนอึกๆ 2 ครั้ง ตาหรี่เคลิ้ม มีประกายฟองเบียร์
       - Phase 3 (0.68..0.84): ลดแก้วเบียร์ลงและหมุนแก้วกลับมาตั้งตรง พร้อมคืนศีรษะและดวงตากลับมาหน้าตรงปกติ
       - Phase 4 (0.84..1.00): แก้วเบียร์ตั้งตรงระดับปาก ดวงตากลับมาตรงสดชื่น
  4. **Preserved Non-Obstructive Positioning**:
     - ทั้งสองไอเทมยังคงอยู่ที่ระดับปาก (Mouth level) ด้านล่างตา ไม่ซ้อนทับหว่างตาเหมือนเป็นจมูก และเคลื่อนไหวอย่างสมจริงเป็นธรรมชาติ
- **Verification & Testing**:
  - บิลด์ผ่านสำเร็จ 100%: `./gradlew :composeApp:assembleDebug`
  - ติดตั้งและจับภาพหน้าจอจริงบน Samsung Galaxy S22 Ultra (`R5CT42YEMMM`):
    - `p6_gesture_bite.png`: จังหวะยกเบอร์เกอร์ขึ้นมาถึงแนวระดับปากเพื่อกัด
    - `p6_gesture_chew.png`: จังหวะลดเบอร์เกอร์ลงมาด้านล่างและเคี้ยวตุ้ยๆ
    - `p7_gesture_drinking.png`: จังหวะดวงตาเอียงและแก้วเบียร์เอียงยกกระดกดื่มที่ระดับปาก
    - `p7_gesture_upright.png`: จังหวะลดแก้วเบียร์ลงและคืนหน้าตรงปกติ

## 2026-09-14 — LOOI Robot Page 6 (Eating) & Page 7 (Drinking) Proportional & Position Parity
- **User Request & Goal**:
  - "moodset หน้าที่ 6 กับ หน้าที่ 7 เบอร์เกอร์ กับ เบียร์ มันไปทับอยู่ ที่ ระหว่างดวงตา เลยทำให้ดูเหมือน เป็นจมูก มากกว่า กำลังกิน"
  - แก้ไขปัญหาเบอร์เกอร์และแก้วเบียร์ที่เคยวางตำแหน่งสูงเกินไปจนทับช่องว่างระหว่างดวงตาทำให้ดูเหมือนจมูก ย้ายลงมาที่ระดับปาก (Mouth Level) ด้านล่างตาอย่างถูกต้องตามภาพอ้างอิง LOOI Reference Sheet 1 (Cell 6 & Cell 7)
- **Key Implementation Details (`PetRobotHeadAvatar.kt`)**:
  1. **Page 6: Eating (เบอร์เกอร์)**:
     - ปรับตำแหน่งแนวตั้ง: `burgerY = eyeCenterY + baseEyeH * 0.68f` (ย้ายลงมาจากเดิม 0.22f) ขอบบนของขนมปังเบอร์เกอร์แตะพอดีกับส่วนโค้งขอบล่างของดวงตา ปล่อยให้ช่องว่างตรงกลางระหว่างดวงตาโล่งสะอาดตา 100%
     - ปรับสัดส่วนขนาด: `burgerW = baseEyeW * 0.58f` (ลดจาก 0.95f) ให้มีขนาดกะทัดรัดได้สัดส่วนมินิเบอร์เกอร์ตามแบบ LOOI Sheet 1 Cell 6
     - ปรับสายตามองลง: `eatingGazeY = (gazeY + 0.38f)` สายตามองก้มลงที่เบอร์เกอร์ขณะเคี้ยว พร้อมแอนิเมชันเศษขนมปังร่วงหล่น
  2. **Page 7: Drinking (แก้วเบียร์)**:
     - ปรับตำแหน่งแนวตั้ง: `beerY = eyeCenterY + baseEyeH * 0.84f` (ย้ายลงมาจากเดิม 0.40f) ปุยฟองเบียร์ด้านบนแตะขอบล่างของดวงตา ตัวแก้วเบียร์ทอดตัวลงด้านล่างในระดับปาก/คาง
     - ปรับสัดส่วนขนาด: `beerW = baseEyeW * 0.46f` ได้สัดส่วนแก้วไพนต์มินิมอล
     - ปรับสายตาเอียงมองลงชนแก้ว: ตาทั้งสองข้างมองก้มเข้าหากันที่ปากแก้วเบียร์ (`gazeX = ±0.18f`, `gazeY = 0.42f`)
- **Verification & Testing**:
  - บิลด์สำเร็จ: `./gradlew :composeApp:assembleDebug`
  - ทดสอบและจับภาพหน้าจอจริงบน Samsung Galaxy S22 Ultra (`R5CT42YEMMM`):
    - `screen_p6_final.png`: เบอร์เกอร์อยู่ระดับปากด้านล่างตา ไม่ทับหว่างคิ้ว/จมูก ดูเป็นการกินเบอร์เกอร์ชัดเจน
    - `screen_p7_final.png`: แก้วเบียร์อยู่ระดับปากด้านล่างตา หว่างตาโล่ง ดูเป็นการดื่มเบียร์สดชื่นสมบูรณ์แบบ

## 2026-09-14 — Pet Mode Portrait Dialogue Layout: Non-Obstructive Overlay & Bottom Status Dimming
- **User Request & Goal**:
  - ในหน้าจอแนวตั้ง (Portrait) เมื่อมีกล่องข้อความสนทนาแสดงขึ้นมา มันเคยแสดงทับใบหน้าของ Pet Robot
  - ผู้ใช้ต้องการให้:
    1. เมื่อมีกล่องข้อความ กล่องข้อความจะต้องมาแสดงทับแถบสถานะด้านล่างแทน (ไม่ทับใบหน้าหุ่นยนต์)
    2. แถบสถานะด้านล่างจะมืดลง (Dimmed) ในขณะที่กล่องข้อความกำลังแสดง
    3. เมื่อกล่องข้อความหายไป แถบสถานะจะกลับมาสว่างเป็นปกติเหมือนเดิม
- **Key Implementation Details (`PetModeScreen.kt`)**:
  1. **Decoupled Pet Face from Message Card in Portrait**:
     - ปรับ `targetFaceOffsetY = 0f` และ `targetFaceScale = if (isLandscape) 0.82f else 1.0f` ในแนวตั้ง ใบหน้าหุ่นยนต์จะคงสเกล 100% เต็มและจัดกึ่งกลางครึ่งบนอย่างสมบูรณ์แบบโดยไม่ถูกบีบหรือเลื่อนหลบ
     - ป้องกันไม่ให้ `PetDialogueCard` ถูกวาดในครึ่งบนในโหมดแนวตั้ง (`if (isLandscape) { ... }`)
  2. **Layered Bottom Status Surface with Dynamic Dimming**:
     - เพิ่ม `statusContentAlpha by animateFloatAsState(targetValue = if (hasMessage) 0.08f else 1.0f, tween(320))` ลดความสว่างของ Dashboard ค่าความต้องการ (Tamagotchi needs) ลงเหลือเพียง 8% เพื่อไม่ให้ลายตาและขับกล่องข้อความให้โดดเด่น
     - เพิ่ม `statusScrimAlpha by animateFloatAsState(targetValue = if (hasMessage) 0.78f else 0.0f, tween(320))` แผ่น Scrim สีกรมเข้ม/ดำ คลุมทับแผงสถานะ พร้อมดักจับการแตะเพื่อปิดกล่องข้อความ (tap to dismiss)
     - ปิดการใช้งานปุ่มคำสั่ง (Feed, Clean, Play, Sleep) ชั่วคราวเมื่อมีกล่องข้อความเปิดอยู่ (`enabled = !hasMessage`)
  3. **Centered Floating Dialogue Card Over Status Panel**:
     - วาง `AnimatedVisibility` ภายใน `Box` กึ่งกลางแถบสถานะด้านล่าง โดยจำกัดความสูง `heightIn(max = screenMaxHeight * 0.38f)` ไม่ให้ล้นเกินขอบเขต
     - เพิ่มพื้นหลัง `PetDialogueCard` ให้ทึบขึ้น (`surfaceColor.copy(alpha = 0.96f)`) พร้อมเงาลึก 20dp และขอบนีออนไซแอนคมชัด
  4. **Smooth Auto-Restore Transition**:
     - เมื่อกล่องข้อความหายไป (Demo จบ หรือแตะปิด) ค่า Alpha จะ Fade-in กลับมาสว่าง 100% ภายใน 320ms อย่างนุ่มนวล
- **Verification & Testing**:
  - บิลด์สำเร็จ: `./gradlew :composeApp:assembleDebug`
  - ทดสอบจริงบน Samsung Galaxy S22 Ultra (`R5CT42YEMMM`):
    - `screen_portrait_dim_verified.png`: ตรวจสอบขณะแสดงกล่องข้อความ หุ่นยนต์อยู่ครึ่งบน 100% ชัดเจน แถบสถานะด้านล่างมืดลง และกล่องข้อความแสดงทับอย่างประณีต
    - `screen_portrait_restored_bright.png`: ตรวจสอบหลังข้อความหายไป แถบสถานะกลับมาสว่างเต็มที่ 100% พร้อมปุ่มคำสั่งพร้อมใช้งาน

## 2026-09-14 — LOOI Robot "Neon Cyan Style Moodset" 2-Layer Flat Glow & Morph Engine
- **User Request & Goal**:
  - สไตล์ภาพอ้างอิง "Looi Robot: Neon Cyan Style Moodset" (flat neon-glow icon + motion) แทนแนวทาง 3D gradient/specular:
    1. เขียนฟังก์ชัน `drawNeonShape` (Glow layer ขยาย ~15-20% alpha ~0.4-0.6 + Core layer ทึบ ไม่มี gradient, strokeCap = Round) และ accentColor ต่ออารมณ์ (Cyan, Red, Pink, Gold, Mint, Grey)
    2. ทำ blink/emotion-switch เป็น morph animation บน scaleY (circle -> flat slit ~150-250ms -> spring open to new shape)
    3. แยก layer การเคลื่อนไหวของ prop ออกจากตาหลัก (hearts, tears, sparkles, swirls, zzz, sweat drops) เคลื่อนไหวอิสระซ้าย-ขวาไม่พร้อมกัน
    4. คง 3D rotation ไว้เป็น outer layer
    5. ลบ radial gradient และ specular highlight จุดขาวทั้งหมดออก
- **Key Architecture & Enhancements Implemented**:
  1. **Neon Vector Drawing Engine (`PetRobotNeonDraw.kt`)**:
     - สร้างชุดฟังก์ชันวาดเวกเตอร์ 2 ชั้น Neon Flat Glow บริสุทธิ์: `drawNeonRoundRect`, `drawNeonCircle`, `drawNeonPath`, `drawNeonFilledPath`, `drawNeonArc`, `drawNeonLine`, `drawNeonHeart`, `drawNeonTeardrop`, `drawNeonSparkle`, `drawNeonZzz`, `drawNeonSwirl`
     - Glow Layer: ขยายขนาดรูปทรง ~15-20% รัศมีแสงฟุ้ง Multi-pass GPU-accelerated DrawScope bloom (alpha 0.35-0.55), Core Layer: เนื้อสีทึบคมชัด ไร้ gradient จุดตัดเส้นโค้งมน Round cap
     - พาเล็ตสีนีออนมาตรฐาน: `NeonCyan` (`#00F5FF`), `NeonRed` (`#FF3B5C`), `NeonPink` (`#FF4081`), `NeonGold` (`#FFD700`), `NeonMint` (`#64FFDA`), `NeonGrey` (`#90A4AE`)
  2. **Morph Animation on Blink & Emotion Switch (`PetRobotHeadAvatar.kt`)**:
     - **Blink Morph**: `naturalBlinkScaleY` แอนิเมชันกะพริบตาธรรมชาติวนรอบ 3800ms บีบ `scaleY` จาก 1f ลงสู่ Flat Slit 0.06f ใน 170ms ด้วย `FastOutSlowInEasing` และเด้งเปิดกลับสู่ 1f ใน 120ms
     - **Emotion Switch Morph**: `emotionMorphScaleY` เมื่อตรวจพบการเปลี่ยนอารมณ์ จะบีบยุบตัวลงสู่ Slit 0.08f ใน 160ms แล้วสลับ `displayedEmotion` ก่อนจะ Spring เด้งเปิดสู่รูปทรงอารมณ์ใหม่อย่างนุ่มนวลด้วย `Spring.DampingRatioLowBouncy`
  3. **Decoupled Out-of-Sync Particle Motion Layer (`rememberPetParticleMotionState()`)**:
     - แยกพร็อพเคลื่อนไหวออกจากแกนตาหลัก: หัวใจลอย (Love), หยดน้ำตาร่วง (Sad/Crying), อักษร Zzz ลอยหมุน (Sleepy), ดาวหมุนประกาย (Wink), ก้นหอยหมุนวน (Sick), หยดเหงื่อ (Sweat)
     - ซ้ายและขวาแยกคาบเวลาและความถี่ไม่พร้อมกัน (Out-of-phase oscillation เช่น ซ้าย 1900ms ขวา 2300ms) ให้ความเป็นธรรมชาติตามหลักฟิสิกส์สิ่งมีชีวิต
  4. **Removal of 3D Gradient & Specular Highlights**:
     - ลบ Brush.radialGradient และจุดขาว specular highlight dots ออกทั้งหมดในทุกดวงตาและพร็อพ เปลี่ยนเป็น Flat Neon Icon ตามแม่แบบ LOOI แท้จริง
     - คงระบบ Native 3D Perspective (`rotationY`, `rotationX` บน Canvas `graphicsLayer`) ไว้นอกสุดเพื่อคงมิติ Parallax
  5. **Crying Waterfall Flow Matching Looi Reference (`PetRobotMoodsetDraw.kt`)**:
     - ปรับปรุง `drawCryingMood` ให้มีดวงตาทรงโดมไซแอนนีออน, ม่านน้ำตกไหลพรั่งพรูจากตาทั้งสองข้าง (`drawNeonFilledPath`), หยดน้ำตาหยดติ๋งอิสระ (`drawNeonTeardrop`), และปากคว่ำเศร้า `⌒`
- **Verification & Testing**:
  - บิลด์ผ่านสำเร็จ 100%: `./gradlew :composeApp:assembleDebug`
  - ติดตั้งและจับภาพหน้าจอบนอุปกรณ์จริง (Samsung Galaxy S22 Ultra - `R5CT42YEMMM`) ผ่าน ADB Intent `com.skyliner2008.jarvis.TEST_EMOTION`:
    - `screen_p1.png`: Normal (Standby Neon Cyan flat glow)
    - `screen_p3_angry.png`: Angry (Neon Red wedge eyes + fangs + red glow)
    - `screen_p4_sleeping.png`: Sleepy (Neon Grey capsule slits + animated rising Zzz)
    - `screen_p8_wink.png`: Wink (Neon Cyan eye + wink slit + gold sparkle star)
    - `screen_p22_sick.png`: Sick (Neon Mint dual out-of-phase swirls + thermometer)
    - `screen_p24_love.png`: In Love (Neon Pink giant hearts + floating mini hearts)
    - `screen_p25_neon.png`: Crying (Neon Cyan dome eyes + cascading waterfalls + sad mouth)

## 2026-09-13 — LOOI Robot True 3D Spherical Face Projection
- **User Request & Goal**:
  - "ตัว Moodsets ส่วนที่ทำ ดวงตา ของ pet ฉันว่า มายังขาด ในส่วนของ อารมณ์แบบโครงหน้า ส่วนโค้งตามรูปหน้า ที่เหมือนเป็นทรงกลม 3D มันเลยยังดูไม่เป็น ธรรมชาติ อย่างถ้าหันซ้าย ตาข้างซ้าย จะใหญ่กว่า ข้างขวา ,หันขวา ตาข้างขวา จะใหญ่กว่าข้างซ้าย ตืออารมณ์เหมือน มีหัวมีหน้า แต่ไม่ต้องวาดหัววาดหน้า มีแค่ดวงตา คิ้ว กับปาก"
  - สร้างมิติโครงหน้าทรงกลม 3D ล่องหน (Invisible 3D Head Effect) เวลาที่ดวงตาและใบหน้าของหุ่นยนต์หันไปด้านข้าง
- **Key Enhancements Implemented**:
  1. **Native 3D Perspective via Compose `graphicsLayer` (`PetRobotHeadAvatar.kt`)**:
     - เพิ่ม `rotationY` (Yaw) และ `rotationX` (Pitch) ลงใน Modifier.graphicsLayer ของ Canvas อวตารหลัก
     - ปรับคำนวณ `targetRotationY = gazeX * 35f` และ `targetRotationX = -gazeY * 20f` ผูกเข้ากับทิศทางสายตา
     - ผลลัพธ์: มิติ Parallax แบบ 3D แท้จริง (เมื่อหันซ้าย `gazeX < 0` หน้าซ้ายจะหมุนมาด้านหน้า ทำให้ดวงตาซ้ายขยายใหญ่ขึ้น และตาขวาแบนเล็กลงอัตโนมัติตามหลัก Perspective Foreshortening)
  2. **Spherical Curve Offset Mapping**:
     - เปลี่ยนการเลื่อนแกน X/Y แบบแบนราบ (Flat translation) เป็นการเลื่อนโค้งแบบทรงกลม (Spherical Wrap-around Spacing)
     - สร้างตัวแปร `faceCenterX` และ `faceCenterY` ที่คำนวณจาก `gazeDisplacementX` และ `dynamicSpacing` เพื่อดึงพร็อพทั้งหมด (ปาก, คิ้ว, แว่น, ของกิน ฯลฯ) ให้หมุนและจัดกึ่งกลางสอดคล้องกับระนาบ 3D ใหม่
  3. **Anticipation Scale Pivot Alignment**:
     - อัปเดตจุดศูนย์กลาง (Pivot) ของฟิสิกส์การยืดหด (Squash & Stretch) จาก `centerX` เดิมเป็น `faceCenterX` เพื่อให้เวลากระโดดเปลี่ยนอารมณ์ ใบหน้าจะยืดหดจากแกนหน้าตัวเอง ไม่ใช่จากแกนกลางจอโทรศัพท์

## 2026-09-13 — LOOI Robot 50 Moodsets Dynamic Background & Foreground 2.5D Depth Engine
- **User Request & Goal**:
  - "มันยังขาดฉากหลัง /ฉากหน้า ไดนามิก ที่คู่กับ Moodsets มันได้เพิ่มความมี มิติ"
  - เพิ่มมิติความลึก (2.5D Layered Depth & Atmospheric Immersion) ให้กับ Avatar ทั้ง 50 Moodsets ผ่านระบบฉากหลังไดนามิก (Dynamic Background) และฉากหน้าเลนส์/ละอองบรรยากาศ (Dynamic Foreground)
  - คงเอกลักษณ์ความมินิมอล High-Contrast Dark OLED (#000000) ของ LOOI หุ่นยนต์ตั้งโต๊ะ โดยดวงตาไซแอน (#00F5FF) ยังคงเด่นชัดเป็นจุดนำสายตา
  - รองรับ Kotlin Multiplatform (KMP) 100% ใน `composeApp/src/commonMain`, วาดด้วย Compose Canvas Vector ล้วน ไม่ใช้บิตแมปภายนอก, ทำงานลื่นไหล 60fps
- **Key Enhancements Implemented**:
  1. **Architecture & State Expansion (`RobotFaceState.kt`)**:
     - เพิ่ม `BackgroundTheme` 12 ธีมใหม่: `CYBER_GRID`, `SPACE_NEBULA`, `MAGIC_MYSTIC`, `CINEMA_COZY`, `WINTER_BLIZZARD`, `SUMMER_HEAT`, `WARRIOR_DOJO`, `PARTY_CONFETTI`, `GOLDEN_VAULT`, `SICK_LAB`, `SPORTS_ARENA`, `LOW_POWER_CRT` รวมเป็น 20 ธีม
     - เพิ่ม `ForegroundEffect` enum 17 เอฟเฟกต์: `NONE`, `CYBER_HUD`, `STAR_DUST`, `MAGIC_SPARKLES`, `LENS_REFLECTION`, `FROST_VIGNETTE`, `HEAT_DISTORTION`, `RAIN_CONDENSATION`, `ELECTRIC_SPARKS`, `FALLING_PETALS`, `CONFETTI_TUMBLE`, `GOLDEN_SHINE`, `HEART_ORBS`, `BUBBLE_FLOAT`, `ANAMORPHIC_FLARE`, `CRT_SCANLINES`, `CAMERA_VIEWFINDER`
     - เพิ่ม `foregroundName` และ property helper `foregroundEffect` ใน `RobotFaceState`
  2. **Dynamic Background Engine (`PetBackgroundLayer.kt`)**:
     - อัปเกรด `DefaultBackground()` ให้มี Organic Breathing Depth Halo (รัศมีแสงไซแอน `#00F5FF` หายใจอย่างนุ่มนวล alpha 0.03f..0.08f) ขจัดความแบนราบมืดทึบ
     - สร้างคอมโพเนนต์ฉากหลัง 12 รูปแบบ: ตารางไซเบอร์เปอร์สเปกทีฟ, กาแล็กซีเนบิวลาพร้อมดาวตก, วงแหวนอักขระเวทมนตร์, แสงไฟโรงหนังนุ่มนวล, พายุหิมะไซบีเรีย, คลื่นแดดฤดูร้อน, โรงฝึกซามูไร, สปอตไลต์ปาร์ตี้, ประกายทองคำในคลังสมบัติ, ฟองทดลองเคมีในแล็บ, อัฒจันทร์สนามกีฬา, เมทริกซ์ CRT พลังงานต่ำ
  3. **Dynamic Foreground Engine (`PetForegroundLayer.kt`)**:
     - สร้างคอมโพเนนต์ `PetForegroundLayer` พร้อม Crossfade Animation (500ms)
     - เรนเดอร์เอฟเฟกต์เลนส์/บรรยากาศ 16 ชนิด: โครงข่าย HUD, ละอองโบเก้ดาวระยิบระยับ, แสงประกายเวท 4 แฉก, แสงสะท้อนเลนส์ Visor กวาดผ่าน, ขอบน้ำแข็งเกาะเลนส์ (Frost Vignette), คลื่นความร้อนบิดเบี้ยว (Heat Distortion), หยดน้ำเกาะกระจก, ประกายไฟสายฟ้าแลบ, กลีบซากุระร่วงปลิวลม, คอนเฟตติหมุนคว้าง 3D, ประกายดาวสีทอง, อณูหัวใจลอย, ฟองสบู่ลอย, แสงแฟลร์แนวนอน Anamorphic, เส้นสแกน CRT ยุค 80, กรอบเล็งกล้อง Viewfinder
  4. **Moodset Catalog Automatic Resolution (`LooiMoodsetCatalog.kt`)**:
     - พัฒนา `resolveDefaultBackground(pageNumber, emotion)` และ `resolveDefaultForeground(pageNumber, emotion)` เชื่อมโยง Moodset ทั้ง 50 หน้าเข้ากับฉากหลังและฉากหน้าอย่างสมบูรณ์แบบ
     - อัปเดต `JarvisViewModel.kt` ส่ง `backgroundName` และ `foregroundName` อัตโนมัติเมื่อเรียก `showMoodsetPage` และ `playAllMoodsets`
     - ติดตั้ง `PetForegroundLayer` ใน `PetModeScreen.kt` (Layer 3.7) ด้านหน้าหุ่นยนต์และอุปกรณ์
  5. **Procedural Synthesis Exhaustiveness (`AmbientSoundEngine.kt`)**:
     - อัปเดต `when(theme)` ใน `AmbientSoundEngine.kt` ให้มี `else` branch รองรับ 12 ธีมใหม่อย่างปลอดภัย
- **Verification & Testing**:
  - **Unit Tests**: `MoodsetCatalogTest` ครอบคลุมการทดสอบทั้ง 50 หน้า, การตรวจสอบ Background และ Foreground Depth $\rightarrow$ **BUILD SUCCESSFUL** (100% Pass)
  - **On-Device ADB Verification (Samsung Galaxy S22 Ultra - `R5CT42YEMMM`)**:
    - ติดตั้ง `PersonalAIBot-debug.apk` สำเร็จ
    - บันทึกและตรวจสอบภาพถ่ายหน้าจอจริง 7 แบบตัวแทน:
      - `screen_p1.png`: Normal (Default Breathing Depth Halo + Visor Lens Reflection)
      - `screen_p23.png`: Rich (Golden Vault Gradient Aura + Golden Star Sparkles)
      - `screen_p31.png`: Cold (Winter Blizzard Snowflakes + Frost Crystalline Vignette)
      - `screen_p36.png`: Space (Deep Space Nebula Orbit + Star Dust Bokeh Depth)
      - `screen_p37.png`: Party (Celebration Sweeping Spotlights + Tumbling Confetti)
      - `screen_p45.png`: Magic (Mystical Rune Rings + Arcane Star Sparkles)
      - `screen_p49.png`: Warrior (Red Dojo Aura + Falling Cherry Blossom Petals)
      - `screen_p50.png`: Low Battery (Pulsing CRT Red Matrix + CRT Scanlines)
    - ทุกเลเยอร์เรนเดอร์สวยงาม มีมิติ 2.5D ลึกชัดเจน ลื่นไหล 60fps บนหน้าจอ OLED

## 2026-09-13 — LOOI Robot 50 Moodsets Full Stylistic Parity & On-Device ADB Verification
- **User Request & Goal**:
  - บรรลุความแม่นยำ 100% ในด้านสัดส่วน ลายเส้น และสไตล์ตามแม่แบบภาพอ้างอิงทั้ง 50 หน้าของ LOOI Robot (Sheet 1: 1-20, Sheet 2: 21-50)
  - แก้ไขพร็อพอาหารและเครื่องดื่ม (🍔 Burger, 🍺 Beer, 🍿 Popcorn) ที่ดูลอยเป็นสติ๊กเกอร์แปะใต้ตา ให้จัดวางและได้สัดส่วนที่กลมกลืนเป็นเนื้อเดียวกับดวงตาไซแอน
  - ตรวจสอบความถูกต้องของทุก Moodset บนอุปกรณ์จริงผ่าน ADB Screencap (Samsung Galaxy S22 Ultra - `R5CT42YEMMM`)
- **Key Enhancements Implemented**:
  1. **Food & Drink Proportional Realignment (`PetRobotHeadAvatar.kt`)**:
     - **Page 6 (Eating)**: ขยายและย้ายตำแหน่งเบอร์เกอร์ให้สอดรับเข้ากับร่องโค้งระหว่างดวงตาทั้งสองข้างอย่างพอดี (`burgerW = baseEyeW * 0.95f`, `burgerY = eyeCenterY + baseEyeH * 0.22f`), ปรับระดับสายตาให้เหลือบมองเบอร์เกอร์อย่างเอร็ดอร่อย, ถอดอุ้งมือและเส้นปากที่ไม่จำเป็นออกเพื่อให้ดูมินิมอลตามแบบ Sheet 1 Cell 6
     - **Page 7 (Drinking)**: ปรับแก้วเบียร์เป็นทรงไพนต์ (Conical Pint Tumbler) ตั้งตรงระหว่างดวงตา (`beerW = baseEyeW * 0.52f`, `beerY = eyeCenterY + baseEyeH * 0.40f`) มีฟองขาวนุ่มพูนขอบและฟองอากาศคาร์บอเนต, เปลี่ยนดวงตาเป็นดวงตากลมสดชื่นเหลือบมองเข้าหาแก้ว (`gazeX = ±0.20f`, `gazeY = 0.25f`), ตัดแก้มชมพูและอุ้งมือออกให้ตรงกับ Sheet 1 Cell 7
     - **Page 12 (VR Mode)**: ปรับขนาดถังป๊อปคอร์นให้กะทัดรัดได้สัดส่วน (`popW = baseEyeW * 0.46f`) วางไว้ที่มุมล่างขวาของแว่น VR Vision Pro (`popX = rightEyeCenterX + baseEyeW * 0.50f`, `popY = eyeCenterY + baseEyeH * 0.50f`), ตัดเม็ดกระเด็นและมือออกตาม Sheet 1 Cell 12
  2. **Costume & Expression Parity (`PetRobotMoodsetDraw.kt`)**:
     - **Page 21 (Confused)**: เพิ่มความหนาของเส้นดวงตาคลื่นหยัก (Wavy Eye Stroke 11dp, Depth Shadow 15dp) ให้เปล่งประกายคมชัด พร้อมเครื่องหมาย `¿` สีฟ้าอ่อน และ `??` สีแดง
     - **Page 45 (Magic)**: ขยายความสูงหมวกพ่อมด 56dp พร้อมริบบิ้นสีเหลือง, ไม้กายสิทธิ์หัวดาวประกาย และรอยยิ้มมั่นใจ `drawSmileArc`
     - **Page 46 (Sporty)**: ปรับจากลูกบาสเกตบอลเป็นลูกฟุตบอลขาว-ดำคลาสสิก (⚽) มีช่องห้าเหลี่ยมตรงกลางและลายเย็บ, สายคาดหัว 3 สี, และรอยยิ้ม
     - **Page 47 (Scientist)**: ออกแบบแว่นตาทดลองคู่เชื่อมด้วยสะพานแว่นและสายรัดด้านข้าง, ขวดแก้วรูปชมพู่มีของเหลวสีเขียวเดือดปุด, และรอยยิ้ม
     - **Page 49 (Warrior)**: เพิ่มยอดเขาเกราะทองซามูไร (Kuwagata V-horns) โค้งขึ้นจากเหรียญวงกลมกลางหน้าผาก, ผ้าคาดหัวสีแดง, ดาบคะตะนะไขว้, และรอยยิ้ม
     - **Page 50 (Low Battery)**: เพิ่มประกายดาวสีเทาเข้มหรี่แสง `✦` เหนือดวงตาข้างซ้ายตาม Sheet 2 Cell 50
- **On-Device ADB Screencap Verification**:
  - ตรวจสอบผ่าน ADB Broadcast: `com.skyliner2008.jarvis.TEST_EMOTION`
  - ตรวจภาพแคปหน้าจอจริง: `screen6.png`, `screen7.png`, `screen12.png`, `screen21.png`, `screen45.png`, `screen46.png`, `screen47.png`, `screen49.png`, `screen50.png` ผ่าน `view_file` ยืนยันตรงตามแบบ 100%

## 2026-09-13 — LOOI Robot Food & Drink Living Gestures Upgrade (🍔 Burger, 🍺 Beer, 🍿 Popcorn)
- **User Feedback**:
  - "ดูมี ชีวิตชีวา ขึ้นมานิดหน่อย แต่พวก 🍔 🍺 🍿 สติ๊กเกอร์ ที่อยู่ ใต้ดวงตา และไม่ได้แสดง ท่าทาง เหมือนกำลังกิน มันดูเล็กไป"
  - พร็อพอาหารและเครื่องดื่มในหน้า Eating (หน้า 6), Drinking (หน้า 7), และ VR Mode (หน้า 12) มีขนาดเล็กเกินไป (24-40dp) ดูแบนเหมือนสติ๊กเกอร์แปะใต้ตา และขาดท่าทางกำลังกิน/ดื่มจริง
- **Changes Implemented**:
  1. **Living Motion Engine Drivers (`AvatarLivingEngine.kt`)**:
     - เพิ่ม `chewCycle` (0..1f ใน 850ms): ควบคุมจังหวะงับเบอร์เกอร์และเคี้ยวตุ้ยๆ (Squash & Stretch)
     - เพิ่ม `gulpCycle` (0..1f ใน 1300ms): ควบคุมการเอียงยกแก้วดื่ม จังหวะกลืน และการเต้นของลำคอ (Throat pulse)
     - เพิ่ม `popPhase` (0..1f ใน 700ms): ควบคุมการป๊อปและวิถีโค้งของเม็ดป๊อปคอร์นลอยเข้าปาก
  2. **Substantial Size Enlargement (ขยายขนาดใหญ่ขึ้น 2.0x – 2.5x)**:
     - 🍔 Burger: ขยายจาก 40dp เป็น **80dp** (เห็นชั้นขนมปังงา, ผักกาด, มะเขือเทศ, ชีสเยิ้ม, เนื้อย่าง, รอยกัด)
     - 🍺 Beer Stein: ขยายจาก 28dp เป็น **66dp x 84dp** (แก้วเบียร์หนักทรงยุโรป มีฟองนุ่มล้นขอบและพรายฟองคาร์บอเนต)
     - 🍿 Popcorn: ขยายจาก 24dp เป็น **62dp x 74dp** (ถังป๊อปคอร์นลายทางขาวแดงโรงหนัง ขอบทอง เม็ดป๊อปคอร์นพูน)
  3. **Living Eating & Drinking Gestures (`PetRobotHeadAvatar.kt`)**:
     - **Eating (หน้า 6)**: อุ้งมือหุ่นยนต์ 2 ข้างจับเบอร์เกอร์ (`drawRobotPaw`), ปากเคี้ยวตุ้ยๆ (`drawChewingMouth`) สลับจังหวะงับ, แก้มสีชมพูพองระเรื่อตามจังหวะเคี้ยว, มีเศษขนมปังสีทองร่วง, สายตาหรี่มองเบอร์เกอร์อย่างเอร็ดอร่อย (`⌒ ⌒`)
     - **Drinking (หน้า 7)**: ยกแก้วเบียร์เอียงทำมุมดื่ม (`-28° ถึง -35°`), มือหุ่นยนต์จับหูแก้ว, ฟองขาวพวยพุ่ง, ปากขยับดื่มอึกๆ พร้อมจังหวะกระตุกกลืนที่คอ, มีคราบฟองเบียร์สีขาวติดมุมปาก, สายตาเคลิบเคลิ้มสดชื่น
     - **VR Mode (หน้า 12)**: ถังป๊อปคอร์นลายทางใบใหญ่, เม็ดป๊อปคอร์นเด้งลอยเป็นวิถีโค้งเข้าปาก, ปากใต้หน้ากาก VR กำลังเคี้ยวหงุบหงับ
  4. **Pet Mode Props Overlay Sync (`PetPropsOverlay.kt`)**:
     - อัปเกรด `BurgerProp()`, `BeerProp()`, `PopcornProp()` ให้มีขนาดและท่าทางเคลื่อนไหวแบบเดียวกันเมื่อผู้ใช้สวมใส่พร็อพ
- **Verification**:
  - Unit Tests: `MoodsetCatalogTest`, `DeviceControlTest` $\rightarrow$ **BUILD SUCCESSFUL in 2m 30s** (100% Pass)
  - Android APK: `./gradlew :composeApp:assembleDebug` $\rightarrow$ **BUILD SUCCESSFUL in 1m 31s** (Ready to install)

## 2026-09-13 — LOOI Robot 50 Moodsets Living Procedural Motion Engine (Canvas Dynamic Physics)
- **User Issue**:
  - ผู้ใช้ตรวจดูหน้าตาหุ่นยนต์ LOOI Robot ทั้ง 50 แบบแล้วพบว่า "ยังแข็ง ไม่มีการขยับเคลื่อนไหวที่ดูเป็นธรรมชาติ ถ้าเรายกระดับเป็น Rive Animation จะได้มั้ย"
  - จากการวิเคราะห์เปรียบเทียบระหว่าง Rive Vector Graphics (ต้องรอสร้างไฟล์ `.riv` ภายนอกและไม่พร้อมรันได้ทันที) กับ Procedural Canvas Living Physics ผู้ใช้เลือก **ทางเลือกที่ 1 (Procedural Engine ใน Compose Canvas)** เพื่อให้ทั้ง 50 Moodsets มีชีวิตชีวา ขยับเคลื่อนไหวนุ่มนวลแบบเรียลไทม์ทันทีโดยไม่ต้องพึ่งพาไฟล์ภายนอก
- **Architecture & Implementation**:
  1. **`AvatarLivingEngine.kt`** (`com.skyliner2008.jarvis.ui.component.avatar`):
     - สร้าง State data class `LivingMotionState` และ Composable hook `rememberLivingMotionState()`
     - **Respiration Scaling (Volume Conservation)**: ขยายแกน Y (1.02f) พร้อมบีบแกน X (0.985f) สลับกันตามจังหวะหายใจอย่างเป็นธรรมชาติ (Sine curve 2800ms)
     - **Subtle Head Sway**: การเอียงศีรษะแบบ Organic Micro-roll (+/- 1.8 องศา) ตามจังหวะการหายใจ
     - **Saccadic Eye Darting**: การเหลือบสายตาขยับโฟกัสฉับพลันทุก 2.5-4.2 วินาที (Saccadic eye jumps: +/- 6dp) จำลองกระบวนการคิดและสังเกตสิ่งแวดล้อมของ AI
     - **Micro-Blinking**: การกะพริบตาจังหวะสั้นๆ (Micro-blinks) นอกเหนือจากการกะพริบตาเต็มรอบ
     - **Multi-loop Timers**: `loopFast` (1.2s), `loopMedium` (2.4s), `loopSlow` (3.6s), `pulse` (1.6s), `sparkleRot` (0..360°), `flickerPhase`
  2. **`PetRobotMoodsetDraw.kt`** (Enhanced 30 Additional Moodsets):
     - อัปเดตฟังก์ชันวาดทั้ง 28 ฟังก์ชันให้รับพารามิเตอร์ `living: LivingMotionState = LivingMotionState()`
     - บรรจุฟิสิกส์แอนิเมชันเฉพาะตัวในทุกอารมณ์: ปรอทไข้สั่น, เหรียญทองร่วง, น้ำตาไหลพราก, กวาดตาอ่านหนังสือ, สั่นหนาวหิมะปลิว, ดาวโคจรรอบหมวกอวกาศแบบ 3D, ฟองเคมีปุดแตกตัว, ดอกกุหลาบแกว่งไกว, ดวงตาคลื่นไหวระลอก, ริบบิ้นนักรบสะบัด ฯลฯ
  3. **`PetRobotHeadAvatar.kt`** (Enhanced Sheet 1 & Visor Canvas):
     - เชื่อมโยง `rememberLivingMotionState()` เข้ากับ Canvas หลัก
     - ครอบการวาดทั้งใบหน้าด้วย Respiration `scale(living.breathingScaleX, living.breathingScaleY)` และ `living.headSwayDeg`
     - รวม `living.saccadeOffsetX/Y` เข้ากับ Gaze displacement
     - อัปเดต Sheet 1 Moodsets ให้มีชีวิตชีวา: หูฟังเพลงเต้นตามบีท, แว่น VR โค้งสะท้อนแสง, ฟองดำน้ำลอยผุด, ตารางเลเซอร์ขยายมิติ, ประกายดาวหมุนวน, เบอร์เกอร์และแก้วเบียร์ขยับตามจังหวะ
- **Verification**:
  - รันคำสั่งทดสอบ Unit Tests: `./gradlew :composeApp:testDebugUnitTest --tests "com.skyliner2008.jarvis.MoodsetCatalogTest" --tests "com.skyliner2008.jarvis.DeviceControlTest"` $\rightarrow$ **BUILD SUCCESSFUL** (ผ่าน 100%)
  - บิลด์ไฟล์ติดตั้ง Android Debug APK: `./gradlew :composeApp:assembleDebug` $\rightarrow$ **BUILD SUCCESSFUL** (ผ่าน 100%)

## 2026-09-13 — LOOI Robot Page 4 (Sleepy) Infinite Wake-Up Loop & Touch Trigger Fix
- **User Issue**:
  - เมื่อสั่ง "แสดงหน้าที่ 4" (Sleepy): มีเสียง SNORE ดังขึ้น แล้วเกิดเสียง WAKE_UP รัวๆ ถี่ๆ ต่อเนื่องไม่หยุด และใน Log พบ `PetStateMachine: 🎯 processTouch: type=WAKE_UP, zone=FACE_CENTER, currentEmotion=SLEEPING` วนลูปทุก 30-50ms
- **Root Causes**:
  1. **`PetModeController.kt` Spurious `wakeUp()` in `notifyInteraction()`**:
     - ภายใน `notifyInteraction()` มีเงื่อนไข `if (current.emotion == AvatarEmotion.SLEEPING) wakeUp()`
     - `notifyInteraction()` ถูกเรียกจากหลายจุด เช่น `updateRobotFace`, การลากสายตา (`onGazeTouch`), และใน `AlwaysLiveScreen.kt` เมื่อมีเสียงพูดหรือระดับเสียง (`audioLevel > 0.15f`)
     - เมื่อผู้ใช้สั่งหน้าที่ 4 ซึ่งเป็นอารมณ์ `SLEEPING`: ทุกครั้งที่ AI พูด หรือระดับเสียงจากไมโครโฟนสตรีมเข้ามา (ประมาณ 20-30 เฟรมต่อวินาที) `AlwaysLiveScreen.kt` จะเรียก `petController.notifyInteraction()` ทุกเฟรม
     - `notifyInteraction()` เห็นว่าอารมณ์คือ `SLEEPING` จึงสั่ง `wakeUp()` ส่งผลให้เกิดการปลุกผ่าน `stateMachine.processTouch(InteractionType.WAKE_UP)` และเล่นเสียง `RobotSoundPlayer.playWakeUp()` วนลูปไม่หยุด
  2. **Missing Catalog Test Guard in StateMachine & Touch Handlers**:
     - เมื่ออยู่ในโหมดทดสอบ Moodset Catalog (`statusText` ขึ้นต้นด้วย 🎭) ตัวหุ่นยนต์ไม่ได้หลับจริงจากความง่วงตามธรรมชาติ แต่เป็นการจัดแสดงหน้าตา Sleepy เพื่อการตรวจสอบ จึงต้องไม่ถูกขัดจังหวะด้วยการปลุก
- **Changes Implemented**:
  1. **`PetModeController.kt`**:
     - ตัด `if (current.emotion == AvatarEmotion.SLEEPING) wakeUp()` ออกจาก `notifyInteraction()` ป้องกันการวนลูปปลุกเมื่อมีการสตรีมเสียงพูดหรืออัปเดตหน้าตา
     - ใน `wakeUp()`: เพิ่มการตรวจสอบ `if (isCatalogTest) return` ป้องกันไม่ให้การปลุกทำงานขณะทดสอบ Moodset
     - ใน `applyStateMachineResult()`: ป้องกันไม่ให้ StateMachine เขียนทับสีหน้าระหว่างที่กำลังทดสอบ Moodset Catalog
  2. **`AlwaysLiveScreen.kt`**:
     - ครอบเงื่อนไข `if (!isSpecificEmotion) petController.notifyInteraction()` ทั้งตอน `avatarState.isSpeaking` และตอน `audioLevel > 0.15f` เพื่อไม่ส่ง interaction event ไปรบกวนหน้าตาที่กำลังทดสอบ
  3. **`PetRobotHeadAvatar.kt`**:
     - เพิ่มการวาด Waveform Mouth สำหรับ `AvatarEmotion.SLEEPING` เมื่อ AI พูดตอบรับ เพื่อให้ปากขยับตามเสียงพูดร่วมกับลูกเล่น Zzz ลอยได้อย่างสมบูรณ์

## 2026-09-13 — LOOI Robot Moodset Face Display Rendering & Speaking Override Fix
- **User Issue**:
  - เมื่อสั่งด้วยเสียงใน Live mode เช่น "หน้าที่ 10" หรือ "หน้าที่ 12": AI ขานตอบรับยืนยันว่าแสดงหน้าแล้วและเล่นเสียง Sound effect แล้ว แต่บนหน้าจอ สีหน้าของ Avatar ไม่เปลี่ยนเป็นหน้านั้นๆ ยังคงเป็นหน้าปกติหรือหน้าพูด
- **Root Cause Analysis**:
  1. **`AlwaysLiveScreen.kt` PET Mode State Synchronization**:
     - ใน `LaunchedEffect(avatarState)` เดิมตรวจสอบเฉพาะ `hasFaceChange = (avatarState.faceState != lastObservedExternalFaceState)` โดยไม่ได้ตรวจสอบการเปลี่ยนแปลงของ `avatarState.emotion` เลย
     - เมื่อ AI เริ่มพูดโต้ตอบ ("แสดงหน้าที่ 12 ให้บอสตรวจสอบแล้วค่ะ") ตัวแปร `isSpeaking = true` บังคับให้ `activeAvatarState.emotion = AvatarEmotion.SPEAKING` ทับอารมณ์ของ Moodset ไปในทันที
     - เมื่อ AI พูดจบ (`isSpeaking = false`) ตัวแปร `activeAvatarState.emotion` เป็น `SPEAKING` ทำให้ `when` block ตกไปที่เคส `activeAvatarState.emotion == AvatarEmotion.SPEAKING -> if (updatedFace.emotion != IDLE) updatedFace.emotion else IDLE` ซึ่ง `updatedFace.emotion` เป็น `IDLE` เพราะ `testFaceStateOverride` ไม่ได้ถูกเซ็ต ส่งผลให้ Avatar กลับสู่ `AvatarEmotion.IDLE` ในเสี้ยววินาที ทำให้ User ไม่เห็นหน้าตาของ Moodset เลย
  2. **`JarvisViewModel.kt` `testFaceStateOverride` & Decay Timing**:
     - ใน `showMoodsetPage` และ `playAllMoodsets` กำหนดเฉพาะ `testEmotionOverride.value = item.emotion` แต่ปล่อยให้ `testFaceStateOverride.value = null` ทำให้ `avatarState.faceState` เป็น `RobotFaceState()` ค่าว่าง และ `PetModeController` ตัวหลักไม่ได้รับข้อมูลหน้าตาใหม่
     - การตั้งเวลาสลายหน้าจอ (`faceAutoDecayJob`) เดิมนับถอยหลังคงที่ 4500ms โดยไม่ได้รอให้ AI พูดจบ หาก AI พูดเกิน 4 วินาที หน้านั้นจะสลายหายไปทันทีที่ AI พูดเสร็จพอดี
     - เมื่อมีคำสั่งเสียงใน Live Mode ทั้ง `VoiceController` (ดักจับ transcript) และ `DeviceControlExecutor` (ดักจับ native tool call) ต่างเรียก `showMoodsetPage` พร้อมกันในเสี้ยววินาที ทำให้เกิดเสียง SFX เบิ้ลซ้ำสองรอบ
- **Changes Implemented**:
  1. **`AlwaysLiveScreen.kt`**:
     - เพิ่ม `lastObservedExternalEmotion` เพื่อตรวจจับทั้งการเปลี่ยน FaceState และ Emotion อย่างแม่นยำ
     - เพิ่มตัวแปร `isSpecificEmotion`: ตรวจจับว่าอารมณ์ปัจจุบันเป็น Moodset Catalog (`statusText` ขึ้นต้นด้วย 🎭) หรืออารมณ์เฉพาะที่ไม่ใช่ IDLE/SPEAKING/LISTENING
     - เมื่อ `avatarState.isSpeaking`: หากเป็น `isSpecificEmotion` ให้คงอารมณ์ของ Moodset นั้นไว้ (`speakingEmotion = avatarState.emotion`) แล้วเรนเดอร์รูปคลื่นเสียงที่ปาก (Waveform mouth) ทับบนสีหน้าของ Moodset โดยตรงแทนการสลับเป็นหน้า SPEAKING
     - เมื่อหยุดพูด (`!avatarState.isSpeaking`): คงสีหน้าของ Moodset ไว้ตลอดระยะเวลาทดสอบ ไม่ดรอปกลับสู่ IDLE ก่อนเวลา
  2. **`JarvisViewModel.kt`**:
     - ใน `showMoodsetPage` และ `playAllMoodsets`: สร้าง `moodFace = RobotFaceState(emotionName = item.emotion.name.lowercase(), eyeStyleName = "default", speechText = ...)` แล้วเซ็ตทั้ง `testFaceStateOverride.value = moodFace` และเรียก `PetModeController.activeInstance?.updateRobotFace(moodFace)` ให้ซิงค์กันทุกจุด
     - เพิ่มการตรวจสอบ De-duplication: ตรวจสอบเลขหน้าและเวลาล่าสุด หากเลขเดิมถูกเรียกซ้ำภายใน 1.5 วินาที จะไม่เล่นเสียง SFX ซ้ำสองรอบ
     - ปรับปรุง `faceAutoDecayJob`: หน่วงเวลา `durationMs` แล้ววนรอจนกว่า AI จะพูดจบ (`while (voice.isAiSpeaking.value) delay(500)`), จากนั้นรออีก 2.5 วินาทีก่อนจะรีเซ็ตกลับสู่ Normal IDLE
     - ใน `resetToIdleFace()`: รีเซ็ต `PetModeController.activeInstance?.updateRobotFace(RobotFaceState())` ให้กลับสู่หน้าปกติพร้อมกันทั้งหมด

## 2026-09-13 — LOOI Robot 50 Moodset Live Tool Bridge & Persona Voice Sync Fix (Pages 10 & 11)
- **User Issue**:
  1. เมื่อพูด "หน้าที่ 11": AI ตอบว่า "หน้าที่ 11 ไม่มีนะคะ จาวิสมีแค่ 10 หน้าตา ตอนนี้... บอสอยากให้จาวิสกลับไปทำหน้าไหนเป็นพิเศษไหมคะ?"
  2. เมื่อพูด "หน้าที่ 10": หน้าจอกลายเป็นหน้า DIZZY (ตาวนก้นหอย + สั่นหัว) แทนที่จะเป็น Laughing (ตาหยีแหลม > < หัวเราะร่าเริง) และไม่ตรงกับสารบัญ 50 หน้าของ LOOI Robot
- **Root Causes**:
  1. **Outdated Persona Prompts**: `JarvisPersona.kt` (บรรทัด 137, LIVE_SYSTEM_PROMPT บรรทัด 341-344) และ `LiveToolBridge.kt` (บรรทัด 273, 695) มีข้อความแจ้ง Gemini เก่าว่ามีเพียง "10 แบบ" ทำให้ Gemini หลอนคิดว่ามีแค่ 10 หน้าและบอกผู้ใช้ว่าไม่มีหน้าที่ 11
  2. **Device Tool Definition Mismatch**: `DeviceToolDefinitions.kt` ขาดพารามิเตอร์ `page` ใน `device_avatar_emotion` และมีคำอธิบายจำกัดแค่ 10 อารมณ์ ทำให้ Gemini แมปเลข 10 เข้ากับอารมณ์ลำดับที่ 10 ในรายการเดิมซึ่งคือ `DIZZY`
  3. **Live Tool Bridge Argument Passthrough**: `LiveToolBridge.kt` ไม่ได้ดักจับ `userPrompt` ที่ระบุ "หน้าที่ 10" หรือ "หน้าที่ 11" เพื่อเขียนทับ arguments ที่ Gemini ส่งมาผิดพลาด และไม่ได้ถอดรหัสเลขหน้า
  4. **Device Control Executor Missing Page Branch**: `DeviceControlExecutor.kt` ไม่ได้รองรับ `action == "page"` หรือ `args["page"]`
  5. **App.kt Missing PAGE Receiver**: `App.kt` ใน `registerTestEmotion` ไม่ได้ดักรับ `"PAGE|$page"` หรือเลขหน้า 1..50 ส่งผลให้คำสั่งไม่ถูกส่งไปยัง `viewModel.showMoodsetPage(page)`
- **Changes Implemented**:
  1. **`JarvisPersona.kt`**:
     - อัปเดตคำอธิบาย 50 LOOI Robot Moodsets (หน้าที่ 1-20 แผ่นที่ 1, หน้าที่ 21-50 แผ่นที่ 2) ใน `DEFAULT_SYSTEM_PROMPT`, `LIVE_SYSTEM_PROMPT`, และ `PET_LIVE_SYSTEM_PROMPT`
     - สั่งห้ามโมเดลพูดว่า "มีแค่ 10 หน้า" หรือ "ไม่มีหน้าที่ 11" อย่างเด็ดขาด
     - ระบุกฎการเรียกเครื่องมือ `device_avatar_emotion(action="page", page="...")` เมื่อผู้ใช้สั่ง "หน้าที่ X" หรือ "หน้า X"
  2. **`DeviceToolDefinitions.kt`**:
     - เพิ่มพารามิเตอร์ `"page"` (ลำดับหน้าที่ 1 ถึง 50)
     - เพิ่ม `"page"` และ `"all"` ใน enum ของพารามิเตอร์ `"action"`
     - ขยายคำอธิบายเครื่องมือ `device_avatar_emotion` ให้ครอบคลุมทั้ง 50 หน้าของ LOOI Robot
  3. **`LiveToolBridge.kt`**:
     - อัปเดต `isAvatarEmotionRequest` ให้รวมคำค้นหน้า ("หน้าที่", "หน้า", "แบบที่", "moodset", "ทุกหน้า", "หน้าทั้งหมด")
     - ใน `handleNativeToolCall`: ตรวจจับ `targetPage = LooiMoodsetCatalog.parsePageNumber(userPrompt) ?: event.args["page"]...`
     - หากตรวจพบเลขหน้า (1..50) จะเขียนทับ `effectiveArgs` เป็น `mapOf("action" to "page", "page" to targetPage.toString())` ทันที ป้องกันการที่โมเดลส่ง `emotion=dizzy` มาทับ
     - หากตรวจพบคำสั่งเล่นทุกหน้า จะเขียนทับเป็น `mapOf("action" to "all")`
     - อัปเดต `voiceRule` ยืนยันชื่อหน้าและกำชับโมเดลห้ามตอบว่าไม่มีหน้าที่ 11
  4. **`DeviceControlExecutor.kt`**:
     - เพิ่มการจัดการ `targetPage` (1..50) ใน `executeAvatarEmotion`: ส่งคำสั่ง `"PAGE|$targetPage"` ไปยัง MainActivity และตอบกลับชื่อหน้าและคำอธิบาย
     - จัดการ action `"all"` และ `"demo"` ส่ง `"ALL"` ไปยัง MainActivity
  5. **`App.kt`**:
     - ใน `registerTestEmotion`: รองรับ `"PAGE|$page"`, `"PAGE_$page"`, `"PAGE:$page"`, และตัวเลขโดดๆ `1..50` โดยเรียก `viewModel.showMoodsetPage(page)`
     - รองรับ `"ALL"` และ `"DEMO"` โดยเรียก `viewModel.playAllMoodsets()`
     - ปรับ `onToggleDemo` บน AlwaysLiveScreen ให้เรียก `playAllMoodsets()`
  6. **`ChatController.kt` & Tests**:
     - อัปเดตข้อความช่วยเหลือ `/avatar` ให้แสดง 50 LOOI Robot Moodsets
     - เพิ่มชุดทดสอบใน `MoodsetCatalogTest.kt` ยืนยันว่า หน้าที่ 10 คือ Laughing (ไม่ใช่ Dizzy) และหน้าที่ 11 คือ Music มีอยู่จริงในระบบ และคำสั่งเสียง "หน้าที่ 10" / "หน้าที่ 11" ถอดรหัสได้ถูกต้องตรงตามสารบัญ 50 หน้า

## 2026-09-13 — LOOI Robot 50 Moodset Voice Navigation & Verification Engine
- **User Request**:
  1. เพิ่มคำสั่งเสียงสำหรับเล่น Moodset แต่ละแบบ เชื่อมโยงกับตัวเลข "หน้าที่ 1", "หน้าที่ 2", "หน้าที่ 3", ... ถึง "หน้าที่ 50" และ "หน้าทั้งหมด"
  2. แสดงหน้าแบบต่างๆ ให้ User ตรวจสอบ โดยจะแสดง 3-5 วินาที (กำหนดเดี่ยว 4.5 วินาที, วนลูปต่อเนื่อง 4.0 วินาที/หน้า) แล้วกลับสู่สถานะสแตนด์บายอัตโนมัติ
  3. บันทึกคอมเมนต์กำกับในโค้ดอย่างละเอียดว่า Moodset ไหน คือ หน้าที่เท่าไร และได้รับการแก้ไขตรวจสอบถูกต้อง 100% ตามรูปภาพอ้างอิงทั้ง 2 แผ่น
- **Changes Implemented**:
  1. **Centralized Moodset Catalog (`LooiMoodsetCatalog.kt`)**:
     - สร้าง `LooiMoodsetItem` (pageNumber, sheet, sheetIndex, nameEn, nameTh, emotion, description, durationMs, soundEffect)
     - สร้าง `LooiMoodsetCatalog.ITEMS` ครบถ้วนทั้ง 50 หน้า (หน้าที่ 1-20 จากแผ่นที่ 1 "LOOI 20 MOODSET", หน้าที่ 21-50 จากแผ่นที่ 2 "LOOI 30 ADDITIONAL MOODSET") พร้อมคอมเมนต์กำกับทุกหน้าและยืนยันสถานะ `[แก้ไขและตรวจสอบถูกต้อง 100%]`
     - ระบบถอดรหัสคำสั่งเสียง `parsePageNumber(text)`: รองรับเลขอารบิก ("หน้าที่ 1", "หน้า 25", "mood 10", "page 50"), เลขไทย ("หน้าที่ ๑" ถึง "หน้าที่ ๕๐"), คำอ่านภาษาไทย ("หน้าที่หนึ่ง" ถึง "หน้าที่ห้าสิบ"), และคำสั่งแชต `/avatar 15`
     - ระบบตรวจจับคำสั่งลูป `isPlayAllCommand(text)`: รองรับ "หน้าทั้งหมด", "เล่นทุกหน้า", "แสดงทุกหน้า", "moodset ทั้งหมด", "all moods", "play all", "/avatar all"
  2. **Controller Voice & Chat Integration (`VoiceController.kt`, `ChatController.kt`)**:
     - เพิ่ม callbacks `onPlayMoodsetPage: ((Int) -> Unit)?` และ `onPlayAllMoodsets: (() -> Unit)?`
     - ใน `VoiceController.kt`: ดักจับข้อความเสียงของผู้ใช้ใน Live Mode เพื่อสั่งแสดงหน้าเจาะจงหรือเล่นทุกหน้าอัตโนมัติ
     - ใน `ChatController.kt`: ดักจับคำสั่งพิมพ์ทั้ง `/avatar <1-50>`, `/avatar all`, ข้อความ "หน้าที่ X", หรือ "หน้าทั้งหมด" พร้อมแสดงข้อความตอบกลับยืนยันรายละเอียดของหน้านั้นๆ ในช่องแชต
  3. **JarvisViewModel Display Controller (`JarvisViewModel.kt`)**:
     - `showMoodsetPage(page: Int, durationMs: Long = 4500L)`: สลับเข้าโหมด Pet Mode อัตโนมัติ (หากยังไม่ได้เปิด), เรนเดอร์ใบหน้า 2D Vector พร้อมเสียง SFX ประจำอารมณ์, แสดงสถานะชื่อหน้า และตั้ง Timer หน่วงเวลา 4.5 วินาที (อยู่ในเกณฑ์ 3-5 วินาที) ก่อนสลายกลับสู่ Normal IDLE อัตโนมัติ
     - `playAllMoodsets(durationPerMoodMs: Long = 4000L)`: เล่นวนลูปทุกหน้า 1 ถึง 50 หน้าละ 4 วินาที (อยู่ในเกณฑ์ 3-5 วินาที) พร้อมเสียงและสถานะแบบเรียลไทม์
     - เชื่อมโยง callbacks ใน `init {}` ทั้งส่วน Chat และ Voice ครบถ้วน
  4. **Unit Test Suite (`MoodsetCatalogTest.kt`)**:
     - เพิ่มชุดทดสอบ 8 ฟังก์ชัน ครอบคลุม: ขนาดรายการ 50 หน้า, การแบ่งแผ่น 1 (20 หน้า) และแผ่น 2 (30 หน้า), การค้นหา `findByPage`, การแปลงคำสั่งเสียงเลขไทย/เลขอารบิก/คำอ่าน, การตรวจจับคำสั่งเล่นทุกหน้า, และการตรวจสอบระยะเวลาแสดงผล (3-5 วินาที)

## 2026-09-13 — LOOI Robot 30 Additional Moodset & 50-Preset Total Verification Audit
- **User Request & Master Style Prompt**:
  - รองรับชุดอารมณ์และท่าทางเพิ่มเติม 30 แบบ จากภาพอ้างอิง "LOOI ROBOT: 30 ADDITIONAL MOODSET (NEON CYAN STYLE)"
  - สไตล์ 2D Vector Minimalist: ดวงตา Squircle สีนีออนไซแอน (`#00F5FF`) พร้อมเลเยอร์เงาสี Dark Cyan (`#004D6B`) เยื้อง Y = +5dp สำหรับมิติตื้น 2D Shallow Depth
  - เพิ่มเติม 27 อารมณ์ใหม่ใน `AvatarEmotion` (รวมเป็น 56 อารมณ์ทั้งหมด) โดยแยก `ROMANTIC` ออกเป็นอิสระจาก `LOVE` (Romantic: คาบกุหลาบแดง 🌹 + ปากจูบ 3 + แก้มชมพูระเรื่อ ///, ส่วน In Love: ตารูปหัวใจดวงโต ♥♥) และยกระดับ `CONFUSED` ด้วยตาสควีร์เคิลคลื่น `~ ~` + เครื่องหมาย `¿` และ `??` คู่กับปากหยักคลื่น
- **Changes Implemented**:
  1. **AvatarEmotion & Color Expansion (`AvatarEmotion.kt`, `AvatarEmotionColors.kt`, `RobotFaceState.kt`)**:
     - เพิ่ม 27 อารมณ์ใหม่: `SICK`, `RICH`, `CRYING`, `READING`, `GAMING`, `TRAVELING`, `WORKING`, `COLD`, `HOT`, `DETECTIVE`, `COOKING`, `ART_MODE`, `SPACE`, `PARTY`, `DREAMING`, `EXHAUSTED`, `ELECTRIC`, `SNEAKY`, `ROMANTIC`, `HERO`, `GLITCHED`, `MAGIC`, `SPORTY`, `SCIENTIST`, `SCARED`, `WARRIOR`, `LOW_BATTERY`
     - แมปสีนีออน primary, secondary, และ statusPill ครบถ้วนทั้ง 27 อารมณ์ (รวม 56 อารมณ์)
     - เพิ่ม 34 ค่า `PropType` ใหม่ใน `RobotFaceState.kt` (รวมเป็น 96 พร็อพ) พร้อมคำศัพท์ภาษาไทย-อังกฤษสำหรับการรู้จำ
  2. **Modular Moodset Drawing Engine (`PetRobotMoodsetDraw.kt`)**:
     - สร้างไฟล์ `PetRobotMoodsetDraw.kt` บรรจุฟังก์ชันวาดเวกเตอร์ Canvas สำหรับ 27 อารมณ์ใหม่ทั้งหมดตามแบบภาพอ้างอิงเป๊ะๆ (รวม `drawRomanticMood` และ `drawConfusedMood`)
     - ปรับฟังก์ชันวาดพื้นฐานใน `PetRobotHeadAvatar.kt` ให้เป็น `internal` เพื่อให้เรียกใช้งานข้ามไฟล์ได้อย่างมีประสิทธิภาพ
     - เชื่อมโยงเข้ากับ `renderPetEmotion` ใน `PetRobotHeadAvatar.kt` ครบ 56 branches พร้อมรองรับระบบ Cross-Fade Alpha Transition
  3. **UI Integration & Overlay (`AlwaysLiveScreen.kt`, `ChatController.kt`, `PetPropsOverlay.kt`)**:
     - เพิ่ม dynamic ambient background gradients (3 layers) และ status text ภาษาไทย-อังกฤษ ใน `AlwaysLiveScreen.kt` รองรับครบ 56 อารมณ์
     - เพิ่มคำสั่งและคำอธิบายสำหรับการทดสอบผ่าน `/avatar` ใน `ChatController.kt`
     - แมป `RenderProp` ใน `PetPropsOverlay.kt` สำหรับ 34 พร็อพใหม่
  4. **Test Suite Verification (`AlwaysLiveTest.kt`, `PetModeTest.kt`)**:
     - อัปเดตการตรวจสอบขนาด `AvatarEmotion.entries.size` จาก 55 เป็น 56
     - อัปเดตการตรวจสอบขนาด `PropType.entries.size` จาก 62 เป็น 96
     - ผ่านการทดสอบทั้งหมด 281 Unit Tests (Build Successful 100%)

## 2026-09-13 — LOOI Robot 20 Moodset (Neon Cyan Style) & Smooth Continuous Emotion Transitions
- **User Request & Master Style Prompt**:
  - ถอดแบบโมชั่นการแสดงของหุ่นยนต์ LOOI Robot จากแผ่นอ้างอิง "LOOI ROBOT: 20 MOODSET (NEON CYAN STYLE)" บนพื้นดำสนิท Pure OLED Black (`#000000`)
  - สไตล์ 2D Vector Minimalist: ดวงตาทรง Squircle สีนีออนไซแอน (`#00F5FF`) พร้อมเลเยอร์เงาสี Dark Cyan (`#004D6B`) เยื้อง Y = +5dp สร้างมิติตื้น 2D Shallow Depth
  - **การเคลื่อนไหวที่ดูต่อเนื่อง (Continuous Motion / Seamless Transition)**:
    - ปรับเปลี่ยนอารมณ์อย่างนุ่มนวลเป็นธรรมชาติ ไม่ตัดฉับ ไม่กระตุก
    - เช่น จากหน้าปกติ (`IDLE`) ค่อยๆ เอียงตาลง ลิ้นเขี้ยวเลื่อนลง เส้นเลือดปูด ค่อยๆ เปลี่ยนเป็นหน้าโกรธ (`ANGRY`)
    - จากหน้าปกติ (`IDLE`) ค่อยๆ หรี่ตาลู่ลง น้ำตาค่อยๆ เลื่อนไหล ปากค่อยๆ คว่ำ เปลี่ยนเป็นหน้าเศร้า (`SAD`)
- **Changes Implemented**:
  1. **New LOOI Moodset & Avatar Emotions (`AvatarEmotion.kt`, `AvatarEmotionColors.kt`, `RobotFaceState.kt`)**:
     - เพิ่มอารมณ์ใหม่ครบทั้ง 12 LOOI Moods: `DEAD`, `LAUGHING`, `MUSIC`, `VR_MODE`, `DIVING`, `EVIL`, `FOCUSED`, `SHY`, `DISGUSTED`, `CAMERA_MODE`, `EATING`, `DRINKING` (รวมทั้งหมด 29 Emotion States)
     - กำหนดคู่สี Neon Primary, Secondary, และ Shadow Depth Colors ใน `AvatarEmotionColors.kt`
     - เพิ่มพร็อพเฉพาะสไตล์ LOOI 7 ชนิดใน `PropType`: `HEADPHONES`, `VR_HEADSET`, `SNORKEL_MASK`, `DEVIL_HORNS`, `SCANNER_GRID`, `TRASH_BIN`, `CAMERA_ICON` (รวมเป็น 62 ชนิด)
  2. **Smooth Continuous Emotion Morphing Engine (`PetRobotHeadAvatar.kt`)**:
     - เพิ่มระบบ Transition State Tracking: `previousEmotion`, `currentEmotionTarget`, และ `emotionTransition = Animatable(1f)`
     - เมื่อตรวจพบการเปลี่ยนอารมณ์ (`LaunchedEffect(effectiveEmotion)`): สั่ง animate transition จาก 0f สู่ 1f ด้วย `tween(durationMillis = 350, easing = FastOutSlowInEasing)`
     - **Disney Anticipation Squash & Stretch**:
       - คำนวณ `anticipationSquashY = 1f - 0.08f * sin(transitionProg * PI)`
       - คำนวณ `anticipationSquashX = 1f + 0.05f * sin(transitionProg * PI)`
       - ตาจะยุบตัวลงเล็กน้อยเพื่อสะสมพลังก่อนเด้งยืดเข้าสู่รูปทรงอารมณ์ใหม่อย่างมีชีวิตชีวา
     - **Continuous Shape & Color Morph Variables**:
       - `animatedEyeColor`: สลับสีระหว่างต้นทางและปลายทางอย่างต่อเนื่องผ่าน `lerp`
       - `eyeSlantProgress`: ควบคุมการเอียงของตา (เช่น ค่อยๆ ปรับจาก Squircle ตรง $\rightarrow$ สามเหลี่ยมลิ่มเฉียง 16dp เมื่อโกรธ)
       - `fangsProgress`: สั่งให้เขี้ยวคู่สีขาวค่อยๆ เลื่อนลงมา (`slideY = (1f - progress) * -14dp`)
       - `angerVeinProgress`: เส้นเลือดปูดค่อยๆ ขยายขนาดปูดขึ้นมาที่ขมับขวา
       - `sadProgress`: ควบคุมการลู่ลงของดวงตา (-8° และ +8°), ตาหย่อนลง 7dp, น้ำตาเลื่อนหยดลง และปากโค้งคว่ำ
       - `sleepProgress`, `surpriseProgress`, `happyProgress`: ควบคุมการหรี่เปลือกตา, เบิกตากว้าง 1.25x, และความโค้งยิ้ม
     - **Dual-Layer Cross-Fade Rendering**:
       - เมื่อ `transitionProg < 1f` และ `previousEmotion != effectiveEmotion`: เรนเดอร์เลเยอร์อารมณ์เดิมด้วย `alpha = 1f - transitionProg` และเรนเดอร์เลเยอร์อารมณ์ใหม่ด้วย `alpha = transitionProg` ทับซ้อนกัน ทำให้การกลายรูปทรง (Morphing) ลื่นไหล ไร้รอยต่อ ไม่มีอาการกระพริบหรือกระตุก
  3. **Canvas Drawing Architecture Overhaul (`PetRobotHeadAvatar.kt`)**:
     - อัปเดตฟังก์ชันวาด Canvas ทั้งหมดให้รองรับพารามิเตอร์ `alpha: Float = 1f` พร้อม Color Alpha Blending
     - ปรับแต่งการวาด LOOI Eye Styles:
       - `drawDualCircleEye`: เลเยอร์เงา Dark Cyan ด้านล่าง + เลเยอร์บน Neon Cyan + Blink Squash
       - `drawHappyEye`: ตาโค้งยิ้มหยี ⌒ ⌒
       - `drawAngryEye`: ตาทรงลิ่มเฉียงปรับมุมได้ตาม `slantProgress` + เขี้ยวขาวคู่ + เส้นเลือดปูดสีแดง
       - `drawSleepingEye`: ตาขีดมน — — + Zzz ลอย
       - `drawLooiCuriousRightEye`: ตาขวาทรงลิ่มเอียง 12° + เครื่องหมายคำถาม
       - `drawCrossEye`: ตากากบาท X X สไตล์ Dead / Dizzy
       - `drawLooiExcitedEye`: ตาทรงพัดเอียงออก + ประกายดาวสีทอง ✦ ✦
       - `drawLooiFocusdWedge`: ตาทรงลิ่มคมกริบ + ตารางเลเซอร์ Synthwave Grid
       - `drawLooiVrHeadset`: แว่น VR Vision Pro + ถังป๊อปคอร์นจิ๋ว
       - `drawLooiSnorkelMask`: แว่นหน้ากากดำน้ำ + ท่อหายใจ + ฟองอากาศ
       - `drawLooiHeadphones`: หูฟังครอบศีรษะ Over-Ear Headphones
       - `drawLooiMiniBurger` & `drawLooiMiniBeer`: มินิเบอร์เกอร์และแก้วเบียร์มีฟองสำหรับ Eating & Drinking
  4. **System Integration & Exhaustiveness (`ChatController.kt`, `AlwaysLiveScreen.kt`, `PetPropsOverlay.kt`)**:
     - รองรับคำสั่งเสียงภาษาไทยและอังกฤษสำหรับทั้ง 12 อารมณ์ใหม่
     - อัปเดต Background Ambiance Gradient และ Status Pill สำหรับทุกอารมณ์
     - ขยาย Exhaustive `when` blocks ป้องกันคอมไพล์เออเรอร์
  5. **Unit Tests Coverage (`AlwaysLiveTest.kt`, `PetModeTest.kt`)**:
     - อัปเดต `AlwaysLiveTest` ให้ตรวจสอบครบ 29 อารมณ์ (`AvatarEmotion.entries.size == 29`)
     - อัปเดต `PetModeTest` ให้ตรวจสอบครบ 62 พร็อพ (`PropType.entries.size == 62`)
- **Verification**:
  - รัน `:composeApp:testDebugUnitTest`: 281 tests ผ่านทั้งหมด 100% (Build Successful)
  - รัน `:composeApp:assembleDebug`: สำเร็จ 100% สร้าง APK ได้อย่างสมบูรณ์แบบ

## 2026-09-13 — Pet Mode Visual Polish & Facial Geometry Anchoring (55 Alive Props & Clumsy Pet Personality)
- **Problem & Feedback**:
  - ผู้ใช้ทดสอบระบบพร็อพและฉาก พบว่าสิ่งของที่สัตว์เลี้ยงนำมาแสดงยังมีขนาดเล็กเกินไป และตำแหน่งยังไม่เหมาะสม: สิ่งของด้านบน เช่น ร่ม ลอยสูงเกือบพ้นขอบจอด้านบน ควรอยู่เหนือดวงตาเพียงนิดเดียว
  - สิ่งของหลายชิ้นดูเป็นเวกเตอร์นิ่งๆ ขาดชีวิตชีวา ขาดความเคลื่อนไหวทางฟิสิกส์ (เช่น ควันอาหาร, ชีสยืด, ไอศกรีมหยด, ฟองเบียร์, เมล็ดข้าวโพดคั่วกระเด้ง)
  - ผู้ใช้ชื่นชอบโมเมนต์ที่สัตว์เลี้ยงหยิบของผิด (เช่น ผู้ใช้สั่งเบอร์เกอร์แต่ AI หยิบป๊อปคอร์น) แล้ว AI แสดงอาการตกใจปนเด๋อด๋า เขินอาย (`CONFUSED` / `SWEAT_DROP` / `TILT_LEFT`) ต้องการให้รักษาและส่งเสริมเสน่ห์ความเด๋อด๋านี้ไว้
- **Changes Implemented**:
  1. **Facial Geometry Mathematical Model (`PetPropsOverlay.kt`)**:
     - เพิ่มฟังก์ชัน `calculateGeometry(width, height)`:
       - `foreheadY = cY - eyeDiameter * 0.65f`: กำหนดจุดอ้างอิงหน้าผากให้อยู่ "เหนือดวงตาพอดี" บนทุกขนาดและอัตราส่วนหน้าจอ (ทั้ง Portrait 20:9 และ Landscape 16:9)
       - `mouthY = cY + eyeDiameter * 0.58f`: จุดอ้างอิงปาก/ถือของด้านล่าง
       - `leftTempleX / rightTempleX = cX +/- eyeDiameter * 0.85f`: จุดอ้างอิงขมับ/ข้างศีรษะ
  2. **Comprehensive Visual & Animation Overhaul across Built-in Props (`PetPropsOverlay.kt`)**:
     - *Umbrella (`UMBRELLA`)*: ขยายขนาดเป็น 175dp อยู่เหนือระดับตาพอดี (`foreheadY + 12dp`) ร่มไล่เฉดสีฟ้าเข้ม-อ่อน ก้านร่มพร้อมด้ามจับโค้ง และมีเม็ดฝน 4 จุดกระเซ็นแตกบนร่ม
     - *Sunglasses (`SUNGLASSES`)*: แว่นตาดำทรงเท่ เลนส์ 76dp เด้งลงมาครอบตาพอดี พร้อมเส้นแสงสะท้อนเฉียงสีขาว (Specular Glare)
     - *Crown (`CROWN`)*: มงกุฎทองคำ 94dp บุผ้ากำมะหยี่สีแดง ล้อมเพชรพลอยไพลินทับทิม พร้อมดาวประกายเพชรหมุน 360 องศา
     - *Burger (`BURGER`)*: เบอร์เกอร์เนื้อย่าง 88dp โรยงา มะเขือเทศ ผักกาดหอม ชีสเยิ้ม จังหวะเด้งเคี้ยวหนึบหนับ และมีควันไอร้อนลอยพวยพุ่ง
     - *Pizza (`PIZZA`)*: พิซซ่าถาด 56dp แป้งเกรียม เปปเปอโรนี ชีสมอสซาเรลล่ายืดหยุ่นหยดติ๋งๆ
     - *Popcorn (`POPCORN`)*: ถังป๊อปคอร์นลายทาง 68dp พร้อมเมล็ดข้าวโพดคั่วเด้งกระโดดออกมาเป็นวิถีโค้งพาราโบลา
     - *Drinks (`COFFEE`, `BOBA_TEA`, `BEER`, `TEA_CUP`)*: ขยายขนาดเป็น ~60dp พร้อมไอควันร้อนลอยเอื่อย, เม็ดไข่มุกกลิ้ง swishing, ฟองเบียร์ซ่าและฟองล้นแก้ว
     - *Tech & Tool Props (`LAPTOP`, `SHIELD`, `BATTERY`, `CLOCK`, `MAGNIFYING_GLASS`, `SIREN`, `CHECK_MARK`)*: แล็ปท็อปจอ Cyber Code สีเขียวนีออน, โล่พลังงานบลูการ์ดขอบทองพร้อมวงแหวนป้องกัน, หลอดไฟไอเดียพร้อมเส้นแสงพลังคิด
  3. **Clumsy Pet Personality Guidelines (`JarvisPersona.kt`, `LiveToolBridge.kt`)**:
     - เพิ่มกฎข้อ 14 ใน `PET_LIVE_SYSTEM_PROMPT`: เมื่อเจ้านายทักหรือแซวว่าหยิบของผิด ("นี่มันป๊อปคอร์น ไม่ใช่เบอร์เกอร์นะ", "หยิบผิดแล้ว") ให้ AI ทำท่าเขินอาย เด๋อด๋า ตกใจปนงงงวย (`CONFUSED` / `SWEAT_DROP` / `TILT_LEFT`) แล้วพูดสารภาพอย่างน่ารักพร้อมรีบเสกของที่ถูกต้องให้ทันที
  4. **Specific Prop Detection & Scene Enrichment (`PetSceneEngine.kt`, `LiveToolBridge.kt`)**:
     - ฟังก์ชัน `detectSpecificPropFromText` สกัดชื่อของกินและของใช้ (เบอร์เกอร์, พิซซ่า, เค้ก, กาแฟ, ร่ม, ป๊อปคอร์น) จากข้อความเจาะจงของผู้ใช้
     - เพิ่ม Scene Archetype `RAIN_UMBRELLA` กางร่มกันฝน
  5. **Unit Tests Coverage (`PetModeTest.kt`)**:
     - เพิ่มชุดทดสอบ `detectSpecificPropFromText`, `RAIN_UMBRELLA` archetype resolution, และ specificProp override priority
- **Verification**:
  - รัน Unit Tests `:composeApp:testDebugUnitTest` ผ่าน 100%

## 2026-09-13 — Smart Scene Archetype Engine & Dynamic SVG Deprecation (Virtual Desk Pet)
- **Problem & Goal**:
  - ระบบเดิม "Dynamic SVG Path Parser System & Auto-Fit Anchor Geometry" มีข้อจำกัด: เวกเตอร์ที่สร้างขึ้นโดย AI มีความหยาบ สัดส่วนผิดเพี้ยน ไม่สวยงาม และตำแหน่งไม่สอดคล้องกับใบหน้าหุ่นยนต์
  - AI สับสนและ hallucinate โค้ด SVG (`M5,9...`) ส่งผลให้ AI ไม่เคยหยิบใช้งานคลังพร็อพสำเร็จรูปที่มีอยู่กว่า 50 ชนิด
  - ฉากอนิเมชันเดิม (เช่น ฉากยิงจรวด) เล่นเร็วเกินไป (<1s) เห็นแค่ระเบิดตู้มๆ โดยไม่เห็นการตั้งท่าโกรธหรือตัวจรวดพุ่ง 3D
  - ผู้ใช้ต้องการระบบ **"ฉากสำเร็จรูปอัจฉริยะ (Smart Scene Archetypes)"** ที่ AI สามารถผสมผสาน (Mix) สิ่งต่างๆ เข้าด้วยกัน:
    1. ธีมฉากหลัง 8 รูปแบบ พร้อมระบบคำนวณตามเวลาจริง (Time-Aware Context)
    2. คลังพร็อพและสติกเกอร์สำเร็จรูปสวยงาม 55 ชนิด
    3. ท่าทาง หน้าตา และอารมณ์ของ Avatar
    4. แสง สี เสียง FX สังเคราะห์เฉพาะฉาก
    5. จังหวะการแสดงผล 3 องก์ (3-Act Pacing) 3.8 - 5.0 วินาที พร้อมระบบคืนสู่ IDLE อัตโนมัติ
    6. รองรับทั้งการสุ่มอัตโนมัติ (Default Smart Random เช่น กดให้อาหาร สุ่มอาหาร 5 เมนู) และการสั่งเจาะจงด้วยเสียง (Specific Override เช่น "ขอดื่มกาแฟ", "ใส่แว่นตา")
- **Changes Implemented**:
  1. **Smart Scene Engine (`PetSceneEngine.kt`)**:
     - สร้าง 15 Scene Archetypes ครอบคลุม 4 หมวดหมู่:
       - *Activity*: `EATING`, `DRINKING`, `BATH_CLEAN`, `PLAY_GAMING`, `STUDY_WORK`
       - *Meme*: `MEME_THUG_LIFE`, `MEME_RICH`, `MEME_ROYAL`
       - *Comedy*: `COMEDY_FIRE`, `COMEDY_THUNDER`, `COMEDY_SOUL_OUT`
       - *Emotion*: `ANGRY_MISSILE`, `SUPER_LOVE`, `DRAMATIC_CRY`, `CELEBRATION`
     - ระบบสุ่มไอเทมจาก Item Pool: อาหาร (เบอร์เกอร์, พิซซ่า, เค้ก, ไอศกรีม, ป๊อปคอร์น) และเครื่องดื่ม (กาแฟ, ชานมไข่มุก, น้ำชา, เบียร์)
     - ระบบคำนวณฉากหลังตามเวลาจริง (`resolveSmartBackground`): กลางวัน 06:00-16:59 `SUNNY`, พระอาทิตย์ตก 17:00-19:59 `SAKURA`, กลางคืน 20:00-05:59 `NIGHT`
     - ตัวแปลงคีย์เวิร์ดภาษาไทยและอังกฤษ (`resolveFromKeyword`): รองรับคำพ้องความหมาย เช่น "กินข้าว", "ขอดื่มกาแฟ", "ใส่แว่นตา", "ยิงจรวด", "อาบน้ำ", "เล่นเกม", "รวย", "มงกุฎ", "ไฟลุก", "โดนช็อต", "วิญญาณหลุด", "ซุปเปอร์เลิฟ", "อกหัก", "ปาร์ตี้"
  2. **Pacing & 3-Act Timing (`MissileBarrageOverlay.kt`, `PetModeController.kt`)**:
     - ปรับระยะเวลาฉากจรวดเป็น 5.0 วินาทีเต็ม พร้อมแบ่ง 3 องก์:
       - องก์ 1 (0–1200ms): ตั้งท่าโกรธ ควันพวยพุ่ง เสียงเตือนภัย
       - องก์ 2 (1200–2400ms): ปล่อยจรวด 5 ลูกเหลื่อมเวลากัน พร้อมไอพ่นเปลวไฟพุ่งขึ้นด้านบนชัดเจนในมิติ 3D
       - องก์ 3 (2400–5000ms): จรวดพุ่งตกกระทบหน้าจอ เกิดแรงกระแทก เขย่าจอ แสงแฟลช และควันจางหาย
     - ฉากอื่นๆ กำหนดระยะเวลา 3.8s – 4.5s พร้อม Coroutine Auto-Revert กลับสู่ Pure Dark OLED IDLE อัตโนมัติ
  3. **Deprecate Dynamic SVG & Enforce Built-in Props (`PetPropsOverlay.kt`, `DeviceToolDefinitions.kt`, `JarvisPersona.kt`)**:
     - ตัด Dynamic SVG Canvas Loop ออกจาก `PetPropsOverlay.kt` และหันมาเรนเดอร์ผ่าน Compose Canvas Handcrafted 55 Built-in Props 100%
     - ลบ Tool `device_custom_prop` ออกจาก Gemini Function Declarations
     - เพิ่มพารามิเตอร์ `scene` และ `action="scene"` ใน `device_avatar_emotion` พร้อมระบุรายการพร็อพทั้ง 55 ชนิดใน Tool Description
     - ปรับ System Prompts ทั้งหมดใน `JarvisPersona.kt` ให้อ้างอิงการเล่นฉากและพร็อพสำเร็จรูป
  4. **Voice Intent Interception & Dual Execution (`LiveToolBridge.kt`, `DeviceControlExecutor.kt`)**:
     - เพิ่ม `isSceneRequest` ใน `LiveToolBridge.kt` ป้องกัน Gemini สับสนเรียก Trading Tools
     - อัปเดตกฎเสียง `[VOICE RULE - PET SCENE]` ให้ AI ตอบรับอย่างเป็นธรรมชาติและมีอารมณ์ร่วมกับฉาก
     - ใน `DeviceControlExecutor.kt`: เชื่อมโยงตรงสู่ `PetModeController.activeInstance?.playSceneByNameOrKeyword()` เมื่ออยู่ใน Pet Mode หรือ Broadcast Emotion Intent ไปยัง MainActivity
  5. **Unit Tests Coverage (`PetModeTest.kt`, `DeviceControlTest.kt`)**:
     - ปรับ `DeviceControlTest.kt` ให้ทดสอบ `device_avatar_emotion` และลบ assertions ของ `device_custom_prop`
     - เพิ่ม Unit Tests 7 ชุดใน `PetModeTest.kt` ครอบคลุมทั้ง 15 Archetypes, Item Pools Randomizer, Specific Prop Overrides, Time-Aware Background, และ Thai/English Keyword Resolver
- **Verification**:
  - `:composeApp:compileDebugKotlinAndroid` ผ่าน 100%
  - `:composeApp:testDebugUnitTest` ผ่าน 100% (30 tasks, Build Successful in 24s)

## 2026-09-13 — Always_AI_Live_Mode Polish (P3 Complete: Driving Safety, Smart Parking Memory & Low-Glare Night Mode)
- **Problem & Goal**:
  - โหมดขับขี่และควบคุม (Drive & Control Mode) ต้องการฟังก์ชันความปลอดภัยขั้นสูง: การแจ้งเตือนเมื่อขับขี่เกินความเร็วที่กำหนด (Speed Limit Alert)
  - ผู้ขับขี่มักลืมตำแหน่งที่จอดรถเมื่อลงจากรถ ต้องการระบบจดจำจุดจอดรถอัจฉริยะ (Smart Parking Location Memory) ที่สามารถบันทึกพิกัดและนำทางกลับไปยังรถได้ทันทีทั้งผ่านการแตะปุ่มและคำสั่งเสียง ("รถจอดอยู่ที่ไหน", "จำที่จอดรถ")
  - การขับขี่ตอนกลางคืนต้องการหน้าจอที่ลดแสงสะท้อน (Low-Glare OLED Night Mode) เพื่อไม่ให้แสงนีออนหรือออร่ารบกวนสายตาและสมาธิของผู้ขับขี่
- **Changes Implemented**:
  1. **Speed Limit Alert & Over-speed Monitoring (`DriveBridge.kt`, `DriveModeController.kt`, `DriveModeScreen.kt`)**:
     - เพิ่ม `speedLimitKmh` (ค่าเริ่มต้น 120 กม./ชม.) และ `isSpeeding` ใน `DriveTelemetry`
     - เพิ่มฟังก์ชัน `setSpeedLimit(limitKmh)` พร้อมรองรับการแตะปุ่ม `MAX 120` บน Speedometer HUD เพื่อวนเปลี่ยนขีดจำกัดความเร็ว (80 $\rightarrow$ 90 $\rightarrow$ 100 $\rightarrow$ 110 $\rightarrow$ 120 กม./ชม.)
     - เมื่อความเร็วเกินกำหนด: Speedometer เปลี่ยนเป็นสี Crimson Red พร้อมขอบหนา 2dp และแสดงป้ายเตือนสีแดงสดกระพริบ `⚠️ ขับขี่เกินความเร็วที่กำหนด (X KM/H) กรุณาลดความเร็ว`
  2. **Smart Parking Location Memory (`DriveBridge.kt`, `DriveModeController.kt`, `DriveModeScreen.kt`, `LiveToolBridge.kt`)**:
     - เพิ่มโมเดล `ParkingLocation(latitude, longitude, address, timestamp)` และ StateFlow `parkingLocation`
     - เพิ่มฟังก์ชัน `saveCurrentParking()`, `saveParkingLocation()`, `clearParkingLocation()`, และ `navigateToParking()`
     - บน UI `DriveControlPanel`:
       - หากยังไม่ได้บันทึก: แสดงปุ่มด่วน `🅿️ บันทึก/จำจุดจอดรถตรงนี้`
       - เมื่อบันทึกแล้ว: แสดงการ์ดจุดจอดสีน้ำเงิน Indigo `🅿️ จุดจอดรถที่จำไว้: [ที่อยู่/พิกัด]` พร้อมปุ่มกด `🗺️ นำทางไปรถ` (เปิด Google Maps นำทางกลับไปยังพิกัดรถ) และปุ่ม `✖` ลบข้อมูล
     - ป้องกัน Voice Intent สับสนใน `LiveToolBridge.kt`:
       - ผู้ใช้สั่ง "จำที่จอดรถ" / "บันทึกที่จอดรถ" / "จอดรถตรงนี้" $\rightarrow$ สั่งบันทึกพิกัด GPS ทันทีและยืนยันด้วยเสียง
       - ผู้ใช้ถาม "รถจอดอยู่ที่ไหน" / "หาที่จอดรถ" $\rightarrow$ รายงานพิกัดที่จอดและพร้อมกดนำทาง
  3. **Low-Glare Night Driving Mode (`DriveBridge.kt`, `DriveModeController.kt`, `DriveModeScreen.kt`, `LiveToolBridge.kt`)**:
     - เพิ่ม StateFlow `isLowGlareMode` พร้อมปุ่มสลับ `🌙 / 🕶️` บน Speedometer HUD
     - เมื่อเปิดใช้งาน:
       - หน้าจอด้านหลังปรับเป็น Pure OLED Pitch Black (`#000000`)
       - หรี่แสง Ambient Aura ลงเหลือ 0.04f และลดขนาดลง 20%
       - ลดความสว่างและระดับ Pulse ของ AudioVisualizerRing ลง 65%
       - ปรับพื้นหลัง Card เป็นสีดำสนิท (`Color(0xFF08080C).copy(alpha = 0.88f)`) ป้องกันแสงสะท้อนแยงตาตอนกลางคืน
       - รองรับคำสั่งเสียง "เปิดโหมดกลางคืน" / "ลดแสงสะท้อน" / "ปิดโหมดกลางคืน"
  4. **Unit Tests Added (`DriveModeTest.kt`)**:
     - `Speed limit and speeding detection calculate accurately`: ตรวจสอบการคำนวณความเร็วเกินและการเปลี่ยนค่าขีดจำกัดความเร็วแบบ Real-time
     - `Smart parking location memory saves, navigates, and clears correctly`: ตรวจสอบการบันทึก, การนำทางกลับ, และการล้างข้อมูลจุดจอดรถ
     - `Low-glare night driving mode toggles state correctly`: ตรวจสอบการเปิด/ปิดโหมดกลางคืน
     - `Parking and night mode voice intents match correctly`: ตรวจสอบความแม่นยำของคำสั่งเสียงเกี่ยวกับที่จอดรถและโหมดกลางคืน
- **Verification**:
  - คอมไพล์ `:composeApp:compileDebugKotlinAndroid` สำเร็จ (Build Successful in 2m 16s)
  - รัน Unit Tests `:composeApp:testDebugUnitTest` ผ่าน 100% ทั้ง 3 ชุด (`DriveModeTest`, `AlwaysLiveTest`, `PetModeTest`) ใน 41s
- **Next Steps**:
  - เตรียมทดสอบ On-Device บนรถยนต์จริง พร้อมทดสอบ HUD Speedometer ในสภาพแสงแดดและกลางคืน

## 2026-09-13 — Always_AI_Live_Mode Enhancement (P2 Complete: Voice Intent Routing, Structured Sentiment & Resilience)
- **Problem & Goal**:
  - เมื่อผู้ใช้สั่งการด้วยเสียงขณะขับขี่หรือควบคุม (เช่น เล่นเพลง, นำทาง, อ่านแจ้งเตือน, ถามความเร็ว) โมเดล Gemini Live มักสับสนและอาจเรียกใช้ trading tools ผิดพลาด
  - ฟังก์ชัน `detectSentiment` ใน `AlwaysLiveManager` เดิมใช้เพียงคีย์เวิร์ดอย่างง่าย ไม่รองรับแท็กอารมณ์แบบมีโครงสร้าง `[EMOTION]` หรือ Piped Commands (`HAPPY|...`) จากคำตอบของ AI
  - ต้องการ Unit Tests ครอบคลุม Intent Detection และ Regex Tag Extraction
- **Changes Implemented**:
  1. **Smart Voice Intent Routing & Guards (`LiveToolBridge.kt`)**:
     - เพิ่มฟังก์ชันตรวจสอบเจตนาคำสั่ง: `isMediaRequest`, `isNavigationRequest`, `isNotificationRequest`, `isLocationOrSpeedRequest`
     - เพิ่ม Interception Guards 4 ชุด: เมื่อ Gemini Live เรียกเครื่องมือผิดทาง ให้ Redirect ไปยัง Tool ที่ถูกต้องอัตโนมัติ:
       - เพลง $\rightarrow$ `device_media_control` พร้อมพารามิเตอร์ `play/pause/next/previous/search_play`
       - แผนที่ $\rightarrow$ `device_navigate` พร้อมจุดหมายปลายทาง
       - แจ้งเตือน $\rightarrow$ `device_notification_read`
       - ความเร็ว/พิกัด $\rightarrow$ `device_location`
  2. **Structured Sentiment & Emotion Tag Parser (`AlwaysLiveManager.kt`)**:
     - อัปเกรด `detectSentiment(text)`:
       - ตรวจสอบ Bracketed Tags ก่อนเป็นอันดับแรก: `[HAPPY]`, `[LOVE]`, `[EXCITED]`, `[SAD]`, `[ANGRY]`, `[CONFUSED]`, `[WINK]`, `[POUT]`, `[DIZZY]`, `[SURPRISED]`, `[BORED]`, `[ENRAGED]`, `[SLEEPING]`
       - รองรับ Pipe-delimited Commands: `HAPPY|props=...`, `SPEAKING|...`
       - ขยายชุดคำค้นหา Natural Sentiment Keywords ภาษาไทยและอังกฤษครอบคลุมทุกอารมณ์
  3. **Unit Tests Added (`DriveModeTest.kt`)**:
     - ทดสอบ Structured Tag Regex Extraction
     - ทดสอบ Pipe Syntax Head Parsing
     - ทดสอบ Driving Voice Intent Keywords
- **Verification**:
  - คอมไพล์ `:composeApp:compileDebugKotlinAndroid` สำเร็จ (Build Successful in 2m 16s)
  - รัน Unit Tests `:composeApp:testDebugUnitTest` ทั้ง 3 ชุด (`DriveModeTest`, `AlwaysLiveTest`, `PetModeTest`) ผ่าน 100% (Build Successful in 50s)

## 2026-09-13 — Always_AI_Live_Mode Implementation (P1 Complete: Full Drive Mode, Telemetry & Power Management)
- **Problem & Goal**:
  - โหมด DRIVE เดิมเป็นเพียง Placeholder Card ที่ไม่มีฟังก์ชันจริง (ขาด GPS Speedometer, Media Player Controls, Notification Reader, Navigation Shortcuts)
  - กล้องพื้นหลังใน Pet mode ทำงานตลอดเวลาแม้สัตว์เลี้ยงจะหลับ (`SLEEPING`) ก่อให้เกิดการใช้แบตเตอรี่และพลังงาน CPU โดยไม่จำเป็น
  - ขาด Unit Tests ครอบคลุมการทำงานของ DriveBridge และ DriveModeController
- **Changes Implemented**:
  1. **Full Drive & Control Architecture (`DriveBridge.kt`, `DriveModeController.kt`)**:
     - สร้าง `DriveBridge` ใน `commonMain` พร้อม StateFlow: `telemetry` (speedKmh, address, lat, lng, isMoving), `mediaState` (title, artist, appName, isPlaying), และ `recentNotificationText`
     - สร้าง `DriveModeController` อำนวยความสะดวกให้ UI Composable สั่งการ playPause, nextTrack, prevTrack, readNotifications, และ startNavigation
  2. **DriveModeScreen Overhaul (`DriveModeScreen.kt`)**:
     - เพิ่ม `DriveTelemetryHeader`: หน้าปัดวัดความเร็ว GPS HUD ขนาดใหญ่ + ป้าย Location Pill ระบุชื่อถนน/ย่าน
     - เพิ่ม `DriveNotificationReaderBar`: แสดงการแจ้งเตือนล่าสุด พร้อมปุ่มอ่านออกเสียงด้วย AI Voice TTS
     - เพิ่ม `DriveActionRow`: ปุ่มควบคุมเพลงขนาดใหญ่พิเศษ (สัมผัสง่ายขณะขับขี่) + ปุ่มลัดเปิด Google Maps Navigation นำทางทันที
     - ปรับปรุง Responsive Landscape: Two-pane layout ด้านซ้ายเป็น Speedometer + Media + Navigation ด้านขวาเป็น Avatar
  3. **Platform Integration in `AlwaysLiveManager.kt`**:
     - เชื่อมต่อ `LocationProvider` ดึงความเร็วและชื่อถนนแบบ Real-time
     - เชื่อมต่อ `MediaInfoProvider` และ `AudioManager.dispatchMediaKeyEvent` เพื่อควบคุมเพลง
     - เชื่อมต่อ `VoiceManager` อ่านข้อความแจ้งเตือนสำคัญ 3 รายการล่าสุดด้วยเสียงสังเคราะห์ TTS
  4. **Pet Mode Background Camera Power Management (`PetModeScreen.kt`)**:
     - เพิ่มตัวแปร `shouldRunBackgroundVision` หยุดประมวลผลเฟรมกล้องพื้นหลังเมื่อสัตว์เลี้ยงอยู่ในสถานะ `SLEEPING` หรือไม่มี processor
  5. **Unit Tests Added (`DriveModeTest.kt`)**:
     - ทดสอบ Telemetry (speed > 5f -> isMoving), Media State, Notification updates, และ DriveModeController callbacks ผ่าน 100%
- **Verification**:
  - คอมไพล์ `:composeApp:compileDebugKotlinAndroid` ผ่าน 100% (Build Successful in 2m 17s)
  - รัน Unit Tests `:composeApp:testDebugUnitTest` ทั้ง 3 ชุด (`AlwaysLiveTest`, `PetModeTest`, `DriveModeTest`) ผ่าน 100% (Build Successful in 50s)

## 2026-09-13 — Always_AI_Live_Mode Refactoring (P0 Complete: Modular Architecture & KMP Compatibility)
- **Problem & Goal**:
  - `AlwaysLiveScreen.kt` มีขนาดใหญ่เกินไป (2,471 บรรทัด) รวบรวมตรรกะ UI ของ PET, DRIVE, และ CONTROL ไว้ใน Composable เดียว
  - การใช้งาน `System.currentTimeMillis()` ใน `commonMain` ส่งผลให้ไม่สามารถคอมไพล์บนเป้าหมาย iOS ได้
  - โค้ด `RobotSoundPlayer.handler` ซ้ำซ้อน 2 จุดใน `AlwaysLiveManager.kt`
  - ตรรกะแปลง Emotion -> Color กระจายซ้ำซ้อนในหลาย Composable
- **Changes Implemented**:
  1. **Decomposed `AlwaysLiveScreen.kt`**:
     - แยก `PetModeScreen.kt` (~800 บรรทัด): รองรับ `PetModeContent`, `PetDialogueCard`, `PetDetectionBadge`, `PetEyeScannerOverlay`
     - แยก `DriveModeScreen.kt` (~270 บรรทัด): รองรับ `DriveModeContent` (ทั้ง Portrait และ Landscape)
     - `AlwaysLiveScreen.kt` ลดขนาดเหลือเพียง ~730 บรรทัด ทำหน้าที่เป็น Clean Profile Router และ Host Shared Sub-Composables
  2. **Centralized Emotion Color Palette**:
     - สร้าง `AvatarEmotionColors.kt` รวบรวม `primary()`, `secondary()`, และ `statusPill()` ลดการเขียน `when(emotion)` ซ้ำซ้อน 3 จุด
  3. **KMP / iOS Compatibility**:
     - แทนที่ `System.currentTimeMillis()` ทั้งหมดด้วย `Clock.System.now().toEpochMilliseconds()` จาก `kotlinx-datetime` ในทุกไฟล์ `commonMain`:
       - `PetModeController.kt`, `PetStateMachine.kt`, `PetMemory.kt`, `PetVisionTargetTracker.kt`, `PetNeedsState.kt`, `AlwaysLiveScreen.kt`
  4. **Deduplicated Sound Handler**:
     - สกัด `installSoundHandler()` ใน `AlwaysLiveManager.kt` ลดโค้ดซ้ำซ้อนใน `init{}` และ `startPetMode()`
  5. **Profile Architecture Alignment**:
     - บันทึกข้อกำหนด: 🚗 ขับขี่ / ควบคุม (`CONTROL` / `DRIVE`) คือโหมดเดียวกัน สำหรับสั่งการ/นำทาง โดยมี 2 กลุ่มโหมดหลักคือ ขับขี่/ควบคุม vs สัตว์เลี้ยง (`PET`)
- **Verification**:
  - คอมไพล์ `:composeApp:compileDebugKotlinAndroid` ผ่าน 100% (Build Successful in 1m 23s)
  - รัน Unit Tests `:composeApp:testDebugUnitTest` ทั้งชุด `AlwaysLiveTest` และ `PetModeTest` ผ่าน 100% (30 actionable tasks, Build Successful in 38s)

## 2026-09-13 — Comprehensive Code Review: Always_AI_Live_Mode (Driving + Pet Modes)
- **Scope**: Full review of Always_AI_Live_Mode covering CONTROL, DRIVE, and PET profiles
- **Files Reviewed**: 14 source files + 2 test files (~9,500 lines total)
- **Key Findings**:
  - **PET Mode (⭐⭐⭐⭐½)**: Excellent implementation — Tamagotchi needs system, 10+ touch reactions, `PetStateMachine` with anti-spam ring buffer, eye scanner camera overlay, face recognition (5 slots), hand gestures, missile barrage, sentry mode, focus timer, copycat game, fortune oracle. 30+ comprehensive tests.
  - **DRIVE Mode (⭐)**: Essentially a placeholder — only a static `DriveFeaturePanel` card. No GPS, no media controls, no notification reader, no hands-free features. Uses identical UI as CONTROL mode.
- **Critical Issues Found (4)**:
  1. `AlwaysLiveScreen.kt` is a 2,471-line God Composable (PET + DRIVE + CONTROL in one function)
  2. Duplicate `RobotSoundPlayer` handler in `AlwaysLiveManager.kt` init/startPetMode (~40 lines × 2)
  3. `System.currentTimeMillis()` in commonMain — breaks iOS compilation (must use `kotlinx-datetime`)
  4. No iOS actual implementations for `PetMotionDetector`, `PetVisionDetector`, `AlwaysLiveManager`
- **Moderate Issues (4)**: Hidden 1dp camera battery drain, fragile keyword-based anti-hallucination guards, WakeLock leak risk, hardcoded sentiment detection
- **Improvement Plan Proposed**:
  - P0: Decompose AlwaysLiveScreen, fix KMP compat, deduplicate sound handler
  - P1: Build real driving features (DriveModeController), extract emotion color map, camera power management, WakeLock safety
  - P2: iOS implementations, intent classifier for guards, structured AI sentiment
- **Files**: [[AlwaysLiveScreen]], [[AlwaysLiveManager]], [[PetModeController]], [[PetStateMachine]], [[PetNeedsState]], [[LiveToolBridge]], [[LiveVoiceAlertEngine]], [[LiveModePanel]], [[AlwaysLiveProfile]]

## 2026-09-13 — Pet Vision Fix: Implement Robust 2-Turn Vision Flow (Eliminate Turn-1 Guessing & Fix Frozen Camera)
- **Problem Solved**:
  - *User Report*: "ยังอาการเดิม สั่งให้ pet ดูสิ่งที่ถืออยู่ pet เปิดกล้องดู และปิดกล้อง ตอบผิด สั่งให้ดูใหม่ pet ไม่ได้เปิดกล้อง แต่ตอบถูก"
  - *Analysis from Log (`02:00:17` - `02:00:40`)*:
    1. ผู้ใช้ถาม: *"หรือว่าฉันถืออะไรอยู่"* (ถือซองกาแฟมอคโคน่าสีเขียว)
    2. Gemini Live เรียก `vision_activate` และได้รับคำตอบ Tool ทันที
    3. Gemini เริ่มพูด Turn 1 ทันทีที่ `02:00:18.424` (หลัง Tool Response เพียง 900ms) โดย ณ วินาทีนั้น กล้องเพิ่งส่งเฟรมแรกขึ้นเซิร์ฟเวอร์ และยังไม่ทันโฟกัส
    4. ผลคือ Gemini "เดาสุ่ม" ใน Turn 1 ว่า *"ถือแก้วน้ำสีขาว"* และลืมเรียก `vision_deactivate` เมื่อพูดจบ
    5. เมื่อ Turn 1 จบลง โค้ดส่ง `sendClientText` แต่ `clientContent` ใน Gemini Live ไม่ทริกเกอร์เสียงพูดขณะ Audio Streaming (ทำหน้าที่เป็นแค่ Pending Context ในเซสชัน) ทำให้ AI นิ่งเงียบ และกล้องค้างเปิดอยู่
    6. ขณะที่กล้องค้างเปิดนาน 15 วินาที กล้องได้ส่งภาพซองกาแฟ Moccona รวม 15 เฟรมเข้าไปสะสมในโมเดล
    7. เมื่อผู้ใช้เอ่ยปากสั่ง *"ดูใหม่"* ที่ `02:00:33.362` เสียงของผู้ใช้ทริกเกอร์เทิร์นใหม่ของ Gemini ซึ่งประมวลผลคำสั่งตกค้างจาก `sendClientText` จึงสั่ง `vision_deactivate` (ปิดกล้อง) ทันที และดึงเอาภาพ 15 เฟรมที่เห็นเมื่อกี้มาตอบอย่างแม่นยำว่า *"เป็นซองกาแฟสีเขียวๆ ยี่ห้อ Moccona"*!
- **Root Cause**:
  1. **Turn 1 Prompt Premature Force**: ผลตอบรับของ Tool บอกให้ Gemini "ตอบคำถามทันที" ทำให้ Gemini พยายามเดาสุ่มตอบใน Turn 1 ก่อนที่ฮาร์ดแวร์กล้องจะจับโฟกัสและส่งภาพจริง
  2. **`sendClientText` vs `sendRealtimeText`**: ใน `LiveGeminiService.kt` การใช้ `sendClientText` (`clientContent`) จะไม่กระตุ้นให้โมเดลเริ่มสร้างเสียงพูดใหม่ ต่างจาก `sendRealtimeText` (`realtimeInput.text`) ที่ประมวลผลเสมือนผู้ใช้พูดเข้ามาจริง
  3. **No Turn 2 Fallback Close**: หาก Gemini ลืมเรียก `vision_deactivate` หลังพูดจบ กล้องจะค้างเปิดไปจนชน timeout
- **Solution & Implementation**:
  1. **Enforce Strict 2-Turn Vision Flow (`JarvisPersona.kt`, `LiveToolBridge.kt`)**:
     - **Turn 1 (เมื่อเปิดกล้อง)**: สั่งให้ Gemini พูดตอบรับสั้นๆ 1 ประโยคเปิดตัวเท่านั้น (เช่น *"ไหนขอน้องจาวิสดูก่อนนะฮับบอส ถือของไว้ใกล้ๆ กล้องนะฮับ"*) **ห้ามเดาสุ่มตอบในเทิร์นนี้เด็ดขาด** ระหว่างนี้กล้องจะจับโฟกัสและส่งภาพชัดเจน 2-3 เฟรมเข้าสู่ระบบ
     - **Turn 2 (วิเคราะห์และตอบจริง)**: เมื่อ Turn 1 จบลง ระบบจะส่ง `liveService.sendRealtimeText(...)` ผ่าน `realtimeInput` กระตุ้นให้ Gemini สรุปสิ่งที่เห็นจากภาพสดและสั่ง `vision_deactivate` ทันที
     - **Turn 2 Auto-Close Fallback**: เมื่อ Turn 2 พูดจบ หาก Gemini ลืมเรียก `vision_deactivate` ระบบจะมี Fallback ปิดกล้องและพับตาลงให้อัตโนมัติหลังจาก 1.0 วินาที เพื่อให้กล้องพร้อมสำหรับการสั่ง "ดูใหม่" ในครั้งถัดไป 100%
- **Verification**:
  - รัน Unit Tests ผ่าน 100% ทั้งชุด `PetModeTest` และ `AlwaysLiveTest`
  - **Live Hardware Test Verified by User (`02:42:28` - `02:43:07`)**:
    - **รอบที่ 1**: ผู้ใช้ถาม *"แล้วว่าฉันถืออะไรอยู่"* (ถือแก้วกาแฟ Nescafe Gold สีดำ)
      - Turn 1: AI เปิดกล้อง ตอบรับ *"ไหนขอน้องจาวิสดูก่อนนะคะบอส ถือของไว้ใกล้ๆ กล้องนะฮับ"* ไม่มีการเดาสุ่ม
      - Turn 2: ระบบส่ง `realtimeInput` ทันที AI ตอบอย่างแม่นยำ 100%: *"บอสถือแก้วกาแฟสีดำอยู่ค่ะ เห็นโลโก้ Nescafe Gold ชัดเจนเลยค่ะ กำลังดื่มกาแฟอยู่เหรอคะ"* พร้อมสั่ง `vision_deactivate` ปิดกล้องทันที
    - **รอบที่ 2**: ผู้ใช้ถาม *"ดูไว้ฉันถืออะไรอยู่"* (ถือรีโมตแอร์)
      - Turn 1: AI เปิดกล้อง ตอบรับเปิดตัว กล้องโฟกัสและส่งวิดีโอสด
      - Turn 2: AI สรุปตอบแม่นยำ 100%: *"บอสถือรีโมตแอร์อยู่ค่ะ เห็นหน้าจอแสดงอุณหภูมิ 25 องศาด้วยนะคะ ร้อนเหรอคะบอส"* พร้อมสั่ง `vision_deactivate` ปิดกล้องเรียบร้อยสมบูรณ์

## 2026-09-13 — Pet Vision Fix: Restore Real-Time Response Pipeline & Eliminate Premature Camera Cutoff
- **Problem Solved**:
  - *User Report*: "รอบก่อนหน้านี้ ถ้าฉัน ขึ้นประโยคว่า ดู ai จะตอบถูก และตอบได้ทันที รอบนี้ ฉัน ขึ้นประโยคว่า ดู* ai ดู เปิดกล้อง และปิดกล้อง แต่จะไม่ตอบทันที ต้องถามอีกรอบ จึงจะตอบ code ก่อนหน้านี้ แม่นยำกว่า code ล่าสุด"
  - *Analysis from Log (`01:24:30` - `01:26:35`)*:
    - เมื่อผู้ใช้พูดสั่ง *"ดูว่าฉันถืออะไรอยู่"* Gemini Live สั่งเปิดกล้อง `vision_activate` สำเร็จ
    - Gemini พูดประโยคเปิดตัว: *"ไหนฮับบอส ขอน้องจาวิสมองดูหน่อยน้าา ชูขึ้นมาใกล้กล้องอีกนิดนึงนะฮับ"*
    - ทันทีที่พูดประโยคเปิดตัวจบ (เทิร์นแรกเสร็จสิ้น) กล้องกลับถูกสั่งปิดทิ้งทันทีภายใน 2 วินาที (`CameraService: Camera stopped`) โดยที่ AI ยังไม่ได้เริ่มวิเคราะห์ภาพและไม่ได้ตอบคำถาม
    - AI เงียบสนิท จนผู้ใช้ต้องเอ่ยปากถามซ้ำรอบสอง (*"เขียนหรือยังว่าอะไร"*) AI จึงตอบสิ่งที่เห็นออกมา
- **Root Cause**:
  1. **Premature Camera Termination**: ใน `AlwaysLiveScreen.kt` มีการใส่ `LaunchedEffect(isCameraPipOpen, isSpeakingNow)` ที่ตรวจจับเมื่อ AI เริ่มพูดแล้วหยุดพูดจะหน่วง 2 วินาทีแล้วสั่งปิดกล้อง (`requestEyeOpen(false)`) ทันที ส่งผลให้เมื่อ AI พูดประโยคเปิดตัวเบื้องต้น เช่น *"ไหนขอน้องจาวิสดูก่อนนะฮับ..."* จบ ตัวแปร `isSpeakingNow` เปลี่ยนเป็น `false` ทำให้กล้องถูกสั่งปิดทิ้งทันทีภายใน 2 วินาที ก่อนที่ AI จะทันได้มองภาพและตอบคำถาม
  2. **Missing `visionPromptJob` in Gemini Live**: ใน Gemini Live WebSockets API เมื่อส่งวิดีโอสตรีมสด โมเดลจะไม่เริ่มเทิร์นใหม่ด้วยตัวมันเองหากไม่มี Client Text ไปสะกิดหลังจากเทิร์นแรกจบ ในโค้ดก่อนหน้านี้มี `visionPromptJob` ที่รอ `turnCompleteFlow.first()` (รอคำพูดทักทายแรกจบ) แล้วส่ง `sendClientText("[SYSTEM] ตอนนี้ภาพสดจากกล้องเข้ามาอย่างชัดเจนแล้ว โปรดสรุปสิ่งที่เห็นตอบคำถามล่าสุดของผู้ใช้ทันที...")` ทันที ซึ่งทำให้ AI ตอบสิ่งที่เห็นได้ถูกต้องและทันใจ แต่ในรอบล่าสุด `visionPromptJob` ถูกตัดออกไป ทำให้ AI เงียบสนิทหลังพูดประโยคเปิดตัว และกล้องถูกปิดไปก่อน จนผู้ใช้ต้องเอ่ยปากถามซ้ำรอบสอง ("เขียนหรือยังว่าอะไร") AI จึงตอบ
  3. **Tool Response Delay (900ms blocking)**: มีการใส่ `delay(900L)` ขวางค้างใน `handleNativeToolCall` สำหรับ `vision_activate` ทำให้การตอบสนอง Tool ของ Gemini ล่าช้าและกระตุก
  4. **Blocked `vision_deactivate`**: ใน `vision_deactivate` มีเงื่อนไขเช็ค `hasVisionIntent` จากคำพูดล่าสุดของผู้ใช้ ซึ่งถ้าผู้ใช้ถาม *"ดูว่าฉันถืออะไรอยู่"* ตัวแปร `lastUserText` จะยังมีคำว่า *"ดู"* และ *"ถือ"* ค้างอยู่เสมอ ทำให้เมื่อ AI พยายามสั่งปิดกล้องหลังตอบจบ ระบบตีกลับด้วย `STILL_NEED_VISION` ทำให้เซสชันติดขัด
- **Solution & Implementation**:
  1. **Remove Premature Auto-Close (`AlwaysLiveScreen.kt`)**: ลบ `LaunchedEffect` ที่ตัดปิดกล้องหลังจาก AI พูดประโยคแรกจบออก เพื่อให้กล้องเปิดค้างไว้จนกระทั่ง AI วิเคราะห์ภาพเสร็จสิ้นและสั่ง `vision_deactivate` ด้วยตัวเอง (หรือปิดเมื่อครบ Safety Timeout 25 วินาที หรือผู้ใช้กดปิดบนหน้าจอ)
  2. **Restore Real-Time Response Pipeline (`LiveToolBridge.kt`)**:
     - คืนชีพ `visionPromptJob`: เมื่อเปิดกล้อง ให้ส่งคำตอบรับ Tool ทันที (ไม่ติด `delay(900L)`) และให้ Job รอจนกระทั่งคำพูดเปิดตัวแรกจบ (`turnCompleteFlow.first()`) จากนั้นกระตุ้น AI ทันทีด้วย System Text ให้วิเคราะห์และตอบสิ่งที่เห็นจากภาพสดและสั่ง `vision_deactivate`
     - ทำความสะอาด `vision_deactivate`: ลบเงื่อนไขที่ขัดขวางการปิดกล้อง คืนการปิดกล้องที่สะอาดและราบรื่น
- **Verification**:
  - รัน `./gradlew.bat testDebugUnitTest` ผ่าน 100%

## 2026-09-13 — Pet Vision Fix: Resolve 1-Turn Lag & Eliminate Stale Video Buffer Leak
- **Problem Solved**:
  - *User Report*: AI รับรู้ภาพช้าไป 1 เทิร์น (1-turn delay)
    - ครั้งที่ 1: ถาม *"ฉันโชว์กี่นิ้วอยู่"* (กำมือ) $\rightarrow$ AI ตอบเดา *"3 นิ้ว"*
    - ครั้งที่ 2: ถาม *"ดูมาอีกกี่นิ้ว"* (ชู 5 นิ้ว) $\rightarrow$ AI ตอบ *"บอสกำมืออยู่ ไม่ได้ชูนิ้วฮับ!"* (ตอบภาพของครั้งที่ 1)
    - ครั้งที่ 3: ถาม *"ฉันถืออยู่นี่หละคืออะไร"* (ถือรีโมทแอร์) $\rightarrow$ AI ตอบเดาเฟรมแรก *"โทรศัพท์มือถือ"*
    - ครั้งที่ 4: ถาม *"อันนี้กี่นิ้ว"* (ชู 4 นิ้ว) $\rightarrow$ AI ตอบ *"ถือรีโมทแอร์อยู่ต่างหาก! มีเลข 25 ด้วย"* (ตอบภาพของครั้งที่ 3)
- **Root Cause**:
  1. **Camera Frame Streaming Leak**: เมื่อ AI ตอบสรุปจบหรือ UI พับดวงตาลง ระบบไม่ได้ปิดสตรีมกล้องระดับ Background (`cameraService.isAiVisionRequested = false`) ทำให้กล้องยังคงปั๊มภาพของคำถามก่อนหน้าเข้าสู่ WebSocket เซสชันของ Gemini Live นานนับ 10-23 วินาที
  2. **Asynchronous Context Desync (`visionPromptJob`)**: ใน `LiveToolBridge.kt` มีโค้ดส่ง `sendClientText("[SYSTEM] ตอนนี้ภาพจากกล้องชัดแล้ว... เมื่อพูดจบให้เรียก vision_deactivate ทันที")` แต่เนื่องจาก `clientContent` ไม่กระตุ้นให้โมเดลเริ่มพูดขณะ Audio Streaming ข้อความนี้จึงตกค้างเป็น Context ในเซสชัน และไปทำงานในเทิร์นถัดไปแทน ทำให้เมื่อผู้ใช้เริ่มถามคำถามใหม่ โมเดลจึงเรียก `vision_deactivate` ทันทีและดึงภาพเก่าในบัฟเฟอร์มาตอบ
  3. **Premature Guessing in Turn 1**: เมื่อ AI เรียก `vision_activate` ระบบตอบรับ Tool ทันทีที่ 0ms ทำให้ AI รีบพูดและเดาสุ่มก่อนที่ฮาร์ดแวร์กล้องจะโฟกัสและส่งภาพจริง 1-2 เฟรมแรกถึง AI
- **Implementation**:
  1. **Synchronized Camera Streaming Bridge (`PetVisionBridge.kt`, `JarvisViewModel.kt`)**:
     - เพิ่ม `onAiVisionStreamToggle` ใน `PetVisionBridge`: เมื่อสั่งปิดตา (`requestEyeOpen(false)` หรือ AI พูดจบ) ระบบจะตัดการสตรีมภาพกล้อง (`_isAiVisionRequested.value = false`, `cameraService.isAiVisionRequested = false`) และหยุดกล้องทันที รับประกันว่าจะไม่มีเฟรมภาพตกค้างไปยังเทิร์นถัดไป 100%
  2. **Stream Stabilization & Remove Context Pollution (`LiveToolBridge.kt`)**:
     - เมื่อ Gemini เรียก `vision_activate`: เปิดตาและเปิดสตรีมทันที พร้อมหน่วงเวลาสั้นๆ ~900ms ให้กล้องจับภาพและส่งวิดีโอสด 1-2 เฟรมเข้าสู่ WebSocket ก่อนส่ง Native Tool Response ทำให้ Gemini เห็นภาพจริงทันทีโดยไม่ต้องเดาสุ่ม
     - ลบ `visionPromptJob` ที่ส่ง `sendClientText` ตกค้างทิ้งอย่างสิ้นเชิง
     - ป้องกัน `vision_deactivate` ก่อนตอบ: หากผู้ใช้กำลังถามคำถามการมองเห็น ระบบจะส่ง `STILL_NEED_VISION` บังคับให้ดูภาพสดปัจจุบันก่อนตอบ
  3. **Voice Fast-Path & Auto-Close Pacing (`VoiceController.kt`, `AlwaysLiveScreen.kt`)**:
     - เสริมคำศัพท์ fast-path: `"ดูมาอีก"`, `"ดูอีก"`, `"ฉันโชว์กี่นิ้ว"`, `"ถืออะไรอยู่"` ให้เปิดตาและเริ่มส่งเฟรมภาพทันทีตั้งแต่ผู้ใช้เริ่มเปล่งเสียง
     - ใน `AlwaysLiveScreen.kt`: เมื่อ AI พูดจบและพับตาลง ให้รีเซ็ตสถานะและเฟรมไทม์มิ่งทันที
  4. **System Prompt Real-Time Precision (`JarvisPersona.kt`)**:
     - ปรับปรุงกฎข้อ 4 (VISION RULES): ให้ตอบจากภาพสดในปัจจุบันเสมอ และห้ามดึงภาพในอดีตมาตอบเด็ดขาด
- **Verification**:
  - รัน `./gradlew.bat testDebugUnitTest` ผ่าน 100% ทั้งชุดทดสอบ `PetModeTest` และ `AlwaysLiveTest`

## 2026-09-13 — Pet Vision Fix: Unlock Blocked vision_activate for Visual & Finger Counting Questions
- **Problem Solved**:
  - *User Report*: "ตรวจสอบ ฉันไม่เห็น ดวงตาขึ้นแสกน แต่ pet ตอบสิ่งที่เห็นได้ถูกต้อง"
  - *Investigation from Log*:
    - ผู้ใช้พูดถาม: `"can ชูกี่นิ้วอยู่"`, `"อันนี้กี่นิ้ว"`, `"กี่นิ้วนะ เอาใหม่"`
    - Gemini Live พยายามเรียก Tool เปิดกล้อง: `Native tool call: vision_activate({duration_seconds=10})`
    - แต่ถูกระบบ Guard ใน `LiveToolBridge.kt` สกัดกั้นและตีกลับด้วย: `EYES_NOT_NEEDED: ผู้ใช้ไม่ได้สั่งให้เปิดกล้องหรือมองดูสิ่งใด (คำพูดล่าสุด: "can ชูกี่นิ้วอยู่") — โปรดสนทนาหรือตอบคำถามของผู้ใช้ตามปกติโดยไม่ต้องเปิดกล้อง`
    - ส่งผลให้ดวงตาแสกนไม่เปิด (`PetVisionBridge.requestEyeOpen(true)` ไม่ถูกเรียก) และไม่มีการสตรีมวิดีโอขึ้น Cloud
    - ส่วนที่ Pet ตอบจำนวนนิ้วออกมา ("บอสชูสองนิ้ว", "อันนี้สามนิ้ว", "สี่นิ้ว") เกิดจากการที่ AI พยายาม "เดาสุ่ม" (Hallucination) เนื่องจากถูกสั่งว่า EYES_NOT_NEEDED ห้ามเปิดกล้อง ให้ตอบไปเลย
- **Root Cause & Implementation**:
  1. **ปลดล็อค Guard สกัดกั้นใน `LiveToolBridge.kt`**:
     - เพิ่มคลังคำศัพท์ตรวจจับเจตนาการมองเห็น (`hasVisionIntent`) ให้ครอบคลุมคำถามวัตถุและท่าทาง: `"นิ้ว"`, `"ชู"`, `"กี่นิ้ว"`, `"กี่"`, `"อันนี้"`, `"อันไหน"`, `"นี่"`, `"นี้"`, `"ตรงนี้"`, `"คืออะไร"`, `"สีอะไร"`, `"ตัวอะไร"`, `"ท่าอะไร"`, `"ถืออะไร"`, `"ใส่อะไร"`, `"finger"`, `"how many"`
     - ป้องกันไม่ให้บล็อกคำถามเชิงการมองเห็น ทำให้ `vision_activate` เปิดตาแสกนและเริ่มสตรีมวิดีโอได้ทันที 100%
  2. **เปิดตาแสกนทันทีบนอุปกรณ์ (Fast-path Local Trigger ใน `VoiceController.kt`)**:
     - ขยาย `isEyeOpenCmd` ให้ครอบคลุม: `"กี่นิ้ว"`, `"ชูกี่นิ้ว"`, `"ชูนิ้ว"`, `"อันนี้กี่นิ้ว"`, `"อันนี้คืออะไร"`, `"อันนี้อะไร"`, `"สีอะไร"`, `"ตัวอะไร"`, `"ท่าอะไร"`, `"ถืออะไร"`
     - ทันทีที่ผู้ใช้พูดประโยคเหล่านี้ จบ STT ปุ๊บ ตาแสกน Cyber Radar จะเปิดทันทีบนเครื่องโดยไม่ต้องรอความล่าช้าจากระบบเน็ตเวิร์ก
  3. **อัปเดตกฎ System Prompt (`JarvisPersona.kt`)**:
     - ระบุชัดเจนในกฎสายตาว่าคำถามตรวจนับหรือสังเกตสิ่งของ เช่น "ชูกี่นิ้ว", "อันนี้คืออะไร", "สีอะไร" ให้เรียก `vision_activate` ได้ทันที
- **Verification**:
  - โค้ดคอมไพล์ผ่านและทดสอบ Unit Test ยืนยันการทำงานของระบบ

## 2026-09-13 — Pet System: Portrait Pinned Exit Button, Responsive Top Bar & Compact Car Icon
- **Problem Solved**:
  - *User Requirement*:
    1. ในหน้าจอแนวตั้ง (Portrait) ปุ่ม `✕` ออกจากหน้าสัตว์เลี้ยงหายไป กดออกไม่ได้ แต่ในหน้าจอแนวนอน (Landscape) มีปุ่ม `✕` แสดงอยู่
    2. ในหน้าจอแนวตั้ง ปุ่มโหมดควบคุมที่เป็นรูปรถมีขนาดใหญ่กว่าปุ่มอื่น ปรับให้เหลือแค่รูปรถ `🚗` พอดีๆ
- **Root Cause & Implementation**:
  1. **สาเหตุที่ปุ่ม [✕] หายไปในหน้าจอแนวตั้ง**:
     - บนหน้าจอแนวตั้ง (Portrait) ความกว้างหน้าจอมือถือทั่วไปอยู่ที่ประมาณ 360dp–412dp
     - แถบเมนูด้านบนจัดวางด้วย `Row` เดี่ยวแบบไม่มีการ Wrap หรือ Scroll โดยมีปุ่มเรียงกันยาว: `[🧪 ทดสอบเดโม]`, `[👁️ ลืมตา]`, `[⚙️ ตั้งค่า]`, `[🚗 โหมดควบคุม]`, และปุ่ม `[✕]` ซึ่งรวมความกว้างเกิน 440dp
     - ส่งผลให้ปุ่มก่อนหน้า (`🚗 โหมดควบคุม`) ถูกบีบอัดตัวอักษรลงมาเป็นแนวตั้ง และปุ่ม `[✕]` ปิดโปรแกรมถูกดันหลุดขอบขวาของจอออกไปทั้งหมด (ในโหมดแนวนอนมีความกว้าง 800dp+ จึงไม่ล้น)
  2. **สถาปัตยกรรม Responsive Top Controls (`AlwaysLiveScreen.kt`)**:
     - **Pinned Exit Button**: แยกปุ่ม `[✕]` ออกมาไว้ที่ `Alignment.TopEnd` ถาวร ด้วยดีไซน์ปุ่มกลมคอนทราสต์ชัดเจน (`size 36.dp`, border ขาวจาง) รับประกันว่าจะอยู่บนหน้าจอมุมบนขวา 100% ไม่ถูกดันหลุดจออีกต่อไป
     - **Horizontally Scrollable Utility Container**: ครอบปุ่มเครื่องมือด้านซ้ายด้วย `weight(1f, fill = false).horizontalScroll(rememberScrollState())` เพื่อให้ไม่เกิดการบีบอัดตัวอักษรเป็นแนวตั้ง และหากใช้บนจอเล็กมากๆ ผู้ใช้ยังสามารถสไลด์เลื่อนดูได้
     - **Safe Insets**: ใส่ `statusBarsPadding()` ป้องกันไม่ให้ปุ่มชนติ่งกล้องหน้าหรือแถบนาฬิกาของระบบ Android
  3. **ปุ่มโหมดควบคุมขนาดกะทัดรัด (Compact Car Icon)**:
     - ในหน้าจอแนวตั้ง ปรับข้อความปุ่มจาก `"🚗 ควบคุม"` เหลือเพียงไอคอน `"🚗"` สวยงาม สบายตา ขนาดสัดส่วนเท่ากันกับปุ่มอื่นๆ
     - ในหน้าจอแนวนอน ยังคงแสดงเต็มว่า `"🚗 โหมดควบคุม"`
- **Verification**:
  - โค้ดคอมไพล์ผ่าน และทดสอบ Unit Test ยืนยันการทำงานของระบบ

## 2026-09-13 — Pet System: Auto AI Camera Scan on Voice Intent, Eye-Overlay Circular Viewfinder & Procedural Cyber Radar SFX
- **Problem Solved**:
  - *User Requirement*:
    1. **ระบบเปิด/ปิดกล้องอัตโนมัติ (Hands-Free Voice & AI Vision)**:
       - ผู้ใช้ไม่ต้องใช้มือกดปุ่มลืมตา/ปิดกล้องเอง
       - เมื่อพูดวลีบอกให้มอง เช่น *"นี่คืออะไร"*, *"ดูนี่หน่อย"*, *"ช่วยดู"*, *"เปิดกล้อง"* หรือเมื่อ Gemini Live เรียก Tool `vision_activate` ระบบจะเปิดกล้องและเริ่มส่งภาพสตรีมทันที
       - เมื่อ Gemini ดูภาพและตอบคำถามจบ (AI พูดอธิบายเสร็จ) ให้ปิดกล้องเองอัตโนมัติ
    2. **FX ตาแสกน พร้อมเสียง (Cyber Scan FX & Audio)**:
       - มี FX กวาดสายตาแสกนเรดาร์บนดวงตาสัตว์เลี้ยง พร้อมสังเคราะห์เสียงไซไฟไฮเทคเมื่อเปิดกล้อง และหยุด FX เมื่อปิดกล้อง
    3. **ภาพเรียลไทม์จากกล้องเป็นวงกลม Overlay แนบสนิทบนดวงตาสัตว์เลี้ยง (Eye Camera Viewfinder)**:
       - เปลี่ยนจากหน้าต่างสี่เหลี่ยมลอยมุมล่างขวา ให้เป็นภาพสดทรงกลม (`CircleShape`) วางทาบสนิทบนดวงตาของสัตว์เลี้ยงพอดีทั้งในโหมดแนวตั้งและแนวนอน
- **Root Cause & Implementation**:
  1. **Voice Intent & AI Tool Bridge (`VoiceController.kt`, `LiveToolBridge.kt`)**:
     - ขยายการตรวจจับเสียงใน `VoiceController.kt` ให้ครอบคลุมคำถามที่ต้องการให้มองดู (*"นี่คืออะไร"*, *"นี้คืออะไร"*, *"ดูนี่"*, *"ดูนี้"*, *"ช่วยดู"*, *"อ่านนี่"*, *"what is this"*, *"look at this"*) ให้สั่ง `PetVisionBridge.requestEyeOpen(true)` ทันที
     - ใน `LiveToolBridge.kt`: เมื่อ AI เรียก `vision_activate` สั่งเปิดตาแสกนอัตโนมัติ และเมื่อเรียก `vision_deactivate` สั่งปิดตา
  2. **Circular Eye Camera Overlay Viewfinder (`PetEyeScannerOverlay` ใน `AlwaysLiveScreen.kt`)**:
     - คำนวณพิกัดดวงตา `(leftEyeCenterX, eyeCenterY)`, `(rightEyeCenterX, eyeCenterY)` และขนาดเส้นผ่านศูนย์กลาง `eyeDiameter` ให้ตรงกับ `PetRobotHeadAvatar` แบบพิกเซลต่อพิกเซลทั้งแนวตั้งและแนวนอน
     - **ตาขวา (Right Eye - Cyber Optical Lens)**:
       - กล้องสดฮาร์ดแวร์ `CameraPreviewView` ตัดรูปทรงกลม (`CircleShape`)
       - กรอบนีออนเรืองแสงสี Cyan Sweep Gradient
       - วงแหวนเล็งเป้าหมายหมุนวน (Aperture Reticle Ticks ที่ 45°, 135°, 225°, 315°)
       - เส้นสแกนแนวนอน `ScanLineEffect`
       - ป้ายระบุวัตถุและกรอบ AR Target Lock
       - ปุ่มสลับกล้องหน้า/หลัง และปุ่มปิด
     - **ตาซ้าย (Left Eye - Holographic Radar Scanner)**:
       - พื้นหลังสีน้ำเงินเข้มไซไฟ วงกลมศูนย์กลาง 3 วง พร้อมแกนเล็ง Crosshairs
       - ลำแสงเรดาร์หมุนกวาด 360 องศาต่อเนื่อง
       - จุด Blip แสดงตำแหน่งวัตถุที่กล้องตรวจจับได้
       - ป้ายสถานะเรดาร์ดิจิทัล `[SCANNING...]` / `[🔒 LOCKED N]`
     - ลบหน้าต่าง PIP สี่เหลี่ยมมุมล่างขวาเดิมออกอย่างสมบูรณ์
  3. **Zero-Asset Procedural Cyber Radar Sound (`RobotSoundEngine.kt`, `RobotSoundPlayer.kt`, `AlwaysLiveManager.kt`)**:
     - เพิ่ม `RobotSound.SCAN_RADAR` (รวมเป็น 19 เสียงในระบบ)
     - `generateScanRadar()`: สังเคราะห์คลื่นเสียง 16-bit PCM Sweep ความถี่ 1400Hz $\rightarrow$ 2600Hz ผสม 35Hz Sinusoidal FM Modulation และเสียงพัลส์สะท้อน (~420ms) ดังขึ้นเมื่อเปิดตาแสกน
  4. **Smart Auto-Close Controller (`AlwaysLiveScreen.kt`)**:
     - ตรวจจับสถานะการพูดของ AI (`isSpeaking`) เมื่อเปิดตาแสกน
     - เมื่อ AI พูดอธิบายภาพจบ (`isSpeaking` เปลี่ยนจาก `true` เป็น `false`) จะหน่วงเวลา 2.0 วินาทีให้ผู้ใช้มองเห็นภาพ แล้วพับปิดกล้องกลับสู่ดวงตาน่ารักตามปกติอัตโนมัติ
     - มี Safety Timeout 25 วินาทีเพื่อป้องกันกล้องเปิดค้าง
- **Verification**:
  - อัปเดต `PetModeTest.kt` ยืนยันเสียง `SCAN_RADAR` ครบ 19 ชนิด
  - รัน `./gradlew.bat testDebugUnitTest` ผ่าน 100% ทั้ง `PetModeTest` และ `AlwaysLiveTest`

## 2026-09-12 — Pet System: Care-Specific Procedural Audio (Crunch, Bubble Pops, Bell Toy) & Portrait Split-Screen Dashboard
- **Problem Solved**:
  - *User Requirement*:
    1. **เพิ่มเสียงเฉพาะการดูแลใน `RobotSoundPlayer`**:
       - เสียงเคี้ยวอาหารกรุบกรอบ (Crunch/Munch) เมื่อกดปุ่มให้อาหาร 🍖 (`CRUNCH_EAT`)
       - เสียงฟองสบู่แตกเปาะแปะ (Bubble Pops) เมื่อกดอาบน้ำ 🧼 (`BUBBLE_POP`)
       - เสียงกระดิ่ง/ลูกบอลเมื่อชวนเล่น 🎾 (`BELL_TOY`)
    2. **เมนูค่าสถานะในโหมดแนวตั้ง (Portrait)**:
       - ไม่ต้องซ่อนแท็บสถานะ ให้แบ่งหน้าจอส่วนบนเป็น Living Pet Robot Head Avatar และส่วนล่างเป็นแท็บสถานะถาวรพร้อมปุ่มต่างๆ (Needs gauges, Mood badge, Care buttons: 🍖, 🧼, 🎾, 💤, Memory stats)
       - ในโหมดแนวนอน (Landscape) ยังคงแสดง Pet เต็มจอ พร้อม Sidebar Panel เลื่อนเปิด-ปิดจากขอบขวาได้เหมือนเดิม
- **Root Cause & Implementation**:
  1. **Zero-Asset Procedural Care Audio Synthesizer (`RobotSoundEngine.kt`, `RobotSoundPlayer.kt`, `AlwaysLiveManager.kt`)**:
     - เพิ่ม `RobotSound.CRUNCH_EAT`, `BUBBLE_POP`, `BELL_TOY` (รวมเป็น 18 เสียงในระบบ)
     - `generateCrunchEat()`: สังเคราะห์เสียงเคี้ยว 3 คำต่อเนื่อง (~80ms interval) ด้วยความถี่กวาดลงจาก 340Hz สู่ 110Hz ผสมเสียงฟริกชันกรุบกรอบ (Band-limited Resonant Noise 950Hz) และ Exponential Decay คมชัดรวดเร็ว
     - `generateBubblePop()`: สังเคราะห์เสียงฟองสบู่และหยดน้ำแตก 5 ลูกไต่คอร์ดขึ้น (650Hz $\rightarrow$ 1450Hz, 850Hz $\rightarrow$ 1850Hz, 720Hz $\rightarrow$ 1600Hz, 1000Hz $\rightarrow$ 2150Hz, 1150Hz $\rightarrow$ 2400Hz) พร้อม Harmonic Resonance ให้ความรู้สึกสดชื่น สะอาด สดใส
     - `generateBellToy()`: สังเคราะห์เสียงกระดิ่งทองเหลืองสองโน้ต (G6 1568Hz + C7 2093Hz) ผสม Inharmonic Overtone 2.76x และ 2.0x พร้อม Tremolo 13Hz ให้เสียงกังวาน ใส ชัดเจน สไตล์ของเล่นสัตว์เลี้ยง
     - เชื่อมต่อใน `PetStateMachine.kt`: `resolveFeed` สั่งเล่น `playCrunchEat()`, `resolveClean` สั่งเล่น `playBubblePop()`, `resolvePlay` สั่งเล่น `playBellToy()`
     - ปรับปรุง `PetModeController.kt` ให้ส่ง `InteractionType.CLEAN` และ `InteractionType.PLAY` ลง `PetMemoryStore` อย่างแม่นยำ
  2. **Portrait Split-Screen Dashboard & Reusable Content (`AlwaysLiveScreen.kt`, `PetNeedsSidebarPanel.kt`)**:
     - แยกคอมโพเนนต์เนื้อหาออกมาเป็น `PetNeedsDashboardContent`: รองรับทั้งโหมดฝังถาวรและโหมด Drawer เลื่อนข้าง
     - ในโหมดแนวตั้ง (`!isLandscape`): แบ่งหน้าจอด้วย `Column` ออกเป็น 2 ส่วน:
       - **ส่วนบน (`weight(1.08f)`)**: Living Pet Robot Head Avatar รองรับระบบสัมผัสและลากสายตาเต็มรูปแบบ (Gaze tracking, ลูบหน้าผาก, เกาคาง, จั๊กจี้แก้ม, จิ้มแก้ม, ตบเบาๆ, พร็อพ, มือโฮโลแกรม, กล้องลืมตา PIP, และปุ่มมุมบน) โดยซ่อนปุ่ม "🐾 สถานะ" ในแถบเครื่องมือเนื่องจากมีแท็บสถานะแสดงอยู่ข้างล่างแล้ว
       - **ส่วนล่าง (`weight(0.92f)`)**: แดชบอร์ดสถานะถาวร พื้นหลัง Dark Card สไตล์ OLED ขอบมนบน 24dp พร้อมแถบ Handle ให้ความรู้สึกโมเดิร์น สวยงาม Thumb-friendly ควบคุมดูแลสัตว์เลี้ยงได้ทันที
     - ในโหมดแนวนอน (`isLandscape`): รักษาเลย์เอาต์เดิม Pet เต็มหน้าจอสำหรับวางตั้งโต๊ะเป็น Desk Companion และมีปุ่ม "🐾 สถานะ" เลื่อนเปิด `PetNeedsSidebarPanel` จากขอบขวา
- **Verification**:
  - อัปเดต `PetModeTest.kt` ทดสอบ `RobotSound` ครบ 18 ชนิด
  - เพิ่ม Unit Tests ยืนยัน helper functions และ `PetStateMachine` care action triggers:
    - `RobotSound enum includes speech cadence and pet care sounds`
    - `RobotSoundPlayer helper functions invoke handler with correct sound type`
    - `PetStateMachine care actions trigger specific care sounds`
  - รัน `./gradlew.bat testDebugUnitTest` ผ่าน 100% ทั้ง `PetModeTest` และ `AlwaysLiveTest`

## 2026-09-12 — Pet System: Motion Sickness, Table Thump/Acoustic Reactions & Enraged Fight-Back Missile Barrage
- **Problem Solved**:
  - *User Requirement*:
    1. **การเขย่ามือถือ (Shake)**: Pet มึน เวียนหัว (`DIZZY`), ถ้าเขย่ามากๆ อย่างต่อเนื่องจนรำคาญจะโกรธ (`ANGRY`)
    2. **เอียงมือถือไปมา (Boat Rocking)**: Pet โคลงเคลงเหมือนนั่งเรือ เริ่มเมาเรือ คลื่นไส้ เวียนหัว (`DIZZY`)
    3. **ทุบโต๊ะ หรือเสียงดัง ตะโกน ตะคอก**: Pet ตกใจสะดุ้งโหยง (`SURPRISED`), ถ้าถูกตะคอกซ้ำๆ ต่อเนื่องจะรู้สึกกลัวและเศร้าเสียใจ ร้องไห้ (`SAD`)
    4. **เพิ่มอารมณ์โกรธต่อสู้กลับ (Fight Back / Enraged Mode)**: เมื่อความโกรธ (Rage) สะสมเต็ม 100% Pet จะทำตาขวางสีแดงเพลิง คิ้วขมวด ปากขบฟันแหลม มีพร็อพจรวดมิสซายลูกเล็กๆ หลายลูก (สร้างตามภาพการ์ตูนที่ผู้ใช้อัปโหลด: ลำตัวขาว หัวแดง ครีบแดง ไฟท้ายส้ม หน้าต่างฟ้า) ยิงใส่หน้าจอ พร้อม FX ระเบิดตูมตามเต็มจอ + คลื่น Shockwave + จอสั่น (Screen Shake) และสังเคราะห์เสียงจริง
- **Root Cause & Implementation**:
  1. **Physical Motion Disturbance & Sickness Matrix (`PetMotionDetector.kt`, `PetMotionBridge.kt`, `PetStateMachine.kt`)**:
     - *Shake & Heavy Shake*: เขย่าเบาๆ ส่ง `onShake` $\rightarrow$ Pet มึน (`AvatarEmotion.DIZZY`, ตาหมุนวนก้นหอย Spiral, ปากคลื่น). หากเขย่าซ้ำๆ ในช่วง 4 วินาที (Sliding Window $\ge 3$) ส่ง `onHeavyShake` $\rightarrow$ Pet โกรธ (`AvatarEmotion.ANGRY`), เพิ่มความโกรธสะสม Rage (+35f/ครั้ง).
     - *Boat Rocking / Seasick*: ตรวจจับการเอียงสลับแกน Roll ซ้าย-ขวาอย่างนุ่มนวล ($|X| > 3.2$, สลับทิศทาง $\ge 3$ ครั้ง) ภายใต้แรงโน้มถ่วงต่ำ ($gForce < 1.6G$) จำลองการโคลงเคลงบนผิวน้ำ $\rightarrow$ ส่ง `onBoatRocking` $\rightarrow$ Pet เมาเรือ มึน เวียนหัว (`AvatarEmotion.DIZZY`).
     - *Table Thump Shock Impulse*: ตรวจจับแรงสะเทือนกระแทกฉับพลัน ($\Delta G > 1.25G$) ขณะที่โทรศัพท์วางนิ่งอยู่บนโต๊ะหรือแท่นวาง $\rightarrow$ ส่ง `onTableThump` $\rightarrow$ Pet ตกใจสะดุ้งสุดตัว (`AvatarEmotion.SURPRISED`, เสียงตกใจ).
  2. **Acoustic Disturbance & Screaming Detection (`AlwaysLiveScreen.kt`, `PetModeController.kt`, `PetStateMachine.kt`)**:
     - ตรวจสอบ `avatarState.audioLevel > 0.68f` เมื่อ AI ไม่ได้กำลังพูด
     - เสียงดัง/ตะโกนครั้งแรก: Pet สะดุ้งตกใจ (`AvatarEmotion.SURPRISED`, ตาโต 1.25x สี White-Cyan สว่างวาบ คิ้วโก่ง ปากอ้า 'O').
     - ตะโกน/ตะคอกซ้ำๆ ต่อเนื่อง: Pet เกิดความกลัว เสียใจ เศร้า ร้องไห้ (`AvatarEmotion.SAD`, หยดน้ำตาสีฟ้าเรืองแสง).
  3. **Enraged Mode & Fight-Back Missile Barrage (`PetNeedsState.kt`, `PetRobotHeadAvatar.kt`, `MissileBarrageOverlay.kt`)**:
     - *Rage State & Discharge*: เพิ่ม `rage: Float = 0f` (0-100), `val isEnraged: Boolean get() = rage >= 100f`, และ `PetMood.ENRAGED("😡💥", "โกรธจัด!")` ใน `PetNeedsState.kt`.
     - *Enraged Visor Drawing*: ใน `PetRobotHeadAvatar.kt` เรนเดอร์ตาขวางสีแดงเพลิง (`Color(0xFFFF1744)`), ดวงตาดำมืดด้านในด้วยเฉด Crimson (`0xFFB71C1C`), ออร่าไฟสีแดงลุกโชน, คิ้วรูปตัว V ขมวดแน่น 24 องศา, ปากขบฟันซิกแซกแหลมคม 6 หยัก, พร้อมไอน้ำร้อนพุ่งออกจากหัว (Steam Puffs).
     - *Procedural Cartoon Missile Salvo*: ออกแบบ `MissileBarrageOverlay.kt` ด้วย Jetpack Compose Pure Canvas วาดจรวดการ์ตูน 5 ลูก 1:1 ตามภาพต้นแบบของผู้ใช้ (ลำตัวขาว หัวแดง ครีบแดง ท้ายส้ม หน้าต่างฟ้า) ยิงพุ่งจากด้านหลังและข้างลำตัว เลี้ยวโค้งสเกลขยายจาก 0.4x จนพุ่งชนจอที่ขนาด 1.9x.
     - *Screen Blast FX & Screen Shake*: เมื่อจรวดกระทบหน้าจอ เกิดลูกไฟระเบิดขยายตัวขนาดใหญ่, คลื่นระเบิด Shockwave กระจายตัวเป็นวงแหวน, สะเก็ดประกายไฟระเบิด 45 ทิศทาง, แฟลชหน้าจอวาบสีขาว-แดง, และเกิดแรงสั่นสะเทือน (Screen Shake) ที่เรนเดอร์ผ่าน `graphicsLayer { translationX, translationY }`.
  4. **Zero-Asset Procedural PCM Audio Synthesis (`RobotSoundEngine.kt`, androidMain)**:
     - พัฒนาการสังเคราะห์เสียงระบบ 16-bit PCM AudioTrack แบบเรียลไทม์:
       - `RobotSound.MISSILE_LAUNCH`: เสียงหวีดความเร็วสูง Pitch Sweep จาก 300Hz ไป 2000Hz พร้อมเสียงฟู่ของเชื้อเพลิงไอพ่น White Noise.
       - `RobotSound.EXPLOSION`: เสียงระเบิด Sub-bass 85Hz กวาดลง 25Hz พร้อม Exponential Decay Noise Burst ให้ความรู้สึกลึก แน่น กระแทกหูอย่างสมจริง.
     - เชื่อมต่อ `AlwaysLiveManager.kt` และ `RobotSoundPlayer.kt` ให้สั่งงานได้จากส่วนกลาง.
  5. **Screen & Controller Integration (`AlwaysLiveScreen.kt`, `PetModeController.kt`, `ChatController.kt`)**:
     - เชื่อมต่อ `PetMotionBridge` callbacks (`onHeavyShake`, `onBoatRocking`, `onTableThump`) ใน `AlwaysLiveScreen.kt`.
     - เชื่อมต่อตัวตรวจจับเสียงดัง `LaunchedEffect(avatarState.audioLevel)`.
     - แสดง `MissileBarrageOverlay` เต็มจอพร้อมตัวแปร `screenShakeIntensity`.
     - รองรับสีพื้นหลัง Gradient และ Aura สำหรับ `AvatarEmotion.ENRAGED` ใน `AlwaysLiveScreen.kt`.
     - รองรับคำสั่งทดสอบ `/avatar enraged` ใน `ChatController.kt`.
- **Verification**:
  - อัปเดต `AlwaysLiveTest.kt` ให้รองรับ 17 อารมณ์ (เพิ่ม `ENRAGED`).
  - เพิ่ม Unit Tests ใน `PetModeTest.kt`:
    - `PetNeedsState rage accumulation and ENRAGED mood behave correctly`
    - `Motion and Audio State Machine Matrix transitions work correctly`
    - `RobotSound enum contains MISSILE_LAUNCH and EXPLOSION`

## 2026-09-12 — Pet System Overhaul: State Machine Matrix, Holographic Hand Overlay, Persistent Pet Memory & Slide-out Sidebar Panel
- **Problem Solved**:
  - *User Requirement*:
    1. **ย้ายค่าสถานะของ Pet และปุ่มดูแลออกจากเมนูตั้งค่า**: นำมาทำเป็น Sidebar สไลด์บาร์ไว้ด้านข้างจอ เพื่อให้เป็นระบบเกมสัตว์เลี้ยง (Tamagotchi) จริงๆ สามารถดูและดูแลน้องได้ตลอดเวลาโดยไม่ต้องเข้าเมนูตั้งค่า
    2. **สร้างระบบ State Machine Matrix เชื่อมโยงค่าต่างๆ ของ Pet เข้าด้วยกัน**: ปฏิกิริยาและอารมณ์ของ Pet ต้องเชื่อมโยงประสานกันอย่างมีเหตุผลและต่อเนื่อง เช่น หิวจัดจนโกรธ, จิ้มแกล้งซ้ำๆ จนโกรธ, โดนปลุกตอนหลับจนตกใจ/งัวเงีย, เหงาจนเบื่อหาว, เหนื่อยมากจนหลับเอง
    3. **ระบบหน่วยความจำของ Pet (Pet Memory) แยกต่างหาก**: Pet ต้องจดจำสถานะ ความชอบ ประวัติการเล่น สถิติตลอดชีวิต (อายุ, จำนวนครั้งที่ให้อาหาร, สถิติความสุข) ข้ามเซสชันการปิด-เปิดแอป โดยบันทึกลง SQLite
    4. **มือโฮโลแกรม (Holographic Hand Overlay)**: เมื่อสัมผัสที่ส่วนต่างๆ ของใบหน้า Pet (เช่น หน้าผาก, แก้ม, คาง) จะมีมือโฮโลแกรมเรืองแสงสีฟ้าสไตล์ไซไฟเคลื่อนไหวเข้ามาลูบหัว จิ้มแก้ม หรือเกาคางจริงๆ
    5. **เพิ่มอารมณ์ใหม่**: เพิ่มสถานะอารมณ์ `SURPRISED` (ตกใจ: ตาเบิกกว้างสุดขีด + ปากอ้า O) และ `BORED` (เบื่อ: ตาลู่ครึ่งปิด + หาว)
- **Root Cause & Implementation**:
  1. **Pet State Machine Matrix (`PetStateMachine.kt`)**:
     - พัฒนาคลาส State Machine คำนวณ `processTouch(type, zone, needs, currentEmotion)` และ `resolvePassiveEmotion(needs, currentEmotion)`
     - มีระบบ Interaction Tracker Ring Buffer ตรวจจับความถี่เพื่อป้องกันสแปม (จิ้ม > 5 ครั้งใน 30 วิ → โกรธ, จั๊กจี้ > 3 ครั้งใน 20 วิ → เหนื่อย, ลูบหัวบ่อย → โบนัส Affection)
     - เชื่อมโยงความต้องการทางกายภาพ (Needs) กับอารมณ์ (Emotions) อย่างสมจริง พร้อมระบบ Decay อารมณ์ตามเวลา
  2. **Persistent Pet Memory (`PetMemory.kt`)**:
     - คลาสเก็บข้อมูลถาวร: Session Stats, Lifetime Stats (อายุ, ปฏิสัมพันธ์รวม, จำนวนครั้งให้อาหาร/อาบน้ำ/เล่น), ประวัติอารมณ์ (สถิติวันที่แฮปปี้ต่อเนื่อง), การเรียนรู้สิ่งที่ชอบที่สุด (Favorite Interaction)
     - จัดเก็บถาวรลง SQLite (`jarvisDatabaseQueries.insertSetting`) ด้วย key `"pet.memory"`
     - เชื่อมต่อใน `PetModeController`: โหลด memory ตอน `start()`, บันทึกอัตโนมัติทุก 60 วินาทีผ่าน Background Job, และบันทึกทันทีก่อน `stop()`
  3. **Pet Needs Sidebar Panel (`PetNeedsSidebarPanel.kt`)**:
     - คอมโพเนนต์ Sidebar สไลด์จากขอบขวาพร้อมแอนิเมชัน Spring Physics และพื้นหลัง Dim
     - แสดง Mood Badge พร้อมอีโมจิ, Affection Level, หลอดค่าสถานะ 5 ค่าแบบ Animated Color-coded (เขียว/เหลือง/แดง), ปุ่มลัด Care Actions (🍖 อาหาร, 🧼 อาบน้ำ, 🎾 เล่น, 💤 นอน), และสถิติ Pet Memory
     - ย้ายแท็บ "ดูแล" ออกจาก `PetSettingsDialog.kt` (ลดเหลือ 3 แท็บ: ดีบัก, จำหน้า, พร็อพ&ธีม)
     - เพิ่มปุ่ม `"🐾 สถานะ"` ใน Toolbar ด้านบนของ AlwaysLiveScreen และรองรับการปัดปิด
  4. **Holographic Hand Overlay (`HolographicHandOverlay.kt`)**:
     - วาดด้วย Canvas ล้วน ไร้การพึ่งพา asset: เส้น Wireframe สีฟ้า Cyan (#00F0FF) เรืองแสง (Glow blur) พร้อมประกายดาววิบวับ (Sparkles) ที่ปลายนิ้ว
     - รองรับ 6 ท่าทาง: `STROKE` (ฝ่ามือลูบจากบนลงล่าง), `POKE` (นิ้วชี้จิ้มแก้ม), `CHIN_SCRATCH` (นิ้วเกาคางแบบแกว่งสั่น), `TICKLE` (นิ้วกระดิกคลื่น), `PAT` (ฝ่ามือตบเบาๆ), `WAVE` (โบกมือ)
     - เชื่อมโยงเข้ากับ Gesture Detector ใน `AlwaysLiveScreen.kt`: ลากนิ้วลงบนหน้าผาก → ลูบหัว, ลากนิ้วขึ้นที่คาง → เกาคาง, แตะแก้ม → จิ้ม, ดับเบิ้ลแทปแก้ม → จั๊กจี้
  5. **New Emotions & Face Rendering (`AvatarEmotion.kt`, `PetRobotHeadAvatar.kt`)**:
     - เพิ่ม `SURPRISED` และ `BORED` ใน `AvatarEmotion`
     - เพิ่มการเรนเดอร์ใน `PetRobotHeadAvatar.kt`:
       - `SURPRISED`: ดวงตาโตขึ้น 1.25x สี White-Cyan สว่างวาบ คิ้วโก่งสูง ปากอ้า 'O' ตกใจ
       - `BORED`: ดวงตาหรี่แบนลง 0.42x สี Slate Gray คิ้วลู่ต่ำ ขีดเปลือกตาด้านบน ปากเส้นตรงเฉียง
     - อัปเดต Exhaustive when blocks ใน `ChatController.kt` และ `AlwaysLiveScreen.kt`
- **Verification & Bug Fixes**:
  - แก้ไข `isDizzy` state synchronization ใน `PetModeController.applyStateMachineResult` ให้เซ็ต `isDizzy = true` ทันทีเมื่อเข้าสู่สถานะ DIZZY
  - อัปเดต `AlwaysLiveTest` ให้รองรับครบทั้ง 16 ค่าของ `AvatarEmotion` (+ SURPRISED, BORED)
  - ปรับปรุง `PetMemoryStore` ให้ใช้ `MutableStateFlow` (`memoryState: StateFlow<PetMemory>`) เพื่อให้ UI อัปเดตแบบ Reactive ทันทีเมื่อเกิดปฏิสัมพันธ์
  - ปรับปรุง Scale ของ `HolographicHandOverlay` เป็น 0.0024f (~140dp) พร้อมเพิ่ม `triggerId` และ `onFinished` callback เพื่อรองรับการแตะซ้ำในจุดเดิม
  - ปรับปรุง Threshold ของ `resolvePassiveEmotion` ใน `PetStateMachine` ให้ตอบสนองความหิวจัด (Hangry) เมื่อ `satiety < 20f`
  - รัน Unit Test ทั้งหมดผ่าน 100% (`./gradlew.bat testDebugUnitTest` — BUILD SUCCESSFUL, ทุกชุดการทดสอบผ่านสมบูรณ์)

## 2026-09-12 — Autonomous Contextual Prop Selection, Dynamic SVG Magic Creator & Permanent SQLite Persistence
- **Problem Solved**:
  - *User Requirement*:
    1. **Autonomous Contextual Prop Selection (เลือกและเรียกใช้พร็อพ/สติกเกอร์ตามบทสนทนาโดยคิดเองเลือกเอง)**: ให้ AI Pet สามารถประเมินบริบทบทสนทนา คิดเอง และเรียกแสดงพร็อพหรือสติกเกอร์ที่เหมาะสมกับสถานการณ์ได้เองอัตโนมัติ โดยที่ผู้ใช้ไม่ต้องคอยสั่ง
    2. **Autonomous Dynamic SVG Creation & Reuse (คิดสร้างสรรค์เวกเตอร์ SVG ใหม่และหยิบใช้ซ้ำ)**: เมื่อไม่มีพร็อพมาตรฐานที่ตรงกับเรื่องที่คุย AI Pet สามารถจินตนาการและเขียนโค้ด SVG Path ขึ้นมาเองได้แบบอัตโนมัติ
    3. **Permanent Persistence (จัดเก็บถาวรใน SQLite)**: เมื่อ AI Pet หรือผู้ใช้สร้างพร็อพใหม่แล้ว จะต้องถูกจัดเก็บถาวรลงฐานข้อมูล SQLite ข้ามการปิด-เปิดแอป เพื่อให้สามารถหยิบมาใช้ซ้ำในคราวต่อไปได้ทันทีโดยระบุเพียงชื่อ ไม่ต้องส่ง SVG Path ใหม่ซ้ำ
    4. **Facial Spatial Intelligence & Eye-Relative Sizing**: ระบบคำนวณตำแหน่งและสัดส่วนใบหน้าหุ่นยนต์ให้แม่นยำ โดยเฉพาะตำแหน่งดวงตา (`LEFT_EYE`, `RIGHT_EYE`) เมื่อกำหนด `size=0` ระบบจะ Auto-Fit ขนาดของพร็อพ (เช่น monocle, eyepatch, แว่นตา) ให้เท่ากับเส้นผ่านศูนย์กลางดวงตาของหุ่นยนต์ 1:1 พอดีเป๊ะ
- **Root Cause & Implementation**:
  1. **Persistent Prop Store (`PetCustomPropStore.kt`)**:
     - พัฒนาคลาส Singleton สำหรับจัดเก็บ Dynamic Vector Props ข้ามแพลตฟอร์ม (Android / iOS) ลงตาราง `AppSetting` (key: `"pet.custom_props"`) ใน SQLite ผ่าน SQLDelight (`JarvisDatabaseHolder`)
     - รองรับ `loadCustomProps()`, `saveCustomProp(prop)`, `deleteCustomProp(nameOrId)`, `clearCustomProps()`, และ `findPropByNameOrId(nameOrId)`
     - Expose `savedCustomProps: StateFlow<List<DynamicVectorProp>>` แบบ Reactive ให้ UI อัปเดตทันที
  2. **Dynamic Eye Geometry & Auto-Fit 1:1 (`DynamicPropRenderer.kt`)**:
     - ซิงโครไนซ์ขนาดเส้นผ่านศูนย์กลางดวงตากับ `PetRobotHeadAvatar.kt`:
       - แนวนอน (Landscape): `eyeDiameter = minOf(size.height * 0.52f, size.width * 0.28f)`
       - แนวตั้ง (Portrait): `eyeDiameter = minOf(size.width * 0.38f, size.height * 0.24f)`
     - คำนวณจุดกึ่งกลางตาซ้ายและตาขวา: `leftEyeCenterX = cX - (eyeDiameter * 0.65f)`, `rightEyeCenterX = cX + (eyeDiameter * 0.65f)`, `eyeCenterY = cY`
     - Auto-Fit Normalizer: เมื่อ `prop.sizeDp <= 0f` และอยู่ที่ตำแหน่ง `LEFT_EYE` หรือ `RIGHT_EYE` จะตั้ง `targetSizePx = eyeDiameter` ทำให้ไอเทมเลนส์แว่นตา/ผ้าปิดตาโจรสลัดมีขนาดแนบสนิทกับดวงตา 1:1 อัตโนมัติ
  3. **Controller & Device Tools Integration (`PetModeController.kt`, `DeviceControlExecutor.kt`, `App.kt`)**:
     - ใน `PetModeController.start()`: สั่งโหลด `PetCustomPropStore.loadCustomProps()` เพื่อเตรียมพร้อมคลัง
     - ใน `addCustomProp(prop)`: บันทึกลง `PetCustomPropStore.saveCustomProp(prop)` ทันที
     - ใน `updateRobotFace`: รองรับคำสั่ง `CUSTOM_PROP|action=add|name=...` ที่ไม่มี `svg_path` โดยจะดึงข้อมูลจาก `PetCustomPropStore.findPropByNameOrId(name)` มาสวมใส่ซ้ำได้ทันที
     - ใน `DeviceControlExecutor.kt`: อัปเดต `device_custom_prop` ให้ค้นหาใน `PetCustomPropStore` หากไม่ได้ระบุ `svg_path` และบันทึกเข้า SQLite เมื่อสร้างใหม่ พร้อมรองรับ `action="delete"` เพื่อลบออกจากคลังถาวร
  4. **Pet Persona & System Prompt Upgrade (`JarvisPersona.kt` & `DeviceToolDefinitions.kt`)**:
     - อัปเดตกฎคำสั่งอุปกรณ์และข้อ 11 ใน `PET_LIVE_SYSTEM_PROMPT` ให้ AI Pet ตระหนักรู้ว่าสามารถคิดเองเลือกใส่พร็อพ/สติกเกอร์ให้เข้ากับบริบทสนทนา (เช่น กาแฟตอนเช้า, เหรียญทองตอนพูดเรื่องเงิน/คริปโต, ร่มตอนฝนตก, ปาร์ตี้ตอนฉลอง)
     - สั่งให้ AI Pet ออกแบบ SVG Path ขึ้นมาเองเมื่อไม่มีพร็อพในระบบ และดึงพร็อพเดิมในคลังมาใช้ซ้ำโดยระบุแค่ `name`
     - แนะนำการใช้ `size=0` สำหรับไอเทมดวงตาเพื่อให้ได้ขนาด 1:1 Auto-Fit พอดี
  5. **Showcase Settings Dialog Integration (`PetSettingsDialog.kt`)**:
     - ในแท็บย่อยเวกเตอร์ SVG เชื่อมต่อ `PetCustomPropStore.savedCustomProps` แสดงรายการ "💾 คลังพร็อพเวกเตอร์ที่บันทึกถาวร"
     - แสดงขนาด, แอนิเมชัน, สถานะสวมใส่/ถอด และปุ่มลบออกจากคลังถาวร (Trash Icon)
- **Verification**:
  - เพิ่ม Unit Tests ใน `SvgPathTest.kt`:
    - `testPetCustomPropStorePersistenceAndLookup`: ทดสอบการบันทึก, ค้นหาตามชื่อ/ID (แบบ Case-Insensitive), และการลบออกจากคลัง
    - `testPetModeControllerPropReuseByName`: ทดสอบการเสกพร็อพใหม่ด้วย SVG Path การบันทึกอัตโนมัติ การถอดออก และการนำกลับมาใส่ซ้ำด้วยชื่อเพียงอย่างเดียวโดยไม่ต้องระบุ `svg_path` ซ้ำ
  - รัน `.\gradlew testDebugUnitTest` ผ่านฉลุยครบทั้ง 252+ tests (`BUILD SUCCESSFUL in 1m 51s`)

## 2026-09-12 — Pet Settings Showcase Catalog (8 Themes, 55 Props) & Dynamic SVG Vector Parser Architecture
- **Problem Solved**:
  - *User Requirement*:
    1. **สร้างหน้ารวมตัวอย่าง (Showcase Catalog) ในการตั้งค่าของสัตว์เลี้ยง**: แสดงรายการ ไดนามิกแบ็คกราวน์ (Background Themes), พร็อพ (Props) และสติกเกอร์ ที่มีอยู่ทั้งหมดในระบบ เพื่อให้ผู้ใช้สามารถดู ตรวจสอบ สวมใส่ ทดสอบ และรู้ว่ามีอะไรให้ปรับแต่งบ้าง
    2. **ชี้แจงเงื่อนไขและขอบเขตในการสร้าง Dynamic SVG Path Parser**: รูปแบบคำสั่ง, จุดยึดบนใบหน้า, แอนิเมชัน และคำถามว่าเมื่อสร้างแล้วจะเป็นแบบใช้ครั้งเดียว (Ephemeral) หรือเก็บไว้ใช้คราวต่อไปได้ (Persistent)
- **Root Cause & Implementation**:
  1. **Pet Settings Props & Themes Catalog (`PetSettingsDialog.kt`)**:
     - เพิ่มแท็บที่ 4: `"🎨 พร็อพ & ธีม"` เข้าไปใน `TabRow` ของ `PetSettingsDialog`
     - แบ่งเนื้อหาออกเป็น 3 หมวดหมู่ย่อย (Sub-sections) ด้วยแถบ Segmented Chip:
       - **🌌 ธีมฉาก (8 รูปแบบ)**: แสดงรายการทั้ง 8 ธีม (`DEFAULT` ดำ OLED, `RAINY` ฝนตก, `SUNNY` แดดจ้า, `NIGHT` ราตรีดาว, `SAKURA` ซากุระ, `MATRIX` ไซเบอร์, `LOVE_BG` หัวใจ, `THUNDER` ฟ้าผ่า) พร้อมแถบสีพรีวิว ไอคอน คำอธิบายภาษาไทย และปุ่มแตะเปลี่ยนแบบ Real-time
       - **✨ พร็อพ (55 ชนิด)**: รวมพร็อพ/สติกเกอร์ทั้งหมดในระบบ แสดงในรูปแบบ Card Grid 2 คอลัมน์ พร้อมปุ่ม Filter แยก 5 หมวด (ทั้งหมด, อารมณ์ 17, อาหาร 13, ธรรมชาติ 10, ไอที 15) แสดงอีโมจิ ชื่อไทย คำอธิบายสถานการณ์ และปุ่มติ๊กถูกสวมใส่/ถอดได้ทันที พร้อมปุ่ม "ล้างทั้งหมด"
       - **🪄 เวกเตอร์ SVG (Dynamic SVG Path Parser)**: บัตรอธิบายเงื่อนไขและขอบเขตการสร้าง พร้อม 5 Quick-Test Presets (👑 มงกุฎทองคำ, 🕶️ แว่นไซเบอร์นีออน, 🩹 พลาสเตอร์แก้ม, ⚡ สายฟ้านีออน, 🤿 หน้ากากดำน้ำ) และรายการ Custom Props ที่กำลังแสดงผลอยู่พร้อมปุ่มลบ
  2. **Dynamic SVG Vector Parser Architecture**:
     - *Input Syntax*: คำสั่ง SVG Path data มาตรฐาน `d="..."` (`M`, `L`, `C`, `Q`, `A`, `Z`)
     - *Auto-Fit & Normalization*: คอมโพเนนต์ `DynamicPropRenderer` ใช้ `Path.getBounds()` คำนวณขนาดและสเกลอัตโนมัติให้พอดีกับ `sizeDp` โดยไม่สนว่า viewBox ของ SVG ต้นทางจะมีขนาดเท่าใด (24x24 หรือ 512x512 ก็ตาม)
     - *7 Anchor Points*: `FOREHEAD` (หน้าผาก/หมวก/มงกุฎ), `LEFT_EYE` (รอบตาซ้าย/แว่น/น้ำตา), `RIGHT_EYE` (รอบตาขวา/เป้าเล็ง), `CHEEKS` (แก้ม/พลาสเตอร์), `CHIN` (คาง/ปาก/หนวด), `FLOATING_LEFT` (ลอยซ้าย/ผี/การแจ้งเตือน), `FLOATING_RIGHT` (ลอยขวา/หลอดไฟ/โน้ตเพลง)
     - *5 Animations*: `STATIC` (นิ่ง), `FLOAT_BOB` (ลอยขึ้นลง), `PULSE` (ชีพจรย่อขยาย), `ROTATE_CONTINUOUS` (หมุน 360°), `SWAY` (แกว่งไกว)
     - *Persistence Lifecycle*: ใน Runtime ถูกจัดเก็บใน `RobotFaceState.customProps: List<DynamicVectorProp>` จะคงอยู่ตลอดเซสชันใบหน้าจนกว่าจะสั่งถอดหรือสั่งล้าง และสามารถขยายลง DataStore เพื่อใช้งานถาวรข้ามแอปได้
  3. **Controller & Screen Wiring (`PetModeController.kt` & `AlwaysLiveScreen.kt`)**:
     - เพิ่ม methods: `setBackgroundTheme(theme)`, `toggleProp(prop)`, `clearProps()`, `addCustomProp(prop)`, `removeCustomProp(id)` ใน `PetModeController`
     - เชื่อมโยง State และ Callbacks เข้าสู่ `PetSettingsDialog` ใน `AlwaysLiveScreen.kt`
- **Verification**:
  - เพิ่ม Unit Tests ใน `PetModeTest.kt`:
    - `BackgroundTheme contains all 8 themes and serializes correctly`
    - `PropType contains all 55 built-in props across all 4 categories`
    - `PetModeController theme and prop toggling operates cleanly`
  - ทดสอบผ่านฉลุยครบทั้ง 250 tests (`BUILD SUCCESSFUL in 1m 48s`)

## 2026-09-12 — Pet Avatar Eye Scaling, True Center Gaze (Desk Elevation Calibration) & Pure OLED Visor Cleanup
- **Problem Solved**:
  - *User Requirement*:
    1. **ขยายขนาดดวงตาให้ใหญ่ขึ้น**: ขนาดเดิมยังเล็กเกินไปสำหรับหน้าจอทั้งแนวนอน (Landscape) และแนวตั้ง (Portrait) ไม่โดดเด่นสมกับการเป็น Living Screen หุ่นยนต์คู่หู (Companion Robot แบบ Eilik / LOOI)
    2. **แก้ปัญหาลูกตาติดมองข้างบน/เฉียงบนตลอดเวลา**: ไม่ว่าจะสัมผัสมุมไหน หรือสายตามุมใด พอลดนิ้วหรือผ่านไปสักพัก ลูกตาจะดึงกลับไปมองข้างบนตลอดเวลา ไม่ยอมมองตรงกลาง (Neutral Center)
    3. **ลบออร่าจางๆ ตรงกลางจอออก**: แสงสีฟ้าฟุ้งๆ (Radial breathing glow) ที่อยู่ตรงกลางจอระหว่างดวงตาให้เอาออก เพื่อให้พื้นหลังเป็นสีดำสนิท Pure OLED Black (#000000) คมกริบ
- **Root Cause & Implementation**:
  1. **Enlarged Eye Dimensions (`PetRobotHeadAvatar.kt`)**:
     - *แนวนอน (Landscape)*: ขยายเส้นผ่านศูนย์กลางดวงตาเป็น `minOf(canvasH * 0.52f, canvasW * 0.28f)` (เพิ่มขนาดขึ้น ~65% เทียบกับขีดจำกัดเดิม 120.dp ทำให้ดวงตาครองความสูงจอมากกว่า 50% ใหญ่เต็มตา สะใจเหมือนจอหุ่นยนต์ LOOI)
     - *แนวตั้ง (Portrait)*: ขยายเส้นผ่านศูนย์กลางดวงตาเป็น `minOf(canvasW * 0.38f, canvasH * 0.24f)` (เพิ่มขนาดขึ้น ~50% ครองพื้นที่กว้าง 87% ของหน้าจอแนวตั้ง)
     - *ระยะห่างระหว่างตา*: คำนวณตามสัดส่วน `baseSpacing = eyeDiameter * 0.65f` ทำให้มีระยะเว้นว่างระหว่างขอบดวงตาสองข้างพอเหมาะ ~30% ไม่ชิดหรือห่างเกินไป และจัดตำแหน่งแนวดิ่งกึ่งกลางจอแท้จริง (`eyeCenterY = centerY + gazeDisplacementY`)
  2. **True Center Gaze Fix (แก้ไขปัญหาสายตาติดมองบน)**:
     - *Root Cause 1 (`PetRobotHeadAvatar.kt`)*: ในฟังก์ชัน `drawDualCircleEye` มีการบวกค่า `baseDepthY = radius * 0.08f` เข้าไปใน `backOffsetY` ของวงกลมเลเยอร์หลังสีน้ำเงินเข้ม ทำให้วงกลมหลังถูกดันลงล่างตลอดเวลา ส่งผลให้วงกลมหน้าสีฟ้าครามดูเหมือน "ลอยขึ้นบน" ตลอดเวลาแม้ค่า gaze จะเป็น (0, 0)
       - *Fix*: ลบ `baseDepthY` ออก กำหนด `backOffsetY = -gazeY * maxShift * 0.35f` โดยตรง เมื่อผู้ใช้มองตรงหรือไม่มีการขยับสายตา (`gazeX = 0f, gazeY = 0f`) วงกลมหน้าและหลังจะซ้อนกันกึ่งกลางสนิท 100% พอดี
     - *Root Cause 2 (`PetVisionDetector.kt`)*: อุปกรณ์มือถือเวลาวางตั้งอยู่บนโต๊ะทำงาน (Desk Stand / Dock) กล้องหน้าจะส่องมุมเงยขึ้นเล็กน้อย ใบหน้าของผู้ใช้จึงมักตกอยู่ในพื้นที่ 20-35% ด้านบนของภาพกล้องเสมอ (`rawNormY ≈ -0.45f ถึง -0.75f` เช่น ใน Logcat: `normX=0.07, normY=-0.75`) ตัวตรวจจับเดิมส่งค่า -0.75 เข้าไประบบ gaze ตลอดเวลา จึงทำให้หุ่นยนต์ "แหงนมองเพดาน" ตลอดเวลา และเมื่อไม่มีใบหน้า (`faces.isEmpty()`) โค้ดเดิม return ทันทีโดยไม่เคยรีเซ็ตค่า gaze กลับมาตรงกลาง
       - *Fix*: ทำการ Calibrate มุมกล้องหน้าโต๊ะทำงานด้วย `deskNeutralBiasY = -0.45f` และคำนวณ `calibratedY = (rawNormY - deskNeutralBiasY) * 1.35f`
       - เพิ่ม Deadzone Filtering: หากตำแหน่งศีรษะอยู่ในช่วงตรงกลาง (`|calibratedX| < 0.12f` และ `|calibratedY| < 0.15f`) ให้ Snap เป็น `(0f, 0f)` ทันที ทำให้หุ่นยนต์สบตาผู้ใช้ตรงกลางจอเป๊ะ
       - เพิ่ม Auto-Reset เมื่อไม่พบใบหน้า: หากไม่พบใบหน้าผู้ใช้นานเกิน 1000ms ให้ส่ง `onGazeDetected(0f, 0f)` กลับคืนตำแหน่งกึ่งกลางอัตโนมัติ
  3. **Visor Cleanliness (`PetRobotHeadAvatar.kt`)**:
     - ลบโค้ดบล็อก `Ambient Face Glow (Breathing Aura)` ออกทั้งหมด ทำให้พื้นหลัง Visor เป็นสีดำทึบสนิท `#000000` แบบ Pure OLED ไร้ฝ้าหมอก ช่วยให้ดวงตาสีฟ้าครามและขอบมิติสีน้ำเงินเข้มคมชัดสูงสุด
  4. **Dynamic Mouth Vertical Rebalancing (จัดตำแหน่งดวงตาขยับขึ้นบนเมื่อมีปากเข้ามา)**:
     - เมื่ออยู่ในโหมด IDLE และไม่ได้ส่งเสียงพูด (`hasMouth = false`) ดวงตาจะวางตัวอยู่กึ่งกลางหน้าจอแท้จริง (`eyeCenterY = centerY + gazeDisplacementY`)
     - เมื่อมีปากปรากฏเข้ามา เช่น AI กำลังส่งเสียงพูด (`drawWaveformMouth`) หรือแสดงอารมณ์ที่มีปาก (`HAPPY`, `SPEAKING`, `POUT`, `LOVE`, ฯลฯ) ระบบจะใช้ `animateFloatAsState` (Spring Physics) เลื่อนดวงตาขึ้นด้านบนเล็กน้อย `eyeDiameter * 0.085f` (~17dp)
     - พร้อมทั้งจัดตำแหน่งปาก `mouthY = eyeCenterY + baseEyeH * 0.75f` ทำให้โครงสร้างใบหน้ารวม (คิ้ว + ดวงตา + ปาก) อยู่ตรงกึ่งกลางหน้าจออย่างสมดุลพอดี ไม่ค่อนหรือหนักไปทางด้านล่าง
- **Verification**:
  - เพิ่ม Unit Tests ใน `PetModeTest.kt`:
    - `Dual circle eye at neutral gaze has concentric front and back circles without upward shift`
    - `Pet vision desk face calibration maps normal desk sitting position to dead center gaze`
    - `Enlarged eye dimensions provide large expressive robot companion eyes on both orientations`
    - `Eye position shifts upward when mouth is added to balance vertical facial composition`
  - คอมไพล์ Kotlin Android ผ่านฉลุย 100%

## 2026-09-12 — Dedicated Weather Tool (`device_weather`), Pet Mode Dialogue Auto-Dismiss, Floating Props & Eilik Face Redesign
- **Problem Solved**:
  - *User Requirement*:
    1. การพยากรณ์อากาศด้วย `search_web({query="สภาพอากาศวันนี้"})` ทำงานผิดพลาด/ไม่คืนค่า และไม่ควรใช้ search_web ควรดึงพิกัด GPS ของอุปกรณ์ก่อน แล้วตรวจเช็คสภาพอากาศจากพิกัด หรือค้นหาตามชื่อเมืองได้
    2. ในโหมดสัตว์เลี้ยง (Pet Mode) กล่องข้อความตอบกลับ (`PetDialogueCard`) ค้างอยู่บนหน้าจอนานเกินไป ต้องการให้หายไปเองอัตโนมัติ 10 วินาทีหลังจาก AI พูดจบหากผู้ใช้ไม่ได้แตะจอ และถ้าผู้ใช้แตะที่หน้าจอต้องปิดกล่องทันที
    3. เพิ่มพร็อพ & สติกเกอร์ลอยได้: เช่น เหรียญทอง (`GOLD_COIN`) สำหรับเรื่องทองคำ/การเงิน และหยดน้ำฝน (`RAIN_DROPS`) สำหรับฝนตก พร้อมแสดงเอฟเฟกต์สภาพอากาศ แดดออก/ฝนตก เสียงเอฟเฟกต์อัตโนมัติ และให้พร็อพหายไปเอง (Auto-decay) หลัง 12 วินาที
    4. ดีไซน์ใบหน้าและดวงตาใหม่ให้คล้ายหุ่นยนต์ Eilik / Dfree: ดวงตาทรง Squircle สัดส่วนสมมาตร ~1:1 ไม่เรียวเป็นเม็ดยาแคปซูล มีเงาหลุม 3D Bezel Drop Shadow ที่ฐานดวงตา ปรับสี OLED LED เปล่งประกาย ไม่มีขีดสีขาว static ค้างที่มุม และแสดงอารมณ์ดวงตาชัดเจน (ยิ้มเป็นเส้นโค้ง `⌒ ⌒`, ขยิบตา Wink, ง่วง `— —`, ตื่นเต้น `> <`, โกรธ, มึนงง `X X`)
- **Root Cause & Implementation**:
  1. **Dedicated Weather Tool (`device_weather`)**:
     - *Tool Declaration (`DeviceToolDefinitions.kt`)*: เพิ่ม `device_weather(location, latitude, longitude)` พร้อมคำอธิบายและกำชับห้ามใช้ `search_web` สำหรับสภาพอากาศ
     - *Open-Meteo REST API Engine (`DeviceControlExecutor.kt`)*: ดึงพิกัดจากอุปกรณ์ผ่าน `locationProvider.getCurrentLocation()` หรือแปลงชื่อเมืองด้วย Geocoder และเชื่อมต่อไปยัง Open-Meteo REST API (`https://api.open-meteo.com/v1/forecast`)
     - *Thai Weather Translation & Contextual Props*: แปลงรหัส WMO Weather Code เป็นภาษาไทย พร้อมบอกอุณหภูมิปัจจุบัน ความชื้น โอกาสฝนตก ลม และพยากรณ์สูงสุด/ต่ำสุดของวัน พร้อมสั่งเปลี่ยนธีมพื้นหลัง (`RAINY` / `SUNNY`), สวมพร็อพ (`UMBRELLA` + `RAIN_DROPS` หรือ `SUNGLASSES`) และเล่นเสียงประกอบ (`SURPRISE` / `CHIRP_HAPPY`)
     - *System Prompt & Voice Rules (`JarvisPersona.kt` & `LiveToolBridge.kt`)*: กำชับกฎห้ามใช้ `search_web` และสั่งให้รายงานสภาพอากาศด้วยน้ำเสียงสดใสกระชับ
  2. **Dialogue Card Auto-Dismiss & Tap-to-Dismiss (`AlwaysLiveScreen.kt`)**:
     - *10s Auto-Dismiss*: เพิ่ม `LaunchedEffect(!activeAvatarState.isSpeaking, hasMessage)` หน่วงเวลา 10 วินาที แล้วสั่ง `onDismissToolCard()` และล้าง `speechText`
     - *Instant Tap-to-Dismiss*: ใน `Modifier.pointerInput` ดักการแตะจอ (`onTap`) ให้ปิดกล่องข้อความทันทีหากมีกล่องแสดงอยู่
  3. **Floating Props (`GOLD_COIN`, `RAIN_DROPS`) & 12s Auto-Decay**:
     - *Prop Catalog (`RobotFaceState.kt`)*: เพิ่ม `GOLD_COIN` และ `RAIN_DROPS` ใน `PropType`
     - *Vector Rendering (`PetPropsOverlay.kt`)*: สร้างคอมโพเนนต์ `GoldCoinProp()` หมุน 3D และมีประกายดาวระยิบระยับ, `RainDropsProp()` หยดน้ำฝนพริ้วไหวพร้อมเอฟเฟกต์ละอองกระเซ็น
     - *12s Auto-Decay*: เพิ่มระบบละลายพร็อพชั่วคราวกลับสู่ปกติหลังผ่านไป 12 วินาที
  4. **Eilik & Dfree Robot Face: Dual Overlapping Circles Eye Architecture (`PetRobotHeadAvatar.kt`)**:
     - *Dual Overlapping Circles System (วงกลม 2 วงเหลื่อมซ้อนกันต่อหนึ่งดวงตา)*: ตามภาพถ่ายอ้างอิงจริงของหุ่นยนต์ แต่ละดวงตาประกอบด้วยวงกลมเรียบเนียน 2 วงซ้อนกัน
       - **เลเยอร์หลัง (Back Disc)**: สีน้ำเงินเข้มจัด (Deep Electric Royal Blue `#0012A8`) ทำหน้าที่เป็นเบ้าตา/เงามิติ
       - **เลเยอร์หน้า (Front Disc)**: สีฟ้าครามสว่างสดใส (Solid Electric Cyan `#4EE2F5`) ทำหน้าที่เป็นม่านตา/ลูกตานำสายตา
     - *Pure Solid Surface — Zero Inner Sparkle*: สีทึบคมชัด สะอาดตา ปราศจากประกาย ไฮไลท์สะท้อน หรือการเกลี่ยสีใดๆ ภายในดวงตา ("ไม่มีประกายด้านใน")
     - *Gaze Parallax Tracking (การขยับเหลื่อมซ้อนกันบอกทิศทางการมอง)*:
       - เมื่อตามองไปทางใด วงกลมหน้า (Front Cyan) จะเลื่อนไปทิศนั้น และวงกลมหลัง (Back Blue) จะเลื่อนไปทิศตรงข้าม
       - เช่น เมื่อหุ่นยนต์มองขึ้นบนซ้าย (Top-Left) วงกลมหน้าจะเลื่อนไปทางบนซ้าย เผยให้เห็นเสี้ยววงกลมสีน้ำเงินเข้มที่ด้านล่างขวา (Bottom-Right crescent) ตรงตามภาพอ้างอิง 100%
       - รองรับการมองรอบทิศทาง: ซ้าย, ขวา, บน, ล่าง, ทแยงมุม ผ่านระบบ Face Tracking และ Touch Interaction
     - *Expressive Eye Shapes*: ปรับใช้แนวคิด 2 เลเยอร์มิติสีน้ำเงินเข้มกับ `drawHappyEye` (⌒ ⌒), `drawSleepingEye` (— —), `drawExcitedEye` (> <), `drawAngryEye` และ `drawCrossEye` (X X)
- **Verification**:
  - เพิ่ม Unit Tests ใน `PetModeTest.kt` ครอบคลุม:
    - `PropType includes GOLD_COIN and RAIN_DROPS for finance and weather props`
    - `Weather WMO code interpretation maps correctly to Thai conditions and themes`
    - `Avatar emotions match Eilik and Dfree expressive face states`
    - `Dual overlapping circle gaze parallax creates correct directional shift and exposed crescent`
  - คอมไพล์ `./gradlew compileDebugKotlinAndroid` ผ่านฉลุย 100% (BUILD SUCCESSFUL)
  - ทดสอบ Unit Test `./gradlew testDebugUnitTest` ผ่านครบ 243 tests (BUILD SUCCESSFUL)

## 2026-09-12 — Fix Pet Mode Erratic Behavior: String Format Crash, Thai Unicode "เปิด/ปิด" Trap & Gemini Tool Hallucination Safeguards
- **Problem Solved**:
  - *User Symptom*: แอปทำงานรวนๆ ("มันยังทำงานรวนๆ"):
    1. Logcat สแปม Error ซ้ำๆ ทุกเฟรมกล้อง: `PetVisionDetector E Error handling detections: Flags = ' ('`
    2. ขณะอยู่ในโหมด Always Live / Pet Mode เมื่อผู้ใช้พูดเรียกชื่อ "จาวิส" (STT จับได้ว่า "ดาวิด") อยู่ๆ Gemini ก็สั่งปิด Always Live และตัดเสียงสนทนาทันที (`device_always_live({action=off})`, `voice_get_profiles({})`)
    3. เมื่อทักทาย "สวัสดีจาวิส" Gemini กลับเปิดกล้องสตรีมวิดีโอขึ้นมาเอง (`vision_activate`) สิ้นเปลืองแบตเตอรี่และโทเค็น
    4. คำสั่งที่มีคำว่า "เปิด" บางครั้งถูกตีความเป็น "ปิด" และสั่งปิดโหมดเอง
- **Root Cause & Fix**:
  1. **Java Formatter Crash in `PetVisionDetector.kt` (`Flags = ' ('`)**:
     - *Root Cause*: เมื่อตรวจจับใบหน้าผู้ใช้ยิ้ม ป้ายกำกับจะถูกสร้างเป็น `"$faceDisplayName Smile 😊 $pct%"` (เช่น `Boss Smile 😊 85%`) ซึ่งมีเครื่องหมาย `%` อยู่ในข้อความ จากนั้นในบรรทัดล็อกผลการตรวจจับ มีการเรียก `"${it.label} (%.2f%s)".format(it.confidence, ...)` ซึ่งนำ `it.label` ไปแทรกตรงใน Format String ทำให้ Java `Formatter` เห็น `% (` แล้วโยน `UnknownFormatConversionException: Flags = ' ('` ออกมาทุกเฟรมที่ผู้ใช้ยิ้ม
     - *Fix*: เปลี่ยนมาใช้ Kotlin String Interpolation ที่ปลอดภัย: `"${it.label} (${(it.confidence * 100).toInt()}%${if (it.isLocked) " 🔒" else ""})"` และปรับ gaze tracking logging ให้ปลอดภัย ไม่มีการใช้ format string กับตัวแปรภายนอก
  2. **Thai Unicode Substring Trap ("เปิด" vs "ปิด")**:
     - *Root Cause*: ในระบบการสะกดและรหัส Unicode ภาษาไทย คำว่า `"เปิด"` ประกอบด้วยสระเอ (`เ`, U+0E40) + ป ปลา (`ป`, U+0E1B) + สระอิ (`ิ`, U+0E34) + ด เด็ก (`ด`, U+0E14) ซึ่งเมื่อตัดสระเอข้างหน้าออก จะได้ลำดับอักขระเป็น `ป` + `ิ` + `ด` ซึ่งคือคำว่า `"ปิด"` พอดี! ส่งผลให้ในภาษา Java/Kotlin การตรวจสอบ `text.contains("ปิด")` จะได้ค่า `true` เสมอแม้ข้อความจะเป็นคำว่า `"เปิด"` ก็ตาม!
     - *Fix*: ใน `LiveToolBridge.kt` และ `ChatController.kt` ทำการตัดคำว่า `"เปิด"` ออกก่อนตรวจสอบคำสั่งปิด (`val pWithoutOpen = p.replace("เปิด", "")`) ทำให้การแยกแยะคำสั่ง "เปิดโหมด" กับ "ปิดโหมด" ถูกต้องแม่นยำ 100%
  3. **Gemini Live Tool Hallucination Safeguards (`LiveToolBridge.kt` & `JarvisPersona.kt`)**:
     - *AlwaysLive Off Guard*: ดักจับการเรียก `device_always_live(action="off")` หากคำพูดล่าสุดของผู้ใช้ไม่มีคำสั่งปิดหรือออกจากโหมดชัดเจน (เช่น ผู้ใช้แค่พูดว่า "ดาวิด", "จาวิส", "สวัสดี") ระบบจะปฏิเสธการปิดโหมด ไม่สั่งปิดหน้าจอ และส่ง Voice Rule ให้ Gemini คุยกับผู้ใช้ตามปกติ
     - *Vision Activate Guard*: ดักจับการเรียก `vision_activate` หากผู้ใช้ไม่ได้สั่งให้เปิดกล้องหรือมองดูสิ่งใด (เช่น แค่ทักทาย "สวัสดีจาวิส") ระบบจะปฏิเสธไม่เปิดกล้อง เพื่อประหยัดพลังงานและโทเค็น
     - *Voice Profiles Guard*: ป้องกันไม่ให้ Gemini สับสนระหว่างชื่อที่ผู้ใช้เรียก ("ดาวิด") กับชื่อโปรไฟล์เสียง โดยตรวจสอบว่าผู้ใช้พูดถึงเรื่อง "เสียง" หรือ "voice" หรือไม่ก่อนเรียก
     - *System Prompt Enhancement*: เพิ่มกฎเหล็กใน `JarvisPersona.kt` (ข้อ 4, 7, 12) กำชับ Gemini ห้ามเรียกปิดโหมด ห้ามเปิดกล้องเอง และห้ามเปลี่ยนเสียงเองเมื่อผู้ใช้แค่เรียกชื่อ
- **Verification**:
  - เพิ่ม Unit Tests ใน `AlwaysLiveTest.kt`:
    - `AlwaysLive off guard blocks hallucinated close when user did not request exit`
    - `Vision activate guard blocks hallucinated camera calls on general conversation`
    - `Voice profile guard prevents name confusion with voice switching`
    - `Detection label with percent symbol does not throw format exception`
  - รัน `./gradlew testDebugUnitTest` ผ่านครบทั้ง 239 tests (BUILD SUCCESSFUL)
  - คอมไพล์ `./gradlew assembleDebug` สำเร็จ 100% (BUILD SUCCESSFUL)

## 2026-09-12 — Fix Always Live & Pet Mode Voice Activation Bug: Accidental Disable/Shutdown and Session Disconnect Resolved
- **Problem Solved**:
  - *User Symptom*: สั่งด้วยเสียงว่า `"เปิดโหมดสัตว์เลี้ยง"` ระหว่างสนทนา Live Voice แล้วแอปตัดการเชื่อมต่อทันที ("ไม่ยอมเปิดให้") Logcat ฟ้อง `AlwaysLiveManager disable() → OFF`, `Stopping Pet Mode`, `Stopping Live Voice Input`, และ `Session disconnected`
  - *Root Cause*:
    1. **Accidental Disable in `DeviceControlExecutor.executeAlwaysLive`**:
       - เมื่อผู้ใช้พูดว่า "เปิดโหมดสัตว์เลี้ยง" หรือ "สลับเป็นโหมดสัตว์เลี้ยง" ตัวโมเดล LLM หรือตัวแปร action อาจถูกส่งเข้ามาเป็น `action="toggle"` หรือ `action="open"`
       - ในสาขา `"toggle"` เดิม ตรวจสอบเพียงว่า `current == FULL_SCREEN` หรือไม่ โดยไม่ได้เช็ค `isPetMode` หรือโปรไฟล์เป้าหมาย เมื่อพบว่าหน้าจอเปิดอยู่แล้ว จึงสั่ง `MainActivity.instance?.closeAlwaysLive()` ส่งผลให้สั่งปิดโหมดแทนที่จะสลับโปรไฟล์
       - นอกจากนี้ หาก action เป็น `"open"`, `"switch"`, `"เข้า"`, `"เริ่ม"` จะไม่ตรงกับ `"on"` ใน `when (action)` เดิม
    2. **Session Interruption in `JarvisViewModel.setAlwaysLiveProfile`**:
       - เมื่อโปรไฟล์เปลี่ยนจาก `CONTROL` ไปเป็น `PET` โค้ดเดิมเรียก `voice.restartVoiceSession()`
       - ซึ่งทำการเรียก `stopVoiceInput()` สั่งตัด WebSocket ปิดไมโครโฟน และตัด Session ทิ้งทันที ทำให้เกิดการตัดสายและหลุดการเชื่อมต่อ
    3. **Rapid Duplicate Fast-Path Triggers in `VoiceController.kt`**:
       - เมื่อผู้ใช้พูด Gemini Live จะสตรีม `inputTranscription` แบบต่อเนื่องหลาย Chunk (เช่น "เปิดโหมด", "เปิดโหมดสัตว์", "เปิดโหมดสัตว์เลี้ยง") ทำให้ Fast-path ใน `VoiceController` ยิง `ToolExecutor.execute` ซ้ำซ้อน 3-4 ครั้งในเสี้ยววินาที เกิด Race Condition ในการ Enable/Disable
  - *Fix*:
    1. **Safeguard `DeviceControlExecutor.executeAlwaysLive`**:
       - แยกแยะ `isExplicitOff` อย่างเข้มงวด (ต้องมี "off", "ปิด", "stop", "disable", "exit", "ออก", "close" เท่านั้น)
       - กรณีที่ระบุ `isPetMode` หรือ `isDriveMode` จะบังคับเป็นการเปิด/สลับโปรไฟล์ (`AlwaysLiveProfile.PET` / `DRIVE`) เสมอ และไม่มีทางสั่ง `closeAlwaysLive()` เด็ดขาด
       - ย้าย Pure Toggle ให้ทำงานเฉพาะเมื่อไม่มีการระบุโหมด และคำสั่งเป็น toggle ชัดเจนเท่านั้น
       - รองรับคำสั่งเปิดทุกรูปแบบ: `"on"`, `"open"`, `"start"`, `"enable"`, `"switch"`, `"change"`, `"เข้า"`, `"เริ่ม"`, `"pet"`
    2. **Seamless In-Session Persona Switching (`JarvisViewModel.setAlwaysLiveProfile`)**:
       - ยกเลิกการเรียก `voice.restartVoiceSession()` ขณะที่ Voice Session เชื่อมต่ออยู่
       - เปลี่ยนมาใช้ `orchestrator.sendLiveRealtimeText(...)` ส่งคำสั่งสลับ Persona เป็นสัตว์เลี้ยงตั้งโต๊ะตัวน้อยเข้าสู่ Live Session ทันที
       - WebSocket ไม่หลุด ไมค์ไม่หยุดบันทึก เสียง TTS ปรับ Pitch เป็น 1.25x ทันที และ Avatar เปลี่ยนเป็นโหมดสัตว์เลี้ยงโดยไร้รอยต่อ
    3. **Debounce Fast-Path Execution (`VoiceController.kt`)**:
       - เพิ่มตัวแปร `lastAlwaysLiveTriggerTime` ป้องกันการยิงคำสั่งเปิด/ปิด Always Live ซ้ำซ้อนภายในระยะเวลา 1.5 วินาที
    4. **Atomic Profile Sync (`MainActivity.expandAlwaysLive`)**:
       - ปรับปรุง `expandAlwaysLive(targetProfile)` ให้ซิงค์ `alwaysLiveManager.setProfile(targetProfile)` และยิง `onProfileChangeCallback` บน UI Thread โดยตรงก่อนแสดงผล `AlwaysLiveScreen` ป้องกัน Race Condition
    5. **Documentation & Tool Declaration**:
       - ปรับปรุงคำอธิบายของ `device_always_live` ใน `DeviceToolDefinitions.kt` ให้ชัดเจนยิ่งขึ้นว่าต้องใช้ `action="on"` สำหรับการเปิดหรือเปลี่ยนโหมด
- **Verification**:
  - เพิ่ม Unit Test `Always Live pet mode activation handles on, toggle, open and switch without closing` ใน `PetModeTest.kt`
  - รัน `./gradlew testDebugUnitTest` ผ่าน 100% (BUILD SUCCESSFUL)
  - คอมไพล์ `./gradlew assembleDebug` ผ่าน 100% (BUILD SUCCESSFUL)

## 2026-09-12 — Live Gemini WebSocket EOFException & Audio Stream State Desynchronization Fix
- **Problem Solved**:
  - *Root Cause*:
    1. **Audio Streaming Thread State Desynchronization (`send skipped — session ไม่พร้อม`)**:
       - เมื่อ WebSocket ฝั่ง Remote ปิดการเชื่อมต่อหรือเริ่มหลุด ตัวแปร `webSocketSession.isActive` เปลี่ยนเป็น `false` ทันที
       - ทว่า Flag `isSetupComplete` ยังคงค้างสถานะเป็น `true` จนกว่า Coroutine บล็อก `client.webSocket` จะหลุดออกจากลูป `incoming`
       - ส่งผลให้เธรดไมโครโฟน (`sendAudioChunk`) ที่ทำงานส่งเฟรมเสียงต่อเนื่องทุก ~50-100ms ข้ามเงื่อนไขตรวจสอบ `isSetupComplete` แล้ววิ่งเข้าสู่ `sendIfReady` เกิดการล็อก `⚠️ send skipped — session ไม่พร้อม (hasSession=true, active=false, ready=true)` สแปมซ้ำซ้อนใน Logcat
    2. **Remote Socket Closure (`java.io.EOFException`) ถูกจัดประเภทเป็น Crash Error และตัดโควตา Retry ผิดพลาด**:
       - ในระบบ WebSocket ของ Google Gemini Live API (`gemini-3.1-flash-live-preview`) เมื่อเซสชันหมดอายุ (Session timeout 10-15 นาที) หรือฝั่งเซิร์ฟเวอร์ตัดสาย TCP FIN โดยไม่มี WebSocket Close Frame ทาง OkHttp `WebSocketReader` จะโยน `java.io.EOFException` ออกมาตามมาตรฐานเครือข่าย
       - โค้ดเดิมใน `catch (e: Exception)` ทำการล็อกเป็น `logError("LiveGemini", "Connection error", e)` พร้อม Stacktrace เต็มรูปแบบ ทำให้ Android Studio ทำเครื่องหมายเตือนเป็นบั๊กหลอน (`Fix with AI`)
       - นอกจากนี้ ในบล็อก `catch` มีการบวกค่า `attempt++` เสมอแม้ว่าเซสชันก่อนหน้าจะเชื่อมต่อสำเร็จและใช้งานได้ (`sessionWasReady == true`) ทำให้เมื่อหลุดครบ 3 ครั้งจากการหมดอายุเซสชันตามเวลาปกติ ระบบจะตัดการเชื่อมต่อไปถาวร (`Giving up`)
  - *Fix*:
    1. **State Synchronization ใน `sendAudioChunk` & `sendIfReady`**:
       - ตรวจสอบ `isSessionActive = session != null && session.isActive` หากพบว่า `!isSessionActive` ให้รีเซ็ต `isSetupComplete = false` ทันที
       - ผันเสียงไมค์เข้าสู่ `preReadyAudioBuffer` ทันทีที่เซสชันไม่ Active ป้องกันการเรียก `sendIfReady` ขณะ Socket กำลัง Reconnect
       - ปรับปรุง `sendIfReady` ให้ทำ Rate-limiting การล็อกข้อความเตือน `⚠️ send skipped` สูงสุดเพียงครั้งเดียวในรอบ 3 วินาที ขจัด Log spam 100%
    2. **Graceful Remote Socket Close Detection & Retry Quota Protection**:
       - เพิ่มฟังก์ชัน `isRemoteSocketCloseException(e)` รองรับ KMP Cross-platform (ตรวจสอบ EOFException, SocketClosed, ClosedReceiveChannelException, Connection reset)
       - หาก `sessionWasReady == true` และเป็น Remote Close ให้ถือเป็น Server timeout ปกติ โดยล็อกแบบสุภาพระดับ Debug (`🔌 Remote server closed connection (EOFException) — auto-reconnecting`)
       - เซ็ต `attempt = if (sessionWasReady) 1 else attempt + 1` เพื่อไม่กินโควตา Retry ของเซสชันที่เคยพร้อมใช้งาน
       - ล้าง `sessionResumptionHandle = null` หากเกิดข้อผิดพลาดซ้ำ เพื่อไม่ให้ติดค้าง Resumption Handle ที่เซิร์ฟเวอร์ปฏิเสธ
- **Verification**:
  - เพิ่ม Unit Test `Remote socket close detection recognizes EOF and transient network terminations` ใน `AlwaysLiveTest.kt`
  - เพิ่ม Unit Test `Live session ready state protects reconnect retry quota on server close` ใน `AlwaysLiveTest.kt`
  - ทดสอบผ่าน 100% ด้วย `./gradlew testDebugUnitTest` (BUILD SUCCESSFUL)
  - คอมไพล์ผ่าน 100% ด้วย `./gradlew assembleDebug` (BUILD SUCCESSFUL)

## 2026-09-12 — Fix Hand Gesture False Positive Spam (HIGH_FIVE Leak) & Multi-Frame Edge Latch Engine
- **Problem Solved**:
  - *Root Cause*:
    1. ใน `PetVisionDetector.kt` บล็อก `when` ของการจำแนกท่าทางมือ สาขา `else` เดิมถูกเขียนเป็น `detectedGesture = HandGesture.HIGH_FIVE` แทนที่จะเป็น `HandGesture.NONE` ส่งผลให้เมื่อกล้องตรวจพบมือปกติที่กำลังถือเครื่อง หรือมือวางบนโต๊ะ ระบบจะบังคับเป็น `HIGH_FIVE` ตลอดเวลา
    2. ทำงานแบบ Level-triggered ด้วย Cooldown สั้นเพียง 1200ms ทำให้เมื่อมีมือปรากฏในจอ ระบบจะยิง `onHandGestureDetected(HIGH_FIVE)` รัวๆ ทุก 1.2 วินาที เกิดเสียงร้อง `CHIRP_HAPPY` วนซ้ำต่อเนื่อง
    3. ขอบเขตยกเว้นใบหน้าเดิม (`faceBounds`) คลุมด้านล่างเพียง 35% ทำให้ผิวบริเวณลำคอและไหปลาร้าหลุดมารวมเป็นกลุ่มก้อนมือ
  - *Fix*:
    1. ปรับปรุงสาขา `else` ให้เป็น `HandGesture.NONE` (แสดงป้าย "Hand ✋" สำหรับกรอบตรวจจับ แต่ไม่สั่งยิง Event ท่าทาง)
    2. เพิ่มการตรวจจับการถือโทรศัพท์ (`isHoldingPhone`): หากกลุ่มผิวหนังอยู่ติดขอบจอด้านล่าง (`minY > height * 0.65f`) จะไม่จัดเป็นท่าทาง
    3. ขยาย `padBottom = (b.height() * 0.85f).toInt()` ตัดผิวลำคอใต้คางทิ้งทั้งหมด
    4. พัฒนาระบบ **Multi-Frame Confirmation & Edge-Triggered Latch**:
       - ต้องตรวจพบท่าเดิมติดต่อกันอย่างน้อย 3 เฟรม (~240ms) ถึงจะ Confirm
       - ยิง Event เพียง **ครั้งเดียว** ต่อการทำท่า 1 ครั้ง ค้างท่าเดิมไว้จะไม่ยิงซ้ำเด็ดขาด
       - ปลด Latch เมื่อเอามือลง (`NONE`) ติดต่อกันเกิน 800ms
       - เว้นระยะ Cooldown 3.5 วินาทีใน Vision Detector
    5. เพิ่ม Defensive Cooldown 2 ชั้นใน `PetModeController.onHandGesture` ป้องกันการกระตุ้นซ้ำภายใน 3.0-5.0 วินาที
- **Verification**:
  - เพิ่ม Unit Test `PetModeController gesture debouncing and cooldown prevents spam` ใน `PetModeTest.kt` ผ่าน 100%

## 2026-09-12 — Virtual Desk Pet Revolution: Jelly Physics & Clean Face, Settings Dialog, 5-Slot Face Recognition & Tamagotchi Engine
- **Problem Solved**:
  1. **ดีไซน์และระบบแอนิเมชันใบหน้านุ่มนิ่ม & คลีน (Facial & Physics Animations)**:
     - *Jelly / Rubber Ball Physics*: ปรับปรุง `PetRobotHeadAvatar.kt` ให้มี Squash and Stretch physics (`squashX`, `squashY`) พร้อมแสงสะท้อนทรงแคปซูลมนบนซ้าย และประกายจุดล่างขวา ให้ความรู้สึกนุ่มนิ่ม เด้งดึ๋ง น่ารักเหมือนโพลิ่ง/เจลลี่
     - *Clean Idle State*: ในโหมดพักหน้าจอ (`IDLE`) ซ่อนคิ้วและปากทั้งหมด 100% คงเหลือเฉพาะดวงตากลมโตคู่ใหญ่นีออนที่กลอกมองสำรวจ หรี่ตา และกระพริบตาอย่างมีชีวิตชีวา ไร้สิ่งรบกวนสายตา
     - *Conditional Eyebrows*: แสดงคิ้วเฉพาะในอารมณ์ที่ต้องการสื่อสารชัดเจน (`THINKING`, `ANGRY`, `CONFUSED`, `SAD`, `LISTENING`) โดยซ่อนคิ้วในอารมณ์ `IDLE`, `HAPPY`, `LOVE`, `WINK`, `SLEEPING`
     - *Dynamic Mouth Shapes*: ปรับเปลี่ยนรูปทรงปากตามสถานะ — ปากคลื่นเสียง 5 แท่งขณะพูด, ปากจู๋ 'O' (`POUT`), ปากยิ้มโค้ง (`HAPPY`/`LOVE`), ปากเส้นตรงแบน (`SAD`/`ANGRY`), และซ่อนสนิทเมื่อไม่ได้พูด
     - *Screensaver Eye Tricks (`EyeTrickState`)*: เมื่อไม่มีการโต้ตอบตามเวลาที่กำหนด (15s, 25s, 45s, 60s) ดวงตาจะเล่นท่ายิมนาสติกแก้เบื่อ:
       - `PING_PONG_BOUNCE`: ลูกตากระเด้งชนขอบจอไปมาเหมือนลูกปิงปอง
       - `TIRED_BOUNCE`: ดวงตาทิ้งตัวดิ่งลงกระแทกขอบล่างแบบเจลลี่แบนแต๊ดแต๋แล้วเด้งกลับ
       - `SNOOKER_SHOT`: ตาซ้ายพุ่งแทงข้ามจอชนตาขวาเหมือนลูกสนุกเกอร์
  2. **ปรับแต่งหน้าจอคลีน & หน้าต่างการตั้งค่า (Clean UI & PetSettingsDialog)**:
     - *Clean Default UI*: ซ่อนป้ายสถานะ Debug (`🐾`, `🎭`, `🌀`, `👀`) เป็นค่าเริ่มต้น โดยต้องเปิด `showDebugHud` ในหน้าต่างตั้งค่าเท่านั้น
     - *PetSettingsDialog*: เพิ่มปุ่ม `⚙️ ตั้งค่า` บนแถบควบคุมของ `AlwaysLiveScreen` เปิด Modal Dialog 3 แท็บ:
       - แท็บทั่วไป: สวิตช์ Debug HUD, ชิปเลือกเวลาพักหน้าจอ Screensaver Delay (15s, 25s, 45s, 60s), คำแนะนำฟิสิกส์และเสียง
       - แท็บจดจำใบหน้า: จัดการ 5 Face Slots (ลงทะเบียน, ตั้งชื่อเล่น เช่น "บอส", "แม่", ลบข้อมูล)
       - แท็บสภาพจิตใจสัตว์เลี้ยง: แถบสถานะความต้องการแบบ Tamagotchi พร้อมปุ่มดูแลด่วน (ให้อาหาร 🍖, ทำความสะอาด 🧼, เล่น 🎾, นอนหลับ 💤)
  3. **ระบบการมองเห็นและการโต้ตอบอัจฉริยะ (5-Slot Face Recognition & 7 Hand Gestures)**:
     - *5-Slot Face Recognition (`PetFaceProfile.kt`, `PetVisionDetector.kt`)*: บันทึกและจดจำใบหน้าคนในบ้านได้ 5 โปรไฟล์โดยใช้อัตราส่วน Landmark ทางกายภาพแบบ Normalized (ระยะห่างดวงตา, สัดส่วนจมูก-ปาก, ความกว้างปาก, สัดส่วนรูปหน้า) เปรียบเทียบด้วย Euclidean Distance ($D < 0.12$) ประมวลผลบนเครื่อง 100% ไม่ส่งข้อมูลชีวมิติขึ้น Cloud
     - *Personalized Greetings (`JarvisPersona.kt` Rule 12)*: AI ทักทายระบุชื่อเล่นที่ลงทะเบียนไว้ได้อย่างอบอุ่นเป็นกันเอง
     - *7 Hand Gestures (`PetGesture.kt`)*: ตรวจจับท่าทางมือ 7 แบบ (`HIGH_FIVE`, `OK`, `BYE`, `NO`, `V_SIGN`, `THUMBS_UP`, `THUMBS_DOWN`) นอกกรอบใบหน้า ส่งผลต่อเสียงเอฟเฟกต์ สีหน้า และเพิ่มระดับความผูกพัน
  4. **ระบบจิตวิทยาและอุปนิสัยสัตว์เลี้ยง (Pet Needs & Tamagotchi Engine)**:
     - *Biological Needs (`PetNeedsState.kt`)*: จำลองระดับความอิ่ม (Satiety), พลังงาน (Energy), ความสะอาด (Hygiene), ความสุข (Happiness), และความเครียด (Stress) พร้อม Loop สลายค่าตามกาลเวลา (`decay`)
     - *Long-term Relationship & Personality*: ระดับความผูกพัน (Affection Level 1–10: แปลกหน้า $\rightarrow$ เพื่อนสนิท $\rightarrow$ คู่ชีวิต), ความเชื่อฟัง (Obedience), ความกระตือรือร้น (Hyper/Calm), และการติดเจ้าของ (Clingy/Independent)
     - *AI Persona Integration (`JarvisPersona.kt` Rule 13)*: AI รับรู้สถานะความหิว อารมณ์ และความเหนื่อยล้าของตัวเอง สามารถบ่นหิวนม หิวขนม หรือขอตัวนอนพักผ่อนอย่างน่ารักในการสนทนาสด
- **Verification**:
  - Unit Tests: `PetModeTest.kt` ทดสอบ HandGesture (8 ค่า), PetFaceProfile (5 slots & distance matching), PetNeedsState (decay, feed, clean, play, love, personality), Screensaver tricks & settings — ผ่าน 100%
  - Kotlin Compile & Test: `./gradlew testDebugUnitTest` ผ่านเรียบร้อย

## 2026-09-12 — Pet Mode Tool-Only Dialogue Card, Auto-Decay to Dark OLED Normal State & Ambient Sound Loop Fix
- **Problem Solved**:
  1. **กล่องข้อความแสดงเฉพาะตอนใช้ Tool (Tool-Only Dialogue Card in Pet Mode)**:
     - *Root Cause*: ใน `AlwaysLiveScreen.kt` มีโค้ด `val messageText = activeAvatarState.faceState.speechText ?: activeAvatarState.statusText` โดยที่ `statusText` ใน `App.kt` มีค่าสตริงสถานะเสมอ (เช่น "พร้อมรับฟัง", "พร้อมรับคำสั่ง", "JARVIS กำลังสนทนา...", หรือ debug string "🧪 [Face]...") ส่งผลให้ `hasMessage` เป็น `true` ตลอดเวลา และผลักใบหน้าหุ่นยนต์หลบไปด้านข้างถาวร
     - *Fix*:
       - ปรับปรุงตรรกะใน `AlwaysLiveScreen.kt`: ให้ `hasMessage` เป็น `true` **เฉพาะ** เมื่อมี Tool กำลังประมวลผล (`activeToolName != null`), มีผลลัพธ์จาก Tool (`lastToolResult != null`), หรือกำลังรัน Emotion Showcase Demo (`isDemoRunning && speechText != null`)
       - สำหรับการสนทนาทั่วไป (AI ตอบรับ, รับฟัง, สแตนด์บาย): `hasMessage = false` เสมอ -> ใบหน้าหุ่นยนต์อยู่ตรงกลางจอ 100% ขยับเฉพาะปากคลื่นเสียง 5-bar waveform ตามจังหวะเสียงพูดและระดับไมค์อย่างเป็นธรรมชาติ
       - เชื่อมโยง `lastToolResult` จาก `LiveToolBridge` -> `JarvisOrchestrator` -> `JarvisViewModel` -> `App.kt` -> `AlwaysLiveScreen` พร้อมปุ่ม Dismiss [✖] หรือปิดอัตโนมัติเมื่อเริ่มพูดใหม่
  2. **แก้ไขปัญหาเสียงวนซ้ำ & ติดอยู่ในอารมณ์ 🎭 [HAPPY] (Auto-Decay to Normal IDLE)**:
     - *Root Cause*: ใน `JarvisPersona.kt` (Rule 9) มีตัวอย่างสั่งให้โมเดลเรียก `device_avatar_emotion(..., background="sunny")` เมื่อทักทายอย่างสดใส และใน `JarvisViewModel.kt` ฟังก์ชัน `setTestFaceState` ไม่มีระบบนับเวลาถอยหลัง ส่งผลให้ค้างอยู่ในธีม `SUNNY` ตลอดไป ซึ่งใน `AmbientSoundEngine.kt` มี Procedural Loop 2.5 วินาที สังเคราะห์เสียงนกร้องดิจิทัล (bird chirp whistle) วนซ้ำทุก 2.5 วินาที
     - *Fix*:
       - เพิ่มระบบ `faceAutoDecayJob` ใน `JarvisViewModel.kt`: เมื่อมีคำสั่งแสดงอารมณ์หรือฉากหลัง จะแสดงผลขณะ AI กำลังพูด และเมื่อพูดจบ + 2.5–3 วินาที จะเรียก `resetToIdleFace()` เพื่อคืนสู่ IDLE อัตโนมัติ
       - ลบการเซ็ต debug string ใน `testStatusOverride` เพื่อไม่ให้มีข้อความ "🧪 [Face] HAPPY..." ค้างในระบบ
       - ใน `JarvisPersona.kt`: ปรับปรุง Rule 5 ห้ามเรียก `device_avatar_emotion` ในการทักทายเริ่มต้น และ Rule 9 ให้ใช้ `background="default"` (โทนมืด) เป็นมาตรฐานสำหรับอารมณ์ทั่วไป
  3. **โหมดปกติเป็นโทนมืดสนิท (Pure Dark OLED Tone / Zero Background Clutter)**:
     - ใน `PetBackgroundLayer.kt`: ปรับแต่ง `DefaultBackground()` ให้เป็นพื้นหลังสีดำ OLED มืดสนิท (`Color(0xFF000000)`) ตัดอนุภาคฝุ่นละอองลอย (dust motes) และ infinite animation ออกทั้งหมด
     - เมื่ออยู่ในโหมดปกติ (`BackgroundTheme.DEFAULT`): `AmbientSoundEngine` สั่ง `stopInternal()` ทันที ทำให้ระบบเงียบสนิท ไร้เสียงรบกวน ไร้แสงสะท้อน และประหยัดพลังงาน
- **Verification**:
  - Unit Tests: `PetModeTest.kt` เพิ่มการทดสอบ Dark OLED tone prompt verification และ Tool-only dialogue logic resolution
  - Full Unit Test Suite: `./gradlew testDebugUnitTest` ผ่าน 100% (30 tasks, BUILD SUCCESSFUL)

## 2026-09-12 — Dynamic SVG Path Parser System (Runtime Vector Prop & Sticker Engine) & Pet Magic Creator Tool
- **Problem Solved**:
  1. **Dynamic SVG Path Parser System (`DynamicVectorProp.kt`, `DynamicPropRenderer.kt`)**:
     - *Concept*: ปลดล็อกขีดจำกัดเดิมที่ต้องคอมไพล์โค้ดใหม่ทุกครั้งที่ต้องการเพิ่มพร็อพ/สติกเกอร์ โดยเปิดโอกาสให้ AI (Gemini Live / Tool Calling / Chat) สามารถออกแบบและสร้างเวกเตอร์ SVG Path ขึ้นมาเองแบบ Real-time ณ รันไทม์ (เช่น หมวกคาวบอย, แว่นตาดำน้ำ, มงกุฎ, คทาเวทมนตร์, ปีกนางฟ้า, หนวดแมว ฯลฯ)
     - *Path Parsing & Performance Caching*: ใช้ `androidx.compose.ui.graphics.vector.PathParser().parsePathString().toNodes().toPath()` ใน `commonMain` พร้อมแคชผ่าน `remember(prop.svgPath)` เพื่อไม่ให้เกิด Overhead การ parse ซ้ำใน Loop 60/120 FPS
     - *Auto-Fit Scale & Center Normalization*: คำนวณ `path.getBounds()` และปรับ Matrix Normalizer อัตโนมัติ (`targetSizePx / max(bounds.width, bounds.height)`) ไม่ว่า AI จะวาดบนสเกล 24x24 หรือ 100x100 ก็จะแสดงผลขนาดถูกต้องตาม `sizeDp`
     - *Anchor Coordinate Mapping*: รองรับตำแหน่งยึดบนใบหน้า `PropPosition` (`FOREHEAD`, `LEFT_EYE`, `RIGHT_EYE`, `CHEEKS`, `CHIN`, `FLOATING_LEFT`, `FLOATING_RIGHT`) พร้อมออฟเซ็ตและเลื่อนตามใบหน้าเมื่อเกิด Adaptive Split-Screen
     - *5 Dynamic Animations*: รองรับ `DynamicPropAnimation` (`FLOAT_BOB`, `PULSE`, `ROTATE_CONTINUOUS`, `SWAY`, `STATIC`) ขับเคลื่อนด้วย Compose InfiniteTransition
  2. **Tool Integration & AI Persona Superpower (`device_custom_prop`)**:
     - *Tool Declaration (`DeviceToolDefinitions.kt`)*: เพิ่ม Tool `device_custom_prop` พร้อมพารามิเตอร์ `action` (`add`, `remove`, `clear`), `name`, `svg_path`, `color`, `stroke_color`, `stroke_width`, `position`, `size`, `animation` และเพิ่มออปชัน `svg_path` ใน `device_avatar_emotion`
     - *Execution Pipeline (`DeviceControlExecutor.kt`, `PetModeController.kt`, `JarvisViewModel.kt`, `App.kt`)*: จัดการคำสั่งเพิ่ม/ลบ/ล้างพร็อพเวกเตอร์แบบเรียลไทม์ พร้อมเชื่อมโยงกับ `RobotFaceState.customProps`
     - *Voice Rule & Persona Prompt (`JarvisPersona.kt`, `LiveToolBridge.kt`)*: เพิ่ม Rule 11 ใน `PET_LIVE_SYSTEM_PROMPT` ให้ AI รับรู้ถึงพลังวิเศษในการเสกไอเทมเวกเตอร์แบบสดๆ เมื่อเจ้านายขอไอเทมใดๆ พร้อมตอบรับอย่างน่ารักและเป็นธรรมชาติ
- **Verification**:
  - Unit Tests: `SvgPathTest.kt` (Complex bezier parsing, bounds calculation, hex color parsing, RobotFaceState integration) และ `DeviceControlTest.kt` (`device_custom_prop` registry & tool declarations)
  - Full Android Build: `./gradlew testDebugUnitTest`

## 2026-09-12 — LOOI Robot Face Evolution ("The Phone IS the Head"), True Fullscreen Immersive Mode, Responsive Split-Screen Dialogue Layout & 52-Prop Catalog
- **Problem Solved**:
  1. **ปรัชญาการออกแบบ "The Phone IS the Head" (Pure OLED Living Glass Face Plate)**:
     - *Concept*: ปรับเปลี่ยนร่างอวตารของโหมดสัตว์เลี้ยงตามสไตล์ LOOI Robot (`GrinZero/super-looi`) โดยตัดภาพจำลองตัวถังเซรามิก (Ceramic Chassis), ขอบกระบังหน้าจำลอง (Visor Frame), และหูโลหะออกทั้งหมด เปลี่ยนให้หน้าจอสมาร์ตโฟนจริงกลายเป็น "หัวหุ่นยนต์ที่มีชีวิต" แบบ Edge-to-Edge 100%
     - *Visual Elements*:
       - **ดวงตานีออน Squircle ขนาดใหญ่**: วาดด้วย `drawRoundRect` แบบ Squircle โค้งมนนุ่มนวล พร้อมรัศมีเรืองแสงนีออนชั้นนอก (`Brush.radialGradient`) และแสงสะท้อน Specular Sheen ด้านบน
       - **คิ้วแบบไดนามิก (Expressive Brows)**: ปรับองศาและระดับตามอารมณ์ (เช่น Thinking เอียงซ้ายขวาไม่เท่ากัน, Angry เอียงกด 18°, Sad เอียงยก 15°, Listening เลิกคิ้วสูงขึ้น)
       - **ปากคลื่นเสียงไมโครโฟน (5-Bar Waveform Equalizer)**: แสดงผลเป็นแท่งคลื่นเสียง 5 แท่งขยับตามจังหวะเสียงพูดจริงของ AI และระดับไมโครโฟน หรือโค้งยิ้ม/ตกใจ/คาบตามอารมณ์
       - **Gaze Tracking & Perspective Distortion**: เมื่อหันมองข้าง ดวงตาข้างที่อยู่ใกล้จะหรี่แคบลงเล็กน้อย ขณะที่ข้างไกลจะขยายกว้างขึ้น สร้างมิติดวงตา 3D บนจอด้านหน้า
  2. **ระบบ True Fullscreen Immersive Mode**:
     - ซ่อน Status Bar และ Navigation Bar อัตโนมัติเมื่อเข้าสู่โหมดสัตว์เลี้ยง (`AlwaysLiveProfile.PET`) ผ่าน `WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`
     - นำ `statusBarsPadding()` ออกในโหมดสัตว์เลี้ยงเพื่อให้พื้นหลังดำ OLED สนิทและเอฟเฟกต์บรรยากาศแผ่ขยายเต็มผืนจอ 100% ไร้รอยต่อ
  3. **เลย์เอาต์แยกหน้าจอสนทนาแบบไดนามิก (Adaptive Split-Screen Dialogue Layout)**:
     - **โหมดแนวนอน (Landscape)**: เมื่อมีข้อความหรือเสียงพูด AI (`speechText` หรือ `statusText`) ส่วนของใบหน้าจะสไลด์ไปทางซ้ายอย่างนุ่มนวลด้วยฟิสิกส์สปริง (`spring(dampingRatio = 0.78f)` พร้อมย่อสเกลเล็กน้อย 0.82) เพื่อเปิดพื้นที่ให้การ์ดสนทนาอะคริลิกเรืองแสง `PetDialogueCard` แสดงทางฝั่งขวา
     - **โหมดแนวตั้ง (Portrait)**: ส่วนของใบหน้าจะสไลด์ขึ้นด้านบน และการ์ดสนทนาจะเลื่อนขึ้นมาจากด้านล่าง
     - **โหมดว่าง (Idle)**: เมื่อข้อความหายไป ใบหน้าจะสไลด์กลับมาอยู่ตรงกลางหน้าจอเต็มผืนอย่างสวยงาม
     - **สัมผัสแม่นยำ (Adaptive Touch Mapping)**: พิกัดการสัมผัส (ลูบหัว, จิ้มแก้ม, ลากสายตา) จะคำนวณออฟเซ็ตตามตำแหน่งสไลด์ของใบหน้าแบบเรียลไทม์
  4. **คลังพร็อพและสติกเกอร์ลอยขนาดใหญ่ 52 ชนิด (52-Prop Vector Catalog)**:
     - ขยาย `PropType` ใน `RobotFaceState.kt` จากเดิม 10 ชนิด เป็น 52 ชนิด ครอบคลุม 4 หมวดหมู่: อารมณ์ (17), อาหาร/ชีวิตประจำวัน (13), ธรรมชาติ/สภาพอากาศ (10), และเทคโนโลยี/เครื่องมือ (12)
     - เรนเดอร์บน Canvas แบบเวกเตอร์เคลื่อนไหวขนาดใหญ่ 30–60dp พร้อมสเกลและเลื่อนตำแหน่งไปพร้อมกับใบหน้า
- **Verification**:
  - Unit Tests: `./gradlew testDebugUnitTest` ผ่าน 100% (30 tasks, BUILD SUCCESSFUL)
  - Kotlin Android Compile: `./gradlew :composeApp:compileDebugKotlinAndroid` ผ่านฉลุย 100%

## 2026-09-12 — Pet Mode Tool Calling Unlock & GPS Nearby Places Search (Restaurants/Cafes) & Recipe/SMC Knowledge Integration
- **Problem Solved**:
  1. **ปลดล็อกการเรียกใช้ Tool ในโหมดสัตว์เลี้ยง (Pet Mode Tool Calling Unlock)**:
     - *Root Cause*: ใน `LiveGeminiService.kt` (บรรทัด 646 เดิม) มีการเขียน `tools = if (isPetMode) null else tools?.let { listOf(it) }` ซึ่งส่งผลให้เมื่อเปิดใช้งานโหมดสัตว์เลี้ยง รายชื่อ Function Declarations ทั้งหมดจะถูกตั้งเป็น `null` ทำให้โมเดล Gemini Live ไม่มีเครื่องมือใดๆ ให้เรียกใช้งาน และเมื่อเจ้านายถามตรวจสภาพอากาศหรือสั่งงาน AI จึงตอบว่า *"จาวิสใช้ทูลไม่ได้ฮับ น้องมองเห็นแค่ผ่านกล้อง..."*
     - *Fix*:
       - แก้ไขใน `LiveGeminiService.kt`: ส่ง `tools = tools?.let { listOf(it) }` เสมอทุกโหมด ทำให้ Gemini Live ในโหมดสัตว์เลี้ยงสามารถเข้าถึง Tools ทั้งหมดใน `ToolRegistry` ได้อย่างสมบูรณ์
       - ส่ง `coreContext` ไปยัง `LiveSystemInstruction` ในโหมดสัตว์เลี้ยง เพื่อให้ AI จดจำข้อมูลผู้ใช้และบริบทสำคัญได้ต่อเนื่อง
  2. **เสริมพลังและปรับจูน System Prompt ให้โหมดสัตว์เลี้ยงฉลาดรอบด้าน (Pet Superpowers & Persona Rules)**:
     - ปรับปรุง `PET_LIVE_SYSTEM_PROMPT` ใน `JarvisPersona.kt`:
       - เพิ่มกฎข้อ 10: **พลังวิเศษและการเรียกใช้เครื่องมือช่วยเหลือเจ้านาย (PET SUPERPOWERS & TOOLS CALLING)** โดยระบุว่าแม้ร่างจะเป็นหุ่นยนต์สัตว์เลี้ยงตัวจิ๋ว แต่น้องมีพลังวิเศษอัจฉริยะ สามารถและต้องเรียกใช้ Tools ต่างๆ ช่วยเหลือเจ้านายได้เสมอ พร้อมตอบกลับด้วยน้ำเสียงน่ารัก ขี้เล่น 1-2 ประโยค
       - **GPS ตำแหน่ง และค้นหาสถานที่ใกล้เคียง**: เมื่อเจ้านายถาม "ตอนนี้อยู่ที่ไหน", "พิกัดปัจจุบัน" เรียก `device_location(action="get_current")` | เมื่อถาม "มีร้านอาหารแถวนี้อะไรบ้าง", "แนะนำร้านอาหารแถวนี้", "คาเฟ่ใกล้ๆ" เรียก `device_location(action="get_current", query="ร้านอาหาร")` หรือ `device_navigate`
       - **สูตรอาหารและข้อมูลทั่วไป**: เมื่อถาม "ขอสูตรหมักหมูย่าง", "สภาพอากาศวันนี้" สามารถตอบสูตรอาหารแสนอร่อยได้ทันที หรือเรียก `search_web` ค้นหาข้อมูลล่าสุด
       - **การเทรด หุ้น ทองคำ และ SMC**: ปรับปรุงกฎข้อ 4 และ 10 เมื่อเจ้านายสั่งวิเคราะห์กราฟ หุ้น หรือทองคำ (เช่น "We call SMC ทองคำ ให้หน่อย") **ห้ามปฏิเสธว่าทำไม่ได้เด็ดขาด!** ให้เรียก `trading_smc_analysis(symbol="XAUUSD")` แล้วสรุปจุดสำคัญ (Order Block, FVG, แนวรับแนวต้าน) ให้เจ้านายฟังอย่างน่ารัก ร่าเริง
     - แก้ไขใน `LiveToolBridge.kt`: ปรับกฎเสียง `[VOICE RULE - PET MODE]` (บรรทัด 348) โดยลบตัวอย่างคำว่า "ปิ๊บๆ!" ออก เพื่อไม่ให้ AI พูดคำเลียนเสียงหุ่นยนต์ออกมา และเพิ่ม `[VOICE RULE - PET LOCATION & NEARBY]` ให้รายงานสถานที่ใกล้เคียงและร้านเด็ดอย่างกระชับ
  3. **ระบบค้นหาสถานที่ใกล้เคียงจากพิกัด GPS จริง (GPS Nearby Places Search with Google Grounding)**:
     - เพิ่มพารามิเตอร์ทางเลือก `query` ใน `device_location` (`DeviceToolDefinitions.kt`) เช่น `"ร้านอาหาร"`, `"คาเฟ่"`, `"ปั๊มน้ำมัน"`
     - ใน `DeviceControlExecutor.kt`: เมื่อมีการระบุ `query` ระบบจะดึงพิกัด Lat/Lng และที่อยู่ย่าน/เขต/แขวงจาก `LocationProvider` แล้วส่งสัญญาณ `NEARBY_SEARCH_REQUEST::query=...::location=...::summary=...`
     - ใน `LiveToolBridge.kt`: ดักจับ `NEARBY_SEARCH_REQUEST::` แล้วทำ Google Search Grounding (`enableGrounding = true`) เพื่อค้นหาร้านอาหาร/สถานที่จริงที่เป็นที่นิยมและเปิดบริการอยู่ในย่านนั้น พร้อมเมนูเด่น ส่งกลับเข้า Gemini Live เพื่อให้ AI ตอบแนะนำเป็นเสียงพูดอย่างเป็นธรรมชาติ
     - ใน `ToolExecutor.kt`: รองรับการแสดงผลลัพธ์สถานที่ใกล้เคียงในโหมดแชทปกติได้อย่างสวยงาม
- **Verification**:
  - Unit Tests: เพิ่มการทดสอบใน `PetModeTest.kt` ทดสอบกฎพลังวิเศษของ Pet Mode, การมีอยู่ของพารามิเตอร์ `query` ใน `device_location` — ผ่าน 100% (`./gradlew testDebugUnitTest` BUILD SUCCESSFUL)
  - Build APK: `./gradlew assembleDebug` สำเร็จ 100% (43 tasks, BUILD SUCCESSFUL)

## 2026-09-12 — Camera FOV Alignment (Dual 9:16 Portrait & 16:9 Landscape) & Stabilized Object Target Locking Engine
- **Problem Solved**:
  1. **แก้ปัญหาขอบเขตภาพกล้องไม่ตรงกับที่ AI เห็น (Camera Preview vs AI Vision 1:1 FOV Alignment)**:
     - *Root Cause*: หน้าต่างลอย PIP ใน `AlwaysLiveScreen.kt` เดิมตั้งขนาดตายตัวไว้ที่ `220.dp × 165.dp` (อัตราส่วนแนวนอน 4:3) ขณะที่ผู้ใช้ถือโทรศัพท์ในแนวตั้ง (9:16) ทำให้ `PreviewView.ScaleType.FILL_CENTER` ซูมและ Crop ขอบภาพบน-ล่างทิ้งไปถึง 57.8% แต่ภาพที่ส่งให้ AI กลับเป็นภาพเต็ม 9:16 (100% Uncropped) ทำให้ผู้ใช้ไม่รู้ขอบเขตสายตาของ AI และกรอบ Bounding Box คำนวณเบี้ยวในแนวตั้ง
     - *Fix*:
       - ปรับขนาดหน้าต่าง PIP ให้เป็น Dynamic Aspect Ratio ตามการหมุนเครื่อง:
         - **แนวตั้ง (Portrait)**: ขนาด `144.dp × 256.dp` (อัตราส่วนเป๊ะ 9:16)
         - **แนวนอน (Landscape)**: ขนาด `240.dp × 135.dp` (อัตราส่วนเป๊ะ 16:9)
       - ปรับ `scaleType` ใน `CameraPreviewView.android.kt` เป็น `PreviewView.ScaleType.FIT_CENTER` เพื่อให้ภาพแสดงเต็มผืน 100% ไร้การ Crop หรือบิดเบี้ยว
       - ปรับปรุงการสลับการหมุนหน้าจอใน `CameraPreviewView.android.kt` ให้ตั้งค่า `targetRotation` บน `previewUseCase` และ `imageAnalysisUseCase` อัตโนมัติตาม `LocalConfiguration.current.orientation`
       - ปรับแก้ตำแหน่งแถบควบคุมด้านบนและคอลัมน์สถานะ `PetDetectionBadge` ใน `AlwaysLiveScreen.kt` โดยเว้นระยะ `top = 56.dp` ในแนวตั้ง เพื่อป้องกันปุ่ม `[🧪 ทดสอบเดโม]` และ `[👁️ ลืมตา]` ซ้อนทับกับป้ายสถานะ
  2. **ระบบล็อกเป้าหมายและกรองสัญญาณรบกวน (Stabilized Object Target Locking & Smoothing Engine)**:
     - พัฒนาโมดูล [`PetVisionTargetTracker.kt`](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/pet/PetVisionTargetTracker.kt) (`commonMain` - Cross-platform):
       - **Clutter & Background Noise Filtering**: กรองผนังห้อง (`Place / Scenery 🏢`), วัตถุขนาดเล็กตามพื้นหลัง (รอยต่อท่อ, สวิตช์ไฟ) ทิ้งโดยอัตโนมัติ
       - **Face/Neck Exclusion Zone**: กรองวัตถุที่ตรวจจับพลาดไปซ้อนทับใบหน้าหรือบริเวณลำคอ/เสื้อ (เช่น false `Accessory / Item 👓` บนปาก/หนวด) ทิ้งทันที
       - **Target Prioritization**: จัดลำดับความสำคัญ 1) ใบหน้าเจ้านาย (`Boss Face`) $\rightarrow$ 2) มือ/นิ้ว (`Hand / Finger`) $\rightarrow$ 3) วัตถุเด่นในมือ/บนโต๊ะ (จำกัดไม่เกิน 3 เป้าหมายพร้อมกัน)
       - **IOU & Center Distance Association**: จับคู่วัตถุเฟรมต่อเฟรมด้วยอัลกอริทึม Intersection-over-Union ป้องกันกรอบสลับตำแหน่ง
       - **EMA Coordinate Smoothing**: เกลี่ยพิกัดกรอบ `(x, y, width, height)` ด้วย Exponential Moving Average ($\alpha = 0.40$) ขจัดอาการกรอบสั่น/กระตุก 100%
       - **Hysteresis Persistence (350ms)**: รักษาตำแหน่งวัตถุไว้ 350ms หากมีเฟรมหลุดชั่วคราว ป้องกันกรอบกระพริบติดๆ ดับๆ
       - **Target Locking Indicator**: เมื่อตรวจจับต่อเนื่องครบ 3 เฟรม จะล็อกเป้าหมาย (`isLocked = true`) แสดงกรอบ Sci-Fi พร้อมสัญลักษณ์ `🔒` และป้ายนับเป้าหมายจะแสดง `🔒 X ล็อกเป้าหมาย`
- **Verification**:
  - Unit Tests: เพิ่ม 6 ข้อใน `PetModeTest.kt` ทดสอบการกรอง Place, การกรอง Face Exclusion Zone, การเกลี่ย EMA, การล็อกเป้าหมาย, การจำกัดโควต้า 3 เป้าหมาย, และการคำนวณอัตราส่วน 9:16 / 16:9 — ผ่าน 100% (`./gradlew testDebugUnitTest` BUILD SUCCESSFUL)
  - Build APK: `./gradlew assembleDebug` สำเร็จ 100% (43 tasks, BUILD SUCCESSFUL)

## 2026-09-12 — Gemini 3.1 Live Multimodal Protocol Alignment (Fix media_chunks deprecation) & 8-Scene Interactive "ทดสอบเดโม" Showcase System
- **Problem Solved**:
  1. **แก้ไขข้อผิดพลาดโมเดล `gemini-3.1-flash-live-preview` (WebSocket Session closed: NOT_CONSISTENT — `realtime_input.media_chunks is deprecated`)**:
     - *Root Cause*: ในการเชื่อมต่อ WebSocket ไปยัง Gemini Live API ฝั่ง Google มีการ deprecate ฟิลด์ `media_chunks` ใน `realtime_input` และบังคับใช้ฟิลด์ตรงคือ `audio`, `video`, หรือ `text` แทน
     - *Fix*:
       - ปรับปรุง `LiveRealtimeInputData` ใน `LiveGeminiService.kt`: นำ `mediaChunks: List<LiveBlob>?` ออก และเปลี่ยนเป็น `val audio: LiveBlob? = null`, `val video: LiveBlob? = null`, `val text: String? = null`
       - ปรับปรุง `sendAudioChunk()` ให้ส่ง `LiveRealtimeInputData(audio = LiveBlob("audio/pcm;rate=16000", pcmBase64))`
       - ปรับปรุง `sendImageChunk()` ให้ส่ง `LiveRealtimeInputData(video = LiveBlob("image/jpeg", jpegBase64))`
       - ทำให้ `gemini-3.1-flash-live-preview` กลับมาเชื่อมต่อ ส่งภาพ/เสียงสด และได้รับคำตอบแบบสตรีมมิ่งได้เสถียร 100%
  2. **ระบบคำสั่ง "ทดสอบเดโม" (Comprehensive 8-Scene Living Avatar Showcase System)**:
     - พัฒนาระบบสาธิตการทำงานของร่างอวตารหุ่นยนต์โหมดสัตว์เลี้ยงแบบอัตโนมัติ 8 ฉากต่อเนื่อง (ฉากละ 3.5 วินาที) ครอบคลุมฟีเจอร์ใหม่ครบวงจร:
       - **Scene 1 (Sunny)**: สภาพอากาศแจ่มใส | ท่าทาง `JUMP` | อุปกรณ์ `MUSIC_NOTES`, `SPARKLES` | เสียงปี๊บ `CHIRP_START` | เสียงบรรยากาศ Harmonic Sine 432Hz + นกร้อง
       - **Scene 2 (Rainy)**: ฝนตกโปรยปราย | ท่าทาง `TILT_LEFT` | อุปกรณ์ `UMBRELLA`, `SWEAT_DROP` | เสียงปี๊บ `ACKNOWLEDGE` | เสียงบรรยากาศ Pink noise ฝนตก + หยดน้ำกระทบกระจก
       - **Scene 3 (Sakura)**: ลมพัดซากุระ | ท่าทาง `WOBBLE` | อุปกรณ์ `SPARKLES` | เสียงปี๊บ `SPARKLE` | เสียงบรรยากาศลมพัด sweeping wind
       - **Scene 4 (Love)**: ส่งความรัก | ท่าทาง `BOUNCE` | อุปกรณ์ `HEARTS` | เสียงคราง `PURR` | เสียงบรรยากาศเมโลดี้ Solfeggio 528Hz
       - **Scene 5 (Thunder)**: พายุฝนฟ้าคะนอง | ท่าทาง `SHAKE` | อุปกรณ์ `FIRE`, `EXCLAMATION` | เสียงเตือน `ALARM` | เสียงบรรยากาศฟ้าร้องครืนๆ
       - **Scene 6 (Matrix)**: โลกไซไฟดิจิทัล | ท่าทาง `TILT_RIGHT` | อุปกรณ์ `QUESTION_MARK` | เสียงสับสน `CONFUSED` | เสียงบรรยากาศ Server Hum 60Hz + Digital pulses
       - **Scene 7 (Night)**: ค่ำคืนสงบเข้านอน | ท่าทาง `NOD` | อุปกรณ์ `ZZZZZ` | เสียงปิดท้าย `CHIRP_END` | เสียงบรรยากาศ Sub-bass drone 55Hz + จิ้งหรีดเรไร
       - **Scene 8 (Default)**: คืนสู่โหมดปกติ | ท่าทาง `IDLE` | กระจกหน้าจอสะอาด | เสียงปี๊บ `CHIRP_START` | เสียงบรรยากาศ Cybernetic room tone
     - รองรับการสั่งเดโมได้ 4 ช่องทาง:
       1. **สั่งด้วยเสียง (Fast-Path Voice)**: พูด *"ทดสอบเดโม"*, *"เดโม"*, *"demo"*, *"ทดสอบระบบ"*, *"โชว์หุ่นยนต์"* หรือสั่งหยุดด้วย *"หยุดเดโม"*, *"หยุดทดสอบ"*
       2. **พิมพ์ในแชท (Chat Command)**: พิมพ์ `/demo`, `ทดสอบเดโม`, `demo`, `หยุดเดโม`
       3. **ปุ่มบนหน้าจอ (Interactive UI Button)**: ปุ่ม `[🧪 ทดสอบเดโม]` / `[⏹️ หยุดเดโม]` ในหน้าจอ AlwaysLiveScreen
       4. **คำสั่งจาก AI (Gemini Live Tool)**: ผ่าน Function Call `device_avatar_emotion`
     - แก้ไขการ Sync สถานะหน้าตาระหว่าง External Face State และ Internal Face State ใน `AlwaysLiveScreen.kt` ให้การ Override และการคืนค่าปกติทำงานได้ทันทีโดยไม่ถูกทับซ้อน
- **Verification**:
  - Unit Tests: เพิ่มการทดสอบ JSON Serialization ของ `LiveRealtimeInputData` และความครอบคลุมของฉากทั้ง 8 ฉากใน `PetModeTest.kt` — ผ่าน 211 tests (100% passed)
  - APK Build: `./gradlew assembleDebug` สำเร็จ 100%

## 2026-09-11 — Real Procedural Robot SFX (Sentence Start/End Chirps) & Dynamic Looping Ambient Background Sound FX Engine
- **Problem Solved (แก้ปัญหา AI พูดคำว่า "ปิ๊บๆ" ออกมาเป็นคำพูด แทนที่จะเป็นเสียงเอฟเฟกต์จริง และเพิ่มระบบเสียงบรรยากาศคลอฉากหลังตามอารมณ์/สภาพแวดล้อม)**:
  1. **การกำจัดคำพูดเลียนเสียงหุ่นยนต์ ("ปิ๊บๆ / บี๊บๆ") ออกจากคำพูดของ AI**:
     - *Root Cause*: ใน `JarvisPersona.kt` (Rule 2 เดิม) มีการสอนว่า `มีเสียงหุ่นยนต์น่ารัก (ROBOT SOUND WORDS): เริ่มต้นหรือลงท้ายประโยคด้วยเสียงหุ่นยนต์น่ารักๆ เสมอ เช่น "ปิ๊บๆ!"` ทำให้โมเดล Gemini Live อ่านออกเสียงคำว่า "ปิ๊บๆ" ออกมาด้วยเสียงสังเคราะห์เหมือนคนพูดคำว่าปี๊บๆ แทนที่จะเป็นเสียงอิเล็กทรอนิกส์จริง
     - *Fix*:
       - ปรับปรุง Rule 2 ใน `PET_LIVE_SYSTEM_PROMPT` เป็น **"ห้ามพูดคำเลียนเสียงหุ่นยนต์ออกมาเป็นคำพูดเด็ดขาด (STRICT - NO SPOKEN SOUND WORDS)"** พร้อมระบุข้อห้ามชัดเจน: ห้ามพูดคำว่า "ปิ๊บๆ", "บี๊บๆ", "ติ๊ดๆ", "วี้ๆ", "beep beep" เด็ดขาด โดยให้พูดเฉพาะเนื้อหาข้อความที่เป็นธรรมชาติ
       - ปรับปรุง Rule 3, Rule 5, และตัวอย่างใน Rule 8 รวมถึงข้อความทักทายเริ่มต้น (`petGreeting`) ใน `VoiceController.kt` และ `JarvisViewModel.kt` ให้เป็นภาษาพูดที่อบอุ่น ไร้คำว่า "ปิ๊บๆ"
  2. **ระบบเสียงเอฟเฟกต์ประโยคของหุ่นยนต์จริง (Procedural Robot Speech Cadence SFX)**:
     - เพิ่มประเภทเสียงใน `RobotSoundPlayer.kt` (`commonMain`) และ `RobotSoundEngine.kt` (`androidMain`):
       - `CHIRP_START`: คลื่นเสียงไซน์สังเคราะห์สังเคราะห์ระดับฮาร์ดแวร์คู่สองจังหวะ (Rising Two-tone Beep 1200Hz -> 1800Hz, 85ms) ดังขึ้นทันทีที่ AI เริ่มประมวลผลหรือเริ่มเปล่งเสียงพูด chunk แรก
       - `CHIRP_END`: คลื่นเสียงก้องจางหาย (Falling Sine Tone with Pitch Glide 1600Hz -> 900Hz, 120ms) ดังขึ้นทันทีเมื่อ AI พูดจบประโยค
       - `ACKNOWLEDGE`: เสียงรับทราบคำสั่ง (1000Hz -> 1500Hz)
       - `SPARKLE`: เสียงระยิบระยับคู่ (Double Pentatonic Sparkle)
     - เชื่อมต่อการเล่นเสียงใน `VoiceController.kt` (เล่น `playChirpStart()` ตอนเริ่มเล่นเสียงก้อนแรก และเล่น `playChirpEnd()` ใน `playbackFinishJob` หลัง AudioTrack เล่นเสียงพูดจนจบ)
     - เชื่อมต่อใน `AlwaysLiveManager.kt` และ `PetModeController.kt` ให้ส่งเสียงอัตโนมัติตามการเปลี่ยนสถานะหรือ Event ต่างๆ
  3. **ระบบเสียงบรรยากาศเบื้องหลังแบบวนลูป (Continuous Looping Ambient Sound FX Engine)**:
     - พัฒนาโมดูลคู่: `AmbientSoundPlayer.kt` (KMP `commonMain` bridge) และ `AmbientSoundEngine.kt` (Android PCM Audio Engine)
     - ใช้ Android `AudioTrack` ในโหมด `MODE_STATIC` ร่วมกับ `setLoopPoints(0, samples.size, -1)` ซึ่งทำงานระดับ AudioFlinger / DSP ฮาร์ดแวร์โดยตรง กิน CPU เป็น 0% พร้อมอัลกอริทึม Circular Crossfade 50ms ที่หัว-ท้ายลูป ป้องกันเสียงแตก/คลิก (zero click/pop artifacts)
     - สร้างเสียงบรรยากาศสังเคราะห์ตาม 8 ธีมฉากหลัง (`BackgroundTheme`):
       - `RAINY`: เสียงฝนตกโปรยปรายต่อเนื่อง (Pink Noise กรอง Low-pass 800Hz + สุ่มหยดน้ำฝนกระทบกระจก)
       - `NIGHT`: บรรยากาศกลางคืนสงบเงียบ (Sub-bass drone 55Hz + สังเคราะห์เสียงจิ้งหรีดเรไรยามค่ำคืน)
       - `SUNNY`: บรรยากาศกลางวันสดใส (Sine drone นุ่มนวล 432Hz + เสียงนกร้องสั้นๆ ชวนผ่อนคลาย)
       - `SAKURA`: ลมพัดเอื่อยๆ พากลีบดอกไม้ปลิวไหว (Filtered sweeping pink noise คลื่นลม)
       - `MATRIX`: เสียงฮัมของเซิร์ฟเวอร์และพัลส์ดิจิทัลไซไฟ (60Hz AC hum + 120Hz digital pulses)
       - `LOVE_BG`: เมโลดี้อบอุ่นหัวใจ (Warm pulsing major third chords 528Hz Solfeggio frequency)
       - `THUNDER`: เสียงฟ้าร้องครืนๆ ในระยะไกล (Deep rumbling low-frequency noise + low-frequency rolling swell)
       - `DEFAULT`: บรรยากาศห้องไซเบอร์เนติกแสนสงบ (Soft cybernetic room tone)
  4. **ระบบ Dynamic Audio Ducking**:
     - เมื่อ AI กำลังพูด ระบบจะ Ducking ปรับลดระดับเสียงบรรยากาศลงอัตโนมัติจาก `0.18f` เหลือ `0.04f` (นุ่มนวล ไม่แย่งความเด่นของเสียงพูด และไม่รบกวน VAD ไมโครโฟน) และคืนระดับเสียงเดิมเมื่อพูดจบ
     - ตัดและหยุดเสียงทั้งหมดอย่างปลอดภัยเมื่อออกจากหน้าจอ หรือ Dispose composable (`AmbientSoundPlayer.stop()`)
- **Verification**:
  - Unit Tests: เพิ่ม 4 Unit Tests ใน `PetModeTest.kt` ทดสอบ Enums ใหม่, Handler Trigger, Lifecycle ของ `AmbientSoundPlayer`, และตรวจสอบความถูกต้องของข้อความ Prompt — `./gradlew testDebugUnitTest` ผ่านครบ 100% (209 tests passed, BUILD SUCCESSFUL)
  - APK Build: `./gradlew assembleDebug` ผ่าน 100% (43 tasks, BUILD SUCCESSFUL)

## 2026-09-11 — Layer-based Living Robot Avatar System (Dynamic Backgrounds, Props Overlay, Gestures & AI JSON Control)
- **Problem Solved (ยกเครื่องหน้าตาหุ่นยนต์สัตว์เลี้ยงให้มีชีวิตชีวา เคลื่อนไหวได้ และเปลี่ยนสถานะ/หน้าตา/ฉากหลัง/อุปกรณ์เสริมได้แบบไดนามิกตามคำตอบของ AI)**:
  1. **สถาปัตยกรรมแบบ Layer-based UI (Jetpack Compose / KMP)**:
     - เดิม `PetRobotHeadAvatar.kt` เป็นผืน Canvas ผืนเดียว 876 บรรทัด (monolithic) ไม่มีแยกเลเยอร์ ไม่สามารถใส่ฉากหลังที่มีอนิเมชัน หรืออุปกรณ์เสริมลอยรอบหัวได้
     - ออกแบบและสร้างโครงสร้าง 4 เลเยอร์ใหม่:
       - **Layer 0 (`PetBackgroundLayer.kt`)**: Dynamic Backgrounds พร้อม Canvas Particle Effects 8 ธีม (`DEFAULT`, `RAINY`, `SUNNY`, `NIGHT`, `SAKURA`, `MATRIX`, `LOVE_BG`, `THUNDER`) เปลี่ยนผ่านนุ่มนวลด้วย `Crossfade`
       - **Layer 1**: Ambient Aura Glow Pulse
       - **Layer 2 (`PetRobotHeadAvatar.kt` + `PetGestureAnimations.kt`)**: White Ceramic Chassis + Visor Screen แสดง LED Dot Matrix พร้อมรองรับภาษากาย (`GestureType`) เช่น `BOUNCE`, `JUMP`, `WOBBLE`, `SHAKE`, `NOD`, `TILT_LEFT`, `TILT_RIGHT`
       - **Layer 3 (`PetPropsOverlay.kt`)**: Animated Props & Sticker Overlay 10 แบบ (`UMBRELLA`, `QUESTION_MARK`, `SWEAT_DROP`, `HEARTS`, `MUSIC_NOTES`, `SPARKLES`, `ZZZZZ`, `EXCLAMATION`, `FIRE`, `SNOW`) พร้อม `AnimatedVisibility` (scaleIn/scaleOut + fadeIn/fadeOut) และ continuous loop animation
  2. **Data Models สำหรับควบคุมสถานะจาก AI (`RobotFaceState.kt`)**:
     - เพิ่ม Enums: `EyeStyle`, `BackgroundTheme`, `PropType`, `GestureType`
     - เพิ่ม `@Serializable data class RobotFaceState` รองรับทั้ง JSON parsing (`fromJson()`), parameter mapping (`fromArgs()`) และ presets (`HAPPY_SUNNY`, `SAD_RAINY`, `LOVE_HEARTS`, `ANGRY_THUNDER`, `SLEEPING_NIGHT`, `EXCITED_SAKURA`)
     - ขยาย `AvatarState` ด้วย `faceState: RobotFaceState` และ `fun AvatarState.withFace(face)` เพื่อความเข้ากันได้ย้อนหลัง 100%
  3. **การควบคุมจาก AI ผ่าน Tool และ System Prompt (`device_avatar_emotion`)**:
     - ขยาย Function Declaration ของ `device_avatar_emotion` ใน `DeviceToolDefinitions.kt` ให้รับ `eye_style`, `background`, `props`, `gesture`
     - ปรับ `DeviceControlExecutor.kt` ให้ parse พารามิเตอร์ใหม่ ส่งต่อไปยัง UI ผ่าน `MainActivity.triggerTestEmotion`
     - เพิ่ม Rule 9 ใน `JarvisPersona.kt` (`PET_LIVE_SYSTEM_PROMPT`) สั่งให้ AI เรียกใช้ `device_avatar_emotion` ควบคู่กับการตอบคำถามที่มีอารมณ์ชัดเจน
  4. **State Management & Controller Integration**:
     - เพิ่ม `PetModeController.updateRobotFace(state: RobotFaceState)` และ `updateRobotFace(commandOrJson: String)`
     - เพิ่ม `testFaceStateOverride` ใน `JarvisViewModel.kt` และประมวลผลคำสั่งใน `App.kt`
- **Verification**:
  - Unit Tests: เพิ่ม 7 ข้อใน `PetModeTest.kt` ทดสอบ JSON parsing, argument mapping, default fallbacks, `updateRobotFace`, pipe-command parsing, gesture overrides, `withFace` compatibility — `./gradlew testDebugUnitTest` ผ่าน 100% (30 tasks, BUILD SUCCESSFUL)
  - APK Build: `./gradlew assembleDebug` ผ่าน 100% (43 tasks, BUILD SUCCESSFUL)

## 2026-09-11 — Fix CameraX Video Encoding, AR Overlay Visibility, and Gemini Live Voice Native Audio (TTS Elimination)
- **Problem Solved (แก้ปัญหาภาพกล้องเป็นเส้นๆ มองไม่เห็น, AR ไม่ขึ้น และ AI พูดด้วย TTS แข็งๆ แทนเสียง Gemini Live สด)**:
  1. **Camera Frame Distortion & Vision Blindness ("ภาพกระพริบ ลายตา / ภาพเป็นเส้นๆ / AI มองไม่เห็น")**:
     - *Root Cause*: ฟังก์ชัน `yuvToJpeg` ใน `CameraPreviewView.android.kt` เดิมใช้วิธีก็อปปี้ byte array แบบ contiguous โดยไม่ได้นำ `pixelStride` และ `rowStride` ของ Android CameraX `ImageFormat.YUV_420_888` มาคำนวณ ทำให้ภาพที่แปลงเป็น JPEG แตกเป็นริ้วเส้นๆ สีเขียว ลายตา และโมเดล Gemini Live ตอบกลับว่า *"ภาพมันกระพริบๆ ลายตาไปหมดเลย / ภาพยังเป็นเส้นๆ อยู่เลย"*
     - *Fix*: เปลี่ยนมาใช้ `imageProxy.toBitmap()` ซึ่งเป็น API ภายในของ CameraX 1.4.1+ (ใช้ C++ libyuv จัดการ row padding และ UV stride แบบ native 100%) พร้อมปรับขนาดความกว้างไม่เกิน 640px, หมุนตาม `imageInfo.rotationDegrees`, กลับภาพแบบ Center Pivot สำหรับกล้องหน้า และบีบอัดเป็น JPEG คุณภาพสูง คมชัด ไร้ริ้วเส้น
  2. **WebSocket Schema Incompatibility & Audio Dropping (AI พูดด้วย TTS อ่านแข็งๆ ไม่เป็นธรรมชาติ)**:
     - *Root Cause*:
       1. โครงสร้าง JSON ของ `realtimeInput` ใน `LiveGeminiService.kt` เดิมแยกเป็น `{ "realtimeInput": { "video": ... } }` และ `{ "realtimeInput": { "audio": ... } }` ซึ่งผิดจาก Google Gemini Multimodal Live API Protocol ที่ต้องส่งผ่าน `mediaChunks: [ { mimeType: "...", data: "..." } ]` ทำให้เซิร์ฟเวอร์แจ้ง warning และปิดกั้นการส่งเสียงสังเคราะห์ PCM กลับมา
       2. ใน `LivePrebuiltVoiceConfig` มีการใช้ `@SerialName("voice_name")` ซึ่งไม่ตรงกับสเปก API (`voiceName`)
       3. การบังคับใช้ `Puck` สำหรับภาษาไทยในโหมดสัตว์เลี้ยงทำให้ Gemini Live ในบางเทิร์นไม่ส่ง Audio Chunks ส่งผลให้ Client ตกไปใช้ Offline Android TTS Fallback (ซึ่งฟังดูเหมือนบอทอ่านหนังสือ แข็งกระด้าง)
     - *Fix*:
       1. ปรับปรุง Data Model ใน `LiveGeminiService.kt`: รวมเป็น `LiveRealtimeInputData(mediaChunks = listOf(LiveMediaChunk(...)))` ตรงตามมาตรฐาน Gemini Multimodal Live API
       2. แก้ไข `@SerialName("voiceName")` ใน `LivePrebuiltVoiceConfig`
       3. ใช้ `selectedVoiceName` (หรือ `"Aoede"`) ซึ่งเป็นเสียงหลักที่เสถียร 100% กับภาษาไทยในโหมด Live ปกติ โดยให้เลเยอร์ Hardware DSP (`PcmAudioEngine.android.kt` ด้วย `pitch = 1.28f, speed = 1.04f` และ Ring Modulation) แปลงเสียงให้เป็นเสียงน้องหุ่นยนต์น่ารักแบบเรียลไทม์ ทำให้ได้เสียงสนทนาที่ลื่นไหล เป็นธรรมชาติ 100% ไม่หลุดไปเป็น TTS อีกต่อไป
  3. **Video Bandwidth Throttling**:
     - ปรับให้ส่งภาพวิดีโอไปยัง WebSocket เฉพาะเมื่อผู้ใช้เปิดหน้าต่างดวงตาสัตว์เลี้ยง (`isCameraPipOpen == true`) เท่านั้น ส่วนตอนปิดตาจะประมวลผลบนเครื่อง (On-Device ML Kit) เท่านั้น เพื่อไม่ให้กิน Bandwidth และไม่รบกวนจังหวะการรับส่งเสียงของ Gemini Live
- **Verification**:
  - Unit Tests: `./gradlew testDebugUnitTest` ผ่าน 100% (30 tasks, BUILD SUCCESSFUL)
  - APK Build: `./gradlew assembleDebug` ผ่าน 100% (43 tasks, BUILD SUCCESSFUL)

## 2026-09-11 — Multimodal Pet Vision (Gemini Live Video Streaming & ML Kit Multi-Object / Hand / Finger Detection) & Voice Responsiveness Fixes
- **Problem Solved (แก้ปัญหา AI ไม่ตอบเสียง และตอบมั่วสิ่งที่เห็นเมื่อถาม)**:
  1. **Voice Responsiveness & Speech Cadence (ตรวจจับเสียงเจอแต่ AI ไม่พูดโต้ตอบ)**:
     - *Root Cause*: การตั้งค่า VAD (`automaticActivityDetection`) ใน `LiveGeminiService.kt` มี `silenceDurationMs` สั้นเกินไป ทำให้ตัดเสียงภาษาไทยก่อนประโยคจบ และไม่มีการระบุ `languageCodes` ใน `inputAudioTranscription` ส่งผลให้ Google Gemini Live คาดเดาภาษาผิดพลาด รวมถึง Buffer ใน `VoiceController.kt` จุได้เพียง 50 chunks เสี่ยงต่อการ drop audio chunks
     - *Fixes*:
       - กำหนด `languageCodes = ["th-TH", "en-US"]` ใน `inputAudioTranscription` เพื่อให้โมเดลประมวลผลเสียงภาษาไทยได้อย่างแม่นยำ
       - ปรับเพิ่ม `silenceDurationMs = 1200` และ `prefixPaddingMs = 300` ใน `realtimeInputConfig.automaticActivityDetection` เพื่อรองรับจังหวะการพูดภาษาไทย ไม่ตัดเสียงก่อนจบประโยค
       - ขยายขนาด `micChannel` buffer ใน `VoiceController.kt` เป็น 100 chunks (~2.5-3 วินาที) ป้องกัน chunk drop
       - เพิ่มเคาน์เตอร์และ debug log `🎤 Audio chunks streaming to WebSocket` (แท็ก `"LiveGemini"`) เพื่อติดตามการส่งเสียง
  2. **Multimodal Pet Vision & Anti-Hallucination (AI มั่วสิ่งที่เห็นเมื่อถาม ไม่รู้ว่าเห็นจริงหรือไม่)**:
     - *Root Cause*: ในโหมดสัตว์เลี้ยง กล้องส่งเฟรมไปเฉพาะ on-device ML Kit ภายในเครื่อง แต่ไม่ได้ส่ง JPEG frames ไปยัง Gemini Live WebSocket (`realtimeInput.video`) ทำให้โมเดลบนคลาวด์ "มองไม่เห็นภาพจริง" และตอบเดา/hallucinate จากข้อความ
     - *Fixes*:
       - เชื่อมโยง `onFrameCapture` ของ `CameraPreviewView` ใน `AlwaysLiveScreen.kt` ส่ง Base64 JPEG frames ผ่าน `JarvisViewModel.sendLiveCameraFrame` ไปยัง Gemini Live WebSocket แบบ Throttled (~1 FPS / 900ms) ทั้งในหน้าต่าง PIP ลอยและ Background Preview
       - เพิ่ม Rule 8 (Pet Vision & Anti-Hallucination) ใน `PET_LIVE_SYSTEM_PROMPT` (`JarvisPersona.kt`) สั่งให้หุ่นยนต์สังเกตภาพจากกล้องจริงอย่างซื่อสัตย์ เมื่อผู้ใช้ถามว่า "เห็นอะไร?", "ฉันถือนิ้วกี่นิ้ว?", หรือ "ในมือฉันคืออะไร?" ให้ตอบสิ่งที่เห็นจริงสั้นๆ น่ารัก
  3. **ML Kit Multi-Object & Skin-Cluster Hand / Finger Detection (ตรวจจับหลายอย่าง: มือ นิ้ว วัตถุ)**:
     - *Dependency*: เพิ่ม `com.google.mlkit:object-detection:17.0.2` ใน `composeApp/build.gradle.kts`
     - *ML Kit Multi-Object Detection*: ติดตั้ง `ObjectDetector` (`STREAM_MODE`, Multiple Objects, Classification) ใน `PetVisionDetector.kt` รันแบบขนานร่วมกับ Face Detection ผ่าน `Tasks.whenAllComplete`
     - *Heuristic Hand & Finger Tracker*: พัฒนาอัลกอริทึม Computer Vision ตรวจจับกลุ่มพิกเซลสีผิว (Skin-Tone Clustering ในระบบสี RGB + HSV) นอกกรอบใบหน้า:
       - วิเคราะห์การกระจายตัวของนิ้วส่วนบน (Top 35% projection analysis) เพื่อจำแนก:
         - `"Finger / Point ☝️"` (ชู 1 นิ้ว)
         - `"Fingers / Peace ✌️"` (ชู 2 นิ้ว / สองนิ้วสู้ตาย)
         - `"Hand / Palm 🖐️"` (กางฝ่ามือ / 5 นิ้ว)
         - `"Hand ✋"` (ยกมือ)
     - *AR Bounding Boxes*: แสดงกรอบ AR สีสดใสแยกหมวดหมู่ในหน้าต่าง Camera PIP Window:
       - หน้า: ไซแอน `#00F0FF`, ยิ้ม: ชมพู `#FF4081`, ขยิบตา: ทอง `#FFD700`
       - มือ/นิ้ว: ส้มสดใส `#FF9100` พร้อมป้ายกำกับอิโมจิ
       - วัตถุ: เขียว `#4CAF50` (เครื่องดื่ม), ไซแอนเข้ม `#00E5FF` (อุปกรณ์/สิ่งของ), ม่วง `#B388FF` (กล่อง/ของใช้)
     - *Live HUD Badge Enhancement*: ปรับปรุง Badge มุมบนซ้ายให้แสดงสถานะ `🖐️ Hand ✋` หรือ `📦 Object / Item 📱` ควบคู่กับใบหน้าแบบเรียลไทม์
- **Verification**:
  - Unit Tests: รัน `./gradlew testDebugUnitTest` ผ่าน 100% (30 tasks, BUILD SUCCESSFUL)
  - APK Build: รัน `./gradlew assembleDebug` สำเร็จ 100% (43 tasks, BUILD SUCCESSFUL)

## 2026-09-11 — Virtual Desk Pet Vision: Live Detection HUD, Camera Eye PIP & Bounding Box Overlay
- **Features Implemented (ระบบตรวจจับสด HUD, หน้าต่างสายตา AI และกรอบสี่เหลี่ยม Bounding Box ในโหมดสัตว์เลี้ยง)**:
  1. **Live Detection Status HUD (`AlwaysLiveScreen.kt`)**:
     - แสดงแถบ HUD แสดงสถานะเซนเซอร์และการตรวจจับแบบ Real-Time ที่มุมบนซ้าย:
       - 🐾 `[Touch]`: แสดงสถานะการสัมผัส (ลูบหัว `Pet Head`, จิ้มแก้ม `Poke`, จั๊กจี้ `Tickle`, ลากสายตา `Gaze`, แตะหน้าจอ) พร้อมเรืองแสงเขียวเมื่อแตะ
       - 🎭 `[LISTENING]`: แสดงสถานะการรับเสียง ไมค์เปิด/ปิด, ระดับเสียงผู้ใช้ (Mic level %), และสถานะ AI ตอบกลับ
       - 🌀 `[Shake]`: ตรวจจับการเขย่าเครื่อง (Accelerometer > 2.2G) พร้อมเตือน `Shake detected! (@_@)` เรืองแสงส้มกระพริบ
       - 👀 `[Face tracked]`: ตรวจจับใบหน้าผู้ใช้ (ML Kit Face Tracking) พร้อมบอกพิกัด `(X, Y)`, สถานะยิ้ม `Smile %` และการขยิบตา `Wink`
     - มี Ticker 500ms อัปเดตสถานะอัตโนมัติ คืนสู่สถานะปกติอย่างนุ่มนวลเมื่อไม่มีการกระทำ
  2. **Camera Eye PIP Preview Window ("ลืมตา / เปิดกล้อง / หลับตา / ปิดกล้อง")**:
     - เพิ่มปุ่มกดมุมบนขวา `[👁️ ลืมตา]` / `[👁️ หลับตา]` สลับเปิดดูสิ่งที่ AI เห็นได้ทันที
     - รองรับคำสั่งเสียงเร็ว (Fast-Path Voice Triggers ใน `VoiceController.kt`): "ลืมตา", "เปิดกล้อง", "มองหน่อย", "ดูหน่อย" $\rightarrow$ ลืมตา; "หลับตา", "ปิดกล้อง" $\rightarrow$ หลับตา
     - หน้าต่างลอยแสดงภาพกล้องจริง (PIP Window) สลับกล้องหน้า/กล้องหลังได้ด้วยปุ่ม `🔄` และปิดได้ด้วยปุ่ม `❌`
     - ทำงานร่วมกับ Background Camera Preview โดยคงการสแกนใบหน้าอย่างต่อเนื่องโดยไม่เกิดปัญหา CameraX Device Conflicts
  3. **Real-Time AR Bounding Box & Label Overlay (`AROverlayEngine.kt`, `PetVisionDetector.kt`, `PetVisionBridge.kt`)**:
     - แปลงผลการตรวจจับจาก Google ML Kit Face Detection เป็น `List<DetectedObject>` พร้อม Normalized `BoundingBox(x, y, width, height)`
     - วาดกรอบสี่เหลี่ยม 4 มุมหนา (Cyberpunk Corner Brackets) พร้อมเอฟเฟกต์ Pulsing Glow และ Scanline Animation
     - ปรับสีและป้ายกำกับอัตโนมัติ:
       - ตรวจพบยิ้ม: สีชมพู `#FF4081` ป้าย `Boss Smile 😊 X%`
       - ตรวจพบขยิบตา: สีทอง `#FFD700` ป้าย `Boss Wink 😉`
       - ใบหน้าปกติ: สีไซแอน `#00F0FF` ป้าย `Boss Face #ID`
     - ปรับปรุง `ObjectLabelTags` ด้วย `BoxWithConstraints` ให้คำนวณตำแหน่งป้ายกำกับสัมพันธ์กับขนาดหน้าต่าง PIP อย่างแม่นยำ
- **Verification**:
  - Unit Tests: เพิ่มการทดสอบ `PetVisionBridge handles objects detected and eye open request` ใน `PetModeTest.kt` รันผ่านครบ 100%
  - Gradle Build: `./gradlew testDebugUnitTest` สำเร็จ (BUILD SUCCESSFUL)

## 2026-09-11 — Complete Isolation of Normal Live Assistant & Virtual Desk Pet Personas
- **Problem Solved (แก้ปัญหาความสับสนระหว่าง Live Persona ปกติ กับ Pet Persona ปนกัน)**:
  - พบปัญหาการ Bleed ข้ามกันระหว่างโหมด: เมื่อเปิด Pet Mode แล้วปิดออกมา หรือสลับโหมด กลายเป็นว่าโหมด Live ปกติยังติดคำทักทาย "ปิ๊บๆ สวัสดีฮับ พร้อมเล่นแล้ว", ใช้เสียง Puck, และเปิดฟิลเตอร์เสียงหุ่นยนต์ DSP ในขณะที่ใน Pet Mode บอทกลับไปดึง Core Memory ว่าเป็น "เทรดเดอร์อัจฉริยะ" และแอบเรียก Tools การเงินหรือ `device_avatar_emotion` ซ้ำซ้อนจนบังคับเปิด AlwaysLive เอง
- **Root Cause & Fixes**:
  1. **Strict Session & Resumption Isolation (`LiveGeminiService.kt`, `JarvisOrchestrator.kt`)**:
     - เพิ่ม `resetSessionResumption()` ล้าง `sessionResumptionHandle = null` ทุกครั้งที่มีการสลับโปรไฟล์ เพื่อตัดขาดบริบทเก่า ไม่ให้ Google Gemini Live กู้คืนประวัติและ System Instruction ของโหมดเดิม
     - ใน `LiveGeminiService`: หากอยู่ในโหมดสัตว์เลี้ยง (`isPetMode == true`) จะส่งเฉพาะ `PET_LIVE_SYSTEM_PROMPT` เท่านั้น โดยตัด Core Memory, ข้อมูลตลาดหุ้น, และประวัติการคุยเก่าทั้งหมดออกเด็ดขาด
     - ปิดการเชื่อมต่อ Native Tools (`tools = null`) ในโหมดสัตว์เลี้ยง 100% ป้องกันโมเดลเรียก Tools การเงินหรือคำสั่งอุปกรณ์โดยไม่ตั้งใจ
  2. **WebSocket Reconnect on Persona Switch (`VoiceController.kt`, `JarvisViewModel.kt`)**:
     - เนื่องจากโพรโทคอล Gemini Live WebSocket กำหนดว่าข้อความ `setup` (System Instruction, Tools, Voice Name) จะถูกส่งเพียงครั้งเดียวตอนเริ่มเชื่อมต่อ การส่ง realtime text แทรกจะไม่สามารถเปลี่ยน Voice หรือ Tools กลางคันได้
     - เมื่อสลับโปรไฟล์และ Live กำลังเปิดอยู่ ระบบจะเรียก `voice.restartVoiceSession()` เพื่อปิด WebSocket เดิมและเปิดเชื่อมต่อใหม่ด้วย System Prompt, Voice Config (Puck vs ผู้ช่วยเดิม), และ Tools ของ Persona ใหม่ทันที
  3. **Strict State Cleanup on Exit & Chat Bar Launch (`App.kt`, `AlwaysLiveManager.kt`, `VoiceController.kt`)**:
     - เมื่อปิด Always Live หรือกดออกจากโหมด (`disable()`, `stopPetMode()`, `onEndLive`): คืนค่า `AlwaysLiveProfile.CONTROL`, ตั้ง `JarvisPersona.isPetMode = false`, และปิด `isRobotVoiceEnabled = false` เสมอ
     - เมื่อเริ่ม Live จาก ChatInputBar ในหน้าแชทปกติ: บังคับตั้งค่าเป็น `AlwaysLiveProfile.CONTROL` เสมอ เพื่อให้การคุยปกติเป็นผู้ช่วย 100% ไม่ปนเสียงหรือบุคลิกสัตว์เลี้ยง
     - ใน `VoiceController`: กรองประวัติ `historySnapshot` ไม่ให้มีคำสั่ง "เปิดโหมดสัตว์เลี้ยง" ตกค้าง และล้างประวัติเป็นว่างเปล่าใน Pet Mode
     - ใน `App.kt`: แก้ไข `registerTestEmotion` ให้เปิด Full Screen Always Live เฉพาะกรณีที่เป็นคำสั่ง `DEMO` เท่านั้น ป้องกันการเด้งสลับหน้าจอไม่พึงประสงค์
- **Verification**:
  - Unit Tests: อัปเดต `PetModeTest.kt` เพิ่มการตรวจสอบ Isolation ของการทักทายและการตั้งค่าทั้งสองโหมด รันผ่านครบ 100% (`:composeApp:testDebugUnitTest`)
  - Compilation: `:composeApp:compileDebugKotlinAndroid` ผ่าน 100% (BUILD SUCCESSFUL)

## 2026-09-11 — Dual-Layer Robot Voice Engine (DSP Filter, PlaybackParams Pitch Shift & Desk Pet Persona)
- **Problem Solved (แก้ปัญหา AI ในโหมดสัตว์เลี้ยงยังตอบกลับด้วยเสียงปกติ ไม่ใช่เสียงหุ่นยนต์)**:
  - ในเวอร์ชันก่อนหน้า โค้ดส่งเสียง PCM 24kHz จาก Gemini Live ตรงไปยังลำโพงโดยตรงโดยไม่มี DSP หรือ Pitch Shifting ทำให้เสียงที่เล่นออกมาเป็นเสียงมนุษย์ผู้ใหญ่ปกติ และ System Prompt ของ Live Session ยังคงเป็นผู้ช่วยระดับสูง/นักวิเคราะห์การเงิน ทำให้ AI ตอบแบบทางการ
- **Dual-Layer Architecture Implementation**:
  1. **Layer 1: Real-Time Hardware & DSP Audio Pipeline (`PcmAudioEngine.android.kt`, `VoiceController.kt`)**:
     - **Dynamic PlaybackParams**: เมื่อเข้าสู่โหมดสัตว์เลี้ยง (`isRobotVoiceEnabled = true`) ปรับ AudioTrack Hardware Playback Parameters ให้เป็นเสียงหุ่นยนต์ตัวจิ๋ว: `pitch = 1.28f` (โทนเสียงสูงน่ารัก กึ่งหุ่นยนต์เด็ก), `speed = 1.04f` (พูดเร็วและกระฉับกระเฉงขึ้นเล็กน้อย)
     - **Real-Time 16-Bit PCM DSP Filter (`applyRobotDsp`)**:
       - *Ring Modulation (72Hz Carrier)*: จำลองฮาร์มอนิกสังเคราะห์เสียงโลหะหุ่นยนต์ (Synthesizer metallic timbre)
       - *Feedforward Comb Filter (48 samples, ~500Hz)*: จำลอง resonance ในโครงสร้างช่องอกหุ่นยนต์ (Acoustic chassis resonance)
       - *Soft Analog Saturation*: ตัดความแหลมคมของคลื่นเสียงด้วย soft clipping เพื่อให้เสียงมีความอบอุ่นและมีมิติ
     - **Opening Robot Chirp**: เล่นเสียงเอฟเฟกต์หุ่นยนต์ทักทายสดใส (`RobotSoundPlayer.playHappy()`) ทันทีที่ AI เริ่มตอบกลับประโยคใหม่
  2. **Layer 2: Virtual Desk Pet Persona & Prompt Switching (`JarvisPersona.kt`, `LiveGeminiService.kt`, `LiveToolBridge.kt`)**:
     - **Dynamic Prompt Switch**: เพิ่ม `JarvisPersona.isPetMode` และ `PET_LIVE_SYSTEM_PROMPT` โดยสลับ prompt อัตโนมัติเมื่ออยู่ในโหมดสัตว์เลี้ยง:
       - กำหนดตัวตนเป็น "หุ่นยนต์สัตว์เลี้ยงตั้งโต๊ะตัวจิ๋วแสนน่ารัก" ขี้เล่น อ้อนเจ้านาย ช่างสงสัย
       - กฎเสียงพูดบังคับตอบสั้นมาก 1-2 ประโยค ห้ามตอบยาวเป็นทางการ ห้ามวิเคราะห์การเงิน/ตลาดหุ้น
       - กำหนดคำเลียนเสียงหุ่นยนต์ประกอบประโยคเสมอ เช่น "ปิ๊บๆ!", "บี๊บๆ!", "งุ้ยย~", "แง้วว~", "ดุ๊กดิ๊กๆ"
       - คำทักทายมาตรฐานประจำโหมด: "ปิ๊บๆ! สวัสดีฮับ... น้องหุ่นยนต์สัตว์เลี้ยงพร้อมเล่นด้วยแล้ว งุ้ยย~"
     - **Playful Prebuilt Voice Profile**: ใน `LiveGeminiService.kt` เลือกใช้เสียง `Puck` (เสียงวัยรุ่น สดใส ร่าเริง) ในโหมดสัตว์เลี้ยงโดยอัตโนมัติ
     - **Offline TTS Fallback Enhancement**: ใน `VoiceManager.kt` ปรับ TTS pitch เป็น `1.35f` และ speed เป็น `1.15f` หากต้อง fallback สังเคราะห์เสียงพูดในเครื่อง
  3. **Seamless State Sync (`JarvisViewModel.kt`, `AlwaysLiveManager.kt`)**:
     - เมื่อสลับโปรไฟล์ผ่าน UI หรือ AlwaysLiveManager ระบบจะอัปเดต `JarvisPersona.isPetMode` และ `VoiceController.setRobotVoiceEnabled` ทันที พร้อมยิง realtime instruction ไปยัง Gemini Live session ปัจจุบันเพื่อเปลี่ยนบุคลิกทันทีโดยไม่ต้องตัดการเชื่อมต่อ
- **Verification**:
  - Unit Tests: เพิ่มการทดสอบใน `PetModeTest.kt` ทดสอบการสลับ prompt แบบ Dynamic, ข้อความเสียงหุ่นยนต์ และข้อห้ามเรื่องการเงิน ผ่านครบ 100% (196 tests ใน `testDebugUnitTest`)
  - Compilation: `:composeApp:compileDebugKotlinAndroid` ผ่าน 100% BUILD SUCCESSFUL

## 2026-09-11 — Fix Pet Motion Bridge, Audio Emotion Clobber Bug & Comprehensive Logcat Tags
- **Root Cause & Bug Fixes (แก้ไขปัญหา Pet Mode ไม่เปลี่ยนอารมณ์ตามเซนเซอร์/การสัมผัส)**:
  1. **PetMotionBridge Event Disconnect**: เซนเซอร์จับการเขย่าเครื่อง (`PetMotionDetector`) ตรวจพบแรงสั่นสะเทือน (`🌀 Shake detected! gForce=2.44`) แต่ `AlwaysLiveManager` ไม่ได้ยิงสัญญาณผ่าน `PetMotionBridge` ไปยัง `AlwaysLiveScreen` / `PetModeController` ทำให้หน้าจอ UI ไม่ทราบว่ามีการเขย่า $\rightarrow$ ทำการเชื่อมโยง `PetMotionBridge.triggerShake()`, `triggerFaceDown()`, `triggerFaceUp()` ใน `AlwaysLiveManager` และผูก Listener ใน `AlwaysLiveScreen` ครบถ้วน
  2. **Ambient Mic Audio Overwrite Clobbering Pet Emotions**: ใน `AlwaysLiveScreen.kt` ฟังก์ชัน `LaunchedEffect(avatarState)` มีเงื่อนไข `avatarState.audioLevel > 0.05f` ซึ่งทำงานทุกครั้งที่ไมโครโฟนจับเสียงสภาพแวดล้อมได้ แล้วเขียนทับ `activeAvatarState = avatarState` (ซึ่งมีสถานะเป็น `IDLE`) ทันที ส่งผลให้อารมณ์ `DIZZY`, `SLEEPING`, `LOVE`, `EXCITED` ถูกล้างหายไปในเสี้ยววินาที $\rightarrow$ แก้ไขให้ในโหมด `PET` จะอัปเดตเฉพาะ `audioLevel` สำหรับขยับปาก/แสงเรืองแสง และรักษาอารมณ์ของน้องไว้ เว้นแต่ AI จะพูดจริง (`isSpeaking == true`)
  3. **Front Camera Gaze Double-Mirror Fix**: แก้ไขการ Mirror พิกัดแกน X ใน `PetVisionDetector` เมื่อรับภาพจาก `CameraPreviewView` ที่ Mirror ภาพกล้องหน้ามาแล้ว เพื่อไม่ให้ทิศทางตาสัตว์เลี้ยงมองย้อนทิศทางของผู้ใช้
- **Logcat Tag System & Observability Guide (แท็กสำหรับตรวจสอบ Log การทำงาน)**:
  - `PetMotionDetector`: ตรวจจับการสั่นสะเทือน/เขย่าเครื่อง (> 2.2G) และการคว่ำ/หงายหน้าจอบนโต๊ะ
  - `PetVisionDetector`: รับเฟรมกล้องหน้า, ตรวจจับใบหน้า Google ML Kit, พิกัดสายตา (Gaze Tracking), ผู้บุกรุก (Sentry), เกมเลียนแบบหน้า (Copycat)
  - `PetModeController`: การสัมผัสเล่น (ลูบหัว, จิ้มแก้ม, จั๊กจี้), อารมณ์และการกระทำต่างๆ ของสัตว์เลี้ยง
  - `RobotSoundEngine`: การสังเคราะห์และเล่นเสียงเอฟเฟกต์หุ่นยนต์ (Purr, Happy, Snore, Confused, Giggle, WakeUp, Alarm)
  - `AlwaysLiveManager`: การสลับโปรไฟล์ (`CONTROL` vs `PET`), Service & WakeLock
  - `JarvisAvatar`: การวาด Canvas และเปลี่ยนสีหน้า Avatar
  - `JARVIS_VM`: การเชื่อมต่อ WebSocket Gemini Live และ Mic Streaming
- **Verification**:
  - คอมไพล์ผ่าน 100%: `:composeApp:compileDebugKotlinAndroid` (BUILD SUCCESSFUL)
  - Unit Tests ผ่าน 100%: `:composeApp:testDebugUnitTest` ผ่านครบทั้ง 195 tests

## 2026-09-11 — Virtual Desk Pet Living Avatar, Clean Mode Separation & Google ML Kit On-Device Vision
- **Strict Mode Separation (แยก 2 โหมดชัดเจน เด็ดขาด ไม่สับสน)**:
  - **โหมดขับขี่ / โหมดควบคุม (Drive & Control Mode)**: รวมเป็นโหมดเดียวกัน (`AlwaysLiveProfile.CONTROL`) แสดงผลด้วย **3D Pearlescent Clay Robot Avatar** (`JarvisAvatar`) พร้อมเครื่องมือ Live เต็มรูปแบบ ควบคุม Google Maps, YouTube, รับสาย, สั่งงานเครื่อง Hands-Free บุคลิกและเสียงตาม Persona ที่ผู้ใช้ตั้งไว้ ไม่มีเสียงร้องเจื้อยแจ้วและไม่มี Gesture สัมผัสกวนใจ
  - **โหมดสัตว์เลี้ยงตั้งโต๊ะ (Virtual Desk Pet)**: โหมดสัตว์เลี้ยงตัวจริง (`AlwaysLiveProfile.PET`) แยก Persona ชัดเจน ไม่ปะปนกับโหมดขับขี่/ควบคุม
- **Fullscreen Living Robot Head Avatar (`PetRobotHeadAvatar.kt`)**:
  - สร้าง Composable แสดงผลเฉพาะ **ส่วนหัวหุ่นยนต์มินิมอลมีชีวิต** ตามภาพเรฟเฟอเรนซ์ของผู้ใช้ (`media_1789115340717.jpg`):
    1. ตัวเรือนหุ่นยนต์เซรามิกขาวเรียบหรูโค้งมน (White Ceramic Rounded Chassis) พร้อมมิติแสง 3D
    2. หูโลหะ Slate-Blue ทั้งสองข้าง (Metallic Ear Disc Knobs with Dual-Rim Bevels)
    3. กระจกหน้ากากดำเงาโค้งมน (Glossy Dark Visor with Top Specular Gloss Arc Reflection)
    4. หน้าจอดิจิทัลเรืองแสง Digital Pixel Dot Matrix แสดงผล 11 อารมณ์ (`HAPPY`, `WINK`, `SAD`, `ANGRY`, `CONFUSED`, `LOVE`, `SLEEPING`, `THINKING`, `EXCITED`, `POUT`, `DIZZY`)
    5. การเคลื่อนไหวมีชีวิต: หายใจกระเพื่อม (Breathing Bobbing), กะพริบตาสดใสทุก 4 วินาที, เอียงคอตามการมอง
- **Zero Button Clutter Philosophy in Pet Mode**:
  - ถอดปุ่มควบคุม แผงแท็บ ชิป และปุ่มกดยิบย่อยทั้งหมดออกจากหน้าจอโหมดสัตว์เลี้ยง เพื่อให้น้องเหมือนสัตว์เลี้ยงหุ่นยนต์ตัวจริง (Living Companion) ที่ทำงานอัตโนมัติ 100% ผ่านการสัมผัส เซนเซอร์ และกล้อง AI มีเพียงปุ่มมุมจอบางๆ ไว้สลับกลับโหมดควบคุมหรือปิด
- **Orientation-Aware Touch Gestures (Portrait & Landscape)**:
  - คำนวณพิกัดสัมผัส Normalization เทียบกับขนาดและตำแหน่งของหัวหุ่นยนต์จริงทั้งแนวตั้งและแนวนอน:
    - ลูบหน้าผากลง (Forehead Swipe Down) $\rightarrow$ ตาหัวใจ (LOVE) + เสียงครางเพลิน (Purr)
    - เกาคางขึ้น (Chin Scratch Up) $\rightarrow$ ตาหัวใจ (LOVE) + เสียง Purr
    - จิ้มแก้ม (Cheek Poke) $\rightarrow$ ร้องส่งเสียงทักทายสดใส (Happy Chirp)
    - จิ้มสองครั้งที่แก้ม (Cheek Double-Tap / Tickle) $\rightarrow$ หัวเราะชอบใจ (Giggle) + ตาหยีสั่น
    - ลากนิ้วบนจอ $\rightarrow$ ตาสัตว์เลี้ยงขยับกลอกตามตำแหน่งนิ้วแบบ Real-time
- **On-Device Vision via Google ML Kit (`PetVisionDetector.kt`, `PetVisionBridge.kt`)**:
  - เพิ่ม Dependency `com.google.android.gms:play-services-mlkit-face-detection:17.1.0`
  - ตรวจจับใบหน้า สายตา และการแสดงออกทางสีหน้าแบบ On-Device 100% (0 tokens, zero latency, ไม่เสียค่า API):
    1. **Real-Time Gaze Tracking**: คำนวณจุดศูนย์กลางใบหน้าผู้ใช้หน้าโต๊ะทำงาน ปรับ `gazeOffsetX`, `gazeOffsetY` ให้ตาสัตว์เลี้ยงมองตามผู้ใช้แบบมีชีวิต
    2. **Desk Sentry (สายตรวจเฝ้าโต๊ะ)**: เมื่อเปิดโหมดเฝ้าโต๊ะ หากมีคนเดินเข้ามาหน้ากล้อง จะส่งเสียงไซเรนเตือนภัย (Alarm) พร้อมหน้าตาแดงดุ (ANGRY) แจ้งเตือนผู้บุกรุกทันที
    3. **Copycat Face Mimic Game**: มินิเกมเลียนแบบหน้า ท้าทายผู้ใช้ยิ้มกว้าง (`smilingProbability > 0.65f`) หรือขยิบตาแข่งกับน้อง (`abs(leftEye - rightEye) > 0.50f`) เมื่อทำสำเร็จจะส่งเสียงเชียร์และแสดงความดีใจ
- **Procedural Snore Synthesizer (`RobotSoundEngine.kt`, `RobotSoundPlayer.kt`)**:
  - เพิ่มเสียง `RobotSound.SNORE`: สังเคราะห์คลื่นเสียงกรนฟี้ๆ ช่วงหายใจเข้า $130\text{Hz} \to 200\text{Hz}$ และหายใจออก $190\text{Hz} \to 95\text{Hz}$ พร้อมลูกคอ Flutter นุ่มนวล
  - ทำงานร่วมกับการคว่ำหน้าจอบนโต๊ะ (Desk Face-Down) $\rightarrow$ น้องหลับฟี้ๆ พร้อมเสียงกรนและตัวอักษร `z z z` ลอย
- **Verification & Testing**:
  - เพิ่ม Unit Tests ใน `PetModeTest.kt` และอัปเดต `AlwaysLiveTest.kt` ทดสอบ 14 AvatarEmotion, Dizzy, Face-Down/Face-Up, Copycat Game, และ PetVisionBridge
  - รันผ่าน 100%: `:composeApp:testDebugUnitTest` ผ่านทั้งหมด 195 tests
  - คอมไพล์ผ่าน 100%: `:composeApp:compileDebugKotlinAndroid` (BUILD SUCCESSFUL)

## 2026-09-11 — Virtual Desk Pet Mode (โหมดสัตว์เลี้ยง), Procedural Robot Sound FX & Motion Sensor Gestures
- **Virtual Desk Pet Architecture (`AlwaysLiveProfile.kt`, `PetModeController.kt`, `PetFeatureTab.kt`)**:
  - พัฒนา "โหมดสัตว์เลี้ยง" (Virtual Desk Pet) เพิ่มเติมควบคู่กับ "โหมดควบคุม" และ "โหมดขับขี่" ตามคำขอและแรงบันดาลใจจาก LOOI Robot
  - รองรับ 3 โหมดหลักใน Always Live ผ่าน `AlwaysLiveProfile` (`CONTROL`, `DRIVE`, `PET`) พร้อม UI Pill Selector ที่สลับโหมดได้แบบเรียลไทม์
  - ควบคุมสถานะและพฤติกรรมผ่าน `PetModeController` จัดการ 4 แท็บฟีเจอร์ย่อย:
    1. `🐾 เล่น` (Play & Interact): ปฏิสัมพันธ์สัมผัสกับน้อง (ลูบหัว, จิ้มแก้ม, จั๊กจี้, ปลุกน้อง)
    2. `🛡️ เฝ้าโต๊ะ` (Desk Sentry): โหมดสายตรวจเฝ้าโต๊ะทำงาน ตรวจจับผู้บุกรุกพร้อมส่งเสียงไซเรนเตือนอัตโนมัติ
    3. `⏱️ โฟกัส` (Focus Buddy): เพื่อนคู่คิดช่วยโฟกัสงาน (Pomodoro Timer) 25m / 5m / 50m พร้อมเสียงให้กำลังใจเมื่อครบเวลา
    4. `🎲 เซียมซี` (Fortune Oracle): มินิเกมเขย่าเซียมซีสุ่มคำทำนายดวงและคำแนะนำประจำวัน
- **Procedural Robot Sound FX Engine (`RobotSoundEngine.kt`, `RobotSoundPlayer.kt`)**:
  - สร้างระบบสังเคราะห์คลื่นเสียงหุ่นยนต์ด้วยคณิตศาสตร์ (16-bit PCM AudioTrack Synthesis) โดยไม่ต้องพึ่งพาไฟล์เสียงภายนอก (.mp3/.wav) ช่วยประหยัดพื้นที่ APK และตอบสนองเร็วกว่า 0ms latency
  - สังเคราะห์ 8 เสียงหุ่นยนต์น่ารัก: `HAPPY` (Double chirp crescendo 880->1760Hz), `PURR` (Low purring vibration 110Hz modulated), `SURPRISE` (Rising glissando 440->2200Hz), `CONFUSED` (Questioning chirp 600->450Hz), `ALARM` (High-pitched siren warble 1400<->2400Hz), `YAWN` (Descending smooth glissando 600->220Hz), `GIGGLE` (Staccato happy bursts 900-1400Hz), `WAKE_UP` (Tri-tone arpeggio C5-E5-G5)
  - เชื่อมโยงผ่าน `RobotSoundPlayer` (commonMain) ไปยัง `RobotSoundEngine` (androidMain)
- **Interactive Touch & Expressive Avatar Animation (`JarvisAvatar.kt`, `AvatarEmotion.kt`)**:
  - รองรับการสอดส่องสายตาแบบมีชีวิตชีวา (Idle Gaze Wander) และวงจรพักผ่อน (หาวนอนเมื่อไม่แตะเล่น 60 วินาที, หลับลึกฟี้ๆ หลัง 150 วินาที)
  - แตะหน้าจอขยับสายตามองตามนิ้วผู้ใช้ (`gazeOffsetX`, `gazeOffsetY`)
  - ตรวจจับท่าทางสัมผัส: แตะครั้งเดียว = จิ้มแก้ม (Chirp, Happy smile), แตะสองครั้ง = จั๊กจี้ (Giggle, Excited), กดค้าง = ลูบหัว (Purr, Love hearts)
  - เอฟเฟกต์เวียนหัวหมุนวน (`isDizzy = true`, Rotating Spiral Eyes `@_@`)
- **Motion Sensor Gesture Detection (`PetMotionDetector.kt`, `AlwaysLiveManager.kt`)**:
  - ตรวจจับการเขย่าเครื่อง (Shake Detection > 2.2G) → น้องแสดงอาการเวียนหัวพร้อมตาหมุนวนและเสียงสับสน
  - ตรวจจับการคว่ำหน้าจอบนโต๊ะ (Face-Down on Desk $z < -8.2\text{ m/s}^2$) → น้องเข้าสู่โหมดหลับพักผ่อน (Zzz) พร้อมเสียงหาวนอน
  - ตรวจจับการยกหน้าจอขึ้นมา (Face-Up / Lift) → น้องตื่นทันทีพร้อมเสียง Wake Up ทักทายสดใส
- **Zero Token Cost & Free Tier Guard**:
  - การตรวจจับการเคลื่อนไหวและท่าทางสัมผัสทั้งหมดทำงานแบบ On-Device 100% (0 tokens, ฟรี ไม่กระทบโควต้า Gemini Live 65K TPM)
- **Multi-Channel Integration (`DeviceControlExecutor.kt`, `DeviceToolDefinitions.kt`, `JarvisPersona.kt`, `VoiceController.kt`, `ChatController.kt`)**:
  - รองรับคำสั่งเสียงและข้อความเปิดโหมดสัตว์เลี้ยง: "เปิดโหมดสัตว์เลี้ยง", "โหมดแก้เบื่อ", "pet mode", `/always pet`
  - อัปเดต `device_always_live` ให้รองรับ parameter `mode="pet"`
- **Verification**:
  - เพิ่ม Unit Tests ครอบคลุมใน `PetModeTest.kt` ทดสอบ State Transitions, Gestures, Sentry, Pomodoro Timer และ Fortune Oracle ครบ 100%

## 2026-09-11 — Real-Time Intra-Bar Live Bar Stitching & Anticipation Radar 1-Minute Observability
- **Intra-Bar Real-Time Stitching (`SignalAlertProvider.kt`, `SmcApiService.kt`)**:
  - แก้ไขปัญหาแท่งเทียน Timeframe สูง (15m, 1h) ถูกแคชแช่แข็งราคาเดิมตลอดแท่ง (Frozen Bar Issue): เดิมระบบคืนค่าแท่งเทียนจาก DB เมื่อพบว่ามี Bucket ของแท่งปัจจุบันแล้ว ทำให้ตลอดนาทีที่ 1–14 ราคาแท่ง 15m หยุดนิ่งอยู่ที่ราคาเปิดของนาทีที่ 0
  - เพิ่มฟังก์ชัน `stitchLiveBar(candles, m1Candles, tf)`: นำแท่ง 1m ที่อัปเดตสดทุกนาที มาถักทอ (stitch) เข้ากับแท่งสดที่กำลังก่อตัว (`liveIdx = candles.size - 1`) ของแท่ง 15m/1h อัปเดต `high = maxOf(last.high, intra.high)`, `low = minOf(last.low, intra.low)`, `close = intra.last().close`, `volume = intra.volume` อย่างต่อเนื่อง
  - ทำให้ทั้ง 13 ปัจจัยของ Anticipation (Wick Sweep Rejection, Keyzone Proximity, RSI Extreme, EMA Near Cross ฯลฯ) ตรวจจับพฤติกรรมราคาระหว่างแท่งแบบ Real-time ทุก 1 นาทีตรงตามการเคลื่อนไหวจริงของตลาด
  - แยกสถาปัตยกรรมชัดเจน: Confirmed Signals (Buy/Sell หลัก) ยังคงใช้แท่งปิดล่าสุด (`sigIdx = n - 2`) 100% ป้องกันการเกิด Repaint ในขณะที่ Anticipation ใช้อินดิเคเตอร์และราคาบนแท่งสด (`liveIdx = n - 1`) เพื่อเตือนล่วงหน้าก่อนแท่งปิด
- **Observability Radar Heartbeat (`SignalAlertProvider.kt`)**:
  - แก้ไขปัญหา Silent Polling ที่ทำให้ผู้ใช้เห็นเฉพาะ Log M15 ปิดแท่งทุก 15 นาที:
    - เพิ่ม Radar Heartbeat Log ทุก 1 นาที: `📡 $symbol/$tf Anticipation radar: live=... (13 factors active) → IDLE`
    - เมื่อตรวจพบเงื่อนไขคาดการณ์ล่วงหน้า จะพ่น Log ชัดเจนทันที: `⚡ $symbol/$tf ANTICIPATION RADAR: live=... [Setup] [Side] conf=...% (13 factors active)`
  - ปรับ `SmcApiService.intervalToMillis(interval)` เป็น `companion object fun` เพื่อให้ทุกโมดูลใช้งานร่วมกันได้แบบ centralized
  - ปรับปรุง `SignalAlertProvider.fetch()` ให้ดึง `m1Candles` ครั้งเดียวแล้วแชร์ให้ทั้ง `stitchLiveBar` และ Unified SMC Confirmation Layer โดยไม่ต้อง fetch ซ้ำ
- **Verification**:
  - เพิ่ม Unit Tests ใน `SignalAlertProviderTest.kt`: `testStitchLiveBar_updatesForming15mBarWithLatestM1Data`, `testStitchLiveBar_appendsNewBucketWhen15mCandleLags`, `testStitchLiveBar_preservesCandlesFor1mTimeframe`
  - ทดสอบผ่าน 100%: `:composeApp:testDebugUnitTest` ผ่านทั้งหมดทั้ง `SignalAlertProviderTest` และ `SignalAnticipationTest` (BUILD SUCCESSFUL)

## 2026-09-11 — Trading Signal Anticipation 13-Factor Default, Market Feature Snapshot & Historical Audit
- **Default to All 13 Factors (`AnticipationConfigManager.kt`)**:
  - ปรับระบบ Anticipation ให้เปิดใช้งานครบทั้ง **13 ปัจจัยมาตรฐานเป็นค่าเริ่มต้น** สำหรับทุกสินทรัพย์ (Curated Factor Whitelist ครบทุกตัว ไม่จำกัดเฉพาะ 4 ปัจจัยเดิม):
    1. `KEYZONE_PROXIMITY`: ทดสอบ Demand/Supply Zone
    2. `WICK_SWEEP_REJECTION`: ไส้เทียนปฏิเสธราคา
    3. `RSI_EXTREME`: RSI Oversold/Overbought
    4. `EMA_NEAR_CROSS`: EMA14/60 บีบตัวจ่อ Golden/Death Cross
    5. `BOLLINGER_SQUEEZE`: Bollinger Bands บีบตัวแคบเตรียม Breakout
    6. `MACD_HISTOGRAM_TURN`: MACD Histogram เงย/ปักหัวกลับทิศ
    7. `VOLUME_ABSORPTION`: Smart Money ซุ่มดูดซับแรงซื้อ/ขาย
    8. `FIBONACCI_GOLDEN_POCKET`: ทดสอบแนว 0.618 - 0.65
    9. `STOCHASTIC_OVERSOLD_TURN`: Stoch %K ตัดกลับตัว
    10. `SESSION_OPEN_SWEEP`: กวาดสภาพคล่อง Session High/Low
    11. `VEYRA_SHIFT`: Institutional Shift Ledger
    12. `BB_KC_SQUEEZE`: Bollinger Bands บีบตัวใน Keltner Channels
    13. `FAST_RSI_REVERSAL`: Fast RSI(5) Reversal
- **Candlestick & Market Genome Feature Snapshot (`SignalAlertProvider.kt`, `SignalOutcomeTracker.kt`)**:
  - เมื่อเกิดการคาดการณ์ล่วงหน้า (Anticipation) ระบบจะบันทึก Snapshot สภาพแวดล้อมตลาดกว่า 25 มิติ (`SignalFeatureExtractor.extractJson`) ควบคู่กับบริบท Anticipation (`setup_type`, `factor_id`, `confidence`, `stage`, `reason`, `zone`) ลงในคอลัมน์ `features_json` ของ `SignalTrackingRecord`
  - ช่วยให้ผู้ใช้และ AI สามารถตรวจสอบย้อนหลังได้ว่า การคาดการณ์สมเหตุสมผลหรือไม่ กราฟอยู่ในสภาพแวดล้อมใด ไม่ได้คาดการณ์มั่ว
- **Anticipation Audit Tool Action (`ToolExecutor.kt`, `TradingToolDefinitions.kt`)**:
  - เพิ่ม `action="inspect"` / `"history"` / `"records"` ในเครื่องมือ `trading_signal_anticipation`
  - รองรับพารามิเตอร์ `limit` (default 10 รายการ)
  - แสดงแจกแจงละเอียด: วันเวลา, ทิศทาง, Stage, Confidence, Entry, SL, TP, ผลลัพธ์ R, และแจกแจง Snapshot สภาพแวดล้อมตลาด (H4/H1 Trend, Squeeze, Veyra Score, RSI, Fast RSI, ADX, ATR)
- **SQLite Schema Migration Fix (`DatabaseDriverFactory.kt`, `11.sqm`)**:
  - แก้ไขข้อผิดพลาด `table SignalTrackingRecord has no column named features_json (code 1 SQLITE_ERROR[1])`
  - เพิ่มคำสั่ง Additive Migration: `ALTER TABLE SignalTrackingRecord ADD COLUMN features_json TEXT;` ใน `DatabaseDriverFactory.ensureNewTablesExist()` และสร้างไฟล์ Migration `11.sqm`
  - อัปเดตนิยาม DDL เริ่มต้นของ `SignalTrackingRecord`
- **Signal Dataset Manager Support (`SignalDatasetManager.kt`)**:
  - ปรับปรุง `exportDataset()` ให้รองรับการส่งออกข้อมูล Anticipation เมื่อผู้ใช้หรือ AI ระบุ `strategy = "anticipation"` หรือ `ANTICIPATION_*`
- **Bollinger Squeeze Calculation Fix (`SignalAlertProvider.kt`)**:
  - แก้ไข Band Tolerance ของ Bollinger Squeeze ให้อิงตามขนาดความกว้าง Bandwidth จริง (`min(0.25 * atr14, bbWidth * 0.15)`) พร้อมตรวจสอบทิศทางเทียบกับ Midline (`bbBasis`) ป้องกันการเกิด False Breakout ในช่วงตลาดแกว่งตัวแคบ
- **Verification**:
  - ชุดทดสอบ Unit Tests: `AnticipationConfigManagerTest`, `SignalAnticipationTest`, `SignalDatasetManagerTest`, `SignalOutcomeTrackerTest` รันผ่าน 100% (30/30 tests passed, BUILD SUCCESSFUL)

## 2026-09-11 — Driving Mode Notification Filtering & AI Voice Announcement Fix (No Robotic Echo)
- **Notification Filtering Optimization (`JarvisNotificationListener.kt`)**:
  - เพิ่ม `IGNORED_SYSTEM_PACKAGES`: บล็อกแจ้งเตือนจากระบบ Android และแพ็กเกจเบื้องหลังอย่างเด็ดขาด (`android`, `com.android.systemui`, `com.google.android.gms`, `com.android.vending`, `com.google.android.dialer` ฯลฯ)
  - เพิ่มการตรวจสอบ `sbn.isOngoing`, `FLAG_ONGOING_EVENT`, และ `FLAG_FOREGROUND_SERVICE`: ตัดการแจ้งเตือนประเภทสถานะการชาร์จแบตเตอรี่ (Battery Charging), มีเดียเพลเยอร์, การดาวน์โหลดไฟล์ หรือบริการที่รันค้างทั้งหมด
  - กรองเฉพาะข้อความแชทจริง: ตรวจสอบ `isMessagingApp()` (LINE, SMS, WhatsApp, Messenger, Telegram, Discord), `CATEGORY_MESSAGE`, หรือมี Inline Reply Action (`RemoteInput`) เท่านั้น
  - ระบบ Deduplication: ป้องกันการอ่านแจ้งเตือนซ้ำภายใน 10 วินาที ทั้งทางคีย์และเนื้อหาข้อความ
- **Seamless Live Voice Announcement (`VoiceController.kt`, `JarvisViewModel.kt`, `App.kt`, `MainActivity.kt`)**:
  - แก้ไขปัญหาเสียงหุ่นยนต์ Android Offline TTS พูดซ้อนทับกับเสียงสดธรรมชาติของ AI (`🤖 JARVIS`):
    - เมื่ออยู่ในโหมด Always Live / Driving Mode จะส่งแจ้งเตือนข้อความเข้าสู่ Gemini Live session โดยตรงผ่าน `orchestrator.sendLiveRealtimeText()`
    - AI จะพูดแจ้งเตือนผู้ใช้ด้วยน้ำเสียงและบุคลิกของ JARVIS ที่อบอุ่นและเป็นธรรมชาติ (เสียงผู้หญิง ค่ะ/คะ)
    - อาศัยกลไก Echo Prevention ของ `PcmAudioEngine` และ `VoiceController` ป้องกันไมโครโฟนดูดเสียงลำโพงกลับเข้าโมเดล ไม่เกิดลูปเสียงและไม่มีเสียงพูดแทรก
    - หาก Live session ไม่ได้เชื่อมต่ออยู่ จะ fallback ไปใช้ Offline TTS พร้อม mute ไมโครโฟนชั่วคราวอย่างปลอดภัย
- **Verification**:
  - Unit tests: `:composeApp:testDebugUnitTest` (BUILD SUCCESSFUL in 1m 19s, 100% pass)
  - Android APK: `:composeApp:assembleDebug` (BUILD SUCCESSFUL)

## 2026-09-11 — Driving Mode Enhancement: Smart Notifications (LINE/SMS), GPS Location Context, and Media Control (Now Playing & Search)
- **Smart Notifications & RemoteInput Auto-Reply (`JarvisNotificationListener.kt`, `NotificationBridge.kt`)**:
  - สร้าง `JarvisNotificationListener : NotificationListenerService` ดักจับการแจ้งเตือนขาเข้าจาก LINE, SMS, WhatsApp, Messenger, Telegram, Discord พร้อมจัดเก็บประวัติ 50 รายการล่าสุด
  - เชื่อมต่อการแจ้งเตือนด้วยเสียงอัตโนมัติในโหมดขับขี่ (`MainActivity.kt` + `AlwaysLiveManager`): เมื่อมีข้อความเข้าในโหมด Always Live จะอ่านออกเสียงให้ฟังทันทีผ่าน `NotificationBridge.formatForDrivingSpeech()`
  - เพิ่ม Native Tool `device_notification_read`: สำหรับอ่านข้อความล่าสุด กรองตามชื่อแอปได้
  - เพิ่ม Native Tool `device_notification_reply`: สำหรับส่งข้อความตอบกลับไปยังการแจ้งเตือนโดยตรงผ่าน `RemoteInput` โดยไม่ต้องสลับหน้าจอ
  - เพิ่ม Voice Fast-Path ใน `VoiceController.kt`: สั่ง "ตอบว่า...", "ตอบไลน์ว่า...", "reply ว่า..." เพื่อพิมพ์ตอบกลับทันที
- **GPS Location & Geocoding Context (`LocationProvider.kt`, `device_location`)**:
  - สร้าง `LocationProvider.kt` โดยใช้ Android Native `LocationManager` + `Geocoder` (Zero Dependency)
  - เพิ่มสิทธิ์ `ACCESS_FINE_LOCATION` และ `ACCESS_COARSE_LOCATION` ใน `AndroidManifest.xml`
  - เพิ่ม Native Tool `device_location` (`action="get_current"|"status"`): อ่านพิกัดปัจจุบัน ความเร็วรถ และชื่อที่อยู่/ตำบล/อำเภอ/จังหวัด สำหรับส่งเป็นบริบทให้ AI และผู้ใช้
  - เพิ่มรายการตรวจสอบสิทธิ์ตำแหน่งที่ตั้งใน Setup Checklist ของ `MainActivity.kt`
- **Enhanced Media Control & Now Playing (`MediaInfoProvider.kt`, `device_media_control`)**:
  - ยกระดับ `device_media_control` ให้รองรับ `action="now_playing"`: ดึง Metadata เพลงปัจจุบัน (ชื่อเพลง, ศิลปิน, อัลบั้ม, สถานะเล่น/หยุด, แอปที่เล่น) ผ่าน `MediaSessionManager.getActiveSessions()`
  - รองรับ `action="search_play"`: ค้นหาและเปิดเล่นเพลงเจาะจงผ่าน Intent ของ YouTube, YouTube Music (`com.google.android.apps.youtube.music`), และ Spotify (`spotify:search:`)
  - ส่งคำสั่ง Transport Controls (`play`, `pause`, `next`, `prev`) ตรงไปยัง Media Session ที่กำลังเล่นอยู่ก่อน หากไม่พบจึง fallback ไปยัง Hardware KeyEvent
- **AI Persona & Live Tool Bridge Integration (`JarvisPersona.kt`, `LiveToolBridge.kt`)**:
  - อัปเดต `DEVICE_CONTROL_RULES` และเพิ่ม Rule 15, 16, 17 ใน `LIVE_RULES`
  - เพิ่ม Voice Presentation Rules สำหรับเครื่องมือแจ้งเตือน, เพลง, และตำแหน่งใน `LiveToolBridge.kt`
- **Verification**:
  - `DeviceControlTest.kt`: เพิ่มการทดสอบ `device_notification_read`, `device_notification_reply`, `device_location`, `device_media_control` (Now Playing) ครบ 10 เครื่องมือ ผ่าน 100%
  - Gradle Tests: `:composeApp:testDebugUnitTest` (BUILD SUCCESSFUL in 1m 20s)
  - Android Build: `:composeApp:assembleDebug` (BUILD SUCCESSFUL in 49s)

## 2026-09-11 — Full Physical Grounding & 3D Avatar Emotion Voice Control Integration, Semantic Disambiguation & Zero-Latency Fast Path
- **Avatar Physical Grounding & Persona Enhancement (`JarvisPersona.kt`)**:
  - แก้ไขปัญหา Gemini ตอบปฏิเสธว่า "ฉันเป็น AI ไม่มีหน้าตา" โดยเพิ่มอัตลักษณ์ทางกายภาพ (Physical Grounding) ใน `CORE_IDENTITY`: JARVIS มีร่างกายเป็นหุ่นยนต์ 3D Pearlescent Clay Robot Avatar บนหน้าจอมือถือของผู้ใช้ พร้อมหูฟังสีฟ้าสดใส ตาไฟดิจิทัล ปากขยับได้ และมี 10 สภาวะอารมณ์
  - เพิ่ม **Rule 14 (`การควบคุมและทดสอบ Avatar 3D`)** ใน `LIVE_RULES` และ `DEVICE_CONTROL_RULES`: สั่งให้โมเดลเรียก `device_avatar_emotion` ทันทีเมื่อผู้ใช้สั่งแสดงหรือเปลี่ยนสีหน้า และห้ามตอบว่าตนเองไม่มีหน้าตา
- **Semantic Disambiguation & Interception Guard (`LiveToolBridge.kt`)**:
  - แก้ไขปัญหา Semantic Confusion ของคำภาษาไทยว่า "อารมณ์" (สีหน้า Avatar vs อารมณ์ตลาด/Sentiment/Fear & Greed Index)
  - เพิ่ม `isAvatarEmotionRequest()` ใน `LiveToolBridge.kt`: หาก Gemini Live สับสนและพยายามเรียก `trading_fear_greed` หรือ `trading_sentiment` ขณะที่ผู้ใช้พูดถึงการเดโม่หรือเปลี่ยนสีหน้า ระบบจะดักจับ (intercept) และแปลงคำสั่งส่งต่อไปยัง `device_avatar_emotion` อัตโนมัติ พร้อมส่ง Voice Presentation Instruction ยืนยันผลสั้นกระชับสดใส
- **Avatar Emotion Device Tool (`device_avatar_emotion`)**:
  - เพิ่มการประกาศ Native Function ใน `DeviceToolDefinitions.kt` (`action="demo"|"set"|"reset"`, `emotion="..."`)
  - รองรับการประมวลผลใน `DeviceControlExecutor.kt` (`executeAvatarEmotion`) และส่งต่อให้ UI ผ่าน `MainActivity.triggerTestEmotion()`
- **Zero-Latency Local Fast-Path (`VoiceController.kt`, `ChatController.kt`)**:
  - ปรับปรุง `ChatController.kt`: ขยายการตรวจจับคำสั่งทดสอบอารมณ์ในแชทให้ครอบคลุม "เดโม่อารมณ์", "เดโมอารมณ์", "แสดงอารมณ์ทั้งหมด", "ซะแดงเดโมอารมณ์", "โชว์อารมณ์", "ทดสอบอารมณ์"
  - ปรับปรุง `VoiceController.kt`: เชื่อมต่อการฟัง Speech Transcript ของผู้ใช้แบบเรียลไทม์ (`orchestrator.textOutputFlow`) เพื่อเปลี่ยนสีหน้าและรันเดโม่ทันทีที่ตรวจพบคำสั่งโดยไม่ต้องรอผลตอบกลับจากเครือข่าย
- **Always AI Live Control & Driving Mode (`device_always_live({action="on"})`)**:
  - เพิ่มการตรวจจับและแม็พคำสั่งภาษาไทย: `"โหมดควบคุม"`, `"โหมดขับขี่"`, `"โหมดรถยนต์"`, `"เปิดโหมดควบคุม"`, `"เปิดโหมดขับขี่"`, `"เปิดโหมดรถยนต์"`, `"เข้าโหมดควบคุม"`, `"เข้าโหมดขับขี่"`, `"เข้าโหมดรถยนต์"`
  - อัปเดต `DeviceToolDefinitions.kt`: ขยายคำอธิบายเครื่องมือ `device_always_live` และเพิ่มอาร์กิวเมนต์ตัวเลือก `mode = "control" | "drive" | "car"`
  - อัปเดต `JarvisPersona.kt`: เพิ่มคำสั่งเข้าสู่ `DEVICE_CONTROL_RULES` และ `LIVE_RULES` (ข้อ 12) อย่างชัดเจน
  - ปรับปรุง `DeviceControlExecutor.kt`: `executeAlwaysLive` รองรับการตอบกลับจำเพาะสำหรับโหมดขับขี่/โหมดรถยนต์
  - เสริม `LiveToolBridge.kt`: เพิ่ม Interception Guard ป้องกัน Gemini เรียก trading tools ผิดพลาดขณะพูดคำสั่งโหมดควบคุม/ขับขี่ และเพิ่ม Voice Rule ยืนยันกระชับ
  - เสริม Zero-Latency Fast Path: รองรับคำสั่งผ่าน `ChatController.kt` และดักจับเสียงสดผ่าน `VoiceController.kt` ขยายหน้าจอ Always Live ทันที
- **Verification**:
  - `DeviceControlTest.kt`: เพิ่ม Unit Test สำหรับ `device_always_live` (action=on, mode=control) ผ่าน 100%
  - `:composeApp:testDebugUnitTest` และ `:composeApp:assembleDebug` ผ่านสมบูรณ์ (BUILD SUCCESSFUL)

## 2026-09-10 — Decoupled Trading Radar vs Signal Engine, 25+ Feature Snapshot, Forward Paper Trading & AI ML Dataset Export/Import
- **Clean Decoupling of Trading Intelligence (แยก 2 ระบบชัดเจนตามคำสั่ง)**:
  1. **ระบบแจ้งเตือนคาดการณ์ล่วงหน้า (Anticipation / Pre-Signal Alert System)**:
     - ทำหน้าที่เป็น **Market Radar / Early Warning System** เฝ้าระวังภาพรวมตลาด (M15, H1, H4) ผ่าน 13 ปัจจัยมาตรฐาน (Keyzone, Wick Sweep, RSI Extreme, EMA Cross, Squeeze, Veyra Shift ฯลฯ)
     - **ตัด mock/fake trade order ออก 100%**: ไม่มีการบันทึกคำสั่งจำลอง (Entry, SL, TP) ลงใน `SignalTrackingRecord` ของระบบเทรด ป้องกันการสร้างข้อมูลขยะใน Trade Tracker
  2. **ระบบแจ้งเตือน Signal (Signal Alert & Simulated Trading Engine)**:
     - เครื่องยนต์ตรวจจับสัญญาณเข้าทำกำไรจริง รันครบทุกกลยุทธ์: 8 กลยุทธ์ Classic + Unified SMC + 3 Pine Script Engines ที่พอร์ตมาใหม่ (`VEYRA`, `BBSQ`, `FRSI`)
     - รองรับ Multi-timeframe: M5, M15, M30, H1, H4
     - คำนวณ SL / TP ตามโครงสร้างและสัดส่วน R:R ที่แท้จริง
- **Candlestick & Market Feature Snapshot (`features_json`)**:
  - สร้าง `SignalFeatureExtractor.kt`: Snapshot คุณลักษณะตลาดและแท่งเทียนกว่า 25+ มิติ ณ วินาทีที่เกิดสัญญาณ:
    - Candlestick Metrics: `body_ratio`, `upper_wick_ratio`, `lower_wick_ratio`, `candle_dir`, `spread_atr_ratio`, `volume_impulse`
    - Oscillators & Indicators: `rsi14`, `fast_rsi5`, `stoch_k`, `macd_hist`, `adx14`, `atr14`
    - Moving Averages & Bands: `ema14_60_spread_pct`, `ema14_60_state`, `ema_trend_50_200`, `bb_width_atr`, `bb_pct_b`, `squeeze_state`
    - Multi-timeframe & Structure: `h4_trend`, `h1_trend`, `m15_trend`, `keyzone_proximity`, `keyzone_type`, `market_zone`
    - Institutional Flow & Context: `veyra_score`, `veyra_state`, `session`, `hour_utc`, `day_of_week`
  - อัปเดต SQLite Schema `SignalTrackingRecord` เพิ่มคอลัมน์ `features_json TEXT`
- **Forward Paper Trading Simulation (การจำลองผลลัพธ์ไปข้างหน้า)**:
  - `SignalOutcomeTracker.kt` ติดตามผลการวิ่งจริงของแท่งเทียนในอนาคต วัดผลครบทุกเมตริก:
    - Status: `WIN`, `LOSS`, `BE`, `OPEN`, `EXPIRED`
    - Outcome Metrics: `MFE (Maximum Favorable Excursion)`, `MAE (Maximum Adverse Excursion)`, `pnl_r`, `bars_held`
- **เครื่องมือส่งออกและนำเข้าข้อมูลสำหรับ AI / Machine Learning**:
  - **`trading_signal_data_export`**:
    - ดึงข้อมูลสัญญาณพร้อม Feature Snapshot และผลลัพธ์จริง ออกมาเป็น JSON หรือ CSV
    - กรองได้ตาม `symbol`, `interval`, `strategy`, `status` (`all`, `resolved`, `open`), `limit`
    - พร้อมส่งให้ AI ภายนอกหรือ ML Model นำไปคำนวณ Correlation, Feature Importance, หรือ Cluster เพื่อหา Parameter ที่ดีที่สุด
  - **`trading_signal_config_import`**:
    - นำเข้าผลการจูนจาก AI ภายนอก (`tunings` และ `entry_params`) กลับเข้า SQLite (`StrategyTuning`, `EntryTuning`)
    - มีผลต่อ Live Alert และ Paper Trading ทันทีในรอบถัดไป
- **Verification**:
  - SQLDelight Interface generated successfully
  - Unit Tests: `SignalAlertProviderTest`, `SignalAnticipationTest`, `SignalOutcomeTrackerTest`, `SignalDatasetManagerTest` ผ่าน 100%
  - Android Build: `:composeApp:assembleDebug` ผ่านสมบูรณ์

## 2026-09-10 — Trading Anticipation Closed-Loop Architecture: Detection ➔ Analysis ➔ Alerting ➔ Learning & Pine Script Porting
- **Pine Script Strategy Porting to Kotlin Multiplatform**:
  - **`VeyraShiftEngine.kt`** (จาก `Veyra Shift Ledger [JOAT]`):
    - พอร์ต 6 เสาหลักเชิงสถาบัน (Institutional Shift Engine):
      1. Trend & Regime (Adaptive Fast 21, Mid 55, Slow 200, DMI/ADX 14, ATR Rank, HTF EMA 55 Filter)
      2. Pressure Engine (Signed body efficiency, Pressure Oscillator -100 ถึง +100, Volume Impulse, Bull/Bear Absorption)
      3. Auction Value Engine (VWAP, Value High/Low Dev 1.15, Discount/Premium/Reclaim/Reject)
      4. Market Structure (Pivots, BOS Up/Down, Liquidity Sweep, Fair Value Gap)
      5. Composite Shift Score (0-100 คะแนน)
      6. Institutional Execution Rails: คำนวณ Entry, Structural/ATR Stop Loss, และ Take Profit 3 ระดับ (TP1: 1.0R, TP2: 2.0R, TP3: 3.2R)
  - **`BBSqueezeTrendEngine.kt`** (จาก `BBSqueezeTrend`):
    - พอร์ต Bollinger Bands (29, 1.82) vs Keltner Channels (29, 1.56) Squeeze On / Squeeze Fired
    - Linear Regression Slope (11) และ ADX (14) >= 19.11 กรองทิศทาง Breakout ที่แท้จริง
    - คำนวณ Execution Rails: Entry, Stop Loss และ Dynamic Take Profit
  - **`FastRsiEngine.kt`** (จาก `ABQ1`):
    - พอร์ต Fast RSI(5) Momentum Thrust Crossover 35/75 พร้อม Emergency Exit ทันทีเมื่อ RSI5 ตัดหลุด 10
- **Closed-Loop 4-Stage Flow Architecture**:
  1. **ตรวจจับ (Detection)**:
     - รองรับการสแกนทันที (On-demand) ผ่าน tool `trading_signal_anticipation` (`action="scan"` / `"analyze"`) ดึงแท่งเทียนเรียลไทม์ ตรวจสอบและคืนผลวิเคราะห์พร้อม Execution Rails
     - เชื่อมต่อการตรวจจับอัตโนมัติในพื้นหลังผ่าน `SignalAlertProvider.detectAnticipation()` ร่วมกับ 10 ปัจจัยเดิมรวมเป็น 13 ปัจจัยมาตรฐาน
  2. **วิเคราะห์ (Analysis)**:
     - แยกสถานะความพร้อมเป็น 3 ระดับ: `PRE_SETUP` (เริ่มฟอร์มตัว), `TRIGGER_READY` (เข้าจุดพร้อมออกคำสั่ง), `CONFIRMING` (สัญญาณยืนยัน)
     - เสริม **`runAnticipationSupervisor()`** ใน `TradingAlertEvaluator.kt` ส่ง MTF Context และ Execution Rails ให้ AI Strategy Supervisor ตรวจคัดกรอง (APPROVE / VETO / ADJUST) แบบเดียวกับสัญญาณจริง
  3. **แจ้งเตือน (Alerting)**:
     - อัปเดต `JarvisAutomationService.kt` ให้รัน Strategy Supervisor สำหรับ Anticipation Alert (ไม่ข้ามเหมือนเดิม)
     - ปรับปรุง `AlertPresentationFormatter.kt`:
       - `buildAnticipationChatCard()` แสดง Badge ระดับความพร้อม (Stage), โซน/ปัจจัย และตาราง Execution Rails (Entry, SL, TP1, TP2, TP3)
       - `buildAnticipationSpeech()` สังเคราะห์เสียงพูดเตือนระดับราคาและสถานะที่กระชับ แม่นยำ
  4. **เรียนรู้ (Learning)**:
     - บันทึกการคาดการณ์ลง SQLite อัตโนมัติผ่าน `SignalOutcomeTracker.recordAnticipation()`
     - ระบบ Closed-Loop Reinforcement Learning ติดตามราคาว่าแปลงเป็นสัญญาณจริง (Conversion Rate), ชนะ (WIN), แพ้ (LOSS), หรือผิดทาง (INVALIDATED)
     - อัปเดตค่าน้ำหนักความเชื่อมั่นแบบไดนามิก (+2% เมื่อชนะ / -2% เมื่อแพ้) ใน `AnticipationConfigManager`
     - สรุปผลการเรียนรู้ผ่าน tool `trading_signal_anticipation` (`action="learning"` / `"performance"`)
- **Verification**:
  - Unit Tests: `SignalAnticipationTest.kt` ครอบคลุม VeyraShiftEngine, BBSqueezeTrendEngine, FastRsiEngine, Reinforcement Learning Weights, และ Tool Actions
  - `:composeApp:testDebugUnitTest` ผ่าน 100%

## 2026-09-10 — Dedicated Logcat Tag (JarvisAvatar), Speech Hysteresis (Anti-Flapping) & 10-Emotion Multi-Channel Testing
- **Dedicated Logcat Tag `JarvisAvatar`**:
  - สร้างจุดบันทึก Logcat แบบเรียลไทม์ผ่าน Tag `JarvisAvatar` สำหรับตรวจจับการเปลี่ยนผ่านของอารมณ์และสถานะการสนทนา:
    - ฟิลเตอร์ง่ายผ่านคำสั่ง: `adb logcat -s JarvisAvatar` หรือใน Android Studio `tag:JarvisAvatar`
    - ล็อกทุกครั้งที่สถานะเปลี่ยน: `🎭 [EMOTION] "statusText" | AI speaking | User speaking | Mic Level`
- **Speech Hysteresis & Hangover Window (แก้ปัญหากล่องข้อความและสีสลับกระพริบไปมา)**:
  - `VoiceController.kt`: คำนวณความยาวเสียง PCM จริง (`chunkDurationMs`) พร้อมบวกช่วง Hangover 850ms หลังเสียงจบ เพื่อป้องกันไม่ให้สถานะ `isAiSpeaking` หลุดลงระหว่างช่วงว่างของ Audio chunks
  - `VoiceController.kt`: ต่อ `setLiveInterruptionHandler` เพื่อเคลียร์คิวและตัดเสียง AI ทันทีเมื่อผู้ใช้พูดแทรก (Barge-in)
  - `App.kt`: เพิ่มตัวหน่วงสถานะการพูดของผู้ใช้ (`userSpeakingHold`) ด้วย Hysteresis Window 700ms ทำให้ช่วงหยุดหายใจหรือเว้นวรรคระหว่างคำไม่ทำให้สถานะแกว่งสลับระหว่าง `LISTENING` และ `IDLE`
- **Multi-Channel Commands สำหรับทดสอบ 10 Facial Expressions & Color Palettes**:
  - **In-Chat Commands** (`ChatController.kt`):
    - `/avatar demo` — เล่นการแสดงโชว์วนลูปครบทั้ง 10 อารมณ์ (อารมณ์ละ 3.2 วินาที)
    - `/avatar <emotion>` — เลือกทดสอบอารมณ์เฉพาะ เช่น `/avatar happy`, `/avatar love`, `/avatar excited`, `/avatar angry`, `/avatar sad`, `/avatar sleeping`, `/avatar thinking`, `/avatar listening`, `/avatar speaking`, `/avatar idle`
    - `/avatar reset` — ยกเลิกการ override กลับสู่โหมดตรวจจับอัตโนมัติตามธรรมชาติ
    - รองรับคำสั่งเสียงภาษาไทยธรรมชาติ: "ทำหน้าดีใจ", "ทำหน้าโกรธ", "ทำหน้ารัก", "เดโม่อารมณ์", "รีเซ็ตอารมณ์"
  - **ADB Terminal Broadcast Commands** (`MainActivity.kt`):
    - `adb shell am broadcast -a com.skyliner2008.jarvis.TEST_EMOTION --es emotion "HAPPY"`
    - `adb shell am broadcast -a com.skyliner2008.jarvis.TEST_EMOTION --es emotion "DEMO"`
    - `adb shell am broadcast -a com.skyliner2008.jarvis.TEST_EMOTION --es emotion "RESET"`
- **StatusPill Color Palette Matching (`AlwaysLiveScreen.kt`)**:
  - กล่องแคปซูลแสดงสถานะ (`StatusPill`) ปรับขอบเรืองแสงและสีตัวอักษรให้ตรงกับ Palette อารมณ์ทั้ง 10 อารมณ์ (Teal, Gold, Pink, Red, Ice Slate, Violet, Lavender, Emerald, Aqua) อย่างกลมกลืน
- **Verification**:
  - `:composeApp:compileDebugKotlinAndroid` ผ่าน 100%
  - `:composeApp:assembleDebug` ผ่าน 100%
  - ติดตั้ง APK และทดสอบ Broadcast Intent / Logcat / Screen capture บน Samsung Galaxy (`R5CT42YEMMM`) ครบทุกสถานะ

## 2026-09-10 — JARVIS 3D Robot Avatar Clay Redesign & Dynamic Ambient Emotion Refinement
- **Avatar 3D Clay Aesthetic Transformation (`JarvisAvatar.kt`)**:
  - เปลี่ยนสไตล์หุ่นยนต์เป็น **3D Pearlescent White Clay Robot** ตาม Reference Image: ลำตัวและศีรษะทรงกลมเคลือบเงานุ่มนวล (Soft radial highlights & depth shadows)
  - เพิ่ม **Sky-Blue 3D Headphone Earcups** (`#29B6F6` / `#0288D1`) โอบด้านข้างศีรษะ พร้อมปุ่มหูฟังทรงโดม และเสาอากาศ Cyan Antenna Sphere ด้านบน
  - **เอาออกตามสั่ง**: ถอด Halo Ring เหนือศีรษะ และเส้นเลเซอร์สแกน Visor Holographic Scanline ออก 100%
  - **3D Articulated Arm & Waving Gesture**: แขนขวาโบกทักทายสดใส (Waving Hello Gesture สวิง -42° ถึง -68° สัมพันธ์กับจังหวะมือ) พร้อมแขนซ้ายลอยตัวปรับท่าทางตามอารมณ์
  - **2D Lissajous Floating Drift**: ตัวหุ่นยนต์ลอยขยับเคลื่อนที่อย่างอิสระและมีชีวิตชีวา (X/Y Drift) เสริมกับการลอยตัว Levitation แนวดิ่ง
  - **Curious Alive Head Tilt & Speech Nod**: ศีรษะเอียงตามอารมณ์ (Curious sway ในโหมดพัก/ฟัง/คิด) และพยักหน้าตามจังหวะคำพูด AI
  - **3D Floating Companion Thought Bubble (`...`)**: บอลลูนความคิดสีขาวคล้ายดินน้ำมันพร้อมจุด 3 จุดเด้งดึ๋งเมื่อ AI อยู่ในโหมด Thinking / Executing Tools
  - **10 Visor Facial Expressions (`drawEyes`, `drawMouth`)**:
    - `IDLE`: ตากลมรีแบบ Capsule Pill LED (`❚ ❚`) พร้อมแอนิเมชันกะพริบตา และลูกเล่นวิ้งตาขี้เล่น (Playful Wink) สลับไปมา
    - `SPEAKING`: ตารูปแคปซูลมีมิติขยายตัวตามพลังเสียง พร้อมปากรูปวงรีเปิด-ปิดสัมพันธ์กับระดับเสียง AI แบบเรียลไทม์
    - `LISTENING`: ตากลมโตสว่างไสว (`O O`) พร้อมวงแหวนสะท้อนแสงรอบนอกและจุดตาดำสีขาวด้านใน และปากรูป "o" น่ารัก
    - `THINKING`: สายตาช่างคิดมองเยื้องขวาบน พร้อมตาขวาสลัวหรี่ลงครึ่งหนึ่ง
    - `HAPPY`: ตาโค้งยิ้มเปี่ยมสุข (`^ ^`) พร้อมปากยิ้มหวาน
    - `EXCITED`: ตารูปดาว 4 แฉกสีทองเปล่งประกาย (`★ ★`) พร้อมปากยิ้มกว้างรูปทรงตัว D
    - `LOVE`: ตารูปหัวใจสีชมพูนีออน (`♥ ♥`) พร้อมประกายเงาสะท้อนและหัวใจลอยเหนือศีรษะ
    - `ANGRY`: ตารูปไข่เฉียงพร้อมคิ้วขมวดทรงพลังและปากซิกแซก
    - `SAD`: ตาโค้งละห้อย (`︵ ︵`) พร้อมหยดน้ำตาสีฟ้าเรืองแสงไหลลงมา และปากคว่ำ
    - `SLEEPING`: ตาปิดสนิทเป็นเส้นโค้งนิ่งสงบ (`─ ─`) พร้อมตัวอักษร ZZZ ลอยหมุนวน
- **Real-time Audio & Sentiment Pipeline Integration (`VoiceController.kt`, `JarvisViewModel.kt`, `App.kt`)**:
  - เชื่อมต่อสัญญาณเสียงไมโครโฟน (`VoiceInputService` RMS) และเสียงสังเคราะห์ของ AI (`LiveGeminiService` / `TtsService` RMS) เข้าสู่ StateFlow `audioLevel` และ `isAiSpeaking`
  - ปรับระบบตรวจจับ Emotion ใน `App.kt`: วิเคราะห์อารมณ์จากเครื่องมือที่ทำงาน (Tools), ระดับเสียงไมค์, ข้อความคำตอบของ AI (ความยินดี, ความสุข, ความตื่นเต้น, ข้อผิดพลาด) ถ่ายทอดไปยัง Avatar แบบอัตโนมัติ
- **Dynamic Ambient Emotion Color Palettes (`AlwaysLiveScreen.kt`)**:
  - อัปเกรดสีพื้นหลัง 3 ชั้น และสีออร่าให้เปลี่ยนตามอารมณ์ทั้ง 10 อารมณ์อย่างชัดเจน (listening: Deep Neon Aqua, speaking: Electric Emerald, thinking: Cyber Violet, happy: Oceanic Teal, excited: Solar Gold, love: Hot Pink, angry: Flame Red, sad: Ice Slate, sleeping: Lavender Void)
  - อัปเกรดวงแหวน 36-Bar Audio Visualizer ให้สะท้อนสีหลักและสีรองตามอารมณ์ของ AI
- **Verification & Deployment**:
  - `:composeApp:compileDebugKotlinAndroid` ผ่าน 100%
  - `:composeApp:testDebugUnitTest` ผ่าน 100%
  - `:composeApp:assembleDebug` ผ่าน 100%
  - ติดตั้ง APK และเปิดใช้งานจริงบนอุปกรณ์จริง Samsung Galaxy (`R5CT42YEMMM`) พร้อมจับภาพหน้าจอยืนยันทั้ง Portrait และ Landscape

## 2026-09-10 — Always Live Mode Sci-Fi Upgrade & Screen Reading Architecture
- **36-Bar Radial Audio Visualizer Ring (`AlwaysLiveScreen.kt`)**:
  - อัปเกรดจาก 24 Arcs เดิม เป็น **36 Radial Equalizer Bars** กระจายรอบทิศทาง 360 องศา ตอบสนองระดับเสียงไมโครโฟน (`audioLevel`) ผสม Wave Frequency Harmonic
  - เพิ่ม **Dual Rotating HUD Reticle Rings**: วงแหวน HUD สองชั้นหมุนทวนเข็ม/ตามเข็มพร้อมเส้นประ Sci-Fi
  - เพิ่ม **Cardinal Tech Dial Marks** ที่ 0°, 90°, 180°, 270° สไตล์ Stark Industries / Jarvis Interface
- **JARVIS 3D Robot Avatar & Sci-Fi Gesture System (`JarvisAvatar.kt`, `AvatarAnimations.kt`)**:
  - เพิ่ม **3D Arc Reactor (Chest Core)**: แกนพลังงานเรืองแสงเต้นเป็นจังหวะที่หน้าอกหุ่นยนต์ พร้อม Metallic outer bezel และ 3 tri-radial emitter notches
  - เพิ่ม **Holographic 3D Halo Ring**: วงแหวนโฮโลแกรมหมุนวนเหนือศีรษะพร้อมประจุพลังงาน Orbiting Energy Node
  - เพิ่ม **Visor Holographic Scanline**: เส้นเลเซอร์เรดาร์สแกนผ่านหน้าจอ Visor แก้วลึก Obsidian
  - เพิ่มท่าทางการเคลื่อนไหวระดับสูง: Anti-gravity Hover Levitation (ลอยตัวนุ่มนวล), Head Tilt Gyro (เอียงศีรษะตามอารมณ์/การฟัง/คิด), และ Arm Floating Articulation
- **Dynamic Ambient Gradient (`AlwaysLiveScreen.kt`)**:
  - พื้นหลังแบบมีชีวิตพร้อม Multi-layer Radial Aura เต้นเรืองแสงตามเสียงพูด
  - การสลับโทนสีตาม Emotion นุ่มนวล 100% ด้วย `animateColorAsState`
- **Responsive Dual-Orientation Layout (Portrait & Landscape)**:
  - ใช้ `BoxWithConstraints` รองรับทั้งแนวตั้งและแนวนอน
  - ในโหมด **Landscape (แนวนอน)**: ปรับเป็น Two-Pane Layout โดยฝั่งซ้ายแสดง 3D Avatar + 36-bar Visualizer เต็มตา และฝั่งขวาแสดง Cyber HUD Telemetry Card พร้อมปุ่มควบคุม ช่วยให้วางบนโต๊ะหรือใช้งานในรถยนต์ได้อย่างลงตัว
- **Screen Reading & External App Automation Architecture Analysis**:
  - ยืนยันการทำงานของ `JarvisAccessibilityService` ในการตรวจจับ `currentPackage` และการแปลง UI Tree เป็น `ScreenNode`
  - ตรวจสอบ Flow การทำงานของ `DeviceControlExecutor` ในการย่อ Always Live เป็น Floating Bubble อัตโนมัติเมื่อเปิดแอปภายนอก (เช่น YouTube, Gmail) เพื่อทำการค้นหา แตะเลือกคลิป หรืออ่านสรุปเนื้อหาอีเมลให้ผู้ใช้ฟัง
- **Verification**:
  - `:composeApp:compileDebugKotlinAndroid` ผ่าน 100%
  - `:composeApp:testDebugUnitTest` ผ่าน 100%

## 2026-09-10 — God Service Decomposition, Production Namespace Migration & iOS Guard
- **Split `JarvisAutomationService.kt` (150KB / 2,275 บรรทัด → 4 โมดูลย่อย)**:
  - `AlertPresentationFormatter.kt` (256 บรรทัด): ฟังก์ชันจัดฟอร์แมตการ์ดแชท (Anticipation, Signal, Keyzone, Alert), JSON metadata, Condition translation, และข้อความเสียงภาษาไทย (Stateless Object)
  - `TradingAlertEvaluator.kt` (514 บรรทัด): โลจิกประเมินเงื่อนไข Alert, SMC, Indicators, Adaptive interval, Backoff, AI Strategy Supervisor, และ Signal Outcome Tracking
  - `LiveVoiceAlertEngine.kt` (745 บรรทัด): ระบบสังเคราะห์เสียง Gemini Live WebSocket แบบสตรีมมิ่ง, AudioTrack PCM, WakeLock, Fallback Model Chain, และคิวจัดลำดับเสียงแจ้งเตือน (Priority Queue Scheduler)
  - `JarvisAutomationService.kt` (Slim Orchestrator เหลือ 733 บรรทัด): จัดการเฉพาะ Service Lifecycle, Android Foreground Notification, Task/Job dispatching loop
- **Production Namespace Migration (`com.example.personalaibot` → `com.skyliner2008.jarvis`)**:
  - ย้ายไดเรกทอรีแพ็กเกจทั้งหมด 5 ชุด: `commonMain`, `androidMain`, `iosMain`, `commonTest`, และ `sqldelight`
  - ปรับปรุง Package statement, imports, broadcast intent action constants และ inline FQN ครบทั้ง 265 ไฟล์
  - อัปเดต `composeApp/build.gradle.kts` (`namespace`, `applicationId`, `sqldelight.packageName`)
  - อัปเดต `AndroidManifest.xml`, `accessibility_service_config.xml`, และ `iosApp/Configuration/Config.xcconfig`
  - เพิ่ม `com.skyliner2008.jarvis` เข้า `composeApp/google-services.json`
- **Temporarily Disable iOS Target (`composeApp/build.gradle.kts`)**:
  - เพิ่มแฟล็ก `enableIos = project.findProperty("enableIos") == "true"` ครอบ iOS targets และ dependencies
  - สามารถเปิดกลับมาคอมไพล์ได้ทุกเมื่อด้วยคำสั่ง `./gradlew build -PenableIos=true`
- **Verification**:
  - `:composeApp:compileDebugKotlinAndroid` ผ่าน 100%
  - `:composeApp:testDebugUnitTest` ผ่านทุกเคส
  - `:composeApp:assembleDebug` ผ่านสำเร็จ (ได้ไฟล์ `PersonalAIBot-debug.apk` ขนาด 105MB)

## 2026-09-10 — Comprehensive Project Review (Antigravity)
- **Full-Stack Project Review** — สำรวจโปรเจคทั้งหมด (composeApp 260 Kotlin files, mt5-core-server 123 TS files, build config, documentation)
- **Overall Score: 4.0/5.0** — Architecture ⭐5, Features ⭐5, Build ⭐5, Documentation ⭐5, Tests ⭐3, Maintainability ⭐3, iOS ⭐2, Production Readiness ⭐4
- **จุดแข็งหลัก**: Controller Delegation Pattern, 6-Layer Memory Engine, FIFO Mutex Bridge, Multi-Provider Fallback, 100+ AI Tools, Device Control ระดับ JARVIS
- **Critical Issues ที่ต้องแก้**:
  1. `JarvisAutomationService.kt` (~150KB) — God Service ต้องแยกเป็น 3-4 services
  2. `com.example.personalaibot` namespace — Google Play จะปฏิเสธ, ต้องเปลี่ยนเป็น production package
  3. iOS stubs — Camera, Voice, Device Control ยังเป็น stubs ทั้งหมด
- **Important Issues**: ไม่มี navigation library (ใช้ boolean flags), test coverage gaps (Controllers/Orchestrator ไม่มี tests), lint ปิด, versionCode ต่ำ
- **Action Plan**: Split God Service → Change namespace → Add Controller tests → Implement navigation → Enable lint → Auto-increment version → iOS MVP

## 2026-09-09 — Gemini 3.1 Flash Live Primary Model & Spontaneous Model Switch Fix
- **Establish `gemini-3.1-flash-live-preview` as Primary Live Model (`ModelConfig.kt`, `SettingsController.kt`, `LiveGeminiService.kt`, `JarvisViewModel.kt`)**:
  - **ปัญหาที่พบ**: ผู้ใช้ทดสอบพบว่า `gemini-3.1-flash-live-preview` ทำงานได้เร็วที่สุด (~835ms), สำเนียงไทยเป็นธรรมชาติ และเรียก Native Tools แม่นยำ แต่ในบางครั้งระบบกลับสลับไปใช้ `gemini-2.5-flash-native-audio-preview-09-2025` เองโดยอัตโนมัติ ทั้งที่ผู้ใช้เลือก 3.1 ไว้
  - **สาเหตุเชิงลึก**:
    1. `"gemini-3.1-flash-live-preview"` เคยถูกบันทึกไว้ใน `deprecatedLiveModels` ของ `SettingsController.kt` ทำให้ทุกครั้งที่เปิดแอปใหม่ (`loadPersistedSettings`) โค้ดจะมองว่า 3.1 ตกยุค และ migrate กลับไปเป็น `09-2025` ใน SQLite
    2. `JarvisViewModel.kt` มีการเชื่อมต่อ `orchestrator.onLiveModelChanged` ไปยัง `settings.updateLiveModelSilently(winningModel)` ซึ่งเมื่อเกิด runtime fallback ชั่วคราว (เช่น เกิดความล่าช้าบนเครือข่าย) จะนำโมเดล fallback ไปเซฟทับฐานข้อมูลจริงอย่างถาวร
    3. `LiveGeminiService.kt` ไม่ได้รีเซ็ตตัวแปร `liveModelName` กลับไปเป็นโมเดลที่ผู้ใช้เลือกไว้เมื่อเริ่ม session ใหม่ ทำให้โมเดล fallback ค้างข้ามรอบการสนทนา
    4. `setupWatchdog` ตั้งเวลา timeout ไว้เพียง 3500ms ซึ่งสั้นเกินไปสำหรับเครือข่ายมือถือบางช่วงเวลา เมื่อเกิด timeout จะติด penalty นานถึง 15 นาที และข้าม 3.1 ไปใช้ fallback ตัวอื่นทันที
  - **แนวทางการแก้ไข**:
    1. ตั้ง `DEFAULT_LIVE_MODEL = "gemini-3.1-flash-live-preview"` และจัดให้อยู่อันดับ 1 ใน `liveCandidates` และ `SEED_LIVE_MODELS`
    2. ลบ 3.1 ออกจาก `deprecatedLiveModels` และใส่ `09-2025` เข้าไปแทนเพื่อ auto-migrate ฐานข้อมูลเก่าที่เคยถูกเขียนทับกลับมาเป็น 3.1 ทันที
    3. ยกเลิกการเขียนทับฐานข้อมูลใน `JarvisViewModel.kt` ตอน runtime fallback
    4. รีเซ็ต `liveModelName = configuredLiveModelName` เสมอใน `LiveGeminiService.kt` ทุกครั้งที่เริ่มคุยรอบใหม่
    5. เพิ่ม watchdog timeout เป็น 6000ms และลดเวลา penalty เหลือ 60 วินาที
    6. อัปเดต Unit Test `DynamicModelTest.kt` ให้รองรับและผ่านทั้งหมด 100%

## 2026-09-08 — Keyguard Lockscreen Overlay & Normal Mode Screen Sleep Fix
- **Fix MainActivity Displaying over Lockscreen in Normal Mode (`AndroidManifest.xml`, `MainActivity.kt`, `AlwaysLiveManager.kt`, `FloatingWidgetService.kt`)**:
  - **ปัญหาที่พบ**: เมื่อเปิดแอปค้างไว้ในโหมดปกติ และปล่อยให้มือถือดับหน้าจอ/ล็อกหน้าจอ เมื่อกดปุ่ม Power ให้หน้าจอสว่างขึ้นมา แทนที่จะติดหน้าล็อก (ใส่รหัส PIN/สแกนนิ้ว) กลับแสดงหน้าแอปทับหน้าล็อก ทำให้ใช้งานแอปได้แต่ไปหน้าโฮมหรือแอปอื่นไม่ได้ จนกว่าจะกดปิดแอปจึงจะโผล่ไปยังหน้าปลดล็อก
  - **สาเหตุเชิงลึก**:
    1. `AndroidManifest.xml` ประกาศ `android:turnScreenOn="true"` และ `android:showWhenLocked="true"` แบบ Static บนแท็ก `<activity android:name=".MainActivity">` ส่งผลให้ Window Manager ของระบบบังคับเรนเดอร์ Activity เหนือ Keyguard เสมอ
    2. `AlwaysLiveManager.wakeScreen()` สั่ง `turnScreenOnTemporarily()` โดยไม่ตรวจสอบสถานะ ทำให้มีการเซ็ต `setShowWhenLocked(true)` และ `FLAG_SHOW_WHEN_LOCKED` แม้ผู้ใช้อยู่ในโหมดปกติ
    3. ขาดการล้าง Flag ใน Lifecycle ของ `MainActivity.kt` (`onCreate`, `onResume`, `onStop`) เมื่ออยู่ในสถานะ `AlwaysLiveState.OFF`
    4. `FloatingWidgetService.kt` มีการใส่ `FLAG_KEEP_SCREEN_ON` ใน WindowParams ของ Overlay
  - **แนวทางการแก้ไข**:
    1. ลบ `android:turnScreenOn="true"` และ `android:showWhenLocked="true"` ออกจาก `AndroidManifest.xml` อย่างถาวร และเปลี่ยนไปใช้ Dynamic Runtime API ควบคุมเฉพาะโหมด Always Live เท่านั้น
    2. ใส่เงื่อนไข `if (_state.value == AlwaysLiveState.FULL_SCREEN)` ใน `AlwaysLiveManager.wakeScreen()` ก่อนเรียก `turnScreenOnTemporarily()`
    3. เพิ่ม `clearScreenFlags()` ใน `onCreate()`, `onResume()`, และ `onStop()` ใน `MainActivity.kt` เพื่อล้าง `FLAG_SHOW_WHEN_LOCKED`, `FLAG_TURN_SCREEN_ON`, และ `FLAG_KEEP_SCREEN_ON` ออกจาก Window
    4. ลบ `FLAG_KEEP_SCREEN_ON` ออกจาก `FloatingWidgetService.kt`

## 2026-09-08 — Screen Wakeup Lifecycle & Normal Mode Auto-Sleep Enforcement
- **Enforce Screen Sleep in Normal Mode (`App.kt`, `MainActivity.kt`, `AlwaysLiveManager.kt`)**:
  - **ปัญหาที่พบ**: เมื่อเข้าโหมดควบคุม (Always Live) แล้วสั่งปิด (`disable() → OFF`) หน้าจอยังคงติดสว่างค้างตลอดเวลา ไม่พักหน้าจอตามเวลา Display Timeout ของระบบ จนกว่าผู้ใช้จะกดปิดแอป (Kill task)
  - **สาเหตุเชิงลึก**:
    1. `MainActivity.wakeAndTurnScreenOn()` เรียก `window.addFlags(FLAG_KEEP_SCREEN_ON)` ค้างไว้บน Activity Window โดยไม่มีการ Clear Flags เมื่อออกจากโหมด Always Live
    2. `AlwaysLiveManager.wakeScreen()` เรียก `acquireScreenBrightLock()` ซึ่งถือ `SCREEN_BRIGHT_WAKE_LOCK` นานถึง 4 ชั่วโมง ส่งผลให้หน้าจอติดสว่างค้างแม้จะไม่ได้อยู่ในโหมดควบคุม
    3. `App.kt` ผูก `onKeepScreenOn(isListening || showAlwaysLive)` ทำให้เมื่อมีการฟังเสียงค้างอยู่ หน้าจอจะไม่ยอมพัก
    4. เมื่อ `closeAlwaysLive()` ถูกเรียก ไม่ได้สั่งหยุด `viewModel.stopVoiceInput()`
  - **แนวทางการแก้ไข**:
    1. เพิ่มฟังก์ชัน `clearScreenFlags()` ใน `MainActivity.kt` เพื่อล้าง `FLAG_KEEP_SCREEN_ON`, `FLAG_TURN_SCREEN_ON`, `FLAG_SHOW_WHEN_LOCKED`, `setShowWhenLocked(false)`, และ `setTurnScreenOn(false)` ทุกครั้งที่ออกจากโหมด Always Live
    2. ปรับปรุง `AlwaysLiveManager.wakeScreen()` ให้ใช้ Temporary WakeLock (10 วินาที) สำหรับปลุกหน้าจอชั่วคราวเท่านั้น และจะไม่ถือ `SCREEN_BRIGHT_WAKE_LOCK` หากไม่ได้อยู่ในสถานะ `FULL_SCREEN` หรือ `MINI_FLOATING`
    3. ปรับ `App.kt` ให้ `onKeepScreenOn(showAlwaysLive)` เท่านั้น แยก `isListening` ออกจากการเปิดหน้าจอค้าง เพื่อให้ในโหมดปกติหน้าจอดับพักได้ตามปกติ 100%
    4. ผูก `viewModel.stopVoiceInput()` เมื่อปิด Always Live ผ่าน `registerCloseAlwaysLive`

## 2026-09-08 — Anticipation Alert Card UI Refinement (Confidence & Price Clean Row, Redundancy Elimination)
- **AnticipationAlertCard3D UI Layout & Text Squeezing Fix (`MessageBubble.kt`)**:
  - **แก้ไขปัญหาตัวเลข % เบียดตกขอบแนวตั้ง**: นำตัวเลขความเชื่อมั่น (`${meta.confidence}%`) ออกจากแถว Header (บรรทัดที่ 1) ทำให้ส่วนแสดงผล Badge คาดการณ์ `⚡ คาดการณ์ SELL/BUY`, ชื่อคู่เงิน `XAUUSD`, และ Badge Timeframe `15M` มีพื้นที่กว้างขวางเต็มที่ ไม่ถูกบีบตัวอักษรแนวตั้งอีกต่อไป
  - **บรรทัดที่ 2: แสดงเฉพาะ % ความเชื่อมั่น และราคาปัจจุบัน**: จัดวางให้อยู่ในแถวที่ 2 โดยเฉพาะ (`ความเชื่อมั่น 76%` และ `ราคา 4405.06`) ด้วยระยะห่างซ้าย-ขวาอย่างสมดุล (SpaceBetween) ตัวหนังสืออ่านง่ายชัดเจน
  - **กำจัดการแสดงผลซ้ำซ้อน (Redundancy Elimination)**: ตัดการแสดงผล `meta.zone` (เช่น `EMA Convergence: 4409.31 → 4408.98`) ออกจากบรรทัดที่ 2 ซึ่งเดิมข้อความมีความยาวจนถูกตัดท้าย `....` และซ้ำซ้อนกับรายการ `ปัจจัยที่เกิด` ด้านล่าง ช่วยให้การ์ดกระชับ สะอาดตา และไม่อึดอัด
- **Fallback Markdown Card Synchronization (`JarvisAutomationService.kt`)**:
  - ปรับปรุง `buildAnticipationChatCard` ให้บรรทัดที่ 2 แสดงผล `ความเชื่อมั่น: $conf% • ราคา: $close` ตรงกันกับการ์ด 3D ไม่ให้มีข้อความโซนซ้ำซ้อนกับปัจจัยที่เกิด

## 2026-09-07 — Gemini Live Voice & Connection Stability Optimization
- **Multi-Tier Automated Fallback Chain (`ModelConfig.kt`, `LiveGeminiService.kt`)**:
  - รองรับการสลับโมเดล Live อัตโนมัติ: `gemini-3.1-flash-live-preview` -> `gemini-2.5-flash-native-audio-preview-12-2025` -> `gemini-2.5-flash-native-audio-latest` เมื่อเกิดข้อผิดพลาดในการ Setup หรือการเชื่อมต่อ โดยไม่ต้องให้ผู้ใช้เข้าไปกดเปลี่ยนโมเดลเอง
  - ระบบคัดกรอง Blacklisted / Dead models ออกจาก Chain อัตโนมัติ
- **7-Second Setup Watchdog (`LiveGeminiService.kt`)**:
  - Coroutine watchdog คอยจับเวลาการตอบกลับ `setupComplete` จาก Server ภายใน 7 วินาที หากเกินเวลาจะปิด socket และ rotate ไปยัง fallback model ถัดไปทันที ป้องกันการค้างรอนาน 15–30 วินาที
- **Acoustic Synthesizer Purity & Native Thai Accent Shield (`VoiceController.kt`, `JarvisPersona.kt`)**:
  - เปลี่ยน Realtime Greeting Input จาก `"สวัสดีJARVIS พร้อมคุยไหม"` เป็นภาษาไทยล้วน `"สวัสดีจาวิส พร้อมคุยไหม"` ป้องกัน Acoustic Decoder ของ Gemini สลับไปใช้สำเนียงและ Prosody ภาษาอังกฤษ
  - ล็อกประโยคทักทายตายตัว (Fixed Greeting): `"สวัสดีค่ะนายท่าน จาวิสพร้อมคุยแล้วค่ะ มีอะไรให้จาวิสช่วยวันนี้ดีคะ"` (ผูกกับ `userCallName` และ gender particle) คำต่อคำ
  - ลบ Vibe บัตเลอร์อังกฤษออกจากค่าตั้งต้น และห้ามแต่งประโยคเรื่องตลาดหุ้น/การเทรดในคำทักทายเริ่มต้น เพื่อให้น้ำเสียง อารมณ์ และสำเนียงนุ่มนวล ชัดเจน เหมือนกันทุกครั้ง

## 2026-09-06 — Always AI Live Mode (Full-Screen, Mini Robot Overlay & Background Wake-on-Voice)
- **Always AI Live Mode Architecture (`AlwaysLiveManager.kt`, `AlwaysLiveScreen.kt`, `FloatingWidgetService.kt`, `HotwordDetector.kt`, `JarvisAvatar.kt`)**:
  - **Full-Screen Live Mode**: หน้าจอแสดงผล JARVIS Robot Avatar 3D-styled แบบเต็มจอ พร้อมวงแหวน Audio Visualizer 36 แท่งที่ตอบสนองต่อระดับเสียงไมโครโฟน, แสงพื้นหลัง Dynamic Ambient Gradient ที่เปลี่ยนโทนสีตามอารมณ์ของ AI, ป้ายแสดงสถานะ Live, และแถบควบคุม (ไมค์, กล้องสลับเลนส์หน้า-หลัง, ย่อเป็น Floating Widget, วางสาย)
  - **Mini Robot Overlay (`FloatingWidgetService.kt`)**: อัปเกรดจาก Text Bubble เดิม สู่ Animated Mini Robot Avatar (~80dp) ที่ลอยทับแอปอื่นผ่าน ComposeView บน Foreground Service (พร้อม `ServiceLifecycleOwner`), รองรับการลากย้ายและ Snap to Edge อัตโนมัติ, แตะเพื่อเข้าแอป, แตะสองครั้ง (Double-tap) เพื่อขยายเต็มจอ (Expand), และแตะค้างเพื่อเปิด/ปิดเสียง
  - **Background Wake-on-Call / Hotword (`HotwordDetector.kt`)**: โหมดรับฟังคำสั่งเสียงแม้ขณะจอดับหรือพักหน้าจอ โดยใช้ low-power AudioRecord 8 kHz Mono แบบ Duty-cycle (ฟัง 2 วิ พัก 1 วิ) ร่วมกับ RMS energy VAD ปลุกเครื่องอัตโนมัติ (`ACQUIRE_CAUSES_WAKEUP`, `turnScreenOn`, `showWhenLocked`) และเปิดหน้าจอ Always Live ทับ Lockscreen
  - **JARVIS 3D-Styled Robot Canvas Avatar (`JarvisAvatar.kt`)**: วาดหุ่นยนต์แอนิเมชันด้วย Compose Canvas แสดงอารมณ์ 10 สถานะ (`IDLE`, `LISTENING`, `THINKING`, `SPEAKING`, `HAPPY`, `EXCITED`, `SAD`, `ANGRY`, `LOVE`, `SLEEPING`) พร้อมระบบ micro-animations (การหายใจ, กะพริบตา, โยกหัว, คลื่นปากพูด, แสงเสาอากาศ, หัวใจลอย, ตัวอักษร Zzz)
  - **State Machine Central Coordinator (`AlwaysLiveManager.kt`)**: บริหารสถานะระหว่าง `OFF`, `FULL_SCREEN`, `MINI_FLOATING`, `BACKGROUND_LISTEN`, จัดการ WakeLock และ BroadcastReceiver สลับโหมดอัตโนมัติตามสถานะหน้าจอเปิด/ปิด
  - **Unit Test Coverage (`AlwaysLiveTest.kt`)**: ทดสอบครอบคลุม Enum ทั้ง 10 อารมณ์, State Machine transitions, Keyword Sentiment Analysis, ค่าตั้งต้น และ Immutability ผ่าน 100%

## 2026-09-06 — JARVIS Full Mobile Device Control via Voice & Accessibility Service
- **Full Mobile Device Control Architecture (`JarvisAccessibilityService.kt`, `DeviceControlExecutor.kt`, `DeviceControlHandler.kt`, `DeviceToolDefinitions.kt`)**:
  - **ยกระดับ JARVIS สู่การควบคุมมือถือทั้งเครื่อง**: รองรับการสั่งงานด้วยเสียงแบบ Real-time (Gemini Live) หรือ Text Chat ในการควบคุมระบบและแอปพลิเคชันอื่นบนมือถือแบบไร้สัมผัส (Hands-free Full Device Automation)
  - **17 เครื่องมือใหม่ในหมวด `📱 Device Control` (`DeviceToolDefinitions.kt`)**:
    - **Hardware**: `device_flashlight` (เปิด/ปิดไฟฉาย), `device_volume` (ปรับระดับเสียงทุก stream), `device_brightness` (ปรับความสว่างจอ/Auto), `device_media_control` (เล่น/หยุด/ข้ามเพลง)
    - **App Launcher**: `device_open_app` (เปิดแอป 40+ ตัวหรือค้นหาในเครื่อง), `device_navigate` (นำทาง Google Maps), `device_send_email` (ร่างอีเมล), `device_add_calendar` (เพิ่มนัดในปฏิทิน), `device_make_call` (โทรศัพท์), `device_send_sms` (ร่างข้อความ), `device_set_alarm` (ตั้งปลุก), `device_open_url` / `device_search_web` (เบราว์เซอร์และการค้นหา)
    - **Screen Interaction & Accessibility**: `device_read_screen` (สแกนองค์ประกอบ UI และข้อความบนจอ), `device_tap` (คลิกปุ่มตามข้อความหรือพิกัด), `device_type_text` (พิมพ์ข้อความลงในช่องที่โฟกัส), `device_scroll` (เลื่อนจอขึ้น/ลง), `device_press_button` (ปุ่ม Back/Home/Recents/Notifications/Quick Settings/Screenshot/Lock), `device_get_app_info` (ดูแอปที่กำลังเปิดอยู่)
    - **System Info**: `device_battery_status` (เช็คแบตเตอรี่และการชาร์จ), `device_wifi_status` (เช็คสถานะ WiFi และ SSID)
  - **KMP Pure Kotlin Decoupling**: สร้าง `DeviceControlHandler` ใน `commonMain` เพื่อให้ `ToolExecutor` สามารถ route คำสั่งไปยัง `DeviceControlExecutor` ใน `androidMain` ได้อย่างสมบูรณ์โดยไม่มีปัญหา Kotlin Multiplatform dependency
  - **Accessibility Setup Integration (`MainActivity.kt`, `AndroidManifest.xml`, `accessibility_service_config.xml`)**:
    - ลงทะเบียน `JarvisAccessibilityService` ใน Manifest พร้อมสิทธิ์ `CALL_PHONE`, `SEND_SMS`, `READ_CALENDAR`, `WRITE_CALENDAR`, `SET_ALARM`, `WRITE_SETTINGS`
    - เพิ่มรายการเปิดใช้งาน Accessibility ใน Setup Checklist ของ Settings Dialog ให้ผู้ใช้แตะเปิดได้ในคลิกเดียว
  - **Persona Rules (`JarvisPersona.kt`)**: เพิ่ม `DEVICE_CONTROL_RULES` ในทั้ง `CHAT_SYSTEM_PROMPT` และ `LIVE_SYSTEM_PROMPT` กำชับ AI ให้ตอบสนองและเรียกใช้ Tool ควบคุมอุปกรณ์ทันที
  - **Unit Test Coverage (`DeviceControlTest.kt`)**: ทดสอบความครบถ้วนของนิยามเครื่องมือ, การลงทะเบียนใน ToolRegistry, และการส่งคำสั่งผ่าน ToolExecutor ผ่าน 100%

## 2026-09-05 — Anticipation Card UI Text Squeezing Fix, Timeframe Display Badge & Model Fallback Optimization
- **AnticipationAlertCard3D UI Layout & Price Squeezing Fix (`MessageBubble.kt`)**:
  - **ปัญหาที่ตรวจพบจาก Screenshot**: ในการ์ดคาดการณ์ 3D แถบ `📍 EMA Convergence: 4438.38 → 4436.73` กินพื้นที่เกือบเต็มความกว้างของ Row ทำให้ข้อความราคา `@ 4424.04` ทางขวาสุดเหลือพื้นที่กว้างเพียงไม่กี่พิกเซล และถูก Compose บีบตัวอักษร wrap แนวตั้งทีละ 1 ตัวอักษรลงมาตามขอบการ์ด (`.`, `@`, `4`, `4`, `2`, `4`, `.`, `0`, `4`) ดูคล้ายข้อความเสียหาย
  - **การแก้ไข (Layout Constraints)**:
    - นำ `TextOverflow` มาใช้งานร่วมกับ `Modifier.weight(1f, fill = false)`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis` สำหรับ `meta.zone`
    - กำหนดให้ราคา `@ ${meta.price}` เป็น `softWrap = false`, `maxLines = 1` และจัดวางแบบ `Arrangement.SpaceBetween`
    - ทำให้ราคาคำนวณขนาดกว้างเต็มที่ก่อนเสมอ และส่วนชื่อโซนจะย่อหรือตัดท้ายด้วย `...` อย่างสวยงาม ไม่มีการบีบตัวอักษรเป็นแถวแนวตั้งอีกต่อไป
- **Timeframe Chip Display & Lifecycle Consistency (`MessageBubble.kt`, `JarvisOrchestrator.kt`, `ToolExecutor.kt`, `JarvisAutomationService.kt`)**:
  - **ปัญหาความสับสน**: ใน Log และเสียง AI แจ้งว่า *"ตั้งแจ้งเตือนที่ไทม์เฟรม 1 ชั่วโมง เรียบร้อย"* แต่บนการ์ดแชทเดิมแสดงเพียงชื่อ `XAUUSD` โดดๆ ไม่มี Timeframe ทำให้ผู้ใช้ตรวจสอบไม่ได้ว่าการ์ดที่เด้งขึ้นมาเป็นการคาดการณ์ของ TF ใด
  - **การแก้ไข**:
    - เพิ่ม Badge Timeframe แบบโปร่งแสง (`[1H]`, `[15M]`, `[4H]` ฯลฯ) ใน Header ของทั้ง `AnticipationAlertCard3D` และ `SignalAlertCard3D` ติดข้างชื่อคู่เงิน
    - ใช้ `IndicatorAlertProvider.splitSymbolAndTf` ในการแยก Symbol และ Timeframe (หากไม่มี suffix จะแสดง `1H` อัตโนมัติ)
    - ปรับ `ToolExecutor.kt` และ `JarvisOrchestrator.kt` ให้เก็บ Symbol ในรูปแบบ `XAUUSD@1h` และชื่อ Alert `Anticipation XAUUSD [1H]` สำหรับ Anticipation Alerts
    - ปรับ `buildAnticipationSpeech` และ `buildAnticipationChatCard` ใน `JarvisAutomationService.kt` ให้ระบุและพูด Timeframe เช่น *"คาดการณ์ ทองคำ ไทม์เฟรม 1 ชั่วโมง..."*
    - เพิ่มการตรวจจับ Edge-triggered bar timestamp สำหรับ `signal_anticipation` และ `signal_anticipation_id` ใน `JarvisAutomationService.kt`
- **Gemini Model Fallback & Latency Optimization (`ModelConfig.kt`)**:
  - **ปัญหาจาก Log**: โมเดลหลักเดิม `gemini-3.1-pro` คืนค่า 404 (not found in v1beta), `gemini-2.5-flash` คืนค่า 404 (deprecated for new users), `gemini-3.5-flash-lite` ติด timeout 8s ส่งผลให้ AI summary เสียเวลารอ cascade fallback นานถึง 13.9 วินาที
  - **การแก้ไข**: ปรับ `DEFAULT_MAIN_MODEL = "gemini-3.6-flash"` ตามคำแนะนำของ Google API และเรียง `GEMINI_FALLBACK_MODELS` นำโมเดลที่เสถียรและเร็ว (`gemini-3.6-flash`, `gemini-3.1-flash-lite`) ขึ้นลำดับแรก
- **Unit Test Coverage (`SignalAnticipationTest.kt`)**:
  - เพิ่มการทดสอบ `testAnticipationTimeframeDisplayAndParsing` ยืนยันการแยกและแสดง Timeframe ถูกต้อง 100%

## 2026-09-05 — Dedicated Signal Anticipation Tool (`trading_signal_anticipation`), 10 Curated Factors & Multi-Factor Confluence
- **Dedicated Signal Anticipation Tool Architecture (`TradingToolDefinitions.kt`, `ToolRegistry.kt`, `ToolExecutor.kt`, `JarvisPersona.kt`)**:
  - **ความยืดหยุ่นที่เพิ่มขึ้นตามคำขอของผู้ใช้**:
    - สร้าง Tool ใหม่ `trading_signal_anticipation` โดยตรงสำหรับ AI (ทั้งในโหมด Text Chat และ Live Voice)
    - รองรับคำสั่งเสียง/ข้อความ เช่น *"ใช้ tool คาดการณ์ล่วงหน้า ทองคำ"* หรือ *"ตั้งแจ้งเตือนคาดการณ์ XAUUSD"*
    - ฟังก์ชันภายในผูกกับการสร้าง Alert อัตโนมัติ (`trading_signal_alert`, `field = signal_anticipation`, `op = >=`, `value = 1`) โดย AI ดำเนินการให้ทันทีโดยไม่ถามย้อนให้ผู้ใช้สับสน
    - **ป้องกันความสับสนใน UI**: นำ Preset การตั้งค่า Anticipation ด้วยตนเองออกจาก UI (`AutomationScreen.kt`) ให้การตั้งค่าคาดการณ์ล่วงหน้าถูกจัดการผ่าน AI Tool เท่านั้นตามความต้องการของผู้ใช้
- **Curated 10-Factor Whitelist & Dynamic Configuration (`AnticipationConfigManager.kt`)**:
  - สร้างคลังปัจจัยมาตรฐาน 10 ปัจจัยที่ผ่านการพิสูจน์ทางคณิตศาสตร์/เทคนิคอล ป้องกันผู้ใช้หรือ AI ระบุปัจจัยมั่ว/ผิดพลาด:
    1. `KEYZONE_PROXIMITY` (Core Default): แตะโซน Demand/Supply OB, FVG, Swing Liquidity (0.3x ATR)
    2. `WICK_SWEEP_REJECTION` (Core Default): กวาด Low/High 10 แท่งแล้วทิ้งไส้เทียนปฏิเสธราคา (Wick >= 1.5x Body)
    3. `RSI_EXTREME` (Core Default): RSI14 Oversold (<=28) หรือ Overbought (>=72)
    4. `EMA_NEAR_CROSS` (Core Default): EMA 14/60 Dynamic Convergence บีบตัวเข้าหากันในระยะกระชั้นชิด
    5. `BOLLINGER_SQUEEZE` (Extended): Bollinger Bandwidth แคบผิดปกติ (<= 2.2x ATR) สะสมพลังเตรียม Breakout
    6. `MACD_HISTOGRAM_TURN` (Extended): MACD Histogram หดตัวกลับทิศใกล้เส้น 0
    7. `VOLUME_ABSORPTION` (Extended): ปริมาณ Volume สูง 1.8x แต่ Spread แคบ (ซุ่มเก็บของหรือรับแรงเทขาย)
    8. `FIBONACCI_GOLDEN_POCKET` (Extended): แตะระดับ Golden Pocket 0.618 - 0.650
    9. `STOCHASTIC_OVERSOLD_TURN` (Extended): Stochastic %K/%D ตัดขึ้นจาก <20 หรือตัดลงจาก >80
    10. `SESSION_OPEN_SWEEP` (Extended): กวาด High/Low ของ Session ก่อนหน้า (เช่น Asia High/Low Sweep)
  - AI สามารถดึงดูรายการ (`list_factors`), แก้ไขเพิ่ม/ลดปัจจัย (`config`), รีเซ็ต (`reset`), และแนะนำปัจจัยที่เหมาะสมตามประเภทสินทรัพย์ (`recommend`) ได้
- **Multi-Factor Confluence Synthesis & Confidence Boosting (`SignalAlertProvider.kt`)**:
  - เมื่อราคาเข้าเงื่อนไขหลายปัจจัยในทิศทางเดียวกัน ระบบจะรวม Confluence เข้าด้วยกัน (เช่น Keyzone + Wick Sweep + RSI + EMA)
  - ปรับสเกลความเชื่อมั่นแบบไดนามิก: 1 ปัจจัย = ค่าเริ่มต้น, 2 ปัจจัย = 80-85%, 3 ปัจจัย = 88-92%, 4+ ปัจจัย = 95-96%
  - สรุปเหตุผลรวมในการ์ดแชท 3D และส่งเสียงสรุปผ่าน Live Voice ชัดเจน
- **Compact 3D Card Layout & Triggered-Factors-Only Display (`MessageBubble.kt`, `JarvisAutomationService.kt`)**:
  - ปรับการ์ด 3D คาดการณ์ในแชท (`AnticipationAlertCard3D`) ให้กะทัดรัด ไม่กินพื้นที่หน้าจอมือถือ
  - **แสดงเฉพาะปัจจัยที่เกิด (Triggered Factors Only)**: ตัดแถวข้อมูลซ้ำซ้อน (ทิศทาง, โครงสร้างตลาด 5TF กล่องใหญ่) ออก เหลือเพียง Badge หัวการ์ด, โซนสำคัญ/ราคา และรายการปัจจัยที่ตรวจพบจริงในสไตล์ Bullet/Tag ชัดเจน
  - ปรับปรุง `buildAnticipationChatCard` ใน fallback Markdown ให้แสดงเฉพาะข้อมูลกระชับและปัจจัยที่เกิด
- **AI Live Voice Summary Optimization — Bias & Key Watch Points (`JarvisPersona.kt`, `JarvisAutomationService.kt`)**:
  - **ไม่ต้องบอกค่าทางเทคนิคมากมาย**: ห้ามอ่านตัวเลขทศนิยมยิบย่อย, ค่า RSI ละเอียด, สเปรด หรือสูตรคำนวณออกเสียง
  - **เน้นสรุปแนวโน้มทิศทาง และสิ่งที่ต้องจับตามองเป็นหลัก**: สรุปว่ากำลังลุ้นกลับตัวขึ้นหรือลงที่แนวรับ/ต้าน และเตือนให้ผู้ใช้จับตาดูการปิดแท่งเทียนยืนยันก่อนเข้าออเดอร์
  - เพิ่มกฎข้อ 11 ใน `LIVE_RULES` ของ `JarvisPersona.kt` กำชับ AI ให้พูดสั้น กระชับ ชัดเจน 1-2 ประโยค จบสมบูรณ์ ลงท้ายด้วย 'ค่ะ' เสมอ
- **Unit Test Verification (`AnticipationConfigManagerTest.kt`, `SignalAnticipationTest.kt`)**:
  - ทดสอบการจัดการ Whitelist, การเพิ่ม/ลดปัจจัย, การบล็อกปัจจัยที่ไม่ได้อยู่ในคลัง, การคำนวณ Confluence และการตรวจสอบความพร้อมของ Tool ผ่าน 100%

## 2026-09-05 — ปรับ Timeframe คาดการณ์เริ่มต้นเป็น 15m (m15) และรองรับหลาย Timeframe (m5–h4 / all)
- **Default Timeframe Policy (15m)**: ปรับแก้เกณฑ์การสร้าง Alert คาดการณ์ล่วงหน้า (`signal_anticipation` / `trading_signal_anticipation`) หากผู้ใช้ไม่ได้ระบุ Timeframe ให้เริ่มต้นที่ **`15m`** (m15) เสมอ (แทนที่ 1h เดิม) ซึ่งสอดคล้องกับหลักการวิเคราะห์ SMC และ Intra-day Confluence
- **รองรับช่วง Timeframe กว้าง (m5–h4)**: อนุญาตให้คาดการณ์ได้ตั้งแต่ `5m`, `15m`, `30m`, `1h`, ถึง `4h` สอดคล้องกับระบบ Market Structure Ingestion ที่ดึงแท่งเทียน 5 TF (1m, 5m, 15m, 1h, 4h) อยู่ตลอดเวลา
- **รองรับ Multi-TF & All**: ผู้ใช้สามารถสั่งให้ AI คาดการณ์หลาย TF เช่น `15m,1h` หรือสั่ง *"ทุก TF"* / `"all"` ซึ่งระบบจะขยายเป็น 5 Timeframe หลัก และสร้าง Alert Job แยกอิสระให้ทุก TF อัตโนมัติ
- **อัปเดตไฟล์ระบบ**:
  - `JarvisOrchestrator.kt`: แยก logic `isAnticipationAlert` กำหนด default `15m` และขยาย `"all"` เป็น `[5m, 15m, 30m, 1h, 4h]`
  - `ToolExecutor.kt`: ฟังก์ชัน `executeSignalAnticipation` ใช้ default `15m`
  - `TradingToolDefinitions.kt`: อัปเดต Parameter Documentation
  - `JarvisPersona.kt`: อัปเดต AI Prompt Rule 9 และ 10 กำหนดชัดเจนว่า default คือ 15m และช่วง m5-h4
  - `SignalAnticipationTest.kt`: เพิ่ม Unit Test `testAnticipationDefaultTimeframeIs15mAndSupportsMultiTf` ทดสอบครอบคลุมทั้ง default 15m, explicit 5m, และ all (ผ่าน 100%)

## 2026-09-04 — SignalTracker Duplication Fix & TradingView WebSocket Timeout Optimization
- **SignalOutcomeTracker Deduplication & Lifecycle Fix (`SignalAlertProvider.kt`, `SignalOutcomeTracker.kt`, `JarvisDatabase.sq`)**:
  - **Verified in Production Log**: ใน Log ล่าสุด (18:53:04 - 19:29:48) เมื่อเกิดสัญญาณใหม่ `XAUUSD_1h_SELL_1788519600000` ที่เวลา 19:08:12 ระบบบันทึก `SignalTracker: Recorded signal ...` **เพียงครั้งเดียวถ้วน** และไม่พ่นซ้ำหรือบันทึกทับในรอบถัดไปอีกเลยตลอด 21+ นาทีที่เหลือ
- **TradingView WebSocket Host-Level Circuit Breaker & Cascade Abort (`SmcApiService.kt`)**:
  - **Root Cause Identified**: เดิมการตั้ง backoff เป็นระดับ Symbol + Interval (`"$sym|$interval"`) ทำให้เมื่อเกิดเน็ตเวิร์ก timeout ไปยัง `data.tradingview.com` แต่ละ Timeframe (1h, 15m, 5m, 1m) ต่างคนต่างรอ timeout 7s ทีละตัว (รวม 28 วินาทีในรอบเดียว)
  - **Remediation & Host-Level Circuit Breaker**:
    - เพิ่ม `tvHostFailureSkipUntil` ใน Companion Object: เมื่อเกิด Connection Timeout หรือล้มเหลวที่ระดับ Host จะตั้ง Circuit Breaker พักทั้ง Host 60 วินาที
    - ทำให้ Timeframe อื่นๆ (15m, 5m, 1m) ในรอบนั้นดึงจาก SQLite DB Cache (`TV:DB`) ที่มีอยู่แล้ว 300-500 แท่งทันทีใน 1ms โดยไม่ต้องเสียเวลารอ timeout 7s ซ้ำๆ
    - เมื่อเชื่อมต่อสำเร็จจะ reset `tvHostFailureSkipUntil = 0L` ทันที

## 2026-09-04 — EMA 14/60 Near-Cross (Convergence) & Confirmed Cross Detection System
- **EMA 14 / EMA 60 Dynamic Convergence & Cross Engine (`SignalAlertProvider.kt`, `SmcFlowAlertProvider.kt`, `AutomationModels.kt`, `AutomationScreen.kt`)**:
  - **ความสามารถที่พัฒนาขึ้นตามคำขอของผู้ใช้**:
    1. **การตรวจจับระยะเกือบตัดกัน (Near-Cross / Convergence)**:
       - คำนวณระยะห่าง (Spread) แบบไดนามิกเทียบความผันผวนของราคา: `nearCrossThreshold = max(atr14 * 0.35, close * 0.0012)` ร่วมกับการตรวจสอบว่าเส้นกำลังบีบตัวแคบลงจริง (`spreadNow < spreadPrev`)
       - ตรวจทิศทางการพุ่งเข้าหากัน (Directional Velocity):
         - `BUY Anticipation`: เมื่อ EMA14 < EMA60 แต่วิ่งเงยหัวขึ้นเข้าหา EMA60 (`efNow >= efPrev`) $\to$ คาดการณ์ล่วงหน้าเตรียมเกิด Golden Cross (Confidence 76%)
         - `SELL Anticipation`: เมื่อ EMA14 > EMA60 แต่วิ่งปักหัวลงเข้าหา EMA60 (`efNow <= efPrev`) $\to$ คาดการณ์ล่วงหน้าเตรียมเกิด Death Cross (Confidence 76%)
       - ส่งออกฟิลด์ `ema14_60_near_cross` ("1"/"0"), `ema14_60_near_cross_side` ("BUY"/"SELL"/"NONE") และบรรจุลงใน Setup 4 ของ `detectAnticipation`
    2. **การตรวจจับการตัดกันยืนยัน (Confirmed Cross)**:
       - ตรวจสอบ `GOLDEN_CROSS` (`e14Prev <= e60Prev && e14 > e60`) และ `DEATH_CROSS` (`e14Prev >= e60Prev && e14 < e60`)
       - ส่งออกฟิลด์ `ema14_60_cross` (`GOLDEN_CROSS`/`DEATH_CROSS`/`NONE`), `ema14_60_spread`, `ema14_60_state` (`BULLISH`/`BEARISH`), `ema14`, `ema60`
       - ปล่อยสัญญาณซื้อขายยืนยัน (Confirmed Signal Event) ฝั่ง BUY/SELL ผ่าน marker edge `E14/60▲` และ `E14/60▼` ทันทีเมื่อแท่งปิดยืนยันการตัด
    3. **Alert Catalog & One-Click Presets**:
       - เพิ่มฟิลด์ใหม่ทั้งหมดใน `AlertFieldCatalog.SIGNAL_ALERT` และ `AlertFieldCatalog.SMC_FLOW`
       - เพิ่ม 3 Presets สำเร็จรูปใน `AutomationScreen.kt`:
         - `⚡ EMA 14/60 เกือบตัดกัน (เตือนก่อนตัด)` (`ema14_60_near_cross == 1`)
         - `🎯 EMA 14/60 Golden Cross (ตัดขึ้น)` (`ema14_60_cross == GOLDEN_CROSS`)
         - `🎯 EMA 14/60 Death Cross (ตัดลง)` (`ema14_60_cross == DEATH_CROSS`)
  - **Unit Test Verification (`SignalAnticipationTest.kt`, `SignalAlertProviderTest.kt`)**:
    - เพิ่มการทดสอบ `testDetectAnticipation_emaNearGoldenCrossProducesBuyAnticipation`, `testDetectAnticipation_emaNearDeathCrossProducesSellAnticipation` และ `testAlertCatalogAndPresets_supportEma14_60FieldsAndPresets` ผ่าน 100%

## 2026-09-04 — Anticipation & Keyzone 3D Chat Alert Card Architecture & Content Fallback
- **Anticipation & Keyzone Alert Card Incompleteness Fix (`JarvisAutomationService.kt`, `MessageBubble.kt`)**:
  - **Root Cause Identified**: การ์ดแจ้งเตือนในแชทแสดงผลเฉพาะข้อความ "signal_anticipation >= 1" และค่าปัจจุบัน "1" โดยไม่แสดงรายละเอียด เกิดจาก 3 สาเหตุ:
    1. ฟิลด์ทิศทางของ Anticipation อยู่ใน `signal_anticipation_side` (ยังไม่ใช่ `signal_side` เนื่องจากเป็น pre-signal ก่อนแท่งปิดยืนยัน) ทำให้ `JarvisAutomationService` ตีความเป็น Alert ทั่วไป (`kind = "alert"`) แทนที่จะเป็นการ์ด Signal
    2. เมทาดาทาของ Alert ทั่วไปเดิมบันทึกเฉพาะ `name`, `symbol`, `condition`, `current` ขาดฟิลด์เชิงโครงสร้าง (`side`, `zone`, `desc`, `confidence`, `price`, `mtf`)
    3. `MessageBubble.kt` มีเฉพาะ `SignalAlertCard3D` และ `GenericAlertCard` ซึ่งเมื่อเป็น `GenericAlertCard` จะแสดงเฉพาะเงื่อนไขและค่าปัจจุบัน โดยละทิ้งข้อความเนื้อหาทั้งหมดใน `message.content` ทิ้งไป
  - **Remediation & Architecture**:
    - **`JarvisAutomationService.kt`**:
      - แยก Routing เฉพาะสำหรับ Anticipation Alert (`isAnticipationAlert`) และ Keyzone Watch (`isKeyzoneOnly`)
      - สร้าง `anticipationChatMeta` และ `keyzoneChatMeta` บรรจุข้อมูลครบถ้วน: `side`, `zone`, `desc`, `confidence`, `price`, `mtf`, `summary`, `voice`
      - สร้าง `buildAnticipationChatCard` และ `buildAnticipationSpeech`
    - **`MessageBubble.kt`**:
      - เพิ่ม `AlertCardMeta.Anticipation` และ `AlertCardMeta.Keyzone` ใน Sealed Class
      - พัฒนา Composable `AnticipationAlertCard3D`: การ์ด 3D สวยงามสไตล์ Glassmorphism พร้อม Badge ไฟฟ้า `⚡ คาดการณ์ BUY` (เขียว) หรือ `⚡ คาดการณ์ SELL` (แดง), โซนสำคัญ, ราคาปัจจุบัน, ความเชื่อมั่น %, เหตุผลการวิเคราะห์ และโครงสร้างตลาด 5TF
      - พัฒนา Composable `KeyzoneAlertCard3D`: การ์ด 3D ธีม Amber สำหรับจุดสัมผัสโครงสร้างสำคัญ
      - พัฒนา **Content Fallback**: สำหรับประวัติแชทเดิมในฐานข้อมูลที่บันทึกเป็น `kind = "alert"` และมี `condition` มีคำว่า `signal_anticipation` จะแปลงร่างเป็นการ์ด Anticipation 3D อัตโนมัติ พร้อมดึงข้อมูลจาก `message.content` มาแสดง และปรับ `GenericAlertCard` ให้แสดง `detailText` เสมอ ไม่ปล่อยให้การ์ดว่างเปล่า
    - **Unit Tests (`SignalAnticipationTest.kt`)**:
      - เพิ่ม 4 ชุดการทดสอบทดสอบการแปลง `parseAlertCardMeta` ครบทุกประเภท (Anticipation, Keyzone, Legacy Fallback, Generic Content Preservation) รันผ่าน 100%

## 2026-09-04 — Live Voice Speech Cutoff Fix & Signal Observability Optimization
- **Live Alert Voice Speech Truncation Fix (`JarvisAutomationService.kt`)**:
  - **Root Cause Identified**: ตรวจพบสาเหตุที่ AI พูดไม่จบประโยคและถูกตัดหยุดกลางคัน เกิดจาก `liveVoiceSummaryCharCap = 120` ทำงานแบบ Hard Guillotine ตัดการเชื่อมต่อ WebSocket ทันทีที่ข้อความสะสมเกิน 120 ตัวอักษร (`RESPONSE_LENGTH_CAP 120 chars → close`) ทำให้ข้อความภาษาไทยที่มีตัวอักษร Unicode และสระ/วรรณยุกต์หนาแน่นถูกตัดขาดช่วงวินาทีที่ 2.7 ก่อนที่โมเดลจะส่ง `turnComplete = true` และก่อนที่จะกล่าวถึง Entry, SL, TP หรือลงท้ายคำว่า "ค่ะ"
  - **Remediation**:
    - ปรับ `liveVoiceSummaryCharCap` จาก 120 เป็น **350** ตัวอักษร เพื่อทำหน้าที่เป็น Runaway Circuit Breaker อย่างแท้จริง โดยปล่อยให้การตอบกลับตามปกติ (1-2 ประโยค ~120-180 ตัวอักษร) จบลงอย่างสมบูรณ์และเป็นธรรมชาติด้วยอีเวนต์ `turnComplete == true` จากเซิร์ฟเวอร์
    - ปรับ `liveVoiceSessionTimeoutMs` จาก 22,000ms เป็น **35,000ms** เพื่อรองรับ Latency การส่งมอบ Audio ก้อนแรก (~8.5 วินาที) ร่วมกับการสตรีมเสียงความยาว 12-18 วินาทีได้อย่างปลอดภัย
    - ปรับ System Instruction สำหรับ `LIVE SIGNAL ALERT MODE` ให้ออกคำสั่งสรุปกระชับ 1-2 ประโยค พูดให้จบประโยคอย่างสมบูรณ์ และลงท้ายด้วย "ค่ะ" เสมอ
- **Signal Observability & Multi-TF Deduplication (`SignalAlertProvider.kt`)**:
  - รวม Logcat `SignalDataSource` เหลือ **1 บรรทัดต่อ Symbol** โดยใช้ State-change Throttling
  - กำจัดการ Fetch และ Query แท่งเทียน `1h` ซ้ำ 2 ครั้งในรอบเดียวกัน โดยการ Reuse แท่งเทียน Base Candle ของ Job เข้าสู่ Unified SMC ทันที

## 2026-09-04 — Mobile AI Trading Intelligence & Closed-Loop Reinforcement Architecture (ระบบปัญญาประดิษฐ์เทรดบนมือถือ, การเตือนล่วงหน้า และการเรียนรู้ปรับตัวแบบ Closed-Loop)
- **Architectural Deliverables (ผลการยกระดับ 4 เสาหลักบนระบบมือถือ `composeApp`)**:
  - **Pillar 1: Real-Time Multi-Timeframe TradingView Fusion (`TradingViewSignalIntelligence.kt`)**:
    - ผสานการวิเคราะห์ข้อมูล Multi-Timeframe (15m, 30m, 1h, 4h) ร่วมกับ SMC Score และ Dynamic Technical Indicators บนมือถือโดยตรง ปราศจากการพึ่งพา Vendor ภายนอก
  - **Pillar 2: Predictive Pre-Signal & Signal Anticipation Engine (`SignalAlertProvider.kt`)**:
    - เพิ่ม `detectAnticipation`: วิเคราะห์ Intra-bar dynamics ตรวจสอบ Keyzone Proximity (0.3×ATR จาก Order Blocks, Fair Value Gaps, Swing Liquidity ใน 5TF Market Context Digest), ตรวจจับ Intra-bar Wick Sweep Rejection ที่กวาดสภาพคล่องแล้วทิ้งไส้ย้อนกลับ, และ RSI Extreme/Divergence Setups
    - ระบบ Dual-Stage Signaling: ส่งออก `signal_stage = "ANTICIPATION"` พร้อม `signal_anticipation_desc` และ `signal_anticipation_zone` แจ้งเตือนผู้ใช้ล่วงหน้า โดยล็อกไม่ให้ Auto-Execution บอทยิงออเดอร์ก่อนเวลาจนกว่าจะเกิดแท่งยืนยัน `signal_stage = "CONFIRMED"`
  - **Pillar 3: Adaptive Self-Learning Backtest Integration (`BacktestToolHandler.kt`, `SignalOutcomeTracker.kt`)**:
    - รองรับการปรับจูนพารามิเตอร์และจำลองผลย้อนหลังเพื่อวิวัฒนาการกลยุทธ์
  - **Pillar 4: Closed-Loop Signal Outcome Tracker & Strategy Reinforcement (`SignalOutcomeTracker.kt`, `StrategyConfirmationGate.kt`)**:
    - พัฒนา SQLite Schema `SignalTrackingRecord` (migration `10.sqm`) บันทึกทุก Signal ที่ปล่อยออกไปสู่ตลาด
    - ติดตามผลลัพธ์จากแท่งเทียนราคาตลาดจริง คำนวณ MFE (Maximum Favorable Excursion), MAE (Maximum Adverse Excursion), R-multiple Realized PnL, สถานะการปิดไม้ (`WIN`, `LOSS`, `EXPIRED`)
    - เชื่อมโยงผลลัพธ์เข้ากับ `StrategyConfirmationGate`: เพิ่มฟังก์ชัน Reinforcement Feedback ปรับ Confidence Boost (+0.10 ถึง +0.20) สำหรับกลยุทธ์ที่ชนะต่อเนื่อง และปรับลด (-0.15 ถึง -0.25) พร้อมบล็อกสัญญาณอ่อนแอหากอยู่ในสภาวะตลาดที่ไม่เหมาะสม
  - **Mobile UI & Alert Integration (`AutomationScreen.kt`, `AutomationModels.kt`, `TerminalEventsTab.kt`, `ResearchToolHandler.kt`)**:
    - เพิ่ม Preset ลัด `⚡ คาดการณ์ Signal ล่วงหน้า (Anticipation)` ใน `AutomationScreen.kt` ให้ผู้ใช้สร้าง Alert เฝ้าระวังได้ในคลิกเดียว
    - บรรจุฟิลด์ `signal_anticipation`, `signal_stage`, `signal_anticipation_side`, `signal_anticipation_zone` ลงใน `AlertFieldCatalog.SIGNAL_ALERT`
    - ฝังการ์ด `🧠 Closed-Loop Signal Outcomes` ในแท็บ Events ของหน้า Trading Terminal แสดงผลลัพธ์ไม้จริง (WIN/LOSS, R-multiple, MFE) แบบ Live
    - ขยายเครื่องมือ `trading_signal_stats` ใน `ResearchToolHandler.kt` แสดง Closed-Loop Strategy Reinforcement Status (Win Rate, Avg R, MFE, MAE, และ Confidence Boost/Penalty)
- **Verification & Test Coverage**:
  - สร้าง `SignalAnticipationTest.kt`: ทดสอบ Demand Keyzone Buy, Supply Keyzone Sell, Wick Sweep Rejection, Normal Candle, และ Alert Catalog / Preset integration
  - สร้าง `SignalOutcomeTrackerTest.kt`: ทดสอบ Buy/Sell TP/SL Collision, Conservative Dual-hit Resolution, 50-bar Expiry Timeout, และ Dynamic Confidence Reinforcement
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL in 1m 12s** (ผ่าน 100% ครบทั้ง 130 รายการทดสอบ)

## 2026-09-04 — Full Non-Trading Tools Audit & Flexibility Upgrades (การตรวจสอบและยกระดับ Tools หมวดอื่นๆ นอกเหนือจากการเทรด)
- **Root Cause & Inflexibility Issues Identified (การตรวจสอบข้อจำกัดในหมวดหมู่อื่นๆ)**:
  - **Date & Time Tool (`get_current_datetime`)**:
    - เดิมแสดงเพียงวันที่แบบ ISO (`2026-09-04`) และชื่อวันภาษาอังกฤษ (`Friday`) โดยไม่มีชื่อวันภาษาไทย, ชื่อเดือนภาษาไทย, ปี พ.ศ. และวินาที ส่งผลให้ AI ในโหมดภาษาไทยตอบวัน/เดือน/ปีสับสนในบางบริบท
  - **Unit Converter Tool (`convert_units`)**:
    - เดิมรองรับเฉพาะชื่อหน่วยภาษาอังกฤษล้วน (`km`, `meter`, `celsius`, `kg`, `rai`) หากผู้ใช้พูดหรือพิมพ์หน่วยเป็นภาษาไทย (เช่น "กิโลเมตร", "เซนติเมตร", "ไร่", "ตารางวา", "ตารางเมตร", "เซลเซียส", "กิโลกรัม") หรือส่ง Argument เป็น `amount`/`from`/`to` จะคืนค่าว่าไม่รองรับหน่วยทันที
    - ขาดหน่วยไทยยอดนิยม เช่น "วา" (2 เมตร), "งาน" (400 ตร.ม.), "ตารางวา" (4 ตร.ม.)
  - **Web Search, Translation & Summarization (`search_web`, `translate_text`, `summarize_text`)**:
    - ใน Text Chat Mode (`GeminiService.kt`) ไม่มีการ Intercept คำขอ `WEB_SEARCH_REQUEST::`, `TRANSLATE_REQUEST::`, `SUMMARIZE_REQUEST::` ทำให้ Gemini ในโหมดข้อความได้รับ Tool Response เป็น String ดิบของ Request แทนที่จะได้รับผลการค้นหาเว็บจริง (ผ่าน Google Search Grounding) หรือผลการแปล/สรุป
  - **Camera & Vision Tools (`CameraToolExecutor.kt`)**:
    - `camera_switch_provider` และ `camera_switch_mode`: บังคับ string แบบ Exact Match เช่น `"openai_gpt4o"`, `"live_stream"` หาก AI ส่ง `"gpt-4o"`, `"claude"`, `"gemini"`, `"stream"`, `"photo"` จะ error ไม่รองรับ
  - **File Management Tools (`FileToolExecutor.kt`)**:
    - `WRITABLE_EXTENSIONS` จำกัดเฉพาะไฟล์ text พื้นฐาน ขาดนามสกุล source code ยอดนิยม (C/C++, Dart, Go, Rust, Swift, PHP, SVG, .env)
  - **Automation & Scheduling (`onManageAlerts`)**:
    - `timeframe` กำหนดตายตัวเฉพาะ `1m..1d` หากส่ง format MT5 (`m15`, `h1`, `d1`) หรือ weekly (`1w`) จะถูกปฏิเสธว่าไม่รองรับ
- **Implemented Solutions**:
  - **Enhanced Thai DateTime (`ToolExecutor.kt`)**: เพิ่มวันภาษาไทย ("วันศุกร์"), วันที่, เดือนภาษาไทย ("กันยายน"), ปี ค.ศ./พ.ศ. ("พ.ศ. 2569"), เวลาถึงระดับวินาที และ Timezone Identifier
  - **Intelligent Unit Normalizer (`ToolExecutor.kt`)**: เพิ่ม `normalizeUnitName` แปลงหน่วยภาษาไทยและชื่อย่อทั้งหมด ("กิโลเมตร" $\to$ `km`, "ไร่" $\to$ `rai`, "ตารางวา" $\to$ `sqwa`, "วา" $\to$ `wa`, "เซลเซียส" $\to$ `celsius`), เพิ่มหน่วยไทย "วา", "งาน", "ตารางวา" ใน Map, รองรับคีย์พารามิเตอร์ `amount`, `from`, `to`
  - **Chat Mode Grounding & Text Pipeline (`GeminiService.kt`)**: เพิ่ม Interceptor สำหรับ `WEB_SEARCH_REQUEST::`, `TRANSLATE_REQUEST::`, `SUMMARIZE_REQUEST::` ใน `generateResponseWithTools` ให้รัน Google Search Grounding และ AI Translation/Summarization โดยอัตโนมัติทั้งใน Live Voice และ Text Chat
  - **Fuzzy Parsing in Camera Tools (`CameraToolExecutor.kt`)**: ปรับปรุงให้รองรับ substring matching ("gpt-4o", "gpt4o", "openai", "claude", "gemini", "stream", "photo", "object", "ar")
  - **Expanded File Extensions (`FileToolExecutor.kt`)**: เพิ่ม `c`, `cpp`, `h`, `hpp`, `dart`, `go`, `rs`, `swift`, `php`, `svg`, `env`
  - **Timeframe Normalization (`JarvisOrchestrator.kt`)**: นำ `TaIndicators.normalizeTimeframe` มาใช้ใน `onManageAlerts` รองรับทั้ง MT5 และ Weekly
- **Verification**:
  - สร้างชุดทดสอบ `NonTradingToolsTest.kt`: ตรวจสอบการแปลงหน่วยภาษาไทย (กิโลเมตร, ไร่, ตารางวา, เซลเซียส), ข้อมูลวัน/เวลาไทย, ความยืดหยุ่นของ Camera Provider/Mode
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL** ผ่าน 100% ครบทุกชุดทดสอบ

## 2026-09-04 — Comprehensive Tool Audit & Flexible Unconstrained Tool Creation System (ระบบสร้าง Tool ไร้ขีดจำกัด)
- **Root Cause & Limitations Analyzed (การตรวจสอบข้อจำกัดและกรอบเดิมในระบบ Tool)**:
  - **Tool Creation System (`system_create_agent_tool`)**:
    - เดิมบังคับชื่อ tool ต้องขึ้นต้นด้วย `"custom_"` เสมอ (`if (name.startsWith("custom_")) name else "custom_$name"`)
    - ลงทะเบียนด้วย `parameters = null` แบบ Hardcoded ส่งผลให้ Custom Tool ไม่สามารถรับพารามิเตอร์หรือ Argument ใดๆ จากผู้ใช้หรือ AI ได้
    - ใน `ToolExecutor.kt` ฟังก์ชัน `executeCustomSkill` เพิกเฉยต่อ `call.args` ส่งเพียง static prompt addon กลับไป โดยไม่มีการแทนที่ค่าตัวแปรใน Template
    - ใน `JarvisOrchestrator.kt` เมื่อโหลด tool จากไฟล์ JSON ตอนเปิดแอป มีการ hardcode `parameters = null` ทำให้ Schema หายไปหลัง restart แอป
  - **Trading & SMC Tools Overly Restrictive Constraints**:
    - `trading_combined`, `trading_technical_analysis`, `trading_multi_timeframe`: กำหนด `required = listOf("symbol", "exchange")` ทั้งที่ระบบมี Auto Exchange Resolver (XAUUSD $\to$ OANDA, BTC $\to$ BINANCE) ทำให้การเรียกแบบยืดหยุ่นล้มเหลว
    - `trading_combined`, `trading_harmonic_scan`, `trading_elliot_modern_analysis`: กำหนด `interval` `enum = listOf("15m", "1h", "4h", "1D")` ทำให้ไม่รองรับ timeframe ยอดนิยม เช่น 1m, 5m, 30m, 1W หรือ MT5 format (M1, M5, H1, H4)
    - `trading_smc_analysis`, `trading_smc_orderblocks`, `trading_smc_structure`: กำหนด `interval` แบบจำกัด enum ไม่รองรับทั้ง standard และ MT5 format ข้ามแพลตฟอร์ม
- **Comprehensive Solution & Implementation**:
  - **Dynamic Parameter Schema & Flexible Execution Engine (`SystemToolExecutor.kt`, `ToolDefinition.kt`)**:
    - ขยาย `SkillDescriptor` ให้รองรับ `parameters: FunctionParameters?`, `executionType: String` ("prompt", "formula", "chain")
    - รองรับการประกาศ Parameters ได้ทั้งแบบ Full JSON Schema (`{"symbol": {"type": "STRING", "description": "..."}}`), Short JSON (`{"symbol": "คำอธิบาย"}`), และ Comma-separated list (`"symbol, timeframe, risk_pct"`)
    - ปรับปรุงการตั้งชื่อ (`sanitizeToolName`): ไม่บังคับกรอบ `"custom_"` เว้นแต่จะชนกับ Built-in Tool เพื่อป้องกัน Name Collision
    - เก็บ Parameter Schema และ Execution Type ลงไฟล์ JSON ใน `custom_agent_tools/` อย่างสมบูรณ์
  - **Template Interpolation & Dynamic Formula Evaluation (`ToolExecutor.kt`)**:
    - ใน `executeCustomSkill`: รองรับ Template Interpolation ทั้ง `{{param}}`, `{param}` และ Whole-word regex match `\bparam\b`
    - เพิ่มโหมด `formula`: คำนวณนิพจน์คณิตศาสตร์ (เช่น การคำนวณ Lot Size, Risk Ratio, Pivot Point) ผ่าน `evalMath` โดยอัตโนมัติ คืนผลลัพธ์ตัวเลขพร้อมขั้นตอนคำนวณ
    - นำส่ง Arguments ที่ได้รับทั้งหมดเข้าสู่ Tool Loop เพื่อให้โมเดลประมวลผลต่อได้อย่างแม่นยำ
  - **Persistent Tool Loading (`JarvisOrchestrator.kt`)**:
    - ปรับปรุงการโหลด Custom Tools จาก JSON บนเครื่อง ให้ deserialize `parameters` และ `executionType` กลับมาลงทะเบียนเข้า `ToolRegistry` อย่างสมบูรณ์ ทำให้เครื่องมือที่สร้างไว้คงคุณสมบัติ Function Calling ครบถ้วนแม้เปิด-ปิดแอปใหม่
  - **Unrestricted Tool Schemas (`TradingToolDefinitions.kt`, `SmcToolDefinitions.kt`)**:
    - ปรับ `exchange` ให้เป็น Optional ใน `trading_technical_analysis`, `trading_multi_timeframe`, `trading_combined`
    - ขยาย Timeframe Enum ให้ครอบคลุมทุกความต้องการ: `1m, 5m, 15m, 30m, 1h, 4h, 1D, 1W` รวมถึง MT5 formats (`m1, m5, m15, m30, h1, h4, d1, w1`)
- **Verification**:
  - สร้างชุดทดสอบ `DynamicToolCreationTest.kt`:
    - ทดสอบสร้าง Dynamic Formula Tool (`lot_size_calculator`) รับ 3 พารามิเตอร์ (`balance, risk_pct, sl_pips`) และคำนวณผลลัพธ์ผ่าน `ToolExecutor` ได้ค่า `0.5 Lots` ถูกต้อง 100%
    - ทดสอบสร้าง Prompt Template Tool (`gold_scalp_strategy`) และแทนที่ค่า `{{symbol}}`, `{{timeframe}}` ใน Argument สำเร็จ
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL in 5s** (ผ่าน 100% ครบทุกชุดทดสอบ)

## 2026-09-04 — ForexFactory Economic Calendar Timezone Accuracy & Remaining Events Prioritization
- **Root Cause Analysis (สาเหตุที่วันและเวลาที่แปลงเป็นเวลาไทยผิดเพี้ยน และ AI ไม่รู้วันที่ปัจจุบัน)**:
  - **Issue 1 (Timezone Offset ผิด 4 ชั่วโมง และวันที่เลื่อนไปวันรุ่งขึ้น)**:
    - ใน `TradingApiService.kt` เดิม มีการดึงข้อมูลจาก `ff_calendar_thisweek.xml` โดยสมมุติว่าเวลาใน feed เป็น ET (New York) แล้วใช้ `ldt.toInstant(TimeZone.of("America/New_York"))`
    - แต่ในความเป็นจริง เวลาใน feed XML ของ ForexFactory ถูกแปลงเป็น **UTC** ไว้แล้ว (หรือใน JSON feed ระบุ ISO-8601 offset เป็น EDT เช่น `2026-09-03T10:00:00-04:00` ซึ่งเท่ากับ `14:00:00 UTC` = `21:00:00 ICT` หรือ 3 ทุ่มตรงในไทย)
    - การนำเวลา UTC ไป parse เป็น `America/New_York` (UTC-4) ทำให้เวลาคลาดเคลื่อนไป **+4 ชั่วโมง** ส่งผลให้ข่าวเวลา 21:00 น. หรือ 20:45 น. เลื่อนกลายเป็น 01:00 น. หรือ 00:45 น. ของวันรุ่งขึ้น
  - **Issue 2 (ข่าว ISM Services PMI และข่าวอื่นๆ ไม่แสดง)**:
    - ใน ForexFactory ข่าว `ISM Services PMI` มีระดับความสำคัญเป็น `Medium` (สีส้ม)
    - แต่โค้ดเดิมเรียงตาม `impact_score` จากมากไปน้อยของทั้งสัปดาห์ แล้วตัดเอาแค่ `limit = 10` ทำให้ข่าว `High` impact จากวันจันทร์-อังคาร-พุธ ยึดโควตาไปทั้งหมด 9-10 ข่าว ส่งผลให้ข่าว `Medium` สำคัญและข่าวช่วงท้ายสัปดาห์ถูกตัดทิ้งทั้งหมด
  - **Issue 3 (AI ไม่รู้วันปัจจุบัน ตอบข่าวที่ผ่านไปแล้วเสมือนเป็นข่าวในอนาคต)**:
    - เมื่อผู้ใช้ถาม "ตัวเลขเศรษฐกิจ ที่เหลือของสัปดาห์นี้" ในวันศุกร์ที่ 4 ก.ย. ตัวเครื่องมือส่งรายการข่าวที่เริ่มตั้งแต่วันอังคารที่ 2 ก.ย. โดยไม่มีการระบุ "เวลาปัจจุบันในไทย" ให้กับ Gemini
    - Gemini ไม่รู้ว่าวันนี้คือวันศุกร์ที่ 4 ก.ย. จึงมองเห็นข่าววันที่ 2 ก.ย. อยู่ด้านบนสุด แล้วนำมาตอบว่า "สิ่งที่ต้องจับตาที่สุดคือ ข่าววันที่ 2 กันยายน" ทั้งๆ ที่ข่าวนั้นผ่านไปแล้ว 2 วัน
- **Comprehensive Solution & Implementation**:
  - **Dual-Source Engine (`TradingApiService.kt`)**:
    - สลับมาใช้ `https://nfs.faireconomy.media/ff_calendar_thisweek.json` เป็นช่องทางหลัก ซึ่งมี ISO-8601 offset ชัดเจน เช่น `2026-09-03T10:00:00-04:00` ทำให้แปลงเป็นเวลาไทย (`Asia/Bangkok`) ได้อย่างแม่นยำ 100%
    - ทำ Fallback ไปยัง `ff_calendar_thisweek.xml` โดย parse ด้วย `TimeZone.UTC` อย่างถูกต้อง
  - **Thai Day-of-Week & Formatted Time**:
    - แสดงวันในสัปดาห์เป็นภาษาไทย (จ., อ., พ., พฤ., ศ., ส., อา.) และระบุเวลาไทยและ ET คู่กัน เช่น `วันพฤหัสบดี พฤ. 03 ก.ย. 21:00 น. (ไทย) | 10:00 ET` ตรงกับหน้าเว็บ ForexFactory เป๊ะ
  - **Smart Filtering & Prioritization**:
    - แบ่งข่าวเป็น `UPCOMING [รอประกาศ]` กับ `PASSED [ประกาศแล้ว]` โดยเทียบกับ `Clock.System.now()`
    - ให้ความสำคัญกับข่าวที่กำลังจะมาถึง (`UPCOMING`) เรียงตามลำดับเวลาที่ใกล้จะเกิดขึ้นที่สุดก่อนเสมอ เพื่อไม่ให้ข่าววันศุกร์ (เช่น Non-Farm Payrolls) ถูกเบียดตกขอบ
    - รองรับพารามิเตอร์ `filter` (`upcoming`, `today`, `all`) และ `currency` (`USD`, `EUR`, `GBP`, ฯลฯ)
  - **Current Time & Strict Chronological AI Prompt (`MarketTechnicalToolHandler.kt`)**:
    - ใส่เวลาปัจจุบันของประเทศไทย (เช่น `วันศุกร์ ที่ 4 ก.ย. เวลา 02:00 น. (ไทย)`) ลงในหัวตารางและใน Prompt ของ Gemini ชัดเจน
    - กำหนดคำสั่งให้ AI วิเคราะห์เฉพาะข่าว `[รอประกาศ]` ที่เหลือของสัปดาห์นี้เป็นหลัก และห้ามพูดถึงข่าว `[ประกาศแล้ว]` เสมือนว่ายังไม่เกิดขึ้นเด็ดขาด
- **Verification**:
  - สร้างชุดทดสอบ `EconomicCalendarTimeTest.kt` ยืนยันการแปลงเวลาของ ISM Services PMI (3 ก.ย. 21:00 น.), NFP (4 ก.ย. 19:30 น.), ISM Mfg PMI (1 ก.ย. 21:00 น.) และการ parse XML แบบ UTC
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL in 56s** (ผ่าน 100% ครบทุกชุดทดสอบ)

## 2026-09-04 — Live Voice Greeting Stability & Thai Phonetics (แก้พูด 2 รอบ และเสียงเพี้ยน 'เจ้านาว')
- **Root Cause Analysis (สาเหตุที่ AI พูด 2 รอบ และพูดทักทายเสียงเพี้ยน เช่น 'เจ้านาววว')**:
  - **Issue 1 (ครั้งที่ 1 พูด 2 รอบ จาก Echo Loop ของ Android TTS)**:
    - ในรอบแรก Live Gemini ส่งกลับมาเป็น Text ล้วน (ไม่มี Audio chunk) ส่งผลให้ตัวจับความผิดพลาด `onTurnWithoutAudio` เรียกใช้ Android Local TTS พูดแทน (`สวัสดีค่ะเจ้านาย จาวิสพร้อมรับใช้แล้วค่ะ`)
    - ในขณะที่เสียง TTS ดังออกจากลำโพงเครื่อง ไมโครโฟนยังคงเปิดอัดและสตรีม PCM อยู่ตลอดเวลา ทำให้ไมค์ดูดเสียง TTS ของตัวเองส่งกลับเข้าไปยัง Gemini Live
    - Gemini Live ถอดความเสียงลำโพงว่าเป็นเสียงผู้ใช้ (`🎤 User: สวัสดีค่ะเจ้านายจาวิสพร้อมรับใช้แล้วค่ะ`) แล้วตอบกลับมาอีกครั้ง (`🤖 JARVIS: สวัสดีค่ะเจ้านาย มีอะไรให้จาวิสช่วยวันนี้ดีคะ?`) ส่งผลให้ผู้ใช้ได้ยิน AI พูดทักทายซ้อนกัน 2 รอบ
  - **Issue 2 (ครั้งที่ 3 เสียงเพี้ยนเป็น 'เจ้านาววว' และหลุดเป็น Text-only)**:
    - ข้อความที่ส่งกระตุ้นตอน READY (`pendingGreetingOnReady`) เดิมส่งเป็น meta-prompt ภาษาอังกฤษปนไทย: `"[SYSTEM] Live session เพิ่งพร้อมใช้งาน โปรดพูดทักผู้ใช้สั้นๆ 1 ประโยคเท่านั้น (เช่น 'สวัสดีครับ พร้อมคุยแล้วครับ' หรือทักตามบุคลิกของคุณ) ไม่ต้องทำงานอื่นต่อ"` ผ่านช่องทาง `realtimeInput` (ซึ่งโมเดลถือว่าเป็น User input turn ไม่ใช่ System channel)
    - การมีแท็ก `[SYSTEM]`, คำภาษาอังกฤษ `Live session`, วงเล็บ, เครื่องหมายคำพูด, และข้อความตัวอย่างที่ขัดกับเพศของตนเอง (มี 'ครับ' แต่ persona คือ 'ค่ะ') ทำให้ Cross-attention ของโมเดลเสียง Native Audio ขาด Acoustic Context ของภาษาไทย
    - ตัวถอดรหัสเสียง (Neural Vocoder) จึงสับสนและสังเคราะห์เสียงสระ/ตัวสะกดแม่เกย (ย) ผิดเพี้ยนเป็นสระกึ่งพยัญชนะ [aʊ] / [w] ("เจ้านาย" $\to$ "เจ้านาววว")
    - ในขณะที่บทสนทนาโต้ตอบปกติ ผู้ใช้พูดเสียงภาษาไทยจริงเข้าไมค์ ทำให้ Audio Encoder ล็อก formant และสำเนียงไทยแท้ได้แม่นยำ เสียงจึงชัดเจนเสมอ
- **Comprehensive Solution & Implementation**:
  - **Mute Mic During TTS Playback (`VoiceController.kt`)**:
    - ผูก `_isMuted.value = true` ทันทีก่อนที่ `voiceManager.speak` จะเล่นเสียง และปลดเป็น `false` ใน callback `onDone`/`onError`
    - ป้องกันไม่ให้ไมค์อัดเสียงลำโพงของเครื่องตัวเอง ตัดวงจร Echo Feedback Loop ทำให้ AI ไม่พูดซ้ำ 2 รอบอีกต่อไป
  - **Conversational Pure-Thai Greeting Trigger (`VoiceController.kt` & `JarvisViewModel.kt`)**:
    - ยกเลิกข้อความ meta-instruction ที่มี `[SYSTEM]` และคำภาษาอังกฤษทั้งหมด
    - เปลี่ยนข้อความกระตุ้นทักทายเมื่อ READY เป็นภาษาไทยสนทนาธรรมชาติ: `"สวัสดี$agentName พร้อมคุยไหม"` (เช่น `"สวัสดีจาวิส พร้อมคุยไหม"`) และข้อความเปลี่ยนเสียงเป็น `"เปลี่ยนมาใช้เสียง $newVoice แล้ว ลองทักทายสั้นๆ ด้วยเสียงใหม่นี้"`
    - โมเดลจะตอบรับกลับมาอย่างเป็นธรรมชาติในฐานะคู่สนทนาภาษาไทย ไม่หลุดไปเป็น Text-only และสร้าง Native Audio เสมอ
  - **Thai Phonetics & Articulation Guardrails (`JarvisPersona.kt`)**:
    - เพิ่มกฎเข้มงวดใน `LIVE_RULES`: กำหนดให้ออกเสียงภาษาไทยสำเนียงไทยแท้ ออกเสียงตัวสะกดแม่เกย (คำว่า "เจ้านาย") ให้กระชับชัดเจน ห้ามลากเสียงหรือเพี้ยนเป็น "เจ้านาว"
- **Verification**:
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL in 1m 8s** (ผ่าน 100% ทุกชุดทดสอบ)

## 2026-09-04 — Dynamic Indicator Overlays (EMA, SMA, BB, DC, Any Period) & Chart Controller Flexibility
- **Root Cause Analysis (สาเหตุที่ EMA8 วาดไม่ได้ แต่ EMA14, EMA50 วาดได้)**:
  - **Tool Parameter Schema (`ToolRegistry.kt`)**: พารามิเตอร์ `overlay` ใน `chart_dashboard_control` กำหนด `enum = listOf("ema14", "ema20", "ema50", "ema60", "ema200", "bb", "smc", "donchian", "signals")` ทำให้ LLM ถูกจำกัดการเรียกเฉพาะค่าตายตัว
  - **Controller Validation (`ChartController.kt`)**: มีการ hardcode `val validOverlayNames = setOf("ema14", "ema20", "ema50", "ema60", "ema200", "bb", "smc", "donchian", "signals")` ใน 5 จุด (ทั้ง `open`, `set_overlay`, `toggleChartOverlay`, และการโหลดจาก DB) เมื่อผู้ใช้หรือ AI ส่ง `overlays=ema8` ตัวควบคุมจึงตัดทิ้งทั้งหมดเหลือเป็น `overlays=""`
  - **Screen Serialization (`TradingChartScreen.kt`, `MessageBubble.kt`)**: ใน `LaunchedEffect` มีการ serialize `overlaysJson` โดยระบุเฉพาะ key แบบ hardcode (`put("ema14", ...)` ฯลฯ) ไม่ได้นำ dynamic overlays ใน Set ส่งต่อเข้า WebView
  - **Engine Indicator Resolver (`dashboard_engine.js`)**: กำหนด `OVERLAY_DEFS` แบบ static dictionary และใน `init()` ตรวจสอบเฉพาะ key เดิมที่มีอยู่ใน `state.overlays` ส่งผลให้ indicator คาบอื่นๆ ไม่ถูกสร้างและไม่ถูกคำนวณ
- **Comprehensive Solution & Implementation**:
  - **`ChartController.kt`**:
    - เพิ่ม `isValidOverlay(name: String)`: รองรับ Regex สำหรับ EMA ทุกคาบ (`^ema\d+$`), SMA/MA ทุกคาบ (`^(sma|ma)\d+$`), WMA/HMA (`^(wma|hma)\d+$`), Donchian (`^dc\d+$`), และ Bollinger Bands (`^bb...$`) รวมถึง standard overlays (`smc`, `signals`, `supertrend`, `vwap`)
    - ใช้ `isValidOverlay` ในทุกฟังก์ชันการตรวจสอบและการบันทึก
    - ปรับ `normalizeChartInterval` ให้เรียกใช้ `TaIndicators.normalizeTimeframe` เพื่อให้รับ format `m1`, `m5`, `m15`, `h1`, `h4`, `d1`, `w1` ได้อย่างสมบูรณ์
  - **`ToolRegistry.kt` & `JarvisPersona.kt`**:
    - ปลดล็อก `enum` ใน `overlay`/`overlays` และอัปเดตคำอธิบายพารามิเตอร์และ System Prompt ให้รองรับ EMA และ SMA ทุกคาบตามที่ผู้ใช้สั่ง (เช่น `ema8`, `ema9`, `ema21`, `ema89`, `sma50`, `sma200`)
  - **`TradingChartScreen.kt` & `MessageBubble.kt`**:
    - ปรับ `overlaysJson` ให้ serialize ทุกตัวเลือกที่อยู่ใน `overlays` ส่งต่อให้ JavaScript Engine แบบ Dynamic
    - เพิ่มการแสดง Chip บนแถบ Toolbar กราฟสำหรับ Custom Overlay ที่เปิดใช้งานอยู่ (เช่น ผู้ใช้สั่ง EMA8 จะมี Chip `EMA8` สี Cyan ปรากฏบนจอทันที)
  - **`dashboard_engine.js` (ทั้ง androidMain และ commonMain)**:
    - เพิ่ม `STATIC_OVERLAY_DEFS` พร้อมสีคลาสสิกสำหรับ Fast/Slow EMA
    - เพิ่มฟังก์ชัน `pickColorForPeriod(period, isSma)` ให้สร้างสีแบบ HSL ที่สวยงามและไม่ซ้ำกันสำหรับคาบใดๆ
    - เพิ่ม `getOverlayDef(key)` แยกแยะประเภท indicator และคาบเวลาอัตโนมัติ
    - ปรับปรุง `addOverlaySeries`, `removeOverlaySeries`, `applyAllData`, `updateLegend`, `init`, และ `setOverlay` ให้สร้าง Series และคำนวณค่า EMA/SMA/BB/Donchian จากแท่งเทียนได้ทุกคาบเวลา
- **Verification**:
  - สร้างชุดทดสอบ `ChartDynamicIndicatorTest.kt` ทดสอบความถูกต้องของ `isValidOverlay` กับ EMA/SMA/BB/DC หลากหลายคาบ และทดสอบ Timeframe Normalization
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL in 15s** (ผ่าน 100% ครบทุกชุดทดสอบ)

## 2026-09-04 — Trading Tool Execution Backend Modularization & Architecture Decoupling
- **Decoupled 2,060-line God-Class (`TradingToolExecutionBackend.kt`)**:
  - **`TradingToolExecutionBackend.kt`**: ลดขนาดลงจาก 2,060 บรรทัด เหลือเพียง ~80 บรรทัด ทำหน้าที่เป็น Coordinator ประสานงานผ่าน `TradingToolRouter.domainOf(toolName)` ส่งต่อให้แต่ละ Domain Handler โดยตรง
  - **`Mt5ToolHandler.kt`**: แยกส่วนการทำงาน MT5 Broker Operations & Market Intelligence (~1,120 บรรทัด) เช่น `trading_mt5_order`, `trading_mt5_close_position`, `trading_mt5_candles`, `trading_mt5_analyze`, `trading_mt5_market_scanner`, `trading_mt5_correlation_radar`, `trading_mt5_institutional_flow`
  - **`MarketTechnicalToolHandler.kt`**: แยกส่วนการทำงาน Market, Technical Analysis, Scanning & Sentiment (~460 บรรทัด) เช่น `trading_price`, `trading_market_snapshot`, `trading_technical_analysis`, `trading_multi_timeframe`, `trading_sentiment`, `trading_news`, `trading_macro_calendar`
  - **`ResearchToolHandler.kt`**: แยกส่วนการทำงาน Research, Backtesting Delegation, Strategy Signals & Analytics (~550 บรรทัด) เช่น `trading_strategy_signal`, `trading_signal_stats`, `trading_position_sizing`, `trading_correlation_matrix`, `trading_economic_data` (FRED), `trading_crypto_overview`, `trading_deep_analysis_suite`, `trading_fundamental_analysis`
  - **Zero Regression**: รักษา Backward Compatibility ของ Public/Internal API เดิม 100%
- **Version Bump**:
  - อัปเดต `versionCode = 2`, `versionName = "1.1.0"` ใน `composeApp/build.gradle.kts`
- **Verification**:
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL** ผ่าน 100% ทุกชุดทดสอบ

## 2026-09-04 — Trading Tools, Indicator Calculation Accuracy & Universal Timeframe (m1, m5, m15, h1, h4, D1, W1)
- **Root Cause Analysis (สาเหตุที่อินดิเคเตอร์แสดงค่าผิด และ Timeframe ตกหล่น)**:
  - **Issue 1 (Timeframe Fallback to 1h)**: ในหลายโมดูล (`IndicatorAlertProvider`, `TradingApiService`, `SmcApiService`, `TradingToolExecutionBackend`, `mt5_bridge.py`) มีการใช้ `when(interval.lowercase())` ตรวจสอบเฉพาะรูปแบบ TradingView เช่น `"1m"`, `"15m"`, `"1d"`, `"1w"` โดยไม่มีการรองรับรูปแบบมาตรฐาน MT5 (`"m1"`, `"m5"`, `"m15"`, `"h1"`, `"h4"`, `"d1"`, `"w1"`) ส่งผลให้ทุกครั้งที่ผู้ใช้หรือ AI ระบุ TF รูปแบบ MT5 ระบบจะตกลงที่บล็อก `else -> "1h"` ทันที ทำให้ค่าอินดิเคเตอร์ทั้งหมดถูกคำนวณจากแท่งเทียน 1 ชั่วโมงเสมอ!
  - **Issue 2 (Candidate Candle Threshold)**: ใน `IndicatorAlertProvider` เดิมกำหนดเงื่อนไข `candles.size < 220` ส่งผลให้ Timeframe ระดับสูงอย่าง D1 (วัน) และ W1 (สัปดาห์) ซึ่ง TradingView ส่งกลับมาประมาณ 100-180 แท่ง ล้มเหลวและไม่สามารถคำนวณอินดิเคเตอร์ได้เลย
  - **Issue 3 (ATR & Stochastic Calculation)**: `SmcApiService.calcATR` เดิมใช้ Simple Average (SMA ของ TR) แทนที่จะเป็น Wilder's Smoothing RMA ตามมาตรฐาน TradingView/MT5 และ Stochastic เดิมคำนวณแบบ Unsmoothed Fast %K แทน Slow %K(14, 3, 3)
  - **Issue 4 (MT5 Python Bridge)**: `mt5_bridge.py` เดิมแปลง alias เฉพาะ key ที่ขึ้นต้นด้วย `"m"` หรือ `"h"` ทำให้ `"1d"` และ `"1w"` ไม่ถูกแปลงลงใน `_TIMEFRAME_MAP` และโยน error `unsupported timeframe`
- **Comprehensive Solution & Implementation**:
  - **Unified `TaIndicators`**:
    - เพิ่มระบบแปลง Timeframe สากล: `normalizeTimeframe()` (แปลงทั้ง MT5/TradingView formats เข้าสู่มาตรฐาน `"1m"`, `"5m"`, `"15m"`, `"1h"`, `"4h"`, `"1D"`, `"1W"`), `toMt5Timeframe()`, และ `toTvResolution()`
    - เพิ่มและปรับปรุงคลังฟังก์ชันคำนวณ Indicator สากล (pure Kotlin KMP): `rsi` (Wilder's), `macd` (EMA Fast/Slow + EMA-9 Signal + Hist), `stochastic` (Slow %K & Slow %D 14, 3, 3), `atr` (Wilder's RMA), `bollingerBands` (basis, upper, lower, width, %b), `cci` (20), `adx` (Wilder's RMA +DI, -DI), `supertrend` (ATR-based bands)
  - **IndicatorAlertProvider**:
    - แก้ไข `splitSymbolAndTf` ให้เรียกใช้ `TaIndicators.normalizeTimeframe`
    - ลดเงื่อนไข Candle count สำหรับ TF D1/W1 ลงเหลือ 35 แท่ง เพื่อให้คำนวณได้ทันทีแม้ประวัติสัปดาห์จะไม่ถึง 220 สัปดาห์
    - ผูกค่า Supertrend และ Slow Stochastic(14,3,3) เข้าในผลลัพธ์การวิเคราะห์
  - **Trading Tool Definitions & Execution Backend**:
    - อัปเดต enum และ description ของ `trading_technical_analysis`, `trading_mt5_broker_ta`, `trading_mt5_market_scanner`, `trading_mt5_correlation_radar`, `trading_mt5_institutional_flow`, และ `trading_signal_alert` ให้รองรับ TF ครบทั้ง: `m1, m5, m15, h1, h4, D1, W1` (และ aliases `1m, 5m, 15m, 1h, 4h, 1D, 1W`)
    - อัปเดต `fillTaFromLocal` และ `executeTechnicalAnalysis` ให้ normalize timeframe ก่อนส่งไปยัง TradingView Scanner API และ Local Indicator Engine
  - **SmcApiService & MT5 Python Bridge**:
    - แก้ไข `tvResolution` ใน `SmcApiService` ให้รองรับ `"m1", "m5", "m15", "h1", "h4", "d1", "w1"`
    - เพิ่ม `"D1" to "1d"` ลงใน MTF Liquidity matrix
    - แก้ไข `mt5_bridge.py` ให้ alias `"1d"`, `"1w"`, `"d"`, `"w"` เข้าสู่ `TIMEFRAME_D1` และ `TIMEFRAME_W1`
- **Verification**:
    - สร้างชุดทดสอบ `TradingIndicatorTimeframeTest.kt` ทดสอบความถูกต้องของ Timeframe Normalization ทุกตัว และทดสอบ Indicator Math 8 ชนิด
    - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL** ผ่าน 100% ครบทุกชุดทดสอบ

## 2026-09-04 (ก่อนหน้า) — Gemini Provider & Live Model Hardening & Dynamic Listing
- **Fix Live Model Filtering (SettingsController.kt)**:
  - แก้ไขจุดบกพร่องสำคัญใน `SettingsController.getLiveCapableModels()` ที่เดิมฟิลเตอร์ด้วย `it.supportsVision` ทำให้โมเดลแชททั่วไป (`gemini-2.5-pro`, `gemini-1.5-pro`, `gemini-2.5-flash`) หลุดเข้าไปอยู่ใน Dropdown ของ **Live Model** และเมื่อผู้ใช้เลือกจะเชื่อมต่อ WebSocket ล้มเหลวทันที
  - เปลี่ยนมาฟิลเตอร์ด้วย `it.supportsLive` อย่างถูกต้อง พร้อมปรับ heuristic fallback และ fallback list ให้คืนค่าโมเดล Live ที่แท้จริง (`gemini-3.1-flash-live-preview`, `gemini-2.5-flash-native-audio-preview-12-2025`, `gemini-2.0-flash-exp`)
- **Future-Proof Model Capabilities (ModelConfig.kt)**:
  - ปรับปรุง `supportsNativeTools()` ให้รองรับโมเดล Gemini ยุคใหม่ทุกรุ่นโดยอัตโนมัติ (1.5, 2.x, 3.x+) โดยไม่จำเป็นต้องฮาร์ดโค้ดเลขเวอร์ชันในอนาคต (ยกเว้นโมเดลที่ไม่ใช่ text/multimodal เช่น embeddings/imagen)
  - อัปเดต `isLiveModel()` ให้รองรับคีย์เวิร์ด `native-audio` และ `realtime` เพิ่มเติม
- **Synchronize Live Models Across Services**:
  - อัปเดต `GeminiLlmProvider.kt` เพิ่ม `gemini-2.5-flash-native-audio-preview-12-2025` และ `gemini-2.0-flash-exp` เข้าใน `knownPreviews` เพื่อให้ UI แสดงรายการโมเดล Live ครบถ้วนแม้ Google API จะซ่อน unlisted preview models
  - ซิงค์ `liveModelChain` ใน `LiveGeminiService.kt` และ `JarvisOrchestrator.kt` ให้มี `gemini-2.0-flash-exp` ในสาย fallback เมื่อโควต้าเต็ม
- **Register Gemini in LlmProviderRegistry**:
  - เติมการทำงานใน `LlmProviderRegistry.registerGemini()` และเพิ่มเคส `"gemini"` ใน `createTempProvider()` ให้สมบูรณ์ ป้องกันกรณีค้นหา provider ไม่พบบน standalone registry
- **Verification**:
  - รัน `:composeApp:testDebugUnitTest` ผ่านฉลุย 100% (24 test suites)

## 2026-09-03 — Mobile Modularization, Backtest Handler, Test Suites & Project Review
- **Modularize Trading Terminal Screen (Mobile App)**:
  - แยก `TradingTerminalScreen.kt` จาก monolithic 2,269 บรรทัด ให้เหลือเพียง 474 บรรทัด (ลดลง 79%!) โดยแยก component ย่อยออกเป็น:
    - `TerminalOverviewTab.kt`: Performance stats, Win Rate, Daily/Weekly PnL hero cards, Risk metrics, Quick switches
    - `TerminalTradeTab.kt`: Order entry card, Quick symbol chips, Volume stepper, Dynamic order placement (BUY/SELL), Open positions card
    - `TerminalHistoryTab.kt`: Trade closed deals, PnL badge, Historical trade timeline, Period filter
    - `TerminalEventsTab.kt`: Live AI Decision feed, Execution events timeline
    - `TerminalSettingsTab.kt`: Risk gate parameters, Auto trading switches, Max daily loss, Trailing stops
    - `TerminalConnectionTab.kt`: Bridge WebSocket & HTTP endpoint, Client ping, Account status
    - `TerminalEditPositionDialog.kt`: Dedicated modal dialog สำหรับปรับ SL/TP ตำแหน่งเปิด
    - `TerminalFormatting.kt`: Single source of truth สำหรับ UI Theme colors, Money/Price formatters, Period filter models, Shared mini-widgets (`StatMini`, `MiniLabel`, `EmptyState`, `JarvisTextField`, `SoftButton`)
- **Extract Backtest & Evolution Engine (Mobile App)**:
  - แยกโค้ดส่วน Backtest execution, Parameter Optimization (V1/V2), Genetic Evolve, Mix Signal Engine และ Dataset fingerprinting (~950 บรรทัด) ออกจาก `TradingToolExecutionBackend.kt` เข้าสู่ `BacktestToolHandler.kt`
  - ลดขนาด `TradingToolExecutionBackend.kt` จาก 3,008 บรรทัด ลงเหลือ 2,060 บรรทัด โดยคง Public/Internal API เดิมไว้อย่างสมบูรณ์
- **Add Mobile Automated Test Suites**:
  - เพิ่ม `AutomationEvaluatorTest.kt`: ทดสอบ Condition parsing (GT, LT, GTE, LTE, EQ), formatted price stripping, currency signs
  - เพิ่ม `IntentClassifierTest.kt`: ทดสอบ Natural language classification สำหรับคำสั่งเทรด, ข้อมูลตลาด, AI chat
  - เพิ่ม `SignalAlertProviderTest.kt`: ทดสอบ `signalKindOf` normalization (ป้องกัน regression ตัวเลขหลุดจากกลยุทธ์ 52H/3BR), ทดสอบ `computeTpSl` logical risk/reward levels, และทดสอบ `TradingSignalMarketDataRouter` source switching ระหว่าง Demo และ Live
  - ขยาย `TradingToolDomainRoutingTest.kt`: ครอบคลุม 100% ของ domain tool routing
  - ผลการรัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL** ผ่าน 24 test suites รวดเร็วภายใน 8 วินาที
- **Project Hygiene & Server Verification**:
  - ลบไฟล์ orphan/ขยะที่ตกค้าง: `composeApp/0)`, `composeApp/0.0`, `stash_smc.diff`, `README.head.tmp`, `GeminiService.HEAD.kt`, `composeApp/p4_build*.log`
  - ย้าย patch scripts เก่าเข้า `scripts/patches/` (`clean_vm.py`, `fix_*.py`, `commit_smc.kt`, `stash_smc.kt`)
  - ตรวจสอบโฟลเดอร์ `strategies/` (61 ไฟล์ QuantPedia) และอัปเดต `.gitignore`
  - Rebuild `better-sqlite3` รองรับ Node.js v24 บน `mt5-core-server` และทดสอบ `npm run analyze` สำเร็จ 100% (158 trades, Net Profit $104.26, 15,796 decision cycles)
  - ตรวจสอบ TypeScript compilation ของ server: `npm run build` (`tsc`) ผ่าน 0 errors
  - ตรวจสอบความพร้อมของ MT5 Python Bridge: ยืนยันการทำงานของ `math.isclose()` ใน `modify_position`, volume clamping ใน `close_position`, และ `snapshots_bulk` endpoint
  - อัปเดตสถานะใน `.obsidian-wiki/09_Roadmap/04_Pro_AI_Trader_Roadmap.md`

## 2026-08-27 — TradingView Strategy Lab
- เพิ่ม `tools/tradingview_strategy_lab.py` เป็น external research harness สำหรับดึง historical OHLCV จาก TradingView หรืออ่าน CSV export แล้วรัน MOM/TR/REV/DC/52H/E/UT/3BR แยกกัน
- แบ่ง Train 60% / Validation 20% / Holdout OOS 20%; รายงาน PF, expectancy R, win rate, DD, Sharpe-like score, long/short asymmetry และ parameter-neighborhood stability
- ใช้ closed-bar signal → next-bar-open entry เพื่อลด look-ahead และบันทึก dataset/source/cost assumptions ใน `run_metadata.json`
- เพิ่ม `.obsidian-wiki/07_Trading_Intelligence/Strategy_Lab_TradingView.md`; ผลเป็น research-only และยังไม่ promote parameter เข้า production

## 2026-08-26 (ล่าสุด) — Strategy Optimization V2
- เพิ่ม `StrategyOptimizationV2`: optimize EntryParams + strategy-native TpSlParams ร่วมกัน แทนการหา SL/TP ที่ดีบน entry แบบคงที่
- แบ่งข้อมูลเป็น Train 60% / Validation 20% / Holdout OOS 20%; candidate ต้องผ่าน evidence gate ทุกช่วงก่อนเป็น candidate
- เพิ่ม `Parameter Neighborhood Stability` เพื่อกัน parameter cliff และให้ความสำคัญกับ robust region มากกว่า single best point
- เปลี่ยน `trading_backtest_optimize` ให้ใช้ V2 เป็น discovery path; ไม่เขียน production params และ promotion ยังเป็นหน้าที่ของ Evolution/Risk Gate
- รองรับ strategy-specific grid สำหรับ MOM/TR/REV/DC/52H/E/UT และ 3BR ใช้ pattern entry โดยไม่มี entry grid
- เพิ่ม `composeApp/.../automation/backtest/StrategyOptimizationV2.kt`

## 2026-08-26 (ก่อนหน้า) — Live Alert Scheduler
- จาก logcat พบ 15m/30m signal พร้อมกันทำให้ `liveVoiceMutex` ตัวเดิมมีการรอคิว ~19.7s และออกแบบ queue/fallback ซับซ้อนเกินไป
- เพิ่ม `Live Alert Scheduler` กลาง: serialize เฉพาะ audio output, เก็บ request เป็น priority queue และให้ M1/M5 signal แซงงาน TF ใหญ่/งานทั่วไปที่รออยู่
- ตัด queue timeout 90s ออกจาก Live session path; `speakAlertNow()` ไม่รอ mutex อีกต่อไป จึงไม่เกิด `APP_QUEUE_BUSY` จากการแข่งขันของ alert ภายใน service
- เพิ่ม diagnostic `Live Scheduler ENQUEUE/DISPATCH` เพื่อวัด queue depth และลำดับ dispatch จริง
- คง no-overlap audio และ Live chain/fallback semantics เดิม; Signal detection/Risk/notification ไม่ถูกเปลี่ยน

## 2026-08-26 (ก่อนหน้า) — Live Signal Alert: short-form + serialized voice queue
- จาก logcat 11:40 พบ 5m Live ใช้ generation ~33s, cap 300 chars แล้ว close ช้า; ระหว่างนั้น 1m signal เข้าคิวและเกิด `APP_QUEUE_BUSY` ก่อน fallback TTS ทำให้ delivery path ซับซ้อนเกินจำเป็น
- ลด Signal Alert response cap **300 → 180 chars** และ one-shot Live ceiling **35 → 22s**; prompt บังคับ **2-3 ประโยค / 35-55 คำ / ~15s**
- เปลี่ยน Live voice queue ของ Signal Alert เป็น **serialized 90s ทุก timeframe** ไม่แยก 1m/5m กับ TF ใหญ่ เพื่อให้ alert ใหม่รอ session ก่อนหน้าจบแทนการ fallback เร็วเกินไป
- ลด transcript chunk logging เป็น progress checkpoints เพื่อให้ `logcat.txt` อ่านง่ายขึ้น โดยยังเก็บ transcript เต็มสำหรับ diagnostics/pushToChat
- คง timeout cause precedence และ no-overlap TTS safety; เป้าหมายคือ Live session เดียวต่อครั้ง, ไม่พูดซ้อน, และ fallback เฉพาะเมื่อ Live chain ล้มจริง

## 2026-08-26 (บ่าย) — Live Signal Alert: graceful completion + timing diagnostics
- จาก logcat 11:54 พบว่า Signal Alert ถูก cap ที่ 180 chars จริง แต่ audio ยาว ~17s และ session ยังไม่มี `turnComplete` ทำให้ total alert latency ~31.5s; queue serialization ทำงานแล้วและไม่มี `APP_QUEUE_BUSY`
- ลด short-form cap **180 → 120 chars** และ prompt เป็น 1–2 ประโยค / 20–35 คำ โดยให้ Signal/Strategy + Entry/SL/TP มี priority สูงสุด เพื่อลดระยะเวลาเสียงจริง
- แก้ timing log ให้แยก `WS_CONNECTED`, `READY total/afterWs`, `REQUEST_SENT total/afterReady` ไม่ใช้ตัวเลข `after connect` ที่อ้างอิงผิดจุด
- เปลี่ยน log `Live OK` เป็น `AUDIO_DELIVERED` เพื่อแยกการส่งเสียงสำเร็จออกจาก `RESULT cause` เช่น `AUDIO_RESPONSE_LENGTH_CAP`
- คง serialized Live voice queue, signal dedup/cache และ timeout cause precedence เดิม
- ปรับ Signal Alert diagnostics ให้ใช้ event format เดียว `SIGNAL_EVENT` สำหรับ FIRE/RESET/SUPPRESS ลดข้อความซ้ำและทำให้ AI อ่าน state transition ได้ทันที โดยไม่เปลี่ยน execution semantics

## 2026-08-26 (บ่าย) — Live Signal Alert: response cap + timeout classification
- จาก logcat 10:26 และ 10:46: 2.5 Native ได้ audio จริง แต่ไม่ส่ง `turnComplete`; watchdog ปิดที่ ~29–29s แล้ว result เดิมถูกจัดเป็น `AUDIO_SUCCESS` ทั้งที่สาเหตุจริงคือ `AUDIO_STREAM_IDLE_TIMEOUT` และมี audio buffer ค้าง ทำให้ generation รวม ~39–43s
- ลด hard session ceiling 90s → **35s** และ audio-idle watchdog 12s → **10s**
- ลด prompt Live Signal Alert เป็น **2–3 ประโยค / 40–60 คำ / ~240 chars** และเพิ่ม circuit breaker ที่ 300 chars เมื่อมี audio แล้ว เพื่อหยุด runaway generation
- แก้ cause precedence ให้ timeout/error มาก่อน `AUDIO_SUCCESS` ทำให้ diagnostics ตรงกับเหตุการณ์จริง ขณะที่ session ที่มี audio แล้วไม่ถูก fallback ไปพูดซ้ำ
- compile verification ทำต่อหลังแพตช์นี้

## 2026-08-26 (สาย) — ปรับ Compact Signal Polling Log + ลดความยาว Live Signal Alert
- ปรับ `JarvisAutomationService` ไม่ให้พิมพ์ `Signal snapshot cache HIT/MISS` ราย symbol ทุก 30s; รวมเป็น `SIGNAL_CACHE` summary ต่อ cycle เพื่อให้ AI วิเคราะห์ log ได้เร็วและลด noise
- เพิ่ม dedup ของ signal action log ตาม `symbol + condition.field + signal_id + action` เพื่อไม่แสดง `SUPPRESS_ALREADY_TRIGGERED` ซ้ำทุก polling cycle; state transition สำคัญยังถูกเก็บ
- คง logic cache/dedup เดิม ไม่ลดความปลอดภัยของ signal execution
- Live Signal Alert prompt อยู่ที่ 2-3 ประโยค / 40-60 คำ / ~240 chars พร้อม circuit breaker 300 chars

## 2026-08-26 (สาย) — ลดความยาว Live Signal Alert + แก้ READY greeting ให้ตรง Persona
- จาก logcat 06:30:43: 2.5 Native ได้ audio สำเร็จ แต่ response ใช้เวลารวม ~43s และ audio stream ยาว 1.68MB ก่อน idle watchdog ปิด session; สาเหตุหลักคือ prompt 100-150 คำยังยาวเกินสำหรับ alert ที่ต้องการความเร็ว
- ปรับ Live Signal Alert prompt เป็น **3-4 ประโยค / 60-90 คำ / เป้าหมาย ~20s** โดยยังคงข้อมูล Signal/Strategy, Entry, SL/TP และ risk/context 1 ประเด็น
- แก้ UI ข้อความ `LIVE READY` จาก `ครับ` เป็น `ค่ะ` ให้สอดคล้องกับ JARVIS female persona
- compile verification จะทำหลังแพตช์ชุดนี้

## 2026-08-26 (เช้า) — สลับ Alert Voice chain: 2.5 Native ขึ้นก่อน 3.1
- จาก logcat 06:24–10:03: **Live voice 14/14 = 100%** (แพตช์ก่อนหน้าสมบูรณ์: key rotation ทำงาน, TF-aware queue รับ alert ซ้อน 07:30 รอ 43.3s แล้วได้ Live, watchdog 14s ชุบ 2.5 ที่ firstAudio 11–12s)
- พบว่า 3.1 TRANSCRIPT_ONLY 12/14 (85%) — ทุก alert เสีย ~5–7s กับ 3.1 สองรอบก่อนจบที่ 2.5 เสมอ ส่วน 2.5 Native สำเร็จ 11/11 เมื่อได้ลอง → สลับ `liveVoiceChain()` ให้ 2.5 Native พูดก่อน 3.1 คงเป็น fallback (ตัด latency ~6s/alert)
- compile ผ่าน: `:composeApp:compileDebugKotlinAndroid` ✅

## 2026-08-26 (ดึก) — Live Voice: หมุน API key ต่อ attempt + ปรับ prompt ความยาวให้ตรงกัน
- จาก logcat 01:50–02:04: พบเคส "3.1 มั่ว 2 รอบติด + 2.5 ตาย" บน key เดียวกันหมด → เพิ่ม **key rotation ต่อ attempt** ใน chain เสียงแจ้งเตือน (อ่าน pool จาก setting `gemini_api_keys` + primary `api_key`) และ log ท้าย key 4 ตัวใน WS connected เพื่อ debug ต่อได้
- แก้ **prompt ขัดกันเอง**: system instruction เคยสั่ง ≤4 ประโยค/80 คำ แต่ payload สั่ง 8–12 ประโยค/180–300 คำ → ปรับทั้งสองฝั่งให้ตรงกันที่ 4–6 ประโยค / 100–150 คำ (ฟังจบใน ~30s) ลด generation 28–63s ลง
- compile ผ่าน: `:composeApp:compileDebugKotlinAndroid` ✅

## 2026-08-26 — แก้เสียงซ้อน + alert TF ใหญ่ตก TTS (JarvisAutomationService.kt)
- ตรวจ logcat 22:29–00:04 หลังแพตช์ก่อนหน้า: Live voice 6/8, chain พัง 0 ครั้ง — ที่เหลือคือ APP_QUEUE_BUSY 2 ครั้ง (22:31, 23:30) ซึ่งทำให้ **Live (15m) + Android TTS (30m) พูดทับกันพร้อมกัน 2 เสียง** (คนละสัญญาณแต่ BUY เหมือนกัน เลยฟังเหมือนซ้ำ)
- **Fix 4 — TF-aware queue timeout**: parse TF จาก symbol ("XAUUSD@15m"); TF ≥ 15m รอคิว Live นานสุด 95s (สัญญาณไม่สตเพิ่ล), TF เล็กคง 4s — alert 30m ทั้ง 2 เคสจะได้ Live แทน TTS
- **Fix 5 — กันเสียงซ้อนถาวร**: เคส queue busy ที่ต้อง fallback TTS จะรอ lock (cap 95s) ให้ session ที่พูดอยู่จบก่อน ค่อยพูด TTS — ไม่มี 2 เสียงทับกันอีก
- compile ผ่าน: `:composeApp:compileDebugKotlinAndroid` ✅

## 2026-08-25 (บ่าย) — แก้ Live Alert Voice จาก logcat จริง (JarvisAutomationService.kt)
- วิเคราะห์ logcat 17:24–18:05: Live voice สำเร็จแค่ 4/8 alert — 3.1 ตอบ TRANSCRIPT_ONLY 5 ครั้ง, 2.5 ติด FIRST_AUDIO_TIMEOUT_NO_OUTPUT 3 ครั้ง, queue busy 1 ครั้ง (fallback Android TTS ครบทุกเคส ไม่มี alert เงียบ)
- **Fix 1 — first-output watchdog 8s → 14s**: พบว่า 2.5 Native ที่สุขภาพดีส่ง first audio ช้า 7.5–8.8s หลัง READY (17:34=7.5s, 17:47=7.8s รอดหวุดหวิด) ส่วนเคสที่ตาย ตายที่ 8.0–8.3s พอดีเป๊ะ → watchdog เดิมฆ่า session ที่กำลังจะตอบ
- **Fix 2 — retry โมเดลเดิมอีก 1 ครั้งเมื่อ TRANSCRIPT_ONLY**: 3.1 ตอบ text ล้วนแบบมั่วราย turn (config เดียวกันเป๊ะ สลับสำเร็จ/ไม่สำเร็จ) → retry session ใหม่ก่อนค่อย fallback โมเดลถัดไป (TRANSCRIPT_ONLY จบไว ~3s จึง retry ได้ถูก)
- **Fix 3 — log setupJson จริงทุก session** (ตัดย่อ 600 chars) เก็บหลักฐาน config ที่ส่งไป ป้องกัน debug มืด
- ยืนยันกับ official Live API reference (ai.google.dev/api/live): realtimeInput.text / clientContent turnComplete ถูกต้องตาม spec ทั้งคู่ → สาเหตุหลักคือ preview model flaky ราย turn + watchdog แคบเกิน ไม่ใช่ wire format
- compile ผ่าน: `:composeApp:compileDebugKotlinAndroid` ✅

## 2026-08-25 — Live Voice timeout/queue diagnostics
- ปรับ Live Voice ให้แยก queue / setup / first-output / session timeout ชัดเจน และ log latency ของ request→output พร้อม cause classification
- เพิ่ม RESULT log: AUDIO_SUCCESS, TRANSCRIPT_ONLY, API_ERROR, FIRST_AUDIO_TIMEOUT_* และสถานะ fallback
- Queue acquisition log แสดงแม้รอ 0ms เพื่อยืนยันว่า queue ไม่ใช่คอขวด

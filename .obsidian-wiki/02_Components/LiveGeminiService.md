# 🔌 LiveGeminiService

`LiveGeminiService.kt` คือหัวใจของระบบ Multimodal Live API ที่ทำงานผ่าน WebSocket

## 🎯 หน้าที่หลัก
1. **WebSocket Connection**: จัดการการเชื่อมต่อ bidi-streaming กับ Google Generative API (URL สร้างใหม่ทุก attempt ผ่าน `LiveProtocol.buildUrl` เพื่อให้ quota key rotation ใช้ key ใหม่จริง)
2. **Data Streaming**: รับ-ส่ง Audio (PCM), Video (JPEG) และ Text แบบ Real-time
3. **Transcription Handling**: จัดการการแปลงเสียงเป็นข้อความทั้งฝั่ง User และ Model
4. **Persistence**: บันทึกบทสนทนาที่เสร็จสมบูรณ์ลง SQLite ผ่าน [[memory_strategy]] (รวม turn ที่ถูกขัดจังหวะ)

## 🔁 Flows ที่ส่งออก (2026-09-16)
| Flow | เนื้อหา | หมายเหตุ |
|---|---|---|
| `audioOutputFlow` | `LiveAudioChunk(epoch, pcm)` | buffer 4096 — chunk ที่ `epoch != audioEpoch` (ก่อน barge-in) ต้องทิ้ง |
| `textOutputFlow` | `LiveTextUpdate` | emit ตรงตามลำดับเฟรม; chunk แรกของทุก turn = bubble ใหม่ |
| `userTurnFinalFlow` | ประโยคผู้ใช้ที่พูดจบ | 1 ครั้ง/turn เมื่อ model เริ่มตอบหรือ turn จบ — ใช้กับคำสั่งลัดฝั่งเครื่อง |
| `nativeToolCallFlow` | `LiveToolCallEvent` (+`sessionGeneration`) | buffer 64 — ลูป WebSocket ไม่ถูกบล็อกระหว่างรัน tool |
| `toolCallCancellationFlow` | id ที่ server ยกเลิก | bridge ยกเลิก job และไม่ส่ง response |
| `reportSnapshot()` (ฟังก์ชัน) | `PendingAnswerQueue.View` — ข้อที่กำลังเล่า (ส่งให้ AI แล้ว ยังไม่ settle) + ข้อที่รอ | รายการงานบนจอหุ่น (`AvatarTasks`) · ผลที่ bridge ใส่คิวมี `key` = call id |
| `answeringReport` | โมเดลกำลังตอบข้อค้างที่แอปส่งให้ และผู้ใช้ยังไม่ได้พูด | bridge ไม่ส่งลูกน้องในเทิร์นนี้ |
| `deafUtterances` / `deafReconnects` | ครั้งที่ server ไม่ได้ยินผู้ใช้ติดกัน / เปิด session ใหม่ (`true`) หรือเปิดแล้วยังไม่ได้ยิน (`false`) | bridge ส่งต่อเป็นคำพูดของหุ่น (`AvatarLiveFeed.notices`) |

## 🛡️ ความเสถียรของ session
- **Session resumption**: ส่ง `session_resumption: {}` ตั้งแต่ครั้งแรก (server จึงส่ง handle), ส่ง handle ตอน reconnect
- **Context window compression**: ส่ง `context_window_compression: {"slidingWindow":{}}` เพื่อยืดอายุ session
- **Self-heal ตามลำดับ**: ถ้า setup ถูกปฏิเสธ (NOT_CONSISTENT/INVALID_ARGUMENT) → ปิด compression แล้วลองใหม่ → ถ้ายังไม่ผ่านปิด resumption → ถ้ายังไม่ผ่านค่อยสลับโมเดล
- **ชุด tool**: `ToolRegistry.getLiveGeminiTool(profile)` — ทุกโหมดใช้ tool ชุดเดียวกัน ยกเว้นกลุ่มควบคุมเครื่องเต็มรูปแบบที่เปิดเฉพาะโหมดขับรถ (ดู [[Model_Policy_Free_Tier]])
- **Latency log**: `⏱️ First audio …ms after user turn` ทุก turn (ใช้ตัดสินใจเรื่อง `thinking_level`)
- **Reconnect ที่ไม่มี handle**: ใส่ turn ล่าสุดของ session (`recentTurns` สูงสุด 12, ≤4000 ตัวอักษร) เป็น history
- **Lifecycle guard**: loop เก่าที่ถูก cancel ไม่ล้าง socket/สถานะของ loop ใหม่ (`sessionGeneration`, `connectCallId`)
- **Tool response**: `sendNativeToolResponse(..., sessionGeneration)` คืน `false` ถ้า socket เปลี่ยนไปแล้ว
- **เสียงก่อน READY ถูกทิ้งโดยตั้งใจ** (burst-flush เคยทำให้ session ปิด) — UI บอกให้รอ AI ทักก่อนพูด
- **`sendAudioStreamEnd()`**: เรียกตอน mute ให้ server VAD ปิดท้าย utterance
- **Log redaction**: error ของการเชื่อมต่อผ่าน `LiveProtocol.redactSecrets` (ไม่ให้ `?key=` หลุดลง logcat/UI)
- **คำตอบที่เป็นแค่คำทักทาย** (`LiveProtocol.isSessionGreetingOnly`): เทิร์นที่มีผล tool แต่ AI พูดแค่ประโยคทักทายเปิดเซสชัน
  = ผู้ใช้ยังไม่ได้ยินผล → `PendingAnswerQueue.onGreetingOnlyAnswer` เก็บผลเข้าคิวรายงานตอนเงียบ (ข้าม tool เปลี่ยนหน้า/โหมด
  `LiveProtocol.UI_ONLY_TOOLS`), ไม่นับ `onToolAnswered`, รายงานจากคิวที่ได้แค่คำทักทายกลับมาถูกส่งใหม่ ·
  log: `⚠️ ทักทายซ้ำกลางบทสนทนา` แล้วตามด้วย `📥 AI ทักทายแทนการรายงานผล — เก็บ N ข้อ…` (เคส 2026-10-01 โหมดอวตาร)
- **เทิร์นทักทายเปิดเซสชัน** (`inGreetingTurn`): จริงตั้งแต่ส่ง "สวัสดีจาวิส พร้อมคุยไหม" จนผู้ใช้พูดหรือเทิร์นจบ — tool call ช่วงนี้
  ไม่มีใครขอ [[LiveToolBridge]] ไม่รัน ตอบ `GREETING_TURN_TOOL_NOTE` (ไม่นับเป็นผลที่ต้องรายงาน) · `connectAndListen` รีเซ็ต `lastUserText`
  (ค่าค้างจาก session ก่อนเคยทำให้ call แรกติดป้าย `[ANSWER FOR] <คำถามเก่า>`)
- **ประวัติตอนเปิด session ใหม่** (`buildSessionHistory`): ตัดคำทักทาย, ตัดคำถามที่ไม่มีคำตอบตามหลัง (ไม่งั้นโมเดลตอบเองทันทีหลังทักทาย
  เรียก tool ด้วย — logcat 2026-10-01 16:51) · reconnect (`buildReconnectHistory`) เก็บคำถามค้างไว้
- **ป้ายผล tool** (`LiveProtocol.tagToolResult`): `[ANSWER FOR]` / `[PENDING QUESTION]` เฉพาะผลที่เป็นคำตอบ — ผลของ `UI_ONLY_TOOLS`
  (เปลี่ยนหน้า/โหมด) ไม่ติดป้าย (ป้าย "ต้องตอบคำถามนี้ทันที" บนผลเปลี่ยนหน้าทำให้ตอบอากาศซ้ำ, logcat 2026-10-01 18:19)
- **`speechMarks`** (`LiveSpeechMark`): คำพูดของ AI ถึงตอนนี้ + เวลาเริ่มของเสียงคำตอบ (หรือเวลาข้อความแรกถ้าเสียงยังไม่มา) + ms ต่อหน่วย
  ของ `LiveProtocol.speechUnits` (เริ่ม 93 · เรียนที่ `turnComplete` จากคำตอบยาว ≥ 6 วิ ไม่ถูกตัด ไม่ถูกปิดเสียงท้าย: 70% เดิม 30% ใหม่)
  — เดิมคิดจากความยาวเสียงที่มาถึงแล้ว แต่เสียงมาล่วงหน้าข้อความไม่เท่ากัน (logcat 2026-10-02 09:48: ฉากอากาศช้าไป ~1 วิ)
- **`resultsDeferred`**: (tool, บรรทัดแรกของผล) ทุกครั้งที่ผล tool เข้าคิวรายงาน (`enqueuePendingToolResult`, เทิร์นถูกตัดก่อน AI พูด,
  ทักทายแทนรายงาน) — โหมดอวตารไม่ขึ้นฉากของผลนั้นจนถึงรอบเล่า
  — ฉากของหุ่นเปลี่ยนตามเสียง ไม่ใช่ตามข้อความ (transcript มาก่อนเสียงหลายวินาที) → [[Avatar_Mode]] "ฉากข้อมูลตามเสียงพูด"
- **`reportsSent`**: (tool, บรรทัดแรกของผล) ทุกครั้งที่ `schedulePendingAnswer` ส่งข้อค้างที่มีผล tool ให้ AI เล่า — โหมดอวตารขึ้นฉาก
  ของผลนั้นพร้อมคำแรกที่ AI พูด (งานของลูกน้องรายงานผ่านคิวนี้เท่านั้น)
- **ไม่เล่นคำพูดเกินหลังเปลี่ยนหน้า** (`LiveProtocol.isTrailingExpression`): พูดตอบแล้วเรียกแค่ tool แสดงท่าทาง → เสียง/ข้อความที่ตามมา
  ในเทิร์นนั้นถูกทิ้ง (`muteRestOfTurn`, log `🔇`) · รีเซ็ตเมื่อเทิร์นจบ ผู้ใช้พูด ถูกขัด หรือมี tool ข้อมูลตามมา
- **คิวคำตอบค้าง**: ลบ/แทนผลด้วย "เรื่อง" = ชื่อ tool + หัวผล (`PendingAnswerQueue.subject`) — ราคาทองกับราคา Bitcoin เป็นคนละเรื่อง
- **หัวหน้า/ลูกน้อง (งานส่งต่อ)**: รับงาน `DELEGATED` → พูดรับงานประโยคเดียว (ไม่ติดป้าย `[ANSWER FOR]`) → ลูกน้องเสร็จ ผลเข้าคิว →
  ส่ง `[PENDING QUESTION]` ตอนเงียบ · คำถามที่ถูกตัดก่อนเรียก tool ก็เข้าคิว แล้วแอปถามโมเดลซ้ำ — call ช่วงนั้นเป็นของคำถามนั้น
  (`currentQuestion`) · รายงานที่ได้แค่คำทักทายกลับมาส่งใหม่ **เว้นแต่**เทิร์นนั้นเรียก tool (กำลังทำอยู่) · คำตอบทาง TTS นับว่าพูดแล้ว
  · รายงานที่ถูกตัดก่อนผู้ใช้ได้ยิน 3 วิ (`voiceHeardBeforeCutMs`) ส่งใหม่ (`LiveProtocol.reportReachedUser`) · เทิร์นที่ถูกตัด:
  ผลเปลี่ยนหน้า/โหมด (`UI_ONLY_TOOLS`) ไม่เข้าคิวเป็นคำถาม (`PendingAnswerQueue.onTurnEnded`) · **คำตอบจากผล tool ที่ผู้ใช้ได้ยินไม่ถึง
  3 วิ** (`onTurnEnded(heardMs = voiceHeardBeforeCutMs)`) → ผลเข้าคิวเล่าใหม่เหมือนรายงาน (log `📥 คำตอบถูกตัดหลังได้ยินแค่ …ms`) —
  งานเร็วของลูกน้องตอบในเทิร์นตัวเองแล้ว (2026-10-03, [[LiveToolBridge]]) · คำตอบคุยเล่น (ไม่มี tool) ที่ถูกตัดไม่ถามซ้ำ
- **ผลที่ผู้ใช้ไม่ได้ยิน** (`LiveProtocol.resultsUntold` → `PendingAnswerQueue.onResultsUntold`): เทิร์นที่มีผล tool แล้ว AI จบเงียบๆ
  (ไม่มีเสียงไม่มีข้อความ) หรือพูดแค่คำทักทาย → ผลเข้าคิวรายงาน (logcat 2026-10-02 13:01:06: ราคาทองส่งถึงโมเดลแล้วเทิร์นจบเงียบ ผู้ใช้
  ไม่ได้ยินเลย — watchdog 12 วิเดิมจับแค่ "เงียบและไม่จบเทิร์น") · คำทักทายรู้จัก "เล่นอะไร" / "มีอะไรให้" เพิ่ม · ประโยคสั้นที่มีตัวเลข
  ไม่นับเป็นคำทักทาย
- **ดันรายงานที่ server ไม่ตอบ** (`nudgeIfUnanswered`): ข้อความที่แอปส่ง (`realtimeInput.text`) ไม่มีจุดจบเทิร์น — server ตัดสินจากเสียงของ
  ผู้ใช้ ("End of turn … derived from user activity", Live API reference) · ส่งรายงานแล้ว 6 วิไม่มีอะไรเกิดขึ้นและไมค์เงียบ → ส่ง
  `audioStreamEnd` (log `⏳`) — ปกติโมเดลเริ่มเล่าใน 1–3 วิ (8 รายงานใน log 2026-10-02) แต่โหมดอวตาร 12:57 รอ 9.9 และ 27.8 วิ
  (log ในเครื่องช่วงนั้นหายแล้ว สาเหตุยังไม่ยืนยัน) · คิวเช็คช่วงเงียบทุก 0.25 วิ (เดิม 0.75)
- **ถามรัว** (logcat 2026-10-02 14:56–14:58 โหมดอวตาร: BTC เล่าสองรอบ ราคาทองหาย):
  - ผู้ใช้ตัด AI ภายใน 15 วิ (`lastUserCutAtMs`) → ไมค์ต้องเงียบ 5 วิ (ปกติ 2.5) ก่อนส่งข้อค้าง (`LiveProtocol.localQuietNeededMs`) —
    เดิมรายงาน BTC ไปชนช่วงเว้น 2.5 วิก่อนคำถามราคาทอง → server ตัดเทิร์นราคาทองแล้วเล่า BTC แทน
  - `LiveProtocol.reportOutcome` → `TOLD` / `NOT_TOLD` / `WAIT`: เทิร์นที่จบเป็นคำถามใหม่ของผู้ใช้ (เรียก tool แล้ว) ถูกตัดก่อนพูด → รอดู
    เทิร์นถัดไป (`reportWaitedSinceMs`) — เดิมนับว่ารายงานถูกกลืนทันที แต่ AI เล่า BTC เทิร์นถัดไปเอง → ส่งซ้ำ เล่าสองรอบ ·
    รอแล้วไม่มีเทิร์นมา → ส่งข้อนั้นก่อนข้อใหม่
  - คิวเก็บ 5 นาที 5 ข้อ (เดิม 2 นาที 3 ข้อ) — ราคาทองค้างหลังรายงานยาว 3 ข้อ หมดอายุตอน 120.2 วิแล้วหายเงียบ · ข้อที่หมดอายุ log `⌛`
- **ไม่ส่งข้อค้างระหว่างโมเดลยังไม่จบเทิร์น** (`modelTurnOpen`: ผู้ใช้พูด / tool call / ผล tool / ข้อความที่แอปส่ง → จนถึง `turnComplete`;
  `LiveProtocol.modelMayStillAnswer` ยอมเมื่อ server เงียบ ≥ 6 วิ) — เดิมดูแค่ server เงียบ 1.5 วิ แต่โมเดลคิดหลังผล tool ได้นานกว่านั้น
  (logcat 2026-10-02 11:42:47: ส่งรายงาน 1.5 วิหลังผล tool → server ตัดเทิร์นที่โมเดลกำลังจะพูดรับงาน; ในห้องเงียบตัดคำตอบจริงได้)
- **ต้นเหตุทักทายซ้ำที่เจอแล้ว**: ประโยคทักทายแบบคำต่อคำใน persona + ข้อความผล tool ที่สั่งแค่ "ตอบรับสั้นๆ น่ารัก"
  (DELEGATED 09-24, `device_avatar_emotion` 10-01) — โมเดลหยิบประโยคทักทายมาใช้ แล้วพูดซ้ำแบบเดิมในเทิร์นถัดไป ·
  **ทั้งสอง persona ไม่มีประโยคทักทายคำต่อคำแล้ว** (โหมดอวตาร 10-01, โหมดควบคุม 10-02 — หลังตอบราคาทองและรับงานด้วยคำทักทาย)
  · **ตัวอย่างประโยคใน persona ก็นับ**: persona สัตว์เลี้ยงข้อ 2 ยกตัวอย่างน้ำเสียงเป็น "สวัสดีฮับบอส! วันนี้เล่นอะไรกันดีฮับ" และข้อ 12
  (เห็นหน้า) "สวัสดีฮับบอส! วันนี้เหนื่อยไหมฮับ" — โมเดลพูดประโยคแรกคำต่อคำแทนคำรับงาน BTC (logcat 2026-10-02 11:42:03) ·
  `DynamicModelTest`: คำทักทายในเครื่องหมายคำพูดของ persona สัตว์เลี้ยงเหลือแค่ข้อความเปิดเซสชันของแอป
- **session ที่ไม่ได้ยินเสียง** (`noteLocalUtterance`, 2026-10-03): logcat 08:39–08:41 เครื่องได้ยินผู้ใช้ 6 ครั้ง (ไมค์ 3–21%) ส่งเสียงครบ
  (ไม่มี `send skipped`) แต่ server ไม่ส่ง transcript และไม่ตอบเลย · 08:43:52 อีก session ปิดด้วย INTERNAL_ERROR · ทางเสียงของแอป
  ไม่ได้เปลี่ยนตั้งแต่ build 00:16 ที่คุยได้ปกติ → ปัญหาฝั่ง server (free tier) แอปแก้ต้นเหตุไม่ได้ จึงตรวจจับแล้วเปิดใหม่:
  - `VoiceController` แจ้งทุกคำพูดที่ `UserVoiceDetector` จับได้ยาว ≥ 1 วิ (`MIN_UTTERANCE_MS`, ไม่นับช่วงเสียง AI/เอฟเฟกต์)
    → `JarvisOrchestrator.noteLiveUtterance`
  - 7 วิต่อมา (`DEAF_CHECK_MS`) ถ้า server ไม่มี transcript หรือ `interrupted` (`lastServerHeardAtMs`), AI ไม่ได้ส่งเสียง/ข้อความ
    ตั้งแต่ผู้ใช้เริ่มพูด (`lastModelOutputAtMs` — คำตอบอาจมาก่อน transcript) และไม่มีคำตอบ/tool ค้าง = ไม่ได้ยิน (log `🙉 ผู้ใช้พูด …ms แต่ server ไม่ได้ยิน … ครั้งที่ N ติดกัน`) · transcript ของผู้ใช้รีเซ็ตตัวนับ
  - ครบ 3 ครั้งติด → ล้าง resumption handle (session เดิมหูหนวก) ปิด socket แล้วลูปเชื่อมต่อเปิด session ใหม่พร้อม `buildReconnectHistory`
    (log `🔁 server ไม่ได้ยินผู้ใช้ 3 ครั้งติด`) · server ไม่ตอบการปิดใน 5 วิ → ตัด socket เอง (`deafReconnectPending`, log `🔁 ปิด session
    ที่ไม่ได้ยินเสียงเอง`)
  - เปิดใหม่ได้ 1 ครั้งต่อ 5 นาที (`DEAF_RECONNECT_EVERY_MS` — setup กิน TPM) · เปิดแล้วยังไม่ได้ยิน → ข้อความในแชทให้ตรวจเน็ต/เปิด Live ใหม่
  - เครื่องจำลอง: `LiveSessionSim.Options(deafServer = true)` · สถานการณ์ปกติต้องไม่มี `🙉`

## 🗣️ รองรับเสียง (Voice Config)
ระบบรองรับเสียง prebuilt กว่า 30 แบบ (Aoede เป็นค่าเริ่มต้น)

---
**Links**: [[overview]] | [[JarvisViewModel]] | [[LiveToolBridge]] | [[Changelog_2026-09-16_Live_Voice_Review_Fixes]] | [[index]]

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
- **`speechMarks`**: คำพูดของ AI ถึงตอนนี้ + เวลาที่คำสุดท้ายจะได้ยิน (เวลาเสียงแรกของเทิร์น + ความยาวเสียงที่มาถึงแล้ว ÷ 48 kB/s)
  — ฉากของหุ่นเปลี่ยนตามเสียง ไม่ใช่ตามข้อความ (transcript มาก่อนเสียงหลายวินาที) → [[Avatar_Mode]] "ฉากข้อมูลตามเสียงพูด"
- **`reportsSent`**: (tool, บรรทัดแรกของผล) ทุกครั้งที่ `schedulePendingAnswer` ส่งข้อค้างที่มีผล tool ให้ AI เล่า — โหมดอวตารขึ้นฉาก
  ของผลนั้นพร้อมคำแรกที่ AI พูด (งานของลูกน้องรายงานผ่านคิวนี้เท่านั้น)
- **ไม่เล่นคำพูดเกินหลังเปลี่ยนหน้า** (`LiveProtocol.isTrailingExpression`): พูดตอบแล้วเรียกแค่ tool แสดงท่าทาง → เสียง/ข้อความที่ตามมา
  ในเทิร์นนั้นถูกทิ้ง (`muteRestOfTurn`, log `🔇`) · รีเซ็ตเมื่อเทิร์นจบ ผู้ใช้พูด ถูกขัด หรือมี tool ข้อมูลตามมา
- **คิวคำตอบค้าง**: ลบ/แทนผลด้วย "เรื่อง" = ชื่อ tool + หัวผล (`PendingAnswerQueue.subject`) — ราคาทองกับราคา Bitcoin เป็นคนละเรื่อง
- **หัวหน้า/ลูกน้อง (งานส่งต่อ)**: รับงาน `DELEGATED` → พูดรับงานประโยคเดียว (ไม่ติดป้าย `[ANSWER FOR]`) → ลูกน้องเสร็จ ผลเข้าคิว →
  ส่ง `[PENDING QUESTION]` ตอนเงียบ · คำถามที่ถูกตัดก่อนเรียก tool ก็เข้าคิว แล้วแอปถามโมเดลซ้ำ — call ช่วงนั้นเป็นของคำถามนั้น
  (`currentQuestion`) · รายงานที่ได้แค่คำทักทายกลับมาส่งใหม่ **เว้นแต่**เทิร์นนั้นเรียก tool (กำลังทำอยู่) · คำตอบทาง TTS นับว่าพูดแล้ว
- **ต้นเหตุทักทายซ้ำที่เจอแล้ว**: ประโยคทักทายแบบคำต่อคำใน persona + ข้อความผล tool ที่สั่งแค่ "ตอบรับสั้นๆ น่ารัก"
  (DELEGATED 09-24, `device_avatar_emotion` 10-01) — โมเดลหยิบประโยคทักทายมาใช้ แล้วพูดซ้ำแบบเดิมในเทิร์นถัดไป ·
  **ทั้งสอง persona ไม่มีประโยคทักทายคำต่อคำแล้ว** (โหมดอวตาร 10-01, โหมดควบคุม 10-02 — หลังตอบราคาทองและรับงานด้วยคำทักทาย)

## 🗣️ รองรับเสียง (Voice Config)
ระบบรองรับเสียง prebuilt กว่า 30 แบบ (Aoede เป็นค่าเริ่มต้น)

---
**Links**: [[overview]] | [[JarvisViewModel]] | [[LiveToolBridge]] | [[Changelog_2026-09-16_Live_Voice_Review_Fixes]] | [[index]]

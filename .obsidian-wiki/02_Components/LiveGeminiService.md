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

## 🗣️ รองรับเสียง (Voice Config)
ระบบรองรับเสียง prebuilt กว่า 30 แบบ (Aoede เป็นค่าเริ่มต้น)

---
**Links**: [[overview]] | [[JarvisViewModel]] | [[LiveToolBridge]] | [[Changelog_2026-09-16_Live_Voice_Review_Fixes]] | [[index]]

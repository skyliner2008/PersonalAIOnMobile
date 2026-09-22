---
title: "Changelog 2026-09-16: Live Voice System Review & Fixes (Mobile)"
category: "Changelog"
tags: ["gemini-live", "voice", "websocket", "tool-bridge", "always-live", "pet-mode", "bugfix"]
sources: ["LiveGeminiService.kt", "LiveProtocol.kt", "LiveToolBridge.kt", "LiveIntentMatchers.kt", "VoiceController.kt", "LiveLocalCommandParser.kt", "LiveSessionBridge.kt", "PcmAudioEngine.android.kt", "AlwaysLiveManager.kt", "LiveVoiceAlertEngine.kt", "JarvisViewModel.kt", "JarvisOrchestrator.kt", "LiveSessionReviewFixesTest.kt"]
created: "2026-09-16"
updated: "2026-09-16"
---

# Changelog 2026-09-16: Live Voice System Review & Fixes (Mobile)

Review เชิงลึกของระบบ Live ฝั่งแอปมือถือ แล้วแก้ทุกข้อที่พบ ยืนยันพฤติกรรม session resumption กับเอกสาร Live API ทางการ

## 1. ปัญหาและการแก้ไข

### 🔴 ร้ายแรง
| # | ปัญหา | สาเหตุ | การแก้ไข |
|---|---|---|---|
| 1 | คำสั่งลัดยิงซ้ำ / ส่ง LINE เป็นท่อนๆ | VoiceController ตรวจคำสั่งจาก transcript **ทุกชิ้นระหว่างพูด** | `LiveGeminiService.userTurnFinalFlow` ส่งประโยคที่พูดจบ 1 ครั้ง/turn; logic ย้ายไป `LiveLocalCommandParser`; ตัดคำสั่งลัดส่งข้อความตอบกลับ/อ่านแจ้งเตือน/เช็คเพลง (ผลถูกทิ้งเงียบ + ซ้ำกับ tool ของ model); bridge บล็อก `device_notification_reply` ข้อความเดิมภายใน 20 วิ |
| 2 | สลับ API key ตอนโควตาหมดไม่มีผล | URL (`?key=`) สร้างครั้งเดียวก่อน retry loop | `LiveProtocol.buildUrl(apiKey)` ทุก attempt |
| 3 | Alert เปิด Live session ที่สองซ้อน | `LiveVoiceAlertEngine` ไม่รู้ว่าผู้ใช้เปิด Live อยู่ | `LiveSessionBridge` (commonMain) — VM register; alert ส่งผ่าน session หลักเมื่อ active (fallback เดิมถ้าส่งไม่ได้ใน 8 วิ) |
| 4 | Pet persona หลุดเป็น JARVIS หลังพับจอ/ปิดจอ | `stopPetMode()` ล้าง `isPetMode` แต่ expand/screen-on ไม่ตั้งคืน | แยก `stopPetSensors()` / `stopPetMode()`; `startPetMode()` ตั้ง flag ตาม profile; screen-on/hotword เรียก `startPetMode()` |
| 5 | Tool call ที่ args เป็น array/object ทำให้ทั้งเฟรมหาย → model รอ response ค้าง | `jsonPrimitive.content` throw | `LiveProtocol.parseToolArgs` (ใช้ `ToolArgParser`) |
| 6 | ลูปอ่าน WebSocket หยุดระหว่างรัน tool | `nativeToolCallFlow` ไม่มี buffer + รัน tool ทีละตัว | buffer 64; bridge รันแต่ละ call ใน job แยก; รองรับ `toolCallCancellation` |
| 7 | พูดแทรกแล้วเสียงเก่ายังเล่นต่อ | chunk ค้างใน SharedFlow ไม่ถูกล้าง; buffer 128 ทำให้ WS reader ค้าง | `LiveAudioChunk(epoch)` + `audioEpoch++` ตอน interrupted; collector ทิ้ง chunk epoch เก่า; buffer 4096 |
| 8 | UI บอกว่า "เสียงระหว่างเชื่อมต่อถูกเก็บไว้" แต่จริงถูกทิ้ง | buffer 400 chunk แต่ `clear()` ตอน READY | เลิก buffer (กัน NOT_CONSISTENT) และแก้ข้อความเป็น "รอให้ AI ทักก่อนแล้วค่อยพูด" |

### 🟠 กระทบผู้ใช้
| # | ปัญหา | การแก้ไข |
|---|---|---|
| 9 | ข้อความ turn ก่อนถูกทับในแชท (`append = isFirst`) และลำดับ update สลับ (`scope.launch` ต่อ update) | chunk แรกของ turn = bubble ใหม่เสมอ; emit ตรงตามลำดับเฟรม |
| 10 | "ราคาทองจะไปที่ 2400" เปิด Google Maps | `LiveIntentMatchers.canRedirectMisroutedTool` = tool กลุ่มที่มักเรียกผิด **และไม่ใช่คำถามเทรด**; navigation ต้องมีคำกริยาเดินทาง |
| 11 | ขอ D1 ไม่ได้แม้ผู้ใช้ระบุ | `allowedTradingTimeframe(args, prompt)` อนุญาต D1/W1 เมื่อผู้ใช้พูดถึง |
| 12 | Session resumption ไม่เคยทำงาน | ส่ง `session_resumption: {}` ตั้งแต่ครั้งแรก (ตามเอกสาร); self-healing ปิดเองถ้า setup ถูกปฏิเสธ; reconnect ที่ไม่มี handle ใส่ `recentTurns` เป็น history |
| 13 | Restart (เปลี่ยนเสียง) race → session ใหม่ถูกปิด/ไม่ reconnect | start รอ `cancelAndJoin` + disconnect job; finally ของ loop เก่าไม่ล้าง socket ใหม่ (`sessionGeneration`, `connectCallId`) |
| 14 | Greeting ค้างหลังสลับโหมด แล้วโผล่ตอน reconnect | ตั้ง greeting เฉพาะเมื่อ session ยังไม่ READY; `startVoiceInput` ใช้ `setLiveGreetingOnReadyIfAbsent` (greeting ยืนยันเสียงใหม่ไม่ถูกทับอีก) |
| 15 | HotwordDetector แย่งไมค์กับ Live ตอนปิดจอ | ไม่เปิด detector เมื่อ `LiveSessionBridge.isActive()` |
| 16 | Alert ถูกตัดเสียงท้ายประโยค | ไม่ close socket เมื่อ transcript เกิน 350 ตัวอักษร (log อย่างเดียว) |

### 🟡 คุณภาพ / ความปลอดภัย
- `LiveProtocol.redactSecrets` สำหรับ error ของการเชื่อมต่อ (ไม่ส่ง throwable ที่มี `?key=` ลง logcat/UI)
- `@Volatile` ให้ state ที่ข้าม coroutine ใน `LiveGeminiService`; `AtomicBoolean/AtomicLong` ใน alert engine
- `PcmAudioEngine`: `@Volatile isRecording`, `stop()` → `join(300)` → `release()`
- `stopVoiceInput()` เรียก `stopPlaying()` (กดหยุดแล้วเงียบทันที)
- Mute ส่ง `realtimeInput.audioStreamEnd`; TTS fallback คืนค่า mute เดิมของผู้ใช้
- ตั้ง interruption handler ครั้งเดียว; "🟢 LIVE READY" ขึ้นครั้งเดียวต่อการกดเริ่ม
- Turn ที่ถูกขัดจังหวะถูกบันทึกลง memory (metadata `interrupted`)
- Tool response ผูก `sessionGeneration`; ถ้า socket เปลี่ยน ส่งผลเป็น realtime text แทน (`deliverIfStale`)
- `voice_set_profile` ตอบ tool ก่อนสั่ง restart session
- บั๊กเดิมที่เจอระหว่างย้าย logic: "เปิดโหมด…" / "เปิดกล้อง" เคยถูกตีความเป็นคำสั่งปิด เพราะ "ปิด" เป็น substring ของ "เปิด"

## 1.5 รอบที่ 2 — ของที่เหลือจาก "ยังไม่ได้ทำ"
| # | เรื่อง | การดำเนินการ |
|---|---|---|
| 17 | **ชุด tool ตามโหมด** | `ToolRegistry.getLiveGeminiTool(petMode)` — โหมดสัตว์เลี้ยงตัด trading/MT5/SMC/strategy/file ออก เหลือ builtin + camera + device + custom + skills; setup เล็กลงและโมเดลไม่หลงเรียก `trading_*` (ต้นเหตุที่ต้องมี guard หลายชั้น) |
| 18 | **สลับ persona กลาง session** | `setAlwaysLiveProfile` restart session จริง (system prompt / tool set / voice ถูกส่งตอน setup เท่านั้น) พร้อม guard ไม่ให้ session ที่ผู้ใช้เพิ่งปิดถูกเปิดใหม่; `respond()` ไม่เล่าผล tool ย้อนหลังสำหรับ UI-only tools เพราะ greeting ของ session ใหม่พูดแทนแล้ว |
| 19 | **เปลี่ยนเสียง** | ใช้ `restartVoiceSession()` (รอ disconnect จริง) แทน `delay(800)` แบบเดา |
| 20 | **Hotword ตรวจคำจริง** | `HotwordVerifier` + `WakeWordMatcher`: energy VAD → ถอดเสียงสั้นๆ (พยายาม offline ก่อน) → ปลุกเฉพาะเมื่อได้ยิน "จาวิส/jarvis" (หรือคำที่ตั้งไว้); ถ้าเครื่องถอดเสียงไม่ได้ ใช้พฤติกรรมเดิม |
| 21 | **Context window compression** | เปิด `slidingWindow` (แก้ data class ให้ serialize จริง — เดิม `encodeDefaults=false` ตัดทิ้งจนเหลือ `{}`) พร้อม self-heal: ถ้า setup ถูกปฏิเสธจะปิด compression ก่อน แล้วค่อยปิด resumption |
| 22 | **Session ตายแล้วไมค์ยังอัด** | VoiceController จบ session และแจ้งในแชทเมื่อ state เป็น `Error` หรือ `Disconnected` ทั้งที่ผู้ใช้ยังเปิดไมค์ |
| 23 | **แจ้งเตือนแทรกด้วย TTS ทั้งที่ Live เปิดอยู่** | `announceNotification` ใช้ `sendLiveRealtimeTextWhenReady(5s)` เผื่อกำลัง reconnect ก่อนตกไป TTS |
| 24 | **Logcat ท่วมตอนเปิดกล้อง** | log เฟรมวิดีโอเป็นช่วง (ทุก 30 เฟรม) |
| 25 | **ข้อมูลสำหรับปรับ thinking level** | log `⏱️ First audio …ms after user turn` ทุก turn เพื่อวัด latency จริงก่อนตัดสินใจตั้ง `thinking_level` |

## 2. ไฟล์ใหม่
- `data/LiveProtocol.kt` — URL, redaction, tool args, transcript merge, reconnect history
- `ai/LiveIntentMatchers.kt` — keyword guards ของ LiveToolBridge
- `ai/LiveSessionBridge.kt` — service ↔ Live session หลัก
- `controller/LiveLocalCommandParser.kt` — คำสั่งลัดฝั่งเครื่อง
- `voice/WakeWordMatcher.kt` — เทียบคำปลุก (pure, ทดสอบได้)
- `androidMain/service/HotwordVerifier.kt` — ถอดเสียงยืนยันคำปลุก
- `commonTest/.../LiveSessionReviewFixesTest.kt`

## 3. ยังไม่ได้ทำ / ต้องยืนยันบนเครื่องจริง
- **`thinking_level` ของ session หลัก**: ยังไม่ตั้ง — รอดู log `⏱️ First audio …ms after user turn` จากการใช้งานจริงก่อน ถ้าค่าสูงค่อยตั้ง `low` เหมือน alert engine
- **Session resumption / compression**: ถ้า log ขึ้น `🧯 Setup with contextWindowCompression rejected` หรือ `🧯 Setup with sessionResumption rejected` แปลว่า server ปฏิเสธและระบบปิดให้เองแล้ว (ใช้งานต่อได้ปกติ)
- **คำปลุกบนเครื่องจริง**: SpeechRecognizer แบบ offline ขึ้นกับรุ่น/ภาษาที่ติดตั้ง — ถ้า log `SpeechRecognizer unavailable` บ่อย ค่อยพิจารณาโมเดล keyword spotting แบบฝังในแอป
- **`AlwaysLiveManager`** ยังไม่มี unit test (ต้องใช้ Android context) — logic ที่แยกออกมาได้ (`WakeWordMatcher`, parsers, matchers) มี test แล้ว
- **ทดสอบจริงบนเครื่อง**: barge-in, GoAway → resume, สลับโหมดกลางบทสนทนา, alert ระหว่างคุย, pet minimize/expand, mute

## 4. Verification
- `./gradlew :composeApp:compileDebugKotlinAndroid` — ผ่าน
- `./gradlew :composeApp:testDebugUnitTest` — 360 tests, ผ่าน 359
  - `LiveSessionReviewFixesTest` ใหม่ 24 tests ผ่านทั้งหมด (protocol, intent guards, local commands, audio epoch, chat bubble, user-turn finalize, tool args/generation, pet tool set, compression config, wake word)
  - ตก 1: `PetModeTest > Pet Mode persona prompt ... cute robot pet rules` — assertion หา "ห้ามวิเคราะห์การเงิน" ใน `PET_LIVE_SYSTEM_PROMPT` ซึ่งไม่มีใน `JarvisPersona.kt` (ไฟล์นี้ไม่ได้ถูกแก้ในรอบนี้ — ปัญหาเดิม)
- ยังไม่ได้ทดสอบบนเครื่องจริง (Live session, barge-in, resumption, alert ระหว่างคุย, pet minimize/expand)

---
**Links**: [[LiveGeminiService]] | [[LiveToolBridge]] | [[Always_AI_Live_Mode]] | [[Changelog_2026-09-09_Gemini_3_1_Live_Model_Primary]] | [[Current_Tasks]]

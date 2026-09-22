---
title: "Changelog 2026-09-16: Free Tier Model Policy, Live Modes & Agent Multi-Session"
category: "Changelog"
tags: ["gemini", "free-tier", "rate-limits", "model-config", "live-api", "agent", "multi-session"]
sources: ["ModelConfig.kt", "GeminiService.kt", "SettingsController.kt", "ToolRegistry.kt", "ToolExecutor.kt", "LiveModeState.kt", "AgentTaskManager.kt", "LongTaskRunner.kt", "JarvisViewModel.kt", "JarvisPersona.kt", "LiveSessionReviewFixesTest.kt", "DynamicModelTest.kt", "DeviceControlTest.kt"]
created: "2026-09-16"
updated: "2026-09-16"
---

# Changelog 2026-09-16: Free Tier Model Policy, Live Modes & Agent Multi-Session

ต่อจาก [[Changelog_2026-09-16_Live_Voice_Review_Fixes]] — ปรับระบบให้ตรงกับรายการโมเดลและโควตาจริงของ free tier
(ตรวจจาก AI Studio console ของ project + เอกสาร rate limits ทางการ) และวางสถาปัตยกรรม "Live = เอเจนต์หลัก"

## 1. แชทใช้ flash-lite เท่านั้น
- `DEFAULT_MAIN_MODEL = gemini-3.5-flash-lite`, `CHAT_MODELS = [3.5-flash-lite, 3.1-flash-lite]` (500 req/วัน ต่อตัว = 1,000/วัน)
- `getFallbackChain()` คืนเฉพาะโมเดลที่ `isChatAllowedModel()` (ตระกูล lite) — ตระกูล flash 20 req/วัน ไม่อยู่ในสายแชทอีกต่อไป
- `updateAvailableModels()` เรียง lite ขึ้นก่อน flash
- SettingsController migrate ค่าเดิมที่ไม่ใช่ lite (เช่น `gemini-3.6-flash`) ให้เป็น flash-lite อัตโนมัติ
- 429 แบบ PerDay → `markModelQuotaExhausted()` (รีเซ็ตเที่ยงคืน Pacific) แล้วดันโมเดลไปท้าย chain; ต่อสายจาก `GeminiService.markKeyDead`
- `chatQuotaExhaustedMessage()` สำหรับแจ้งผู้ใช้เมื่อหมดทั้งคู่ (โหมดเสียงยังใช้ได้เพราะ Live ไม่จำกัดจำนวนครั้ง)
- **เหตุผลเชิงระบบ**: งานวนลูป (เฝ้าราคา/เงื่อนไข) ใช้ automation engine ของแอปเอง โมเดลถูกเรียกเมื่อเข้าเงื่อนไขจริงเท่านั้น จึงไม่ต้องพึ่งโมเดลโควตาสูง

## 2. Live — รหัสรุ่นจริง + เลือกเองจาก ListModels
- Seed: `gemini-3.8-live` (ค่าเริ่มต้น) → `gemini-3.1-flash-live-preview` → `gemini-3.8-live-extended-thinking` (`DEEP_THINKING_LIVE_MODEL`) → `gemini-2.5-flash-native-audio-preview-12-2025` (สำรองสุดท้าย)
- ตัด `gemini-2.0-flash-exp` (ปิดบริการ) และ `native-audio-preview-09-2025` ออก
- หลัง sync ListModels: จัดอันดับจากโมเดล `bidiGenerateContent` จริง เรียงเวอร์ชันใหม่ก่อน → รุ่นใหม่ที่ Google เพิ่มถูกใช้เองโดยไม่ต้องแก้โค้ด
- `isConversationalLiveModel()` กันรุ่นเฉพาะทาง (`transcribe` / `live-translate` / `tts`) ออกจากสายผู้ช่วย
- จุดที่ hardcode ชื่อรุ่นไว้ (camera provider, LLM provider lists, ADK, settings picker) เปลี่ยนมาอิง `ModelConfig` ทั้งหมด

## 3. โหมด = บทบาท ไม่ใช่ความสามารถ
- `LiveModeState` เป็นแหล่งความจริงเดียวของโหมดปัจจุบัน (ตั้ง `JarvisPersona.isPetMode` ให้ด้วย)
- ผู้ช่วยส่วนตัว / ขับรถ / สัตว์เลี้ยง = ผู้ช่วยตัวเดียวกัน ใช้ tool ได้เหมือนกันหมด (ยกเลิกการตัด tool ของ pet mode ที่ทำไว้รอบก่อน)
- ต่างกันจุดเดียว: `ToolRegistry.DEVICE_CONTROL_TOOLS` (อ่านจอ/แตะ/พิมพ์/เลื่อน/ปุ่ม/เปิดแอป/ปลุก-พักจอ) เปิดเฉพาะ **โหมดขับรถ**
  - กันสองชั้น: ไม่ส่งใน setup ของ session + บล็อกซ้ำตอน execute พร้อมข้อความบอกให้สั่ง "เปิดโหมดขับรถ" ก่อน
  - โหมดอื่นยังทำงานเบื้องหลังได้ตามปกติแม้หน้าจอพัก

## 4. Live = เอเจนต์หลัก (multi-session)
- `AgentTaskManager` + tool ใหม่ 4 ตัว: `agent_task_start` / `agent_task_list` / `agent_task_status` / `agent_task_cancel`
- flow: ผู้ใช้สั่งด้วยเสียง → Live เรียก `agent_task_start` แล้วตอบรับทันที → งานจริงรันด้วยโมเดลแชท (flash-lite + tool ครบ) เบื้องหลัง → เสร็จแล้วการ์ดลงแชท + ส่งกลับเข้า Live ให้พูดรายงาน (ใช้ท่อ `LongTaskRunner.completions` เดิม)
- `LongTaskRunner` เพิ่ม `MAX_CONCURRENT = 3`, `canStartNew()`, `snapshot()`, `cancel(id)` (เก็บ Job ต่อ task)
- `JarvisPersona` LIVE_RULES ข้อ 2.5: อธิบายบทบาทเอเจนต์ + กติกาการควบคุมเครื่องเฉพาะโหมดขับรถ

## 5. Verification
- `./gradlew :composeApp:compileDebugKotlinAndroid` — ผ่าน
- `./gradlew :composeApp:testDebugUnitTest` — 365 tests ผ่าน 364
  - ใหม่/อัปเดต: chat chain = lite เท่านั้น, โควตาหมดถูกดันท้าย chain, live seed/รุ่นเฉพาะทาง, tool เท่ากันทุกโหมดยกเว้นกลุ่มควบคุมเครื่อง, agent task (degrade ปลอดภัย + รันจริงแล้วรายงานผล), DeviceControlTest ยืนยันการบล็อกนอกโหมดขับรถ, DynamicModelTest อัปเดตรุ่นใหม่
  - ตก 1 ข้อเดิม: `PetModeTest > Pet Mode persona prompt ...` (assert หาข้อความที่ไม่มีใน `JarvisPersona` — มีมาก่อนงานรอบนี้)

## 6. ยังไม่ได้ทำ / ต้องยืนยันบนเครื่องจริง
- **โหมดประชุม / โหมดแปลภาษา**: ต้องทดสอบก่อนว่า `gemini-3.5-transcribe-live` และ `gemini-3.5-live-translate-preview` รองรับ tool/บทสนทนาแค่ไหน แล้วค่อยออกแบบ flow (ถอดเสียงยาว → ให้ chat สรุป)
- **ทดสอบ agent flow จริง**: สั่งงานด้วยเสียง → ฟังว่า AI ตอบรับทันทีและรายงานผลกลับเมื่อเสร็จ
- **งบโควตาแยกแชท/งานเบื้องหลัง**: ตอนนี้ใช้โควตาก้อนเดียวกัน (จำกัดด้วยเพดาน 3 งานพร้อมกัน) ถ้าใช้จริงแล้วชนกันค่อยเพิ่มการกันสัดส่วน
- **`thinking_level` ของ Live session หลัก**: รอดู log `⏱️ First audio …ms after user turn`


## 7. ผลทดสอบบนเครื่องจริง + แก้ต่อ (2026-09-16 14:05)

### ที่ยืนยันว่าทำงานถูกแล้ว
- แชท chain = lite ล้วน: `[gemini-3.5-flash-lite, gemini-3.1-flash-lite, gemini-3.1-flash-lite-preview, gemini-2.5-flash-lite, gemini-flash-lite-latest]`
- `🧰 Live tools for setup: 127 (profile=CONTROL)` — กลุ่มควบคุมเครื่องถูกตัดออกนอกโหมดขับรถ
- `✅ Live session READY (1246ms, resumption=on)` + `♻️ Session resumption handle updated` ซ้ำๆ → resumption ใช้ได้จริง และ compression ไม่ถูกปฏิเสธ
- `🧹 Dropped 26 pre-READY mic chunks`, greeting ส่งครั้งเดียว, `🚫 Tool call cancelled by server` ถูกจับ, `⚡ Interrupted … audioEpoch=1`
- `⏱️ First audio 13985ms after user turn` — metric ใหม่ชี้เป้าปัญหาได้ทันที

### บั๊กที่ log เปิดโปง แล้วแก้ในรอบนี้
1. **Thai transcript มีช่องว่างคั่นคำ** (`"ราคา ทองคำ เท่า ไหร่ ตอน นี้"`) ทำให้ keyword guard ทั้งหมดพลาด
   - ผลคือ profile ไม่เป็น `USER_QUERY` → model เรียก `trading_smc_analysis` ซ้ำคู่กับ `trading_price` → รอ 14 วินาทีกว่าจะได้ยินเสียง
   - แก้: `LiveIntentMatchers.normalize()` + `matchesAny()` เทียบทั้งแบบมีช่องว่างและตัดช่องว่าง ใช้ทั้ง guard และ `LiveLocalCommandParser`
2. **ส่ง tool response ของ call ที่ถูกยกเลิก** — tool จับ `CancellationException` เองแล้วคืน error ปกติ ทำให้ bridge ส่งผลกลับไป
   - แก้: bridge จำ id ที่ถูกยกเลิก (ล่าสุด 64 รายการ) แล้ว `respond()` ข้ามการส่งทั้งหมด
3. **timeframe guard อ่านแค่ `symbol`/`timeframe`** แต่ของจริงโมเดลส่ง `interval=m15` → เพิ่มการอ่าน `interval`/`tf`
4. **Live ยังใช้ 3.1** เพราะค่าใน DB เป็นของเดิม → เพิ่ม one-time upgrade ไป `gemini-3.8-live` (จำด้วย flag `live_model_upgraded_2026_09_16`) หลังจากนั้นเคารพค่าที่ผู้ใช้เลือกเสมอ; ถ้ารุ่นใหม่ต่อไม่ติดจะ fallback เองใน 6 วินาที
5. **log รายชื่อโมเดล Live ที่ API คืนมา** เพื่อยืนยันรหัสรุ่นจริงของ project (`live=[...]`)

### หมายเหตุ
- `❌ Strict TV candle source violation` ของ SMC รอบแรกเกิดเพราะ call ถูกยกเลิกกลางคัน (ดึงแท่งเทียนไม่ทัน) รอบถัดมาดึงได้ปกติ (`TV new candles XAUUSD/m15: +300 bar(s)`)

---
**Links**: [[Model_Policy_Free_Tier]] | [[Changelog_2026-09-16_Live_Voice_Review_Fixes]] | [[LiveGeminiService]] | [[Current_Tasks]]

## 8. ผลทดสอบรอบสอง + แก้ต่อ (2026-09-16 14:24)

### ยืนยันจาก log
- `One-time live model upgrade: 'gemini-3.1-flash-live-preview' → 'gemini-3.8-live'` แล้ว `✅ Live session READY (1359ms)` — **รหัสรุ่น `gemini-3.8-live` ถูกต้องและใช้งานได้จริง**
- `⏱️ First audio 1525ms after user turn` — ลดจาก 13,985ms ของรอบก่อน (fix เรื่องช่องว่างในภาษาไทยได้ผล)
- ไม่มี `🛡️ Profile guard blocked` ที่ผิดพลาดอีก

### แก้เพิ่มรอบนี้
1. **logcat ยาวเกิน**: `🤖 JARVIS (Progress)` เดิม log ข้อความสะสมทุกชิ้น → เปลี่ยนเป็น log เฉพาะชิ้นใหม่ (`🎤 User +"…"` / `🤖 JARVIS +"…"`) และสรุปข้อความเต็มครั้งเดียวตอน `🏁 Turn Complete`
2. **ห้องแชทเต็มไปด้วยคำพูดของ AI**: กลับไปเป็นพฤติกรรมเดิม — Live text ที่ไม่ใช่ `isStatic` (transcript สด) ไม่ถูกแสดงในแชทอีก เหลือเฉพาะรายงาน/ผล tool ที่สำคัญ (transcript ยังบันทึกลง memory/DB ตามเดิม)
3. **custom tool / skill หยุดกลางทาง** (`custom_gold_check` แล้วจบเลย):
   - ต้นเหตุ: ผลของ skill เป็น "ขั้นตอนที่ต้องทำต่อ" แต่ bridge ต่อท้ายด้วย `[VOICE PRESENTATION POLICY]` ซึ่งสั่งให้สรุปให้ผู้ใช้ฟัง → โมเดลเลยหยุด
   - แก้: ตรวจผลที่ขึ้นต้นด้วย `🛠️ เปิดใช้งานเครื่องมือ` แล้วต่อท้ายด้วย `[CHAIN RULE]` แทน (ให้เรียก tool ตามขั้นตอนจนครบ พูดคั่นสั้นๆ ว่ากำลังทำ ห้ามสรุปก่อนได้ข้อมูล) และไม่ dump ขั้นตอนลงแชท
   - ปลด profile guard ให้ turn ที่อยู่ใน skill chain (skill กำหนด TF เอง เช่น 15m/1h/4h/1D) พร้อมขยายเพดานจาก 3 เป็น 6 ครั้ง

## 9. โหมดประชุม & โหมดแปลภาษา (2026-09-16)

แยกออกจากห้องแชทและโหมดผู้ช่วยทั้งหมด — ปุ่มบนแถบเครื่องมือ (ไอคอนคน 2 คน / ไอคอนแปลภาษา) เปิดหน้าจอของตัวเอง
รายละเอียดความสามารถและ UI: [[Meeting_And_Translate_Modes]]

- `data/LiveSpecialistService.kt`: session ของโมเดลเฉพาะทาง (`gemini-3.5-transcribe-live`, `gemini-3.5-live-translate-preview`)
  - โหมดประชุม: `responseModalities=[TEXT]`, `inputAudioTranscription{languageCodes:[], mode:SMART, customVocabulary}` และ **ต่อ session ใหม่อัตโนมัติทุก 8 นาที** (เพดานของโมเดลคือ 10 นาที)
  - โหมดแปล: `responseModalities=[AUDIO]` + `translationConfig{targetLanguageCode, echoTargetLanguage}` + transcript ทั้งสองฝั่ง
- `controller/SpecialistSessionController.kt`: ไมค์แยกของตัวเอง, รวมข้อความเป็นย่อหน้า, จับเวลา, พัก/หยุด, สรุปด้วย flash-lite, ส่งผลเข้าห้องแชท, ปิด session ผู้ช่วยก่อนเริ่ม (กันแย่งไมค์)
- UI ใหม่ 2 หน้า: `MeetingScreen` (ข้อความเรียลไทม์ + เวลา + พัก/หยุดและสรุป), `TranslateScreen` (เลือกภาษาปลายทาง + สวิตช์ echo + คู่ข้อความ ได้ยิน/คำแปล)
- **ข้อจำกัดที่ยืนยันจากเอกสาร**: โหมดสด **แยกเสียงผู้พูดไม่ได้** และไม่มี timestamp ระดับคำ (มีเฉพาะโหมดประมวลผลไฟล์ `gemini-3.5-transcribe` สูงสุด 8 คน) — ถ้าต้องการ "ใครพูดอะไร" ต้องทำ pass ที่สองจากไฟล์เสียง (ยังไม่ได้ทำ)
- tests: setup JSON ของทั้งสองโหมด + ยืนยันว่าโมเดลเฉพาะทางไม่หลุดเข้าสาย Live ของผู้ช่วย

### 9.1 ถาม AI ระหว่างประชุม
- `askDuringSession()` + `buildAskPrompt()` — แนบบทประชุมจนถึงตอนนั้น (ท้ายสุด 20,000 ตัวอักษร) ไปกับคำถาม ให้ flash-lite ตอบโดยรู้บริบท
- คำตอบเป็นการ์ดในหน้าจอ ไม่อ่านออกเสียง (กันไมค์อัดเสียง AI กลับเข้าบทประชุม) และติดป้าย `[AI]` ในบันทึกที่ส่งเข้าแชท
- tests: prompt ต้องมีบริบท + คำถาม, บทยาวมากต้องเก็บช่วงท้ายไว้
- **ยังไม่ทำ**: pass ที่สองเพื่อแยกผู้พูดจากไฟล์เสียง (ผู้ใช้เลือกยังไม่ทำในรอบนี้)

### 9.2 หน้าประชุมแบ่ง 2 ส่วน + ถามด้วยเสียง
- หน้าประชุมแยกเป็น "บทประชุม" (บน) และ "ถามผู้ช่วย" (ล่าง) พร้อมช่องพิมพ์ + ปุ่มส่ง + ปุ่มไมค์
- ปุ่มไมค์ = `askByVoice()` → พักบันทึก + เปิด Live assistant พร้อมคำถาม (ผ่าน greeting-on-ready) → ตอบเป็นเสียง คุยต่อได้ → `endVoiceAsk()` ปิด session ผู้ช่วยแล้วกลับมาบันทึกต่อ
- คำตอบด้วยเสียงถูกดักจาก transcript ของผู้ช่วยมาบันทึกลงแชทของหน้าประชุม
- tests: `buildVoiceAskPrompt` ต้องมีบริบทประชุม ห้าม markdown และกรณีไม่พิมพ์คำถามต้องให้ผู้ช่วยทักแล้วรอฟัง

### 10. รอบทดสอบจริง 2026-09-16 23:0x — เสียง/ความลึกของคำตอบ (multi-session)

**10.1 เสียงต่างกันระหว่าง gemini-3.8-live กับ gemini-3.1-flash-live-preview — ไม่ใช่บัค**
- setup ส่ง `speech_config.voice_config.prebuilt_voice_config.voiceName` ค่าเดียวกันทุกโมเดล (ไม่มีเงื่อนไขแยก) และ playback 24 kHz เท่ากัน
- ชื่อเสียงเดียวกันแต่ audio stack คนละรุ่น → timbre/จังหวะต่างกันเป็นปกติ; 3.8 รองรับ affective dialog + proactive audio ส่วน 3.1 Flash Live ไม่รองรับ (เอกสาร Live API capabilities)
- **จุดที่ต้องระวัง**: ระบบ promote โมเดล Live ที่เชื่อมสำเร็จแล้ว `updateLiveModelSilently()` เขียนทับค่าใน Settings ถาวร — fallback ครั้งเดียวจะติดโมเดลใหม่ไปตลอด ทำให้ "เสียงเปลี่ยนเอง"
- เพิ่มชื่อโมเดล/เสียงเข้า log ตอนเชื่อมต่อสำเร็จ: `✅ Live session READY model=… voice=… (…ms, resumption=…)`

**10.2 คำตอบสั้น/ตื้น**
- สาเหตุที่ 1 — schema ของ `analyze_and_display_report` เขียนว่า "speak ONLY a short summary (2-4 sentences)" ขัดกับกฎ 5-8 ประโยคใน persona และกฎ 8-12 ประโยคหลัง tool → แก้เป็น **6-10 ประโยค** ระบุให้ครอบคลุม ข้อสรุป / ตัวเลขสำคัญ 3-5 จุด / จุดที่ต้องระวัง และเปลี่ยน description ให้ "พูด voice_summary ที่เขียนไว้" (เดิมโมเดลอ่านสรุปสั้นของตัวเองแล้วจบ)
- สาเหตุที่ 2 — session หลักไม่เคยส่ง `thinking_config` เลย Gemini 3.x Live จึงใช้ค่าเริ่มต้น `minimal` (เน้น latency ต่ำสุด) → ตั้ง `thinkingLevel = "low"` ผ่านฟิลด์ใหม่ `LiveGeminiService.liveThinkingLevel` (ส่งเฉพาะรุ่น `gemini-3*` เพราะ 2.5 native audio ใช้ `thinking_budget` คนละฟิลด์) — ไม่ต้องสลับไป `gemini-3.8-live-extended-thinking`

**10.3 ไม่พูดผลของ tool ตัวแรก**
- log: `✅ Tool response sent` → 0.8 วินาทีต่อมา `⚡ Interrupted (VAD/user)` → `Turn Complete (audio: 0 bytes)` — server ทิ้งคำตอบทั้ง turn เพราะคิดว่าผู้ใช้พูดแทรก (เอกสาร: interruption ทำให้ pending function call/คำตอบถูกทิ้ง)
- แก้ที่ VAD ของ session หลัก: `startOfSpeechSensitivity = START_SENSITIVITY_LOW` และ `prefixPaddingMs` 300 → 500 (`silenceDurationMs` คงที่ 1200) ลด false barge-in จากเสียงรบกวน/ลมหายใจ ขณะยังขัดจังหวะด้วยเสียงพูดจริงได้

### 11. รอบทดสอบเทียบ 2 โมเดล (23:29 = 3.8-live, 23:31 = 3.1-flash-live-preview)

- **3.8-live ผ่านทั้งชุด**: ถาม SMC แล้วถามราคาซ้อนระหว่างรอ tool → ตอบราคาก่อน แล้วกลับมาตอบ SMC ตอน tool response มาถึง (SMC ใช้เวลา 14 วิ) ไม่มี turn ไหนหาย
- **3.1-flash-live-preview ตกรอบแรกอีกครั้ง**: `Tool response sent` (23:31:53.239) → `⚡ Interrupted (VAD/user)` (23:31:53.559, ห่าง 0.3 วิ) → `Turn Complete (audio: 0 bytes)` — ยืนยันสาเหตุเดียวกับข้อ 10.3 (log รอบนี้เป็นบิลด์ก่อนแก้ VAD)
- ข้อมูลไม่หาย: ถามซ้ำ "แล้ว SMC ที่วิเคราะห์ล่ะ" โมเดลตอบจากผลเดิมในบริบทได้ทันที (first audio 0 ms) ไม่เรียก tool ซ้ำ
- **แก้ log/metric เพิ่ม**
  - `⏱️ First audio …` เดิมวัดจาก `userTurnFinalAtMs` ที่ตั้งตอน model เริ่มตอบ → ได้ทั้ง 0 ms และ 67,258 ms (ค้างจาก session ก่อน) เปลี่ยนมาวัดจาก `lastUserSpeechAtMs` (transcription ชิ้นสุดท้ายของผู้ใช้) และ**รีเซ็ตตัวจับเวลาทุกครั้งที่เปิด session ใหม่** ข้อความเปลี่ยนเป็น `after user speech`
  - `♻️ Session resumption handle updated` ของ 3.1 ขึ้นทุก 1-2 วินาทีจนท่วม logcat → throttle เหลือคาบละ 60 วินาที

### 12. รอบทดสอบ 23:53–00:00 (บิลด์ที่มีข้อ 10-11 แล้ว)

**12.1 regression: `gemini-3.8-live` ไม่รองรับ thinking_level → ถูก rotate ทิ้งทุกครั้ง**
- `Session closed: NOT_CONSISTENT — Thinking level is not supported for this model.` ทุกครั้งที่ต่อ `gemini-3.8-live` ทำให้ตกไป `gemini-3.1-flash-live-preview` ตลอด (เกิดจากข้อ 10.2 ที่เริ่มส่ง `thinking_config`)
- ยืนยันจากล็อกว่า **`gemini-3.1-flash-live-preview` และ `gemini-3.8-live-extended-thinking` รับ `thinking_level` ได้ มีแต่ `gemini-3.8-live` ที่ไม่รับ**
- ลำดับ self-heal ยังโทษผิดตัว: ปิด `contextWindowCompression` → ปิด `sessionResumption` → rotate โมเดล ทั้งที่ข้อความบอกสาเหตุตรงๆ ว่าเป็น thinking (ผลข้างเคียงคือ session ต่อๆ มาขึ้น `resumption=off`)
- **แก้**: เพิ่ม `thinkingUnsupportedModels` + `supportsThinkingLevel()` และย้ายการตรวจ "thinking" ขึ้นเป็น**เงื่อนไขแรก**ของ self-heal — เจอครั้งเดียวจำไว้ทั้ง service ไม่ส่งซ้ำ และไม่ไปปิด compression/resumption ทิ้งฟรีๆ อีก

**12.2 สิ่งที่ทำงานถูกแล้วในรอบนี้**
- `✅ READY model=… voice=Aoede` และ throttle `♻️ resumption` ใช้ได้จริง
- `⏱️ First audio … after user speech` ให้เลขที่ตีความได้แล้ว (17.9 วิ / 38.6 วิ = ช่วงที่รอ tool + agent task จริง)
- `⚡ Interrupted` ที่เหลือเป็น **barge-in จริง** (ผู้ใช้ยิงคำถามซ้อน 3 ข้อรวด) ไม่ใช่ false positive — โมเดลตอบครบทั้ง 3 เรื่องในเทิร์นเดียวตอนข้อมูลพร้อม
- **multi-session delegation ครบวงจร**: `agent_task_start` → chat flash-lite ทำ 3 รอบ tool (deep analysis + SMC) → `📦 LongTask เสร็จ` → ส่ง `[SYSTEM] งานพื้นหลังเสร็จแล้ว` เข้า Live → จาวิสรายงานผลด้วยเสียงต่อทันที
- PROFILE_GUARD ทำงานถูก: `trading_deep_analysis_suite` ถูกบล็อกเมื่อผู้ใช้ถามแค่ราคา แล้วโมเดลหันไปใช้ `trading_price` แทน

### 13. รอบทดสอบ 00:13–00:17 (2026-09-17)

**13.1 self-heal thinking ใช้ได้จริง**
- `🧯 gemini-3.8-live ไม่รองรับ thinking_level — ปิดสำหรับโมเดลนี้แล้วลองใหม่` → `✅ READY model=gemini-3.8-live … resumption=on` ภายใน 1 วินาที ไม่ rotate โมเดลทิ้ง และ compression/resumption ไม่ถูกปิดผิดตัวอีก

**13.2 ถามซ้อน 3 คำถาม → ตอบแค่คำถามล่าสุด**
- 00:13:37 "วิเคราะห์ BTC 5 มิติ" → tool, 00:13:45 "ปฏิทินเศรษฐกิจ" → tool, 00:13:53 "ราคาทองคำ" → tool
- ผล tool กลับมาครบทั้ง 3 ตัว (00:13:50 / 00:13:51 / 00:13:54) แต่ `gemini-3.8-live` **พูดเฉพาะราคาทองคำ** ทิ้งอีกสองคำถามเงียบๆ — และรอบนี้**ไม่มี `⚡ Interrupted`** จึงไม่ใช่ VAD แต่เป็นการเลือกตอบของโมเดลเอง
- `gemini-3.8-live-extended-thinking` จัดการดีกว่า: ตอบรับทุกคำถามด้วยเสียงสั้นๆ แล้วย้อนกลับมารายงานผล 5 มิติเต็ม ("ตอนนี้กลับมาที่ผลการวิเคราะห์ บีทีซี…") แต่ก็ยังตกปฏิทินไป
- **แก้**: เก็บ `toolCallQuestions[callId] = คำถามขณะเรียก tool` แล้วตอนส่งผลกลับ ถ้าคำถามนั้นไม่ใช่คำถามล่าสุด จะเติมหัวเรื่อง `[PENDING QUESTION] … ต้องพูดตอบให้ครบด้วย` ไว้หน้าผลลัพธ์ + เพิ่มกฎในข้อ 2.5 ของ persona ว่าถามซ้อนต้องตอบครบทุกข้อ

**13.3 เน็ตหลุดแล้วเผา model chain ทิ้ง**
- 00:17:07 `SocketException` → reconnect → `UnknownHostException: Unable to resolve host` (เน็ตมือถือหลุด) ระบบไล่สลับโมเดลครบทั้ง chain ภายใน ~50 ms (3.8-extended → 3.8-live → 3.1 → 2.5-native ×2) ทั้งที่ทุกตัวล้มด้วยสาเหตุเดียวกันคือ DNS
- **แก้**: เพิ่ม `isNetworkUnreachableException()` (UnknownHost / NoRouteToHost / ConnectException / unable to resolve host / network is unreachable) — เจอกรณีนี้จะ**ไม่สลับโมเดล** ใช้ backoff retry โมเดลเดิมอย่างเดียว (รอบนี้โชคดีที่วนกลับมาที่ extended-thinking พอดี ถ้าลำดับต่างออกไปจะค้างอยู่กับโมเดลที่แย่กว่า)

### 14. รอบทดสอบ 00:30 — `[PENDING QUESTION]` ได้ผลบางส่วน

- **ดีขึ้นจริง**: ตอบราคาทองคำเสร็จ (00:30:25) แล้ว **พูดต่อเรื่องปฏิทินเศรษฐกิจเองที่ 00:30:32 โดยผู้ใช้ไม่ต้องถามซ้ำ** — ผลปฏิทินกลับมาตอนที่คำถามล่าสุดเป็นเรื่องราคาทอง จึงติดป้าย `[PENDING QUESTION]` และโมเดลตอบให้ครบ
- **ยังตกหนึ่งข้อ**: ผล `trading_deep_analysis_suite` กลับมาที่ 00:30:14.945 ซึ่งคำถามล่าสุดยังเป็น "วิเคราะห์ BTC 5 มิติ" อยู่ จึง**ไม่ติดป้าย** — แต่โมเดลปิดเทิร์นไปแล้วตั้งแต่ 00:30:14.462 (`Turn Complete audio: 0 bytes`) ก่อนผลจะมาถึง เลยเงียบจนผู้ใช้ถามซ้ำที่ 00:31:25
- **แก้**: ติดป้ายทุกกรณี — คำถามเดิมใช้ `[ANSWER FOR] … ต้องพูดตอบคำถามนี้ให้ครบทันที ห้ามเงียบหรือรอให้ผู้ใช้ถามซ้ำ` ส่วนคำถามที่ค้างจากก่อนหน้ายังใช้ `[PENDING QUESTION]` เหมือนเดิม

### 15. เพิ่ม log ให้โหมดประชุม/แปลภาษา (2026-09-17)

เดิมสองโหมดนี้ log น้อยมาก (มีแค่ "Started …", "Paused", error) ตรวจการทำงานจริงจาก logcat ไม่ได้ — เพิ่มชุด log ที่ใช้รูปแบบเดียวกับ `LiveGemini`:

- `LiveSpecialistService`: `▶ Start <MODE> model=… (target/languages)`, `✅ READY <MODE> session #N (…ms)`, `🎤 heard +"…"` / `🌐 translated +"…"` (log เฉพาะชิ้นที่เพิ่ม), `🏁 Segment closed — source=… chars, translated=… chars`, `🎙 Audio streaming to specialist (#N)` ทุก 250 เฟรม
- `SpecialistSessionController`: `▶ Started <MODE>` (+ ภาษาปลายทางของโหมดแปล), `■ Stopped <MODE> — Ns active, N segment(s), N chars, summarize=…`, `❓ Ask (typed)` / `💬 Answer (typed)` พร้อมขนาดคำถาม/บริบท/คำตอบ, `📝 Summary ready (…)`
- ตารางอธิบาย log ทั้งหมดอยู่ใน `02_Components/Meeting_And_Translate_Modes.md` หัวข้อ "Log สำหรับตรวจการทำงาน"

### 16. ทดสอบประชุมยาว 10 นาที + แท็บสรุปประชุม (2026-09-17 01:07–01:19)

- **ข้อความไม่ได้หาย**: log แสดง `heard` แค่ 2 ครั้ง (01:11 และ 01:18) เพราะ `gemini-3.5-transcribe-live` ส่ง transcript เป็นก้อนใหญ่ตอนปิด turn ไม่ใช่ทีละคำ และก้อนละ 3-4 พันตัวอักษรเกินเพดาน logcat (~4000 ไบต์) จึงถูกหั่นเป็น `[part 1/2]` และบางท่อนหายจาก log — บทประชุมจริงครบ (`2 segment(s), 7394 chars` และสรุปออกมาครบ) → เปลี่ยน log เป็น preview 100 ตัวอักษร + ความยาว
- **แก้บั๊ก UI**: กล่องสรุปเดิมไม่จำกัดความสูงและไม่เลื่อน ดันปุ่มควบคุมตกจอ และค้างข้ามการเปิดหน้าใหม่จนต้องปิดแอป
- **ย้ายสรุปออกจากแชทหลัก**: เพิ่มแท็บ "สรุปประชุม" ในหน้าประชุม เก็บรายการชื่อ `วัน/เดือน/ปี-เวลา` พร้อมปุ่ม เล่น (ผู้ช่วยอ่านออกเสียง) / อ่าน / ลบ — เก็บใน settings key `meeting_records_v1` สูงสุด 50 รายการ
- ไฟล์ใหม่ `data/MeetingArchive.kt` (`MeetingRecord`, encode/decode, `buildReadAloudPrompt`) + tests 3 ข้อใน `LiveSessionReviewFixesTest`

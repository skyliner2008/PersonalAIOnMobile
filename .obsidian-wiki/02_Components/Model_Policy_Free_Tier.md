# 🎛️ นโยบายเลือกโมเดล (Gemini API — Free Tier)

อ้างอิงจาก AI Studio console ของ project จริง (2026-09-16) และเอกสาร rate limits ทางการ

## ข้อเท็จจริงที่นโยบายนี้ตั้งอยู่
- โควตาเป็นของ **project ไม่ใช่ของ API key** — key หลายใบใน project เดียวกันไม่ได้เพิ่ม RPD
- **RPD รีเซ็ตเที่ยงคืน Pacific** (ประมาณ 14:00–15:00 ตามเวลาไทย)
- text-out: `flash` = 5 RPM / 250K TPM / **20 RPD**, `flash-lite` = 15 RPM / 250K TPM / **500 RPD**
- Live API: RPM/RPD **ไม่จำกัด**, TPM 65K (ตระกูล Live รุ่นใหม่), native audio dialog 1M TPM

## แชท (`ModelConfig.CHAT_MODELS`)
| ลำดับ | โมเดล | โควตา/วัน |
|---|---|---|
| 1 | `gemini-3.5-flash-lite` (ค่าเริ่มต้น) | 500 |
| 2 | `gemini-3.1-flash-lite` | 500 |

- ตระกูล `flash` (20/วัน) **ไม่อยู่ในสายแชท** — ใช้ได้เฉพาะเมื่อผู้ใช้เลือกเองใน Settings
- ค่าที่เคยบันทึกไว้ (เช่น `gemini-3.6-flash`) ถูก migrate เป็น flash-lite อัตโนมัติตอนเปิดแอป
- 429 แบบ PerDay → `markModelQuotaExhausted` ดันโมเดลนั้นไปท้าย chain จนถึงเวลารีเซ็ต
- โควตาหมดทั้งคู่ → `chatQuotaExhaustedMessage()` บอกผู้ใช้ตรงๆ พร้อมเวลารีเซ็ต (โหมดเสียงยังใช้ได้)
- งานวนลูป (เฝ้าราคา/เงื่อนไขตลาด) ใช้ automation engine ของแอปเอง **ไม่เรียกโมเดล** — โมเดลถูกเรียกเมื่อเข้าเงื่อนไขจริงเท่านั้น

## Live (`ModelConfig.SEED_LIVE_MODELS`)
| ลำดับ | โมเดล | บทบาท |
|---|---|---|
| 1 | `gemini-3.8-live` | ค่าเริ่มต้น — ใหม่สุด 65K TPM |
| 2 | `gemini-3.1-flash-live-preview` | ผ่านการใช้งานจริงมาแล้ว |
| 3 | `gemini-3.8-live-extended-thinking` | เหตุผลแน่นกว่า latency สูงกว่า (`DEEP_THINKING_LIVE_MODEL`) |
| 4 | `gemini-2.5-flash-native-audio-preview-12-2025` | native audio dialog — สำรองสุดท้าย |

- หลัง sync `ListModels` ระบบจัดอันดับจากโมเดล `bidiGenerateContent` จริงที่ project มองเห็น เรียงเวอร์ชันใหม่ก่อน → **โมเดล Live ที่ Google เพิ่มใหม่ถูกใช้เองโดยไม่ต้องแก้โค้ด**
- `isConversationalLiveModel()` ตัดรุ่นเฉพาะทางออกจากสายผู้ช่วย: `gemini-3.5-transcribe-live` (ถอดเสียง) และ `gemini-3.5-live-translate-preview` (แปลภาษา) เก็บไว้ใช้เฉพาะโหมดของมัน

## โหมด (`LiveModeState` + `AlwaysLiveProfile`)
ทุกโหมดคือผู้ช่วยตัวเดียวกัน ใช้ tool ได้เหมือนกันหมด ต่างกันที่บทบาท/น้ำเสียง **ยกเว้น**:

| กลุ่ม | โหมดที่ใช้ได้ |
|---|---|
| `ToolRegistry.DEVICE_CONTROL_TOOLS` (อ่านจอ/แตะ/พิมพ์/เลื่อน/ปุ่ม/เปิดแอป/ปลุก-พักจอ) | **DRIVE เท่านั้น** |
| tool อื่นทั้งหมด (เทรด, MT5, กล้อง, แจ้งเตือน, อวาตาร์, งานเบื้องหลัง …) | ทุกโหมด |

กันสองชั้น: กรองออกจาก setup ของ session (`getLiveGeminiTool`) และบล็อกซ้ำตอน execute (`ToolExecutor`)

## เส้นทางการใช้ AI ทั้งแอป (ตรวจ 2026-10-03)

ผู้ใช้: "ตรวจสอบเส้นทางการใช้ ai … จะใช้โมเดล live เป็นหลัก ที่ใช้ได้ไม่จำกัด และจะใช้โมเดล chat Gemini 3.1 Flash Lite /500RPD,
Gemini 3.5 Flash Lite /500RPD และ Gemini อื่นๆ 20RPD ซึ่งฉันมีระบบ multi api key เผื่อไว้แล้ว"

**Live (ไม่จำกัดจำนวนครั้ง · จำกัด TPM)**
| งาน | ที่ | หมายเหตุ |
|---|---|---|
| ผู้ช่วยเสียงทุกโหมด (ควบคุม/อวตาร/ขับรถ) | `LiveGeminiService` | หมุน key + โมเดลเมื่อติดโควตา (`rotateLiveCredentialOnQuota`, ใช้ `gemini_api_keys`) |
| tool ที่ Live เรียก (รวมงานลูกน้อง) | `LiveToolBridge` → `ToolExecutor` | ทุก call อยู่ใน `LiveToolCallContext` → ข้ามบทวิเคราะห์เสริมของ tool (ไม่เรียกโมเดลแชท) |
| พูดแจ้งเตือนด้วยเสียง Live | `LiveVoiceAlertEngine` | อ่าน `gemini_api_keys` |
| ประชุม/แปลภาษา | `LiveSpecialistService` | โมเดล Live เฉพาะทาง |
| กล้องแบบสด | เฟรมเข้า session Live | |

**แชท generateContent (นับ RPD — 3.5/3.1 flash-lite 500/วัน · ตัวอื่น 20/วัน · โควตาเป็นของ project)**
| งาน | ความถี่ | เส้นทาง |
|---|---|---|
| พิมพ์แชท (วน tool หลายรอบ) | ต่อข้อความ หลาย request | `generateResponseWithTools` — โมเดลผู้ใช้ → `getFallbackChain` (2 ตัว 500 แล้วตามด้วย lite อื่น) · หมุน key ได้ |
| บทวิเคราะห์เสริมของ tool ตอนพิมพ์แชท (ปฏิทิน/ข่าว/sentiment/งบการเงิน) | +1 ต่อ tool | `generateToolEnrichment` (ข้ามใน Live) |
| จับคู่ sector ของ market snapshot | ต่อครั้งที่ระบุ sector | `skipInLive = false` (ใช้ทั้งใน Live) |
| Live เรียก search_web / ค้นหาใกล้ฉัน / translate_text / summarize_text / วิเคราะห์ไฟล์ | ต่อ call | `LiveToolBridge.processInterceptedRequest` → โมเดลแชท (+ Google Search grounding) |
| Live ส่งคำขอ tool เป็นข้อความ (Path B เก่า) | น้อยมาก | router ด้วยโมเดลแชท |
| แจ้งเตือน: สรุป AI, ระบบปลุก AI (วิเคราะห์ NOTIFY/SKIP), supervisor ของ Unified SMC, งานตั้งเวลา | ตามเหตุการณ์ — ปลุก AI ได้ถึง 40/ชม./สินทรัพย์, 3,000/วัน | `TradingAlertEvaluator.generateAiText` |
| ภาพนิ่งจากกล้อง | ต่อภาพ | `CameraAnalysisService` |
| รวบรวมความจำ (sleep cycle) | ครบ 200 ข้อความ | `JarvisMemoryManager.performSleepCycle` |

**ที่ไม่ตรงแผนและแก้แล้ว**
- **งานแจ้งเตือนเบื้องหลังใช้ key เดียว** (`api_key`) ไม่ใช้ `gemini_api_keys` → ตอนนี้หมุนทุก key
- **งานแจ้งเตือนไหลไปโมเดล 20/วันได้**: `fallbackModelsOverride = emptyList()` ไม่ได้ปิด fallback — `generateResponse` กลับไปใช้
  chain เต็มในแต่ละ attempt (`takeIf { isNotEmpty() } ?: getFallbackChain`) และรอ 429 รายนาทีได้ถึง 3 × 60 วิ → ตอนนี้ใช้เฉพาะ
  2 โมเดล 500/วัน (`CHAT_MODELS`) ทีละโมเดล (`fallbackModelsOverride = listOf(m)`) และไม่รอ 429 (`waitOn429 = false`)
- **แชทหลักเปลี่ยนโมเดลของผู้ใช้เป็นตัวสำรองถาวร** (`onWorkingConfigChanged` บันทึก `model_name` ทุกครั้งที่ fallback ใช้ได้ —
  รวมโมเดล 20/วัน) → บันทึกโมเดลเฉพาะ 2 ตัว 500/วัน (key ที่ใช้ได้ยังบันทึกเหมือนเดิม) ไม่งั้นกลับไปเริ่มที่โมเดลของผู้ใช้
- **ภาพนิ่งจากกล้องใช้ `gemini-2.5-flash` (20/วัน)** เป็นตัวแรก → `DEFAULT_MAIN_MODEL` (flash-lite)
- (รอบก่อน) แจ้งเตือนเคยเขียนทับ `model_name` ของทั้งแอป → จำโมเดลสำรองไว้ใช้เฉพาะแจ้งเตือน 1 ชม.

**ข้อสังเกต**
- log 14:39 ของวันนี้: `Gemini API keys for rotation: 1 key(s)` = ในเครื่องมี key เดียว (`gemini_api_keys` ว่าง)
- key หลายใบใน **project เดียวกัน** ใช้โควตาก้อนเดียวกัน — multi-key เพิ่ม RPD ได้เฉพาะ key จากคนละ project/บัญชี
  (`markKeyDead` ยังลดลำดับโมเดลทั้ง chain เมื่อ key ใดติดรายวัน แม้ key อื่นจะอยู่คนละ project — แต่สลับ key ก่อนสลับโมเดลเสมอ)
- chain แชทอัตโนมัติ = 2 ตัว 500/วัน แล้วต่อด้วย lite อื่นที่ project มองเห็น (เช่น 3.1-flash-lite-preview, 2.5-flash-lite,
  flash-lite-latest — ผู้ใช้ระบุว่าเป็น 20/วัน) เป็นตัวสำรองสุดท้ายของงานที่ผู้ใช้รออยู่เท่านั้น

**ข้อเสนอที่รอผู้ใช้ตัดสิน**
1. เปิด Google Search ในตัวของ Live (`tools: [{googleSearch:{}}, {functionDeclarations}]` — เอกสาร Live API "Combining multiple tools",
   `gemini-3.1-flash-live-preview` รองรับ search grounding) → คำถามค้นเว็บใน Live ไม่ต้องเรียกโมเดลแชท (ต้องมีตัวปิดเองถ้า setup ถูกปฏิเสธ +
   ทดสอบด้วยเสียง)
2. translate_text / summarize_text ข้อความสั้นที่ Live เรียก → ให้ Live แปล/สรุปเอง (ข้อความยาวใช้โมเดลแชทต่อ เพราะ TPM ของ Live 65K)
3. งบระบบปลุก AI ตามจำนวน key จริง (ตอนนี้ 40/ชม., 3,000/วัน ตั้งไว้ตอนมี 7 key) และนโยบายตอน AI วิเคราะห์ไม่ได้ (ดู
   [[../07_Trading_Intelligence/66_WakeEngine_Live_Backup_V29]] ข้อ 9)
4. ไม่แนะนำย้ายการวิเคราะห์ของระบบปลุกไป Live ตอนนี้: ต้องเปิด session ใหม่ทุกครั้ง, ยังไม่ได้ทดสอบโหมดตอบเป็นข้อความ, และอาจชนลิมิต
   session พร้อมกันขณะผู้ใช้คุย Live อยู่

## Live workers — แบ่งงานให้โมเดล Live ตัวอื่น (2026-10-03)

ผู้ใช้: "โมเดล live ทั้งหมดใช้ได้ไม่จำกัด RPM กับ RPD ฉันเลยอยากใช้ประโยชน์จากตรงนี้ให้ได้มากที่สุด แต่โมเดล 3.1 live โต้ตอบด้วยเสียง
ดีกว่าโมเดลอื่น ฉันเลยเลือกให้เป็นโมเดลหลักในการใช้เสียง แต่เราก็ยังแบ่งหน้าที่ไปให้โมเดล live ตัวอื่นทำงานแทนได้"
(free tier: 7 บัญชีแยก project บัญชีละ 1 key — ช่วงพัฒนาใส่ key เดียว)

**`data/LiveWorker`** — งานละ 1 session บนโมเดล Live ที่เสียงไม่ได้ใช้ (`gemini-3.1-flash-live-preview` เป็นของเสียงเท่านั้น)
- **คำตอบมาทาง function**: โมเดล Live ตอบเป็นเสียงได้อย่างเดียว ("native audio models only support AUDIO response modality" —
  Live API capabilities) → worker ประกาศ tool `submit_answer(text)` และสั่งให้ส่งคำตอบฉบับเต็มทางนั้น → ได้ข้อความตรงตัว ไม่ผ่านการถอดเสียง
  (รูปแบบ 7 บรรทัด DECISION/BIAS/… ของระบบปลุกไม่เพี้ยน) · ถ้าเทิร์นจบโดยไม่เรียก ใช้ transcript ของเสียงแทน
- ทีละงาน (`gate`) — ให้มี session Live ข้างเสียงไม่เกิน 1 · หมุนทุก key (คนละ project) · quota → key ถัดไป, อย่างอื่น → โมเดลถัดไป
  (setup ถูกปฏิเสธ = พักโมเดล 30 นาที · งานที่ไม่ได้คำตอบ = 2 นาที, ครั้งที่ 2 ติดกัน 30 นาที · อย่างอื่น 2 นาที) · ทุกจุดที่เรียก:
  worker ไม่ตอบ → โมเดลแชทเดิม
- **คำตอบต้องตรงรูปแบบของงาน** (`accept` ใน `LiveWorker.ask` และลูปโมเดลแชทของ `TradingAlertEvaluator.generateAiText`): ระบบปลุก =
  `WakePrompt.parse` อ่านได้, Supervisor = มีบรรทัด `DECISION:` · ไม่ตรง = ไม่ใช่คำตอบ → โมเดลถัดไป (worker ถัดไป แล้วโมเดลแชท)
- **โมเดลที่พูดขณะคิด** (`FOLLOW_UP_MS`: 3.8-live-extended-thinking): เสียงพูดของมันไม่นับเป็นคำตอบ นับเฉพาะ `submit_answer` ·
  จบเทิร์นแล้วยังไม่ได้คำตอบ → ฟังต่อจนเงียบครบ 15 วิ รอคำตอบในเทิร์นถัดไป (สูงสุด 3 เทิร์น; รายงานการใช้โทเคนไม่นับว่าโมเดลยังทำงาน)
- โมเดลอื่น: เทิร์นจบเปล่า (ไม่เรียก tool ไม่มีเสียง) → ส่งงานเดิมซ้ำ 1 ครั้งเป็น `realtimeInput.text` (`realtimeTextJson` — เอกสาร Live API:
  บนโมเดล 3.x Live `clientContent` มีไว้ใส่ประวัติตอนเริ่ม ข้อความระหว่าง session ส่งทาง realtimeInput) · เหตุผลที่ล้มเหลวบอกว่า session มีอะไร
  `(audio N, thoughts M, calls …, said "…")`
- โมเดลตามชนิดงาน (`Kind`):

| ชนิด | ลำดับโมเดล | ใช้กับ |
|---|---|---|
| `THINKING` | 3.8-live → 2.5-native-audio → 3.8-extended-thinking (เดิมตัวนี้นำ — ดูรอบสี่) | วิเคราะห์ของระบบปลุก AI, Strategy Supervisor |
| `GENERAL` | 3.8-live → 2.5-native-audio → 3.8-extended-thinking | สรุปแจ้งเตือน, แปล/สรุปข้อความสั้น, บทวิเคราะห์เสริมของ tool ตอนพิมพ์แชท |
| `HEAVY` | 2.5-native-audio (1M TPM, context 128K) → 3.8-live | แปล/สรุปข้อความยาว > 12,000 ตัวอักษร |
| `SEARCH` | เหมือน GENERAL + `googleSearch` ใน session | search_web / ค้นหาใกล้ฉันที่ Live เรียก, search ตอนพิมพ์แชท, งานตั้งเวลา |

- เวลารอ: งานที่ผู้ใช้รอ 15 วิ (บทวิเคราะห์เสริม 60% ของงบ 10 วิ) แล้วถอยไปแชท · แจ้งเตือน 15 วิ · THINKING 30 วิ
- ยังใช้โมเดลแชท: พิมพ์แชท (วน tool หลายรอบ), ภาพนิ่งจากกล้อง, วิเคราะห์ไฟล์, รวบรวมความจำ
- log: `LiveWorker: ✅ THINKING model=… key=1/7 …ms … via=tool` (ต่อท้าย `(in turn 2, 8123ms after turn 1 ended saying "…")` หรือ
  `(after resending as realtime text)`) · `⚠️ … SETUP: … → MODEL` · `⚠️ … ANSWER: no answer in the 15s after turn 1 ended (…, said "…") → MODEL` ·
  `⌛ … chat models take it` · แจ้งเตือน `🧠 AI summary OK via Live worker model=…` · คำตอบผิดรูปแบบจากโมเดลแชท
  `🧠 AI summary model … answered outside the asked format`
- เทสต์ `LiveWorkerTest` (11) กับ server จำลอง
- **รอบแรกกับ API จริง** (มือถือ 2026-10-04 00:15, ระบบปลุก BTCUSDT@15m): วิเคราะห์ได้ 2 ครั้ง (SKIP แล้ว NOTIFY BUY) ผ่าน
  `gemini-3.8-live` แต่ (1) `gemini-3.8-live-extended-thinking` ถูกปฏิเสธ "1007 Thinking level must be specified for this model"
  → ใส่ `thinkingConfig.thinkingLevel = medium` เฉพาะตัวนี้ (2) 3.8-live **พูด**คำตอบแทนการเรียก `submit_answer` (via=speech: 3.5 วิ
  และ 19.9 วิสำหรับ 375 ตัวอักษร) — Live ไม่มี `toolConfig` ใน setup จึงบังคับโหมด ANY ไม่ได้ → คำอธิบาย tool หนักขึ้น + ต่อท้ายทุกงานด้วย
  `SUBMIT_REMINDER` (3) ข้อความแจ้งเตือนที่ส่งเข้า session เสียงไม่มีชื่อสินทรัพย์ → session เสียงเรียก BTC ว่า "ทองคำ" (ผู้ใช้เพิ่งถามเรื่องทอง)
  → `fullSpeech` ขึ้นต้นด้วย `AlertPresentationFormatter.assetNameTh` + prompt ห้ามเอาสินทรัพย์จากบทสนทนาก่อนหน้า
- **รอบสอง** (00:26–00:46 หลังแก้รอบแรก): `gemini-3.8-live` ตอบผ่าน `submit_answer` ทุกครั้ง (via=tool) — 7 การปลุก 2.9–5.9 วิ,
  SKIP พร้อมเหตุผล + NOTIFY BUY 00:45:45 ที่ session เสียง 3.1 พูดออกไป · แต่ `gemini-3.8-live-extended-thinking` รับ setup แล้ว **จบเทิร์น
  เปล่าทุกครั้ง** (ไม่มีเสียง ไม่มี thought ไม่เรียก tool, ~3.5 วิ) และถูกลองใหม่ทุกการปลุก → พักนานขึ้นเมื่อเปล่าติดกัน, บอกสิ่งที่อยู่ในเทิร์น,
  ส่งงานซ้ำเป็น realtime text 1 ครั้ง
- **รอบสาม** (02:10, 02:20 — ผู้ใช้: "มีการปลุก แต่ไม่มีเสียง"): 3.8-live-extended-thinking พูด "กำลังวิเคราะห์ข้อมูลกราฟครับ" /
  "วิเคราะห์ข้อมูลกราฟทั้งหมดก่อนนะคะ" แล้วจบเทิร์นใน ~4.9 วิ — worker เอาประโยคนั้นเป็นคำตอบ (via=speech), ระบบปลุกอ่านรูปแบบไม่ได้ →
  ทางแจ้งเงียบ "AI ใช้ไม่ได้" ทั้งที่ 3.8-live ยังตอบได้ (รอบ 02:20 ไม่แจ้งเลยเพราะเพิ่งแจ้งเงียบไป) · เทิร์นเปล่าของรอบสองน่าจะเป็นแบบเดียวกัน
  (จบเทิร์นก่อนแล้วคิดต่อ แต่ worker ปิด session ไปก่อน) → การตรวจรูปแบบ + ฟังต่อ 15 วิ (ด้านบน)
- **รอบสี่** (02:21 หลังติดตั้ง): ฟังต่อแล้ว — เทิร์น 1 เงียบ, เทิร์น 2 พูด "ขออภัย เกิดข้อผิดพลาดขึ้นในระบบขณะประมวลผลข้อมูลครับ" แล้วเงียบ 15 วิ
  → ล้มเหลวที่ 25 วิ; การตรวจรูปแบบส่งงานต่อ 3.8-live ตอบผ่าน tool ใน 3.2 วิ (SKIP พร้อมเหตุผล) · สรุป: ตัว extended-thinking ไม่ตอบงานไหน
  เลยใน 4 รอบ (เปล่า / คำพูดถ่วงเวลา / แจ้งข้อผิดพลาดของมันเอง) และแต่ละครั้งถ่วงการปลุก ~25 วิ → `THINKING` เริ่มที่ 3.8-live, ตัวนี้เป็นทาง
  สุดท้าย (ยังมีใน `candidates` — ถ้าวันหนึ่งใช้ได้ log จะเห็น `✅ … model=gemini-3.8-live-extended-thinking … via=tool`) ·
  ยืนยัน 02:30: การปลุกได้คำตอบจาก 3.8-live ผ่าน tool ใน 2.9 วิ (เทียบ 28 วิ ตอนตัว extended-thinking นำ)

---
**Links**: [[LiveGeminiService]] | [[Always_AI_Live_Mode]] | [[Changelog_2026-09-16_Free_Tier_Model_Policy_And_Agent_Tasks]] | [[index]]

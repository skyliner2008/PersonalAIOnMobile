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

---
**Links**: [[LiveGeminiService]] | [[Always_AI_Live_Mode]] | [[Changelog_2026-09-16_Free_Tier_Model_Policy_And_Agent_Tasks]] | [[index]]

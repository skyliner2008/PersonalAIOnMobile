# 📜 สรุปภาพรวมการพัฒนาโปรเจค PersonalAIBot

> เรียบเรียงจาก `log.md` ทั้งหมด (เม.ย. → ส.ค. 2026) — อัปเดตล่าสุด: 2026-08-10
> จัดกลุ่มตามระบบ ไม่ไล่ตามวันที่ — รายละเอียดรายวันดูที่ `00_System/log.md`

---

## 🏗️ ช่วงวางรากฐาน (เม.ย. 2026)

- เริ่มระบบ "สมองส่วนนอก" (.obsidian-wiki) เป็นสารบัญอ้างอิงกลางของโปรเจค
- **mt5-core-server** V20–V26: EA Manager, Unified Gates, Decoupling + scripts วิเคราะห์ (`npm run analyze`, advanced_analytics)
- **Strategy Library** 60 ไฟล์กลยุทธ์ Quantpedia (offline, 3 tools)
- **Memory System Fix Pack** (B1–B4 / E1–E3): Core Memory, Archival recall
- **Identity System + Provider/Persona Unification** — JarvisPersona ก้อนเดียวทุก provider path
- **AI Hardening** F1–F10 (tool loop, injection guard)
- แหล่งข่าว 6 แหล่ง + FRED (ไม่ต้อง API key) / ForexFactory macro calendar
- Lightweight Charts knowledge hub

## 🎙️ Live Voice & Vision (แก้หลายรอบจนลงตัว)

- แก้ AI เงียบหลังเปลี่ยนเสียง — **บทเรียนสำคัญ: `clientContent` ไม่ trigger generation ขณะ audio streaming ต้องใช้ `realtimeInput.text`** (`sendRealtimeText()`)
- **Voice Profile ↔ Identity**: 30 เสียง (♀/♂ + tone) ผูกเพศ/อารมณ์/คำลงท้าย (ค่ะ/ครับ) persist ข้าม session เลือกจาก Settings ได้
- ลบ resume-task หลังเปลี่ยนเสียง (เดิม AI วิ่งทำงานเก่าซ้ำ เช่น วิเคราะห์ SMC อีกรอบ)
- กัน AI bluff เปลี่ยนเสียงโดยไม่เรียก `voice_set_profile` (LIVE_RULES ข้อ 7: โจทย์กว้างต้องเสนอเสียง+รอยืนยัน / ระบุชื่อชัด=เปลี่ยนทันที)
- กัน user identity ถูกทับ (applyVoiceIdentity persist เฉพาะ agent_gender/agent_vibe)
- ซ่อน transcription ฝั่ง user ของ live ตอนโหลดประวัติแชท
- Live Voice รอบก่อนหน้า: transcription config, interruption handling, audio buffer, TTS fallback, สมดุลความยาวคำตอบ 5-8 ประโยค
- **Camera/Vision**: auto-prompt หลังเปิดกล้อง (ไม่ต้องถามซ้ำ) + vision provider sync ตามโมเดลหลัก
- **สถานะ: user ยืนยัน "ทำงานได้อย่างลงตัว" ✅ (หมวด 7 + 11)**

## 🔔 Alert / Automation V2 (งานใหญ่ต้น ส.ค.)

- Notification มีปุ่ม **หยุดแจ้งเตือน / แจ้งเตือนซ้ำ** — manifest-declared receiver ทำงานแม้แอปถูกฆ่า (เดิม dynamic receiver ตายตาม service)
- **Adaptive Interval**: tick หลัก 30 วิ + เร่งเช็คอัตโนมัติเมื่อราคาใกล้เป้า (<0.1% → 30 วิ) รองรับทองวิ่งแรง + แยก AI summary ออกจาก loop เป็น async
- **AlertFieldCatalog** — dropdown เลือกได้เฉพาะ field ที่ดึงค่าได้จริง + validate operator (field ข้อความบังคับ ==/contains) ทั้ง UI และ AI
- **IndicatorAlertProvider** — คำนวณ EMA/RSI/MACD/Stoch/CCI/BB/ATR/EMA Cross เองจากแท่งเทียน cache (incremental fetch ยืนยันแล้ว ดึงเฉพาะ delta) เพราะ TV scanner คืน null หลายตัว
- **SmcAlertProvider** — แปลง SMC analysis เป็น 18 alert fields (zone, BOS/CHOCH, OB, FVG, liquidity + ดาว)
- รองรับ `symbol@TF` (1m–1D) ทุก tool วิเคราะห์; deep suite ครบ 9 ฟิลด์ 5 มิติ + TA fallback OANDA→FX_IDC→TVC + circuit breaker กัน ngrok bridge ล่ม
- UI หน้าสร้าง Alert ออกแบบใหม่ (แก้ dropdown crash, ช่องเวลาถูกตัด) + 14 preset ลัด + ปุ่ม 🧪 auto-test ทุก tool (ผ่าน 8/8)
- rename/edit ค่า/เวลา ได้ทั้ง UI และผ่าน AI; wakeup service เมื่อสร้าง alert ขณะ service ดับ
- แก้ MT5-only mode ติดเองตอนคุย SMC (keyword กว้างเกิน) + toggle ตั้งค่าไม่จำค่า

## 🤖 Providers & โมเดล (revamp ใหญ่ 2026-08-08)

- ตัด OpenAI/Claude/Vertex/LiteLLM/ADK/NIM ออก — เหลือ **Gemini / OpenRouter / Groq / MiniMax**
- **Gemini Multi API Key** (free tier หลายเมล์) + key rotation ก่อน model fallback
- **Model fallback chain ตามโควต้าจริง** + หน้า Settings จัดลำดับเองได้
- **Cross-provider fallback**: Gemini ตายทั้ง chain → Groq → OpenRouter (Live mode ไม่กระทบ — LiveToolBridge execute เอง)
- **ModelAutoTester** — เทส chat+tools ทุกโมเดล persist `model_caps_v1` ซ่อนโมเดลใช้ไม่ได้; ผลจริง: Groq ผ่าน 3/8 (llama-3.1-8b, llama-3.3-70b, qwen3.6-27b), OpenRouter free ผ่าน 6/17
- **Root cause ใหญ่**: `take(12)` ตัด trading tools ทิ้ง → sort trading ขึ้นก่อน + ตัด MT5 tools 21 ตัวออกจาก spec เมื่อไม่ใช่ strict MT5 + ซ่อน search_web ใน trading context
- Loop detection (tool ซ้ำ args), 429 backoff ตามเวลา API บอก, empty round retry, timeout 120s, ตัด SSE log ยาว, mask key หัว-ท้าย (logcat เท่านั้น โดย user ตั้งใจ), sanitize key ออกจากข้อความ error
- บทเรียน: Round 1 = model ขอ tool, Round 2 = สรุปจากผล tool — ปกติของ tool calling ไม่ใช่ทำงานซ้ำ; โมเดลหลอนราคาได้แม้ tool คืนถูก (llama-3.3-70b)

## 🛠️ Custom Tools (หมวด 5 ✅)

- แก้ tool ที่สร้างไม่โหลดกลับ (ไม่มี caller) + duplicate declaration 400
- **CRUD ครบวงจรผ่าน AI**: `system_create_agent_tool` / `system_list_agent_tools` / `system_delete_agent_tool` ทั้งโหมด chat และ live
- persist ลง `custom_agent_tools/*.json` โหลดกลับอัตโนมัติ
- เทสจริงผ่าน: custom_gold_check สร้าง+แก้เพิ่ม RSI สำเร็จ

## 📎 File Tools (หมวด 9)

- แนบไฟล์/รูป/PDF ในแชท (สูงสุด 5 ไฟล์, 15MB) — Gemini วิเคราะห์ native + กันเรียก camera tool ผิดตอนมีไฟล์แนบ
- file_write: **verify หลังเขียน + MediaScanner** (เห็นไฟล์ใน Download ทันที — user ยืนยันแล้ว)
- **รองรับ .xlsx จริง** (XlsxWriter zip+XML ไม่พึ่ง POI) — อ่านสลิปหลายใบ → ตารางรายรับ-รายจ่าย Excel
- Security: path guard ครบ, extension allowlist, read จำกัด 200K ตัวอักษร, ห้ามลบโฟลเดอร์สาธารณะทั้งก้อน

## 🔧 ระบบอื่นที่ผ่านการทดสอบ

- **หมวด 6 Orchestrator**: ลบ JarvisPlanner (dead code), IntentClassifier single-source trading keywords, ANALYSIS addon เปลี่ยนเป็น TV-first protocol
- **หมวด 10 System Tools**: diagnostics ตรวจจริง (เดิม placeholder PASS เสมอ)
- **หมวด 13 Database**: wire dead queries (updateArchivalAccess, getArchivalRecent) — user เทสผ่าน
- **หมวด 14 mt5-core-server scripts**: `npm run analyze` ผ่านจริง (15,796 cycles)
- **หมวด 15 Logging**: API key ไม่หลุด log, preview คำตอบ AI 800 chars + tool result 300 chars
- **README.md** อัปเดตทั้งฉบับให้เป็นปัจจุบัน (2026-08-10)

## 📊 สถานะ App Review Checklist (2026-08-10)

**ผ่าน 13/15 หมวด** — เหลือ:
- หมวด 9 File Tools — เสร็จโค้ดแล้ว รอ user เทส .xlsx บนเครื่องจริง
- หมวด 10 System Tools — review แล้ว รอเทสรวม
- หมวด 12 UI/UX — review แล้ว รอเทสรวม + roadmap ใหม่ **Rich Chat Rendering** (ตาราง/รูป/กราฟ/infographic ในแชท)

## 🧭 Roadmap ถัดไป

1. Rich Chat Rendering (อยู่ในหมวด 12)
2. Multi-Agent Orchestration (Swarm)
3. Portfolio Hub
4. Strategy Backtester
5. Hardware Extension

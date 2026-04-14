# 🤖 PersonalAIBot — JARVIS for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-orange.svg?style=flat)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Gemini](https://img.shields.io/badge/AI-Gemini%203.1%20Flash-green.svg?style=flat&logo=google-gemini)](https://ai.google.dev/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**JARVIS** (PersonalAIBot) คือระบบผู้ช่วย AI ส่วนบุคคลระดับสูง (Personal AI Assistant) ที่ออกแบบมาเพื่อเป็นทั้งเพื่อนคู่คิดและนักวิเคราะห์ข้อมูลอัจฉริยะ ขับเคลื่อนด้วยพลังของ **Google Gemini 3.1 Flash Live** และระบบความจำแบบ 4 ชั้น (GraphRAG)

---

## 🚀 Verfied Features (ใช้งานได้จริง 100%)

### 🎙️ 1. Pro-Analyst Live Voice & UI
ยกระดับการโต้ตอบด้วยเสียงแบบ Real-time ที่ฉลาดกว่าเดิม:
- **Pro-Analyst Summarization** — AI ไม่เพียงแค่สรุป แต่จะวิเคราะห์แนวโน้มและจุดสำคัญ (Insights) ให้ฟังทันที
- **Dynamic UI Separation** — ระบบแยกกล่องข้อความอัจฉริยะ: **Static Boxes** สำหรับข้อมูลเทคนิค/รายงานถาวร และ **Progress Boxes** สำหรับคำพูด AI ที่อัปเดตแบบ Real-time
- **Low Latency Interaction** — ตอบโต้รวดเร็วด้วย WebSocket 60fps พร้อมระบบ AEC + Noise Suppression

### 📸 2. Real-time Camera Vision (Adaptive System)
- **AI Adaptive Vision** — ระบบปรับความเร็วภาพอัตโนมัติ 0–3 FPS (0 FPS เมื่อนิ่ง, 3 FPS เมื่อ AI ร้องขอ) เพื่อประหยัด Token และแบตเตอรี่
- **Vision Activate/Deactivate** — AI สามารถ "เปิด/ปิดตา" เองได้ตามบริบทความจำเป็น
- **Multi-Provider Support** — สลับการใช้งานระหว่าง Gemini Live, Gemini Flash และ OpenAI GPT-4o เพื่อการวิเคราะห์ที่แม่นยำที่สุด
- **AR Overlay Engine** — แสดง Bounding Box และคำอธิบายวัตถุบนภาพจริงแบบ Real-time

### 🧠 3. Advanced 4-Layer Memory Engine
- **Layer 1: Core Memory** — จำข้อมูลส่วนตัวผู้ใช้และสกัดความสนใจอัตโนมัติ
- **Layer 2: Working Memory** — บันทึกประวัติการคุยปัจจุบันลง SQLite ทันที
- **Layer 3: Archival Memory** — ระบบค้นหาความความจำด้วยความหมาย (Semantic Search / Vector Embeddings)
- **Layer 4: GraphRAG Knowledge Graph** — เชื่อมโยงความสัมพันธ์ของแนวคิดต่างๆ เป็นโครงข่ายสมอง

### 📊 4. Trading & SMC Intelligence (V8.3)
- **SMC Master** — วิเคราะห์ Market Structure (BOS/CHoCH), Order Blocks with FVG, Liquidity Zones
- **Pro Scanners** — ค้นหาโอกาสจาก Bollinger Squeeze, RSI Oversold/Overbought และ Volume Breakout
- **MTF Sweeps** — ตรวจจับการกวาดสภาพคล่องข้ามหลาย Timeframe (M1 → H4)

---

## 🛠️ Tech Stack (v2026)

| Layer | Technology | Status |
|-------|-----------|--------|
| Language | Kotlin 2.0 (KMP) | Stable |
| UI Framework | Jetpack Compose Multiplatform | Stable |
| Primary Brain | Gemini 3.1 Flash Live Preview | Active |
| Multimodal | Live Stream (PCM 16kHz + JPEG) | Active |
| Database | SQLDelight + SQLite Persistence | Active |
| Logic Controller | JarvisOrchestrator (Single-Agent) | Active |

---

## 📦 Tool Catalogue (Total: 44 Tools)

### 🧠 BUILT-IN TOOLS (10 tools)
- `calculate`: คำนวณนิพจน์คณิตศาสตร์ซับซ้อน
- `get_current_datetime`: ข้อมูลวันเวลาและปฏิทินปัจจุบัน
- `remember_fact`: บันทึกข้อมูลสำคัญลงความจำระยะยาว
- `recall_memory`: ดึงข้อมูลจากฐานความรู้เดิม
- `convert_units`: แปลงหน่วยสากลทุกประเภท
- `set_reminder`: ตั้งการแจ้งเตือน/TODO
- `translate_text`: แปลภาษาแบบ Multilingual
- `summarize_text`: สรุปข้อความยาวๆ พร้อมกำหนดระดับความละเอียด
- `search_web`: ค้นหาข้อมูลล่าสุดจากโลกออนไลน์
- `analyze_and_display_report`: แสดงรายงาน Markdown ขั้นสูงในแชท

### 📊 TRADING TOOLS (13 tools)
- `trading_price`: ราคา Real-time (Stocks/Crypto/Forex/Gold)
- `trading_market_snapshot`: ภาพรวมตลาดเจาะตามกลุ่มอุตสาหกรรม
- `trading_top_gainers`: หุ้น/Crypto ที่พุ่งแรงที่สุดในตลาด
- `trading_top_losers`: หุ้น/Crypto ที่ดิ่งแรงที่สุดในตลาด
- `trading_technical_analysis`: วิเคราะห์ TA (RSI, MACD, BB, EMA) พร้อม Signal
- `trading_multi_timeframe`: วิเคราะห์ความสอดคล้องทุก TF (W → 15m)
- `trading_bollinger_scan`: หาตัวที่กำลังจะระเบิด (Bollinger Squeeze)
- `trading_oversold_scan`: หาตัวที่ราคาถูกเกินไป (RSI < 30)
- `trading_overbought_scan`: หาตัวที่ราคาร้อนแรงเกินไป (RSI > 70)
- `trading_volume_breakout`: หาตัวที่มีแรงซื้อขายผิดปกติพร้อมราคาพุ่ง
- `trading_sentiment`: วิเคราะห์อารมณ์ตลาดจาก Reddit
- `trading_news`: ข่าวการเงินล่าสุดแยกตาม Symbol
- `trading_combined`: สุดยอดเครื่องมือวิเคราะห์ (TA + News + Sentiment)

### 📈 SMC TOOLS (5 tools)
- `trading_smc_analysis`: Full SMC Dashboard
- `trading_smc_sweeps`: MTF Sweep Detection
- `trading_smc_liquidity`: MTF Liquidity Zones & Stars
- `trading_smc_orderblocks`: Order Blocks + FVG Confirmation
- `trading_smc_structure`: Market Structure (BOS/CHoCH, Premium/Discount)

### 📁 FILE MANAGEMENT (7 tools)
- `file_list`, `file_read`, `file_write`, `file_delete`, `file_analyze` (OCR/PDF Support), `file_move`, `file_search`

### 📷 CAMERA & VISION (9 tools)
- `vision_activate`/`deactivate`: ควบคุมการเปิด/ปิดดวงตา AI
- `camera_analyze_scene`: วิเคราะห์ภาพ Snapshot
- `camera_detect_objects`: ตรวจจับวัตถุ (AR Overlay)
- `camera_read_text`: อ่านข้อความจากกล้อง (OCR)
- `camera_switch_provider`: สลับสมองที่ใช้มอง (Gemini/OpenAI/Claude)
- `camera_switch_mode`: เปลี่ยนโหมดกล้อง
- `voice_get_profiles`: ดูรายการเสียงพูด 30 รูปแบบ
- `voice_set_profile`: เปลี่ยนเสียง JARVIS

---

## 🚀 Future Roadmap (Coming Soon)

1.  **Multi-Agent Orchestration (Swarm Architecture)**: ระบบวิเคราะห์งานและกระจายงานให้ AI Agent เฉพาะทางทำงานพร้อมกัน
2.  **Portfolio Hub**: ระบบติดตามพอร์ตการลงทุนแบบละเอียดและ P&L Analytics
3.  **Strategy Backtester**: ระบบทดสอบกลยุทธ์การเทรดแบบครบวงจร
4.  **Hardware Extension**: การเชื่อมต่อกับอุปกรณ์ Smart Home และ Wearables
5.  **Offline Memory Layer**: ระบบความจำขนาดเล็กที่ทำงานได้โดยไม่ต้องพึ่งอินเทอร์เน็ต

---
© 2026 JARVIS PersonalAIBot Project. All rights reserved.
ng_smc_structure` — Market Structure
วิเคราะห์ Market Structure: BOS, CHoCH, Premium/Discount
- BOS (Break of Structure) — breakout ต่อเนื่อง
- CHoCH (Change of Character) — สัญญาณ trend reversal
- Premium zone (>75%) — แพง → Short
- Discount zone (<25%) — ถูก → Long
- Equilibrium (50%) — จุดสมดุล
- **ตัวอย่าง**: "structure BTC 4h", "BOS หรือ CHoCH ETHUSDT", "ราคาอยู่ใน premium หรือ discount"


---

### 📂 FILE MANAGEMENT TOOLS (7 tools)

#### 21. `file_list` — เลือกดูรายการไฟล์
เรียกดูรายชื่อไฟล์และโฟลเดอร์ในตำแหน่งที่กำหนด
- แยกประเภทโฟลเดอร์ และไฟล์ชัดเจน พร้อมระบุขนาด
- **ตัวอย่าง**: "ดูไฟล์ในโฟลเดอร์ Download", "มีอะไรอยู่ใน Documents"

#### 22. `file_read` — อ่านไฟล์ข้อความ
เปิดอ่านเนื้อหาในไฟล์ (txt, md, csv, json, log)
- **ตัวอย่าง**: "อ่านไฟล์ note.txt", "ขอเนื้อหาในไฟล์ logs.csv"

#### 23. `file_write` — สร้าง/แก้ไขไฟล์
สร้างไฟล์ใหม่ หรือเขียนทับข้อมูลลงในไฟล์เดิม
- **ตัวอย่าง**: "สร้างไฟล์ memo.txt", "เขียนสรุปงานลง summary.md"

#### 24. `file_delete` — ลบไฟล์/โฟลเดอร์
ลบข้อมูลที่ไม่ต้องการออกจากเครื่อง (Recursive delete สำหรับโฟลเดอร์)
- **ตัวอย่าง**: "ลบไฟล์ temp.txt", "ลบโฟลเดอร์ junk_folder"

#### 25. `file_analyze` — วิเคราะห์เอกสารเชิงลึก
ส่งไฟล์ PDF, Word หรือรูปภาพให้ Gemini วิเคราะห์แบบ Native (Multimodal)
- **ตัวอย่าง**: "สรุปไฟล์รายงาน.pdf", "แกะข้อความจากรูปนี้ให้หน่อย"

#### 26. `file_move` — จัดระเบียบไฟล์
ย้านตำแหน่งไฟล์ หรือใช้สำหรับ Rename เปลี่ยนชื่อไฟล์
- **ตัวอย่าง**: "ย้ายไฟล์งานไปที่ Documents", "เปลี่ยนชื่อ old.txt เป็น new.txt"

#### 27. `file_search` — ค้นหาไฟล์อัจฉริยะ
ค้นหาไฟล์ทั่วเครื่องด้วย Keyword หรือนามสกุลไฟล์
- **ตัวอย่าง**: "หาไฟล์ที่มีคำว่า invoice", "หาไฟล์ .pdf ทั้งหมด"

---

### ⚙️ SYSTEM TOOLS (8 tools)

#### 28. `system_status` — System Monitor
ตรวจสอบสถานะระบบและการตั้งค่าทั้งหมด
- Model ที่ใช้งาน, Memory usage
- Tools ที่ active, Session info
- **ตัวอย่าง**: "สถานะระบบตอนนี้", "ใช้โมเดลอะไรอยู่"

#### 29. `skill_manager` — Skill Manager
จัดการ Skills (Custom AI agents) ในระบบ
- ดู Skills ที่มีอยู่, เปิด/ปิด Skill
- ดู trigger keywords, system prompt addon
- **ตัวอย่าง**: "Skills ที่มีอยู่มีอะไรบ้าง", "เปิด skill trading"

#### 30. `image_analyzer` — Image Analyzer
วิเคราะห์รูปภาพด้วย Gemini Vision
- อธิบายภาพ, อ่านข้อความในรูป (OCR)
- วิเคราะห์กราฟ chart จากภาพ
- **ตัวอย่าง**: "อธิบายรูปนี้", "อ่านข้อความในภาพ", "วิเคราะห์ chart นี้"

#### 31. `code_executor` — Code Runner
รันโค้ด Python/JavaScript ได้โดยตรง
- คำนวณ, data processing
- ทดสอบ algorithm, script อัตโนมัติ
- **ตัวอย่าง**: "รันโค้ด Python นี้", "คำนวณ fibonacci 20 ตัว"


#### 32. `app_controller` — App Controller
ควบคุม app และระบบ Android
- เปิด/ปิด app, ปรับ settings
- ส่ง notification, ดู app list
- **ตัวอย่าง**: "เปิด Spotify", "ลด volume ลง 20%"

#### 33. `reminder_scheduler` — Reminder & Scheduler
ตั้งการแจ้งเตือนและกำหนดการ
- Reminder แบบครั้งเดียว/ซ้ำ
- Calendar event, deadline tracker
- **ตัวอย่าง**: "เตือนฉันทุกเช้า 8 โมง", "ตั้ง reminder ประชุม พรุ่งนี้ 14:00"

#### 34. `custom_skill_trigger` — Custom Skill Trigger
เรียกใช้ Custom Skills ที่ผู้ใช้สร้างเอง
- Trigger ด้วย keyword หรือชื่อ skill โดยตรง
- รองรับ skill แบบ chain (multi-step)
- **ตัวอย่าง**: "เรียกใช้ skill ของฉัน", "รัน MyTradingSkill"

---

### 📷 Camera & Vision Tools (5 tools)

#### 35. `camera_analyze_scene` — Scene Analyzer
วิเคราะห์ภาพจากกล้องแบบ Real-time
- อธิบายวัตถุ, บริบท, ข้อความที่เห็นในกล้อง
- รองรับ Gemini Live, OpenAI GPT-4o, Claude Vision
- **ตัวอย่าง**: "ดูกล้องแล้วบอกว่าเห็นอะไร", "อ่านข้อความที่กล้องเห็น"

#### 36. `camera_detect_objects` — Object Detector
ตรวจจับวัตถุพร้อม bounding box และ confidence score
- AR Overlay แสดงกรอบวัตถุบนภาพจริง
- รองรับ multi-object detection
- **ตัวอย่าง**: "หาวัตถุในกล้อง", "หาแมวในภาพ"

#### 37. `camera_read_text` — Camera OCR
อ่านและดึงข้อความ (OCR) จากกล้อง
- อ่านป้าย, เอกสาร, หน้าจอ, ฉลาก
- **ตัวอย่าง**: "อ่านข้อความจากป้ายนี้", "อ่านเอกสารหน้ากล้อง"

#### 38. `camera_switch_provider` — Provider Switcher
สลับ AI Provider สำหรับวิเคราะห์ภาพ
- Gemini Live / Flash, OpenAI GPT-4o / GPT-4.1, Claude Sonnet / Opus
- **ตัวอย่าง**: "สลับไปใช้ OpenAI", "ใช้ Claude วิเคราะห์"

#### 39. `camera_switch_mode` — Mode Switcher
เปลี่ยนโหมดกล้อง
- Live Stream, Snapshot, Object Detection, AR Overlay
- **ตัวอย่าง**: "เปลี่ยนเป็น AR mode", "ใช้โหมด snapshot"

#### 40. `vision_activate` — เปิดดวงตา (AI-Managed)
สั่งให้แอปเริ่มส่งสตรีมภาพความละเอียดสูงให้ AI แบบ Real-time
- **ตัวอย่าง**: จาร์วิสเรียกใช้เองเมื่อคุณถามว่า "นี่คืออะไร"

#### 41. `vision_deactivate` — ปิดดวงตา (Token Saving)
สั่งหยุดส่งสตรีมภาพทันทีเพื่อประหยัด Token และแบตเตอรี่
- **ตัวอย่าง**: จาร์วิสเรียกใช้เองหลังจากตอบคำถามด้านสายตาเสร็จ

#### 42. `vision_adaptive_logic` — AI Adaptive Vision
ระบบวิเคราะห์ความสำคัญของภาพอัตโนมัติ
- ปรับความละเอียด (Resolution) ตามความซับซ้อนของสิ่งที่เห็น
- ลดการส่งภาพซ้ำหากไม่มีการเคลื่อนไหว (Motion Detection)
- **ตัวอย่าง**: "เปิดโหมดประหยัดพลังงานสายตา"

---

## 📊 สรุปจำนวน Tools ทั้งหมด: **41 Tools** (6 หมวดหมู่)

| หมวด | จำนวน |
|------|-------|
| 🧠 Built-in Tools | 10 |
| 📊 Trading Tools | 10 |
| 📈 SMC Tools | 5 |
| 📁 File Management | 7 |
| 🎯 System Tools | 2 |
| 📷 Camera & Vision | 7 |
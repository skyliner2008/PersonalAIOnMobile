# 🤖 PersonalAIBot — JARVIS for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-orange.svg?style=flat)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Gemini](https://img.shields.io/badge/AI-Gemini%20Flash-green.svg?style=flat&logo=google-gemini)](https://ai.google.dev/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**JARVIS** (PersonalAIBot) คือระบบผู้ช่วย AI ส่วนบุคคลระดับสูงที่สร้างขึ้นด้วย **Kotlin Multiplatform (KMP)** ออกแบบมาเพื่อเป็นทั้งเพื่อนคู่คิดและนักวิเคราะห์การเงินอัจฉริยะ ขับเคลื่อนด้วยพลังของ **Google Gemini 2.0 / 2.5 / 3.x Flash**

---

## 🚀 Key Features

### 🎙️ 1. Live Voice Mode
สั่งการด้วยเสียงแบบ Real-time ผ่าน **Gemini Live API**:
- **Low Latency** — ตอบโต้ความเร็วสูงเหมือนคุยกับมนุษย์
- **Native Audio Flow** — PCM Audio 16kHz พร้อม AEC + Noise Suppression
- **Voice-First Design** — เน้นการพูดคุยเป็นหลัก ไม่ต้องพิมพ์

### 📸 2. Real-time Camera
- วิเคราะห์ภาพจากกล้องแบบ Live ร่วมกับ Gemini Multimodal
- ส่ง frame อัตโนมัติขณะ Live Mode เปิดอยู่

### 🧠 3. 4-Layer Memory Engine
- **Short-term** — บันทึก context การสนทนาปัจจุบัน
- **Long-term** — จำข้อมูลข้ามเซสชันใน SQLite
- **Vector + GraphRAG** — ค้นหาความหมายและความสัมพันธ์
- **Embeddings** — เข้าใจบริบทเชิงความหมาย

### 🤖 4. Multi-Agent Orchestration
- วิเคราะห์ intent และ route งานซับซ้อนไปยัง Swarm of AI Agents
- Skills system — เพิ่ม agent เฉพาะทางได้ไม่จำกัด

### 📊 5. Trading Intelligence (SMC + TA)
- **SMC** — Smart Money Concepts (Order Blocks, BOS/CHoCH, FVG, Liquidity)
- **Technical Analysis** — RSI, MACD, Bollinger, EMA, Volume, ATR
- **Sentiment** — Reddit + Financial News scraping
- **Multi-Timeframe** — M1 → D1 sweep analysis


### 📁 6. Advanced Android File Management
- **Autonomous File Agent** — จัดการไฟล์ได้เหมือนมนุษย์ (List, Read, Write, Move, Delete)
- **Native Document Processing** — วิเคราะห์เอกสาร (PDF, Word, Excel) และรูปภาพ (OCR) โดยส่งไฟล์ตรงให้สมองของ Gemini แบบ Base64 ไม่ต้องพึ่งพา Library ภายนอก
- **Intelligent Path Resolution** — แปลง Virtual Path (`/sdcard/`) เป็น System Path (`/storage/emulated/0/`) อัตโนมัติ

### 🔧 7. Floating Widget
- ทำงานแบบพื้นหลัง — กดปุ่มลอยเพื่อ toggle Live Mode ได้ทุกที่

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0 (KMP) |
| UI | Jetpack Compose + Material3 |
| AI Model | Google Gemini 2.0/2.5/3.x Flash |
| Audio | Gemini Live API (WebSocket) |
| HTTP | Ktor HttpClient |
| Database | SQLDelight (SQLite) |
| Market Data | Binance API v3 |

---

## 🎨 Theme (JarvisTheme)

| Color | Hex | Usage |
|-------|-----|-------|
| Cyan | `#00E5FF` | Primary accent, JARVIS title |
| Dark | `#0A0A14` | Background |
| Surface | `#141422` | TopBar, cards |
| Card | `#1C1C2E` | Chat bubbles, panels |
| Purple | `#7C4DFF` | AI messages, highlights |
| Red | `#FF1744` | LIVE indicator |

---

## 📦 Tool Catalogue

ระบบมีเครื่องมือทั้งหมด **35 tools** แบ่งเป็น 5 หมวดหมู่ สามารถดูได้ผ่านปุ่ม **⊞** (Apps icon) ใน TopBar

---

### 🔷 BUILT-IN TOOLS (6 tools)

#### 1. `calculator` — เครื่องคิดเลข
คำนวณนิพจน์คณิตศาสตร์ทุกรูปแบบ
- บวก ลบ คูณ หาร ยกกำลัง รากที่สอง
- ตรีโกณมิติ (sin, cos, tan)
- ลอการิทึม, เลขชี้กำลัง
- **ตัวอย่าง**: "คำนวณ 15% ของ 8500", "sqrt(144) + 3^4"

#### 2. `unit_converter` — แปลงหน่วย
แปลงหน่วยวัดทุกประเภทในระบบสากล
- ความยาว, น้ำหนัก, อุณหภูมิ, พื้นที่, ปริมาตร
- ความเร็ว, ข้อมูล (KB/MB/GB)
- **ตัวอย่าง**: "100 กิโลเมตรเท่ากับกี่ไมล์", "แปลง 37°C เป็น Fahrenheit"

#### 3. `datetime_info` — วันเวลาและปฏิทิน
ข้อมูลวันที่ เวลา เขตเวลา ทั่วโลก
- วันนี้คือวันอะไร, กี่วันถึง...
- เปรียบเทียบ timezone ทั่วโลก
- นับวันระหว่างสองวัน
- **ตัวอย่าง**: "ตอนนี้กี่โมงที่ Tokyo", "เหลืออีกกี่วันถึงปีใหม่"

#### 4. `random_generator` — สุ่มตัวเลขและข้อมูล
สุ่มค่าต่างๆ ตามที่กำหนด
- ตัวเลข UUID รหัสผ่าน
- เลือกสุ่มจากรายการ (เช่น เมนูอาหาร)
- **ตัวอย่าง**: "สุ่มเลข 1-100", "สุ่มเมนูอาหารเย็นให้หน่อย"

#### 5. `web_search` — ค้นหาข้อมูลออนไลน์
ค้นหาข้อมูล ข่าวสาร และเนื้อหาล่าสุดจากอินเทอร์เน็ต
- ข่าวล่าสุด, ราคา, สภาพอากาศ
- ความรู้ทั่วไป, คำนิยาม
- **ตัวอย่าง**: "ข่าว BTC วันนี้", "นายกรัฐมนตรีไทยคนปัจจุบัน"

#### 6. `memory_manager` — จัดการความจำ
บันทึกและเรียกข้อมูลจากหน่วยความจำระยะยาว
- จำข้อมูลสำคัญข้ามเซสชัน
- ค้นหาบทสนทนาที่ผ่านมา
- **ตัวอย่าง**: "จำไว้ว่าฉันชอบ timeframe 4h", "ฉันเคยบอกอะไรเกี่ยวกับ BTC?"

---

### 📈 TRADING TOOLS (9 tools)

#### 7. `trading_price` — ราคาคริปโตแบบ Real-time
ราคา OHLCV + % เปลี่ยนแปลงใน 24h จาก Binance
- ราคาปัจจุบัน, High/Low 24h, Volume
- **ตัวอย่าง**: "ราคา BTC ตอนนี้", "ETH ขึ้นหรือลงวันนี้"

#### 8. `trading_technical` — Technical Analysis
วิเคราะห์เทคนิคครบถ้วนด้วย Indicator หลัก
- RSI, MACD, Bollinger Bands
- EMA 9/21/50, Volume analysis, ATR
- Overall Signal: BUY / SELL / NEUTRAL
- **ตัวอย่าง**: "TA BTC 4h", "RSI ETH ตอนนี้เท่าไหร่"

#### 9. `trading_sentiment` — Reddit Sentiment
วิเคราะห์ความรู้สึกของ community จาก Reddit
- Bullish/Bearish/Neutral score
- Top keywords, trending topics
- **ตัวอย่าง**: "Reddit คิดยังไงกับ BTC", "sentiment SOL วันนี้"

#### 10. `trading_news` — Financial News
ดึงข่าวการเงินล่าสุดจากหลายแหล่ง
- Sentiment ของข่าวแต่ละชิ้น
- Impact assessment (HIGH/MEDIUM/LOW)
- **ตัวอย่าง**: "ข่าว ETHUSDT ล่าสุด", "มีข่าวอะไรกระทบ BTC บ้าง"

#### 11. `trading_portfolio` — Portfolio Tracker
ติดตาม Portfolio คริปโตแบบ Real-time
- คำนวณมูลค่ารวม, P&L แต่ละเหรียญ
- สัดส่วน allocation
- **ตัวอย่าง**: "portfolio ฉันเป็นยังไงบ้าง", "คำนวณกำไรขาดทุน"

#### 12. `trading_alert` — Price Alert
ตั้ง Alert เมื่อราคาถึง target ที่กำหนด
- Trigger เมื่อ price >= หรือ <= target
- แจ้งเตือนใน session ปัจจุบัน
- **ตัวอย่าง**: "แจ้งเมื่อ BTC ถึง 100000", "set alert ETH ต่ำกว่า 3000"

#### 13. `trading_scan` — Market Scanner
สแกนหา trading opportunities จาก watchlist
- ค้นหา oversold/overbought, breakout, volume spike
- **ตัวอย่าง**: "scan crypto ที่น่าสนใจ", "หา coin ที่ RSI oversold"

#### 14. `trading_backtest` — Strategy Backtester
ทดสอบกลยุทธ์ด้วยข้อมูลย้อนหลัง
- Simulated trades, Win rate, Max drawdown
- Sharpe ratio, Profit factor
- **ตัวอย่าง**: "backtest EMA cross BTC 1h", "ทดสอบ RSI strategy SOL"

#### 15. `trading_combined` — Full Confluence Analysis
วิเคราะห์ครบทุกด้านพร้อมกัน
- TA + Reddit Sentiment + Financial News
- Confluence Decision: BUY / SELL / MIXED
- **ตัวอย่าง**: "วิเคราะห์ BTC ครบทุกด้าน", "combined analysis ETHUSDT 4h"

---

### 🟣 SMC TOOLS — Smart Money Concepts (5 tools)

แปลงจาก TradingView indicator "SMC & Multi-TF Order Blocks Sweeps V8.3" (Pine Script v6)

#### 16. `trading_smc_analysis` — Full SMC Dashboard
วิเคราะห์ครบทุก Smart Money Concept
- Market Structure (BOS/CHoCH)
- Order Blocks + FVG Confirmation
- Liquidity Zones (Equal H/L)
- Premium/Discount zones
- Confluence Stars (★★★★★)
- **ตัวอย่าง**: "วิเคราะห์ SMC BTC", "Order blocks ETH 4h", "zone ไหน strong ที่สุด"

#### 17. `trading_smc_sweeps` — Multi-TF Sweep Detection
ตรวจจับ Liquidity Sweep signals ข้ามทุก Timeframe (M1→H4)
- Sweep = ราคา wick ทะลุ OB แล้ว reclaim >50% (Reversal signal)
- Bullish Sweep → สัญญาณ Long
- Bearish Sweep → สัญญาณ Short
- **ตัวอย่าง**: "sweep BTC ทุก TF", "MTF sweep ETHUSDT", "มี sweep ไหมวันนี้"

#### 18. `trading_smc_liquidity` — MTF Liquidity Zones
แสดง Liquidity Zones ข้ามหลาย Timeframe พร้อม Confluence Stars
- Equal Highs = Sell-side Liquidity (ราคาจะ sweep ก่อนลง)
- Equal Lows = Buy-side Liquidity (ราคาจะ sweep ก่อนขึ้น)
- **ตัวอย่าง**: "liquidity BTC ทุก TF", "Equal highs ETH", "ราคาจะไป sweep ที่ไหน"

#### 19. `trading_smc_orderblocks` — Order Blocks + FVG
ตรวจจับ Bullish/Bearish Order Blocks พร้อม FVG Confirmation
- Bullish OB 🟢 — แหล่ง demand, ราคามักเด้งขึ้น
- Bearish OB 🔴 — แหล่ง supply, ราคามักร่วงลง
- แสดง distance % จากราคาปัจจุบัน + ⚡NEAR tag
- **ตัวอย่าง**: "Order blocks BTC 4h", "OB ที่ยังไม่โดน ETHUSDT", "demand zone SOL"

#### 20. `trading_smc_structure` — Market Structure
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
- **ตัวอย่าง**: "เรียกใช้ skill ของฉัน", "run skill:trading_journal"

#### 35. `system_diagnostics` — System Diagnostics
ตรวจสอบสถานะเครื่องและทรัพยากร
- CPU, RAM, Battery, Storage info
- **ตัวอย่าง**: "เช็คสถานะเครือง", "ความจำเหลือเท่าไหร่?"

---

## 📱 UI Guide

### TopBar Actions
| Icon | Action |
|------|--------|
| ⊞ (Apps) | เปิดรายการ Tools ทั้งหมด (Tool List Dialog) |
| ⚙️ (Settings) | เปิด Settings — เลือก Model, API Key, Widget |

### Tool List Dialog
- **Search bar** — ค้นหา tool ด้วยชื่อหรือคำอธิบาย
- **Category chips** — กรองตามหมวด: All / Built-in / Trading / SMC / System
- **Tool cards** — กดเพื่อขยายดู capabilities + example prompts + function name

### Chat Input
- พิมพ์ข้อความและกด Send หรือกด 🎙️ เพื่อเริ่ม Live Voice Mode
- พิมพ์แล้วถาม JARVIS ได้เลยโดยไม่ต้องระบุ tool — ระบบ route ให้อัตโนมัติ

---

## 🏗️ Project Structure

```
composeApp/src/commonMain/kotlin/com/example/personalaibot/
├── App.kt                          # Entry point
├── JarvisViewModel.kt              # Main ViewModel
├── tools/
│   ├── ToolRegistry.kt             # Registry of all tools
│   ├── ToolExecutor.kt             # Built-in tool executor
│   ├── LiveToolBridge.kt           # Bridge for Live mode
│   └── trading/
│       ├── TradingToolDefinitions.kt
│       ├── TradingToolExecutor.kt
│       ├── TradingApiService.kt
│       ├── SmcToolDefinitions.kt   # 5 SMC tool definitions
│       ├── SmcToolExecutor.kt      # SMC tool executor
│       └── SmcApiService.kt        # SMC algorithms + Binance API
├── ui/
│   ├── screen/
│   │   ├── JarvisTopBar.kt         # TopBar with Tools + Settings buttons
│   │   ├── ToolListDialog.kt       # Full-screen tool catalogue dialog
│   │   ├── SettingsDialog.kt
│   │   ├── ChatInputBar.kt
│   │   └── LiveModePanel.kt
│   ├── components/
│   └── theme/
│       └── JarvisTheme.kt
├── db/                             # SQLDelight database
├── voice/                          # VoiceManager (Live API)
└── memory/                         # Memory Engine
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog+
- Kotlin 2.0+
- Google Gemini API Key ([Get here](https://aistudio.google.com/))

### Setup
1. Clone repository
2. เปิดด้วย Android Studio
3. ใส่ Gemini API Key ใน Settings Dialog ของแอป
4. Build & Run บน Android device/emulator

### Build
```bash
./gradlew :composeApp:assembleDebug
```

---

## 📝 License

MIT License — see [LICENSE](LICENSE) for details.

---

*Built with ❤️ using Kotlin Multiplatform + Gemini AI*

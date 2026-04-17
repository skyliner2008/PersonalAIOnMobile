# 🤖 PersonalAIBot — JARVIS for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-orange.svg?style=flat)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Gemini](https://img.shields.io/badge/AI-Gemini%202.0%20Flash-green.svg?style=flat&logo=google-gemini)](https://ai.google.dev/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**JARVIS** (PersonalAIBot) คือระบบผู้ช่วย AI ส่วนบุคคลระดับสูง (Personal AI Assistant) ที่ออกแบบมาเพื่อเป็นทั้งเพื่อนคู่คิดและนักวิเคราะห์ข้อมูลอัจฉริยะ ขับเคลื่อนด้วยพลังของ **Google Gemini 2.0 Flash Live** และระบบความจำแบบ 6 ชั้น (GraphRAG & Obsidian Wiki)

---

## 🚀 Verified Features (ใช้งานได้จริง 100%)

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

### 🧠 3. Advanced 6-Layer Memory Engine
- **Layer 1: Core Memory** — จำข้อมูลส่วนตัวผู้ใช้และสกัดความสนใจอัตโนมัติ (Profile Persistence)
- **Layer 2: Working Memory** — บันทึกประวัติการคุยปัจจุบันลง SQLite ทันที (Context Tracking)
- **Layer 3: Archival Memory** — ระบบค้นหาความความจำด้วยความหมาย (Semantic Search / Vector Embeddings)
- **Layer 4: GraphRAG Knowledge Graph** — เชื่อมโยงความสัมพันธ์ของแนวคิดต่างๆ เป็นโครงข่ายสมอง (Entities & Edges)
- **Layer 5: Memory Consolidation** — ระบบ "Sleep Cycle" สรุปและย้ายความจำจากระยะสั้นไประยะยาวอัตโนมัติ
- **Layer 6: LLM-Wiki (Obsidian)** — ระบบ "สมองส่วนนอก" ที่ AI และมนุษย์จัดการร่วมกันผ่าน Markdown (Persistent Knowledge Hub)

### 📊 4. JARVIS Advanced Trading Intelligence V14.4 (Institutional Resilience Patch)

> **Status: 🟢 Stable (Institutional Resilience & Health Restore)**  
> **Last Update: 2026-04-17 (V14.4 Maintenance)**  
- **OANDA Price Unity Architecture** — ระบบรวมศูนย์ราคา (`getUnifiedPrice`) ที่เชื่อมโยงทุกเครื่องมือวิเคราะห์ (TA, SMC, Deep Analysis) ให้ใช้ราคาสปอต OANDA เสมอ 100%
- **Zero-Filtering & Full Transparency** — แผ่กางข้อมูลดิบในรายงาน Deep Analysis (แสดง Fibonacci ทุกระดับ) และ SMC Metadata (Fresh/Mitigated Status)
- **Universal Asset Success** — รองรับ หุ้นไทย (.BK), หุ้นนอก, คริปโต, Spot และ Forex อย่างสมบูรณ์และเสถียร 100% 
- **Institutional SMC Integrity** — ขยายฐานข้อมูลย้อนหลังเป็น 3-6 เดือน เพื่อประกันความแม่นยำของ Smart Money Concepts (ต้องมี >150-200 แท่งเสมอ)
- **Advanced SMC Metadata** — รายงาน SMC แสดงระดับความน่าเชื่อถือ (Stars), จำนวน Touches และขนาดของ FVG อย่างละเอียด

### 🤖 5. Automation & Monitoring Dashboard
- **Real-time Alert Management** — หน้าจอ Dashboard ระดับพรีเมียมสำหรับจัดการงานเฝ้าติดตามตลาด
- **Autonomous Triggering** — JARVIS สามารถตัดสินใจและตั้งค่าการแจ้งเตือนได้เองเมื่อเห็นจังหวะที่ "เกือบ" จะมาถึง
- **Once-Only Notification** — ระบบแจ้งเตือนอัจฉริยะ (Notification) แจ้งเพียงครั้งเดียวเมื่อเงื่อนไขเป็นจริง เพื่อป้องกันความรำคาญ

### 🏥 6. JARVIS Diagnostic Engine (Self-Healing)
- **Autonomous Health Verification** — ระบบตรวจสอบตนเองอัตโนมัติ (API Connectivity, Data Accuracy, Database Integrity)
- **Price Source Sync** — ระบบเปรียบเทียบราคาจากหลายแหล่ง (Yahoo, OANDA, TV) เพื่อระบุความเหลื่อมล่วง (Discrepancy) และ Delay
- **Obsidian Wiki Reporting** — สรุปผลการตรวจสอบระบบเป็นไฟล์ Markdown บันทึกลงในไดเรกทอรีส่วนตัวโดยอัตโนมัติ

### 🏗️ 7. Smart Money Concepts (SMC) V14.4
ระบบ SMC ได้ถูกอัปเกรดเป็นระดับสถาบัน (Institutional Grade):
- **Multi-Source Data Hub**: ระบบดึงข้อมูลอัจฉริยะ (Yahoo Finance + Binance Fallback) ประกันข้อมูล OHLCV 100%
- **Threshold Resilience**: ปรับปรุงเกณฑ์การคำนวณขั้นต่ำ (150 Bars) เพื่อความเสถียรสูงสุด
- **Automated Structure**: ตรวจจับ BOS/CHoCH และ Liquidity Zones อัตโนมัติ
- **Premium/Discount Mapping**: แบ่งโซนราคาเพื่อความได้เปรียบในการเข้าเทรด

---

## 🛠️ Tech Stack (v2026)

| Layer | Technology | Status |
|-------|-----------|--------|
| Language | Kotlin 2.0 (KMP) | Stable |
| UI Framework | Jetpack Compose Multiplatform | Stable |
| Primary Brain | Gemini 2.0 Flash Live Preview | Active |
| Multimodal | Live Stream (PCM 16kHz + JPEG) | Active |
| Database | SQLDelight + SQLite Persistence | Active |
| Background Service | Android Foreground (DataSync) | Active |
| Logic Controller | JarvisOrchestrator (Single-Agent) | Active |

---

## 📦 Tool Catalogue (Total: 52 Tools)

### 🧠 BUILT-IN & SYSTEM TOOLS (11 tools)
- `calculate`: คำนวณนิพจน์คณิตศาสตร์ซับซ้อน
- `get_current_datetime`: ข้อมูลวันเวลาและปฏิทินปัจจุบัน
- `remember_fact`: บันทึกข้อมูลสำคัญลงความจำระยะยาว
- `recall_memory`: ดึงข้อมูลจากฐานความรู้เดิม
- `convert_units`: แปลงหน่วยสากลทุกประเภท
- `set_reminder`: ตั้งการแจ้งเตือน/TODO
- `translate_text`: แปลภาษาแบบ Multilingual
- `summarize_text`: สรุปข้อความยาวๆ พร้อมกำหนดระดับความละเอียด
- `search_web`: ค้นหาข้อมูลล่าสุดจากโลกออนไลน์
- `system_run_diagnostics`: ตรวจสอบสุขภาพระบบ (Self-healing)
- `system_check_connectivity`: ตรวจสอบการเชื่อมต่อ API ทั้งหมด

### 📊 TRADING TOOLS (20 tools)
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
- `trading_fundamental_analysis`: วิเคราะห์ปัจจัยพื้นฐาน (Fundamental)
- `trading_fear_greed`: ดัชนีความกลัวและความโลภ (Crypto)
- `trading_macro_calendar`: ปฏิทินเหตุการณ์เศรษฐกิจโลก
- `trading_correlation_matrix`: คำนวณความสัมพันธ์ระหว่างสินทรัพย์
- `trading_position_sizing`: ช่วยคำนวณขนาดไม้ที่เหมาะสม
- `automation_manage_alerts`: ระบบสร้าง/ลบงานเฝ้าติดตามอัตโนมัติ
- `trading_deep_analysis_suite`: วิเคราะห์ 5 มิติ (LSD, Orderflow, Fibo Score) - **Institutional Grade** 

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

## 📊 สรุปจำนวน Tools ทั้งหมด: **52 Tools** (5 หมวดหมู่หลัก)

| หมวดหมู่ | จำนวน | ความสามารถหลัก |
| :--- | :---: | :--- |
| **Trading & Finance** | 20 | ราคา Real-time, TA, Scanner, **Institutional Stability V14.4** |
| **SMC (Smart Money)** | 5 | Market Structure, Order Blocks, Liquidity Sweeps, Premium/Discount |
| **Files & Documents** | 7 | จัดการไฟล์ในเครื่อง, อ่าน/เขียน, วิเคราะห์ PDF/Word, ค้นหาข้อมูล |
| **Vision & Camera** | 9 | Gemini Live Vision, Object Detection, Scan QR/Barcode, Text OCR |
| **Core & Intelligence** | 11 | ค้นหาเว็บ, แปลภาษา, สรุปความ, คำนวณ, บันทึกความจำระยะยาว, Diagnostic |
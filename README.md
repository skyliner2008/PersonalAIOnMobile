# 🤖 PersonalAIBot — JARVIS for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-orange.svg?style=flat)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Gemini](https://img.shields.io/badge/AI-Gemini%203%20Series-green.svg?style=flat&logo=google-gemini)](https://ai.google.dev/)
[![Release APK](https://img.shields.io/github/v/release/skyliner2008/PersonalAIOnMobile?color=brightgreen&label=Download%20Release%20APK&logo=android)](https://github.com/skyliner2008/PersonalAIOnMobile/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**JARVIS** (PersonalAIBot) คือระบบผู้ช่วย AI ส่วนบุคคลระดับสูง (Personal AI Assistant) ที่ออกแบบมาเพื่อเป็นทั้งเพื่อนคู่คิดและนักวิเคราะห์การเงินอัจฉริยะ ขับเคลื่อนด้วย **Google Gemini 3 Series (Free-Tier Optimized)** + ระบบ **Multi-Provider Fallback**, ความจำ 6 ชั้น (GraphRAG & Obsidian Wiki), ระบบเฝ้าติดตามตลาดอัตโนมัติ (Alert System V2) และระบบ **MT5 Full Agent Control** สำหรับเทรดแบบครบวงจร

- **🔌 Multi-Provider + Fallback หลายชั้น** — Gemini (Multi API Key + Model Fallback Chain) → Groq → OpenRouter → MiniMax สลับอัตโนมัติเมื่อติด limit พร้อม Auto-Test คัดเฉพาะโมเดลที่ใช้ tool ได้จริง
- **🔔 Alert System V2** — ตั้งเงื่อนไขเฝ้าราคา/อินดิเคเตอร์/SMC/**สัญญาณกลยุทธ์ (Signal Alert)** ได้จริง แจ้งเตือนพร้อมปุ่ม "🗑 ลบ / 🔁 ซ้ำ" + การ์ด 3D ในแชท + เสียงพูดแจ้งเตือนเลือก engine ได้ (AI Live / เครื่อง) และ Adaptive Interval เร่งเช็คเมื่อราคาใกล้เป้า
- **🎙️ Live Voice + Vision** — คุยสดกับ Gemini Live, เลือกเสียงได้ 30 โปรไฟล์ (ผูกตัวตน/คำลงท้ายอัตโนมัติ), เปิด "ตา" ให้ AI มองผ่านกล้องพร้อม AR Overlay
- **📲 Mobile Android App (Compose Multiplatform)** — ดีไซน์พรีเมียม, แนบไฟล์/รูป/PDF ในแชทให้ AI วิเคราะห์, สร้างไฟล์ Excel จริง, Symbol Catalogue, Decision Feed, Auto Trading Controls และ **Setup Checklist** 6 ข้อใน Settings (แจ้งเตือน/ไมค์/กล้อง/Overlay/All-files/Battery — ปุ่มพาไปหน้าตั้งค่าระบบตรงจุด)

---

## 🌟 ระบบหลัก (Core Systems)

### 🎙️ 1. Live Voice & Voice Profile ↔ Identity

- **Gemini Live API** — สนทนาสดด้วยเสียง (PCM 16kHz) latency ต่ำ รองรับการขัดจังหวะ (barge-in) และ flush คิวเสียงอัตโนมัติ
- **30 Voice Profiles** — เสียงหญิง/ชาย หลายน้ำเสียงและอารมณ์ เลือกได้จาก Settings หรือสั่งด้วยเสียง ("เปลี่ยนเสียงเป็น Leda") — เปลี่ยนแล้ว AI ทักทายยืนยันด้วยเสียงใหม่ทันที
- **เสียงผูกกับตัวตน** — เสียงหญิงพูดลงท้าย "ค่ะ" / เสียงชาย "ครับ" อัตโนมัติ จำข้าม session ผ่าน Core Memory
- **Vision (ตาของ JARVIS)** — เปิดกล้องให้ AI มองโลกจริง พูดสรุปสิ่งที่เห็นทันทีโดยไม่ต้องถามซ้ำ, OCR อ่านข้อความ, Object Detection (AR Overlay), สลับ vision provider ตามโมเดลหลักอัตโนมัติ
- **Live Tool Bridge** — โมเดล Live สั่ง tool ผ่าน chat model อีกชั้น (Live API ไม่เรียก tool เอง) ทำให้เสียงไม่ถูกกระทบเมื่อสลับ chat provider

### 🧠 2. Advanced 6-Layer Memory Engine

- **Layer 1: Core Memory** — จำข้อมูลตัวตนผู้ใช้/AI (identity จัดการผ่าน tool/Settings เท่านั้น กัน heuristic ทับ)
- **Layer 2: Working Memory** — บันทึกประวัติการคุยลง SQLite ทันที (Context Tracking)
- **Layer 3: Archival Memory** — Semantic Search / Vector Embeddings (Local ONNX หรือ Gemini Cloud — auto-backfill)
- **Layer 4: GraphRAG Knowledge Graph** — โครงข่ายความสัมพันธ์แนวคิด + retrieval จริงผ่าน `recall_memory`
- **Layer 5: Memory Consolidation** — "Sleep Cycle" สรุปและย้ายความจำระยะสั้นไประยะยาว (auto-trigger เมื่อแชทสะสม 200 ข้อความ)
- **Layer 6: LLM-Wiki (Obsidian)** — ระบบ "สมองส่วนนอก" ที่ AI และมนุษย์จัดการร่วมกันผ่าน Markdown

### 📊 3. Trading Intelligence บนมือถือ (TV-Powered)

- **Indicator Alert Provider** — คำนวณ EMA20/50/200, EMA Cross (Golden/Death), RSI14, MACD, Stoch, CCI, Bollinger Bands, ATR เองจากแท่งเทียน cache (incremental fetch ไม่ดึงใหม่ทั้งชุด) แม่นกว่า TradingView scanner
- **Strategy Signal Provider** — 5 กลยุทธ์จากคลัง Quantpedia คำนวณในเครื่องจาก OHLCV ล้วน (TSMOM, Trend EMA50/200, Reversal RSI+BB, Donchian Breakout, 52-Week High) + consensus score
- **🎯 Signal Alert Provider** — ตรวจ edge สัญญาณซื้อ/ขายใหม่เฉพาะแท่งปิดล่าสุดจาก **8 กลยุทธ์** (5 ข้างต้น + EMA Cross, UT Bot, 3-Bar Reversal) พร้อม payload ครบ: Entry/SL/TP/RR เฉพาะกลยุทธ์ (คำนวณจาก ATR) + เหตุผลภาษาไทย + context snapshot — baseline กันสัญญาณเก่าเด้งตอนสร้าง alert อัตโนมัติ และบันทึกทุกสัญญาณลง DB เพื่อติดตามผล TP/SL จริง (win-rate/avgR)
- **SMC Alert Provider** — แปลง SMC analysis เป็น field เฝ้าติดตามได้ 18 ตัว: zone (Premium/Discount), BOS/CHoCH, Order Blocks, FVG, Liquidity zones + ดาว
- **Deep Analysis Suite ครบ 5 มิติ** — LSD state + confluence, Orderflow Delta, Fibo Score, Momentum, Squeeze (9 fields) พร้อม TV local fallback + circuit breaker เมื่อ bridge ล่ม
- **Multi-Timeframe ทุก tool** — ระบุ TF ได้ด้วย suffix `symbol@TF` เช่น `XAUUSD@15m` (1m/5m/15m/30m/1h/4h/1D)
- **Chart Dashboard (Lightweight Charts v5.1, offline)** — กราฟ multi-pane ในตัวแอป: layouts (single / RSI / MACD / RSI+MACD / Volume / Full), overlays EMA20/50/200 + Bollinger Bands + Donchian (DC20) + **Signal Markers (SIG)** จุดสัญญาณย้อนหลัง 8 ชนิดบนกราฟ, วาด SMC zones (OB/FVG), ข้อมูลแท่งเทียนจาก TV incremental cache (market-closed backoff กันดึงซ้ำตอนตลาดปิด), สลับไป TradingView widget ได้ทุกเมื่อ — **AI ปรับ layout/indicator เองผ่าน tool `chart_dashboard_control`** ทั้งแชทและ Live ("เปิดกราฟทองคำ 1 ชั่วโมง เพิ่ม RSI กับ MACD")
- **Rich Chat Rendering** — ตาราง markdown แสดงเป็นตารางจริง (header สี + scroll แนวนอน) และ AI แนบ **chart card** ในแชทได้ (```chart fence) แตะการ์ดเพื่อเปิดกราฟเต็มจอด้วย config นั้นทันที

### 🔔 4. Alert System V2 (ระบบเฝ้าติดตามตลาด)

- **🎯 Signal Alert (ใหม่)** — job type `trading_signal_alert` เฝ้าสัญญาณซื้อ/ขายจาก 8 กลยุทธ์ edge-triggered; payload ครบ Entry/SL/TP/RR/ATR + เหตุผลไทย; **re-arm อัตโนมัติ**ทุกแท่งใหม่ (ไม่ต้องกดซ้ำ) และสร้างผ่านเสียง/แชท/UI ได้ (AI แปลง `signal contains BUY` ให้เป็น signal alert อัตโนมัติ ทำงานตรงกันทุกเครื่อง)
- **Alert Lifecycle ชัดเจน** — alert ทั่วไป (ราคา/indicator) ที่ TRIGGERED แล้ว **ออกจาก job loop ทันที** ไม่วนเช็กเปลืองทรัพยากร; re-arm ทางเดียวคือปุ่ม **🔁 แจ้งเตือนซ้ำ** (บน notification และในหน้า Cron Jobs) หรือ **🗑 ลบแจ้งเตือน** (ลบออกจาก list จริง ประวัติยังเก็บ)
- **2 โหมดส่ง (delivery)** — `ai`: AI quick-check ก่อนแจ้ง (fallback chain หลายโมเดล) | `direct`: แจ้งตรงทันทีไม่เรียก AI — ทั้งสองโหมดส่งเข้าแชทพร้อม notification
- **การ์ด 3D ในแชท** — ทุก alert แสดงเป็นการ์ดจัดระเบียบ (ป้าย 🟢BUY/🔴SELL + gradient/shadow + ตาราง Entry/TP/SL/RR/ATR เฉพาะ field ที่จำเป็น) พร้อม footer บอก engine เสียงที่ใช้จริง; เก่าเป็น markdown table ยังเปิดดูได้ (backward compatible)
- **🔊 เสียงแจ้งเตือนเลือก engine ได้** — `⚡ เครื่อง` (Android TTS ทันที/ไม่จำกัด — default) | `✨ AI Live` (streaming ผ่าน Live API ได้ยินตั้งแต่ chunk แรก) — chain เริ่มจาก **โมเดล Live ที่เลือกใน Settings** เสมอ → fallback โมเดล Live อีกตัว → Android TTS; ใช้ voice profile + identity เดียวกับ Live mode
- **Actionable Notifications** — ปุ่ม "🗑 ลบแจ้งเตือน" / "🔁 แจ้งเตือนซ้ำ" (manifest receiver ทำงานได้แม้แอปถูกฆ่า) + สถานะชัดเจน "ACTIVE · เฝ้าดูอยู่" / "TRIGGERED · รอเลือก ลบ/ซ้ำ"
- **Adaptive Interval** — tick หลัก 30 วินาที + เร่งเช็คอัตโนมัติเมื่อราคาใกล้เป้า (<0.1% → 30 วิ, <0.5% → 1 นาที, ไกล → ตามที่ตั้ง) รองรับทองคำที่วิ่งแรง
- **AlertFieldCatalog** — dropdown ตอนสร้าง alert เลือกได้เฉพาะ tool/field ที่ดึงค่าได้จริง + validate operator (field ข้อความบังคับ `==`/`contains`) กันตั้งเงื่อนไขมั่ว ทั้งฝั่ง UI และฝั่ง AI
- **14+ Preset ลัด** — ราคาถึงเป้า, RSI Overbought/Oversold, Golden/Death Cross, Discount/Premium Zone, Bollinger Squeeze, ADX แรง, Extreme Fear, High Confluence, LSD ขาขึ้น, Squeeze Breakout, 📡 Signal BUY/SELL, Consensus STRONG_BUY/SELL, Donchian Breakout ฯลฯ
- **🧪 Auto-Test** — ปุ่มเดียวไล่ทดสอบดึงข้อมูลทุก tool ที่ alert ใช้ แสดงสถานะสดในแอป
- **แก้ไขครบทุกช่อง** — ชื่อ, ค่าเป้าหมาย, ความถี่, โหมดส่ง ผ่าน UI หรือสั่ง AI (`automation_manage_alerts`: create/update/rename/delete/list)
- **Scheduled Tasks** — `automation_manage_schedule` งานตามเวลา (one-time/daily) ถึงเวลาปลุก AI มาทำตาม prompt แล้วส่งผลเข้าแชทด้วย
- **🔍 Pipeline Trace Log** — log ละเอียดทุกขั้นตอนแจ้งเตือน (🔔 FIRE config → 🧠 AI summary ต่อโมเดล → 🔊 voice engine/fallback → 💬 push แชท) debug จาก logcat ได้จบในที่เดียว

### 🔌 5. Provider System — Multi-Key, Fallback & Auto-Test

- **Providers ปัจจุบัน**: Gemini / OpenRouter / Groq / MiniMax (ตัด OpenAI, Claude, Vertex, LiteLLM, NVIDIA NIM ออก — ไม่มี free tier ที่ใช้งาน tool ได้จริง)
- **Gemini Multi API Key** — เพิ่ม/แก้/ลบ key ได้หลายอัน (Free tier หลายเมล์) rotation อัตโนมัติเมื่อ 429/503 พร้อม UI จัดการแบบ mask หัว-ท้าย
- **Model Fallback Chain** — เรียงตามโควต้า free tier จริง ปรับลำดับเองได้ใน Settings; key หมดก่อนค่อยสลับโมเดล
- **Cross-Provider Fallback** — Gemini ตายทั้ง chain → Groq → OpenRouter (เลือกเฉพาะโมเดลที่ auto-test ผ่าน) — ผลเทสจริง: Groq ผ่าน 3/8, OpenRouter free ผ่าน 6/17
- **Model Auto-Tester** — เทส chat + tool calling ทุกโมเดล บันทึกผล (`model_caps_v1`) ซ่อนโมเดลที่ใช้ไม่ได้อัตโนมัติ
- **Hardening** — tool-loop detection (กัน model เรียก tool ซ้ำไม่จบ), 429 backoff ตามเวลาที่ API บอก, empty-round retry, timeout 120s, trading-context บังคับ sort trading tools ขึ้นก่อนและซ่อน search_web, sanitize/mask API key ใน log

### 🤖 6. Custom Tools — AI สร้าง/แก้/ลบเครื่องมือตัวเอง

- **CRUD ครบวงจรผ่าน AI** — `system_create_agent_tool` (สร้าง/แก้ไขเขียนทับ), `system_list_agent_tools` (ดูรายการ+logic), `system_delete_agent_tool`
- **Persist ข้าม session** — บันทึกลง `custom_agent_tools/*.json` โหลดกลับอัตโนมัติตอนเปิดแอป ใช้ได้ทั้งโหมด chat และ live
- **ปลอดภัย** — sanitize ชื่อ, JSON ผ่าน kotlinx.serialization, register เข้า registry ทันทีโดยไม่ชน declaration เดิม

### 📁 7. File Tools & Attachments

- **แนบไฟล์ในแชท** — ปุ่ม 📎 แนบได้สูงสุด 5 ไฟล์ (รูป/PDF/DOCX สูงสุด 15MB ส่ง inline ให้ Gemini วิเคราะห์ native; ไฟล์ text ฝังเข้า prompt)
- **file_write รองรับ .xlsx จริง** — เขียน Excel ด้วย zip+XML ในตัว (ไม่พึ่ง POI) — เช่นอ่านสลิปหลายใบ → สร้างตารางรายรับ-รายจ่ายลง Download
- **verify + media scan** — ตรวจหลังเขียนว่าไฟล์อยู่จริง + MediaScanner ให้ file manager เห็นทันที
- **Security** — path guard ทุก op (read/write/delete/move/analyze), canonicalize กัน traversal, extension allowlist, จำกัด read 200K ตัวอักษร, ห้ามลบโฟลเดอร์สาธารณะทั้งก้อน

### 🏥 8. JARVIS Diagnostic Engine (Self-Healing)

- **Autonomous Health Verification** — ตรวจสอบตนเองอัตโนมัติ (API Connectivity, Data Accuracy, Database Integrity จริง ไม่ใช่ placeholder)
- **Price Source Sync** — เปรียบเทียบราคาจากหลายแหล่ง (Yahoo, OANDA, TV) ระบุความเหลื่อมและ Delay
- **Obsidian Wiki Reporting** — สรุปผลการตรวจสอบระบบเป็นไฟล์ Markdown อัตโนมัติ
- **Log Hygiene** — mask API key หัว-ท้ายใน logcat, sanitize key ออกจากข้อความ error, log preview คำตอบ AI + ผล tool สำหรับ debug

---

## 📊 9. mt5-core-server — Trading Intelligence Engine (Node.js)

> **Status: Production = V26.26 (Modular Codebase & Verification Pass 2026-05-23)**
> **Runtime ปัจจุบัน: `engineMode: M15_WALL_SCALPING` — decision path ลดรูปเหลือ IndicatorPipeline + PriceMap wall detection**

- **M15 Wall Scalping Simplification (2026-05-30 → 06-02)** — กลยุทธ์เริ่มต้นเฉพาะ `SCALPING`; wall registry คง identity ข้าม cycle; M15/M30/H1/H4 เป็น anchor, M1/M5 เป็น confirmation (ต้อง touch/reclaim + directional body + micro-structure break); หยุดเข้าเมื่อถึง daily/consecutive loss limit; sync closed history กลับเข้า journal อัตโนมัติ (`SimpleScalpingEngine.ts`)
- **V26.26 Modular Codebase (2026-05-23)** — แยก helpers จาก `autoTradingService.ts` (~6,124 บรรทัด) เป็น `decisionLogger.ts` + `strategySelector.ts` — ผ่าน Unit Tests 103/103
- **V26.24–26.25 Runtime Controls** — เปิด/ปิด gate และ strategy รายตัวผ่าน `adaptive.gateToggles`/`strategyToggles` บน UI; Unified Zone Gate + Execution Quality Gate; Regime-Adaptive Scoring; SL Proportional Scaling on TP Cap
- **V26.18–26.23 Execution Hardening** — MTF weighting ใหม่ (H4 22/H1 23/M30 15/M15 18/M5 15/M1 7), Breakout ต้องมี M15/M5 confirm, wall-break scalp playbook, fresh decision price sync, EA-only confidence 55%, post-TP cooldown
- **V25 Real-Time Wall Engine** — 7-layer event-driven + Wall State Machine (IDLE→APPROACH→REACT→CONFIRM→COMMITTED) + 5 playbooks (PB1 Wall-Touch Scalp default) — กฎเหล็ก "no wall, no trade" + 7 coordination patches (V25.1)
- **Context Levels (V24.2)** — Daily Pivot Points, Session VWAP (NY open anchored), Auto-Fibonacci, Confluence Boost (+1★) — coverage ~85% ของ indicator ที่ pro retail ใช้
- **Analytics Scripts** — `npm run analyze [YYYY-MM-DD]` (Bangkok-day), `advanced_analytics.mjs`, `analytics:gate`, `analytics:audit`, `analyze:logs`, `analyze:deep` (ผ่านการทดสอบจริง: 15,796 cycles, top reject reasons)
- **TradingView Pine V8.5** — SMC & Multi-TF Order Blocks Sweeps สำหรับดู Wall Map บน M15 พร้อม MTF confluence ดาวซ้อน + toggle ครบ
- **ย้อนหลัง V17–V24** — Institutional SMC (`smartmoneyconcepts`), Pruning Gate (ลด token ~70%), Structurally-Aware SL/TP, Dynamic MTF Zone Gates, Cluster Trading Logic, Sequential Offset Recovery, Outcome Attribution Loop, AI Model Leaderboard — รายละเอียดเต็มใน `.obsidian-wiki/07_Trading_Intelligence/`

### 🔗 MT5 Full Agent Control (Broker-First)

> **Status: 🟢 Stable (21 MT5 Tools)** — Mobile (KMP) → mt5-core-server (Node.js:8090) → Python Bridge → MetaTrader5 API

- **Broker-First Data** — ราคา, volume, positions, OHLCV จาก MT5 Broker โดยตรง (ใช้เฉพาะเมื่อ user ระบุชัด — ค่าเริ่มต้นวิเคราะห์ผ่าน TradingView)
- **Full Account Control** — equity, balance, margin, P&L, symbols, positions, orders, history, batch close/break-even
- **Advanced Intelligence Suite** — Market Scanner, Correlation Radar, Sentiment Gauge, Institutional Flow, Economic Radar, Trade Journal (AI scoring)
- **Audit Trail** — บันทึกทุก trade action ที่ AI สั่ง พร้อมคะแนนคุณภาพ

---

## 🛠️ Prerequisites (ข้อกำหนดพื้นฐาน)

- **Java JDK 21** (มีมาพร้อม Android Studio ในโฟลเดอร์ `jbr`)
- **Android Studio** เวอร์ชันล่าสุด
- **Node.js** — สำหรับรัน `mt5-core-server`
- **Python 3.10+** — สำหรับ `mt5_bridge.py`

---

## 🛠️ Tech Stack (v2026)

| Layer | Technology | Status |
|-------|-----------|--------|
| Language | Kotlin 2.0 (KMP) | Stable |
| UI Framework | Jetpack Compose Multiplatform | Stable |
| Primary Brain | Gemini 3 Series (Free-Tier Optimized, Multi-Key) | Active |
| AI Providers | Gemini / OpenRouter / Groq / MiniMax (+ cross-provider fallback) | Active |
| Multimodal | Live Stream (PCM 16kHz + JPEG) | Active |
| Database | SQLDelight + SQLite Persistence | Active |
| Embedding (Mobile) | LocalOnnx (paraphrase-multilingual-MiniLM-L12-v2, ~117MB offline) / Gemini Cloud cascade | Active |
| Background Service | Android Foreground (DataSync) + Manifest Alert Receivers | Active |
| Logic Controller | JarvisOrchestrator (Multi-Provider + Fallback) | Active |
| MT5 Core Server | Node.js + Express (port 8090) — M15_WALL_SCALPING | Active |
| MT5 Python Bridge | Python + MetaTrader5 API | Active |

---

## 📦 Tool Catalogue (Total: 91 Tools)

### 🧠 BUILT-IN & SYSTEM TOOLS (21 tools)
- `calculate`: คำนวณนิพจน์คณิตศาสตร์
- `get_current_datetime`: ข้อมูลวันเวลาและปฏิทิน
- `remember_fact`: บันทึกข้อมูลลงความจำระยะยาว
- `recall_memory`: Semantic search จากฐานความรู้
- `convert_units`: แปลงหน่วยสากล
- `set_reminder`: ตั้งการเตือน/TODO
- `format_json`: จัดรูปแบบ JSON
- `translate_text`: แปลภาษา
- `summarize_text`: สรุปข้อความยาว
- `search_web`: ค้นหาเว็บ (ถูกซ่อนอัตโนมัติใน trading context)
- `identity_update`: AI ปรับแต่งตัวตน/ข้อมูลผู้ใช้เมื่อถูกสั่ง
- `analyze_and_display_report`: ส่งรายงานยาวลงแชทแล้วพูดสรุป
- `chart_dashboard_control`: 📊 AI ควบคุมหน้ากราฟเอง — เปิด/ปิดกราฟ, เปลี่ยน symbol/timeframe, ปรับ layout (single/rsi/macd/rsi_macd/volume/full), เปิด-ปิด indicator (EMA20/50/200, Bollinger), สลับ Dashboard↔TradingView
- `system_run_diagnostics`: ตรวจสุขภาพระบบ (Self-healing)
- `system_check_connectivity`: ตรวจการเชื่อมต่อ API
- `system_create_agent_tool`: 🤖 AI สร้าง/แก้ไข tool เอง (persist ข้าม session)
- `system_list_agent_tools`: ดูรายการ custom tools + logic ข้างใน
- `system_delete_agent_tool`: ลบ custom tool (ไฟล์+registry)
- `voice_summary`: สรุปเฉพาะส่วนที่พูดในโหมดเสียง
- `mt5_place_order` / `mt5_close_position`: alias ส่ง/ปิดออเดอร์ MT5

### 📊 TRADING TOOLS (28 tools)
- `trading_price`: ราคา Real-time (Stocks/Crypto/Forex/Gold — TV primary + fallback)
- `trading_market_snapshot`: ภาพรวมตลาดตามกลุ่มอุตสาหกรรม
- `trading_top_gainers` / `trading_top_losers`: ตัวพุ่ง/ดิ่งแรงสุด
- `trading_technical_analysis`: TA (RSI, MACD, BB, EMA) รองรับ @TF + OANDA/FX_IDC fallback
- `trading_multi_timeframe`: ความสอดคล้องทุก TF (W → 15m)
- `trading_bollinger_scan` / `trading_oversold_scan` / `trading_overbought_scan` / `trading_volume_breakout`: Scanners
- `trading_sentiment`: อารมณ์ตลาดจาก Reddit ⚠️ (upstream 403 เป็นครั้งคราว)
- `trading_news`: ข่าวการเงิน 6 แหล่ง (Google News + Yahoo/CNBC/MarketWatch/Investing.com/CoinDesk)
- `trading_combined`: TA + News + Sentiment
- `trading_fundamental_analysis`: ปัจจัยพื้นฐาน
- `trading_fear_greed`: 🌡️ Crypto Fear & Greed (alternative.me)
- `trading_macro_calendar`: 📅 ปฏิทินเศรษฐกิจ (ForexFactory, เวลาไทย)
- `trading_economic_data`: 🇺🇸 FRED (GDP, CPI, ว่างงาน, ดอกเบี้ย Fed — ไม่ต้อง API Key)
- `trading_correlation_matrix`: Correlation ระหว่างสินทรัพย์
- `trading_position_sizing`: คำนวณขนาดไม้
- `trading_crypto_overview`: 🪙 CoinGecko (Market Cap, Dominance, Trending)
- `trading_deep_analysis_suite`: วิเคราะห์ 5 มิติ (LSD, Orderflow, Fibo, Momentum, Squeeze) + TV local fallback
- `trading_harmonic_scan`: Harmonic Patterns (Gartley, Bat, Butterfly)
- `trading_elliot_modern_analysis`: Elliott Wave แบบ Modern
- `trading_strategy_signal`: สัญญาณจาก 5 กลยุทธ์ Quantpedia คำนวณในเครื่อง (TSMOM/Trend/Reversal/Donchian/52W-High) + consensus
- `trading_signal_stats`: สถิติสัญญาณ — backtest ย้อนหลัง (win-rate/avgR) หรือผล TP/SL จริงจาก signal ที่ยิงไปแล้ว (source=live)
- `automation_manage_alerts`: สร้าง/แก้/ rename /ลบ/list alerts (validate ด้วย AlertFieldCatalog)
- `automation_manage_schedule`: ⏰ งานตามเวลา (one-time/daily)

### 🔗 MT5 BRIDGE TOOLS (21 tools)
**Core Actions:** `trading_mt5_order`, `trading_mt5_close_position`, `trading_mt5_modify_position`

**Core Agent (Broker โดยตรง):** `trading_mt5_account_info`, `trading_mt5_list_positions`, `trading_mt5_list_orders`, `trading_mt5_list_history`, `trading_mt5_candles`, `trading_mt5_symbol_info`, `trading_mt5_symbol_search`, `trading_mt5_analyze`, `trading_mt5_close_all` ⚠️, `trading_mt5_break_even_all` ⚠️, `trading_mt5_snapshot`, `trading_mt5_trade_actions`

**Advanced Intelligence:** `trading_mt5_market_scanner`, `trading_mt5_correlation_radar`, `trading_mt5_sentiment_gauge`, `trading_mt5_institutional_flow`, `trading_mt5_economic_radar`, `trading_mt5_trade_journal`

### 📈 SMC TOOLS (5 tools)
- `trading_smc_analysis`: Full SMC Dashboard (รองรับ @TF)
- `trading_smc_sweeps`: MTF Sweep Detection
- `trading_smc_liquidity`: MTF Liquidity Zones & Stars
- `trading_smc_orderblocks`: Order Blocks + FVG
- `trading_smc_structure`: Market Structure (BOS/CHoCH, Premium/Discount)

### 📚 STRATEGY LIBRARY (3 tools — offline)
- `strategy_list` / `strategy_search` / `strategy_explain`: คลังกลยุทธ์ Quantpedia 60 แบบ 9 หมวด พร้อมโค้ด QuantConnect

### 📁 FILE MANAGEMENT (7 tools)
- `file_list`, `file_read`, `file_write` (รองรับ **.xlsx จริง**), `file_delete`, `file_analyze` (OCR/PDF), `file_move`, `file_search`

### 📷 CAMERA, VISION & VOICE (9 tools)
- `vision_activate` / `vision_deactivate`: เปิด/ปิดตา AI
- `camera_analyze_scene`: วิเคราะห์ภาพ
- `camera_detect_objects`: ตรวจจับวัตถุ (AR Overlay)
- `camera_read_text`: OCR
- `camera_switch_provider` / `camera_switch_mode`
- `voice_get_profiles`: ดูเสียง 30 โปรไฟล์
- `voice_set_profile`: เปลี่ยนเสียง (ผูกตัวตน/คำลงท้ายอัตโนมัติ)

---

## 🚀 Roadmap

1. **Rich Chat Rendering** — แสดงตาราง / รูป / กราฟ / infographic ในแชท (markdown table renderer, chart library)
2. **Multi-Agent Orchestration (Swarm)** — กระจายงานให้ AI Agent เฉพาะทางทำงานพร้อมกัน
3. **Portfolio Hub** — ติดตามพอร์ตการลงทุน + P&L Analytics
4. **Strategy Backtester** — ทดสอบกลยุทธ์แบบครบวงจร
5. **Hardware Extension** — Smart Home / Wearables

---

## 📚 เอกสารอ้างอิง (Obsidian Wiki)

- `.obsidian-wiki/01_Architecture/` — สถาปัตยกรรม + AI subsystem reviews
- `.obsidian-wiki/02_Components/Alert_System_V2.md` — ระบบ Alert + AlertFieldCatalog
- `.obsidian-wiki/04_Tasks/App_Review_Checklist.md` — checklist ทดสอบ 15 หมวด (ผ่าน 13/15)
- `.obsidian-wiki/07_Trading_Intelligence/` — ประวัติ mt5-core-server V17–V26 ฉบับเต็ม
- `.obsidian-wiki/00_System/log.md` — บันทึกการพัฒนาทั้งหมด

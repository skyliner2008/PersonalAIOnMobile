# 🤖 PersonalAIBot — JARVIS for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-orange.svg?style=flat)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Gemini](https://img.shields.io/badge/AI-Gemini%203%20Series-green.svg?style=flat&logo=google-gemini)](https://ai.google.dev/)
[![Release APK](https://img.shields.io/github/v/release/skyliner2008/PersonalAIOnMobile?color=brightgreen&label=Download%20Release%20APK&logo=android)](https://github.com/skyliner2008/PersonalAIOnMobile/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **Gemini 3.1 Flash Live Primary Model & Automatic Model Switch Fix (2026-09-09):**
> - **Primary Default Live Model (`gemini-3.1-flash-live-preview`)**:
>   - Established `gemini-3.1-flash-live-preview` as the primary default Live model (`DEFAULT_LIVE_MODEL` and index 0 in `SEED_LIVE_MODELS` and `liveCandidates`). It delivers the lowest latency (~835ms READY), natural Thai prosody, and the most reliable native tool calling (`device_always_live`, `trading_smc_analysis`).
> - **Root-Cause Resolution of Spontaneous Model Switching**:
>   - **Removed from `deprecatedLiveModels`**: Removed `gemini-3.1-flash-live-preview` from `deprecatedLiveModels` in `SettingsController.kt`. Previously, cold-start boot mistakenly detected it as deprecated and overwrote SQLite with the older `09-2025` fallback.
>   - **Prevented Silent SQLite Overwrite on Transient Fallback**: Removed `settings.updateLiveModelSilently(winningModel)` from `JarvisViewModel.kt`. Temporary runtime fallbacks during brief network hiccups no longer permanently overwrite the user's manual setting in the database.
>   - **Clean Session Start**: Configured `LiveGeminiService.kt` to always reset `liveModelName` to the user's configured model at the start of each user-initiated conversation, preventing fallback models from sticking across sessions.
>   - **Expanded Setup Watchdog & Reduced Penalty**: Increased `setupWatchdog` delay from 3500ms to 6000ms to accommodate mobile data handshake latencies without premature timeouts, and reduced `penalizeLiveModel` duration from 15 minutes to 60 seconds.
>
> **Keyboard IME AdjustResize & Inset Fix (2026-09-09):**
> - **Enforce `adjustResize` in `AndroidManifest.xml`**:
>   - Declared `android:windowSoftInputMode="adjustResize"` on `MainActivity`, preventing OEM ROMs (e.g. Huawei/Honor EMUI/MagicOS, Xiaomi) using 3-button navigation from falling back to `adjustPan`. Eliminates the severe bug where tapping the text input panned the entire window off-screen to the top status bar.
> - **Consolidated Additive Insets in `ChatInputBar.kt`**:
>   - Replaced stacked `.navigationBarsPadding().imePadding()` with `windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))`, calculating `max(navigationBars, ime)` to ensure the input bar rests cleanly right above the soft keyboard without giant black voids or double-insets.
>
> **Screen Wakeup Lifecycle, Keyguard Overlay & Normal Mode Auto-Sleep Fix (2026-09-08):**
> - **Elimination of Lock Screen Keyguard Overlay (`AndroidManifest.xml`)**:
>   - Removed static `android:showWhenLocked="true"` and `android:turnScreenOn="true"` from `MainActivity` in `AndroidManifest.xml`. When the phone sleeps in normal mode and wakes, Android displays the standard lock screen with PIN/fingerprint instead of trapping the user in the app above the keyguard.
> - **Gated Temporary Wake Flags (`AlwaysLiveManager.kt`)**:
>   - Gated `turnScreenOnTemporarily()` in `wakeScreen()` behind `_state.value == AlwaysLiveState.FULL_SCREEN`. In normal mode, screen wake for trade alerts uses only the 10-second `WakeLock` without permanently applying `FLAG_SHOW_WHEN_LOCKED` to `MainActivity`.
> - **Window Flags Lifecycle Cleanup (`MainActivity.kt`)**:
>   - Added `clearScreenFlags()` in `onCreate()`, `onResume()`, and `onStop()` whenever in normal mode (`AlwaysLiveState.OFF`), ensuring `setShowWhenLocked(false)`, `setTurnScreenOn(false)`, and window flags are actively cleared.
> - **Screen Sleep Only in Control Mode**:
>   - Strictly enforced that the screen only stays awake when in active Control Mode (`AlwaysLiveState.FULL_SCREEN` or `MINI_FLOATING`). In normal chat mode, the screen sleeps normally based on Android system display timeout.
>
> **Voice Alert Delivery & Anticipation Alert UI (2026-09-08):**
> - **Default-Enabled Voice Alerts**:
>   - Converted `alert_voice` default from `false` to `true` across `AlertController.kt`, `JarvisAutomationService.kt`, and SQLite `AppSetting` fallback, ensuring voice alerts trigger out of the box.
> - **Anticipation Alert Card UI Refinement (`MessageBubble.kt`)**:
>   - Clean Line 2 for Confidence & Price (`ความเชื่อมั่น 76%` • `ราคา 4405.06`) and removed redundant zone strings from headers.
> - **Background Screen Wakeup & CPU WakeLock**:
>   - Automatic screen wakeup (`AlwaysLiveManager.wakeScreen()`) upon alert firing so users see and hear notifications even when locked.
>   - Temporary 30-second `PARTIAL_WAKE_LOCK` prevents CPU Doze suspension during audio synthesis and playback.
>
> **Dynamic Gemini Model Registry & Self-Healing (2026-09-07):**
> - **Zero Hardcoded Model Lock-in**: Dynamic registry (`ModelConfig.kt`) synchronized with Google's API (`ModelService.ListModels`).
> - **Self-Healing on 404 NOT_FOUND**: Instantly blacklists dead/deprecated models upon receiving HTTP 404, excises them from active fallback chains, and auto-migrates database preferences to healthy models.
>
> **Always AI Live Mode — 3D Robot Avatar, Mini Floating Overlay & Wake-on-Voice (2026-09-06):**
> - **Full-Screen Live Mode (`AlwaysLiveScreen.kt`)**: Full-screen ambient AI companion with 3D-styled animated robot avatar (`JarvisAvatar.kt`) and 36-bar circular audio visualizer ring.
> - **Mini Floating Robot Overlay (`FloatingWidgetService.kt`)**: Animated mini robot avatar (~80dp) running Jetpack Compose inside a Foreground Service with touch drag gestures, physics snap-to-edge, and quick voice toggle.
> - **Background Wake-on-Call / Hotword Engine (`HotwordDetector.kt`)**: Energy-efficient voice detection using AudioRecord with duty-cycle sampling (2s listen, 1s sleep).
> - **Central State Machine (`AlwaysLiveManager.kt`)**: Coordinates states (`OFF`, `FULL_SCREEN`, `MINI_FLOATING`, `BACKGROUND_LISTEN`), auto-recovers on screen on/off, and maps real-time speech sentiment to 10 avatar emotion states.
>
> **JARVIS Full Mobile Device Control via Voice & Accessibility Service (2026-09-06):**
> - **Hands-Free Full Mobile Automation**: Operate other apps and hardware via real-time voice commands (Gemini Live) and text chat.
> - **18 Native Tools in `📱 Device Control` Category (`DeviceToolDefinitions.kt`)**:
>   - Hardware: `device_flashlight`, `device_volume`, `device_brightness`, `device_media_control`, `device_always_live` (โหมดควบคุม).
>   - App & Navigation: `device_open_app`, `device_navigate`, `device_send_email`, `device_add_calendar`, `device_make_call`, `device_send_sms`, `device_set_alarm`, `device_open_url`, `device_search_web`.
>   - Screen & UI Automation: `device_read_screen`, `device_tap`, `device_type_text`, `device_scroll`, `device_press_button` (Back, Home, Recents, Lock Screen, Wake Screen), `device_get_app_info`.
>   - System Status: `device_battery_status`, `device_wifi_status`.
>
> **Dedicated Signal Anticipation Tool & Curated 10-Factor Confluence Engine (2026-09-05):**
> - Native tool `trading_signal_anticipation` with 10-factor whitelist (`KEYZONE_PROXIMITY`, `WICK_SWEEP_REJECTION`, `RSI_EXTREME`, `EMA_NEAR_CROSS`, `BOLLINGER_SQUEEZE`, `MACD_HISTOGRAM_TURN`, `VOLUME_ABSORPTION`, `FIBONACCI_GOLDEN_POCKET`, `STOCHASTIC_OVERSOLD_TURN`, `SESSION_OPEN_SWEEP`).
> - Dual-stage signal engine (`ANTICIPATION` → `CONFIRMED`) and closed-loop outcome evaluation (`SignalOutcomeTracker.kt`) feeding back into `StrategyConfirmationGate.kt`.

---

**JARVIS** (PersonalAIBot) คือระบบผู้ช่วย AI ส่วนบุคคลระดับสูง (Personal AI Assistant) ที่ออกแบบมาเพื่อเป็นทั้งเพื่อนคู่คิดและนักวิเคราะห์การเงินอัจฉริยะ ขับเคลื่อนด้วย **Google Gemini 3 Series (Free-Tier Optimized)** + ระบบ **Multi-Provider Fallback**, ความจำ 6 ชั้น (GraphRAG & Obsidian Wiki), ระบบเฝ้าติดตามตลาดอัตโนมัติ (Alert System V2), ระบบควบคุมเครื่องมือถือด้วยเสียง (**Device Control & Always AI Live**) และระบบ **MT5 Full Agent Control** สำหรับเทรดแบบครบวงจร

- **🔌 Multi-Provider + Fallback หลายชั้น** — Gemini (Multi API Key + Model Fallback Chain) → Groq → OpenRouter → MiniMax สลับอัตโนมัติเมื่อติด limit พร้อม Auto-Test คัดเฉพาะโมเดลที่ใช้ tool ได้จริง
- **🎙️ Live Voice + Vision** — คุยสดกับ Gemini Live (`gemini-3.1-flash-live-preview`), เลือกเสียงได้ 30 โปรไฟล์ (ผูกตัวตน/คำลงท้ายอัตโนมัติ), เปิด "ตา" ให้ AI มองผ่านกล้องพร้อม AR Overlay
- **📱 Always AI Live & Device Control** — โหมดควบคุมเครื่องเต็มรูปแบบ สั่งเปิดแอพ, นำทาง Google Maps, ปรับเสียง/ความสว่าง, พักหน้าจอ/ปลุกหน้าจอ, อ่านหน้าจอ และแตะปุ่มอัตโนมัติผ่าน Accessibility Service
- **🔔 Alert System V2 & Signal Anticipation** — แจ้งเตือนสัญญาณเทรดล่วงหน้า (Anticipation) และสัญญาณยืนยัน (Confirmed) ด้วยการ์ด 3D พร้อมระบบเสียงพูดแจ้งเตือน (Voice Alert Delivery)
- **📲 Mobile Android App (Compose Multiplatform)** — ดีไซน์พรีเมียม, แนบไฟล์/รูป/PDF ในแชทให้ AI วิเคราะห์, สร้างไฟล์ Excel จริง, Symbol Catalogue, Decision Feed และ Auto Trading Controls

---

## 🌟 ระบบหลัก (Core Systems)

### 🎙️ 1. Live Voice & Voice Profile ↔ Identity
- **Gemini Live API** — สนทนาสดด้วยเสียง (PCM 16kHz) latency ต่ำมาก (~835ms) ด้วยโมเดลหลัก `gemini-3.1-flash-live-preview` รองรับการขัดจังหวะ (barge-in) และ flush คิวเสียงอัตโนมัติ
- **30 Voice Profiles** — เสียงหญิง/ชาย หลายน้ำเสียงและอารมณ์ เลือกได้จาก Settings หรือสั่งด้วยเสียง ("เปลี่ยนเสียงเป็น Leda") — เปลี่ยนแล้ว AI ทักทายยืนยันด้วยเสียงใหม่ทันที
- **เสียงผูกกับตัวตน** — เสียงหญิงพูดลงท้าย "ค่ะ" / เสียงชาย "ครับ" อัตโนมัติ จำข้าม session ผ่าน Core Memory
- **Vision (ตาของ JARVIS)** — เปิดกล้องให้ AI มองโลกจริง พูดสรุปสิ่งที่เห็นทันทีโดยไม่ต้องถามซ้ำ, OCR อ่านข้อความ, Object Detection (AR Overlay), สลับ vision provider ตามโมเดลหลักอัตโนมัติ
- **Live Tool Bridge** — โมเดล Live สั่ง tool ผ่าน native function calling ตรง และประสานงานกับเครื่องมือภายนอกได้อย่างแม่นยำ

### 📱 2. Always AI Live Mode & Device Control (โหมดควบคุม)
- **Full-Screen Live Mode (`AlwaysLiveScreen.kt`)** — อวตารหุ่นยนต์ 3D เคลื่อนไหวตามอารมณ์ 10 สถานะ พร้อมวงแหวน Audio Visualizer 36 แท่ง สลับกล้องหน้า/หลัง และย่อเป็นมินิวิจเจ็ตได้
- **Mini Floating Robot Overlay (`FloatingWidgetService.kt`)** — อวตารมินิ (~80dp) ลอยบนหน้าจอทุกแอป แตะลากย้ายตำแหน่งอิสระ ดูดติดขอบจอ และแตะสองครั้งเพื่อขยายเต็มจอ
- **Hands-Free Full Mobile Automation** — รองรับคำสั่งเสียงควบคุมฮาร์ดแวร์ ปรับเสียง, ไฟฉาย, ความสว่าง, มีเดีย, ล็อกหน้าจอ, ปลุกหน้าจอ, เปิดแอป และนำทาง Google Maps
- **UI Automation (Android Accessibility Service)** — อ่านโครงสร้างหน้าจอ (`device_read_screen`), แตะปุ่ม (`device_tap`), พิมพ์ข้อความ (`device_type_text`) และเลื่อนหน้าจอ (`device_scroll`)

### 🧠 3. Advanced 6-Layer Memory Engine
- **Layer 1: Core Memory** — จำข้อมูลตัวตนผู้ใช้/AI (identity จัดการผ่าน tool/Settings เท่านั้น กัน heuristic ทับ)
- **Layer 2: Working Memory** — บันทึกประวัติการคุยลง SQLite ทันที (Context Tracking)
- **Layer 3: Archival Memory** — Semantic Search / Vector Embeddings (Local ONNX หรือ Gemini Cloud — auto-backfill)
- **Layer 4: GraphRAG Knowledge Graph** — โครงข่ายความสัมพันธ์แนวคิด + retrieval จริงผ่าน `recall_memory`
- **Layer 5: Memory Consolidation** — "Sleep Cycle" สรุปและย้ายความจำระยะสั้นไประยะยาว (auto-trigger เมื่อแชทสะสม 200 ข้อความ)
- **Layer 6: LLM-Wiki (Obsidian)** — ระบบ "สมองส่วนนอก" ที่ AI และมนุษย์จัดการร่วมกันผ่าน Markdown

### 📊 4. Trading Intelligence บนมือถือ (TV-Powered & Unified SMC)
- **Dual-Stage Signal Engine (Anticipation → Confirmed)** — ตรวจจับการตั้งเค้าของราคาล่วงหน้า (Anticipation) ด้วย 10 ปัจจัยเทคนิคอล และส่งสัญญาณยืนยัน (Confirmed) เมื่อแท่งเทียนปิด
- **Indicator Alert Provider** — คำนวณ EMA20/50/200, EMA 14/60 Near-Cross & Golden/Death Cross, RSI14, MACD, Stoch, CCI, Bollinger Bands, ATR เองจากแท่งเทียน cache แม่นกว่า TradingView scanner
- **SMC Alert Provider** — ตรวจสอบโครงสร้างตลาด 5 มิติ: Premium/Discount zones, BOS/CHoCH, Order Blocks, FVG และ Liquidity Sweeps
- **Deep Analysis Suite ครบ 5 มิติ** — LSD state + confluence, Orderflow Delta, Fibo Score, Momentum, Squeeze (9 fields) พร้อม TV local fallback
- **Multi-Timeframe ทุก tool** — ระบุ TF ได้ด้วย suffix `symbol@TF` เช่น `XAUUSD@15m` (1m/5m/15m/30m/1h/4h/1D)
- **Chart Dashboard (Lightweight Charts v5.1, offline)** — กราฟ multi-pane ในตัวแอป, overlays EMA/SMA/DC/BB ทุกช่วงพีเรียด, แสดงโซน SMC และจุดสัญญาณ SIG ย้อนหลัง

### 🔔 5. Alert System V2 (ระบบเฝ้าติดตามตลาด)
- **Actionable Notifications** — แจ้งเตือนพร้อมปุ่ม **"🛑 หยุดแจ้งเตือน" / "🔁 แจ้งเตือนซ้ำ"** (manifest receiver ทำงานได้แม้แอปถูกฆ่า)
- **Voice Alert Delivery** — เปิดระบบเสียงพูดแจ้งเตือนเป็นค่าเริ่มต้น พร้อมปลุกหน้าจอขึ้นมาแจ้งเตือนอัตโนมัติแม้ปิดหน้าจออยู่
- **Adaptive Interval** — tick หลัก 30 วินาที + เร่งเช็คอัตโนมัติเมื่อราคาใกล้เป้า (<0.1% → 30 วิ, <0.5% → 1 นาที)
- **AlertFieldCatalog** — dropdown ตอนสร้าง alert เลือกได้เฉพาะ tool/field ที่ดึงค่าได้จริง ป้องกันการตั้งเงื่อนไขผิดพลาด
- **14+ Preset ลัด** — ราคาถึงเป้า, RSI Overbought/Oversold, Golden/Death Cross, Discount/Premium Zone, Bollinger Squeeze, EMA 14/60 Convergence ฯลฯ

### 🔌 6. Provider System — Multi-Key, Fallback & Auto-Test
- **Providers ปัจจุบัน**: Gemini / OpenRouter / Groq / MiniMax
- **Gemini Multi API Key** — เพิ่ม/แก้/ลบ key ได้หลายอัน หมุนเวียนอัตโนมัติเมื่อติด Limit 429/503
- **Dynamic Model Fallback Chain** — ดึงโมเดลจริงจาก API Google เรียงลำดับตามความเร็ว Flash models และคัดกรองโมเดล 404 ออกอัตโนมัติ
- **Cross-Provider Fallback** — สลับข้ามค่ายไป Groq / OpenRouter ทันทีเมื่อ Gemini ติดโควต้า
- **Model Auto-Tester** — ทดสอบ chat + tool calling ทุกโมเดลอัตโนมัติ

### 🤖 7. Custom Tools — AI สร้าง/แก้/ลบเครื่องมือตัวเอง
- **CRUD ครบวงจรผ่าน AI** — `system_create_agent_tool` (สร้าง/แก้ไข), `system_list_agent_tools` (ดูรายการ), `system_delete_agent_tool` (ลบ)
- **Persist ข้าม session** — บันทึกลง `custom_agent_tools/*.json` โหลดกลับอัตโนมัติตอนเปิดแอป ใช้ได้ทั้งโหมด chat และ live
- **Template Argument Interpolation & Formula Mode** — คำนวณสูตรคณิตศาสตร์และแทนที่ค่าตัวแปรแม่นยำ

### 📁 8. File Tools & Attachments
- **แนบไฟล์ในแชท** — ปุ่ม 📎 แนบได้สูงสุด 5 ไฟล์ (รูป/PDF/DOCX ส่ง inline ให้ Gemini วิเคราะห์; ไฟล์ text ฝังเข้า prompt)
- **file_write รองรับ .xlsx จริง** — เขียน Excel ด้วย zip+XML ในตัว ไม่ต้องพึ่งไลบรารีภายนอก
- **Security Guard** — ป้องกัน path traversal, ควบคุมนามสกุลไฟล์ที่อนุญาต และจำกัดการอ่านไม่เกิน 200K ตัวอักษร

### 🏥 9. JARVIS Diagnostic Engine (Self-Healing)
- **Autonomous Health Verification** — ตรวจสอบการเชื่อมต่อ API, ความถูกต้องของข้อมูล และความสมบูรณ์ของฐานข้อมูลอัตโนมัติ
- **Price Source Sync** — เปรียบเทียบราคาจากหลายแหล่ง (Yahoo, OANDA, TV) เพื่อตรวจสอบ Delay
- **Log Hygiene** — ปิดบัง API key (masking) ใน logcat ป้องกันข้อมูลรั่วไหล

---

## 📊 10. mt5-core-server — Trading Intelligence Engine (Node.js)

> **Status: Production = V26.26 (Modular Codebase & Verification Pass 2026-05-23)**  
> **Runtime ปัจจุบัน: `engineMode: M15_WALL_SCALPING` — decision path ลดรูปเหลือ IndicatorPipeline + PriceMap wall detection**

- **M15 Wall Scalping Simplification** — กลยุทธ์เริ่มต้นเฉพาะ `SCALPING`; wall registry คง identity ข้าม cycle; M15/M30/H1/H4 เป็น anchor, M1/M5 เป็น confirmation
- **V26.26 Modular Codebase** — แยก helpers จาก `autoTradingService.ts` เป็นโมดูลย่อย `decisionLogger.ts` + `strategySelector.ts`
- **Context Levels & Pine Script V8.5** — Daily Pivot Points, Session VWAP, Auto-Fibonacci และ Pine Script สำหรับดู Wall Map บน TradingView
- **Analytics Scripts** — คำสั่งวิเคราะห์ประสิทธิภาพการเทรด:
  ```bash
  # แบบที่ 1: วิเคราะห์แบบระบุวันที่เฉพาะเจาะจง (เช่น วันนี้)
  npm run analyze 2026-09-09

  # แบบที่ 2: วิเคราะห์ภาพรวมทั้งหมด (All Time)
  npm run analyze
  ```

### 🔗 MT5 Full Agent Control (Broker-First)

> **Status: 🟢 Stable (21 MT5 Tools)** — Mobile (KMP) → mt5-core-server (Node.js:8090) → Python Bridge → MetaTrader5 API

- **Broker-First Data** — ราคา, volume, positions, OHLCV จาก MT5 Broker โดยตรง
- **Full Account Control** — equity, balance, margin, P&L, symbols, positions, orders, history, batch close/break-even
- **Advanced Intelligence Suite** — Market Scanner, Correlation Radar, Sentiment Gauge, Institutional Flow, Economic Radar, Trade Journal (AI scoring)
- **Audit Trail** — บันทึกทุก trade action ที่ AI สั่ง พร้อมคะแนนคุณภาพ

---

## 🛠️ Prerequisites (ข้อกำหนดพื้นฐาน)

- **Java JDK 21** (มีมาพร้อม Android Studio ในโฟลเดอร์ `jbr`)
- **Android Studio** เวอร์ชันล่าสุด
- **Node.js** (v18+) — สำหรับรัน `mt5-core-server`
- **Python 3.10+** — สำหรับ `mt5_bridge.py`

---

## 🛠️ Tech Stack (v2026)

| Layer | Technology | Status |
|-------|-----------|--------|
| Language | Kotlin 2.0 (KMP) | Stable |
| UI Framework | Jetpack Compose Multiplatform | Stable |
| Primary Brain | Gemini 3 Series (Free-Tier Optimized, Multi-Key) | Active |
| Primary Live Model | `gemini-3.1-flash-live-preview` (Native Audio, Low Latency) | Active |
| AI Providers | Gemini / OpenRouter / Groq / MiniMax (+ cross-provider fallback) | Active |
| Multimodal | Live Stream (PCM 16kHz + JPEG) | Active |
| Database | SQLDelight + SQLite Persistence | Active |
| Embedding (Mobile) | LocalOnnx (paraphrase-multilingual-MiniLM-L12-v2, ~117MB offline) / Gemini Cloud cascade | Active |
| Background Service | Android Foreground (DataSync) + Accessibility Service + Manifest Alert Receivers | Active |
| Logic Controller | JarvisOrchestrator (Multi-Provider + Fallback) | Active |
| MT5 Core Server | Node.js + Express (port 8090) — M15_WALL_SCALPING | Active |
| MT5 Python Bridge | Python + MetaTrader5 API | Active |

---

## 📦 Tool Catalogue (Total: 100+ Tools)

### 📱 DEVICE CONTROL TOOLS (18 tools)
- `device_flashlight`: ควบคุมไฟฉาย (ON, OFF, TOGGLE)
- `device_volume`: ปรับระดับเสียง (UP, DOWN, MUTE, UNMUTE, SET %, STATUS) ทุกสตรีม
- `device_brightness`: ปรับความสว่างหน้าจอ (SET %, AUTO)
- `device_media_control`: ควบคุมการเล่นเพลง/สื่อ (PLAY, PAUSE, NEXT, PREV, STOP)
- `device_always_live`: สั่งเปิด/ปิดโหมด Always AI Live (โหมดควบคุม)
- `device_open_app`: เปิดแอปพลิเคชันใดๆ ในเครื่องด้วยชื่อภาษาไทยหรืออังกฤษ
- `device_navigate`: เปิด Google Maps ดูสถานที่หรือเริ่มนำทาง Turn-by-turn
- `device_send_email`: เปิดหน้าต่างเขียนอีเมลพร้อมผู้รับ หัวข้อ และเนื้อหา
- `device_add_calendar`: บันทึกนัดหมายลง Google Calendar
- `device_make_call` & `device_send_sms`: โทรออกและส่งข้อความ SMS
- `device_set_alarm`: ตั้งนาฬิกาปลุกในระบบ Android
- `device_open_url` & `device_search_web`: เปิดเว็บเบราว์เซอร์หรือค้นหา Google
- `device_read_screen`: อ่านข้อมูลหน้าจอปัจจุบันผ่าน Accessibility Service
- `device_tap`: แตะปุ่มหรือพิกัดบนหน้าจอ
- `device_type_text`: พิมพ์ข้อความลงในช่องที่โฟกัสอยู่
- `device_scroll`: เลื่อนหน้าจอขึ้นหรือลง
- `device_press_button`: สั่งปุ่มระบบ (Back, Home, Recents, Notifications, Screenshot, Lock Screen, Wake Screen)
- `device_get_app_info`: ตรวจสอบชื่อแอปและหน้าต่างที่กำลังเปิดใช้งานอยู่
- `device_battery_status` & `device_wifi_status`: ตรวจสอบสถานะแบตเตอรี่และเครือข่าย WiFi

### 🧠 BUILT-IN & SYSTEM TOOLS (21 tools)
- `calculate`: คำนวณนิพจน์คณิตศาสตร์
- `get_current_datetime`: ข้อมูลวันเวลาและปฏิทินไทย (วันในสัปดาห์, เดือนไทย, ปี พ.ศ., เวลา Asia/Bangkok)
- `remember_fact`: บันทึกข้อมูลลงความจำระยะยาว
- `recall_memory`: Semantic search จากฐานความรู้
- `convert_units`: แปลงหน่วยสากลและหน่วยไทย (ไร่, งาน, ตารางวา)
- `set_reminder`: ตั้งการเตือน/TODO
- `format_json`: จัดรูปแบบ JSON
- `translate_text`: แปลภาษา
- `summarize_text`: สรุปข้อความยาว
- `search_web`: ค้นหาเว็บ
- `identity_update`: AI ปรับแต่งตัวตน/ข้อมูลผู้ใช้เมื่อถูกสั่ง
- `analyze_and_display_report`: ส่งรายงานยาวลงแชทแล้วพูดสรุป
- `chart_dashboard_control`: ควบคุมหน้ากราฟ เปิด/ปิดกราฟ, ปรับ layout และ indicators
- `system_run_diagnostics`: ตรวจสุขภาพระบบ (Self-healing)
- `system_check_connectivity`: ตรวจการเชื่อมต่อ API
- `system_create_agent_tool`: AI สร้าง/แก้ไข tool เอง (persist ข้าม session)
- `system_list_agent_tools`: ดูรายการ custom tools
- `system_delete_agent_tool`: ลบ custom tool
- `voice_summary`: สรุปเฉพาะส่วนที่พูดในโหมดเสียง
- `mt5_place_order` / `mt5_close_position`: alias ส่ง/ปิดออเดอร์ MT5

### 📊 TRADING TOOLS (29 tools)
- `trading_signal_anticipation`: ⚡ คาดการณ์สัญญาณเทรดล่วงหน้าด้วย 10 ปัจจัยคอนฟลูเอนซ์ (Keyzone Proximity, Wick Sweep, RSI Extreme, EMA Near-Cross ฯลฯ)
- `trading_price`: ราคา Real-time (Stocks/Crypto/Forex/Gold — TV primary + fallback)
- `trading_market_snapshot`: ภาพรวมตลาดตามกลุ่มอุตสาหกรรม
- `trading_top_gainers` / `trading_top_losers`: หุ้น/สินทรัพย์ที่พุ่ง/ดิ่งแรงสุด
- `trading_technical_analysis`: TA (RSI, MACD, BB, EMA) รองรับทุกช่วงเวลา @TF
- `trading_multi_timeframe`: ความสอดคล้องทุก TF (W → 15m)
- `trading_bollinger_scan` / `trading_oversold_scan` / `trading_overbought_scan` / `trading_volume_breakout`: Scanners
- `trading_sentiment`: อารมณ์ตลาด
- `trading_news`: ข่าวการเงิน 6 แหล่ง
- `trading_combined`: TA + News + Sentiment
- `trading_fundamental_analysis`: ปัจจัยพื้นฐาน
- `trading_fear_greed`: 🌡️ Crypto Fear & Greed Index
- `trading_macro_calendar`: 📅 ปฏิทินเศรษฐกิจ ForexFactory แปลงเวลาไทยแม่นยำ
- `trading_economic_data`: 🇺🇸 ข้อมูลเศรษฐกิจสหรัฐฯ จาก FRED
- `trading_correlation_matrix`: Correlation ระหว่างสินทรัพย์
- `trading_position_sizing`: คำนวณขนาดไม้ตามความเสี่ยง
- `trading_crypto_overview`: ข้อมูลตลาด Crypto จาก CoinGecko
- `trading_deep_analysis_suite`: วิเคราะห์ 5 มิติ (LSD, Orderflow, Fibo, Momentum, Squeeze)
- `trading_harmonic_scan`: Harmonic Patterns
- `trading_elliot_modern_analysis`: Elliott Wave แบบ Modern
- `trading_strategy_signal`: สัญญาณจากกลยุทธ์ Quantpedia คำนวณในเครื่อง
- `trading_signal_stats`: สถิติผลการเทรดย้อนหลังและผลจริง (Win rate / R-multiple)
- `automation_manage_alerts`: จัดการแจ้งเตือนเงื่อนไขราคาและสัญญาณเทรด
- `automation_manage_schedule`: จัดการงานอัตโนมัติตามเวลา

### 🔗 MT5 BRIDGE TOOLS (21 tools)
- **Core Actions:** `trading_mt5_order`, `trading_mt5_close_position`, `trading_mt5_modify_position`
- **Core Agent:** `trading_mt5_account_info`, `trading_mt5_list_positions`, `trading_mt5_list_orders`, `trading_mt5_list_history`, `trading_mt5_candles`, `trading_mt5_symbol_info`, `trading_mt5_symbol_search`, `trading_mt5_analyze`, `trading_mt5_close_all`, `trading_mt5_break_even_all`, `trading_mt5_snapshot`, `trading_mt5_trade_actions`
- **Advanced Intelligence:** `trading_mt5_market_scanner`, `trading_mt5_correlation_radar`, `trading_mt5_sentiment_gauge`, `trading_mt5_institutional_flow`, `trading_mt5_economic_radar`, `trading_mt5_trade_journal`

### 📈 SMC TOOLS (5 tools)
- `trading_smc_analysis`: Full SMC Dashboard
- `trading_smc_sweeps`: ตรวจจับการกวาดสภาพคล่อง (MTF Liquidity Sweeps)
- `trading_smc_liquidity`: โซนสภาพคล่อง MTF พร้อมดาวระดับความสำคัญ
- `trading_smc_orderblocks`: ตรวจจับ Order Blocks และ Fair Value Gaps (FVG)
- `trading_smc_structure`: ตรวจสอบ Market Structure (BOS / CHoCH, Premium/Discount)

### 📚 STRATEGY LIBRARY (3 tools)
- `strategy_list` / `strategy_search` / `strategy_explain`: คลังกลยุทธ์ Quantpedia 60 แบบ พร้อมคำอธิบายและแนวคิด

### 📁 FILE MANAGEMENT (7 tools)
- `file_list`, `file_read`, `file_write` (รองรับ **.xlsx จริง**), `file_delete`, `file_analyze` (OCR/PDF), `file_move`, `file_search`

### 📷 CAMERA, VISION & VOICE (7 tools)
- `vision_activate` / `vision_deactivate`: เปิด/ปิดตา AI
- `camera_analyze_scene`: วิเคราะห์ภาพจากกล้อง
- `camera_detect_objects`: ตรวจจับวัตถุ (AR Overlay)
- `camera_read_text`: สแกนอ่านตัวหนังสือ (OCR)
- `camera_switch_provider` / `camera_switch_mode`: สลับผู้ให้บริการกล้องและโหมดการทำงาน
- `voice_get_profiles` & `voice_set_profile`: จัดการและเปลี่ยนโปรไฟล์เสียง AI (30 เสียง)

---

## 🚀 Roadmap

1. **Rich Chat Rendering** — แสดงตาราง / รูป / กราฟ / infographic ในแชท
2. **Multi-Agent Orchestration (Swarm)** — กระจายงานให้ AI Agent เฉพาะทางทำงานร่วมกัน
3. **Portfolio Hub** — ติดตามพอร์ตการลงทุนและวิเคราะห์ P&L แบบละเอียด
4. **Strategy Backtester & Evolution** — พัฒนาและค้นหาพารามิเตอร์กลยุทธ์ด้วยระบบพันธุกรรม (Genetic Evolution)
5. **Hardware Extension** — เชื่อมต่อ Smart Home / อุปกรณ์สวมใส่ (Wearables)

---

## 📚 เอกสารอ้างอิง (Obsidian Wiki)

- `.obsidian-wiki/00_System/index.md` — สารบัญใหญ่ของระบบ
- `.obsidian-wiki/00_System/log.md` — บันทึกการพัฒนาทั้งหมด
- `.obsidian-wiki/01_Architecture/` — สถาปัตยกรรมระบบมือถือและ AI
- `.obsidian-wiki/02_Components/Always_AI_Live_Mode.md` — เอกสารระบบโหมดควบคุมและอวตาร 3D
- `.obsidian-wiki/04_Tasks/` — บันทึก Changelog และแผนงานรายวัน
- `.obsidian-wiki/07_Trading_Intelligence/` — รายละเอียดระบบการเทรด mt5-core-server และ Unified SMC

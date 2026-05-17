# 📱 แผนปรับปรุง Mobile App (PersonalAIBot)

**วันที่จัดทำ:** 2026-04-30
**ผู้สั่ง:** skyliner.jojo@gmail.com
**Scope:** composeApp (Kotlin Multiplatform) — Settings + MT5 (Overview / Trade / Auto / History)
**สถานะ:** 📋 Approved — รอเริ่ม Phase 1

---

## 🎯 Decisions ที่ตอบมาแล้ว

| # | คำถาม | คำตอบ |
|---|---|---|
| 1 | Chart library | ใช้ `compose-multiplatform-charts` หรือ Canvas เอง — เลือกตัวที่สะดวก/สวย |
| 2 | Test Connection | Ping endpoint จริงเพื่อยืนยันว่า key ใช้งานได้ |
| 3 | ลำดับ Phase | ทำตาม **P1 → P5** (เพิ่ม **P6** ใหม่ — Persistence) |
| 4 | Watchlist seeding | **เช็ค symbol จาก broker ก่อน** → pre-fill ทองคำ 1 ตัว + จดจำการเพิ่ม symbol ของ broker นั้น ๆ |
| 5 | **Persistence (NEW)** | ข้อมูล MT5 ต้อง **คงค้างไม่หาย** เมื่อ server ปิด / offline — เห็นข้อมูลล่าสุดได้เสมอ |

---

## 🆕 Phase 6 (เพิ่มจาก Decision #5) — Local Persistence Layer

**ปัญหาเดิม:** ทุก ๆ ครั้งที่เปิด app, MT5 fetch fresh 100% — server ปิด = หน้าจอว่างเปล่า

**เป้าหมาย:** ข้อมูล MT5 ทั้งหมดถูก cache ลง local storage → เปิด app แล้วเห็นข้อมูลล่าสุดทันที (พร้อม timestamp "Last sync") แม้ server offline

### การออกแบบ

| Component | คำอธิบาย |
|---|---|
| `Mt5LocalCache.kt` | service ใหม่ ห่อหุ้ม DataStore + JSON serialization |
| Cache keys | `account_snapshot`, `positions`, `history_recent_500`, `symbols_<broker_id>`, `daily_stats_<date>`, `equity_curve_<period>` |
| Save policy | ทุกครั้งหลัง fetch สำเร็จ → save พร้อม `lastSyncMs` |
| Load policy | เปิด screen → load cache ทันที (instant render) → trigger refresh ใน background |
| Offline indicator | banner "Last synced: N นาทีก่อน" บนทุก MT5 screen เมื่อ `now - lastSyncMs > 30s` |
| Stale data warning | banner สีส้ม "Server offline — แสดงข้อมูล cached" เมื่อ fetch ล้มเหลว แต่มี cache อยู่ |

### ผลลัพธ์ที่ได้

```
[เปิด app, server ปิด]
↓
Mt5LocalCache.load()  ← instant
↓
Render Overview/Trade/Auto/History จาก cache
↓
Banner: "🟠 Server offline — Last synced: 2 ชม.ที่แล้ว"
↓
Background retry connect ทุก 30s
↓
[server กลับมา] → fetch fresh + save → banner หาย
```

---

## 🔵 Phase 1 — Settings Page (UX + Validation)

**ขอบเขต:** `SettingsDialog.kt` (435 บรรทัด)

### งานที่จะทำ

| # | หัวข้อ | รายละเอียด |
|---|---|---|
| 1.1 | Re-organize เป็น Collapsible Sections | API Keys / Models / Live / Local AI / Widget / Permissions |
| 1.2 | Per-provider API key card | 4 cards (Gemini/OpenAI/Claude/OpenRouter) — แต่ละ card มี logo, prefix hint, status badge |
| 1.3 | Test Connection ปุ่ม | ปุ่ม **"Test"** ที่แต่ละ key — ping endpoint จริง: ✓ green / ✗ red + error message |
| 1.4 | API key validation inline | ตรวจ prefix (`sk-`, `sk-ant-`, `sk-or-`, `AIza`) + length minimum |
| 1.5 | Live Capability filter | ใหม่: ดึง `getLiveCapableModels(provider)` แทน keyword filter — รองรับ vision/audio/realtime |
| 1.6 | Free Only checkbox always visible | enabled เฉพาะตอน provider = openrouter |
| 1.7 | Local Model card detail | progress + size + version + ปุ่ม Re-download/Delete |
| 1.8 | Font scaling refit | title 16sp / label 14sp / body 14sp / helper 11sp |

### Service ที่ต้องเพิ่ม

- `ApiKeyTester.kt` — pure function `suspend fun test(provider, key): TestResult`
- `JarvisViewModel.testApiKey(provider)` → return `TestResult(ok, latencyMs, error?)`
- `JarvisViewModel.getLiveCapableModels(provider)` → return `List<LlmModelInfo>` ที่มี modality≥multimodal

---

## 🟢 Phase 2 — MT5 Overview (Premium Stats)

**ขอบเขต:** Tab Overview ใน `TradingTerminalScreen.kt`

### งานที่จะทำ

| # | หัวข้อ | รายละเอียด |
|---|---|---|
| 2.1 | Hero Section | Balance ใหญ่ + Δ PnL วันนี้ (ลูกศรขึ้น/ลงพร้อมสี) |
| 2.2 | Equity Curve Sparkline | mini chart 7 วันล่าสุด — Canvas เอง |
| 2.3 | Period Selector | Toggle: **Today / 7D / 30D / All** |
| 2.4 | Stats Cards (5 ตัว) | Net PnL, Win Rate %, Profit Factor, Avg R:R, Max Drawdown |
| 2.5 | Margin Level Gauge | semi-circle gauge แทน progress bar — Red <100% / Amber <200% / Green >300% |
| 2.6 | Equity Curve (Big) | Line chart full-width — period ตาม selector |
| 2.7 | Open Positions Table | เพิ่มคอลัมน์: Entry, Current, SL dist %, TP dist %, R-value |

### Service ที่ต้องเพิ่ม

- `TradeStatsCalculator.kt`:
    - `calculate(trades: List<TradeRecord>, period: Period): TradeStats`
    - return: `winRate, profitFactor, avgWin, avgLoss, maxDD, netPnl, totalTrades, avgRr`
- `EquityCurveBuilder.kt`: รับ trade history → return `List<EquityPoint(timeMs, equity)>`

---

## 🟡 Phase 3 — Trade Tab (Broker-Sourced Symbols)

**ขอบเขต:** Tab Trade ใน `TradingTerminalScreen.kt` (บรรทัด 815-1044)

### งานที่จะทำ

| # | หัวข้อ | รายละเอียด |
|---|---|---|
| 3.1 | ลบ QuickSymbols hardcoded | ดึงจาก `Mt5TerminalService.fetchAvailableSymbols()` (มีอยู่แล้ว line 290-311) |
| 3.2 | BrokerSymbolCache | service ใหม่ TTL 5 นาที + persist ลง DataStore (Phase 6) |
| 3.3 | Default symbol dynamic | ใช้ตัวที่ user เคยเทรดล่าสุด หรือ "Gold" ตัวแรก (ชื่อขึ้นกับ broker: XAUUSD/GOLD/XAU/GOLDmicro ฯลฯ) |
| 3.4 | Symbol Picker Dialog | search box + grouping: Forex / Metals / Crypto / Indices / Stocks |
| 3.5 | Favorite chip row | ⭐ icon ที่ symbol → save list — chip row สูงสุด 8 ตัวบนสุด |
| 3.6 | Compact UI | field 56dp → 48dp, icon button ลด, padding ปรับ |

### Service ที่ต้องเพิ่ม

- `BrokerSymbolCache.kt` — singleton + TTL + persistence
- `SymbolCategoryClassifier.kt` — heuristic แยกหมวด (Forex = `[A-Z]{6}` / Crypto = มี BTC/ETH / Metals = มี XAU/XAG / Indices = SPX/NAS/US30)

---

## 🟠 Phase 4 — Auto Tab (Broker Watchlist + Streamline)

**ขอบเขต:** `AutoTradingScreen.kt`

### งานที่จะทำ

| # | หัวข้อ | รายละเอียด |
|---|---|---|
| 4.1 | Watchlist Multi-Select Dialog | จาก broker symbol list (ไม่ใช่ text input) |
| 4.2 | First-Time Seeding | เปิด app ครั้งแรก: ดึง symbols จาก broker → หา "Gold" ตัวที่มีอยู่จริง (XAUUSD / GOLD / XAU / XAUUSDm / etc.) → pre-fill ตัวเดียว |
| 4.3 | Symbol Memory | จดจำ symbols ที่ user เพิ่ม → เก็บ broker_id + symbol_name → เปลี่ยน broker ก็ memory แยก |
| 4.4 | ลบ Blacklist | รวมเป็น Watchlist exclusion ใน UI เดียว |
| 4.5 | Settings 3-Tier Disclosure | **Basic** (6 ค่าหลัก) / **Advanced** (collapsible) / **Expert** (collapsible + warning) |
| 4.6 | Validation | ตรวจว่า symbol ที่เลือกมีจริงใน broker — block ถ้าไม่มี |

### Basic Settings (6 ค่าที่เห็นเสมอ)

```
1. Max Positions per Symbol      [5]
2. Risk per Trade %              [1.0]
3. Min RRR                       [1.5]
4. Max Daily Loss %              [3.0]
5. AI Mode                       (Multi-Agent / Single / Off)
6. Auto-Resume on Restart        [On]
```

### Service ที่ต้องเพิ่ม

- `BrokerWatchlistMemory.kt` — `Map<broker_id, List<symbol>>` ใน DataStore
- `GoldSymbolDetector.kt` — heuristic หา "Gold" จาก symbol list: priority `XAUUSD > XAU/USD > GOLD > XAUUSDm > XAUUSD.r`

---

## 🟣 Phase 5 — History Tab (Period Filter + Extended Stats)

**ขอบเขต:** Tab History ใน `TradingTerminalScreen.kt` (บรรทัด 1250-1349)

### งานที่จะทำ

| # | หัวข้อ | รายละเอียด |
|---|---|---|
| 5.1 | Date Range bar | **Today / Yesterday / 7D / 30D / 90D / Custom** |
| 5.2 | Side filter (เดิม) | คงไว้: All / Buy / Sell / Winners / Losers — ทำงานร่วมกับ Date filter |
| 5.3 | Stats panel (ขยาย) | Win Rate %, Profit Factor, Avg Win, Avg Loss, Best/Worst Trade, Avg Duration, Total Volume |
| 5.4 | Group by Date | collapsible section ตามวัน + day total PnL |
| 5.5 | Persistence | ใช้ `Mt5LocalCache` เก็บ history 500 deals ล่าสุด |

### Reuse จาก Phase 2

- `TradeStatsCalculator.calculate(filtered, period)` ตัวเดียวกัน → ลดโค้ดซ้ำ

---

## 🔧 Cross-cutting (ทุก Phase ใช้ร่วม)

### Service Layer ใหม่

| File | หน้าที่ |
|---|---|
| `Mt5LocalCache.kt` | Persistence wrapper รอบ DataStore — JSON snapshot ของ MT5 data |
| `BrokerSymbolCache.kt` | Symbols จาก broker + TTL + memory ต่อ broker |
| `BrokerWatchlistMemory.kt` | Watchlist persistence per-broker |
| `TradeStatsCalculator.kt` | Pure stats (Win Rate, PF, Avg R, Max DD, ฯลฯ) |
| `EquityCurveBuilder.kt` | สร้าง equity points จาก trade history |
| `GoldSymbolDetector.kt` | Detect Gold symbol ตาม broker naming |
| `SymbolCategoryClassifier.kt` | จำแนก Forex/Metal/Crypto/Index |
| `ApiKeyTester.kt` | Validate + ping API key |

### UI Tokens (ใช้ทั้ง app)

| Element | ปัจจุบัน | ใหม่ |
|---|---|---|
| Title | 14sp | **16sp** |
| Body | 12sp | **14sp** |
| Helper | 10sp | **11sp** |
| Field height | 56dp | **48dp** |
| Card padding | 12dp | **16dp** |
| Card spacing | 8dp | **12dp** |
| Touch target | unset | **min 48dp** (Material 3) |

---

## ⏱ ลำดับและเวลา (โดยประมาณ)

```
P1 Settings        ──┐
                     ├─ ทำคู่ขนานได้ (ไม่กระทบกัน)
P6 Persistence     ──┘  ← วาง infrastructure
                            │
                            ▼
P3 Trade tab          ← ใช้ BrokerSymbolCache
                            │
                            ▼
P4 Auto tab           ← ใช้ Watchlist + Symbol memory
                            │
                            ▼
P2 Overview Premium   ← ใช้ Stats + Equity curve
                            │
                            ▼
P5 History            ← reuse Stats calculator
```

**แนะนำเริ่ม:** P1 + P6 พร้อมกัน (P6 = infrastructure ที่ทุก Phase อาศัย)

---

## ✅ Definition of Done (ของแต่ละ Phase)

- ✅ Functionality: ตรงตาม spec
- ✅ Persistence: ข้อมูลคงอยู่หลัง app restart + server offline
- ✅ Empty state: แสดง message สวยงามเมื่อไม่มีข้อมูล (ครั้งแรก)
- ✅ Loading state: skeleton / shimmer ระหว่าง fetch
- ✅ Error state: banner + retry button
- ✅ Build pass: TypeScript check ผ่าน + KMP build ทุก target
- ✅ Manual smoke test: เปิด/ปิด server ทดสอบทุก screen

---

## 📚 ไฟล์ที่จะเปลี่ยน (รายชื่อโดยประมาณ)

### Phase 1 (Settings)
- `composeApp/.../ui/screen/SettingsDialog.kt` (refactor major)
- `composeApp/.../JarvisViewModel.kt` (เพิ่ม `testApiKey`, `getLiveCapableModels`)
- `composeApp/.../data/ApiKeyTester.kt` (ใหม่)

### Phase 6 (Persistence — ทำคู่ P1)
- `composeApp/.../data/Mt5LocalCache.kt` (ใหม่)
- `composeApp/.../tools/trading/Mt5TerminalService.kt` (เพิ่ม cache layer)
- `composeApp/.../tools/trading/auto/AutoTradingViewModel.kt` (load cache ก่อน fetch)

### Phase 3 (Trade)
- `composeApp/.../ui/screen/TradingTerminalScreen.kt` (Trade tab section)
- `composeApp/.../data/BrokerSymbolCache.kt` (ใหม่)
- `composeApp/.../data/SymbolCategoryClassifier.kt` (ใหม่)
- `composeApp/.../ui/screen/SymbolPickerDialog.kt` (ใหม่)

### Phase 4 (Auto)
- `composeApp/.../ui/screen/AutoTradingScreen.kt`
- `composeApp/.../data/BrokerWatchlistMemory.kt` (ใหม่)
- `composeApp/.../data/GoldSymbolDetector.kt` (ใหม่)

### Phase 2 (Overview)
- `composeApp/.../ui/screen/TradingTerminalScreen.kt` (Overview tab section)
- `composeApp/.../data/TradeStatsCalculator.kt` (ใหม่)
- `composeApp/.../data/EquityCurveBuilder.kt` (ใหม่)
- `composeApp/.../ui/component/SparklineChart.kt` (ใหม่)
- `composeApp/.../ui/component/MarginGauge.kt` (ใหม่)

### Phase 5 (History)
- `composeApp/.../ui/screen/TradingTerminalScreen.kt` (History tab section)
- ใช้ `TradeStatsCalculator` ร่วมกับ Phase 2

---

## 📊 Progress Tracking

| Phase | สถานะ | เริ่ม | เสร็จ | Commit |
|---|---|---|---|---|
| P1 Settings | 📋 Planned | – | – | – |
| P6 Persistence | 📋 Planned | – | – | – |
| P3 Trade | 📋 Planned | – | – | – |
| P4 Auto | 📋 Planned | – | – | – |
| P2 Overview | 📋 Planned | – | – | – |
| P5 History | 📋 Planned | – | – | – |

---

**สรุปสั้น:** แผนนี้แก้ปัญหา 22 จุดที่พบ + เพิ่ม Persistence Layer (P6) เพื่อให้ app แสดงข้อมูลได้แม้ server ปิด — เน้น "เห็นข้อมูลล่าสุดเสมอ" + "broker-aware" (symbol/watchlist ตาม broker จริง) + UI ที่อ่านง่ายบนมือถือ

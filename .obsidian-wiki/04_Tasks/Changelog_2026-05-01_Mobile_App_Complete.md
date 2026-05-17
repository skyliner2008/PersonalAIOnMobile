# 📋 Changelog — Mobile App Full Improvement (2026-05-01)

**วันที่:** 2026-05-01
**ผู้ดำเนินการ:** Claude (Cowork) ← skyliner.jojo@gmail.com
**Branch/Scope:** `composeApp` — KMP/Compose Multiplatform
**สถานะ:** ✅ ครบทุกเฟส + Integration Audit ผ่าน

---

## 📦 ไฟล์ที่สร้างใหม่ (New Files)

### 1. `data/SymbolCategoryClassifier.kt`
**วัตถุประสงค์:** จัดหมวดหมู่ symbol ของ broker อัตโนมัติโดยใช้ heuristic

```
SymbolCategory enum:
  METALS  → XAUUSD, XAGUSD, GOLD, SILVER, XAU…, XAG…
  CRYPTO  → BTC, ETH, LTC, XRP, BNB… และ suffix …USD/USDT/BTC
  INDICES → US30, NAS100, SPX500, DAX, FTSE, JP225, HSI…
  STOCKS  → ลงท้าย .US / .NAS / .NYSE, หรือ single-word >3 ตัวอักษร
  FOREX   → คู่ที่เหลือ 6-8 ตัวอักษร
  OTHER   → fallback
```

**Functions:**
- `classify(symbol: String): SymbolCategory`
- `groupSymbols(symbols: List<String>): Map<SymbolCategory, List<String>>`
- `labelFor(symbol: String): String` — emoji label สำหรับแสดงใน UI

---

### 2. `tools/trading/BrokerSymbolCache.kt`
**วัตถุประสงค์:** In-memory TTL cache ของ symbol catalogue ต่อ broker พร้อม favorites persistence

**คุณสมบัติ:**
- TTL 5 นาที (in-memory) ต่อ brokerId
- Thread-safe ด้วย `Mutex`
- Favorites เก็บใน SQLDelight `AppSetting` key: `broker_symbol_favs.<brokerId>`
- แยกข้อมูลต่าม broker ไม่ปนกัน (e.g., XAUUSD vs XAUUSDm)

**API สาธารณะ:**

| Method | คำอธิบาย |
|--------|----------|
| `loadAll(brokerId)` | โหลด list ทั้งหมด (cache หรือ DB) |
| `grouped(brokerId)` | จัดกลุ่มตาม SymbolCategory |
| `favoritesFirst(brokerId)` | ⭐ Favorites + Metals/Crypto top 10 |
| `invalidate(brokerId)` | ล้าง cache หลัง sync ใหม่ |
| `invalidateAll()` | ล้างทุก broker (ใช้ตอน logout) |
| `favorites(brokerId)` | โหลด list favorites จาก DB |
| `saveFavorites(brokerId, symbols)` | บันทึก favorites ทั้งชุด |
| `toggleFavorite(brokerId, symbol)` | toggle เดี่ยว → return Boolean |
| `isFavorite(brokerId, symbol)` | ตรวจสอบ |
| `search(brokerId, query)` | ค้นหาแบบ substring (symbol + description) |

---

### 3. `ui/screen/SymbolPickerDialog.kt`
**วัตถุประสงค์:** Dialog เลือก symbol แบบ full-screen พร้อม UX ระดับ production

**Feature:**
- 🔍 Search box (instant filter บน symbol code + description)
- 📂 Grouped list: Metals / Crypto / Indices / Stocks / Forex / Other
- ⭐ Favorite toggle — persist ต่อ broker ผ่าน `BrokerSymbolCache`
- Favorites section ปัก pin ไว้ด้านบนเสมอ (ถ้ามี)
- Loading state ขณะโหลด symbols จาก cache
- Empty-state message เมื่อไม่มี symbol ใน DB ("No symbols in cache. Connect to broker and sync.")
- รองรับ keyboard Search action

---

## ✏️ ไฟล์ที่แก้ไข (Modified Files)

### 4. `ui/screen/TradingTerminalScreen.kt`

#### 🔴 Critical Bug Fix — File Truncation
ไฟล์ถูก truncate กลางประโยคที่ line ~1894 (`modifier = Modifier.fillMax`) — ส่วนที่หายไป:
- Surface body ของ Terminal tab (LazyColumn + color-coded lines)
- `ClientRow` composable (status dot, path, selection highlight)
- `EventsTab` composable (engine status bar + decision cards + StatChip rows)
- Format helper functions ทั้ง 5 ตัว

**การแก้ไข:** ใช้ Python trim ถึง truncation point แล้ว append ส่วนที่หายครบ — ตรวจ `{` - `}` balance = 0

#### P2 — Overview Tab: Premium Stats
เพิ่มเข้า `OverviewTab`:
- `PeriodFilter` enum (Today / 7D / 30D / All)
- `filterByPeriod()` extension บน `List<Mt5TradeItem>`
- `PeriodStats` data class (total, wins, losses, totalProfit, avgProfit, best/worst, winRate)
- `computeStats()` — คำนวณ stats จาก trade list
- `PeriodStatsCard` composable — แสดง stats 4 ตัวใน 2×2 grid
- `StatMini` composable — label + value unit สำหรับ stats
- Period selector row (chip เลือก Today/7D/30D/All) ที่ด้านบน Overview

#### P3 — Trade Tab: Broker-Sourced Symbols
แก้ `TradeTab` และ `OrderEntryCard`:
- รับ `brokerId: String` parameter
- Symbol chips บน Trade tab โหลดจาก `BrokerSymbolCache.favoritesFirst(brokerId)` แทน hardcoded list
- ปุ่ม **"Browse…"** เปิด `SymbolPickerDialog`
- chip "+ More" trigger picker เช่นกัน
- `LaunchedEffect(brokerId)` refresh chips เมื่อเปลี่ยน broker

#### P5 — History Tab: Period + Outcome Filter
แก้ `HistoryTab`:
- Dual filter: Period (Today/7D/30D/All) + Side/Outcome (All/BUY/SELL/Win/Loss)
- Extended stats card: win-rate progress bar + 2-row stats grid
- เปลี่ยนเป็น `LazyColumn` wrapper (รองรับ list ยาว)

---

### 5. `ui/screen/AutoTradingScreen.kt`

#### P4.1 — Gold-Symbol Watchlist Seeding
เพิ่มใน `AutoTradingPanel`:
```kotlin
LaunchedEffect(Unit) {
    // ถ้า watchlist ว่าง → เช็ค broker → หา gold symbol → pre-fill
    if (cfg.watchlist.isEmpty()) {
        val brokerId = Mt5LocalCache.activeBrokerId()
        val syms = BrokerSymbolCache.loadAll(brokerId).map { it.symbol }
        val gold = Mt5LocalCache.detectGoldSymbol(syms)
        if (gold != null) vm.updateConfig(cfg.copy(watchlist = listOf(gold)))
    }
}
```

#### P4.2 — 3-Tier Advanced Settings
เพิ่ม `AdvancedSettingsPanel` composable ที่แทน flat settings list เดิม:

| Tier | Badge | เนื้อหา |
|------|-------|---------|
| Tier 1 | BASIC | Min confluence, max risk %, max positions, max daily loss |
| Tier 2 | INTERMEDIATE | Break-even threshold, trailing stop % |
| Tier 3 | EXPERT | AI agent prompt override, memory window size |

- `DisclosureTier` composable — header ที่ toggle expand/collapse
- **ลบ** Symbol Blacklist section ออก (ไม่ใช้งาน)
- `WatchlistEditor` เพิ่มปุ่ม **"Browse"** → เปิด `SymbolPickerDialog`

---

## 🔗 Integration Audit Results (ตรวจสอบ 2026-05-01)

ตรวจ **10 เส้นทาง** ระหว่าง Mobile App ↔ mt5-core-server — **ผ่านทั้งหมด**

| # | เส้นทาง | Mobile | Server | ผล |
|---|---------|--------|--------|----|
| 1 | Bridge Connect | `connectMt5()` → `ServerConfig` | `POST /bridge` (Zod validated) | ✅ |
| 2 | MT5 Snapshot | `fetchMt5Snapshot()` → `Mt5LocalCache.saveSnapshot()` | `GET /snapshot` | ✅ |
| 3 | Place Order | `placeMt5Trade()` → `Mt5TerminalService` | `POST /trade` | ✅ |
| 4 | Close Position | `closeMt5Position()` | `POST /trade` (close variant) | ✅ |
| 5 | Modify SL/TP | `modifyMt5Trade()` | `POST /modify` (TRADE_ACTION_SLTP) | ✅ |
| 6 | Break-Even All | `setMt5BreakEvenAll()` → JarvisViewModel L.1429 | `POST /modify` loop | ✅ |
| 7 | AutoTrading | `AutoTradingEngine` 6-phase cycle | `/snapshot` + `/candles` + `/trade` | ✅ |
| 8 | API Key Test | `testApiKey()` → `ApiKeyTester` | `/health` + model listing | ✅ |
| 9 | Settings Save | `AppSetting` SQLDelight | (local only) | ✅ |
| 10 | Watchlist Sync | `applySnapshot` callback bidirectional | (local + server-sourced) | ✅ |

**ประเด็นที่ตรวจสอบพิเศษ:**
- `serverOnline` StateFlow อัปเดตถูกต้องที่ JarvisViewModel lines 1290 (online) / 1321 (offline)
- Break-even direction guard ป้องกัน SL ถอยหลัง
- Watchlist sync เป็น bidirectional ผ่าน `applySnapshot` callback ใน `submit()`
- Schema Kotlin ↔ Node.js ↔ MT5 Bridge ตรงกันทุก field

---

## 📁 สรุปไฟล์ทั้งหมดที่เปลี่ยนแปลง

```
composeApp/src/commonMain/kotlin/com/example/personalaibot/
├── data/
│   └── SymbolCategoryClassifier.kt              [NEW]
├── tools/trading/
│   └── BrokerSymbolCache.kt                     [NEW]
└── ui/screen/
    ├── SymbolPickerDialog.kt                    [NEW]
    ├── TradingTerminalScreen.kt                 [MODIFIED — P2, P3, P5 + truncation fix]
    └── AutoTradingScreen.kt                     [MODIFIED — P4.1, P4.2]
```

**ไฟล์ที่สร้างในเซชันก่อนหน้า (ยังคงอยู่):**
```
├── tools/trading/
│   ├── Mt5LocalCache.kt                         [P6 — created 2026-04-30]
│   └── Mt5TerminalService.kt                    [modified 2026-04-30]
├── ui/screen/
│   └── SettingsDialog.kt                        [P1 — modified 2026-04-30]
└── ui/components/
    └── StaleBanner.kt                           [P6 — created 2026-04-30]
```

---

## 🧪 Phase Completion Summary

| Phase | เป้าหมาย | Task IDs | สถานะ |
|-------|----------|----------|-------|
| P6 | Local Persistence Layer — Mt5LocalCache + stale banner | #1–3 | ✅ Done |
| P1 | Settings — API Key Tester + collapsible sections | #4–7 | ✅ Done |
| P3 | Trade Tab — broker symbol catalogue + SymbolPickerDialog | #8–11 | ✅ Done |
| P4 | Auto Tab — gold seeding + 3-tier advanced settings | #12–13 | ✅ Done |
| P2 | Overview Tab — period selector + premium stats cards | #14 | ✅ Done |
| P5 | History Tab — period filter + extended stats | #15 | ✅ Done |
| Audit | Integration audit — 10 paths verified | #16 | ✅ Done |

**รวม Tasks ที่เสร็จ: 16/16** 🎉

---

## 🔖 Tags
`#mobile` `#trading` `#mt5` `#compose` `#kotlin` `#changelog` `#2026-05-01`

# 🔗 V17.0: MT5 Full Agent Control — Broker-First Architecture

> **Status: 🟢 Stable** | **Release: 2026-04-21** | **Tools: 19 ตัว (10 Core + 3 Action + 6 Intelligence)**

## 🏗️ สถาปัตยกรรม

```
Mobile App (Kotlin/KMP)
    │
    ├─ FunctionDeclaration (TradingToolDefinitions.kt)
    ├─ Executor (TradingToolExecutor.kt)
    ├─ Endpoint Mapping (ToolExecutor.kt → enrichMt5Args)
    │
    ▼
mt5-core-server (Node.js + Express, port 8090)
    │
    ├─ Zod Schema Validation
    ├─ SQLite Audit Log (mt5_snapshots, mt5_trade_actions)
    │
    ▼
Python Bridge (mt5_bridge.py)
    │
    └─ MetaTrader5 API (mt5.*)
```

## 📋 MT5 Tool Catalogue (19 ตัว)

### ⚡ Core Actions (3 ตัว — มีมาก่อน V17)
| Tool | Endpoint | Method |
|:-----|:---------|:-------|
| `trading_mt5_order` | `/api/mt5/order` | POST |
| `trading_mt5_close_position` | `/api/mt5/close` | POST |
| `trading_mt5_modify_position` | `/api/mt5/modify` | POST |

### 📊 Core Agent (10 ตัว — ใหม่ V17)
| Tool | Endpoint | Method | Description |
|:-----|:---------|:-------|:------------|
| `trading_mt5_account_info` | `/account` | GET | Equity, Balance, Margin, P&L |
| `trading_mt5_list_positions` | `/positions` | GET | Open positions (filter by symbol) |
| `trading_mt5_list_orders` | `/orders` | GET | Pending orders |
| `trading_mt5_list_history` | `/history` | GET | Trade history deals |
| `trading_mt5_candles` | `/candles` | GET | OHLCV from broker |
| `trading_mt5_symbol_info` | `/symbols` | GET | Spread, digits, contract size |
| `trading_mt5_close_all` | `/close` | POST | Close all positions |
| `trading_mt5_break_even_all` | `/positions→/modify` | GET+POST | Move SL to break-even |
| `trading_mt5_snapshot` | `/snapshot` | GET | Full snapshot (account+positions+orders) |
| `trading_mt5_trade_actions` | `/trade-actions` | GET | JARVIS audit log |

### 🧠 Advanced Intelligence (6 ตัว — ใหม่ V17)
| Tool | Data Source | Description |
|:-----|:-----------|:------------|
| `trading_mt5_market_scanner` | `/candles` + `/symbols` | สแกนหา symbols น่าสนใจจาก broker |
| `trading_mt5_correlation_radar` | `/candles` หลาย symbols | Pearson correlation ระหว่าง symbols |
| `trading_mt5_sentiment_gauge` | `/account` + `/positions` + `/history` | วิเคราะห์สุขภาพพอร์ต/จิตวิทยา |
| `trading_mt5_institutional_flow` | `/candles` multi-TF | Volume profile & order flow |
| `trading_mt5_economic_radar` | `/positions` + macro_calendar | ข่าวเศรษฐกิจ → risk mapping |
| `trading_mt5_trade_journal` | `/history` + `/trade-actions` | AI Scoring & pattern analysis |

## ⚠️ Key Contract Decisions

### จาก project_mt5_core_fixes_2026_04_20.md
1. `/api/mt5/order` accepts ทั้ง `side` และ `action` (case-insensitive) — ห้าม tighten กลับ
2. `/api/mt5/close` ยอมรับ `ticket: ""` ถ้ามี `symbol` — Kotlin ส่ง `ticket: ""` เมื่อ close by symbol
3. `normalizeTradeSide` ทำ defensive ทั้ง Node + Python — ห้ามลบฝั่งใดฝั่งหนึ่ง
4. `ensure_mt5()` cache init state (TTL 30s success / 3s fail) — ห้าม revert
5. Python `_try_order_send_with_filling()` cycle IOC→FOK→RETURN — broker-specific

### จาก project_mt5_modify_break_even_2026_04_21.md
1. เพิ่ม tool ใหม่ต้องทำครบ 7 ชั้น: bridge → Node → Executor → Definitions → Registry → ToolExecutor → VM → UI
2. `POST /modify` ใช้ `mt5.TRADE_ACTION_SLTP` (ไม่ใช่ DEAL)
3. SL/TP ว่าง = คงค่าเดิม (Python: `_coerce(value, default=cur_value)`)

### จาก project_mt5_ui_redesign_2026_04_20.md
1. `Mt5TradeItem.side` ต้องเป็น `"BUY"/"SELL"` — ถ้าลบ `normalizeTradeSide` UI จะพัง
2. KMP: ใช้ `kotlinx.datetime.Clock.System.now()` แทน `System.currentTimeMillis()`
3. Semantic colors (BuyGreen, SellRed) เป็น file-local vals ไม่ใช่ theme tokens
4. PnL ทุกที่ใน UI ต้องใช้ `row.netPnl` (computed: profit + swap + commission) — ห้ามใช้ `profit` ลอยๆ
5. Win/Loss count + History filter + PositionCard + HistoryRow ต้องใช้ `netPnl` ทั้งหมด
6. **CRITICAL: MT5 `entry` field** — `history_deals_get()` returns ALL deals:
   - `entry=0` (DEAL_ENTRY_IN) = เปิด position → **profit=0 เสมอ** → ห้ามแสดงใน History
   - `entry=1` (DEAL_ENTRY_OUT) = ปิด position → มี P&L จริง → แสดงเฉพาะตัวนี้
   - `entry=2` (INOUT) / `entry=3` (OUT_BY) = reverse/hedge close → มี P&L จริง
   - History UI filter: `deals.filter { it.isExitDeal }` (entry > 0)
7. **Exit deal side flip** — Closing a BUY = deal type=SELL → `parseTrades` flip กลับเป็น BUY
   เพื่อให้ history แสดง side ของ ORIGINAL trade ไม่ใช่ closing action

## 🔑 Broker-First Data Sourcing Policy

> **Advanced Intelligence tools ดึงข้อมูลจาก MT5 broker โดยตรง** ไม่ใช่จาก TradingView
> เพื่อให้ข้อมูลตรงกับสิ่งที่โบรกเกอร์ forex แสดง (timing, spread, volume อาจต่างกัน)

---
**Links**: [[Trading_Intelligence_MOC]] | [[catalogue]] | [[10_Global_Insights_V16.0]]

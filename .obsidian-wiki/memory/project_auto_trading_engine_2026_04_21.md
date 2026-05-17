---
name: Auto-Trading Engine + MT5 token/race/prompt fixes (2026-04-21)
description: แก้ 3 บัคใหญ่ (token, race, tool confusion) + เพิ่ม AutoTradingEngine (6-phase loop) ที่ composeApp/.../tools/trading/AutoTradingEngine.kt
type: project
originSessionId: 776b71ae-233f-4e43-add7-9e532865f100
---
ชุดการเปลี่ยนแปลงใหญ่วันที่ 2026-04-21: ตอบโจทย์ผู้ใช้เรื่อง "ระบบ Agent auto Trading ที่มีแบบแผน วิเคราะห์, กลยุทธ, Trading, แก้ไข, บันทึก, เรียนรู้" + แก้บัค 3 ข้อที่ผู้ใช้รายงาน

**Why:** ผู้ใช้รายงาน (1) Agent สั่งออเดอร์ MT5 ไม่ได้ "Client token required" (2) bridge โดนเรียกซ้อนกัน (3) Agent แยกไม่ออกระหว่าง Trading tools (22, TV) vs MT5 tools (19, broker)

**How to apply:** ถ้าต้องแก้/ขยายระบบ auto-trading ในอนาคต ดู flow นี้:

**1. Token forwarding fix — TradingToolExecutor.kt**
- `mt5Get()` helper รับ `token: String = ""` param แล้วใส่ header `Authorization: Bearer <token>` + `X-Client-Token: <token>` เมื่อไม่ว่าง
- Helper ใหม่ `mt5GetWithArgs(endpoint, args)` สำหรับ tool ที่มี args map
- 14 MT5 tool functions ถูกอัปเดตให้ forward token ครบ (8 core + 6 advanced intelligence)

**2. Concurrency fix — bridgeClient.ts + mt5_bridge.py**
- Node: เพิ่ม `bridgeChain` promise + `enqueue()` helper ที่ serialize ทุก `callBridge()` เป็น FIFO queue (กัน stacking)
- Python: เพิ่ม `_MT5_OPS_LOCK = threading.RLock()` ครอบทั้ง `do_GET` และ `do_POST` เป็นเกราะชั้นสอง (กันถ้ามี caller อื่นเข้ามาข้าม Node)
- QUEUE_MAX_WAIT_MS = 60s กัน starvation

**3. Tool routing — GeminiService.kt JARVIS_SYSTEM_PROMPT**
- เพิ่มบล็อก TOOL ROUTING แบ่ง A) Market Analysis (trading_*) vs B) Live Broker (trading_mt5_*)
- ลิสต์คำ trigger ชัดเจน: "บัญชี/พอร์ต/equity/MT5/broker/เปิด-ปิดออเดอร์" → trading_mt5_*
- "ราคา/วิเคราะห์/สแกน/SMC/Wyckoff" → trading_*
- เพิ่มกฎใหม่ข้อ 6 [TRADE SAFETY]: ยืนยันก่อนวางออเดอร์ MT5 (เว้น autonomous mode)
- เพิ่มกฎข้อ 7 [RISK]: R:R ≥ 1.5 + แจ้ง % equity เสี่ยง

**4. AutoTradingEngine — ไฟล์ใหม่**
- Path: `composeApp/src/commonMain/kotlin/com/example/personalaibot/tools/trading/AutoTradingEngine.kt`
- Class `AutoTradingEngine(scope: CoroutineScope)` — มี 3 coroutine loop: cycle (60s) / manage (30s) / learn (6h)
- 6 เฟส: ANALYZING → STRATEGIZING → EXECUTING → MANAGING → LEARNING → IDLE
- `Config` data class: watchlist, timeframe, confluenceThreshold, riskPerTradePct, maxOpenPositions, minRRR, breakEvenTriggerR, enableLiveTrading (default false = paper), symbolBlacklist
- Public StateFlows: `state`, `config`, `lastDecisions`, `lastJournalSummary` — UI อ่านได้ตรง
- `start()` / `stop()` / `runOnceNow()` / `updateConfig { ... }`
- Analyze: เรียก trading_price + trading_smc_analysis + trading_multi_timeframe + trading_mt5_candles → detectBias (BOS/OB/LIQ/OVERSOLD/OVERBOUGHT/trend) → compute SL (swing high/low last 20 bars) + TP (minRRR × risk)
- Strategize: score = 40 + 8×signals + bonus(BOS=10, OB=8, LIQ=7); ≥70 ถือว่าพร้อม execute
- Execute: paper mode (log เฉย ๆ) หรือเรียก trading_mt5_order (enableLiveTrading=true)
- Manage: ดึง list_positions → คำนวณ R-multiple → ถ้า ≥ breakEvenTriggerR (default 1.0R) ยังไม่ BE → trading_mt5_modify_position sl=priceOpen
- Learn: ดึง trade_journal (200 deals) → สรุป winRate, profit factor, best/worst symbol → auto-tune threshold (wr<35% เพิ่ม 5, wr>60% ลด 2) + blacklist worst symbol ถ้า total ≥10
- ใช้ Mutex single-writer + parsePositions ด้วย regex (ไม่ต้องพึ่ง JSON lib)

**Key files touched (this session):**
- composeApp/src/commonMain/kotlin/com/example/personalaibot/tools/trading/TradingToolExecutor.kt — token forwarding 14 tools
- composeApp/src/commonMain/kotlin/com/example/personalaibot/tools/trading/AutoTradingEngine.kt — **NEW**
- composeApp/src/commonMain/kotlin/com/example/personalaibot/data/GeminiService.kt — JARVIS_SYSTEM_PROMPT routing block
- mt5-core-server/src/services/bridgeClient.ts — promise-chain mutex
- mt5-core-server/bridge/mt5_bridge.py — _MT5_OPS_LOCK RLock

**Pending wiring (next session):**
- สร้าง ViewModel / injection point เรียก `AutoTradingEngine(scope)` + ผ่าน config จาก Settings screen
- UI toggle enableLiveTrading (default OFF → paper) และแสดง `state`/`lastDecisions`/`lastJournalSummary` ใน TradingTerminalScreen
- Persistent config store (SharedPreferences/DataStore) สำหรับ watchlist + threshold

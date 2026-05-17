---
name: V25.0 Real-Time Wall Engine (Design)
description: ออกแบบ V25 — tick-level watchtower + Wall State Machine + 5 playbooks เพื่อแก้ปัญหา SKIP loop จาก log 2026-05-09
type: project
---

# V25.0 — Real-Time Wall Engine (Design Spec)

**สถานะ:** 🟢 Phase A + B + C + D committed 2026-05-10 (shadow mode opt-in) · ⏳ LLM-gating + V24 sunset pending
**เอกสารเต็ม:** `.obsidian-wiki/07_Trading_Intelligence/25_RealTimeWallEngine_V25.md`
**Diagrams:** `.obsidian-wiki/07_Trading_Intelligence/25_TradeFlow_Diagrams.md`

## Phase A–D files committed (2026-05-10) — total ~2,446 LOC

```
mt5-core-server/src/services/auto/v25/
├── types.ts                          # Tick, BarCloseEvent, MicroWallSnapshot, WallStateName, V25SymbolProfile + DEFAULT_PROFILES
├── TickBuffer.ts                     # ring buffer + registry (singleton tickBuffers)
├── V25EventBus.ts                    # dedicated bus (V25_TICK/BAR_CLOSE/MICRO_WALL/WALL_STATE_CHANGE/STALE_TICK)
├── TickGateway.ts                    # REST polling /symbol_info every 1000ms, dedupe by srcTs, stale guard 5000ms
├── BarCloseEventBus.ts               # clock-aligned setTimeout per (symbol, TF), 50ms post-boundary slack
├── M1MicroWallBuilder.ts             # rolling micro-pivot 1s sample, 60s window, dedupe by epsilon
├── state/
│   ├── PriceMapSnapshotProvider.ts   # read-only adapter over indicatorPipeline.getPriceMap()
│   ├── WallProximityIndex.ts         # sorted asc/desc per symbol, binary search nearest above/below
│   ├── FvgFreshTracker.ts            # bull/bear FVG arrays, drop on price-fill (per tick)
│   ├── ReactionDetector.ts           # M1 engulf/pinbar/FVG-fill classifier
│   └── WallStateMachine.ts           # 7-state FSM × side (above/below) per symbol, attach() to V25 bus
├── playbooks/
│   ├── types.ts                      # PlaybookContext, TradePlan, PlaybookId
│   ├── PB1_WallTouchScalp.ts         # default — wall touch + opposite-FVG-fresh + RRR ≥ profile.minRrr
│   ├── PB2_FvgFillReversal.ts        # OB+FVG combo, tighter SL inside OB, RRR ≥ 2
│   ├── PB3_SweepReversal.ts          # liquidity sweep (FVG_FILL/PINBAR), SL outside swing wick, RRR ≥ 2
│   ├── PB4_RangeFade.ts              # RANGING regime, TP at range mid, RRR ≥ 1.5
│   ├── PB5_BreakoutRetest.ts         # only on RETEST state, continuation in break direction
│   └── index.ts                      # PlaybookSelector cascade: PB3>PB2>PB5>PB4>PB1, shadow-only emit
└── index.ts                          # bootstrapV25Shadow() + shutdownV25Shadow() + v25Health() + v25SymbolState()

mt5-core-server/src/routes/v25.ts     # GET /api/v25/{health,ticks/:s,state/:s,candidates/:s} (token-protected)
mt5-core-server/src/index.ts          # +imports + bootstrap call inside bootstrap() + shutdown hook
```

Compile: `tsc --noEmit` clean for all 19 V25 files (4 pre-existing V24 errors unrelated).

## Phase B/C/D mechanics

**Wall State Machine** (`WallStateMachine.ts`):
- 2 states per symbol (`above`/`below`), each runs IDLE→APPROACH→REACT→{CONFIRM,BREAK_OUT}→{COMMITTED,RETEST→COMMITTED}
- Tick handler: throttled 200ms, evaluates proximity vs ATR-relative zones (approach=1.5×ATR, react=0.5×ATR per profile)
- Bar-close handler: M5/M15/M30/H1 → refresh proximity + FVG; M1 → run ReactionDetector to advance REACT→CONFIRM
- STALE_TICK → freeze until next bar close (Q1 design decision)
- Velocity decel check on APPROACH→REACT (last 20 ticks, pip/sec vs profile.reactAtrMul × ATR×0.05)

**PlaybookSelector** (`playbooks/index.ts`):
- Subscribes to V25_WALL_STATE_CHANGE; acts only on CONFIRM/RETEST
- Builds PlaybookContext from: nearest walls, ATR(M15) from PriceMap, M5 last-5 swing high/low, fvgFreshTracker, symbol profile
- Cascade tries PB3 → PB2 → (PB5 if RETEST) → PB4 (if RANGING) → PB1; first non-null wins
- Shadow-only: logs `[V25] 🎯 PBn ...` + ring-buffers up to 20 candidates per symbol; no OrderSend
- Skip reasons stored (last 10 per symbol) for diagnostic via `/api/v25/candidates/:symbol`

## Activation
- env `V25_SHADOW=true` → bootstrap pipeline (default off)
- env `V25_SYMBOLS=XBTUSD,XAUUSD` (csv default)
- env `V25_TICK_POLL_MS=1000` · `V25_STALE_MS=5000` · `V25_VERBOSE_TICK=false`
- Diagnostics:
  - `GET /api/v25/health` — counters, ticks/sec, gateway+state+playbook stats
  - `GET /api/v25/ticks/:symbol?limit=N` — raw tick buffer
  - `GET /api/v25/state/:symbol` — wall proximity + fresh FVGs + state + recent candidates
  - `GET /api/v25/candidates/:symbol` — last 20 trade plans + last 10 skip reasons

## Pending
- LLM gating: have slTpAgent fire only when WallStateMachine = REACT (skip ~14 wasted calls/cycle)
- ExecutionService handoff: flip `playbookSelector.configure({ shadowOnly: false })` + wire to `tradingExecutionService.placeOrder`
- V24 sunset: introduce `enable_v25_only` config flag + decommission `deterministicEngine` after A/B

## ที่มา (จาก log 2026-05-09 14:09–14:29)

XBTUSD รัน 14 cycle ติดลูป SKIP รูปแบบเดียวกัน:
- Entry กลางช่อง 80146–80278 (ระหว่าง wall 5★ 80113.37 ↔ 80483.36)
- slTpAgent ตั้ง SL ห่าง 200–400 pip (เลือก wall ดาวเยอะที่ไกล)
- Global TP cap ดึง TP มาใกล้ entry เพราะ wall เล็ก 1–2★ ขวาง path
- RRR = 0.12–0.53 → Hard Risk Gate SKIP ทุกรอบ
- เปลือง LLM token (slTpAgent ถูกเรียกทุก cycle)

**Why:** ระบบ V24 ทำงานเป็น cycle 60–90s + Proximity Gate ใช้ `proximityMaxPip:100` ที่ไม่ scale ตาม XBTUSD volatility → ปล่อยให้ trigger entry กลางช่อง

**How to apply:**
- ห้ามแก้ V24 ทีละ patch (proximity threshold, SL chooser) เพราะปัญหาเชิงโครงสร้าง — ต้อง re-architect เป็น event-driven
- ตอน implement Phase A ของ V25 ให้รัน parallel กับ V24 (shadow mode) ก่อน sunset

## สาระแก่นของ V25

### 7-layer architecture
- **L0** MT5 Bridge (REST + WS tick)
- **L1** TickBuffer + M1MicroWallBuilder (200ms throttle)
- **L2** Bar-Close Calculators (per-TF event-driven)
- **L3** PriceMap (V21.1 ของเดิม + FvgFresh + SweepRaw + M1Micro)
- **L4** WallProximityIndex + WallStateMachine (IDLE→APPROACH→REACT→CONFIRM/BREAK→COMMITTED)
- **L5** PlaybookSelector (5 playbooks)
- **L6** Existing AutoTradingEngine (execution + management)

### กฎเหล็ก 5 ข้อ
- R1: Entry **ต้อง** อยู่ใน Reaction-Zone (ATR×k) ของ wall ≥ 2★ — no mid-channel
- R2: SL ใต้/เหนือ swing **เลย wall** ≤ 1×ATR(M15)
- R3: TP = ใต้/เหนือ wall ตรงข้าม — ไม่ลด TP ลงเพราะ obstacle 1★
- R4: Indicator/SMC คำนวณเฉพาะตอน bar-close ของ TF นั้น
- R5: LLM (slTpAgent) เรียกเฉพาะตอน Wall State = REACT

### 5 Playbooks
- PB1 Wall-Touch Scalp ⭐ (default ของ user)
- PB2 FVG Fill Reversal
- PB3 Liquidity Sweep Reversal
- PB4 Range Boundary Fade
- PB5 Breakout-Retest Continuation

### Detection Modules ตามที่ user ขอ
1. TickBuffer + M1MicroWall (ราคา realtime + M1 wall)
2. WallProximityIndex (mark wall ใกล้ราคา)
3. WallStateMachine (ใกล้/ทะลุ wall)
4. ReactionDetector (เด้งกลับเมื่อใกล้/ทะลุ)
5. FvgFreshTracker (FVG ใหม่ที่ยังไม่ filled)
6. M1ReversalConfirmer (engulf/pinbar/FVG-fill ใน M1)
7. BreakoutWallPromoter (wall ใหม่หลังทะลุ + retest setup)

## Migration: 4 phases (4 sprint)
- A: Foundation (TickGateway + BarCloseEventBus) — shadow mode
- B: Wall Engine (state machine + reaction) — emit events to V24
- C: PB1 only — A/B vs V24
- D: PB2–PB5 + LLM gating — sunset V24

## KPI target (เทียบ V24 จาก log)
- Cycles per executed trade: ∞ → ≤6
- LLM calls per trade: ~14 → ≤2
- Avg RRR at execute: n/a → ≥2.0
- TP > SL distance: ❌ → ✅ (R3)

## Files ที่ทำใหม่ใน mt5-core-server
```
services/auto/tick/        — TickBuffer, TickGateway, M1MicroWallBuilder
services/auto/state/       — WallStateMachine, WallProximityIndex, ReactionDetector
services/auto/playbooks/   — PB1–PB5 + selector
services/auto/analyzers/smc/ — FvgFreshTracker, BreakoutWallPromoter
services/auto/core/BarCloseEventBus.ts
bridge/mt5Bridge.ts        — เพิ่ม WS endpoint /ws/tick/:symbol
```

## Open questions (ก่อน implement Phase A)
1. Tick latency budget end-to-end (target ≤ 300ms)
2. Multi-symbol throttle (worker pool หรือ sequential?)
3. Persistence ของ Wall State หลัง server restart
4. ATR multiplier override per symbol (XBT vs XAU)

## Related
- V21.1 PriceMap (เก็บไว้, V25 ต่อยอด)
- V22.0 Indicator Confluence (เก็บไว้, ใช้ใน L2)
- V24.0 FadeTheLevel (จะ deprecate หลัง Phase D)

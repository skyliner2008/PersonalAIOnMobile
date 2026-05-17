---
name: Auto-trading correctness audit 2026-04-22
description: Shipped 6 fixes after auditing mt5-core-server AutoTrading pipeline — ticket extraction, SL/TP tick rounding, break-even guard, config hot-reload, trade-outcome embeddings
type: project
originSessionId: 4e0a795a-c079-492a-9f0b-2aa2ea11be20
---
Audit performed on `mt5-core-server/src/services/autoTradingService.ts` after a real XAUUSD SELL order executed on broker (retcode 10009, deal=100455254, order=142476221) but journal recorded `mt5_ticket=null`. Root cause was regex-based `extractTicket` looking for a field name that MT5 does not return.

Fixes applied:

1. **`extractTicket` structured parser** (line ~927). Now tries `order` → `ticket` → `order_ticket` → `position` → `deal` in priority order across root / `data` / `result` / `response` nesting. Unblocks `runManage`'s position→journal matching.

2. **Tick-size snapping for SL/TP** (line ~411). Added `tickSize()` + `roundToTick()` helpers in `auto/utils.ts`. SL/TP are now snapped to the symbol tick grid (XAUUSD=0.01, FX5=0.00001, etc.) before being sent and before being written to the journal — so the journal matches the broker's post-rounding values, and `profitR` at close stays accurate. Also added a guard that bumps SL/TP one tick away from entry if rounding collapses them onto entry.

3. **Actual-RRR recompute** (line ~428). `actualRrr` is now calculated from the rounded SL/TP (not from the target `targetRrr`), logged alongside target, and written to the journal. Prevents silent RRR drift.

4. **Early SKIP on invalid riskDistance** (line ~433). Guards against `riskDistance <= 0` / NaN before volume sizing — avoids sending orders with zero stop.

5. **Config hot-reload per cycle** (line ~297). `this.config = this.loadConfig()` runs at the top of `runCycle()`, so API-key rotation and watchlist edits take effect without a server restart. Failure to reload falls back to cached config with a warning.

6. **Break-even SL direction guard** (line ~665). Break-even move now requires the new SL to be strictly better than the current SL for that side (BUY: new > current; SELL: new < current OR current ≤ 0). Stops the old code from re-setting SL to entry when it's already been trailed further.

7. **Trade-outcome embeddings** (line ~613). When a trade closes in `runManage`, a `trade_outcome` vector with `{outcome, profit, profitR, closeReason}` context is written to `vectorStore` alongside the existing knowledge-graph update. Future similarity search now sees real win/loss pairs, not just entry-time snapshots frozen at OPEN.

**Why:** real money at stake — false positives in audits were acceptable, missing a bug was not.
**How to apply:** When extending the AutoEngine, preserve the "rounded SL/TP is authoritative, raw values are a first draft" invariant. If vectorStore `DIMENSION` changes from 768, also update `EMBEDDING_TARGET_DIMS` in `agentOrchestrator.ts`.

Related memory: `project_embedding_fix_2026_04_22.md` (the embedding-model cascade fix that preceded this audit).

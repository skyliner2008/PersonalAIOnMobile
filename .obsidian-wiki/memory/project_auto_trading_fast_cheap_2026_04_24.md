---
name: Auto-trade Fast & Cheap Overhaul 2026-04-24
description: 7-change overhaul addressing user intents — hedge/protect, counter-trend H4, cut LLM cost, parallel manage audit, anti-hedge fix, orphan-journal race, and deterministic pre-analysis
type: project
originSessionId: 70abff31-ceb4-42cb-bf73-9d1d293e9f50
---
Intent recap (user gave 4 explicit goals):
1. Hedge to protect; add winning-side when opportunity; close fast at net-BE
2. Allow counter-trend H4
3. Drastically reduce LLM token cost — prefer system-based analysis
4. Speed up AI reasoning + Manager audit — they lagged behind price

**Why:** By the time reasoning + manage finished (20-40s for Gemini + serialized manage), price had already moved. Also, anti-hedge gate had a bug blocking SELL when any BUY existed.

**How to apply:** Next time we revisit auto-trade logs showing stale decisions, SKIP-vs-hedge thrash, or token blowups, the building blocks are now in place — tune thresholds rather than redesign.

Key changes by file:
- `mt5-core-server/src/services/auto/risk.ts` — Anti-hedge gate rewritten with `.some()`/`.filter()` instead of `symbolPositions[0]` (broker-dependent order bug)
- `mt5-core-server/src/services/auto/deterministicEngine.ts` (NEW) — Pure rule-based decider (<5ms), 4-TF weighted scoring (H4:35/H1:35/M15:20/M5:10), returns `needsLlmSecondOpinion`, plus `marketStateHash()` and `llmCache` (200-entry LRU, 3-min TTL)
- `mt5-core-server/src/services/autoTradingService.ts` — Lowered counter-trend thresh (ctMinConf 70→55, removed hard-SKIP); added deterministic pre-analysis + LLM cache check; removed `cycleBusy` block in `runManage` so manage runs in parallel with cycle; added snapshot invalidation after order placement
- `mt5-core-server/src/services/auto/agentOrchestrator.ts` — Added `prior` param to `reason()` with SHORT PROMPT MODE (~70% token reduction) when deterministic prior supplied; long prompt kept as fallback
- `mt5-core-server/src/services/auto/core/TradeManagementService.ts` — New config knobs (`hedgeTriggerHeatR` 0.6, `scaleWinnerMinProfit` 5, `netBeProfitBand` 2, `hedgedExitMaxHeatR` 0.25) + 3 portfolio-protection rules (proactive HEDGE when heat≥0.6R + market opposes net, SCALE_IN winners when aligned + profit≥$5, CLOSE at net-BE when |net|≤$2 + heat≤0.25R)
- `mt5-core-server/src/services/auto/core/MarketDataService.ts` — 500ms TTL snapshot cache for `fetchAccount`/`fetchPositions` with in-flight dedup; `invalidateSnapshots()` called after writes; added broker-time → `openedAtMs` on PositionRow
- `mt5-core-server/src/services/auto/core/TradingExecutionService.ts` — Calls `invalidateSnapshots()` after successful HEDGE/SCALE_IN/close/modify
- `mt5-core-server/src/services/auto/types.ts` — New config knobs, `openedAtMs` on PositionRow

Backfill race guard: orphan backfill in runManage skips positions younger than 10s — prevents duplicate BACKFILL rows for trades the cycle just opened but hasn't journaled yet (parallel manage + cycle now runs simultaneously).

Build verification: `npx tsc --noEmit` passes in mt5-core-server (2026-04-24).

Config knobs exposed under `config.adaptive.*`:
- `hedgeTriggerHeatR` (0.6) — open proactive hedge at this cluster heat R
- `scaleWinnerMinProfit` (5) — USD floor for scaling into winners
- `netBeProfitBand` (2) — |net pnl| band where hedged book is treated as net-BE
- `hedgedExitMaxHeatR` (0.25) — heat ceiling for the net-BE exit

LLM cache hit path: when `deterministicDecide()` returns high-confidence non-close-call decision + no second-opinion needed, the LLM is skipped entirely (aiDecision marked `_source: 'deterministic'`). When second opinion is needed, short prompt is used to confirm/adjust/override the prior.

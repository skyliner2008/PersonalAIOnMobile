# M15 Wall Scalping Simplification

Date: 2026-05-30

## Goal

Refactor `mt5-core-server` away from the complex multi-system trading stack and make the first recovery runtime intentionally small:

- Decision core: `IndicatorPipeline` + `PriceMap`
- Timeframe focus: merge wall evidence into M15 for short-term trading
- Strategy surface: `SCALPING` only
- Default engine mode: `M15_WALL_SCALPING`
- Wall model: keep HTF walls stable across cycles, then use LTF evidence only as confirmation.

## Runtime Shape

`AutoTradingService` now routes cycles to `SimpleScalpingEngine` when `config.engineMode` is `M15_WALL_SCALPING` or `adaptive.simpleScalping.enabled` is true.

In this mode:

- EventBus stays paused.
- V25 shadow runtime is shut down.
- Legacy manage and learn loops are not scheduled.
- OpenRouter/model sync and AgentCoordinator initialization are skipped.
- Each cycle loads configured TF candles, runs `IndicatorPipeline.processUpdate(..., { emitEvents: false })`, reads the generated PriceMap, and builds a single M15 wall scalp plan.
- Closed MT5 positions are still reconciled in simple mode. `SimpleScalpingEngine` matches missing live tickets against account history, writes close price/profit/R/SL-TP reason back to the journal and decision outcome, and keeps the legacy manage loop disabled.
- `StableWallRegistry` keeps PriceMap walls alive across short raw-cluster gaps. A wall can drift slightly when its raw cluster shifts, but it keeps the same `id` and remains visible for a short grace window unless invalidated by a real break.

## Entry Rules

The planner looks for price near the nearest support/resistance wall:

- BUY: price must be close to a support wall.
- SELL: price must be close to a resistance wall.
- Anchor wall side must be explicit: BUY requires `SUPPORT`, SELL requires `RESISTANCE`; ambiguous `BOTH` walls are skipped until a later confirmation rule is designed.
- Retained `STALE` walls are display/context only. They keep the PriceMap visually stable, but they are no longer allowed as execution anchors.
- Anchor wall must include at least one core wall timeframe: `M15`, `M30`, `H1`, or `H4`.
- `M1` and `M5` are confirmation layers. In live planning, both must show wall touch, reclaim, and directional body; at least one of the two TFs must also show a directional micro-structure break (`breakHigh` for BUY / `breakLow` for SELL) before a trade can execute. Reclaim can complete after a recent wall touch; the entry is still rejected if price has drifted beyond the wall/FVG distance limit.
- Context guard blocks BUY in `TRENDING_DOWN` / `BEAR` and SELL in `TRENDING_UP` / `BULL`, unless the plan is a valid `M15_FVG_MAGNET`.
- Mitigated OB sources may remain on the map as historical context, but they no longer count as fresh OB or OB+FVG confirmation for wall strength.
- Anchor wall must meet `minWallStars`.
- Opposite wall/path target must meet `targetWallMinStars`.
- SL is placed beyond the anchor wall with ATR buffer.
- TP is capped before the target wall with ATR buffer.
- Final plan must meet `minRrr`, `maxRiskAtrMul`, and `minRewardAtrMul`.
- Only one open position per symbol by default.
- Simple-mode loss guard blocks new entries for a symbol after `maxDailyLossesPerSymbol` or `maxConsecutiveLossesPerSymbol` closed losses on the same Bangkok trading day. Defaults are `2` and `2`.

## Playbooks

- `HTF_TREND_RETEST`: trades with the M15 context. A bullish context can buy a tested support wall; a bearish context can sell a tested resistance wall. Counter-context entries are blocked.
- `M15_FVG_MAGNET`: may ignore HTF trend only when there is an unmitigated M15 FVG from the last `3-5` bars in the trade direction target path and M1/M5 confirm the wall reaction. BUY targets a recent bearish M15 FVG above price; SELL targets a recent bullish M15 FVG below price.

## Key Files

- `mt5-core-server/src/services/auto/core/SimpleScalpingEngine.ts`
- `mt5-core-server/src/services/auto/core/SimpleScalpingEngine.test.ts`
- `mt5-core-server/src/services/auto/core/IndicatorPipeline.ts`
- `mt5-core-server/src/services/auto/analyzers/smc/WallRegistry.ts`
- `mt5-core-server/src/services/auto/analyzers/smc/WallRegistry.test.ts`
- `mt5-core-server/src/services/auto/core/PersistenceService.ts`
- `mt5-core-server/src/services/auto/types.ts`
- `mt5-core-server/src/services/autoTradingService.ts`

## Verification

- `npm test -- WallRegistry SimpleScalpingEngine priceMapBuilder journal`
- `npm run build`

## 2026-06-01 Loss Audit Notes

- Fixed the simple-mode journal gap: closed MT5 trades were not reflected in analytics because the legacy manager loop is intentionally off in `M15_WALL_SCALPING`.
- Ticket `164475366` was a formerly rule-valid BUY from a support wall, but it was counter to `TRENDING_DOWN` / `BEAR` context; the new context guard blocks this pattern.
- Ticket `164479736` was bias-aligned SELL, but the anchor wall was `BOTH` / mixed support-resistance evidence and the entry was early before a cleaner resistance rejection; the new wall-side guard blocks this pattern.
- Ticket `164493971` later repeated the same SELL-from-`BOTH` pattern near 4527.75 and is also covered by the wall-side guard.
- R calculation now prefers the broker open/fill price from MT5 history, so slipped fills are audited as actual broker risk instead of planned-entry risk.

## 2026-06-02 Four-Loss Follow-up

- Tickets `164583870` and `164596486` were bias-aligned SELL retests near 4517, but the rejection confirmation was still too permissive. The planner could accept reclaim/body without a local `breakLow`, so entries fired before a true M1/M5 breakdown.
- Ticket `164655314` was a counter-context BUY toward a recent M15 bearish FVG, but the anchor was a retained `STALE` wall (`M1+M15`, 2-star, no OB/FVG). Stable walls should remain visible for context, not act as fresh execution anchors.
- Ticket `164772905` was a RANGING BUY from a 3-star `M1+M5+M15` wall where both M1/M5 showed reclaim, but neither had `breakHigh`. This confirmed a bounce attempt, not a completed reversal.
- Fix: execution now rejects `STALE` anchors, requires both M1 and M5 to reclaim with directional body, requires at least one M1/M5 local structure break in the trade direction, checks wall distance before LTF confirmation for clearer logs, and adds a simple loss guard so two same-day losses on a symbol pause further simple scalps for that symbol.

## 2026-06-02 Latest Log Follow-up

- Latest `log.txt` from 2026-06-02 00:08:44-01:57:40 BKK had 109 logged simple cycles, 0 executed orders, 109 skipped/blocked, and no runtime errors.
- Rejection mix: `LTF_CONFIRMATION_MISSING` 49, `SCALP_CONTEXT_GUARD` 42, `WALL_SIDE_MISMATCH` 18. `SIMPLE_LOSS_GUARD` did not trigger.
- Most LTF blocks had M1/M5 touch/reclaim evidence but no completed micro-break on both TFs. The confirmation rule was adjusted so both TFs still need reaction evidence, but only one of M1/M5 needs the directional micro-break.

## 2026-06-02 14:00 Log Follow-up

- Latest `log.txt` through 2026-06-02 14:00:29 BKK had 246 logged simple cycles, 0 executed orders, 224 blocked, 22 no-signal, and no runtime errors.
- Log confirms the updated runtime is active because skip reasons include `LTF combined: reaction=... anyBreak=...`.
- The remaining no-order behavior is mostly valid: many BUY attempts were already 2.5-4.9 points away from the support wall while `entryProximityAtrMul` allowed about 1.7-1.8 points, and nearby SELL attempts still lacked bearish body/break confirmation.
- Adjustment: reclaim now accepts a recent wall touch followed by a close back on the correct side, but proximity is checked before LTF confirmation so future logs distinguish `WALL_DISTANCE_TOO_FAR` from genuine `LTF_CONFIRMATION_MISSING`.

## Next Notes

The next improvement should be based on decision-feed evidence from this simplified mode, not by re-enabling old gate layers. Tune only the simple settings first: wall stars, ATR proximity, SL/TP buffers, RRR floor, and max position cap.

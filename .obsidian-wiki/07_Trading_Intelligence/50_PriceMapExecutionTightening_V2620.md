# V26.20 - PriceMap Execution Tightening

Date: 2026-05-21

## Problem

The 2026-05-21 live log showed that AutoEngine could still enter bad locations after the strategy label looked valid:

- `BREAKOUT` could bypass Proximity Gate even while H4 was `RANGING`, causing a BUY directly under resistance.
- Unified `V25_PB1_TREND_TOUCH` could start with strong proof, then pass final execution after SL/TP adjustment with final RRR near `1.09`.
- Winning XAU trades around `0.4R-0.5R` were not always moved to breakeven before price rotated back.
- Fast scalp/V25/breakout losers waited too close to full SL even after the original proof failed.

## Change

- Final RRR validation now uses one execution floor after PriceMap TP cap, entry drift, and the hard risk gate. V25/scalp strategies no longer pass just because the old scalp floor was `1.0R`.
- Unified V25 candidate selection now checks live tick preflight with `liveMarketRiskIssue()` and `executableMinRrrForPlan()`. PB1 wall-touch candidates expire after `30s` by default.
- Breakout/continuation proximity bypass now requires H4 to be genuinely trending or volatile. H4 `RANGING` keeps the wall proximity check active.
- Trade management moves XAU positions to breakeven from `0.4R` by default, with V25/scalp strategies included in the early BE path.
- Early invalidation reduces at `-0.45R` and closes at `-0.65R` after `90s` when V25/scalp/breakout/mean-reversion proof fails.

## Config

- `adaptive.earlyBreakevenR` default `0.4`
- `adaptive.enableEarlyInvalidation` default `true`
- `adaptive.earlyInvalidationR` default `-0.45`
- `adaptive.earlyInvalidationCloseR` default `-0.65`
- `adaptive.earlyInvalidationMinAgeMs` default `90_000`
- `adaptive.v25.unifiedPb1CandidateTtlMs` default `30_000`

## Verification

- `npm test -- --run src/services/auto/core/TradeManagementService.test.ts`
- `npm run build`

## Related

- [[51_EaOnlyExecutionHardening_V2621]]
- [[49_UnifiedV25DecisionPath_V2619]]
- [[48_ExecutionAwareMtfWeighting_V2618]]
- [[45_SlippageGuard_AnalyticsImprovement_V2615]]

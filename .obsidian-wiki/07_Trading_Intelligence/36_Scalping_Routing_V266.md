# V26.6 Scalping Routing

Date: 2026-05-18

## Problem

The new `SCALPING` analyzer path was visible inside M5/M15 analysis, but it did not become the top-level executable strategy in the decision feed. Ranging markets were still routed to `SMC_FVG_SCALP`, which then inherited stricter FVG/Zone gates.

In the 2026-05-18 decision log:
- Closed trades: 5 total, all `BREAKOUT`
- Top-level `SCALPING`: 0 executions
- `SMC_FVG_SCALP`: 25 decisions, 0 executions
- Scalp blocks included `Zone-Aware Gate`, `MTF zone gate`, and `AI_UNAVAILABLE_EA_FALLBACK_BLOCKED: RRR 1.26 < 1.5`

## Change

- `deterministicEngine.ts` now promotes valid M5/M15 `SCALPING` setups to the executable strategy.
- `DeterministicDecision` now carries `strategy`, so downstream fallback/fast-path decisions do not fall back to the broader H1 `RANGE` label.
- `autoTradingService.ts` now uses a shared scalp-aware RRR helper:
  - `SCALPING`
  - any strategy containing `SCALP`
  - `MEAN_REVERSION`
- EA fallback now applies `strategy.scalpingRRR` to scalp-like strategies instead of the global `strategy.minRRR`.
- `hasContinuationIntent()` now recognizes generic `SCALP` intent so Zone-Aware Gate can evaluate local execution context instead of treating scalps as non-continuation noise.
- `AnalysisSummary.candles` is now declared in `types.ts` because SignalDetector and MTF gates already use it at runtime.

## Safety

This does not disable Zone-Aware Gate. Scalp entries still need the existing local evidence stack:
- execution-TF premium/discount relief,
- aligned FVG or SMC support,
- wall fade support,
- or strong confluence.

The main effect is that real `SCALPING` no longer gets hidden behind `SMC_FVG_SCALP` and does not inherit a global `1.5` RRR floor when `strategy.scalpingRRR` is configured lower.

## Validation

- `npm test -- --run src/services/auto/deterministicEngine.test.ts src/services/auto/core/ZoneAwareGate.test.ts`
- `npm run build`

## Related

- [[34_ZoneAwareGate_MTF_LocalOverride_V264]]
- [[35_StaleStop_RsiDivergence_V265]]

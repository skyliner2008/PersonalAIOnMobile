# V26.19 - Unified V25 Decision Path

Date: 2026-05-20

## Problem

V25/PB1 had become a second trading system. PB1 could generate a good wall-touch scalp candidate, but live execution still depended on the separate `enableV25Only` path while AutoEngine could independently place a `BREAKOUT` order from the deterministic/EA route.

That created two failure modes:

- A local-proof PB1 BUY could remain only a V25 candidate while AutoEngine sold from HTF breakout context.
- Turning on `V25 Only` stopped normal AutoEngine orders, but did not make PB1 part of the same final decision tree.

## Change

V25 playbooks now feed AutoEngine as local-proof candidates instead of acting as a separate default order sender.

- `autoTradingService.ts` reads fresh `playbookSelector.recentCandidates(symbol)` during deterministic pre-analysis.
- A valid fresh V25 candidate can replace the deterministic prior with its own side, SL, TP, RRR, strategy, and M1 timeframe.
- V25 strategies are treated as local-proof scalp strategies, so HTF context gates do not re-block them as generic counter-trend trades.
- `playbooks/index.ts` defaults to candidate-only unified mode. Legacy V25 direct execution requires explicit `legacyDirectExecution=true` plus the old `enableV25Only=true`.
- The dashboard no longer shows the `V25 Only` toggle. `V25 Adaptive` is the switch for the realtime wall/proof engine.

## Follow-Up From Latest Log

The 2026-05-20 latest log confirmed unified mode was active:

- PB1 emitted `candidate - unified pipeline`.
- AutoEngine consumed it as `V25_PB1_TREND_TOUCH`.

Two downstream legacy behaviors were tightened:

- Deterministic fast-path now carries PB candidate `sl` and `tp` into `aiDecision`, so the bracket does not fall back to generic SMC TP/SL.
- V25 selector skips the old AI Bias/Decision mandate gate in unified candidate mode; AutoEngine is now the single arbiter for V25 side conflicts.
- PriceMap TP-cap RRR floor now treats `V25_*` as scalp-like, using the scalp floor instead of the generic swing `minRRR`.

## Intent

There should be one live execution authority: AutoEngine.

V25 is now a proof provider inside that authority. If PB1 sees a support-wall BUY with valid local proof, that proof can override or veto an HTF-driven `BREAKOUT SELL` inside the same pipeline.

## Verification

- `npm test -- --run src/services/auto/deterministicEngine.test.ts`
- `npm test -- --run src/services/auto/core/SignalDetector.test.ts`
- `npm run build`

## Related

- [[48_ExecutionAwareMtfWeighting_V2618]]
- [[47_BreakoutLocalConfirmationGuard_V2617]]
- [[25_RealTimeWallEngine_V25]]

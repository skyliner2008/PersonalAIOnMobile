# V26.21 - EA-Only Execution Hardening

Date: 2026-05-22

## Problem

The 2026-05-21/2026-05-22 audit showed that losses were not caused by win rate alone. The real failure pattern was small wins versus full losses:

- 2026-05-21 closed `22` trades, net `-$117.36`, win rate `50%`, but average win was much smaller than average loss.
- Most losing trades never reached meaningful positive excursion before hitting full SL.
- `MEAN_REVERSION` could bypass Proximity Gate because the rationale contained `MTF-ALIGNED`, even when the entry was no longer close enough to a PriceMap wall.
- After a same-side TP, the engine could re-enter near the just-paid target/support area before the market reset.
- EA-only mode allowed deterministic signals from the old `35%` threshold, while no AI supervisor was active to reject weak execution context.
- Early invalidation existed in the planning service, but the live manager loop did not run that protection continuously.
- Partial close markers were written as free text, so the same `1R` partial could repeat on the same ticket.

## Change

- EA-only deterministic fallback now defaults to `adaptive.eaOnlyMinConfidence = 55`.
- EA-only entries are blocked when both `M1` and `M5` strongly oppose the selected side via `LTF_CONSENSUS_GUARD`.
- `MEAN_REVERSION` can no longer bypass Proximity Gate through generic continuation text such as `MTF-ALIGNED`; it must stay near a valid wall.
- Order preflight now blocks same-side re-entry near a recent TP close with `POST_TP_COOLDOWN`.
- The live manager loop now runs early invalidation directly: reduce at `-0.45R`, close at `-0.65R`, only after `90s` and only if peak progress stayed below `0.25R`.
- `PARTIAL_CLOSE` now writes stable markers (`PARTIAL_1R`, `PARTIAL_2R`) so staged partials do not repeat on the same ticket.

## Config

- `adaptive.eaOnlyMinConfidence` default `55`
- `adaptive.enableEaOnlyLtfConsensusGuard` default `true`
- `adaptive.ltfConsensusGuardMinConfluence` default `55`
- `adaptive.postTakeProfitCooldownMs` default `1_800_000`
- `adaptive.postTakeProfitReentryMinDistanceXAU` default `6.0`
- `adaptive.earlyInvalidationMaxPeakR` default `0.25`

## Verification

- `npm test`
- `npm run build`
- `npm run analyze 2026-05-21`
- `npm run analyze 2026-05-22`

## Related

- [[50_PriceMapExecutionTightening_V2620]]
- [[48_ExecutionAwareMtfWeighting_V2618]]
- [[40_MeanReversion_OrderGuard_V2610]]

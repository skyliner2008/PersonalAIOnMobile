# V26.7 Scalp TP/SL Bracket Rebalance

Date: 2026-05-18

## Problem

Live `SMC_FVG_SCALP` orders started executing well after V26.6, but the bracket shape was not ideal:

- TP often stayed near the minimum scalp reward (`1.2R`).
- SL inherited wider FVG/ATR structure.
- PriceMap could cap TP closer, while SL remained wide.

Example pattern from 2026-05-18:
- XAUUSD SELL around `4541.45`
- SL around `4546.66`
- TP around `4535.19`
- RRR around `1.2`

This works, but it is not an efficient scalp bracket: the stop is too wide for the intended quick fill.

## Change

- `SMC_FVG_SCALP` target RRR is now at least `1.35R`.
- Compact scalp bracket no longer requires `v25.enableV25Only`; it applies to normal V24/V26 order flow too.
- XAUUSD compact scalp risk default changed:
  - minimum risk: `3.0` points,
  - max risk guidance: `0.16 * ATR`.
- The compactor now tightens SL first and keeps the existing TP if that TP is already farther than the minimum required reward.

## Expected Effect

- Smaller SL for scalp entries.
- Better reward-to-risk without forcing overly distant TP.
- PriceMap-capped TP can still pass if the tightened SL keeps RRR acceptable.
- Duplicate and sequential gates still prevent order spam.

## Validation

- `npm run build`
- `npm test -- --run src/services/auto/deterministicEngine.test.ts src/services/auto/core/ZoneAwareGate.test.ts`

## Related

- [[36_Scalping_Routing_V266]]
- [[34_ZoneAwareGate_MTF_LocalOverride_V264]]

# V26.23 Entry Drift Synced Decision Price

Date: 2026-05-22
Status: Production

## Problem

The 2026-05-22 log showed several cases where the pipeline reached a valid execution setup near a PriceMap wall, but the final `ENTRY_DRIFT` gate blocked the order even though AutoEngine had already synced the decision price to the live tick/broker price.

Example pattern from the log:

- Proximity Gate passed near a 5-star resistance wall.
- Zone-Aware Gate allowed the selected side.
- AutoEngine logged `Decision price synced: candleClose=4525.36 -> 4520.xx`.
- The final drift gate still reported `decision=4525.36`, so the order was treated as if price had drifted more than 2-5 points.

Root cause:

- The drift/slippage guard compared final entry against `originalLastClose`.
- `originalLastClose` is intentionally retained for audit, but it is stale after the earlier V26.14 tick/broker price sync updates `last.c`.
- This created false drift blocks after valid wall-touch / wall-reaction conditions.

## Change

- `autoTradingService.ts` now uses the synced live decision price for the drift guard:
  - Prefer `last.c` after AutoEngine's decision-price sync.
  - Fall back to `originalLastClose` only if `last.c` is not finite.
- Added an inline V26.23 comment at the guard so future edits do not accidentally reintroduce stale candle-close comparison.
- `IndicatorPipeline.ts` now logs PriceMap counts as separate overlay, ladder-position, and execution-side counts. This removes the confusing mismatch where the printed ladder could show more above-price walls than the old `resistance/support` summary implied.

## Strategy Impact

- Valid wall-proximity and Zone-Aware-approved setups should no longer be blocked just because the previous candle close was far from the live tick.
- `MEAN_REVERSION`, `SCALPING`, and V25/local-proof scalp paths still keep the same drift/RRR protection, but the comparison anchor is now correct.
- Real slippage remains blocked when the executable entry moves too far from the actual synced decision price and final RRR degrades below the strategy floor.

## Verification

- `npm run build`
- `npm test`
- `npm run analyze 2026-05-22`

Note: historical analytics still report the older blocked rows until new cycles are written after this patch.

## Related

- [[52_PriceMapWallOverlayStability_V2622]]
- [[50_PriceMapExecutionTightening_V2620]]
- [[45_SlippageGuard_AnalyticsImprovement_V2615]]
- [[44_WallBreakScalp_FreshDecisionPrice_V2614]]

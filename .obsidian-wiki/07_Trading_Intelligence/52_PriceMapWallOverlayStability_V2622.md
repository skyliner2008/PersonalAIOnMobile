# V26.22 PriceMap Wall Overlay Stability

Date: 2026-05-22
Status: Production

## Problem

The 2026-05-22 log showed that PriceMap walls were still too sensitive to the current tick price. In the 10:24-11:30 window, price moved from about `4519` to `4532`, but the wall ladder changed even when higher-timeframe context had not materially changed.

Most visible example:

- `10:26:03` PriceMap: `11 resistance, 8 support`; `PRICE: 4520.56`; no `4520.53` wall displayed.
- `10:27:03` PriceMap: `11 resistance, 9 support`; `PRICE: 4521.02`; new wall displayed as `4520.53 ***** M1+M5+M15+M30+H1+SESSION+SWING`.
- Context levels were effectively unchanged at that moment: Pivot `4525.58`, VWAP `4522.44`, Fib swing `[4488.71, 4547.06]`.

Root causes:

- PriceMap dropped raw SMC/context levels when `abs(level.price - currentPrice) < minDistanceAtrPct * ATR`. A valid wall could disappear exactly when price touched it, then reappear one tick later.
- Resistance/support lists were filtered by `currentPrice`, so a wall could move lists before there was a meaningful close-confirmed break.
- Clustering used the previous sorted level as the join anchor. This allowed chain-clustering, where one newly included M1/context level could merge a wider group and move the displayed wall.
- Context sources (`PIVOT`, `VWAP`, `FIB`) could affect side/stars too much. They are useful confluence, but they should not create or drag a structural MTF wall by themselves.

## Change

- PriceMap now keeps levels near current price. It only applies the max-distance guard, not a min-distance removal guard.
- `walls` now contains the full stable overlay map instead of only the currently above/below subsets.
- `formatPriceMap()` now renders the visible ladder strictly by price position: walls above `PRICE` go in `RESISTANCE`, walls at/below `PRICE` go in `SUPPORT`.
- `resistanceWalls` and `supportWalls` keep the execution-facing above/below side filters, while `walls` remains the stable full overlay map.
- Cluster joins now compare against a structural-aware weighted cluster anchor, not the immediately previous sorted level.
- Wall price anchoring is weighted by timeframe so larger TFs are steadier than M1 noise:
  - `H4 > H1 > M30/M45 > M15 > M5 > M1`
  - `SWING`/`D1` can anchor context-only walls, but context is ignored for anchor when structural sources exist.
- Stars are now scored from structural TF overlap first. Context can boost a real structural wall, but context-only overlap is capped at `2` stars.
- Wall side is derived from structural sources when present, so VWAP/Fib side flipping around the tick price cannot flip a real SMC wall.

## Strategy Impact

- `ProximityGate` should no longer see a valid wall disappear while price is testing it.
- V25 Wall State Machine receives steadier wall prices and fewer false state changes from context drift.
- `SMC_WALL_BREAK_SCALP` and `SMC_FVG_MAGNET_SCALP` get a more reliable wall anchor for touch/reclaim logic.
- TP path analysis still sees opposing walls by filtering `resistanceWalls/supportWalls` by entry price, but the underlying wall map is more stable.

## Verification

- `npm test -- --run src/services/auto/analyzers/smc/priceMapBuilder.test.ts`
- `npm run build`
- `npm run analyze 2026-05-22`

## Related

- [[51_EaOnlyExecutionHardening_V2621]]
- [[50_PriceMapExecutionTightening_V2620]]
- [[43_PriceMap_WallAnchorStability_V2613]]

# V26.13 PriceMap Wall Anchor Stability

Date: 2026-05-19
Status: Production

## Why

Wall levels sometimes changed even when current price had not directly interacted with that wall. The main reason was that PriceMap clusters SMC/structure levels together with dynamic context levels:

- Pivot (`D1`)
- VWAP (`SESSION`)
- Fibonacci (`SWING`)

Those context levels are useful for confluence, but VWAP/Fib can drift as the input window updates. Before V26.13, they could also move the displayed wall anchor because the wall price was calculated from the median of all sources in the cluster.

## Change

When a cluster contains structural/SMC sources, those sources now own:

- `price`
- `priceTop`
- `priceBottom`

Context sources still remain in the wall source list, contribute timeframes such as `SESSION` or `SWING`, and can increase stars. If a wall is context-only, it still behaves as before.

## Interpretation

Example:

```text
4589.28 ★★★★  M1+M5+M15+M30+H1+H4
```

If `VWAP` or `Fib 0.618` drifts close enough to this cluster, it may strengthen the wall or add `SESSION/SWING`, but it should not pull the wall price away from the structural anchor.

## Strategy Impact

- `ProximityGate`: fewer false changes in nearest wall price.
- `SMC_FVG_MAGNET_SCALP`: wall touch/reclaim anchor is more stable.
- V25 playbooks: SL/entry placement using `wall.priceTop/priceBottom` is less likely to widen because of context-only drift.
- `PathAnalysis`: obstacle/TP wall anchors better represent actual SMC structure while still honoring context confluence.

## Verification

- `npm test -- --run src/services/auto/analyzers/smc/priceMapBuilder.test.ts`
- `npm run build`


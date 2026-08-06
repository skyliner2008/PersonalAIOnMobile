# V26.9 Wall-to-FVG Magnet Scalp

Date: 2026-05-18

## Intent

`SMC_FVG_MAGNET_SCALP` is a separate scalp playbook for the user's wall-to-FVG idea:

1. Price is near a 3+ star wall from the normal PriceMap wall engine.
2. The latest M15 structure has an unmitigated FVG on the other side of that wall path.
3. M1 or M5 confirms reclaim/micro-break after the wall touch.
4. The trade targets that FVG edge, not a higher-timeframe zone thesis.

This is not the same as `SMC_FVG_SCALP`. `SMC_FVG_SCALP` remains the strict FVG-fill strategy that requires current price to be inside an aligned filled FVG.

## Entry Logic

- BUY: use a nearby support wall, require an unmitigated M15 bearish FVG above price/wall, then require M1/M5 wall reclaim or break above the local micro high.
- SELL: use a nearby resistance wall, require an unmitigated M15 bullish FVG below price/wall, then require M1/M5 wall reclaim or break below the local micro low.
- Minimum wall strength defaults to `3` stars.
- Wall distance defaults to `0.35 * ATR` with a small price and wall-width floor.
- Test coverage explicitly verifies both BUY and SELL mirror paths.

## Gate Policy

The playbook intentionally bypasses:

- HTF bias/context veto.
- Pre-AI MTF Zone Gate.
- Post-AI Zone-Aware Gate.
- Counter-trend block caused only by H4/H1 disagreement.

It still keeps:

- Wall Proximity Gate.
- Spread-aware TP adjustment.
- Sequential/duplicate and hard-risk guards.
- Broker stop-side sanity checks.

## TP/SL Policy

- TP is the M15 FVG edge minus spread for BUY, plus spread for SELL.
- SL is recalculated from the remaining TP reward: `risk = reward / targetRrr`.
- Current target RRR floor is `1.35R` for this strategy.
- TP rescue, compact scalp bracket, and global TP cap do not alter this strategy's FVG target.

## Files

- `mt5-core-server/src/services/auto/core/SignalDetector.ts`
- `mt5-core-server/src/services/auto/core/SignalDetector.test.ts`
- `mt5-core-server/src/services/auto/core/IndicatorPipeline.ts`
- `mt5-core-server/src/services/autoTradingService.ts`
- `mt5-core-server/scripts/advanced_analytics.mjs` uses Asia/Bangkok day boundaries for date-specific order audits.

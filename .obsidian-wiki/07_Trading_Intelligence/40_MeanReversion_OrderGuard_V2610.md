# V26.10 Mean Reversion Order Guard

Date: 2026-05-19

## Audit

`npm run analyze 2026-05-18` showed the trading day was net positive, but `MEAN_REVERSION` was the weak bucket:

- Total `MEAN_REVERSION`: 14 trades
- Win rate: 57.1%
- Net PnL: -$42.75
- Worst pattern: repeated SELL entries around the same price with TP stretched to roughly 4R-6R.

`npm run analyze 2026-05-19` showed 3 `SCALPING` trades and no executed `MEAN_REVERSION` trades at the time of review.

## Fix

`MEAN_REVERSION` now has two additional controls:

- `adaptive.meanReversionDuplicateIntentTtlMs` defaults to `900_000` ms, while the normal duplicate TTL remains `300_000` ms.
- `adaptive.meanReversionMaxRrr` defaults to `1.8`, capping TP distance after scalp bracket compaction.

This keeps mean reversion as a nearby snap-back play instead of letting it behave like a swing target with a compact SL.

## Files

- `mt5-core-server/src/services/autoTradingService.ts`
- `mt5-core-server/src/services/auto/types.ts`
- `mt5-core-server/src/services/auto/core/PersistenceService.ts`
- `README.md`
- `mt5-core-server/README.md`
- `.obsidian-wiki/07_Trading_Intelligence/Trading_Intelligence_MOC.md`

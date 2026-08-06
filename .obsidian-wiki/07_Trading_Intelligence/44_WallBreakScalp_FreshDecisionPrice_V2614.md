# V26.14 Wall Break Scalp + Fresh Decision Price

Date: 2026-05-19
Status: Production

## Why

Recent XAUUSD logs showed PriceMap rendering the live price near a 5-star wall while AutoEngine still made decisions from the active H1 candle close. That caused the deterministic layer to keep selecting `MEAN_REVERSION` and miss fast wall-touch scalp setups.

The user also wanted a non-FVG scalp path: if price is near a strong wall and both M1 and M5 break/reclaim in the right local Premium/Discount zone, the system should be able to scalp without waiting for an M15 FVG target.

## Change

### `SMC_WALL_BREAK_SCALP`

New local playbook in `auto/core/SignalDetector.ts`:

- BUY requires a 3+ star support wall near price.
- SELL requires a 3+ star resistance wall near price.
- M1 and M5 must both confirm wall break/reclaim.
- BUY requires both M1 and M5 in `DISCOUNT`.
- SELL requires both M1 and M5 in `PREMIUM`.
- M15 FVG is not required.

This is intentionally separate from `SMC_FVG_MAGNET_SCALP`, which still requires an unmitigated M15 FVG target.

### Fresh Decision Price

AutoEngine now syncs `last.c` before deterministic strategy selection and gates:

1. Fresh V25 tick mid, when available and younger than 10 seconds.
2. Broker symbol bid/ask mid.
3. Broker bid or symbol price.
4. Candle close fallback.

Decision logs now include:

- `originalLastClose`
- `decisionPriceSource`
- `decisionPriceAgeMs`

## Strategy Impact

- `SMC_WALL_BREAK_SCALP` can override deterministic `MEAN_REVERSION` when local wall-break proof is present.
- The new playbook bypasses HTF zone/context vetoes like other local proof scalp entries, but still keeps spread, duplicate, proximity, and hard-risk protections.
- `SMC_FVG_MAGNET_SCALP` remains the stricter wall-to-M15-FVG magnet strategy.
- Logs should no longer show decision `price` stuck at an old H1 close while PriceMap shows the current tick near a wall.

## Verification

- `npm test -- --run src/services/auto/core/SignalDetector.test.ts`
- `npm run build`


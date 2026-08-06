# V26.17 Breakout Local Confirmation Guard

Date: 2026-05-20
Status: Production

## Why

Recent logs showed deterministic repeatedly selecting `BREAKOUT SELL` while M15 and M5 were already bullish. The SELL was not truly high-quality; it came from the side score weighting:

- H4: 35%
- H1: 35%
- M15: 20%
- M5: 10%

With H4 and H1 bearish, the engine could select SELL even when M15/M5 were warning of local reversal. The late `M15 SMC Pressure Guard` blocked the trade, but that was a downstream fix. The strategy-selection layer still kept saying `BREAKOUT SELL`.

## Change

`auto/deterministicEngine.ts` now adds a local breakout confirmation guard:

- If `BREAKOUT SELL` is selected but M15 and M5 both oppose as bullish, deterministic returns `SKIP`.
- If `BREAKOUT BUY` is selected but M15 and M5 both oppose as bearish, deterministic returns `SKIP`.
- SELL in `DISCOUNT` and BUY in `PREMIUM` need M15/M5 confirmation before becoming actionable.
- Rationale becomes `Breakout wait` instead of `MTF-aligned Short/Long`.

## Interpretation

This does not disable breakout trading. It only prevents HTF-only breakout entries when the execution layers are already warning against the side.

For example, this should now wait:

```text
H4 BEAR VOLATILE_BREAKOUT
H1 BEAR VOLATILE_BREAKOUT
M15 BULL RANGING
M5 BULL TRENDING_UP
Action: SELL
Zone: DISCOUNT
```

This can still pass:

```text
H4 BEAR VOLATILE_BREAKOUT
H1 BEAR VOLATILE_BREAKOUT
M15 BEAR TRENDING_DOWN
M5 BEAR TRENDING_DOWN
Action: SELL
```

## Verification

- `npm test -- --run src/services/auto/deterministicEngine.test.ts`
- `npm run build`


# P3.1 Pine V11.29 Fixture Harness

This directory is the empirical-parity boundary for the SMC liquidity port.

## Fixture contract

Use `pine_v11_29_liquidity_fixture.json` as the schema/template. Export **the exact same candle stream** from TradingView/Pine V11.29 and populate `referenceSnapshots` with the visible liquidity levels after every bar.

Each snapshot must contain:

- `barIndex`: zero-based candle index.
- `zones`: the Pine-visible liquidity zones after that bar.
- `price`: exact Pine level as exported (do not round for the fixture).
- `isHigh`: `true` for EQH/buy-side liquidity and `false` for EQL/sell-side liquidity.
- `strength`: Pine touch/strength value represented by the reference output.

The Kotlin harness replays `candles[0..barIndex]` for every reference snapshot and compares the complete visible zone list. A mismatch reports the exact bar, expected zones, and actual Kotlin zones.

## How to use

1. Export OHLCV candles from TradingView using the same symbol, timeframe, session and history window used by the Kotlin test.
2. Export/capture the Pine V11.29 liquidity output after each bar. Preserve ordering and exact prices.
3. Replace the template contents with that fixture.
4. Point a test at the fixture JSON using `PineLiquidityFixtureHarness.parse(...)` and call `assertMatches(...)`.
5. Keep the fixture committed so regressions become deterministic and reviewable.

## Important

The template is intentionally **not** an empirical validation fixture. Until a real TradingView/Pine export is inserted and the harness passes, the project must continue to report **algorithmic/regression parity**, not empirical 1:1 Pine parity.

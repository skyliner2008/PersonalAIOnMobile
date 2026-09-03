# SMC Pine V11.29 Port Status

Updated: 2026-08-23

## Source of truth
`SMC_Multi-TF_Order_Blocks_Sweeps_V11_29.txt`

## Current architecture
- Primary SMC analysis timeframe defaults to **M15** when `trading_smc_analysis` does not receive an explicit `interval`.
- Explicit timeframe overrides remain supported.
- MTF liquidity snapshot scans M1/M5/M15/M30/M45/H1/H4/D1/W1.
- Liquidity semantics: EQH = buy-side liquidity above price; EQL = sell-side liquidity below price.
- Liquidity presentation is spatial: levels are separated by current price instead of being ranked globally by strength.
- TradingView strict-source guards remain enabled for the Live trading path.

## Important compatibility boundary
The Kotlin MTF liquidity detector now replays the bounded candle history in Pine's temporal order and implements the V11.29 liquidity lifecycle: persistent-style newest-first zones, sweep tombstones, re-detection blocking, tombstone age-out, persistent-pool cap, and newest-first visible output. This is a deterministic reconstruction of the Pine `var` array rather than a cross-call mutable store; with the current lookback=10 and a 150-bar fetch, the 20-bar tombstone lifetime is fully reconstructable.

The remaining parity boundary is empirical: Kotlin still needs comparison against TradingView/Pine output on identical candle fixtures, especially around exact equal-H/L pairing, pivot timing, and MTF merge ordering. The Kotlin merge now mirrors Pine `f_mergeLevels` by constructing all clusters before trimming the farthest clusters from current price. Do not claim mathematical 1:1 parity until those fixtures pass.

## P3 Trading Intelligence wall layer
- **P3.2 OB Wall:** active bullish/bearish Order Blocks are normalized into deterministic `SmcWall` objects.
- **P3.3 FVG Wall:** active bullish/bearish FVGs are normalized into the same wall model.
- **P3.4 Confluence:** OB/FVG overlap, nearby liquidity stars, wall proximity, and wall presence are combined into a bounded 0-10 side score. The raw wall score remains bounded 0-5.
- `SmcSnapshot` now exposes `walls` and `confluence` so downstream APS/signal scoring can consume one normalized decision layer instead of reimplementing zone matching.
- These wall/confluence semantics are an application decision layer, not a claim of Pine V11.29 empirical parity. Fixture validation remains required for any strict 1:1 claim.

## Latency policy
SMC/MTF calculation latency around ~9 seconds is currently accepted as a correctness trade-off. Optimization should target redundant pre/post-analysis waiting rather than removing MTF data required by the Pine logic.

## P3.1 empirical fixture harness
A reusable `PineLiquidityFixtureHarness` now exists in `commonTest`. It defines a JSON fixture contract containing exact candles plus the Pine-visible liquidity snapshot after every bar, replays each candle prefix through the Kotlin detector, and reports bar-level expected-vs-actual mismatches. A fixture template and usage contract live under `docs/fixtures/`.

The harness itself is regression-tested, including a negative mismatch case. The real TradingView/Pine V11.29 export is still pending, so empirical 1:1 parity remains **not yet claimed**.

## Validation policy
Do not use this document as a claim that the latest build is fully validated. A consolidated compile/unit/integration/logcat test pass is still required after the current development batch.

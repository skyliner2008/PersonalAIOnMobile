---
name: V24.2.0 Context Levels (Pivot+VWAP+Fib)
description: Added Daily Pivot Points, Session VWAP (NY anchor), Auto-Fibonacci as PriceMap confluence layers — addresses Top-3 indicator gaps from 2026-05-08 audit
type: project
originSessionId: 4b39e66c-57a7-4a6f-bfe4-7f7f7b4c8e13
---
V24.2.0 Context Levels — `mt5-core-server`, 2026-05-08

**Why**: Indicator coverage audit (2026-05-08) found system relied on SMC + classical only. Pro retail traders use Pivot Points, VWAP, and Fibonacci as standard confluence layers. Adding these closes ~30% of the indicator gap with minimal code (~250 LOC).

**How to apply**: When debugging entries, check the new `Context levels` log line per cycle. When TP/SL placement seems off, verify whether Pivot R1/S1, VWAP, or Fib 0.618 should have shifted the level.

**Files added/changed**:
- `mt5-core-server/src/services/auto/analyzers/levels.ts` (NEW) — pure calculator module:
  - `computePivotLevels(d1Ohlc)` — Standard Floor Pivot (P/R1-3/S1-3)
  - `deriveDailyOhlcFromIntraday(candles, hours, tfMinutes)` — derives D1 OHLC from H1 24-bar window (no extra MT5 fetch needed)
  - `computeSessionVwap(intradayCandles, anchorHourUtc=13)` — NY equity-open anchored VWAP + ±1σ bands
  - `computeFibLevels(swingHigh, swingLow)` — 0.236/0.382/0.5/0.618/0.786 retracements + 1.272/1.618 extensions
  - `buildContextLevels({...})` — aggregator → returns `ContextLevel[]` with weight 1-3
- `mt5-core-server/src/services/auto/analyzers/smc/types.ts` — extended `WallSourceType` with `'PIVOT' | 'VWAP' | 'FIB'`; added optional `label?` and `weight?` to `WallSource`
- `mt5-core-server/src/services/auto/analyzers/smc/priceMapBuilder.ts` — added `ExtraContextLevel` type; `buildPriceMap()` and `buildPriceMapFromRecord()` accept new `extraLevels` param; `buildWall()` adds +1★ when cluster contains a high-weight context (Pivot R1/S1/P, VWAP center, Fib 0.618)
- `mt5-core-server/src/services/auto/analyzers/smc/index.ts` — re-exports `ExtraContextLevel`
- `mt5-core-server/src/services/auto/core/IndicatorPipeline.ts` — new private `buildContextLevelsFor()` computes context per cycle + passes to `buildPriceMapFromRecord(...)`. Logs compact diagnostic per cycle: `Context levels — Pivot P=4714.67 R1=4746.33 S1=4689.33 | VWAP=4719.30 dev=+1.85 (47 bars) | Fib swing=[4683, 4740] 0.618=4704.77`

**Weight rules** (in `levels.ts:buildContextLevels`):
- Pivot: P/R1/S1 = 3, R2/S2 = 2, R3/S3 = 1
- VWAP: center = 3, ±1σ = 2
- Fib: 0.618 = 3, 0.5/0.382 = 2, 0.236/0.786 = 1, extensions = 2

**Confluence stars boost**: when a context level (weight≥3) clusters with SMC OB/FVG/Liquidity, the wall gets +1★ (e.g., 3★ wall + Pivot R1 nearby → 4★ wall).

**Effect on existing systems**:
- ProximityGate, ZoneAwareGate: see stronger walls → more accurate "near support/resistance" detection
- TP cap (V24.1.3 `analyzePath`): uses suggestedTP (before first 3★+ wall) — context levels make 3★+ walls appear more often → tighter, more realistic TPs
- BE/Trail: untouched (uses fixed multipliers), but stale positions identified earlier when price camps near Pivot/VWAP

**Verification**: `npx tsc --noEmit` on Windows host. Bash mount in this session is frozen snapshot, can't verify there.
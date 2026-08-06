# V26.24 Gate Strategy Runtime Controls

Date: 2026-05-23
Status: Production

## Problem

The latest log still showed zero executed orders even when deterministic setup quality looked acceptable. The repeated late-session pattern was:

- `TRENDING_DOWN` / `TREND_FOLLOW`
- Deterministic `SELL` confidence around `54-55.6%`
- Entry price around `4512-4514`
- `Pre-AI MTF zone gate` blocked every SELL because H4/H1 were both in `DISCOUNT`

That gate is protective by default, but it also made live tuning difficult because the operator could not temporarily disable a specific gate from the Auto Trade UI to validate whether a blocked setup was genuinely bad or simply over-filtered.

## Change

- Added `adaptive.gateToggles` with per-gate switches.
- Added `adaptive.strategyToggles` with per-strategy switches.
- Added Auto Trade UI sections:
  - `Execution Gates`
  - `Strategy Switches`
- Deep-merge support keeps existing toggle values when a single switch is updated.
- Defaults keep all gates and strategies enabled, preserving the current production behavior until the user disables a switch.

## Runtime Gates Covered

- Market tradability
- Pre-AI position cap
- Pre-AI MTF zone
- EA fallback confidence
- EA fallback RRR
- Decision side guard
- SL distance guard
- Counter-trend guard
- LTF consensus
- Proximity
- Zone-Aware
- M15 SMC pressure
- FVG fill
- Sequential entry
- V25 only
- V25 breakout
- Entry drift
- Hard risk
- Risk params
- Close weakest
- Order preflight
- Post-TP cooldown

## Strategy Switches Covered

- `SCALPING`
- `TREND_FOLLOW`
- `MEAN_REVERSION`
- `BREAKOUT`
- `RANGE`
- `SMC_FVG_SCALP`
- `SMC_FVG_REVERSAL`
- `SMC_FVG_CONTINUATION`
- `SMC_FVG_MAGNET_SCALP`
- `SMC_WALL_BREAK_SCALP`
- `SMC_RSI_DIVERGENCE`
- `V25_PLAYBOOKS`
- `SWING`
- `GRID`
- `TRAILING`

## Operational Notes

- Disable one gate at a time when diagnosing missed entries.
- For the latest log pattern, the first diagnostic switch is `Pre-AI MTF Zone`.
- If `EA RRR` or `Hard Risk` is disabled, monitor live orders closely because the engine can accept setups with weaker executable reward/risk.

## Verification

- `npm run build`
- `npm test`
- `npm run analyze 2026-05-23`

## Related

- [[53_EntryDriftSyncedDecisionPrice_V2623]]
- [[52_PriceMapWallOverlayStability_V2622]]
- [[51_EaOnlyExecutionHardening_V2621]]

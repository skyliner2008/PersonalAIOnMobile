# V26.12 Decision Gate Analytics Observability

Date: 2026-05-19
Status: Production

## Why

The live flow audit needed a clear answer to:

- which strategy was selected
- which signal/gate blocked the trade
- whether the block came from zone context, deterministic uncertainty, signal conflict, or M15 SMC pressure

`npm run analyze YYYY-MM-DD` had also regressed after the Bangkok-day analytics update because `existsSync` was used without being imported.

## Changes

- Restored the missing `existsSync` import in `scripts/advanced_analytics.mjs`.
- Added explicit decision-feed categories for:
  - `DECISION_SIDE_GUARD`
  - `M15_SMC_PRESSURE`
  - `ANTI_HEDGE`
  - `FITNESS_GATE`
- Updated `advanced_analytics.mjs` and `deep_analysis.py` to normalize historical rows from `risk_gate`, so older rows do not remain hidden under `SKIP_OTHER` or `FVG_ALIGNMENT`.

## Audit Snapshot

For `2026-05-19`, after normalization:

- Total market cycles: `539`
- Skipped: `532` (`98.7%`)
- Top blockers:
  - `ZONE_GATE`: `263`
  - `FITNESS_GATE`: `60`
  - `M15_SMC_PRESSURE`: `39`
  - `SEQUENTIAL_DUPLICATE`: `39`
  - `NO_DETERMINISTIC_SIGNAL`: `34`
  - `ANTI_HEDGE`: `12`
  - `DECISION_SIDE_GUARD`: `6`

For `2026-05-18`:

- Total market cycles: `3001`
- Skipped: `2961` (`98.7%`)
- `DECISION_SIDE_GUARD`: `60`

## Interpretation

The system is still highly selective. Most skips come from Zone-Aware context and risk/spacing gates, while the latest scalp-specific safety logic is now visible as its own category. This makes it easier to see whether the bot is blocked by HTF zone logic, signal conflict, or local M15 SMC pressure.

## Verification

- `npm run analyze 2026-05-19`
- `npm run analyze 2026-05-18`
- `npm run build`

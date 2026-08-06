# TradingView SMC Pine V8.3-V8.5 Hardening

Date: 2026-05-26

## Scope

Hardening pass for `Pine Script/SMC & Multi-TF Order Blocks Sweeps V8.3.txt`, the TradingView Pine Script v6 indicator used to visualize SMC structure, order blocks, fair value gaps, liquidity zones, and multi-timeframe sweeps.

This change is separate from the MT5 auto-trading engine. It improves the chart-side indicator so it is easier to tune in TradingView and less likely to hit drawing or history-scan limits.

## Changes

- Added `max_boxes_count=500` to the indicator declaration because the script creates FVG and Premium/Discount boxes.
- Converted hardcoded settings into TradingView inputs:
  - FVG ATR multiplier, colors, gradient fill, and lookback bounds.
  - Equal high/low threshold, swing liquidity length, and max stored liquidity zones.
  - MTF sweep enable/aggregate toggles, timeframe toggles, reclaim factor, wick ATR filter, and midline filter.
  - Premium/Discount zone location and width.
- Added guardrails for performance and stability:
  - `maxOrderBlocks` and `obSearchBars` cap OB array size and historical OB candle scan depth.
  - Liquidity/FVG arrays now trim with `while` until they are back under their configured cap.
  - Equal-level helper loops now check that their forward comparison range is valid before iterating.
  - MTF liquidity snapshot receives the same liquidity-zone cap as the main chart context.

## Notes

- Unicode labels (`★`, arrows, and the momentum marker) are valid UTF-8 in the file. If Windows PowerShell displays them as garbled text, read with `-Encoding UTF8`.
- TradingView/Pine v6 allows dynamic `request.*()` calls in local scopes by default, so the existing MTF request pattern remains aligned with v6 behavior.

## V8.4 Follow-up

Date: 2026-05-26

- Updated the TradingView indicator title to `SMC & Multi-TF Order Blocks Sweeps V8.4`.
- Removed `barstate.isconfirmed` from the MTF sweep expression passed through `request.security()`. The sweep request now returns raw sweep booleans and gates chart labels with `ta.change(time(tf)) != 0`, so labels print once when the requested timeframe rolls forward.
- Aggregate MTF sweep mode now respects the `M1`, `M5`, and `M15` toggles. If only one aggregate TF is enabled, one confirmed sweep is enough; if two or more are enabled, the score still requires two aligned sweeps.
- `Show FVGs on Chart` now controls FVG box creation and FVG average plotting, preventing hidden boxes from consuming drawing quota when FVG display is off.
- OB triggering now waits for confirmed bars before setting `top.crossed`/`btm.crossed`, and trend filtering accepts a pending bullish/bearish structure break on that same confirmed bar. A CHoCH/BOS bar can seed its first aligned order block instead of being filtered by the previous `structureDirection`.

## V8.5 M15 Wall Map Follow-up

Date: 2026-05-27

- Updated the TradingView indicator title to `SMC & Multi-TF Order Blocks Sweeps V8.5`.
- MTF liquidity merge now uses a dedicated `MTF Merge Threshold (%)` input instead of reusing the Equal HL detection threshold. Default is `0.02%`, which keeps nearby XAUUSD walls such as `4503.2` and `4500.6` from collapsing into one averaged label.
- `MTF Equal HL Lookback` default now matches the main liquidity lookback (`10`) so M1/M5 levels detected on their own charts have the same chance to appear on the M15 wall map.
- Added `MTF Levels Per TF` with a default/max of `5`, replacing the old hardcoded 3-level snapshot. This gives each TF enough room to contribute more visible support/resistance levels to the M15 overlay.
- MTF labels can now show TF-count stars. The stars represent the number of timeframes merged into that wall, so a level shared by M1 and M5 appears like `4503.2 ★★ M1+M5`, while broader walls get thicker lines and more stars.
- MTF tag text is ordered from execution to higher context (`M1+M5+M15+M30+M45+H1+H4`) so wall labels are easier to scan on the M15 chart.
- Added `Show Current TF Zones` (`showCurrentTFZones`) input option to gate the rendering of current timeframe liquidity zones. This allows Instance 2 (MTF Confluence overlay) to hide current TF zones, preventing redundant drawing since Instance 1 already draws them.
- Hardcoded all numeric and string input values (e.g., lookbacks, thresholds, lengths, colors) to their defaults, leaving only boolean toggles in the TradingView settings menu for a cleaner UI.

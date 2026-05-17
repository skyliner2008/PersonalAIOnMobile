# V26.5 - Stale-Stop Market Closed Guard & RSI Divergence Strategy

> Date: 2026-05-18  
> Scope: `mt5-core-server/src/services/autoTradingService.ts`, `SignalDetector.ts`, `types.ts`

## Problem 1: Stale-Stop Spamming on Closed Market
The system was spamming `Market closed (retcode=10018)` errors every 30 seconds when trying to close stale positions of XAUUSD during market close hours.

## Fix 1: Market Closed Guard
- Added `isSymbolTradable` check in the `Stale-Stop` loop.
- If the symbol is not tradable (stale tick > 10 mins), it skips the close attempt and backs off for 5 minutes to avoid spamming the broker.

## Problem 2: Lack of Scalping Orders & Counter-Trend Blocks
The system had very few scalping orders because the `Zone-Aware Gate` blocked counter-trend trades based on H4 bias, even when M5/M15 showed alignment.

## Fix 2: Relaxed Counter-Trend for Scalp & RSI Divergence
- **Relaxed Scalp Gate**: Modified `zoneHtfAligned` in `autoTradingService.ts` to allow `SMC_FVG_SCALP`, `SCALPING`, and `SMC_RSI_DIVERGENCE` to pass if M15 or M5 bias agrees with the trade side, even if H4 disagrees.
- **New Strategy**: Added `SMC_RSI_DIVERGENCE` in `SignalDetector.ts`.
  - **Bullish Divergence**: Price makes Lower Low, RSI makes Higher Low (< 35).
  - **Bearish Divergence**: Price makes Higher High, RSI makes Lower High (> 65).
  - Uses local RSI calculation back to the previous swing points.

## Validation
- Verified that `Stale-Stop` skips silently without spamming errors.
- Verified that system boots up successfully after fixing duplicate variable declaration in `SignalDetector.ts`.

## Related Notes
- [[34_ZoneAwareGate_MTF_LocalOverride_V264]]

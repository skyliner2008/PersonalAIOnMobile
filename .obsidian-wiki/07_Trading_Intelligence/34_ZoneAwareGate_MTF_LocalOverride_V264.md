# V26.4 - Zone-Aware Gate MTF Local Override

> Date: 2026-05-18  
> Scope: `mt5-core-server/src/services/autoTradingService.ts`, `auto/core/ZoneAwareGate.ts`

## Problem

Runtime log showed repeated XBTUSD SELL candidates with strong BEAR bias and high confluence, but every decision was blocked by:

`Zone-Aware Gate blocked: SELL in DISCOUNT zone`

The protection was useful, but too coarse. It treated the larger swing zone as the main veto, so H4/H1 discount could block a short even when M15/M5 had already pulled back into a better local execution zone or had a fresh aligned FVG.

## Change

V26.4 splits zone analysis into layers:

| Layer | Role |
|---|---|
| H4 | Major structure / trend context |
| H1 | Context confirmation |
| M30/M15/M5/active TF | Execution zone |

The gate still blocks BUY in PREMIUM and SELL in DISCOUNT when evidence is weak. It can now allow an entry when:

- HTF bias agrees with the trade side.
- Strategy/rationale has continuation intent.
- Execution TF has local premium/discount relief.
- A fresh aligned FVG is near price, including LTF FVGs within ATR tolerance.
- Or SMC / wall-fade evidence is strong enough.

## Safety

- Deep wrong execution TF still blocks.
- HTF deep wrong zone alone no longer hard-vetoes a scalp if local execution evidence is present.
- `SMC_FVG_SCALP` FVG alignment now checks MTF FVGs, not only the active/H1 candle set.
- Defaults:
  - `zoneLocalAlignmentConfluenceMin = 68`
  - `zoneFvgProximityAtrMul = 0.35`

## Related Fix

Stale-position time stop now computes R using broker `priceOpen` first, matching the manager audit line. This fixes a mismatch where audit could show a stale dead-zone position while the close logic calculated a different R from journal entry.

## Validation

- Added `auto/core/ZoneAwareGate.test.ts`
- `npm test -- --run src/services/auto/core/ZoneAwareGate.test.ts`
- `npm run build`

## Related Notes

- [[31_PreAI_ZoneGate_TrendOverride_V26]]
- [[33_AutoEngine_Runtime_Stability_V262]]

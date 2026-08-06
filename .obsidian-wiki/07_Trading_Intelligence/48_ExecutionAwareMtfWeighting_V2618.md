# V26.18 - Execution-Aware MTF Weighting

Date: 2026-05-20

## Problem

Recent logs showed several directional strategies were too easy for H4/H1 to dominate. The clearest failure mode was repeated HTF-driven `BREAKOUT SELL`, but the same structure could also affect `MEAN_REVERSION` and `TREND_FOLLOW`.

Before this update, deterministic side scoring gave H4 and H1 a combined 70% of the score and did not include M30 or M1. That made local execution evidence arrive too late, often as a downstream guard/block instead of influencing the strategy decision itself.

## Change

`auto/deterministicEngine.ts` now treats H4/H1 as context and gives local/mid execution timeframes enough weight to steer entries:

| Timeframe | Weight |
| --- | ---: |
| H4 | 22% |
| H1 | 23% |
| M30 | 15% |
| M15 | 18% |
| M5 | 15% |
| M1 | 7% |

Directional strategies now also need local execution agreement:

- `BREAKOUT`, `MEAN_REVERSION`, and `TREND_FOLLOW` return `SKIP` when M15 and M5 both oppose the selected side.
- `MEAN_REVERSION` also waits when one of M15/M5 opposes and M1 also opposes with no M15/M5 alignment.
- Local-proof scalp strategies are exempt because they already require their own LTF evidence path.

## Intent

The model should no longer read H4 as an entry trigger. H4/H1 can describe context and bias, but M15/M5/M1 must decide whether there is an executable entry now.

## Verification

- `npm test -- --run src/services/auto/deterministicEngine.test.ts`
- `npm run build`

## Related

- [[47_BreakoutLocalConfirmationGuard_V2617]]
- [[44_WallBreakScalp_FreshDecisionPrice_V2614]]
- [[40_MeanReversion_OrderGuard_V2610]]

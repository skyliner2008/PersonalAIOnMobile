/**
 * V25.0 — PB1 Wall-Touch Scalp (default playbook, mirrors user's manual rules)
 *
 *  Pre-conditions — STANDARD (range/consolidation):
 *    - wall.confluenceStars >= profile.minWallStars (typically >= 2)
 *    - wall.timeframes includes M15 (user's primary plan TF)
 *    - reversal pattern present (handled by WallStateMachine.CONFIRM)
 *    - opposite-side fresh FVG exists
 *
 *  Pre-conditions — TREND mode (regime == TRENDING):
 *    - wall.confluenceStars >= profile.minWallStars
 *    - wall.timeframes includes M5 (at minimum — M15 not required)
 *    - opposite wall exists for TP target (fresh FVG not required)
 *    - side must align with HTF bias (BUY for BELOW wall, SELL for ABOVE wall)
 *
 *  Entry:
 *    BUY  = wall.price + 1 tick   (just above support)
 *    SELL = wall.price - 1 tick   (just below resistance)
 *
 *  SL:
 *    BUY  = min(swingLow, wall.priceBottom) - slBufferAtrMul * atrM1
 *    SELL = max(swingHigh, wall.priceTop)   + slBufferAtrMul * atrM1
 *    Hard limit: SL distance must <= 1.0 * atrM15 — else reject (rule R2)
 *
 *  TP:
 *    BUY  = oppositeWall.price - 1 tick - spread     (rule R3)
 *    SELL = oppositeWall.price + 1 tick + spread
 *
 *  Min RRR: profile.minRrr  (XBTUSD 1.5, XAUUSD 1.8, default 1.8)
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import type { PlaybookContext, PlaybookEvalResult, TradePlan } from './types.js';

const TICK = 0.01; // generic; spread already encodes broker tick magnitude

export function evaluatePB1(
  ctx: PlaybookContext,
  regime: 'TRENDING' | 'RANGING' = 'RANGING',
): PlaybookEvalResult {
  const triggers: string[] = [];

  // ── Gate 1: wall quality ──────────────────────────────────────────────────
  const isMicroWall = ctx.wall.confluenceStars === 1 && ctx.wall.timeframes.includes('M1');
  const allowedMicro = isMicroWall && ctx.touchCount >= 2;
  
  if (ctx.wall.confluenceStars < ctx.profile.minWallStars && !allowedMicro) {
    return { plan: null, reason: `wall stars ${ctx.wall.confluenceStars} < min ${ctx.profile.minWallStars} (and not an active micro-wall)` };
  }

  // ── Gate 2: timeframe anchor ──────────────────────────────────────────────
  // STANDARD: must include M15 (or be an allowed micro-wall).
  // TREND mode: M5 is sufficient — M15 bars close less frequently so fewer walls qualify.
  const hasTfAnchor = regime === 'TRENDING'
    ? ctx.wall.timeframes.includes('M5') || ctx.wall.timeframes.includes('M15') || allowedMicro
    : ctx.wall.timeframes.includes('M15') || allowedMicro;
  if (!hasTfAnchor) {
    return { plan: null, reason: `wall TF ${ctx.wall.timeframes.join('+')} insufficient (need M5 in TRENDING or M15 in RANGING)` };
  }

  // ── Gate 3: target / FVG ──────────────────────────────────────────────────
  // STANDARD: require fresh opposite FVG for TP.
  // TREND mode: use the opposite wall as TP (fresh FVG optional — we still prefer it).
  if (regime !== 'TRENDING' && !ctx.hasFreshOpposite) {
    return { plan: null, reason: 'no fresh opposite FVG (R1/R3 gate)' };
  }
  if (!ctx.oppositeWall) {
    return { plan: null, reason: 'no opposite wall for TP target' };
  }

  const isTrend = regime === 'TRENDING';
  triggers.push(`wall ${ctx.wall.price} ${ctx.wall.confluenceStars}★ ${ctx.wall.timeframes.join('+')}`);
  triggers.push(`reversal ${ctx.reversalKind}`);
  triggers.push(isTrend ? 'trend-mode (opposite wall TP)' : 'fresh opposite FVG present');

  const tickSize = ctx.spread > 0 ? Math.max(ctx.spread / 4, TICK) : TICK;
  const slBufferAtrM1Approx = ctx.profile.slBufferAtrMul * Math.max(ctx.atrM15 * 0.2, ctx.spread * 4, tickSize * 5);

  let entry: number;
  let sl: number;
  let tp: number;
  if (ctx.side === 'BUY') {
    entry = ctx.wall.price + tickSize;
    // Fix: Must use Math.min to ensure SL is below the wall if swingLow is above the wall,
    // or below the swingLow if it swept below the wall.
    sl = Math.min(ctx.swingLow, ctx.wall.priceBottom) - slBufferAtrM1Approx;
    tp = ctx.oppositeWall.price - tickSize - ctx.spread;
  } else {
    entry = ctx.wall.price - tickSize;
    // Fix: Must use Math.max to ensure SL is above the wall if swingHigh is below the wall,
    // or above the swingHigh if it swept above the wall.
    sl = Math.max(ctx.swingHigh, ctx.wall.priceTop) + slBufferAtrM1Approx;
    tp = ctx.oppositeWall.price + tickSize + ctx.spread;
  }

  const slDist = Math.abs(entry - sl);
  const tpDist = Math.abs(tp - entry);
  if (slDist <= 0 || tpDist <= 0) {
    return { plan: null, reason: `degenerate distances slDist=${slDist} tpDist=${tpDist}` };
  }
  // 2026-05-11 V25.3 — In TRENDING/BREAKOUT regime, M1 swings are naturally wider
  // than in range-bound conditions. Relax the SL cap from 1.0× to 2.0× ATR(M15)
  // to allow valid entries near high-confluence walls during volatile breakouts.
  const slAtrMaxMul = isTrend ? 2.0 : 1.0;
  if (ctx.atrM15 > 0 && slDist > ctx.atrM15 * slAtrMaxMul) {
    return { plan: null, reason: `SL distance ${slDist.toFixed(2)} > ${slAtrMaxMul.toFixed(1)} * ATR(M15) ${ctx.atrM15.toFixed(2)} — wall not a true scalp anchor (R2)` };
  }
  const rrr = tpDist / slDist;
  const minRrr = ctx.wall.confluenceStars >= 5
    ? Math.max(1.5, ctx.profile.minRrr - 0.3)
    : ctx.profile.minRrr;
  if (rrr + 1e-6 < minRrr) {
    return { plan: null, reason: `RRR ${rrr.toFixed(2)} < min ${minRrr.toFixed(2)} (PB1)` };
  }

  triggers.push(`entry=${entry.toFixed(2)} sl=${sl.toFixed(2)} tp=${tp.toFixed(2)} rrr=${rrr.toFixed(2)}`);

  const plan: TradePlan = {
    playbook: isTrend ? 'PB1_TREND_TOUCH' : 'PB1_WALL_TOUCH',
    symbol: ctx.symbol,
    side: ctx.side,
    entry,
    sl,
    tp,
    rrr,
    wallStars: ctx.wall.confluenceStars,
    oppositeWallStars: ctx.oppositeWall.confluenceStars,
    triggers,
    ts: Date.now(),
  };
  return { plan, reason: 'PB1 ok' };
}

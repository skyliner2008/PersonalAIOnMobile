/**
 * V25.0 — PB5 Breakout-Retest Continuation
 *
 * Fires only after WallStateMachine transitioned BREAK_OUT → RETEST. The
 * "broken" wall flips polarity (resistance → support, vice versa) and we
 * trade the continuation in the breakout direction.
 *
 * SL: just inside the broken wall (small, since retest must hold).
 * TP: next opposite wall in breakout direction.
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import type { PlaybookContext, PlaybookEvalResult, TradePlan } from './types.js';

const TICK = 0.01;

export function evaluatePB5(ctx: PlaybookContext, isRetest: boolean): PlaybookEvalResult {
  if (!isRetest) {
    return { plan: null, reason: 'state not RETEST — PB5 skipped' };
  }
  if (!ctx.oppositeWall) return { plan: null, reason: 'no continuation target wall' };

  const triggers: string[] = [
    `RETEST after break of ${ctx.wall.price}`,
    `continuation ${ctx.side}`,
    `target wall ${ctx.oppositeWall.price} ${ctx.oppositeWall.confluenceStars}★`,
  ];

  const tickSize = ctx.spread > 0 ? Math.max(ctx.spread / 4, TICK) : TICK;
  const slBuffer = ctx.profile.slBufferAtrMul * Math.max(ctx.atrM15 * 0.2, ctx.spread * 4);

  let entry: number;
  let sl: number;
  let tp: number;
  // For PB5, "side" is the breakout direction — opposite of wall.side polarity.
  if (ctx.side === 'BUY') {
    // wall acted as resistance, was broken upward; now retest from above
    entry = ctx.wall.price + tickSize;
    sl = ctx.wall.priceBottom - slBuffer;
    tp = ctx.oppositeWall.price - tickSize - ctx.spread;
  } else {
    entry = ctx.wall.price - tickSize;
    sl = ctx.wall.priceTop + slBuffer;
    tp = ctx.oppositeWall.price + tickSize + ctx.spread;
  }

  const slDist = Math.abs(entry - sl);
  const tpDist = Math.abs(tp - entry);
  if (slDist <= 0 || tpDist <= 0) return { plan: null, reason: 'degenerate distances' };
  if (ctx.atrM15 > 0 && slDist > ctx.atrM15 * 1.0) {
    return { plan: null, reason: `SL ${slDist.toFixed(2)} > 1.0 × ATR(M15)` };
  }
  const rrr = tpDist / slDist;
  const minRrr = Math.max(2.0, ctx.profile.minRrr);
  if (rrr + 1e-6 < minRrr) {
    return { plan: null, reason: `RRR ${rrr.toFixed(2)} < min ${minRrr.toFixed(2)}` };
  }

  triggers.push(`entry=${entry.toFixed(2)} sl=${sl.toFixed(2)} tp=${tp.toFixed(2)} rrr=${rrr.toFixed(2)}`);

  const plan: TradePlan = {
    playbook: 'PB5_BREAKOUT_RETEST',
    symbol: ctx.symbol,
    side: ctx.side,
    entry, sl, tp, rrr,
    wallStars: ctx.wall.confluenceStars,
    oppositeWallStars: ctx.oppositeWall.confluenceStars,
    triggers,
    ts: Date.now(),
  };
  return { plan, reason: 'PB5 ok' };
}

/**
 * V25.0 — PB2 FVG Fill Reversal
 *
 * Fires when wall has BOTH OB and FVG, AND opposite-side fresh FVG exists.
 * Same SL/TP framework as PB1 but RRR target higher (2–4) — we accept a
 * tighter SL because the OB+FVG zone is institutionally significant.
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import type { PlaybookContext, PlaybookEvalResult, TradePlan } from './types.js';

const TICK = 0.01;

export function evaluatePB2(ctx: PlaybookContext): PlaybookEvalResult {
  const triggers: string[] = [];

  if (!ctx.wall.hasOB || !ctx.wall.hasFVG) {
    return { plan: null, reason: 'wall missing OB+FVG combination' };
  }
  if (!ctx.hasFreshOpposite) {
    return { plan: null, reason: 'no fresh opposite FVG' };
  }
  if (!ctx.oppositeWall) {
    return { plan: null, reason: 'no opposite wall for TP target' };
  }
  // Tighter star floor: 2★ ok if OB+FVG combined
  if (ctx.wall.confluenceStars < Math.max(2, ctx.profile.minWallStars - 1)) {
    return { plan: null, reason: `wall stars ${ctx.wall.confluenceStars} insufficient for PB2` };
  }

  triggers.push(`OB+FVG wall ${ctx.wall.price} ${ctx.wall.confluenceStars}★`);
  triggers.push('opposite FVG fresh');
  triggers.push(`reversal ${ctx.reversalKind}`);

  const tickSize = ctx.spread > 0 ? Math.max(ctx.spread / 4, TICK) : TICK;
  const slBuffer = ctx.profile.slBufferAtrMul * Math.max(ctx.atrM15 * 0.15, ctx.spread * 4);

  let entry: number;
  let sl: number;
  let tp: number;
  if (ctx.side === 'BUY') {
    entry = ctx.wall.priceBottom + tickSize; // enter inside OB (lower edge)
    sl = ctx.wall.priceBottom - slBuffer;
    tp = ctx.oppositeWall.price - tickSize - ctx.spread;
  } else {
    entry = ctx.wall.priceTop - tickSize;
    sl = ctx.wall.priceTop + slBuffer;
    tp = ctx.oppositeWall.price + tickSize + ctx.spread;
  }

  const slDist = Math.abs(entry - sl);
  const tpDist = Math.abs(tp - entry);
  if (slDist <= 0 || tpDist <= 0) return { plan: null, reason: 'degenerate distances' };
  if (ctx.atrM15 > 0 && slDist > ctx.atrM15 * 0.8) {
    return { plan: null, reason: `SL ${slDist.toFixed(2)} > 0.8 × ATR(M15) ${ctx.atrM15.toFixed(2)}` };
  }
  const rrr = tpDist / slDist;
  const minRrrPB2 = Math.max(2.0, ctx.profile.minRrr);
  if (rrr + 1e-6 < minRrrPB2) {
    return { plan: null, reason: `RRR ${rrr.toFixed(2)} < min ${minRrrPB2.toFixed(2)} (PB2)` };
  }

  triggers.push(`entry=${entry.toFixed(2)} sl=${sl.toFixed(2)} tp=${tp.toFixed(2)} rrr=${rrr.toFixed(2)}`);

  const plan: TradePlan = {
    playbook: 'PB2_FVG_FILL',
    symbol: ctx.symbol,
    side: ctx.side,
    entry, sl, tp, rrr,
    wallStars: ctx.wall.confluenceStars,
    oppositeWallStars: ctx.oppositeWall.confluenceStars,
    triggers,
    ts: Date.now(),
  };
  return { plan, reason: 'PB2 ok' };
}

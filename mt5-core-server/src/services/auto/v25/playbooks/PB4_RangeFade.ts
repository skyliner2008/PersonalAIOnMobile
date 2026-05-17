/**
 * V25.0 — PB4 Range Boundary Fade
 *
 * Fires in RANGING regime: fade the touched wall back toward the range mid.
 * Lower RRR (1.5–2.5) but higher hit rate. Target = midpoint between this
 * wall and the opposite wall (NOT the opposite wall itself).
 *
 * Regime is determined externally by the V21/V22 deterministicEngine; PB4
 * receives this via PlaybookContext extension (PlaybookSelector enriches).
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import type { PlaybookContext, PlaybookEvalResult, TradePlan } from './types.js';

const TICK = 0.01;

export function evaluatePB4(ctx: PlaybookContext, regime: string): PlaybookEvalResult {
  if (regime !== 'RANGING') {
    return { plan: null, reason: `regime ${regime} != RANGING` };
  }
  if (!ctx.oppositeWall) return { plan: null, reason: 'no opposite wall — range undefined' };
  if (ctx.wall.confluenceStars < ctx.profile.minWallStars) {
    return { plan: null, reason: `wall stars ${ctx.wall.confluenceStars} < min` };
  }

  const triggers: string[] = [
    `RANGING regime`,
    `boundary wall ${ctx.wall.price} ${ctx.wall.confluenceStars}★`,
    `reversal ${ctx.reversalKind}`,
  ];

  const tickSize = ctx.spread > 0 ? Math.max(ctx.spread / 4, TICK) : TICK;
  const slBuffer = ctx.profile.slBufferAtrMul * Math.max(ctx.atrM15 * 0.15, ctx.spread * 4);
  const mid = (ctx.wall.price + ctx.oppositeWall.price) / 2;

  let entry: number;
  let sl: number;
  let tp: number;
  if (ctx.side === 'BUY') {
    entry = ctx.wall.price + tickSize;
    sl = Math.min(ctx.swingLow, ctx.wall.priceBottom, entry) - slBuffer;
    tp = mid - tickSize;
  } else {
    entry = ctx.wall.price - tickSize;
    sl = Math.max(ctx.swingHigh, ctx.wall.priceTop, entry) + slBuffer;
    tp = mid + tickSize;
  }

  const slDist = Math.abs(entry - sl);
  const tpDist = Math.abs(tp - entry);
  if (slDist <= 0 || tpDist <= 0) return { plan: null, reason: 'degenerate distances' };
  if (ctx.side === 'BUY' && (sl >= entry || tp <= entry)) {
    return { plan: null, reason: 'BUY target/stop not on executable side' };
  }
  if (ctx.side === 'SELL' && (sl <= entry || tp >= entry)) {
    return { plan: null, reason: 'SELL target/stop not on executable side' };
  }
  const rrr = tpDist / slDist;
  const minRrr = Math.max(1.5, ctx.profile.minRrr - 0.3); // PB4 accepts lower RRR
  if (rrr + 1e-6 < minRrr) {
    return { plan: null, reason: `RRR ${rrr.toFixed(2)} < min ${minRrr.toFixed(2)} (PB4)` };
  }

  triggers.push(`entry=${entry.toFixed(2)} sl=${sl.toFixed(2)} tp=${tp.toFixed(2)} rrr=${rrr.toFixed(2)}`);

  const plan: TradePlan = {
    playbook: 'PB4_RANGE_FADE',
    symbol: ctx.symbol,
    side: ctx.side,
    entry, sl, tp, rrr,
    wallStars: ctx.wall.confluenceStars,
    oppositeWallStars: ctx.oppositeWall.confluenceStars,
    triggers,
    ts: Date.now(),
  };
  return { plan, reason: 'PB4 ok' };
}

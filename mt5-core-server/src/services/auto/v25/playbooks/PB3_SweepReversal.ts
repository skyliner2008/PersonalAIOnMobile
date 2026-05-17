/**
 * V25.0 — PB3 Liquidity Sweep Reversal
 *
 * Fires when price has wicked through the wall and closed back inside on M1
 * (i.e. liquidity sweep). Detected via reversalKind containing FVG_FILL or
 * via the WallStateMachine entering REACT after a brief BREAK_OUT bounce.
 *
 * Highest RRR target (2–5) because false breaks generally retrace strongly.
 * We use the swing of the sweep wick as SL anchor.
 *
 * Phase B's WallStateMachine treats sweeps as a REACT after BREAK_OUT cancel,
 * so for now PB3 is gated by reversalKind. Phase B+ may add explicit sweep
 * detection (V25_SWEEP_DETECTED event).
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import type { PlaybookContext, PlaybookEvalResult, TradePlan } from './types.js';

const TICK = 0.01;

export function evaluatePB3(ctx: PlaybookContext): PlaybookEvalResult {
  const triggers: string[] = [];
  const isSweep = /FVG_FILL|PINBAR/.test(ctx.reversalKind);
  if (!isSweep) {
    return { plan: null, reason: `reversalKind ${ctx.reversalKind} not a sweep pattern` };
  }
  if (!ctx.oppositeWall) return { plan: null, reason: 'no opposite wall' };
  if (ctx.wall.confluenceStars < Math.max(2, ctx.profile.minWallStars - 1)) {
    return { plan: null, reason: `wall stars ${ctx.wall.confluenceStars} insufficient for PB3` };
  }

  triggers.push(`sweep at wall ${ctx.wall.price} (${ctx.reversalKind})`);
  triggers.push(`wall ${ctx.wall.confluenceStars}★ ${ctx.wall.timeframes.join('+')}`);

  const tickSize = ctx.spread > 0 ? Math.max(ctx.spread / 4, TICK) : TICK;
  const slBuffer = ctx.profile.slBufferAtrMul * Math.max(ctx.atrM15 * 0.25, ctx.spread * 6);

  let entry: number;
  let sl: number;
  let tp: number;
  if (ctx.side === 'BUY') {
    entry = ctx.wall.price + tickSize;
    sl = Math.min(ctx.swingLow, ctx.wall.priceBottom, entry) - slBuffer;
    tp = ctx.oppositeWall.price - tickSize - ctx.spread;
  } else {
    entry = ctx.wall.price - tickSize;
    sl = Math.max(ctx.swingHigh, ctx.wall.priceTop, entry) + slBuffer;
    tp = ctx.oppositeWall.price + tickSize + ctx.spread;
  }

  const slDist = Math.abs(entry - sl);
  const tpDist = Math.abs(tp - entry);
  if (slDist <= 0 || tpDist <= 0) return { plan: null, reason: 'degenerate distances' };
  if (ctx.side === 'BUY' && (sl >= entry || tp <= entry)) return { plan: null, reason: 'BUY wrong-side SL/TP' };
  if (ctx.side === 'SELL' && (sl <= entry || tp >= entry)) return { plan: null, reason: 'SELL wrong-side SL/TP' };
  if (ctx.atrM15 > 0 && slDist > ctx.atrM15 * 1.2) {
    return { plan: null, reason: `SL ${slDist.toFixed(2)} > 1.2 × ATR(M15)` };
  }
  const rrr = tpDist / slDist;
  const minRrr = Math.max(2.0, ctx.profile.minRrr);
  if (rrr + 1e-6 < minRrr) {
    return { plan: null, reason: `RRR ${rrr.toFixed(2)} < min ${minRrr.toFixed(2)}` };
  }

  triggers.push(`entry=${entry.toFixed(2)} sl=${sl.toFixed(2)} tp=${tp.toFixed(2)} rrr=${rrr.toFixed(2)}`);

  const plan: TradePlan = {
    playbook: 'PB3_SWEEP_REVERSAL',
    symbol: ctx.symbol,
    side: ctx.side,
    entry, sl, tp, rrr,
    wallStars: ctx.wall.confluenceStars,
    oppositeWallStars: ctx.oppositeWall.confluenceStars,
    triggers,
    ts: Date.now(),
  };
  return { plan, reason: 'PB3 ok' };
}

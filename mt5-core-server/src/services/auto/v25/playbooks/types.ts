/**
 * V25.0 — Playbook types
 *
 * Common types used across PB1–PB5.
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import type { PriceWall } from '../../analyzers/smc/types.js';

export type PlaybookId =
  | 'PB1_WALL_TOUCH'
  | 'PB1_TREND_TOUCH'
  | 'PB2_FVG_FILL'
  | 'PB3_SWEEP_REVERSAL'
  | 'PB4_RANGE_FADE'
  | 'PB5_BREAKOUT_RETEST';

export interface PlaybookContext {
  symbol: string;
  side: 'BUY' | 'SELL';
  /** The wall the price reacted at (entry anchor). */
  wall: PriceWall;
  /** ATR(M15) for distance budgeting. */
  atrM15: number;
  /** Latest tick mid. */
  price: number;
  /** Reversal kind from ReactionDetector (informational). */
  reversalKind: string;
  /** Whether opposite-side fresh FVG exists (gate for PB1/PB2). */
  hasFreshOpposite: boolean;
  /** Opposite wall (target for TP). */
  oppositeWall: PriceWall | null;
  /** Last 5 M5 candle highs/lows for SL anchor (BUY uses lows, SELL uses highs). */
  swingHigh: number;
  swingLow: number;
  /** Spread (last tick). */
  spread: number;
  /** Number of times this wall has been touched/decayed (Phase 3.3). */
  touchCount: number;
  /** Symbol profile (minRrr, ATR muls, etc). */
  profile: {
    minRrr: number;
    minWallStars: number;
    slBufferAtrMul: number;
  };
}

export interface TradePlan {
  playbook: PlaybookId;
  symbol: string;
  side: 'BUY' | 'SELL';
  entry: number;
  sl: number;
  tp: number;
  rrr: number;
  /** Wall stars at the touched wall. */
  wallStars: number;
  /** Wall stars at the opposite wall (TP target). */
  oppositeWallStars: number;
  /** Reasons / triggers for log + UI. */
  triggers: string[];
  /** First strong PriceMap wall between entry and TP; used for BE/trail protection, not TP capping. */
  pathMilestone?: {
    price: number;
    edge: number;
    protectAt: number;
    stars: number;
    timeframes: string[];
    wallSide: string;
    rAtMilestone: number;
  };
  /** Computed at this timestamp. */
  ts: number;
}

export interface PlaybookEvalResult {
  /** If null → playbook chose to skip. */
  plan: TradePlan | null;
  /** Reason this playbook was selected (or skipped). */
  reason: string;
}

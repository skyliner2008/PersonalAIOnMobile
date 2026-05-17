import type { PriceWall } from '../analyzers/smc/types.js';

/**
 * V25.0 Real-Time Wall Engine — Shared Types
 *
 * Phase A scope: Tick + Bar-Close + MicroWall types.
 * Layers L4 (state machine) and L5 (playbooks) will extend in Phase B/C.
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

// ── Tick ─────────────────────────────────────────────────────────────

export interface Tick {
  /** Symbol identifier (uppercase). */
  symbol: string;
  /** Source timestamp from MT5 broker (ms). */
  srcTs: number;
  /** Receive timestamp at gateway (ms). */
  recvTs: number;
  /** Bid price. */
  bid: number;
  /** Ask price. */
  ask: number;
  /** Mid price = (bid + ask) / 2. */
  mid: number;
  /** Spread in price units. */
  spread: number;
}

export interface TickBufferStats {
  symbol: string;
  size: number;
  capacity: number;
  oldestTs: number | null;
  newestTs: number | null;
  /** Last tick latency in ms (recvTs - srcTs). */
  latencyMs: number | null;
}

// ── Bar-Close events ─────────────────────────────────────────────────

export type V25Timeframe = 'M1' | 'M5' | 'M15' | 'M30' | 'H1' | 'H4';

export interface BarCloseEvent {
  symbol: string;
  tf: V25Timeframe;
  /** Bar open timestamp (ms). */
  barOpenTs: number;
  /** Bar close timestamp (ms). */
  barCloseTs: number;
}

// ── Micro-Wall ────────────────────────────────────────────────────────

export interface MicroWallSnapshot {
  symbol: string;
  ts: number;
  /** Highest tick mid in last N seconds. */
  microHigh: number;
  /** Lowest tick mid in last N seconds. */
  microLow: number;
  /** Number of ticks consumed. */
  tickCount: number;
  /** Window size in ms. */
  windowMs: number;
}

// ── Wall State (Phase B placeholder — keep here so Phase A typing is forward-compatible) ──

export type WallStateName =
  | 'IDLE'
  | 'APPROACH'
  | 'REACT'
  | 'CONFIRM'
  | 'BREAK_OUT'
  | 'RETEST'
  | 'COMMITTED';

export interface WallStateEvent {
  symbol: string;
  side: 'ABOVE' | 'BELOW';
  state: WallStateName;
  prevState: WallStateName;
  wallPrice: number | null;
  /** Full wall captured at transition time; keeps CONFIRM tied to its trigger wall. */
  wall?: PriceWall | null;
  ts: number;
}

// ── V25 internal events ──────────────────────────────────────────────

export type V25EventType =
  | 'V25_TICK'
  | 'V25_BAR_CLOSE'
  | 'V25_MICRO_WALL'
  | 'V25_WALL_STATE_CHANGE'
  | 'V25_STALE_TICK';

export interface V25Event {
  type: V25EventType;
  symbol: string;
  ts: number;
  payload: Tick | BarCloseEvent | MicroWallSnapshot | WallStateEvent | { reason: string; ageMs: number };
}

// ── Symbol Profile (per-symbol ATR multipliers from spec Q4) ──────────

export interface V25SymbolProfile {
  symbol: string;
  approachAtrMul: number;
  reactAtrMul: number;
  slBufferAtrMul: number;
  minRrr: number;
  minWallStars: number;
}

export const V25_DEFAULT_PROFILES: Record<string, V25SymbolProfile> = {
  XBTUSD: {
    symbol: 'XBTUSD',
    approachAtrMul: 1.5,
    reactAtrMul: 0.5,
    slBufferAtrMul: 1.0,
    minRrr: 1.5,
    minWallStars: 2,
  },
  XAUUSD: {
    symbol: 'XAUUSD',
    approachAtrMul: 1.2,
    reactAtrMul: 0.4,
    slBufferAtrMul: 0.8,
    minRrr: 1.8,
    minWallStars: 2,
  },
  US100: {
    symbol: 'US100',
    approachAtrMul: 1.3,
    reactAtrMul: 0.4,
    slBufferAtrMul: 0.7,
    minRrr: 1.6,
    minWallStars: 2,
  },
  US500: {
    symbol: 'US500',
    approachAtrMul: 1.3,
    reactAtrMul: 0.4,
    slBufferAtrMul: 0.7,
    minRrr: 1.6,
    minWallStars: 2,
  },
  DEFAULT: {
    symbol: 'DEFAULT',
    approachAtrMul: 1.0,
    reactAtrMul: 0.3,
    slBufferAtrMul: 0.6,
    minRrr: 1.8,
    minWallStars: 3,
  },
};

export function resolveSymbolProfile(symbol: string): V25SymbolProfile {
  const upper = (symbol || '').toUpperCase();
  return V25_DEFAULT_PROFILES[upper] ?? V25_DEFAULT_PROFILES.DEFAULT;
}

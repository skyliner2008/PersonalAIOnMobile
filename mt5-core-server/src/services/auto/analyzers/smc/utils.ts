/**
 * SMC Engine V2 — Utility Functions
 * Shared helpers used across all SMC modules.
 *
 * 2026-05-06 — skyliner.jojo@gmail.com
 */

import type { Candle, SwingPoint } from './types.js';

/**
 * Compute ATR (Average True Range) for candle array.
 * Uses Wilder's smoothing (same as Pine's ta.atr).
 */
export function computeATR(candles: Candle[], period: number = 14): number {
  if (candles.length < period + 1) {
    // Fallback: average range of available candles
    const sum = candles.reduce((acc, c) => acc + (c.h - c.l), 0);
    return sum / candles.length || 0;
  }

  let atr = 0;
  // Initial ATR = SMA of first `period` true ranges
  for (let i = 1; i <= period; i++) {
    atr += trueRange(candles[i], candles[i - 1]);
  }
  atr /= period;

  // Wilder smoothing for remaining
  for (let i = period + 1; i < candles.length; i++) {
    const tr = trueRange(candles[i], candles[i - 1]);
    atr = (atr * (period - 1) + tr) / period;
  }
  return atr;
}

function trueRange(current: Candle, previous: Candle): number {
  return Math.max(
    current.h - current.l,
    Math.abs(current.h - previous.c),
    Math.abs(current.l - previous.c)
  );
}

/**
 * Detect Swing Highs and Swing Lows.
 * Equivalent to Pine Script's ta.pivothigh / ta.pivotlow.
 *
 * @param candles Candle array
 * @param len Lookback/lookahead length (e.g. 5 = check 5 bars on each side)
 * @returns Arrays of swing highs and swing lows
 */
export function detectSwings(candles: Candle[], len: number = 5): { highs: SwingPoint[]; lows: SwingPoint[] } {
  const highs: SwingPoint[] = [];
  const lows: SwingPoint[] = [];

  for (let i = len; i < candles.length - len; i++) {
    const h = candles[i].h;
    const l = candles[i].l;
    let isSwingHigh = true;
    let isSwingLow = true;

    for (let j = i - len; j <= i + len; j++) {
      if (j === i) continue;
      if (candles[j].h >= h) isSwingHigh = false;
      if (candles[j].l <= l) isSwingLow = false;
    }

    if (isSwingHigh) highs.push({ index: i, price: h, barIndex: i });
    if (isSwingLow) lows.push({ index: i, price: l, barIndex: i });
  }

  return { highs, lows };
}

/**
 * Compute SMA (Simple Moving Average).
 */
export function computeSMA(values: number[], period: number): number | null {
  if (values.length < period) return null;
  const slice = values.slice(-period);
  return slice.reduce((a, b) => a + b, 0) / period;
}

/**
 * Get candle body max (max of open, close).
 */
export function candleMax(c: Candle): number {
  return Math.max(c.o, c.c);
}

/**
 * Get candle body min (min of open, close).
 */
export function candleMin(c: Candle): number {
  return Math.min(c.o, c.c);
}

/**
 * Check if candle is bearish (close < open).
 */
export function isBearish(c: Candle): boolean {
  return c.c < c.o;
}

/**
 * Check if candle is bullish (close > open).
 */
export function isBullish(c: Candle): boolean {
  return c.c > c.o;
}

/**
 * Candle body size.
 */
export function bodySize(c: Candle): number {
  return Math.abs(c.c - c.o);
}

/**
 * Candle total range.
 */
export function candleRange(c: Candle): number {
  return c.h - c.l;
}

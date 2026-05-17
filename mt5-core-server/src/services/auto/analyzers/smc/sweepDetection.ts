/**
 * SMC Engine V2 — Multi-TF Sweep Detection + Attack Force
 * Port จาก Pine Script V8.3
 * 2026-05-06
 */

import type { Candle, MtfSweep, MtfSweepScore, AttackForce } from './types.js';
import { computeATR, bodySize, candleMax, candleMin, isBullish, isBearish } from './utils.js';

// ── Sweep Detection ────────────────────────────────────────────────

const RECLAIM_FACTOR = 0.50;
const MIN_WICK_ATR_MULT = 0.30;

/**
 * Detect sweeps on a single timeframe candle array.
 * Port of Pine Script f_sweepSignals()
 */
export function detectSweeps(candles: Candle[]): { bull: boolean; bear: boolean } {
  if (candles.length < 10) return { bull: false, bear: false };

  const last = candles[candles.length - 1];
  const atr = computeATR(candles);

  // Find nearest bearish candle (for bullish OB)
  let bullTop: number | null = null, bullBtm: number | null = null;
  let bearTop: number | null = null, bearBtm: number | null = null;

  for (let i = candles.length - 2; i >= 0; i--) {
    if (isBearish(candles[i]) && bullTop === null) {
      bullTop = candleMax(candles[i]);
      bullBtm = candleMin(candles[i]);
    }
    if (isBullish(candles[i]) && bearTop === null) {
      bearTop = candleMax(candles[i]);
      bearBtm = candleMin(candles[i]);
    }
    if (bullTop !== null && bearTop !== null) break;
  }

  let bull = false, bear = false;

  if (bullBtm !== null && bullTop !== null) {
    const width = Math.abs(bullTop - bullBtm);
    const reclaim = bullBtm + RECLAIM_FACTOR * width;
    const wick = bullBtm - last.l;
    bull = width > 0 && last.l < bullBtm && last.c > reclaim && wick > (MIN_WICK_ATR_MULT * atr);
  }

  if (bearTop !== null && bearBtm !== null) {
    const width = Math.abs(bearTop - bearBtm);
    const reclaim = bearTop - RECLAIM_FACTOR * width;
    const wick = last.h - bearTop;
    bear = width > 0 && last.h > bearTop && last.c < reclaim && wick > (MIN_WICK_ATR_MULT * atr);
  }

  return { bull, bear };
}

/**
 * Aggregate MTF sweeps from multiple timeframe candle arrays.
 * Similar to Pine Script's aggregate mode (M1+M5+M15 score >= 2)
 */
export function aggregateMtfSweeps(
  tfCandles: { timeframe: string; candles: Candle[] }[]
): MtfSweepScore {
  const sweeps: MtfSweep[] = [];
  let bullScore = 0, bearScore = 0;

  for (const { timeframe, candles } of tfCandles) {
    if (candles.length < 10) continue;
    const { bull, bear } = detectSweeps(candles);
    const last = candles[candles.length - 1];

    if (bull) {
      bullScore++;
      sweeps.push({ timeframe, side: 'BULL', barIndex: candles.length - 1, price: last.c });
    }
    if (bear) {
      bearScore++;
      sweeps.push({ timeframe, side: 'BEAR', barIndex: candles.length - 1, price: last.c });
    }
  }

  let aggregateSide: 'BULL' | 'BEAR' | 'NEUTRAL' = 'NEUTRAL';
  if (bullScore >= 2 && bullScore > bearScore) aggregateSide = 'BULL';
  else if (bearScore >= 2 && bearScore > bullScore) aggregateSide = 'BEAR';

  return { bullScore, bearScore, sweeps, aggregateSide };
}

// ── Attack Force Detection ─────────────────────────────────────────

export interface AttackForceConfig {
  atrMultiplier: number;     // Body must be > ATR * mult (default: 2.0)
  bodyAvgPeriod: number;     // SMA period for body size (default: 10)
  volumeAvgPeriod: number;   // SMA period for volume (default: 10)
  bodyMultiplier: number;    // Body must be > avgBody * mult (default: 2.0)
  volumeMultiplier: number;  // Volume must be > avgVol * mult (default: 1.5)
}

const DEFAULT_AF_CFG: AttackForceConfig = {
  atrMultiplier: 2.0,
  bodyAvgPeriod: 10,
  volumeAvgPeriod: 10,
  bodyMultiplier: 2.0,
  volumeMultiplier: 1.5,
};

/**
 * Detect high-momentum "Attack Force" candles.
 * Port of Pine Script's Attack Force logic.
 */
export function detectAttackForce(
  candles: Candle[],
  config: Partial<AttackForceConfig> = {}
): AttackForce[] {
  const cfg = { ...DEFAULT_AF_CFG, ...config };
  if (candles.length < cfg.bodyAvgPeriod + 1) return [];

  const atr = computeATR(candles);
  const forces: AttackForce[] = [];

  // Only check recent candles (last 5)
  const startIdx = Math.max(cfg.bodyAvgPeriod, candles.length - 5);

  for (let i = startIdx; i < candles.length; i++) {
    const c = candles[i];
    const body = bodySize(c);

    // Average body size (SMA)
    let bodySum = 0;
    for (let j = i - cfg.bodyAvgPeriod; j < i; j++) {
      bodySum += bodySize(candles[j]);
    }
    const avgBody = bodySum / cfg.bodyAvgPeriod;

    // Average volume (SMA)
    let volSum = 0;
    const volStart = Math.max(0, i - cfg.volumeAvgPeriod);
    const volCount = i - volStart;
    for (let j = volStart; j < i; j++) {
      volSum += candles[j].v;
    }
    const avgVol = volCount > 0 ? volSum / volCount : 0;

    const bodyCondition = body > avgBody * cfg.bodyMultiplier;
    const volCondition = avgVol <= 0 || c.v > avgVol * cfg.volumeMultiplier;
    const atrCondition = body > atr * cfg.atrMultiplier;

    if (bodyCondition && volCondition && atrCondition) {
      forces.push({
        barIndex: i,
        side: isBullish(c) ? 'BULL' : 'BEAR',
        bodySize: body,
        atrRatio: atr > 0 ? body / atr : 0,
        volumeRatio: avgVol > 0 ? c.v / avgVol : 0,
      });
    }
  }

  return forces;
}

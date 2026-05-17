/**
 * SMC Engine V2 — Liquidity Zones Detection
 * Port จาก Pine Script V8.3: Equal H/L, Swing, MTF merge, Confluence Stars
 * 2026-05-06
 */

import type { Candle, LiquidityZone, OrderBlock, MarketStructure, PremiumDiscountZone } from './types.js';
import { detectSwings, computeATR } from './utils.js';

export interface LiquidityConfig {
  lookback: number;
  equalThresholdPct: number;   // % threshold for equal H/L (default 0.1%)
  swingLength: number;
  maxZones: number;
}

const DEFAULT_CFG: LiquidityConfig = { lookback: 10, equalThresholdPct: 0.1, swingLength: 10, maxZones: 50 };

export function detectLiquidityZones(
  candles: Candle[],
  config: Partial<LiquidityConfig> = {}
): LiquidityZone[] {
  const cfg = { ...DEFAULT_CFG, ...config };
  if (candles.length < 20) return [];
  const zones: LiquidityZone[] = [];

  // 1. Equal Highs
  for (let i = 1; i <= Math.min(cfg.lookback, candles.length - 1); i++) {
    const ch = candles[candles.length - 1 - i]?.h;
    if (!ch) continue;
    const thr = ch * (cfg.equalThresholdPct / 100);
    let eqCount = 0;
    for (let j = i + 1; j <= Math.min(i + cfg.lookback, candles.length - 1); j++) {
      const other = candles[candles.length - 1 - j]?.h;
      if (other && Math.abs(other - ch) <= thr) eqCount++;
    }
    if (eqCount >= 1 && !zones.some(z => z.isHigh && Math.abs(z.price - ch) <= thr)) {
      zones.push({ price: ch, barIndex: candles.length - 1 - i, isHigh: true, swept: false, strength: eqCount, source: 'EQUAL_HL', confluenceStars: 0 });
    }
  }

  // 2. Equal Lows
  for (let i = 1; i <= Math.min(cfg.lookback, candles.length - 1); i++) {
    const cl = candles[candles.length - 1 - i]?.l;
    if (!cl) continue;
    const thr = cl * (cfg.equalThresholdPct / 100);
    let eqCount = 0;
    for (let j = i + 1; j <= Math.min(i + cfg.lookback, candles.length - 1); j++) {
      const other = candles[candles.length - 1 - j]?.l;
      if (other && Math.abs(other - cl) <= thr) eqCount++;
    }
    if (eqCount >= 1 && !zones.some(z => !z.isHigh && Math.abs(z.price - cl) <= thr)) {
      zones.push({ price: cl, barIndex: candles.length - 1 - i, isHigh: false, swept: false, strength: eqCount, source: 'EQUAL_HL', confluenceStars: 0 });
    }
  }

  // 3. Swing H/L Liquidity
  const { highs, lows } = detectSwings(candles, cfg.swingLength);
  for (const sh of highs.slice(-5)) {
    if (!zones.some(z => z.isHigh && Math.abs(z.price - sh.price) < sh.price * 0.001)) {
      zones.push({ price: sh.price, barIndex: sh.index, isHigh: true, swept: false, strength: 2, source: 'SWING', confluenceStars: 0 });
    }
  }
  for (const sl of lows.slice(-5)) {
    if (!zones.some(z => !z.isHigh && Math.abs(z.price - sl.price) < sl.price * 0.001)) {
      zones.push({ price: sl.price, barIndex: sl.index, isHigh: false, swept: false, strength: 2, source: 'SWING', confluenceStars: 0 });
    }
  }

  // 4. Mark swept zones
  const last = candles[candles.length - 1];
  for (const z of zones) {
    if (z.isHigh && last.h > z.price) z.swept = true;
    if (!z.isHigh && last.l < z.price) z.swept = true;
  }

  return zones.filter(z => !z.swept).slice(0, cfg.maxZones);
}

/**
 * Score zone confluence (0-5 stars)
 * Based on Pine Script V8.3 confluence scoring
 */
export function scoreZoneConfluence(
  zone: LiquidityZone,
  bullOBs: OrderBlock[],
  bearOBs: OrderBlock[],
  structure: MarketStructure,
  pdZone: PremiumDiscountZone,
  atr: number
): number {
  let score = 1; // Base score

  // OB overlap (+2)
  const obs = zone.isHigh ? bearOBs : bullOBs;
  if (obs.some(ob => zone.price <= ob.top * 1.001 && zone.price >= ob.bottom * 0.999)) score += 2;

  // Structure confluence (+1)
  if (Math.abs(zone.price - structure.structureHigh) < atr * 0.5 ||
      Math.abs(zone.price - structure.structureLow) < atr * 0.5) score += 1;

  // Premium/Discount alignment (+1)
  if (zone.isHigh && pdZone === 'PREMIUM') score += 1;
  if (!zone.isHigh && pdZone === 'DISCOUNT') score += 1;

  // Trend alignment (+1)
  if ((zone.isHigh && structure.direction === 'BEARISH') ||
      (!zone.isHigh && structure.direction === 'BULLISH')) score += 1;

  return Math.min(5, score);
}

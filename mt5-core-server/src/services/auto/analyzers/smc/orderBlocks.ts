/**
 * SMC Engine V2 — Order Block Detection
 * Port จาก Pine Script V8.3: Strict SMC + FVG Confirm + Mitigation
 * 2026-05-06
 */

import type { Candle, FVG, OrderBlock, StructureDirection } from './types.js';
import { detectSwings, candleMax, candleMin, isBearish, isBullish } from './utils.js';

export interface OrderBlockConfig {
  swingLookback: number;
  useBody: boolean;
  filterByTrend: boolean;
  maxOBCount: number;
}

const DEFAULT_CFG: OrderBlockConfig = {
  swingLookback: 5,
  useBody: true,
  filterByTrend: true,
  maxOBCount: 30,
};

export function detectOrderBlocks(
  candles: Candle[],
  fvgs: FVG[],
  structDir: StructureDirection = 'INIT',
  config: Partial<OrderBlockConfig> = {}
): { bullOBs: OrderBlock[]; bearOBs: OrderBlock[] } {
  const cfg = { ...DEFAULT_CFG, ...config };
  if (candles.length < 20) return { bullOBs: [], bearOBs: [] };

  const { highs, lows } = detectSwings(candles, cfg.swingLookback);
  const bullOBs: OrderBlock[] = [];
  const bearOBs: OrderBlock[] = [];
  const gMax = (c: Candle) => cfg.useBody ? candleMax(c) : c.h;
  const gMin = (c: Candle) => cfg.useBody ? candleMin(c) : c.l;

  // Bullish OBs
  for (const swing of highs) {
    const breakIdx = candles.findIndex((c, idx) => idx > swing.index && c.c > swing.price);
    if (breakIdx === -1) continue;
    let lowestLow = gMin(candles[swing.index]), lowestIdx = swing.index;
    for (let i = swing.index; i < breakIdx; i++) {
      const low = gMin(candles[i]);
      if (low <= lowestLow) { lowestLow = low; lowestIdx = i; }
    }
    let obIdx = lowestIdx, found = false;
    for (let k = lowestIdx; k <= Math.min(breakIdx + 5, candles.length - 1); k++) {
      if (isBearish(candles[k])) { obIdx = k; found = true; break; }
    }
    if (!found) continue;
    if (cfg.filterByTrend && structDir === 'BEARISH') continue;
    const top = gMax(candles[obIdx]), btm = gMin(candles[obIdx]);
    const hasFVG = fvgs.some(f => f.type === 'BULL' && Math.abs(f.barIndex - obIdx) <= 5);
    if (hasFVG) {
      bullOBs.unshift({ type: 'BULL', top, bottom: btm, barIndex: obIdx, mitigated: false, hasFVG, invalidated: false, volume: candles[obIdx].v });
    }
  }

  // Bearish OBs
  for (const swing of lows) {
    const breakIdx = candles.findIndex((c, idx) => idx > swing.index && c.c < swing.price);
    if (breakIdx === -1) continue;
    let highestHigh = gMax(candles[swing.index]), highestIdx = swing.index;
    for (let i = swing.index; i < breakIdx; i++) {
      const high = gMax(candles[i]);
      if (high >= highestHigh) { highestHigh = high; highestIdx = i; }
    }
    let obIdx = highestIdx, found = false;
    for (let k = highestIdx; k <= Math.min(breakIdx + 5, candles.length - 1); k++) {
      if (isBullish(candles[k])) { obIdx = k; found = true; break; }
    }
    if (!found) continue;
    if (cfg.filterByTrend && structDir === 'BULLISH') continue;
    const top = gMax(candles[obIdx]), btm = gMin(candles[obIdx]);
    const hasFVG = fvgs.some(f => f.type === 'BEAR' && Math.abs(f.barIndex - obIdx) <= 5);
    if (hasFVG) {
      bearOBs.unshift({ type: 'BEAR', top, bottom: btm, barIndex: obIdx, mitigated: false, hasFVG, invalidated: false, volume: candles[obIdx].v });
    }
  }

  // Update mitigation & invalidation
  for (const ob of bullOBs) {
    for (let j = ob.barIndex + 1; j < candles.length; j++) {
      if (candles[j].l <= ob.top && candles[j].h >= ob.bottom) { ob.mitigated = true; break; }
    }
    if (candleMin(candles[candles.length - 1]) < ob.bottom) ob.invalidated = true;
  }
  for (const ob of bearOBs) {
    for (let j = ob.barIndex + 1; j < candles.length; j++) {
      if (candles[j].h >= ob.bottom && candles[j].l <= ob.top) { ob.mitigated = true; break; }
    }
    if (candleMax(candles[candles.length - 1]) > ob.top) ob.invalidated = true;
  }

  return {
    bullOBs: bullOBs.filter(ob => !ob.invalidated).slice(0, cfg.maxOBCount),
    bearOBs: bearOBs.filter(ob => !ob.invalidated).slice(0, cfg.maxOBCount),
  };
}

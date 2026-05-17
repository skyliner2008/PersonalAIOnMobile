/**
 * SMC Engine V2 — Fair Value Gap (FVG) Detection
 * Port จาก Pine Script V8.3: ATR-filtered, Bull/Bear average lines, Aging
 *
 * FVG = ช่องว่างระหว่าง candle[i-2].high กับ candle[i].low (Bull)
 *        หรือ candle[i-2].low กับ candle[i].high (Bear)
 * ต้องมีขนาดมากกว่า ATR * multiplier ถึงนับ (กรอง noise)
 *
 * 2026-05-06 — skyliner.jojo@gmail.com
 */

import type { Candle, FVG } from './types.js';
import { computeATR } from './utils.js';

export interface FvgConfig {
  lookback: number;         // จำนวนแท่งย้อนหลังที่ดู (default: 30)
  atrMultiplier: number;    // FVG ต้องใหญ่กว่า ATR * mult (default: 0.25)
  atrPeriod: number;        // ATR period (default: 200)
}

const DEFAULT_CONFIG: FvgConfig = {
  lookback: 30,
  atrMultiplier: 0.25,
  atrPeriod: 200,
};

/**
 * Detect all FVGs in candle array.
 * Returns only active (unfilled) FVGs within lookback window.
 */
export function detectFVGs(
  candles: Candle[],
  config: Partial<FvgConfig> = {}
): FVG[] {
  const cfg = { ...DEFAULT_CONFIG, ...config };

  if (candles.length < 3) return [];

  const atr = computeATR(candles, Math.min(cfg.atrPeriod, candles.length - 1));
  const minGapSize = atr * cfg.atrMultiplier;
  const startIdx = Math.max(2, candles.length - cfg.lookback);
  const lastIdx = candles.length - 1;

  const fvgs: FVG[] = [];

  for (let i = startIdx; i < candles.length; i++) {
    const c0 = candles[i];       // current candle
    const c1 = candles[i - 1];   // middle candle
    const c2 = candles[i - 2];   // two bars ago

    // Bullish FVG: gap between c2.high and c0.low
    // ต้อง c1 close above c2.high (displacement)
    if (c0.l > c2.h && c1.c > c2.h) {
      const gapSize = c0.l - c2.h;
      if (gapSize > minGapSize) {
        fvgs.push({
          type: 'BULL',
          top: c0.l,
          bottom: c2.h,
          barIndex: i,
          mitigated: false,
          age: lastIdx - i,
        });
      }
    }

    // Bearish FVG: gap between c2.low and c0.high
    // ต้อง c1 close below c2.low (displacement)
    if (c0.h < c2.l && c1.c < c2.l) {
      const gapSize = c2.l - c0.h;
      if (gapSize > minGapSize) {
        fvgs.push({
          type: 'BEAR',
          top: c2.l,
          bottom: c0.h,
          barIndex: i,
          mitigated: false,
          age: lastIdx - i,
        });
      }
    }
  }

  // Mark mitigated FVGs (price has returned to fill the gap)
  for (const fvg of fvgs) {
    for (let j = fvg.barIndex + 1; j < candles.length; j++) {
      const c = candles[j];
      if (fvg.type === 'BULL' && c.l <= fvg.bottom) {
        fvg.mitigated = true;
        break;
      }
      if (fvg.type === 'BEAR' && c.h >= fvg.top) {
        fvg.mitigated = true;
        break;
      }
    }
  }

  return fvgs;
}

/**
 * Get only active (unfilled) FVGs.
 */
export function getActiveFVGs(candles: Candle[], config?: Partial<FvgConfig>): FVG[] {
  return detectFVGs(candles, config).filter(f => !f.mitigated);
}

/**
 * Compute FVG Average Lines (like Pine Script's Bull/Bear average).
 * ค่าเฉลี่ยของ FVG bottoms (bull) / tops (bear) ที่ยังใช้งานได้
 */
export function fvgAverages(candles: Candle[], config?: Partial<FvgConfig>): { bullAvg: number | null; bearAvg: number | null } {
  const active = getActiveFVGs(candles, config);
  const bullFvgs = active.filter(f => f.type === 'BULL');
  const bearFvgs = active.filter(f => f.type === 'BEAR');

  const bullAvg = bullFvgs.length > 0
    ? bullFvgs.reduce((sum, f) => sum + f.bottom, 0) / bullFvgs.length
    : null;

  const bearAvg = bearFvgs.length > 0
    ? bearFvgs.reduce((sum, f) => sum + f.top, 0) / bearFvgs.length
    : null;

  return { bullAvg, bearAvg };
}

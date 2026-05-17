import { parseCandles } from '../../tracking.js';
import { Bias, AnalyzerModuleResult, AnalysisSignal } from '../types.js';

// SMC Engine V2 — enhanced modules ported from Pine Script V8.3
import { buildSmcSnapshot } from './smc/index.js';
import type { SmcSnapshot, Candle as SmcCandle } from './smc/index.js';
export { buildSmcSnapshot } from './smc/index.js';
export type { SmcSnapshot } from './smc/index.js';

export interface FVG {
    type: Bias;
    top: number;
    bottom: number;
    index: number;
}

export interface OrderBlock {
    type: Bias;
    top: number;
    bottom: number;
    volume: number;
    hasFVG: boolean;
}

/**
 * SMC Analyzer (Premium Version)
 * Ported from SmcApiService.kt - The "Starred" Logic
 */
export function smcAnalyzer(candles: ReturnType<typeof parseCandles>): AnalyzerModuleResult {
    if (candles.length < 50) {
        return { name: 'SMC', bias: 'NEUTRAL', strengthScore: 0, signals: [], notes: 'insufficient candles' };
    }

    const signals: AnalysisSignal[] = [];
    const last = candles[candles.length - 1];

    // 1. Swing Detection & Market Structure
    const { direction, structureHigh, structureLow, lastEvent } = detectMarketStructure(candles);
    if (direction !== 'NEUTRAL') {
        signals.push({
            name: 'market_structure',
            bias: direction as Bias,
            score: 30,
            evidence: `Structure: ${direction} | High: ${structureHigh.toFixed(2)} | Low: ${structureLow.toFixed(2)} | Last: ${lastEvent || 'None'}`
        });
    }

    // 2. FVG Detection
    const fvgs = detectFVGs(candles);
    const lastFvg = fvgs[fvgs.length - 1];
    if (lastFvg) {
        const gapSize = Math.abs(lastFvg.top - lastFvg.bottom);
        signals.push({
            name: 'fvg_detected',
            bias: lastFvg.type,
            score: 15,
            evidence: `${lastFvg.type} FVG at ${lastFvg.bottom.toFixed(2)}-${lastFvg.top.toFixed(2)} (Size: ${gapSize.toFixed(2)})`
        });
    }

    // 3. Order Block Detection (Strict with FVG)
    const { bullOBs, bearOBs } = detectOrderBlocks(candles, direction, fvgs);
    const lastBullOB = bullOBs[bullOBs.length - 1];
    const lastBearOB = bearOBs[bearOBs.length - 1];

    if (lastBullOB && last.c <= lastBullOB.top * 1.001 && last.c >= lastBullOB.bottom * 0.999) {
        const obScore = lastBullOB.hasFVG ? 30 : 18; // FVG-confirmed OB scores higher
        signals.push({
            name: 'price_in_bull_ob',
            bias: 'BULL',
            score: obScore,
            evidence: `Price testing Bullish OB${lastBullOB.hasFVG ? '+FVG' : ''} (${lastBullOB.bottom.toFixed(2)}-${lastBullOB.top.toFixed(2)})`
        });
    }
    if (lastBearOB && last.c >= lastBearOB.bottom * 0.999 && last.c <= lastBearOB.top * 1.001) {
        const obScore = lastBearOB.hasFVG ? 30 : 18; // FVG-confirmed OB scores higher
        signals.push({
            name: 'price_in_bear_ob',
            bias: 'BEAR',
            score: obScore,
            evidence: `Price testing Bearish OB${lastBearOB.hasFVG ? '+FVG' : ''} (${lastBearOB.bottom.toFixed(2)}-${lastBearOB.top.toFixed(2)})`
        });
    }

    // 4. Premium / Discount
    const range = structureHigh - structureLow;
    if (range > 0) {
        const equilibrium = structureLow + range * 0.5;
        const isPremium = last.c > equilibrium;
        signals.push({
            name: 'pd_zone',
            bias: isPremium ? 'BEAR' : 'BULL',
            score: 10,
            evidence: `Price in ${isPremium ? 'PREMIUM' : 'DISCOUNT'} zone (Eq: ${equilibrium.toFixed(2)})`
        });
    }

    // 5. Candle Patterns (Reversal Confirmation)
    const candlePatterns = detectCandlePatterns(candles);
    signals.push(...candlePatterns);

    // 6. Liquidity Sweep Detection
    const sweeps = detectSweeps(candles);
    const lastSweep = sweeps[sweeps.length - 1];
    if (lastSweep) {
        signals.push({
            name: 'liquidity_sweep',
            bias: lastSweep.direction === 'BULLISH' ? 'BULL' : 'BEAR',
            score: 30,
            evidence: `${lastSweep.direction} Sweep at ${lastSweep.price.toFixed(2)} (Wick through OB)`
        });
    }

    const bullScore = signals.filter(s => s.bias === 'BULL').reduce((a, b) => a + b.score, 0);
    const bearScore = signals.filter(s => s.bias === 'BEAR').reduce((a, b) => a + b.score, 0);

    return {
        name: 'SMC',
        bias: bullScore > bearScore ? 'BULL' : bearScore > bullScore ? 'BEAR' : 'NEUTRAL',
        strengthScore: Math.min(100, Math.max(bullScore, bearScore)),
        signals,
        notes: `Structure: ${direction} | OBs: ${bullOBs.length + bearOBs.length} | Sweeps: ${sweeps.length} | Last Event: ${lastEvent}`
    };
}

// --- Internal SMC Helper Functions (Ported from Kotlin) ---

// --- Candle Pattern Detection ---
function detectCandlePatterns(candles: any[]): AnalysisSignal[] {
    if (candles.length < 3) return [];
    const signals: AnalysisSignal[] = [];
    const last  = candles[candles.length - 1];
    const prev  = candles[candles.length - 2];
    const prev2 = candles[candles.length - 3];

    const lastBody  = Math.abs(last.c - last.o);
    const lastRange = last.h - last.l;
    const prevBody  = Math.abs(prev.c - prev.o);
    const prevRange = prev.h - prev.l;

    if (lastRange <= 0) return signals;

    const lowerWick = Math.min(last.o, last.c) - last.l;
    const upperWick = last.h - Math.max(last.o, last.c);

    // Hammer / Bullish Pin Bar
    if (lowerWick >= lastBody * 2.5 && upperWick <= lastBody * 0.5) {
        signals.push({
            name: 'HAMMER',
            bias: 'BULL',
            score: 18,
            evidence: `Hammer: lower wick ${lowerWick.toFixed(2)} >> body ${lastBody.toFixed(2)}`
        });
    }

    // Shooting Star / Bearish Pin Bar
    if (upperWick >= lastBody * 2.5 && lowerWick <= lastBody * 0.5) {
        signals.push({
            name: 'SHOOTING_STAR',
            bias: 'BEAR',
            score: 18,
            evidence: `Shooting Star: upper wick ${upperWick.toFixed(2)} >> body ${lastBody.toFixed(2)}`
        });
    }

    // Bullish Engulfing
    if (prev.c < prev.o && last.c > last.o && last.c > prev.o && last.o < prev.c && lastBody > prevBody) {
        signals.push({
            name: 'BULL_ENGULFING',
            bias: 'BULL',
            score: 22,
            evidence: `Bullish Engulfing: prev ${prev.o.toFixed(2)}-${prev.c.toFixed(2)} -> last ${last.o.toFixed(2)}-${last.c.toFixed(2)}`
        });
    }

    // Bearish Engulfing
    if (prev.c > prev.o && last.c < last.o && last.c < prev.o && last.o > prev.c && lastBody > prevBody) {
        signals.push({
            name: 'BEAR_ENGULFING',
            bias: 'BEAR',
            score: 22,
            evidence: `Bearish Engulfing: prev ${prev.o.toFixed(2)}-${prev.c.toFixed(2)} -> last ${last.o.toFixed(2)}-${last.c.toFixed(2)}`
        });
    }

    // Doji (indecision - reduce weight of dominant signal)
    if (lastBody / lastRange < 0.1) {
        signals.push({
            name: 'DOJI',
            bias: 'NEUTRAL',
            score: 3,
            evidence: `Doji: body/range=${((lastBody / lastRange) * 100).toFixed(1)}%`
        });
    }

    void prev2; // suppress unused warning
    return signals;
}

function detectSweeps(candles: any[]) {
    if (candles.length < 20) return [];
    const sweeps: { direction: string, price: number }[] = [];

    // Simple Sweep Logic: Price wick goes beyond recent OB then closes back inside
    for (let i = candles.length - 10; i < candles.length; i++) {
        const c = candles[i];
        const prev = candles[i-1];

        // Bullish Sweep: Low below previous candle low, but close above it
        if (c.l < prev.l && c.c > prev.l && (prev.l - c.l) > (prev.h - prev.l) * 0.3) {
            sweeps.push({ direction: 'BULLISH', price: c.c });
        }
        // Bearish Sweep: High above previous candle high, but close below it
        if (c.h > prev.h && c.c < prev.h && (c.h - prev.h) > (prev.h - prev.l) * 0.3) {
            sweeps.push({ direction: 'BEARISH', price: c.c });
        }
    }
    return sweeps;
}

function detectSwings(candles: any[], len: number = 5) {
    const highs: { idx: number, val: number }[] = [];
    const lows: { idx: number, val: number }[] = [];

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
        if (isSwingHigh) highs.push({ idx: i, val: h });
        if (isSwingLow) lows.push({ idx: i, val: l });
    }
    return { highs, lows };
}

function detectMarketStructure(candles: any[]) {
    const { highs, lows } = detectSwings(candles, 10);
    if (highs.length < 2 || lows.length < 2) return { direction: 'NEUTRAL', structureHigh: 0, structureLow: 0, lastEvent: '' };

    const lastHigh = highs[highs.length - 1];
    const prevHigh = highs[highs.length - 2];
    const lastLow = lows[lows.length - 1];
    const prevLow = lows[lows.length - 2];

    const currentPrice = candles[candles.length - 1].c;
    let direction = 'NEUTRAL';
    let lastEvent = '';

    if (lastHigh.val > prevHigh.val && lastLow.val > prevLow.val) {
        direction = 'BULLISH';
        if (currentPrice > prevHigh.val) lastEvent = 'BOS_UP';
    } else if (lastHigh.val < prevHigh.val && lastLow.val < prevLow.val) {
        direction = 'BEARISH';
        if (currentPrice < prevLow.val) lastEvent = 'BOS_DOWN';
    }

    // CHoCH Detection
    if (lastLow.idx > lastHigh.idx && currentPrice < lastLow.val) {
        direction = 'BEARISH';
        lastEvent = 'CHOCH_DOWN';
    } else if (lastHigh.idx > lastLow.idx && currentPrice > lastHigh.val) {
        direction = 'BULLISH';
        lastEvent = 'CHOCH_UP';
    }

    return { direction, structureHigh: lastHigh.val, structureLow: lastLow.val, lastEvent };
}

function detectFVGs(candles: any[]): FVG[] {
    const fvgs: FVG[] = [];
    for (let i = 2; i < candles.length; i++) {
        const c0 = candles[i];
        const c2 = candles[i-2];

        if (c0.l > c2.h) {
            fvgs.push({ type: 'BULL', top: c0.l, bottom: c2.h, index: i });
        } else if (c0.h < c2.l) {
            fvgs.push({ type: 'BEAR', top: c2.l, bottom: c0.h, index: i });
        }
    }

    // Filter out FVGs that have been fully filled by subsequent price action.
    // A Bull FVG is filled when a later candle's low breaches the gap bottom.
    // A Bear FVG is filled when a later candle's high breaches the gap top.
    return fvgs.filter(fvg => {
        const laterCandles = candles.slice(fvg.index + 1);
        if (fvg.type === 'BULL') {
            return !laterCandles.some((c: any) => c.l <= fvg.bottom);
        } else {
            return !laterCandles.some((c: any) => c.h >= fvg.top);
        }
    });
}

function detectOrderBlocks(candles: any[], structureDir: string, fvgs: FVG[]) {
    const { highs, lows } = detectSwings(candles, 5);
    const bullOBs: OrderBlock[] = [];
    const bearOBs: OrderBlock[] = [];

    // Bullish OB: last bearish candle before a Break of Structure to the upside
    for (const swing of highs) {
        const breakIdx = candles.findIndex((c, idx) => idx > swing.idx && c.c > swing.val);
        if (breakIdx === -1) continue;

        let lowestLow = Infinity;
        let lowestIdx = swing.idx;
        for (let i = swing.idx; i < breakIdx; i++) {
            if (candles[i].l < lowestLow) { lowestLow = candles[i].l; lowestIdx = i; }
        }

        const obTop = Math.max(candles[lowestIdx].o, candles[lowestIdx].c);
        const obBtm = candles[lowestIdx].l;
        const hasFVG = fvgs.some(f => f.type === 'BULL' && f.bottom >= obBtm * 0.999 && f.top <= obTop * 1.001);

        // Include OB regardless of FVG -- FVG confluence upgrades the score
        bullOBs.push({ type: 'BULL', top: obTop, bottom: obBtm, volume: candles[lowestIdx].v, hasFVG });
    }

    // Bearish OB: last bullish candle before a Break of Structure to the downside
    for (const swing of lows) {
        const breakIdx = candles.findIndex((c, idx) => idx > swing.idx && c.c < swing.val);
        if (breakIdx === -1) continue;

        let highestHigh = -Infinity;
        let highestIdx = swing.idx;
        for (let i = swing.idx; i < breakIdx; i++) {
            if (candles[i].h > highestHigh) { highestHigh = candles[i].h; highestIdx = i; }
        }

        const obTop = candles[highestIdx].h;
        const obBtm = Math.min(candles[highestIdx].o, candles[highestIdx].c);
        const hasFVG = fvgs.some(f => f.type === 'BEAR' && f.bottom >= obBtm * 0.999 && f.top <= obTop * 1.001);

        // Include OB regardless of FVG -- FVG confluence upgrades the score
        bearOBs.push({ type: 'BEAR', top: obTop, bottom: obBtm, volume: candles[highestIdx].v, hasFVG });
    }

    void structureDir; // suppress unused warning
    return { bullOBs, bearOBs };
}

// ---------------------------------------------------------------------------
// P1.1 -- Premium / Discount Zone Classifier
// ---------------------------------------------------------------------------
export type PremiumDiscountZone = 'PREMIUM' | 'EQ' | 'DISCOUNT';

export interface PremiumDiscountResult {
  zone: PremiumDiscountZone;
  pctFromRange: number;
  structureHigh: number;
  structureLow: number;
  equilibrium: number;
}

export function classifyPremiumDiscount(
  candles: ReturnType<typeof parseCandles>,
  currentPrice: number
): PremiumDiscountResult {
  const defaultResult: PremiumDiscountResult = {
    zone: 'EQ',
    pctFromRange: 50,
    structureHigh: currentPrice,
    structureLow: currentPrice,
    equilibrium: currentPrice,
  };

  if (candles.length < 20) return defaultResult;

  const { structureHigh, structureLow } = detectMarketStructure(candles);
  if (structureHigh <= 0 || structureLow <= 0 || structureHigh <= structureLow) return defaultResult;

  const range = structureHigh - structureLow;
  const equilibrium = structureLow + range * 0.5;
  const pctFromRange = ((currentPrice - structureLow) / range) * 100;

  let zone: PremiumDiscountZone = 'EQ';
  if (pctFromRange > 62) zone = 'PREMIUM';
  else if (pctFromRange < 38) zone = 'DISCOUNT';

  return { zone, pctFromRange, structureHigh, structureLow, equilibrium };
}

/**
 * Get all active (unfilled) FVGs for external use (e.g. SL buffer check).
 */
export function getActiveFVGs(candles: ReturnType<typeof parseCandles>): FVG[] {
  return detectFVGs(candles);
}

// ---------------------------------------------------------------------------
// V20.0 -- Full SMC Structure snapshot for slTpAgent
// ---------------------------------------------------------------------------

export interface SmcStructure {
  activeFVGs:    FVG[];
  bullOBs:       OrderBlock[];
  bearOBs:       OrderBlock[];
  swingHighs:    number[];   // Recent pivot highs (resistance levels)
  swingLows:     number[];   // Recent pivot lows (support levels)
  structureHigh: number;
  structureLow:  number;
}

/**
 * Extract full SMC structure from a parsed candle array.
 * Used by slTpAnalystAgent to pick structurally sound SL/TP levels.
 */
export function getSmcStructure(candles: ReturnType<typeof parseCandles>): SmcStructure {
  const fvgs = detectFVGs(candles);
  const { structureHigh, structureLow } = detectMarketStructure(candles);
  const { bullOBs, bearOBs } = detectOrderBlocks(candles, 'NEUTRAL', fvgs);
  const { highs, lows } = detectSwings(candles, 5);

  // Most recent 5 swing highs/lows so the prompt stays concise
  const swingHighs = highs.slice(-5).map(h => h.val);
  const swingLows  = lows.slice(-5).map(l => l.val);

  return { activeFVGs: fvgs, bullOBs, bearOBs, swingHighs, swingLows, structureHigh, structureLow };
}

/**
 * SMC Engine V2 — Full Enhanced Snapshot
 * Includes: Market Structure (BOS/CHoCH/IDM), FVG w/ mitigation,
 * OB w/ FVG-confirm + invalidation, Liquidity Zones w/ confluence stars,
 * MTF Sweeps, Attack Force, Premium/Discount
 */
export function getSmcSnapshotV2(
  candles: ReturnType<typeof parseCandles>,
  symbol: string,
  timeframe: string,
  mtfCandles?: { timeframe: string; candles: SmcCandle[] }[]
): SmcSnapshot {
  // Convert parseCandles format to SmcCandle format
  const smcCandles: SmcCandle[] = candles.map(c => ({
    t: c.t ?? 0,
    o: c.o,
    h: c.h,
    l: c.l,
    c: c.c,
    v: c.v ?? 0,
  }));
  return buildSmcSnapshot(smcCandles, symbol, timeframe, mtfCandles);
}

export function isSupportedBySMC(side: 'BUY' | 'SELL', price: number, pySmc: any): boolean {
  if (!pySmc) return false;
  const threshold = 0.002;
  if (side === 'BUY') {
    if (Array.isArray(pySmc.ob)) {
      for (const ob of pySmc.ob.filter((o: any) => o.OB === 1)) {
        if (price >= ob.Bottom * (1 - threshold) && price <= ob.Top * (1 + threshold)) return true;
      }
    }
    if (Array.isArray(pySmc.fvg)) {
      for (const fvg of pySmc.fvg.filter((f: any) => f.FVG === 1)) {
        if (price >= fvg.Bottom * (1 - threshold) && price <= fvg.Top * (1 + threshold)) return true;
      }
    }
  } else {
    if (Array.isArray(pySmc.ob)) {
      for (const ob of pySmc.ob.filter((o: any) => o.OB === -1)) {
        if (price <= ob.Top * (1 + threshold) && price >= ob.Bottom * (1 - threshold)) return true;
      }
    }
    if (Array.isArray(pySmc.fvg)) {
      for (const fvg of pySmc.fvg.filter((f: any) => f.FVG === -1)) {
        if (price <= fvg.Top * (1 + threshold) && price >= fvg.Bottom * (1 - threshold)) return true;
      }
    }
  }
  return false;
}


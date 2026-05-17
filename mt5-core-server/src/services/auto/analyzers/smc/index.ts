/**
 * SMC Engine V2 — Main Entry Point
 * รวมทุก module: Structure, FVG, OB, Liquidity, Sweeps, AttackForce
 * สร้าง SmcSnapshot ที่ครบถ้วนสำหรับ EA + Agents
 *
 * 2026-05-06 — skyliner.jojo@gmail.com
 */

// Re-export types
export type {
  Candle, SwingPoint, FVG, OrderBlock, LiquidityZone,
  MarketStructure, StructureEvent, StructureDirection,
  MtfSweep, MtfSweepScore, AttackForce, ZoneConfluence,
  PremiumDiscountZone, PremiumDiscountResult,
  SmcSnapshot, EaEntrySignal,
  // Price Map types (V21.1)
  WallSourceType, WallSource, PriceWall, PriceMap,
  PathObstacle, PathAnalysis,
} from './types.js';

// Re-export modules
export { detectMarketStructure } from './marketStructure.js';
export { detectFVGs, getActiveFVGs, fvgAverages } from './fvgDetection.js';
export { detectOrderBlocks } from './orderBlocks.js';
export { detectLiquidityZones, scoreZoneConfluence } from './liquidityZones.js';
export { detectSweeps, aggregateMtfSweeps, detectAttackForce } from './sweepDetection.js';
export { detectSwings, computeATR } from './utils.js';

// Price Map Builder (V21.1) — MTF Zone Aggregator + Path Analysis
export {
  buildPriceMap,
  buildPriceMapFromRecord,
  analyzePath,
  formatPriceMap,
} from './priceMapBuilder.js';
export type { PriceMapConfig, ExtraContextLevel } from './priceMapBuilder.js';

import type { Candle, SmcSnapshot, PremiumDiscountResult, PremiumDiscountZone } from './types.js';
import { detectMarketStructure } from './marketStructure.js';
import { detectFVGs, getActiveFVGs } from './fvgDetection.js';
import { detectOrderBlocks } from './orderBlocks.js';
import { detectLiquidityZones, scoreZoneConfluence } from './liquidityZones.js';
import { aggregateMtfSweeps, detectAttackForce } from './sweepDetection.js';
import { detectSwings, computeATR } from './utils.js';

/**
 * Build a complete SmcSnapshot from candle data.
 * This is the main function EA calls every time indicators need refresh.
 *
 * @param candles Primary timeframe candles (e.g. M5)
 * @param symbol Symbol name (e.g. 'XAUUSD')
 * @param timeframe Timeframe string (e.g. 'M5')
 * @param mtfCandles Optional: candle arrays from other timeframes for MTF sweep
 */
export function buildSmcSnapshot(
  candles: Candle[],
  symbol: string,
  timeframe: string,
  mtfCandles?: { timeframe: string; candles: Candle[] }[]
): SmcSnapshot {
  if (candles.length < 30) {
    return emptySnapshot(symbol, timeframe, candles[candles.length - 1]?.c ?? 0);
  }

  const lastPrice = candles[candles.length - 1].c;
  const atr = computeATR(candles);

  // 1. Market Structure
  const structure = detectMarketStructure(candles);

  // 2. FVG Detection
  const allFvgs = detectFVGs(candles);
  const activeFVGs = allFvgs.filter(f => !f.mitigated);

  // 3. Order Blocks
  const { bullOBs, bearOBs } = detectOrderBlocks(candles, allFvgs, structure.direction);

  // 4. Premium/Discount
  const premiumDiscount = classifyPremiumDiscount(structure, lastPrice);

  // 5. Liquidity Zones + Confluence Scoring
  const rawZones = detectLiquidityZones(candles);
  for (const zone of rawZones) {
    zone.confluenceStars = scoreZoneConfluence(
      zone, bullOBs, bearOBs, structure, premiumDiscount.zone, atr
    );
  }
  // Sort by confluence stars descending
  rawZones.sort((a, b) => b.confluenceStars - a.confluenceStars);

  // 6. MTF Sweeps
  const mtfSweeps = mtfCandles ? aggregateMtfSweeps(mtfCandles) : {
    bullScore: 0, bearScore: 0, sweeps: [], aggregateSide: 'NEUTRAL' as const
  };

  // 7. Attack Force
  const attackForces = detectAttackForce(candles);

  // 8. Swing points
  const { highs, lows } = detectSwings(candles, 5);
  const swingHighs = highs.slice(-5).map(h => h.price);
  const swingLows = lows.slice(-5).map(l => l.price);

  return {
    activeFVGs,
    bullOBs,
    bearOBs,
    liquidityZones: rawZones,
    structure,
    mtfSweeps,
    attackForces,
    premiumDiscount,
    swingHighs,
    swingLows,
    symbol,
    timeframe,
    lastPrice,
    timestamp: Date.now(),
  };
}

/**
 * Classify Premium/Discount zone from structure.
 */
function classifyPremiumDiscount(
  structure: ReturnType<typeof detectMarketStructure>,
  currentPrice: number
): PremiumDiscountResult {
  const { structureHigh, structureLow } = structure;
  if (structureHigh <= 0 || structureLow <= 0 || structureHigh <= structureLow) {
    return { zone: 'EQ', pctFromRange: 50, structureHigh: currentPrice, structureLow: currentPrice, equilibrium: currentPrice };
  }
  const range = structureHigh - structureLow;
  const equilibrium = structureLow + range * 0.5;
  const pct = ((currentPrice - structureLow) / range) * 100;

  let zone: PremiumDiscountZone = 'EQ';
  if (pct > 62) zone = 'PREMIUM';
  else if (pct < 38) zone = 'DISCOUNT';

  return { zone, pctFromRange: Math.round(pct * 10) / 10, structureHigh, structureLow, equilibrium };
}

function emptySnapshot(symbol: string, timeframe: string, lastPrice: number): SmcSnapshot {
  return {
    activeFVGs: [], bullOBs: [], bearOBs: [], liquidityZones: [],
    structure: {
      direction: 'INIT', structureHigh: 0, structureLow: 0,
      structureHighBarIndex: 0, structureLowBarIndex: 0,
      lastEvent: 'NONE', lastEventSide: null, continuationCount: 0,
      idmHigh: null, idmLow: null, idmHighSwept: false, idmLowSwept: false,
    },
    mtfSweeps: { bullScore: 0, bearScore: 0, sweeps: [], aggregateSide: 'NEUTRAL' },
    attackForces: [],
    premiumDiscount: { zone: 'EQ', pctFromRange: 50, structureHigh: lastPrice, structureLow: lastPrice, equilibrium: lastPrice },
    swingHighs: [], swingLows: [],
    symbol, timeframe, lastPrice, timestamp: Date.now(),
  };
}

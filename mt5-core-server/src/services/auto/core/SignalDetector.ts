/**
 * Signal Detector — EA Brain (Phase 3)
 * ตรวจจับจังหวะเข้า-ออก trade จาก SMC data + Indicators
 * ทำงานแบบ deterministic — ไม่ต้องรอ AI
 *
 * Entry Conditions:
 * 1. OB_BOUNCE:      ราคาชนขอบ Active OB + FVG confirm + MTF aligned
 * 2. LIQ_SWEEP:      Liquidity sweep + wick reclaim
 * 3. STRUCTURE_BREAK: CHoCH/BOS confirmed + re-enter at new OB
 * 4. FVG_FILL:       Price fills 50% of FVG + trend aligned
 *
 * 2026-05-06 — skyliner.jojo@gmail.com
 */

import type { SmcSnapshot, OrderBlock, FVG, LiquidityZone, PriceMap, PriceWall } from '../analyzers/smc/types.js';
import { analyzePath } from '../analyzers/smc/priceMapBuilder.js';
import type { AnalysisSummary, StrategyType, Bias } from '../types.js';
import { applyIndicatorConfluence } from './IndicatorConfluence.js';

export interface EntrySignal {
  symbol: string;
  side: 'BUY' | 'SELL';
  entry: number;
  sl: number;
  tp: number;
  confidence: number;         // 0-100
  confluenceStars: number;    // 0-5
  strategy: StrategyType;
  triggers: string[];
  riskRewardRatio: number;
  // V21.1 — Path Analysis (ถ้ามี PriceMap)
  pathAnalysis?: {
    clearPathProbability: number;   // โอกาสถึง TP (0-100)
    obstacleCount: number;          // จำนวนกำแพงขวาง
    biggestObstaclePrice?: number;  // กำแพงที่ใหญ่สุด
    biggestObstacleStars?: number;
    conservativeTP: number;         // TP แบบ conservative
    aggressiveTP: number;           // TP แบบ aggressive
    note: string;
  };
}

export interface SignalDetectorConfig {
  minConfluenceStars: number;  // min stars to trigger (default: 2)
  minRRR: number;              // min risk-reward ratio (default: 1.2)
  obProximityPct: number;      // % tolerance for OB hit (default: 0.15%)
  fvgFillPct: number;          // how much of FVG must be filled (default: 50%)
  enableOBBounce: boolean;
  enableLiqSweep: boolean;
  enableStructureBreak: boolean;
  enableFvgFill: boolean;
  enableSweepToFvg: boolean;
}

const DEFAULT_CONFIG: SignalDetectorConfig = {
  minConfluenceStars: 2,
  minRRR: 1.2,
  obProximityPct: 0.15,
  fvgFillPct: 50,
  enableOBBounce: true,
  enableLiqSweep: true,
  enableStructureBreak: true,
  enableFvgFill: true,
  enableSweepToFvg: true,
};

/**
 * Main detection function — runs every time indicators update
 *
 * V21.1: รับ PriceMap เพิ่มเติมเพื่อใช้ path analysis
 */
export function detectEntrySignals(
  symbol: string,
  lastPrice: number,
  smc: SmcSnapshot,
  analyses: Record<string, AnalysisSummary>,
  priceMap?: PriceMap,
  config: Partial<SignalDetectorConfig> = {}
): EntrySignal[] {
  const cfg = { ...DEFAULT_CONFIG, ...config };
  const signals: EntrySignal[] = [];

  if (!smc || !smc.structure) return signals;

  // MTF bias computation
  const htfBias = computeHtfBias(analyses);
  const structureDir = smc.structure.direction;

  // 1. OB Bounce Entry
  if (cfg.enableOBBounce) {
    const obSignals = detectOBBounce(symbol, lastPrice, smc, htfBias, cfg, priceMap);
    signals.push(...obSignals);
  }

  // 2. Liquidity Sweep Entry
  if (cfg.enableLiqSweep) {
    const sweepSignals = detectLiquiditySweep(symbol, lastPrice, smc, htfBias, cfg, priceMap);
    signals.push(...sweepSignals);
  }

  // 3. Structure Break Entry (CHoCH/BOS)
  if (cfg.enableStructureBreak) {
    const structSignals = detectStructureBreak(symbol, lastPrice, smc, htfBias, cfg, priceMap);
    signals.push(...structSignals);
  }

  // 4. FVG Fill Entry
  if (cfg.enableFvgFill) {
    const fvgSignals = detectFvgFill(symbol, lastPrice, smc, htfBias, cfg, priceMap);
    signals.push(...fvgSignals);
  }

  // 5. Sweep to FVG Magnet Entry (V24.2 - Human Scalp Logic)
  if (cfg.enableSweepToFvg) {
    const magnetSignals = detectSweepToFvg(symbol, lastPrice, smc, htfBias, cfg);
    signals.push(...magnetSignals);
  }

  // 6. RSI Divergence Entry (V24.1.5)
  const entryTfAnalysis = analyses['M5'] ?? analyses['M15'] ?? analyses['H1'];
  const tfCandles = entryTfAnalysis?.candles || [];
  const divSignals = detectRsiDivergence(symbol, lastPrice, tfCandles, smc, priceMap);
  signals.push(...divSignals);

  // Filter by minimum RRR and confluence
  const passedBasic = signals.filter(s =>
    s.riskRewardRatio >= cfg.minRRR &&
    s.confluenceStars >= cfg.minConfluenceStars
  );

  // V22.0: Apply Indicator Confluence — RSI / MACD / Stochastic / BB active scoring
  // ใช้ analysis ของ entry TF (M5 → M15 → H1 fallback) เป็น indicator source
  const filtered: EntrySignal[] = [];
  for (const signal of passedBasic) {
    const adjusted = applyIndicatorConfluence(signal, entryTfAnalysis);
    if (adjusted !== null) filtered.push(adjusted);
  }

  return filtered;
}

// ── OB Bounce ──────────────────────────────────────────────────────

function detectOBBounce(
  symbol: string,
  lastPrice: number,
  smc: SmcSnapshot,
  htfBias: { bullCount: number; bearCount: number; dominant: Bias },
  cfg: SignalDetectorConfig,
  priceMap?: PriceMap
): EntrySignal[] {
  const signals: EntrySignal[] = [];
  const tolerance = lastPrice * (cfg.obProximityPct / 100);

  // Bullish OB bounce (buy from demand zone)
  for (const ob of smc.bullOBs.filter(ob => !ob.mitigated && ob.hasFVG)) {
    if (lastPrice >= ob.bottom - tolerance && lastPrice <= ob.top + tolerance) {
      if (htfBias.dominant !== 'BEAR') {
        const sl = ob.bottom - tolerance * 2;
        const nearZone = smc.liquidityZones.find(z =>
          !z.isHigh && Math.abs(z.price - ob.bottom) < tolerance * 3
        );
        const stars = nearZone?.confluenceStars ?? 1;

        // V21.1: ใช้ PriceMap ถ้ามี (แม่นกว่า)
        let tp: number;
        let pathAnalysis: EntrySignal['pathAnalysis'];
        if (priceMap) {
          const result = findTPFromPriceMap(lastPrice, 'UP', priceMap, sl);
          tp = result.tp;
          pathAnalysis = result.pathAnalysis;
        } else {
          tp = findNearestLevel(lastPrice, smc, 'above') || lastPrice + (lastPrice - sl) * 2;
        }

        const rrr = Math.abs(tp - lastPrice) / Math.abs(lastPrice - sl);
        signals.push({
          symbol, side: 'BUY', entry: lastPrice,
          sl: round(sl), tp: round(tp),
          confidence: Math.min(90, 50 + stars * 8 + (ob.hasFVG ? 10 : 0) + htfBias.bullCount * 5),
          confluenceStars: stars,
          strategy: 'SMC_FVG_REVERSAL',
          triggers: [`OB_BOUNCE at ${round(ob.bottom)}-${round(ob.top)}`, `FVG confirmed`, `HTF bias: ${htfBias.dominant}`],
          riskRewardRatio: round(rrr),
          pathAnalysis,
        });
      }
    }
  }

  // Bearish OB bounce (sell from supply zone)
  for (const ob of smc.bearOBs.filter(ob => !ob.mitigated && ob.hasFVG)) {
    if (lastPrice >= ob.bottom - tolerance && lastPrice <= ob.top + tolerance) {
      if (htfBias.dominant !== 'BULL') {
        const sl = ob.top + tolerance * 2;
        const nearZone = smc.liquidityZones.find(z =>
          z.isHigh && Math.abs(z.price - ob.top) < tolerance * 3
        );
        const stars = nearZone?.confluenceStars ?? 1;

        let tp: number;
        let pathAnalysis: EntrySignal['pathAnalysis'];
        if (priceMap) {
          const result = findTPFromPriceMap(lastPrice, 'DOWN', priceMap, sl);
          tp = result.tp;
          pathAnalysis = result.pathAnalysis;
        } else {
          tp = findNearestLevel(lastPrice, smc, 'below') || lastPrice - (sl - lastPrice) * 2;
        }

        const rrr = Math.abs(lastPrice - tp) / Math.abs(sl - lastPrice);
        signals.push({
          symbol, side: 'SELL', entry: lastPrice,
          sl: round(sl), tp: round(tp),
          confidence: Math.min(90, 50 + stars * 8 + (ob.hasFVG ? 10 : 0) + htfBias.bearCount * 5),
          confluenceStars: stars,
          strategy: 'SMC_FVG_REVERSAL',
          triggers: [`OB_BOUNCE at ${round(ob.bottom)}-${round(ob.top)}`, `FVG confirmed`, `HTF bias: ${htfBias.dominant}`],
          riskRewardRatio: round(rrr),
          pathAnalysis,
        });
      }
    }
  }

  return signals;
}

// ── Liquidity Sweep ────────────────────────────────────────────────

function detectLiquiditySweep(
  symbol: string,
  lastPrice: number,
  smc: SmcSnapshot,
  htfBias: { bullCount: number; bearCount: number; dominant: Bias },
  cfg: SignalDetectorConfig,
  priceMap?: PriceMap
): EntrySignal[] {
  const signals: EntrySignal[] = [];

  // MTF sweep must agree
  if (smc.mtfSweeps.bullScore >= 2 && htfBias.dominant !== 'BEAR') {
    const sl = Math.min(...smc.swingLows.slice(-2), lastPrice * 0.998);
    const stars = Math.min(5, smc.mtfSweeps.bullScore + 1);

    let tp: number;
    let pathAnalysis: EntrySignal['pathAnalysis'];
    if (priceMap) {
      const result = findTPFromPriceMap(lastPrice, 'UP', priceMap, sl);
      tp = result.tp;
      pathAnalysis = result.pathAnalysis;
    } else {
      tp = findNearestLevel(lastPrice, smc, 'above') || lastPrice * 1.005;
    }
    const rrr = Math.abs(tp - lastPrice) / Math.abs(lastPrice - sl);

    signals.push({
      symbol, side: 'BUY', entry: lastPrice,
      sl: round(sl), tp: round(tp),
      confidence: Math.min(85, 55 + smc.mtfSweeps.bullScore * 10),
      confluenceStars: stars,
      strategy: 'SMC_FVG_SCALP',
      triggers: [`MTF_SWEEP bull (${smc.mtfSweeps.bullScore} TFs)`, `Sweep reclaim`],
      riskRewardRatio: round(rrr),
      pathAnalysis,
    });
  }

  if (smc.mtfSweeps.bearScore >= 2 && htfBias.dominant !== 'BULL') {
    const sl = Math.max(...smc.swingHighs.slice(-2), lastPrice * 1.002);
    const stars = Math.min(5, smc.mtfSweeps.bearScore + 1);

    let tp: number;
    let pathAnalysis: EntrySignal['pathAnalysis'];
    if (priceMap) {
      const result = findTPFromPriceMap(lastPrice, 'DOWN', priceMap, sl);
      tp = result.tp;
      pathAnalysis = result.pathAnalysis;
    } else {
      tp = findNearestLevel(lastPrice, smc, 'below') || lastPrice * 0.995;
    }
    const rrr = Math.abs(lastPrice - tp) / Math.abs(sl - lastPrice);

    signals.push({
      symbol, side: 'SELL', entry: lastPrice,
      sl: round(sl), tp: round(tp),
      confidence: Math.min(85, 55 + smc.mtfSweeps.bearScore * 10),
      confluenceStars: stars,
      strategy: 'SMC_FVG_SCALP',
      triggers: [`MTF_SWEEP bear (${smc.mtfSweeps.bearScore} TFs)`, `Sweep reclaim`],
      riskRewardRatio: round(rrr),
      pathAnalysis,
    });
  }

  return signals;
}

// ── Structure Break ────────────────────────────────────────────────

function detectStructureBreak(
  symbol: string,
  lastPrice: number,
  smc: SmcSnapshot,
  htfBias: { bullCount: number; bearCount: number; dominant: Bias },
  cfg: SignalDetectorConfig,
  priceMap?: PriceMap
): EntrySignal[] {
  const signals: EntrySignal[] = [];
  const { structure } = smc;

  const buildTP = (dir: 'UP' | 'DOWN', sl: number, fallback: number) => {
    if (priceMap) return findTPFromPriceMap(lastPrice, dir, priceMap, sl);
    return { tp: fallback, pathAnalysis: undefined };
  };

  if (structure.lastEvent === 'CHoCH') {
    if (structure.lastEventSide === 'UP' && htfBias.dominant !== 'BEAR') {
      const nearestBullOB = smc.bullOBs[0];
      if (nearestBullOB) {
        const sl = nearestBullOB.bottom - (lastPrice * 0.001);
        const { tp, pathAnalysis } = buildTP('UP', sl, structure.structureHigh);
        const rrr = Math.abs(tp - lastPrice) / Math.abs(lastPrice - sl);
        signals.push({
          symbol, side: 'BUY', entry: lastPrice,
          sl: round(sl), tp: round(tp),
          confidence: Math.min(85, 60 + (structure.idmLowSwept ? 15 : 0)),
          confluenceStars: structure.idmLowSwept ? 4 : 3,
          strategy: 'SMC_FVG_REVERSAL',
          triggers: ['CHoCH_UP', structure.idmLowSwept ? 'IDM swept' : 'No IDM'],
          riskRewardRatio: round(rrr), pathAnalysis,
        });
      }
    }

    if (structure.lastEventSide === 'DOWN' && htfBias.dominant !== 'BULL') {
      const nearestBearOB = smc.bearOBs[0];
      if (nearestBearOB) {
        const sl = nearestBearOB.top + (lastPrice * 0.001);
        const { tp, pathAnalysis } = buildTP('DOWN', sl, structure.structureLow);
        const rrr = Math.abs(lastPrice - tp) / Math.abs(sl - lastPrice);
        signals.push({
          symbol, side: 'SELL', entry: lastPrice,
          sl: round(sl), tp: round(tp),
          confidence: Math.min(85, 60 + (structure.idmHighSwept ? 15 : 0)),
          confluenceStars: structure.idmHighSwept ? 4 : 3,
          strategy: 'SMC_FVG_REVERSAL',
          triggers: ['CHoCH_DOWN', structure.idmHighSwept ? 'IDM swept' : 'No IDM'],
          riskRewardRatio: round(rrr), pathAnalysis,
        });
      }
    }
  }

  if (structure.lastEvent === 'SMS' || structure.lastEvent === 'BMS') {
    if (structure.direction === 'BULLISH' && htfBias.dominant !== 'BEAR') {
      const sl = structure.structureLow - (lastPrice * 0.0005);
      const { tp, pathAnalysis } = buildTP('UP', sl, lastPrice * 1.003);
      const rrr = Math.abs(tp - lastPrice) / Math.abs(lastPrice - sl);
      signals.push({
        symbol, side: 'BUY', entry: lastPrice, sl: round(sl), tp: round(tp),
        confidence: Math.min(75, 50 + structure.continuationCount * 3), confluenceStars: 2,
        strategy: 'SMC_FVG_CONTINUATION',
        triggers: [`${structure.lastEvent}_UP`, `Continuation #${structure.continuationCount}`],
        riskRewardRatio: round(rrr), pathAnalysis,
      });
    }
    if (structure.direction === 'BEARISH' && htfBias.dominant !== 'BULL') {
      const sl = structure.structureHigh + (lastPrice * 0.0005);
      const { tp, pathAnalysis } = buildTP('DOWN', sl, lastPrice * 0.997);
      const rrr = Math.abs(lastPrice - tp) / Math.abs(sl - lastPrice);
      signals.push({
        symbol, side: 'SELL', entry: lastPrice, sl: round(sl), tp: round(tp),
        confidence: Math.min(75, 50 + structure.continuationCount * 3), confluenceStars: 2,
        strategy: 'SMC_FVG_CONTINUATION',
        triggers: [`${structure.lastEvent}_DOWN`, `Continuation #${structure.continuationCount}`],
        riskRewardRatio: round(rrr), pathAnalysis,
      });
    }
  }

  return signals;
}

// ── FVG Fill ───────────────────────────────────────────────────────

function detectFvgFill(
  symbol: string,
  lastPrice: number,
  smc: SmcSnapshot,
  htfBias: { bullCount: number; bearCount: number; dominant: Bias },
  cfg: SignalDetectorConfig,
  priceMap?: PriceMap
): EntrySignal[] {
  const signals: EntrySignal[] = [];
  const fillThreshold = cfg.fvgFillPct / 100;

  for (const fvg of smc.activeFVGs.filter(f => !f.mitigated)) {
    const fvgSize = Math.abs(fvg.top - fvg.bottom);

    if (fvg.type === 'BULL' && htfBias.dominant !== 'BEAR') {
      const fillLevel = fvg.top - fvgSize * fillThreshold;
      if (lastPrice <= fillLevel && lastPrice >= fvg.bottom) {
        const sl = fvg.bottom - fvgSize * 0.5;
        let tp: number;
        let pathAnalysis: EntrySignal['pathAnalysis'];
        if (priceMap) {
          const r = findTPFromPriceMap(lastPrice, 'UP', priceMap, sl);
          tp = r.tp; pathAnalysis = r.pathAnalysis;
        } else {
          tp = findNearestLevel(lastPrice, smc, 'above') || lastPrice + fvgSize * 2;
        }
        const rrr = Math.abs(tp - lastPrice) / Math.abs(lastPrice - sl);
        signals.push({
          symbol, side: 'BUY', entry: lastPrice, sl: round(sl), tp: round(tp),
          confidence: Math.min(70, 45 + htfBias.bullCount * 5), confluenceStars: 2,
          strategy: 'SMC_FVG_SCALP',
          triggers: [`FVG_FILL bull at ${round(fvg.bottom)}-${round(fvg.top)}`, `${Math.round(fillThreshold * 100)}% filled`],
          riskRewardRatio: round(rrr), pathAnalysis,
        });
      }
    }

    if (fvg.type === 'BEAR' && htfBias.dominant !== 'BULL') {
      const fillLevel = fvg.bottom + fvgSize * fillThreshold;
      if (lastPrice >= fillLevel && lastPrice <= fvg.top) {
        const sl = fvg.top + fvgSize * 0.5;
        let tp: number;
        let pathAnalysis: EntrySignal['pathAnalysis'];
        if (priceMap) {
          const r = findTPFromPriceMap(lastPrice, 'DOWN', priceMap, sl);
          tp = r.tp; pathAnalysis = r.pathAnalysis;
        } else {
          tp = findNearestLevel(lastPrice, smc, 'below') || lastPrice - fvgSize * 2;
        }
        const rrr = Math.abs(lastPrice - tp) / Math.abs(sl - lastPrice);
        signals.push({
          symbol, side: 'SELL', entry: lastPrice, sl: round(sl), tp: round(tp),
          confidence: Math.min(70, 45 + htfBias.bearCount * 5), confluenceStars: 2,
          strategy: 'SMC_FVG_SCALP',
          triggers: [`FVG_FILL bear at ${round(fvg.bottom)}-${round(fvg.top)}`, `${Math.round(fillThreshold * 100)}% filled`],
          riskRewardRatio: round(rrr), pathAnalysis,
        });
      }
    }
  }

  return signals;
}

// ── Sweep to FVG (Magnet) ──────────────────────────────────────────

function detectSweepToFvg(
  symbol: string,
  lastPrice: number,
  smc: SmcSnapshot,
  htfBias: { bullCount: number; bearCount: number; dominant: Bias },
  cfg: SignalDetectorConfig
): EntrySignal[] {
  const signals: EntrySignal[] = [];

  // BUY: If recent bull sweep (swept support) -> target nearest unmitigated bear FVG above
  if (smc.mtfSweeps.bullScore >= 1 && htfBias.dominant !== 'BEAR') {
    const targetFvg = smc.activeFVGs
      .filter(f => f.type === 'BEAR' && !f.mitigated && f.bottom > lastPrice)
      .sort((a, b) => a.bottom - b.bottom)[0];
      
    if (targetFvg) {
      const tp = targetFvg.bottom; // TP exactly at the start of the FVG
      const sl = Math.min(...smc.swingLows.slice(-2), lastPrice * 0.998); // SL below recent swing low
      const rrr = Math.abs(tp - lastPrice) / Math.abs(lastPrice - sl);
      
      signals.push({
        symbol, side: 'BUY', entry: lastPrice,
        sl: round(sl), tp: round(tp),
        confidence: Math.min(85, 75 + smc.mtfSweeps.bullScore * 5),
        confluenceStars: 3,
        strategy: 'SMC_FVG_MAGNET_SCALP',
        triggers: [`LIQ_SWEEP_BULL`, `Magnet target FVG at ${round(tp)}`],
        riskRewardRatio: round(rrr)
      });
    }
  }

  // SELL: If recent bear sweep (swept resistance) -> target nearest unmitigated bull FVG below
  if (smc.mtfSweeps.bearScore >= 1 && htfBias.dominant !== 'BULL') {
    const targetFvg = smc.activeFVGs
      .filter(f => f.type === 'BULL' && !f.mitigated && f.top < lastPrice)
      .sort((a, b) => b.top - a.top)[0];
      
    if (targetFvg) {
      const tp = targetFvg.top; // TP exactly at the start of the FVG
      const sl = Math.max(...smc.swingHighs.slice(-2), lastPrice * 1.002); // SL above recent swing high
      const rrr = Math.abs(lastPrice - tp) / Math.abs(sl - lastPrice);
      
      signals.push({
        symbol, side: 'SELL', entry: lastPrice,
        sl: round(sl), tp: round(tp),
        confidence: Math.min(85, 75 + smc.mtfSweeps.bearScore * 5),
        confluenceStars: 3,
        strategy: 'SMC_FVG_MAGNET_SCALP',
        triggers: [`LIQ_SWEEP_BEAR`, `Magnet target FVG at ${round(tp)}`],
        riskRewardRatio: round(rrr)
      });
    }
  }

  return signals;
}

// ── Helpers ────────────────────────────────────────────────────────

function computeHtfBias(analyses: Record<string, AnalysisSummary>): {
  bullCount: number; bearCount: number; dominant: Bias;
} {
  let bullCount = 0, bearCount = 0;
  for (const tf of ['H4', 'H1', 'M15']) {
    const a = analyses[tf];
    if (!a) continue;
    if (a.bias === 'BULL') bullCount++;
    else if (a.bias === 'BEAR') bearCount++;
  }
  const dominant: Bias = bullCount > bearCount ? 'BULL' : bearCount > bullCount ? 'BEAR' : 'NEUTRAL';
  return { bullCount, bearCount, dominant };
}

/**
 * หา TP จาก PriceMap (V21.1) — แม่นยำกว่า findNearestLevel()
 */
function findTPFromPriceMap(
  entryPrice: number,
  direction: 'UP' | 'DOWN',
  priceMap: PriceMap,
  slPrice: number
): { tp: number; pathAnalysis: EntrySignal['pathAnalysis'] } {
  const path = analyzePath(priceMap, entryPrice, direction);
  const tp = path.suggestedTP;
  const rawRRR = Math.abs(tp - entryPrice) / Math.abs(entryPrice - slPrice);
  const finalTP = rawRRR < 1.0 ? path.aggressiveTP : tp;
  return {
    tp: round(finalTP),
    pathAnalysis: {
      clearPathProbability: path.clearPathProbability,
      obstacleCount: path.obstacles.length,
      biggestObstaclePrice: path.biggestObstacle?.wall.price,
      biggestObstacleStars: path.biggestObstacle?.wall.confluenceStars,
      conservativeTP: round(path.conservativeTP),
      aggressiveTP: round(path.aggressiveTP),
      note: path.note,
    },
  };
}

/**
 * findNearestLevel — fallback เมื่อไม่มี PriceMap
 */
function findNearestLevel(price: number, smc: SmcSnapshot, direction: 'above' | 'below'): number | null {
  const candidates: number[] = [];
  if (direction === 'above') {
    candidates.push(...smc.swingHighs.filter(h => h > price));
    candidates.push(...smc.liquidityZones.filter(z => z.isHigh && z.price > price).map(z => z.price));
    candidates.push(...smc.bearOBs.filter(ob => ob.bottom > price).map(ob => ob.bottom));
  } else {
    candidates.push(...smc.swingLows.filter(l => l < price));
    candidates.push(...smc.liquidityZones.filter(z => !z.isHigh && z.price < price).map(z => z.price));
    candidates.push(...smc.bullOBs.filter(ob => ob.top < price).map(ob => ob.top));
  }
  if (candidates.length === 0) return null;
  candidates.sort((a, b) => Math.abs(a - price) - Math.abs(b - price));
  return candidates[0];
}

function round(n: number): number {
  return Math.round(n * 100) / 100;
}

// ── V24.1.5: RSI Divergence Detection ──────────────────────────────

function calculateRSI(closes: number[], period = 14): number | null {
  if (closes.length <= period) return null;
  let gains = 0;
  let losses = 0;
  for (let i = 1; i <= period; i += 1) {
    const diff = closes[i] - closes[i - 1];
    if (diff >= 0) gains += diff;
    else losses += Math.abs(diff);
  }
  let avgGain = gains / period;
  let avgLoss = losses / period;
  for (let i = period + 1; i < closes.length; i += 1) {
    const diff = closes[i] - closes[i - 1];
    const gain = diff > 0 ? diff : 0;
    const loss = diff < 0 ? Math.abs(diff) : 0;
    avgGain = (avgGain * (period - 1) + gain) / period;
    avgLoss = (avgLoss * (period - 1) + loss) / period;
  }
  if (avgLoss === 0) return 100;
  const rs = avgGain / avgLoss;
  return 100 - 100 / (1 + rs);
}

export function detectRsiDivergence(
  symbol: string,
  lastPrice: number,
  candles: any[],
  smc: SmcSnapshot,
  priceMap?: PriceMap
): EntrySignal[] {
  const signals: EntrySignal[] = [];
  if (!candles || candles.length < 50 || !smc) return signals;

  const closes = candles.map((c: any) => c.c);
  const currentRsi = calculateRSI(closes, 14);
  if (currentRsi === null) return signals;

  // Bullish Divergence: Price Lower Low, RSI Higher Low
  if (smc.swingLows.length >= 1) {
    const prevLowPrice = smc.swingLows[smc.swingLows.length - 1];
    const candleIdx = candles.findIndex((c: any) => c.l === prevLowPrice);
    if (candleIdx > 0 && candleIdx < candles.length - 5) {
      const prevRsi = calculateRSI(closes.slice(0, candleIdx + 1), 14);
      if (prevRsi !== null) {
        if (lastPrice < prevLowPrice && currentRsi > prevRsi && currentRsi < 35) {
          const sl = lastPrice - (lastPrice - prevLowPrice) * 0.5;
          let tp: number;
          if (priceMap) {
            tp = findTPFromPriceMap(lastPrice, 'UP', priceMap, sl).tp;
          } else {
            tp = findNearestLevel(lastPrice, smc, 'above') || lastPrice * 1.005;
          }
          const rrr = Math.abs(tp - lastPrice) / Math.abs(lastPrice - sl);
          if (rrr >= 1.2) {
            signals.push({
              symbol, side: 'BUY', entry: lastPrice,
              sl: round(sl), tp: round(tp),
              confidence: Math.min(85, 60 + (currentRsi - prevRsi) * 2),
              confluenceStars: 3,
              strategy: 'SMC_RSI_DIVERGENCE',
              triggers: [`Bullish Divergence (RSI ${round(prevRsi)} -> ${round(currentRsi)})`, `Price LL (${round(prevLowPrice)} -> ${round(lastPrice)})`],
              riskRewardRatio: round(rrr)
            });
          }
        }
      }
    }
  }

  // Bearish Divergence: Price Higher High, RSI Lower High
  if (smc.swingHighs.length >= 1) {
    const prevHighPrice = smc.swingHighs[smc.swingHighs.length - 1];
    const candleIdx = candles.findIndex((c: any) => c.h === prevHighPrice);
    if (candleIdx > 0 && candleIdx < candles.length - 5) {
      const prevRsi = calculateRSI(closes.slice(0, candleIdx + 1), 14);
      if (prevRsi !== null) {
        if (lastPrice > prevHighPrice && currentRsi < prevRsi && currentRsi > 65) {
          const sl = lastPrice + (prevHighPrice - lastPrice) * 0.5;
          let tp: number;
          if (priceMap) {
            tp = findTPFromPriceMap(lastPrice, 'DOWN', priceMap, sl).tp;
          } else {
            tp = findNearestLevel(lastPrice, smc, 'below') || lastPrice * 0.995;
          }
          const rrr = Math.abs(lastPrice - tp) / Math.abs(sl - lastPrice);
          if (rrr >= 1.2) {
            signals.push({
              symbol, side: 'SELL', entry: lastPrice,
              sl: round(sl), tp: round(tp),
              confidence: Math.min(85, 60 + (prevRsi - currentRsi) * 2),
              confluenceStars: 3,
              strategy: 'SMC_RSI_DIVERGENCE',
              triggers: [`Bearish Divergence (RSI ${round(prevRsi)} -> ${round(currentRsi)})`, `Price HH (${round(prevHighPrice)} -> ${round(lastPrice)})`],
              riskRewardRatio: round(rrr)
            });
          }
        }
      }
    }
  }

  return signals;
}

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

import type { SmcSnapshot, OrderBlock, FVG, LiquidityZone, PriceMap, PriceWall, Candle } from '../analyzers/smc/types.js';
import { analyzePath } from '../analyzers/smc/priceMapBuilder.js';
import { getActiveFVGs } from '../analyzers/smc/fvgDetection.js';
import type { AnalysisSummary, StrategyType, Bias } from '../types.js';
import { applyIndicatorConfluence } from './IndicatorConfluence.js';
import { zoneIsFavorableForSide } from './ZoneAwareGate.js';

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
  enableWallBreakScalp: boolean;
  magnetMinWallStars: number;
  magnetWallMaxAtrMul: number;
  magnetTargetRrr: number;
  wallBreakTargetRrr: number;
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
  enableWallBreakScalp: true,
  magnetMinWallStars: 3,
  magnetWallMaxAtrMul: 0.35,
  magnetTargetRrr: 1.35,
  wallBreakTargetRrr: 1.25,
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
    const magnetSignals = detectSweepToFvg(symbol, lastPrice, analyses, priceMap, cfg);
    signals.push(...magnetSignals);
  }

  // 6. Wall Break Scalp (V26.14): no M15 FVG required, but M1+M5 must
  // both confirm the break and both execution TFs must be in the right PD zone.
  if (cfg.enableWallBreakScalp) {
    const wallBreakSignals = detectWallBreakScalp(symbol, lastPrice, analyses, priceMap, cfg);
    signals.push(...wallBreakSignals);
  }

  // 7. RSI Divergence Entry (V24.1.5)
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
    if (signal.strategy === 'SMC_FVG_MAGNET_SCALP' || signal.strategy === 'SMC_WALL_BREAK_SCALP') {
      filtered.push(signal);
      continue;
    }
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
      strategy: 'SCALPING',
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
      strategy: 'SCALPING',
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
  analyses: Record<string, AnalysisSummary>,
  priceMap: PriceMap | undefined,
  cfg: SignalDetectorConfig
): EntrySignal[] {
  const signals: EntrySignal[] = [];
  if (!priceMap) return signals;

  const m15Candles = analyses['M15']?.candles ?? [];
  const m15Fvgs = m15Candles.length >= 20 ? getActiveFVGs(m15Candles as Candle[]) : [];
  const confirmTf = analyses['M1']?.candles?.length ? 'M1' : 'M5';
  const confirmCandles = analyses[confirmTf]?.candles;
  if (!confirmCandles || confirmCandles.length < 4) return signals;

  const buyWall = findMagnetWall('BUY', lastPrice, priceMap, cfg);
  if (buyWall) {
    const targetFvg = findMagnetTargetFvg('BUY', lastPrice, buyWall.wall, m15Fvgs);
    const breakConfirm = targetFvg ? detectWallReclaimBreak('BUY', buyWall.wall, confirmCandles, priceMap.atr, confirmTf) : null;
    if (targetFvg && breakConfirm?.ok) {
      const tp = targetFvg.bottom;
      const reward = Math.abs(tp - lastPrice);
      const sl = lastPrice - reward / cfg.magnetTargetRrr;
      const rrr = reward / Math.abs(lastPrice - sl);

      signals.push({
        symbol, side: 'BUY', entry: lastPrice,
        sl: round(sl), tp: round(tp),
        confidence: Math.min(88, 58 + buyWall.wall.confluenceStars * 6 + Math.min(10, reward / Math.max(priceMap.atr, 0.0001) * 3)),
        confluenceStars: Math.min(5, buyWall.wall.confluenceStars),
        strategy: 'SMC_FVG_MAGNET_SCALP',
        triggers: [
          `WALL_RECLAIM_BUY ${buyWall.wall.confluenceStars}star @ ${round(buyWall.wall.price)}`,
          `M15_BEAR_FVG_MAGNET ${round(targetFvg.bottom)}-${round(targetFvg.top)}`,
          breakConfirm.note,
        ],
        riskRewardRatio: round(rrr),
        pathAnalysis: buildMagnetPathAnalysis(lastPrice, 'UP', tp, priceMap),
      });
    }
  }

  const sellWall = findMagnetWall('SELL', lastPrice, priceMap, cfg);
  if (sellWall) {
    const targetFvg = findMagnetTargetFvg('SELL', lastPrice, sellWall.wall, m15Fvgs);
    const breakConfirm = targetFvg ? detectWallReclaimBreak('SELL', sellWall.wall, confirmCandles, priceMap.atr, confirmTf) : null;
    if (targetFvg && breakConfirm?.ok) {
      const tp = targetFvg.top;
      const reward = Math.abs(lastPrice - tp);
      const sl = lastPrice + reward / cfg.magnetTargetRrr;
      const rrr = reward / Math.abs(sl - lastPrice);

      signals.push({
        symbol, side: 'SELL', entry: lastPrice,
        sl: round(sl), tp: round(tp),
        confidence: Math.min(88, 58 + sellWall.wall.confluenceStars * 6 + Math.min(10, reward / Math.max(priceMap.atr, 0.0001) * 3)),
        confluenceStars: Math.min(5, sellWall.wall.confluenceStars),
        strategy: 'SMC_FVG_MAGNET_SCALP',
        triggers: [
          `WALL_RECLAIM_SELL ${sellWall.wall.confluenceStars}star @ ${round(sellWall.wall.price)}`,
          `M15_BULL_FVG_MAGNET ${round(targetFvg.bottom)}-${round(targetFvg.top)}`,
          breakConfirm.note,
        ],
        riskRewardRatio: round(rrr),
        pathAnalysis: buildMagnetPathAnalysis(lastPrice, 'DOWN', tp, priceMap),
      });
    }
  }

  return signals;
}

// ── Helpers ────────────────────────────────────────────────────────

function detectWallBreakScalp(
  symbol: string,
  lastPrice: number,
  analyses: Record<string, AnalysisSummary>,
  priceMap: PriceMap | undefined,
  cfg: SignalDetectorConfig,
): EntrySignal[] {
  const signals: EntrySignal[] = [];
  if (!priceMap) return signals;

  const m1Candles = analyses['M1']?.candles as Candle[] | undefined;
  const m5Candles = analyses['M5']?.candles as Candle[] | undefined;
  if (!m1Candles || !m5Candles || m1Candles.length < 6 || m5Candles.length < 6) {
    return signals;
  }

  const m1Zone = classifyExecutionPdZone(m1Candles, lastPrice);
  const m5Zone = classifyExecutionPdZone(m5Candles, lastPrice);

  const buyWall = findMagnetWall('BUY', lastPrice, priceMap, cfg);
  if (
    buyWall &&
    (m1Zone.zone === 'DISCOUNT' || m1Zone.zone === 'EQ') &&
    (m5Zone.zone === 'DISCOUNT' || m5Zone.zone === 'EQ')
  ) {
    const m1Break = detectWallReclaimBreak('BUY', buyWall.wall, m1Candles, priceMap.atr, 'M1');
    const m5Break = detectWallReclaimBreak('BUY', buyWall.wall, m5Candles, priceMap.atr, 'M5');
    if (m1Break.ok && m5Break.ok) {
      const signal = buildWallBreakScalpSignal({
        symbol,
        side: 'BUY',
        lastPrice,
        wall: buyWall.wall,
        priceMap,
        cfg,
        breakNotes: [m1Break.note, m5Break.note],
        zoneNotes: [`M1_${m1Zone.zone}_${round(m1Zone.pct)}%`, `M5_${m5Zone.zone}_${round(m5Zone.pct)}%`],
      });
      if (signal) signals.push(signal);
    }
  }

  const sellWall = findMagnetWall('SELL', lastPrice, priceMap, cfg);
  if (
    sellWall &&
    (m1Zone.zone === 'PREMIUM' || m1Zone.zone === 'EQ') &&
    (m5Zone.zone === 'PREMIUM' || m5Zone.zone === 'EQ')
  ) {
    const m1Break = detectWallReclaimBreak('SELL', sellWall.wall, m1Candles, priceMap.atr, 'M1');
    const m5Break = detectWallReclaimBreak('SELL', sellWall.wall, m5Candles, priceMap.atr, 'M5');
    if (m1Break.ok && m5Break.ok) {
      const signal = buildWallBreakScalpSignal({
        symbol,
        side: 'SELL',
        lastPrice,
        wall: sellWall.wall,
        priceMap,
        cfg,
        breakNotes: [m1Break.note, m5Break.note],
        zoneNotes: [`M1_${m1Zone.zone}_${round(m1Zone.pct)}%`, `M5_${m5Zone.zone}_${round(m5Zone.pct)}%`],
      });
      if (signal) signals.push(signal);
    }
  }

  return signals;
}

function classifyExecutionPdZone(candles: Candle[], price: number): {
  zone: 'PREMIUM' | 'EQ' | 'DISCOUNT';
  pct: number;
} {
  const lookback = candles.slice(-80);
  const highs = lookback.map((c) => Number(c.h ?? c.c)).filter(Number.isFinite);
  const lows = lookback.map((c) => Number(c.l ?? c.c)).filter(Number.isFinite);
  const high = Math.max(...highs);
  const low = Math.min(...lows);
  if (!Number.isFinite(high) || !Number.isFinite(low) || high <= low) {
    return { zone: 'EQ', pct: 50 };
  }
  const pct = ((price - low) / (high - low)) * 100;
  if (pct > 62) return { zone: 'PREMIUM', pct };
  if (pct < 38) return { zone: 'DISCOUNT', pct };
  return { zone: 'EQ', pct };
}

function buildWallBreakScalpSignal(args: {
  symbol: string;
  side: 'BUY' | 'SELL';
  lastPrice: number;
  wall: PriceWall;
  priceMap: PriceMap;
  cfg: SignalDetectorConfig;
  breakNotes: string[];
  zoneNotes: string[];
}): EntrySignal | null {
  const direction = args.side === 'BUY' ? 'UP' : 'DOWN';
  const path = analyzePath(args.priceMap, args.lastPrice, direction);
  const rawTp = path.suggestedTP;
  const reward = args.side === 'BUY'
    ? rawTp - args.lastPrice
    : args.lastPrice - rawTp;
  if (!Number.isFinite(reward) || reward <= Math.max(args.priceMap.atr * 0.05, Math.abs(args.lastPrice) * 0.00005)) {
    return null;
  }

  const risk = reward / args.cfg.wallBreakTargetRrr;
  const sl = args.side === 'BUY'
    ? args.lastPrice - risk
    : args.lastPrice + risk;
  const rrr = reward / Math.abs(args.lastPrice - sl);

  return {
    symbol: args.symbol,
    side: args.side,
    entry: args.lastPrice,
    sl: round(sl),
    tp: round(rawTp),
    confidence: Math.min(86, 56 + args.wall.confluenceStars * 5 + Math.min(10, path.clearPathProbability / 12)),
    confluenceStars: Math.min(5, args.wall.confluenceStars),
    strategy: 'SMC_WALL_BREAK_SCALP',
    triggers: [
      `WALL_BREAK_${args.side} ${args.wall.confluenceStars}star @ ${round(args.wall.price)}`,
      ...args.breakNotes,
      ...args.zoneNotes,
      `TP_PATH_${direction} ${round(rawTp)}`,
    ],
    riskRewardRatio: round(rrr),
    pathAnalysis: buildMagnetPathAnalysis(args.lastPrice, direction, rawTp, args.priceMap),
  };
}

function findMagnetWall(
  side: 'BUY' | 'SELL',
  lastPrice: number,
  priceMap: PriceMap,
  cfg: SignalDetectorConfig,
): { wall: PriceWall; distance: number } | null {
  const candidates = side === 'BUY' ? priceMap.supportWalls : priceMap.resistanceWalls;
  const maxDistance = Math.max(
    priceMap.atr * cfg.magnetWallMaxAtrMul,
    Math.abs(lastPrice) * 0.00015,
  );

  const valid = candidates
    .filter((wall) => wall.confluenceStars >= cfg.magnetMinWallStars)
    .map((wall) => ({ wall, distance: Math.abs(lastPrice - wall.price) }))
    .filter((item) => {
      const wallRange = Math.abs(item.wall.priceTop - item.wall.priceBottom);
      return item.distance <= Math.max(maxDistance, wallRange * 0.6);
    })
    .sort((a, b) =>
      b.wall.confluenceStars - a.wall.confluenceStars ||
      a.distance - b.distance
    );

  return valid[0] ?? null;
}

function findMagnetTargetFvg(
  side: 'BUY' | 'SELL',
  lastPrice: number,
  wall: PriceWall,
  fvgs: FVG[],
): FVG | null {
  const targetSide = side === 'BUY' ? 'BEAR' : 'BULL';
  const boundary = side === 'BUY'
    ? Math.max(lastPrice, wall.price)
    : Math.min(lastPrice, wall.price);

  const candidates = fvgs
    .filter((fvg) => fvg.type === targetSide && !fvg.mitigated)
    .filter((fvg) => side === 'BUY' ? fvg.bottom > boundary : fvg.top < boundary)
    .sort((a, b) =>
      (a.age ?? Number.MAX_SAFE_INTEGER) - (b.age ?? Number.MAX_SAFE_INTEGER) ||
      Math.abs((side === 'BUY' ? a.bottom : a.top) - lastPrice) -
        Math.abs((side === 'BUY' ? b.bottom : b.top) - lastPrice)
    );

  return candidates[0] ?? null;
}

function detectWallReclaimBreak(
  side: 'BUY' | 'SELL',
  wall: PriceWall,
  candles: any[],
  atr: number,
  timeframe: string,
): { ok: boolean; note: string } {
  const recent = candles.slice(-6);
  const last = recent[recent.length - 1];
  const prev = recent[recent.length - 2] ?? last;
  const prior = recent.slice(0, -1);
  const wallRange = Math.abs(wall.priceTop - wall.priceBottom);
  const tolerance = Math.max(atr * 0.05, Math.abs(wall.price) * 0.00005, wallRange * 0.25);

  const priorHigh = Math.max(...prior.map((c: any) => Number(c.h ?? c.c ?? 0)));
  const priorLow = Math.min(...prior.map((c: any) => Number(c.l ?? c.c ?? 0)));
  const recentLow = Math.min(...recent.map((c: any) => Number(c.l ?? c.c ?? 0)));
  const recentHigh = Math.max(...recent.map((c: any) => Number(c.h ?? c.c ?? 0)));
  const lastOpen = Number(last.o ?? last.c);
  const lastClose = Number(last.c);
  const lastLow = Number(last.l ?? last.c);
  const lastHigh = Number(last.h ?? last.c);
  const prevClose = Number(prev.c);

  if (side === 'BUY') {
    const touchedWall = recentLow <= wall.price + tolerance;
    const reclaimedWall = lastClose >= wall.price && (prevClose <= wall.price + tolerance || lastLow <= wall.price + tolerance);
    const brokeMicroHigh = lastClose > priorHigh + tolerance * 0.1;
    const bullishBody = lastClose > lastOpen || lastClose > Number(prev.h ?? prevClose);
    return {
      ok: touchedWall && bullishBody && (reclaimedWall || brokeMicroHigh),
      note: `${timeframe}_BREAK_BUY wall=${round(wall.price)} reclaim=${reclaimedWall} breakHigh=${brokeMicroHigh}`,
    };
  }

  const touchedWall = recentHigh >= wall.price - tolerance;
  const reclaimedWall = lastClose <= wall.price && (prevClose >= wall.price - tolerance || lastHigh >= wall.price - tolerance);
  const brokeMicroLow = lastClose < priorLow - tolerance * 0.1;
  const bearishBody = lastClose < lastOpen || lastClose < Number(prev.l ?? prevClose);
  return {
    ok: touchedWall && bearishBody && (reclaimedWall || brokeMicroLow),
    note: `${timeframe}_BREAK_SELL wall=${round(wall.price)} reclaim=${reclaimedWall} breakLow=${brokeMicroLow}`,
  };
}

function buildMagnetPathAnalysis(
  entry: number,
  direction: 'UP' | 'DOWN',
  tp: number,
  priceMap: PriceMap,
): EntrySignal['pathAnalysis'] {
  const path = analyzePath(priceMap, entry, direction, tp);
  return {
    clearPathProbability: path.clearPathProbability,
    obstacleCount: path.obstacles.length,
    biggestObstaclePrice: path.biggestObstacle?.wall.price,
    biggestObstacleStars: path.biggestObstacle?.wall.confluenceStars,
    conservativeTP: path.conservativeTP,
    aggressiveTP: path.aggressiveTP,
    note: path.note,
  };
}

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

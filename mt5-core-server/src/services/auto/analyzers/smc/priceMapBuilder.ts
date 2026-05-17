/**
 * Price Map Builder — MTF Zone Aggregator + Path Analysis
 *
 * รวบรวม zone ทุกประเภทจากทุก Timeframe มาเป็น "กำแพงราคา" (PriceWall)
 * เหมือนกับที่ Pine Script SMC V8.3 แสดงทางขวาของกราฟ เช่น:
 *   "4718.2 ★★★★★  M1+M5+M15+H1"
 *   "4710.14 M1+M5+M15"
 *   "4700.17 ★  M15+M5+M1"
 *
 * หลักการ:
 * 1. แยก level ทั้งหมด (OB, FVG, Liquidity, Structure) ออกจากแต่ละ TF snapshot
 * 2. Cluster level ที่อยู่ใกล้กัน (ภายใน ATR × clusterPct) เข้าด้วยกัน
 * 3. Score confluence (★) = base + TF count + zone type bonuses
 * 4. สร้าง PathAnalysis: ถ้า bounce จากจุด X ไปทิศ Y มีกำแพงอะไรขวาง
 *
 * 2026-05-07 — skyliner.jojo@gmail.com
 */

import type {
  SmcSnapshot, FVG, OrderBlock, LiquidityZone,
  PriceWall, PriceMap, PathAnalysis, PathObstacle,
  WallSource, WallSourceType,
} from './types.js';
import { computeATR } from './utils.js';

// ── Config ────────────────────────────────────────────────────────────

export interface PriceMapConfig {
  /** % ของ ATR ที่ใช้ cluster zone เข้าด้วยกัน (default: 0.3 = 30% ATR) */
  clusterAtrPct: number;
  /** max กำแพงที่จะเก็บ per side (default: 20) */
  maxWallsPerSide: number;
  /** min ระยะห่างจากราคาปัจจุบัน (ATR ×) ที่จะนับ (default: 0.05) */
  minDistanceAtrPct: number;
  /** max ระยะห่างจากราคาปัจจุบัน (ATR ×) ที่จะนับ (default: 10) */
  maxDistanceAtrPct: number;
  /** แรง blockProbability ของกำแพงที่มี stars เท่าไร ถึงจะหยุด path (default: 3) */
  blockThresholdStars: number;
}

const DEFAULT_CFG: PriceMapConfig = {
  clusterAtrPct: 0.3,
  maxWallsPerSide: 20,
  minDistanceAtrPct: 0.05,
  maxDistanceAtrPct: 10,
  blockThresholdStars: 3,
};

// ── Raw Level (internal) ─────────────────────────────────────────────

interface RawLevel {
  price: number;
  priceTop: number;
  priceBottom: number;
  side: 'RESISTANCE' | 'SUPPORT' | 'BOTH';
  source: WallSource;
}

/** V24.2.0 — Context level (Pivot/VWAP/Fib) ที่ caller ส่งเข้ามา feed wall */
export interface ExtraContextLevel {
  price: number;
  side: 'RESISTANCE' | 'SUPPORT' | 'BOTH';
  type: 'PIVOT' | 'VWAP' | 'FIB';
  label: string;
  weight: number;       // 1-3
  /** TF ของ context (default 'D1' สำหรับ pivot, 'SESSION' สำหรับ vwap, 'SWING' สำหรับ fib) */
  timeframe?: string;
}

// ── Main Builder ─────────────────────────────────────────────────────

/**
 * สร้าง PriceMap จาก SMC snapshots ของหลาย TF
 *
 * @param snapshots  Map<timeframe, SmcSnapshot> เช่น { 'M5': snap5, 'M15': snap15, ... }
 * @param currentPrice  ราคาปัจจุบัน
 * @param primaryAtr    ATR ของ primary TF (ใช้ cluster & filter)
 * @param config        optional config override
 */
export function buildPriceMap(
  snapshots: Map<string, SmcSnapshot>,
  currentPrice: number,
  primaryAtr: number,
  config: Partial<PriceMapConfig> = {},
  extraLevels: ExtraContextLevel[] = []   // V24.2.0 — Pivot/VWAP/Fib
): PriceMap {
  const cfg = { ...DEFAULT_CFG, ...config };
  const atr = primaryAtr > 0 ? primaryAtr : currentPrice * 0.001;
  const clusterRange = atr * cfg.clusterAtrPct;
  const minDist = atr * cfg.minDistanceAtrPct;
  const maxDist = atr * cfg.maxDistanceAtrPct;

  const sourceTimeframes = Array.from(snapshots.keys()).sort();

  // ── Step 1: แยก raw levels จากทุก snapshot ─────────────────────────
  const rawLevels: RawLevel[] = [];

  for (const [tf, snap] of snapshots) {
    extractLevels(tf, snap, currentPrice, minDist, maxDist, rawLevels);
  }

  // ── Step 1b (V24.2.0): inject context levels (Pivot/VWAP/Fib) ──────
  for (const ctx of extraLevels) {
    const dist = Math.abs(ctx.price - currentPrice);
    if (dist < minDist || dist > maxDist) continue;
    const tf = ctx.timeframe ?? (ctx.type === 'PIVOT' ? 'D1' : ctx.type === 'VWAP' ? 'SESSION' : 'SWING');
    rawLevels.push({
      price: ctx.price,
      priceTop: ctx.price,
      priceBottom: ctx.price,
      side: ctx.side,
      source: {
        type: ctx.type,
        timeframe: tf,
        priceTop: ctx.price,
        priceBottom: ctx.price,
        label: ctx.label,
        weight: ctx.weight,
      },
    });
  }

  // ── Step 2: Cluster levels ที่ใกล้กัน ───────────────────────────────
  const clusters = clusterLevels(rawLevels, clusterRange);

  // ── Step 3: Score + build PriceWall ────────────────────────────────
  const walls: PriceWall[] = clusters.map(cluster =>
    buildWall(cluster, currentPrice, atr)
  );

  // ── Step 4: Split + sort ────────────────────────────────────────────
  const resistanceWalls = walls
    .filter(w => w.price > currentPrice && (w.side === 'RESISTANCE' || w.side === 'BOTH'))
    .sort((a, b) => a.price - b.price)   // ใกล้สุดก่อน (บนลงล่าง)
    .slice(0, cfg.maxWallsPerSide);

  const supportWalls = walls
    .filter(w => w.price < currentPrice && (w.side === 'SUPPORT' || w.side === 'BOTH'))
    .sort((a, b) => b.price - a.price)   // ใกล้สุดก่อน (ล่างขึ้นบน)
    .slice(0, cfg.maxWallsPerSide);

  const allWalls = [...resistanceWalls, ...supportWalls]
    .sort((a, b) => b.price - a.price);  // เรียงจากบนลงล่าง

  return {
    symbol: Array.from(snapshots.values())[0]?.symbol ?? '',
    currentPrice,
    atr,
    walls: allWalls,
    resistanceWalls,
    supportWalls,
    nearestResistance: resistanceWalls[0] ?? null,
    nearestSupport: supportWalls[0] ?? null,
    strongestResistance: [...resistanceWalls].sort((a, b) => b.confluenceStars - a.confluenceStars)[0] ?? null,
    strongestSupport: [...supportWalls].sort((a, b) => b.confluenceStars - a.confluenceStars)[0] ?? null,
    sourceTimeframes,
    timestamp: Date.now(),
  };
}

// ── Path Analysis ──────────────────────────────────────────────────────

/**
 * วิเคราะห์เส้นทางราคา: ถ้า bounce จาก entryPrice ไปทิศ direction
 * มีกำแพงอะไรขวางอยู่ก่อนถึง target TP?
 *
 * @param priceMap   PriceMap ที่สร้างไว้แล้ว
 * @param entryPrice  จุดเข้า
 * @param direction  'UP' (BUY) หรือ 'DOWN' (SELL)
 * @param targetTP   TP เป้าหมายที่ EA ตั้งไว้ (optional; ถ้าไม่มีจะหาเอง)
 */
export function analyzePath(
  priceMap: PriceMap,
  entryPrice: number,
  direction: 'UP' | 'DOWN',
  targetTP?: number,
  minStars: number = 3
): PathAnalysis {
  const atr = priceMap.atr;

  // เลือก walls ที่อยู่ระหว่าง entry และ TP
  const wallsInPath: PriceWall[] = direction === 'UP'
    ? priceMap.resistanceWalls.filter(w => w.price > entryPrice)
    : priceMap.supportWalls.filter(w => w.price < entryPrice);

  // หา TP แบบต่างๆ จาก walls
  const weakWalls = wallsInPath.filter(w => w.confluenceStars <= 1);
  const mediumWalls = wallsInPath.filter(w => w.confluenceStars >= 2 && w.confluenceStars < 4);
  const strongWalls = wallsInPath.filter(w => w.confluenceStars >= 4);

  // Conservative TP: ก่อนกำแพง 2★+ แรก
  const firstMediumWall = direction === 'UP'
    ? mediumWalls.sort((a, b) => a.price - b.price)[0]
    : mediumWalls.sort((a, b) => b.price - a.price)[0];

  // Suggested TP: ก่อนกำแพงที่มีดาว >= minStars แรก
  const firstStrongWall = direction === 'UP'
    ? wallsInPath.filter(w => w.confluenceStars >= minStars).sort((a, b) => a.price - b.price)[0]
    : wallsInPath.filter(w => w.confluenceStars >= minStars).sort((a, b) => b.price - a.price)[0];

  // Aggressive TP: กำแพงไกลสุดที่ weak (liquidity sweep target)
  const lastWeakWall = direction === 'UP'
    ? weakWalls.sort((a, b) => b.price - a.price)[0]
    : weakWalls.sort((a, b) => a.price - b.price)[0];

  const pricePad = (direction === 'UP' ? -1 : 1) * atr * 0.1; // buffer ก่อนถึงกำแพง

  const conservativeTP = firstMediumWall
    ? firstMediumWall.price + pricePad
    : entryPrice + (direction === 'UP' ? atr * 2 : -atr * 2);

  const suggestedTP = targetTP ?? (firstStrongWall
    ? firstStrongWall.price + pricePad
    : conservativeTP);

  const aggressiveTP = lastWeakWall
    ? lastWeakWall.price + (direction === 'UP' ? atr * 0.3 : -atr * 0.3)
    : suggestedTP;

  // สร้าง obstacles ระหว่าง entry และ suggestedTP
  const effectiveTP = suggestedTP;
  const obstacleWalls = direction === 'UP'
    ? wallsInPath.filter(w => w.price > entryPrice && w.price < effectiveTP)
        .sort((a, b) => a.price - b.price)
    : wallsInPath.filter(w => w.price < entryPrice && w.price > effectiveTP)
        .sort((a, b) => b.price - a.price);

  const obstacles: PathObstacle[] = obstacleWalls.map(wall => {
    const distanceFromEntry = Math.abs(wall.price - entryPrice);
    const blockProb = calcBlockProbability(wall);
    const note = buildObstacleNote(wall);
    return { wall, distanceFromEntry, blockProbability: blockProb, note };
  });

  // คำนวณโอกาสที่ราคาจะถึง TP
  const clearPathProb = calcClearPathProbability(obstacles);
  const biggestObstacle = obstacles.length > 0
    ? obstacles.reduce((max, o) => o.blockProbability > max.blockProbability ? o : max, obstacles[0])
    : null;

  const note = buildPathNote(direction, obstacles, clearPathProb, firstStrongWall ?? null);

  const targetWall = firstStrongWall ?? firstMediumWall ?? null;

  return {
    entryPrice,
    direction,
    suggestedTP: round(suggestedTP),
    conservativeTP: round(conservativeTP),
    aggressiveTP: round(aggressiveTP),
    obstacles,
    clearPathProbability: Math.round(clearPathProb),
    biggestObstacle: biggestObstacle ?? null,
    targetWallStars: targetWall?.confluenceStars ?? 0,
    note,
  };
}

// ── Extract Levels from Snapshot ─────────────────────────────────────

function extractLevels(
  tf: string,
  snap: SmcSnapshot,
  currentPrice: number,
  minDist: number,
  maxDist: number,
  out: RawLevel[]
): void {
  const push = (level: RawLevel) => {
    const dist = Math.abs(level.price - currentPrice);
    if (dist >= minDist && dist <= maxDist) {
      out.push(level);
    }
  };

  // 1. Bullish OBs → SUPPORT (demand zone ด้านล่าง)
  for (const ob of snap.bullOBs.filter(o => !o.invalidated)) {
    const mid = (ob.top + ob.bottom) / 2;
    push({
      price: mid,
      priceTop: ob.top,
      priceBottom: ob.bottom,
      side: 'SUPPORT',
      source: { type: 'OB_BULL', timeframe: tf, priceTop: ob.top, priceBottom: ob.bottom, hasFVG: ob.hasFVG, mitigated: ob.mitigated },
    });
  }

  // 2. Bearish OBs → RESISTANCE (supply zone ด้านบน)
  for (const ob of snap.bearOBs.filter(o => !o.invalidated)) {
    const mid = (ob.top + ob.bottom) / 2;
    push({
      price: mid,
      priceTop: ob.top,
      priceBottom: ob.bottom,
      side: 'RESISTANCE',
      source: { type: 'OB_BEAR', timeframe: tf, priceTop: ob.top, priceBottom: ob.bottom, hasFVG: ob.hasFVG, mitigated: ob.mitigated },
    });
  }

  // 3. Bull FVGs (เขียว) → SUPPORT (ราคาอาจลงไปเก็บ)
  for (const fvg of snap.activeFVGs.filter(f => f.type === 'BULL' && !f.mitigated)) {
    const mid = (fvg.top + fvg.bottom) / 2;
    push({
      price: mid,
      priceTop: fvg.top,
      priceBottom: fvg.bottom,
      side: 'SUPPORT',
      source: { type: 'FVG_BULL', timeframe: tf, priceTop: fvg.top, priceBottom: fvg.bottom },
    });
  }

  // 4. Bear FVGs (แดง) → RESISTANCE (ราคาอาจขึ้นไปเก็บ)
  for (const fvg of snap.activeFVGs.filter(f => f.type === 'BEAR' && !f.mitigated)) {
    const mid = (fvg.top + fvg.bottom) / 2;
    push({
      price: mid,
      priceTop: fvg.top,
      priceBottom: fvg.bottom,
      side: 'RESISTANCE',
      source: { type: 'FVG_BEAR', timeframe: tf, priceTop: fvg.top, priceBottom: fvg.bottom },
    });
  }

  // 5. Liquidity Zones
  for (const liq of snap.liquidityZones.filter(z => !z.swept)) {
    push({
      price: liq.price,
      priceTop: liq.price,
      priceBottom: liq.price,
      side: liq.isHigh ? 'RESISTANCE' : 'SUPPORT',
      source: { type: 'LIQUIDITY', timeframe: tf, priceTop: liq.price, priceBottom: liq.price },
    });
  }

  // 6. Structure High/Low
  const sh = snap.structure.structureHigh;
  const sl = snap.structure.structureLow;
  if (sh > 0) {
    push({
      price: sh,
      priceTop: sh,
      priceBottom: sh,
      side: 'RESISTANCE',
      source: { type: 'STRUCTURE_HIGH', timeframe: tf, priceTop: sh, priceBottom: sh },
    });
  }
  if (sl > 0) {
    push({
      price: sl,
      priceTop: sl,
      priceBottom: sl,
      side: 'SUPPORT',
      source: { type: 'STRUCTURE_LOW', timeframe: tf, priceTop: sl, priceBottom: sl },
    });
  }

  // 7. Swing Highs/Lows
  for (const swH of snap.swingHighs) {
    push({
      price: swH,
      priceTop: swH,
      priceBottom: swH,
      side: 'RESISTANCE',
      source: { type: 'SWING_HIGH', timeframe: tf, priceTop: swH, priceBottom: swH },
    });
  }
  for (const swL of snap.swingLows) {
    push({
      price: swL,
      priceTop: swL,
      priceBottom: swL,
      side: 'SUPPORT',
      source: { type: 'SWING_LOW', timeframe: tf, priceTop: swL, priceBottom: swL },
    });
  }
}

// ── Cluster ───────────────────────────────────────────────────────────

/** Group raw levels ที่อยู่ภายใน clusterRange เข้าด้วยกัน */
function clusterLevels(
  rawLevels: RawLevel[],
  clusterRange: number
): RawLevel[][] {
  if (rawLevels.length === 0) return [];

  // เรียงตามราคา
  const sorted = [...rawLevels].sort((a, b) => a.price - b.price);
  const clusters: RawLevel[][] = [];
  let current: RawLevel[] = [sorted[0]];

  for (let i = 1; i < sorted.length; i++) {
    const prev = sorted[i - 1];
    const curr = sorted[i];
    if (Math.abs(curr.price - prev.price) <= clusterRange) {
      current.push(curr);
    } else {
      clusters.push(current);
      current = [curr];
    }
  }
  clusters.push(current);
  return clusters;
}

// ── Build Wall from Cluster ───────────────────────────────────────────

function buildWall(
  cluster: RawLevel[],
  currentPrice: number,
  atr: number
): PriceWall {
  // ราคากลาง = median
  const prices = cluster.map(l => l.price).sort((a, b) => a - b);
  const price = prices[Math.floor(prices.length / 2)];
  const priceTop = Math.max(...cluster.map(l => l.priceTop));
  const priceBottom = Math.min(...cluster.map(l => l.priceBottom));

  // รวม sources
  const sources = cluster.map(l => l.source);

  // TF ที่ appear (unique + sorted)
  // V24.2.0: เพิ่ม D1/SESSION/SWING สำหรับ Pivot/VWAP/Fib
  const tfOrder = ['M1', 'M5', 'M15', 'M30', 'M45', 'H1', 'H4', 'D1', 'SESSION', 'SWING'];
  const tfSet = new Set(sources.map(s => s.timeframe));
  const timeframes = tfOrder.filter(tf => tfSet.has(tf));

  // ── Scoring ───────────────────────────────────────────────────────

  let stars = 0;

  // 1★ base: มี level อย่างน้อย 1 อัน
  stars += 1;

  // +1 per additional TF (สูงสุด +3)
  const additionalTfBonus = Math.min(3, timeframes.length - 1);
  stars += additionalTfBonus;

  // +1 ถ้ามี OB
  const hasOB = sources.some(s => s.type === 'OB_BULL' || s.type === 'OB_BEAR');
  if (hasOB) stars += 1;

  // +1 ถ้ามี OB + FVG confirm
  const hasFVG = sources.some(s =>
    s.type === 'FVG_BULL' || s.type === 'FVG_BEAR' ||
    (s.hasFVG === true)
  );
  if (hasFVG && hasOB) stars += 1;

  // +1 ถ้ามี Structure level (แทนของ Pine Script: premium/discount alignment)
  const isStructure = sources.some(s =>
    s.type === 'STRUCTURE_HIGH' || s.type === 'STRUCTURE_LOW'
  );

  // V24.2.0: +1 ถ้า cluster ตรงกับ Pivot R1/S1/P, VWAP center, หรือ Fib 0.618 (high-weight context)
  const hasHighWeightContext = sources.some(s =>
    (s.type === 'PIVOT' || s.type === 'VWAP' || s.type === 'FIB') &&
    (s.weight ?? 0) >= 3
  );
  if (hasHighWeightContext) stars += 1;

  // cap 5★
  stars = Math.min(5, stars);

  // ── Side ─────────────────────────────────────────────────────────

  const resistanceCount = cluster.filter(l => l.side === 'RESISTANCE').length;
  const supportCount = cluster.filter(l => l.side === 'SUPPORT').length;
  let side: 'RESISTANCE' | 'SUPPORT' | 'BOTH' = 'BOTH';
  if (resistanceCount > supportCount * 1.5) side = 'RESISTANCE';
  else if (supportCount > resistanceCount * 1.5) side = 'SUPPORT';

  // ── Label ─────────────────────────────────────────────────────────

  const starStr = '★'.repeat(stars);
  const tfStr = timeframes.join('+');
  const label = stars > 0 && timeframes.length > 1
    ? `${round(price)} ${starStr}  ${tfStr}`
    : timeframes.length > 1
    ? `${round(price)} ${tfStr}`
    : `${round(price)} ${starStr}`;

  const hasLiquidity = sources.some(s => s.type === 'LIQUIDITY');
  const distanceFromPrice = Math.abs(price - currentPrice);
  const distancePct = (distanceFromPrice / currentPrice) * 100;

  return {
    price: round(price),
    priceTop: round(priceTop),
    priceBottom: round(priceBottom),
    side,
    confluenceStars: stars,
    timeframes,
    sources,
    hasOB,
    hasFVG,
    hasLiquidity,
    isStructure,
    label,
    distanceFromPrice: round(distanceFromPrice),
    distancePct: Math.round(distancePct * 100) / 100,
  };
}

// ── Scoring Helpers ───────────────────────────────────────────────────

/** คำนวณโอกาสที่ราคาจะถูกหยุดที่กำแพงนี้ (0-100) */
function calcBlockProbability(wall: PriceWall): number {
  let prob = 0;
  // base จาก stars
  prob += wall.confluenceStars * 15;          // 5★ = 75
  if (wall.hasOB) prob += 10;
  if (wall.hasFVG && wall.hasOB) prob += 10;  // OB+FVG = extra
  if (wall.isStructure) prob += 5;
  if (wall.timeframes.length >= 3) prob += 5; // 3+ TF overlay
  return Math.min(100, Math.max(0, prob));
}

/** คำนวณโอกาสที่ราคาจะผ่าน path ได้โดยไม่โดนหยุด (0-100) */
function calcClearPathProbability(obstacles: PathObstacle[]): number {
  if (obstacles.length === 0) return 90;
  // แต่ละกำแพงลด prob ลง
  let prob = 100;
  for (const obs of obstacles) {
    // ยิ่งกำแพงแข็ง ยิ่ง deduct มาก
    prob -= obs.blockProbability * 0.4;
  }
  return Math.max(10, Math.min(90, prob));
}

/** สร้าง note สำหรับ obstacle */
function buildObstacleNote(wall: PriceWall): string {
  const parts: string[] = [];
  if (wall.confluenceStars >= 4) parts.push(`${wall.confluenceStars}★ strong wall`);
  if (wall.hasOB && wall.hasFVG) parts.push('OB+FVG confirmed');
  else if (wall.hasOB) parts.push('OB zone');
  else if (wall.hasFVG) parts.push('FVG unfilled');
  if (wall.timeframes.length >= 3) parts.push(`${wall.timeframes.join('+')} confluence`);
  if (wall.isStructure) parts.push('structure level');
  return parts.length > 0 ? parts.join(' | ') : `${wall.confluenceStars}★ zone`;
}

/** สร้าง note สรุปภาพรวม path */
function buildPathNote(
  direction: 'UP' | 'DOWN',
  obstacles: PathObstacle[],
  clearProb: number,
  biggestWall: PriceWall | null
): string {
  if (obstacles.length === 0) return `Clear path ${direction} — no significant walls`;
  const strongest = biggestWall;
  if (!strongest) return `${obstacles.length} walls in path`;
  if (clearProb >= 70) return `Mostly clear ${direction} — ${obstacles.length} minor wall(s), strongest at ${strongest.price}`;
  if (clearProb >= 50) return `Moderate path ${direction} — ${strongest.confluenceStars}★ wall at ${strongest.price} may slow momentum`;
  return `Blocked path ${direction} — ${strongest.confluenceStars}★ wall at ${strongest.price} (${strongest.label}) likely reversal`;
}

// ── Utility ───────────────────────────────────────────────────────────

function round(n: number): number {
  return Math.round(n * 100) / 100;
}

// ── Convenience: build PriceMap from IndicatorPipeline smcSnapshots ──

/**
 * Shortcut: สร้าง PriceMap จาก Record<timeframe, SmcSnapshot>
 * ใช้ใน IndicatorPipeline
 */
export function buildPriceMapFromRecord(
  snapshotRecord: Record<string, SmcSnapshot>,
  currentPrice: number,
  primaryAtr: number,
  config?: Partial<PriceMapConfig>,
  extraLevels: ExtraContextLevel[] = []   // V24.2.0
): PriceMap {
  const map = new Map<string, SmcSnapshot>(Object.entries(snapshotRecord));
  return buildPriceMap(map, currentPrice, primaryAtr, config, extraLevels);
}

/**
 * Format PriceMap เป็น log string เหมือน Pine Script sidebar
 * ตัวอย่าง output:
 *   ── RESISTANCE ─────────────────
 *   4718.2 ★★★★★  M1+M5+M15+H1
 *   4710.1 ★★★  M1+M5+M15
 *   4705.1 M1+M5
 *   ── PRICE: 4708.2 ──────────────
 *   4700.2 ★  M15+M5+M1
 *   4692.5 ★★  M30+H1+M5
 *   ── SUPPORT ─────────────────────
 */
export function formatPriceMap(map: PriceMap): string {
  const lines: string[] = [];
  lines.push(`── RESISTANCE ────────────────────`);
  for (const w of [...map.resistanceWalls].reverse()) {
    lines.push(`  ${w.label}`);
  }
  lines.push(`── PRICE: ${map.currentPrice} ──────────────`);
  for (const w of map.supportWalls) {
    lines.push(`  ${w.label}`);
  }
  lines.push(`── SUPPORT ────────────────────────`);
  return lines.join('\n');
}

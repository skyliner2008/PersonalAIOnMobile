/**
 * SMC Engine V2 — Shared Types
 * Ported from Pine Script "SMC & Multi-TF Order Blocks Sweeps V8.3"
 *
 * 2026-05-06 — skyliner.jojo@gmail.com
 */

import type { Bias } from '../../types.js';

// ── Candle ────────────────────────────────────────────────────────────
export interface Candle {
  t: number;   // timestamp (ms)
  o: number;   // open
  h: number;   // high
  l: number;   // low
  c: number;   // close
  v: number;   // volume
}

// ── Swing Point ──────────────────────────────────────────────────────
export interface SwingPoint {
  index: number;
  price: number;
  barIndex: number;   // bar_index equivalent
}

// ── Fair Value Gap ───────────────────────────────────────────────────
export interface FVG {
  type: 'BULL' | 'BEAR';
  top: number;
  bottom: number;
  barIndex: number;       // index ในอาร์เรย์ candles
  mitigated: boolean;     // ราคาเคยกลับมาแตะแล้วหรือยัง
  age: number;            // จำนวนแท่งตั้งแต่เกิด
}

// ── Order Block ──────────────────────────────────────────────────────
export interface OrderBlock {
  type: 'BULL' | 'BEAR';
  top: number;
  bottom: number;
  barIndex: number;
  mitigated: boolean;     // ราคาเคย test แล้วหรือยัง
  hasFVG: boolean;        // ยืนยันด้วย FVG (Displacement)
  invalidated: boolean;   // ราคาทะลุ OB ไปแล้ว
  volume: number;
}

// ── Liquidity Zone ───────────────────────────────────────────────────
export interface LiquidityZone {
  price: number;
  barIndex: number;
  isHigh: boolean;        // true = resistance (sell-side), false = support (buy-side)
  swept: boolean;
  strength: number;       // จำนวน equal highs/lows ที่ซ้อนกัน
  source: 'EQUAL_HL' | 'SWING' | 'MTF';
  timeframe?: string;     // TF ที่ตรวจพบ (e.g. 'M15', 'H1')
  confluenceStars: number; // 0-5★ confluence score
}

// ── Market Structure ─────────────────────────────────────────────────
export type StructureEvent = 'BOS' | 'CHoCH' | 'SMS' | 'BMS' | 'NONE';
export type StructureDirection = 'BULLISH' | 'BEARISH' | 'INIT';

export interface MarketStructure {
  direction: StructureDirection;
  structureHigh: number;
  structureLow: number;
  structureHighBarIndex: number;
  structureLowBarIndex: number;
  lastEvent: StructureEvent;
  lastEventSide: 'UP' | 'DOWN' | null;
  continuationCount: number;    // BOS streak count
  // IDM (Inducement) filter
  idmHigh: number | null;
  idmLow: number | null;
  idmHighSwept: boolean;
  idmLowSwept: boolean;
}

// ── Multi-TF Sweep ──────────────────────────────────────────────────
export interface MtfSweep {
  timeframe: string;
  side: 'BULL' | 'BEAR';
  barIndex: number;
  price: number;
}

export interface MtfSweepScore {
  bullScore: number;   // จำนวน TFs ที่มี bullish sweep
  bearScore: number;   // จำนวน TFs ที่มี bearish sweep
  sweeps: MtfSweep[];
  aggregateSide: 'BULL' | 'BEAR' | 'NEUTRAL';
}

// ── Attack Force ────────────────────────────────────────────────────
export interface AttackForce {
  barIndex: number;
  side: 'BULL' | 'BEAR';
  bodySize: number;
  atrRatio: number;    // bodySize / ATR
  volumeRatio: number; // volume / avgVolume
}

// ── Zone Confluence ─────────────────────────────────────────────────
export interface ZoneConfluence {
  zone: LiquidityZone;
  stars: number;        // 0-5★
  factors: string[];    // list of why each star was awarded
}

// ── Premium / Discount ──────────────────────────────────────────────
export type PremiumDiscountZone = 'PREMIUM' | 'EQ' | 'DISCOUNT';

export interface PremiumDiscountResult {
  zone: PremiumDiscountZone;
  pctFromRange: number;
  structureHigh: number;
  structureLow: number;
  equilibrium: number;
}

// ── Full SMC Snapshot ───────────────────────────────────────────────
export interface SmcSnapshot {
  // Zones
  activeFVGs: FVG[];
  bullOBs: OrderBlock[];
  bearOBs: OrderBlock[];
  liquidityZones: LiquidityZone[];

  // Structure
  structure: MarketStructure;

  // Sweeps
  mtfSweeps: MtfSweepScore;
  attackForces: AttackForce[];

  // Premium / Discount
  premiumDiscount: PremiumDiscountResult;

  // Swing points
  swingHighs: number[];
  swingLows: number[];

  // Metadata
  symbol: string;
  timeframe: string;
  lastPrice: number;
  timestamp: number;
}


// ── Price Map (MTF Wall Map) ─────────────────────────────────────────
// ระบบ "กำแพงราคา" — รวม zone ทุก TF เป็นภาพรวมเดียว
// ใช้วิเคราะห์ว่า ราคาจะเด้งที่ไหน และมีอะไรขวางทางไปถึง TP

/** ประเภทของ zone ที่เป็นส่วนประกอบของ PriceWall */
export type WallSourceType =
  | 'OB_BULL'
  | 'OB_BEAR'
  | 'FVG_BULL'
  | 'FVG_BEAR'
  | 'LIQUIDITY'
  | 'STRUCTURE_HIGH'
  | 'STRUCTURE_LOW'
  | 'SWING_HIGH'
  | 'SWING_LOW'
  // V24.2.0 (2026-05-08) — Context levels (non-SMC)
  | 'PIVOT'
  | 'VWAP'
  | 'FIB';

/** แหล่งที่มาแต่ละรายการภายใน PriceWall */
export interface WallSource {
  type: WallSourceType;
  timeframe: string;
  priceTop: number;
  priceBottom: number;
  hasFVG?: boolean;
  mitigated?: boolean;
  /** V24.2.0 — สำหรับ context levels (เช่น "P", "R1", "VWAP", "Fib 0.618") */
  label?: string;
  /** V24.2.0 — น้ำหนักของ source (1-3) ใช้ตอน aggregate stars */
  weight?: number;
}

/** PriceWall — กำแพงราคาหนึ่งจุด รวม zone จากหลาย TF */
export interface PriceWall {
  price: number;
  priceTop: number;
  priceBottom: number;
  side: 'RESISTANCE' | 'SUPPORT' | 'BOTH';
  confluenceStars: number;
  timeframes: string[];
  sources: WallSource[];
  hasOB: boolean;
  hasFVG: boolean;
  hasLiquidity: boolean;
  isStructure: boolean;
  label: string;
  distanceFromPrice: number;
  distancePct: number;
}

/** PathObstacle — กำแพงที่ขวางอยู่ระหว่างจุดเข้าและ TP */
export interface PathObstacle {
  wall: PriceWall;
  distanceFromEntry: number;
  blockProbability: number;
  note: string;
}

/** PathAnalysis — วิเคราะห์เส้นทางราคาจากจุดเข้าไปถึง TP */
export interface PathAnalysis {
  entryPrice: number;
  direction: 'UP' | 'DOWN';
  suggestedTP: number;
  conservativeTP: number;
  aggressiveTP: number;
  obstacles: PathObstacle[];
  clearPathProbability: number;
  biggestObstacle: PathObstacle | null;
  targetWallStars: number;
  note: string;
}

/** PriceMap — ภาพรวมกำแพงราคาทั้งหมดจากทุก TF */
export interface PriceMap {
  symbol: string;
  currentPrice: number;
  atr: number;
  walls: PriceWall[];
  resistanceWalls: PriceWall[];
  supportWalls: PriceWall[];
  nearestResistance: PriceWall | null;
  nearestSupport: PriceWall | null;
  strongestResistance: PriceWall | null;
  strongestSupport: PriceWall | null;
  sourceTimeframes: string[];
  timestamp: number;
}

// ── Entry Signal from EA ────────────────────────────────────────────
export interface EaEntrySignal {
  symbol: string;
  side: 'BUY' | 'SELL';
  entry: number;
  sl: number;
  tp: number;
  confidence: number;
  confluenceStars: number;
  triggers: string[];
  strategy: string;
  smcSnapshot: SmcSnapshot;
  timestamp: number;
}

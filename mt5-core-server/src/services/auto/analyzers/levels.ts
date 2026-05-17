/**
 * Context Levels — Daily Pivot Points + Session VWAP + Auto-Fibonacci
 *
 * เพิ่ม 3 ชั้น confluence ที่หายไปจากระบบ SMC-only เดิม:
 *   1. Daily Pivot (Floor / Standard) — strong intraday SR ตามที่ pro retail ใช้
 *   2. Session VWAP — dynamic SR + mean-reversion target (anchored ที่ NY open)
 *   3. Auto-Fibonacci — entry-on-pullback levels จาก swing high/low ล่าสุด
 *
 * ผลลัพธ์ feed เข้า PriceMap ผ่าน `extraLevels` ของ buildPriceMap()
 * → confluence stars ของ wall จะรวมข้อมูล 3 ชั้นนี้ด้วยอัตโนมัติ
 *
 * 2026-05-08 V24.2.0 — skyliner.jojo@gmail.com
 */

import type { Candle } from './smc/types.js';

// ── Types ─────────────────────────────────────────────────────────────

export interface PivotLevels {
  pivot: number;
  r1: number;
  r2: number;
  r3: number;
  s1: number;
  s2: number;
  s3: number;
  /** OHLC ที่ใช้คำนวณ */
  source: { high: number; low: number; close: number };
}

export interface VwapResult {
  vwap: number;
  /** จำนวน candles ที่ใช้คำนวณ (อย่างน้อย 5 ถึงน่าเชื่อถือ) */
  samples: number;
  /** Standard deviation bands (1σ) — สำหรับ mean-reversion zone */
  upperBand: number;
  lowerBand: number;
  /** ATR-style deviation (price - vwap) → ใช้ตรวจ stretch */
  deviation: number;
}

export interface FibLevels {
  swingHigh: number;
  swingLow: number;
  /** Fib retracement levels (BUY → ราคาขึ้นจาก swingLow) */
  fib236: number;
  fib382: number;
  fib500: number;
  fib618: number;
  fib786: number;
  /** Extension levels (สำหรับ TP) */
  fib1272: number;
  fib1618: number;
  /** Direction ที่ swing เกิด */
  direction: 'UP' | 'DOWN';
}

/** RawLevel-compatible structure ใช้ feed เข้า priceMapBuilder */
export interface ContextLevel {
  price: number;
  side: 'RESISTANCE' | 'SUPPORT' | 'BOTH';
  type: 'PIVOT' | 'VWAP' | 'FIB';
  label: string;
  /** น้ำหนักของระดับ (1 = อ่อน, 3 = แข็ง) — ใช้ตอน score wall stars */
  weight: number;
}

// ── Daily Pivot Points (Floor / Standard) ────────────────────────────

/**
 * คำนวณ Standard Floor Pivot จาก D1 OHLC
 *
 * Pivot   = (H + L + C) / 3
 * R1 = 2P - L,  S1 = 2P - H
 * R2 = P + (H - L),  S2 = P - (H - L)
 * R3 = H + 2(P - L),  S3 = L - 2(H - P)
 *
 * @param d1Candle  candle D1 ล่าสุดที่ "ปิดแล้ว" (ใช้ของเมื่อวาน)
 *                   หรือ derived OHLC จาก H1 24-bar window
 */
export function computePivotLevels(d1Candle: { h: number; l: number; c: number }): PivotLevels {
  const { h, l, c } = d1Candle;
  const pivot = (h + l + c) / 3;
  const range = h - l;
  return {
    pivot,
    r1: 2 * pivot - l,
    s1: 2 * pivot - h,
    r2: pivot + range,
    s2: pivot - range,
    r3: h + 2 * (pivot - l),
    s3: l - 2 * (h - pivot),
    source: { high: h, low: l, close: c },
  };
}

/**
 * Derive D1 OHLC จาก H1/M30/H4 candles (24h window)
 * ใช้เมื่อระบบไม่ได้ fetch D1 โดยตรง (default behavior ของ pipeline ปัจจุบัน)
 *
 * @param candles    candles ของ TF ใดก็ได้ที่ครอบคลุม ≥24 ชั่วโมงล่าสุด
 * @param hoursBack  จำนวนชั่วโมงย้อนหลังที่นับเป็น "เมื่อวาน" (default 24)
 * @param tfMinutes  TF ของ candles เป็นนาที (H1=60, M30=30, H4=240)
 */
export function deriveDailyOhlcFromIntraday(
  candles: Candle[],
  hoursBack: number = 24,
  tfMinutes: number = 60
): { h: number; l: number; c: number } | null {
  if (!Array.isArray(candles) || candles.length === 0) return null;
  const barsForOneDay = Math.max(1, Math.round((hoursBack * 60) / tfMinutes));
  // ใช้ "เมื่อวาน" = window ที่ปิดแล้ว → skip 1 candle ปัจจุบัน, take N candles ก่อนหน้า
  const endIdx = candles.length - 1; // exclusive: ใช้ index < endIdx เพื่อข้าม current bar
  const startIdx = Math.max(0, endIdx - barsForOneDay);
  if (endIdx - startIdx < Math.min(barsForOneDay * 0.5, 6)) return null; // need at least half-day data
  let h = -Infinity;
  let l = Infinity;
  let c = candles[endIdx - 1].c;
  for (let i = startIdx; i < endIdx; i++) {
    const cd = candles[i];
    if (cd.h > h) h = cd.h;
    if (cd.l < l) l = cd.l;
  }
  if (!isFinite(h) || !isFinite(l)) return null;
  return { h, l, c };
}

// ── Session VWAP ─────────────────────────────────────────────────────

/**
 * คำนวณ VWAP anchored ที่ start of session
 *
 * VWAP = Σ(typical × volume) / Σ(volume)
 * typical = (h + l + c) / 3
 *
 * @param intradayCandles  M5 หรือ M15 candles (มี v field)
 * @param anchorHourUtc    UTC hour ที่ session เริ่ม (default 13 = NY equity open;
 *                          18 = NY futures session; 0 = midnight UTC = full-day VWAP)
 */
export function computeSessionVwap(
  intradayCandles: Candle[],
  anchorHourUtc: number = 13
): VwapResult | null {
  if (!Array.isArray(intradayCandles) || intradayCandles.length === 0) return null;
  // หา anchor index = candle ตัวแรกใน "session ปัจจุบัน"
  const now = Date.now();
  const todayUtc = new Date(now);
  const anchorMs = Date.UTC(
    todayUtc.getUTCFullYear(),
    todayUtc.getUTCMonth(),
    todayUtc.getUTCDate(),
    anchorHourUtc, 0, 0, 0
  );
  // ถ้า anchor อยู่อนาคต (เช่น เวลาตอนนี้ 02:00 UTC, anchor 13:00 UTC) → ใช้ของเมื่อวาน
  const effectiveAnchor = anchorMs > now ? anchorMs - 86_400_000 : anchorMs;

  let sumPv = 0;
  let sumV = 0;
  let sumPv2 = 0; // สำหรับคำนวณ variance
  let samples = 0;
  let lastClose = intradayCandles[intradayCandles.length - 1].c;

  for (const cd of intradayCandles) {
    const tMs = (cd.t || 0) * (cd.t < 1e12 ? 1000 : 1); // sec → ms
    if (tMs < effectiveAnchor) continue;
    const typical = (cd.h + cd.l + cd.c) / 3;
    const volume = Math.max(0, cd.v || 1); // fallback volume=1 ถ้าไม่มี
    sumPv += typical * volume;
    sumV += volume;
    sumPv2 += typical * typical * volume;
    samples += 1;
  }

  if (samples < 3 || sumV === 0) return null;

  const vwap = sumPv / sumV;
  const meanSq = sumPv2 / sumV;
  const variance = Math.max(0, meanSq - vwap * vwap);
  const sd = Math.sqrt(variance);
  return {
    vwap,
    samples,
    upperBand: vwap + sd,
    lowerBand: vwap - sd,
    deviation: lastClose - vwap,
  };
}

// ── Auto-Fibonacci ───────────────────────────────────────────────────

/**
 * สร้าง fib retracement + extension จาก swing high/low ที่ระบบ SMC จับมาแล้ว
 *
 * @param swingHigh / swingLow  เช่น primarySmc.structure.structureHigh / Low
 */
export function computeFibLevels(swingHigh: number, swingLow: number): FibLevels | null {
  if (!isFinite(swingHigh) || !isFinite(swingLow) || swingHigh <= swingLow) return null;
  const range = swingHigh - swingLow;
  if (range <= 0) return null;
  // direction ของ leg ล่าสุด — ถ้า swingHigh ใหม่กว่า swingLow → ขาขึ้น (UP)
  // เราไม่มีข้อมูล timestamp ของ swing → assume UP (BUY-on-pullback retracement)
  // retracements (จาก swingHigh ลงมา = ระดับซื้อ pullback)
  const direction: 'UP' | 'DOWN' = 'UP';
  return {
    swingHigh,
    swingLow,
    direction,
    fib236: swingHigh - range * 0.236,
    fib382: swingHigh - range * 0.382,
    fib500: swingHigh - range * 0.500,
    fib618: swingHigh - range * 0.618,
    fib786: swingHigh - range * 0.786,
    // extensions เหนือ swingHigh (สำหรับ TP)
    fib1272: swingHigh + range * 0.272,
    fib1618: swingHigh + range * 0.618,
  };
}

// ── Aggregator ───────────────────────────────────────────────────────

/**
 * รวบรวม 3 ชนิด context levels แปลงเป็น `ContextLevel[]` พร้อม side & weight
 * เพื่อ feed เข้า priceMapBuilder
 *
 * Side rule:
 *   level > currentPrice → RESISTANCE
 *   level < currentPrice → SUPPORT
 *   |level - currentPrice| < tolerance → BOTH
 *
 * Weight (ใช้ใน scoring):
 *   PIVOT (P, R1/S1)  = 3 (strongest — ใช้กันทั่วโลก)
 *   PIVOT (R2/S2)    = 2
 *   PIVOT (R3/S3)    = 1
 *   VWAP center      = 3 (institutional reference)
 *   VWAP bands ±1σ   = 2
 *   FIB 0.618        = 3 (golden ratio — most respected)
 *   FIB 0.5 / 0.382  = 2
 *   FIB 0.786 / 0.236 = 1
 *   FIB extensions   = 2
 */
export function buildContextLevels(args: {
  currentPrice: number;
  pivots?: PivotLevels | null;
  vwap?: VwapResult | null;
  fibs?: FibLevels | null;
  /** % ของ ATR ใช้ตัดสินว่า level "ใกล้" ปัจจุบันแค่ไหนเป็น BOTH (default 0.05) */
  atr?: number;
}): ContextLevel[] {
  const { currentPrice, pivots, vwap, fibs, atr = currentPrice * 0.001 } = args;
  const tolerance = atr * 0.1;
  const out: ContextLevel[] = [];

  const sideOf = (price: number): 'RESISTANCE' | 'SUPPORT' | 'BOTH' => {
    const diff = price - currentPrice;
    if (Math.abs(diff) < tolerance) return 'BOTH';
    return diff > 0 ? 'RESISTANCE' : 'SUPPORT';
  };

  if (pivots) {
    out.push({ price: pivots.pivot, side: sideOf(pivots.pivot), type: 'PIVOT', label: 'P', weight: 3 });
    out.push({ price: pivots.r1, side: sideOf(pivots.r1), type: 'PIVOT', label: 'R1', weight: 3 });
    out.push({ price: pivots.s1, side: sideOf(pivots.s1), type: 'PIVOT', label: 'S1', weight: 3 });
    out.push({ price: pivots.r2, side: sideOf(pivots.r2), type: 'PIVOT', label: 'R2', weight: 2 });
    out.push({ price: pivots.s2, side: sideOf(pivots.s2), type: 'PIVOT', label: 'S2', weight: 2 });
    out.push({ price: pivots.r3, side: sideOf(pivots.r3), type: 'PIVOT', label: 'R3', weight: 1 });
    out.push({ price: pivots.s3, side: sideOf(pivots.s3), type: 'PIVOT', label: 'S3', weight: 1 });
  }

  if (vwap) {
    out.push({ price: vwap.vwap, side: sideOf(vwap.vwap), type: 'VWAP', label: 'VWAP', weight: 3 });
    out.push({ price: vwap.upperBand, side: sideOf(vwap.upperBand), type: 'VWAP', label: 'VWAP+1σ', weight: 2 });
    out.push({ price: vwap.lowerBand, side: sideOf(vwap.lowerBand), type: 'VWAP', label: 'VWAP-1σ', weight: 2 });
  }

  if (fibs) {
    out.push({ price: fibs.fib236, side: sideOf(fibs.fib236), type: 'FIB', label: 'Fib 0.236', weight: 1 });
    out.push({ price: fibs.fib382, side: sideOf(fibs.fib382), type: 'FIB', label: 'Fib 0.382', weight: 2 });
    out.push({ price: fibs.fib500, side: sideOf(fibs.fib500), type: 'FIB', label: 'Fib 0.5', weight: 2 });
    out.push({ price: fibs.fib618, side: sideOf(fibs.fib618), type: 'FIB', label: 'Fib 0.618', weight: 3 });
    out.push({ price: fibs.fib786, side: sideOf(fibs.fib786), type: 'FIB', label: 'Fib 0.786', weight: 1 });
    out.push({ price: fibs.fib1272, side: sideOf(fibs.fib1272), type: 'FIB', label: 'Fib 1.272', weight: 2 });
    out.push({ price: fibs.fib1618, side: sideOf(fibs.fib1618), type: 'FIB', label: 'Fib 1.618', weight: 2 });
  }

  return out;
}

/**
 * ProximityGate — V24.0 Fade-the-Level Entry Filter
 *
 * เปลี่ยน entry strategy จาก "BREAKOUT chasing" เป็น "FADE THE LEVEL" — เข้าเทรด
 * ก็ต่อเมื่อราคาอยู่ใกล้ S/R wall ที่มีนัยสำคัญ (within 50-100 pips):
 *   - BUY  : ราคาลงไปใกล้ supportWall (คาดเด้งขึ้น)
 *   - SELL : ราคาขึ้นไปใกล้ resistanceWall (คาดเด้งลง)
 *
 * ★ rating policy
 *   ★★★+   : approve outright (3+ TF confluence)
 *   ★★     : approve เฉพาะเมื่อ LTF (M5/M15) RSI extreme หรือ smc bias ยืนยัน reversal
 *   ★      : reject  ดาวน้อยเกินไป ต้อง confirm ก่อน
 *
 * 2026-05-07 — skyliner.jojo@gmail.com
 */

import type { PriceMap, PriceWall } from '../analyzers/smc/types.js';
import type { AnalysisSummary } from '../types.js';

export interface ProximityGateResult {
  allowed: boolean;
  reason: string;
  reason_th: string;
  wallStars: number;
  nearestWall: PriceWall | null;
  distancePip: number;
}

export interface ProximityGateOptions {
  /** pip ขั้นต่ำ — 0 = ที่ wall เลย, ปกติไม่ต้องตั้ง */
  minPipDist?: number;
  /** pip สูงสุดที่ยังถือว่า "ใกล้" — default 100 pip */
  maxPipDist?: number;
  /** ★ rating ขั้นต่ำที่ผ่านได้แบบ unconditional */
  unconditionalStars?: number;
  /** ★ rating ขั้นต่ำที่ต้องมี LTF confirmation */
  conditionalStars?: number;
  /** บังคับว่าต้องมี LTF confirmation เสมอ ไม่ว่ากำแพงจะหนาแค่ไหน (เช่น Scalping/SMC) */
  requireLtfConfirmation?: boolean;
  /** Allow a very strong wall just outside max distance to pass only with LTF confirmation. */
  strongWallRelaxMultiplier?: number;
}

/**
 * pipUnit ต่อ symbol (ราคาต่อ 1 pip):
 *   XAUUSD : 1 pip = $0.01  →  100 pip = $1.00
 *   XBTUSD : 1 pip = $1.00  →  100 pip = $100
 *   FX     : 1 pip = 0.0001 →  100 pip = 0.01
 */
export function getPipUnit(symbol: string): number {
  const upper = symbol.toUpperCase();
  if (upper === 'XAUUSD' || upper === 'GOLD' || upper.startsWith('XAU')) return 0.01;
  if (upper === 'XBTUSD' || upper === 'BTCUSD' || upper.startsWith('XBT') || upper.startsWith('BTC')) return 1.0;
  // JPY pairs
  if (upper.endsWith('JPY')) return 0.01;
  // Default FX
  return 0.0001;
}

export function computeAdaptiveProximityMaxPip(
  symbol: string,
  atr: number | null | undefined,
  baseMaxPip = 100,
  opts: {
    atrMultiplier?: number;
    capMultiplier?: number;
  } = {}
): number {
  const safeBase = Math.max(1, Math.round(baseMaxPip));
  const pipUnit = getPipUnit(symbol);
  const cleanAtr = typeof atr === 'number' && Number.isFinite(atr) && atr > 0 ? atr : 0;
  const atrMultiplier = opts.atrMultiplier ?? 0.25;
  const capMultiplier = opts.capMultiplier ?? 5;
  const atrPips = cleanAtr > 0 ? Math.round((cleanAtr * atrMultiplier) / pipUnit) : 0;
  const atrScaled = Math.max(safeBase, atrPips);
  const hardCap = Math.max(safeBase, Math.round(safeBase * capMultiplier));
  return Math.min(atrScaled, hardCap);
}

/**
 * ตัดสินว่า entry ของ side นี้ผ่าน proximity gate หรือไม่
 *
 * @param symbol Symbol name (uppercase)
 * @param side BUY หรือ SELL
 * @param lastPrice ราคาปัจจุบัน
 * @param priceMap MTF wall map (จาก IndicatorPipeline)
 * @param ltfAnalysis Analysis ของ TF เล็ก (M5 หรือ M15) ใช้ confirm reversal
 * @param opts override ค่า default
 */
export function evaluateProximityGate(
  symbol: string,
  side: 'BUY' | 'SELL',
  lastPrice: number,
  priceMap: PriceMap | null,
  ltfAnalysis: AnalysisSummary | null,
  opts: ProximityGateOptions = {}
): ProximityGateResult {
  const minPip = opts.minPipDist ?? 0;
  const maxPip = opts.maxPipDist ?? 100;
  const unconditional = opts.unconditionalStars ?? 3;
  const conditional = opts.conditionalStars ?? 2;
  const pipUnit = getPipUnit(symbol);

  if (!priceMap) {
    return {
      allowed: false,
      reason: 'No PriceMap available — cannot evaluate proximity',
      reason_th: 'ไม่มีข้อมูล PriceMap — ข้ามการเช็คตำแหน่ง',
      wallStars: 0,
      nearestWall: null,
      distancePip: Number.POSITIVE_INFINITY,
    };
  }

  // ── หา wall ที่อยู่ในทิศทางที่เราจะ "fade" ─────────────────────────
  // BUY: ต้องมี supportWall อยู่ใต้ราคาในระยะที่ตั้งไว้
  // SELL: ต้องมี resistanceWall อยู่บนราคาในระยะที่ตั้งไว้
  const candidates = side === 'BUY' ? priceMap.supportWalls : priceMap.resistanceWalls;
  const sideOk = (w: PriceWall) =>
    side === 'BUY' ? w.price <= lastPrice : w.price >= lastPrice;

  let nearestWall: PriceWall | null = null;
  let nearestPip = Number.POSITIVE_INFINITY;

  let bestValidWall: PriceWall | null = null;
  let bestValidPip = Number.POSITIVE_INFINITY;
  let bestValidStars = 0;
  let strongestNearWall: PriceWall | null = null;
  let strongestNearPip = Number.POSITIVE_INFINITY;
  let strongestNearStars = 0;

  const ltfConfirm = checkLtfConfirmation(side, ltfAnalysis);
  const relaxedMaxPip = maxPip * Math.max(1, opts.strongWallRelaxMultiplier ?? 1);

  for (const wall of candidates) {
    if (!sideOk(wall)) continue;
    const distPip = Math.abs(lastPrice - wall.price) / pipUnit;
    
    // Track nearest absolute wall for logging
    if (distPip < nearestPip) {
      nearestPip = distPip;
      nearestWall = wall;
    }

    if (distPip >= minPip && distPip <= relaxedMaxPip && wall.confluenceStars >= unconditional + 1) {
      if (wall.confluenceStars > strongestNearStars || (wall.confluenceStars === strongestNearStars && distPip < strongestNearPip)) {
        strongestNearStars = wall.confluenceStars;
        strongestNearPip = distPip;
        strongestNearWall = wall;
      }
    }

    // Check if within allowed distance
    if (distPip >= minPip && distPip <= maxPip) {
       let isValid = false;
       
       if (wall.confluenceStars >= unconditional) {
          isValid = opts.requireLtfConfirmation ? ltfConfirm.ok : true;
       } else if (wall.confluenceStars >= conditional && ltfConfirm.ok) {
          isValid = true;
       } else if (wall.confluenceStars === 1 && ltfConfirm.ok && (wall.hasOB || wall.hasFVG || wall.hasLiquidity)) {
          isValid = true;
       }

       if (isValid) {
          // Prefer higher stars. If stars equal, prefer closer distance.
          if (wall.confluenceStars > bestValidStars || (wall.confluenceStars === bestValidStars && distPip < bestValidPip)) {
             bestValidStars = wall.confluenceStars;
             bestValidPip = distPip;
             bestValidWall = wall;
          }
       }
    }
  }

  const dirWord = side === 'BUY' ? 'support' : 'resistance';
  const dirThai = side === 'BUY' ? 'แนวรับ' : 'แนวต้าน';

  // ── ถ้าเจอ wall ที่ valid ในระยะ ────────────────────────────────────
  if (bestValidWall) {
     if (bestValidWall.confluenceStars >= unconditional) {
        return {
          allowed: true,
          reason: `${side} near ${bestValidWall.confluenceStars}★ ${dirWord} @ ${bestValidWall.price.toFixed(2)} (${bestValidPip.toFixed(0)} pips, TFs=${bestValidWall.timeframes.join('+')})`,
          reason_th: `${side} ใกล้${dirThai} ${bestValidWall.confluenceStars}★ ที่ ${bestValidWall.price.toFixed(2)} (${bestValidPip.toFixed(0)} pip · ${bestValidWall.timeframes.join('+')})`,
          wallStars: bestValidWall.confluenceStars,
          nearestWall: bestValidWall,
          distancePip: bestValidPip,
        };
     } else {
        return {
          allowed: true,
          reason: `${side} near ${bestValidWall.confluenceStars}★ ${dirWord} @ ${bestValidWall.price.toFixed(2)} (${bestValidPip.toFixed(0)} pips) + LTF confirm: ${ltfConfirm.note}`,
          reason_th: `${side} ใกล้${dirThai} ${bestValidWall.confluenceStars}★ ที่ ${bestValidWall.price.toFixed(2)} (${bestValidPip.toFixed(0)} pip) + LTF ยืนยัน: ${ltfConfirm.note_th}`,
          wallStars: bestValidWall.confluenceStars,
          nearestWall: bestValidWall,
          distancePip: bestValidPip,
        };
     }
  }

  // ── ไม่มี wall ที่ valid ในระยะ (reject) ────────────────────────────────────
  if (strongestNearWall && ltfConfirm.ok && strongestNearPip > maxPip) {
     return {
       allowed: true,
       reason: `${side} near extended ${strongestNearWall.confluenceStars}-star ${dirWord} @ ${strongestNearWall.price.toFixed(2)} (${strongestNearPip.toFixed(0)} pips, max ${maxPip.toFixed(0)}) + LTF confirm: ${ltfConfirm.note}`,
       reason_th: `${side} extended strong wall ${strongestNearWall.confluenceStars}* @ ${strongestNearWall.price.toFixed(2)} + LTF confirm`,
       wallStars: strongestNearWall.confluenceStars,
       nearestWall: strongestNearWall,
       distancePip: strongestNearPip,
     };
  }

  if (!nearestWall) {
    return {
      allowed: false,
      reason: `No ${dirWord} wall found ${side === 'BUY' ? 'below' : 'above'} price`,
      reason_th: `ไม่พบ${dirThai}ที่${side === 'BUY' ? 'ใต้' : 'เหนือ'}ราคา`,
      wallStars: 0,
      nearestWall: null,
      distancePip: Number.POSITIVE_INFINITY,
    };
  }
  
  if (nearestPip > maxPip || nearestPip < minPip) {
    return {
      allowed: false,
      reason: `PROXIMITY_TOO_FAR: nearest ${dirWord} ${nearestWall.confluenceStars}-star is ${nearestPip.toFixed(0)} pips away (max ${maxPip.toFixed(0)})`,
      reason_th: `${dirThai} ${nearestWall.confluenceStars}★ ที่ใกล้สุด ห่าง ${nearestPip.toFixed(0)} pip (เกิน ${maxPip.toFixed(0)} pip)`,
      wallStars: nearestWall.confluenceStars,
      nearestWall,
      distancePip: nearestPip,
    };
  }

  // If we reach here, nearestWall is within maxPip but didn't pass valid check
  const stars = nearestWall.confluenceStars;
  if (stars >= conditional) {
     return {
        allowed: false,
        reason: `PROXIMITY_NO_LTF_CONFIRM: ${side} near ${stars}-star ${dirWord} @ ${nearestWall.price.toFixed(2)} but no LTF confirmation (${ltfConfirm.note})`,
        reason_th: `${side} ใกล้${dirThai} ${stars}★ ที่ ${nearestWall.price.toFixed(2)} แต่ไม่มี LTF ยืนยัน (${ltfConfirm.note_th})`,
        wallStars: stars,
        nearestWall,
        distancePip: nearestPip,
     };
  }

  return {
    allowed: false,
    reason: `PROXIMITY_WEAK_WALL: ${side} near only ${stars}-star ${dirWord} - too weak to fade (need 2-star+ with LTF confirm or 3-star+)`,
    reason_th: `${side} ใกล้${dirThai} ${stars}★ เท่านั้น — ดาวน้อยไป (ต้อง ★★+LTF หรือ ★★★+)`,
    wallStars: stars,
    nearestWall,
    distancePip: nearestPip,
  };
}

/**
 * ตรวจ LTF confirmation จาก AnalysisSummary
 * - BUY: RSI < 35 (oversold) ในเชิง mean-reversion
 * - SELL: RSI > 65 (overbought)
 * + bias ของ LTF ไม่ตรงกับ side ก็ยังให้ผ่านได้ ถ้ามี indicator confirm
 *   (เพราะเรากำลัง fade — บรรยากาศควรขัดทิศกับ side)
 */
function checkLtfConfirmation(
  side: 'BUY' | 'SELL',
  ltf: AnalysisSummary | null
): { ok: boolean; note: string; note_th: string } {
  if (!ltf) {
    return { ok: false, note: 'no LTF data', note_th: 'ไม่มี LTF' };
  }

  const rsi = typeof ltf.rsi === 'number' ? ltf.rsi : null;
  const ltfBias = ltf.bias;
  const alignedSignalBias = side === 'BUY' ? 'BULL' : 'BEAR';
  const alignedSignals = (ltf.signals || [])
    .filter((s) => s.bias === alignedSignalBias && Number(s.score ?? 0) >= 12)
    .sort((a, b) => Number(b.score ?? 0) - Number(a.score ?? 0));
  const alignedScore = alignedSignals.reduce((sum, s) => sum + Number(s.score ?? 0), 0);

  const reasons: string[] = [];
  const reasonsTh: string[] = [];

  if (side === 'BUY') {
    if (rsi !== null && rsi < 35) {
      reasons.push(`RSI=${rsi.toFixed(0)} oversold`);
      reasonsTh.push(`RSI=${rsi.toFixed(0)} oversold`);
    }
    // LTF bias เริ่มกลับเป็น BULL = bullish reversal
    if (ltfBias === 'BULL') {
      reasons.push('LTF bias=BULL');
      reasonsTh.push('LTF กลับเป็น BULL');
    }
    if (alignedScore >= 25) {
      reasons.push(`LTF signals=${alignedSignals.slice(0, 2).map((s) => s.name).join('+')}`);
      reasonsTh.push('LTF signals aligned');
    }
  } else {
    if (rsi !== null && rsi > 65) {
      reasons.push(`RSI=${rsi.toFixed(0)} overbought`);
      reasonsTh.push(`RSI=${rsi.toFixed(0)} overbought`);
    }
    if (ltfBias === 'BEAR') {
      reasons.push('LTF bias=BEAR');
      reasonsTh.push('LTF กลับเป็น BEAR');
    }
    if (alignedScore >= 25) {
      reasons.push(`LTF signals=${alignedSignals.slice(0, 2).map((s) => s.name).join('+')}`);
      reasonsTh.push('LTF signals aligned');
    }
  }

  if (reasons.length === 0) {
    return {
      ok: false,
      note: rsi !== null
        ? `RSI=${rsi.toFixed(0)} not extreme, LTF bias=${ltfBias}`
        : `no RSI, LTF bias=${ltfBias}`,
      note_th: rsi !== null
        ? `RSI=${rsi.toFixed(0)} ไม่สุดขอบ, LTF=${ltfBias}`
        : `ไม่มี RSI, LTF=${ltfBias}`,
    };
  }

  return { ok: true, note: reasons.join(' + '), note_th: reasonsTh.join(' + ') };
}

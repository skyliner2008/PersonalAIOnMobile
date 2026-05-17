/**
 * Indicator Confluence — V22.0
 * ใช้ RSI, MACD, Stochastic, BB, EMA/MA เป็น active scoring components
 * ในการตัดสินใจ entry — ไม่ใช่แค่ HTF bias filter แบบเดิม
 *
 * Flow:
 *   side ('BUY'|'SELL') + AnalysisSummary (M5 or M15)
 *   → IndicatorConfluenceResult
 *     ├── indicatorScore: -100 to +100
 *     ├── confidenceAdjustment: -20 to +20 (บวกเข้า base confidence)
 *     ├── confirmedBy[]: indicators ที่สนับสนุน trade
 *     ├── conflictedBy[]: indicators ที่ขัดแย้ง
 *     ├── shouldBlock: true ถ้าความขัดแย้งแรงเกิน
 *     └── reason: summary สั้นๆ
 *
 * 2026-05-07 — skyliner.jojo@gmail.com
 */

import type { AnalysisSummary, MarketRegime } from '../types.js';

// ── Types ────────────────────────────────────────────────────────────

export interface IndicatorConfluenceResult {
  /** net indicator score: positive = supporting, negative = conflicting */
  indicatorScore: number;
  /** amount to add/subtract from base SMC confidence (-20 to +20) */
  confidenceAdjustment: number;
  /** indicator signal names that confirm the trade direction */
  confirmedBy: string[];
  /** indicator signal names that conflict with the trade direction */
  conflictedBy: string[];
  /** block this trade entry due to too-strong indicator conflicts */
  shouldBlock: boolean;
  blockReason?: string;
  /** short human-readable summary for logs and triggers array */
  reason: string;
}

// ── Scoring Table ────────────────────────────────────────────────────
// Each entry: [signalName, scoreForBuy, scoreForSell]
// Positive = confirms the direction, negative = conflicts
// For SELL we flip the sign convention from the table column

type SignalRow = [name: string, buyScore: number, sellScore: number];

const SIGNAL_SCORES: SignalRow[] = [
  // ── RSI (from technical.ts) ──────────────────────────────────────
  ['RSI<30',             +15, -12],   // oversold → strong BUY confirm, SELL conflict
  ['RSI<45',             +6,  -5],    // below midline
  ['RSI-BULL-MOMENTUM',  +5,  -8],    // 55-70 bullish zone
  ['RSI>70',            -12, +15],    // overbought → BUY conflict, SELL confirm

  // ── Bollinger Bands (from technical.ts) ──────────────────────────
  ['BB-LOWER',           +8,  -8],    // price at lower band → mean-reversion BUY
  ['BB-UPPER',           -8,  +8],    // price at upper band → mean-reversion SELL

  // ── EMA / MA Trend Structure (from technical.ts) ─────────────────
  ['EMA20>MA50',         +6,  -5],    // uptrend structure → favors BUY
  ['EMA20<MA50',         -5,  +6],    // downtrend structure → favors SELL

  // ── MACD (from momentum.ts) ──────────────────────────────────────
  ['MACD-BULL',         +12, -10],    // bullish cross + positive histogram
  ['MACD-ABOVE-ZERO',   +5,  -5],    // both MACD & Signal above zero
  ['MACD-BEAR',         -10, +12],    // bearish cross + negative histogram
  ['MACD-BELOW-ZERO',   -5,  +5],    // both MACD & Signal below zero

  // ── Stochastic (from momentum.ts) ────────────────────────────────
  ['STOCH-OVERSOLD',    +12, -12],   // %K & %D < 20
  ['STOCH-CROSS-UP',    +6,  -5],    // %K crossed above %D (mid-zone)
  ['STOCH-OVERBOUGHT',  -12, +12],   // %K & %D > 80
  ['STOCH-CROSS-DN',    -5,  +6],    // %K crossed below %D (mid-zone)
];

// ── Regime Bonus ────────────────────────────────────────────────────

function regimeScore(regime: MarketRegime, side: 'BUY' | 'SELL'): number {
  switch (regime) {
    case 'TRENDING_UP':       return side === 'BUY'  ? +8 : -6;
    case 'TRENDING_DOWN':     return side === 'SELL' ? +8 : -6;
    case 'RANGING':           return -5;   // both sides slightly penalised
    case 'VOLATILE_BREAKOUT': return 0;    // neutral — too risky to bias
    case 'QUIET':             return -3;   // low momentum → less conviction
    default:                  return 0;
  }
}

// ── Block Conditions ─────────────────────────────────────────────────

/** Hard-block: overbought extreme on BUY, oversold extreme on SELL */
function checkHardBlock(
  side: 'BUY' | 'SELL',
  analysis: AnalysisSummary,
  signalNames: Set<string>
): string | null {
  const rsi = analysis.rsi ?? 50;

  if (side === 'BUY') {
    // Extreme overbought — price likely to reverse down
    if (rsi >= 78) return `RSI=${rsi.toFixed(1)} extreme overbought — BUY blocked`;
    // Stochastic overbought + bearish MACD momentum — double-confirm reversal down
    if (signalNames.has('STOCH-OVERBOUGHT') && signalNames.has('MACD-BEAR')) {
      return 'STOCH-OVERBOUGHT + MACD-BEAR — momentum strongly against BUY';
    }
  }

  if (side === 'SELL') {
    // Extreme oversold — price likely to bounce
    if (rsi <= 22) return `RSI=${rsi.toFixed(1)} extreme oversold — SELL blocked`;
    // Stochastic oversold + bullish MACD — double-confirm bounce
    if (signalNames.has('STOCH-OVERSOLD') && signalNames.has('MACD-BULL')) {
      return 'STOCH-OVERSOLD + MACD-BULL — momentum strongly against SELL';
    }
  }

  return null;
}

// ── Main Function ────────────────────────────────────────────────────

/**
 * checkIndicatorConfluence
 *
 * ตรวจสอบ RSI, MACD, Stochastic, BB, EMA/MA จาก AnalysisSummary
 * และให้ค่า adjustedConfidence กับ shouldBlock สำหรับ entry signal
 *
 * @param side    'BUY' | 'SELL' — ทิศทาง trade ที่ SMC ตรวจพบ
 * @param analysis AnalysisSummary ของ entry TF (M5 หรือ M15)
 */
export function checkIndicatorConfluence(
  side: 'BUY' | 'SELL',
  analysis: AnalysisSummary
): IndicatorConfluenceResult {
  const signalNames = new Set(analysis.signals.map(s => s.name));
  const confirmedBy: string[] = [];
  const conflictedBy: string[] = [];
  let rawScore = 0;

  // 1. Score each known indicator signal
  for (const [name, buyScore, sellScore] of SIGNAL_SCORES) {
    if (!signalNames.has(name)) continue;

    const score = side === 'BUY' ? buyScore : sellScore;
    rawScore += score;

    if (score > 0) {
      confirmedBy.push(`${name}(+${score})`);
    } else if (score < 0) {
      conflictedBy.push(`${name}(${score})`);
    }
  }

  // 2. Regime bonus/penalty
  const rBonus = regimeScore(analysis.regime, side);
  if (rBonus !== 0) {
    rawScore += rBonus;
    if (rBonus > 0) confirmedBy.push(`regime:${analysis.regime}(+${rBonus})`);
    else conflictedBy.push(`regime:${analysis.regime}(${rBonus})`);
  }

  // 3. Hard-block check
  const hardBlockReason = checkHardBlock(side, analysis, signalNames);
  if (hardBlockReason) {
    return {
      indicatorScore: rawScore,
      confidenceAdjustment: -20,
      confirmedBy,
      conflictedBy,
      shouldBlock: true,
      blockReason: hardBlockReason,
      reason: `BLOCKED: ${hardBlockReason}`,
    };
  }

  // 4. Soft-block: score too negative AND too many conflicts
  const shouldBlock =
    rawScore < -25 && conflictedBy.length >= 3;

  // 5. Confidence adjustment: score/5, capped [-20, +20]
  const confidenceAdjustment = Math.max(-20, Math.min(20, Math.round(rawScore / 5)));

  // 6. Build summary reason
  const parts: string[] = [];
  if (confirmedBy.length > 0) parts.push(`✓ ${confirmedBy.map(s => s.split('(')[0]).join(', ')}`);
  if (conflictedBy.length > 0) parts.push(`✗ ${conflictedBy.map(s => s.split('(')[0]).join(', ')}`);
  const reason = parts.length > 0
    ? parts.join(' | ')
    : `No indicator signals (score ${rawScore > 0 ? '+' : ''}${rawScore})`;

  return {
    indicatorScore: rawScore,
    confidenceAdjustment,
    confirmedBy,
    conflictedBy,
    shouldBlock: shouldBlock ? true : false,
    blockReason: shouldBlock
      ? `Score ${rawScore} with ${conflictedBy.length} conflicting indicators`
      : undefined,
    reason,
  };
}

/**
 * applyIndicatorConfluence
 *
 * Helper: รับ EntrySignal ดิบ + AnalysisSummary → ปรับ confidence
 * และ filter ออกถ้า shouldBlock
 *
 * @returns ปรับ confidence แล้ว หรือ null ถ้าถูก block
 */
export function applyIndicatorConfluence<T extends { side: 'BUY' | 'SELL'; confidence: number; triggers: string[] }>(
  signal: T,
  analysis: AnalysisSummary | undefined
): T | null {
  if (!analysis) return signal;  // ไม่มีข้อมูล indicator → pass through

  const result = checkIndicatorConfluence(signal.side, analysis);

  if (result.shouldBlock) {
    return null;  // block signal
  }

  const newConfidence = Math.max(10, Math.min(100,
    signal.confidence + result.confidenceAdjustment
  ));

  return {
    ...signal,
    confidence: newConfidence,
    triggers: [
      ...signal.triggers,
      `IND: ${result.reason}`,
      ...(result.confidenceAdjustment !== 0
        ? [`conf adj ${result.confidenceAdjustment > 0 ? '+' : ''}${result.confidenceAdjustment}`]
        : []),
    ],
  };
}

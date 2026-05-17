import { indicators, parseCandles } from '../../tracking.js';
import type { AnalysisSignal, AnalyzerModuleResult } from '../types.js';
import { round1, round2 } from '../utils.js';

/**
 * Momentum Analyzer — MACD (12,26,9) + Stochastic (14,3)
 *
 * เพิ่มมาเพื่อแทนที่ "MACD-like" (ema20 - sma20) ใน technical.ts
 * ซึ่งไม่ใช่ MACD มาตรฐาน และ Stochastic ช่วยจับ oversold/overbought
 * ในตลาดที่ RSI อาจยังไม่ถึง threshold.
 */
export function momentumAnalyzer(candles: ReturnType<typeof parseCandles>): AnalyzerModuleResult {
  if (candles.length < 50) {
    return { name: 'MOMENTUM', bias: 'NEUTRAL', strengthScore: 0, signals: [], notes: 'insufficient candles' };
  }

  const stats = indicators(candles);
  const macdLine      = stats.macdLine      ?? 0;
  const macdSignal    = stats.macdSignal    ?? 0;
  const macdHistogram = stats.macdHistogram ?? 0;
  const stochK        = stats.stochK        ?? 50;
  const stochD        = stats.stochD        ?? 50;

  const signals: AnalysisSignal[] = [];
  let bull = 0;
  let bear = 0;
  let score = 40;

  // --- MACD Cross Signal ---
  if (stats.macdLine !== null && stats.macdSignal !== null) {
    if (macdLine > macdSignal && macdHistogram > 0) {
      signals.push({ name: 'MACD-BULL', bias: 'BULL', score: 15,
        evidence: `MACD ${round2(macdLine)} > Signal ${round2(macdSignal)} | Hist: +${round2(macdHistogram)}` });
      bull++; score += 15;
    } else if (macdLine < macdSignal && macdHistogram < 0) {
      signals.push({ name: 'MACD-BEAR', bias: 'BEAR', score: 15,
        evidence: `MACD ${round2(macdLine)} < Signal ${round2(macdSignal)} | Hist: ${round2(macdHistogram)}` });
      bear++; score += 15;
    }

    // MACD above/below zero line — confirms trend direction
    if (macdLine > 0 && macdSignal > 0) {
      signals.push({ name: 'MACD-ABOVE-ZERO', bias: 'BULL', score: 7,
        evidence: `MACD ${round2(macdLine)} above zero` });
      bull++; score += 7;
    } else if (macdLine < 0 && macdSignal < 0) {
      signals.push({ name: 'MACD-BELOW-ZERO', bias: 'BEAR', score: 7,
        evidence: `MACD ${round2(macdLine)} below zero` });
      bear++; score += 7;
    }
  }

  // --- Stochastic Extreme Zone ---
  if (stats.stochK !== null && stats.stochD !== null) {
    if (stochK < 20 && stochD < 20) {
      signals.push({ name: 'STOCH-OVERSOLD', bias: 'BULL', score: 14,
        evidence: `%K=${round1(stochK)} %D=${round1(stochD)} — oversold` });
      bull += 2; score += 14;
    } else if (stochK > 80 && stochD > 80) {
      signals.push({ name: 'STOCH-OVERBOUGHT', bias: 'BEAR', score: 14,
        evidence: `%K=${round1(stochK)} %D=${round1(stochD)} — overbought` });
      bear += 2; score += 14;
    }

    // Stochastic %K/%D crossover (momentum shift — only in non-extreme zones)
    if (stochK > stochD && stochK >= 20 && stochK <= 80) {
      signals.push({ name: 'STOCH-CROSS-UP', bias: 'BULL', score: 6,
        evidence: `%K=${round1(stochK)} crossed above %D=${round1(stochD)}` });
      bull++; score += 6;
    } else if (stochK < stochD && stochK >= 20 && stochK <= 80) {
      signals.push({ name: 'STOCH-CROSS-DN', bias: 'BEAR', score: 6,
        evidence: `%K=${round1(stochK)} crossed below %D=${round1(stochD)}` });
      bear++; score += 6;
    }
  }

  const dominantBias = bull > bear ? 'BULL' : bear > bull ? 'BEAR' : 'NEUTRAL';

  return {
    name: 'MOMENTUM',
    bias: dominantBias,
    strengthScore: Math.min(score, 100),
    signals,
    notes: `MACD=${round2(macdLine)}/Sig=${round2(macdSignal)}/Hist=${round2(macdHistogram)} | Stoch K=${round1(stochK)} D=${round1(stochD)}`,
  };
}

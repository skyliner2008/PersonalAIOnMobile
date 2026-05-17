import { parseCandles } from '../../tracking.js';
import type { AnalysisSignal, AnalyzerModuleResult } from '../types.js';
import { round2 } from '../utils.js';

export function rangeAnalyzer(candles: ReturnType<typeof parseCandles>): AnalyzerModuleResult {
  if (candles.length < 60) {
    return { name: 'RANGE', bias: 'NEUTRAL', strengthScore: 0, signals: [], notes: 'insufficient candles' };
  }

  const current = candles[candles.length - 1];
  const rangeSlice = candles.slice(-40);
  const hi = Math.max(...rangeSlice.map((c) => c.h));
  const lo = Math.min(...rangeSlice.map((c) => c.l));
  const width = hi - lo;
  const fromLo = current.c - lo;
  const fromHi = hi - current.c;
  const recentMoves = candles.slice(-14).map((c, index, arr) => index === 0 ? 0 : Math.abs(c.c - arr[index - 1].c));
  const avgMove = recentMoves.reduce((sum, value) => sum + value, 0) / Math.max(recentMoves.length, 1);
  const adxLikeQuiet = width > 0 && avgMove / width < 0.18;

  const signals: AnalysisSignal[] = [];
  let score = 40;
  let bias: AnalyzerModuleResult['bias'] = 'NEUTRAL';

  if (adxLikeQuiet && width > 0) {
    if (fromLo / width < 0.25) {
      signals.push({ name: 'RANGE-LO', bias: 'BULL', score: 18, evidence: `near low ${round2(lo)}` });
      bias = 'BULL';
      score += 18;
    }
    if (fromHi / width < 0.25) {
      signals.push({ name: 'RANGE-HI', bias: 'BEAR', score: 18, evidence: `near high ${round2(hi)}` });
      bias = 'BEAR';
      score += 18;
    }
    if (bias === 'NEUTRAL') {
      signals.push({ name: 'RANGE-MID', bias: 'NEUTRAL', score: 3, evidence: 'mid range' });
    }
  }

  return {
    name: 'RANGE',
    bias,
    strengthScore: Math.min(score, 100),
    signals,
    notes: `range=[${round2(lo)},${round2(hi)}]`,
  };
}

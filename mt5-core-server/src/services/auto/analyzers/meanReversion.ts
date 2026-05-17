import { indicators, parseCandles } from '../../tracking.js';
import type { AnalysisSignal, AnalyzerModuleResult } from '../types.js';
import { round1, round2 } from '../utils.js';

export function meanReversionAnalyzer(candles: ReturnType<typeof parseCandles>): AnalyzerModuleResult {
  if (candles.length < 50) {
    return { name: 'MEAN_REVERSION', bias: 'NEUTRAL', strengthScore: 0, signals: [], notes: 'insufficient candles' };
  }

  const stats = indicators(candles);
  const last = candles[candles.length - 1];
  const rsi = stats.rsi14 ?? 50;
  const sma50 = stats.sma50 ?? last.c;
  const window = candles.slice(-20).map((c) => c.c);
  const mean = window.reduce((sum, value) => sum + value, 0) / Math.max(window.length, 1);
  const variance = window.reduce((sum, value) => sum + (value - mean) ** 2, 0) / Math.max(window.length, 1);
  const sd = Math.sqrt(variance);
  const bbUpper = mean + sd * 2;
  const bbLower = mean - sd * 2;

  const signals: AnalysisSignal[] = [];
  let score = 40;
  let bias: AnalyzerModuleResult['bias'] = 'NEUTRAL';

  if (last.c <= bbLower && rsi < 35) {
    signals.push({ name: 'MEAN-REV-BUY', bias: 'BULL', score: 25, evidence: `bbLower + rsi=${round1(rsi)}` });
    bias = 'BULL';
    score += 25;
  }
  if (last.c >= bbUpper && rsi > 65) {
    signals.push({ name: 'MEAN-REV-SELL', bias: 'BEAR', score: 25, evidence: `bbUpper + rsi=${round1(rsi)}` });
    bias = 'BEAR';
    score += 25;
  }
  if (sma50 > 0) {
    const devPct = ((last.c - sma50) / sma50) * 100;
    if (Math.abs(devPct) > 3) {
      signals.push({
        name: 'STRETCH-SMA50',
        bias: devPct > 0 ? 'BEAR' : 'BULL',
        score: 8,
        evidence: `deviation=${round2(devPct)}%`,
      });
      score += 8;
      if (bias === 'NEUTRAL') bias = devPct > 0 ? 'BEAR' : 'BULL';
    }
  }

  return {
    name: 'MEAN_REVERSION',
    bias,
    strengthScore: Math.min(score, 100),
    signals,
    notes: `rsi=${round1(rsi)}`,
  };
}

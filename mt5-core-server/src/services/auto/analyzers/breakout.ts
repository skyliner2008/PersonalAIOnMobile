import { indicators, parseCandles } from '../../tracking.js';
import type { AnalysisSignal, AnalyzerModuleResult } from '../types.js';
import { round2 } from '../utils.js';

export function breakoutAnalyzer(candles: ReturnType<typeof parseCandles>): AnalyzerModuleResult {
  if (candles.length < 50) {
    return { name: 'BREAKOUT', bias: 'NEUTRAL', strengthScore: 0, signals: [], notes: 'insufficient candles' };
  }

  const last = candles[candles.length - 1];
  const stats = indicators(candles);
  const atr14 = stats.atr14 ?? 0;
  const longSlice = candles.slice(-48);
  const hi = Math.max(...candles.slice(-20).map((c) => c.h));
  const lo = Math.min(...candles.slice(-20).map((c) => c.l));
  const longRange = longSlice.map((c) => Math.abs(c.h - c.l));
  const avgLongAtr = longRange.reduce((sum, v) => sum + v, 0) / Math.max(longRange.length, 1);
  const volumes = candles.slice(-20).map((c) => c.v ?? 0);
  const volAvg = volumes.reduce((sum, v) => sum + v, 0) / Math.max(volumes.length, 1);
  const volNow = last.v ?? 0;
  const compressing = avgLongAtr > 0 && atr14 > 0 && atr14 / avgLongAtr < 0.75;
  const volSurge = volAvg > 0 && volNow > volAvg * 1.8;

  const signals: AnalysisSignal[] = [];
  let score = 40;
  let bias: AnalyzerModuleResult['bias'] = 'NEUTRAL';

  if (last.c >= hi * 0.999) {
    signals.push({ name: 'BREAK-UP', bias: 'BULL', score: volSurge ? 25 : 15, evidence: `hi=${round2(hi)} volSurge=${volSurge}` });
    bias = 'BULL';
    score += volSurge ? 25 : 15;
  }
  if (last.c <= lo * 1.001) {
    signals.push({ name: 'BREAK-DN', bias: 'BEAR', score: volSurge ? 25 : 15, evidence: `lo=${round2(lo)} volSurge=${volSurge}` });
    bias = 'BEAR';
    score += volSurge ? 25 : 15;
  }
  if (compressing && bias === 'NEUTRAL') {
    signals.push({ name: 'PRE-BREAK', bias: 'NEUTRAL', score: 4, evidence: 'atr compression' });
  }

  return {
    name: 'BREAKOUT',
    bias,
    strengthScore: Math.min(score, 100),
    signals,
    notes: `hi=${round2(hi)} lo=${round2(lo)} atr=${round2(atr14)}`,
  };
}

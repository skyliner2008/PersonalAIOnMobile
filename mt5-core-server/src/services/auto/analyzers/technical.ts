import { indicators, parseCandles } from '../../tracking.js';
import type { AnalysisSignal, AnalyzerModuleResult, Bias } from '../types.js';
import { round1, round2 } from '../utils.js';

function aggregateBias(bull: number, bear: number): Bias {
  if (bull - bear >= 2) return 'BULL';
  if (bear - bull >= 2) return 'BEAR';
  return 'NEUTRAL';
}

export function technicalAnalyzer(candles: ReturnType<typeof parseCandles>): AnalyzerModuleResult {
  if (candles.length < 30) {
    return { name: 'TECHNICAL', bias: 'NEUTRAL', strengthScore: 0, signals: [], notes: 'insufficient candles' };
  }

  const stats = indicators(candles);
  const last = candles[candles.length - 1];
  const rsi = stats.rsi14 ?? 50;
  const ema20 = stats.ema20 ?? last.c;
  const sma20 = stats.sma20 ?? last.c;
  const sma50 = stats.sma50 ?? sma20;
  const window = candles.slice(-20).map((c) => c.c);
  const mean = window.reduce((sum, value) => sum + value, 0) / Math.max(window.length, 1);
  const variance = window.reduce((sum, value) => sum + (value - mean) ** 2, 0) / Math.max(window.length, 1);
  const sd = Math.sqrt(variance);
  const bbUpper = mean + sd * 2;
  const bbLower = mean - sd * 2;

  const signals: AnalysisSignal[] = [];
  let bull = 0;
  let bear = 0;
  let score = 50;

  // RSI Zones — oversold/overbought (mean reversion) AND momentum confirmation
  // RSI < 30: oversold → BULL reversal signal (high weight)
  // RSI 30-45: below midline → mild BULL momentum bias  
  // RSI 55-70: above midline, healthy uptrend → BULL momentum (not overbought)
  // RSI > 70: overbought → BEAR reversal signal (high weight)
  if (rsi < 30) {
    signals.push({ name: 'RSI<30', bias: 'BULL', score: 15, evidence: `rsi=${round1(rsi)} oversold` });
    bull += 2;
    score += 15;
  } else if (rsi < 45) {
    signals.push({ name: 'RSI<45', bias: 'BULL', score: 6, evidence: `rsi=${round1(rsi)} below midline` });
    bull += 1;
    score += 6;
  } else if (rsi > 70) {
    signals.push({ name: 'RSI>70', bias: 'BEAR', score: 15, evidence: `rsi=${round1(rsi)} overbought` });
    bear += 2;
    score += 15;
  } else if (rsi >= 55) {
    // 55-70 = bullish momentum zone (not yet overbought)
    signals.push({ name: 'RSI-BULL-MOMENTUM', bias: 'BULL', score: 6, evidence: `rsi=${round1(rsi)} bullish momentum` });
    bull += 1;
    score += 6;
  }

  if (last.c < bbLower) {
    signals.push({ name: 'BB-LOWER', bias: 'BULL', score: 6, evidence: `${round2(last.c)} < ${round2(bbLower)}` });
    bull += 1;
    score += 6;
  } else if (last.c > bbUpper) {
    signals.push({ name: 'BB-UPPER', bias: 'BEAR', score: 6, evidence: `${round2(last.c)} > ${round2(bbUpper)}` });
    bear += 1;
    score += 6;
  }

  if (ema20 > sma50 && last.c > ema20) {
    signals.push({ name: 'EMA20>MA50', bias: 'BULL', score: 7, evidence: 'uptrend structure' });
    bull += 1;
    score += 7;
  } else if (ema20 < sma50 && last.c < ema20) {
    signals.push({ name: 'EMA20<MA50', bias: 'BEAR', score: 7, evidence: 'downtrend structure' });
    bear += 1;
    score += 7;
  }

  return {
    name: 'TECHNICAL',
    bias: aggregateBias(bull, bear),
    strengthScore: Math.min(score, 100),
    signals,
    notes: `rsi=${round1(rsi)} bb=[${round2(bbLower)},${round2(bbUpper)}]`,
  };
}

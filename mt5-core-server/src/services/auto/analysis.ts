import { indicators, parseCandles } from '../tracking.js';
import type { AnalysisSignal, AnalysisSummary, AutoTradingConfig, Bias, MarketRegime, PositionRow, StrategyType, AnalyzerModuleResult } from './types.js';
import { clamp, round1, round2 } from './utils.js';
import { runDefaultAnalyzers } from './analyzers/index.js';
import { sessionAnalyzer } from './analyzers/session.js';

export function inferAnalysis(candles: ReturnType<typeof parseCandles>): AnalysisSummary {
  if (candles.length < 60) {
    return {
      regime: 'UNKNOWN',
      bias: 'NEUTRAL',
      confluence: 0,
      fitness: 0,
      strategy: 'HOLD_CASH',
      atr: null,
      rsi: null,
      sma20: null,
      sma50: null,
      ema20: null,
      signals: [],
      rationale: 'insufficient candles',
    };
  }
  const last = candles[candles.length - 1];
  const stats = indicators(candles);
  const sma20 = stats.sma20 ?? last.c;
  const sma50 = stats.sma50 ?? sma20;
  const ema20 = stats.ema20 ?? sma20;
  const atr = stats.atr14 ?? null;
  const rsi = stats.rsi14 ?? 50;
  const range = candles.slice(-20);
  const hi = Math.max(...range.map((c) => c.h));
  const lo = Math.min(...range.map((c) => c.l));
  const widthPct = last.c > 0 ? ((hi - lo) / last.c) * 100 : 0;
  const trendUp = last.c > sma20 && sma20 >= sma50;
  const trendDown = last.c < sma20 && sma20 <= sma50;
  const breakout = widthPct > 1.2 && Math.abs(last.c - sma20) > (atr ?? 0) * 0.8;
  const moduleResults = runDefaultAnalyzers(candles);
  const baseSignals: AnalysisSignal[] = [];
  if (trendUp) baseSignals.push({ name: 'trend_alignment_up', bias: 'BULL', score: 26, evidence: 'price > sma20 >= sma50' });
  else if (trendDown) baseSignals.push({ name: 'trend_alignment_down', bias: 'BEAR', score: 26, evidence: 'price < sma20 <= sma50' });
  if (last.c > ema20) baseSignals.push({ name: 'ema20_support', bias: 'BULL', score: 12, evidence: 'price above ema20' });
  else baseSignals.push({ name: 'ema20_resistance', bias: 'BEAR', score: 12, evidence: 'price below ema20' });
  if (rsi >= 58) baseSignals.push({ name: 'rsi_bullish', bias: 'BULL', score: 14, evidence: `rsi=${round1(rsi)}` });
  else if (rsi <= 42) baseSignals.push({ name: 'rsi_bearish', bias: 'BEAR', score: 14, evidence: `rsi=${round1(rsi)}` });

  const signals: AnalysisSignal[] = [...baseSignals, ...moduleResults.flatMap((it) => it.signals)];

  const bullScore = signals.filter((it) => it.bias === 'BULL').reduce((sum, it) => sum + it.score, 0);
  const bearScore = signals.filter((it) => it.bias === 'BEAR').reduce((sum, it) => sum + it.score, 0);
  const dominantBias: Bias = bullScore > bearScore ? 'BULL' : bearScore > bullScore ? 'BEAR' : 'NEUTRAL';
  const alignedScore = dominantBias === 'BULL' ? bullScore : dominantBias === 'BEAR' ? bearScore : 0;
  const totalScore = bullScore + bearScore;
  const confluence = totalScore > 0 ? clamp((alignedScore / totalScore) * 100, 0, 95) : 0;

  let regime: MarketRegime = 'UNKNOWN';
  if (trendUp) regime = breakout ? 'VOLATILE_BREAKOUT' : 'TRENDING_UP';
  else if (trendDown) regime = breakout ? 'VOLATILE_BREAKOUT' : 'TRENDING_DOWN';
  else if (widthPct < 0.35) regime = 'QUIET';
  else regime = 'RANGING';

  let strategy: StrategyType = 'HOLD_CASH';
  let fitness = 0;
  if (regime === 'TRENDING_UP' || regime === 'TRENDING_DOWN') {
    strategy = 'TREND_FOLLOW';
    fitness = clamp(45 + confluence * 0.45 + (breakout ? 6 : 0), 0, 100);
  } else if (regime === 'VOLATILE_BREAKOUT') {
    strategy = 'BREAKOUT';
    fitness = clamp(48 + confluence * 0.42, 0, 100);
  } else if (regime === 'RANGING' && dominantBias !== 'NEUTRAL') {
    // If it's a tight bounce or low timeframe, it's SCALPING, otherwise RANGE/MEAN_REVERSION
    if (rsi <= 32 || rsi >= 68) {
        strategy = 'MEAN_REVERSION';
    } else if (widthPct < 0.6) {
        strategy = 'SCALPING'; // Tight consolidation bounce
    } else {
        strategy = 'RANGE';
    }
    fitness = clamp(38 + confluence * 0.4 + (strategy === 'SCALPING' ? 10 : 0), 0, 100);
  } else if (regime === 'QUIET') {
    strategy = 'HOLD_CASH';
    fitness = 12;
  }

  // --- Session Awareness Integration [Phase 1] ---
  const sessionCtx = sessionAnalyzer.getCurrentSession();
  fitness = sessionAnalyzer.adjustFitness(strategy, sessionCtx, fitness);

  const rationale = [
    `regime=${regime}`,
    `bias=${dominantBias}`,
    `confluence=${round1(confluence)}`,
    `fitness=${round1(fitness)}`,
    `session=${sessionCtx.session}`,
    ...topModuleNotes(moduleResults),
    ...signals.slice(0, 4).map((it) => `${it.name}:${it.evidence}`),
  ].join(' | ');

  return { 
    regime, 
    bias: dominantBias, 
    confluence, 
    fitness, 
    strategy, 
    atr, 
    rsi: round1(rsi),
    sma20: round2(sma20),
    sma50: round2(sma50),
    ema20: round2(ema20),
    signals, 
    rationale 
  };
}

function topModuleNotes(results: AnalyzerModuleResult[]): string[] {
  return results
    .sort((a, b) => b.strengthScore - a.strengthScore)
    .slice(0, 3)
    .map((it) => `${it.name}:${it.bias}:${round1(it.strengthScore)}`);
}

export function pickStrategy(analysis: AnalysisSummary, cfg: AutoTradingConfig): StrategyType {
  if (cfg.preferredStrategies.length > 0 && cfg.preferredStrategies.includes(analysis.strategy)) return analysis.strategy;
  if (cfg.preferredStrategies.length > 0) {
    if ((analysis.regime === 'TRENDING_UP' || analysis.regime === 'TRENDING_DOWN') && cfg.preferredStrategies.includes('TREND_FOLLOW')) return 'TREND_FOLLOW';
    if (analysis.regime === 'VOLATILE_BREAKOUT' && cfg.preferredStrategies.includes('BREAKOUT')) return 'BREAKOUT';
    if (analysis.regime === 'RANGING') {
      if (cfg.preferredStrategies.includes('MEAN_REVERSION')) return 'MEAN_REVERSION';
      if (cfg.preferredStrategies.includes('RANGE')) return 'RANGE';
    }
  }
  return analysis.strategy;
}

export function correlationClusterCount(symbol: string, positions: PositionRow[]): number {
  const clusters = [
    ['EURUSD', 'GBPUSD', 'AUDUSD', 'NZDUSD'],
    ['USDJPY', 'USDCHF', 'USDCAD'],
    ['XAUUSD', 'XAGUSD', 'XPTUSD'],
    ['BTCUSD', 'ETHUSD'],
    ['US30', 'NAS100', 'SPX500', 'US500', 'GER40'],
  ];
  const cluster = clusters.find((group) => group.includes(symbol.toUpperCase()));
  if (!cluster) return positions.filter((it) => it.symbol.toUpperCase() === symbol.toUpperCase()).length;
  return positions.filter((it) => cluster.includes(it.symbol.toUpperCase())).length;
}

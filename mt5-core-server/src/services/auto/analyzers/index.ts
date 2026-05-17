import { parseCandles } from '../../tracking.js';
import type { AnalyzerModuleResult } from '../types.js';
import { breakoutAnalyzer } from './breakout.js';
import { meanReversionAnalyzer } from './meanReversion.js';
import { momentumAnalyzer } from './momentum.js';
import { rangeAnalyzer } from './range.js';
import { technicalAnalyzer } from './technical.js';
import { smcAnalyzer } from './smc.js';
import { multiAgentAnalyzer } from './multiAgent.js';

export function runDefaultAnalyzers(candles: ReturnType<typeof parseCandles>): AnalyzerModuleResult[] {
  return [
    technicalAnalyzer(candles),
    meanReversionAnalyzer(candles),
    momentumAnalyzer(candles),   // MACD(12,26,9) + Stochastic(14,3)
    breakoutAnalyzer(candles),
    rangeAnalyzer(candles),
    smcAnalyzer(candles),
    multiAgentAnalyzer(candles),
  ];
}

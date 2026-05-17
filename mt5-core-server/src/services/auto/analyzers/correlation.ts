import { AnalyzerModuleResult } from '../types.js';

/**
 * Calculates Pearson Correlation Coefficient between two series
 */
function calculateCorrelation(x: number[], y: number[]): number {
  const n = Math.min(x.length, y.length);
  if (n < 2) return 0;

  const muX = x.reduce((a, b) => a + b, 0) / n;
  const muY = y.reduce((a, b) => a + b, 0) / n;

  let num = 0;
  let denX = 0;
  let denY = 0;

  for (let i = 0; i < n; i++) {
    const dx = x[i] - muX;
    const dy = y[i] - muY;
    num += dx * dy;
    denX += dx * dx;
    denY += dy * dy;
  }

  const den = Math.sqrt(denX * denY);
  if (den === 0) return 0;
  return num / den;
}

export function analyzeCorrelation(
  symbol: string,
  symbolCandles: any[],
  openPositions: any[],
  allCandles: Map<string, any[]>
): AnalyzerModuleResult {
  const signals: any[] = [];
  let maxCorrelation = 0;
  let correlatedSymbol = '';

  const currentPrices = symbolCandles.map(c => c.c);

  for (const pos of openPositions) {
    if (pos.symbol === symbol) continue;
    
    const otherCandles = allCandles.get(pos.symbol);
    if (!otherCandles) continue;

    const otherPrices = otherCandles.map(c => c.c);
    const corr = calculateCorrelation(currentPrices, otherPrices);

    if (Math.abs(corr) > Math.abs(maxCorrelation)) {
      maxCorrelation = corr;
      correlatedSymbol = pos.symbol;
    }

    signals.push({
      name: `Corr_${pos.symbol}`,
      bias: corr > 0.7 ? 'BULL' : corr < -0.7 ? 'BEAR' : 'NEUTRAL',
      score: Math.abs(corr) * 100,
      evidence: `Correlation with ${pos.symbol} is ${corr.toFixed(2)}`
    });
  }

  const isHighlyCorrelated = Math.abs(maxCorrelation) > 0.8;

  return {
    name: 'CorrelationGuard',
    bias: isHighlyCorrelated ? (maxCorrelation > 0 ? 'BULL' : 'BEAR') : 'NEUTRAL',
    strengthScore: Math.abs(maxCorrelation) * 100,
    signals,
    notes: isHighlyCorrelated 
      ? `High correlation (${maxCorrelation.toFixed(2)}) detected with ${correlatedSymbol}. Risk exposure is overlapping.`
      : 'No high correlation detected with open positions.'
  };
}

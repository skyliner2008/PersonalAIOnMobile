import { indicators, parseCandles } from '../../tracking.js';
import type { AnalysisSignal, AnalyzerModuleResult, Bias } from '../types.js';
import { round1, round2 } from '../utils.js';

/**
 * Multi-Agent Debate Logic (Ported from tradingview-mcp-main)
 * This analyzer performs a synthetic debate between:
 * 1. Technical Analyst (Bollinger Bands + RSI)
 * 2. Sentiment Analyst (Momentum + MACD)
 * 3. Risk Manager (Volatility + Trend structure)
 */
export function multiAgentAnalyzer(candles: ReturnType<typeof parseCandles>): AnalyzerModuleResult {
  if (candles.length < 50) {
    return { name: 'MULTI_AGENT', bias: 'NEUTRAL', strengthScore: 0, signals: [], notes: 'insufficient candles' };
  }

  const stats = indicators(candles);
  const last = candles[candles.length - 1];

  // 1. Technical Analyst Views
  const techResult = runTechnicalAgent(candles, stats);

  // 2. Sentiment Analyst Views
  const sentimentResult = runSentimentAgent(candles, stats);

  // 3. Risk Manager Views
  const riskResult = runRiskAgent(candles, stats);

  // Final Consensus
  const totalScore = techResult.score + sentimentResult.score + riskResult.score;
  const signals: AnalysisSignal[] = [
    ...techResult.signals,
    ...sentimentResult.signals,
    ...riskResult.signals
  ];

  let bias: Bias = 'NEUTRAL';
  if (totalScore >= 3 && riskResult.level !== 'High') bias = 'BULL';
  else if (totalScore > 0) bias = 'BULL';
  else if (totalScore <= -3) bias = 'BEAR';
  else if (totalScore < 0) bias = 'BEAR';

  return {
    name: 'MULTI_AGENT',
    bias,
    strengthScore: Math.min(Math.abs(totalScore) * 20, 100),
    signals,
    notes: `Consensus: ${bias} | Tech: ${techResult.score} | Sent: ${sentimentResult.score} | Risk: ${riskResult.score} (${riskResult.level})`
  };
}

// --- Agent 1: Technical Analyst ---
function runTechnicalAgent(candles: any[], stats: any) {
  const last = candles[candles.length - 1];
  const window = candles.slice(-20).map(c => c.c);
  const mean = window.reduce((a, b) => a + b, 0) / window.length;
  const variance = window.reduce((a, b) => a + (b - mean) ** 2, 0) / window.length;
  const sd = Math.sqrt(variance);
  const bbUpper = mean + sd * 2;
  const bbLower = mean - sd * 2;
  const bbMiddle = mean;

  let score = 0;
  if (last.c > bbUpper) score = 3;
  else if (last.c > bbMiddle + ((bbUpper - bbMiddle) / 2)) score = 2;
  else if (last.c > bbMiddle) score = 1;
  else if (last.c < bbLower) score = -3;
  else if (last.c < bbMiddle - ((bbMiddle - bbLower) / 2)) score = -2;
  else if (last.c < bbMiddle) score = -1;

  return {
    score,
    signals: [{
      name: 'tech_agent_rating',
      bias: score > 0 ? 'BULL' as Bias : score < 0 ? 'BEAR' as Bias : 'NEUTRAL' as Bias,
      score: Math.abs(score) * 10,
      evidence: `Bollinger Rating: ${score} (Price=${last.c})`
    }]
  };
}

// --- Agent 2: Sentiment Analyst ---
function runSentimentAgent(candles: any[], stats: any) {
  const last = candles[candles.length - 1];
  const prev = candles[candles.length - 2];
  const change = ((last.c - prev.c) / prev.c) * 100;
  const rsi = stats.rsi14 ?? 50;
  const macd = stats.macdLine ?? 0;
  const signal = stats.macdSignal ?? 0;

  let score = 0;
  const signals: AnalysisSignal[] = [];

  if (change > 0) score += 1;
  else if (change < 0) score -= 1;

  if (rsi > 60) {
    score += 1;
    signals.push({ name: 'sentiment_rsi_bull', bias: 'BULL', score: 10, evidence: 'Bullish RSI (>60)' });
  } else if (rsi < 40) {
    score -= 1;
    signals.push({ name: 'sentiment_rsi_bear', bias: 'BEAR', score: 10, evidence: 'Bearish RSI (<40)' });
  }

  if (macd > signal) {
    score += 1;
    signals.push({ name: 'sentiment_macd_bull', bias: 'BULL', score: 10, evidence: 'MACD crossover BULL' });
  } else if (macd < signal) {
    score -= 1;
    signals.push({ name: 'sentiment_macd_bear', bias: 'BEAR', score: 10, evidence: 'MACD crossover BEAR' });
  }

  return { score, signals };
}

// --- Agent 3: Risk Manager ---
function runRiskAgent(candles: any[], stats: any) {
  const last = candles[candles.length - 1];
  const sma20 = stats.sma20 ?? last.c;
  const ema200 = stats.ema200 ?? last.c; // Note: indicators() might not return ema200, will fallback to SMA50 if available
  const sma50 = stats.sma50 ?? last.c;
  const targetEma = ema200 || sma50;

  const window = candles.slice(-20).map(c => c.c);
  const mean = window.reduce((a, b) => a + b, 0) / window.length;
  const variance = window.reduce((a, b) => a + (b - mean) ** 2, 0) / window.length;
  const sd = Math.sqrt(variance);
  const bbw = mean > 0 ? (sd * 4) / mean : 0;

  let score = 0;
  const warnings: string[] = [];

  if (bbw > 0.1) {
    score -= 2;
    warnings.push('High volatility (Wide BBW > 0.1)');
  } else if (bbw < 0.03) {
    score += 1;
    warnings.push('Low volatility (Squeeze)');
  }

  if (last.c < targetEma) {
    score -= 1;
    warnings.push(`Price below trend EMA (${round2(targetEma)})`);
  }

  const dist = Math.abs(last.c - sma20) / sma20;
  if (dist > 0.05) {
    score -= 1;
    warnings.push(`Extended from 20 SMA (>5%)`);
  }

  return {
    score,
    level: score < -1 ? 'High' : score === -1 ? 'Medium' : 'Low',
    signals: warnings.map(w => ({
      name: 'risk_warning',
      bias: 'BEAR' as Bias,
      score: 5,
      evidence: w
    }))
  };
}

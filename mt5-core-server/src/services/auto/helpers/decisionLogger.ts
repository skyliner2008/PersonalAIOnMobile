import { round1, round2 } from '../utils.js';

export function compactAnalysisForLog(analysis: any): Record<string, unknown> {
  if (!analysis) return {};
  const signals = Array.isArray(analysis.signals)
    ? analysis.signals.slice(0, 30).map((s: any) => ({
        name: s?.name,
        bias: s?.bias,
        score: s?.score,
        evidence: s?.evidence,
      }))
    : [];
  return {
    bias: analysis.bias,
    regime: analysis.regime,
    confluence: Number.isFinite(Number(analysis.confluence)) ? round1(Number(analysis.confluence)) : analysis.confluence,
    fitness: Number.isFinite(Number(analysis.fitness)) ? round1(Number(analysis.fitness)) : analysis.fitness,
    strategy: analysis.strategy,
    rationale: analysis.rationale,
    atr: analysis.atr ?? null,
    rsi: analysis.rsi ?? null,
    sma20: analysis.sma20 ?? null,
    sma50: analysis.sma50 ?? null,
    ema20: analysis.ema20 ?? null,
    signals,
  };
}

export function compactMtfForLog(analyses: Record<string, any>): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(analyses).map(([tf, analysis]) => [tf, compactAnalysisForLog(analysis)])
  );
}

export function compactDeterministicForLog(detDecision: any): Record<string, unknown> {
  return {
    action: detDecision?.action,
    confidence: detDecision?.confidence,
    strategy: detDecision?.strategy,
    management: detDecision?.management,
    sizeFraction: detDecision?.size_fraction,
    counterTrend: Boolean(detDecision?.counterTrend),
    zoneAtEntry: detDecision?.zoneAtEntry,
    stateHash: detDecision?.stateHash,
    timeframe: detDecision?.timeframe,
    sl: detDecision?.sl ?? null,
    tp: detDecision?.tp ?? null,
    rationale: detDecision?.rationale,
    rationaleTh: detDecision?.rationale_th,
  };
}

export function classifyDecisionBlockCategory(
  riskGate: string | null | undefined,
  side: string,
  executed: boolean,
): string | null {
  if (executed) return null;
  const gate = String(riskGate || '').toUpperCase();
  if (side !== 'SKIP' && gate === 'ALLOWED') return null;
  if (gate.includes('MARKET_CLOSED') || gate === 'MARKET_CLOSED') return 'MARKET_CLOSED';
  if (gate.includes('NO_DETERMINISTIC_SIGNAL')) return 'NO_DETERMINISTIC_SIGNAL';
  if (gate.includes('EA_FALLBACK_CONFIDENCE_LOW')) return 'EA_FALLBACK_CONFIDENCE_LOW';
  if (gate.includes('DECISION SIDE GUARD')) return 'DECISION_SIDE_GUARD';
  if (gate.includes('M15 SMC PRESSURE GUARD')) return 'M15_SMC_PRESSURE';
  if (gate.includes('ANTI-HEDGE') || gate.includes('ANTI HEDGE')) return 'ANTI_HEDGE';
  if (gate.includes('FITNESS') && gate.includes('BELOW MIN')) return 'FITNESS_GATE';
  if (gate.includes('ENTRY_DRIFT') || gate.includes('SLIPPAGE')) return 'ENTRY_DRIFT';
  if (gate.includes('RSI') && (gate.includes('OVERBOUGHT') || gate.includes('OVERSOLD'))) return 'RSI_EXTREME';
  if (gate.includes('EA_FALLBACK_RRR') || gate.includes('RRR')) return 'RRR_OR_REWARD_RISK';
  if (gate.includes('V25_ONLY') || gate.includes('V25')) return 'V25_ONLY_GATE';
  if (gate.includes('NEAR_DUPLICATE') || gate.includes('SEQUENTIAL')) return 'SEQUENTIAL_DUPLICATE';
  if (gate.includes('POST_TP_COOLDOWN')) return 'SEQUENTIAL_DUPLICATE';
  if (gate.includes('LTF_CONSENSUS_GUARD')) return 'MTF_CONFLICT';
  if (gate.includes('MTF ZONE') || gate.includes('ZONE-AWARE')) return 'ZONE_GATE';
  if (gate.includes('PROXIMITY')) return 'PROXIMITY_GATE';
  if (gate.includes('FVG')) return 'FVG_ALIGNMENT';
  if (gate.includes('RISK')) return 'RISK_GATE';
  if (gate.includes('ORDER ERROR') || gate.includes('ORDER REQUEST FAILED')) return 'ORDER_SEND_FAILED';
  return side === 'SKIP' ? 'SKIP_OTHER' : null;
}

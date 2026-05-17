export type TradeSide = 'BUY' | 'SELL' | 'SKIP';

export interface DecisionSideResolution {
  side: TradeSide;
  applied: boolean;
  reason: string;
}

export function resolveDecisionSide(input: {
  technicalSide: TradeSide;
  proposedAction: unknown;
  confidence?: number;
  minOverrideConfidence?: number;
}): DecisionSideResolution {
  const action = input.proposedAction === 'BUY' || input.proposedAction === 'SELL'
    ? input.proposedAction
    : 'SKIP';
  const confidence = Number.isFinite(input.confidence)
    ? Number(input.confidence)
    : 0;
  const minOverrideConfidence = input.minOverrideConfidence ?? 75;

  if (action === 'SKIP') {
    return {
      side: 'SKIP',
      applied: true,
      reason: 'non-trade action',
    };
  }

  if (input.technicalSide === action) {
    return {
      side: action,
      applied: true,
      reason: `confirmed technical ${input.technicalSide}`,
    };
  }

  if (confidence > minOverrideConfidence) {
    return {
      side: action,
      applied: true,
      reason: `high-confidence override ${confidence.toFixed(1)}% > ${minOverrideConfidence}%`,
    };
  }

  return {
    side: 'SKIP',
    applied: false,
    reason: `${action} conflicts with technical ${input.technicalSide} at ${confidence.toFixed(1)}% <= ${minOverrideConfidence}%`,
  };
}

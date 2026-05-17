import { describe, expect, it } from 'vitest';
import { resolveDecisionSide } from './DecisionGuards.js';

describe('resolveDecisionSide', () => {
  it('blocks low-confidence opposite-side overrides', () => {
    const result = resolveDecisionSide({
      technicalSide: 'BUY',
      proposedAction: 'SELL',
      confidence: 41.7,
      minOverrideConfidence: 75,
    });

    expect(result.side).toBe('SKIP');
    expect(result.applied).toBe(false);
    expect(result.reason).toContain('conflicts');
  });

  it('allows high-confidence opposite-side overrides', () => {
    const result = resolveDecisionSide({
      technicalSide: 'BUY',
      proposedAction: 'SELL',
      confidence: 80,
      minOverrideConfidence: 75,
    });

    expect(result.side).toBe('SELL');
    expect(result.applied).toBe(true);
  });

  it('allows low-confidence decisions that confirm the technical side', () => {
    const result = resolveDecisionSide({
      technicalSide: 'BUY',
      proposedAction: 'BUY',
      confidence: 35,
      minOverrideConfidence: 75,
    });

    expect(result.side).toBe('BUY');
    expect(result.applied).toBe(true);
  });
});

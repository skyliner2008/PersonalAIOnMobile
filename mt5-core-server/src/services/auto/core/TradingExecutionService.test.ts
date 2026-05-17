import { describe, expect, it } from 'vitest';
import { tradingExecutionService } from './TradingExecutionService.js';
import type { AutoTradingConfig, PositionRow } from '../types.js';

const baseConfig = {
  adaptive: {
    preventNearDuplicateEntries: true,
    sequentialMinEntryDistanceXAU: 5,
    sequentialMinEntryDistanceAtrMul: 0,
  },
} as AutoTradingConfig;

const openSell: PositionRow = {
  ticket: 1001,
  symbol: 'XAUUSD',
  side: 'SELL',
  volume: 0.05,
  priceOpen: 4692,
  priceCurrent: 4689,
  sl: 4698,
  tp: 4670,
  profit: 0,
};

describe('TradingExecutionService nearDuplicateEntryIssue', () => {
  it('blocks normal XAU entries inside the default 5.0 spacing', () => {
    const issue = tradingExecutionService.nearDuplicateEntryIssue(
      'XAUUSD',
      'SELL',
      4689,
      [openSell],
      baseConfig,
      { atr: 20, comment: 'normal-entry' },
    );

    expect(issue).toContain('NEAR_DUPLICATE_ENTRY');
  });

  it('allows scale-in callers to use a smaller explicit spacing policy', () => {
    const issue = tradingExecutionService.nearDuplicateEntryIssue(
      'XAUUSD',
      'SELL',
      4689,
      [openSell],
      baseConfig,
      {
        atr: 20,
        baseMinDistance: 2,
        atrMultiplier: 0,
        comment: 'scale-in-entry',
      },
    );

    expect(issue).toBeNull();
  });

  it('still blocks scale-ins that are inside the explicit scale-in spacing', () => {
    const issue = tradingExecutionService.nearDuplicateEntryIssue(
      'XAUUSD',
      'SELL',
      4691,
      [openSell],
      baseConfig,
      {
        atr: 20,
        baseMinDistance: 2,
        atrMultiplier: 0,
        comment: 'scale-in-entry',
      },
    );

    expect(issue).toContain('NEAR_DUPLICATE_ENTRY');
  });
});

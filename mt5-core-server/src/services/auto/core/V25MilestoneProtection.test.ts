import { describe, expect, it } from 'vitest';
import type { JournalRow } from '../types.js';
import { extractV25PathMilestone, v25MilestoneReached } from './V25MilestoneProtection.js';
import type { Tick } from '../v25/types.js';

function journal(overrides: Partial<JournalRow> = {}): JournalRow {
  return {
    id: 1,
    decisionId: 'd1',
    symbol: 'XAUUSD',
    timeframe: 'M1',
    side: 'BUY',
    strategy: 'V25_PB1',
    analyzersUsed: 'V25_Engine',
    signalsJson: '',
    confluenceScore: 80,
    entry: 4721.72,
    sl: 4718.06,
    tp: 4728.5,
    volume: 0.03,
    riskPct: 0.3,
    rrr: 1.85,
    regime: 'TRENDING',
    marketSnapshot: null,
    wasExecuted: true,
    mt5Ticket: 153232513,
    closeReason: null,
    closePrice: null,
    closeAt: null,
    profit: null,
    profitR: null,
    outcome: 'OPEN',
    aiReview: null,
    createdAt: 1000,
    updatedAt: 1000,
    ...overrides,
  };
}

describe('V25 milestone protection helpers', () => {
  it('extracts a path milestone from the V25 market snapshot', () => {
    const row = journal({
      marketSnapshot: JSON.stringify({
        v25: true,
        pathMilestone: {
          price: 4723.33,
          edge: 4723.33,
          protectAt: 4723.33,
          stars: 5,
          timeframes: ['M5', 'M15', 'H1'],
        },
      }),
    });

    expect(extractV25PathMilestone(row)).toMatchObject({
      price: 4723.33,
      protectAt: 4723.33,
      stars: 5,
      timeframes: ['M5', 'M15', 'H1'],
    });
  });

  it('detects BUY milestone touches from buffered bid ticks after entry', () => {
    const ticks: Tick[] = [
      { symbol: 'XAUUSD', srcTs: 900, recvTs: 900, bid: 4723.5, ask: 4723.66, mid: 4723.58, spread: 0.16 },
      { symbol: 'XAUUSD', srcTs: 1200, recvTs: 1200, bid: 4723.34, ask: 4723.5, mid: 4723.42, spread: 0.16 },
    ];

    expect(v25MilestoneReached('BUY', { price: 4723.33, protectAt: 4723.33 }, 4721.9, ticks, 1000)).toBe(true);
  });

  it('ignores ticks that crossed before the trade opened', () => {
    const ticks: Tick[] = [
      { symbol: 'XAUUSD', srcTs: 900, recvTs: 900, bid: 4723.5, ask: 4723.66, mid: 4723.58, spread: 0.16 },
    ];

    expect(v25MilestoneReached('BUY', { price: 4723.33, protectAt: 4723.33 }, 4721.9, ticks, 1000)).toBe(false);
  });
});

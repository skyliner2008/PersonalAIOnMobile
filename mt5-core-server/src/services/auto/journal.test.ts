import { describe, expect, it } from 'vitest';
import type { JournalRow } from './types.js';
import { detectCloseReason, extractDealProfit, matchHistoryDeal } from './journal.js';

function journal(overrides: Partial<JournalRow> = {}): JournalRow {
  return {
    id: 1,
    decisionId: 'v25-153232513',
    symbol: 'XAUUSD',
    timeframe: 'M1',
    side: 'BUY',
    strategy: 'V25_PB1',
    analyzersUsed: '',
    signalsJson: '{}',
    confluenceScore: 90,
    entry: 4720.41,
    sl: 4718.06,
    tp: 4728.5,
    volume: 0.03,
    riskPct: 0.01,
    rrr: 3.44,
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
    modelId: null,
    createdAt: 1,
    updatedAt: 1,
    ...overrides,
  };
}

describe('journal history matching', () => {
  it('does not treat a zero-profit entry deal as a closed trade', () => {
    const row = journal();
    const deal = matchHistoryDeal(row, [
      {
        ticket: 109525133,
        order: 153232513,
        position_id: 153232513,
        symbol: 'XAUUSD',
        entry: 0,
        reason: 3,
        type: 'BUY',
        price: 4721.72,
        profit: 0,
      },
    ]);

    expect(deal).toBeUndefined();
  });

  it('matches a closing deal by position field and returns net profit', () => {
    const row = journal();
    const deal = matchHistoryDeal(row, [
      {
        ticket: 153232513,
        order: 153232513,
        symbol: 'XAUUSD',
        entry: 'IN',
        type: 'BUY',
        price: 4721.72,
        profit: 0,
      },
      {
        ticket: 9001,
        position: 153232513,
        symbol: 'XAUUSD',
        entry: 'OUT',
        type: 'SELL',
        price: 4728.5,
        profit: 20.5,
        commission: -0.16,
      },
    ]);

    expect(deal?.ticket).toBe(9001);
    expect(extractDealProfit(deal)).toBe(20.34);
  });

  it('still accepts a true zero-profit closing deal when MT5 marks it as OUT', () => {
    const row = journal();
    const deal = matchHistoryDeal(row, [
      {
        ticket: 9002,
        position_id: 153232513,
        symbol: 'XAUUSD',
        entry: 'OUT',
        type: 'SELL',
        price: 4721.72,
        profit: 0,
      },
    ]);

    expect(deal?.ticket).toBe(9002);
  });
});

describe('close reason detection', () => {
  it('classifies MT5 numeric SL reason as SL even when entry marker is present', () => {
    expect(detectCloseReason({
      reason: 4,
      entry: 1,
      comment: '[sl 4692.69]',
      price: 4692.69,
      profit: -25.5,
    })).toBe('SL');
  });

  it('classifies MT5 numeric TP reason as TP', () => {
    expect(detectCloseReason({
      reason: 5,
      entry: 1,
      comment: '[tp 4728.50]',
      price: 4728.5,
      profit: 20.5,
    })).toBe('TP');
  });

  it('keeps explicit break-even labels as BE', () => {
    expect(detectCloseReason({
      reason: 4,
      entry: 'OUT',
      comment: 'sl_to_be break_even protected exit',
      price: 4720.41,
      profit: 0,
    })).toBe('BE');
  });
});

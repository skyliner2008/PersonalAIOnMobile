import { describe, expect, it } from 'vitest';
import type { JournalRow } from './types.js';
import {
  detectCloseReason,
  extractDealTimeMs,
  extractDealProfit,
  extractHistoryOpenPrice,
  matchHistoryDeal,
  profitToR,
  resolveDealCloseAtMs,
} from './journal.js';

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

  it('extracts broker open price from the entry deal so loss R is not biased by planned entry slippage', () => {
    const row = journal({
      mt5Ticket: 164475366,
      entry: 4528.05,
      sl: 4524.74,
      side: 'BUY',
    });
    const history = [
      {
        ticket: 119474736,
        order: 164475366,
        position_id: 164475366,
        symbol: 'XAUUSD',
        entry: 0,
        price: 4528.37,
        profit: 0,
        time_msc: 1780289740376,
      },
      {
        ticket: 119475809,
        order: 164476497,
        position_id: 164475366,
        symbol: 'XAUUSD',
        entry: 1,
        reason: 4,
        price: 4524.74,
        profit: -18.15,
        time_msc: 1780289825781,
      },
    ];

    const openPrice = extractHistoryOpenPrice(row, history);
    const closeDeal = matchHistoryDeal(row, history);

    expect(openPrice).toBe(4528.37);
    expect(extractDealTimeMs(closeDeal)).toBe(1780289825781);
    expect(profitToR(row.entry, row.sl, 4524.74, row.side, openPrice)).toBe(-1);
  });

  it('does not persist broker-local history timestamps that are ahead of server time', () => {
    const now = 1780279000000;

    expect(resolveDealCloseAtMs({ time_msc: now + 3 * 60 * 60_000 }, now)).toBe(now);
    expect(resolveDealCloseAtMs({ time_msc: now - 30_000 }, now)).toBe(now - 30_000);
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

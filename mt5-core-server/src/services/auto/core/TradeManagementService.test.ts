import { describe, expect, it } from 'vitest';

import { tradeManagementService } from './TradeManagementService.js';

const baseCfg: any = {
  risk: {
    minLot: 0.01,
    minLotStep: 0.01,
    maxOpenPositions: 5,
    defaultMaxPositionsPerSymbol: 5,
  },
  adaptive: {
    adverseConfluence: 78,
    enableEarlyBreakeven: true,
    earlyBreakevenR: 0.4,
    enableEarlyInvalidation: true,
    earlyInvalidationR: -0.45,
    earlyInvalidationCloseR: -0.65,
    earlyInvalidationMinAgeMs: 90_000,
  },
};

const account: any = {
  balance: 10_000,
  equity: 10_000,
  margin: 0,
  freeMargin: 10_000,
  todayPnL: 0,
  openPositions: 1,
  currency: 'USD',
};

function playbook(worstLossR: number): any {
  return {
    dimensions: {
      trendStrength: 0.2,
      floatingLossPressure: worstLossR < 0 ? Math.abs(worstLossR) : 0,
      exposureImbalance: 0,
      recoveryProbability: 0,
      marginHeadroom: 1,
    },
    actions: {
      HOLD: 0.8,
      TRAIL: 0,
      BREAKEVEN: 0,
      REDUCE: 0,
      CLOSE: 0,
      HEDGE: 0,
      SCALE_IN: 0,
      PARTIAL_CLOSE: 0,
    },
    recommendedMode: 'HOLD',
    summary: 'test playbook',
    worstLossR,
    scaleInSteps: 0,
  };
}

function journal(overrides: Partial<any>): any {
  return {
    id: 1,
    decisionId: 'D-1',
    symbol: 'XAUUSD',
    timeframe: 'M15',
    side: 'BUY',
    strategy: 'TREND_FOLLOW',
    analyzersUsed: '',
    signalsJson: '[]',
    confluenceScore: 60,
    entry: 4537.72,
    sl: 4533.05,
    tp: 4548.85,
    volume: 0.03,
    riskPct: 1,
    rrr: 2.3,
    regime: 'RANGING',
    marketSnapshot: null,
    wasExecuted: true,
    mt5Ticket: 159481697,
    closeReason: null,
    closePrice: null,
    closeAt: null,
    profit: null,
    profitR: null,
    outcome: 'OPEN',
    aiReview: null,
    createdAt: Date.now() - 120_000,
    updatedAt: Date.now() - 120_000,
    ...overrides,
  };
}

function cluster(position: any): any {
  return {
    symbol: 'XAUUSD',
    positions: [position],
    winningPositions: position.profit > 0 ? [position] : [],
    losingPositions: position.profit < 0 ? [position] : [],
    buyVolume: position.side === 'BUY' ? position.volume : 0,
    sellVolume: position.side === 'SELL' ? position.volume : 0,
    totalVolume: position.volume,
    netVolume: position.side === 'BUY' ? position.volume : -position.volume,
    totalProfit: position.profit,
    avgPrice: position.priceOpen,
    netSide: position.side,
    bias: position.side === 'BUY' ? 'BULL' : 'BEAR',
  };
}

describe('TradeManagementService', () => {
  it('moves XAU positions to breakeven at the early 0.4R trigger', () => {
    const entry = 4537.72;
    const sl = 4533.05;
    const risk = entry - sl;
    const position: any = {
      ticket: 159481697,
      symbol: 'XAUUSD',
      side: 'BUY',
      volume: 0.03,
      priceOpen: entry,
      priceCurrent: entry + risk * 0.45,
      sl,
      tp: 4548.85,
      profit: 6,
      openedAtMs: Date.now() - 180_000,
    };

    const plan = tradeManagementService.planMarketAwareManagement(
      baseCfg,
      account,
      cluster(position),
      { bias: 'BULL', regime: 'RANGING', confluence: 60, fitness: 60, strategy: 'TREND_FOLLOW', rationale: '', signals: [] } as any,
      { action: 'SKIP', management: 'HOLD', confidence: 0 },
      0.03,
      playbook(0.45),
      1,
      [journal({ mt5Ticket: position.ticket, entry, sl })],
    );

    expect(plan.mode).toBe('BREAKEVEN');
    expect(plan.ticket).toBe(position.ticket);
  });

  it('closes stale V25 wall-touch positions when proof fails before full SL', () => {
    const entry = 4532.64;
    const sl = 4528.09;
    const risk = entry - sl;
    const position: any = {
      ticket: 159491458,
      symbol: 'XAUUSD',
      side: 'BUY',
      volume: 0.03,
      priceOpen: entry,
      priceCurrent: entry - risk * 0.7,
      sl,
      tp: 4537.25,
      profit: -10,
      openedAtMs: Date.now() - 180_000,
    };

    const plan = tradeManagementService.planMarketAwareManagement(
      baseCfg,
      account,
      cluster(position),
      { bias: 'BULL', regime: 'RANGING', confluence: 55, fitness: 55, strategy: 'V25_PB1_TREND_TOUCH', rationale: '', signals: [] } as any,
      { action: 'SKIP', management: 'HOLD', confidence: 0 },
      0.03,
      playbook(-0.7),
      1,
      [journal({ mt5Ticket: position.ticket, entry, sl, strategy: 'V25_PB1_TREND_TOUCH' })],
    );

    expect(plan.mode).toBe('CLOSE');
    expect(plan.ticket).toBe(position.ticket);
  });

  it('does not repeat the 1R partial close once the ticket is marked', () => {
    const entry = 4537.72;
    const sl = 4533.05;
    const risk = entry - sl;
    const position: any = {
      ticket: 159982915,
      symbol: 'XAUUSD',
      side: 'SELL',
      volume: 0.03,
      priceOpen: entry,
      priceCurrent: entry - risk * 1.15,
      sl,
      tp: 4527,
      profit: 12,
      openedAtMs: Date.now() - 180_000,
    };

    const plan = tradeManagementService.planMarketAwareManagement(
      baseCfg,
      account,
      cluster(position),
      { bias: 'BEAR', regime: 'RANGING', confluence: 60, fitness: 60, strategy: 'MEAN_REVERSION', rationale: '', signals: [] } as any,
      { action: 'SKIP', management: 'HOLD', confidence: 0 },
      0.03,
      playbook(1.15),
      1,
      [journal({ mt5Ticket: position.ticket, side: 'SELL', entry, sl, strategy: 'MEAN_REVERSION', aiReview: 'PARTIAL_1R' })],
    );

    expect(plan.mode).not.toBe('PARTIAL_CLOSE');
  });
});

import { describe, expect, it } from 'vitest';
import { deterministicDecide } from './deterministicEngine.js';
import type { AnalysisSummary, PositionCluster, StrategyType } from './types.js';

const analysis = (overrides: Partial<AnalysisSummary>): AnalysisSummary => ({
  bias: 'BULL',
  regime: 'RANGING',
  confluence: 60,
  fitness: 65,
  strategy: 'RANGE',
  rationale: 'test',
  signals: [],
  ...overrides,
});

const emptyCluster: PositionCluster = {
  symbol: 'XBTUSD',
  positions: [],
  winningPositions: [],
  losingPositions: [],
  buyVolume: 0,
  sellVolume: 0,
  totalVolume: 0,
  netVolume: 0,
  totalProfit: 0,
  avgPrice: 0,
  netSide: 'FLAT',
  bias: 'NEUTRAL',
};

describe('deterministicDecide strategy routing', () => {
  it('promotes a valid LTF SCALPING setup to the executable strategy', () => {
    const primary = analysis({ strategy: 'RANGE', regime: 'RANGING', bias: 'BULL', confluence: 63, fitness: 64 });
    const decision = deterministicDecide({
      symbol: 'XBTUSD',
      lastPrice: 100,
      primary,
      analyses: {
        H4: analysis({ bias: 'BULL', regime: 'RANGING', confluence: 45 }),
        H1: primary,
        M15: analysis({ bias: 'BULL', regime: 'RANGING', confluence: 58, fitness: 63, strategy: 'RANGE' }),
        M5: analysis({ bias: 'BULL', regime: 'RANGING', confluence: 66, fitness: 75, strategy: 'SCALPING' as StrategyType }),
      },
      cluster: emptyCluster,
      positions: [],
      totalHeatR: 0,
      floatingProfit: 0,
      enableZoneAwareGate: false,
    });

    expect(decision.strategy).toBe('SCALPING');
    expect(decision.timeframe).toBe('M5');
    expect(decision.action).toBe('BUY');
  });

  it('keeps ranging fallback generic when no LTF SCALPING/FVG setup exists', () => {
    const primary = analysis({ strategy: 'RANGE', regime: 'RANGING', bias: 'BULL', confluence: 63, fitness: 64 });
    const decision = deterministicDecide({
      symbol: 'XBTUSD',
      lastPrice: 100,
      primary,
      analyses: {
        H4: analysis({ bias: 'BULL', regime: 'RANGING', confluence: 45 }),
        H1: primary,
        M15: analysis({ bias: 'BULL', regime: 'RANGING', confluence: 58, fitness: 63, strategy: 'RANGE' }),
        M5: analysis({ bias: 'BULL', regime: 'RANGING', confluence: 54, fitness: 59, strategy: 'RANGE' }),
      },
      cluster: emptyCluster,
      positions: [],
      totalHeatR: 0,
      floatingProfit: 0,
      enableZoneAwareGate: false,
    });

    expect(decision.strategy).toBe('MEAN_REVERSION');
    expect(decision.timeframe).toBe('M5');
  });

  it('waits on HTF breakout SELL when local M15 and M5 oppose the breakout', () => {
    const primary = analysis({ bias: 'BEAR', regime: 'VOLATILE_BREAKOUT', confluence: 73, fitness: 88, strategy: 'BREAKOUT' as StrategyType });
    const decision = deterministicDecide({
      symbol: 'XAUUSD',
      lastPrice: 4507,
      primary,
      analyses: {
        H4: analysis({ bias: 'BEAR', regime: 'VOLATILE_BREAKOUT', confluence: 74, fitness: 89, strategy: 'BREAKOUT' as StrategyType }),
        H1: primary,
        M30: analysis({ bias: 'BEAR', regime: 'VOLATILE_BREAKOUT', confluence: 70, fitness: 80, strategy: 'BREAKOUT' as StrategyType }),
        M15: analysis({ bias: 'BULL', regime: 'RANGING', confluence: 62, fitness: 63, strategy: 'MEAN_REVERSION' as StrategyType }),
        M5: analysis({ bias: 'BULL', regime: 'TRENDING_UP', confluence: 63, fitness: 83, strategy: 'TREND_FOLLOW' as StrategyType }),
        M1: analysis({ bias: 'BULL', regime: 'TRENDING_UP', confluence: 68, fitness: 86, strategy: 'TREND_FOLLOW' as StrategyType }),
      },
      cluster: emptyCluster,
      positions: [],
      totalHeatR: 0,
      floatingProfit: 0,
      h4Candles: Array.from({ length: 30 }, (_, i) => ({ h: 4700, l: 4500, c: i === 29 ? 4507 : 4600 })),
      enableZoneAwareGate: true,
    });

    expect(decision.strategy).toBe('BREAKOUT');
    expect(decision.action).toBe('SKIP');
    expect(decision.confidence).toBeLessThanOrEqual(35);
    expect(decision.rationale).toContain('Breakout wait');
  });

  it('waits on MEAN_REVERSION when local M15 and M5 oppose the HTF-selected side', () => {
    const primary = analysis({ bias: 'BEAR', regime: 'RANGING', confluence: 70, fitness: 76, strategy: 'MEAN_REVERSION' as StrategyType });
    const decision = deterministicDecide({
      symbol: 'XAUUSD',
      lastPrice: 4507,
      primary,
      analyses: {
        H4: analysis({ bias: 'BEAR', regime: 'RANGING', confluence: 72, fitness: 78, strategy: 'MEAN_REVERSION' as StrategyType }),
        H1: primary,
        M30: analysis({ bias: 'BEAR', regime: 'RANGING', confluence: 70, fitness: 76, strategy: 'MEAN_REVERSION' as StrategyType }),
        M15: analysis({ bias: 'BULL', regime: 'RANGING', confluence: 62, fitness: 68, strategy: 'MEAN_REVERSION' as StrategyType }),
        M5: analysis({ bias: 'BULL', regime: 'TRENDING_UP', confluence: 63, fitness: 83, strategy: 'TREND_FOLLOW' as StrategyType }),
        M1: analysis({ bias: 'BULL', regime: 'TRENDING_UP', confluence: 67, fitness: 84, strategy: 'TREND_FOLLOW' as StrategyType }),
      },
      cluster: emptyCluster,
      positions: [],
      totalHeatR: 0,
      floatingProfit: 0,
      enableZoneAwareGate: false,
    });

    expect(decision.strategy).toBe('MEAN_REVERSION');
    expect(decision.action).toBe('SKIP');
    expect(decision.confidence).toBeLessThanOrEqual(35);
    expect(decision.rationale).toContain('Execution wait');
  });

  it('allows breakout SELL when M15 and M5 confirm the HTF direction', () => {
    const primary = analysis({ bias: 'BEAR', regime: 'VOLATILE_BREAKOUT', confluence: 73, fitness: 88, strategy: 'BREAKOUT' as StrategyType });
    const decision = deterministicDecide({
      symbol: 'XAUUSD',
      lastPrice: 4520,
      primary,
      analyses: {
        H4: analysis({ bias: 'BEAR', regime: 'VOLATILE_BREAKOUT', confluence: 74, fitness: 89, strategy: 'BREAKOUT' as StrategyType }),
        H1: primary,
        M15: analysis({ bias: 'BEAR', regime: 'TRENDING_DOWN', confluence: 62, fitness: 70, strategy: 'TREND_FOLLOW' as StrategyType }),
        M5: analysis({ bias: 'BEAR', regime: 'TRENDING_DOWN', confluence: 63, fitness: 75, strategy: 'TREND_FOLLOW' as StrategyType }),
      },
      cluster: emptyCluster,
      positions: [],
      totalHeatR: 0,
      floatingProfit: 0,
      enableZoneAwareGate: false,
    });

    expect(decision.strategy).toBe('BREAKOUT');
    expect(decision.action).toBe('SELL');
  });
});

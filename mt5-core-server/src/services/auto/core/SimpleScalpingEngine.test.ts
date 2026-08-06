import { describe, expect, it } from 'vitest';
import type { AutoTradingConfig } from '../types.js';
import type { PriceMap, PriceWall } from '../analyzers/smc/types.js';
import { buildM15WallScalpPlan, SimpleScalpingEngine } from './SimpleScalpingEngine.js';

function testConfig(overrides: Record<string, unknown> = {}): AutoTradingConfig {
  return {
    minRRR: 1.15,
    strategy: { scalpingRRR: 1.15 },
    adaptive: {
      simpleScalping: {
        minWallStars: 2,
        targetWallMinStars: 2,
        entryProximityAtrMul: 0.22,
        slBufferAtrMul: 0.08,
        tpBufferAtrMul: 0.06,
        minRrr: 1.15,
        maxRiskAtrMul: 0.45,
        minRewardAtrMul: 0.12,
        maxPositionsPerSymbol: 1,
        ...overrides,
      },
    },
  } as AutoTradingConfig;
}

function wall(side: PriceWall['side'], price: number, stars = 3): PriceWall {
  const halfWidth = 0.3;
  return {
    price,
    priceTop: price + halfWidth,
    priceBottom: price - halfWidth,
    side,
    confluenceStars: stars,
    timeframes: ['M5', 'M15', 'H1'],
    sources: [{
      type: side === 'SUPPORT' ? 'OB_BULL' : 'OB_BEAR',
      timeframe: 'M15',
      priceTop: price + halfWidth,
      priceBottom: price - halfWidth,
    }],
    hasOB: true,
    hasFVG: false,
    hasLiquidity: true,
    isStructure: false,
    label: `${side}-${price}`,
    distanceFromPrice: 0,
    distancePct: 0,
  };
}

function priceMap(currentPrice: number, supports: PriceWall[], resistances: PriceWall[], atr = 2): PriceMap {
  return {
    symbol: 'XAUUSD',
    currentPrice,
    atr,
    walls: [...supports, ...resistances],
    supportWalls: supports,
    resistanceWalls: resistances,
    nearestSupport: supports[0] ?? null,
    nearestResistance: resistances[0] ?? null,
    strongestSupport: supports[0] ?? null,
    strongestResistance: resistances[0] ?? null,
    sourceTimeframes: ['M1', 'M5', 'M15', 'M30', 'H1'],
    timestamp: Date.now(),
  };
}

function confirmCandles(side: 'BUY' | 'SELL') {
  if (side === 'BUY') {
    return [
      { o: 100.2, h: 100.3, l: 99.86, c: 100.05 },
      { o: 100.05, h: 100.12, l: 99.84, c: 99.95 },
      { o: 99.95, h: 100.18, l: 99.82, c: 100.08 },
      { o: 100.08, h: 100.2, l: 99.9, c: 100.02 },
      { o: 100.02, h: 100.24, l: 99.88, c: 99.96 },
      { o: 99.96, h: 100.55, l: 99.89, c: 100.5 },
    ];
  }
  return [
    { o: 99.8, h: 100.14, l: 99.7, c: 99.95 },
    { o: 99.95, h: 100.18, l: 99.76, c: 100.05 },
    { o: 100.05, h: 100.22, l: 99.74, c: 99.98 },
    { o: 99.98, h: 100.16, l: 99.72, c: 100.02 },
    { o: 100.02, h: 100.24, l: 99.73, c: 100.08 },
    { o: 100.08, h: 100.21, l: 99.35, c: 99.4 },
  ];
}

function reclaimWithoutBreakCandles(side: 'BUY' | 'SELL') {
  if (side === 'BUY') {
    return [
      { o: 100.02, h: 100.3, l: 99.86, c: 100.1 },
      { o: 100.1, h: 100.28, l: 99.84, c: 99.96 },
      { o: 99.96, h: 100.2, l: 99.82, c: 100.06 },
      { o: 100.06, h: 100.18, l: 99.88, c: 100.0 },
      { o: 100.0, h: 100.16, l: 99.86, c: 99.95 },
      { o: 99.95, h: 100.14, l: 99.88, c: 100.05 },
    ];
  }
  return [
    { o: 99.98, h: 100.14, l: 99.7, c: 99.9 },
    { o: 99.9, h: 100.18, l: 99.76, c: 100.04 },
    { o: 100.04, h: 100.22, l: 99.74, c: 99.98 },
    { o: 99.98, h: 100.16, l: 99.72, c: 100.02 },
    { o: 100.02, h: 100.24, l: 99.73, c: 100.08 },
    { o: 100.08, h: 100.18, l: 99.82, c: 99.95 },
  ];
}

function delayedReclaimBreakCandles(side: 'BUY' | 'SELL') {
  if (side === 'BUY') {
    return [
      { o: 100.12, h: 100.25, l: 99.86, c: 100.05 },
      { o: 100.05, h: 100.2, l: 99.9, c: 100.15 },
      { o: 100.15, h: 100.28, l: 100.08, c: 100.22 },
      { o: 100.22, h: 100.32, l: 100.12, c: 100.3 },
      { o: 100.3, h: 100.35, l: 100.22, c: 100.32 },
      { o: 100.32, h: 100.72, l: 100.28, c: 100.65 },
    ];
  }
  return [
    { o: 99.88, h: 100.14, l: 99.75, c: 99.98 },
    { o: 99.98, h: 100.18, l: 99.82, c: 99.9 },
    { o: 99.9, h: 99.94, l: 99.72, c: 99.82 },
    { o: 99.82, h: 99.88, l: 99.68, c: 99.76 },
    { o: 99.76, h: 99.82, l: 99.65, c: 99.72 },
    { o: 99.72, h: 99.78, l: 99.28, c: 99.35 },
  ];
}

describe('SimpleScalpingEngine M15 wall planner', () => {
  it('lets explicit legacy engine mode override the simple toggle', () => {
    const cfg = testConfig({ enabled: true });
    cfg.engineMode = 'LEGACY_COMPLEX';

    expect(SimpleScalpingEngine.isEnabled(cfg)).toBe(false);
  });

  it('builds a BUY scalp when price is close to a support wall with a valid resistance target', () => {
    const map = priceMap(100, [wall('SUPPORT', 99.9, 3)], [wall('RESISTANCE', 101.5, 2)]);

    const result = buildM15WallScalpPlan(map, testConfig());

    expect(result.plan).toMatchObject({
      side: 'BUY',
      entry: 100,
    });
    expect(result.plan?.sl).toBeLessThan(100);
    expect(result.plan?.tp).toBeGreaterThan(100);
    expect(result.plan?.rrr).toBeGreaterThanOrEqual(1.15);
    expect(result.blockCategory).toBeNull();
  });

  it('blocks a BUY scalp when M15 context is bearish and trending down', () => {
    const map = priceMap(100, [wall('SUPPORT', 99.9, 3)], [wall('RESISTANCE', 101.5, 2)]);

    const result = buildM15WallScalpPlan(map, testConfig(), { regime: 'TRENDING_DOWN', bias: 'BEAR' });

    expect(result.plan).toBeNull();
    expect(result.blockCategory).toBe('SCALP_CONTEXT_GUARD');
    expect(result.reason).toContain('context guard');
  });

  it('blocks a SELL scalp from an ambiguous BOTH wall instead of treating it as clean resistance', () => {
    const map = priceMap(100, [wall('SUPPORT', 95, 3)], [wall('BOTH', 100.1, 5)]);

    const result = buildM15WallScalpPlan(map, testConfig());

    expect(result.plan).toBeNull();
    expect(result.blockCategory).toBe('WALL_SIDE_MISMATCH');
    expect(result.reason).toContain('wall side mismatch BOTH');
  });

  it('skips when price is in the middle of the channel and not close to any wall', () => {
    const map = priceMap(100, [wall('SUPPORT', 98, 3)], [wall('RESISTANCE', 102, 3)]);

    const result = buildM15WallScalpPlan(map, testConfig());

    expect(result.plan).toBeNull();
    expect(result.blockCategory).toBe('WALL_DISTANCE_TOO_FAR');
    expect(result.reason).toContain('price too far from wall');
  });

  it('blocks a near-wall setup when the next opposite wall leaves too little reward', () => {
    const map = priceMap(100, [wall('SUPPORT', 99.9, 3)], [wall('RESISTANCE', 100.5, 2)]);

    const result = buildM15WallScalpPlan(map, testConfig());

    expect(result.plan).toBeNull();
    expect(result.blockCategory).toBe('SCALP_PLAN_INVALID');
  });

  it('requires M1 and M5 wall confirmation when live candle context is available', () => {
    const map = priceMap(100, [wall('SUPPORT', 99.9, 3)], [wall('RESISTANCE', 101.5, 2)]);
    const flat = Array.from({ length: 6 }, () => ({ o: 100.5, h: 100.6, l: 100.4, c: 100.5 }));

    const result = buildM15WallScalpPlan(map, testConfig(), {
      analysis: { regime: 'RANGING', bias: 'NEUTRAL' },
      candlesByTf: new Map([['M1', flat], ['M5', flat]]),
    });

    expect(result.plan).toBeNull();
    expect(result.blockCategory).toBe('LTF_CONFIRMATION_MISSING');
    expect(result.reason).toContain('body=false');
  });

  it('requires at least one local structure break, not just M1/M5 reclaim', () => {
    const map = priceMap(100, [wall('SUPPORT', 99.9, 3)], [wall('RESISTANCE', 101.5, 2)]);

    const result = buildM15WallScalpPlan(map, testConfig(), {
      analysis: { regime: 'RANGING', bias: 'NEUTRAL' },
      candlesByTf: new Map([['M1', reclaimWithoutBreakCandles('BUY')], ['M5', reclaimWithoutBreakCandles('BUY')]]),
    });

    expect(result.plan).toBeNull();
    expect(result.blockCategory).toBe('LTF_CONFIRMATION_MISSING');
    expect(result.reason).toContain('breakHigh=false');
  });

  it('accepts M1/M5 wall reactions when one timeframe has the directional micro break', () => {
    const map = priceMap(100, [wall('SUPPORT', 99.9, 3)], [wall('RESISTANCE', 101.5, 2)]);

    const result = buildM15WallScalpPlan(map, testConfig(), {
      analysis: { regime: 'RANGING', bias: 'NEUTRAL' },
      candlesByTf: new Map([['M1', confirmCandles('BUY')], ['M5', reclaimWithoutBreakCandles('BUY')]]),
    });

    expect(result.plan?.side).toBe('BUY');
    expect(result.plan?.confirmationNotes).toContain('LTF combined: reaction=true anyBreak=true');
  });

  it('accepts a delayed reclaim after a recent wall touch when price is still close enough', () => {
    const map = priceMap(100.4, [wall('SUPPORT', 99.9, 3)], [wall('RESISTANCE', 102.2, 2)], 3);

    const result = buildM15WallScalpPlan(map, testConfig(), {
      analysis: { regime: 'RANGING', bias: 'NEUTRAL' },
      candlesByTf: new Map([['M1', delayedReclaimBreakCandles('BUY')], ['M5', reclaimWithoutBreakCandles('BUY')]]),
    });

    expect(result.plan?.side).toBe('BUY');
    expect(result.plan?.confirmationNotes?.[0]).toContain('reclaim=true');
    expect(result.plan?.confirmationNotes).toContain('LTF combined: reaction=true anyBreak=true');
  });

  it('does not execute from a retained stale wall', () => {
    const staleSupport = {
      ...wall('SUPPORT', 99.9, 3),
      status: 'STALE',
      missingCycles: 3,
    } as PriceWall;
    const map = priceMap(100, [staleSupport], [wall('RESISTANCE', 101.5, 2)]);

    const result = buildM15WallScalpPlan(map, testConfig(), {
      analysis: { regime: 'RANGING', bias: 'NEUTRAL' },
      candlesByTf: new Map([['M1', confirmCandles('BUY')], ['M5', confirmCandles('BUY')]]),
    });

    expect(result.plan).toBeNull();
    expect(result.reason).toContain('anchor wall is stale');
  });

  it('allows a counter-context BUY only when a recent M15 bearish FVG is an active magnet and M1/M5 confirm', () => {
    const map = priceMap(100, [wall('SUPPORT', 99.9, 3)], [wall('RESISTANCE', 103, 2)]);

    const result = buildM15WallScalpPlan(map, testConfig(), {
      analysis: { regime: 'TRENDING_DOWN', bias: 'BEAR' },
      candlesByTf: new Map([['M1', confirmCandles('BUY')], ['M5', confirmCandles('BUY')]]),
      smcSnapshots: {
        M15: {
          activeFVGs: [{ type: 'BEAR', top: 103, bottom: 102, barIndex: 10, mitigated: false, age: 2 }],
        } as any,
      },
    });

    expect(result.plan?.side).toBe('BUY');
    expect(result.plan?.playbook).toBe('M15_FVG_MAGNET');
    expect(result.plan?.tp).toBeGreaterThan(101.5);
  });
});

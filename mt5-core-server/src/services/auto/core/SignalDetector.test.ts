import { describe, expect, it } from 'vitest';
import { detectEntrySignals } from './SignalDetector.js';
import type { AnalysisSummary, StrategyType } from '../types.js';
import type { Candle, PriceMap, PriceWall, SmcSnapshot } from '../analyzers/smc/types.js';

const candle = (i: number, o: number, h: number, l: number, c: number): Candle => ({
  t: i,
  o,
  h,
  l,
  c,
  v: 100,
});

const analysis = (candles: Candle[], overrides: Partial<AnalysisSummary> = {}): AnalysisSummary => ({
  bias: 'BEAR',
  regime: 'RANGING',
  confluence: 55,
  fitness: 60,
  strategy: 'RANGE',
  rationale: 'test',
  signals: [],
  atr: 2,
  candles,
  ...overrides,
});

const emptySmc = (lastPrice = 100): SmcSnapshot => ({
  activeFVGs: [],
  bullOBs: [],
  bearOBs: [],
  liquidityZones: [],
  structure: {
    direction: 'INIT',
    structureHigh: 110,
    structureLow: 90,
    structureHighBarIndex: 0,
    structureLowBarIndex: 0,
    lastEvent: 'NONE',
    lastEventSide: null,
    continuationCount: 0,
    idmHigh: null,
    idmLow: null,
    idmHighSwept: false,
    idmLowSwept: false,
  },
  mtfSweeps: {
    bullScore: 0,
    bearScore: 0,
    sweeps: [],
    aggregateSide: 'NEUTRAL',
  },
  attackForces: [],
  premiumDiscount: {
    zone: 'EQ',
    pctFromRange: 50,
    structureHigh: 110,
    structureLow: 90,
    equilibrium: 100,
  },
  swingHighs: [],
  swingLows: [],
  symbol: 'XAUUSD',
  timeframe: 'M5',
  lastPrice,
  timestamp: 0,
});

const supportWall = (price = 100, stars = 4): PriceWall => ({
  price,
  priceTop: price + 0.1,
  priceBottom: price - 0.1,
  side: 'SUPPORT',
  confluenceStars: stars,
  timeframes: ['M5', 'M15', 'M30', 'SWING'],
  sources: [],
  hasOB: false,
  hasFVG: false,
  hasLiquidity: true,
  isStructure: true,
  label: 'test support',
  distanceFromPrice: 0,
  distancePct: 0,
});

const resistanceWall = (price = 100, stars = 4): PriceWall => ({
  price,
  priceTop: price + 0.1,
  priceBottom: price - 0.1,
  side: 'RESISTANCE',
  confluenceStars: stars,
  timeframes: ['M5', 'M15', 'M30', 'SWING'],
  sources: [],
  hasOB: false,
  hasFVG: false,
  hasLiquidity: true,
  isStructure: true,
  label: 'test resistance',
  distanceFromPrice: 0,
  distancePct: 0,
});

const priceMapWithSupport = (wall: PriceWall): PriceMap => ({
  symbol: 'XAUUSD',
  currentPrice: 100.5,
  atr: 2,
  walls: [wall],
  resistanceWalls: [],
  supportWalls: [wall],
  nearestResistance: null,
  nearestSupport: wall,
  strongestResistance: null,
  strongestSupport: wall,
  sourceTimeframes: ['M5', 'M15', 'M30'],
  timestamp: 0,
});

const priceMapWithResistance = (wall: PriceWall): PriceMap => ({
  symbol: 'XAUUSD',
  currentPrice: 99.5,
  atr: 2,
  walls: [wall],
  resistanceWalls: [wall],
  supportWalls: [],
  nearestResistance: wall,
  nearestSupport: null,
  strongestResistance: wall,
  strongestSupport: null,
  sourceTimeframes: ['M5', 'M15', 'M30'],
  timestamp: 0,
});

const m15CandlesWithBearFvgAbove = (): Candle[] => {
  const candles: Candle[] = [];
  for (let i = 0; i < 27; i++) {
    candles.push(candle(i, 106, 106.5, 105.5, 106));
  }
  candles.push(candle(27, 106, 106.2, 105, 105.8));
  candles.push(candle(28, 104.2, 104.5, 103.5, 104));
  candles.push(candle(29, 102, 102.4, 101.5, 101.8));
  return candles;
};

const m15CandlesWithBullFvgBelow = (): Candle[] => {
  const candles: Candle[] = [];
  for (let i = 0; i < 27; i++) {
    candles.push(candle(i, 94, 94.5, 93.5, 94));
  }
  candles.push(candle(27, 94, 95, 93.8, 94.2));
  candles.push(candle(28, 95.2, 96.5, 95.1, 96.2));
  candles.push(candle(29, 97.8, 98.5, 97.6, 98));
  return candles;
};

const reclaimCandles = (): Candle[] => [
  candle(1, 100.2, 100.4, 99.7, 100),
  candle(2, 100, 100.2, 99.45, 99.7),
  candle(3, 99.7, 99.9, 99.4, 99.6),
  candle(4, 99.6, 100.05, 99.5, 99.95),
  candle(5, 99.95, 100.1, 99.7, 99.95),
  candle(6, 99.95, 100.55, 99.8, 100.5),
];

const rejectionCandles = (): Candle[] => [
  candle(1, 99.8, 100.3, 99.6, 100),
  candle(2, 100, 100.55, 99.8, 100.3),
  candle(3, 100.3, 100.6, 100.1, 100.4),
  candle(4, 100.4, 100.5, 99.95, 100.05),
  candle(5, 100.05, 100.2, 99.9, 100.05),
  candle(6, 100.05, 100.2, 99.45, 99.5),
];

const noBreakCandles = (): Candle[] => [
  candle(1, 100.2, 100.4, 99.7, 100),
  candle(2, 100, 100.2, 99.45, 99.7),
  candle(3, 99.7, 99.9, 99.4, 99.6),
  candle(4, 99.6, 99.95, 99.5, 99.7),
  candle(5, 99.7, 99.9, 99.55, 99.65),
  candle(6, 99.65, 99.85, 99.5, 99.6),
];

const magnetOnlyConfig = {
  enableOBBounce: false,
  enableLiqSweep: false,
  enableStructureBreak: false,
  enableFvgFill: false,
  enableSweepToFvg: true,
  enableWallBreakScalp: false,
  minRRR: 1.2,
};

const wallBreakOnlyConfig = {
  enableOBBounce: false,
  enableLiqSweep: false,
  enableStructureBreak: false,
  enableFvgFill: false,
  enableSweepToFvg: false,
  enableWallBreakScalp: true,
  minRRR: 1.2,
};

const discountContext = (tail: Candle[]): Candle[] => [
  ...Array.from({ length: 20 }, (_, i) => candle(i, 106, 110, 98, 106)),
  ...tail.map((c, i) => ({ ...c, t: 20 + i })),
];

const premiumContext = (tail: Candle[]): Candle[] => [
  ...Array.from({ length: 20 }, (_, i) => candle(i, 94, 102, 90, 94)),
  ...tail.map((c, i) => ({ ...c, t: 20 + i })),
];

describe('SignalDetector wall-to-FVG magnet scalp', () => {
  it('emits SMC_FVG_MAGNET_SCALP only after wall reclaim with an unmitigated M15 FVG target', () => {
    const signals = detectEntrySignals(
      'XAUUSD',
      100.5,
      emptySmc(100.5),
      {
        H4: analysis([], { bias: 'BEAR' }),
        H1: analysis([], { bias: 'BEAR' }),
        M15: analysis(m15CandlesWithBearFvgAbove(), { bias: 'BEAR' }),
        M5: analysis(reclaimCandles(), { bias: 'BULL', strategy: 'SCALPING' as StrategyType }),
      },
      priceMapWithSupport(supportWall()),
      magnetOnlyConfig,
    );

    expect(signals).toHaveLength(1);
    expect(signals[0]).toMatchObject({
      side: 'BUY',
      strategy: 'SMC_FVG_MAGNET_SCALP',
      confluenceStars: 4,
    });
    expect(signals[0].tp).toBeCloseTo(102.4, 5);
    expect(signals[0].riskRewardRatio).toBeGreaterThanOrEqual(1.34);
    expect(signals[0].triggers.join(' | ')).toContain('M15_BEAR_FVG_MAGNET');
    expect(signals[0].triggers.join(' | ')).toContain('BREAK_BUY');
  });

  it('does not emit the magnet scalp when price touches the wall but no M1/M5 break happens', () => {
    const signals = detectEntrySignals(
      'XAUUSD',
      99.6,
      emptySmc(99.6),
      {
        M15: analysis(m15CandlesWithBearFvgAbove()),
        M5: analysis(noBreakCandles()),
      },
      priceMapWithSupport(supportWall()),
      magnetOnlyConfig,
    );

    expect(signals.find((signal) => signal.strategy === 'SMC_FVG_MAGNET_SCALP')).toBeUndefined();
  });

  it('mirrors the magnet logic for SELL with resistance wall and unmitigated M15 bull FVG below', () => {
    const signals = detectEntrySignals(
      'XAUUSD',
      99.5,
      emptySmc(99.5),
      {
        H4: analysis([], { bias: 'BULL' }),
        H1: analysis([], { bias: 'BULL' }),
        M15: analysis(m15CandlesWithBullFvgBelow(), { bias: 'BULL' }),
        M5: analysis(rejectionCandles(), { bias: 'BEAR', strategy: 'SCALPING' as StrategyType }),
      },
      priceMapWithResistance(resistanceWall()),
      magnetOnlyConfig,
    );

    expect(signals).toHaveLength(1);
    expect(signals[0]).toMatchObject({
      side: 'SELL',
      strategy: 'SMC_FVG_MAGNET_SCALP',
      confluenceStars: 4,
    });
    expect(signals[0].tp).toBeCloseTo(97.6, 5);
    expect(signals[0].riskRewardRatio).toBeGreaterThanOrEqual(1.34);
    expect(signals[0].triggers.join(' | ')).toContain('M15_BULL_FVG_MAGNET');
    expect(signals[0].triggers.join(' | ')).toContain('BREAK_SELL');
  });
});

describe('SignalDetector M1+M5 wall-break scalp', () => {
  it('emits SMC_WALL_BREAK_SCALP on support reclaim when M1 and M5 are both in discount', () => {
    const signals = detectEntrySignals(
      'XAUUSD',
      100.5,
      emptySmc(100.5),
      {
        M1: analysis(discountContext(reclaimCandles()), { bias: 'BULL', strategy: 'SCALPING' as StrategyType }),
        M5: analysis(discountContext(reclaimCandles()), { bias: 'BULL', strategy: 'SCALPING' as StrategyType }),
        M15: analysis(discountContext(reclaimCandles()), { bias: 'BEAR', strategy: 'RANGE' as StrategyType }),
      },
      priceMapWithSupport(supportWall()),
      wallBreakOnlyConfig,
    );

    expect(signals).toHaveLength(1);
    expect(signals[0]).toMatchObject({
      side: 'BUY',
      strategy: 'SMC_WALL_BREAK_SCALP',
      confluenceStars: 4,
    });
    expect(signals[0].triggers.join(' | ')).toContain('M1_DISCOUNT');
    expect(signals[0].triggers.join(' | ')).toContain('M5_DISCOUNT');
    expect(signals[0].triggers.join(' | ')).toContain('M1_BREAK_BUY');
    expect(signals[0].triggers.join(' | ')).toContain('M5_BREAK_BUY');
  });

  const eqContext = (tail: Candle[]): Candle[] => [
    ...Array.from({ length: 20 }, (_, i) => candle(i, 100, 105, 95, 100)),
    ...tail.map((c, i) => ({ ...c, t: 20 + i })),
  ];

  it('emits SMC_WALL_BREAK_SCALP when M1 and M5 are in EQ zone (lenient zone check)', () => {
    const signals = detectEntrySignals(
      'XAUUSD',
      100.5,
      emptySmc(100.5),
      {
        M1: analysis(eqContext(reclaimCandles()), { bias: 'BULL', strategy: 'SCALPING' as StrategyType }),
        M5: analysis(eqContext(reclaimCandles()), { bias: 'BULL', strategy: 'SCALPING' as StrategyType }),
        M15: analysis(discountContext(reclaimCandles()), { bias: 'BEAR', strategy: 'RANGE' as StrategyType }),
      },
      priceMapWithSupport(supportWall()),
      wallBreakOnlyConfig,
    );

    expect(signals).toHaveLength(1);
    expect(signals[0]).toMatchObject({
      side: 'BUY',
      strategy: 'SMC_WALL_BREAK_SCALP',
    });
    expect(signals[0].triggers.join(' | ')).toContain('M1_EQ');
    expect(signals[0].triggers.join(' | ')).toContain('M5_EQ');
  });

  it('does not emit wall-break scalp if M5 has not confirmed the break', () => {
    const signals = detectEntrySignals(
      'XAUUSD',
      100.5,
      emptySmc(100.5),
      {
        M1: analysis(discountContext(reclaimCandles()), { bias: 'BULL', strategy: 'SCALPING' as StrategyType }),
        M5: analysis(discountContext(noBreakCandles()), { bias: 'BULL', strategy: 'SCALPING' as StrategyType }),
      },
      priceMapWithSupport(supportWall()),
      wallBreakOnlyConfig,
    );

    expect(signals.find((signal) => signal.strategy === 'SMC_WALL_BREAK_SCALP')).toBeUndefined();
  });

  it('mirrors wall-break scalp for resistance rejection when M1 and M5 are both in premium', () => {
    const signals = detectEntrySignals(
      'XAUUSD',
      99.5,
      emptySmc(99.5),
      {
        M1: analysis(premiumContext(rejectionCandles()), { bias: 'BEAR', strategy: 'SCALPING' as StrategyType }),
        M5: analysis(premiumContext(rejectionCandles()), { bias: 'BEAR', strategy: 'SCALPING' as StrategyType }),
      },
      priceMapWithResistance(resistanceWall()),
      wallBreakOnlyConfig,
    );

    expect(signals).toHaveLength(1);
    expect(signals[0]).toMatchObject({
      side: 'SELL',
      strategy: 'SMC_WALL_BREAK_SCALP',
      confluenceStars: 4,
    });
    expect(signals[0].triggers.join(' | ')).toContain('M1_PREMIUM');
    expect(signals[0].triggers.join(' | ')).toContain('M5_PREMIUM');
    expect(signals[0].triggers.join(' | ')).toContain('M1_BREAK_SELL');
    expect(signals[0].triggers.join(' | ')).toContain('M5_BREAK_SELL');
  });
});

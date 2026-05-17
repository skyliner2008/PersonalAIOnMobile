import { describe, expect, it } from 'vitest';
import type { PriceWall } from '../../analyzers/smc/types.js';
import { evaluatePB3 } from './PB3_SweepReversal.js';
import type { PlaybookContext } from './types.js';

function wall(price: number, side: PriceWall['side'], stars: number, timeframes: string[] = ['M15']): PriceWall {
  return {
    price,
    priceTop: price,
    priceBottom: price,
    side,
    confluenceStars: stars,
    timeframes,
    sources: [],
    hasOB: false,
    hasFVG: false,
    hasLiquidity: false,
    isStructure: false,
    label: `${side}@${price}`,
    distanceFromPrice: 0,
    distancePct: 0,
  };
}

function baseContext(overrides: Partial<PlaybookContext>): PlaybookContext {
  return {
    symbol: 'XAUUSD',
    side: 'SELL',
    wall: wall(4707.93, 'RESISTANCE', 5, ['M5', 'M15', 'M30', 'H1', 'D1']),
    atrM15: 3,
    price: 4706.55,
    reversalKind: 'BEARISH_PINBAR',
    hasFreshOpposite: true,
    oppositeWall: wall(4698.54, 'SUPPORT', 5, ['M5', 'M15', 'H1']),
    swingHigh: 4706.7,
    swingLow: 4702.4,
    spread: 0.1,
    touchCount: 1,
    profile: {
      minRrr: 1.8,
      minWallStars: 2,
      slBufferAtrMul: 0.2,
    },
    ...overrides,
  };
}

describe('PB3 sweep reversal', () => {
  it('anchors a SELL stop above the wall even when the M1 swing high is below it', () => {
    const result = evaluatePB3(baseContext({}));

    expect(result.plan).not.toBeNull();
    expect(result.plan?.side).toBe('SELL');
    expect(result.plan!.sl).toBeGreaterThan(result.plan!.entry);
    expect(result.plan!.tp).toBeLessThan(result.plan!.entry);
  });

  it('anchors a BUY stop below the wall even when the M1 swing low is above it', () => {
    const result = evaluatePB3(baseContext({
      side: 'BUY',
      wall: wall(4687.42, 'SUPPORT', 5, ['M5', 'M15', 'M30', 'H1']),
      oppositeWall: wall(4698.54, 'RESISTANCE', 5, ['M5', 'M15', 'H1']),
      reversalKind: 'BULLISH_PINBAR',
      swingLow: 4688.1,
      swingHigh: 4692,
    }));

    expect(result.plan).not.toBeNull();
    expect(result.plan?.side).toBe('BUY');
    expect(result.plan!.sl).toBeLessThan(result.plan!.entry);
    expect(result.plan!.tp).toBeGreaterThan(result.plan!.entry);
  });
});

import { describe, expect, it } from 'vitest';
import { evaluatePB4 } from './PB4_RangeFade.js';
import type { PriceWall } from '../../analyzers/smc/types.js';
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

function ctx(overrides: Partial<PlaybookContext>): PlaybookContext {
  const touchedWall = wall(4707.93, 'RESISTANCE', 5, ['M5', 'M15', 'M30', 'H1', 'D1']);
  return {
    symbol: 'XAUUSD',
    side: 'SELL',
    wall: touchedWall,
    atrM15: 8,
    price: 4706.55,
    reversalKind: 'CONFIRM_VIA_STATE_MACHINE',
    hasFreshOpposite: false,
    oppositeWall: wall(4698.54, 'SUPPORT', 5, ['M5', 'M15', 'H1']),
    swingHigh: 4706.7,
    swingLow: 4702,
    spread: 0.12,
    touchCount: 1,
    profile: {
      minRrr: 1.8,
      minWallStars: 2,
      slBufferAtrMul: 0.1,
    },
    ...overrides,
  };
}

describe('evaluatePB4', () => {
  it('anchors SELL stop above the touched wall when the latest swing is below resistance', () => {
    const result = evaluatePB4(ctx({}), 'RANGING');

    expect(result.plan).not.toBeNull();
    expect(result.plan?.side).toBe('SELL');
    expect(result.plan?.entry).toBeLessThan(4707.93);
    expect(result.plan?.sl).toBeGreaterThan(result.plan!.entry);
    expect(result.plan?.tp).toBeLessThan(result.plan!.entry);
    expect(result.plan?.rrr).toBeGreaterThanOrEqual(1.5);
  });

  it('anchors BUY stop below the touched wall when the latest swing is above support', () => {
    const result = evaluatePB4(
      ctx({
        side: 'BUY',
        wall: wall(4687.42, 'SUPPORT', 4, ['M5', 'M15', 'M30', 'H1']),
        oppositeWall: wall(4698.54, 'RESISTANCE', 5, ['M5', 'M15', 'H1']),
        price: 4687.9,
        swingLow: 4688.2,
        swingHigh: 4692,
      }),
      'RANGING',
    );

    expect(result.plan).not.toBeNull();
    expect(result.plan?.side).toBe('BUY');
    expect(result.plan?.entry).toBeGreaterThan(4687.42);
    expect(result.plan?.sl).toBeLessThan(result.plan!.entry);
    expect(result.plan?.tp).toBeGreaterThan(result.plan!.entry);
    expect(result.plan?.rrr).toBeGreaterThanOrEqual(1.5);
  });
});

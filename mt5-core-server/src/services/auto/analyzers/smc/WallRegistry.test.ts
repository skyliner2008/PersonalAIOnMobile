import { describe, expect, it } from 'vitest';
import type { PriceMap, PriceWall } from './types.js';
import { StableWallRegistry } from './WallRegistry.js';

function wall(price: number, side: PriceWall['side'], timeframes: string[]): PriceWall {
  return {
    price,
    priceTop: price + 0.3,
    priceBottom: price - 0.3,
    side,
    confluenceStars: Math.min(5, Math.max(1, timeframes.length)),
    timeframes,
    sources: timeframes.map((timeframe) => ({
      type: side === 'SUPPORT' ? 'SWING_LOW' : 'SWING_HIGH',
      timeframe,
      priceTop: price,
      priceBottom: price,
    })),
    hasOB: false,
    hasFVG: false,
    hasLiquidity: true,
    isStructure: true,
    label: `${price}`,
    distanceFromPrice: 0,
    distancePct: 0,
  };
}

function map(currentPrice: number, walls: PriceWall[], atr = 8): PriceMap {
  const resistanceWalls = walls.filter((w) => w.price > currentPrice).sort((a, b) => a.price - b.price);
  const supportWalls = walls.filter((w) => w.price <= currentPrice).sort((a, b) => b.price - a.price);
  return {
    symbol: 'XAUUSD',
    currentPrice,
    atr,
    walls,
    resistanceWalls,
    supportWalls,
    nearestResistance: resistanceWalls[0] ?? null,
    nearestSupport: supportWalls[0] ?? null,
    strongestResistance: resistanceWalls[0] ?? null,
    strongestSupport: supportWalls[0] ?? null,
    sourceTimeframes: ['M1', 'M5', 'M15', 'M30', 'H1'],
    timestamp: Date.now(),
  };
}

describe('StableWallRegistry', () => {
  it('keeps the same wall identity when the raw cluster drifts inside the same ATR zone', () => {
    const registry = new StableWallRegistry();
    const first = registry.stabilize('XAUUSD', map(4525.15, [
      wall(4521.05, 'SUPPORT', ['M1', 'M5', 'M15', 'M30', 'H1']),
    ]));
    const second = registry.stabilize('XAUUSD', map(4524.59, [
      wall(4517.45, 'SUPPORT', ['M5', 'M15', 'M30', 'H1']),
    ]));

    expect(second.walls[0].id).toBe(first.walls[0].id);
    expect(second.walls[0].price).toBeGreaterThan(4517.45);
    expect(second.walls[0].price).toBeLessThan(4521.05);
    expect(second.walls[0].status).toBe('ACTIVE');
  });

  it('retains a missing HTF wall for a few cycles instead of flickering it off the map', () => {
    const registry = new StableWallRegistry({ retainMissingCycles: 2 });
    const first = registry.stabilize('XAUUSD', map(4518, [
      wall(4517.15, 'SUPPORT', ['M15', 'M30', 'H1']),
    ]));
    const second = registry.stabilize('XAUUSD', map(4518, []));

    expect(second.walls[0].id).toBe(first.walls[0].id);
    expect(second.walls[0].status).toBe('STALE');
    expect(second.walls[0].missingCycles).toBe(1);
  });
});

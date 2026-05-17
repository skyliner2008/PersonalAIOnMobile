import { describe, expect, it } from 'vitest';
import type { PriceMap, PriceWall } from '../analyzers/smc/types.js';
import { computeAdaptiveProximityMaxPip, evaluateProximityGate } from './ProximityGate.js';

function wall(price: number, side: PriceWall['side'], stars = 4): PriceWall {
  return {
    price,
    priceTop: price,
    priceBottom: price,
    side,
    confluenceStars: stars,
    timeframes: ['M15', 'H1', 'H4'],
    sources: [],
    hasOB: true,
    hasFVG: false,
    hasLiquidity: false,
    isStructure: true,
    label: `${side}@${price}`,
    distanceFromPrice: 0,
    distancePct: 0,
  };
}

function priceMap(currentPrice: number, resistance: PriceWall[], support: PriceWall[] = []): PriceMap {
  return {
    symbol: 'XAUUSD',
    currentPrice,
    atr: 21.5,
    walls: [...resistance, ...support],
    resistanceWalls: resistance,
    supportWalls: support,
    nearestResistance: resistance[0] ?? null,
    nearestSupport: support[0] ?? null,
    strongestResistance: resistance[0] ?? null,
    strongestSupport: support[0] ?? null,
    sourceTimeframes: ['M15', 'H1', 'H4'],
    timestamp: Date.now(),
  };
}

describe('computeAdaptiveProximityMaxPip', () => {
  it('caps volatile XAUUSD ATR expansion to keep wall entries close', () => {
    expect(computeAdaptiveProximityMaxPip('XAUUSD', 21.5, 100)).toBe(500);
  });

  it('keeps the configured base when ATR expansion is smaller', () => {
    expect(computeAdaptiveProximityMaxPip('XAUUSD', 3, 100)).toBe(100);
  });
});

describe('evaluateProximityGate', () => {
  it('blocks a SELL when the nearest resistance is thousands of pips away', () => {
    const result = evaluateProximityGate(
      'XAUUSD',
      'SELL',
      4725.14,
      priceMap(4725.14, [wall(4749.38, 'RESISTANCE', 4)]),
      null,
      { maxPipDist: 500 }
    );

    expect(result.allowed).toBe(false);
    expect(result.distancePip).toBeGreaterThan(2000);
  });

  it('allows a strong resistance fade inside the capped distance', () => {
    const result = evaluateProximityGate(
      'XAUUSD',
      'SELL',
      4725.14,
      priceMap(4725.14, [wall(4728.9, 'RESISTANCE', 4)]),
      null,
      { maxPipDist: 500 }
    );

    expect(result.allowed).toBe(true);
    expect(result.distancePip).toBeLessThanOrEqual(500);
  });
});

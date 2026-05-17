import { describe, expect, it } from 'vitest';
import { buildZoneLayerContext, hasAlignedFvgNearPrice } from './ZoneAwareGate.js';

const zone = (zoneName: 'PREMIUM' | 'EQ' | 'DISCOUNT', pctFromRange: number) => ({
  zone: zoneName,
  pctFromRange,
  structureHigh: 100,
  structureLow: 0,
  equilibrium: 50,
});

describe('ZoneAwareGate MTF context', () => {
  it('keeps HTF wrong-zone risk but recognizes execution-TF pullback alignment', () => {
    const ctx = buildZoneLayerContext('SELL', [
      { timeframe: 'H4', role: 'major', result: zone('DISCOUNT', 7.7) },
      { timeframe: 'H1', role: 'context', result: zone('DISCOUNT', 8.4) },
      { timeframe: 'M15', role: 'execution', result: zone('EQ', 45) },
      { timeframe: 'M5', role: 'execution', result: zone('PREMIUM', 67) },
    ], 25);

    expect(ctx.hasWrongZone).toBe(true);
    expect(ctx.majorWrong).toBe(true);
    expect(ctx.hardMajorWrong).toBe(true);
    expect(ctx.executionFavorable).toBe(true);
    expect(ctx.hardExecutionWrong).toBe(false);
    expect(ctx.favorableLayers).toContain('M5');
  });

  it('still treats deep wrong execution zones as a hard block candidate', () => {
    const ctx = buildZoneLayerContext('SELL', [
      { timeframe: 'H4', role: 'major', result: zone('DISCOUNT', 12) },
      { timeframe: 'M15', role: 'execution', result: zone('DISCOUNT', 9) },
      { timeframe: 'M5', role: 'execution', result: zone('DISCOUNT', 10) },
    ], 25);

    expect(ctx.executionWrong).toBe(true);
    expect(ctx.executionAllWrong).toBe(true);
    expect(ctx.hardExecutionWrong).toBe(true);
    expect(ctx.executionFavorable).toBe(false);
  });

  it('accepts a fresh aligned FVG when price is just outside the gap by ATR tolerance', () => {
    expect(hasAlignedFvgNearPrice('SELL', 78004.31, [
      { type: 'BEAR', top: 78068.12, bottom: 78004.48, index: 10 },
    ], { atr: 217.24, atrMultiplier: 0.35 })).toBe(true);

    expect(hasAlignedFvgNearPrice('SELL', 77700, [
      { type: 'BEAR', top: 78068.12, bottom: 78004.48, index: 10 },
    ], { atr: 217.24, atrMultiplier: 0.35 })).toBe(false);
  });
});

import { describe, expect, it } from 'vitest';
import { buildZoneLayerContext, findAlignedFvgFill, findOpposingScalpPressure, hasAlignedFvgNearPrice } from './ZoneAwareGate.js';

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

  it('bypasses major HTF wrong-zone checks for scalp-like strategies', () => {
    const ctx = buildZoneLayerContext('SELL', [
      { timeframe: 'H4', role: 'major', result: zone('DISCOUNT', 7.7) },
      { timeframe: 'H1', role: 'context', result: zone('DISCOUNT', 8.4) },
      { timeframe: 'M15', role: 'execution', result: zone('EQ', 45) },
      { timeframe: 'M5', role: 'execution', result: zone('PREMIUM', 67) },
    ], 25, 'SCALPING');

    expect(ctx.hasWrongZone).toBe(false);
    expect(ctx.majorWrong).toBe(false);
    expect(ctx.hardMajorWrong).toBe(false);
    expect(ctx.executionFavorable).toBe(true);
    expect(ctx.hardExecutionWrong).toBe(false);
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

  it('requires SMC_FVG_SCALP proof to be inside a filled aligned FVG', () => {
    expect(findAlignedFvgFill('SELL', 78050, [
      { type: 'BEAR', top: 78068.12, bottom: 78004.48, index: 10 },
    ], { minFillPct: 0.5 })?.fillPct).toBeGreaterThanOrEqual(0.5);

    expect(findAlignedFvgFill('SELL', 77990, [
      { type: 'BEAR', top: 78068.12, bottom: 78004.48, index: 10 },
    ], { minFillPct: 0.5 })).toBeNull();

    expect(findAlignedFvgFill('BUY', 100.6, [
      { type: 'BULL', top: 101, bottom: 100, index: 10 },
    ], { minFillPct: 0.5 })).toBeNull();
  });

  it('detects M15 opposing SMC pressure for generic scalps', () => {
    expect(findOpposingScalpPressure('BUY', [
      { name: 'fvg_detected', bias: 'BULL', evidence: 'BULL FVG at 4544.93-4547.87' },
      { name: 'price_in_bear_ob', bias: 'BEAR', evidence: 'Price testing Bearish OB (4529.88-4555.08)' },
    ])?.reason).toContain('BUY into M15 opposing BEAR');

    expect(findOpposingScalpPressure('SELL', [
      { name: 'fvg_detected', bias: 'BULL', evidence: 'BULL FVG at 4544.93-4547.87' },
      { name: 'price_in_bear_ob', bias: 'BEAR', evidence: 'Price testing Bearish OB (4529.88-4555.08)' },
    ])).toBeNull();
  });
});

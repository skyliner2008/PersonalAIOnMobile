import { describe, expect, it } from 'vitest';
import { buildPriceMap, formatPriceMap } from './priceMapBuilder.js';
import type { SmcSnapshot } from './types.js';

const snapshot = (overrides: Partial<SmcSnapshot> = {}): SmcSnapshot => ({
  activeFVGs: [],
  bullOBs: [],
  bearOBs: [],
  liquidityZones: [],
  structure: {
    direction: 'INIT',
    structureHigh: 0,
    structureLow: 0,
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
    structureHigh: 100,
    structureLow: 0,
    equilibrium: 50,
  },
  swingHighs: [],
  swingLows: [],
  symbol: 'XAUUSD',
  timeframe: 'M15',
  lastPrice: 4565,
  timestamp: 0,
  ...overrides,
});

describe('PriceMap wall anchoring', () => {
  it('uses dynamic context as confluence without moving a structural wall anchor', () => {
    const map = buildPriceMap(
      new Map([
        ['M15', snapshot({ swingHighs: [4589.28] })],
      ]),
      4565.33,
      4,
      {},
      [
        { price: 4590.0, side: 'RESISTANCE', type: 'VWAP', label: 'VWAP', weight: 3, timeframe: 'SESSION' },
      ],
    );

    expect(map.resistanceWalls[0].price).toBe(4589.28);
    expect(map.resistanceWalls[0].priceTop).toBe(4589.28);
    expect(map.resistanceWalls[0].priceBottom).toBe(4589.28);
    expect(map.resistanceWalls[0].timeframes).toEqual(['M15', 'SESSION']);
    expect(map.resistanceWalls[0].confluenceStars).toBeGreaterThanOrEqual(3);
  });

  it('still creates a context-only wall when no structural source is nearby', () => {
    const map = buildPriceMap(
      new Map([
        ['M15', snapshot()],
      ]),
      4565.33,
      4,
      {},
      [
        { price: 4589.28, side: 'RESISTANCE', type: 'FIB', label: 'Fib 0.618', weight: 3, timeframe: 'SWING' },
      ],
    );

    expect(map.resistanceWalls[0].price).toBe(4589.28);
    expect(map.resistanceWalls[0].timeframes).toEqual(['SWING']);
  });

  it('keeps an MTF wall visible while price is sitting on the wall', () => {
    const mtfSnapshots = new Map([
      ['M1', snapshot({ swingLows: [4520.53] })],
      ['M5', snapshot({ swingLows: [4520.53] })],
      ['M15', snapshot({ swingLows: [4520.53] })],
      ['M30', snapshot({ swingLows: [4520.53] })],
      ['H1', snapshot({ swingLows: [4520.53] })],
    ]);

    const before = buildPriceMap(mtfSnapshots, 4520.5, 16, {}, [
      { price: 4520.53, side: 'BOTH', type: 'VWAP', label: 'VWAP', weight: 3, timeframe: 'SESSION' },
      { price: 4520.53, side: 'BOTH', type: 'FIB', label: 'Fib 0.618', weight: 3, timeframe: 'SWING' },
    ]);
    const after = buildPriceMap(mtfSnapshots, 4521.02, 16, {}, [
      { price: 4520.53, side: 'BOTH', type: 'VWAP', label: 'VWAP', weight: 3, timeframe: 'SESSION' },
      { price: 4520.53, side: 'BOTH', type: 'FIB', label: 'Fib 0.618', weight: 3, timeframe: 'SWING' },
    ]);

    const beforeWall = before.walls.find(w => Math.abs(w.price - 4520.53) < 0.01);
    const afterWall = after.walls.find(w => Math.abs(w.price - 4520.53) < 0.01);

    expect(beforeWall).toBeTruthy();
    expect(afterWall).toBeTruthy();
    expect(beforeWall?.timeframes).toEqual(['M1', 'M5', 'M15', 'M30', 'H1', 'SESSION', 'SWING']);
    expect(afterWall?.timeframes).toEqual(beforeWall?.timeframes);
    expect(beforeWall?.confluenceStars).toBe(5);
    expect(afterWall?.confluenceStars).toBe(5);
    expect(before.supportWalls.some(w => Math.abs(w.price - 4520.53) < 0.01)).toBe(true);
    expect(after.supportWalls.some(w => Math.abs(w.price - 4520.53) < 0.01)).toBe(true);
  });

  it('does not let context-only overlap become a five-star structural wall', () => {
    const map = buildPriceMap(
      new Map([['M15', snapshot()]]),
      4520.5,
      16,
      {},
      [
        { price: 4520.53, side: 'BOTH', type: 'PIVOT', label: 'P', weight: 3, timeframe: 'D1' },
        { price: 4520.53, side: 'BOTH', type: 'VWAP', label: 'VWAP', weight: 3, timeframe: 'SESSION' },
        { price: 4520.53, side: 'BOTH', type: 'FIB', label: 'Fib 0.618', weight: 3, timeframe: 'SWING' },
      ],
    );

    const wall = map.walls.find(w => Math.abs(w.price - 4520.53) < 0.01);
    expect(wall?.timeframes).toEqual(['D1', 'SESSION', 'SWING']);
    expect(wall?.confluenceStars).toBeLessThanOrEqual(2);
  });

  it('formats the ladder strictly by price around the divider', () => {
    const map = buildPriceMap(
      new Map([
        ['M1', snapshot({
          swingLows: [4534.55],
          swingHighs: [4529.86],
        })],
      ]),
      4533.7,
      10,
    );

    const formatted = formatPriceMap(map);
    const [resistanceSection, afterPrice] = formatted.split('── PRICE: 4533.7');

    expect(resistanceSection).toContain('4534.55');
    expect(resistanceSection).not.toContain('4529.86');
    expect(afterPrice).toContain('4529.86');
    expect(afterPrice).not.toContain('4534.55');
  });

  it('caps confluence stars to 3 when no HTF source is present', () => {
    const mtfSnapshots = new Map([
      ['M1', snapshot({ swingHighs: [4550.0] })],
      ['M5', snapshot({ swingHighs: [4550.0] })],
      ['M15', snapshot({ swingHighs: [4550.0] })],
      ['M30', snapshot({ swingHighs: [4550.0] })],
    ]);

    const map = buildPriceMap(mtfSnapshots, 4530.0, 10, {
      clusterAtrPct: 0.5,
    });

    const wall = map.walls.find(w => Math.abs(w.price - 4550.0) < 0.01);
    expect(wall).toBeTruthy();
    // โดน Cap ดาวไว้ไม่เกิน 3★ แม้จะมีหลาย TF คอนฟลูเอนซ์ เพราะเป็นเพียงกรอบเล็ก
    expect(wall?.confluenceStars).toBeLessThanOrEqual(3);
  });

  it('does not count a mitigated order block as fresh OB/FVG confirmation', () => {
    const map = buildPriceMap(
      new Map([
        ['M15', snapshot({
          bearOBs: [{
            type: 'BEAR',
            top: 101,
            bottom: 100,
            barIndex: 10,
            mitigated: true,
            hasFVG: true,
            invalidated: false,
            volume: 100,
          }],
        })],
      ]),
      99.8,
      2,
    );

    const wall = map.resistanceWalls.find(w => Math.abs(w.price - 100.5) < 0.01);
    expect(wall).toBeTruthy();
    expect(wall?.hasOB).toBe(false);
    expect(wall?.hasFVG).toBe(false);
    expect(wall?.confluenceStars).toBe(1);
  });

  it('applies proximity suppression to filter adjacent walls of the same side', () => {
    const mtfSnapshots = new Map([
      ['H1', snapshot({ swingHighs: [4550.0] })],
      ['M15', snapshot({ swingHighs: [4552.0] })],
    ]);

    const map = buildPriceMap(mtfSnapshots, 4530.0, 10, {
      clusterAtrPct: 0.05,        // 0.05 * 10 = 0.5 points cluster range (แยกตัวกันก่อน)
      enableProximitySuppression: true,
      minWallSpacingAtrPct: 0.75, // 7.5 points spacing limit (มายุบระงับที่ขั้นตอนนี้)
    });

    // 4552.0 อยู่ใกล้ 4550.0 (ระยะห่าง 2.0 < 7.5) และทั้งคู่เป็นแนวต้านฝั่งเดียวกัน
    // 4550.0 แข็งแกร่งกว่าเพราะมี H1 (HTF) ส่วน 4552.0 มีแค่ M15 (LTF)
    // ดังนั้น 4552.0 จะต้องโดนยุบระงับออกไป
    const wallAt50 = map.walls.find(w => Math.abs(w.price - 4550.0) < 0.5);
    const wallAt52 = map.walls.find(w => Math.abs(w.price - 4552.0) < 0.5);

    expect(wallAt50).toBeTruthy();
    expect(wallAt52).toBeFalsy(); // ยุบสำเร็จ
  });
});

/**
 * SMC Engine V2 — Market Structure Detection
 * Port จาก Pine Script V8.3: BOS / CHoCH / SMS / BMS + IDM Filter
 *
 * ตรวจจับ:
 * - Break of Structure (BOS) — continuation
 * - Change of Character (CHoCH) — reversal
 * - SMS (Shift in Market Structure) — first continuation after CHoCH
 * - BMS (Break in Market Structure) — subsequent continuations
 * - IDM (Inducement) Filter — require liquidity sweep before valid break
 *
 * 2026-05-06 — skyliner.jojo@gmail.com
 */

import type { Candle, MarketStructure, StructureDirection, StructureEvent } from './types.js';
import { detectSwings } from './utils.js';

export interface MarketStructureConfig {
  useBodyBreak: boolean;      // ใช้ body หรือ wick ในการเช็ค break (default: true = body)
  swingLength: number;        // swing detection lookback (default: 10)
  idmEnabled: boolean;        // เปิดใช้ IDM filter (default: true)
  idmSwingLength: number;     // IDM swing length (default: 3)
  idmApplyTo: 'CHoCH' | 'CHoCH+BOS'; // default: 'CHoCH'
}

const DEFAULT_CONFIG: MarketStructureConfig = {
  useBodyBreak: true,
  swingLength: 10,
  idmEnabled: true,
  idmSwingLength: 3,
  idmApplyTo: 'CHoCH',
};

/**
 * Detect Market Structure from candle array.
 * State-based approach matching Pine Script V8.3 logic.
 */
export function detectMarketStructure(
  candles: Candle[],
  config: Partial<MarketStructureConfig> = {}
): MarketStructure {
  const cfg = { ...DEFAULT_CONFIG, ...config };

  if (candles.length < 30) {
    return defaultStructure();
  }

  // State variables (equivalent to Pine Script `var`)
  let direction: StructureDirection = 'INIT';
  let structureHigh = candles[0].h;
  let structureLow = candles[0].l;
  let structureHighBarIndex = 0;
  let structureLowBarIndex = 0;
  let lastEvent: StructureEvent = 'NONE';
  let lastEventSide: 'UP' | 'DOWN' | null = null;
  let continuationCount = 0;

  // IDM state
  let idmHigh: number | null = null;
  let idmLow: number | null = null;
  let idmHighSwept = false;
  let idmLowSwept = false;

  // Detect swing points for IDM
  const idmSwings = cfg.idmEnabled ? detectSwings(candles, cfg.idmSwingLength) : { highs: [], lows: [] };

  // Process each bar
  for (let i = 1; i < candles.length; i++) {
    const c = candles[i];
    const breakLow = cfg.useBodyBreak ? c.c : c.l;
    const breakHigh = cfg.useBodyBreak ? c.c : c.h;

    // --- IDM Processing ---
    if (cfg.idmEnabled) {
      // Update IDM swing points
      for (const sh of idmSwings.highs) {
        if (sh.index === i) {
          idmHigh = sh.price;
          idmHighSwept = false;
        }
      }
      for (const sl of idmSwings.lows) {
        if (sl.index === i) {
          idmLow = sl.price;
          idmLowSwept = false;
        }
      }

      // Check IDM sweeps (wick through but close back)
      if (!idmHighSwept && idmHigh !== null) {
        if (c.h > idmHigh && c.c < idmHigh) idmHighSwept = true;
        if (i > 0 && candles[i - 1].h > idmHigh && candles[i - 1].c < idmHigh) idmHighSwept = true;
      }
      if (!idmLowSwept && idmLow !== null) {
        if (c.l < idmLow && c.c > idmLow) idmLowSwept = true;
        if (i > 0 && candles[i - 1].l < idmLow && candles[i - 1].c > idmLow) idmLowSwept = true;
      }
    }

    // --- Dynamic Lookback for Structure Extremes ---
    const lbHigh = Math.max(1, Math.min(i - structureLowBarIndex, i));
    const lbLow = Math.max(1, Math.min(i - structureHighBarIndex, i));

    // Find highest high since last structure low
    let highestInRange = structureHigh;
    let highestIdx = structureHighBarIndex;
    for (let j = Math.max(0, i - lbHigh); j <= i; j++) {
      if (candles[j].h > highestInRange) {
        highestInRange = candles[j].h;
        highestIdx = j;
      }
    }

    // Find lowest low since last structure high
    let lowestInRange = structureLow;
    let lowestIdx = structureLowBarIndex;
    for (let j = Math.max(0, i - lbLow); j <= i; j++) {
      if (candles[j].l < lowestInRange) {
        lowestInRange = candles[j].l;
        lowestIdx = j;
      }
    }

    // --- Break Detection ---
    const isLowBroken = breakLow < structureLow && i > structureLowBarIndex;
    const isHighBroken = breakHigh > structureHigh && i > structureHighBarIndex;

    if (isLowBroken) {
      const priorDir = direction;
      const isReversal = priorDir !== 'BEARISH';

      // IDM validation
      const needIDM = cfg.idmEnabled && (
        (cfg.idmApplyTo === 'CHoCH' && isReversal) ||
        cfg.idmApplyTo === 'CHoCH+BOS'
      );
      const idmOk = !needIDM || idmHighSwept;

      if (idmOk) {
        if (isReversal) {
          lastEvent = 'CHoCH';
          continuationCount = 0;
        } else {
          continuationCount++;
          lastEvent = continuationCount === 1 ? 'SMS' : 'BMS';
        }
        lastEventSide = 'DOWN';
        direction = 'BEARISH';

        // Update extremes
        structureHighBarIndex = highestIdx;
        structureHigh = highestInRange;
        structureLowBarIndex = i;
        structureLow = c.l;
      }
    } else if (isHighBroken) {
      const priorDir = direction;
      const isReversal = priorDir !== 'BULLISH';

      const needIDM = cfg.idmEnabled && (
        (cfg.idmApplyTo === 'CHoCH' && isReversal) ||
        cfg.idmApplyTo === 'CHoCH+BOS'
      );
      const idmOk = !needIDM || idmLowSwept;

      if (idmOk) {
        if (isReversal) {
          lastEvent = 'CHoCH';
          continuationCount = 0;
        } else {
          continuationCount++;
          lastEvent = continuationCount === 1 ? 'SMS' : 'BMS';
        }
        lastEventSide = 'UP';
        direction = 'BULLISH';

        // Update extremes
        structureLowBarIndex = lowestIdx;
        structureLow = lowestInRange;
        structureHighBarIndex = i;
        structureHigh = c.h;
      }
    } else {
      // Trail the extreme points
      if (direction === 'BULLISH') {
        if (c.h > structureHigh) {
          structureHigh = c.h;
          structureHighBarIndex = i;
        }
      } else if (direction === 'BEARISH') {
        if (c.l < structureLow) {
          structureLow = c.l;
          structureLowBarIndex = i;
        }
      } else {
        // INIT
        if (c.h > structureHigh) {
          structureHigh = c.h;
          structureHighBarIndex = i;
        }
        if (c.l < structureLow) {
          structureLow = c.l;
          structureLowBarIndex = i;
        }
      }
    }
  }

  return {
    direction,
    structureHigh,
    structureLow,
    structureHighBarIndex,
    structureLowBarIndex,
    lastEvent,
    lastEventSide,
    continuationCount,
    idmHigh,
    idmLow,
    idmHighSwept,
    idmLowSwept,
  };
}

function defaultStructure(): MarketStructure {
  return {
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
  };
}

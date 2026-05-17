/**
 * V25.0 — WallProximityIndex
 *
 * Sorted-array index of walls per symbol. Allows O(log n) lookup of:
 *  - nearest wall above price (resistance for SELL setup)
 *  - nearest wall below price (support for BUY setup)
 *
 * Refreshed on V25_BAR_CLOSE for M5/M15/M30/H1 (the TFs that drive PriceMap).
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import type { PriceWall } from '../../analyzers/smc/types.js';
import { priceMapSnapshotProvider } from './PriceMapSnapshotProvider.js';
import { v25EventBus } from '../V25EventBus.js';
import type { MicroWallSnapshot, V25Event } from '../types.js';

export interface NearestWallResult {
  /** Wall above price, or null if none. */
  above: PriceWall | null;
  /** Wall below price, or null if none. */
  below: PriceWall | null;
  /** All resistance walls sorted ascending by price. */
  resistanceAsc: PriceWall[];
  /** All support walls sorted descending by price. */
  supportDesc: PriceWall[];
}

export class WallProximityIndex {
  private resistanceAsc = new Map<string, PriceWall[]>(); // sorted ascending by price
  private supportDesc = new Map<string, PriceWall[]>();   // sorted descending by price
  private lastRefreshTs = new Map<string, number>();
  private microWalls = new Map<string, MicroWallSnapshot>();
  private subscribed = false;
  // Phase 2.3: Wall Strength Decay
  private wallDecay = new Map<string, Map<number, number>>(); // symbol -> price -> decay amount

  public decayWall(symbol: string, price: number) {
    const upper = symbol.toUpperCase();
    let symbolDecays = this.wallDecay.get(upper);
    if (!symbolDecays) {
      symbolDecays = new Map<number, number>();
      this.wallDecay.set(upper, symbolDecays);
    }
    const currentDecay = symbolDecays.get(price) || 0;
    symbolDecays.set(price, currentDecay + 1);
  }

  public getTouchCount(symbol: string, price: number): number {
    return this.wallDecay.get(symbol.toUpperCase())?.get(price) || 0;
  }

  attach() {
    if (this.subscribed) return;
    this.subscribed = true;
    v25EventBus.on('V25_MICRO_WALL', (ev: V25Event) => {
      if (ev.type !== 'V25_MICRO_WALL') return;
      const mw = ev.payload as MicroWallSnapshot;
      this.microWalls.set(mw.symbol.toUpperCase(), mw);
    });
  }

  /** Pull fresh data from V21 PriceMap and rebuild sorted arrays. */
  refresh(symbol: string): boolean {
    const upper = (symbol || '').toUpperCase();
    const snap = priceMapSnapshotProvider.get(upper);
    if (!snap.priceMap) return false;

    const res = [...snap.priceMap.resistanceWalls].sort((a, b) => a.price - b.price);
    const sup = [...snap.priceMap.supportWalls].sort((a, b) => b.price - a.price);
    this.resistanceAsc.set(upper, res);
    this.supportDesc.set(upper, sup);
    this.lastRefreshTs.set(upper, Date.now());
    return true;
  }

  /** Lookup nearest above & below (price defines the divider). */
  query(symbol: string, price: number): NearestWallResult {
    const upper = (symbol || '').toUpperCase();
    const res = this.resistanceAsc.get(upper) ?? [];
    const sup = this.supportDesc.get(upper) ?? [];

    const micro = this.microWalls.get(upper);

    // Convert micro wall to PriceWall format if available
    let microRes: PriceWall | null = null;
    let microSup: PriceWall | null = null;
    if (micro) {
      if (micro.microHigh > price) {
        microRes = this.createMicroPriceWall(micro.microHigh);
      }
      if (micro.microLow < price) {
        microSup = this.createMicroPriceWall(micro.microLow);
      }
    }

    // Determine the closest above
    let above = this.firstAboveAsc(res, price);
    if (microRes && (!above || microRes.price < above.price)) {
      above = microRes;
    } else if (microRes && above && microRes.price === above.price) {
      // If same price, the main wall takes precedence as it has more context
    }

    // Determine the closest below
    let below = this.firstAtOrBelowDesc(sup, price);
    if (microSup && (!below || microSup.price > below.price)) {
      below = microSup;
    } else if (microSup && below && microSup.price === below.price) {
      // If same price, main wall takes precedence
    }

    return {
      above,
      below,
      resistanceAsc: res,
      supportDesc: sup,
    };
  }

  private createMicroPriceWall(price: number): PriceWall {
    return {
      price,
      priceTop: price,
      priceBottom: price,
      side: 'BOTH',
      confluenceStars: 1,
      timeframes: ['M1'],
      sources: [{
        type: 'SWING_HIGH', // generic type for micro pivot
        timeframe: 'M1',
        priceTop: price,
        priceBottom: price,
      }],
      hasOB: false,
      hasFVG: false,
      hasLiquidity: false,
      isStructure: false,
      label: `${price.toFixed(2)} ★  M1_MICRO`,
      distanceFromPrice: 0,
      distancePct: 0,
    };
  }

  /**
   * V25.1 — pick the STRONGEST wall (max confluenceStars) within `radius` of price.
   * Prevents PB1 from latching onto a 1★ wall when a 5★ wall sits a few ticks away.
   * Returns null if no wall on that side meets minStars within the radius.
   */
  queryStrongest(
    symbol: string,
    price: number,
    radius: number,
    minStars: number,
  ): { above: PriceWall | null; below: PriceWall | null } {
    const upper = (symbol || '').toUpperCase();
    const res = this.resistanceAsc.get(upper) ?? [];
    const sup = this.supportDesc.get(upper) ?? [];

    const aboveCandidates = res.filter(w => w.price > price && w.price - price <= radius && (w.confluenceStars ?? 0) >= minStars);
    const belowCandidates = sup.filter(w => w.price < price && price - w.price <= radius && (w.confluenceStars ?? 0) >= minStars);

    const pickStrongest = (arr: PriceWall[]): PriceWall | null => {
      if (arr.length === 0) return null;
      return arr.reduce((best, cur) =>
        (cur.confluenceStars ?? 0) > (best.confluenceStars ?? 0) ? cur : best, arr[0]);
    };

    return {
      above: pickStrongest(aboveCandidates),
      below: pickStrongest(belowCandidates),
    };
  }

  /** Binary-search first element with price > target. */
  private firstAboveAsc(arr: PriceWall[], target: number): PriceWall | null {
    if (arr.length === 0) return null;
    let lo = 0, hi = arr.length;
    while (lo < hi) {
      const mid = (lo + hi) >>> 1;
      if (arr[mid].price > target) hi = mid;
      else lo = mid + 1;
    }
    return lo < arr.length ? arr[lo] : null;
  }

  /** Binary-search first element with price <= target (sorted descending). */
  private firstAtOrBelowDesc(arr: PriceWall[], target: number): PriceWall | null {
    if (arr.length === 0) return null;
    let lo = 0, hi = arr.length;
    while (lo < hi) {
      const mid = (lo + hi) >>> 1;
      if (arr[mid].price <= target) hi = mid;
      else lo = mid + 1;
    }
    return lo < arr.length ? arr[lo] : null;
  }

  status() {
    const symbols: Array<{ symbol: string; resistance: number; support: number; lastRefreshTs: number }> = [];
    for (const sym of new Set([...this.resistanceAsc.keys(), ...this.supportDesc.keys()])) {
      symbols.push({
        symbol: sym,
        resistance: this.resistanceAsc.get(sym)?.length ?? 0,
        support: this.supportDesc.get(sym)?.length ?? 0,
        lastRefreshTs: this.lastRefreshTs.get(sym) ?? 0,
      });
    }
    return { symbols };
  }
}

export const wallProximityIndex = new WallProximityIndex();

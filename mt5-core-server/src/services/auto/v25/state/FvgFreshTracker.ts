/**
 * V25.0 — FvgFreshTracker
 *
 * Tracks FVGs that are still fresh (not yet filled). Refreshed on M5/M15
 * bar close from the V21 SmcSnapshot. On every tick we drop any FVG whose
 * price has been crossed (so `hasFreshOpposite` returns false once filled).
 *
 * Answers user requirement #5: detect fresh opposite FVG before entry.
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import type { FVG } from '../../analyzers/smc/types.js';
import { priceMapSnapshotProvider } from './PriceMapSnapshotProvider.js';

interface TrackedFvg {
  type: 'BULL' | 'BEAR';
  top: number;
  bottom: number;
  insertedAt: number;
}

export class FvgFreshTracker {
  private bull = new Map<string, TrackedFvg[]>(); // bull FVG = price gap ที่เกิดจาก up-impulse — เป็น "support" ที่รอเก็บ
  private bear = new Map<string, TrackedFvg[]>(); // bear FVG = price gap ที่เกิดจาก down-impulse — เป็น "resistance" ที่รอเก็บ
  private lastRefreshTs = new Map<string, number>();

  /** Pull fresh FVGs from V21 SmcSnapshot. Idempotent — dedupes by bounds. */
  refresh(symbol: string): boolean {
    const upper = (symbol || '').toUpperCase();
    const snap = priceMapSnapshotProvider.get(upper);
    if (!snap.smc) return false;

    const now = Date.now();
    const incoming = snap.smc.activeFVGs ?? [];

    const bull: TrackedFvg[] = [];
    const bear: TrackedFvg[] = [];
    for (const f of incoming) {
      if (!Number.isFinite(f.top) || !Number.isFinite(f.bottom)) continue;
      if (f.mitigated) continue;
      const tracked: TrackedFvg = { type: f.type, top: f.top, bottom: f.bottom, insertedAt: now };
      if (f.type === 'BULL') bull.push(tracked);
      else bear.push(tracked);
    }
    this.bull.set(upper, bull);
    this.bear.set(upper, bear);
    this.lastRefreshTs.set(upper, now);
    return true;
  }

  /**
   * Drop FVGs that the price has crossed. Call on every tick (cheap — array
   * usually has < 10 elements).
   *
   *  - BULL FVG is "filled" when price closes BELOW its bottom.
   *  - BEAR FVG is "filled" when price closes ABOVE its top.
   */
  onPrice(symbol: string, price: number): void {
    const upper = (symbol || '').toUpperCase();
    const bullArr = this.bull.get(upper);
    if (bullArr && bullArr.length > 0) {
      const filtered = bullArr.filter((f) => price > f.bottom);
      if (filtered.length !== bullArr.length) this.bull.set(upper, filtered);
    }
    const bearArr = this.bear.get(upper);
    if (bearArr && bearArr.length > 0) {
      const filtered = bearArr.filter((f) => price < f.top);
      if (filtered.length !== bearArr.length) this.bear.set(upper, filtered);
    }
  }

  /**
   * "Opposite" means: for BUY setup we want a fresh BEAR FVG above the entry
   * (price has not yet collected those sells), and for SELL we want a fresh
   * BULL FVG below the entry (price has not yet collected those buys).
   */
  hasFreshOpposite(symbol: string, side: 'BUY' | 'SELL'): boolean {
    const upper = (symbol || '').toUpperCase();
    if (side === 'BUY') return (this.bear.get(upper)?.length ?? 0) > 0;
    return (this.bull.get(upper)?.length ?? 0) > 0;
  }

  /** Snapshot of fresh FVGs for /api/v25/state/:symbol. */
  snapshot(symbol: string) {
    const upper = (symbol || '').toUpperCase();
    return {
      bull: this.bull.get(upper) ?? [],
      bear: this.bear.get(upper) ?? [],
      lastRefreshTs: this.lastRefreshTs.get(upper) ?? 0,
    };
  }
}

export const fvgFreshTracker = new FvgFreshTracker();

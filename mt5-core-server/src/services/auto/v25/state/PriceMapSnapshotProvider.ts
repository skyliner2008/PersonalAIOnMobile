/**
 * V25.0 — PriceMapSnapshotProvider
 *
 * Read-only adapter over the existing V21.1 IndicatorPipeline. Returns the
 * latest PriceMap + SMC snapshot for a symbol, plus a freshness flag.
 *
 * Phase B uses this to feed walls into WallProximityIndex without coupling
 * V25 to the V24 pipeline lifecycle.
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import { indicatorPipeline } from '../../core/IndicatorPipeline.js';
import type { PriceMap, SmcSnapshot } from '../../analyzers/smc/types.js';

export interface PriceMapSnapshot {
  symbol: string;
  /** PriceMap from V21.1 IndicatorPipeline, or null if not yet built. */
  priceMap: PriceMap | null;
  /** Primary SMC snapshot (currently first TF, typically M5/M15). */
  smc: SmcSnapshot | null;
  /** Snapshot timestamp from PriceMap, or 0 if missing. */
  ts: number;
  /** Age of snapshot in ms; Infinity if missing. */
  ageMs: number;
}

export class PriceMapSnapshotProvider {
  /** Maximum acceptable age before we treat snapshot as stale (default 90s). */
  staleMs: number;

  constructor(staleMs = 90_000) {
    this.staleMs = staleMs;
  }

  get(symbol: string): PriceMapSnapshot {
    const upper = (symbol || '').toUpperCase();
    const pm = indicatorPipeline.getPriceMap(upper);
    const smc = indicatorPipeline.getSmcSnapshot(upper);
    const ts = pm?.timestamp ?? 0;
    const ageMs = ts > 0 ? Date.now() - ts : Number.POSITIVE_INFINITY;
    return { symbol: upper, priceMap: pm, smc, ts, ageMs };
  }

  isFresh(symbol: string): boolean {
    const s = this.get(symbol);
    return s.priceMap != null && s.ageMs < this.staleMs;
  }
}

export const priceMapSnapshotProvider = new PriceMapSnapshotProvider();

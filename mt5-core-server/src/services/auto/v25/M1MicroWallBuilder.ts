/**
 * V25.0 — M1MicroWallBuilder
 *
 * Computes a rolling micro-pivot from the tick buffer every 1s. The output
 * (microHigh/microLow over a sliding window) is the "M1 micro-wall" that
 * answers user requirement #1: "5 ticks in M1 form a wall on M5".
 *
 * Phase A: emit V25_MICRO_WALL events on a 1-second timer per tracked
 * symbol. Phase B will feed these into the PriceMap as `M1_MICRO` tag.
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import { atLog } from '../utils.js';
import { tickBuffers } from './TickBuffer.js';
import { v25EventBus } from './V25EventBus.js';
import type { MicroWallSnapshot } from './types.js';

export interface MicroWallOptions {
  /** Sampling cadence in ms. Default 1000ms. */
  intervalMs?: number;
  /** Window size in ms. Default 60_000 (1 minute). */
  windowMs?: number;
  /** Min tick count to emit (avoid noise during cold start). */
  minTickCount?: number;
}

export class M1MicroWallBuilder {
  private symbols = new Set<string>();
  private timers = new Map<string, NodeJS.Timeout>();
  private opts: Required<MicroWallOptions>;
  private running = false;
  private lastEmittedHigh = new Map<string, number>();
  private lastEmittedLow = new Map<string, number>();

  constructor(opts: MicroWallOptions = {}) {
    this.opts = {
      intervalMs: opts.intervalMs ?? 3000,
      windowMs: opts.windowMs ?? 60_000,
      minTickCount: opts.minTickCount ?? 5,
    };
  }

  trackSymbol(symbol: string): void {
    const upper = (symbol || '').toUpperCase();
    if (!upper || this.symbols.has(upper)) return;
    this.symbols.add(upper);
    if (this.running) this.scheduleNext(upper);
  }

  untrackSymbol(symbol: string): void {
    const upper = (symbol || '').toUpperCase();
    this.symbols.delete(upper);
    const t = this.timers.get(upper);
    if (t) clearTimeout(t);
    this.timers.delete(upper);
  }

  start(): void {
    if (this.running) return;
    this.running = true;
    for (const sym of this.symbols) this.scheduleNext(sym);
    atLog(`[V25] M1MicroWallBuilder started — window=${this.opts.windowMs}ms interval=${this.opts.intervalMs}ms`);
  }

  stop(): void {
    this.running = false;
    for (const t of this.timers.values()) clearTimeout(t);
    this.timers.clear();
  }

  status() {
    return {
      running: this.running,
      symbols: Array.from(this.symbols),
      ...this.opts,
    };
  }

  private scheduleNext(symbol: string): void {
    if (!this.running) return;
    const old = this.timers.get(symbol);
    if (old) clearTimeout(old);

    const t = setTimeout(() => {
      void this.computeOnce(symbol).finally(() => {
        if (this.running) this.scheduleNext(symbol);
      });
    }, this.opts.intervalMs);
    this.timers.set(symbol, t);
  }

  private async computeOnce(symbol: string): Promise<void> {
    const buf = tickBuffers.get(symbol);
    const now = Date.now();
    const since = now - this.opts.windowMs;
    const ticks = buf.sinceMs(since);
    if (ticks.length < this.opts.minTickCount) return;

    let microHigh = -Infinity;
    let microLow = Infinity;
    for (const t of ticks) {
      if (t.mid > microHigh) microHigh = t.mid;
      if (t.mid < microLow) microLow = t.mid;
    }
    if (!Number.isFinite(microHigh) || !Number.isFinite(microLow)) return;

    // Skip emit if levels are unchanged (cheap dedupe — saves bus chatter)
    const prevH = this.lastEmittedHigh.get(symbol);
    const prevL = this.lastEmittedLow.get(symbol);
    const epsilon = (microHigh - microLow) * 0.0001 + 1e-6;
    if (prevH !== undefined && prevL !== undefined &&
        Math.abs(prevH - microHigh) < epsilon &&
        Math.abs(prevL - microLow) < epsilon) {
      return;
    }
    this.lastEmittedHigh.set(symbol, microHigh);
    this.lastEmittedLow.set(symbol, microLow);

    const snap: MicroWallSnapshot = {
      symbol,
      ts: now,
      microHigh,
      microLow,
      tickCount: ticks.length,
      windowMs: this.opts.windowMs,
    };
    await v25EventBus.emitMicroWall(symbol, snap);
  }
}

export const m1MicroWallBuilder = new M1MicroWallBuilder();

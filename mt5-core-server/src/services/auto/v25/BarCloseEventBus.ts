/**
 * V25.0 — BarCloseDetector
 *
 * Clock-aligned bar-close emitter. Each TF runs on its own setInterval but
 * is anchored to wall-clock minute boundaries (so M1 fires exactly at xx:00,
 * M5 at xx:00 / xx:05 / xx:10, etc).
 *
 * For Phase A this is a "soft" clock — we emit BAR_CLOSE based on local
 * clock alone. Phase B will cross-check by polling /candles to confirm the
 * broker actually has a fresh bar (avoids edge cases on weekends/holidays).
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import { atLog, atWarn } from '../utils.js';
import { v25EventBus } from './V25EventBus.js';
import type { BarCloseEvent, V25Timeframe } from './types.js';

const TF_MS: Record<V25Timeframe, number> = {
  M1: 60_000,
  M5: 5 * 60_000,
  M15: 15 * 60_000,
  M30: 30 * 60_000,
  H1: 60 * 60_000,
  H4: 4 * 60 * 60_000,
};

export class BarCloseDetector {
  private timers = new Map<string, NodeJS.Timeout>(); // key = `${symbol}:${tf}`
  private symbols = new Set<string>();
  private timeframes: V25Timeframe[];
  private running = false;
  private lastEmittedAt = new Map<string, number>(); // dedupe within 5s

  constructor(timeframes: V25Timeframe[] = ['M1', 'M5', 'M15', 'M30', 'H1', 'H4']) {
    this.timeframes = timeframes;
  }

  trackSymbol(symbol: string): void {
    const upper = (symbol || '').toUpperCase();
    if (!upper) return;
    if (this.symbols.has(upper)) return;
    this.symbols.add(upper);
    if (this.running) this.scheduleAll(upper);
  }

  untrackSymbol(symbol: string): void {
    const upper = (symbol || '').toUpperCase();
    this.symbols.delete(upper);
    for (const tf of this.timeframes) {
      const key = `${upper}:${tf}`;
      const t = this.timers.get(key);
      if (t) clearTimeout(t);
      this.timers.delete(key);
    }
  }

  start(): void {
    if (this.running) return;
    this.running = true;
    for (const sym of this.symbols) this.scheduleAll(sym);
    atLog(`[V25] BarCloseDetector started for ${this.symbols.size} symbol(s) × ${this.timeframes.length} TFs`);
  }

  stop(): void {
    this.running = false;
    for (const t of this.timers.values()) clearTimeout(t);
    this.timers.clear();
  }

  private scheduleAll(symbol: string): void {
    for (const tf of this.timeframes) this.scheduleNext(symbol, tf);
  }

  private scheduleNext(symbol: string, tf: V25Timeframe): void {
    if (!this.running) return;
    const key = `${symbol}:${tf}`;
    const oldTimer = this.timers.get(key);
    if (oldTimer) clearTimeout(oldTimer);

    const tfMs = TF_MS[tf];
    const now = Date.now();
    // align to next TF boundary (UTC). add 50 ms buffer so broker has time to seal the bar.
    const nextBoundary = Math.ceil(now / tfMs) * tfMs;
    const wait = Math.max(50, nextBoundary - now + 50);

    const timer = setTimeout(() => {
      this.fire(symbol, tf, nextBoundary).catch((e) => {
        atWarn(`[V25] bar-close fire failed ${symbol}/${tf}: ${e}`);
      });
      // schedule next iteration
      this.scheduleNext(symbol, tf);
    }, wait);

    this.timers.set(key, timer);
  }

  private async fire(symbol: string, tf: V25Timeframe, boundaryMs: number): Promise<void> {
    const key = `${symbol}:${tf}:${boundaryMs}`;
    if (this.lastEmittedAt.has(key)) return;
    this.lastEmittedAt.set(key, Date.now());
    // GC old keys
    if (this.lastEmittedAt.size > 200) {
      const cutoff = Date.now() - 60_000;
      for (const [k, ts] of this.lastEmittedAt) {
        if (ts < cutoff) this.lastEmittedAt.delete(k);
      }
    }

    const evt: BarCloseEvent = {
      symbol,
      tf,
      barOpenTs: boundaryMs - TF_MS[tf],
      barCloseTs: boundaryMs,
    };
    await v25EventBus.emitBarClose(symbol, evt);
  }

  status() {
    return {
      running: this.running,
      symbols: Array.from(this.symbols),
      timeframes: this.timeframes,
      activeTimers: this.timers.size,
    };
  }
}

export const barCloseDetector = new BarCloseDetector();

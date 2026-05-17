/**
 * V25.0 — TickBuffer
 *
 * In-memory ring buffer per symbol. Stores last N ticks (default 600 ≈ 5 min
 * at 200 ms throttle). Supports binary-search lastN, high/low scan.
 *
 * Throttle policy: caller (TickGateway) decides whether to push or drop.
 * TickBuffer itself is dumb storage.
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import type { Tick, TickBufferStats } from './types.js';

export class TickBuffer {
  private buf: Tick[] = [];
  private head = 0; // next write index
  private full = false;
  public readonly capacity: number;
  public readonly symbol: string;

  constructor(symbol: string, capacity = 600) {
    this.symbol = (symbol || '').toUpperCase();
    this.capacity = Math.max(10, capacity);
    this.buf = new Array(this.capacity);
  }

  /** Push a tick. Returns true if accepted. */
  push(t: Tick): boolean {
    if (!t || !Number.isFinite(t.bid) || !Number.isFinite(t.ask)) return false;
    this.buf[this.head] = t;
    this.head = (this.head + 1) % this.capacity;
    if (this.head === 0) this.full = true;
    return true;
  }

  /** Current size. */
  size(): number {
    return this.full ? this.capacity : this.head;
  }

  /** Get the most recent tick, or null. */
  last(): Tick | null {
    const sz = this.size();
    if (sz === 0) return null;
    const idx = (this.head - 1 + this.capacity) % this.capacity;
    return this.buf[idx] ?? null;
  }

  /** Last N ticks ordered oldest → newest. */
  lastN(n: number): Tick[] {
    const sz = this.size();
    const k = Math.max(0, Math.min(n, sz));
    if (k === 0) return [];
    const out: Tick[] = new Array(k);
    for (let i = 0; i < k; i++) {
      // newest is at head-1; we want oldest first
      const offset = sz - k + i;
      const idx = this.full
        ? (this.head + offset) % this.capacity
        : offset;
      out[i] = this.buf[idx];
    }
    return out;
  }

  /** Highest mid in last N ticks. */
  highN(n: number): number | null {
    const arr = this.lastN(n);
    if (arr.length === 0) return null;
    let h = -Infinity;
    for (const t of arr) if (t.mid > h) h = t.mid;
    return Number.isFinite(h) ? h : null;
  }

  /** Lowest mid in last N ticks. */
  lowN(n: number): number | null {
    const arr = this.lastN(n);
    if (arr.length === 0) return null;
    let l = Infinity;
    for (const t of arr) if (t.mid < l) l = t.mid;
    return Number.isFinite(l) ? l : null;
  }

  /** Ticks newer than the given timestamp (ms). */
  sinceMs(sinceMs: number): Tick[] {
    const all = this.lastN(this.size());
    return all.filter((t) => t.recvTs >= sinceMs);
  }

  /** Stats snapshot (for /api/v25/health). */
  stats(): TickBufferStats {
    const sz = this.size();
    if (sz === 0) {
      return {
        symbol: this.symbol,
        size: 0,
        capacity: this.capacity,
        oldestTs: null,
        newestTs: null,
        latencyMs: null,
      };
    }
    const last = this.last();
    const oldest = this.full
      ? this.buf[this.head] // wrap point is the oldest
      : this.buf[0];
    return {
      symbol: this.symbol,
      size: sz,
      capacity: this.capacity,
      oldestTs: oldest?.recvTs ?? null,
      newestTs: last?.recvTs ?? null,
      latencyMs: last ? last.recvTs - last.srcTs : null,
    };
  }

  clear(): void {
    this.buf = new Array(this.capacity);
    this.head = 0;
    this.full = false;
  }
}

/** Registry — one buffer per symbol. */
export class TickBufferRegistry {
  private buffers = new Map<string, TickBuffer>();
  private defaultCapacity: number;

  constructor(defaultCapacity = 600) {
    this.defaultCapacity = defaultCapacity;
  }

  get(symbol: string): TickBuffer {
    const upper = (symbol || '').toUpperCase();
    let buf = this.buffers.get(upper);
    if (!buf) {
      buf = new TickBuffer(upper, this.defaultCapacity);
      this.buffers.set(upper, buf);
    }
    return buf;
  }

  has(symbol: string): boolean {
    return this.buffers.has((symbol || '').toUpperCase());
  }

  symbols(): string[] {
    return Array.from(this.buffers.keys());
  }

  allStats(): TickBufferStats[] {
    return Array.from(this.buffers.values()).map((b) => b.stats());
  }

  clearAll(): void {
    for (const b of this.buffers.values()) b.clear();
  }
}

export const tickBuffers = new TickBufferRegistry(600);

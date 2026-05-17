/**
 * V25.0 — V25EventBus
 *
 * Dedicated event bus for V25 layers (separate from existing core/EventBus.ts
 * to avoid coupling with V21–V24 trading events). Future Phase D may merge.
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import { atLog, atWarn } from '../utils.js';
import type {
  BarCloseEvent,
  MicroWallSnapshot,
  Tick,
  V25Event,
  V25EventType,
  WallStateEvent,
} from './types.js';

export type V25Listener = (ev: V25Event) => void | Promise<void>;

class V25EventBusImpl {
  private listeners = new Map<V25EventType, Set<V25Listener>>();
  private logCount = 0;
  private logSuppressUntil = 0;

  on(type: V25EventType, l: V25Listener): () => void {
    let set = this.listeners.get(type);
    if (!set) {
      set = new Set();
      this.listeners.set(type, set);
    }
    set.add(l);
    return () => set!.delete(l);
  }

  async emit(ev: V25Event): Promise<void> {
    const set = this.listeners.get(ev.type);
    if (!set || set.size === 0) return;

    // Throttle log spam — at most ~1 log/sec per event type
    const now = Date.now();
    if (now > this.logSuppressUntil && (ev.type === 'V25_WALL_STATE_CHANGE' || ev.type === 'V25_BAR_CLOSE' || ev.type === 'V25_STALE_TICK')) {
      atLog(`[V25] ${ev.type} ${ev.symbol}`);
      this.logSuppressUntil = now + 1000;
    }
    this.logCount++;

    const results = await Promise.allSettled(
      Array.from(set).map((fn) => Promise.resolve().then(() => fn(ev)))
    );
    for (const r of results) {
      if (r.status === 'rejected') {
        atWarn(`[V25] listener error on ${ev.type}: ${(r as PromiseRejectedResult).reason}`);
      }
    }
  }

  // Helpers for type-safe emit
  emitTick(symbol: string, tick: Tick): Promise<void> {
    return this.emit({ type: 'V25_TICK', symbol, ts: tick.recvTs, payload: tick });
  }
  emitBarClose(symbol: string, bar: BarCloseEvent): Promise<void> {
    return this.emit({ type: 'V25_BAR_CLOSE', symbol, ts: bar.barCloseTs, payload: bar });
  }
  emitMicroWall(symbol: string, mw: MicroWallSnapshot): Promise<void> {
    return this.emit({ type: 'V25_MICRO_WALL', symbol, ts: mw.ts, payload: mw });
  }
  emitWallStateChange(symbol: string, ws: WallStateEvent): Promise<void> {
    return this.emit({ type: 'V25_WALL_STATE_CHANGE', symbol, ts: ws.ts, payload: ws });
  }
  emitStaleTick(symbol: string, reason: string, ageMs: number): Promise<void> {
    return this.emit({ type: 'V25_STALE_TICK', symbol, ts: Date.now(), payload: { reason, ageMs } });
  }

  stats(): { types: V25EventType[]; listenerCount: number; emittedCount: number } {
    let total = 0;
    const types: V25EventType[] = [];
    for (const [t, s] of this.listeners) {
      types.push(t);
      total += s.size;
    }
    return { types, listenerCount: total, emittedCount: this.logCount };
  }
}

export const v25EventBus = new V25EventBusImpl();

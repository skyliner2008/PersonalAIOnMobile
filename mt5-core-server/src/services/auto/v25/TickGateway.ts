/**
 * V25.0 — TickGateway
 *
 * Subscribes to MT5 broker tick stream. Phase A: REST polling fallback via
 * `/symbol_info?symbol=X` (which already returns the latest tick + bid/ask).
 * Phase B will add native WS subscription on the bridge side.
 *
 * Per-symbol async loop with throttle. Each successful poll → push to
 * TickBuffer + emit V25_TICK. If `srcTs` is older than `staleThresholdMs`
 * we emit V25_STALE_TICK (state machine will freeze on this signal).
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import { callBridge } from '../../bridgeClient.js';
import { atLog, atWarn, asNumber, asRecord, unwrapData } from '../utils.js';
import { tickBuffers } from './TickBuffer.js';
import { v25EventBus } from './V25EventBus.js';
import type { Tick } from './types.js';

export interface TickGatewayOptions {
  /** Polling interval per symbol in ms. Default 1000ms (shadow-mode safe). */
  pollIntervalMs?: number;
  /** Tick is "stale" if srcTs older than this many ms. Default 5000. */
  staleThresholdMs?: number;
  /** If true, log every successful tick (verbose; default false). */
  verboseTickLog?: boolean;
  /**
   * Min interval between V25_STALE_TICK emits per symbol (ms). Prevents log
   * spam when a symbol's market is closed and srcTs sits stale for hours.
   * Default 30s.
   */
  staleEmitMinIntervalMs?: number;
  /**
   * Adaptive back-off: once we see this many consecutive stale polls for a
   * symbol, scale the polling interval up by `backoffMultiplier` until a
   * fresh tick arrives. Default trigger=5 polls, multiplier=30, cap=60s.
   */
  staleBackoffTriggerCount?: number;
  staleBackoffMultiplier?: number;
  staleBackoffCapMs?: number;
}

export class TickGateway {
  private symbols = new Set<string>();
  private timers = new Map<string, NodeJS.Timeout>();
  private inFlight = new Set<string>();
  private opts: Required<TickGatewayOptions>;
  private running = false;
  private stats = {
    polls: 0,
    accepted: 0,
    rejectedStale: 0,
    failures: 0,
    lastFailureMs: 0,
  };
  // for de-duplication: skip if (srcTs unchanged) — prevents pushing the same broker tick twice
  private lastSrcTs = new Map<string, number>();
  // throttle V25_STALE_TICK emit per symbol (Date.now() of last emit)
  private lastStaleEmitMs = new Map<string, number>();
  // consecutive stale poll counter — drives adaptive back-off
  private consecutiveStale = new Map<string, number>();
  // current effective poll interval (mutated by back-off)
  private effectivePollMs = new Map<string, number>();

  constructor(opts: TickGatewayOptions = {}) {
    this.opts = {
      pollIntervalMs: opts.pollIntervalMs ?? 3000,
      staleThresholdMs: opts.staleThresholdMs ?? 10000,
      verboseTickLog: opts.verboseTickLog ?? false,
      staleEmitMinIntervalMs: opts.staleEmitMinIntervalMs ?? 30_000,
      staleBackoffTriggerCount: opts.staleBackoffTriggerCount ?? 5,
      staleBackoffMultiplier: opts.staleBackoffMultiplier ?? 30,
      staleBackoffCapMs: opts.staleBackoffCapMs ?? 60_000,
    };
  }

  trackSymbol(symbol: string): void {
    const upper = (symbol || '').toUpperCase();
    if (!upper || this.symbols.has(upper)) return;
    this.symbols.add(upper);
    tickBuffers.get(upper); // pre-create buffer
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
    atLog(`[V25] TickGateway started — polling ${this.symbols.size} symbol(s) every ${this.opts.pollIntervalMs}ms`);
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
      pollIntervalMs: this.opts.pollIntervalMs,
      staleThresholdMs: this.opts.staleThresholdMs,
      stats: { ...this.stats },
    };
  }

  // ── internals ──────────────────────────────────────────────────────

  private scheduleNext(symbol: string): void {
    if (!this.running) return;
    const old = this.timers.get(symbol);
    if (old) clearTimeout(old);

    // Adaptive poll interval — if symbol is in stale back-off, slow down
    const interval = this.effectivePollMs.get(symbol) ?? this.opts.pollIntervalMs;

    const t = setTimeout(() => {
      void this.pollOnce(symbol).finally(() => {
        // Re-schedule regardless of success/failure
        if (this.running) this.scheduleNext(symbol);
      });
    }, interval);
    this.timers.set(symbol, t);
  }

  private async pollOnce(symbol: string): Promise<void> {
    if (this.inFlight.has(symbol)) return; // skip if previous poll still running
    this.inFlight.add(symbol);
    this.stats.polls++;
    const recvTs = Date.now();
    try {
      const result = await callBridge('GET', ['/symbol_info'], {
        query: { symbol },
        timeoutMs: 4000,
        priority: 'low',
      });
      const outer = asRecord(result.data);
      const inner = asRecord((outer as any).data ?? unwrapData(result.data));
      const info = Object.keys(inner).length > 0 ? inner : outer;

      const bid = asNumber((info as any).bid);
      const ask = asNumber((info as any).ask);
      const srcTimeSec = asNumber((info as any).time);
      if (!Number.isFinite(bid) || !Number.isFinite(ask) || bid <= 0 || ask <= 0) {
        // unusable data
        this.stats.failures++;
        return;
      }
      const srcTs = srcTimeSec > 0 ? Math.round(srcTimeSec * 1000) : recvTs;
      const ageMs = recvTs - srcTs;

      // Stale tick — emit warning but DO NOT push (downstream uses last known)
      if (ageMs > this.opts.staleThresholdMs) {
        this.stats.rejectedStale++;

        // Track consecutive stale → escalate back-off so we don't burn CPU/log
        const prevCount = this.consecutiveStale.get(symbol) ?? 0;
        const nextCount = prevCount + 1;
        this.consecutiveStale.set(symbol, nextCount);
        if (nextCount >= this.opts.staleBackoffTriggerCount) {
          const backoffMs = Math.min(
            this.opts.pollIntervalMs * this.opts.staleBackoffMultiplier,
            this.opts.staleBackoffCapMs,
          );
          if (this.effectivePollMs.get(symbol) !== backoffMs) {
            this.effectivePollMs.set(symbol, backoffMs);
            atLog(`[V25] TickGateway back-off ${symbol} → poll every ${backoffMs}ms (market likely closed)`);
          }
        }

        // Throttle V25_STALE_TICK emit — at most once per staleEmitMinIntervalMs per symbol
        const lastEmit = this.lastStaleEmitMs.get(symbol) ?? 0;
        if (recvTs - lastEmit >= this.opts.staleEmitMinIntervalMs) {
          this.lastStaleEmitMs.set(symbol, recvTs);
          await v25EventBus.emitStaleTick(symbol, `srcTs ${ageMs}ms older than threshold ${this.opts.staleThresholdMs}ms (${nextCount} consecutive)`, ageMs);
        }
        return;
      }

      // Fresh tick → reset back-off
      if (this.consecutiveStale.get(symbol)) {
        this.consecutiveStale.set(symbol, 0);
        if (this.effectivePollMs.get(symbol) !== this.opts.pollIntervalMs) {
          this.effectivePollMs.set(symbol, this.opts.pollIntervalMs);
          atLog(`[V25] TickGateway resumed normal polling for ${symbol} (fresh tick)`);
        }
      }

      // Dedupe — same broker srcTs we already saw
      const prev = this.lastSrcTs.get(symbol);
      if (prev && prev === srcTs) return;
      this.lastSrcTs.set(symbol, srcTs);

      const tick: Tick = {
        symbol,
        srcTs,
        recvTs,
        bid,
        ask,
        mid: (bid + ask) / 2,
        spread: Math.max(0, ask - bid),
      };

      const accepted = tickBuffers.get(symbol).push(tick);
      if (!accepted) return;
      this.stats.accepted++;
      if (this.opts.verboseTickLog) {
        atLog(`[V25] tick ${symbol} bid=${bid} ask=${ask} age=${ageMs}ms`);
      }
      await v25EventBus.emitTick(symbol, tick);
    } catch (err: any) {
      this.stats.failures++;
      this.stats.lastFailureMs = Date.now();
      atWarn(`[V25] TickGateway poll ${symbol} failed: ${err?.message ?? err}`);
    } finally {
      this.inFlight.delete(symbol);
    }
  }
}

export const tickGateway = new TickGateway();

/**
 * V25.0 — Phase A bootstrap & shadow-mode wiring
 *
 * Wires the four Phase-A modules together and exposes a single entry point
 * `bootstrapV25Shadow()` to be called from `src/index.ts`.
 *
 * Phase A behaviour:
 *  - Track default symbols (XBTUSD, XAUUSD).
 *  - TickGateway polls /symbol_info every 1000ms (configurable via env).
 *  - BarCloseDetector emits clock-aligned BAR_CLOSE events (M1..H4).
 *  - M1MicroWallBuilder samples micro highs/lows every 1000ms.
 *  - All events flow through V25EventBus only — no order placement, no
 *    interaction with V24 AutoTradingEngine.
 *  - Shadow logger subscribes and counts events for /api/v25/health.
 *
 * Env knobs:
 *   V25_SHADOW=true|false        (default false — opt-in)
 *   V25_SYMBOLS=XBTUSD,XAUUSD    (csv)
 *   V25_TICK_POLL_MS=1000
 *   V25_STALE_MS=5000
 *   V25_VERBOSE_TICK=false
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import { atLog } from '../utils.js';
import { barCloseDetector } from './BarCloseEventBus.js';
import { m1MicroWallBuilder } from './M1MicroWallBuilder.js';
import { TickGateway, tickGateway } from './TickGateway.js';
import { tickBuffers } from './TickBuffer.js';
import { v25EventBus } from './V25EventBus.js';
import type { V25Event } from './types.js';
// Phase B
import { wallProximityIndex } from './state/WallProximityIndex.js';
import { wallStateMachine } from './state/WallStateMachine.js';
import { fvgFreshTracker } from './state/FvgFreshTracker.js';
import { priceMapSnapshotProvider } from './state/PriceMapSnapshotProvider.js';
// Phase C
import { playbookSelector } from './playbooks/index.js';

interface ShadowState {
  enabled: boolean;
  startedAt: number | null;
  symbols: string[];
  tickGateway: TickGateway;
  counters: {
    tick: number;
    barClose: number;
    microWall: number;
    staleTick: number;
    wallStateChange: number;
  };
  lastEvents: V25Event[]; // ring of last 50 events for debugging
}

const state: ShadowState = {
  enabled: false,
  startedAt: null,
  symbols: [],
  tickGateway: tickGateway,
  counters: {
    tick: 0,
    barClose: 0,
    microWall: 0,
    staleTick: 0,
    wallStateChange: 0,
  },
  lastEvents: [],
};

function readEnvBool(name: string, dflt: boolean): boolean {
  const v = (process.env[name] ?? '').toString().trim().toLowerCase();
  if (v === '') return dflt;
  return v === '1' || v === 'true' || v === 'yes' || v === 'on';
}

function readEnvNumber(name: string, dflt: number): number {
  const v = Number(process.env[name]);
  return Number.isFinite(v) && v > 0 ? v : dflt;
}

function readEnvList(name: string, dflt: string[]): string[] {
  const raw = (process.env[name] ?? '').trim();
  if (!raw) return dflt;
  return raw.split(',').map((s) => s.trim().toUpperCase()).filter(Boolean);
}

/**
 * Boot the V25 shadow pipeline. Idempotent — calling twice is a no-op.
 *
 * @returns true if started, false if disabled or already running.
 */
export function bootstrapV25Shadow(): boolean {
  if (state.enabled) return false;

  const enabled = readEnvBool('V25_SHADOW', false);
  if (!enabled) {
    atLog('[V25] shadow mode disabled (set V25_SHADOW=true to enable)');
    return false;
  }

  const symbols = readEnvList('V25_SYMBOLS', ['XBTUSD', 'XAUUSD']);
  const pollMs = readEnvNumber('V25_TICK_POLL_MS', 3000);
  const staleMs = readEnvNumber('V25_STALE_MS', 10000);
  const verboseTick = readEnvBool('V25_VERBOSE_TICK', false);

  // Configure tick gateway
  // (re-create with options, replace singleton's behaviour by mutating opts)
  // Since TickGateway has its singleton already, we register symbols and
  // override its options via a fresh instance only if needed. For Phase A
  // we use the singleton + plug per-symbol.
  const gw = new TickGateway({
    pollIntervalMs: pollMs,
    staleThresholdMs: staleMs,
    verboseTickLog: verboseTick,
  });
  state.tickGateway = gw;

  for (const sym of symbols) {
    gw.trackSymbol(sym);
    barCloseDetector.trackSymbol(sym);
    m1MicroWallBuilder.trackSymbol(sym);
    // Phase B
    wallStateMachine.trackSymbol(sym);
    // Pre-warm proximity index from current PriceMap (if available)
    wallProximityIndex.refresh(sym);
    fvgFreshTracker.refresh(sym);
  }
  // Phase B: subscribe state machine + Phase C selector
  wallStateMachine.attach();
  playbookSelector.attach();

  // Subscribe shadow logger
  const pushEvent = (ev: V25Event) => {
    state.lastEvents.push(ev);
    if (state.lastEvents.length > 50) state.lastEvents.shift();
  };
  v25EventBus.on('V25_TICK', (ev) => { state.counters.tick++; if (verboseTick) pushEvent(ev); });
  v25EventBus.on('V25_BAR_CLOSE', (ev) => { state.counters.barClose++; pushEvent(ev); });
  v25EventBus.on('V25_MICRO_WALL', (ev) => { state.counters.microWall++; pushEvent(ev); });
  v25EventBus.on('V25_STALE_TICK', (ev) => { state.counters.staleTick++; pushEvent(ev); });
  v25EventBus.on('V25_WALL_STATE_CHANGE', (ev) => { state.counters.wallStateChange++; pushEvent(ev); });

  gw.start();
  barCloseDetector.start();
  m1MicroWallBuilder.start();

  state.enabled = true;
  state.startedAt = Date.now();
  state.symbols = symbols;

  atLog(`[V25] ✅ Phase-A shadow pipeline started — symbols=${symbols.join(',')} pollMs=${pollMs} staleMs=${staleMs}`);
  return true;
}

/**
 * Alternative boot path: reads settings from `adaptive.v25` in the
 * persisted config instead of env vars. Called by `updateConfig()` when
 * the dashboard toggles the V25 Shadow switch.
 *
 * Falls back to `bootstrapV25Shadow()` for env-based boot.
 */
export function bootstrapV25ShadowFromConfig(v25cfg: {
  enabled?: boolean;
  symbols?: string[];
  pollMs?: number;
  staleMs?: number;
  verboseTick?: boolean;
}, watchlist: string[] = []): boolean {
  if (!v25cfg?.enabled) {
    if (state.enabled) {
      shutdownV25Shadow();
    }
    atLog('[V25] adaptive.v25.enabled=false — shadow not started from config');
    return false;
  }

  // If symbols are not explicitly provided in v25cfg, fallback to the main watchlist.
  // If watchlist is also empty, fallback to the hardcoded defaults.
  const symbols = (
    v25cfg.symbols && v25cfg.symbols.length > 0 
      ? v25cfg.symbols 
      : watchlist
  ).map((s) => s.toUpperCase());

  const pollMs = v25cfg.pollMs ?? 3000;
  const staleMs = v25cfg.staleMs ?? 10000;
  const verboseTick = v25cfg.verboseTick ?? false;

  if (state.enabled) {
    // Hot-reload: check if symbols changed
    const current = new Set(state.symbols);
    const next = new Set(symbols);

    // Remove untracked
    for (const s of state.symbols) {
      if (!next.has(s)) {
        atLog(`[V25] untracking symbol: ${s}`);
        state.tickGateway.untrackSymbol(s);
        barCloseDetector.untrackSymbol(s);
        m1MicroWallBuilder.untrackSymbol(s);
        wallStateMachine.untrackSymbol(s);
      }
    }

    // Add new ones
    for (const s of symbols) {
      if (!current.has(s)) {
        atLog(`[V25] tracking new symbol: ${s}`);
        state.tickGateway.trackSymbol(s);
        barCloseDetector.trackSymbol(s);
        m1MicroWallBuilder.trackSymbol(s);
        wallStateMachine.trackSymbol(s);
        wallProximityIndex.refresh(s);
        fvgFreshTracker.refresh(s);
      }
    }

    state.symbols = symbols;
    // Note: pollMs/staleMs updates are not hot-reloaded yet into the gateway instance 
    // but the symbols are.
    atLog(`[V25] hot-reloaded configuration — symbols=${symbols.join(',')} pollMs=${pollMs}`);
    return true;
  }

  const gw = new TickGateway({ pollIntervalMs: pollMs, staleThresholdMs: staleMs, verboseTickLog: verboseTick });
  state.tickGateway = gw;

  for (const sym of symbols) {
    gw.trackSymbol(sym);
    barCloseDetector.trackSymbol(sym);
    m1MicroWallBuilder.trackSymbol(sym);
    wallStateMachine.trackSymbol(sym);
    wallProximityIndex.refresh(sym);
    fvgFreshTracker.refresh(sym);
  }
  wallStateMachine.attach();
  playbookSelector.attach();

  const pushEvent = (ev: V25Event) => {
    state.lastEvents.push(ev);
    if (state.lastEvents.length > 50) state.lastEvents.shift();
  };
  v25EventBus.on('V25_TICK', (ev) => { state.counters.tick++; if (verboseTick) pushEvent(ev); });
  v25EventBus.on('V25_BAR_CLOSE', (ev) => { state.counters.barClose++; pushEvent(ev); });
  v25EventBus.on('V25_MICRO_WALL', (ev) => { state.counters.microWall++; pushEvent(ev); });
  v25EventBus.on('V25_STALE_TICK', (ev) => { state.counters.staleTick++; pushEvent(ev); });
  v25EventBus.on('V25_WALL_STATE_CHANGE', (ev) => { state.counters.wallStateChange++; pushEvent(ev); });

  gw.start();
  barCloseDetector.start();
  m1MicroWallBuilder.start();

  state.enabled = true;
  state.startedAt = Date.now();
  state.symbols = symbols;

  atLog(`[V25] ✅ Phase-A started from UI config — symbols=${symbols.join(',')} pollMs=${pollMs} staleMs=${staleMs}`);
  return true;
}


export function shutdownV25Shadow(): void {
  if (!state.enabled) return;
  state.tickGateway.stop();
  barCloseDetector.stop();
  m1MicroWallBuilder.stop();
  state.enabled = false;
  atLog('[V25] Phase-A shadow pipeline stopped');
}

/** Health snapshot for /api/v25/health */
export function v25Health() {
  return {
    enabled: state.enabled,
    startedAt: state.startedAt,
    uptimeMs: state.startedAt ? Date.now() - state.startedAt : 0,
    symbols: state.symbols,
    tickGateway: state.tickGateway.status(),
    barCloseDetector: barCloseDetector.status(),
    microWallBuilder: m1MicroWallBuilder.status(),
    eventBus: v25EventBus.stats(),
    counters: { ...state.counters },
    tickBuffers: tickBuffers.allStats(),
    proximityIndex: wallProximityIndex.status(),
    wallStateMachine: wallStateMachine.status(),
    playbookSelector: playbookSelector.status(),
    lastEvents: state.lastEvents.slice(-20).map((ev) => ({
      type: ev.type,
      symbol: ev.symbol,
      ts: ev.ts,
    })),
  };
}

/** Per-symbol detail for /api/v25/state/:symbol */
export function v25SymbolState(symbol: string) {
  const upper = (symbol || '').toUpperCase();
  return {
    symbol: upper,
    snapshot: priceMapSnapshotProvider.get(upper),
    proximity: (() => {
      const last = tickBuffers.get(upper).last();
      return last ? wallProximityIndex.query(upper, last.mid) : null;
    })(),
    fvg: fvgFreshTracker.snapshot(upper),
    state: wallStateMachine.status().find((s) => s.symbol === upper) ?? null,
    lastTick: tickBuffers.get(upper).last(),
    candidates: playbookSelector.recentCandidates(upper),
  };
}

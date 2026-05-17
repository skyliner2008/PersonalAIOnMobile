/**
 * V25.0 — WallStateMachine
 *
 * One state per (symbol × side). State is the heart of V25:
 *
 *   IDLE → APPROACH → REACT → CONFIRM      ─┐
 *                          → BREAK_OUT → RETEST → COMMITTED
 *                                              └→ COMMITTED
 *
 * Transitions are driven by:
 *   - V25_TICK         (proximity check, velocity, break detection)
 *   - V25_BAR_CLOSE    (M1 reaction confirm, M5/M15 wall refresh)
 *
 * Phase B does NOT execute orders — only emits V25_WALL_STATE_CHANGE for the
 * Playbook Selector (Phase C) to consume.
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import { atLog } from '../../utils.js';
import type { PriceWall } from '../../analyzers/smc/types.js';
import { v25EventBus } from '../V25EventBus.js';
import { tickBuffers } from '../TickBuffer.js';
import {
  resolveSymbolProfile,
  type V25SymbolProfile,
  type WallStateName,
  type WallStateEvent,
  type Tick,
  type BarCloseEvent,
} from '../types.js';
import { wallProximityIndex } from './WallProximityIndex.js';
import { fvgFreshTracker } from './FvgFreshTracker.js';
import { reactionDetector, type ReversalResult } from './ReactionDetector.js';
import { priceMapSnapshotProvider } from './PriceMapSnapshotProvider.js';
import { breakoutWallPromoter } from './BreakoutWallPromoter.js';

type Side = 'ABOVE' | 'BELOW';

interface SideState {
  state: WallStateName;
  wall: PriceWall | null;
  enteredAt: number;
  /** Tick price at last APPROACH entry (for velocity baseline). */
  approachPrice: number | null;
  approachTs: number | null;
  /** Last reversal detection result on M1. */
  lastReversal: ReversalResult | null;
  /** Count of state transitions in last 10s (for churn detection). */
  recentTransitions: number[];
}

function newSideState(): SideState {
  return {
    state: 'IDLE',
    wall: null,
    enteredAt: Date.now(),
    approachPrice: null,
    approachTs: null,
    lastReversal: null,
    recentTransitions: [],
  };
}

// 2026-05-10 V25.1 — minimum dwell times to prevent oscillation
const MIN_DWELL_MS = {
  APPROACH: 1500,  // don't IDLE-out within 1.5s of entering APPROACH
  REACT:    3000,  // hold REACT at least 3s — protects bar-close CONFIRM logic
  BREAK_OUT: 2000,
};

interface SymbolEntry {
  above: SideState;
  below: SideState;
  profile: V25SymbolProfile;
  /** ATR(M15) cached from PriceMap snapshot. Used for distance budgets. */
  atrM15: number;
}

export class WallStateMachine {
  private symbols = new Map<string, SymbolEntry>();
  /** Throttle: drop ticks within this many ms per symbol to avoid bus storm. */
  private tickEvalMinIntervalMs = 200;
  private lastEvalAt = new Map<string, number>();
  /** Frozen on STALE_TICK — stays IDLE until next bar close. */
  private frozenUntilBarClose = new Set<string>();

  trackSymbol(symbol: string): void {
    const upper = (symbol || '').toUpperCase();
    if (this.symbols.has(upper)) return;
    this.symbols.set(upper, {
      above: newSideState(),
      below: newSideState(),
      profile: resolveSymbolProfile(upper),
      atrM15: 0,
    });
  }

  untrackSymbol(symbol: string): void {
    this.symbols.delete((symbol || '').toUpperCase());
  }

  /** Subscribe to V25 events. Idempotent. */
  private subscribed = false;
  attach(): void {
    if (this.subscribed) return;
    this.subscribed = true;

    v25EventBus.on('V25_TICK', async (ev) => {
      if (ev.type !== 'V25_TICK') return;
      await this.onTick(ev.payload as Tick);
    });

    v25EventBus.on('V25_BAR_CLOSE', async (ev) => {
      if (ev.type !== 'V25_BAR_CLOSE') return;
      await this.onBarClose(ev.payload as BarCloseEvent);
    });

    v25EventBus.on('V25_STALE_TICK', (ev) => {
      // Only log on first transition into "frozen" — subsequent stale events
      // (TickGateway already throttles to 1/30s) just maintain the freeze.
      const wasFrozen = this.frozenUntilBarClose.has(ev.symbol);
      this.frozenUntilBarClose.add(ev.symbol);
      if (!wasFrozen) {
        atLog(`[V25] state machine frozen for ${ev.symbol} (stale tick) — resumes on next bar close`);
      }
    });
  }

  // ── tick handler ───────────────────────────────────────────────────

  private async onTick(tick: Tick): Promise<void> {
    const upper = tick.symbol.toUpperCase();
    const entry = this.symbols.get(upper);
    if (!entry) return;
    if (this.frozenUntilBarClose.has(upper)) return;

    const lastEval = this.lastEvalAt.get(upper) ?? 0;
    if (tick.recvTs - lastEval < this.tickEvalMinIntervalMs) return;
    this.lastEvalAt.set(upper, tick.recvTs);

    // refresh nearest walls (cheap; index already prebuilt)
    const q = wallProximityIndex.query(upper, tick.mid);

    // Update FVG tracker (drop filled)
    fvgFreshTracker.onPrice(upper, tick.mid);

    // ABOVE side (resistance / SELL setups)
    await this.evalSide(upper, entry, 'ABOVE', q.above, tick);
    // BELOW side (support / BUY setups)
    await this.evalSide(upper, entry, 'BELOW', q.below, tick);
  }

  private async evalSide(
    symbol: string,
    entry: SymbolEntry,
    sideKind: Side,
    wall: PriceWall | null,
    tick: Tick,
  ): Promise<void> {
    const ss = sideKind === 'ABOVE' ? entry.above : entry.below;
    const profile = entry.profile;
    const atr = entry.atrM15 > 0 ? entry.atrM15 : 0;

    if (!wall) {
      this.transition(symbol, sideKind, ss, 'IDLE', null, tick.recvTs);
      return;
    }

    const dist = Math.abs(tick.mid - wall.price);
    // ATR fallback: 0.4% of price is a much better proxy than 0.1% for crypto.
    // XBTUSD @ 80750 → fallback ATR ≈ 323 pts (real ATR ~300–500 pts).
    const budget = atr > 0 ? atr : Math.max(wall.price * 0.004, 50);
    const approachZone = profile.approachAtrMul * budget;
    const reactZone = profile.reactAtrMul * budget;

    // Wall identity changed?
    // 2026-05-10 V25.1: when current state has a stronger anchor wall (more stars)
    // already, refuse to swap to a weaker nearer wall — prevents APPROACH/REACT
    // bouncing between two adjacent walls when price wobbles past one.
    // Also hold equal/stronger replacements while a working state remains inside
    // the same price cluster; otherwise fast ticks can churn between anchors.
    if (ss.wall && ss.wall.price !== wall.price) {
      const priceDiff = Math.abs(ss.wall.price - wall.price);
      const isSignificantShift = priceDiff > reactZone;
      // Hold the existing strong wall as long as price is still within 2× reactZone of it
      const stillCloseToOld =
        Math.abs(tick.mid - ss.wall.price) <= reactZone * 2.0;
      const inWorkingState =
        ss.state === 'APPROACH' ||
        ss.state === 'REACT' ||
        ss.state === 'CONFIRM' ||
        ss.state === 'RETEST';

      if (inWorkingState && stillCloseToOld) {
        // The existing anchor is still the active trading context.
        // Keep tracking the stronger wall — do nothing
      } else if (isSignificantShift || !inWorkingState) {
        ss.wall = wall;
        ss.approachPrice = null;
        ss.approachTs = null;
        this.transition(symbol, sideKind, ss, 'IDLE', wall, tick.recvTs);
      } else {
        // Minor shift to equal/stronger wall — update reference, keep state
        ss.wall = wall;
      }
    } else if (!ss.wall) {
      ss.wall = wall;
    }

    const dwell = tick.recvTs - ss.enteredAt;

    switch (ss.state) {
      case 'IDLE':
        if (dist <= approachZone) {
          ss.approachPrice = tick.mid;
          ss.approachTs = tick.recvTs;
          this.transition(symbol, sideKind, ss, 'APPROACH', wall, tick.recvTs);
        }
        break;

      case 'APPROACH': {
        // velocity check — calmer means decel toward wall = good
        const velocityOk = this.velocityDecelerating(symbol, sideKind, tick, ss);
        if (dist <= reactZone && velocityOk) {
          wallProximityIndex.decayWall(symbol, wall.price); // Phase 2.3: Decay wall on touch
          this.transition(symbol, sideKind, ss, 'REACT', wall, tick.recvTs);
        } else if (dist > approachZone * 2.0 && dwell > MIN_DWELL_MS.APPROACH) {
          // 2026-05-10 V25.1: hysteresis — was 1.5x, now 2.0x; also require min dwell
          // to stop tick-level oscillation when price wobbles around boundary.
          ss.approachPrice = null;
          ss.approachTs = null;
          this.transition(symbol, sideKind, ss, 'IDLE', wall, tick.recvTs);
        }
        break;
      }

      case 'REACT': {
        // detect break-out: price has poked through wall by > 20% reactZone
        const broke = sideKind === 'ABOVE'
          ? tick.mid > wall.price + reactZone * 0.2
          : tick.mid < wall.price - reactZone * 0.2;
        if (broke) {
          this.transition(symbol, sideKind, ss, 'BREAK_OUT', wall, tick.recvTs);
        }
        // 2026-05-10 V25.1: REACT no longer falls back to IDLE on tick distance.
        // Exit REACT only via BREAK_OUT (above) or M1 bar-close CONFIRM (in
        // onBarClose) — protects the M1 reversal-detection window so the
        // PlaybookSelector actually sees CONFIRM events instead of IDLE flicker.
        break;
      }

      case 'BREAK_OUT': {
        // Watch for retest: price comes back toward broken wall within reactZone
        if (dist <= reactZone && wall) {
          const flippedWall = breakoutWallPromoter.promote(symbol, wall, sideKind);
          this.transition(symbol, sideKind, ss, 'RETEST', flippedWall, tick.recvTs);
        }
        break;
      }

      case 'RETEST':
      case 'CONFIRM':
      case 'COMMITTED':
        // Phase C / D will move out of these on order events. Phase B leaves them.
        break;
    }
  }

  private velocityDecelerating(symbol: string, sideKind: Side, tick: Tick, ss: SideState): boolean {
    if (ss.approachPrice == null || ss.approachTs == null) return true;
    const buf = tickBuffers.get(symbol);
    const recent = buf.lastN(20);
    if (recent.length < 5) return true;
    const a = recent[Math.max(0, recent.length - 5)];
    const b = recent[recent.length - 1];
    if (!a || !b || b.recvTs === a.recvTs) return true;
    const v = Math.abs(b.mid - a.mid) / Math.max(1, (b.recvTs - a.recvTs) / 1000);
    // Compare with approach-window average: rough heuristic — if v < 4 pip/s for XBT, ok
    const profile = this.symbols.get(symbol)?.profile;
    const limit = (profile?.reactAtrMul ?? 0.5) * Math.max(0.5, (this.symbols.get(symbol)?.atrM15 ?? 0) * 0.05);
    return v <= Math.max(limit, 0.0001);
  }

  // ── bar-close handler ──────────────────────────────────────────────

  private async onBarClose(bar: BarCloseEvent): Promise<void> {
    const upper = bar.symbol.toUpperCase();
    const entry = this.symbols.get(upper);
    if (!entry) return;

    const inWorkingState = entry.above.state === 'REACT' || entry.above.state === 'BREAK_OUT' || 
                           entry.below.state === 'REACT' || entry.below.state === 'BREAK_OUT';

    // Refresh proximity index + FVG tracker on M5/M15/M30/H1 
    // OR on M1 if we are in a working state (REACT / BREAK_OUT) for hot-refresh
    if (bar.tf === 'M5' || bar.tf === 'M15' || bar.tf === 'M30' || bar.tf === 'H1' || (bar.tf === 'M1' && inWorkingState)) {
      const refreshed = wallProximityIndex.refresh(upper);
      if (refreshed) {
        fvgFreshTracker.refresh(upper);
        // Update ATR from the snapshot — uses smc.lastPrice * 0.01 fallback if missing
        const snap = priceMapSnapshotProvider.get(upper);
        if (snap.priceMap?.atr && snap.priceMap.atr > 0) entry.atrM15 = snap.priceMap.atr;
      }
      // Resume from freeze — log once per resume
      if (this.frozenUntilBarClose.delete(upper)) {
        atLog(`[V25] state machine resumed for ${upper} (bar close ${bar.tf})`);
      }
    }

    // M1 close → run reversal detector for any side currently in REACT
    if (bar.tf === 'M1') {
      const snap = priceMapSnapshotProvider.get(upper);
      let htfBias: 'BULL' | 'BEAR' | 'NEUTRAL' = 'NEUTRAL';
      if (snap.smc?.structure?.direction === 'BULLISH') htfBias = 'BULL';
      else if (snap.smc?.structure?.direction === 'BEARISH') htfBias = 'BEAR';

      if (entry.above.state === 'REACT') {
        const reversal = await reactionDetector.detectWallAware(upper, entry.above.wall, 'SELL', htfBias);
        if (reversal.side === 'SELL') {
          const ss = entry.above;
          ss.lastReversal = reversal;
          this.transition(upper, 'ABOVE', ss, 'CONFIRM', entry.above.wall, bar.barCloseTs);
        }
      }

      if (entry.below.state === 'REACT') {
        const reversal = await reactionDetector.detectWallAware(upper, entry.below.wall, 'BUY', htfBias);
        if (reversal.side === 'BUY') {
          const ss = entry.below;
          ss.lastReversal = reversal;
          this.transition(upper, 'BELOW', ss, 'CONFIRM', entry.below.wall, bar.barCloseTs);
        }
      }
    }
  }

  // ── transition emit ───────────────────────────────────────────────

  private transition(
    symbol: string,
    sideKind: Side,
    ss: SideState,
    next: WallStateName,
    wall: PriceWall | null,
    ts: number,
  ): void {
    if (ss.state === next) return;
    const prev = ss.state;
    ss.state = next;
    ss.enteredAt = ts;
    if (wall) ss.wall = wall;

    // 2026-05-10 V25.1: churn telemetry — emit warning when >6 transitions / 10s.
    ss.recentTransitions.push(ts);
    const cutoff = ts - 10_000;
    while (ss.recentTransitions.length && ss.recentTransitions[0] < cutoff) {
      ss.recentTransitions.shift();
    }
    if (ss.recentTransitions.length === 7) {
      atLog(`[V25] ⚠️ ${symbol} ${sideKind} state churn — ${ss.recentTransitions.length} transitions in 10s (wall=${wall?.price?.toFixed(2) ?? '?'})`);
    }

    const evt: WallStateEvent = {
      symbol,
      side: sideKind,
      state: next,
      prevState: prev,
      wallPrice: wall?.price ?? null,
      wall: wall ?? null,
      ts,
    };
    // Diagnostic log — show state transition so we can diagnose CONFIRM flow
    atLog(`[V25] ${symbol} ${sideKind} wall=${wall?.price?.toFixed(2) ?? '?'} ${prev}→${next}`);
    void v25EventBus.emitWallStateChange(symbol, evt);
  }

  // ── status / diagnostics ──────────────────────────────────────────

  status() {
    return Array.from(this.symbols.entries()).map(([sym, entry]) => ({
      symbol: sym,
      profile: entry.profile,
      atrM15: entry.atrM15,
      above: {
        state: entry.above.state,
        enteredAt: entry.above.enteredAt,
        wall: entry.above.wall ? { price: entry.above.wall.price, stars: entry.above.wall.confluenceStars, tfs: entry.above.wall.timeframes } : null,
      },
      below: {
        state: entry.below.state,
        enteredAt: entry.below.enteredAt,
        wall: entry.below.wall ? { price: entry.below.wall.price, stars: entry.below.wall.confluenceStars, tfs: entry.below.wall.timeframes } : null,
      },
      frozen: this.frozenUntilBarClose.has(sym),
    }));
  }
}

export const wallStateMachine = new WallStateMachine();

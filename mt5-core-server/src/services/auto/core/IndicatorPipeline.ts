/**
 * Indicator Pipeline — ระบบคำนวณ Indicators แบบ Event-Driven (Phase 2)
 * รวม SMC Engine V2 + Technical Indicators เข้าด้วยกัน
 * อัปเดตทุกครั้งที่มี M1 candle ใหม่ → emit events ถ้าพบ signal
 *
 * 2026-05-06 — skyliner.jojo@gmail.com
 */

import { buildSmcSnapshot, buildPriceMapFromRecord, analyzePath, formatPriceMap } from '../analyzers/smc/index.js';
import type { SmcSnapshot, Candle, PriceMap, PathAnalysis } from '../analyzers/smc/types.js';
import type { AnalysisSummary, StrategyType } from '../types.js';
import { inferAnalysis } from '../analysis.js';
import { parseCandles } from '../../tracking.js';
import { eventBus } from './EventBus.js';
import { detectEntrySignals, type EntrySignal } from './SignalDetector.js';
import { atLog, atWarn } from '../utils.js';
import { computeATR } from '../analyzers/smc/utils.js';
// V25 — prefer realtime tick for currentPrice (much fresher than M5 candle close)
// Imported as a leaf module (TickBuffer.ts has no other deps), so no circular risk.
import { tickBuffers } from '../v25/TickBuffer.js';
// V24.2.0 (2026-05-08) — Daily Pivot + Session VWAP + Auto-Fibonacci context layers
import {
  computePivotLevels, deriveDailyOhlcFromIntraday,
  computeSessionVwap, computeFibLevels, buildContextLevels,
} from '../analyzers/levels.js';

// ── Indicator Snapshot ─────────────────────────────────────────────

export interface IndicatorSnapshot {
  symbol: string;
  timeframe: string;
  timestamp: number;

  // Technical indicators
  analyses: Record<string, AnalysisSummary>;

  // SMC V2 snapshots per timeframe
  smcSnapshots: Record<string, SmcSnapshot>;

  // Entry signals detected
  entrySignals: EntrySignal[];

  // Previous structure event (for change detection)
  prevStructureEvent: string | null;

  // Price Map (V21.1) — ภาพรวมกำแพงราคาจากทุก TF
  priceMap: PriceMap | null;
}

// ── Pipeline State ─────────────────────────────────────────────────

interface PipelineState {
  lastSmcSnapshot: SmcSnapshot | null;
  lastStructureEvent: string | null;
  lastStructureDirection: string | null;
  lastSignalAt: number;
  signalCooldownMs: number;       // minimum time between signals (default 60s)
}

// ── Pipeline Class ─────────────────────────────────────────────────

class IndicatorPipeline {
  private states = new Map<string, PipelineState>();
  private snapshots = new Map<string, IndicatorSnapshot>();
  private signalCooldownMs = 60_000; // 1 minute between signals

  /**
   * Process new candle data for a symbol.
   * Called when candles are fetched/updated (replaces fixed polling).
   *
   * @param symbol Symbol name (e.g. 'XAUUSD')
   * @param candlesByTf Map of timeframe → candle arrays
   * @returns Detected entry signals (if any)
   */
  async processUpdate(
    symbol: string,
    candlesByTf: Map<string, ReturnType<typeof parseCandles>>
  ): Promise<EntrySignal[]> {
    const upper = symbol.toUpperCase();

    // Get or create pipeline state
    let state = this.states.get(upper);
    if (!state) {
      state = {
        lastSmcSnapshot: null,
        lastStructureEvent: null,
        lastStructureDirection: null,
        lastSignalAt: 0,
        signalCooldownMs: this.signalCooldownMs,
      };
      this.states.set(upper, state);
    }

    // 1. Run technical analysis on each timeframe
    const analyses: Record<string, AnalysisSummary> = {};
    for (const [tf, candles] of candlesByTf) {
      if (candles.length >= 20) {
        analyses[tf] = inferAnalysis(candles);
      }
    }

    // 2. Build SMC V2 snapshots for ALL structural timeframes (ทุก TF เพื่อ PriceMap)
    const smcSnapshots: Record<string, SmcSnapshot> = {};
    const smcTfs = ['M5', 'M15', 'M30', 'H1', 'H4'];  // V21.1: เพิ่ม M30
    const mtfCandleArrays: { timeframe: string; candles: Candle[] }[] = [];

    for (const tf of smcTfs) {
      const rawCandles = candlesByTf.get(tf);
      if (!rawCandles || rawCandles.length < 30) continue;

      const smcCandles: Candle[] = rawCandles.map(c => ({
        t: (c as any).t ?? 0,
        o: c.o, h: c.h, l: c.l, c: c.c,
        v: (c as any).v ?? 0,
      }));

      // Build individual snapshot per TF (สำหรับ PriceMap)
      const tfSnap = buildSmcSnapshot(smcCandles, upper, tf);
      smcSnapshots[tf] = tfSnap;
      mtfCandleArrays.push({ timeframe: tf, candles: smcCandles });
    }

    // Build primary SMC snapshot with MTF context (use M5 if available, else M15)
    const primaryTf = candlesByTf.has('M5') ? 'M5' : (candlesByTf.has('M15') ? 'M15' : 'H1');
    const primaryCandles = candlesByTf.get(primaryTf);
    if (primaryCandles && primaryCandles.length >= 30) {
      const smcCandles: Candle[] = primaryCandles.map(c => ({
        t: (c as any).t ?? 0, o: c.o, h: c.h, l: c.l, c: c.c, v: (c as any).v ?? 0,
      }));

      // Rebuild primary with all MTF context
      const snapshot = buildSmcSnapshot(smcCandles, upper, primaryTf, mtfCandleArrays);
      smcSnapshots[primaryTf] = snapshot;

      // 3. Detect regime changes
      await this.checkRegimeChange(upper, snapshot, state);

      // Update state
      state.lastSmcSnapshot = snapshot;
    }

    // 4. Build PriceMap (V21.1) — รวม zone จากทุก TF เป็นแผนที่กำแพงราคา
    // 2026-05-10: prefer V25 realtime tick over M5 candle close (was up to 5min stale)
    const lastPrice = this.getLastPrice(candlesByTf, upper);
    let priceMap: PriceMap | null = null;

    if (Object.keys(smcSnapshots).length >= 2 && lastPrice > 0) {
      try {
        const priceMapStartedAt = Date.now();
        // คำนวณ ATR จาก primary TF
        const primaryRaw = candlesByTf.get(primaryTf);
        const primaryAtr = primaryRaw && primaryRaw.length >= 14
          ? computeATR(primaryRaw.map(c => ({
              t: (c as any).t ?? 0, o: c.o, h: c.h, l: c.l, c: c.c, v: (c as any).v ?? 0,
            })))
          : lastPrice * 0.001;

        // V24.2.0 — คำนวณ Context Levels (Pivot + VWAP + Fib) ก่อนสร้าง PriceMap
        const contextLevels = this.buildContextLevelsFor(upper, candlesByTf, lastPrice, primaryAtr, smcSnapshots[primaryTf] ?? null);

        priceMap = buildPriceMapFromRecord(smcSnapshots, lastPrice, primaryAtr, undefined, contextLevels);
        const priceMapMs = Date.now() - priceMapStartedAt;
        atLog(`[Pipeline] ${upper}: PriceMap calculation time=${priceMapMs}ms`);
        atLog(`[Pipeline] ${upper}: PriceMap built — ${priceMap.resistanceWalls.length} resistance, ${priceMap.supportWalls.length} support walls (+${contextLevels.length} context levels: Pivot/VWAP/Fib)`);
        atLog(`[Pipeline]\n${formatPriceMap(priceMap)}`);
      } catch (pmErr) {
        atWarn(`[Pipeline] ${upper}: PriceMap build error: ${pmErr}`);
      }
    }

    // 5. Detect entry signals (ใช้ PriceMap ด้วยถ้ามี)
    const primarySmc = smcSnapshots[primaryTf];
    let entrySignals: EntrySignal[] = [];

    if (primarySmc && lastPrice > 0) {
      entrySignals = detectEntrySignals(upper, lastPrice, primarySmc, analyses, priceMap ?? undefined);

      // Apply signal cooldown
      const now = Date.now();
      if (entrySignals.length > 0 && now - state.lastSignalAt < state.signalCooldownMs) {
        atLog(`[Pipeline] ${upper}: ${entrySignals.length} signal(s) suppressed by cooldown (${Math.round((state.signalCooldownMs - (now - state.lastSignalAt)) / 1000)}s remaining)`);
        entrySignals = [];
      }

      // 6. Emit entry signals via EventBus
      for (const signal of entrySignals) {
        state.lastSignalAt = now;
        await eventBus.emitSignalEntry(
          upper,
          signal.side,
          signal.entry,
          signal.sl,
          signal.tp,
          signal.confidence,
          signal.confluenceStars,
          signal.strategy,
          signal.triggers,
          primarySmc
        );
      }
    }

    // 7. Save snapshot
    const indicatorSnapshot: IndicatorSnapshot = {
      symbol: upper,
      timeframe: primaryTf,
      timestamp: Date.now(),
      analyses,
      smcSnapshots,
      entrySignals,
      prevStructureEvent: state.lastStructureEvent,
      priceMap,
    };
    this.snapshots.set(upper, indicatorSnapshot);

    // 7. Emit indicator update
    await eventBus.emit({
      type: 'INDICATOR_UPDATE',
      symbol: upper,
      timestamp: Date.now(),
      data: {
        reason: `Indicators updated for ${upper} (${Object.keys(analyses).join(',')})`,
        indicators: indicatorSnapshot,
      },
    });

    return entrySignals;
  }

  /**
   * Check if market structure has changed (regime change detection)
   */
  private async checkRegimeChange(
    symbol: string,
    smc: SmcSnapshot,
    state: PipelineState
  ): Promise<void> {
    const currentEvent = smc.structure.lastEvent;
    const currentDir = smc.structure.direction;

    // Detect structure change
    if (state.lastStructureEvent !== null &&
        (currentEvent !== state.lastStructureEvent || currentDir !== state.lastStructureDirection)) {

      if (currentEvent === 'CHoCH') {
        atLog(`[Pipeline] REGIME CHANGE: ${symbol} ${state.lastStructureDirection} -> ${currentDir} (${currentEvent}_${smc.structure.lastEventSide})`);
        await eventBus.emitRegimeChange(
          symbol,
          `${currentEvent}_${smc.structure.lastEventSide}`,
          `Market structure changed from ${state.lastStructureDirection} to ${currentDir}`
        );
      }
    }

    state.lastStructureEvent = currentEvent;
    state.lastStructureDirection = currentDir;
  }

  /**
   * V24.2.0 — Compute Daily Pivot + Session VWAP + Auto-Fibonacci context levels
   * for a symbol, returns ExtraContextLevel[] ready to feed buildPriceMap.
   */
  private buildContextLevelsFor(
    symbol: string,
    candlesByTf: Map<string, any[]>,
    lastPrice: number,
    primaryAtr: number,
    primarySmc: SmcSnapshot | null
  ): import('../analyzers/smc/priceMapBuilder.js').ExtraContextLevel[] {
    try {
      // ── 1. Daily Pivot — derive from H1 (24-bar window) ─────────────
      const h1Candles = candlesByTf.get('H1') ?? [];
      const h1Smc: Candle[] = h1Candles.map((c: any) => ({
        t: c.t ?? 0, o: c.o, h: c.h, l: c.l, c: c.c, v: c.v ?? 0,
      }));
      const dailyOhlc = deriveDailyOhlcFromIntraday(h1Smc, 24, 60);
      const pivots = dailyOhlc ? computePivotLevels(dailyOhlc) : null;

      // ── 2. Session VWAP — anchor at NY equity open (13:00 UTC) ──────
      // ใช้ M5 ถ้ามี (มี granularity ดีกว่า), fallback M15
      const vwapTf = candlesByTf.has('M5') ? 'M5' : 'M15';
      const vwapRaw = candlesByTf.get(vwapTf) ?? [];
      const vwapCandles: Candle[] = vwapRaw.map((c: any) => ({
        t: c.t ?? 0, o: c.o, h: c.h, l: c.l, c: c.c, v: c.v ?? 0,
      }));
      const vwap = computeSessionVwap(vwapCandles, 13);

      // ── 3. Auto-Fibonacci — from primary SMC structure swing H/L ────
      let fibs = null;
      if (primarySmc?.structure?.structureHigh && primarySmc?.structure?.structureLow) {
        fibs = computeFibLevels(primarySmc.structure.structureHigh, primarySmc.structure.structureLow);
      }

      // Diagnostic log (compact)
      const note: string[] = [];
      if (pivots) note.push(`Pivot P=${pivots.pivot.toFixed(2)} R1=${pivots.r1.toFixed(2)} S1=${pivots.s1.toFixed(2)}`);
      if (vwap) note.push(`VWAP=${vwap.vwap.toFixed(2)} dev=${vwap.deviation.toFixed(2)} (${vwap.samples} bars)`);
      if (fibs) note.push(`Fib swing=[${fibs.swingLow.toFixed(2)}, ${fibs.swingHigh.toFixed(2)}] 0.618=${fibs.fib618.toFixed(2)}`);
      if (note.length > 0) atLog(`[Pipeline] ${symbol}: Context levels — ${note.join(' | ')}`);

      return buildContextLevels({
        currentPrice: lastPrice,
        pivots,
        vwap,
        fibs,
        atr: primaryAtr,
      });
    } catch (e) {
      atWarn(`[Pipeline] ${symbol}: context levels error: ${e}`);
      return [];
    }
  }

  getSnapshot(symbol: string): IndicatorSnapshot | null {
    return this.snapshots.get(symbol.toUpperCase()) || null;
  }

  getSmcSnapshot(symbol: string): SmcSnapshot | null {
    const snap = this.snapshots.get(symbol.toUpperCase());
    if (!snap) return null;
    return Object.values(snap.smcSnapshots)[0] || null;
  }

  getPriceMap(symbol: string): PriceMap | null {
    return this.snapshots.get(symbol.toUpperCase())?.priceMap ?? null;
  }

  setSignalCooldown(ms: number): void {
    this.signalCooldownMs = ms;
    for (const state of this.states.values()) {
      state.signalCooldownMs = ms;
    }
  }

  /**
   * Resolve "current price" with the freshest source available:
   *  1. V25 TickGateway buffer (poll /symbol_info every 1s, age < 10s) — preferred
   *  2. M5 candle close (used to be the only source — up to 5 min stale, but the
   *     candle is what V21 PriceMap walls were measured against)
   *  3. Larger TF candle close (defensive fallback)
   *
   * Returning the V25 tick is critical for `── PRICE: ──` to match what the user
   * sees in the MT5 terminal; otherwise the wall ladder rendered to log lags
   * the real bid/ask by a full M5 bar.
   *
   * 2026-05-10 — fix for "PRICE in log doesn't match MT5"
   */
  private getLastPrice(candlesByTf: Map<string, any[]>, symbol?: string): number {
    if (symbol) {
      try {
        const tick = tickBuffers.get(symbol).last();
        if (tick) {
          const ageMs = Date.now() - tick.recvTs;
          if (ageMs < 10_000 && Number.isFinite(tick.mid) && tick.mid > 0) {
            return tick.mid;
          }
        }
      } catch {
        // V25 disabled or symbol not tracked — fall through to candle close
      }
    }
    for (const tf of ['M5', 'M15', 'M30', 'H1', 'H4']) {
      const candles = candlesByTf.get(tf);
      if (candles && candles.length > 0) {
        return candles[candles.length - 1].c;
      }
    }
    return 0;
  }
}

export const indicatorPipeline = new IndicatorPipeline();

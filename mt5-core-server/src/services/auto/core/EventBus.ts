/**
 * Event Bus — Typed Event System สำหรับ Event-Driven Architecture
 * แทนที่ Polling Cycle ด้วย Event-based triggers
 *
 * Events:
 * - SIGNAL_ENTRY:     EA ตรวจพบจุดเข้าที่ตรงเงื่อนไข → ปลุก AI Agents
 * - SIGNAL_EXIT:      ถึงจุด BE/Trail/CLP → ปลุก SL/TP Agent
 * - REGIME_CHANGE:    Market Structure เปลี่ยน (BOS/CHoCH) → ปลุก Analyst
 * - TRADE_CLOSED:     Trade ถูกปิด → ปลุก Post-Mortem
 * - INDICATOR_UPDATE:  Indicator snapshot ใหม่ → บันทึก DB
 * - CLUSTER_ALERT:    Cluster loss/profit alert → ปลุก Risk Officer
 *
 * 2026-05-06 — skyliner.jojo@gmail.com
 */

import { atLog, atWarn } from '../utils.js';
import type { StrategyType, PositionRow } from '../types.js';

// ── Event Types ─────────────────────────────────────────────────────

export type TradingEventType =
  | 'SIGNAL_ENTRY'
  | 'SIGNAL_EXIT'
  | 'REGIME_CHANGE'
  | 'TRADE_CLOSED'
  | 'INDICATOR_UPDATE'
  | 'CLUSTER_ALERT'
  | 'SL_TP_REVIEW';

export interface TradingEvent {
  type: TradingEventType;
  symbol: string;
  timestamp: number;
  data: TradingEventData;
}

export interface TradingEventData {
  side?: 'BUY' | 'SELL';
  entry?: number;
  sl?: number;
  tp?: number;
  strategy?: StrategyType;
  confidence?: number;
  confluenceStars?: number;
  triggers?: string[];
  reason: string;
  // Additional context
  position?: PositionRow;
  ticket?: number;
  smcSnapshot?: any;     // SmcSnapshot from SMC Engine V2
  indicators?: any;      // IndicatorSnapshot
  structureEvent?: string; // e.g. 'CHoCH_UP', 'BOS_DOWN'
  clusterAlert?: {
    type: 'LOSS_LIMIT' | 'PROFIT_PEAK' | 'HEAT_WARNING';
    value: number;
  };
}

// ── Event Listener Type ─────────────────────────────────────────────

export type EventListener = (event: TradingEvent) => void | Promise<void>;

// ── Event Bus Class ─────────────────────────────────────────────────

class EventBus {
  private listeners = new Map<TradingEventType, EventListener[]>();
  private eventLog: TradingEvent[] = [];
  private maxLogSize = 500;
  private _paused = false;

  /**
   * Subscribe ให้ listener รับ event ที่ต้องการ
   */
  on(type: TradingEventType, listener: EventListener): () => void {
    const list = this.listeners.get(type) || [];
    list.push(listener);
    this.listeners.set(type, list);

    // Return unsubscribe function
    return () => {
      const idx = list.indexOf(listener);
      if (idx >= 0) list.splice(idx, 1);
    };
  }

  /**
   * Subscribe to ALL event types
   */
  onAll(listener: EventListener): () => void {
    const unsubFns: (() => void)[] = [];
    const types: TradingEventType[] = [
      'SIGNAL_ENTRY', 'SIGNAL_EXIT', 'REGIME_CHANGE',
      'TRADE_CLOSED', 'INDICATOR_UPDATE', 'CLUSTER_ALERT', 'SL_TP_REVIEW',
    ];
    for (const t of types) {
      unsubFns.push(this.on(t, listener));
    }
    return () => unsubFns.forEach(fn => fn());
  }

  /**
   * Emit event → ส่งไปทุก listener ที่ subscribe
   */
  async emit(event: TradingEvent): Promise<void> {
    if (this._paused) {
      atLog(`[EventBus] PAUSED — dropping ${event.type} for ${event.symbol}`);
      return;
    }

    // Log event
    this.eventLog.push(event);
    if (this.eventLog.length > this.maxLogSize) {
      this.eventLog = this.eventLog.slice(-this.maxLogSize);
    }

    const listeners = this.listeners.get(event.type) || [];
    if (listeners.length === 0) {
      atLog(`[EventBus] ${event.type} for ${event.symbol} — no listeners`);
      return;
    }

    atLog(`[EventBus] ⚡ ${event.type} ${event.symbol} → ${listeners.length} listener(s) | ${event.data.reason}`);

    // Execute listeners concurrently but catch errors
    const results = await Promise.allSettled(
      listeners.map(fn => fn(event))
    );

    for (const result of results) {
      if (result.status === 'rejected') {
        atWarn(`[EventBus] Listener error on ${event.type}: ${result.reason}`);
      }
    }
  }

  /**
   * Helper: emit SIGNAL_ENTRY event
   */
  emitSignalEntry(
    symbol: string,
    side: 'BUY' | 'SELL',
    entry: number,
    sl: number,
    tp: number,
    confidence: number,
    confluenceStars: number,
    strategy: StrategyType,
    triggers: string[],
    smcSnapshot?: any
  ): Promise<void> {
    return this.emit({
      type: 'SIGNAL_ENTRY',
      symbol,
      timestamp: Date.now(),
      data: {
        side,
        entry,
        sl,
        tp,
        strategy,
        confidence,
        confluenceStars,
        triggers,
        reason: `${side} signal: ${triggers.join(', ')}`,
        smcSnapshot,
      },
    });
  }

  /**
   * Helper: emit REGIME_CHANGE event
   */
  emitRegimeChange(
    symbol: string,
    structureEvent: string,
    reason: string
  ): Promise<void> {
    return this.emit({
      type: 'REGIME_CHANGE',
      symbol,
      timestamp: Date.now(),
      data: { structureEvent, reason },
    });
  }

  /**
   * Helper: emit TRADE_CLOSED event
   */
  emitTradeClosed(
    symbol: string,
    position: PositionRow,
    reason: string
  ): Promise<void> {
    return this.emit({
      type: 'TRADE_CLOSED',
      symbol,
      timestamp: Date.now(),
      data: { position, ticket: position.ticket, reason },
    });
  }

  /**
   * Pause / Resume event processing
   */
  pause(): void { this._paused = true; }
  resume(): void { this._paused = false; }
  get paused(): boolean { return this._paused; }

  /**
   * Get recent event log
   */
  getRecentEvents(limit: number = 50): TradingEvent[] {
    return this.eventLog.slice(-limit);
  }

  /**
   * Get stats
   */
  stats(): { listenerCount: number; eventLogSize: number; paused: boolean } {
    let listenerCount = 0;
    for (const [, list] of this.listeners) listenerCount += list.length;
    return { listenerCount, eventLogSize: this.eventLog.length, paused: this._paused };
  }

  /**
   * Clear all listeners (for testing)
   */
  clear(): void {
    this.listeners.clear();
    this.eventLog = [];
  }
}

// Singleton
export const eventBus = new EventBus();

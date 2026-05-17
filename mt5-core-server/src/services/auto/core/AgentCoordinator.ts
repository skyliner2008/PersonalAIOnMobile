/**
 * Agent Coordinator — AI Approval Mode (Phase 4)
 * รับ events จาก EventBus → ปลุก AI agents ที่เกี่ยวข้อง
 * AI เป็น Approver ไม่ใช่ Driver — EA ตัดสินใจหลัก, AI ยืนยัน/ปฏิเสธ
 *
 * Event → Agent mapping:
 * - SIGNAL_ENTRY  → Analyst → Risk Officer → Execution → SL/TP
 * - SIGNAL_EXIT   → SL/TP Agent
 * - REGIME_CHANGE → Analyst (ปรับ bias)
 * - TRADE_CLOSED  → Post-Mortem
 * - CLUSTER_ALERT → Risk Officer
 *
 * 2026-05-06 — skyliner.jojo@gmail.com
 */

import { eventBus, type TradingEvent } from './EventBus.js';
import type { EntrySignal } from './SignalDetector.js';
import type { AutoTradingConfig, PositionRow } from '../types.js';
import { atLog, atWarn, atError, round2 } from '../utils.js';

// ── Agent Decision Types ────────────────────────────────────────────

export interface AgentApprovalResult {
  approved: boolean;
  agentName: string;
  confidence: number;
  reason: string;
  reason_th: string;
  modifiedSl?: number;
  modifiedTp?: number;
  modifiedVolume?: number;
  processingTimeMs: number;
}

export interface ApprovalPipelineResult {
  approved: boolean;
  signal: EntrySignal | null;
  agentResults: AgentApprovalResult[];
  totalProcessingTimeMs: number;
  source: 'EA_ONLY' | 'EA_AI_APPROVED' | 'EA_AI_REJECTED' | 'AI_UNAVAILABLE';
  reason: string;
}

// ── Coordinator Config ──────────────────────────────────────────────

export interface AgentCoordinatorConfig {
  enableAiApproval: boolean;       // เปิด/ปิด AI approval (default: true)
  aiTimeoutMs: number;             // timeout per agent call (default: 15000)
  minConfidenceForAuto: number;    // EA confidence ≥ นี้ ข้าม AI ได้ (default: 80)
  maxAiCallsPerHour: number;      // จำกัดการเรียก AI (default: 20)
  fallbackToEaOnAiError: boolean;  // ถ้า AI error ให้ EA ตัดสินใจเอง (default: true)
}

const DEFAULT_CONFIG: AgentCoordinatorConfig = {
  enableAiApproval: true,
  aiTimeoutMs: 15_000,
  minConfidenceForAuto: 80,
  maxAiCallsPerHour: 20,
  fallbackToEaOnAiError: true,
};

// ── Agent Coordinator Class ─────────────────────────────────────────

class AgentCoordinator {
  private config: AgentCoordinatorConfig = { ...DEFAULT_CONFIG };
  private aiCallsThisHour = 0;
  private hourResetAt = 0;
  private _initialized = false;

  // External callback — set by AutoTradingService to invoke existing agents
  private _onSignalEntry: ((event: TradingEvent) => Promise<ApprovalPipelineResult>) | null = null;
  private _onRegimeChange: ((event: TradingEvent) => Promise<void>) | null = null;
  private _onTradeClosed: ((event: TradingEvent) => Promise<void>) | null = null;
  private _onSignalExit: ((event: TradingEvent) => Promise<void>) | null = null;
  private lastIndicatorUpdateAt: Map<string, number> = new Map();

  /**
   * Initialize coordinator — subscribe to EventBus
   */
  initialize(config?: Partial<AgentCoordinatorConfig>): void {
    if (this._initialized) return;
    if (config) this.config = { ...DEFAULT_CONFIG, ...config };

    // Subscribe to EventBus events
    eventBus.on('SIGNAL_ENTRY', (e) => this.handleSignalEntry(e));
    eventBus.on('REGIME_CHANGE', (e) => this.handleRegimeChange(e));
    eventBus.on('TRADE_CLOSED', (e) => this.handleTradeClosed(e));
    eventBus.on('SIGNAL_EXIT', (e) => this.handleSignalExit(e));
    eventBus.on('CLUSTER_ALERT', (e) => this.handleClusterAlert(e));
    eventBus.on('INDICATOR_UPDATE', (e) => this.handleIndicatorUpdate(e));

    this._initialized = true;
    atLog('[AgentCoordinator] Initialized — listening to EventBus');
  }

  /**
   * Set callbacks for external integration
   */
  setHandlers(handlers: {
    onSignalEntry?: (event: TradingEvent) => Promise<ApprovalPipelineResult>;
    onRegimeChange?: (event: TradingEvent) => Promise<void>;
    onTradeClosed?: (event: TradingEvent) => Promise<void>;
    onSignalExit?: (event: TradingEvent) => Promise<void>;
  }): void {
    if (handlers.onSignalEntry) this._onSignalEntry = handlers.onSignalEntry;
    if (handlers.onRegimeChange) this._onRegimeChange = handlers.onRegimeChange;
    if (handlers.onTradeClosed) this._onTradeClosed = handlers.onTradeClosed;
    if (handlers.onSignalExit) this._onSignalExit = handlers.onSignalExit;
  }

  // ── Event Handlers ──────────────────────────────────────────────

  private async handleSignalEntry(event: TradingEvent): Promise<void> {
    const { symbol, data } = event;
    const startTime = Date.now();

    atLog(`[AgentCoordinator] 📥 SIGNAL_ENTRY ${symbol} ${data.side} conf=${data.confidence} stars=${data.confluenceStars}`);

    // Check AI rate limit
    this.checkHourlyReset();
    if (this.aiCallsThisHour >= this.config.maxAiCallsPerHour) {
      atWarn(`[AgentCoordinator] AI rate limit reached (${this.aiCallsThisHour}/${this.config.maxAiCallsPerHour}/hr) — using EA-only mode`);
      return;
    }

    // Decision: use AI approval or EA-only?
    const confidence = data.confidence ?? 0;
    const useAi = this.config.enableAiApproval && confidence < this.config.minConfidenceForAuto;

    if (!useAi) {
      atLog(`[AgentCoordinator] EA confidence ${confidence}% ≥ ${this.config.minConfidenceForAuto}% — auto-approving without AI`);
      return;
    }

    // Run AI approval pipeline
    if (this._onSignalEntry) {
      try {
        this.aiCallsThisHour++;
        const result = await Promise.race([
          this._onSignalEntry(event),
          this.timeout(this.config.aiTimeoutMs),
        ]) as ApprovalPipelineResult;

        const elapsed = Date.now() - startTime;
        atLog(`[AgentCoordinator] AI Pipeline result: ${result.approved ? '✅ APPROVED' : '❌ REJECTED'} in ${elapsed}ms — ${result.reason}`);
      } catch (err) {
        atWarn(`[AgentCoordinator] AI Pipeline error: ${err}`);
        if (this.config.fallbackToEaOnAiError) {
          atLog(`[AgentCoordinator] Falling back to EA-only decision`);
        }
      }
    }
  }

  private async handleRegimeChange(event: TradingEvent): Promise<void> {
    atLog(`[AgentCoordinator] 🔄 REGIME_CHANGE ${event.symbol} — ${event.data.structureEvent}`);
    if (this._onRegimeChange) {
      try {
        await this._onRegimeChange(event);
      } catch (err) {
        atWarn(`[AgentCoordinator] Regime change handler error: ${err}`);
      }
    }
  }

  private async handleTradeClosed(event: TradingEvent): Promise<void> {
    atLog(`[AgentCoordinator] 📊 TRADE_CLOSED ${event.symbol} #${event.data.ticket}`);
    if (this._onTradeClosed) {
      try {
        this.aiCallsThisHour++;
        await this._onTradeClosed(event);
      } catch (err) {
        atWarn(`[AgentCoordinator] Trade closed handler error: ${err}`);
      }
    }
  }

  private async handleSignalExit(event: TradingEvent): Promise<void> {
    atLog(`[AgentCoordinator] 🚪 SIGNAL_EXIT ${event.symbol} — ${event.data.reason}`);
    if (this._onSignalExit) {
      try {
        await this._onSignalExit(event);
      } catch (err) {
        atWarn(`[AgentCoordinator] Signal exit handler error: ${err}`);
      }
    }
  }

  private async handleClusterAlert(event: TradingEvent): Promise<void> {
    atLog(`[AgentCoordinator] ⚠️ CLUSTER_ALERT ${event.symbol} — ${event.data.clusterAlert?.type}: ${event.data.clusterAlert?.value}`);
  }

  // ── Approval Pipeline (called by AutoTradingService) ────────────

  /**
   * Run full AI approval pipeline for an entry signal.
   * This is the main function called by the engine when AI approval is needed.
   *
   * Flow: Signal → Analyst → Risk Officer → Execution → SL/TP
   */
  private handleIndicatorUpdate(event: TradingEvent): void {
    this.lastIndicatorUpdateAt.set(event.symbol, event.timestamp);
  }

  async runApprovalPipeline(
    signal: EntrySignal,
    tradingConfig: AutoTradingConfig,
    positions: PositionRow[]
  ): Promise<ApprovalPipelineResult> {
    const startTime = Date.now();
    const results: AgentApprovalResult[] = [];

    // Step 1: Check if EA confidence is high enough to skip AI
    if (signal.confidence >= this.config.minConfidenceForAuto && signal.confluenceStars >= 3) {
      return {
        approved: true,
        signal,
        agentResults: [],
        totalProcessingTimeMs: Date.now() - startTime,
        source: 'EA_ONLY',
        reason: `EA confidence ${signal.confidence}% with ${signal.confluenceStars}★ — auto-approved`,
      };
    }

    // Step 2: Check AI availability
    this.checkHourlyReset();
    if (this.aiCallsThisHour >= this.config.maxAiCallsPerHour) {
      if (this.config.fallbackToEaOnAiError) {
        return {
          approved: signal.confidence >= 65,
          signal,
          agentResults: [],
          totalProcessingTimeMs: Date.now() - startTime,
          source: 'AI_UNAVAILABLE',
          reason: `AI rate limit — EA fallback (conf=${signal.confidence}%)`,
        };
      }
      return {
        approved: false, signal, agentResults: [],
        totalProcessingTimeMs: Date.now() - startTime,
        source: 'AI_UNAVAILABLE',
        reason: 'AI rate limit exceeded',
      };
    }

    this.aiCallsThisHour++;

    // Step 3: Run through agents (stubbed — actual LLM calls happen in AutoTradingService)
    // The coordinator prepares the decision context; the actual AI calls
    // are delegated to AutoTradingService via the _onSignalEntry callback.
    //
    // For now, return EA-only decision with enhanced data
    return {
      approved: signal.confidence >= 60 && signal.confluenceStars >= 2,
      signal,
      agentResults: results,
      totalProcessingTimeMs: Date.now() - startTime,
      source: 'EA_ONLY',
      reason: `EA decision: conf=${signal.confidence}% stars=${signal.confluenceStars}★ rrr=${signal.riskRewardRatio}`,
    };
  }

  // ── Helpers ─────────────────────────────────────────────────────

  private checkHourlyReset(): void {
    const now = Date.now();
    if (now > this.hourResetAt) {
      this.aiCallsThisHour = 0;
      this.hourResetAt = now + 3_600_000;
    }
  }

  private timeout(ms: number): Promise<never> {
    return new Promise((_, reject) =>
      setTimeout(() => reject(new Error(`AI timeout after ${ms}ms`)), ms)
    );
  }

  /**
   * Get coordinator stats
   */
  stats(): { aiCallsThisHour: number; maxPerHour: number; initialized: boolean } {
    return {
      aiCallsThisHour: this.aiCallsThisHour,
      maxPerHour: this.config.maxAiCallsPerHour,
      initialized: this._initialized,
    };
  }

  /**
   * Update config
   */
  updateConfig(config: Partial<AgentCoordinatorConfig>): void {
    this.config = { ...this.config, ...config };
  }
}

// Singleton
export const agentCoordinator = new AgentCoordinator();

// Provider-agnostic: all AI calls go through providers/dispatcher
import { db, getDb } from '../db.js';
import { asArray, callBridge } from './bridgeClient.js';
import { parseCandles } from './tracking.js';
import { inferAnalysis, pickStrategy } from './auto/analysis.js';
import { matchHistoryDeal, detectCloseReason, profitToR, extractDealClosePrice, extractDealProfit, classifyOutcome, aggregateDealProfits } from './auto/journal.js';
import { gateTrade } from './auto/risk.js';
import { agentOrchestrator } from './auto/agentOrchestrator.js';
import { vectorStore } from './auto/vectorStore.js';
import { knowledgeGraph } from './auto/knowledgeGraph.js';
import { broadcast } from './mt5RealtimeHub.js';
import { analyzeCorrelation } from './auto/analyzers/correlation.js';
import { newsAnalyzer, NewsAnalyzer } from './auto/analyzers/news.js';
import { crossAssetAnalyzer } from './auto/analyzers/crossAsset.js';
import type {
  AutoTradingConfig,
  EngineState,
  CycleDecision,
  LearnSummary,
  AutoTradingSnapshot,
  JournalRow,
  PositionRow,
  PositionCluster,
  AccountSnapshot,
  StrategyType,
  StrategyStat,
  AnalyzerStat,
  MarketRegime,
  Bias,
  ManagementPlan,
  PlaybookScores,
  ManagementMode,
  ManagementJournalRow,
} from './auto/types.js';
import { DEFAULT_GATE_TOGGLES, DEFAULT_STRATEGY_TOGGLES } from './auto/types.js';
import { atLog, atWarn, atError, getLogTime, nowIso, safeJsonParse, unwrapData, asRecord, asNumber, asString, round2, round1, clamp, moneyPerPriceUnit, tickSize, roundToTick, normalizeConfig } from './auto/utils.js';
import { traceStorage } from './logger.js';
import { cycleDurationSeconds, tradesPlacedTotal, tradesRejectedTotal, zoneGateBlocksTotal, slBufferActivationsTotal, counterTrendBlocksTotal, entryZoneDistribution, managementEventsTotal } from './metrics.js';
import { memoryConsolidationService } from './auto/memoryConsolidation.js';
import { persistenceService, defaultConfig, defaultState } from './auto/core/PersistenceService.js';
import { CircuitBreaker } from './auto/risk/circuitBreaker.js';
import { AnalystAgent } from './auto/agents/analyst.js';
import { RiskOfficerAgent } from './auto/agents/riskOfficer.js';
import { ExecutionAgent } from './auto/agents/executionTrader.js';
import { PostMortemAgent, detectFailurePattern } from './auto/agents/postMortem.js';
import { classifyPremiumDiscount, getActiveFVGs, isSupportedBySMC, getSmcStructure } from './auto/analyzers/smc.js';
import { slTpAnalystAgent, pickSlTpDeterministic } from './auto/agents/slTpAnalyst.js';
import { tradeManagementService } from './auto/core/TradeManagementService.js';
import { tradingExecutionService } from './auto/core/TradingExecutionService.js';
import { marketDataService } from './auto/core/MarketDataService.js';
import { deterministicDecide, llmCache, marketStateHashLoose } from './auto/deterministicEngine.js';
import { parseLlmJson } from './auto/agents/jsonParse.js';
import {
  resolveProviderCredentials,
  resolveAgentChoice,
  AgentRole,
} from './auto/modelResolver.js';
import { spreadBaseline } from './auto/spreadBaseline.js';
import { modelRankerService } from './auto/core/ModelRankerService.js';

// ── V21.0 Event-Driven Architecture Modules ──────────────────────
import { eventBus } from './auto/core/EventBus.js';
import { indicatorPipeline } from './auto/core/IndicatorPipeline.js';
import { detectEntrySignals } from './auto/core/SignalDetector.js';
import { agentCoordinator } from './auto/core/AgentCoordinator.js';
import { computeSmcTrailTargets, detectRegimeFlipAction, improvedAntiHedge } from './auto/core/SmcTrailManager.js';
import { getSmcSnapshotV2 } from './auto/analyzers/smc.js';
// V24.1.3 (2026-05-08): PriceMap path analyzer for EA-Only TP cap
import { analyzePath } from './auto/analyzers/smc/priceMapBuilder.js';
// V24.0 — Fade-the-Level Proximity Gate
import { computeAdaptiveProximityMaxPip, evaluateProximityGate } from './auto/core/ProximityGate.js';
import { resolveDecisionSide } from './auto/core/DecisionGuards.js';
import { buildZoneLayerContext, findAlignedFvgFill, findOpposingScalpPressure, hasAlignedFvgNearPrice } from './auto/core/ZoneAwareGate.js';
import { extractV25PathMilestone, v25MilestoneReached } from './auto/core/V25MilestoneProtection.js';
// V25.0 — Wall State Machine + config-driven bootstrap
import { wallStateMachine } from './auto/v25/state/WallStateMachine.js';
import { bootstrapV25ShadowFromConfig, shutdownV25Shadow } from './auto/v25/index.js';
import { tickBuffers } from './auto/v25/TickBuffer.js';
import { executableMinRrrForPlan, liveMarketRiskIssue, playbookSelector } from './auto/v25/playbooks/index.js';
import type { TradePlan } from './auto/v25/playbooks/types.js';

import { LearningEngine } from './auto/core/LearningEngine.js';
import { PositionManagerEngine } from './auto/core/PositionManagerEngine.js';
import { MarketCycleEngine } from './auto/core/MarketCycleEngine.js';
import { SimpleScalpingEngine } from './auto/core/SimpleScalpingEngine.js';

// Note: staleCloseBackoff and earlyInvalidationBackoff have been relocated to PositionManagerEngine.ts

type TradeSide = 'BUY' | 'SELL';
type DecisionTraceStatus = 'INFO' | 'ALLOW' | 'BLOCK' | 'SKIP' | 'WARN' | 'EXECUTED' | 'FAILED';
type DecisionTraceStep = {
  at: string;
  stage: string;
  status: DecisionTraceStatus;
  reason: string;
  data?: Record<string, unknown>;
};

function pushDecisionTrace(
  trace: DecisionTraceStep[],
  stage: string,
  status: DecisionTraceStatus,
  reason: string,
  data?: Record<string, unknown>,
): void {
  trace.push({
    at: nowIso(),
    stage,
    status,
    reason,
    ...(data ? { data } : {}),
  });
}
import {
  compactAnalysisForLog,
  compactMtfForLog,
  compactDeterministicForLog,
  classifyDecisionBlockCategory,
} from './auto/helpers/decisionLogger.js';

import {
  upperText,
  isGateEnabled,
  isStrategyEnabled,
  disabledStrategyReason,
  isScalpLikeStrategy,
  isV25Strategy,
  isMeanReversionStrategy,
  isWallFvgMagnetScalp,
  isLocalProofScalpStrategy,
  v25StrategyForPlan,
  v25PlanReason,
  unifiedV25CandidateTtlMs,
  selectUnifiedV25Candidate,
  resolveFreshDecisionPrice,
  duplicateIntentTtlForStrategy,
  targetRrrForStrategy,
  finalRrrFloorForStrategy,
  hasContinuationIntent,
  htfBiasAgrees,
  biasOpposesSide,
  ltfConsensusBlockReason,
  postTakeProfitCooldownIssue,
  isDeepWrongZone,
  describeZoneMissingEvidence,
  v25AlignedDirectEntry,
} from './auto/helpers/strategySelector.js';

import {
  buildPositionCluster,
  clampFraction,
  extractTicket,
  getTfMs,
  buildSkipDecision,
} from './auto/helpers/tradingHelpers.js';

import {
  isSymbolTradable,
  isMarketOpen,
} from './auto/helpers/marketHelpers.js';

import {
  closedTradeService,
} from './auto/helpers/closedTradeService.js';


export class AutoTradingService {
  public config = persistenceService.loadConfig();
  public state = persistenceService.loadRuntimeState();
  public lastDecisions = persistenceService.loadLastDecisions();
  public openJournal = persistenceService.loadOpenJournal();
  public learnSummary = persistenceService.loadLearnSummary();

  public analyst: AnalystAgent | null = null;
  public riskOfficer: RiskOfficerAgent | null = null;
  public executionTrader: ExecutionAgent | null = null;

  public cycleTimer: NodeJS.Timeout | null = null;
  public manageTimer: NodeJS.Timeout | null = null;
  public learnTimer: NodeJS.Timeout | null = null;
  // 2026-05-01 Phase 2.5 — daily refresh ของ OpenRouter free pool
  public modelSyncTimer: NodeJS.Timeout | null = null;
  public cycleBusy = false;
  public manageBusy = false;
  // P2.3 cooldown tracker  prevents close-weakest from churning the book
  public closeWeakestLastAt: Map<string, number> = new Map();
  public lastCycleTime = 0;
  public v25MilestoneProtectedTickets = new Set<number>();
  public symbolTimeframes: Map<string, string> = new Map(); // Active TF per symbol
  public learnBusy = false;
  public lastMarketAwareManageAt = 0;
  public aiCooldownUntil = 0;
  public lastConfigReloadAt = 0;
  public clusterMaxProfit: Map<string, number> = new Map();
  public aiFallbackCircuitLastLogAt: Map<string, number> = new Map();
  // 2026-05-15 V26.2 — MTF Analysis Cache for Event-Driven updates
  public mtfCache: Map<string, Map<string, { candles: any[]; analysis: any }>> = new Map();
  public lastTfUpdate: Map<string, Map<string, number>> = new Map();


  private isSimpleScalpingMode(config: AutoTradingConfig = this.config): boolean {
    return SimpleScalpingEngine.isEnabled(config);
  }

  constructor() {
    // 2026-04-30: Clean up any legacy seeded model stats before starting
    if (!this.isSimpleScalpingMode()) {
      modelRankerService.cleanupLegacyStats();
    }

    // 2026-05-02 Phase 3.1 — Boot-time OpenRouter sync (เดิมรันแค่ใน runLearn())
    //   ทำให้ทุก boot (ทั้ง resume และ fresh) ได้ pool ทันสมัยตั้งแต่ cycle แรก
    //   + ตั้ง daily timer ไว้เลยเพื่อให้ refresh ทุก 24 ชม.
    if (!this.isSimpleScalpingMode()) {
      this.bootstrapOpenRouterSync().catch(err => atWarn(`[AutoEngine] Boot sync failed: ${err}`));
    }

    // 2026-05-06 V21.0 — Initialize Event-Driven Architecture modules
    if (!this.isSimpleScalpingMode()) {
      agentCoordinator.initialize();
      atLog('[AutoEngine] V21.0 Event-Driven Architecture initialized (EventBus + IndicatorPipeline + AgentCoordinator)');
    } else {
      eventBus.pause();
      shutdownV25Shadow();
      atLog('[AutoEngine] Simple mode active: M15 Wall Scalping only (PriceMap/Pipeline, no AI/legacy gate cascade)');
    }

    // V23.0 — Wire AgentCoordinator handlers so EventBus signals reach the AI pipeline
    agentCoordinator.setHandlers({
      onSignalEntry: async (event) => {
        const cfg = this.config;
        const d = event.data;
        // Reconstruct EntrySignal from TradingEventData fields
        if (!d.side || d.entry == null || d.sl == null || d.tp == null) {
          return { approved: false, signal: null, agentResults: [], totalProcessingTimeMs: 0, source: 'EA_ONLY', reason: 'Incomplete signal data in event' };
        }
        const signal = {
          symbol: event.symbol,
          side: d.side,
          entry: d.entry,
          sl: d.sl,
          tp: d.tp,
          confidence: d.confidence ?? 50,
          confluenceStars: d.confluenceStars ?? 2,
          strategy: d.strategy ?? 'TREND_FOLLOW',
          triggers: d.triggers ?? [],
          riskRewardRatio: Math.abs(d.tp - d.entry) / Math.abs(d.entry - d.sl),
        };
        const positions = await this.fetchPositions().catch(() => []);
        // Delegate to runApprovalPipeline
        const result = await agentCoordinator.runApprovalPipeline(signal, cfg, positions);
        atLog(`[AgentCoordinator] onSignalEntry result: ${result.source} approved=${result.approved} — ${result.reason}`);
        return result;
      },
      onTradeClosed: async (event) => {
        // Post-mortem logging (full analysis happens in runLearn)
        atLog(`[AgentCoordinator] onTradeClosed: #${event.data.ticket} — triggering async post-mortem log`);
      },
    });

    // Auto-resume: if the engine was running before server restart, resume loops.
    // 2026-04-25 (MEDIUM fix): defer the first cycle 15s so any in-flight
    // upsertJournal that was mid-write at shutdown gets reloaded into memory
    // before runManage() tries to backfill orphan positions. Without this delay
    // the manage loop could write a synthetic BACKFILL row that shadows the
    // real journal entry.
    if (this.state.running) {
      // eslint-disable-next-line no-console
      atLog('[AutoEngine]  Auto-resuming from previous session (state was running). First cycle deferred 15s for journal recovery.');
      setTimeout(() => {
        this.scheduleCycleLoop();
        if (!this.isSimpleScalpingMode()) {
          this.scheduleManageLoop();
          this.scheduleLearnLoop();
        }
      }, 15_000);
    }
  }

  /**
   * 2026-05-02 Phase 3.1 — Boot-time OpenRouter sync + start daily timer.
   * เรียกจาก constructor เพื่อให้ pool ทันสมัยตั้งแต่ cycle แรก (ไม่ต้องรอ learn loop)
   */
  private async bootstrapOpenRouterSync(): Promise<void> {
    try {
      const orKeys = resolveProviderCredentials(this.config, 'openrouter');
      if (!orKeys.apiKey) {
        atLog('[AutoEngine] OpenRouter sync skipped (no API key)');
        return;
      }
      const r = await modelRankerService.syncDiscoveredModels(orKeys.apiKey, orKeys.baseUrl);
      atLog(`[AutoEngine] Boot OpenRouter sync: total=${r.totalFree} added=${r.added} removed=${r.removed} kept=${r.kept}`);
    } catch (err) {
      atWarn(`[AutoEngine] Boot OpenRouter sync failed: ${err}`);
    }
    // Schedule daily refresh (idempotent — clears previous timer if any)
    this.scheduleDailyModelSync();
  }

  snapshot(): AutoTradingSnapshot {
    return {
      config: this.config,
      state: this.state,
      lastDecisions: this.lastDecisions,
      openJournal: this.openJournal,
      learnSummary: this.learnSummary,
      serverMode: 'server',
    };
  }

  async start(): Promise<AutoTradingSnapshot> {
    if (this.state.running) return this.snapshot();
    // eslint-disable-next-line no-console
    atLog('[AutoEngine]  STARTING...');
    this.state = { ...this.state, running: true, phase: 'IDLE', message: 'server engine started' };
    this.persistRuntime();
    // V21.0 — Resume EventBus on start
    if (this.isSimpleScalpingMode()) {
      eventBus.pause();
      shutdownV25Shadow();
    } else {
      eventBus.resume();
    }
    this.runCycle().catch(err => atError('[AutoEngine] error in first cycle:', err)); 
    this.scheduleCycleLoop();
    if (!this.isSimpleScalpingMode()) {
      this.scheduleManageLoop();
      this.scheduleLearnLoop();
    }
    return this.snapshot();
  }

  async stop(): Promise<AutoTradingSnapshot> {
    // eslint-disable-next-line no-console
    atLog('[AutoEngine]  STOPPING...');
    this.clearTimers();
    // V21.0 — Pause EventBus on stop
    eventBus.pause();
    this.state = { ...this.state, running: false, phase: 'STOPPED', message: 'server engine stopped' };
    this.persistRuntime();
    return this.snapshot();
  }

  async runOnce(): Promise<AutoTradingSnapshot> {
    await this.runCycle();
    return this.snapshot();
  }

  async getManagementJournal(limit = 100): Promise<ManagementJournalRow[]> {
    return persistenceService.getManagementJournal(limit);
  }

  async getDecisionJournal(limit = 120): Promise<JournalRow[]> {
    return persistenceService.getDecisionJournal(limit);
  }

  async getDecisionFeed(limit = 180): Promise<any[]> {
    return persistenceService.getDecisionFeed(limit);
  }

  async learnNow(): Promise<AutoTradingSnapshot> {
    await this.runLearn();
    return this.snapshot();
  }

  updateConfig(input: Partial<AutoTradingConfig>): AutoTradingSnapshot {
    const merged = normalizeConfig(input, this.config);
    // Deep-merge nested bags so a partial dashboard PATCH (e.g. only setting
    // agentModels.analyst) doesn't drop the rest of the user's choices.

    // V25: deep-merge adaptive.v25 so UI toggle for enableV25Only
    // doesn't accidentally wipe ATR multipliers or other v25 sub-keys.
    if (input?.adaptive) {
      const baseAdaptive = (this.config.adaptive || {}) as Record<string, any>;
      const nextAdaptive = (input.adaptive || {}) as Record<string, any>;
      const mergedAdaptive: Record<string, any> = { ...baseAdaptive, ...nextAdaptive };
      if (nextAdaptive.gateToggles !== undefined) {
        mergedAdaptive.gateToggles = { ...(baseAdaptive.gateToggles || {}), ...nextAdaptive.gateToggles };
      }
      if (nextAdaptive.strategyToggles !== undefined) {
        mergedAdaptive.strategyToggles = { ...(baseAdaptive.strategyToggles || {}), ...nextAdaptive.strategyToggles };
      }
      // Granularly merge the v25 sub-object
      if (nextAdaptive.v25 !== undefined) {
        mergedAdaptive.v25 = { ...(baseAdaptive.v25 || {}), ...nextAdaptive.v25 };
      }
      if (nextAdaptive.simpleScalping !== undefined) {
        mergedAdaptive.simpleScalping = { ...(baseAdaptive.simpleScalping || {}), ...nextAdaptive.simpleScalping };
      }
      (merged as any).adaptive = mergedAdaptive;
    }

    if (input?.agentModels) {
      const baseBag = (this.config.agentModels || {}) as Record<string, any>;
      const nextBag = (input.agentModels || {}) as Record<string, any>;
      const mergedBag: Record<string, any> = { ...baseBag };
      
      for (const role of Object.keys(nextBag)) {
        const val = nextBag[role];
        if (!val) continue;
        if (typeof val === 'string') {
          if (val.includes('/')) {
            const parts = val.split('/');
            mergedBag[role] = { provider: parts[0], model: parts.slice(1).join('/') };
          } else {
            mergedBag[role] = { provider: 'gemini', model: val };
          }
        } else {
          mergedBag[role] = { ...val };
        }
      }
      merged.agentModels = mergedBag;
    }
    this.config = merged;
    this.persistConfig();
    // In-process update is authoritative — bump the reload timestamp so the
    // next cycle won't immediately re-read the row we just wrote.
    this.lastConfigReloadAt = Date.now();
    if (this.state.running) {
      this.scheduleCycleLoop();
      if (!this.isSimpleScalpingMode(merged)) {
        this.scheduleManageLoop();
        this.scheduleLearnLoop();
      } else {
        if (this.manageTimer) clearTimeout(this.manageTimer);
        if (this.learnTimer) clearTimeout(this.learnTimer);
        this.manageTimer = null;
        this.learnTimer = null;
      }
    }
    // V25: Sync shadow pipeline with updated config/watchlist.
    const v25cfg = (merged as any).adaptive?.v25 || {};
    if (this.isSimpleScalpingMode(merged)) {
      shutdownV25Shadow();
    } else if (v25cfg.enabled) {
      bootstrapV25ShadowFromConfig(v25cfg, merged.watchlist);
    } else if (input?.adaptive?.v25 !== undefined && !v25cfg.enabled) {
      shutdownV25Shadow();
    }
    return this.snapshot();
  }

  async getPerformanceAnalytics(daysWindow = 0) {
    // 2026-04-25: optional time-window filter for the dashboard.
    // daysWindow=0 means "all trades"; otherwise restrict to the last N days.
    const cutoff = daysWindow > 0 ? Date.now() - daysWindow * 86_400_000 : 0;
    const rows = db.prepare(
      cutoff > 0
         ? `SELECT profit, profit_r as profitR, outcome, side, strategy, symbol, created_at
              FROM auto_trading_journal
             WHERE outcome IN ('WIN', 'LOSS', 'BE')
               AND learning_eligible = 1
               AND created_at >= ?
             ORDER BY created_at ASC`
         : `SELECT profit, profit_r as profitR, outcome, side, strategy, symbol, created_at
              FROM auto_trading_journal
             WHERE outcome IN ('WIN', 'LOSS', 'BE')
               AND learning_eligible = 1
             ORDER BY created_at ASC`
    ).all(...(cutoff > 0 ? [cutoff] : [])) as any[];

    if (rows.length === 0) {
      return {
        windowDays: daysWindow,
        empty: true,
        message: 'No closed trades in window',
        equityCurve: [],
        stats: null,
        breakdown: { byStrategy: [], bySymbol: [], bySide: { BUY: 0, SELL: 0 }, outcomes: { WIN: 0, LOSS: 0, BE: 0 } },
      };
    }

    let cumulativeProfit = 0;
    let peak = 0;
    let maxDrawdown = 0;
    const equityCurve = rows.map((t) => {
      cumulativeProfit += (Number(t.profit) || 0);
      if (cumulativeProfit > peak) peak = cumulativeProfit;
      const dd = peak - cumulativeProfit;
      if (dd > maxDrawdown) maxDrawdown = dd;
      return { t: t.created_at, profit: round2(cumulativeProfit) };
    });

    const wins = rows.filter((t) => t.profit > 0);
    const losses = rows.filter((t) => t.profit < 0);
    const totalWin = wins.reduce((s, t) => s + t.profit, 0);
    const totalLoss = Math.abs(losses.reduce((s, t) => s + t.profit, 0));

    const profitFactor = totalLoss === 0 ? totalWin : round2(totalWin / totalLoss);
    const winRate = round2(wins.length / rows.length);
    const expectancy = round2((totalWin - totalLoss) / rows.length);
    const avgWin = wins.length ? round2(totalWin / wins.length) : 0;
    const avgLoss = losses.length ? round2(totalLoss / losses.length) : 0;

    const returns = rows.map((t) => Number(t.profit) || 0);
    const avgReturn = returns.reduce((a, b) => a + b, 0) / returns.length;
    const stdDev = Math.sqrt(returns.map((x) => Math.pow(x - avgReturn, 2)).reduce((a, b) => a + b, 0) / returns.length);
    const sharpeRatio = stdDev === 0 ? 0 : round2(avgReturn / stdDev);

    // Streaks
    let curStreak = 0, bestWin = 0, worstLoss = 0;
    let prev: 'W' | 'L' | null = null;
    for (const t of rows) {
      const sign = (Number(t.profit) || 0) > 0 ? 'W' : (Number(t.profit) || 0) < 0 ? 'L' : null;
      if (sign && sign === prev) curStreak += 1;
      else curStreak = sign ? 1 : 0;
      if (sign === 'W' && curStreak > bestWin) bestWin = curStreak;
      if (sign === 'L' && curStreak > worstLoss) worstLoss = curStreak;
      prev = sign;
    }

    // Breakdowns for the dashboard
    const groupSum = <K extends string>(key: K) => {
      const out = new Map<string, { total: number; wins: number; losses: number; net: number }>();
      for (const t of rows) {
        const k = String((t as any)[key] ?? 'unknown');
        const cur = out.get(k) || { total: 0, wins: 0, losses: 0, net: 0 };
        cur.total += 1;
        if ((t.profit || 0) > 0) cur.wins += 1;
        else if ((t.profit || 0) < 0) cur.losses += 1;
        cur.net += Number(t.profit) || 0;
        out.set(k, cur);
      }
      return Array.from(out.entries()).map(([key, v]) => ({
        key,
        total: v.total,
        wins: v.wins,
        losses: v.losses,
        winRate: v.total ? round2(v.wins / v.total) : 0,
        net: round2(v.net),
      }));
    };

    return {
      windowDays: daysWindow,
      empty: false,
      equityCurve,
      stats: {
        totalTrades: rows.length,
        winRate,
        profitFactor,
        sharpeRatio,
        expectancy,
        totalProfit: round2(cumulativeProfit),
        avgWin,
        avgLoss,
        maxWin: round2(Math.max(...returns, 0)),
        maxLoss: round2(Math.min(...returns, 0)),
        maxDrawdown: round2(maxDrawdown),
        bestWinStreak: bestWin,
        worstLossStreak: worstLoss,
      },
      breakdown: {
        byStrategy: groupSum('strategy'),
        bySymbol: groupSum('symbol'),
        bySide: {
          BUY: rows.filter((r) => r.side === 'BUY').length,
          SELL: rows.filter((r) => r.side === 'SELL').length,
        },
        outcomes: {
          WIN: rows.filter((r) => r.outcome === 'WIN').length,
          LOSS: rows.filter((r) => r.outcome === 'LOSS').length,
          BE: rows.filter((r) => r.outcome === 'BE').length,
        },
      },
    };
  }

  public persistConfig(): void {
    persistenceService.persistConfig(this.config);
  }

  public persistRuntime(): void {
    persistenceService.persistRuntime(this.state, this.lastDecisions, this.openJournal, this.learnSummary);
  }

  private scheduleCycleLoop(): void {
    if (this.cycleTimer) clearTimeout(this.cycleTimer);
    if (!this.state.running) return;
    this.cycleTimer = setTimeout(async () => {
      await this.runCycle();
      this.scheduleCycleLoop();
    }, Math.max(5_000, this.config.tickIntervalMs));
  }

  public appendDecisionFeed(cycleNo: number, timeframe: string, decisions: CycleDecision[]): void {
    persistenceService.appendDecisionFeed(cycleNo, timeframe, decisions);
  }

  private scheduleManageLoop(): void {
    if (this.manageTimer) clearTimeout(this.manageTimer);
    this.manageTimer = null;
    if (!this.state.running || this.isSimpleScalpingMode()) return;
    const hasTrackedOpenTrades = this.openJournal.some((it) => it.outcome === 'OPEN');
    const hasLivePositions = (this.state.openTradesTracked || 0) > 0;
    const hasV25ScalpOpen = hasLivePositions && this.openJournal.some((it) =>
      it.outcome === 'OPEN' && String(it.strategy ?? '').toUpperCase().startsWith('V25_')
    );
    const intervalMs = hasV25ScalpOpen
      ? 5_000
      : hasTrackedOpenTrades
      ? Math.max(15_000, this.config.manageIntervalMs)
      : hasLivePositions
      ? Math.max(20_000, this.config.manageIntervalMs)
      : Math.max(20_000, this.config.tickIntervalMs);
    this.manageTimer = setTimeout(async () => {
      await this.runManage();
      this.scheduleManageLoop();
    }, intervalMs);
  }

  private scheduleLearnLoop(): void {
    if (this.learnTimer) clearTimeout(this.learnTimer);
    this.learnTimer = null;
    if (!this.state.running || this.isSimpleScalpingMode()) return;
    this.learnTimer = setTimeout(async () => {
      await this.runLearn();
      this.scheduleLearnLoop();
    }, Math.max(60_000, this.config.learnIntervalMs));
  }

  private clearTimers(): void {
    if (this.cycleTimer) clearTimeout(this.cycleTimer);
    if (this.manageTimer) clearTimeout(this.manageTimer);
    if (this.learnTimer) clearTimeout(this.learnTimer);
    if (this.modelSyncTimer) clearInterval(this.modelSyncTimer);
    this.cycleTimer = null;
    this.manageTimer = null;
    this.learnTimer = null;
    this.modelSyncTimer = null;
  }

  /**
   * 2026-05-01 Phase 2.5 — Daily refresh ของ OpenRouter free model pool
   *   - run ทุก 24 ชม. (idempotent, clear timer เก่าก่อนตั้งใหม่)
   *   - เพิ่ม model ที่เพิ่งกลายเป็น free, ลบ model ที่หายจาก free list (และไม่เคยใช้)
   *   - ป้องกัน "stuck on 2 models" loop เพราะ pool ทันสมัยตลอด
   */
  public scheduleDailyModelSync(): void {
    if (this.modelSyncTimer) clearInterval(this.modelSyncTimer);
    // 2026-05-05 — ลดจาก 24h → 6h เพราะ OpenRouter เปลี่ยน free list บ่อย
    // Dead models (HTTP 404) ถูกพบ 2+ ตัวภายใน session เดียว
    const SYNC_INTERVAL_MS = 6 * 60 * 60_000;
    this.modelSyncTimer = setInterval(async () => {
      try {
        const orKeys = resolveProviderCredentials(this.config, 'openrouter');
        if (!orKeys.apiKey) return;
        const r = await modelRankerService.syncDiscoveredModels(orKeys.apiKey, orKeys.baseUrl);
        atLog(`[AutoEngine] Model sync: total=${r.totalFree} added=${r.added} removed=${r.removed}`);
      } catch (err) {
        atWarn(`[AutoEngine] Model sync failed: ${err}`);
      }
    }, SYNC_INTERVAL_MS);
  }

  private async runCycle(): Promise<void> {
    if (this.isSimpleScalpingMode()) {
      return SimpleScalpingEngine.runCycle(this);
    }
    return MarketCycleEngine.runCycle(this);
  }

  private async runManage(): Promise<void> {
    if (this.isSimpleScalpingMode()) return;
    return PositionManagerEngine.runManage(this);
  }

  private async runLearn(): Promise<void> {
    if (this.isSimpleScalpingMode()) return;
    return LearningEngine.runLearn(this);
  }

  public buildHistoryContext(cluster: PositionCluster, playbook?: PlaybookScores): string {
    return tradeManagementService.buildHistoryContext(cluster, playbook, this.learnSummary);
  }

  public positionRiskR(position: PositionRow, journal: JournalRow | undefined): number {
    return tradeManagementService.positionRiskR(position, journal);
  }

  public normalizeClosedJournalOutcome(row: JournalRow): JournalRow {
    return tradeManagementService.normalizeClosedJournalOutcome(row);
  }

  public normalizeCloseVolume(requested: number | null | undefined, positionVolume: number, mode: ManagementMode): number | null {
    return tradingExecutionService.normalizeCloseVolume(requested, positionVolume, mode, this.config);
  }

  public planMarketAwareManagement(
    cfg: AutoTradingConfig,
    account: AccountSnapshot,
    cluster: PositionCluster,
    analysis: any,
    aiDecision: any,
    proposedVolume: number,
    playbook: PlaybookScores,
    totalOpenPositions: number = 0
  ): ManagementPlan {
    return tradeManagementService.planMarketAwareManagement(cfg, account, cluster, analysis, aiDecision, proposedVolume, playbook, totalOpenPositions, this.openJournal);
  }

  public computePlaybookScores(
    cfg: AutoTradingConfig,
    account: AccountSnapshot,
    cluster: PositionCluster,
    analysis: any
  ): PlaybookScores {
    return tradeManagementService.computePlaybookScores(cfg, account, cluster, analysis, this.openJournal, this.learnSummary);
  }

  public async executeManagementPlan(
    symbol: string,
    plan: ManagementPlan,
    sl: number | null,
    tp: number | null,
    aiDecision: any
  ): Promise<{ executed: boolean; riskGate: string; ticket: number | null; side?: 'BUY' | 'SELL' }> {
    return PositionManagerEngine.executeManagementPlan(this, symbol, plan, sl, tp, aiDecision);
  }

  public insertManagementJournal(args: any): void {
    persistenceService.insertManagementJournal(args);
  }

  public markManagementOutcome(
    ticket: number,
    decisionId: string,
    outcome: 'WIN' | 'LOSS' | 'BE',
    profit: number,
    profitR: number | null,
    closeReason: string
  ): void {
    persistenceService.markManagementOutcome(ticket, decisionId, outcome, profit, profitR, closeReason);
  }

  public matchTicketFromOpenPositions(row: JournalRow, positions: PositionRow[]): number | null {
    const candidates = positions.filter((it) =>
      it.symbol === row.symbol &&
      it.side === row.side &&
      row.entry !== null &&
      Math.abs(it.priceOpen - row.entry) <= Math.max(0.05, row.entry * 0.0003) &&
      row.volume !== null &&
      Math.abs(it.volume - row.volume) <= 0.02
    );
    if (candidates.length === 1) return candidates[0].ticket;
    const recent = candidates.sort((a, b) => Math.abs(b.ticket) - Math.abs(a.ticket));
    return recent[0]?.ticket ?? null;
  }


  public computeVolume(account: AccountSnapshot, stopDistance: number, symbol: string, strategy?: string): number {
    return tradingExecutionService.computeVolume(account, stopDistance, symbol, this.config, strategy, this.learnSummary);
  }

  public async fetchAccount(): Promise<AccountSnapshot> {
    return marketDataService.fetchAccount();
  }

  public async fetchPositions(): Promise<PositionRow[]> {
    return marketDataService.fetchPositions();
  }

  public async fetchHistory(limit: number): Promise<Record<string, unknown>[]> {
    return marketDataService.fetchHistory(limit);
  }

  public async fetchCandles(symbol: string, timeframe: string, count: number) {
    return marketDataService.fetchCandles(symbol, timeframe, count);
  }

  public async fetchSMC(symbol: string, timeframe: string, count = 300): Promise<any> {
    try {
      const r = await callBridge('GET', ['/smc'], {
        query: { symbol, timeframe, count },
        timeoutMs: 8000,
      });
      return (r.data as any)?.data ?? unwrapData(r.data) ?? null;
    } catch {
      return null;
    }
  }



  /** Re-read config from DB (used for hot-reload every 60 s). */
  public loadConfig(): AutoTradingConfig {
    return persistenceService.loadConfig();
  }

  /** Load candles for a symbol + timeframe. Wraps MarketDataService. */
  public async loadSymbolCandles(symbol: string, timeframe: string, count = 200): Promise<any[]> {
    return marketDataService.loadSymbolCandles(symbol, timeframe, count);
  }


  /**
   * Load candles for a cross-asset symbol that may not be in the broker watchlist.
   * Returns an empty array instead of throwing when the symbol is unavailable.
   */
  public async loadOptionalCandles(symbol: string, timeframe: string, count = 60): Promise<any[]> {
    try {
      return await marketDataService.loadSymbolCandles(symbol, timeframe, count);
    } catch {
      return [];
    }
  }


  /** Deep analysis for a single symbol — runs inferAnalysis on fresh candles and returns AnalysisSummary. */
  async runDeepAnalysis(symbol: string, timeframe: string): Promise<any> {
    const candles = await this.loadSymbolCandles(symbol, timeframe, 200);
    if (!candles.length) throw new Error(`No candles for ${symbol}/${timeframe}`);
    const analysis = inferAnalysis(candles);
    return { symbol, timeframe, analysis, at: new Date().toISOString() };
  }

}

export const autoTradingService = new AutoTradingService();

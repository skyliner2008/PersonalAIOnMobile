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
import { buildZoneLayerContext, hasAlignedFvgNearPrice } from './auto/core/ZoneAwareGate.js';
import { extractV25PathMilestone, v25MilestoneReached } from './auto/core/V25MilestoneProtection.js';
// V25.0 — Wall State Machine + config-driven bootstrap
import { wallStateMachine } from './auto/v25/state/WallStateMachine.js';
import { bootstrapV25ShadowFromConfig, shutdownV25Shadow } from './auto/v25/index.js';
import { tickBuffers } from './auto/v25/TickBuffer.js';

const staleCloseBackoff = new Map<number, number>();

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

function compactAnalysisForLog(analysis: any): Record<string, unknown> {
  if (!analysis) return {};
  const signals = Array.isArray(analysis.signals)
    ? analysis.signals.slice(0, 30).map((s: any) => ({
        name: s?.name,
        bias: s?.bias,
        score: s?.score,
        evidence: s?.evidence,
      }))
    : [];
  return {
    bias: analysis.bias,
    regime: analysis.regime,
    confluence: Number.isFinite(Number(analysis.confluence)) ? round1(Number(analysis.confluence)) : analysis.confluence,
    fitness: Number.isFinite(Number(analysis.fitness)) ? round1(Number(analysis.fitness)) : analysis.fitness,
    strategy: analysis.strategy,
    rationale: analysis.rationale,
    atr: analysis.atr ?? null,
    rsi: analysis.rsi ?? null,
    sma20: analysis.sma20 ?? null,
    sma50: analysis.sma50 ?? null,
    ema20: analysis.ema20 ?? null,
    signals,
  };
}

function compactMtfForLog(analyses: Record<string, any>): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(analyses).map(([tf, analysis]) => [tf, compactAnalysisForLog(analysis)])
  );
}

function compactDeterministicForLog(detDecision: any): Record<string, unknown> {
  return {
    action: detDecision?.action,
    confidence: detDecision?.confidence,
    management: detDecision?.management,
    sizeFraction: detDecision?.size_fraction,
    counterTrend: Boolean(detDecision?.counterTrend),
    zoneAtEntry: detDecision?.zoneAtEntry,
    stateHash: detDecision?.stateHash,
    timeframe: detDecision?.timeframe,
    sl: detDecision?.sl ?? null,
    tp: detDecision?.tp ?? null,
    rationale: detDecision?.rationale,
    rationaleTh: detDecision?.rationale_th,
  };
}

function classifyDecisionBlockCategory(riskGate: string | null | undefined, side: string, executed: boolean): string | null {
  if (executed) return null;
  const gate = String(riskGate || '').toUpperCase();
  if (side !== 'SKIP' && gate === 'ALLOWED') return null;
  if (gate.includes('NO_DETERMINISTIC_SIGNAL')) return 'NO_DETERMINISTIC_SIGNAL';
  if (gate.includes('EA_FALLBACK_CONFIDENCE_LOW')) return 'EA_FALLBACK_CONFIDENCE_LOW';
  if (gate.includes('EA_FALLBACK_RRR') || gate.includes('RRR')) return 'RRR_OR_REWARD_RISK';
  if (gate.includes('V25_ONLY') || gate.includes('V25')) return 'V25_ONLY_GATE';
  if (gate.includes('NEAR_DUPLICATE') || gate.includes('SEQUENTIAL')) return 'SEQUENTIAL_DUPLICATE';
  if (gate.includes('MTF ZONE') || gate.includes('ZONE-AWARE')) return 'ZONE_GATE';
  if (gate.includes('PROXIMITY')) return 'PROXIMITY_GATE';
  if (gate.includes('FVG')) return 'FVG_ALIGNMENT';
  if (gate.includes('RISK')) return 'RISK_GATE';
  if (gate.includes('ORDER ERROR') || gate.includes('ORDER REQUEST FAILED')) return 'ORDER_SEND_FAILED';
  return side === 'SKIP' ? 'SKIP_OTHER' : null;
}

function upperText(...parts: unknown[]): string {
  return parts
    .filter((part) => part !== null && part !== undefined)
    .map((part) => String(part))
    .join(' ')
    .toUpperCase();
}

function hasContinuationIntent(strategy: string | null | undefined, ...texts: unknown[]): boolean {
  const text = upperText(strategy, ...texts);
  return (
    text.includes('CONTINUATION') ||
    text.includes('BREAKOUT') ||
    text.includes('MOMENTUM') ||
    text.includes('SMC_FVG_SCALP') ||
    text.includes('MTF-ALIGNED') ||
    text.includes('BMS_') ||
    text.includes('SMS_') ||
    text.includes('BOS')
  );
}

function htfBiasAgrees(side: TradeSide, analyses: Record<string, any>): boolean {
  const wanted = side === 'BUY' ? 'BULL' : 'BEAR';
  const h4 = analyses['H4']?.bias;
  const h1 = analyses['H1']?.bias;
  return h4 === wanted && (h1 === wanted || h1 === undefined || h1 === 'NEUTRAL');
}

function isDeepWrongZone(side: TradeSide, pctFromRange: number, hardBlockPct: number): boolean {
  return side === 'SELL'
    ? pctFromRange <= hardBlockPct
    : pctFromRange >= 100 - hardBlockPct;
}

function describeZoneMissingEvidence(args: {
  side: TradeSide;
  pctFromRange: number;
  hardBlockPct: number;
  htfAligned: boolean;
  continuationIntent: boolean;
  smcSupported: boolean;
  fvgSupported: boolean;
  wallFadeSupported?: boolean;
  wallStars?: number;
  localExecutionAligned?: boolean;
  zonePath?: string;
  fvgTimeframes?: string[];
  confluence: number;
  minConfluence: number;
}): string {
  const missing: string[] = [];
  if (isDeepWrongZone(args.side, args.pctFromRange, args.hardBlockPct) && !args.localExecutionAligned) {
    missing.push('too deep in wrong zone');
  }
  if (!args.localExecutionAligned) missing.push('no execution-TF premium/discount relief');
  if (!args.continuationIntent) missing.push('not continuation/breakdown intent');
  if (!args.htfAligned) missing.push('HTF bias not aligned');
  if (!args.smcSupported) missing.push('no OB/FVG support');
  if (!args.fvgSupported) missing.push('no fresh aligned FVG');
  if (!args.wallFadeSupported) missing.push(`no strong wall fade evidence${args.wallStars != null ? ` (${args.wallStars}★)` : ''}`);
  if (args.confluence < args.minConfluence) missing.push(`confluence ${args.confluence.toFixed(1)} < ${args.minConfluence}`);
  const evidence = args.fvgTimeframes?.length ? `; fvgTF=${args.fvgTimeframes.join('/')}` : '';
  const path = args.zonePath ? `; zones=${args.zonePath}` : '';
  return `${missing.join(', ') || 'continuation override conditions not met'}${evidence}${path}`;
}

function v25AlignedDirectEntry(args: {
  symbol: string;
  side: TradeSide;
  minStars: number;
  maxStateAgeMs: number;
  entryPrice?: number;
  atr?: number;
  allowApproach?: boolean;
  approachMinStars?: number;
  approachMaxAtrMul?: number;
  confluence?: number;
  approachMinConfluence?: number;
}): { allowed: boolean; reason: string } {
  const snap = wallStateMachine.status().find((s) => s.symbol === args.symbol);
  if (!snap) return { allowed: false, reason: 'no V25 wall state snapshot' };

  const sideState = args.side === 'BUY' ? snap.below : snap.above;
  const sideLabel = args.side === 'BUY' ? 'BELOW/support' : 'ABOVE/resistance';
  const state = String(sideState?.state ?? 'UNKNOWN');
  const entryStates = new Set(['REACT', 'CONFIRM', 'RETEST']);
  const approachCandidate = args.allowApproach === true && state === 'APPROACH';
  if (!entryStates.has(state) && !approachCandidate) {
    return { allowed: false, reason: `V25 ${sideLabel} state ${state} is not REACT/CONFIRM/RETEST/qualified APPROACH` };
  }

  const enteredAt = Number(sideState?.enteredAt ?? 0);
  const ageMs = Date.now() - enteredAt;
  if (!Number.isFinite(ageMs) || ageMs < 0 || ageMs > args.maxStateAgeMs) {
    return { allowed: false, reason: `V25 ${sideLabel} state stale age=${Math.max(0, Math.round(ageMs))}ms` };
  }

  const wall = sideState?.wall;
  const stars = Number(wall?.stars ?? 0);
  if (!wall || stars < args.minStars) {
    return { allowed: false, reason: `V25 ${sideLabel} wall stars ${stars} < ${args.minStars}` };
  }

  const wallPrice = Number(wall.price);
  if (approachCandidate) {
    const minApproachStars = args.approachMinStars ?? Math.max(args.minStars, 4);
    if (stars < minApproachStars) {
      return { allowed: false, reason: `V25 ${sideLabel} APPROACH wall stars ${stars} < ${minApproachStars}` };
    }
    const entryPrice = Number(args.entryPrice);
    if (!Number.isFinite(entryPrice) || !Number.isFinite(wallPrice)) {
      return { allowed: false, reason: `V25 ${sideLabel} APPROACH missing entry/wall price` };
    }
    const correctSide = args.side === 'BUY' ? entryPrice >= wallPrice : entryPrice <= wallPrice;
    if (!correctSide) {
      return { allowed: false, reason: `V25 ${sideLabel} APPROACH price is on wrong side of wall` };
    }
    const atr = Number(args.atr);
    const budget = Number.isFinite(atr) && atr > 0 ? atr : Math.max(Math.abs(entryPrice) * 0.004, 50);
    const maxDist = budget * (args.approachMaxAtrMul ?? 0.18);
    const dist = Math.abs(entryPrice - wallPrice);
    if (dist > maxDist) {
      return { allowed: false, reason: `V25 ${sideLabel} APPROACH distance ${dist.toFixed(2)} > ${maxDist.toFixed(2)}` };
    }
    const confluence = Number(args.confluence ?? 0);
    const minConfluence = args.approachMinConfluence ?? 62;
    if (confluence < minConfluence) {
      return { allowed: false, reason: `V25 ${sideLabel} APPROACH confluence ${confluence.toFixed(1)} < ${minConfluence}` };
    }
  }

  const wallText = Number.isFinite(wallPrice) ? wallPrice.toFixed(2) : String(wall.price ?? '?');
  const tfText = Array.isArray(wall.tfs) && wall.tfs.length ? `, TFs=${wall.tfs.join('+')}` : '';
  return {
    allowed: true,
    reason: `V25 ${sideLabel} ${state} wall=${wallText} ${stars}-star age=${Math.round(ageMs)}ms${tfText}`,
  };
}

export class AutoTradingService {
  private config = persistenceService.loadConfig();
  private state = persistenceService.loadRuntimeState();
  private lastDecisions = persistenceService.loadLastDecisions();
  private openJournal = persistenceService.loadOpenJournal();
  private learnSummary = persistenceService.loadLearnSummary();

  private analyst: AnalystAgent | null = null;
  private riskOfficer: RiskOfficerAgent | null = null;
  private executionTrader: ExecutionAgent | null = null;
  private postMortem: PostMortemAgent | null = null;

  private cycleTimer: NodeJS.Timeout | null = null;
  private manageTimer: NodeJS.Timeout | null = null;
  private learnTimer: NodeJS.Timeout | null = null;
  // 2026-05-01 Phase 2.5 — daily refresh ของ OpenRouter free pool
  private modelSyncTimer: NodeJS.Timeout | null = null;
  private cycleBusy = false;
  private manageBusy = false;
  // P2.3 cooldown tracker  prevents close-weakest from churning the book
  private closeWeakestLastAt: Map<string, number> = new Map();
  private lastCycleTime = 0;
  private v25MilestoneProtectedTickets = new Set<number>();
  private closedDealDeferredLastLogAt = new Map<string, number>();
  private postMortemReviewedTickets = new Set<string>();
  private symbolTimeframes: Map<string, string> = new Map(); // Active TF per symbol
  private learnBusy = false;
  private lastMarketAwareManageAt = 0;
  private aiCooldownUntil = 0;
  private lastConfigReloadAt = 0;
  private clusterMaxProfit: Map<string, number> = new Map();
  private aiFallbackCircuitLastLogAt: Map<string, number> = new Map();
  // 2026-05-15 V26.2 — MTF Analysis Cache for Event-Driven updates
  private mtfCache: Map<string, Map<string, { candles: any[]; analysis: any }>> = new Map();
  private lastTfUpdate: Map<string, Map<string, number>> = new Map();


  constructor() {
    // 2026-04-30: Clean up any legacy seeded model stats before starting
    modelRankerService.cleanupLegacyStats();

    // 2026-05-02 Phase 3.1 — Boot-time OpenRouter sync (เดิมรันแค่ใน runLearn())
    //   ทำให้ทุก boot (ทั้ง resume และ fresh) ได้ pool ทันสมัยตั้งแต่ cycle แรก
    //   + ตั้ง daily timer ไว้เลยเพื่อให้ refresh ทุก 24 ชม.
    this.bootstrapOpenRouterSync().catch(err => atWarn(`[AutoEngine] Boot sync failed: ${err}`));

    // 2026-05-06 V21.0 — Initialize Event-Driven Architecture modules
    agentCoordinator.initialize();
    atLog('[AutoEngine] V21.0 Event-Driven Architecture initialized (EventBus + IndicatorPipeline + AgentCoordinator)');

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
        this.scheduleManageLoop();
        this.scheduleLearnLoop();
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
    eventBus.resume();
    this.runCycle().catch(err => atError('[AutoEngine] error in first cycle:', err)); 
    this.scheduleCycleLoop();
    this.scheduleManageLoop();
    this.scheduleLearnLoop();
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
      // Granularly merge the v25 sub-object
      if (nextAdaptive.v25 !== undefined) {
        mergedAdaptive.v25 = { ...(baseAdaptive.v25 || {}), ...nextAdaptive.v25 };
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
      this.scheduleManageLoop();
      this.scheduleLearnLoop();
    }
    // V25: Sync shadow pipeline with updated config/watchlist.
    const v25cfg = (merged as any).adaptive?.v25 || {};
    if (v25cfg.enabled) {
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

  private persistConfig(): void {
    persistenceService.persistConfig(this.config);
  }

  private persistRuntime(): void {
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

  private appendDecisionFeed(cycleNo: number, timeframe: string, decisions: CycleDecision[]): void {
    persistenceService.appendDecisionFeed(cycleNo, timeframe, decisions);
  }

  private scheduleManageLoop(): void {
    if (this.manageTimer) clearTimeout(this.manageTimer);
    if (!this.state.running) return;
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
    if (!this.state.running) return;
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
  private scheduleDailyModelSync(): void {
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
    if (this.cycleBusy) return;
    this.cycleBusy = true;
    
    // Generate a unique trace ID for this market cycle
    const traceId = `cycle-${this.state.cycleCount + 1}-${Date.now().toString().slice(-4)}`;
    
    await traceStorage.run({ traceId }, async () => {
      const timer = cycleDurationSeconds.startTimer();
      try {
      // eslint-disable-next-line no-console
      atLog('[AutoEngine]  Running Market Cycle...');

      // Market Guard: Check if markets are open
      const marketOpen = await this.isMarketOpen();
      if (!marketOpen) {
          if (this.state.phase !== 'SLEEPING') {
              // eslint-disable-next-line no-console
              atLog('[AutoEngine]  All watchlist symbols are CLOSED. Entering Sleep Mode & Brain Consolidation...');
              this.state = { ...this.state, phase: 'SLEEPING', message: 'Market closed. AI is consolidating memory.' };
              this.persistRuntime();
              
              // Trigger Sleep Cycle
              memoryConsolidationService.runSleepCycle(this.config).catch(err => {
                  atError('[AutoEngine] Sleep Cycle error:', err);
              });

              broadcast({
                  type: 'intelligence_alert',
                  title: ' AI SLEEP MODE',
                  message: 'Market is closed. JARVIS is consolidating memory and pruning subconscious patterns.',
                  priority: 'MEDIUM',
                  at: Date.now()
              });
          }
          return;
      }
      
      // --- Post-Mortem Detection [Phase 2] ---
      // Detect trades that have closed since the last cycle and trigger AI review.
      const positions = await this.fetchPositions();
      await this.detectAndReviewClosedTrades(positions);

      // --- Circuit Breaker Check [Phase 3] ---
      const account = await this.fetchAccount();
      const breaker = CircuitBreaker.check(account, this.config);
      if (breaker.tripped) {
          // eslint-disable-next-line no-console
          atError(`[AutoEngine]  CIRCUIT BREAKER TRIPPED: ${breaker.reason}`);
          broadcast({
              type: 'intelligence_alert',
              title: 'CIRCUIT BREAKER',
              message: `Auto-trading PAUSED: ${breaker.reason}`,
              priority: 'CRITICAL',
              at: Date.now()
          });
          if (breaker.action === 'PAUSE' || breaker.action === 'STOP') {
              this.config.enableLiveTrading = false; // Emergency disable
              this.state.status = 'PAUSED';
              this.state.message = `Circuit Breaker: ${breaker.reason}`;
              this.persistConfig();
              this.persistRuntime();
              return;
          }
      }

      // --- News Intelligence Phase [Phase 1] ---
      // Derive the set of quote currencies from the active watchlist and
      // refresh news risk for each one. `updateNewsRisk` mutates the SHARED
      // newsRisk.* fields, so we intentionally call it sequentially and keep
      // the NEAREST (min minutesToEvent) HIGH-impact hit across currencies.
      if (this.config.newsRisk?.enabled) {
          try {
              const newsWatchlist = (((this.config as any).watchlist ?? (this.config as any).symbols ?? defaultConfig.watchlist) as unknown[])
                .map((item) => String(item).trim().toUpperCase())
                .filter(Boolean);
              const currencies = NewsAnalyzer.currenciesForSymbols(newsWatchlist);
              if (currencies.length === 0) {
                  // Fallback to USD if watchlist didn't produce any mapped currency
                  await newsAnalyzer.updateNewsRisk('USD', this.config);
              } else {
                  let best: { event?: string; impact?: 'LOW' | 'MEDIUM' | 'HIGH'; minutes?: number } = {};
                  for (const ccy of currencies) {
                      await newsAnalyzer.updateNewsRisk(ccy, this.config);
                      const riskNow = this.config.newsRisk;
                      if (!riskNow) break;
                      if (riskNow.impactLevel === 'HIGH' && typeof riskNow.minutesToEvent === 'number') {
                          if (best.minutes === undefined || Math.abs(riskNow.minutesToEvent) < Math.abs(best.minutes)) {
                              best = {
                                  event: riskNow.activeEvent,
                                  impact: riskNow.impactLevel,
                                  minutes: riskNow.minutesToEvent,
                              };
                          }
                      }
                  }
                  if (best.impact && this.config.newsRisk) {
                      this.config.newsRisk.activeEvent = best.event;
                      this.config.newsRisk.impactLevel = best.impact;
                      this.config.newsRisk.minutesToEvent = best.minutes;
                  }
              }
          } catch (err) {
              atError('   News update failed:', err);
          }
      }

      // Refresh config from DB each cycle so that API-key rotation,
      // watchlist edits, and risk-param changes take effect without
      // needing to restart the server. `updateConfig()` persists, so
      // reloading here never overwrites unsaved in-memory mutations.
      // 2026-04-25 (LOW fix): only re-read every CONFIG_RELOAD_INTERVAL_MS
      // unless the in-process updateConfig() explicitly bumped a dirty flag.
      // SQLite reads are cheap but JSON parse + normalize for a 5-minute cycle
      // is wasteful when the row hasn't changed.
      try {
        const CONFIG_RELOAD_INTERVAL_MS = 60_000;
        if (Date.now() - this.lastConfigReloadAt > CONFIG_RELOAD_INTERVAL_MS) {
          this.config = this.loadConfig();
          this.lastConfigReloadAt = Date.now();
          // V25: Ensure shadow pipeline is in sync with reloaded config
          const v25cfg = (this.config as any).adaptive?.v25 || {};
          if (v25cfg.enabled) {
            const v25Watchlist = (((this.config as any).watchlist ?? (this.config as any).symbols ?? defaultConfig.watchlist) as unknown[])
              .map((item) => String(item).trim().toUpperCase())
              .filter(Boolean);
            bootstrapV25ShadowFromConfig(v25cfg, v25Watchlist);
          }
        }
      } catch (reloadErr) {
        atWarn('[AutoEngine] config reload failed, using cached copy:', reloadErr);
      }
      const cfg = this.config;
      const cfgWatchlist = Array.from(new Set(
        (((cfg as any).watchlist ?? (cfg as any).symbols ?? defaultConfig.watchlist) as unknown[])
          .map((item) => String(item).trim().toUpperCase())
          .filter(Boolean)
      ));
      const cfgSymbolBlacklist = new Set(
        (((cfg as any).symbolBlacklist ?? []) as unknown[])
          .map((item) => String(item).trim().toUpperCase())
          .filter(Boolean)
      );
      // eslint-disable-next-line no-console
      // 2026-04-30 — Per-symbol position breakdown (e.g. "XAUUSD 2/5, XBTUSD 3/5").
      // The mobile-app `maxOpenPositions` is interpreted as the per-symbol cap;
      // a 2× hard ceiling applies to defensive scale-in / hedging.  The previous
      // "(total N/M)" suffix compared against the same cap and was misleading
      // (e.g. 7/5 looked over-limit when it was actually 2 symbols × cap 5).
      // Show running total without a fake ceiling instead.
      const perSymCap = (s: string) => cfg.risk.maxPositionsPerSymbol?.[s.toUpperCase()] ?? cfg.risk.defaultMaxPositionsPerSymbol ?? cfg.risk.maxOpenPositions;
      const symCounts = new Map<string, number>();
      for (const p of positions) symCounts.set(p.symbol.toUpperCase(), (symCounts.get(p.symbol.toUpperCase()) || 0) + 1);
      const perSymStr = cfgWatchlist.map((s) => {
        const cap = perSymCap(s);
        const held = symCounts.get(s.toUpperCase()) || 0;
        return `${s.toUpperCase()} ${held}/${cap}${held > cap ? ` (def<=${cap * 2})` : ''}`;
      }).join(', ');
      atLog(`   Account: Balance=$${account.balance} | Equity=$${account.equity} | Positions: ${perSymStr} | open=${positions.length}`);
      const openSymbolCount = new Map<string, number>();
      for (const p of positions) {
        const s = p.symbol.toUpperCase();
        openSymbolCount.set(s, (openSymbolCount.get(s) || 0) + 1);
      }
      const decisions: CycleDecision[] = [];
      const allCandles = new Map<string, any[]>();
      // --- V26.2 Event-Driven MTF Loader ---
      // Instead of fetching all candles for every symbol every 5 seconds (redundant),
      // we only refresh the primary timeframe candles when a bar closes.
      for (const symbol of cfgWatchlist) {
          const upper = symbol.toUpperCase();
          const activeTF = this.symbolTimeframes.get(upper) || cfg.timeframe || 'H1';
          const tfMs = this.getTfMs(activeTF);
          const now = Date.now();

          if (!this.mtfCache.has(upper)) this.mtfCache.set(upper, new Map());
          if (!this.lastTfUpdate.has(upper)) this.lastTfUpdate.set(upper, new Map());
          
          const symCache = this.mtfCache.get(upper)!;
          const symLastUpdate = this.lastTfUpdate.get(upper)!;
          const lastUpdate = symLastUpdate.get(activeTF) || 0;

          // Refresh if: 1. No cache 2. Wall-clock crossed a TF boundary
          const needsRefresh = !symCache.has(activeTF) || (Math.floor(now / tfMs) > Math.floor(lastUpdate / tfMs));

          if (needsRefresh) {
              try {
                  const tfCandles = await this.loadSymbolCandles(upper, activeTF, 180);
                  if (tfCandles && tfCandles.length >= 60) {
                      const summary = inferAnalysis(tfCandles);
                      symCache.set(activeTF, { candles: tfCandles, analysis: summary });
                      symLastUpdate.set(activeTF, now);
                      allCandles.set(upper, tfCandles);
                  }
              } catch (e) { /* skip */ }
          } else {
              // Use cached data for correlation and downstream logic
              const cached = symCache.get(activeTF);
              if (cached?.candles) {
                  allCandles.set(upper, cached.candles);
              }
          }
      }

      this.state = { ...this.state, phase: 'ANALYZING', lastTickAt: nowIso(), message: 'server analyzing' };
      this.persistRuntime();

      for (const symbol of cfgWatchlist) {
        const upper = symbol.toUpperCase();
        if (cfgSymbolBlacklist.has(upper)) {
          decisions.push(this.buildSkipDecision(upper, 'UNKNOWN', 'NEUTRAL', 0, 'HOLD_CASH', `SKIP ${upper} blacklisted`, 'symbol blacklisted'));
          continue;
        }
        // 2026-04-26 — per-symbol tradability gate. Skip closed markets so
        // crypto can still trade while FX/Metals are off-hours.
        const tradeStatus = await this.isSymbolTradable(upper);
        if (!tradeStatus.tradable) {
          atLog(`   ${upper}: market closed (${tradeStatus.reason}) — skipping`);
          decisions.push(this.buildSkipDecision(upper, 'UNKNOWN', 'NEUTRAL', 0, 'HOLD_CASH', `SKIP ${upper}: ${tradeStatus.reason}`, 'market_closed', `ตลาดปิดอยู่: ${tradeStatus.reason}`));
          continue;
        }
        // Determine active timeframe for this symbol (default to config, or what AI suggested last)
        let activeTF = this.symbolTimeframes.get(upper) || cfg.timeframe || 'H1';
        
        // --- Multi-Timeframe Smart Loading ---
        const analyses: Record<string, any> = {};
        let primaryAnalysis: any = null;
        let candles: any[] = [];
        let eaSignals: any[] = [];

        try {
            const tfsToLoad = [...new Set(['H4', 'H1', 'M30', 'M15', 'M5', activeTF])];
            const candlesByTf = new Map<string, any[]>();
            const now = Date.now();

            const symCache = this.mtfCache.get(upper)!;
            const symLastUpdate = this.lastTfUpdate.get(upper)!;

            for (const tf of tfsToLoad) {
                const tfMs = this.getTfMs(tf);
                const lastUpdate = symLastUpdate.get(tf) || 0;
                
                // Refresh if: 1. No cache 2. Wall-clock crossed a TF boundary
                const needsRefresh = !symCache.has(tf) || (Math.floor(now / tfMs) > Math.floor(lastUpdate / tfMs));
                
                let tfData = symCache.get(tf);

                if (needsRefresh) {
                    const tfCandles = await this.loadSymbolCandles(upper, tf, 200);
                    if (tfCandles && tfCandles.length >= 20) {
                        const summary = inferAnalysis(tfCandles);
                        
                        // V20.0 — Fetch SMC from bridge for structural TFs on refresh
                        if (tf === 'H4' || tf === 'H1' || tf === activeTF) {
                            summary.smc = await this.fetchSMC(upper, tf, 300);
                        }
                        
                        tfData = { candles: tfCandles, analysis: summary };
                        symCache.set(tf, tfData);
                        symLastUpdate.set(tf, now);
                    }
                }

                if (tfData) {
                    const { candles: tfCandles, analysis: summary } = tfData;
                    analyses[tf] = { ...summary, candles: tfCandles };
                    candlesByTf.set(tf, tfCandles);
                    
                    if (tf === activeTF) {
                        primaryAnalysis = analyses[tf];
                        candles = tfCandles;
                    }
                }
            }


            // V21.0 — Feed IndicatorPipeline (runs SMC V2 + Signal Detection via EventBus)
            if (candlesByTf.size >= 2) {
              try {
                eaSignals = await indicatorPipeline.processUpdate(upper, candlesByTf);
                if (eaSignals.length > 0) {
                  atLog(`   ${upper}: V21.0 EA detected ${eaSignals.length} signal(s): ${eaSignals.map(s => `${s.side} ${s.strategy} ${s.confluenceStars}★`).join(', ')}`);
                }
              } catch (pipeErr) {
                atWarn(`   ${upper}: IndicatorPipeline error (non-critical): ${pipeErr}`);
              }
            }
        } catch (err) {
            atError(`   ${upper}: Critical error in MTF loading:`, err);
            continue;
        }
        
        if (!primaryAnalysis) {
          atWarn(`   ${upper}: Primary analysis (${activeTF}) failed - skipping.`);
          continue;
        }

        this.state = { ...this.state, message: `Analyzing ${upper} (MTF)...` };
        this.persistRuntime();

        // Fix: positions is a separate variable, not a property of account
        const symbolPositions = positions.filter((it) => it.symbol === upper);
        const cluster = this.buildPositionCluster(upper, symbolPositions);

        // eslint-disable-next-line no-console
        atLog(`   ${upper}: MTF Analysis ready (${Object.keys(analyses).join(',')})`);

        const analysis = primaryAnalysis; // Use active TF as base for technical rules
        const last = candles.length > 0 ? candles[candles.length - 1] : null;

        // --- Fetch Spread for Guarding ---
        const symbolInfoRaw = await callBridge('GET', [`/symbol_info?symbol=${upper}`], { timeoutMs: 5000 });
        const symbolInfoData = symbolInfoRaw.data as any;
        const symbolInfo = symbolInfoData?.success ? symbolInfoData.data : null;
        const currentSpread = symbolInfo?.spread || 0;
        if (currentSpread > 0) spreadBaseline.observe(upper, currentSpread);
        const point = symbolInfo?.point || 0.01;
        const spreadInPrice = currentSpread * point;
        
        if (!last) {
            atWarn(`   ${upper}: Missing primary candle - skipping symbol.`);
            continue;
        }

        const playbookScores = tradeManagementService.computePlaybookScores(cfg, account, cluster, analysis, this.openJournal, this.learnSummary);
        // Phase 1 fix: maxCorrelation is optional (default 1 = disabled). Previous
        // code read it unguarded and produced `undefined < 1  false` silently,
        // making the dynamic correlation guard always off regardless of config.
        const maxCorr = cfg.risk.maxCorrelation ?? 0.8;
        const corrAnalysis = analyzeCorrelation(upper, candles, positions, allCandles);

        const totalOpen = positions.length;
        // 2026-04-30 — Cap is per-symbol now (no global aggregate).  The mobile-
        // app `maxOpenPositions` setting controls the per-symbol budget.
        // Defense (hedge / scale-in to recover) gets a 2× allowance per symbol.
        const perSymbolCap =
          cfg.risk.maxPositionsPerSymbol?.[upper]
          ?? cfg.risk.defaultMaxPositionsPerSymbol
          ?? cfg.risk.maxOpenPositions
          ?? 5;
        const defenseSymbolCap = perSymbolCap * 2;
        // atHardCap is true when this symbol is at its non-defensive cap.
        // The hedge/defense flow can still operate up to defenseSymbolCap.
        const atHardCap = symbolPositions.length >= perSymbolCap;
        const atDefenseCap = symbolPositions.length >= defenseSymbolCap;

        let historyContext = this.buildHistoryContext(cluster, playbookScores);
        if (atHardCap) {
          historyContext = `[CRITICAL MANAGEMENT DIRECTIVE: ${upper} AT PER-SYMBOL CAP (${symbolPositions.length}/${perSymbolCap})] ` +
            `WE CANNOT OPEN A NEW DIRECTIONAL TRADE FOR ${upper}. ` +
            `FOCUS ON MANAGING THE CURRENT ${symbolPositions.length} POSITIONS TO HARVEST PROFIT OR REDUCE LOSS. ` +
            `Your suggested Action should be 'SKIP' unless you are HEDGING (defense allowance up to ${defenseSymbolCap}). ` +
            historyContext;
        }

        let aiDecision: any = null;
        const aiAvailable = !!cfg.apiKey && Date.now() > this.aiCooldownUntil;
        const gateTrace: DecisionTraceStep[] = [];
        pushDecisionTrace(gateTrace, 'analysis', 'INFO', 'MTF analysis ready', {
          activeTF,
          loadedTimeframes: Object.keys(analyses),
          price: last.c,
          spread: currentSpread,
          spreadInPrice,
          openPositions: positions.length,
          symbolPositions: symbolPositions.length,
        });

        // --- DETERMINISTIC PRE-ANALYSIS (fast, free, always runs) ---
        // 2026-04-24: computes side/strategy/confluence from MTF indicators in <5ms.
        // Used to:
        //  1. Skip LLM when confident (confidence >= 70 and no defensive action needed)
        //  2. Provide a strong prior to the LLM (shortens prompt by ~70%)
        //  3. Cache LLM results by market-state hash to skip identical queries.
        const totalHeatR = this.openJournal
          .filter((j) => j.outcome === 'OPEN' && j.symbol === upper)
          .reduce((sum, j) => {
            const pos = positions.find((p) => p.ticket === j.mt5Ticket);
            if (!pos) return sum;
            const r = tradeManagementService.positionRiskR(pos, j);
            return sum + Math.max(0, -r);
          }, 0);
        const lastPos = symbolPositions.slice().sort((a, b) => b.ticket - a.ticket)[0];
        let isLastSafe = true;
        let lastPositionR = 0;
        if (lastPos) {
          const lastJournal = this.openJournal.find(j => j.mt5Ticket === lastPos.ticket);
          isLastSafe = tradeManagementService.isPositionSafe(lastPos, lastJournal);
          lastPositionR = tradeManagementService.positionRiskR(lastPos, lastJournal);
        }

        let detDecision = deterministicDecide({
          symbol: upper,
          lastPrice: last.c,
          primary: primaryAnalysis,
          analyses,
          cluster,
          positions,
          totalHeatR,
          floatingProfit: cluster.totalProfit,
          h4Candles: allCandles.get(upper) || [],
          enableZoneAwareGate: cfg.adaptive?.enableZoneAwareGate ?? true,
          isLastSafe,
          lastPositionR,
          recoveryThresholdR: -(cfg.adaptive?.recoveryTriggerPct ?? 0.2),
        });

        // V23.1 — Integrate V21.0 EA Signals into Execution Pipeline
        if (eaSignals && eaSignals.length > 0) {
            const bestSignal = eaSignals.sort((a, b) => b.confluenceStars - a.confluenceStars)[0];
            // Only override if EA signal is strong (>= 2 stars) and detDecision isn't already doing defensive management
            const isDefensive = ['HEDGE', 'SCALE_IN', 'CLOSE', 'REDUCE'].includes(detDecision.management);
            if (bestSignal.confluenceStars >= 2 && !isDefensive) {
                detDecision.action = bestSignal.side as 'BUY' | 'SELL';
                detDecision.confidence = bestSignal.confidence;
                detDecision.sl = bestSignal.sl;
                detDecision.tp = bestSignal.tp;
                detDecision.rationale = `V21.0 EA Signal: ${bestSignal.strategy} ${bestSignal.confluenceStars}★`;
                detDecision.rationale_th = `สัญญาณ V21.0 EA: ${bestSignal.strategy} ${bestSignal.confluenceStars}★`;
                primaryAnalysis.strategy = bestSignal.strategy; // Update for downstream logic
                atLog(`   ${upper}: 🤖 V21.0 EA Signal applied to pipeline → ${detDecision.action} conf=${detDecision.confidence}%`);
            }
        }

        atLog(`   ${upper}: Deterministic  ${detDecision.action} (${detDecision.confidence}%) ${detDecision.counterTrend ? '[CT]' : ''} mgmt=${detDecision.management} zone=${detDecision.zoneAtEntry} stateHash=${detDecision.stateHash.substring(0, 40)}`);
        pushDecisionTrace(gateTrace, 'deterministic', detDecision.action === 'SKIP' ? 'SKIP' : 'INFO', detDecision.rationale, {
          action: detDecision.action,
          confidence: detDecision.confidence,
          management: detDecision.management,
          zoneAtEntry: detDecision.zoneAtEntry,
          counterTrend: Boolean(detDecision.counterTrend),
        });
        
        // P4.1: Track zone distribution (only for actionable signals)
        if (detDecision.action !== 'SKIP') {
            entryZoneDistribution.inc({ symbol: upper, zone: detDecision.zoneAtEntry });
        }

        // Decide whether to call the LLM.
        //
        // 2026-04-30 — Token-saving short-circuits, in priority order:
        //  (a) PRE-AI POSITION CAP GATE: when account is over the position
        //      cap AND deterministic management isn't a defensive action,
        //      the trade can never be opened — burning ~14k tokens on the
        //      AI pipeline only to be SKIP'd at the risk gate is pure waste.
        //  (b) PRE-AI ZONE GATE: if deterministic side would be blocked by
        //      the post-AI Zone-Aware Gate (BUY in PREMIUM / SELL in
        //      DISCOUNT, ex-breakouts), promote deterministic SKIP straight
        //      away.  Zone gate ran *after* Execution Trader before — fix.
        //  (c) STRICT LLM CACHE: identical market state hash within 3min.
        //  (d) LOOSE LLM CACHE: HTF-only key — same H4/H1 picture, same side
        //      and strategy, even if M15/M5 churn or pos count changes.
        //  (e) DETERMINISTIC FAST PATH: confidence >= threshold and action
        //      is actionable.  Threshold relaxes when we're at the cap with
        //      no defensive intent (signal can only be SKIP, no need for AI).
        const isDefensiveMgmt =
          detDecision.management === 'HEDGE' ||
          detDecision.management === 'SCALE_IN' ||
          detDecision.management === 'CLOSE' ||
          detDecision.management === 'REDUCE';

        // (a) Pre-AI position cap gate — this symbol is at its per-symbol cap
        // and we don't need a defensive action.  No new entry can be opened,
        // so skip the LLM round-trip entirely.  When intent IS defensive, we
        // let the AI run because the hedge / scale-in path uses the 2× cap.
        if (atHardCap && !isDefensiveMgmt && detDecision.action !== 'SKIP') {
          aiDecision = {
            action: 'SKIP',
            management: detDecision.management,
            size_fraction: 0,
            confidence: 0,
            rationale: `[DET] ${upper} per-symbol cap full (${symbolPositions.length}/${perSymbolCap}) and no defensive intent — token-saving SKIP`,
            rationale_th: `[ ] ${upper} เต็ม cap ${symbolPositions.length}/${perSymbolCap} และไม่ต้องป้องกัน — ข้าม LLM`,
            timeframe: detDecision.timeframe,
            strategy: primaryAnalysis.strategy,
            _source: 'pre_ai_cap_gate',
          };
          atLog(`   ${upper}: Pre-AI cap gate → SKIP LLM (per-symbol ${symbolPositions.length}/${perSymbolCap}, mgmt=${detDecision.management})`);
        }
        // Hard absolute ceiling — even defensive intent must respect 2× cap.
        if (!aiDecision && atDefenseCap && detDecision.action !== 'SKIP') {
          aiDecision = {
            action: 'SKIP',
            management: detDecision.management,
            size_fraction: 0,
            confidence: 0,
            rationale: `[DET] ${upper} hit defense ceiling (${symbolPositions.length}/${defenseSymbolCap} = 2× per-symbol)`,
            rationale_th: `[ ] ${upper} ถึงเพดานป้องกัน ${symbolPositions.length}/${defenseSymbolCap}`,
            timeframe: detDecision.timeframe,
            strategy: primaryAnalysis.strategy,
            _source: 'pre_ai_defense_cap',
          };
          atLog(`   ${upper}: Pre-AI defense-cap gate → SKIP LLM (${symbolPositions.length}/${defenseSymbolCap})`);
        }

        // (b) Pre-AI zone gate [V19.5 Dynamic MTF] — deterministic side would lose to Zone-Aware
        // Gate after a full LLM round-trip. We now check H4, H1, and activeTF layers.
        if (
          !aiDecision &&
          (detDecision.action === 'BUY' || detDecision.action === 'SELL') &&
          (cfg.adaptive?.enableZoneAwareGate ?? true)
        ) {
          const symbolCandles = allCandles.get(upper);
          if (symbolCandles && symbolCandles.length >= 20) {
            // 1. Check H4 (Major Structure)
            const zcH4 = (analyses['H4']?.candles) 
                ? classifyPremiumDiscount(analyses['H4'].candles, last.c) 
                : classifyPremiumDiscount(symbolCandles, last.c);
            
            // 2. Check H1 (Daily Context)
            const h1Candles = analyses['H1']?.candles || [];
            const zcH1 = h1Candles.length >= 20 ? classifyPremiumDiscount(h1Candles, last.c) : null;
            
            // 3. Check Active TF (Execution Zone) - only if different from H1/H4
            const zcActive = (activeTF !== 'H1' && activeTF !== 'H4' && analyses[activeTF]?.candles) 
                ? classifyPremiumDiscount(analyses[activeTF].candles, last.c) 
                : null;

            const isBuy = detDecision.action === 'BUY';
            const h4Wrong = isBuy ? zcH4.zone === 'PREMIUM' : zcH4.zone === 'DISCOUNT';
            const h1Wrong = zcH1 ? (isBuy ? zcH1.zone === 'PREMIUM' : zcH1.zone === 'DISCOUNT') : h4Wrong;
            const activeWrong = zcActive ? (isBuy ? zcActive.zone === 'PREMIUM' : zcActive.zone === 'DISCOUNT') : h1Wrong;

            // SHORT-CIRCUIT RULE: Only skip if ALL layers agree it's the wrong zone.
            const allWrong = h4Wrong && h1Wrong && activeWrong;
            
            const allowBreakout = (cfg.adaptive?.allowBreakoutThroughZone ?? true);
            const breakoutPass =
              allowBreakout &&
              ((isBuy  && zcH4.pctFromRange > 100) ||
               (!isBuy && zcH4.pctFromRange < 0));

            // V26.0 — Trend Continuation Override for Pre-AI Zone Gate
            // The Post-AI gate (Zone-Aware Gate at ~L2100) already has continuation/
            // SMC/FVG overrides, but they NEVER fire because this Pre-AI gate blocks
            // the signal first.  Port the essential overrides here so that strong
            // trend-following signals can pass through to the execution pipeline.
            //
            // Conditions (ALL must be true):
            //   1. HTF bias (H4+H1) agrees with the trade direction
            //   2. Regime is TRENDING_DOWN or TRENDING_UP or VOLATILE_BREAKOUT
            //   3. Strategy has continuation/trend intent (TREND_FOLLOW, BREAKOUT, etc.)
            //   4. Not counter-trend
            //   5. Price is NOT in stale deep-zone (see breakdown detection below)
            //   6. Confluence ≥ threshold (55 for breakout, 68 for trend)
            //   OR the trade is supported by a nearby FVG aligned with side
            let trendContinuationPass = false;
            if (allWrong && !breakoutPass) {
              const preAiHtfAligned = htfBiasAgrees(
                detDecision.action as TradeSide,
                analyses
              );
              const trendingRegime =
                primaryAnalysis.regime === 'TRENDING_DOWN' ||
                primaryAnalysis.regime === 'TRENDING_UP' ||
                primaryAnalysis.regime === 'VOLATILE_BREAKOUT';
              const continuationStrategy = hasContinuationIntent(
                primaryAnalysis.strategy,
                detDecision.rationale
              );
              const hardBlockPct = cfg.adaptive?.zoneHardBlockPct ?? 25;

              // V26.1 — Breakdown Detection: when price breaks below the H4
              // swing low (pctFromRange < 0 for SELL) or above H4 swing high
              // (pctFromRange > 100 for BUY), the old structure is STALE and
              // isDeepWrongZone gives a false positive.  In a genuine breakdown
              // with HTF bias aligned, the deep-zone check should be bypassed.
              const isSide = detDecision.action;
              const isBreakdown =
                (isSide === 'SELL' && zcH4.pctFromRange < 0) ||
                (isSide === 'BUY'  && zcH4.pctFromRange > 100);
              const notDeepWrong = isBreakdown || !isDeepWrongZone(
                detDecision.action as TradeSide,
                zcH4.pctFromRange,
                hardBlockPct
              );

              // V26.1 — Use a relaxed confluence threshold for breakout regimes
              // VOLATILE_BREAKOUT signals naturally have lower confluence (~55-60)
              // because opposing signals create noise; the trend alignment matters
              // more than raw confluence in a breakdown.
              const isBreakoutRegime =
                primaryAnalysis.regime === 'VOLATILE_BREAKOUT';
              const zoneContinuationMin = isBreakoutRegime
                ? (cfg.adaptive?.zoneBreakoutConfluenceMin ?? 55)
                : (cfg.adaptive?.zoneContinuationConfluenceMin ?? 68);
              const confluenceOk =
                primaryAnalysis.confluence >= zoneContinuationMin;

              // FVG support check — widened to include price NEAR a FVG (within
              // 1× ATR), not just inside it. During a breakdown the price often
              // trades just below a bear FVG that acted as the impulse origin.
              const h4CandlesForFvg = analyses['H4']?.candles || symbolCandles;
              const preAiFvgs = getActiveFVGs(h4CandlesForFvg);
              const atrProximity = (primaryAnalysis.atr || 20) * 1.0;
              const fvgAligned = preAiFvgs.some(
                (f: any) =>
                  (detDecision.action === 'SELL' &&
                    f.type === 'BEAR' &&
                    last.c <= f.top * 1.002 + atrProximity &&
                    last.c >= f.bottom * 0.998 - atrProximity) ||
                  (detDecision.action === 'BUY' &&
                    f.type === 'BULL' &&
                    last.c <= f.top * 1.002 + atrProximity &&
                    last.c >= f.bottom * 0.998 - atrProximity)
              );

              trendContinuationPass =
                preAiHtfAligned &&
                trendingRegime &&
                continuationStrategy &&
                !detDecision.counterTrend &&
                (
                  // Path 1: Normal continuation — not deep + confluence OK
                  (notDeepWrong && confluenceOk) ||
                  // Path 2: FVG-backed institutional override — nearby Bear/Bull
                  // FVG confirms institutional flow regardless of deep-zone.
                  // Requires HTF aligned (already checked) + FVG proximity.
                  fvgAligned ||
                  // Path 3: Breakdown with any confluence above min threshold
                  (isBreakdown && confluenceOk)
                );

              if (trendContinuationPass) {
                // Reduce lot size for safety when trading in wrong zone
                // Path 1 (normal continuation): 0.3x
                // Path 2 (FVG-only, possibly deep zone): 0.15x — max caution
                // Path 3 (breakdown + confluence): 0.3x
                const overridePath = (notDeepWrong && confluenceOk)
                  ? 'CONTINUATION'
                  : isBreakdown && confluenceOk
                    ? 'BREAKDOWN'
                    : 'FVG_INSTITUTIONAL';
                const safeFraction = overridePath === 'FVG_INSTITUTIONAL' ? 0.15 : 0.3;
                detDecision.size_fraction = Math.min(
                  detDecision.size_fraction ?? 0.5,
                  safeFraction
                );
                atLog(
                  `   ${upper}: Pre-AI Zone Gate OVERRIDE → ${overridePath} ` +
                    `(regime=${primaryAnalysis.regime}, htfAligned=${preAiHtfAligned}, ` +
                    `confluence=${primaryAnalysis.confluence.toFixed(1)}/${zoneContinuationMin}, ` +
                    `fvg=${fvgAligned}, breakdown=${isBreakdown}, ` +
                    `pct=${zcH4.pctFromRange.toFixed(0)}%, ` +
                    `size=${safeFraction}x)`
                );
                pushDecisionTrace(gateTrace, 'pre_ai_zone_override', 'INFO',
                  `Zone gate override: ${overridePath} (fvg=${fvgAligned}, conf=${primaryAnalysis.confluence.toFixed(1)}, pct=${zcH4.pctFromRange.toFixed(0)}%)`, {
                    overridePath,
                    fvgAligned,
                    confluenceOk,
                    isBreakdown,
                    notDeepWrong,
                    safeFraction,
                    pctFromRange: zcH4.pctFromRange,
                  });
              }
            }

            if (allWrong && !breakoutPass && !trendContinuationPass) {
              const zonePath = [`H4:${zcH4.zone}`, zcH1 ? `H1:${zcH1.zone}` : null, zcActive ? `${activeTF}:${zcActive.zone}` : null].filter(Boolean).join(' | ');
              aiDecision = {
                action: 'SKIP',
                management: detDecision.management,
                size_fraction: 0,
                confidence: 0,
                rationale: `[DET] MTF zone gate veto: ${detDecision.action} blocked (${zonePath})`,
                rationale_th: `[ ] MTF zone gate ตัด: ${detDecision.action} ติดโซนเสียเปรียบทุก TF (${zonePath})`,
                timeframe: detDecision.timeframe,
                strategy: primaryAnalysis.strategy,
                _source: 'pre_ai_zone_gate',
              };
              atLog(`   ${upper}: Pre-AI MTF zone gate → SKIP LLM (${detDecision.action} blocked: H4=${zcH4.pctFromRange.toFixed(0)}%, H1=${zcH1?.pctFromRange.toFixed(0) || 'NA'}%)`);
            } else if (h4Wrong && !trendContinuationPass) {
               const betterZone = !h1Wrong ? 'H1' : (zcActive && !activeWrong ? activeTF : 'LTF');
               atLog(`   ${upper}: H4 Zone (${zcH4.zone}) mismatch, but ${betterZone} alignment found. Proceeding to AI reasoning...`);
            }
          }
        }

        // (c) Strict cache (full state)
        if (!aiDecision) {
          const strictHit = llmCache.get(detDecision.stateHash);
          if (strictHit) {
            aiDecision = { ...strictHit, _source: 'llm_cache_strict' };
            atLog(`   ${upper}: LLM cache HIT (strict) — token cost saved`);
          }
        }

        // (d) Loose cache (HTF-only)
        let looseKey: string | null = null;
        if (!aiDecision) {
          looseKey = marketStateHashLoose(
            upper,
            last.c,
            analyses,
            detDecision.action,
            primaryAnalysis.strategy || 'NA'
          );
          const looseHit = llmCache.get(looseKey);
          if (looseHit) {
            aiDecision = { ...looseHit, _source: 'llm_cache_loose' };
            atLog(`   ${upper}: LLM cache HIT (loose HTF) — token cost saved`);
          }
        }

        // (e) Deterministic fast-path — relaxed threshold when at cap (we
        // can't open anything anyway, so noisy AI second-opinions add no
        // value).  Otherwise keep the original 70% gate.
        if (!aiDecision) {
          const detThreshold = atHardCap && !isDefensiveMgmt ? 50 : 70;
          const skipLlm =
            !detDecision.needsLlmSecondOpinion &&
            detDecision.confidence >= detThreshold &&
            detDecision.action !== 'SKIP';
          if (skipLlm) {
            aiDecision = {
              action: detDecision.action,
              management: detDecision.management,
              size_fraction: detDecision.size_fraction,
              confidence: detDecision.confidence,
              rationale: `[DET] ${detDecision.rationale}`,
              rationale_th: `[] ${detDecision.rationale_th}`,
              timeframe: detDecision.timeframe,
              strategy: primaryAnalysis.strategy,
              _source: 'deterministic',
            };
            atLog(`   ${upper}: Deterministic confident (${detDecision.confidence}% >= ${detThreshold}%) → SKIP LLM call`);
          }
        }

        // V23.0 — EA-only mode: bypass entire AI pipeline when disabled
        const aiModeEnabled = cfg.enableAiMode !== false;
        if (!aiModeEnabled && !aiDecision && detDecision.action !== 'SKIP') {
          atLog(`   ${upper}: [EA-ONLY MODE] AI pipeline disabled — using deterministic engine directly`);
          // Force EA fallback by leaving aiDecision null; the EA FALLBACK block below handles it
          // Lower threshold to 35% so EA mode is more active than AI-fallback mode
        }

        if (aiModeEnabled && aiAvailable && !aiDecision) {
            try {
                // --- P3.3: Pre-Analysis Prior (Similar Failed Setups) ---
                // Query vector store for past failed trades in similar market conditions
                // to give the AI context about what NOT to do.
                let failedSetupContext = '';
                if (cfg.adaptive?.enablePreAnalysisPrior !== false) {
                    try {
                        const snapshotText = `Market: ${upper}, Price: ${last.c}, Regime: ${analysis.regime}, Bias: ${analysis.bias}`;
                        const queryEmbedding = await agentOrchestrator.getEmbeddingForConfig(cfg, snapshotText);
                        // P3.4: also pull lessons (richer metadata: failure_pattern, profitR)
                        const outcomeResults = vectorStore.search(queryEmbedding, 5, { symbol: upper, type: 'trade_outcome' });
                        const lessonResults = vectorStore.search(queryEmbedding, 5, { symbol: upper, type: 'trade_lesson' });
                        const merged = [...outcomeResults, ...lessonResults];
                        const failedTrades = merged
                            .filter(r => r.metadata?.outcome === 'LOSS' || (r.metadata?.profitR !== undefined && r.metadata.profitR < -0.5))
                            .slice(0, 3);
                        if (failedTrades.length > 0) {
                            // P3.3: confluence penalty when historic similar setups had bad avgR
                            const validRs = failedTrades
                                .map((t) => Number(t.metadata?.profitR ?? NaN))
                                .filter((r) => Number.isFinite(r));
                            const avgR = validRs.length > 0
                                ? validRs.reduce((a: number, b: number) => a + b, 0) / validRs.length
                                : 0;
                            // Aggregate failure_pattern tags (P3.4)
                            const patternTags = Array.from(new Set(
                                failedTrades.flatMap((t) => String((t.metadata as any)?.failure_pattern || '').split(',').map((s: string) => s.trim()).filter(Boolean))
                            )).slice(0, 6);
                            const patternsLine = patternTags.length > 0
                                ? `\n   Common failure patterns: ${patternTags.join(', ')}`
                                : '';
                            failedSetupContext = `\n SIMILAR FAILED SETUPS (learn from past mistakes):\n` +
                                failedTrades.map((t, i) => `  ${i + 1}. ${t.metadata?.text || 'unknown'} (R=${t.metadata?.profitR?.toFixed(2) ?? 'N/A'}, dist=${t.distance.toFixed(3)})`).join('\n')
                                + patternsLine;
                            if (avgR < -0.3) {
                                const before = analysis.confluence;
                                analysis.confluence = Math.max(0, analysis.confluence - 10);
                                atWarn(`   ${upper}: Confluence penalty 10 (avgR of ${failedTrades.length} similar failed setups = ${avgR.toFixed(2)}). ${before.toFixed(1)}  ${analysis.confluence.toFixed(1)}`);
                            } else {
                                atLog(`   ${upper}: Found ${failedTrades.length} similar failed setups from memory (avgR=${avgR.toFixed(2)})`);
                            }
                        }
                    } catch { /* vector query is best-effort */ }
                }

                // --- Cross-Asset Correlation [Phase 1] ---
                const correlations: any[] = [];
                if (upper === 'XAUUSD') {
                    try {
                        const dxyCandles = await this.loadOptionalCandles('DXY', 'H1', 60);
                        const yieldCandles = await this.loadOptionalCandles('US10Y', 'H1', 60);
                        const spxCandles = await this.loadOptionalCandles('SPX500', 'H1', 60);
                        
                        const dxyAnalysis = dxyCandles.length >= 60 ? inferAnalysis(dxyCandles) : undefined;
                        const yieldAnalysis = yieldCandles.length >= 60 ? inferAnalysis(yieldCandles) : undefined;
                        const spxAnalysis = spxCandles.length >= 60 ? inferAnalysis(spxCandles) : undefined;

                        const contexts = crossAssetAnalyzer.analyzeGoldBias(dxyAnalysis, yieldAnalysis, spxAnalysis);
                        correlations.push(...contexts);
                    } catch (err) {
                        atError('   Correlation fetch failed:', err);
                    }
                }

                // 2026-05-10 V25.1 — gate AI pipeline on V25 wall state. User's
                // Wall-Touch Scalp style requires entry at wall, so running 3×LLM
                // (Analyst+RiskOfficer+Execution ~150s) when price is mid-channel
                // wastes tokens AND produces junk SL/TP that get discarded.
                const skipMidChannelLLMGate = cfg.adaptive?.v25?.skipMidChannelLLM ?? true;
                if (skipMidChannelLLMGate) {
                  const v25Snap = wallStateMachine.status().find(s => s.symbol === upper);
                  const isV25EntryState = v25Snap && (
                    v25Snap.above.state === 'REACT' || v25Snap.below.state === 'REACT' ||
                    v25Snap.above.state === 'CONFIRM' || v25Snap.below.state === 'CONFIRM' ||
                    v25Snap.above.state === 'RETEST' || v25Snap.below.state === 'RETEST'
                  );
                  if (!isV25EntryState) {
                    atLog(`   ${upper}: [V25 Gate] AI pipeline skipped (no wall in REACT/CONFIRM/RETEST, mid-channel) — saves ~150s + LLM tokens`);
                    // 2026-05-10 V25.1 hotfix — `selectedStrategy` is declared later
                    // in this function (~line 1535) so we cannot reference it here
                    // without hitting TDZ. Use the per-symbol primary analysis
                    // strategy as a stable label; downstream code will recompute
                    // its own selectedStrategy from `analysis` regardless.
                    const fallbackStrategy = (primaryAnalysis?.strategy as any) || 'TREND_FOLLOW';
                    aiDecision = {
                      action: 'SKIP',
                      rationale: 'V25 Gate: mid-channel — no wall touch. Wait for price to reach a wall.',
                      rationale_th: 'V25: ราคาอยู่กลาง channel ไม่มี wall touch รอราคาเข้าใกล้ wall ก่อน',
                      strategy: fallbackStrategy,
                      _source: 'v25_gate',
                    };
                    // Skip to next symbol — bypass the entire AI pipeline below.
                    // Reuse aiDecision so downstream side resolution treats this as SKIP.
                  }
                }

                // eslint-disable-next-line no-console
                if (!aiDecision || (aiDecision as any)._source !== 'v25_gate') {
                  atLog(`   ${upper}: AI Agent is reasoning (${cfg.provider || 'AI'} Pipeline)...`);
                }

                if ((aiDecision as any)?._source !== 'v25_gate' && cfg.adaptive?.useMultiAgent) {
                  // --- Phase 2: Multi-Agent Pipeline with 240s Timeout ---
                  const AGENT_TIMEOUT_MS = 240000;
                  const runPipeline = async () => {
                    const startPipe = Date.now();
                    if (!this.analyst) this.analyst = new AnalystAgent(cfg.apiKey!);
                    if (!this.riskOfficer) this.riskOfficer = new RiskOfficerAgent(cfg.apiKey!);
                    if (!this.executionTrader) this.executionTrader = new ExecutionAgent(cfg.apiKey!);

                    const aStart = Date.now();
                    const aChoice = resolveAgentChoice(cfg, 'analyst');
                    atLog(`   ${upper}: Analyst Starting (Model: ${aChoice.model}${aChoice.useSmartFree ? ' [Smart]' : ''})...`);

                    // V20.0 — Pre-calculate deterministic anchors to help small models
                    let smcAnchors = null;
                    try {
                        const rawCandles: Record<string, any[]> = {};
                        const structures: Record<string, any> = {};
                        for (const [tf, a] of Object.entries(analyses)) {
                            if (a.candles && a.candles.length >= 20) {
                                rawCandles[tf] = a.candles;
                                structures[tf]  = getSmcStructure(a.candles);
                            }
                        }
                        const det = pickSlTpDeterministic({
                            symbol: upper,
                            side: detDecision.action as 'BUY'|'SELL',
                            entry: last.c,
                            atr: analysis.atr || 0,
                            analyses,
                            rawCandles,
                            targetRrr: cfg.strategy?.minRRR ?? 1.5
                        }, structures);
                        smcAnchors = { 
                            sl: det.sl, 
                            tp: det.tp, 
                            rationale: det.rationale, 
                            side: detDecision.action 
                        };
                    } catch (err) {
                        atWarn(`   ${upper}: Failed to calculate pre-analyst anchors: ${err}`);
                    }

                    const report = await this.analyst.analyze(cfg, upper, analyses, correlations, last.c, smcAnchors || undefined);
                    atLog(`   ${upper}: Analyst (${report.modelUsed || aChoice.model}) finished in ${((Date.now() - aStart)/1000).toFixed(1)}s`);

                    const rStart = Date.now();
                    const rChoice = resolveAgentChoice(cfg, 'riskOfficer');
                    atLog(`   ${upper}: Risk Officer Starting (Model: ${rChoice.model}${rChoice.useSmartFree ? ' [Smart]' : ''})...`);
                    const approval = await this.riskOfficer.review(report, account, positions, cfg, last.c);
                    atLog(`   ${upper}: Risk Officer (${approval.modelUsed || rChoice.model}) finished in ${((Date.now() - rStart)/1000).toFixed(1)}s`);
                    
                    if (approval.approved && approval.action !== 'SKIP') {
                      // The AI pipeline took ~30 seconds. Fetch a fresh price to prevent stale entries.
                      let freshPrice = last.c;
                      try {
                        const infoRes = await callBridge('GET', [`/symbol_info?symbol=${upper}`], { timeoutMs: 3000 });
                        const infoData = infoRes.data as any;
                        if (infoData?.success && infoData.data) {
                           // Approximate mid price or just use bid
                           freshPrice = infoData.data.bid || infoData.data.price || last.c;
                        }
                      } catch (err) {
                        atWarn(`[AutoEngine] Could not fetch fresh price for ${upper}, using 30s old price.`);
                      }

                      // ── HIGH-2 (2026-04-25): Stale-price guard ─────────────────
                      const marketPrice = freshPrice;
                      
                      // V20.1: Symbol-aware stale threshold
                      // Bitcoin moves 3pts in a blink; Gold is much slower.
                      let stalePriceMaxPts = cfg.adaptive?.staleAgentPriceMaxPoints ?? 3.0;
                      if (upper.includes('XBT') || upper.includes('BTC')) {
                        stalePriceMaxPts = Math.max(stalePriceMaxPts, marketPrice * 0.001); // 0.1% or default
                      } else if (upper.includes('XAU') || upper.includes('GOLD')) {
                        stalePriceMaxPts = Math.max(stalePriceMaxPts, 2.0);
                      }

                      const priceDeltaAbs = Math.abs(marketPrice - last.c);
                      if (priceDeltaAbs > stalePriceMaxPts) {
                        atWarn(`   ${upper}: AI pipeline stale — price moved ${priceDeltaAbs.toFixed(2)} pts (> ${stalePriceMaxPts.toFixed(2)}). Skip this cycle to re-analyze on fresh data.`);
                        return { action: 'SKIP', rationale: `stale_price_${priceDeltaAbs.toFixed(2)}pts` };
                      }
                      const eStart = Date.now();
                      const eChoice = resolveAgentChoice(cfg, 'executionTrader');
                      atLog(`   ${upper}: Execution Agent Starting (Model: ${eChoice.model}${eChoice.useSmartFree ? ' [Smart]' : ''})...`);
                      const plan = await this.executionTrader.planOrder(approval, marketPrice, cfg);
                      atLog(`   ${upper}: Execution Trader (${plan?.modelUsed || eChoice.model}) finished in ${((Date.now() - eStart)/1000).toFixed(1)}s (Total Pipeline: ${((Date.now() - startPipe)/1000).toFixed(1)}s)`);

                      if (plan) {
                        // Within the staleness threshold we can safely shift
                        // SL/TP by the delta. Beyond it we already returned SKIP above.
                        const priceDelta = marketPrice - last.c;
                        const shiftedSl = approval.sl !== null ? roundToTick(approval.sl + priceDelta, tickSize(upper)) : null;
                        const shiftedTp = approval.tp !== null ? roundToTick(approval.tp + priceDelta, tickSize(upper)) : null;

                        return {
                          action: plan.side,
                          management: approval.management,
                          size_fraction: approval.adjustedVolume ? (approval.adjustedVolume / (plan.volume || 1)) : 1,
                          target_ticket: approval.targetTicket,
                          timeframe: report.suggestedStrategy === 'SCALPING' ? 'M5' : 'M15',
                          strategy: report.suggestedStrategy,
                          rationale: report.narrative,
                          rationale_th: report.narrative_th,
                          confidence: report.confidence,
                          sl: shiftedSl ?? approval.sl,
                          tp: shiftedTp ?? approval.tp,
                          adjustment: approval.riskNote,
                          risk_note: approval.reason,
                          allowOverLimitDefense: !!approval.allowOverLimitDefense,
                          playbook: `Execution: ${plan.type} at ${plan.price || 'Market'} (${plan.fillStrategy})`,
                          modelUsed: approval.modelUsed || report.modelUsed || 'multi-agent'
                        };
                      }
                      return { action: 'SKIP', rationale: 'Execution plan failed' };
                    }
                    return { 
                      action: 'SKIP', 
                      rationale: approval.reason,
                      rationale_th: approval.reason_th ? `[] ${approval.reason_th}` : `[]  Risk Officer: ${approval.reason}`
                    };
                  };

                   const timeoutPromise = new Promise((_, reject) => 
                    setTimeout(() => reject(new Error('AI_PIPELINE_TIMEOUT')), AGENT_TIMEOUT_MS)
                  );

                  try {
                    aiDecision = await Promise.race([runPipeline(), timeoutPromise]);
                  } catch (raceErr: any) {
                    if (raceErr.message === 'AI_PIPELINE_TIMEOUT') {
                        atWarn(`   ${upper}: ${cfg.provider || 'AI'} Pipeline timed out (${AGENT_TIMEOUT_MS/1000}s). Skipping AI input for this cycle.`);
                        aiDecision = { action: 'SKIP', rationale: 'ai_timeout' };
                    } else {
                        throw raceErr;
                    }
                  }

                  // ── V21.0 EA FALLBACK (graceful AI failure) ─────────────────
                  // AI pipeline returned SKIP not from analysis, but from errors:
                  //   "Execution plan failed", "Internal agent error", "HTTP 429"
                  // Instead of wasting the entire cycle, fall back to EA deterministic + SMC V2.
                  const aiSkipRationale = String(aiDecision?.rationale || '');
                  const isAiErrorSkip = aiDecision?.action === 'SKIP' && (
                    aiSkipRationale.includes('failed') ||
                    aiSkipRationale.includes('429') ||
                    aiSkipRationale.includes('timeout') ||
                    aiSkipRationale.includes('error') ||
                    aiSkipRationale.includes('blacklisted')
                  );

                  if (
                    isAiErrorSkip &&
                    detDecision.action !== 'SKIP' &&
                    detDecision.confidence >= 45 &&
                    !(detDecision.counterTrend && analysis.regime === 'VOLATILE_BREAKOUT')
                  ) {
                    const smcSnap = indicatorPipeline.getSmcSnapshot(upper);
                    let eaSl: number | null = null;
                    let eaTp: number | null = null;

                    if (smcSnap) {
                      const tol = last.c * 0.001;
                      if (detDecision.action === 'BUY') {
                        const sup = smcSnap.bullOBs.filter(ob => !ob.invalidated && ob.bottom < last.c).sort((a, b) => b.bottom - a.bottom)[0];
                        if (sup) eaSl = sup.bottom - tol;
                        const res = smcSnap.bearOBs.filter(ob => ob.bottom > last.c).sort((a, b) => a.bottom - b.bottom)[0];
                        if (res) eaTp = res.bottom;
                      } else {
                        const res = smcSnap.bearOBs.filter(ob => !ob.invalidated && ob.top > last.c).sort((a, b) => a.top - b.top)[0];
                        if (res) eaSl = res.top + tol;
                        const sup = smcSnap.bullOBs.filter(ob => ob.top < last.c).sort((a, b) => b.top - a.top)[0];
                        if (sup) eaTp = sup.top;
                      }
                    }

                    const rd = Math.abs(last.c - (eaSl ?? last.c));
                    const rw = Math.abs((eaTp ?? last.c) - last.c);
                    const rrr = rd > 0 ? rw / rd : 0;
                    const minR = cfg.strategy?.minRRR ?? 1.2;

                    // V24.0 fix: float-safe RRR compare (was failing when 1.4999... < 1.5)
                    if (rrr + 1e-6 >= minR && rd > 0) {
                      atLog(`   ${upper}: ⚡ V21.0 EA FALLBACK: AI error("${aiSkipRationale.substring(0, 40)}") → EA ${detDecision.action} conf=${detDecision.confidence}% RRR=${rrr.toFixed(2)}`);
                      aiDecision = {
                        action: detDecision.action,
                        management: detDecision.management,
                        size_fraction: detDecision.size_fraction,
                        confidence: detDecision.confidence,
                        sl: eaSl, tp: eaTp,
                        rationale: `[EA FALLBACK] AI error → deterministic+SMC V2 | RRR=${rrr.toFixed(2)}`,
                        rationale_th: `[🤖 EA อัตโนมัติ] AI ล้มเหลว → ใช้ระบบ deterministic+SMC V2 | RRR=${rrr.toFixed(2)}`,
                        timeframe: detDecision.timeframe,
                        strategy: primaryAnalysis.strategy,
                        _source: 'ea_fallback_v21',
                      };
                    } else {
                      atLog(`   ${upper}: V21.0 EA Fallback skipped — RRR ${rrr.toFixed(2)} < ${minR}`);
                    }
                  }
                } else {
                  // --- Legacy Single-Agent reasoning  now with deterministic prior to shorten prompt + P3.3 failed setups ---
                  const enrichedHistory = failedSetupContext ? historyContext + failedSetupContext : historyContext;
                  const aiResult = await agentOrchestrator.reason(cfg, upper, analyses, enrichedHistory, correlations, {
                    priorAction: detDecision.action,
                    priorConfidence: detDecision.confidence,
                    priorManagement: detDecision.management,
                    priorRationale: detDecision.rationale,
                    priorStrategy: primaryAnalysis.strategy,
                  });
                  atLog(`   ${upper}: Single-Agent (${aiResult.modelUsed}) reasoning completed.`);
                  // 2026-04-25 (LOW fix): use shared robust parser instead of
                  // greedy-regex JSON.parse so code-fenced LLM replies don't
                  // silently degrade to SKIP without a log line.
                  aiDecision = parseLlmJson<any>(aiResult.text, { allowEmpty: true, defaultValue: { action: 'SKIP' } });
                  aiDecision.modelUsed = aiResult.modelUsed;
                  aiDecision._source = 'llm';
                }

                // 2026-04-30 — cache LLM-produced decisions on BOTH the strict
                // and loose keys.  Previously only the single-agent branch
                // wrote to cache and only on the strict key, so multi-agent
                // runs never seeded the cache and identical-HTF queries kept
                // hitting the LLM.  We skip caching SKIP decisions because
                // they're often gate-related and don't generalise across
                // states.  We also skip caching when _source already came
                // from a cache (avoid no-op refresh).
                if (
                  aiDecision &&
                  aiDecision.action &&
                  aiDecision.action !== 'SKIP' &&
                  aiDecision._source !== 'llm_cache_strict' &&
                  aiDecision._source !== 'llm_cache_loose' &&
                  aiDecision._source !== 'pre_ai_cap_gate' &&
                  aiDecision._source !== 'pre_ai_zone_gate' &&
                  aiDecision._source !== 'deterministic'
                ) {
                  llmCache.set(detDecision.stateHash, aiDecision);
                  if (looseKey) llmCache.set(looseKey, aiDecision);
                }

                // --- Capture AI Timeframe Suggestion ---
                if (aiDecision.timeframe) {
                    const suggestedTF = String(aiDecision.timeframe).toUpperCase();
                    const validTFs = ['M1','M5','M15','M30','H1','H4','D1'];
                    const tfRank = (t: string) => validTFs.indexOf(t);
                    if (validTFs.includes(suggestedTF) && suggestedTF !== activeTF) {
                        const mtfBlock = cfg.adaptive?.mtfDowngradeBlock !== false;
                        const confMin = cfg.adaptive?.mtfDowngradeConfluenceMin ?? 80;
                        const h4 = analyses['H4'];
                        const h1 = analyses['H1'];
                        const bothAligned = !!h4 && !!h1 && h4.bias !== 'NEUTRAL' && h4.bias === h1.bias && h4.confluence >= confMin && h1.confluence >= confMin;
                        const dominantThreshold = Math.max(confMin, 85);
                        const h1Dominant = !!h1 && h1.bias !== 'NEUTRAL' && h1.confluence >= dominantThreshold && (!h4 || h4.bias === 'NEUTRAL' || h4.bias === h1.bias);
                        const htfAligned = bothAligned || h1Dominant;
                        const isDowngrade = tfRank(suggestedTF) >= 0 && tfRank(suggestedTF) < tfRank('H1');
                        if (mtfBlock && htfAligned && isDowngrade) {
                            atLog(`[AutoEngine]  TF-switch BLOCKED for ${upper} due to HTF alignment.`);
                        } else {
                            atLog(`[AutoEngine]  AI suggests switching ${upper} to ${suggestedTF} for next cycle.`);
                            this.symbolTimeframes.set(upper, suggestedTF);
                        }
                    }
                }

                // Store embedding
                const snapshotText = `Market: ${upper}, Price: ${last.c}, Regime: ${analysis.regime}, Bias: ${analysis.bias}, Rationale: ${aiDecision.rationale || analysis.rationale}`;
                const embedding = await agentOrchestrator.getEmbeddingForConfig(cfg, snapshotText);
                vectorStore.add(embedding, {
                    id: `snap_${Date.now()}_${upper}`,
                    symbol: upper,
                    timeframe: cfg.timeframe,
                    type: 'market_snapshot',
                    timestamp: Date.now()
                });
            } catch (aiErr: any) {
                if (aiErr.message === 'GEMINI_PIPELINE_TIMEOUT') {
                    atWarn(`   ${upper}: Gemini Pipeline timed out (90s). Skipping AI input for this cycle.`);
                    aiDecision = { action: 'SKIP', rationale: 'gemini_timeout' };
                } else {
                    atError(`   ${upper}: AI reasoning failed, falling back to technical rules.`, aiErr);
                }

                // Detect Quota Exceeded (429) and trigger cooldown
                const isRateLimit = aiErr?.status === 429 || String(aiErr).includes('429') || String(aiErr).includes('Quota');
                if (isRateLimit) {
                    this.aiCooldownUntil = Date.now() + (60 * 60 * 1000); // 1 hour cooldown
                    atError(`[AutoEngine]  AI Quota Exceeded. Entering AI Cooldown for 1 hour. Falling back to technicals.`);
                    broadcast({
                        type: 'intelligence_alert',
                        title: 'AI QUOTA EXCEEDED',
                        message: 'JARVIS is resting due to API limits. Technical fallback active for 1 hour.',
                        priority: 'MEDIUM',
                        at: Date.now()
                    });
                }

            }
        }

        // ── V21.0 EA FALLBACK (also primary path in EA-only mode) ────────────
        // AI mode OFF: always enter this block when det has a valid action
        // AI mode ON:  enter only when AI unavailable (rate-limit/timeout/blacklist)
        const eaConfidenceThreshold = aiModeEnabled ? 45 : 35;
        let eaFallbackMissReason: string | null = null;
        if (
          !aiDecision &&
          detDecision.action !== 'SKIP' &&
          detDecision.confidence >= eaConfidenceThreshold &&
          !(detDecision.counterTrend && analysis.regime === 'VOLATILE_BREAKOUT')
        ) {
          // Use SMC V2 data for enhanced SL/TP if available
          const smcSnap = indicatorPipeline.getSmcSnapshot(upper);
          let eaSl: number | null = null;
          let eaTp: number | null = null;

          if (smcSnap) {
            // Find SMC-based SL/TP using structural zones
            const tolerance = last.c * 0.001;
            if (detDecision.action === 'BUY') {
              // SL = nearest bull OB bottom or swing low
              const support = smcSnap.bullOBs
                .filter(ob => !ob.invalidated && ob.bottom < last.c)
                .sort((a, b) => b.bottom - a.bottom)[0];
              if (support) eaSl = support.bottom - tolerance;
              // TP = nearest bear OB bottom or swing high
              const resist = smcSnap.bearOBs
                .filter(ob => ob.bottom > last.c)
                .sort((a, b) => a.bottom - b.bottom)[0];
              if (resist) eaTp = resist.bottom;
            } else {
              const resist = smcSnap.bearOBs
                .filter(ob => !ob.invalidated && ob.top > last.c)
                .sort((a, b) => a.top - b.top)[0];
              if (resist) eaSl = resist.top + tolerance;
              const support = smcSnap.bullOBs
                .filter(ob => ob.top < last.c)
                .sort((a, b) => b.top - a.top)[0];
              if (support) eaTp = support.top;
            }
          }

          // V24.1.3 (2026-05-08): PriceMap-aware TP cap.
          // The OB-only logic above ignores swing/liquidity walls between entry
          // and the first OB. Live log: TP=4766 with 4★ wall at 4734 in the path
          // → TP unreachable, BE/Trail at 0.5R never hits, position stalls.
          // Fix: cap eaTp at the first 3★+ wall from the PriceMap (covers all
          // TFs M5/M15/M30/H1/H4) so TP is realistically reachable.
          try {
            const eaPriceMap = indicatorPipeline.getPriceMap(upper);
            if (eaPriceMap && eaTp !== null && eaTp !== undefined) {
              const direction = detDecision.action === 'BUY' ? 'UP' : 'DOWN';
              const path = analyzePath(eaPriceMap, last.c, direction);
              // Use suggestedTP (before first 3★+ wall). If our OB-derived TP is
              // BEYOND that wall, replace it.
              const suggestedTp = path.suggestedTP;
              const conservativeTp = path.conservativeTP;
              const overshoot = direction === 'UP'
                ? eaTp > suggestedTp
                : eaTp < suggestedTp;
              if (overshoot) {
                const oldTp = eaTp;
                eaTp = suggestedTp;
                const obstaclesNote = path.biggestObstacle
                  ? ` (obstacle ${path.biggestObstacle.wall.confluenceStars}★ @ ${round2(path.biggestObstacle.wall.price)})`
                  : '';
                atLog(`   ${upper}: TP capped by PriceMap: ${oldTp.toFixed(2)} → ${eaTp.toFixed(2)} suggested=${suggestedTp.toFixed(2)} conservative=${conservativeTp.toFixed(2)}${obstaclesNote}`);
              }
            }
          } catch (e) {
            atLog(`   ${upper}: PriceMap TP cap skipped: ${e}`);
          }

          // ATR-based SL/TP fallback when SMC OBs don't provide valid levels
          if (!eaSl || !eaTp) {
            const atrVal = analysis.atr ?? (last.c * 0.005);
            const atrMult = detDecision.action === 'BUY' ? 1 : -1;
            const targetRrrEa = cfg.strategy?.minRRR ?? 1.5;
            if (!eaSl) eaSl = last.c - atrMult * atrVal * 1.5;
            if (!eaTp) eaTp = last.c + atrMult * atrVal * 1.5 * targetRrrEa;
          }

          // Verify RRR before allowing fallback
          const eaRiskDist = Math.abs(last.c - (eaSl ?? last.c));
          const eaRewardDist = Math.abs((eaTp ?? last.c) - last.c);
          const eaRRR = eaRiskDist > 0 ? eaRewardDist / eaRiskDist : 0;
          const minRRR = cfg.strategy?.minRRR ?? 1.2;

          // V24.0 fix: float-safe RRR compare (was failing when 1.4999... < 1.5)
          if (eaRRR + 1e-6 >= minRRR && eaRiskDist > 0) {
            aiDecision = {
              action: detDecision.action,
              management: detDecision.management,
              size_fraction: detDecision.size_fraction,
              confidence: detDecision.confidence,
              sl: eaSl,
              tp: eaTp,
              rationale: aiModeEnabled
                ? `[EA FALLBACK] AI unavailable — using deterministic+SMC V2: ${detDecision.rationale} | RRR=${eaRRR.toFixed(2)}`
                : `[EA-ONLY] AI disabled — deterministic+SMC V2: ${detDecision.rationale} | RRR=${eaRRR.toFixed(2)}`,
              rationale_th: aiModeEnabled
                ? `[🤖 EA อัตโนมัติ] AI ไม่พร้อม — ใช้ระบบ deterministic+SMC V2: ${detDecision.rationale_th || detDecision.rationale} | RRR=${eaRRR.toFixed(2)}`
                : `[🤖 EA เท่านั้น] ปิด AI — ใช้ deterministic+SMC V2: ${detDecision.rationale_th || detDecision.rationale} | RRR=${eaRRR.toFixed(2)}`,
              timeframe: detDecision.timeframe,
              strategy: primaryAnalysis.strategy,
              _source: aiModeEnabled ? 'ea_fallback_v21' : 'ea_only_v23',
            };
            atLog(`   ${upper}: ${aiModeEnabled ? '⚡ V21.0 EA FALLBACK' : '🤖 V23.0 EA-ONLY'} activated: ${detDecision.action} conf=${detDecision.confidence}% RRR=${eaRRR.toFixed(2)} SL=${eaSl?.toFixed(2)} TP=${eaTp?.toFixed(2)}`);
          } else {
            const misses: string[] = [];
            if (!(eaRiskDist > 0)) misses.push('invalid risk distance');
            if (eaRRR + 1e-6 < minRRR) misses.push(`RRR ${eaRRR.toFixed(2)} < ${minRRR}`);
            if (!eaSl || !eaTp) misses.push('missing SL/TP');
            eaFallbackMissReason = misses.join(', ') || 'conditions not met';
            atLog(`   ${upper}: V21.0 EA Fallback skipped — ${eaFallbackMissReason}`);
          }
        }

        // --- HARD FALLBACK ---
        // If AI is unavailable and EA Fallback didn't trigger, we MUST explicitly
        // set SKIP to prevent raw technical bias from trading unconditionally.
        if (!aiDecision) {
            const nowMs = Date.now();
            const lastCircuitLog = this.aiFallbackCircuitLastLogAt.get(upper) ?? 0;
            const hardFallbackCode =
              detDecision.action === 'SKIP'
                ? 'NO_DETERMINISTIC_SIGNAL'
                : detDecision.confidence < eaConfidenceThreshold
                ? 'EA_FALLBACK_CONFIDENCE_LOW'
                : detDecision.counterTrend && analysis.regime === 'VOLATILE_BREAKOUT'
                ? 'EA_FALLBACK_COUNTER_TREND_VOLATILE'
                : 'AI_UNAVAILABLE_EA_FALLBACK_BLOCKED';
            const hardFallbackReason =
              hardFallbackCode === 'NO_DETERMINISTIC_SIGNAL'
                ? `NO_DETERMINISTIC_SIGNAL: ${detDecision.rationale}`
                : hardFallbackCode === 'EA_FALLBACK_CONFIDENCE_LOW'
                ? `EA_FALLBACK_CONFIDENCE_LOW: deterministic ${detDecision.action} confidence ${detDecision.confidence} < ${eaConfidenceThreshold}`
                : hardFallbackCode === 'EA_FALLBACK_COUNTER_TREND_VOLATILE'
                ? `EA_FALLBACK_COUNTER_TREND_VOLATILE: ${detDecision.action} counter-trend during ${analysis.regime}`
                : `AI_UNAVAILABLE_EA_FALLBACK_BLOCKED: ${eaFallbackMissReason ?? 'conditions not met'}`;
            eaFallbackMissReason = hardFallbackReason;
            pushDecisionTrace(gateTrace, 'hard_fallback', 'SKIP', hardFallbackReason, {
              code: hardFallbackCode,
              aiModeEnabled,
              aiAvailable,
              eaConfidenceThreshold,
              eaFallbackMissReason,
            });
            if (aiModeEnabled && nowMs - lastCircuitLog > 5 * 60_000) {
                this.aiFallbackCircuitLastLogAt.set(upper, nowMs);
                atWarn(`   ${upper}: AI fallback circuit active — hard SKIP until EA fallback passes (${eaFallbackMissReason ?? 'no actionable deterministic fallback'})`);
            }
            aiDecision = {
                action: 'SKIP',
                rationale: hardFallbackReason,
                rationale_th: `AI ไม่พร้อม และไม่ผ่านเงื่อนไข EA Fallback (${eaFallbackMissReason ?? 'no actionable deterministic fallback'})`,
                timeframe: detDecision.timeframe,
                strategy: primaryAnalysis.strategy,
                _source: 'hard_fallback',
                block_code: hardFallbackCode,
            };
        }

        let selectedStrategy = pickStrategy(analysis, cfg);
        let side: 'BUY' | 'SELL' | 'SKIP' = analysis.bias === 'BULL' ? 'BUY' : analysis.bias === 'BEAR' ? 'SELL' : 'SKIP';

        // --- Counter-Trend Confluence Logic (P1.5 HARD BLOCK) ---
        // 2026-04-25: upgraded from soft gate to hard block.
        // 2026-05-08 V24.1: counter-trend test now also considers H4 bias (MTF) so the
        // gate doesn't block valid reversal entries when M15 (primary) lags H4. Pure
        // primary-TF check used to fire when H4=BULL was already confirmed (chart
        // BMS bullish) but M15 SMA-cross was still BEAR, blocking BUY-on-retest.
        const h4BiasForCt = analyses['H4']?.bias as ('BULL' | 'BEAR' | 'NEUTRAL' | undefined);
        const primaryCounterTrend = (analysis.bias === 'BULL' && analysis.regime === 'TRENDING_DOWN') ||
                                    (analysis.bias === 'BEAR' && analysis.regime === 'TRENDING_UP');
        // If H4 bias agrees with the primary intent direction, it's NOT counter-trend
        // even if M15 oscillator says otherwise.
        const intendedSide: 'BUY' | 'SELL' | 'SKIP' = analysis.bias === 'BULL' ? 'BUY' : analysis.bias === 'BEAR' ? 'SELL' : 'SKIP';
        const htfAgreesWithIntent =
          (intendedSide === 'BUY' && h4BiasForCt === 'BULL') ||
          (intendedSide === 'SELL' && h4BiasForCt === 'BEAR');
        const isCounterTrend = primaryCounterTrend && !htfAgreesWithIntent;
        const ctMinConf = cfg.adaptive?.counterTrendConfluenceMin ?? 62; // P1.5: raised from 55 to 62

        let rationale = `${selectedStrategy} | ${analysis.rationale}`;
        let translatedTh = null;
        let forcedSkipReason: string | null = null;

        if (aiDecision) {
            const action = aiDecision.action || 'SKIP';
            const aiTh = aiDecision.rationale_th || aiDecision.rationale;
            
            if (aiDecision.strategy) {
                selectedStrategy = aiDecision.strategy as StrategyType;
            }
            // Technical rationale stays technical
            rationale = `[AI ${action}] ${aiDecision.rationale}`;
            
            // Thai Rationale for Dashboard - Clean & Beautiful
            if (aiTh.includes('stale_price')) {
                const pts = aiTh.replace('stale_price_', '').replace('pts', '');
                translatedTh = `⚠️ ข้ามการเทรด (ราคาขยับไปแล้ว ${pts} จุด)\n` +
                               `📍 กลยุทธ์: ${selectedStrategy} (${cfg.timeframe})`;
            } else if (action === 'SKIP') {
                const displayReason = (aiTh && aiTh !== '()') ? aiTh : 'ไม่มีเหตุผลระบุ';
                translatedTh = `⚠️ ข้ามการเทรด (SKIP)\n` +
                               `📝 เหตุผล: ${displayReason}\n` +
                               `📍 กลยุทธ์: ${selectedStrategy} (${cfg.timeframe})`;
            } else {
                // Check if it's eventually going to be BLOCKED (side rewritten to SKIP later)
                const actionEmoji = action === 'BUY' ? '🟢' : action === 'SELL' ? '🔴' : '⚠️';
                translatedTh = `${actionEmoji} AI แนะนำ: ${action}\n` +
                               `📝 วิเคราะห์: ${aiTh}\n` +
                               `📍 กลยุทธ์: ${selectedStrategy} (${cfg.timeframe})`;
            }

            // Proactive notification for high confidence analysis
            if (cfg.notificationSettings?.onAnalysis && aiDecision.confidence > 70) {
               broadcast({ 
                 type: 'intelligence_alert', 
                 title: `AI Analysis: ${upper}`, 
                 message: ` [${aiDecision.action}] ${aiTh} \n(Source: ${selectedStrategy} on ${cfg.timeframe})`,
                 priority: 'HIGH',
                 at: Date.now()
               });
            }

            const proposedAction = aiDecision.action as any;
            const sideResolution = resolveDecisionSide({
                technicalSide: side,
                proposedAction,
                confidence: Number(aiDecision.confidence ?? 0),
                minOverrideConfidence: cfg.adaptive?.decisionOverrideConfidenceMin ?? 75,
            });
            if (!sideResolution.applied && (proposedAction === 'BUY' || proposedAction === 'SELL')) {
                forcedSkipReason = `Decision Side Guard blocked: ${sideResolution.reason}`;
                atWarn(`   ${upper}: ${forcedSkipReason}. Forced SKIP.`);
            }
            side = sideResolution.side;

            // --- Pre-Gate Validator (XAUUSD SL Distance) ---
            if (side !== 'SKIP' && upper === 'XAUUSD' && (proposedAction === 'BUY' || proposedAction === 'SELL') && aiDecision.sl) {
                const entry = last.c;
                const slDist = Math.abs(entry - aiDecision.sl);
                const minSlDist = 1.5; // Enforce 1.5 points (150 ticks)
                if (slDist < minSlDist) {
                    forcedSkipReason = `SL Distance Guard blocked: ${slDist.toFixed(4)} < ${minSlDist}`;
                    atWarn(`   ${upper}: AI provided invalid SL distance (${slDist.toFixed(4)} < ${minSlDist}). Forced SKIP.`);
                    side = 'SKIP';
                }
            }

            if (forcedSkipReason) {
                aiDecision = {
                    ...aiDecision,
                    action: 'SKIP',
                    rationale: forcedSkipReason,
                    rationale_th: forcedSkipReason,
                    _source: 'decision_guard',
                };
                rationale = `[AI SKIP] ${forcedSkipReason}`;
                translatedTh = `⚠️ SKIP\n${forcedSkipReason}`;
            }

               // --- Post-AI Counter-Trend & Momentum Guard ---
               if (side === 'BUY' || side === 'SELL') {
                   let latestProximityPass: { wallStars: number; distancePip: number; reason: string } | null = null;
                   // V24.1 (2026-05-08): MTF-aware counter-trend test. M15 alone misjudges
                   // reversals where H4 has already flipped (chart BMS bullish but M15 SMA
                   // still bear → blocks valid BUY-on-pullback). Exempt when H4 agrees.
                   const h4BiasNow = analyses['H4']?.bias as ('BULL' | 'BEAR' | 'NEUTRAL' | undefined);
                   const aiCtPrimary = (side === 'BUY' && analysis.bias === 'BEAR') ||
                                       (side === 'SELL' && analysis.bias === 'BULL');
                   const aiHtfAgrees = (side === 'BUY' && h4BiasNow === 'BULL') ||
                                       (side === 'SELL' && h4BiasNow === 'BEAR');
                   const isAiCounterTrend = aiCtPrimary && !aiHtfAgrees;
                   if (aiCtPrimary && aiHtfAgrees) {
                       atLog(`   ${upper}: Counter-trend gate skipped — H4=${h4BiasNow} agrees with AI ${side} (MTF-aware exemption).`);
                   }

                   if (isAiCounterTrend) {
                        // 1. Never trade against a VOLATILE_BREAKOUT (Falling Knife / Squeeze)  hard block
                        if (analysis.regime === 'VOLATILE_BREAKOUT') {
                            atWarn(`   ${upper}: AI suggested counter-trend ${side} during a VOLATILE_BREAKOUT! Blocking falling knife.`);
                            side = 'SKIP';
                        }
                        // 2. P1.5: HARD BLOCK counter-trend when confluence < ctMinConf (62).
                        //    Log analysis shows counter-trend entries at 51-58 confluence
                        //    consistently close at BE with zero profit.
                        else if (analysis.confluence < ctMinConf) {
                            atWarn(`   ${upper}: Counter-trend ${side} BLOCKED  confluence ${analysis.confluence.toFixed(1)} < ${ctMinConf}. Historically these close at BE.`);
                            counterTrendBlocksTotal.inc({ symbol: upper, side });
                            side = 'SKIP';
                        }
                    }

                    // --- V24.0 Fade-the-Level Proximity Gate (PRE Zone-Aware) ---
                    // เปลี่ยน entry policy จาก "BREAKOUT chasing" → "Fade S/R wall"
                    // BUY ก็ต่อเมื่อราคาลงไปใกล้ supportWall (50-100 pip)
                    // SELL ก็ต่อเมื่อราคาขึ้นไปใกล้ resistanceWall (50-100 pip)
                    // ★★★+ ผ่าน, ★★ ต้อง LTF (M5/M15) confirm, ★ reject
                    if ((cfg.adaptive?.enableProximityGate ?? true) && (side === 'BUY' || side === 'SELL')) {
                        // V24.1: Bypass ProximityGate for explicit breakout/continuation strategies
                        // — BUT only when HTF is genuinely trending. If H4 is RANGING, the
                        // "breakout" label from analyzers is unreliable (often a fakeout)
                        // and bypassing the proximity wall has historically caused entries
                        // right under a 4★ resistance cluster (live log 2026-05-08).
                        const intentText = `${aiDecision?.rationale ?? ''} ${detDecision.rationale ?? ''}`;
                        const continuationIntent = hasContinuationIntent(selectedStrategy, intentText);
                        const isBreakoutStrategy = selectedStrategy.includes('BREAKOUT') ||
                                                   selectedStrategy.includes('CONTINUATION') ||
                                                   selectedStrategy.includes('MOMENTUM') ||
                                                   selectedStrategy.includes('SCALP') ||
                                                   continuationIntent;
                        const isBreakoutRegime = analysis.regime === 'VOLATILE_BREAKOUT';
                        const h4RegimeNow = analyses['H4']?.regime;
                        const htfIsTrending = h4RegimeNow === 'TRENDING_UP' ||
                                              h4RegimeNow === 'TRENDING_DOWN' ||
                                              h4RegimeNow === 'VOLATILE_BREAKOUT' ||
                                              (isBreakoutRegime && htfBiasAgrees(side as TradeSide, analyses));
                        const continuationMinConf = isBreakoutRegime
                          ? (cfg.adaptive?.zoneBreakoutConfluenceMin ?? 55)
                          : (cfg.adaptive?.proximityContinuationConfluenceMin ?? 62);
                        const htfContinuationAligned = continuationIntent &&
                          htfBiasAgrees(side as TradeSide, analyses) &&
                          analysis.confluence >= continuationMinConf;
                        const allowBypass = isBreakoutStrategy && (htfIsTrending || htfContinuationAligned);

                        if (allowBypass) {
                             atLog(`   ${upper}: Proximity Gate: BYPASSED for ${selectedStrategy} strategy (H4=${h4RegimeNow}, continuation=${continuationIntent}, htfAligned=${htfContinuationAligned}, conf=${analysis.confluence.toFixed(1)}/${continuationMinConf}).`);
                        } else if (isBreakoutStrategy && !htfIsTrending) {
                             atLog(`   ${upper}: Proximity Gate: NOT bypassing ${selectedStrategy} (H4=${h4RegimeNow}, not trending) — wall check enforced (conf=${analysis.confluence.toFixed(1)}/${continuationMinConf}).`);
                        }
                        if (!allowBypass) {
                            const proxPriceMap = indicatorPipeline.getPriceMap(upper);
                            const ltfAnalysis = analyses['M5'] ?? analyses['M15'] ?? null;
                            const baseMaxPip = cfg.adaptive?.proximityMaxPip ?? 100;

                            // Keep the fade entry genuinely close to a wall. ATR can expand
                            // the budget in volatile markets, but the cap prevents "near"
                            // from becoming thousands of pips away on XAUUSD.
                            const currentAtr = analysis.atr ?? (Math.abs(last.h - last.l) || last.c * 0.001);
                            const maxPipDist = computeAdaptiveProximityMaxPip(upper, currentAtr, baseMaxPip);

                            const requireLtfConfirmation = selectedStrategy.includes('SCALP') || selectedStrategy.includes('SMC_FVG_REVERSAL');
                            const proxResult = evaluateProximityGate(
                                upper,
                                side as 'BUY' | 'SELL',
                                last.c,
                                proxPriceMap,
                                ltfAnalysis,
                                {
                                    maxPipDist,
                                    unconditionalStars: cfg.adaptive?.proximityUnconditionalStars,
                                    conditionalStars: cfg.adaptive?.proximityConditionalStars,
                                    requireLtfConfirmation,
                                    strongWallRelaxMultiplier: cfg.adaptive?.proximityStrongWallRelaxMultiplier ?? 1.15,
                                }
                            );
                            if (!proxResult.allowed) {
                                forcedSkipReason = `Proximity Gate blocked: ${proxResult.reason}`;
                                atWarn(`   ${upper}: ${forcedSkipReason}. WAIT_PULLBACK.`);
                                zoneGateBlocksTotal.inc({ symbol: upper, side, zone: 'PROXIMITY' });
                                side = 'SKIP';
                            } else {
                                latestProximityPass = {
                                    wallStars: proxResult.wallStars,
                                    distancePip: proxResult.distancePip,
                                    reason: proxResult.reason,
                                };
                                atLog(`   ${upper}: Proximity Gate: ${proxResult.reason}.`);
                            }
                        }
                    }

                    // --- P1.2 (expanded) + P1.3: Zone-Aware Gate (post-AI) ---
                    // Block BUY in PREMIUM and SELL in DISCOUNT regardless of H4 bias.
                    // Buying high / selling low is suboptimal in any regime; the prior
                    // version only fired when H4 trend matched the side, which let
                    // counter-trend BUY-at-top / SELL-at-bottom slip through (see audit
                    //  P1.2  2026-04-25).
                    // V26.4: evaluate premium/discount as an MTF stack. H4/H1 are
                    // context, while M30/M15/M5/active TF can prove the actual entry
                    // has pulled back into a better local zone. This prevents H4 from
                    // vetoing every trend-follow scalp after price is already deep in
                    // the larger swing.
                    if ((cfg.adaptive?.enableZoneAwareGate ?? true) && (side === 'BUY' || side === 'SELL')) {
                        const hardBlockPct = cfg.adaptive?.zoneHardBlockPct ?? 25;
                        const zoneLayers: Array<{ timeframe: string; result: any; role: 'major' | 'context' | 'execution' }> = [];
                        const pushZoneLayer = (tf: string, role: 'major' | 'context' | 'execution') => {
                            const tfCandles = analyses[tf]?.candles;
                            if (tfCandles && tfCandles.length >= 20) {
                                zoneLayers.push({ timeframe: tf, role, result: classifyPremiumDiscount(tfCandles, last.c) });
                            }
                        };
                        pushZoneLayer('H4', 'major');
                        pushZoneLayer('H1', 'context');
                        pushZoneLayer('M30', 'execution');
                        pushZoneLayer('M15', 'execution');
                        pushZoneLayer('M5', 'execution');
                        if (!['H4', 'H1', 'M30', 'M15', 'M5'].includes(activeTF)) {
                            pushZoneLayer(activeTF, 'execution');
                        }
                        if (zoneLayers.length === 0) {
                            const fallbackCandles = allCandles.get(upper) || [];
                            if (fallbackCandles.length >= 20) {
                                zoneLayers.push({
                                    timeframe: activeTF,
                                    role: activeTF === 'H4' ? 'major' : activeTF === 'H1' ? 'context' : 'execution',
                                    result: classifyPremiumDiscount(fallbackCandles, last.c),
                                });
                            }
                        }

                        const zoneContext = buildZoneLayerContext(side as TradeSide, zoneLayers, hardBlockPct);
                        const zoneCheck = zoneContext.primaryLayer?.result;
                        if (zoneCheck) {
                            const h4Layer = zoneContext.layers.find((layer) => layer.timeframe === 'H4');
                            const h4Bias = analyses['H4']?.bias;
                            const h4Pct = h4Layer?.result.pctFromRange ?? zoneCheck.pctFromRange;
                            const allowBreakout = (cfg.adaptive?.allowBreakoutThroughZone ?? true);
                            const breakoutBuy  = allowBreakout && side === 'BUY'  && h4Pct > 100 && h4Bias === 'BULL';
                            const breakoutSell = allowBreakout && side === 'SELL' && h4Pct < 0   && h4Bias === 'BEAR';

                            const smcSources = [analyses['H4']?.smc, analyses['H1']?.smc, analyses[activeTF]?.smc];
                            const isSMCSupported = smcSources.some((smc) => isSupportedBySMC(side as 'BUY'|'SELL', last.c, smc));
                            const isBreakoutRegime = analysis.regime === 'VOLATILE_BREAKOUT';
                            const zoneContinuationMin = isBreakoutRegime
                              ? (cfg.adaptive?.zoneBreakoutConfluenceMin ?? 55)
                              : (cfg.adaptive?.zoneContinuationConfluenceMin ?? 68);
                            const zoneLocalAlignmentMin = cfg.adaptive?.zoneLocalAlignmentConfluenceMin ?? zoneContinuationMin;
                            const zoneWallFadeMinStars = cfg.adaptive?.zoneWallFadeMinStars ?? 4;
                            const zoneWallFadeMinConfluence = cfg.adaptive?.zoneWallFadeConfluenceMin ?? 50;
                            const zoneContinuationIntent = hasContinuationIntent(selectedStrategy, aiDecision?.rationale, detDecision.rationale);
                            
                            // V24.1.5: Relax counter-trend for SCALPING strategies.
                            // If M15 or M5 has started making an aligned trend, allow it even if H4 disagrees.
                            const isScalp = selectedStrategy === 'SMC_FVG_SCALP' || selectedStrategy === 'SCALPING' || selectedStrategy === 'SMC_FVG_MAGNET_SCALP';
                            const m15Bias = analyses['M15']?.bias;
                            const m5Bias = analyses['M5']?.bias;
                            const ltfAligned = side === 'BUY' ? (m15Bias === 'BULL' || m5Bias === 'BULL') : (m15Bias === 'BEAR' || m5Bias === 'BEAR');
                            
                            const zoneHtfAligned = htfBiasAgrees(side as TradeSide, analyses) || (isScalp && ltfAligned);
                            
                            const fvgTimeframes: string[] = [];
                            const fvgTfCandidates = [...new Set(['H4', 'H1', 'M30', 'M15', 'M5', activeTF])];
                            for (const tf of fvgTfCandidates) {
                                const tfCandles = analyses[tf]?.candles || (tf === activeTF ? allCandles.get(upper) : null);
                                if (!tfCandles || tfCandles.length < 20) continue;
                                if (hasAlignedFvgNearPrice(side as TradeSide, last.c, getActiveFVGs(tfCandles), {
                                    atr: analysis.atr ?? primaryAnalysis?.atr ?? 0,
                                    atrMultiplier: cfg.adaptive?.zoneFvgProximityAtrMul ?? 0.35,
                                })) {
                                    fvgTimeframes.push(tf);
                                }
                            }
                            const fvgSupported = fvgTimeframes.length > 0;
                            const wallFadeSupported =
                                Boolean(latestProximityPass) &&
                                (latestProximityPass?.wallStars ?? 0) >= zoneWallFadeMinStars &&
                                analysis.confluence >= zoneWallFadeMinConfluence;
                            const localExecutionOverride =
                                zoneContext.executionFavorable &&
                                zoneContinuationIntent &&
                                zoneHtfAligned &&
                                !zoneContext.hardExecutionWrong &&
                                analysis.confluence >= zoneLocalAlignmentMin &&
                                (
                                  isSMCSupported ||
                                  fvgSupported ||
                                  wallFadeSupported ||
                                  analysis.confluence >= zoneLocalAlignmentMin + 6
                                );
                            const continuationOverride =
                                zoneContinuationIntent &&
                                zoneHtfAligned &&
                                !zoneContext.hardExecutionWrong &&
                                (
                                  isSMCSupported ||
                                  fvgSupported ||
                                  wallFadeSupported ||
                                  localExecutionOverride ||
                                  (!zoneContext.hardMajorWrong && analysis.confluence >= zoneContinuationMin)
                                );
                            const breakoutPass = breakoutBuy || breakoutSell || isSMCSupported || continuationOverride;
                            const zoneLabel = zoneCheck.zone;

                            if (zoneContext.hasWrongZone && !breakoutPass) {
                                const missing = describeZoneMissingEvidence({
                                    side: side as TradeSide,
                                    pctFromRange: zoneCheck.pctFromRange,
                                    hardBlockPct,
                                    htfAligned: zoneHtfAligned,
                                    continuationIntent: zoneContinuationIntent,
                                    smcSupported: isSMCSupported,
                                    fvgSupported,
                                    fvgTimeframes,
                                    wallFadeSupported,
                                    wallStars: latestProximityPass?.wallStars,
                                    localExecutionAligned: zoneContext.executionFavorable,
                                    zonePath: zoneContext.path,
                                    confluence: analysis.confluence,
                                    minConfluence: zoneContinuationMin,
                                });
                                forcedSkipReason = `Zone-Aware Gate blocked: ${side} in ${zoneLabel} zone (${zoneCheck.pctFromRange.toFixed(1)}% of primary range; H4=${h4Bias}; wrongTF=${zoneContext.wrongLayers.join('/') || 'none'}); missing override: ${missing}`;
                                atWarn(`   ${upper}: ${forcedSkipReason}.`);
                                zoneGateBlocksTotal.inc({ symbol: upper, side, zone: zoneLabel });
                                side = 'SKIP';
                            } else if (isSMCSupported) {
                                atLog(`   ${upper}: Zone-Aware Gate: ${side} in ${zoneLabel} zone ALLOWED - supported by Institutional SMC (zones=${zoneContext.path})`);
                            } else if (continuationOverride) {
                                atLog(`   ${upper}: Zone-Aware Gate: ${side} in ${zoneLabel} zone ALLOWED - MTF continuation override (conf=${analysis.confluence.toFixed(1)}, fvgTF=${fvgTimeframes.join('/') || 'none'}, localAligned=${zoneContext.executionFavorable}, wallFade=${wallFadeSupported ? String(latestProximityPass?.wallStars) : 'false'}, zones=${zoneContext.path})`);
                            } else if (breakoutPass) {
                                atLog(`   ${upper}: Zone-Aware Gate: ${side} ALLOWED - trend-aligned breakout (${h4Pct.toFixed(1)}% of H4 range, H4=${h4Bias})`);
                            }
                        }
                    }

                    if (false && (cfg.adaptive?.enableZoneAwareGate ?? true) && (side === 'BUY' || side === 'SELL')) {
                        const h4CandlesForZone = allCandles.get(upper) || [];
                        if (h4CandlesForZone.length >= 20) {
                            const zoneCheck = classifyPremiumDiscount(h4CandlesForZone, last.c);
                            const h4Bias = analyses['H4']?.bias;
                            const wrongZoneBuy = side === 'BUY' && zoneCheck.zone === 'PREMIUM';
                            const wrongZoneSell = side === 'SELL' && zoneCheck.zone === 'DISCOUNT';
                            // 2026-04-26 — allow trend-aligned breakouts. When price has
                            // already broken ABOVE the H4 swing high (pct > 100) and H4
                            // bias is BULL, a BUY is a continuation, not buying at top.
                            // Mirror logic for SELL breaking BELOW (pct < 0) with H4=BEAR.
                            const allowBreakout = (cfg.adaptive?.allowBreakoutThroughZone ?? true);
                            const breakoutBuy  = allowBreakout && side === 'BUY'  && zoneCheck.pctFromRange > 100 && h4Bias === 'BULL';
                            const breakoutSell = allowBreakout && side === 'SELL' && zoneCheck.pctFromRange < 0   && h4Bias === 'BEAR';
                            
                            // V20.0 — New: SMC Support Gate. If we are in Premium/Discount but sitting on a 
                            // professional Bullish OB or FVG (from Python bridge), it's a valid "Buy on Retest".
                            const pySmcH4 = analyses['H4']?.smc;
                            const isSMCSupported = isSupportedBySMC(side as 'BUY'|'SELL', last.c, pySmcH4);
                            const zoneContinuationMin = cfg.adaptive?.zoneContinuationConfluenceMin ?? 68;
                            const hardBlockPct = cfg.adaptive?.zoneHardBlockPct ?? 25;
                            const zoneWallFadeMinStars = cfg.adaptive?.zoneWallFadeMinStars ?? 4;
                            const zoneWallFadeMinConfluence = cfg.adaptive?.zoneWallFadeConfluenceMin ?? 50;
                            const zoneContinuationIntent = hasContinuationIntent(selectedStrategy, aiDecision?.rationale, detDecision.rationale);
                            const zoneHtfAligned = htfBiasAgrees(side as TradeSide, analyses);
                            const activeFvgsForZone = getActiveFVGs(h4CandlesForZone);
                            const fvgSupported = activeFvgsForZone.some((f) =>
                                (side === 'BUY' && f.type === 'BULL' && last.c <= f.top * 1.002 && last.c >= f.bottom * 0.998) ||
                                (side === 'SELL' && f.type === 'BEAR' && last.c <= f.top * 1.002 && last.c >= f.bottom * 0.998)
                            );
                            const wallFadeSupported =
                                Boolean(latestProximityPass) &&
                                (latestProximityPass?.wallStars ?? 0) >= zoneWallFadeMinStars &&
                                analysis.confluence >= zoneWallFadeMinConfluence;
                            const continuationOverride =
                                zoneContinuationIntent &&
                                zoneHtfAligned &&
                                !isDeepWrongZone(side as TradeSide, zoneCheck.pctFromRange, hardBlockPct) &&
                                (isSMCSupported || fvgSupported || wallFadeSupported || analysis.confluence >= zoneContinuationMin);
                            
                            const breakoutPass = breakoutBuy || breakoutSell || isSMCSupported || continuationOverride;

                            if ((wrongZoneBuy || wrongZoneSell) && !breakoutPass) {
                                const missing = describeZoneMissingEvidence({
                                    side: side as TradeSide,
                                    pctFromRange: zoneCheck.pctFromRange,
                                    hardBlockPct,
                                    htfAligned: zoneHtfAligned,
                                    continuationIntent: zoneContinuationIntent,
                                    smcSupported: isSMCSupported,
                                    fvgSupported,
                                    wallFadeSupported,
                                    wallStars: latestProximityPass?.wallStars,
                                    confluence: analysis.confluence,
                                    minConfluence: zoneContinuationMin,
                                });
                                forcedSkipReason = `Zone-Aware Gate blocked: ${side} in ${zoneCheck.zone} zone (${zoneCheck.pctFromRange.toFixed(1)}% of range; H4=${h4Bias}); missing override: ${missing}`;
                                atWarn(`   ${upper}: ${forcedSkipReason}.`);
                                zoneGateBlocksTotal.inc({ symbol: upper, side, zone: zoneCheck.zone });
                                side = 'SKIP';
                            } else if (isSMCSupported) {
                                atLog(`   ${upper}: Zone-Aware Gate: ${side} in ${zoneCheck.zone} zone ALLOWED — supported by Institutional SMC (OB/FVG)`);
                            } else if (continuationOverride) {
                                atLog(`   ${upper}: Zone-Aware Gate: ${side} in ${zoneCheck.zone} zone ALLOWED — continuation override (conf=${analysis.confluence.toFixed(1)}, fvg=${fvgSupported}, wallFade=${wallFadeSupported ? `${latestProximityPass?.wallStars}★` : 'false'}, htfAligned=${zoneHtfAligned})`);
                            } else if (breakoutPass) {
                                atLog(`   ${upper}: Zone-Aware Gate: ${side} ALLOWED — trend-aligned breakout (${zoneCheck.pctFromRange.toFixed(1)}% of H4 range, H4=${h4Bias})`);
                            }
                        }
                    }

                    // --- P1.3: FVG Entry Alignment for SMC_FVG_SCALP ---
                    // For FVG-scalp setups, require entry to be inside (or just outside)
                    // an FVG aligned with the trade direction. This stops "buy above FVG
                    // expecting more upside"  the strategy should be a FILL, not a chase.
                    if (
                        (cfg.adaptive?.enableZoneAwareGate ?? true) &&
                        (side === 'BUY' || side === 'SELL') &&
                        selectedStrategy === 'SMC_FVG_SCALP'
                    ) {
                        const fvgTimeframes: string[] = [];
                        let activeFvgCount = 0;
                        const fvgTfCandidates = [...new Set(['H1', 'M30', 'M15', 'M5', activeTF])];
                        for (const tf of fvgTfCandidates) {
                            const tfCandles = analyses[tf]?.candles || (tf === activeTF ? allCandles.get(upper) : null);
                            if (!tfCandles || tfCandles.length < 20) continue;
                            const activeFvgs = getActiveFVGs(tfCandles);
                            activeFvgCount += activeFvgs.length;
                            if (hasAlignedFvgNearPrice(side as TradeSide, last.c, activeFvgs, {
                                atr: analysis.atr ?? primaryAnalysis?.atr ?? 0,
                                atrMultiplier: cfg.adaptive?.zoneFvgProximityAtrMul ?? 0.35,
                                tolerancePct: 0.005,
                            })) {
                                fvgTimeframes.push(tf);
                            }
                        }
                        if (fvgTimeframes.length === 0) {
                            forcedSkipReason = `FVG-Align Gate blocked: SMC_FVG_SCALP ${side} but price ${last.c} not near any aligned MTF FVG (${activeFvgCount} active)`;
                            atWarn(`   ${upper}: ${forcedSkipReason}.`);
                            zoneGateBlocksTotal.inc({ symbol: upper, side, zone: 'FVG_MISALIGN' });
                            side = 'SKIP';
                        } else if (fvgTimeframes.length > 0) {
                            atLog(`   ${upper}: FVG-Align Gate: ${side} aligned on ${fvgTimeframes.join('/')}`);
                        }
                    }
               }
        }

        // V24.0 — Sequential Entry Gate (1 ไม้แรก/symbol; ไม้ที่ 2+ ต้องเข้าเงื่อนไข)
        // (a) ไม้ล่าสุด safe (TRAIL/BE/Golden) → เปิดเพิ่มเป็น pyramid ฝั่งกำไร
        // (b) ไม้ล่าสุดขาดทุน >= sequentialAddOnLossPct (default 25% ของ SL) → เปิดเพิ่มเป็น recovery (แก้ไม้)
        // (c) detDecision.management = SCALE_IN / HEDGE → ผ่าน (เป็น defense plan)
        if (
          (cfg.adaptive?.enableSequentialEntry ?? true) &&
          (side === 'BUY' || side === 'SELL')
        ) {
          const sameSide = symbolPositions.filter(p => p.side === side);
          const isPlannedDefense =
            detDecision.management === 'SCALE_IN' ||
            detDecision.management === 'HEDGE';
          if (sameSide.length > 0 && detDecision.management === 'SCALE_IN') {
            const lossPctThreshold = cfg.adaptive?.sequentialAddOnLossPct ?? 0.25;
            const requiresSafeOrLossGate = cfg.adaptive?.scaleInRequiresSafeOrLossGate ?? true;
            const passTrailGate = isLastSafe;
            const passLossGate = lastPositionR <= -lossPctThreshold;
            if (requiresSafeOrLossGate && !passTrailGate && !passLossGate) {
              forcedSkipReason = `Sequential SCALE_IN blocked: last ${side} is not safe and not losing enough (lastR=${lastPositionR.toFixed(3)}, need TRAIL/BE or <= -${lossPctThreshold.toFixed(2)}R)`;
              atWarn(`   ${upper}: ${forcedSkipReason}.`);
              pushDecisionTrace(gateTrace, 'sequential_entry', 'BLOCK', forcedSkipReason, {
                management: detDecision.management,
                sameSide: sameSide.length,
                lastPositionR,
                isLastSafe,
              });
              side = 'SKIP';
            } else {
              const seqAtr = analysis.atr ?? (Math.abs(last.h - last.l) || last.c * 0.001);
              const scaleBaseMin = upper.includes('XAU') || upper.includes('GOLD')
                ? (cfg.adaptive?.scaleInMinEntryDistanceXAU ?? 2.0)
                : Math.max(last.c * 0.00025, 0.00025);
              const duplicateIssue = tradingExecutionService.nearDuplicateEntryIssue(
                upper,
                side,
                last.c,
                positions,
                cfg,
                {
                  atr: seqAtr,
                  baseMinDistance: scaleBaseMin,
                  atrMultiplier: cfg.adaptive?.scaleInMinEntryDistanceAtrMul ?? 0.04,
                  comment: 'AutoEngine scale-in gate',
                },
              );
              if (duplicateIssue) {
                forcedSkipReason = `Sequential SCALE_IN blocked: ${duplicateIssue}`;
                atWarn(`   ${upper}: ${forcedSkipReason}.`);
                pushDecisionTrace(gateTrace, 'sequential_entry', 'BLOCK', forcedSkipReason, {
                  management: detDecision.management,
                  sameSide: sameSide.length,
                  scaleBaseMin,
                  lastPositionR,
                  isLastSafe,
                });
                side = 'SKIP';
              } else {
                const trigger = passTrailGate ? 'TRAIL_SAFE' : `LOSS_${(lossPctThreshold * 100).toFixed(0)}%`;
                atLog(`   ${upper}: Sequential SCALE_IN: ${trigger} OK (last R=${lastPositionR.toFixed(3)}, minDist=${scaleBaseMin.toFixed(2)}).`);
                pushDecisionTrace(gateTrace, 'sequential_entry', 'ALLOW', `SCALE_IN ${trigger} OK`, {
                  management: detDecision.management,
                  sameSide: sameSide.length,
                  scaleBaseMin,
                  lastPositionR,
                  isLastSafe,
                });
              }
            }
          } else if (!isPlannedDefense) {
            const seqAtr = analysis.atr ?? (Math.abs(last.h - last.l) || last.c * 0.001);
            const duplicateIssue = tradingExecutionService.nearDuplicateEntryIssue(
              upper,
              side,
              last.c,
              positions,
              cfg,
              { 
                atr: seqAtr, 
                comment: 'AutoEngine sequential gate',
                ttlMs: cfg.adaptive?.sequentialDuplicateIntentTtlMs ?? 300_000
              },
            );
            if (duplicateIssue) {
              forcedSkipReason = `Sequential Entry blocked: ${duplicateIssue}`;
              atWarn(`   ${upper}: ${forcedSkipReason}. Choose one setup or wait for spacing.`);
              pushDecisionTrace(gateTrace, 'sequential_entry', 'BLOCK', forcedSkipReason, {
                management: detDecision.management,
                sameSide: sameSide.length,
              });
              side = 'SKIP';
            }
          }

          if (sameSide.length > 0 && side !== 'SKIP' && lastPos && lastPos.side === side && !isPlannedDefense) {
            const lossPctThreshold = cfg.adaptive?.sequentialAddOnLossPct ?? 0.25;
            const passTrailGate = isLastSafe;
            const passLossGate = lastPositionR <= -lossPctThreshold;
            if (!passTrailGate && !passLossGate) {
              atWarn(`   ${upper}: Sequential Entry: ${sameSide.length} ${side} open, last R=${lastPositionR.toFixed(3)} (need TRAIL or ≤-${lossPctThreshold.toFixed(2)}R). BLOCKED.`);
              side = 'SKIP';
            } else {
              const trigger = passTrailGate ? 'TRAIL_SAFE' : `LOSS_${(lossPctThreshold * 100).toFixed(0)}%`;
              atLog(`   ${upper}: Sequential Entry: ${trigger} OK (last R=${lastPositionR.toFixed(3)}, ${sameSide.length} ${side} open).`);
            }
          }
        }

        const atr = analysis.atr ?? (Math.abs(last.h - last.l) || last.c * 0.001);
        // 2026-05-10 V25.1: surface bias-vs-side contradiction so the user can spot
        // when AI overrides the deterministic regime (BUY against BEAR bias and
        // vice versa). Tag strategy COUNTER_TREND in the rationale so cluster
        // sizing/risk caps can react. Does NOT block the trade — V20 design
        // permits AI override.
        const biasVsSideContradicts =
          (side === 'BUY' && analysis.bias === 'BEAR') ||
          (side === 'SELL' && analysis.bias === 'BULL');
        if (biasVsSideContradicts) {
          atWarn(`   ${upper}: ⚠️ COUNTER-TREND override — Final Side=${side} but deterministic Bias=${analysis.bias}. AI is overriding regime. Reduce size or require stronger confirmation.`);
        }
        // eslint-disable-next-line no-console
        atLog(`   ${upper}: Final Side=${side} | Regime=${analysis.regime} | Bias=${analysis.bias} | Confluence=${analysis.confluence.toFixed(1)} | Fitness=${analysis.fitness.toFixed(1)} | Strategy=${selectedStrategy}${biasVsSideContradicts ? ' [COUNTER_TREND]' : ''}`);
        const riskMultiplier =
          selectedStrategy === 'BREAKOUT' ? 1.2 :
          selectedStrategy === 'MEAN_REVERSION' ? 1.0 :
          selectedStrategy === 'TREND_FOLLOW' ? 1.5 : 1.3;
        const rawRiskDistance = atr > 0 ? atr * riskMultiplier : last.c * 0.002;
        const entry = last.c;
        const targetRrr =
          (selectedStrategy === 'MEAN_REVERSION' || selectedStrategy === 'SCALPING' || selectedStrategy === 'SMC_FVG_SCALP') ? Math.max(1.2, cfg.strategy.scalpingRRR) :
          selectedStrategy === 'TREND_FOLLOW' ? Math.max(cfg.minRRR, cfg.strategy.swingRRR) :
          Math.max(cfg.minRRR, 1.2);

        // Snap SL/TP to broker tick grid so journal values match the actual
        // stored order after the broker's own rounding. `riskDistance` is
        // recomputed from the rounded SL so volume sizing and gate checks
        // use the exact distance the broker will enforce.
        const tick = tickSize(upper, cfg.risk.pointValueOverride || {});
        const biasSide = analysis.bias === 'BULL' ? 'BUY' : analysis.bias === 'BEAR' ? 'SELL' : 'SKIP';
        // 2026-05-10 FIX: prefer FINAL side (post-AI) so ATR fallback lines up with
        // the executed direction. biasSide is used only as fallback when side=SKIP
        // (diagnostic path).
        const rawDirSide = (side === 'BUY' || side === 'SELL') ? side : biasSide;
        const rawSl = rawDirSide === 'BUY' ? entry - rawRiskDistance : rawDirSide === 'SELL' ? entry + rawRiskDistance : null;
        const rawTp = rawDirSide === 'BUY' ? entry + rawRiskDistance * targetRrr
                    : rawDirSide === 'SELL' ? entry - rawRiskDistance * targetRrr : null;

        // --- V20.0: SMC-Aware SL/TP from slTpAnalystAgent ---
        // Runs BEFORE the Execution Trader so its structural levels feed into
        // the AI validator. ATR-based rawSl/rawTp remain as backstop fallback.
        let smcSl: number | null = null;
        let smcTp: number | null = null;
        let slTpModelUsed: string | null = null;
        // 2026-05-10 FIX: gate on FINAL `side` (post-AI), not `biasSide` —
        // previously slTpAgent was called with biasSide=SELL (from BEAR bias)
        // even when AI overrode side=BUY → smcSl/smcTp came back wrong-side and
        // had to be discarded (log spam + wasted LLM call).
        // Use side when both are valid; if side=SKIP fall back to biasSide so
        // the agent still runs for the diagnostic SKIP path.
        const slTpSide: 'BUY' | 'SELL' | null =
          (side === 'BUY' || side === 'SELL') ? side
          : (biasSide === 'BUY' || biasSide === 'SELL') ? biasSide
          : null;
        if (slTpSide && side !== 'SKIP') {
          if (slTpSide !== biasSide) {
            atLog(`   ${upper}: slTpAgent side=${slTpSide} (overrides biasSide=${biasSide}) — preventing wrong-side SL/TP`);
          }
          const skipMidChannelLLM = cfg.adaptive?.v25?.skipMidChannelLLM ?? true;
          const v25State = wallStateMachine.status().find(s => s.symbol === upper);
          const isV25EntryState = v25State && (
            v25State.above.state === 'REACT' || v25State.below.state === 'REACT' ||
            v25State.above.state === 'CONFIRM' || v25State.below.state === 'CONFIRM' ||
            v25State.above.state === 'RETEST' || v25State.below.state === 'RETEST'
          );

          if (skipMidChannelLLM && !isV25EntryState) {
            atLog(`   ${upper}: [V25 Gate] slTpAgent skipped (WallState is not REACT/CONFIRM/RETEST)`);
          } else {
            try {
              // Build rawCandles map from analyses (each TF has .candles injected at load time)
              const rawCandlesForSmc: Record<string, any[]> = {};
              for (const [tf, a] of Object.entries(analyses)) {
              if (a.candles && a.candles.length >= 20) rawCandlesForSmc[tf] = a.candles;
            }
            const slTpRes = await slTpAnalystAgent.analyze(cfg, {
              symbol: upper,
              side: slTpSide,
              entry,
              atr,
              analyses,
              rawCandles: rawCandlesForSmc,
              strategy: selectedStrategy,
              targetRrr,
              enableAiMode: cfg.enableAiMode,
            });
            smcSl = slTpRes.sl;
            smcTp = slTpRes.tp;
            slTpModelUsed = slTpRes.modelUsed || null;
            atLog(`   ${upper}: slTpAgent(${slTpRes.source}) SL=${slTpRes.sl?.toFixed(2)} TP=${slTpRes.tp?.toFixed(2)} conf=${slTpRes.confidence} model=${slTpModelUsed} | ${slTpRes.rationale}`);
            // Note: recordExecution() for slTpAgent is already handled inside
            // dispatcher.ts invoke() — no need to duplicate here.
          } catch (slTpErr: any) {
            atWarn(`   ${upper}: slTpAgent failed (${slTpErr?.message || slTpErr}), using ATR fallback`);
          }
        }
        }

        // --- AI TP/SL Validator: Fallback to ATR if AI values are degenerate ---
        // 2026-05-04 — Enhanced: auto-correct distance values before rejecting.
        // Models like liquid/lfm consistently return SL/TP as distances (e.g. SL=0.44)
        // instead of absolute prices (e.g. SL=4605.63). Instead of rejecting and
        // falling back to ATR, try to convert: absolute = entry ± distance.
        const minTpDistanceForAi = (selectedStrategy === 'SCALPING' || selectedStrategy === 'SMC_FVG_SCALP') ? 3.0 : 5.0;
        const minSlDistanceForAi = 1.5;
        // 2026-05-04 Fix #6: Don't auto-correct distances > 10% of entry (hallucinated
        // multiplications like TP=199126 ≈ entry×2.5). Adding these to entry makes
        // the value even worse (278781). Only auto-correct small, plausible distances.
        const maxAutoCorrectDist = entry * 0.10; // 10% of entry price
        // Determine the pipeline role for penalty tracking
        const aiPenaltyRole: any = aiDecision?._source === 'llm' ? 'executionTrader' : 'reasoning';

        let aiTpRejected = false;
        let aiSlRejected = false;

        if (aiDecision?.tp && side !== 'SKIP') {
          const aiTpPctFromEntry = (aiDecision.tp / entry) * 100;
          const aiTpIsDistance = aiTpPctFromEntry < 0.1 || aiTpPctFromEntry > 200;

          // Auto-correct: if TP looks like a small distance, convert to absolute price
          if (aiTpIsDistance && Math.abs(aiDecision.tp) > 0.001 && Math.abs(aiDecision.tp) <= maxAutoCorrectDist) {
            const corrected = side === 'BUY' ? entry + Math.abs(aiDecision.tp)
                            : entry - Math.abs(aiDecision.tp);
            const corrDist = Math.abs(corrected - entry);
            const corrWrongSide = (side === 'BUY' && corrected <= entry) || (side === 'SELL' && corrected >= entry);
            if (corrDist >= minTpDistanceForAi && !corrWrongSide) {
              atLog(`   ${upper}: AI TP=${aiDecision.tp} auto-corrected from distance to price → TP=${corrected.toFixed(2)}`);
              aiDecision.tp = corrected;
              if (aiDecision.modelUsed) {
                modelRankerService.recordContentFailure(aiDecision.modelUsed, 'openrouter', aiPenaltyRole);
              }
            }
          }

          // Standard validation after potential auto-correction
          const aiTpDist = Math.abs(aiDecision.tp - entry);
          const aiTpWrongSide = (side === 'BUY' && aiDecision.tp <= entry) || (side === 'SELL' && aiDecision.tp >= entry);
          const aiTpPctFinal = (aiDecision.tp / entry) * 100;
          const aiTpStillDistance = aiTpPctFinal < 0.1 || aiTpPctFinal > 200;
          if (aiTpDist < minTpDistanceForAi || aiTpWrongSide || aiTpStillDistance) {
            atWarn(`   ${upper}: AI TP=${aiDecision.tp} rejected (dist=${aiTpDist.toFixed(4)}, pct=${aiTpPctFinal.toFixed(1)}%, wrongSide=${aiTpWrongSide}, isDistance=${aiTpStillDistance}) → fallback to ATR-based TP`);
            aiTpRejected = true;
            if (aiDecision.modelUsed && aiTpStillDistance) {
              modelRankerService.recordContentFailure(aiDecision.modelUsed, 'openrouter', aiPenaltyRole);
              atWarn(`   ${upper}: Model "${aiDecision.modelUsed}" penalized for returning TP as distance (${aiDecision.tp}) instead of price level`);
            }
          }
        }

        if (aiDecision?.sl && side !== 'SKIP') {
          const aiSlPctFromEntry = (aiDecision.sl / entry) * 100;
          const aiSlIsDistance = aiSlPctFromEntry < 0.1 || aiSlPctFromEntry > 200;

          // Auto-correct: if SL looks like a small distance, convert to absolute price
          if (aiSlIsDistance && Math.abs(aiDecision.sl) > 0.001 && Math.abs(aiDecision.sl) <= maxAutoCorrectDist) {
            const corrected = side === 'BUY' ? entry - Math.abs(aiDecision.sl)
                            : entry + Math.abs(aiDecision.sl);
            const corrDist = Math.abs(corrected - entry);
            const corrWrongSide = (side === 'BUY' && corrected >= entry) || (side === 'SELL' && corrected <= entry);
            if (corrDist >= minSlDistanceForAi && !corrWrongSide) {
              atLog(`   ${upper}: AI SL=${aiDecision.sl} auto-corrected from distance to price → SL=${corrected.toFixed(2)}`);
              aiDecision.sl = corrected;
              if (aiDecision.modelUsed) {
                modelRankerService.recordContentFailure(aiDecision.modelUsed, 'openrouter', aiPenaltyRole);
              }
            }
          }

          // Standard validation after potential auto-correction
          const aiSlDist = Math.abs(aiDecision.sl - entry);
          const aiSlWrongSide = (side === 'BUY' && aiDecision.sl >= entry) || (side === 'SELL' && aiDecision.sl <= entry);
          const aiSlPctFinal = (aiDecision.sl / entry) * 100;
          const aiSlStillDistance = aiSlPctFinal < 0.1 || aiSlPctFinal > 200;
          if (aiSlDist < minSlDistanceForAi || aiSlWrongSide || aiSlStillDistance) {
            atWarn(`   ${upper}: AI SL=${aiDecision.sl} rejected (dist=${aiSlDist.toFixed(4)}, pct=${aiSlPctFinal.toFixed(1)}%, wrongSide=${aiSlWrongSide}, isDistance=${aiSlStillDistance}) → fallback to ATR-based SL`);
            aiSlRejected = true;
            if (aiDecision.modelUsed && aiSlStillDistance) {
              modelRankerService.recordContentFailure(aiDecision.modelUsed, 'openrouter', aiPenaltyRole);
              atWarn(`   ${upper}: Model "${aiDecision.modelUsed}" penalized for returning SL as distance (${aiDecision.sl}) instead of price level`);
            }
          }
        }

        // Use validated AI SL/TP or SMC-structural or ATR fallback
        // Priority: 
        // 1. Explicit LLM ExecutionTrader level (if _source === 'llm')
        // 2. slTpAgent SMC refined level (validSmcSl)
        // 3. Crude EA Fallback level (if _source !== 'llm')
        // 4. ATR backstop
        const isLlmSource = aiDecision?._source === 'llm';
        const aiSlIfLlm = isLlmSource && !aiSlRejected ? aiDecision.sl : null;
        const aiTpIfLlm = isLlmSource && !aiTpRejected ? aiDecision.tp : null;
        
        const aiSlIfFallback = !isLlmSource && !aiSlRejected ? aiDecision?.sl : null;
        const aiTpIfFallback = !isLlmSource && !aiTpRejected ? aiDecision?.tp : null;

        // 2026-05-04 Fix #4 (CRITICAL): slTpAgent was called with `biasSide` which
        // may differ from the AI-determined `side`. E.g. bias=BEAR → slTpAgent
        // computes SL/TP for SELL, but AI overrides to BUY → smcSl is ABOVE entry
        // (correct for SELL, wrong for BUY). Must null out wrong-side SMC values.
        let validSmcSl = smcSl;
        let validSmcTp = smcTp;
        if (side === 'BUY' || side === 'SELL') {
          if (validSmcSl !== null) {
            const smcSlWrongSide = (side === 'BUY' && validSmcSl >= entry) || (side === 'SELL' && validSmcSl <= entry);
            if (smcSlWrongSide) {
              atWarn(`   ${upper}: smcSl=${validSmcSl?.toFixed(2)} on wrong side for ${side} (entry=${entry.toFixed(2)}) — discarded`);
              validSmcSl = null;
            }
          }
          if (validSmcTp !== null) {
            const smcTpWrongSide = (side === 'BUY' && validSmcTp <= entry) || (side === 'SELL' && validSmcTp >= entry);
            if (smcTpWrongSide) {
              atWarn(`   ${upper}: smcTp=${validSmcTp?.toFixed(2)} on wrong side for ${side} (entry=${entry.toFixed(2)}) — discarded`);
              validSmcTp = null;
            }
          }
        }

        // Also validate rawSl/rawTp against final side (same biasSide mismatch)
        let validRawSl = rawSl;
        let validRawTp = rawTp;
        if (side === 'BUY' || side === 'SELL') {
          if (validRawSl !== null && ((side === 'BUY' && validRawSl >= entry) || (side === 'SELL' && validRawSl <= entry))) {
            // Recompute ATR fallback for correct side
            validRawSl = side === 'BUY' ? entry - rawRiskDistance : entry + rawRiskDistance;
          }
          if (validRawTp !== null && ((side === 'BUY' && validRawTp <= entry) || (side === 'SELL' && validRawTp >= entry))) {
            validRawTp = side === 'BUY' ? entry + rawRiskDistance * targetRrr : entry - rawRiskDistance * targetRrr;
          }
        }

        let sl = roundToTick(aiSlIfLlm || validSmcSl || aiSlIfFallback || validRawSl, tick);
        let tp = roundToTick(aiTpIfLlm || validSmcTp || aiTpIfFallback || validRawTp, tick);
        const entryRounded = roundToTick(entry, tick) ?? entry;

        // --- P1.4: Dynamic SL Buffer (prevent 10016 Invalid Stops) ---
        // Ensure SL is at least minSlBuffer away from entry, and outside any active FVG zone.
        // Uses: max(ATR*0.25, spread*3, 50 ticks, broker stopsLevel) as minimum distance.
        if ((cfg.adaptive?.enableFvgSlBuffer ?? true) && sl !== null && side !== 'SKIP') {
            const stopsLevel = symbolInfo?.stopsLevel || 0;
            const minStopsTick = stopsLevel * point;
            const atrBuffer = atr * 0.25;
            const minSlBuffer = Math.max(atrBuffer, spreadInPrice * 3, point * 50, minStopsTick);
            const currentSlDist = Math.abs(entryRounded - sl);

            // Check if SL is inside an active FVG → if so, push it outside
            // 2026-05-04 FIX: Validate pushed SL stays on correct side of entry.
            // Previously, if FVG was entirely above entry (e.g. BUY entry=79825,
            // FVG=80021-80211), pushing SL to fvg.bottom caused SL > entry → wrong
            // side → sanity check collapsed SL to entry-tick → distance ≈ 0 → SKIP.
            try {
                const m15CandlesForFvg = allCandles.get(upper) || [];
                if (m15CandlesForFvg.length >= 20) {
                    const activeFvgs = getActiveFVGs(m15CandlesForFvg);
                    for (const fvg of activeFvgs) {
                        if (sl !== null && sl >= fvg.bottom && sl <= fvg.top) {
                            // SL is inside an FVG → push it outside
                            const bufferTick = tick * 5; // 5 ticks buffer outside FVG
                            const preFvgSl = sl;
                            if (side === 'BUY') {
                                const candidate = roundToTick(fvg.bottom - bufferTick, tick);
                                // Only use the pushed SL if it's still BELOW entry (correct side for BUY)
                                if (candidate !== null && candidate < entryRounded) {
                                    sl = candidate;
                                } else {
                                    // FVG is above entry — this FVG is irrelevant for BUY SL
                                    atLog(`   ${upper}: P1.4 FVG (${fvg.bottom.toFixed(2)}-${fvg.top.toFixed(2)}) is above entry — skipping SL push`);
                                    continue; // try next FVG
                                }
                            } else {
                                const candidate = roundToTick(fvg.top + bufferTick, tick);
                                // Only use the pushed SL if it's still ABOVE entry (correct side for SELL)
                                if (candidate !== null && candidate > entryRounded) {
                                    sl = candidate;
                                } else {
                                    // FVG is below entry — this FVG is irrelevant for SELL SL
                                    atLog(`   ${upper}: P1.4 FVG (${fvg.bottom.toFixed(2)}-${fvg.top.toFixed(2)}) is below entry — skipping SL push`);
                                    continue; // try next FVG
                                }
                            }
                            atLog(`   ${upper}: P1.4 SL moved outside FVG (${fvg.bottom.toFixed(2)}-${fvg.top.toFixed(2)}) → SL=${sl}`);
                            slBufferActivationsTotal.inc({ symbol: upper, trigger: 'fvg_overlap' });
                            break; // only fix the first overlapping FVG
                        }
                    }
                }
            } catch { /* ignore FVG check errors */ }

            // Enforce minimum distance — recalculate after FVG adjustments
            const updatedSlDist = sl !== null ? Math.abs(entryRounded - sl) : currentSlDist;
            if (sl !== null && updatedSlDist < minSlBuffer) {
                if (side === 'BUY') {
                    sl = roundToTick(entryRounded - minSlBuffer, tick);
                } else if (side === 'SELL') {
                    sl = roundToTick(entryRounded + minSlBuffer, tick);
                }
                atLog(`   ${upper}: P1.4 SL buffered to min distance ${minSlBuffer.toFixed(2)} → SL=${sl}`);
                slBufferActivationsTotal.inc({ symbol: upper, trigger: 'min_distance' });
            }
        }

        // 2026-04-26 — TP rescue. P1.4 SL buffer can widen the SL distance
        // beyond what the AI's TP was sized for, dropping RRR below threshold
        // and triggering the Hard Risk Gate even though the trade is still
        // sound. Re-extend TP to keep the original RRR target whenever SL moved
        // away from entry. Only runs when both SL & TP are set and side is BUY/SELL.
        if (side !== 'SKIP' && sl !== null && tp !== null) {
          const slDist = Math.abs(entryRounded - sl);
          const curTpDist = Math.abs(tp - entryRounded);
          const requiredTpDist = slDist * targetRrr;
          if (slDist > 0 && curTpDist + 1e-9 < requiredTpDist) {
            const oldTp = tp;
            // 2026-05-01 — add 1 tick buffer to compensate for roundToTick
            // rounding loss.  Without this, RRR can land at 1.4999... and
            // trip the Hard Risk Gate even though the trade was intended at
            // exactly targetRrr.
            tp = side === 'BUY'
              ? roundToTick(entryRounded + requiredTpDist + tick, tick)
              : roundToTick(entryRounded - requiredTpDist - tick, tick);
            atLog(`   ${upper}: TP rescue after SL buffer — ${oldTp} → ${tp} (slDist=${slDist.toFixed(2)}, target RRR ${targetRrr.toFixed(2)})`);
          }
        }

        // Sanity check: after rounding, ensure SL/TP remain on the correct side.
        // If the rounded values collapsed onto entry (very thin ATR), bump them
        // one tick away so the order is not rejected for zero stop distance.
        if (side === 'BUY' && sl !== null && sl >= entryRounded) sl = entryRounded - tick;
        if (side === 'SELL' && sl !== null && sl <= entryRounded) sl = entryRounded + tick;
        if (side === 'BUY' && tp !== null && tp <= entryRounded) tp = entryRounded + tick;
        if (side === 'SELL' && tp !== null && tp >= entryRounded) tp = entryRounded - tick;

        const isScalpBracket =
          selectedStrategy === 'SCALPING' ||
          selectedStrategy === 'MEAN_REVERSION' ||
          selectedStrategy === 'SMC_FVG_SCALP' ||
          selectedStrategy.toUpperCase().includes('SCALP');

        // V25 hybrid direct can inherit V24's wide M15/FVG brackets. That makes
        // scalp entries open correctly but forces TP/SL far from the active wall.
        // Compact only when the risk is clearly oversized; hard risk gates still
        // validate the adjusted bracket below.
        if (
          side !== 'SKIP' &&
          sl !== null &&
          tp !== null &&
          isScalpBracket &&
          cfg.adaptive?.v25?.enableV25Only === true &&
          cfg.adaptive?.v25?.compactScalpBracket !== false
        ) {
          const currentRisk = Math.abs(entryRounded - sl);
          const stopsLevel = symbolInfo?.stopsLevel || 0;
          const minStopsTick = stopsLevel * point;
          const isXau = upper.includes('XAU');
          const minRisk = Math.max(
            spreadInPrice * 4,
            point * 100,
            minStopsTick,
            isXau ? (cfg.adaptive?.v25?.compactScalpMinRiskXAU ?? 4.5) : entryRounded * 0.0005,
          );
          const maxRisk = Math.max(
            minRisk,
            atr * (cfg.adaptive?.v25?.compactScalpMaxRiskAtrMul ?? 0.12),
          );

          if (currentRisk > maxRisk * 1.25) {
            let desiredRisk = maxRisk;
            const wallBuffer = Math.max(spreadInPrice * 3, point * 80, isXau ? 0.8 : entryRounded * 0.0002);
            const compactPriceMap = indicatorPipeline.getPriceMap(upper);
            const anchorWall = compactPriceMap
              ? side === 'BUY'
                ? compactPriceMap.supportWalls
                    .filter((w) => w.price < entryRounded && w.confluenceStars >= 3)
                    .sort((a, b) => b.price - a.price)[0]
                : compactPriceMap.resistanceWalls
                    .filter((w) => w.price > entryRounded && w.confluenceStars >= 3)
                    .sort((a, b) => a.price - b.price)[0]
              : null;

            if (anchorWall) {
              const wallSl = side === 'BUY'
                ? anchorWall.price - wallBuffer
                : anchorWall.price + wallBuffer;
              const wallRisk = Math.abs(entryRounded - wallSl);
              desiredRisk = Math.max(minRisk, Math.min(maxRisk, wallRisk));
            }

            const compactRrr = Math.min(targetRrr, cfg.adaptive?.v25?.compactScalpRrr ?? 1.25);
            let compactSl = side === 'BUY'
              ? roundToTick(entryRounded - desiredRisk, tick)
              : roundToTick(entryRounded + desiredRisk, tick);
            let compactTp = side === 'BUY'
              ? roundToTick(entryRounded + desiredRisk * compactRrr, tick)
              : roundToTick(entryRounded - desiredRisk * compactRrr, tick);

            const oppositeWall = compactPriceMap
              ? side === 'BUY'
                ? compactPriceMap.resistanceWalls
                    .filter((w) => w.price > entryRounded)
                    .sort((a, b) => a.price - b.price)[0]
                : compactPriceMap.supportWalls
                    .filter((w) => w.price < entryRounded)
                    .sort((a, b) => b.price - a.price)[0]
              : null;
            if (oppositeWall && compactTp !== null && compactSl !== null) {
              const wallTp = side === 'BUY'
                ? roundToTick(oppositeWall.price - wallBuffer, tick)
                : roundToTick(oppositeWall.price + wallBuffer, tick);
              if (wallTp !== null) {
                const wallRrr = Math.abs((wallTp - entryRounded) / (entryRounded - compactSl));
                if (wallRrr >= 1.0) {
                  compactTp = side === 'BUY'
                    ? Math.min(compactTp, wallTp)
                    : Math.max(compactTp, wallTp);
                }
              }
            }

            const compactRisk = compactSl !== null ? Math.abs(entryRounded - compactSl) : 0;
            const compactRrrActual = compactSl !== null && compactTp !== null && compactRisk > 0
              ? Math.abs((compactTp - entryRounded) / (entryRounded - compactSl))
              : 0;
            const compactCorrectSide =
              compactSl !== null &&
              compactTp !== null &&
              ((side === 'BUY' && compactSl < entryRounded && compactTp > entryRounded) ||
               (side === 'SELL' && compactSl > entryRounded && compactTp < entryRounded));

            if (compactCorrectSide && compactRisk >= minRisk && compactRisk < currentRisk && compactRrrActual >= 1.0) {
              atLog(`   ${upper}: [V25] compact scalp bracket — SL ${sl} -> ${compactSl}, TP ${tp} -> ${compactTp} (risk ${currentRisk.toFixed(2)} -> ${compactRisk.toFixed(2)}, RRR=${compactRrrActual.toFixed(2)})`);
              sl = compactSl;
              tp = compactTp;
            }
          }
        }

        let tpCapRiskReason: string | null = null;

        // --- V24.3 Global PriceMap TP Cap ---
        // Ensure that the final TP (after TP rescue or AI hallucination) is realistic
        // by capping it at the nearest 3★+ wall. If this cap ruins the RRR, the Hard
        // Risk Gate below will correctly SKIP the trade.
        // 2026-05-10 V25.2 — when V25 is active (`enableV25Only`), only cap TP when
        // the obstacle is ≥3★. V24's 1★/2★ obstacle caps killed RRR (e.g. 81588 →
        // 80793 because of a single 1★ wall) which directly violates V25 R3:
        // "TP = under/over opposite wall — don't shrink for 1★ obstacles".
        if (side !== 'SKIP' && tp !== null) {
          try {
            const priceMap = indicatorPipeline.getPriceMap(upper);
            if (priceMap) {
              const direction = side === 'BUY' ? 'UP' : 'DOWN';
              const v25Only = cfg.adaptive?.v25?.enableV25Only === true;
              const minCapStars = v25Only ? 4 : 3;

              const path = analyzePath(priceMap, entryRounded, direction, undefined, minCapStars);
              const suggestedTp = path.suggestedTP;
              const overshoot = side === 'BUY' ? tp > suggestedTp : tp < suggestedTp;
              const obstacleStars = Math.max(path.biggestObstacle?.wall.confluenceStars ?? 0, path.targetWallStars);

              if (overshoot && obstacleStars < minCapStars) {
                atLog(`   ${upper}: Global TP cap skipped — soft ${obstacleStars}★ obstacle while ${v25Only ? 'V25-only' : 'standard'} mode requires ${minCapStars}★+`);
              }
              if (overshoot && obstacleStars >= minCapStars) {
                const oldTp = tp;
                tp = suggestedTp;
                
                // Identify the wall that caused the cap (it is the first strong wall)
                const directionText = side === 'BUY' ? 'UP' : 'DOWN';
                const firstStrongWall = directionText === 'UP'
                  ? priceMap.resistanceWalls.filter(w => w.price > entryRounded && w.confluenceStars >= 3).sort((a, b) => a.price - b.price)[0]
                  : priceMap.supportWalls.filter(w => w.price < entryRounded && w.confluenceStars >= 3).sort((a, b) => b.price - a.price)[0];
                
                const obstaclesNote = firstStrongWall
                  ? ` (obstacle ${firstStrongWall.confluenceStars}★ @ ${round2(firstStrongWall.price)})`
                  : '';
                atLog(`   ${upper}: Global TP capped by PriceMap: ${oldTp.toFixed(2)} → ${tp.toFixed(2)}${obstaclesNote} — keeping realistic targets`);
                const postCapRisk = sl !== null ? Math.abs(entryRounded - sl) : 0;
                const postCapRrr = postCapRisk > 0 ? Math.abs((tp - entryRounded) / (entryRounded - sl!)) : 0;
                const minAfterCap = selectedStrategy.toUpperCase().includes('SCALP') || selectedStrategy.toUpperCase().includes('SMC') || selectedStrategy === 'MEAN_REVERSION'
                  ? 1.0
                  : cfg.minRRR;
                if (postCapRrr < minAfterCap) {
                  tpCapRiskReason = `TP_CAP_RRR: PriceMap cap reduced RRR to ${postCapRrr.toFixed(2)} < ${minAfterCap} (oldTP=${oldTp.toFixed(2)} cappedTP=${tp.toFixed(2)}${obstaclesNote})`;
                }
              }
            }
          } catch (e) { /* ignore */ }
        }

        const riskDistance = sl !== null ? Math.abs(entryRounded - sl) : rawRiskDistance;
        const actualRrr = (sl !== null && tp !== null && riskDistance > 0)
          ? Math.abs((tp - entryRounded) / (entryRounded - sl))
          : targetRrr;
        const buildDecisionLogFields = (
          loggedSide: 'BUY' | 'SELL' | 'SKIP',
          loggedExecuted: boolean,
          loggedRiskGate: string,
          orderLog: Record<string, unknown> = {},
        ): Partial<CycleDecision> => {
          const blockCategory = classifyDecisionBlockCategory(loggedRiskGate, loggedSide, loggedExecuted);
          const analysisConfluence = Number(analysis?.confluence);
          const analysisFitness = Number(analysis?.fitness);
          const deterministicAction = String(detDecision?.action || '').toUpperCase();
          const candidateSide = deterministicAction === 'BUY' || deterministicAction === 'SELL'
            ? deterministicAction
            : loggedSide === 'BUY' || loggedSide === 'SELL'
            ? loggedSide
            : null;
          const candidateRrr = Number.isFinite(actualRrr) ? round2(actualRrr) : null;
          const candidateRiskDistance = Number.isFinite(riskDistance) ? round2(riskDistance) : null;
          const decisionType: CycleDecision['decisionType'] = loggedExecuted
            ? 'ORDER_EXECUTED'
            : blockCategory
            ? blockCategory === 'NO_DETERMINISTIC_SIGNAL' ? 'NO_SIGNAL' : 'ORDER_BLOCKED'
            : loggedSide === 'SKIP'
            ? blockCategory === 'NO_DETERMINISTIC_SIGNAL' ? 'NO_SIGNAL' : 'ORDER_BLOCKED'
            : cfg.enableLiveTrading ? 'ORDER_ALLOWED' : 'PAPER';
          return {
            decisionType,
            blockCategory,
            ...(Number.isFinite(analysisConfluence) ? { confluence: round1(analysisConfluence) } : {}),
            ...(Number.isFinite(analysisFitness) ? { fitness: round1(analysisFitness) } : {}),
            deterministic: compactDeterministicForLog(detDecision),
            analysisSnapshot: {
              ...compactAnalysisForLog(analysis),
              activeTF,
              configuredTimeframe: cfg.timeframe,
              mtf: compactMtfForLog(analyses),
              correlation: corrAnalysis,
              account: {
                balance: account.balance,
                equity: account.equity,
                freeMargin: account.freeMargin,
                openPositions: account.openPositions,
              },
              positions: {
                total: positions.length,
                symbol: symbolPositions.length,
                perSymbolCap,
                defenseSymbolCap,
                cluster: {
                  totalProfit: round2(cluster.totalProfit),
                  buyVolume: cluster.buyVolume,
                  sellVolume: cluster.sellVolume,
                  netSide: cluster.netSide,
                  totalVolume: cluster.totalVolume,
                },
                totalHeatR: round2(totalHeatR),
                lastPositionR: round2(lastPositionR),
                isLastSafe,
              },
            },
            signals: {
              primary: Array.isArray(analysis.signals) ? analysis.signals : [],
              eaSignals: (eaSignals || []).map((s: any) => ({
                side: s.side,
                strategy: s.strategy,
                confidence: s.confidence,
                confluenceStars: s.confluenceStars,
                sl: s.sl,
                tp: s.tp,
              })),
            },
            gateTrace: gateTrace.map((step) => ({ ...step })),
            marketSnapshotLog: {
              price: entryRounded,
              lastClose: last.c,
              atr,
              spread: currentSpread,
              spreadInPrice,
              point,
              targetRrr: round2(targetRrr),
              actualRrr: loggedSide === 'SKIP' ? null : candidateRrr,
              riskDistance: loggedSide === 'SKIP' ? null : candidateRiskDistance,
              candidateSide,
              candidateRrr,
              candidateRiskDistance,
              tradePlanStatus: loggedExecuted ? 'EXECUTED' : loggedSide === 'SKIP' ? 'BLOCKED' : cfg.enableLiveTrading ? 'ALLOWED' : 'PAPER',
              selectedStrategy,
              finalSide: loggedSide,
            },
            order: orderLog,
          };
        };
        const enrichDecisionForLog = (decision: CycleDecision, orderLog: Record<string, unknown> = {}): CycleDecision => ({
          ...decision,
          ...buildDecisionLogFields(decision.side, decision.executed, decision.riskGate, orderLog),
        });

        if (riskDistance <= 0 || !isFinite(riskDistance)) {
          // Guard: never send an order with zero/invalid risk.
          atWarn(`   ${upper}: invalid riskDistance=${riskDistance} (atr=${atr})  SKIP`);
          pushDecisionTrace(gateTrace, 'hard_risk', 'BLOCK', `invalid risk distance (${riskDistance})`, { atr });
          decisions.push(enrichDecisionForLog(this.buildSkipDecision(upper, analysis.regime, analysis.bias, analysis.confluence, selectedStrategy, `invalid risk distance (${riskDistance})`, 'invalid risk distance', `  (${riskDistance.toFixed(4)})`)));
          continue;
        }

        let volume = this.computeVolume(account, riskDistance, upper, selectedStrategy);
        
        // --- V24 Sunset Check ---
        // 2026-05-11 V25.3 — In VOLATILE_BREAKOUT regime, V25 wall-touch logic
        // cannot produce breakout continuation entries (it only does reversals).
        // Allow EA direct execution in breakout to prevent 3-way deadlock:
        //   EA wants BUY → enableV25Only blocks → V25 only sees SELL → Bias Gate blocks.
        //
        // V25.3.1 FIX: But we must NOT buy right into strong resistance (or sell into
        // strong support). If there's a ≥3★ opposing wall within 0.5×ATR, SKIP — the
        // market is testing resistance and may reject (e.g., triple-top at 81474).
        const isBreakoutRegime = (analysis.regime || '').includes('BREAKOUT');
        const v25OnlyEnabled = cfg.adaptive?.v25?.enableV25Only === true;
        const hybridDirectEnabled = cfg.adaptive?.v25?.allowV24DirectWhenV25Aligned !== false;
        let v25HybridDirect = { allowed: false, reason: 'hybrid direct disabled' };
        if (v25OnlyEnabled && hybridDirectEnabled && side !== 'SKIP' && !isBreakoutRegime) {
          v25HybridDirect = v25AlignedDirectEntry({
            symbol: upper,
            side: side as TradeSide,
            minStars: cfg.adaptive?.v25?.hybridDirectMinWallStars ?? 3,
            maxStateAgeMs: cfg.adaptive?.v25?.hybridDirectMaxStateAgeMs ?? 90_000,
            entryPrice: entryRounded,
            atr: analysis.atr ?? 0,
            allowApproach: cfg.adaptive?.v25?.allowV24DirectOnV25Approach !== false,
            approachMinStars: cfg.adaptive?.v25?.hybridApproachMinWallStars ?? 4,
            approachMaxAtrMul: cfg.adaptive?.v25?.hybridApproachMaxAtrMul ?? 0.18,
            confluence: analysis.confluence,
            approachMinConfluence: cfg.adaptive?.v25?.hybridApproachMinConfluence ?? 62,
          });
        }

        let v25TrendFollowDirect = { allowed: false, reason: 'trend-follow direct disabled' };
        if (
          v25OnlyEnabled &&
          side !== 'SKIP' &&
          !isBreakoutRegime &&
          !v25HybridDirect.allowed &&
          cfg.adaptive?.v25?.allowV24TrendFollowDirectWhenV25Stale === true &&
          /stale age/i.test(v25HybridDirect.reason)
        ) {
          const trendMinConfluence = cfg.adaptive?.v25?.hybridTrendFollowMinConfluence ?? 80;
          const isTrendFollowIntent =
            selectedStrategy === 'TREND_FOLLOW' ||
            selectedStrategy.includes('CONTINUATION') ||
            hasContinuationIntent(selectedStrategy, aiDecision?.rationale, detDecision.rationale);
          const htfAligned = htfBiasAgrees(side as TradeSide, analyses);
          const trendPriceMap = indicatorPipeline.getPriceMap(upper);
          const maxOpposingDist = (analysis.atr ?? atr) * (cfg.adaptive?.v25?.hybridTrendFollowMaxOpposingWallAtrMul ?? 0.5);
          const opposingWalls = trendPriceMap
            ? side === 'BUY'
              ? trendPriceMap.resistanceWalls
              : trendPriceMap.supportWalls
            : [];
          const nearestOpposingWall = opposingWalls
            .filter((wall: any) => side === 'BUY' ? wall.price > entryRounded : wall.price < entryRounded)
            .map((wall: any) => ({ wall, distance: Math.abs(wall.price - entryRounded) }))
            .sort((a: any, b: any) => a.distance - b.distance)[0];
          const strongOpposingWall =
            nearestOpposingWall &&
            nearestOpposingWall.wall.confluenceStars >= 3 &&
            nearestOpposingWall.distance <= maxOpposingDist;

          if (!trendPriceMap) {
            v25TrendFollowDirect = { allowed: false, reason: 'trend-follow direct needs PriceMap to verify opposing walls' };
          } else if (!isTrendFollowIntent) {
            v25TrendFollowDirect = { allowed: false, reason: `not a trend-follow/continuation intent (${selectedStrategy})` };
          } else if (!htfAligned) {
            v25TrendFollowDirect = { allowed: false, reason: 'HTF bias is not aligned with final side' };
          } else if (analysis.confluence < trendMinConfluence) {
            v25TrendFollowDirect = { allowed: false, reason: `confluence ${analysis.confluence.toFixed(1)} < ${trendMinConfluence}` };
          } else if (strongOpposingWall) {
            v25TrendFollowDirect = {
              allowed: false,
              reason: `strong opposing wall ${nearestOpposingWall.wall.confluenceStars}-star @ ${nearestOpposingWall.wall.price.toFixed(2)} within ${nearestOpposingWall.distance.toFixed(2)} <= ${maxOpposingDist.toFixed(2)}`,
            };
          } else {
            v25TrendFollowDirect = {
              allowed: true,
              reason: `V25 stale bypass for HTF-aligned trend-follow (${side}, conf=${analysis.confluence.toFixed(1)}, no strong opposing wall within ${maxOpposingDist.toFixed(2)})`,
            };
          }
        }

        if (v25OnlyEnabled && side !== 'SKIP' && !isBreakoutRegime && !v25HybridDirect.allowed && !v25TrendFollowDirect.allowed) {
            forcedSkipReason = `[V25_ONLY] V24 deterministic plan delegated to V25 pipeline; direct order disabled by enableV25Only (${v25HybridDirect.reason})`;
            atLog(`   ${upper}: [V25] V24 deterministic execution disabled via enableV25Only. Delegating entry to V25 pipeline (${v25HybridDirect.reason}).`);
            pushDecisionTrace(gateTrace, 'v25_only', 'BLOCK', forcedSkipReason, {
              hybridReason: v25HybridDirect.reason,
              trendFollowReason: v25TrendFollowDirect.reason,
            });
            side = 'SKIP';
            // Also override AI decision so it doesn't trigger AI execution block
            aiDecision = null;
        } else if (v25OnlyEnabled && side !== 'SKIP' && !isBreakoutRegime && v25HybridDirect.allowed) {
            atLog(`   ${upper}: [V25] enableV25Only HYBRID PASS — ${v25HybridDirect.reason}; EA direct execution continues.`);
            pushDecisionTrace(gateTrace, 'v25_only', 'ALLOW', v25HybridDirect.reason);
        } else if (v25OnlyEnabled && side !== 'SKIP' && !isBreakoutRegime && v25TrendFollowDirect.allowed) {
            atLog(`   ${upper}: [V25] enableV25Only TREND-FOLLOW PASS - ${v25TrendFollowDirect.reason}; EA direct execution continues.`);
            pushDecisionTrace(gateTrace, 'v25_only', 'ALLOW', v25TrendFollowDirect.reason, {
              hybridReason: v25HybridDirect.reason,
            });
        } else if (v25OnlyEnabled && side !== 'SKIP' && isBreakoutRegime) {
            // Opposition Wall Safety Check: don't BUY into strong resistance or SELL into strong support
            const brkPriceMap = indicatorPipeline.getPriceMap(upper);
            const brkAtr = analysis.atr ?? 100;
            const oppositionMaxDist = brkAtr * 0.5;  // within 0.5×ATR = too close to wall
            const minOppositionStars = 3;
            let oppositionBlocked = false;

            if (brkPriceMap) {
                // For BUY: check resistance walls above entry
                // For SELL: check support walls below entry
                const oppositionWalls = side === 'BUY' ? brkPriceMap.resistanceWalls : brkPriceMap.supportWalls;
                for (const wall of oppositionWalls) {
                    const isOpposing = side === 'BUY' ? wall.price > entryRounded : wall.price < entryRounded;
                    if (!isOpposing) continue;
                    const dist = Math.abs(wall.price - entryRounded);
                    if (dist <= oppositionMaxDist && wall.confluenceStars >= minOppositionStars) {
                        forcedSkipReason = `[V25] Breakout entry BLOCKED — ${side} at ${entryRounded.toFixed(2)} would hit ${wall.confluenceStars}★ ${side === 'BUY' ? 'resistance' : 'support'} @ ${wall.price.toFixed(2)} (within 0.5×ATR)`;
                        atWarn(`   ${upper}: ${forcedSkipReason} (${dist.toFixed(0)} pts vs max ${oppositionMaxDist.toFixed(0)})`);
                        pushDecisionTrace(gateTrace, 'v25_breakout_guard', 'BLOCK', forcedSkipReason, {
                          wallStars: wall.confluenceStars,
                          wallPrice: wall.price,
                          dist: dist,
                          maxDist: oppositionMaxDist
                        });
                        side = 'SKIP';
                        oppositionBlocked = true;
                        break;
                    }
                }
            }
            if (!oppositionBlocked) {
                atLog(`   ${upper}: [V25] enableV25Only BYPASSED — ${analysis.regime} regime, no strong opposing wall nearby. EA direct execution.`);
            }
        }

        // --- HARD RRR & SL GATE [CRITICAL FIX] ---
        if (side !== 'SKIP') {
            const isScalpSMC = selectedStrategy.toUpperCase().includes('SCALP') || 
                               selectedStrategy.toUpperCase().includes('SMC') || 
                               selectedStrategy === 'MEAN_REVERSION';
            
            const minRRR = isScalpSMC ? 1.0 : cfg.minRRR;
            const slDistance = Math.abs(entryRounded - (sl ?? entryRounded));

            // Spread-aware SL Guard
            const minSlPoints = Math.max(0.3, spreadInPrice + (point * 10)); // Min 30 pts or Spread + 10 pts buffer
            const minTpPoints = (selectedStrategy === 'SCALPING' || selectedStrategy === 'SMC_FVG_SCALP') ? 3.0 : 5.0;

            // 2026-04-30 fix — float-safe compare. Prior `<` rejected RRR=1.50
            // when the underlying value was 1.4999999... causing legitimate
            // BREAKOUT setups to be SKIP'd at the gate.
            const RRR_EPS = 1e-9;
            if (actualRrr < (minRRR - RRR_EPS) || (tp !== null && Math.abs(tp - entryRounded) < minTpPoints) || slDistance < minSlPoints) {
                const reason = slDistance < minSlPoints
                  ? `SL distance ${slDistance.toFixed(4)} < min ${minSlPoints.toFixed(4)} (Spread: ${currentSpread}pts)`
                  : tpCapRiskReason
                  ? tpCapRiskReason
                  : `RRR ${actualRrr.toFixed(2)} < minRRR ${minRRR} (strategy=${selectedStrategy}) or TP too close`;
                atWarn(`   ${upper}: Hard Risk Gate triggered (${reason}) -> SKIP`);
                pushDecisionTrace(gateTrace, 'hard_risk', 'BLOCK', reason, {
                  entry: entryRounded,
                  side,
                  sl,
                  tp,
                  actualRrr,
                  minRRR,
                });
                decisions.push(enrichDecisionForLog(this.buildSkipDecision(upper, analysis.regime, analysis.bias, analysis.confluence, selectedStrategy, `[RISK GUARD] ${reason} | Entry:${entryRounded} AI intended ${side} SL:${sl} TP:${tp}`, 'risk_params_gate', `  (${reason})`)));
                continue;
            }
        }

        const managementPlan = tradeManagementService.planMarketAwareManagement(cfg, account, cluster, analysis, aiDecision, volume, playbookScores, positions.length, this.openJournal);

        // Heat Guard: suppress defense override when cluster is already under high heat.
        // Even if management says "scale_in / defense", we shouldn't keep adding to a
        // losing cluster when heat is already dangerous (>= 1.0R total drawdown).
        const clusterHeatR = this.openJournal
          .filter((j) => j.outcome === 'OPEN' && j.symbol === upper)
          .reduce((sum, j) => {
            const pos = positions.find((p) => p.ticket === j.mt5Ticket);
            if (!pos) return sum;
            const r = tradeManagementService.positionRiskR(pos, j);
            return sum + Math.max(0, -r); // only count negative R
          }, 0);
        const maxHeatForDefenseOverride = (cfg.adaptive?.maxHeatForScaleIn ?? 1.5) * 0.67; // stricter than scale-in
        // P2.4: AI confidence escape hatch  high-confidence defense (e.g. protective hedge)
        // is allowed to bypass the heat suppression. Without this, the system blocks
        // exactly the moment it most needs to hedge (failure mode M5 from 2026-04-24 log).
        // 2026-04-30 — lowered default 75 -> 70 after observing AI conf=74 fail
        // by exactly 1 unit while heat sat at 1.43R for multiple cycles.
        const hedgeOverrideMinConf = (cfg.adaptive?.hedgeOverrideMinConfidence ?? 70);
        const aiConfidenceForHedge = Number(aiDecision?.confidence ?? 0);
        const aiAllowsHeatedHedge = (managementPlan?.mode === 'HEDGE' || managementPlan?.isAiDefense === true)
          && aiConfidenceForHedge >= hedgeOverrideMinConf;

        // 2026-04-30 — Deterministic escape hatch: when the AI pipeline either
        // failed (confidence=0) OR was VETO'd by Risk Officer (confidence stays
        // 0/low because Execution Trader never ran), but our own MTF analysis
        // still endorses a defensive plan, we allow the override.
        // Threshold lowered 70 -> 65 after observing Confluence=68.2 narrowly
        // miss the gate three cycles in a row (3393–3395) while heat was 1.30R+.
        const detOverrideMinConfluence = (cfg.adaptive?.deterministicHedgeMinConfluence ?? 65);
        const detAllowsHeatedHedge =
          aiConfidenceForHedge < hedgeOverrideMinConf && // AI either failed or below threshold
          (managementPlan?.mode === 'HEDGE' || managementPlan?.isAiDefense === true) &&
          (analysis?.confluence ?? 0) >= detOverrideMinConfluence;

        const heatBlocksOverride = clusterHeatR >= maxHeatForDefenseOverride
          && !aiAllowsHeatedHedge
          && !detAllowsHeatedHedge;

        // P2: Absolute Risk Caps (Anti-AI Hallucination)
        const ABSOLUTE_MAX_POSITIONS = this.config.risk.maxOpenPositions * 3;
        const absoluteCapReached = positions.length >= ABSOLUTE_MAX_POSITIONS;

        const effectiveOverLimitDefense = managementPlan?.allowOverLimitDefense === true && !heatBlocksOverride && !absoluteCapReached;

        if (absoluteCapReached && managementPlan?.allowOverLimitDefense) {
             atWarn(`   ${upper}: ABSOLUTE RISK CAP REACHED (${positions.length} >= ${ABSOLUTE_MAX_POSITIONS}). Defense override permanently blocked to prevent AI hallucination.`);
        } else if (heatBlocksOverride && managementPlan?.allowOverLimitDefense) {
          atWarn(`   ${upper}: Defense override SUPPRESSED  cluster heat ${clusterHeatR.toFixed(2)}R >= ${maxHeatForDefenseOverride.toFixed(2)}R threshold (AI conf=${aiConfidenceForHedge} < ${hedgeOverrideMinConf})`);
        } else if (aiAllowsHeatedHedge && clusterHeatR >= maxHeatForDefenseOverride && !absoluteCapReached) {
          atLog(`   ${upper}: Defense override ALLOWED despite heat ${clusterHeatR.toFixed(2)}R  AI confidence ${aiConfidenceForHedge}  ${hedgeOverrideMinConf}`);
        } else if (detAllowsHeatedHedge && clusterHeatR >= maxHeatForDefenseOverride && !absoluteCapReached) {
          atLog(`   ${upper}: Defense override ALLOWED on deterministic confluence ${(analysis?.confluence ?? 0).toFixed(1)} (AI unavailable, heat ${clusterHeatR.toFixed(2)}R)`);
        }

        let gate = gateTrade(this.config, account, positions, upper, volume, analysis, riskDistance, currentSpread, {
          allowOverLimitDefense: effectiveOverLimitDefense,
          correlationResult: corrAnalysis,
          side: side
        });

        // --- P2.3: Close-Weakest-Loser to make room for a high-quality setup ---
        // If the only reason we'd skip is "max open positions" AND the new setup
        // is high-quality (confluence  threshold) AND the cooldown has elapsed,
        // close the worst loser of THIS symbol's cluster so the new trade can run.
        const closeWeakestEnabled = (this.config.adaptive?.enableCloseWeakest ?? true);
        const cwMinConf = (this.config.adaptive?.closeWeakestMinConfluence ?? 70);
        const cwCooldownMs = (this.config.adaptive?.closeWeakestCooldownMs ?? 3_600_000); // 1 hr default
        if (
          closeWeakestEnabled &&
          !gate.allowed &&
          /max open positions/i.test(gate.reason) &&
          (side === 'BUY' || side === 'SELL') &&
          analysis.confluence >= cwMinConf &&
          cluster.positions.length > 0
        ) {
          const lastClose = this.closeWeakestLastAt.get(upper) ?? 0;
          if (Date.now() - lastClose >= cwCooldownMs) {
            // Pick weakest position of THIS symbol  most negative profit
            const weakest = [...cluster.positions]
              .filter((p) => p.profit < 0)
              .sort((a, b) => a.profit - b.profit)[0];
            if (weakest) {
              const closeWeakestPlan: ManagementPlan = {
                mode: 'CLOSE',
                summary: `close-weakest #${weakest.ticket} for new high-quality ${side}`,
                reason: `confluence=${analysis.confluence.toFixed(1)}  ${cwMinConf} but max-positions hit; sacrificing weakest loser (PnL=${round2(weakest.profit)}) to free a slot.`,
                ticket: weakest.ticket,
                closeVolume: weakest.volume,
                allowOverLimitDefense: false,
              };
              const cwResult = await this.executeManagementPlan(upper, closeWeakestPlan, sl, tp, aiDecision);
              managementEventsTotal.inc({ symbol: upper, event: cwResult.executed ? 'close_weakest' : 'close_weakest_failed' });
              if (cwResult.executed) {
                this.closeWeakestLastAt.set(upper, Date.now());
                atLog(`   ${upper}: P2.3 close-weakest #${weakest.ticket} executed  re-gating new ${side}`);
                // Re-fetch positions and re-gate so the new trade can proceed
                const refreshedPositions = await this.fetchPositions();
                positions.length = 0;
                positions.push(...refreshedPositions);
                gate = gateTrade(this.config, account, positions, upper, volume, analysis, riskDistance, currentSpread, {
                  allowOverLimitDefense: effectiveOverLimitDefense,
                  correlationResult: corrAnalysis,
                  side: side,
                });
              } else {
                atWarn(`   ${upper}: P2.3 close-weakest failed: ${cwResult.riskGate}`);
              }
            }
          } else {
            atLog(`   ${upper}: P2.3 close-weakest in cooldown (${Math.round((cwCooldownMs - (Date.now() - lastClose)) / 1000)}s left)`);
          }
        }

        // eslint-disable-next-line no-console
        const finalGateReason = forcedSkipReason
          ?? (side === 'SKIP' && aiDecision?.rationale
          ? (aiDecision.rationale.includes('[DET]') ? aiDecision.rationale.split(']')[1].trim() : aiDecision.rationale)
          : gate.reason);
        const skipRiskGate = forcedSkipReason ?? finalGateReason;
        
        atLog(`   ${upper}: Side=${side} | Entry=${entryRounded} | SL=${sl} | TP=${tp} | Vol=${volume} | RRR=${actualRrr.toFixed(2)} (target ${targetRrr.toFixed(2)}) | Gate=${finalGateReason}`);

        // --- HARD GATE ENFORCEMENT [CRITICAL FIX] ---
        // If the risk gate is not allowed, we MUST skip immediately.
        // Previously, the code only skipped on low confluence/fitness, letting
        // gate-blocked trades proceed to the execution block.
        if (!gate.allowed) {
            atWarn(`   ${upper}: Trade BLOCKED by Risk Gate (${gate.reason}) -> SKIP`);
            pushDecisionTrace(gateTrace, 'risk_gate', 'BLOCK', gate.reason, { volume, riskDistance, currentSpread });
            decisions.push(enrichDecisionForLog(this.buildSkipDecision(upper, analysis.regime, analysis.bias, analysis.confluence, selectedStrategy, rationale, gate.reason, translatedTh)));
            continue;
        }
        
        // Fix 3: Before skipping, honour shrink actions (CLOSE / REDUCE / PARTIAL_CLOSE)
        // carried by the management plan. The AI can say action=SKIP but still instruct
        // us to close a specific losing ticket  we must not drop that intent.
        const shrinkMode = managementPlan.mode === 'CLOSE' || managementPlan.mode === 'REDUCE' || managementPlan.mode === 'PARTIAL_CLOSE';
        if (analysis.confluence < cfg.minConfluence || analysis.fitness < cfg.minFitness || side === 'SKIP') {
          if (shrinkMode && cluster.positions.length > 0) {
            const managementId = `MGMT-SKIP-${Date.now()}-${upper}`;
            this.insertManagementJournal({
              managementId,
              symbol: upper,
              timeframe: cfg.timeframe,
              mode: managementPlan.mode,
              status: 'PLANNED',
              side: managementPlan.side ?? null,
              targetTicket: managementPlan.ticket ?? null,
              volume: managementPlan.volume ?? managementPlan.closeVolume ?? null,
              sizeFraction: managementPlan.fraction ?? null,
              reason: managementPlan.reason,
              playbook: managementPlan.playbook ?? null,
              marketContext: {
                regime: analysis.regime,
                bias: analysis.bias,
                confluence: round1(analysis.confluence),
                fitness: round1(analysis.fitness),
                clusterPnl: cluster.totalProfit,
                triggeredFrom: 'SKIP_BRANCH',
              },
              aiContext: aiDecision ?? {},
              result: { phase: 'before_execution' },
            });
            const mgmtResult = await this.executeManagementPlan(upper, managementPlan, sl, tp, aiDecision);
            this.insertManagementJournal({
              managementId,
              symbol: upper,
              timeframe: cfg.timeframe,
              mode: managementPlan.mode,
              status: mgmtResult.executed ? 'EXECUTED' : 'SKIPPED',
              side: managementPlan.side ?? null,
              targetTicket: managementPlan.ticket ?? null,
              createdTicket: mgmtResult.ticket,
              volume: managementPlan.volume ?? managementPlan.closeVolume ?? null,
              sizeFraction: managementPlan.fraction ?? null,
              reason: managementPlan.reason,
              playbook: managementPlan.playbook ?? null,
              result: { executed: mgmtResult.executed, gate: mgmtResult.riskGate, triggeredFrom: 'SKIP_BRANCH' },
            });
            // eslint-disable-next-line no-console
            atLog(`[AutoEngine]  SKIP-branch ${managementPlan.mode} on ${upper}: ${mgmtResult.executed ? ' executed' : ' failed'}  ${mgmtResult.riskGate}`);
            const skipDecision = this.buildSkipDecision(upper, analysis.regime, analysis.bias, analysis.confluence, selectedStrategy, rationale, skipRiskGate, translatedTh);
            skipDecision.riskGate = mgmtResult.executed
              ? `SKIP + ${managementPlan.mode.toLowerCase()} executed | ${mgmtResult.riskGate}`
              : `SKIP + ${managementPlan.mode.toLowerCase()} attempted | ${mgmtResult.riskGate}`;
            pushDecisionTrace(gateTrace, 'skip_branch_management', mgmtResult.executed ? 'EXECUTED' : 'FAILED', skipDecision.riskGate);
            decisions.push(enrichDecisionForLog(skipDecision, { managementResult: mgmtResult }));
            continue;
          }
          pushDecisionTrace(gateTrace, 'final_entry_gate', 'SKIP', skipRiskGate, {
            confluence: round1(analysis.confluence),
            minConfluence: cfg.minConfluence,
            fitness: round1(analysis.fitness),
            minFitness: cfg.minFitness,
            side,
          });
          decisions.push(enrichDecisionForLog(this.buildSkipDecision(upper, analysis.regime, analysis.bias, analysis.confluence, selectedStrategy, rationale, skipRiskGate, translatedTh)));
          continue;
        }

        const decisionId = `AUTO-${Date.now()}-${upper}`;
        const managementId = `MGMT-${Date.now()}-${upper}`;
        let executed = false;
        let ticket: number | null = null;
        let riskGate = gate.reason;
        let decisionSide: 'BUY' | 'SELL' | 'SKIP' = side;
        let managementOpenedTrade = false;
        let orderLog: Record<string, unknown> = {};

        if (cluster.positions.length > 0 && managementPlan.mode !== 'HOLD') {
          this.insertManagementJournal({
            managementId,
            relatedDecisionId: decisionId,
            symbol: upper,
            timeframe: cfg.timeframe,
            mode: managementPlan.mode,
            status: 'PLANNED',
            side: managementPlan.side ?? null,
            targetTicket: managementPlan.ticket ?? null,
            volume: managementPlan.volume ?? managementPlan.closeVolume ?? null,
            sizeFraction: managementPlan.fraction ?? null,
            reason: managementPlan.reason,
            playbook: managementPlan.playbook ?? null,
            marketContext: {
              regime: analysis.regime,
              bias: analysis.bias,
              confluence: round1(analysis.confluence),
              fitness: round1(analysis.fitness),
              clusterPnl: cluster.totalProfit,
            },
            aiContext: aiDecision ?? {},
            result: { phase: 'before_execution' },
          });
          const managementResult = await this.executeManagementPlan(upper, managementPlan, sl, tp, aiDecision);
          orderLog = {
            type: 'management',
            mode: managementPlan.mode,
            requestedSide: managementPlan.side ?? null,
            requestedVolume: managementPlan.volume ?? managementPlan.closeVolume ?? null,
            result: managementResult,
          };
          if (managementResult.executed) {
            executed = true;
            riskGate = managementResult.riskGate;
            ticket = managementResult.ticket;
            decisionSide = managementResult.side ?? side;
            managementOpenedTrade = managementPlan.mode === 'HEDGE' || managementPlan.mode === 'SCALE_IN';
            if (managementOpenedTrade && managementPlan.volume) {
                volume = managementPlan.volume;
            }

            // P3.1 second leg  after a successful FLIP_CLUSTER close, immediately
            // open the opposite-side trade specified by the manager (if any).
            // The manager only attaches `triggerOpposite` for confirmed regime
            // reversals (H4 BOS + 65% confluence + worstLossR  0.5R).
            if (managementPlan.triggerOpposite && managementPlan.mode === 'CLOSE') {
              try {
                const opp = managementPlan.triggerOpposite;
                const oppPlan: ManagementPlan = {
                  mode: 'HEDGE', // HEDGE branch is the simplest "open new side" path inside executeManagementPlan
                  summary: `FLIP-leg: open ${opp.side}`,
                  reason: opp.rationale,
                  side: opp.side,
                  volume: opp.volume,
                  isAiDefense: true,
                  allowOverLimitDefense: true,
                  playbook: managementPlan.playbook,
                };
                const flipResult = await this.executeManagementPlan(upper, oppPlan, sl, tp, aiDecision);
                managementEventsTotal.inc({ symbol: upper, event: flipResult.executed ? 'flip_opposite_open' : 'flip_opposite_failed' });
                atLog(`[AutoEngine]  ${upper}: FLIP opposite ${opp.side} ${opp.volume}  ${flipResult.executed ? '' : ''} ${flipResult.riskGate}`);
              } catch (flipErr: any) {
                atWarn(`[AutoEngine]  ${upper}: FLIP opposite-leg threw: ${String(flipErr?.message || flipErr)}`);
              }
            }
          } else {
            riskGate = managementResult.riskGate;
          }
          this.insertManagementJournal({
            managementId,
            relatedDecisionId: decisionId,
            symbol: upper,
            timeframe: cfg.timeframe,
            mode: managementPlan.mode,
            status: managementResult.executed ? 'EXECUTED' : 'SKIPPED',
            side: managementPlan.side ?? null,
            targetTicket: managementPlan.ticket ?? null,
            createdTicket: managementResult.ticket,
            volume: managementPlan.volume ?? managementPlan.closeVolume ?? null,
            sizeFraction: managementPlan.fraction ?? null,
            reason: managementPlan.reason,
            playbook: managementPlan.playbook ?? null,
            marketContext: {
              regime: analysis.regime,
              bias: analysis.bias,
              confluence: round1(analysis.confluence),
              fitness: round1(analysis.fitness),
            },
            aiContext: aiDecision ?? {},
            result: {
              executed: managementResult.executed,
              gate: managementResult.riskGate,
            },
          });
        }

        const shouldProcessTrade = (!executed || managementOpenedTrade) && (gate.allowed || managementOpenedTrade) && sl !== null && tp !== null;
        if (shouldProcessTrade) {
          if (cfg.enableLiveTrading) {
            if (!managementOpenedTrade) {
              const rawComment = `A_${selectedStrategy}_${upper}`;
              const safeComment = rawComment.substring(0, 26);
              let orderType = 'market';
              let entryPrice: number | undefined = undefined;

              if (aiDecision?.playbook) {
                if (aiDecision.playbook.includes('LIMIT')) orderType = 'limit';
                else if (aiDecision.playbook.includes('STOP')) orderType = 'stop';
                
                const priceMatch = aiDecision.playbook.match(/at\s+([\d.]+)/);
                if (priceMatch && priceMatch[1] && orderType !== 'market') {
                  entryPrice = parseFloat(priceMatch[1]);
                }
              }

              const orderPayload = {
                symbol: upper,
                side: side.toLowerCase(),
                volume,
                sl,
                tp,
                type: orderType,
                ...(entryPrice !== undefined ? { price: entryPrice } : {}),
                comment: safeComment,
              };
              orderLog = { type: 'order', payload: orderPayload };
              const orderEntryPrice = entryPrice ?? entryRounded;
              const useScaleInDuplicatePolicy = detDecision.management === 'SCALE_IN';
              const duplicateOpts = useScaleInDuplicatePolicy
                ? {
                    atr,
                    baseMinDistance: upper.includes('XAU') || upper.includes('GOLD')
                      ? (cfg.adaptive?.scaleInMinEntryDistanceXAU ?? 2.0)
                      : Math.max(orderEntryPrice * 0.00025, 0.00025),
                    atrMultiplier: cfg.adaptive?.scaleInMinEntryDistanceAtrMul ?? 0.04,
                    comment: safeComment,
                  }
                : { atr, comment: safeComment };
              const livePositionsForDuplicate = await marketDataService.fetchPositions(true).catch(() => positions);
              const duplicateIssue = tradingExecutionService.nearDuplicateEntryIssue(
                upper,
                side as 'BUY' | 'SELL',
                orderEntryPrice,
                livePositionsForDuplicate,
                cfg,
                duplicateOpts,
              );
              if (duplicateIssue) {
                atWarn(`   ${upper}: Order blocked: ${duplicateIssue}. Choose one setup or wait for spacing.`);
                riskGate = duplicateIssue;
                orderLog = { ...orderLog, blocked: true, duplicateIssue };
                pushDecisionTrace(gateTrace, 'order_preflight', 'BLOCK', duplicateIssue, {
                  useScaleInDuplicatePolicy,
                  orderEntryPrice,
                });
              } else {
                tradingExecutionService.rememberEntryIntent(upper, side as 'BUY' | 'SELL', orderEntryPrice, safeComment);
              pushDecisionTrace(gateTrace, 'order_preflight', 'ALLOW', 'duplicate preflight passed', {
                useScaleInDuplicatePolicy,
                orderEntryPrice,
              });
              // eslint-disable-next-line no-console
              atLog(`[AutoEngine]  Sending Order: ${JSON.stringify(orderPayload)}`);
              try {
                const result = await callBridge('POST', ['/order', '/mt5/order', '/api/mt5/order'], {
                  body: orderPayload,
                  timeoutMs: 25_000,
                  priority: 'critical',
                });
                ticket = this.extractTicket(result.data);
                executed = ticket !== null || JSON.stringify(result.data).includes('"success":true');
                // Broker state just changed  drop the 500ms snapshot cache so the
                // next fetchPositions()/fetchAccount() sees the new position.
                if (executed) marketDataService.invalidateSnapshots();
                // eslint-disable-next-line no-console
                atLog(`[AutoEngine] ${executed ? '' : ''} Order Result: ticket=${ticket} | response=${JSON.stringify(result.data)}`);
                riskGate = executed ? 'live order sent' : 'order request failed';
                orderLog = { ...orderLog, response: result.data, ticket, executed };
                pushDecisionTrace(gateTrace, 'order_send', executed ? 'EXECUTED' : 'FAILED', riskGate, {
                  ticket,
                  response: result.data as Record<string, unknown>,
                });
              } catch (orderErr: any) {
                // eslint-disable-next-line no-console
                atError(`[AutoEngine]  Order FAILED: ${String(orderErr?.message || orderErr)}`);
                riskGate = `order error: ${String(orderErr?.message || orderErr)}`;
                orderLog = { ...orderLog, error: String(orderErr?.message || orderErr), executed: false };
                pushDecisionTrace(gateTrace, 'order_send', 'FAILED', riskGate);
              }
              }
              if (executed && cfg.notificationSettings?.onOrder) {
                broadcast({
                    type: 'intelligence_alert',
                    title: `ORDER EXECUTED: ${upper}`,
                    message: `Opened ${side} ${volume} lots. SL: ${sl}, TP: ${tp}. AI Confidence: ${aiDecision?.confidence || 0}%`,
                    priority: 'CRITICAL',
                    at: Date.now()
                });
              }
            }
          } else {
            riskGate = managementOpenedTrade ? `paper manage: ${managementPlan.mode.toLowerCase()}` : 'paper mode';
          }

          persistenceService.upsertJournal({
            id: 0,
            decisionId,
            symbol: upper,
            timeframe: cfg.timeframe,
            side: decisionSide,
            strategy: selectedStrategy,
            analyzersUsed: `BROKER_TREND,BROKER_MOMENTUM,BROKER_RISK`,
            signalsJson: JSON.stringify({ source: 'broker', regime: analysis.regime, signals: analysis.signals }),
            confluenceScore: round1(analysis.confluence),
            entry: entryRounded,
            sl,
            tp,
            volume,
            riskPct: cfg.risk.riskPerTradePct,
            rrr: round2(actualRrr),
            regime: analysis.regime,
            marketSnapshot: JSON.stringify({ price: entryRounded, atr, timeframe: cfg.timeframe, fitness: analysis.fitness, targetRrr: round2(targetRrr), slTpModelId: slTpModelUsed }),
            wasExecuted: executed,
            mt5Ticket: ticket,
            closeReason: null,
            closePrice: null,
            closeAt: null,
            profit: null,
            profitR: null,
            outcome: executed ? 'OPEN' : cfg.enableLiveTrading ? 'CANCELLED' : 'PAPER',
            aiReview: managementPlan.mode !== 'HOLD'
              ? `management=${managementPlan.mode}; reason=${managementPlan.reason}`
              : aiDecision?.risk_note || null,
            modelId: aiDecision?.modelUsed || 'technical_rules',
            createdAt: Date.now(),
            updatedAt: Date.now(),
          });
        }

        decisions.push(enrichDecisionForLog({
          symbol: upper,
          regime: analysis.regime,
          overallBias: analysis.bias,
          confluence: round1(analysis.confluence),
          fitness: round1(analysis.fitness),
          strategy: selectedStrategy,
          side: decisionSide,
          entry: entryRounded,
          sl,
          tp,
          volume,
          rrr: round2(actualRrr),
          rationale,
          translatedTh,
          analyzersUsed: ['BROKER_TREND', 'BROKER_MOMENTUM', 'BROKER_RISK'],
          riskGate,
          executed,
          mt5Ticket: ticket,
          at: nowIso(),
          decisionId,
        }, orderLog));
      }

      this.lastDecisions = decisions;
      this.appendDecisionFeed(this.state.cycleCount + 1, cfg.timeframe, decisions);
      this.openJournal = persistenceService.loadOpenJournal();
      const nExec = decisions.filter((it) => it.executed).length;
      const nSkip = decisions.filter((it) => it.side === 'SKIP' || (!it.executed && it.riskGate !== 'allowed' && it.riskGate !== 'paper mode')).length;
      const nNoSignal = decisions.filter((it) => it.decisionType === 'NO_SIGNAL').length;
      const nBlocked = decisions.filter((it) => it.decisionType === 'ORDER_BLOCKED').length;
      // eslint-disable-next-line no-console
      atLog(`[AutoEngine]  Cycle #${this.state.cycleCount + 1} completed: ${nExec} executed | ${nSkip} skipped (${nBlocked} blocked | ${nNoSignal} no-signal)`);
      for (const d of decisions) {
		// eslint-disable-next-line no-console
        atLog(`   ${d.symbol.padEnd(6)} | ${d.side.padEnd(4)} | ${d.rationale}  (Gate: ${d.riskGate})`);
      }
      this.state = {
        ...this.state,
        phase: 'IDLE',
        cycleCount: this.state.cycleCount + 1,
        openTradesTracked: positions.length,
        message: `server cycle done ${nExec} executed / ${nSkip} skipped`,
      };
      this.persistRuntime();
    } catch (error) {
      atError('[AutoEngine]  Critical error in runCycle:', error);
      this.state = { 
        ...this.state, 
        phase: 'IDLE', 
        message: `cycle failed: ${String((error as Error)?.message || error)}` 
      };
      } finally {
        timer();
        this.cycleBusy = false;
      }
    });
  }

  private async runManage(): Promise<void> {
    if (this.manageBusy) return;
    // 2026-04-24: no longer block on cycleBusy  management runs in parallel with
    // the analysis cycle. They share a 500ms snapshot cache in MarketDataService
    // so there's no extra bridge load, and management no longer waits 20-40s for
    // the LLM phase to finish before checking BE/Trail/Hedge.
    this.manageBusy = true;
    
    // Generate a unique trace ID for this management cycle
    const traceId = `manage-${Date.now().toString().slice(-6)}`;
    
    await traceStorage.run({ traceId }, async () => {
      try {
      // Market Guard: Don't manage positions if all watchlist markets are closed.
      // This prevents "Modify" calls (SL/TP) that would be rejected by MT5 (retcode 10018).
      const marketOpen = await this.isMarketOpen();
      if (!marketOpen) {
          // If the cycle loop already put us to sleep, just stay silent.
          if (this.state.phase !== 'SLEEPING') {
              this.state = { ...this.state, phase: 'SLEEPING', message: 'Market closed. Management paused.' };
              this.persistRuntime();
          }
          return;
      }

      const positions = await this.fetchPositions();
      const hasOpenJournal = this.openJournal.some((it) => it.outcome === 'OPEN');
      if (positions.length === 0 && !hasOpenJournal) {
        this.state = { ...this.state, phase: 'IDLE', openTradesTracked: 0, message: 'server manage idle (no open positions)' };
        this.persistRuntime();
        return;
      }
      if (Date.now() - this.lastMarketAwareManageAt < 15_000) {
        this.state = { ...this.state, phase: 'IDLE', openTradesTracked: positions.length, message: 'server manage skipped (cycle already managed positions)' };
        this.persistRuntime();
        return;
      }

      // eslint-disable-next-line no-console
      atLog('[AutoEngine]  Running Trade Manager...');
      this.state = { ...this.state, phase: 'MANAGING', message: 'server managing positions' };
      this.persistRuntime();

      const openTickets = new Set(positions.map((it) => it.ticket));
      const history = await this.fetchHistory(500);

      for (const row of this.openJournal.filter((it) => it.outcome === 'OPEN')) {
        const resolvedTicket = row.mt5Ticket ?? this.matchTicketFromOpenPositions(row, positions);
        if (resolvedTicket && row.mt5Ticket !== resolvedTicket) {
          persistenceService.upsertJournal({
            ...row,
            mt5Ticket: resolvedTicket,
            updatedAt: Date.now(),
          });
        }
        if (resolvedTicket && !openTickets.has(resolvedTicket)) {
          const deal = matchHistoryDeal(row, history);
          if (!deal) {
            this.logDeferredClosedDeal(resolvedTicket, row.symbol, 'outcome classification');
            continue;
          }
          this.closedDealDeferredLastLogAt.delete(`outcome classification:${resolvedTicket}`);
          const closePrice = extractDealClosePrice(deal) ?? row.closePrice ?? row.entry ?? 0;
          const profit = aggregateDealProfits(row, history);
          const closeReason = detectCloseReason(deal);
          // Use actual broker open price from deal if available to eliminate slippage bias in R
          const dealPriceOpen: number | null = (deal as any)?.price_open > 0
            ? Number((deal as any).price_open)
            : (deal as any)?.priceOpen > 0
              ? Number((deal as any).priceOpen)
              : null;
          const profitR = profitToR(row.entry, row.sl, closePrice, row.side, dealPriceOpen);
          const outcome = classifyOutcome(profit, profitR);
          persistenceService.upsertJournal({
            ...row,
            closeReason,
            closePrice,
            closeAt: Date.now(),
            profit,
            profitR,
            outcome,
            updatedAt: Date.now(),
          });
          persistenceService.markDecisionOutcome(row.decisionId, {
            recordType: 'TRADE_OUTCOME',
            symbol: row.symbol,
            mt5Ticket: resolvedTicket,
            outcome,
            profit,
            profitR,
            closeReason,
            closePrice,
            closeAt: Date.now(),
            historyDeal: deal,
          });
          this.markManagementOutcome(resolvedTicket, row.decisionId, outcome, profit, profitR, closeReason);

          // Update Knowledge Graph with the normalized outcome so it learns from
          // R-multiple fallback when broker profit is missing/zero.
          knowledgeGraph.addTradeInsight(row.symbol, row.strategy, outcome === 'LOSS' ? 'LOSS' : 'WIN', closeReason);

          // Feed the closed outcome back into the vector memory so future
          // retrieval can see real (entry, outcome) pairs  not just the
          // open-side snapshot. Embedding failure must never block close.
          if (this.config.apiKey) {
            try {
              const profitRSafe = profitR ?? 0;
              // P3.4: tag closed trade with failure-pattern labels (if any)
              const scaleInSteps = String(row.aiReview || '').split(';').filter((s) => s.toUpperCase().includes('SCALE_IN')).length;
              const failurePatterns = detectFailurePattern({
                side: row.side,
                strategy: row.strategy,
                outcome,
                profitR: profitRSafe,
                zoneAtEntry: (row as any).zoneAtEntry ?? null,
                regimeAtEntry: row.regime,
                htfBiasAtEntry: (row as any).htfBiasAtEntry ?? null,
                closeReason,
                scaleInSteps,
                weightedAvgEntry: (row as any).weightedAvgEntry ?? row.entry ?? null,
                finalEntry: row.entry ?? null,
                slInsideFvg: Boolean((row as any).slInsideFvg),
              });
              const patternsLabel = failurePatterns.length > 0 ? `Patterns: ${failurePatterns.join(',')}` : 'Patterns: none';
              const outcomeText = [
                `Trade closed on ${row.symbol}/${row.timeframe}`,
                `Strategy: ${row.strategy}`,
                `Side: ${row.side} | Entry: ${row.entry} | Close: ${closePrice}`,
                `Outcome: ${outcome} | Profit: ${profit.toFixed(2)} | ProfitR: ${profitRSafe.toFixed(2)}`,
                `Close reason: ${closeReason}`,
                `Regime at entry: ${row.regime}`,
                patternsLabel,
              ].join(' | ');
              const vec = await agentOrchestrator.getEmbeddingForConfig(this.config, outcomeText);
              if (vec.some((v) => v !== 0)) {
                vectorStore.add(vec, {
                  id: `outcome_${resolvedTicket}_${Date.now()}`,
                  symbol: row.symbol,
                  timeframe: row.timeframe,
                  type: 'trade_outcome',
                  timestamp: Date.now(),
                  outcome,
                  profitR: profitRSafe,
                  text: outcomeText,
                  side: row.side,
                  strategy: row.strategy,
                  regime: row.regime ?? undefined,
                  zoneAtEntry: (row as any).zoneAtEntry ?? undefined,
                  failure_pattern: failurePatterns.join(','),
                });
                if (failurePatterns.length > 0) {
                  atLog(`[AutoEngine]  #${resolvedTicket} tagged: ${failurePatterns.join(', ')}`);
                }
              }
            } catch (embErr) {
              atWarn(`[AutoEngine] trade_outcome embedding failed for #${resolvedTicket}:`, embErr);
            }
          }

          if (this.config.notificationSettings?.onClose) {
              broadcast({
                  type: 'intelligence_alert',
                  title: `TRADE CLOSED: ${row.symbol}`,
                  message: `${outcome === 'WIN' ? ' WIN' : outcome === 'LOSS' ? ' LOSS' : ' BE'} | Profit: $${profit.toFixed(2)} | R: ${(profitR ?? 0).toFixed(2)} | Reason: ${closeReason}`,
                  priority: 'HIGH',
                  at: Date.now()
              });
          }
          // eslint-disable-next-line no-console
          const swap = (deal as any)?.swap ?? 0;
          const commission = (deal as any)?.commission ?? 0;
          const netProfit = profit + swap + commission;
          atLog(`[AutoEngine]  Closed Trade #${row.mt5Ticket} | ${row.symbol} | ${outcome} | Profit: ${profit} | Net(swap+comm): ${netProfit.toFixed(2)} | R=${profitR ?? 0} (${closeReason})`);
        }
      }

      // Fix 6: NO_JOURNAL_MATCH backfill  for broker positions that have no
      // matching journal row (e.g. opened before this engine started or the
      // journal row was lost), synthesize a minimal OPEN journal row using
      // broker priceOpen/sl so BE/Trail can still compute r. Without this,
      // the most profitable legacy tickets go silently unmanaged.
      //
      // 2026-04-24 race guard: cycle + manage now run in parallel, so a ticket
      // that was just opened by the cycle may appear here BEFORE the cycle's
      // upsertJournal() call lands. Skip positions younger than 10 seconds so
      // we don't write a duplicate BACKFILL row that shadows the real one.
      if (positions.length > 0) {
        const now = Date.now();
        const FRESH_MS = 10_000;
        const orphanPositions = positions.filter(
          (p) => !this.openJournal.some((j) => j.mt5Ticket === p.ticket)
        );
        for (const p of orphanPositions) {
          // 2026-05-02 Phase 3.4 — กัน clock skew ระหว่าง bridge/server
          //   เดิม: ถ้า p.openedAtMs > now (server เวลาเร็วกว่า broker) → age ติดลบ
          //         negative < FRESH_MS เป็นจริง → defer ตลอด → BE/Trail ใช้ไม่ได้
          //   แก้: ใช้ Math.max(0, age) จะถือเป็น "เพิ่งเปิด" ปกติ และ defer ตามเดิม 10s
          const ageRaw = p.openedAtMs ? now - p.openedAtMs : Number.MAX_SAFE_INTEGER;
          const age = Math.max(0, ageRaw);
          if (p.openedAtMs && age < FRESH_MS) {
            const skewNote = ageRaw < 0 ? ` [clock skew ${ageRaw}ms clamped to 0]` : '';
            atLog(`[AutoEngine]  Deferring backfill for fresh #${p.ticket} ${p.symbol} (age=${age}ms < ${FRESH_MS}ms)${skewNote}  cycle may still be writing journal`);
            continue;
          }
          // Refuse to invent a risk unit when broker SL is missing  inventing
          // one here would make BE trigger at a fabricated r.
          if (!(p.priceOpen > 0) || !(p.sl > 0)) {
            atLog(`[AutoEngine]  Cannot backfill journal for orphan #${p.ticket} ${p.symbol}: priceOpen=${p.priceOpen} sl=${p.sl}  BE/Trail will remain gated`);
            continue;
          }
          const risk = Math.abs(p.priceOpen - p.sl);
          const tp = p.side === 'BUY' ? p.priceOpen + risk * 1.5 : p.priceOpen - risk * 1.5;
          const syntheticDecisionId = `BACKFILL-${p.ticket}-${Date.now()}`;
          try {
            persistenceService.upsertJournal({
              id: 0,
              decisionId: syntheticDecisionId,
              symbol: p.symbol,
              timeframe: this.config.timeframe,
              side: p.side,
              strategy: 'BACKFILL',
              analyzersUsed: 'BACKFILL',
              signalsJson: '{}',
              confluenceScore: 0,
              entry: p.priceOpen,
              sl: p.sl,
              tp,
              volume: p.volume,
              riskPct: null,
              rrr: 1.5,
              regime: null,
              marketSnapshot: JSON.stringify({ backfilled: true, atBackfill: Date.now() }),
              wasExecuted: true,
              mt5Ticket: p.ticket,
              closeReason: null,
              closePrice: null,
              closeAt: null,
              profit: null,
              profitR: null,
              outcome: 'OPEN',
              aiReview: 'BACKFILL: synthesized from broker position (journal row missing)',
              createdAt: Date.now(),
              updatedAt: Date.now(),
            });
            atLog(`[AutoEngine]  Backfilled journal for orphan #${p.ticket} ${p.symbol} ${p.side} entry=${p.priceOpen} sl=${p.sl} tp=${round2(tp)} (risk=${round2(risk)})`);
          } catch (err) {
            atWarn(`[AutoEngine]  Backfill failed for #${p.ticket}:`, err);
          }
        }
      }

      // Fix 5: Trade Manager audit log — emit a compact per-position summary before
      // any break-even/trail decision so the reason for inaction is never a silent mystery.

      // 2026-05-01 — AUTO-REPAIR: Detect and fix positions with broken SL (AI sent
      // distance instead of price level). SL < 1% of entry price is a dead giveaway.
      // V2: Use current price as SL base (not entry) to avoid Error 10016 when
      // price has already moved past the entry level.
      for (const p of positions) {
        const j = this.openJournal.find((it) => it.mt5Ticket === p.ticket);
        if (!j || j.entry === null || j.sl === null) continue;
        
        const journalSlPct = (j.sl / j.entry) * 100;
        const brokerSlPct = p.priceOpen > 10 ? (p.sl / p.priceOpen) * 100 : 100;
        
        const journalTpPct = j.tp ? (j.tp / j.entry) * 100 : 100;
        const brokerTpPct = (p.priceOpen > 10 && p.tp > 0) ? (p.tp / p.priceOpen) * 100 : 100;
        
        let needsBrokerModify = false;
        let needsJournalUpdate = false;
        
        let targetSl = p.sl;
        let targetTp = p.tp;
        let targetJournalSl = j.sl;
        let targetJournalTp = j.tp;

        const isSlBroken = journalSlPct < 1 || journalSlPct > 200 || brokerSlPct < 1 || brokerSlPct > 200;
        const isTpBroken = journalTpPct < 1 || journalTpPct > 200 || brokerTpPct < 1 || brokerTpPct > 200;

        // Repair SL if needed
        if (isSlBroken && j.entry > 10) {
          const currentPrice = p.priceCurrent;
          const basePrice = j.side === 'BUY' ? Math.min(j.entry, currentPrice) : Math.max(j.entry, currentPrice);
          const atrFallback = basePrice * 0.004; 
          const fixedSl = j.side === 'BUY' ? basePrice - atrFallback : basePrice + atrFallback;
          
          targetSl = fixedSl;
          targetJournalSl = fixedSl;
          if (brokerSlPct < 1 || brokerSlPct > 200) needsBrokerModify = true;
          if (journalSlPct < 1 || journalSlPct > 200 || Math.abs(j.sl - fixedSl) > 0.01) needsJournalUpdate = true;
          
          atWarn(`[AutoEngine] ⚠️ BROKEN SL DETECTED #${p.ticket} ${p.symbol}: journal SL=${j.sl} (${journalSlPct.toFixed(2)}%), broker SL=${p.sl} (${brokerSlPct.toFixed(2)}%). Current=${currentPrice}. Auto-repairing to ${fixedSl}`);
        }
        
        // Repair TP if needed
        if (isTpBroken && j.entry > 10) {
          const currentPrice = p.priceCurrent;
          // TP base should be the current optimal price direction
          const basePrice = j.side === 'BUY' ? Math.max(j.entry, currentPrice) : Math.min(j.entry, currentPrice);
          const atrFallbackTp = basePrice * 0.006; // 0.6% target
          const fixedTp = j.side === 'BUY' ? basePrice + atrFallbackTp : basePrice - atrFallbackTp;
          
          targetTp = fixedTp;
          targetJournalTp = fixedTp;
          if (brokerTpPct < 1 || brokerTpPct > 200) needsBrokerModify = true;
          if (journalTpPct < 1 || journalTpPct > 200 || Math.abs((j.tp || 0) - fixedTp) > 0.01) needsJournalUpdate = true;
          
          atWarn(`[AutoEngine] ⚠️ BROKEN TP DETECTED #${p.ticket} ${p.symbol}: journal TP=${j.tp} (${journalTpPct.toFixed(2)}%), broker TP=${p.tp} (${brokerTpPct.toFixed(2)}%). Current=${currentPrice}. Auto-repairing to ${fixedTp}`);
        }

        if (needsJournalUpdate) {
           j.sl = targetJournalSl;
           j.tp = targetJournalTp;
           persistenceService.upsertJournal(j);
        }

        if (needsBrokerModify) {
            try {
              const tick = tickSize(p.symbol);
              await callBridge('POST', ['/modify', '/mt5/modify', '/api/mt5/modify'], {
                body: { 
                  ticket: p.ticket, 
                  symbol: p.symbol, 
                  sl: roundToTick(targetSl, tick), 
                  tp: targetTp > 0 ? roundToTick(targetTp, tick) : 0 
                },
                timeoutMs: 15_000,
                priority: 'critical',
              });
              atLog(`[AutoEngine] ✅ Broker SL/TP repaired for #${p.ticket} ${p.symbol} -> SL=${targetSl}, TP=${targetTp}`);
              marketDataService.invalidateSnapshots();
            } catch (err) {
              atWarn(`[AutoEngine] ⚠️ Failed to repair broker SL/TP for #${p.ticket}: ${err}`);
            }
        }
      }

      if (positions.length > 0) {
        let totalHeatR = 0;
        const auditLines = positions.map((p) => {
          const j = this.openJournal.find((it) => it.mt5Ticket === p.ticket);
          if (!j || j.entry === null || j.sl === null) {
            return `  ${p.symbol}#${p.ticket} ${p.side} vol=${p.volume} pnl=${round2(p.profit)}  NO_JOURNAL_MATCH (BE/Trail cannot evaluate)`;
          }
          const entryForR = p.priceOpen > 0 ? p.priceOpen : j.entry;
          const risk = Math.abs(entryForR - j.sl);
          const reward = p.side === 'BUY' ? p.priceCurrent - entryForR : entryForR - p.priceCurrent;
          const r = risk > 0 ? reward / risk : 0;
          if (r < 0) totalHeatR += Math.abs(r); // Accumulate drawdown risk
          
          const isScalp = j.strategy === 'SCALPING' || j.strategy === 'MEAN_REVERSION' || String(j.strategy ?? '').toUpperCase().includes('SCALP');
          const sameSideOpen = positions.filter((it) => it.symbol === p.symbol && it.side === p.side).length;
          // Scalp baskets are short-lived; stacked entries need faster protection
          // once the first leg gets paid, otherwise small wins often round-trip.
          const beTrigger = isScalp
            ? (sameSideOpen >= 2 ? (this.config.adaptive?.scalpStackBreakEvenTriggerR ?? 0.15) : (this.config.adaptive?.scalpBreakEvenTriggerR ?? 0.3))
            : (this.config.breakEvenTriggerR ?? 0.5);
          const trailTrigger = isScalp
            ? (sameSideOpen >= 2 ? (this.config.adaptive?.scalpStackTrailAfterR ?? 0.3) : (this.config.adaptive?.scalpTrailAfterR ?? 0.4))
            : (this.config.trailAfterR ?? 0.6);
          const beMsg = r >= beTrigger ? 'BE' : `BE<${beTrigger}`;
          const trailMsg = r >= trailTrigger ? 'TRAIL' : `TRAIL<${trailTrigger}`;
          const milestone = extractV25PathMilestone(j);
          const milestoneSince = Math.max(0, Math.min(j.createdAt || Date.now(), (p.openedAtMs && p.openedAtMs > 0) ? p.openedAtMs : Date.now()));
          const milestoneTicks = milestone ? tickBuffers.get(p.symbol.toUpperCase()).sinceMs(milestoneSince) : [];
          const milestoneHit = v25MilestoneReached(p.side, milestone, p.priceCurrent, milestoneTicks, milestoneSince);
          const milestoneMsg = milestone
            ? (milestoneHit ? ` | MILESTONE ${round2(milestone.protectAt ?? milestone.price)} HIT` : ` | MILESTONE<${round2(milestone.protectAt ?? milestone.price)}`)
            : '';
          // V24.1: surface position age in audit so stale positions are visible at a glance
          const ageMin = (j.createdAt && j.createdAt > 0)
            ? Math.round((Date.now() - j.createdAt) / 60_000)
            : null;
          const ageMsg = ageMin !== null ? ` age=${ageMin}m` : '';
          return `  ${p.symbol}#${p.ticket} ${p.side} vol=${p.volume} pnl=${round2(p.profit)} r=${round2(r)} sl=${p.sl}${ageMsg}  ${beMsg} | ${trailMsg}${milestoneMsg}`;
        });
        atLog(`[AutoEngine]  Manager audit (${positions.length} pos, Total Heat: ${totalHeatR.toFixed(2)}R):\n${auditLines.join('\n')}`);
      } else {
        atLog('[AutoEngine]  Manager audit: no open broker positions.');
      }

      if (!this.config.enableLiveTrading) {
        atLog('[AutoEngine]  Manager: enableLiveTrading=false  skipping BE/Trail/GaeMai phase.');
      }
      if (this.config.enableLiveTrading) {
        // --- V24.1 (2026-05-08) Stale-Position Time Stop ---
        // Close positions that have been stuck in the indecision zone for too long.
        // Live log showed 3 SELLs sitting at R∈[-0.4, +0.1] for 60+ min — neither
        // hitting BE/Trail nor scale-in recovery. They tie up risk budget and rarely
        // recover. This sweep cuts that churn.
        // V24.1.1 FIX (2026-05-08): use the OLDER of (p.openedAtMs, j.createdAt) and
        // clamp ageMs to 0 — broker time can be ahead of server time (UTC skew),
        // making nowMs - openedAt go negative and silently disabling the gate.
        const staleMaxAge = this.config.stalePositionMaxAgeMs ?? 45 * 60_000;
        const staleMinR   = this.config.stalePositionMinR   ?? -0.25;
        const staleMaxR   = this.config.stalePositionMaxR   ?? 0.30;
        const nowMs = Date.now();
        let staleCandidates = 0;
        let staleClosed = 0;
        for (const p of positions) {
          const backoff = staleCloseBackoff.get(p.ticket) ?? 0;
          if (nowMs < backoff) continue;

          // V24.1.4: Check if symbol is tradable before trying to close.
          // This avoids spamming close requests when market is closed.
          const tradeStatus = await this.isSymbolTradable(p.symbol);
          if (!tradeStatus.tradable) {
            // Market is closed! Backoff for 5 minutes so we don't spam isSymbolTradable.
            staleCloseBackoff.set(p.ticket, nowMs + 5 * 60 * 1000);
            continue;
          }

          const j = this.openJournal.find((it) => it.mt5Ticket === p.ticket);
          if (!j || j.entry === null || j.sl === null) continue;
          const entryForR = p.priceOpen > 0 ? p.priceOpen : j.entry;
          const risk = Math.abs(entryForR - j.sl);
          if (risk <= 0) continue;
          const reward = p.side === 'BUY' ? p.priceCurrent - entryForR : entryForR - p.priceCurrent;
          const r = reward / risk;
          // Use the OLDER timestamp (most conservative) — clock-skew safe
          const ts1 = (typeof p.openedAtMs === 'number' && p.openedAtMs > 0) ? p.openedAtMs : 0;
          const ts2 = (typeof j.createdAt === 'number' && j.createdAt > 0) ? j.createdAt : 0;
          const tsCandidates = [ts1, ts2].filter((t) => t > 0);
          if (tsCandidates.length === 0) continue;
          const openedAt = Math.min(...tsCandidates);
          const ageMs = Math.max(0, nowMs - openedAt);
          const inDeadZone = r >= staleMinR && r <= staleMaxR;
          if (ageMs >= staleMaxAge && inDeadZone) {
            staleCandidates += 1;
            const ageMin = Math.round(ageMs / 60_000);
            // V24.1.1: use atLog (not atWarn) so this is visible even if user has
            // log filter set to info-only. Stale-exits are routine, not warnings.
            atLog(`[AutoEngine] ⏱️ Stale Position: ${p.symbol}#${p.ticket} ${p.side} R=${r.toFixed(2)} age=${ageMin}m (dead zone [${staleMinR}, ${staleMaxR}]) — closing to free risk budget`);
            try {
              await callBridge('POST', ['/close', '/mt5/close', '/api/mt5/close'], {
                body: { ticket: p.ticket, symbol: p.symbol },
                priority: 'critical',
              });
              this.insertManagementJournal({
                managementId: `MGMT-STALE-${p.ticket}-${nowMs}`,
                symbol: p.symbol,
                timeframe: j.timeframe,
                mode: 'CLOSE',
                status: 'EXECUTED',
                side: p.side,
                targetTicket: p.ticket,
                volume: p.volume,
                reason: `stale_position_time_stop (age=${ageMin}m, R=${r.toFixed(2)}, deadZone=[${staleMinR},${staleMaxR}])`,
                result: { closedTicket: p.ticket, ageMin, r: round2(r) },
              });
              staleClosed += 1;
              staleCloseBackoff.delete(p.ticket);
              if (this.config.notificationSettings?.onClose) {
                broadcast({
                  type: 'intelligence_alert',
                  title: `Stale exit: ${p.symbol}`,
                  message: `Closed #${p.ticket} ${p.side} after ${ageMin}m at R=${r.toFixed(2)} (no progress).`,
                  priority: 'MEDIUM',
                  at: nowMs,
                });
              }
            } catch (e) {
              const errStr = String(e);
              atLog(`[AutoEngine] ⚠️ Failed to close stale position #${p.ticket}: ${e}`);
              // If it's a 400 error (likely market closed or invalid ticket), backoff for 1 hour
              // Otherwise backoff for 5 minutes
              const backoffTime = errStr.includes('400') ? 60 * 60 * 1000 : 5 * 60 * 1000;
              staleCloseBackoff.set(p.ticket, nowMs + backoffTime);
            }
          }
        }
        // V24.1.1: per-cycle one-line summary so we can confirm the gate is alive
        // even when no candidate matches (helps diagnose silent disable).
        if (positions.length > 0) {
          atLog(`[AutoEngine]  Stale-Stop swept ${positions.length} pos → candidates=${staleCandidates} closed=${staleClosed} (maxAge=${Math.round(staleMaxAge/60_000)}m, deadZone=[${staleMinR},${staleMaxR}])`);
        }
        // refresh positions/journal in case stale-exits closed some tickets
        const refreshedPositions = await this.fetchPositions().catch(() => positions);
        if (refreshedPositions.length !== positions.length) {
          // mutate the array length so downstream loops skip closed tickets
          positions.length = 0;
          for (const rp of refreshedPositions) positions.push(rp);
          this.openJournal = persistenceService.loadOpenJournal();
        }
        // --- End Stale-Position Time Stop ---

        // --- 2026-05-01 Cluster Profit Protection (CPP) ---
        const positionsBySymbolAndSide: Record<string, any[]> = {};
        for (const position of positions) {
          const key = `${position.symbol}_${position.side}`;
          if (!positionsBySymbolAndSide[key]) positionsBySymbolAndSide[key] = [];
          positionsBySymbolAndSide[key].push(position);
        }

        for (const [key, group] of Object.entries(positionsBySymbolAndSide)) {
          const symbol = key.split('_')[0];
          const tick = tickSize(symbol, this.config.risk?.pointValueOverride || {});
          let totalProfit = 0;
          let totalRisk = 0;
          let unTrailedVolume = 0;
          let unTrailedWeightedPrice = 0;
          const unTrailedGroup = [];
          
          for (const p of group) {
            const j = this.openJournal.find((it) => it.mt5Ticket === p.ticket);
            if (!j || j.entry === null || j.sl === null) continue;
            const realEntry = p.priceOpen > 0 ? p.priceOpen : j.entry;
            const riskPoints = Math.abs(realEntry - j.sl);
            if (riskPoints <= 0) continue;
            
            const rewardPoints = p.side === 'BUY' ? p.priceCurrent - realEntry : realEntry - p.priceCurrent;
            const r = rewardPoints / riskPoints;
            
            if (r < this.config.trailAfterR) {
              unTrailedGroup.push({ position: p, journal: j, r, tick, realEntry });
              totalProfit += p.profit;
              unTrailedVolume += p.volume;
              unTrailedWeightedPrice += realEntry * p.volume;
              
              // Estimate dollar risk dynamically based on current profit vs reward points
              let pointValue = 1;
              if (Math.abs(rewardPoints) > 0.00001) {
                pointValue = Math.abs(p.profit / rewardPoints);
              } else if (this.config.risk?.pointValueOverride?.[symbol]) {
                pointValue = this.config.risk.pointValueOverride[symbol] * p.volume;
              }
              totalRisk += Math.abs(riskPoints * pointValue);
            }
          }

          if (unTrailedGroup.length > 0 && totalRisk > 0) {
            const clusterR = totalProfit / totalRisk;
            const activationR = this.config.risk?.clusterProfitProtectionActivationR ?? 0.75;
            const dropPct = this.config.risk?.clusterProfitProtectionDropPct ?? 50;
            const minDollar = this.config.risk?.clusterProfitProtectionMinDollar ?? 20.0;
            
            let currentMax = this.clusterMaxProfit.get(key) || 0;
            // Activate tracking when clusterR >= activationR OR profit >= minDollar
            if ((clusterR >= activationR || totalProfit >= minDollar) && totalProfit > currentMax) {
              currentMax = totalProfit;
              this.clusterMaxProfit.set(key, currentMax);
            }
            
            // If we have an active peak, check for drawdown
            if (currentMax > 0 && totalProfit <= currentMax * (dropPct / 100)) {
              const avgEntry = unTrailedVolume > 0 ? unTrailedWeightedPrice / unTrailedVolume : 0;
              atWarn(`[AutoEngine] ⚠️ Cluster Profit Protection triggered for ${key} (Un-trailed profit dropped from $${currentMax.toFixed(2)} to $${totalProfit.toFixed(2)}). Forcing Cluster Break-Even at avg ${avgEntry.toFixed(2)}.`);
              
              for (const item of unTrailedGroup) {
                // V24.1.2: dynamic buffer based on observed spread baseline (was hardcoded 0.5 for XAU)
                const isXauCpp = symbol.includes('XAU');
                const baselinePtsCpp = spreadBaseline.baseline(symbol) ?? 0;
                const baselinePriceCpp = baselinePtsCpp * item.tick;
                const minBufferCpp = isXauCpp ? 0.5 : (item.realEntry * 0.0002);
                const buffer = Math.max(2.5 * baselinePriceCpp, minBufferCpp);
                // Use Cluster Average Entry instead of individual entry so the whole cluster acts together!
                const targetEntry = item.position.side === 'BUY' ? avgEntry + buffer : avgEntry - buffer;
                let newSl = roundToTick(targetEntry, item.tick) ?? targetEntry;

                const tolerance = item.realEntry * 0.0001;
                const currentSl = item.position.sl;

                // Guard against 10016 Invalid Stops: Ensure SL is at least spreadBuffer away from current price
                const minSpreadBufferCpp = isXauCpp ? 0.8 : (item.realEntry * 0.0005);
                const spreadBuffer = Math.max(2.0 * baselinePriceCpp, minSpreadBufferCpp);
                if (item.position.side === 'BUY') {
                    if (newSl >= item.position.priceCurrent - spreadBuffer) {
                        newSl = item.position.priceCurrent - spreadBuffer;
                        newSl = roundToTick(newSl, item.tick) ?? newSl;
                    }
                } else {
                    if (newSl <= item.position.priceCurrent + spreadBuffer) {
                        newSl = item.position.priceCurrent + spreadBuffer;
                        newSl = roundToTick(newSl, item.tick) ?? newSl;
                    }
                }

                // We only modify if it moves SL favorably in the protection direction
                const isImprovement = item.position.side === 'BUY'
                  ? newSl > currentSl + tolerance
                  : (currentSl <= 0 || newSl < currentSl - tolerance);
                  
                if (isImprovement) {
                  callBridge('POST', ['/modify', '/mt5/modify', '/api/mt5/modify'], {
                    body: { ticket: item.position.ticket, symbol: item.position.symbol, sl: newSl },
                    priority: 'critical',
                  }).catch(e => atWarn(`CPP Modify Error: ${e}`));
                  
                  this.insertManagementJournal({
                    managementId: `MGMT-CPP-${item.position.ticket}-${Date.now()}`,
                    symbol: item.position.symbol,
                    timeframe: item.journal.timeframe,
                    mode: 'BREAKEVEN',
                    status: 'EXECUTED',
                    side: item.position.side,
                    targetTicket: item.position.ticket,
                    volume: item.position.volume,
                    reason: `Cluster Profit Protection: Profit dropped to $${totalProfit.toFixed(2)} from peak $${currentMax.toFixed(2)} | Cluster Average Entry BE`,
                    result: { newSl, triggerR: round2(item.r) },
                  });
                  // eslint-disable-next-line no-console
                  atLog(`[AutoEngine]  CPP Modified Trade #${item.position.ticket} | Set Cluster Break-Even to ${newSl}`);
                }
              }
              this.clusterMaxProfit.set(key, 0); // Reset after trigger
            }
          } else if (unTrailedGroup.length === 0) {
            this.clusterMaxProfit.delete(key);
          }
        }
        // --- End CPP ---

        // --- 2026-05-02 Cluster Loss Protection (CLP) ---
        for (const [key, group] of Object.entries(positionsBySymbolAndSide)) {
          let clusterPnL = 0;
          let clusterR = 0;
          const groupWithR = [];
          
          for (const p of group) {
            clusterPnL += p.profit;
            const j = this.openJournal.find((it) => it.mt5Ticket === p.ticket);
            let r = 0;
            if (j && j.entry !== null && j.sl !== null) {
              const risk = Math.abs(j.entry - j.sl);
              if (risk > 0) {
                const reward = p.side === 'BUY' ? p.priceCurrent - j.entry : j.entry - p.priceCurrent;
                r = reward / risk;
              }
            }
            clusterR += r;
            groupWithR.push({ position: p, r });
          }

          if (clusterR < -1.5 || clusterPnL < -150) {
             const worst = groupWithR.sort((a,b) => a.position.profit - b.position.profit)[0];
             if (worst) {
                const p = worst.position;
                atWarn(`[AutoEngine] ⚠️ Cluster Loss Protection triggered for ${key} (R=${clusterR.toFixed(2)}, PnL=$${clusterPnL.toFixed(2)}). Closing worst position #${p.ticket}`);
                try {
                   await callBridge('POST', ['/close', '/mt5/close', '/api/mt5/close'], {
                     body: { ticket: p.ticket, symbol: p.symbol },
                     priority: 'critical',
                   });
                   this.insertManagementJournal({
                     managementId: `MGMT-CLP-${p.ticket}-${Date.now()}`,
                     symbol: p.symbol,
                     timeframe: this.config.timeframe,
                     mode: 'CLOSE',
                     status: 'EXECUTED',
                     side: p.side,
                     targetTicket: p.ticket,
                     volume: p.volume,
                     reason: `emergency_cluster_stop (Cluster R=${clusterR.toFixed(2)}, PnL=$${clusterPnL.toFixed(2)})`,
                     result: { closedTicket: p.ticket },
                   });
                } catch (e) {
                   atWarn(`[AutoEngine] ⚠️ Failed to emergency close position #${p.ticket}: ${e}`);
                }
             }
          }
        }
        // --- End CLP ---

        // --- V24.1 (2026-05-08) SMC-Aware Trail + Regime-Flip Exit ---
        // Previously imported but never called. The legacy ATR-based trail below
        // still runs as a fallback, but SMC trail is preferred when valid because
        // it places SL at the actual structural level (OB/FVG/Swing) instead of
        // a fixed % distance.
        const smcTrailedTickets = new Set<number>();
        const symbolsWithPositions = Array.from(new Set(positions.map((p) => p.symbol.toUpperCase())));
        for (const sym of symbolsWithPositions) {
          const symbolPositions = positions.filter((p) => p.symbol.toUpperCase() === sym);
          if (symbolPositions.length === 0) continue;
          let smcSnapshot: any = null;
          try {
            const candlesForSmc = await this.fetchCandles(sym, this.config.timeframe, 180);
            if (candlesForSmc.length >= 60) {
              smcSnapshot = getSmcSnapshotV2(candlesForSmc, sym, this.config.timeframe);
            }
          } catch (e) {
            atWarn(`[AutoEngine]  SMC snapshot failed for ${sym}: ${e}`);
          }
          if (!smcSnapshot) continue;
          const cluster = this.buildPositionCluster(sym, symbolPositions);

          // ── (1) Regime-Flip Exit ──
          // If H4 structure flipped against the cluster (CHoCH down vs BUY cluster
          // or vice-versa), close the losing leg(s) instead of waiting for SL hit.
          try {
            const flip = detectRegimeFlipAction(cluster, smcSnapshot, this.openJournal);
            if (flip.action === 'CLOSE_LOSING' && flip.tickets.length > 0) {
              atWarn(`[AutoEngine] 🔄 Regime Flip on ${sym}: ${flip.reason}`);
              for (const ticket of flip.tickets) {
                const p = symbolPositions.find((it) => it.ticket === ticket);
                if (!p) continue;
                try {
                  await callBridge('POST', ['/close', '/mt5/close', '/api/mt5/close'], {
                    body: { ticket, symbol: p.symbol },
                    priority: 'critical',
                  });
                  this.insertManagementJournal({
                    managementId: `MGMT-FLIP-${ticket}-${Date.now()}`,
                    symbol: p.symbol,
                    timeframe: this.config.timeframe,
                    mode: 'CLOSE',
                    status: 'EXECUTED',
                    side: p.side,
                    targetTicket: ticket,
                    volume: p.volume,
                    reason: `regime_flip_exit (${flip.reason})`,
                    result: { closedTicket: ticket, newSide: flip.newSide },
                  });
                  atLog(`[AutoEngine]  Regime-Flip closed #${ticket} ${p.side} on ${sym}`);
                } catch (e) {
                  atWarn(`[AutoEngine] ⚠️ Regime-Flip close failed #${ticket}: ${e}`);
                }
              }
            }
          } catch (e) {
            atWarn(`[AutoEngine]  Regime-flip detection error on ${sym}: ${e}`);
          }

          // ── (2) SMC-Aware Trail ──
          // Compute structural trail targets and apply when better than current SL.
          try {
            const trailTargets = computeSmcTrailTargets(cluster, smcSnapshot, this.openJournal, 1.0);
            for (const target of trailTargets) {
              const p = symbolPositions.find((it) => it.ticket === target.ticket);
              if (!p) continue;
              const j = this.openJournal.find((it) => it.mt5Ticket === target.ticket);
              if (!j) continue;
              const tick = tickSize(p.symbol, this.config.risk.pointValueOverride || {});
              const newSl = roundToTick(target.proposedSl, tick) ?? target.proposedSl;
              try {
                await callBridge('POST', ['/modify', '/mt5/modify', '/api/mt5/modify'], {
                  body: { ticket: target.ticket, symbol: p.symbol, sl: newSl },
                  priority: 'critical',
                });
                this.insertManagementJournal({
                  managementId: `MGMT-SMCTRAIL-${target.ticket}-${Date.now()}`,
                  symbol: p.symbol,
                  timeframe: j.timeframe,
                  mode: 'TRAIL',
                  status: 'EXECUTED',
                  side: p.side,
                  targetTicket: target.ticket,
                  volume: p.volume,
                  reason: `smc_trail (${target.zoneType} conf=${target.confidence}) ${target.reason}`,
                  result: { newSl, oldSl: target.currentSl, zoneType: target.zoneType },
                });
                atLog(`[AutoEngine]  SMC-Trail #${target.ticket} ${p.side} → SL ${newSl} (${target.zoneType} ${target.reason})`);
                smcTrailedTickets.add(target.ticket);
              } catch (e) {
                atWarn(`[AutoEngine] ⚠️ SMC-Trail modify failed #${target.ticket}: ${e}`);
              }
            }
          } catch (e) {
            atWarn(`[AutoEngine]  SMC-Trail compute error on ${sym}: ${e}`);
          }
        }
        // --- End SMC-Trail / Regime-Flip ---

        for (const position of positions) {
          const matched = this.openJournal.find((it) => it.mt5Ticket === position.ticket);
          if (!matched || matched.entry === null || matched.sl === null) continue;
          // V24.1: skip ATR fallback trail if SMC trail already moved this ticket
          const smcAlreadyTrailed = smcTrailedTickets.has(position.ticket);
          const tick = tickSize(position.symbol, this.config.risk.pointValueOverride || {});
          
          // 2026-05-09 Fix: Use actual MT5 execution price instead of AI expected entry.
          // In fast markets (like XAU), slippage can cause matched.entry to be much better
          // than the real execution. BE/Trail must protect the REAL capital.
          const realEntry = position.priceOpen > 0 ? position.priceOpen : matched.entry;
          const risk = Math.abs(realEntry - matched.sl);
          if (risk <= 0) continue;
          
          const reward = position.side === 'BUY' ? position.priceCurrent - realEntry : realEntry - position.priceCurrent;
          const r = reward / risk;
          
          const isScalp = matched.strategy === 'SCALPING' || matched.strategy === 'MEAN_REVERSION' || String(matched.strategy ?? '').toUpperCase().includes('SCALP');
          const sameSideOpen = positions.filter((it) => it.symbol === position.symbol && it.side === position.side).length;
          const beTriggerR = isScalp
            ? (sameSideOpen >= 2 ? (this.config.adaptive?.scalpStackBreakEvenTriggerR ?? 0.15) : (this.config.adaptive?.scalpBreakEvenTriggerR ?? 0.3))
            : (this.config.breakEvenTriggerR ?? 0.5);
          const trailTriggerR = isScalp
            ? (sameSideOpen >= 2 ? (this.config.adaptive?.scalpStackTrailAfterR ?? 0.3) : (this.config.adaptive?.scalpTrailAfterR ?? 0.4))
            : (this.config.trailAfterR ?? 0.6);

          const milestone = extractV25PathMilestone(matched);
          const milestoneSince = Math.max(0, Math.min(matched.createdAt || Date.now(), (position.openedAtMs && position.openedAtMs > 0) ? position.openedAtMs : Date.now()));
          const milestoneTicks = milestone ? tickBuffers.get(position.symbol.toUpperCase()).sinceMs(milestoneSince) : [];
          const milestoneHit = v25MilestoneReached(position.side, milestone, position.priceCurrent, milestoneTicks, milestoneSince);
          if (milestoneHit && !this.v25MilestoneProtectedTickets.has(position.ticket)) {
            const isXauSym = position.symbol.includes('XAU');
            const baselineSpreadPts = spreadBaseline.baseline(position.symbol) ?? 0;
            const baselineSpreadPrice = baselineSpreadPts * tick;
            const beBuffer = Math.max(2.5 * baselineSpreadPrice, isXauSym ? 0.5 : (realEntry * 0.0002));
            const spreadBuffer = Math.max(2.0 * baselineSpreadPrice, isXauSym ? 0.8 : (realEntry * 0.0005));
            const wallBuffer = Math.max(spreadBuffer, isXauSym ? 0.5 : tick * 20);
            const protectAt = milestone?.protectAt ?? milestone?.edge ?? milestone?.price ?? realEntry;
            let targetSl = position.side === 'BUY'
              ? Math.max(realEntry + beBuffer, protectAt - wallBuffer)
              : Math.min(realEntry - beBuffer, protectAt + wallBuffer);

            if (position.side === 'BUY' && targetSl >= position.priceCurrent - spreadBuffer) {
              targetSl = position.priceCurrent - spreadBuffer;
            } else if (position.side === 'SELL' && targetSl <= position.priceCurrent + spreadBuffer) {
              targetSl = position.priceCurrent + spreadBuffer;
            }

            const newSl = roundToTick(targetSl, tick) ?? targetSl;
            const tolerance = realEntry * 0.00005;
            const isImprovement = position.side === 'BUY'
              ? newSl > position.sl + tolerance
              : (position.sl <= 0 || newSl < position.sl - tolerance);

            if (isImprovement) {
              await callBridge('POST', ['/modify', '/mt5/modify', '/api/mt5/modify'], {
                body: { ticket: position.ticket, symbol: position.symbol, sl: newSl },
                priority: 'critical',
              });
              this.v25MilestoneProtectedTickets.add(position.ticket);
              this.insertManagementJournal({
                managementId: `MGMT-V25MILESTONE-${position.ticket}-${Date.now()}`,
                symbol: position.symbol,
                timeframe: matched.timeframe,
                mode: 'BREAKEVEN',
                status: 'EXECUTED',
                side: position.side,
                targetTicket: position.ticket,
                volume: position.volume,
                reason: `V25 path milestone protection: touched ${round2(protectAt)} (${milestone?.stars ?? '?'}★) before TP; protect against wall rejection`,
                result: { newSl, triggerR: round2(r), milestone: protectAt, currentPrice: position.priceCurrent },
              });
              atLog(`[AutoEngine]  V25 Milestone Protect #${position.ticket} ${position.symbol} ${position.side} | wall=${round2(protectAt)} SL->${newSl} r=${round2(r)}`);
              continue;
            }
          }

          if (r >= beTriggerR) {
            // Only move SL toward safety  never away from it.
            // V24.1.2 (2026-05-08): dynamic buffer based on observed spread baseline.
            const isXauSym = position.symbol.includes('XAU');
            const baselineSpreadPts = spreadBaseline.baseline(position.symbol) ?? 0;
            const baselineSpreadPrice = baselineSpreadPts * tick;
            const minBuffer = isXauSym ? 0.5 : (realEntry * 0.0002);
            const buffer = Math.max(2.5 * baselineSpreadPrice, minBuffer);
            
            // BE must be set relative to REAL entry so it actually protects capital + spread
            const targetEntry = position.side === 'BUY' ? realEntry + buffer : realEntry - buffer;

            const tolerance = realEntry * 0.0001;
            const currentSl = position.sl;
            let newSl = roundToTick(targetEntry, tick) ?? targetEntry;

            // Guard against 10016 Invalid Stops: Ensure SL is at least spreadBuffer away from current price
            // V24.1.2: also dynamic — 2x baseline spread, fallback to old constants
            const minSpreadBuffer = isXauSym ? 0.8 : (realEntry * 0.0005);
            const spreadBuffer = Math.max(2.0 * baselineSpreadPrice, minSpreadBuffer);
            if (position.side === 'BUY') {
                if (newSl >= position.priceCurrent - spreadBuffer) {
                    newSl = position.priceCurrent - spreadBuffer;
                    newSl = roundToTick(newSl, tick) ?? newSl;
                }
            } else {
                if (newSl <= position.priceCurrent + spreadBuffer) {
                    newSl = position.priceCurrent + spreadBuffer;
                    newSl = roundToTick(newSl, tick) ?? newSl;
                }
            }

            const isImprovement = position.side === 'BUY'
              ? newSl > currentSl + tolerance
              : (currentSl <= 0 || newSl < currentSl - tolerance);
            if (isImprovement) {
              await callBridge('POST', ['/modify', '/mt5/modify', '/api/mt5/modify'], {
                body: { ticket: position.ticket, symbol: position.symbol, sl: newSl },
                priority: 'critical',
              });
              this.insertManagementJournal({
                managementId: `MGMT-BE-${position.ticket}-${Date.now()}`,
                symbol: position.symbol,
                timeframe: matched.timeframe,
                mode: 'BREAKEVEN',
                status: 'EXECUTED',
                side: position.side,
                targetTicket: position.ticket,
                volume: position.volume,
                reason: `Move stop to break-even after ${round2(r)}R`,
                result: { newSl, triggerR: round2(r) },
              });
              // V24.1.2: include buffer + spread baseline in BE log so user sees the math
              atLog(`[AutoEngine]  Modified Trade #${position.ticket} | Set Break-Even to ${newSl} (realEntry=${realEntry} buffer=${buffer.toFixed(2)} spreadBaseline=${baselineSpreadPts}pts=${baselineSpreadPrice.toFixed(2)})`);
            }
          }
          if (r >= trailTriggerR && !smcAlreadyTrailed) {
            // V24.1.2: trail distance must also respect spread baseline so we don't
            // trail SL within 1 spread-width of price (would close on next tick widen).
            const baselinePtsTrail = spreadBaseline.baseline(position.symbol) ?? 0;
            const baselinePriceTrail = baselinePtsTrail * tick;
            const minTrailFromSpread = 3.0 * baselinePriceTrail; // 3× spread minimum
            const trailDistance = Math.max(
              realEntry * 0.001,
              Math.abs((matched.tp ?? realEntry) - realEntry) / 2,
              minTrailFromSpread
            );
            const rawNewSl = position.side === 'BUY'
              ? Math.max(position.sl, position.priceCurrent - trailDistance)
              : position.sl <= 0
                ? position.priceCurrent + trailDistance
                : Math.min(position.sl, position.priceCurrent + trailDistance);
            const newSl = roundToTick(rawNewSl, tick) ?? rawNewSl;
            const shouldTrail = position.side === 'BUY'
              ? newSl > position.sl + realEntry * 0.00005
              : position.sl <= 0 || newSl < position.sl - realEntry * 0.00005;
            if (shouldTrail) {
              await callBridge('POST', ['/modify', '/mt5/modify', '/api/mt5/modify'], {
                body: { ticket: position.ticket, symbol: position.symbol, sl: newSl },
                priority: 'critical',
              });
              this.insertManagementJournal({
                managementId: `MGMT-TRAIL-${position.ticket}-${Date.now()}`,
                symbol: position.symbol,
                timeframe: matched.timeframe,
                mode: 'TRAIL',
                status: 'EXECUTED',
                side: position.side,
                targetTicket: position.ticket,
                volume: position.volume,
                reason: `Trail stop after ${round2(r)}R`,
                result: { newSl, triggerR: round2(r) },
              });
              // eslint-disable-next-line no-console
              atLog(`[AutoEngine]  Trailed Trade #${position.ticket} | SL brought to ${newSl}`);
            }
          }

          // Situation Correction (Gae Mai) — 2026-04-25 (MEDIUM fix)
          // The previous version called agentOrchestrator.reason() with a faked
          // AnalysisSummary (NEUTRAL/0-confluence) so the LLM had no real data
          // to reason from. That wasted tokens on advice the model couldn't
          // actually justify. Replace with a deterministic banner: if a position
          // is past -1.5R, raise a CRITICAL notification so the user sees it,
          // and let the next runCycle's full MTF analysis decide what to do.
          if (r < -1.5 && this.config.notificationSettings?.onLoss) {
              const drawdownR = r.toFixed(2);
              atWarn(`[AutoEngine] Troubled trade #${position.ticket} ${position.symbol} ${position.side} drawdown=${drawdownR}R — flagged for next-cycle re-analysis`);
              broadcast({
                  type: 'intelligence_alert',
                  title: `TROUBLED TRADE: ${position.symbol}`,
                  message: `Trade #${position.ticket} drawdown ${drawdownR}R. Engine will re-evaluate next cycle.`,
                  priority: 'CRITICAL',
                  at: Date.now()
              });
          }
        }
      }

      this.openJournal = persistenceService.loadOpenJournal();
      this.state = { ...this.state, phase: 'IDLE', openTradesTracked: positions.length, message: 'server manage done' };
      this.persistRuntime();
    } catch (error) {
      this.state = { ...this.state, phase: 'IDLE', message: `manage failed: ${String((error as Error)?.message || error)}` };
      this.persistRuntime();
    } finally {
      this.manageBusy = false;
    }
    });
  }

  private async runLearn(): Promise<void> {
    if (this.learnBusy) return;
    this.learnBusy = true;
    
    // Generate a unique trace ID for this learning cycle
    const traceId = `learn-${Date.now().toString().slice(-6)}`;
    
    await traceStorage.run({ traceId }, async () => {
      try {
      // Market Guard: AI learning can still run while market is closed (offline consolidation),
      // but we prefer to skip if the whole system is in Sleep Mode to save resources.
      const marketOpen = await this.isMarketOpen();
      if (!marketOpen && this.state.phase === 'SLEEPING') {
          return;
      }
      
      // eslint-disable-next-line no-console
      atLog('[AutoEngine]  Running AI Learning Phase...');
      this.state = { ...this.state, phase: 'LEARNING', message: 'server learning from journal' };
      
      // 2026-04-30: Sync newly discovered free models for Smart Fallback
      // 2026-05-01 Phase 2: เปลี่ยนเป็น await + log diff (added/removed/kept)
      //   เพื่อ verify ว่า sync สำเร็จจริง (เคย fail silent ทำให้ DB เห็นแค่ 4 ตัว)
      const orKeys = resolveProviderCredentials(this.config, 'openrouter');
      try {
        const r = await modelRankerService.syncDiscoveredModels(orKeys.apiKey, orKeys.baseUrl);
        atLog(`[AutoEngine] OpenRouter sync done: total=${r.totalFree} added=${r.added} removed=${r.removed} kept=${r.kept}`);
      } catch (err) {
        atWarn(`[AutoEngine] Model sync failed: ${err}`);
      }
      // Phase 2.5 — Schedule daily refresh ของ free pool (OpenRouter เปลี่ยนรายชื่อบ่อย)
      this.scheduleDailyModelSync();

      const rows = await persistenceService.getLearningJournal(400);
      const dataQuality = persistenceService.getLearningQualitySummary();
      const blockStats = persistenceService.getDecisionBlockStats(1000);
      const managementStats = persistenceService.getManagementLearningStats(1000);
      const strategyMap = new Map<string, JournalRow[]>();
      const analyzerMap = new Map<string, JournalRow[]>();

      for (const row of rows.filter((it) => ['WIN', 'LOSS', 'BE'].includes(it.outcome))) {
        const sRows = strategyMap.get(row.strategy) || [];
        sRows.push(row);
        strategyMap.set(row.strategy, sRows);

        const aRows = analyzerMap.get(row.analyzersUsed) || [];
        aRows.push(row);
        analyzerMap.set(row.analyzersUsed, aRows);
      }

      const strategyStats: StrategyStat[] = Array.from(strategyMap.entries()).map(([strategy, list]) => {
        const wins = list.filter((it) => it.outcome === 'WIN').length;
        const losses = list.filter((it) => it.outcome === 'LOSS').length;
        const totalProfit = list.reduce((sum, it) => sum + (it.profit ?? 0), 0);
        const avgR = list.reduce((sum, it) => sum + (it.profitR ?? 0), 0) / Math.max(list.length, 1);
        return { strategy, total: list.length, wins, losses, winRate: wins / Math.max(list.length, 1), totalProfit, avgR: round2(avgR) };
      });

      const analyzerStats: AnalyzerStat[] = Array.from(analyzerMap.entries()).map(([analyzers, list]) => {
        const wins = list.filter((it) => it.outcome === 'WIN').length;
        const totalProfit = list.reduce((sum, it) => sum + (it.profit ?? 0), 0);
        return { analyzers, total: list.length, wins, winRate: wins / Math.max(list.length, 1), totalProfit };
      });

      const totalDeals = strategyStats.reduce((sum, it) => sum + it.total, 0);
      const wins = strategyStats.reduce((sum, it) => sum + it.wins, 0);
      const losses = strategyStats.reduce((sum, it) => sum + it.losses, 0);
      const totalProfit = strategyStats.reduce((sum, it) => sum + it.totalProfit, 0);
      const bestStrategy = strategyStats.slice().sort((a, b) => b.winRate - a.winRate || b.totalProfit - a.totalProfit)[0] ?? null;
      const worstStrategy = strategyStats.slice().sort((a, b) => a.winRate - b.winRate || a.totalProfit - b.totalProfit)[0] ?? null;
      const bestAnalyzer = analyzerStats.slice().sort((a, b) => b.winRate - a.winRate || b.totalProfit - a.totalProfit)[0] ?? null;
      const worstAnalyzer = analyzerStats.slice().sort((a, b) => a.winRate - b.winRate || a.totalProfit - b.totalProfit)[0] ?? null;
      const winRate = wins / Math.max(totalDeals, 1);

      this.learnSummary = {
        totalDeals,
        eligibleDeals: dataQuality.eligible,
        excludedDeals: dataQuality.excluded,
        wins,
        losses,
        winRate,
        totalProfit: round2(totalProfit),
        dataQuality,
        blockStats,
        managementStats,
        bestStrategy: bestStrategy?.strategy ?? null,
        worstStrategy: worstStrategy?.strategy ?? null,
        bestAnalyzerCombo: bestAnalyzer?.analyzers ?? null,
        worstAnalyzerCombo: worstAnalyzer?.analyzers ?? null,
        strategyStats,
        analyzerStats,
        aiNote: '',
        atIso: nowIso(),
      };

      if (this.config.apiKey) {
        const prompt = [
          'Analyze these clean, learning-eligible trade results and management actions:',
          `Performance: ${JSON.stringify(strategyStats)}`,
          `Analyzers: ${JSON.stringify(analyzerStats)}`,
          `Data Quality: ${JSON.stringify(dataQuality)}`,
          `Blocked Decision Mix: ${JSON.stringify(blockStats)}`,
          `Management Actions: ${JSON.stringify(managementStats)}`,
          '',
          'Ignore excluded/legacy rows. Provide a short professional trading floor insight and suggest 1-2 adjustments for the pro-trader agent.',
        ].join('\n');
        try {
          // Use reasoning engine for meta-learning
          const aiNote = await agentOrchestrator.reason(
            this.config,
            'GLOBAL',
            { GLOBAL: { regime: 'UNKNOWN', bias: 'NEUTRAL', confluence: 0, fitness: 0, strategy: 'HOLD_CASH', rationale: prompt, signals: [], atr: null, rsi: null, sma20: null, sma50: null, ema20: null } },
            'Periodic learning'
          );
          this.learnSummary.aiNote = aiNote.text;
        } catch (err) {
          this.learnSummary.aiNote = 'AI Analysis unavailable.';
        }
      } else {
        this.learnSummary.aiNote = totalDeals === 0
            ? 'No closed trades yet on server journal.'
            : winRate < 0.4
              ? 'Server auto trading is in cautious mode. Consider raising confluence or narrowing the watchlist.'
              : 'Server auto trading is stable enough for continued paper validation before wider live usage.';
      }

      if (this.config.autoTune && totalDeals >= 8) {
        const blacklist = new Set(this.config.symbolBlacklist);
        const symbolRows = new Map<string, JournalRow[]>();
        for (const row of rows.filter((it) => ['WIN', 'LOSS', 'BE'].includes(it.outcome))) {
          const list = symbolRows.get(row.symbol) || [];
          list.push(row);
          symbolRows.set(row.symbol, list);
        }
        for (const [symbol, list] of symbolRows.entries()) {
          const lossesOnly = list.length >= this.config.autoBlacklistAfterLosses && list.every((it) => it.outcome === 'LOSS');
          if (lossesOnly) blacklist.add(symbol.toUpperCase());
        }
        const newMinConfluence =
          winRate < 0.38 ? clamp(this.config.minConfluence + 3, 30, 90) :
          winRate > 0.6 ? clamp(this.config.minConfluence - 1.5, 30, 90) :
          this.config.minConfluence;
        this.config = {
          ...this.config,
          minConfluence: round1(newMinConfluence),
          symbolBlacklist: Array.from(blacklist),
        };
        this.persistConfig();
      }

      this.state = { ...this.state, phase: 'IDLE', message: 'server learn done' };
      this.persistRuntime();
    } catch (error) {
      this.state = { ...this.state, phase: 'IDLE', message: `learn failed: ${String((error as Error)?.message || error)}` };
      this.persistRuntime();
    } finally {
      this.learnBusy = false;
    }
    });
  }

  private buildPositionCluster(symbol: string, positions: PositionRow[]): PositionCluster {
    const clusterPositions = positions.filter((it) => it.symbol === symbol);
    const buyVolume = clusterPositions.filter((it) => it.side === 'BUY').reduce((sum, it) => sum + it.volume, 0);
    const sellVolume = clusterPositions.filter((it) => it.side === 'SELL').reduce((sum, it) => sum + it.volume, 0);
    const netVolume = round2(buyVolume - sellVolume);
    const totalProfit = round2(clusterPositions.reduce((sum, it) => sum + it.profit, 0));
    const avgPrice = clusterPositions.length > 0 
      ? clusterPositions.reduce((sum, it) => sum + it.priceOpen * it.volume, 0) / (buyVolume + sellVolume || 1)
      : 0;
    
    return {
      symbol,
      positions: clusterPositions,
      winningPositions: clusterPositions.filter((it) => it.profit > 0).sort((a, b) => b.profit - a.profit),
      losingPositions: clusterPositions.filter((it) => it.profit < 0).sort((a, b) => a.profit - b.profit),
      buyVolume: round2(buyVolume),
      sellVolume: round2(sellVolume),
      totalVolume: round2(buyVolume + sellVolume),
      netVolume,
      totalProfit,
      avgPrice: round2(avgPrice),
      netSide: netVolume > 0 ? 'BUY' : netVolume < 0 ? 'SELL' : 'FLAT',
      bias: netVolume > 0 ? 'BULL' : netVolume < 0 ? 'BEAR' : 'NEUTRAL',
    };
  }

  private buildHistoryContext(cluster: PositionCluster, playbook?: PlaybookScores): string {
    return tradeManagementService.buildHistoryContext(cluster, playbook, this.learnSummary);
  }

  private positionRiskR(position: PositionRow, journal: JournalRow | undefined): number {
    return tradeManagementService.positionRiskR(position, journal);
  }

  private normalizeClosedJournalOutcome(row: JournalRow): JournalRow {
    return tradeManagementService.normalizeClosedJournalOutcome(row);
  }

  private clampFraction(input: number, min: number, max: number, fallback: number): number {
    if (!isFinite(input) || input <= 0) return fallback;
    return clamp(input, min, max);
  }

  private normalizeCloseVolume(requested: number | null | undefined, positionVolume: number, mode: ManagementMode): number | null {
    return tradingExecutionService.normalizeCloseVolume(requested, positionVolume, mode, this.config);
  }

  private planMarketAwareManagement(
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

  private computePlaybookScores(
    cfg: AutoTradingConfig,
    account: AccountSnapshot,
    cluster: PositionCluster,
    analysis: any
  ): PlaybookScores {
    return tradeManagementService.computePlaybookScores(cfg, account, cluster, analysis, this.openJournal, this.learnSummary);
  }

  private async executeManagementPlan(
    symbol: string,
    plan: ManagementPlan,
    sl: number | null,
    tp: number | null,
    aiDecision: any
  ): Promise<{ executed: boolean; riskGate: string; ticket: number | null; side?: 'BUY' | 'SELL' }> {
    return tradingExecutionService.executeManagementPlan(
      symbol,
      plan,
      sl,
      tp,
      aiDecision,
      this.config,
      this.fetchPositions.bind(this),
      persistenceService.upsertJournal.bind(persistenceService),
      this.openJournal
    );
  }

  private insertManagementJournal(args: any): void {
    persistenceService.insertManagementJournal(args);
  }

  private markManagementOutcome(
    ticket: number,
    decisionId: string,
    outcome: 'WIN' | 'LOSS' | 'BE',
    profit: number,
    profitR: number | null,
    closeReason: string
  ): void {
    persistenceService.markManagementOutcome(ticket, decisionId, outcome, profit, profitR, closeReason);
  }

  private matchTicketFromOpenPositions(row: JournalRow, positions: PositionRow[]): number | null {
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

  private logDeferredClosedDeal(ticket: number | string, symbol: string, purpose: string): void {
    const key = `${purpose}:${ticket}`;
    const now = Date.now();
    const last = this.closedDealDeferredLastLogAt.get(key) ?? 0;
    if (now - last < 60_000) return;
    this.closedDealDeferredLastLogAt.set(key, now);
    atWarn(`[AutoEngine]  Closed position #${ticket} ${symbol} is no longer open, but no usable closing history deal was found yet. Deferring ${purpose}.`);
  }

  /**
   * Phase 2: Post-Mortem Detection
   * Compares currently open positions with the tracked journal.
   * Any tracked trade that is no longer open is reviewed after a usable
   * closing history deal appears, so entry deals cannot produce false BE labels.
   */
  private async detectAndReviewClosedTrades(livePositions: PositionRow[]): Promise<void> {
    const closedRows = this.openJournal.filter(j => 
        j.outcome === 'OPEN' && 
        j.mt5Ticket !== null && 
        !livePositions.some(p => p.ticket === j.mt5Ticket) &&
        !this.postMortemReviewedTickets.has(String(j.mt5Ticket))
    );

    if (closedRows.length === 0) return;

    const history = await this.fetchHistory(500).catch((err) => {
      atWarn(`[AutoEngine]  Post-Mortem history fetch failed: ${String((err as Error)?.message || err)}`);
      return [] as Record<string, unknown>[];
    });
    const reviewableRows = closedRows.filter((row) => {
      const deal = matchHistoryDeal(row, history);
      if (!deal) {
        this.logDeferredClosedDeal(row.mt5Ticket!, row.symbol, 'post-mortem');
        return false;
      }
      this.closedDealDeferredLastLogAt.delete(`post-mortem:${row.mt5Ticket}`);
      return true;
    });

    if (reviewableRows.length === 0) return;

    // V23.0 — EA-only mode: bypass Post-Mortem AI review when disabled
    const aiModeEnabled = this.config.enableAiMode !== false;
    if (!aiModeEnabled) {
      atLog(`[AutoEngine]  EA-Only mode active. Skipping Post-Mortem AI review for ${reviewableRows.length} trade(s).`);
      for (const row of reviewableRows) {
        this.postMortemReviewedTickets.add(String(row.mt5Ticket));
        this.markManagementOutcome(
            row.mt5Ticket!, 
            row.decisionId, 
            'BE', 
            0, 
            0, 
            'Skipped Post-Mortem AI review (EA-Only mode)'
        );
      }
      return;
    }

    // eslint-disable-next-line no-console
    atLog(`[AutoEngine]  Detected ${reviewableRows.length} closed trade(s). Triggering Post-Mortem...`);

    if (!this.postMortem) this.postMortem = new PostMortemAgent(this.config.apiKey || '');

    for (const row of reviewableRows) {
        try {
            // 1. Fetch historical deal data for accurate PnL if possible, or use last known
            // For now, we use the row data which might be slightly stale but contains the plan.
            const lesson = await this.postMortem.review(row, this.config);
            
            // eslint-disable-next-line no-console
            atLog(`[AutoEngine]  Post-Mortem #${row.mt5Ticket}: ${lesson.outcome} | ${lesson.lesson}`);
            this.postMortemReviewedTickets.add(String(row.mt5Ticket));
            
            // 2. Mark outcome in DB (PersistenceService)
            // We use 0 for profit here because we don't have the final deal yet, 
            // but the normalize step in Learning phase will fix it.
            this.markManagementOutcome(
                row.mt5Ticket!, 
                row.decisionId, 
                lesson.outcome as any, 
                0, 
                lesson.profitR, 
                lesson.lesson
            );

            // 2.1 Update Model Performance Statistics [2026-04-30]
            if (row.modelId) {
                modelRankerService.recordTradeOutcome(row.modelId, lesson.profitR);
            }
            // 2.2 V20.0 — Update slTpAgent accuracy score (per-agent)
            try {
              const snap = safeJsonParse<any>(row.marketSnapshot || '{}', {});
              const slTpModelId = snap?.slTpModelId;
              if (slTpModelId) {
                const plannedRrr = row.rrr || 1.5;
                const actualR = lesson.profitR;
                let delta = 0;
                let reason = '';

                if (lesson.outcome === 'WIN') {
                  if (actualR >= plannedRrr * 0.95) {
                    delta = 0.2; // Hit target TP exactly
                    reason = 'TP Bullseye';
                  } else {
                    delta = 0.1; // Closed in profit (partial or early)
                    reason = 'Profit achieved';
                  }
                } else if (lesson.outcome === 'LOSS') {
                  if (lesson.wasGoodDecision) {
                    // Entry was good, but SL was hit. 
                    // This is the most critical failure for slTpAgent.
                    delta = -0.2; 
                    reason = 'Good entry, but SL hit (Too tight?)';
                  } else {
                    // Entry was bad. Analyst is more to blame.
                    delta = -0.05;
                    reason = 'Bad analysis/entry';
                  }
                } else if (lesson.outcome === 'BE') {
                  delta = 0.05; // Better than loss, preserved capital
                  reason = 'Capital protected (BE)';
                }

                if (delta !== 0) {
                  modelRankerService.recordSlTpAccuracy(slTpModelId, delta);
                  atLog(`[AutoEngine] SL/TP Accuracy update for "${slTpModelId}": delta=${delta > 0 ? '+' : ''}${delta} | ${reason} (R=${actualR.toFixed(2)}, Target=${plannedRrr})`);
                }
              }
            } catch (err) {
              atWarn(`[AutoEngine] recordSlTpAccuracy failed: ${err}`);
            }

            // 3. Store lesson in episodic memory (vectorStore) [Phase 4]
            const lessonText = `Trade #${row.mt5Ticket} (${row.symbol}): ${lesson.lesson} | Outcome: ${lesson.outcome} | R: ${lesson.profitR}`;
            const embedding = await agentOrchestrator.getEmbeddingForConfig(this.config, lessonText);
            vectorStore.add(embedding, {
                id: `lesson_${Date.now()}_${row.mt5Ticket}`,
                symbol: row.symbol,
                timeframe: row.timeframe,
                type: 'trade_lesson',
                outcome: lesson.outcome,
                profitR: lesson.profitR,
                text: lesson.lesson,
                timestamp: Date.now()
            });

            // 4. Store lesson in knowledge graph [Future Phase 4-]
            // knowledgeGraph.addLesson(row.symbol, lesson.lesson, lesson.tags);

            broadcast({
                type: 'intelligence_alert',
                title: `TRADE POST-MORTEM: ${row.symbol}`,
                message: `Trade #${row.mt5Ticket} closed as ${lesson.outcome}. AI Lesson: ${lesson.lesson}`,
                priority: 'LOW',
                at: Date.now()
            });

        } catch (err) {
            atError(`[AutoEngine] Post-Mortem failed for #${row.mt5Ticket}:`, err);
        }
    }

    // Refresh local journal after marking outcomes
    this.openJournal = persistenceService.loadOpenJournal();
  }

  private buildSkipDecision(
    symbol: string,
    regime: MarketRegime,
    bias: Bias,
    confluence: number,
    strategy: StrategyType,
    rationale: string,
    riskGate: string,
    translatedTh: string | null = null
  ): CycleDecision {
    return tradeManagementService.buildSkipDecision(symbol, regime, bias, confluence, strategy, rationale, riskGate, translatedTh);
  }

  private computeVolume(account: AccountSnapshot, stopDistance: number, symbol: string, strategy?: string): number {
    return tradingExecutionService.computeVolume(account, stopDistance, symbol, this.config, strategy, this.learnSummary);
  }

  private async fetchAccount(): Promise<AccountSnapshot> {
    return marketDataService.fetchAccount();
  }

  private async fetchPositions(): Promise<PositionRow[]> {
    return marketDataService.fetchPositions();
  }

  private async fetchHistory(limit: number): Promise<Record<string, unknown>[]> {
    return marketDataService.fetchHistory(limit);
  }

  private async fetchCandles(symbol: string, timeframe: string, count: number) {
    return marketDataService.fetchCandles(symbol, timeframe, count);
  }

  private async fetchSMC(symbol: string, timeframe: string, count = 300): Promise<any> {
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

  /**
   * Extract the broker-side identifier for the just-opened position.
   */
  private extractTicket(input: unknown): number | null {
    return tradingExecutionService.extractTicket(input);
  }

  // 2026-04-26 — per-symbol open/close detection.
  // The previous global isMarketOpen() looked at calendar weekday + a single
  // probe symbol, so a closed XAUUSD made the whole engine sleep even when
  // BTCUSD/ETHUSD were trading 24/7. Now we ask the broker per symbol.
  // Cache for 60 s so we don't hammer /symbol_info every cycle.
  private _tradableCache: Map<string, { at: number; tradable: boolean; reason: string }> = new Map();
  private static readonly TRADABLE_TTL_MS = 60_000;

  /**
   * True iff the broker reports `trade_mode==4` (FULL) and the last tick is
   * fresh (within 10 min). Stale ticks usually mean the session is closed
   * even if trade_mode reports something other than 0.
   */
  public async isSymbolTradable(symbol: string): Promise<{ tradable: boolean; reason: string }> {
    const upper = String(symbol || '').toUpperCase();
    if (!upper) return { tradable: false, reason: 'empty symbol' };
    const hit = this._tradableCache.get(upper);
    if (hit && Date.now() - hit.at < AutoTradingService.TRADABLE_TTL_MS) {
      return { tradable: hit.tradable, reason: hit.reason };
    }
    let tradable = true;
    let reason = 'tradable';
    try {
      const result = await callBridge('GET', ['/symbol_info'], { query: { symbol: upper }, timeoutMs: 5000 });
      const outer = asRecord(result.data);
      const inner = asRecord((outer as any).data ?? unwrapData(result.data));
      const info = Object.keys(inner).length > 0 ? inner : outer;
      const mode = Number((info as any).trade_mode ?? 4);
      if (mode === 0) { tradable = false; reason = 'trade_mode=0 (disabled)'; }
      else {
        const tickT = Number((info as any).time ?? 0);
        if (tickT > 0) {
          const ageSec = (Date.now() / 1000) - tickT;
          if (ageSec > 600) { tradable = false; reason = `stale tick (${Math.round(ageSec)}s old)`; }
        }
      }
    } catch (err) {
      reason = 'bridge probe failed — assuming tradable';
    }
    this._tradableCache.set(upper, { at: Date.now(), tradable, reason });
    return { tradable, reason };
  }


  // --- Private helpers (delegates) ---

  /**
   * Returns true if at least one watchlist symbol is currently tradable.
   * Used as a gate before running market cycles and management loops.
   */
  private async isMarketOpen(): Promise<boolean> {
    const watchlist = this.config?.watchlist ?? [];
    if (watchlist.length === 0) return true;
    const results = await Promise.all(
      watchlist.map(sym => this.isSymbolTradable(sym).catch(() => ({ tradable: true, reason: '' }))),
    );
    return results.some(r => r.tradable);
  }

  /** Re-read config from DB (used for hot-reload every 60 s). */
  private loadConfig(): AutoTradingConfig {
    return persistenceService.loadConfig();
  }

  /** Load candles for a symbol + timeframe. Wraps MarketDataService. */
  private async loadSymbolCandles(symbol: string, timeframe: string, count = 200): Promise<any[]> {
    return marketDataService.loadSymbolCandles(symbol, timeframe, count);
  }


  /**
   * Load candles for a cross-asset symbol that may not be in the broker watchlist.
   * Returns an empty array instead of throwing when the symbol is unavailable.
   */
  private async loadOptionalCandles(symbol: string, timeframe: string, count = 60): Promise<any[]> {
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
  private getTfMs(tf: string): number {
    const m = tf.match(/([MHD])(\d+)?/);
    if (!m) return 60000;
    const unit = m[1];
    const val = parseInt(m[2] || '1');
    if (unit === 'M') return val * 60000;
    if (unit === 'H') return val * 3600000;
    if (unit === 'D') return val * 86400000;
    return 60000;
  }
}

export const autoTradingService = new AutoTradingService();

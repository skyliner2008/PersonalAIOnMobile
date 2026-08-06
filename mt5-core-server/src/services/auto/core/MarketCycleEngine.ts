import { traceStorage } from '../../logger.js';
import { db } from '../../../db.js';
import { asArray, callBridge } from '../../bridgeClient.js';
import { parseCandles } from '../../tracking.js';
import { inferAnalysis, pickStrategy } from '../analysis.js';
import { gateTrade } from '../risk.js';
import { agentOrchestrator } from '../agentOrchestrator.js';
import { vectorStore } from '../vectorStore.js';
import { broadcast } from '../../mt5RealtimeHub.js';
import { analyzeCorrelation } from '../analyzers/correlation.js';
import { newsAnalyzer, NewsAnalyzer } from '../analyzers/news.js';
import { crossAssetAnalyzer } from '../analyzers/crossAsset.js';
import { 
  cycleDurationSeconds, 
  tradesPlacedTotal, 
  tradesRejectedTotal, 
  zoneGateBlocksTotal, 
  slBufferActivationsTotal, 
  counterTrendBlocksTotal, 
  entryZoneDistribution,
  managementEventsTotal
} from '../../metrics.js';
import { persistenceService, defaultConfig, defaultState } from './PersistenceService.js';
import { 
  classifyPremiumDiscount, 
  getActiveFVGs, 
  isSupportedBySMC, 
  getSmcSnapshotV2,
  getSmcStructure
} from '../analyzers/smc.js';
import { deterministicDecide, llmCache, marketStateHashLoose } from '../deterministicEngine.js';
import { 
  resolveProviderCredentials, 
  resolveAgentChoice, 
  AgentRole 
} from '../modelResolver.js';
import { spreadBaseline } from '../spreadBaseline.js';
import { modelRankerService } from './ModelRankerService.js';
import { eventBus } from './EventBus.js';
import { indicatorPipeline } from './IndicatorPipeline.js';
import { detectEntrySignals } from './SignalDetector.js';
import { agentCoordinator } from './AgentCoordinator.js';
import { improvedAntiHedge } from './SmcTrailManager.js';
import { analyzePath } from '../analyzers/smc/priceMapBuilder.js';
import { evaluateProximityGate, computeAdaptiveProximityMaxPip } from './ProximityGate.js';
import { resolveDecisionSide } from './DecisionGuards.js';
import { 
  buildZoneLayerContext, 
  findAlignedFvgFill, 
  findOpposingScalpPressure, 
  hasAlignedFvgNearPrice 
} from './ZoneAwareGate.js';
import { 
  extractV25PathMilestone, 
  v25MilestoneReached 
} from './V25MilestoneProtection.js';
import { wallStateMachine } from '../v25/state/WallStateMachine.js';
import { bootstrapV25ShadowFromConfig, shutdownV25Shadow } from '../v25/index.js';
import { tickBuffers } from '../v25/TickBuffer.js';
import { 
  executableMinRrrForPlan, 
  liveMarketRiskIssue, 
  playbookSelector 
} from '../v25/playbooks/index.js';
import {
  compactAnalysisForLog,
  compactMtfForLog,
  compactDeterministicForLog,
  classifyDecisionBlockCategory,
} from '../helpers/decisionLogger.js';
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
} from '../helpers/strategySelector.js';
import {
  buildPositionCluster,
  clampFraction,
  extractTicket,
  getTfMs,
  buildSkipDecision,
} from '../helpers/tradingHelpers.js';
import {
  isSymbolTradable,
  isMarketOpen,
} from '../helpers/marketHelpers.js';
import {
  closedTradeService,
} from '../helpers/closedTradeService.js';
import { 
  atLog, 
  atWarn, 
  atError, 
  getLogTime, 
  nowIso, 
  safeJsonParse, 
  unwrapData, 
  asRecord, 
  asNumber, 
  asString, 
  round2, 
  round1, 
  clamp, 
  moneyPerPriceUnit, 
  tickSize, 
  roundToTick, 
  normalizeConfig 
} from '../utils.js';

import { AnalystAgent } from '../agents/analyst.js';
import { RiskOfficerAgent } from '../agents/riskOfficer.js';
import { ExecutionAgent } from '../agents/executionTrader.js';
import { parseLlmJson } from '../agents/jsonParse.js';
import { slTpAnalystAgent, pickSlTpDeterministic } from '../agents/slTpAnalyst.js';
import { tradeManagementService } from './TradeManagementService.js';
import { marketDataService } from './MarketDataService.js';
import { tradingExecutionService } from './TradingExecutionService.js';
import { memoryConsolidationService } from '../memoryConsolidation.js';
import { CircuitBreaker } from '../risk/circuitBreaker.js';

import type { AutoTradingService } from '../../autoTradingService.js';
import type {
  CycleDecision,
  PositionRow,
  JournalRow,
  PlaybookScores,
  StrategyType,
  ManagementPlan
} from '../types.js';

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


export class MarketCycleEngine {
    public static async runCycle(context: AutoTradingService): Promise<void> {
    if (context.cycleBusy) return;
    context.cycleBusy = true;
    
    // Generate a unique trace ID for this market cycle
    const traceId = `cycle-${context.state.cycleCount + 1}-${Date.now().toString().slice(-4)}`;
    
    await traceStorage.run({ traceId }, async () => {
      const timer = cycleDurationSeconds.startTimer();
      try {
      // eslint-disable-next-line no-console
      atLog('[AutoEngine]  Running Market Cycle...');

      // Market Guard: Check if markets are open
      const marketOpen = await isMarketOpen(context.config);
      if (!marketOpen) {
          if (context.state.phase !== 'SLEEPING') {
              // eslint-disable-next-line no-console
              atLog('[AutoEngine]  All watchlist symbols are CLOSED. Entering Sleep Mode & Brain Consolidation...');
              context.state = { ...context.state, phase: 'SLEEPING', message: 'Market closed. AI is consolidating memory.' };
              context.persistRuntime();
              
              // Trigger Sleep Cycle
              memoryConsolidationService.runSleepCycle(context.config).catch((err: any) => {
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
      const positions = await context.fetchPositions();
      await closedTradeService.detectAndReviewClosedTrades(
          positions,
          context.config,
          context.openJournal,
          context.fetchHistory.bind(this),
          context.markManagementOutcome.bind(this),
          () => { context.openJournal = persistenceService.loadOpenJournal(); }
      );

      // --- Circuit Breaker Check [Phase 3] ---
      const account = await context.fetchAccount();
      const breaker = CircuitBreaker.check(account, context.config);
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
              context.config.enableLiveTrading = false; // Emergency disable
              context.state.status = 'PAUSED';
              context.state.message = `Circuit Breaker: ${breaker.reason}`;
              context.persistConfig();
              context.persistRuntime();
              return;
          }
      }

      // --- News Intelligence Phase [Phase 1] ---
      // Derive the set of quote currencies from the active watchlist and
      // refresh news risk for each one. `updateNewsRisk` mutates the SHARED
      // newsRisk.* fields, so we intentionally call it sequentially and keep
      // the NEAREST (min minutesToEvent) HIGH-impact hit across currencies.
      if (context.config.newsRisk?.enabled) {
          try {
              const newsWatchlist = (((context.config as any).watchlist ?? (context.config as any).symbols ?? defaultConfig.watchlist) as unknown[])
                .map((item) => String(item).trim().toUpperCase())
                .filter(Boolean);
              const currencies = NewsAnalyzer.currenciesForSymbols(newsWatchlist);
              if (currencies.length === 0) {
                  // Fallback to USD if watchlist didn't produce any mapped currency
                  await newsAnalyzer.updateNewsRisk('USD', context.config);
              } else {
                  let best: { event?: string; impact?: 'LOW' | 'MEDIUM' | 'HIGH'; minutes?: number } = {};
                  for (const ccy of currencies) {
                      await newsAnalyzer.updateNewsRisk(ccy, context.config);
                      const riskNow = context.config.newsRisk;
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
                  if (best.impact && context.config.newsRisk) {
                      context.config.newsRisk.activeEvent = best.event;
                      context.config.newsRisk.impactLevel = best.impact;
                      context.config.newsRisk.minutesToEvent = best.minutes;
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
        if (Date.now() - context.lastConfigReloadAt > CONFIG_RELOAD_INTERVAL_MS) {
          context.config = context.loadConfig();
          context.lastConfigReloadAt = Date.now();
          // V25: Ensure shadow pipeline is in sync with reloaded config
          const v25cfg = (context.config as any).adaptive?.v25 || {};
          if (v25cfg.enabled) {
            const v25Watchlist = (((context.config as any).watchlist ?? (context.config as any).symbols ?? defaultConfig.watchlist) as unknown[])
              .map((item) => String(item).trim().toUpperCase())
              .filter(Boolean);
            bootstrapV25ShadowFromConfig(v25cfg, v25Watchlist);
          }
        }
      } catch (reloadErr) {
        atWarn('[AutoEngine] config reload failed, using cached copy:', reloadErr);
      }
      const cfg = context.config;
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
          const activeTF = context.symbolTimeframes.get(upper) || cfg.timeframe || 'H1';
          const tfMs = getTfMs(activeTF);
          const now = Date.now();

          if (!context.mtfCache.has(upper)) context.mtfCache.set(upper, new Map());
          if (!context.lastTfUpdate.has(upper)) context.lastTfUpdate.set(upper, new Map());
          
          const symCache = context.mtfCache.get(upper)!;
          const symLastUpdate = context.lastTfUpdate.get(upper)!;
          const lastUpdate = symLastUpdate.get(activeTF) || 0;

          // Refresh if: 1. No cache 2. Wall-clock crossed a TF boundary
          const needsRefresh = !symCache.has(activeTF) || (Math.floor(now / tfMs) > Math.floor(lastUpdate / tfMs));

          if (needsRefresh) {
              try {
                  const tfCandles = await context.loadSymbolCandles(upper, activeTF, 180);
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

      context.state = { ...context.state, phase: 'ANALYZING', lastTickAt: nowIso(), message: 'server analyzing' };
      context.persistRuntime();

      await Promise.all(cfgWatchlist.map(async (symbol) => {
        const upper = symbol.toUpperCase();
        if (cfgSymbolBlacklist.has(upper)) {
          decisions.push(buildSkipDecision(upper, 'UNKNOWN', 'NEUTRAL', 0, 'HOLD_CASH', `SKIP ${upper} blacklisted`, 'symbol blacklisted'));
          return;
        }
        // 2026-04-26 — per-symbol tradability gate. Skip closed markets so
        // crypto can still trade while FX/Metals are off-hours.
        const tradeStatus = await isSymbolTradable(upper);
        if (!tradeStatus.tradable) {
          decisions.push(buildSkipDecision(upper, 'UNKNOWN', 'NEUTRAL', 0, 'HOLD_CASH', `SKIP ${upper}: ${tradeStatus.reason}`, 'market_closed', `ตลาดปิดอยู่: ${tradeStatus.reason}`));
          return;
        }
        // Determine active timeframe for this symbol (default to config, or what AI suggested last)
        let activeTF = context.symbolTimeframes.get(upper) || cfg.timeframe || 'H1';
        
        // --- Multi-Timeframe Smart Loading ---
        const analyses: Record<string, any> = {};
        let primaryAnalysis: any = null;
        let candles: any[] = [];
        let eaSignals: any[] = [];

        try {
            const tfsToLoad = [...new Set(['H4', 'H1', 'M30', 'M15', 'M5', 'M1', activeTF])];
            const candlesByTf = new Map<string, any[]>();
            const now = Date.now();

            const symCache = context.mtfCache.get(upper)!;
            const symLastUpdate = context.lastTfUpdate.get(upper)!;

            for (const tf of tfsToLoad) {
                const tfMs = getTfMs(tf);
                const lastUpdate = symLastUpdate.get(tf) || 0;
                
                // Refresh if: 1. No cache 2. Wall-clock crossed a TF boundary
                const needsRefresh = !symCache.has(tf) || (Math.floor(now / tfMs) > Math.floor(lastUpdate / tfMs));
                
                let tfData = symCache.get(tf);

                if (needsRefresh) {
                    const tfCandles = await context.loadSymbolCandles(upper, tf, 200);
                    if (tfCandles && tfCandles.length >= 20) {
                        const summary = inferAnalysis(tfCandles);
                        
                        // V20.0 — Fetch SMC from bridge for structural TFs on refresh
                        if (tf === 'H4' || tf === 'H1' || tf === activeTF) {
                            summary.smc = await context.fetchSMC(upper, tf, 300);
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
            return;
        }
        
        const primaryAnalysisCfg = analyses[cfg.timeframe] || analyses['M15'] || analyses['H4'];
        if (!primaryAnalysis || !primaryAnalysis.atr) {
          atWarn(`   ${upper}: missing M15/H4 analysis or atr -> skipping (M15=${!!analyses['M15']}, H4=${!!analyses['H4']}, atr=${!!primaryAnalysis?.atr})`);
          return;
        }

        context.state = { ...context.state, message: `Analyzing ${upper} (MTF)...` };
        context.persistRuntime();

        // Fix: positions is a separate variable, not a property of account
        const symbolPositions = positions.filter((it) => it.symbol === upper);
        const cluster = buildPositionCluster(upper, symbolPositions);

        // eslint-disable-next-line no-console
        atLog(`   ${upper}: MTF Analysis ready (${Object.keys(analyses).join(',')})`);

        const analysis = primaryAnalysis; // Use active TF as base for technical rules
        let last = candles.length > 0 ? candles[candles.length - 1] : null;
        const originalLastClose = Number(last?.c);

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
            return;
        }

        const freshDecisionPrice = resolveFreshDecisionPrice(upper, symbolInfo, Number(last.c));
        if (
          freshDecisionPrice.source !== 'candle_close' &&
          Number.isFinite(freshDecisionPrice.price) &&
          freshDecisionPrice.price > 0
        ) {
          const staleDelta = Math.abs(freshDecisionPrice.price - Number(last.c));
          const minLogDelta = Math.max(point * 10, Math.abs(Number(last.c)) * 0.00005);
          last = {
            ...last,
            c: freshDecisionPrice.price,
            h: Math.max(Number(last.h ?? last.c), freshDecisionPrice.price),
            l: Math.min(Number(last.l ?? last.c), freshDecisionPrice.price),
          };
          if (staleDelta >= minLogDelta) {
            const ageNote = freshDecisionPrice.ageMs !== undefined ? ` age=${freshDecisionPrice.ageMs}ms` : '';
            atLog(`   ${upper}: Decision price synced: candleClose=${originalLastClose.toFixed(2)} -> ${freshDecisionPrice.price.toFixed(2)} (${freshDecisionPrice.source}${ageNote})`);
          }
        }

        const playbookScores = tradeManagementService.computePlaybookScores(cfg, account, cluster, analysis, context.openJournal, context.learnSummary);
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

        let historyContext = context.buildHistoryContext(cluster, playbookScores);
        if (atHardCap) {
          historyContext = `[CRITICAL MANAGEMENT DIRECTIVE: ${upper} AT PER-SYMBOL CAP (${symbolPositions.length}/${perSymbolCap})] ` +
            `WE CANNOT OPEN A NEW DIRECTIONAL TRADE FOR ${upper}. ` +
            `FOCUS ON MANAGING THE CURRENT ${symbolPositions.length} POSITIONS TO HARVEST PROFIT OR REDUCE LOSS. ` +
            `Your suggested Action should be 'SKIP' unless you are HEDGING (defense allowance up to ${defenseSymbolCap}). ` +
            historyContext;
        }

        let aiDecision: any = null;
        const aiAvailable = !!cfg.apiKey && Date.now() > context.aiCooldownUntil;
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
        const totalHeatR = context.openJournal
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
          const lastJournal = context.openJournal.find(j => j.mt5Ticket === lastPos.ticket);
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
          enableZoneAwareGate: (cfg.adaptive?.enableZoneAwareGate ?? true) && isGateEnabled(cfg, 'unifiedZoneGate'),
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
                detDecision.strategy = bestSignal.strategy as StrategyType;
                detDecision.rationale = `V21.0 EA Signal: ${bestSignal.strategy} ${bestSignal.confluenceStars}★`;
                detDecision.rationale_th = `สัญญาณ V21.0 EA: ${bestSignal.strategy} ${bestSignal.confluenceStars}★`;
                primaryAnalysis.strategy = bestSignal.strategy; // Update for downstream logic
                atLog(`   ${upper}: 🤖 V21.0 EA Signal applied to pipeline → ${detDecision.action} conf=${detDecision.confidence}%`);
            }
        }

        // V26.19: Unified execution path. V25/PB candidates are no longer a
        // second order sender gated by "V25 Only"; they become local-proof
        // evidence inside the same AutoEngine decision pipeline.
        const unifiedV25Plan = selectUnifiedV25Candidate(upper, cfg, detDecision);
        if (unifiedV25Plan) {
            const v25Strategy = v25StrategyForPlan(unifiedV25Plan);
            const v25Confidence = clamp(
              72 + (unifiedV25Plan.wallStars * 4) + (unifiedV25Plan.oppositeWallStars * 2) + Math.max(0, unifiedV25Plan.rrr - 1.35) * 5,
              72,
              94,
            );
            const reason = v25PlanReason(unifiedV25Plan);
            const previousAction = detDecision.action;
            const previousStrategy = detDecision.strategy;
            
            const missing = ['H4', 'H1', 'M30', 'M15', 'M5', 'M1'].filter(tf => !analyses[tf]);
            if (missing.length > 0) {
              atWarn(`   ${upper}: missing timeframes (${missing.join(', ')}) -> skipping`);
              pushDecisionTrace(gateTrace, 'missing_required_timeframe', 'BLOCK', `Missing required timeframe: ${missing.join(', ')}`);
              return;
            }
            
            detDecision = {
              ...detDecision,
              action: unifiedV25Plan.side,
              confidence: Math.max(detDecision.confidence ?? 0, round1(v25Confidence)),
              strategy: v25Strategy,
              rationale: `V25 unified candidate selected over ${previousAction}/${previousStrategy}: ${reason}`,
              rationale_th: `V25 unified: ใช้ local proof จาก ${unifiedV25Plan.playbook} ${unifiedV25Plan.side} แทนสัญญาณเดิม ${previousAction}/${previousStrategy}`,
              timeframe: 'M1',
              size_fraction: Math.min(detDecision.size_fraction ?? 0.5, 0.5),
              counterTrend: false,
              htfAligned: false,
              needsLlmSecondOpinion: false,
              sl: unifiedV25Plan.sl,
              tp: unifiedV25Plan.tp,
            };
            primaryAnalysis.strategy = v25Strategy;
            atLog(`   ${upper}: V25 unified candidate applied → ${unifiedV25Plan.side} ${v25Strategy} conf=${detDecision.confidence}% entry=${unifiedV25Plan.entry.toFixed(2)} sl=${unifiedV25Plan.sl.toFixed(2)} tp=${unifiedV25Plan.tp.toFixed(2)} rrr=${unifiedV25Plan.rrr.toFixed(2)}`);
            pushDecisionTrace(gateTrace, 'v25_unified_candidate', 'ALLOW', reason, {
              side: unifiedV25Plan.side,
              strategy: v25Strategy,
              playbook: unifiedV25Plan.playbook,
              entry: round2(unifiedV25Plan.entry),
              sl: round2(unifiedV25Plan.sl),
              tp: round2(unifiedV25Plan.tp),
              rrr: round2(unifiedV25Plan.rrr),
              previousAction,
              previousStrategy,
            });
        }

        atLog(`   ${upper}: Deterministic  ${detDecision.action} (${detDecision.confidence}%) ${detDecision.counterTrend ? '[CT]' : ''} mgmt=${detDecision.management} zone=${detDecision.zoneAtEntry} stateHash=${detDecision.stateHash.substring(0, 40)}`);
        pushDecisionTrace(gateTrace, 'deterministic', detDecision.action === 'SKIP' ? 'SKIP' : 'INFO', detDecision.rationale, {
          action: detDecision.action,
          confidence: detDecision.confidence,
          management: detDecision.management,
          zoneAtEntry: detDecision.zoneAtEntry,
          counterTrend: Boolean(detDecision.counterTrend),
        });

        const detStrategyDisabled = disabledStrategyReason(cfg, detDecision.strategy ?? primaryAnalysis.strategy);
        if (detStrategyDisabled && detDecision.action !== 'SKIP') {
          atWarn(`   ${upper}: ${detStrategyDisabled}. Forced deterministic SKIP.`);
          pushDecisionTrace(gateTrace, 'strategy_toggle', 'BLOCK', detStrategyDisabled, {
            strategy: detDecision.strategy ?? primaryAnalysis.strategy,
          });
          detDecision = {
            ...detDecision,
            action: 'SKIP',
            confidence: 0,
            rationale: detStrategyDisabled,
            rationale_th: detStrategyDisabled,
          };
        }
        
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
        if (isGateEnabled(cfg, 'preAiPositionCapGate') && atHardCap && !isDefensiveMgmt && detDecision.action !== 'SKIP') {
          aiDecision = {
            action: 'SKIP',
            management: detDecision.management,
            size_fraction: 0,
            confidence: 0,
            rationale: `[DET] ${upper} per-symbol cap full (${symbolPositions.length}/${perSymbolCap}) and no defensive intent — token-saving SKIP`,
            rationale_th: `[ ] ${upper} เต็ม cap ${symbolPositions.length}/${perSymbolCap} และไม่ต้องป้องกัน — ข้าม LLM`,
            timeframe: detDecision.timeframe,
            strategy: detDecision.strategy ?? primaryAnalysis.strategy,
            _source: 'pre_ai_cap_gate',
          };
          atLog(`   ${upper}: Pre-AI cap gate → SKIP LLM (per-symbol ${symbolPositions.length}/${perSymbolCap}, mgmt=${detDecision.management})`);
        }
        // Hard absolute ceiling — even defensive intent must respect 2× cap.
        if (isGateEnabled(cfg, 'preAiPositionCapGate') && atDefenseCap && detDecision.action !== 'SKIP') {
          aiDecision = {
            action: 'SKIP',
            management: detDecision.management,
            size_fraction: 0,
            confidence: 0,
            rationale: `[DET] ${upper} hit defense ceiling (${symbolPositions.length}/${defenseSymbolCap} = 2× per-symbol)`,
            rationale_th: `[ ] ${upper} ถึงเพดานป้องกัน ${symbolPositions.length}/${defenseSymbolCap}`,
            timeframe: detDecision.timeframe,
            strategy: detDecision.strategy ?? primaryAnalysis.strategy,
            _source: 'pre_ai_defense_cap',
          };
          atLog(`   ${upper}: Pre-AI defense-cap gate → SKIP LLM (${symbolPositions.length}/${defenseSymbolCap})`);
        }

        // (c) Strict cache (full state)
        if (!aiDecision && !unifiedV25Plan) {
          const strictHit = llmCache.get(detDecision.stateHash);
          if (strictHit) {
            aiDecision = { ...strictHit, _source: 'llm_cache_strict' };
            atLog(`   ${upper}: LLM cache HIT (strict) — token cost saved`);
          }
        }

        // (d) Loose cache (HTF-only)
        let looseKey: string | null = null;
        if (!aiDecision && !unifiedV25Plan) {
          looseKey = marketStateHashLoose(
            upper,
            last.c,
            analyses,
            detDecision.action,
            detDecision.strategy || primaryAnalysis.strategy || 'NA'
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
              strategy: detDecision.strategy ?? primaryAnalysis.strategy,
              sl: detDecision.sl,
              tp: detDecision.tp,
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
          // EA-only now uses a higher configurable threshold because AI is not supervising weak deterministic signals.
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
                        const dxyCandles = await context.loadOptionalCandles('DXY', 'H1', 60);
                        const yieldCandles = await context.loadOptionalCandles('US10Y', 'H1', 60);
                        const spxCandles = await context.loadOptionalCandles('SPX500', 'H1', 60);
                        
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
                const skipMidChannelLLMGate =
                  (cfg.adaptive?.v25?.skipMidChannelLLM ?? true) &&
                  !isLocalProofScalpStrategy(detDecision.strategy ?? primaryAnalysis?.strategy);
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
                    if (!context.analyst) context.analyst = new AnalystAgent(cfg.apiKey!);
                    if (!context.riskOfficer) context.riskOfficer = new RiskOfficerAgent(cfg.apiKey!);
                    if (!context.executionTrader) context.executionTrader = new ExecutionAgent(cfg.apiKey!);

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
                            targetRrr: targetRrrForStrategy(detDecision.strategy ?? primaryAnalysis.strategy, cfg)
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

                    const report = await context.analyst.analyze(cfg, upper, analyses, correlations, last.c, smcAnchors || undefined);
                    atLog(`   ${upper}: Analyst (${report.modelUsed || aChoice.model}) finished in ${((Date.now() - aStart)/1000).toFixed(1)}s`);

                    const rStart = Date.now();
                    const rChoice = resolveAgentChoice(cfg, 'riskOfficer');
                    atLog(`   ${upper}: Risk Officer Starting (Model: ${rChoice.model}${rChoice.useSmartFree ? ' [Smart]' : ''})...`);
                    const approval = await context.riskOfficer.review(report, account, positions, cfg, last.c);
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
                      let plan: any;
                      try {
                        plan = await context.executionTrader.planOrder(approval, marketPrice, cfg);
                      } catch (err: any) {
                        atWarn(`   ${upper}: Execution Trader AI parse failed: ${err.message} -> skipping`);
                        return { action: 'SKIP', rationale: 'ai_parse_failed' };
                      }
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

                    if (Number.isFinite(Number(detDecision.sl)) && Number.isFinite(Number(detDecision.tp))) {
                      eaSl = Number(detDecision.sl);
                      eaTp = Number(detDecision.tp);
                    }

                    if ((!eaSl || !eaTp) && smcSnap) {
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
                    const fallbackStrategy = detDecision.strategy ?? primaryAnalysis.strategy;
                    const minR = targetRrrForStrategy(fallbackStrategy, cfg);

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
                        strategy: fallbackStrategy,
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
                            context.symbolTimeframes.set(upper, suggestedTF);
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
                    context.aiCooldownUntil = Date.now() + (60 * 60 * 1000); // 1 hour cooldown
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
        const eaConfidenceThreshold = isGateEnabled(cfg, 'eaFallbackConfidenceGate')
          ? (aiModeEnabled ? 45 : (cfg.adaptive?.eaOnlyMinConfidence ?? 55))
          : 0;
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

          if (Number.isFinite(Number(detDecision.sl)) && Number.isFinite(Number(detDecision.tp))) {
            eaSl = Number(detDecision.sl);
            eaTp = Number(detDecision.tp);
          }

          if ((!eaSl || !eaTp) && smcSnap) {
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

          const fallbackStrategy = detDecision.strategy ?? primaryAnalysis.strategy;

          // ATR-based SL/TP fallback when SMC OBs don't provide valid levels
          if (!eaSl || !eaTp) {
            const atrVal = analysis.atr ?? (last.c * 0.005);
            const atrMult = detDecision.action === 'BUY' ? 1 : -1;
            const targetRrrEa = targetRrrForStrategy(fallbackStrategy, cfg);
            if (!eaSl) eaSl = last.c - atrMult * atrVal * 1.5;
            if (!eaTp) eaTp = last.c + atrMult * atrVal * 1.5 * targetRrrEa;
          }

          // Verify RRR before allowing fallback
          const eaRiskDist = Math.abs(last.c - (eaSl ?? last.c));
          const eaRewardDist = Math.abs((eaTp ?? last.c) - last.c);
          const eaRRR = eaRiskDist > 0 ? eaRewardDist / eaRiskDist : 0;
          const minRRR = targetRrrForStrategy(fallbackStrategy, cfg);

          // V24.0 fix: float-safe RRR compare (was failing when 1.4999... < 1.5)
          const eaRrrGateEnabled = isGateEnabled(cfg, 'eaFallbackRrrGate');
          if ((!eaRrrGateEnabled || eaRRR + 1e-6 >= minRRR) && eaRiskDist > 0) {
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
              strategy: fallbackStrategy,
              _source: aiModeEnabled ? 'ea_fallback_v21' : 'ea_only_v23',
            };
            atLog(`   ${upper}: ${aiModeEnabled ? '⚡ V21.0 EA FALLBACK' : '🤖 V23.0 EA-ONLY'} activated: ${detDecision.action} conf=${detDecision.confidence}% RRR=${eaRRR.toFixed(2)} SL=${eaSl?.toFixed(2)} TP=${eaTp?.toFixed(2)}`);
            if (!eaRrrGateEnabled && eaRRR + 1e-6 < minRRR) {
              atWarn(`   ${upper}: EA Fallback RRR Gate disabled - allowing RRR ${eaRRR.toFixed(2)} < ${minRRR}.`);
              pushDecisionTrace(gateTrace, 'ea_fallback_rrr', 'ALLOW', `BYPASS: RRR gate disabled (${eaRRR.toFixed(2)} < ${minRRR})`, {
                eaRRR,
                minRRR,
              });
            }
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
            const lastCircuitLog = context.aiFallbackCircuitLastLogAt.get(upper) ?? 0;
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
                context.aiFallbackCircuitLastLogAt.set(upper, nowMs);
                atWarn(`   ${upper}: AI fallback circuit active — hard SKIP until EA fallback passes (${eaFallbackMissReason ?? 'no actionable deterministic fallback'})`);
            }
            aiDecision = {
                action: 'SKIP',
                rationale: hardFallbackReason,
                rationale_th: `AI ไม่พร้อม และไม่ผ่านเงื่อนไข EA Fallback (${eaFallbackMissReason ?? 'no actionable deterministic fallback'})`,
                timeframe: detDecision.timeframe,
                strategy: detDecision.strategy ?? primaryAnalysis.strategy,
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
            const aiStrategyDisabled = disabledStrategyReason(cfg, selectedStrategy);
            if (aiStrategyDisabled && (action === 'BUY' || action === 'SELL')) {
                forcedSkipReason = aiStrategyDisabled;
                side = 'SKIP';
                atWarn(`   ${upper}: ${forcedSkipReason}. Forced SKIP.`);
                pushDecisionTrace(gateTrace, 'strategy_toggle', 'BLOCK', forcedSkipReason, {
                  strategy: selectedStrategy,
                });
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
            const technicalSideBeforeDecisionGuard = side;
            const sideResolution = resolveDecisionSide({
                technicalSide: side,
                proposedAction,
                confidence: Number(aiDecision.confidence ?? 0),
                minOverrideConfidence: cfg.adaptive?.decisionOverrideConfidenceMin ?? 75,
            });
            if (!isGateEnabled(cfg, 'decisionSideGuard') && (proposedAction === 'BUY' || proposedAction === 'SELL')) {
                side = forcedSkipReason ? 'SKIP' : proposedAction;
                atWarn(`   ${upper}: Decision Side Guard disabled - accepting proposed ${proposedAction}.`);
                pushDecisionTrace(gateTrace, 'decision_side_guard', 'ALLOW', `BYPASS: accepted proposed ${proposedAction}`, {
                  technicalSide: technicalSideBeforeDecisionGuard,
                  proposedAction,
                  confidence: Number(aiDecision.confidence ?? 0),
                });
            } else if (!sideResolution.applied && (proposedAction === 'BUY' || proposedAction === 'SELL')) {
                forcedSkipReason = `Decision Side Guard blocked: ${sideResolution.reason}`;
                atWarn(`   ${upper}: ${forcedSkipReason}. Forced SKIP.`);
                side = sideResolution.side;
            } else {
                side = forcedSkipReason ? 'SKIP' : sideResolution.side;
            }

            // --- Pre-Gate Validator (XAUUSD SL Distance) ---
            if (isGateEnabled(cfg, 'slDistanceGuard') && side !== 'SKIP' && upper === 'XAUUSD' && (proposedAction === 'BUY' || proposedAction === 'SELL') && aiDecision.sl) {
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
                   const isLocalProofScalp = isLocalProofScalpStrategy(selectedStrategy);
                   if (aiCtPrimary && aiHtfAgrees) {
                       atLog(`   ${upper}: Counter-trend gate skipped — H4=${h4BiasNow} agrees with AI ${side} (MTF-aware exemption).`);
                   }

                   if (!isGateEnabled(cfg, 'counterTrendGuard') && isAiCounterTrend) {
                       atWarn(`   ${upper}: Counter-trend gate disabled - allowing ${side} counter-trend candidate.`);
                       pushDecisionTrace(gateTrace, 'counter_trend_guard', 'ALLOW', `BYPASS: counter-trend gate disabled for ${side}`, {
                         strategy: selectedStrategy,
                         regime: analysis.regime,
                         confluence: analysis.confluence,
                       });
                   } else if (isLocalProofScalp && isAiCounterTrend) {
                       atLog(`   ${upper}: Counter-trend gate bypassed for ${selectedStrategy}: wall break/reclaim proof is LTF-local by design.`);
                   } else if (isAiCounterTrend) {
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

                    // EA-only has no AI supervisor to reject local M1/M5 conflicts.
                    // Block fast entries when both execution TFs point the other way.
                    if (isGateEnabled(cfg, 'ltfConsensusGuard') && !aiModeEnabled && (side === 'BUY' || side === 'SELL')) {
                        const ltfBlock = ltfConsensusBlockReason(side as TradeSide, selectedStrategy, analyses, cfg);
                        if (ltfBlock) {
                            forcedSkipReason = ltfBlock;
                            side = 'SKIP';
                            atWarn(`   ${upper}: ${forcedSkipReason}. WAIT_LOCAL_ALIGNMENT.`);
                            pushDecisionTrace(gateTrace, 'ltf_consensus_guard', 'BLOCK', forcedSkipReason, {
                              strategy: selectedStrategy,
                              m1: { bias: analyses['M1']?.bias, confluence: analyses['M1']?.confluence },
                              m5: { bias: analyses['M5']?.bias, confluence: analyses['M5']?.confluence },
                            });
                        }
                    }

                    // --- V24.0 Fade-the-Level Proximity Gate (PRE Zone-Aware) ---
                    // เปลี่ยน entry policy จาก "BREAKOUT chasing" → "Fade S/R wall"
                    // BUY ก็ต่อเมื่อราคาลงไปใกล้ supportWall (50-100 pip)
                    // SELL ก็ต่อเมื่อราคาขึ้นไปใกล้ resistanceWall (50-100 pip)
                    // ★★★+ ผ่าน, ★★ ต้อง LTF (M5/M15) confirm, ★ reject
                    if ((cfg.adaptive?.enableProximityGate ?? true) && isGateEnabled(cfg, 'proximityGate') && (side === 'BUY' || side === 'SELL')) {
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
                                              h4RegimeNow === 'VOLATILE_BREAKOUT';
                        const continuationMinConf = isBreakoutRegime
                          ? (cfg.adaptive?.zoneBreakoutConfluenceMin ?? 55)
                          : (cfg.adaptive?.proximityContinuationConfluenceMin ?? 62);
                        const htfContinuationAligned = continuationIntent &&
                          htfIsTrending &&
                          htfBiasAgrees(side as TradeSide, analyses) &&
                          analysis.confluence >= continuationMinConf;
                        const forceWallProximity = isLocalProofScalpStrategy(selectedStrategy) || isMeanReversionStrategy(selectedStrategy);
                        const allowBypass = !forceWallProximity && isBreakoutStrategy && (htfIsTrending || htfContinuationAligned);

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

                            const requireLtfConfirmation =
                                !isLocalProofScalpStrategy(selectedStrategy) &&
                                (selectedStrategy.includes('SCALP') || selectedStrategy.includes('SMC_FVG_REVERSAL'));
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
                    // --- V26.25: UNIFIED ZONE GATE ---
                    // ย้ายการกรอง Zone ทั้งหมดมาตรวจสอบที่ Post-AI เพียงจุดเดียว เพื่อป้องกันการ Veto ล่วงหน้า
                    if ((cfg.adaptive?.enableZoneAwareGate ?? true) && isGateEnabled(cfg, 'unifiedZoneGate') && (side === 'BUY' || side === 'SELL') && !isLocalProofScalpStrategy(selectedStrategy)) {
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

                        const zoneContext = buildZoneLayerContext(side as TradeSide, zoneLayers, hardBlockPct, selectedStrategy);
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
                            
                            const isScalp = isScalpLikeStrategy(selectedStrategy);
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
                                pushDecisionTrace(gateTrace, 'zone_aware_gate', 'BLOCK', forcedSkipReason, {
                                  side,
                                  primaryZone: zoneLabel,
                                  pctFromRange: round2(zoneCheck.pctFromRange),
                                  h4Bias,
                                  zoneLayers: zoneLayers.map(zl => ({
                                    tf: zl.timeframe,
                                    role: zl.role,
                                    zone: zl.result?.zone,
                                    pct: round2(zl.result?.pctFromRange ?? 0),
                                  })),
                                  wrongLayers: zoneContext.wrongLayers,
                                  executionFavorable: zoneContext.executionFavorable,
                                  hardMajorWrong: zoneContext.hardMajorWrong,
                                  hardExecutionWrong: zoneContext.hardExecutionWrong,
                                  overrideChecks: {
                                    htfAligned: zoneHtfAligned,
                                    continuationIntent: zoneContinuationIntent,
                                    smcSupported: isSMCSupported,
                                    fvgSupported,
                                    fvgTimeframes,
                                    wallFadeSupported,
                                    wallStars: latestProximityPass?.wallStars ?? null,
                                    localExecutionOverride,
                                    continuationOverride,
                                    breakoutPass,
                                    confluence: round1(analysis.confluence),
                                    minConfluence: zoneContinuationMin,
                                  },
                                });
                                zoneGateBlocksTotal.inc({ symbol: upper, side, zone: zoneLabel });
                                side = 'SKIP';
                            } else {
                                if (isSMCSupported) {
                                    atLog(`   ${upper}: Zone-Aware Gate: ${side} in ${zoneLabel} zone ALLOWED - supported by Institutional SMC (zones=${zoneContext.path})`);
                                } else if (continuationOverride) {
                                    atLog(`   ${upper}: Zone-Aware Gate: ${side} in ${zoneLabel} zone ALLOWED - MTF continuation override (conf=${analysis.confluence.toFixed(1)}, fvgTF=${fvgTimeframes.join('/') || 'none'}, localAligned=${zoneContext.executionFavorable}, wallFade=${wallFadeSupported ? String(latestProximityPass?.wallStars) : 'false'}, zones=${zoneContext.path})`);
                                } else if (breakoutPass) {
                                    atLog(`   ${upper}: Zone-Aware Gate: ${side} ALLOWED - trend-aligned breakout (${h4Pct.toFixed(1)}% of H4 range, H4=${h4Bias})`);
                                }
                                
                                // V26.25 Dynamic Lot Size Reduction on Wrong Zone Override
                                if (zoneContext.hasWrongZone && breakoutPass) {
                                    const isBreakout = breakoutBuy || breakoutSell;
                                    const safeFraction = isBreakout ? 0.3 : (fvgSupported ? 0.15 : 0.25);
                                    if (aiDecision) {
                                        aiDecision.size_fraction = Math.min(aiDecision.size_fraction ?? 0.5, safeFraction);
                                    }
                                    if (detDecision) {
                                        detDecision.size_fraction = Math.min(detDecision.size_fraction ?? 0.5, safeFraction);
                                    }
                                    atLog(`   ${upper}: Unified Zone Gate OVERRIDE PASS (wrong zone) -> size_fraction capped at ${safeFraction}x for safety`);
                                    pushDecisionTrace(gateTrace, 'unified_zone_override_size_cap', 'INFO', `Capped size_fraction at ${safeFraction}x due to wrong zone override`, {
                                        safeFraction,
                                        isBreakout,
                                        fvgSupported,
                                    });
                                }
                            }
                        }
                    }

                    // V26.11: generic SCALPING must not chase into the opposite
                    // M15 SMC pressure. The dedicated FVG playbooks can still trade
                    // their own proof, but A_SCALPING should respect the latest
                    // local supply/demand context before opening a market order.
                    if (
                        (cfg.adaptive?.enableScalpM15PressureGuard ?? true) &&
                        isGateEnabled(cfg, 'm15SmcPressureGate') &&
                        (side === 'BUY' || side === 'SELL') &&
                        (!selectedStrategy.includes('SWING') && !selectedStrategy.includes('TREND_FOLLOW'))
                    ) {
                        const m15Signals = analyses['M15']?.signals ?? [];
                        const pressure = findOpposingScalpPressure(side as TradeSide, m15Signals);
                        if (pressure) {
                            const m1Aligned = analyses['M1']?.bias === side && (analyses['M1']?.confluence ?? 0) >= 65;
                            const m5Aligned = analyses['M5']?.bias === side && (analyses['M5']?.confluence ?? 0) >= 65;
                            if (selectedStrategy === 'SCALPING' && (m1Aligned || m5Aligned)) {
                                atLog(`   ${upper}: M15 SMC Pressure Guard OVERPASS: SCALPING has strong LTF confluence (M1=${m1Aligned ? analyses['M1']?.confluence : 'no'}, M5=${m5Aligned ? analyses['M5']?.confluence : 'no'})`);
                            } else {
                                const blockedSide = side;
                                forcedSkipReason = `M15 SMC Pressure Guard blocked: ${selectedStrategy} ${pressure.reason}; ${pressure.evidence.join('; ')}`;
                                atWarn(`   ${upper}: ${forcedSkipReason}. WAIT_FOR_FVG_CONFIRM.`);
                                zoneGateBlocksTotal.inc({ symbol: upper, side: blockedSide, zone: 'M15_SMC_PRESSURE' });
                                pushDecisionTrace(gateTrace, 'm15_smc_pressure', 'BLOCK', forcedSkipReason, {
                                  m15Bias: analyses['M15']?.bias,
                                  m15Confluence: analyses['M15']?.confluence,
                                  evidence: pressure.evidence,
                                });
                                side = 'SKIP';
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
                                // V26.15: Add structured zone trace for post-analysis
                                pushDecisionTrace(gateTrace, 'zone_aware_gate', 'BLOCK', forcedSkipReason!, {
                                  side,
                                  primaryZone: zoneCheck.zone,
                                  pctFromRange: round2(zoneCheck.pctFromRange),
                                  h4Bias,
                                  wrongZoneBuy,
                                  wrongZoneSell,
                                  overrideChecks: {
                                    htfAligned: zoneHtfAligned,
                                    continuationIntent: zoneContinuationIntent,
                                    smcSupported: isSMCSupported,
                                    fvgSupported,
                                    wallFadeSupported,
                                    wallStars: latestProximityPass?.wallStars ?? null,
                                    breakoutPass,
                                    confluence: round1(analysis.confluence),
                                    minConfluence: zoneContinuationMin,
                                  },
                                });
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
                    // For FVG-scalp setups, require a real fill inside an aligned active
                    // FVG. Broad "near FVG" support is still useful for zone overrides,
                    // but the SMC_FVG_SCALP label should not be used for generic sweeps.
                    if (
                        (cfg.adaptive?.enableZoneAwareGate ?? true) &&
                        isGateEnabled(cfg, 'fvgFillGate') &&
                        (side === 'BUY' || side === 'SELL') &&
                        selectedStrategy === 'SMC_FVG_SCALP'
                    ) {
                        const fvgFillMatches: string[] = [];
                        let activeFvgCount = 0;
                        const fvgTfCandidates = [...new Set(['H1', 'M30', 'M15', 'M5', activeTF])];
                        for (const tf of fvgTfCandidates) {
                            const tfCandles = analyses[tf]?.candles || (tf === activeTF ? allCandles.get(upper) : null);
                            if (!tfCandles || tfCandles.length < 20) continue;
                            const activeFvgs = getActiveFVGs(tfCandles);
                            activeFvgCount += activeFvgs.length;
                            const fillMatch = findAlignedFvgFill(side as TradeSide, last.c, activeFvgs, {
                                minFillPct: 0.15,
                                edgeTolerancePct: 0.05,
                                minDistance: Math.max((symbolInfo?.point ?? 0.01) * 10, Math.abs(last.c) * 0.00001),
                            });
                            if (fillMatch) {
                                fvgFillMatches.push(`${tf}:${fillMatch.fvg.type} ${round2(fillMatch.fvg.bottom)}-${round2(fillMatch.fvg.top)} fill=${Math.round(fillMatch.fillPct * 100)}%`);
                            }
                        }
                        if (fvgFillMatches.length === 0) {
                            forcedSkipReason = `FVG-Fill Gate blocked: SMC_FVG_SCALP ${side} but price ${last.c} is not inside a >=15% aligned active FVG fill (${activeFvgCount} active)`;
                            atWarn(`   ${upper}: ${forcedSkipReason}.`);
                            zoneGateBlocksTotal.inc({ symbol: upper, side, zone: 'FVG_MISALIGN' });
                            side = 'SKIP';
                        } else if (fvgFillMatches.length > 0) {
                            atLog(`   ${upper}: FVG-Fill Gate: ${side} confirmed on ${fvgFillMatches.join('; ')}`);
                        }
                    }
               }
        }

        // V24.0 — Sequential Entry Gate (1 ไม้แรก/symbol; ไม้ที่ 2+ ต้องเข้าเงื่อนไข)
        // (a) ไม้ล่าสุด safe (TRAIL/BE/Golden) → เปิดเพิ่มเป็น pyramid ฝั่งกำไร
        // (b) ไม้ล่าสุดขาดทุน >= sequentialAddOnLossPct (default 25% ของ SL) → เปิดเพิ่มเป็น recovery (แก้ไม้)
        // (c) detDecision.management = SCALE_IN / HEDGE → ผ่าน (เป็น defense plan)
        // V26.25: ลบ duplicate checks ในด่านนี้ออกทั้งหมดเพื่อให้ไปตรวจที่ Order Preflight Gate ทีเดียว
        if (
          (cfg.adaptive?.enableSequentialEntry ?? true) &&
          isGateEnabled(cfg, 'sequentialEntryGate') &&
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
              const scaleBaseMin = upper.includes('XAU') || upper.includes('GOLD')
                ? (cfg.adaptive?.scaleInMinEntryDistanceXAU ?? 2.0)
                : Math.max(last.c * 0.00025, 0.00025);
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
        const targetRrr = targetRrrForStrategy(selectedStrategy, cfg);

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
        const minTpDistanceForAi = isScalpLikeStrategy(selectedStrategy) ? 3.0 : 5.0;
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
        if ((cfg.adaptive?.enableFvgSlBuffer ?? true) && isGateEnabled(cfg, 'executionQualityGate') && sl !== null && side !== 'SKIP') {
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

        // V26.9: Wall -> M15 FVG magnet scalp owns its bracket.
        // TP stays at the target FVG edge minus spread; SL is derived from that
        // remaining reward so the playbook does not inherit wide HTF brackets.
        if (isWallFvgMagnetScalp(selectedStrategy) && side !== 'SKIP' && tp !== null && sl !== null) {
          let magnetRrr = targetRrrForStrategy(selectedStrategy, cfg);
          const rawTargetTp = Number(detDecision?.tp ?? aiDecision?.tp ?? tp);
          const targetTp = Number.isFinite(rawTargetTp) ? rawTargetTp : tp;
          const spreadSafeTp = side === 'BUY'
            ? roundToTick(targetTp - spreadInPrice, tick)
            : roundToTick(targetTp + spreadInPrice, tick);
          const tpWrongSide =
            spreadSafeTp === null ||
            (side === 'BUY' && spreadSafeTp <= entryRounded) ||
            (side === 'SELL' && spreadSafeTp >= entryRounded);

          if (tpWrongSide) {
            forcedSkipReason = `SMC_FVG_MAGNET_SCALP blocked: FVG TP after spread is not beyond entry (entry=${entryRounded}, rawTP=${targetTp}, spread=${spreadInPrice})`;
            atWarn(`   ${upper}: ${forcedSkipReason}`);
            side = 'SKIP';
          } else {
            const reward = Math.abs(spreadSafeTp - entryRounded);
            
            // Dynamic adjustment of RRR to get a safer/wider SL distance when reward is narrow
            const stopsLevel = symbolInfo?.stopsLevel || 0;
            const minStopsTick = stopsLevel * point;
            const atrBuffer = atr * 0.25;
            const safeMinSl = Math.max(atrBuffer, spreadInPrice * 3, point * 50, minStopsTick);

            if (reward / magnetRrr < safeMinSl) {
              const preferredRrr = reward / safeMinSl;
              if (preferredRrr >= 1.1) {
                magnetRrr = preferredRrr;
                atLog(`   ${upper}: SMC_FVG_MAGNET_SCALP RRR adjusted dynamically to ${magnetRrr.toFixed(2)} to maintain safe SL distance.`);
              } else {
                magnetRrr = 1.1;
                atLog(`   ${upper}: SMC_FVG_MAGNET_SCALP RRR floored at 1.1 due to narrow reward (${reward.toFixed(4)})`);
              }
            }

            const formulaRisk = reward / magnetRrr;
            const formulaSl = side === 'BUY'
              ? roundToTick(entryRounded - formulaRisk, tick)
              : roundToTick(entryRounded + formulaRisk, tick);
            const slWrongSide =
              formulaSl === null ||
              (side === 'BUY' && formulaSl >= entryRounded) ||
              (side === 'SELL' && formulaSl <= entryRounded);

            if (formulaRisk <= 0 || slWrongSide) {
              forcedSkipReason = `SMC_FVG_MAGNET_SCALP blocked: invalid SL formula from FVG reward (reward=${reward.toFixed(4)}, RRR=${magnetRrr.toFixed(2)})`;
              atWarn(`   ${upper}: ${forcedSkipReason}`);
              side = 'SKIP';
            } else {
              const oldTp = tp;
              const oldSl = sl;
              tp = spreadSafeTp;
              sl = formulaSl;
              atLog(`   ${upper}: SMC_FVG_MAGNET_SCALP bracket locked: TP ${oldTp} -> ${tp} (FVG-spread), SL ${oldSl} -> ${sl} (reward/${magnetRrr.toFixed(2)})`);
            }
          }
        }

        // TP rescue: re-extend TP to keep the original RRR target whenever SL moved
        // away from entry. Only runs when both SL & TP are set and side is BUY/SELL.
        if (side !== 'SKIP' && sl !== null && tp !== null && !isWallFvgMagnetScalp(selectedStrategy)) {
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
          !isWallFvgMagnetScalp(selectedStrategy) &&
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
            isXau ? (cfg.adaptive?.v25?.compactScalpMinRiskXAU ?? 3.0) : entryRounded * 0.0005,
          );
          const maxRisk = Math.max(
            minRisk,
            atr * (cfg.adaptive?.v25?.compactScalpMaxRiskAtrMul ?? 0.16),
          );

          if (currentRisk > maxRisk * 1.1) {
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
              // Fix: Never clamp the risk if it forces SL in front of the wall.
              // Stretching maxRisk up to 4.0x allows the SL to stay safely behind the institutional wall.
              // If this results in poor RRR, the gate will reject it later.
              desiredRisk = wallRisk <= maxRisk * 4.0
                  ? Math.max(minRisk, wallRisk)
                  : Math.max(minRisk, Math.min(maxRisk, wallRisk));
            }

            const compactRrr = Math.max(targetRrr, cfg.adaptive?.v25?.compactScalpRrr ?? 1.35);
            let compactSl = side === 'BUY'
              ? roundToTick(entryRounded - desiredRisk, tick)
              : roundToTick(entryRounded + desiredRisk, tick);
            const minRewardTp = side === 'BUY'
              ? roundToTick(entryRounded + desiredRisk * compactRrr, tick)
              : roundToTick(entryRounded - desiredRisk * compactRrr, tick);
            let compactTp: number | null = tp;
            const existingReward = Math.abs(tp - entryRounded);
            if (compactTp === null || existingReward + 1e-9 < desiredRisk * compactRrr) {
              compactTp = minRewardTp;
            }

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

            const isValidCompact = compactCorrectSide && compactRisk >= minRisk && compactRrrActual >= 1.0;
            const isShrinking = compactRisk < currentRisk;
            const isWideningToWall = anchorWall && compactRisk > currentRisk && compactRisk === desiredRisk;

            if (isValidCompact && (isShrinking || isWideningToWall)) {
              atLog(`   ${upper}: compact scalp bracket (wall anchored): SL ${sl} -> ${compactSl}, TP ${tp} -> ${compactTp} (risk ${currentRisk.toFixed(2)} -> ${compactRisk.toFixed(2)}, RRR=${compactRrrActual.toFixed(2)})`);
              sl = compactSl;
              tp = compactTp;
            }
          }
        }

        // MEAN_REVERSION should harvest the nearby snap-back, not swing for
        // distant 4R-6R targets. Yesterday's loss cluster showed repeated
        // entries with oversized TP while SL stayed compact, so cap only the TP.
        if (
          side !== 'SKIP' &&
          sl !== null &&
          tp !== null &&
          isMeanReversionStrategy(selectedStrategy)
        ) {
          const mrMaxRrr = Math.max(1.2, cfg.adaptive?.meanReversionMaxRrr ?? 1.8);
          const mrRisk = Math.abs(entryRounded - sl);
          const mrReward = Math.abs(tp - entryRounded);
          const mrRrr = mrRisk > 0 ? mrReward / mrRisk : 0;
          if (mrRisk > 0 && mrRrr > mrMaxRrr + 1e-9) {
            const oldTp = tp;
            const cappedTp = side === 'BUY'
              ? roundToTick(entryRounded + mrRisk * mrMaxRrr, tick)
              : roundToTick(entryRounded - mrRisk * mrMaxRrr, tick);
            const cappedCorrectSide =
              cappedTp !== null &&
              ((side === 'BUY' && cappedTp > entryRounded) ||
               (side === 'SELL' && cappedTp < entryRounded));

            if (cappedCorrectSide) {
              tp = cappedTp;
              atLog(`   ${upper}: MEAN_REVERSION TP capped: ${oldTp} -> ${tp} (RRR ${mrRrr.toFixed(2)} -> ${mrMaxRrr.toFixed(2)})`);
            } else {
              forcedSkipReason = `MEAN_REVERSION blocked: TP cap would be on wrong side (entry=${entryRounded}, tp=${cappedTp})`;
              atWarn(`   ${upper}: ${forcedSkipReason}`);
              side = 'SKIP';
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
        if (side !== 'SKIP' && tp !== null && !isWallFvgMagnetScalp(selectedStrategy)) {
          try {
            const priceMap = indicatorPipeline.getPriceMap(upper);
            if (priceMap) {
              const direction = side === 'BUY' ? 'UP' : 'DOWN';
              const v25Only = cfg.adaptive?.v25?.unifiedDecisionPath === false && cfg.adaptive?.v25?.enableV25Only === true;
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
                const postCapReward = Math.abs(tp - entryRounded);
                const postCapRrr = postCapRisk > 0 ? postCapReward / postCapRisk : 0;
                const minAfterCap = finalRrrFloorForStrategy(selectedStrategy, cfg, targetRrr);

                // V26.25: Proportional SL shrink — if TP cap would kill RRR,
                // try to shrink SL proportionally to preserve at least minAfterCap RRR.
                // This prevents the common pattern: cap TP → RRR drops → Hard Risk Gate blocks.
                if (postCapRrr < minAfterCap && sl !== null && postCapReward > 0) {
                  const desiredRisk = postCapReward / minAfterCap;
                  const minSlDist = Math.max(spreadInPrice * 3, point * 30); // absolute minimum SL distance
                  if (desiredRisk >= minSlDist) {
                    const oldSl = sl;
                    sl = side === 'BUY'
                      ? roundToTick(entryRounded - desiredRisk, tick)
                      : roundToTick(entryRounded + desiredRisk, tick);
                    // Verify SL is still on correct side
                    const slValid = sl !== null && (
                      (side === 'BUY' && sl < entryRounded) ||
                      (side === 'SELL' && sl > entryRounded)
                    );
                    if (slValid) {
                      const adjustedRrr = Math.abs((tp - entryRounded) / (entryRounded - sl!));
                      atLog(`   ${upper}: V26.25 SL shrink after TP cap: SL ${oldSl} → ${sl} (risk ${postCapRisk.toFixed(2)} → ${desiredRisk.toFixed(2)}, RRR ${postCapRrr.toFixed(2)} → ${adjustedRrr.toFixed(2)})`);
                    } else {
                      sl = oldSl; // revert if invalid
                      tpCapRiskReason = `TP_CAP_RRR: PriceMap cap reduced RRR to ${postCapRrr.toFixed(2)} < ${minAfterCap} (SL shrink produced invalid SL)`;
                    }
                  } else {
                    tpCapRiskReason = `TP_CAP_RRR: PriceMap cap reduced RRR to ${postCapRrr.toFixed(2)} < ${minAfterCap}, SL shrink blocked (need ${desiredRisk.toFixed(2)} < min ${minSlDist.toFixed(2)})${obstaclesNote}`;
                  }
                } else if (postCapRrr < minAfterCap) {
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
              originalLastClose,
              decisionPriceSource: freshDecisionPrice.source,
              decisionPriceAgeMs: freshDecisionPrice.ageMs ?? null,
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
          decisions.push(enrichDecisionForLog(buildSkipDecision(upper, analysis.regime, analysis.bias, analysis.confluence, selectedStrategy, `invalid risk distance (${riskDistance})`, 'invalid risk distance', `  (${riskDistance.toFixed(4)})`)));
          return;
        }

        let volume = context.computeVolume(account, riskDistance, upper, selectedStrategy);
        
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
        const unifiedV25Path = cfg.adaptive?.v25?.unifiedDecisionPath !== false;
        const v25OnlyEnabled = isGateEnabled(cfg, 'v25OnlyGate') && !unifiedV25Path && cfg.adaptive?.v25?.enableV25Only === true;
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
        } else if (v25OnlyEnabled && isGateEnabled(cfg, 'v25BreakoutGuard') && side !== 'SKIP' && isBreakoutRegime) {
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

        // --- V26.25: EXECUTION QUALITY GATE (Unified Drift & Hard Risk & SL Guard) ---
        // ยุบรวม Entry Drift และ Hard RRR/SL เข้ามาอยู่ด่านเดียวกัน เพื่อป้องกันการคิดซ้ำซ้อน
        if (isGateEnabled(cfg, 'executionQualityGate') && side !== 'SKIP') {
            const minRRR = finalRrrFloorForStrategy(selectedStrategy, cfg, targetRrr);
            const slDistance = Math.abs(entryRounded - (sl ?? entryRounded));

            // Spread-aware SL Guard
            const minSlPoints = Math.max(0.3, spreadInPrice + (point * 10)); // Min 30 pts or Spread + 10 pts buffer
            const minTpPoints = isScalpLikeStrategy(selectedStrategy) ? 3.0 : 5.0;

            // Recalculate RRR using the real execution price (broker entry rounded) instead of stale candle close
            const decisionPrice = Number.isFinite(Number(last?.c)) ? Number(last.c) : originalLastClose;
            const driftPoints = Math.abs(entryRounded - decisionPrice);
            const driftRisk = Math.abs(entryRounded - (sl ?? entryRounded));
            const driftReward = tp !== null ? Math.abs(tp - entryRounded) : 0;
            const driftRrr = driftRisk > 0 ? driftReward / driftRisk : 0;

            // Check if drift is too extreme AND RRR is degraded below minRRR
            const driftAtr = Number(analysis?.atr ?? 30);
            const maxDriftAtrMul = 0.15;
            const maxDriftPoints = Math.max(driftAtr * maxDriftAtrMul, 2.0); // at least $2
            const isDriftTooFar = isGateEnabled(cfg, 'executionQualityGate') && driftPoints > maxDriftPoints && driftRrr < minRRR;

            // float-safe RRR check
            const RRR_EPS = 1e-9;
            const isRrrInvalid = actualRrr < (minRRR - RRR_EPS) || (isGateEnabled(cfg, 'executionQualityGate') && driftRrr < (minRRR - RRR_EPS));
            const isTpTooClose = tp !== null && Math.abs(tp - entryRounded) < minTpPoints;
            const isSlTooClose = slDistance < minSlPoints;

            if (isDriftTooFar || isRrrInvalid || isTpTooClose || isSlTooClose) {
                let reason = '';
                let gateKey = 'execution_quality';
                if (isSlTooClose) {
                    reason = `SL distance ${slDistance.toFixed(4)} < min ${minSlPoints.toFixed(4)} (Spread: ${currentSpread}pts)`;
                } else if (isTpTooClose) {
                    reason = `TP too close: target TP distance ${Math.abs((tp ?? 0) - entryRounded).toFixed(4)} < min ${minTpPoints}`;
                } else if (isDriftTooFar) {
                    reason = `ENTRY_DRIFT: price drifted ${driftPoints.toFixed(2)}pts (>${maxDriftPoints.toFixed(2)} ATR×${maxDriftAtrMul}), RRR degraded to ${driftRrr.toFixed(2)} < ${minRRR}`;
                } else if (tpCapRiskReason) {
                    reason = tpCapRiskReason;
                } else {
                    reason = `RRR degraded: actualRrr ${actualRrr.toFixed(2)} or driftRrr ${driftRrr.toFixed(2)} < minRRR ${minRRR} (strategy=${selectedStrategy})`;
                }

                atWarn(`   ${upper}: Execution Quality Gate triggered (${reason}) -> SKIP`);
                pushDecisionTrace(gateTrace, 'execution_quality', 'BLOCK', reason, {
                    entry: entryRounded,
                    side,
                    sl,
                    tp,
                    actualRrr,
                    driftRrr,
                    driftPoints,
                    minRRR,
                    isDriftTooFar,
                    isRrrInvalid,
                    isTpTooClose,
                    isSlTooClose,
                });
                decisions.push(enrichDecisionForLog(buildSkipDecision(upper, analysis.regime, analysis.bias, analysis.confluence, selectedStrategy, `[EXECUTION QUALITY] ${reason} | Entry:${entryRounded} SL:${sl} TP:${tp}`, 'risk_params_gate', ` (${reason})`)));
                return;
            }
        }

        const managementPlan = tradeManagementService.planMarketAwareManagement(cfg, account, cluster, analysis, aiDecision, volume, playbookScores, positions.length, context.openJournal);

        // Heat Guard: suppress defense override when cluster is already under high heat.
        // Even if management says "scale_in / defense", we shouldn't keep adding to a
        // losing cluster when heat is already dangerous (>= 1.0R total drawdown).
        const clusterHeatR = context.openJournal
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
        const ABSOLUTE_MAX_POSITIONS = context.config.risk.maxOpenPositions * 3;
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

        let gate = gateTrade(context.config, account, positions, upper, volume, analysis, riskDistance, currentSpread, {
          allowOverLimitDefense: effectiveOverLimitDefense,
          correlationResult: corrAnalysis,
          side: side
        });

        // --- P2.3: Close-Weakest-Loser to make room for a high-quality setup ---
        // If the only reason we'd skip is "max open positions" AND the new setup
        // is high-quality (confluence  threshold) AND the cooldown has elapsed,
        // close the worst loser of THIS symbol's cluster so the new trade can run.
        const closeWeakestEnabled = (context.config.adaptive?.enableCloseWeakest ?? true) && isGateEnabled(cfg, 'closeWeakestGate');
        const cwMinConf = (context.config.adaptive?.closeWeakestMinConfluence ?? 70);
        const cwCooldownMs = (context.config.adaptive?.closeWeakestCooldownMs ?? 3_600_000); // 1 hr default
        if (
          closeWeakestEnabled &&
          !gate.allowed &&
          /max open positions/i.test(gate.reason) &&
          (side === 'BUY' || side === 'SELL') &&
          analysis.confluence >= cwMinConf &&
          cluster.positions.length > 0
        ) {
          const lastClose = context.closeWeakestLastAt.get(upper) ?? 0;
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
              const cwResult = await context.executeManagementPlan(upper, closeWeakestPlan, sl, tp, aiDecision);
              managementEventsTotal.inc({ symbol: upper, event: cwResult.executed ? 'close_weakest' : 'close_weakest_failed' });
              if (cwResult.executed) {
                context.closeWeakestLastAt.set(upper, Date.now());
                atLog(`   ${upper}: P2.3 close-weakest #${weakest.ticket} executed  re-gating new ${side}`);
                // Re-fetch positions and re-gate so the new trade can proceed
                const refreshedPositions = await context.fetchPositions();
                positions.length = 0;
                positions.push(...refreshedPositions);
                gate = gateTrade(context.config, account, positions, upper, volume, analysis, riskDistance, currentSpread, {
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
        if (!gate.allowed && isGateEnabled(cfg, 'riskParamsGate')) {
            atWarn(`   ${upper}: Trade BLOCKED by Risk Gate (${gate.reason}) -> SKIP`);
            pushDecisionTrace(gateTrace, 'risk_gate', 'BLOCK', gate.reason, { volume, riskDistance, currentSpread });
            decisions.push(enrichDecisionForLog(buildSkipDecision(upper, analysis.regime, analysis.bias, analysis.confluence, selectedStrategy, rationale, gate.reason, translatedTh)));
            return;
        } else if (!gate.allowed) {
            atWarn(`   ${upper}: Risk Params Gate disabled - allowing trade despite: ${gate.reason}`);
            pushDecisionTrace(gateTrace, 'risk_gate', 'ALLOW', `BYPASS: risk gate disabled: ${gate.reason}`, { volume, riskDistance, currentSpread });
        }
        
        // Fix 3: Before skipping, honour shrink actions (CLOSE / REDUCE / PARTIAL_CLOSE)
        // carried by the management plan. The AI can say action=SKIP but still instruct
        // us to close a specific losing ticket  we must not drop that intent.
        const shrinkMode = managementPlan.mode === 'CLOSE' || managementPlan.mode === 'REDUCE' || managementPlan.mode === 'PARTIAL_CLOSE';
        if (analysis.confluence < cfg.minConfluence || analysis.fitness < cfg.minFitness || side === 'SKIP') {
          if (shrinkMode && cluster.positions.length > 0) {
            const managementId = `MGMT-SKIP-${Date.now()}-${upper}`;
            context.insertManagementJournal({
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
            const mgmtResult = await context.executeManagementPlan(upper, managementPlan, sl, tp, aiDecision);
            context.insertManagementJournal({
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
            const skipDecision = buildSkipDecision(upper, analysis.regime, analysis.bias, analysis.confluence, selectedStrategy, rationale, skipRiskGate, translatedTh);
            skipDecision.riskGate = mgmtResult.executed
              ? `SKIP + ${managementPlan.mode.toLowerCase()} executed | ${mgmtResult.riskGate}`
              : `SKIP + ${managementPlan.mode.toLowerCase()} attempted | ${mgmtResult.riskGate}`;
            pushDecisionTrace(gateTrace, 'skip_branch_management', mgmtResult.executed ? 'EXECUTED' : 'FAILED', skipDecision.riskGate);
            decisions.push(enrichDecisionForLog(skipDecision, { managementResult: mgmtResult }));
            return;
          }
          pushDecisionTrace(gateTrace, 'final_entry_gate', 'SKIP', skipRiskGate, {
            confluence: round1(analysis.confluence),
            minConfluence: cfg.minConfluence,
            fitness: round1(analysis.fitness),
            minFitness: cfg.minFitness,
            side,
          });
          decisions.push(enrichDecisionForLog(buildSkipDecision(upper, analysis.regime, analysis.bias, analysis.confluence, selectedStrategy, rationale, skipRiskGate, translatedTh)));
          return;
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
          context.insertManagementJournal({
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
          const managementResult = await context.executeManagementPlan(upper, managementPlan, sl, tp, aiDecision);
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
                const flipResult = await context.executeManagementPlan(upper, oppPlan, sl, tp, aiDecision);
                managementEventsTotal.inc({ symbol: upper, event: flipResult.executed ? 'flip_opposite_open' : 'flip_opposite_failed' });
                atLog(`[AutoEngine]  ${upper}: FLIP opposite ${opp.side} ${opp.volume}  ${flipResult.executed ? '' : ''} ${flipResult.riskGate}`);
              } catch (flipErr: any) {
                atWarn(`[AutoEngine]  ${upper}: FLIP opposite-leg threw: ${String(flipErr?.message || flipErr)}`);
              }
            }
          } else {
            riskGate = managementResult.riskGate;
          }
          context.insertManagementJournal({
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
                : { atr, comment: safeComment, ttlMs: duplicateIntentTtlForStrategy(selectedStrategy, cfg) };
              const livePositionsForDuplicate = await marketDataService.fetchPositions(true).catch(() => positions);
              const duplicateIssue = isGateEnabled(cfg, 'orderPreflightGate')
                ? tradingExecutionService.nearDuplicateEntryIssue(
                  upper,
                  side as 'BUY' | 'SELL',
                  orderEntryPrice,
                  livePositionsForDuplicate,
                  cfg,
                  duplicateOpts,
                )
                : null;
              const postTpIssue = duplicateIssue || !isGateEnabled(cfg, 'postTpCooldownGate')
                ? null
                : postTakeProfitCooldownIssue(upper, side as TradeSide, orderEntryPrice, cfg);
              const preflightIssue = duplicateIssue ?? postTpIssue;
              if (preflightIssue) {
                atWarn(`   ${upper}: Order blocked: ${preflightIssue}. Choose one setup or wait for spacing.`);
                riskGate = preflightIssue;
                orderLog = { ...orderLog, blocked: true, duplicateIssue, postTpIssue };
                pushDecisionTrace(gateTrace, 'order_preflight', 'BLOCK', preflightIssue, {
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
                ticket = extractTicket(result.data);
                executed = ticket !== null || JSON.stringify(result.data).includes('"success":true');
                // Broker state just changed  drop the 500ms snapshot cache so the
                // next fetchPositions()/fetchAccount() sees the new position.
                if (executed) {
                  marketDataService.invalidateSnapshots();
                  try {
                    // Fetch the actual filled position to check for slippage
                    const livePositions = await marketDataService.fetchPositions(true);
                    const livePosition = livePositions.find(p => p.ticket === ticket);
                    if (livePosition) {
                      const actualPrice = livePosition.priceOpen;
                      const isWorse = (side === 'BUY' && actualPrice > orderEntryPrice) || (side === 'SELL' && actualPrice < orderEntryPrice);
                      const actualRisk = sl !== null ? Math.abs(actualPrice - sl) : 0;
                      const actualReward = tp !== null ? Math.abs(tp - actualPrice) : 0;
                      const actualRRR = actualRisk > 0 ? actualReward / actualRisk : 0;

                      if (isWorse && actualRRR < 1.0 && sl !== null && tp !== null) {
                        const intendedRisk = Math.abs(orderEntryPrice - sl);
                        const intendedReward = Math.abs(tp - orderEntryPrice);
                        
                        const newSlRaw = side === 'BUY' ? actualPrice - intendedRisk : actualPrice + intendedRisk;
                        const newTpRaw = side === 'BUY' ? actualPrice + intendedReward : actualPrice - intendedReward;
                        
                        const newSl = roundToTick(newSlRaw, tick);
                        const newTp = roundToTick(newTpRaw, tick);
                        
                        atWarn(`   ${upper}: ⚠️ SLIPPAGE DETECTED on #${ticket} (Intent: ${orderEntryPrice}, Actual: ${actualPrice}). RRR shrunk to ${actualRRR.toFixed(2)}. Restoring original bracket distances.`);
                        
                        const modResult = await callBridge('POST', ['/modify', '/mt5/modify', '/api/mt5/modify'], {
                            body: { ticket, symbol: upper, sl: newSl, tp: newTp },
                            timeoutMs: 15_000,
                            priority: 'critical'
                        });
                        
                        if (JSON.stringify(modResult.data).includes('"success":true')) {
                            sl = newSl;
                            tp = newTp;
                            atLog(`   ${upper}: ✅ SL/TP modified successfully. SL -> ${sl}, TP -> ${tp}`);
                            marketDataService.invalidateSnapshots();
                        } else {
                            atError(`   ${upper}: ❌ Failed to modify SL/TP after slippage. Response: ${JSON.stringify(modResult.data)}`);
                        }
                      } else if (!isWorse && actualPrice !== orderEntryPrice) {
                          atLog(`   ${upper}: 🟢 POSITIVE SLIPPAGE on #${ticket} (Intent: ${orderEntryPrice}, Actual: ${actualPrice}). Keeping original SL/TP (RRR improved to ${actualRRR.toFixed(2)}).`);
                      }
                    }
                  } catch (slipErr: any) {
                    atError(`   ${upper}: Error during slippage check for #${ticket}: ${String(slipErr?.message || slipErr)}`);
                  }
                }
                
                // eslint-disable-next-line no-console
                atLog(`[AutoEngine] ${executed ? '' : ''} Order Result: ticket=${ticket} | response=${JSON.stringify(result.data)}`);
                riskGate = executed ? 'live order sent' : 'order request failed';
                // V26.15: Enrich order log with proposed vs actual execution detail
                const executionDetail: Record<string, unknown> = {
                  proposedEntry: orderEntryPrice,
                  proposedSl: orderPayload.sl,
                  proposedTp: orderPayload.tp,
                  originalDecisionPrice: originalLastClose,
                };
                if (executed && ticket) {
                  try {
                    const filledPos = (await marketDataService.fetchPositions(false)).find(p => p.ticket === ticket);
                    if (filledPos) {
                      const slippagePoints = round2(filledPos.priceOpen - orderEntryPrice);
                      const filledRisk = sl !== null ? Math.abs(filledPos.priceOpen - sl) : 0;
                      const filledReward = tp !== null ? Math.abs(tp - filledPos.priceOpen) : 0;
                      const filledRRR = filledRisk > 0 ? round2(filledReward / filledRisk) : 0;
                      executionDetail.actualEntry = filledPos.priceOpen;
                      executionDetail.actualSl = filledPos.sl;
                      executionDetail.actualTp = filledPos.tp;
                      executionDetail.slippagePoints = slippagePoints;
                      executionDetail.slippageDirection = slippagePoints === 0 ? 'NONE' : (side === 'BUY' ? (slippagePoints > 0 ? 'ADVERSE' : 'FAVORABLE') : (slippagePoints < 0 ? 'ADVERSE' : 'FAVORABLE'));
                      executionDetail.proposedRRR = round2(actualRrr);
                      executionDetail.filledRRR = filledRRR;
                    }
                  } catch { /* non-critical */ }
                }
                orderLog = { ...orderLog, response: result.data, ticket, executed, executionDetail };
                pushDecisionTrace(gateTrace, 'order_send', executed ? 'EXECUTED' : 'FAILED', riskGate, {
                  ticket,
                  response: result.data as Record<string, unknown>,
                  executionDetail,
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
      }));

      context.lastDecisions = decisions;
      context.appendDecisionFeed(context.state.cycleCount + 1, cfg.timeframe, decisions);
      context.openJournal = persistenceService.loadOpenJournal();
      const nExec = decisions.filter((it) => it.executed).length;
      const nSkip = decisions.filter((it) => it.side === 'SKIP' || (!it.executed && it.riskGate !== 'allowed' && it.riskGate !== 'paper mode')).length;
      const nNoSignal = decisions.filter((it) => it.decisionType === 'NO_SIGNAL').length;
      const nBlocked = decisions.filter((it) => it.decisionType === 'ORDER_BLOCKED').length;
      // eslint-disable-next-line no-console
      atLog(`[AutoEngine]  Cycle #${context.state.cycleCount + 1} completed: ${nExec} executed | ${nSkip} skipped (${nBlocked} blocked | ${nNoSignal} no-signal)`);
      for (const d of decisions) {
		// eslint-disable-next-line no-console
        atLog(`   ${d.symbol.padEnd(6)} | ${d.side.padEnd(4)} | ${d.rationale}  (Gate: ${d.riskGate})`);
      }
      context.state = {
        ...context.state,
        phase: 'IDLE',
        cycleCount: context.state.cycleCount + 1,
        openTradesTracked: positions.length,
        message: `server cycle done ${nExec} executed / ${nSkip} skipped`,
      };
      context.persistRuntime();
    } catch (error) {
      atError('[AutoEngine]  Critical error in runCycle:', error);
      context.state = { 
        ...context.state, 
        phase: 'IDLE', 
        message: `cycle failed: ${String((error as Error)?.message || error)}` 
      };
      } finally {
        timer();
        context.cycleBusy = false;
      }
    });
  }
}

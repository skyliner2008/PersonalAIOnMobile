import { traceStorage } from '../../logger.js';
import { callBridge } from '../../bridgeClient.js';
import { 
  matchHistoryDeal, 
  detectCloseReason, 
  profitToR, 
  extractDealClosePrice, 
  extractHistoryOpenPrice,
  classifyOutcome, 
  aggregateDealProfits,
  resolveDealCloseAtMs
} from '../journal.js';
import { persistenceService } from './PersistenceService.js';
import { knowledgeGraph } from '../knowledgeGraph.js';
import { agentOrchestrator } from '../agentOrchestrator.js';
import { vectorStore } from '../vectorStore.js';
import { detectFailurePattern } from '../agents/postMortem.js';
import { broadcast } from '../../mt5RealtimeHub.js';
import { 
  isSymbolTradable, 
  isMarketOpen 
} from '../helpers/marketHelpers.js';
import { closedTradeService } from '../helpers/closedTradeService.js';
import { 
  tickSize, 
  roundToTick, 
  round2, 
  atLog, 
  atWarn, 
  nowIso 
} from '../utils.js';
import { buildPositionCluster } from '../helpers/tradingHelpers.js';
import { spreadBaseline } from '../spreadBaseline.js';
import { 
  extractV25PathMilestone, 
  v25MilestoneReached 
} from './V25MilestoneProtection.js';
import { tickBuffers } from '../v25/TickBuffer.js';
import { getSmcSnapshotV2 } from '../analyzers/smc.js';
import { 
  detectRegimeFlipAction, 
  computeSmcTrailTargets 
} from './SmcTrailManager.js';
import { tradingExecutionService } from './TradingExecutionService.js';
import type { AutoTradingService } from '../../autoTradingService.js';
import type { 
  ManagementPlan,
  PositionRow,
  JournalRow
} from '../types.js';

const staleCloseBackoff = new Map<number, number>();
const earlyInvalidationBackoff = new Map<number, number>();

export class PositionManagerEngine {
  public static async runManage(context: AutoTradingService): Promise<void> {
    if (context.manageBusy) return;
    // 2026-04-24: no longer block on cycleBusy  management runs in parallel with
    // the analysis cycle. They share a 500ms snapshot cache in MarketDataService
    // so there's no extra bridge load, and management no longer waits 20-40s for
    // the LLM phase to finish before checking BE/Trail/Hedge.
    context.manageBusy = true;
    
    // Generate a unique trace ID for this management cycle
    const traceId = `manage-${Date.now().toString().slice(-6)}`;
    
    await traceStorage.run({ traceId }, async () => {
      try {
        // Market Guard: Don't manage positions if all watchlist markets are closed.
        // This prevents "Modify" calls (SL/TP) that would be rejected by MT5 (retcode 10018).
        const marketOpen = await isMarketOpen(context.config);
        if (!marketOpen) {
          // If the cycle loop already put us to sleep, just stay silent.
          if (context.state.phase !== 'SLEEPING') {
            context.state = { ...context.state, phase: 'SLEEPING', message: 'Market closed. Management paused.' };
            context.persistRuntime();
          }
          return;
        }

        const positions = await context.fetchPositions();
        const hasOpenJournal = context.openJournal.some((it) => it.outcome === 'OPEN');
        if (positions.length === 0 && !hasOpenJournal) {
          context.state = { ...context.state, phase: 'IDLE', openTradesTracked: 0, message: 'server manage idle (no open positions)' };
          context.persistRuntime();
          return;
        }
        if (Date.now() - context.lastMarketAwareManageAt < 15_000) {
          context.state = { ...context.state, phase: 'IDLE', openTradesTracked: positions.length, message: 'server manage skipped (cycle already managed positions)' };
          context.persistRuntime();
          return;
        }

        // eslint-disable-next-line no-console
        atLog('[AutoEngine]  Running Trade Manager...');
        context.state = { ...context.state, phase: 'MANAGING', message: 'server managing positions' };
        context.persistRuntime();

        const openTickets = new Set(positions.map((it) => it.ticket));
        const history = await context.fetchHistory(500);

        for (const row of context.openJournal.filter((it) => it.outcome === 'OPEN')) {
          const resolvedTicket = row.mt5Ticket ?? context.matchTicketFromOpenPositions(row, positions);
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
              closedTradeService.logDeferredClosedDeal(resolvedTicket, row.symbol, 'outcome classification');
              continue;
            }
            closedTradeService.clearDeferredLog(resolvedTicket, 'outcome classification');
            const closePrice = extractDealClosePrice(deal) ?? row.closePrice ?? row.entry ?? 0;
            const profit = aggregateDealProfits(row, history);
            const closeReason = detectCloseReason(deal);
            const closeAt = resolveDealCloseAtMs(deal);
            const brokerOpenPrice = extractHistoryOpenPrice(row, history);
            const profitR = profitToR(row.entry, row.sl, closePrice, row.side, brokerOpenPrice);
            const outcome = classifyOutcome(profit, profitR);
            persistenceService.upsertJournal({
              ...row,
              closeReason,
              closePrice,
              closeAt,
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
              closeAt,
              brokerOpenPrice,
              historyDeal: deal,
            });
            context.markManagementOutcome(resolvedTicket, row.decisionId, outcome, profit, profitR, closeReason);

            // Update Knowledge Graph with the normalized outcome so it learns from
            // R-multiple fallback when broker profit is missing/zero.
            knowledgeGraph.addTradeInsight(row.symbol, row.strategy, outcome === 'LOSS' ? 'LOSS' : 'WIN', closeReason);

            // Feed the closed outcome back into the vector memory so future
            // retrieval can see real (entry, outcome) pairs  not just the
            // open-side snapshot. Embedding failure must never block close.
            if (context.config.apiKey) {
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
                const vec = await agentOrchestrator.getEmbeddingForConfig(context.config, outcomeText);
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

            if (context.config.notificationSettings?.onClose) {
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
            (p) => !context.openJournal.some((j) => j.mt5Ticket === p.ticket)
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
                timeframe: context.config.timeframe,
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
          const j = context.openJournal.find((it) => it.mt5Ticket === p.ticket);
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
              context.mtfCache.clear(); // invalidate snapshots / cache via service if needed
            } catch (err) {
              atWarn(`[AutoEngine] ⚠️ Failed to repair broker SL/TP for #${p.ticket}: ${err}`);
            }
          }
        }

        if (positions.length > 0) {
          let totalHeatR = 0;
          const auditLines = positions.map((p) => {
            const j = context.openJournal.find((it) => it.mt5Ticket === p.ticket);
            if (!j || j.entry === null || j.sl === null) {
              return `  ${p.symbol}#${p.ticket} ${p.side} vol=${p.volume} pnl=${round2(p.profit)}  NO_JOURNAL_MATCH (BE/Trail cannot evaluate)`;
            }
            const entryForR = p.priceOpen > 0 ? p.priceOpen : j.entry;
            const risk = Math.abs(entryForR - j.sl);
            const reward = p.side === 'BUY' ? p.priceCurrent - entryForR : entryForR - p.priceCurrent;
            const r = risk > 0 ? reward / risk : 0;
            if (r < 0) totalHeatR += Math.abs(r); // Accumulate drawdown risk
            // V26.15: Peak profit/loss tracking per position
            const currentR = round2(r);
            const prevPeak = j.peakProfitR ?? -999;
            const prevDd = j.maxDrawdownR ?? 999;
            let peakChanged = false;
            if (currentR > prevPeak) { j.peakProfitR = currentR; peakChanged = true; }
            if (currentR < prevDd) { j.maxDrawdownR = currentR; peakChanged = true; }
            if (peakChanged) {
              j.updatedAt = Date.now();
              persistenceService.upsertJournal(j);
            }
            
            const isScalp = j.strategy === 'SCALPING' || j.strategy === 'MEAN_REVERSION' || String(j.strategy ?? '').toUpperCase().includes('SCALP');
            const sameSideOpen = positions.filter((it) => it.symbol === p.symbol && it.side === p.side).length;
            // Scalp baskets are short-lived; stacked entries need faster protection
            // once the first leg gets paid, otherwise small wins often round-trip.
            const beTrigger = isScalp
              ? (sameSideOpen >= 2 ? (context.config.adaptive?.scalpStackBreakEvenTriggerR ?? 0.15) : (context.config.adaptive?.scalpBreakEvenTriggerR ?? 0.3))
              : (context.config.breakEvenTriggerR ?? 0.5);
            const trailTrigger = isScalp
              ? (sameSideOpen >= 2 ? (context.config.adaptive?.scalpStackTrailAfterR ?? 0.3) : (context.config.adaptive?.scalpTrailAfterR ?? 0.4))
              : (context.config.trailAfterR ?? 0.6);
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

        if (!context.config.enableLiveTrading) {
          atLog('[AutoEngine]  Manager: enableLiveTrading=false  skipping BE/Trail/GaeMai phase.');
        }
        if (context.config.enableLiveTrading) {
          // V26.21 EA-only hardening: cut trades that fail immediately instead of
          // waiting for a full SL. The prior early-invalidation logic existed in the
          // planner, but this manager sweep is what runs continuously when AI is off.
          if (context.config.adaptive?.enableEarlyInvalidation ?? true) {
            const nowMsEarly = Date.now();
            const reduceAtR = context.config.adaptive?.earlyInvalidationR ?? -0.45;
            const closeAtR = context.config.adaptive?.earlyInvalidationCloseR ?? -0.65;
            const minAgeMs = context.config.adaptive?.earlyInvalidationMinAgeMs ?? 90_000;
            const maxPeakR = context.config.adaptive?.earlyInvalidationMaxPeakR ?? 0.25;
            let earlyCandidates = 0;
            let earlyActions = 0;

            for (const p of positions) {
              const backoff = earlyInvalidationBackoff.get(p.ticket) ?? 0;
              if (nowMsEarly < backoff) continue;

              const j = context.openJournal.find((it) => it.mt5Ticket === p.ticket);
              if (!j || j.entry === null || j.sl === null) continue;
              const entryForR = p.priceOpen > 0 ? p.priceOpen : j.entry;
              const risk = Math.abs(entryForR - j.sl);
              if (!(risk > 0)) continue;

              const reward = p.side === 'BUY' ? p.priceCurrent - entryForR : entryForR - p.priceCurrent;
              const r = reward / risk;
              const peakR = Number.isFinite(Number(j.peakProfitR)) ? Number(j.peakProfitR) : r;
              const tsCandidates = [
                (typeof p.openedAtMs === 'number' && p.openedAtMs > 0) ? p.openedAtMs : 0,
                (typeof j.createdAt === 'number' && j.createdAt > 0) ? j.createdAt : 0,
              ].filter((t) => t > 0);
              if (tsCandidates.length === 0) continue;
              const ageMs = Math.max(0, nowMsEarly - Math.min(...tsCandidates));
              const proofFailed = ageMs >= minAgeMs && peakR <= maxPeakR && r <= reduceAtR;
              if (!proofFailed) continue;

              earlyCandidates += 1;
              const tradeStatus = await isSymbolTradable(p.symbol);
              if (!tradeStatus.tradable) {
                earlyInvalidationBackoff.set(p.ticket, nowMsEarly + 5 * 60_000);
                continue;
              }

              const alreadyReduced = String(j.aiReview ?? '').includes('EARLY_INVALIDATION_REDUCED');
              const mode: 'REDUCE' | 'CLOSE' = (r <= closeAtR || alreadyReduced) ? 'CLOSE' : 'REDUCE';
              const requestedReduce = mode === 'REDUCE' ? p.volume * 0.5 : null;
              const normalizedReduce = mode === 'REDUCE'
                ? tradingExecutionService.normalizeCloseVolume(requestedReduce, p.volume, 'REDUCE', context.config)
                : null;
              if (mode === 'REDUCE' && (!normalizedReduce || normalizedReduce <= 0)) continue;

              const body: Record<string, unknown> = { ticket: p.ticket, symbol: p.symbol };
              if (mode === 'REDUCE') body.volume = normalizedReduce;
              const ageMin = Math.round(ageMs / 60_000);
              const reason = `early_invalidation_${mode.toLowerCase()} (R=${r.toFixed(2)}, peakR=${round2(peakR)}, age=${ageMin}m, thresholds reduce<=${reduceAtR}/close<=${closeAtR})`;
              atWarn(`[AutoEngine]  Early invalidation: ${mode} ${p.symbol}#${p.ticket} ${p.side} ${reason}`);

              try {
                await callBridge('POST', ['/close', '/mt5/close', '/api/mt5/close'], {
                  body,
                  timeoutMs: 20_000,
                  priority: 'critical',
                });
                context.insertManagementJournal({
                  managementId: `MGMT-EARLY-INV-${p.ticket}-${nowMsEarly}`,
                  symbol: p.symbol,
                  timeframe: j.timeframe,
                  mode,
                  status: 'EXECUTED',
                  side: p.side,
                  targetTicket: p.ticket,
                  volume: mode === 'REDUCE' ? normalizedReduce : p.volume,
                  reason,
                  result: { r: round2(r), peakR: round2(peakR), ageMin },
                });
                if (mode === 'REDUCE') {
                  persistenceService.upsertJournal({
                    ...j,
                    aiReview: `${j.aiReview || ''}; EARLY_INVALIDATION_REDUCED`,
                    updatedAt: Date.now(),
                  });
                }
                earlyInvalidationBackoff.set(p.ticket, nowMsEarly + 5 * 60_000);
                earlyActions += 1;
              } catch (err) {
                atWarn(`[AutoEngine]  Early invalidation failed for #${p.ticket}: ${String((err as any)?.message || err)}`);
                earlyInvalidationBackoff.set(p.ticket, nowMsEarly + 5 * 60_000);
              }
            }

            if (positions.length > 0) {
              atLog(`[AutoEngine]  Early-Invalidation swept ${positions.length} pos -> candidates=${earlyCandidates} actions=${earlyActions} (reduce<=${reduceAtR}R close<=${closeAtR}R maxPeak=${maxPeakR}R minAge=${Math.round(minAgeMs / 1000)}s)`);
            }
            if (earlyActions > 0) {
              const refreshedPositions = await context.fetchPositions().catch(() => positions);
              positions.length = 0;
              positions.push(...refreshedPositions);
              context.openJournal = persistenceService.loadOpenJournal();
            }
          }

          // --- V24.1 (2026-05-08) Stale-Position Time Stop ---
          // Close positions that have been stuck in the indecision zone for too long.
          // Live log showed 3 SELLs sitting at R∈[-0.4, +0.1] for 60+ min — neither
          // hitting BE/Trail nor scale-in recovery. They tie up risk budget and rarely
          // recover. This sweep cuts that churn.
          // V24.1.1 FIX (2026-05-08): use the OLDER of (p.openedAtMs, j.createdAt) and
          // clamp ageMs to 0 — broker time can be ahead of server time (UTC skew),
          // making nowMs - openedAt go negative and silently disabling the gate.
          const staleMaxAge = context.config.stalePositionMaxAgeMs ?? 45 * 60_000;
          const staleMinR   = context.config.stalePositionMinR   ?? -0.25;
          const staleMaxR   = context.config.stalePositionMaxR   ?? 0.30;
          const nowMs = Date.now();
          let staleCandidates = 0;
          let staleClosed = 0;
          for (const p of positions) {
            const backoff = staleCloseBackoff.get(p.ticket) ?? 0;
            if (nowMs < backoff) continue;

            // V24.1.4: Check if symbol is tradable before trying to close.
            // This avoids spamming close requests when market is closed.
            const tradeStatus = await isSymbolTradable(p.symbol);
            if (!tradeStatus.tradable) {
              // Market is closed! Backoff for 5 minutes so we don't spam isSymbolTradable.
              staleCloseBackoff.set(p.ticket, nowMs + 5 * 60 * 1000);
              continue;
            }

            const j = context.openJournal.find((it) => it.mt5Ticket === p.ticket);
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
                context.insertManagementJournal({
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
                if (context.config.notificationSettings?.onClose) {
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
          const refreshedPositions = await context.fetchPositions().catch(() => positions);
          if (refreshedPositions.length !== positions.length) {
            // mutate the array length so downstream loops skip closed tickets
            positions.length = 0;
            for (const rp of refreshedPositions) positions.push(rp);
            context.openJournal = persistenceService.loadOpenJournal();
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
            const tick = tickSize(symbol, context.config.risk?.pointValueOverride || {});
            let totalProfit = 0;
            let totalRisk = 0;
            let unTrailedVolume = 0;
            let unTrailedWeightedPrice = 0;
            const unTrailedGroup = [];
            
            for (const p of group) {
              const j = context.openJournal.find((it) => it.mt5Ticket === p.ticket);
              if (!j || j.entry === null || j.sl === null) continue;
              const realEntry = p.priceOpen > 0 ? p.priceOpen : j.entry;
              const riskPoints = Math.abs(realEntry - j.sl);
              if (riskPoints <= 0) continue;
              
              const rewardPoints = p.side === 'BUY' ? p.priceCurrent - realEntry : realEntry - p.priceCurrent;
              const r = rewardPoints / riskPoints;
              
              if (r < context.config.trailAfterR) {
                unTrailedGroup.push({ position: p, journal: j, r, tick, realEntry });
                totalProfit += p.profit;
                unTrailedVolume += p.volume;
                unTrailedWeightedPrice += realEntry * p.volume;
                
                // Estimate dollar risk dynamically based on current profit vs reward points
                let pointValue = 1;
                if (Math.abs(rewardPoints) > 0.00001) {
                  pointValue = Math.abs(p.profit / rewardPoints);
                } else if (context.config.risk?.pointValueOverride?.[symbol]) {
                  pointValue = context.config.risk.pointValueOverride[symbol] * p.volume;
                }
                totalRisk += Math.abs(riskPoints * pointValue);
              }
            }

            if (unTrailedGroup.length > 0 && totalRisk > 0) {
              const clusterR = totalProfit / totalRisk;
              const activationR = context.config.risk?.clusterProfitProtectionActivationR ?? 0.75;
              const dropPct = context.config.risk?.clusterProfitProtectionDropPct ?? 50;
              const minDollar = context.config.risk?.clusterProfitProtectionMinDollar ?? 20.0;
              
              let currentMax = context.clusterMaxProfit.get(key) || 0;
              // Activate tracking when clusterR >= activationR OR profit >= minDollar
              if ((clusterR >= activationR || totalProfit >= minDollar) && totalProfit > currentMax) {
                currentMax = totalProfit;
                context.clusterMaxProfit.set(key, currentMax);
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
                    
                    context.insertManagementJournal({
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
                context.clusterMaxProfit.set(key, 0); // Reset after trigger
              }
            } else if (unTrailedGroup.length === 0) {
              context.clusterMaxProfit.delete(key);
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
              const j = context.openJournal.find((it) => it.mt5Ticket === p.ticket);
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
                  context.insertManagementJournal({
                    managementId: `MGMT-CLP-${p.ticket}-${Date.now()}`,
                    symbol: p.symbol,
                    timeframe: context.config.timeframe,
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
              const candlesForSmc = await context.fetchCandles(sym, context.config.timeframe, 180);
              if (candlesForSmc.length >= 60) {
                smcSnapshot = getSmcSnapshotV2(candlesForSmc, sym, context.config.timeframe);
              }
            } catch (e) {
              atWarn(`[AutoEngine]  SMC snapshot failed for ${sym}: ${e}`);
            }
            if (!smcSnapshot) continue;
            const cluster = buildPositionCluster(sym, symbolPositions);

            // ── (1) Regime-Flip Exit ──
            // If H4 structure flipped against the cluster (CHoCH down vs BUY cluster
            // or vice-versa), close the losing leg(s) instead of waiting for SL hit.
            try {
              const flip = detectRegimeFlipAction(cluster, smcSnapshot, context.openJournal);
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
                    context.insertManagementJournal({
                      managementId: `MGMT-FLIP-${ticket}-${Date.now()}`,
                      symbol: p.symbol,
                      timeframe: context.config.timeframe,
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
              const trailTargets = computeSmcTrailTargets(cluster, smcSnapshot, context.openJournal, 1.0);
              for (const target of trailTargets) {
                const p = symbolPositions.find((it) => it.ticket === target.ticket);
                if (!p) continue;
                const j = context.openJournal.find((it) => it.mt5Ticket === target.ticket);
                if (!j) continue;
                const tick = tickSize(p.symbol, context.config.risk.pointValueOverride || {});
                const newSl = roundToTick(target.proposedSl, tick) ?? target.proposedSl;
                try {
                  await callBridge('POST', ['/modify', '/mt5/modify', '/api/mt5/modify'], {
                    body: { ticket: target.ticket, symbol: p.symbol, sl: newSl },
                    priority: 'critical',
                  });
                  context.insertManagementJournal({
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
            const matched = context.openJournal.find((it) => it.mt5Ticket === position.ticket);
            if (!matched || matched.entry === null || matched.sl === null) continue;
            // V24.1: skip ATR fallback trail if SMC trail already moved this ticket
            const smcAlreadyTrailed = smcTrailedTickets.has(position.ticket);
            const tick = tickSize(position.symbol, context.config.risk.pointValueOverride || {});
            
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
              ? (sameSideOpen >= 2 ? (context.config.adaptive?.scalpStackBreakEvenTriggerR ?? 0.15) : (context.config.adaptive?.scalpBreakEvenTriggerR ?? 0.3))
              : (context.config.breakEvenTriggerR ?? 0.5);
            const trailTriggerR = isScalp
              ? (sameSideOpen >= 2 ? (context.config.adaptive?.scalpStackTrailAfterR ?? 0.3) : (context.config.adaptive?.scalpTrailAfterR ?? 0.4))
              : (context.config.trailAfterR ?? 0.6);

            const milestone = extractV25PathMilestone(matched);
            const milestoneSince = Math.max(0, Math.min(matched.createdAt || Date.now(), (position.openedAtMs && position.openedAtMs > 0) ? position.openedAtMs : Date.now()));
            const milestoneTicks = milestone ? tickBuffers.get(position.symbol.toUpperCase()).sinceMs(milestoneSince) : [];
            const milestoneHit = v25MilestoneReached(position.side, milestone, position.priceCurrent, milestoneTicks, milestoneSince);
            if (milestoneHit && !context.v25MilestoneProtectedTickets.has(position.ticket)) {
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
                context.v25MilestoneProtectedTickets.add(position.ticket);
                context.insertManagementJournal({
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
                position.sl = newSl; // Fix: Update local state to prevent trailing stop regression in same cycle
                context.insertManagementJournal({
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
                position.sl = newSl; // Fix: Keep local state updated
                context.insertManagementJournal({
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
            // Deterministic banner: if a position is past -1.5R, raise a CRITICAL notification
            if (r < -1.5 && context.config.notificationSettings?.onLoss) {
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

        context.openJournal = persistenceService.loadOpenJournal();
        context.state = { ...context.state, phase: 'IDLE', openTradesTracked: positions.length, message: 'server manage done' };
        context.persistRuntime();
      } catch (error) {
        context.state = { ...context.state, phase: 'IDLE', message: `manage failed: ${String((error as Error)?.message || error)}` };
        context.persistRuntime();
      } finally {
        context.manageBusy = false;
      }
    });
  }

  public static async executeManagementPlan(
    context: AutoTradingService,
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
      context.config,
      context.fetchPositions.bind(context),
      persistenceService.upsertJournal.bind(persistenceService),
      context.openJournal
    );
  }
}

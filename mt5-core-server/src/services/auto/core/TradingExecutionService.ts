import { callBridge } from '../../bridgeClient.js';
import { broadcast } from '../../mt5RealtimeHub.js';
import {
  asNumber,
  asRecord,
  round2,
  roundToTick,
  tickSize,
  clamp,
  moneyPerPriceUnit,
  unwrapData,
  nowIso,
  getLogTime,
  atLog,
  atWarn,
  atError
} from '../utils.js';
import { sessionAnalyzer } from '../../auto/analyzers/session.js';
import { KellySizer } from '../../auto/risk/kelly.js';
import { marketDataService } from './MarketDataService.js';
import { 
  AccountSnapshot, 
  PositionRow, 
  AutoTradingConfig,
  ManagementPlan,
  ManagementMode
} from '../types.js';

class TradingExecutionService {
  private lastHedgeAt: Map<string, number> = new Map();
  private lastScaleInAt: Map<string, number> = new Map();
  private recentEntryIntents: Map<string, { price: number; ts: number; comment: string }> = new Map();

  public nearDuplicateEntryIssue(
    symbol: string,
    side: 'BUY' | 'SELL',
    entryPrice: number,
    livePositions: PositionRow[],
    config: AutoTradingConfig,
    opts: { atr?: number; minDistance?: number; baseMinDistance?: number; atrMultiplier?: number; ttlMs?: number; comment?: string } = {},
  ): string | null {
    if (config.adaptive?.preventNearDuplicateEntries === false) return null;
    if (!Number.isFinite(entryPrice) || entryPrice <= 0) return 'near-duplicate guard: invalid entry price';

    const upper = symbol.toUpperCase();
    const isXau = upper.includes('XAU') || upper.includes('GOLD');
    const adaptive = config.adaptive || {};
    const configuredBaseMin = isXau ? (adaptive.sequentialMinEntryDistanceXAU ?? 5.0) : Math.max(entryPrice * 0.0005, 0.0005);
    const baseMin = opts.baseMinDistance ?? configuredBaseMin;
    const atrMin = opts.atr && opts.atr > 0
      ? opts.atr * (opts.atrMultiplier ?? adaptive.sequentialMinEntryDistanceAtrMul ?? 0.10)
      : 0;
    const minDistance = Math.max(baseMin, atrMin, opts.minDistance ?? 0);

    const sameSidePositions = livePositions
      .filter((p) => p.symbol.toUpperCase() === upper && p.side === side && p.priceOpen > 0);
    const nearest = sameSidePositions
      .map((p) => ({ position: p, distance: Math.abs(entryPrice - p.priceOpen) }))
      .sort((a, b) => a.distance - b.distance)[0];
    if (nearest && nearest.distance < minDistance) {
      return `NEAR_DUPLICATE_ENTRY: ${side} entry ${round2(entryPrice)} is ${round2(nearest.distance)} from open #${nearest.position.ticket} @ ${round2(nearest.position.priceOpen)} (min ${round2(minDistance)})`;
    }

    const key = `${upper}:${side}`;
    const ttlMs = opts.ttlMs ?? adaptive.sequentialDuplicateIntentTtlMs ?? 300_000;
    const now = Date.now();
    const recent = this.recentEntryIntents.get(key);
    if (recent && now - recent.ts <= ttlMs) {
      const recentDist = Math.abs(entryPrice - recent.price);
      if (recentDist < minDistance) {
        return `NEAR_DUPLICATE_INTENT: ${side} entry ${round2(entryPrice)} is ${round2(recentDist)} from recent ${recent.comment || 'order'} @ ${round2(recent.price)} (${Math.round((now - recent.ts) / 1000)}s ago, min ${round2(minDistance)})`;
      }
    }

    return null;
  }

  public rememberEntryIntent(symbol: string, side: 'BUY' | 'SELL', entryPrice: number, comment: string = 'order'): void {
    if (!Number.isFinite(entryPrice) || entryPrice <= 0) return;
    this.recentEntryIntents.set(`${symbol.toUpperCase()}:${side}`, {
      price: entryPrice,
      ts: Date.now(),
      comment,
    });
  }

  public async executeManagementPlan(
    symbol: string,
    plan: ManagementPlan,
    sl: number | null,
    tp: number | null,
    aiDecision: any,
    config: AutoTradingConfig,
    fetchPositions: () => Promise<PositionRow[]>,
    upsertJournal: (row: any) => void,
    openJournal: any[]
  ): Promise<{ executed: boolean; riskGate: string; ticket: number | null; side?: 'BUY' | 'SELL' }> {
    if (plan.mode === 'HOLD') {
      return { executed: false, riskGate: plan.reason, ticket: null };
    }

    if (!config.enableLiveTrading) {
      return {
        executed: false,
        riskGate: `paper manage: ${plan.mode.toLowerCase()} | ${plan.reason}`,
        ticket: null,
        side: plan.side,
      };
    }

    try {
      if ((plan.mode === 'HEDGE' || plan.mode === 'SCALE_IN') && plan.side && plan.volume && sl !== null && tp !== null) {
        if (plan.mode === 'SCALE_IN' && plan.rotateTicket && plan.rotateVolume) {
          try {
            atLog(`[AutoEngine] 🔄 Rotation: closing worst ticket #${plan.rotateTicket} (vol=${plan.rotateVolume}) before scale-in`);
            await callBridge('POST', ['/close', '/mt5/close', '/api/mt5/close'], {
              body: { ticket: plan.rotateTicket, symbol, volume: plan.rotateVolume },
              timeoutMs: 20_000,
              priority: 'critical',
            });
            marketDataService.invalidateSnapshots();
          } catch (err) {
            atWarn(`[AutoEngine] ⚠️ Rotation close failed for #${plan.rotateTicket}, proceeding with scale-in anyway: ${err}`);
          }
        }
        const tSize = tickSize(symbol);
        const orderPayload = {
          symbol,
          side: plan.side.toLowerCase(),
          volume: plan.volume,
          sl: roundToTick(sl, tSize),
          tp: roundToTick(tp, tSize),
          type: 'market',
          comment: `A_${plan.mode}_${symbol}_${plan.summary}`.substring(0, 26).replace(/[^\w\s-]/g, ''),
        };
        const result = await callBridge('POST', ['/order', '/mt5/order', '/api/mt5/order'], {
          body: orderPayload,
          timeoutMs: 25_000,
          priority: 'critical',
        });
        const ticket = this.extractTicket(result.data);
        const executed = ticket !== null || JSON.stringify(result.data).includes('"success":true');

        if (executed) {
          const stampMap = plan.mode === 'HEDGE' ? this.lastHedgeAt : this.lastScaleInAt;
          stampMap.set(symbol, Date.now());
          // Broker state just changed — drop the snapshot cache so the next
          // fetchPositions()/fetchAccount() call reflects reality.
          marketDataService.invalidateSnapshots();
        }
        const detail = `${plan.mode.toLowerCase()} ${plan.side} ${plan.volume} | ${plan.reason}`;
        if (executed && config.notificationSettings?.onOrder) {
          broadcast({
            type: 'intelligence_alert',
            title: `${plan.mode}: ${symbol}`,
            message: `${detail}. AI Confidence: ${asNumber(aiDecision?.confidence, 0)}%`,
            priority: 'CRITICAL',
            at: Date.now(),
          });
        }
        return {
          executed,
          riskGate: executed ? detail : `${plan.mode.toLowerCase()} order request failed`,
          ticket,
          side: plan.side,
        };
      }

      if ((plan.mode === 'REDUCE' || plan.mode === 'CLOSE' || plan.mode === 'PARTIAL_CLOSE')) {
        const ticketsToProcess = plan.tickets && plan.tickets.length > 0 ? plan.tickets : (plan.ticket ? [plan.ticket] : []);
        if (ticketsToProcess.length === 0) {
          return { executed: false, riskGate: `${plan.mode.toLowerCase()} skipped: no tickets provided`, ticket: null };
        }

        const livePositions = await fetchPositions();
        let executedCount = 0;
        let totalVolumeClosed = 0;

        for (const t of ticketsToProcess) {
          const targetPosition = livePositions.find((p) => p.ticket === t);
          if (!targetPosition) {
            atWarn(`[AutoEngine] ${plan.mode.toLowerCase()} skipped for #${t}: not found in live positions`);
            continue;
          }

          const closeVolume = this.normalizeCloseVolume(plan.closeVolume ?? null, targetPosition.volume, plan.mode, config);
          if (closeVolume === null || closeVolume <= 0) {
            atWarn(`[AutoEngine] ${plan.mode.toLowerCase()} skipped for #${t}: invalid normalized volume`);
            continue;
          }

          const closePayload = {
            ticket: t,
            symbol,
            volume: closeVolume,
          };

          try {
            const result = await callBridge('POST', ['/close', '/mt5/close', '/api/mt5/close'], {
              body: closePayload,
              timeoutMs: 20_000,
              priority: 'critical',
            });
            const success = JSON.stringify(result.data).includes('"success":true');
            if (success) {
              executedCount++;
              totalVolumeClosed += closeVolume;
              if (plan.mode === 'PARTIAL_CLOSE') {
                const matched = openJournal.find(j => j.mt5Ticket === t);
                if (matched) {
                    const partialToken = this.partialTokenForPlan(plan);
                    upsertJournal({
                        ...matched,
                        aiReview: `${matched.aiReview || ''}; ${partialToken}`,
                        updatedAt: Date.now()
                    });
                }
              }
            }
          } catch (err) {
            atError(`[AutoEngine] Close failed for #${t}:`, err);
          }
        }

        if (executedCount > 0) marketDataService.invalidateSnapshots();

        return {
          executed: executedCount > 0,
          riskGate: executedCount > 0
            ? `${plan.mode.toLowerCase()} ${executedCount}/${ticketsToProcess.length} tickets | totalVol=${totalVolumeClosed} | ${plan.reason}`
            : `${plan.mode.toLowerCase()} all requests failed`,
          ticket: ticketsToProcess[0],
        };
      }

      // --- BREAKEVEN / TRAIL / SL_MODIFY ---
      if ((plan.mode === 'BREAKEVEN' || plan.mode === 'TRAIL') && plan.ticket) {
        const livePositions = await fetchPositions();
        const targetPosition = livePositions.find((p) => p.ticket === plan.ticket);
        const journal = openJournal.find(j => j.mt5Ticket === plan.ticket);
        
        if (!targetPosition || !journal) {
          return { executed: false, riskGate: `${plan.mode} failed: position/journal not found`, ticket: plan.ticket };
        }

        let newSl = targetPosition.sl;
        if (plan.mode === 'BREAKEVEN') {
            const entry = journal.entry;
            // Spec V19.6 §4.1: buffer = 0.5 points for XAUUSD-family, 0 for all other pairs.
            // A 0.5-point buffer on Forex would be 5 pips — far too wide for a pure BE.
            const buffer = symbol.toUpperCase().includes('XAU') || symbol.toUpperCase().includes('GOLD') ? 0.5 : 0;
            newSl = targetPosition.side === 'BUY' ? entry + buffer : entry - buffer;
        } else if (plan.mode === 'TRAIL' && sl !== null) {
            newSl = sl;
        }

        // Only modify if SL actually moves in our favor
        const slChanged = Math.abs(newSl - targetPosition.sl) > 0.01;
        if (!slChanged) {
             return { executed: false, riskGate: `${plan.mode} skipped: SL already at target`, ticket: plan.ticket };
        }

        const tSize = tickSize(symbol);
        const modifyPayload = {
          ticket: plan.ticket,
          symbol,
          sl: roundToTick(newSl, tSize),
          tp: roundToTick(targetPosition.tp, tSize), // Keep existing TP, but ensure rounding
        };

        const result = await callBridge('POST', ['/modify', '/mt5/modify', '/api/mt5/modify'], {
          body: modifyPayload,
          timeoutMs: 15_000,
          priority: 'critical',
        });

        const executed = JSON.stringify(result.data).includes('"success":true');
        if (executed) marketDataService.invalidateSnapshots();
        if (executed && config.notificationSettings?.onOrder) {
             broadcast({
                type: 'intelligence_alert',
                title: `${plan.mode}: ${symbol}`,
                message: `${plan.mode} successful for #${plan.ticket} at ${round2(newSl)}. ${plan.reason}`,
                priority: 'LOW',
                at: Date.now(),
              });
        }

        return {
          executed,
          riskGate: executed ? `${plan.mode.toLowerCase()} #${plan.ticket} SL -> ${round2(newSl)}` : `${plan.mode.toLowerCase()} modify failed`,
          ticket: plan.ticket,
        };
      }
    } catch (err: any) {
      return {
        executed: false,
        riskGate: `${plan.mode.toLowerCase()} error: ${String(err?.message || err)}`,
        ticket: null,
        side: plan.side,
      };
    }

    return { executed: false, riskGate: `${plan.mode.toLowerCase()} skipped`, ticket: null, side: plan.side };
  }

  private partialTokenForPlan(plan: ManagementPlan): string {
    const text = `${plan.summary ?? ''} ${plan.reason ?? ''}`.toUpperCase();
    if (text.includes('STAGE 2') || text.includes('2R')) return 'PARTIAL_2R';
    if (text.includes('STAGE 1') || text.includes('1R')) return 'PARTIAL_1R';
    return `PARTIAL_${String(plan.summary || 'UNKNOWN').replace(/\s+/g, '_').toUpperCase()}`;
  }

  public extractTicket(input: unknown): number | null {
    const candidates: Record<string, unknown>[] = [];
    const root = asRecord(input);
    candidates.push(root);
    const data = asRecord(unwrapData(input));
    if (data !== root) candidates.push(data);
    for (const key of ['result', 'response']) {
      const nested = asRecord((root as any)[key]);
      if (Object.keys(nested).length) candidates.push(nested);
    }

    const pickFields = ['order', 'ticket', 'order_ticket', 'position', 'deal'];
    for (const bag of candidates) {
      for (const field of pickFields) {
        const n = asNumber((bag as any)[field], 0);
        if (n > 0) return n;
      }
    }

    const text = JSON.stringify(input ?? '');
    const match = /"(?:order|ticket|order_ticket|position|deal)"\s*:\s*(\d+)/.exec(text);
    const parsed = match ? Number(match[1]) : NaN;
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }

  public normalizeCloseVolume(requested: number | null | undefined, positionVolume: number, mode: ManagementMode, config: AutoTradingConfig): number | null {
    const step = Math.max(config.risk.minLotStep || 0.01, 0.001);
    const minLot = Math.max(config.risk.minLot || step, step);
    const snapDown = (v: number) => round2(Math.floor((v + 1e-9) / step) * step);

    const maxClose = snapDown(positionVolume);
    if (!isFinite(maxClose) || maxClose <= 0) return null;

    let volume = requested !== null && requested !== undefined && requested > 0 ? snapDown(requested) : maxClose;
    if (!isFinite(volume) || volume <= 0) volume = maxClose;
    if (volume > maxClose) volume = maxClose;

    if (volume < minLot) {
      if (mode === 'PARTIAL_CLOSE') {
        volume = maxClose;
      } else {
        volume = Math.min(maxClose, snapDown(minLot));
      }
    }

    if (!isFinite(volume) || volume <= 0) return null;
    return round2(volume);
  }

  public computeVolume(account: AccountSnapshot, stopDistance: number, symbol: string, config: AutoTradingConfig, strategy?: string, learnSummary?: any): number {
    // Kelly-scaled Risk [Phase 3-ก]
    let riskPct = config.risk.riskPerTradePct;
    if (strategy && learnSummary) {
        const stat = (learnSummary.strategyStats || []).find((s: any) => s.strategy === strategy) || null;
        riskPct = KellySizer.computeFraction(stat, riskPct / 100) * 100;
    }

    const riskCash = account.equity * (riskPct / 100);
    const unitValue = moneyPerPriceUnit(symbol, config.risk.pointValueOverride || {});
    const raw = stopDistance > 0 ? riskCash / (stopDistance * unitValue) : config.risk.minLot;
    const step = config.risk.minLotStep;
    const snapped = Math.floor(raw / step) * step;
    const baseVolume = round2(clamp(snapped || step, config.risk.minLot, config.risk.maxLot));

    // Session Volume Throttle [Phase 1-ค]
    const sessionContext = sessionAnalyzer.getCurrentSession();
    const multiplier = sessionAnalyzer.getVolumeMultiplier(sessionContext);
    
    if (multiplier <= 0) return 0; // Refuse outright during QUIET session
    
    return round2(Math.max(config.risk.minLot, baseVolume * multiplier));
  }

  public getLastHedgeAt(symbol: string): number {
    return this.lastHedgeAt.get(symbol) || 0;
  }

  public getLastScaleInAt(symbol: string): number {
    return this.lastScaleInAt.get(symbol) || 0;
  }

  public async placeOrder(
    symbol: string,
    side: 'BUY' | 'SELL',
    volume: number,
    sl: number,
    tp: number,
    comment: string = 'V25_Order'
  ): Promise<{ executed: boolean; ticket: number | null }> {
    try {
      const tSize = tickSize(symbol);
      const orderPayload = {
        symbol,
        side: side.toLowerCase(),
        volume,
        sl: roundToTick(sl, tSize),
        tp: roundToTick(tp, tSize),
        type: 'market',
        comment,
      };
      const result = await callBridge('POST', ['/order', '/mt5/order', '/api/mt5/order'], {
        body: orderPayload,
        timeoutMs: 25_000,
        priority: 'critical',
      });
      const ticket = this.extractTicket(result.data);
      const executed = ticket !== null || JSON.stringify(result.data).includes('"success":true');
      if (executed) marketDataService.invalidateSnapshots();
      return { executed, ticket };
    } catch (err: any) {
      atError(`[AutoEngine] placeOrder failed for ${symbol}:`, err);
      return { executed: false, ticket: null };
    }
  }
}

export const tradingExecutionService = new TradingExecutionService();

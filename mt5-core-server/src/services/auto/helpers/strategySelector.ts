import { db } from '../../../db.js';
import type { AutoTradingConfig, StrategyType } from '../types.js';
import type { TradePlan } from '../v25/playbooks/types.js';
import { DEFAULT_STRATEGY_TOGGLES, DEFAULT_GATE_TOGGLES } from '../types.js';
import { atWarn, round2, round1 } from '../utils.js';
import { tickBuffers } from '../v25/TickBuffer.js';
import { playbookSelector, executableMinRrrForPlan, liveMarketRiskIssue } from '../v25/playbooks/index.js';
import { wallStateMachine } from '../v25/state/WallStateMachine.js';

export type TradeSide = 'BUY' | 'SELL';

export function isGateEnabled(cfg: AutoTradingConfig, gate: keyof typeof DEFAULT_GATE_TOGGLES | string): boolean {
  const toggles = cfg.adaptive?.gateToggles;
  
  if (gate === 'unifiedZoneGate') {
    if (toggles && Object.prototype.hasOwnProperty.call(toggles, 'unifiedZoneGate')) {
      return toggles.unifiedZoneGate !== false;
    }
    // Backward compatibility fallback to old zone gates
    if (toggles?.zoneAwareGate === false || toggles?.preAiMtfZoneGate === false) {
      return false;
    }
    return true;
  }

  if (gate === 'executionQualityGate') {
    if (toggles && Object.prototype.hasOwnProperty.call(toggles, 'executionQualityGate')) {
      return toggles.executionQualityGate !== false;
    }
    // Backward compatibility fallback to old risk/drift gates
    if (toggles?.hardRiskGate === false || toggles?.entryDriftGate === false) {
      return false;
    }
    return true;
  }

  // Handle old gate keys if requested elsewhere in code
  if (gate === 'zoneAwareGate' || gate === 'preAiMtfZoneGate') {
    if (toggles && Object.prototype.hasOwnProperty.call(toggles, 'unifiedZoneGate')) {
      return toggles.unifiedZoneGate !== false;
    }
    return toggles?.[gate] !== false;
  }

  if (gate === 'hardRiskGate' || gate === 'entryDriftGate') {
    if (toggles && Object.prototype.hasOwnProperty.call(toggles, 'executionQualityGate')) {
      return toggles.executionQualityGate !== false;
    }
    return toggles?.[gate] !== false;
  }

  return toggles?.[gate] !== false;
}

export function upperText(...parts: unknown[]): string {
  return parts
    .filter((part) => part !== null && part !== undefined)
    .map((part) => String(part))
    .join(' ')
    .toUpperCase();
}

export function isStrategyEnabled(cfg: AutoTradingConfig, strategy: string | null | undefined): boolean {
  const text = upperText(strategy);
  if (!text || text === 'HOLD_CASH') return true;
  const toggles = cfg.adaptive?.strategyToggles ?? {};
  if (text.startsWith('V25_') && toggles.V25_PLAYBOOKS === false) return false;
  if (Object.prototype.hasOwnProperty.call(DEFAULT_STRATEGY_TOGGLES, text)) {
    return toggles[text] !== false;
  }
  return toggles[text] !== false;
}

export function disabledStrategyReason(cfg: AutoTradingConfig, strategy: string | null | undefined): string | null {
  if (isStrategyEnabled(cfg, strategy)) return null;
  return `STRATEGY_DISABLED: ${upperText(strategy)} disabled by Auto Trade settings`;
}

export function isScalpLikeStrategy(strategy: string | null | undefined): boolean {
  const text = upperText(strategy);
  return text === 'SCALPING' || text.includes('SCALP') || text === 'MEAN_REVERSION' || text.startsWith('V25_');
}

export function isV25Strategy(strategy: string | null | undefined): boolean {
  return upperText(strategy).startsWith('V25_');
}

export function isMeanReversionStrategy(strategy: string | null | undefined): boolean {
  return upperText(strategy) === 'MEAN_REVERSION';
}

export function isWallFvgMagnetScalp(strategy: string | null | undefined): boolean {
  return upperText(strategy) === 'SMC_FVG_MAGNET_SCALP';
}

export function isLocalProofScalpStrategy(strategy: string | null | undefined): boolean {
  const text = upperText(strategy);
  return (
    text === 'SCALPING' ||
    text === 'SMC_FVG_SCALP' ||
    text === 'SMC_FVG_MAGNET_SCALP' ||
    text === 'SMC_WALL_BREAK_SCALP' ||
    text.startsWith('V25_')
  );
}

export function v25StrategyForPlan(plan: TradePlan): StrategyType {
  return `V25_${plan.playbook}` as StrategyType;
}

export function v25PlanReason(plan: TradePlan): string {
  const ageMs = Date.now() - Number(plan.ts ?? 0);
  const triggers = Array.isArray(plan.triggers) ? plan.triggers.slice(0, 4).join(' | ') : '';
  return `${plan.playbook} ${plan.side} local proof: wall=${plan.wallStars}★ target=${plan.oppositeWallStars}★ rrr=${round2(plan.rrr)} age=${Math.max(0, Math.round(ageMs / 1000))}s${triggers ? ` | ${triggers}` : ''}`;
}

export function unifiedV25CandidateTtlMs(plan: TradePlan, cfg: AutoTradingConfig): number {
  const defaultTtl = cfg.adaptive?.v25?.unifiedCandidateTtlMs ?? 120_000;
  if (plan.playbook === 'PB1_WALL_TOUCH' || plan.playbook === 'PB1_TREND_TOUCH') {
    return Math.min(defaultTtl, cfg.adaptive?.v25?.unifiedPb1CandidateTtlMs ?? 30_000);
  }
  return defaultTtl;
}

export function selectUnifiedV25Candidate(
  symbol: string,
  cfg: AutoTradingConfig,
  detDecision: { management?: string },
): TradePlan | null {
  if (cfg.adaptive?.v25?.enabled !== true) return null;
  if (cfg.adaptive?.v25?.unifiedDecisionPath === false) return null;
  if (['HEDGE', 'SCALE_IN', 'CLOSE', 'REDUCE'].includes(String(detDecision.management ?? ''))) return null;

  const now = Date.now();
  const minRrr = Math.max(1.35, cfg.strategy?.scalpingRRR ?? 1.2);
  const latestTick = tickBuffers.get(symbol.toUpperCase()).last();
  const candidates = playbookSelector
    .recentCandidates(symbol)
    .filter((plan) => {
      const age = now - Number(plan.ts ?? 0);
      const planTtlMs = unifiedV25CandidateTtlMs(plan, cfg);
      const validAge = Number.isFinite(age) && age >= 0 && age <= planTtlMs;
      const pricesOk = [plan.entry, plan.sl, plan.tp].every(Number.isFinite);
      const sidesOk =
        (plan.side === 'BUY' && plan.sl < plan.entry && plan.tp > plan.entry) ||
        (plan.side === 'SELL' && plan.sl > plan.entry && plan.tp < plan.entry);
      const planMinRrr = executableMinRrrForPlan(plan, minRrr);
      const tickAge = latestTick ? now - Number(latestTick.recvTs ?? 0) : Number.POSITIVE_INFINITY;
      const tickIsFresh = Number.isFinite(tickAge) && tickAge >= 0 && tickAge <= Math.min(10_000, planTtlMs);
      const liveIssue = tickIsFresh
        ? liveMarketRiskIssue(plan, latestTick, planMinRrr)
        : `stale live tick age=${Math.round(tickAge)}ms`;
      return validAge && pricesOk && sidesOk && Number(plan.rrr) >= planMinRrr - 0.05 && !liveIssue;
    })
    .sort((a, b) => {
      const scoreA = Number(a.rrr ?? 0) + Number(a.wallStars ?? 0) * 0.08 + Number(a.oppositeWallStars ?? 0) * 0.04;
      const scoreB = Number(b.rrr ?? 0) + Number(b.wallStars ?? 0) * 0.08 + Number(b.oppositeWallStars ?? 0) * 0.04;
      return scoreB - scoreA;
    });

  return candidates[0] ?? null;
}

export function resolveFreshDecisionPrice(
  symbol: string,
  symbolInfo: any,
  fallbackClose: number,
): { price: number; source: 'tick_mid' | 'symbol_mid' | 'symbol_bid' | 'symbol_price' | 'candle_close'; ageMs?: number } {
  try {
    const latestTick = tickBuffers.get(symbol.toUpperCase()).last();
    if (latestTick) {
      const ageMs = Date.now() - latestTick.recvTs;
      if (ageMs < 10_000 && Number.isFinite(latestTick.mid) && latestTick.mid > 0) {
        return { price: latestTick.mid, source: 'tick_mid', ageMs };
      }
    }
  } catch {
    // Tick buffer may be disabled during tests or before V25 shadow starts.
  }

  const bid = Number(symbolInfo?.bid);
  const ask = Number(symbolInfo?.ask);
  const price = Number(symbolInfo?.price);
  if (Number.isFinite(bid) && bid > 0 && Number.isFinite(ask) && ask > 0) {
    return { price: (bid + ask) / 2, source: 'symbol_mid' };
  }
  if (Number.isFinite(bid) && bid > 0) return { price: bid, source: 'symbol_bid' };
  if (Number.isFinite(price) && price > 0) return { price, source: 'symbol_price' };
  return { price: fallbackClose, source: 'candle_close' };
}

export function duplicateIntentTtlForStrategy(strategy: string | null | undefined, cfg: AutoTradingConfig): number {
  if (isMeanReversionStrategy(strategy)) {
    return cfg.adaptive?.meanReversionDuplicateIntentTtlMs ?? 900_000;
  }
  return cfg.adaptive?.sequentialDuplicateIntentTtlMs ?? 300_000;
}

export function targetRrrForStrategy(strategy: string | null | undefined, cfg: AutoTradingConfig): number {
  const text = upperText(strategy);
  if (text === 'SMC_FVG_SCALP' || text === 'SMC_FVG_MAGNET_SCALP' || text === 'SMC_WALL_BREAK_SCALP') {
    return Math.max(1.35, cfg.strategy?.scalpingRRR ?? 1.2);
  }
  if (isScalpLikeStrategy(strategy)) {
    return Math.max(1.2, cfg.strategy?.scalpingRRR ?? 1.2);
  }
  if (strategy === 'TREND_FOLLOW') {
    return Math.max(cfg.minRRR ?? 1.5, cfg.strategy?.swingRRR ?? 2.5);
  }
  return Math.max(cfg.minRRR ?? 1.5, 1.2);
}

export function finalRrrFloorForStrategy(
  strategy: string | null | undefined,
  cfg: AutoTradingConfig,
  targetRrr?: number,
): number {
  const configuredTarget = Number.isFinite(Number(targetRrr))
    ? Number(targetRrr)
    : targetRrrForStrategy(strategy, cfg);
  if (isV25Strategy(strategy)) {
    return Math.max(configuredTarget, cfg.adaptive?.v25?.compactScalpRrr ?? 1.35);
  }
  if (isScalpLikeStrategy(strategy) || upperText(strategy).includes('SMC')) {
    return Math.max(configuredTarget, 1.2);
  }
  return Math.max(cfg.minRRR ?? 1.5, 1.2);
}

export function hasContinuationIntent(strategy: string | null | undefined, ...texts: unknown[]): boolean {
  const text = upperText(strategy, ...texts);
  return (
    text.includes('CONTINUATION') ||
    text.includes('BREAKOUT') ||
    text.includes('MOMENTUM') ||
    text.includes('SCALP') ||
    text.includes('SMC_FVG_SCALP') ||
    text.includes('TREND_FOLLOW') ||
    text.includes('MTF-ALIGNED') ||
    text.includes('BMS_') ||
    text.includes('SMS_') ||
    text.includes('BOS')
  );
}

export function htfBiasAgrees(side: TradeSide, analyses: Record<string, any>): boolean {
  const wanted = side === 'BUY' ? 'BULL' : 'BEAR';
  const h4 = analyses['H4']?.bias;
  const h1 = analyses['H1']?.bias;
  return h4 === wanted && (h1 === wanted || h1 === undefined || h1 === 'NEUTRAL');
}

export function biasOpposesSide(side: TradeSide, bias: unknown): boolean {
  const normalized = String(bias ?? '').toUpperCase();
  return (side === 'BUY' && normalized === 'BEAR') || (side === 'SELL' && normalized === 'BULL');
}

export function ltfConsensusBlockReason(
  side: TradeSide,
  strategy: string | null | undefined,
  analyses: Record<string, any>,
  cfg: AutoTradingConfig,
): string | null {
  if (cfg.adaptive?.enableEaOnlyLtfConsensusGuard === false) return null;
  const text = upperText(strategy);
  const guarded =
    isScalpLikeStrategy(strategy) ||
    text.includes('BREAKOUT') ||
    text.includes('TREND_FOLLOW') ||
    text.includes('MOMENTUM');
  if (!guarded) return null;

  const minConfluence = cfg.adaptive?.ltfConsensusGuardMinConfluence ?? 55;
  const m1 = analyses['M1'];
  const m5 = analyses['M5'];
  const m1Conf = Number(m1?.confluence ?? 0);
  const m5Conf = Number(m5?.confluence ?? 0);
  const m1Opposes = biasOpposesSide(side, m1?.bias) && m1Conf >= minConfluence;
  const m5Opposes = biasOpposesSide(side, m5?.bias) && m5Conf >= minConfluence;
  if (!m1Opposes || !m5Opposes) return null;

  return `LTF_CONSENSUS_GUARD: ${side} conflicts with M5=${m5?.bias ?? 'NA'}(${round1(m5Conf)}) and M1=${m1?.bias ?? 'NA'}(${round1(m1Conf)})`;
}

export function postTakeProfitCooldownIssue(
  symbol: string,
  side: TradeSide,
  entryPrice: number,
  cfg: AutoTradingConfig,
): string | null {
  const cooldownMs = cfg.adaptive?.postTakeProfitCooldownMs ?? 30 * 60_000;
  if (!(cooldownMs > 0) || !(entryPrice > 0)) return null;

  const upper = symbol.toUpperCase();
  const isXau = upper.includes('XAU') || upper.includes('GOLD');
  const minDistance = isXau
    ? (cfg.adaptive?.postTakeProfitReentryMinDistanceXAU ?? 6.0)
    : Math.max(entryPrice * 0.001, 0.001);
  const cutoff = Date.now() - cooldownMs;

  try {
    const recentTp = db.prepare(
      `SELECT mt5_ticket AS ticket, close_price AS closePrice, close_at AS closeAt, profit_r AS profitR, profit AS profit
       FROM auto_trading_journal
       WHERE UPPER(symbol) = ?
         AND side = ?
         AND was_executed = 1
         AND outcome = 'WIN'
         AND close_at IS NOT NULL
         AND close_at >= ?
         AND close_price IS NOT NULL
         AND (UPPER(COALESCE(close_reason, '')) = 'TP' OR COALESCE(profit_r, 0) >= 0.8 OR COALESCE(profit, 0) > 0)
       ORDER BY close_at DESC
       LIMIT 1`
    ).get(upper, side, cutoff) as { ticket?: number; closePrice?: number; closeAt?: number; profitR?: number; profit?: number } | undefined;

    const closePrice = Number(recentTp?.closePrice);
    if (!recentTp || !Number.isFinite(closePrice) || closePrice <= 0) return null;
    const distance = Math.abs(entryPrice - closePrice);
    if (distance > minDistance) return null;

    const ageMin = recentTp.closeAt ? Math.max(0, Math.round((Date.now() - Number(recentTp.closeAt)) / 60_000)) : null;
    const ageText = ageMin !== null ? `${ageMin}m ago` : 'recently';
    return `POST_TP_COOLDOWN: same-side ${side} entry ${round2(entryPrice)} is ${round2(distance)} from TP close #${recentTp.ticket ?? 'NA'} @ ${round2(closePrice)} (${ageText}, minDistance=${round2(minDistance)})`;
  } catch (err) {
    atWarn(`[AutoEngine] post-TP cooldown check failed for ${upper}: ${String((err as any)?.message || err)}`);
    return null;
  }
}

export function isDeepWrongZone(side: TradeSide, pctFromRange: number, hardBlockPct: number): boolean {
  return side === 'SELL'
    ? pctFromRange <= hardBlockPct
    : pctFromRange >= 100 - hardBlockPct;
}

export function describeZoneMissingEvidence(args: {
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

export function v25AlignedDirectEntry(args: {
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

import type { AutoTradingService } from '../../autoTradingService.js';
import type {
  AccountSnapshot,
  AutoTradingConfig,
  Bias,
  CycleDecision,
  JournalRow,
  MarketRegime,
  PositionRow,
} from '../types.js';
import type { FVG, PriceMap, PriceWall, SmcSnapshot } from '../analyzers/smc/types.js';
import { getDb } from '../../../db.js';
import { analyzePath } from '../analyzers/smc/priceMapBuilder.js';
import {
  aggregateDealProfits,
  classifyOutcome,
  detectCloseReason,
  extractDealClosePrice,
  extractHistoryOpenPrice,
  matchHistoryDeal,
  profitToR,
  resolveDealCloseAtMs,
} from '../journal.js';
import { closedTradeService } from '../helpers/closedTradeService.js';
import { indicatorPipeline } from './IndicatorPipeline.js';
import { persistenceService } from './PersistenceService.js';
import { tradingExecutionService } from './TradingExecutionService.js';
import {
  atError,
  atLog,
  clamp,
  moneyPerPriceUnit,
  nowIso,
  round1,
  round2,
  roundToTick,
  tickSize,
} from '../utils.js';

type TradeSide = 'BUY' | 'SELL';
type SimplePlaybook = 'HTF_TREND_RETEST' | 'M15_FVG_MAGNET';
type SimpleBlockCategory =
  | 'NO_WALL_SETUP'
  | 'PRICE_MAP_UNAVAILABLE'
  | 'POSITION_CAP'
  | 'SCALP_CONTEXT_GUARD'
  | 'LTF_CONFIRMATION_MISSING'
  | 'WALL_DISTANCE_TOO_FAR'
  | 'SCALP_PLAN_INVALID'
  | 'WALL_SIDE_MISMATCH'
  | 'VOLUME_INVALID'
  | 'SIMPLE_LOSS_GUARD'
  | 'ORDER_SEND_FAILED';

export interface SimpleScalpingSettings {
  primaryTimeframe: 'M15';
  timeframes: string[];
  candleCount: number;
  minWallStars: number;
  targetWallMinStars: number;
  entryProximityAtrMul: number;
  slBufferAtrMul: number;
  tpBufferAtrMul: number;
  minRrr: number;
  maxRiskAtrMul: number;
  minRewardAtrMul: number;
  maxPositionsPerSymbol: number;
  m15FvgMaxAgeBars: number;
  fvgMaxDriftAtrMul: number;
  maxDailyLossesPerSymbol: number;
  maxConsecutiveLossesPerSymbol: number;
  allowBuy: boolean;
  allowSell: boolean;
}

export interface M15WallScalpPlan {
  side: TradeSide;
  entry: number;
  sl: number;
  tp: number;
  rrr: number;
  volume: number | null;
  anchorWall: PriceWall;
  targetWall: PriceWall | null;
  pathNote: string;
  rationale: string;
  score: number;
  playbook: SimplePlaybook;
  fvgTarget?: FVG;
  confirmationNotes?: string[];
}

interface PlannerResult {
  plan: M15WallScalpPlan | null;
  reason: string;
  blockCategory: SimpleBlockCategory | null;
  rejectedReasons: string[];
}

type SimpleAnalysisContext = Pick<CycleDecision, 'regime' | 'overallBias'> | {
  regime?: MarketRegime;
  bias?: Bias;
} | null;

interface SimplePlannerContext {
  analysis?: SimpleAnalysisContext;
  candlesByTf?: Map<string, any[]>;
  smcSnapshots?: Record<string, SmcSnapshot>;
}

const CORE_WALL_TFS = new Set(['M15', 'M30', 'H1', 'H4']);

function finitePositive(value: unknown, fallback: number): number {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

export function resolveSimpleScalpingSettings(config: AutoTradingConfig): SimpleScalpingSettings {
  const raw = config.adaptive?.simpleScalping || {};
  return {
    primaryTimeframe: 'M15',
    timeframes: (raw.timeframes && raw.timeframes.length > 0
      ? raw.timeframes
      : ['M1', 'M5', 'M15', 'M30', 'H1', 'H4'])
      .map((tf) => String(tf).toUpperCase())
      .filter(Boolean),
    candleCount: Math.floor(finitePositive(raw.candleCount, 220)),
    minWallStars: Math.floor(finitePositive(raw.minWallStars, 2)),
    targetWallMinStars: Math.floor(finitePositive(raw.targetWallMinStars, 2)),
    entryProximityAtrMul: finitePositive(raw.entryProximityAtrMul, 0.22),
    slBufferAtrMul: finitePositive(raw.slBufferAtrMul, 0.08),
    tpBufferAtrMul: finitePositive(raw.tpBufferAtrMul, 0.06),
    minRrr: finitePositive(raw.minRrr, config.strategy?.scalpingRRR ?? config.minRRR ?? 1.15),
    maxRiskAtrMul: finitePositive(raw.maxRiskAtrMul, 0.45),
    minRewardAtrMul: finitePositive(raw.minRewardAtrMul, 0.12),
    maxPositionsPerSymbol: Math.floor(finitePositive(raw.maxPositionsPerSymbol, 1)),
    m15FvgMaxAgeBars: Math.floor(finitePositive(raw.m15FvgMaxAgeBars, 5)),
    fvgMaxDriftAtrMul: finitePositive(raw.fvgMaxDriftAtrMul, 0.65),
    maxDailyLossesPerSymbol: Math.floor(finitePositive(raw.maxDailyLossesPerSymbol, 2)),
    maxConsecutiveLossesPerSymbol: Math.floor(finitePositive(raw.maxConsecutiveLossesPerSymbol, 2)),
    allowBuy: raw.allowBuy !== false,
    allowSell: raw.allowSell !== false,
  };
}

function bkkTradingDayRange(now = Date.now()): { start: number; end: number; key: string } {
  const key = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Bangkok' }).format(new Date(now));
  const start = Date.parse(`${key}T00:00:00+07:00`);
  return { start, end: start + 24 * 60 * 60 * 1000, key };
}

function simpleLossGuard(symbol: string, settings: SimpleScalpingSettings, now = Date.now()): { allowed: boolean; reason: string } {
  const maxDailyLosses = settings.maxDailyLossesPerSymbol;
  const maxConsecutiveLosses = settings.maxConsecutiveLossesPerSymbol;
  if (maxDailyLosses <= 0 && maxConsecutiveLosses <= 0) return { allowed: true, reason: 'loss guard disabled' };

  const { start, end, key } = bkkTradingDayRange(now);
  const rows = getDb().prepare(`
    SELECT outcome, profit_r AS profitR, created_at AS createdAt, close_at AS closeAt
      FROM auto_trading_journal
     WHERE symbol = ?
       AND strategy = 'SCALPING'
       AND was_executed = 1
       AND outcome IN ('WIN', 'LOSS', 'BE')
       AND created_at >= ?
       AND created_at < ?
     ORDER BY COALESCE(close_at, updated_at, created_at) DESC, id DESC
  `).all(symbol.toUpperCase(), start, end) as Array<{ outcome: string; profitR: number | null; createdAt: number; closeAt: number | null }>;

  const dailyLosses = rows.filter((row) => String(row.outcome).toUpperCase() === 'LOSS').length;
  let consecutiveLosses = 0;
  for (const row of rows) {
    if (String(row.outcome).toUpperCase() !== 'LOSS') break;
    consecutiveLosses++;
  }

  if (maxDailyLosses > 0 && dailyLosses >= maxDailyLosses) {
    return {
      allowed: false,
      reason: `SIMPLE_LOSS_GUARD: ${symbol} daily losses ${dailyLosses}/${maxDailyLosses} on ${key}`,
    };
  }
  if (maxConsecutiveLosses > 0 && consecutiveLosses >= maxConsecutiveLosses) {
    return {
      allowed: false,
      reason: `SIMPLE_LOSS_GUARD: ${symbol} consecutive losses ${consecutiveLosses}/${maxConsecutiveLosses} on ${key}`,
    };
  }
  return { allowed: true, reason: `loss guard ok (${dailyLosses} daily losses, ${consecutiveLosses} consecutive)` };
}

function normalizePlannerContext(input?: SimpleAnalysisContext | SimplePlannerContext): SimplePlannerContext {
  if (!input) return {};
  const maybeContext = input as SimplePlannerContext;
  if (maybeContext.candlesByTf || maybeContext.smcSnapshots || 'analysis' in maybeContext) return maybeContext;
  return { analysis: input as SimpleAnalysisContext };
}

function nearestSupport(map: PriceMap): PriceWall | null {
  return [...map.supportWalls]
    .filter((w) => w.price <= map.currentPrice)
    .sort((a, b) => Math.abs(a.price - map.currentPrice) - Math.abs(b.price - map.currentPrice))[0] ?? null;
}

function nearestResistance(map: PriceMap): PriceWall | null {
  return [...map.resistanceWalls]
    .filter((w) => w.price > map.currentPrice)
    .sort((a, b) => Math.abs(a.price - map.currentPrice) - Math.abs(b.price - map.currentPrice))[0] ?? null;
}

function firstOppositeWall(map: PriceMap, side: TradeSide, minStars: number): PriceWall | null {
  const walls = side === 'BUY' ? map.resistanceWalls : map.supportWalls;
  const sorted = [...walls]
    .filter((w) => w.confluenceStars >= minStars)
    .sort((a, b) => side === 'BUY' ? a.price - b.price : b.price - a.price);
  return sorted[0] ?? null;
}

function wallLabel(wall: PriceWall | null): string {
  if (!wall) return 'none';
  return `${round2(wall.price)} ${wall.confluenceStars}star ${wall.timeframes.join('+') || 'TF?'}`;
}

function hasCoreWallAnchor(wall: PriceWall): boolean {
  const anchorTimeframes = wall.anchorTimeframes ?? wall.timeframes.filter((tf) => CORE_WALL_TFS.has(tf));
  return anchorTimeframes.length > 0;
}

function expectedAnchorSide(side: TradeSide): PriceWall['side'] {
  return side === 'BUY' ? 'SUPPORT' : 'RESISTANCE';
}

function simpleAnalysisBias(analysis?: SimpleAnalysisContext): Bias | undefined {
  if (!analysis) return undefined;
  const value = analysis as { bias?: Bias; overallBias?: Bias };
  return value.bias ?? value.overallBias;
}

function analysisOpposesSide(side: TradeSide, analysis?: SimpleAnalysisContext): boolean {
  if (!analysis) return false;
  const regime = String(analysis.regime || '').toUpperCase();
  const bias = String(simpleAnalysisBias(analysis) || '').toUpperCase();
  if (side === 'BUY') return regime === 'TRENDING_DOWN' || bias === 'BEAR';
  return regime === 'TRENDING_UP' || bias === 'BULL';
}

function candleAtr(candles: any[], fallback: number): number {
  if (candles.length < 2) return fallback;
  const recent = candles.slice(-14);
  const ranges = recent.map((c) => Math.abs(Number(c.h ?? c.c ?? 0) - Number(c.l ?? c.c ?? 0))).filter((n) => Number.isFinite(n) && n > 0);
  if (ranges.length === 0) return fallback;
  return ranges.reduce((sum, n) => sum + n, 0) / ranges.length;
}

type WallReclaimProbe = {
  ok: boolean;
  reaction: boolean;
  broke: boolean;
  note: string;
};

function detectWallReclaimBreak(
  side: TradeSide,
  wall: PriceWall,
  candles: any[] | undefined,
  fallbackAtr: number,
  timeframe: string,
): WallReclaimProbe {
  if (!candles || candles.length < 6) return { ok: false, reaction: false, broke: false, note: `${timeframe}: missing candles` };
  const recent = candles.slice(-6);
  const last = recent[recent.length - 1];
  const prev = recent[recent.length - 2] ?? last;
  const prior = recent.slice(0, -1);
  const atr = candleAtr(candles, fallbackAtr);
  const wallRange = Math.abs(wall.priceTop - wall.priceBottom);
  const tolerance = Math.max(atr * 0.12, Math.abs(wall.price) * 0.00005, wallRange * 0.25);
  const priorHigh = Math.max(...prior.map((c) => Number(c.h ?? c.c ?? 0)));
  const priorLow = Math.min(...prior.map((c) => Number(c.l ?? c.c ?? 0)));
  const recentLow = Math.min(...recent.map((c) => Number(c.l ?? c.c ?? 0)));
  const recentHigh = Math.max(...recent.map((c) => Number(c.h ?? c.c ?? 0)));
  const lastOpen = Number(last.o ?? last.c);
  const lastClose = Number(last.c);
  const lastLow = Number(last.l ?? last.c);
  const lastHigh = Number(last.h ?? last.c);
  const prevClose = Number(prev.c);

  if (side === 'BUY') {
    const touchedWall = recentLow <= wall.price + tolerance;
    const reclaimedWall = touchedWall && lastClose >= wall.price;
    const brokeMicroHigh = lastClose > priorHigh + tolerance * 0.1;
    const bullishBody = lastClose > lastOpen || lastClose > Number(prev.h ?? prevClose);
    const reaction = touchedWall && bullishBody && reclaimedWall;
    return {
      ok: reaction && brokeMicroHigh,
      reaction,
      broke: brokeMicroHigh,
      note: `${timeframe}: BUY confirm touch=${touchedWall} body=${bullishBody} reclaim=${reclaimedWall} breakHigh=${brokeMicroHigh}`,
    };
  }

  const touchedWall = recentHigh >= wall.price - tolerance;
  const reclaimedWall = touchedWall && lastClose <= wall.price;
  const brokeMicroLow = lastClose < priorLow - tolerance * 0.1;
  const bearishBody = lastClose < lastOpen || lastClose < Number(prev.l ?? prevClose);
  const reaction = touchedWall && bearishBody && reclaimedWall;
  return {
    ok: reaction && brokeMicroLow,
    reaction,
    broke: brokeMicroLow,
    note: `${timeframe}: SELL confirm touch=${touchedWall} body=${bearishBody} reclaim=${reclaimedWall} breakLow=${brokeMicroLow}`,
  };
}

function ltfConfirmation(
  side: TradeSide,
  wall: PriceWall,
  context: SimplePlannerContext,
  atr: number,
): { ok: boolean; notes: string[] } {
  if (!context.candlesByTf) return { ok: true, notes: ['LTF confirmation skipped: no candle context'] };
  const m1 = detectWallReclaimBreak(side, wall, context.candlesByTf.get('M1'), atr, 'M1');
  const m5 = detectWallReclaimBreak(side, wall, context.candlesByTf.get('M5'), atr, 'M5');
  const reactionOk = m1.reaction && m5.reaction;
  const breakOk = m1.broke || m5.broke;
  return {
    ok: reactionOk && breakOk,
    notes: [m1.note, m5.note, `LTF combined: reaction=${reactionOk} anyBreak=${breakOk}`],
  };
}

function findRecentM15FvgTarget(
  side: TradeSide,
  entry: number,
  context: SimplePlannerContext,
  maxAgeBars: number,
): FVG | null {
  const m15 = context.smcSnapshots?.M15;
  if (!m15) return null;
  const targetType = side === 'BUY' ? 'BEAR' : 'BULL';
  const candidates = m15.activeFVGs
    .filter((fvg) => fvg.type === targetType && !fvg.mitigated && (fvg.age ?? Number.MAX_SAFE_INTEGER) <= maxAgeBars)
    .filter((fvg) => side === 'BUY' ? fvg.bottom > entry : fvg.top < entry)
    .sort((a, b) =>
      (a.age ?? Number.MAX_SAFE_INTEGER) - (b.age ?? Number.MAX_SAFE_INTEGER) ||
      Math.abs((side === 'BUY' ? a.bottom : a.top) - entry) - Math.abs((side === 'BUY' ? b.bottom : b.top) - entry)
    );
  return candidates[0] ?? null;
}

function buildCandidate(
  map: PriceMap,
  side: TradeSide,
  anchorWall: PriceWall | null,
  settings: SimpleScalpingSettings,
  context: SimplePlannerContext,
): { plan: M15WallScalpPlan | null; reason: string } {
  if (!anchorWall) return { plan: null, reason: `${side}: no anchor wall` };
  if (side === 'BUY' && !settings.allowBuy) return { plan: null, reason: 'BUY disabled' };
  if (side === 'SELL' && !settings.allowSell) return { plan: null, reason: 'SELL disabled' };
  const requiredWallSide = expectedAnchorSide(side);
  if (anchorWall.side !== requiredWallSide) {
    return {
      plan: null,
      reason: `${side}: wall side mismatch ${anchorWall.side} at ${wallLabel(anchorWall)} (needs ${requiredWallSide})`,
    };
  }
  if (anchorWall.status === 'STALE') {
    return { plan: null, reason: `${side}: anchor wall is stale (${anchorWall.missingCycles ?? 0} missing cycles): ${wallLabel(anchorWall)}` };
  }
  if (!hasCoreWallAnchor(anchorWall)) {
    return { plan: null, reason: `${side}: anchor wall lacks core TF (needs M15/M30/H1/H4): ${wallLabel(anchorWall)}` };
  }
  if (anchorWall.confluenceStars < settings.minWallStars) {
    return { plan: null, reason: `${side}: anchor wall too weak (${anchorWall.confluenceStars} < ${settings.minWallStars})` };
  }

  const atr = finitePositive(map.atr, Math.max(map.currentPrice * 0.001, 0.01));
  const entry = map.currentPrice;
  const fvgTarget = findRecentM15FvgTarget(side, entry, context, settings.m15FvgMaxAgeBars);
  const playbook: SimplePlaybook = fvgTarget ? 'M15_FVG_MAGNET' : 'HTF_TREND_RETEST';
  if (!fvgTarget && analysisOpposesSide(side, context.analysis)) {
    const regime = String(context.analysis?.regime || 'UNKNOWN');
    const bias = String(simpleAnalysisBias(context.analysis) || 'UNKNOWN');
    return { plan: null, reason: `${side}: context guard blocks ${side} in ${regime}/${bias}` };
  }

  const wallDistance = Math.abs(entry - anchorWall.price);
  const maxEntryDistance = atr * (fvgTarget ? settings.fvgMaxDriftAtrMul : settings.entryProximityAtrMul);
  if (wallDistance > maxEntryDistance) {
    return {
      plan: null,
      reason: `${side}: price too far from wall: ${round2(entry)} is ${round2(wallDistance)} from ${wallLabel(anchorWall)} (max ${round2(maxEntryDistance)}${fvgTarget ? ' FVG drift' : ''})`,
    };
  }

  const confirmation = ltfConfirmation(side, anchorWall, context, atr);
  if (!confirmation.ok) {
    return { plan: null, reason: `${side}: missing M1/M5 confirmation at ${wallLabel(anchorWall)} (${confirmation.notes.join(' | ')})` };
  }

  const direction = side === 'BUY' ? 'UP' : 'DOWN';
  const slBuffer = atr * settings.slBufferAtrMul;
  const tpBuffer = atr * settings.tpBufferAtrMul;
  const fvgTp = fvgTarget
    ? side === 'BUY'
      ? fvgTarget.bottom - tpBuffer
      : fvgTarget.top + tpBuffer
    : null;
  const path = analyzePath(map, entry, direction, fvgTp ?? undefined, settings.targetWallMinStars);
  const targetWall = firstOppositeWall(map, side, settings.targetWallMinStars);

  const rawSl = side === 'BUY'
    ? Math.min(anchorWall.priceBottom, anchorWall.price) - slBuffer
    : Math.max(anchorWall.priceTop, anchorWall.price) + slBuffer;
  const rawTpFromPath = fvgTp ?? (path.targetWallStars >= settings.targetWallMinStars
    ? path.suggestedTP
    : path.conservativeTP);
  const rawTp = side === 'BUY'
    ? (fvgTp ?? rawTpFromPath - tpBuffer)
    : (fvgTp ?? rawTpFromPath + tpBuffer);

  const risk = Math.abs(entry - rawSl);
  const reward = Math.abs(rawTp - entry);
  if (side === 'BUY' && !(rawSl < entry && rawTp > entry)) {
    return { plan: null, reason: `BUY: invalid bracket SL=${round2(rawSl)} TP=${round2(rawTp)} entry=${round2(entry)}` };
  }
  if (side === 'SELL' && !(rawSl > entry && rawTp < entry)) {
    return { plan: null, reason: `SELL: invalid bracket SL=${round2(rawSl)} TP=${round2(rawTp)} entry=${round2(entry)}` };
  }
  if (risk > atr * settings.maxRiskAtrMul) {
    return { plan: null, reason: `${side}: risk ${round2(risk)} > ${round2(atr * settings.maxRiskAtrMul)} (${settings.maxRiskAtrMul} ATR)` };
  }
  if (reward < atr * settings.minRewardAtrMul) {
    return { plan: null, reason: `${side}: reward ${round2(reward)} < ${round2(atr * settings.minRewardAtrMul)} (${settings.minRewardAtrMul} ATR)` };
  }

  const rrr = risk > 0 ? reward / risk : 0;
  if (rrr < settings.minRrr) {
    return { plan: null, reason: `${side}: RRR ${round2(rrr)} < ${settings.minRrr}` };
  }

  const proximityScore = maxEntryDistance > 0 ? (1 - Math.min(1, wallDistance / maxEntryDistance)) * 10 : 0;
  const score = anchorWall.confluenceStars * 10 + proximityScore + Math.min(rrr, 3) * 3 + path.clearPathProbability / 10 + (fvgTarget ? 8 : 0);
  const fvgNote = fvgTarget
    ? ` | M15 ${fvgTarget.type} FVG age=${fvgTarget.age} target=${round2(rawTp)}`
    : '';

  return {
    plan: {
      side,
      entry,
      sl: rawSl,
      tp: rawTp,
      rrr: round2(rrr),
      volume: null,
      anchorWall,
      targetWall,
      pathNote: path.note,
      rationale: `${playbook} ${side} from ${wallLabel(anchorWall)} toward ${fvgTarget ? 'recent M15 FVG' : wallLabel(targetWall)} | ${path.note}${fvgNote} | ${confirmation.notes.join(' | ')}`,
      score: round2(score),
      playbook,
      fvgTarget: fvgTarget ?? undefined,
      confirmationNotes: confirmation.notes,
    },
    reason: 'allowed',
  };
}

export function buildM15WallScalpPlan(
  map: PriceMap | null,
  config: AutoTradingConfig,
  plannerContext?: SimpleAnalysisContext | SimplePlannerContext,
): PlannerResult {
  if (!map) {
    return {
      plan: null,
      reason: 'PriceMap unavailable',
      blockCategory: 'PRICE_MAP_UNAVAILABLE',
      rejectedReasons: [],
    };
  }
  const settings = resolveSimpleScalpingSettings(config);
  const context = normalizePlannerContext(plannerContext);
  const buy = buildCandidate(map, 'BUY', nearestSupport(map), settings, context);
  const sell = buildCandidate(map, 'SELL', nearestResistance(map), settings, context);
  const candidates = [buy.plan, sell.plan].filter((it): it is M15WallScalpPlan => Boolean(it));
  if (!candidates.length) {
    const rejectedReasons = [buy.reason, sell.reason].filter(Boolean);
    const hasContextGuard = rejectedReasons.some((reason) => reason.includes('context guard'));
    const hasWallSideMismatch = rejectedReasons.some((reason) => reason.includes('wall side mismatch'));
    const hasLtfMissing = rejectedReasons.some((reason) => reason.includes('missing M1/M5 confirmation'));
    const hasDistanceTooFar = rejectedReasons.some((reason) => reason.includes('price too far from wall'));
    const hasNearButInvalid = rejectedReasons.some((reason) =>
      reason.includes('RRR') ||
      reason.includes('risk') ||
      reason.includes('reward') ||
      reason.includes('invalid bracket')
    );
    return {
      plan: null,
      reason: rejectedReasons.join(' | '),
      blockCategory: hasWallSideMismatch
        ? 'WALL_SIDE_MISMATCH'
        : hasContextGuard
          ? 'SCALP_CONTEXT_GUARD'
          : hasLtfMissing
            ? 'LTF_CONFIRMATION_MISSING'
            : hasNearButInvalid
              ? 'SCALP_PLAN_INVALID'
              : hasDistanceTooFar ? 'WALL_DISTANCE_TOO_FAR' : 'NO_WALL_SETUP',
      rejectedReasons,
    };
  }

  const plan = candidates.sort((a, b) => b.score - a.score)[0];
  return {
    plan,
    reason: plan.rationale,
    blockCategory: null,
    rejectedReasons: [buy.reason, sell.reason].filter((reason) => reason !== 'allowed'),
  };
}

function computeSimpleVolume(
  account: AccountSnapshot,
  config: AutoTradingConfig,
  symbol: string,
  stopDistance: number,
): number {
  if (!Number.isFinite(stopDistance) || stopDistance <= 0) return 0;
  const overridePct = config.risk.riskPerTradePctOverride?.[symbol.toUpperCase()];
  const riskPct = finitePositive(overridePct, config.risk.riskPerTradePct || 0.5);
  const riskCash = account.equity * (riskPct / 100);
  const unitValue = moneyPerPriceUnit(symbol, config.risk.pointValueOverride || {});
  const raw = riskCash / (stopDistance * unitValue);
  const step = finitePositive(config.risk.minLotStep, 0.01);
  const minLot = finitePositive(config.risk.minLot, step);
  const maxLot = finitePositive(config.risk.maxLot, minLot);
  const snapped = Math.floor(raw / step) * step;
  return round2(clamp(snapped || minLot, minLot, maxLot));
}

function decisionFromPlan(args: {
  symbol: string;
  analysis: { regime: MarketRegime; bias: Bias; confluence: number; fitness: number };
  plan: M15WallScalpPlan | null;
  riskGate: string;
  rationale: string;
  decisionType: CycleDecision['decisionType'];
  blockCategory: string | null;
  executed: boolean;
  ticket: number | null;
  order?: Record<string, unknown>;
  marketSnapshot?: Record<string, unknown>;
  gateTrace?: Array<Record<string, unknown>>;
}): CycleDecision {
  const now = nowIso();
  const side = args.plan?.side ?? 'SKIP';
  return {
    symbol: args.symbol,
    regime: args.analysis.regime,
    overallBias: args.analysis.bias,
    confluence: round1(args.analysis.confluence),
    fitness: round1(args.analysis.fitness),
    strategy: 'SCALPING',
    side,
    entry: args.plan ? round2(args.plan.entry) : null,
    sl: args.plan ? round2(args.plan.sl) : null,
    tp: args.plan ? round2(args.plan.tp) : null,
    volume: args.plan?.volume ?? null,
    rrr: args.plan?.rrr ?? 0,
    rationale: args.rationale,
    translatedTh: args.plan
      ? `SCALPING ${side}: ${args.rationale}`
      : `SKIP: ${args.rationale}`,
    analyzersUsed: ['PRICE_MAP', 'M15_WALLS'],
    riskGate: args.riskGate,
    executed: args.executed,
    mt5Ticket: args.ticket,
    at: now,
    decisionId: `${side === 'SKIP' ? 'SKIP' : 'SIMPLE'}-${Date.now()}-${args.symbol}`,
    decisionType: args.decisionType,
    blockCategory: args.blockCategory,
    deterministic: {
      engine: 'M15_WALL_SCALPING',
      strategy: 'SCALPING',
      side,
      rrr: args.plan?.rrr ?? 0,
      score: args.plan?.score ?? 0,
      playbook: args.plan?.playbook ?? null,
    },
    analysisSnapshot: args.analysis,
    signals: {
      playbook: args.plan?.playbook ?? null,
      anchorWall: args.plan?.anchorWall ?? null,
      targetWall: args.plan?.targetWall ?? null,
      fvgTarget: args.plan?.fvgTarget ?? null,
      confirmationNotes: args.plan?.confirmationNotes ?? [],
      pathNote: args.plan?.pathNote ?? null,
    },
    gateTrace: args.gateTrace ?? [],
    marketSnapshotLog: args.marketSnapshot ?? {},
    order: args.order ?? {},
    outcome: {},
  };
}

function upsertSimpleJournal(args: {
  decision: CycleDecision;
  config: AutoTradingConfig;
  marketSnapshot: Record<string, unknown>;
  status: 'OPEN' | 'PAPER' | 'CANCELLED';
}): void {
  const d = args.decision;
  const row: JournalRow = {
    id: 0,
    decisionId: d.decisionId,
    symbol: d.symbol,
    timeframe: 'M15',
    side: d.side,
    strategy: 'SCALPING',
    analyzersUsed: 'PRICE_MAP,M15_WALLS',
    signalsJson: JSON.stringify(d.signals || {}),
    confluenceScore: d.confluence,
    entry: d.entry,
    sl: d.sl,
    tp: d.tp,
    volume: d.volume,
    riskPct: args.config.risk.riskPerTradePct,
    rrr: d.rrr,
    regime: d.regime,
    marketSnapshot: JSON.stringify(args.marketSnapshot),
    wasExecuted: d.executed,
    mt5Ticket: d.mt5Ticket,
    closeReason: null,
    closePrice: null,
    closeAt: null,
    profit: null,
    profitR: null,
    outcome: args.status,
    aiReview: d.rationale,
    createdAt: Date.now(),
    updatedAt: Date.now(),
    modelId: 'simple_m15_wall_scalping',
    learningEligible: true,
  };
  persistenceService.upsertJournal(row);
}

function isSimpleScalpingJournal(row: JournalRow): boolean {
  return (
    row.modelId === 'simple_m15_wall_scalping' ||
    row.decisionId.startsWith('SIMPLE-') ||
    (row.strategy.toUpperCase() === 'SCALPING' && String(row.aiReview || '').includes('[SimpleScalping]'))
  );
}

async function syncClosedSimpleJournal(
  context: AutoTradingService,
  livePositions: PositionRow[],
): Promise<number> {
  const openTickets = new Set(livePositions.map((it) => it.ticket));
  const closedRows = context.openJournal.filter((row) =>
    row.outcome === 'OPEN' &&
    row.mt5Ticket !== null &&
    !openTickets.has(row.mt5Ticket) &&
    isSimpleScalpingJournal(row)
  );

  if (closedRows.length === 0) return 0;

  const history = await context.fetchHistory(500).catch((err) => {
    atLog(`[SimpleScalping] Closed-trade sync skipped: history fetch failed (${String((err as Error)?.message || err)})`);
    return [] as Record<string, unknown>[];
  });
  if (history.length === 0) return 0;

  let synced = 0;
  for (const row of closedRows) {
    const ticket = row.mt5Ticket!;
    const deal = matchHistoryDeal(row, history);
    if (!deal) {
      closedTradeService.logDeferredClosedDeal(ticket, row.symbol, 'simple journal sync');
      continue;
    }

    closedTradeService.clearDeferredLog(ticket, 'simple journal sync');
    const closePrice = extractDealClosePrice(deal) ?? row.closePrice ?? row.entry ?? 0;
    const closeAt = resolveDealCloseAtMs(deal);
    const profit = aggregateDealProfits(row, history);
    const brokerOpenPrice = extractHistoryOpenPrice(row, history);
    const profitR = profitToR(row.entry, row.sl, closePrice, row.side, brokerOpenPrice);
    const outcome = classifyOutcome(profit, profitR);
    const closeReason = detectCloseReason(deal);

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
      mt5Ticket: ticket,
      outcome,
      profit,
      profitR,
      closeReason,
      closePrice,
      closeAt,
      brokerOpenPrice,
      historyDeal: deal,
    });
    context.markManagementOutcome(ticket, row.decisionId, outcome, profit, profitR, closeReason);
    atLog(`[SimpleScalping] Synced closed trade #${ticket} ${row.symbol} ${outcome} profit=${round2(profit)} R=${profitR ?? 0} (${closeReason})`);
    synced++;
  }

  if (synced > 0) {
    context.openJournal = persistenceService.loadOpenJournal();
    context.persistRuntime();
  }
  return synced;
}

export class SimpleScalpingEngine {
  static isEnabled(config: AutoTradingConfig): boolean {
    if (config.engineMode === 'LEGACY_COMPLEX') return false;
    return config.engineMode === 'M15_WALL_SCALPING' || config.adaptive?.simpleScalping?.enabled === true;
  }

  static async runCycle(context: AutoTradingService): Promise<void> {
    if (context.cycleBusy) return;
    context.cycleBusy = true;

    try {
      if (Date.now() - context.lastConfigReloadAt > 60_000) {
        context.config = context.loadConfig();
        context.lastConfigReloadAt = Date.now();
      }

      const cfg = context.config;
      const settings = resolveSimpleScalpingSettings(cfg);
      context.state = { ...context.state, phase: 'ANALYZING', message: 'simple M15 wall scalping cycle running' };
      atLog('[SimpleScalping] Running M15 Wall Scalping cycle...');

      const account = await context.fetchAccount();
      const positions = await context.fetchPositions();
      await syncClosedSimpleJournal(context, positions);
      const decisions: CycleDecision[] = [];

      for (const symbolRaw of cfg.watchlist) {
        const symbol = String(symbolRaw).trim().toUpperCase();
        if (!symbol || cfg.symbolBlacklist.includes(symbol)) continue;

        const candlesByTf = new Map<string, any[]>();
        for (const tf of settings.timeframes) {
          const candles = await context.loadSymbolCandles(symbol, tf, settings.candleCount).catch(() => []);
          if (candles.length > 0) candlesByTf.set(tf, candles);
        }

        await indicatorPipeline.processUpdate(symbol, candlesByTf, { emitEvents: false });
        const snap = indicatorPipeline.getSnapshot(symbol);
        const priceMap = snap?.priceMap ?? null;
        const m15 = snap?.analyses?.M15;
        const analysis = {
          regime: (m15?.regime ?? 'UNKNOWN') as MarketRegime,
          bias: (m15?.bias ?? 'NEUTRAL') as Bias,
          confluence: Number(m15?.confluence ?? 0),
          fitness: Number(m15?.fitness ?? 0),
        };
        const gateTrace: Array<Record<string, unknown>> = [{
          at: nowIso(),
          stage: 'price_map',
          status: priceMap ? 'ALLOW' : 'SKIP',
          reason: priceMap ? 'PriceMap ready' : 'PriceMap unavailable',
          data: priceMap ? {
            currentPrice: priceMap.currentPrice,
            atrM15: priceMap.atr,
            walls: priceMap.walls.length,
            supportWalls: priceMap.supportWalls.length,
            resistanceWalls: priceMap.resistanceWalls.length,
            sourceTimeframes: priceMap.sourceTimeframes,
          } : {},
        }];

        const lossGuard = simpleLossGuard(symbol, settings);
        if (!lossGuard.allowed) {
          gateTrace.push({ at: nowIso(), stage: 'simple_loss_guard', status: 'BLOCK', reason: lossGuard.reason });
          decisions.push(decisionFromPlan({
            symbol,
            analysis,
            plan: null,
            riskGate: lossGuard.reason,
            rationale: `[SimpleScalping] ${lossGuard.reason}`,
            decisionType: 'ORDER_BLOCKED',
            blockCategory: 'SIMPLE_LOSS_GUARD',
            executed: false,
            ticket: null,
            gateTrace,
            marketSnapshot: { priceMap },
          }));
          continue;
        }

        const planner = buildM15WallScalpPlan(priceMap, cfg, {
          analysis,
          candlesByTf,
          smcSnapshots: snap?.smcSnapshots,
        });
        if (!planner.plan) {
          const block = planner.blockCategory ?? 'NO_WALL_SETUP';
          gateTrace.push({
            at: nowIso(),
            stage: 'scalp_plan',
            status: block === 'NO_WALL_SETUP' ? 'SKIP' : 'BLOCK',
            reason: planner.reason,
          });
          decisions.push(decisionFromPlan({
            symbol,
            analysis,
            plan: null,
            riskGate: `${block}: ${planner.reason}`,
            rationale: `[SimpleScalping] ${planner.reason}`,
            decisionType: block === 'NO_WALL_SETUP' ? 'NO_SIGNAL' : 'ORDER_BLOCKED',
            blockCategory: block,
            executed: false,
            ticket: null,
            gateTrace,
            marketSnapshot: { priceMap },
          }));
          continue;
        }

        const plan = planner.plan;
        const symbolPositions = positions.filter((p) => String(p.symbol).toUpperCase() === symbol);
        if (symbolPositions.length >= settings.maxPositionsPerSymbol) {
          const reason = `POSITION_CAP: ${symbolPositions.length}/${settings.maxPositionsPerSymbol} open for ${symbol}`;
          gateTrace.push({ at: nowIso(), stage: 'position_cap', status: 'BLOCK', reason });
          decisions.push(decisionFromPlan({
            symbol,
            analysis,
            plan,
            riskGate: reason,
            rationale: `[SimpleScalping] ${reason}`,
            decisionType: 'ORDER_BLOCKED',
            blockCategory: 'POSITION_CAP',
            executed: false,
            ticket: null,
            gateTrace,
            marketSnapshot: { priceMap },
          }));
          continue;
        }

        const tSize = tickSize(symbol);
        plan.entry = roundToTick(plan.entry, tSize) ?? plan.entry;
        plan.sl = roundToTick(plan.sl, tSize) ?? plan.sl;
        plan.tp = roundToTick(plan.tp, tSize) ?? plan.tp;
        plan.rrr = round2(Math.abs(plan.tp - plan.entry) / Math.abs(plan.entry - plan.sl));
        plan.volume = computeSimpleVolume(account, cfg, symbol, Math.abs(plan.entry - plan.sl));
        if (!plan.volume || plan.volume <= 0) {
          const reason = `VOLUME_INVALID: computed volume ${plan.volume}`;
          gateTrace.push({ at: nowIso(), stage: 'volume', status: 'BLOCK', reason });
          decisions.push(decisionFromPlan({
            symbol,
            analysis,
            plan,
            riskGate: reason,
            rationale: `[SimpleScalping] ${reason}`,
            decisionType: 'ORDER_BLOCKED',
            blockCategory: 'VOLUME_INVALID',
            executed: false,
            ticket: null,
            gateTrace,
            marketSnapshot: { priceMap },
          }));
          continue;
        }

        let executed = false;
        let ticket: number | null = null;
        let riskGate = 'paper mode';
        let orderLog: Record<string, unknown> = {
          engine: 'M15_WALL_SCALPING',
          symbol,
          side: plan.side,
          volume: plan.volume,
          sl: plan.sl,
          tp: plan.tp,
        };

        if (cfg.enableLiveTrading) {
          const result = await tradingExecutionService.placeOrder(
            symbol,
            plan.side,
            plan.volume,
            plan.sl,
            plan.tp,
            `S_M15_${plan.side}_${symbol}`.slice(0, 26),
          );
          executed = result.executed;
          ticket = result.ticket;
          riskGate = executed ? 'live order sent' : 'order request failed';
          orderLog = { ...orderLog, result };
          gateTrace.push({
            at: nowIso(),
            stage: 'order_send',
            status: executed ? 'EXECUTED' : 'FAILED',
            reason: riskGate,
            data: result,
          });
        } else {
          gateTrace.push({ at: nowIso(), stage: 'paper_mode', status: 'ALLOW', reason: 'Live trading disabled' });
        }

        const decision = decisionFromPlan({
          symbol,
          analysis,
          plan,
          riskGate,
          rationale: `[SimpleScalping] ${plan.rationale}`,
          decisionType: executed ? 'ORDER_EXECUTED' : cfg.enableLiveTrading ? 'ORDER_BLOCKED' : 'PAPER',
          blockCategory: executed || !cfg.enableLiveTrading ? null : 'ORDER_SEND_FAILED',
          executed,
          ticket,
          order: orderLog,
          gateTrace,
          marketSnapshot: {
            price: priceMap?.currentPrice,
            atrM15: priceMap?.atr,
            anchorWall: plan.anchorWall,
            targetWall: plan.targetWall,
            engine: 'M15_WALL_SCALPING',
          },
        });
        upsertSimpleJournal({
          decision,
          config: cfg,
          marketSnapshot: decision.marketSnapshotLog ?? {},
          status: executed ? 'OPEN' : cfg.enableLiveTrading ? 'CANCELLED' : 'PAPER',
        });
        decisions.push(decision);
      }

      context.lastDecisions = decisions;
      context.appendDecisionFeed(context.state.cycleCount + 1, 'M15', decisions);
      context.openJournal = persistenceService.loadOpenJournal();
      const nExec = decisions.filter((it) => it.executed).length;
      const nBlocked = decisions.filter((it) => it.decisionType === 'ORDER_BLOCKED').length;
      const nNoSignal = decisions.filter((it) => it.decisionType === 'NO_SIGNAL').length;
      const nSkip = decisions.filter((it) => it.side === 'SKIP' || it.decisionType === 'ORDER_BLOCKED').length;
      atLog(`[SimpleScalping] Cycle #${context.state.cycleCount + 1} completed: ${nExec} executed | ${nSkip} skipped (${nBlocked} blocked | ${nNoSignal} no-signal)`);
      for (const d of decisions) {
        atLog(`   ${d.symbol.padEnd(6)} | ${d.side.padEnd(4)} | ${d.rationale} (Gate: ${d.riskGate})`);
      }

      context.state = {
        ...context.state,
        phase: 'IDLE',
        lastTickAt: nowIso(),
        cycleCount: context.state.cycleCount + 1,
        openTradesTracked: positions.length,
        message: `simple M15 scalp cycle done ${nExec} executed / ${nSkip} skipped`,
        status: 'ONLINE',
      };
      context.persistRuntime();
    } catch (error) {
      atError('[SimpleScalping] Critical error in runCycle:', error);
      context.state = {
        ...context.state,
        phase: 'IDLE',
        message: `simple cycle failed: ${String((error as Error)?.message || error)}`,
        status: 'ERROR',
      };
      context.persistRuntime();
    } finally {
      context.cycleBusy = false;
    }
  }
}

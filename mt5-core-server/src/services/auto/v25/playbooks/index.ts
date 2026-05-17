/**
 * V25.0 — PlaybookSelector
 *
 * Subscribes to V25_WALL_STATE_CHANGE and routes CONFIRM/RETEST events
 * to the appropriate playbook.
 *
 * Priority (per design 25_TradeFlow_Diagrams.md §3):
 *   PB3 sweep > PB2 OB+FVG > PB5 retest > PB4 range > PB1 default
 *
 * Phase C/D scope: SHADOW MODE — emits TradePlan candidates to log + ring
 * buffer + V25EventBus, but does NOT call OrderSend. Phase D will gate the
 * existing slTpAgent so it only runs at REACT/CONFIRM, after which the
 * V25 plan can supersede V24's deterministic engine.
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import { atLog, tickSize } from '../../utils.js';
import { marketDataService } from '../../core/MarketDataService.js';
import { v25EventBus } from '../V25EventBus.js';
import { tickBuffers } from '../TickBuffer.js';
import { fvgFreshTracker } from '../state/FvgFreshTracker.js';
import { wallProximityIndex, type NearestWallResult } from '../state/WallProximityIndex.js';
import { priceMapSnapshotProvider } from '../state/PriceMapSnapshotProvider.js';
import { tradingExecutionService } from '../../core/TradingExecutionService.js';
import { persistenceService } from '../../core/PersistenceService.js';
import { newsAnalyzer, NewsAnalyzer } from '../../analyzers/news.js';
import {
  resolveSymbolProfile,
  type Tick,
  type WallStateEvent,
} from '../types.js';
import type { PriceMap, PriceWall } from '../../analyzers/smc/types.js';
import { evaluatePB1 } from './PB1_WallTouchScalp.js';
import { evaluatePB2 } from './PB2_FvgFillReversal.js';
import { evaluatePB3 } from './PB3_SweepReversal.js';
import { evaluatePB4 } from './PB4_RangeFade.js';
import { evaluatePB5 } from './PB5_BreakoutRetest.js';
import type { PlaybookContext, PlaybookEvalResult, TradePlan } from './types.js';

interface PlaybookConfig {
  enablePB1: boolean;
  enablePB2: boolean;
  enablePB3: boolean;
  enablePB4: boolean;
  enablePB5: boolean;
  /** Max age of the latest AutoEngine decision that V25 may execute from. */
  maxDecisionAgeMs: number;
  /** When true, only emit candidates (no further chain). Default true (shadow). */
  shadowOnly: boolean;
}

const DEFAULT_CONFIG: PlaybookConfig = {
  enablePB1: true,
  enablePB2: true,
  enablePB3: true,
  enablePB4: true,
  enablePB5: true,
  maxDecisionAgeMs: 150_000,
  shadowOnly: true,
};

type DirectionalMandate = {
  side: 'BUY' | 'SELL' | null;
  source: 'decision_side' | 'rationale' | 'bias' | 'none';
  reason: string;
  stale: boolean;
  decision: any | null;
};

type SkipDecisionBlock = {
  side: 'BUY' | 'SELL' | 'ANY';
  reason: string;
};

const MIN_PATH_OBSTACLE_STARS = 3;
const EVENT_WALL_TOLERANCE = 0.06;

function wallMatchesPrice(wall: PriceWall | null | undefined, price: number | null | undefined): wall is PriceWall {
  return !!wall && price != null && Math.abs(wall.price - price) <= EVENT_WALL_TOLERANCE;
}

export function resolveWallForEvent(ev: WallStateEvent, q: NearestWallResult): PriceWall | null {
  if (!ev.wallPrice) return null;
  if (wallMatchesPrice(ev.wall, ev.wallPrice)) return ev.wall;
  const anchorIsAbove = ev.state === 'RETEST' ? ev.side === 'BELOW' : ev.side === 'ABOVE';
  const primary = anchorIsAbove ? q.resistanceAsc : q.supportDesc;
  const indexed = primary.find(w => wallMatchesPrice(w, ev.wallPrice));
  if (indexed) return indexed;
  return anchorIsAbove ? q.above : q.below;
}

export function tradeSideForWallEvent(ev: Pick<WallStateEvent, 'side' | 'state'>): 'BUY' | 'SELL' {
  if (ev.state === 'RETEST') {
    return ev.side === 'ABOVE' ? 'BUY' : 'SELL';
  }
  return ev.side === 'ABOVE' ? 'SELL' : 'BUY';
}

export function targetWallForTradeSide(side: 'BUY' | 'SELL', q: NearestWallResult): PriceWall | null {
  return side === 'BUY' ? q.above ?? null : q.below ?? null;
}

function strongerTargetWallForTradeSide(
  side: 'BUY' | 'SELL',
  q: NearestWallResult,
  minWallStars: number,
  currentTarget: PriceWall,
): PriceWall {
  if (side === 'BUY') {
    return q.resistanceAsc.find(w => (w.confluenceStars ?? 0) >= minWallStars && w.price > currentTarget.price) ?? currentTarget;
  }
  return q.supportDesc.find(w => (w.confluenceStars ?? 0) >= minWallStars && w.price < currentTarget.price) ?? currentTarget;
}

export function inferDirectionalMandate(
  symbol: string,
  decision: any | null,
  maxAgeMs: number,
  opts: { deriveFromSkip?: boolean } = {},
): DirectionalMandate {
  if (!decision) {
    return { side: null, source: 'none', reason: 'no recent AutoEngine decision', stale: true, decision: null };
  }

  const atRaw = Date.parse(String(decision.at ?? decision.atIso ?? ''));
  const hasTimestamp = Number.isFinite(atRaw);
  const ageMs = hasTimestamp ? Date.now() - atRaw : Number.POSITIVE_INFINITY;
  const stale = !hasTimestamp || ageMs > maxAgeMs;
  const text = `${decision.side ?? ''} ${decision.rationale ?? ''} ${decision.riskGate ?? ''}`.toUpperCase();

  let side: 'BUY' | 'SELL' | null = null;
  let source: DirectionalMandate['source'] = 'none';
  const deriveFromSkip = opts.deriveFromSkip ?? true;
  if (decision.side === 'BUY' || decision.side === 'SELL') {
    side = decision.side;
    source = 'decision_side';
  } else if (deriveFromSkip) {
    const aiBuy = /\[(AI|DET)\s+BUY\]/.test(text) || /FINAL\s+SIDE=BUY/.test(text);
    const aiSell = /\[(AI|DET)\s+SELL\]/.test(text) || /FINAL\s+SIDE=SELL/.test(text);
    if (aiBuy !== aiSell) {
      side = aiBuy ? 'BUY' : 'SELL';
      source = 'rationale';
    } else if (decision.overallBias === 'BULL') {
      side = 'BUY';
      source = 'bias';
    } else if (decision.overallBias === 'BEAR') {
      side = 'SELL';
      source = 'bias';
    }
  }

  const ageText = Number.isFinite(ageMs) ? `${Math.max(0, Math.round(ageMs / 1000))}s` : 'unknown';
  const reason = side
    ? `${symbol} mandate=${side} from ${source} age=${ageText}`
    : `${symbol} no directional mandate age=${ageText}`;
  return { side, source, reason, stale, decision };
}

function inferSideFromSkipText(text: string): 'BUY' | 'SELL' | null {
  const direct =
    /\b(BUY|SELL)\s+BLOCKED\b/.exec(text) ||
    /DECISION SIDE GUARD(?: BLOCKED)?:\s*(BUY|SELL)\s+CONFLICTS/.exec(text) ||
    /\b(BUY|SELL)\s+(?:IN|NEAR|AT)\b/.exec(text) ||
    /SIDE=(BUY|SELL)\b/.exec(text) ||
    /FINAL\s+SIDE=(BUY|SELL)\b/.exec(text);
  if (direct?.[1] === 'BUY' || direct?.[1] === 'SELL') return direct[1];

  const hasBuy =
    /\[(AI|DET)\s+BUY\]/.test(text) ||
    /\bMTF-ALIGNED\s+LONG\b/.test(text) ||
    /\bLONG:\b/.test(text);
  const hasSell =
    /\[(AI|DET)\s+SELL\]/.test(text) ||
    /\bMTF-ALIGNED\s+SHORT\b/.test(text) ||
    /\bSHORT:\b/.test(text);
  if (hasBuy !== hasSell) return hasBuy ? 'BUY' : 'SELL';
  return null;
}

export function skipDecisionBlockReason(decision: any | null): SkipDecisionBlock | null {
  if (!decision || decision.side !== 'SKIP') return null;
  const text = `${decision.rationale ?? ''} ${decision.riskGate ?? ''}`.toUpperCase();
  const globalBlockingPatterns = [
    'NEWS',
    'CAP FULL',
    'DEFENSE CEILING',
    'SPREAD',
    'MARKET CLOSED',
    'SESSION CLOSED',
    'MAX OPEN',
    'PER-SYMBOL CAP',
  ];
  const directionalBlockingPatterns = [
    'MTF ZONE GATE',
    'ZONE-AWARE GATE',
    'ZONE GATE',
    'PROXIMITY',
    'FVG-ALIGN',
    'COUNTER-TREND',
    'DECISION SIDE GUARD',
    'SL DISTANCE GUARD',
    'VETO',
    'BLOCKED',
  ];
  const globalHit = globalBlockingPatterns.find((pattern) => text.includes(pattern));
  const directionalHit = directionalBlockingPatterns.find((pattern) => text.includes(pattern));
  const hit = globalHit ?? directionalHit;
  if (!hit) return null;
  const summary = String(decision.rationale || decision.riskGate || '').slice(0, 140);
  const blockedSide = globalHit ? 'ANY' : (inferSideFromSkipText(text) ?? 'ANY');
  return {
    side: blockedSide,
    reason: `${hit}: ${summary}`,
  };
}

export function skipBlockAppliesToSide(block: SkipDecisionBlock | null, side: 'BUY' | 'SELL'): boolean {
  return Boolean(block && (block.side === 'ANY' || block.side === side));
}

function canV25OverrideSkipBlock(
  block: SkipDecisionBlock | null,
  side: 'BUY' | 'SELL',
  wall: PriceWall | null,
  minWallStars: number,
): boolean {
  if (!skipBlockAppliesToSide(block, side) || !wall) return false;
  const reason = block!.reason.toUpperCase();

  // A V24 proximity skip is based on the previous market-cycle snapshot. When
  // the real-time V25 state machine has since confirmed a fresh wall touch,
  // the current wall evidence should be allowed to reach the playbook risk
  // checks instead of being rejected by stale distance math.
  if (reason.includes('PROXIMITY')) {
    return (wall.confluenceStars ?? 0) >= Math.max(minWallStars, 2);
  }

  return false;
}

export function aiBiasConflictBlocksSide(
  decision: any | null,
  side: 'BUY' | 'SELL',
  regime: 'TRENDING' | 'RANGING',
  block: SkipDecisionBlock | null = null,
): boolean {
  if (!decision) return false;
  if (block && block.side !== 'ANY' && block.side !== side) return false;

  const aiSide = decision.side;
  if (aiSide === 'BUY' || aiSide === 'SELL') return aiSide !== side;

  // In a range, a SKIP with bearish/bullish context is not a hard directional
  // mandate. It often means the continuation trade was blocked by proximity or
  // zone guards, while V25 can still evaluate the opposite wall bounce.
  if (regime === 'RANGING' || String(decision.regime ?? '').toUpperCase() === 'RANGING') return false;

  const aiBias = decision.overallBias;
  return (
    (side === 'SELL' && aiBias === 'BULL') ||
    (side === 'BUY' && aiBias === 'BEAR')
  );
}

function minStopDistance(symbol: string): number {
  const upper = symbol.toUpperCase();
  if (upper === 'XAUUSD' || upper === 'GOLD' || upper.startsWith('XAU')) return 1.5;
  return 0;
}

function planRiskIssue(plan: TradePlan, ctx: PlaybookContext): string | null {
  const entry = Number(plan.entry);
  const sl = Number(plan.sl);
  const tp = Number(plan.tp);
  if (![entry, sl, tp].every(Number.isFinite)) return 'plan contains non-finite price';
  const slDist = Math.abs(entry - sl);
  const tpDist = Math.abs(tp - entry);
  const minSl = minStopDistance(ctx.symbol);
  if (slDist <= 0 || tpDist <= 0) return `invalid distances sl=${slDist} tp=${tpDist}`;
  if (plan.side === 'BUY' && (sl >= entry || tp <= entry)) return 'BUY plan has wrong-side SL/TP';
  if (plan.side === 'SELL' && (sl <= entry || tp >= entry)) return 'SELL plan has wrong-side SL/TP';
  if (minSl > 0 && slDist < minSl) return `SL distance ${slDist.toFixed(2)} below ${ctx.symbol} minimum ${minSl.toFixed(2)}`;
  if (plan.rrr < ctx.profile.minRrr - 0.35) return `RRR ${plan.rrr.toFixed(2)} below executable floor`;
  if (ctx.atrM15 > 0 && slDist > ctx.atrM15 * 2.2) return `SL distance ${slDist.toFixed(2)} too wide vs ATR(M15) ${ctx.atrM15.toFixed(2)}`;
  if (ctx.spread > 0 && slDist < ctx.spread * 3) return `SL distance ${slDist.toFixed(2)} too tight vs spread ${ctx.spread.toFixed(2)}`;
  return null;
}

function marketEntryPrice(side: 'BUY' | 'SELL', tick: Tick): number {
  return side === 'BUY' ? tick.ask : tick.bid;
}

function brokerStopReference(side: 'BUY' | 'SELL', tick: Tick): number {
  return side === 'BUY' ? tick.bid : tick.ask;
}

function liveStopFloor(symbol: string, tick: Tick): number {
  return Math.max(minStopDistance(symbol), tick.spread * 3, tickSize(symbol) * 50);
}

export function liveMarketRiskIssue(plan: TradePlan, tick: Tick | null, minRrr: number): string | null {
  if (!tick) return 'no fresh tick for live order preflight';
  const entryNow = marketEntryPrice(plan.side, tick);
  const stopRef = brokerStopReference(plan.side, tick);
  if (![entryNow, stopRef, plan.sl, plan.tp].every(Number.isFinite)) {
    return 'non-finite live order price';
  }

  const floor = liveStopFloor(plan.symbol, tick);
  const slGap = plan.side === 'BUY' ? stopRef - plan.sl : plan.sl - stopRef;
  const tpGap = plan.side === 'BUY' ? plan.tp - stopRef : stopRef - plan.tp;
  if (slGap <= 0) return `live SL wrong side sl=${plan.sl.toFixed(2)} ref=${stopRef.toFixed(2)}`;
  if (tpGap <= 0) return `live TP wrong side tp=${plan.tp.toFixed(2)} ref=${stopRef.toFixed(2)}`;
  if (slGap < floor) {
    return `live SL distance ${slGap.toFixed(2)} below floor ${floor.toFixed(2)} (bid=${tick.bid.toFixed(2)} ask=${tick.ask.toFixed(2)})`;
  }
  if (tpGap < floor) {
    return `live TP distance ${tpGap.toFixed(2)} below floor ${floor.toFixed(2)} (bid=${tick.bid.toFixed(2)} ask=${tick.ask.toFixed(2)})`;
  }

  const liveRisk = Math.abs(entryNow - plan.sl);
  const liveReward = Math.abs(plan.tp - entryNow);
  const liveRrr = liveRisk > 0 ? liveReward / liveRisk : 0;
  if (minRrr > 0 && liveRrr < minRrr) {
    return `live RRR ${liveRrr.toFixed(2)} below min ${minRrr.toFixed(2)} after price drift (plannedEntry=${plan.entry.toFixed(2)} liveEntry=${entryNow.toFixed(2)})`;
  }
  return null;
}

export function executableMinRrrForPlan(plan: TradePlan, profileMinRrr: number): number {
  if (plan.playbook === 'PB4_RANGE_FADE') {
    return Math.max(1.5, profileMinRrr - 0.3);
  }
  if ((plan.playbook === 'PB1_WALL_TOUCH' || plan.playbook === 'PB1_TREND_TOUCH') && plan.wallStars >= 5) {
    return Math.max(1.5, profileMinRrr - 0.3);
  }
  return profileMinRrr;
}

function pathObstacleEdge(side: 'BUY' | 'SELL', wall: PriceWall): number {
  const prices = [wall.price, wall.priceTop, wall.priceBottom].filter(Number.isFinite);
  if (prices.length === 0) return wall.price;
  return side === 'BUY' ? Math.min(...prices) : Math.max(...prices);
}

function wallSummary(wall: PriceWall): string {
  const frames = wall.timeframes.length > 0 ? wall.timeframes.join('+') : 'no-tf';
  return `${wall.confluenceStars}-star wall @ ${wall.price.toFixed(2)} (${frames})`;
}

export function firstPathObstacle(
  plan: TradePlan,
  priceMap: PriceMap | null | undefined,
  minStars = MIN_PATH_OBSTACLE_STARS,
): PriceWall | null {
  if (!priceMap) return null;
  const walls = plan.side === 'BUY' ? priceMap.resistanceWalls : priceMap.supportWalls;
  const candidates = walls
    .map((wall) => ({ wall, edge: pathObstacleEdge(plan.side, wall) }))
    .filter(({ wall, edge }) => {
      if ((wall.confluenceStars ?? 0) < minStars) return false;
      return plan.side === 'BUY'
        ? edge > plan.entry && edge < plan.tp
        : edge < plan.entry && edge > plan.tp;
    })
    .sort((a, b) => plan.side === 'BUY' ? a.edge - b.edge : b.edge - a.edge);
  return candidates[0]?.wall ?? null;
}

export function annotatePathMilestone(
  plan: TradePlan,
  priceMap: PriceMap | null | undefined,
): TradePlan['pathMilestone'] | null {
  const obstacle = firstPathObstacle(plan, priceMap);
  if (!obstacle) return null;

  const edge = pathObstacleEdge(plan.side, obstacle);
  const risk = Math.abs(plan.entry - plan.sl);
  const reward = plan.side === 'BUY' ? edge - plan.entry : plan.entry - edge;
  const rAtMilestone = risk > 0 ? reward / risk : 0;
  const obstacleText = wallSummary(obstacle);

  plan.pathMilestone = {
    price: obstacle.price,
    edge,
    protectAt: edge,
    stars: obstacle.confluenceStars,
    timeframes: obstacle.timeframes,
    wallSide: obstacle.side,
    rAtMilestone,
  };
  plan.triggers.push(`path milestone ${obstacleText}; protectAt=${edge.toFixed(2)} rAtWall=${rAtMilestone.toFixed(2)}`);
  return plan.pathMilestone;
}

function liveRiskDistance(plan: TradePlan, tick: Tick): number {
  return Math.abs(marketEntryPrice(plan.side, tick) - plan.sl);
}

class PlaybookSelectorImpl {
  private config: PlaybookConfig = { ...DEFAULT_CONFIG };
  private subscribed = false;
  private candidates = new Map<string, TradePlan[]>(); // ring per symbol (last 20)
  private skipReasons = new Map<string, string[]>();
  private stats = {
    evaluations: 0,
    plansEmitted: 0,
    skipped: 0,
  };

  configure(opts: Partial<PlaybookConfig>): void {
    this.config = { ...this.config, ...opts };
  }

  attach(): void {
    if (this.subscribed) return;
    this.subscribed = true;

    v25EventBus.on('V25_WALL_STATE_CHANGE', async (ev) => {
      if (ev.type !== 'V25_WALL_STATE_CHANGE') return;
      const w = ev.payload as WallStateEvent;
      // We act only on CONFIRM (entry trigger) or RETEST (PB5 trigger)
      if (w.state !== 'CONFIRM' && w.state !== 'RETEST') return;
      atLog(`[V25] PlaybookSelector: ${w.symbol} ${w.side} ${w.prevState}→${w.state} wall=${w.wallPrice?.toFixed(2)} — evaluating...`);
      await this.evaluate(w);
    });
  }

  private async evaluate(ev: WallStateEvent): Promise<void> {
    this.stats.evaluations++;
    const symbol = ev.symbol;
    const upper = symbol.toUpperCase();
    if (!ev.wallPrice) return this.recordSkip(upper, 'no wallPrice');

    // Resolve current wall (re-query proximity index to get full PriceWall object)
    const lastTick = tickBuffers.get(upper).last();
    if (!lastTick) return this.recordSkip(upper, 'no recent tick');
    const q = wallProximityIndex.query(upper, lastTick.mid);
    let wall: PriceWall | null = null;
    let oppositeWall: PriceWall | null = null;
    const side = tradeSideForWallEvent(ev);

    wall = resolveWallForEvent(ev, q);
    oppositeWall = targetWallForTradeSide(side, q);

    const profileEarly = resolveSymbolProfile(upper);
    if (oppositeWall && (oppositeWall.confluenceStars ?? 0) < profileEarly.minWallStars) {
      oppositeWall = strongerTargetWallForTradeSide(side, q, profileEarly.minWallStars, oppositeWall);
    }

    const resolvedWallPrice = wall?.price;
    if (!wall || !wallMatchesPrice(wall, ev.wallPrice)) {
      return this.recordSkip(upper, `wall mismatch ev=${ev.wallPrice} idx=${resolvedWallPrice}`);
    }

    // V25.1 — if the nearest wall fails minWallStars, look for a STRONGER wall
    // within reactZone radius and use that instead. Solves "PB1:wall stars 1 < min 2"
    // SKIP loop where price wobbled past a 1★ wall while a 5★ wall sat 30 pts further.
    if ((wall.confluenceStars ?? 0) < profileEarly.minWallStars) {
      const snapEarly = priceMapSnapshotProvider.get(upper);
      const atrM15Early = snapEarly.priceMap?.atr ?? lastTick.mid * 0.004;
      const radius = atrM15Early * profileEarly.reactAtrMul * 3.5; // wider than reactZone; prevents nearest weak-wall loops
      const strong = wallProximityIndex.queryStrongest(upper, lastTick.mid, radius, profileEarly.minWallStars);
      const stronger = side === 'SELL' ? strong.above : strong.below;
      if (stronger) {
        atLog(`[V25] PlaybookSelector: ${upper} ${ev.side} swap weak wall ${wall.price.toFixed(2)} (${wall.confluenceStars}★) → strong wall ${stronger.price.toFixed(2)} (${stronger.confluenceStars}★) within ${radius.toFixed(1)} pts`);
        wall = stronger;
      }
    }

    const snap = priceMapSnapshotProvider.get(upper);
    const atrM15 = snap.priceMap?.atr ?? 0;

    // 2026-05-10 V25.2 — switch to M1 last 8 candles (~8 min) for tight scalp window.
    // Was M5 last 5 (=25 min) which produced 110-150 pt swings on XBTUSD that
    // tripped PB1's R2 (SL ≤ 1×ATR(M15) ≈ 40 pts). M1 swing aligns with the
    // user's "M1 confirms reversal" rule and matches the wall-touch timeframe.
    let swingHigh = lastTick.mid;
    let swingLow = lastTick.mid;
    try {
      const m1 = await marketDataService.fetchCandles(upper, 'M1', 8);
      if (m1 && m1.length > 0) {
        swingHigh = Math.max(...m1.map((c: any) => c.h ?? c.high ?? lastTick.mid));
        swingLow = Math.min(...m1.map((c: any) => c.l ?? c.low ?? lastTick.mid));
      }
    } catch {
      // best-effort; fall back to tick mid
    }

    const profile = resolveSymbolProfile(upper);
    const ctx: PlaybookContext = {
      symbol: upper,
      side,
      wall,
      atrM15,
      price: lastTick.mid,
      reversalKind: 'CONFIRM_VIA_STATE_MACHINE', // PB-specific detail comes from WallStateMachine.ss.lastReversal
      hasFreshOpposite: fvgFreshTracker.hasFreshOpposite(upper, side),
      oppositeWall,
      swingHigh,
      swingLow,
      spread: lastTick.spread,
      touchCount: wallProximityIndex.getTouchCount(upper, wall.price),
      profile: {
        minRrr: profile.minRrr,
        minWallStars: profile.minWallStars,
        slBufferAtrMul: profile.slBufferAtrMul,
      },
    };

    // ── Selector cascade (priority order) ──
    const isRetest = ev.state === 'RETEST';
    // Regime heuristic: if structure direction is BULLISH/BEARISH with continuation
    // streak > 0, treat as TRENDING; else RANGING. (Phase D may use V21 regime API.)
    const dir = snap.smc?.structure?.direction;
    const cont = snap.smc?.structure?.continuationCount ?? 0;
    const regime = (dir === 'BULLISH' || dir === 'BEARISH') && cont > 0 ? 'TRENDING' : 'RANGING';
    let mandate: DirectionalMandate = { side: null, source: 'none', reason: 'not evaluated', stale: true, decision: null };

    // ── V25.3 AI Bias Alignment Gate ──────────────────────────────────────
    // V25 operates on pure wall mechanics (price hits wall → trade reversal).
    // But in TRENDING/BREAKOUT regimes, the AI pipeline (Deterministic + Analyst
    // + Risk Officer) often confirms continuation. We must NOT fire orders that
    // contradict the AI pipeline's directional consensus.
    //
    // Rule: If the last AI/Deterministic cycle for this symbol decided BUY,
    //        V25 must NOT fire SELL (and vice versa). Only aligned or SKIP cycles
    //        are permitted. This prevents the catastrophic scenario observed in
    //        Cycle #10003/#10004 where V25 fired SELL while the entire AI stack
    //        confirmed BUY in a VOLATILE_BREAKOUT regime.
    try {
      const lastDecisions = persistenceService.loadLastDecisions();
      const lastForSymbol = lastDecisions.find(d => d.symbol === upper);
      if (lastForSymbol) {
        const block = skipDecisionBlockReason(lastForSymbol);
        const deriveFromSkip = !(lastForSymbol.side === 'SKIP' && regime === 'RANGING');
        const gateMandate = inferDirectionalMandate(upper, lastForSymbol, this.config.maxDecisionAgeMs, { deriveFromSkip });
        if (gateMandate.stale) {
          return this.recordSkip(upper, `Decision Gate: V25 ${side} blocked - ${gateMandate.reason} is stale`);
        }
        if (skipBlockAppliesToSide(block, side)) {
          if (canV25OverrideSkipBlock(block, side, wall, profileEarly.minWallStars)) {
            atLog(`[V25] PlaybookSelector: ${upper} ${side} overriding AutoEngine proximity skip with current wall ${wall.price.toFixed(2)} (${wall.confluenceStars}★)`);
          } else {
            return this.recordSkip(upper, `Decision Gate: V25 ${side} blocked - AutoEngine SKIP gate (${block!.reason})`);
          }
        }
        const oppositeSideSpecificBlock = Boolean(block && block.side !== 'ANY' && block.side !== side);

        const aiSide = lastForSymbol.side;          // 'BUY' | 'SELL' | 'SKIP'

        if (!oppositeSideSpecificBlock && aiBiasConflictBlocksSide(lastForSymbol, side, regime, block)) {
          return this.recordSkip(upper, `AI Bias Gate: V25 ${side} blocked — AI pipeline says ${aiSide} (bias=${lastForSymbol.overallBias}, regime=${lastForSymbol.regime})`);
        }
      }
    } catch { /* best-effort — if loadLastDecisions fails, proceed without gate */ }

    try {
      const latestDecision = persistenceService.loadLastDecisions().find(d => d.symbol === upper) ?? null;
      const block = skipDecisionBlockReason(latestDecision);
      if (skipBlockAppliesToSide(block, side)) {
        if (canV25OverrideSkipBlock(block, side, wall, profileEarly.minWallStars)) {
          atLog(`[V25] PlaybookSelector: ${upper} ${side} overriding AutoEngine proximity skip with current wall ${wall.price.toFixed(2)} (${wall.confluenceStars}★)`);
        } else {
          return this.recordSkip(upper, `Decision Gate: V25 ${side} blocked - AutoEngine SKIP gate (${block!.reason})`);
        }
      }
      if (block && block.side !== 'ANY' && block.side !== side) {
        mandate = {
          side: null,
          source: 'none',
          reason: `${upper} ${block.side} blocked; ${side} may evaluate`,
          stale: false,
          decision: latestDecision,
        };
      } else {
        const deriveFromSkip = !(latestDecision?.side === 'SKIP' && regime === 'RANGING');
        mandate = inferDirectionalMandate(upper, latestDecision, this.config.maxDecisionAgeMs, { deriveFromSkip });
        if (latestDecision && mandate.stale) {
          return this.recordSkip(upper, `Decision Gate: V25 ${side} blocked - ${mandate.reason} is stale`);
        }
        if (mandate.side && mandate.side !== side) {
          return this.recordSkip(upper, `Decision Gate: V25 ${side} blocked - ${mandate.reason}`);
        }
      }
    } catch { /* best-effort - if mandate lookup fails, keep the legacy bias gate result */ }

    const tries: Array<{ name: string; result: PlaybookEvalResult }> = [];

    if (this.config.enablePB3) tries.push({ name: 'PB3', result: evaluatePB3(ctx) });
    if (this.config.enablePB2) tries.push({ name: 'PB2', result: evaluatePB2(ctx) });
    if (this.config.enablePB5 && isRetest) tries.push({ name: 'PB5', result: evaluatePB5(ctx, isRetest) });
    if (this.config.enablePB4) tries.push({ name: 'PB4', result: evaluatePB4(ctx, regime) });
    if (this.config.enablePB1) tries.push({ name: 'PB1', result: evaluatePB1(ctx, regime) });

    let winner: { name: string; result: PlaybookEvalResult } | null = null;
    for (const t of tries) {
      if (t.result.plan) { winner = t; break; }
    }

    if (!winner) {
      const reasons = tries.map((t) => `${t.name}:${t.result.reason}`).slice(0, 5);
      this.recordSkip(upper, reasons.join(' | '));
      return;
    }

    const riskIssue = planRiskIssue(winner.result.plan!, ctx);
    if (riskIssue) {
      return this.recordSkip(upper, `Plan Risk Gate: ${winner.name} ${riskIssue}`);
    }
    annotatePathMilestone(winner.result.plan!, snap.priceMap);

    this.stats.plansEmitted++;
    this.recordPlan(upper, winner.result.plan!);
    const sysConfigLog = persistenceService.loadConfig();
    const isShadowLog = sysConfigLog.adaptive?.v25?.enableV25Only === true ? false : this.config.shadowOnly;
    const modeLabel = isShadowLog ? 'shadow - no order' : 'live';
    atLog(`[V25] 🎯 ${winner.name} ${upper} ${side} entry=${winner.result.plan!.entry.toFixed(2)} sl=${winner.result.plan!.sl.toFixed(2)} tp=${winner.result.plan!.tp.toFixed(2)} rrr=${winner.result.plan!.rrr.toFixed(2)} (${modeLabel})`);

    // Phase D hook: if !shadowOnly we'd hand off to TradingExecutionService here.
    const sysConfig = persistenceService.loadConfig();
    const isShadow = sysConfig.adaptive?.v25?.enableV25Only === true ? false : this.config.shadowOnly;
    
    if (!isShadow && winner.result.plan) {
      const plan = winner.result.plan;
      try {
        const config = persistenceService.loadConfig();
        if (config.newsRisk?.enabled) {
          try {
            const currencies = NewsAnalyzer.currenciesForSymbols([upper]);
            for (const currency of currencies) {
              await newsAnalyzer.updateNewsRisk(currency, config);
            }
          } catch (err) {
            atLog(`[V25] ${upper} news risk refresh failed: ${err instanceof Error ? err.message : String(err)}`);
          }
          const newsGate = newsAnalyzer.isBlockedByNews(config);
          if (newsGate.blocked) {
            this.recordSkip(upper, `Execution Gate: ${newsGate.reason}; plan archived ${winner.name} ${side} entry=${plan.entry.toFixed(2)} rrr=${plan.rrr.toFixed(2)}`);
            return;
          }
        }
        const livePositions = await marketDataService.fetchPositions(true);
        const symbolPositions = livePositions.filter((p) => p.symbol.toUpperCase() === upper);
        const perSymbolCap =
          config.risk.maxPositionsPerSymbol?.[upper]
          ?? config.risk.defaultMaxPositionsPerSymbol
          ?? config.risk.maxOpenPositions
          ?? 5;
        if (symbolPositions.length >= perSymbolCap) {
          this.recordSkip(upper, `Execution Gate: per-symbol cap reached ${symbolPositions.length}/${perSymbolCap}`);
          return;
        }
        const duplicateIssue = tradingExecutionService.nearDuplicateEntryIssue(
          upper,
          side,
          plan.entry,
          livePositions,
          config,
          { comment: `V25_${winner.name}` },
        );
        if (duplicateIssue) {
          this.recordSkip(upper, `Execution Gate: ${duplicateIssue}`);
          return;
        }
        const account = await marketDataService.fetchAccount();
        const liveTick = tickBuffers.get(upper).last();
        const planAgeMs = Date.now() - plan.ts;
        if (planAgeMs > 15_000) {
          this.recordSkip(upper, `Execution Preflight: ${winner.name} ${side} stale plan age=${Math.round(planAgeMs)}ms`);
          return;
        }
        const liveIssue = liveMarketRiskIssue(plan, liveTick, executableMinRrrForPlan(plan, profile.minRrr));
        if (liveIssue) {
          const prefix = liveIssue.includes('after price drift') ? 'MISSED_BY_DRIFT' : 'Execution Preflight';
          this.recordSkip(upper, `${prefix}: ${winner.name} ${side} ${liveIssue}`);
          return;
        }
        const volume = tradingExecutionService.computeVolume(
            account, 
            liveRiskDistance(plan, liveTick!), 
            upper, 
            config, 
            winner.name
        );
        
        atLog(`[V25] 🚀 Executing ${winner.name} order for ${upper} ${side} vol=${volume}`);
        if (!Number.isFinite(volume) || volume <= 0) {
          this.recordSkip(upper, `Execution Gate: computed volume invalid (${volume})`);
          return;
        }
        tradingExecutionService.rememberEntryIntent(upper, side, liveTick?.mid ?? plan.entry, `V25_${winner.name}`);
        const res = await tradingExecutionService.placeOrder(
            upper, 
            side, 
            volume, 
            plan.sl, 
            plan.tp, 
            `V25_${winner.name}`
        );
        if (res.executed) {
            atLog(`[V25] ✅ Order ${res.ticket} executed for ${upper}`);
            if (res.ticket) {
              const decisionId = `v25-${winner.name}-${Date.now()}-${upper}`;
              const executedPositions = await marketDataService.fetchPositions(true).catch(() => []);
              const executedPosition = executedPositions.find((p) => p.ticket === res.ticket);
              const actualEntry = executedPosition?.priceOpen && executedPosition.priceOpen > 0 ? executedPosition.priceOpen : plan.entry;
              const actualSl = executedPosition?.sl && executedPosition.sl > 0 ? executedPosition.sl : plan.sl;
              const actualTp = executedPosition?.tp && executedPosition.tp > 0 ? executedPosition.tp : plan.tp;
              const actualRisk = Math.abs(actualEntry - actualSl);
              const actualReward = Math.abs(actualTp - actualEntry);
              const actualRrr = actualRisk > 0 ? actualReward / actualRisk : plan.rrr;
              const snapshot = {
                v25: true,
                playbook: winner.result.plan!.playbook,
                selector: winner.name,
                entryWall: {
                  price: wall.price,
                  top: wall.priceTop,
                  bottom: wall.priceBottom,
                  stars: wall.confluenceStars,
                  timeframes: wall.timeframes,
                  hasOB: wall.hasOB,
                  hasFVG: wall.hasFVG,
                },
                oppositeWall: oppositeWall ? {
                  price: oppositeWall.price,
                  stars: oppositeWall.confluenceStars,
                  timeframes: oppositeWall.timeframes,
                } : null,
                stateEvent: ev,
                mandate,
                regime,
                atrM15,
                spread: lastTick.spread,
                priceAtDecision: lastTick.mid,
                plannedEntry: plan.entry,
                actualEntry,
                pathMilestone: plan.pathMilestone ?? null,
                triggers: plan.triggers,
              };
              const journal = {
                id: Date.now(),
                decisionId,
                symbol: upper,
                timeframe: 'M1',
                side: side,
                strategy: `V25_${winner.name}`,
                analyzersUsed: 'V25_Engine',
                signalsJson: JSON.stringify({
                  playbook: winner.result.plan!.playbook,
                  triggers: plan.triggers,
                  wallStars: plan.wallStars,
                  oppositeWallStars: plan.oppositeWallStars,
                  pathMilestone: plan.pathMilestone ?? null,
                  mandate: mandate.reason,
                }),
                confluenceScore: Math.min(100, 50 + plan.wallStars * 10 + plan.oppositeWallStars * 5),
                entry: actualEntry,
                sl: actualSl,
                tp: actualTp,
                volume: volume,
                riskPct: config.risk.riskPerTradePct,
                rrr: actualRrr,
                regime: regime,
                marketSnapshot: JSON.stringify(snapshot),
                wasExecuted: true,
                mt5Ticket: res.ticket,
                closeReason: null,
                closePrice: null,
                closeAt: null,
                profit: null,
                profitR: null,
                outcome: 'OPEN',
                aiReview: `V25 ${winner.name}: ${plan.triggers.join(' | ')} | ${mandate.reason}`,
                modelId: null,
                createdAt: Date.now(),
                updatedAt: Date.now(),
              } as any;
              persistenceService.upsertJournal(journal);
              
              // 2026-05-15: Send V25 execution to the decision feed (JSONL) so it appears in logs
              persistenceService.appendDecisionFeed(0, 'M1', [{
                symbol: upper,
                regime: regime as any,
                overallBias: side === 'BUY' ? 'BULL' : 'BEAR',
                confluence: journal.confluenceScore,
                fitness: 100,
                strategy: `V25_${winner.name}` as any,
                side: side,
                entry: actualEntry,
                sl: actualSl,
                tp: actualTp,
                volume: volume,
                rrr: actualRrr,
                rationale: `V25 ${winner.name}: ${plan.triggers.join(' | ')}`,
                analyzersUsed: ['V25_Engine'],
                riskGate: 'v25 execution',
                executed: true,
                mt5Ticket: res.ticket,
                at: new Date().toISOString(),
                decisionId: decisionId,
                decisionType: 'ORDER_EXECUTED',
                marketSnapshotLog: snapshot,
                order: { type: 'v25', ticket: res.ticket, side, volume }
              }]);
              
              atLog(`[V25] Structured journal and decision feed saved for #${res.ticket}: ${winner.name} ${side} ${mandate.reason}`);
            }
        }
      } catch (err) {
        atLog(`[V25] ❌ Order execution failed for ${upper}: ${err}`);
      }
    }
  }

  private recordPlan(symbol: string, plan: TradePlan): void {
    const arr = this.candidates.get(symbol) ?? [];
    arr.push(plan);
    while (arr.length > 20) arr.shift();
    this.candidates.set(symbol, arr);
  }

  private recordSkip(symbol: string, reason: string): void {
    this.stats.skipped++;
    const arr = this.skipReasons.get(symbol) ?? [];
    arr.push(`${new Date().toISOString()} ${reason}`);
    while (arr.length > 10) arr.shift();
    this.skipReasons.set(symbol, arr);
    atLog(`[V25] PlaybookSelector SKIP ${symbol}: ${reason}`);
  }

  recentCandidates(symbol: string): TradePlan[] {
    return [...(this.candidates.get((symbol || '').toUpperCase()) ?? [])];
  }

  recentSkips(symbol: string): string[] {
    return [...(this.skipReasons.get((symbol || '').toUpperCase()) ?? [])];
  }
  status() {
    return {
      config: this.config,
      stats: { ...this.stats },
      symbolsWithCandidates: Array.from(this.candidates.keys()),
    };
  }
}

export const playbookSelector = new PlaybookSelectorImpl();

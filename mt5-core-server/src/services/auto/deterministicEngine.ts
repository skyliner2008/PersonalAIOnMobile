/**
 * DeterministicEngine — Rule-based decision engine that emulates the LLM
 * output locally. Used to (a) skip the LLM entirely when market state hash
 * matches a prior cached decision, and (b) provide a strong prior to the
 * LLM so we can shorten the prompt dramatically.
 *
 * Design goal: 60-80% token reduction for auto-trade cycles while keeping
 * decisions actionable within <100ms (vs 20-40s for full Gemini pipeline).
 *
 * 2026-04-24 — skyliner.jojo@gmail.com
 */

import type { AnalysisSummary, Bias, StrategyType, PositionRow, PositionCluster } from './types.js';
import { round1, round2 } from './utils.js';
import { classifyPremiumDiscount, type PremiumDiscountZone } from './analyzers/smc.js';

export type DeterministicAction = 'BUY' | 'SELL' | 'SKIP';
export type ManagementHint = 'HOLD' | 'HEDGE' | 'SCALE_IN' | 'REDUCE' | 'CLOSE' | 'BREAK_EVEN' | 'TRAIL';

export interface DeterministicDecision {
  action: DeterministicAction;
  management: ManagementHint;
  confidence: number;        // 0-100
  strategy: StrategyType;
  rationale: string;
  rationale_th: string;
  timeframe: string;         // preferred next TF
  size_fraction: number;     // 0.25..0.8
  htfAligned: boolean;       // H4 and H1 agree with our side
  counterTrend: boolean;     // opposite to H4 regime
  needsLlmSecondOpinion: boolean; // true for edge cases that benefit from LLM
  stateHash: string;         // cache key
  zoneAtEntry: PremiumDiscountZone; // P1.2: Premium/Discount/EQ zone
  sl?: number;               // V21.0 EA sl
  tp?: number;               // V21.0 EA tp
}

/**
 * Build a stable hash of market state — used to key the LLM cache.
 * Price is bucketed to ~$2 for XAUUSD to absorb tick noise.
 */
export function marketStateHash(
  symbol: string,
  lastPrice: number,
  analyses: Record<string, AnalysisSummary>,
  clusterNetSide: string,
  openPositions: number
): string {
  const priceBucket = Math.round(lastPrice / 2) * 2; // $2 bucket for XAUUSD
  const tfSig = ['H4', 'H1', 'M15', 'M5']
    .map((tf) => {
      const a = analyses[tf];
      if (!a) return `${tf}:-`;
      return `${tf}:${a.bias}:${Math.round(a.confluence / 10) * 10}:${a.regime}`;
    })
    .join('|');
  return `${symbol}@${priceBucket}|${tfSig}|net=${clusterNetSide}|pos=${openPositions}`;
}

/**
 * 2026-04-30 — Loose / HTF-only cache key.
 *
 * The strict `marketStateHash` includes M15, M5 and pos count which churn
 * almost every cycle, so the LLM cache misses on virtually every call even
 * when the H4/H1 picture is identical for many minutes.  This loose key
 * matches when the high-timeframe story hasn't changed:
 *   symbol, $2 price bucket, H4 (bias/conf10/regime), H1 (bias/conf10),
 *   strategy family, side intent.
 * It's only used as a *secondary* lookup — the strict key is checked first
 * so we never return a cache entry that misses a meaningful state change.
 */
export function marketStateHashLoose(
  symbol: string,
  lastPrice: number,
  analyses: Record<string, AnalysisSummary>,
  side: string,
  strategy: string
): string {
  const priceBucket = Math.round(lastPrice / 2) * 2;
  const h4 = analyses['H4'];
  const h1 = analyses['H1'];
  const h4Sig = h4 ? `H4:${h4.bias}:${Math.round(h4.confluence / 10) * 10}:${h4.regime}` : 'H4:-';
  const h1Sig = h1 ? `H1:${h1.bias}:${Math.round(h1.confluence / 10) * 10}` : 'H1:-';
  return `LOOSE|${symbol}@${priceBucket}|${h4Sig}|${h1Sig}|side=${side}|strat=${strategy}`;
}

/**
 * Regime-adaptive weight profiles for MTF scoring.
 *
 * V26.25 — Previously used fixed weights (H4=22%, H1=23%) which over-weighted
 * HTF context and suppressed valid LTF execution signals. Now weights shift
 * based on market regime:
 *   TRENDING:  HTF matters for direction → H4/H1 = 50%
 *   RANGING:   Execution TFs matter → M15/M5 = 44%
 *   BREAKOUT:  Momentum TFs matter → M5/M1 = 35%
 *   DEFAULT:   Balanced profile similar to legacy
 */
const REGIME_WEIGHTS: Record<string, Record<string, number>> = {
  TRENDING_UP:       { H4: 0.25, H1: 0.25, M30: 0.14, M15: 0.16, M5: 0.13, M1: 0.07 },
  TRENDING_DOWN:     { H4: 0.25, H1: 0.25, M30: 0.14, M15: 0.16, M5: 0.13, M1: 0.07 },
  RANGING:           { H4: 0.15, H1: 0.15, M30: 0.15, M15: 0.22, M5: 0.22, M1: 0.11 },
  VOLATILE_BREAKOUT: { H4: 0.15, H1: 0.18, M30: 0.12, M15: 0.20, M5: 0.20, M1: 0.15 },
  DEFAULT:           { H4: 0.20, H1: 0.20, M30: 0.15, M15: 0.20, M5: 0.17, M1: 0.08 },
};

/**
 * Score MTF confluence for each side (BUY/SELL).
 *
 * V26.25 — Regime-adaptive weights + fitness quality multiplier.
 * Weights shift based on the primary regime so that execution TFs
 * have more influence in RANGING/BREAKOUT and HTF context dominates
 * in TRENDING. Each TF's contribution is also scaled by its fitness
 * (0-100 → 0.5-1.0 multiplier) to down-weight noisy / low-quality TFs.
 *
 * Returns buyScore and sellScore in 0..100 range.
 */
function scoreSides(analyses: Record<string, AnalysisSummary>, regime?: string): { buyScore: number; sellScore: number; htfAligned: Bias } {
  const regimeKey = regime && REGIME_WEIGHTS[regime] ? regime : 'DEFAULT';
  const weights = REGIME_WEIGHTS[regimeKey];
  let buyScore = 0;
  let sellScore = 0;
  for (const [tf, w] of Object.entries(weights)) {
    const a = analyses[tf];
    if (!a) continue;
    const c = a.confluence || 0;
    // Fitness quality multiplier: fitness 0→0.5, 50→0.75, 100→1.0
    // This down-weights TFs with poor signal quality
    const fitnessRaw = a.fitness ?? 50;
    const fitnessMul = 0.5 + (Math.min(100, Math.max(0, fitnessRaw)) / 200);
    const effectiveWeight = w * fitnessMul;
    if (a.bias === 'BULL') buyScore += c * effectiveWeight;
    else if (a.bias === 'BEAR') sellScore += c * effectiveWeight;
    else {
      // Neutral splits evenly
      buyScore += c * effectiveWeight * 0.5;
      sellScore += c * effectiveWeight * 0.5;
    }
  }
  const h4 = analyses['H4'];
  const h1 = analyses['H1'];
  let htfAligned: Bias = 'NEUTRAL';
  if (h4 && h1 && h4.bias === h1.bias && h4.bias !== 'NEUTRAL') htfAligned = h4.bias;
  return { buyScore: round1(buyScore), sellScore: round1(sellScore), htfAligned };
}

/**
 * Pick a strategy + timeframe from the scored analyses.
 */
function pickStrategyAndTF(
  primary: AnalysisSummary,
  analyses: Record<string, AnalysisSummary>
): { strategy: StrategyType; timeframe: string } {
  const h4 = analyses['H4'];
  const h1 = analyses['H1'];
  const m15 = analyses['M15'];
  const m5 = analyses['M5'];

  const ltfScalp =
    m5?.strategy === 'SCALPING' && m5.bias !== 'NEUTRAL' && m5.confluence >= 55 && m5.fitness >= 60
      ? { strategy: 'SCALPING' as StrategyType, timeframe: 'M5' }
      : m15?.strategy === 'SCALPING' && m15.bias !== 'NEUTRAL' && m15.confluence >= 55 && m15.fitness >= 60
      ? { strategy: 'SCALPING' as StrategyType, timeframe: 'M15' }
      : null;

  if (primary.strategy === 'SCALPING') {
    return { strategy: 'SCALPING' as StrategyType, timeframe: 'M5' };
  }
  // V26.35 — Allow LTF scalp to execute even in TRENDING or other regimes
  if (ltfScalp) {
    return ltfScalp;
  }

  const htfDisagrees = h4 && h1 && h4.bias !== h1.bias;
  // Ranging markets without a dedicated LTF scalp signal should stay generic.
  // SMC_FVG_SCALP is reserved for the SignalDetector path where an active FVG
  // fill is proven before the order enters the execution pipeline.
  if (primary.regime === 'RANGING' && (m5 || m15)) {
    return { strategy: 'MEAN_REVERSION' as StrategyType, timeframe: 'M5' };
  }
  if (primary.regime === 'TRENDING_UP' || primary.regime === 'TRENDING_DOWN') {
    return { strategy: 'TREND_FOLLOW' as StrategyType, timeframe: 'M15' };
  }
  if (primary.regime === 'VOLATILE_BREAKOUT') {
    return { strategy: 'BREAKOUT' as StrategyType, timeframe: 'M15' };
  }
  if (htfDisagrees) {
    return { strategy: 'MEAN_REVERSION' as StrategyType, timeframe: 'M5' };
  }
  return { strategy: primary.strategy as StrategyType, timeframe: 'M15' };
}

function localBreakoutBlockReason(
  action: DeterministicAction,
  strategy: StrategyType,
  analyses: Record<string, AnalysisSummary>,
  zoneAtEntry: PremiumDiscountZone,
): string | null {
  if (action !== 'BUY' && action !== 'SELL') return null;
  if (strategy !== 'BREAKOUT') return null;

  const wanted: Bias = action === 'BUY' ? 'BULL' : 'BEAR';
  const opposite: Bias = action === 'BUY' ? 'BEAR' : 'BULL';
  const localTfs = ['M15', 'M5'] as const;
  const aligned = localTfs.filter((tf) => {
    const a = analyses[tf];
    return a?.bias === wanted && (a.confluence ?? 0) >= 55;
  });
  const opposing = localTfs.filter((tf) => {
    const a = analyses[tf];
    return a?.bias === opposite && (a.confluence ?? 0) >= 55;
  });
  const m1 = analyses['M1'];
  const m1Opposes = m1?.bias === opposite && (m1.confluence ?? 0) >= 55;
  const wrongZone =
    (action === 'BUY' && zoneAtEntry === 'PREMIUM') ||
    (action === 'SELL' && zoneAtEntry === 'DISCOUNT');

  if (opposing.length >= 2) {
    return `local ${opposing.join('+')} oppose ${action} breakout`;
  }
  if (wrongZone && aligned.length === 0) {
    return `${action} breakout in ${zoneAtEntry} without M15/M5 confirmation${m1Opposes ? ' and M1 opposes' : ''}`;
  }
  if (wrongZone && opposing.length >= 1 && m1Opposes) {
    return `${action} breakout in ${zoneAtEntry} while ${opposing.join('+')} and M1 oppose`;
  }
  return null;
}

function isLocalProofStrategy(strategy: StrategyType): boolean {
  return strategy === 'SCALPING' || String(strategy).includes('SCALP');
}

function localExecutionBlockReason(
  action: DeterministicAction,
  strategy: StrategyType,
  analyses: Record<string, AnalysisSummary>,
): string | null {
  if (action !== 'BUY' && action !== 'SELL') return null;
  if (isLocalProofStrategy(strategy)) return null;

  const needsLocalExecution = strategy === 'BREAKOUT' || strategy === 'MEAN_REVERSION' || strategy === 'TREND_FOLLOW';
  if (!needsLocalExecution) return null;

  const wanted: Bias = action === 'BUY' ? 'BULL' : 'BEAR';
  const opposite: Bias = action === 'BUY' ? 'BEAR' : 'BULL';
  const localTfs = ['M15', 'M5'] as const;
  const aligned = localTfs.filter((tf) => {
    const a = analyses[tf];
    return a?.bias === wanted && (a.confluence ?? 0) >= 55;
  });
  const opposing = localTfs.filter((tf) => {
    const a = analyses[tf];
    return a?.bias === opposite && (a.confluence ?? 0) >= 55;
  });
  const m1 = analyses['M1'];
  const m1Opposes = m1?.bias === opposite && (m1.confluence ?? 0) >= 55;

  if (opposing.length >= 2 && aligned.length === 0) {
    return `local ${opposing.join('+')} oppose ${strategy} ${action}`;
  }
  if (strategy === 'MEAN_REVERSION' && opposing.length >= 1 && m1Opposes && aligned.length === 0) {
    return `local ${opposing.join('+')} plus M1 oppose ${strategy} ${action}`;
  }
  return null;
}

/**
 * Main decision function. Runs in O(1) — no async, no network.
 */
export function deterministicDecide(args: {
  symbol: string;
  lastPrice: number;
  primary: AnalysisSummary;
  analyses: Record<string, AnalysisSummary>;
  cluster: PositionCluster;
  positions: PositionRow[];
  totalHeatR: number;
  floatingProfit: number;
  h4Candles?: any[];         // P1.2: H4 candles for zone classification
  enableZoneAwareGate?: boolean; // P1.2 feature flag
  isLastSafe?: boolean;      // P4.2: True if most recent position has locked profit
  lastPositionR?: number;    // P4.2: R-value of most recent position
  recoveryThresholdR?: number; // P4.2: R-value where recovery (DCA) begins (e.g. -0.2)
}): DeterministicDecision {
  const { 
    symbol, lastPrice, primary, analyses, cluster, positions, 
    totalHeatR, floatingProfit, h4Candles, 
    enableZoneAwareGate = true,
    isLastSafe = true,
    lastPositionR = 0,
    recoveryThresholdR = -0.2
  } = args;

  const { buyScore, sellScore, htfAligned } = scoreSides(analyses, primary.regime);
  const { strategy, timeframe } = pickStrategyAndTF(primary, analyses);

  // V26.25 — Regime-adaptive score threshold.
  // TRENDING: require higher delta (clearer direction from HTF)
  // RANGING:  allow lower delta (LTF execution signals are smaller but valid)
  // BREAKOUT: allow lower delta (momentum moves fast, don't miss)
  const scoreThreshold = primary.regime === 'TRENDING_UP' || primary.regime === 'TRENDING_DOWN'
    ? 10
    : primary.regime === 'RANGING'
    ? 5
    : primary.regime === 'VOLATILE_BREAKOUT'
    ? 6
    : 8; // default

  // Baseline side from score delta
  const scoreDelta = buyScore - sellScore;
  let action: DeterministicAction = 'SKIP';
  if (scoreDelta > scoreThreshold) action = 'BUY';
  else if (scoreDelta < -scoreThreshold) action = 'SELL';

  // --- P1.2: Zone-Aware Gate ---
  // Classify current price position relative to H4 swing structure.
  // Downgrade confidence when trading into wrong zone:
  //   BULL regime + BUY in PREMIUM → penalize (buying high in uptrend)
  //   BEAR regime + SELL in DISCOUNT → penalize (selling low in downtrend)
  let zoneAtEntry: PremiumDiscountZone = 'EQ';
  if (h4Candles && h4Candles.length >= 20 && enableZoneAwareGate) {
    const zoneResult = classifyPremiumDiscount(h4Candles, lastPrice);
    zoneAtEntry = zoneResult.zone;
  }

  // Override: counter-trend H4 is allowed, but we reduce size
  const counterTrend = htfAligned !== 'NEUTRAL' && (
    (action === 'BUY' && htfAligned === 'BEAR') ||
    (action === 'SELL' && htfAligned === 'BULL')
  );

  // Confidence from score strength
  let confidence = Math.min(95, Math.max(buyScore, sellScore));
  if (counterTrend) confidence = Math.max(50, confidence - 15); // penalty for counter-trend

  // P1.2: Zone penalty — buying in premium or selling in discount is suboptimal
  // V26.35: Skip H4 zone penalty for scalp-like strategies
  const isScalp = strategy === 'SCALPING' || String(strategy).includes('SCALP') || String(strategy).startsWith('V25_');
  if (enableZoneAwareGate && !isScalp) {
    const h4Bias = analyses['H4']?.bias;
    const wrongZoneBuy = action === 'BUY' && zoneAtEntry === 'PREMIUM' && h4Bias === 'BULL';
    const wrongZoneSell = action === 'SELL' && zoneAtEntry === 'DISCOUNT' && h4Bias === 'BEAR';
    if (wrongZoneBuy || wrongZoneSell) {
      confidence = Math.max(40, confidence - 15); // significant downgrade
    }
  }

  const breakoutLocalBlock = localBreakoutBlockReason(action, strategy, analyses, zoneAtEntry);
  const executionLocalBlock = breakoutLocalBlock ?? localExecutionBlockReason(action, strategy, analyses);
  if (executionLocalBlock) {
    action = 'SKIP';
    confidence = Math.min(confidence, 35);
  }

  // Sizing
  let sizeFraction = 0.5;
  if (htfAligned !== 'NEUTRAL' && !counterTrend && confidence >= 70) sizeFraction = 0.8;
  else if (counterTrend) sizeFraction = 0.3;

  // Management hint — prioritize protect → scale-in winners → close BE
  let management: ManagementHint = 'HOLD';
  const hasPositions = cluster.positions.length > 0;
  const hasLosers = cluster.losingPositions.length > 0;
  const hasWinners = cluster.winningPositions.length > 0;
  const hasBothSides = positions.some((p) => p.symbol === symbol && p.side === 'BUY')
                    && positions.some((p) => p.symbol === symbol && p.side === 'SELL');

  if (hasPositions) {
    const clusterBiasMatches =
      (cluster.netSide === 'BUY' && action === 'BUY') ||
      (cluster.netSide === 'SELL' && action === 'SELL');

    // 1) HEDGE: portfolio under pressure + market opposes
    // P4.2: Regime Flip Hedge — if H4 bias flipped against us, prioritize HEDGE over recovery
    const h4Bias = analyses['H4']?.bias;
    const regimeFlipped = (cluster.netSide === 'BUY' && h4Bias === 'BEAR') || (cluster.netSide === 'SELL' && h4Bias === 'BULL');
    
    if (regimeFlipped && hasLosers && action !== 'SKIP' && !clusterBiasMatches) {
      management = 'HEDGE';
    }
    else if (!hasBothSides && hasLosers && action !== 'SKIP' && !clusterBiasMatches && totalHeatR >= 0.6) {
      management = 'HEDGE';
    }
    // 2) SCALE_IN rules (Sequential Protection)
    else if (clusterBiasMatches && confidence >= 65) {
      if (isLastSafe && hasWinners) {
        // Normal Pyramiding: only if last is safe
        management = 'SCALE_IN';
      } 
      else if (lastPositionR <= recoveryThresholdR) {
        // Recovery Mode: last is in drawdown, we add to improve average
        management = 'SCALE_IN';
      }
      else {
        // Blocked by sequential protection (last is neither safe nor in deep drawdown)
        action = 'SKIP'; 
      }
    }
    // 3) CLOSE at net-BE when we're hedged and net pnl flipped near zero
    else if (hasBothSides && Math.abs(floatingProfit) < 2 && totalHeatR < 0.2) {
      management = 'CLOSE';
    }
    // 4) BREAK_EVEN for isolated winners
    else if (hasWinners && floatingProfit > 5 && totalHeatR < 0.3) {
      management = 'BREAK_EVEN';
    }
  }

  const sideName = action === 'BUY' ? 'Long' : action === 'SELL' ? 'Short' : 'Wait';
  const isWait = action === 'SKIP';
  const rationale = counterTrend
    ? `Counter-trend ${sideName}: buy=${buyScore} vs sell=${sellScore}; HTF=${htfAligned} → small size`
    : executionLocalBlock
    ? `${strategy === 'BREAKOUT' ? 'Breakout' : 'Execution'} wait: buy=${buyScore} vs sell=${sellScore}; HTF=${htfAligned || 'mixed'}; ${executionLocalBlock}`
    : isWait && (strategy === 'BREAKOUT' || strategy === 'MEAN_REVERSION' || strategy === 'TREND_FOLLOW')
    ? `${strategy === 'BREAKOUT' ? 'Breakout' : 'Execution'} wait: buy=${buyScore} vs sell=${sellScore}; HTF=${htfAligned || 'mixed'}; strategy=${strategy}`
    : `MTF-aligned ${sideName}: buy=${buyScore} vs sell=${sellScore}; HTF=${htfAligned || 'mixed'}; strategy=${strategy}`;
  const rationale_th = counterTrend
    ? `Counter-trend ${sideName} | buy=${buyScore} vs sell=${sellScore} | HTF=${htfAligned} | smaller size`
    : executionLocalBlock
    ? `${strategy === 'BREAKOUT' ? 'Breakout' : 'Execution'} wait | buy=${buyScore} vs sell=${sellScore} | HTF=${htfAligned || 'mixed'} | ${executionLocalBlock}`
    : isWait && (strategy === 'BREAKOUT' || strategy === 'MEAN_REVERSION' || strategy === 'TREND_FOLLOW')
    ? `${strategy === 'BREAKOUT' ? 'Breakout' : 'Execution'} wait | buy=${buyScore} vs sell=${sellScore} | HTF=${htfAligned || 'mixed'} | ${strategy}`
    : `MTF-aligned ${sideName} | buy=${buyScore} vs sell=${sellScore} | HTF=${htfAligned || 'mixed'} | ${strategy}`;

  // Second-opinion trigger: close calls or heavy defense decisions
  const needsLlmSecondOpinion =
    (confidence >= 55 && confidence <= 68) ||           // close call
    management === 'CLOSE' ||                            // defensive close
    management === 'HEDGE' ||                            // big hedge commitment
    (counterTrend && confidence >= 70);                  // counter-trend with high conf → double-check

  const stateHash = marketStateHash(symbol, lastPrice, analyses, cluster.netSide, positions.length);

  return {
    action,
    management,
    confidence: round1(confidence),
    strategy,
    rationale,
    rationale_th,
    timeframe,
    size_fraction: round2(sizeFraction),
    htfAligned: !counterTrend && htfAligned !== 'NEUTRAL',
    counterTrend,
    needsLlmSecondOpinion,
    stateHash,
    zoneAtEntry,
  };
}

/**
 * Simple LRU-ish cache for LLM second-opinion results, keyed by stateHash.
 * Entries expire after TTL so stale quotes don't leak into the next day.
 */
class LlmCache {
  private entries = new Map<string, { decision: any; at: number }>();
  private maxSize = 200;
  private ttlMs = 3 * 60 * 1000; // 3 minutes

  get(key: string): any | null {
    const hit = this.entries.get(key);
    if (!hit) return null;
    if (Date.now() - hit.at > this.ttlMs) {
      this.entries.delete(key);
      return null;
    }
    return hit.decision;
  }

  set(key: string, decision: any): void {
    if (this.entries.size >= this.maxSize) {
      // Evict oldest
      const firstKey = this.entries.keys().next().value;
      if (firstKey) this.entries.delete(firstKey);
    }
    this.entries.set(key, { decision, at: Date.now() });
  }

  stats(): { size: number; ttlMs: number } {
    return { size: this.entries.size, ttlMs: this.ttlMs };
  }

  clear(): void { this.entries.clear(); }
}

export const llmCache = new LlmCache();

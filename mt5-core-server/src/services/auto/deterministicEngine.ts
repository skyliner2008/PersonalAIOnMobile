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
 * Score the four-TF MTF confluence for each side (BUY/SELL).
 * Weights: H4 (35%), H1 (35%), M15 (20%), M5 (10%).
 * Returns buyScore and sellScore in 0..100 range.
 */
function scoreSides(analyses: Record<string, AnalysisSummary>): { buyScore: number; sellScore: number; htfAligned: Bias } {
  const weights: Record<string, number> = { H4: 0.35, H1: 0.35, M15: 0.2, M5: 0.1 };
  let buyScore = 0;
  let sellScore = 0;
  for (const [tf, w] of Object.entries(weights)) {
    const a = analyses[tf];
    if (!a) continue;
    const c = a.confluence || 0;
    if (a.bias === 'BULL') buyScore += c * w;
    else if (a.bias === 'BEAR') sellScore += c * w;
    else {
      // Neutral splits evenly
      buyScore += c * w * 0.5;
      sellScore += c * w * 0.5;
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

  // If HTFs disagree with primary → use SMC/FVG scalp on LTF
  const htfDisagrees = h4 && h1 && h4.bias !== h1.bias;
  if (primary.regime === 'RANGING' && (m5 || m15)) {
    return { strategy: 'SMC_FVG_SCALP' as StrategyType, timeframe: 'M5' };
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

  const { buyScore, sellScore, htfAligned } = scoreSides(analyses);
  const { strategy, timeframe } = pickStrategyAndTF(primary, analyses);

  // Baseline side from score delta
  const scoreDelta = buyScore - sellScore;
  let action: DeterministicAction = 'SKIP';
  if (scoreDelta > 8) action = 'BUY';
  else if (scoreDelta < -8) action = 'SELL';

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
  if (enableZoneAwareGate) {
    const h4Bias = analyses['H4']?.bias;
    const wrongZoneBuy = action === 'BUY' && zoneAtEntry === 'PREMIUM' && h4Bias === 'BULL';
    const wrongZoneSell = action === 'SELL' && zoneAtEntry === 'DISCOUNT' && h4Bias === 'BEAR';
    if (wrongZoneBuy || wrongZoneSell) {
      confidence = Math.max(40, confidence - 15); // significant downgrade
    }
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
  const rationale = counterTrend
    ? `Counter-trend ${sideName}: buy=${buyScore} vs sell=${sellScore}; HTF=${htfAligned} → small size`
    : `MTF-aligned ${sideName}: buy=${buyScore} vs sell=${sellScore}; HTF=${htfAligned || 'mixed'}; strategy=${strategy}`;
  const rationale_th = counterTrend
    ? `สวนเทรนด์ ${sideName} · buy=${buyScore} vs sell=${sellScore} · HTF=${htfAligned} · ใช้ size เล็ก`
    : `ตาม MTF ${sideName} · buy=${buyScore} vs sell=${sellScore} · HTF=${htfAligned || 'mixed'} · ${strategy}`;

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

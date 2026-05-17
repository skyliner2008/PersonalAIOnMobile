import type { AccountSnapshot, AnalysisSummary, AutoTradingConfig, PositionRow } from './types.js';
import { round1, round2, estimateNotionalPct } from './utils.js';
import { correlationClusterCount } from './analysis.js';
import { newsAnalyzer } from './analyzers/news.js';
import { CircuitBreaker } from './risk/circuitBreaker.js';
import { spreadBaseline } from './spreadBaseline.js';

export function gateTrade(
  config: AutoTradingConfig,
  account: AccountSnapshot,
  positions: PositionRow[],
  symbol: string,
  volume: number,
  analysis: AnalysisSummary,
  stopDistance: number,
  spread: number,
  options: {
    allowOverLimitDefense?: boolean;
    correlationResult?: any;
    side?: 'BUY' | 'SELL' | 'SKIP';
  } = {}
): { allowed: boolean; reason: string } {
  // ── Hoist symbolUpper here so every gate below can reference it safely.
  // Previously declared at line ~102 (after the Sequential Protection block),
  // causing a ReferenceError (TDZ) whenever gateTrade() was called with a side.
  const symbolUpper = symbol.toUpperCase();

  const drawdownPct = account.balance > 0 ? ((account.balance - account.equity) / account.balance) * 100 : 0;
  const freeMarginPct = account.equity > 0 ? (account.freeMargin / account.equity) * 100 : 0;
  const dailyLossPct = account.balance > 0 ? Math.max(0, -account.todayPnL) / account.balance * 100 : 0;
  const correlated = correlationClusterCount(symbol, positions);
  const overLimitDefense = options.allowOverLimitDefense === true;

  // ── Circuit Breaker [PRIORITY 0 — Emergency Stop] ─────────────────────────
  // Must fire FIRST: if margin is in a critical state, no other gate matters.
  // Moved ahead of News Gate and Anti-Hedge Rule (2026-05-01).
  const breaker = CircuitBreaker.check(account, config);
  if (breaker.tripped) {
    return { allowed: false, reason: `Circuit Breaker: ${breaker.reason}` };
  }

  // Anti-Hedge Rule: Prevent playing both sides simultaneously unless explicitly hedging.
  // 2026-04-24 fix: use .some()/.filter() instead of symbolPositions[0] — the array order
  // is broker-dependent and could block SELL even when a SELL was already open alongside BUY.
  // When user opts into hedging, `allowOverLimitDefense=true` skips this block (intentional).
  if (!overLimitDefense && options.side && options.side !== 'SKIP') {
      const symbolPositions = positions.filter((p) => p.symbol.toUpperCase() === symbolUpper);
      if (symbolPositions.length > 0) {
          const hasOpposite = symbolPositions.some((p) => p.side !== options.side);
          const hasSameSide = symbolPositions.some((p) => p.side === options.side);
          if (hasOpposite && !hasSameSide) {
              // Pure opposite-side book → refuse without defense override
              const existingSide = symbolPositions.find((p) => p.side !== options.side)!.side;
              return { allowed: false, reason: `anti-hedge: cannot open ${options.side} while holding ${existingSide} (no defense override)` };
          }
          // If we already hold the same side (possibly alongside opposite), allow the add.
          // If pure same-side, allow. Hedge additions come in with overLimitDefense=true.
      }
  }

  // Sequential Protection [Phase 4.2 Hard Enforcement]
  // To prevent the AI from bypassing Sequential Protection via Defense Override,
  // we strictly enforce it here: if adding to a same-side position, the last position
  // must either be SAFE (profit >= 0) or in RECOVERY (r <= -0.2).
  if (options.side && options.side !== 'SKIP') {
      const sameSidePositions = positions.filter((p) => p.symbol.toUpperCase() === symbolUpper && p.side === options.side);
      if (sameSidePositions.length > 0) {
          // Find the last opened position for this side
          const lastPosition = sameSidePositions.sort((a, b) => b.ticket - a.ticket)[0];
          const riskDist = Math.abs(lastPosition.priceOpen - lastPosition.sl);
          let lastPositionR = 0;
          if (riskDist > 0) {
              const pnlPoints = lastPosition.side === 'BUY' 
                  ? lastPosition.priceCurrent - lastPosition.priceOpen 
                  : lastPosition.priceOpen - lastPosition.priceCurrent;
              lastPositionR = pnlPoints / riskDist;
          }

          // 2026-05-04 Fix #10: Refine Risk Gate Sensitivity
          // Treating exactly >= 0 as safe was too restrictive, freezing the pipeline
          // when a trade chops around -0.05R. Allow anything >= -0.10R as "Near Breakeven / Safe".
          const isLastSafe = lastPositionR >= -0.10;
          const recoveryThresholdR = -(config.adaptive?.recoveryTriggerPct ?? 0.2);

          if (!isLastSafe && lastPositionR > recoveryThresholdR) {
              return { 
                  allowed: false, 
                  reason: `Sequential Protection: last ${options.side} ticket #${lastPosition.ticket} is not safe (R=${round2(lastPositionR)}) and not deep enough for recovery (threshold=${recoveryThresholdR}R)`
              };
          }
      }
  }

  // News Risk Gate [Phase 1]
  const newsGate = newsAnalyzer.isBlockedByNews(config);
  if (newsGate.blocked && !overLimitDefense) {
      return { allowed: false, reason: newsGate.reason };
  }

  // 2026-05-05 RSI Overbought/Oversold Gate — Deterministic block
  // Log analysis found XBTUSD BUY opened 5+ times in PREMIUM zone while
  // RSI > 70 (overbought). Every single one closed at BE ($0), then CLP
  // triggered 5 times losing ~$226. This gate prevents MEAN_REVERSION
  // from buying into overbought territory or selling into oversold.
  if (options.side && options.side !== 'SKIP' && analysis.rsi != null && !overLimitDefense) {
    const rsi = analysis.rsi;
    if (options.side === 'BUY' && rsi > 70) {
      return { allowed: false, reason: `RSI overbought gate: BUY blocked (RSI=${round1(rsi)} > 70)` };
    }
    if (options.side === 'SELL' && rsi < 30) {
      return { allowed: false, reason: `RSI oversold gate: SELL blocked (RSI=${round1(rsi)} < 30)` };
    }
  }

  if (drawdownPct >= config.risk.maxDrawdownPct) return { allowed: false, reason: `drawdown ${round2(drawdownPct)}% >= cap` };
  if (dailyLossPct >= config.risk.maxDailyLossPct) return { allowed: false, reason: `daily loss ${round2(dailyLossPct)}% >= cap` };
  if (freeMarginPct < config.risk.minFreeMarginPct) return { allowed: false, reason: `free margin ${round2(freeMarginPct)}% too low` };

  // Absolute aggregate safety cap.
  // Normal entry budgeting is per-symbol, but an account-level hard cap still
  // prevents a broad watchlist from accumulating unlimited small positions.
  const aggregateBaseCap = config.risk.maxOpenPositions ?? 5;
  const aggregateMultiplier = Math.max(1, config.adaptive?.hardCapPositionMultiplier ?? 3);
  const aggregateHardCap = Math.floor(aggregateBaseCap * aggregateMultiplier);
  if (positions.length >= aggregateHardCap) {
    return {
      allowed: false,
      reason: `aggregate hard cap ${aggregateHardCap} reached (${positions.length} open)`,
    };
  }

  // 2026-04-30 — Per-symbol cap is the ONLY position ceiling now.
  // The mobile-app `maxOpenPositions` setting is interpreted as "max positions
  // per symbol" (user intent: 5 means each symbol can hold up to 5 trades),
  // and a 2× hard cap is allowed for defensive scale-in / hedging
  // (overLimitDefense=true).  The previous global aggregate cap was wrong:
  // 2 watched symbols × 5 each = 10 lines should be allowed, but the global
  // gate cut us off at 5 total and froze new entries with positions like
  // "XAUUSD 4/5, XBTUSD 3/5 (total 7/5)" → bogus block.
  // NOTE: symbolUpper already declared at the top of this function (hoisted 2026-05-01).
  const perSymbolCap =
    config.risk.maxPositionsPerSymbol?.[symbolUpper]
    ?? config.risk.defaultMaxPositionsPerSymbol
    ?? config.risk.maxOpenPositions
    ?? config.risk.correlationCap                 // legacy fallback
    ?? 5;
  const symbolHeld = positions.filter((p) => p.symbol.toUpperCase() === symbolUpper).length;
  // Defense override doubles the cap (e.g. user setting 5 → 10 lines allowed
  // when hedging / scaling in to recover existing exposure).
  const effectiveCap = overLimitDefense ? perSymbolCap * 2 : perSymbolCap;
  if (symbolHeld >= effectiveCap) {
    return {
      allowed: false,
      reason: overLimitDefense
        ? `${symbolUpper} hard cap ${effectiveCap} (=${perSymbolCap}×2 defense) reached (${symbolHeld} held)`
        : `${symbolUpper} per-symbol cap ${perSymbolCap} reached (${symbolHeld} held)`,
    };
  }
  
  // Dynamic correlation guard
  const corr = options.correlationResult;
  if (corr && !overLimitDefense) {
      const highCorrSignal = corr.signals.find((s: any) => s.score > 85);
      if (highCorrSignal) {
          return { allowed: false, reason: `dynamic correlation too high: ${highCorrSignal.evidence}` };
      }
  }

  if (analysis.fitness < config.minFitness) return { allowed: false, reason: `fitness ${round1(analysis.fitness)} below min` };
  
  // Dynamic Spread Guard — % deviation from rolling baseline per symbol.
  // The legacy hard cap (config.risk.maxSpread) is kept as an absolute safety
  // ceiling (e.g. 5x baseline). Per-symbol overrides still win.
  if (!overLimitDefense) {
    spreadBaseline.observe(symbol, spread);
    const absoluteOverride = config.risk.maxSpreadOverride?.[symbol.toUpperCase()];
    const verdict = spreadBaseline.verdict(symbol, spread, {
      absoluteOverride,
      maxIncreasePct: config.risk.maxSpreadIncreasePct ?? 100,
    });
    if (!verdict.ok) {
      return { allowed: false, reason: `${symbol} ${verdict.reason}` };
    }
  }

  if (stopDistance <= 0) return { allowed: false, reason: 'stop distance invalid' };
  if (volume <= 0) return { allowed: false, reason: 'invalid volume' };

  // Calculate current total exposure (margin is a decent proxy for total exposure, but we add the new notional)
  const currentMarginPct = account.equity > 0 ? (account.margin / account.equity) * 100 : 0;
  const newExposurePct = estimateNotionalPct(symbol, volume, account.equity);
  if (currentMarginPct + newExposurePct > config.risk.maxTotalExposurePct) {
    return { allowed: false, reason: `exposure limit exceeded (${round2(currentMarginPct + newExposurePct)}% > ${config.risk.maxTotalExposurePct}%)` };
  }

  return { allowed: true, reason: overLimitDefense ? 'allowed via defense override' : 'allowed' };
}

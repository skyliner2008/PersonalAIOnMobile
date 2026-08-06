import { 
  round1, 
  round2, 
  clamp, 
  nowIso 
} from '../utils.js';
import { managementEventsTotal } from '../../metrics.js';
import { 
  classifyOutcome 
} from '../journal.js';
import { 
  AutoTradingConfig, 
  AccountSnapshot, 
  PositionCluster, 
  ManagementPlan, 
  ManagementMode, 
  PlaybookScores, 
  Bias, 
  MarketRegime, 
  StrategyType, 
  CycleDecision, 
  JournalRow, 
  PositionRow,
  AnalysisSummary,
  LearnSummary
} from '../types.js';

class TradeManagementService {
  public planMarketAwareManagement(
    cfg: AutoTradingConfig,
    account: AccountSnapshot,
    cluster: PositionCluster,
    analysis: AnalysisSummary,
    aiDecision: any, // This is usually a Decision from the agent or server
    proposedVolume: number,
    playbook: PlaybookScores,
    totalOpenPositions: number,
    openJournal: JournalRow[]
  ): ManagementPlan {
    if (cluster.positions.length === 0) {
      return { mode: 'HOLD', summary: 'no open positions', reason: 'fresh setup' };
    }

    const adaptive = cfg.adaptive || {};
    const adverseConfluence = adaptive.adverseConfluence ?? 78;
    const defenseConfidence = adaptive.defenseConfidence ?? 82;
    const closeLossThresholdR = adaptive.closeLossThresholdR ?? -2.5;
    const reduceLossThresholdR = adaptive.reduceLossThresholdR ?? -1.5;
    const scaleInLossThresholdR = adaptive.scaleInLossThresholdR ?? -1.2;
    const maxScaleInStepsPerSymbol = adaptive.maxScaleInStepsPerSymbol ?? 3;
    // 2026-04-30 — per-symbol cap follows the mobile-app `maxOpenPositions`
    // setting unless explicitly overridden via `adaptive.maxSameSymbolPositions`.
    // Defense scale-in / hedging is allowed up to 2× this cap (handled by
    // `hardCapPerSymbolMultiplier` below, default raised 1.5 → 2.0).
    const maxSameSymbolPositions =
      adaptive.maxSameSymbolPositions
      ?? cfg.risk.maxPositionsPerSymbol?.[cluster.symbol.toUpperCase()]
      ?? cfg.risk.defaultMaxPositionsPerSymbol
      ?? cfg.risk.maxOpenPositions
      ?? 5;
    const hedgeRatioMin = adaptive.hedgeRatioMin ?? 0.5;
    const hedgeRatioMax = adaptive.hedgeRatioMax ?? 1.2;
    const defaultHedgeRatio = adaptive.defaultHedgeRatio ?? 0.8;
    const scaleInRatioMin = adaptive.scaleInRatioMin ?? 0.3;
    const scaleInRatioMax = adaptive.scaleInRatioMax ?? 0.8;
    const defaultScaleInRatio = adaptive.defaultScaleInRatio ?? 0.5;
    // 2026-04-30 hotfix — was referenced at the SCALE_INTO_WINNER branch (~L332)
    // without ever being declared, throwing "ReferenceError: scaleWinnerMinProfit
    // is not defined" and crashing the whole runCycle. Minimum cluster profit
    // ($USD) required before we add to a winning side.
    const scaleWinnerMinProfit = adaptive.scaleWinnerMinProfit ?? 5;

    // --- ENHANCED CLUSTER SETTINGS (2026-04-30) ---
    const goldenThresholdR = adaptive.goldenThresholdR ?? 2.0;       // Profit > 2R is "Golden" and protected
    const recoveryTriggerPct = adaptive.recoveryTriggerPct ?? 0.2;   // Start recovery at -20% of SL distance
    const recoveryMaxAgeMs = adaptive.recoveryMaxAgeMs ?? 21600000;  // 6 hours for recovery group
    const sequentialOffsetEnabled = adaptive.enableSequentialOffset !== false;
    const netBeProfitBand = adaptive.netBeProfitBand ?? 2;           // close near BE
    const hedgeTriggerHeatR = adaptive.hedgeTriggerHeatR ?? 0.6;     // open hedge at >= 0.6R
    const hedgedExitMaxHeatR = adaptive.hedgedExitMaxHeatR ?? 0.8;   // P4.2: Exit hedge when heat is low

    const aiManagement = (aiDecision?.management || 'HOLD') as ManagementMode;
    const aiAction = aiDecision?.action || 'SKIP';
    const aiConfidence = aiDecision?.confidence || 0;
    const aiSizeFraction = aiDecision?.size_fraction || 0;
    const rationale = aiDecision?.rationale || '';
    const forcedClose = aiManagement === 'CLOSE' && aiConfidence >= defenseConfidence;
    const targetTicket = aiDecision?.target_ticket;

    const rawMarketSide =
      analysis.bias === 'BULL' ? 'BUY' :
      analysis.bias === 'BEAR' ? 'SELL' :
      'SKIP';
    const marketSide = analysis.confluence < adverseConfluence ? 'SKIP' : rawMarketSide;

    const marketOpposesNet =
      cluster.netSide !== 'FLAT' &&
      marketSide !== 'SKIP' &&
      marketSide !== cluster.netSide;
    const rawMarketOpposesNet =
      cluster.netSide !== 'FLAT' &&
      rawMarketSide !== 'SKIP' &&
      rawMarketSide !== cluster.netSide;
    const marketAlignsWithNet =
      cluster.netSide !== 'FLAT' &&
      marketSide !== 'SKIP' &&
      marketSide === cluster.netSide;
    const losingPressure = cluster.totalProfit < 0 && cluster.losingPositions.length > 0;
    // 2026-04-30 — `overPositionLimit` is now a PER-SYMBOL check (no global
    // aggregate cap).  The per-symbol budget is `maxSameSymbolPositions`; the
    // 2× ceiling for defense (`hardCapSymbol`) is the absolute stop.
    const overPositionLimit = cluster.positions.length >= maxSameSymbolPositions;
    const canAddDefense = cluster.positions.length < maxSameSymbolPositions;

    // hardCapAccount is kept as a sanity cap (sum of all symbols) but is
    // computed from the per-symbol budget, not used as a primary gate.
    const hardCapAccount = Math.floor(cfg.risk.maxOpenPositions * (adaptive.hardCapPositionMultiplier ?? 2.0));
    // Defense allowance per symbol = 2× user setting (5 → 10).  Was 1.5×.
    const hardCapSymbol  = Math.floor(maxSameSymbolPositions     * (adaptive.hardCapPerSymbolMultiplier ?? 2.0));
    const effectiveOpenPositions = Math.max(
      account.openPositions || 0,
      totalOpenPositions || 0,
      cluster.positions.length || 0
    );
    const atHardCapAccount = effectiveOpenPositions >= hardCapAccount;
    const atHardCapSymbol  = cluster.positions.length >= hardCapSymbol;
    const atHardCap = atHardCapAccount || atHardCapSymbol;

    const defOverride = (flag: boolean): boolean => atHardCap ? false : flag;
    const openJournalForSymbol = openJournal.filter((it) => it.outcome === 'OPEN' && it.symbol === cluster.symbol);
    const scaleInSteps = playbook.scaleInSteps;
    const matchedRows = cluster.positions.map((position: PositionRow) => {
      const journal = openJournalForSymbol.find((row) => row.mt5Ticket === position.ticket);
      const r = this.positionRiskR(position, journal);
      return {
        position,
        journal,
        r,
        isSafe: this.isPositionSafe(position, journal),
        isGolden: r >= goldenThresholdR
      };
    });

    // 2026-04-30 — compute cluster-wide metrics early so they're available for Priority 0 gates
    const totalHeatR = matchedRows.reduce((sum: number, m: any) => sum + Math.max(0, -m.r), 0);
    const worst = matchedRows
      .slice()
      .sort((a: any, b: any) => a.r - b.r || a.position.profit - b.position.profit)[0];

    // --- P4.2: SEQUENTIAL OFFSET & RECOVERY (2026-04-30) ---
    // newest first
    const activePositions = matchedRows.filter(m => !m.isSafe).sort((a, b) => b.position.ticket - a.position.ticket);
    const safePositions = matchedRows.filter(m => m.isSafe).sort((a, b) => b.position.ticket - a.position.ticket);

    // ─── PRIORITY 0: HARD CLUSTER LOSS STOP (Capital Preservation) ─────────
    // 2026-04-30 — Absolute emergency cut.  When the cluster's accumulated
    // heat blows past `clusterMaxHeatR` (default 3.0R), or the open floating
    // loss exceeds `clusterMaxLossPct` of equity (default 5%), we force-close
    // the worst loser regardless of any other rule.  This is the "stop the
    // bleeding" backstop for when none of the softer offset / hedge logic
    // can get us out — a hard line in the sand to protect the account.
    const clusterHeatHardStop = adaptive.clusterMaxHeatR ?? 3.0;
    const clusterMaxLossPct = adaptive.clusterMaxLossPct ?? 5.0;
    const equityLossPct = account.equity > 0
      ? Math.max(0, -cluster.totalProfit) / account.equity * 100
      : 0;
    const hardStopByHeat = totalHeatR >= clusterHeatHardStop;
    const hardStopByEquity = equityLossPct >= clusterMaxLossPct;
    if ((hardStopByHeat || hardStopByEquity) && worst?.position) {
      managementEventsTotal.inc({ symbol: cluster.symbol, event: 'hard_cluster_stop' });
      return {
        mode: 'CLOSE',
        summary: `HARD STOP: emergency cut on ${cluster.symbol}`,
        reason: hardStopByEquity
          ? `Cluster floating loss ${round2(cluster.totalProfit)} = ${round2(equityLossPct)}% of equity >= ${clusterMaxLossPct}% hard stop. Closing worst #${worst.position.ticket} to halt bleed.`
          : `Cluster heat ${round2(totalHeatR)}R >= ${clusterHeatHardStop}R hard stop. Closing worst #${worst.position.ticket} to halt bleed.`,
        ticket: worst.position.ticket,
        closeVolume: worst.position.volume,
        allowOverLimitDefense: false,
        playbook,
      };
    }

    // ─── PRIORITY 1: SEQUENTIAL OFFSET (search BEST pair, not just latest) ──
    // 2026-04-30 — Previous version only tested `activePositions[0]` paired
    // with `safePositions[0]`.  When the latest safe was a Golden runner the
    // offset never fired even though older safes could have neutralised the
    // worst loser.  Now we search for the worst-loser × best-non-golden-safe
    // pair so capital is preserved earlier when cluster is in distress.
    if (sequentialOffsetEnabled && activePositions.length > 0 && safePositions.length > 0) {
      const worstActive = activePositions
        .slice()
        .sort((a, b) => a.position.profit - b.position.profit)[0]; // most negative
      const bestNonGoldenSafe = safePositions
        .filter(s => !s.isGolden && s.position.profit > 0)
        .sort((a, b) => b.position.profit - a.position.profit)[0]; // most positive

      if (worstActive && bestNonGoldenSafe && worstActive.position.profit < 0) {
        const netPair = (worstActive.position.profit || 0) + (bestNonGoldenSafe.position.profit || 0);
        if (netPair >= 0) {
          return {
            mode: 'CLOSE',
            summary: `Sequential Offset: close #${worstActive.position.ticket} + buffer #${bestNonGoldenSafe.position.ticket}`,
            reason: `Worst active #${worstActive.position.ticket} (${round2(worstActive.position.profit)}) offset by best non-golden safe #${bestNonGoldenSafe.position.ticket} (${round2(bestNonGoldenSafe.position.profit)}). Net=${round2(netPair)} >= 0. Closing pair to preserve account.`,
            tickets: [worstActive.position.ticket, bestNonGoldenSafe.position.ticket],
            allowOverLimitDefense: false,
            playbook
          };
        }
      }
    }

    // ─── PRIORITY 2: RECOVERY GROUP TIME DECAY (3-tier) ─────────────────────
    // 2026-04-30 — Was a single-trigger gate (>6h + near BE).  Capital can be
    // tied up indefinitely if Net stays slightly negative.  Now uses tiers:
    //   Tier-1  (≥6h):  close if Net near BE  (was: >= -netBeProfitBand)
    //   Tier-2 (≥12h): close if Net within ±2× netBeProfitBand
    //   Tier-3 (≥24h): force-close regardless of Net
    const isRecoveryCluster = cluster.positions.length > 1 && matchedRows.some(m => m.r <= -recoveryTriggerPct);
    if (isRecoveryCluster) {
      const oldestInGroup = matchedRows.slice().sort((a, b) => (a.position.openedAtMs || 0) - (b.position.openedAtMs || 0))[0];
      const openedAt = oldestInGroup.position.openedAtMs;
      if (openedAt) {
        const groupAgeMs = Date.now() - openedAt;
        const tier2AgeMs = adaptive.recoveryTier2AgeMs ?? (recoveryMaxAgeMs * 2);
        const tier3AgeMs = adaptive.recoveryTier3AgeMs ?? (recoveryMaxAgeMs * 4);
        const tier1Trigger = groupAgeMs >= recoveryMaxAgeMs && cluster.totalProfit >= -netBeProfitBand;
        const tier2Trigger = groupAgeMs >= tier2AgeMs && cluster.totalProfit >= -(netBeProfitBand * 2);
        const tier3Trigger = groupAgeMs >= tier3AgeMs;
        if (tier1Trigger || tier2Trigger || tier3Trigger) {
          const tier = tier3Trigger ? 3 : (tier2Trigger ? 2 : 1);
          return {
            mode: 'CLOSE',
            summary: `Recovery Time Decay (T${tier}): closing ${cluster.symbol} cluster`,
            reason: `Recovery group active for ${round1(groupAgeMs/3600000)}h. Tier ${tier} trigger fired (Net=${round2(cluster.totalProfit)}). Exiting cluster to free capital.`,
            tickets: cluster.positions.map(p => p.ticket),
            allowOverLimitDefense: false,
            playbook
          };
        }
      }
    }

    const worstLossR = playbook.worstLossR;
    const baseExposure = Math.abs(cluster.netVolume) || proposedVolume;
    const hedgeFraction = this.clampFraction(aiManagement === 'HEDGE' ? aiSizeFraction : defaultHedgeRatio, hedgeRatioMin, hedgeRatioMax, defaultHedgeRatio);
    const scaleInFraction = this.clampFraction(aiManagement === 'SCALE_IN' ? aiSizeFraction : defaultScaleInRatio, scaleInRatioMin, scaleInRatioMax, defaultScaleInRatio);
    const hedgeVolume = round2(Math.max(cfg.risk.minLot, Math.min(baseExposure * hedgeFraction, proposedVolume)));
    // `scaleInVolume` is `let` (not const) because the anti-martingale cap
    // below (~L620) may snap it down before the SCALE_IN return branch.
    let scaleInVolume = round2(Math.max(cfg.risk.minLot, Math.min(baseExposure * scaleInFraction, proposedVolume)));
    const recommendedMode = playbook.recommendedMode;
    
    const playbookHedge = playbook.actions.HEDGE;
    const playbookScaleIn = playbook.actions.SCALE_IN;
    const playbookHold = playbook.actions.HOLD;
    const playbookReduce = playbook.actions.REDUCE;
    const playbookClose = playbook.actions.CLOSE;
    const topPlaybookScore = Math.max(...Object.values(playbook.actions));
    
    const aiHedgeBoost = (aiManagement === 'HEDGE' && aiConfidence >= defenseConfidence) ? 0.15 : 0;
    const aiScaleInBoost = (aiManagement === 'SCALE_IN' || (aiAction === cluster.netSide && aiConfidence >= defenseConfidence)) ? 0.15 : 0;

    const preferHedge = (playbookHedge + aiHedgeBoost) >= Math.max(playbookHold, playbookReduce, playbook.actions.CLOSE);
    const preferScaleIn = (playbookScaleIn + aiScaleInBoost) >= Math.max(playbookHold, playbookReduce);
    const closeScoreAllowsOverride =
      playbookClose >= 0.35 ||
      recommendedMode === 'CLOSE' ||
      worstLossR <= closeLossThresholdR ||
      (aiConfidence >= 90 && playbookClose >= topPlaybookScore - 0.12 && playbook.dimensions.floatingLossPressure >= 0.4);
    const reduceScoreAllowsOverride =
      playbookReduce >= 0.35 ||
      recommendedMode === 'REDUCE' ||
      recommendedMode === 'CLOSE' ||
      (aiConfidence >= 88 && playbookReduce >= topPlaybookScore - 0.15 && playbook.dimensions.floatingLossPressure >= 0.35);

    // isAiDefense is true only when AI explicitly calls for management actions, 
    // NOT just because it agrees with the current cluster direction.
    const isAiDefense = (
      aiManagement === 'HEDGE' || 
      aiManagement === 'SCALE_IN' || 
      forcedClose
    ) && aiConfidence >= defenseConfidence;
    
    // Anti-Martingale Guard: isAiDefense must NOT bypass scale-in threshold
    // unless the position is genuinely in significant drawdown (worstLossR <= -0.5R).
    // This prevents adding more SELL/BUY to average down on positions that just started losing.
    const genuineDrawdown = worstLossR <= -0.5;
    const isAiDefenseForScaleIn = isAiDefense && genuineDrawdown;
    
    let finalMode = aiManagement as ManagementMode;
    // Only auto-upgrade HOLD→SCALE_IN when rationale explicitly mentions scale-in strategy
    // (NOT recovery/structure keywords which appear in normal trade rationales)
    const isScaleInIntent = rationale.toLowerCase().includes('scale-in') || 
                           rationale.toLowerCase().includes('averaging down') ||
                           rationale.toLowerCase().includes('cost average');

    if (finalMode === 'HOLD' && isScaleInIntent && isAiDefenseForScaleIn) {
        finalMode = 'SCALE_IN';
    }

    // Fast invalidation for entries that failed immediately. This keeps V25
    // wall-touch/scalp/breakout orders from waiting for the full SL after the
    // market moves against the original proof.
    if ((adaptive.enableEarlyInvalidation ?? true) && worst?.position && !worst.isSafe) {
      const strategyText = String(worst.journal?.strategy ?? '').toUpperCase();
      const isFastInvalidationStrategy =
        strategyText.startsWith('V25_') ||
        strategyText.includes('SCALP') ||
        strategyText.includes('BREAKOUT') ||
        strategyText.includes('MOMENTUM') ||
        strategyText === 'MEAN_REVERSION';
      const rawOpposesWorst =
        rawMarketSide !== 'SKIP' &&
        rawMarketSide !== worst.position.side;
      const openedAtMs = worst.position.openedAtMs ?? worst.journal?.createdAt ?? 0;
      const ageMs = openedAtMs > 0 ? Date.now() - openedAtMs : Number.POSITIVE_INFINITY;
      const minAgeMs = adaptive.earlyInvalidationMinAgeMs ?? 90_000;
      const reduceAtR = adaptive.earlyInvalidationR ?? -0.45;
      const closeAtR = adaptive.earlyInvalidationCloseR ?? -0.65;
      const invalidationConfirmed = isFastInvalidationStrategy || rawOpposesWorst || rawMarketOpposesNet;

      if (ageMs >= minAgeMs && invalidationConfirmed && worst.r <= closeAtR) {
        managementEventsTotal.inc({ symbol: cluster.symbol, event: 'early_invalidation_close' });
        return {
          mode: 'CLOSE',
          summary: `Early invalidation close ${cluster.symbol} #${worst.position.ticket}`,
          reason: `Trade proof failed early: ${round2(worst.r)}R <= ${closeAtR}R after ${round1(ageMs / 1000)}s, strategy=${strategyText || 'unknown'}, market=${rawMarketSide}. Closing before full SL.`,
          ticket: worst.position.ticket,
          closeVolume: worst.position.volume,
          allowOverLimitDefense: false,
          playbook,
        };
      }

      if (ageMs >= minAgeMs && invalidationConfirmed && worst.r <= reduceAtR) {
        const minCloseVolume = cfg.risk.minLotStep ?? cfg.risk.minLot ?? 0.01;
        const closeVolume = round2(Math.max(minCloseVolume, worst.position.volume * 0.5));
        managementEventsTotal.inc({ symbol: cluster.symbol, event: 'early_invalidation_reduce' });
        return {
          mode: 'REDUCE',
          summary: `Early invalidation reduce ${cluster.symbol} #${worst.position.ticket}`,
          reason: `Trade proof weakening: ${round2(worst.r)}R <= ${reduceAtR}R after ${round1(ageMs / 1000)}s, strategy=${strategyText || 'unknown'}, market=${rawMarketSide}. Reducing risk before SL.`,
          ticket: worst.position.ticket,
          closeVolume: Math.min(worst.position.volume, closeVolume),
          allowOverLimitDefense: false,
          playbook,
        };
      }
    }

    // --- PORTFOLIO-PROTECTION RULES (2026-04-24) ---------------------------
    // These fire BEFORE other heuristics so the book can self-heal when hedged.
    const hasBuy = cluster.positions.some((p) => p.side === 'BUY');
    const hasSell = cluster.positions.some((p) => p.side === 'SELL');
    const hasBothSides = hasBuy && hasSell;
    const netProfit = cluster.totalProfit;

    // 1) HEDGED BOOK NEAR NET-BREAKEVEN → close losing leg to lock in draw
    //    User intent: "รีบปิดหนีเมื่อเสมอทุน" (close quickly when at break-even).
    if (hasBothSides && Math.abs(netProfit) <= netBeProfitBand && totalHeatR <= hedgedExitMaxHeatR) {
      const loser = matchedRows.filter((m: any) => m.position.profit < 0)
                               .sort((a: any, b: any) => a.position.profit - b.position.profit)[0];
      if (loser) {
        return {
          mode: 'CLOSE',
          summary: `hedged book at net-BE — close losing leg`,
          reason: `net pnl=${round2(netProfit)} within ±${netBeProfitBand}; heat=${round2(totalHeatR)}R ≤ ${hedgedExitMaxHeatR}R — close loser #${loser.position.ticket} to exit hedge`,
          ticket: loser.position.ticket,
          closeVolume: loser.position.volume,
          allowOverLimitDefense: false,
          playbook,
        };
      }
    }

    // --- P3.1: FLIP_CLUSTER — Regime Reversal Detection ---
    // When H4 regime has clearly flipped against the cluster's dominant side
    // AND market confluence is strong, close the weakest loser to free capacity.
    // This is the first step of "flipping" — closing losers on old bias.
    const enableFlipCluster = adaptive.enableFlipCluster !== false;
    if (enableFlipCluster && cluster.netSide !== 'FLAT' && losingPressure) {
      const regimeReversed =
        (cluster.netSide === 'BUY' && analysis.regime === 'TRENDING_DOWN' && analysis.bias === 'BEAR') ||
        (cluster.netSide === 'SELL' && analysis.regime === 'TRENDING_UP' && analysis.bias === 'BULL');
      const strongReversal = analysis.confluence >= 65 && regimeReversed;
      if (strongReversal && marketOpposesNet && worstLossR <= -0.5) {
        const worstLoser = worst?.position ?? cluster.losingPositions[0];
        if (worstLoser) {
          managementEventsTotal.inc({ symbol: cluster.symbol, event: 'flip_cluster' });
          // P3.1 second leg — instruct orchestrator to open opposite-side trade at
          // 50% of original cluster volume after the close completes successfully.
          // Side mapping: cluster BUY → SELL, cluster SELL → BUY.
          const oppositeSide: 'BUY' | 'SELL' = cluster.netSide === 'BUY' ? 'SELL' : 'BUY';
          const flipFraction = adaptive.flipOppositeFraction ?? 0.5;
          const baseVol = Math.abs(cluster.netVolume) || proposedVolume;
          const oppositeVolume = round2(Math.max(cfg.risk.minLot, Math.min(baseVol * flipFraction, proposedVolume)));
          return {
            mode: 'CLOSE',
            summary: `FLIP: close worst loser + open opposite — regime reversed against ${cluster.netSide}`,
            reason: `H4 regime flipped to ${analysis.regime} (${analysis.bias}) with ${round1(analysis.confluence)}% confluence; cluster net=${cluster.netSide} is losing (${round2(worstLossR)}R). Close #${worstLoser.ticket} and open ${oppositeSide} ${oppositeVolume} (=${round2(flipFraction * 100)}% of cluster).`,
            ticket: worstLoser.ticket,
            closeVolume: worstLoser.volume,
            allowOverLimitDefense: false,
            playbook,
            triggerOpposite: {
              side: oppositeSide,
              volume: oppositeVolume,
              rationale: `FLIP_CLUSTER: H4 regime ${analysis.regime} (${analysis.bias}) confluence=${round1(analysis.confluence)}% — opening ${oppositeSide} ${oppositeVolume} after closing #${worstLoser.ticket}`,
            },
          };
        }
      }
    }

    // --- P3.2: Cluster Concentration Guard ---
    // Prevent excessive same-side positions clustered near same price (within 10 pips).
    // This reduces risk from correlated stops being hit simultaneously.
    //
    // V24.0 (2026-05-07) — Basket BE Guard:
    //   ก่อนปิดไม้ที่ "ขาดทุน" ตรวจว่า basket รวม >= 0 (ทำกำไร/เท่าทุนได้จริง)
    //   เดิม: ปิดไม้ขาดทุนแล้วเรียก "BE" ทำให้ขาดทุนสะสมทั้งที่ basket ยังลบ
    //   ใหม่: ถ้า basket ลบ → log warning + ไม่ปิด (รอให้ recovery หรือ hedge แทน)
    //         ถ้า basket = / + → ปิด weakest ที่กำไรน้อยสุดได้ (เพื่อลด correlated risk)
    const maxConcentration = adaptive.maxClusterConcentration ?? 3;
    const requireBasketBe = adaptive.requireBasketBeBeforeClose ?? true;
    if (cluster.positions.length >= maxConcentration) {
      const sameSidePositions = cluster.positions.filter(p => p.side === cluster.netSide);
      if (sameSidePositions.length >= maxConcentration) {
        // Check if positions are clustered within a tight price band
        const prices = sameSidePositions.map(p => p.priceOpen).sort((a, b) => a - b);
        const priceRange = prices[prices.length - 1] - prices[0];
        const avgPrice = prices.reduce((s, p) => s + p, 0) / prices.length;
        const pipRange = avgPrice > 0 ? (priceRange / avgPrice) * 10000 : 0; // in pips
        if (pipRange <= 10 && sameSidePositions.length > maxConcentration) {
          // V24.0: คำนวณ basket profit ของฝั่งนี้ก่อนตัดสินใจ
          const basketProfit = sameSidePositions.reduce((s, p) => s + p.profit, 0);
          const weakest = sameSidePositions
            .sort((a, b) => a.profit - b.profit)[0];

          if (requireBasketBe && basketProfit < 0) {
            // basket ยังลบ — ห้ามปิดไม้ขาดทุนแล้วเรียก "BE"
            // (เคยทำแบบนี้ทำให้ขาดทุนสะสม — เปลี่ยนเป็น HOLD รอ recovery)
            managementEventsTotal.inc({ symbol: cluster.symbol, event: 'concentration_hold_basket_negative' });
            // ไม่ return — ปล่อยให้ flow ไปทำ HEDGE / SCALE_IN ตามปกติ
            // log เพื่อ audit
            // (หมายเหตุ: เก็บ note ใส่ playbook summary ไม่ได้เพราะเป็น parameter — log ผ่าน metrics)
          } else if (weakest) {
            // basket BE/positive แล้ว — ปิด weakest ได้ปลอดภัย
            const weakestProfit = weakest.profit;
            const closeAllowed = weakestProfit >= 0  // ปิดเฉพาะไม้ที่ไม่ขาดทุน
              || basketProfit >= Math.abs(weakestProfit); // หรือ basket bsketcover ขาดทุนของไม้นี้ได้
            if (closeAllowed) {
              managementEventsTotal.inc({ symbol: cluster.symbol, event: 'concentration_close' });
              return {
                mode: 'CLOSE',
                summary: `concentration guard — ${sameSidePositions.length} ${cluster.netSide} within ${round1(pipRange)} pips (basket ${round2(basketProfit)})`,
                reason: `${sameSidePositions.length} same-side positions within ${round1(pipRange)} pips exceeds max ${maxConcentration}. Basket=${round2(basketProfit)} ≥ |weakest|=${round2(Math.abs(weakestProfit))}. Closing weakest #${weakest.ticket} to reduce correlated risk.`,
                ticket: weakest.ticket,
                closeVolume: weakest.volume,
                allowOverLimitDefense: false,
                playbook,
              };
            }
          }
        }
      }
    }

    // 2) SCALE into WINNING SIDE on confluence pullback
    //    User intent: "มีจังหวะ ต้องเพิ่มไม้ฝั่งได้เปรียบ".
    //    Only when: market aligns with cluster AND cluster is profitable AND confluence fresh.
    const marketSideEarly =
      analysis.confluence < adverseConfluence ? 'SKIP' :
      analysis.bias === 'BULL' ? 'BUY' :
      analysis.bias === 'BEAR' ? 'SELL' :
      'SKIP';
    const marketAlignsEarly = marketSideEarly !== 'SKIP' && marketSideEarly === cluster.netSide;
    if (
      marketAlignsEarly &&
      netProfit >= scaleWinnerMinProfit &&
      totalHeatR < 0.5 &&
      cluster.positions.length < maxSameSymbolPositions &&
      adaptive.allowScaleInRecovery !== false
    ) {
      const winnerFraction = this.clampFraction(defaultScaleInRatio, scaleInRatioMin, scaleInRatioMax, defaultScaleInRatio);
      const baseVol = Math.abs(cluster.netVolume) || proposedVolume;
      const winnerVolume = round2(Math.max(cfg.risk.minLot, Math.min(baseVol * winnerFraction, proposedVolume)));
      return {
        mode: 'SCALE_IN',
        summary: `scale into winning ${cluster.netSide} side`,
        reason: `cluster profitable ($${round2(netProfit)}) + market aligns ${marketSideEarly} + low heat (${round2(totalHeatR)}R) → add ${round2(winnerFraction * 100)}%`,
        side: cluster.netSide === 'BUY' ? 'BUY' : 'SELL',
        volume: winnerVolume,
        fraction: winnerFraction,
        isAiDefense: false,
        allowOverLimitDefense: false, // scale-in on winners does NOT need override
        playbook,
      };
    }

    // 3) PROACTIVE HEDGE when heat rises & market opposes
    //    User intent: "hedge เมื่อต้องการรักษาพอร์ต"
    if (
      !hasBothSides &&
      totalHeatR >= hedgeTriggerHeatR &&
      cluster.netSide !== 'FLAT' &&
      marketSideEarly !== 'SKIP' &&
      marketSideEarly !== cluster.netSide &&
      adaptive.allowCounterHedge !== false
    ) {
      const protectFraction = this.clampFraction(defaultHedgeRatio, hedgeRatioMin, hedgeRatioMax, defaultHedgeRatio);
      const protectVol = round2(Math.max(cfg.risk.minLot, Math.min(Math.abs(cluster.netVolume) * protectFraction, proposedVolume)));
      return {
        mode: 'HEDGE',
        summary: `protect ${cluster.symbol} — hedge against adverse move`,
        reason: `heat=${round2(totalHeatR)}R >= ${hedgeTriggerHeatR}R trigger + market=${marketSideEarly} opposes net=${cluster.netSide} → protective hedge ${round2(protectFraction * 100)}%`,
        side: marketSideEarly === 'BUY' ? 'BUY' : 'SELL',
        volume: protectVol,
        fraction: protectFraction,
        isAiDefense: true,
        allowOverLimitDefense: true,  // Protect mode is allowed to bypass anti-hedge
        playbook,
      };
    }

    if (cfg.newsRisk?.enabled && cfg.newsRisk.minutesToEvent !== undefined && cfg.newsRisk.minutesToEvent < 30) {
        if (worst?.position && worst.r > 0) {
            return {
                mode: 'TRAIL',
                summary: `Pre-News Tighten`,
                reason: `Imminent News (${cfg.newsRisk.activeEvent}) in ${cfg.newsRisk.minutesToEvent}m; tightening SL to lock gains.`,
                ticket: worst.position.ticket,
                playbook,
            };
        }
    }

    // 0. SMC-Based SL Optimization (Tighten SL for legacy wide-stop orders)
    if (worst?.position && aiDecision?.sl && aiDecision.action === worst.position.side) {
        const currentSl = worst.position.sl;
        const proposedSl = aiDecision.sl;
        const isBuy = worst.position.side === 'BUY';
        
        // If the AI suggests a much tighter (better) SL based on new SMC data
        const betterSl = isBuy ? (proposedSl > currentSl) : (proposedSl < currentSl);
        if (betterSl && Math.abs(proposedSl - currentSl) > 5.0) { // Only if more than 500 points improvement
            return {
                mode: 'TRAIL',
                summary: `SMC SL Optimization`,
                reason: `Tightening SL to SMC invalidation zone (${proposedSl}) to improve Risk/Reward`,
                ticket: worst.position.ticket,
                sl: proposedSl,
                playbook,
            };
        }
    }

    if (worst?.position && worstLossR > 0) {
        const r = worstLossR;
        const journal = worst.journal;
        const strategyText = String(journal?.strategy ?? '').toUpperCase();
        const isScalp = strategyText === 'SCALPING' || strategyText === 'MEAN_REVERSION'
                     || strategyText.includes('SCALP')
                     || strategyText.startsWith('V25_');

        // --- P2.1: Staged BE/Trail System ---
        // Stage 1 (1R):     25% partial close + move SL to BE
        // Stage 2 (2R):     50% partial close + trail SL at entry + ATR
        // Stage 3 (3R):     trail SL at ATR×1.5 below/above price
        // Stage 4 (Golden): tighter trail (ATR×0.7) once R >= goldenThresholdR + 1
        //                   so Golden runners give back less of accrued gains.
        const enableStaged = adaptive.enableStagedPartials !== false;

        if (enableStaged) {
            // 2026-04-30 — Stage 4 / Golden Tightening.  When a position is in
            // "Golden" territory (R >= goldenThresholdR + 1, i.e. ≥3R with
            // default 2R Golden cutoff), tighten the trail to ATR×0.7 so
            // we lock in the bigger share of an outsized run instead of
            // letting it bleed back through the wider Stage-3 trail.
            const goldenTightR = (adaptive.goldenThresholdR ?? 2.0) + 1.0;
            const goldenTrailMultiplier = adaptive.goldenTrailMultiplier ?? 0.7;
            if (r >= goldenTightR && !openJournalForSymbol.some(j => j.mt5Ticket === worst.position.ticket && j.aiReview?.includes('TRAIL_GOLD'))) {
                const slRisk = Math.abs((journal?.sl ?? 0) - (journal?.entry ?? worst.position.priceOpen));
                const trailDistance = slRisk > 0 ? slRisk * goldenTrailMultiplier : worst.position.priceOpen * 0.0015;
                const trailSl = worst.position.side === 'BUY'
                    ? worst.position.priceCurrent - trailDistance
                    : worst.position.priceCurrent + trailDistance;
                managementEventsTotal.inc({ symbol: cluster.symbol, event: 'golden_trail_tighten' });
                return {
                    mode: 'TRAIL',
                    summary: `Golden Trail (${round2(r)}R) — ATR×${goldenTrailMultiplier} tight`,
                    reason: `Golden runner at ${round2(r)}R: tightening trail to ATR×${goldenTrailMultiplier} to lock in the bigger share of accrued gains.`,
                    ticket: worst.position.ticket,
                    sl: round2(trailSl),
                    playbook,
                };
            }

            // Stage 3: Trail at 3R+ (ATR×1.5 trailing stop)
            if (r >= 3.0 && !openJournalForSymbol.some(j => j.mt5Ticket === worst.position.ticket && j.aiReview?.includes('TRAIL_3R'))) {
                // Use ATR from analysis if available, otherwise estimate from journal risk
                const slRisk = Math.abs((journal?.sl ?? 0) - (journal?.entry ?? worst.position.priceOpen));
                const trailDistance = slRisk > 0 ? slRisk * 1.5 : worst.position.priceOpen * 0.003;
                const trailSl = worst.position.side === 'BUY'
                    ? worst.position.priceCurrent - trailDistance
                    : worst.position.priceCurrent + trailDistance;
                managementEventsTotal.inc({ symbol: cluster.symbol, event: 'staged_partial' });
                return {
                    mode: 'TRAIL',
                    summary: `Stage 3 Trail (${round2(r)}R) — ATR×1.5 trailing`,
                    reason: `Reached 3R reward; engaging trailing stop at ATR×1.5 distance`,
                    ticket: worst.position.ticket,
                    sl: round2(trailSl),
                    playbook,
                };
            }

            // Stage 2: Partial 50% at 2R + trail SL at entry + ATR
            if (r >= 2.0 && cluster.positions.length > 0 && !openJournalForSymbol.some(j => j.mt5Ticket === worst.position.ticket && j.aiReview?.includes('PARTIAL_2R'))) {
                const closeVolume = round2(Math.max(cfg.risk.minLotStep, worst.position.volume * 0.5));
                managementEventsTotal.inc({ symbol: cluster.symbol, event: 'staged_partial' });
                return {
                    mode: 'PARTIAL_CLOSE',
                    summary: `Stage 2 Partial Profit 2R (50%)`,
                    reason: `Reached 2R reward; locking 50% profits + trailing SL`,
                    ticket: worst.position.ticket,
                    closeVolume,
                    playbook,
                };
            }

            // Stage 1: Partial 25% at 1R + move SL to BE
            if (r >= 1.0 && cluster.positions.length > 0 && !openJournalForSymbol.some(j => j.mt5Ticket === worst.position.ticket && j.aiReview?.includes('PARTIAL_1R'))) {
                const closeVolume = round2(Math.max(cfg.risk.minLotStep, worst.position.volume * 0.25));
                managementEventsTotal.inc({ symbol: cluster.symbol, event: 'staged_partial' });
                return {
                    mode: 'PARTIAL_CLOSE',
                    summary: `Stage 1 Partial Profit 1R (25%) + BE`,
                    reason: `Reached 1R reward; locking 25% profits and moving SL to breakeven`,
                    ticket: worst.position.ticket,
                    closeVolume,
                    playbook,
                };
            }
        }

        // Break Even Logic (BE) — fallback when staged partials disabled
        const posEntry = worst.position.priceOpen > 0 ? worst.position.priceOpen : (journal?.entry ?? 0);
        const posSl = journal?.sl ?? worst.position.sl;
        const earlyBeR = adaptive.earlyBreakevenR ?? (isScalp ? 0.35 : 0.4);
        const beTriggerR = isScalp
            ? Math.min(0.5, adaptive.scalpBreakEvenTriggerR ?? earlyBeR)
            : earlyBeR;
        const allowEarlyBe =
            (adaptive.enableEarlyBreakeven ?? true) &&
            (cluster.symbol.toUpperCase().includes('XAU') || isScalp || analysis.regime === 'RANGING' || analysis.regime === 'VOLATILE_BREAKOUT');
        if (r >= beTriggerR && posSl && posEntry && posSl !== posEntry) {
            if (allowEarlyBe || isScalp || r >= 0.8) {
                return {
                    mode: 'BREAKEVEN',
                    summary: `Move to BE (${round2(r)}R reached)`,
                    reason: `Locking entry as trade is moving in favor at ${round2(r)}R (trigger=${round2(beTriggerR)}R) | Strategy: ${journal?.strategy ?? 'unknown'}`,
                    ticket: worst.position.ticket,
                    playbook,
                };
            }
        }
    }

    if (forcedClose && closeScoreAllowsOverride) {
      const targetPos = targetTicket ? cluster.positions.find((p: PositionRow) => p.ticket === targetTicket) : worst?.position;
      if (targetPos) {
        return {
          mode: 'CLOSE',
          summary: `AI forced close on ${cluster.symbol} #${targetPos.ticket}`,
          reason: `AI decision with ${aiConfidence}% confidence: ${rationale}`,
          ticket: targetPos.ticket,
          playbook,
          isAiDefense: true,
          allowOverLimitDefense: true,
        };
      }
    }

    if ((marketOpposesNet && losingPressure) || finalMode === 'REDUCE' || recommendedMode === 'HEDGE' || recommendedMode === 'CLOSE' || recommendedMode === 'REDUCE') {
      const worstLoser = worst?.position ?? cluster.losingPositions[0];
      if (worstLossR <= closeLossThresholdR && worstLoser) {
        return {
          mode: 'CLOSE',
          summary: `hard stop close ${cluster.symbol}`,
          reason: `loss reached ${round2(worstLossR)}R and market is still adverse`,
          ticket: worstLoser.ticket,
          closeVolume: worstLoser.volume,
          allowOverLimitDefense: false,
          playbook,
        };
      }
      if (adaptive.allowCounterHedge !== false && (canAddDefense || isAiDefense) && (preferHedge || finalMode === 'HEDGE')) {
        if (!atHardCap) {
          return {
            mode: 'HEDGE',
            summary: `hedge ${cluster.symbol} against adverse trend`,
            reason: isAiDefense ? `AI high-confidence hedge signal | ${playbook.summary}` : `open ${marketSide} hedge at ${round2(hedgeFraction * 100)}% of net exposure to offset losing ${cluster.netSide} book | ${playbook.summary}`,
            side: marketSide === 'BUY' ? 'BUY' : 'SELL',
            volume: hedgeVolume,
            fraction: hedgeFraction,
            isAiDefense,
            allowOverLimitDefense: isAiDefense || (adaptive.allowOverLimitDefense !== false && overPositionLimit),
            playbook,
          };
        }
      }
      if (worstLoser && (marketOpposesNet || reduceScoreAllowsOverride || atHardCap)) {
        return {
          mode: 'REDUCE',
          summary: `reduce worst ${cluster.symbol} loser`,
          reason: marketOpposesNet
            ? `market bias ${marketSide} is against net ${cluster.netSide}; trim weakest ticket #${worstLoser.ticket} | ${playbook.summary}`
            : `reduce weakest ticket #${worstLoser.ticket} by playbook/risk guard; market=${marketSide}, net=${cluster.netSide} | ${playbook.summary}`,
          ticket: worstLoser.ticket,
          closeVolume: round2(Math.max(cfg.risk.minLot, worstLoser.volume * 0.5)),
          allowOverLimitDefense: adaptive.allowOverLimitDefense !== false && overPositionLimit,
          playbook,
        };
      }
    }

    // Scale-in guard: reuse totalHeatR computed earlier (line 176)
    const maxHeatForScaleIn = adaptive.maxHeatForScaleIn ?? 1.5; // default: block scale-in if heat > 1.5R
    const heatAllowsScaleIn = totalHeatR <= maxHeatForScaleIn || (isAiDefenseForScaleIn && aiConfidence >= 90);

    // 2026-04-30 — ANTI-MARTINGALE VOLUME CAP.
    // Recovery DCA must NEVER use a larger volume than the most-recent
    // position on the same side.  Doubling-down on losers is the classic
    // martingale trap.  We snap `scaleInVolume` down to the smallest of:
    //   • the latest same-side position's volume
    //   • the average same-side volume (defensive floor when latest is huge)
    //   • the original scale-in volume that was computed
    const sameSideVols = cluster.positions
      .filter(p => p.side === cluster.netSide)
      .map(p => p.volume)
      .filter(v => v > 0);
    if (sameSideVols.length > 0) {
      const latestSameSide = cluster.positions
        .filter(p => p.side === cluster.netSide)
        .sort((a, b) => b.ticket - a.ticket)[0];
      const latestVol = latestSameSide?.volume ?? Math.max(...sameSideVols);
      const avgVol = sameSideVols.reduce((a, b) => a + b, 0) / sameSideVols.length;
      const cap = Math.min(latestVol, avgVol);
      if (scaleInVolume > cap) {
        scaleInVolume = round2(Math.max(cfg.risk.minLot, cap));
      }
    }

    if (
      heatAllowsScaleIn &&
      marketAlignsWithNet &&
      (losingPressure || isAiDefenseForScaleIn) &&
      (worstLossR <= scaleInLossThresholdR || isAiDefenseForScaleIn) &&
      adaptive.allowScaleInRecovery !== false &&
      (canAddDefense || isAiDefenseForScaleIn) &&
      (scaleInSteps < maxScaleInStepsPerSymbol || isAiDefenseForScaleIn) &&
      (preferScaleIn || finalMode === 'SCALE_IN')
    ) {
      // --- P2.2: Smart Scale-In Price Guard ---
      // Only scale-in if current price is better than weighted average entry - 0.25×ATR.
      // This prevents adding at worse prices that increase cluster risk.
      const enableSmartScaleIn = adaptive.enableSmartScaleIn !== false;
      if (enableSmartScaleIn && cluster.positions.length > 0) {
        const weightedAvg = cluster.avgPrice;
        // Estimate ATR from cluster's risk spread
        const riskSpread = matchedRows
          .filter((m: any) => m.journal?.sl)
          .map((m: any) => Math.abs(m.position.priceOpen - (m.journal?.sl ?? m.position.sl)))
          .reduce((sum: number, v: number) => sum + v, 0) / Math.max(1, matchedRows.length);
        const atrEstimate = riskSpread > 0 ? riskSpread : weightedAvg * 0.002;
        const priceThreshold = cluster.netSide === 'BUY'
          ? weightedAvg - atrEstimate * 0.25  // for BUY: price should be below avg - buffer (better entry)
          : weightedAvg + atrEstimate * 0.25; // for SELL: price should be above avg + buffer (better entry)
        const currentPrice = worst?.position?.priceCurrent ?? 0;
        const priceBetterForBuy = cluster.netSide === 'BUY' && currentPrice <= priceThreshold;
        const priceBetterForSell = cluster.netSide === 'SELL' && currentPrice >= priceThreshold;
        if (!priceBetterForBuy && !priceBetterForSell && !isAiDefenseForScaleIn) {
          // Price is not better enough — skip scale-in, fall through to HOLD
          // (AI defense can override this for genuine emergencies)
        } else {
          const isRotation = atHardCap && worst?.position;
          return {
            mode: 'SCALE_IN',
            summary: isRotation ? `rotate out worst #${worst.position.ticket} and scale in` : `scale in with ${cluster.netSide} recovery`,
            reason: isAiDefenseForScaleIn 
              ? `AI high-confidence recovery signal (drawdown=${round2(worstLossR)}R) | ${playbook.summary}` 
              : `trend still supports ${cluster.netSide}; add ${round2(scaleInFraction * 100)}% at better price (${round2(currentPrice)} vs avg ${round2(weightedAvg)}) | ${playbook.summary}`,
            side: cluster.netSide === 'BUY' ? 'BUY' : 'SELL',
            volume: scaleInVolume,
            fraction: scaleInFraction,
            isAiDefense,
            allowOverLimitDefense: (adaptive.allowOverLimitDefense !== false && (overPositionLimit || isAiDefenseForScaleIn)),
            playbook,
            rotateTicket: isRotation ? worst.position.ticket : undefined,
            rotateVolume: isRotation ? worst.position.volume : undefined,
          };
        }
      } else {
        // Smart scale-in disabled or no positions — use original logic
        const isRotation = atHardCap && worst?.position;
        return {
          mode: 'SCALE_IN',
          summary: isRotation ? `rotate out worst #${worst.position.ticket} and scale in` : `scale in with ${cluster.netSide} recovery`,
          reason: isAiDefenseForScaleIn 
            ? `AI high-confidence recovery signal (drawdown=${round2(worstLossR)}R) | ${playbook.summary}` 
            : `trend still supports ${cluster.netSide}; add ${round2(scaleInFraction * 100)}% to improve average entry | ${playbook.summary}`,
          side: cluster.netSide === 'BUY' ? 'BUY' : 'SELL',
          volume: scaleInVolume,
          fraction: scaleInFraction,
          isAiDefense,
          allowOverLimitDefense: (adaptive.allowOverLimitDefense !== false && (overPositionLimit || isAiDefenseForScaleIn)),
          playbook,
          rotateTicket: isRotation ? worst.position.ticket : undefined,
          rotateVolume: isRotation ? worst.position.volume : undefined,
        };
      }
    }

    if (worst?.position && worstLossR <= reduceLossThresholdR && marketSide === 'SKIP') {
      return {
        mode: 'REDUCE',
        summary: `reduce passive drawdown on ${cluster.symbol}`,
        reason: `drawdown reached ${round2(worstLossR)}R without clear continuation edge | ${playbook.summary}`,
        ticket: worst.position.ticket,
        closeVolume: round2(Math.max(cfg.risk.minLot, worst.position.volume * 0.33)),
        playbook,
      };
    }

    return {
      mode: 'HOLD',
      summary: `hold ${cluster.symbol} cluster`,
      reason: `net=${cluster.netSide} pnl=${cluster.totalProfit} market=${marketSide} worstR=${round2(worstLossR)}`,
      playbook,
      isAiDefense,
      allowOverLimitDefense: false, // Critical Fix: HOLD must never leak defense limits to the main trading loop
    };
  }

  public computePlaybookScores(
    cfg: AutoTradingConfig,
    account: AccountSnapshot,
    cluster: PositionCluster,
    analysis: AnalysisSummary,
    openJournal: JournalRow[],
    learnSummary: LearnSummary | null
  ): PlaybookScores {
    const openJournalForSymbol = openJournal.filter((it: JournalRow) => it.outcome === 'OPEN' && it.symbol === cluster.symbol);
    const matchedRows = cluster.positions.map((position: PositionRow) => {
      const journal = openJournalForSymbol.find((row) => row.mt5Ticket === position.ticket);
      return {
        position,
        journal,
        r: this.positionRiskR(position, journal),
      };
    });
    const worst = matchedRows.slice().sort((a: any, b: any) => a.r - b.r || a.position.profit - b.position.profit)[0];
    const worstLossR = worst?.r ?? 0;
    const totalAbsExposure = Math.max(Math.abs(cluster.buyVolume) + Math.abs(cluster.sellVolume), 0.01);
    const exposureImbalanceRaw = Math.abs(cluster.buyVolume - cluster.sellVolume) / totalAbsExposure;
    const freeMarginPct = account.equity > 0 ? (account.freeMargin / account.equity) * 100 : 0;
    const learnWinRate = learnSummary?.winRate ?? 0.5;
    const clusterBias: Bias =
      cluster.netSide === 'BUY' ? 'BULL' :
      cluster.netSide === 'SELL' ? 'BEAR' :
      'NEUTRAL';
    const trendStrength = clamp((analysis.confluence * 0.6 + analysis.fitness * 0.4) / 100, 0, 1);
    const floatingLossPressure = clamp(Math.abs(Math.min(worstLossR, 0)) / 3, 0, 1);
    const exposureImbalance = clamp(exposureImbalanceRaw, 0, 1);
    const recoveryProbability = clamp(
      ((analysis.bias === clusterBias ? 0.45 : 0.15) + learnWinRate * 0.35 + trendStrength * 0.2),
      0,
      1
    );
    const marginHeadroom = clamp(freeMarginPct / Math.max(cfg.risk.minFreeMarginPct, 1), 0, 1.5);
    const scaleInSteps = openJournalForSymbol.filter((it) => String(it.aiReview || '').includes('SCALE_IN')).length;

    const actionScores: Record<ManagementMode, number> = {
      HOLD: clamp(0.45 + recoveryProbability * 0.25 - floatingLossPressure * 0.3, 0, 1),
      REDUCE: clamp(0.2 + floatingLossPressure * 0.45 + exposureImbalance * 0.2, 0, 1),
      CLOSE: clamp(0.05 + floatingLossPressure * 0.6 + (1 - marginHeadroom) * 0.25 + (analysis.bias === clusterBias ? 0 : 0.1), 0, 1),
      HEDGE: clamp(0.1 + floatingLossPressure * 0.35 + trendStrength * 0.2 + exposureImbalance * 0.2 + (analysis.bias !== clusterBias && cluster.netSide !== 'FLAT' ? 0.15 : 0), 0, 1),
      SCALE_IN: clamp(0.05 + recoveryProbability * 0.4 + trendStrength * 0.25 - floatingLossPressure * 0.15 - scaleInSteps * 0.12, 0, 1),
      TRAIL: clamp((worstLossR > 1.0 ? 0.4 : 0) + trendStrength * 0.3, 0, 1),
      BREAKEVEN: clamp((worstLossR > 0.5 ? 0.5 : 0), 0, 1),
      PARTIAL_CLOSE: clamp((worstLossR > 1.0 ? 0.45 : 0), 0, 1),
    };

    const recommendedMode = (Object.entries(actionScores).sort((a, b) => b[1] - a[1])[0]?.[0] ?? 'HOLD') as PlaybookScores['recommendedMode'];
    const summary = `recommended=${recommendedMode} trend=${round2(trendStrength)} loss=${round2(floatingLossPressure)} imbalance=${round2(exposureImbalance)} recovery=${round2(recoveryProbability)} margin=${round2(marginHeadroom)}`;
    return {
      dimensions: {
        trendStrength,
        floatingLossPressure,
        exposureImbalance,
        recoveryProbability,
        marginHeadroom,
      },
      actions: actionScores,
      recommendedMode,
      summary,
      worstLossR,
      scaleInSteps,
    };
  }

  public positionRiskR(position: PositionRow, journal: JournalRow | undefined): number {
    // Use actual broker fill price (priceOpen) as base for R calculation.
    // Using journal.entry causes slippage bias: if entry=4684 but fill=4683,
    // the position starts at r=-0.16 immediately which prevents BE from ever triggering.
    const baseEntry = (position.priceOpen > 0) ? position.priceOpen : (journal?.entry ?? 0);
    const sl = journal?.sl ?? position.sl; // Fallback to broker SL if no journal
    if (!baseEntry || !sl || baseEntry === sl) return 0;
    const risk = Math.abs(baseEntry - sl);
    if (!isFinite(risk) || risk <= 0) return 0;
    const reward = position.side === 'BUY'
      ? position.priceCurrent - baseEntry
      : baseEntry - position.priceCurrent;
    return reward / risk;
  }

  /**
   * Check if a position has locked in profit (SL >= Entry for BUY, SL <= Entry for SELL)
   */
  public isPositionSafe(position: PositionRow, journal: JournalRow | undefined): boolean {
    const entry = (position.priceOpen > 0) ? position.priceOpen : (journal?.entry ?? 0);
    const sl = position.sl;
    // If SL is missing (0), it's definitely NOT safe
    if (!sl) return false;
    // If entry is unknown, we can't determine safety
    if (!entry) return false;
    
    if (entry === sl) return true; // Breakeven is safe
    
    return position.side === 'BUY' ? (sl >= entry) : (sl <= entry);
  }

  public buildHistoryContext(cluster: PositionCluster, playbook?: PlaybookScores, learnSummary?: any): string {
    const learn = learnSummary
      ? `learned winRate=${round1(learnSummary.winRate * 100)}% totalProfit=${round2(learnSummary.totalProfit)} best=${learnSummary.bestStrategy ?? 'n/a'}`
      : 'no closed-trade learning yet';
    const playbookText = playbook
      ? `playbook=${playbook.summary}; actionScores=${JSON.stringify(playbook.actions)}`
      : 'playbook unavailable';
    if (cluster.positions.length === 0) {
      return `No open ${cluster.symbol} positions. ${learn}. ${playbookText}.`;
    }
    const rows = cluster.positions
      .map((it: PositionRow) => `#${it.ticket} ${it.side} vol=${it.volume} open=${it.priceOpen} now=${it.priceCurrent} pnl=${round2(it.profit)}`)
      .join(' | ');
    return `Open ${cluster.symbol} positions=${cluster.positions.length}, netSide=${cluster.netSide}, netVolume=${cluster.netVolume}, totalProfit=${cluster.totalProfit}. ${rows}. ${learn}. ${playbookText}.`;
  }

  public buildSkipDecision(
    symbol: string,
    regime: MarketRegime,
    bias: Bias,
    confluence: number,
    strategy: StrategyType,
    rationale: string,
    riskGate: string,
    translatedTh: string | null = null
  ): CycleDecision {
    return {
      symbol,
      regime,
      overallBias: bias,
      confluence: round1(confluence),
      fitness: 0,
      strategy,
      side: 'SKIP',
      entry: null,
      sl: null,
      tp: null,
      volume: null,
      rrr: 0,
      rationale,
      translatedTh,
      analyzersUsed: ['BROKER_MOMENTUM'],
      riskGate,
      executed: false,
      mt5Ticket: null,
      at: nowIso(),
      decisionId: `SKIP-${Date.now()}-${symbol}`,
    };
  }

  public clampFraction(input: number, min: number, max: number, fallback: number): number {
    if (!isFinite(input) || input <= 0) return fallback;
    return clamp(input, min, max);
  }

  public normalizeClosedJournalOutcome(row: JournalRow): JournalRow {
    if (!['WIN', 'LOSS', 'BE'].includes(row.outcome)) return row;
    const normalized = classifyOutcome(row.profit ?? 0, row.profitR);
    return normalized === row.outcome ? row : { ...row, outcome: normalized };
  }
}

export const tradeManagementService = new TradeManagementService();

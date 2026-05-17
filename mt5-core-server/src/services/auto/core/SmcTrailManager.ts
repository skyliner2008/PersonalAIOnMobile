/**
 * SMC Trail Manager — Enhanced Trade Management (Phase 5)
 * ใช้ SMC zones (OB, FVG, Liquidity, Structure) ในการ trail SL
 * แทนที่ fixed ATR-based trailing
 *
 * Improvements:
 * 1. Trail ตาม SMC zones — SL ย้ายไปที่ขอบ OB/FVG ที่ใกล้ที่สุด
 * 2. Regime Flip Recovery — ปิดฝั่งเดิมอัตโนมัติเมื่อ structure เปลี่ยน
 * 3. Anti-Hedge Fix — ปิดฝั่งเดิมก่อนเปิดฝั่งใหม่ (ไม่ block)
 * 4. CLP Enhancement — ลดการปิด BE ซ้ำที่ไม่จำเป็น
 *
 * 2026-05-06 — skyliner.jojo@gmail.com
 */

import type { SmcSnapshot, OrderBlock, FVG } from '../analyzers/smc/types.js';
import type { PositionRow, JournalRow, PositionCluster } from '../types.js';
import { round2 } from '../utils.js';

// ── SMC-Based Trail Targets ─────────────────────────────────────────

export interface SmcTrailTarget {
  ticket: number;
  currentSl: number;
  proposedSl: number;
  reason: string;
  zoneType: 'OB' | 'FVG' | 'SWING' | 'LIQUIDITY' | 'STRUCTURE';
  confidence: number;    // 0-100
}

/**
 * Compute SMC-based trail targets for all positions in a cluster.
 * Uses OB/FVG/Swing/Liquidity zones as structural SL levels.
 *
 * For BUY positions: trail SL up to below the nearest support zone
 * For SELL positions: trail SL down to above the nearest resistance zone
 */
export function computeSmcTrailTargets(
  cluster: PositionCluster,
  smc: SmcSnapshot,
  openJournal: JournalRow[],
  minImprovementPts: number = 1.0
): SmcTrailTarget[] {
  const targets: SmcTrailTarget[] = [];
  if (!smc || !smc.structure) return targets;

  for (const pos of cluster.positions) {
    const journal = openJournal.find(j => j.mt5Ticket === pos.ticket && j.outcome === 'OPEN');
    const entryPrice = pos.priceOpen;
    const currentSl = pos.sl;
    const currentPrice = pos.priceCurrent;

    // Only trail if position is in profit
    const inProfit = pos.side === 'BUY' ? currentPrice > entryPrice : currentPrice < entryPrice;
    if (!inProfit) continue;

    let proposedSl: number | null = null;
    let reason = '';
    let zoneType: SmcTrailTarget['zoneType'] = 'SWING';
    let confidence = 60;

    if (pos.side === 'BUY') {
      // For BUY: find nearest support zone BELOW current price but ABOVE current SL
      const supportLevels = collectSupportLevels(smc, currentPrice, currentSl);
      if (supportLevels.length > 0) {
        // Pick the highest support (most aggressive trail)
        const best = supportLevels.sort((a, b) => b.price - a.price)[0];
        // Buffer: 0.3% below the zone
        proposedSl = best.price * 0.997;
        reason = `Trail BUY SL to ${best.type} support at ${round2(best.price)}`;
        zoneType = best.type;
        confidence = best.confidence;
      }
    } else {
      // For SELL: find nearest resistance zone ABOVE current price but BELOW current SL
      const resistanceLevels = collectResistanceLevels(smc, currentPrice, currentSl);
      if (resistanceLevels.length > 0) {
        const best = resistanceLevels.sort((a, b) => a.price - b.price)[0];
        proposedSl = best.price * 1.003;
        reason = `Trail SELL SL to ${best.type} resistance at ${round2(best.price)}`;
        zoneType = best.type;
        confidence = best.confidence;
      }
    }

    if (proposedSl !== null) {
      // Ensure the new SL is better than current
      const isBetter = pos.side === 'BUY'
        ? proposedSl > currentSl + minImprovementPts
        : proposedSl < currentSl - minImprovementPts;

      // Ensure SL doesn't cross the current price (would close immediately)
      const isSafe = pos.side === 'BUY'
        ? proposedSl < currentPrice
        : proposedSl > currentPrice;

      if (isBetter && isSafe) {
        targets.push({
          ticket: pos.ticket,
          currentSl,
          proposedSl: round2(proposedSl),
          reason,
          zoneType,
          confidence,
        });
      }
    }
  }

  return targets;
}

// ── Regime Flip Detection ───────────────────────────────────────────

export interface RegimeFlipAction {
  action: 'CLOSE_LOSING' | 'CLOSE_ALL' | 'HOLD';
  tickets: number[];
  reason: string;
  newSide?: 'BUY' | 'SELL';
}

/**
 * Detect if a regime flip warrants closing positions.
 * Called when REGIME_CHANGE event fires.
 *
 * Rules:
 * - CHoCH against cluster → close all losing positions on old side
 * - BOS continuing → hold (trend continues)
 * - Structure change + cluster profitable → tighten trail only
 */
export function detectRegimeFlipAction(
  cluster: PositionCluster,
  smc: SmcSnapshot,
  _openJournal: JournalRow[]
): RegimeFlipAction {
  if (cluster.positions.length === 0 || cluster.netSide === 'FLAT') {
    return { action: 'HOLD', tickets: [], reason: 'No positions' };
  }

  const structure = smc.structure;

  // CHoCH against cluster direction → close losers
  if (structure.lastEvent === 'CHoCH') {
    const isAgainstCluster =
      (cluster.netSide === 'BUY' && structure.lastEventSide === 'DOWN') ||
      (cluster.netSide === 'SELL' && structure.lastEventSide === 'UP');

    if (isAgainstCluster) {
      // Protect fresh positions (< 5 mins old) from being immediately closed due to spread
      const losers = cluster.losingPositions
        .filter(p => p.openedAtMs ? (Date.now() - p.openedAtMs > 5 * 60 * 1000) : true)
        .map(p => p.ticket);
      
      const newSide: 'BUY' | 'SELL' = cluster.netSide === 'BUY' ? 'SELL' : 'BUY';

      if (losers.length > 0) {
        return {
          action: 'CLOSE_LOSING',
          tickets: losers,
          reason: `CHoCH_${structure.lastEventSide} against ${cluster.netSide} cluster — closing ${losers.length} loser(s) for regime flip`,
          newSide,
        };
      }
    }
  }

  return { action: 'HOLD', tickets: [], reason: 'No regime flip detected' };
}

// ── Anti-Hedge Improvement ──────────────────────────────────────────

export interface AntiHedgeAction {
  shouldCloseFirst: boolean;
  ticketsToClose: number[];
  reason: string;
}

/**
 * Improved anti-hedge logic: instead of blocking the opposite side,
 * close the losing positions on the old side first.
 *
 * Old behavior: BUY exists → SELL blocked (user stuck)
 * New behavior: BUY exists + SELL signal → close worst BUY loser → then open SELL
 */
export function improvedAntiHedge(
  cluster: PositionCluster,
  newSide: 'BUY' | 'SELL'
): AntiHedgeAction {
  if (cluster.netSide === 'FLAT' || cluster.netSide === newSide) {
    return { shouldCloseFirst: false, ticketsToClose: [], reason: 'Same side or flat' };
  }

  // Opposite side detected
  const oppositeSidePositions = cluster.positions.filter(p =>
    (newSide === 'BUY' && p.side === 'SELL') ||
    (newSide === 'SELL' && p.side === 'BUY')
  );

  if (oppositeSidePositions.length === 0) {
    return { shouldCloseFirst: false, ticketsToClose: [], reason: 'No opposite positions' };
  }

  // Find worst losers on opposite side
  const losers = oppositeSidePositions
    .filter(p => p.profit < 0)
    .sort((a, b) => a.profit - b.profit);

  if (losers.length > 0) {
    return {
      shouldCloseFirst: true,
      ticketsToClose: [losers[0].ticket], // Close worst loser first
      reason: `Close worst ${losers[0].side} loser #${losers[0].ticket} ($${round2(losers[0].profit)}) before opening ${newSide}`,
    };
  }

  // All opposite positions are in profit — don't close them
  return {
    shouldCloseFirst: false,
    ticketsToClose: [],
    reason: `All ${oppositeSidePositions[0].side} positions profitable — holding`,
  };
}

// ── Helpers ─────────────────────────────────────────────────────────

interface ZoneLevel {
  price: number;
  type: SmcTrailTarget['zoneType'];
  confidence: number;
}

function collectSupportLevels(smc: SmcSnapshot, currentPrice: number, currentSl: number): ZoneLevel[] {
  const levels: ZoneLevel[] = [];

  // Bull OB tops (demand zones)
  for (const ob of smc.bullOBs.filter(ob => !ob.invalidated)) {
    if (ob.top < currentPrice && ob.top > currentSl) {
      levels.push({ price: ob.bottom, type: 'OB', confidence: ob.hasFVG ? 80 : 65 });
    }
  }

  // Bull FVG bottoms
  for (const fvg of smc.activeFVGs.filter(f => f.type === 'BULL' && !f.mitigated)) {
    if (fvg.bottom < currentPrice && fvg.bottom > currentSl) {
      levels.push({ price: fvg.bottom, type: 'FVG', confidence: 70 });
    }
  }

  // Swing lows
  for (const sl of smc.swingLows) {
    if (sl < currentPrice && sl > currentSl) {
      levels.push({ price: sl, type: 'SWING', confidence: 60 });
    }
  }

  // Liquidity zones (support)
  for (const lz of smc.liquidityZones.filter(z => !z.isHigh && !z.swept)) {
    if (lz.price < currentPrice && lz.price > currentSl) {
      levels.push({ price: lz.price, type: 'LIQUIDITY', confidence: 55 + lz.confluenceStars * 5 });
    }
  }

  // Structure low
  if (smc.structure.structureLow > currentSl && smc.structure.structureLow < currentPrice) {
    levels.push({ price: smc.structure.structureLow, type: 'STRUCTURE', confidence: 75 });
  }

  return levels;
}

function collectResistanceLevels(smc: SmcSnapshot, currentPrice: number, currentSl: number): ZoneLevel[] {
  const levels: ZoneLevel[] = [];

  // Bear OB bottoms (supply zones)
  for (const ob of smc.bearOBs.filter(ob => !ob.invalidated)) {
    if (ob.bottom > currentPrice && ob.bottom < currentSl) {
      levels.push({ price: ob.top, type: 'OB', confidence: ob.hasFVG ? 80 : 65 });
    }
  }

  // Bear FVG tops
  for (const fvg of smc.activeFVGs.filter(f => f.type === 'BEAR' && !f.mitigated)) {
    if (fvg.top > currentPrice && fvg.top < currentSl) {
      levels.push({ price: fvg.top, type: 'FVG', confidence: 70 });
    }
  }

  // Swing highs
  for (const sh of smc.swingHighs) {
    if (sh > currentPrice && sh < currentSl) {
      levels.push({ price: sh, type: 'SWING', confidence: 60 });
    }
  }

  // Liquidity zones (resistance)
  for (const lz of smc.liquidityZones.filter(z => z.isHigh && !z.swept)) {
    if (lz.price > currentPrice && lz.price < currentSl) {
      levels.push({ price: lz.price, type: 'LIQUIDITY', confidence: 55 + lz.confluenceStars * 5 });
    }
  }

  // Structure high
  if (smc.structure.structureHigh < currentSl && smc.structure.structureHigh > currentPrice) {
    levels.push({ price: smc.structure.structureHigh, type: 'STRUCTURE', confidence: 75 });
  }

  return levels;
}

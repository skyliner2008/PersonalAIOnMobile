import type { FVG, PremiumDiscountResult, PremiumDiscountZone } from '../analyzers/smc.js';

export type ZoneTradeSide = 'BUY' | 'SELL';

export interface ZoneLayer {
  timeframe: string;
  result?: PremiumDiscountResult | null;
  role?: 'major' | 'context' | 'execution';
}

type ZoneLayerRole = 'major' | 'context' | 'execution';
type NormalizedZoneLayer = {
  timeframe: string;
  result: PremiumDiscountResult;
  role: ZoneLayerRole;
};

export interface ZoneLayerContext {
  layers: NormalizedZoneLayer[];
  wrongLayers: string[];
  favorableLayers: string[];
  hardWrongLayers: string[];
  majorWrong: boolean;
  majorAllWrong: boolean;
  executionWrong: boolean;
  executionAllWrong: boolean;
  executionFavorable: boolean;
  hardMajorWrong: boolean;
  hardExecutionWrong: boolean;
  hasWrongZone: boolean;
  primaryLayer?: NormalizedZoneLayer;
  path: string;
}

const EXECUTION_TFS = new Set(['M1', 'M5', 'M15', 'M30']);
const MAJOR_TFS = new Set(['H4', 'D1', 'W1']);
const CONTEXT_TFS = new Set(['H1']);

export function zoneIsWrongForSide(side: ZoneTradeSide, zone: PremiumDiscountZone): boolean {
  return (side === 'BUY' && zone === 'PREMIUM') || (side === 'SELL' && zone === 'DISCOUNT');
}

export function zoneIsFavorableForSide(side: ZoneTradeSide, zone: PremiumDiscountZone): boolean {
  return (side === 'BUY' && zone === 'DISCOUNT') || (side === 'SELL' && zone === 'PREMIUM');
}

export function isDeepWrongZoneForSide(
  side: ZoneTradeSide,
  pctFromRange: number,
  hardBlockPct: number,
): boolean {
  return side === 'SELL'
    ? pctFromRange <= hardBlockPct
    : pctFromRange >= 100 - hardBlockPct;
}

function layerRole(layer: ZoneLayer): ZoneLayerRole {
  if (layer.role) return layer.role;
  const tf = layer.timeframe.toUpperCase();
  if (MAJOR_TFS.has(tf)) return 'major';
  if (CONTEXT_TFS.has(tf)) return 'context';
  return EXECUTION_TFS.has(tf) ? 'execution' : 'execution';
}

function isScalpLikeStrategy(strategy: string | null | undefined): boolean {
  if (!strategy) return false;
  const text = String(strategy).toUpperCase();
  return text === 'SCALPING' || text.includes('SCALP') || text === 'MEAN_REVERSION' || text.startsWith('V25_');
}

export function buildZoneLayerContext(
  side: ZoneTradeSide,
  layers: ZoneLayer[],
  hardBlockPct: number,
  strategy?: string,
): ZoneLayerContext {
  const deduped = new Map<string, NormalizedZoneLayer>();
  for (const layer of layers) {
    const result = layer.result;
    if (!result) continue;
    const timeframe = layer.timeframe.toUpperCase();
    if (!timeframe || deduped.has(timeframe)) continue;
    deduped.set(timeframe, { timeframe, result, role: layerRole(layer) });
  }

  const valid = [...deduped.values()];
  const isScalp = isScalpLikeStrategy(strategy);

  // Filter out Major and Context TFs for scalp-like strategies so they don't block
  const wrong = valid
    .filter((layer) => {
      if (isScalp && (layer.role === 'major' || layer.role === 'context')) return false;
      return true;
    })
    .filter((layer) => zoneIsWrongForSide(side, layer.result.zone));

  const favorable = valid.filter((layer) => zoneIsFavorableForSide(side, layer.result.zone));
  const hardWrong = wrong.filter((layer) => isDeepWrongZoneForSide(side, layer.result.pctFromRange, hardBlockPct));
  const majorLayers = valid.filter((layer) => layer.role === 'major' || layer.role === 'context');
  const executionLayers = valid.filter((layer) => layer.role === 'execution');
  
  const majorWrongLayers = majorLayers
    .filter((layer) => {
      if (isScalp && (layer.role === 'major' || layer.role === 'context')) return false;
      return true;
    })
    .filter((layer) => zoneIsWrongForSide(side, layer.result.zone));

  const executionWrongLayers = executionLayers.filter((layer) => zoneIsWrongForSide(side, layer.result.zone));
  const executionFavorableLayers = executionLayers.filter((layer) => zoneIsFavorableForSide(side, layer.result.zone));

  const primaryLayer =
    valid.find((layer) => layer.timeframe === 'H4') ??
    valid.find((layer) => layer.timeframe === 'H1') ??
    valid[0];

  return {
    layers: valid,
    wrongLayers: wrong.map((layer) => layer.timeframe),
    favorableLayers: favorable.map((layer) => layer.timeframe),
    hardWrongLayers: hardWrong.map((layer) => layer.timeframe),
    majorWrong: majorWrongLayers.length > 0,
    majorAllWrong: majorLayers.length > 0 && majorWrongLayers.length === majorLayers.length,
    executionWrong: executionWrongLayers.length > 0,
    executionAllWrong: executionLayers.length > 0 && executionWrongLayers.length === executionLayers.length,
    executionFavorable: executionFavorableLayers.length > 0,
    hardMajorWrong: hardWrong.some((layer) => {
      if (isScalp && (layer.role === 'major' || layer.role === 'context')) return false;
      return layer.role === 'major' || layer.role === 'context';
    }),
    hardExecutionWrong: hardWrong.some((layer) => layer.role === 'execution'),
    hasWrongZone: wrong.length > 0,
    primaryLayer,
    path: valid
      .map((layer) => `${layer.timeframe}:${layer.result.zone}(${layer.result.pctFromRange.toFixed(1)}%)`)
      .join(' | '),
  };
}

export function hasAlignedFvgNearPrice(
  side: ZoneTradeSide,
  price: number,
  fvgs: FVG[],
  options: {
    atr?: number;
    atrMultiplier?: number;
    tolerancePct?: number;
    minDistance?: number;
  } = {},
): boolean {
  const wanted = side === 'BUY' ? 'BULL' : 'BEAR';
  const tolerancePct = options.tolerancePct ?? 0.002;
  const atrDistance = Math.max(0, options.atr ?? 0) * (options.atrMultiplier ?? 0.35);
  const minDistance = options.minDistance ?? Math.abs(price) * 0.0002;
  const distance = Math.max(atrDistance, minDistance);

  return fvgs.some((fvg) => {
    if (fvg.type !== wanted) return false;
    const top = Math.max(fvg.top, fvg.bottom);
    const bottom = Math.min(fvg.top, fvg.bottom);
    return price <= top * (1 + tolerancePct) + distance &&
      price >= bottom * (1 - tolerancePct) - distance;
  });
}

export interface FvgFillMatch {
  fvg: FVG;
  fillPct: number;
  distanceFromEdge: number;
}

export function findAlignedFvgFill(
  side: ZoneTradeSide,
  price: number,
  fvgs: FVG[],
  options: {
    minFillPct?: number;
    edgeTolerancePct?: number;
    minDistance?: number;
  } = {},
): FvgFillMatch | null {
  const wanted = side === 'BUY' ? 'BULL' : 'BEAR';
  const minFillPct = options.minFillPct ?? 0.5;
  const edgeTolerancePct = options.edgeTolerancePct ?? 0.05;
  const minDistance = options.minDistance ?? Math.abs(price) * 0.00001;
  let best: FvgFillMatch | null = null;

  for (const fvg of fvgs) {
    if (fvg.type !== wanted) continue;

    const top = Math.max(fvg.top, fvg.bottom);
    const bottom = Math.min(fvg.top, fvg.bottom);
    const gapSize = top - bottom;
    if (gapSize <= 0) continue;

    const edgeAllowance = Math.max(minDistance, gapSize * edgeTolerancePct);
    const insideGap = price <= top + edgeAllowance && price >= bottom - edgeAllowance;
    if (!insideGap) continue;

    const clampedPrice = Math.min(top, Math.max(bottom, price));
    const fillPct = side === 'BUY'
      ? (top - clampedPrice) / gapSize
      : (clampedPrice - bottom) / gapSize;
    if (fillPct + 1e-9 < minFillPct) continue;

    const distanceFromEdge = Math.min(Math.abs(price - top), Math.abs(price - bottom));
    const match = { fvg, fillPct, distanceFromEdge };
    if (!best || match.fillPct > best.fillPct) best = match;
  }

  return best;
}

export function hasAlignedFvgFill(
  side: ZoneTradeSide,
  price: number,
  fvgs: FVG[],
  options?: Parameters<typeof findAlignedFvgFill>[3],
): boolean {
  return findAlignedFvgFill(side, price, fvgs, options) !== null;
}

export interface SmcSignalLike {
  name?: string;
  bias?: string;
  score?: number;
  evidence?: string;
}

export interface OpposingScalpPressure {
  reason: string;
  evidence: string[];
}

export function findOpposingScalpPressure(
  side: ZoneTradeSide,
  signals: SmcSignalLike[] | null | undefined,
): OpposingScalpPressure | null {
  const list = Array.isArray(signals) ? signals : [];
  const oppositeBias = side === 'BUY' ? 'BEAR' : 'BULL';
  const oppositeObName = side === 'BUY' ? 'price_in_bear_ob' : 'price_in_bull_ob';

  const opposingOb = list.find((signal) => signal.name === oppositeObName);
  const opposingSweep = list.find((signal) =>
    signal.name === 'liquidity_sweep' &&
    String(signal.bias || '').toUpperCase() === oppositeBias
  );
  const opposingFvg = list.find((signal) =>
    signal.name === 'fvg_detected' &&
    String(signal.bias || '').toUpperCase() === oppositeBias
  );

  // A fresh liquidity sweep against our direction is enough pressure to block a tight scalp/reversion,
  // we do not need to wait for a fully formed opposing FVG.
  if (!opposingOb && !opposingSweep) return null;

  const evidence = [opposingOb, opposingSweep, opposingFvg]
    .filter(Boolean)
    .map((signal) => String(signal?.evidence || signal?.name || 'SMC pressure'));

  const pressureKind = opposingOb
    ? `opposing ${oppositeBias} OB/FVG`
    : `opposing ${oppositeBias} sweep (pending reversal)`;

  return {
    reason: `${side} into M15 ${pressureKind}`,
    evidence,
  };
}

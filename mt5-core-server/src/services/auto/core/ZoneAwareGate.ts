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

export function buildZoneLayerContext(
  side: ZoneTradeSide,
  layers: ZoneLayer[],
  hardBlockPct: number,
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
  const wrong = valid.filter((layer) => zoneIsWrongForSide(side, layer.result.zone));
  const favorable = valid.filter((layer) => zoneIsFavorableForSide(side, layer.result.zone));
  const hardWrong = wrong.filter((layer) => isDeepWrongZoneForSide(side, layer.result.pctFromRange, hardBlockPct));
  const majorLayers = valid.filter((layer) => layer.role === 'major' || layer.role === 'context');
  const executionLayers = valid.filter((layer) => layer.role === 'execution');
  const majorWrongLayers = majorLayers.filter((layer) => zoneIsWrongForSide(side, layer.result.zone));
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
    hardMajorWrong: hardWrong.some((layer) => layer.role === 'major' || layer.role === 'context'),
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

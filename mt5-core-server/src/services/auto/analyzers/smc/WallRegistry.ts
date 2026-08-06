import type { PriceMap, PriceWall } from './types.js';

const CORE_TFS = new Set(['M15', 'M30', 'H1', 'H4']);
const CONFIRM_TFS = new Set(['M1', 'M5']);
const CONTEXT_TFS = new Set(['D1', 'SESSION', 'SWING']);

export interface WallRegistryOptions {
  matchAtrMul: number;
  retainMissingCycles: number;
  invalidationAtrMul: number;
}

const DEFAULT_OPTIONS: WallRegistryOptions = {
  matchAtrMul: 0.65,
  retainMissingCycles: 5,
  invalidationAtrMul: 0.35,
};

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

function splitTimeframes(timeframes: string[]): {
  anchorTimeframes: string[];
  confirmationTimeframes: string[];
  contextTimeframes: string[];
} {
  return {
    anchorTimeframes: timeframes.filter((tf) => CORE_TFS.has(tf)),
    confirmationTimeframes: timeframes.filter((tf) => CONFIRM_TFS.has(tf)),
    contextTimeframes: timeframes.filter((tf) => CONTEXT_TFS.has(tf)),
  };
}

function timeframeOverlap(a: PriceWall, b: PriceWall): number {
  const aSet = new Set(a.timeframes);
  return b.timeframes.filter((tf) => aSet.has(tf)).length;
}

function sidesCompatible(a: PriceWall, b: PriceWall): boolean {
  return a.side === b.side || a.side === 'BOTH' || b.side === 'BOTH';
}

function mergeSide(previous: PriceWall, current: PriceWall): PriceWall['side'] {
  if (current.side === previous.side) return current.side;
  if (current.side === 'BOTH') return previous.side === 'BOTH' ? 'BOTH' : previous.side;
  if (previous.side === 'BOTH') return current.side;
  return 'BOTH';
}

function isTouched(wall: PriceWall, currentPrice: number, atr: number): boolean {
  const pad = Math.max(atr * 0.05, Math.abs(wall.price) * 0.00005);
  return currentPrice >= wall.priceBottom - pad && currentPrice <= wall.priceTop + pad;
}

function isInvalidated(wall: PriceWall, currentPrice: number, atr: number, options: WallRegistryOptions): boolean {
  const pad = Math.max(atr * options.invalidationAtrMul, Math.abs(wall.price) * 0.0002);
  if (wall.side === 'SUPPORT') return currentPrice < wall.priceBottom - pad;
  if (wall.side === 'RESISTANCE') return currentPrice > wall.priceTop + pad;
  return false;
}

function decorateWall(wall: PriceWall, currentPrice: number, atr: number, now: number): PriceWall {
  const layers = splitTimeframes(wall.timeframes);
  const touched = isTouched(wall, currentPrice, atr);
  return {
    ...wall,
    rawPrice: wall.rawPrice ?? wall.price,
    ...layers,
    status: touched ? 'TOUCHED' : 'ACTIVE',
    firstSeenAt: wall.firstSeenAt ?? now,
    lastSeenAt: now,
    missingCycles: 0,
    touchCount: (wall.touchCount ?? 0) + (touched ? 1 : 0),
  };
}

function mergeWall(previous: PriceWall, current: PriceWall, currentPrice: number, atr: number, now: number): PriceWall {
  const currentLayers = splitTimeframes(current.timeframes);
  const rawPrice = current.price;
  const hasCoreAnchor = currentLayers.anchorTimeframes.length > 0;
  const alpha = hasCoreAnchor ? 0.35 : 0.2;
  const stablePrice = round2(previous.price * (1 - alpha) + rawPrice * alpha);
  const side = mergeSide(previous, current);
  const touched = isTouched({ ...current, side, price: stablePrice }, currentPrice, atr);
  return {
    ...current,
    id: previous.id,
    price: stablePrice,
    rawPrice,
    priceTop: round2(Math.max(previous.priceTop, current.priceTop)),
    priceBottom: round2(Math.min(previous.priceBottom, current.priceBottom)),
    side,
    ...currentLayers,
    status: touched ? 'TOUCHED' : 'ACTIVE',
    firstSeenAt: previous.firstSeenAt ?? now,
    lastSeenAt: now,
    missingCycles: 0,
    touchCount: (previous.touchCount ?? 0) + (touched ? 1 : 0),
  };
}

function rebuildMap(base: PriceMap, walls: PriceWall[]): PriceMap {
  const sortedWalls = [...walls].sort((a, b) => b.price - a.price);
  const dividerTolerance = Math.max(base.atr * 0.05, Math.abs(base.currentPrice) * 0.00001);
  const resistanceWalls = sortedWalls
    .filter((w) => w.price >= base.currentPrice - dividerTolerance && (w.side === 'RESISTANCE' || w.side === 'BOTH'))
    .sort((a, b) => a.price - b.price);
  const supportWalls = sortedWalls
    .filter((w) => w.price <= base.currentPrice + dividerTolerance && (w.side === 'SUPPORT' || w.side === 'BOTH'))
    .sort((a, b) => b.price - a.price);

  return {
    ...base,
    walls: sortedWalls,
    resistanceWalls,
    supportWalls,
    nearestResistance: resistanceWalls[0] ?? null,
    nearestSupport: supportWalls[0] ?? null,
    strongestResistance: [...resistanceWalls].sort((a, b) => b.confluenceStars - a.confluenceStars)[0] ?? null,
    strongestSupport: [...supportWalls].sort((a, b) => b.confluenceStars - a.confluenceStars)[0] ?? null,
  };
}

export class StableWallRegistry {
  private nextId = 1;
  private walls = new Map<string, PriceWall[]>();
  private options: WallRegistryOptions;

  constructor(options: Partial<WallRegistryOptions> = {}) {
    this.options = { ...DEFAULT_OPTIONS, ...options };
  }

  stabilize(symbol: string, map: PriceMap, now = Date.now()): PriceMap {
    const key = symbol.toUpperCase();
    const previousWalls = this.walls.get(key) ?? [];
    const usedPrevious = new Set<number>();
    const stabilized: PriceWall[] = [];
    const matchDistance = Math.max(map.atr * this.options.matchAtrMul, Math.abs(map.currentPrice) * 0.0002);

    for (const raw of map.walls) {
      let bestIndex = -1;
      let bestScore = Number.POSITIVE_INFINITY;

      for (let i = 0; i < previousWalls.length; i++) {
        if (usedPrevious.has(i)) continue;
        const prev = previousWalls[i];
        if (!sidesCompatible(prev, raw)) continue;
        const dist = Math.abs(prev.price - raw.price);
        if (dist > matchDistance) continue;
        const overlap = timeframeOverlap(prev, raw);
        const hasCore = (prev.anchorTimeframes?.length ?? 0) > 0 || raw.timeframes.some((tf) => CORE_TFS.has(tf));
        if (overlap === 0 && !hasCore) continue;
        const score = dist - overlap * 0.25;
        if (score < bestScore) {
          bestScore = score;
          bestIndex = i;
        }
      }

      if (bestIndex >= 0) {
        usedPrevious.add(bestIndex);
        stabilized.push(mergeWall(previousWalls[bestIndex], raw, map.currentPrice, map.atr, now));
      } else {
        const id = `${key}-W${this.nextId++}`;
        stabilized.push(decorateWall({ ...raw, id }, map.currentPrice, map.atr, now));
      }
    }

    for (let i = 0; i < previousWalls.length; i++) {
      if (usedPrevious.has(i)) continue;
      const prev = previousWalls[i];
      const missingCycles = (prev.missingCycles ?? 0) + 1;
      if (missingCycles > this.options.retainMissingCycles) continue;
      if (isInvalidated(prev, map.currentPrice, map.atr, this.options)) continue;
      stabilized.push({
        ...prev,
        status: 'STALE',
        missingCycles,
      });
    }

    const unique = new Map<string, PriceWall>();
    for (const wall of stabilized) {
      unique.set(wall.id ?? `${key}-${wall.price}-${wall.side}`, wall);
    }
    const nextWalls = [...unique.values()];
    this.walls.set(key, nextWalls);
    return rebuildMap(map, nextWalls);
  }

  reset(symbol?: string): void {
    if (symbol) {
      this.walls.delete(symbol.toUpperCase());
      return;
    }
    this.walls.clear();
    this.nextId = 1;
  }
}

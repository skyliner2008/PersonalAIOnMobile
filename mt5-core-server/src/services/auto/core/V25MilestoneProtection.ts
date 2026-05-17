import type { JournalRow } from '../types.js';
import { safeJsonParse } from '../utils.js';
import type { Tick } from '../v25/types.js';

export type V25PathMilestone = {
  price: number;
  edge?: number;
  protectAt?: number;
  stars?: number;
  timeframes?: string[];
  wallSide?: string;
  rAtMilestone?: number;
};

function normalizeMilestone(raw: unknown): V25PathMilestone | null {
  if (!raw || typeof raw !== 'object') return null;
  const obj = raw as Record<string, unknown>;
  const price = Number(obj.price);
  const protectAt = Number(obj.protectAt ?? obj.edge ?? obj.price);
  if (!Number.isFinite(price) || !Number.isFinite(protectAt)) return null;
  return {
    price,
    edge: Number.isFinite(Number(obj.edge)) ? Number(obj.edge) : price,
    protectAt,
    stars: Number.isFinite(Number(obj.stars)) ? Number(obj.stars) : undefined,
    timeframes: Array.isArray(obj.timeframes) ? obj.timeframes.map(String) : [],
    wallSide: obj.wallSide != null ? String(obj.wallSide) : undefined,
    rAtMilestone: Number.isFinite(Number(obj.rAtMilestone)) ? Number(obj.rAtMilestone) : undefined,
  };
}

export function extractV25PathMilestone(journal: JournalRow | undefined | null): V25PathMilestone | null {
  if (!journal) return null;
  const snapshot = safeJsonParse(journal.marketSnapshot || '', null) as any;
  const fromSnapshot = normalizeMilestone(snapshot?.pathMilestone ?? snapshot?.v25?.pathMilestone);
  if (fromSnapshot) return fromSnapshot;

  const signals = safeJsonParse(journal.signalsJson || '', null) as any;
  return normalizeMilestone(signals?.pathMilestone ?? signals?.v25?.pathMilestone);
}

export function v25MilestoneReached(
  side: string,
  milestone: V25PathMilestone | null,
  currentPrice: number,
  ticks: Tick[] = [],
  sinceMs = 0,
): boolean {
  if (!milestone) return false;
  const protectAt = milestone.protectAt ?? milestone.edge ?? milestone.price;
  if (!Number.isFinite(protectAt)) return false;

  if (side === 'BUY') {
    if (currentPrice >= protectAt) return true;
    return ticks.some((t) => t.recvTs >= sinceMs && t.bid >= protectAt);
  }
  if (side === 'SELL') {
    if (currentPrice <= protectAt) return true;
    return ticks.some((t) => t.recvTs >= sinceMs && t.ask <= protectAt);
  }
  return false;
}

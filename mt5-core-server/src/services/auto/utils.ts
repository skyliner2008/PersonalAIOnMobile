export function nowIso(): string {
  return new Date().toISOString();
}

export function getLogTime(): string {
  return new Date().toLocaleString('sv-SE', { timeZone: 'Asia/Bangkok' });
}

import { atLog, atWarn, atError } from '../logger.js';

export { atLog, atWarn, atError };

export function safeJsonParse<T>(raw: string | null | undefined, fallback: T): T {
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

export function unwrapData(input: unknown): unknown {
  if (input && typeof input === 'object' && !Array.isArray(input)) {
    const bag = input as Record<string, unknown>;
    if ('data' in bag) return bag.data;
  }
  return input;
}

export function asRecord(input: unknown): Record<string, unknown> {
  return input && typeof input === 'object' && !Array.isArray(input) ? (input as Record<string, unknown>) : {};
}

export function asNumber(input: unknown, fallback = 0): number {
  return typeof input === 'number' && Number.isFinite(input)
    ? input
    : typeof input === 'string' && Number.isFinite(Number(input))
      ? Number(input)
      : fallback;
}

export function asString(input: unknown, fallback = ''): string {
  return typeof input === 'string' ? input : fallback;
}

export function round2(input: number): number {
  return Math.round(input * 100) / 100;
}

export function round1(input: number): number {
  return Math.round(input * 10) / 10;
}

export function clamp(input: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, input));
}

// 2026-05-01 — เพิ่ม XBTUSD/XETUSD (alias ของ BTCUSD/ETHUSD ที่บางโบรกใช้)
//   เดิม XBTUSD ตกไป fallback length===6 → 100000 (FX pair) ทำให้ lot คำนวณเล็ก
//   เกินไป (~0.01) ขยับ $1000 = $10 P/L แทนที่จะเป็น $1000.
const SYMBOL_DEFAULTS: Record<string, number> = {
  XAUUSD: 100.0,
  XAGUSD: 5000.0,
  BTCUSD: 1.0,
  XBTUSD: 1.0,
  ETHUSD: 1.0,
  XETUSD: 1.0,
  EURUSD: 100000.0,
  GBPUSD: 100000.0,
  USDJPY: 1000.0,
  US30: 1.0,
  NAS100: 1.0,
};

export function moneyPerPriceUnit(symbol: string, overrides: Record<string, number> = {}): number {
  const up = symbol.trim().toUpperCase();
  if (overrides[up] !== undefined) return overrides[up];
  if (SYMBOL_DEFAULTS[up] !== undefined) return SYMBOL_DEFAULTS[up];

  if (up.startsWith('XAU')) return 100.0;
  if (up.startsWith('XAG')) return 5000.0;
  // 2026-05-01 — รวม XBT (alias BTC) และ XET (alias ETH) เข้า branch crypto
  if (up.startsWith('BTC') || up.startsWith('XBT') ||
      up.startsWith('ETH') || up.startsWith('XET')) return 1.0;
  if (up.includes('JPY')) return 1000.0;
  if (up.length === 6) return 100000.0; // standard FX pairs
  return 1000.0;
}

export function estimateNotionalPct(symbol: string, lot: number, equity: number): number {
  if (equity <= 0) return 0.0;
  const notional = moneyPerPriceUnit(symbol) * lot * 0.01; // ~1% price-move notional
  return (notional / equity) * 100.0;
}

/**
 * Approximate minimum price tick per symbol. Used to snap SL/TP before
 * sending orders so the values recorded in the journal match what the
 * broker actually stores post-rounding.
 */
export function tickSize(symbol: string, overrides: Record<string, number> = {}): number {
  const up = symbol.trim().toUpperCase();
  if (overrides[up] !== undefined && overrides[up] > 0) return overrides[up];
  if (up.startsWith('XAU')) return 0.01;     // Gold: 2 decimals
  if (up.startsWith('XAG')) return 0.001;    // Silver: 3 decimals
  if (up.startsWith('BTC')) return 0.01;
  if (up.startsWith('ETH')) return 0.01;
  if (up.includes('JPY')) return 0.001;      // 3-decimal JPY pairs
  if (up === 'US30' || up === 'NAS100' || up === 'SPX500') return 0.1;
  if (up.length === 6) return 0.00001;       // 5-decimal FX pairs
  return 0.01;
}

/** Snap a price to the symbol's tick grid (nearest) and clean up floating point noise. */
export function roundToTick(price: number | null, tick: number): number | null {
  if (price === null || !Number.isFinite(price)) return price;
  if (!tick || !Number.isFinite(tick) || tick <= 0) return price;
  
  // Use a small epsilon to avoid precision issues before rounding
  const rounded = Math.round((price / tick) + 1e-10) * tick;
  
  const tickStr = tick.toString();
  const decimalMatch = tickStr.match(/\.(\d+)/);
  const precision = decimalMatch ? decimalMatch[1].length : 0;
  
  return parseFloat(rounded.toFixed(precision));
}

export function normalizeConfig<T extends Record<string, any>>(input: Partial<T> | null | undefined, base: T): T {
  return { ...base, ...(input || {}) };
}

/** Calculate Risk/Reward Ratio. Reward / Risk. */
export function calculateRRR(side: 'BUY' | 'SELL', entry: number, sl: number | null, tp: number | null): number {
  if (sl === null || tp === null) return 0;
  const risk = Math.abs(entry - sl);
  const reward = Math.abs(tp - entry);
  if (risk === 0) return 0;
  return reward / risk;
}
export function tryParseJson(raw: string, fallback: any = null): any { if (!raw) return fallback; try { const obj = JSON.parse(raw); return (obj && typeof obj === 'object') ? obj : fallback; } catch { return fallback; } }

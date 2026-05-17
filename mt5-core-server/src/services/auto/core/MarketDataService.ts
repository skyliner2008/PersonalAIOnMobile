import { callBridge, asArray } from '../../bridgeClient.js';
import { parseCandles } from '../../tracking.js';
import { 
  asNumber, 
  asString, 
  asRecord, 
  nowIso,
  unwrapData,
  getLogTime,
  atLog,
  atWarn,
  atError
} from '../utils.js';
import { triggerImmediatePush } from '../../mt5RealtimeHub.js';
import { 
  AccountSnapshot, 
  PositionRow, 
  AutoTradingConfig 
} from '../types.js';

class MarketDataService {
  private candleCache: Map<string, any[]> = new Map();
  private optionalSymbolBackoffUntil: Map<string, number> = new Map();
  private optionalSymbolResolved: Map<string, string> = new Map();

  // Short-lived snapshot caches so that cycle + manage + status calls in the same
  // ~500ms window share one bridge round-trip. This is a big win because
  // /account and /positions were the two dominant per-cycle calls.
  // 2026-04-24 — skyliner.jojo@gmail.com
  private accountCache: { value: AccountSnapshot; at: number } | null = null;
  private positionsCache: { value: PositionRow[]; at: number } | null = null;
  private accountInFlight: Promise<AccountSnapshot> | null = null;
  private positionsInFlight: Promise<PositionRow[]> | null = null;
  private readonly snapshotTtlMs = 500;

  public async fetchAccount(forceFresh: boolean = false): Promise<AccountSnapshot> {
    const now = Date.now();
    if (!forceFresh && this.accountCache && now - this.accountCache.at < this.snapshotTtlMs) {
      return this.accountCache.value;
    }
    // Dedupe concurrent fetches
    if (!forceFresh && this.accountInFlight) {
      return this.accountInFlight;
    }
    const task = (async () => {
      const result = await callBridge('GET', ['/account', '/mt5/account', '/api/mt5/account']);
      const bag = asRecord(unwrapData(result.data));
      const snap: AccountSnapshot = {
        balance: asNumber(bag.balance),
        equity: asNumber(bag.equity, asNumber(bag.balance)),
        margin: asNumber(bag.margin),
        freeMargin: asNumber(bag.free_margin ?? bag.freeMargin ?? bag.margin_free),
        todayPnL: asNumber(bag.profit_today ?? bag.today_pnl),
        openPositions: asNumber(bag.positions_total),
        currency: asString(bag.currency, 'USD'),
      };
      this.accountCache = { value: snap, at: Date.now() };
      return snap;
    })();
    this.accountInFlight = task;
    try {
      return await task;
    } finally {
      this.accountInFlight = null;
    }
  }

  public async fetchPositions(forceFresh: boolean = false): Promise<PositionRow[]> {
    const now = Date.now();
    if (!forceFresh && this.positionsCache && now - this.positionsCache.at < this.snapshotTtlMs) {
      return this.positionsCache.value;
    }
    if (!forceFresh && this.positionsInFlight) {
      return this.positionsInFlight;
    }
    const task = (async () => {
      const result = await callBridge('GET', ['/positions', '/mt5/positions', '/api/mt5/positions']);
      const rows = asArray(unwrapData(result.data)).map((row) => {
        const bag = asRecord(row);
        const rawType = bag.type;
        let side: 'BUY' | 'SELL' = 'BUY';
        if (typeof rawType === 'number') {
          side = rawType === 1 ? 'SELL' : 'BUY';
        } else {
          const typeStr = asString(rawType, 'BUY').toUpperCase();
          side = (typeStr === 'SELL' || typeStr === '1') ? 'SELL' : 'BUY';
        }
        // Broker typically returns seconds; some bridges return millis.
        // Heuristic: anything < 1e12 is seconds.
        const rawTime = asNumber(bag.time ?? bag.time_setup ?? bag.time_open ?? 0);
        const openedAtMs = rawTime > 0
          ? (rawTime < 1e12 ? rawTime * 1000 : rawTime)
          : undefined;
        return {
          ticket: asNumber(bag.ticket),
          symbol: asString(bag.symbol).toUpperCase(),
          side,
          volume: asNumber(bag.volume),
          priceOpen: asNumber(bag.price_open ?? bag.priceOpen),
          priceCurrent: asNumber(bag.price_current ?? bag.priceCurrent ?? bag.price),
          sl: asNumber(bag.sl),
          tp: asNumber(bag.tp),
          profit: asNumber(bag.profit),
          openedAtMs,
        };
      }).filter((it) => it.ticket > 0 && it.symbol);
      this.positionsCache = { value: rows, at: Date.now() };
      return rows;
    })();
    this.positionsInFlight = task;
    try {
      return await task;
    } finally {
      this.positionsInFlight = null;
    }
  }

  /**
   * Invalidate the snapshot caches — call this immediately after any order
   * placement, position close, or SL/TP modification so the next fetch reflects
   * broker state, not a 500ms-stale cache.
   */
  public invalidateSnapshots(): void {
    this.accountCache = null;
    this.positionsCache = null;
    // Notify WebSocket clients to refresh state immediately
    triggerImmediatePush();
  }

  public async fetchHistory(limit: number): Promise<Record<string, unknown>[]> {
    const result = await callBridge('GET', ['/history', '/deals', '/trades/history', '/mt5/history', '/api/mt5/history'], {
      query: { limit },
    });
    return asArray(unwrapData(result.data)).map((row) => asRecord(row));
  }

  public async fetchCandles(symbol: string, timeframe: string, count: number) {
    const result = await callBridge('GET', ['/candles', '/rates'], {
      query: { symbol, timeframe, count },
      timeoutMs: 25_000,
    });
    return parseCandles(asArray(unwrapData(result.data))).sort((a, b) => a.t - b.t);
  }

  public async loadSymbolCandles(symbol: string, timeframe: string, fullCount = 200): Promise<any[]> {
    const cacheKey = `${symbol}_${timeframe}`;
    const cached = this.candleCache.get(cacheKey) || [];

    if (cached.length === 0) {
      const fresh = await this.fetchCandles(symbol, timeframe, fullCount);
      this.candleCache.set(cacheKey, fresh);
      return fresh;
    }

    const incremental = await this.fetchCandles(symbol, timeframe, 10);
    if (incremental.length === 0) return cached;

    const candleMap = new Map<number, any>();
    for (const c of cached) { if (c && c.t) candleMap.set(c.t, c); }
    for (const c of incremental) { if (c && c.t) candleMap.set(c.t, c); }

    const merged = Array.from(candleMap.values())
      .sort((a, b) => a.t - b.t)
      .slice(-fullCount);

    this.candleCache.set(cacheKey, merged);
    return merged;
  }

  public async loadOptionalCandles(symbol: string, timeframe: string, count: number): Promise<any[]> {
    const upper = symbol.trim().toUpperCase();
    const now = Date.now();
    const blockedUntil = this.optionalSymbolBackoffUntil.get(upper) ?? 0;
    if (blockedUntil > now) return [];
    const resolved = this.optionalSymbolResolved.get(upper) ?? (await this.resolveOptionalSymbol(upper));
    if (!resolved) {
      this.optionalSymbolBackoffUntil.set(upper, now + 30 * 60_000);
      return [];
    }
    try {
      return await this.loadSymbolCandles(resolved, timeframe, count);
    } catch (err: any) {
      const message = String(err?.message || err).toLowerCase();
      const unavailable =
        message.includes('cannot select symbol') ||
        message.includes('symbol_select') ||
        message.includes('symbol not found');
      if (unavailable) {
        const backoffMs = 30 * 60_000;
        this.optionalSymbolBackoffUntil.set(upper, now + backoffMs);
        this.optionalSymbolResolved.delete(upper);
        atWarn(`[AutoEngine] ⏭️ Optional symbol ${upper} unavailable on broker. Backing off for ${Math.round(backoffMs / 60000)}m.`);
        return [];
      }
      throw err;
    }
  }

  public async resolveOptionalSymbol(alias: string): Promise<string | null> {
    const q = alias.trim().toUpperCase();
    if (!q) return null;
    try {
      const result = await callBridge('GET', ['/symbol-search', '/symbols-meta', '/symbols'], { query: { q, query: q, limit: 100 } });
      const rows = asArray<any>(unwrapData(result.data));
      if (!rows.length) return null;
      const pick = rows
        .map((row) => {
          const symbol = asString(row.symbol ?? row.name, '').toUpperCase();
          const description = asString(row.description, '').toUpperCase();
          const score = asNumber(row.score, 0);
          const exact = symbol === q ? 1000 : 0;
          const prefix = symbol.startsWith(q) ? 80 : 0;
          const contains = symbol.includes(q) ? 50 : 0;
          const desc = description.includes(q) ? 25 : 0;
          return { symbol, rank: exact + prefix + contains + desc + score };
        })
        .filter((row) => row.symbol)
        .sort((a, b) => b.rank - a.rank)[0];
      if (!pick?.symbol) return null;
      this.optionalSymbolResolved.set(q, pick.symbol);
      if (pick.symbol !== q) {
        atLog(`[AutoEngine] 🔎 Optional symbol mapped: ${q} -> ${pick.symbol}`);
      }
      return pick.symbol;
    } catch (err) {
      return null;
    }
  }

  public clearCache() {
    this.candleCache.clear();
  }
}

export const marketDataService = new MarketDataService();

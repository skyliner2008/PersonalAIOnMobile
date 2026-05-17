import { Router } from 'express';
import { z } from 'zod';
import { getDb } from '../db.js';
import { asArray, callBridge } from '../services/bridgeClient.js';
import { indicators, parseCandles } from '../services/tracking.js';
import { listMt5Clients, startMt5Client, stopMt5ByPid } from '../services/mt5Clients.js';
import { getSnapshotDelta, normalizeTradeSide } from '../services/mt5SnapshotHub.js';
import { inferAnalysis } from '../services/auto/analysis.js';
import { atLog, atWarn, tryParseJson } from '../services/auto/utils.js';

const router = Router();
const db = getDb();

// Accepts both `side` and legacy `action` (BUY/SELL/long/short, any case).
// This keeps compatibility with the PersonalAIBot Kotlin client which still
// sends `{"action":"BUY"}` on the mobile side.
export const orderSchema = z
  .object({
    symbol: z.string().min(1),
    side: z.string().optional(),
    action: z.string().optional(),
    volume: z.coerce.number().positive(),
    price: z.coerce.number().optional(),
    sl: z.coerce.number().optional(),
    tp: z.coerce.number().optional(),
    type: z.string().optional(),
    comment: z.string().optional(),
    magic: z.coerce.number().optional(),
  })
  .transform((value) => {
    const sideRaw = String(value.side || value.action || '').trim().toLowerCase();
    const side = sideRaw === 'long' ? 'buy' : sideRaw === 'short' ? 'sell' : sideRaw;
    return { ...value, side };
  })
  .refine((v) => v.side === 'buy' || v.side === 'sell', {
    message: 'side/action must be BUY or SELL',
    path: ['side'],
  });

// `ticket` is optional — at least one of { ticket, symbol } is required.
export const closeSchema = z
  .object({
    ticket: z.union([z.string(), z.coerce.number()]).optional(),
    symbol: z.string().optional(),
    volume: z.coerce.number().positive().optional(),
  })
  .transform((value) => {
    const rawTicket = value.ticket === undefined || value.ticket === null ? '' : String(value.ticket).trim();
    const symbol = value.symbol ? value.symbol.trim() : '';
    return {
      ...value,
      ticket: rawTicket,
      symbol,
    };
  })
  .refine((v) => v.ticket !== '' || (v.symbol && v.symbol.length > 0), {
    message: 'At least one of `ticket` or `symbol` is required',
    path: ['ticket'],
  });

// `modify` updates SL/TP of an existing open position without closing it.
// One of { ticket, symbol } is required, and at least one of { sl, tp } must
// be provided. Pass `sl: 0` or `tp: 0` to explicitly clear a value.
export const modifySchema = z
  .object({
    ticket: z.union([z.string(), z.coerce.number()]).optional(),
    symbol: z.string().optional(),
    sl: z.coerce.number().optional(),
    tp: z.coerce.number().optional(),
    comment: z.string().optional(),
  })
  .transform((value) => {
    const rawTicket = value.ticket === undefined || value.ticket === null ? '' : String(value.ticket).trim();
    const symbol = value.symbol ? value.symbol.trim() : '';
    return {
      ...value,
      ticket: rawTicket,
      symbol,
    };
  })
  .refine((v) => v.ticket !== '' || (v.symbol && v.symbol.length > 0), {
    message: 'At least one of `ticket` or `symbol` is required',
    path: ['ticket'],
  })
  .refine((v) => v.sl !== undefined || v.tp !== undefined, {
    message: 'At least one of `sl` or `tp` is required',
    path: ['sl'],
  });

export const bulkCloseSchema = z.object({
  side: z
    .string()
    .optional()
    .default('ALL')
    .transform((value) => value.trim().toUpperCase())
    .refine((value) => ['ALL', 'BUY', 'SELL'].includes(value), {
      message: 'side must be ALL, BUY, or SELL',
    }),
  symbol: z.string().optional().transform((value) => value?.trim().toUpperCase() || ''),
  maxPositions: z.coerce.number().int().min(1).max(100).optional().default(100),
});

export const breakEvenSchema = z.object({
  side: z
    .string()
    .optional()
    .default('ALL')
    .transform((value) => value.trim().toUpperCase())
    .refine((value) => ['ALL', 'BUY', 'SELL'].includes(value), {
      message: 'side must be ALL, BUY, or SELL',
    }),
  symbol: z.string().optional().transform((value) => value?.trim().toUpperCase() || ''),
  onlyWinning: z.coerce.boolean().optional().default(true),
  maxPositions: z.coerce.number().int().min(1).max(100).optional().default(100),
  bufferBySymbol: z.record(z.coerce.number()).optional().default({ XAU: 0.5 }),
});

const trackingSchema = z.object({
  symbols: z.array(z.string().min(1)).min(1),
  timeframes: z.array(z.string().min(1)).min(1),
  bars: z.coerce.number().int().min(50).max(2000).optional().default(300),
});

const startClientSchema = z.object({
  exePath: z.string().min(3),
});

const stopClientSchema = z.object({
  pid: z.coerce.number().int().positive().optional(),
  exePath: z.string().optional(),
});

function ok(res: any, data: unknown) {
  res.json({ success: true, data });
}

function fail(res: any, err: any) {
  const message = String(err?.message || err || 'unknown');
  // eslint-disable-next-line no-console
  console.error(`[mt5-route] ${message}`);
  const status =
    message.includes('Bridge call failed') ||
    message.includes('Unable to connect') ||
    message.includes('ECONNREFUSED')
      ? 502
      : 500;
  res.status(status).json({ success: false, error: message });
}

function saveSnapshot(snapshotType: string, payload: unknown, symbol?: string, timeframe?: string): void {
  db.prepare(
    `INSERT INTO mt5_snapshots (snapshot_type, symbol, timeframe, payload_json, created_at)
     VALUES (?, ?, ?, ?, datetime('now'))`
  ).run(snapshotType, symbol || null, timeframe || null, JSON.stringify(payload ?? null));
}

type CachedCandle = {
  t: number;
  o: number;
  h: number;
  l: number;
  c: number;
  v: number;
};

function timeframeToSeconds(raw: string): number | null {
  const normalized = String(raw || '').trim().toLowerCase();
  const map: Record<string, number> = {
    m1: 60,
    '1m': 60,
    m2: 120,
    '2m': 120,
    m3: 180,
    '3m': 180,
    m5: 300,
    '5m': 300,
    m10: 600,
    '10m': 600,
    m15: 900,
    '15m': 900,
    m30: 1800,
    '30m': 1800,
    h1: 3600,
    '1h': 3600,
    h2: 7200,
    '2h': 7200,
    h3: 10800,
    '3h': 10800,
    h4: 14400,
    '4h': 14400,
    h6: 21600,
    '6h': 21600,
    h8: 28800,
    '8h': 28800,
    h12: 43200,
    '12h': 43200,
    d1: 86400,
    '1d': 86400,
    w1: 604800,
    '1w': 604800,
    mn1: 2592000,
  };
  return map[normalized] ?? null;
}

function getCachedCandles(symbol: string, timeframe: string, limit: number): CachedCandle[] {
  const rows = db.prepare(
    `SELECT t, o, h, l, c, v
     FROM mt5_candle_cache
     WHERE symbol = ? AND timeframe = ?
     ORDER BY t DESC
     LIMIT ?`
  ).all(symbol, timeframe, limit) as CachedCandle[];
  return rows.reverse();
}

function upsertCandles(symbol: string, timeframe: string, candles: CachedCandle[]): void {
  if (candles.length === 0) return;
  const stmt = db.prepare(
    `INSERT INTO mt5_candle_cache (symbol, timeframe, t, o, h, l, c, v, source, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'broker', datetime('now'))
     ON CONFLICT(symbol, timeframe, t) DO UPDATE SET
       o = excluded.o,
       h = excluded.h,
       l = excluded.l,
       c = excluded.c,
       v = excluded.v,
       source = excluded.source,
       updated_at = datetime('now')`
  );
  const tx = db.transaction((items: CachedCandle[]) => {
    for (const candle of items) {
      stmt.run(symbol, timeframe, candle.t, candle.o, candle.h, candle.l, candle.c, candle.v);
    }
  });
  tx(candles);
}

function normalizeCachedCandles(input: unknown): CachedCandle[] {
  return parseCandles(asArray(input)).sort((a, b) => a.t - b.t);
}

function computeIncrementalFetchCount(lastCachedTime: number | null, timeframe: string, requestedCount: number): number {
  if (!lastCachedTime) return requestedCount;
  const tfSeconds = timeframeToSeconds(timeframe);
  if (!tfSeconds) return Math.min(requestedCount, 500);
  const nowSec = Math.floor(Date.now() / 1000);
  const missingBars = Math.max(0, Math.ceil((nowSec - lastCachedTime) / tfSeconds));
  const safetyBars = 3;
  return Math.max(10, Math.min(Math.max(missingBars + safetyBars, 20), Math.max(requestedCount, 300)));
}

function saveAction(actionType: string, status: string, requestPayload: unknown, responsePayload: unknown, symbol?: string, ticket?: string) {
  db.prepare(
    `INSERT INTO mt5_trade_actions (action_type, status, symbol, ticket, request_json, response_json, created_at)
     VALUES (?, ?, ?, ?, ?, ?, datetime('now'))`
  ).run(
    actionType,
    status,
    symbol || null,
    ticket || null,
    JSON.stringify(requestPayload ?? null),
    JSON.stringify(responsePayload ?? null)
  );
}

router.get('/clients', async (_req, res) => {
  try {
    const clients = await listMt5Clients();
    ok(res, { count: clients.length, rows: clients });
  } catch (err) {
    fail(res, err);
  }
});

router.post('/clients/start', async (req, res) => {
  try {
    const payload = startClientSchema.parse(req.body || {});
    const result = await startMt5Client(payload.exePath);
    saveAction('mt5_client_start', 'ok', payload, result);
    ok(res, result);
  } catch (err) {
    fail(res, err);
  }
});

router.post('/clients/stop', async (req, res) => {
  try {
    const payload = stopClientSchema.parse(req.body || {});
    let pid = payload.pid;
    if (!pid && payload.exePath) {
      const rows = await listMt5Clients();
      const match = rows.find((r) => r.exePath.toLowerCase() === payload.exePath!.toLowerCase());
      pid = match?.pids?.[0];
    }
    if (!pid) throw new Error('pid or running exePath is required');

    const result = await stopMt5ByPid(pid);
    saveAction('mt5_client_stop', 'ok', payload, result);
    ok(res, result);
  } catch (err) {
    fail(res, err);
  }
});

router.get('/account', async (_req, res) => {
  try {
    const result = await callBridge('GET', ['/account', '/mt5/account', '/api/mt5/account']);
    saveSnapshot('account', result.data);
    ok(res, result.data);
  } catch (err) {
    fail(res, err);
  }
});

router.get('/positions', async (req, res) => {
  try {
    const symbol = typeof req.query.symbol === 'string' ? req.query.symbol : undefined;
    const result = await callBridge('GET', ['/positions', '/mt5/positions', '/api/mt5/positions'], { query: { symbol } });
    const normalized = normalizeTradeSide(unwrapData(result.data));
    saveSnapshot('positions', normalized, symbol);
    ok(res, normalized);
  } catch (err) {
    fail(res, err);
  }
});

router.get('/symbols', async (_req, res) => {
  try {
    const result = await callBridge('GET', ['/symbols', '/market/symbols', '/mt5/symbols', '/api/mt5/symbols']);
    saveSnapshot('symbols', result.data);
    ok(res, result.data);
  } catch (err) {
    fail(res, err);
  }
});

// 2026-04-26 — public /symbols-meta endpoint exposes the FULL broker symbol
// list with the hierarchical `path` field (Forex\\Major, Metals\\Spot, etc).
// /symbols caps at 200 and drops `path` so the dashboard's category browser
// needs this richer endpoint.
router.get('/symbols-meta', async (req, res) => {
  try {
    const limit = Math.min(Number(req.query.limit || 5000), 10000);
    const result = await callBridge('GET', ['/symbols-meta', '/symbols'], { query: { limit } });
    ok(res, result.data);
  } catch (err) {
    fail(res, err);
  }
});

// ── Symbol Search & Validate ──────────────────────────────────────────────
// In-memory cache: broker symbol list (re-fetches every 5 min)
let _symbolCache: { data: any[]; ts: number } | null = null;
const SYMBOL_CACHE_TTL_MS = 5 * 60 * 1000;

async function fetchAllSymbolsMeta(): Promise<any[]> {
  const now = Date.now();
  if (_symbolCache && now - _symbolCache.ts < SYMBOL_CACHE_TTL_MS) {
    return _symbolCache.data;
  }
  // Prefer /symbols-meta (no tick overhead), fall back to /symbols
  try {
    const r = await callBridge('GET', ['/symbols-meta', '/symbols'], { query: { limit: 5000 } });
    const arr = Array.isArray(r.data) ? r.data : (Array.isArray((r.data as any)?.data) ? (r.data as any).data : []);
    _symbolCache = { data: arr, ts: now };
    return arr;
  } catch {
    return _symbolCache?.data ?? [];
  }
}

function scoreSymbol(sym: any, q: string): number {
  const name = String(sym.symbol || sym.name || '').toUpperCase();
  const desc = String(sym.description || '').toUpperCase();
  const path = String(sym.path || '').toUpperCase();
  if (name === q) return 100;
  if (name.startsWith(q)) return 80;
  if (name.includes(q)) return 60;
  if (desc.startsWith(q) || desc.split(/\s+/).some((w: string) => w.startsWith(q))) return 45;
  if (desc.includes(q)) return 30;
  if (path.includes(q)) return 15;
  return 0;
}

/**
 * GET /api/mt5/symbol-search?q=btcusd&limit=10&validate=true
 *
 * q        — search term (symbol name or description keyword)
 * limit    — max results (default 15)
 * validate — if true, also checks if q is an exact valid symbol
 *
 * Response:
 *   matches  — ranked list of broker symbols
 *   exact    — true if broker has a symbol whose name === q exactly
 *   validated— the exact broker symbol name if exact=true, else null
 *   suggested— best non-exact match name (useful when q is a common alias)
 */
router.get('/symbol-search', async (req, res) => {
  try {
    const q     = typeof req.query.q === 'string' ? req.query.q.trim().toUpperCase() : '';
    const limit = Math.min(Number(req.query.limit || 15), 50);
    const validateMode = req.query.validate === 'true' || req.query.validate === '1';

    if (!q) return void res.status(400).json({ success: false, error: 'Missing query param: q' });

    // Try broker's native search first (fast, uses MT5 built-in)
    let matches: any[] = [];
    let usedBridge = false;
    try {
      const r = await callBridge('GET', ['/symbol-search'], { query: { q, limit } });
      const arr = Array.isArray(r.data) ? r.data
        : (Array.isArray((r.data as any)?.data) ? (r.data as any).data : null);
      if (arr && arr.length > 0) {
        matches = arr;
        usedBridge = true;
      }
    } catch { /* fall through to local search */ }

    // Fallback: local fuzzy search on cached metadata
    if (!usedBridge) {
      const all = await fetchAllSymbolsMeta();
      const scored = all
        .map(s => ({ ...s, _score: scoreSymbol(s, q) }))
        .filter(s => s._score > 0)
        .sort((a, b) => b._score - a._score)
        .slice(0, limit);
      matches = scored.map(s => ({
        symbol:      s.symbol || s.name,
        description: s.description || '',
        path:        s.path || '',
        digits:      s.digits ?? 0,
        trade_mode:  s.trade_mode ?? 0,
        bid:         0,
        ask:         0,
        score:       s._score,
        exact:       s._score === 100,
      }));
    }

    const exact     = matches.some((m: any) => String(m.symbol || m.name || '').toUpperCase() === q && (m.exact !== false));
    const validated = exact ? matches.find((m: any) => String(m.symbol || m.name || '').toUpperCase() === q)?.symbol ?? null : null;
    const suggested = !exact && matches.length > 0 ? (matches[0].symbol || matches[0].name) : null;

    ok(res, { matches, exact, validated, suggested, query: q, source: usedBridge ? 'bridge' : 'cache' });
  } catch (err) {
    fail(res, err);
  }
});

// 2026-04-26 — batch tick endpoint for the dashboard's Market Symbols browser.
// The bridge is single-threaded so we cap to 32 symbols per call and cache
// the result for 1.5s to avoid hammering it.
type TickEntry = { symbol: string; bid: number; ask: number; last: number; spread?: number; time?: number; digits?: number };
const _tickCache = new Map<string, { t: number; tick: TickEntry }>();
const TICK_TTL_MS = 1500;

router.post('/ticks', async (req, res) => {
  try {
    const body = (req.body || {}) as { symbols?: string[] };
    const list = Array.isArray(body.symbols) ? body.symbols.map((s) => String(s).toUpperCase()).filter(Boolean) : [];
    if (list.length === 0) return res.json({ success: true, ticks: [] });
    const capped = list.slice(0, 32);
    const now = Date.now();
    const out: TickEntry[] = [];
    const need: string[] = [];
    for (const sym of capped) {
      const hit = _tickCache.get(sym);
      if (hit && now - hit.t < TICK_TTL_MS) out.push(hit.tick);
      else need.push(sym);
    }
    if (need.length > 0) {
      // Single batched call to the bridge that does select->tick->deselect
      // per symbol so we never permanently grow Market Watch.
      try {
        const r = await callBridge('POST', ['/ticks_lite'], { body: { symbols: need }, timeoutMs: 15_000 });
        const inner: any = (r.data as any)?.data ?? unwrapData(r.data) ?? [];
        const arr: any[] = Array.isArray(inner) ? inner : [];
        for (const item of arr) {
          const tick: TickEntry = {
            symbol: String(item.symbol || '').toUpperCase(),
            bid: Number(item.bid ?? 0),
            ask: Number(item.ask ?? 0),
            last: Number(item.last ?? 0),
            spread: Number(item.spread ?? 0),
            time: Number(item.time ?? Date.now()),
            digits: Number(item.digits ?? 0),
          };
          if (tick.symbol) {
            _tickCache.set(tick.symbol, { t: now, tick });
            out.push(tick);
          }
        }
      } catch {
        // Silent — return whatever we already have from cache.
      }
    }
    res.json({ success: true, ticks: out });
  } catch (err) {
    fail(res, err);
  }
});

// Force-remove symbols from Market Watch (cleanup after browsing).
// Body: { symbols: string[] }
router.post('/symbols/deselect', async (req, res) => {
  try {
    const body = (req.body || {}) as { symbols?: string[] };
    const list = Array.isArray(body.symbols) ? body.symbols.map((s) => String(s).toUpperCase()).filter(Boolean) : [];
    if (list.length === 0) return res.json({ success: true, removed: {} });
    const r = await callBridge('POST', ['/symbols/select'], { body: { symbols: list, select: false }, timeoutMs: 10_000 });
    const inner: any = (r.data as any)?.data ?? unwrapData(r.data) ?? {};
    res.json({ success: true, removed: inner });
  } catch (err) {
    fail(res, err);
  }
});

router.get('/market-watch', async (_req, res) => {
  try {
    const r = await callBridge('GET', ['/market-watch'], { timeoutMs: 5000 });
    const inner: any = (r.data as any)?.data ?? unwrapData(r.data) ?? [];
    res.json({ success: true, symbols: Array.isArray(inner) ? inner : [] });
  } catch (err) {
    fail(res, err);
  }
});

router.get('/orders', async (req, res) => {
  try {
    const symbol = typeof req.query.symbol === 'string' ? req.query.symbol : undefined;
    const result = await callBridge('GET', ['/orders', '/mt5/orders', '/api/mt5/orders'], { query: { symbol } });
    const normalized = normalizeTradeSide(unwrapData(result.data));
    saveSnapshot('orders', normalized, symbol);
    ok(res, normalized);
  } catch (err) {
    fail(res, err);
  }
});

router.get('/history', async (req, res) => {
  try {
    const symbol = typeof req.query.symbol === 'string' ? req.query.symbol : undefined;
    const limit = Number(req.query.limit || 200);
    const result = await callBridge(
      'GET',
      ['/history', '/deals', '/trades/history', '/mt5/history', '/api/mt5/history'],
      { query: { symbol, limit } }
    );
    const normalized = normalizeTradeSide(unwrapData(result.data));
    saveSnapshot('history', normalized, symbol);
    ok(res, normalized);
  } catch (err) {
    fail(res, err);
  }
});

// Small helper: the bridge may wrap payloads in `{ data: ... }`; expose only
// the inner value to clients so UI code always sees a flat array.
function unwrapData(input: unknown): unknown {
  if (input && typeof input === 'object' && !Array.isArray(input)) {
    const bag = input as Record<string, unknown>;
    if ('data' in bag) return bag.data;
  }
  return input;
}

type PositionLike = Record<string, unknown>;

function normalizeSide(input: unknown): string {
  const raw = String(input || '').trim().toUpperCase();
  if (raw === 'BUY' || raw === 'SELL') return raw;
  const type = Number(input);
  if (Number.isFinite(type)) return type % 2 === 0 ? 'BUY' : 'SELL';
  return '';
}

function positionTicket(position: PositionLike): string {
  return String(position.ticket ?? position.position ?? position.id ?? '').trim();
}

function positionSymbol(position: PositionLike): string {
  return String(position.symbol ?? '').trim().toUpperCase();
}

function positionSide(position: PositionLike): string {
  return normalizeSide(position.side ?? position.type);
}

function numericField(position: PositionLike, keys: string[]): number | null {
  for (const key of keys) {
    const value = Number(position[key]);
    if (Number.isFinite(value) && value !== 0) return value;
  }
  return null;
}

function roundPrice(price: number, digits: number | null): number {
  const d = digits !== null && Number.isFinite(digits) ? Math.max(0, Math.min(8, Math.floor(digits))) : 2;
  return Number(price.toFixed(d));
}

function breakEvenBuffer(symbol: string, bufferBySymbol: Record<string, number>): number {
  const upper = symbol.toUpperCase();
  for (const [prefix, value] of Object.entries(bufferBySymbol)) {
    if (upper.includes(prefix.toUpperCase())) return Number(value) || 0;
  }
  return 0;
}

router.get('/snapshot', async (req, res) => {
  try {
    const limit = Number(req.query.limit || 200);
    const since = typeof req.query.since === 'string' ? req.query.since : '';
    const waitMs = Number(req.query.waitMs || 0);
    const delta = await getSnapshotDelta({ since, limit, waitMs });

    if (delta.changed && delta.snapshot) {
      if (delta.changedSections.includes('account')) saveSnapshot('account', delta.snapshot.account);
      if (delta.changedSections.includes('symbols')) saveSnapshot('symbols', delta.snapshot.symbols);
      if (delta.changedSections.includes('positions')) saveSnapshot('positions', delta.snapshot.positions);
      if (delta.changedSections.includes('orders')) saveSnapshot('orders', delta.snapshot.orders);
      if (delta.changedSections.includes('history')) saveSnapshot('history', delta.snapshot.history);
    }

    ok(res, delta);
  } catch (err) {
    fail(res, err);
  }
});

router.get('/candles', async (req, res) => {
  try {
    const symbol = String(req.query.symbol || '');
    const timeframe = String(req.query.timeframe || '');
    const count = Number(req.query.count || 300);
    if (!symbol || !timeframe) {
      throw new Error('symbol and timeframe are required');
    }

    const cachedBefore = getCachedCandles(symbol, timeframe, count);
    const lastCachedTime = cachedBefore.length > 0 ? cachedBefore[cachedBefore.length - 1].t : null;
    const fetchCount = computeIncrementalFetchCount(lastCachedTime, timeframe, count);

    const result = await callBridge('GET', ['/candles', '/rates'], {
      query: { symbol, timeframe, count: fetchCount },
      timeoutMs: 25000,
    });

    const latestFromBroker = normalizeCachedCandles(result.data);
    upsertCandles(symbol, timeframe, latestFromBroker);

    const finalCandles = getCachedCandles(symbol, timeframe, count);
    const latestFinalTime = finalCandles.length > 0 ? finalCandles[finalCandles.length - 1].t : null;
    const missingBars = lastCachedTime && latestFinalTime && timeframeToSeconds(timeframe)
      ? Math.max(0, Math.floor((latestFinalTime - lastCachedTime) / (timeframeToSeconds(timeframe) || 1)))
      : finalCandles.length
    // eslint-disable-next-line no-console
    console.log(
      `[mt5-candles] incremental refresh ${symbol}/${timeframe}: ` +
      `missingBars=${missingBars} fetchDelta=${fetchCount} dbBars=${finalCandles.length}`
    );

    saveSnapshot('candles', finalCandles, symbol, timeframe);
    ok(res, finalCandles);
  } catch (err) {
    fail(res, err);
  }
});

router.post('/order', async (req, res) => {
  atLog({ event: 'order_request', body: req.body });
  let parsed: z.infer<typeof orderSchema> | null = null;
  try {
    parsed = orderSchema.parse(req.body);
    // Forward with both `side` (normalized lowercase) and `action` (uppercase)
    // so either style of bridge is satisfied.
    const forwardBody = {
      ...parsed,
      side: parsed.side,
      action: parsed.side.toUpperCase(),
    };
    const result = await callBridge('POST', ['/order', '/orders/open', '/mt5/order', '/mt5/orders/open'], {
      body: forwardBody,
      timeoutMs: 25000,
      priority: 'critical',
    });
    saveAction('order', 'ok', forwardBody, result.data, parsed.symbol);
    ok(res, result.data);
  } catch (err) {
    if (parsed) {
      saveAction('order', 'error', parsed, { error: String((err as any)?.message || err) }, parsed.symbol);
    } else {
      saveAction('order', 'invalid', req.body, { error: String((err as any)?.message || err) });
    }
    fail(res, err);
  }
});

router.post('/close', async (req, res) => {
  atLog({ event: 'close_request', body: req.body });
  let parsed: z.infer<typeof closeSchema> | null = null;
  try {
    parsed = closeSchema.parse(req.body);
    const forwardBody = {
      ticket: parsed.ticket,
      symbol: parsed.symbol,
      volume: parsed.volume,
    };
    const result = await callBridge('POST', ['/close', '/positions/close', '/mt5/close', '/mt5/positions/close'], {
      body: forwardBody,
      timeoutMs: 25000,
      priority: 'critical',
    });
    saveAction('close', 'ok', forwardBody, result.data, parsed.symbol, parsed.ticket);
    ok(res, result.data);
  } catch (err) {
    if (parsed) {
      saveAction('close', 'error', parsed, { error: String((err as any)?.message || err) }, parsed.symbol, parsed.ticket);
    } else {
      saveAction('close', 'invalid', req.body, { error: String((err as any)?.message || err) });
    }
    fail(res, err);
  }
});

router.post('/modify', async (req, res) => {
  atLog({ event: 'modify_request', body: req.body });
  let parsed: z.infer<typeof modifySchema> | null = null;
  try {
    parsed = modifySchema.parse(req.body);
    const forwardBody: Record<string, unknown> = {
      ticket: parsed.ticket,
      symbol: parsed.symbol,
      comment: parsed.comment,
    };
    if (parsed.sl !== undefined) forwardBody.sl = parsed.sl;
    if (parsed.tp !== undefined) forwardBody.tp = parsed.tp;
    const result = await callBridge(
      'POST',
      ['/modify', '/position/modify', '/positions/modify', '/mt5/modify'],
      { body: forwardBody, timeoutMs: 25000, priority: 'critical' }
    );
    saveAction('modify', 'ok', forwardBody, result.data, parsed.symbol, parsed.ticket);
    ok(res, result.data);
  } catch (err) {
    if (parsed) {
      saveAction('modify', 'error', parsed, { error: String((err as any)?.message || err) }, parsed.symbol, parsed.ticket);
    } else {
      saveAction('modify', 'invalid', req.body, { error: String((err as any)?.message || err) });
    }
    fail(res, err);
  }
});

router.post('/bulk-close', async (req, res) => {
  const startedAt = Date.now();
  let parsed: z.infer<typeof bulkCloseSchema> | null = null;
  try {
    parsed = bulkCloseSchema.parse(req.body || {});
    const positionsResult = await callBridge('GET', ['/positions', '/mt5/positions', '/api/mt5/positions'], {
      query: { symbol: parsed.symbol || undefined },
      timeoutMs: 15000,
      priority: 'high',
    });
    const positions = asArray<PositionLike>(normalizeTradeSide(unwrapData(positionsResult.data)));
    const targets = positions
      .filter((position) => {
        const side = positionSide(position);
        if (parsed!.side !== 'ALL' && side !== parsed!.side) return false;
        if (parsed!.symbol && positionSymbol(position) !== parsed!.symbol) return false;
        return Boolean(positionTicket(position) || positionSymbol(position));
      })
      .slice(0, parsed.maxPositions);

    const results: Array<Record<string, unknown>> = [];
    for (const position of targets) {
      const ticket = positionTicket(position);
      const symbol = positionSymbol(position);
      const forwardBody = { ticket, symbol };
      try {
        const result = await callBridge('POST', ['/close', '/positions/close', '/mt5/close', '/mt5/positions/close'], {
          body: forwardBody,
          timeoutMs: 25000,
          priority: 'critical',
        });
        saveAction('bulk_close_item', 'ok', forwardBody, result.data, symbol, ticket);
        results.push({ ticket, symbol, side: positionSide(position), success: true, data: result.data });
      } catch (err: any) {
        const error = String(err?.message || err);
        saveAction('bulk_close_item', 'error', forwardBody, { error }, symbol, ticket);
        results.push({ ticket, symbol, side: positionSide(position), success: false, error });
      }
    }

    const summary = {
      side: parsed.side,
      symbol: parsed.symbol || null,
      requested: targets.length,
      ok: results.filter((item) => item.success).length,
      failed: results.filter((item) => !item.success).length,
      elapsedMs: Date.now() - startedAt,
      results,
    };
    saveAction('bulk_close', summary.failed ? 'partial' : 'ok', parsed, summary, parsed.symbol || undefined);
    ok(res, summary);
  } catch (err) {
    saveAction('bulk_close', 'invalid', req.body, { error: String((err as any)?.message || err) }, parsed?.symbol || undefined);
    fail(res, err);
  }
});

router.post('/break-even', async (req, res) => {
  const startedAt = Date.now();
  let parsed: z.infer<typeof breakEvenSchema> | null = null;
  try {
    parsed = breakEvenSchema.parse(req.body || {});
    const positionsResult = await callBridge('GET', ['/positions', '/mt5/positions', '/api/mt5/positions'], {
      query: { symbol: parsed.symbol || undefined },
      timeoutMs: 15000,
      priority: 'high',
    });
    const positions = asArray<PositionLike>(normalizeTradeSide(unwrapData(positionsResult.data)));
    const targets = positions
      .filter((position) => {
        const side = positionSide(position);
        if (parsed!.side !== 'ALL' && side !== parsed!.side) return false;
        if (parsed!.symbol && positionSymbol(position) !== parsed!.symbol) return false;
        const open = numericField(position, ['priceOpen', 'price_open', 'price_opened', 'openPrice']);
        const current = numericField(position, ['priceCurrent', 'price_current', 'price', 'currentPrice']);
        if (!open || !current) return false;
        if (!parsed!.onlyWinning) return true;
        return side === 'BUY' ? current > open : side === 'SELL' ? current < open : false;
      })
      .slice(0, parsed.maxPositions);

    const results: Array<Record<string, unknown>> = [];
    for (const position of targets) {
      const ticket = positionTicket(position);
      const symbol = positionSymbol(position);
      const side = positionSide(position);
      const open = numericField(position, ['priceOpen', 'price_open', 'price_opened', 'openPrice']);
      const digitsRaw = Number(position.digits);
      const digits = Number.isFinite(digitsRaw) ? digitsRaw : null;
      if (!open || (side !== 'BUY' && side !== 'SELL')) {
        results.push({ ticket, symbol, side, success: false, error: 'missing open price or side' });
        continue;
      }

      const buffer = breakEvenBuffer(symbol, parsed.bufferBySymbol);
      const sl = roundPrice(side === 'BUY' ? open + buffer : open - buffer, digits);
      const forwardBody = { ticket, symbol, sl, comment: 'JARVIS_BULK_BE' };
      try {
        const result = await callBridge('POST', ['/modify', '/position/modify', '/positions/modify', '/mt5/modify'], {
          body: forwardBody,
          timeoutMs: 25000,
          priority: 'critical',
        });
        saveAction('break_even_item', 'ok', forwardBody, result.data, symbol, ticket);
        results.push({ ticket, symbol, side, sl, success: true, data: result.data });
      } catch (err: any) {
        const error = String(err?.message || err);
        saveAction('break_even_item', 'error', forwardBody, { error }, symbol, ticket);
        results.push({ ticket, symbol, side, sl, success: false, error });
      }
    }

    const summary = {
      side: parsed.side,
      symbol: parsed.symbol || null,
      onlyWinning: parsed.onlyWinning,
      requested: targets.length,
      ok: results.filter((item) => item.success).length,
      failed: results.filter((item) => !item.success).length,
      elapsedMs: Date.now() - startedAt,
      results,
    };
    saveAction('break_even', summary.failed ? 'partial' : 'ok', parsed, summary, parsed.symbol || undefined);
    ok(res, summary);
  } catch (err) {
    saveAction('break_even', 'invalid', req.body, { error: String((err as any)?.message || err) }, parsed?.symbol || undefined);
    fail(res, err);
  }
});

router.post('/tracking/run-once', async (req, res) => {
  try {
    const payload = trackingSchema.parse(req.body);
    const rows: Array<{
      symbol: string;
      timeframe: string;
      bars: number;
      lastCandleTime: number | null;
      indicators: Record<string, number | null>;
    }> = [];

    for (const symbol of payload.symbols) {
      for (const timeframe of payload.timeframes) {
        const result = await callBridge('GET', ['/candles', '/rates'], {
          query: { symbol, timeframe, count: payload.bars },
          timeoutMs: 25000,
        });

        const candles = parseCandles(asArray(result.data));
        const calc = indicators(candles);
        const lastCandleTime = candles.length > 0 ? candles[candles.length - 1].t : null;

        db.prepare(
          `INSERT INTO mt5_tracking_state (symbol, timeframe, last_candle_time, indicators_json, updated_at)
           VALUES (?, ?, ?, ?, datetime('now'))
           ON CONFLICT(symbol, timeframe) DO UPDATE SET
             last_candle_time = excluded.last_candle_time,
             indicators_json = excluded.indicators_json,
             updated_at = datetime('now')`
        ).run(symbol, timeframe, lastCandleTime, JSON.stringify(calc));

        const trackPayload = {
          symbol,
          timeframe,
          bars: candles.length,
          lastCandleTime,
          indicators: calc,
          trackedAt: new Date().toISOString(),
        };
        saveSnapshot('tracking', trackPayload, symbol, timeframe);
        rows.push(trackPayload);
      }
    }

    ok(res, { count: rows.length, rows });
  } catch (err) {
    fail(res, err);
  }
});

router.get('/tracking/state', (_req, res) => {
  try {
    const rows = db
      .prepare(
        `SELECT symbol, timeframe, last_candle_time, indicators_json, updated_at
         FROM mt5_tracking_state
         ORDER BY updated_at DESC
         LIMIT 500`
      )
      .all() as Array<{
      symbol: string;
      timeframe: string;
      last_candle_time: number | null;
      indicators_json: string;
      updated_at: string;
    }>;

    const data = rows.map((row) => ({
      symbol: row.symbol,
      timeframe: row.timeframe,
      lastCandleTime: row.last_candle_time,
      indicators: tryParseJson(row.indicators_json, {}),
      updatedAt: row.updated_at,
    }));
    ok(res, { count: data.length, rows: data });
  } catch (err) {
    fail(res, err);
  }
});

router.get('/trade-actions', (_req, res) => {
  try {
    const rows = db
      .prepare(
        `SELECT id, action_type, status, symbol, ticket, request_json, response_json, created_at
         FROM mt5_trade_actions
         ORDER BY id DESC
         LIMIT 500`
      )
      .all();
    ok(res, { count: rows.length, rows });
  } catch (err) {
    fail(res, err);
  }
});

// ── /analyze — broker candles + full server-side technical analysis ─────────
// Returns regime, bias, confluence, fitness, ATR, signals, and indicator
// snapshot for the requested symbol/timeframe. No raw candle dump.
router.get('/analyze', async (req, res) => {
  try {
    const symbol    = typeof req.query.symbol    === 'string' ? req.query.symbol.toUpperCase()      : 'XAUUSD';
    const timeframe = typeof req.query.timeframe === 'string' ? req.query.timeframe.toUpperCase()   : 'M15';
    const count     = Math.min(Number(req.query.count || 300), 1500);

    // Fetch candles (incremental cache)
    const cachedBefore = getCachedCandles(symbol, timeframe, count);
    const lastCachedTime = cachedBefore.length > 0 ? cachedBefore[cachedBefore.length - 1].t : null;
    const fetchCount = computeIncrementalFetchCount(lastCachedTime, timeframe, count);

    try {
      const r = await callBridge('GET', ['/candles', '/rates'], {
        query: { symbol, timeframe, count: fetchCount },
        timeoutMs: 30_000,
      });
      const latestFromBroker = normalizeCachedCandles(r.data);
      upsertCandles(symbol, timeframe, latestFromBroker);
    } catch (e) {
      // If bridge fails, just proceed with whatever we have in cache
    }

    const finalCandles = getCachedCandles(symbol, timeframe, count);
    const candles = finalCandles;

    if (candles.length < 10) {
      return void ok(res, { symbol, timeframe, error: 'Insufficient candle data', barsReceived: candles.length });
    }

    // Server-side analysis
    const analysis = inferAnalysis(candles);
    const indSnap  = indicators(candles);

    // Price context
    const closes     = candles.map((b: any) => Number(b.c ?? b.close ?? 0)).filter(Boolean);
    let current      = closes.at(-1) ?? 0;

    // Fetch live tick to ensure price consistency across all timeframes
    try {
      const rTick = await callBridge('POST', ['/ticks_lite'], { body: { symbols: [symbol] }, timeoutMs: 3000 });
      const innerTick: any = (rTick.data as any)?.data ?? unwrapData(rTick.data) ?? [];
      const tickArr = Array.isArray(innerTick) ? innerTick : [];
      if (tickArr.length > 0 && tickArr[0].bid) {
        current = Number(tickArr[0].bid);
      }
    } catch {
      // Fallback to candle close if tick fetch fails
    }

    let smcData: any = null;
    try {
      const rSmc = await callBridge('GET', ['/smc'], {
        query: { symbol, timeframe, count: 300 },
        timeoutMs: 5000,
      });
      smcData = (rSmc.data as any)?.data ?? unwrapData(rSmc.data) ?? null;
    } catch {
      // SMC fetch failed
    }

    const prev       = closes.at(-2) ?? current;
    const changePct  = prev !== 0 ? ((current - prev) / prev) * 100 : 0;
    const last20c    = closes.slice(-20);
    const high20     = Math.max(...last20c);
    const low20      = Math.min(...last20c);
    const range20Pct = low20 !== 0 ? ((high20 - low20) / low20) * 100 : 0;

    // Infer digit precision from current price
    const digits = current > 1000 ? 2 : current > 10 ? 3 : current > 1 ? 4 : 5;

    // Compact recent bars (last 20)
    const recentBars = candles.slice(-20).map((b: any) => ({
      t: new Date(Number(b.t ?? b.time ?? 0) * 1000).toISOString().slice(0, 16),
      o: Number(b.o ?? b.open  ?? 0).toFixed(digits),
      h: Number(b.h ?? b.high  ?? 0).toFixed(digits),
      l: Number(b.l ?? b.low   ?? 0).toFixed(digits),
      c: Number(b.c ?? b.close ?? 0).toFixed(digits),
      v: Number(b.v ?? b.tick_volume ?? b.volume ?? 0).toFixed(0),
    }));

    const fmt = (v: number | null | undefined, d: number) =>
      v !== null && v !== undefined ? +v.toFixed(d) : null;

    ok(res, {
      symbol,
      timeframe,
      barsAnalyzed: candles.length,
      regime:    analysis.regime,
      bias:      analysis.bias,
      confluence: analysis.confluence,
      fitness:   analysis.fitness,
      strategy:  analysis.strategy,
      rationale: analysis.rationale,
      signals:   analysis.signals,
      indicators: {
        rsi14:    fmt(indSnap.rsi14,    2),
        sma20:    fmt(indSnap.sma20,    digits),
        sma50:    fmt(indSnap.sma50,    digits),
        ema20:    fmt(indSnap.ema20,    digits),
        macdHist: fmt(indSnap.macdHist ?? null, digits),
        atr14:    fmt(indSnap.atr14,    digits),
        digits,
      },
      price: {
        current:    +current.toFixed(digits),
        changePct:  +changePct.toFixed(4),
        high20:     +high20.toFixed(digits),
        low20:      +low20.toFixed(digits),
        range20Pct: +range20Pct.toFixed(3),
      },
      smc: smcData,
      recentBars,
    });
  } catch (err) {
    fail(res, err);
  }
});

export default router;

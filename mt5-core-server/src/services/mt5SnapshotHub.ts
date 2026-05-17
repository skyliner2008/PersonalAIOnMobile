import { createHash } from 'node:crypto';
import { callBridge } from './bridgeClient.js';

const SECTION_KEYS = ['account', 'symbols', 'positions', 'orders', 'history'] as const;
type SectionKey = (typeof SECTION_KEYS)[number];

const SECTION_INTERVAL_MS: Record<SectionKey, number> = {
  account: 1000,
  positions: 1000,
  orders: 1000,
  history: 3000,
  symbols: 15000,
};

type Mt5SnapshotPayload = {
  account: unknown;
  symbols: unknown;
  positions: unknown;
  orders: unknown;
  history: unknown;
  syncedAt: number;
};

type SectionFetchedAt = Record<SectionKey, number>;
type SectionHashes = Record<SectionKey, string>;

type CachedSnapshot = Mt5SnapshotPayload & {
  revision: string;
  sectionHashes: SectionHashes;
  sectionFetchedAt: SectionFetchedAt;
  previousRevision: string;
  changedFromPrevious: SectionKey[];
};

const cacheByLimit = new Map<number, CachedSnapshot>();
const inflightByLimit = new Map<number, Promise<CachedSnapshot>>();

export type SnapshotDelta = {
  changed: boolean;
  revision: string;
  changedSections: SectionKey[];
  syncedAt: number;
  snapshot?: Partial<Mt5SnapshotPayload>;
};

export async function getSnapshotDelta(options: {
  since?: string;
  limit: number;
  waitMs?: number;
}): Promise<SnapshotDelta> {
  const since = String(options.since || '').trim();
  const limit = clampLimit(options.limit);
  const waitMs = clampWait(options.waitMs ?? 0);
  const deadline = Date.now() + waitMs;

  while (true) {
    const current = await getOrRefreshSnapshot(limit);
    if (!since || current.revision !== since) {
      const changedSections = resolveChangedSections(since, current);
      return {
        changed: true,
        revision: current.revision,
        changedSections,
        syncedAt: current.syncedAt,
        snapshot: buildPartialSnapshot(current, changedSections),
      };
    }

    if (Date.now() >= deadline) {
      return {
        changed: false,
        revision: current.revision,
        changedSections: [],
        syncedAt: current.syncedAt,
      };
    }

    await sleep(Math.min(500, deadline - Date.now()));
  }
}

function clampLimit(limit: number): number {
  if (!Number.isFinite(limit)) return 200;
  return Math.max(20, Math.min(1000, Math.floor(limit)));
}

function clampWait(waitMs: number): number {
  if (!Number.isFinite(waitMs)) return 0;
  return Math.max(0, Math.min(15000, Math.floor(waitMs)));
}

async function getOrRefreshSnapshot(limit: number): Promise<CachedSnapshot> {
  const inflight = inflightByLimit.get(limit);
  if (inflight) return inflight;

  const promise = refreshSnapshot(limit)
    .then((next) => {
      inflightByLimit.delete(limit);
      cacheByLimit.set(limit, next);
      return next;
    })
    .catch((err) => {
      inflightByLimit.delete(limit);
      throw err;
    });

  inflightByLimit.set(limit, promise);
  return promise;
}

async function refreshSnapshot(limit: number): Promise<CachedSnapshot> {
  const now = Date.now();
  const prev = cacheByLimit.get(limit);
  const base = prev ?? emptySnapshot(now);
  const next: CachedSnapshot = {
    ...base,
    sectionHashes: { ...base.sectionHashes },
    sectionFetchedAt: { ...base.sectionFetchedAt },
    changedFromPrevious: [],
    previousRevision: base.revision,
  };

  const forceAll = !prev;
  const fetchSections = SECTION_KEYS.filter((key) => forceAll || now - next.sectionFetchedAt[key] >= SECTION_INTERVAL_MS[key]);

  if (fetchSections.length > 0) {
    const fetched = await fetchSectionsData(fetchSections, limit);
    for (const key of fetchSections) {
      const value = fetched[key];
      next[key] = value;
      next.sectionHashes[key] = hashValue(value);
      next.sectionFetchedAt[key] = now;
    }
  }

  const revisionSeed = `${next.sectionHashes.account}|${next.sectionHashes.positions}|${next.sectionHashes.orders}|${next.sectionHashes.history}`;
  next.revision = createHash('sha256').update(revisionSeed).digest('hex').slice(0, 16);
  next.syncedAt = now;

  if (prev && prev.revision !== next.revision) {
    next.changedFromPrevious = SECTION_KEYS.filter((k) => prev.sectionHashes[k] !== next.sectionHashes[k]);
  } else if (!prev) {
    next.changedFromPrevious = [...SECTION_KEYS];
  } else {
    next.changedFromPrevious = [];
  }

  return next;
}

async function fetchSectionsData(
  sections: SectionKey[],
  limit: number
): Promise<Partial<Record<SectionKey, unknown>>> {
  const tasks = sections.map(async (section) => {
    const data = await fetchSection(section, limit);
    return [section, data] as const;
  });
  const rows = await Promise.all(tasks);
  const out: Partial<Record<SectionKey, unknown>> = {};
  for (const [k, v] of rows) out[k] = v;
  return out;
}

async function fetchSection(section: SectionKey, limit: number): Promise<unknown> {
  switch (section) {
    case 'account':
      return callBridge('GET', ['/account', '/mt5/account', '/api/mt5/account'], { priority: 'low' }).then((r) => unwrapData(r.data));
    case 'symbols':
      return callBridge('GET', ['/symbols', '/market/symbols', '/mt5/symbols', '/api/mt5/symbols'], { priority: 'low' }).then((r) => unwrapData(r.data));
    case 'positions':
      return callBridge('GET', ['/positions', '/mt5/positions', '/api/mt5/positions'], { priority: 'low' }).then((r) => normalizeTradeRows(unwrapData(r.data)));
    case 'orders':
      return callBridge('GET', ['/orders', '/mt5/orders', '/api/mt5/orders'], { priority: 'low' }).then((r) => normalizeTradeRows(unwrapData(r.data)));
    case 'history':
      return callBridge('GET', ['/history', '/deals', '/trades/history', '/mt5/history', '/api/mt5/history'], {
        query: { limit },
        priority: 'low',
      }).then((r) => normalizeTradeRows(unwrapData(r.data)));
    default:
      return null;
  }
}

// MT5 returns position.type / deal.type as integer codes. The mobile client
// expects a human-readable side ("BUY"/"SELL"), so enrich each row in place
// without removing the original `type` field.
export function normalizeTradeSide(input: unknown): unknown {
  return normalizeTradeRows(input);
}

function normalizeTradeRows(input: unknown): unknown {
  if (!Array.isArray(input)) return input;
  return input.map((row) => {
    if (!row || typeof row !== 'object') return row;
    const bag = row as Record<string, unknown>;
    const typeRaw = bag.type;
    const sideExisting = typeof bag.side === 'string' ? bag.side.trim() : '';
    if (sideExisting && /^(buy|sell)$/i.test(sideExisting)) return row;

    let side: string | null = null;
    if (typeof typeRaw === 'number') {
      // 0=BUY, 1=SELL for positions. 2/3/4/5 are limit/stop orders — treat as BUY/SELL by parity.
      side = typeRaw % 2 === 0 ? 'BUY' : 'SELL';
    } else if (typeof typeRaw === 'string') {
      const t = typeRaw.trim().toLowerCase();
      if (t.includes('buy')) side = 'BUY';
      else if (t.includes('sell')) side = 'SELL';
      else if (t === '0') side = 'BUY';
      else if (t === '1') side = 'SELL';
    }
    if (!side) return row;
    return { ...bag, side };
  });
}

function unwrapData(input: unknown): unknown {
  if (input && typeof input === 'object' && !Array.isArray(input)) {
    const bag = input as Record<string, unknown>;
    if ('data' in bag) return bag.data;
  }
  return input;
}

function hashValue(value: unknown): string {
  return createHash('sha256').update(stableStringify(value)).digest('hex').slice(0, 16);
}

function resolveChangedSections(since: string, current: CachedSnapshot): SectionKey[] {
  if (!since) return [...SECTION_KEYS];
  if (since === current.previousRevision && current.changedFromPrevious.length > 0) {
    return current.changedFromPrevious;
  }
  return [...SECTION_KEYS];
}

function buildPartialSnapshot(snapshot: CachedSnapshot, changedSections: SectionKey[]): Partial<Mt5SnapshotPayload> {
  const out: Partial<Mt5SnapshotPayload> = { syncedAt: snapshot.syncedAt };
  for (const key of changedSections) {
    out[key] = snapshot[key];
  }
  return out;
}

function emptySnapshot(now: number): CachedSnapshot {
  const emptyHash = hashValue(null);
  const sectionHashes: SectionHashes = {
    account: emptyHash,
    symbols: emptyHash,
    positions: emptyHash,
    orders: emptyHash,
    history: emptyHash,
  };
  const sectionFetchedAt: SectionFetchedAt = {
    account: 0,
    symbols: 0,
    positions: 0,
    orders: 0,
    history: 0,
  };

  return {
    account: null,
    symbols: [],
    positions: [],
    orders: [],
    history: [],
    syncedAt: now,
    revision: '',
    sectionHashes,
    sectionFetchedAt,
    previousRevision: '',
    changedFromPrevious: [...SECTION_KEYS],
  };
}

function stableStringify(value: unknown): string {
  return JSON.stringify(sortDeep(value));
}

function sortDeep(input: unknown): unknown {
  if (Array.isArray(input)) return input.map(sortDeep);
  if (input && typeof input === 'object') {
    const out: Record<string, unknown> = {};
    const keys = Object.keys(input as Record<string, unknown>).sort();
    for (const key of keys) out[key] = sortDeep((input as Record<string, unknown>)[key]);
    return out;
  }
  return input;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, Math.max(0, ms)));
}

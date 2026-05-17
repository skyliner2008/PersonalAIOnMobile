import { config } from '../config.js';
import { atWarn } from './auto/utils.js';
import { getTraceId } from './logger.js';
import { bridgeCallDurationSeconds } from './metrics.js';

type Method = 'GET' | 'POST';
export type BridgePriority = 'critical' | 'high' | 'normal' | 'low';
type BridgeCallOptions = {
  query?: Record<string, string | number | undefined>;
  body?: unknown;
  timeoutMs?: number;
  priority?: BridgePriority;
};

type QueueJob<T> = {
  id: number;
  priority: BridgePriority;
  enqueuedAt: number;
  task: () => Promise<T>;
  resolve: (value: T) => void;
  reject: (reason?: unknown) => void;
};

// ── Serialization ──────────────────────────────────────────────────────────
// The Python MT5 bridge is single-threaded (HTTPServer + MetaTrader5 module
// is not reentrant-safe). When multiple agent tool calls fire in parallel
// (e.g. MarketScanner loops 8–12 symbols), concurrent fetches stack up on
// the socket and can timeout or interleave inside MT5 itself.
//
// We protect the bridge with a promise-chain mutex so every call is queued
// and processed strictly FIFO. Each call also carries an upper bound so one
// slow request can't starve the queue forever.
const QUEUE_MAX_WAIT_MS = 60_000;
const PRIORITY_WEIGHT: Record<BridgePriority, number> = {
  critical: 0,
  high: 1,
  normal: 2,
  low: 3,
};
let queueSeq = 0;
let bridgeRunning = false;
const bridgeQueue: Array<QueueJob<unknown>> = [];

function enqueue<T>(task: () => Promise<T>, priority: BridgePriority = 'normal'): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    bridgeQueue.push({
      id: ++queueSeq,
      priority,
      enqueuedAt: Date.now(),
      task,
      resolve: resolve as (value: unknown) => void,
      reject,
    });
    drainQueue();
  });
}

function drainQueue(): void {
  if (bridgeRunning) return;
  const job = nextJob();
  if (!job) return;

  bridgeRunning = true;
  void (async () => {
    try {
      const waited = Date.now() - job.enqueuedAt;
      if (waited > QUEUE_MAX_WAIT_MS) {
        throw new Error(`Bridge queue wait exceeded ${QUEUE_MAX_WAIT_MS}ms (waited ${waited}ms)`);
      }
      job.resolve(await job.task());
    } catch (err) {
      job.reject(err);
    } finally {
      bridgeRunning = false;
      drainQueue();
    }
  })();
}

function nextJob(): QueueJob<unknown> | null {
  if (!bridgeQueue.length) return null;
  let bestIndex = 0;
  for (let i = 1; i < bridgeQueue.length; i++) {
    const current = bridgeQueue[i];
    const best = bridgeQueue[bestIndex];
    const currentWeight = PRIORITY_WEIGHT[current.priority];
    const bestWeight = PRIORITY_WEIGHT[best.priority];
    if (currentWeight < bestWeight || (currentWeight === bestWeight && current.id < best.id)) {
      bestIndex = i;
    }
  }
  return bridgeQueue.splice(bestIndex, 1)[0] ?? null;
}

export async function callBridge(
  method: Method,
  paths: string[],
  options: BridgeCallOptions = {}
): Promise<{ path: string; status: number; data: unknown }> {
  return enqueue(() => callBridgeInner(method, paths, options), options.priority ?? 'normal');
}

async function callBridgeInner(
  method: Method,
  paths: string[],
  options: BridgeCallOptions
): Promise<{ path: string; status: number; data: unknown }> {
  const headers: Record<string, string> = { 
    'Content-Type': 'application/json',
    'X-Trace-Id': getTraceId(),
  };
  if (config.mt5BridgeToken) {
    headers.Authorization = `Bearer ${config.mt5BridgeToken}`;
    headers['X-API-Key'] = config.mt5BridgeToken;
  }

  let lastError = 'unknown';
  for (const p of paths) {
    const query = options.query ? toQuery(options.query) : '';
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? 15000);
    const timer = bridgeCallDurationSeconds.startTimer({ path: p, method });
    try {
      const res = await fetch(`${config.mt5BridgeUrl}${p}${query}`, {
        method,
        headers,
        body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
        signal: controller.signal,
      });
      timer({ status: res.status });
      clearTimeout(timeout);
      const raw = await res.text();
      const data = raw ? safeJson(raw) : null;
      if (!res.ok) {
        lastError = `HTTP ${res.status} on ${p}`;
        const errPayload = typeof data === 'object' && data ? (data as any).error || JSON.stringify(data) : raw;
        // Only log warning if this was the last path attempted
        if (p === paths[paths.length - 1]) {
            atWarn(`[bridge] ${method} ${config.mt5BridgeUrl}${p} -> ${res.status} | error=${errPayload}`);
        }
        continue;
      }
      return { path: p, status: res.status, data };
    } catch (err: any) {
      clearTimeout(timeout);
      lastError = `${p}: ${String(err?.message || err)}`;
      // eslint-disable-next-line no-console
      atWarn(`[bridge] ${method} ${config.mt5BridgeUrl}${p} failed: ${String(err?.message || err)}`);
    }
  }
  throw new Error(`Bridge call failed (${method}) base=${config.mt5BridgeUrl} ${lastError}`);
}

function toQuery(query: Record<string, string | number | undefined>): string {
  const sp = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    sp.set(key, String(value));
  }
  const s = sp.toString();
  return s ? `?${s}` : '';
}

function safeJson(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    return { raw };
  }
}

export function getBridgeQueueStats(): {
  running: boolean;
  queued: number;
  byPriority: Record<BridgePriority, number>;
} {
  const byPriority: Record<BridgePriority, number> = {
    critical: 0,
    high: 0,
    normal: 0,
    low: 0,
  };
  for (const job of bridgeQueue) byPriority[job.priority]++;
  return {
    running: bridgeRunning,
    queued: bridgeQueue.length,
    byPriority,
  };
}

export function asArray<T = unknown>(input: unknown): T[] {
  if (Array.isArray(input)) return input as T[];
  if (input && typeof input === 'object') {
    const bag = input as Record<string, unknown>;
    for (const key of ['data', 'items', 'rows', 'result', 'positions', 'orders', 'symbols', 'history', 'candles', 'bars']) {
      if (Array.isArray(bag[key])) return bag[key] as T[];
    }
  }
  return [];
}

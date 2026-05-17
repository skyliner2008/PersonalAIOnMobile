import { randomUUID } from 'node:crypto';
import type { IncomingMessage } from 'node:http';
import { WebSocketServer, WebSocket } from 'ws';
import { getSnapshotDelta } from './mt5SnapshotHub.js';
import { resolveClientToken } from './tokenAuth.js';

type ClientState = {
  id: string;
  ws: WebSocket;
  tokenId: number;
  tokenPrefix: string;
  lastRevision: string;
  lastSeenMs: number;
  lastSentMs: number; // เพิ่มเพื่อจำเวลาที่ส่งข้อมูลล่าสุด
  busy: boolean;
};

type Mt5WsHub = {
  wss: WebSocketServer;
  handleUpgradeRequest: (req: IncomingMessage) => { ok: boolean; reason?: string; client?: ReturnType<typeof resolveClientToken> };
};

const clients = new Map<string, ClientState>();
let tickTimer: NodeJS.Timeout | null = null;

const POLL_MS = 5000; // เพิ่มเป็น 5 วินาทีเพื่อความเสถียรของแอปมือถือ
const HISTORY_LIMIT = 200;

export function createMt5RealtimeHub(): Mt5WsHub {
  const wss = new WebSocketServer({ noServer: true });

  wss.on('connection', (ws: WebSocket, _request: IncomingMessage, auth: unknown) => {
    const token = auth as ReturnType<typeof resolveClientToken>;
    if (!token) {
      ws.close(1008, 'unauthorized');
      return;
    }

    const id = randomUUID();
    const client: ClientState = {
      id,
      ws,
      tokenId: token.id,
      tokenPrefix: token.prefix,
      lastRevision: '',
      lastSeenMs: Date.now(),
      lastSentMs: Date.now(),
      busy: false,
    };
    clients.set(id, client);

    // eslint-disable-next-line no-console
    console.log(`[mt5-ws] client connected id=${id} prefix=${token.prefix} online=${clients.size}`);
    safeSend(ws, {
      type: 'hello',
      connectionId: id,
      online: true,
      serverTime: Date.now(),
    });

    void pushDeltaNow(client, true);
    ensureTicking();

    ws.on('message', (raw: WebSocket.RawData) => {
      client.lastSeenMs = Date.now();
      const txt = String(raw || '').trim();
      if (!txt) return;
      try {
        const msg = JSON.parse(txt) as Record<string, unknown>;
        const type = String(msg.type || '').toLowerCase();
        if (type === 'ping') {
          safeSend(ws, { type: 'pong', serverTime: Date.now() });
          return;
        }
        if (type === 'force_snapshot') {
          void pushDeltaNow(client, true);
        }
      } catch {
        // ignore malformed frame
      }
    });

    ws.on('close', () => {
      clients.delete(id);
      // eslint-disable-next-line no-console
      console.log(`[mt5-ws] client disconnected id=${id} online=${clients.size}`);
      if (clients.size === 0) stopTicking();
    });

    ws.on('error', (err: Error) => {
      // eslint-disable-next-line no-console
      console.warn(`[mt5-ws] client error id=${id}: ${String((err as any)?.message || err)}`);
    });
  });

  return {
    wss,
    handleUpgradeRequest: (req: IncomingMessage) => {
      const token = extractWsToken(req);
      if (!token) return { ok: false, reason: 'token required' };
      const client = resolveClientToken(token);
      if (!client) return { ok: false, reason: 'token invalid' };
      return { ok: true, client };
    },
  };
}

export function broadcast(payload: unknown): void {
  for (const client of clients.values()) {
    safeSend(client.ws, payload);
  }
}

/**
 * Manually trigger an immediate delta push to all connected clients.
 * Useful after trade actions (open/close/modify) to reflect state changes ASAP.
 */
export function triggerImmediatePush(): void {
  for (const client of clients.values()) {
    if (!client.busy) {
      void pushDeltaNow(client, true);
    }
  }
}

function ensureTicking(): void {
  if (tickTimer) return;
  tickTimer = setInterval(() => {
    for (const client of clients.values()) {
      if (client.busy) continue;
      void pushDeltaNow(client, false);
    }
  }, POLL_MS);
}

function stopTicking(): void {
  if (!tickTimer) return;
  clearInterval(tickTimer);
  tickTimer = null;
}

async function pushDeltaNow(client: ClientState, forceSnapshot: boolean): Promise<void> {
  if (client.ws.readyState !== WebSocket.OPEN) return;
  client.busy = true;
  try {
    const delta = await getSnapshotDelta({
      since: forceSnapshot ? '' : client.lastRevision,
      limit: HISTORY_LIMIT,
      waitMs: 0,
    });

    if (!delta.changed) {
      // หากไม่มีการเปลี่ยนแปลงเกิน 5 วินาที ให้ส่ง Snapshot Delta เปล่าที่มี syncedAt เพื่อรีเซ็ตเวลา Sync ในแอป
      const idleMs = Date.now() - client.lastSentMs;
      if (idleMs > 5000) {
        safeSend(client.ws, {
          type: 'snapshot_delta',
          revision: client.lastRevision,
          changed: true,
          changedSections: [],
          syncedAt: Date.now(),
          snapshot: { syncedAt: Date.now() },
          serverTime: Date.now(),
        });
        client.lastSentMs = Date.now();
      }
      return;
    }
    
    client.lastRevision = delta.revision;
    client.lastSentMs = Date.now(); // อัปเดตเวลาที่ส่งข้อมูลจริง
    safeSend(client.ws, {
      type: 'snapshot_delta',
      revision: delta.revision,
      changed: true,
      changedSections: delta.changedSections,
      syncedAt: delta.syncedAt,
      snapshot: delta.snapshot || {},
      serverTime: Date.now(),
    });
  } catch (err: any) {
    safeSend(client.ws, {
      type: 'error',
      message: String(err?.message || err),
      serverTime: Date.now(),
    });
  } finally {
    client.busy = false;
  }
}

function extractWsToken(req: IncomingMessage): string {
  const auth = String(req.headers.authorization || '').trim();
  if (auth.toLowerCase().startsWith('bearer ')) {
    return auth.slice(7).trim();
  }
  const url = req.url || '';
  try {
    const parsed = new URL(url, 'http://localhost');
    const fromQuery = parsed.searchParams.get('token');
    if (fromQuery) return fromQuery.trim();
  } catch {
    // noop
  }
  const x = String(req.headers['x-client-token'] || '').trim();
  return x;
}

function safeSend(ws: WebSocket, payload: unknown): void {
  if (ws.readyState !== WebSocket.OPEN) return;
  ws.send(JSON.stringify(payload));
}

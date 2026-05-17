import { Router } from 'express';
import { config } from '../config.js';
import { register } from '../services/metrics.js';

const router = Router();

async function probeBridge() {
  const bridgeHealthUrl = `${config.mt5BridgeUrl}/health`;
  let bridgeReachable = false;
  let bridgeMt5Ready = false;
  let bridgeMessage: string | null = null;
  let bridgeError = '';
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 3000);
    const response = await fetch(bridgeHealthUrl, { method: 'GET', signal: controller.signal });
    clearTimeout(timeout);
    const raw = await response.text();
    const payload = safeJson(raw);
    const data = payload?.data && typeof payload.data === 'object' ? (payload.data as Record<string, unknown>) : null;
    bridgeMt5Ready = Boolean(data?.mt5Ready);
    bridgeMessage = data?.message ? String(data.message) : null;
    bridgeReachable = response.ok;
    if (!response.ok) bridgeError = payload?.error ? String(payload.error) : `HTTP ${response.status}`;
  } catch (err: any) {
    bridgeError = String(err?.message || err);
  }
  return { bridgeHealthUrl, bridgeReachable, bridgeMt5Ready, bridgeMessage, bridgeError };
}

// Liveness — ตอบ 200 เสมอถ้า process ยังรันอยู่ ไม่เช็ค dependency
// ใช้สำหรับ container orchestrator (Docker/K8s) ตัดสินใจ restart process
router.get('/live', (_req, res) => {
  res.json({
    status: 'ok',
    service: 'mt5-core-server',
    time: new Date().toISOString(),
    uptimeSec: Math.round(process.uptime()),
  });
});

// Readiness — ตอบ 200 ถ้าเชื่อม Python bridge ได้ + MT5 ready
// 503 ถ้ายังไม่พร้อมรับ trade request (แต่ process ยัง alive)
router.get('/ready', async (_req, res) => {
  const probe = await probeBridge();
  const ready = probe.bridgeReachable && probe.bridgeMt5Ready;
  res.status(ready ? 200 : 503).json({
    status: ready ? 'ready' : 'not_ready',
    service: 'mt5-core-server',
    time: new Date().toISOString(),
    bridgeUrl: config.mt5BridgeUrl,
    ...probe,
    uptimeSec: Math.round(process.uptime()),
  });
});

// Prometheus metrics
router.get('/metrics', async (_req, res) => {
  res.set('Content-Type', register.contentType);
  res.end(await register.metrics());
});

// Legacy /health — คงไว้เพื่อ backward compat (client เดิมอาจ call อยู่)
router.get('/', async (_req, res) => {
  const probe = await probeBridge();
  res.json({
    status: 'ok',
    service: 'mt5-core-server',
    time: new Date().toISOString(),
    bridgeUrl: config.mt5BridgeUrl,
    ...probe,
    bridgeError: probe.bridgeReachable ? null : probe.bridgeError || 'unreachable',
    uptimeSec: Math.round(process.uptime()),
  });
});

function safeJson(raw: string): Record<string, unknown> | null {
  try {
    const obj = JSON.parse(raw);
    if (obj && typeof obj === 'object') return obj as Record<string, unknown>;
    return null;
  } catch {
    return null;
  }
}

export default router;

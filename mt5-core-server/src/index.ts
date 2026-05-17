import express from 'express';
import cors from 'cors';
import rateLimit from 'express-rate-limit';
import { createServer } from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import pinoHttp from 'pino-http';
import { logger, traceStorage } from './services/logger.js';
import { config } from './config.js';
import { atLog, atWarn, atError } from './services/auto/utils.js';
import { initDb } from './db.js';
import healthRoutes from './routes/health.js';
import mt5Routes from './routes/mt5.js';
import authRoutes from './routes/auth.js';
import autoTradingRoutes from './routes/autoTrading.js';
import v25Routes from './routes/v25.js';
import vertexProxyRoutes from './routes/vertexProxy.js';
import { requireClientToken } from './services/tokenAuth.js';
import { ensureBridgeReady } from './services/bridgeSupervisor.js';
import { createMt5RealtimeHub } from './services/mt5RealtimeHub.js';
import { bootstrapV25Shadow, shutdownV25Shadow } from './services/auto/v25/index.js';

// -----------------------------------------------------------------------------
// CORS allowlist — ถ้า config.corsOrigins ว่าง (ไม่ได้ set CORS_ORIGIN env)
// จะอนุญาตเฉพาะ loopback (localhost/127.0.0.1) ทุก port เพื่อ dev บน local
// -----------------------------------------------------------------------------
function isLoopbackOrigin(origin: string): boolean {
  try {
    const url = new URL(origin);
    const host = url.hostname.toLowerCase();
    return host === 'localhost' || host === '127.0.0.1' || host === '::1';
  } catch {
    return false;
  }
}

function buildCorsOptions(): cors.CorsOptions {
  return {
    origin: (origin, callback) => {
      // ไม่มี Origin header = same-origin / server-to-server / health probe → allow
      if (!origin) return callback(null, true);
      if (config.corsOrigins.length === 0) {
        // Default policy: อนุญาตเฉพาะ loopback
        if (isLoopbackOrigin(origin)) return callback(null, true);
        // eslint-disable-next-line no-console
        atWarn(`[cors] blocked non-loopback origin (CORS_ORIGIN ว่าง): ${origin}`);
        return callback(new Error(`CORS: origin not allowed (${origin})`));
      }
      if (config.corsOrigins.includes(origin) || config.corsOrigins.includes('*')) {
        return callback(null, true);
      }
      // eslint-disable-next-line no-console
      atWarn(`[cors] blocked origin not in allowlist: ${origin}`);
      return callback(new Error(`CORS: origin not allowed (${origin})`));
    },
    credentials: true,
  };
}

// -----------------------------------------------------------------------------
// Rate limiters — ใช้ IP + token เป็น key เพื่อให้ per-client เหมาะสมแม้ NAT
// -----------------------------------------------------------------------------
function tokenAwareKey(req: express.Request): string {
  const tokenHeader = String(req.headers['x-client-token'] || req.headers['authorization'] || '');
  const token = tokenHeader.replace(/^Bearer\s+/i, '').trim();
  const tokenFingerprint = token ? token.slice(0, 12) : 'anon';
  const ip = (req.ip || req.socket.remoteAddress || 'unknown').toString();
  return `${ip}:${tokenFingerprint}`;
}

// General limiter สำหรับทุก endpoint (กัน abuse เบื้องต้น)
const generalLimiter = rateLimit({
  windowMs: 60_000,
  limit: 300, // 300 req/นาที/client
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  keyGenerator: tokenAwareKey,
  message: { success: false, error: 'rate_limit_exceeded' },
});

// Strict limiter สำหรับ mutation endpoints (trade actions)
// ป้องกันกรณี token รั่วแล้วยิงคำสั่ง trade รัว ๆ
const mutationLimiter = rateLimit({
  windowMs: 60_000,
  limit: 30, // 30 mutation req/นาที/client
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  keyGenerator: tokenAwareKey,
  message: { success: false, error: 'mutation_rate_limit_exceeded' },
});

// Gate: ใช้ mutationLimiter เฉพาะ method ที่เปลี่ยน state (POST/PATCH/DELETE)
const mutationLimiterGate: express.RequestHandler = (req, res, next) => {
  if (req.method === 'GET' || req.method === 'HEAD' || req.method === 'OPTIONS') return next();
  return mutationLimiter(req, res, next);
};

/**
 * Middleware to extract or generate Trace ID and wrap the request in AsyncLocalStorage
 */
const traceIdMiddleware: express.RequestHandler = (req, res, next) => {
  const traceId = (req.headers['x-trace-id'] || req.headers['x-request-id'] || randomUUID()).toString();
  res.setHeader('x-trace-id', traceId);
  traceStorage.run({ traceId }, () => next());
};

async function bootstrap(): Promise<void> {
  await ensureBridgeReady();
  initDb();

  const app = express();
  // `trust proxy` 1 hop — required for express-rate-limit เมื่อรันหลัง reverse proxy
  app.set('trust proxy', 1);
  app.use(traceIdMiddleware);
  app.use(pinoHttp({
    logger,
    customLogLevel: (req, res, err) => {
      if (res.statusCode >= 500 || err) return 'error';
      if (res.statusCode >= 400) return 'warn';
      return 'info';
    },
    autoLogging: config.env !== 'development',
    genReqId: (req) => req.headers['x-trace-id'] || randomUUID(),
    quietReqLogger: true // We use traceStorage for our own atLog calls
  }));
  app.use(cors(buildCorsOptions()));
  app.use(express.json({ limit: '5mb' }));
  app.use(generalLimiter);

  const __filename = fileURLToPath(import.meta.url);
  const __dirname = path.dirname(__filename);
  app.use(express.static(path.join(__dirname, '../public')));

  // Health routes — ไม่ require token + ไม่ rate-limit หนัก (orchestrator ถาม probe ถี่ได้)
  app.use('/health', healthRoutes);

  // Protected mutation-aware routes
  app.use('/api/auth', authRoutes);
  app.use('/api/mt5/auto', requireClientToken, mutationLimiterGate, autoTradingRoutes);
  app.use('/api/mt5', requireClientToken, mutationLimiterGate, mt5Routes);
  // V25 Phase A — read-only diagnostics (token-protected; no mutation rate-limit needed)
  app.use('/api/v25', requireClientToken, v25Routes);
  // Vertex AI Proxy — token-protected; lets mobile app use ADC via server
  app.use('/api/vertex', requireClientToken, vertexProxyRoutes);

  app.use((err: any, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
    const message = String(err?.message || err || 'unknown');
    // CORS error → 403 (origin rejected) ไม่ใช่ 500
    if (message.startsWith('CORS:')) {
      res.status(403).json({ success: false, error: message });
      return;
    }
    res.status(500).json({ success: false, error: message });
  });

  const server = createServer(app);
  const mt5WsHub = createMt5RealtimeHub();

  // V25 Phase A — Real-Time Wall Engine
  // Boot order: env-based first (V25_SHADOW=true), then fallback to persisted UI config.
  try {
    const envStarted = bootstrapV25Shadow();
    if (!envStarted) {
      // ENV didn't enable it — check if user turned it ON via UI (adaptive.v25.enabled)
      const { autoTradingService } = await import('./services/autoTradingService.js');
      const { bootstrapV25ShadowFromConfig } = await import('./services/auto/v25/index.js');
      const cfg = autoTradingService.snapshot().config;
      const v25cfg = (cfg as any).adaptive?.v25 || {};
      if (v25cfg.enabled) {
        atLog('[V25] env flag absent — booting from UI config (adaptive.v25.enabled=true)');
        bootstrapV25ShadowFromConfig(v25cfg, cfg.watchlist);
      }
    }
  } catch (err) {
    atError(`[mt5-core-server] V25 shadow bootstrap failed: ${err}`);
  }


  // --- Graceful Shutdown Handler ---
  const shutdown = async (sig: string) => {
    atWarn(`[mt5-core-server] received ${sig}, stopping services...`);
    try {
      shutdownV25Shadow();
    } catch (err) {
      atError(`[mt5-core-server] error stopping V25: ${err}`);
    }
    try {
      const { autoTradingService } = await import('./services/autoTradingService.js');
      await autoTradingService.stop();
    } catch (err) {
      atError(`[mt5-core-server] error during shutdown: ${err}`);
    }
    process.exit(0);
  };

  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));

  server.on('upgrade', (req, socket, head) => {
    const pathname = (() => {
      try {
        return new URL(req.url || '', 'http://localhost').pathname;
      } catch {
        return req.url || '';
      }
    })();
    if (pathname !== '/ws/mt5') {
      socket.destroy();
      return;
    }

    const auth = mt5WsHub.handleUpgradeRequest(req);
    if (!auth.ok || !auth.client) {
      const reason = auth.reason || 'unauthorized';
      socket.write('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n');
      socket.destroy();
      // eslint-disable-next-line no-console
      atWarn(`[mt5-ws] upgrade denied: ${reason}`);
      return;
    }

    mt5WsHub.wss.handleUpgrade(req, socket, head, (ws) => {
      mt5WsHub.wss.emit('connection', ws, req, auth.client);
    });
  });

  server.listen(config.port, () => {
    // eslint-disable-next-line no-console
    atLog(`[mt5-core-server] running on http://localhost:${config.port}`);
    // eslint-disable-next-line no-console
    atLog(`[mt5-core-server] ws ready at ws://localhost:${config.port}/ws/mt5`);
    // eslint-disable-next-line no-console
    atLog(
      `[mt5-core-server] CORS mode: ${
        config.corsOrigins.length ? `allowlist(${config.corsOrigins.length})` : 'loopback-only'
      }`,
    );
  });
}

bootstrap().catch((err: any) => {
  // eslint-disable-next-line no-console
  atError(`[mt5-core-server] startup failed: ${String(err?.message || err)}`);
  process.exit(1);
});

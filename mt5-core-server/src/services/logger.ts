import pino from 'pino';
import { AsyncLocalStorage } from 'node:async_hooks';

/**
 * AsyncLocalStorage for propagating Trace IDs across the request lifecycle
 * without prop-drilling.
 */
export const traceStorage = new AsyncLocalStorage<{ traceId: string }>();

const env = process.env.NODE_ENV || 'development';
const isDev = env === 'development';

/**
 * Base pino logger configuration.
 * In production, this emits JSON. In development, it can be piped to pino-pretty.
 */
export const logger = pino({
  level: process.env.LOG_LEVEL || 'info',
  base: isDev ? undefined : {
    service: 'mt5-core-server',
    env: env,
  },
  transport: isDev ? {
    target: 'pino-pretty',
    options: {
      colorize: true,
      ignore: 'pid,hostname,env,service,level,trace_id,req,res,responseTime,reqId',
      messageFormat: '{msg}',
      translateTime: 'SYS:yyyy-mm-dd HH:MM:ss',
      singleLine: true,
    }
  } : undefined,
  timestamp: () => `,"time":"${new Date().toLocaleString('sv-SE', { timeZone: 'Asia/Bangkok' }).replace(' ', 'T')}"`,
});

/**
 * Get the current trace ID from storage, or return 'no-trace'
 */
export function getTraceId(): string {
  const id = traceStorage.getStore()?.traceId;
  return id || '....';
}

/**
 * Re-implementing the smart logging helpers using pino.
 * These maintain backward compatibility with the existing codebase
 * while adding structured data and trace ID propagation.
 */

export function atLog(msg: any, ...args: any[]) {
  const traceId = getTraceId();
  if (typeof msg === 'string') {
    logger.info({ trace_id: traceId, args: args.length ? args : undefined }, msg);
  } else {
    logger.info({ trace_id: traceId, data: msg, args: args.length ? args : undefined }, 'structured_log');
  }
}

export function atWarn(msg: any, ...args: any[]) {
  const traceId = getTraceId();
  if (typeof msg === 'string') {
    logger.warn({ trace_id: traceId, args: args.length ? args : undefined }, msg);
  } else {
    logger.warn({ trace_id: traceId, data: msg, args: args.length ? args : undefined }, 'structured_warn');
  }
}

export function atError(msg: any, ...args: any[]) {
  const traceId = getTraceId();
  
  // 2026-04-30 — Fix: properly serialize Error objects in args so Pino shows them.
  // Standard JSON.stringify/Pino serialization often misses .message and .stack.
  const serializedArgs = args.map(arg => {
    if (arg instanceof Error) {
      return { 
        name: arg.name, 
        message: arg.message, 
        stack: arg.stack,
        ...(arg as any) // capture any custom properties (e.g. .status for 429)
      };
    }
    return arg;
  });

  if (msg instanceof Error) {
    logger.error({ trace_id: traceId, err: msg, args: serializedArgs.length ? serializedArgs : undefined }, msg.message);
  } else if (typeof msg === 'string') {
    logger.error({ trace_id: traceId, args: serializedArgs.length ? serializedArgs : undefined }, msg);
  } else {
    logger.error({ trace_id: traceId, data: msg, args: serializedArgs.length ? serializedArgs : undefined }, 'structured_error');
  }
}

import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock dependencies
vi.mock('../config.js', () => ({
  config: {
    mt5BridgeUrl: 'http://localhost:8001',
    mt5BridgeToken: 'test-token'
  }
}));

vi.mock('./logger.js', () => ({
  getTraceId: () => 'test-trace-id'
}));

vi.mock('./metrics.js', () => ({
  bridgeCallDurationSeconds: {
    startTimer: vi.fn().mockReturnValue(vi.fn())
  }
}));

vi.mock('./auto/utils.js', () => ({
  atWarn: vi.fn(),
  atLog: vi.fn(),
  atError: vi.fn(),
}));

// Mock fetch globally
global.fetch = vi.fn();

import { callBridge } from './bridgeClient.js';

describe('bridgeClient Mutex', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should serialize concurrent bridge calls (FIFO)', async () => {
    let activeCalls = 0;
    let maxConcurrent = 0;
    const executionOrder: number[] = [];

    (global.fetch as any).mockImplementation(async () => {
      activeCalls++;
      maxConcurrent = Math.max(maxConcurrent, activeCalls);
      
      // Simulate network delay
      await new Promise(resolve => setTimeout(resolve, 50));
      
      activeCalls--;
      return {
        ok: true,
        status: 200,
        text: async () => JSON.stringify({ success: true })
      };
    });

    // Fire 5 calls at once
    const p1 = callBridge('GET', ['/p1']).then(() => executionOrder.push(1));
    const p2 = callBridge('GET', ['/p2']).then(() => executionOrder.push(2));
    const p3 = callBridge('GET', ['/p3']).then(() => executionOrder.push(3));
    const p4 = callBridge('GET', ['/p4']).then(() => executionOrder.push(4));
    const p5 = callBridge('GET', ['/p5']).then(() => executionOrder.push(5));

    await Promise.all([p1, p2, p3, p4, p5]);

    // Critical: maxConcurrent should be 1 because of the mutex
    expect(maxConcurrent).toBe(1);
    // Should maintain FIFO order
    expect(executionOrder).toEqual([1, 2, 3, 4, 5]);
    expect(global.fetch).toHaveBeenCalledTimes(5);
  });

  it('should not break the chain if a call fails', async () => {
    (global.fetch as any)
      .mockResolvedValueOnce({ ok: false, status: 500, text: async () => 'error' }) // p1 fails
      .mockResolvedValue({ ok: true, status: 200, text: async () => JSON.stringify({ ok: true }) }); // p2 succeeds

    const p1 = callBridge('GET', ['/fail']).catch(err => err.message);
    const p2 = callBridge('GET', ['/success']);

    const [err, res] = await Promise.all([p1, p2]);

    expect(err).toContain('Bridge call failed');
    expect(res.status).toBe(200);
    expect(global.fetch).toHaveBeenCalledTimes(2);
  });

  it('should run higher-priority queued calls before lower-priority reads', async () => {
    let releaseFirst!: () => void;
    const firstStarted = new Promise<void>((resolve) => {
      (global.fetch as any).mockImplementation(async (url: string) => {
        if (String(url).includes('/slow')) {
          resolve();
          await new Promise<void>((release) => {
            releaseFirst = release;
          });
        }
        return {
          ok: true,
          status: 200,
          text: async () => JSON.stringify({ success: true })
        };
      });
    });

    const slow = callBridge('GET', ['/slow'], { priority: 'low' });
    await firstStarted;

    const low = callBridge('GET', ['/low'], { priority: 'low' });
    const critical = callBridge('POST', ['/critical'], { priority: 'critical', body: { ticket: 1 } });
    const normal = callBridge('GET', ['/normal']);

    releaseFirst();
    await Promise.all([slow, low, critical, normal]);

    const requestedPaths = (global.fetch as any).mock.calls.map(([url]: [string]) => new URL(url).pathname);
    expect(requestedPaths).toEqual(['/slow', '/critical', '/normal', '/low']);
  });
});

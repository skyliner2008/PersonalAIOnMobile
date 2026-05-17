import { describe, it, expect, vi } from 'vitest';

// Mock DB and other dependencies used by mt5.ts
vi.mock('../db.js', () => ({
  getDb: () => ({
    prepare: vi.fn().mockReturnThis(),
    all: vi.fn(),
    run: vi.fn(),
    transaction: vi.fn(),
  })
}));

vi.mock('../services/bridgeClient.js', () => ({
  asArray: vi.fn(),
  callBridge: vi.fn(),
}));

vi.mock('../services/tracking.js', () => ({
  indicators: vi.fn(),
  parseCandles: vi.fn(),
}));

vi.mock('../services/mt5Clients.js', () => ({
  listMt5Clients: vi.fn(),
  startMt5Client: vi.fn(),
  stopMt5ByPid: vi.fn(),
}));

vi.mock('../services/mt5SnapshotHub.js', () => ({
  getSnapshotDelta: vi.fn(),
  normalizeTradeSide: vi.fn(),
}));

vi.mock('../services/auto/analysis.js', () => ({
  inferAnalysis: vi.fn(),
}));

import { orderSchema, closeSchema, modifySchema, bulkCloseSchema, breakEvenSchema } from './mt5.js';

describe('MT5 Schemas', () => {
  describe('orderSchema', () => {
    it('should parse and transform BUY order', () => {
      const input = { symbol: 'XAUUSD', action: 'BUY', volume: 0.1 };
      const parsed = orderSchema.parse(input);
      expect(parsed.side).toBe('buy');
      expect(parsed.volume).toBe(0.1);
    });

    it('should handle legacy "long" as buy', () => {
      const input = { symbol: 'XAUUSD', action: 'long', volume: 0.1 };
      const parsed = orderSchema.parse(input);
      expect(parsed.side).toBe('buy');
    });

    it('should handle legacy "short" as sell', () => {
      const input = { symbol: 'XAUUSD', side: 'SHORT', volume: 0.5 };
      const parsed = orderSchema.parse(input);
      expect(parsed.side).toBe('sell');
    });

    it('should throw error for invalid side', () => {
      const input = { symbol: 'XAUUSD', action: 'INVALID', volume: 0.1 };
      expect(() => orderSchema.parse(input)).toThrow();
    });

    it('should coerce string volume to number', () => {
      const input = { symbol: 'XAUUSD', side: 'BUY', volume: '0.2' };
      const parsed = orderSchema.parse(input);
      expect(parsed.volume).toBe(0.2);
    });
  });

  describe('closeSchema', () => {
    it('should require at least ticket or symbol', () => {
      expect(() => closeSchema.parse({ volume: 0.1 })).toThrow();
      expect(closeSchema.parse({ ticket: 123456 }).ticket).toBe('123456');
      expect(closeSchema.parse({ symbol: 'XAUUSD' }).symbol).toBe('XAUUSD');
    });
  });

  describe('modifySchema', () => {
    it('should require sl or tp', () => {
      const input = { ticket: 12345, comment: 'test' };
      expect(() => modifySchema.parse(input)).toThrow();
      
      const valid = { ticket: 12345, sl: 1900 };
      expect(modifySchema.parse(valid).sl).toBe(1900);
    });
  });

  describe('bulkCloseSchema', () => {
    it('should default to ALL and normalize side/symbol', () => {
      const parsed = bulkCloseSchema.parse({ side: ' buy ', symbol: 'xauusd' });
      expect(parsed.side).toBe('BUY');
      expect(parsed.symbol).toBe('XAUUSD');
      expect(parsed.maxPositions).toBe(100);
    });

    it('should reject invalid side', () => {
      expect(() => bulkCloseSchema.parse({ side: 'LONG' })).toThrow();
    });
  });

  describe('breakEvenSchema', () => {
    it('should default to winning-only break-even with XAU buffer', () => {
      const parsed = breakEvenSchema.parse({});
      expect(parsed.side).toBe('ALL');
      expect(parsed.onlyWinning).toBe(true);
      expect(parsed.bufferBySymbol.XAU).toBe(0.5);
    });

    it('should cap max positions at 100', () => {
      expect(() => breakEvenSchema.parse({ maxPositions: 101 })).toThrow();
    });
  });
});

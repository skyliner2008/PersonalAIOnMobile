import { describe, it, expect, vi } from 'vitest';
import { gateTrade } from './risk.js';
import type { AutoTradingConfig, AccountSnapshot, AnalysisSummary, PositionRow } from './types.js';

describe('risk gateTrade', () => {
  const defaultConfig: AutoTradingConfig = {
    risk: {
      maxDrawdownPct: 5,
      maxDailyLossPct: 3,
      minFreeMarginPct: 20,
      maxOpenPositions: 5,
      correlationCap: 3,
      maxTotalExposurePct: 10,
    },
    minFitness: 50,
  } as any;

  const defaultAccount: AccountSnapshot = {
    balance: 10000,
    equity: 10000,
    freeMargin: 10000,
    margin: 0,
    todayPnL: 0,
  } as any;

  const defaultAnalysis: AnalysisSummary = {
    fitness: 80,
    bias: 'BULL',
    regime: 'TRENDING_UP',
  } as any;

  it('should allow trade if within limits', () => {
    const result = gateTrade(defaultConfig, defaultAccount, [], 'XAUUSD', 0.1, defaultAnalysis, 100, 20);
    expect(result.allowed).toBe(true);
    expect(result.reason).toBe('allowed');
  });

  it('should block if drawdown is too high', () => {
    const account = { ...defaultAccount, equity: 9000 }; // 10% drawdown
    const result = gateTrade(defaultConfig, account, [], 'XAUUSD', 0.1, defaultAnalysis, 100, 20);
    expect(result.allowed).toBe(false);
    expect(result.reason).toContain('drawdown');
  });

  it('should block if daily loss is too high', () => {
    const account = { ...defaultAccount, todayPnL: -400 }; // 4% loss
    const result = gateTrade(defaultConfig, account, [], 'XAUUSD', 0.1, defaultAnalysis, 100, 20);
    expect(result.allowed).toBe(false);
    expect(result.reason.toLowerCase()).toContain('daily loss');
  });

  it('should allow other symbols when one symbol is below its own cap', () => {
    const positions = new Array(5).fill({ symbol: 'EURUSD' });
    const result = gateTrade(defaultConfig, defaultAccount, positions as PositionRow[], 'XAUUSD', 0.1, defaultAnalysis, 100, 20);
    expect(result.allowed).toBe(true);
  });

  it('should block if per-symbol cap is reached', () => {
    const positions = new Array(5).fill({ symbol: 'XAUUSD' });
    const result = gateTrade(defaultConfig, defaultAccount, positions as PositionRow[], 'XAUUSD', 0.1, defaultAnalysis, 100, 20);
    expect(result.allowed).toBe(false);
    expect(result.reason).toContain('XAUUSD per-symbol cap 5 reached');
  });

  it('should block if aggregate hard cap is reached', () => {
    const positions = [
      ...new Array(5).fill({ symbol: 'EURUSD' }),
      ...new Array(5).fill({ symbol: 'GBPUSD' }),
      ...new Array(5).fill({ symbol: 'USDJPY' }),
    ];
    const result = gateTrade(defaultConfig, defaultAccount, positions as PositionRow[], 'XAUUSD', 0.1, defaultAnalysis, 100, 20);
    expect(result.allowed).toBe(false);
    expect(result.reason).toBe('aggregate hard cap 15 reached (15 open)');
  });

  it('should allow over-limit if defense override is enabled', () => {
    const positions = new Array(5).fill({ symbol: 'EURUSD' });
    const result = gateTrade(defaultConfig, defaultAccount, positions as PositionRow[], 'XAUUSD', 0.1, defaultAnalysis, 100, 20, {
      allowOverLimitDefense: true
    });
    expect(result.allowed).toBe(true);
    expect(result.reason).toBe('allowed via defense override');
  });

  it('should block if fitness is too low', () => {
    const analysis = { ...defaultAnalysis, fitness: 40 };
    const result = gateTrade(defaultConfig, defaultAccount, [], 'XAUUSD', 0.1, analysis, 100, 20);
    expect(result.allowed).toBe(false);
    expect(result.reason).toContain('fitness');
  });

  it('should apply anti-hedge using case-insensitive symbol matching', () => {
    const positions = [{ symbol: 'xauusd', side: 'SELL', ticket: 1 }] as PositionRow[];
    const result = gateTrade(defaultConfig, defaultAccount, positions, 'XAUUSD', 0.1, defaultAnalysis, 100, 20, {
      side: 'BUY',
    });
    expect(result.allowed).toBe(false);
    expect(result.reason).toContain('anti-hedge');
  });
});

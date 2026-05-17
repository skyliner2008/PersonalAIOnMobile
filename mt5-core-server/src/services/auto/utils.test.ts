import { describe, it, expect } from 'vitest';
import { calculateRRR, roundToTick, tickSize } from './utils.js';

describe('auto utils', () => {
  describe('calculateRRR', () => {
    it('should calculate correct RRR for the user scenario (XAUUSD SELL)', () => {
      // Entry:4698.06 SL:4711.5 TP:4687.5
      const rrr = calculateRRR('SELL', 4698.06, 4711.5, 4687.5);
      expect(rrr).toBeCloseTo(0.7857, 4);
      expect(Math.round(rrr * 100) / 100).toBe(0.79);
    });

    it('should calculate correct RRR for a typical BUY trade', () => {
      // Entry: 100, SL: 90 (Risk: 10), TP: 120 (Reward: 20) -> RRR: 2.0
      const rrr = calculateRRR('BUY', 100, 90, 120);
      expect(rrr).toBe(2.0);
    });

    it('should return 0 if SL or TP is null', () => {
      expect(calculateRRR('BUY', 100, null, 120)).toBe(0);
      expect(calculateRRR('BUY', 100, 90, null)).toBe(0);
    });
  });

  describe('roundToTick', () => {
    it('should round to 2 decimals for Gold (0.01 tick)', () => {
      expect(roundToTick(4698.062, 0.01)).toBe(4698.06);
      expect(roundToTick(4698.065, 0.01)).toBe(4698.07);
    });

    it('should round to 5 decimals for FX (0.00001 tick)', () => {
      expect(roundToTick(1.085423, 0.00001)).toBe(1.08542);
    });
  });

  describe('tickSize', () => {
    it('should return 0.01 for XAUUSD', () => {
      expect(tickSize('XAUUSD')).toBe(0.01);
    });

    it('should return 0.00001 for EURUSD', () => {
      expect(tickSize('EURUSD')).toBe(0.00001);
    });
  });
});

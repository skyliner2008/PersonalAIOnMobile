/**
 * V25.0 — ReactionDetector
 *
 * M1 reversal pattern detector. Answers user requirement #4 + #6:
 *  - Detect bounce/reversal at wall
 *  - Confirm in M1 (engulf / pinbar / FVG-fill)
 *
 * Strategy: load M1 candles via MarketDataService (cached), look at the last
 * 3 candles, classify the most recent as bullish-reversal / bearish-reversal /
 * none.
 *
 * Phase B uses this synchronously — caller must `await` because it fetches
 * candles. WallStateMachine calls this on M1 BAR_CLOSE events.
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import { marketDataService } from '../../core/MarketDataService.js';
import { atWarn } from '../../utils.js';
import type { PriceWall } from '../../analyzers/smc/types.js';
import { tickBuffers } from '../TickBuffer.js';

export type ReversalKind =
  | 'NONE'
  | 'BULLISH_ENGULF'
  | 'BEARISH_ENGULF'
  | 'BULLISH_PINBAR'
  | 'BEARISH_PINBAR'
  | 'BULLISH_FVG_FILL'
  | 'BEARISH_FVG_FILL';

export interface ReversalResult {
  symbol: string;
  side: 'BUY' | 'SELL' | null;
  kind: ReversalKind;
  m1Close: number;
  m1Open: number;
  ts: number;
}

interface CandleLike {
  t: number; o: number; h: number; l: number; c: number;
}

const NEUTRAL: ReversalResult = { symbol: '', side: null, kind: 'NONE', m1Close: 0, m1Open: 0, ts: 0 };

export class ReactionDetector {
  /**
   * Detect M1 reversal pattern in the most recent closed M1 bar.
   * Returns NEUTRAL if data unavailable.
   */
  async detect(symbol: string): Promise<ReversalResult> {
    return this.detectWallAware(symbol, null, null);
  }

  /**
   * Phase 1.2: Wall-Aware Detection
   * Relax criteria if we know the context (strong wall, HTF alignment).
   */
  async detectWallAware(
    symbol: string, 
    wall: PriceWall | null, 
    side: 'BUY' | 'SELL' | null,
    htfBias: 'BULL' | 'BEAR' | 'NEUTRAL' = 'NEUTRAL'
  ): Promise<ReversalResult> {
    const upper = (symbol || '').toUpperCase();
    let candles: CandleLike[];
    try {
      const raw = await marketDataService.fetchCandles(upper, 'M1', 5);
      candles = raw as CandleLike[];
    } catch (err) {
      atWarn(`[V25] ReactionDetector fetch M1 ${upper} failed: ${err}`);
      return { ...NEUTRAL, symbol: upper };
    }
    if (!candles || candles.length < 3) return { ...NEUTRAL, symbol: upper };

    const cur = candles[candles.length - 1];
    const prev = candles[candles.length - 2];
    if (!cur || !prev) return { ...NEUTRAL, symbol: upper };

    const range = Math.max(cur.h - cur.l, 1e-9);
    const body = Math.abs(cur.c - cur.o);
    const upperWick = cur.h - Math.max(cur.o, cur.c);
    const lowerWick = Math.min(cur.o, cur.c) - cur.l;

    // Phase 1.3: Relax threshold for strong walls + HTF alignment
    let engulfThreshold = 0.55;
    let pinbarBodyThreshold = 0.35;
    let pinbarWickThreshold = 0.55;

    if (wall && wall.confluenceStars >= 4) {
      // Is this side aligned with HTF bias?
      const isAligned = (side === 'BUY' && htfBias === 'BULL') || (side === 'SELL' && htfBias === 'BEAR');
      if (isAligned) {
        engulfThreshold = 0.40;  // More relaxed for strong walls
        pinbarBodyThreshold = 0.45;
        pinbarWickThreshold = 0.45;
      }
    }

    // ── Phase 1.2: Check if M1 micro-wall (tick swing) touched the wall ──
    let touchConfirmed = false;
    if (wall) {
        const buf = tickBuffers.get(upper);
        if (buf) {
            // Check last 60 seconds of ticks to see if price reached wall
            const ticks = buf.sinceMs(Date.now() - 60000);
            if (side === 'BUY') {
                const minMid = Math.min(...ticks.map(t => t.mid));
                // If price dropped below the wall or came very close (within 20% of M1 range)
                if (minMid <= wall.priceTop + (range * 0.2)) touchConfirmed = true;
            } else if (side === 'SELL') {
                const maxMid = Math.max(...ticks.map(t => t.mid));
                // If price rose above the wall or came very close
                if (maxMid >= wall.priceBottom - (range * 0.2)) touchConfirmed = true;
            }
        }
    }

    // ── Engulfing ──
    if (cur.c > cur.o && cur.o <= prev.c && cur.c >= prev.o && body / range > engulfThreshold && prev.c < prev.o) {
      if (!wall || side === 'BUY') return { symbol: upper, side: 'BUY', kind: 'BULLISH_ENGULF', m1Close: cur.c, m1Open: cur.o, ts: cur.t };
    }
    if (cur.c < cur.o && cur.o >= prev.c && cur.c <= prev.o && body / range > engulfThreshold && prev.c > prev.o) {
      if (!wall || side === 'SELL') return { symbol: upper, side: 'SELL', kind: 'BEARISH_ENGULF', m1Close: cur.c, m1Open: cur.o, ts: cur.t };
    }

    // ── Pinbar ── (long wick on one side, small body)
    if (lowerWick / range > pinbarWickThreshold && body / range < pinbarBodyThreshold && cur.c > cur.o) {
      if (!wall || side === 'BUY') return { symbol: upper, side: 'BUY', kind: 'BULLISH_PINBAR', m1Close: cur.c, m1Open: cur.o, ts: cur.t };
    }
    if (upperWick / range > pinbarWickThreshold && body / range < pinbarBodyThreshold && cur.c < cur.o) {
      if (!wall || side === 'SELL') return { symbol: upper, side: 'SELL', kind: 'BEARISH_PINBAR', m1Close: cur.c, m1Open: cur.o, ts: cur.t };
    }

    // ── FVG fill rejection ── (price wicked into prev candle's gap then closed back)
    //   simplified: if cur low pierced prev2.high then closed above prev.h → bullish fill
    if (candles.length >= 3) {
      const prev2 = candles[candles.length - 3];
      // Bullish FVG fill: prev2 had a gap-up vs prev (prev.l > prev2.h) → FVG bull;
      // current candle wicked back into [prev.l, prev2.h] then closed above
      if (prev.l > prev2.h && cur.l <= prev.l && cur.c > prev.l && cur.c > cur.o) {
        if (!wall || side === 'BUY') return { symbol: upper, side: 'BUY', kind: 'BULLISH_FVG_FILL', m1Close: cur.c, m1Open: cur.o, ts: cur.t };
      }
      // Bearish FVG fill
      if (prev.h < prev2.l && cur.h >= prev.h && cur.c < prev.h && cur.c < cur.o) {
        if (!wall || side === 'SELL') return { symbol: upper, side: 'SELL', kind: 'BEARISH_FVG_FILL', m1Close: cur.c, m1Open: cur.o, ts: cur.t };
      }
    }

    // Phase 1.4: "Wall Before Main Wall" - Fallback
    // If we didn't find a strong reversal but price touched the micro-wall and reversed slightly
    if (wall && touchConfirmed) {
      // Did price close in the expected direction?
      if (side === 'BUY' && cur.c > cur.o) {
        return { symbol: upper, side: 'BUY', kind: 'BULLISH_PINBAR', m1Close: cur.c, m1Open: cur.o, ts: cur.t }; // Use Pinbar as generic reversal
      } else if (side === 'SELL' && cur.c < cur.o) {
        return { symbol: upper, side: 'SELL', kind: 'BEARISH_PINBAR', m1Close: cur.c, m1Open: cur.o, ts: cur.t };
      }
    }

    return { ...NEUTRAL, symbol: upper, m1Close: cur.c, m1Open: cur.o, ts: cur.t };
  }
}

export const reactionDetector = new ReactionDetector();

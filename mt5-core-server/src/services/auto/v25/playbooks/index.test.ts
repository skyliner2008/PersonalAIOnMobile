import { describe, expect, it } from 'vitest';
import {
  aiBiasConflictBlocksSide,
  annotatePathMilestone,
  executableMinRrrForPlan,
  inferDirectionalMandate,
  liveMarketRiskIssue,
  resolveWallForEvent,
  skipBlockAppliesToSide,
  skipDecisionBlockReason,
  targetWallForTradeSide,
  tradeSideForWallEvent,
} from './index.js';
import type { PriceMap, PriceWall } from '../../analyzers/smc/types.js';
import type { Tick, WallStateEvent } from '../types.js';
import type { TradePlan } from './types.js';

function wall(price: number, side: PriceWall['side'], stars: number, timeframes: string[] = ['M15']): PriceWall {
  return {
    price,
    priceTop: price,
    priceBottom: price,
    side,
    confluenceStars: stars,
    timeframes,
    sources: [],
    hasOB: false,
    hasFVG: false,
    hasLiquidity: false,
    isStructure: false,
    label: `${side}@${price}`,
    distanceFromPrice: 0,
    distancePct: 0,
  };
}

function priceMap(overrides: Partial<PriceMap>): PriceMap {
  return {
    symbol: 'XAUUSD',
    currentPrice: 4721.94,
    atr: 8,
    walls: [],
    resistanceWalls: [],
    supportWalls: [],
    nearestResistance: null,
    nearestSupport: null,
    strongestResistance: null,
    strongestSupport: null,
    sourceTimeframes: ['M5', 'M15', 'M30', 'H1'],
    timestamp: Date.now(),
    ...overrides,
  };
}

describe('V25 decision skip side matching', () => {
  it('blocks only BUY when AutoEngine vetoed BUY in premium zone', () => {
    const block = skipDecisionBlockReason({
      side: 'SKIP',
      rationale: '[AI SKIP] [DET] MTF zone gate veto: BUY blocked (H4:PREMIUM | H1:PREMIUM)',
      riskGate: 'MTF zone gate veto: BUY blocked (H4:PREMIUM | H1:PREMIUM)',
    });

    expect(block?.side).toBe('BUY');
    expect(skipBlockAppliesToSide(block, 'BUY')).toBe(true);
    expect(skipBlockAppliesToSide(block, 'SELL')).toBe(false);
  });

  it('blocks only SELL when Decision Side Guard rejected SELL', () => {
    const block = skipDecisionBlockReason({
      side: 'SKIP',
      rationale: '[AI SKIP] Decision Side Guard blocked: SELL conflicts with technical BUY at 39.4% <= 75%. Forced SKIP.',
      riskGate: 'Decision Side Guard blocked: SELL conflicts with technical BUY at 39.4% <= 75%. Forced SKIP.',
    });

    expect(block?.side).toBe('SELL');
    expect(skipBlockAppliesToSide(block, 'SELL')).toBe(true);
    expect(skipBlockAppliesToSide(block, 'BUY')).toBe(false);
  });

  it('applies global risk blocks to both sides', () => {
    const block = skipDecisionBlockReason({
      side: 'SKIP',
      rationale: '[AI SKIP] NEWS gate: high impact event too close',
      riskGate: 'NEWS gate: high impact event too close',
    });

    expect(block?.side).toBe('ANY');
    expect(skipBlockAppliesToSide(block, 'BUY')).toBe(true);
    expect(skipBlockAppliesToSide(block, 'SELL')).toBe(true);
  });

  it('treats a blocked SELL skip as side-specific, allowing V25 BUY to evaluate', () => {
    const block = skipDecisionBlockReason({
      side: 'SKIP',
      rationale: '[AI SELL] [EA-ONLY] MTF-aligned Short: buy=0 vs sell=74.8',
      riskGate: 'Zone-Aware Gate: SELL in DISCOUNT zone (31.1% of range; H4=BEAR). BLOCKED.',
    });

    expect(block?.side).toBe('SELL');
    expect(skipBlockAppliesToSide(block, 'SELL')).toBe(true);
    expect(skipBlockAppliesToSide(block, 'BUY')).toBe(false);
  });
});

describe('V25 AI bias conflict gate', () => {
  it('does not hard-block a range support BUY from SKIP + BEAR context', () => {
    const decision = {
      side: 'SKIP',
      overallBias: 'BEAR',
      regime: 'RANGING',
      rationale: '[AI SELL] [EA-ONLY] deterministic+SMC V2: MTF-aligned Short',
      riskGate: 'Proximity Gate: Nearest resistance 4-star is 1435 pips away. BLOCKED.',
    };

    expect(aiBiasConflictBlocksSide(decision, 'BUY', 'RANGING', null)).toBe(false);
  });

  it('still blocks the opposite side when AutoEngine has an executable side', () => {
    expect(aiBiasConflictBlocksSide({ side: 'SELL', overallBias: 'BEAR' }, 'BUY', 'RANGING', null)).toBe(true);
    expect(aiBiasConflictBlocksSide({ side: 'BUY', overallBias: 'BULL' }, 'SELL', 'RANGING', null)).toBe(true);
  });

  it('keeps bias protection in trending conditions when the last decision is SKIP', () => {
    expect(aiBiasConflictBlocksSide({ side: 'SKIP', overallBias: 'BEAR' }, 'BUY', 'TRENDING', null)).toBe(true);
    expect(aiBiasConflictBlocksSide({ side: 'SKIP', overallBias: 'BULL' }, 'SELL', 'TRENDING', null)).toBe(true);
  });

  it('treats AutoEngine RANGING SKIP as soft context even when V25 local regime is trending', () => {
    expect(aiBiasConflictBlocksSide({ side: 'SKIP', overallBias: 'BEAR', regime: 'RANGING' }, 'BUY', 'TRENDING', null)).toBe(false);
  });

  it('marks old or undated AutoEngine decisions stale before directional blocking', () => {
    const oldDecision = {
      symbol: 'XAUUSD',
      side: 'SELL',
      at: new Date(Date.now() - 10_000).toISOString(),
      rationale: '[AI SELL] old cycle',
    };

    expect(inferDirectionalMandate('XAUUSD', oldDecision, 1_000).stale).toBe(true);
    expect(inferDirectionalMandate('XAUUSD', { symbol: 'XAUUSD', side: 'SELL' }, 1_000).stale).toBe(true);
  });
});

describe('V25 live order preflight', () => {
  const sellPlan: TradePlan = {
    playbook: 'PB1_WALL_TOUCH',
    symbol: 'XAUUSD',
    side: 'SELL',
    entry: 4733.76,
    sl: 4735.49,
    tp: 4729.3,
    rrr: 2.57,
    wallStars: 4,
    oppositeWallStars: 3,
    triggers: [],
    ts: Date.now(),
  };

  it('blocks a SELL when price has drifted close to the planned TP before execution', () => {
    const tick: Tick = {
      symbol: 'XAUUSD',
      srcTs: Date.now(),
      recvTs: Date.now(),
      bid: 4729.68,
      ask: 4729.85,
      mid: 4729.765,
      spread: 0.17,
    };

    expect(liveMarketRiskIssue(sellPlan, tick, 1.8)).toContain('live TP distance');
  });

  it('allows the same SELL plan while the live price still preserves stop distance and RRR', () => {
    const tick: Tick = {
      symbol: 'XAUUSD',
      srcTs: Date.now(),
      recvTs: Date.now(),
      bid: 4733.76,
      ask: 4733.86,
      mid: 4733.81,
      spread: 0.1,
    };

    expect(liveMarketRiskIssue(sellPlan, tick, 1.8)).toBeNull();
  });

  it('uses the lower PB4 executable floor during live preflight', () => {
    const pb4Plan: TradePlan = {
      playbook: 'PB4_RANGE_FADE',
      symbol: 'XAUUSD',
      side: 'SELL',
      entry: 4696.9,
      sl: 4698.88,
      tp: 4693.74,
      rrr: 1.6,
      wallStars: 3,
      oppositeWallStars: 3,
      triggers: [],
      ts: Date.now(),
    };
    const tick: Tick = {
      symbol: 'XAUUSD',
      srcTs: Date.now(),
      recvTs: Date.now(),
      bid: 4696.9,
      ask: 4697,
      mid: 4696.95,
      spread: 0.1,
    };

    expect(executableMinRrrForPlan(pb4Plan, 1.8)).toBe(1.5);
    expect(liveMarketRiskIssue(pb4Plan, tick, executableMinRrrForPlan(pb4Plan, 1.8))).toBeNull();
    expect(liveMarketRiskIssue(pb4Plan, tick, 1.8)).toContain('live RRR');
  });

  it('uses the lower PB1 executable floor when the touched wall has 5 stars', () => {
    const pb1Plan: TradePlan = {
      playbook: 'PB1_WALL_TOUCH',
      symbol: 'XAUUSD',
      side: 'SELL',
      entry: 4696.9,
      sl: 4698.9,
      tp: 4693.7,
      rrr: 1.6,
      wallStars: 5,
      oppositeWallStars: 3,
      triggers: [],
      ts: Date.now(),
    };

    expect(executableMinRrrForPlan(pb1Plan, 1.8)).toBe(1.5);
  });
});

describe('V25 PriceMap path milestone', () => {
  it('keeps the original TP and annotates a BUY whose TP crosses a strong resistance wall', () => {
    const plan: TradePlan = {
      playbook: 'PB1_TREND_TOUCH',
      symbol: 'XAUUSD',
      side: 'BUY',
      entry: 4720.41,
      sl: 4718.06,
      tp: 4728.5,
      rrr: 3.44,
      wallStars: 2,
      oppositeWallStars: 4,
      triggers: [],
      ts: Date.now(),
    };
    const map = priceMap({
      resistanceWalls: [
        wall(4723.33, 'RESISTANCE', 5, ['M5', 'M15', 'H1', 'SESSION']),
        wall(4728.9, 'RESISTANCE', 4, ['M5', 'M15', 'M30']),
      ],
    });

    const milestone = annotatePathMilestone(plan, map);

    expect(milestone).toMatchObject({
      price: 4723.33,
      protectAt: 4723.33,
      stars: 5,
      timeframes: ['M5', 'M15', 'H1', 'SESSION'],
    });
    expect(plan.tp).toBe(4728.5);
    expect(plan.rrr).toBe(3.44);
    expect(plan.triggers[plan.triggers.length - 1]).toContain('path milestone 5-star wall @ 4723.33');
  });

  it('ignores weak path walls below the path obstacle threshold', () => {
    const plan: TradePlan = {
      playbook: 'PB1_TREND_TOUCH',
      symbol: 'XAUUSD',
      side: 'BUY',
      entry: 4720.41,
      sl: 4718.06,
      tp: 4728.5,
      rrr: 3.44,
      wallStars: 2,
      oppositeWallStars: 4,
      triggers: [],
      ts: Date.now(),
    };
    const map = priceMap({
      resistanceWalls: [
        wall(4723.33, 'RESISTANCE', 2, ['M5']),
      ],
    });

    expect(annotatePathMilestone(plan, map)).toBeNull();
    expect(plan.tp).toBe(4728.5);
  });
});

describe('V25 wall event resolution', () => {
  it('keeps the event trigger wall after price crosses it and nearest index advances', () => {
    const triggerWall = wall(4706.65, 'RESISTANCE', 5, ['M5', 'M15', 'M30', 'H1', 'D1']);
    const ev: WallStateEvent = {
      symbol: 'XAUUSD',
      side: 'ABOVE',
      state: 'CONFIRM',
      prevState: 'REACT',
      wallPrice: 4706.65,
      wall: triggerWall,
      ts: Date.now(),
    };

    const resolved = resolveWallForEvent(ev, {
      above: wall(4714.2, 'RESISTANCE', 3, ['M15', 'M30']),
      below: wall(4702.59, 'SUPPORT', 3, ['M5', 'H1']),
      resistanceAsc: [wall(4714.2, 'RESISTANCE', 3, ['M15', 'M30'])],
      supportDesc: [wall(4702.59, 'SUPPORT', 3, ['M5', 'H1'])],
    });

    expect(resolved?.price).toBe(4706.65);
    expect(resolved?.confluenceStars).toBe(5);
  });

  it('routes CONFIRM fades and RETEST continuations to the correct trade side', () => {
    expect(tradeSideForWallEvent({ side: 'ABOVE', state: 'CONFIRM' })).toBe('SELL');
    expect(tradeSideForWallEvent({ side: 'BELOW', state: 'CONFIRM' })).toBe('BUY');
    expect(tradeSideForWallEvent({ side: 'ABOVE', state: 'RETEST' })).toBe('BUY');
    expect(tradeSideForWallEvent({ side: 'BELOW', state: 'RETEST' })).toBe('SELL');
  });

  it('selects the target wall from the actual trade direction', () => {
    const above = wall(4710, 'RESISTANCE', 4);
    const below = wall(4698, 'SUPPORT', 5);
    const q = {
      above,
      below,
      resistanceAsc: [above],
      supportDesc: [below],
    };

    expect(targetWallForTradeSide('BUY', q)?.price).toBe(4710);
    expect(targetWallForTradeSide('SELL', q)?.price).toBe(4698);
  });
});

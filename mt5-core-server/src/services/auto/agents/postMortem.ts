import { TradeLesson } from './types.js';
import { AutoTradingConfig } from '../types.js';
import { generateForRole } from '../providers/dispatcher.js';
import { parseLlmJson, LlmJsonError } from './jsonParse.js';

export interface FailurePatternArgs {
  side: string | null | undefined;
  strategy: string | null | undefined;
  outcome: string | null | undefined;
  profitR: number | null | undefined;
  zoneAtEntry?: string | null;
  regimeAtEntry?: string | null;
  htfBiasAtEntry?: string | null;
  closeReason?: string | null;
  scaleInSteps?: number;
  weightedAvgEntry?: number | null;
  finalEntry?: number | null;
  slInsideFvg?: boolean;
}

export function detectFailurePattern(args: FailurePatternArgs): string[] {
  const tags = new Set<string>();
  const profitR = Number(args.profitR ?? 0);
  const wasLoss = (args.outcome === 'LOSS') || profitR <= -0.5;
  const isBuy = args.side === 'BUY';
  const isSell = args.side === 'SELL';
  if (wasLoss && isBuy && args.zoneAtEntry === 'PREMIUM') tags.add('BUY_IN_PREMIUM');
  if (wasLoss && isSell && args.zoneAtEntry === 'DISCOUNT') tags.add('SELL_IN_DISCOUNT');
  if (wasLoss && isBuy && args.htfBiasAtEntry === 'BEAR') tags.add('COUNTERTREND_BUY');
  if (wasLoss && isSell && args.htfBiasAtEntry === 'BULL') tags.add('COUNTERTREND_SELL');
  if (wasLoss && args.slInsideFvg) tags.add('SL_INSIDE_FVG');
  if (wasLoss && (args.scaleInSteps ?? 0) > 0) {
    if (isBuy && args.finalEntry != null && args.weightedAvgEntry != null && args.finalEntry > args.weightedAvgEntry) tags.add('SCALE_UP_LOSER');
    else if (isSell && args.finalEntry != null && args.weightedAvgEntry != null && args.finalEntry < args.weightedAvgEntry) tags.add('SCALE_DOWN_LOSER');
    else tags.add('SCALED_INTO_LOSS');
  }
  if (wasLoss && (args.strategy || '').toUpperCase().includes('FVG')) {
    if (args.zoneAtEntry === 'PREMIUM' || args.zoneAtEntry === 'DISCOUNT') tags.add('FVG_STRATEGY_OUTSIDE_FVG');
  }
  if (args.outcome === 'BE' && (args.closeReason || '').toLowerCase().includes('breakeven')) tags.add('BE_STOP_ERASED_GAIN');
  return Array.from(tags);
}

export class PostMortemAgent {
  private hasKeyHint: boolean;
  constructor(apiKey: string) { this.hasKeyHint = !!apiKey; }

  public async review(closedTrade: any, config?: AutoTradingConfig): Promise<TradeLesson> {
    if (!config) throw new Error('PostMortemAgent: config missing');

    // 2026-05-05 Fix: Inject pre-computed outcome so LLM cannot hallucinate
    // "WIN" for Break-Even ($0) trades. Log analysis found 5+ BE trades
    // incorrectly labeled as WIN, corrupting learning statistics.
    const serverOutcome = closedTrade?.outcome || null;
    const serverProfitR = closedTrade?.profitR ?? closedTrade?.profit_r ?? null;
    const serverProfit = closedTrade?.profit ?? null;
    const outcomeHint = serverOutcome
      ? [
          '',
          'ACTUAL OUTCOME (pre-computed by server — DO NOT OVERRIDE):',
          `- outcome: ${serverOutcome}`,
          `- profitR: ${serverProfitR ?? 'unknown'}`,
          `- profit: $${serverProfit ?? 'unknown'}`,
          '⚠️ You MUST use the outcome above in your response. Do NOT change it.',
        ]
      : [];

    const prompt = [
      'You are a Professional Trading Performance Coach.',
      'Conduct a Post-Mortem on a closed trade to extract deep lessons.',
      '',
      'TRADE DATA:',
      JSON.stringify(closedTrade, null, 2),
      ...outcomeHint,
      '',
      'TASK:',
      '1. Determine if execution followed the plan.',
      '2. Identify why the trade resulted in a win or loss.',
      '3. Suggest one concrete sentence of advice for future trades.',
      '4. Provide a few tags for the knowledge graph.',
      '5. Determine if it was a Good Decision (even if it was a loss).',
      '6. Suggest a minor prompt change to the Analyst or Risk Officer if you see a repetitive mistake.',
      '',
      'Respond in JSON format ONLY:',
      '{ "ticket": 0, "outcome": "WIN|LOSS|BE|PARTIAL", "profitR": 0.0, "lesson": "...", "tags": [], "wasGoodDecision": true, "suggestedPromptDelta": "" }',
    ].join('\n');
    try {
      const result = await generateForRole({ cfg: config, role: 'postMortem' }, prompt, { temperature: 0.4 });
      return parseLlmJson<TradeLesson>(result.text, { allowEmpty: false });
    } catch (error) {
      const errLabel = error instanceof LlmJsonError
        ? `parse failed (${error.message}) raw=${error.raw.slice(0, 160)}`
        : (error as any)?.message || String(error);
      console.error(`[PostMortemAgent] Review failed: ${errLabel}`);
      throw error;
    }
  }
}

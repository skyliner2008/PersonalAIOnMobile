import { RiskApproval, OrderPlan } from './types.js';
import { AutoTradingConfig } from '../types.js';
import { generateForRole } from '../providers/dispatcher.js';
import { modelRankerService } from '../core/ModelRankerService.js';
import { parseLlmJson, LlmJsonError } from './jsonParse.js';

export class ExecutionAgent {
  private hasKeyHint: boolean;
  constructor(apiKey: string) { this.hasKeyHint = !!apiKey; }

  public async planOrder(
    approval: RiskApproval,
    marketPrice: number,
    config?: AutoTradingConfig,
  ): Promise<OrderPlan | null> {
    if (!approval.approved || approval.action === 'SKIP') return null;
    if (!config) throw new Error('ExecutionAgent: config missing');

    const prompt = `
You are a Professional Execution Trader.
Take the approved trade plan and decide the best execution strategy.

APPROVAL:
- Action: ${approval.action}
- Management: ${approval.management}
- Volume: ${approval.adjustedVolume}
- SL: ${approval.sl}
- TP: ${approval.tp}
- Current Market Price: ${marketPrice}

TASK:
1. Choose between MARKET, LIMIT, or STOP order.
2. Decide the entry price (if LIMIT/STOP).
3. Set fill strategy (AGGRESSIVE for news/breakouts, PATIENT for ranges).
4. Provide a professional comment for the MT5 ticket.

Respond in JSON format ONLY:
{
  "type": "MARKET|LIMIT|STOP",
  "price": 0.0,
  "volume": 0.0,
  "symbol": "XAUUSD",
  "side": "BUY|SELL",
  "sl": 0.0,
  "tp": 0.0,
  "comment": "JARVIS Execution",
  "fillStrategy": "AGGRESSIVE|PATIENT"
}
`;

    let modelLabel = 'unknown';
    try {
      const result = await generateForRole({ cfg: config, role: 'executionTrader' }, prompt, { 
        temperature: 0.2, 
        jsonMode: true,
        maxOutputTokens: 1024
      });
      modelLabel = result.modelUsed;
      const plan = parseLlmJson<OrderPlan>(result.text, { allowEmpty: false });
      plan.volume = approval.adjustedVolume || 0.01;
      plan.sl = approval.sl;
      plan.tp = approval.tp;
      plan.modelUsed = modelLabel;
      return plan;
    } catch (error: any) {
      if (modelLabel && modelLabel !== 'unknown') {
        modelRankerService.recordContentFailure(modelLabel, 'openrouter', 'executionTrader');
      }
      const errLabel = error instanceof LlmJsonError
        ? `parse failed (${error.message}) raw=${error.raw.slice(0, 160)}`
        : (error as any)?.message || String(error);
      console.error(`[ExecutionAgent] Planning failed (Model: ${modelLabel}): ${errLabel}`);
      return null;
    }
  }
}

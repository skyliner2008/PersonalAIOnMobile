import { AutoTradingConfig, AccountSnapshot, PositionRow } from '../types.js';
import { AnalystReport, RiskApproval } from './types.js';
import { generateForRole } from '../providers/dispatcher.js';
import { modelRankerService } from '../core/ModelRankerService.js';
import { parseLlmJson, LlmJsonError } from './jsonParse.js';

export class RiskOfficerAgent {
  private hasKeyHint: boolean;
  constructor(apiKey: string) { this.hasKeyHint = !!apiKey; }

  public async review(
    report: AnalystReport,
    account: AccountSnapshot,
    positions: PositionRow[],
    config: AutoTradingConfig,
    currentEntry?: number,
  ): Promise<RiskApproval> {
    const symbolPositions = positions.filter((p) => p.symbol === report.symbol);
    const totalExposure = (account.margin / account.equity) * 100;

    const livePrice = currentEntry
      ? currentEntry.toFixed(2)
      : report.suggestedSL
        ? Math.abs((report.suggestedSL + (report.suggestedTP || report.suggestedSL)) / 2).toFixed(2)
        : 'unknown';

    const strategyUpper = report.suggestedStrategy?.toUpperCase() || '';
    const minRRR = (
      strategyUpper.includes('SCALP') ||
      strategyUpper.includes('SMC') ||
      strategyUpper.includes('MEAN_REVERSION') ||
      strategyUpper.includes('RANGE') ||
      strategyUpper.includes('GRID')
    ) ? 1.0 : 1.5;

    const prompt = `
You are a Strict Risk Officer Agent.
Your job is to VETO or APPROVE trade proposals from the Analyst.
You prioritize capital preservation over profit.

Current live price of ${report.symbol} is ~${livePrice}. Validate ALL SL/TP relative to THIS price.

RRR CALIBRATION RULE (MUST ENFORCE):
- Minimum RRR for this strategy (${report.suggestedStrategy}): ${minRRR}:1
- TP distance MUST be >= SL distance * ${minRRR}
- If Analyst's TP gives RRR < ${minRRR}, you MUST recalculate TP to achieve RRR = ${minRRR}.

PROPOSAL:
- Symbol: ${report.symbol}
- Current Live Entry Price: ${livePrice}
- Bias: ${report.bias}
- Strategy: ${report.suggestedStrategy}
- Suggested SL: ${report.suggestedSL} (Must be on the correct side of ${livePrice} based on Bias)
- Suggested TP: ${report.suggestedTP} (Must provide at least ${minRRR}:1 RRR against SL)
- Confidence: ${report.confidence}%
- Narrative: ${report.narrative}

PORTFOLIO STATE:
- Equity: ${account.equity.toFixed(2)}
- Current Margin: ${account.margin.toFixed(2)} (${totalExposure.toFixed(1)}%)
- Total Open Positions: ${positions.length}
- Positions for ${report.symbol}: ${symbolPositions.length}

RISK LIMITS:
- Max Open Positions PER SYMBOL: ${config.risk.maxOpenPositions}
- Max Total Exposure: ${config.risk.maxTotalExposurePct}%
- Risk Per Trade: ${config.risk.riskPerTradePct}%

Respond in JSON format ONLY:
{
  "approved": true,
  "reason": "Clear explanation in English",
  "reason_th": "Thai explanation",
  "action": "BUY|SELL|SKIP",
  "management": "HEDGE|REDUCE|SCALE_IN|TRAIL|HOLD",
  "adjustedVolume": 0.0,
  "sl": 0.0,
  "tp": 0.0,
  "allowOverLimitDefense": false,
  "riskNote": "Brief note in English",
  "riskNote_th": "Thai note"
}
`;

    let modelLabel = 'unknown';
    try {
      const result = await generateForRole({ cfg: config, role: 'riskOfficer' }, prompt, { 
        temperature: 0.2, 
        jsonMode: true,
        maxOutputTokens: 1024 
      });
      modelLabel = result.modelUsed;
      const parsed = parseLlmJson<RiskApproval>(result.text, { allowEmpty: false });
      parsed.modelUsed = modelLabel;

      // 2026-05-01 — Penalize Analyst เมื่อเสนอ SL/TP ผิดด้าน (wrong-direction)
      //   เดิม Risk Officer แค่ veto trade แต่ไม่บันทึกว่า Analyst output ผิด
      //   ทำให้ ranker ไม่รู้ว่าควรลดคะแนน → ใช้โมเดลแย่ๆ ซ้ำ
      //   ตอนนี้ถือเป็น content failure (เทียบเท่า parse fail) → blacklist 30/60/120 นาที
      if (currentEntry && (report.bias === 'BEAR' || report.bias === 'BULL')) {
        const isBear = report.bias === 'BEAR';
        const sSl = report.suggestedSL;
        const sTp = report.suggestedTP;
        const slWrong = sSl != null && (isBear ? sSl <= currentEntry : sSl >= currentEntry);
        const tpWrong = sTp != null && (isBear ? sTp >= currentEntry : sTp <= currentEntry);
        if (slWrong || tpWrong) {
          const analystModel = (report.modelUsed || '')
            .replace(/^\[Model:\s*/, '').replace(/\]\s*$/, '').trim();
          if (analystModel) {
            modelRankerService.recordContentFailure(analystModel, 'openrouter', 'analyst');
          }
          parsed.approved = false;
          parsed.action = 'SKIP';
          parsed.management = 'HOLD';
          const tags: string[] = [];
          if (slWrong) tags.push(`SL ${sSl} on wrong side of entry ${currentEntry} for ${report.bias}`);
          if (tpWrong) tags.push(`TP ${sTp} on wrong side of entry ${currentEntry} for ${report.bias}`);
          parsed.reason = `Analyst wrong-direction (${analystModel || 'unknown'}): ${tags.join(' | ')}`;
          parsed.riskNote = `Analyst penalized for direction violation`;
          return parsed;
        }
      }

      // Code-side RRR validator (LLM cannot be trusted to do the math).
      if (parsed.approved && (parsed.action === 'BUY' || parsed.action === 'SELL') && currentEntry && parsed.sl != null) {
        const slDist = Math.abs(currentEntry - parsed.sl);
        if (slDist <= 0) {
          parsed.approved = false;
          parsed.action = 'SKIP';
          parsed.reason = `Code-side guard: SL distance ${slDist} is non-positive`;
        } else {
          const requiredTpDist = slDist * minRRR;
          const desiredTp = parsed.action === 'BUY'
            ? currentEntry + requiredTpDist
            : currentEntry - requiredTpDist;
          if (parsed.tp == null || Math.abs(parsed.tp - currentEntry) < requiredTpDist) {
            const oldTp = parsed.tp;
            parsed.tp = Number(desiredTp.toFixed(5));
            parsed.riskNote = `${parsed.riskNote || ''} [TP auto-extended ${oldTp ?? 'null'}->${parsed.tp} for minRRR ${minRRR}]`.trim();
          }
        }
      }

      // 2026-05-04 Fix #9: Detect Risk Officer RRR Math Hallucinations
      // Small models often hallucinate that RRR < 1.5 when it's exactly 1.5,
      // rejecting valid trades. If reason mentions RRR but math is correct,
      // penalize the Risk Officer for content failure.
      if (!parsed.approved && parsed.action === 'SKIP' && currentEntry && report.suggestedSL && report.suggestedTP) {
        const reasonLower = (parsed.reason || '').toLowerCase();
        if (reasonLower.includes('rrr') || reasonLower.includes('ratio') || reasonLower.includes('risk-adjusted')) {
          const slDist = Math.abs(currentEntry - report.suggestedSL);
          const tpDist = Math.abs(report.suggestedTP - currentEntry);
          if (slDist > 0) {
            const actualRrr = tpDist / slDist;
            if (actualRrr >= minRRR * 0.98) { // 2% tolerance for float math
              modelRankerService.recordContentFailure(modelLabel, 'openrouter', 'riskOfficer');
              parsed.reason = `Code-side Guard: Risk Officer hallucinated RRR violation (actual ${actualRrr.toFixed(2)} >= ${minRRR}). Model penalized.`;
            }
          }
        }
      }

      return parsed;
    } catch (error: any) {
      if (modelLabel && modelLabel !== 'unknown') {
        modelRankerService.recordContentFailure(modelLabel, 'openrouter', 'riskOfficer');
      }
      const errLabel = error instanceof LlmJsonError
        ? `parse failed (${error.message}) raw=${error.raw.slice(0, 160)}`
        : (error as any)?.message || String(error);
      console.error(`[RiskOfficerAgent] Review failed (Model: ${modelLabel}): ${errLabel}`);
      return {
        approved: false,
        reason: `Internal agent error (${modelLabel}): ${errLabel.slice(0, 120)}`,
        action: 'SKIP',
        management: 'HOLD',
        adjustedVolume: null,
        sl: null,
        tp: null,
        riskNote: 'Internal error occurred during risk review',
        allowOverLimitDefense: false,
        modelUsed: modelLabel,
      };
    }
  }
}

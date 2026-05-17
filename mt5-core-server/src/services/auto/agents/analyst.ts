import { AutoTradingConfig, AnalysisSummary } from '../types.js';
import { AnalystReport } from './types.js';
import { vectorStore } from '../vectorStore.js';
import { agentOrchestrator } from '../agentOrchestrator.js';
import { generateForRole } from '../providers/dispatcher.js';
import { modelRankerService } from '../core/ModelRankerService.js';
import { parseLlmJson, LlmJsonError } from './jsonParse.js';

export class AnalystAgent {
  private hasKeyHint: boolean;
  constructor(apiKey: string) { this.hasKeyHint = !!apiKey; }

  public async analyze(
    config: AutoTradingConfig,
    symbol: string,
    analyses: Record<string, AnalysisSummary>,
    correlations: any[] = [],
    currentPrice?: number,
    smcAnchors?: { sl: number | null; tp: number | null; rationale: string; side?: string }
  ): Promise<AnalystReport> {
    const primary = analyses[config.timeframe || 'H1'] || Object.values(analyses)[0];

    // Episodic memory retrieval — single embedding source-of-truth.
    let pastLessonsContext = '';
    try {
      const query = `Symbol: ${symbol}, Regime: ${primary.regime}, Bias: ${primary.bias}`;
      const queryEmbedding = await agentOrchestrator.getEmbeddingForConfig(config, query);
      const similarities = vectorStore.search(queryEmbedding, 10, { type: 'trade_lesson' });
      if (similarities.length > 0) {
        pastLessonsContext = similarities.map((s) => {
          const meta = s.metadata;
          if (!meta) return '';
          return `- ${meta.text || 'N/A'} (Outcome: ${meta.outcome || 'N/A'})`;
        }).filter((t) => t !== '').join('\n');
      }
    } catch (err: any) {
      console.warn(`[AnalystAgent] Memory retrieval failed:`, err?.message || err);
      pastLessonsContext = 'Memory unavailable. Relying on real-time technicals.';
    }

    const priceAnchor = currentPrice ? currentPrice.toFixed(2) : (analyses['M15']?.ema20 || analyses['H1']?.ema20 || 'unknown');
    const systemPrompt = `You are a Senior Market Analyst. Respond in JSON ONLY.
NO conversational text. NO markdown. START with '{' and END with '}'.`;

    const userPrompt = `
SYMBOL: ${symbol}
LIVE PRICE: ${priceAnchor}

MTF DATA:
${Object.entries(analyses).map(([tf, a]) => `- ${tf}: Bias=${a.bias}, Conf=${a.confluence.toFixed(1)}, Regime=${a.regime}`).join('\n')}

SMC STRUCTURE:
${Object.entries(analyses).map(([tf, a]) => {
    if (!a.smc) return `${tf}: N/A`;
    const p = [];
    if (a.smc.fvg?.length) {
      const f = a.smc.fvg[a.smc.fvg.length - 1];
      p.push(`FVG(${f.FVG > 0 ? 'Bull' : 'Bear'}):${f.Bottom}-${f.Top}`);
    }
    if (a.smc.ob?.length) {
      const o = a.smc.ob[a.smc.ob.length - 1];
      p.push(`OB(${o.OB > 0 ? 'Bull' : 'Bear'}):${o.Bottom}-${o.Top}`);
    }
    return `${tf}: ${p.join(', ')}`;
  }).join('\n')}

RECOMMENDED ANCHORS (Conditional):
${smcAnchors ? `Note: These anchors are pre-calculated ONLY for a ${smcAnchors.side || 'undetermined'} direction.` : ''}
- Recommended SL: ${smcAnchors?.sl ?? 'ATR-based'}
- Recommended TP: ${smcAnchors?.tp ?? 'ATR-based'}
- Rationale: ${smcAnchors?.rationale ?? 'N/A'}
${smcAnchors?.side ? `CRITICAL RULE: If your analysis concludes a different bias than ${smcAnchors.side}, YOU MUST DISCARD THESE ANCHORS and manually select suitable PriceMap / SMC levels for your chosen bias.` : ''}

PAST LESSONS:
${pastLessonsContext || 'N/A'}

TASK: 
Return JSON with valid SL/TP. Use Anchors unless you see a better SMC level.
If BULL: SL < Price < TP. If BEAR: TP < Price < SL.

REQUIRED JSON FORMAT:
{
  "symbol": "${symbol}",
  "bias": "BULL|BEAR|NEUTRAL",
  "suggestedSL": ${smcAnchors?.sl || 0.0},
  "suggestedTP": ${smcAnchors?.tp || 0.0},
  "confluence": 85,
  "confidence": 80,
  "regime": "TRENDING",
  "suggestedStrategy": "TREND_FOLLOW",
  "narrative": "Price retesting H4 Bullish OB with FVG support.",
  "narrative_th": "ราคาทดสอบแนวรับ OB ระดับ H4 พร้อมแรงหนุนจาก FVG",
  "keyLevels": { "support": [], "resistance": [] },
  "correlations": []
}
`;

    let aiData: any = null;
    let modelLabel = 'unknown';
    try {
      const result = await generateForRole(
        { cfg: config, role: 'analyst' }, 
        userPrompt, 
        { 
          systemPrompt,
          temperature: 0.2, 
          jsonMode: true,
          timeoutMs: 100000,
          maxOutputTokens: 1024 
        }
      );
      modelLabel = result.modelUsed;
      aiData = parseLlmJson(result.text, { allowEmpty: false });
    } catch (error: any) {
      if (modelLabel && modelLabel !== 'unknown') {
        modelRankerService.recordContentFailure(modelLabel, 'openrouter', 'analyst');
      }
      const errLabel = error instanceof LlmJsonError
        ? `parse failed (${error.message}) raw=${error.raw.slice(0, 160)}`
        : (error?.message || error);
      console.warn(`[AnalystAgent] generate failed (Model: ${modelLabel}): ${errLabel}`);
      throw new Error(`AnalystAgent (${modelLabel}): ${errLabel}`);
    }

    // 2026-05-02 Phase 3.3 — Post-validation ของ SL/TP direction ที่ source
    //   ถ้า model ส่ง SL/TP ผิดด้าน (เช่น BULL แต่ SL > entry) → null ทิ้ง
    //   เพื่อให้ slTpAgent คำนวณใหม่ + บันทึก content fail เป็น early signal
    //   (ไม่ต้องรอให้ RiskOfficer veto ทีหลัง)
    const finalBias = (aiData.bias || primary.bias || '').toUpperCase();
    const livePrice = currentPrice ?? (primary as any)?.currentPrice ?? (primary as any)?.entry ?? 0;
    
    // 2026-05-04: Robust number extraction for weak models
    const extractNum = (val: any): number | null => {
      if (typeof val === 'number') return val;
      if (typeof val === 'string') {
        const cleaned = val.replace(/,/g, '');
        const match = cleaned.match(/[-+]?[0-9]*\.?[0-9]+/);
        if (match) return parseFloat(match[0]);
      }
      return null;
    };

    let cleanSL = extractNum(aiData.suggestedSL);
    let cleanTP = extractNum(aiData.suggestedTP);
    
    if (cleanSL != null && cleanSL <= 0) cleanSL = null;
    if (cleanTP != null && cleanTP <= 0) cleanTP = null;

    if (livePrice > 0 && (finalBias === 'BULL' || finalBias === 'BEAR')) {
      const isBull = finalBias === 'BULL';
      const slBad = cleanSL != null && (isBull ? cleanSL >= livePrice : cleanSL <= livePrice);
      const tpBad = cleanTP != null && (isBull ? cleanTP <= livePrice : cleanTP >= livePrice);
      if (slBad || tpBad) {
        console.warn(`[AnalystAgent] ${modelLabel} wrong-direction at source: bias=${finalBias} entry=${livePrice} SL=${cleanSL} TP=${cleanTP} — nullifying for slTpAgent fallback`);
        modelRankerService.recordContentFailure(modelLabel, 'openrouter', 'analyst');
        if (slBad) cleanSL = null;
        if (tpBad) cleanTP = null;
      }
    }

    return {
      symbol,
      regime: primary.regime,
      confluence: primary.confluence,
      fitness: primary.fitness,
      bias: finalBias || primary.bias,
      narrative: `[Model: ${modelLabel}] ${aiData.narrative || 'No narrative provided'}`,
      narrative_th: aiData.narrative_th || '-',
      confidence: aiData.confidence || 50,
      suggestedStrategy: aiData.suggestedStrategy || primary.strategy,
      suggestedSL: cleanSL,
      suggestedTP: cleanTP,
      keyLevels: aiData.keyLevels || { support: [], resistance: [] },
      correlations: aiData.correlations || [],
      modelUsed: modelLabel,
    } as AnalystReport;
  }
}

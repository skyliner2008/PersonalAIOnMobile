/**
 * agentOrchestrator — 4-layer AI reasoning engine.
 *
 * All generation & embedding calls are routed through the provider dispatcher
 * (providers/dispatcher.ts) so the orchestrator is fully provider-agnostic.
 *
 * 2026-04-25 — initial
 * 2026-04-29 — removed GoogleGenerativeAI SDK dependency; all embedding
 *              now goes through embedForRole (dispatcher).
 */

import { vectorStore } from './vectorStore.js';
import { knowledgeGraph } from './knowledgeGraph.js';
import { AutoTradingConfig, AnalysisSummary } from './types.js';
import { resolveModel as resolveAgentModel } from './modelResolver.js';
import { generateForRole, embedForRole } from './providers/dispatcher.js';
import { EMBEDDING_TARGET_DIMS } from './providers/types.js';

class AgentOrchestrator {
  /**
   * 4-layer AI reasoning:
   * Layer 1 - Relational: config rules
   * Layer 2 - Vector:     similar past snapshots (HNSW)
   * Layer 3 - Graph:      strategy-symbol knowledge (SQLite)
   * Layer 4 - Reasoning:  LLM (any provider via dispatcher)
   */
  public async reason(
    config: AutoTradingConfig,
    symbol: string,
    analyses: Record<string, AnalysisSummary>,
    historyContext: string,
    correlations: any[] = [],
    prior?: {
      priorAction?: string;
      priorConfidence?: number;
      priorManagement?: string;
      priorRationale?: string;
      priorStrategy?: string;
    }
  ): Promise<{ text: string; modelUsed: string }> {
    if (!config.apiKey && !config.providerKeys) return { text: 'No API Key provided for AI reasoning.', modelUsed: 'none' };

    // --- Layer 1: Relational ---
    const ruleContext = [
      'Current Strategy Rules:',
      `- Min Confluence: ${config.minConfluence}`,
      `- Risk Per Trade: ${config.risk.riskPerTradePct}%`,
      `- MTF Data Available: ${Object.entries(analyses).map(([tf, a]) => `${tf}(Bias:${a.bias},Conf:${a.confluence.toFixed(1)})`).join(' | ')}`,
      `- Preferred Strategies: ${config.preferredStrategies.length > 0 ? config.preferredStrategies.join(', ') : 'any'}`,
      `- Same-symbol positions allowed: ${config.adaptive?.allowSameSymbolPositions !== false}`,
      `- Over-limit defense allowed: ${config.adaptive?.allowOverLimitDefense !== false}`,
      `- Counter-hedge allowed: ${config.adaptive?.allowCounterHedge !== false}`,
      `- Scale-in recovery allowed: ${config.adaptive?.allowScaleInRecovery !== false}`,
      `- Hedge ratio range: ${config.adaptive?.hedgeRatioMin ?? 0.25}..${config.adaptive?.hedgeRatioMax ?? 1.0}`,
      `- Scale-in ratio range: ${config.adaptive?.scaleInRatioMin ?? 0.2}..${config.adaptive?.scaleInRatioMax ?? 0.75}`,
    ].join('\n');

    // --- Layer 2: Vector ---
    let vectorContext = '';
    try {
      const primary = analyses[config.timeframe || 'H1'] || Object.values(analyses)[0];
      const snapshotText = `Market: ${symbol}, Regime: ${primary.regime}, Bias: ${primary.bias}, Confluence: ${primary.confluence.toFixed(1)}, Fitness: ${primary.fitness.toFixed(1)}`;
      const queryEmbedding = await this.getEmbeddingForConfig(config, snapshotText);
      const isZeroVector = queryEmbedding.every(v => v === 0);
      if (!isZeroVector) {
        const similarSnapshots = vectorStore.search(queryEmbedding, 3, { symbol });
        if (similarSnapshots.length > 0) {
          const snippets = similarSnapshots.map((hit, i) => {
            const meta = hit.metadata;
            if (!meta) return null;
            return `  #${i + 1}: ${meta.symbol}/${meta.timeframe} [${meta.type}] at ${new Date(meta.timestamp).toISOString()} (distance=${hit.distance?.toFixed(3) ?? 'n/a'})`;
          }).filter(Boolean).join('\n');
          vectorContext = `[Vector Context]: ${similarSnapshots.length} similar past market snapshot(s) retrieved:\n${snippets}`;
        }
      } else {
        vectorContext = '[Vector Context]: Embedding unavailable (zero vector) -- skipping similarity search.';
      }
    } catch (vecErr) {
      console.error('[Agent] Vector retrieval failed:', vecErr);
    }

    // --- Layer 3: Graph ---
    let graphContext = `[Graph Context]: No structural knowledge available for ${symbol} yet.`;
    try {
      const symbolNodeId = `symbol_${symbol}`;
      const neighbors = knowledgeGraph.getContext(symbolNodeId, 1);
      if (neighbors.length > 0) {
        const lines = neighbors.map((n: any) => {
          const props = (() => { try { return JSON.parse(n.properties_json || '{}'); } catch { return {}; } })();
          const winRateStr = props.winRate !== undefined ? ` [winRate=${(props.winRate * 100).toFixed(1)}%]` : '';
          return `  - ${n.relation} -> ${n.label} (${n.type})${winRateStr}`;
        });
        graphContext = `[Graph Context]: Structural knowledge for ${symbol}:\n${lines.join('\n')}`;
      }
    } catch (graphErr) {
      console.error('[Agent] Graph retrieval failed:', graphErr);
    }

    // --- Layer 4: Reasoning ---
    const systemPrompt = config.agentPrompt || [
      'You are an AI Trading Agent with a strict operating playbook.',
      'You must analyze market regime, current exposure, unrealized PnL, and risk before deciding.',
      'If positions are already open, think in this order: protect capital, improve structure, then seek profit.',
      'You may recommend HOLD, REDUCE, CLOSE, HEDGE, SCALE_IN, BREAK_EVEN, or TRAIL when appropriate.',
      'Act like a professional portfolio trader: defend first, average only with trend support, hedge partially before full reversal, and close decisively when risk is no longer justified.',
      'When at position limit (indicated in CONTEXT), you must shift focus to harvesting profit or defending existing trades. Skip new entries.',
      'Prefer structured, repeatable reasoning over intuition.'
    ].join(' ');

    let prompt: string;
    if (prior?.priorAction) {
      const mtfDigest = Object.entries(analyses)
        .map(([tf, data]) => `${tf}:${data.bias}/${data.regime}/c=${data.confluence.toFixed(0)}`)
        .join(' | ');
      const corrDigest = correlations.length > 0
        ? correlations.slice(0, 3).map(c => c.evidence).join('; ')
        : 'none';
      prompt = [
        `You are a trading risk reviewer for ${symbol}. A rule engine has produced a PRIOR decision.`,
        `Your job: CONFIRM, ADJUST, or OVERRIDE within 1 JSON response. Be decisive.`,
        '',
        `PRIOR: action=${prior.priorAction} mgmt=${prior.priorManagement ?? 'HOLD'} conf=${prior.priorConfidence ?? 0} strategy=${prior.priorStrategy ?? 'n/a'}`,
        `PRIOR_REASON: ${prior.priorRationale ?? ''}`,
        '',
        `MTF: ${mtfDigest}`,
        `CORR: ${corrDigest}`,
        `POSITIONS: ${historyContext.slice(0, 300)}`,
        '',
        'Rules:',
        '- If you agree with PRIOR, echo it with short rationale.',
        '- If price/structure clearly contradicts PRIOR, override to SKIP or opposite side.',
        '- management=HEDGE only when cluster heat >= 0.6R and opposite side makes sense.',
        '- management=SCALE_IN only when cluster is winning AND trend is with us.',
        '- management=CLOSE when hedged and near net breakeven.',
        '- Counter-trend H4 is OK with size_fraction <= 0.35.',
        '',
        'Respond JSON only:',
        '{"action":"BUY|SELL|SKIP","management":"HOLD|REDUCE|CLOSE|HEDGE|SCALE_IN|BREAK_EVEN|TRAIL","size_fraction":0.5,"timeframe":"M5|M15|H1","rationale":"short EN","rationale_th":"สั้นๆ","confidence":70,"target_ticket":null}',
      ].join('\n');
    } else {
      prompt = [
        systemPrompt,
        '',
        'CONTEXT:', ruleContext,
        '', vectorContext,
        '', graphContext,
        '',
        `Historical Context: ${historyContext}`,
        '',
        'CROSS-ASSET CORRELATIONS:',
        correlations.length > 0 ? correlations.map(c => `- ${c.evidence}`).join('\n') : 'No correlation data available.',
        '',
        'MARKET DATA:',
        Object.entries(analyses).map(([tf, data]) => `[${tf}]: ${data.rationale}`).join('\n'),
        '',
        'Decide: action (BUY/SELL/SKIP), management, size_fraction, rationale_th, confidence (0-100), timeframe.',
        'Respond JSON only:',
        '{ "action": "BUY|SELL|SKIP", "management": "HOLD|REDUCE|CLOSE|HEDGE|SCALE_IN|BREAK_EVEN|TRAIL", "size_fraction": 0.35, "target_ticket": 123456, "timeframe": "M15", "rationale": "short EN", "rationale_th": "สั้นๆ", "confidence": 0 }',
      ].join('\n');
    }

    try {
      const result = await generateForRole({ cfg: config, role: 'reasoning' }, prompt, { temperature: 0.3 });
      return { text: result.text, modelUsed: result.modelUsed };
    } catch (error) {
      console.error('[Agent] Reasoning failed:', error);
      return { 
        text: JSON.stringify({ action: 'SKIP', rationale: `AI Error: ${String(error)}`, confidence: 0 }),
        modelUsed: 'error'
      };
    }
  }

  /**
   * Get a 768-dim embedding using the user's configured embedding provider.
   * This is the primary embedding method — routes through the provider dispatcher.
   */
  public async getEmbeddingForConfig(config: AutoTradingConfig, text: string): Promise<number[]> {
    try {
      const result = await embedForRole({ cfg: config, role: 'embedding' }, text);
      return result.vector;
    } catch (err: any) {
      console.warn('[Agent] embedForRole failed, returning zero vector:', err?.message || err);
      return new Array(EMBEDDING_TARGET_DIMS).fill(0);
    }
  }

  /**
   * @deprecated Use `getEmbeddingForConfig(config, text)` instead.
   * Kept for backward compatibility — will be removed after all callers migrate.
   */
  public async getEmbedding(apiKey: string, text: string, _preferredModel?: string): Promise<number[]> {
    // Build a minimal config so we can route through the dispatcher.
    // The dispatcher will resolve credentials from the config's providerKeys.
    const minimalConfig: Partial<AutoTradingConfig> = { apiKey };
    try {
      const result = await embedForRole({ cfg: minimalConfig as AutoTradingConfig, role: 'embedding' }, text);
      return result.vector;
    } catch (err: any) {
      console.warn('[Agent] Legacy getEmbedding failed, returning zero vector:', err?.message || err);
      return new Array(EMBEDDING_TARGET_DIMS).fill(0);
    }
  }
}

export const agentOrchestrator = new AgentOrchestrator();

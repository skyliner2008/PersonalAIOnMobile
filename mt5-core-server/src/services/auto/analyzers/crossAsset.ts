import { Bias } from '../types.js';

export interface CorrelationContext {
    symbol: string;
    bias: Bias;
    strength: number; // 0-100
    evidence: string;
}

/**
 * Cross-Asset Correlation Analyzer
 * Analyzes relationship between Gold and other macro assets like DXY, Yields, and SPX.
 */
export class CrossAssetAnalyzer {
    /**
     * Analyze correlation bias for XAUUSD.
     * Logic: 
     * - DXY Bullish -> Gold Bearish (Inverted)
     * - US10Y Bullish -> Gold Bearish (Inverted)
     * - SPX Bullish -> Gold Bearish (Safe-haven flow out - sometimes)
     */
    public analyzeGoldBias(
        dxyAnalysis?: { bias: Bias, confluence: number },
        yieldsAnalysis?: { bias: Bias, confluence: number },
        spxAnalysis?: { bias: Bias, confluence: number }
    ): CorrelationContext[] {
        const contexts: CorrelationContext[] = [];

        // DXY Impact (Strong Inverse)
        if (dxyAnalysis && dxyAnalysis.bias !== 'NEUTRAL') {
            const goldBias: Bias = dxyAnalysis.bias === 'BULL' ? 'BEAR' : 'BULL';
            contexts.push({
                symbol: 'DXY',
                bias: goldBias,
                strength: dxyAnalysis.confluence,
                evidence: `DXY is ${dxyAnalysis.bias} (${dxyAnalysis.confluence}%) -> Inverted Gold Bias`
            });
        }

        // US10Y Yields Impact (Inverse)
        if (yieldsAnalysis && yieldsAnalysis.bias !== 'NEUTRAL') {
            const goldBias: Bias = yieldsAnalysis.bias === 'BULL' ? 'BEAR' : 'BULL';
            contexts.push({
                symbol: 'US10Y',
                bias: goldBias,
                strength: yieldsAnalysis.confluence * 0.8, // Slightly lower weight than DXY
                evidence: `US10Y is ${yieldsAnalysis.bias} (${yieldsAnalysis.confluence}%) -> Yields up, Gold down`
            });
        }

        // SPX Impact (Mixed/Safe-haven flow)
        if (spxAnalysis && spxAnalysis.bias !== 'NEUTRAL') {
            const goldBias: Bias = spxAnalysis.bias === 'BULL' ? 'BEAR' : 'BULL';
            contexts.push({
                symbol: 'SPX',
                bias: goldBias,
                strength: spxAnalysis.confluence * 0.5, // Lower weight, safe-haven context
                evidence: `SPX is ${spxAnalysis.bias} -> Risk-on sentiment impact`
            });
        }

        return contexts;
    }

    /**
     * Synthesize multiple correlations into a single Bias.
     */
    public getSyntheticBias(contexts: CorrelationContext[]): { bias: Bias; confluence: number; evidence: string } {
        if (contexts.length === 0) return { bias: 'NEUTRAL', confluence: 0, evidence: 'no correlation data' };

        let bullScore = 0;
        let bearScore = 0;

        for (const ctx of contexts) {
            if (ctx.bias === 'BULL') bullScore += ctx.strength;
            else if (ctx.bias === 'BEAR') bearScore += ctx.strength;
        }

        const total = bullScore + bearScore;
        if (total === 0) return { bias: 'NEUTRAL', confluence: 0, evidence: 'neutral correlations' };

        const bias: Bias = bullScore > bearScore ? 'BULL' : bearScore > bullScore ? 'BEAR' : 'NEUTRAL';
        const confluence = Math.round((Math.max(bullScore, bearScore) / total) * 100);
        const evidence = contexts.map(c => `${c.symbol}:${c.bias}(${Math.round(c.strength)})`).join(', ');

        return { bias, confluence, evidence };
    }
}

export const crossAssetAnalyzer = new CrossAssetAnalyzer();

import { Bias, MarketRegime, StrategyType, AnalysisSummary } from '../types.js';

export interface AnalystReport {
    symbol: string;
    bias: Bias;
    regime: MarketRegime;
    confluence: number;
    fitness: number;
    narrative: string;
    narrative_th: string;
    confidence: number;
    suggestedStrategy: StrategyType;
    suggestedSL: number | null;
    suggestedTP: number | null;
    keyLevels: {
        support: number[];
        resistance: number[];
    };
    correlations: string[];
    modelUsed?: string;
}

export interface RiskApproval {
    approved: boolean;
    reason: string;
    reason_th?: string;
    adjustedVolume: number | null;
    action: 'BUY' | 'SELL' | 'SKIP';
    management: string; // HOLD, REDUCE, etc.
    targetTicket?: number;
    sl: number | null;
    tp: number | null;
    allowOverLimitDefense?: boolean;
    riskNote: string;
    riskNote_th?: string;
    modelUsed?: string;
}

export interface OrderPlan {
    type: 'MARKET' | 'LIMIT' | 'STOP';
    price: number | null;
    volume: number;
    symbol: string;
    side: 'BUY' | 'SELL';
    sl: number | null;
    tp: number | null;
    comment: string;
    fillStrategy: 'AGGRESSIVE' | 'PATIENT';
    modelUsed?: string;
}

export interface TradeLesson {
    ticket: number;
    outcome: string;
    profitR: number;
    lesson: string;
    tags: string[];
    wasGoodDecision: boolean;
    suggestedPromptDelta?: string;
}

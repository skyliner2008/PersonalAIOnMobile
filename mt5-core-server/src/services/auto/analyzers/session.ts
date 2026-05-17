import { Bias, StrategyType } from '../types.js';

export type MarketSession = 'ASIAN' | 'LONDON' | 'NEWYORK' | 'LONDON_NY_OVERLAP' | 'QUIET';

export interface SessionContext {
    session: MarketSession;
    isHighVolatilityTime: boolean;
    recommendedStrategies: StrategyType[];
    sessionNote: string;
}

/**
 * Market Session Analyzer
 * Provides context about current global market hours and volatility expectations.
 */
export class SessionAnalyzer {
    /**
     * Get the current market session based on UTC time.
     */
    public getCurrentSession(): SessionContext {
        const now = new Date();
        const utcHour = now.getUTCHours();
        const utcMin = now.getUTCMinutes();
        const time = utcHour + utcMin / 60;

        // Asian: 00:00 - 09:00 UTC
        // London: 08:00 - 17:00 UTC
        // NY: 13:00 - 22:00 UTC
        
        let session: MarketSession = 'QUIET';
        let isHighVolatilityTime = false;
        let recommendedStrategies: StrategyType[] = ['MEAN_REVERSION', 'RANGE'];
        let sessionNote = 'Market is in a relatively quiet period.';

        if (time >= 13 && time <= 17) {
            session = 'LONDON_NY_OVERLAP';
            isHighVolatilityTime = true;
            recommendedStrategies = ['BREAKOUT', 'TREND_FOLLOW', 'SWING'];
            sessionNote = 'London & NY Overlap: Peak volatility and liquidity. Good for breakouts.';
        } else if (time >= 8 && time < 13) {
            session = 'LONDON';
            isHighVolatilityTime = true;
            recommendedStrategies = ['TREND_FOLLOW', 'BREAKOUT'];
            sessionNote = 'London Session: High liquidity. Trending moves common.';
        } else if (time >= 17 && time <= 22) {
            session = 'NEWYORK';
            isHighVolatilityTime = true;
            recommendedStrategies = ['TREND_FOLLOW', 'MEAN_REVERSION'];
            sessionNote = 'NY Session: Significant volatility. Watch for reversals in late session.';
        } else if (time >= 0 && time < 8) {
            session = 'ASIAN';
            isHighVolatilityTime = false;
            recommendedStrategies = ['SCALPING', 'RANGE', 'MEAN_REVERSION'];
            sessionNote = 'Asian Session: Lower volatility. Range-bound strategies preferred.';
        } else {
            session = 'QUIET';
            isHighVolatilityTime = false;
            recommendedStrategies = ['HOLD_CASH'];
            sessionNote = 'Late NY / Pre-Asian: Very low liquidity. Trading not recommended.';
        }

        return {
            session,
            isHighVolatilityTime,
            recommendedStrategies,
            sessionNote
        };
    }

    /**
     * Adjust analysis fitness based on session recommendations.
     */
    public adjustFitness(strategy: StrategyType, context: SessionContext, currentFitness: number): number {
        if (context.recommendedStrategies.includes(strategy)) {
            return Math.min(100, currentFitness + 10); // Boost recommended strategies
        }

        if (context.session === 'QUIET') {
            return currentFitness * 0.5; // Penalize trading in quiet hours
        }

        return currentFitness;
    }

    /**
     * Session-based volume multiplier. Used to throttle position sizing during
     * low-liquidity sessions so the engine doesn't take full-risk trades at
     * 02:00 UTC when spread blows up and news-driven reversals are common.
     *
     *  - LONDON_NY_OVERLAP → 1.00 (peak liquidity, full size)
     *  - LONDON / NEWYORK  → 1.00 (healthy liquidity, full size)
     *  - ASIAN             → 0.50 (thin liquidity, half size)
     *  - QUIET             → 0.00 (refuse — caller should block or scale to min-lot)
     */
    public getVolumeMultiplier(context: SessionContext): number {
        switch (context.session) {
            case 'LONDON_NY_OVERLAP':
            case 'LONDON':
            case 'NEWYORK':
                return 1.0;
            case 'ASIAN':
                return 0.5;
            case 'QUIET':
            default:
                return 1.0;
        }
    }
}

export const sessionAnalyzer = new SessionAnalyzer();

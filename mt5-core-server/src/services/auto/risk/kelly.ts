import { StrategyStat } from '../types.js';

/**
 * Kelly Criterion Calculator
 * Calculates the optimal fraction of equity to risk on a trade based on historical performance.
 * Formula: K% = W - [(1 - W) / R]
 * where W is win probability and R is win/loss ratio (avg win / avg loss).
 */
export class KellySizer {
    /**
     * Compute Kelly fraction for a strategy based on its statistics.
     * We use a "Half-Kelly" approach (multiply by 0.5) to provide a margin of safety
     * and reduce volatility, which is standard professional practice.
     */
    public static computeFraction(stat: StrategyStat | null, fallbackRisk: number): number {
        if (!stat || stat.total < 10) return fallbackRisk; // Need at least 10 trades for statistical significance

        const w = stat.winRate;
        const r = stat.avgR > 0 ? stat.avgR : 1.0; // Expected R-multiple

        if (w <= 0) return fallbackRisk;

        // Standard Kelly formula
        // Note: In our system, R is the "reward to risk" multiple.
        const k = w - ((1 - w) / r);

        // Limit to reasonable range and apply "Fractional Kelly" (Half-Kelly)
        const halfKelly = k * 0.5;
        
        // Clamp between min-risk (0.1%) and max-risk (2.5%)
        const clamped = Math.max(0.001, Math.min(0.025, halfKelly));
        
        return isFinite(clamped) && clamped > 0 ? clamped : fallbackRisk;
    }
}

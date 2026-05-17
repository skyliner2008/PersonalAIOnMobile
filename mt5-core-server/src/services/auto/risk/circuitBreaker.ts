import { AccountSnapshot, AutoTradingConfig } from '../types.js';

/**
 * Drawdown Circuit Breaker
 * Protects the account from catastrophic losses by pausing or stopping the engine
 * when specific drawdown thresholds are breached.
 */
export class CircuitBreaker {
    /**
     * Check if the circuit is tripped.
     * Returns a status and a reason if tripped.
     */
    public static check(account: AccountSnapshot, config: AutoTradingConfig): { tripped: boolean; reason: string; action: 'PAUSE' | 'STOP' | 'NONE' } {
        const balance = account.balance;
        const equity = account.equity;
        const dailyLoss = account.todayPnL; // Assuming todayPnL is available in snapshot
        
        const dailyLossPct = balance > 0 ? (Math.abs(Math.min(dailyLoss, 0)) / balance) * 100 : 0;
        const totalDrawdownPct = balance > 0 ? ((balance - equity) / balance) * 100 : 0;

        // 1. Daily Loss Limit (Soft Trip -> Pause 24h)
        const maxDaily = config.risk.maxDailyLossPct || 3.0;
        if (dailyLossPct >= maxDaily) {
            return { 
                tripped: true, 
                reason: `Daily loss limit reached (${dailyLossPct.toFixed(2)}% >= ${maxDaily}%)`, 
                action: 'PAUSE' 
            };
        }

        // 2. Max Drawdown Limit (Hard Trip -> STOP manual review)
        const maxDD = config.risk.maxDrawdownPct || 5.0;
        if (totalDrawdownPct >= maxDD) {
            return { 
                tripped: true, 
                reason: `Maximum drawdown reached (${totalDrawdownPct.toFixed(2)}% >= ${maxDD}%)`, 
                action: 'STOP' 
            };
        }

        return { tripped: false, reason: '', action: 'NONE' };
    }
}

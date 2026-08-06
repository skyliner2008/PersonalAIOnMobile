import { traceStorage } from '../../logger.js';
import { isMarketOpen } from '../helpers/marketHelpers.js';
import { 
  atLog, 
  atWarn, 
  nowIso, 
  round2, 
  clamp, 
  round1 
} from '../utils.js';
import { resolveProviderCredentials } from '../modelResolver.js';
import { modelRankerService } from './ModelRankerService.js';
import { persistenceService } from './PersistenceService.js';
import { agentOrchestrator } from '../agentOrchestrator.js';
import type { 
  JournalRow, 
  StrategyStat, 
  AnalyzerStat 
} from '../types.js';
import type { AutoTradingService } from '../../autoTradingService.js';

export class LearningEngine {
  public static async runLearn(context: AutoTradingService): Promise<void> {
    if (context.learnBusy) return;
    context.learnBusy = true;
    
    // Generate a unique trace ID for this learning cycle
    const traceId = `learn-${Date.now().toString().slice(-6)}`;
    
    await traceStorage.run({ traceId }, async () => {
      try {
        // Market Guard: AI learning can still run while market is closed (offline consolidation),
        // but we prefer to skip if the whole system is in Sleep Mode to save resources.
        const marketOpen = await isMarketOpen(context.config);
        if (!marketOpen && context.state.phase === 'SLEEPING') {
          return;
        }
        
        // eslint-disable-next-line no-console
        atLog('[AutoEngine] Running AI Learning Phase...');
        context.state = { ...context.state, phase: 'LEARNING', message: 'server learning from journal' };
        
        // 2026-04-30: Sync newly discovered free models for Smart Fallback
        // 2026-05-01 Phase 2: เปลี่ยนเป็น await + log diff (added/removed/kept)
        //   เพื่อ verify ว่า sync สำเร็จจริง (เคย fail silent ทำให้ DB เห็นแค่ 4 ตัว)
        const orKeys = resolveProviderCredentials(context.config, 'openrouter');
        try {
          const r = await modelRankerService.syncDiscoveredModels(orKeys.apiKey, orKeys.baseUrl);
          atLog(`[AutoEngine] OpenRouter sync done: total=${r.totalFree} added=${r.added} removed=${r.removed} kept=${r.kept}`);
        } catch (err) {
          atWarn(`[AutoEngine] Model sync failed: ${err}`);
        }
        // Phase 2.5 — Schedule daily refresh ของ free pool (OpenRouter เปลี่ยนรายชื่อบ่อย)
        context.scheduleDailyModelSync();

        const rows = await persistenceService.getLearningJournal(400);
        const dataQuality = persistenceService.getLearningQualitySummary();
        const blockStats = persistenceService.getDecisionBlockStats(1000);
        const managementStats = persistenceService.getManagementLearningStats(1000);
        const strategyMap = new Map<string, JournalRow[]>();
        const analyzerMap = new Map<string, JournalRow[]>();

        for (const row of rows.filter((it) => ['WIN', 'LOSS', 'BE'].includes(it.outcome))) {
          const sRows = strategyMap.get(row.strategy) || [];
          sRows.push(row);
          strategyMap.set(row.strategy, sRows);

          const aRows = analyzerMap.get(row.analyzersUsed) || [];
          aRows.push(row);
          analyzerMap.set(row.analyzersUsed, aRows);
        }

        const strategyStats: StrategyStat[] = Array.from(strategyMap.entries()).map(([strategy, list]) => {
          const wins = list.filter((it) => it.outcome === 'WIN').length;
          const losses = list.filter((it) => it.outcome === 'LOSS').length;
          const totalProfit = list.reduce((sum, it) => sum + (it.profit ?? 0), 0);
          const avgR = list.reduce((sum, it) => sum + (it.profitR ?? 0), 0) / Math.max(list.length, 1);
          return { strategy, total: list.length, wins, losses, winRate: wins / Math.max(list.length, 1), totalProfit, avgR: round2(avgR) };
        });

        const analyzerStats: AnalyzerStat[] = Array.from(analyzerMap.entries()).map(([analyzers, list]) => {
          const wins = list.filter((it) => it.outcome === 'WIN').length;
          const totalProfit = list.reduce((sum, it) => sum + (it.profit ?? 0), 0);
          return { analyzers, total: list.length, wins, winRate: wins / Math.max(list.length, 1), totalProfit };
        });

        const totalDeals = strategyStats.reduce((sum, it) => sum + it.total, 0);
        const wins = strategyStats.reduce((sum, it) => sum + it.wins, 0);
        const losses = strategyStats.reduce((sum, it) => sum + it.losses, 0);
        const totalProfit = strategyStats.reduce((sum, it) => sum + it.totalProfit, 0);
        const bestStrategy = strategyStats.slice().sort((a, b) => b.winRate - a.winRate || b.totalProfit - a.totalProfit)[0] ?? null;
        const worstStrategy = strategyStats.slice().sort((a, b) => a.winRate - b.winRate || a.totalProfit - b.totalProfit)[0] ?? null;
        const bestAnalyzer = analyzerStats.slice().sort((a, b) => b.winRate - a.winRate || b.totalProfit - a.totalProfit)[0] ?? null;
        const worstAnalyzer = analyzerStats.slice().sort((a, b) => a.winRate - b.winRate || a.totalProfit - b.totalProfit)[0] ?? null;
        const winRate = wins / Math.max(totalDeals, 1);

        context.learnSummary = {
          totalDeals,
          eligibleDeals: dataQuality.eligible,
          excludedDeals: dataQuality.excluded,
          wins,
          losses,
          winRate,
          totalProfit: round2(totalProfit),
          dataQuality,
          blockStats,
          managementStats,
          bestStrategy: bestStrategy?.strategy ?? null,
          worstStrategy: worstStrategy?.strategy ?? null,
          bestAnalyzerCombo: bestAnalyzer?.analyzers ?? null,
          worstAnalyzerCombo: worstAnalyzer?.analyzers ?? null,
          strategyStats,
          analyzerStats,
          aiNote: '',
          atIso: nowIso(),
        };

        if (context.config.apiKey) {
          const prompt = [
            'Analyze these clean, learning-eligible trade results and management actions:',
            `Performance: ${JSON.stringify(strategyStats)}`,
            `Analyzers: ${JSON.stringify(analyzerStats)}`,
            `Data Quality: ${JSON.stringify(dataQuality)}`,
            `Blocked Decision Mix: ${JSON.stringify(blockStats)}`,
            `Management Actions: ${JSON.stringify(managementStats)}`,
            '',
            'Ignore excluded/legacy rows. Provide a short professional trading floor insight and suggest 1-2 adjustments for the pro-trader agent.',
          ].join('\n');
          try {
            // Use reasoning engine for meta-learning
            const aiNote = await agentOrchestrator.reason(
              context.config,
              'GLOBAL',
              { GLOBAL: { regime: 'UNKNOWN', bias: 'NEUTRAL', confluence: 0, fitness: 0, strategy: 'HOLD_CASH', rationale: prompt, signals: [], atr: null, rsi: null, sma20: null, sma50: null, ema20: null } },
              'Periodic learning'
            );
            context.learnSummary.aiNote = aiNote.text;
          } catch (err) {
            context.learnSummary.aiNote = 'AI Analysis unavailable.';
          }
        } else {
          context.learnSummary.aiNote = totalDeals === 0
            ? 'No closed trades yet on server journal.'
            : winRate < 0.4
              ? 'Server auto trading is in cautious mode. Consider raising confluence or narrowing the watchlist.'
              : 'Server auto trading is stable enough for continued paper validation before wider live usage.';
        }

        if (context.config.autoTune && totalDeals >= 8) {
          const blacklist = new Set(context.config.symbolBlacklist);
          const symbolRows = new Map<string, JournalRow[]>();
          for (const row of rows.filter((it) => ['WIN', 'LOSS', 'BE'].includes(it.outcome))) {
            const list = symbolRows.get(row.symbol) || [];
            list.push(row);
            symbolRows.set(row.symbol, list);
          }
          for (const [symbol, list] of symbolRows.entries()) {
            const lossesOnly = list.length >= context.config.autoBlacklistAfterLosses && list.every((it) => it.outcome === 'LOSS');
            if (lossesOnly) blacklist.add(symbol.toUpperCase());
          }
          const newMinConfluence =
            winRate < 0.38 ? clamp(context.config.minConfluence + 3, 30, 90) :
            winRate > 0.6 ? clamp(context.config.minConfluence - 1.5, 30, 90) :
            context.config.minConfluence;
          context.config = {
            ...context.config,
            minConfluence: round1(newMinConfluence),
            symbolBlacklist: Array.from(blacklist),
          };
          context.persistConfig();
        }

        context.state = { ...context.state, phase: 'IDLE', message: 'server learn done' };
        context.persistRuntime();
      } catch (error) {
        context.state = { ...context.state, phase: 'IDLE', message: `learn failed: ${String((error as Error)?.message || error)}` };
        context.persistRuntime();
      } finally {
        context.learnBusy = false;
      }
    });
  }
}

import { getDb } from '../../db.js';
import { atLog, atWarn, atError, getLogTime } from './utils.js';
import { knowledgeGraph } from './knowledgeGraph.js';
import { vectorStore } from './vectorStore.js';
import { agentOrchestrator } from './agentOrchestrator.js';
import { AutoTradingConfig } from './types.js';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const WIKI_PATH = path.join(__dirname, '../../../../.obsidian-wiki/07_Trading_Intelligence/Weekly_Insights.md');

class MemoryConsolidationService {
  private db = getDb();

  /**
   * Run the sleep cycle: Consolidate memory, prune old data, and update knowledge graph.
   * Accepts full config so reasoning + embedding use the configured provider.
   */
  public async runSleepCycle(config: AutoTradingConfig) {
    // eslint-disable-next-line no-console
    atLog('[MemoryConsolidation] 💤 Starting Sleep Cycle (Memory Consolidation)...');

    // 1. Summarize last week's trades
    await this.consolidateTradeJournal(config);

    // 2. Update Knowledge Graph weights based on performance
    this.updateGraphWeights();

    // 3. Prune old or irrelevant vector embeddings
    this.pruneVectorStore();

    // eslint-disable-next-line no-console
    atLog('[MemoryConsolidation] ✨ Sleep Cycle complete. Brain is refreshed.');
  }

  private async consolidateTradeJournal(config: AutoTradingConfig) {
    const lastWeek = Date.now() - 7 * 24 * 60 * 60 * 1000;
    const trades = this.db.prepare(
      `SELECT * FROM auto_trading_journal 
       WHERE created_at > ?
         AND outcome IN ('WIN', 'LOSS')
         AND learning_eligible = 1`
    ).all(lastWeek) as any[];

    if (trades.length === 0) return;

    const summary = trades.reduce((acc, trade) => {
      acc[trade.symbol] = acc[trade.symbol] || { wins: 0, losses: 0, profit: 0 };
      if (trade.outcome === 'WIN') acc[trade.symbol].wins++;
      else acc[trade.symbol].losses++;
      acc[trade.symbol].profit += trade.profit || 0;
      return acc;
    }, {});

    const report = [
      `# 🧠 Weekly Insights (${new Date().toLocaleDateString()})`,
      '',
      '## 📊 Performance Summary',
      '| Symbol | Wins | Losses | Net Profit |',
      '| :--- | :---: | :---: | :---: |',
      ...Object.entries(summary).map(([symbol, stat]: any) => 
        `| ${symbol} | ${stat.wins} | ${stat.losses} | $${stat.profit.toFixed(2)} |`
      ),
      '',
      '## 🧠 AI Observations',
    ];

    if (config.apiKey || config.providerKeys) {
        const aiPrompt = `Summarize these trades and provide 3 key lessons for the pro-trader AI agent: ${JSON.stringify(summary)}`;
        try {
            const aiLessons = await agentOrchestrator.reason(config, 'GLOBAL', { rationale: aiPrompt } as any, 'Weekly consolidation');
            report.push(aiLessons.text);
        } catch (err) {
            report.push('AI Lesson generation failed.');
        }
    }

    fs.appendFileSync(WIKI_PATH, report.join('\n') + '\n\n---\n\n');
  }

  private updateGraphWeights() {
    const stats = this.db.prepare(
      `SELECT symbol, strategy, outcome, COUNT(*) as count 
       FROM auto_trading_journal 
       WHERE outcome IN ('WIN', 'LOSS')
         AND learning_eligible = 1
       GROUP BY symbol, strategy, outcome`
    ).all() as any[];

    for (const stat of stats) {
      const weight = stat.outcome === 'WIN' ? 1.1 : 0.9;
      knowledgeGraph.link(`symbol_${stat.symbol}`, `strat_${stat.strategy}`, 'WORKS_WITH', weight);
    }
  }

  private pruneVectorStore() {
    // Actual pruning: remove snapshots older than 14 days to keep "Subconscious" fresh
    const fourteenDaysMs = 14 * 24 * 60 * 60 * 1000;
    vectorStore.prune(fourteenDaysMs);
  }
}

export const memoryConsolidationService = new MemoryConsolidationService();

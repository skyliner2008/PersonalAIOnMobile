import { matchHistoryDeal } from '../journal.js';
import { modelRankerService } from '../core/ModelRankerService.js';
import { vectorStore } from '../vectorStore.js';
import { agentOrchestrator } from '../agentOrchestrator.js';
import { broadcast } from '../../mt5RealtimeHub.js';
import { PostMortemAgent } from '../agents/postMortem.js';
import { safeJsonParse, atLog, atWarn, atError } from '../utils.js';
import type { AutoTradingConfig, JournalRow, PositionRow } from '../types.js';

export class ClosedTradeService {
  private postMortemReviewedTickets = new Set<string>();
  private closedDealDeferredLastLogAt = new Map<string, number>();
  private postMortemAgent: PostMortemAgent | null = null;

  /**
   * บันทึก Warning สำหรับดีลที่ปิดแล้วแต่ยังค้นหาข้อมูลจาก Broker ประวัติไม่พบชั่วคราว
   * เพื่อควบคุมอัตราการเขียน Log (Throttle) ไม่ให้ถี่เกินไป (จำกัด 60 วินาทีต่อครั้ง)
   */
  public logDeferredClosedDeal(ticket: number | string, symbol: string, purpose: string): void {
    const key = `${purpose}:${ticket}`;
    const now = Date.now();
    const last = this.closedDealDeferredLastLogAt.get(key) ?? 0;
    if (now - last < 60_000) return;
    this.closedDealDeferredLastLogAt.set(key, now);
    atWarn(`[AutoEngine]  Closed position #${ticket} ${symbol} is no longer open, but no usable closing history deal was found yet. Deferring ${purpose}.`);
  }

  /**
   * ล้างสถานะการรอ Log สำหรับดีลที่ประมวลผลสำเร็จแล้ว
   */
  public clearDeferredLog(ticket: number | string, purpose: string): void {
    const key = `${purpose}:${ticket}`;
    this.closedDealDeferredLastLogAt.delete(key);
  }

  /**
   * ตรวจจับออเดอร์เทรดที่เพิ่งปิดตัวลง เปรียบเทียบกับ openJournal
   * และเรียกใช้งาน AI Post-Mortem Review เพื่อสรุปบทเรียน บันทึกสถิติเรตติ้งโมเดล
   * และจดจำประสบการณ์ลงใน Vector Database ของระบบ
   */
  public async detectAndReviewClosedTrades(
    livePositions: PositionRow[],
    config: AutoTradingConfig,
    openJournal: JournalRow[],
    fetchHistory: (limit: number) => Promise<Record<string, unknown>[]>,
    markManagementOutcome: (
      ticket: number,
      decisionId: string,
      outcome: 'WIN' | 'LOSS' | 'BE',
      profit: number,
      profitR: number | null,
      closeReason: string
    ) => void,
    onJournalUpdated: () => void
  ): Promise<void> {
    const closedRows = openJournal.filter(j => 
        j.outcome === 'OPEN' && 
        j.mt5Ticket !== null && 
        !livePositions.some(p => p.ticket === j.mt5Ticket) &&
        !this.postMortemReviewedTickets.has(String(j.mt5Ticket))
    );

    if (closedRows.length === 0) return;

    const history = await fetchHistory(500).catch((err) => {
      atWarn(`[AutoEngine]  Post-Mortem history fetch failed: ${String((err as Error)?.message || err)}`);
      return [] as Record<string, unknown>[];
    });

    const reviewableRows = closedRows.filter((row) => {
      const deal = matchHistoryDeal(row, history);
      if (!deal) {
        this.logDeferredClosedDeal(row.mt5Ticket!, row.symbol, 'post-mortem');
        return false;
      }
      this.closedDealDeferredLastLogAt.delete(`post-mortem:${row.mt5Ticket}`);
      return true;
    });

    if (reviewableRows.length === 0) return;

    // V23.0 — EA-only mode: bypass Post-Mortem AI review when disabled
    const aiModeEnabled = config.enableAiMode !== false;
    if (!aiModeEnabled) {
      atLog(`[AutoEngine]  EA-Only mode active. Skipping Post-Mortem AI review for ${reviewableRows.length} trade(s).`);
      for (const row of reviewableRows) {
        this.postMortemReviewedTickets.add(String(row.mt5Ticket));
        markManagementOutcome(
            row.mt5Ticket!, 
            row.decisionId, 
            'BE', 
            0, 
            0, 
            'Skipped Post-Mortem AI review (EA-Only mode)'
        );
      }
      onJournalUpdated();
      return;
    }

    atLog(`[AutoEngine]  Detected ${reviewableRows.length} closed trade(s). Triggering Post-Mortem...`);

    if (!this.postMortemAgent) {
      this.postMortemAgent = new PostMortemAgent(config.apiKey || '');
    }

    for (const row of reviewableRows) {
        try {
            const lesson = await this.postMortemAgent.review(row, config);
            
            atLog(`[AutoEngine]  Post-Mortem #${row.mt5Ticket}: ${lesson.outcome} | ${lesson.lesson}`);
            this.postMortemReviewedTickets.add(String(row.mt5Ticket));
            
            markManagementOutcome(
                row.mt5Ticket!, 
                row.decisionId, 
                lesson.outcome as any, 
                0, 
                lesson.profitR, 
                lesson.lesson
            );

            // 2.1 Update Model Performance Statistics
            if (row.modelId) {
                modelRankerService.recordTradeOutcome(row.modelId, lesson.profitR);
            }
            
            // 2.2 V20.0 — Update slTpAgent accuracy score
            try {
              const snap = safeJsonParse<any>(row.marketSnapshot || '{}', {});
              const slTpModelId = snap?.slTpModelId;
              if (slTpModelId) {
                const plannedRrr = row.rrr || 1.5;
                const actualR = lesson.profitR;
                let delta = 0;
                let reason = '';

                if (lesson.outcome === 'WIN') {
                  if (actualR >= plannedRrr * 0.95) {
                    delta = 0.2; // Hit target TP exactly
                    reason = 'TP Bullseye';
                  } else {
                    delta = 0.1; // Closed in profit (partial or early)
                    reason = 'Profit achieved';
                  }
                } else if (lesson.outcome === 'LOSS') {
                  if (lesson.wasGoodDecision) {
                    delta = -0.2; 
                    reason = 'Good entry, but SL hit (Too tight?)';
                  } else {
                    delta = -0.05;
                    reason = 'Bad analysis/entry';
                  }
                } else if (lesson.outcome === 'BE') {
                  delta = 0.05; 
                  reason = 'Capital protected (BE)';
                }

                if (delta !== 0) {
                  modelRankerService.recordSlTpAccuracy(slTpModelId, delta);
                  atLog(`[AutoEngine] SL/TP Accuracy update for "${slTpModelId}": delta=${delta > 0 ? '+' : ''}${delta} | ${reason} (R=${actualR.toFixed(2)}, Target=${plannedRrr})`);
                }
              }
            } catch (err) {
              atWarn(`[AutoEngine] recordSlTpAccuracy failed: ${err}`);
            }

            // 3. Store lesson in episodic memory (vectorStore)
            const lessonText = `Trade #${row.mt5Ticket} (${row.symbol}): ${lesson.lesson} | Outcome: ${lesson.outcome} | R: ${lesson.profitR}`;
            const embedding = await agentOrchestrator.getEmbeddingForConfig(config, lessonText);
            vectorStore.add(embedding, {
                id: `lesson_${Date.now()}_${row.mt5Ticket}`,
                symbol: row.symbol,
                timeframe: row.timeframe,
                type: 'trade_lesson',
                outcome: lesson.outcome,
                profitR: lesson.profitR,
                text: lesson.lesson,
                timestamp: Date.now()
            });

            broadcast({
                type: 'intelligence_alert',
                title: `TRADE POST-MORTEM: ${row.symbol}`,
                message: `Trade #${row.mt5Ticket} closed as ${lesson.outcome}. AI Lesson: ${lesson.lesson}`,
                priority: 'LOW',
                at: Date.now()
            });

        } catch (err) {
            atError(`[AutoEngine] Post-Mortem failed for #${row.mt5Ticket}:`, err);
        }
    }

    // Refresh local journal after marking outcomes
    onJournalUpdated();
  }
}

export const closedTradeService = new ClosedTradeService();

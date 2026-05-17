/**
 * ModelRankerService V20.0 — Per-Agent Scoring, Time-Based Blacklist, Round-Robin Exploration
 *
 * Key improvements over V19.x:
 *  1. Per-agent-role stats in `ai_model_agent_stats` table.
 *     Analyst, RiskOfficer, ExecutionTrader, PostMortem, Reasoning, SlTpAgent
 *     each have independent scores so a bad Analyst doesn't pollute Execution.
 *  2. Improved scoring formula:
 *       score = winRate×35 + successRate×25 + normalizedProfitR×20
 *             + latencyBonus×5 − errorRate×20
 *             − consecutiveFailures×10 − contentFailCalls×3
 *  3. Time-based blacklist (not session-only):
 *       content_fail        → 2 h cooldown
 *       consecutive ≥ 2     → 4 h cooldown
 *       consecutive ≥ 4     → 12 h cooldown
 *  4. Round-robin exploration:
 *       Every EXPLORE_EVERY_N calls, the dispatcher picks an unexplored /
 *       under-tested model (total_calls < MIN_CALLS_EXPLORE, not blacklisted)
 *       so new models get fair evaluation instead of starving.
 *  5. Dashboard: getAllRankings() now returns per-agent grouped results.
 *
 * 2026-05-01 — skyliner.jojo@gmail.com
 */

import { getDb } from '../../../db.js';
import { atLog, atWarn } from '../utils.js';
import { listModelsCached } from '../providers/registry.js';
import type { AgentRole } from '../modelResolver.js';

const db = getDb();

// ─────────────────────────────────────────────────────────────────────────────
// Public types
// ─────────────────────────────────────────────────────────────────────────────

export interface ModelScoreRow {
  model_id: string;
  provider_id: string;
  agent_role: string;
  score: number;
  avg_latency: number;
  success_rate: number;
  profit_r: number;
  total_calls: number;
  win_count: number;
  loss_count: number;
  consecutive_failures: number;
  blacklisted_until: number;
}

export interface AgentLeaderboardRow extends ModelScoreRow {
  rank: number;
  badge: 'gold' | 'silver' | 'bronze' | null;
  isBlacklisted: boolean;
  cooldownRemainingMs: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

// 2026-05-01 Phase 2 — ปรับให้ rotation ครอบคลุมโมเดล 20+ ตัวจาก OpenRouter free pool
//   เดิม min 5/10 + explore_every 5 ทำให้กว่าจะได้ทดสอบครบ pool ใหม่ใช้เวลา ~100 cycles
//   ตอนนี้ลดลงครึ่งหนึ่ง — โมเดลใหม่ได้ chance เร็ว และ leaderboard converge เร็ว
const MIN_CALLS_TO_RANK = 3;   // 5 → 3 (qualify เป็น "ranked" หลัง 3 calls)
const MIN_CALLS_EXPLORE = 5;   // 10 → 5 (ออกจาก "underexplored" หลัง 5 calls)
const EXPLORE_EVERY_N   = 3;   // 5 → 3 (explore โมเดลใหม่ทุก 3 cycles)

/** Blacklist durations in ms
 * 2026-05-01 Phase 2 — ลดเวลาลงมาก เพราะเดิม content fail ครั้งเดียว = 2 ชม.
 *   ทำให้ pool หดเหลือ 1-2 ตัวภายใน session 4 ชม. → ไม่มีโมเดลให้ rotate
 *   หลังลดเวลาแล้ว: failed model จะกลับเข้า rotation ใน 15 นาที-4 ชม. ตาม streak */
const BL_CONTENT_FAIL_MS  = 15 * 60_000;       // 2h → 15min — single parse/format fail
const BL_STREAK_2_MS      =  60 * 60_000;      // 4h → 1h  — 2+ consecutive failures
const BL_STREAK_4_MS      =  4 * 3_600_000;    // 12h → 4h — 4+ consecutive failures
const BL_DEFAULT_MS       = 10 * 60_000;       // 30min → 10min — single hard error

/** Models that cannot do JSON reasoning (OCR / vision / audio / embedding). */
const INCAPABLE_PATTERN =
  /(\b|[-/])(ocr|vl|vision|embed|embedding|whisper|audio|tts|stt|speech|moderation|guard|safety|image|diffusion|sdxl|midjourney)([-/]|\b)/i;

// ─────────────────────────────────────────────────────────────────────────────
// Service
// ─────────────────────────────────────────────────────────────────────────────

class ModelRankerService {
  /** Per-role call counter; resets after exploration fires. */
  private exploreCounter = new Map<string, number>();
  /** 2026-05-02 Phase 5.2 — Per-role timestamp ครั้งล่าสุดที่ force-explore
   *  ป้องกันไม่ให้ force ติดกันทุก cycle (ทำให้เปลือง quota + pipeline ช้า) */
  private lastForceExploreAt = new Map<string, number>();

  // ── Helpers ────────────────────────────────────────────────────────────────

  private isCapable(modelId: string): boolean {
    if (!modelId) return false;
    return !INCAPABLE_PATTERN.test(modelId);
  }

  private cleanModelId(id: string): string {
    if (!id) return id;
    const parts = id.split(' ');
    if (parts.length > 1 &&
        ['openrouter', 'gemini', 'ollama', 'openai', 'claude'].includes(parts[0])) {
      return parts.slice(1).join(' ');
    }
    return id;
  }

  private blacklistDurationMs(consecutiveFailures: number, isContentFail: boolean): number {
    // 2026-05-04 — Escalating blacklist. Models with extreme failure streaks
    // (e.g. liquid/lfm streak=118, llama-3.3-70b streak=70) were recycled every
    // 4h despite never producing valid output. Now: exponential escalation up to
    // 30-day effective permanent ban.
    if (consecutiveFailures >= 10) return 30 * 24 * 3_600_000;  // 30 days = permanent
    if (consecutiveFailures >= 8)  return 24 * 3_600_000;       // 24h
    if (consecutiveFailures >= 6)  return 12 * 3_600_000;       // 12h
    if (consecutiveFailures >= 4)  return BL_STREAK_4_MS;       // 4h
    if (consecutiveFailures >= 2)  return BL_STREAK_2_MS;       // 1h
    if (isContentFail)             return BL_CONTENT_FAIL_MS;   // 15min
    return BL_DEFAULT_MS;                                       // 10min
  }

  /** True when model is currently in a time-based blacklist window. */
  private isBlacklisted(blacklistedUntil: number): boolean {
    return blacklistedUntil > 0 && Date.now() < blacklistedUntil;
  }

  /** Public check: is a specific model blacklisted for a given role? */
  public isModelBlacklisted(modelId: string, agentRole: AgentRole = 'reasoning'): boolean {
    const cleanId = this.cleanModelId(modelId);
    try {
      const row = db.prepare(
        `SELECT blacklisted_until FROM ai_model_agent_stats WHERE model_id=? AND agent_role=?`
      ).get(cleanId, agentRole) as any;
      return row ? this.isBlacklisted(row.blacklisted_until) : false;
    } catch {
      return false;
    }
  }

  // ── Record API call result ──────────────────────────────────────────────────

  public recordExecution(args: {
    modelId: string;
    providerId: string;
    agentRole?: AgentRole;
    latencyMs: number;
    success: boolean;
    isTimeout: boolean;
    tokens?: number;
  }): void {
    const {
      modelId, providerId,
      agentRole = 'reasoning',
      latencyMs, success, isTimeout,
      tokens = 0,
    } = args;

    const nowMs = Date.now();
    try {
      if (!success) {
        // Get current streak to compute blacklist duration
        const existing = db.prepare(
          `SELECT consecutive_failures FROM ai_model_agent_stats WHERE model_id=? AND agent_role=?`
        ).get(modelId, agentRole) as any;
        const streak = (existing?.consecutive_failures || 0) + 1;
        const blUntil = nowMs + this.blacklistDurationMs(streak, false);

        db.prepare(`
          INSERT INTO ai_model_agent_stats
            (model_id, provider_id, agent_role, total_calls, error_calls, timeout_calls,
             consecutive_failures, avg_latency_ms, total_tokens, blacklisted_until, last_used_at, updated_at)
          VALUES
            (@m, @p, @r, 1, 1, @to, 1, @lat, @tok, @bl, @now, @now)
          ON CONFLICT(model_id, agent_role) DO UPDATE SET
            total_calls          = total_calls + 1,
            error_calls          = error_calls + 1,
            timeout_calls        = timeout_calls + @to,
            consecutive_failures = consecutive_failures + 1,
            avg_latency_ms       = (avg_latency_ms * 0.8) + (@lat * 0.2),
            total_tokens         = total_tokens + @tok,
            blacklisted_until    = @bl,
            last_used_at         = @now,
            updated_at           = @now
        `).run({ m: modelId, p: providerId, r: agentRole, to: isTimeout ? 1 : 0,
                 lat: latencyMs, tok: tokens, bl: blUntil, now: nowMs });

        atLog(`[ModelRanker] ❌ ${agentRole}/${modelId} failed (streak=${streak}) — blacklisted ${Math.round(this.blacklistDurationMs(streak, false)/60_000)}min`);
      } else {
        db.prepare(`
          INSERT INTO ai_model_agent_stats
            (model_id, provider_id, agent_role, total_calls, success_calls,
             consecutive_failures, avg_latency_ms, total_tokens, blacklisted_until, last_used_at, updated_at)
          VALUES
            (@m, @p, @r, 1, 1, 0, @lat, @tok, 0, @now, @now)
          ON CONFLICT(model_id, agent_role) DO UPDATE SET
            total_calls          = total_calls + 1,
            success_calls        = success_calls + 1,
            consecutive_failures = 0,
            blacklisted_until    = 0,
            avg_latency_ms       = (avg_latency_ms * 0.8) + (@lat * 0.2),
            total_tokens         = total_tokens + @tok,
            last_used_at         = @now,
            updated_at           = @now
        `).run({ m: modelId, p: providerId, r: agentRole, lat: latencyMs, tok: tokens, now: nowMs });
      }

      // Keep legacy table in sync for backward compat (dashboard old code)
      this._recordLegacy(args);
    } catch (err) {
      atWarn(`[ModelRanker] recordExecution failed for ${modelId}: ${err}`);
    }
  }

  /** Record content/format failure (JSON parse error, wrong-side SL/TP, etc.) */
  public recordContentFailure(modelId: string, providerId: string, agentRole: AgentRole = 'reasoning'): void {
    if (!modelId) return;
    const cleanId = this.cleanModelId(modelId);
    const nowMs   = Date.now();
    try {
      const existing = db.prepare(
        `SELECT consecutive_failures, content_fail_calls FROM ai_model_agent_stats WHERE model_id=? AND agent_role=?`
      ).get(cleanId, agentRole) as any;
      const streak = (existing?.consecutive_failures || 0) + 1;
      // 2026-05-11: Paid/direct providers (minimax, gemini, openai, claude) get a soft
      // cooldown (max 5 min) instead of the escalating ban ladder. Their failures are
      // typically caused by our own code bugs, not by the model being defective.
      // NOTE: We detect by model ID pattern because most call sites hardcode providerId='openrouter'.
      const isPaidModel = this.isPaidDirectModel(cleanId);
      const rawBl = this.blacklistDurationMs(streak, true);
      const paidSoftCapMs = agentRole === 'slTpAgent' ? 30 * 60_000 : 5 * 60_000;
      const blUntil = nowMs + (isPaidModel ? Math.min(rawBl, paidSoftCapMs) : rawBl);

      db.prepare(`
        INSERT INTO ai_model_agent_stats
          (model_id, provider_id, agent_role, total_calls, content_fail_calls, consecutive_failures,
           blacklisted_until, last_used_at, updated_at)
        VALUES (@m, @p, @r, 1, 1, 1, @bl, @now, @now)
        ON CONFLICT(model_id, agent_role) DO UPDATE SET
          content_fail_calls   = content_fail_calls + 1,
          consecutive_failures = consecutive_failures + 1,
          success_calls        = MAX(0, success_calls - 1),
          error_calls          = error_calls + 1,
          blacklisted_until    = @bl,
          updated_at           = @now
      `).run({ m: cleanId, p: providerId, r: agentRole, bl: blUntil, now: nowMs });

      atWarn(`[ModelRanker] 🚫 ${agentRole}/${cleanId} content fail — blacklisted ${Math.round((isPaidModel ? Math.min(rawBl, paidSoftCapMs) : rawBl)/60_000)}min`);

      // 2026-05-04 Fix #7: Cross-role toxic model detection.
      // If a model has ≥10 content failures across ALL roles combined,
      // it's systematically broken (e.g. liquid/lfm always returns distances).
      // Blacklist it for ALL roles to prevent role-shuffling evasion.
      this.checkCrossRoleToxic(cleanId, providerId, nowMs);

      // Legacy compat
      try {
        db.prepare(`
          UPDATE ai_model_stats
          SET success_calls = MAX(0, success_calls - 1), error_calls = error_calls + 1, updated_at = datetime('now')
          WHERE model_id = @m
        `).run({ m: cleanId });
      } catch { /* ignore */ }
    } catch (err) {
      atWarn(`[ModelRanker] recordContentFailure failed for ${modelId}: ${err}`);
    }
  }

  /** Detect if a model ID belongs to a paid/direct provider (not free OpenRouter pool).
   *  These models should never be permanently banned — their failures are almost
   *  always caused by our own code bugs, not by the model being defective. */
  private isPaidDirectModel(modelId: string): boolean {
    const id = (modelId || '').toLowerCase();
    return id.startsWith('minimax') || id.startsWith('gpt-') || id.startsWith('claude-')
      || id.startsWith('gemini-') || id.startsWith('o1') || id.startsWith('o3')
      || id.includes('openai/') || id.includes('anthropic/')
      || (!id.includes(':free') && !id.includes('/'));
  }

  /** Cross-role toxic check: blacklist model for ALL roles if total failures ≥ threshold.
   *  2026-05-11: Only applies to free OpenRouter models. Paid/direct providers (minimax,
   *  openai, claude, gemini) are exempted — their failures are almost always caused by
   *  our own code bugs (wrong anchors, token truncation), not by the model being toxic. */
  private checkCrossRoleToxic(modelId: string, providerId: string, nowMs: number): void {
    // Exempt paid/direct models from permanent cross-role bans
    // (can't rely on providerId — it's hardcoded as 'openrouter' in most agent call sites)
    if (this.isPaidDirectModel(modelId)) return;

    const CROSS_ROLE_TOXIC_THRESHOLD = 10;
    try {
      const row = db.prepare(`
        SELECT SUM(content_fail_calls) as total_cf
        FROM ai_model_agent_stats
        WHERE model_id = ?
      `).get(modelId) as any;

      const totalCf = row?.total_cf || 0;
      if (totalCf >= CROSS_ROLE_TOXIC_THRESHOLD) {
        const permanentBan = nowMs + 30 * 24 * 3_600_000; // 30 days
        const updated = db.prepare(`
          UPDATE ai_model_agent_stats
          SET blacklisted_until = MAX(blacklisted_until, @bl), updated_at = @now
          WHERE model_id = @m AND blacklisted_until < @bl
        `).run({ m: modelId, bl: permanentBan, now: nowMs });

        if (updated.changes > 0) {
          atWarn(`[ModelRanker] ☠️ CROSS-ROLE BAN: "${modelId}" has ${totalCf} content failures across all roles — blacklisted ALL roles for 30 days`);
        }
      }
    } catch (err) {
      atWarn(`[ModelRanker] checkCrossRoleToxic failed for ${modelId}: ${err}`);
    }
  }

  /** Record trade outcome (WIN/LOSS) for the model that generated the signal. */
  public recordTradeOutcome(modelId: string, profitR: number, agentRole: AgentRole = 'reasoning'): void {
    if (!modelId) return;
    const cleanId = this.cleanModelId(modelId);
    const nowMs   = Date.now();
    try {
      db.prepare(`
        INSERT INTO ai_model_agent_stats (model_id, provider_id, agent_role, total_profit_r, win_count, loss_count, updated_at)
        VALUES (@m, 'openrouter', @r, @pr, @w, @l, @now)
        ON CONFLICT(model_id, agent_role) DO UPDATE SET
          total_profit_r = total_profit_r + @pr,
          win_count  = win_count  + @w,
          loss_count = loss_count + @l,
          updated_at = @now
      `).run({
        m: cleanId, r: agentRole, pr: profitR,
        w: profitR > 0 ? 1 : 0,
        l: profitR < 0 ? 1 : 0,
        now: nowMs,
      });

      // Legacy compat
      try {
        db.prepare(`
          UPDATE ai_model_stats SET
            total_profit_r = total_profit_r + @pr,
            win_count  = win_count  + @w,
            loss_count = loss_count + @l,
            updated_at = datetime('now')
          WHERE model_id = @m
        `).run({ m: cleanId, pr: profitR, w: profitR > 0 ? 1 : 0, l: profitR < 0 ? 1 : 0 });
      } catch { /* ignore */ }
    } catch (err) {
      atWarn(`[ModelRanker] recordTradeOutcome failed for ${modelId}: ${err}`);
    }
  }

  /** Update SL/TP accuracy score for slTpAgent specifically. */
  public recordSlTpAccuracy(modelId: string, deltaScore: number): void {
    if (!modelId) return;
    const cleanId = this.cleanModelId(modelId);
    try {
      db.prepare(`
        UPDATE ai_model_agent_stats
        SET sl_tp_score = MAX(0, MIN(100, sl_tp_score + @d)), updated_at = @now
        WHERE model_id = @m AND agent_role = 'slTpAgent'
      `).run({ m: cleanId, d: deltaScore, now: Date.now() });
    } catch (err) {
      atWarn(`[ModelRanker] recordSlTpAccuracy failed: ${err}`);
    }
  }

  // --- Top model selection ---------------------------------------------------

  /**
   * Get the best models for a given agent role.
   *
   * Selection logic:
   *  1. Filter out: time-blacklisted, incapable, models with < MIN_CALLS_TO_RANK
   *  2. Every EXPLORE_EVERY_N calls: pick an underexplored model (total_calls < MIN_CALLS_EXPLORE)
   *     that hasn't been blacklisted — ensures new models get evaluated.
   *  3. Otherwise: return top-scored models from DB.
   *  4. Fallback ladder if everything is filtered.
   */
  public getTopModels(
    providerId: string = 'openrouter',
    limit: number = 10,
    agentRole: AgentRole = 'reasoning',
  ): ModelScoreRow[] {
    const nowMs = Date.now();
    try {
      // ── Fetch all candidates with computed score ─────────────────────────
      const rows = db.prepare(`
        SELECT
          model_id,
          provider_id,
          agent_role,
          total_calls,
          success_calls,
          error_calls,
          content_fail_calls,
          consecutive_failures,
          blacklisted_until,
          total_profit_r   AS profit_r,
          avg_latency_ms   AS avg_latency,
          win_count,
          loss_count,
          last_used_at,
          -- 2026-05-01 Phase 1 — เปลี่ยน NULLIF(x,0) → MAX(x,1) ป้องกัน NULL propagate
          -- 2026-05-01 Phase 2 — ลด penalty (10→5, 3→1.5) เพื่อให้โมเดลใหม่ที่ fail
          --   1-2 ครั้งแรกยังแข่งขันได้ ไม่ถูกตัดสินจาก fail เดียว
          CAST(success_calls AS REAL) / MAX(total_calls, 1)   AS success_rate,
          CAST(win_count     AS REAL) / MAX(win_count + loss_count, 1) AS win_rate,
          (
            (CAST(win_count AS REAL) / MAX(win_count + loss_count, 1) * 35) +
            (CAST(success_calls AS REAL) / MAX(total_calls, 1) * 25) +
            (MIN(20, COALESCE(total_profit_r, 0) * 2)) +
            (MAX(0, 5 - COALESCE(avg_latency_ms, 0) / 2000.0)) +
            (CASE WHEN agent_role = 'slTpAgent' THEN COALESCE(sl_tp_score, 0) * 2 ELSE 0 END) -
            (CAST(error_calls AS REAL) / MAX(total_calls, 1) * 20) -
            (consecutive_failures * 5) -
            (content_fail_calls * 1.5)
          ) AS score
        FROM ai_model_agent_stats
        WHERE provider_id = @p AND agent_role = @r
        ORDER BY score DESC NULLS LAST, RANDOM()
      `).all({ p: providerId, r: agentRole }) as any[];

      const toRow = (r: any): ModelScoreRow & { total_calls: number; last_used_at: number } => ({
        model_id:             r.model_id,
        provider_id:          r.provider_id,
        agent_role:           r.agent_role || agentRole,
        score:                Number(r.score ?? 0),
        avg_latency:          Number(r.avg_latency ?? 0),
        success_rate:         Number(r.success_rate ?? 0),
        profit_r:             Number(r.profit_r ?? 0),
        total_calls:          Number(r.total_calls ?? 0),
        win_count:            Number(r.win_count ?? 0),
        loss_count:           Number(r.loss_count ?? 0),
        consecutive_failures: Number(r.consecutive_failures ?? 0),
        blacklisted_until:    Number(r.blacklisted_until ?? 0),
        last_used_at:         Number(r.last_used_at ?? 0),
      });

      const all = rows.map(toRow);

      // ── Filter predicates ────────────────────────────────────────────────
      const notBL     = (c: typeof all[0]) => !this.isBlacklisted(c.blacklisted_until);
      const capable   = (c: typeof all[0]) => this.isCapable(c.model_id);
      const hasHist   = (c: typeof all[0]) => c.total_calls >= MIN_CALLS_TO_RANK;
      const unexplored = (c: typeof all[0]) => c.total_calls < MIN_CALLS_EXPLORE;

      // ── Round-robin exploration ──────────────────────────────────────────
      const roleKey = `${agentRole}:${providerId}`;
      const counter = (this.exploreCounter.get(roleKey) || 0) + 1;
      this.exploreCounter.set(roleKey, counter);

      if (counter % EXPLORE_EVERY_N === 0) {
        // Pick a capable, non-blacklisted, underexplored model
        const candidates = all.filter(c => notBL(c) && capable(c) && unexplored(c));
        if (candidates.length > 0) {
          // Shuffle + pick one random underexplored model
          const pick = candidates[Math.floor(Math.random() * candidates.length)];
          atLog(`[ModelRanker] 🔍 Exploration: ${agentRole} testing underexplored "${pick.model_id}" (calls=${pick.total_calls})`);
          return [pick];
        }
      }

      // ── Normal ranked selection ──────────────────────────────────────────
      let filtered = all.filter(c => notBL(c) && capable(c) && hasHist(c));

      // Fallback ladder
      if (filtered.length === 0 && all.length > 0) {
        filtered = all.filter(c => notBL(c) && capable(c));
        if (filtered.length > 0) {
          atLog(`[ModelRanker] ${agentRole}: relaxing min-calls gate — no model with ≥${MIN_CALLS_TO_RANK} calls`);
        }
      }
      if (filtered.length === 0 && all.length > 0) {
        // All blacklisted → clear time-based blacklist for expired entries and retry
        const expired = all.filter(c => capable(c) && c.blacklisted_until > 0 && c.blacklisted_until <= nowMs);
        if (expired.length > 0) {
          // Reset expired blacklists
          db.prepare(`
            UPDATE ai_model_agent_stats SET blacklisted_until = 0
            WHERE agent_role = ? AND blacklisted_until > 0 AND blacklisted_until <= ?
          `).run(agentRole, nowMs);
          filtered = all.filter(c => capable(c));
          atLog(`[ModelRanker] ${agentRole}: cleared ${expired.length} expired blacklists — restarting rotation`);
        } else {
          // 2026-05-01 — Only force-reset models with low streak (<5).
          // Models with consecutive_failures >= 5 are "toxic" — they have a
          // proven pattern of returning empty/broken responses and should NOT
          // be force-unblocked.  Let the dispatcher's fallback chain handle it.
          const TOXIC_STREAK = 5;
          const recoverable = all.filter(c => capable(c) && c.consecutive_failures < TOXIC_STREAK);
          if (recoverable.length > 0) {
            atWarn(`[ModelRanker] ${agentRole}: force-reset ${recoverable.length} recoverable models (skipping ${all.length - recoverable.length} toxic)`);
            const recoverableIds = recoverable.map(c => c.model_id);
            db.prepare(`
              UPDATE ai_model_agent_stats SET blacklisted_until = 0
              WHERE agent_role = ? AND blacklisted_until > 0 AND consecutive_failures < ?
            `).run(agentRole, TOXIC_STREAK);
            filtered = recoverable;
          } else {
            // ALL models are toxic — return empty so dispatcher uses fallback chain
            atWarn(`[ModelRanker] ${agentRole}: all ${all.length} model(s) are toxic (streak≥${TOXIC_STREAK}) — returning empty for fallback`);
            filtered = [];
          }
        }
      }

      // 2026-05-02 Phase 4.2 — Force exploration เมื่อ top model มี score ติดลบมาก
      // 2026-05-02 Phase 5.1 — ห้ามเลือกตัวที่เคย fail (consecutive_failures > 0)
      //   เดิม: model ที่ fail ครั้งแรก → BL หาย → ถูก force เลือกอีก → fail ลูป
      // 2026-05-02 Phase 5.2 — Per-role cooldown 5 นาที กัน force ทุก cycle
      const FORCE_EXPLORE_THRESHOLD = -10;
      const FORCE_EXPLORE_COOLDOWN_MS = 5 * 60_000;
      if (filtered.length > 0 && filtered[0].score < FORCE_EXPLORE_THRESHOLD) {
        const lastForce = this.lastForceExploreAt.get(agentRole) || 0;
        const sinceLastForce = nowMs - lastForce;
        if (sinceLastForce < FORCE_EXPLORE_COOLDOWN_MS) {
          // ยัง cooldown — ใช้ ranked top แม้ score ลบ (กัน force ลูปทุก cycle)
          // log แค่ครั้งแรกใน cooldown window เพื่อไม่ spam
        } else {
          // เลือกเฉพาะ "บริสุทธิ์": ไม่เคย fail + ยัง unexplored + ผ่าน BL/capable
          const fresh = all.filter(c =>
            notBL(c) && capable(c) && unexplored(c) && c.consecutive_failures === 0
          );
          if (fresh.length > 0) {
            // Round-robin: เลือกตัวที่ last_used_at เก่าสุด (ให้ทุกตัวได้ chance)
            fresh.sort((a, b) => (a.last_used_at || 0) - (b.last_used_at || 0));
            const pick = fresh[0];
            this.lastForceExploreAt.set(agentRole, nowMs);
            atWarn(`[ModelRanker] ${agentRole}: top score ${filtered[0].score.toFixed(1)} < ${FORCE_EXPLORE_THRESHOLD} — forcing fresh model "${pick.model_id}" (calls=${pick.total_calls}, ${fresh.length} clean candidates, cooldown ${Math.round(FORCE_EXPLORE_COOLDOWN_MS/60000)}m)`);
            return [pick];
          }
        }
      }

      return filtered.slice(0, limit);
    } catch (err) {
      atWarn(`[ModelRanker] getTopModels failed for role=${agentRole}: ${err}`);
      return [];
    }
  }

  // ── Dashboard leaderboard ───────────────────────────────────────────────────

  /**
   * Get full per-agent leaderboard for Dashboard display.
   * Returns all agents grouped; each model may appear in multiple roles.
   */
  public getAllRankings(agentRole?: AgentRole): AgentLeaderboardRow[] {
    const nowMs = Date.now();
    try {
      // 2026-05-01 Phase 1 — sync กับ getTopModels(): NULLIF→MAX, COALESCE on avg_latency
      // 2026-05-01 Phase 2 — ลด penalty (10→5, 3→1.5) ให้สอดคล้อง getTopModels
      const query = agentRole
        ? `SELECT *, agent_role FROM ai_model_agent_stats WHERE agent_role = ? AND total_calls >= 1 ORDER BY (
             (CAST(win_count AS REAL) / MAX(win_count + loss_count, 1) * 35) +
             (CAST(success_calls AS REAL) / MAX(total_calls, 1) * 25) +
             (MIN(20, COALESCE(total_profit_r, 0) * 2)) +
             (MAX(0, 5 - COALESCE(avg_latency_ms, 0) / 2000.0)) +
             (CASE WHEN agent_role = 'slTpAgent' THEN COALESCE(sl_tp_score, 0) * 2 ELSE 0 END) -
             (CAST(error_calls AS REAL) / MAX(total_calls, 1) * 20) -
             (consecutive_failures * 5) - (content_fail_calls * 1.5)
           ) DESC NULLS LAST`
        : `SELECT *, agent_role FROM ai_model_agent_stats WHERE total_calls >= 1 ORDER BY agent_role, (
             (CAST(win_count AS REAL) / MAX(win_count + loss_count, 1) * 35) +
             (CAST(success_calls AS REAL) / MAX(total_calls, 1) * 25) +
             (MIN(20, COALESCE(total_profit_r, 0) * 2)) +
             (MAX(0, 5 - COALESCE(avg_latency_ms, 0) / 2000.0)) +
             (CASE WHEN agent_role = 'slTpAgent' THEN COALESCE(sl_tp_score, 0) * 2 ELSE 0 END) -
             (CAST(error_calls AS REAL) / MAX(total_calls, 1) * 20) -
             (consecutive_failures * 5) - (content_fail_calls * 1.5)
           ) DESC NULLS LAST`;

      const rows = (agentRole
        ? db.prepare(query).all(agentRole)
        : db.prepare(query).all()) as any[];

      // Assign per-role rank badges
      const roleRankCounters = new Map<string, number>();
      const BADGES: Array<'gold' | 'silver' | 'bronze'> = ['gold', 'silver', 'bronze'];

      return rows.map(r => {
        const role  = r.agent_role || 'reasoning';
        const rank  = (roleRankCounters.get(role) || 0) + 1;
        roleRankCounters.set(role, rank);

        const successRate = Number(r.success_calls || 0) / Math.max(Number(r.total_calls), 1);
        const winRate     = Number(r.win_count || 0) / Math.max(Number(r.win_count || 0) + Number(r.loss_count || 0), 1);
        const errRate     = Number(r.error_calls || 0) / Math.max(Number(r.total_calls), 1);
        const blUntil     = Number(r.blacklisted_until || 0);
        const slTpBonus = role === 'slTpAgent' ? Number(r.sl_tp_score || 0) * 2 : 0;
        // 2026-05-01 Phase 2 — sync penalty กับ SQL (10→5, 3→1.5)
        const score = (winRate * 35) + (successRate * 25) +
          Math.min(20, Number(r.total_profit_r || 0) * 2) +
          Math.max(0, 5 - Number(r.avg_latency_ms || 0) / 2000) +
          slTpBonus -
          (errRate * 20) - (Number(r.consecutive_failures || 0) * 5) -
          (Number(r.content_fail_calls || 0) * 1.5);

        const isBlacklisted = this.isBlacklisted(blUntil);
        const badge = rank <= 3 ? BADGES[rank - 1] : null;
        return {
          model_id:             r.model_id,
          provider_id:          r.provider_id,
          agent_role:           role,
          score,
          avg_latency:          Number(r.avg_latency_ms || 0),
          success_rate:         successRate,
          profit_r:             Number(r.total_profit_r || 0),
          total_calls:          Number(r.total_calls || 0),
          win_count:            Number(r.win_count || 0),
          loss_count:           Number(r.loss_count || 0),
          consecutive_failures: Number(r.consecutive_failures || 0),
          blacklisted_until:    blUntil,
          rank,
          badge,
          isBlacklisted,
          cooldownRemainingMs:  isBlacklisted ? Math.max(0, blUntil - nowMs) : 0,
        } as AgentLeaderboardRow;
      });
    } catch (err) {
      atWarn(`[ModelRanker] getAllRankings failed: ${err}`);
      return [];
    }
  }

  // --- Discovery & Cleanup ---------------------------------------------------

  /**
   * 2026-05-01 Phase 2 — Two-way sync กับ OpenRouter free pool:
   *   - INSERT โมเดลใหม่ที่เพิ่งเปิด free (เริ่ม stats เป็น 0)
   *   - DELETE โมเดลที่หายจาก free list (ไม่ free แล้ว/ถูก deprecate)
   *     เก็บไว้เฉพาะตัวที่มี total_calls > 0 (มี history เผื่อ user reactivate)
   *   - คืนค่าสรุป {added, removed, kept, totalFree} เพื่อ logger ใช้
   *
   * เรียกตอน boot + ทุก 24 ชม. (autoTradingService.ts) เพื่อให้ pool ทันสมัย
   * เพราะ OpenRouter เปลี่ยน free list บ่อย
   */
  public async syncDiscoveredModels(
    apiKey: string,
    baseUrl?: string,
    roles: AgentRole[] = ['reasoning', 'analyst', 'riskOfficer', 'executionTrader', 'slTpAgent'],
  ): Promise<{ added: number; removed: number; kept: number; totalFree: number }> {
    const result = { added: 0, removed: 0, kept: 0, totalFree: 0 };
    if (!apiKey) return result;
    try {
      const models = await listModelsCached('openrouter', apiKey, baseUrl, true); // force refresh
      const freeCapable = models.filter(m => m.isFree && this.isCapable(m.id));
      const freeIds = new Set(freeCapable.map(m => m.id));
      result.totalFree = freeCapable.length;

      // ── (1) INSERT new models (per role) ───────────────────────────────────
      const nowMs = Date.now();
      const insertStmt = db.prepare(`
        INSERT INTO ai_model_agent_stats (model_id, provider_id, agent_role, last_used_at, updated_at)
        VALUES (@m, 'openrouter', @r, 0, @now)
        ON CONFLICT(model_id, agent_role) DO NOTHING
      `);
      const insertMany = db.transaction((rows: { m: string; r: string; now: number }[]) => {
        let added = 0;
        for (const row of rows) {
          const r = insertStmt.run(row);
          added += r.changes;
        }
        return added;
      });
      for (const role of roles) {
        result.added += insertMany(freeCapable.map(m => ({ m: m.id, r: role, now: nowMs })));
      }

      // ── (2) PURGE models that vanished from OpenRouter free list ───────
      //   2026-05-05 — Aggressive purge:
      //   (a) total_calls=0 + เก่ากว่า GRACE_MS → ลบจริง (ไม่มีประโยชน์เก็บ)
      //   (b) total_calls>0 แต่ไม่อยู่ใน freeIds → blacklist 30 days + reset stats
      //       ป้องกัน Smart Ranker เลือก dead models (เช่น deepseek/deepseek-chat-v3.1:free
      //       ที่ return HTTP 404 ซ้ำๆ ทำให้ pipeline ล้ม)
      const GRACE_MS = 60 * 60_000; // 1 ชม.
      const cutoffMs = Date.now() - GRACE_MS;
      const existing = db.prepare(`
        SELECT model_id, agent_role, total_calls, updated_at
        FROM ai_model_agent_stats
        WHERE provider_id = 'openrouter'
      `).all() as Array<{ model_id: string; agent_role: string; total_calls: number; updated_at: number }>;

      const stale = existing.filter(r => !freeIds.has(r.model_id));

      // (a) ลบ rows ที่ไม่มี history + เก่ากว่า GRACE_MS
      const purgeIds = stale
        .filter(r => r.total_calls === 0 && (r.updated_at || 0) < cutoffMs)
        .map(r => `${r.agent_role}|${r.model_id}`);
      const skippedFresh = stale.filter(r => r.total_calls === 0 && (r.updated_at || 0) >= cutoffMs).length;
      if (skippedFresh > 0) {
        atLog(`[ModelRanker] Sync grace: skipped ${skippedFresh} fresh rows (<1h old) — will reconsider next cycle`);
      }

      if (purgeIds.length > 0) {
        const delStmt = db.prepare(`
          DELETE FROM ai_model_agent_stats
          WHERE provider_id = 'openrouter' AND agent_role = ? AND model_id = ? AND total_calls = 0
        `);
        const deleteMany = db.transaction((items: string[]) => {
          let removed = 0;
          for (const key of items) {
            const [role, modelId] = key.split('|');
            const r = delStmt.run(role, modelId);
            removed += r.changes;
          }
          return removed;
        });
        result.removed = deleteMany(purgeIds);
      }

      // (b) Blacklist + purge models ที่มี history แต่ไม่อยู่ใน free list อีกแล้ว
      //     → blacklist 30 days เพื่อ Smart Ranker ไม่เลือก
      //     → ลบ stats เพื่อไม่ให้ seed กลับเข้ามา
      const staleWithHistory = stale.filter(r => r.total_calls > 0);
      const staleModelIds = new Set(staleWithHistory.map(r => r.model_id));
      if (staleModelIds.size > 0) {
        const permanentBan = nowMs + 30 * 24 * 3_600_000; // 30 days
        const banStmt = db.prepare(`
          UPDATE ai_model_agent_stats
          SET blacklisted_until = @bl, consecutive_failures = 99, updated_at = @now
          WHERE provider_id = 'openrouter' AND model_id = @m AND blacklisted_until < @bl
        `);
        const banTx = db.transaction((models: string[]) => {
          for (const m of models) {
            banStmt.run({ m, bl: permanentBan, now: nowMs });
          }
        });
        banTx(Array.from(staleModelIds));
        atWarn(`[ModelRanker] 🗑️ Purged ${staleModelIds.size} dead models from free pool: ${Array.from(staleModelIds).join(', ')}`);
        result.removed += staleWithHistory.length;
      }
      result.kept = 0; // ไม่เก็บ dead models อีกแล้ว

      atLog(
        `[ModelRanker] Sync OpenRouter free: total=${result.totalFree} | added=${result.added} | removed=${result.removed} | purged-dead=${staleModelIds.size}`,
      );
      return result;
    } catch (err) {
      atWarn(`[ModelRanker] syncDiscoveredModels failed: ${err}`);
      return result;
    }
  }

  public cleanupLegacyStats(): void {
    try {
      const r = db.prepare(
        `DELETE FROM ai_model_stats WHERE total_calls = 0 AND win_count = 0 AND loss_count = 0`
      ).run();
      if (r.changes > 0) atLog(`[ModelRanker] Cleaned ${r.changes} empty legacy rows`);
    } catch { /* ignore */ }

    // 2026-05-02 Phase 3.2 — Seed known-free models ทันที (fallback กรณี
    //   syncDiscoveredModels ล้ม/network down) เพื่อให้ ranker มี pool หลายตัว
    //   ตั้งแต่ boot แรก ไม่ต้องรอ daily sync
    this.seedKnownFreeModels();
  }

  /**
   * Static fallback seed — รายชื่อโมเดล free ที่รู้ว่าใช้งานได้บน OpenRouter
   * ตอน 2026-05 (อ้างอิง dashboard/community pin). syncDiscoveredModels จะ
   * refresh ตามจริงทุก 24 ชม. ส่วน seed นี้แค่ป้องกัน "0 models in pool" บน boot
   */
  private seedKnownFreeModels(): void {
    // 2026-05-05 — เพิ่ม gemma-4, owl-alpha จาก log analysis ที่พิสูจน์ว่าใช้งานได้
    // ลบ dead models: deepseek-chat-v3.1:free (404), sonoma-sky-alpha (404)
    const KNOWN_FREE_OPENROUTER = [
      'google/gemini-2.0-flash-exp:free',
      'google/gemma-3-27b-it:free',
      'google/gemma-4-26b-a4b-it:free',     // ✅ proven in log (Analyst 12.3s)
      'google/gemma-3n-e4b-it:free',
      'qwen/qwen3-235b-a22b:free',
      'qwen/qwen-2.5-72b-instruct:free',
      'z-ai/glm-4.5-air:free',
      'mistralai/mistral-small-3.2-24b-instruct:free',
      'deepseek/deepseek-r1:free',
      'nvidia/nemotron-3-super-120b-a12b:free',
      'meta-llama/llama-3.3-70b-instruct:free',
      'minimax/minimax-m2.5:free',
      'openai/gpt-oss-120b:free',
      'openrouter/owl-alpha',                // ✅ primary model in current config
    ];
    const SEEDED_ROLES: AgentRole[] = ['analyst', 'riskOfficer', 'slTpAgent', 'postMortem', 'executionTrader', 'reasoning'];
    try {
      const stmt = db.prepare(`
        INSERT INTO ai_model_agent_stats (model_id, provider_id, agent_role, last_used_at, updated_at)
        VALUES (?, 'openrouter', ?, 0, ?)
        ON CONFLICT(model_id, agent_role) DO NOTHING
      `);
      const tx = db.transaction(() => {
        let added = 0;
        const now = Date.now();
        for (const m of KNOWN_FREE_OPENROUTER) {
          for (const r of SEEDED_ROLES) {
            const res = stmt.run(m, r, now);
            added += res.changes;
          }
        }
        return added;
      });
      const added = tx();
      if (added > 0) {
        atLog(`[ModelRanker] Seeded ${added} known-free model rows (${KNOWN_FREE_OPENROUTER.length} models × ${SEEDED_ROLES.length} roles)`);
      }
    } catch (err) {
      atWarn(`[ModelRanker] seedKnownFreeModels failed: ${err}`);
    }
  }

  private _recordLegacy(args: {
    modelId: string; providerId: string; latencyMs: number;
    success: boolean; isTimeout: boolean; tokens?: number;
  }): void {
    const { modelId, providerId, latencyMs, success, isTimeout, tokens = 0 } = args;
    try {
      if (!success) {
        db.prepare(`
          INSERT INTO ai_model_stats (model_id, provider_id, total_calls, error_calls, timeout_calls, avg_latency_ms, last_used_at)
          VALUES (@m, @p, 1, 1, @to, @lat, datetime('now'))
          ON CONFLICT(model_id) DO UPDATE SET
            total_calls = total_calls + 1, error_calls = error_calls + 1,
            timeout_calls = timeout_calls + @to,
            avg_latency_ms = (avg_latency_ms * 0.8) + (@lat * 0.2),
            last_used_at = datetime('now'), updated_at = datetime('now')
        `).run({ m: modelId, p: providerId, to: isTimeout ? 1 : 0, lat: latencyMs });
      } else {
        db.prepare(`
          INSERT INTO ai_model_stats (model_id, provider_id, total_calls, success_calls, avg_latency_ms, total_tokens, last_used_at)
          VALUES (@m, @p, 1, 1, @lat, @tok, datetime('now'))
          ON CONFLICT(model_id) DO UPDATE SET
            total_calls = total_calls + 1, success_calls = success_calls + 1,
            avg_latency_ms = (avg_latency_ms * 0.8) + (@lat * 0.2),
            total_tokens = total_tokens + @tok,
            last_used_at = datetime('now'), updated_at = datetime('now')
        `).run({ m: modelId, p: providerId, lat: latencyMs, tok: tokens });
      }
    } catch { /* ignore */ }
  }
}

export const modelRankerService = new ModelRankerService();

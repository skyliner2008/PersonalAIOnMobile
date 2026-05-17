import { appendFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { getDb } from '../../../db.js';
import { 
  AutoTradingConfig, 
  EngineState, 
  CycleDecision, 
  JournalRow, 
  LearnSummary, 
  ManagementJournalRow,
  PlaybookScores
} from '../types.js';
import { 
  classifyOutcome,
  profitCashToR
} from '../journal.js';
import { 
  safeJsonParse, 
  asNumber, 
  asString, 
  round2
} from '../utils.js';
import { encrypt, decrypt } from '../../encryption.js';

const db = getDb();
const __dirname = dirname(fileURLToPath(import.meta.url));
const tradeDecisionLogDir = join(__dirname, '../../../../data/trade_decision_logs');
const decisionLogTimeZone = 'Asia/Bangkok';

function decisionLogDateKey(date = new Date()): string {
  return date.toLocaleDateString('sv-SE', { timeZone: decisionLogTimeZone });
}

function decisionLogLocalIso(date = new Date()): string {
  return date.toLocaleString('sv-SE', { timeZone: decisionLogTimeZone }).replace(' ', 'T');
}

type DataQuality = {
  score: number;
  flags: string[];
  eligible: boolean;
};

const CLOSED_OUTCOMES = new Set(['WIN', 'LOSS', 'BE']);

function clampQuality(score: number): number {
  return Math.max(0, Math.min(1, round2(score)));
}

function quality(flags: string[], eligible: boolean, baseScore = 1): DataQuality {
  return {
    score: clampQuality(baseScore - flags.length * 0.12),
    flags: Array.from(new Set(flags)),
    eligible,
  };
}

function isNearBreakEven(profit: number | null | undefined, profitR: number | null | undefined): boolean {
  const cashOk = profit === null || profit === undefined || Math.abs(Number(profit)) <= 1;
  const rOk = profitR === null || profitR === undefined || Math.abs(Number(profitR)) <= 0.15;
  return cashOk && rOk;
}

function evaluateJournalQuality(row: Partial<JournalRow>): DataQuality {
  const flags: string[] = [];
  const outcome = String(row.outcome || '').toUpperCase();
  const isClosed = CLOSED_OUTCOMES.has(outcome);
  const profit = row.profit === null || row.profit === undefined ? null : Number(row.profit);
  const profitR = row.profitR === null || row.profitR === undefined ? null : Number(row.profitR);

  if (!row.decisionId) flags.push('MISSING_DECISION_ID');
  if (row.wasExecuted && !row.mt5Ticket) flags.push('EXECUTED_MISSING_TICKET');
  if (isClosed && !row.wasExecuted) flags.push('CLOSED_BUT_NOT_EXECUTED');
  if (isClosed && !row.mt5Ticket) flags.push('CLOSED_MISSING_TICKET');
  if (isClosed && (profit === null || !Number.isFinite(profit))) flags.push('CLOSED_MISSING_PROFIT');
  if (isClosed && (profitR === null || !Number.isFinite(profitR))) flags.push('CLOSED_MISSING_PROFIT_R');
  if (row.wasExecuted && (row.entry === null || row.entry === undefined || row.sl === null || row.sl === undefined)) {
    flags.push('EXECUTED_MISSING_RISK_PRICES');
  }
  if (outcome === 'BE' && !isNearBreakEven(profit, profitR)) flags.push('SUSPICIOUS_BE_OUTCOME');
  if (String(row.closeReason || '').toUpperCase() === 'BE' && !isNearBreakEven(profit, profitR)) {
    flags.push('SUSPICIOUS_BE_CLOSE_REASON');
  }
  if (outcome === 'WIN' && profit !== null && profit < -0.005) flags.push('OUTCOME_PROFIT_MISMATCH');
  if (outcome === 'LOSS' && profit !== null && profit > 0.005) flags.push('OUTCOME_PROFIT_MISMATCH');

  const eligible =
    isClosed &&
    Boolean(row.wasExecuted) &&
    Boolean(row.mt5Ticket) &&
    profit !== null &&
    Number.isFinite(profit) &&
    profitR !== null &&
    Number.isFinite(profitR) &&
    !flags.includes('SUSPICIOUS_BE_OUTCOME') &&
    !flags.includes('OUTCOME_PROFIT_MISMATCH');

  return quality(flags, eligible);
}

function evaluateDecisionQuality(payload: Record<string, unknown>): DataQuality {
  const flags: string[] = [];
  const decisionType = String(payload.decisionType || '');
  const side = String(payload.side || '');
  const gateTrace = Array.isArray(payload.gateTrace) ? payload.gateTrace as any[] : [];
  const analysis = payload.analysis && typeof payload.analysis === 'object' ? payload.analysis as Record<string, unknown> : {};
  const wasExecuted = Boolean(payload.wasExecuted);

  if (!payload.decisionId) flags.push('MISSING_DECISION_ID');
  if (!decisionType) flags.push('MISSING_DECISION_TYPE');
  if (side === 'SKIP' && !payload.blockCategory && decisionType !== 'NO_SIGNAL') flags.push('SKIP_MISSING_BLOCK_CATEGORY');
  if (gateTrace.length === 0) flags.push('MISSING_GATE_TRACE');
  if (Object.keys(analysis).length === 0) flags.push('MISSING_ANALYSIS');
  if (decisionType === 'ORDER_EXECUTED' && (!wasExecuted || !payload.mt5Ticket)) flags.push('EXECUTED_LINK_INCOMPLETE');
  if (decisionType === 'ORDER_BLOCKED' && !payload.blockCategory) flags.push('BLOCKED_MISSING_CATEGORY');
  if (decisionType === 'ORDER_BLOCKED' && !gateTrace.some((g) => ['BLOCK', 'FAILED', 'SKIP'].includes(String(g?.status || '')))) {
    flags.push('BLOCKED_MISSING_TRACE_STATUS');
  }

  const eligible =
    Boolean(decisionType) &&
    gateTrace.length > 0 &&
    Object.keys(analysis).length > 0 &&
    !flags.includes('EXECUTED_LINK_INCOMPLETE') &&
    !flags.includes('BLOCKED_MISSING_CATEGORY') &&
    !flags.includes('BLOCKED_MISSING_TRACE_STATUS');

  return quality(flags, eligible);
}

export const defaultConfig: AutoTradingConfig = {
  watchlist: ['XAUUSD'],
  timeframe: 'M15',
  tickIntervalMs: 300_000,
  manageIntervalMs: 30_000,
  learnIntervalMs: 6 * 60 * 60_000,
  minConfluence: 45,
  minFitness: 40,
  minRRR: 1.5,
  enableLiveTrading: false,
  enableAiMode: true,       // V23.0: default = AI supervisor mode
  preferredStrategies: [],
  symbolBlacklist: [],
  // 2026-05-08 V24.1 Fix: ลด BE/Trail trigger จาก 1.0R → 0.5R เพราะ live log แสดงว่า
  // positions float -0.6R ถึง +0.1R เป็นชั่วโมง ไม่เคยถึง +1R เลย → BE/TRAIL
  // ไม่เคย fire และ R-distribution จริงต่ำกว่า 1R ส่วนใหญ่
  breakEvenTriggerR: 0.5,
  trailAfterR: 0.6,
  // V24.1 Time-based stale-position exit (BUG#4 fix):
  // ถ้า position ค้างใน mid-zone (R∈[stalePositionMinR, stalePositionMaxR]) นานกว่า
  // stalePositionMaxAgeMs → close at market เพื่อตัด churn (ลด opportunity cost)
  stalePositionMaxAgeMs: 45 * 60_000,   // 45 นาที
  stalePositionMinR: -0.25,
  stalePositionMaxR: 0.30,
  autoTune: true,
  preferFreeOnly: true,
  autoBlacklistAfterLosses: 4,
  risk: {
    riskPerTradePct: 0.5,
    maxTotalExposurePct: 3,
    maxOpenPositions: 50,    // Aggregate ceiling across ALL symbols — per-symbol cap below is the real gate.
    maxDailyLossPct: 2.5,
    maxDrawdownPct: 8,
    minFreeMarginPct: 50,
    correlationCap: 2,
    maxCorrelation: 0.8, // Default 0.8 (Highly correlated)
    minLotStep: 0.01,
    maxLot: 5,
    minLot: 0.01,
    maxSpread: 100,
    maxSpreadOverride: {
      // Per-symbol absolute hard caps (broker-specific). The dynamic baseline
      // does the per-tick filtering; these are just the safety ceiling.
      'XAUUSD': 100,
      'XAGUSD': 200,
      'XBTUSD': 8000,    // some brokers use XBT prefix
      'BTCUSD': 8000,
      'XETUSD': 3000,
      'ETHUSD': 3000,
    },
    pointValueOverride: {},
    // 2026-04-26 — per-symbol position cap defaults. Each symbol gets its own
    // slot budget. AI sees positions of THIS symbol only when judging risk.
    maxPositionsPerSymbol: {
      'XAUUSD': 5,
      'XAGUSD': 5,
      'XBTUSD': 5,
      'BTCUSD': 5,
      'ETHUSD': 5,
    },
    defaultMaxPositionsPerSymbol: 5,
    riskPerTradePctOverride: {},
  },
  strategy: {
    minRRR: 1.5,
    minConfluence: 60,
    scalpingRRR: 1.2,
    swingRRR: 2.5,
    gridLegs: 5,
    gridStepPct: 0.4,
    trailingAtrMult: 1.5,
    breakoutBufferPct: 0.1,
  },
  newsRisk: {
    enabled: true,
    impactLevel: 'HIGH',
    minutesToEvent: 999,
  },
  adaptive: {
    allowSameSymbolPositions: true,
    allowOverLimitDefense: true,
    allowCounterHedge: true,
    allowScaleInRecovery: true,
    maxDefensePositionsPerSymbol: 3,
    maxSameSymbolPositions: 6,
    adverseConfluence: 78,
    defenseConfidence: 72,
    hedgeRatioMin: 0.25,
    hedgeRatioMax: 1.0,
    defaultHedgeRatio: 0.6,
    scaleInRatioMin: 0.2,
    scaleInRatioMax: 0.75,
    defaultScaleInRatio: 0.35,
    reduceLossThresholdR: -1.2,
    closeLossThresholdR: -2.4,
    scaleInLossThresholdR: -0.9,
    maxScaleInStepsPerSymbol: 2,
    hedgeCooldownMs: 3 * 60_000,
    scaleInCooldownMs: 5 * 60_000,
    hardCapPositionMultiplier: 2.0,
    hardCapPerSymbolMultiplier: 1.5,
    mtfDowngradeBlock: true,
    mtfDowngradeConfluenceMin: 80,
    decisionOverrideConfidenceMin: 75,
    useMultiAgent: false,
    // V24.0 (2026-05-07) — Fade-the-Level + Sequential Entry + Basket BE
    enableProximityGate: true,
    proximityMaxPip: 100,                // XAUUSD ~ $1.00, XBTUSD ~ $100
    zoneContinuationConfluenceMin: 68,
    zoneBreakoutConfluenceMin: 55,
    zoneHardBlockPct: 25,
    zoneLocalAlignmentConfluenceMin: 68,
    zoneFvgProximityAtrMul: 0.35,
    zoneWallFadeMinStars: 4,
    zoneWallFadeConfluenceMin: 50,
    proximityUnconditionalStars: 3,      // ★★★+ approve โดยไม่ต้อง confirm
    proximityConditionalStars: 2,        // ★★ ต้องมี LTF (M5/M15) RSI/bias confirm
    enableSequentialEntry: true,
    sequentialAddOnLossPct: 0.25,        // ขาดทุน 25% ของ SL → เปิดไม้แก้ได้
    recoveryTriggerPct: 0.25,            // sync กับ sequentialAddOnLossPct
    scaleInMinEntryDistanceXAU: 2.0,
    scaleInMinEntryDistanceAtrMul: 0.04,
    scaleInRequiresSafeOrLossGate: true,
    v25: {
      hybridDirectMaxStateAgeMs: 180_000,
      allowV24TrendFollowDirectWhenV25Stale: true,
      hybridTrendFollowMinConfluence: 80,
      hybridTrendFollowMaxOpposingWallAtrMul: 0.5,
    },
    requireBasketBeBeforeClose: true,
  },
  notificationSettings: {
    onAnalysis: true,
    onOrder: true,
    onLoss: true,
    onClose: true,
  },
};

export const defaultState: EngineState = {
  running: false,
  phase: 'STOPPED',
  lastTickAt: null,
  cycleCount: 0,
  openTradesTracked: 0,
  message: 'server auto trading ready',
  status: 'OFFLINE',
};

class PersistenceService {
  public loadConfig(): AutoTradingConfig {
    const row = db.prepare(`SELECT config_json, ai_model, api_key, agent_prompt, notification_settings_json FROM auto_trading_config WHERE id = 1`).get() as any;
    const baseConfig = this.normalizeConfig(safeJsonParse(row?.config_json, defaultConfig), defaultConfig);

    // 2026-05-08 V24.1 Migration: lower trail/BE thresholds.
    // Live log analysis (08-05-2026) showed positions never reached +1R for hours
    // → BE/TRAIL never activated. New defaults: BE=0.5R, TRAIL=0.6R.
    // Old configs in DB get migrated down once.
    if (baseConfig.trailAfterR >= 1.0) {
      baseConfig.trailAfterR = defaultConfig.trailAfterR; // 0.6
    }
    if ((baseConfig.breakEvenTriggerR ?? 1.0) >= 1.0) {
      baseConfig.breakEvenTriggerR = defaultConfig.breakEvenTriggerR; // 0.5
    }

    const decryptedProviderKeys: any = {};
    const rawPK = (baseConfig as any).providerKeys || {};
    for (const k of Object.keys(rawPK)) {
      const v = (rawPK as any)[k];
      if (typeof v === 'string' && v.startsWith('enc:')) {
        try { decryptedProviderKeys[k] = decrypt(v.slice(4)); } catch { /* leave undefined */ }
      } else if (typeof v === 'string' && v) {
        decryptedProviderKeys[k] = v;
      }
    }
    return {
      ...baseConfig,
      aiModel: row?.ai_model || baseConfig.aiModel,
      apiKey: row?.api_key ? decrypt(row.api_key) : baseConfig.apiKey,
      agentPrompt: row?.agent_prompt || baseConfig.agentPrompt,
      notificationSettings: safeJsonParse(row?.notification_settings_json, baseConfig.notificationSettings),
      providerKeys: { ...(baseConfig as any).providerKeys, ...decryptedProviderKeys },
    };
  }

  public loadRuntimeState(): EngineState {
    const row = db.prepare(`SELECT state_json FROM auto_trading_runtime WHERE id = 1`).get() as { state_json: string } | undefined;
    return { ...defaultState, ...safeJsonParse(row?.state_json, defaultState) };
  }

  public loadLastDecisions(): CycleDecision[] {
    const row = db.prepare(`SELECT last_decisions_json FROM auto_trading_runtime WHERE id = 1`).get() as { last_decisions_json: string } | undefined;
    return safeJsonParse(row?.last_decisions_json, [] as CycleDecision[]);
  }

  public loadOpenJournal(): JournalRow[] {
    const rows = db.prepare(
      `SELECT * FROM auto_trading_journal
       WHERE outcome IN ('OPEN', 'PAPER')
       ORDER BY created_at DESC
       LIMIT 50`
    ).all() as any[];
    return rows.map((row) => this.mapJournalRow(row));
  }

  public loadLearnSummary(): LearnSummary | null {
    const row = db.prepare(`SELECT learn_summary_json FROM auto_trading_runtime WHERE id = 1`).get() as { learn_summary_json: string | null } | undefined;
    return safeJsonParse(row?.learn_summary_json, null as LearnSummary | null);
  }

  public persistConfig(config: AutoTradingConfig): void {
    // Encrypt providerKeys before serializing into config_json so plaintext
    // API keys never sit in SQLite. Marker prefix "enc:" lets loadConfig
    // identify legacy plaintext rows after the schema rolls forward.
    const cloneForStorage: any = { ...config, providerKeys: { ...((config.providerKeys || {}) as any) } };
    for (const k of Object.keys(cloneForStorage.providerKeys)) {
      const v = cloneForStorage.providerKeys[k];
      if (typeof v === 'string' && v && !v.startsWith('enc:')) {
        cloneForStorage.providerKeys[k] = 'enc:' + encrypt(v);
      }
    }
    db.prepare(
      `INSERT INTO auto_trading_config (id, config_json, ai_model, api_key, agent_prompt, notification_settings_json, updated_at)
       VALUES (1, ?, ?, ?, ?, ?, datetime('now'))
       ON CONFLICT(id) DO UPDATE SET 
         config_json = excluded.config_json,
         ai_model = excluded.ai_model,
         api_key = excluded.api_key,
         agent_prompt = excluded.agent_prompt,
         notification_settings_json = excluded.notification_settings_json,
         updated_at = datetime('now')`
    ).run(
      JSON.stringify(cloneForStorage),
      config.aiModel || null,
      config.apiKey ? encrypt(config.apiKey) : null,
      config.agentPrompt || null,
      JSON.stringify(config.notificationSettings || {})
    );
  }

  public persistRuntime(state: EngineState, lastDecisions: CycleDecision[], openJournal: JournalRow[], learnSummary: LearnSummary | null): void {
    db.prepare(
      `INSERT INTO auto_trading_runtime (id, state_json, last_decisions_json, open_journal_json, learn_summary_json, updated_at)
       VALUES (1, ?, ?, ?, ?, datetime('now'))
       ON CONFLICT(id) DO UPDATE SET
         state_json = excluded.state_json,
         last_decisions_json = excluded.last_decisions_json,
         open_journal_json = excluded.open_journal_json,
         learn_summary_json = excluded.learn_summary_json,
         updated_at = datetime('now')`
    ).run(
      JSON.stringify(state),
      JSON.stringify(lastDecisions),
      JSON.stringify(openJournal),
      learnSummary ? JSON.stringify(learnSummary) : null
    );
  }

  public appendDecisionFeed(cycleNo: number, timeframe: string, decisions: CycleDecision[]): void {
    if (!decisions.length) return;
    const stmt = db.prepare(
      `INSERT INTO auto_trading_decision_feed (
        decision_id, cycle_no, symbol, timeframe, side, strategy, regime, confluence, fitness,
        entry, sl, tp, volume, rrr, risk_gate, rationale, translated_th, decision_type, block_category,
        deterministic_json, analysis_json, signals_json, gate_trace_json, market_snapshot_json, order_json, outcome_json,
        quality_score, quality_flags_json, learning_eligible, linked_journal_id, linked_management_count,
        was_executed, mt5_ticket, at_iso, created_at, updated_at
      ) VALUES (
        @decisionId, @cycleNo, @symbol, @timeframe, @side, @strategy, @regime, @confluence, @fitness,
        @entry, @sl, @tp, @volume, @rrr, @riskGate, @rationale, @translatedTh, @decisionType, @blockCategory,
        @deterministicJson, @analysisJson, @signalsJson, @gateTraceJson, @marketSnapshotJson, @orderJson, @outcomeJson,
        @qualityScore, @qualityFlagsJson, @learningEligible, @linkedJournalId, @linkedManagementCount,
        @wasExecuted, @mt5Ticket, @atIso, @createdAt, @updatedAt
      )
      ON CONFLICT(decision_id) DO UPDATE SET
        cycle_no = excluded.cycle_no,
        side = excluded.side,
        strategy = excluded.strategy,
        regime = excluded.regime,
        confluence = excluded.confluence,
        fitness = excluded.fitness,
        entry = excluded.entry,
        sl = excluded.sl,
        tp = excluded.tp,
        volume = excluded.volume,
        rrr = excluded.rrr,
        risk_gate = excluded.risk_gate,
        rationale = excluded.rationale,
        translated_th = excluded.translated_th,
        decision_type = excluded.decision_type,
        block_category = excluded.block_category,
        deterministic_json = excluded.deterministic_json,
        analysis_json = excluded.analysis_json,
        signals_json = excluded.signals_json,
        gate_trace_json = excluded.gate_trace_json,
        market_snapshot_json = excluded.market_snapshot_json,
        order_json = excluded.order_json,
        outcome_json = excluded.outcome_json,
        quality_score = excluded.quality_score,
        quality_flags_json = excluded.quality_flags_json,
        learning_eligible = excluded.learning_eligible,
        linked_journal_id = excluded.linked_journal_id,
        linked_management_count = excluded.linked_management_count,
        was_executed = excluded.was_executed,
        mt5_ticket = excluded.mt5_ticket,
        updated_at = excluded.updated_at`
    );
    const tx = db.transaction((rows: CycleDecision[]) => {
      const now = Date.now();
      for (const d of rows) {
        const payload = this.decisionFeedPayload(cycleNo, timeframe, d, now);
        stmt.run({
          ...payload,
          wasExecuted: d.executed ? 1 : 0,
        });
        this.appendDecisionJsonl(payload);
      }
    });
    tx(decisions);
  }

  public markDecisionOutcome(decisionId: string, outcome: Record<string, unknown>): void {
    const existing = db.prepare(
      `SELECT outcome_json FROM auto_trading_decision_feed WHERE decision_id = ?`
    ).get(decisionId) as { outcome_json?: string } | undefined;
    const previous = safeJsonParse(existing?.outcome_json, {} as Record<string, unknown>);
    const merged = {
      ...previous,
      ...outcome,
      updatedAt: Date.now(),
    };
    const linkedJournal = db.prepare(`SELECT id FROM auto_trading_journal WHERE decision_id = ?`).get(decisionId) as { id: number } | undefined;
    const linkedManagement = db.prepare(
      `SELECT COUNT(*) AS count FROM auto_trading_management_journal WHERE related_decision_id = ?`
    ).get(decisionId) as { count: number } | undefined;
    db.prepare(
      `UPDATE auto_trading_decision_feed
       SET outcome_json = ?, linked_journal_id = ?, linked_management_count = ?, updated_at = ?
       WHERE decision_id = ?`
    ).run(JSON.stringify(merged), linkedJournal?.id ?? null, Number(linkedManagement?.count ?? 0), Date.now(), decisionId);
    this.appendDecisionJsonl({
      schemaVersion: 1,
      recordType: 'TRADE_OUTCOME',
      decisionId,
      outcome: merged,
      atIso: new Date().toISOString(),
      atLocal: decisionLogLocalIso(),
      timeZone: decisionLogTimeZone,
      createdAt: Date.now(),
    });
  }

  public upsertJournal(row: JournalRow): void {
    const q = evaluateJournalQuality(row);
    const linkedDecision = db.prepare(`SELECT id FROM auto_trading_decision_feed WHERE decision_id = ?`).get(row.decisionId) as { id: number } | undefined;
    const managementCount = db.prepare(
      `SELECT COUNT(*) AS count FROM auto_trading_management_journal WHERE related_decision_id = ?`
    ).get(row.decisionId) as { count: number } | undefined;
    db.prepare(
      `INSERT INTO auto_trading_journal (
        decision_id, symbol, timeframe, side, strategy, analyzers_used, signals_json, confluence_score,
        entry, sl, tp, volume, risk_pct, rrr, regime, market_snapshot, was_executed, mt5_ticket,
        close_reason, close_price, close_at, profit, profit_r, outcome, ai_review, model_id,
        decision_feed_id, management_count, quality_score, quality_flags_json, learning_eligible, data_version,
        created_at, updated_at
      ) VALUES (
        @decisionId, @symbol, @timeframe, @side, @strategy, @analyzersUsed, @signalsJson, @confluenceScore,
        @entry, @sl, @tp, @volume, @riskPct, @rrr, @regime, @marketSnapshot, @wasExecuted, @mt5Ticket,
        @closeReason, @closePrice, @closeAt, @profit, @profitR, @outcome, @aiReview, @modelId,
        @decisionFeedId, @managementCount, @qualityScore, @qualityFlagsJson, @learningEligible, @dataVersion,
        @createdAt, @updatedAt
      )
      ON CONFLICT(decision_id) DO UPDATE SET
        side = excluded.side,
        strategy = excluded.strategy,
        analyzers_used = excluded.analyzers_used,
        signals_json = excluded.signals_json,
        confluence_score = excluded.confluence_score,
        entry = excluded.entry,
        sl = excluded.sl,
        tp = excluded.tp,
        volume = excluded.volume,
        risk_pct = excluded.risk_pct,
        rrr = excluded.rrr,
        regime = excluded.regime,
        market_snapshot = excluded.market_snapshot,
        was_executed = excluded.was_executed,
        mt5_ticket = excluded.mt5_ticket,
        close_reason = excluded.close_reason,
        close_price = excluded.close_price,
        close_at = excluded.close_at,
        profit = excluded.profit,
        profit_r = excluded.profit_r,
        outcome = excluded.outcome,
        ai_review = excluded.ai_review,
        model_id = excluded.model_id,
        decision_feed_id = excluded.decision_feed_id,
        management_count = excluded.management_count,
        quality_score = excluded.quality_score,
        quality_flags_json = excluded.quality_flags_json,
        learning_eligible = excluded.learning_eligible,
        data_version = excluded.data_version,
        updated_at = excluded.updated_at`
    ).run({
      ...row,
      wasExecuted: row.wasExecuted ? 1 : 0,
      decisionFeedId: linkedDecision?.id ?? row.decisionFeedId ?? null,
      managementCount: Number(managementCount?.count ?? row.managementCount ?? 0),
      qualityScore: q.score,
      qualityFlagsJson: JSON.stringify(q.flags),
      learningEligible: q.eligible ? 1 : 0,
      dataVersion: 2,
    });
    if (linkedDecision?.id) {
      const journal = db.prepare(`SELECT id FROM auto_trading_journal WHERE decision_id = ?`).get(row.decisionId) as { id: number } | undefined;
      db.prepare(
        `UPDATE auto_trading_decision_feed
         SET linked_journal_id = ?, linked_management_count = ?, updated_at = ?
         WHERE id = ?`
      ).run(journal?.id ?? null, Number(managementCount?.count ?? 0), Date.now(), linkedDecision.id);
    }
  }

  public insertManagementJournal(args: {
    managementId: string;
    relatedDecisionId?: string | null;
    symbol: string;
    timeframe: string;
    mode: string;
    status: string;
    side?: string | null;
    targetTicket?: number | null;
    createdTicket?: number | null;
    volume?: number | null;
    sizeFraction?: number | null;
    reason: string;
    playbook?: PlaybookScores | null;
    marketContext?: Record<string, unknown>;
    aiContext?: Record<string, unknown>;
    result?: Record<string, unknown>;
  }): void {
    const flags = [
      ...(args.relatedDecisionId ? [] : ['UNLINKED_DECISION']),
      ...(args.status === 'EXECUTED' && !args.targetTicket && !args.createdTicket ? ['EXECUTED_MISSING_TICKET'] : []),
      ...(args.reason ? [] : ['MISSING_REASON']),
    ];
    const q = quality(flags, args.status === 'EXECUTED' && flags.length === 0);
    db.prepare(
      `INSERT INTO auto_trading_management_journal (
        management_id, related_decision_id, symbol, timeframe, mode, status, side, target_ticket, created_ticket,
        volume, size_fraction, reason, playbook_json, market_context_json, ai_context_json, result_json,
        quality_score, quality_flags_json, learning_eligible, created_at, updated_at
      ) VALUES (
        @managementId, @relatedDecisionId, @symbol, @timeframe, @mode, @status, @side, @targetTicket, @createdTicket,
        @volume, @sizeFraction, @reason, @playbookJson, @marketContextJson, @aiContextJson, @resultJson,
        @qualityScore, @qualityFlagsJson, @learningEligible, @createdAt, @updatedAt
      )
      ON CONFLICT(management_id) DO UPDATE SET
        status = excluded.status,
        created_ticket = excluded.created_ticket,
        result_json = excluded.result_json,
        quality_score = excluded.quality_score,
        quality_flags_json = excluded.quality_flags_json,
        learning_eligible = excluded.learning_eligible,
        updated_at = excluded.updated_at`
    ).run({
      managementId: args.managementId,
      relatedDecisionId: args.relatedDecisionId ?? null,
      symbol: args.symbol,
      timeframe: args.timeframe,
      mode: args.mode,
      status: args.status,
      side: args.side ?? null,
      targetTicket: args.targetTicket ?? null,
      createdTicket: args.createdTicket ?? null,
      volume: args.volume ?? null,
      sizeFraction: args.sizeFraction ?? null,
      reason: args.reason,
      playbookJson: JSON.stringify(args.playbook ?? {}),
      marketContextJson: JSON.stringify(args.marketContext ?? {}),
      aiContextJson: JSON.stringify(args.aiContext ?? {}),
      resultJson: JSON.stringify(args.result ?? {}),
      qualityScore: q.score,
      qualityFlagsJson: JSON.stringify(q.flags),
      learningEligible: q.eligible ? 1 : 0,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    });
  }

  public markManagementOutcome(
    ticket: number,
    decisionId: string,
    outcome: 'WIN' | 'LOSS' | 'BE',
    profit: number,
    profitR: number | null,
    closeReason: string
  ): void {
    const rows = db.prepare(
      `SELECT management_id, result_json
       FROM auto_trading_management_journal
       WHERE status = 'EXECUTED'
         AND (target_ticket = ? OR created_ticket = ? OR related_decision_id = ?)
       ORDER BY created_at DESC
       LIMIT 20`
    ).all(ticket, ticket, decisionId) as any[];

    for (const row of rows) {
      const previous = safeJsonParse(row.result_json, {} as Record<string, unknown>);
      db.prepare(
        `UPDATE auto_trading_management_journal
         SET result_json = ?, updated_at = ?
         WHERE management_id = ?`
      ).run(
        JSON.stringify({
          ...previous,
          evaluated: true,
          closedTicket: ticket,
          outcome,
          profit: round2(profit),
          profitR: profitR === null ? null : round2(profitR),
          closeReason,
          evaluatedAt: Date.now(),
        }),
        Date.now(),
        row.management_id
      );
    }
  }

  public async getManagementJournal(limit = 100): Promise<ManagementJournalRow[]> {
    const rows = db.prepare(
      `SELECT * FROM auto_trading_management_journal 
       ORDER BY created_at DESC 
       LIMIT ?`
    ).all(limit) as any[];
    return rows.map(row => ({
      ...row,
      playbookJson: row.playbook_json,
      marketContextJson: row.market_context_json,
      aiContextJson: row.ai_context_json,
      resultJson: row.result_json,
    }));
  }

  public async getDecisionJournal(limit = 120): Promise<JournalRow[]> {
    const rows = db.prepare(
      `SELECT * FROM auto_trading_journal
       ORDER BY created_at DESC
       LIMIT ?`
    ).all(limit) as any[];
    return rows.map((row) => this.normalizeClosedJournalOutcome(this.mapJournalRow(row)));
  }

  public async getLearningJournal(limit = 400): Promise<JournalRow[]> {
    const rows = db.prepare(
      `SELECT * FROM auto_trading_journal
       WHERE outcome IN ('WIN', 'LOSS', 'BE')
         AND learning_eligible = 1
       ORDER BY updated_at DESC
       LIMIT ?`
    ).all(limit) as any[];
    return rows.map((row) => this.normalizeClosedJournalOutcome(this.mapJournalRow(row)));
  }

  public getLearningQualitySummary(): {
    eligible: number;
    excluded: number;
    avgQualityScore: number;
    flags: Record<string, number>;
  } {
    const rows = db.prepare(
      `SELECT learning_eligible, quality_score, quality_flags_json
       FROM auto_trading_journal
       WHERE outcome IN ('WIN', 'LOSS', 'BE')`
    ).all() as any[];
    const flags: Record<string, number> = {};
    let eligible = 0;
    let scoreTotal = 0;
    for (const row of rows) {
      if (Number(row.learning_eligible) === 1) eligible++;
      const score = Number(row.quality_score);
      if (Number.isFinite(score)) scoreTotal += score;
      const parsed = safeJsonParse(row.quality_flags_json, [] as string[]);
      if (Array.isArray(parsed)) {
        for (const flag of parsed) {
          flags[String(flag)] = (flags[String(flag)] || 0) + 1;
        }
      }
    }
    return {
      eligible,
      excluded: Math.max(0, rows.length - eligible),
      avgQualityScore: rows.length ? round2(scoreTotal / rows.length) : 0,
      flags,
    };
  }

  public getDecisionBlockStats(limit = 1000): Array<{ blockCategory: string; count: number }> {
    return db.prepare(
      `SELECT COALESCE(block_category, 'UNKNOWN') AS blockCategory, COUNT(*) AS count
       FROM (
         SELECT block_category
         FROM auto_trading_decision_feed
         WHERE decision_type IS NOT NULL
           AND learning_eligible = 1
         ORDER BY created_at DESC
         LIMIT ?
       )
       GROUP BY blockCategory
       ORDER BY count DESC`
    ).all(limit) as Array<{ blockCategory: string; count: number }>;
  }

  public getManagementLearningStats(limit = 1000): Array<{ mode: string; status: string; count: number; wins?: number; losses?: number; avgR?: number | null }> {
    const rows = db.prepare(
      `SELECT mode, status, result_json
       FROM auto_trading_management_journal
       WHERE learning_eligible = 1
       ORDER BY updated_at DESC
       LIMIT ?`
    ).all(limit) as any[];
    const map = new Map<string, { mode: string; status: string; count: number; wins: number; losses: number; rTotal: number; rCount: number }>();
    for (const row of rows) {
      const key = `${row.mode}|${row.status}`;
      const stat = map.get(key) || { mode: String(row.mode), status: String(row.status), count: 0, wins: 0, losses: 0, rTotal: 0, rCount: 0 };
      stat.count += 1;
      const result = safeJsonParse(row.result_json, {} as any);
      if (result?.outcome === 'WIN') stat.wins += 1;
      if (result?.outcome === 'LOSS') stat.losses += 1;
      const r = Number(result?.profitR);
      if (Number.isFinite(r)) {
        stat.rTotal += r;
        stat.rCount += 1;
      }
      map.set(key, stat);
    }
    return Array.from(map.values())
      .sort((a, b) => b.count - a.count)
      .map((it) => ({
        mode: it.mode,
        status: it.status,
        count: it.count,
        wins: it.wins,
        losses: it.losses,
        avgR: it.rCount ? round2(it.rTotal / it.rCount) : null,
      }));
  }

  public async getDecisionFeed(limit = 180): Promise<any[]> {
    const rows = db.prepare(
      `SELECT * FROM auto_trading_decision_feed
       ORDER BY created_at DESC
       LIMIT ?`
    ).all(limit) as any[];
    return rows.map((row) => ({
      id: Number(row.id),
      decisionId: String(row.decision_id),
      cycleNo: Number(row.cycle_no),
      symbol: String(row.symbol),
      timeframe: String(row.timeframe),
      side: String(row.side),
      strategy: String(row.strategy),
      regime: String(row.regime),
      confluence: Number(row.confluence),
      fitness: Number(row.fitness),
      entry: row.entry === null ? null : Number(row.entry),
      sl: row.sl === null ? null : Number(row.sl),
      tp: row.tp === null ? null : Number(row.tp),
      volume: row.volume === null ? null : Number(row.volume),
      rrr: row.rrr === null ? null : Number(row.rrr),
      riskGate: row.risk_gate === null ? null : String(row.risk_gate),
      rationale: row.rationale === null ? null : String(row.rationale),
      translatedTh: row.translated_th === null ? null : String(row.translated_th),
      decisionType: row.decision_type === null ? null : String(row.decision_type),
      blockCategory: row.block_category === null ? null : String(row.block_category),
      deterministic: safeJsonParse(row.deterministic_json, {}),
      analysis: safeJsonParse(row.analysis_json, {}),
      signals: safeJsonParse(row.signals_json, {}),
      gateTrace: safeJsonParse(row.gate_trace_json, []),
      marketSnapshot: safeJsonParse(row.market_snapshot_json, {}),
      order: safeJsonParse(row.order_json, {}),
      outcome: safeJsonParse(row.outcome_json, {}),
      qualityScore: row.quality_score === null || row.quality_score === undefined ? null : Number(row.quality_score),
      qualityFlags: safeJsonParse(row.quality_flags_json, []),
      learningEligible: Number(row.learning_eligible) === 1,
      linkedJournalId: row.linked_journal_id === null || row.linked_journal_id === undefined ? null : Number(row.linked_journal_id),
      linkedManagementCount: Number(row.linked_management_count ?? 0),
      wasExecuted: Number(row.was_executed) === 1,
      mt5Ticket: row.mt5_ticket === null ? null : Number(row.mt5_ticket),
      at: row.at_iso === null ? null : String(row.at_iso),
      createdAt: Number(row.created_at),
      updatedAt: row.updated_at === null ? null : Number(row.updated_at),
    }));
  }

  private decisionFeedPayload(cycleNo: number, timeframe: string, d: CycleDecision, now: number): Record<string, unknown> {
    const linkedJournal = db.prepare(`SELECT id FROM auto_trading_journal WHERE decision_id = ?`).get(d.decisionId) as { id: number } | undefined;
    const linkedManagement = db.prepare(
      `SELECT COUNT(*) AS count FROM auto_trading_management_journal WHERE related_decision_id = ?`
    ).get(d.decisionId) as { count: number } | undefined;
    const payload: Record<string, unknown> = {
      schemaVersion: 1,
      recordType: 'TRADE_DECISION',
      decisionId: d.decisionId,
      cycleNo,
      symbol: d.symbol,
      timeframe,
      side: d.side,
      strategy: d.strategy,
      regime: d.regime,
      overallBias: d.overallBias,
      confluence: d.confluence,
      fitness: d.fitness,
      entry: d.entry,
      sl: d.sl,
      tp: d.tp,
      volume: d.volume,
      rrr: d.rrr,
      riskGate: d.riskGate,
      rationale: d.rationale,
      translatedTh: d.translatedTh ?? null,
      decisionType: d.decisionType ?? (d.executed ? 'ORDER_EXECUTED' : d.side === 'SKIP' ? 'ORDER_BLOCKED' : 'ORDER_ALLOWED'),
      blockCategory: d.blockCategory ?? null,
      deterministic: d.deterministic ?? {},
      analysis: d.analysisSnapshot ?? {},
      signals: d.signals ?? {},
      gateTrace: d.gateTrace ?? [],
      marketSnapshot: d.marketSnapshotLog ?? {},
      order: d.order ?? {},
      outcome: d.outcome ?? {},
      wasExecuted: d.executed,
      mt5Ticket: d.mt5Ticket,
      atIso: d.at,
      atLocal: decisionLogLocalIso(d.at ? new Date(d.at) : new Date(now)),
      timeZone: decisionLogTimeZone,
      createdAt: now,
      updatedAt: now,
    };
    const q = evaluateDecisionQuality(payload);
    return {
      ...payload,
      quality: {
        score: q.score,
        flags: q.flags,
        learningEligible: q.eligible,
      },
      qualityScore: q.score,
      qualityFlagsJson: JSON.stringify(q.flags),
      learningEligible: q.eligible ? 1 : 0,
      linkedJournalId: linkedJournal?.id ?? null,
      linkedManagementCount: Number(linkedManagement?.count ?? 0),
      deterministicJson: JSON.stringify(payload.deterministic),
      analysisJson: JSON.stringify(payload.analysis),
      signalsJson: JSON.stringify(payload.signals),
      gateTraceJson: JSON.stringify(payload.gateTrace),
      marketSnapshotJson: JSON.stringify(payload.marketSnapshot),
      orderJson: JSON.stringify(payload.order),
      outcomeJson: JSON.stringify(payload.outcome),
    };
  }

  private appendDecisionJsonl(payload: Record<string, unknown>): void {
    try {
      mkdirSync(tradeDecisionLogDir, { recursive: true });
      const dateKey = decisionLogDateKey();
      appendFileSync(join(tradeDecisionLogDir, `${dateKey}.jsonl`), `${JSON.stringify(payload)}\n`, 'utf8');
    } catch {
      // File logging is secondary to SQLite persistence.
    }
  }

  private normalizeConfig(input: Partial<AutoTradingConfig> | null | undefined, base: AutoTradingConfig): AutoTradingConfig {
    const next = { ...base, ...(input || {}) };
    return {
      ...next,
      watchlist: Array.from(new Set((next.watchlist || base.watchlist).map((it) => String(it).trim().toUpperCase()).filter(Boolean))),
      timeframe: String(next.timeframe || base.timeframe).trim().toUpperCase(),
      preferredStrategies: Array.from(new Set((next.preferredStrategies || base.preferredStrategies).map((it) => String(it) as any))),
      symbolBlacklist: Array.from(new Set((next.symbolBlacklist || base.symbolBlacklist).map((it) => String(it).trim().toUpperCase()).filter(Boolean))),
      risk: { ...base.risk, ...(next.risk || {}) },
      strategy: { ...base.strategy, ...(next.strategy || {}) },
      adaptive: {
        ...(base.adaptive || {}),
        ...(next.adaptive || {}),
        v25: {
          ...((base.adaptive || {}).v25 || {}),
          ...((next.adaptive || {}).v25 || {}),
        },
      },
      aiModel: next.aiModel || base.aiModel,
      apiKey: next.apiKey || base.apiKey,
      agentPrompt: next.agentPrompt || base.agentPrompt,
      // Per-agent model overrides survive shallow merges; we deep-merge the
      // agentModels bag and ensure legacy string-based configs are normalized
      // to the new {provider, model, useSmartFree} object format.
      agentModels: (() => {
        const baseBag = (base.agentModels || {}) as Record<string, any>;
        const nextBag = (next.agentModels || {}) as Record<string, any>;
        const merged: Record<string, any> = { ...baseBag };
        
        for (const role of Object.keys(nextBag)) {
          const val = nextBag[role];
          if (!val) continue;
          
          if (typeof val === 'string') {
            // Legacy format: "provider/model" or just "model"
            if (val.includes('/')) {
              const parts = val.split('/');
              merged[role] = { provider: parts[0], model: parts.slice(1).join('/') };
            } else {
              merged[role] = { provider: 'gemini', model: val };
            }
          } else {
            // Modern format: object
            merged[role] = { ...val };
          }
        }
        return merged;
      })(),
      providerKeys: { ...((base as any).providerKeys || {}), ...((next as any).providerKeys || {}) },
      providerBaseUrls: { ...((base as any).providerBaseUrls || {}), ...((next as any).providerBaseUrls || {}) },
      notificationSettings: { ...base.notificationSettings, ...(next.notificationSettings || {}) },
    };
  }

  private mapJournalRow(row: any): JournalRow {
    return {
      id: Number(row.id),
      decisionId: String(row.decision_id),
      symbol: String(row.symbol),
      timeframe: String(row.timeframe),
      side: String(row.side),
      strategy: String(row.strategy),
      analyzersUsed: String(row.analyzers_used ?? ''),
      signalsJson: String(row.signals_json ?? '{}'),
      confluenceScore: asNumber(row.confluence_score),
      entry: row.entry === null ? null : asNumber(row.entry),
      sl: row.sl === null ? null : asNumber(row.sl),
      tp: row.tp === null ? null : asNumber(row.tp),
      volume: row.volume === null ? null : asNumber(row.volume),
      riskPct: row.risk_pct === null ? null : asNumber(row.risk_pct),
      rrr: row.rrr === null ? null : asNumber(row.rrr),
      regime: row.regime === null ? null : String(row.regime),
      marketSnapshot: row.market_snapshot === null ? null : String(row.market_snapshot),
      wasExecuted: Number(row.was_executed) === 1,
      mt5Ticket: row.mt5_ticket === null ? null : asNumber(row.mt5_ticket),
      closeReason: row.close_reason === null ? null : String(row.close_reason),
      closePrice: row.close_price === null ? null : asNumber(row.close_price),
      closeAt: row.close_at === null ? null : asNumber(row.close_at),
      profit: row.profit === null ? null : asNumber(row.profit),
      profitR: row.profit_r === null ? null : asNumber(row.profit_r),
      outcome: String(row.outcome ?? 'OPEN'),
      aiReview: row.ai_review === null ? null : String(row.ai_review),
      modelId: row.model_id === null ? null : String(row.model_id),
      decisionFeedId: row.decision_feed_id === null || row.decision_feed_id === undefined ? null : asNumber(row.decision_feed_id),
      managementCount: row.management_count === null || row.management_count === undefined ? 0 : asNumber(row.management_count),
      qualityScore: row.quality_score === null || row.quality_score === undefined ? 1 : asNumber(row.quality_score),
      qualityFlags: safeJsonParse(row.quality_flags_json, [] as string[]),
      learningEligible: row.learning_eligible === null || row.learning_eligible === undefined ? true : Number(row.learning_eligible) === 1,
      dataVersion: row.data_version === null || row.data_version === undefined ? 1 : asNumber(row.data_version),
      createdAt: asNumber(row.created_at),
      updatedAt: asNumber(row.updated_at),
    };
  }

  private normalizeClosedJournalOutcome(row: JournalRow): JournalRow {
    if (row.outcome !== 'OPEN' && row.outcome !== 'PAPER') return row;
    if (!row.mt5Ticket) return row;
    const deal = db.prepare(`SELECT * FROM mt5_history WHERE ticket = ? AND (type = 0 OR type = 1) ORDER BY time DESC LIMIT 1`).get(row.mt5Ticket) as any;
    if (!deal) return row;
    const profit = asNumber(deal.profit) + asNumber(deal.swap) + asNumber(deal.commission);
    const sl = asNumber(row.sl);
    const entry = asNumber(row.entry);
    const profitR = profitCashToR(profit, entry, sl, asNumber(row.volume), row.symbol);
    return {
      ...row,
      outcome: classifyOutcome(profit, profitR),
      closePrice: asNumber(deal.price),
      closeAt: asNumber(deal.time) * 1000,
      profit: round2(profit),
      profitR: profitR === null ? null : round2(profitR),
      closeReason: 'MT5_EXTERNAL',
    };
  }
}

export const persistenceService = new PersistenceService();

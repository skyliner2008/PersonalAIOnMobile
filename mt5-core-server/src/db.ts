import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import Database from 'better-sqlite3';
import { config } from './config.js';

let _db: Database.Database | null = null;

export function initDb(): Database.Database {
  if (_db) return _db;
  fs.mkdirSync(path.dirname(config.dbPath), { recursive: true });
  _db = new Database(config.dbPath);
  _db.pragma('journal_mode = WAL');
  _db.exec(`
    CREATE TABLE IF NOT EXISTS mt5_trade_actions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      action_type TEXT NOT NULL,
      status TEXT NOT NULL,
      symbol TEXT,
      ticket TEXT,
      request_json TEXT,
      response_json TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS mt5_snapshots (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      snapshot_type TEXT NOT NULL,
      symbol TEXT,
      timeframe TEXT,
      payload_json TEXT NOT NULL,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS mt5_tracking_state (
      symbol TEXT NOT NULL,
      timeframe TEXT NOT NULL,
      last_candle_time INTEGER,
      indicators_json TEXT DEFAULT '{}',
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY(symbol, timeframe)
    );

    CREATE TABLE IF NOT EXISTS mt5_candle_cache (
      symbol TEXT NOT NULL,
      timeframe TEXT NOT NULL,
      t INTEGER NOT NULL,
      o REAL NOT NULL,
      h REAL NOT NULL,
      l REAL NOT NULL,
      c REAL NOT NULL,
      v REAL NOT NULL DEFAULT 0,
      source TEXT DEFAULT 'broker',
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY(symbol, timeframe, t)
    );

    CREATE TABLE IF NOT EXISTS api_tokens (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      label TEXT,
      token_hash TEXT NOT NULL UNIQUE,
      token_prefix TEXT NOT NULL,
      is_active INTEGER DEFAULT 1,
      last_used_at DATETIME,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      expires_at DATETIME
    );

    CREATE TABLE IF NOT EXISTS api_token_usage (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      token_id INTEGER NOT NULL,
      path TEXT NOT NULL,
      method TEXT NOT NULL,
      ip TEXT,
      user_agent TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY(token_id) REFERENCES api_tokens(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS pairing_requests (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id TEXT,
      device_name TEXT,
      app_version TEXT,
      token_hash TEXT NOT NULL UNIQUE,
      token_prefix TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'PENDING',
      request_count INTEGER NOT NULL DEFAULT 1,
      requested_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      last_seen_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      approved_at DATETIME,
      approved_by TEXT
    );

    CREATE TABLE IF NOT EXISTS auto_trading_config (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      config_json TEXT NOT NULL,
      ai_model TEXT,
      api_key TEXT,
      agent_prompt TEXT,
      notification_settings_json TEXT DEFAULT '{}',
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS auto_trading_runtime (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      state_json TEXT NOT NULL,
      last_decisions_json TEXT NOT NULL DEFAULT '[]',
      open_journal_json TEXT NOT NULL DEFAULT '[]',
      learn_summary_json TEXT,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS auto_trading_journal (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      decision_id TEXT NOT NULL UNIQUE,
      symbol TEXT NOT NULL,
      timeframe TEXT NOT NULL,
      side TEXT NOT NULL,
      strategy TEXT NOT NULL,
      analyzers_used TEXT NOT NULL DEFAULT '',
      signals_json TEXT NOT NULL DEFAULT '{}',
      confluence_score REAL NOT NULL DEFAULT 0,
      entry REAL,
      sl REAL,
      tp REAL,
      volume REAL,
      risk_pct REAL,
      rrr REAL,
      regime TEXT,
      market_snapshot TEXT,
      was_executed INTEGER NOT NULL DEFAULT 0,
      mt5_ticket INTEGER,
      close_reason TEXT,
      close_price REAL,
      close_at INTEGER,
      profit REAL,
      profit_r REAL,
      outcome TEXT NOT NULL DEFAULT 'OPEN',
      ai_review TEXT,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS auto_trading_management_journal (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      management_id TEXT NOT NULL UNIQUE,
      related_decision_id TEXT,
      symbol TEXT NOT NULL,
      timeframe TEXT NOT NULL,
      mode TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'PLANNED',
      side TEXT,
      target_ticket INTEGER,
      created_ticket INTEGER,
      volume REAL,
      size_fraction REAL,
      reason TEXT NOT NULL,
      playbook_json TEXT NOT NULL DEFAULT '{}',
      market_context_json TEXT NOT NULL DEFAULT '{}',
      ai_context_json TEXT NOT NULL DEFAULT '{}',
      result_json TEXT NOT NULL DEFAULT '{}',
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS auto_trading_decision_feed (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      decision_id TEXT NOT NULL UNIQUE,
      cycle_no INTEGER NOT NULL,
      symbol TEXT NOT NULL,
      timeframe TEXT NOT NULL,
      side TEXT NOT NULL,
      strategy TEXT NOT NULL,
      regime TEXT,
      confluence REAL NOT NULL DEFAULT 0,
      fitness REAL NOT NULL DEFAULT 0,
      entry REAL,
      sl REAL,
      tp REAL,
      volume REAL,
      rrr REAL,
      risk_gate TEXT,
      rationale TEXT,
      translated_th TEXT,
      decision_type TEXT,
      block_category TEXT,
      deterministic_json TEXT NOT NULL DEFAULT '{}',
      analysis_json TEXT NOT NULL DEFAULT '{}',
      signals_json TEXT NOT NULL DEFAULT '{}',
      gate_trace_json TEXT NOT NULL DEFAULT '[]',
      market_snapshot_json TEXT NOT NULL DEFAULT '{}',
      order_json TEXT NOT NULL DEFAULT '{}',
      outcome_json TEXT NOT NULL DEFAULT '{}',
      was_executed INTEGER NOT NULL DEFAULT 0,
      mt5_ticket INTEGER,
      at_iso TEXT,
      created_at INTEGER NOT NULL,
      updated_at INTEGER
    );

    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      role TEXT DEFAULT 'admin',
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS knowledge_nodes (
      id TEXT PRIMARY KEY,
      type TEXT NOT NULL,
      label TEXT NOT NULL,
      properties_json TEXT DEFAULT '{}',
      embedding BLOB,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS knowledge_edges (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      source_id TEXT NOT NULL,
      target_id TEXT NOT NULL,
      relation TEXT NOT NULL,
      weight REAL DEFAULT 1.0,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      UNIQUE(source_id, target_id, relation),
      FOREIGN KEY(source_id) REFERENCES knowledge_nodes(id) ON DELETE CASCADE,
      FOREIGN KEY(target_id) REFERENCES knowledge_nodes(id) ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_mt5_trade_actions_created ON mt5_trade_actions(created_at);
    CREATE INDEX IF NOT EXISTS idx_mt5_snapshots_type_created ON mt5_snapshots(snapshot_type, created_at);
    CREATE INDEX IF NOT EXISTS idx_mt5_candle_cache_lookup ON mt5_candle_cache(symbol, timeframe, t DESC);
    CREATE INDEX IF NOT EXISTS idx_auto_trading_journal_created ON auto_trading_journal(created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_auto_trading_journal_open ON auto_trading_journal(outcome, updated_at DESC);
    CREATE INDEX IF NOT EXISTS idx_auto_trading_management_created ON auto_trading_management_journal(created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_auto_trading_management_symbol ON auto_trading_management_journal(symbol, mode, created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_auto_trading_decision_feed_created ON auto_trading_decision_feed(created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_auto_trading_decision_feed_symbol ON auto_trading_decision_feed(symbol, created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_api_tokens_active ON api_tokens(is_active, created_at);
    CREATE INDEX IF NOT EXISTS idx_api_usage_token_created ON api_token_usage(token_id, created_at);
    CREATE INDEX IF NOT EXISTS idx_pairing_requests_status ON pairing_requests(status, requested_at);
    CREATE INDEX IF NOT EXISTS idx_pairing_requests_device ON pairing_requests(device_id, last_seen_at);
    CREATE INDEX IF NOT EXISTS idx_knowledge_nodes_type ON knowledge_nodes(type);
    CREATE INDEX IF NOT EXISTS idx_knowledge_edges_lookup ON knowledge_edges(source_id, relation);
  `);

  // ตรวจสอบและสร้างผู้ใช้เริ่มต้น admin/admin
  const userCount = _db.prepare(`SELECT count(*) as count FROM users`).get() as { count: number };
  if (userCount.count === 0) {
    const hash = crypto.createHash('sha256').update('admin').digest('hex');
    _db.prepare(`INSERT INTO users (username, password_hash) VALUES (?, ?)`).run('admin', hash);
    console.log('[db] Inserted default user admin/admin');
  }

  // --- MIGRATIONS ---
  const tableInfo = _db.prepare(`PRAGMA table_info(auto_trading_config)`).all() as any[];
  const hasColumn = (name: string) => tableInfo.some(c => c.name === name);

  if (!hasColumn('ai_model')) {
    _db.exec(`ALTER TABLE auto_trading_config ADD COLUMN ai_model TEXT`);
  }
  if (!hasColumn('api_key')) {
    _db.exec(`ALTER TABLE auto_trading_config ADD COLUMN api_key TEXT`);
  }
  if (!hasColumn('agent_prompt')) {
    _db.exec(`ALTER TABLE auto_trading_config ADD COLUMN agent_prompt TEXT`);
  }
  if (!hasColumn('notification_settings_json')) {
    _db.exec(`ALTER TABLE auto_trading_config ADD COLUMN notification_settings_json TEXT DEFAULT '{}'`);
  }

  const decisionFeedInfo = _db.prepare(`PRAGMA table_info(auto_trading_decision_feed)`).all() as any[];
  if (!decisionFeedInfo.some(c => c.name === 'translated_th')) {
    _db.exec(`ALTER TABLE auto_trading_decision_feed ADD COLUMN translated_th TEXT`);
  }
  const addDecisionFeedColumn = (name: string, sql: string) => {
    if (!decisionFeedInfo.some(c => c.name === name)) {
      _db!.exec(`ALTER TABLE auto_trading_decision_feed ADD COLUMN ${sql}`);
    }
  };
  addDecisionFeedColumn('decision_type', `decision_type TEXT`);
  addDecisionFeedColumn('block_category', `block_category TEXT`);
  addDecisionFeedColumn('deterministic_json', `deterministic_json TEXT NOT NULL DEFAULT '{}'`);
  addDecisionFeedColumn('analysis_json', `analysis_json TEXT NOT NULL DEFAULT '{}'`);
  addDecisionFeedColumn('signals_json', `signals_json TEXT NOT NULL DEFAULT '{}'`);
  addDecisionFeedColumn('gate_trace_json', `gate_trace_json TEXT NOT NULL DEFAULT '[]'`);
  addDecisionFeedColumn('market_snapshot_json', `market_snapshot_json TEXT NOT NULL DEFAULT '{}'`);
  addDecisionFeedColumn('order_json', `order_json TEXT NOT NULL DEFAULT '{}'`);
  addDecisionFeedColumn('outcome_json', `outcome_json TEXT NOT NULL DEFAULT '{}'`);
  addDecisionFeedColumn('updated_at', `updated_at INTEGER`);
  addDecisionFeedColumn('quality_score', `quality_score REAL NOT NULL DEFAULT 1`);
  addDecisionFeedColumn('quality_flags_json', `quality_flags_json TEXT NOT NULL DEFAULT '[]'`);
  addDecisionFeedColumn('learning_eligible', `learning_eligible INTEGER NOT NULL DEFAULT 1`);
  addDecisionFeedColumn('linked_journal_id', `linked_journal_id INTEGER`);
  addDecisionFeedColumn('linked_management_count', `linked_management_count INTEGER NOT NULL DEFAULT 0`);
  _db.exec(`CREATE INDEX IF NOT EXISTS idx_auto_trading_decision_feed_block ON auto_trading_decision_feed(block_category, created_at DESC)`);
  _db.exec(`CREATE INDEX IF NOT EXISTS idx_auto_trading_decision_feed_learning ON auto_trading_decision_feed(learning_eligible, decision_type, created_at DESC)`);

  // Migration: add UNIQUE index on knowledge_edges for DBs created before this constraint
  const keIndexes = _db.prepare(`PRAGMA index_list(knowledge_edges)`).all() as any[];
  const hasUniqueKe = keIndexes.some((idx: any) => {
    if (!idx.unique) return false;
    const cols = (_db as any).prepare(`PRAGMA index_info(${idx.name})`).all() as any[];
    const names = cols.map((c: any) => c.name).sort().join(',');
    return names === 'relation,source_id,target_id';
  });
  if (!hasUniqueKe) {
    _db.exec(`
      DELETE FROM knowledge_edges
      WHERE id NOT IN (
        SELECT MIN(id) FROM knowledge_edges
        GROUP BY source_id, target_id, relation
      );
      CREATE UNIQUE INDEX IF NOT EXISTS idx_ke_unique ON knowledge_edges(source_id, target_id, relation);
    `);
  }

  // --- 2026-04-30: AI Model Statistics & Ranking Support ---
  _db.exec(`
    CREATE TABLE IF NOT EXISTS ai_model_stats (
      model_id TEXT PRIMARY KEY,
      provider_id TEXT NOT NULL,
      total_calls INTEGER DEFAULT 0,
      success_calls INTEGER DEFAULT 0,
      error_calls INTEGER DEFAULT 0,
      timeout_calls INTEGER DEFAULT 0,
      avg_latency_ms REAL DEFAULT 0,
      total_tokens INTEGER DEFAULT 0,
      total_profit_r REAL DEFAULT 0,
      win_count INTEGER DEFAULT 0,
      loss_count INTEGER DEFAULT 0,
      last_used_at DATETIME,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );
  `);

  // --- 2026-05-01: Per-Agent Model Statistics (V20.0) ---
  // Replaces the global ai_model_stats for routing decisions.
  // Each (model_id, agent_role) pair gets its own stats so:
  //  - Analyst scores are independent of ExecutionTrader scores
  //  - slTpAgent accuracy is tracked separately from reasoning quality
  //  - Time-based blacklist per role prevents a bad Analyst from blocking Execution
  _db.exec(`
    CREATE TABLE IF NOT EXISTS ai_model_agent_stats (
      model_id      TEXT    NOT NULL,
      provider_id   TEXT    NOT NULL DEFAULT 'openrouter',
      agent_role    TEXT    NOT NULL DEFAULT 'reasoning',
      total_calls   INTEGER DEFAULT 0,
      success_calls INTEGER DEFAULT 0,
      error_calls   INTEGER DEFAULT 0,
      timeout_calls INTEGER DEFAULT 0,
      content_fail_calls INTEGER DEFAULT 0,
      consecutive_failures INTEGER DEFAULT 0,
      avg_latency_ms REAL   DEFAULT 0,
      total_tokens  INTEGER DEFAULT 0,
      total_profit_r REAL   DEFAULT 0,
      win_count     INTEGER DEFAULT 0,
      loss_count    INTEGER DEFAULT 0,
      sl_tp_score   REAL    DEFAULT 0,
      blacklisted_until INTEGER DEFAULT 0,
      last_used_at  INTEGER DEFAULT 0,
      updated_at    INTEGER DEFAULT 0,
      PRIMARY KEY (model_id, agent_role)
    );

    CREATE INDEX IF NOT EXISTS idx_amas_role_score ON ai_model_agent_stats(agent_role, blacklisted_until, total_calls);
    CREATE INDEX IF NOT EXISTS idx_amas_last_used  ON ai_model_agent_stats(agent_role, last_used_at);
  `);

  const journalInfo = _db.prepare(`PRAGMA table_info(auto_trading_journal)`).all() as any[];
  if (!journalInfo.some(c => c.name === 'model_id')) {
    _db.exec(`ALTER TABLE auto_trading_journal ADD COLUMN model_id TEXT`);
  }
  const addJournalColumn = (name: string, sql: string) => {
    if (!journalInfo.some(c => c.name === name)) {
      _db!.exec(`ALTER TABLE auto_trading_journal ADD COLUMN ${sql}`);
    }
  };
  addJournalColumn('decision_feed_id', `decision_feed_id INTEGER`);
  addJournalColumn('management_count', `management_count INTEGER NOT NULL DEFAULT 0`);
  addJournalColumn('quality_score', `quality_score REAL NOT NULL DEFAULT 1`);
  addJournalColumn('quality_flags_json', `quality_flags_json TEXT NOT NULL DEFAULT '[]'`);
  addJournalColumn('learning_eligible', `learning_eligible INTEGER NOT NULL DEFAULT 1`);
  addJournalColumn('data_version', `data_version INTEGER NOT NULL DEFAULT 1`);
  _db.exec(`CREATE INDEX IF NOT EXISTS idx_auto_trading_journal_learning ON auto_trading_journal(learning_eligible, outcome, updated_at DESC)`);
  _db.exec(`CREATE INDEX IF NOT EXISTS idx_auto_trading_journal_ticket ON auto_trading_journal(mt5_ticket)`);

  const managementInfo = _db.prepare(`PRAGMA table_info(auto_trading_management_journal)`).all() as any[];
  const addManagementColumn = (name: string, sql: string) => {
    if (!managementInfo.some(c => c.name === name)) {
      _db!.exec(`ALTER TABLE auto_trading_management_journal ADD COLUMN ${sql}`);
    }
  };
  addManagementColumn('quality_score', `quality_score REAL NOT NULL DEFAULT 1`);
  addManagementColumn('quality_flags_json', `quality_flags_json TEXT NOT NULL DEFAULT '[]'`);
  addManagementColumn('learning_eligible', `learning_eligible INTEGER NOT NULL DEFAULT 1`);
  _db.exec(`CREATE INDEX IF NOT EXISTS idx_auto_trading_management_learning ON auto_trading_management_journal(learning_eligible, mode, status, updated_at DESC)`);

  return _db;
}

export const db = initDb();

export function getDb(): Database.Database {
  return db;
}

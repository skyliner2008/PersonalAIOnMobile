package com.example.personalaibot.db

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
    fun getAppDir(): String
}

fun createDatabase(databaseDriverFactory: DatabaseDriverFactory): JarvisDatabase {
    val driver = databaseDriverFactory.createDriver()

    // สร้าง schema ครั้งแรก
    try {
        JarvisDatabase.Schema.create(driver)
    } catch (_: Exception) {
        // Schema มีอยู่แล้ว — ข้ามขั้นตอนนี้
    }

    // ✅ Additive migration: สร้าง tables ที่เพิ่มใหม่ถ้ายังไม่มี
    // ใช้ CREATE TABLE IF NOT EXISTS เพื่อรองรับ users ที่มี DB เวอร์ชันเก่า
    ensureNewTablesExist(driver)

    return JarvisDatabase(driver)
}

/**
 * สร้าง tables ที่เพิ่มมาใน v2+ แบบ idempotent
 * ไม่กระทบ tables เดิมที่มีอยู่แล้ว
 */
private fun ensureNewTablesExist(driver: SqlDriver) {
    val statements = listOf(
        // Layer 1: Core Memory
        """CREATE TABLE IF NOT EXISTS CoreMemory (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL,
            updated_at INTEGER NOT NULL
        )""",
        // Layer 3: Archival Memory
        """CREATE TABLE IF NOT EXISTS ArchivalMemory (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            content TEXT NOT NULL,
            embedding_json TEXT,
            source_role TEXT NOT NULL,
            importance REAL NOT NULL DEFAULT 0.5,
            timestamp INTEGER NOT NULL,
            access_count INTEGER NOT NULL DEFAULT 0
        )""",
        // Layer 4: GraphRAG Nodes
        """CREATE TABLE IF NOT EXISTS KnowledgeNode (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            node_type TEXT NOT NULL,
            properties TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )""",
        // Layer 4: GraphRAG Edges
        """CREATE TABLE IF NOT EXISTS KnowledgeEdge (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_id INTEGER NOT NULL,
            target_id INTEGER NOT NULL,
            relation TEXT NOT NULL,
            weight REAL NOT NULL DEFAULT 1.0,
            created_at INTEGER NOT NULL
        )""",
        // User Profile
        """CREATE TABLE IF NOT EXISTS UserProfile (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            field TEXT NOT NULL UNIQUE,
            value TEXT NOT NULL,
            confidence REAL NOT NULL DEFAULT 0.8,
            updated_at INTEGER NOT NULL
        )""",
        // TV OHLCV candle cache
        """CREATE TABLE IF NOT EXISTS TvCandle (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            symbol TEXT NOT NULL,
            interval TEXT NOT NULL,
            source TEXT NOT NULL,
            ts INTEGER NOT NULL,
            open REAL NOT NULL,
            high REAL NOT NULL,
            low REAL NOT NULL,
            close REAL NOT NULL,
            volume REAL NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL,
            UNIQUE(symbol, interval, ts)
        )""",
        // MT5 account snapshots
        """CREATE TABLE IF NOT EXISTS Mt5AccountSnapshot (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            login TEXT,
            account_name TEXT,
            server TEXT,
            currency TEXT,
            leverage INTEGER,
            balance REAL,
            equity REAL,
            margin REAL,
            free_margin REAL,
            payload_json TEXT,
            updated_at INTEGER NOT NULL
        )""",
        // MT5 symbols
        """CREATE TABLE IF NOT EXISTS Mt5SymbolCatalog (
            symbol TEXT PRIMARY KEY,
            description TEXT,
            digits INTEGER,
            point REAL,
            trade_mode TEXT,
            bid REAL,
            ask REAL,
            spread REAL,
            payload_json TEXT,
            updated_at INTEGER NOT NULL
        )""",
        // MT5 positions/orders/deals
        """CREATE TABLE IF NOT EXISTS Mt5TradeRecord (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            record_type TEXT NOT NULL,
            ticket TEXT NOT NULL,
            position_ticket TEXT,
            symbol TEXT NOT NULL,
            side TEXT,
            volume REAL,
            price_open REAL,
            price_current REAL,
            profit REAL,
            swap REAL,
            commission REAL,
            sl REAL,
            tp REAL,
            state TEXT,
            comment TEXT,
            event_time INTEGER,
            payload_json TEXT,
            synced_at INTEGER NOT NULL,
            UNIQUE(record_type, ticket, event_time)
        )""",
        // Raw sync snapshots
        """CREATE TABLE IF NOT EXISTS TradeSyncSnapshot (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source TEXT NOT NULL,
            payload_json TEXT NOT NULL,
            synced_at INTEGER NOT NULL
        )""",
        // Optimization trials (Adaptive Optimize learning memory)
        """CREATE TABLE IF NOT EXISTS OptimizationTrial (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            symbol TEXT NOT NULL,
            interval TEXT NOT NULL,
            kind TEXT NOT NULL,
            sl_mult REAL NOT NULL,
            tp_mult REAL NOT NULL,
            score REAL NOT NULL,
            expectancy_r REAL,
            profit_factor REAL,
            trades INTEGER,
            delta_vs_baseline REAL,
            applied INTEGER NOT NULL DEFAULT 0,
            source TEXT NOT NULL DEFAULT 'adaptive',
            created_at INTEGER NOT NULL
        )""",
        // Migration: add unique index for KnowledgeNode.name (idempotent)
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_knowledge_node_name ON KnowledgeNode(name)",
        "CREATE INDEX IF NOT EXISTS idx_tv_candle_symbol_interval_ts ON TvCandle(symbol, interval, ts)",
        "CREATE INDEX IF NOT EXISTS idx_mt5_trade_type_time ON Mt5TradeRecord(record_type, event_time DESC)",
        // Signal Tracking Records (Closed-Loop Learning)
        """CREATE TABLE IF NOT EXISTS SignalTrackingRecord (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            signal_id TEXT NOT NULL UNIQUE,
            symbol TEXT NOT NULL,
            interval TEXT NOT NULL,
            strategy TEXT NOT NULL,
            side TEXT NOT NULL,
            entry_price REAL NOT NULL,
            stop_loss REAL NOT NULL,
            take_profit REAL NOT NULL,
            rr REAL NOT NULL,
            status TEXT NOT NULL DEFAULT 'OPEN',
            mfe REAL NOT NULL DEFAULT 0.0,
            mae REAL NOT NULL DEFAULT 0.0,
            exit_price REAL,
            pnl_r REAL,
            bars_held INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            closed_at INTEGER
        )""",
        "CREATE INDEX IF NOT EXISTS idx_signal_tracking_symbol_status ON SignalTrackingRecord(symbol, status)",
        "CREATE INDEX IF NOT EXISTS idx_signal_tracking_strategy ON SignalTrackingRecord(strategy, status)"
    )

    statements.forEach { sql ->
        try {
            driver.execute(null, sql.trimIndent(), 0)
        } catch (_: Exception) {
            // ถ้า table มีอยู่แล้ว ข้ามไป
        }
    }
}

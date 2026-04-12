package com.example.personalaibot.db

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
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
        // Migration: add unique index for KnowledgeNode.name (idempotent)
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_knowledge_node_name ON KnowledgeNode(name)"
    )

    statements.forEach { sql ->
        try {
            driver.execute(null, sql.trimIndent(), 0)
        } catch (_: Exception) {
            // ถ้า table มีอยู่แล้ว ข้ามไป
        }
    }
}

package com.example.personalaibot.tools.trading.auto

/**
 * ─── Trade Journal Store (Client-Side Stub) ─────────────────────────────────
 *
 * This file was truncated as part of the AutoTrading Server-Side Migration.
 * The data classes are preserved to satisfy the `AutoTradingViewModel`
 * and UI dependencies that receive the journal state from the Node.js server.
 */
object TradeJournalStore {

    // ── Stats for Learn phase ────────────────────────────────────────────────
    data class StrategyStat(
        val strategy: String,
        val total: Int,
        val wins: Int,
        val losses: Int,
        val winRate: Double,
        val totalProfit: Double,
        val avgR: Double
    )

    data class AnalyzerStat(
        val analyzers: String,       // CSV
        val total: Int,
        val wins: Int,
        val winRate: Double,
        val totalProfit: Double
    )

    // ── Row projection ──────────────────────────────────────────────────────
    data class JournalRow(
        val id: Long,
        val decisionId: String,
        val symbol: String,
        val timeframe: String,
        val side: String,
        val strategy: String,
        val analyzersUsed: String,
        val signalsJson: String,
        val confluenceScore: Double,
        val entry: Double?,
        val sl: Double?,
        val tp: Double?,
        val volume: Double?,
        val riskPct: Double?,
        val rrr: Double?,
        val regime: String?,
        val marketSnapshot: String?,
        val wasExecuted: Boolean,
        val mt5Ticket: Long?,
        val closeReason: String?,
        val closePrice: Double?,
        val closeAt: Long?,
        val profit: Double?,
        val profitR: Double?,
        val outcome: String,
        val aiReview: String?,
        val createdAt: Long,
        val updatedAt: Long
    )
}


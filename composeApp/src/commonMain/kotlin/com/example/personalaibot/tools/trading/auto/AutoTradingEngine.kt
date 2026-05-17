package com.example.personalaibot.tools.trading.auto

/**
 * ─── AutoTradingEngine (Remote Stub) ────────────────────────────────────────────────
 *
 * This file replaces the local execution logic of the AutoTrading engine.
 * The core execution loop has been migrated to `mt5-core-server` (Node.js).
 * Data classes are kept here to define the UI/ViewModel interactions with the server API.
 */
object AutoTradingEngine {
    data class RiskConfig(
        val riskPerTradePct: Double = 1.0,
        val maxTotalExposurePct: Double = 50.0,
        val maxOpenPositions: Int = 5,
        val maxDailyLossPct: Double = 5.0,
        val maxDrawdownPct: Double = 10.0,
        val minFreeMarginPct: Double = 10.0,
        val correlationCap: Int = 80,
        val minLotStep: Double = 0.01,
        val maxLot: Double = 100.0,
        val minLot: Double = 0.01,
        val pointValueOverride: Map<String, Double> = emptyMap()
    )

    data class StrategyConfig(
        val minRRR: Double = 1.5,
        val minConfluence: Double = 45.0,
        val scalpingRRR: Double = 1.5,
        val swingRRR: Double = 2.0,
        val gridLegs: Int = 3,
        val gridStepPct: Double = 0.2,
        val trailingAtrMult: Double = 1.5,
        val breakoutBufferPct: Double = 0.1
    )
    
    data class NotificationConfig(
        val onAnalysis: Boolean = true,
        val onOrder: Boolean = true,
        val onLoss: Boolean = true,
        val onClose: Boolean = true
    )

    data class Config(
        val watchlist: List<String> = listOf("XAUUSD", "EURUSD", "BTCUSD"),
        val timeframe: String = "H1",
        val tickIntervalMs: Long = 60_000L,
        val manageIntervalMs: Long = 30_000L,
        val learnIntervalMs: Long = 6 * 60 * 60_000L,
        val minConfluence: Double = 45.0,
        val minFitness: Double = 40.0,
        val minRRR: Double = 1.5,
        val enableLiveTrading: Boolean = false,
        // V23.0 — true = AI Supervisor mode (default), false = EA-only (no AI calls)
        val enableAiMode: Boolean = true,
        val preferredStrategies: Set<StrategyType> = emptySet(),
        val symbolBlacklist: Set<String> = emptySet(),
        val risk: RiskConfig = RiskConfig(),
        val strategy: StrategyConfig = StrategyConfig(),
        val breakEvenTriggerR: Double = 1.0,
        val trailAfterR: Double = 1.5,
        val autoTune: Boolean = true,
        val autoBlacklistAfterLosses: Int = 4,

        // AI Agent Fields
        val aiModel: String = "gemini-1.5-flash-lite",
        val apiKey: String = "",
        val agentPrompt: String = "",
        val preferFreeOnly: Boolean = false,
        val notificationSettings: NotificationConfig = NotificationConfig()
    )


    // ════════════════════════════════════════════════════════════════════════
    // Public state
    // ════════════════════════════════════════════════════════════════════════
    enum class Phase { IDLE, ANALYZING, STRATEGIZING, EXECUTING, MANAGING, LEARNING, STOPPED }

    data class EngineState(
        val running: Boolean = false,
        val phase: Phase = Phase.IDLE,
        val lastTickAt: String? = null,
        val cycleCount: Int = 0,
        val openTradesTracked: Int = 0,
        val message: String = "",
    )

    data class CycleDecision(
        val symbol: String,
        val regime: MarketRegime,
        val overallBias: Bias,
        val confluence: Double,
        val strategy: StrategyType,
        val side: String,
        val entry: Double?,
        val sl: Double?,
        val tp: Double?,
        val volume: Double?,
        val rrr: Double,
        val rationale: String,
        val analyzersUsed: List<AnalyzerKind>,
        val riskGate: String,
        val executed: Boolean,
        val mt5Ticket: Long?,
        val at: String,
        val decisionId: String,
        val translatedTh: String? = null,
    )

    data class LearnSummary(
        val totalDeals: Int,
        val wins: Int,
        val losses: Int,
        val winRate: Double,
        val totalProfit: Double,
        val bestStrategy: String?,
        val worstStrategy: String?,
        val bestAnalyzerCombo: String?,
        val worstAnalyzerCombo: String?,
        val strategyStats: List<TradeJournalStore.StrategyStat>,
        val analyzerStats: List<TradeJournalStore.AnalyzerStat>,
        val aiNote: String,
        val atIso: String,
    )
}

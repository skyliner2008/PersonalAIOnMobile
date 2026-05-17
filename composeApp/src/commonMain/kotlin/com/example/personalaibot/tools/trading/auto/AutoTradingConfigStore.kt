package com.example.personalaibot.tools.trading.auto

import com.example.personalaibot.db.JarvisDatabaseHolder

/**
 * ─── AutoTrading Config Persistence ─────────────────────────────────────────
 *
 * persist [AutoTradingEngine.Config] ลง AppSetting (key/value) ของ SQLDelight
 * แยกการ save แต่ละ field ด้วย key prefix `auto_trading.*` เพื่อไม่ชนกับ
 * setting อื่น ๆ (มี key prefix `mt5_*`, `ai_*` อยู่แล้ว)
 *
 * Format: string scalar สำหรับทุก primitive, CSV สำหรับ list/set
 */
object AutoTradingConfigStore {

    private const val P = "auto_trading."

    // ── Save ────────────────────────────────────────────────────────────────
    fun save(cfg: AutoTradingEngine.Config) {
        val db = JarvisDatabaseHolder.database ?: return
        val q = db.jarvisDatabaseQueries
        fun put(k: String, v: String) = q.insertSetting(P + k, v)

        put("watchlist", cfg.watchlist.joinToString(","))
        put("timeframe", cfg.timeframe)
        put("tick_ms", cfg.tickIntervalMs.toString())
        put("manage_ms", cfg.manageIntervalMs.toString())
        put("learn_ms", cfg.learnIntervalMs.toString())
        put("min_confluence", cfg.minConfluence.toString())
        put("min_fitness", cfg.minFitness.toString())
        put("min_rrr", cfg.minRRR.toString())
        put("enable_live", if (cfg.enableLiveTrading) "1" else "0")
        put("enable_ai_mode", if (cfg.enableAiMode) "1" else "0")
        put("preferred_strategies", cfg.preferredStrategies.joinToString(",") { it.name })
        put("blacklist", cfg.symbolBlacklist.joinToString(","))
        put("break_even_r", cfg.breakEvenTriggerR.toString())
        put("trail_after_r", cfg.trailAfterR.toString())
        put("auto_tune", if (cfg.autoTune) "1" else "0")
        put("auto_blacklist_after_losses", cfg.autoBlacklistAfterLosses.toString())

        // Risk
        val r = cfg.risk
        put("risk.per_trade", r.riskPerTradePct.toString())
        put("risk.max_total_exposure", r.maxTotalExposurePct.toString())
        put("risk.max_open", r.maxOpenPositions.toString())
        put("risk.max_daily_loss", r.maxDailyLossPct.toString())
        put("risk.max_dd", r.maxDrawdownPct.toString())
        put("risk.min_free_margin", r.minFreeMarginPct.toString())
        put("risk.correlation_cap", r.correlationCap.toString())
        put("risk.min_lot_step", r.minLotStep.toString())
        put("risk.max_lot", r.maxLot.toString())
        put("risk.min_lot", r.minLot.toString())

        // Strategy
        val s = cfg.strategy
        put("strategy.scalp_rrr", s.scalpingRRR.toString())
        put("strategy.swing_rrr", s.swingRRR.toString())
        put("strategy.grid_legs", s.gridLegs.toString())
        put("strategy.grid_step_pct", s.gridStepPct.toString())
        put("strategy.trail_atr_mult", s.trailingAtrMult.toString())
        put("strategy.breakout_buffer_pct", s.breakoutBufferPct.toString())
    }

    // ── Load ────────────────────────────────────────────────────────────────
    fun load(defaults: AutoTradingEngine.Config = AutoTradingEngine.Config()): AutoTradingEngine.Config {
        val db = JarvisDatabaseHolder.database ?: return defaults
        val q = db.jarvisDatabaseQueries

        fun get(k: String): String? = q.getSetting(P + k).executeAsOneOrNull()
        fun d(k: String, fallback: Double): Double = get(k)?.toDoubleOrNull() ?: fallback
        fun l(k: String, fallback: Long): Long = get(k)?.toLongOrNull() ?: fallback
        fun i(k: String, fallback: Int): Int = get(k)?.toIntOrNull() ?: fallback
        fun b(k: String, fallback: Boolean): Boolean = when (get(k)) {
            "1", "true", "True", "TRUE" -> true
            "0", "false", "False", "FALSE" -> false
            else -> fallback
        }

        val watchlist = get("watchlist")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: defaults.watchlist
        val blacklist = get("blacklist")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?: defaults.symbolBlacklist
        val preferred = get("preferred_strategies")?.split(",")?.mapNotNull { raw ->
            runCatching { StrategyType.valueOf(raw.trim()) }.getOrNull()
        }?.toSet() ?: defaults.preferredStrategies

        val risk = defaults.risk.copy(
            riskPerTradePct = d("risk.per_trade", defaults.risk.riskPerTradePct),
            maxTotalExposurePct = d("risk.max_total_exposure", defaults.risk.maxTotalExposurePct),
            maxOpenPositions = i("risk.max_open", defaults.risk.maxOpenPositions),
            maxDailyLossPct = d("risk.max_daily_loss", defaults.risk.maxDailyLossPct),
            maxDrawdownPct = d("risk.max_dd", defaults.risk.maxDrawdownPct),
            minFreeMarginPct = d("risk.min_free_margin", defaults.risk.minFreeMarginPct),
            correlationCap = i("risk.correlation_cap", defaults.risk.correlationCap),
            minLotStep = d("risk.min_lot_step", defaults.risk.minLotStep),
            maxLot = d("risk.max_lot", defaults.risk.maxLot),
            minLot = d("risk.min_lot", defaults.risk.minLot)
        )

        val strategy = defaults.strategy.copy(
            scalpingRRR = d("strategy.scalp_rrr", defaults.strategy.scalpingRRR),
            swingRRR = d("strategy.swing_rrr", defaults.strategy.swingRRR),
            gridLegs = i("strategy.grid_legs", defaults.strategy.gridLegs),
            gridStepPct = d("strategy.grid_step_pct", defaults.strategy.gridStepPct),
            trailingAtrMult = d("strategy.trail_atr_mult", defaults.strategy.trailingAtrMult),
            breakoutBufferPct = d("strategy.breakout_buffer_pct", defaults.strategy.breakoutBufferPct)
        )

        return defaults.copy(
            watchlist = watchlist,
            timeframe = get("timeframe") ?: defaults.timeframe,
            tickIntervalMs = l("tick_ms", defaults.tickIntervalMs),
            manageIntervalMs = l("manage_ms", defaults.manageIntervalMs),
            learnIntervalMs = l("learn_ms", defaults.learnIntervalMs),
            minConfluence = d("min_confluence", defaults.minConfluence),
            minFitness = d("min_fitness", defaults.minFitness),
            minRRR = d("min_rrr", defaults.minRRR),
            enableLiveTrading = b("enable_live", defaults.enableLiveTrading),
            enableAiMode = b("enable_ai_mode", defaults.enableAiMode),
            preferredStrategies = preferred,
            symbolBlacklist = blacklist,
            breakEvenTriggerR = d("break_even_r", defaults.breakEvenTriggerR),
            trailAfterR = d("trail_after_r", defaults.trailAfterR),
            autoTune = b("auto_tune", defaults.autoTune),
            autoBlacklistAfterLosses = i("auto_blacklist_after_losses", defaults.autoBlacklistAfterLosses),
            risk = risk,
            strategy = strategy
        )
    }
}

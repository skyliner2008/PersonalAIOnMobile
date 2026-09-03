package com.example.personalaibot.tools.trading

import com.example.personalaibot.automation.backtest.RiskEngine

/**
 * P0.3 execution guard.
 * Validates dangerous MT5 mutations before network execution.
 * Kept independent from TradingToolExecutor so the safety boundary can be tested/reused.
 */
internal object Mt5RiskGate {
    data class Policy(
        val maxVolume: Double = 10.0,
        val maxRiskPct: Double = 2.0,
        val maxDrawdownPct: Double = 5.0,
        val maxDailyLossPct: Double = 5.0,
        val maxTotalExposurePct: Double = 50.0,
        val maxOpenPositions: Int = 5,
        val minFreeMarginPct: Double = 10.0,
        val correlationCap: Int = 80,
        val requireCloseAllConfirmation: Boolean = true
    )

    fun validateOrder(args: Map<String, String>, policy: Policy = policyFrom(args)): String? {
        val volume = args["volume"]?.toDoubleOrNull() ?: return "Invalid volume"
        if (volume <= 0.0) return "Invalid volume: must be > 0"
        if (volume > policy.maxVolume) return "Risk gate blocked order: volume $volume > max ${policy.maxVolume}"
        val riskPct = args["risk_pct"]?.toDoubleOrNull()
        if (riskPct != null && (riskPct < 0.0 || riskPct > policy.maxRiskPct)) {
            return "Risk gate blocked order: risk_pct $riskPct% > max ${policy.maxRiskPct}%"
        }
        val currentDd = args["current_dd_pct"]?.toDoubleOrNull()
        if (currentDd != null && currentDd >= policy.maxDrawdownPct) {
            return "Risk gate blocked order: current drawdown $currentDd% >= max ${policy.maxDrawdownPct}%"
        }
        val action = args["action"]?.uppercase().orEmpty()
        val price = args["price"]?.toDoubleOrNull()
        val sl = args["sl"]?.toDoubleOrNull()
        val tp = args["tp"]?.toDoubleOrNull()
        // P6: strategy-generated orders must pass the deterministic portfolio risk engine.
        // Manual orders without strategy_gate retain the existing P0.3 validation path.
        if (args["strategy_gate"] != null) {
            validateP6StrategyOrder(args, policy)?.let { return it }
        }

        if (price != null && price > 0.0) {
            if (sl != null && ((action == "BUY" && sl >= price) || (action == "SELL" && sl <= price))) {
                return "Risk gate blocked order: SL is on the wrong side of price"
            }
            if (tp != null && ((action == "BUY" && tp <= price) || (action == "SELL" && tp >= price))) {
                return "Risk gate blocked order: TP is on the wrong side of price"
            }
        }
        return null
    }

    private fun validateP6StrategyOrder(args: Map<String, String>, policy: Policy): String? {
        if (args["strategy_gate"]?.uppercase() != "PASS") {
            return "Risk gate blocked strategy order: strategy gate must be PASS"
        }
        if (args["live_execution_enabled"]?.toBooleanStrictOrNull() != true) {
            return "Risk gate blocked strategy order: live execution is disabled"
        }

        fun required(key: String): Double? = args[key]?.toDoubleOrNull()
        val account = RiskEngine.Account(
            equity = required("equity") ?: return "Risk gate blocked strategy order: missing equity",
            balance = required("balance") ?: return "Risk gate blocked strategy order: missing balance",
            freeMargin = required("free_margin") ?: return "Risk gate blocked strategy order: missing free_margin",
            todayPnL = required("today_pnl") ?: return "Risk gate blocked strategy order: missing today_pnl",
            openPositions = args["open_positions"]?.toIntOrNull()
                ?: return "Risk gate blocked strategy order: missing open_positions",
            currentExposurePct = required("current_exposure_pct")
                ?: return "Risk gate blocked strategy order: missing current_exposure_pct",
            correlatedExposurePct = required("correlated_exposure_pct")
                ?: return "Risk gate blocked strategy order: missing correlated_exposure_pct"
        )
        val proposal = RiskEngine.Proposal(
            strategyGate = com.example.personalaibot.automation.backtest.RiskGate.Decision.PASS,
            entry = required("price") ?: return "Risk gate blocked strategy order: missing price",
            stopLoss = required("sl") ?: return "Risk gate blocked strategy order: missing sl",
            valuePerPriceUnit = required("value_per_price_unit")
                ?: return "Risk gate blocked strategy order: missing value_per_price_unit",
            requestedRiskPct = required("risk_pct"),
            requestedVolume = volumeOrNull(args["volume"]),
            maxCorrelationPct = required("max_correlation_pct") ?: 0.0
        )
        val result = RiskEngine.evaluate(
            account = account,
            proposal = proposal,
            policy = RiskEngine.Policy(
                riskPerTradePct = policy.maxRiskPct,
                maxTotalExposurePct = policy.maxTotalExposurePct,
                maxOpenPositions = policy.maxOpenPositions,
                maxDailyLossPct = policy.maxDailyLossPct,
                maxDrawdownPct = policy.maxDrawdownPct,
                minFreeMarginPct = policy.minFreeMarginPct,
                correlationCap = policy.correlationCap,
                minLotStep = args["min_lot_step"]?.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.01,
                minLot = args["min_lot"]?.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.01,
                maxLot = policy.maxVolume
            )
        )
        return if (result.decision == RiskEngine.Decision.PASS) null
        else "Risk gate blocked strategy order: ${result.reasons.joinToString(",")}" 
    }

    private fun volumeOrNull(raw: String?): Double? = raw?.toDoubleOrNull()

    fun validateCloseAll(args: Map<String, String>, policy: Policy = policyFrom(args)): String? =
        if (policy.requireCloseAllConfirmation && args["confirm"]?.trim()?.lowercase() != "true") {
            "Risk gate blocked close-all: explicit confirm=true is required"
        } else null

    fun policyFrom(args: Map<String, String>): Policy = Policy(
        maxVolume = args["max_volume"]?.toDoubleOrNull()?.takeIf { it > 0 } ?: 10.0,
        maxRiskPct = args["max_risk_pct"]?.toDoubleOrNull()?.takeIf { it > 0 } ?: 2.0,
        maxDrawdownPct = args["max_dd_pct"]?.toDoubleOrNull()?.takeIf { it > 0 } ?: 5.0,
        maxDailyLossPct = args["max_daily_loss_pct"]?.toDoubleOrNull()?.takeIf { it > 0 } ?: 5.0,
        maxTotalExposurePct = args["max_total_exposure_pct"]?.toDoubleOrNull()?.takeIf { it > 0 } ?: 50.0,
        maxOpenPositions = args["max_open_positions"]?.toIntOrNull()?.takeIf { it > 0 } ?: 5,
        minFreeMarginPct = args["min_free_margin_pct"]?.toDoubleOrNull()?.takeIf { it > 0 } ?: 10.0,
        correlationCap = args["correlation_cap"]?.toIntOrNull()?.coerceIn(0, 100) ?: 80,
        requireCloseAllConfirmation = args["require_close_all_confirmation"]?.toBooleanStrictOrNull() ?: true
    )
}

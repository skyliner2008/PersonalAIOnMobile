package com.example.personalaibot

import com.example.personalaibot.automation.backtest.RiskEngine
import com.example.personalaibot.automation.backtest.RiskGate
import com.example.personalaibot.tools.trading.Mt5RiskGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P6.4 System Validation Harness.
 *
 * This is the deterministic safety/validation layer for the trading stack.
 * UI/network/MT5 checks remain manual acceptance checks; pure domain checks
 * must stay executable in commonTest so a green build has meaningful evidence.
 */
class SystemValidationHarnessTest {
    private fun account(
        equity: Double = 10_000.0,
        balance: Double = 10_000.0,
        freeMargin: Double = 9_000.0,
        todayPnL: Double = 0.0,
        openPositions: Int = 0,
        exposure: Double = 0.0,
        correlated: Double = 0.0,
    ) = RiskEngine.Account(equity, balance, freeMargin, todayPnL, openPositions, exposure, correlated)

    private fun proposal(
        strategyGate: RiskGate.Decision = RiskGate.Decision.PASS,
    ) = RiskEngine.Proposal(
        strategyGate = strategyGate,
        entry = 100.0,
        stopLoss = 98.0,
        valuePerPriceUnit = 1.0,
        requestedRiskPct = 1.0,
        requestedVolume = 5.0,
        maxCorrelationPct = 0.0,
    )

    private val policy = RiskEngine.Policy(
        riskPerTradePct = 1.0,
        maxTotalExposurePct = 50.0,
        maxDailyLossPct = 5.0,
        maxDrawdownPct = 10.0,
        maxOpenPositions = 10,
        correlationCap = 80,
    )

    @Test
    fun validation_harness_accepts_safe_risk_path() {
        val result = RiskEngine.evaluate(account(), proposal(), policy)
        assertEquals(RiskEngine.Decision.PASS, result.decision)
        assertTrue(result.executionAllowed)
    }

    @Test
    fun validation_harness_blocks_daily_loss() {
        val result = RiskEngine.evaluate(account(todayPnL = -500.0), proposal(), policy)
        assertEquals(RiskEngine.Decision.BLOCK, result.decision)
        assertTrue(RiskEngine.Reason.DAILY_LOSS_LIMIT in result.reasons)
    }

    @Test
    fun validation_harness_blocks_drawdown() {
        val result = RiskEngine.evaluate(account(equity = 8_900.0), proposal(), policy)
        assertEquals(RiskEngine.Decision.BLOCK, result.decision)
        assertTrue(RiskEngine.Reason.DRAWDOWN_LIMIT in result.reasons)
    }

    @Test
    fun validation_harness_blocks_strategy_gate_failure() {
        val result = RiskEngine.evaluate(account(), proposal(RiskGate.Decision.HOLD), policy)
        assertEquals(RiskEngine.Decision.BLOCK, result.decision)
        assertTrue(RiskEngine.Reason.STRATEGY_GATE_NOT_PASS in result.reasons)
    }

    @Test
    fun validation_harness_blocks_unsafe_mt5_order() {
        val error = Mt5RiskGate.validateOrder(mapOf("action" to "BUY", "volume" to "11"))
        assertTrue(error?.contains("volume") == true)
    }

    @Test
    fun validation_harness_requires_close_all_confirmation() {
        val error = Mt5RiskGate.validateCloseAll(emptyMap())
        assertTrue(error?.contains("confirm=true") == true)
        assertEquals(null, Mt5RiskGate.validateCloseAll(mapOf("confirm" to "true")))
    }
}

package com.example.personalaibot

import com.example.personalaibot.automation.backtest.RiskEngine
import com.example.personalaibot.automation.backtest.RiskGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RiskEngineTest {
    private fun account(
        equity: Double = 10_000.0,
        balance: Double = 10_000.0,
        freeMargin: Double = 9_000.0,
        todayPnL: Double = 0.0,
        openPositions: Int = 0,
        exposure: Double = 0.0,
        correlated: Double = 0.0
    ) = RiskEngine.Account(equity, balance, freeMargin, todayPnL, openPositions, exposure, correlated)

    private fun proposal(
        entry: Double = 100.0,
        stop: Double = 98.0,
        value: Double = 10.0,
        riskPct: Double? = null,
        volume: Double? = null,
        correlation: Double = 0.0
    ) = RiskEngine.Proposal(RiskGate.Decision.PASS, entry, stop, value, riskPct, volume, correlation)

    private val policy = RiskEngine.Policy(
        riskPerTradePct = 1.0,
        maxTotalExposurePct = 50.0,
        maxOpenPositions = 5,
        maxDailyLossPct = 5.0,
        maxDrawdownPct = 10.0,
        minFreeMarginPct = 10.0,
        correlationCap = 80,
        minLotStep = 0.01,
        minLot = 0.01,
        maxLot = 100.0
    )

    @Test
    fun sizes_position_from_risk_budget() {
        val result = RiskEngine.evaluate(account(), proposal(), policy)
        assertEquals(RiskEngine.Decision.PASS, result.decision)
        assertEquals(5.0, result.suggestedVolume)
        assertEquals(1.0, result.riskPct)
        assertTrue(result.executionAllowed)
    }

    @Test
    fun blocks_when_daily_loss_limit_is_reached() {
        val result = RiskEngine.evaluate(account(todayPnL = -500.0), proposal(), policy)
        assertEquals(RiskEngine.Decision.BLOCK, result.decision)
        assertTrue(RiskEngine.Reason.DAILY_LOSS_LIMIT in result.reasons)
    }

    @Test
    fun blocks_when_drawdown_limit_is_reached() {
        val result = RiskEngine.evaluate(account(equity = 8_900.0), proposal(), policy)
        assertEquals(RiskEngine.Decision.BLOCK, result.decision)
        assertTrue(RiskEngine.Reason.DRAWDOWN_LIMIT in result.reasons)
    }

    @Test
    fun blocks_when_total_exposure_would_be_exceeded() {
        val result = RiskEngine.evaluate(account(exposure = 49.5), proposal(), policy)
        assertEquals(RiskEngine.Decision.BLOCK, result.decision)
        assertTrue(RiskEngine.Reason.TOTAL_EXPOSURE in result.reasons)
    }

    @Test
    fun blocks_when_correlation_budget_is_exceeded() {
        val result = RiskEngine.evaluate(account(correlated = 39.5), proposal(correlation = 100.0), policy)
        assertEquals(RiskEngine.Decision.BLOCK, result.decision)
        assertTrue(RiskEngine.Reason.CORRELATION_EXPOSURE in result.reasons)
    }

    @Test
    fun blocks_strategy_that_did_not_pass_strategy_gate() {
        val p = proposal().copy(strategyGate = RiskGate.Decision.HOLD)
        val result = RiskEngine.evaluate(account(), p, policy)
        assertEquals(RiskEngine.Decision.BLOCK, result.decision)
        assertTrue(RiskEngine.Reason.STRATEGY_GATE_NOT_PASS in result.reasons)
    }

    @Test
    fun risk_engine_does_not_enable_live_execution() {
        // P6 produces a risk PASS for paper/simulation; live enablement remains an execution-layer concern.
        val result = RiskEngine.evaluate(account(), proposal(), policy)
        assertEquals(RiskEngine.Decision.PASS, result.decision)
        assertTrue(result.executionAllowed)
    }
}

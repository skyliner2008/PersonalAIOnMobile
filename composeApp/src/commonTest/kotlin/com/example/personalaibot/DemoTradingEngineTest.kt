package com.example.personalaibot

import com.example.personalaibot.automation.backtest.DemoAccountConfig
import com.example.personalaibot.automation.backtest.DemoTradingEngine
import com.example.personalaibot.automation.backtest.TradingAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoTradingEngineTest {
    @Test
    fun creates_independent_demo_account_without_mt5() {
        val engine = DemoTradingEngine(initialBalance = 10_000.0)
        assertEquals(TradingAccount.Environment.DEMO, engineAccount(engine).environment)
        assertEquals(10_000.0, engineAccount(engine).balance)
    }

    @Test
    fun executes_and_marks_to_market_position_locally() {
        val engine = DemoTradingEngine(initialBalance = 10_000.0)
        engine.openMarketSync("EURUSD", true, 0.01, 1.1000, 1.0980, 1.1050)
        engine.updatePrice("EURUSD", 1.1020)
        assertEquals(1, engineAccount(engine).openPositions)
        assertTrue(engineAccount(engine).equity > engineAccount(engine).balance)
    }

    @Test
    fun stop_loss_closes_position_without_server() {
        val engine = DemoTradingEngine(initialBalance = 10_000.0)
        engine.openMarketSync("EURUSD", true, 0.01, 1.1000, 1.0980, null)
        engine.updatePrice("EURUSD", 1.0970)
        assertEquals(0, engineAccount(engine).openPositions)
    }


    @Test
    fun demo_risk_sizing_includes_round_turn_cost_budget() {
        val engine = DemoTradingEngine(
            config = DemoAccountConfig(
                id = "risk-demo",
                name = "Risk Demo",
                costProfile = com.example.personalaibot.automation.backtest.DemoCostProfile(
                    spreadPrice = 0.20,
                    commissionPerLotRoundTurn = 7.0,
                    rebatePerLotRoundTurn = 1.0,
                    contractSize = 100.0,
                    tickSize = 0.01,
                    tickValuePerLot = 0.01,
                ),
            ),
            initialBalance = 10_000.0,
        )
        val result = engine.evaluateRisk(
            symbol = "XAUUSD",
            buy = true,
            entry = 2000.0,
            stopLoss = 1990.0,
            requestedRiskPct = 1.0,
        )
        assertTrue(result.executionAllowed)
        assertTrue((result.suggestedVolume ?: 0.0) > 0.0)
        assertTrue((result.riskPct ?: 0.0) <= 1.0 + 1e-9)
    }

    @Test
    fun execution_path_enforces_risk_and_stop_loss() {
        val engine = DemoTradingEngine(initialBalance = 10_000.0)
        val result = runCatching {
            kotlinx.coroutines.runBlocking { engine.executeOpen("XAUUSD", true, 0.01, 2000.0, null, null) }
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun floating_pnl_uses_symbol_tick_value() {
        val engine = DemoTradingEngine(initialBalance = 10_000.0)
        val id = engine.openMarketSync("XAUUSD", true, 0.01, 2000.0, 1990.0, null)
        engine.updateQuote("XAUUSD", 2001.0, 2001.2)
        assertTrue(engine.accountSnapshot().equity > engine.accountSnapshot().balance)
        assertTrue(id.isNotBlank())
    }

    @Test
    fun kill_switch_blocks_new_orders_after_daily_loss_threshold() {
        val engine = DemoTradingEngine(
            config = DemoAccountConfig(
                id = "kill-demo", name = "Kill Demo", killSwitchDailyLossPct = 0.0
            ),
            initialBalance = 10_000.0
        )
        engine.accountSnapshot()
        assertTrue(engine.isKillSwitchActive())
        val blocked = runCatching { engine.openMarketSync("XAUUSD", true, 0.01, 2000.0, 1990.0, null) }
        assertTrue(blocked.isFailure)
    }

    private fun engineAccount(engine: DemoTradingEngine): TradingAccount = engine.accountSnapshot()
}

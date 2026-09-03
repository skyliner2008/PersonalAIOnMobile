package com.example.personalaibot

import com.example.personalaibot.automation.backtest.DemoAccountConfig
import com.example.personalaibot.automation.backtest.DemoCostProfile
import com.example.personalaibot.automation.backtest.DemoTradingCostCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoAccountCostTest {
    private val config = DemoAccountConfig(
        id = "test",
        name = "Test",
        costProfile = DemoCostProfile(
            spreadPrice = 0.20,
            commissionPerLotRoundTurn = 7.0,
            rebatePerLotRoundTurn = 1.0,
            contractSize = 100.0,
        ),
    )

    @Test
    fun round_turn_cost_includes_spread_commission_and_rebate() {
        val cost = DemoTradingCostCalculator.estimateRoundTurn(config, 1.0, 100.0, 101.0, true)
        assertEquals(20.0, cost.spreadCost, 1e-9)
        assertEquals(7.0, cost.commission, 1e-9)
        assertEquals(1.0, cost.rebate, 1e-9)
        assertEquals(26.0, cost.totalNetCost, 1e-9)
    }

    @Test
    fun costs_never_become_negative_from_rebate() {
        val cost = DemoTradingCostCalculator.estimateRoundTurn(
            config.copy(costProfile = config.costProfile.copy(rebatePerLotRoundTurn = 100.0)),
            1.0, 100.0, 101.0, true,
        )
        assertTrue(cost.totalNetCost < 0.0)
    }
}

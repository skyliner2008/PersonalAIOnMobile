package com.example.personalaibot

import com.example.personalaibot.automation.backtest.DemoMarketPriceModel
import kotlin.test.Test
import kotlin.test.assertEquals

class DemoMarketPriceModelTest {
    @Test
    fun bid_ask_execution_uses_correct_side() {
        val model = DemoMarketPriceModel()
        model.update("EURUSD", 1.1000, 1.1002, 1L)
        assertEquals(1.1002, model.executionPrice("EURUSD", true), 1e-9)
        assertEquals(1.1000, model.executionPrice("EURUSD", false), 1e-9)
        assertEquals(0.0002, model.quote("EURUSD")!!.spread, 1e-9)
    }
}

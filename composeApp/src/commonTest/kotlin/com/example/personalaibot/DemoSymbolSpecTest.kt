package com.example.personalaibot

import com.example.personalaibot.automation.backtest.DemoSymbolRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoSymbolSpecTest {
    @Test
    fun registry_contains_core_symbols() {
        assertTrue(DemoSymbolRegistry.get("EURUSD")!!.digits == 5)
        assertEquals(2, DemoSymbolRegistry.get("XAUUSD")!!.digits)
        assertEquals(3, DemoSymbolRegistry.get("USDJPY")!!.digits)
    }

    @Test
    fun volume_rules_are_symbol_specific() {
        val fx = DemoSymbolRegistry.get("EURUSD")!!
        assertTrue(fx.isValidVolume(0.01))
        assertTrue(fx.isValidVolume(0.11))
        assertFalse(fx.isValidVolume(0.105))
        assertEquals(0.11, fx.normalizeVolume(0.119), 1e-9)
    }
}

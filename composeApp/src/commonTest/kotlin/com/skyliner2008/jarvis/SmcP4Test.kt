package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.smc.SmcApsConfig
import com.skyliner2008.jarvis.automation.smc.SmcBacktest
import com.skyliner2008.jarvis.automation.smc.SmcBacktestConfig
import com.skyliner2008.jarvis.automation.smc.SmcEvolve
import com.skyliner2008.jarvis.tools.trading.Candle
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SmcP4Test {
    private fun candles(n: Int = 150): List<Candle> = (0 until n).map { i ->
        val p = 100.0 + i * 0.05 + kotlin.math.sin(i * 0.2) * 0.4
        Candle(timestamp = i.toLong(), open = p, high = p + 0.3, low = p - 0.3, close = p + if (i % 3 == 0) 0.12 else -0.04, volume = 1000.0 + (i % 10) * 10)
    }

    @Test fun p41NoTradeRequiresConfirmation() {
        val s = com.skyliner2008.jarvis.automation.smc.SmcApsEngine.evaluate(candles(), "XAUUSD", "M5", SmcApsConfig())
        assertTrue(s.decision.name in setOf("NO_TRADE", "BUY", "SELL"))
        if (s.decision.name != "NO_TRADE") assertTrue(s.triggers.isNotEmpty())
    }

    @Test fun p42BacktestIsDeterministic() {
        val c = candles()
        val a = SmcBacktest.run(c, "XAUUSD", "M5", SmcBacktestConfig())
        val b = SmcBacktest.run(c, "XAUUSD", "M5", SmcBacktestConfig())
        assertTrue(a == b)
    }

    @Test fun p43EvolutionProducesValidation() {
        val result = SmcEvolve.evolve(candles(180), "XAUUSD", "M5")
        assertNotNull(result)
        assertTrue(result.tested > 1)
    }
}

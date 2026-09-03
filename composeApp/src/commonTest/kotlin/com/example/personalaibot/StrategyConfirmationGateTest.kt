package com.example.personalaibot

import com.example.personalaibot.automation.StrategyConfirmationGate
import com.example.personalaibot.tools.trading.Candle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrategyConfirmationGateTest {
    private fun bars(last: Candle): List<Candle> = buildList {
        repeat(25) { i ->
            val p = 100.0 + i * 0.02
            add(Candle(p, p + 0.5, p - 0.5, p + 0.05, 100.0, i.toLong()))
        }
        add(last.copy(timestamp = 25L))
    }

    @Test
    fun weakMomentumCandleIsRejected() {
        val c = Candle(100.0, 100.6, 99.4, 100.03, 100.0)
        assertFalse(StrategyConfirmationGate.evaluate(bars(c), 25, "BUY", "TR").accepted)
    }

    @Test
    fun strongMomentumCandleIsAccepted() {
        val c = Candle(100.0, 101.0, 99.9, 100.85, 100.0)
        assertTrue(StrategyConfirmationGate.evaluate(bars(c), 25, "BUY", "TR").accepted)
    }

    @Test
    fun reversalNeedsRejectionWick() {
        val weak = Candle(100.0, 100.2, 99.95, 100.05, 100.0)
        val strong = Candle(100.4, 100.8, 99.0, 100.7, 100.0)
        assertFalse(StrategyConfirmationGate.evaluate(bars(weak), 25, "BUY", "REV").accepted)
        assertTrue(StrategyConfirmationGate.evaluate(bars(strong), 25, "BUY", "REV").accepted)
    }

    @Test
    fun threeBarPatternIsNotDoubleGated() {
        val c = Candle(100.0, 100.1, 99.9, 100.01, 100.0)
        assertTrue(StrategyConfirmationGate.evaluate(bars(c), 25, "BUY", "3BR").accepted)
    }
}

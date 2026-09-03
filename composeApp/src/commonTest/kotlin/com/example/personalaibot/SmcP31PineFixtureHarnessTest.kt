package com.example.personalaibot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmcP31PineFixtureHarnessTest {
    @Test
    fun fixture_contract_parses_and_preserves_bar_order() {
        val fixture = PineLiquidityFixtureHarness.parse(SAMPLE_FIXTURE)

        assertEquals("p3.1-contract", fixture.name)
        assertEquals("XAUUSD", fixture.symbol)
        assertEquals("M15", fixture.timeframe)
        assertEquals(2, fixture.candles.size)
        assertEquals(0, fixture.referenceSnapshots.first().barIndex)
        assertEquals(1, fixture.referenceSnapshots.last().barIndex)
        assertTrue(fixture.candles.first().timestamp < fixture.candles.last().timestamp)
    }

    @Test
    fun fixture_comparator_detects_a_reference_difference() {
        val fixture = PineLiquidityFixtureHarness.parse(SAMPLE_FIXTURE_WITH_EXPECTED_LEVEL)
        val mismatches = PineLiquidityFixtureHarness.compare(fixture)

        assertEquals(1, mismatches.size)
        assertEquals(1, mismatches.single().barIndex)
    }

    companion object {
        private val SAMPLE_FIXTURE = """
            {
              "name": "p3.1-contract",
              "symbol": "XAUUSD",
              "timeframe": "M15",
              "candles": [
                {"timestamp": 1, "open": 100.0, "high": 101.0, "low": 99.0, "close": 100.5},
                {"timestamp": 2, "open": 100.5, "high": 102.0, "low": 100.0, "close": 101.0}
              ],
              "referenceSnapshots": [
                {"barIndex": 0, "zones": []},
                {"barIndex": 1, "zones": []}
              ]
            }
        """.trimIndent()

        // Deliberately contains one impossible reference level. This proves the
        // harness reports a bar-level mismatch instead of silently passing.
        private val SAMPLE_FIXTURE_WITH_EXPECTED_LEVEL = """
            {
              "name": "p3.1-negative",
              "symbol": "XAUUSD",
              "timeframe": "M15",
              "candles": [
                {"timestamp": 1, "open": 100.0, "high": 101.0, "low": 99.0, "close": 100.5},
                {"timestamp": 2, "open": 100.5, "high": 102.0, "low": 100.0, "close": 101.0}
              ],
              "referenceSnapshots": [
                {"barIndex": 0, "zones": []},
                {"barIndex": 1, "zones": [{"price": 101.0, "isHigh": true, "strength": 1}]}
              ]
            }
        """.trimIndent()
    }
}

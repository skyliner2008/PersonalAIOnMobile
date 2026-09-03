package com.example.personalaibot

import com.example.personalaibot.controller.ChartController
import com.example.personalaibot.tools.trading.TaIndicators
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChartDynamicIndicatorTest {

    @Test
    fun testIsValidOverlayAllowsDynamicEmaPeriods() {
        val testPeriods = listOf("ema8", "ema9", "ema12", "ema14", "ema20", "ema21", "ema26", "ema34", "ema50", "ema60", "ema89", "ema100", "ema200")
        for (ema in testPeriods) {
            assertTrue(ChartController.isValidOverlay(ema), "Expected $ema to be valid overlay")
            assertTrue(ChartController.isValidOverlay(ema.uppercase()), "Expected ${ema.uppercase()} to be valid overlay")
        }
    }

    @Test
    fun testIsValidOverlayAllowsDynamicSmaPeriods() {
        val testPeriods = listOf("sma20", "sma50", "sma100", "sma200", "ma50", "wma20", "hma9")
        for (sma in testPeriods) {
            assertTrue(ChartController.isValidOverlay(sma), "Expected $sma to be valid overlay")
        }
    }

    @Test
    fun testIsValidOverlayAllowsStandardAndDynamicTechnicalOverlays() {
        val standard = listOf("bb", "bb_20_2", "bb20", "donchian", "dc20", "dc55", "smc", "signals", "supertrend", "st", "vwap")
        for (overlay in standard) {
            assertTrue(ChartController.isValidOverlay(overlay), "Expected $overlay to be valid overlay")
        }
    }

    @Test
    fun testIsValidOverlayRejectsInvalidNames() {
        val invalid = listOf("foo", "bar", "unknown_indicator", "random123", "", "   ")
        for (name in invalid) {
            assertFalse(ChartController.isValidOverlay(name), "Expected '$name' to be rejected")
        }
    }

    @Test
    fun testChartIntervalTimeframeNormalization() {
        val testCases = mapOf(
            "m1" to "1m",
            "M5" to "5m",
            "m15" to "15m",
            "H1" to "1h",
            "h4" to "4h",
            "D1" to "1d",
            "w1" to "1w"
        )
        for ((input, expected) in testCases) {
            val norm = TaIndicators.normalizeTimeframe(input).lowercase()
            assertEquals(expected, norm, "Normalization for $input")
        }
    }
}

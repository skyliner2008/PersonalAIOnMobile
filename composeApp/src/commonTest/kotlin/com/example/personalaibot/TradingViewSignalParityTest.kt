package com.example.personalaibot

import com.example.personalaibot.automation.TradingViewSignalIntelligence
import kotlin.test.Test
import kotlin.test.assertEquals

class TradingViewSignalParityTest {
    @Test
    fun twoAlignedTimeframesCanPassDecisionGate() {
        val frames = listOf(
            TradingViewSignalIntelligence.TimeframeSignal("15m", 2.0, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.WAIT, TradingViewSignalIntelligence.Decision.BUY, 100, 300),
            TradingViewSignalIntelligence.TimeframeSignal("1h", 2.0, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.WAIT, TradingViewSignalIntelligence.Decision.BUY, 100, 300),
            TradingViewSignalIntelligence.TimeframeSignal("4h", 2.0, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.WAIT, TradingViewSignalIntelligence.Decision.BUY, 100, 300)
        )
        val result = TradingViewSignalIntelligence.fuse(frames, smcScore = 0.8)
        assertEquals(TradingViewSignalIntelligence.Decision.BUY, result.decision)
        assertEquals("PASS", result.gate)
    }

    @Test
    fun lowQualityDataAlwaysForcesWait() {
        val frame = TradingViewSignalIntelligence.TimeframeSignal("1h", 4.0, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, TradingViewSignalIntelligence.Decision.BUY, 50, 80)
        val result = TradingViewSignalIntelligence.fuse(listOf(frame, frame), smcScore = 1.0)
        assertEquals(TradingViewSignalIntelligence.Decision.WAIT, result.decision)
        assertEquals("DATA_QUALITY", result.gate)
    }
}

package com.example.personalaibot.tools.trading

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global State Manager for the Charting system
 * Allows ToolExecutors to update data that the UI observes
 */
object ChartStateManager {
    private val _currentSymbol = MutableStateFlow("XAUUSD")
    val currentSymbol: StateFlow<String> = _currentSymbol.asStateFlow()

    private val _currentCandles = MutableStateFlow<List<Candle>>(emptyList())
    val currentCandles: StateFlow<List<Candle>> = _currentCandles.asStateFlow()

    private val _currentSmcResult = MutableStateFlow<SmcAnalysisResult?>(null)
    val currentSmcResult: StateFlow<SmcAnalysisResult?> = _currentSmcResult.asStateFlow()

    /**
     * Update the global chart state with new analysis data
     */
    fun updateData(symbol: String, candles: List<Candle>, smc: SmcAnalysisResult? = null) {
        _currentSymbol.value = symbol
        _currentCandles.value = candles
        _currentSmcResult.value = smc
    }
}

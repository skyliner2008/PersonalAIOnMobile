package com.example.personalaibot.tools.trading

data class AiTrackingInsight(
    val symbol: String,
    val lastPrice: Double,
    val positionCount: Int,
    val orderCount: Int,
    val slCoveragePct: Double,
    val tpCoveragePct: Double,
    val candles1m: Int,
    val candles5m: Int,
    val candles15m: Int,
    val canFetchHistory: Boolean,
    val indicatorsReady: Boolean,
    val ema20: Double?,
    val ema50: Double?,
    val rsi14: Double?,
    val atr14: Double?,
    val bias: String,
    val candleSourceSummary: String,
    val updatedAt: Long
)

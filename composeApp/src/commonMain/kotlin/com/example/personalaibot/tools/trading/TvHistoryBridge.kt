package com.example.personalaibot.tools.trading

expect suspend fun fetchTvHistoryBars(
    symbol: String,
    resolution: String,
    bars: Int,
    timeoutSec: Int = 12
): List<Candle>

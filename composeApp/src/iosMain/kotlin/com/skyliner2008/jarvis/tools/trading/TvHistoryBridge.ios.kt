package com.skyliner2008.jarvis.tools.trading

actual suspend fun fetchTvHistoryBars(
    symbol: String,
    resolution: String,
    bars: Int,
    timeoutSec: Int
): List<Candle> = emptyList()

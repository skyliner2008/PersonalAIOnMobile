package com.skyliner2008.jarvis.tools.trading

expect suspend fun fetchTvHistoryBars(
    symbol: String,
    resolution: String,
    bars: Int,
    timeoutSec: Int = 12
): List<Candle>

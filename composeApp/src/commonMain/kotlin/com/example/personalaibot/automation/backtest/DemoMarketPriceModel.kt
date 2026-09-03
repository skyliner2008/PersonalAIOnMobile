package com.example.personalaibot.automation.backtest

/**
 * Broker-like quote model for Demo. Bid/ask are first-class values; strategies may
 * submit against the executable side instead of a synthetic mid price.
 */
data class DemoQuote(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val timestamp: Long,
) {
    init {
        require(symbol.isNotBlank())
        require(bid > 0.0)
        require(ask >= bid)
    }

    val mid: Double get() = (bid + ask) / 2.0
    val spread: Double get() = ask - bid
}

class DemoMarketPriceModel(
    private val defaultSpread: Double = 0.20,
) {
    private val quotes = mutableMapOf<String, DemoQuote>()

    fun update(symbol: String, bid: Double, ask: Double, timestamp: Long): DemoQuote {
        val quote = DemoQuote(symbol.uppercase(), bid, ask, timestamp)
        quotes[quote.symbol] = quote
        return quote
    }

    fun updateMid(symbol: String, mid: Double, timestamp: Long, spread: Double = defaultSpread): DemoQuote {
        require(mid > 0.0)
        val half = spread.coerceAtLeast(0.0) / 2.0
        return update(symbol, mid - half, mid + half, timestamp)
    }

    fun quote(symbol: String): DemoQuote? = quotes[symbol.uppercase()]
    fun all(): List<DemoQuote> = quotes.values.toList()

    fun executionPrice(symbol: String, buy: Boolean): Double =
        requireNotNull(quote(symbol)) { "No demo quote for $symbol" }.let { if (buy) it.ask else it.bid }
}

object DemoPnlCalculator {
    fun pnl(position: DemoTradingEngine.Position, quote: DemoQuote, tickSize: Double, tickValuePerLot: Double): Double {
        require(tickSize > 0.0)
        val exit = if (position.buy) quote.bid else quote.ask
        val ticks = if (position.buy) (exit - position.entry) / tickSize else (position.entry - exit) / tickSize
        return ticks * tickValuePerLot * position.volume
    }
}

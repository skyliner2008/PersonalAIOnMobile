package com.example.personalaibot.tools.trading

/**
 * Central in-memory OHLCV store (foundation layer).
 * - Keeps recent candles by symbol/timeframe/source
 * - Shared by SMC tools as a single cache point
 */
object OhlcvCentralStore {

    private data class Key(
        val symbol: String,
        val interval: String,
        val source: String
    )

    private val store = mutableMapOf<Key, List<Candle>>()

    private fun normalizeSymbol(symbol: String): String = symbol.uppercase()
    private fun normalizeInterval(interval: String): String = interval.lowercase()
    private fun normalizeSource(source: String): String = source.uppercase()

    @Synchronized
    fun put(symbol: String, interval: String, source: String, candles: List<Candle>) {
        if (candles.isEmpty()) return
        val key = Key(
            symbol = normalizeSymbol(symbol),
            interval = normalizeInterval(interval),
            source = normalizeSource(source)
        )
        // Deduplicate by timestamp and keep sorted
        val merged = (store[key].orEmpty() + candles)
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }
        store[key] = merged
    }

    @Synchronized
    fun get(symbol: String, interval: String, source: String, limit: Int): List<Candle> {
        val key = Key(
            symbol = normalizeSymbol(symbol),
            interval = normalizeInterval(interval),
            source = normalizeSource(source)
        )
        return store[key].orEmpty().takeLast(limit)
    }

    @Synchronized
    fun getAny(symbol: String, interval: String, limit: Int): Pair<String, List<Candle>>? {
        val sym = normalizeSymbol(symbol)
        val tf = normalizeInterval(interval)
        val best = store.entries
            .asSequence()
            .filter { it.key.symbol == sym && it.key.interval == tf && it.value.isNotEmpty() }
            .maxByOrNull { it.value.size }
            ?: return null
        return best.key.source to best.value.takeLast(limit)
    }
}


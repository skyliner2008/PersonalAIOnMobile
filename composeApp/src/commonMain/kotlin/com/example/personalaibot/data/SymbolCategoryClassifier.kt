package com.example.personalaibot.data

/**
 * SymbolCategoryClassifier — pure heuristic classifier for MT5 symbol strings.
 *
 * Used by SymbolPickerDialog to group symbols into visual categories.
 * No network I/O — purely deterministic string rules so it works offline.
 *
 * Priority order (first match wins):
 *   1. Metals  — XAU, XAG, XPD, XPT prefix / GOLD / SILVER
 *   2. Crypto  — known coin tickers (BTC, ETH, …) or "USD" suffix with short base
 *   3. Indices — SPX, NAS, US30, GER, UK100, JP225, AUS200, etc.
 *   4. Stocks  — single-word symbols with dot separators (e.g. AAPL.NAS, TSLA.NQ)
 *   5. Forex   — 6-letter pair (EURUSD) or known 3-letter currency prefixes
 *   6. Other   — fallback
 */
enum class SymbolCategory(val displayName: String) {
    METALS("Metals"),
    CRYPTO("Crypto"),
    INDICES("Indices"),
    STOCKS("Stocks"),
    FOREX("Forex"),
    OTHER("Other"),
}

object SymbolCategoryClassifier {

    // ── Metals ────────────────────────────────────────────────────────────────
    private val METAL_PREFIXES = setOf("XAU", "XAG", "XPD", "XPT")
    private val METAL_KEYWORDS = setOf("GOLD", "SILVER", "PALLADIUM", "PLATINUM")

    // ── Crypto ────────────────────────────────────────────────────────────────
    // Common coins; extends gracefully — unknown coins may fall through to OTHER
    private val CRYPTO_BASE = setOf(
        "BTC", "ETH", "BNB", "SOL", "XRP", "ADA", "DOGE", "DOT", "AVAX",
        "MATIC", "LINK", "LTC", "BCH", "ATOM", "UNI", "XLM", "TRX", "ETC",
        "NEAR", "APT", "OP", "ARB", "FTM", "SUI", "INJ", "SEI", "TON",
        "SHIB", "PEPE", "WIF", "BONK", "MEME",
    )
    // Broker-specific crypto suffixes
    private val CRYPTO_SUFFIXES = setOf("USDT", "USDC", "USD", "BTC", "ETH", "BUSD", "PERP")

    // ── Indices ───────────────────────────────────────────────────────────────
    private val INDEX_SUBSTRINGS = listOf(
        "SPX", "SPY", "S&P", "SP500",
        "NAS", "NDX", "NQ",
        "US30", "DOW", "DJI",
        "US500", "US100",
        "GER40", "GER30", "DAX",
        "UK100", "FTSE",
        "JP225", "NIKKEI",
        "AUS200", "ASX",
        "HK50", "HANG",
        "EU50", "STOXX",
        "CAC40", "CAC",
        "CHINA50",
        "VIX",
        "RUSSELL",
    )

    // ── Forex currencies ──────────────────────────────────────────────────────
    private val FOREX_CURRENCIES = setOf(
        "EUR", "USD", "GBP", "JPY", "CHF", "AUD", "NZD", "CAD",
        "SGD", "HKD", "NOK", "SEK", "DKK", "MXN", "ZAR", "TRY",
        "CNH", "CNY", "PLN", "CZK", "HUF", "INR", "THB",
    )

    /**
     * Classify a single MT5 symbol string.
     *
     * @param symbol  Raw symbol as returned by the broker (e.g. "XAUUSD", "BTCUSDT", "EURUSD")
     * @return        Best-matching [SymbolCategory]
     */
    fun classify(symbol: String): SymbolCategory {
        val s = symbol.uppercase().trim()
            .replace("#", "")   // some brokers prefix with #
            .replace(".", "")   // strip exchange suffix for category test (AAPL.NAS → AAPLNAS)

        // 1 ── Metals
        if (METAL_PREFIXES.any { s.startsWith(it) }) return SymbolCategory.METALS
        if (METAL_KEYWORDS.any { s.contains(it) }) return SymbolCategory.METALS

        // 2 ── Crypto: base in CRYPTO_BASE AND ends with a crypto suffix
        for (base in CRYPTO_BASE) {
            if (s.startsWith(base)) {
                val rest = s.removePrefix(base)
                if (rest.isEmpty() || CRYPTO_SUFFIXES.any { rest == it || rest.startsWith(it) }) {
                    return SymbolCategory.CRYPTO
                }
            }
            // Also match reverse pairs: USDBTC
            if (s.endsWith(base)) {
                val prefix = s.removeSuffix(base)
                if (prefix.isEmpty() || FOREX_CURRENCIES.contains(prefix)) {
                    return SymbolCategory.CRYPTO
                }
            }
        }

        // 3 ── Indices
        if (INDEX_SUBSTRINGS.any { s.contains(it) }) return SymbolCategory.INDICES
        // Pure digit-suffix patterns: US30, US500, AUS200, JP225 (already covered above but
        // catch any "WORD + digits" that escaped the list)
        if (Regex("""^[A-Z]{2,5}\d{2,4}$""").matches(s)) return SymbolCategory.INDICES

        // 4 ── Stocks: original symbol (before stripping dot) had a dot — exchange-suffix notation
        val rawUpper = symbol.uppercase().trim()
        if (rawUpper.contains('.') && !rawUpper.startsWith("XA")) return SymbolCategory.STOCKS

        // 5 ── Forex: exactly 6 letters, both halves are known currencies
        if (s.length == 6) {
            val base3 = s.substring(0, 3)
            val quote3 = s.substring(3, 6)
            if (FOREX_CURRENCIES.contains(base3) && FOREX_CURRENCIES.contains(quote3)) {
                return SymbolCategory.FOREX
            }
        }
        // 7-letter forex (e.g. some brokers append 'm' or use 'USD/JPY')
        if (s.length == 7 && s[3] == '/') {
            val base3 = s.substring(0, 3)
            val quote3 = s.substring(4, 7)
            if (FOREX_CURRENCIES.contains(base3) && FOREX_CURRENCIES.contains(quote3)) {
                return SymbolCategory.FOREX
            }
        }
        // Partial match: starts with a known 3-letter currency AND ends with one
        if (s.length in 5..8) {
            val leading = s.substring(0, 3)
            val trailing = s.substring(s.length - 3)
            if (FOREX_CURRENCIES.contains(leading) && FOREX_CURRENCIES.contains(trailing)) {
                return SymbolCategory.FOREX
            }
        }

        return SymbolCategory.OTHER
    }

    /**
     * Classify a list of symbols and return a map from category to sorted symbol list.
     * Categories with no symbols are omitted.
     */
    fun groupSymbols(symbols: List<String>): Map<SymbolCategory, List<String>> {
        return symbols
            .groupBy { classify(it) }
            .mapValues { (_, list) -> list.sorted() }
            .toSortedMap(compareBy { it.ordinal })
    }

    /**
     * Return a display label for a symbol, including its category tag.
     * Useful for accessibility / screen-reader labels.
     */
    fun labelFor(symbol: String): String {
        val cat = classify(symbol)
        return "$symbol (${cat.displayName})"
    }
}

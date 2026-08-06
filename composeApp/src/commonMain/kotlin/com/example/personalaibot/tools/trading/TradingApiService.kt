package com.example.personalaibot.tools.trading

import com.example.personalaibot.data.GeminiService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlinx.datetime.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * TradingApiService — HTTP layer สำหรับดึงข้อมูลการเทรดแบบ Real-time
 */
class TradingApiService(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val smcApi = SmcApiService(client)
    private val modernApi = ModernTechnicalApiService(smcApi)

    /**
     * Resolve the best exchange for a given symbol if not specified by the user.
     * Maps Forex to OANDA/FX_IDC and Commodities to TVC/COMEX.
     */
    fun resolveExchange(symbol: String, requestedExchange: String?): String {
        if (!requestedExchange.isNullOrBlank() && requestedExchange != "BINANCE") return requestedExchange
        
        val s = symbol.uppercase()
        return when {
            // Gold, Silver, Oil
            s == "XAUUSD" || s == "GOLD" -> "TVC"
            s == "XAGUSD" || s == "SILVER" -> "TVC"
            s == "USOIL" || s == "WTI" || s == "CL=F" -> "TVC"
            
            // Forex: usually 6 chars (EURUSD, USDJPY, etc.)
            s.length == 6 && s.all { it.isLetter() } -> "OANDA"
            s.endsWith("=X") -> "FX_IDC"
            
            // Stocks: if user requests common stocks but doesn't specify exchange
            s in listOf("AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "NVDA") -> "NASDAQ"
            
            // Default to BINANCE for crypto-like symbols or if nothing matches
            else -> requestedExchange ?: "BINANCE"
        }
    }

    // ─── Yahoo Finance ────────────────────────────────────────────────────────

    suspend fun getYahooPrice(symbol: String): Map<String, String> {
        val raw = symbol.uppercase().replace("/", "").replace("-", "")
        val yahooSymbol = when {
            raw.contains("XAU") || raw.contains("GOLD") || raw == "GC=F" || raw == "GCF" -> "XAUUSD=X"
            raw.contains("XAG") || raw.contains("SILVER") -> "XAGUSD=X"
            raw.length == 6 && raw.all { it.isLetter() } -> "${raw}=X"
            else -> symbol.uppercase()
        }

        return try {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$yahooSymbol"
            val response = client.get(url) {
                parameter("interval", "1d")
                parameter("range", "1d")
                header("User-Agent", "Mozilla/5.0")
                timeout { requestTimeoutMillis = 15_000 }
            }
            if (!response.status.isSuccess()) return getYahooQuoteFallback(yahooSymbol)

            val body = response.bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject
            val meta = root["chart"]?.jsonObject?.get("result")?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("meta")?.jsonObject
                ?: return getYahooQuoteFallback(yahooSymbol)

            fun metaStr(key: String) = meta[key]?.jsonPrimitive?.contentOrNull ?: "N/A"
            fun metaDbl(key: String) = meta[key]?.jsonPrimitive?.doubleOrNull

            val price       = metaDbl("regularMarketPrice") ?: 0.0
            val prevClose   = metaDbl("chartPreviousClose") ?: metaDbl("previousClose") ?: price
            val change      = price - prevClose
            val changePct   = if (prevClose != 0.0) (change / prevClose * 100) else 0.0
            val high52w     = metaDbl("fiftyTwoWeekHigh") ?: 0.0
            val low52w      = metaDbl("fiftyTwoWeekLow") ?: 0.0
            val currency    = metaStr("currency")
            val marketState = metaStr("marketState")

            mapOf(
                "symbol"       to yahooSymbol,
                "price"        to "%.4f".format(price),
                "change"       to "%.4f".format(change),
                "change_pct"   to "%.2f%%".format(changePct),
                "prev_close"   to "%.4f".format(prevClose),
                "high_52w"     to "%.4f".format(high52w),
                "low_52w"      to "%.4f".format(low52w),
                "currency"     to currency,
                "market_state" to "$marketState | YAHOO_CHART",
                "direction"    to if (change >= 0) "UP" else "DOWN",
                "source"       to "YAHOO_CHART"
            )
        } catch (e: Exception) {
            getYahooQuoteFallback(yahooSymbol)
        }
    }

    suspend fun getBestEffortPrice(symbol: String): Map<String, String> {
        val raw = symbol.uppercase().replace("/", "").replace("-", "")
        val canonical = when {
            raw.contains("XAU") || raw.contains("GOLD") || raw == "GC=F" || raw == "GCF" -> "XAUUSD"
            raw.contains("XAG") || raw.contains("SILVER") -> "XAGUSD"
            raw.endsWith("=X") -> raw.removeSuffix("=X")
            else -> raw
        }

        // Detect Yahoo-style crypto (e.g. BTCUSD from BTC-USD) and create proper Binance symbol
        val cryptoCanonical = when {
            canonical.endsWith("USDT") || canonical.endsWith("BTC") || canonical.endsWith("ETH") -> canonical
            canonical.endsWith("USD") && canonical.length > 3 -> {
                val base = canonical.removeSuffix("USD")
                // Check if it's a Forex pair (base is a known fiat currency)
                val fiatCurrencies = setOf("EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "NZD", "CNY", "HKD", "SGD", "SEK", "NOK", "MXN", "ZAR", "TRY", "INR", "THB")
                if (base.length == 3 && base in fiatCurrencies) canonical  // It's Forex, keep as-is
                else "${base}USDT"  // It's crypto (BTC-USD → BTCUSDT)
            }
            else -> canonical
        }

        val exchanges = when {
            canonical == "XAUUSD" || canonical == "XAGUSD" -> listOf("OANDA", "FX_IDC", "TVC")
            canonical.length == 6 && canonical.all { it.isLetter() } && !cryptoCanonical.endsWith("USDT") -> listOf("OANDA", "FX_IDC")
            cryptoCanonical.endsWith("USDT") -> listOf("BINANCE")
            else -> listOf(resolveExchange(cryptoCanonical, null))
        }

        for (ex in exchanges.distinct()) {
            val tvSymbol = if (ex == "BINANCE") cryptoCanonical else canonical
            val ta = getTechnicalAnalysis(tvSymbol, ex, "1m")
            val close = ta["close"]?.toDoubleOrNull()
            if (close != null && close > 0.0) {
                val change = ta["change"]?.toDoubleOrNull() ?: 0.0
                val prevClose = close - change
                return mapOf(
                    "symbol" to symbol.uppercase(),
                    "canonical_symbol" to canonical,
                    "price" to "%.4f".format(close),
                    "change" to "%.4f".format(change),
                    "change_pct" to "%.2f%%".format(change),
                    "prev_close" to "%.4f".format(prevClose),
                    "high_52w" to "N/A",
                    "low_52w" to "N/A",
                    "currency" to "USD",
                    "market_state" to "LIVE | TV:$ex",
                    "direction" to if (change >= 0.0) "UP" else "DOWN",
                    "source" to "TV:$ex"
                )
            }
        }

        val requestedSymbol = symbol.uppercase()
        val yahoo = getYahooPrice(symbol).toMutableMap()
        if (!yahoo.containsKey("error")) {
            val resolvedSymbol = yahoo["symbol"] ?: canonical
            yahoo["canonical_symbol"] = resolvedSymbol
            yahoo["symbol"] = requestedSymbol
            return yahoo
        }

        getSmcPriceFallback(cryptoCanonical, requestedSymbol)?.let { return it }
        return yahoo
    }

    private suspend fun getSmcPriceFallback(canonical: String, requestedSymbol: String): Map<String, String>? {
        for (tf in listOf("1m", "5m", "15m")) {
            val fetch = smcApi.fetchCandlesWithSource(canonical, tf, 120)
            val candles = fetch.candles
            if (fetch.source == "NONE" || candles.size < 2) continue

            val last = candles.last()
            val prev = candles[candles.lastIndex - 1]
            val change = last.close - prev.close
            val changePct = if (prev.close != 0.0) (change / prev.close) * 100.0 else 0.0

            return mapOf(
                "symbol" to requestedSymbol,
                "canonical_symbol" to canonical,
                "price" to "%.4f".format(last.close),
                "change" to "%.4f".format(change),
                "change_pct" to "%.2f%%".format(changePct),
                "prev_close" to "%.4f".format(prev.close),
                "high_52w" to "N/A",
                "low_52w" to "N/A",
                "currency" to "USD",
                "market_state" to "LIVE | ${fetch.source} | TF:$tf",
                "direction" to if (change >= 0.0) "UP" else "DOWN",
                "source" to "SMC_FALLBACK:${fetch.source}"
            )
        }
        return null
    }

    private suspend fun getYahooQuoteFallback(yahooSymbol: String): Map<String, String> {
        return try {
            val response = client.get("https://query1.finance.yahoo.com/v7/finance/quote") {
                parameter("symbols", yahooSymbol)
                header("User-Agent", "Mozilla/5.0")
                timeout { requestTimeoutMillis = 15_000 }
            }
            if (!response.status.isSuccess()) return mapOf("error" to "HTTP ${response.status.value}")

            val body = response.bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject
            val quote = root["quoteResponse"]?.jsonObject?.get("result")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?: return mapOf("error" to "No quote for symbol: $yahooSymbol")

            fun qStr(key: String) = quote[key]?.jsonPrimitive?.contentOrNull ?: "N/A"
            fun qDbl(key: String) = quote[key]?.jsonPrimitive?.doubleOrNull

            val price = qDbl("regularMarketPrice") ?: 0.0
            val prevClose = qDbl("regularMarketPreviousClose") ?: qDbl("regularMarketOpen") ?: price
            val change = price - prevClose
            val changePct = if (prevClose != 0.0) (change / prevClose * 100) else 0.0

            mapOf(
                "symbol" to (qStr("symbol").takeIf { it.isNotBlank() && it != "N/A" } ?: yahooSymbol),
                "price" to "%.4f".format(price),
                "change" to "%.4f".format(change),
                "change_pct" to "%.2f%%".format(changePct),
                "prev_close" to "%.4f".format(prevClose),
                "high_52w" to "%.4f".format(qDbl("fiftyTwoWeekHigh") ?: 0.0),
                "low_52w" to "%.4f".format(qDbl("fiftyTwoWeekLow") ?: 0.0),
                "currency" to qStr("currency"),
                "market_state" to "${qStr("marketState")} | YAHOO_QUOTE",
                "direction" to if (change >= 0) "UP" else "DOWN",
                "source" to "YAHOO_QUOTE"
            )
        } catch (e: Exception) {
            mapOf("error" to "Yahoo Finance error: ${e.message}")
        }
    }

    // ─── AI Sector Resolver ───────────────────────────────────────────────────

    val TV_US_SECTORS = listOf(
        "Technology Services", "Electronic Technology", "Finance", "Health Technology", 
        "Retail Trade", "Producer Manufacturing", "Energy Minerals", "Consumer Non-Durables", 
        "Utilities", "Consumer Durables", "Non-Energy Minerals", "Consumer Services", 
        "Industrial Services", "Transportation", "Process Industries", "Commercial Services", 
        "Communications", "Health Services", "Distribution Services", "Miscellaneous"
    )

    val TV_TH_SECTORS = listOf(
        "Agro & Food Industry", "Consumer Products", "Financials", "Industrials", 
        "Property & Construction", "Resources", "Services", "Technology",
        "Banking", "Energy & Utilities", "Commerce", "Health Care Services", 
        "Information & Communication Technology", "Food & Beverage", "Finance & Securities",
        "Property Development", "Construction Materials", "Automotive", "Petrochemicals & Chemicals",
        "Transportation & Logistics", "Media & Publishing", "Professional Services", "Tourism & Leisure"
    )

    suspend fun resolveSectorsWithAI(
        geminiService: GeminiService,
        userInput: String,
        market: String
    ): List<String> {
        // Step 1: Priority check using static aliases (fast & verified)
        val staticAliases = getSectorAliases(userInput, market)
        if (staticAliases.size > 1 || (staticAliases.isNotEmpty() && !staticAliases[0].equals(userInput, ignoreCase = true))) {
            return staticAliases
        }

        // Step 2: Fallback to AI for unrecognized terms
        val prompt = """
            Map the user's intent "$userInput" to the most relevant TradingView market sectors for the $market market.
            Authorized sectors: [Commercial & Professional Services, Communications, Consumer Durables, Consumer Non-Durables, Consumer Services, Distribution Services, Electronic Technology, Energy Minerals, Finance, Health Services, Health Technology, Industrial Services, Miscellaneous, Non-Energy Minerals, Process Industries, Producer Manufacturing, Retail Trade, Technology Services, Transportation, Utilities]
            
            Return ONLY a comma-separated list of matches from the authorized list.
            Return exact strings. If no good match, return 'Finance' as a safe default for business/property or 'Technology Services' for tech.
        """.trimIndent()

        return try {
            val response = geminiService.generateResponse(
                prompt = prompt,
                intentAddon = """
                    You are a specialized financial sector mapper for TradingView Scanner.
                    Respond ONLY with a comma-separated list of exact sector names from this authorized list:
                    [Commercial & Professional Services, Communications, Consumer Durables, Consumer Non-Durables, Consumer Services, Distribution Services, Electronic Technology, Energy Minerals, Finance, Health Services, Health Technology, Industrial Services, Miscellaneous, Non-Energy Minerals, Process Industries, Producer Manufacturing, Retail Trade, Technology Services, Transportation, Utilities]
                    
                    Rules:
                    1. For Banks/Finance/Insurance, use 'Finance'.
                    2. For Tech/Electronics, use 'Electronic Technology, Technology Services'.
                    3. For Energy/Power, use 'Energy Minerals, Utilities'.
                    4. For Property/Real Estate, use 'Finance'.
                    5. For Healthcare, use 'Health Services, Health Technology'.
                    
                    Return ONLY the names, no extra text.
                """.trimIndent()
            ).trim()
            if (response.isBlank() || response.contains("error", ignoreCase = true)) listOf(userInput)
            else response.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } catch (_: Exception) { listOf(userInput) }
    }

    suspend fun getMarketSnapshot(
        sector: String? = null,
        market: String? = null,
        limit: Int = 10,
        resolvedAliases: List<String>? = null
    ): Any {
        if (!sector.isNullOrBlank()) {
            return getSectorSnapshot(sector, market ?: "US", limit, resolvedAliases)
        }
        if (market?.uppercase() == "TH") return getSectorSnapshot("All", "TH", limit)
        if (market?.uppercase() == "CRYPTO") return getTopGainers("BINANCE", limit)
        
        // Forex Snapshot
        if (market?.uppercase() == "FOREX" || market?.uppercase() == "FX") {
            val fxPairs = listOf("EURUSD=X", "USDJPY=X", "GBPUSD=X", "AUDUSD=X", "USDCAD=X", "USDCHF=X", "EURGBP=X")
            return fxPairs.associateWith { try { getYahooPrice(it) } catch (_: Exception) { mapOf("error" to "N/A") } }
        }
        
        // Commodities Snapshot
        if (market?.uppercase() == "GOLD" || market?.uppercase() == "COMMODITY") {
            val comms = mapOf("Gold" to "GC=F", "Silver" to "SI=F", "Oil (WTI)" to "CL=F", "Oil (Brent)" to "BZ=F", "Copper" to "HG=F")
            return comms.mapValues { try { getYahooPrice(it.value) } catch (_: Exception) { mapOf("error" to "N/A") } }
        }

        val symbols = mapOf(
            "S&P500" to "^GSPC", "NASDAQ" to "^IXIC", "DOW" to "^DJI", "VIX" to "^VIX",
            "BTC" to "BTC-USD", "ETH" to "ETH-USD", "SOL" to "SOL-USD", "Gold" to "GC=F", "Oil" to "CL=F"
        )
        return symbols.mapValues { (_, sym) ->
            try { getYahooPrice(sym) } catch (_: Exception) { mapOf("error" to "unavailable") }
        }
    }

    private suspend fun getSectorSnapshot(
        sector: String,
        market: String,
        limit: Int,
        resolvedAliases: List<String>? = null
    ): List<Map<String, String>> {
        val isTh = market.uppercase() == "TH"
        val mkt = if (isTh) "thailand" else "america"
        val aliases = resolvedAliases ?: getSectorAliases(sector, market)
        
        val filters = mutableListOf<JsonObject>()
        
        // Exchange filter
        if (isTh) {
            filters.add(buildJsonObject {
                put("left", "exchange")
                put("operation", "in_range")
                put("right", buildJsonArray { add("SET"); add("MAI") })
            })
        } else {
            filters.add(buildJsonObject {
                put("left", "exchange")
                put("operation", "equal")
                put("right", "NASDAQ") // Default to NASDAQ for US snapshot if not specified
            })
        }

        // Sector filter
        if (!sector.equals("All", ignoreCase = true)) {
            filters.add(buildJsonObject {
                put("left", "sector")
                if (aliases.size == 1) {
                    put("operation", "equal"); put("right", aliases[0])
                } else {
                    put("operation", "in_range")
                    put("right", buildJsonArray { aliases.forEach { add(it) } })
                }
            })
        }
        
        return scanTradingViewByMarket(mkt, extraFilters = filters, sortBy = "market_cap_basic", limit = 20)
    }

    private fun getSectorAliases(sector: String, market: String): List<String> {
        val s = sector.lowercase()
        val isTh = market.uppercase() == "TH"
        return when {
            s.contains("energy") || s.contains("oil") || s.contains("gas") ->
                listOf("Energy Minerals", "Utilities")
            s.contains("tech") || s.contains("ai") || s.contains("semiconductor") ->
                listOf("Electronic Technology", "Technology Services")
            s.contains("finance") || s.contains("bank") || s.contains("property") || s.contains("estate") ->
                listOf("Finance")
            s.contains("retail") || s.contains("commerce") || s.contains("consumer") ->
                listOf("Retail Trade", "Consumer Services")
            s.contains("health") || s.contains("hospital") || s.contains("medical") ->
                listOf("Health Services", "Health Technology")
            s.contains("transport") || s.contains("logistics") ->
                listOf("Transportation", "Industrial Services")
            s.contains("industrial") || s.contains("manufactur") ->
                listOf("Producer Manufacturing", "Industrial Services", "Process Industries")
            else -> listOf(sector)
        }
    }

    private val defaultColumns = listOf("name", "description", "market_cap_basic", "close", "change", "change_abs", "volume", "RSI", "MACD.macd", "MACD.signal", "BB.upper", "BB.lower", "BB.basis", "ATR", "ADX", "EMA20", "EMA50", "Stoch.K", "Stoch.D", "Recommend.All")

    private fun buildScannerBody(exchange: String, sortBy: String = "change", sortOrder: String = "desc", filters: List<JsonObject> = emptyList(), columns: List<String> = defaultColumns, limit: Int = 50): JsonObject = buildJsonObject {
        put("filter", buildJsonArray {
            if (!exchange.isNullOrBlank()) {
                add(buildJsonObject { put("left", "exchange"); put("operation", "equal"); put("right", exchange.uppercase()) })
            }
            add(buildJsonObject { put("left", "volume"); put("operation", "greater"); put("right", 0) })
            filters.forEach { add(it) }
        })
        put("columns", buildJsonArray { columns.forEach { add(it) } })
        put("sort", buildJsonObject { put("sortBy", sortBy); put("sortOrder", sortOrder) })
        put("range", buildJsonArray { add(0); add(limit) })
    }

    suspend fun getTopGainers(exchange: String, limit: Int = 25) = scanTradingView(exchange, sortBy = "change", sortOrder = "desc", limit = limit)
    suspend fun getTopLosers(exchange: String, limit: Int = 25) = scanTradingView(exchange, sortBy = "change", sortOrder = "asc", limit = limit)
    suspend fun getBollingerSqueeze(exchange: String, limit: Int = 50) = scanTradingView(exchange, sortBy = "BB.width", sortOrder = "asc", extraFilters = listOf(buildJsonObject { put("left", "BB.width"); put("operation", "less"); put("right", 0.04) }), columns = defaultColumns + "BB.width", limit = limit)
    suspend fun getOversoldSymbols(exchange: String, limit: Int = 30) = scanTradingView(exchange, extraFilters = listOf(buildJsonObject { put("left", "RSI"); put("operation", "less"); put("right", 30) }), sortBy = "RSI", sortOrder = "asc", limit = limit)
    suspend fun getOverboughtSymbols(exchange: String, limit: Int = 30) = scanTradingView(exchange, extraFilters = listOf(buildJsonObject { put("left", "RSI"); put("operation", "greater"); put("right", 70) }), sortBy = "RSI", sortOrder = "desc", limit = limit)
    suspend fun getVolumeBreakout(exchange: String, limit: Int = 25) = scanTradingView(exchange, extraFilters = listOf(buildJsonObject { put("left", "change"); put("operation", "greater"); put("right", 3.0) }, buildJsonObject { put("left", "volume"); put("operation", "greater"); put("right", 100000) }), sortBy = "relative_volume_10d_calc", sortOrder = "desc", columns = defaultColumns + "relative_volume_10d_calc", limit = limit)

    private suspend fun scanTradingViewByMarket(marketPath: String, exchange: String? = null, sortBy: String = "change", sortOrder: String = "desc", extraFilters: List<JsonObject> = emptyList(), columns: List<String> = defaultColumns, limit: Int = 50): List<Map<String, String>> {
        return try {
            val url = "https://scanner.tradingview.com/$marketPath/scan"
            val body = buildScannerBody(exchange ?: "", sortBy, sortOrder, extraFilters, columns, limit)
            val resp = client.post(url) { contentType(ContentType.Application.Json); setBody(body.toString()); header("User-Agent", "Mozilla/5.0"); timeout { requestTimeoutMillis = 20_000 } }
            if (!resp.status.isSuccess()) return listOf(mapOf("error" to "HTTP ${resp.status.value}"))
            val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            
            val results = data.map { node ->
                val d = node.jsonObject
                val name = d["s"]?.jsonPrimitive?.content ?: "Unknown"
                val vals = d["d"]?.jsonArray ?: return@map emptyMap<String, String>()
                
                val map = mutableMapOf<String, String>()
                map["symbol"] = name.substringAfter(":")
                columns.forEachIndexed { i, col ->
                    val v = vals.getOrNull(i)
                    map[col] = when {
                        v == null || v is JsonNull -> "N/A"
                        v.jsonPrimitive.isString -> v.jsonPrimitive.content
                        col == "market_cap_basic" -> formatMarketCap(v.jsonPrimitive.doubleOrNull ?: 0.0)
                        else -> v.jsonPrimitive.doubleOrNull?.let { "%.4f".format(it).trimEnd('0').trimEnd('.') } ?: v.jsonPrimitive.content
                    }
                }
                map
            }.filter { it.isNotEmpty() }

            if (marketPath == "thailand") {
                results.filter { !(it["symbol"] ?: "").contains(".") }
            } else {
                results
            }
        } catch (e: Exception) { listOf(mapOf("error" to "Scanner error: ${e.message}")) }
    }

    private fun formatMarketCap(cap: Double): String {
        return when {
            cap >= 1_000_000_000_000 -> "${"%.2f".format(cap / 1_000_000_000_000)}T"
            cap >= 1_000_000_000 -> "${"%.2f".format(cap / 1_000_000_000)}B"
            cap >= 1_000_000 -> "${"%.2f".format(cap / 1_000_000)}M"
            else -> "%.2f".format(cap)
        }
    }

    suspend fun scanTradingView(
        exchange: String, sortBy: String = "market_cap_basic", sortOrder: String = "desc", extraFilters: List<JsonObject> = emptyList(), columns: List<String> = defaultColumns, limit: Int = 50): List<Map<String, String>> {
        val market = exchangeToMarket(exchange)
        return scanTradingViewByMarket(market, exchange, sortBy, sortOrder, extraFilters, columns, limit)
    }


    suspend fun getTechnicalAnalysis(symbol: String, exchange: String, interval: String = "1h"): Map<String, String> {
        return try {
            val fullSymbol = if (":" in symbol) symbol else "${exchange.uppercase()}:${symbol.uppercase()}"
            // TradingView scanner เลือก timeframe ผ่าน suffix ท้ายชื่อคอลัมน์ เช่น RSI|15, close|240
            // (plain = 1h) — ก่อนหน้านี้ param interval ไม่ได้ถูกใช้ ทำให้ alert ติดที่ TF 1h เสมอ
            val tfSuffix = when (interval.lowercase()) {
                "1m" -> "|1"; "3m" -> "|3"; "5m" -> "|5"; "15m" -> "|15"; "30m" -> "|30"; "45m" -> "|45"
                "4h" -> "|240"; "1d" -> "|1D"; "1w" -> "|1W"
                else -> ""
            }
            val requestCols = taColumns.map { it + tfSuffix }
            val url = "https://scanner.tradingview.com/symbol"
            val resp = client.get(url) { parameter("symbol", fullSymbol); parameter("fields", requestCols.joinToString(",")); header("User-Agent", "Mozilla/5.0"); timeout { requestTimeoutMillis = 15_000 } }
            if (!resp.status.isSuccess()) return mapOf("error" to "HTTP ${resp.status.value}")
            val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val res = mutableMapOf("symbol" to symbol.uppercase())
            taColumns.forEach { col ->
                val v = root[col + tfSuffix]
                res[col] = when {
                    v == null || v is JsonNull -> "N/A"
                    v.jsonPrimitive.isString -> v.jsonPrimitive.content
                    else -> v.jsonPrimitive.doubleOrNull?.let { "%.4f".format(it).trimEnd('0').trimEnd('.') } ?: v.jsonPrimitive.content
                }
            }
            val rec = root["Recommend.All$tfSuffix"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            res["signal"] = when { rec >= 0.5 -> "STRONG BUY"; rec >= 0.1 -> "BUY"; rec >= -0.1 -> "HOLD"; rec >= -0.5 -> "SELL"; else -> "STRONG SELL" }
            res["recommend_score"] = "%.3f".format(rec)
            res
        } catch (e: Exception) { mapOf("error" to "TA error: ${e.message}") }
    }

    suspend fun getMultiTimeframeAnalysis(symbol: String, exchange: String): Map<String, Map<String, String>> {
        return listOf("1W", "1D", "4h", "1h", "15m").associateWith { try { getTechnicalAnalysis(symbol, exchange, it) } catch (_: Exception) { mapOf("error" to "N/A") } }
    }

    private val taColumns = listOf("close", "change", "volume", "RSI", "MACD.macd", "MACD.signal", "BB.basis", "BB.width", "ATR", "ADX", "Recommend.All", "buy_signals", "sell_signals", "neutral_signals")

    suspend fun getRedditSentiment(symbol: String): Map<String, Any> {
        val query = symbol.uppercase().removeSuffix("-USD")
        var total = 0; var bull = 0; var bear = 0
        for (sub in listOf("wallstreetbets", "stocks", "investing")) {
            try {
                val resp = client.get("https://www.reddit.com/r/$sub/search.json") { parameter("q", query); parameter("sort", "hot"); parameter("t", "day"); header("User-Agent", "Mozilla/5.0") }
                if (!resp.status.isSuccess()) continue
                val posts = json.parseToJsonElement(resp.bodyAsText()).jsonObject["data"]?.jsonObject?.get("children")?.jsonArray ?: continue
                posts.forEach { post ->
                    val title = post.jsonObject["data"]?.jsonObject?.get("title")?.jsonPrimitive?.content?.lowercase() ?: return@forEach
                    total++; if (listOf("buy", "bull", "moon").any { title.contains(it) }) bull++; if (listOf("sell", "bear", "crash").any { title.contains(it) }) bear++
                }
            } catch (_: Exception) {}
        }
        val score = if (total > 0) (bull - bear).toDouble() / total else 0.0
        return mapOf(
            "symbol" to query,
            "sentiment_score" to score,
            "sentiment_label" to if (score > 0.1) "Bullish" else if (score < -0.1) "Bearish" else "Neutral",
            "posts_analyzed" to total,
            "bullish_posts" to bull,
            "bearish_posts" to bear
        )
    }

    suspend fun getFinancialNews(symbol: String? = null, limit: Int = 10): Map<String, Any> = coroutineScope {
        val all = mutableListOf<Map<String, String>>()

        // 1) Symbol-specific: Google News RSS (ค้นตรงสินทรัพย์ฝั่ง server ฟรี ไม่ต้อง API key)
        if (!symbol.isNullOrBlank()) {
            val q = getAssetQuery(symbol).encodeURLParameter()
            all.addAll(fetchFeed("Google News",
                "https://news.google.com/rss/search?q=$q&hl=en-US&gl=US&ceid=US:en") { xml ->
                parseRssItems(xml, "Google News", null)
            })
        }

        // 2) General feeds หลายแหล่ง (กรอง keyword ถ้ามี symbol) — ดึงขนานกัน
        val feeds = mapOf(
            "Yahoo" to "https://finance.yahoo.com/news/rssindex",
            "CNBC" to "https://www.cnbc.com/id/100003114/device/rss/rss.html",
            "MarketWatch" to "https://feeds.content.dowjones.io/public/rss/mw_topstories",
            "Investing.com" to "https://www.investing.com/rss/news.rss",
            "Cointelegraph" to "https://cointelegraph.com/rss"
        )
        feeds.map { (src, url) ->
            async { fetchFeed(src, url) { xml -> parseRssItems(xml, src, symbol) } }
        }.forEach { all.addAll(it.await()) }

        // กรองข่าวซ้ำและจำกัดจำนวน
        val uniqueNews = all.distinctBy { it["title"]?.lowercase() }
        return@coroutineScope mapOf("news" to uniqueNews.take(limit))
    }

    /** ดึง RSS feed เดียวแบบปลอดภัย (timeout 10 วิ, ล้มเหลวคืน list ว่าง) */
    private suspend fun fetchFeed(src: String, url: String, parse: (String) -> List<Map<String, String>>): List<Map<String, String>> {
        return try {
            val resp = client.get(url) {
                header("User-Agent", "Mozilla/5.0")
                timeout { requestTimeoutMillis = 10_000 }
            }
            if (resp.status.isSuccess()) parse(resp.bodyAsText()) else {
                com.example.personalaibot.logDebug("TradingApi", "Feed $src failed: HTTP ${resp.status.value}")
                emptyList()
            }
        } catch (_: Exception) { emptyList() }
    }

    /** สร้าง search query สำหรับ Google News ตามประเภทสินทรัพย์ */
    private fun getAssetQuery(symbol: String): String {
        val s = symbol.uppercase().trim()
        return when {
            s.contains("XAU") || s == "GOLD" -> "gold price OR XAUUSD OR bullion"
            s.contains("XAG") || s == "SILVER" -> "silver price OR XAGUSD"
            s in listOf("USOIL", "WTI", "CL=F") -> "crude oil price OR WTI"
            s.contains("BTC") -> "bitcoin price OR BTC"
            s.contains("ETH") -> "ethereum price"
            s.endsWith("=F") -> "${s.removeSuffix("=F")} futures price"
            s.endsWith("=X") -> "${s.removeSuffix("=X")} exchange rate"
            s.length == 6 && s.all { it.isLetter() } -> "${s.take(3)}/${s.takeLast(3)} forex OR ${s.take(3)} exchange rate"
            else -> "$s stock"
        }
    }

    /**
     * ดึงปฏิทินเศรษฐกิจรายสัปดาห์จาก ForexFactory (ฟรี ไม่ต้องใช้ API Key)
     * แหล่งเดิม FXStreet API ตายแล้ว (401) — เปลี่ยนมาใช้ ff_calendar_thisweek.xml
     * เวลาใน feed เป็น ET (New York) — แปลงเป็นเวลาไทยให้พร้อมกัน
     */
    suspend fun getEconomicCalendar(limit: Int = 10): List<Map<String, String>> {
        return try {
            val resp = client.get("https://nfs.faireconomy.media/ff_calendar_thisweek.xml") {
                header("User-Agent", "curl/8.0")
                timeout { requestTimeoutMillis = 15_000 }
            }
            if (!resp.status.isSuccess()) {
                com.example.personalaibot.logDebug("TradingApi", "FF calendar failed: HTTP ${resp.status.value}")
                return emptyList()
            }
            val xml = resp.bodyAsText()

            val events: List<Map<String, String>> = Regex("<event>(.*?)</event>", RegexOption.DOT_MATCHES_ALL)
                .findAll(xml).mapNotNull { block ->
                    val c = block.groupValues[1]
                    fun tag(name: String): String {
                        val m = Regex(
                            "<$name>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</$name>",
                            RegexOption.DOT_MATCHES_ALL
                        ).find(c) ?: return ""
                        return m.groupValues[1].trim()
                    }
                    val title = tag("title")
                    if (title.isBlank()) return@mapNotNull null
                    val impact = tag("impact")
                    val date = tag("date"); val time = tag("time")
                    mapOf(
                        "title" to title,
                        "country" to tag("country"),
                        "impact" to impact,
                        "date_time" to formatEventTimeThai(date, time),
                        "date_sort" to eventSortKey(date, time),
                        "actual" to "-",
                        "forecast" to tag("forecast").ifBlank { "-" },
                        "previous" to tag("previous").ifBlank { "-" },
                        "impact_score" to when (impact.lowercase()) {
                            "high" -> "3"; "medium" -> "2"; "low" -> "1"; else -> "0"
                        }
                    )
                }
                .sortedWith(
                    compareByDescending<Map<String, String>> { it["impact_score"]?.toInt() ?: 0 }
                        .thenBy { it["date_sort"] ?: "" }
                )
                .take(limit)
                .toList()
            events
        } catch (e: Exception) {
            com.example.personalaibot.logDebug("TradingApi", "FF calendar error: ${e.message}")
            emptyList<Map<String, String>>()
        }
    }

    /** แปลงเวลา ET (New York) ของ ForexFactory เป็นเวลาไทย — input: date "MM-dd-yyyy", time "h:mmam/pm" */
    private fun formatEventTimeThai(date: String, time: String): String {
        return try {
            val dp = date.split("-")
            if (dp.size != 3) return "$date $time".trim()
            val iso = "${dp[2]}-${dp[0]}-${dp[1]}"
            val t = time.lowercase().trim()
            val m = Regex("(\\d+):(\\d+)(am|pm)").find(t)
                ?: return "$iso ($time)"
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2]
            if (m.groupValues[3] == "pm" && h != 12) h += 12
            if (m.groupValues[3] == "am" && h == 12) h = 0
            val ldt = LocalDateTime.parse("${iso}T${h.toString().padStart(2, '0')}:$min:00")
            val instant = ldt.toInstant(TimeZone.of("America/New_York"))
            val bkk = instant.toLocalDateTime(TimeZone.of("Asia/Bangkok"))
            "${bkk.date} ${bkk.hour.toString().padStart(2, '0')}:${bkk.minute.toString().padStart(2, '0')} น. (ไทย) | $iso $time ET"
        } catch (_: Exception) { "$date $time".trim() }
    }

    private fun eventSortKey(date: String, time: String): String {
        return try {
            val dp = date.split("-")
            val iso = if (dp.size == 3) "${dp[2]}-${dp[0]}-${dp[1]}" else date
            val t = time.lowercase().trim()
            val m = Regex("(\\d+):(\\d+)(am|pm)").find(t)
            if (m != null) {
                var h = m.groupValues[1].toInt()
                if (m.groupValues[3] == "pm" && h != 12) h += 12
                if (m.groupValues[3] == "am" && h == 12) h = 0
                "$iso ${h.toString().padStart(2, '0')}:${m.groupValues[2]}"
            } else "$iso 99"
        } catch (_: Exception) { date }
    }

    /**
     * ดึงข้อมูลอนุกรมเวลาเศรษฐกิจสหรัฐฯ จาก FRED (Federal Reserve Economic Data)
     * - ไม่มี API Key → ใช้ endpoint สาธารณะ fredgraph.csv (ฟรี ไม่ต้องสมัคร)
     * - มี API Key → ใช้ FRED JSON API (api.stlouisfed.org)
     * @return list of (date, value) เรียงจากเก่า→ใหม่ หรือ null ถ้าดึงไม่สำเร็จ
     */
    suspend fun getFredSeriesObservations(seriesId: String, apiKey: String? = null): List<Pair<String, Double>>? {
        if (!apiKey.isNullOrBlank()) {
            getFredViaJsonApi(seriesId, apiKey)?.let { return it }
        }
        return getFredViaCsv(seriesId)
    }

    private suspend fun getFredViaCsv(seriesId: String): List<Pair<String, Double>>? {
        return try {
            val resp = client.get("https://fred.stlouisfed.org/graph/fredgraph.csv") {
                parameter("id", seriesId.uppercase())
                // CDN ของ FRED กรอง User-Agent: browser UA ถูก stall, ไม่มี UA ถูกปฏิเสธ
                // ผ่านเฉพาะ UA แบบ curl (ทดสอบแล้ว: curl/8.0 → 200 OK)
                header("User-Agent", "curl/8.0")
                timeout { requestTimeoutMillis = 15_000 }
            }
            if (!resp.status.isSuccess()) {
                com.example.personalaibot.logDebug("TradingApi", "FRED CSV $seriesId failed: HTTP ${resp.status.value}")
                return null
            }
            resp.bodyAsText().lines().drop(1).mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size < 2) return@mapNotNull null
                // FRED ใช้ "." แทนค่าที่ไม่มีข้อมูล
                val v = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
                parts[0].trim() to v
            }.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            com.example.personalaibot.logDebug("TradingApi", "FRED CSV $seriesId error: ${e.message}")
            null
        }
    }

    private suspend fun getFredViaJsonApi(seriesId: String, apiKey: String): List<Pair<String, Double>>? {
        return try {
            val resp = client.get("https://api.stlouisfed.org/fred/series/observations") {
                parameter("series_id", seriesId.uppercase())
                parameter("api_key", apiKey)
                parameter("file_type", "json")
                parameter("sort_order", "asc")
                header("User-Agent", "curl/8.0")
                timeout { requestTimeoutMillis = 15_000 }
            }
            if (!resp.status.isSuccess()) return null
            val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            root["observations"]?.jsonArray?.mapNotNull { obs ->
                val date = obs.jsonObject["date"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val v = obs.jsonObject["value"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    ?: return@mapNotNull null
                date to v
            }?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) { null }
    }

    /**
     * API สำหรับเรียกใช้ Modern Technical Analysis
     */
    suspend fun getModernTechnicalAnalysis(symbol: String, interval: String): ModernAnalysisResult? {
        val fetch = smcApi.fetchCandlesWithSource(symbol, interval, 300)
        if (fetch.candles.size < 150) return null
        
        // ดึง SMC มาเป็นตัวช่วยกรอง Confluence
        val smc = smcApi.getSmcAnalysis(symbol, interval)
        
        return modernApi.analyze(fetch.candles, symbol, interval, smc)
    }

    private fun parseRssItems(xml: String, src: String, sym: String?): List<Map<String, String>> {
        val keywords = if (!sym.isNullOrBlank()) getAssetKeywords(sym) else emptyList()

        fun tag(content: String, name: String): String =
            Regex("<$name>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</$name>", RegexOption.DOT_MATCHES_ALL)
                .find(content)?.groupValues?.get(1)?.trim() ?: ""

        fun clean(html: String): String = html
            // unescape ก่อน (Google News เข้ารหัส HTML ไว้) แล้วค่อย strip tag 2 รอบ
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ").trim()

        return Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { block ->
            val content = block.groupValues[1]
            var title = clean(tag(content, "title"))
            if (title.isBlank()) return@mapNotNull null
            val desc = clean(tag(content, "description"))
            val link = tag(content, "link")

            // Google News ต่อท้าย title ด้วย " - SourceName" → แยกเป็น source จริง
            var source = src
            if (src == "Google News" && title.contains(" - ")) {
                val idx = title.lastIndexOf(" - ")
                source = title.substring(idx + 3)
                title = title.substring(0, idx)
            }

            // กรองด้วย keyword เฉพาะ general feeds เมื่อมี symbol (Google News ค้นฝั่ง server แล้ว)
            if (keywords.isNotEmpty()) {
                val fullText = (title + desc).lowercase()
                if (keywords.none { fullText.contains(it) }) return@mapNotNull null
            }

            // Google News ใส่ description = "title + source" ซ้ำกัน → ลบทิ้งถ้าไม่มีเนื้อหาเพิ่ม
            val cleanDesc = if (desc.isBlank() || desc.removeSuffix(source).trim() == title) "" else desc

            mapOf(
                "title" to title,
                "description" to (cleanDesc.take(200) + if (cleanDesc.length > 200) "..." else ""),
                "link" to link,
                "source" to source
            )
        }.toList()
    }

    private fun getAssetKeywords(symbol: String): List<String> {
        val s = symbol.lowercase()
        return when {
            s.contains("xau") || s.contains("gold") -> listOf("gold", "xau", "fed", "inflation", "bullion", "treasury")
            s.contains("btc") || s.contains("bitcoin") -> listOf("bitcoin", "btc", "crypto", "etf", "halving", "satoshi")
            s.contains("eth") || s.contains("ether") -> listOf("ethereum", "eth", "vitalik", "layer 2", "staking")
            s.length == 6 && !s.contains("usdt") -> listOf(s.substring(0, 3), s.substring(3), s, "forex", "central bank") // FX Pairs
            else -> listOf(s, s.replace("usdt", ""), s.replace("usd", ""))
        }.filter { it.isNotBlank() }
    }

    private fun exchangeToMarket(ex: String) = if (ex.uppercase() in listOf("NASDAQ", "NYSE")) "america" else if (ex.uppercase() in listOf("SET", "MAI")) "thailand" else "crypto"
    private fun mapInterval(i: String) = i.uppercase()

    // ─── Fear & Greed Index (alternative.me — ฟรี ไม่ต้องใช้ API Key) ────────

    /**
     * ดึง Crypto Fear & Greed Index ตัวจริงจาก alternative.me
     * คืน map: value (0-100), classification, trend (ค่าย้อนหลัง N วัน "newest,…,oldest")
     */
    suspend fun getFearGreedIndex(limit: Int = 7): Map<String, String> {
        return try {
            val resp = client.get("https://api.alternative.me/fng/?limit=$limit&format=json") {
                timeout { requestTimeoutMillis = 10_000 }
            }
            if (!resp.status.isSuccess()) {
                return mapOf("error" to "HTTP ${resp.status.value}")
            }
            val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val data = root["data"]?.jsonArray ?: return mapOf("error" to "no data")
            if (data.isEmpty()) return mapOf("error" to "no data")
            val latest = data[0].jsonObject
            val trend = data.mapNotNull { it.jsonObject["value"]?.jsonPrimitive?.contentOrNull }
                .joinToString(",")
            mapOf(
                "value" to (latest["value"]?.jsonPrimitive?.contentOrNull ?: "0"),
                "classification" to (latest["value_classification"]?.jsonPrimitive?.contentOrNull ?: "Unknown"),
                "trend" to trend
            )
        } catch (e: Exception) {
            com.example.personalaibot.logDebug("TradingApi", "FearGreed error: ${e.message}")
            mapOf("error" to (e.message ?: "unknown"))
        }
    }

    // ─── CoinGecko (ฟรี ไม่ต้องใช้ API Key) ──────────────────────────────────

    /**
     * ภาพรวมตลาดคริปโตจาก CoinGecko /global — market cap รวม, BTC/ETH dominance,
     * volume 24h, % เปลี่ยนแปลง market cap, จำนวนเหรียญ/ตลาดที่ active
     */
    suspend fun getCryptoGlobal(): Map<String, String> {
        return try {
            val resp = client.get("https://api.coingecko.com/api/v3/global") {
                header("User-Agent", "curl/8.0")
                timeout { requestTimeoutMillis = 12_000 }
            }
            if (!resp.status.isSuccess()) {
                return mapOf("error" to "HTTP ${resp.status.value}")
            }
            val data = json.parseToJsonElement(resp.bodyAsText())
                .jsonObject["data"]?.jsonObject ?: return mapOf("error" to "no data")
            fun num(path: JsonObject?, key: String) =
                path?.get(key)?.jsonPrimitive?.contentOrNull ?: "-"
            val totalMcap = data["total_market_cap"]?.jsonObject
            val totalVol  = data["total_volume"]?.jsonObject
            val dom       = data["market_cap_percentage"]?.jsonObject
            mapOf(
                "total_market_cap_usd" to num(totalMcap, "usd"),
                "total_volume_24h_usd" to num(totalVol, "usd"),
                "btc_dominance" to num(dom, "btc"),
                "eth_dominance" to num(dom, "eth"),
                "market_cap_change_24h" to num(data, "market_cap_change_percentage_24h_usd"),
                "active_cryptocurrencies" to num(data, "active_cryptocurrencies"),
                "markets" to num(data, "markets")
            )
        } catch (e: Exception) {
            com.example.personalaibot.logDebug("TradingApi", "CoinGecko global error: ${e.message}")
            mapOf("error" to (e.message ?: "unknown"))
        }
    }

    /**
     * เหรียญที่กำลัง trending บน CoinGecko (24h ที่คนค้นหามากสุด)
     * คืน list ของ map: name, symbol, market_cap_rank, price_btc
     */
    suspend fun getCryptoTrending(): List<Map<String, String>> {
        return try {
            val resp = client.get("https://api.coingecko.com/api/v3/search/trending") {
                header("User-Agent", "curl/8.0")
                timeout { requestTimeoutMillis = 12_000 }
            }
            if (!resp.status.isSuccess()) return emptyList()
            val coins = json.parseToJsonElement(resp.bodyAsText())
                .jsonObject["coins"]?.jsonArray ?: return emptyList()
            coins.mapNotNull { c ->
                val item = c.jsonObject["item"]?.jsonObject ?: return@mapNotNull null
                mapOf(
                    "name" to (item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null),
                    "symbol" to (item["symbol"]?.jsonPrimitive?.contentOrNull ?: ""),
                    "market_cap_rank" to (item["market_cap_rank"]?.jsonPrimitive?.contentOrNull ?: "-")
                )
            }
        } catch (e: Exception) {
            com.example.personalaibot.logDebug("TradingApi", "CoinGecko trending error: ${e.message}")
            emptyList()
        }
    }
}

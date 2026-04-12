package com.example.personalaibot.tools.trading

import com.example.personalaibot.data.GeminiService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * TradingApiService — HTTP layer สำหรับดึงข้อมูลการเทรดแบบ Real-time
 */
class TradingApiService(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // ─── Yahoo Finance ──────────────────────────────────────────────────────

    suspend fun getYahooPrice(symbol: String): Map<String, String> {
        return try {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/${symbol.uppercase()}"
            val response = client.get(url) {
                parameter("interval", "1d")
                parameter("range", "1d")
                header("User-Agent", "Mozilla/5.0")
                timeout { requestTimeoutMillis = 15_000 }
            }
            if (!response.status.isSuccess()) return mapOf("error" to "HTTP ${response.status.value}")

            val body = response.bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject
            val meta = root["chart"]?.jsonObject?.get("result")?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("meta")?.jsonObject
                ?: return mapOf("error" to "ไม่พบข้อมูล symbol: $symbol")

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
                "symbol"       to symbol.uppercase(),
                "price"        to "%.4f".format(price),
                "change"       to "%.4f".format(change),
                "change_pct"   to "%.2f%%".format(changePct),
                "prev_close"   to "%.4f".format(prevClose),
                "high_52w"     to "%.4f".format(high52w),
                "low_52w"      to "%.4f".format(low52w),
                "currency"     to currency,
                "market_state" to marketState,
                "direction"    to if (change >= 0) "▲" else "▼"
            )
        } catch (e: Exception) {
            mapOf("error" to "Yahoo Finance error: ${e.message}")
        }
    }

    // ─── AI Sector Resolver ────────────────────────────────────────────────

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
            s.contains("energy") || s.contains("พลังงาน") -> listOf("Energy Minerals", "Utilities")
            s.contains("tech") || s.contains("เทคโนโลยี") -> listOf("Electronic Technology", "Technology Services")
            s.contains("finance") || s.contains("การเงิน") || s.contains("bank") || s.contains("property") || s.contains("estate") || s.contains("อสังหา") -> listOf("Finance")
            s.contains("retail") || s.contains("ค้าปลีก") || s.contains("commerce") -> listOf("Retail Trade", "Consumer Services")
            s.contains("health") || s.contains("hospital") || s.contains("สุขภาพ") || s.contains("โรงพยาบาล") -> listOf("Health Services", "Health Technology")
            s.contains("transport") || s.contains("logistics") || s.contains("ขนส่ง") -> listOf("Transportation", "Industrial Services")
            s.contains("industrial") || s.contains("อุตสาหกรรม") || s.contains("manufactur") -> listOf("Producer Manufacturing", "Industrial Services", "Process Industries")
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
            val url = "https://scanner.tradingview.com/symbol"
            val resp = client.get(url) { parameter("symbol", fullSymbol); parameter("fields", taColumns.joinToString(",")); header("User-Agent", "Mozilla/5.0"); timeout { requestTimeoutMillis = 15_000 } }
            if (!resp.status.isSuccess()) return mapOf("error" to "HTTP ${resp.status.value}")
            val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val res = mutableMapOf("symbol" to symbol.uppercase())
            taColumns.forEach { col ->
                val v = root[col]
                res[col] = when {
                    v == null || v is JsonNull -> "N/A"
                    v.jsonPrimitive.isString -> v.jsonPrimitive.content
                    else -> v.jsonPrimitive.doubleOrNull?.let { "%.4f".format(it).trimEnd('0').trimEnd('.') } ?: v.jsonPrimitive.content
                }
            }
            val rec = root["Recommend.All"]?.jsonPrimitive?.doubleOrNull ?: 0.0
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
        return mapOf("symbol" to query, "sentiment_score" to score, "sentiment_label" to if (score > 0.1) "Bullish 📈" else if (score < -0.1) "Bearish 📉" else "Neutral ➡️", "posts_analyzed" to total)
    }

    suspend fun getFinancialNews(symbol: String? = null, limit: Int = 10): Map<String, Any> {
        val all = mutableListOf<Map<String, String>>()
        for (url in listOf("https://finance.yahoo.com/news/rssindex")) {
            try {
                val resp = client.get(url) { header("User-Agent", "Mozilla/5.0") }
                if (resp.status.isSuccess()) all.addAll(parseRssItems(resp.bodyAsText(), "Yahoo", symbol))
            } catch (_: Exception) {}
        }
        return mapOf("news" to all.take(limit))
    }

    private fun parseRssItems(xml: String, src: String, sym: String?): List<Map<String, String>> {
        return Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { block ->
            val content = block.groupValues[1]
            val title = Regex("<title>(.*?)</title>").find(content)?.groupValues?.get(1) ?: return@mapNotNull null
            mapOf("title" to title, "source" to src)
        }.toList()
    }

    private fun exchangeToMarket(ex: String) = if (ex.uppercase() in listOf("NASDAQ", "NYSE")) "america" else if (ex.uppercase() in listOf("SET", "MAI")) "thailand" else "crypto"
    private fun mapInterval(i: String) = i.uppercase()
}

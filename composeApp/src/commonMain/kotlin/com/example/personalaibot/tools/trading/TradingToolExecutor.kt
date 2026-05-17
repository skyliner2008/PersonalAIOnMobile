package com.example.personalaibot.tools.trading

import io.ktor.client.*
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.logDebug

/**
 * TradingToolExecutor — รับ trading tool calls และ format ผลลัพธ์เป็น text
 * สำหรับส่งกลับให้ Gemini อ่านและอธิบายให้ผู้ใช้
 *
 * รองรับทั้ง Classic Trading Tools และ SMC (Smart Money Concepts) Tools
 */
class TradingToolExecutor(private val client: HttpClient, private val geminiService: GeminiService) {

    private val api = TradingApiService(client)
    private val smcExecutor = SmcToolExecutor(client)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Execute tool call และ return ผลลัพธ์เป็น String
     */
    suspend fun execute(toolName: String, args: Map<String, String>): String {
        return when (toolName) {
            "trading_price"            -> executePrice(args)
            "trading_market_snapshot"  -> executeMarketSnapshot(args)
            "trading_top_gainers"      -> executeTopGainers(args)
            "trading_top_losers"       -> executeTopLosers(args)
            "trading_technical_analysis" -> executeTechnicalAnalysis(args)
            "trading_multi_timeframe"  -> executeMultiTimeframe(args)
            "trading_bollinger_scan"   -> executeBollingerScan(args)
            "trading_oversold_scan"    -> executeOversoldScan(args)
            "trading_overbought_scan"  -> executeOverboughtScan(args)
            "trading_volume_breakout"  -> executeVolumeBreakout(args)
            "trading_sentiment"        -> executeSentiment(args)
            "trading_news"             -> executeNews(args)
            "trading_macro_calendar"   -> executeMacroCalendar(args)
            "trading_combined"         -> executeCombined(args)
            "trading_harmonic_scan"    -> executeHarmonicScan(args)
            "trading_elliot_modern_analysis" -> executeElliotWave(args)
            "trading_mt5_order"        -> executeMt5Order(args)
            "trading_mt5_close_position" -> executeMt5ClosePosition(args)
            "trading_mt5_modify_position" -> executeMt5ModifyPosition(args)
            // ─── MT5 Core Agent Tools ──────────────────────────────────────────
            "trading_mt5_account_info"    -> executeMt5AccountInfo(args)
            "trading_mt5_list_positions"  -> executeMt5ListPositions(args)
            "trading_mt5_list_orders"     -> executeMt5ListOrders(args)
            "trading_mt5_list_history"    -> executeMt5ListHistory(args)
            "trading_mt5_candles"         -> executeMt5Candles(args)
            "trading_mt5_analyze"         -> executeMt5Analyze(args)
            "trading_mt5_symbol_info"     -> executeMt5SymbolInfo(args)
            "trading_mt5_symbol_search"   -> executeMt5SymbolSearch(args)
            "trading_mt5_close_all"       -> executeMt5CloseAll(args)
            "trading_mt5_break_even_all"  -> executeMt5BreakEvenAll(args)
            "trading_mt5_snapshot"        -> executeMt5Snapshot(args)
            "trading_mt5_trade_actions"   -> executeMt5TradeActions(args)
            // ─── MT5 Advanced Intelligence ─────────────────────────────────────
            "trading_mt5_market_scanner"     -> executeMt5MarketScanner(args)
            "trading_mt5_correlation_radar"  -> executeMt5CorrelationRadar(args)
            "trading_mt5_sentiment_gauge"    -> executeMt5SentimentGauge(args)
            "trading_mt5_institutional_flow" -> executeMt5InstitutionalFlow(args)
            "trading_mt5_economic_radar"     -> executeMt5EconomicRadar(args)
            "trading_mt5_trade_journal"      -> executeMt5TradeJournal(args)
            "trading_deep_analysis_suite"    -> executeDeepAnalysisSuite(args)
            "trading_fundamental_analysis"   -> executeFundamentalAnalysis(args)
            "trading_fear_greed"             -> executeFearGreed(args)
            // ─── SMC (Smart Money Concepts) Tools ──────────────────────────────
            "trading_smc_analysis",
            "trading_smc_sweeps",
            "trading_smc_liquidity",
            "trading_smc_orderblocks",
            "trading_smc_structure"    -> smcExecutor.execute(toolName, args)
            else -> "Unknown trading tool: $toolName"
        }
    }

    // ─── Implementations ──────────────────────────────────────────────────────

    private suspend fun executeDeepAnalysisSuite(args: Map<String, String>): String {
        val symbol = args["symbol"] ?: return "Missing symbol"
        val tf = args["timeframe"] ?: "H1"
        val endpoint = args["endpoint"]?.trim()
            ?: "http://127.0.0.1:8090/api/mt5/auto/deep-analysis"
        val finalUrl = if (endpoint.contains("?")) "$endpoint&symbol=$symbol&timeframe=$tf"
                      else "$endpoint?symbol=$symbol&timeframe=$tf"
        val token = args["token"]?.trim().orEmpty()

        return try {
            val response = client.get(finalUrl) {
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Client-Token", token)
                }
            }
            val body = response.bodyAsText()
            "═══ Deep Analysis Suite: $symbol $tf ═══\n$body"
        } catch (e: Exception) {
            "Deep analysis error: ${e.message}"
        }
    }

    private suspend fun executeFundamentalAnalysis(args: Map<String, String>): String {
        val symbol = args["symbol"]?.trim()
        val limit = args["limit"]?.toIntOrNull() ?: 10

        return try {
            val data = api.getFinancialNews(symbol, limit) as? Map<String, Any> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val items = data["news"] as? List<Map<String, String>> ?: emptyList()

            if (items.isEmpty()) return "No fundamental data found for ${symbol ?: "global market"}."

            val newsText = items.joinToString("\n") { "- ${it["title"]}: ${it["description"]}" }
            val prompt = """
                Analyze the following financial and fundamental news for ${symbol ?: "the global market"}.
                1. Identify the core macroeconomic or fundamental drivers.
                2. Evaluate the impact on long-term valuation vs. short-term sentiment.
                3. Provide a 'Fundamental Health Score' (0-100).
                4. Summary of Bullish/Bearish fundamental factors.

                Data:
                $newsText
            """.trimIndent()

            val aiAnalysis = try {
                geminiService.generateResponse(
                    prompt = prompt,
                    history = emptyList(),
                    intentAddon = "You are a senior fundamental analyst. Focus on structural drivers, not just technical noise."
                )
            } catch (_: Exception) { "AI Synthesis unavailable." }

            buildString {
                appendLine("🏛️ **Fundamental Analysis - ${symbol?.uppercase() ?: "Global"}**")
                appendLine("=".repeat(40))
                appendLine(aiAnalysis)
                appendLine()
                appendLine("🔍 **Source Data Context:**")
                items.take(3).forEach { item ->
                    appendLine("- ${item["title"]}")
                }
            }
        } catch (e: Exception) {
            "Fundamental analysis error: ${e.message}"
        }
    }

    private suspend fun executeFearGreed(args: Map<String, String>): String {
        // 🔄 Smart Base URL Detection
        val baseUrl = args["endpoint"]?.trim()
            ?: args["server_url"]?.trim()?.removeSuffix("/")?.let { "$it/api/mt5/auto" }
            ?: "http://127.0.0.1:8090/api/mt5/auto"

        val token = args["token"]?.trim().orEmpty()

        return try {
            logDebug("TradingTool", "FearGreed fetching from: $baseUrl")

            // Fetch both Performance and Quality Metrics
            val analyticsUrl = if (baseUrl.endsWith("/analytics")) baseUrl else "$baseUrl/analytics"
            val qualityUrl   = if (baseUrl.endsWith("/metrics/quality")) baseUrl else "$baseUrl/metrics/quality"

            // ใช้ Response เป็น String เปล่าถ้าดึงไม่ได้ แทนที่จะปล่อยให้ Exception หลุด
            val analyticsBody = try {
                client.get(analyticsUrl) {
                    if (token.isNotBlank()) {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        header("X-Client-Token", token)
                    }
                }.bodyAsText()
            } catch (e: Exception) {
                logDebug("TradingTool", "Analytics fetch failed: ${e.message}")
                "{}"
            }

            val qualityBody = try {
                client.get(qualityUrl) {
                    if (token.isNotBlank()) {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        header("X-Client-Token", token)
                    }
                }.bodyAsText()
            } catch (e: Exception) {
                logDebug("TradingTool", "Quality metrics fetch failed: ${e.message}")
                "{}"
            }

            if (analyticsBody == "{}" && qualityBody == "{}") {
                return "⚠️ ระบบไม่สามารถดึงข้อมูล Analytics จาก Server ได้ในขณะนี้ ($baseUrl)"
            }

            val prompt = """
                Analyze the following internal system metrics to determine the Market Fear & Greed status.

                Performance Analytics:
                $analyticsBody

                System Quality & Rejection Metrics:
                $qualityBody

                Output Specification:
                1. Calculated Fear & Greed Index (0-100).
                2. Market Sentiment Label (Extremely Fearful to Extremely Greedy).
                3. Analysis of 'System Stress' (Are trades being rejected or blocked frequently?).
                4. Strategic recommendation based on current sentiment and system health.

                Please respond in Thai.
            """.trimIndent()

            val aiAnalysis = try {
                geminiService.generateResponse(
                    prompt = prompt,
                    history = emptyList(),
                    intentAddon = "You are a market psychologist. Analyze trade data and system behaviors to gauge the underlying fear/greed."
                )
            } catch (e: Exception) { "AI Synthesis failed: ${e.message}" }

            "🌡️ **Market Fear & Greed Index (System Analysis)**\n" + "=".repeat(45) + "\n" + aiAnalysis
        } catch (e: Exception) {
            "Sentiment analytics error: ${e.message}"
        }
    }


    private suspend fun executePrice(args: Map<String, String>): String {
        val symbol = args["symbol"] ?: return "Missing required argument: symbol"
        val data = api.getBestEffortPrice(symbol)

        if (data.containsKey("error")) return "Price fetch failed: ${data["error"]}"

        return buildString {
            appendLine("**${symbol.uppercase()}** - Real-time Price")
            appendLine("Price: ${data["price"]} ${data["currency"]}")
            appendLine("Change: ${data["direction"]} ${data["change"]} (${data["change_pct"]})")
            appendLine("Prev Close: ${data["prev_close"]}")
            appendLine("52W High: ${data["high_52w"]} | Low: ${data["low_52w"]}")
            appendLine("Market: ${data["market_state"]}")
            data["source"]?.let { appendLine("Source: $it") }
        }
    }

    private suspend fun executeMarketSnapshot(args: Map<String, String>): String {
        val sector = args["sector"]
        val market = args["market"] ?: "US"
        val limit  = args["limit"]?.toIntOrNull() ?: 10

        val resolvedAliases = if (!sector.isNullOrBlank()) {
            val resolved = api.resolveSectorsWithAI(geminiService, sector, market)
            logDebug("TradingTool", "Resolved sector '$sector' ($market) -> $resolved")
            resolved
        } else null

        val result = api.getMarketSnapshot(sector, market, limit, resolvedAliases)

        return buildString {
            if (result is Map<*, *>) {
                // Yahoo Finance Snapshot (Global Indices)
                appendLine("🌍 **Global Market Snapshot**")
                appendLine("=".repeat(40))
                @Suppress("UNCHECKED_CAST")
                val snapshot = result as Map<String, Map<String, String>>
                snapshot.forEach { (name, data) ->
                    if (!data.containsKey("error")) {
                        val dir = data["direction"] ?: ""
                        val price = data["price"] ?: "N/A"
                        val pct = data["change_pct"] ?: "N/A"
                        appendLine("$dir **$name**: $price ($pct)")
                    }
                }
            } else if (result is List<*>) {
                // TradingView Sector Snapshot
                val title = buildString {
                    append("📊 **Market Snapshot")
                    if (!sector.isNullOrBlank()) append(" — กลุ่มหุ้น $sector")
                    if (!market.isNullOrBlank()) append(" ($market)")
                    append("**")
                }
                @Suppress("UNCHECKED_CAST")
                val list = result as List<Map<String, String>>
                append(formatScanResults(title, list))
            }
        }
    }

    private suspend fun executeTopGainers(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getTopGainers(exchange, limit.coerceAtMost(50))
        return formatScanResults("🚀 Top Gainers — $exchange", results)
    }

    private suspend fun executeTopLosers(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getTopLosers(exchange, limit.coerceAtMost(50))
        return formatScanResults("📉 Top Losers — $exchange", results)
    }

    private suspend fun executeTechnicalAnalysis(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "Missing required argument: symbol"
        val resolvedExchange = api.resolveExchange(symbol, args["exchange"])
        val interval = args["interval"] ?: "1h"
        val data = api.getTechnicalAnalysis(symbol, resolvedExchange, interval)

        if (data.containsKey("error")) return "TA error: ${data["error"]}"

        val signal = data["signal"] ?: "N/A"
        val score  = data["recommend_score"] ?: "N/A"

        val signalEmoji = when (signal) {
            "STRONG BUY"  -> "++"
            "BUY"         -> "+"
            "HOLD"        -> "="
            "SELL"        -> "-"
            "STRONG SELL" -> "--"
            else          -> "?"
        }

        return buildString {
            appendLine("Technical Analysis - ${symbol.uppercase()} ($interval)")
            appendLine("Signal: $signalEmoji $signal (score: $score)")
            appendLine("-".repeat(35))
            appendLine("Price: ${data["close"]} | Change: ${data["change"]}%")
            appendLine("")
            appendLine("Momentum")
            appendLine("  RSI: ${data["RSI"]} (prev: ${data["RSI[1]"]})")
            appendLine("  MACD: ${data["MACD.macd"]} | Signal: ${data["MACD.signal"]} | Hist: ${data["MACD.hist"]}")
            appendLine("  Stoch K/D: ${data["Stoch.K"]} / ${data["Stoch.D"]}")
            appendLine("  CCI: ${data["CCI20"]} | AO: ${data["AO"]}")
            appendLine("")
            appendLine("Trend")
            appendLine("  EMA20: ${data["EMA20"]} | EMA50: ${data["EMA50"]} | EMA200: ${data["EMA200"]}")
            appendLine("  ADX: ${data["ADX"]} (+DI: ${data["ADX+DI"]} / -DI: ${data["ADX-DI"]})")
            appendLine("")
            appendLine("Volatility")
            appendLine("  BB Upper: ${data["BB.upper"]}")
            appendLine("  BB Basis: ${data["BB.basis"]}")
            appendLine("  BB Lower: ${data["BB.lower"]}")
            appendLine("  BB Width: ${data["BB.width"]} | ATR: ${data["ATR"]}")
            appendLine("")
            appendLine("Summary - Buy: ${data["buy_signals"]} | Sell: ${data["sell_signals"]} | Neutral: ${data["neutral_signals"]}")
        }
    }

    private suspend fun executeMultiTimeframe(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "Missing required argument: symbol"
        val resolvedExchange = api.resolveExchange(symbol, args["exchange"])
        val data = api.getMultiTimeframeAnalysis(symbol, resolvedExchange)

        return buildString {
            appendLine("Multi-Timeframe Analysis - ${symbol.uppercase()}")
            appendLine("-".repeat(40))

            val signals = mutableListOf<String>()
            for (entry in data) {
                val tf = entry.key
                val taData = entry.value
                val signal = taData["signal"] ?: "N/A"
                val rsi    = taData["RSI"]    ?: "N/A"
                val score  = taData["recommend_score"] ?: "N/A"
                val emoji  = signalEmoji(signal)
                appendLine("$emoji $tf: $signal | RSI: $rsi | Score: $score")
                signals.add(signal)
            }

            appendLine("")
            // Alignment check
            val buyCount  = signals.count { it.contains("BUY") }
            val sellCount = signals.count { it.contains("SELL") }
            val alignment = when {
                buyCount >= 4  -> "Strong alignment (BUY)"
                sellCount >= 4 -> "Strong alignment (SELL)"
                buyCount > sellCount -> "Mostly bullish"
                sellCount > buyCount -> "Mostly bearish"
                else -> "Mixed signals"
            }
            appendLine("Alignment: $alignment")
        }
    }

    private suspend fun executeBollingerScan(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 30
        val results = api.getBollingerSqueeze(exchange, limit)
        return formatScanResults("🔥 Bollinger Squeeze — $exchange (กำลัง Breakout)", results, extraKey = "BB.width")
    }

    private suspend fun executeOversoldScan(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getOversoldSymbols(exchange, limit)
        return formatScanResults("🟢 Oversold Scan (RSI < 30) — $exchange", results, extraKey = "RSI")
    }

    private suspend fun executeOverboughtScan(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getOverboughtSymbols(exchange, limit)
        return formatScanResults("🔴 Overbought Scan (RSI > 70) — $exchange", results, extraKey = "RSI")
    }

    private suspend fun executeVolumeBreakout(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getVolumeBreakout(exchange, limit)
        return formatScanResults("🌋 Volume Breakout — $exchange", results, extraKey = "volume")
    }

    private suspend fun executeSentiment(args: Map<String, String>): String {
        val symbol = args["symbol"] ?: return "Missing required argument: symbol"
        val data = api.getRedditSentiment(symbol)

        @Suppress("UNCHECKED_CAST")
        val topPosts = data["top_posts"] as? List<String> ?: emptyList()

        return buildString {
            appendLine("Reddit Sentiment - ${symbol.uppercase()}")
            appendLine("-".repeat(35))
            appendLine("Label: ${data["sentiment_label"]}")
            appendLine("Score: ${data["sentiment_score"]}")
            appendLine("Posts analyzed: ${data["posts_analyzed"]}")
            appendLine("Bullish posts: ${data["bullish_posts"]} | Bearish: ${data["bearish_posts"]}")
            if (topPosts.isNotEmpty()) {
                appendLine("")
                appendLine("Top Posts:")
                topPosts.forEachIndexed { i, post -> appendLine("${i+1}. $post") }
            }
        }
    }

    private suspend fun executeNews(args: Map<String, String>): String {
        var symbol = args["symbol"]?.trim()
        val limit  = args["limit"]?.toIntOrNull() ?: 5

        // 🔄 Smart Fallback for Gold
        if (symbol?.uppercase() == "XAUUSD" || symbol?.lowercase() == "gold") {
            symbol = "Gold"
        }

        var data = api.getFinancialNews(symbol, limit) as? Map<String, Any> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        var items = data["news"] as? List<Map<String, String>> ?: emptyList()

        // 🛡️ Double Fallback: หากยังไม่พบ ให้ดึงข่าวตลาดรวม
        if (items.isEmpty() && symbol != null) {
            logDebug("TradingTool", "No news for $symbol, falling back to Global Macro")
            data = api.getFinancialNews(null, limit) as? Map<String, Any> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            items = data["news"] as? List<Map<String, String>> ?: emptyList()
        }

        if (items.isEmpty()) return "No relevant financial news found at this moment."

        // 🧠 AI Synthesis (Premium Analysis)
        val newsText = items.joinToString("\n") { "- ${it["title"]}: ${it["description"]}" }
        val prompt = """
            Analyze the following financial news for ${symbol ?: "the market"}.
            1. Provide a Sentiment Bias Score (-10 to 10).
            2. Summarize key risks or opportunities.
            3. Recommend if this requires immediate action or tracking.

            News:
            $newsText
        """.trimIndent()

        val aiAnalysis = try {
            geminiService.generateResponse(
                prompt = prompt,
                history = emptyList(),
                intentAddon = "You are a professional news analyst. Be concise and objective."
            )
        } catch (_: Exception) { "AI analysis unavailable." }

        return buildString {
            appendLine("🗞️ **News Technical Analysis - ${symbol?.uppercase() ?: "Global"}**")
            appendLine("-".repeat(40))
            items.forEachIndexed { i, item ->
                appendLine("${i+1}. **${item["title"]}** (${item["source"]})")
                appendLine("   > ${item["description"]}")
            }
            appendLine("")
            appendLine("🧠 **JARVIS Intelligence Summary**")
            appendLine(aiAnalysis)
        }
    }

    private suspend fun executeMacroCalendar(args: Map<String, String>): String {
        val limit = args["limit"]?.toIntOrNull() ?: 10
        val events = api.getEconomicCalendar(limit)

        if (events.isEmpty()) return "No major macro events found for this week."

        val eventsText = events.joinToString("\n") {
            "[${it["impact"]}] ${it["country"]}: ${it["title"]} (${it["date_time"]}) -> Act: ${it["actual"]}, Cons: ${it["forecast"]}, Prev: ${it["previous"]}"
        }

        val aiAnalysis = try {
            geminiService.generateResponse(
                prompt = "Analyze these economic events and tell me which ones are critical for trading: \n$eventsText",
                history = emptyList(),
                intentAddon = "You are a macro economist. Highlight the Red (High Impact) events and their likely effect on USD or Gold. If Actual data exists, analyze the deviation from Consensus."
            )
        } catch (_: Exception) { "Analysis unavailable." }

        return buildString {
            appendLine("📅 **Economic Calendar (FXStreet Insights)**")
            appendLine("-".repeat(40))
            events.forEach { e ->
                val emoji = when(e["impact"]?.lowercase()) {
                    "high" -> "🔴"
                    "medium" -> "🟠"
                    else -> "🟡"
                }
                appendLine("$emoji **${e["title"]}** (${e["country"]})")
                appendLine("   | Impact: ${e["impact"]} | Time: ${e["date_time"]}")
                appendLine("   | Actual: ${e["actual"]} | Forecast: ${e["forecast"]} | Prev: ${e["previous"]}")
            }
            appendLine("")
            appendLine("🔦 **AI Strategic Preview**")
            appendLine(aiAnalysis)
        }
    }

    private suspend fun executeHarmonicScan(args: Map<String, String>): String {
        val symbol = args["symbol"] ?: return "Missing symbol"
        val tf = args["interval"] ?: "1h"
        val result = api.getModernTechnicalAnalysis(symbol, tf) ?: return "Insufficient data for Harmonic scan."

        return buildString {
            appendLine("📐 **Harmonic Pattern Scan - ${symbol.uppercase()} ($tf)**")
            appendLine("-".repeat(40))
            if (result.harmonicPatterns.isEmpty()) {
                appendLine("No high-probability Harmonic patterns detected currently.")
            } else {
                result.harmonicPatterns.forEach { p ->
                    val stars = "⭐".repeat(p.score)
                    appendLine("Pattern: **${p.type}** (${p.direction}) $stars")
                    appendLine("   PRZ: ${"%.4f".format(p.przBottom)} - ${"%.4f".format(p.przTop)}")
                    appendLine("   Confluence Score: ${p.score}/5")
                }
            }
        }
    }

    private suspend fun executeElliotWave(args: Map<String, String>): String {
        val symbol = args["symbol"] ?: return "Missing symbol"
        val tf = args["interval"] ?: "1h"
        val result = api.getModernTechnicalAnalysis(symbol, tf) ?: return "Insufficient data."

        val elliot = result.elliotWave
        return buildString {
            appendLine("🌊 **Modern Elliot Wave Insight - ${symbol.uppercase()} ($tf)**")
            appendLine("-".repeat(40))
            appendLine("Market Stage: **${elliot.stage}**")
            appendLine("Momentum Bias: **${elliot.momentumBias}**")
            appendLine("Confidence: ${"%.0f".format(elliot.confidence * 100)}%")
            appendLine("Impulse Score: ${elliot.impulseScore}/100")
            appendLine("")
            appendLine("Reasoning: ${elliot.reasoning}")
        }
    }

    private suspend fun executeCombined(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "Missing required argument: symbol"
        val exchange = args["exchange"] ?: "BINANCE"
        val interval = args["interval"] ?: "1h"

        // เรียกทั้ง 3 แบบพร้อมกันผ่าน coroutine
        val ta        = api.getTechnicalAnalysis(symbol, exchange, interval)
        val sentiment = api.getRedditSentiment(symbol.removeSuffix("USDT"))
        val news      = api.getFinancialNews(symbol.removeSuffix("USDT"), 3) as? Map<String, Any> ?: emptyMap()

        val taSignal   = ta["signal"]?.toString() ?: "N/A"
        val sentLabel  = sentiment["sentiment_label"]?.toString() ?: "N/A"
        val sentScore  = sentiment["sentiment_score"]?.toString()?.toDoubleOrNull() ?: 0.0

        // Confluence logic
        val taBullish  = taSignal.contains("BUY")
        val sentBullish = sentScore > 0.1
        val confluenceMatch = taBullish == sentBullish

        val finalCall = when {
            taBullish && sentBullish && confluenceMatch -> "STRONG BUY"
            taBullish && confluenceMatch -> "BUY"
            !taBullish && !sentBullish   -> "SELL"
            else                          -> "MIXED"
        }

        @Suppress("UNCHECKED_CAST")
        val newsItems = news["news"] as? List<Map<String, String>> ?: emptyList()

        return buildString {
            appendLine("Combined Analysis - ${symbol.uppercase()} ($interval)")
            appendLine("=".repeat(29))
            appendLine("")
            appendLine("Technical: $taSignal (score: ${ta["recommend_score"]})")
            appendLine("  RSI: ${ta["RSI"]} | MACD hist: ${ta["MACD.hist"]}")
            appendLine("  EMA20: ${ta["EMA20"]} vs EMA50: ${ta["EMA50"]}")
            appendLine("")
            appendLine("Sentiment: $sentLabel (${sentiment["sentiment_score"]})")
            appendLine("  ${sentiment["posts_analyzed"]} posts - Bull: ${sentiment["bullish_posts"]} Bear: ${sentiment["bearish_posts"]}")
            appendLine("")
            appendLine("Latest News:")
            newsItems.take(3).forEach { item ->
                appendLine("  - ${item["title"]}")
            }
            appendLine("")
            appendLine("-".repeat(29))
            appendLine("Confluence Decision: $finalCall")
            appendLine(
                if (confluenceMatch) "TA and Sentiment are aligned."
                else "TA and Sentiment conflict; wait for confirmation."
            )
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────


    private fun signalEmoji(signal: String) = when (signal) {
        "STRONG BUY"  -> "++"
        "BUY"         -> "+"
        "HOLD"        -> "="
        "SELL"        -> "-"
        "STRONG SELL" -> "--"
        else          -> "?"
    }

    private fun formatScanResults(
        title: String,
        results: List<Map<String, String>>,
        extraKey: String? = null
    ): String = buildString {
        appendLine(title)
        appendLine("-".repeat(title.length.coerceAtMost(40)))
        if (results.isEmpty()) {
            appendLine("No data.")
            return@buildString
        }
        results.forEachIndexed { i, row ->
            val symbol = row["symbol"] ?: row["name"] ?: "?"
            val change = row["change_pct"] ?: row["change"] ?: ""
            val price  = row["price"] ?: row["close"] ?: ""
            val extra  = if (extraKey != null) row[extraKey]?.let { " | $extraKey: $it" } ?: "" else ""
            appendLine("${i + 1}. $symbol - $price $change$extra")
        }
    }

    private suspend fun executeMt5Order(args: Map<String, String>): String {
        val action = args["action"]?.uppercase() ?: return "Missing required argument: action"
        if (action != "BUY" && action != "SELL") return "Invalid action: $action (allowed: BUY, SELL)"

        val symbol = args["symbol"]?.trim().orEmpty()
        if (symbol.isBlank()) return "Missing required argument: symbol"

        val volume = args["volume"]?.toDoubleOrNull()
            ?: return "Missing or invalid required argument: volume"
        if (volume <= 0.0) return "Invalid volume: must be > 0"

        // Default endpoint matches the mt5-core-server at :8090. When invoked
        // from the app the JarvisViewModel / ToolExecutor rewrites this with
        // the user's configured bridgeBaseUrl; the literal here is only a
        // fallback for local cup testing.
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/order"
        val token = args["token"]?.trim().orEmpty()

        val sl = args["sl"]?.toDoubleOrNull()
        val tp = args["tp"]?.toDoubleOrNull()
        val comment = args["comment"]?.trim().orEmpty()

        return try {
            val payload = buildJsonObject {
                put("action", action)
                put("symbol", symbol)
                put("volume", JsonPrimitive(volume))
                put("sl", sl?.let { JsonPrimitive(it) } ?: JsonNull)
                put("tp", tp?.let { JsonPrimitive(it) } ?: JsonNull)
                put("comment", comment)
            }
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            val ok = response.status.value in 200..299
            if (ok) {
                "MT5 order sent successfully ($action $symbol $volume)\nStatus: ${response.status.value}\nResponse: $body"
            } else {
                "MT5 order failed ($action $symbol $volume)\nStatus: ${response.status.value}\nResponse: $body"
            }
        } catch (e: Exception) {
            "MT5 order request error: ${e.message}"
        }
    }

    private suspend fun executeMt5ClosePosition(args: Map<String, String>): String {
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/close"
        val token = args["token"]?.trim().orEmpty()
        val symbol = args["symbol"]?.trim().orEmpty()
        val ticket = args["ticket"]?.trim().orEmpty()

        if (symbol.isBlank() && ticket.isBlank()) {
            return "Missing required argument: provide at least one of symbol or ticket"
        }

        return try {
            val payload = buildJsonObject {
                put("symbol", symbol.ifBlank { "" })
                put("ticket", ticket.ifBlank { "" })
            }
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            val ok = response.status.value in 200..299
            if (ok) {
                "MT5 close command sent successfully\nStatus: ${response.status.value}\nResponse: $body"
            } else {
                "MT5 close command failed\nStatus: ${response.status.value}\nResponse: $body"
            }
        } catch (e: Exception) {
            "MT5 close request error: ${e.message}"
        }
    }
    private suspend fun executeMt5ModifyPosition(args: Map<String, String>): String {
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/modify"
        val token = args["token"]?.trim().orEmpty()
        val symbol = args["symbol"]?.trim().orEmpty()
        val ticket = args["ticket"]?.trim().orEmpty()
        val sl = args["sl"]?.trim()?.toDoubleOrNull()
        val tp = args["tp"]?.trim()?.toDoubleOrNull()
        val comment = args["comment"]?.trim().orEmpty()

        if (symbol.isBlank() && ticket.isBlank()) {
            return "Missing required argument: provide at least one of symbol or ticket"
        }
        if (sl == null && tp == null) {
            return "Missing required argument: provide at least one of sl or tp"
        }

        return try {
            val payload = buildJsonObject {
                if (symbol.isNotBlank()) put("symbol", symbol)
                if (ticket.isNotBlank()) put("ticket", ticket)
                if (sl != null) put("sl", JsonPrimitive(sl))
                if (tp != null) put("tp", JsonPrimitive(tp))
                if (comment.isNotBlank()) put("comment", comment)
            }
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            val ok = response.status.value in 200..299
            if (ok) {
                "MT5 modify sent successfully (symbol=$symbol ticket=$ticket sl=$sl tp=$tp)\nStatus: ${response.status.value}\nResponse: $body"
            } else {
                "MT5 modify failed (symbol=$symbol ticket=$ticket)\nStatus: ${response.status.value}\nResponse: $body"
            }
        } catch (e: Exception) {
            "MT5 modify request error: ${e.message}"
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ██  MT5 CORE AGENT TOOLS — ดึงข้อมูลจาก MT5 Broker โดยตรง  ██
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Helper: HTTP GET to MT5 bridge endpoint, returns raw body text.
     * IMPORTANT: Always include the bridge client token if provided, otherwise
     * the Node core server replies with 401 "Client token required".
     */
    private suspend fun mt5Get(endpoint: String, token: String = ""): String {
        return try {
            val response = client.get(endpoint) {
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Client-Token", token)
                }
            }
            response.bodyAsText()
        } catch (e: Exception) {
            throw Exception("MT5 bridge connection failed: ${e.message}")
        }
    }

    /** Convenience overload that extracts the token from args map */
    private suspend fun mt5GetWithArgs(endpoint: String, args: Map<String, String>): String =
        mt5Get(endpoint, args["token"]?.trim().orEmpty())

    private fun formatMt5CandlesResponse(symbol: String, timeframe: String, requestedCount: Int, body: String): String {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val outerSuccess = root.booleanField("success") ?: true
            // Server returns { success, data: [...] } (flat array) — but the bridge may
            // also wrap it as { success, data: { success, data: [...] } } (double-wrap).
            // Handle both so we never get an empty parse from a valid response.
            val candles: JsonArray = when (val dataEl = root["data"]) {
                is JsonArray  -> dataEl                                        // single-wrap ✅
                is JsonObject -> dataEl["data"] as? JsonArray ?: JsonArray(emptyList()) // double-wrap
                else          -> JsonArray(emptyList())
            }
            val innerSuccess = outerSuccess  // success flag fully determined by outerSuccess

            when {
                !outerSuccess || !innerSuccess ->
                    "MT5 Candles ($symbol $timeframe) returned an unsuccessful response from broker."

                candles.isEmpty() ->
                    "MT5 Candles ($symbol $timeframe) returned no candle data from broker."

                else -> {
                    val first = candles.first().jsonObject
                    val last = candles.last().jsonObject
                    val highs = candles.mapNotNull { it.jsonObject.doubleField("h") }
                    val lows = candles.mapNotNull { it.jsonObject.doubleField("l") }
                    val volumes = candles.mapNotNull { it.jsonObject.doubleField("v") }
                    val firstClose = first.doubleField("c")
                    val lastClose = last.doubleField("c")
                    val netChange = if (firstClose != null && lastClose != null) lastClose - firstClose else null
                    val netChangePct = if (firstClose != null && lastClose != null && firstClose != 0.0) {
                        ((lastClose - firstClose) / firstClose) * 100.0
                    } else null

                    buildString {
                        appendLine("MT5 Candles ($symbol $timeframe, ${candles.size} bars) from broker")
                        appendLine("Latest candle:")
                        appendLine("O=${last.stringField("o")} H=${last.stringField("h")} L=${last.stringField("l")} C=${last.stringField("c")} V=${last.stringField("v")}")
                        appendLine("Range summary:")
                        appendLine("High=${highs.maxOrNull()?.format4() ?: "N/A"} Low=${lows.minOrNull()?.format4() ?: "N/A"} AvgVolume=${volumes.averageOrNull()?.format0() ?: "N/A"}")
                        if (netChange != null && netChangePct != null) {
                            appendLine("Net change over ${candles.size} bars: ${netChange.format4()} (${netChangePct.format2()}%)")
                        }
                        appendLine("Requested bars: $requestedCount")
                        append("Structured broker payload kept out of visible chat to avoid dumping raw JSON.")
                    }
                }
            }
        } catch (_: Exception) {
            "MT5 Candles ($symbol $timeframe, $requestedCount bars) from broker.\nStructured payload received, but raw JSON was hidden from chat."
        }
    }

    private fun JsonObject.booleanField(key: String): Boolean? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.toBooleanStrictOrNull()

    private fun JsonObject.doubleField(key: String): Double? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.toDoubleOrNull()

    private fun JsonObject.stringField(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull() ?: "N/A"

    private fun Double.format4(): String = "%.4f".format(this)

    private fun Double.format2(): String = "%.2f".format(this)

    private fun Double.format0(): String = "%.0f".format(this)

    private fun Iterable<Double>.averageOrNull(): Double? {
        val values = toList()
        return if (values.isEmpty()) null else values.average()
    }

    private suspend fun executeMt5AccountInfo(args: Map<String, String>): String {
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/account"
        return try {
            val body = mt5GetWithArgs(endpoint, args)
            "MT5 Account Info (from broker):\n$body"
        } catch (e: Exception) {
            "MT5 account info error: ${e.message}"
        }
    }

    private suspend fun executeMt5ListPositions(args: Map<String, String>): String {
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/positions"
        val symbol = args["symbol"]?.trim().orEmpty()
        val endpoint = if (symbol.isNotBlank()) "$base?symbol=$symbol" else base
        return try {
            val body = mt5GetWithArgs(endpoint, args)
            "MT5 Open Positions${if (symbol.isNotBlank()) " ($symbol)" else " (all)"}:\n$body"
        } catch (e: Exception) {
            "MT5 list positions error: ${e.message}"
        }
    }

    private suspend fun executeMt5ListOrders(args: Map<String, String>): String {
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/orders"
        val symbol = args["symbol"]?.trim().orEmpty()
        val endpoint = if (symbol.isNotBlank()) "$base?symbol=$symbol" else base
        return try {
            val body = mt5GetWithArgs(endpoint, args)
            "MT5 Pending Orders${if (symbol.isNotBlank()) " ($symbol)" else " (all)"}:\n$body"
        } catch (e: Exception) {
            "MT5 list orders error: ${e.message}"
        }
    }

    private suspend fun executeMt5ListHistory(args: Map<String, String>): String {
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/history"
        val symbol = args["symbol"]?.trim().orEmpty()
        val limit = args["limit"]?.toIntOrNull() ?: 100
        val params = mutableListOf<String>()
        if (symbol.isNotBlank()) params.add("symbol=$symbol")
        params.add("limit=$limit")
        val endpoint = "$base?${params.joinToString("&")}"
        return try {
            val body = mt5GetWithArgs(endpoint, args)
            "MT5 Trade History (last $limit deals):\n$body"
        } catch (e: Exception) {
            "MT5 list history error: ${e.message}"
        }
    }

    private suspend fun executeMt5Candles(args: Map<String, String>): String {
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/candles"
        val symbol = args["symbol"]?.trim() ?: return "Missing required argument: symbol"
        val timeframe = args["timeframe"]?.trim() ?: return "Missing required argument: timeframe"
        val count = args["count"]?.toIntOrNull() ?: 300
        val endpoint = "$base?symbol=$symbol&timeframe=$timeframe&count=$count"
        logDebug("MT5Candles", "Calling: $endpoint")
        return try {
            val body = mt5GetWithArgs(endpoint, args)
            formatMt5CandlesResponse(symbol, timeframe, count, body)
        } catch (e: Exception) {
            "MT5 candles error: ${e.message}"
        }
    }

    private suspend fun executeMt5Analyze(args: Map<String, String>): String {
        // enrichMt5Args already sets endpoint to "$base/analyze" — use it directly
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/analyze"
        val symbol    = args["symbol"]?.trim()    ?: return "Missing required argument: symbol"
        val timeframe = args["timeframe"]?.trim() ?: return "Missing required argument: timeframe"
        val count     = args["count"]?.toIntOrNull() ?: 180
        val endpoint  = "$base?symbol=$symbol&timeframe=$timeframe&count=$count"
        logDebug("MT5Analyze", "Calling: $endpoint")
        return try {
            val body = mt5GetWithArgs(endpoint, args)
            formatMt5AnalyzeResponse(symbol, timeframe, body)
        } catch (e: Exception) {
            "MT5 analyze error: ${e.message}"
        }
    }

    private fun formatMt5AnalyzeResponse(symbol: String, timeframe: String, body: String): String {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val ok   = root.booleanField("success") ?: true
            if (!ok) return "MT5 Analyze ($symbol $timeframe) returned an error."

            var dataEl = root["data"]
            // Unwrap double-wrapped data: { success, data: { success, data: { ... } } }
            if (dataEl is JsonObject && dataEl.containsKey("success") && dataEl.containsKey("data")) {
                dataEl = dataEl["data"]
            }

            val d = when (dataEl) {
                is JsonObject -> dataEl
                else          -> return "MT5 Analyze ($symbol $timeframe): unexpected response format."
            }

            val regime     = d.stringField("regime")
            val bias       = d.stringField("bias")
            val confluence = d.doubleField("confluence") ?: 0.0
            val fitness    = d.doubleField("fitness")    ?: 0.0
            val strategy   = d.stringField("strategy")
            val rationale  = d.stringField("rationale")
            val bars       = runCatching { d["barsAnalyzed"]?.jsonPrimitive?.content?.toIntOrNull() }.getOrNull() ?: 0

            val ind = d["indicators"] as? JsonObject
            val rsi    = ind?.doubleField("rsi14")
            val sma20  = ind?.doubleField("sma20")
            val sma50  = ind?.doubleField("sma50")
            val ema20  = ind?.doubleField("ema20")
            val macdH  = ind?.doubleField("macdHist")
            val atr    = ind?.doubleField("atr14")

            val price = d["price"] as? JsonObject
            val cur   = price?.doubleField("current")
            val chPct = price?.doubleField("changePct")
            val hi20  = price?.doubleField("high20")
            val lo20  = price?.doubleField("low20")
            val rng   = price?.doubleField("range20Pct")

            val signals = (d["signals"] as? JsonArray)?.take(6)?.joinToString("\n") { el ->
                val s = el.jsonObject
                "  • ${s.stringField("name")} [${s.stringField("bias")}] score=${s.doubleField("score")?.toInt()} — ${s.stringField("evidence")}"
            } ?: "  (no signals)"

            val recentBarsArr = d["recentBars"] as? JsonArray
            val barsTable = recentBarsArr?.takeLast(10)?.joinToString("\n") { el ->
                val b = el.jsonObject
                "  ${b.stringField("t")}  O=${b.stringField("o")} H=${b.stringField("h")} L=${b.stringField("l")} C=${b.stringField("c")} V=${b.stringField("v")}"
            } ?: ""

            buildString {
                appendLine("═══ MT5 Analysis: $symbol $timeframe ($bars bars) ═══")
                appendLine()
                appendLine("REGIME    : $regime")
                appendLine("BIAS      : $bias")
                appendLine("STRATEGY  : $strategy")
                appendLine("CONFLUENCE: ${confluence.format1()}%   FITNESS: ${fitness.format1()}%")
                appendLine()
                appendLine("── Price ────────────────────────────────")
                appendLine("Current : ${cur?.format4() ?: "N/A"}  (${if ((chPct ?: 0.0) >= 0) "+" else ""}${chPct?.format2() ?: "N/A"}%)")
                appendLine("20-bar  : High=${hi20?.format4() ?: "N/A"}  Low=${lo20?.format4() ?: "N/A"}  Range=${rng?.format2() ?: "N/A"}%")
                appendLine()
                appendLine("── Indicators ───────────────────────────")
                appendLine("RSI14    : ${rsi?.format1() ?: "N/A"}")
                appendLine("SMA20/50 : ${sma20?.format4() ?: "N/A"} / ${sma50?.format4() ?: "N/A"}")
                appendLine("EMA20    : ${ema20?.format4() ?: "N/A"}")
                appendLine("MACD Hist: ${macdH?.format4() ?: "N/A"}")
                appendLine("ATR14    : ${atr?.format4() ?: "N/A"}")
                appendLine()
                appendLine("── Top Signals ──────────────────────────")
                appendLine(signals)
                appendLine()
                
                val smcObj = dataEl["smc"]?.jsonObject
                if (smcObj != null && smcObj.isNotEmpty()) {
                    appendLine("── Smart Money Concepts (SMC) ───────────")
                    val fvgList = smcObj["fvg"]?.jsonArray
                    if (!fvgList.isNullOrEmpty()) {
                        val last = fvgList.last().jsonObject
                        val type = if (last["FVG"]?.jsonPrimitive?.doubleOrNull == 1.0) "BULLISH" else "BEARISH"
                        appendLine("FVG       : $type Gap [${last["Bottom"]?.jsonPrimitive?.content} - ${last["Top"]?.jsonPrimitive?.content}]")
                    }
                    val obList = smcObj["ob"]?.jsonArray
                    if (!obList.isNullOrEmpty()) {
                        val last = obList.last().jsonObject
                        val type = if (last["OB"]?.jsonPrimitive?.doubleOrNull == 1.0) "BULLISH" else "BEARISH"
                        appendLine("OrderBlock: $type OB [${last["Bottom"]?.jsonPrimitive?.content} - ${last["Top"]?.jsonPrimitive?.content}]")
                    }
                    val chochList = smcObj["choch"]?.jsonArray
                    if (!chochList.isNullOrEmpty()) {
                        val last = chochList.last().jsonObject
                        val type = if (last["CHOCH"]?.jsonPrimitive?.doubleOrNull == 1.0) "BULLISH" else "BEARISH"
                        appendLine("Structure : CHoCH ($type) Level: ${last["Level"]?.jsonPrimitive?.content}")
                    }
                    val liqList = smcObj["liquidity"]?.jsonArray
                    if (!liqList.isNullOrEmpty()) {
                        val last = liqList.last().jsonObject
                        val type = if (last["Liquidity"]?.jsonPrimitive?.doubleOrNull == 1.0) "BULLISH" else "BEARISH"
                        appendLine("Liquidity : Swept ($type) Level: ${last["Level"]?.jsonPrimitive?.content}")
                    }
                    appendLine()
                }

                appendLine("── Rationale ────────────────────────────")
                appendLine(rationale)
                if (barsTable.isNotBlank()) {
                    appendLine()
                    appendLine("── Last 10 Bars (HH:MM) ─────────────────")
                    append(barsTable)
                }
            }.trim()
        } catch (e: Exception) {
            "MT5 Analyze ($symbol $timeframe): received response but could not parse it. Detail: ${e.message}"
        }
    }

    private fun Double.format1(): String = "%.1f".format(this)

    private suspend fun executeMt5SymbolInfo(args: Map<String, String>): String {
        val base     = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?.let { if (it.endsWith("/symbols")) it else it.substringBeforeLast("/") + "/symbols" }
            ?: "http://127.0.0.1:8090/api/mt5/symbols"
        val symbol   = args["symbol"]?.trim()
        val endpoint = if (!symbol.isNullOrBlank()) "$base?symbol=$symbol" else base
        return try {
            val body = mt5GetWithArgs(endpoint, args)
            val root = json.parseToJsonElement(body).jsonObject

            // Unwrap double-wrapped data: { success, data: { success, data: [...] } }
            var dataEl = root["data"]
            if (dataEl is JsonObject && dataEl.containsKey("success") && dataEl.containsKey("data")) {
                dataEl = dataEl["data"]
            }

            if (!symbol.isNullOrBlank()) {
                // Find the requested symbol in the array or object
                val sym = when (dataEl) {
                    is JsonArray -> dataEl.firstOrNull { el ->
                        el.jsonObject.stringField("name")?.equals(symbol, ignoreCase = true) == true ||
                        el.jsonObject.stringField("symbol")?.equals(symbol, ignoreCase = true) == true
                    }?.jsonObject
                    is JsonObject -> if (
                        dataEl.stringField("name")?.equals(symbol, ignoreCase = true) == true ||
                        dataEl.stringField("symbol")?.equals(symbol, ignoreCase = true) == true
                    ) dataEl else null
                    else -> null
                }
                if (sym != null) {
                    buildString {
                        appendLine("MT5 Symbol Info: $symbol")
                        sym.entries.forEach { (k, v) ->
                            appendLine("  $k = ${v.jsonPrimitive.content}")
                        }
                    }.trimEnd()
                } else {
                    // fallback — symbol not found in response, show compact summary
                    "MT5 Symbol Info ($symbol): not found in broker response. Raw: ${body.take(500)}"
                }
            } else {
                // No specific symbol — return only symbol names to avoid dumping huge JSON
                val names = when (dataEl) {
                    is JsonArray -> dataEl.take(50).mapNotNull { el ->
                        val obj = el.jsonObject
                        obj.stringField("symbol") ?: obj.stringField("name") ?: obj.stringField("s")
                    }
                    else -> emptyList()
                }
                buildString {
                    appendLine("MT5 Available Symbols (${names.size} shown, use symbol= to get details):")
                    appendLine(names.joinToString(", "))
                }.trimEnd()
            }
        } catch (e: Exception) {
            "MT5 symbol info error: ${e.message}"
        }
    }

    // ── Symbol Search & Validate ─────────────────────────────────────────────
    private suspend fun executeMt5SymbolSearch(args: Map<String, String>): String {
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?.let { it.substringBeforeLast("/") + "/symbol-search" }
            ?: "http://127.0.0.1:8090/api/mt5/symbol-search"
        val q        = args["q"]?.trim()       ?: return "Missing required argument: q (search query)"
        val limit    = args["limit"]?.trim()   ?: "15"
        val validate = args["validate"]?.trim() ?: "false"
        val endpoint = "$base?q=${q.uppercase()}&limit=$limit&validate=$validate"

        return try {
            val body = mt5GetWithArgs(endpoint, args)
            val root = json.parseToJsonElement(body).jsonObject
            var dObj = root["data"] as? JsonObject ?: return "MT5 Symbol Search: unexpected response."

            // Unwrap double-wrapped data
            if (dObj.containsKey("success") && dObj.containsKey("data")) {
                dObj = dObj["data"] as? JsonObject ?: dObj
            }
            val d = dObj

            val exact     = d["exact"]?.jsonPrimitive?.content?.lowercase() == "true"
            val validated = d["validated"]?.jsonPrimitive?.content
            val suggested = d["suggested"]?.jsonPrimitive?.content
            val query     = d["query"]?.jsonPrimitive?.content ?: q.uppercase()
            val source    = d["source"]?.jsonPrimitive?.content ?: "unknown"
            val matches   = d["matches"] as? JsonArray ?: JsonArray(emptyList())

            buildString {
                appendLine("MT5 Symbol Search: \"$query\" (source=$source)")
                appendLine()
                if (exact) {
                    appendLine("EXACT MATCH: $validated  ← ใช้ชื่อนี้ใน orders/analysis")
                } else {
                    appendLine("NOT EXACT — โบรกไม่มีชื่อ \"$query\" ตรงๆ")
                    if (suggested != null) appendLine("SUGGESTED  : $suggested  ← ลองใช้ชื่อนี้แทน")
                }
                appendLine()
                if (matches.isEmpty()) {
                    appendLine("ไม่พบ symbol ที่ตรงกับคำค้นหา")
                } else {
                    appendLine("── ผลลัพธ์ ${matches.size} รายการ ──────────────────────────────")
                    matches.forEach { el ->
                        val m     = el.jsonObject
                        val sym   = m.stringField("symbol")  ?: "?"
                        val desc  = m.stringField("description") ?: ""
                        val path  = m.stringField("path")    ?: ""
                        val bid   = m.doubleField("bid")     ?: 0.0
                        val ask   = m.doubleField("ask")     ?: 0.0
                        val isEx  = m["exact"]?.jsonPrimitive?.content?.lowercase() == "true"
                        val tag   = if (isEx) " [EXACT]" else ""
                        val priceStr = if (bid > 0.0) "  bid=${bid.format4()} ask=${ask.format4()}" else ""
                        appendLine("  $sym$tag — $desc${if (path.isNotBlank()) " ($path)" else ""}$priceStr")
                    }
                }
            }.trimEnd()
        } catch (e: Exception) {
            "MT5 symbol search error: ${e.message}"
        }
    }

    private suspend fun executeMt5CloseAll(args: Map<String, String>): String {
        // Step 1: List positions first
        val listBase = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?.replace("/close", "/positions")
            ?: "http://127.0.0.1:8090/api/mt5/positions"
        val closeBase = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/close"
        val symbol = args["symbol"]?.trim().orEmpty()
        val token = args["token"]?.trim().orEmpty()

        return try {
            // Get positions list
            val listEndpoint = if (symbol.isNotBlank()) "$listBase?symbol=$symbol" else listBase
            val positionsBody = mt5Get(listEndpoint, token)

            // Parse positions to get tickets
            // Send close for each — using symbol-based close
            val payload = buildJsonObject {
                put("symbol", symbol.ifBlank { "" })
                put("ticket", "")
            }
            val response = client.post(closeBase) {
                contentType(ContentType.Application.Json)
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Client-Token", token)
                }
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            val ok = response.status.value in 200..299
            if (ok) {
                "MT5 Close All${if (symbol.isNotBlank()) " ($symbol)" else ""} sent successfully\nPositions before close:\n$positionsBody\nClose response: $body"
            } else {
                "MT5 Close All failed\nStatus: ${response.status.value}\nResponse: $body"
            }
        } catch (e: Exception) {
            "MT5 close all error: ${e.message}"
        }
    }

    private suspend fun executeMt5BreakEvenAll(args: Map<String, String>): String {
        // Step 1: Get positions
        val listBase = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?.replace("/modify", "/positions")
            ?: "http://127.0.0.1:8090/api/mt5/positions"
        val modifyBase = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/modify"
        val symbol = args["symbol"]?.trim().orEmpty()
        val token = args["token"]?.trim().orEmpty()

        return try {
            val listEndpoint = if (symbol.isNotBlank()) "$listBase?symbol=$symbol" else listBase
            val positionsBody = mt5Get(listEndpoint, token)

            // The response is JSON — we forward positions info and send modify
            // In reality the AI needs to parse the positions and loop;
            // for now we return the positions data and let AI decide which to modify
            "MT5 Break-Even Analysis\nCurrent positions from broker:\n$positionsBody\n\n" +
            "To break-even: filter positions where profit > 0 and priceOpen > 0, " +
            "then call trading_mt5_modify_position for each with sl = priceOpen. " +
            "Use the ticket numbers from the positions data above."
        } catch (e: Exception) {
            "MT5 break-even error: ${e.message}"
        }
    }

    private suspend fun executeMt5Snapshot(args: Map<String, String>): String {
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/snapshot"
        val limit = args["limit"]?.toIntOrNull() ?: 200
        val endpoint = "$base?limit=$limit"
        return try {
            val body = mt5GetWithArgs(endpoint, args)

            // Parse JSON to remove massive symbols array
            try {
                val root = json.parseToJsonElement(body).jsonObject
                if (root.containsKey("data")) {
                    val data = root["data"]!!.jsonObject
                    if (data.containsKey("snapshot")) {
                        val snapshot = data["snapshot"]!!.jsonObject

                        // Construct a new, concise summary
                        return buildString {
                            appendLine("MT5 Snapshot Summary:")
                            if (snapshot.containsKey("account")) {
                                val acc = snapshot["account"]!!.jsonObject
                                appendLine("Account: Balance=${acc["balance"]}, Equity=${acc["equity"]}, MarginLevel=${acc["margin_level"]}")
                            }
                            if (snapshot.containsKey("positions")) {
                                val posCount = snapshot["positions"]!!.jsonObject.keys.size
                                appendLine("Active Positions: $posCount")
                            }
                            if (snapshot.containsKey("orders")) {
                                val ordCount = snapshot["orders"]!!.jsonObject.keys.size
                                appendLine("Pending Orders: $ordCount")
                            }
                        }.trimEnd()
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors and fallback
            }

            "MT5 Snapshot data fetched successfully but is too large to display directly. Use specific tools like trading_mt5_account_info or trading_mt5_list_positions to query details."
        } catch (e: Exception) {
            "MT5 snapshot error: ${e.message}"
        }
    }

    private suspend fun executeMt5TradeActions(args: Map<String, String>): String {
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/trade-actions"
        return try {
            val body = mt5GetWithArgs(endpoint, args)
            "MT5 Trade Actions Audit Log:\n$body"
        } catch (e: Exception) {
            "MT5 trade actions error: ${e.message}"
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ██  MT5 ADVANCED INTELLIGENCE — วิเคราะห์ขั้นสูงจากข้อมูล Broker  ██
    // ═══════════════════════════════════════════════════════════════════════════

    // ── Market Scanner — คำนวณ server-side ด้วย inferAnalysis ──────────────
    private suspend fun executeMt5MarketScanner(args: Map<String, String>): String {
        val analyzeBase = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?.let { it.substringBeforeLast("/") + "/analyze" }
            ?: "http://127.0.0.1:8090/api/mt5/analyze"
        val symbolsStr = args["symbols"]?.trim().orEmpty()
        val timeframe  = args["timeframe"]?.trim() ?: "H1"
        val count      = args["count"]?.toIntOrNull() ?: 180

        val symbols = if (symbolsStr.isNotBlank()) {
            symbolsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            listOf("XAUUSD", "EURUSD", "GBPUSD", "USDJPY", "BTCUSD", "US30", "NAS100", "XAGUSD")
        }

        return try {
            val results = mutableListOf<String>()
            for (sym in symbols.take(8)) {
                try {
                    val endpoint = "$analyzeBase?symbol=$sym&timeframe=$timeframe&count=$count"
                    val body = mt5GetWithArgs(endpoint, args)
                    results.add(formatMt5AnalyzeResponse(sym, timeframe, body))
                } catch (e: Exception) {
                    results.add("[$sym] Error: ${e.message}")
                }
            }
            buildString {
                appendLine("MT5 Market Scanner — Server-Side Analysis ($timeframe)")
                appendLine("Scanned ${results.size} of ${symbols.size} symbols")
                appendLine()
                results.forEachIndexed { i, r -> appendLine("[${ i + 1 }] $r") }
            }.trimEnd()
        } catch (e: Exception) {
            "MT5 market scanner error: ${e.message}"
        }
    }

    // ── Correlation Radar — คำนวณ server-side ด้วย inferAnalysis ────────────
    private suspend fun executeMt5CorrelationRadar(args: Map<String, String>): String {
        val analyzeBase = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?.let { it.substringBeforeLast("/") + "/analyze" }
            ?: "http://127.0.0.1:8090/api/mt5/analyze"
        val symbolsStr = args["symbols"] ?: return "Missing required argument: symbols"
        val timeframe  = args["timeframe"]?.trim() ?: "H1"
        val count      = args["bars"]?.toIntOrNull() ?: 180
        val symbols    = symbolsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }

        if (symbols.size < 2) return "Need at least 2 symbols for correlation analysis"

        return try {
            // sym, bias, regime, confluence, fitness, strategy, current, atr
            class SymbolAnalysis(
                val sym: String,
                val bias: String,
                val regime: String,
                val confluence: Double,
                val fitness: Double,
                val strategy: String,
                val current: Double,
                val atr: Double
            )

            val analyses = mutableListOf<SymbolAnalysis>()
            val errors   = mutableListOf<String>()

            for (sym in symbols.take(8)) {
                try {
                    val body = mt5GetWithArgs("$analyzeBase?symbol=$sym&timeframe=$timeframe&count=$count", args)
                    val root = json.parseToJsonElement(body).jsonObject
                    val d    = root["data"] as? JsonObject
                    if (d != null) {
                        val ind   = d["indicators"] as? JsonObject
                        val price = d["price"]       as? JsonObject
                        analyses.add(SymbolAnalysis(
                            sym        = sym,
                            bias       = d.stringField("bias")       ?: "NEUTRAL",
                            regime     = d.stringField("regime")     ?: "UNKNOWN",
                            confluence = d.doubleField("confluence") ?: 0.0,
                            fitness    = d.doubleField("fitness")    ?: 0.0,
                            strategy   = d.stringField("strategy")   ?: "",
                            current    = price?.doubleField("current") ?: 0.0,
                            atr        = ind?.doubleField("atr14")    ?: 0.0
                        ))
                    } else {
                        errors.add("$sym: unexpected response")
                    }
                } catch (e: Exception) {
                    errors.add("$sym: ${e.message}")
                }
            }

            // Correlation: compare bias alignment between symbol pairs
            val bullish  = analyses.filter { it.bias == "BULLISH" }.map { it.sym }
            val bearish  = analyses.filter { it.bias == "BEARISH" }.map { it.sym }
            val neutral  = analyses.filter { it.bias == "NEUTRAL" }.map { it.sym }
            val topByFit = analyses.sortedByDescending { it.fitness }.take(3).map { "${it.sym}(${it.fitness.format1()}%)" }

            buildString {
                appendLine("MT5 Correlation Radar — Server-Side Analysis ($timeframe, $count bars)")
                appendLine("Symbols: ${symbols.joinToString(", ")}")
                appendLine()
                appendLine("── Bias Clusters (correlated direction) ─────────────────")
                appendLine("BULLISH : ${if (bullish.isEmpty()) "none" else bullish.joinToString(", ")}")
                appendLine("BEARISH : ${if (bearish.isEmpty()) "none" else bearish.joinToString(", ")}")
                appendLine("NEUTRAL : ${if (neutral.isEmpty()) "none" else neutral.joinToString(", ")}")
                appendLine()
                appendLine("── Top Fitness ──────────────────────────────────────────")
                appendLine(topByFit.joinToString("  |  "))
                appendLine()
                appendLine("── Per-Symbol Summary ───────────────────────────────────")
                analyses.forEach { a ->
                    appendLine("  ${a.sym.padEnd(10)} Regime=${a.regime.padEnd(12)} Bias=${a.bias.padEnd(8)} " +
                               "Conf=${a.confluence.format1().padStart(5)}%  Fit=${a.fitness.format1().padStart(5)}%  " +
                               "ATR=${a.atr.format4()}  Price=${a.current.format4()}")
                }
                if (errors.isNotEmpty()) {
                    appendLine()
                    appendLine("── Errors ───────────────────────────────────────────────")
                    errors.forEach { appendLine("  $it") }
                }
            }.trimEnd()
        } catch (e: Exception) {
            "MT5 correlation radar error: ${e.message}"
        }
    }

    private suspend fun executeMt5SentimentGauge(args: Map<String, String>): String {
        val base  = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5"
        val token = args["token"]?.trim().orEmpty()

        return try {
            val account   = mt5Get("$base/account", token)
            val positions = mt5Get("$base/positions", token)
            val history   = mt5Get("$base/history?limit=50", token)

            buildString {
                appendLine("MT5 Sentiment Gauge — Portfolio Health (from broker)")
                appendLine()
                appendLine("=== Account ===")
                appendLine(account)
                appendLine()
                appendLine("=== Open Positions ===")
                appendLine(positions)
                appendLine()
                appendLine("=== Recent History (50 deals) ===")
                appendLine(history)
                appendLine()
                append("Analyze equity curve, drawdown, win-rate, and overall portfolio health.")
            }.toString()
        } catch (e: Exception) {
            "MT5 sentiment gauge error: ${e.message}"
        }
    }

    private suspend fun executeMt5InstitutionalFlow(args: Map<String, String>): String {
        val analyzeBase = args["endpoint"]?.trim()
            ?.let { it.substringBeforeLast("/") + "/analyze" }
            ?: "http://127.0.0.1:8090/api/mt5/analyze"
        val symbol    = args["symbol"]?.trim()    ?: "XAUUSD"
        val timeframe = args["timeframe"]?.trim() ?: "M15"
        val count     = args["count"]?.toIntOrNull() ?: 300
        return try {
            val body = mt5GetWithArgs("$analyzeBase?symbol=$symbol&timeframe=$timeframe&count=$count", args)
            formatMt5AnalyzeResponse(symbol, timeframe, body)
        } catch (e: Exception) {
            "MT5 institutional flow error: ${e.message}"
        }
    }

    private suspend fun executeMt5TradeJournal(args: Map<String, String>): String {
        val type = args["type"]?.lowercase() ?: "decision" // decision, management, performance
        val limit = args["limit"]?.toIntOrNull() ?: 50

        val subPath = when(type) {
            "management" -> "management-journal"
            "performance" -> "analytics"
            else -> "journal"
        }

        val endpoint = args["endpoint"]?.trim()
            ?: args["server_url"]?.trim()?.removeSuffix("/")?.let { "$it/api/mt5/auto" }
            ?: "http://127.0.0.1:8090/api/mt5/auto"

        val token = args["token"]?.trim().orEmpty()
        val finalUrl = if (endpoint.endsWith(subPath)) endpoint else "$endpoint/$subPath"
        val finalUrlWithLimit = if (finalUrl.contains("?")) "$finalUrl&limit=$limit" else "$finalUrl?limit=$limit"

        return try {
            val response = client.get(finalUrl) {
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Client-Token", token)
                }
            }
            val body = response.bodyAsText()

            // 🧠 AI Synthesis for Journaling
            val prompt = """
                Analyze the following MT5 Auto-Trading Journal data (Type: $type).
                1. Summarize the recent activity or performance.
                2. Identify any patterns of success or recurring errors.
                3. Provide a 'System Health & Performance' commentary.
                4. List 3 key takeaways for the user.

                Journal Data:
                $body
            """.trimIndent()

            val aiAnalysis = try {
                geminiService.generateResponse(
                    prompt = prompt,
                    history = emptyList(),
                    intentAddon = "You are a professional trading coach and system auditor. Provide a constructive and data-driven summary."
                )
            } catch (_: Exception) { "AI Synthesis unavailable." }

            "═══ MT5 Trade Journal ($type) ═══\n" + "=".repeat(40) + "\n" + aiAnalysis + "\n\n(Raw data was processed by JARVIS Intelligence)"
        } catch (e: Exception) {
            "Journal fetch error: ${e.message}"
        }
    }

    private suspend fun executeMt5EconomicRadar(args: Map<String, String>): String {
        val base  = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5"
        val token = args["token"]?.trim().orEmpty()
        return try {
            val account   = mt5Get("$base/account", token)
            val positions = mt5Get("$base/positions", token)
            buildString {
                appendLine("MT5 Economic Radar")
                appendLine("Account: $account")
                appendLine("Positions: $positions")
            }
        } catch (e: Exception) {
            "Economic radar error: ${e.message}"
        }
    }
}

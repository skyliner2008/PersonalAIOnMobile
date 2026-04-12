package com.example.personalaibot.tools.trading

import io.ktor.client.*

import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.logDebug

/**
 * TradingToolExecutor — รัน trading tool calls และ format ผลลัพธ์เป็น text
 * สำหรับส่งกลับให้ Gemini อ่านและอธิบายให้ผู้ใช้
 */
class TradingToolExecutor(private val client: HttpClient, private val geminiService: GeminiService) {

    private val api = TradingApiService(client)

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
            "trading_combined"         -> executeCombined(args)
            else -> "ไม่พบ trading tool: $toolName"
        }
    }

    // ─── Implementations ────────────────────────────────────────────────────

    private suspend fun executePrice(args: Map<String, String>): String {
        val symbol = args["symbol"] ?: return "กรุณาระบุ symbol"
        val data = api.getYahooPrice(symbol)

        if (data.containsKey("error")) return "❌ ดึงข้อมูลไม่ได้: ${data["error"]}"

        return buildString {
            appendLine("💰 **${data["symbol"]}** — Real-time Price")
            appendLine("ราคา: ${data["price"]} ${data["currency"]}")
            appendLine("เปลี่ยนแปลง: ${data["direction"]} ${data["change"]} (${data["change_pct"]})")
            appendLine("ปิดเมื่อวาน: ${data["prev_close"]}")
            appendLine("52W High: ${data["high_52w"]} | Low: ${data["low_52w"]}")
            appendLine("ตลาด: ${data["market_state"]}")
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
                appendLine("=" .repeat(40))
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
                    if (!sector.isNullOrBlank()) append(" — กลุ่ม $sector")
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
        return formatScanResults("📈 Top Gainers — $exchange", results)
    }

    private suspend fun executeTopLosers(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getTopLosers(exchange, limit.coerceAtMost(50))
        return formatScanResults("📉 Top Losers — $exchange", results)
    }

    private suspend fun executeTechnicalAnalysis(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "กรุณาระบุ symbol"
        val exchange = args["exchange"] ?: "BINANCE"
        val interval = args["interval"] ?: "1h"
        val data = api.getTechnicalAnalysis(symbol, exchange, interval)

        if (data.containsKey("error")) return "❌ TA error: ${data["error"]}"

        val signal = data["signal"] ?: "N/A"
        val score  = data["recommend_score"] ?: "N/A"

        val signalEmoji = when (signal) {
            "STRONG BUY"  -> "🟢🟢"
            "BUY"         -> "🟢"
            "HOLD"        -> "🟡"
            "SELL"        -> "🔴"
            "STRONG SELL" -> "🔴🔴"
            else          -> "⚪"
        }

        return buildString {
            appendLine("📊 **Technical Analysis — ${symbol.uppercase()} ($interval)**")
            appendLine("สัญญาณ: $signalEmoji $signal (score: $score)")
            appendLine("─".repeat(35))
            appendLine("💵 ราคา: ${data["close"]} | เปลี่ยน: ${data["change"]}%")
            appendLine("")
            appendLine("**Momentum**")
            appendLine("  RSI: ${data["RSI"]} (prev: ${data["RSI[1]"]})")
            appendLine("  MACD: ${data["MACD.macd"]} | Signal: ${data["MACD.signal"]} | Hist: ${data["MACD.hist"]}")
            appendLine("  Stoch K/D: ${data["Stoch.K"]} / ${data["Stoch.D"]}")
            appendLine("  CCI: ${data["CCI20"]} | AO: ${data["AO"]}")
            appendLine("")
            appendLine("**Trend**")
            appendLine("  EMA20: ${data["EMA20"]} | EMA50: ${data["EMA50"]} | EMA200: ${data["EMA200"]}")
            appendLine("  ADX: ${data["ADX"]} (+DI: ${data["ADX+DI"]} / -DI: ${data["ADX-DI"]})")
            appendLine("")
            appendLine("**Volatility**")
            appendLine("  BB Upper: ${data["BB.upper"]}")
            appendLine("  BB Basis: ${data["BB.basis"]}")
            appendLine("  BB Lower: ${data["BB.lower"]}")
            appendLine("  BB Width: ${data["BB.width"]} | ATR: ${data["ATR"]}")
            appendLine("")
            appendLine("**Summary** — Buy: ${data["buy_signals"]} | Sell: ${data["sell_signals"]} | Neutral: ${data["neutral_signals"]}")
        }
    }

    private suspend fun executeMultiTimeframe(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "กรุณาระบุ symbol"
        val exchange = args["exchange"] ?: "BINANCE"
        val data = api.getMultiTimeframeAnalysis(symbol, exchange)

        return buildString {
            appendLine("⏱ **Multi-Timeframe Analysis — ${symbol.uppercase()}**")
            appendLine("─".repeat(40))

            val signals = mutableListOf<String>()
            for (entry in data) {
                val tf = entry.key
                val taData = entry.value
                val signal = taData["signal"] ?: "N/A"
                val rsi    = taData["RSI"]    ?: "N/A"
                val score  = taData["recommend_score"] ?: "N/A"
                val emoji  = signalEmoji(signal)
                appendLine("$emoji **$tf**: $signal | RSI: $rsi | Score: $score")
                signals.add(signal)
            }

            appendLine("")
            // Alignment check
            val buyCount  = signals.count { it.contains("BUY") }
            val sellCount = signals.count { it.contains("SELL") }
            val alignment = when {
                buyCount >= 4  -> "🟢 Strong Alignment — สัญญาณ BUY หลาย timeframe"
                sellCount >= 4 -> "🔴 Strong Alignment — สัญญาณ SELL หลาย timeframe"
                buyCount > sellCount -> "🟡 Mostly Bullish — แต่ยังมี timeframe ที่ขัดแย้ง"
                sellCount > buyCount -> "🟡 Mostly Bearish — แต่ยังมี timeframe ที่ขัดแย้ง"
                else -> "⚪ Mixed Signals — timeframe ขัดแย้งกัน ควรระวัง"
            }
            appendLine("**Alignment: $alignment**")
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
        return formatScanResults("🔵 Oversold Scan (RSI < 30) — $exchange", results, extraKey = "RSI")
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
        return formatScanResults("💥 Volume Breakout — $exchange", results, extraKey = "volume")
    }

    private suspend fun executeSentiment(args: Map<String, String>): String {
        val symbol = args["symbol"] ?: return "กรุณาระบุ symbol"
        val data = api.getRedditSentiment(symbol)

        @Suppress("UNCHECKED_CAST")
        val topPosts = data["top_posts"] as? List<String> ?: emptyList()

        return buildString {
            appendLine("🧠 **Reddit Sentiment — ${data["symbol"]}**")
            appendLine("─".repeat(35))
            appendLine("สรุป: **${data["sentiment_label"]}**")
            appendLine("Score: ${data["sentiment_score"]}")
            appendLine("โพสต์ที่วิเคราะห์: ${data["posts_analyzed"]}")
            appendLine("Bullish posts: ${data["bullish_posts"]} | Bearish: ${data["bearish_posts"]}")
            if (topPosts.isNotEmpty()) {
                appendLine("")
                appendLine("**Top Posts (Hot):**")
                topPosts.forEachIndexed { i, post -> appendLine("${i+1}. $post") }
            }
        }
    }

    private suspend fun executeNews(args: Map<String, String>): String {
        val symbol = args["symbol"]
        val limit  = args["limit"]?.toIntOrNull() ?: 8
        val data   = api.getFinancialNews(symbol, limit) as? Map<String, Any> ?: emptyMap()

        @Suppress("UNCHECKED_CAST")
        val items = data["news"] as? List<Map<String, String>> ?: emptyList()

        return buildString {
            val header = if (symbol != null) "📰 **ข่าว — $symbol**" else "📰 **Financial News**"
            appendLine(header)
            appendLine("─".repeat(35))
            if (items.isEmpty()) {
                appendLine("ไม่พบข่าวในขณะนี้")
            } else {
                items.forEachIndexed { i, item ->
                    appendLine("${i+1}. [${item["source"]}] ${item["title"]}")
                    if (!item["date"].isNullOrBlank()) appendLine("   📅 ${item["date"]}")
                    appendLine("")
                }
            }
        }
    }

    private suspend fun executeCombined(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "กรุณาระบุ symbol"
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
            taBullish && sentBullish && confluenceMatch -> "🟢 **STRONG BUY** — TA และ Sentiment ตรงกัน"
            taBullish && confluenceMatch -> "🟢 **BUY** — TA ยืนยัน Bullish"
            !taBullish && !sentBullish   -> "🔴 **SELL** — ทั้ง TA และ Sentiment Bearish"
            else                          -> "🟡 **MIXED** — TA และ Sentiment ขัดแย้งกัน ควรระวัง"
        }

        @Suppress("UNCHECKED_CAST")
        val newsItems = news["news"] as? List<Map<String, String>> ?: emptyList()

        return buildString {
            appendLine("⚡ **Combined Analysis — ${symbol.uppercase()} ($interval)**")
            appendLine("=" .repeat(45))
            appendLine("")
            appendLine("**📊 Technical:** $taSignal (score: ${ta["recommend_score"]})")
            appendLine("  RSI: ${ta["RSI"]} | MACD hist: ${ta["MACD.hist"]}")
            appendLine("  EMA20: ${ta["EMA20"]} vs EMA50: ${ta["EMA50"]}")
            appendLine("")
            appendLine("**🧠 Sentiment:** $sentLabel (${sentiment["sentiment_score"]})")
            appendLine("  ${sentiment["posts_analyzed"]} posts — Bull: ${sentiment["bullish_posts"]} Bear: ${sentiment["bearish_posts"]}")
            appendLine("")
            appendLine("**📰 Latest News:**")
            newsItems.take(3).forEach { item ->
                appendLine("  • ${item["title"]}")
            }
            appendLine("")
            appendLine("─".repeat(45))
            appendLine("**🎯 Confluence Decision: $finalCall**")
            appendLine(if (confluenceMatch) "✅ สัญญาณ TA + Sentiment ตรงกัน (confidence สูง)"
                       else "⚠️ สัญญาณขัดแย้ง — ควร wait ยืนยันก่อน")
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun formatScanResults(
        title: String,
        results: List<Map<String, String>>,
        extraKey: String? = null
    ): String {
        if (results.firstOrNull()?.containsKey("error") == true) {
            return "❌ ${results.first()["error"]}"
        }
        return buildString {
            appendLine("$title")
            appendLine("─".repeat(40))
            results.take(20).forEachIndexed { i, row ->
                val sym    = row["symbol"] ?: "?"
                val desc   = row["description"] ?: sym
                val change = row["change"] ?: "0.0"
                val cap    = row["market_cap_basic"] ?: "N/A"
                val extra  = if (extraKey != null) " | $extraKey: ${row[extraKey] ?: "N/A"}" else ""
                val dir    = if ((change.toDoubleOrNull() ?: 0.0) >= 0) "▲" else "▼"
                
                // Premium format: Name (Symbol)
                val displayName = if (desc != sym && desc.isNotBlank()) "**$desc** ($sym)" else "**$sym**"
                val capStr = if (cap != "N/A") " | Cap: $cap" else ""
                appendLine("${i+1}. $dir $displayName — $change%$capStr$extra")
            }
            appendLine("(${results.size} รายการ)")
        }
    }

    private fun signalEmoji(signal: String): String = when (signal) {
        "STRONG BUY"  -> "🟢🟢"
        "BUY"         -> "🟢"
        "HOLD"        -> "🟡"
        "SELL"        -> "🔴"
        "STRONG SELL" -> "🔴🔴"
        else           -> "⚪"
    }
}

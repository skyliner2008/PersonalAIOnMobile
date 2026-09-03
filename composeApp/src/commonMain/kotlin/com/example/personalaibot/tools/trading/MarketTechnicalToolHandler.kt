package com.example.personalaibot.tools.trading

import com.example.personalaibot.automation.IndicatorAlertProvider
import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.logDebug
import io.ktor.client.*
import kotlinx.datetime.*

/**
 * MarketTechnicalToolHandler: Handles market snapshots, price lookups, technical indicators,
 * multi-timeframe scans, sentiment gauges, news synthesis, macro calendars, and harmonic/wave patterns.
 */
internal class MarketTechnicalToolHandler(
    private val client: HttpClient,
    private val api: TradingApiService,
    private val geminiService: GeminiService,
    private val advancedEngine: AdvancedTradingEngine
) {
    private val indicatorProvider = IndicatorAlertProvider(SmcApiService(client))

    suspend fun execute(toolName: String, args: Map<String, String>): String = when (toolName) {
        "trading_price" -> executePrice(args)
        "trading_market_snapshot" -> executeMarketSnapshot(args)
        "trading_top_gainers" -> executeTopGainers(args)
        "trading_top_losers" -> executeTopLosers(args)
        "trading_technical_analysis" -> executeTechnicalAnalysis(args)
        "trading_multi_timeframe" -> executeMultiTimeframe(args)
        "trading_bollinger_scan" -> executeBollingerScan(args)
        "trading_oversold_scan" -> executeOversoldScan(args)
        "trading_overbought_scan" -> executeOverboughtScan(args)
        "trading_volume_breakout" -> executeVolumeBreakout(args)
        "trading_sentiment" -> executeSentiment(args)
        "trading_fear_greed" -> executeFearGreed(args)
        "trading_news" -> executeNews(args)
        "trading_macro_calendar" -> executeMacroCalendar(args)
        "trading_harmonic_scan" -> executeHarmonicScan(args)
        "trading_elliot_wave" -> executeElliotWave(args)
        else -> "Unknown market/technical tool: $toolName"
    }

    internal suspend fun executePrice(args: Map<String, String>): String {
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

    internal suspend fun executeMarketSnapshot(args: Map<String, String>): String {
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

    internal suspend fun executeTopGainers(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getTopGainers(exchange, limit.coerceAtMost(50))
        return formatScanResults("🚀 Top Gainers — $exchange", results)
    }

    internal suspend fun executeTopLosers(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getTopLosers(exchange, limit.coerceAtMost(50))
        return formatScanResults("📉 Top Losers — $exchange", results)
    }

    /** เติมค่าที่ scanner คืน null/N/A ด้วยค่าที่คำนวณเองจากแท่งเทียน (300 แท่ง) */
    internal suspend fun fillTaFromLocal(symbol: String, interval: String, data: Map<String, String>): Map<String, String> {
        fun bad(v: String?) = v == null || v == "N/A" || v == "null"
        val needsLocal = listOf("close", "RSI", "RSI[1]", "MACD.macd", "MACD.signal", "MACD.hist",
            "Stoch.K", "Stoch.D", "CCI20", "AO", "EMA20", "EMA50", "EMA200",
            "BB.upper", "BB.basis", "BB.lower", "BB.width", "ATR", "ADX", "ADX+DI", "ADX-DI"
        ).any { bad(data[it]) }
        if (!needsLocal) return data

        val tf = TaIndicators.normalizeTimeframe(interval)
        val local = runCatching { indicatorProvider.fetch("$symbol@$tf") }.getOrNull() ?: return data
        if (local.containsKey("error")) return data

        val out = data.toMutableMap()
        fun fill(key: String, localKey: String) {
            if (bad(out[key])) local[localKey]?.let { out[key] = it }
        }
        fill("close", "close"); fill("RSI", "rsi14"); fill("RSI[1]", "rsi14_prev")
        fill("MACD.macd", "macd"); fill("MACD.signal", "macd_signal"); fill("MACD.hist", "macd_hist")
        fill("Stoch.K", "stoch_k"); fill("Stoch.D", "stoch_d"); fill("CCI20", "cci20"); fill("AO", "ao")
        fill("EMA20", "ema20"); fill("EMA50", "ema50"); fill("EMA200", "ema200")
        fill("BB.upper", "bb_upper"); fill("BB.basis", "bb_basis"); fill("BB.lower", "bb_lower")
        fill("BB.width", "bb_width"); fill("ATR", "atr14")
        fill("ADX", "adx"); fill("ADX+DI", "di_plus"); fill("ADX-DI", "di_minus")
        return out
    }

    internal suspend fun executeTechnicalAnalysis(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "Missing required argument: symbol"
        val resolvedExchange = api.resolveExchange(symbol, args["exchange"])
        val interval = TaIndicators.normalizeTimeframe(args["interval"] ?: "1h")
        val data = fillTaFromLocal(symbol, interval, api.getTechnicalAnalysis(symbol, resolvedExchange, interval))

        if (data.containsKey("error")) return "TA error: ${data["error"]}"

        val signal = data["signal"] ?: "N/A"
        val score  = data["recommend_score"] ?: "N/A"
        val emoji  = signalEmoji(signal)

        return buildString {
            appendLine("Technical Analysis - ${symbol.uppercase()} ($interval)")
            appendLine("Signal: $emoji $signal (score: $score)")
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

    internal suspend fun executeMultiTimeframe(args: Map<String, String>): String {
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

    internal suspend fun executeBollingerScan(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 30
        val results = api.getBollingerSqueeze(exchange, limit)
        return formatScanResults("🔥 Bollinger Squeeze — $exchange (กำลัง Breakout)", results, extraKey = "BB.width")
    }

    internal suspend fun executeOversoldScan(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getOversoldSymbols(exchange, limit)
        return formatScanResults("🟢 Oversold Scan (RSI < 30) — $exchange", results, extraKey = "RSI")
    }

    internal suspend fun executeOverboughtScan(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getOverboughtSymbols(exchange, limit)
        return formatScanResults("🔴 Overbought Scan (RSI > 70) — $exchange", results, extraKey = "RSI")
    }

    internal suspend fun executeVolumeBreakout(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getVolumeBreakout(exchange, limit)
        return formatScanResults("🌋 Volume Breakout — $exchange", results, extraKey = "volume")
    }

    internal suspend fun executeSentiment(args: Map<String, String>): String {
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

    internal suspend fun executeFearGreed(@Suppress("UNUSED_PARAMETER") args: Map<String, String>): String {
        val fng = api.getFearGreedIndex(7)
        fng["error"]?.let { return "⚠️ ดึง Fear & Greed Index ไม่สำเร็จ ($it)" }

        val value = fng["value"]?.toIntOrNull() ?: 0
        val classification = fng["classification"] ?: "Unknown"
        val trend = fng["trend"].orEmpty().split(",").mapNotNull { it.toIntOrNull() }

        val emoji = when {
            value <= 24 -> "😱"; value <= 44 -> "😨"
            value <= 55 -> "😐"; value <= 74 -> "😃"; else -> "🤑"
        }
        val thaiLabel = when {
            value <= 24 -> "Extreme Fear — ตลาดหวาดกลัวสุดขีด (มักเป็นจังหวะสะสมของนักลงทุน contrarian)"
            value <= 44 -> "Fear — ตลาดกังวล แรงขายยังคุมเกม"
            value <= 55 -> "Neutral — ตลาดสมดุล ไม่มีอารมณ์ครอบงำ"
            value <= 74 -> "Greed — ตลาดเริ่มโลภ โมเมนตัมฝั่งซื้อแข็งแรง"
            else -> "Extreme Greed — ตลาดโลภสุดขีด ระวังจุดฟองสบู่/การทำกำไรรุนแรง"
        }

        return buildString {
            appendLine("🌡️ **Crypto Fear & Greed Index (alternative.me — Real-time)**")
            appendLine("=".repeat(45))
            appendLine("$emoji **ค่าปัจจุบัน: $value/100 — $classification**")
            appendLine("📖 การตีความ: $thaiLabel")
            if (trend.size >= 2) {
                val newest = trend.first(); val oldest = trend.last()
                val dir = when {
                    newest > oldest + 3 -> "📈 อารมณ์ตลาดกำลังดีขึ้น"
                    newest < oldest - 3 -> "📉 อารมณ์ตลาดกำลังแย่ลง"
                    else -> "➡️ อารมณ์ตลาดทรงตัว"
                }
                appendLine("📊 อนุกรม ${trend.size} วัน (ใหม่→เก่า): ${trend.joinToString(", ")}")
                appendLine("🧭 ทิศทาง: $dir ($oldest → $newest)")
            }
        }
    }

    internal suspend fun executeNews(args: Map<String, String>): String {
        var symbol = args["symbol"]?.trim()
        val limit  = args["limit"]?.toIntOrNull() ?: 5

        if (symbol?.uppercase() == "XAUUSD" || symbol?.lowercase() == "gold") {
            symbol = "Gold"
        }

        var data = api.getFinancialNews(symbol, limit) as? Map<String, Any> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        var items = data["news"] as? List<Map<String, String>> ?: emptyList()

        if (items.isEmpty() && symbol != null) {
            logDebug("TradingTool", "No news for $symbol, falling back to Global Macro")
            data = api.getFinancialNews(null, limit) as? Map<String, Any> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            items = data["news"] as? List<Map<String, String>> ?: emptyList()
        }

        if (items.isEmpty()) return "No relevant financial news found at this moment."

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

    internal suspend fun executeMacroCalendar(args: Map<String, String>): String {
        val limit = args["limit"]?.toIntOrNull() ?: 15
        val filter = args["filter"]?.trim()?.lowercase()
        val currency = args["currency"]?.trim()?.uppercase()
        val events = api.getEconomicCalendar(limit = limit, filter = filter, currency = currency)

        if (events.isEmpty()) return "ไม่พบข้อมูลปฏิทินเศรษฐกิจสำหรับเงื่อนไขที่ระบุในสัปดาห์นี้"

        val nowInstant = Clock.System.now()
        val nowBkk = nowInstant.toLocalDateTime(TimeZone.of("Asia/Bangkok"))
        val thaiDays = mapOf(
            DayOfWeek.MONDAY to "จันทร์", DayOfWeek.TUESDAY to "อังคาร", DayOfWeek.WEDNESDAY to "พุธ",
            DayOfWeek.THURSDAY to "พฤหัสบดี", DayOfWeek.FRIDAY to "ศุกร์", DayOfWeek.SATURDAY to "เสาร์",
            DayOfWeek.SUNDAY to "อาทิตย์"
        )
        val thaiMonths = listOf("", "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
        val nowDay = thaiDays[nowBkk.dayOfWeek] ?: ""
        val nowMonth = thaiMonths.getOrElse(nowBkk.monthNumber) { "" }
        val nowFormatted = "วัน$nowDay ที่ ${nowBkk.dayOfMonth} $nowMonth เวลา ${nowBkk.hour.toString().padStart(2, '0')}:${nowBkk.minute.toString().padStart(2, '0')} น."

        val eventsText = events.joinToString("\n") {
            "[${it["status"]}] [${it["impact"]}] ${it["country"]}: ${it["title"]} (${it["date_time"]}) -> คาดการณ์: ${it["forecast"]}, ครั้งก่อน: ${it["previous"]}"
        }

        val aiAnalysis = try {
            geminiService.generateResponse(
                prompt = """
                    เวลาปัจจุบันในประเทศไทยคือ: $nowFormatted
                    
                    รายการข่าวเศรษฐกิจ ForexFactory (แปลงเวลาไทยเรียบร้อยแล้ว):
                    $eventsText
                    
                    คำแนะนำในการวิเคราะห์สำหรับเทรดเดอร์:
                    1. ให้ความสำคัญกับข่าวที่กำลังจะมาถึง [UPCOMING / รอประกาศ] ในช่วงเวลาที่เหลือของสัปดาห์นี้เป็นอันดับแรก โดยเฉพาะข่าวความสำคัญสูง (🔴 High) และปานกลาง (🟠 Medium) ที่ส่งผลต่อ USD, ทองคำ (XAUUSD) หรือคู่เงินหลัก
                    2. ข่าวที่ระบุสถานะ [PASSED / ประกาศแล้ว] หมายถึงประกาศผ่านไปแล้ว ให้สรุปสั้นๆ เท่านั้น ห้ามพูดเสมือนว่าเป็นเหตุการณ์ที่ยังไม่เกิดขึ้นเด็ดขาด
                    3. สรุปความเสี่ยงและคำแนะนำสำหรับเทรดเดอร์ (เช่น การงดถือออเดอร์ข้ามช่วงข่าว)
                """.trimIndent(),
                intentAddon = "คุณคือนักเศรษฐศาสตร์มหภาคและเทรดเดอร์มืออาชีพ ปัจจุบันคือเวลา $nowFormatted จงวิเคราะห์เฉพาะข่าวที่กำลังจะเกิดขึ้นต่อจากนี้และผลกระทบต่อตลาดอย่างแม่นยำ"
            )
        } catch (_: Exception) { "การวิเคราะห์อัตโนมัติไม่พร้อมใช้งานในขณะนี้" }

        return buildString {
            appendLine("📅 **Economic Calendar (ForexFactory — Real-time, เวลาไทย)**")
            appendLine("⏰ เวลาปัจจุบัน: **$nowFormatted (ไทย)**")
            appendLine("-".repeat(45))
            events.forEach { e ->
                val emoji = when(e["impact"]?.lowercase()) {
                    "high" -> "🔴"
                    "medium" -> "🟠"
                    else -> "🟡"
                }
                val statusTag = if (e["status"] == "UPCOMING") "⏳ [รอประกาศ]" else "✅ [ประกาศแล้ว]"
                appendLine("$emoji **${e["title"]}** (${e["country"]}) $statusTag")
                appendLine("   | ความสำคัญ: ${e["impact"]} | เวลา: ${e["date_time"]}")
                appendLine("   | คาดการณ์ (Forecast): ${e["forecast"]} | ครั้งก่อน (Previous): ${e["previous"]}")
            }
            appendLine("")
            appendLine("🔦 **AI Strategic Preview**")
            appendLine(aiAnalysis)
        }
    }

    internal suspend fun executeHarmonicScan(args: Map<String, String>): String {
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

    internal suspend fun executeElliotWave(args: Map<String, String>): String {
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

    internal fun formatScanResults(
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

    internal fun signalEmoji(signal: String) = when (signal) {
        "STRONG BUY"  -> "++"
        "BUY"         -> "+"
        "HOLD"        -> "="
        "SELL"        -> "-"
        "STRONG SELL" -> "--"
        else          -> "?"
    }
}

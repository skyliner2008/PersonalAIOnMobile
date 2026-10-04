package com.skyliner2008.jarvis.tools.trading

import com.skyliner2008.jarvis.automation.IndicatorAlertProvider
import com.skyliner2008.jarvis.avatar.AvatarToolData
import com.skyliner2008.jarvis.data.GeminiService
import com.skyliner2008.jarvis.logDebug
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
        // ชื่อ tool ที่ประกาศ/route จริงคือ trading_elliot_modern_analysis
        // เดิม branch ชื่อ "trading_elliot_wave" ซึ่งไม่มีใครเรียก → tool นี้ตกไป else
        // แล้วคืน "Unknown market/technical tool" ทุกครั้ง ทั้งที่ implementation พร้อมใช้
        "trading_elliot_modern_analysis" -> executeElliotWave(args)
        else -> "Unknown market/technical tool: $toolName"
    }

    internal suspend fun executePrice(args: Map<String, String>): String {
        val symbol = args["symbol"] ?: return "Missing required argument: symbol"
        val data = api.getBestEffortPrice(symbol)

        if (data.containsKey("error")) return "Price fetch failed: ${data["error"]}"
        AvatarToolData.publish("trading_price", mapOf("symbol" to symbol.uppercase()) + data)

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
                val rows = snapshot.filterValues { !it.containsKey("error") }.map { (name, data) ->
                    mapOf("symbol" to name, "close" to data["price"], "change" to data["change_pct"])
                }
                if (rows.isNotEmpty()) AvatarToolData.publishRows("trading_market_snapshot", rows)
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
                if (list.isNotEmpty()) AvatarToolData.publishRows("trading_market_snapshot", list)
                append(formatScanResults(title, list))
            }
        }
    }

    internal suspend fun executeTopGainers(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getTopGainers(exchange, limit.coerceAtMost(50))
        AvatarToolData.publishRows("trading_top_gainers", results)
        return formatScanResults("🚀 Top Gainers — $exchange${scanScope(exchange)}", results)
    }

    internal suspend fun executeTopLosers(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getTopLosers(exchange, limit.coerceAtMost(50))
        AvatarToolData.publishRows("trading_top_losers", results)
        return formatScanResults("📉 Top Losers — $exchange${scanScope(exchange)}", results)
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
        fill("ADX", "adx14"); fill("ADX+DI", "di_plus"); fill("ADX-DI", "di_minus")
        return out
    }

    internal suspend fun executeTechnicalAnalysis(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "Missing required argument: symbol"
        val resolvedExchange = api.resolveExchange(symbol, args["exchange"])
        val interval = TaIndicators.normalizeTimeframe(args["interval"] ?: "1h")
        val data = fillTaFromLocal(symbol, interval, api.getTechnicalAnalysis(symbol, resolvedExchange, interval))

        if (data.containsKey("error")) return "TA error: ${data["error"]}"
        AvatarToolData.publish("trading_technical_analysis", mapOf("symbol" to symbol.uppercase(), "interval" to interval) + data)

        val signal = data["signal"] ?: "N/A"
        val score  = data["recommend_score"] ?: "N/A"
        val emoji  = signalEmoji(signal)

        return buildString {
            appendLine("Technical Analysis - ${symbol.uppercase()} ($interval)")
            appendLine("Signal: $emoji $signal (score: $score)")
            appendLine("-".repeat(35))
            appendLine("Price: ${data["close"]} | Change (this $interval bar): ${data["change"]}%")
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
            appendLine("  BB Width: ${data["BB.width"]?.takeIf { it != "N/A" }?.let { "$it%" } ?: "N/A"} | ATR: ${data["ATR"]}")
            appendLine("")
            // TradingView's rating, -1 (strong sell) … +1 (strong buy). (Was "Summary - Buy/Sell/Neutral": the scanner
            // has no such columns, it always said N/A.)
            appendLine("Rating -1…+1 (TradingView) - Moving averages: ${data["Recommend.MA"] ?: "N/A"} | Oscillators: ${data["Recommend.Other"] ?: "N/A"}")
        }
    }

    internal suspend fun executeMultiTimeframe(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "Missing required argument: symbol"
        val resolvedExchange = api.resolveExchange(symbol, args["exchange"])
        val data = api.getMultiTimeframeAnalysis(symbol, resolvedExchange)
        val signals = data.values.map { it["signal"] ?: "N/A" }
        val buyCount  = signals.count { it.contains("BUY") }
        val sellCount = signals.count { it.contains("SELL") }
        val alignment = when {
            buyCount >= 4  -> "Strong alignment (BUY)"
            sellCount >= 4 -> "Strong alignment (SELL)"
            buyCount > sellCount -> "Mostly bullish"
            sellCount > buyCount -> "Mostly bearish"
            else -> "Mixed signals"
        }
        // one row per timeframe for Avatar mode's robot (pet_godot mtf_report)
        if (data.isNotEmpty()) {
            AvatarToolData.publish("trading_multi_timeframe", mapOf(
                "symbol" to symbol.uppercase(), "alignment" to alignment,
                "buy_count" to buyCount, "sell_count" to sellCount,
                "timeframes" to data.map { (tf, ta) ->
                    mapOf("tf" to tf, "signal" to (ta["signal"] ?: "N/A"), "rsi" to ta["RSI"], "score" to ta["recommend_score"])
                }
            ))
        }

        return buildString {
            appendLine("Multi-Timeframe Analysis - ${symbol.uppercase()}")
            appendLine("-".repeat(40))
            for ((tf, taData) in data) {
                val signal = taData["signal"] ?: "N/A"
                val rsi    = taData["RSI"]    ?: "N/A"
                val score  = taData["recommend_score"] ?: "N/A"
                appendLine("${signalEmoji(signal)} $tf: $signal | RSI: $rsi | Score: $score")
            }
            appendLine("")
            appendLine("Alignment: $alignment")
        }
    }

    internal suspend fun executeBollingerScan(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 30
        val results = api.getBollingerSqueeze(exchange, limit)
        if (results.isNotEmpty()) AvatarToolData.publishRows("trading_bollinger_scan", results)
        return formatScanResults(
            "🔥 Bollinger แคบที่สุด (แท่งวัน) — $exchange${scanScope(exchange)} · BB.width = กว้างกี่ % ของเส้นกลาง " +
                "(ยิ่งน้อยยิ่งบีบตัว มักตามด้วยการเคลื่อนไหวแรง — ยังไม่ใช่ breakout)",
            results, extraKey = "BB.width"
        )
    }

    internal suspend fun executeOversoldScan(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getOversoldSymbols(exchange, limit)
        if (results.isNotEmpty()) AvatarToolData.publishRows("trading_oversold_scan", results)
        return formatScanResults("🟢 Oversold Scan (RSI รายวัน < 30) — $exchange${scanScope(exchange)}", results, extraKey = "RSI")
    }

    internal suspend fun executeOverboughtScan(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getOverboughtSymbols(exchange, limit)
        if (results.isNotEmpty()) AvatarToolData.publishRows("trading_overbought_scan", results)
        return formatScanResults("🔴 Overbought Scan (RSI รายวัน > 70) — $exchange${scanScope(exchange)}", results, extraKey = "RSI")
    }

    internal suspend fun executeVolumeBreakout(args: Map<String, String>): String {
        val exchange = args["exchange"] ?: "BINANCE"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val results = api.getVolumeBreakout(exchange, limit)
        if (results.isNotEmpty()) AvatarToolData.publishRows("trading_volume_breakout", results)
        return formatScanResults("🌋 Volume Breakout — $exchange${scanScope(exchange)}", results, extraKey = "volume")
    }

    internal suspend fun executeSentiment(args: Map<String, String>): String {
        val rawSymbol = args["symbol"]?.trim()?.takeIf { it.isNotEmpty() }
        val unified = api.getUnifiedCompositeSentiment(rawSymbol)
        val targetName = if (unified.isGlobalMacro) "GLOBAL MACRO" else unified.target.uppercase()
        // for Avatar mode's robot (pet_godot sentiment_report)
        AvatarToolData.publish("trading_sentiment", buildMap {
            put("symbol", targetName)
            put("sentiment", unified.compositeScore)
            put("sentiment_label", unified.compositeLabel)
            put("sentiment_th", unified.labelThai)
            put("pillars", unified.pillars.map { p ->
                mapOf("name" to pillarName(p.name), "short" to pillarShortName(p.name), "weight" to p.weightPct, "score" to p.score)
            })
            unified.binancePositioning?.let { b ->
                put("retail_long", b.retailLongAccountPct)
                put("top_long", b.topTraderLongPositionPct)
                put("taker_ratio", b.takerBuySellRatio)
            }
            unified.contrarianAlert?.let { put("contrarian", it) }
        })

        // ส่งให้ AI Gemini สังเคราะห์มิติจิตวิทยาและ Contrarian Opportunity
        val prompt = buildString {
            appendLine("You are JARVIS Senior Market Sentiment & Behavioral Economics Specialist.")
            appendLine("Analyze the market sentiment for $targetName based on live composite data:")
            appendLine("- Composite Sentiment Score: ${"%.1f".format(unified.compositeScore)}/100 (${unified.compositeLabel} / ${unified.labelThai})")
            appendLine("- Pillars Breakdown:")
            unified.pillars.forEach { p ->
                appendLine("  * ${p.name}: ${"%.1f".format(p.score)}/100 (Weight: ${p.weightPct}%) — ${p.detail}")
            }
            val binancePos = unified.binancePositioning
            if (binancePos != null) {
                appendLine("- Derivatives Retail vs Smart Money (Binance Futures):")
                appendLine("  * Retail Accounts: Long ${"%.1f".format(binancePos.retailLongAccountPct)}% vs Short ${"%.1f".format(binancePos.retailShortAccountPct)}% (Ratio: ${"%.2f".format(binancePos.retailLongShortRatio)})")
                appendLine("  * Top Traders (Whales): Long ${"%.1f".format(binancePos.topTraderLongPositionPct)}% vs Short ${"%.1f".format(binancePos.topTraderShortPositionPct)}% (Ratio: ${"%.2f".format(binancePos.topTraderLongShortRatio)})")
                appendLine("  * Taker Buy/Sell Ratio: ${"%.2f".format(binancePos.takerBuySellRatio)}")
                appendLine("  * Divergence Alert: ${binancePos.divergenceSignal}")
            }
            if (unified.contrarianAlert != null) {
                appendLine("- Contrarian Signal Detected: ${unified.contrarianAlert}")
            }
            if (unified.topHeadlines.isNotEmpty()) {
                appendLine("- Recent Market Narrative Headlines:")
                unified.topHeadlines.take(4).forEach { appendLine("  * $it") }
            }
            appendLine("")
            appendLine("Please generate a concise, professional Thai synthesis covering:")
            appendLine("1. 🧠 สภาวะจิตวิทยาฝูงชน (Crowd Psychology Phase เช่น Euphoria, Complacency, Skepticism, Panic, Capitulation)")
            if (!unified.isGlobalMacro && binancePos != null) {
                appendLine("2. ⚖️ ความสอดคล้องหรือขัดแย้งระหว่างรายย่อยกับ Smart Money (Divergence / Positioning Analysis)")
            }
            appendLine("3. 🎯 สรุปกลยุทธ์ Contrarian หรือระดับความเสี่ยงของตลาดในปัจจุบัน (Actionable Takeaway)")
            appendLine("Keep it focused, actionable, and formatted in clean markdown bullet points.")
        }

        val aiSynthesis = try {
            geminiService.generateToolEnrichment(
                prompt = prompt,
                intentAddon = "You are a behavioral finance and market sentiment expert. Provide sharp, realistic insights in Thai."
            )
        } catch (_: Exception) { "ไม่สามารถประมวลผลบทวิเคราะห์ AI ได้ในขณะนี้" }

        return buildString {
            appendLine("### 🗣️ JARVIS Unified Sentiment Index — $targetName")
            appendLine("---")
            appendLine("${unified.emoji} **`${unified.meterBar}` ${"%.1f".format(unified.compositeScore)}/100 — ${unified.labelThai} (${unified.compositeLabel})**")
            appendLine("")

            appendLine("### 🏛️ โครงสร้าง 4 เสาหลัก (4-Pillars)")
            appendLine("| เสาหลัก | น้ำหนัก | คะแนน | รายละเอียด |")
            appendLine("| :--- | :---: | :---: | :--- |")
            unified.pillars.forEach { p ->
                appendLine("| ${pillarName(p.name)} | ${p.weightPct}% | **${"%.1f".format(p.score)}** | ${p.detail} |")
            }
            appendLine("")

            val binancePos = unified.binancePositioning
            if (binancePos != null) {
                appendLine("### 🐋 สัดส่วนพอร์ตอนุพันธ์ (Derivatives Positioning)")
                appendLine("- **รายย่อย (Retail):** Long ${"%.1f".format(binancePos.retailLongAccountPct)}% | Short ${"%.1f".format(binancePos.retailShortAccountPct)}% (Ratio: `${"%.2f".format(binancePos.retailLongShortRatio)}`)")
                appendLine("- **สถาบัน/รายใหญ่ (Top Traders):** Long ${"%.1f".format(binancePos.topTraderLongPositionPct)}% | Short ${"%.1f".format(binancePos.topTraderShortPositionPct)}% (Ratio: `${"%.2f".format(binancePos.topTraderLongShortRatio)}`)")
                appendLine("- **Taker Buy/Sell Ratio:** `${"%.2f".format(binancePos.takerBuySellRatio)}`")
                appendLine("- **สัญญาณเตือน:** ${binancePos.divergenceSignal}")
                appendLine("")
            }

            if (unified.contrarianAlert != null) {
                appendLine("🚨 **${unified.contrarianAlert}**")
                appendLine("")
            }

            if (unified.topHeadlines.isNotEmpty()) {
                appendLine("### 📰 พาดหัวข่าวกระแสหลัก (Market Narratives)")
                unified.topHeadlines.take(3).forEachIndexed { i, post -> appendLine("- **${i + 1}.** $post") }
                appendLine("")
            }

            appendLine("### 🧠 บทวิเคราะห์ & Contrarian Intelligence")
            appendLine(aiSynthesis.trim())
        }
    }

    private fun pillarName(name: String): String = when {
        name.contains("News") -> "📰 ข่าวสาร/โซเชียล"
        name.contains("Market F&G") || name.contains("CNN") -> "🌡️ สภาวะตลาดรวม"
        name.contains("Positioning") -> "🐋 สัญญาอนุพันธ์"
        name.contains("Technical") -> "📊 เทคนิคัลโมเมนตัม"
        else -> name
    }

    /** A pillar's name short enough to stand under a bar on the robot's chart. */
    private fun pillarShortName(name: String): String = when {
        name.contains("News") -> "ข่าว"
        name.contains("Market F&G") || name.contains("CNN") -> "ตลาดรวม"
        name.contains("Positioning") -> "อนุพันธ์"
        name.contains("Technical") -> "เทคนิค"
        else -> name.take(8)
    }

    internal suspend fun executeFearGreed(args: Map<String, String>): String {
        val market = args["market"]?.trim()?.lowercase() ?: "crypto"
        val isStocks = market in listOf("stock", "stocks", "cnn", "us", "sp500", "nasdaq")
        val isAll = market in listOf("all", "both", "global", "macro")

        if (isStocks) {
            val cnn = api.getCnnStockFearAndGreed() ?: return "⚠️ ไม่สามารถดึงข้อมูล CNN Fear & Greed Index ได้ในขณะนี้"
            return formatCnnFearGreed(cnn)
        }

        if (isAll) {
            val cryptoFng = api.getFearGreedIndex(5)
            val cnn = api.getCnnStockFearAndGreed()
            return buildString {
                appendLine("🌐 **Global Dual-Market Fear & Greed Overview (Macro Risk Appetite)**")
                appendLine("=".repeat(55))
                if (cryptoFng["error"] == null) {
                    val v = cryptoFng["value"]?.toIntOrNull() ?: 0
                    val cls = cryptoFng["classification"] ?: "Unknown"
                    appendLine("🪙 **Crypto Market**: $v/100 — $cls")
                }
                if (cnn != null) {
                    appendLine("🏛️ **US Stock Market (CNN)**: ${"%.1f".format(cnn.score)}/100 — ${cnn.ratingThai}")
                }
                appendLine("-".repeat(55))
                if (cnn != null) {
                    appendLine(formatCnnFearGreed(cnn, isCompact = true))
                }
            }
        }

        val fng = api.getFearGreedIndex(7)
        fng["error"]?.let { return "⚠️ ดึง Fear & Greed Index ไม่สำเร็จ ($it)" }
        AvatarToolData.publish("trading_fear_greed", fng)

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
            appendLine("")
            appendLine("💡 *Tip: สามารถเรียกดูดัชนีตลาดหุ้นสหรัฐฯ ได้โดยระบุ market='stocks' หรือดูภาพรวมทั้ง 2 ตลาดด้วย market='all'*")
        }
    }

    private fun formatCnnFearGreed(cnn: CnnFearAndGreedData, isCompact: Boolean = false): String = buildString {
        val emoji = when (cnn.rating.lowercase()) {
            "extreme fear" -> "😱"
            "fear" -> "😨"
            "greed" -> "😃"
            "extreme greed" -> "🤑"
            else -> "😐"
        }
        if (!isCompact) {
            appendLine("🏛️ **CNN Fear & Greed Index — ตลาดหุ้นสหรัฐฯ (Real-time)**")
            appendLine("=".repeat(50))
        }
        appendLine("$emoji **คะแนนดัชนีรวม: ${"%.1f".format(cnn.score)}/100 — ${cnn.ratingThai}**")
        appendLine("📅 ข้อมูลเปรียบเทียบ: ปิดวันก่อน: ${"%.1f".format(cnn.previousClose)} | 1 สัปดาห์ก่อน: ${"%.1f".format(cnn.previous1Week)} | 1 เดือนก่อน: ${"%.1f".format(cnn.previous1Month)}")
        if (cnn.subIndicators.isNotEmpty()) {
            appendLine("")
            appendLine("🔍 **7 ตัวชี้วัดย่อย (Sub-indicators):**")
            cnn.subIndicators.forEach { sub ->
                val subEmoji = when (sub.rating.lowercase()) {
                    "extreme fear" -> "😱"
                    "fear" -> "😨"
                    "greed" -> "😃"
                    "extreme greed" -> "🤑"
                    else -> "😐"
                }
                appendLine("- $subEmoji **${sub.labelThai}**: ${"%.1f".format(sub.score)}/100 (${sub.rating})")
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
            geminiService.generateToolEnrichment(
                prompt = prompt,
                intentAddon = "You are a professional news analyst. Be concise and objective."
            )
        } catch (_: Exception) { "AI analysis unavailable." }
        // the headlines on the robot's card (Avatar mode)
        AvatarToolData.publish("trading_news", mapOf(
            "title" to "ข่าวล่าสุด · ${symbol?.uppercase() ?: "ตลาดโลก"}",
            "markdown" to items.take(5).mapIndexed { i, item ->
                val source = item["source"]?.takeIf { it.isNotBlank() }?.let { " _(${it})_" }.orEmpty()
                "${i + 1}. **${item["title"]?.trim()?.take(90)}**$source"
            }.joinToString("\n")
        ))

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
        AvatarToolData.publish("trading_macro_calendar", mapOf("title" to "ปฏิทินเศรษฐกิจ", "markdown" to calendarCard(events)))

        val aiAnalysis = try {
            geminiService.generateToolEnrichment(
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
            // ForexFactory's free feed has no actual figures — the AI must not read the forecast out as the result
            if (events.any { it["status"] != "UPCOMING" }) {
                appendLine("ℹ️ แหล่งนี้ไม่มีตัวเลขจริง (Actual) ของข่าวที่ประกาศแล้ว — ตัวเลขในรายการเป็นค่าคาดการณ์/ครั้งก่อนเท่านั้น " +
                    "ห้ามบอกว่าเป็นผลจริง ถ้าผู้ใช้ถามผลจริงให้ค้นเว็บ (search_web)")
            }
            if (events.none { it["status"] == "UPCOMING" }) {
                appendLine("ℹ️ ไม่มีข่าวที่รอประกาศในสัปดาห์นี้แล้ว และแหล่งนี้ยังไม่มีปฏิทินสัปดาห์หน้า")
            }
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

    /**
     * The calendar on the robot's card: the coming events first (high impact before medium), then the last
     * ones out; a line each — impact, time (Thai), name, currency, forecast / previous.
     */
    internal fun calendarCard(events: List<Map<String, String>>, max: Int = 5): String {
        fun rank(e: Map<String, String>) = when (e["impact"]?.lowercase()) { "high" -> 0; "medium" -> 1; else -> 2 }
        val (coming, out) = events.partition { it["status"] == "UPCOMING" }
        val picked = (coming.sortedBy { rank(it) }.take(max) + out.take((max - coming.size).coerceAtLeast(0)))
        return picked.joinToString("\n") { e ->
            val dot = when (rank(e)) { 0 -> "🔴"; 1 -> "🟠"; else -> "🟡" }
            // "ศ. 02 ต.ค. 19:30 น. (ไทย) | 08:30 ET" → "ศ. 19:30"
            val parts = e["date_time"].orEmpty().substringBefore(" น.").trim().split(" ")
            val time = if (parts.size >= 4) "${parts[0]} ${parts.last()}" else parts.joinToString(" ")
            val numbers = listOfNotNull(
                e["forecast"]?.takeIf { it.isNotBlank() && it != "-" }?.let { "คาด $it" },
                e["previous"]?.takeIf { it.isNotBlank() && it != "-" }?.let { "ก่อน $it" }
            ).joinToString(" / ")
            val done = if (e["status"] == "UPCOMING") "" else " ✅"
            "- $dot `$time` **${e["title"]}** (${e["country"]})$done" + if (numbers.isNotEmpty()) " · $numbers" else ""
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
            // the scanner's "change" is a percent: it showed bare ("GLMRUSDT - 0.0123 42.2217") and read like a price
            val change = row["change_pct"] ?: row["change"]?.let { c -> c.toDoubleOrNull()?.let { "%+.2f%%".format(it) } ?: c } ?: ""
            val price  = row["price"] ?: row["close"] ?: ""
            val extra  = if (extraKey != null) row[extraKey]?.let { " | $extraKey: $it" } ?: "" else ""
            appendLine("${i + 1}. $symbol - $price $change$extra")
        }
    }

    /** What a scan covers: crypto = USDT spot pairs, the day from TradingView's daily bar (00:00 UTC = 07:00 Thai). */
    internal fun scanScope(exchange: String): String =
        if (exchange.uppercase() in setOf("NASDAQ", "NYSE", "SET", "MAI")) " (แท่งวัน)"
        else " (คู่ USDT spot ไม่รวม stablecoin · แท่งวันเริ่ม 07:00 น. ไทย)"

    internal fun signalEmoji(signal: String) = when (signal) {
        "STRONG BUY"  -> "++"
        "BUY"         -> "+"
        "HOLD"        -> "="
        "SELL"        -> "-"
        "STRONG SELL" -> "--"
        else          -> "?"
    }
}

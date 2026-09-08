package com.example.personalaibot.tools.trading

import com.example.personalaibot.automation.SignalAlertProvider
import com.example.personalaibot.automation.StrategySignalProvider
import com.example.personalaibot.db.JarvisDatabaseHolder
import com.example.personalaibot.db.SignalAlertRecord
import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.logDebug
import io.ktor.client.*
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.datetime.*
import kotlinx.serialization.json.*

/**
 * ResearchToolHandler: Handles strategy signals, backtest analytics, signal live statistics,
 * position sizing, correlation matrices, economic indicators (FRED), crypto global overview,
 * deep analysis suite, and fundamental news synthesis.
 */
internal class ResearchToolHandler(
    private val client: HttpClient,
    private val geminiService: GeminiService,
    private val api: TradingApiService,
    private val advancedEngine: AdvancedTradingEngine,
    private val strategySignalProvider: StrategySignalProvider,
    private val signalAlertProvider: SignalAlertProvider,
    private val backtestHandler: BacktestToolHandler
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var deepSuiteBridgeDownUntilMs: Long = 0L

    private val fredPresets = mapOf(
        "gdp"          to ("GDP" to "GDP สหรัฐฯ (พันล้าน USD, รายไตรมาส)"),
        "gdp_growth"   to ("A191RO1Q156NBEA" to "อัตราการเติบโต GDP (%, รายไตรมาส)"),
        "cpi"          to ("CPIAUCSL" to "ดัชนีราคาผู้บริโภค CPI (Index 1982-84=100)"),
        "core_cpi"     to ("CPILFESL" to "Core CPI — ไม่รวมอาหาร/พลังงาน (Index)"),
        "pce"          to ("PCEPI" to "PCE Price Index — เงินเฟ้อที่ Fed ใช้วัด (Index)"),
        "unemployment" to ("UNRATE" to "อัตราการว่างงาน (%)"),
        "nfp"          to ("PAYEMS" to "Nonfarm Payrolls (พันตำแหน่ง)"),
        "fedfunds"     to ("FEDFUNDS" to "อัตราดอกเบี้ย Fed Funds (%)"),
        "10y"          to ("DGS10" to "Bond Yield 10 ปี (%)"),
        "2y"           to ("DGS2" to "Bond Yield 2 ปี (%)"),
        "m2"           to ("M2SL" to "ปริมาณเงิน M2 (พันล้าน USD)"),
        "retail"       to ("RSAFS" to "ยอดค้าปลีก (ล้าน USD)"),
        "housing"      to ("HOUST" to "Housing Starts (พันยูนิต)"),
        "sentiment"    to ("UMCSENT" to "ความเชื่อมั่นผู้บริโภค U. of Michigan (Index)"),
        "indpro"       to ("INDPRO" to "ดัชนีการผลิตภาคอุตสาหกรรม (Index)"),
        "claims"       to ("ICSA" to "Initial Jobless Claims รายสัปดาห์ (ราย)")
    )

    suspend fun execute(toolName: String, args: Map<String, String>): String = when (toolName) {
        "trading_backtest" -> backtestHandler.executeBacktest(args)
        "trading_backtest_optimize" -> backtestHandler.executeOptimize(args)
        "trading_backtest_evolve" -> backtestHandler.executeEvolve(args)
        "trading_mix_config" -> backtestHandler.executeMixConfig(args)
        "trading_strategy_signal" -> executeStrategySignal(args)
        "trading_signal_stats" -> executeSignalStats(args)
        "trading_position_sizing" -> executePositionSizing(args)
        "trading_correlation_matrix" -> executeCorrelationMatrix(args)
        "trading_economic_data" -> executeEconomicData(args)
        "trading_deep_analysis_suite" -> executeDeepAnalysisSuite(args)
        "trading_fundamental_analysis" -> executeFundamentalAnalysis(args)
        "trading_crypto_overview" -> executeCryptoOverview(args)
        "trading_combined" -> executeCombined(args)
        else -> "Unknown research tool: $toolName"
    }

    internal suspend fun executeStrategySignal(args: Map<String, String>): String {
        val symbol = (args["symbol"] ?: "XAUUSD").trim().uppercase()
        val interval = (args["interval"] ?: "1h").trim().lowercase()
        val strategy = (args["strategy"] ?: "all").trim().lowercase()
        return runCatching {
            if (strategy == "all") {
                strategySignalProvider.fetchMultiTimeframeFormatted(symbol, listOf("15m", "1h", "4h"))
            } else {
                strategySignalProvider.fetchFormatted("$symbol@$interval", strategy)
            }
        }.getOrElse { "❌ Strategy Signal error: ${it.message}" }
    }

    internal suspend fun executeSignalStats(args: Map<String, String>): String {
        val source = (args["source"] ?: "backtest").trim().lowercase()
        if (source == "live") return executeSignalStatsLive(args)
        val symbol = (args["symbol"] ?: "XAUUSD").trim().uppercase()
        val interval = (args["interval"] ?: "1h").trim().lowercase()
        val strategy = (args["strategy"] ?: "all").trim().lowercase()
        return runCatching { signalAlertProvider.fetchStats("$symbol@$interval", strategy) }
            .getOrElse { "❌ Signal Stats error: ${it.message}" }
    }

    internal fun executeSignalStatsLive(args: Map<String, String>): String {
        val mgr = runCatching { JarvisDatabaseHolder.getAutomationManager() }
            .getOrElse { return "❌ ยังเข้าถึงฐานข้อมูลไม่ได้: ${it.message}" }
        val range = (args["range"] ?: "today").trim().lowercase()
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val sinceMs = when (range) {
            "all" -> 0L
            "7d" -> now.minus(7, DateTimeUnit.DAY, tz).toEpochMilliseconds()
            else -> {
                val local = now.toLocalDateTime(tz)
                LocalDateTime(local.year, local.monthNumber, local.dayOfMonth, 0, 0)
                    .toInstant(tz).toEpochMilliseconds()
            }
        }
        val recs = mgr.getSignalAlertsSince(sinceMs)
        val rangeLabel = when (range) { "all" -> "ทั้งหมด"; "7d" -> "7 วันล่าสุด"; else -> "วันนี้" }
        if (recs.isEmpty()) {
            return "📭 ยังไม่มี signal ที่บันทึกไว้ ($rangeLabel)\n" +
                "ระบบจะบันทึกอัตโนมัติเมื่อ Signal Alert (trading_signal_alert) ยิงแจ้งเตือน — ลองตั้ง alert แล้วรอ signal เกิด"
        }

        fun fmtTime(ms: Long): String {
            val l = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
            return "%02d:%02d".format(l.hour, l.minute)
        }
        fun fmtDate(ms: Long): String {
            val l = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
            return "%02d/%02d %02d:%02d".format(l.dayOfMonth, l.monthNumber, l.hour, l.minute)
        }
        val showDate = range != "today"

        return buildString {
            appendLine("📊 **Signal Stats จาก Alert จริง — $rangeLabel** (${recs.size} สัญญาณ)")
            appendLine()
            recs.take(20).forEach { r ->
                val icon = if (r.side == "BUY") "🟢" else "🔴"
                val outcome = when (r.outcome) {
                    "TP" -> "✅ TP ${"%+.2f".format(r.result_r ?: 0.0)}R @ ${r.hit_price}"
                    "SL" -> "❌ SL −1R @ ${r.hit_price}"
                    else -> "⏳ กำลังวิ่ง"
                }
                val whenStr = if (showDate) fmtDate(r.created_at) else fmtTime(r.created_at)
                appendLine("$icon **${r.side}** ${r.symbol} @ ${r.entry} (${r.strategy}) — $outcome ・ $whenStr")
                appendLine("   SL ${r.sl} / TP ${r.tp} (RR 1:${r.rr?.let { "%.2f".format(it) } ?: "-"})")
            }
            if (recs.size > 20) appendLine("… และอีก ${recs.size - 20} รายการ")
            appendLine()

            fun summarize(list: List<SignalAlertRecord>): String {
                val tp = list.count { it.outcome == "TP" }
                val sl = list.count { it.outcome == "SL" }
                val openN = list.count { it.outcome == "OPEN" }
                val decided = tp + sl
                val wr = if (decided > 0) "%.0f%%".format(tp * 100.0 / decided) else "-"
                val avgR = if (decided > 0) list.filter { it.outcome != "OPEN" }
                    .sumOf { it.result_r ?: 0.0 } / decided else 0.0
                return "${list.size} สัญญาณ | TP $tp SL $sl วิ่งอยู่ $openN | win-rate $wr | avg ${"%+.2f".format(avgR)}R"
            }
            appendLine("**รวม: **${summarize(recs)}")
            appendLine()
            appendLine("**แยกตามกลยุทธ์:**")
            recs.groupBy { it.strategy }.toList()
                .sortedByDescending { (_, l) -> l.filter { it.outcome != "OPEN" }.sumOf { it.result_r ?: 0.0 } }
                .forEach { (name, list) -> appendLine("• **$name**: ${summarize(list)}") }

            appendLine()
            appendLine("🧠 **Closed-Loop Strategy Reinforcement Status (สถานะการปรับน้ำหนัก AI):**")
            val strategies = recs.map { it.strategy }.distinct()
            strategies.forEach { strat ->
                val perf = com.example.personalaibot.automation.SignalOutcomeTracker.getStrategyPerformance(strat)
                val adj = com.example.personalaibot.automation.SignalOutcomeTracker.getStrategyConfidenceAdjustment(strat)
                val statusBadge = when {
                    adj > 0.15 -> "🔥 HOT (+${"%.2f".format(adj)} Boost — เพิ่มน้ำหนัก)"
                    adj > 0.0 -> "📈 FAVORABLE (+${"%.2f".format(adj)} Boost)"
                    adj < -0.20 -> "❄️ COLD (${"%.2f".format(adj)} Penalty — สกัดกั้นสัญญาณอ่อน)"
                    adj < 0.0 -> "⚠️ UNFAVORABLE (${"%.2f".format(adj)} Penalty)"
                    else -> "⚖️ NEUTRAL (+0.00)"
                }
                if (perf != null && (perf.wins + perf.losses > 0)) {
                    appendLine("• **$strat**: WR ${"%.1f".format(perf.winRatePct)}% | Avg ${"%+.2f".format(perf.avgR)}R | MFE ${"%.2f".format(perf.avgMfeR)}R | MAE ${"%.2f".format(perf.avgMaeR)}R → $statusBadge")
                } else {
                    appendLine("• **$strat**: $statusBadge")
                }
            }
        }.trim()
    }

    internal fun executePositionSizing(args: Map<String, String>): String {
        val balance = args["balance"]?.toDoubleOrNull() ?: 10_000.0
        val riskPct = args["risk_pct"]?.toDoubleOrNull() ?: 1.0
        val entry   = args["entry"]?.toDoubleOrNull() ?: return "Missing entry"
        val stopLoss = args["stop_loss"]?.toDoubleOrNull() ?: return "Missing stop_loss"

        val riskPerUnit = kotlin.math.abs(entry - stopLoss)
        if (riskPerUnit == 0.0) return "❌ entry กับ stop_loss ต้องไม่เท่ากัน"
        if (riskPct <= 0.0 || riskPct > 100.0) return "❌ risk_pct ต้องอยู่ระหว่าง 0–100"

        val riskAmount   = balance * riskPct / 100.0
        val units        = riskAmount / riskPerUnit
        val positionValue = units * entry
        val leverage     = positionValue / balance
        val stopPct      = riskPerUnit / entry * 100.0
        val direction    = if (stopLoss < entry) "LONG" else "SHORT"

        return buildString {
            append("## 📐 Position Sizing Calculator\n\n")
            append("- **ทิศทาง**: $direction (entry $entry, stop $stopLoss)\n")
            append("- **เงินทุน**: ${"%,.2f".format(balance)}\n")
            append("- **ความเสี่ยงต่อไม้**: ${"%.2f".format(riskPct)}% = ${"%,.2f".format(riskAmount)}\n")
            append("- **ระยะ Stop Loss**: ${"%.4f".format(riskPerUnit)} (${"%.2f".format(stopPct)}% จาก entry)\n\n")
            append("### ✅ ผลลัพธ์\n")
            append("- **ขนาดที่ควรเปิด (Units)**: ${"%,.4f".format(units)}\n")
            append("- **มูลค่าสัญญา (Notional)**: ${"%,.2f".format(positionValue)}\n")
            append("- **Leverage ที่ต้องใช้**: ${"%.2f".format(leverage)}x\n\n")
            if (leverage > 1.0) {
                append("⚠️ ไม้นี้ต้องใช้ leverage ${"%.2f".format(leverage)}x — หากโบรกเกอร์จำกัด leverage ")
                append("ให้ลดขนาดเหลือ ${"%,.4f".format(balance / riskPerUnit)} units (เสี่ยง ${"%,.2f".format(balance * stopPct / 100)})\n")
            }
        }
    }

    internal suspend fun executeCorrelationMatrix(args: Map<String, String>): String {
        val symbols = (args["symbols"] ?: "BTC-USD,GC=F,^GSPC,EURUSD=X")
            .split(",").map { it.trim() }.filter { it.isNotBlank() }.take(6)
        if (symbols.size < 2) return "❌ ต้องระบุอย่างน้อย 2 symbols (คั่นด้วย comma)"
        val days = (args["days"]?.toIntOrNull() ?: 30).coerceIn(7, 365)

        val seriesMap = mutableMapOf<String, List<Double>>()
        val failed = mutableListOf<String>()
        symbols.forEach { sym ->
            val closes = fetchYahooDailyCloses(sym, days)
            if (closes != null && closes.size >= 5) seriesMap[sym] = closes else failed.add(sym)
        }
        if (seriesMap.size < 2) {
            return "❌ ดึงข้อมูลย้อนหลังไม่สำเร็จสำหรับ: ${(failed + seriesMap.keys.filter { seriesMap[it]!!.size < 5 }).joinToString(", ")}"
        }

        val returnsMap = seriesMap.mapValues { (_, closes) ->
            closes.zipWithNext { a, b -> if (a != 0.0) (b - a) / a else 0.0 }
        }
        val minLen = returnsMap.values.minOf { it.size }
        val aligned = returnsMap.mapValues { (_, r) -> r.takeLast(minLen) }

        val keys = aligned.keys.toList()
        return buildString {
            append("## 🔗 Correlation Matrix (daily returns, ${minLen} วัน)\n\n")
            keys.forEach { append("- **$it**\n") }
            append("\n| | ${keys.joinToString(" | ")} |\n")
            append("|${"---|".repeat(keys.size + 1)}\n")
            keys.forEach { a ->
                append("| $a |")
                keys.forEach { b ->
                    val c = pearson(aligned[a]!!, aligned[b]!!)
                    append(" ${"%.2f".format(c)} |")
                }
                append("\n")
            }
            append("\nอ่านค่า: ใกล้ 1.0 = เคลื่อนไหวทิศเดียวกัน, ใกล้ -1.0 = สวนทางกัน, ใกล้ 0 = ไม่เกี่ยวข้อง\n")
            if (failed.isNotEmpty()) append("\n⚠️ ข้าม symbol ที่ดึงข้อมูลไม่ได้: ${failed.joinToString(", ")}")
        }
    }

    internal suspend fun fetchYahooDailyCloses(symbol: String, days: Int): List<Double>? {
        return try {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol"
            val response = client.get(url) {
                url {
                    parameters.append("interval", "1d")
                    parameters.append("range", "${days}d")
                }
                header("User-Agent", "Mozilla/5.0")
            }
            if (!response.status.value.toString().startsWith("2")) return null
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val result = root["chart"]?.jsonObject?.get("result")?.jsonArray?.firstOrNull()?.jsonObject
                ?: return null
            val closes = result["indicators"]?.jsonObject?.get("quote")?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("close")?.jsonArray ?: return null
            closes.mapNotNull { it.jsonPrimitive.doubleOrNull }
        } catch (e: Exception) {
            logDebug("TradingExecutor", "fetchYahooDailyCloses($symbol) failed: ${e.message}")
            null
        }
    }

    internal fun pearson(x: List<Double>, y: List<Double>): Double {
        val n = minOf(x.size, y.size)
        if (n < 3) return 0.0
        val xs = x.take(n); val ys = y.take(n)
        val meanX = xs.average(); val meanY = ys.average()
        var num = 0.0; var denX = 0.0; var denY = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX; val dy = ys[i] - meanY
            num += dx * dy; denX += dx * dx; denY += dy * dy
        }
        val den = kotlin.math.sqrt(denX * denY)
        return if (den == 0.0) 0.0 else num / den
    }

    internal suspend fun executeEconomicData(args: Map<String, String>): String {
        val seriesArg = args["series"]?.trim()?.lowercase()
            ?: return "❌ ต้องระบุ series (เช่น gdp, cpi, unemployment, fedfunds, overview)"
        val limit = (args["limit"]?.toIntOrNull() ?: 12).coerceIn(3, 60)
        val apiKey = args["api_key"]?.trim().takeUnless { it.isNullOrBlank() }

        if (seriesArg in listOf("overview", "all", "ภาพรวม")) {
            return executeEconomicOverview(limit, apiKey)
        }

        val (seriesId, label) = fredPresets[seriesArg]
            ?: (seriesArg.uppercase() to "FRED Series: ${seriesArg.uppercase()}")

        val obs = api.getFredSeriesObservations(seriesId, apiKey)
            ?: return "❌ ดึงข้อมูล $seriesId จาก FRED ไม่สำเร็จ (series id อาจไม่ถูกต้อง หรือ FRED ไม่ตอบสนอง)"

        return formatFredReport(seriesId, label, obs.takeLast(limit))
    }

    internal suspend fun executeEconomicOverview(limit: Int, apiKey: String?): String {
        val keys = listOf("gdp_growth", "cpi", "unemployment", "fedfunds")
        val sb = StringBuilder("## 🇺🇸 ภาพรวมเศรษฐกิจสหรัฐฯ (FRED Overview)\n\n")
        keys.forEach { key ->
            val (id, label) = fredPresets.getValue(key)
            val obs = api.getFredSeriesObservations(id, apiKey)
            if (obs == null) {
                sb.append("- **$label**: ดึงข้อมูลไม่สำเร็จ\n")
            } else {
                val w = obs.takeLast(limit)
                val latest = w.last()
                val prev = w.dropLast(1).lastOrNull()
                val trend = if (prev != null) {
                    val d = latest.second - prev.second
                    when {
                        d > 0 -> "🔺 +${"%.2f".format(d)}"
                        d < 0 -> "🔻 ${"%.2f".format(d)}"
                        else -> "➖ คงที่"
                    }
                } else "—"
                sb.append("- **$label**: ${"%,.2f".format(latest.second)} (${latest.first}) $trend จากช่วงก่อน\n")
            }
        }
        sb.append("\n_ดูรายละเอียดแต่ละตัวได้ด้วย trading_economic_data + series เฉพาะทาง_")
        return sb.toString()
    }

    internal fun formatFredReport(seriesId: String, label: String, window: List<Pair<String, Double>>): String {
        val latest = window.last()
        val prev = window.dropLast(1).lastOrNull()
        val change = prev?.let { latest.second - it.second }
        val changePct = prev?.takeIf { it.second != 0.0 }?.let { (latest.second - it.second) / it.second * 100.0 }
        val hi = window.maxOf { it.second }
        val lo = window.minOf { it.second }

        return buildString {
            append("## 🇺🇸 $label\n")
            append("**FRED Series:** $seriesId | **Source:** Federal Reserve Economic Data (FRED)\n\n")
            append("### ค่าล่าสุด\n")
            append("- **${"%,.2f".format(latest.second)}** (ณ ${latest.first})\n")
            if (change != null) {
                val arrow = if (change > 0) "🔺" else if (change < 0) "🔻" else "➖"
                append("- เปลี่ยนจากช่วงก่อน (${prev!!.first}): $arrow ${"%+.2f".format(change)}")
                changePct?.let { append(" (${"%+.2f".format(it)}%)") }
                append("\n")
            }
            append("- กรอบ ${window.size} ช่วง: ต่ำสุด ${"%,.2f".format(lo)} — สูงสุด ${"%,.2f".format(hi)}\n\n")
            append("### อนุกรมย้อนหลัง\n")
            window.asReversed().forEach { (date, v) ->
                append("- $date : ${"%,.2f".format(v)}\n")
            }
        }
    }

    internal suspend fun executeDeepAnalysisSuite(args: Map<String, String>): String {
        val symbol = args["symbol"] ?: return "Missing symbol"
        val tf = args["timeframe"] ?: args["interval"] ?: "1h"

        val nowMs = Clock.System.now().toEpochMilliseconds()
        if (nowMs < deepSuiteBridgeDownUntilMs) {
            logDebug("TradingTool", "Deep analysis bridge circuit OPEN — skip HTTP, using local engine directly")
            return runLocalDeepSuite(symbol, tf, "bridge circuit open")
        }

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
            val invalid = response.status.value !in 200..299 ||
                body.contains("ERR_NGROK") || body.contains("ngrok") && body.contains("offline") ||
                body.trimStart().startsWith("<")
            if (invalid) {
                throw Exception("bridge unreachable (HTTP ${response.status.value}, ngrok/html error page)")
            }
            deepSuiteBridgeDownUntilMs = 0L
            "═══ Deep Analysis Suite: $symbol $tf ═══\n$body"
        } catch (e: Exception) {
            deepSuiteBridgeDownUntilMs = Clock.System.now().toEpochMilliseconds() + 10 * 60_000L
            runLocalDeepSuite(symbol, tf, e.message ?: "bridge failed")
        }
    }

    internal suspend fun runLocalDeepSuite(symbol: String, tf: String, reason: String): String {
        logDebug("TradingTool", "Deep analysis using local engine ($reason)")
        val result = try {
            advancedEngine.analyze(symbol, tf.lowercase())
        } catch (e2: Exception) {
            logDebug("TradingTool", "Local deep analysis failed: ${e2.message}")
            null
        }
        if (result == null) {
            return "⚠️ Deep analysis ไม่สำเร็จทั้ง bridge และ local engine ($reason)"
        }
        return buildString {
            appendLine("═══ Deep Analysis Suite (Local Engine): ${result.symbol} ${result.interval} ═══")
            appendLine("💵 ราคาปัจจุบัน: ${result.currentPrice}")
            appendLine("🏛️ LSD Trend: ${result.lsdTrend.state} (Confluence TF: ${result.lsdTrend.confluenceTF}/4)")
            appendLine("📦 Orderflow: ${result.orderflow.deltaLabel} (Δ ${"%.2f".format(result.orderflow.lastDelta)})")
            appendLine("🌀 Momentum: ${result.momentum.signal}${if (result.momentum.isSqueeze) " (SQUEEZE!)" else ""}")
            val hot = result.fiboStrength.filter { it.isHot }
            if (hot.isNotEmpty()) {
                appendLine("🎯 Fibo Hot Zones: " + hot.joinToString { "${it.label} (${it.score}/10)" })
            }
            appendLine("⭐ Summary Score: ${result.summaryScore}/100")
        }
    }

    internal suspend fun executeFundamentalAnalysis(args: Map<String, String>): String {
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

    internal suspend fun executeCryptoOverview(@Suppress("UNUSED_PARAMETER") args: Map<String, String>): String {
        val global = api.getCryptoGlobal()
        global["error"]?.let { return "⚠️ ดึงข้อมูลตลาดคริปโตไม่สำเร็จ ($it)" }

        fun fmtUsd(raw: String): String {
            val v = raw.toDoubleOrNull() ?: return raw
            return when {
                v >= 1e12 -> "$${"%.2f".format(v / 1e12)}T"
                v >= 1e9  -> "$${"%.2f".format(v / 1e9)}B"
                v >= 1e6  -> "$${"%.2f".format(v / 1e6)}M"
                else -> "$${"%,.0f".format(v)}"
            }
        }

        val mcapChange = global["market_cap_change_24h"]?.toDoubleOrNull()
        val btcDom = global["btc_dominance"]?.toDoubleOrNull()
        val ethDom = global["eth_dominance"]?.toDoubleOrNull()
        val trending = api.getCryptoTrending().take(5)
        val fng = api.getFearGreedIndex(1)

        return buildString {
            appendLine("🪙 **ภาพรวมตลาดคริปโต (CoinGecko — Real-time)**")
            appendLine("=".repeat(45))
            appendLine("💰 Market Cap รวม: **${fmtUsd(global["total_market_cap_usd"] ?: "-")}** (${if ((mcapChange ?: 0.0) >= 0) "+" else ""}${"%.2f".format(mcapChange ?: 0.0)}% / 24h)")
            appendLine("📊 Volume 24h: ${fmtUsd(global["total_volume_24h_usd"] ?: "-")}")
            appendLine("₿ BTC Dominance: ${"%.1f".format(btcDom ?: 0.0)}%  |  Ξ ETH Dominance: ${"%.1f".format(ethDom ?: 0.0)}%")
            appendLine("🏷️ เหรียญที่ active: ${global["active_cryptocurrencies"]}  |  ตลาด: ${global["markets"]}")
            if (mcapChange != null) {
                val mood = when {
                    mcapChange >= 2.0 -> "🟢 ตลาดเขียวแข็งแรง เงินไหลเข้า"
                    mcapChange >= 0.0 -> "🟢 ตลาดเขียวเล็กน้อย"
                    mcapChange <= -2.0 -> "🔴 ตลาดแดงแรง เงินไหลออก ระวังแรงขายต่อเนื่อง"
                    else -> "🔴 ตลาดแดงเล็กน้อย"
                }
                appendLine("🧭 สภาพตลาด: $mood")
            }
            if (fng["error"] == null) {
                appendLine("🌡️ Fear & Greed: ${fng["value"]}/100 (${fng["classification"]})")
            }
            if (trending.isNotEmpty()) {
                appendLine()
                appendLine("🔥 **เหรียญ Trending (คนค้นหามากสุด):**")
                trending.forEach { t ->
                    appendLine("- ${t["name"]} (${t["symbol"]}) — Rank #${t["market_cap_rank"]}")
                }
            }
        }
    }

    internal suspend fun executeCombined(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "Missing required argument: symbol"
        val exchange = args["exchange"] ?: "BINANCE"
        val interval = args["interval"] ?: "1h"

        val ta        = api.getTechnicalAnalysis(symbol, exchange, interval)
        val sentiment = api.getRedditSentiment(symbol.removeSuffix("USDT"))
        val news      = api.getFinancialNews(symbol.removeSuffix("USDT"), 3) as? Map<String, Any> ?: emptyMap()

        val taSignal   = ta["signal"]?.toString() ?: "N/A"
        val sentLabel  = sentiment["sentiment_label"]?.toString() ?: "N/A"
        val sentScore  = sentiment["sentiment_score"]?.toString()?.toDoubleOrNull() ?: 0.0

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
}

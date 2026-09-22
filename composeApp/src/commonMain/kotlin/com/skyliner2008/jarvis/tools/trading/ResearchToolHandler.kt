package com.skyliner2008.jarvis.tools.trading

import com.skyliner2008.jarvis.automation.SignalAlertProvider
import com.skyliner2008.jarvis.automation.StrategySignalProvider
import com.skyliner2008.jarvis.db.JarvisDatabaseHolder
import com.skyliner2008.jarvis.db.SignalAlertRecord
import com.skyliner2008.jarvis.data.GeminiService
import com.skyliner2008.jarvis.logDebug
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
        "trading_signal_data_export" -> executeSignalDataExport(args)
        "trading_signal_config_import" -> executeSignalConfigImport(args)
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
                val perf = com.skyliner2008.jarvis.automation.SignalOutcomeTracker.getStrategyPerformance(strat)
                val adj = com.skyliner2008.jarvis.automation.SignalOutcomeTracker.getStrategyConfidenceAdjustment(strat)
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

    /**
     * contract size มาตรฐานต่อ 1 lot สำหรับสัญลักษณ์ที่รู้จัก
     * คืน null เมื่อไม่แน่ใจ — ให้ผู้ใช้/AI ระบุเอง ดีกว่าเดาแล้วได้ lot ผิดสเกล
     */
    internal fun defaultContractSize(symbol: String?): Double? {
        val s = symbol?.uppercase()?.replace("/", "")?.replace("-", "") ?: return null
        val fiat = setOf("USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "NZD")
        return when {
            s.contains("XAU") || s.contains("GOLD") -> 100.0    // 100 ทรอยออนซ์
            s.contains("XAG") || s.contains("SILVER") -> 5_000.0 // 5,000 ทรอยออนซ์
            s.length == 6 && s.take(3) in fiat && s.drop(3) in fiat -> 100_000.0 // FX standard lot
            else -> null
        }
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
        val gearing      = positionValue / balance
        val stopPct      = riskPerUnit / entry * 100.0
        val direction    = if (stopLoss < entry) "LONG" else "SHORT"

        // contract size = จำนวนหน่วยต่อ 1 lot (XAUUSD = 100 oz, FX = 100,000)
        // ระบุมาเมื่อไหร่จึงจะแปลงเป็น lot ที่ใช้กับ MT5 ได้จริง
        val contractSize = args["contract_size"]?.toDoubleOrNull()?.takeIf { it > 0 }
            ?: defaultContractSize(args["symbol"])
        val lots = contractSize?.let { units / it }
        val lotStep = args["lot_step"]?.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.01
        val roundedLots = lots?.let { kotlin.math.floor(it / lotStep + 1e-9) * lotStep }

        return buildString {
            append("## 📐 Position Sizing Calculator\n\n")
            append("- **ทิศทาง**: $direction (entry $entry, stop $stopLoss)\n")
            append("- **เงินทุน**: ${"%,.2f".format(balance)}\n")
            append("- **ความเสี่ยงต่อไม้**: ${"%.2f".format(riskPct)}% = ${"%,.2f".format(riskAmount)}\n")
            append("- **ระยะ Stop Loss**: ${"%.4f".format(riskPerUnit)} (${"%.2f".format(stopPct)}% จาก entry)\n\n")
            append("### ✅ ผลลัพธ์\n")
            append("- **ขนาดที่ควรเปิด**: ${"%,.4f".format(units)} units")
            if (contractSize != null) append(" (contract size ${"%,.0f".format(contractSize)}/lot)")
            append("\n")
            if (roundedLots != null) {
                if (roundedLots >= lotStep) {
                    val actualRisk = roundedLots * contractSize!! * riskPerUnit
                    append("- **ขนาดสำหรับ MT5**: **${"%.2f".format(roundedLots)} lot** ")
                    append("(ปัดลงตาม lot step $lotStep — เสี่ยงจริง ${"%,.2f".format(actualRisk)} = ")
                    append("${"%.2f".format(actualRisk / balance * 100.0)}% ของพอร์ต)\n")
                } else {
                    append("- ⚠️ **ขนาดที่คำนวณได้เล็กกว่า lot ขั้นต่ำ ($lotStep)** — ")
                    append("ต้องลดระยะ SL, เพิ่มเงินทุน หรือยอมรับความเสี่ยงสูงกว่า ${"%.2f".format(riskPct)}%\n")
                }
            } else {
                append("- ℹ️ ระบุ `contract_size` เพื่อให้คำนวณเป็น lot สำหรับ MT5 ")
                append("(เช่น XAUUSD = 100, คู่เงิน FX = 100000)\n")
            }
            append("- **มูลค่าสัญญา (Notional)**: ${"%,.2f".format(positionValue)}\n")
            append("- **Gearing (notional ÷ เงินทุน)**: ${"%.2f".format(gearing)}x\n\n")
            if (gearing > 1.0) {
                append("⚠️ มูลค่าสัญญาสูงกว่าเงินทุน ${"%.2f".format(gearing)} เท่า — ")
                append("ต้องมี margin/leverage รองรับ ถ้าโบรกเกอร์จำกัด leverage ไว้ต่ำกว่านี้ ")
                append("จะเปิดไม้ขนาดนี้ไม่ได้ ต้องลดขนาดลง (ซึ่งจะทำให้เสี่ยงน้อยกว่า ${"%.2f".format(riskPct)}% ตามไปด้วย) ")
                append("หรือย้าย stop ให้ใกล้ขึ้นเพื่อให้ได้ขนาดที่เล็กลงโดยคงความเสี่ยงเท่าเดิม\n")
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
        val rawSymbol = (args["symbol"] ?: args["ticker"])?.trim()
        val exchange = args["exchange"]?.trim()

        if (rawSymbol.isNullOrBlank()) {
            return "⚠️ โปรดระบุชื่อหุ้นหรือสินทรัพย์ที่ต้องการวิเคราะห์ เช่น SCB, PTT, CPALL, AAPL, NVDA"
        }

        return try {
            val fundamental = api.getStockFundamentals(rawSymbol, exchange)

            if (fundamental == null) {
                // Fallback กรณีหาหุ้นไม่พบใน Scanner (เช่น อาจเป็นเหรียญ Crypto หรือดัชนี)
                val data = api.getFinancialNews(rawSymbol, 8) as? Map<String, Any> ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val items = data["news"] as? List<Map<String, String>> ?: emptyList()

                if (items.isEmpty()) {
                    return "❌ ไม่พบข้อมูลปัจจัยพื้นฐานหรือข้อมูลงบการเงินของสัญลักษณ์ \"$rawSymbol\" (รองรับหุ้นไทย SET/MAI และหุ้นสหรัฐฯ NASDAQ/NYSE)"
                }

                val newsText = items.joinToString("\n") { "- ${it["title"]}: ${it["description"]}" }
                val fallbackPrompt = """
                    Analyze the following financial context for $rawSymbol:
                    $newsText
                    
                    Please evaluate:
                    1. Core drivers and current financial position
                    2. Bullish and Bearish factors
                    3. Fundamental Health Score (0-100)
                """.trimIndent()

                val aiText = runCatching {
                    geminiService.generateResponse(
                        prompt = fallbackPrompt,
                        intentAddon = "You are a senior equity research analyst."
                    )
                }.getOrElse { "AI analysis unavailable." }

                return buildString {
                    appendLine("🏛️ **วิเคราะห์ปัจจัยพื้นฐาน - ${rawSymbol.uppercase()}**")
                    appendLine("⚠️ *ไม่พบข้อมูลงบการเงินแบบตารางของ $rawSymbol (แสดงบทวิเคราะห์จากข่าวสารการเงิน)*")
                    appendLine("=".repeat(40))
                    appendLine(aiText)
                }
            }

            // ซิงค์สัญลักษณ์หุ้นเข้าสู่ระบบกราฟและแดชบอร์ด
            ChartStateManager.updateSymbol(fundamental.ticker)

            // จัดเตรียมข้อเท็จจริงและตัวเลขทางการเงิน
            val cur = fundamental.currency.ifBlank { "THB" }
            val priceStr = fundamental.closePrice?.let { "%.2f".format(it) } ?: "N/A"
            val changeStr = fundamental.changePrice?.let { "%+.2f".format(it) } ?: "0.00"
            val changePctStr = fundamental.changePct?.let { "%+.2f%%".format(it) } ?: "0.00%"

            val capStr = StockFundamentalData.formatMoneyCompact(fundamental.marketCap, cur)
            val evStr = StockFundamentalData.formatMoneyCompact(fundamental.enterpriseValue, cur)
            val debtStr = StockFundamentalData.formatMoneyCompact(fundamental.totalDebt, cur)
            val cashStr = StockFundamentalData.formatMoneyCompact(fundamental.cashAndEquivalents, cur)
            val netDebtStr = StockFundamentalData.formatMoneyCompact(fundamental.netDebt, cur)
            val equityStr = StockFundamentalData.formatMoneyCompact(fundamental.totalEquity, cur)
            val assetsStr = StockFundamentalData.formatMoneyCompact(fundamental.totalAssets, cur)
            val liabStr = StockFundamentalData.formatMoneyCompact(fundamental.totalLiabilities, cur)

            val revTtmStr = StockFundamentalData.formatMoneyCompact(fundamental.totalRevenueTtm, cur)
            val revFyStr = StockFundamentalData.formatMoneyCompact(fundamental.totalRevenueFy, cur)
            val revFqStr = StockFundamentalData.formatMoneyCompact(fundamental.totalRevenueFq, cur)
            val niTtmStr = StockFundamentalData.formatMoneyCompact(fundamental.netIncomeTtm, cur)
            val niFyStr = StockFundamentalData.formatMoneyCompact(fundamental.netIncomeFy, cur)
            val niFqStr = StockFundamentalData.formatMoneyCompact(fundamental.netIncomeFq, cur)
            val fcfStr = StockFundamentalData.formatMoneyCompact(fundamental.freeCashFlowTtm, cur)

            val peStr = StockFundamentalData.formatRatio(fundamental.peTtm)
            val psStr = StockFundamentalData.formatRatio(fundamental.psCurrent)
            val pbStr = StockFundamentalData.formatRatio(fundamental.pbFq)
            val pfcfStr = StockFundamentalData.formatRatio(fundamental.pfcfTtm)
            val evRevStr = StockFundamentalData.formatRatio(fundamental.evToRevenueTtm)
            val deStr = StockFundamentalData.formatRatio(fundamental.debtToEquity)

            val epsStr = fundamental.epsBasicTtm?.let { "${"%.2f".format(it)} $cur" } ?: "N/A"
            val divYieldStr = StockFundamentalData.formatPercent(fundamental.dividendYieldCurrent)
            val dpsStr = fundamental.dpsFy?.let { "${"%.2f".format(it)} $cur" } ?: "N/A"

            val totalSharesStr = StockFundamentalData.formatSharesCompact(fundamental.totalShares)
            val floatSharesStr = StockFundamentalData.formatSharesCompact(fundamental.floatShares)
            val floatPctStr = StockFundamentalData.formatPercent(fundamental.floatPct)
            val closeSharesStr = StockFundamentalData.formatSharesCompact(fundamental.closelyHeldShares)
            val closePctStr = StockFundamentalData.formatPercent(fundamental.closelyHeldPct)

            val opMarginStr = StockFundamentalData.formatPercent(fundamental.operatingMarginTtm)
            val netMarginStr = StockFundamentalData.formatPercent(fundamental.netMarginTtm)
            val roeStr = StockFundamentalData.formatPercent(fundamental.returnOnEquity)
            val roaStr = StockFundamentalData.formatPercent(fundamental.returnOnAssets)
            val roicStr = StockFundamentalData.formatPercent(fundamental.returnOnInvestedCapital)

            val ptAvgStr = fundamental.targetPriceAvg?.let { "${"%.2f".format(it)} $cur" } ?: "N/A"
            val ptHighStr = fundamental.targetPriceHigh?.let { "${"%.2f".format(it)} $cur" } ?: "N/A"
            val ptLowStr = fundamental.targetPriceLow?.let { "${"%.2f".format(it)} $cur" } ?: "N/A"
            val upsideStr = fundamental.upsidePct?.let { "%+.2f%%".format(it) } ?: "N/A"

            val prompt = """
                คุณคือ Senior Fundamental & Equity Research Analyst ที่เชี่ยวชาญการวิเคราะห์งบการเงินและการประเมินมูลค่าหุ้น
                โปรดวิเคราะห์ข้อมูลปัจจัยพื้นฐานและงบการเงินเชิงลึกของหุ้น ${fundamental.name} (${fundamental.ticker}):

                【ข้อมูลทางการเงินจริง】
                - ราคาปัจจุบัน: $priceStr $cur ($changeStr, $changePctStr) | 52W: ${fundamental.low52w ?: "N/A"} - ${fundamental.high52w ?: "N/A"}
                - ภาคธุรกิจ/อุตสาหกรรม: ${fundamental.sector} / ${fundamental.industry} (${fundamental.country})
                - มูลค่าตลาด (Market Cap): $capStr | มูลค่ากิจการ (EV): $evStr
                - อัตราส่วนมูลค่า: P/E TTM = $peStr, P/S = $psStr, P/B = $pbStr, P/FCF = $pfcfStr, EV/Revenue = $evRevStr
                - กำไรและปันผล: EPS TTM = $epsStr, Dividend Yield = $divYieldStr, เงินปันผลต่อหุ้นล่าสุด = $dpsStr
                - สัดส่วนผู้ถือหุ้น: หุ้นทั้งหมด $totalSharesStr, รายย่อย (Free Float) = $floatSharesStr ($floatPctStr), ผู้ถือหุ้นใหญ่/กลุ่มเฉพาะ = $closeSharesStr ($closePctStr)
                - โครงสร้างทุนและงบดุล: หนี้สินรวม = $debtStr, เงินสด = $cashStr, หนี้สินสุทธิ = $netDebtStr, ส่วนผู้ถือหุ้น = $equityStr, สินทรัพย์รวม = $assetsStr, หนี้สิน/ทุน (D/E) = $deStr
                - ผลการดำเนินงาน: รายได้รวม TTM = $revTtmStr (FQ: $revFqStr, FY: $revFyStr), กำไรสุทธิ TTM = $niTtmStr (FQ: $niFqStr, FY: $niFyStr), Free Cash Flow = $fcfStr
                - ประสิทธิภาพและผลตอบแทน: Operating Margin = $opMarginStr, Net Margin = $netMarginStr, ROE = $roeStr, ROA = $roaStr, ROIC = $roicStr
                - เป้าหมายนักวิเคราะห์: Target Price เฉลี่ย = $ptAvgStr (Upside: $upsideStr, กรอบ: $ptLowStr - $ptHighStr)

                โปรดทำการวิเคราะห์เชิงลึกโดยแบ่งหัวข้ออย่างชัดเจนดังนี้:
                1. 📊 **การประเมินมูลค่า (Valuation & Price Attractiveness)**: วิเคราะห์ระดับราคาเมื่อเทียบกับ P/E, P/B, P/S และ EV/Revenue เทียบกับธรรมชาติกลุ่มอุตสาหกรรม และ Upside จากเป้าหมายนักวิเคราะห์
                2. 🏛️ **โครงสร้างเงินทุนและสุขภาพงบดุล (Capital Structure & Solvency)**: วิเคราะห์ระดับหนี้สิน สภาพคล่องเงินสด ความปลอดภัยของ D/E และความเสี่ยงทางการเงิน
                3. 💰 **คุณภาพกำไรและประสิทธิภาพการดำเนินงาน (Profitability & Cash Flow)**: วิเคราะห์ Margins, ROE, ROA และความสามารถในการแปลงกำไรเป็นกระแสเงินสดอิสระ (FCF)
                4. 🎁 **ความยั่งยืนของเงินปันผล (Dividend Quality & Safety)**: วิเคราะห์ Yield เทียบกับ FCF และกำไรต่อหุ้น มีโอกาสรักษาหรือเพิ่มเงินปันผลได้หรือไม่
                5. 👥 **โครงสร้างการถือหุ้นและจิตวิทยาตลาด (Ownership & Float Dynamics)**: วิเคราะห์สัดส่วน Free Float ต่อสภาพคล่องและความผันผวน
                6. 🎯 **บทสรุปคะแนนพื้นฐานและคำแนะนำเชิงกลยุทธ์ (Fundamental Health Score & Strategic Outlook)**:
                   - ระบุ **Fundamental Health Score: [0-100]/100**
                   - สรุป **Bullish Factors (จุดเด่น)** และ **Bearish Risks (ความเสี่ยงที่ต้องระวัง)**
                   - สรุปคำแนะนำเชิงกลยุทธ์สำหรับนักลงทุน

                ตอบด้วยภาษาไทยที่กระชับ ตรงประเด็น อ้างอิงตัวเลขจริง และเป็นกลางอย่างมืออาชีพ
            """.trimIndent()

            val aiAnalysis = try {
                geminiService.generateResponse(
                    prompt = prompt,
                    intentAddon = "You are a professional equity research analyst. Deliver deep, data-driven financial insights with clear markdown formatting."
                )
            } catch (_: Exception) {
                "⚠️ การสังเคราะห์บทวิเคราะห์ AI ขัดข้องชั่วคราว (แสดงข้อมูลตัวเลขทางการเงินครบถ้วนด้านล่าง)"
            }

            buildString {
                appendLine("🏛️ **${fundamental.name} (${fundamental.ticker})** • ข้อมูลพื้นฐาน & งบการเงิน")
                appendLine("หมวดธุรกิจ: **${fundamental.sector}** | อุตสาหกรรม: **${fundamental.industry}** | ประเทศ: **${fundamental.country}**")
                appendLine("ราคาล่าสุด: **$priceStr $cur** ($changeStr, $changePctStr) | 52W กรอบ: ${fundamental.low52w ?: "-"} - ${fundamental.high52w ?: "-"}")
                appendLine()

                appendLine("### 📌 ข้อเท็จจริงที่มีนัยยะ (Key Metrics)")
                appendLine("| ตัวชี้วัดสำคัญ | ค่า | ตัวชี้วัดสำคัญ | ค่า |")
                appendLine("|---|---|---|---|")
                appendLine("| มูลค่าตามราคาตลาด (Market Cap) | $capStr | อัตราผลตอบแทนเงินปันผล (Yield) | $divYieldStr |")
                appendLine("| อัตราส่วนราคาต่อกำไร (P/E TTM) | $peStr | กำไรต่อหุ้น (Basic EPS TTM) | $epsStr |")
                appendLine("| Price to Sales (P/S) | $psStr | Price to Book (P/B) | $pbStr |")
                appendLine("| Price to Free Cash Flow (P/FCF) | $pfcfStr | เงินปันผลต่อหุ้นล่าสุด (DPS) | $dpsStr |")
                appendLine("| Beta (ความผันผวน 1 ปี) | ${fundamental.beta1y?.let { "%.2f".format(it) } ?: "N/A"} | ผลตอบแทนย้อนหลัง 1 ปี | ${StockFundamentalData.formatPercent(fundamental.perf1y)} |")
                appendLine()

                appendLine("### 👥 ความเป็นเจ้าของ & สัดส่วนผู้ถือหุ้น (Ownership)")
                appendLine("- **จำนวนหุ้นทั้งหมด**: $totalSharesStr")
                appendLine("- **หุ้นกระจายสู่รายย่อย (Free Float)**: $floatSharesStr (**$floatPctStr**)")
                appendLine("- **หุ้นถือเฉพาะกลุ่ม / ผู้ถือหุ้นใหญ่**: $closeSharesStr (**$closePctStr**)")
                val floatPctVal = fundamental.floatPct ?: 50.0
                val floatBarRatio = (floatPctVal.coerceIn(0.0, 100.0) / 10).toInt()
                val barStr = "█".repeat(floatBarRatio) + "░".repeat(10 - floatBarRatio)
                appendLine("  `[$barStr]` รายย่อย $floatPctStr | ผู้ถือหุ้นใหญ่ $closePctStr")
                appendLine()

                appendLine("### 🏛️ โครงสร้างเงินทุน & สภาพคล่องงบดุล (Capital Structure)")
                appendLine("| รายการโครงสร้างทุน / งบดุล | มูลค่า ($cur) | คำอธิบาย |")
                appendLine("|---|---|---|")
                appendLine("| มูลค่ากิจการ (Enterprise Value) | $evStr | Market Cap + หนี้สินสุทธิ |")
                appendLine("| มูลค่าตามราคาตลาด (Market Cap) | $capStr | มูลค่าหุ้นทั้งหมดในตลาด |")
                appendLine("| หนี้สินรวม (Total Debt) | $debtStr | ภาระหนี้สินทางการเงินทั้งหมด |")
                appendLine("| เงินสดและรายการเทียบเท่า (Cash) | $cashStr | สภาพคล่องเงินสดในมือ |")
                appendLine("| หนี้สินสุทธิ (Net Debt) | $netDebtStr | หนี้สินรวมหักเงินสด |")
                appendLine("| ส่วนของผู้ถือหุ้น (Total Equity) | $equityStr | ทุนของบริษัท |")
                appendLine("| สินทรัพย์รวม (Total Assets) | $assetsStr | ขนาดงบดุลรวม |")
                appendLine("| หนี้สินต่อทุน (Debt to Equity) | $deStr | อัตราส่วนความเสี่ยงหนี้สิน |")
                appendLine()

                appendLine("### 💰 ผลการดำเนินงาน & ความสามารถทำกำไร (Financials)")
                appendLine("| ตัวชี้วัดงบการเงิน | ล่าสุด (TTM / FQ) | รอบปีงบประมาณ (FY) |")
                appendLine("|---|---|---|")
                appendLine("| รายได้รวม (Total Revenue) | TTM: $revTtmStr (FQ: $revFqStr) | $revFyStr |")
                appendLine("| กำไรสุทธิ (Net Income) | TTM: $niTtmStr (FQ: $niFqStr) | $niFyStr |")
                appendLine("| กระแสเงินสดอิสระ (Free Cash Flow) | TTM: $fcfStr | - |")
                appendLine("| อัตรากำไรดำเนินงาน (Operating Margin) | $opMarginStr | อัตรากำไรจากธุรกิจหลัก |")
                appendLine("| อัตรากำไรสุทธิ (Net Profit Margin) | $netMarginStr | อัตรากำไรสุทธิบรรทัดสุดท้าย |")
                appendLine("| ผลตอบแทนต่อส่วนผู้ถือหุ้น (ROE) | $roeStr | ประสิทธิภาพสร้างกำไรจากทุน |")
                appendLine("| ผลตอบแทนต่อสินทรัพย์ (ROA) | $roaStr | ประสิทธิภาพใช้สินทรัพย์ |")
                appendLine("| ผลตอบแทนเงินลงทุน (ROIC) | $roicStr | ผลตอบแทนจากเงินลงทุนรวม |")
                appendLine()

                appendLine("### 🎯 เป้าหมายนักวิเคราะห์ & โมเมนตัมราคา (Consensus)")
                appendLine("- **ราคาเป้าหมายเฉลี่ย (Target Price)**: **$ptAvgStr** (Upside: **$upsideStr**)")
                appendLine("- **กรอบราคาเป้าหมาย**: ต่ำสุด $ptLowStr — สูงสุด $ptHighStr")
                appendLine("- **ผลตอบแทน YTD**: ${StockFundamentalData.formatPercent(fundamental.perfYtd)}")
                appendLine()

                appendLine("---")
                appendLine("### 🤖 บทวิเคราะห์ปัจจัยพื้นฐานรอบด้านโดย AI (Multi-Dimensional Analysis)")
                appendLine(aiAnalysis)
            }
        } catch (e: Exception) {
            "❌ เกิดข้อผิดพลาดในการวิเคราะห์ปัจจัยพื้นฐาน: ${e.message}"
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
            if (sentiment.containsKey("positioning_divergence") && sentiment["positioning_divergence"] != "N/A") {
                appendLine("  Positioning: ${sentiment["positioning_divergence"]}")
            }
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

    internal fun executeSignalDataExport(args: Map<String, String>): String {
        val symbol = args["symbol"]?.takeUnless { it.equals("all", ignoreCase = true) }
        val interval = args["interval"]?.takeUnless { it.equals("all", ignoreCase = true) }
        val strategy = args["strategy"]?.takeUnless { it.equals("all", ignoreCase = true) }
        val status = args["status"] ?: "all"
        val format = args["format"] ?: "json"
        val limit = args["limit"]?.toIntOrNull() ?: 500

        val result = com.skyliner2008.jarvis.automation.SignalDatasetManager.exportDataset(
            symbol = symbol,
            interval = interval,
            strategy = strategy,
            status = status,
            format = format,
            limit = limit
        )

        return buildString {
            appendLine(result.summaryTh)
            appendLine()
            if (result.totalCount > 0) {
                appendLine("```${if (result.dataFormat == "csv") "csv" else "json"}")
                appendLine(result.payload)
                appendLine("```")
            } else {
                appendLine("*(ยังไม่มีข้อมูลสัญญาณที่ตรงกับเงื่อนไขการค้นหา)*")
            }
        }
    }

    internal fun executeSignalConfigImport(args: Map<String, String>): String {
        val configJson = args["config_json"] ?: args["config"] ?: args["json"]
            ?: return "❌ กรุณาระบุพารามิเตอร์ `config_json` ที่มี JSON โครงสร้างการตั้งค่า (tunings หรือ entry_params)"

        val result = com.skyliner2008.jarvis.automation.SignalDatasetManager.importConfiguration(configJson)

        return buildString {
            appendLine(result.messageTh)
            if (result.details.isNotEmpty()) {
                appendLine()
                appendLine("📋 **รายละเอียด:**")
                result.details.forEach { appendLine("- $it") }
            }
        }
    }
}

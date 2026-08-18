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
import kotlinx.datetime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.logDebug
import com.example.personalaibot.automation.backtest.EntryParams

/**
 * TradingToolExecutor — รับ trading tool calls และ format ผลลัพธ์เป็น text
 * สำหรับส่งกลับให้ Gemini อ่านและอธิบายให้ผู้ใช้
 *
 * รองรับทั้ง Classic Trading Tools และ SMC (Smart Money Concepts) Tools
 */
class TradingToolExecutor(private val client: HttpClient, private val geminiService: GeminiService) {

    private val api = TradingApiService(client)
    private val smcExecutor = SmcToolExecutor(client)
    private val advancedEngine = AdvancedTradingEngine(SmcApiService(client))
    private val smcFlowProvider = com.example.personalaibot.automation.SmcFlowAlertProvider(SmcApiService(client))
    private val strategySignalProvider = com.example.personalaibot.automation.StrategySignalProvider(SmcApiService(client))
    private val signalAlertProvider = com.example.personalaibot.automation.SignalAlertProvider(SmcApiService(client))
    private val json = Json { ignoreUnknownKeys = true }

    /** Circuit breaker — bridge ล่มแล้วข้าม HTTP ไป local engine ตรงๆ เป็นเวลา 10 นาที */
    @Volatile
    private var deepSuiteBridgeDownUntilMs: Long = 0L

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
            "trading_smc_flow"               -> executeSmcFlow(args)
            "trading_strategy_signal"        -> executeStrategySignal(args)
            "trading_signal_stats"           -> executeSignalStats(args)
            "trading_backtest"               -> executeBacktest(args)
            "trading_backtest_optimize"      -> executeBacktestOptimize(args)
            "trading_backtest_evolve"        -> executeBacktestEvolve(args)
            "trading_mix_config"             -> executeMixConfig(args)
            "trading_fundamental_analysis"   -> executeFundamentalAnalysis(args)
            "trading_fear_greed"             -> executeFearGreed(args)
            "trading_crypto_overview"        -> executeCryptoOverview(args)
            "trading_position_sizing"        -> executePositionSizing(args)
            "trading_correlation_matrix"     -> executeCorrelationMatrix(args)
            "trading_economic_data"          -> executeEconomicData(args)
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

    /** trading_smc_flow — สัญญาณจาก SMC Flow System (คำนวณในเครื่องจากแท่งเทียน TV) */
    private suspend fun executeSmcFlow(args: Map<String, String>): String {
        val symbol = (args["symbol"] ?: "XAUUSD").trim().uppercase()
        val interval = (args["interval"] ?: "1h").trim().lowercase()
        return runCatching { smcFlowProvider.fetchFormatted("$symbol@$interval") }
            .getOrElse { "❌ SMC Flow error: ${it.message}" }
    }

    /** trading_strategy_signal — สัญญาณกลยุทธ์จาก Strategy Library (คำนวณในเครื่องจากแท่งเทียน TV) */
    private suspend fun executeStrategySignal(args: Map<String, String>): String {
        val symbol = (args["symbol"] ?: "XAUUSD").trim().uppercase()
        val interval = (args["interval"] ?: "1h").trim().lowercase()
        val strategy = (args["strategy"] ?: "all").trim().lowercase()
        return runCatching { strategySignalProvider.fetchFormatted("$symbol@$interval", strategy) }
            .getOrElse { "❌ Strategy Signal error: ${it.message}" }
    }

    /** trading_signal_stats — backtest win-rate/avgR (จำลองย้อนหลัง) หรือ live (สถิติ signal ที่ alert ยิงจริง) */
    private suspend fun executeSignalStats(args: Map<String, String>): String {
        val source = (args["source"] ?: "backtest").trim().lowercase()
        if (source == "live") return executeSignalStatsLive(args)
        val symbol = (args["symbol"] ?: "XAUUSD").trim().uppercase()
        val interval = (args["interval"] ?: "1h").trim().lowercase()
        val strategy = (args["strategy"] ?: "all").trim().lowercase()
        return runCatching { signalAlertProvider.fetchStats("$symbol@$interval", strategy) }
            .getOrElse { "❌ Signal Stats error: ${it.message}" }
    }

    /**
     * trading_backtest — จำลองเทรดย้อนหลัง 5,000 แท่ง (BacktestEngine: equity curve, drawdown, ต้นทุน)
     * สัญญาณจาก SignalMarkerProvider + SL/TP สูตรเดียวกับ signal alert (computeTpSl)
     * interval=all → รันครบ 3 TF (15m/1h/4h) ในคำสั่งเดียว + ตารางเทียบผล
     *
     * 2026-08-17 — MULTI-SESSION: งานหนักรันใน LongTaskRunner (session แยก) ไม่บล็อก turn ของ AI
     * tool ตอบ ack ทันที → ผู้ใช้คุยต่อ/วางสาย live ได้ → เสร็จแล้วระบบแจ้งผลเอง (แชท+เสียง+Backtest Lab)
     */
    private fun executeBacktest(args: Map<String, String>): String {
        val symbol = (args["symbol"] ?: "XAUUSD").trim().uppercase()
        val interval = (args["interval"] ?: "1h").trim().lowercase()
        val strategy = (args["strategy"] ?: "all").trim().lowercase()
        val costsOn = (args["costs"] ?: "on").trim().lowercase() != "off"
        // mix: ผู้ใช้เลือกกลยุทธ์ที่จะโหวตเอง (เช่น "tsmom,trend,donchian,utbot")
        // mix_min_votes default = ครึ่งของจำนวนกลยุทธ์ปัดขึ้น
        val mixStrategies = args["mix_strategies"]?.trim()
        val mixKinds = if (strategy == "mix") {
            val ks = mixStrategies?.let { com.example.personalaibot.automation.MixSignalEngine.parseKinds(it) }
                ?: listOf("MOM", "TR", "E", "UT") // default mix = โมเมนตัม + เทรนด์ 2 ชั้น + UT
            if (ks.size < 2) return "❌ mix ต้องเลือกอย่างน้อย 2 กลยุทธ์ เช่น mix_strategies=\"tsmom,trend,donchian,utbot\""
            ks
        } else emptyList()
        val mixMinVotes = args["mix_min_votes"]?.trim()?.toIntOrNull()
            ?: ((mixKinds.size + 1) / 2)

        val tfLabel = if (interval == "all") "all TF (15m/1h/4h)" else interval
        val label = "Backtest $symbol $tfLabel [$strategy]"
        com.example.personalaibot.automation.backtest.LongTaskRunner.launch("backtest", label) {
            runBacktestTask(symbol, interval, strategy, costsOn, mixKinds, mixMinVotes)
        }
        return "⏳ รับคำสั่งแล้ว — กำลังรัน **$label** ในเบื้องหลัง (session แยก ไม่บล็อกการสนทนา) " +
            "เมื่อเสร็จระบบจะสรุปผลให้อัตโนมัติ (แชท + เสียง + กราฟในหน้า Backtest Lab) — " +
            "ตอบผู้ใช้สั้นๆ ว่ากำลังดำเนินการอยู่ แล้วคุยเรื่องอื่นต่อได้ตามปกติ"
    }

    /** ตัวงาน backtest จริง (รันใน LongTaskRunner) — คืน (ข้อความผลลัพธ์เต็ม, สรุปสั้นสำหรับพูด) */
    private suspend fun runBacktestTask(
        symbol: String, interval: String, strategy: String, costsOn: Boolean,
        mixKinds: List<String>, mixMinVotes: Int
    ): Pair<String, String> = withContext(Dispatchers.Default) {
        if (interval == "all") {
            // รัน 3 TF พร้อมกัน (parallel) — เดิม sequential ใช้ ~6 นาทีจน Live websocket โดนตัด
            // ผลลัพธ์คงลำดับ 15m/1h/4h ตาม tfs (map คงลำดับอยู่แล้ว)
            val tfs = listOf("15m", "1h", "4h")
            val outcomes: List<Triple<String, com.example.personalaibot.automation.backtest.BacktestResult?, String?>> =
                coroutineScope {
                    tfs.map { tf -> async { runBacktestOne(symbol, tf, strategy, costsOn, mixKinds, mixMinVotes) } }.awaitAll()
                }
            val parts = mutableListOf<String>()
            val summary = mutableListOf<Triple<String, com.example.personalaibot.automation.backtest.BacktestResult?, String?>>()
            outcomes.forEachIndexed { i, outcome ->
                summary += Triple(tfs[i], outcome.second, outcome.third)
                parts += "══════════ TF ${tfs[i]} ══════════\n${outcome.first}"
            }
            val text = buildString {
                appendLine("📈 **Backtest All-TF — $symbol** (strategy=$strategy, ต้นทุน: ${if (costsOn) "เปิด" else "ปิด"})")
                appendLine()
                appendLine("**เทียบผล 3 Timeframes**")
                appendLine("| TF | ไม้ | Win% | PF | Expectancy | กำไรสุทธิ | MaxDD |")
                appendLine("|---|---|---|---|---|---|---|")
                for ((tf, r, err) in summary) {
                    if (r == null) appendLine("| $tf | — | — | — | — | ${err ?: "ไม่มีสัญญาณ"} | — |")
                    else appendLine("| $tf | ${r.totalTrades} | ${"%.1f".format(r.winRate * 100)} | ${"%.2f".format(r.profitFactor)} | ${"%+.2f".format(r.expectancyR)}R | ${"%+.1f".format(r.totalReturnPct)}% | −${"%.1f".format(r.maxDrawdownPct)}% |")
                }
                appendLine()
                appendLine("---")
                parts.forEach { appendLine(); appendLine(it) }
            }.trim()
            // สรุปเสียง: ทีละ TF สั้นๆ
            val speech = "Backtest $symbol ครบ 3 timeframe เสร็จแล้วครับ: " + summary.joinToString(" / ") { (tf, r, err) ->
                if (r == null) "$tf ${err ?: "ไม่มีสัญญาณ"}"
                else "$tf profit factor ${"%.2f".format(r.profitFactor)}, ${if (r.totalReturnPct >= 0) "กำไร" else "ขาดทุน"} ${"%.0f".format(kotlin.math.abs(r.totalReturnPct))} เปอร์เซ็นต์"
            } + " — รายละเอียดอยู่ในแชทและหน้า Backtest Lab ครับ"
            return@withContext text to speech
        }

        val (text, result, err) = runBacktestOne(symbol, interval, strategy, costsOn, mixKinds, mixMinVotes)
        val speech = if (result == null) "Backtest $symbol $interval ไม่สำเร็จครับ: ${err ?: "ไม่ทราบสาเหตุ"}"
        else "Backtest $symbol $interval เสร็จแล้วครับ: ${result.totalTrades} ไม้, win rate ${"%.0f".format(result.winRate * 100)} เปอร์เซ็นต์, profit factor ${"%.2f".format(result.profitFactor)}, ${if (result.totalReturnPct >= 0) "กำไร" else "ขาดทุน"}สุทธิ ${"%.0f".format(kotlin.math.abs(result.totalReturnPct))} เปอร์เซ็นต์ — รายละเอียดอยู่ในแชทและหน้า Backtest Lab ครับ"
        return@withContext text to speech
    }

    /** รัน backtest 1 TF — คืน (ข้อความผลลัพธ์, BacktestResult?, error?) */
    private suspend fun runBacktestOne(
        symbol: String, interval: String, strategy: String, costsOn: Boolean,
        mixKinds: List<String> = emptyList(), mixMinVotes: Int = 0
    ): Triple<String, com.example.personalaibot.automation.backtest.BacktestResult?, String?> {
        val t0 = System.currentTimeMillis()
        val smc = SmcApiService(client)
        val fetched = runCatching { smc.fetchBacktestCandles(symbol, interval) }
            .getOrElse { return Triple("❌ ดึงแท่งเทียนย้อนหลังไม่ได้: ${it.message}", null, "ดึงข้อมูลไม่ได้") }
        if (fetched.candles.size < 300) {
            return Triple("❌ แท่งเทียนย้อนหลังไม่พอสำหรับ backtest (${fetched.candles.size} < 300 แท่ง)", null, "แท่งไม่พอ")
        }
        logDebug("Backtest", "▶ $symbol/$interval fetch OK ${fetched.candles.size} แท่ง (${System.currentTimeMillis() - t0}ms) — เริ่มคำนวณสัญญาณ")

        val kindFilter = when (strategy) {
            "tsmom" -> setOf("MOM"); "trend" -> setOf("TR"); "reversal" -> setOf("REV")
            "donchian" -> setOf("DC"); "w52high" -> setOf("52H"); "ema1460", "ema14_60" -> setOf("E")
            "utbot", "ut" -> setOf("UT"); "threebar", "3br" -> setOf("3BR")
            "smc" -> setOf("SMC")
            "mix" -> setOf("MIX")
            else -> setOf("MOM", "TR", "REV", "DC", "52H", "E", "UT", "3BR", "SMC")
        }

        val candles = fetched.candles
        // entry params ที่จูนแล้ว (EntryTuning) — backtest ต้องวัดบนจุดเข้าเดียวกับที่ live ใช้จริง
        val tunedEntry = runCatching {
            com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager()
                .getTunedEntryParams(symbol, interval)
        }.getOrElse { emptyMap() }
        // คำนวณเฉพาะกลุ่มที่เลือกจริง — กรณี strategy=smc/mix ไม่ต้องเสียเวลาสแกนกลยุทธ์ classic ทั้ง 8 ตัว
        val hasClassic = kindFilter.any { it != "SMC" && it != "MIX" }
        val classicMarkers = if (hasClassic) {
            com.example.personalaibot.automation.SignalMarkerProvider(smc)
                .compute(candles, Int.MAX_VALUE, tunedEntry)
        } else emptyList()

        // ── MIX (โหวตหลายกลยุทธ์): state-based voting → edge เมื่อ score ข้ามเกณฑ์
        val mixMarkers = if ("MIX" in kindFilter) {
            com.example.personalaibot.automation.MixSignalEngine.mixMarkers(candles, mixKinds, mixMinVotes)
        } else emptyList()

        // ── SMC (MT5 Engine): เดินหน้าทีละแท่ง สร้าง snapshot จาก window 300 แท่ง (กัน lookahead)
        //    SL/TP ของสัญญาณมาจากโครงสร้างตลาด ไม่ใช่ ATR multiple — map แยกไว้ให้ engine ใช้ตรงๆ
        val smcBarSignals = if ("SMC" in kindFilter) {
            com.example.personalaibot.automation.smc.SmcSignals.generate(candles, symbol, interval)
        } else emptyList()
        val smcSlTpByTime = HashMap<Long, Pair<Double, Double>>(smcBarSignals.size)
        val smcMarkers = smcBarSignals.map { bs ->
            smcSlTpByTime[bs.time] = bs.signal.sl to bs.signal.tp
            com.example.personalaibot.automation.SignalMarkerProvider.SignalMarker(
                bs.time, bs.signal.side,
                "SMC${if (bs.signal.side == "BUY") "▲" else "▼"}",
                if (bs.signal.side == "BUY") "#26A69A" else "#EF5350"
            )
        }
        val markers = classicMarkers + smcMarkers + mixMarkers
        if (markers.isEmpty()) {
            if (strategy == "mix") {
                return Triple("📭 Mix voting (${mixKinds.joinToString("+")}, เกณฑ์ $mixMinVotes เสียง) ไม่มี edge ที่ score ข้ามเกณฑ์ใน $symbol $interval — ลองลด mix_min_votes", null, "ไม่มีสัญญาณ")
            }
            return Triple("📭 ไม่พบสัญญาณย้อนหลังของกลยุทธ์ที่เลือกใน $symbol $interval", null, "ไม่มีสัญญาณ")
        }

        val engine = com.example.personalaibot.automation.backtest.BacktestEngine()
        val result = runCatching {
            engine.run(
                symbol = symbol, interval = interval, source = fetched.source,
                candles = candles, markers = markers, kindFilter = kindFilter,
                kindOf = { label -> com.example.personalaibot.automation.signalKindOf(label) },
                strategyName = { kind -> signalAlertProvider.strategyName(kind) },
                tpSl = { k, s, c, i, a14, a6 ->
                    if (k == "SMC") smcSlTpByTime[c[i].timestamp] ?: signalAlertProvider.computeTpSl(k, s, c, i, a14, a6)
                    else signalAlertProvider.computeTpSl(k, s, c, i, a14, a6)
                },
                config = com.example.personalaibot.automation.backtest.BacktestConfig(includeCosts = costsOn)
            )
        }.getOrElse { return Triple("❌ Backtest error: ${it.message}", null, it.message) }

        logDebug("Backtest", "✅ $symbol/$interval เสร็จ — ${result.totalTrades} ไม้ ใช้เวลารวม ${System.currentTimeMillis() - t0}ms")
        // บันทึกผลลัพธ์ละเอียดลง logcat ให้ตรวจสอบความถูกต้องของตัวเลขได้ (ไม่ต้องเปิดแชท)
        logDebug("Backtest", buildString {
            appendLine("═══ ผล Backtest $symbol/$interval (strategy=$strategy) ═══")
            appendLine("ข้อมูล: ${result.bars} แท่ง ts ${result.fromTs}→${result.toTs} | source=${result.source} | ต้นทุน=${if (costsOn) "on" else "off"}")
            appendLine("ไม้=${result.totalTrades} (ข้าม ${result.skippedSignals}) | W/L/T=${result.wins}/${result.losses}/${result.timeouts}")
            appendLine("Win%=${"%.1f".format(result.winRate * 100)} PF=${"%.2f".format(result.profitFactor)} Exp=${"%+.2f".format(result.expectancyR)}R สุทธิ=${"%+.1f".format(result.totalReturnPct)}% MaxDD=-${"%.1f".format(result.maxDrawdownPct)}% Sharpe=${"%.2f".format(result.sharpe)}")
            result.perStrategy.forEach { s ->
                appendLine("  ▸ ${s.name}: สัญญาณ=${s.signals} เข้า=${s.taken} ข้าม=${s.skipped} W/L/T=${s.wins}/${s.losses}/${s.timeouts} Win%=${"%.0f".format(s.winRate * 100)} avgR=${"%+.2f".format(s.avgR)} PF=${"%.2f".format(s.profitFactor)}")
            }
            result.trades.takeLast(3).forEach { t ->
                appendLine("  ↳ ${t.strategyName} ${t.side} @ ${t.entryPrice} → ${t.exitReason} @ ${t.exitPrice} (${"%+.2f".format(t.pnlR)}R, ${"%+,.2f".format(t.pnlMoney)})")
            }
        }.trimEnd())
        // 🔬 SMC trade forensics — dump ทุกไม้ SMC ใต้ tag JarvisVM (อยู่ใน filter ที่ผู้ใช้ capture อยู่แล้ว)
        //    เพื่อตรวจว่า 0% win เกิดจากกลยุทธ์จริง หรือ entry/SL/TP ผิดตำแหน่ง (structure-based)
        val smcTrades = result.trades.filter { it.kind == "SMC" }
        if (smcTrades.isNotEmpty()) {
            logDebug("JarvisVM", buildString {
                appendLine("🔬 SMC trade detail [$symbol/$interval] — ${smcTrades.size} ไม้:")
                smcTrades.forEach { t ->
                    val slDist = kotlin.math.abs(t.entryPrice - t.sl)
                    val tpDist = kotlin.math.abs(t.tp - t.entryPrice)
                    appendLine("  ${t.side} entry=${t.entryPrice} | SL=${t.sl} (${"%.1f".format(slDist)}) TP=${t.tp} (${"%.1f".format(tpDist)}) RR=${"%.2f".format(tpDist / slDist)} → ${t.exitReason} @ ${t.exitPrice} (${"%+.2f".format(t.pnlR)}R)")
                }
            }.trimEnd())
        }
        // push เข้า store ให้หน้าจอ Backtest (แท็บกลยุทธ์ + กราฟ) อ่านไปแสดง
        com.example.personalaibot.automation.backtest.BacktestResultStore.add(result, strategy)
        // บันทึกสุขภาพกลยุทธ์ลง DB (Per-TF Strategy Gate) — live alert เช็กตารางนี้ก่อนยิงทุกครั้ง
        runCatching { com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager() }.getOrNull()?.let { m ->
            result.perStrategy.forEach { s ->
                m.saveStrategyHealth(symbol, interval, s.kind, s.profitFactor, s.avgR, s.winRate, s.taken, result.bars)
            }
            logDebug("JarvisVM", "💾 StrategyHealth saved: ${result.perStrategy.size} kinds ($symbol/$interval)")
        }
        return Triple(formatBacktestResult(result) + buildRegimeSection(result, candles), result, null)
    }

    private fun formatBacktestResult(r: com.example.personalaibot.automation.backtest.BacktestResult): String {
        fun fmt(v: Double) = if (kotlin.math.abs(v) >= 100) "%.2f".format(v) else "%.4f".format(v)
        fun fmtTime(ms: Long): String {
            val l = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
                .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
            return "%02d/%02d/%02d %02d:%02d".format(l.dayOfMonth, l.monthNumber, l.year % 100, l.hour, l.minute)
        }
        fun pct(v: Double) = "%.1f%%".format(v)

        return buildString {
            appendLine("📈 **Backtest — ${r.symbol} ${r.interval}** (${r.bars} แท่ง)")
            appendLine("ข้อมูล: ${fmtTime(r.fromTs)} → ${fmtTime(r.toTs)} UTC จาก ${r.source} | ต้นทุน: ${if (r.config.includeCosts) "เปิด (spread ${r.config.spreadPrice}, commission ${r.config.commissionPct * 100}%/ข้าง)" else "ปิด"}")
            appendLine("เงื่อนไข: ทุน $${"%,.0f".format(r.config.initialBalance)} เสี่ยง ${r.config.riskPerTradePct * 100}%/ไม้ เลเวอเรจ ≤${r.config.maxLeverage.toInt()}x ถือทีละ 1 ไม้ เข้าที่ปิดแท่งสัญญาณ")
            appendLine()
            appendLine("**ภาพรวม**")
            appendLine("| ตัวชี้วัด | ค่า |")
            appendLine("|---|---|")
            appendLine("| ไม้ทั้งหมด | ${r.totalTrades} (ข้าม ${r.skippedSignals} สัญญาณเพราะมีไม้ค้าง) |")
            appendLine("| ชนะ / แพ้ / ค้าง | ${r.wins} / ${r.losses} / ${r.timeouts} |")
            appendLine("| Win-rate | ${pct(r.winRate * 100)} |")
            appendLine("| Profit Factor | ${"%.2f".format(r.profitFactor)} |")
            appendLine("| Expectancy | ${"%+.2f".format(r.expectancyR)}R/ไม้ |")
            appendLine("| กำไรสุทธิ | ${"%+,.2f".format(r.finalBalance - r.config.initialBalance)} (${"%+.1f".format(r.totalReturnPct)}%) |")
            appendLine("| Max Drawdown | −${pct(r.maxDrawdownPct)} |")
            appendLine("| Sharpe (คร่าวๆ) | ${"%.2f".format(r.sharpe)} |")
            appendLine()
            if (r.perStrategy.isNotEmpty()) {
                appendLine("**แยกตามกลยุทธ์**")
                appendLine("| กลยุทธ์ | สัญญาณ | ไม้ | ชนะ | แพ้ | ค้าง | Win% | avgR | PF |")
                appendLine("|---|---|---|---|---|---|---|---|---|")
                r.perStrategy.forEach { s ->
                    appendLine("| ${s.name} | ${s.signals} | ${s.taken} | ${s.wins} | ${s.losses} | ${s.timeouts} | ${pct(s.winRate * 100)} | ${"%+.2f".format(s.avgR)} | ${"%.2f".format(s.profitFactor)} |")
                }
                appendLine()
            }
            if (r.trades.isNotEmpty()) {
                appendLine("**${minOf(5, r.trades.size)} ไม้ล่าสุด**")
                r.trades.takeLast(5).reversed().forEach { t ->
                    val icon = if (t.side == "BUY") "🟢" else "🔴"
                    val out = when (t.exitReason) {
                        "TP" -> "✅ TP"; "SL" -> "❌ SL"; else -> "⏱ ค้าง"
                    }
                    appendLine("$icon **${t.side}** @ ${fmt(t.entryPrice)} → $out @ ${fmt(t.exitPrice)} (${"%+.2f".format(t.pnlR)}R, ${"%+,.2f".format(t.pnlMoney)}) ・ ${t.strategyName} ・ ${fmtTime(t.entryTime)}")
                }
                appendLine()
            }
            appendLine("⚠️ ผล backtest จากข้อมูลย้อนหลัง ไม่การันตีอนาคต — ใช้ `trading_backtest_optimize` เพื่อตรวจ overfitting (walk-forward/permutation/Monte Carlo) ก่อนเชื่อผลลัพธ์")
            appendLine("📲 ดูแบบกราฟ (equity curve + แท็บแยกกลยุทธ์) ได้ที่หน้า **Backtest Lab** — ไอคอนกราฟแท่ง 📊 บนแถบด้านบน")
        }.trim()
    }

    /** สถิติแยกตามสภาพตลาด (BULL/BEAR/SIDEWAYS) — ดูว่ากลยุทธ์เกิด regime ไหน */
    private fun buildRegimeSection(
        r: com.example.personalaibot.automation.backtest.BacktestResult,
        candles: List<Candle>
    ): String {
        if (r.trades.size < 5) return ""
        val regimes = com.example.personalaibot.automation.backtest.RegimeClassifier.classify(candles)
        val stats = com.example.personalaibot.automation.backtest.RegimeClassifier.statsByRegime(r.trades, candles, regimes)
        if (stats.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine("**สถิติตามสภาพตลาด (Regime)**")
            appendLine("| Regime | ไม้ | Win% | avgR | รวมR |")
            appendLine("|---|---|---|---|---|")
            stats.forEach { s ->
                val icon = when (s.regime) {
                    "BULL" -> "🐂"; "BEAR" -> "🐻"; else -> "🦀"
                }
                appendLine("| $icon ${s.regime} | ${s.trades} | ${"%.0f%%".format(s.winRate * 100)} | ${"%+.2f".format(s.avgR)} | ${"%+.2f".format(s.totalR)} |")
            }
        }
    }

    /**
     * trading_mix_config — ตั้ง/ดู/ลบ mix config ต่อ symbol+TF (ใช้กับ live signal alert)
     * action: set (ต้องส่ง strategies, min_votes optional) / show / clear
     */
    private fun executeMixConfig(args: Map<String, String>): String {
        val symbol = (args["symbol"] ?: "XAUUSD").trim().uppercase()
        val interval = (args["interval"] ?: "1h").trim().lowercase()
        val action = (args["action"] ?: "show").trim().lowercase()
        val mgr = runCatching { com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager() }
            .getOrElse { return "❌ เข้าถึงฐานข้อมูลไม่ได้: ${it.message}" }

        return when (action) {
            "set" -> {
                val kindsCsv = args["strategies"]?.trim()
                    ?: return "❌ ต้องระบุ strategies เช่น \"tsmom,trend,donchian,utbot\""
                val kinds = com.example.personalaibot.automation.MixSignalEngine.parseKinds(kindsCsv)
                if (kinds.size < 2) return "❌ ต้องเลือกอย่างน้อย 2 กลยุทธ์ (เลือกได้จาก tsmom,trend,reversal,donchian,w52high,ema1460,utbot,threebar)"
                val minVotes = args["min_votes"]?.trim()?.toIntOrNull() ?: ((kinds.size + 1) / 2)
                if (minVotes < 2 || minVotes > kinds.size) return "❌ min_votes ต้องอยู่ระหว่าง 2 ถึง ${kinds.size} (จำนวนกลยุทธ์ที่เลือก)"
                mgr.setMixConfig(symbol, interval, kinds.joinToString(","), minVotes)
                "✅ ตั้ง Mix config $symbol/$interval แล้ว\n" +
                    "กลยุทธ์: ${kinds.joinToString(" + ")} ・ เกณฑ์โหวต: ≥$minVotes/${kinds.size} เสียง\n" +
                    "สัญญาณ MIX จะโหวตรวม state ของทุกกลยุทธ์ แล้วแจ้งเตือนเมื่อคะแนนข้ามเกณฑ์"
            }
            "clear" -> {
                mgr.setMixConfig(symbol, interval, "", 0)
                "🗑 ลบ Mix config ของ $symbol/$interval แล้ว — สัญญาณ mix จะไม่ถูกคำนวณใน alert อีก"
            }
            else -> {
                val cfg = mgr.getMixConfig(symbol, interval)
                if (cfg == null) {
                    "ℹ️ $symbol/$interval ยังไม่มี Mix config — ตั้งด้วย action=set เช่น strategies=\"tsmom,trend,donchian,utbot\""
                } else {
                    "📋 Mix config $symbol/$interval: กลยุทธ์ ${cfg.first} ・ เกณฑ์โหวต ≥${cfg.second} เสียง"
                }
            }
        }
    }

    /**
     * trading_backtest_optimize — ADAPTIVE: grid 25 combos + mutation รอบ params ปัจจุบัน/ประวัติ
     * + walk-forward 5 splits + permutation 200 รอบ + Monte Carlo 1,000 รอบ → overfitting score + เกรด
     * มีความจำ (OptimizationTrial): เรียนรู้ว่าปรับทิศไหนแล้วดี/แย่ จากทุกรอบที่เคยจูน
     * AUTO-APPLY: ถ้า params ใหม่ดีกว่าค่าเดิม (score สูงกว่า ≥2%) และไม่ overfit → บันทึกใช้จริงทันที
     * (apply=off = dry-run ดูผลอย่างเดียวไม่บันทึก)
     */
    private fun executeBacktestOptimize(args: Map<String, String>): String {
        val symbol = (args["symbol"] ?: "XAUUSD").trim().uppercase()
        val interval = (args["interval"] ?: "1h").trim().lowercase()
        val strategy = (args["strategy"] ?: "all").trim().lowercase()
        val dryRun = (args["apply"] ?: "auto").trim().lowercase() == "off"
        // scope: sltp = จูน SL/TP (default) | entry = จูน params จุดเข้า | both = จูนจุดเข้าก่อนแล้วจูน SL/TP ต่อ
        val scope = (args["scope"] ?: "sltp").trim().lowercase()
            .let { if (it in setOf("sltp", "entry", "both")) it else "sltp" }
        // interval=all ต้องขยายเป็น 3 TF จริง — เดิมส่ง "all" เป็น interval ตรงๆ ทำ tuning ถูกเซฟใต้ key "all"
        // ซึ่ง live alert (lookup ด้วย 15m/1h/4h) ไม่เคยอ่านเจอ = จูนแล้วไม่มีผลจริง (พบจาก log 2026-08-18 00:53)
        val tfs = if (interval == "all") listOf("15m", "1h", "4h") else listOf(interval)
        val tfLabel = if (interval == "all") "all TF (15m/1h/4h)" else interval
        val label = "Adaptive Optimize $symbol $tfLabel [$strategy/$scope]${if (dryRun) " (dry-run)" else ""}"
        com.example.personalaibot.automation.backtest.LongTaskRunner.launch("optimize", label) {
            val text = tfs.map { tf -> runOptimizeTask(symbol, tf, strategy, dryRun, scope) }.joinToString("\n\n")
            // สรุปเสียงจากผล: จำนวนกลยุทธ์ที่ auto-apply + เกรดรวม
            val applied = Regex("AUTO-APPLY แล้ว (\\d+) รายการ").findAll(text).sumOf { it.groupValues[1].toIntOrNull() ?: 0 }
            val overfitCount = Regex("🔴 overfit").findAll(text).count()
            val speech = when {
                text.startsWith("❌") -> "Optimize $symbol ไม่สำเร็จครับ"
                applied > 0 -> "Adaptive optimize $symbol $tfLabel เสร็จแล้วครับ ปรับค่าให้อัตโนมัติรวม $applied กลยุทธ์" +
                    (if (overfitCount > 0) " มี $overfitCount กลยุทธ์ที่ overfit ระวังไว้ครับ" else "") + " รายละเอียดอยู่ในแชทครับ"
                else -> "Adaptive optimize $symbol $tfLabel เสร็จแล้วครับ ยังไม่มีค่าใหม่ที่ดีกว่าค่าเดิม ระบบคง params เดิมไว้ รายละเอียดอยู่ในแชทครับ"
            }
            text to speech
        }
        return "⏳ รับคำสั่งแล้ว — กำลังรัน **$label** ในเบื้องหลัง (session แยก อาจใช้เวลา 1-3 นาที) " +
            "เมื่อเสร็จระบบจะสรุปผลให้อัตโนมัติ (แชท + เสียง) — ตอบผู้ใช้สั้นๆ ว่ากำลังดำเนินการอยู่ แล้วคุยเรื่องอื่นต่อได้ตามปกติ"
    }

    /** ตัวงาน optimize จริง (รันใน LongTaskRunner) — scope: sltp=จูน SL/TP (default) | entry=จูน params จุดเข้า | both=จุดเข้าก่อนแล้ว SL/TP */
    private suspend fun runOptimizeTask(symbol: String, interval: String, strategy: String, dryRun: Boolean, scope: String = "sltp"): String = withContext(Dispatchers.Default) {

        // SMC ใช้ SL/TP จากโครงสร้างตลาด (structure-based) ไม่ใช่ ATR multiplier — tune ด้วย optimize ไม่ได้
        val s = strategy.trim().lowercase()
        val known = setOf("", "all", "tsmom", "trend", "reversal", "donchian", "w52high", "ema1460", "ema14_60", "utbot", "ut", "threebar", "3br")
        if (s == "smc") {
            return@withContext "ℹ️ SMC ใช้ SL/TP จากโครงสร้างตลาด (swing high/low) ไม่ได้ใช้ ATR multiplier จึง tune ด้วย optimize ไม่ได้ — ใช้ได้เฉพาะ 8 กลยุทธ์ classic: tsmom, trend, reversal, donchian, w52high, ema1460, utbot, threebar"
        }
        if (s !in known) {
            return@withContext "❌ ไม่รู้จักกลยุทธ์ '$strategy' — เลือกได้: tsmom, trend, reversal, donchian, w52high, ema1460, utbot, threebar หรือ all"
        }

        val smc = SmcApiService(client)
        val fetched = runCatching { smc.fetchBacktestCandles(symbol, interval) }
            .getOrElse { return@withContext "❌ ดึงแท่งเทียนย้อนหลังไม่ได้: ${it.message}" }
        if (fetched.candles.size < 500) {
            return@withContext "❌ แท่งเทียนไม่พอสำหรับ optimize (${fetched.candles.size} < 500) — ลอง TF ใหญ่ขึ้น"
        }
        val candles = fetched.candles
        val markerProvider = com.example.personalaibot.automation.SignalMarkerProvider(smc)
        // entry params ที่จูนไว้แล้ว (EntryTuning) เป็น baseline — SL/TP tuning ต้องวัดบนจุดเข้าเดียวกับที่ live ใช้จริง
        val tunedEntryBaseline = runCatching {
            com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager()
                .getTunedEntryParams(symbol, interval)
        }.getOrElse { emptyMap() }
        val markers = markerProvider.compute(candles, Int.MAX_VALUE, tunedEntryBaseline)

        val kinds = when (strategy) {
            "tsmom" -> listOf("MOM"); "trend" -> listOf("TR"); "reversal" -> listOf("REV")
            "donchian" -> listOf("DC"); "w52high" -> listOf("52H"); "ema1460", "ema14_60" -> listOf("E")
            "utbot", "ut" -> listOf("UT"); "threebar", "3br" -> listOf("3BR")
            else -> listOf("MOM", "TR", "REV", "DC", "52H", "E", "UT", "3BR")
        }

        val engine = com.example.personalaibot.automation.backtest.BacktestEngine()
        val kindOf = { label: String -> com.example.personalaibot.automation.signalKindOf(label) }
        val nameOf = { k: String -> signalAlertProvider.strategyName(k) }
        val mgr = runCatching { com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager() }.getOrNull()

        val sb = StringBuilder()
        sb.appendLine("🧬 **Adaptive Optimize — $symbol $interval** (${candles.size} แท่ง, ${fetched.source}) [scope=$scope]")
        sb.appendLine("${if (scope != "sltp") "entry grid (จุดเข้า) → " else ""}grid + mutation รอบค่าปัจจุบัน/ประวัติ → walk-forward 5 splits → permutation → Monte Carlo | 🧠 เรียนรู้จากประวัติการจูน + auto-apply เมื่อดีกว่า${if (dryRun) " (dry-run: ไม่บันทึก)" else ""}")
        sb.appendLine()

        var applied = 0
        for (kind in kinds) {
            val name = nameOf(kind)

            // baseline SL/TP = tuned params ที่ใช้อยู่ปัจจุบัน (ถ้ามีและไม่ overfit) ไม่งั้น default
            val currentTuning = mgr?.getStrategyTuning(symbol, interval, kind)?.takeIf { it.grade != "overfit" }
            val baselineParams = currentTuning?.let {
                com.example.personalaibot.automation.backtest.TpSlParams(it.sl_mult, it.tp_mult)
            } ?: com.example.personalaibot.automation.backtest.TpSlParams.defaultsFor(kind)

            // ── Entry params tuning (scope entry|both): จูน "จุดเข้า" โดย SL/TP คงค่าปัจจุบัน ──
            // edge ของกลยุทธ์อยู่ที่จุดเข้า — grid search lookback/period/threshold ของ kind นั้น
            var activeEntry: EntryParams? = null  // null = default
            var entryReport: String? = null
            if (scope != "sltp") {
                if (kind !in EntryParams.TUNABLE_KINDS) {
                    entryReport = "- 🎯 **Entry**: กลยุทธ์นี้เป็น pattern ล้วน ไม่มี params จุดเข้าให้จูน — ข้าม"
                } else {
                    val savedEntry = mgr?.getEntryTuning(symbol, interval, kind)?.takeIf { it.grade != "overfit" }
                    val baselineEntry = savedEntry?.let { EntryParams.deserialize(kind, it.params_json) }
                        ?: EntryParams.defaultsFor(kind)

                    fun runEntry(ep: EntryParams): com.example.personalaibot.automation.backtest.BacktestResult? {
                        val m = markerProvider.compute(candles, Int.MAX_VALUE, mapOf(kind to ep))
                        return runCatching {
                            engine.run(
                                symbol = "", interval = "", source = "",
                                candles = candles, markers = m, kindFilter = setOf(kind),
                                kindOf = kindOf, strategyName = nameOf,
                                tpSl = { k, s, c, i, a14, a6 ->
                                    com.example.personalaibot.automation.backtest.parameterizedTpSl(k, s, c, i, a14, a6, baselineParams)
                                }
                            )
                        }.getOrNull()
                    }

                    val baseRun = runEntry(baselineEntry)
                    var bestEntry = baselineEntry
                    var bestRun = baseRun
                    var bestScore = baseRun?.let { com.example.personalaibot.automation.backtest.ParamOptimizer.score(it) } ?: -999.0
                    var tried = 0
                    for (cand in EntryParams.gridFor(kind)) {
                        if (cand == baselineEntry) continue
                        val r = runEntry(cand) ?: continue
                        tried++
                        val sc = com.example.personalaibot.automation.backtest.ParamOptimizer.score(r)
                        if (sc > bestScore) { bestScore = sc; bestEntry = cand; bestRun = r }
                    }

                    // apply gate เดียวกับ evolve fix: วัดบนข้อมูลเต็ม + expectancy ดีกว่า + PF≥1.0 + ไม้≥10
                    val entryImproved = bestEntry != baselineEntry && bestRun != null && baseRun != null &&
                        bestRun!!.expectancyR > baseRun.expectancyR &&
                        bestRun.profitFactor >= 1.0 && bestRun.totalTrades >= 10
                    // Holdout 30% ท้าย (2026-08-18): grid เลือกจากข้อมูลเต็ม = in-sample ล้วน
                    // → เช็กซ้ำว่า entry ใหม่ยังไม่แพ้ค่าเดิมในช่วงท้ายก่อน apply จริง
                    // (เคสจริง: entry apply ไปหลายตัวโดยไม่มี OOS validation เลย)
                    val tailStart = (candles.size * 0.7).toInt()
                    fun runEntryTail(ep: EntryParams) = runCatching {
                        engine.run(
                            symbol = "", interval = "", source = "",
                            candles = candles, markers = markerProvider.compute(candles, Int.MAX_VALUE, mapOf(kind to ep)),
                            kindFilter = setOf(kind), kindOf = kindOf, strategyName = nameOf,
                            tpSl = { k, s, c, i, a14, a6 ->
                                com.example.personalaibot.automation.backtest.parameterizedTpSl(k, s, c, i, a14, a6, baselineParams)
                            },
                            startIndex = tailStart
                        )
                    }.getOrNull()
                    val baseTail = if (entryImproved && !dryRun) runEntryTail(baselineEntry) else null
                    val bestTail = if (entryImproved && !dryRun) runEntryTail(bestEntry) else null
                    val tailEvidence = bestTail != null && baseTail != null && bestTail.totalTrades >= 5
                    val entryBlocked = entryImproved && tailEvidence &&
                        (bestTail!!.expectancyR < baseTail!!.expectancyR || bestTail.profitFactor < 1.0)
                    val entrySaved = !dryRun && entryImproved && !entryBlocked && mgr != null
                    if (entrySaved) {
                        mgr!!.saveEntryTuning(
                            symbol, interval, kind, EntryParams.serialize(kind, bestEntry),
                            score = bestScore, expectancyR = bestRun!!.expectancyR,
                            profitFactor = bestRun.profitFactor, trades = bestRun.totalTrades,
                            grade = null, source = "entry-optimize"
                        )
                        applied++
                    }
                    // ค่าที่เคยจูนไว้ (savedEntry) ยังใช้ต่อใน scope=both แม้รอบนี้ไม่เจอค่าที่ดีกว่า
                    activeEntry = when {
                        entryImproved && !entryBlocked -> bestEntry
                        baselineEntry != EntryParams.defaultsFor(kind) -> baselineEntry
                        else -> null
                    }
                    entryReport = "- 🎯 **Entry**: เดิม `${EntryParams.describe(kind, baselineEntry)}` (avg ${"%+.2f".format(baseRun?.expectancyR ?: 0.0)}R) → ใหม่ `${EntryParams.describe(kind, bestEntry)}` (avg ${"%+.2f".format(bestRun?.expectancyR ?: 0.0)}R, PF ${"%.2f".format(bestRun?.profitFactor ?: 0.0)}, ${bestRun?.totalTrades ?: 0} ไม้, ลอง $tried combos) ${if (entryImproved) "📈 ดีขึ้น" else "⏸ ไม่ดีกว่า — คงค่าเดิม"}${if (entryBlocked) " — 🚫 บล็อก: holdout 30% ท้าย แพ้ค่าเดิม/PF<1 (avg ${"%+.2f".format(bestTail?.expectancyR ?: 0.0)}R vs เดิม ${"%+.2f".format(baseTail?.expectancyR ?: 0.0)}R)" else if (entrySaved) " — 💾 APPLY แล้ว (signal alert ใช้ params จุดเข้าใหม่ตั้งแต่สัญญาณถัดไป)" else ""}"
                }
                if (scope == "entry") {
                    sb.appendLine("### $name ($kind)")
                    sb.appendLine(entryReport)
                    sb.appendLine()
                    continue
                }
            }

            // markers ของ kind นี้ — scope=both ที่จูนจุดเข้าได้ ต้อง recompute ด้วย entry params ใหม่ก่อนจูน SL/TP ต่อ
            val entryOverride = activeEntry?.let { mapOf(kind to it) } ?: emptyMap()
            val kindMarkers = if (entryOverride.isEmpty()) markers
                else markerProvider.compute(candles, Int.MAX_VALUE, entryOverride)

            val ranked = com.example.personalaibot.automation.backtest.ParamOptimizer.gridSearch(
                kind, candles, kindMarkers, engine, kindOf, nameOf
            )
            val gridBest = ranked.firstOrNull { it.score > -999.0 }

            // ── Adaptive run: วัดจริงทุก candidate + บันทึกเข้าความจำ ──
            val adaptive = com.example.personalaibot.automation.backtest.AdaptiveOptimizer.run(
                kind, symbol, interval, candles, kindMarkers, engine, kindOf, nameOf, baselineParams
            )
            val bestParams = when {
                adaptive.bestScore > -999.0 -> adaptive.bestParams
                gridBest != null -> gridBest.params
                else -> {
                    sb.appendLine("### $name\nไม่มี combo ที่ไม้พอ (≥5) — ข้าม\n")
                    continue
                }
            }

            val wf = com.example.personalaibot.automation.backtest.WalkForward.run(
                kind, candles, markerProvider, engine, kindOf, nameOf,
                entryParams = entryOverride
            )
            val bestFull = runCatching {
                engine.run(
                    symbol = symbol, interval = interval, source = fetched.source,
                    candles = candles, markers = kindMarkers, kindFilter = setOf(kind),
                    kindOf = kindOf, strategyName = nameOf,
                    tpSl = { k, s, c, i, a14, a6 ->
                        com.example.personalaibot.automation.backtest.parameterizedTpSl(
                            k, s, c, i, a14, a6, bestParams
                        )
                    }
                )
            }.getOrNull()
            val mc = com.example.personalaibot.automation.backtest.MonteCarlo.run(
                bestFull?.trades?.map { it.pnlMoney } ?: emptyList()
            )
            val perm = com.example.personalaibot.automation.backtest.PermutationTest.run(
                kind, candles, kindMarkers, engine, kindOf, nameOf, bestParams
            )
            val overfit = com.example.personalaibot.automation.backtest.OverfittingScore.compute(wf, perm, mc)

            val gradeTh = when (overfit.grade) {
                "healthy" -> "✅ สุขภาพดี"; "moderate" -> "🟡 ปานกลาง"; "overfit" -> "🔴 overfit"; else -> "❓"
            }
            // AUTO-APPLY: ดีกว่าค่าเดิม + ไม่ overfit + มี edge จริงบนข้อมูลเต็ม (PF≥1.0, ไม้≥10 — เกณฑ์เดียวกับ evolve/entry gate)
            // เดิมขาด PF/ไม้ → params ของกลยุทธ์ที่ PF<1 (ขาดทุนโดยรวม) ก็ถูก apply ได้ถ้า score ดีขึ้น
            // Hard blocks เพิ่ม 2026-08-18 (เคสจริง: 15m มี 4 กลยุทธ์ OOS Sharpe ติดลบ/permutation fail แต่ถูก apply):
            //  - OOS Sharpe ≤ 0 → params แพ้ out-of-sample ห้ามใช้จริงเด็ดขาด
            //  - permutation p ≥ 0.10 → ไม่มี edge เหนือความสุ่ม ห้ามใช้จริง
            val blockReasons = buildList {
                if (wf.nSplits > 0 && wf.oosAvgSharpe <= 0) add("OOS Sharpe ${"%.2f".format(wf.oosAvgSharpe)} ≤ 0")
                if (perm.nPermutations > 0 && perm.pValue >= 0.10) add("permutation p=${"%.3f".format(perm.pValue)} ไม่มี edge")
            }
            val saved = !dryRun && adaptive.improved && overfit.grade != "overfit" &&
                adaptive.bestProfitFactor >= 1.0 && adaptive.bestTrades >= 10 && blockReasons.isEmpty() && mgr != null
            if (saved) {
                mgr!!.saveStrategyTuning(
                    symbol, interval, kind, bestParams.slMult, bestParams.tpMult,
                    score = overfit.overfittingPct, grade = overfit.grade, source = "adaptive"
                )
                applied++
            }

            sb.appendLine("### $name ($kind)")
            if (entryReport != null) sb.appendLine(entryReport)
            sb.appendLine("- **เดิม**: SL ${baselineParams.slMult}× / TP ${baselineParams.tpMult}× (score ${"%.3f".format(adaptive.baselineScore)}) → **ใหม่**: SL ${bestParams.slMult}× / TP ${bestParams.tpMult}× (score ${"%.3f".format(adaptive.bestScore)}) ${if (adaptive.improved) "📈 ดีขึ้น" else "⏸ ไม่ดีกว่า — คงค่าเดิม"}")
            sb.appendLine("- **ผล params ใหม่**: ${adaptive.bestTrades} ไม้, win ${"%.0f%%".format(adaptive.bestWinRate * 100)}, PF ${"%.2f".format(adaptive.bestProfitFactor)}, Sharpe ${"%.2f".format(adaptive.bestSharpe)}, avg ${"%+.2f".format(adaptive.bestExpectancyR)}R (ลอง ${adaptive.candidatesTried} candidates)")
            sb.appendLine(if (adaptive.learnedFromTrials > 0) "- 🧠 **ประวัติการจูน ${adaptive.learnedFromTrials} ครั้ง**: ${adaptive.directionInsight}" else "- 🧠 ยังไม่มีประวัติการจูน — ระบบจะเริ่มจำจากรอบนี้เป็นต้นไป")
            if (wf.nSplits > 0) {
                sb.appendLine("- **Walk-forward** ${wf.nSplits} splits: IS Sharpe ${"%.2f".format(wf.inSampleAvgSharpe)} → OOS ${"%.2f".format(wf.oosAvgSharpe)} (ratio ${"%.2f".format(wf.overfittingRatio)})${if (wf.likelyOverfit) " ⚠️ น่าสงสัย overfit" else ""}, param stability CV ${"%.2f".format(wf.paramStabilityCv)}")
            }
            if (perm.nPermutations > 0) {
                sb.appendLine("- **Permutation**: p=${"%.3f".format(perm.pValue)} ${if (perm.isSignificant) "✅ มี edge เหนือความบังเอิญ" else "❌ ไม่ต่างจากสุ่ม"}")
            }
            if (mc.nSimulations > 0) {
                sb.appendLine("- **Monte Carlo**: P(พอร์ตพัง) ${"%.1f".format(mc.probabilityOfRuin * 100)}%, P(กำไร) ${"%.1f".format(mc.probabilityOfProfit * 100)}%, p95 DD ${"%.1f".format(mc.p95MaxDrawdown * 100)}%")
            }
            val blockSuffix = if (blockReasons.isNotEmpty()) " — 🚫 บล็อก: ${blockReasons.joinToString(", ")}" else ""
            sb.appendLine("- 🎯 **Overfitting ${"%.0f".format(overfit.overfittingPct)}% → $gradeTh**${if (saved) " — 💾 AUTO-APPLY แล้ว (signal alert ใช้ params ใหม่ตั้งแต่สัญญาณถัดไป)" else if (!dryRun && adaptive.improved) " — ไม่บันทึก (เกรด/PF/จำนวนไม้ไม่ผ่านเกณฑ์)$blockSuffix" else "$blockSuffix"}")
            sb.appendLine()
        }

        if (!dryRun) {
            sb.appendLine(if (applied > 0) "💾 AUTO-APPLY แล้ว $applied รายการ — signal alert ของ $symbol $interval จะใช้ params ที่จูนแล้วตั้งแต่สัญญาณถัดไป"
            else "⏸ ไม่มีกลยุทธ์ไหนดีกว่าค่าเดิมพอจะเปลี่ยน — ระบบคง params เดิม (ประวัติการลองถูกบันทึกเพื่อเรียนรู้ต่อ)")
        } else {
            sb.appendLine("ℹ️ dry-run — ยังไม่บันทึก (ลบ apply=off ออกเพื่อให้ระบบ auto-apply เมื่อ params ใหม่ดีกว่า)")
        }
        sb.toString().trim()
    }

    /**
     * trading_backtest_evolve — AI สะท้อนผลปรับ params ทีละนิดเป็นช่วงๆ (moss evolution)
     * apply=on บันทึก params สุดท้ายถ้าผลวิวัฒน์ดีกว่า params เดิม
     */
    private fun executeBacktestEvolve(args: Map<String, String>): String {
        val symbol = (args["symbol"] ?: "XAUUSD").trim().uppercase()
        val interval = (args["interval"] ?: "1h").trim().lowercase()
        val strategy = (args["strategy"] ?: "all").trim().lowercase()
        val applyOn = (args["apply"] ?: "off").trim().lowercase() == "on"
        // interval=all ขยายเป็น 3 TF จริง (เหตุผลเดียวกับ optimize — กัน tuning ถูกเซฟใต้ key "all" ที่ live ไม่อ่าน)
        val tfs = if (interval == "all") listOf("15m", "1h", "4h") else listOf(interval)
        val tfLabel = if (interval == "all") "all TF (15m/1h/4h)" else interval
        val label = "Backtest Evolution $symbol $tfLabel [$strategy]"
        com.example.personalaibot.automation.backtest.LongTaskRunner.launch("evolve", label) {
            val text = tfs.map { tf -> runEvolveTask(symbol, tf, strategy, applyOn) }.joinToString("\n\n")
            // ดึงชื่อกลยุทธ์จริงจากผลลัพธ์ (กัน model พูดมั่วว่าตัวไหนดีขึ้น/แย่ลง)
            val sectionRx = Regex("### (.+?) \\([A-Z0-9]+\\)\\nparams:[^\\n]*?(📈 ดีขึ้น|📉 แย่ลง|➖ เท่าเดิม)")
            val improved = mutableListOf<String>()
            val worsened = mutableListOf<String>()
            sectionRx.findAll(text).forEach { m ->
                when (m.groupValues[2]) {
                    "📈 ดีขึ้น" -> improved += m.groupValues[1]
                    "📉 แย่ลง" -> worsened += m.groupValues[1]
                }
            }
            val savedCount = Regex("💾 บันทึก params สุดท้ายเข้าระบบแล้ว").findAll(text).count()
            val speech = when {
                text.startsWith("❌") -> "Evolution $symbol ไม่สำเร็จครับ"
                else -> buildString {
                    append("Backtest evolution $symbol $tfLabel เสร็จแล้วครับ ")
                    if (improved.isNotEmpty()) append("กลยุทธ์ที่วิวัฒน์แล้วดีขึ้นคือ ${improved.joinToString(" และ ")} ")
                    if (worsened.isNotEmpty()) append("ส่วนที่แย่ลงคือ ${worsened.joinToString(" และ ")} ")
                    if (improved.isEmpty() && worsened.isEmpty()) append("ผลแทบไม่ต่างจาก params เดิมทุกกลยุทธ์ ")
                    append(
                        when {
                            applyOn -> "บันทึก params ที่ดีขึ้นเข้าระบบแล้ว $savedCount กลยุทธ์ครับ"
                            improved.isNotEmpty() -> "ถ้าต้องการให้ระบบใช้ params ที่ดีขึ้นจริง สั่งผมว่า apply ผล evolution ได้เลยครับ"
                            else -> "ยังไม่มี params ใหม่ที่คุ้มจะบันทึกครับ"
                        }
                    )
                    append(" รายละเอียดอยู่ในแชทครับ")
                }
            }
            text to speech
        }
        return "⏳ รับคำสั่งแล้ว — กำลังรัน **$label** ในเบื้องหลัง (session แยก อาจใช้เวลาหลายนาที) " +
            "เมื่อเสร็จระบบจะสรุปผลให้อัตโนมัติ (แชท + เสียง) — ตอบผู้ใช้สั้นๆ ว่ากำลังดำเนินการอยู่ แล้วคุยเรื่องอื่นต่อได้ตามปกติ"
    }

    /** ตัวงาน evolve จริง (รันใน LongTaskRunner) */
    private suspend fun runEvolveTask(symbol: String, interval: String, strategy: String, applyOn: Boolean): String {

        // SMC ใช้ SL/TP จากโครงสร้างตลาด (structure-based) ไม่ใช่ ATR multiplier — tune ด้วย evolve ไม่ได้
        val s = strategy.trim().lowercase()
        val known = setOf("", "all", "tsmom", "trend", "reversal", "donchian", "w52high", "ema1460", "ema14_60", "utbot", "ut", "threebar", "3br")
        if (s == "smc") {
            return "ℹ️ SMC ใช้ SL/TP จากโครงสร้างตลาด (swing high/low) ไม่ได้ใช้ ATR multiplier จึง tune ด้วย evolution ไม่ได้ — ใช้ได้เฉพาะ 8 กลยุทธ์ classic: tsmom, trend, reversal, donchian, w52high, ema1460, utbot, threebar"
        }
        if (s !in known) {
            return "❌ ไม่รู้จักกลยุทธ์ '$strategy' — เลือกได้: tsmom, trend, reversal, donchian, w52high, ema1460, utbot, threebar หรือ all"
        }

        val smc = SmcApiService(client)
        val fetched = runCatching { smc.fetchBacktestCandles(symbol, interval) }
            .getOrElse { return "❌ ดึงแท่งเทียนย้อนหลังไม่ได้: ${it.message}" }
        if (fetched.candles.size < 800) {
            return "❌ แท่งเทียนไม่พอสำหรับ evolution (${fetched.candles.size} < 800) — ลอง TF ใหญ่ขึ้น"
        }
        val candles = fetched.candles
        // entry params ที่จูนแล้ว (EntryTuning) — evolve ต้องวัดบนจุดเข้าเดียวกับที่ live ใช้จริง
        val tunedEntry = runCatching {
            com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager()
                .getTunedEntryParams(symbol, interval)
        }.getOrElse { emptyMap() }
        val markers = com.example.personalaibot.automation.SignalMarkerProvider(smc)
            .compute(candles, Int.MAX_VALUE, tunedEntry)

        val kinds = when (strategy) {
            "tsmom" -> listOf("MOM"); "trend" -> listOf("TR"); "reversal" -> listOf("REV")
            "donchian" -> listOf("DC"); "w52high" -> listOf("52H"); "ema1460", "ema14_60" -> listOf("E")
            "utbot", "ut" -> listOf("UT"); "threebar", "3br" -> listOf("3BR")
            else -> listOf("MOM", "TR", "REV", "DC", "52H", "E", "UT", "3BR")
        }

        val engine = com.example.personalaibot.automation.backtest.BacktestEngine()
        val kindOf = { label: String -> com.example.personalaibot.automation.signalKindOf(label) }
        val nameOf = { k: String -> signalAlertProvider.strategyName(k) }
        val evo = com.example.personalaibot.automation.backtest.BacktestEvolution(geminiService)
        val mgr = runCatching { com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager() }.getOrNull()

        val sb = StringBuilder()
        sb.appendLine("🧬 **Backtest Evolution — $symbol $interval** (${candles.size} แท่ง, 8 ช่วง)")
        sb.appendLine("AI สะท้อนผลทีละช่วง ปรับ ≤10%/รอบ หนีค่าเริ่มต้นไม่เกิน ±30%")
        sb.appendLine()

        var applied = 0
        for (kind in kinds) {
            val name = nameOf(kind)
            // ต่อเนื่องจาก optimize: ถ้าเคย tuning/auto-apply ไว้ ใช้ params นั้นเป็นฐานวิวัฒน์ แทนค่า default
            val tuned = mgr?.getStrategyTuning(symbol, interval, kind)
            val baseParams = tuned?.let {
                com.example.personalaibot.automation.backtest.TpSlParams(
                    com.example.personalaibot.automation.backtest.TpSlParams.round2(it.sl_mult),
                    com.example.personalaibot.automation.backtest.TpSlParams.round2(it.tp_mult)
                )
            }
            // ── Reflection Memory: ประวัติการปรับ 5 ครั้งล่าสุด + ผลที่ตามมา → ใส่ prompt ให้ AI เรียนรู้จากความผิดพลาดตัวเอง
            val memoryLines = mgr?.getOptimizationTrials(symbol, interval, kind, 5)?.map { t ->
                "sl/tp ${t.sl_mult}/${t.tp_mult} → ไม้=${t.trades ?: "-"} avgR=${t.expectancy_r?.let { "%+.2f".format(it) } ?: "-"} PF=${t.profit_factor?.let { "%.2f".format(it) } ?: "-"} (${(t.delta_vs_baseline ?: 0.0).let { if (it >= 0) "ดีกว่า" else "แย่กว่า" }}ค่าก่อนหน้า ${"%+.2f".format(t.delta_vs_baseline ?: 0.0)}R, ${t.source})"
            } ?: emptyList()
            val result = runCatching {
                evo.evolve(kind, candles, markers, engine, kindOf, nameOf, initialOverride = baseParams, memoryLines = memoryLines)
            }.getOrNull()
            if (result == null) {
                sb.appendLine("### $name\n❌ evolution ล้มเหลว\n")
                continue
            }
            if (result.rounds.isEmpty()) {
                sb.appendLine("### $name\nไม่มีรอบที่รันได้ — ข้าม\n")
                continue
            }

            // ── บันทึก trials ของ run นี้ลงความจำ (OptimizationTrial, source=evolve) ──
            result.trials.forEach { t ->
                mgr?.saveOptimizationTrial(
                    symbol, interval, kind,
                    slMult = t.toSl, tpMult = t.toTp, score = t.totalR,
                    expectancyR = t.avgR, profitFactor = t.profitFactor, trades = t.trades,
                    deltaVsBaseline = t.deltaVsBaseline, applied = false, source = "evolve"
                )
            }
            mgr?.pruneOptimizationTrials(symbol, interval, kind)

            // ── FIX: Apply gate วัดบนข้อมูลเต็มทั้งชุด ไม่ใช่ผลรวม walk-forward ที่ noise ครอบงำ ──
            // เดิม: saved = gain > 0 (เปรียบเทียบเส้นทาง adaptive บน 8 segments ที่รอบละ 2-10 ไม้ → ฟลุ๊คเซฟค่าแย่)
            // ใหม่: finalParams ต้องรัน full backtest แล้วชนะ initial ทั้ง expectancy และ PF≥1 และไม้ ≥ 10
            fun fullRun(p: com.example.personalaibot.automation.backtest.TpSlParams) = runCatching {
                engine.run(
                    symbol = symbol, interval = interval, source = "",
                    candles = candles, markers = markers, kindFilter = setOf(kind),
                    kindOf = kindOf, strategyName = nameOf,
                    tpSl = { k, sd, c, i, a14, a6 ->
                        com.example.personalaibot.automation.backtest.parameterizedTpSl(k, sd, c, i, a14, a6, p)
                    }
                )
            }.getOrNull()
            val fullInit = fullRun(result.initialParams)
            val fullFinal = fullRun(result.finalParams)
            val fullOk = fullInit != null && fullFinal != null
            val fullBetter = fullOk &&
                fullFinal!!.expectancyR > fullInit!!.expectancyR &&
                fullFinal.profitFactor >= 1.0 &&
                fullFinal.totalTrades >= 10
            // Holdout 30% ท้าย (2026-08-18): fullRun เป็น in-sample (evolve เลือก params จากข้อมูลชุดเดียวกัน)
            // → เช็กซ้ำว่า params สุดท้ายยังไม่แพ้ค่าเดิมในช่วงท้ายก่อน apply จริง
            val tailStart = (candles.size * 0.7).toInt()
            fun tailRun(p: com.example.personalaibot.automation.backtest.TpSlParams) = runCatching {
                engine.run(
                    symbol = symbol, interval = interval, source = "",
                    candles = candles, markers = markers, kindFilter = setOf(kind),
                    kindOf = kindOf, strategyName = nameOf,
                    tpSl = { k, sd, c, i, a14, a6 ->
                        com.example.personalaibot.automation.backtest.parameterizedTpSl(k, sd, c, i, a14, a6, p)
                    },
                    startIndex = tailStart
                )
            }.getOrNull()
            val tailInit = if (fullBetter && applyOn) tailRun(result.initialParams) else null
            val tailFinal = if (fullBetter && applyOn) tailRun(result.finalParams) else null
            val tailEvidence = tailInit != null && tailFinal != null && tailFinal.totalTrades >= 5
            val tailBlocked = applyOn && fullBetter && tailEvidence &&
                (tailFinal!!.expectancyR < tailInit!!.expectancyR || tailFinal.profitFactor < 1.0)
            val saved = applyOn && mgr != null && fullBetter && !tailBlocked
            if (saved) {
                mgr!!.saveStrategyTuning(
                    symbol, interval, kind, result.finalParams.slMult, result.finalParams.tpMult,
                    score = (fullFinal!!.expectancyR - fullInit!!.expectancyR), grade = "evolved", source = "evolve"
                )
                applied++
            }

            val gain = result.evolvedTotalR - result.baselineTotalR
            sb.appendLine("### $name ($kind)")
            sb.appendLine("params: ${result.initialParams.slMult}/${result.initialParams.tpMult}${if (baseParams != null) " (ต่อจาก tuning ล่าสุด)" else " (ค่า default)"} → **${result.finalParams.slMult}/${result.finalParams.tpMult}** | รวมR เดิม ${"%+.2f".format(result.baselineTotalR)} → วิวัฒน์ **${"%+.2f".format(result.evolvedTotalR)}** (${if (gain > 0) "📈 ดีขึ้น" else if (gain < 0) "📉 แย่ลง" else "➖ เท่าเดิม"} ${"%+.2f".format(gain)}R)${if (result.usedAiReflection) " | 🤖 ใช้ AI reflection" else " | 📏 ใช้กฎ heuristic"}")
            // แสดงผล validation บนข้อมูลเต็มเสมอ — ผู้ใช้จะได้เห็นว่า apply หรือไม่เพราะอะไร
            if (fullOk) {
                sb.appendLine("🔎 ผลเต็ม ${candles.size} แท่ง: เดิม PF ${"%.2f".format(fullInit!!.profitFactor)} avgR ${"%+.2f".format(fullInit.expectancyR)} → ใหม่ PF ${"%.2f".format(fullFinal!!.profitFactor)} avgR ${"%+.2f".format(fullFinal.expectancyR)} ไม้ ${fullFinal.totalTrades} → ${if (saved) "💾 APPLY" else if (tailBlocked) "🚫 ไม่ apply (holdout 30% ท้าย: ใหม่ avgR ${"%+.2f".format(tailFinal?.expectancyR ?: 0.0)} vs เดิม ${"%+.2f".format(tailInit?.expectancyR ?: 0.0)})" else if (!fullBetter) "⛔ ไม่ apply (ใหม่ไม่ชนะบนข้อมูลเต็ม)" else "🚫 dry-run"}")
            }
            sb.appendLine("| รอบ | params | ไม้ | Win% | avgR | สะท้อนผล |")
            sb.appendLine("|---|---|---|---|---|---|")
            result.rounds.forEach { rd ->
                sb.appendLine("| ${rd.round} | ${rd.params.slMult}/${rd.params.tpMult} | ${rd.trades} | ${"%.0f%%".format(rd.winRate * 100)} | ${"%+.2f".format(rd.avgR)} | ${rd.reflection.take(60)} |")
            }
            if (saved) sb.appendLine("💾 บันทึก params สุดท้ายเข้าระบบแล้ว")
            sb.appendLine()
        }

        if (applyOn) {
            sb.appendLine(if (applied > 0) "💾 บันทึก tuning แล้ว $applied กลยุทธ์" else "ไม่มีกลยุทธ์ไหนดีกว่าเดิม — ยังใช้ params เดิม")
        } else {
            sb.appendLine("ℹ️ ยังไม่ได้บันทึก — รันด้วย apply=on เพื่อให้ระบบใช้ params ที่วิวัฒน์แล้ว (เฉพาะตัวที่ดีกว่าเดิม)")
        }
        return sb.toString().trim()
    }

    /**
     * trading_signal_stats (source=live) — สถิติจาก Signal Alert ที่ยิงจริง
     * (JarvisAutomationService บันทึกทุก signal + tracker ตามเช็ก TP/SL ทุก cycle)
     * range: today (default, เวลาท้องถิ่น) | 7d | all
     */
    private fun executeSignalStatsLive(args: Map<String, String>): String {
        val mgr = runCatching { com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager() }
            .getOrElse { return "❌ ยังเข้าถึงฐานข้อมูลไม่ได้: ${it.message}" }
        val range = (args["range"] ?: "today").trim().lowercase()
        val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
        val now = kotlinx.datetime.Clock.System.now()
        val sinceMs = when (range) {
            "all" -> 0L
            "7d" -> now.minus(7, kotlinx.datetime.DateTimeUnit.DAY, tz).toEpochMilliseconds()
            else -> { // today (local midnight)
                val local = now.toLocalDateTime(tz)
                kotlinx.datetime.LocalDateTime(local.year, local.monthNumber, local.dayOfMonth, 0, 0)
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
            val l = kotlinx.datetime.Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
            return "%02d:%02d".format(l.hour, l.minute)
        }
        fun fmtDate(ms: Long): String {
            val l = kotlinx.datetime.Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
            return "%02d/%02d %02d:%02d".format(l.dayOfMonth, l.monthNumber, l.hour, l.minute)
        }
        val showDate = range != "today"

        return buildString {
            appendLine("📊 **Signal Stats จาก Alert จริง — $rangeLabel** (${recs.size} สัญญาณ)")
            appendLine()
            // รายการสัญญาณ (ล่าสุดก่อน — จำกัด 20 รายการ)
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
            // สรุปรวม + รายกลยุทธ์
            fun summarize(list: List<com.example.personalaibot.db.SignalAlertRecord>): String {
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
        }.trim()
    }

    /**
     * trading_position_sizing — คำนวณขนาดไม้จากความเสี่ยง (pure math, ไม่ต้องเรียก API)
     * units = (balance × risk%) / |entry − stop_loss|
     */
    private fun executePositionSizing(args: Map<String, String>): String {
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

    /**
     * trading_correlation_matrix — Pearson correlation ของ daily returns ระหว่าง symbols
     * ดึงข้อมูลย้อนหลังจาก Yahoo chart API (closes รายวัน)
     */
    private suspend fun executeCorrelationMatrix(args: Map<String, String>): String {
        val symbols = (args["symbols"] ?: "BTC-USD,GC=F,^GSPC,EURUSD=X")
            .split(",").map { it.trim() }.filter { it.isNotBlank() }.take(6)
        if (symbols.size < 2) return "❌ ต้องระบุอย่างน้อย 2 symbols (คั่นด้วย comma)"
        val days = (args["days"]?.toIntOrNull() ?: 30).coerceIn(7, 365)

        // ดึง closes ของแต่ละ symbol
        val seriesMap = mutableMapOf<String, List<Double>>()
        val failed = mutableListOf<String>()
        symbols.forEach { sym ->
            val closes = fetchYahooDailyCloses(sym, days)
            if (closes != null && closes.size >= 5) seriesMap[sym] = closes else failed.add(sym)
        }
        if (seriesMap.size < 2) {
            return "❌ ดึงข้อมูลย้อนหลังไม่สำเร็จสำหรับ: ${(failed + seriesMap.keys.filter { seriesMap[it]!!.size < 5 }).joinToString(", ")}"
        }

        // คำนวณ daily returns แล้วจับคู่ความยาวให้เท่ากัน (ใช้จำนวนวันที่น้อยสุด)
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

    private suspend fun fetchYahooDailyCloses(symbol: String, days: Int): List<Double>? {
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

    private fun pearson(x: List<Double>, y: List<Double>): Double {
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

    // ─── US Economic Data (FRED) ─────────────────────────────────────────────

    /** preset name → (FRED series id, คำอธิบายภาษาไทย) */
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

    /**
     * trading_economic_data — ตัวเลขเศรษฐกิจมหภาคสหรัฐฯ จาก FRED
     * series = preset / FRED id / "overview" (สรุป 4 ตัวชี้วัดหลัก)
     */
    private suspend fun executeEconomicData(args: Map<String, String>): String {
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

    private suspend fun executeEconomicOverview(limit: Int, apiKey: String?): String {
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

    private fun formatFredReport(seriesId: String, label: String, window: List<Pair<String, Double>>): String {
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

    private suspend fun executeDeepAnalysisSuite(args: Map<String, String>): String {
        val symbol = args["symbol"] ?: return "Missing symbol"
        // รองรับทั้ง "timeframe" และ "interval" (model มักส่ง interval ตาม declaration)
        val tf = args["timeframe"] ?: args["interval"] ?: "1h"

        // Circuit breaker — bridge เพิ่งล่มภายใน 10 นาที: ข้าม HTTP ไป local engine เลย
        // (กันเสียเวลา 0.5-2 วิต่อครั้งทุกรอบเรียก เมื่อ ngrok offline ค้าง)
        val nowMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
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
            // ngrok offline / HTML error page ไม่ throw exception — ต้องตรวจเอง
            // แล้วตกไป local engine (ข้อมูลจาก TV) แทนการส่งขยะกลับเข้า tool loop
            val invalid = response.status.value !in 200..299 ||
                body.contains("ERR_NGROK") || body.contains("ngrok") && body.contains("offline") ||
                body.trimStart().startsWith("<")
            if (invalid) {
                throw Exception("bridge unreachable (HTTP ${response.status.value}, ngrok/html error page)")
            }
            deepSuiteBridgeDownUntilMs = 0L // bridge กลับมาแล้ว — ปิด circuit
            "═══ Deep Analysis Suite: $symbol $tf ═══\n$body"
        } catch (e: Exception) {
            // เปิด circuit 10 นาที — รอบถัดไปข้าม HTTP ไป local ตรงๆ
            deepSuiteBridgeDownUntilMs =
                kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 10 * 60_000L
            runLocalDeepSuite(symbol, tf, e.message ?: "bridge failed")
        }
    }

    /** Local fallback — คำนวณเองบนเครื่องด้วย AdvancedTradingEngine (ข้อมูลแท่งเทียน TV) */
    private suspend fun runLocalDeepSuite(symbol: String, tf: String, reason: String): String {
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

    /**
     * trading_fear_greed — Crypto Fear & Greed Index ตัวจริงจาก alternative.me
     * (เดิมผูกกับ MT5 server analytics ซึ่งไม่ใช่ดัชนีจริงและพังเมื่อ server ออฟไลน์)
     */
    private suspend fun executeFearGreed(@Suppress("UNUSED_PARAMETER") args: Map<String, String>): String {
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

    /**
     * trading_crypto_overview — ภาพรวมตลาดคริปโตจาก CoinGecko (ฟรี ไม่ต้องใช้ API Key)
     * market cap รวม, BTC/ETH dominance, volume 24h, เหรียญ trending + Fear & Greed ประกอบ
     */
    private suspend fun executeCryptoOverview(@Suppress("UNUSED_PARAMETER") args: Map<String, String>): String {
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

    private val indicatorProvider = com.example.personalaibot.automation.IndicatorAlertProvider(SmcApiService(client))

    /** เติมค่าที่ scanner คืน null/N/A ด้วยค่าที่คำนวณเองจากแท่งเทียน (300 แท่ง) */
    private suspend fun fillTaFromLocal(symbol: String, interval: String, data: Map<String, String>): Map<String, String> {
        fun bad(v: String?) = v == null || v == "N/A" || v == "null"
        val needsLocal = listOf("close", "RSI", "RSI[1]", "MACD.macd", "MACD.signal", "MACD.hist",
            "Stoch.K", "Stoch.D", "CCI20", "AO", "EMA20", "EMA50", "EMA200",
            "BB.upper", "BB.basis", "BB.lower", "BB.width", "ATR", "ADX", "ADX+DI", "ADX-DI"
        ).any { bad(data[it]) }
        if (!needsLocal) return data

        val tf = interval.lowercase().let { if (it == "1d") "1D" else it }
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

    private suspend fun executeTechnicalAnalysis(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "Missing required argument: symbol"
        val resolvedExchange = api.resolveExchange(symbol, args["exchange"])
        val interval = args["interval"] ?: "1h"
        val data = fillTaFromLocal(symbol, interval, api.getTechnicalAnalysis(symbol, resolvedExchange, interval))

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
            appendLine("📅 **Economic Calendar สัปดาห์นี้ (ForexFactory — Real-time, เวลาไทย)**")
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

package com.example.personalaibot.tools.trading

import io.ktor.client.HttpClient
import kotlinx.datetime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.example.personalaibot.automation.SignalAlertProvider
import com.example.personalaibot.automation.backtest.EntryParams
import com.example.personalaibot.automation.backtest.LongTaskRunner
import com.example.personalaibot.automation.backtest.BacktestResult
import com.example.personalaibot.automation.backtest.BacktestEngine
import com.example.personalaibot.automation.MixSignalEngine
import com.example.personalaibot.logDebug

/**
 * BacktestToolHandler - handles backtest execution, parameter optimization,
 * genetic/evolution simulation, and mix signal configuration.
 */
internal class BacktestToolHandler(
    private val client: HttpClient,
    private val signalAlertProvider: SignalAlertProvider
) {
    companion object {
        private const val MIN_EVOLVE_GAIN_R = 0.05
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun executeBacktest(args: Map<String, String>): String = executeBacktestInternal(args)
    fun executeOptimize(args: Map<String, String>): String = executeBacktestOptimizeV2Internal(args)
    fun executeEvolve(args: Map<String, String>): String = executeBacktestEvolveInternal(args)

    internal fun executeBacktestInternal(args: Map<String, String>): String {
        val symbol = (args["symbol"] ?: "XAUUSD").trim().uppercase()
        val interval = (args["interval"] ?: "1h").trim().lowercase()
        val strategy = (args["strategy"] ?: "all").trim().lowercase()
        val costsOn = (args["costs"] ?: "on").trim().lowercase() != "off"
        // mix: ผู้ใช้เลือกกลยุทธ์ที่จะโหวตเอง (เช่น "tsmom,trend,donchian,utbot")
        // mix_min_votes default = ครึ่งของจำนวนกลยุทธ์ปัดขึ้น
        val mixStrategies = args["mix_strategies"]?.trim()
        val mixKinds = if (strategy == "mix") {
            val ks = mixStrategies?.let { com.example.personalaibot.automation.MixSignalEngine.parseKinds(it) }
                ?: listOf("MOM", "REV") // default mix = MOM+REV (2 ตัวที่ forensics 2026-08-27 พิสูจน์ว่ามี edge)
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
    internal suspend fun runBacktestTask(
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

    /**
     * Dataset fingerprint สำหรับพิสูจน์ reproducibility (Evolution Audit 2026-08-20):
     * สองรันที่ใช้ข้อมูลชุดเดียวกันต้องได้ tag เดียวกันเป๊ะ — ถ้า tag ต่างกัน แปลว่า
     * dataset ขยับ (cache 2 ชม. หมดอายุแล้ว refetch หน้าต่างใหม่) → ผลสองรันนั้น
     * เทียบกันตรงๆ ไม่ได้ ไม่ใช่เพราะ strategy/params เปลี่ยน
     */
    internal fun datasetTag(candles: List<com.example.personalaibot.tools.trading.Candle>): String {
        if (candles.isEmpty()) return "DS#empty"
        var h = -3750763034362895579L  // FNV-1a 64 offset basis (วนครบตาม Long overflow)
        fun mix(v: Long) { h = (h xor v) * 1099511628211L }
        mix(candles.size.toLong())
        // sample กระจายทั่วทั้งชุด — เร็ว แต่จับความต่างได้ทั้งหน้าต่างเวลาและราคา
        val step = (candles.size / 64).coerceAtLeast(1)
        var i = 0
        while (i < candles.size) {
            val c = candles[i]
            mix(c.timestamp)
            mix((c.close * 100).toLong())
            i += step
        }
        val hex = h.toULong().toString(16).takeLast(8)
        fun f(ms: Long): String {
            val l = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
                .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
            return "%02d/%02d %02d:%02d".format(l.dayOfMonth, l.monthNumber, l.hour, l.minute)
        }
        return "DS#$hex · ${candles.size} แท่ง · ${f(candles.first().timestamp)} → ${f(candles.last().timestamp)} UTC"
    }

    /** รัน backtest 1 TF — คืน (ข้อความผลลัพธ์, BacktestResult?, error?) */
    internal suspend fun runBacktestOne(
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
            else -> setOf("MOM", "REV", "SMC") // default: เฉพาะตัวที่ forensics 2026-08-27 พิสูจน์ว่ามี edge
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
                .compute(candles, Int.MAX_VALUE, tunedEntry, kindFilter - setOf("SMC", "MIX"))
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
            appendLine("ข้อมูล: ${result.bars} แท่ง ts ${result.fromTs}→${result.toTs} | source=${result.source} | ต้นทุน=${if (costsOn) "on" else "off"} | ${datasetTag(candles)}")
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
        return Triple(formatBacktestResult(result, datasetTag(candles)) + buildRegimeSection(result, candles), result, null)
    }

    internal fun formatBacktestResult(r: com.example.personalaibot.automation.backtest.BacktestResult, dataset: String = ""): String {
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
            if (dataset.isNotEmpty()) appendLine("🧷 dataset: `$dataset` — รันอื่นที่ tag ตรงกันนี้ใช้ข้อมูลชุดเดียวกันเป๊ะ (เทียบผลได้ตรง)")
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
    internal fun buildRegimeSection(
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
    internal fun executeMixConfig(args: Map<String, String>): String {
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
     * DISCOVERY-ONLY: หา candidate ที่ดีกว่า + validation evidence แต่ห้ามเขียน production params
     * การ promote params เข้าระบบจริงเป็นหน้าที่ของ trading_backtest_evolve เท่านั้น
     */
    /**
     * Strategy Optimization V2 — authoritative discovery pipeline.
     * Jointly tunes entry + strategy-native SL/TP and validates on untouched holdout data.
     * Discovery-only: never writes production params.
     */
    internal fun executeBacktestOptimizeV2Internal(args: Map<String, String>): String {
        val symbol = (args["symbol"] ?: "XAUUSD").trim().uppercase()
        val interval = (args["interval"] ?: "1h").trim().lowercase()
        val strategy = (args["strategy"] ?: "all").trim().lowercase()
        val tfs = if (interval == "all") listOf("15m", "1h", "4h") else listOf(interval)
        val label = "Strategy Optimization V2 $symbol ${if (interval == "all") "all TF (15m/1h/4h)" else interval} [$strategy]"
        com.example.personalaibot.automation.backtest.LongTaskRunner.launch("optimize-v2", label) {
            val text = tfs.map { tf -> runStrategyOptimizationV2Task(symbol, tf, strategy) }.joinToString("\n\n")
            val pass = Regex("CANDIDATE —").findAll(text).count()
            val speech = if (text.startsWith("❌")) {
                "Strategy Optimization V2 $symbol ไม่สำเร็จครับ"
            } else {
                "Strategy Optimization V2 $symbol เสร็จแล้วครับ พบ candidate ที่ผ่านขั้น validation จำนวน $pass รายการ แต่ยังต้องผ่าน permutation, Monte Carlo และ Risk Gate ก่อนนำไปใช้จริงครับ รายละเอียดอยู่ในแชทครับ"
            }
            text to speech
        }
        return "⏳ รับคำสั่งแล้ว — กำลังรัน **$label** ในเบื้องหลัง; V2 จะ optimize จุดเข้าและ SL/TP ร่วมกัน พร้อม holdout validation และจะไม่แก้ production params"
    }

    private suspend fun runStrategyOptimizationV2Task(symbol: String, interval: String, strategy: String): String {
        val s = strategy.trim().lowercase()
        val kinds = when (s) {
            "tsmom" -> listOf("MOM")
            "trend" -> listOf("TR")
            "reversal" -> listOf("REV")
            "donchian" -> listOf("DC")
            "w52high" -> listOf("52H")
            "ema1460", "ema14_60" -> listOf("E")
            "utbot", "ut" -> listOf("UT")
            "threebar", "3br" -> listOf("3BR")
            "", "all" -> listOf("MOM", "REV") // default: เฉพาะตัวที่ forensics 2026-08-27 พิสูจน์ว่ามี edge
            "smc" -> return "ℹ️ SMC ใช้ SL/TP จากโครงสร้างตลาด จึงไม่เข้า V2 classic parameter optimizer — ควรใช้ structure-specific optimization แยก"
            else -> return "❌ ไม่รู้จักกลยุทธ์ '$strategy'"
        }
        val smc = SmcApiService(client)
        val fetched = runCatching { smc.fetchBacktestCandles(symbol, interval) }
            .getOrElse { return "❌ ดึงแท่งเทียนย้อนหลังไม่ได้: ${it.message}" }
        if (fetched.candles.size < 800) return "❌ แท่งเทียนไม่พอสำหรับ Strategy Optimization V2 (${fetched.candles.size} < 800)"

        val candles = fetched.candles
        val markerProvider = com.example.personalaibot.automation.SignalMarkerProvider(smc)
        val mgr = runCatching { com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager() }.getOrNull()
        val engine = com.example.personalaibot.automation.backtest.BacktestEngine()
        val kindOf = { label: String -> com.example.personalaibot.automation.signalKindOf(label) }
        val nameOf = { k: String -> signalAlertProvider.strategyName(k) }
        val tunedEntries = runCatching { mgr?.getTunedEntryParams(symbol, interval) ?: emptyMap() }.getOrElse { emptyMap() }

        val sb = StringBuilder()
        sb.appendLine("🧬 **Strategy Optimization V2 — $symbol $interval** (${candles.size} แท่ง, ${fetched.source})")
        sb.appendLine("🎯 Joint Entry + SL/TP → Train 60% → Validation 20% → Holdout OOS 20% → Parameter Neighborhood Stability")
        sb.appendLine("🔎 Discovery-only: ไม่มีการเขียน production params")
        sb.appendLine()

        for (kind in kinds) {
            val initialEntry = tunedEntries[kind] ?: com.example.personalaibot.automation.backtest.EntryParams.defaultsFor(kind)
            val initialTp = runCatching {
                mgr?.getStrategyTuning(symbol, interval, kind)?.let { com.example.personalaibot.automation.backtest.TpSlParams(it.sl_mult, it.tp_mult) }
            }.getOrNull() ?: com.example.personalaibot.automation.backtest.TpSlParams.defaultsFor(kind)

            val resultOrError = runCatching {
                com.example.personalaibot.automation.backtest.StrategyOptimizationV2.optimize(
                    kind = kind,
                    candles = candles,
                    markerProvider = markerProvider,
                    engine = engine,
                    kindOf = kindOf,
                    strategyName = nameOf,
                    initialEntry = initialEntry,
                    initialTpSl = initialTp
                )
            }
            if (resultOrError.isFailure) {
                sb.appendLine("### ${nameOf(kind)} ($kind)\n❌ V2 error: ${resultOrError.exceptionOrNull()?.message}\n")
                continue
            }
            val result = resultOrError.getOrThrow()
            val best = result.best
            sb.appendLine("### ${nameOf(kind)} ($kind)")
            if (best == null) {
                sb.appendLine("- 🚫 **HOLD**: ${result.recommendation}")
            } else {
                sb.appendLine("- **เดิม Entry**: `${com.example.personalaibot.automation.backtest.EntryParams.describe(kind, result.initialEntry)}`")
                sb.appendLine("- **V2 Entry**: `${com.example.personalaibot.automation.backtest.EntryParams.describe(kind, best.entry)}`")
                sb.appendLine("- **เดิม SL/TP**: ${result.initialTpSl.slMult}/${result.initialTpSl.tpMult} → **V2** ${best.tpSl.slMult}/${best.tpSl.tpMult}")
                sb.appendLine("- Train: ${best.trainTrades} ไม้, ${"%+.3f".format(best.trainExpectancyR)}R, score ${"%.3f".format(best.trainScore)}")
                sb.appendLine("- Validation: ${best.validationTrades} ไม้, ${"%+.3f".format(best.validationExpectancyR)}R, score ${"%.3f".format(best.validationScore)}")
                sb.appendLine("- **Holdout OOS**: ${best.holdoutTrades} ไม้, ${"%+.3f".format(best.holdoutExpectancyR)}R, PF ${"%.2f".format(best.holdoutPf)}, DD ${"%.1f".format(best.holdoutDd)}%")
                sb.appendLine("- Parameter neighborhood stability: ${"%.0f".format(best.neighborhoodStability * 100)}% | robust region candidates: ${result.robustRegionSize}")
                sb.appendLine("- **${result.recommendation}**")
            }
            sb.appendLine("- Evaluated ${result.candidatesEvaluated} joint candidates | split ${result.trainBars}/${result.validationBars}/${result.holdoutBars} bars")
            sb.appendLine()
        }
        return sb.toString().trim()
    }

    internal fun executeBacktestOptimizeInternal(args: Map<String, String>): String {
        val symbol = (args["symbol"] ?: "XAUUSD").trim().uppercase()
        val interval = (args["interval"] ?: "1h").trim().lowercase()
        val strategy = (args["strategy"] ?: "all").trim().lowercase()
        // Optimization is discovery-only. It must never promote production parameters.
        // Production promotion is owned exclusively by trading_backtest_evolve.
        val dryRun = true
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
                else -> "Adaptive optimize $symbol $tfLabel เสร็จแล้วครับ ผลลัพธ์เป็น candidate สำหรับ validation/promotion และยังไม่มีการเปลี่ยน production params — หากต้องการให้ระบบวิวัฒน์และ promote ให้ใช้ trading_backtest_evolve ครับ รายละเอียดอยู่ในแชทครับ"
            }
            text to speech
        }
        return "⏳ รับคำสั่งแล้ว — กำลังรัน **$label** ในเบื้องหลัง (session แยก อาจใช้เวลา 1-3 นาที) " +
            "เมื่อเสร็จระบบจะสรุปผลให้อัตโนมัติ (แชท + เสียง) — ตอบผู้ใช้สั้นๆ ว่ากำลังดำเนินการอยู่ แล้วคุยเรื่องอื่นต่อได้ตามปกติ"
    }

    /** ตัวงาน optimize จริง (รันใน LongTaskRunner) — scope: sltp=จูน SL/TP (default) | entry=จูน params จุดเข้า | both=จุดเข้าก่อนแล้ว SL/TP */
    internal suspend fun runOptimizeTask(symbol: String, interval: String, strategy: String, dryRun: Boolean, scope: String = "sltp"): String = withContext(Dispatchers.Default) {

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
        val kinds = when (strategy) {
            "tsmom" -> listOf("MOM"); "trend" -> listOf("TR"); "reversal" -> listOf("REV")
            "donchian" -> listOf("DC"); "w52high" -> listOf("52H"); "ema1460", "ema14_60" -> listOf("E")
            "utbot", "ut" -> listOf("UT"); "threebar", "3br" -> listOf("3BR")
            else -> listOf("MOM", "REV") // default: เฉพาะตัวที่ forensics 2026-08-27 พิสูจน์ว่ามี edge
        }
        // kindsOverride: ระบุ kind เองชัดเจน = forensics ย้อนหลังของ kind ที่ถูกตัดยังทำได้
        val markers = markerProvider.compute(candles, Int.MAX_VALUE, tunedEntryBaseline, kinds.toSet())

        val engine = com.example.personalaibot.automation.backtest.BacktestEngine()
        val kindOf = { label: String -> com.example.personalaibot.automation.signalKindOf(label) }
        val nameOf = { k: String -> signalAlertProvider.strategyName(k) }
        val mgr = runCatching { com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager() }.getOrNull()

        val sb = StringBuilder()
        sb.appendLine("🧬 **Adaptive Optimize — $symbol $interval** (${candles.size} แท่ง, ${fetched.source}) [scope=$scope]")
        sb.appendLine("🧷 dataset: `${datasetTag(candles)}`")
        sb.appendLine("${if (scope != "sltp") "entry grid (จุดเข้า) → " else ""}grid + mutation รอบค่าปัจจุบัน/ประวัติ → walk-forward 5 splits → permutation → Monte Carlo | 🧠 เรียนรู้จากประวัติการจูน | 🔎 discovery-only: ไม่เขียน production params")
        sb.appendLine()

        var applied = 0
        for (kind in kinds) {
            val name = nameOf(kind)

            // baseline SL/TP = tuned params ที่ใช้อยู่ปัจจุบัน (ถ้ามีและไม่ overfit) ไม่งั้น default
            val currentTuning = mgr?.getStrategyTuning(symbol, interval, kind)?.takeIf { it.risk_gate_eligible != 0L && it.source == "evolve" && it.grade != "overfit" }
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
                        val m = markerProvider.compute(candles, Int.MAX_VALUE, mapOf(kind to ep), setOf(kind))
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
                            candles = candles, markers = markerProvider.compute(candles, Int.MAX_VALUE, mapOf(kind to ep), setOf(kind)),
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
                    // P6.4: optimization is discovery-only. Keep the candidate in-memory for
                    // scope=both, but never persist it. Production promotion belongs to evolve.
                    val entrySaved = false
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
                else markerProvider.compute(candles, Int.MAX_VALUE, entryOverride, setOf(kind))

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
            // PROMOTION GATE (discovery only): ดีกว่าค่าเดิม + ไม่ overfit + มี edge จริงบนข้อมูลเต็ม (PF≥1.0, ไม้≥10 — เกณฑ์เดียวกับ evolve/entry gate)
            // เดิมขาด PF/ไม้ → params ของกลยุทธ์ที่ PF<1 (ขาดทุนโดยรวม) ก็ถูก apply ได้ถ้า score ดีขึ้น
            // Hard blocks เพิ่ม 2026-08-18 (เคสจริง: 15m มี 4 กลยุทธ์ OOS Sharpe ติดลบ/permutation fail แต่ถูก apply):
            //  - OOS Sharpe ≤ 0 → params แพ้ out-of-sample ห้ามใช้จริงเด็ดขาด
            //  - permutation p ≥ 0.10 → ไม่มี edge เหนือความสุ่ม ห้ามใช้จริง
            val blockReasons = buildList {
                if (wf.nSplits > 0 && wf.oosAvgSharpe <= 0) add("OOS Sharpe ${"%.2f".format(wf.oosAvgSharpe)} ≤ 0")
                if (perm.nPermutations > 0 && perm.pValue >= 0.10) add("permutation p=${"%.3f".format(perm.pValue)} ไม่มี edge")
            }
            // P6.4: discovery-only. Validation may identify a strong candidate, but optimize
            // is never allowed to persist production StrategyTuning/EntryTuning.
            val saved = false

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
            sb.appendLine("🔎 Discovery-only — พบ candidate ได้ แต่ไม่มี production params ถูกเปลี่ยน; หากต้องการ promote ต้องใช้ trading_backtest_evolve และสั่ง apply=on อย่างชัดเจน")
        } else {
            sb.appendLine("🔎 Discovery-only — ยังไม่บันทึก production params. หากต้องการ promote ให้ใช้ trading_backtest_evolve และสั่ง apply=on อย่างชัดเจน")
        }
        sb.toString().trim()
    }

    /**
     * trading_backtest_evolve — AI สะท้อนผลปรับ params ทีละนิดเป็นช่วงๆ (moss evolution)
     * apply=on บันทึก params สุดท้ายถ้าผลวิวัฒน์ดีกว่า params เดิม
     */
    internal fun executeBacktestEvolveInternal(args: Map<String, String>): String {
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
    internal suspend fun runEvolveTask(symbol: String, interval: String, strategy: String, applyOn: Boolean): String {

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
        val kinds = when (strategy) {
            "tsmom" -> listOf("MOM"); "trend" -> listOf("TR"); "reversal" -> listOf("REV")
            "donchian" -> listOf("DC"); "w52high" -> listOf("52H"); "ema1460", "ema14_60" -> listOf("E")
            "utbot", "ut" -> listOf("UT"); "threebar", "3br" -> listOf("3BR")
            else -> listOf("MOM", "REV") // default: เฉพาะตัวที่ forensics 2026-08-27 พิสูจน์ว่ามี edge
        }
        // kindsOverride: ระบุ kind เองชัดเจน = forensics ย้อนหลังของ kind ที่ถูกตัดยังทำได้
        val markers = com.example.personalaibot.automation.SignalMarkerProvider(smc)
            .compute(candles, Int.MAX_VALUE, tunedEntry, kinds.toSet())

        val engine = com.example.personalaibot.automation.backtest.BacktestEngine()
        val kindOf = { label: String -> com.example.personalaibot.automation.signalKindOf(label) }
        val nameOf = { k: String -> signalAlertProvider.strategyName(k) }
        val evo = com.example.personalaibot.automation.backtest.BacktestEvolution()
        val mgr = runCatching { com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager() }.getOrNull()

        val sb = StringBuilder()
        sb.appendLine("🧬 **Backtest Evolution — $symbol $interval** (${candles.size} แท่ง, 8 ช่วง)")
        sb.appendLine("🧷 dataset: `${datasetTag(candles)}` — เทียบกับรันอื่นได้ก็ต่อเมื่อ tag ตรงกัน")
        sb.appendLine("AI สะท้อนผลทีละช่วง ปรับ ≤10%/รอบ หนีค่าเริ่มต้นไม่เกิน ±30%")
        sb.appendLine()

        var applied = 0
        for (kind in kinds) {
            val name = nameOf(kind)
            // ต่อเนื่องจาก optimize: ถ้าเคย tuning/auto-apply ไว้ ใช้ params นั้นเป็นฐานวิวัฒน์ แทนค่า default
            val tuned = mgr?.getStrategyTuning(symbol, interval, kind)
                ?.takeIf { it.risk_gate_eligible != 0L && it.source == "evolve" && it.grade != "overfit" }
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
                    // causal delta (เทียบ params รอบก่อนบน segment เดียวกัน) มีให้ใช้ตั้งแต่ 2026-08-19
                    // — ถ้าไม่มี (รอบแรก) ค่อย fallback เป็น delta เทียบ initial แบบเดิม
                    deltaVsBaseline = t.deltaVsPrev ?: t.deltaVsBaseline, applied = false, source = "evolve"
                )
            }
            mgr?.pruneOptimizationTrials(symbol, interval, kind)

            // ── FIX: Apply gate วัดบนข้อมูลเต็มทั้งชุด ไม่ใช่ผลรวม walk-forward ที่ noise ครอบงำ ──
            // เดิม: saved = gain > 0 (เปรียบเทียบเส้นทาง adaptive บน 8 segments ที่รอบละ 2-10 ไม้ → ฟลุ๊คเซฟค่าแย่)
            // ใหม่: finalParams ต้องรัน full backtest แล้วชนะ initial แบบ "คุ้ม" — expectancy ต้องดีกว่า
            // แบบมีนัยสำคัญ (≥ +0.05R) ไม่ใช่แค่ +0.01R ที่อยู่ในระดับ noise สถิติของไม้ ~100 ตัว
            // (ผู้ใช้ตัดสินเองหลายรอบว่า "ดีขึ้นนิดเดียว ไม่คุ้มเซฟ" → เข้ารหัสดุลยพินิจนั้นลง gate เลย)
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
            // แยกเงื่อนไขทีละข้อ เพื่อบอกเหตุผลที่บล็อกได้ตรง (เดิมรวมเป็น "ไม่ชนะ"
            // ทั้งที่ expectancy/PF ดีขึ้นแต่โดน DD บล็อก — ผู้ใช้อ่านแล้วเข้าใจผิด)
            val condExpectancy = fullOk && fullFinal!!.expectancyR >= fullInit!!.expectancyR + MIN_EVOLVE_GAIN_R
            val condPf = fullOk && fullFinal!!.profitFactor >= 1.0
            val condTrades = fullOk && fullFinal!!.totalTrades >= 10
            // ไม่แลก expectancy ที่ดีขึ้นกับ drawdown ที่แย่ลงอย่างมีนัยสำคัญ
            val condDd = fullOk && fullFinal!!.maxDrawdownPct <= fullInit!!.maxDrawdownPct + 1.0
            val fullBetter = condExpectancy && condPf && condTrades && condDd
            // Holdout 30% ท้าย: แยก validation ออกจากข้อมูลที่ใช้ evolve ให้ชัดเจน
            // และใช้เกณฑ์ "ดีกว่าเดิม" จริง ไม่ใช่แค่ "ไม่แย่ลง" ก่อนบันทึก params
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
            val tailInit = if (fullBetter) tailRun(result.initialParams) else null
            val tailFinal = if (fullBetter) tailRun(result.finalParams) else null
            val tailEvidence = tailInit != null && tailFinal != null &&
                tailInit.totalTrades >= 10 && tailFinal.totalTrades >= 10
            val tailBetter = tailEvidence &&
                tailFinal!!.expectancyR >= tailInit!!.expectancyR &&
                tailFinal.profitFactor >= tailInit.profitFactor &&
                tailFinal.maxDrawdownPct <= tailInit.maxDrawdownPct + 1.0
            val tailBlocked = fullBetter && !tailBetter
            val saved = applyOn && mgr != null && fullBetter && tailBetter
            if (saved) {
                if (mgr!!.saveStrategyTuning(
                    symbol, interval, kind, result.finalParams.slMult, result.finalParams.tpMult,
                    score = (fullFinal!!.expectancyR - fullInit!!.expectancyR), grade = "evolved", source = "evolve",
                    riskGateEligible = true
                )) applied++
            }

            val gain = result.evolvedTotalR - result.baselineTotalR
            sb.appendLine("### $name ($kind)")
            sb.appendLine("params: ${result.initialParams.slMult}/${result.initialParams.tpMult}${if (baseParams != null) " (ต่อจาก tuning ล่าสุด)" else " (ค่า default)"} → **${result.finalParams.slMult}/${result.finalParams.tpMult}** | adaptive walk-forward R เดิม ${"%+.2f".format(result.baselineTotalR)} → วิวัฒน์ **${"%+.2f".format(result.evolvedTotalR)}** (${if (gain > 0) "📈" else if (gain < 0) "📉" else "➖"} ${"%+.2f".format(gain)}R — ใช้เป็นหลักฐานประกอบเท่านั้น; การตัดสิน apply ใช้ full + holdout validation)${if (result.usedAiReflection) " | 🤖 ใช้ AI reflection" else " | 📏 ใช้กฎ heuristic"})")
            // แสดงผล validation บนข้อมูลเต็มเสมอ — ผู้ใช้จะได้เห็นว่า apply หรือไม่เพราะอะไร
            if (fullOk) {
                // เหตุผลที่บล็อกแบบแยกข้อ — กันข้อความ "ไม่ชนะ" กว้างเกินจนเข้าใจผิด
                val blockParts = mutableListOf<String>()
                if (!condExpectancy) blockParts += "expectancy ดีขึ้นไม่ถึงเกณฑ์คุ้ม (${"%+.2f".format(fullFinal!!.expectancyR - fullInit!!.expectancyR)}R < +${"%.2f".format(MIN_EVOLVE_GAIN_R)}R)"
                if (!condPf) blockParts += "PF ${"%.2f".format(fullFinal!!.profitFactor)} < 1.0"
                if (!condTrades) blockParts += "ไม้น้อยเกิน (${fullFinal!!.totalTrades} < 10)"
                if (!condDd) blockParts += "MaxDD แย่ลงเกิน +1% (${"%.1f".format(fullInit!!.maxDrawdownPct)}% → ${"%.1f".format(fullFinal!!.maxDrawdownPct)}%)"
                // holdout: ถ้าไม้ไม่พอ (<10) ต้องบอกตรงๆ — เดิมพิมพ์ avgR +0.00 PF 0.00 ซึ่งดูเหมือนบั๊ก
                val tailNote = if (!tailEvidence) {
                    "holdout 30% มีไม้ไม่พอประเมิน (เดิม ${tailInit?.totalTrades ?: 0} ไม้ / ใหม่ ${tailFinal?.totalTrades ?: 0} ไม้ ต้องการ ≥10) → ไม่เสี่ยงเซฟ"
                } else {
                    "holdout 30% ไม่ยืนยัน: ใหม่ avgR ${"%+.2f".format(tailFinal?.expectancyR ?: 0.0)} PF ${"%.2f".format(tailFinal?.profitFactor ?: 0.0)} DD ${"%.1f".format(tailFinal?.maxDrawdownPct ?: 0.0)}% vs เดิม avgR ${"%+.2f".format(tailInit?.expectancyR ?: 0.0)} PF ${"%.2f".format(tailInit?.profitFactor ?: 0.0)} DD ${"%.1f".format(tailInit?.maxDrawdownPct ?: 0.0)}%"
                }
                sb.appendLine("🔎 ผลเต็ม ${candles.size} แท่ง: เดิม PF ${"%.2f".format(fullInit!!.profitFactor)} avgR ${"%+.2f".format(fullInit.expectancyR)} DD ${"%.1f".format(fullInit.maxDrawdownPct)}% → ใหม่ PF ${"%.2f".format(fullFinal!!.profitFactor)} avgR ${"%+.2f".format(fullFinal.expectancyR)} DD ${"%.1f".format(fullFinal.maxDrawdownPct)}% ไม้ ${fullFinal.totalTrades} → ${if (saved) "💾 APPLY" else if (tailBlocked) "🚫 ไม่ apply ($tailNote)" else if (!fullBetter) "⛔ ไม่ apply — ${blockParts.joinToString("; ")}" else "🚫 dry-run"}")
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

}

package com.skyliner2008.jarvis.service

import com.skyliner2008.jarvis.createHttpClient
import com.skyliner2008.jarvis.automation.*
import com.skyliner2008.jarvis.data.GeminiService
import com.skyliner2008.jarvis.data.ModelConfig
import com.skyliner2008.jarvis.db.AlertJob
import com.skyliner2008.jarvis.db.JarvisDatabase
import com.skyliner2008.jarvis.tools.trading.AdvancedTradingEngine
import com.skyliner2008.jarvis.tools.trading.SmcApiService
import com.skyliner2008.jarvis.tools.trading.TradingApiService
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal data class SupervisorResult(
    val decision: String,
    val adjSl: Double?,
    val adjTp: Double?,
    val confidence: Int,
    val reasonTh: String
)

internal class TradingAlertEvaluator(
    private val database: JarvisDatabase,
    private val automationManager: AutomationManager,
    private val tradingApi: TradingApiService,
    private val smcApi: SmcApiService,
    private val evaluator: AutomationEvaluator,
    private val advancedEngine: AdvancedTradingEngine,
    private val indicatorProvider: IndicatorAlertProvider,
    private val smcAlertProvider: SmcAlertProvider,
    private val smcFlowProvider: SmcFlowAlertProvider,
    private val strategySignalProvider: StrategySignalProvider,
    private val signalAlertProvider: SignalAlertProvider,
    private val scope: CoroutineScope
) {
    private val geminiClient = createHttpClient()
    val signalCycleCache = mutableMapOf<String, Map<String, String>>()
    val signalCycleFetched = mutableSetOf<String>()
    val signalCycleHits = AtomicInteger(0)

    private val alertAiMutex = Mutex()
    private val signalAlertLastHandledAt = ConcurrentHashMap<String, Long>()
    private val signalAlertLastHandledId = ConcurrentHashMap<String, Long>()
    private val signalAlertCooldownMs = 90_000L
    private val signalLastLoggedAction = ConcurrentHashMap<String, String>()

    var consecutiveNetworkFailures = 0
    var emptyCycleCount = 0

    fun clearCycleCache() {
        signalCycleCache.clear()
        signalCycleFetched.clear()
        signalCycleHits.set(0)
    }

    private fun setting(key: String): String = runCatching {
        database.jarvisDatabaseQueries.getSetting(key).executeAsOneOrNull() ?: ""
    }.getOrDefault("")

    private fun settingEnabled(key: String, default: Boolean): Boolean {
        val v = setting(key)
        return if (v.isBlank()) default else (v == "true" || v == "1")
    }

    fun effectiveIntervalMs(job: AlertJob): Long {
        val baseMs = job.interval_minutes * 60_000L
        try {
            val condition = automationJson.decodeFromString(AutomationCondition.serializer(), job.condition_json)
            // เฉพาะตัวเลขเท่านั้น (EQ/CONTAINS ไม่มี notion ของระยะห่าง)
            if (condition.operator != ConditionOperator.GT && condition.operator != ConditionOperator.LT &&
                condition.operator != ConditionOperator.GTE && condition.operator != ConditionOperator.LTE
            ) return baseMs

            val target = condition.value.replace(",", "").replace("%", "").trim().toDoubleOrNull() ?: return baseMs
            val last = job.last_value?.replace(",", "")?.replace("%", "")?.trim()?.toDoubleOrNull() ?: return baseMs
            if (target == 0.0) return baseMs

            val distancePct = kotlin.math.abs(last - target) / kotlin.math.abs(target)
            return when {
                distancePct < 0.001 -> minOf(baseMs, 30_000L)   // ใกล้มาก → 30 วิ
                distancePct < 0.005 -> minOf(baseMs, 60_000L)   // ใกล้ → ≤ 1 นาที
                else -> baseMs
            }
        } catch (_: Exception) {
            return baseMs
        }
    }


    fun backoffDelayMs(): Long {
        val pow = consecutiveNetworkFailures.coerceIn(0, 8)
        val ms = 60_000L * (1L shl pow)
        return ms.coerceAtMost(15 * 60_000L)
    }


    suspend fun trackSignalOutcomes() {
        val open = automationManager.getOpenSignalAlerts()
        if (open.isEmpty()) return
        val candleCache = HashMap<String, List<com.skyliner2008.jarvis.tools.trading.Candle>>()
        for (rec in open) {
            try {
                val candles = candleCache.getOrPut(rec.symbol) {
                    val (sym, tf) = com.skyliner2008.jarvis.automation.IndicatorAlertProvider.splitSymbolAndTf(rec.symbol)
                    runCatching { smcApi.fetchCandlesWithSource(sym, tf, 300).candles }.getOrElse { emptyList() }
                }
                if (candles.isEmpty()) continue
                val isBuy = rec.side == "BUY"
                for (c in candles) {
                    if (c.timestamp <= rec.bar_time) continue // เฉพาะแท่งหลังจุดสัญญาณ
                    val hitSl = if (isBuy) c.low <= rec.sl else c.high >= rec.sl
                    val hitTp = if (isBuy) c.high >= rec.tp else c.low <= rec.tp
                    if (hitSl) {
                        automationManager.closeSignalAlert(rec.id, "SL", c.timestamp, rec.sl, -1.0)
                        break
                    }
                    if (hitTp) {
                        val rr = rec.rr ?: (kotlin.math.abs(rec.tp - rec.entry) / kotlin.math.abs(rec.entry - rec.sl))
                        automationManager.closeSignalAlert(rec.id, "TP", c.timestamp, rec.tp, rr)
                        break
                    }
                }
            } catch (e: Exception) {
                logError("AutomationService", "trackSignalOutcomes #${rec.id} failed: ${e.message}", e)
            }
        }
    }


    suspend fun fetchTechnicalAnalysisWithFallback(job: AlertJob): Map<String, String> {
        // รองรับเลือก TF ด้วย suffix เช่น XAUUSD@15m (default 1h)
        val (baseSymbol, tf) = com.skyliner2008.jarvis.automation.IndicatorAlertProvider.splitSymbolAndTf(job.symbol)
        val candidates = when {
            job.exchange != null -> listOf(job.exchange!!)
            else -> {
                val s = baseSymbol.removeSuffix("=X")
                when {
                    s == "XAUUSD" || s == "XAGUSD" || s == "GOLD" || s == "SILVER" ->
                        listOf("OANDA", "FX_IDC", "TVC")
                    s.length == 6 && s.all { it.isLetter() } && !s.endsWith("USDT") ->
                        listOf("OANDA", "FX_IDC")
                    else -> listOf(tradingApi.resolveExchange(baseSymbol, null))
                }
            }
        }
        var lastResult: Map<String, String> = mapOf("error" to "no data")
        for (ex in candidates) {
            lastResult = tradingApi.getTechnicalAnalysis(baseSymbol, ex, tf)
            if (!lastResult.containsKey("error") && lastResult["close"] != "N/A") {
                logDebug("AutomationService", "TA fallback OK: $baseSymbol@$tf via $ex")
                return fillTaNullsFromLocal(baseSymbol, tf, lastResult)
            }
        }
        // scanner ล้มทุก exchange → ใช้ค่าที่คำนวณเองจากแท่งเทียนแทน (key แบบ scanner)
        val localOnly = fillTaNullsFromLocal(baseSymbol, tf, emptyMap())
        return if (localOnly.isNotEmpty()) {
            logDebug("AutomationService", "TA fallback to local indicators: $baseSymbol@$tf")
            localOnly
        } else lastResult
    }


    suspend fun fillTaNullsFromLocal(symbol: String, tf: String, data: Map<String, String>): Map<String, String> {
        fun bad(v: String?) = v == null || v == "N/A" || v == "null"
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


    suspend fun generateAiText(prompt: String): String? {
        val apiKey = setting("api_key")
        if (apiKey.isBlank()) {
            logDebug("AutomationService", "🧠 AI summary skipped — no api_key in settings")
            return null
        }
        val primary = setting("model_name").ifBlank { com.skyliner2008.jarvis.data.ModelConfig.getBestActiveModel() }
        // Alert summaries are time-critical. Keep a bounded fallback chain so a provider outage
        // cannot turn one notification into a minute-long cascade of sequential timeouts.
        val models = com.skyliner2008.jarvis.data.ModelConfig.getFallbackChain(primary).take(4)
        logDebug("AutomationService", "🧠 AI summary start — chain=${models.joinToString(" → ")}")
        for (m in models) {
            val t0 = System.currentTimeMillis()
            val text = try {
                // Alert AI is latency-sensitive: use one model per attempt, short timeout,
                // and disable GeminiService's 45s long-retry. The outer chain owns fallback.
                val service = GeminiService(geminiClient, apiKey, m).apply {
                    fallbackModelsOverride = emptyList()
                }
                service.generateResponse(
                    prompt = prompt,
                    intentAddon = "คุณคือ JARVIS ผู้ช่วยส่วนตัว พูดสั้น กระชับ สุภาพ เป็นมิตร ใช้ภาษาไทยเป็นหลัก " +
                            "ตอบเป็นข้อความธรรมดาเท่านั้น ห้ามใช้ markdown ห้ามใส่หัวข้อหรือตาราง ห้ามใส่ code block หรือ chart",
                    timeoutMs = 8_000,
                    retryLongerOnTimeout = false
                ).trim().takeIf { it.isNotBlank() && !it.startsWith("⚠️") }
                    ?.let { AlertPresentationFormatter.stripCodeFences(it) }
                    ?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                logError("AutomationService", "🧠 AI summary FAILED model=$m (${System.currentTimeMillis() - t0}ms): ${e.message}", e)
                null
            }
            if (text != null) {
                if (m != primary) {
                    // persist โมเดลที่ใช้ได้จริงกลับลง settings — รอบถัดไปจะไม่ชน 404/429 ซ้ำตัวเดิม
                    logDebug("AutomationService", "🧠 AI summary fallback model: $primary → $m (persist)")
                    runCatching { database.jarvisDatabaseQueries.insertSetting("model_name", m) }
                }
                logDebug("AutomationService", "🧠 AI summary OK model=$m (${System.currentTimeMillis() - t0}ms, ${text.length} chars)")
                return text
            }
            logDebug("AutomationService", "🧠 AI summary model $m failed → try next in fallback chain")
        }
        logError("AutomationService", "🧠 AI summary FAILED on all models (${models.size}) → ใช้ template body แทน", null)
        return null
    }


    fun symbolTimeframeMin(symbol: String): Int? {
        val tf = symbol.substringAfter('@', "").trim().lowercase()
        if (tf.length < 2) return null
        val n = tf.dropLast(1).toIntOrNull() ?: return null
        return when (tf.last()) {
            'm' -> n
            'h' -> n * 60
            'd' -> n * 1440
            'w' -> n * 10080
            else -> null
        }
    }


    suspend fun runStrategySupervisor(job: AlertJob, data: Map<String, String>): SupervisorResult? {
        val prompt = buildString {
            appendLine("บทบาท: คุณคือ AI Strategy Supervisor — คัดกรองสัญญาณเทรดก่อนส่งถึงผู้ใช้ โดยเทียบสัญญาณกับโครงสร้างตลาด 5 ไทม์เฟรม")
            appendLine()
            appendLine("══ สัญญาณ ══")
            appendLine("${data["signal_side"]} ${job.symbol} | กลยุทธ์: ${data["signal_strategy"]}")
            appendLine("Entry ${data["signal_entry"]} | SL ${data["signal_sl"]} | TP ${data["signal_tp"]} | RR 1:${data["signal_rr"]} | ATR14 ${data["signal_atr"] ?: "-"}")
            appendLine("เงื่อนไขที่เกิดสัญญาณ: ${data["signal_reason"]}")
            appendLine()
            appendLine("══ โครงสร้างตลาด 5 ไทม์เฟรม (คำนวณ deterministic จากแท่งเทียนจริง) ══")
            appendLine(data["signal_mtf_context"]?.takeIf { it.isNotBlank() } ?: (data["signal_context"] ?: "-"))
            appendLine()
            appendLine("แนวทางตัดสิน:")
            appendLine("- VETO เมื่อสัญญาณขัดโครงสร้างหลักชัดเจน เช่น BUY ขณะ H1/H4 เป็น DOWN, ราคาติด Supply/แนวต้านสำคัญพอดี, หรือเข้า BUY โซน PREMIUM สุดขอบ / SELL โซน DISCOUNT สุดขอบ")
            appendLine("- ADJUST เมื่อสัญญาณสอดคล้องโครงสร้าง แต่ SL/TP วางไม่สอดคล้องกับระดับจริง — ย้ายไปอ้างอิงโครงสร้าง (BUY: SL ใต้ Demand/swing low ใกล้สุด, TP ใต้ Supply/swing high ใกล้สุด; SELL กลับด้าน) โดย RR ต้องไม่ต่ำกว่า 1.0")
            appendLine("- APPROVE เมื่อสัญญาณสอดคล้องและ SL/TP สมเหตุสมผลแล้ว")
            appendLine("ใช้เฉพาะข้อมูลด้านบน ห้ามสมมติข่าว/ตัวเลขอื่น และห้ามรับประกันผลกำไร")
            appendLine()
            appendLine("ตอบตามรูปแบบนี้เท่านั้น 5 บรรทัด ห้ามมีข้อความอื่น:")
            appendLine("DECISION: APPROVE หรือ VETO หรือ ADJUST")
            appendLine("ADJUST_SL: ราคาใหม่ หรือ -")
            appendLine("ADJUST_TP: ราคาใหม่ หรือ -")
            appendLine("CONFIDENCE: ตัวเลข 0-100")
            appendLine("REASON_TH: เหตุผลภาษาไทยสั้นๆ 1-2 ประโยค")
        }
        val waitStart = System.currentTimeMillis()
        alertAiMutex.lock()
        val raw = try {
            val waitedMs = System.currentTimeMillis() - waitStart
            if (waitedMs > 0L) logDebug("AutomationService", "🧑‍✈️ Supervisor queued — waited ${waitedMs}ms")
            generateAiText(prompt)
        } finally {
            alertAiMutex.unlock()
        } ?: return null
        // generateAiText บีบ whitespace เป็นบรรทัดเดียว (stripCodeFences) → parse ด้วย regex ไม่ใช่แยกบรรทัด
        val decisionRaw = Regex("DECISION:\\s*(\\w+)", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.uppercase() ?: return null
        val decision = when {
            decisionRaw.contains("VETO") -> "VETO"
            decisionRaw.contains("ADJUST") -> "ADJUST"
            else -> "APPROVE"
        }
        fun priceField(name: String): Double? =
            Regex("$name:\\s*([0-9][0-9,]*\\.?[0-9]*)", RegexOption.IGNORE_CASE).find(raw)
                ?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val conf = Regex("CONFIDENCE:\\s*(\\d{1,3})", RegexOption.IGNORE_CASE).find(raw)
            ?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100) ?: 50
        val reason = Regex("REASON_TH:\\s*(.+)$", RegexOption.IGNORE_CASE).find(raw)
            ?.groupValues?.get(1)?.trim()?.take(300)?.takeIf { it.isNotBlank() } ?: "ผ่านเกณฑ์ Supervisor"
        return SupervisorResult(decision, priceField("ADJUST_SL"), priceField("ADJUST_TP"), conf, reason)
    }

    suspend fun runAnticipationSupervisor(job: AlertJob, data: Map<String, String>): SupervisorResult? {
        val side = data["signal_anticipation_side"] ?: data["signal_side"] ?: "BUY"
        val factor = data["signal_anticipation_factor"] ?: "ANTICIPATION"
        val stage = data["signal_anticipation_stage"] ?: "PRE_SETUP"
        val entry = data["signal_anticipation_entry"] ?: data["close"] ?: "-"
        val sl = data["signal_anticipation_sl"] ?: "-"
        val tp1 = data["signal_anticipation_tp1"] ?: "-"
        val tp2 = data["signal_anticipation_tp2"] ?: "-"
        val desc = data["signal_anticipation_desc"] ?: "-"
        val zone = data["signal_anticipation_zone"] ?: "-"
        val mtf = data["signal_mtf_context"]?.takeIf { it.isNotBlank() } ?: (data["signal_context"] ?: "-")

        val prompt = buildString {
            appendLine("บทบาท: คุณคือ AI Strategy Supervisor — วิเคราะห์และคัดกรองการคาดการณ์สัญญาณล่วงหน้า (Signal Anticipation) ก่อนแจ้งเตือนผู้ใช้")
            appendLine()
            appendLine("══ สัญญาณคาดการณ์ล่วงหน้า ══")
            appendLine("สินทรัพย์: ${job.symbol} | ทิศทางคาดการณ์: $side | ขั้นตอน: $stage")
            appendLine("ปัจจัยกระตุ้น: $factor | โซน/ระดับ: $zone")
            appendLine("Execution Rails คาดหมาย: Entry $entry | SL $sl | TP1 $tp1 | TP2 $tp2")
            appendLine("รายละเอียดเหตุผล: $desc")
            appendLine()
            appendLine("══ โครงสร้างตลาด MTF Context (คำนวณจากแท่งเทียนจริง) ══")
            appendLine(mtf)
            appendLine()
            appendLine("แนวทางตัดสิน:")
            appendLine("- VETO เมื่อการคาดการณ์นี้เสี่ยงสูงเกินไป เช่น สัญญาณขัดแย้งเทรนด์ใหญ่ H1/H4 ชัดเจน, ตลาดผันผวนผิดปกติ, หรือแนวต้าน/รับขวางทาง")
            appendLine("- ADJUST เมื่อการคาดการณ์ถูกต้อง แต่วางระดับ SL หรือ TP กว้าง/แคบเกินไปเทียบกับโซนโครงสร้าง")
            appendLine("- APPROVE เมื่อการคาดการณ์สอดคล้องกับพฤติกรรมราคาและ MTF Context สมควรแจ้งเตือนล่วงหน้า")
            appendLine("ใช้เฉพาะข้อมูลด้านบน ห้ามสมมติข่าว/ตัวเลขอื่น")
            appendLine()
            appendLine("ตอบตามรูปแบบนี้เท่านั้น 5 บรรทัด ห้ามมีข้อความอื่น:")
            appendLine("DECISION: APPROVE หรือ VETO หรือ ADJUST")
            appendLine("ADJUST_SL: ราคาใหม่ หรือ -")
            appendLine("ADJUST_TP: ราคาใหม่ หรือ -")
            appendLine("CONFIDENCE: ตัวเลข 0-100")
            appendLine("REASON_TH: เหตุผลภาษาไทยสั้นๆ 1-2 ประโยค")
        }
        val waitStart = System.currentTimeMillis()
        alertAiMutex.lock()
        val raw = try {
            val waitedMs = System.currentTimeMillis() - waitStart
            if (waitedMs > 0L) logDebug("AutomationService", "🧑‍✈️ Anticipation Supervisor queued — waited ${waitedMs}ms")
            generateAiText(prompt)
        } finally {
            alertAiMutex.unlock()
        } ?: return null

        val decisionRaw = Regex("DECISION:\\s*(\\w+)", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.uppercase() ?: return null
        val decision = when {
            decisionRaw.contains("VETO") -> "VETO"
            decisionRaw.contains("ADJUST") -> "ADJUST"
            else -> "APPROVE"
        }
        fun priceField(name: String): Double? =
            Regex("$name:\\s*([0-9][0-9,]*\\.?[0-9]*)", RegexOption.IGNORE_CASE).find(raw)
                ?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val conf = Regex("CONFIDENCE:\\s*(\\d{1,3})", RegexOption.IGNORE_CASE).find(raw)
            ?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100) ?: 50
        val reason = Regex("REASON_TH:\\s*(.+)$", RegexOption.IGNORE_CASE).find(raw)
            ?.groupValues?.get(1)?.trim()?.take(300)?.takeIf { it.isNotBlank() } ?: "ผ่านเกณฑ์ Anticipation Supervisor"
        return SupervisorResult(decision, priceField("ADJUST_SL"), priceField("ADJUST_TP"), conf, reason)
    }


    fun validateSupervisorAdjustment(data: Map<String, String>, sup: SupervisorResult): Map<String, String>? {
        val entry = data["signal_entry"]?.replace(",", "")?.toDoubleOrNull() ?: return null
        val sl0 = data["signal_sl"]?.replace(",", "")?.toDoubleOrNull() ?: return null
        val tp0 = data["signal_tp"]?.replace(",", "")?.toDoubleOrNull() ?: return null
        val side = data["signal_side"] ?: return null
        val atr = data["signal_atr"]?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val sl = sup.adjSl ?: sl0
        val tp = sup.adjTp ?: tp0
        if (sl == sl0 && tp == tp0) return null                      // ไม่ได้เปลี่ยนอะไรจริง
        val sideOk = if (side == "BUY") sl < entry && tp > entry else sl > entry && tp < entry
        if (!sideOk) return null
        val risk = kotlin.math.abs(entry - sl)
        if (risk <= 0) return null
        val rr = kotlin.math.abs(tp - entry) / risk
        if (rr < 1.0) return null                                   // RR ต่ำกว่า 1 ไม่รับ
        if (atr > 0) {
            if (risk < 0.2 * atr || risk > 6.0 * atr) return null   // SL แคบ/กว้างผิดปกติ
            if (kotlin.math.abs(tp - entry) > 10.0 * atr) return null
        }
        fun fmt(v: Double) = if (kotlin.math.abs(v) >= 100) "%.2f".format(v) else "%.4f".format(v)
        return mapOf(
            "signal_sl" to fmt(sl),
            "signal_tp" to fmt(tp),
            "signal_rr" to "%.2f".format(rr)
        )
    }


    suspend fun checkJob(job: AlertJob, fireAlert: (suspend (AlertJob, String, Map<String, String>, String) -> Unit)? = null) {
        // Alert ทั่วไปที่ยิงไปแล้ว (is_triggered=1) → พักการเฝ้าดู รอผู้ใช้ตัดสินใจจากปุ่มบน notification
        // (🛑 หยุดแจ้งเตือน = ปิด job / 🔁 แจ้งเตือนซ้ำ = รีเซ็ตให้เฝ้าดูใหม่ ผ่าน AlertActionReceiver)
        // trading_signal_alert ใช้ signal_id เป็น dedup key: signal ใหม่ยิงได้ 1 ครั้ง,
        // signal_id เดิมจะถูก suppress แม้ is_triggered ของ job จะยังเป็น 1
        if (job.is_triggered == 1L && job.tool_name != "trading_signal_alert") {
            logDebug("AutomationService", "⏸ พัก '${job.name}' — TRIGGERED แล้ว รอผู้ใช้เลือก หยุด/ซ้ำ จาก notification")
            return
        }

        // Decode condition first (need field name for logging regardless of fetch result)
        val condition = try {
            automationJson.decodeFromString(AutomationCondition.serializer(), job.condition_json)
        } catch (e: Exception) {
            logError("AutomationService", "Job ${job.name}: invalid condition_json: ${job.condition_json}")
            return
        }

        // 1. Fetch data based on tool_name
        val data = when (job.tool_name) {
            "trading_price" -> tradingApi.getBestEffortPrice(job.symbol)
            "trading_indicators" -> indicatorProvider.fetch(job.symbol)
            "trading_smc" -> smcAlertProvider.fetch(job.symbol)
            "trading_smc_flow" -> smcFlowProvider.fetch(job.symbol)
            "trading_strategy_signal" -> strategySignalProvider.fetch(job.symbol)
            "trading_signal_alert" -> {
                signalCycleCache[job.symbol]?.also {
                    signalCycleHits.incrementAndGet()
                } ?: signalAlertProvider.fetch(job.symbol).also {
                    signalCycleCache[job.symbol] = it
                    signalCycleFetched.add(job.symbol)
                }
            }
            "trading_technical_analysis" -> fetchTechnicalAnalysisWithFallback(job)
            "trading_sentiment" -> tradingApi.getRedditSentiment(job.symbol).mapValues { it.value.toString() }
            "trading_fear_greed" -> tradingApi.getFearGreedIndex(1)
            "trading_crypto_overview" -> tradingApi.getCryptoGlobal()
            "trading_deep_analysis_suite" -> {
                // รองรับเลือก TF ด้วย suffix เช่น XAUUSD@15m (default 1h) เหมือน indicators/smc
                val (sym, tf) = com.skyliner2008.jarvis.automation.IndicatorAlertProvider.splitSymbolAndTf(job.symbol)
                val result = advancedEngine.analyze(sym, tf)
                if (result == null) emptyMap<String, String>()
                else mapOf(
                    "summaryScore" to result.summaryScore.toString(),
                    "lsdState" to result.lsdTrend.state,
                    "lsdConfluenceTF" to result.lsdTrend.confluenceTF.toString(),
                    "deltaLabel" to result.orderflow.deltaLabel,
                    "deltaValue" to result.orderflow.lastDelta.toString(),
                    "fiboScore" to (result.fiboStrength.maxOfOrNull { it.score }?.toString() ?: "0"),
                    "momentum" to result.momentum.signal,
                    "isSqueeze" to (if (result.momentum.isSqueeze) "1" else "0"),
                    "close" to result.currentPrice.toString()
                )
            }
            else -> {
                logDebug("AutomationService", "Job ${job.name}: tool '${job.tool_name}' ไม่รองรับใน background — ข้าม")
                emptyMap()
            }
        }

        val lastSample = data[condition.field]

        if (data.isEmpty() || data.containsKey("error")) {
            val errMsg = data["error"] ?: "empty"
            logDebug("AutomationService", "Job ${job.name} → ${condition.field}=ERR ($errMsg) | fetch failed")
            if (job.tool_name != "trading_signal_alert") {
                automationManager.recordJobCheckResult(job.id, "ERR($errMsg)")
            }
            return
        }

        // Compact event diagnostics: emit only state transitions for signal jobs;
        // routine polling remains silent so logcat stays useful for AI diagnostics.
        val sampleStr = lastSample ?: "N/A"
        val isMet = evaluator.evaluate(data, condition)

        // Trading signal alerts are edge-triggered by signal_bar_id, not by the
        // job's boolean is_triggered alone. This prevents a new candle/signal from
        // being suppressed by the previous candle while still deduplicating the
        // exact same signal_id.
        val signalId = if (job.tool_name == "trading_signal_alert") {
            when (condition.field) {
                "signal_buy", "signal_buy_id" -> data["signal_buy_id"]?.toLongOrNull() ?: 0L
                "signal_sell", "signal_sell_id" -> data["signal_sell_id"]?.toLongOrNull() ?: 0L
                "signal_anticipation", "signal_anticipation_id" -> data["signal_anticipation_id"]?.toLongOrNull() ?: 0L
                else -> 0L
            }
        } else 0L
        val triggered = if (job.tool_name == "trading_signal_alert" && signalId > 0L) {
            job.is_triggered == 1L && job.last_value?.toLongOrNull() == signalId
        } else {
            job.is_triggered == 1L
        }

        // Job-level dedup only knows about the exact last signal_id. A new candle/strategy
        // can therefore bypass it even when the previous alert was only seconds ago.
        // Apply a short market-event throttle at symbol+TF+side level before FIRE.
        val signalSide = data["signal_side"] ?: data["signal_anticipation_side"] ?: "UNKNOWN"
        val signalThrottleKey = if (job.tool_name == "trading_signal_alert" && signalId > 0L) {
            job.symbol + "|" + condition.field + "|" + signalSide
        } else null
        var throttledSignal = false
        if (isMet && signalThrottleKey != null && !triggered) {
            val now = System.currentTimeMillis()
            val lastAt = signalAlertLastHandledAt[signalThrottleKey] ?: 0L
            val lastId = signalAlertLastHandledId[signalThrottleKey] ?: 0L
            throttledSignal = signalId == lastId || (lastAt > 0L && now - lastAt < signalAlertCooldownMs)
            if (throttledSignal) {
                logDebug(
                    "AutomationService",
                    "SIGNAL_EVENT job=${job.name} symbol=${job.symbol} side=${data["signal_side"] ?: "UNKNOWN"} signal_id=$signalId strategy=${data["signal_strategy"] ?: "UNKNOWN"} action=SUPPRESS_COOLDOWN remaining=${if (lastAt > 0L) (signalAlertCooldownMs - (now - lastAt)).coerceAtLeast(0L) else 0L}ms"
                )
            } else {
                signalAlertLastHandledAt[signalThrottleKey] = now
                signalAlertLastHandledId[signalThrottleKey] = signalId
            }
        }
        val action = when {
            isMet && !triggered && !throttledSignal -> "FIRE"
            isMet && (triggered || throttledSignal) -> if (throttledSignal) "SUPPRESS_COOLDOWN" else "SUPPRESS_ALREADY_TRIGGERED"
            !isMet && job.is_triggered == 1L -> "RESET"
            else -> "WAIT"
        }
        // WAIT is routine polling noise; signal actions are deduplicated below.
        val signalLogKey = if (job.tool_name == "trading_signal_alert") "${job.symbol}|${condition.field}|$signalId" else null
        val previousSignalAction = if (signalLogKey != null && action != "WAIT") signalLastLoggedAction.put(signalLogKey, action) else null
        if (action != "WAIT" && (signalLogKey == null || previousSignalAction != action)) {
            val signalPart = if (job.tool_name == "trading_signal_alert") {
                " | signal_id=$signalId | job_triggered=${job.is_triggered}"
            } else ""
            logDebug(
                "AutomationService",
                "SIGNAL_EVENT job=${job.name} field=${condition.field} value=$sampleStr condition=${condition.operator}:${condition.value} met=${if (isMet) 1 else 0} triggered=${if (triggered) 1 else 0}$signalPart action=$action"
            )
        }
        // For trading_signal_alert, last_value is reserved for the last triggered signal_id.
        // Do not overwrite it with the boolean/sample value, otherwise a later cycle could
        // lose the signal-id dedup key due to the asynchronous recordJobCheckResult().
        if (job.tool_name != "trading_signal_alert") {
            automationManager.recordJobCheckResult(job.id, sampleStr)
        }

        // --- Auto High Confluence Alert ---
        if (job.tool_name == "trading_deep_analysis_suite") {
             val score = data["summaryScore"]?.toDoubleOrNull() ?: 0.0
             if (score >= 85.0 && job.is_triggered == 0L) {
                 // mark ก่อนยิง (กันยิงซ้ำทุก cycle ขณะ score ค้าง ≥85) แล้วพักรอผู้ใช้เลือกเหมือน alert ทั่วไป
                 automationManager.markTriggered(job.id, "🌟 $score")
                 // แยกไปทำขนาน — ไม่บล็อก loop (AI call ใช้เวลา ~10 วิ)
                 scope.launch { fireAlert?.invoke(job, "🌟 High Confluence ($score)", data, "ai") }
                 return // ยิง confluence แล้ว ไม่ประเมินเงื่อนไขหลักซ้ำในรอบนี้ (กัน notification เบิ้ล)
             }
        }

        // 2. Apply the already-evaluated condition result.
        if (isMet) {
            if (!triggered && !throttledSignal) {
                // Signal alerts are keyed by signal_id; ordinary alerts keep the
                // existing boolean trigger semantics.
                val triggerValue = if (job.tool_name == "trading_signal_alert" && signalId > 0L) {
                    signalId.toString()
                } else {
                    sampleStr
                }
                // mark ก่อนเสมอ (synchronous) กัน tick ถัดไปยิงซ้ำ แล้วค่อยปลุก AI แบบขนาน
                // — เดิมเรียก Gemini แบบ serial ใน loop ทำให้ job ถัดไปช้าไป ~10 วินาที
                automationManager.markTriggered(job.id, triggerValue)
                scope.launch {
                    try {
                        fireAlert?.invoke(job, sampleStr, data, condition.delivery)
                    } catch (e: Exception) {
                        logError("AutomationService", "fireJobAlert ${job.name} failed: ${e.message}", e)
                    }
                }
            }
        } else {
            if (job.is_triggered == 1L) {
                // Flipped to FALSE -> Reset so we can notify again later
                automationManager.resetTrigger(job.id)
            }
        }
    }


    fun destroy() {
        try { geminiClient.close() } catch (_: Throwable) {}
    }
}

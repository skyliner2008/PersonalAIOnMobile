package com.example.personalaibot.automation

import com.example.personalaibot.logDebug
import com.example.personalaibot.automation.backtest.EntryParams
import com.example.personalaibot.tools.trading.Candle
import com.example.personalaibot.tools.trading.SmcApiService
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * StrategySignalProvider — แปลงกลยุทธ์จาก Strategy Library (Quantpedia/QuantConnect reference)
 * มาคำนวณในเครื่องจากแท่งเทียน (TV) โดยตรง ไม่ต้องพึ่ง QuantConnect
 *
 * เฉพาะกลยุทธ์ที่ลดรูปเป็น "สัญญาณต่อแท่งเทียน" จาก OHLCV ล้วนได้จริง
 * (กลุ่ม cross-sectional/portfolio/fundamental ทำบนแท่งเทียนตัวเดียวไม่ได้ — ไม่รวม)
 *
 * กลยุทธ์ใน v1 (map จากคลัง):
 *  1) tsmom     — Time-Series Momentum (time-series-momentum-effect):
 *                 ROC 20 แท่ง > 0 → BUY, < 0 → SELL (ตามทิศโมเมนตัมของตัวเอง)
 *  2) trend     — Trend Following (asset-class-trend-following):
 *                 ราคา > EMA200 และ EMA50 > EMA200 → BUY (กลับกัน → SELL)
 *  3) reversal  — Short-Term Reversal (short-term-reversal-in-stocks):
 *                 RSI(14) < 30 และแตะ BB Lower → BUY (ดีดกลับ), RSI > 70 + BB Upper → SELL
 *  4) donchian  — Donchian Channel Breakout (trend-following classics):
 *                 ทะลุ high 20 แท่ง → BUY, หลุด low 20 แท่ง → SELL
 *  5) w52high   — 52-Weeks High proximity (52-weeks-high-effect):
 *                 ใกล้จุดสูงสุดของข้อมูลที่มี (>= 98% ของ high) → BUY bias (โมเมนตัมต่อเนื่อง)
 *                 หลุดต่ำกว่า 90% → อ่อนแอ (SELL bias)
 *
 * ใช้ผ่าน tool_name = "trading_strategy_signal" ใน AlertJob — เลือก TF ด้วย suffix @TF (default 1h)
 */
class StrategySignalProvider(private val smcApi: SmcApiService) {

    companion object {
        const val TSMOM_LOOKBACK = 20
        const val DONCHIAN_PERIOD = 20
        const val RSI_PERIOD = 14
        const val BB_PERIOD = 20
        const val BB_MULT = 2.0
        const val W52_PROX_BUY = 0.98
        const val W52_PROX_SELL = 0.90

        val STRATEGY_IDS = listOf("tsmom", "trend", "reversal", "donchian", "w52high")
    }

    suspend fun fetch(rawSymbol: String): Map<String, String> {
        val (symbol, tf) = IndicatorAlertProvider.splitSymbolAndTf(rawSymbol)
        val result = smcApi.fetchCandlesWithSource(symbol, tf, 300)
        val candles = result.candles
        if (candles.size < 60) {
            return mapOf("error" to "candles ${candles.size} < 60 (${result.source})")
        }
        val closes = candles.map { it.close }
        val n = candles.size
        val last = candles.last()

        // entry params ที่จูนแล้ว (EntryTuning) — ใช้แทนค่า default ของกลยุทธ์ที่มี tuning
        val tuned = runCatching {
            com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager()
                .getTunedEntryParams(symbol, tf)
        }.getOrElse { emptyMap() }
        val epMOM = tuned["MOM"] ?: EntryParams()
        val epTR = tuned["TR"] ?: EntryParams()
        val epREV = tuned["REV"] ?: EntryParams()
        val epDC = tuned["DC"] ?: EntryParams()
        val ep52H = tuned["52H"] ?: EntryParams()

        // ── 1) Time-Series Momentum ──
        val roc = (last.close - closes[n - 1 - epMOM.momLookback]) / closes[n - 1 - epMOM.momLookback] * 100.0
        val tsmomSignal = when {
            roc > 0 -> "BUY"
            roc < 0 -> "SELL"
            else -> "NONE"
        }

        // ── 2) Trend Following (EMA fast/slow + price vs EMA slow) ──
        val emaFast = ema(closes, epTR.trFast).last()
        val emaSlow = ema(closes, epTR.trSlow).last()
        val trendState = when {
            last.close > emaSlow && emaFast > emaSlow -> "UPTREND"
            last.close < emaSlow && emaFast < emaSlow -> "DOWNTREND"
            else -> "RANGE"
        }
        val trendSignal = when (trendState) {
            "UPTREND" -> "BUY"
            "DOWNTREND" -> "SELL"
            else -> "NONE"
        }

        // ── 3) Short-Term Reversal (RSI + Bollinger) ──
        val rsiNow = rsi(closes, epREV.revRsiPeriod)
        val (bbUpper, bbBasis, bbLower) = bollinger(closes, epREV.revBbPeriod, epREV.revBbMult)
        val reversalSignal = when {
            rsiNow != null && rsiNow < epREV.revRsiLow && last.close <= bbLower -> "BUY"
            rsiNow != null && rsiNow > epREV.revRsiHigh && last.close >= bbUpper -> "SELL"
            else -> "NONE"
        }

        // ── 4) Donchian Breakout (ไม่รวมแท่งปัจจุบัน) ──
        val look = candles.subList(n - 1 - epDC.dcPeriod, n - 1)
        val dUpper = look.maxOf { it.high }
        val dLower = look.minOf { it.low }
        val dMid = (dUpper + dLower) / 2.0
        val donchianSignal = when {
            last.close > dUpper -> "BUY"
            last.close < dLower -> "SELL"
            else -> "NONE"
        }

        // ── 5) 52-Weeks High proximity — ROLLING window (w52Lookback แท่ง ไม่รวมแท่งปัจจุบัน) ──
        //    เดิม maxOf ทั้งชุดที่ดึงมา (live ~300 แท่ง = ไม่กี่วัน ไม่ใช่ 52 สัปดาห์) — ให้ตรงกับ
        //    SignalMarkerProvider (backtest) เพื่อ live == backtest parity
        val w52From = maxOf(0, n - 1 - ep52H.w52Lookback)
        val periodHigh = candles.subList(w52From, n - 1).maxOf { it.high }
        val proximity = last.close / periodHigh
        val w52Signal = when {
            proximity >= ep52H.w52ProxBuy -> "BUY"
            proximity <= ep52H.w52ProxSell -> "SELL"
            else -> "NONE"
        }

        // ── Consensus (BUY=+1, SELL=-1) ──
        val signals = listOf(tsmomSignal, trendSignal, reversalSignal, donchianSignal, w52Signal)
        var score = 0
        signals.forEach { s -> if (s == "BUY") score++ else if (s == "SELL") score-- }
        val consensusSignal = when {
            score >= 3 -> "STRONG_BUY"
            score >= 1 -> "BUY"
            score <= -3 -> "STRONG_SELL"
            score <= -1 -> "SELL"
            else -> "NEUTRAL"
        }

        logDebug("StrategySig", "$symbol/$tf tsmom=$tsmomSignal(${fmt(roc)}%) trend=$trendSignal($trendState) rev=$reversalSignal don=$donchianSignal w52=$w52Signal(${fmt(proximity * 100)}%) → consensus=$consensusSignal($score)")

        return buildMap {
            put("symbol", symbol)
            put("timeframe", tf)
            put("source", result.source)
            put("close", fmt(last.close))
            // tsmom
            put("tsmom_signal", tsmomSignal)
            put("tsmom_roc_pct", fmt(roc))
            put("tsmom_lookback", epMOM.momLookback.toString())
            // trend
            put("trend_signal", trendSignal)
            put("trend_state", trendState)
            put("trend_ema50", fmt(emaFast))
            put("trend_ema200", fmt(emaSlow))
            // reversal
            put("reversal_signal", reversalSignal)
            put("reversal_rsi", rsiNow?.let { fmt(it) } ?: "N/A")
            put("reversal_bb_upper", fmt(bbUpper))
            put("reversal_bb_lower", fmt(bbLower))
            // donchian
            put("donchian_signal", donchianSignal)
            put("donchian_upper", fmt(dUpper))
            put("donchian_lower", fmt(dLower))
            put("donchian_mid", fmt(dMid))
            // w52 high
            put("w52_signal", w52Signal)
            put("w52_high", fmt(periodHigh))
            put("w52_proximity_pct", fmt(proximity * 100))
            // consensus
            put("consensus_signal", consensusSignal)
            put("consensus_score", score.toString())
            put("consensus_buy_count", signals.count { it == "BUY" }.toString())
            put("consensus_sell_count", signals.count { it == "SELL" }.toString())
        }
    }

    /** format ผลเป็นข้อความให้ AI อ่านอธิบายผู้ใช้ (ใช้ใน chat tool) */
    suspend fun fetchFormatted(rawSymbol: String, strategy: String = "all"): String {
        val d = fetch(rawSymbol)
        d["error"]?.let { return "❌ Strategy Signal: $it" }
        val want = strategy.trim().lowercase()
        return buildString {
            appendLine("## 📐 Strategy Signals — ${d["symbol"]} (${d["timeframe"]}) [${d["source"]}]")
            appendLine("close=${d["close"]} | consensus=${d["consensus_signal"]} (score=${d["consensus_score"]}, buy=${d["consensus_buy_count"]}, sell=${d["consensus_sell_count"]})")
            appendLine()
            if (want == "all" || want == "tsmom") {
                appendLine("### 1) Time-Series Momentum (ROC ${d["tsmom_lookback"]} แท่ง)")
                appendLine("tsmom_signal=${d["tsmom_signal"]} | roc=${d["tsmom_roc_pct"]}%")
            }
            if (want == "all" || want == "trend") {
                appendLine("### 2) Trend Following (EMA50/200)")
                appendLine("trend_signal=${d["trend_signal"]} | state=${d["trend_state"]} | ema50=${d["trend_ema50"]} | ema200=${d["trend_ema200"]}")
            }
            if (want == "all" || want == "reversal") {
                appendLine("### 3) Short-Term Reversal (RSI + BB)")
                appendLine("reversal_signal=${d["reversal_signal"]} | rsi=${d["reversal_rsi"]} | bb_upper=${d["reversal_bb_upper"]} | bb_lower=${d["reversal_bb_lower"]}")
            }
            if (want == "all" || want == "donchian") {
                appendLine("### 4) Donchian Breakout (20 แท่ง)")
                appendLine("donchian_signal=${d["donchian_signal"]} | upper=${d["donchian_upper"]} | mid=${d["donchian_mid"]} | lower=${d["donchian_lower"]}")
            }
            if (want == "all" || want == "w52high") {
                appendLine("### 5) 52-Weeks High Proximity")
                appendLine("w52_signal=${d["w52_signal"]} | period_high=${d["w52_high"]} | proximity=${d["w52_proximity_pct"]}%")
            }
            appendLine()
            appendLine("_Consensus: STRONG_BUY/BUY เมื่อ score ≥ +1 (STRONG ≥ +3), SELL/STRONG_SELL เมื่อ ≤ -1 — คำนวณในเครื่องจากแท่งเทียน TV ไม่พึ่ง QuantConnect_")
        }
    }

    // ─── Math helpers ──────────────────────────────────────────────────────

    private fun fmt(v: Double): String = "%.4f".format(v).trimEnd('0').trimEnd('.')

    private fun ema(values: List<Double>, period: Int): List<Double> {
        if (values.size < period) return List(values.size) { Double.NaN }
        val k = 2.0 / (period + 1)
        val out = ArrayList<Double>(values.size)
        var prev = values.take(period).average()
        repeat(period - 1) { out.add(Double.NaN) }
        out.add(prev)
        for (i in period until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out.add(prev)
        }
        return out
    }

    private fun rsi(closes: List<Double>, period: Int): Double? {
        if (closes.size <= period) return null
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff > 0) gain += diff else loss -= diff
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        for (i in period + 1 until closes.size) {
            val diff = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + max(diff, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-diff, 0.0)) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun bollinger(closes: List<Double>, period: Int, mult: Double): Triple<Double, Double, Double> {
        val window = closes.takeLast(period)
        val mean = window.average()
        val sd = sqrt(window.sumOf { (it - mean).pow(2) } / period)
        return Triple(mean + mult * sd, mean, mean - mult * sd)
    }

    @Suppress("unused")
    private fun atr(candles: List<Candle>, period: Int): Double? {
        if (candles.size <= period) return null
        var sum = 0.0
        for (i in candles.size - period until candles.size) {
            val h = candles[i].high; val l = candles[i].low; val pc = candles[i - 1].close
            sum += maxOf(h - l, abs(h - pc), abs(l - pc))
        }
        return sum / period
    }

    @Suppress("unused")
    private fun clamp(v: Double, lo: Double, hi: Double) = min(max(v, lo), hi)
}

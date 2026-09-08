package com.example.personalaibot.automation

import com.example.personalaibot.logDebug
import com.example.personalaibot.tools.trading.Candle
import com.example.personalaibot.tools.trading.SmcApiService
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * SmcFlowAlertProvider — port ของ TradingView "SMC Flow System v2" (Pine Script)
 * ตัดเฉพาะส่วนสัญญาณ ไม่วาดอะไรทั้งนั้น:
 *
 *  1) EMA 14/60 cross signal — crossover/crossunder + แท่งยืนยัน (close vs open)
 *  2) UT Bot trigger (key=2.0, ATR period=6, src=close) — ATR trailing stop
 *  3) 3-Bar Reversal pattern (raw, ไม่ใส่ trend filter)
 *  4) SMC Confluence signal = trigger (UT Bot หรือ 3BR, ภายใน 3 แท่ง)
 *     + structure bias (จาก SmcApiService) + recipe A/B/C + แท่งยืนยัน
 *     - Recipe A: ราคาอยู่ใน OB (ใช้ OB เป็น demand/supply proxy — Pine ต้นฉบับมี D/S แยก)
 *     - Recipe B: ราคาอยู่ใน FVG และ OB พร้อมกัน
 *     - Recipe C: ราคาอยู่ใน Fib Golden Zone (0.618–0.786 ของ swing structureHigh/Low)
 *  5) Auto Fib levels จาก swing ปัจจุบัน (คำนวณให้ใช้ต่อ ไม่วาด)
 *
 * ใช้ผ่าน tool_name = "trading_smc_flow" ใน AlertJob — เลือก TF ด้วย suffix @TF (default 1h)
 */
class SmcFlowAlertProvider(private val smcApi: SmcApiService) {

    companion object {
        private const val UT_KEY = 2.0
        private const val UT_ATR_PERIOD = 6
        private const val EMA_FAST = 14
        private const val EMA_SLOW = 60
        private const val TRIGGER_LOOKBACK = 3
        private val FIB_RATIOS = listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 0.886, 1.0, 1.272, 1.618)
    }

    suspend fun fetch(rawSymbol: String): Map<String, String> {
        val (symbol, tf) = IndicatorAlertProvider.splitSymbolAndTf(rawSymbol)
        val result = smcApi.fetchCandlesWithSource(symbol, tf, 300)
        val candles = result.candles
        if (candles.size < EMA_SLOW + 10) {
            return mapOf("error" to "candles ${candles.size} < ${EMA_SLOW + 10} (${result.source})")
        }

        val closes = candles.map { it.close }
        val n = candles.size
        val last = candles.last()

        // ── EMA 14/60 ──
        val emaFast = ema(closes, EMA_FAST)
        val emaSlow = ema(closes, EMA_SLOW)
        val ef = emaFast.last()
        val es = emaSlow.last()
        val efPrev = emaFast[n - 2]
        val esPrev = emaSlow[n - 2]
        val emaCross = when {
            efPrev <= esPrev && ef > es -> "GOLDEN_CROSS"
            efPrev >= esPrev && ef < es -> "DEATH_CROSS"
            else -> "NONE"
        }
        val spread = abs(ef - es)
        val spreadPrev = abs(efPrev - esPrev)
        val isConverging = spread < spreadPrev
        // LTF confirm ใน Pine ใช้ TF 5m — ที่นี่ใช้แท่งปัจจุบันของ TF ตัวเองแทน (close vs open)
        val bullConfirm = last.close > last.open
        val bearConfirm = last.close < last.open
        val emaSignal = when {
            emaCross == "GOLDEN_CROSS" && bullConfirm -> "BUY"
            emaCross == "DEATH_CROSS" && bearConfirm -> "SELL"
            else -> "NONE"
        }
        val atrSeries = atrSeries(candles, UT_ATR_PERIOD)
        val nearCrossThreshold = max((atrSeries.lastOrNull() ?: 0.0) * 0.35, last.close * 0.0012)
        val isNearCross = spread <= nearCrossThreshold && isConverging && emaCross == "NONE"
        val nearCrossSide = when {
            isNearCross && ef < es && ef >= efPrev -> "BUY"
            isNearCross && ef > es && ef <= efPrev -> "SELL"
            else -> "NONE"
        }

        // ── UT Bot (trailing stop + trigger) ──
        val utStop = DoubleArray(n)
        val utPos = IntArray(n)  // 1 = long trend, -1 = short trend
        var prevStop = 0.0
        var pos = 0
        for (i in 0 until n) {
            val src = closes[i]
            val srcPrev = closes[max(0, i - 1)]
            val nLoss = UT_KEY * (atrSeries.getOrElse(i) { 0.0 })
            prevStop = when {
                src > prevStop && srcPrev > prevStop -> max(prevStop, src - nLoss)
                src < prevStop && srcPrev < prevStop -> min(prevStop, src + nLoss)
                src > prevStop -> src - nLoss
                else -> src + nLoss
            }
            utStop[i] = prevStop
            pos = when {
                srcPrev < prevStop && src > prevStop -> 1
                srcPrev > prevStop && src < prevStop -> -1
                else -> pos
            }
            utPos[i] = pos
        }
        // buy/sell ของ Pine: src > stop && crossover(src, stop) — เทียบแท่งปัจจุบันกับแท่งก่อน
        fun utBuyAt(i: Int) = i >= 1 && closes[i] > utStop[i] && closes[i - 1] <= utStop[i - 1]
        fun utSellAt(i: Int) = i >= 1 && closes[i] < utStop[i] && closes[i - 1] >= utStop[i - 1]

        // ── 3-Bar Reversal (raw pattern จาก Pine ต้นฉบับ) ──
        fun threeBarBullAt(i: Int): Boolean {
            if (i < 2) return false
            val c2 = candles[i - 2]; val c1 = candles[i - 1]; val c0 = candles[i]
            return (c2.close < c2.open) &&
                (c1.low < c2.low) && (c1.high < c2.high) && (c1.close < c1.open) &&
                (c0.close > c0.open) && (c0.high > c2.high)
        }
        fun threeBarBearAt(i: Int): Boolean {
            if (i < 2) return false
            val c2 = candles[i - 2]; val c1 = candles[i - 1]; val c0 = candles[i]
            return (c2.close > c2.open) &&
                (c1.high > c2.high) && (c1.low > c2.low) && (c1.close > c1.open) &&
                (c0.close < c0.open) && (c0.low < c2.low)
        }

        // trigger (UT Bot หรือ 3BR) ภายใน TRIGGER_LOOKBACK แท่งล่าสุด — เหมือน "Any" ของ Pine
        var bullTriggerBar = -1
        var bearTriggerBar = -1
        for (i in max(1, n - 1 - TRIGGER_LOOKBACK) until n) {
            if (utBuyAt(i) || threeBarBullAt(i)) bullTriggerBar = i
            if (utSellAt(i) || threeBarBearAt(i)) bearTriggerBar = i
        }
        val bullTriggerActive = bullTriggerBar >= 0 && (n - 1 - bullTriggerBar) <= TRIGGER_LOOKBACK
        val bearTriggerActive = bearTriggerBar >= 0 && (n - 1 - bearTriggerBar) <= TRIGGER_LOOKBACK

        // ── SMC state (จาก SmcApiService — structure bias, OB, FVG, swing) ──
        val smc = runCatching { smcApi.getSmcAnalysis(symbol, tf, strictTvSource = false) }.getOrNull()
        val bias = when (smc?.structureDirection) {
            "BULLISH" -> 1
            "BEARISH" -> -1
            else -> 0
        }
        val close = last.close
        val inBullOB = smc?.bullishOBs?.any { !it.mitigated && close in it.bottom..it.top } == true
        val inBearOB = smc?.bearishOBs?.any { !it.mitigated && close in it.bottom..it.top } == true
        val inBullFVG = smc?.fvgs?.any { it.isBullish && close in it.bottom..it.top } == true
        val inBearFVG = smc?.fvgs?.any { !it.isBullish && close in it.bottom..it.top } == true

        // ── Auto Fib จาก swing ปัจจุบัน (structureHigh/Low) ──
        val sHigh = smc?.structureHigh ?: 0.0
        val sLow = smc?.structureLow ?: 0.0
        val range = sHigh - sLow
        // Golden zone: retrace 0.618–0.786 ของ swing ตามทิศ bias
        val (gzBottom, gzTop) = when {
            range <= 0 -> 0.0 to 0.0
            bias >= 0 -> (sHigh - range * 0.786) to (sHigh - range * 0.618) // swing ขาขึ้น: รอ retest ลงมา
            else -> (sLow + range * 0.618) to (sLow + range * 0.786)        // swing ขาลง: รอดีดขึ้นมา
        }
        val inFibGolden = range > 0 && close in gzBottom..gzTop

        // ── Recipes (A: OB, B: FVG+OB, C: Fib golden) ──
        val recipeABull = inBullOB
        val recipeABear = inBearOB
        val recipeBBull = inBullFVG && inBullOB
        val recipeBBear = inBearFVG && inBearOB
        val recipeC = inFibGolden
        val anyBull = recipeABull || recipeBBull || recipeC
        val anyBear = recipeABear || recipeBBear || recipeC

        val smcSignal = when {
            bullTriggerActive && bias == 1 && anyBull && bullConfirm -> "BUY"
            bearTriggerActive && bias == -1 && anyBear && bearConfirm -> "SELL"
            else -> "NONE"
        }
        val recipesHit = buildList {
            if (smcSignal == "BUY") {
                if (recipeABull) add("A")
                if (recipeBBull) add("B")
                if (recipeC) add("C")
            } else if (smcSignal == "SELL") {
                if (recipeABear) add("A")
                if (recipeBBear) add("B")
                if (recipeC) add("C")
            }
        }.joinToString("+").ifBlank { "-" }

        logDebug("SmcFlow", "$symbol/$tf ema=$emaSignal($emaCross) ut=${utPos.last()} trigger(b=$bullTriggerBar,s=$bearTriggerBar) bias=$bias smc=$smcSignal[$recipesHit]")

        return buildMap {
            put("symbol", symbol)
            put("timeframe", tf)
            put("source", result.source)
            put("close", fmt(close))
            // EMA 14/60
            put("ema14", fmt(ef))
            put("ema60", fmt(es))
            put("ema14_60_spread", fmt(ef - es))
            put("ema14_60_state", if (ef > es) "BULLISH" else "BEARISH")
            put("ema14_60_cross", emaCross)
            put("ema14_60_signal", emaSignal)
            put("ema14_60_near_cross", if (isNearCross) "1" else "0")
            put("ema14_60_near_cross_side", nearCrossSide)
            // UT Bot
            put("utbot_signal", when {
                utBuyAt(n - 1) -> "BUY"
                utSellAt(n - 1) -> "SELL"
                else -> "NONE"
            })
            put("utbot_trend", if (utPos.last() >= 0) "BULL" else "BEAR")
            put("utbot_stop", fmt(utStop.last()))
            // SMC confluence
            put("smc_signal", smcSignal)
            put("smc_recipes", recipesHit)
            put("structure_bias", when (bias) { 1 -> "BULLISH"; -1 -> "BEARISH"; else -> "NEUTRAL" })
            put("in_bull_ob", if (inBullOB) "1" else "0")
            put("in_bear_ob", if (inBearOB) "1" else "0")
            put("in_bull_fvg", if (inBullFVG) "1" else "0")
            put("in_bear_fvg", if (inBearFVG) "1" else "0")
            put("in_fib_golden", if (inFibGolden) "1" else "0")
            // Auto Fib (คำนวณให้ใช้ต่อ — ไม่วาด)
            if (range > 0) {
                put("fib_swing_high", fmt(sHigh))
                put("fib_swing_low", fmt(sLow))
                put("fib_golden_top", fmt(gzTop))
                put("fib_golden_bottom", fmt(gzBottom))
                FIB_RATIOS.forEach { r ->
                    val level = if (bias >= 0) sHigh - range * r else sLow + range * r
                    put("fib_${(r * 1000).toInt()}", fmt(level))
                }
            }
        }
    }

    /** format ผลเป็นข้อความให้ AI อ่านอธิบายผู้ใช้ (ใช้ใน chat tool) */
    suspend fun fetchFormatted(rawSymbol: String): String {
        val d = fetch(rawSymbol)
        d["error"]?.let { return "❌ SMC Flow: $it" }
        return buildString {
            appendLine("## 🌊 SMC Flow System — ${d["symbol"]} (${d["timeframe"]}) [${d["source"]}]")
            appendLine("close=${d["close"]}")
            appendLine()
            appendLine("### EMA 14/60")
            appendLine("ema14=${d["ema14"]} | ema60=${d["ema60"]} | state=${d["ema14_60_state"]}")
            appendLine("ema14_60_cross=${d["ema14_60_cross"]} | ema14_60_signal=${d["ema14_60_signal"]} | ema14_60_near_cross=${d["ema14_60_near_cross"]}(${d["ema14_60_near_cross_side"]})")
            appendLine()
            appendLine("### UT Bot (key=2.0, atr=6)")
            appendLine("utbot_signal=${d["utbot_signal"]} | utbot_trend=${d["utbot_trend"]} | utbot_stop=${d["utbot_stop"]}")
            appendLine()
            appendLine("### SMC Confluence (trigger + structure + recipe A/B/C + confirm)")
            appendLine("smc_signal=${d["smc_signal"]} | smc_recipes=${d["smc_recipes"]} | structure_bias=${d["structure_bias"]}")
            appendLine("in_bull_ob=${d["in_bull_ob"]} | in_bear_ob=${d["in_bear_ob"]} | in_bull_fvg=${d["in_bull_fvg"]} | in_bear_fvg=${d["in_bear_fvg"]} | in_fib_golden=${d["in_fib_golden"]}")
            if (d.containsKey("fib_swing_high")) {
                appendLine()
                appendLine("### Auto Fib (swing ${d["fib_swing_low"]} → ${d["fib_swing_high"]})")
                appendLine("golden_zone=${d["fib_golden_bottom"]} — ${d["fib_golden_top"]}")
                appendLine("fib_0=${d["fib_0"]} | fib_236=${d["fib_236"]} | fib_382=${d["fib_382"]} | fib_500=${d["fib_500"]} | fib_618=${d["fib_618"]} | fib_786=${d["fib_786"]} | fib_1000=${d["fib_1000"]} | fib_1272=${d["fib_1272"]} | fib_1618=${d["fib_1618"]}")
            }
            appendLine()
            appendLine("_Recipes: A=ราคาใน OB | B=ราคาใน FVG+OB | C=ราคาใน Fib Golden Zone (0.618–0.786) — ต้องมี structure bias ตรง + trigger (UT Bot/3-Bar Reversal) ภายใน 3 แท่ง + แท่งยืนยัน_")
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

    /** ATR series (Wilder/RMA) — index i ใช้ข้อมูลถึงแท่ง i */
    private fun atrSeries(candles: List<Candle>, period: Int): DoubleArray {
        val out = DoubleArray(candles.size)
        if (candles.size <= period) return out
        val trs = DoubleArray(candles.size)
        for (i in 1 until candles.size) {
            val h = candles[i].high; val l = candles[i].low; val pc = candles[i - 1].close
            trs[i] = maxOf(h - l, abs(h - pc), abs(l - pc))
        }
        var atr = 0.0
        for (i in 1..period) atr += trs[i]
        atr /= period
        out[period] = atr
        for (i in period + 1 until candles.size) {
            atr = (atr * (period - 1) + trs[i]) / period
            out[i] = atr
        }
        // backfill ช่วงต้นด้วยค่าแรกที่คำนวณได้ (Pine ta.atr ก็ warmup คล้ายกัน)
        for (i in 0 until period) out[i] = out[period]
        return out
    }
}

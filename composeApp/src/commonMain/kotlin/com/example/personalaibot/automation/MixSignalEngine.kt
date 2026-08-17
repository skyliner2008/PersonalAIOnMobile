package com.example.personalaibot.automation

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * MixSignalEngine — ผสมหลายกลยุทธ์เป็น 1 สัญญาณด้วยการโหวต (state-based voting)
 *
 * ต่างจาก edge markers เดิม: แต่ละกลยุทธ์ให้ "คะแนนโหวต" ณ แท่งนั้น (+1 BUY / -1 SELL / 0 เฉย)
 * จาก state ของตัวเอง — เพราะ edge ของกลยุทธ์ต่างตัวแทบไม่ตรงแท่งกันเลย โหวตแบบ state จึงมีความหมาย
 *
 * state ของแต่ละกลยุทธ์ (ใช้ข้อมูล ≤ แท่งปัจจุบันเท่านั้น ไม่ lookahead):
 *  - MOM  : sign(ROC 20 แท่ง)
 *  - TR   : EMA50 เหนือ/ใต้ EMA200
 *  - E    : EMA14 เหนือ/ใต้ EMA60
 *  - UT   : ราคาปิด เหนือ/ใต้ UT Bot trailing stop (ATR6×2)
 *  - REV  : RSI<30 → +1 (ขายมากเกิน), RSI>70 → -1, อื่นๆ 0
 *  - DC   : sticky — ทิศ breakout 20 แท่งล่าสุดค้างไว้จนกลับฝั่ง
 *  - 52H  : sticky — เข้าโซน ≥98% high → +1, หลุด ≤90% → -1
 *  - 3BR  : event — โหวตค้าง 5 แท่งหลัง pattern
 *
 * Composite edge: score ข้ามเกณฑ์ minVotes (เช่น ≥ +3) ครั้งแรก → marker MIX▲ (และฝั่งตรงข้าม)
 */
object MixSignalEngine {

    /** alias → kind มาตรฐาน */
    val ALIASES = mapOf(
        "tsmom" to "MOM", "mom" to "MOM",
        "trend" to "TR", "tr" to "TR",
        "reversal" to "REV", "rev" to "REV",
        "donchian" to "DC", "dc" to "DC",
        "w52high" to "52H", "52h" to "52H",
        "ema1460" to "E", "ema14_60" to "E", "e" to "E",
        "utbot" to "UT", "ut" to "UT",
        "threebar" to "3BR", "3br" to "3BR"
    )

    fun parseKinds(csv: String): List<String> =
        csv.split(",", " ", "+", "|").mapNotNull { ALIASES[it.trim().lowercase()] }.distinct()

    /** โหวตของแต่ละ kind ณ แท่ง i (+1/-1/0) — series ต้องคำนวณจาก candles เดียวกัน */
    fun votesAt(c: SeriesCache, kinds: List<String>, i: Int): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (k in kinds) out[k] = voteOf(k, c, i)
        return out
    }

    /** markers ของ mix (edge เมื่อ score ข้ามเกณฑ์) — คืนเรียงตามเวลา (ส่ง cache เข้ามาได้ถ้าสร้างไว้แล้ว) */
    fun mixMarkers(candles: List<Candle>, kinds: List<String>, minVotes: Int, cache: SeriesCache? = null): List<SignalMarkerProvider.SignalMarker> {
        if (candles.size < 210 || kinds.size < 2) return emptyList()
        val c = cache ?: SeriesCache(candles)
        val n = candles.size
        val out = mutableListOf<SignalMarkerProvider.SignalMarker>()
        var prevScore = 0
        for (i in 210 until n) {
            var score = 0
            for (k in kinds) score += voteOf(k, c, i)
            val crossed = (prevScore < minVotes && score >= minVotes) || (prevScore > -minVotes && score <= -minVotes)
            if (crossed) {
                val side = if (score > 0) "BUY" else "SELL"
                out += SignalMarkerProvider.SignalMarker(
                    candles[i].timestamp, side, "MIX${if (side == "BUY") "▲" else "▼"}",
                    if (side == "BUY") "#00BFA5" else "#FF5252"
                )
            }
            if (score != 0) prevScore = score
        }
        return out
    }

    /** score โหวตรวม ณ แท่ง i (บวก = ฝั่ง BUY) */
    fun scoreAt(c: SeriesCache, kinds: List<String>, i: Int): Int {
        var s = 0; for (k in kinds) s += voteOf(k, c, i); return s
    }

    // ─── Series cache (คำนวณครั้งเดียวต่อชุดแท่ง) ──────────────────────

    class SeriesCache(candles: List<Candle>) {
        val n = candles.size
        val closes = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val opens = candles.map { it.open }
        val e14 = ema(closes, 14); val e50 = ema(closes, 50)
        val e60 = ema(closes, 60); val e200 = ema(closes, 200)
        val rsi14 = rsiSeries(closes, 14)
        val atr6 = atrSeries(candles, 6)
        val utStop = DoubleArray(n)
        val dcState = IntArray(n)     // sticky donchian direction
        val w52State = IntArray(n)    // sticky 52H direction
        val brState = IntArray(n)     // 3BR vote persist 5 แท่ง

        init {
            // UT Bot stop (สูตรเดียวกับ SignalMarkerProvider)
            for (i in 0 until n) {
                val src = closes[i]; val srcPrev = closes[max(0, i - 1)]
                val nLoss = 2.0 * atr6[i]
                utStop[i] = when {
                    src > utStop[max(0, i - 1)] && srcPrev > utStop[max(0, i - 1)] -> max(utStop[max(0, i - 1)], src - nLoss)
                    src < utStop[max(0, i - 1)] && srcPrev < utStop[max(0, i - 1)] -> min(utStop[max(0, i - 1)], src + nLoss)
                    src > utStop[max(0, i - 1)] -> src - nLoss
                    else -> src + nLoss
                }
            }
            // Donchian sticky state
            val p = StrategySignalProvider.DONCHIAN_PERIOD
            var st = 0
            for (i in 0 until n) {
                if (i >= p + 1) {
                    var hh = Double.NEGATIVE_INFINITY; var ll = Double.POSITIVE_INFINITY
                    for (j in i - p until i) { hh = max(hh, highs[j]); ll = min(ll, lows[j]) }
                    if (closes[i] > hh) st = 1 else if (closes[i] < ll) st = -1
                }
                dcState[i] = st
            }
            // 52H sticky state (warmup 100 แท่งแรก = 0 — ตรงกับ marker provider)
            var runHigh = 0.0; var s52 = 0
            for (i in 0 until n) {
                runHigh = max(runHigh, highs[i])
                if (i >= 100) {
                    val prox = closes[i] / runHigh
                    if (prox >= StrategySignalProvider.W52_PROX_BUY) s52 = 1
                    else if (prox <= StrategySignalProvider.W52_PROX_SELL) s52 = -1
                }
                w52State[i] = s52
            }
            // 3BR: โหวตค้าง 5 แท่งหลัง pattern (สูตร pattern เดียวกับ SignalMarkerProvider)
            var lastBr = 0; var lastBrBar = -99
            for (i in 2 until n) {
                val bull = (closes[i - 2] < opens[i - 2]) &&
                    (lows[i - 1] < lows[i - 2]) && (highs[i - 1] < highs[i - 2]) && (closes[i - 1] < opens[i - 1]) &&
                    (closes[i] > opens[i]) && (highs[i] > highs[i - 2])
                val bear = (closes[i - 2] > opens[i - 2]) &&
                    (highs[i - 1] > highs[i - 2]) && (lows[i - 1] > lows[i - 2]) && (closes[i - 1] > opens[i - 1]) &&
                    (closes[i] < opens[i]) && (lows[i] < lows[i - 2])
                if (bull) { lastBr = 1; lastBrBar = i } else if (bear) { lastBr = -1; lastBrBar = i }
                brState[i] = if (i - lastBrBar <= 5) lastBr else 0
            }
        }

        private fun ema(values: List<Double>, period: Int): List<Double> {
            if (values.size < period) return List(values.size) { Double.NaN }
            val k = 2.0 / (period + 1)
            val out = ArrayList<Double>(values.size)
            var prev = values.take(period).average()
            repeat(period - 1) { out.add(Double.NaN) }
            out.add(prev)
            for (i in period until values.size) { prev = values[i] * k + prev * (1 - k); out.add(prev) }
            return out
        }

        private fun rsiSeries(closes: List<Double>, period: Int): DoubleArray {
            val out = DoubleArray(closes.size) { 50.0 }
            if (closes.size <= period) return out
            var gain = 0.0; var loss = 0.0
            for (i in 1..period) { val d = closes[i] - closes[i - 1]; if (d > 0) gain += d else loss -= d }
            var avgGain = gain / period; var avgLoss = loss / period
            out[period] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
            for (i in period + 1 until closes.size) {
                val d = closes[i] - closes[i - 1]
                avgGain = (avgGain * (period - 1) + max(d, 0.0)) / period
                avgLoss = (avgLoss * (period - 1) + max(-d, 0.0)) / period
                out[i] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
            }
            return out
        }

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
            for (i in period + 1 until candles.size) { atr = (atr * (period - 1) + trs[i]) / period; out[i] = atr }
            for (i in 0 until period) out[i] = out[period]
            return out
        }
    }

    private fun voteOf(kind: String, c: SeriesCache, i: Int): Int = when (kind) {
        "MOM" -> {
            if (i < StrategySignalProvider.TSMOM_LOOKBACK) 0
            else {
                val roc = c.closes[i] - c.closes[i - StrategySignalProvider.TSMOM_LOOKBACK]
                if (roc > 0) 1 else if (roc < 0) -1 else 0
            }
        }
        "TR" -> {
            val a = c.e50[i]; val b = c.e200[i]
            if (a.isNaN() || b.isNaN()) 0 else if (a > b) 1 else -1
        }
        "E" -> {
            val a = c.e14[i]; val b = c.e60[i]
            if (a.isNaN() || b.isNaN()) 0 else if (a > b) 1 else -1
        }
        "UT" -> if (c.closes[i] > c.utStop[i]) 1 else -1
        "REV" -> {
            val r = c.rsi14[i]
            if (r < 30) 1 else if (r > 70) -1 else 0
        }
        "DC" -> c.dcState[i]
        "52H" -> c.w52State[i]
        "3BR" -> c.brState[i]
        else -> 0
    }
}

package com.example.personalaibot.automation

import com.example.personalaibot.logDebug
import com.example.personalaibot.tools.trading.Candle
import com.example.personalaibot.tools.trading.SmcApiService
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * IndicatorAlertProvider — คำนวณอินดิเคเตอร์จากแท่งเทียนจริง (300 แท่ง, cache ใน DB)
 *
 * เหตุผลที่มี tool นี้: TradingView scanner endpoint (/symbol) ให้ค่าจำกัด —
 * EMA/Stoch/CCI/AO/BB upper/lower/BB width คืน null หมด (พิสูจน์แล้ว 2026-08-04)
 * แต่เรามีแท่งเทียนครบจาก SmcApiService จึงคำนวณเองได้ทุกค่า แม่นกว่าและไม่โดน rate limit
 *
 * ใช้ผ่าน tool_name = "trading_indicators" ใน AlertJob
 * เลือก timeframe ได้ด้วย suffix @TF ในสัญลักษณ์ เช่น "XAUUSD@15m" (default 1h)
 */
class IndicatorAlertProvider(private val smcApi: SmcApiService) {

    companion object {
        private val SUPPORTED_TF = setOf("1m", "5m", "15m", "30m", "1h", "4h", "1D")

        /** แยก "XAUUSD@15m" → ("XAUUSD", "15m"); ไม่มี suffix → default "1h" */
        fun splitSymbolAndTf(raw: String): Pair<String, String> {
            val parts = raw.split("@")
            val sym = parts[0].uppercase().trim()
            val tf = parts.getOrNull(1)?.trim()?.lowercase()?.let { t ->
                SUPPORTED_TF.firstOrNull { it.lowercase() == t }
            } ?: "1h"
            return sym to tf
        }
    }

    /** ดึงแท่งเทียนแล้วคำนวณอินดิเคเตอร์ทั้งหมด → map พร้อม evaluate */
    suspend fun fetch(rawSymbol: String): Map<String, String> {
        val (symbol, tf) = splitSymbolAndTf(rawSymbol)
        val result = smcApi.fetchCandlesWithSource(symbol, tf, 300)
        val candles = result.candles
        if (candles.size < 60) {
            return mapOf("error" to "candles ${candles.size} < 60 (${result.source})")
        }
        logDebug("IndicatorProvider", "$symbol/$tf: ${candles.size} candles from ${result.source}")

        val closes = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val last = candles.last()

        // ── EMA ──
        val ema20 = ema(closes, 20).last()
        val ema50 = ema(closes, 50).last()
        val ema200 = if (closes.size >= 200) ema(closes, 200).last() else null
        val ema50Series = ema(closes, 50)
        val ema200Series = if (closes.size >= 200) ema(closes, 200) else null

        // cross state: เทียบ ema50 vs ema200 แท่งปัจจุบัน vs แท่งก่อน
        val crossState = if (ema200Series != null && ema200 != null) {
            val cur = ema50Series.last() - ema200Series.last()
            val prev = ema50Series[ema50Series.lastIndex - 1] - ema200Series[ema200Series.lastIndex - 1]
            when {
                prev <= 0 && cur > 0 -> "GOLDEN_CROSS"   // เพิ่งตัดขึ้นแท่งนี้
                prev >= 0 && cur < 0 -> "DEATH_CROSS"    // เพิ่งตัดลงแท่งนี้
                cur > 0 -> "BULLISH"                     // อยู่โซนบวก
                else -> "BEARISH"
            }
        } else "N/A"

        // ── MACD (12,26,9) ──
        val ema12 = ema(closes, 12)
        val ema26 = ema(closes, 26)
        // macd valid ตั้งแต่ index 25 (ema26 ต้องการ 26 จุด) — ตัด NaN ช่วงต้นออก
        // ก่อนคำนวณ signal ไม่งั้น NaN จะ propagate ตลอด series
        val macdFull = ema12.mapIndexed { i, v -> v - ema26[i] }
        val macdSeries = macdFull.drop(25)
        val signalSeries = ema(macdSeries, 9)
        val macd = macdSeries.last()
        val macdSignal = signalSeries.last()
        val macdHist = macd - macdSignal

        // ── RSI (14, Wilder) ──
        val rsi = rsi(closes, 14)

        // ── Stochastic (14,3,3) ──
        val stochK = stochK(closes, highs, lows, 14)
        val stochD = sma(stochK.filterNotNull().mapIndexed { i, v -> i to v }, 3)

        // ── CCI (20) ──
        val cci = cci(candles, 20)

        // ── Bollinger Bands (20, 2) ──
        val bb = bollinger(closes, 20, 2.0)

        // ── ATR (14, Wilder) ──
        val atr = atr(candles, 14)

        return buildMap {
            put("symbol", symbol)
            put("timeframe", tf)
            put("source", result.source)
            put("close", fmt(last.close))
            put("ema20", fmt(ema20))
            put("ema50", fmt(ema50))
            ema200?.let { put("ema200", fmt(it)) }
            // spread เป็นตัวเลข — ตั้ง alert แบบ "ema50_200_spread >= 0" = โซน golden
            put("ema20_50_spread", fmt(ema20 - ema50))
            ema200?.let { put("ema50_200_spread", fmt(ema50 - it)) }
            put("ema_cross_state", crossState)
            put("macd", fmt(macd))
            put("macd_signal", fmt(macdSignal))
            put("macd_hist", fmt(macdHist))
            rsi?.let { put("rsi14", fmt(it)) }
            stochK.lastOrNull()?.let { put("stoch_k", fmt(it)) }
            stochD?.let { put("stoch_d", fmt(it)) }
            cci?.let { put("cci20", fmt(it)) }
            bb?.let { (basis, upper, lower) ->
                put("bb_basis", fmt(basis))
                put("bb_upper", fmt(upper))
                put("bb_lower", fmt(lower))
                if (basis != 0.0) put("bb_width", fmt((upper - lower) / basis * 100.0))
            }
            atr?.let { put("atr14", fmt(it)) }
        }
    }

    // ─── Math helpers (ล้วน pure Kotlin — test ได้) ─────────────────────────

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

    private fun sma(indexed: List<Pair<Int, Double>>, period: Int): Double? {
        if (indexed.size < period) return null
        return indexed.takeLast(period).map { it.second }.average()
    }

    private fun rsi(closes: List<Double>, period: Int): Double? {
        if (closes.size <= period) return null
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d >= 0) gain += d else loss -= d
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        for (i in period + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + maxOf(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + maxOf(-d, 0.0)) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    private fun stochK(closes: List<Double>, highs: List<Double>, lows: List<Double>, period: Int): List<Double?> {
        val out = ArrayList<Double?>(closes.size)
        for (i in closes.indices) {
            if (i < period - 1) { out.add(null); continue }
            val hh = highs.subList(i - period + 1, i + 1).max()
            val ll = lows.subList(i - period + 1, i + 1).min()
            out.add(if (hh == ll) 50.0 else (closes[i] - ll) / (hh - ll) * 100.0)
        }
        return out
    }

    private fun cci(candles: List<Candle>, period: Int): Double? {
        if (candles.size < period) return null
        val tps = candles.map { (it.high + it.low + it.close) / 3.0 }
        val window = tps.takeLast(period)
        val sma = window.average()
        val meanDev = window.map { abs(it - sma) }.average()
        if (meanDev == 0.0) return 0.0
        return (tps.last() - sma) / (0.015 * meanDev)
    }

    private fun bollinger(closes: List<Double>, period: Int, mult: Double): Triple<Double, Double, Double>? {
        if (closes.size < period) return null
        val window = closes.takeLast(period)
        val basis = window.average()
        val sd = sqrt(window.map { (it - basis) * (it - basis) }.average())
        return Triple(basis, basis + mult * sd, basis - mult * sd)
    }

    private fun atr(candles: List<Candle>, period: Int): Double? {
        if (candles.size <= period) return null
        val trs = ArrayList<Double>()
        for (i in 1 until candles.size) {
            val h = candles[i].high
            val l = candles[i].low
            val pc = candles[i - 1].close
            trs.add(maxOf(h - l, abs(h - pc), abs(l - pc)))
        }
        var atr = trs.take(period).average()
        for (i in period until trs.size) {
            atr = (atr * (period - 1) + trs[i]) / period
        }
        return atr
    }
}

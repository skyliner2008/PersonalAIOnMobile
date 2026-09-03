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
        val SUPPORTED_TF = setOf("1m", "5m", "15m", "30m", "1h", "4h", "1D", "1W")

        fun normalizeTf(tf: String): String = com.example.personalaibot.tools.trading.TaIndicators.normalizeTimeframe(tf)

        /** แยก "XAUUSD@15m" หรือ "XAUUSD@H4" → ("XAUUSD", "4h"); ไม่มี suffix → default "1h" */
        fun splitSymbolAndTf(raw: String): Pair<String, String> {
            val parts = raw.split("@")
            val sym = parts[0].uppercase().trim()
            val tf = parts.getOrNull(1)?.let { normalizeTf(it) } ?: "1h"
            return sym to tf
        }
    }

    suspend fun fetch(rawSymbol: String): Map<String, String> {
        val (symbol, tf) = splitSymbolAndTf(rawSymbol)
        val result = smcApi.fetchTradingViewCandlesOnly(symbol, tf, 500)
        val candles = result.candles
        val minCandles = when (tf) {
            "1D", "1W" -> 35
            else -> 60
        }
        if (candles.size < minCandles) return mapOf("error" to "TV candles ${candles.size} < $minCandles (${result.source})")

        val closes = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val volumes = candles.map { it.volume.coerceAtLeast(0.0) }
        val last = candles.last()
        fun value(series: List<Double>) = series.lastOrNull()?.takeIf { it.isFinite() }
        fun smaValues(values: List<Double>, period: Int): Double? = if (values.size < period) null else values.takeLast(period).average()
        fun pct(v: Double): Double = if (last.close != 0.0) v / last.close * 100.0 else 0.0

        val ema20 = value(ema(closes, 20)); val ema50 = value(ema(closes, 50)); val ema200 = value(ema(closes, 200))
        val ema7 = value(ema(closes, 7)); val ema9 = value(ema(closes, 9)); val ema14 = value(ema(closes, 14)); val ema21 = value(ema(closes, 21)); val ema100 = value(ema(closes, 100))
        val sma20 = smaValues(closes, 20); val sma50 = smaValues(closes, 50); val sma100 = smaValues(closes, 100); val sma200 = smaValues(closes, 200)

        val ema50Series = ema(closes, 50); val ema200Series = ema(closes, 200)
        val crossState = when {
            ema50Series.size >= 2 && ema200Series.size >= 2 && ema50Series[ema50Series.lastIndex - 1] <= ema200Series[ema200Series.lastIndex - 1] && ema50Series.last() > ema200Series.last() -> "GOLDEN_CROSS"
            ema50Series.size >= 2 && ema200Series.size >= 2 && ema50Series[ema50Series.lastIndex - 1] >= ema200Series[ema200Series.lastIndex - 1] && ema50Series.last() < ema200Series.last() -> "DEATH_CROSS"
            ema50 != null && ema200 != null && ema50 > ema200 -> "BULLISH"
            ema50 != null && ema200 != null -> "BEARISH"
            else -> "N/A"
        }

        val ema12 = ema(closes, 12); val ema26 = ema(closes, 26)
        val macdSeries = ema12.mapIndexed { i, v -> v - ema26[i] }.filter { it.isFinite() }
        val macd = macdSeries.lastOrNull()?.takeIf { it.isFinite() }
        val signalSeries = if (macdSeries.size >= 9) ema(macdSeries, 9) else emptyList()
        val macdSignal = signalSeries.lastOrNull()?.takeIf { it.isFinite() }
        val macdHist = if (macd != null && macdSignal != null) macd - macdSignal else null
        val rsi7 = rsi(closes, 7); val rsi14 = rsi(closes, 14); val rsi21 = rsi(closes, 21)
        val rsi14Prev = rsi(closes.dropLast(1), 14)
        val stochK = stochK(closes, highs, lows, 14, 3); val stochD = sma(stochK.filterNotNull(), 3)
        val cci20 = cci(candles, 20)
        val medians = candles.map { (it.high + it.low) / 2.0 }; val ao = if (medians.size >= 34) medians.takeLast(5).average() - medians.takeLast(34).average() else null
        val adxPack = adx(candles, 14)
        val bb = bollinger(closes, 20, 2.0)
        val atr7 = atr(candles, 7); val atr14 = atr(candles, 14); val atr21 = atr(candles, 21)
        val supertrend = com.example.personalaibot.tools.trading.TaIndicators.supertrend(highs, lows, closes)
        val typical = candles.map { (it.high + it.low + it.close) / 3.0 }
        val vwapDen = volumes.sum().coerceAtLeast(1e-9); val vwap = typical.zip(volumes).sumOf { it.first * it.second } / vwapDen
        val volumeAvg20 = smaValues(volumes, 20)?.coerceAtLeast(1e-9) ?: 1.0; val volumeRatio20 = volumes.last() / volumeAvg20
        val prior = candles[candles.lastIndex - 1]
        val pivot = (prior.high + prior.low + prior.close) / 3.0
        val r1 = 2 * pivot - prior.low; val s1 = 2 * pivot - prior.high; val r2 = pivot + prior.high - prior.low; val s2 = pivot - prior.high + prior.low
        val donchian20High = highs.takeLast(20).maxOrNull(); val donchian20Low = lows.takeLast(20).minOrNull()

        return buildMap {
            put("symbol", symbol); put("timeframe", tf); put("source", result.source); put("close", fmt(last.close))
            listOf("ema7" to ema7, "ema9" to ema9, "ema14" to ema14, "ema20" to ema20, "ema21" to ema21, "ema50" to ema50, "ema100" to ema100, "ema200" to ema200,
                "sma20" to sma20, "sma50" to sma50, "sma100" to sma100, "sma200" to sma200,
                "rsi7" to rsi7, "rsi14" to rsi14, "rsi21" to rsi21, "macd" to macd, "macd_signal" to macdSignal, "macd_hist" to macdHist,
                "stoch_k" to stochK.lastOrNull(), "stoch_d" to stochD, "cci20" to cci20, "ao" to ao,
                "atr7" to atr7, "atr14" to atr14, "atr21" to atr21, "vwap" to vwap, "vwap_distance_pct" to pct(last.close - vwap),
                "volume_ratio20" to volumeRatio20, "pivot" to pivot, "r1" to r1, "r2" to r2, "s1" to s1, "s2" to s2,
                "donchian20_high" to donchian20High, "donchian20_low" to donchian20Low).forEach { (k, v) -> if (v != null && v.isFinite()) put(k, fmt(v)) }
            put("ema_cross_state", crossState)
            rsi14Prev?.let { put("rsi14_prev", fmt(it)) }
            adxPack?.let { (a, p, m) -> put("adx", fmt(a)); put("di_plus", fmt(p)); put("di_minus", fmt(m)) }
            supertrend?.let { st -> put("supertrend", fmt(st.value)); put("supertrend_direction", if (st.isBullish) "BULLISH" else "BEARISH") }
            bb?.let { (basis, upper, lower) -> put("bb_basis", fmt(basis)); put("bb_upper", fmt(upper)); put("bb_lower", fmt(lower)); if (basis != 0.0) put("bb_width", fmt((upper - lower) / basis * 100.0)); put("bb_percent_b", fmt((last.close - lower) / (upper - lower).coerceAtLeast(1e-9) * 100.0)) }
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

    private fun sma(values: List<Double>, period: Int): Double? {
        if (values.size < period) return null
        return values.takeLast(period).average()
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

    private fun stochK(closes: List<Double>, highs: List<Double>, lows: List<Double>, period: Int = 14, smooth: Int = 3): List<Double?> {
        val raw = ArrayList<Double?>()
        for (i in closes.indices) {
            if (i < period - 1) { raw.add(null); continue }
            val hh = highs.subList(i - period + 1, i + 1).max()
            val ll = lows.subList(i - period + 1, i + 1).min()
            raw.add(if (hh == ll) 50.0 else (closes[i] - ll) / (hh - ll) * 100.0)
        }
        if (smooth <= 1) return raw
        val out = ArrayList<Double?>()
        for (i in raw.indices) {
            if (i < period - 1 + smooth - 1) { out.add(null); continue }
            val window = (i - smooth + 1..i).mapNotNull { raw[it] }
            out.add(if (window.size == smooth) window.average() else null)
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

    private fun atr(candles: List<Candle>, period: Int): Double? {        if (candles.size <= period) return null
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

    /** ADX (Wilder) → Triple(ADX, +DI, -DI); ต้องมีแท่งอย่างน้อย period*2+1 */
    private fun adx(candles: List<Candle>, period: Int): Triple<Double, Double, Double>? {
        if (candles.size <= period * 2) return null
        val n = candles.size
        val tr = DoubleArray(n); val pdm = DoubleArray(n); val ndm = DoubleArray(n)
        for (i in 1 until n) {
            val h = candles[i].high; val l = candles[i].low
            val ph = candles[i - 1].high; val pl = candles[i - 1].low; val pc = candles[i - 1].close
            tr[i] = maxOf(h - l, abs(h - pc), abs(l - pc))
            val up = h - ph; val dn = pl - l
            pdm[i] = if (up > dn && up > 0) up else 0.0
            ndm[i] = if (dn > up && dn > 0) dn else 0.0
        }
        // Wilder smoothing ของ TR/+DM/-DM: seed = ผลรวม period แรก แล้วไล่ถึงแท่งสุดท้าย
        var sTr = (1..period).sumOf { tr[it] }
        var sPdm = (1..period).sumOf { pdm[it] }
        var sNdm = (1..period).sumOf { ndm[it] }
        val dxList = ArrayList<Double>()
        var lastPdi = 0.0; var lastNdi = 0.0
        for (i in period + 1 until n) {
            sTr = sTr - sTr / period + tr[i]
            sPdm = sPdm - sPdm / period + pdm[i]
            sNdm = sNdm - sNdm / period + ndm[i]
            if (sTr == 0.0) continue
            lastPdi = 100.0 * sPdm / sTr
            lastNdi = 100.0 * sNdm / sTr
            val denom = lastPdi + lastNdi
            dxList.add(if (denom == 0.0) 0.0 else 100.0 * abs(lastPdi - lastNdi) / denom)
        }
        if (dxList.size < period) return null
        // ADX = Wilder smooth ของ DX (seed = average period แรก)
        var adxV = dxList.take(period).average()
        for (k in period until dxList.size) {
            adxV = (adxV * (period - 1) + dxList[k]) / period
        }
        return Triple(adxV, lastPdi, lastNdi)
    }
}

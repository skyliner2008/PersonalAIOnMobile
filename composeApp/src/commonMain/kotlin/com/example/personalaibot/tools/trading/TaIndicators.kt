package com.example.personalaibot.tools.trading

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * TaIndicators — Technical Analysis indicator library กลาง (pure Kotlin/KMP)
 *
 * ใช้ร่วมกันระหว่าง SmcApiService, AdvancedTradingEngine, IndicatorAlertProvider
 * รองรับทุก Timeframe: M1, M5, M15, M30, H1, H4, D1, W1 (และ aliases 1m, 5m, 15m, 1h, 4h, 1D, 1W)
 *
 * หมายเหตุ: ฟังก์ชันทั้งหมดเป็น pure function ไม่มี side effect คำนวณตามมาตรฐานสากล (TradingView / MT5)
 */
object TaIndicators {

    // ─── Timeframe Normalization ─────────────────────────────────────────────

    /**
     * แปลง timeframe format ต่างๆ เช่น "m1", "1m", "H4", "4h", "D1", "1D", "W1", "1W"
     * ให้เป็นมาตรฐานสม่ำเสมอ ("1m", "5m", "15m", "30m", "1h", "4h", "1D", "1W")
     */
    fun normalizeTimeframe(raw: String?): String {
        if (raw.isNullOrBlank()) return "1h"
        return when (raw.trim().lowercase().replace(" ", "")) {
            "m1", "1m", "1" -> "1m"
            "m3", "3m", "3" -> "3m"
            "m5", "5m", "5" -> "5m"
            "m15", "15m", "15" -> "15m"
            "m30", "30m", "30" -> "30m"
            "h1", "1h", "60" -> "1h"
            "h2", "2h", "120" -> "2h"
            "h4", "4h", "240" -> "4h"
            "h6", "6h", "360" -> "6h"
            "h8", "8h", "480" -> "8h"
            "h12", "12h", "720" -> "12h"
            "d1", "1d", "d" -> "1D"
            "w1", "1w", "w" -> "1W"
            "mn1", "1mn", "mn", "m" -> "1M"
            else -> "1h"
        }
    }

    /** แปลงเป็น MT5 timeframe string เช่น "M1", "M5", "M15", "H1", "H4", "D1", "W1" */
    fun toMt5Timeframe(raw: String?): String {
        val norm = normalizeTimeframe(raw)
        return when (norm) {
            "1m" -> "M1"
            "3m" -> "M3"
            "5m" -> "M5"
            "15m" -> "M15"
            "30m" -> "M30"
            "1h" -> "H1"
            "2h" -> "H2"
            "4h" -> "H4"
            "6h" -> "H6"
            "8h" -> "H8"
            "12h" -> "H12"
            "1D" -> "D1"
            "1W" -> "W1"
            "1M" -> "MN1"
            else -> "H1"
        }
    }

    /** แปลงเป็น TradingView Resolution string เช่น "1", "5", "15", "60", "240", "D", "W" */
    fun toTvResolution(raw: String?): String {
        val norm = normalizeTimeframe(raw)
        return when (norm) {
            "1m" -> "1"
            "3m" -> "3"
            "5m" -> "5"
            "15m" -> "15"
            "30m" -> "30"
            "1h" -> "60"
            "2h" -> "120"
            "4h" -> "240"
            "1D" -> "D"
            "1W" -> "W"
            "1M" -> "M"
            else -> "60"
        }
    }

    // ─── Moving Averages ─────────────────────────────────────────────────────

    /** Simple Moving Average ของค่าสุดท้าย [period] ค่า */
    fun sma(data: List<Double>, period: Int): Double {
        if (data.isEmpty() || period <= 0) return 0.0
        val window = data.takeLast(period)
        return window.average()
    }

    /** SMA ทั้ง series (index i = ค่า SMA ณ จุดนั้น, null เมื่อข้อมูลไม่พอ) */
    fun smaSeries(data: List<Double>, period: Int): List<Double?> {
        if (period <= 0) return List(data.size) { null }
        val out = ArrayList<Double?>(data.size)
        var sum = 0.0
        for (i in data.indices) {
            sum += data[i]
            if (i >= period) sum -= data[i - period]
            out.add(if (i >= period - 1) sum / period else null)
        }
        return out
    }

    /** Exponential Moving Average (ค่าสุดท้าย) — seeder = SMA(period) แรกตามมาตรฐาน TA */
    fun ema(data: List<Double>, period: Int): Double {
        if (data.size < period || period <= 0) return data.lastOrNull() ?: 0.0
        val multiplier = 2.0 / (period + 1)
        var ema = data.take(period).average()
        for (i in period until data.size) {
            ema = (data[i] - ema) * multiplier + ema
        }
        return ema
    }

    /** EMA ทั้ง series — seeder = SMA(period) แรก, ก่อนหน้านั้นเป็น null */
    fun emaSeries(data: List<Double>, period: Int): List<Double?> {
        if (period <= 0 || data.isEmpty()) return emptyList()
        val out = ArrayList<Double?>(data.size)
        if (data.size < period) {
            return List(data.size) { null }
        }
        val multiplier = 2.0 / (period + 1)
        repeat(period - 1) { out.add(null) }
        var ema = data.take(period).average()
        out.add(ema)
        for (i in period until data.size) {
            ema = (data[i] - ema) * multiplier + ema
            out.add(ema)
        }
        return out
    }

    // ─── Momentum & Oscillators ──────────────────────────────────────────────

    /** RSI (Wilder's smoothing) ค่าสุดท้าย — คืน null เมื่อข้อมูลน้อยกว่า period+1 */
    fun rsi(closes: List<Double>, period: Int = 14): Double? {
        if (closes.size < period + 1) return null
        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) gainSum += diff else lossSum += -diff
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        for (i in period + 1 until closes.size) {
            val diff = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + maxOf(diff, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + maxOf(-diff, 0.0)) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    /** MACD (Moving Average Convergence Divergence) */
    data class MacdResult(val macd: Double, val signal: Double, val hist: Double)

    fun macd(
        closes: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MacdResult? {
        if (closes.size < slowPeriod + signalPeriod) return null
        val fastEma = emaSeries(closes, fastPeriod)
        val slowEma = emaSeries(closes, slowPeriod)

        val macdValues = ArrayList<Double>()
        for (i in slowPeriod - 1 until closes.size) {
            val f = fastEma[i]
            val s = slowEma[i]
            if (f != null && s != null) {
                macdValues.add(f - s)
            }
        }
        if (macdValues.size < signalPeriod) return null
        val macdLine = macdValues.last()
        val signalLine = ema(macdValues, signalPeriod)
        return MacdResult(macd = macdLine, signal = signalLine, hist = macdLine - signalLine)
    }

    /** Stochastic Oscillator (14, 3, 3) — Slow %K และ Slow %D ตามมาตรฐาน TV / MT5 */
    data class StochResult(val k: Double, val d: Double)

    fun stochastic(
        closes: List<Double>,
        highs: List<Double>,
        lows: List<Double>,
        kPeriod: Int = 14,
        dPeriod: Int = 3,
        smoothK: Int = 3
    ): StochResult? {
        val size = minOf(closes.size, highs.size, lows.size)
        if (size < kPeriod + smoothK + dPeriod - 2) return null

        // 1. Raw Fast %K
        val rawK = ArrayList<Double>()
        for (i in kPeriod - 1 until size) {
            var hh = Double.NEGATIVE_INFINITY
            var ll = Double.POSITIVE_INFINITY
            for (j in i - kPeriod + 1..i) {
                if (highs[j] > hh) hh = highs[j]
                if (lows[j] < ll) ll = lows[j]
            }
            val range = hh - ll
            val kVal = if (range > 0.0) (closes[i] - ll) / range * 100.0 else 50.0
            rawK.add(kVal)
        }

        // 2. Slow %K = SMA(smoothK) of Raw %K
        val slowK = ArrayList<Double>()
        for (i in smoothK - 1 until rawK.size) {
            val sum = (i - smoothK + 1..i).sumOf { rawK[it] }
            slowK.add(sum / smoothK)
        }
        if (slowK.size < dPeriod) return null

        // 3. Slow %D = SMA(dPeriod) of Slow %K
        val kLast = slowK.last()
        val dLast = (slowK.size - dPeriod until slowK.size).sumOf { slowK[it] } / dPeriod
        return StochResult(k = kLast, d = dLast)
    }

    /** Commodity Channel Index (CCI) */
    fun cci(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 20): Double? {
        val size = minOf(highs.size, lows.size, closes.size)
        if (size < period) return null
        val tp = List(size) { i -> (highs[i] + lows[i] + closes[i]) / 3.0 }
        val window = tp.takeLast(period)
        val mean = window.average()
        val meanDev = window.sumOf { abs(it - mean) } / period
        if (meanDev == 0.0) return 0.0
        return (tp.last() - mean) / (0.015 * meanDev)
    }

    // ─── Volatility & Trend ──────────────────────────────────────────────────

    /** Average True Range ค่าสุดท้าย (Wilder's smoothing RMA) */
    fun atr(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): Double? {
        val size = minOf(highs.size, lows.size, closes.size)
        if (size < period + 1) return null
        val trs = ArrayList<Double>(size - 1)
        for (i in 1 until size) {
            val tr = maxOf(
                highs[i] - lows[i],
                maxOf(abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1]))
            )
            trs.add(tr)
        }
        var atr = trs.take(period).average()
        for (i in period until trs.size) {
            atr = (atr * (period - 1) + trs[i]) / period
        }
        return atr
    }

    /** Bollinger Bands (basis = SMA, upper/lower = basis ± mult * stdev) */
    data class BollingerBands(val basis: Double, val upper: Double, val lower: Double, val width: Double, val percentB: Double)

    fun bollingerBands(data: List<Double>, period: Int = 20, mult: Double = 2.0): BollingerBands? {
        if (data.size < period) return null
        val window = data.takeLast(period)
        val basis = window.average()
        val variance = window.fold(0.0) { acc, v -> acc + (v - basis) * (v - basis) } / period
        val stdev = sqrt(variance)
        val upper = basis + mult * stdev
        val lower = basis - mult * stdev
        val width = if (basis != 0.0) (upper - lower) / basis * 100.0 else 0.0
        val lastClose = data.last()
        val percentB = if (upper != lower) (lastClose - lower) / (upper - lower) * 100.0 else 50.0
        return BollingerBands(basis, upper, lower, width, percentB)
    }

    /** Average Directional Index (ADX) — Wilder's smoothing */
    data class AdxResult(val adx: Double, val diPlus: Double, val diMinus: Double)

    fun adx(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): AdxResult? {
        val size = minOf(highs.size, lows.size, closes.size)
        if (size <= period * 2) return null
        val tr = DoubleArray(size)
        val pdm = DoubleArray(size)
        val ndm = DoubleArray(size)

        for (i in 1 until size) {
            val h = highs[i]
            val l = lows[i]
            val ph = highs[i - 1]
            val pl = lows[i - 1]
            val pc = closes[i - 1]
            tr[i] = maxOf(h - l, abs(h - pc), abs(l - pc))
            val up = h - ph
            val dn = pl - l
            pdm[i] = if (up > dn && up > 0) up else 0.0
            ndm[i] = if (dn > up && dn > 0) dn else 0.0
        }

        var sTr = (1..period).sumOf { tr[it] }
        var sPdm = (1..period).sumOf { pdm[it] }
        var sNdm = (1..period).sumOf { ndm[it] }

        val dxList = ArrayList<Double>()
        var lastPdi = 0.0
        var lastNdi = 0.0

        for (i in period + 1 until size) {
            sTr = sTr - (sTr / period) + tr[i]
            sPdm = sPdm - (sPdm / period) + pdm[i]
            sNdm = sNdm - (sNdm / period) + ndm[i]
            if (sTr == 0.0) continue
            lastPdi = 100.0 * sPdm / sTr
            lastNdi = 100.0 * sNdm / sTr
            val denom = lastPdi + lastNdi
            dxList.add(if (denom == 0.0) 0.0 else 100.0 * abs(lastPdi - lastNdi) / denom)
        }
        if (dxList.size < period) return null
        var adxVal = dxList.take(period).average()
        for (k in period until dxList.size) {
            adxVal = (adxVal * (period - 1) + dxList[k]) / period
        }
        return AdxResult(adx = adxVal, diPlus = lastPdi, diMinus = lastNdi)
    }

    /** Supertrend (ATR Band-based trend filter) */
    data class SupertrendResult(val value: Double, val isBullish: Boolean)

    fun supertrend(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int = 10,
        multiplier: Double = 3.0
    ): SupertrendResult? {
        val size = minOf(highs.size, lows.size, closes.size)
        if (size < period + 1) return null

        var upperBand = 0.0
        var lowerBand = 0.0
        var isBullish = true

        for (i in period until size) {
            val subH = highs.subList(0, i + 1)
            val subL = lows.subList(0, i + 1)
            val subC = closes.subList(0, i + 1)
            val currAtr = atr(subH, subL, subC, period) ?: continue

            val hl2 = (highs[i] + lows[i]) / 2.0
            val basicUpper = hl2 + (multiplier * currAtr)
            val basicLower = hl2 - (multiplier * currAtr)

            upperBand = if (basicUpper < upperBand || closes[i - 1] > upperBand) basicUpper else upperBand
            lowerBand = if (basicLower > lowerBand || closes[i - 1] < lowerBand) basicLower else lowerBand

            isBullish = when {
                closes[i] > upperBand -> true
                closes[i] < lowerBand -> false
                else -> isBullish
            }
        }

        val stValue = if (isBullish) lowerBand else upperBand
        return SupertrendResult(value = stValue, isBullish = isBullish)
    }

    /** Standard deviation (population) ของค่าสุดท้าย [period] ค่า */
    fun stdev(data: List<Double>, period: Int): Double? {
        if (data.size < period || period <= 0) return null
        val window = data.takeLast(period)
        val mean = window.average()
        val variance = window.fold(0.0) { acc, v -> acc + (v - mean) * (v - mean) } / period
        return sqrt(variance)
    }
}

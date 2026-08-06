package com.example.personalaibot.tools.trading

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * TaIndicators — Technical Analysis indicator library กลาง (pure Kotlin/KMP)
 *
 * ใช้ร่วมกันระหว่าง SmcApiService, AdvancedTradingEngine, ModernTechnicalApiService
 * เพื่อไม่ให้ indicator logic ถูก implement ซ้ำหลายที่
 *
 * หมายเหตุ: ฟังก์ชันทั้งหมดเป็น pure function ไม่มี side effect
 */
object TaIndicators {

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

    /** Exponential Moving Average (ค่าสุดท้าย) — multiplier = 2/(period+1) */
    fun ema(data: List<Double>, period: Int): Double {
        if (data.isEmpty() || period <= 0) return 0.0
        val multiplier = 2.0 / (period + 1)
        var ema = data[0]
        for (i in 1 until data.size) {
            ema = (data[i] - ema) * multiplier + ema
        }
        return ema
    }

    /** EMA ทั้ง series */
    fun emaSeries(data: List<Double>, period: Int): List<Double> {
        if (data.isEmpty() || period <= 0) return emptyList()
        val multiplier = 2.0 / (period + 1)
        val out = ArrayList<Double>(data.size)
        var ema = data[0]
        out.add(ema)
        for (i in 1 until data.size) {
            ema = (data[i] - ema) * multiplier + ema
            out.add(ema)
        }
        return out
    }

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

    /** Average True Range ค่าสุดท้าย (Wilder's smoothing) */
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

    /** Bollinger Bands (basis = SMA, bands = basis ± mult * stdev แบบ population) */
    data class BollingerBands(val basis: Double, val upper: Double, val lower: Double, val width: Double)

    fun bollingerBands(data: List<Double>, period: Int = 20, mult: Double = 2.0): BollingerBands? {
        if (data.size < period) return null
        val window = data.takeLast(period)
        val basis = window.average()
        val variance = window.fold(0.0) { acc, v -> acc + (v - basis) * (v - basis) } / period
        val stdev = sqrt(variance)
        val upper = basis + mult * stdev
        val lower = basis - mult * stdev
        val width = if (basis != 0.0) (upper - lower) / basis else 0.0
        return BollingerBands(basis, upper, lower, width)
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

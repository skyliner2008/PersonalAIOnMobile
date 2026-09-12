package com.skyliner2008.jarvis.automation.strategy

import com.skyliner2008.jarvis.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * BBSqueezeTrendEngine — Port จาก `BBSqueezeTrend.txt` (Jesse / TradingView)
 * Trend-following Bollinger Band vs Keltner Channel squeeze strategy:
 * - ตรวจจับ Bollinger Bands บีบตัวเข้าข้างใน Keltner Channels (Squeeze On)
 * - เมื่อขยายตัวระเบิดออกนอก Keltner Channels (Squeeze Fired)
 * - กรองทิศทางด้วย Linear Regression Slope ของราคาปิด
 * - กรองสภาวะเทรนด์ด้วย ADX(14) >= Threshold
 * - เหมาะมากสำหรับการคาดการณ์ (Squeeze On = รอระเบิดพลัง) และการยืนยัน (Squeeze Fired = จุดเข้าตามเทรนด์)
 */
object BBSqueezeTrendEngine {

    data class BBSqueezeResult(
        val isSqueezeOn: Boolean,
        val isSqueezeFired: Boolean,
        val squeezeBarsCount: Int,
        val linRegSlope: Double,
        val adx: Double,
        val isAdxOk: Boolean,
        val bbUpper: Double,
        val bbLower: Double,
        val kcUpper: Double,
        val kcLower: Double,
        val bandWidth: Double,
        val atr14: Double,
        val signalSide: String, // "BUY", "SELL", "ANTICIPATE_BUY", "ANTICIPATE_SELL", "NONE"
        val entryPrice: Double,
        val stopLoss: Double,
        val takeProfit: Double,
        val reasonTh: String
    )

    fun evaluate(
        candles: List<Candle>,
        bbPeriod: Int = 29,
        bbDev: Double = 1.82,
        kcMult: Double = 1.56,
        slopePeriod: Int = 11,
        adxThreshold: Double = 19.11,
        minSqueezeBars: Int = 4,
        atrStopMult: Double = 1.74,
        atrTargetMult: Double = 3.71
    ): BBSqueezeResult? {
        if (candles.size < max(bbPeriod, max(slopePeriod, 14)) + 10) return null

        val n = candles.size
        val liveIdx = n - 1
        val live = candles[liveIdx]
        val closes = candles.map { it.close }

        // 1. Calculate ATR (14)
        val atrArr = calculateAtrSeries(candles, 14)
        val atr14 = atrArr[liveIdx].coerceAtLeast(0.0001)

        // 2. Bollinger Bands
        val bbBasisArr = calculateSma(closes, bbPeriod)
        val bbUpperArr = DoubleArray(n)
        val bbLowerArr = DoubleArray(n)
        for (i in (bbPeriod - 1) until n) {
            val sd = calculateStdev(closes, bbPeriod, i)
            bbUpperArr[i] = bbBasisArr[i] + bbDev * sd
            bbLowerArr[i] = bbBasisArr[i] - bbDev * sd
        }

        // 3. Keltner Channels (EMA basis + KC Mult * ATR)
        val kcBasisArr = calculateEma(closes, bbPeriod)
        val kcUpperArr = DoubleArray(n)
        val kcLowerArr = DoubleArray(n)
        for (i in (bbPeriod - 1) until n) {
            val a = atrArr[i]
            kcUpperArr[i] = kcBasisArr[i] + kcMult * a
            kcLowerArr[i] = kcBasisArr[i] - kcMult * a
        }

        // 4. Squeeze condition series
        val squeezeOnArr = BooleanArray(n)
        for (i in (bbPeriod - 1) until n) {
            squeezeOnArr[i] = bbUpperArr[i] < kcUpperArr[i] && bbLowerArr[i] > kcLowerArr[i]
        }

        val isSqueezeOnNow = squeezeOnArr[liveIdx]
        val isSqueezeOnPrev = if (liveIdx > 0) squeezeOnArr[liveIdx - 1] else false

        // Count how many bars squeeze has been on consecutively up to previous bar
        var squeezeBarsCount = 0
        var checkIdx = if (isSqueezeOnNow) liveIdx else liveIdx - 1
        while (checkIdx >= 0 && squeezeOnArr[checkIdx]) {
            squeezeBarsCount++
            checkIdx--
        }

        // Squeeze fired: previous bar was Squeeze On, current bar expanded outside, and had >= minSqueezeBars
        val isSqueezeFired = isSqueezeOnPrev && !isSqueezeOnNow && squeezeBarsCount >= minSqueezeBars

        // 5. Linear Regression Slope
        val linRegSlope = calculateLinRegSlope(closes, slopePeriod, liveIdx)

        // 6. ADX (14)
        val adx = calculateAdx(candles, 14)
        val isAdxOk = adx >= adxThreshold

        // Determine Signal & Anticipation state
        var signalSide = "NONE"
        var reason = ""
        val entryPrice = live.close

        val bbUpperNow = bbUpperArr[liveIdx]
        val bbLowerNow = bbLowerArr[liveIdx]
        val kcUpperNow = kcUpperArr[liveIdx]
        val kcLowerNow = kcLowerArr[liveIdx]
        val bandWidth = bbUpperNow - bbLowerNow

        if (isSqueezeFired && isAdxOk) {
            if (linRegSlope > 0) {
                signalSide = "BUY"
                reason = "BB Squeeze Fired ระเบิดออกนอก Keltner Channel ขาขึ้น (Slope ${"%.4f".format(linRegSlope)}, ADX ${"%.1f".format(adx)})"
            } else if (linRegSlope < 0) {
                signalSide = "SELL"
                reason = "BB Squeeze Fired ระเบิดออกนอก Keltner Channel ขาลง (Slope ${"%.4f".format(linRegSlope)}, ADX ${"%.1f".format(adx)})"
            }
        } else if (isSqueezeOnNow && squeezeBarsCount >= minSqueezeBars) {
            // Anticipation Mode: สะสมพลังใน Keltner Channel มานานพอแล้ว เตรียมระเบิด
            if (linRegSlope > 0) {
                signalSide = "ANTICIPATE_BUY"
                reason = "Bollinger Bands บีบตัวแคบใน Keltner Channel ($squeezeBarsCount แท่ง) ความชัน Slope บ่งชี้สะสมแรงขึ้น (Slope ${"%.4f".format(linRegSlope)})"
            } else if (linRegSlope < 0) {
                signalSide = "ANTICIPATE_SELL"
                reason = "Bollinger Bands บีบตัวแคบใน Keltner Channel ($squeezeBarsCount แท่ง) ความชัน Slope บ่งชี้สะสมแรงลง (Slope ${"%.4f".format(linRegSlope)})"
            }
        }

        val stopLoss = if (signalSide.contains("BUY")) entryPrice - atrStopMult * atr14 else entryPrice + atrStopMult * atr14
        val takeProfit = if (signalSide.contains("BUY")) entryPrice + atrTargetMult * atr14 else entryPrice - atrTargetMult * atr14

        return BBSqueezeResult(
            isSqueezeOn = isSqueezeOnNow,
            isSqueezeFired = isSqueezeFired,
            squeezeBarsCount = squeezeBarsCount,
            linRegSlope = linRegSlope,
            adx = adx,
            isAdxOk = isAdxOk,
            bbUpper = bbUpperNow,
            bbLower = bbLowerNow,
            kcUpper = kcUpperNow,
            kcLower = kcLowerNow,
            bandWidth = bandWidth,
            atr14 = atr14,
            signalSide = signalSide,
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            reasonTh = reason
        )
    }

    private fun calculateSma(values: List<Double>, period: Int): DoubleArray {
        val out = DoubleArray(values.size) { Double.NaN }
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            if (i >= period - 1) out[i] = sum / period
        }
        return out
    }

    private fun calculateEma(values: List<Double>, period: Int): DoubleArray {
        val out = DoubleArray(values.size) { Double.NaN }
        if (values.size < period) return out
        val k = 2.0 / (period + 1)
        var sum = 0.0
        for (i in 0 until period) sum += values[i]
        var prev = sum / period
        out[period - 1] = prev
        for (i in period until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out[i] = prev
        }
        return out
    }

    private fun calculateStdev(values: List<Double>, period: Int, idx: Int): Double {
        if (idx < period - 1) return 0.0
        val window = values.subList(idx - period + 1, idx + 1)
        val mean = window.average()
        return sqrt(window.sumOf { (it - mean).pow(2) } / window.size)
    }

    private fun calculateAtrSeries(candles: List<Candle>, period: Int): DoubleArray {
        val out = DoubleArray(candles.size) { 0.0 }
        if (candles.size <= period) return out
        val trs = DoubleArray(candles.size)
        for (i in 1 until candles.size) {
            val h = candles[i].high
            val l = candles[i].low
            val pc = candles[i - 1].close
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
        for (i in 0 until period) out[i] = out[period]
        return out
    }

    private fun calculateLinRegSlope(values: List<Double>, period: Int, idx: Int): Double {
        if (idx < period - 1) return 0.0
        val n = period.toDouble()
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumXX = 0.0

        for (i in 0 until period) {
            val x = i.toDouble()
            val y = values[idx - period + 1 + i]
            sumX += x
            sumY += y
            sumXY += x * y
            sumXX += x * x
        }

        val denominator = n * sumXX - sumX * sumX
        return if (abs(denominator) > 0.0000001) (n * sumXY - sumX * sumY) / denominator else 0.0
    }

    private fun calculateAdx(candles: List<Candle>, period: Int): Double {
        if (candles.size <= period + 1) return 20.0
        val trList = DoubleArray(candles.size)
        val dmPlus = DoubleArray(candles.size)
        val dmMinus = DoubleArray(candles.size)

        for (i in 1 until candles.size) {
            val upMove = candles[i].high - candles[i - 1].high
            val downMove = candles[i - 1].low - candles[i].low
            dmPlus[i] = if (upMove > downMove && upMove > 0) upMove else 0.0
            dmMinus[i] = if (downMove > upMove && downMove > 0) downMove else 0.0
            val h = candles[i].high
            val l = candles[i].low
            val pc = candles[i - 1].close
            trList[i] = maxOf(h - l, abs(h - pc), abs(l - pc))
        }

        val smoothTr = calculateEma(trList.toList(), period).last().coerceAtLeast(0.0001)
        val smoothDmPlus = calculateEma(dmPlus.toList(), period).last()
        val smoothDmMinus = calculateEma(dmMinus.toList(), period).last()

        val plusDi = (smoothDmPlus / smoothTr) * 100.0
        val minusDi = (smoothDmMinus / smoothTr) * 100.0
        return if (plusDi + minusDi > 0) (abs(plusDi - minusDi) / (plusDi + minusDi) * 100.0) else 20.0
    }
}

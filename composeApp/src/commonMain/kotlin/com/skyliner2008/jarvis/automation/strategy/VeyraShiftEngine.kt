package com.skyliner2008.jarvis.automation.strategy

import com.skyliner2008.jarvis.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * VeyraShiftEngine — Port จาก Pine Script v6 `Veyra Shift Ledger [JOAT]`
 * รวม 6 เสาหลักเชิงสถาบัน (Institutional Shift Engine):
 * 1. Trend + Regime: Fast EMA 21, Mid EMA 55, Slow EMA 200, ATR 21, DMI/ADX 14, ATR Percentile Rank, HTF EMA 55
 * 2. Pressure Engine: Signed body efficiency, Pressure Oscillator (-100 ถึง +100), Volume Impulse, Bull/Bear Absorption
 * 3. Auction Engine: Volume-weighted Price, Auction Dev, Value High/Low, Discount / Premium / Reclaim / Reject
 * 4. Structure Engine: Pivot 5 High/Low, Displacement (|body| > 1.15x ATR + Vol), BOS, Liquidity Sweep, FVG
 * 5. Composite Shift Score (0-100 pts)
 * 6. Execution Rails: Entry, Structural / ATR Stop Loss, TP1 (1.0R), TP2 (2.0R), TP3 (3.2R)
 */
object VeyraShiftEngine {

    data class VeyraShiftResult(
        val longScore: Double,
        val shortScore: Double,
        val dominantDir: Int, // 1 = BID SHIFT, -1 = ASK SHIFT, 0 = BALANCED
        val dominantScore: Double,
        val pressureOsc: Double,
        val volumeImpulse: Double,
        val isAbsorptionBull: Boolean,
        val isAbsorptionBear: Boolean,
        val isCompression: Boolean,
        val isExpansion: Boolean,
        val auctionState: String, // "DISCOUNT", "PREMIUM", "RECLAIM", "REJECT", "VALUE"
        val structureState: String, // "BULL", "BEAR", "WAIT"
        val bosUp: Boolean,
        val bosDown: Boolean,
        val sweepLow: Boolean,
        val sweepHigh: Boolean,
        val fvgBull: Boolean,
        val fvgBear: Boolean,
        val longSignal: Boolean,
        val shortSignal: Boolean,
        val entry: Double,
        val stopLoss: Double,
        val tp1: Double,
        val tp2: Double,
        val tp3: Double,
        val risk: Double,
        val rr1: Double = 1.0,
        val rr2: Double = 2.0,
        val rr3: Double = 3.2,
        val reasonTh: String
    )

    fun evaluate(
        candles: List<Candle>,
        htfCandles: List<Candle>? = null,
        fastLen: Int = 21,
        midLen: Int = 55,
        slowLen: Int = 200,
        atrLen: Int = 21,
        adxLen: Int = 14,
        pressureLen: Int = 24,
        volumeLen: Int = 34,
        auctionLen: Int = 90,
        valueDevMult: Double = 1.15,
        pivotLen: Int = 5,
        displaceMult: Double = 1.15,
        longThreshold: Double = 70.0,
        shortThreshold: Double = 70.0,
        riskAtr: Double = 1.7
    ): VeyraShiftResult? {
        if (candles.size < 50) return null
        val n = candles.size
        val effSlowLen = slowLen.coerceAtMost(max(20, n - 5))
        val effMidLen = midLen.coerceAtMost(max(15, effSlowLen - 5))
        val effFastLen = fastLen.coerceAtMost(max(10, effMidLen - 5))
        val liveIdx = n - 1
        val live = candles[liveIdx]
        val closes = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val opens = candles.map { it.open }
        val volumes = candles.map { it.volume }

        // 1. Trend, Momentum, and Regime
        val atrArr = calculateAtrSeries(candles, atrLen)
        val atr = atrArr[liveIdx].coerceAtLeast(0.0001)

        val fastEma = calculateEma(closes, effFastLen)[liveIdx]
        val midEma = calculateEma(closes, effMidLen)[liveIdx]
        val slowEma = calculateEma(closes, effSlowLen)[liveIdx]

        // VWAP approximation
        var cumVol = 0.0
        var cumHlc3Vol = 0.0
        val vwapLookback = min(120, n)
        for (i in (n - vwapLookback) until n) {
            val hlc3 = (highs[i] + lows[i] + closes[i]) / 3.0
            val v = max(1.0, volumes[i])
            cumVol += v
            cumHlc3Vol += hlc3 * v
        }
        val vwap = if (cumVol > 0) cumHlc3Vol / cumVol else closes[liveIdx]
        val prevVwap = if (n > 2) (highs[n - 2] + lows[n - 2] + closes[n - 2]) / 3.0 else vwap

        // MACD (12, 26, 9)
        val ema12 = calculateEma(closes, 12)
        val ema26 = calculateEma(closes, 26)
        val macdLine = closes.indices.map { if (ema12[it].isNaN() || ema26[it].isNaN()) 0.0 else ema12[it] - ema26[it] }
        val signalLine = calculateEma(macdLine, 9)
        val macdHist = macdLine[liveIdx] - signalLine[liveIdx]

        // RSI (14)
        val rsi14 = calculateRsi(closes, 14)[liveIdx]

        // DMI & ADX (14)
        val (plusDi, minusDi, adx) = calculateDmi(candles, adxLen)

        val trendBull = fastEma > midEma && midEma > slowEma && live.close > midEma
        val trendBear = fastEma < midEma && midEma < slowEma && live.close < midEma
        val vwapBull = live.close > vwap && vwap >= prevVwap
        val vwapBear = live.close < vwap && vwap <= prevVwap
        val momentumBull = macdHist > 0 && rsi14 > 52 && plusDi > minusDi
        val momentumBear = macdHist < 0 && rsi14 < 48 && minusDi > plusDi
        val trendStrength = (adx.coerceIn(0.0, 50.0)) * 2.0 // 0..100

        // Volatility Regime (ATR % Rank over 120 bars)
        val regimeLookback = min(120, n - atrLen)
        val atrPcts = (n - regimeLookback until n).map { i ->
            if (closes[i] > 0) atrArr[i] / closes[i] * 100.0 else 0.0
        }
        val curAtrPct = if (live.close > 0) atr / live.close * 100.0 else 0.0
        val countLower = atrPcts.count { it < curAtrPct }
        val atrRank = if (atrPcts.isNotEmpty()) countLower.toDouble() / atrPcts.size * 100.0 else 50.0
        val compression = atrRank < 32.0
        val expansion = atrRank > 62.0

        // HTF Basis Filter (4H / 240m EMA 55)
        val htfBull: Boolean
        val htfBear: Boolean
        if (htfCandles != null && htfCandles.size >= 55) {
            val htfCloses = htfCandles.map { it.close }
            val htfEma55 = calculateEma(htfCloses, 55).last()
            htfBull = live.close > htfEma55
            htfBear = live.close < htfEma55
        } else {
            htfBull = live.close > slowEma
            htfBear = live.close < slowEma
        }

        // 2. Pressure Engine
        val signedBodies = ArrayList<Double>(n)
        val absSignedBodies = ArrayList<Double>(n)
        for (i in 0 until n) {
            val b = closes[i] - opens[i]
            val sp = max(highs[i] - lows[i], 0.0001)
            val eff = b / sp
            val sb = eff * max(1.0, volumes[i])
            signedBodies.add(sb)
            absSignedBodies.add(abs(sb))
        }
        val pRaw = calculateEma(signedBodies, pressureLen)[liveIdx]
        val pBase = calculateEma(absSignedBodies, pressureLen)[liveIdx]
        val pressureOsc = if (pBase > 0.00001) (pRaw / pBase * 100.0).coerceIn(-100.0, 100.0) else 0.0
        val prevPressureOsc = if (liveIdx > 0) {
            val pr = calculateEma(signedBodies, pressureLen)[liveIdx - 1]
            val pb = calculateEma(absSignedBodies, pressureLen)[liveIdx - 1]
            if (pb > 0.00001) (pr / pb * 100.0).coerceIn(-100.0, 100.0) else 0.0
        } else 0.0

        val volWindow = volumes.subList(max(0, liveIdx - volumeLen + 1), liveIdx + 1)
        val avgVol = if (volWindow.isNotEmpty()) volWindow.average() else 1.0
        val volumeImpulse = if (avgVol > 0) live.volume / avgVol else 1.0

        val bidPressure = pressureOsc > 12.0 && volumeImpulse > 1.05
        val askPressure = pressureOsc < -12.0 && volumeImpulse > 1.05
        val prevCandle = candles[max(0, liveIdx - 1)]
        val absorptionBull = live.low < prevCandle.low && live.close > live.open && pressureOsc > prevPressureOsc && volumeImpulse > 1.1
        val absorptionBear = live.high > prevCandle.high && live.close < live.open && pressureOsc < prevPressureOsc && volumeImpulse > 1.1

        // 3. Auction Engine
        val auctionWindow = min(auctionLen, n)
        var sumHlc3Vol = 0.0
        var sumVol = 0.0
        val hlc3List = ArrayList<Double>(auctionWindow)
        for (i in (n - auctionWindow) until n) {
            val hlc3 = (highs[i] + lows[i] + closes[i]) / 3.0
            val v = max(1.0, volumes[i])
            sumHlc3Vol += hlc3 * v
            sumVol += v
            hlc3List.add(hlc3)
        }
        val weightedPrice = if (sumVol > 0) sumHlc3Vol / sumVol else live.close
        val hlc3Mean = hlc3List.average()
        val auctionDev = sqrt(hlc3List.sumOf { (it - hlc3Mean).pow(2) } / hlc3List.size)
        val valueHigh = weightedPrice + auctionDev * valueDevMult
        val valueLow = weightedPrice - auctionDev * valueDevMult
        val liveSpread = max(live.high - live.low, 0.0001)

        val auctionDiscount = live.close < valueLow && live.close > live.low + liveSpread * 0.45
        val auctionPremium = live.close > valueHigh && live.close < live.high - liveSpread * 0.45
        val auctionReclaim = live.close > valueLow && prevCandle.close <= valueLow
        val auctionReject = live.close < valueHigh && prevCandle.close >= valueHigh
        val insideValue = live.close in valueLow..valueHigh

        val auctionState = when {
            auctionDiscount -> "DISCOUNT"
            auctionPremium -> "PREMIUM"
            auctionReclaim -> "RECLAIM"
            auctionReject -> "REJECT"
            insideValue -> "VALUE"
            else -> "EXTENDED"
        }

        // 4. Structure Engine (Pivots, BOS, Sweep, FVG)
        var lastPivotHigh = Double.NaN
        var lastPivotLow = Double.NaN
        for (i in pivotLen until (n - pivotLen)) {
            val curH = highs[i]
            var isPivotH = true
            for (k in 1..pivotLen) {
                if (highs[i - k] >= curH || highs[i + k] >= curH) {
                    isPivotH = false; break
                }
            }
            if (isPivotH) lastPivotHigh = curH

            val curL = lows[i]
            var isPivotL = true
            for (k in 1..pivotLen) {
                if (lows[i - k] <= curL || lows[i + k] <= curL) {
                    isPivotL = false; break
                }
            }
            if (isPivotL) lastPivotLow = curL
        }

        val displacement = abs(live.close - live.open) > atr * displaceMult && volumeImpulse > 1.0
        val bosUp = !lastPivotHigh.isNaN() && live.close > lastPivotHigh && prevCandle.close <= lastPivotHigh && displacement
        val bosDown = !lastPivotLow.isNaN() && live.close < lastPivotLow && prevCandle.close >= lastPivotLow && displacement
        val sweepLow = !lastPivotLow.isNaN() && live.low < lastPivotLow && live.close > lastPivotLow
        val sweepHigh = !lastPivotHigh.isNaN() && live.high > lastPivotHigh && live.close < lastPivotHigh
        val fvgBull = n >= 3 && lows[liveIdx] > highs[liveIdx - 2] && live.close > live.open
        val fvgBear = n >= 3 && highs[liveIdx] < lows[liveIdx - 2] && live.close < live.open

        val structureBias = when {
            bosUp -> 1
            bosDown -> -1
            sweepLow -> 1
            sweepHigh -> -1
            else -> 0
        }
        val structureBull = structureBias == 1 || sweepLow || fvgBull
        val structureBear = structureBias == -1 || sweepHigh || fvgBear
        val structureState = when {
            structureBull -> "BULL"
            structureBear -> "BEAR"
            else -> "WAIT"
        }

        // 5. Shift Score (0-100 pts)
        fun score(cond: Boolean, weight: Double): Double = if (cond) weight else 0.0

        var longScoreRaw = 0.0
        longScoreRaw += score(trendBull, 16.0)
        longScoreRaw += score(vwapBull, 10.0)
        longScoreRaw += score(momentumBull, 12.0)
        longScoreRaw += score(bidPressure, 13.0)
        longScoreRaw += score(absorptionBull, 9.0)
        longScoreRaw += score(structureBull, 14.0)
        longScoreRaw += score(auctionDiscount || auctionReclaim, 10.0)
        longScoreRaw += score(htfBull, 8.0)
        longScoreRaw += score(expansion && !auctionPremium, 4.0)
        longScoreRaw += (trendStrength / 8.0).coerceIn(0.0, 4.0)

        var shortScoreRaw = 0.0
        shortScoreRaw += score(trendBear, 16.0)
        shortScoreRaw += score(vwapBear, 10.0)
        shortScoreRaw += score(momentumBear, 12.0)
        shortScoreRaw += score(askPressure, 13.0)
        shortScoreRaw += score(absorptionBear, 9.0)
        shortScoreRaw += score(structureBear, 14.0)
        shortScoreRaw += score(auctionPremium || auctionReject, 10.0)
        shortScoreRaw += score(htfBear, 8.0)
        shortScoreRaw += score(expansion && !auctionDiscount, 4.0)
        shortScoreRaw += (trendStrength / 8.0).coerceIn(0.0, 4.0)

        val longScore = longScoreRaw.coerceIn(0.0, 100.0)
        val shortScore = shortScoreRaw.coerceIn(0.0, 100.0)
        val dominantDir = when {
            longScore > shortScore -> 1
            shortScore > longScore -> -1
            else -> 0
        }
        val dominantScore = max(longScore, shortScore)

        val longSignal = longScore >= longThreshold && longScore > shortScore + 8.0 && htfBull && !auctionPremium
        val shortSignal = shortScore >= shortThreshold && shortScore > longScore + 8.0 && htfBear && !auctionDiscount

        // 6. Execution Rails
        val entry = live.close
        val planDir = if (dominantDir == 1) 1 else -1
        val structureStop = if (planDir == 1 && !lastPivotLow.isNaN()) lastPivotLow
        else if (planDir == -1 && !lastPivotHigh.isNaN()) lastPivotHigh
        else Double.NaN

        val atrStop = if (planDir == 1) entry - atr * riskAtr else entry + atr * riskAtr
        val stopLoss = if (structureStop.isNaN()) atrStop
        else if (planDir == 1) min(atrStop, structureStop)
        else max(atrStop, structureStop)

        val risk = max(abs(entry - stopLoss), atr * 0.25)
        val tp1 = if (planDir == 1) entry + risk * 1.0 else entry - risk * 1.0
        val tp2 = if (planDir == 1) entry + risk * 2.0 else entry - risk * 2.0
        val tp3 = if (planDir == 1) entry + risk * 3.2 else entry - risk * 3.2

        val reason = buildString {
            if (dominantDir == 1) {
                append("🟢 BID SHIFT (${"%.0f".format(longScore)}/100): ")
                val parts = mutableListOf<String>()
                if (bidPressure) parts.add("แรงซื้อหนาแน่น (Pressure ${"%.1f".format(pressureOsc)})")
                if (absorptionBull) parts.add("เกิด Bullish Absorption")
                if (auctionDiscount || auctionReclaim) parts.add("ราคาอยู่ในโซน Value Discount/Reclaim")
                if (structureBull) parts.add("โครงสร้างหนุนขาขึ้น ($structureState)")
                if (trendBull) parts.add("Trend EMAs ขาขึ้น")
                if (htfBull) parts.add("HTF สนับสนุน")
                append(parts.joinToString(" + "))
            } else if (dominantDir == -1) {
                append("🔴 ASK SHIFT (${"%.0f".format(shortScore)}/100): ")
                val parts = mutableListOf<String>()
                if (askPressure) parts.add("แรงขายหนาแน่น (Pressure ${"%.1f".format(pressureOsc)})")
                if (absorptionBear) parts.add("เกิด Bearish Absorption")
                if (auctionPremium || auctionReject) parts.add("ราคาอยู่ในโซน Value Premium/Reject")
                if (structureBear) parts.add("โครงสร้างหนุนขาลง ($structureState)")
                if (trendBear) parts.add("Trend EMAs ขาลง")
                if (htfBear) parts.add("HTF สนับสนุน")
                append(parts.joinToString(" + "))
            } else {
                append("⚖️ BALANCED (Long: ${"%.0f".format(longScore)}, Short: ${"%.0f".format(shortScore)})")
            }
        }

        return VeyraShiftResult(
            longScore = longScore,
            shortScore = shortScore,
            dominantDir = dominantDir,
            dominantScore = dominantScore,
            pressureOsc = pressureOsc,
            volumeImpulse = volumeImpulse,
            isAbsorptionBull = absorptionBull,
            isAbsorptionBear = absorptionBear,
            isCompression = compression,
            isExpansion = expansion,
            auctionState = auctionState,
            structureState = structureState,
            bosUp = bosUp,
            bosDown = bosDown,
            sweepLow = sweepLow,
            sweepHigh = sweepHigh,
            fvgBull = fvgBull,
            fvgBear = fvgBear,
            longSignal = longSignal,
            shortSignal = shortSignal,
            entry = entry,
            stopLoss = stopLoss,
            tp1 = tp1,
            tp2 = tp2,
            tp3 = tp3,
            risk = risk,
            reasonTh = reason
        )
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

    private fun calculateRsi(closes: List<Double>, period: Int): DoubleArray {
        val out = DoubleArray(closes.size) { 50.0 }
        if (closes.size <= period) return out
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d > 0) gain += d else loss -= d
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        out[period] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        for (i in period + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + max(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-d, 0.0)) / period
            out[i] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        }
        return out
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

    private fun calculateDmi(candles: List<Candle>, period: Int): Triple<Double, Double, Double> {
        if (candles.size <= period + 1) return Triple(25.0, 25.0, 20.0)
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
        val dx = if (plusDi + minusDi > 0) abs(plusDi - minusDi) / (plusDi + minusDi) * 100.0 else 0.0
        return Triple(plusDi, minusDi, dx)
    }
}

package com.skyliner2008.jarvis.automation

import com.skyliner2008.jarvis.automation.smc.MarketContextDigest
import com.skyliner2008.jarvis.automation.strategy.BBSqueezeTrendEngine
import com.skyliner2008.jarvis.automation.strategy.FastRsiEngine
import com.skyliner2008.jarvis.automation.strategy.VeyraShiftEngine
import com.skyliner2008.jarvis.tools.trading.Candle
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * SignalFeatureExtractor — สกัด Snapshot คุณลักษณะของแท่งเทียนและสภาวะตลาด (Market Genome)
 * ณ แท่งที่เกิดสัญญาณเข้าเทรดจริง เพื่อจัดเก็บเป็น Dataset สำหรับวิเคราะห์และปรับจูนด้วย AI ภายนอก
 */
object SignalFeatureExtractor {

    data class FeatureSnapshot(
        val bodyRatio: Double,
        val upperWickRatio: Double,
        val lowerWickRatio: Double,
        val candleDir: String,
        val spreadAtrRatio: Double,
        val volumeImpulse: Double,
        val rsi14: Double,
        val fastRsi5: Double,
        val stochK: Double,
        val macdHist: Double,
        val ema14_60_spread_pct: Double,
        val ema14_60_state: String,
        val emaTrend50_200: String,
        val adx14: Double,
        val atr14: Double,
        val bbWidthAtr: Double,
        val bbPctB: Double,
        val squeezeState: String,
        val h4Trend: String,
        val h1Trend: String,
        val m15Trend: String,
        val keyzoneProximity: Double,
        val keyzoneType: String,
        val marketZone: String,
        val veyraScore: Double,
        val veyraState: String,
        val session: String,
        val hourUtc: Int,
        val dayOfWeek: Int
    )

    fun extract(
        candles: List<Candle>,
        sigIdx: Int,
        symbol: String,
        interval: String,
        strategy: String,
        side: String,
        mtfDigest: MarketContextDigest.Digest? = null
    ): FeatureSnapshot {
        val safeIdx = sigIdx.coerceIn(0, max(0, candles.size - 1))
        val currentCandles = if (candles.isNotEmpty()) candles.subList(0, min(safeIdx + 1, candles.size)) else emptyList()
        val c = if (currentCandles.isNotEmpty()) currentCandles.last() else Candle(0.0, 0.0, 0.0, 0.0, 0.0, 0L)
        val closes = currentCandles.map { it.close }
        val n = currentCandles.size

        // 1. Candlestick Price Action
        val range = (c.high - c.low).takeIf { it > 1e-9 } ?: 1e-9
        val body = abs(c.close - c.open)
        val upperWick = max(0.0, c.high - max(c.open, c.close))
        val lowerWick = max(0.0, min(c.open, c.close) - c.low)
        val bodyRatio = body / range
        val upperWickRatio = upperWick / range
        val lowerWickRatio = lowerWick / range
        val candleDir = if (c.close >= c.open) "BULLISH" else "BEARISH"

        val atr14Series = atrSeries(currentCandles, 14)
        val atr14 = if (atr14Series.isNotEmpty()) atr14Series.last() else 1.0
        val spreadAtrRatio = if (atr14 > 1e-9) range / atr14 else 1.0

        val vols = currentCandles.map { it.volume }
        val avgVol20 = if (vols.size >= 20) vols.takeLast(20).average() else vols.average().takeIf { it > 0 } ?: 1.0
        val volumeImpulse = if (avgVol20 > 0) c.volume / avgVol20 else 1.0

        // 2. Oscillators & Indicators
        val rsi14Val = if (n >= 15) rsiSeries(closes, 14).last() else 50.0
        val fastRsiRes = runCatching { FastRsiEngine.evaluate(currentCandles) }.getOrNull()
        val fastRsi5 = fastRsiRes?.rsi5 ?: (if (n >= 6) rsiSeries(closes, 5).last() else 50.0)

        // Stochastic 14
        val stochK = runCatching {
            if (n >= 14) {
                val window = currentCandles.takeLast(14)
                val highest = window.maxOf { it.high }
                val lowest = window.minOf { it.low }
                val den = highest - lowest
                if (den > 1e-9) ((c.close - lowest) / den) * 100.0 else 50.0
            } else 50.0
        }.getOrElse { 50.0 }

        // MACD 12/26/9
        val macdHist = runCatching {
            if (n >= 35) {
                val ema12 = ema(closes, 12)
                val ema26 = ema(closes, 26)
                val macdLine = ema12.zip(ema26) { a, b -> if (!a.isNaN() && !b.isNaN()) a - b else 0.0 }
                val signalLine = ema(macdLine, 9)
                val mVal = macdLine.last()
                val sVal = signalLine.last()
                if (!mVal.isNaN() && !sVal.isNaN()) mVal - sVal else 0.0
            } else 0.0
        }.getOrElse { 0.0 }

        // 3. Trend & Moving Averages
        val ema14Val = if (n >= 14) ema(closes, 14).last() else c.close
        val ema60Val = if (n >= 60) ema(closes, 60).last() else c.close
        val ema14_60_spread_pct = if (c.close > 0) (abs(ema14Val - ema60Val) / c.close) * 100.0 else 0.0
        val ema14_60_state = if (ema14Val >= ema60Val) "BULLISH" else "BEARISH"

        val ema50Val = if (n >= 50) ema(closes, 50).last() else Double.NaN
        val ema200Val = if (n >= 200) ema(closes, 200).last() else Double.NaN
        val emaTrend50_200 = when {
            ema50Val.isNaN() || ema200Val.isNaN() -> "NEUTRAL"
            ema50Val >= ema200Val -> "UPTREND"
            else -> "DOWNTREND"
        }

        // ADX 14
        val adx14 = calculateAdx(currentCandles, 14)

        // 4. Volatility & Bollinger Bands
        val (bbUpper, bbLower, bbBasis) = calculateBollingerBands(closes, 20, 2.0)
        val bbWidthAtr = if (atr14 > 1e-9) (bbUpper - bbLower) / atr14 else 2.0
        val bbDen = (bbUpper - bbLower).takeIf { it > 1e-9 } ?: 1.0
        val bbPctB = (c.close - bbLower) / bbDen

        val squeezeRes = runCatching { BBSqueezeTrendEngine.evaluate(currentCandles) }.getOrNull()
        val squeezeState = when {
            squeezeRes?.isSqueezeFired == true -> "FIRED"
            squeezeRes?.isSqueezeOn == true -> "SQUEEZE_ON"
            else -> "NORMAL"
        }

        // 5. Market Structure & MTF
        val mtfLines = mtfDigest?.tfLines.orEmpty()
        val h4Line = mtfLines.firstOrNull { it.tf.equals("H4", ignoreCase = true) }
        val h1Line = mtfLines.firstOrNull { it.tf.equals("H1", ignoreCase = true) }
        val m15Line = mtfLines.firstOrNull { it.tf.equals("M15", ignoreCase = true) }

        val h4Trend = if (h4Line != null) (if (h4Line.trend > 0) "BULLISH" else if (h4Line.trend < 0) "BEARISH" else "NEUTRAL") else emaTrend50_200
        val h1Trend = if (h1Line != null) (if (h1Line.trend > 0) "BULLISH" else if (h1Line.trend < 0) "BEARISH" else "NEUTRAL") else ema14_60_state
        val m15Trend = if (m15Line != null) (if (m15Line.trend > 0) "BULLISH" else if (m15Line.trend < 0) "BEARISH" else "NEUTRAL") else candleDir

        val keyzoneType = mtfDigest?.keyZoneHit?.takeIf { it.isNotBlank() } ?: "NONE"
        val keyzoneProximity = if (keyzoneType != "NONE") 0.3 else 999.0
        val marketZone = mtfDigest?.premiumDiscount ?: "EQUILIBRIUM"

        // 6. Institutional Orderflow
        val veyraRes = runCatching { VeyraShiftEngine.evaluate(currentCandles) }.getOrNull()
        val veyraScore = veyraRes?.dominantScore ?: 0.0
        val veyraState = veyraRes?.auctionState ?: "NONE"

        // 7. Session & Timing
        val tz = TimeZone.UTC
        val instant = Instant.fromEpochMilliseconds(c.timestamp.takeIf { it > 0 } ?: kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
        val dt = instant.toLocalDateTime(tz)
        val hourUtc = dt.hour
        val dayOfWeek = dt.dayOfWeek.ordinal + 1 // 1=Mon .. 7=Sun

        val session = when (hourUtc) {
            in 0..6 -> "ASIA"
            in 7..11 -> "LONDON"
            in 12..16 -> "LONDON_NY_OVERLAP"
            in 17..21 -> "NEW_YORK"
            else -> "OFF_HOURS"
        }

        return FeatureSnapshot(
            bodyRatio = round2(bodyRatio),
            upperWickRatio = round2(upperWickRatio),
            lowerWickRatio = round2(lowerWickRatio),
            candleDir = candleDir,
            spreadAtrRatio = round2(spreadAtrRatio),
            volumeImpulse = round2(volumeImpulse),
            rsi14 = round2(rsi14Val),
            fastRsi5 = round2(fastRsi5),
            stochK = round2(stochK),
            macdHist = round4(macdHist),
            ema14_60_spread_pct = round2(ema14_60_spread_pct),
            ema14_60_state = ema14_60_state,
            emaTrend50_200 = emaTrend50_200,
            adx14 = round2(adx14),
            atr14 = round4(atr14),
            bbWidthAtr = round2(bbWidthAtr),
            bbPctB = round2(bbPctB),
            squeezeState = squeezeState,
            h4Trend = h4Trend,
            h1Trend = h1Trend,
            m15Trend = m15Trend,
            keyzoneProximity = round2(keyzoneProximity),
            keyzoneType = keyzoneType,
            marketZone = marketZone,
            veyraScore = round2(veyraScore),
            veyraState = veyraState,
            session = session,
            hourUtc = hourUtc,
            dayOfWeek = dayOfWeek
        )
    }

    fun extractJson(
        candles: List<Candle>,
        sigIdx: Int,
        symbol: String,
        interval: String,
        strategy: String,
        side: String,
        mtfDigest: MarketContextDigest.Digest? = null
    ): String {
        val s = extract(candles, sigIdx, symbol, interval, strategy, side, mtfDigest)
        return buildJsonObject {
            put("body_ratio", s.bodyRatio)
            put("upper_wick_ratio", s.upperWickRatio)
            put("lower_wick_ratio", s.lowerWickRatio)
            put("candle_dir", s.candleDir)
            put("spread_atr_ratio", s.spreadAtrRatio)
            put("volume_impulse", s.volumeImpulse)
            put("rsi14", s.rsi14)
            put("fast_rsi5", s.fastRsi5)
            put("stoch_k", s.stochK)
            put("macd_hist", s.macdHist)
            put("ema14_60_spread_pct", s.ema14_60_spread_pct)
            put("ema14_60_state", s.ema14_60_state)
            put("ema_trend_50_200", s.emaTrend50_200)
            put("adx14", s.adx14)
            put("atr14", s.atr14)
            put("bb_width_atr", s.bbWidthAtr)
            put("bb_pct_b", s.bbPctB)
            put("squeeze_state", s.squeezeState)
            put("h4_trend", s.h4Trend)
            put("h1_trend", s.h1Trend)
            put("m15_trend", s.m15Trend)
            put("keyzone_proximity", s.keyzoneProximity)
            put("keyzone_type", s.keyzoneType)
            put("market_zone", s.marketZone)
            put("veyra_score", s.veyraScore)
            put("veyra_state", s.veyraState)
            put("session", s.session)
            put("hour_utc", s.hourUtc)
            put("day_of_week", s.dayOfWeek)
        }.toString()
    }

    private fun round2(v: Double): Double = (v * 100.0).let { if (it.isNaN() || it.isInfinite()) 0.0 else kotlin.math.round(it) / 100.0 }
    private fun round4(v: Double): Double = (v * 10000.0).let { if (it.isNaN() || it.isInfinite()) 0.0 else kotlin.math.round(it) / 10000.0 }

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

    private fun rsiSeries(closes: List<Double>, period: Int): DoubleArray {
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
        for (i in 0 until period) out[i] = out[period]
        return out
    }

    private fun calculateBollingerBands(closes: List<Double>, period: Int, mult: Double): Triple<Double, Double, Double> {
        if (closes.size < period) return Triple(0.0, 0.0, 0.0)
        val window = closes.takeLast(period)
        val mean = window.average()
        val sd = sqrt(window.sumOf { (it - mean).pow(2) } / window.size)
        return Triple(mean + mult * sd, mean - mult * sd, mean)
    }

    private fun calculateAdx(candles: List<Candle>, period: Int): Double {
        if (candles.size <= period * 2) return 20.0
        var plusDm = 0.0
        var minusDm = 0.0
        var trSum = 0.0
        for (i in candles.size - period until candles.size) {
            val up = candles[i].high - candles[i - 1].high
            val down = candles[i - 1].low - candles[i].low
            plusDm += if (up > down && up > 0) up else 0.0
            minusDm += if (down > up && down > 0) down else 0.0
            val h = candles[i].high; val l = candles[i].low; val pc = candles[i - 1].close
            trSum += maxOf(h - l, abs(h - pc), abs(l - pc))
        }
        if (trSum <= 1e-9) return 20.0
        val plusDi = (plusDm / trSum) * 100.0
        val minusDi = (minusDm / trSum) * 100.0
        val diSum = plusDi + minusDi
        return if (diSum > 1e-9) (abs(plusDi - minusDi) / diSum) * 100.0 else 20.0
    }
}

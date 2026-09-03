package com.example.personalaibot.automation

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * TradingView-first signal fusion for the mobile analysis plane.
 * Pure/deterministic: no MT5 dependency and no vendor fallback.
 */
object TradingViewSignalIntelligence {
    enum class Decision { BUY, SELL, WAIT }

    data class TimeframeSignal(
        val timeframe: String,
        val score: Double,
        val decision: Decision,
        val trend: Decision,
        val momentum: Decision,
        val reversal: Decision,
        val breakout: Decision,
        val dataQualityPct: Int,
        val bars: Int
    )

    data class FusionResult(
        val decision: Decision,
        val score: Double,
        val confidencePct: Int,
        val dataQualityPct: Int,
        val alignedTimeframes: Int,
        val totalTimeframes: Int,
        val smcScore: Double,
        val technicalScore: Double,
        val reason: String,
        val gate: String
    )

    data class Outcome(
        val decision: Decision,
        val entry: Double,
        val exit: Double,
        val returnPct: Double,
        val barsForward: Int,
        val hit: Boolean
    )

    fun analyze(timeframe: String, candles: List<Candle>): TimeframeSignal {
        if (candles.size < 60) {
            return TimeframeSignal(timeframe, 0.0, Decision.WAIT, Decision.WAIT, Decision.WAIT, Decision.WAIT, Decision.WAIT, 0, candles.size)
        }
        val closes = candles.map { it.close }
        val last = closes.last()
        val fast = ema(closes, min(50, max(5, closes.size / 5)))
        val slow = ema(closes, min(200, max(20, closes.size / 2)))
        val trend = when {
            last > slow && fast > slow -> Decision.BUY
            last < slow && fast < slow -> Decision.SELL
            else -> Decision.WAIT
        }
        val lookback = min(20, closes.size - 2)
        val roc = (last - closes[closes.size - 1 - lookback]) / closes[closes.size - 1 - lookback]
        val momentum = when { roc > 0.001 -> Decision.BUY; roc < -0.001 -> Decision.SELL; else -> Decision.WAIT }
        val rsi = rsi(closes, 14)
        val reversal = when { rsi < 30 -> Decision.BUY; rsi > 70 -> Decision.SELL; else -> Decision.WAIT }
        val prior = candles.subList(candles.size - 21, candles.size - 1)
        val upper = prior.maxOf { it.high }
        val lower = prior.minOf { it.low }
        val breakout = when { last > upper -> Decision.BUY; last < lower -> Decision.SELL; else -> Decision.WAIT }
        val components = listOf(trend to 1.2, momentum to 1.0, reversal to 0.8, breakout to 1.2)
        val score = components.sumOf { (signal, weight) -> if (signal == Decision.BUY) weight else if (signal == Decision.SELL) -weight else 0.0 }
        val decision = when { score >= 1.0 -> Decision.BUY; score <= -1.0 -> Decision.SELL; else -> Decision.WAIT }
        val quality = min(100, ((candles.size / 300.0) * 80.0 + if (candles.last().timestamp > 0) 20.0 else 10.0).toInt())
        return TimeframeSignal(timeframe, score, decision, trend, momentum, reversal, breakout, quality, candles.size)
    }

    fun fuse(
        timeframes: List<TimeframeSignal>,
        smcScore: Double = 0.0,
        indicatorScore: Double = 0.0
    ): FusionResult {
        if (timeframes.isEmpty()) {
            return FusionResult(
                Decision.WAIT, 0.0, 0, 0, 0, 0, smcScore, 0.0,
                "No TradingView timeframe data", "NO_DATA"
            )
        }

        val configuredWeights = mapOf("15m" to 0.20, "30m" to 0.20, "1h" to 0.30, "4h" to 0.30)
        val rawWeightTotal = timeframes.sumOf { configuredWeights[it.timeframe] ?: 0.0 }
        val weighted = if (rawWeightTotal > 0.0) {
            timeframes.sumOf { it.score * (configuredWeights[it.timeframe] ?: 0.0) } / rawWeightTotal
        } else {
            timeframes.map { it.score }.average()
        }
        val technical = (weighted / 4.2).coerceIn(-1.0, 1.0)
        val normalizedSmc = smcScore.coerceIn(-1.0, 1.0)
        val normalizedIndicators = indicatorScore.coerceIn(-1.0, 1.0)
        val total = (technical + normalizedIndicators * 0.15 + normalizedSmc * 0.35).coerceIn(-1.0, 1.0)
        val buy = timeframes.count { it.decision == Decision.BUY }
        val sell = timeframes.count { it.decision == Decision.SELL }
        val aligned = max(buy, sell)
        val decision = when {
            total >= 0.55 && buy >= 2 -> Decision.BUY
            total <= -0.55 && sell >= 2 -> Decision.SELL
            else -> Decision.WAIT
        }
        val avgQuality = timeframes.map { it.dataQualityPct }.average().toInt()
        val agreement = aligned.toDouble() / timeframes.size
        val rawConfidence = 45.0 + abs(total) * 35.0 + agreement * 20.0
        val confidence = min(95, rawConfidence.toInt())
        val gate = when {
            avgQuality < 70 -> "DATA_QUALITY"
            decision == Decision.WAIT -> "NO_CONFLUENCE"
            aligned < 2 -> "MTF_MISALIGNMENT"
            else -> "PASS"
        }
        val finalDecision = if (gate == "PASS") decision else Decision.WAIT
        val reason = "MTF=$buy BUY/$sell SELL, aligned=$aligned/${timeframes.size}, technical=${"%.2f".format(technical)}, indicators=${"%.2f".format(normalizedIndicators)}, smc=${"%.2f".format(normalizedSmc)}, quality=$avgQuality%"
        return FusionResult(finalDecision, total, confidence, avgQuality, aligned, timeframes.size, normalizedSmc, technical, reason, gate)
    }

    /** Deterministic forward outcome evaluator used for live-signal parity tests/history. */
    fun evaluateOutcome(decision: Decision, candles: List<Candle>, forwardBars: Int = 5): Outcome? {
        if (decision == Decision.WAIT || candles.size <= forwardBars) return null
        val entryIndex = candles.size - 1 - forwardBars
        val entry = candles[entryIndex].close
        val exit = candles.last().close
        val ret = if (decision == Decision.BUY) (exit - entry) / entry * 100.0 else (entry - exit) / entry * 100.0
        return Outcome(decision, entry, exit, ret, forwardBars, ret > 0.0)
    }

    private fun ema(values: List<Double>, period: Int): Double {
        val p = min(period, values.size)
        val alpha = 2.0 / (p + 1.0)
        var e = values.take(p).average()
        for (i in p until values.size) e = values[i] * alpha + e * (1.0 - alpha)
        return e
    }

    private fun rsi(values: List<Double>, period: Int): Double {
        if (values.size <= period) return 50.0
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val d = values[i] - values[i - 1]
            if (d >= 0) gain += d else loss -= d
        }
        for (i in period + 1 until values.size) {
            val d = values[i] - values[i - 1]
            gain = (gain * (period - 1) + max(d, 0.0)) / period
            loss = (loss * (period - 1) + max(-d, 0.0)) / period
        }
        if (loss == 0.0) return 100.0
        return 100.0 - 100.0 / (1.0 + gain / loss)
    }
}

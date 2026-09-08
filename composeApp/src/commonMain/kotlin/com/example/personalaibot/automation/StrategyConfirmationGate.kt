package com.example.personalaibot.automation

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.max

/**
 * Closed-bar quality gate for the classic strategy library.
 *
 * A marker is an event, not an order. The gate prevents weak crosses/breakouts
 * from becoming actionable candidates without changing the underlying signal
 * definition. SMC is intentionally not involved here.
 */
object StrategyConfirmationGate {
    data class Decision(
        val accepted: Boolean,
        val reason: String,
        val strength: Double
    )

    fun evaluate(candles: List<Candle>, index: Int, side: String, label: String): Decision {
        if (index !in candles.indices || index < 20) return Decision(false, "INSUFFICIENT_HISTORY", 0.0)
        val c = candles[index]
        val range = (c.high - c.low).coerceAtLeast(1e-9)
        val body = abs(c.close - c.open)
        val bodyRatio = body / range
        val atr = atr(candles, index, 14).coerceAtLeast(1e-9)
        val bodyAtr = body / atr
        val closeLocation = if (side == "BUY") (c.close - c.low) / range else (c.high - c.close) / range
        val kind = signalKind(label)

        val rawDecision = when (kind) {
            "REV" -> {
                // Reversal needs rejection, not merely an oscillator extreme.
                val wick = if (side == "BUY") minOf(c.open, c.close) - c.low else c.high - maxOf(c.open, c.close)
                val wickRatio = wick / range
                val ok = wickRatio >= 0.25 && closeLocation >= 0.55 && bodyRatio >= 0.15
                Decision(ok, if (ok) "REJECTION_CONFIRMED" else "WEAK_REJECTION", (wickRatio + closeLocation) / 2.0)
            }
            "DC", "52H" -> {
                // Breakout requires a meaningful body and close near the breakout side.
                val ok = bodyAtr >= 0.25 && bodyRatio >= 0.35 && closeLocation >= 0.65
                Decision(ok, if (ok) "BREAKOUT_CANDLE_CONFIRMED" else "WEAK_BREAKOUT_CANDLE", minOf(1.0, bodyAtr / 0.75))
            }
            "MOM", "TR", "E", "UT" -> {
                // Cross/flip strategies are noisy in flat candles. Demand directional body.
                val ok = bodyAtr >= 0.20 && bodyRatio >= 0.25 && closeLocation >= 0.55
                Decision(ok, if (ok) "MOMENTUM_CANDLE_CONFIRMED" else "WEAK_MOMENTUM_CANDLE", minOf(1.0, bodyAtr / 0.60))
            }
            "3BR" -> Decision(true, "PATTERN_CONFIRMED", 1.0)
            else -> Decision(true, "UNKNOWN_STRATEGY_UNGATED", 0.0)
        }

        // Closed-loop reinforcement: adjust confidence based on recent outcome performance
        val adj = runCatching { SignalOutcomeTracker.getStrategyConfidenceAdjustment(kind) }.getOrDefault(0.0)
        val finalStrength = (rawDecision.strength + adj).coerceIn(0.0, 1.0)
        val blockedByColdStreak = adj <= -0.20 && rawDecision.strength < 0.55
        val finalAccepted = rawDecision.accepted && !blockedByColdStreak
        val finalReason = if (blockedByColdStreak) "COLD_STREAK_REDUCED" else rawDecision.reason

        return Decision(finalAccepted, finalReason, finalStrength)
    }

    fun signalKind(label: String): String = when {
        label.startsWith("MOM") -> "MOM"
        label.startsWith("TR") -> "TR"
        label.startsWith("REV") -> "REV"
        label.startsWith("DC") -> "DC"
        label.startsWith("52H") -> "52H"
        label.startsWith("E") -> "E"
        label.startsWith("UT") -> "UT"
        label.startsWith("3BR") -> "3BR"
        else -> "UNKNOWN"
    }

    private fun atr(candles: List<Candle>, index: Int, period: Int): Double {
        val from = (index - period + 1).coerceAtLeast(1)
        if (from > index) return 0.0
        var sum = 0.0
        var count = 0
        for (i in from..index) {
            val c = candles[i]
            val prev = candles[i - 1]
            sum += max(c.high - c.low, max(abs(c.high - prev.close), abs(c.low - prev.close)))
            count++
        }
        return if (count > 0) sum / count else 0.0
    }
}

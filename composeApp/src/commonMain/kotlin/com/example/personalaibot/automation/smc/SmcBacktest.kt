package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.max

data class SmcBacktestConfig(
    val initialEquity: Double = 10_000.0,
    val riskPercent: Double = 1.0,
    val maxBarsInTrade: Int = 80,
    val aps: SmcApsConfig = SmcApsConfig()
)

data class SmcTradeResult(val barIndex: Int, val side: SmcApsDecision, val path: SmcApsPath, val rMultiple: Double, val barsHeld: Int, val exitReason: String)
data class SmcBacktestReport(val trades: List<SmcTradeResult>, val wins: Int, val losses: Int, val netR: Double, val profitFactor: Double, val maxDrawdownR: Double) {
    val winRate: Double get() = if (trades.isEmpty()) 0.0 else wins.toDouble() / trades.size
}

object SmcBacktest {
    fun run(candles: List<Candle>, symbol: String, timeframe: String, config: SmcBacktestConfig = SmcBacktestConfig()): SmcBacktestReport {
        if (candles.size < SmcSignals.WARMUP + 3) return emptyReport()
        val trades = mutableListOf<SmcTradeResult>()
        var i = SmcSignals.WARMUP
        var equityR = 0.0
        var peak = 0.0
        var maxDd = 0.0
        while (i < candles.lastIndex) {
            val window = candles.subList(max(0, i - SmcSignals.WINDOW + 1), i + 1)
            val signal = SmcApsEngine.evaluate(window, symbol, timeframe, config.aps)
            if (signal.decision == SmcApsDecision.NO_TRADE) { i++; continue }
            val exit = resolve(candles, i, signal, config.maxBarsInTrade)
            trades += SmcTradeResult(i, signal.decision, signal.path, exit.r, exit.bars, exit.reason)
            equityR += exit.r
            peak = max(peak, equityR)
            maxDd = max(maxDd, peak - equityR)
            i += max(1, exit.bars)
        }
        val wins = trades.count { it.rMultiple > 0 }
        val grossWin = trades.filter { it.rMultiple > 0 }.sumOf { it.rMultiple }
        val grossLoss = -trades.filter { it.rMultiple < 0 }.sumOf { it.rMultiple }
        return SmcBacktestReport(trades, wins, trades.count() - wins, equityR, if (grossLoss > 0) grossWin / grossLoss else if (grossWin > 0) Double.POSITIVE_INFINITY else 0.0, maxDd)
    }

    private data class Exit(val r: Double, val bars: Int, val reason: String)
    private fun resolve(c: List<Candle>, entryBar: Int, s: SmcApsSignal, maxBars: Int): Exit {
        val risk = kotlin.math.abs(s.entry - s.sl)
        val end = minOf(c.lastIndex, entryBar + maxBars)
        for (j in entryBar + 1..end) {
            val x = c[j]
            if (s.decision == SmcApsDecision.BUY) {
                if (x.low <= s.sl) return Exit(-1.0, j - entryBar, "SL")
                if (x.high >= s.tp) return Exit(if (risk > 0) kotlin.math.abs(s.tp - s.entry) / risk else 0.0, j - entryBar, "TP")
            } else {
                if (x.high >= s.sl) return Exit(-1.0, j - entryBar, "SL")
                if (x.low <= s.tp) return Exit(if (risk > 0) kotlin.math.abs(s.tp - s.entry) / risk else 0.0, j - entryBar, "TP")
            }
        }
        val last = c[end].close
        val pnl = if (s.decision == SmcApsDecision.BUY) last - s.entry else s.entry - last
        return Exit(if (risk > 0) pnl / risk else 0.0, end - entryBar, "TIME")
    }
    private fun emptyReport() = SmcBacktestReport(emptyList(), 0, 0, 0.0, 0.0, 0.0)
}

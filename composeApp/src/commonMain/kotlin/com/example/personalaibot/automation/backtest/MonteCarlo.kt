package com.example.personalaibot.automation.backtest

import kotlin.random.Random

object MonteCarlo {
    data class MonteCarloResult(
        val nSimulations: Int,
        val medianFinalBalance: Double,
        val p5FinalBalance: Double,
        val p95FinalBalance: Double,
        val medianMaxDrawdown: Double,
        val p95MaxDrawdown: Double,
        val probabilityOfRuin: Double,
        val probabilityOfProfit: Double
    )
    fun run(
        tradePnls: List<Double>, nSimulations: Int = 1000, initialBalance: Double = 10_000.0, ruinThreshold: Double = 0.5, seed: Long = 42
    ): MonteCarloResult {
        if (tradePnls.size < 5) return MonteCarloResult(0,0.0,0.0,0.0,0.0,0.0,0.0,0.0)
        require(nSimulations > 0) { "nSimulations must be > 0" }
        val rng = Random(seed); val finals = DoubleArray(nSimulations); val dds = DoubleArray(nSimulations)
        repeat(nSimulations) { i -> var equity=initialBalance; var peak=equity; var maxDd=0.0
            for(pnl in tradePnls.shuffled(rng)){ equity += pnl; if(equity > peak) peak = equity; val dd = if(peak > 0) (peak-equity)/peak else 0.0; if(dd > maxDd) maxDd = dd }; finals[i] = equity; dds[i] = maxDd
        }
        finals.sort()
        dds.sort()
        fun pct(a: DoubleArray, p: Double): Double {
            return a[((a.size - 1) * p).toInt().coerceIn(0, a.size - 1)]
        }
        val ruin = finals.count { it < initialBalance * ruinThreshold }.toDouble() / nSimulations
        val profit = finals.count { it > initialBalance }.toDouble() / nSimulations
        return MonteCarloResult(nSimulations,pct(finals,0.5),pct(finals,0.05),pct(finals,0.95),pct(dds,0.5),pct(dds,0.95),ruin,profit)
    }
}



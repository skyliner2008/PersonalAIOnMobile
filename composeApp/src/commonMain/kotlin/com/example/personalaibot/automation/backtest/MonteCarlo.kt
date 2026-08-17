package com.example.personalaibot.automation.backtest

import kotlin.random.Random

/**
 * MonteCarlo — สุ่มสลับลำดับผล trades เพื่อวัดความทนทานของกลยุทธ์
 * (port จาก OLD_Code backtest/monte_carlo.py)
 */
object MonteCarlo {

    data class MonteCarloResult(
        val nSimulations: Int,
        val medianFinalBalance: Double,
        val p5FinalBalance: Double,
        val p95FinalBalance: Double,
        val medianMaxDrawdown: Double,
        val p95MaxDrawdown: Double,
        val probabilityOfRuin: Double,   // P(ทุนหดเหลือ < 50%)
        val probabilityOfProfit: Double  // P(สุดท้ายกำไร)
    )

    fun run(
        tradePnls: List<Double>,
        nSimulations: Int = 1000,
        initialBalance: Double = 10_000.0,
        ruinThreshold: Double = 0.5,
        seed: Long = 42
    ): MonteCarloResult {
        if (tradePnls.size < 5) {
            return MonteCarloResult(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
        val rng = Random(seed)
        val n = tradePnls.size
        val finalBalances = DoubleArray(nSimulations)
        val maxDrawdowns = DoubleArray(nSimulations)

        for (sim in 0 until nSimulations) {
            val shuffled = tradePnls.shuffled(rng)
            var equity = initialBalance
            var peak = equity
            var maxDd = 0.0
            for (pnl in shuffled) {
                equity += pnl
                if (equity > peak) peak = equity
                val dd = if (peak > 0) (peak - equity) / peak else 0.0
                if (dd > maxDd) maxDd = dd
            }
            finalBalances[sim] = equity
            maxDrawdowns[sim] = maxDd
        }

        finalBalances.sort()
        maxDrawdowns.sort()
        fun percentile(sorted: DoubleArray, p: Double): Double =
            sorted[((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)]
        fun median(sorted: DoubleArray) = percentile(sorted, 0.5)

        val ruinLevel = initialBalance * ruinThreshold
        val ruinCount = finalBalances.count { it < ruinLevel }
        val profitCount = finalBalances.count { it > initialBalance }

        return MonteCarloResult(
            nSimulations = nSimulations,
            medianFinalBalance = median(finalBalances),
            p5FinalBalance = percentile(finalBalances, 0.05),
            p95FinalBalance = percentile(finalBalances, 0.95),
            medianMaxDrawdown = median(maxDrawdowns),
            p95MaxDrawdown = percentile(maxDrawdowns, 0.95),
            probabilityOfRuin = ruinCount.toDouble() / nSimulations,
            probabilityOfProfit = profitCount.toDouble() / nSimulations
        )
    }
}

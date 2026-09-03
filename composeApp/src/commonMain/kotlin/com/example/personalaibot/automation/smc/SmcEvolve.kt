package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle

/** P4.3 deterministic walk-forward parameter evolution. Optimizes only on train data. */
data class SmcEvolveCandidate(val aps: SmcApsConfig, val report: SmcBacktestReport, val fitness: Double)
data class SmcEvolveResult(val best: SmcEvolveCandidate, val validation: SmcBacktestReport, val tested: Int)

object SmcEvolve {
    fun evolve(candles: List<Candle>, symbol: String, timeframe: String, trainRatio: Double = 0.70): SmcEvolveResult? {
        if (candles.size < SmcSignals.WARMUP + 20) return null
        val split = (candles.size * trainRatio).toInt().coerceIn(SmcSignals.WARMUP + 1, candles.lastIndex - 1)
        val train = candles.subList(0, split)
        val validation = candles.subList(split - SmcSignals.WARMUP, candles.size)
        val candidates = buildCandidates()
        var best: SmcEvolveCandidate? = null
        for (cfg in candidates) {
            val report = SmcBacktest.run(train, symbol, timeframe, SmcBacktestConfig(aps = cfg))
            val fitness = fitness(report)
            val item = SmcEvolveCandidate(cfg, report, fitness)
            if (best == null || item.fitness > best!!.fitness) best = item
        }
        val chosen = best ?: return null
        return SmcEvolveResult(chosen, SmcBacktest.run(validation, symbol, timeframe, SmcBacktestConfig(aps = chosen.aps)), candidates.size)
    }

    private fun buildCandidates(): List<SmcApsConfig> = buildList {
        for (score in listOf(5, 6, 7, 8)) for (rr in listOf(1.5, 1.8, 2.0)) for (body in listOf(0.35, 0.5, 0.7)) {
            add(SmcApsConfig(minSetupScore = score, minRiskReward = rr, minBodyAtr = body))
        }
    }

    private fun fitness(r: SmcBacktestReport): Double {
        if (r.trades.size < 5) return -1_000.0 + r.trades.size
        return r.netR + r.profitFactor.coerceAtMost(5.0) * 0.5 - r.maxDrawdownR * 0.75 + r.winRate * 0.25
    }
}

package com.example.personalaibot.automation.backtest

object StrategyRanking {
    enum class Verdict { PASS, HOLD }
    data class Evidence(
        val name: String,
        val baseScore: Double,
        val robustness: RobustnessAnalyzer.Report,
        val monteCarlo: MonteCarlo.MonteCarloResult,
        val permutation: PermutationTest.PermutationResult
    )
    data class Ranked(val name: String, val score: Double, val rank: Int, val verdict: Verdict, val riskGateEligible: Boolean)

    fun rank(items: List<Evidence>): List<Ranked> = items
        .map { e ->
            val robustnessScore = when (e.robustness.verdict) {
                RobustnessAnalyzer.Verdict.ROBUST -> 30.0
                RobustnessAnalyzer.Verdict.ACCEPTABLE -> 20.0
                RobustnessAnalyzer.Verdict.FRAGILE -> 5.0
                else -> 0.0
            }
            val significanceScore = if (e.permutation.isSignificant) 25.0 else 0.0
            val monteCarloScore = e.monteCarlo.probabilityOfProfit * 20.0 - e.monteCarlo.p95MaxDrawdown * 25.0
            val score = e.baseScore.coerceIn(0.0, 100.0) * 0.25 + robustnessScore + significanceScore + monteCarloScore
            score to e
        }
        .sortedWith(compareByDescending<Pair<Double, Evidence>> { it.first }.thenBy { it.second.name })
        .mapIndexed { index, (score, e) ->
            val gate = score >= 60.0 &&
                e.robustness.verdict == RobustnessAnalyzer.Verdict.ROBUST &&
                e.permutation.isSignificant &&
                e.monteCarlo.probabilityOfRuin <= 0.05 &&
                e.monteCarlo.p95MaxDrawdown <= 0.30
            Ranked(e.name, score, index + 1, if (gate) Verdict.PASS else Verdict.HOLD, gate)
        }
}


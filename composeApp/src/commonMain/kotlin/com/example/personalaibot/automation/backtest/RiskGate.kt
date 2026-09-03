package com.example.personalaibot.automation.backtest

object RiskGate {
 enum class Decision { PASS, HOLD, BLOCK }
 enum class Reason { LOW_SCORE, NOT_ROBUST, NOT_SIGNIFICANT, HIGH_RUIN, HIGH_DRAWDOWN, STRESS_FRAGILE }
 data class Input(val rankingScore:Double,val robustness:RobustnessAnalyzer.Verdict,val permutationSignificant:Boolean,val monteCarloRuinProbability:Double,val monteCarloP95Drawdown:Double,val stressFragile:Boolean)
 data class Result(val decision:Decision,val reasons:List<Reason>)
 fun evaluate(i: Input): Result {
        val r = mutableListOf<Reason>()
        if (i.rankingScore < 60.0) r += Reason.LOW_SCORE
        if (i.robustness != RobustnessAnalyzer.Verdict.ROBUST) r += Reason.NOT_ROBUST
        if (!i.permutationSignificant) r += Reason.NOT_SIGNIFICANT
        if (i.monteCarloRuinProbability > 0.05) r += Reason.HIGH_RUIN
        if (i.monteCarloP95Drawdown > 0.30) r += Reason.HIGH_DRAWDOWN
        if (i.stressFragile) r += Reason.STRESS_FRAGILE
        val hardBlock = Reason.HIGH_RUIN in r || Reason.HIGH_DRAWDOWN in r
        val decision = when {
            hardBlock -> Decision.BLOCK
            r.isEmpty() -> Decision.PASS
            else -> Decision.HOLD
        }
        return Result(decision, r.distinct())
    }
}

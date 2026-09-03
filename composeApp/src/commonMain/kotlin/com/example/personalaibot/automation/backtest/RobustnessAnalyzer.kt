package com.example.personalaibot.automation.backtest

/** Classifies walk-forward robustness without changing the core WalkForward result model. */
object RobustnessAnalyzer {
    enum class Verdict { ROBUST, ACCEPTABLE, FRAGILE, INSUFFICIENT }
    data class Report(
        val windows: Int,
        val positiveOosWindows: Int,
        val positiveOosRatio: Double,
        val oosProfitFactorMedian: Double,
        val oosToIsSharpeRatio: Double,
        val parameterStabilityCv: Double,
        val verdict: Verdict
    )
    fun analyze(result: WalkForward.WalkForwardResult): Report {
        val windows = result.windows
        if (windows.isEmpty()) return Report(0, 0, 0.0, 0.0, 0.0, result.paramStabilityCv, Verdict.INSUFFICIENT)
        val positive = windows.count { it.oosSharpe > 0.0 && it.oosProfitFactor >= 1.0 }
        val ratio = positive.toDouble() / windows.size
        val pf = windows.map { it.oosProfitFactor }.filter { it.isFinite() }.sorted()
        val medianPf = if (pf.isEmpty()) 0.0 else { val m = pf.size / 2; if (pf.size % 2 == 0) (pf[m - 1] + pf[m]) / 2.0 else pf[m] }
        val sharpeRatio = result.overfittingRatio
        val cv = result.paramStabilityCv
        val verdict = when {
            windows.size < 3 -> Verdict.INSUFFICIENT
            ratio >= 0.70 && medianPf >= 1.15 && sharpeRatio >= 0.60 && cv <= 0.50 -> Verdict.ROBUST
            ratio >= 0.50 && medianPf >= 1.00 && sharpeRatio >= 0.40 && cv <= 0.75 -> Verdict.ACCEPTABLE
            else -> Verdict.FRAGILE
        }
        return Report(windows.size, positive, ratio, medianPf, sharpeRatio, cv, verdict)
    }
}

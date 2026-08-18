package com.example.personalaibot.automation.backtest

/**
 * OverfittingScore — คะแนน overfitting รวม 0–100% (port จาก OLD_Code backtest/overfitting.py)
 * components: walk_forward (40%) / permutation (25%) / param_stability (20%) / monte_carlo (15%)
 * grade: <30 healthy, <60 moderate, else overfit — component ไหนไม่มีให้กระจายน้ำหนักอัตโนมัติ
 */
object OverfittingScore {

    data class OverfitResult(
        val overfittingPct: Double,
        val grade: String,                 // healthy | moderate | overfit | unknown
        val components: Map<String, Double>,
        val weightsUsed: Map<String, Double>,
        val skipped: List<String>
    )

    private val DEFAULT_WEIGHTS = mapOf(
        "walk_forward" to 0.40,
        "permutation" to 0.25,
        "param_stability" to 0.20,
        "monte_carlo" to 0.15
    )

    private fun clamp(v: Double, lo: Double = 0.0, hi: Double = 100.0) = v.coerceIn(lo, hi)

    fun compute(
        wf: WalkForward.WalkForwardResult?,
        perm: PermutationTest.PermutationResult?,
        mc: MonteCarlo.MonteCarloResult?
    ): OverfitResult {
        val components = mutableMapOf<String, Double>()
        val skipped = mutableListOf<String>()

        // Walk-forward: ratio 1.0 = ไม่ overfit → score = (1 - ratio) × 100
        // IS Sharpe ≤ 0 = ไม่มี edge ตั้งแต่ in-sample — เดิม "ข้าม" ทำกลยุทธ์ขาดทุนได้เกรด healthy
        // แล้วผ่าน gate auto-apply ได้ (เช่น REV IS=-0.13 ได้ overfit 16% healthy — ผิด) → ให้คะแนนแย่สุดแทน
        if (wf != null && wf.nSplits > 0) {
            components["walk_forward"] =
                if (wf.inSampleAvgSharpe > 0) clamp((1.0 - wf.overfittingRatio) * 100) else 100.0
            components["param_stability"] = clamp(wf.paramStabilityCv * 100)
        } else {
            skipped += "walk_forward"; skipped += "param_stability"
        }

        // Permutation: p สูง = ไม่ต่างจากสุ่ม = overfit
        if (perm != null && perm.nPermutations > 0) {
            components["permutation"] = clamp(perm.pValue * 200)
        } else skipped += "permutation"

        // Monte Carlo: โอกาสพอร์ตพังสูง = เสี่ยง/overfit
        if (mc != null && mc.nSimulations > 0) {
            components["monte_carlo"] = clamp(mc.probabilityOfRuin * 200)
        } else skipped += "monte_carlo"

        if (components.isEmpty()) {
            return OverfitResult(0.0, "unknown", emptyMap(), emptyMap(), skipped)
        }

        // กระจายน้ำหนักตาม component ที่มีจริง
        val totalW = components.keys.sumOf { DEFAULT_WEIGHTS[it] ?: 0.0 }
        val weights = components.keys.associateWith { (DEFAULT_WEIGHTS[it] ?: 0.0) / totalW }
        val composite = clamp(components.entries.sumOf { (k, v) -> v * (weights[k] ?: 0.0) })

        val grade = when {
            composite < 30.0 -> "healthy"
            composite < 60.0 -> "moderate"
            else -> "overfit"
        }
        return OverfitResult(composite, grade, components, weights, skipped)
    }
}

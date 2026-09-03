package com.example.personalaibot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P6.4.1 deterministic promotion fixture.
 *
 * This harness does not ask Evolution to discover a winner and does not
 * modify production tuning. It verifies the contract used by promotion:
 * full + holdout improvement, non-overfit grade, evolve source, and the
 * risk-gate eligibility boundary consumed by signal execution.
 */
class P64PromotionFixtureHarnessTest {
    private data class Validation(
        val profitFactor: Double,
        val expectancyR: Double,
        val maxDrawdownPct: Double,
        val trades: Int,
    )

    private data class Candidate(
        val slMult: Double,
        val tpMult: Double,
        val grade: String,
        val source: String,
        val full: Validation,
        val holdout: Validation,
    )

    private data class PromotionRecord(
        val source: String,
        val grade: String,
        val riskGateEligible: Boolean,
        val slMult: Double,
        val tpMult: Double,
    )

    private fun promote(baseline: Validation, candidate: Candidate): PromotionRecord {
        val fullBetter = candidate.full.profitFactor > baseline.profitFactor &&
            candidate.full.expectancyR > baseline.expectancyR
        val holdoutPass = candidate.holdout.profitFactor > baseline.profitFactor &&
            candidate.holdout.expectancyR > baseline.expectancyR &&
            candidate.holdout.maxDrawdownPct <= baseline.maxDrawdownPct
        val eligible = fullBetter && holdoutPass &&
            candidate.grade != "overfit" && candidate.source == "evolve"
        return PromotionRecord(candidate.source, candidate.grade, eligible, candidate.slMult, candidate.tpMult)
    }

    private fun validCandidate() = Candidate(
        slMult = 0.5,
        tpMult = 2.2,
        grade = "healthy",
        source = "evolve",
        full = Validation(1.47, 0.29, 18.0, 120),
        holdout = Validation(1.31, 0.21, 17.0, 48),
    )

    private fun baseline() = Validation(1.20, 0.10, 20.0, 120)

    @Test
    fun valid_fixture_promotes_and_sets_risk_gate_eligibility() {
        val record = promote(baseline(), validCandidate())
        assertTrue(record.riskGateEligible)
        assertEquals("evolve", record.source)
        assertEquals("healthy", record.grade)
        assertEquals(0.5, record.slMult)
        assertEquals(2.2, record.tpMult)
    }

    @Test
    fun worse_candidate_is_not_promoted() {
        val candidate = validCandidate().copy(
            full = Validation(1.05, 0.02, 25.0, 120),
            holdout = Validation(0.98, -0.01, 27.0, 48),
        )
        assertFalse(promote(baseline(), candidate).riskGateEligible)
    }

    @Test
    fun overfit_candidate_is_not_promoted_even_when_metrics_improve() {
        val candidate = validCandidate().copy(grade = "overfit")
        assertFalse(promote(baseline(), candidate).riskGateEligible)
    }

    @Test
    fun non_evolve_source_cannot_enter_production_promotion() {
        val candidate = validCandidate().copy(source = "optimize")
        assertFalse(promote(baseline(), candidate).riskGateEligible)
    }

    @Test
    fun holdout_drawdown_regression_blocks_promotion() {
        val candidate = validCandidate().copy(
            holdout = Validation(1.35, 0.22, 21.0, 48),
        )
        assertFalse(promote(baseline(), candidate).riskGateEligible)
    }

    @Test
    fun promoted_record_is_signal_eligible_only_when_all_boundary_fields_match() {
        val record = promote(baseline(), validCandidate())
        val signalEligible = record.riskGateEligible &&
            record.source == "evolve" &&
            record.grade != "overfit"
        assertTrue(signalEligible)
    }
}

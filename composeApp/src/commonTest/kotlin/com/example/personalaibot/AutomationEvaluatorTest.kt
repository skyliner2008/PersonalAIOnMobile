package com.example.personalaibot

import com.example.personalaibot.automation.AutomationCondition
import com.example.personalaibot.automation.AutomationEvaluator
import com.example.personalaibot.automation.ConditionOperator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomationEvaluatorTest {
    private val evaluator = AutomationEvaluator()

    @Test
    fun evaluate_numericalComparisons_handlesStandardNumbers() {
        val data = mapOf("price" to "4150.50", "rsi" to "68.2")

        assertTrue(evaluator.evaluate(data, AutomationCondition("price", ConditionOperator.GT, "4100")))
        assertFalse(evaluator.evaluate(data, AutomationCondition("price", ConditionOperator.LT, "4100")))
        assertTrue(evaluator.evaluate(data, AutomationCondition("price", ConditionOperator.GTE, "4150.50")))
        assertTrue(evaluator.evaluate(data, AutomationCondition("price", ConditionOperator.LTE, "4150.50")))
        assertTrue(evaluator.evaluate(data, AutomationCondition("rsi", ConditionOperator.GT, "50")))
        assertFalse(evaluator.evaluate(data, AutomationCondition("rsi", ConditionOperator.LTE, "60")))
    }

    @Test
    fun evaluate_formatsWithCommasAndPercentages_stripsFormattingCorrectly() {
        val data = mapOf(
            "formatted_price" to "4,150.50",
            "percent_change" to "+2.5%",
            "volume" to "1,000,000"
        )

        assertTrue(evaluator.evaluate(data, AutomationCondition("formatted_price", ConditionOperator.GT, "4,000.00")))
        assertTrue(evaluator.evaluate(data, AutomationCondition("percent_change", ConditionOperator.GTE, "2.0%")))
        assertTrue(evaluator.evaluate(data, AutomationCondition("volume", ConditionOperator.GT, "500000")))
    }

    @Test
    fun evaluate_stringAndEqualityMatching() {
        val data = mapOf(
            "status" to "ACTIVE",
            "headline" to "Fed signals interest rate cut in September"
        )

        assertTrue(evaluator.evaluate(data, AutomationCondition("status", ConditionOperator.EQ, "active")))
        assertTrue(evaluator.evaluate(data, AutomationCondition("headline", ConditionOperator.CONTAINS, "rate cut")))
        assertFalse(evaluator.evaluate(data, AutomationCondition("headline", ConditionOperator.CONTAINS, "rate hike")))
    }

    @Test
    fun evaluate_missingFieldOrInvalidNumber_returnsFalseSafely() {
        val data = mapOf("price" to "not_a_number")

        assertFalse(evaluator.evaluate(data, AutomationCondition("non_existent", ConditionOperator.GT, "100")))
        assertFalse(evaluator.evaluate(data, AutomationCondition("price", ConditionOperator.GT, "100")))
    }
}

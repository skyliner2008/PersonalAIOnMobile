package com.example.personalaibot.automation

import com.example.personalaibot.logDebug

class AutomationEvaluator {

    /**
     * ตรวจสอบว่าข้อมูลดิบ (Data Map) จาก API ตรงกับเงื่อนไขที่ตั้งไว้หรือไม่
     */
    fun evaluate(data: Map<String, String>, condition: AutomationCondition): Boolean {
        val rawValue = data[condition.field] ?: return false
        
        // Clean raw value for numerical comparison (remove commas, percentage signs, spaces)
        val cleanValue = rawValue.replace(",", "").replace("%", "").trim()
        val targetValue = condition.value.replace(",", "").replace("%", "").trim()

        return try {
            when (condition.operator) {
                ConditionOperator.GT -> cleanValue.toDouble() > targetValue.toDouble()
                ConditionOperator.LT -> cleanValue.toDouble() < targetValue.toDouble()
                ConditionOperator.GTE -> cleanValue.toDouble() >= targetValue.toDouble()
                ConditionOperator.LTE -> cleanValue.toDouble() <= targetValue.toDouble()
                ConditionOperator.EQ -> {
                    val numValue = cleanValue.toDoubleOrNull()
                    val targetNum = targetValue.toDoubleOrNull()
                    if (numValue != null && targetNum != null) {
                        numValue == targetNum
                    } else {
                        cleanValue.equals(targetValue, ignoreCase = true)
                    }
                }
                ConditionOperator.CONTAINS -> rawValue.contains(condition.value, ignoreCase = true)
            }
        } catch (e: Exception) {
            logDebug("AutomationEvaluator", "❌ Error evaluating [${condition.field}] '$rawValue' vs '$targetValue': ${e.message}")
            false
        }
    }
}

package com.example.personalaibot.automation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ConditionOperator {
    GT,  // Greater Than (>)
    LT,  // Less Than (<)
    GTE, // Greater Than or Equal (>=)
    LTE, // Less Than or Equal (<=)
    EQ,  // Equal (==)
    CONTAINS // For string matching (News, Sentiment)
}

@Serializable
data class AutomationCondition(
    val field: String,      // e.g. "price", "RSI", "sentiment_score"
    val operator: ConditionOperator,
    val value: String       // String representation of the threshold
)

val automationJson = Json { 
    ignoreUnknownKeys = true
    isLenient = true
}

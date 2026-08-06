package com.example.personalaibot.tools.strategy

import com.example.personalaibot.tools.FunctionDeclaration
import com.example.personalaibot.tools.FunctionParameters
import com.example.personalaibot.tools.ParameterProperty

/**
 * StrategyToolDefinitions — เครื่องมือเข้าถึงคลังกลยุทธ์ Quantpedia (60 แบบ)
 * ให้ AI ค้นหา/อธิบาย/เปรียบเทียบกลยุทธ์เชิงวิชาการได้จากคลังภายในแอป (offline)
 */
object StrategyToolDefinitions {

    val allDefinitions: List<FunctionDeclaration> = listOf(
        FunctionDeclaration(
            name = "strategy_list",
            description = """Lists trading strategies from the built-in Quantpedia strategy library (60 academic strategies with reference implementations).
                |Use without arguments to see all categories with counts, or pass a category to list strategies in it.
                |Categories: Momentum & Trend, Reversal, Value & Fundamental, Carry & FX, Calendar & Seasonality, Volatility & Risk, Pairs & Arbitrage, Asset Allocation & Macro, Crypto.""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "category" to ParameterProperty("STRING", "Optional category name to filter (e.g., 'Momentum & Trend', 'Crypto'). Leave empty to see category overview.")
                ),
                required = emptyList()
            )
        ),
        FunctionDeclaration(
            name = "strategy_search",
            description = """Searches the strategy library by keyword (name, category, or description).
                |Use when the user asks about a trading concept, anomaly, or factor — e.g., 'momentum', 'carry trade', 'earnings', 'seasonality', 'reversal', 'bitcoin'.""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "query" to ParameterProperty("STRING", "Search keywords, e.g., 'momentum stocks', 'carry trade', 'earnings announcement'")
                ),
                required = listOf("query")
            )
        ),
        FunctionDeclaration(
            name = "strategy_explain",
            description = """Gets the full details of one strategy: academic description, trading logic, and the complete QuantConnect (Lean) Python reference implementation.
                |Use to explain how a strategy works, adapt its logic to current market analysis, or compare strategies.
                |NOTE: the code is a reference for reasoning/adaptation — it is NOT executed on the device.""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "name" to ParameterProperty("STRING", "Strategy slug or title (e.g., 'consistent-momentum-strategy', 'fx-carry-trade', 'Pairs Trading With Country Etfs')")
                ),
                required = listOf("name")
            )
        )
    )
}

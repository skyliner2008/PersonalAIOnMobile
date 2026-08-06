package com.example.personalaibot.tools.strategy

/**
 * StrategyToolExecutor — route การเรียก strategy tools ไปยัง StrategyLibrary
 * (pure commonMain, อ่านจาก composeResources — ไม่ต้อง init ไม่มี side effect)
 */
object StrategyToolExecutor {

    suspend fun execute(toolName: String, args: Map<String, String>): String = when (toolName) {
        "strategy_list" -> {
            val category = args["category"]?.takeIf { it.isNotBlank() }
            if (category == null && args.isEmpty()) StrategyLibrary.listCategories()
            else StrategyLibrary.listStrategies(category)
        }
        "strategy_search" -> StrategyLibrary.search(args["query"] ?: "")
        "strategy_explain" -> StrategyLibrary.getStrategyDetail(args["name"] ?: "")
        else -> "❌ ไม่รู้จัก strategy tool: $toolName"
    }
}

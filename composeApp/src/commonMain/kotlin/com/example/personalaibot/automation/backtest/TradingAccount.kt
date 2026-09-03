package com.example.personalaibot.automation.backtest

/** Unified account model consumed by RiskEngine regardless of execution environment. */
data class TradingAccount(
    val id: String,
    val name: String,
    val environment: Environment,
    val equity: Double,
    val balance: Double,
    val freeMargin: Double,
    val todayPnL: Double,
    val openPositions: Int,
    val currentExposurePct: Double = 0.0,
    val correlatedExposurePct: Double = 0.0,
) {
    enum class Environment { MT5_LIVE, DEMO }

    val drawdownPct: Double
        get() = if (balance > 0.0) ((balance - equity) / balance * 100.0).coerceAtLeast(0.0) else 100.0
}


/** Convert the unified provider model into the deterministic P6 risk model. */
fun TradingAccount.toRiskAccount(): RiskEngine.Account = RiskEngine.Account(
    equity = equity,
    balance = balance,
    freeMargin = freeMargin,
    todayPnL = todayPnL,
    openPositions = openPositions,
    currentExposurePct = currentExposurePct,
    correlatedExposurePct = correlatedExposurePct,
)

interface TradingAccountProvider {
    suspend fun getAccount(): TradingAccount?
}

interface TradingExecutionProvider {
    suspend fun openMarket(symbol: String, buy: Boolean, volume: Double, entry: Double, stopLoss: Double?, takeProfit: Double?): String
    suspend fun closePosition(positionId: String): String
}

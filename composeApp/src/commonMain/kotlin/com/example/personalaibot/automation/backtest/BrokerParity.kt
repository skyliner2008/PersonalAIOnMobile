package com.example.personalaibot.automation.backtest

/** Deterministic parity checks shared by Demo, Backtest and MT5 account snapshots. */
data class BrokerParityResult(
    val passed: Boolean,
    val mismatches: List<String> = emptyList(),
)

object BrokerParity {
    fun compareAccounts(expected: TradingAccount, actual: TradingAccount, tolerance: Double = 1e-6): BrokerParityResult {
        val mismatches = mutableListOf<String>()
        fun check(name: String, a: Double, b: Double) {
            if (kotlin.math.abs(a - b) > tolerance) mismatches += "$name expected=$a actual=$b"
        }
        check("balance", expected.balance, actual.balance)
        check("equity", expected.equity, actual.equity)
        check("freeMargin", expected.freeMargin, actual.freeMargin)
        check("todayPnL", expected.todayPnL, actual.todayPnL)
        if (expected.openPositions != actual.openPositions) mismatches += "openPositions expected=${expected.openPositions} actual=${actual.openPositions}"
        check("currentExposurePct", expected.currentExposurePct, actual.currentExposurePct)
        check("correlatedExposurePct", expected.correlatedExposurePct, actual.correlatedExposurePct)
        return BrokerParityResult(mismatches.isEmpty(), mismatches)
    }

    fun comparePnl(demo: Double, brokerValuation: Double, tolerance: Double = 1e-6): BrokerParityResult =
        if (kotlin.math.abs(demo - brokerValuation) <= tolerance) BrokerParityResult(true)
        else BrokerParityResult(false, listOf("pnl expected=$brokerValuation actual=$demo"))
}

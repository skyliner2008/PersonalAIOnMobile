package com.example.personalaibot.automation.backtest

import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * P6 Risk Engine.
 *
 * Runtime risk boundary between strategy approval and execution. This is deliberately
 * pure/deterministic: it consumes an account/portfolio snapshot and a proposed trade,
 * then returns a sizing decision. It does not place orders and never enables live trading.
 */
object RiskEngine {
    enum class Decision { PASS, HOLD, BLOCK }

    enum class Reason {
        DAILY_LOSS_LIMIT,
        DRAWDOWN_LIMIT,
        MAX_POSITIONS,
        FREE_MARGIN,
        TOTAL_EXPOSURE,
        CORRELATION_EXPOSURE,
        INVALID_STOP,
        RISK_BUDGET,
        LOT_LIMIT,
        STRATEGY_GATE_NOT_PASS
    }

    data class Policy(
        val riskPerTradePct: Double = 1.0,
        val maxTotalExposurePct: Double = 50.0,
        val maxOpenPositions: Int = 5,
        val maxDailyLossPct: Double = 5.0,
        val maxDrawdownPct: Double = 10.0,
        val minFreeMarginPct: Double = 10.0,
        val correlationCap: Int = 80,
        val minLotStep: Double = 0.01,
        val minLot: Double = 0.01,
        val maxLot: Double = 100.0
    )

    /** Build an account risk view directly from the latest MT5 terminal snapshot. */
    fun accountFromMt5Snapshot(
        snapshot: com.example.personalaibot.tools.trading.Mt5TerminalSnapshot,
        nowMs: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
        targetSymbol: String? = null,
    ): Account {
        val account = snapshot.account
        val balance = account?.balance ?: 0.0
        val equity = account?.equity ?: 0.0
        val freeMargin = account?.freeMargin ?: 0.0
        val positions = snapshot.positions
        val openCount = positions.size
        val currentExposurePct = if (equity > 0.0) {
            ((account?.margin ?: 0.0) / equity * 100.0).coerceAtLeast(0.0)
        } else 100.0

        // Without a dedicated broker-side Pearson matrix, use a conservative
        // concentration proxy: positions in the requested symbol are fully
        // correlated; closely related FX/metals/crypto symbols contribute 75%.
        // A future correlation adapter can replace this scalar without changing
        // the RiskEngine contract.
        val correlatedExposurePct = if (equity > 0.0 && targetSymbol != null) {
            val target = targetSymbol.trim().uppercase()
            val clusterExposure = positions.sumOf { p ->
                val weight = correlationProxy(target, p.symbol)
                if (weight <= 0.0) 0.0 else positionMarginProxy(p) * weight
            }
            clusterExposure / equity * 100.0
        } else {
            0.0
        }

        val todayStart = startOfLocalDay(nowMs)
        val todayPnL = snapshot.deals
            .filter { it.isExitDeal && normalizeEpochMs(it.eventTime) >= todayStart }
            .sumOf { it.netPnl }

        return Account(
            equity = equity,
            balance = balance,
            freeMargin = freeMargin,
            todayPnL = todayPnL,
            openPositions = openCount,
            currentExposurePct = currentExposurePct,
            correlatedExposurePct = correlatedExposurePct,
        )
    }

    private fun positionMarginProxy(position: com.example.personalaibot.tools.trading.Mt5TradeItem): Double {
        // MT5 position payloads may expose margin under a backend-specific key.
        // The mobile parser intentionally keeps the raw payload, so use it when
        // available; otherwise fall back to volume*current-price as a relative
        // exposure proxy. This is used only for correlation concentration.
        val raw = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(position.payloadJson)
                .jsonObject["margin"]?.jsonPrimitive?.doubleOrNull
        }.getOrNull()
        return raw?.coerceAtLeast(0.0)
            ?: (position.volume * position.priceCurrent).coerceAtLeast(0.0)
    }

    private fun correlationProxy(target: String, symbol: String): Double {
        val a = target.uppercase().replace("/", "")
        val b = symbol.uppercase().replace("/", "")
        if (a == b) return 1.0
        val metals = listOf("XAU", "XAG")
        val crypto = listOf("BTC", "ETH", "SOL")
        val sameMetal = metals.any { a.contains(it) } && metals.any { b.contains(it) }
        val sameCrypto = crypto.any { a.contains(it) } && crypto.any { b.contains(it) }
        val sharedCurrency = listOf("USD", "EUR", "GBP", "JPY", "CHF", "AUD", "CAD", "NZD")
            .count { a.contains(it) && b.contains(it) } > 0
        return when {
            sameMetal || sameCrypto -> 0.75
            sharedCurrency -> 0.50
            else -> 0.0
        }
    }

    private fun normalizeEpochMs(value: Long): Long = when {
        value <= 0L -> 0L
        value < 100_000_000_000L -> value * 1000L
        else -> value
    }

    private fun startOfLocalDay(nowMs: Long): Long {
        // Deterministic UTC day boundary for normalized epoch timestamps.
        val dayMs = 86_400_000L
        return nowMs - (nowMs % dayMs)
    }

    data class Account(
        val equity: Double,
        val balance: Double,
        val freeMargin: Double,
        val todayPnL: Double,
        val openPositions: Int,
        val currentExposurePct: Double = 0.0,
        val correlatedExposurePct: Double = 0.0
    )

    data class Proposal(
        val strategyGate: RiskGate.Decision,
        val entry: Double,
        val stopLoss: Double,
        val valuePerPriceUnit: Double,
        val requestedRiskPct: Double? = null,
        val requestedVolume: Double? = null,
        val maxCorrelationPct: Double = 0.0,
        /** Round-trip transaction cost per lot, expressed in account currency. */
        val transactionCostPerLot: Double = 0.0
    )

    data class Result(
        val decision: Decision,
        val reasons: List<Reason> = emptyList(),
        val suggestedVolume: Double? = null,
        val riskPct: Double? = null,
        val projectedExposurePct: Double? = null,
        val projectedCorrelatedExposurePct: Double? = null,
        val executionAllowed: Boolean = decision == Decision.PASS
    )

    fun evaluate(account: Account, proposal: Proposal, policy: Policy = Policy()): Result {
        val reasons = mutableListOf<Reason>()

        if (proposal.strategyGate != RiskGate.Decision.PASS) reasons += Reason.STRATEGY_GATE_NOT_PASS
        if (account.equity <= 0.0 || account.balance <= 0.0) reasons += Reason.RISK_BUDGET

        val dailyLossPct = if (account.balance > 0.0) {
            (-account.todayPnL / account.balance * 100.0).coerceAtLeast(0.0)
        } else 100.0
        if (dailyLossPct >= policy.maxDailyLossPct) reasons += Reason.DAILY_LOSS_LIMIT

        val drawdownPct = if (account.balance > 0.0) {
            ((account.balance - account.equity) / account.balance * 100.0).coerceAtLeast(0.0)
        } else 100.0
        if (drawdownPct >= policy.maxDrawdownPct) reasons += Reason.DRAWDOWN_LIMIT
        if (account.openPositions >= policy.maxOpenPositions) reasons += Reason.MAX_POSITIONS

        val freeMarginPct = if (account.equity > 0.0) account.freeMargin / account.equity * 100.0 else 0.0
        if (freeMarginPct < policy.minFreeMarginPct) reasons += Reason.FREE_MARGIN

        val stopDistance = kotlin.math.abs(proposal.entry - proposal.stopLoss)
        if (stopDistance <= 0.0 || proposal.valuePerPriceUnit <= 0.0) {
            reasons += Reason.INVALID_STOP
            return Result(Decision.BLOCK, reasons)
        }

        val requestedRiskPct = (proposal.requestedRiskPct ?: policy.riskPerTradePct)
            .coerceIn(0.0, policy.riskPerTradePct)
        val riskAmount = account.equity * requestedRiskPct / 100.0
        val transactionCostPerLot = proposal.transactionCostPerLot.coerceAtLeast(0.0)
        val riskPerLot = stopDistance * proposal.valuePerPriceUnit + transactionCostPerLot
        val rawVolume = proposal.requestedVolume
            ?: if (riskAmount > 0.0 && riskPerLot > 0.0) riskAmount / riskPerLot else 0.0
        val volume = floorToStep(rawVolume, policy.minLotStep)

        if (volume < policy.minLot) reasons += Reason.RISK_BUDGET
        if (volume > policy.maxLot) reasons += Reason.LOT_LIMIT

        val actualRiskAmount = volume * riskPerLot
        val actualRiskPct = if (account.equity > 0.0) actualRiskAmount / account.equity * 100.0 else 100.0
        if (actualRiskPct > policy.riskPerTradePct + 1e-9) reasons += Reason.RISK_BUDGET

        val projectedExposure = account.currentExposurePct + actualRiskAmount / account.equity * 100.0
        if (projectedExposure > policy.maxTotalExposurePct + 1e-9) reasons += Reason.TOTAL_EXPOSURE

        val correlation = proposal.maxCorrelationPct.coerceIn(0.0, 100.0) / 100.0
        val projectedCorrelated = account.correlatedExposurePct + (actualRiskAmount / account.equity * 100.0) * correlation
        val correlationBudget = policy.maxTotalExposurePct * policy.correlationCap.coerceIn(0, 100) / 100.0
        if (projectedCorrelated > correlationBudget + 1e-9) reasons += Reason.CORRELATION_EXPOSURE

        val decision = when {
            reasons.any { it in setOf(
                Reason.DAILY_LOSS_LIMIT,
                Reason.DRAWDOWN_LIMIT,
                Reason.MAX_POSITIONS,
                Reason.FREE_MARGIN,
                Reason.TOTAL_EXPOSURE,
                Reason.CORRELATION_EXPOSURE,
                Reason.INVALID_STOP,
                Reason.RISK_BUDGET,
                Reason.LOT_LIMIT,
                Reason.STRATEGY_GATE_NOT_PASS
            ) } -> Decision.BLOCK
            else -> Decision.PASS
        }

        return Result(
            decision = decision,
            reasons = reasons,
            suggestedVolume = volume.takeIf { it >= policy.minLot },
            riskPct = actualRiskPct.takeIf { it > 0.0 },
            projectedExposurePct = projectedExposure,
            projectedCorrelatedExposurePct = projectedCorrelated
        )
    }

    private fun floorToStep(value: Double, step: Double): Double {
        if (value <= 0.0 || step <= 0.0) return 0.0
        return kotlin.math.floor(value / step + 1e-9) * step
    }
}

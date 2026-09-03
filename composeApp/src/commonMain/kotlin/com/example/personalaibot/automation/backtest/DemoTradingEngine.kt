package com.example.personalaibot.automation.backtest

import kotlinx.datetime.Clock

/** P7.8: explicit DEMO-only execution boundary. LIVE/remote execution is intentionally not supported here. */
object DemoSignalExecution {
    data class Request(
        val symbol: String,
        val buy: Boolean,
        val volume: Double,
        val entry: Double,
        val stopLoss: Double?,
        val takeProfit: Double?,
        val signalReady: Boolean,
        val riskGateEligible: Boolean,
        val promotionSource: String,
    )

    data class Result(val executed: Boolean, val positionId: String?, val reason: String)

    suspend fun execute(engine: DemoTradingEngine, request: Request): Result {
        if (!request.signalReady) return Result(false, null, "SIGNAL_NOT_READY")
        if (!request.riskGateEligible) return Result(false, null, "RISK_GATE_NOT_ELIGIBLE")
        if (request.promotionSource != "P7.6_STRATEGY_RANKING") {
            return Result(false, null, "INVALID_PROMOTION_SOURCE")
        }
        val id = engine.openMarket(
            request.symbol, request.buy, request.volume, request.entry,
            request.stopLoss, request.takeProfit
        )
        return Result(true, id, "DEMO_EXECUTED")
    }
}

/**
 * Mobile-only paper broker. It is intentionally deterministic and never calls MT5.
 * Prices are supplied by the caller, so the same engine can be driven by live ticks,
 * historical bars, or a backtest replay.
 */
class DemoTradingEngine(
    private val config: DemoAccountConfig = DemoAccountConfig(
        id = "demo-default",
        name = "Default Demo",
    ),
    initialBalance: Double = config.initialBalance,
    private val accountId: String = config.id,
    private val accountName: String = config.name,
) : TradingAccountProvider, TradingExecutionProvider {
    data class Position(
        val id: String,
        val symbol: String,
        val buy: Boolean,
        val volume: Double,
        val entry: Double,
        val stopLoss: Double? = null,
        val takeProfit: Double? = null,
        val currentPrice: Double = entry,
        val openedAt: Long = Clock.System.now().toEpochMilliseconds(),
    )

    private var balance = initialBalance.coerceAtLeast(0.0)
    private val costProfile = config.costProfile
    private val positions = linkedMapOf<String, Position>()
    private val deals = mutableListOf<CloseResult>()
    private var sequence = 0L
    private var todayPnL = 0.0
    private var currentExposurePct = 0.0
    private var usedMargin = 0.0
    private var killSwitchActive = false
    private val market = DemoMarketPriceModel(costProfile.spreadPrice)

    private fun symbolSpec(symbol: String): DemoSymbolSpec =
        DemoSymbolRegistry.get(symbol) ?: DemoSymbolSpec(
            symbol = symbol.uppercase(), digits = 5, point = costProfile.tickSize,
            tickSize = costProfile.tickSize, tickValuePerLot = costProfile.tickValuePerLot,
            contractSize = costProfile.contractSize, volumeMin = 0.01, volumeMax = 100.0,
            volumeStep = 0.01, defaultSpread = costProfile.spreadPrice,
            commissionPerLotRoundTurn = costProfile.commissionPerLotRoundTurn,
            commissionPctPerSide = costProfile.commissionPctPerSide,
            rebatePerLotRoundTurn = costProfile.rebatePerLotRoundTurn,
            swapLongPerLotPerDay = costProfile.swapLongPerLotPerDay,
            swapShortPerLotPerDay = costProfile.swapShortPerLotPerDay,
            leverage = config.leverage,
        )

    data class ExecutionResult(
        val positionId: String,
        val fillPrice: Double,
        val spreadCost: Double,
        val commission: Double,
        val rebate: Double,
        val marginRequired: Double,
    )

    data class CloseResult(
        val positionId: String,
        val grossPnl: Double,
        val spreadCost: Double,
        val commission: Double,
        val rebate: Double,
        val swap: Double,
        val netPnl: Double,
    )

    fun accountSnapshot(): TradingAccount {
        val equity = balance + positions.values.sumOf { floatingPnl(it) }
        updateProtection(equity)
        val freeMargin = (equity - usedMargin).coerceAtLeast(0.0)
        return TradingAccount(
            id = accountId,
            name = accountName,
            environment = TradingAccount.Environment.DEMO,
            equity = equity,
            balance = balance,
            freeMargin = freeMargin,
            todayPnL = todayPnL,
            openPositions = positions.size,
            currentExposurePct = currentExposurePct,
            correlatedExposurePct = 0.0,
        )
    }

    fun openMarketSync(symbol: String, buy: Boolean, volume: Double, entry: Double, stopLoss: Double?, takeProfit: Double?): String {
        require(symbol.isNotBlank()) { "symbol is required" }
        require(volume > 0.0) { "volume must be > 0" }
        require(entry > 0.0) { "entry must be > 0" }
        require(!killSwitchActive) { "demo kill switch active" }
        if (config.requireStopLoss) require(stopLoss != null) { "stop loss is required by account protection" }
        val risk = evaluateRisk(symbol, buy, entry, stopLoss ?: entry, requestedVolume = volume)
        require(risk.executionAllowed) { "risk gate blocked demo order: ${risk.reasons.joinToString()}" }
        val spec = symbolSpec(symbol)
        require(spec.isValidVolume(volume)) { "invalid volume for ${spec.symbol}: $volume" }
        val spread = market.quote(symbol)?.spread ?: spec.defaultSpread
        val id = "demo-${++sequence}"
        val effectiveEntry = if (buy) entry + spread / 2.0 else entry - spread / 2.0
        positions[id] = Position(id, symbol.uppercase(), buy, volume, effectiveEntry, stopLoss, takeProfit)
        return id
    }

    override suspend fun getAccount(): TradingAccount {
        val equity = balance + positions.values.sumOf { floatingPnl(it) }
        updateProtection(equity)
        val freeMargin = (equity - usedMargin).coerceAtLeast(0.0)
        return TradingAccount(
            id = accountId,
            name = accountName,
            environment = TradingAccount.Environment.DEMO,
            equity = equity,
            balance = balance,
            freeMargin = freeMargin,
            todayPnL = todayPnL,
            openPositions = positions.size,
            currentExposurePct = currentExposurePct,
            correlatedExposurePct = 0.0,
        )
    }


    /** P6.2f: evaluate a Demo order through the same risk boundary used by MT5. */
    fun evaluateRisk(
        symbol: String,
        buy: Boolean,
        entry: Double,
        stopLoss: Double,
        requestedVolume: Double? = null,
        requestedRiskPct: Double? = null,
        maxCorrelationPct: Double = 0.0,
        policy: RiskEngine.Policy = RiskEngine.Policy(
            maxDailyLossPct = config.maxDailyLossPct,
            maxDrawdownPct = config.maxDrawdownPct,
        ),
    ): RiskEngine.Result {
        val spec = symbolSpec(symbol)
        val roundTurnCostPerLot = DemoTradingCostCalculator.estimateRoundTurn(
            config = config.copy(
                leverage = spec.leverage,
                costProfile = config.costProfile.copy(
                    spreadPrice = spec.defaultSpread,
                    commissionPerLotRoundTurn = spec.commissionPerLotRoundTurn,
                    commissionPctPerSide = spec.commissionPctPerSide,
                    rebatePerLotRoundTurn = spec.rebatePerLotRoundTurn,
                    contractSize = spec.contractSize,
                    tickSize = spec.tickSize,
                    tickValuePerLot = spec.tickValuePerLot,
                ),
            ),
            volumeLots = 1.0,
            entry = entry,
            exit = entry,
            buy = buy,
        ).totalNetCost
        return RiskEngine.evaluate(
            account = accountSnapshot().toRiskAccount(),
            proposal = RiskEngine.Proposal(
                strategyGate = RiskGate.Decision.PASS,
                entry = entry,
                stopLoss = stopLoss,
                valuePerPriceUnit = spec.tickValuePerLot / spec.tickSize,
                requestedRiskPct = requestedRiskPct,
                requestedVolume = requestedVolume,
                maxCorrelationPct = maxCorrelationPct,
                transactionCostPerLot = roundTurnCostPerLot.coerceAtLeast(0.0),
            ),
            policy = policy.copy(
                minLotStep = spec.volumeStep,
                minLot = spec.volumeMin,
                maxLot = spec.volumeMax,
            ),
        )
    }

    override suspend fun openMarket(symbol: String, buy: Boolean, volume: Double, entry: Double, stopLoss: Double?, takeProfit: Double?): String =
        executeOpen(symbol, buy, volume, entry, stopLoss, takeProfit).positionId

    suspend fun executeOpen(symbol: String, buy: Boolean, volume: Double, entry: Double, stopLoss: Double?, takeProfit: Double?): ExecutionResult {
        require(symbol.isNotBlank()) { "symbol is required" }
        require(volume > 0.0) { "volume must be > 0" }
        require(entry > 0.0) { "entry must be > 0" }
        require(!killSwitchActive) { "demo kill switch active" }
        if (config.requireStopLoss) require(stopLoss != null) { "stop loss is required by account protection" }
        val risk = evaluateRisk(symbol, buy, entry, stopLoss ?: entry, requestedVolume = volume)
        require(risk.executionAllowed) { "risk gate blocked demo order: ${risk.reasons.joinToString()}" }
        val spec = symbolSpec(symbol)
        require(spec.isValidVolume(volume)) { "invalid volume for ${spec.symbol}: $volume" }
        val costConfig = config.copy(
            leverage = spec.leverage,
            costProfile = config.costProfile.copy(
                spreadPrice = spec.defaultSpread,
                commissionPerLotRoundTurn = spec.commissionPerLotRoundTurn,
                commissionPctPerSide = spec.commissionPctPerSide,
                rebatePerLotRoundTurn = spec.rebatePerLotRoundTurn,
                swapLongPerLotPerDay = spec.swapLongPerLotPerDay,
                swapShortPerLotPerDay = spec.swapShortPerLotPerDay,
                contractSize = spec.contractSize,
                tickSize = spec.tickSize,
                tickValuePerLot = spec.tickValuePerLot,
            ),
        )
        val spread = market.quote(symbol)?.spread ?: spec.defaultSpread
        val cost = DemoTradingCostCalculator.estimateEntry(costConfig, volume, entry, spread)
        val fill = if (buy) entry + spread / 2.0 else entry - spread / 2.0
        val margin = entry * volume * spec.contractSize / spec.leverage.coerceAtLeast(1.0)
        val equity = balance + positions.values.sumOf { floatingPnl(it) }
        require(equity - usedMargin >= margin) { "insufficient demo free margin" }
        val id = "demo-${++sequence}"
        val position = Position(id, symbol.uppercase(), buy, volume, fill, stopLoss, takeProfit)
        positions[id] = position
        usedMargin += margin
        balance -= (cost.commission - cost.rebate)
        todayPnL -= (cost.commission - cost.rebate)
        DemoBrokerRepository.savePosition(accountId, position)
        DemoBrokerRepository.updateBalance(accountId, balance)
        return ExecutionResult(id, fill, cost.spreadCost, cost.commission, cost.rebate, margin)
    }

    override suspend fun closePosition(positionId: String): String =
        executeClose(positionId)?.let { "DEMO position closed: $positionId net P/L=${"%.2f".format(it.netPnl)}" }
            ?: "DEMO position not found: $positionId"

    suspend fun executeClose(positionId: String, holdingDays: Double = Double.NaN): CloseResult? {
        val position = positions.remove(positionId) ?: return null
        val gross = floatingPnl(position)
        val effectiveHoldingDays = if (holdingDays.isNaN()) {
            ((Clock.System.now().toEpochMilliseconds() - position.openedAt).coerceAtLeast(0L) / 86_400_000.0)
        } else holdingDays.coerceAtLeast(0.0)
        val spec = symbolSpec(position.symbol)
        val closeConfig = config.copy(costProfile = config.costProfile.copy(
            spreadPrice = market.quote(position.symbol)?.spread ?: spec.defaultSpread,
            commissionPerLotRoundTurn = spec.commissionPerLotRoundTurn,
            commissionPctPerSide = spec.commissionPctPerSide,
            rebatePerLotRoundTurn = spec.rebatePerLotRoundTurn,
            swapLongPerLotPerDay = spec.swapLongPerLotPerDay,
            swapShortPerLotPerDay = spec.swapShortPerLotPerDay,
            contractSize = spec.contractSize,
        ))
        val costs = DemoTradingCostCalculator.estimateRoundTurn(closeConfig, position.volume, position.entry, position.currentPrice, position.buy, effectiveHoldingDays)
        val margin = position.entry * position.volume * costProfile.contractSize / config.leverage.coerceAtLeast(1.0)
        usedMargin = (usedMargin - margin).coerceAtLeast(0.0)
        val net = gross - costs.commission - costs.swap + costs.rebate
        balance += net
        todayPnL += net
        val result = CloseResult(positionId, gross, costs.spreadCost, costs.commission, costs.rebate, costs.swap, net)
        deals += result
        DemoBrokerRepository.deletePosition(positionId)
        DemoBrokerRepository.recordDeal(accountId, positionId, position, result)
        DemoBrokerRepository.updateBalance(accountId, balance)
        return result
    }

    fun updateQuote(symbol: String, bid: Double, ask: Double, timestamp: Long = Clock.System.now().toEpochMilliseconds()) {
        val quote = market.update(symbol, bid, ask, timestamp)
        positions.replaceAll { _, p -> if (p.symbol == quote.symbol) p.copy(currentPrice = if (p.buy) quote.bid else quote.ask) else p }
        positions.values.filter { it.symbol == quote.symbol }.mapNotNull { position ->
            val hitSl = position.stopLoss?.let { if (position.buy) quote.bid <= it else quote.ask >= it } == true
            val hitTp = position.takeProfit?.let { if (position.buy) quote.bid >= it else quote.ask <= it } == true
            position.id.takeIf { hitSl || hitTp }
        }.forEach { id -> kotlinx.coroutines.runBlocking { executeClose(id) } }
    }

    fun updatePrice(symbol: String, price: Double) {
        if (price <= 0.0) return
        updateQuote(symbol, price, price, Clock.System.now().toEpochMilliseconds())
    }

    fun quote(symbol: String): DemoQuote? = market.quote(symbol)

    fun restoreFromPersistence(restored: List<DemoTradingEngine.Position>, persistedBalance: Double) {
        positions.clear()
        positions.putAll(restored.associateBy { it.id })
        balance = persistedBalance.coerceAtLeast(0.0)
        usedMargin = positions.values.sumOf { it.entry * it.volume * costProfile.contractSize / config.leverage.coerceAtLeast(1.0) }
        sequence = restored.mapNotNull { it.id.removePrefix("demo-").toLongOrNull() }.maxOrNull() ?: 0L
    }

    fun positions(): List<Position> = positions.values.toList()

    private fun floatingPnl(position: Position): Double {
        val spec = symbolSpec(position.symbol)
        val ticks = if (position.buy) (position.currentPrice - position.entry) / spec.tickSize else (position.entry - position.currentPrice) / spec.tickSize
        return ticks * spec.tickValuePerLot * position.volume
    }

    fun isKillSwitchActive(): Boolean = killSwitchActive
    fun resetKillSwitch() { killSwitchActive = false }

    private fun updateProtection(equity: Double) {
        val dailyLossPct = if (balance > 0.0) (-todayPnL / balance * 100.0).coerceAtLeast(0.0) else 100.0
        val drawdownPct = if (balance > 0.0) ((balance - equity) / balance * 100.0).coerceAtLeast(0.0) else 100.0
        if (dailyLossPct >= config.killSwitchDailyLossPct || drawdownPct >= config.killSwitchDrawdownPct) killSwitchActive = true
    }
}

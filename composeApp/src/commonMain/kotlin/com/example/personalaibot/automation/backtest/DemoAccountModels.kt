package com.example.personalaibot.automation.backtest

/** Broker-like cost profile for a mobile demo account. */
data class DemoCostProfile(
    val spreadMode: SpreadMode = SpreadMode.FIXED,
    val spreadPrice: Double = 0.20,
    val commissionPerLotRoundTurn: Double = 0.0,
    val commissionPctPerSide: Double = 0.0,
    val rebatePerLotRoundTurn: Double = 0.0,
    val swapLongPerLotPerDay: Double = 0.0,
    val swapShortPerLotPerDay: Double = 0.0,
    val contractSize: Double = 1.0,
    val tickSize: Double = 0.01,
    val tickValuePerLot: Double = 0.01,
    val spreadVolatilityPct: Double = 0.0,
    val maxSlippagePrice: Double = 0.0,
) {
    enum class SpreadMode { FIXED, VARIABLE }

    init {
        require(spreadPrice >= 0.0)
        require(commissionPerLotRoundTurn >= 0.0)
        require(commissionPctPerSide >= 0.0)
        require(rebatePerLotRoundTurn >= 0.0)
        require(contractSize > 0.0)
        require(tickSize > 0.0)
        require(tickValuePerLot >= 0.0)
        require(spreadVolatilityPct >= 0.0)
        require(maxSlippagePrice >= 0.0)
    }
}

data class DemoAccountConfig(
    val id: String,
    val name: String,
    val initialBalance: Double = 10_000.0,
    val currency: String = "USD",
    val leverage: Double = 100.0,
    val maxDailyLossPct: Double = 5.0,
    val maxDrawdownPct: Double = 10.0,
    val killSwitchDrawdownPct: Double = 15.0,
    val killSwitchDailyLossPct: Double = 7.0,
    val requireStopLoss: Boolean = true,
    val costProfile: DemoCostProfile = DemoCostProfile(),
)

data class DemoTradeCost(
    val spreadCost: Double,
    val commission: Double,
    val rebate: Double,
    val swap: Double,
) {
    val totalNetCost: Double get() = spreadCost + commission + swap - rebate
}

object DemoTradingCostCalculator {
    fun estimateEntry(
        config: DemoAccountConfig,
        volumeLots: Double,
        midPrice: Double,
        spreadPrice: Double = config.costProfile.spreadPrice,
    ): DemoTradeCost {
        val p = config.costProfile
        val spread = spreadPrice.coerceAtLeast(0.0) * volumeLots * p.contractSize
        val notional = midPrice.coerceAtLeast(0.0) * volumeLots * p.contractSize
        val commission = volumeLots * p.commissionPerLotRoundTurn / 2.0 + notional * p.commissionPctPerSide / 100.0
        val rebate = volumeLots * p.rebatePerLotRoundTurn / 2.0
        return DemoTradeCost(spread, commission, rebate, 0.0)
    }

    fun estimateRoundTurn(
        config: DemoAccountConfig,
        volumeLots: Double,
        entry: Double,
        exit: Double,
        buy: Boolean,
        holdingDays: Double = 0.0,
        spreadPrice: Double = config.costProfile.spreadPrice,
    ): DemoTradeCost {
        val p = config.costProfile
        val spread = spreadPrice.coerceAtLeast(0.0) * volumeLots * p.contractSize
        val entryNotional = entry.coerceAtLeast(0.0) * volumeLots * p.contractSize
        val exitNotional = exit.coerceAtLeast(0.0) * volumeLots * p.contractSize
        val commission = volumeLots * p.commissionPerLotRoundTurn +
            (entryNotional + exitNotional) * p.commissionPctPerSide / 100.0
        val rebate = volumeLots * p.rebatePerLotRoundTurn
        val swapRate = if (buy) p.swapLongPerLotPerDay else p.swapShortPerLotPerDay
        val swap = swapRate * volumeLots * holdingDays.coerceAtLeast(0.0)
        return DemoTradeCost(spread, commission, rebate, swap)
    }
}

package com.example.personalaibot.automation.backtest

/** MT5-like symbol specification used by the mobile Demo Broker and backtests. */
data class DemoSymbolSpec(
    val symbol: String,
    val digits: Int,
    val point: Double,
    val tickSize: Double,
    val tickValuePerLot: Double,
    val contractSize: Double,
    val volumeMin: Double,
    val volumeMax: Double,
    val volumeStep: Double,
    val defaultSpread: Double,
    val commissionPerLotRoundTurn: Double = 0.0,
    val commissionPctPerSide: Double = 0.0,
    val rebatePerLotRoundTurn: Double = 0.0,
    val swapLongPerLotPerDay: Double = 0.0,
    val swapShortPerLotPerDay: Double = 0.0,
    val leverage: Double = 100.0,
) {
    init {
        require(symbol.isNotBlank())
        require(digits in 0..10)
        require(point > 0.0 && tickSize > 0.0 && tickValuePerLot >= 0.0)
        require(contractSize > 0.0)
        require(volumeMin > 0.0 && volumeMax >= volumeMin && volumeStep > 0.0)
        require(defaultSpread >= 0.0)
        require(commissionPerLotRoundTurn >= 0.0 && commissionPctPerSide >= 0.0)
        require(rebatePerLotRoundTurn >= 0.0)
        require(leverage > 0.0)
    }

    fun normalizeVolume(volume: Double): Double {
        if (volume <= 0.0) return 0.0
        val steps = kotlin.math.floor((volume - volumeMin) / volumeStep + 1e-9)
        return (volumeMin + steps.coerceAtLeast(0.0) * volumeStep).coerceIn(volumeMin, volumeMax)
    }

    fun isValidVolume(volume: Double): Boolean =
        volume >= volumeMin - 1e-9 && volume <= volumeMax + 1e-9 &&
            kotlin.math.abs((volume - volumeMin) / volumeStep - kotlin.math.round((volume - volumeMin) / volumeStep)) < 1e-7
}

object DemoSymbolRegistry {
    private val specs = linkedMapOf(
        "EURUSD" to DemoSymbolSpec("EURUSD", 5, 0.00001, 0.00001, 1.0, 100000.0, 0.01, 100.0, 0.01, 0.00012),
        "GBPUSD" to DemoSymbolSpec("GBPUSD", 5, 0.00001, 0.00001, 1.0, 100000.0, 0.01, 100.0, 0.01, 0.00015),
        "USDJPY" to DemoSymbolSpec("USDJPY", 3, 0.001, 0.001, 1.0, 100000.0, 0.01, 100.0, 0.01, 0.015),
        "XAUUSD" to DemoSymbolSpec("XAUUSD", 2, 0.01, 0.01, 1.0, 100.0, 0.01, 100.0, 0.01, 0.20),
        "BTCUSD" to DemoSymbolSpec("BTCUSD", 2, 0.01, 0.01, 1.0, 1.0, 0.01, 100.0, 0.01, 20.0),
    )

    fun get(symbol: String): DemoSymbolSpec? = specs[symbol.uppercase()]
    fun all(): List<DemoSymbolSpec> = specs.values.toList()
}

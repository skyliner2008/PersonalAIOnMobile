package com.example.personalaibot.automation.backtest

import com.example.personalaibot.automation.SignalMarkerProvider
import com.example.personalaibot.tools.trading.Candle
import kotlin.math.min

/**
 * ParamOptimizer — grid search บน TpSlParams (port จาก OLD_Code backtest/optimizer.py)
 * composite score = Sharpe × ProfitFactor(cap 10) × WinRate, กรอง min trades
 */
object ParamOptimizer {

    data class ComboResult(
        val params: TpSlParams,
        val score: Double,
        val trades: Int,
        val winRate: Double,
        val profitFactor: Double,
        val sharpe: Double,
        val maxDrawdownPct: Double,
        val totalReturnPct: Double,
        val expectancyR: Double
    )

    fun score(r: BacktestResult, minTrades: Int = 5): Double {
        if (r.totalTrades < minTrades) return -999.0
        if (r.profitFactor <= 0) return -999.0
        val pf = min(r.profitFactor, 10.0)
        return r.sharpe * pf * r.winRate
    }

    /**
     * รัน backtest ทุก combo ใน grid สำหรับ kind เดียว แล้วจัดอันดับจาก score
     * markers ต้องคำนวณมาแล้ว (ชุดเดียวกับ candles) — engine จะ filter เฉพาะ kind นี้
     */
    fun gridSearch(
        kind: String,
        candles: List<Candle>,
        markers: List<SignalMarkerProvider.SignalMarker>,
        engine: BacktestEngine,
        kindOf: (String) -> String,
        strategyName: (String) -> String,
        config: BacktestConfig = BacktestConfig(),
        minTrades: Int = 5
    ): List<ComboResult> {
        val results = mutableListOf<ComboResult>()
        for (params in TpSlParams.grid()) {
            val r = runCatching {
                engine.run(
                    symbol = "", interval = "", source = "",
                    candles = candles, markers = markers, kindFilter = setOf(kind),
                    kindOf = kindOf, strategyName = strategyName,
                    tpSl = { k, s, c, i, a14, a6 -> parameterizedTpSl(k, s, c, i, a14, a6, params) },
                    config = config
                )
            }.getOrNull() ?: continue
            results += ComboResult(
                params = params,
                score = score(r, minTrades),
                trades = r.totalTrades,
                winRate = r.winRate,
                profitFactor = r.profitFactor,
                sharpe = r.sharpe,
                maxDrawdownPct = r.maxDrawdownPct,
                totalReturnPct = r.totalReturnPct,
                expectancyR = r.expectancyR
            )
        }
        return results.sortedByDescending { it.score }
    }
}

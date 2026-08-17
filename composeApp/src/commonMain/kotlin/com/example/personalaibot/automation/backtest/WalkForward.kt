package com.example.personalaibot.automation.backtest

import com.example.personalaibot.automation.SignalMarkerProvider
import com.example.personalaibot.tools.trading.Candle

/**
 * WalkForward — train บนอดีต ทดสอบบนอนาคต วนซ้ำ (port จาก OLD_Code backtest/walk_forward.py)
 * ตรวจ overfitting โดยเทียบ in-sample vs out-of-sample Sharpe
 * หมายเหตุ: markers คำนวณใหม่บน slice เสมอ (indicator ใช้ข้อมูลอดีตล้วน ไม่มี lookahead)
 */
object WalkForward {

    data class WindowResult(
        val split: Int,
        val trainBars: Int,
        val testBars: Int,
        val bestParams: TpSlParams,
        val inSampleSharpe: Double,
        val oosSharpe: Double,
        val oosWinRate: Double,
        val oosProfitFactor: Double,
        val oosTrades: Int
    )

    data class WalkForwardResult(
        val nSplits: Int,
        val windows: List<WindowResult>,
        val inSampleAvgSharpe: Double,
        val oosAvgSharpe: Double,
        val overfittingRatio: Double,   // OOS / IS (1.0 = ดีมาก, <0.5 = น่าสงสัย)
        val likelyOverfit: Boolean,
        val paramStabilityCv: Double    // coefficient of variation เฉลี่ยของ best params (ต่ำ = เสถียร)
    )

    fun run(
        kind: String,
        candles: List<Candle>,
        markerProvider: SignalMarkerProvider,
        engine: BacktestEngine,
        kindOf: (String) -> String,
        strategyName: (String) -> String,
        nSplits: Int = 5,
        trainPct: Double = 0.7,
        config: BacktestConfig = BacktestConfig()
    ): WalkForwardResult {
        val total = candles.size
        if (total < 500) {
            return WalkForwardResult(0, emptyList(), 0.0, 0.0, 0.0, false, 0.0)
        }
        val splitSize = total / nSplits
        val windows = mutableListOf<WindowResult>()
        val isSharpes = mutableListOf<Double>()
        val oosSharpes = mutableListOf<Double>()
        val bestParamsList = mutableListOf<TpSlParams>()

        for (s in 0 until nSplits) {
            // anchored: train เริ่มที่ 0 เสมอ (expanding window)
            val splitEnd = if (s < nSplits - 1) (s + 1) * splitSize else total
            val trainEnd = (splitEnd * trainPct).toInt()
            val testStart = trainEnd
            if (splitEnd - testStart < 100 || trainEnd < 300) continue

            val trainCandles = candles.subList(0, trainEnd)
            val prefixCandles = candles.subList(0, splitEnd)

            // markers คำนวณบน prefix ที่ anchor ที่ 0 เสมอ — indicator (EMA200/ATR) warm ด้วยประวัติเต็ม
            // เดิมคำนวณ test markers บน slice แยก → indicator reseed ที่ขอบ slice ทำสัญญาณช่วงต้น OOS เพี้ยน
            val trainMarkers = markerProvider.compute(trainCandles, Int.MAX_VALUE)
            val testStartTs = candles[testStart].timestamp
            val testMarkers = markerProvider.compute(prefixCandles, Int.MAX_VALUE)
                .filter { it.time >= testStartTs }

            val ranked = ParamOptimizer.gridSearch(
                kind, trainCandles, trainMarkers, engine, kindOf, strategyName, config
            )
            val best = ranked.firstOrNull { it.score > -999.0 } ?: continue
            bestParamsList += best.params
            isSharpes += best.sharpe

            val oos = runCatching {
                engine.run(
                    symbol = "", interval = "", source = "",
                    candles = prefixCandles, markers = testMarkers, kindFilter = setOf(kind),
                    kindOf = kindOf, strategyName = strategyName,
                    tpSl = { k, sd, c, i, a14, a6 -> parameterizedTpSl(k, sd, c, i, a14, a6, best.params) },
                    config = config,
                    startIndex = testStart   // ATR/indicator warm จาก prefix แต่วัดผลเฉพาะช่วง OOS
                )
            }.getOrNull() ?: continue
            oosSharpes += oos.sharpe

            windows += WindowResult(
                split = s + 1,
                trainBars = trainCandles.size,
                testBars = splitEnd - testStart,
                bestParams = best.params,
                inSampleSharpe = best.sharpe,
                oosSharpe = oos.sharpe,
                oosWinRate = oos.winRate,
                oosProfitFactor = oos.profitFactor,
                oosTrades = oos.totalTrades
            )
        }

        val isAvg = if (isSharpes.isNotEmpty()) isSharpes.average() else 0.0
        val oosAvg = if (oosSharpes.isNotEmpty()) oosSharpes.average() else 0.0
        val ratio = if (isAvg > 0) oosAvg / isAvg else 0.0

        // param stability: CV เฉลี่ยของ sl/tp mults ข้าม windows (ต่ำ = params นิ่ง = overfit น้อย)
        fun cv(values: List<Double>): Double {
            if (values.size < 2) return 0.0
            val mean = values.average()
            if (mean == 0.0) return 0.0
            val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
            return kotlin.math.sqrt(variance) / kotlin.math.abs(mean)
        }
        val stabCv = listOf(
            cv(bestParamsList.map { it.slMult }),
            cv(bestParamsList.map { it.tpMult })
        ).average()

        return WalkForwardResult(
            nSplits = windows.size,
            windows = windows,
            inSampleAvgSharpe = isAvg,
            oosAvgSharpe = oosAvg,
            overfittingRatio = ratio,
            likelyOverfit = ratio < 0.5 && isAvg > 0,
            paramStabilityCv = stabCv
        )
    }
}

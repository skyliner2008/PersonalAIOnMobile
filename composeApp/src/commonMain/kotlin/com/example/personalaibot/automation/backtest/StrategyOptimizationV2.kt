package com.example.personalaibot.automation.backtest

import com.example.personalaibot.automation.SignalMarkerProvider
import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Strategy Optimization V2
 *
 * เป้าหมายไม่ใช่หา parameter ที่ให้กำไรสูงสุดบนข้อมูลชุดเดียว แต่หา
 * "parameter region" ที่ยังทำงานได้เมื่อเปลี่ยนช่วงข้อมูล
 *
 * Pipeline:
 *   train -> validation -> holdout(OOS)
 *   + neighborhood stability
 *   + drawdown/trade-count constraints
 *
 * ใช้ strategy-specific EntryParams + strategy-native TpSlParams ร่วมกัน
 * เพื่อให้ optimization วัด edge ของ strategy ทั้งระบบ ไม่ใช่แค่ SL/TP
 */
object StrategyOptimizationV2 {
    data class Candidate(
        val entry: EntryParams,
        val tpSl: TpSlParams,
        val trainScore: Double,
        val validationScore: Double,
        val holdoutScore: Double,
        val trainTrades: Int,
        val validationTrades: Int,
        val holdoutTrades: Int,
        val trainExpectancyR: Double,
        val validationExpectancyR: Double,
        val holdoutExpectancyR: Double,
        val holdoutPf: Double,
        val holdoutDd: Double,
        val neighborhoodStability: Double
    )

    data class Result(
        val kind: String,
        val initialEntry: EntryParams,
        val initialTpSl: TpSlParams,
        val best: Candidate?,
        val candidatesEvaluated: Int,
        val robustRegionSize: Int,
        val trainBars: Int,
        val validationBars: Int,
        val holdoutBars: Int,
        val recommendation: String
    )

    private data class Eval(
        val result: BacktestResult,
        val score: Double
    )

    /**
     * Optimize one strategy. The marker factory MUST use the supplied EntryParams so
     * entry parameters are evaluated with the exact same signal implementation as live.
     */
    fun optimize(
        kind: String,
        candles: List<Candle>,
        markerProvider: SignalMarkerProvider,
        engine: BacktestEngine,
        kindOf: (String) -> String,
        strategyName: (String) -> String,
        initialEntry: EntryParams = EntryParams.defaultsFor(kind),
        initialTpSl: TpSlParams = TpSlParams.defaultsFor(kind),
        config: BacktestConfig = BacktestConfig(),
        trainFraction: Double = 0.60,
        validationFraction: Double = 0.20,
        topK: Int = 8
    ): Result {
        require(candles.size >= 800) { "StrategyOptimizationV2 ต้องการอย่างน้อย 800 แท่ง" }

        val trainEnd = (candles.size * trainFraction.coerceIn(0.50, 0.70)).toInt()
        val validationEnd = (candles.size * (trainFraction + validationFraction).coerceIn(0.70, 0.90)).toInt()
        val holdoutStart = validationEnd
        if (trainEnd < 300 || validationEnd <= trainEnd || holdoutStart >= candles.size - 50) {
            return Result(kind, initialEntry, initialTpSl, null, 0, 0, trainEnd, validationEnd - trainEnd, candles.size - holdoutStart, "ข้อมูลแบ่งช่วงไม่เพียงพอ")
        }

        val entryGrid = (EntryParams.gridFor(kind) + initialEntry).distinct()
        val tpGrid = (TpSlParams.grid() + initialTpSl).distinct()
        val combinations = entryGrid.flatMap { e -> tpGrid.map { t -> e to t } }

        fun run(
            entry: EntryParams,
            tpSl: TpSlParams,
            end: Int,
            start: Int = 1
        ): Eval? {
            if (end <= start + 62) return null
            val prefix = candles.subList(0, end)
            val markers = markerProvider.compute(prefix, Int.MAX_VALUE, mapOf(kind to entry))
            val r = runCatching {
                engine.run(
                    symbol = "", interval = "", source = "",
                    candles = prefix,
                    markers = markers,
                    kindFilter = setOf(kind),
                    kindOf = kindOf,
                    strategyName = strategyName,
                    tpSl = { k, side, c, i, a14, a6 -> parameterizedTpSl(k, side, c, i, a14, a6, tpSl) },
                    config = config,
                    startIndex = start
                )
            }.getOrNull() ?: return null
            return Eval(r, objective(r))
        }

        // Stage 1: cheap but strict train screening. No candidate with weak evidence enters validation.
        val trainCandidates = combinations.mapNotNull { (e, t) ->
            val ev = run(e, t, trainEnd) ?: return@mapNotNull null
            if (!eligible(ev.result, 8)) null else Triple(e, t, ev)
        }.sortedByDescending { it.third.score }
            .take(topK.coerceIn(3, 12))

        if (trainCandidates.isEmpty()) {
            return Result(kind, initialEntry, initialTpSl, null, combinations.size, 0, trainEnd, validationEnd - trainEnd, candles.size - holdoutStart, "ไม่มี candidate ผ่าน train evidence gate")
        }

        val validated = trainCandidates.mapNotNull { (entry, tp, train) ->
            // validation starts at trainEnd; prefix is retained so ATR/indicator state is warmed correctly.
            val v = run(entry, tp, validationEnd, trainEnd) ?: return@mapNotNull null
            if (!eligible(v.result, 5)) return@mapNotNull null
            val hold = run(entry, tp, candles.size, holdoutStart) ?: return@mapNotNull null
            if (!eligible(hold.result, 5, hardDd = 40.0)) return@mapNotNull null
            val stability = neighborhoodStability(entry, tp, kind, candles, markerProvider, engine, kindOf, strategyName, config, trainEnd)
            Candidate(
                entry = entry,
                tpSl = tp,
                trainScore = train.score,
                validationScore = v.score,
                holdoutScore = hold.score,
                trainTrades = train.result.totalTrades,
                validationTrades = v.result.totalTrades,
                holdoutTrades = hold.result.totalTrades,
                trainExpectancyR = train.result.expectancyR,
                validationExpectancyR = v.result.expectancyR,
                holdoutExpectancyR = hold.result.expectancyR,
                holdoutPf = hold.result.profitFactor,
                holdoutDd = hold.result.maxDrawdownPct,
                neighborhoodStability = stability
            )
        }.sortedWith(compareByDescending<Candidate> { it.holdoutScore + it.validationScore * 0.50 + it.neighborhoodStability * 0.25 }
            .thenByDescending { it.holdoutExpectancyR }
            .thenBy { it.holdoutDd })

        val best = validated.firstOrNull()
        val robustRegion = validated.count { it.neighborhoodStability >= 0.70 && it.holdoutPf >= 1.0 }
        val recommendation = when {
            best == null -> "HOLD — ไม่มี candidate ผ่าน validation + holdout"
            best.holdoutPf < 1.0 || best.holdoutExpectancyR <= 0.0 -> "HOLD — OOS ไม่มี positive expectancy"
            best.neighborhoodStability < 0.60 -> "HOLD — parameter เป็น cliff/เปราะบาง"
            best.validationScore <= 0.0 -> "HOLD — validation ไม่ยืนยัน edge"
            else -> "CANDIDATE — ผ่าน train/validation/holdout และมี stability evidence; ต้องผ่าน permutation/Monte Carlo/Risk Gate ก่อน LIVE"
        }

        return Result(
            kind = kind,
            initialEntry = initialEntry,
            initialTpSl = initialTpSl,
            best = best,
            candidatesEvaluated = combinations.size,
            robustRegionSize = robustRegion,
            trainBars = trainEnd,
            validationBars = validationEnd - trainEnd,
            holdoutBars = candles.size - holdoutStart,
            recommendation = recommendation
        )
    }

    private fun objective(r: BacktestResult): Double {
        if (r.totalTrades < 5 || r.profitFactor < 1.0 || r.expectancyR <= 0.0 || r.maxDrawdownPct > 35.0) return -999.0
        val tradeConfidence = minOf(1.0, r.totalTrades / 30.0)
        val pf = (r.profitFactor.coerceIn(1.0, 3.0) - 1.0) / 2.0
        val sharpe = r.sharpe.coerceIn(-1.0, 2.0) / 2.0
        val ddPenalty = 1.0 / (1.0 + r.maxDrawdownPct / 20.0)
        return (r.expectancyR * 0.55 + pf * 0.25 + sharpe * 0.20) * ddPenalty * tradeConfidence
    }

    private fun eligible(r: BacktestResult, minTrades: Int, hardDd: Double = 35.0): Boolean =
        r.totalTrades >= minTrades && r.profitFactor >= 1.0 && r.expectancyR > 0.0 && r.maxDrawdownPct <= hardDd

    /**
     * Stability is measured around the chosen point, not by the absolute score.
     * A parameter that only wins at one exact point receives a low score.
     */
    private fun neighborhoodStability(
        entry: EntryParams,
        tp: TpSlParams,
        kind: String,
        candles: List<Candle>,
        markerProvider: SignalMarkerProvider,
        engine: BacktestEngine,
        kindOf: (String) -> String,
        strategyName: (String) -> String,
        config: BacktestConfig,
        trainEnd: Int
    ): Double {
        val entryNeighbors = EntryParams.gridFor(kind).filter { isNearEntry(kind, entry, it) }.take(8)
        val tpNeighbors = TpSlParams.grid().filter { isNearTp(tp, it) }.take(8)
        val values = mutableListOf<Double>()
        for (e in entryNeighbors) for (t in tpNeighbors) {
            val prefix = candles.subList(0, trainEnd)
            val markers = markerProvider.compute(prefix, Int.MAX_VALUE, mapOf(kind to e))
            val r = runCatching {
                engine.run(
                    symbol = "", interval = "", source = "", candles = prefix,
                    markers = markers, kindFilter = setOf(kind), kindOf = kindOf, strategyName = strategyName,
                    tpSl = { k, side, c, i, a14, a6 -> parameterizedTpSl(k, side, c, i, a14, a6, t) },
                    config = config
                )
            }.getOrNull() ?: continue
            if (r.totalTrades >= 5 && r.expectancyR > 0.0 && r.profitFactor >= 1.0) values += objective(r)
        }
        if (values.isEmpty()) return 0.0
        val positive = values.count { it > 0.0 }.toDouble() / values.size
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        val cv = if (mean > 0.0) sqrt(variance) / mean else 1.0
        return (0.70 * positive + 0.30 * (1.0 - cv.coerceIn(0.0, 1.0))).coerceIn(0.0, 1.0)
    }

    private fun isNearTp(a: TpSlParams, b: TpSlParams): Boolean =
        abs(a.slMult - b.slMult) <= 0.30 && abs(a.tpMult - b.tpMult) <= 0.50

    private fun isNearEntry(kind: String, a: EntryParams, b: EntryParams): Boolean = when (kind) {
        "MOM" -> abs(a.momLookback - b.momLookback) <= 10
        "TR" -> abs(a.trFast - b.trFast) <= 15 && abs(a.trSlow - b.trSlow) <= 50
        "REV" -> abs(a.revRsiLow - b.revRsiLow) <= 5 && abs(a.revRsiHigh - b.revRsiHigh) <= 5
        "DC" -> abs(a.dcPeriod - b.dcPeriod) <= 10
        "52H" -> abs(a.w52ProxBuy - b.w52ProxBuy) <= 0.03 && abs(a.w52ProxSell - b.w52ProxSell) <= 0.05 && abs(a.w52Lookback - b.w52Lookback) <= 1000
        "E" -> abs(a.eFast - b.eFast) <= 6 && abs(a.eSlow - b.eSlow) <= 30
        "UT" -> abs(a.utKey - b.utKey) <= 0.5 && abs(a.utAtrPeriod - b.utAtrPeriod) <= 2
        else -> a == b
    }
}

package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** P4.1 decision engine: confluence is setup quality; candle/event confirmation is mandatory. */
enum class SmcApsDecision { BUY, SELL, NO_TRADE }
enum class SmcApsPath { REVERSAL, CONTINUATION, NONE }

data class SmcApsConfig(
    val minSetupScore: Int = 6,
    val minRiskReward: Double = 1.5,
    val minBodyAtr: Double = 0.35,
    val minSweepWickAtr: Double = 0.20,
    val minRunBodyAtr: Double = 0.50
)

data class SmcApsSignal(
    val decision: SmcApsDecision,
    val path: SmcApsPath,
    val score: Int,
    val entry: Double,
    val sl: Double,
    val tp: Double,
    val rr: Double,
    val confirmation: SmcSignalConfirmation,
    val triggers: List<String>
)

object SmcApsEngine {
    fun evaluate(candles: List<Candle>, symbol: String, timeframe: String, config: SmcApsConfig = SmcApsConfig()): SmcApsSignal {
        if (candles.size < 60) return none()
        val last = candles.last()
        val prev = candles[candles.lastIndex - 1]
        val atr = computeAtr(candles)
        if (atr <= 0.0) return none()
        val smc = SmcEngine.buildSnapshot(candles, symbol, timeframe)
        val sweep = SweepDetection.detectSweeps(candles)
        val bullEvent = if (sweep.first) SmcSignalConfirmation.LIQUIDITY_SWEEP else SmcSignalConfirmation.NONE
        val bearEvent = if (sweep.second) SmcSignalConfirmation.LIQUIDITY_SWEEP else SmcSignalConfirmation.NONE
        val bull = smc.confluence.firstOrNull { it.side == "BULL" }
        val bear = smc.confluence.firstOrNull { it.side == "BEAR" }
        val bullRun = isRun(last, prev, true, smc, atr, config)
        val bearRun = isRun(last, prev, false, smc, atr, config)
        val bullConfirm = if (bullEvent != SmcSignalConfirmation.NONE) bullEvent else if (bullRun) SmcSignalConfirmation.LIQUIDITY_RUN else SmcSignalConfirmation.NONE
        val bearConfirm = if (bearEvent != SmcSignalConfirmation.NONE) bearEvent else if (bearRun) SmcSignalConfirmation.LIQUIDITY_RUN else SmcSignalConfirmation.NONE
        val candidates = listOfNotNull(
            candidate(true, bull, bullConfirm, last, atr, smc, config),
            candidate(false, bear, bearConfirm, last, atr, smc, config)
        )
        return candidates.maxByOrNull { it.score } ?: none()
    }

    private fun candidate(bull: Boolean, c: SmcConfluence?, confirmation: SmcSignalConfirmation, last: Candle, atr: Double, smc: SmcSnapshot, cfg: SmcApsConfig): SmcApsSignal? {
        if (c == null || c.score < cfg.minSetupScore || confirmation == SmcSignalConfirmation.NONE) return null
        val side = if (bull) 1 else -1
        val wall = smc.walls.firstOrNull { it.isBull == bull }
        val structural = if (bull) smc.structure.structureLow else smc.structure.structureHigh
        val sl = if (bull) min(last.low, wall?.bottom ?: structural) - atr * 0.15 else max(last.high, wall?.top ?: structural) + atr * 0.15
        val risk = abs(last.close - sl)
        if (risk <= atr * 0.5) return null
        val tp = if (bull) last.close + risk * cfg.minRiskReward else last.close - risk * cfg.minRiskReward
        val rr = abs(tp - last.close) / risk
        if (rr < cfg.minRiskReward) return null
        val score = min(100, c.score * 8 + if (confirmation == SmcSignalConfirmation.LIQUIDITY_SWEEP) 18 else 15 + if (c.obCount > 0) 8 else 0 + if (c.fvgCount > 0) 8 else 0)
        val triggers = buildList {
            add(if (bull) "BUY" else "SELL")
            add(if (confirmation == SmcSignalConfirmation.LIQUIDITY_SWEEP) "LIQUIDITY_SWEEP" else "LIQUIDITY_RUN")
            if (c.obCount > 0) add("OB")
            if (c.fvgCount > 0) add("FVG")
            if (c.liquidityStars > 0) add("LIQ=${c.liquidityStars}★")
        }
        return SmcApsSignal(if (side > 0) SmcApsDecision.BUY else SmcApsDecision.SELL, if (confirmation == SmcSignalConfirmation.LIQUIDITY_SWEEP) SmcApsPath.REVERSAL else SmcApsPath.CONTINUATION, score, last.close, sl, tp, rr, confirmation, triggers)
    }

    private fun isRun(last: Candle, prev: Candle, bull: Boolean, smc: SmcSnapshot, atr: Double, cfg: SmcApsConfig): Boolean {
        val body = bodySize(last)
        if (body < atr * cfg.minRunBodyAtr) return false
        val directional = if (bull) isBullish(last) && last.close > prev.high else !isBullish(last) && last.close < prev.low
        if (!directional) return false
        return if (bull) smc.confluence.any { it.side == "BULL" && it.liquidityStars > 0 } else smc.confluence.any { it.side == "BEAR" && it.liquidityStars > 0 }
    }

    private fun none() = SmcApsSignal(SmcApsDecision.NO_TRADE, SmcApsPath.NONE, 0, 0.0, 0.0, 0.0, 0.0, SmcSignalConfirmation.NONE, emptyList())
}

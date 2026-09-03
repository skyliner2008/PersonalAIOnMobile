package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.max

/**
 * Sweep Detection + Attack Force — port จาก mt5-core-server sweepDetection.ts (Pine V8.3)
 * บนมือถือมี TF เดียวต่อ job — ใช้ detectSweeps แบบ single-TF (MTF aggregate ของต้นฉบับข้ามไป)
 */
internal object SweepDetection {

    private const val RECLAIM_FACTOR = 0.50
    private const val MIN_WICK_ATR_MULT = 0.30

    /** (bull, bear) — sweep ของแท่งล่าสุดเทียบกับแท่ง bearish/bullish ที่ใกล้สุด */
    fun detectSweeps(candles: List<Candle>): Pair<Boolean, Boolean> {
        if (candles.size < 10) return false to false

        val last = candles[candles.size - 1]
        val atr = computeAtr(candles)

        var bullTop: Double? = null
        var bullBtm: Double? = null
        var bearTop: Double? = null
        var bearBtm: Double? = null

        for (i in candles.size - 2 downTo 0) {
            if (isBearish(candles[i]) && bullTop == null) {
                bullTop = candleMax(candles[i]); bullBtm = candleMin(candles[i])
            }
            if (isBullish(candles[i]) && bearTop == null) {
                bearTop = candleMax(candles[i]); bearBtm = candleMin(candles[i])
            }
            if (bullTop != null && bearTop != null) break
        }

        var bull = false
        var bear = false

        val bb = bullBtm; val bt = bullTop
        if (bb != null && bt != null) {
            val width = abs(bt - bb)
            val reclaim = bb + RECLAIM_FACTOR * width
            val wick = bb - last.low
            bull = width > 0 && last.low < bb && last.close > reclaim && wick > MIN_WICK_ATR_MULT * atr
        }

        val rb = bearBtm; val rt = bearTop
        if (rb != null && rt != null) {
            val width = abs(rt - rb)
            val reclaim = rt - RECLAIM_FACTOR * width
            val wick = last.high - rt
            bear = width > 0 && last.high > rt && last.close < reclaim && wick > MIN_WICK_ATR_MULT * atr
        }

        return bull to bear
    }

    /** Candle-confirmed liquidity event: sweep = pierce + reclaim; run = decisive close beyond liquidity. */
    fun detectLiquidityEvents(candles: List<Candle>, zones: List<LiquidityZone>): Pair<SmcSignalConfirmation, SmcSignalConfirmation> {
        if (candles.size < 2 || zones.isEmpty()) return SmcSignalConfirmation.NONE to SmcSignalConfirmation.NONE
        val c = candles.last()
        val atr = computeAtr(candles)
        val tol = max(atr * 0.10, (c.high - c.low) * 0.05)
        val above = zones.filter { it.isHigh && it.price > 0 }.minByOrNull { abs(it.price - c.high) }
        val below = zones.filter { !it.isHigh && it.price > 0 }.minByOrNull { abs(it.price - c.low) }
        fun event(level: Double, highSide: Boolean): SmcSignalConfirmation {
            val pierced = if (highSide) c.high > level + tol else c.low < level - tol
            if (!pierced) return SmcSignalConfirmation.NONE
            val reclaimed = if (highSide) c.close < level else c.close > level
            return if (reclaimed) SmcSignalConfirmation.LIQUIDITY_SWEEP else SmcSignalConfirmation.LIQUIDITY_RUN
        }
        return (below?.let { event(it.price, false) } ?: SmcSignalConfirmation.NONE) to
            (above?.let { event(it.price, true) } ?: SmcSignalConfirmation.NONE)
    }

    /** Attack Force — แท่งโมเมนตัมแรงผิดปกติ (body > 2×ATR และ > 2×ค่าเฉลี่ย + volume ยืนยัน) 5 แท่งล่าสุด */
    fun detectAttackForce(
        candles: List<Candle>,
        atrMultiplier: Double = 2.0,
        bodyAvgPeriod: Int = 10,
        volumeAvgPeriod: Int = 10,
        bodyMultiplier: Double = 2.0,
        volumeMultiplier: Double = 1.5
    ): List<AttackForce> {
        if (candles.size < bodyAvgPeriod + 1) return emptyList()

        val atr = computeAtr(candles)
        val forces = mutableListOf<AttackForce>()
        val startIdx = max(bodyAvgPeriod, candles.size - 5)

        for (i in startIdx until candles.size) {
            val c = candles[i]
            val body = bodySize(c)

            var bodySum = 0.0
            for (j in i - bodyAvgPeriod until i) bodySum += bodySize(candles[j])
            val avgBody = bodySum / bodyAvgPeriod

            var volSum = 0.0
            val volStart = max(0, i - volumeAvgPeriod)
            for (j in volStart until i) volSum += candles[j].volume
            val volCount = i - volStart
            val avgVol = if (volCount > 0) volSum / volCount else 0.0

            val bodyOk = body > avgBody * bodyMultiplier
            val volOk = avgVol <= 0 || c.volume > avgVol * volumeMultiplier
            val atrOk = body > atr * atrMultiplier

            if (bodyOk && volOk && atrOk) {
                forces += AttackForce(
                    barIndex = i,
                    isBull = isBullish(c),
                    bodySize = body,
                    atrRatio = if (atr > 0) body / atr else 0.0,
                    volumeRatio = if (avgVol > 0) c.volume / avgVol else 0.0
                )
            }
        }

        return forces
    }
}

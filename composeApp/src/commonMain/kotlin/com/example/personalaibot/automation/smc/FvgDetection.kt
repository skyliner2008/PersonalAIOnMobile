package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.max
import kotlin.math.min

/**
 * FVG Detection — port จาก mt5-core-server fvgDetection.ts (Pine V8.3)
 * FVG = ช่องว่าง candle[i-2] กับ candle[i] ที่ใหญ่กว่า ATR × multiplier
 */
internal object FvgDetection {

    fun detect(
        candles: List<Candle>,
        lookback: Int = 30,
        atrMultiplier: Double = 0.25,
        atrPeriod: Int = 200
    ): List<Fvg> {
        if (candles.size < 3) return emptyList()

        val atr = computeAtr(candles, min(atrPeriod, candles.size - 1).coerceAtLeast(1))
        val minGapSize = atr * atrMultiplier
        val startIdx = max(2, candles.size - lookback)
        val lastIdx = candles.size - 1

        val fvgs = mutableListOf<Fvg>()

        for (i in startIdx until candles.size) {
            val c0 = candles[i]
            val c1 = candles[i - 1]
            val c2 = candles[i - 2]

            // Bullish FVG: gap c2.high → c0.low + displacement (c1 close เหนือ c2.high)
            if (c0.low > c2.high && c1.close > c2.high) {
                val gap = c0.low - c2.high
                if (gap > minGapSize) {
                    fvgs += Fvg(true, top = c0.low, bottom = c2.high, barIndex = i, age = lastIdx - i)
                }
            }

            // Bearish FVG: gap c2.low ← c0.high + displacement (c1 close ใต้ c2.low)
            if (c0.high < c2.low && c1.close < c2.low) {
                val gap = c2.low - c0.high
                if (gap > minGapSize) {
                    fvgs += Fvg(false, top = c2.low, bottom = c0.high, barIndex = i, age = lastIdx - i)
                }
            }
        }

        // mark mitigated — ราคากลับมา fill gap แล้ว
        for (fvg in fvgs) {
            for (j in fvg.barIndex + 1 until candles.size) {
                val c = candles[j]
                if (fvg.isBull && c.low <= fvg.bottom) { fvg.mitigated = true; break }
                if (!fvg.isBull && c.high >= fvg.top) { fvg.mitigated = true; break }
            }
        }

        return fvgs
    }

    fun active(candles: List<Candle>): List<Fvg> = detect(candles).filter { !it.mitigated }
}

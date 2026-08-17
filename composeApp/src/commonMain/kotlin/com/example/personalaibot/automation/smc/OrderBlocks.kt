package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.min

/**
 * Order Block Detection — port จาก mt5-core-server orderBlocks.ts (Pine V8.3)
 * Strict SMC: ต้องมี FVG confirm (displacement) + กรองตามทิศโครงสร้าง
 * คืน list เรียง "ใหม่สุดก่อน" (index 0 = ล่าสุด) ตรงกับต้นฉบับที่ใช้ unshift
 */
internal object OrderBlockDetection {

    fun detect(
        candles: List<Candle>,
        fvgs: List<Fvg>,
        structDir: StructureDirection = StructureDirection.INIT,
        swingLookback: Int = 5,
        useBody: Boolean = true,
        filterByTrend: Boolean = true,
        maxOBCount: Int = 30
    ): Pair<List<SmcOrderBlock>, List<SmcOrderBlock>> {
        if (candles.size < 20) return emptyList<SmcOrderBlock>() to emptyList()

        val (highs, lows) = detectSwings(candles, swingLookback)
        val bullOBs = mutableListOf<SmcOrderBlock>()
        val bearOBs = mutableListOf<SmcOrderBlock>()
        val gMax = { c: Candle -> if (useBody) candleMax(c) else c.high }
        val gMin = { c: Candle -> if (useBody) candleMin(c) else c.low }

        // Bullish OBs: swing high ที่ถูกทะลุขึ้น → OB = แท่ง bearish แรกหลังจุดต่ำสุดในช่วง
        for (swing in highs) {
            var breakIdx = -1
            for (idx in swing.index + 1 until candles.size) {
                if (candles[idx].close > swing.price) { breakIdx = idx; break }
            }
            if (breakIdx == -1) continue

            var lowestLow = gMin(candles[swing.index])
            var lowestIdx = swing.index
            for (i in swing.index until breakIdx) {
                val low = gMin(candles[i])
                if (low <= lowestLow) { lowestLow = low; lowestIdx = i }
            }
            var obIdx = -1
            for (k in lowestIdx..min(breakIdx + 5, candles.size - 1)) {
                if (isBearish(candles[k])) { obIdx = k; break }
            }
            if (obIdx == -1) continue
            if (filterByTrend && structDir == StructureDirection.BEARISH) continue

            val hasFvg = fvgs.any { it.isBull && abs(it.barIndex - obIdx) <= 5 }
            if (hasFvg) {
                bullOBs.add(0, SmcOrderBlock(
                    isBull = true, top = gMax(candles[obIdx]), bottom = gMin(candles[obIdx]),
                    barIndex = obIdx, hasFvg = true, volume = candles[obIdx].volume
                ))
            }
        }

        // Bearish OBs: swing low ที่ถูกทะลุลง → OB = แท่ง bullish แรกหลังจุดสูงสุดในช่วง
        for (swing in lows) {
            var breakIdx = -1
            for (idx in swing.index + 1 until candles.size) {
                if (candles[idx].close < swing.price) { breakIdx = idx; break }
            }
            if (breakIdx == -1) continue

            var highestHigh = gMax(candles[swing.index])
            var highestIdx = swing.index
            for (i in swing.index until breakIdx) {
                val high = gMax(candles[i])
                if (high >= highestHigh) { highestHigh = high; highestIdx = i }
            }
            var obIdx = -1
            for (k in highestIdx..min(breakIdx + 5, candles.size - 1)) {
                if (isBullish(candles[k])) { obIdx = k; break }
            }
            if (obIdx == -1) continue
            if (filterByTrend && structDir == StructureDirection.BULLISH) continue

            val hasFvg = fvgs.any { !it.isBull && abs(it.barIndex - obIdx) <= 5 }
            if (hasFvg) {
                bearOBs.add(0, SmcOrderBlock(
                    isBull = false, top = gMax(candles[obIdx]), bottom = gMin(candles[obIdx]),
                    barIndex = obIdx, hasFvg = true, volume = candles[obIdx].volume
                ))
            }
        }

        // mitigation & invalidation
        val last = candles[candles.size - 1]
        for (ob in bullOBs) {
            for (j in ob.barIndex + 1 until candles.size) {
                if (candles[j].low <= ob.top && candles[j].high >= ob.bottom) { ob.mitigated = true; break }
            }
            if (candleMin(last) < ob.bottom) ob.invalidated = true
        }
        for (ob in bearOBs) {
            for (j in ob.barIndex + 1 until candles.size) {
                if (candles[j].high >= ob.bottom && candles[j].low <= ob.top) { ob.mitigated = true; break }
            }
            if (candleMax(last) > ob.top) ob.invalidated = true
        }

        return bullOBs.filter { !it.invalidated }.take(maxOBCount) to
                bearOBs.filter { !it.invalidated }.take(maxOBCount)
    }
}

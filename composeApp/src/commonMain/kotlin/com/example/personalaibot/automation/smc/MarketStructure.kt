package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.max
import kotlin.math.min

/**
 * Market Structure Detection — port จาก mt5-core-server marketStructure.ts (Pine V8.3)
 * ตรวจ BOS / CHoCH / SMS / BMS + IDM (Inducement) filter
 */
internal object MarketStructureDetector {

    fun detect(
        candles: List<Candle>,
        useBodyBreak: Boolean = true,
        idmEnabled: Boolean = true,
        idmSwingLength: Int = 3
    ): MarketStructure {
        if (candles.size < 30) return emptyStructure()

        var direction = StructureDirection.INIT
        var structureHigh = candles[0].high
        var structureLow = candles[0].low
        var structureHighBarIndex = 0
        var structureLowBarIndex = 0
        var lastEvent = StructureEvent.NONE
        var lastEventSide: String? = null
        var continuationCount = 0

        var idmHigh: Double? = null
        var idmLow: Double? = null
        var idmHighSwept = false
        var idmLowSwept = false

        val idmSwings = if (idmEnabled) detectSwings(candles, idmSwingLength) else (emptyList<SwingPoint>() to emptyList())

        for (i in 1 until candles.size) {
            val c = candles[i]
            val breakLow = if (useBodyBreak) c.close else c.low
            val breakHigh = if (useBodyBreak) c.close else c.high

            // --- IDM ---
            if (idmEnabled) {
                for (sh in idmSwings.first) {
                    if (sh.index == i) { idmHigh = sh.price; idmHighSwept = false }
                }
                for (sl in idmSwings.second) {
                    if (sl.index == i) { idmLow = sl.price; idmLowSwept = false }
                }
                val ih = idmHigh
                if (!idmHighSwept && ih != null) {
                    if (c.high > ih && c.close < ih) idmHighSwept = true
                    if (candles[i - 1].high > ih && candles[i - 1].close < ih) idmHighSwept = true
                }
                val il = idmLow
                if (!idmLowSwept && il != null) {
                    if (c.low < il && c.close > il) idmLowSwept = true
                    if (candles[i - 1].low < il && candles[i - 1].close > il) idmLowSwept = true
                }
            }

            // --- Dynamic lookback ---
            val lbHigh = max(1, min(i - structureLowBarIndex, i))
            val lbLow = max(1, min(i - structureHighBarIndex, i))

            var highestInRange = structureHigh
            var highestIdx = structureHighBarIndex
            for (j in max(0, i - lbHigh)..i) {
                if (candles[j].high > highestInRange) {
                    highestInRange = candles[j].high
                    highestIdx = j
                }
            }

            var lowestInRange = structureLow
            var lowestIdx = structureLowBarIndex
            for (j in max(0, i - lbLow)..i) {
                if (candles[j].low < lowestInRange) {
                    lowestInRange = candles[j].low
                    lowestIdx = j
                }
            }

            val isLowBroken = breakLow < structureLow && i > structureLowBarIndex
            val isHighBroken = breakHigh > structureHigh && i > structureHighBarIndex

            if (isLowBroken) {
                val isReversal = direction != StructureDirection.BEARISH
                // IDM validation (idmApplyTo = 'CHoCH' เท่านั้น — ตาม default ต้นฉบับ)
                val needIdm = idmEnabled && isReversal
                if (!needIdm || idmHighSwept) {
                    if (isReversal) {
                        lastEvent = StructureEvent.CHoCH
                        continuationCount = 0
                    } else {
                        continuationCount++
                        lastEvent = if (continuationCount == 1) StructureEvent.SMS else StructureEvent.BMS
                    }
                    lastEventSide = "DOWN"
                    direction = StructureDirection.BEARISH
                    structureHighBarIndex = highestIdx
                    structureHigh = highestInRange
                    structureLowBarIndex = i
                    structureLow = c.low
                }
            } else if (isHighBroken) {
                val isReversal = direction != StructureDirection.BULLISH
                val needIdm = idmEnabled && isReversal
                if (!needIdm || idmLowSwept) {
                    if (isReversal) {
                        lastEvent = StructureEvent.CHoCH
                        continuationCount = 0
                    } else {
                        continuationCount++
                        lastEvent = if (continuationCount == 1) StructureEvent.SMS else StructureEvent.BMS
                    }
                    lastEventSide = "UP"
                    direction = StructureDirection.BULLISH
                    structureLowBarIndex = lowestIdx
                    structureLow = lowestInRange
                    structureHighBarIndex = i
                    structureHigh = c.high
                }
            } else {
                // trail extremes
                when (direction) {
                    StructureDirection.BULLISH -> if (c.high > structureHigh) {
                        structureHigh = c.high; structureHighBarIndex = i
                    }
                    StructureDirection.BEARISH -> if (c.low < structureLow) {
                        structureLow = c.low; structureLowBarIndex = i
                    }
                    StructureDirection.INIT -> {
                        if (c.high > structureHigh) { structureHigh = c.high; structureHighBarIndex = i }
                        if (c.low < structureLow) { structureLow = c.low; structureLowBarIndex = i }
                    }
                }
            }
        }

        return MarketStructure(
            direction, structureHigh, structureLow,
            structureHighBarIndex, structureLowBarIndex,
            lastEvent, lastEventSide, continuationCount,
            idmHigh, idmLow, idmHighSwept, idmLowSwept
        )
    }
}

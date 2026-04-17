package com.example.personalaibot.tools.trading

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Bridge for communicating between Kotlin SMC objects and JS Chart Engine
 */
object ChartDataBridge {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class JsCandle(
        val time: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double
    )

    @Serializable
    data class JsSmcZone(
        val type: String, // OB, FVG, LIQ
        val top: Double,
        val bottom: Double,
        val startTime: Long,
        val endTime: Long,
        val color: String
    )

    /**
     * Convert List<Candle> to JS-ready JSON
     */
    fun candlesToJson(candles: List<Candle>): String {
        val jsCandles = candles.map { 
            JsCandle(
                time  = it.timestamp / 1000, 
                open  = it.open,
                high  = it.high,
                low   = it.low,
                close = it.close
            )
        }
        return json.encodeToString(jsCandles)
    }

    /**
     * Convert SMC Result to drawing commands
     */
    fun smcResultToZonesJson(result: SmcAnalysisResult, candles: List<Candle>): String {
        val zones = mutableListOf<JsSmcZone>()
        val latestTime = (candles.lastOrNull()?.timestamp ?: 0L) / 1000
        val fallbackStartTime = latestTime - 3600

        fun normalizeZone(
            type: String,
            top: Double,
            bottom: Double,
            rawStart: Long,
            rawEnd: Long,
            color: String
        ): JsSmcZone {
            val normalizedTop = maxOf(top, bottom)
            val normalizedBottom = minOf(top, bottom)
            val startTime = if (rawStart > 0L) rawStart else fallbackStartTime
            val endTime = if (rawEnd > startTime) rawEnd else latestTime
            return JsSmcZone(
                type = type,
                top = normalizedTop,
                bottom = normalizedBottom,
                startTime = startTime,
                endTime = endTime,
                color = color
            )
        }

        // 1. Add Order Blocks
        result.bullishOBs.forEach { ob ->
            zones.add(
                normalizeZone(
                    type = "OB_BULL",
                    top = ob.top,
                    bottom = ob.bottom,
                    rawStart = ob.timestamp / 1000,
                    rawEnd = latestTime,
                    color = "rgba(38, 166, 154, 0.18)"
                )
            )
        }
        result.bearishOBs.forEach { ob ->
            zones.add(
                normalizeZone(
                    type = "OB_BEAR",
                    top = ob.top,
                    bottom = ob.bottom,
                    rawStart = ob.timestamp / 1000,
                    rawEnd = latestTime,
                    color = "rgba(239, 83, 80, 0.18)"
                )
            )
        }

        // 2. Add FVGs
        result.fvgs.forEach { fvg ->
            val color = if (fvg.isBullish) "rgba(0, 188, 212, 0.14)" else "rgba(255, 171, 0, 0.14)"
            zones.add(
                normalizeZone(
                    type = "FVG",
                    top = fvg.top,
                    bottom = fvg.bottom,
                    rawStart = fvg.timestamp / 1000,
                    rawEnd = latestTime,
                    color = color
                )
            )
        }

        return json.encodeToString(zones)
    }
}

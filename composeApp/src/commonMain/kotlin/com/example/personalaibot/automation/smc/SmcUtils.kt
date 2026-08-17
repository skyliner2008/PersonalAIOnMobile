package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.max

/** ATR แบบ Wilder's smoothing (ตรงกับ Pine ta.atr และต้นฉบับ TS) */
internal fun computeAtr(candles: List<Candle>, period: Int = 14): Double {
    if (candles.isEmpty()) return 0.0
    if (candles.size < period + 1) {
        return candles.sumOf { it.high - it.low } / candles.size
    }
    var atr = 0.0
    for (i in 1..period) atr += trueRange(candles[i], candles[i - 1])
    atr /= period
    for (i in period + 1 until candles.size) {
        atr = (atr * (period - 1) + trueRange(candles[i], candles[i - 1])) / period
    }
    return atr
}

private fun trueRange(c: Candle, p: Candle): Double =
    max(c.high - c.low, max(abs(c.high - p.close), abs(c.low - p.close)))

/** Swing high/low แบบ ta.pivothigh/pivotlow — ยืนยันด้วย len แท่งซ้ายขวา */
internal fun detectSwings(candles: List<Candle>, len: Int = 5): Pair<List<SwingPoint>, List<SwingPoint>> {
    val highs = mutableListOf<SwingPoint>()
    val lows = mutableListOf<SwingPoint>()
    for (i in len until candles.size - len) {
        val h = candles[i].high
        val l = candles[i].low
        var isHigh = true
        var isLow = true
        for (j in i - len..i + len) {
            if (j == i) continue
            if (candles[j].high >= h) isHigh = false
            if (candles[j].low <= l) isLow = false
        }
        if (isHigh) highs += SwingPoint(i, h)
        if (isLow) lows += SwingPoint(i, l)
    }
    return highs to lows
}

internal fun candleMax(c: Candle): Double = max(c.open, c.close)
internal fun candleMin(c: Candle): Double = minOf(c.open, c.close)
internal fun isBearish(c: Candle): Boolean = c.close < c.open
internal fun isBullish(c: Candle): Boolean = c.close > c.open
internal fun bodySize(c: Candle): Double = abs(c.close - c.open)

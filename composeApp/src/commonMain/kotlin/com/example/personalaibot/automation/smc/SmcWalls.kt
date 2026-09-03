package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class SmcWallKind { ORDER_BLOCK, FVG }
enum class SmcSignalConfirmation { NONE, LIQUIDITY_SWEEP, LIQUIDITY_RUN }

data class SmcWall(
    val kind: SmcWallKind, val isBull: Boolean, val top: Double, val bottom: Double,
    val barIndex: Int, val freshness: Int, val confluence: Int, val active: Boolean = true
) { val midpoint: Double get() = (top + bottom) * 0.5 }

data class SmcConfluence(
    val side: String, val score: Int, val liquidityStars: Int, val obCount: Int, val fvgCount: Int,
    val nearestDistance: Double, val reasons: List<String>,
    val confirmation: SmcSignalConfirmation = SmcSignalConfirmation.NONE
)

internal object SmcWallBuilder {
    fun build(candles: List<Candle>, bullObs: List<SmcOrderBlock>, bearObs: List<SmcOrderBlock>, fvgs: List<Fvg>, liquidity: List<LiquidityZone>, price: Double, atr: Double): Pair<List<SmcWall>, List<SmcConfluence>> {
        if (candles.isEmpty()) return emptyList<SmcWall>() to emptyList()
        val tolerance = max(price * 0.001, atr * 0.25)
        val raw = buildList {
            bullObs.filter { !it.mitigated && !it.invalidated }.forEach { add(SmcWall(SmcWallKind.ORDER_BLOCK, true, it.top, it.bottom, it.barIndex, candles.lastIndex - it.barIndex, 0)) }
            bearObs.filter { !it.mitigated && !it.invalidated }.forEach { add(SmcWall(SmcWallKind.ORDER_BLOCK, false, it.top, it.bottom, it.barIndex, candles.lastIndex - it.barIndex, 0)) }
            fvgs.filter { !it.mitigated }.forEach { add(SmcWall(SmcWallKind.FVG, it.isBull, it.top, it.bottom, it.barIndex, candles.lastIndex - it.barIndex, 0)) }
        }
        val walls = raw.map { wall ->
            val overlap = raw.count { it !== wall && it.isBull == wall.isBull && it.kind != wall.kind && overlaps(wall, it, tolerance) }.coerceAtMost(2)
            val liq = liquidity.filter { it.isHigh != wall.isBull && abs(it.price - wall.midpoint) <= tolerance }.maxOfOrNull { it.confluenceStars } ?: 0
            wall.copy(confluence = min(5, 1 + overlap + liq))
        }.sortedWith(compareByDescending<SmcWall> { it.confluence }.thenBy { it.freshness }.thenByDescending { it.barIndex })
        val events = detectEvents(candles, liquidity, atr)
        val signals = listOf(true, false).map { bull ->
            val side = walls.filter { it.isBull == bull }
            val liq = liquidity.filter { it.isHigh != bull }.maxOfOrNull { it.confluenceStars } ?: 0
            val ob = side.count { it.kind == SmcWallKind.ORDER_BLOCK }
            val fvg = side.count { it.kind == SmcWallKind.FVG }
            val near = side.minOfOrNull { distance(price, it) } ?: Double.POSITIVE_INFINITY
            val overlap = side.count { w -> side.any { it !== w && it.kind != w.kind && overlaps(w, it, tolerance) } }.coerceAtMost(2)
            val event = if (bull) events.first else events.second
            val score = min(10, (if (side.isNotEmpty()) 2 else 0) + min(3, liq) + overlap + min(2, fvg) + if (near <= tolerance) 1 else 0 + if (event != SmcSignalConfirmation.NONE) 2 else 0)
            val reasons = buildList {
                if (ob > 0) add("OB")
                if (fvg > 0) add("FVG")
                if (liq > 0) add("LIQ=${liq}★")
                if (overlap > 0) add("OB+FVG overlap")
                if (near <= tolerance) add("price near wall")
                if (event != SmcSignalConfirmation.NONE) add(event.name)
            }
            SmcConfluence(if (bull) "BULL" else "BEAR", score, liq, ob, fvg, near, reasons, event)
        }
        return walls to signals
    }

    private fun detectEvents(candles: List<Candle>, zones: List<LiquidityZone>, atr: Double): Pair<SmcSignalConfirmation, SmcSignalConfirmation> {
        if (candles.size < 2 || zones.isEmpty()) return SmcSignalConfirmation.NONE to SmcSignalConfirmation.NONE
        val c = candles.last(); val tol = max(atr * 0.10, (c.high - c.low) * 0.05)
        val high = zones.filter { it.isHigh }.minByOrNull { abs(it.price - c.high) }
        val low = zones.filter { !it.isHigh }.minByOrNull { abs(it.price - c.low) }
        fun classify(level: Double, highSide: Boolean): SmcSignalConfirmation {
            val pierced = if (highSide) c.high > level + tol else c.low < level - tol
            if (!pierced) return SmcSignalConfirmation.NONE
            val reclaim = if (highSide) c.close < level else c.close > level
            return if (reclaim) SmcSignalConfirmation.LIQUIDITY_SWEEP else SmcSignalConfirmation.LIQUIDITY_RUN
        }
        return (low?.let { classify(it.price, false) } ?: SmcSignalConfirmation.NONE) to (high?.let { classify(it.price, true) } ?: SmcSignalConfirmation.NONE)
    }
    private fun overlaps(a: SmcWall, b: SmcWall, tolerance: Double): Boolean = min(a.top, b.top) + tolerance >= max(a.bottom, b.bottom)
    private fun distance(price: Double, wall: SmcWall): Double = when { price < wall.bottom -> wall.bottom - price; price > wall.top -> price - wall.top; else -> 0.0 }
}

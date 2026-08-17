package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.min

/**
 * Liquidity Zones — port จาก mt5-core-server liquidityZones.ts (Pine V8.3)
 * Equal Highs/Lows + Swing liquidity + confluence stars (0-5★)
 */
internal object LiquidityZones {

    fun detect(
        candles: List<Candle>,
        lookback: Int = 10,
        equalThresholdPct: Double = 0.1,
        swingLength: Int = 10,
        maxZones: Int = 50
    ): List<LiquidityZone> {
        if (candles.size < 20) return emptyList()
        val zones = mutableListOf<LiquidityZone>()
        val n = candles.size

        // 1. Equal Highs
        for (i in 1..min(lookback, n - 1)) {
            val ch = candles[n - 1 - i].high
            val thr = ch * (equalThresholdPct / 100.0)
            var eqCount = 0
            for (j in i + 1..min(i + lookback, n - 1)) {
                val other = candles[n - 1 - j].high
                if (abs(other - ch) <= thr) eqCount++
            }
            if (eqCount >= 1 && zones.none { it.isHigh && abs(it.price - ch) <= thr }) {
                zones += LiquidityZone(ch, n - 1 - i, isHigh = true, strength = eqCount, source = "EQUAL_HL")
            }
        }

        // 2. Equal Lows
        for (i in 1..min(lookback, n - 1)) {
            val cl = candles[n - 1 - i].low
            val thr = cl * (equalThresholdPct / 100.0)
            var eqCount = 0
            for (j in i + 1..min(i + lookback, n - 1)) {
                val other = candles[n - 1 - j].low
                if (abs(other - cl) <= thr) eqCount++
            }
            if (eqCount >= 1 && zones.none { !it.isHigh && abs(it.price - cl) <= thr }) {
                zones += LiquidityZone(cl, n - 1 - i, isHigh = false, strength = eqCount, source = "EQUAL_HL")
            }
        }

        // 3. Swing H/L liquidity (5 จุดล่าสุด)
        val (highs, lows) = detectSwings(candles, swingLength)
        for (sh in highs.takeLast(5)) {
            if (zones.none { it.isHigh && abs(it.price - sh.price) < sh.price * 0.001 }) {
                zones += LiquidityZone(sh.price, sh.index, isHigh = true, strength = 2, source = "SWING")
            }
        }
        for (sl in lows.takeLast(5)) {
            if (zones.none { !it.isHigh && abs(it.price - sl.price) < sl.price * 0.001 }) {
                zones += LiquidityZone(sl.price, sl.index, isHigh = false, strength = 2, source = "SWING")
            }
        }

        // 4. mark swept ด้วยแท่งล่าสุด แล้วตัดทิ้ง
        val last = candles[n - 1]
        for (z in zones) {
            if (z.isHigh && last.high > z.price) z.swept = true
            if (!z.isHigh && last.low < z.price) z.swept = true
        }

        return zones.filter { !it.swept }.take(maxZones)
    }

    /** Confluence score 0-5★ — ตรงต้นฉบับ: base 1 + OB overlap(+2) + structure(+1) + PD(+1) + trend(+1) */
    fun scoreConfluence(
        zone: LiquidityZone,
        bullObs: List<SmcOrderBlock>,
        bearObs: List<SmcOrderBlock>,
        structure: MarketStructure,
        pdZone: PremiumDiscountZone,
        atr: Double
    ): Int {
        var score = 1

        val obs = if (zone.isHigh) bearObs else bullObs
        if (obs.any { zone.price <= it.top * 1.001 && zone.price >= it.bottom * 0.999 }) score += 2

        if (abs(zone.price - structure.structureHigh) < atr * 0.5 ||
            abs(zone.price - structure.structureLow) < atr * 0.5
        ) score += 1

        if (zone.isHigh && pdZone == PremiumDiscountZone.PREMIUM) score += 1
        if (!zone.isHigh && pdZone == PremiumDiscountZone.DISCOUNT) score += 1

        if ((zone.isHigh && structure.direction == StructureDirection.BEARISH) ||
            (!zone.isHigh && structure.direction == StructureDirection.BULLISH)
        ) score += 1

        return min(5, score)
    }
}

package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle

/**
 * SmcEngine — จุดเข้าหลัก: รวมทุก module เป็น SmcSnapshot เดียว
 * port จาก mt5-core-server analyzers/smc/index.ts (buildSmcSnapshot)
 *
 * บนมือถือใช้ TF เดียว (ไม่มี MTF sweep aggregate) — snapshot จากชุดแท่งเดียว
 */
object SmcEngine {

    fun buildSnapshot(candles: List<Candle>, symbol: String, timeframe: String): SmcSnapshot {
        val lastPrice = candles.lastOrNull()?.close ?: 0.0
        if (candles.size < 30) {
            return SmcSnapshot(
                activeFvgs = emptyList(), bullObs = emptyList(), bearObs = emptyList(),
                liquidityZones = emptyList(), structure = emptyStructure(),
                attackForces = emptyList(),
                premiumDiscount = PremiumDiscount(PremiumDiscountZone.EQ, 50.0, lastPrice, lastPrice, lastPrice),
                swingHighs = emptyList(), swingLows = emptyList(),
                symbol = symbol, timeframe = timeframe, lastPrice = lastPrice
            )
        }

        val atr = computeAtr(candles)

        // 1. Market Structure
        val structure = MarketStructureDetector.detect(candles)

        // 2. FVG
        val allFvgs = FvgDetection.detect(candles)
        val activeFvgs = allFvgs.filter { !it.mitigated }

        // 3. Order Blocks
        val (bullObs, bearObs) = OrderBlockDetection.detect(candles, allFvgs, structure.direction)

        // 4. Premium/Discount
        val premiumDiscount = classifyPremiumDiscount(structure, lastPrice)

        // 5. Liquidity Zones + confluence scoring (เรียงดาวมากสุดก่อน)
        val zones = LiquidityZones.detect(candles).toMutableList()
        for (z in zones) {
            z.confluenceStars = LiquidityZones.scoreConfluence(
                z, bullObs, bearObs, structure, premiumDiscount.zone, atr
            )
        }
        zones.sortByDescending { it.confluenceStars }

        // 6. Attack Force
        val attackForces = SweepDetection.detectAttackForce(candles)

        // 7. Swing points (5 จุดล่าสุด)
        val (highs, lows) = detectSwings(candles, 5)
        val swingHighs = highs.takeLast(5).map { it.price }
        val swingLows = lows.takeLast(5).map { it.price }

        return SmcSnapshot(
            activeFvgs = activeFvgs,
            bullObs = bullObs, bearObs = bearObs,
            liquidityZones = zones,
            structure = structure,
            attackForces = attackForces,
            premiumDiscount = premiumDiscount,
            swingHighs = swingHighs, swingLows = swingLows,
            symbol = symbol, timeframe = timeframe, lastPrice = lastPrice
        )
    }

    private fun classifyPremiumDiscount(structure: MarketStructure, currentPrice: Double): PremiumDiscount {
        val hi = structure.structureHigh
        val lo = structure.structureLow
        if (hi <= 0 || lo <= 0 || hi <= lo) {
            return PremiumDiscount(PremiumDiscountZone.EQ, 50.0, currentPrice, currentPrice, currentPrice)
        }
        val range = hi - lo
        val equilibrium = lo + range * 0.5
        val pct = ((currentPrice - lo) / range) * 100.0
        val zone = when {
            pct > 62 -> PremiumDiscountZone.PREMIUM
            pct < 38 -> PremiumDiscountZone.DISCOUNT
            else -> PremiumDiscountZone.EQ
        }
        return PremiumDiscount(zone, Math.round(pct * 10) / 10.0, hi, lo, equilibrium)
    }
}

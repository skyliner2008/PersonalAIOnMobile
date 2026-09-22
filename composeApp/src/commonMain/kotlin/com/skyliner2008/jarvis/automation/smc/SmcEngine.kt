package com.skyliner2008.jarvis.automation.smc

import com.skyliner2008.jarvis.tools.trading.Candle

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

        // 2. FVG — ช่วงล่าสุด (ค่าเริ่มต้น 30 แท่ง) สำหรับจุดเข้าเทรด/กำแพง
        val recentFvgs = FvgDetection.detect(candles)
        val activeFvgs = recentFvgs.filter { !it.mitigated }

        // 3. Order Blocks
        // OB ต้องการ FVG อยู่ห่างไม่เกิน 5 แท่งจากแท่ง OB และค้นหา swing ทั้งหน้าต่าง (300+ แท่ง)
        // ถ้าใช้ FVG แค่ 30 แท่งท้าย OB ที่เก่ากว่านั้นจะหา FVG คู่ไม่เจอ → ไม่เกิด OB เลย
        // (วัดบนแท่ง BTC จริง 300 แท่ง: FVG 3 อันอยู่ท้ายสุด → OB 0 อัน; ค้นทั้งหน้าต่างได้ FVG 42 อัน → OB 8 อัน)
        // ผลคือทั้ง setup แบบ OB, กำแพง OB, คะแนน confluence ของโซน และระดับ OB ในภาพตลาด ไม่เคยทำงานเลย
        val obFvgs = FvgDetection.detect(candles, lookback = candles.size)
        val (bullObs, bearObs) = OrderBlockDetection.detect(candles, obFvgs, structure.direction)

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

        // 6. Explicit OB/FVG walls + unified confluence decision layer
        val (walls, confluence) = SmcWallBuilder.build(
            candles, bullObs, bearObs, activeFvgs, zones, lastPrice, atr
        )

        // 7. Attack Force
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
            symbol = symbol, timeframe = timeframe, lastPrice = lastPrice,
            walls = walls, confluence = confluence
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

package com.example.personalaibot.tools.trading

import io.ktor.client.*

/**
 * SmcToolExecutor — Execute SMC tool calls และ format ผลลัพธ์เป็น text
 * สำหรับส่งกลับให้ Gemini อ่านและอธิบายให้ผู้ใช้
 *
 * แปลงจาก indicator "SMC & Multi-TF Order Blocks Sweeps V8.3"
 */
class SmcToolExecutor(private val client: HttpClient) {

    private val api = SmcApiService(client)
    private val priceApi = TradingApiService(client)

    /**
     * Execute SMC tool call
     */
    suspend fun execute(toolName: String, args: Map<String, String>): String {
        return try {
            when (toolName) {
                "trading_smc_analysis"    -> executeSmcAnalysis(args)
                "trading_smc_sweeps"      -> executeSmcSweeps(args)
                "trading_smc_liquidity"   -> executeSmcLiquidity(args)
                "trading_smc_orderblocks" -> executeSmcOrderBlocks(args)
                "trading_smc_structure"   -> executeSmcStructure(args)
                else                      -> "ไม่พบ SMC tool: $toolName"
            }
        } catch (e: StrictSourceMismatchException) {
            "❌ ${e.message}"
        }
    }

    // ─── 1. Full SMC Analysis Dashboard ──────────────────────────────────────

    private suspend fun executeSmcAnalysis(args: Map<String, String>): String {
        val symbol   = args["symbol"] ?: return "กรุณาระบุ symbol เช่น BTCUSDT"
        // Pine SMC V11.29 is designed around M15 as the primary chart/use-case TF.
        // Keep explicit caller overrides intact, but make the implicit analysis path use M15
        // instead of silently falling back to H1.
        val interval = args["interval"]?.trim()?.takeIf { it.isNotEmpty() } ?: "15m"
        val strictTv = args["strict_tv"]?.toBooleanStrictOrNull() ?: true

        val result = api.getSmcAnalysis(symbol, interval, strictTvSource = strictTv)
            ?: return "❌ ดึงข้อมูล SMC ไม่ได้สำหรับ ${symbol.uppercase()} — กรุณาตรวจสอบ symbol"

        val priceStr = formatPrice(result.currentPrice)
        val atrStr   = formatPrice(result.atr)

        val dirEmoji = when (result.structureDirection) {
            "BULLISH" -> "🟢"
            "BEARISH" -> "🔴"
            else      -> "⚪"
        }

        val eventStr = when (result.lastStructureEvent) {
            "BOS_UP"     -> "📈 BOS ขึ้น (BoS Bullish)"
            "BOS_DOWN"   -> "📉 BOS ลง (BoS Bearish)"
            "CHOCH_UP"   -> "🔄 CHoCH ขึ้น (CHoCH → Bullish)"
            "CHOCH_DOWN" -> "🔄 CHoCH ลง (CHoCH → Bearish)"
            else          -> "— ไม่มี event ล่าสุด"
        }

        val zoneEmoji = when (result.priceZone) {
            "PREMIUM"  -> "🔴 PREMIUM (แพง — บริเวณ Short)"
            "DISCOUNT" -> "🟢 DISCOUNT (ถูก — บริเวณ Long)"
            else       -> "🟡 EQUILIBRIUM (กลาง)"
        }

        return buildString {
            appendLine("=".repeat(29))
            appendLine("🧠 **SMC Analysis — ${result.symbol} ($interval)**")
            appendLine("💰 ราคาปัจจุบัน: **$priceStr**|ATR: $atrStr")
            if (result.attackForce) appendLine("⚡ **Attack Force!** — Momentum สูงผิดปกติ (>2x ATR)")
            appendLine("")

            appendLine("**📐 Market Structure**")
            appendLine("  $dirEmoji Trend: **${result.structureDirection}**")
            appendLine("  Structure High: ${formatPrice(result.structureHigh)}")
            appendLine("  Structure Low:  ${formatPrice(result.structureLow)}")
            appendLine("  Last Event: $eventStr")
            appendLine("")

            appendLine("**📊 Premium / Discount Zones**")
            appendLine("  Zone ปัจจุบัน: $zoneEmoji")
            appendLine("  Premium  ≥ ${formatPrice(result.premiumBot)}")
            appendLine("  Equilibrium: ${formatPrice(result.equilibrium)}")
            appendLine("  Discount ≤ ${formatPrice(result.discountTop)}")
            appendLine("")

            appendLine("**🟢 Bullish OB (Demand Zones)**")
            if (result.bullishOBs.isEmpty()) {
                appendLine("  — ไม่พบ Active Bullish OB")
            } else {
                result.bullishOBs.reversed().forEach { ob ->
                    val mid = (ob.top + ob.bottom) / 2
                    appendLine("  🟢 ${formatPrice(ob.top)} — ${formatPrice(ob.bottom)}  (mid: ${formatPrice(mid)})${if (ob.hasFVG) " ✅FVG" else ""}")
                }
            }
            appendLine("**🔴 Bearish OB (Supply Zones)**")
            if (result.bearishOBs.isEmpty()) {
                appendLine("  — ไม่พบ Active Bearish OB")
            } else {
                result.bearishOBs.reversed().forEach { ob ->
                    val mid = (ob.top + ob.bottom) / 2
                    appendLine("  🔴 ${formatPrice(ob.top)} — ${formatPrice(ob.bottom)}  (mid: ${formatPrice(mid)})${if (ob.hasFVG) " ✅FVG" else ""}")
                }
            }

            appendLine("")
            appendLine("**⬛ Fair Value Gaps (FVG)**")
            val recentFVGs = result.fvgs.takeLast(5)
            if (recentFVGs.isEmpty()) {
                appendLine("  — ไม่พบ FVG ล่าสุด")
            } else {
                recentFVGs.reversed().forEach { fvg ->
                    val icon = if (fvg.isBullish) "🟢 Bull" else "🔴 Bear"
                    appendLine("  $icon FVG: ${formatPrice(fvg.bottom)} — ${formatPrice(fvg.top)}  (size: ${formatPrice(fvg.size)})")
                }
            }

            appendLine("")
            appendLine("**💧 Liquidity Zones (Pending Sweeps)**")
            // Liquidity is spatial information: classify by semantic type AND position
            // relative to current price, never by strength score alone.
            val eqhAbove = result.liquidityZones
                .filter { it.isHigh && it.price > result.currentPrice }
                .sortedBy { it.price }
            val eqlBelow = result.liquidityZones
                .filter { !it.isHigh && it.price < result.currentPrice }
                .sortedByDescending { it.price }
            val invalidHighs = result.liquidityZones.count { it.isHigh && it.price <= result.currentPrice }
            val invalidLows = result.liquidityZones.count { !it.isHigh && it.price >= result.currentPrice }

            appendLine("  🔻 **Buy-side Liquidity / EQH เหนือราคา**")
            if (eqhAbove.isEmpty()) {
                appendLine("    — ไม่พบ EQH เหนือราคาปัจจุบัน")
            } else {
                eqhAbove.take(6).forEach { z ->
                    appendLine("    🔻 EQH ${formatPrice(z.price)}  ${starsStr(z.confluenceScore)} (touches: ${z.strength})")
                }
            }
            appendLine("  ───────── ${formatPrice(result.currentPrice)} CURRENT PRICE")
            appendLine("  🔺 **Sell-side Liquidity / EQL ใต้ราคา**")
            if (eqlBelow.isEmpty()) {
                appendLine("    — ไม่พบ EQL ใต้ราคาปัจจุบัน")
            } else {
                eqlBelow.take(6).forEach { z ->
                    appendLine("    🔺 EQL ${formatPrice(z.price)}  ${starsStr(z.confluenceScore)} (touches: ${z.strength})")
                }
            }
            if (invalidHighs + invalidLows > 0) {
                appendLine("  ⚠️ ตรวจพบ liquidity ที่อยู่ผิดฝั่งราคา: ${invalidHighs + invalidLows} ระดับ")
            }
            appendLine("=".repeat(29))

            val bullOBNearPrice = result.bullishOBs.any {
                result.currentPrice >= it.bottom * 0.995 && result.currentPrice <= it.top * 1.005
            }
            val bearOBNearPrice = result.bearishOBs.any {
                result.currentPrice >= it.bottom * 0.995 && result.currentPrice <= it.top * 1.005
            }

            val bias = when {
                result.structureDirection == "BULLISH" && result.priceZone == "DISCOUNT" && bullOBNearPrice ->
                    "🟢 **STRONG BUY BIAS** — Bullish structure + Discount zone + ราคาอยู่ใน Bullish OB"
                result.structureDirection == "BEARISH" && result.priceZone == "PREMIUM" && bearOBNearPrice ->
                    "🔴 **STRONG SELL BIAS** — Bearish structure + Premium zone + ราคาอยู่ใน Bearish OB"
                result.structureDirection == "BULLISH" && result.priceZone == "DISCOUNT" ->
                    "🟢 **BUY BIAS** — Bullish structure + Discount zone (รอ pullback ถึง OB)"
                result.structureDirection == "BEARISH" && result.priceZone == "PREMIUM" ->
                    "🔴 **SELL BIAS** — Bearish structure + Premium zone (รอ retest ถึง OB)"
                result.priceZone == "PREMIUM" ->
                    "🟡 **CAUTION** — ราคาอยู่ใน Premium zone — ระวัง Short opportunity"
                result.priceZone == "DISCOUNT" ->
                    "🟡 **WATCH** — ราคาอยู่ใน Discount zone — รอ Long setup"
                else ->
                    "⚪ **NEUTRAL** — รอ structure break หรือ OB retest ที่ชัดเจนกว่านี้"
            }
            appendLine("")
            appendLine("**🎯 SMC Bias: $bias**")
            appendLine("[Data] candle_source=${result.candleSource} | price_source=${result.priceSource} | bars=${result.candlesCount} | strict_tv=$strictTv")
        }
    }

    // ─── 2. MTF Sweeps ────────────────────────────────────────────────────────

    private suspend fun executeSmcSweeps(args: Map<String, String>): String {
        val symbol = args["symbol"] ?: return "กรุณาระบุ symbol เช่น BTCUSDT"
        val strictTv = args["strict_tv"]?.toBooleanStrictOrNull() ?: true
        val sweepsMap = api.getMTFSweeps(symbol, strictTvSource = strictTv)

        return buildString {
            appendLine("🌊 **MTF Sweeps — ${symbol.uppercase()}**")
            appendLine("=".repeat(29))
            appendLine("Sweep = ราคา wick ทะลุ OB แล้ว reclaim กลับ >50%")
            appendLine("=".repeat(29))

            if (sweepsMap.isEmpty()) {
                appendLine("✅ ไม่พบ Sweep signal ล่าสุดในทุก Timeframe")
                appendLine("(หมายความว่าตลาดกำลัง trend ปกติ ไม่มีการ manipulate)")
            } else {
                var totalBull = 0
                var totalBear = 0

                for ((tf, signals) in sweepsMap.entries.sortedBy { tfOrder(it.key) }) {
                    appendLine("**⏱ $tf:**")
                    for (sweep in signals) {
                        if (sweep.direction == "BULLISH") {
                            totalBull++
                            appendLine("  🟢 Bullish Sweep — ราคา: ${formatPrice(sweep.price)}")
                            appendLine("     OB Zone: ${formatPrice(sweep.obBottom)} — ${formatPrice(sweep.obTop)}")
                            appendLine("     📌 ราคา wick ลงใต้ Bearish OB แล้ว close กลับขึ้น → Long opportunity")
                        } else {
                            totalBear++
                            appendLine("  🔴 Bearish Sweep — ราคา: ${formatPrice(sweep.price)}")
                            appendLine("     OB Zone: ${formatPrice(sweep.obBottom)} — ${formatPrice(sweep.obTop)}")
                            appendLine("     📌 ราคา wick ขึ้นเหนือ Bullish OB แล้ว close ลง → Short opportunity")
                        }
                    }
                }

                appendLine("=".repeat(29))
                val sweepBias = when {
                    totalBull > totalBear -> "🟢 Bullish Sweep Dominant ($totalBull bull vs $totalBear bear)"
                    totalBear > totalBull -> "🔴 Bearish Sweep Dominant ($totalBear bear vs $totalBull bull)"
                    else -> "⚪ Mixed Sweeps — ระวังความผันผวนสูง"
                }
                appendLine("**Sweep Bias: $sweepBias**")
            }
        }
    }

    // ─── 3. MTF Liquidity Zones ───────────────────────────────────────────────

    private suspend fun executeSmcLiquidity(args: Map<String, String>): String {
        val symbol = args["symbol"] ?: return "กรุณาระบุ symbol เช่น BTCUSDT"
        val strictTv = args["strict_tv"]?.toBooleanStrictOrNull() ?: true
        val liqMap = api.getMTFLiquidity(symbol, strictTvSource = strictTv)

        // Collect all zones with tags for merging
        val allHighs = mutableListOf<Pair<Double, String>>()
        val allLows  = mutableListOf<Pair<Double, String>>()

        for ((tf, zones) in liqMap) {
            for (z in zones) {
                if (z.isHigh) allHighs.add(z.price to tf)
                else          allLows.add(z.price to tf)
            }
        }

        // Pine V11.29: base merge tolerance is 0.02%; D1/W1 use 2.5x (0.05%).
        val mergedHighs = mergeLevels(allHighs, 0.02)
        val mergedLows  = mergeLevels(allLows, 0.02)

        val currentPrice = priceApi.getBestEffortPrice(symbol)["price"]?.toDoubleOrNull()
        val highs = mergedHighs.sortedByDescending { it.first }
        val lows = mergedLows.sortedByDescending { it.first }

        return buildString {
            appendLine("💧 **MTF Liquidity Zones — ${symbol.uppercase()}**")
            appendLine("=".repeat(29))
            if (currentPrice != null) appendLine("💰 ราคาปัจจุบัน: ${formatPrice(currentPrice)}")
            appendLine("EQH = Buy-side Liquidity (เหนือราคา)")
            appendLine("EQL = Sell-side Liquidity (ใต้ราคา)")
            appendLine("=".repeat(29))

            val above = if (currentPrice != null) highs.filter { it.first > currentPrice } else highs
            val below = if (currentPrice != null) lows.filter { it.first < currentPrice } else lows

            appendLine("**🔻 Liquidity เหนือราคา — Buy-side / EQH:**")
            if (above.isEmpty()) appendLine("  — ไม่พบ EQH เหนือราคาปัจจุบัน")
            else above.take(8).forEach { (price, tags) ->
                appendLine("  🔻 EQH ${formatPrice(price)}  [$tags]")
            }

            if (currentPrice != null) appendLine("--- ${formatPrice(currentPrice)}")

            appendLine("**🔺 Liquidity ใต้ราคา — Sell-side / EQL:**")
            if (below.isEmpty()) appendLine("  — ไม่พบ EQL ใต้ราคาปัจจุบัน")
            else below.take(8).forEach { (price, tags) ->
                appendLine("  🔺 EQL ${formatPrice(price)}  [$tags]")
            }
            appendLine("")
            appendLine("📌 Wall ถูก merge ตาม Pine V11.29: 0.02% และ D1/W1 ใช้ 0.05%")
        }
    }

    // ─── 4. Order Blocks ──────────────────────────────────────────────────────

    private suspend fun executeSmcOrderBlocks(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "กรุณาระบุ symbol เช่น BTCUSDT"
        val interval = args["interval"] ?: "1h"
        val strictTv = args["strict_tv"]?.toBooleanStrictOrNull() ?: true

        val result = api.getSmcAnalysis(symbol, interval, strictTvSource = strictTv)
            ?: return "❌ ดึงข้อมูล OB ไม่ได้สำหรับ ${symbol.uppercase()}"

        val currentPrice = result.currentPrice

        return buildString {
            appendLine("📦 **Order Blocks — ${result.symbol} ($interval)**")
            appendLine("=".repeat(29))
            appendLine("💰 ราคาปัจจุบัน: ${formatPrice(currentPrice)}")
            appendLine("✅ FVG = มี Fair Value Gap ยืนยัน (Displacement สูง)")
            appendLine("=".repeat(29))

            appendLine("**🟢 Bullish OB (Demand Zones):**")
            if (result.bullishOBs.isEmpty()) {
                appendLine("  — ไม่พบ Active Bullish OB (อาจถูก mitigate ไปแล้ว)")
            } else {
                result.bullishOBs.reversed().forEachIndexed { idx, ob ->
                    val mid = (ob.top + ob.bottom) / 2
                    val distPct = ((currentPrice - ob.top) / currentPrice * 100)
                    val nearTag = if (kotlin.math.abs(distPct) < 1.0) " ⚡ NEAR!" else ""
                    appendLine("  🟢 OB #${idx + 1}:")
                    appendLine("     Top:    ${formatPrice(ob.top)}")
                    appendLine("     Bottom: ${formatPrice(ob.bottom)}")
                    appendLine("     Mid:    ${formatPrice(mid)}")
                    appendLine("     FVG: ${if (ob.hasFVG) "✅ ยืนยัน" else "❌ ไม่มี"}$nearTag")
                    val dist = if (distPct >= 0) "+${"%.2f".format(distPct)}% จากราคาปัจจุบัน"
                               else "${"%.2f".format(distPct)}% จากราคาปัจจุบัน"
                    appendLine("     Distance: $dist")
                }
            }

            appendLine("**🔴 Bearish OB (Supply Zones):**")
            if (result.bearishOBs.isEmpty()) {
                appendLine("  — ไม่พบ Active Bearish OB")
            } else {
                result.bearishOBs.reversed().forEachIndexed { idx, ob ->
                    val mid = (ob.top + ob.bottom) / 2
                    val distPct = ((ob.bottom - currentPrice) / currentPrice * 100)
                    val nearTag = if (kotlin.math.abs(distPct) < 1.0) " ⚡ NEAR!" else ""
                    appendLine("  🔴 OB #${idx + 1}:")
                    appendLine("     Top:    ${formatPrice(ob.top)}")
                    appendLine("     Bottom: ${formatPrice(ob.bottom)}")
                    appendLine("     Mid:    ${formatPrice(mid)}")
                    appendLine("     FVG: ${if (ob.hasFVG) "✅ ยืนยัน" else "❌ ไม่มี"}$nearTag")
                    val dist = if (distPct >= 0) "+${"%.2f".format(distPct)}% จากราคาปัจจุบัน"
                               else "${"%.2f".format(distPct)}% จากราคาปัจจุบัน"
                    appendLine("     Distance: $dist")
                }
            }

            appendLine("=".repeat(29))
            appendLine("📌 OB ที่มี ✅FVG = strong zone (ราคามักเด้งจากจุดนี้)")
            appendLine("📌 OB ที่มี ⚡NEAR = ราคาใกล้ถึงแล้ว ควรระวัง/รอ entry")
        }
    }

    // ─── 5. Market Structure ──────────────────────────────────────────────────

    private suspend fun executeSmcStructure(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "กรุณาระบุ symbol เช่น BTCUSDT"
        val interval = args["interval"] ?: "1h"
        val strictTv = args["strict_tv"]?.toBooleanStrictOrNull() ?: true

        val result = api.getSmcAnalysis(symbol, interval, strictTvSource = strictTv)
            ?: return "❌ ดึงข้อมูล Structure ไม่ได้สำหรับ ${symbol.uppercase()}"

        val dirEmoji = when (result.structureDirection) {
            "BULLISH" -> "🟢"
            "BEARISH" -> "🔴"
            else      -> "⚪"
        }

        val eventStr = when (result.lastStructureEvent) {
            "BOS_UP"    -> "📈 **BOS ขึ้น** — Break of Structure Bullish (ยืนยัน Uptrend)"
            "BOS_DOWN"  -> "📉 **BOS ลง** — Break of Structure Bearish (ยืนยัน Downtrend)"
            "CHOCH_UP"  -> "🔄 **CHoCH ขึ้น** — Change of Character → Bullish Flip (Trend Reversal!)"
            "CHOCH_DOWN"-> "🔄 **CHoCH ลง** — Change of Character → Bearish Flip (Trend Reversal!)"
            else        -> "⚪ ไม่มี structure event ล่าสุด"
        }

        val range = result.structureHigh - result.structureLow
        val currentPrice = result.currentPrice
        val rangePercent = if (range > 0) ((currentPrice - result.structureLow) / range * 100) else 50.0

        val zoneEmoji = when (result.priceZone) {
            "PREMIUM"     -> "🔴 PREMIUM"
            "DISCOUNT"    -> "🟢 DISCOUNT"
            else          -> "🟡 EQUILIBRIUM"
        }

        return buildString {
            appendLine("=".repeat(29))
            appendLine("📐 **Market Structure — ${result.symbol} ($interval)**")
            appendLine("💰 ราคาปัจจุบัน: ${formatPrice(currentPrice)}")
            appendLine("**Structure Direction: $dirEmoji ${result.structureDirection}**")
            appendLine("")
            appendLine("**Last Event:**")
            appendLine("  $eventStr")
            appendLine("")
            appendLine("**Structure Range:**")
            appendLine("  High: ${formatPrice(result.structureHigh)}")
            appendLine("  Low:  ${formatPrice(result.structureLow)}")
            appendLine("  Range: ${formatPrice(range)} (${"%.1f".format(range / result.structureLow * 100)}%)")
            appendLine("")
            appendLine("**Premium / Discount Analysis:**")
            appendLine("  Premium  ≥ ${formatPrice(result.premiumBot)}  (ราคาแพง — zone ของ Smart Money Sell)")
            appendLine("  Equilib.   ${formatPrice(result.equilibrium)}   (50% ของ range)")
            appendLine("  Discount ≤ ${formatPrice(result.discountTop)}  (ราคาถูก — zone ของ Smart Money Buy)")
            appendLine("")
            appendLine("  📍 ราคาอยู่ที่: $zoneEmoji (${"%.1f".format(rangePercent)}% ของ range)")
            appendLine("")
            appendLine("=".repeat(29))

            val advice = when {
                result.structureDirection == "BULLISH" && result.priceZone == "DISCOUNT" ->
                    "✅ **IDEAL LONG SETUP** — Bullish structure + ราคาอยู่ใน Discount\n" +
                    "   รอ Bullish OB หรือ FVG เป็น entry point"
                result.structureDirection == "BEARISH" && result.priceZone == "PREMIUM" ->
                    "✅ **IDEAL SHORT SETUP** — Bearish structure + ราคาอยู่ใน Premium\n" +
                    "   รอ Bearish OB หรือ FVG เป็น entry point"
                result.structureDirection == "BULLISH" && result.priceZone == "PREMIUM" ->
                    "⚠️ **CAUTION** — Bullish structure แต่ราคาอยู่ใน Premium\n" +
                    "   ระวัง pullback — อย่า Chase Long"
                result.structureDirection == "BEARISH" && result.priceZone == "DISCOUNT" ->
                    "⚠️ **CAUTION** — Bearish structure แต่ราคาอยู่ใน Discount\n" +
                    "   ระวัง bounce — อย่า Chase Short"
                result.lastStructureEvent.startsWith("CHOCH") ->
                    "🔄 **REVERSAL ALERT** — CHoCH เพิ่งเกิด รอยืนยัน entry\n" +
                    "   รอ retest ของ structure break ก่อน entry"
                else ->
                    "⚪ **NEUTRAL** — รอ structure ชัดเจนขึ้นก่อน"
            }
            appendLine("**🎯 Analysis: $advice**")
        }
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private fun tfOrder(tf: String): Int = when (tf) {
        "M1" -> 0; "M5" -> 1; "M15" -> 2; "M30" -> 3; "H1" -> 4; "H4" -> 5; else -> 99
    }

    /**
     * Merge nearby price levels using Pine V11.29 wall tolerances.
     *
     * The wider tolerance applies when either side of a candidate cluster comes
     * from D1/W1. Using only the incoming tag made the previous implementation
     * order-dependent (D1 could merge differently depending on iteration order).
     */
    private fun mergeLevels(
        levels: List<Pair<Double, String>>,
        thresholdPct: Double
    ): List<Pair<Double, String>> {
        if (levels.isEmpty()) return emptyList()

        val sorted = levels.sortedBy { it.first }
        val merged = mutableListOf<Pair<Double, String>>()
        var clusterPrices = mutableListOf(sorted.first().first)
        var clusterTags = mutableSetOf(sorted.first().second)

        for (i in 1 until sorted.size) {
            val (price, tag) = sorted[i]
            val clusterAvg = clusterPrices.average()
            val candidateTags = clusterTags + tag
            val effectiveThresholdPct =
                if (candidateTags.any { it == "D1" || it == "W1" }) thresholdPct * 2.5 else thresholdPct
            val tol = clusterAvg * (effectiveThresholdPct / 100.0)

            if (kotlin.math.abs(price - clusterAvg) <= tol) {
                clusterPrices.add(price)
                clusterTags.add(tag)
            } else {
                merged.add(clusterPrices.average() to clusterTags.sorted().joinToString("+"))
                clusterPrices = mutableListOf(price)
                clusterTags = mutableSetOf(tag)
            }
        }

        merged.add(clusterPrices.average() to clusterTags.sorted().joinToString("+"))
        return merged
    }
}

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

    /**
     * Execute SMC tool call
     */
    suspend fun execute(toolName: String, args: Map<String, String>): String {
        return when (toolName) {
            "trading_smc_analysis"    -> executeSmcAnalysis(args)
            "trading_smc_sweeps"      -> executeSmcSweeps(args)
            "trading_smc_liquidity"   -> executeSmcLiquidity(args)
            "trading_smc_orderblocks" -> executeSmcOrderBlocks(args)
            "trading_smc_structure"   -> executeSmcStructure(args)
            else                      -> "ไม่พบ SMC tool: $toolName"
        }
    }

    // ─── 1. Full SMC Analysis Dashboard ──────────────────────────────────────

    private suspend fun executeSmcAnalysis(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "กรุณาระบุ symbol เช่น BTCUSDT"
        val interval = args["interval"] ?: "1h"

        val result = api.getSmcAnalysis(symbol, interval)
            ?: return "❌ ดึงข้อมูล SMC ไม่ได้สำหรับ ${symbol.uppercase()} — กรุณาตรวจสอบ symbol"

        val priceStr = formatPrice(result.currentPrice)
        val atrStr   = formatPrice(result.atr)

        val dirEmoji = when (result.structureDirection) {
            "BULLISH" -> "🟢"
            "BEARISH" -> "🔴"
            else      -> "⚪"
        }

        val eventStr = when (result.lastStructureEvent) {
            "BOS_UP"    -> "📈 BOS ขึ้น (Break of Structure Bullish)"
            "BOS_DOWN"  -> "📉 BOS ลง (Break of Structure Bearish)"
            "CHOCH_UP"  -> "🔄 CHoCH ขึ้น (Trend Flip → Bullish)"
            "CHOCH_DOWN"-> "🔄 CHoCH ลง (Trend Flip → Bearish)"
            else        -> "— ไม่มี event ล่าสุด"
        }

        val zoneEmoji = when (result.priceZone) {
            "PREMIUM"     -> "🔴 PREMIUM (แพง — บริเวณ Short)"
            "DISCOUNT"    -> "🟢 DISCOUNT (ถูก — บริเวณ Long)"
            else          -> "🟡 EQUILIBRIUM (กลาง)"
        }

        return buildString {
            appendLine("🧠 **SMC Analysis — ${result.symbol} ($interval)**")
            appendLine("=".repeat(48))
            appendLine("")
            appendLine("💰 ราคาปัจจุบัน: **$priceStr**  |  ATR: $atrStr")
            if (result.attackForce) appendLine("⚡ **Attack Force!** — Momentum สูงผิดปกติ (>2x ATR)")
            appendLine("")

            // Market Structure
            appendLine("**📐 Market Structure**")
            appendLine("  $dirEmoji Trend: **${result.structureDirection}**")
            appendLine("  Structure High: ${formatPrice(result.structureHigh)}")
            appendLine("  Structure Low:  ${formatPrice(result.structureLow)}")
            appendLine("  Last Event: $eventStr")
            appendLine("")

            // Premium / Discount
            appendLine("**📊 Premium / Discount Zones**")
            appendLine("  Zone ปัจจุบัน: $zoneEmoji")
            appendLine("  Premium  ≥ ${formatPrice(result.premiumBot)}")
            appendLine("  Equilibrium: ${formatPrice(result.equilibrium)}")
            appendLine("  Discount ≤ ${formatPrice(result.discountTop)}")
            appendLine("")

            // Order Blocks
            appendLine("**🟢 Bullish Order Blocks (Demand Zones)**")
            if (result.bullishOBs.isEmpty()) {
                appendLine("  — ไม่พบ Active Bullish OB")
            } else {
                result.bullishOBs.reversed().forEach { ob ->
                    val mid = (ob.top + ob.bottom) / 2
                    appendLine("  🟢 ${formatPrice(ob.top)} — ${formatPrice(ob.bottom)}  (mid: ${formatPrice(mid)})${if (ob.hasFVG) " ✅FVG" else ""}")
                }
            }
            appendLine("")
            appendLine("**🔴 Bearish Order Blocks (Supply Zones)**")
            if (result.bearishOBs.isEmpty()) {
                appendLine("  — ไม่พบ Active Bearish OB")
            } else {
                result.bearishOBs.reversed().forEach { ob ->
                    val mid = (ob.top + ob.bottom) / 2
                    appendLine("  🔴 ${formatPrice(ob.top)} — ${formatPrice(ob.bottom)}  (mid: ${formatPrice(mid)})${if (ob.hasFVG) " ✅FVG" else ""}")
                }
            }
            appendLine("")

            // Fair Value Gaps
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

            // Liquidity Zones
            appendLine("**💧 Liquidity Zones (Pending Sweeps)**")
            val topZones = result.liquidityZones.sortedByDescending { it.confluenceScore }.take(6)
            if (topZones.isEmpty()) {
                appendLine("  — ไม่พบ Liquidity Zone")
            } else {
                topZones.forEach { z ->
                    val icon = if (z.isHigh) "🔺 EQH" else "🔻 EQL"
                    val stars = starsStr(z.confluenceScore)
                    appendLine("  $icon ${formatPrice(z.price)}  $stars (touches: ${z.strength})")
                }
            }
            appendLine("")
            appendLine("─".repeat(48))

            // Trading Bias Summary
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
            appendLine("**🎯 SMC Bias: $bias**")
        }
    }

    // ─── 2. MTF Sweeps ────────────────────────────────────────────────────────

    private suspend fun executeSmcSweeps(args: Map<String, String>): String {
        val symbol = args["symbol"] ?: return "กรุณาระบุ symbol เช่น BTCUSDT"
        val sweepsMap = api.getMTFSweeps(symbol)

        return buildString {
            appendLine("🌊 **MTF Sweeps — ${symbol.uppercase()}**")
            appendLine("=".repeat(45))
            appendLine("")
            appendLine("Sweep = ราคา wick ทะลุ OB แล้ว reclaim กลับ >50%")
            appendLine("─".repeat(45))
            appendLine("")

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
                            appendLine("     📌 ราค wick ลงใต้ Bearish OB แล้ว close กลับขึ้น → Long opportunity")
                        } else {
                            totalBear++
                            appendLine("  🔴 Bearish Sweep — ราคา: ${formatPrice(sweep.price)}")
                            appendLine("     OB Zone: ${formatPrice(sweep.obBottom)} — ${formatPrice(sweep.obTop)}")
                            appendLine("     📌 ราคา wick ขึ้นเหนือ Bullish OB แล้ว close ลง → Short opportunity")
                        }
                    }
                    appendLine("")
                }

                appendLine("─".repeat(45))
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
        val liqMap = api.getMTFLiquidity(symbol)

        // Collect all zones with tags for merging
        val allHighs = mutableListOf<Pair<Double, String>>()
        val allLows  = mutableListOf<Pair<Double, String>>()

        for ((tf, zones) in liqMap) {
            for (z in zones) {
                if (z.isHigh) allHighs.add(z.price to tf)
                else          allLows.add(z.price to tf)
            }
        }

        // Merge nearby levels (within 0.2%)
        val mergedHighs = mergeLevels(allHighs, 0.2)
        val mergedLows  = mergeLevels(allLows, 0.2)

        return buildString {
            appendLine("💧 **MTF Liquidity Zones — ${symbol.uppercase()}**")
            appendLine("=".repeat(48))
            appendLine("")
            appendLine("★★★★★ = OB + Structure + Premium/Discount + Trend")
            appendLine("EQH = Equal High (Sell-side Liquidity)")
            appendLine("EQL = Equal Low  (Buy-side Liquidity)")
            appendLine("─".repeat(48))
            appendLine("")

            appendLine("**🔺 Equal Highs (Resistance / Sell Liquidity):**")
            if (mergedHighs.isEmpty()) {
                appendLine("  — ไม่พบ Equal Highs ที่มีนัยสำคัญ")
            } else {
                mergedHighs.sortedByDescending { it.first }.take(8).forEach { (price, tags) ->
                    appendLine("  🔺 ${formatPrice(price)}  [$tags]")
                }
            }
            appendLine("")

            appendLine("**🔻 Equal Lows (Support / Buy Liquidity):**")
            if (mergedLows.isEmpty()) {
                appendLine("  — ไม่พบ Equal Lows ที่มีนัยสำคัญ")
            } else {
                mergedLows.sortedBy { it.first }.take(8).forEach { (price, tags) ->
                    appendLine("  🔻 ${formatPrice(price)}  [$tags]")
                }
            }
            appendLine("")
            appendLine("─".repeat(48))
            appendLine("📌 Liquidity ที่ถูก tag หลาย TF = Strong level — มีโอกาสสูงที่ราคาจะไป sweep ก่อนกลับทิศ")
        }
    }

    // ─── 4. Order Blocks ──────────────────────────────────────────────────────

    private suspend fun executeSmcOrderBlocks(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "กรุณาระบุ symbol เช่น BTCUSDT"
        val interval = args["interval"] ?: "1h"

        val result = api.getSmcAnalysis(symbol, interval)
            ?: return "❌ ดึงข้อมูล OB ไม่ได้สำหรับ ${symbol.uppercase()}"

        val currentPrice = result.currentPrice

        return buildString {
            appendLine("📦 **Order Blocks — ${result.symbol} ($interval)**")
            appendLine("=".repeat(45))
            appendLine("💰 ราคาปัจจุบัน: ${formatPrice(currentPrice)}")
            appendLine("✅ FVG = มี Fair Value Gap ยืนยัน (Displacement สูง)")
            appendLine("─".repeat(45))
            appendLine("")

            appendLine("**🟢 Bullish Order Blocks (Demand Zones):**")
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
                    appendLine("")
                }
            }

            appendLine("**🔴 Bearish Order Blocks (Supply Zones):**")
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
                    appendLine("")
                }
            }

            appendLine("─".repeat(45))
            appendLine("📌 OB ที่มี ✅FVG = strong zone (ราคามักเด้งจากจุดนี้)")
            appendLine("📌 OB ที่มี ⚡NEAR = ราคาใกล้ถึงแล้ว ควรระวัง/รอ entry")
        }
    }

    // ─── 5. Market Structure ──────────────────────────────────────────────────

    private suspend fun executeSmcStructure(args: Map<String, String>): String {
        val symbol   = args["symbol"]   ?: return "กรุณาระบุ symbol เช่น BTCUSDT"
        val interval = args["interval"] ?: "1h"

        val result = api.getSmcAnalysis(symbol, interval)
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
            appendLine("📐 **Market Structure — ${result.symbol} ($interval)**")
            appendLine("=".repeat(48))
            appendLine("💰 ราคาปัจจุบัน: ${formatPrice(currentPrice)}")
            appendLine("")
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
            appendLine("─".repeat(48))

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
     * Merge nearby price levels within threshold%, combine their tags
     */
    private fun mergeLevels(
        levels: List<Pair<Double, String>>,
        thresholdPct: Double
    ): List<Pair<Double, String>> {
        if (levels.isEmpty()) return emptyList()
        val sorted = levels.sortedBy { it.first }
        val merged = mutableListOf<Pair<Double, String>>()
        var clusterPrices = mutableListOf(sorted[0].first)
        var clusterTags   = mutableSetOf(sorted[0].second)

        for (i in 1 until sorted.size) {
            val (price, tag) = sorted[i]
            val clusterAvg = clusterPrices.average()
            val tol = clusterAvg * (thresholdPct / 100.0)
            if (kotlin.math.abs(price - clusterAvg) <= tol) {
                clusterPrices.add(price)
                clusterTags.add(tag)
            } else {
                merged.add(clusterPrices.average() to clusterTags.sorted().joinToString("+"))
                clusterPrices = mutableListOf(price)
                clusterTags   = mutableSetOf(tag)
            }
        }
        if (clusterPrices.isNotEmpty()) {
            merged.add(clusterPrices.average() to clusterTags.sorted().joinToString("+"))
        }
        return merged
    }
}

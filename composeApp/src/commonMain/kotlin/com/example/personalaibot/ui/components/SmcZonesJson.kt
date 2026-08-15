package com.example.personalaibot.ui.components

import com.example.personalaibot.tools.trading.Candle
import com.example.personalaibot.tools.trading.SmcAnalysisResult
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * แปลงผล SMC analysis → JSON zones สำหรับ dashboard_engine.js drawSMC()
 * ใช้ร่วมกันทั้ง dashboard เต็มจอ (TradingChartScreen) และ mini-chart ในแชท (MessageBubble)
 *
 * zone 2 แบบ:
 *  - กล่อง (OB/FVG): top, bottom
 *  - เส้น (Liquidity/Premium/Discount/EQ): line=true + price
 */
fun buildSmcZonesJson(smc: SmcAnalysisResult, candles: List<Candle>): String {
    val lastTime = candles.lastOrNull()?.timestamp ?: return "[]"
    fun norm(t: Long): Long = if (t > 1_000_000_000_000L) t / 1000 else t
    val endTime = norm(lastTime)
    val fallbackStart = endTime - 100 * 3600

    val zones = buildJsonArray {
        // ─── Order Blocks (กล่อง) ───
        smc.bullishOBs.filter { !it.mitigated }.forEach { ob ->
            add(buildJsonObject {
                put("top", ob.top); put("bottom", ob.bottom)
                put("startTime", if (ob.timestamp > 0) norm(ob.timestamp) else fallbackStart)
                put("endTime", endTime)
                put("color", "rgba(38, 166, 154, 0.9)"); put("type", "Bull OB")
            })
        }
        smc.bearishOBs.filter { !it.mitigated }.forEach { ob ->
            add(buildJsonObject {
                put("top", ob.top); put("bottom", ob.bottom)
                put("startTime", if (ob.timestamp > 0) norm(ob.timestamp) else fallbackStart)
                put("endTime", endTime)
                put("color", "rgba(239, 83, 80, 0.9)"); put("type", "Bear OB")
            })
        }

        // ─── Fair Value Gaps (กล่อง) ───
        smc.fvgs.take(6).forEach { f ->
            add(buildJsonObject {
                put("top", f.top); put("bottom", f.bottom)
                put("startTime", if (f.timestamp > 0) norm(f.timestamp) else fallbackStart)
                put("endTime", endTime)
                put("color", "rgba(0, 188, 212, 0.8)"); put("type", "FVG")
            })
        }

        // ─── Liquidity zones (เส้น EQL/EQH พร้อมดาวตาม confluence) ───
        smc.liquidityZones
            .sortedByDescending { it.confluenceScore }
            .take(8)
            .forEach { z ->
                val stars = "★".repeat(z.confluenceScore.coerceIn(1, 5))
                add(buildJsonObject {
                    put("line", true); put("price", z.price)
                    put("startTime", fallbackStart); put("endTime", endTime)
                    put("color", if (z.isHigh) "rgba(255, 82, 82, 0.95)" else "rgba(105, 240, 174, 0.95)")
                    put("type", "${if (z.isHigh) "EQH" else "EQL"} $stars")
                })
            }

        // ─── Premium / Discount / Equilibrium (เส้น) ───
        if (smc.premiumBot > 0) {
            add(buildJsonObject {
                put("line", true); put("price", smc.premiumBot)
                put("startTime", fallbackStart); put("endTime", endTime)
                put("color", "rgba(239, 83, 80, 0.55)"); put("type", "Premium ≥")
            })
        }
        if (smc.equilibrium > 0) {
            add(buildJsonObject {
                put("line", true); put("price", smc.equilibrium)
                put("startTime", fallbackStart); put("endTime", endTime)
                put("color", "rgba(255, 167, 38, 0.8)"); put("type", "EQ")
            })
        }
        if (smc.discountTop > 0) {
            add(buildJsonObject {
                put("line", true); put("price", smc.discountTop)
                put("startTime", fallbackStart); put("endTime", endTime)
                put("color", "rgba(38, 166, 154, 0.55)"); put("type", "Discount ≤")
            })
        }
    }
    return zones.toString()
}

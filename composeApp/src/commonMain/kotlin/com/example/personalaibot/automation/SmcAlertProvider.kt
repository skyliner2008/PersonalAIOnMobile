package com.example.personalaibot.automation

import com.example.personalaibot.logDebug
import com.example.personalaibot.tools.trading.SmcApiService

/**
 * SmcAlertProvider — แปลงผล SMC Analysis (Market Structure / Premium-Discount /
 * Order Blocks / FVG / Liquidity Zones) ให้เป็น field ที่ตั้ง alert ได้
 *
 * ใช้ผ่าน tool_name = "trading_smc" ใน AlertJob
 * เลือก timeframe ได้ด้วย suffix @TF ในสัญลักษณ์ เช่น "XAUUSD@15m" (default 1h)
 *
 * หมายเหตุ: ระบบแท่งเทียนเป็น incremental (cache DB + ดึงเฉพาะแท่งที่ขาด)
 * จึงเรียกซ้ำถี่ได้โดยไม่เปลือง — ตรวจสอบแล้วใน SmcApiService.fetchCandlesWithSource
 */
class SmcAlertProvider(private val smcApi: SmcApiService) {

    suspend fun fetch(rawSymbol: String): Map<String, String> {
        val (symbol, tf) = IndicatorAlertProvider.splitSymbolAndTf(rawSymbol)
        val result = try {
            // สำหรับ alert ไม่บังคับ TV source เคร่ง — ยอม fallback (Yahoo/Binance)
            // ไม่งั้น alert จะ error ทุกครั้งที่ TV websocket ล่ม
            smcApi.getSmcAnalysis(symbol, tf, strictTvSource = false)
        } catch (e: Exception) {
            logDebug("SmcAlertProvider", "$symbol/$tf failed: ${e.message}")
            return mapOf("error" to (e.message ?: "unknown"))
        } ?: return mapOf("error" to "no analysis ($symbol/$tf)")

        val price = result.currentPrice
        fun dist(target: Double) = "%.4f".format(kotlin.math.abs(price - target)).trimEnd('0').trimEnd('.')

        // ตำแหน่งราคาในโครงสร้าง 0-100 (0=structure low, 100=structure high)
        val range = result.structureHigh - result.structureLow
        val zonePct = if (range > 0) ((price - result.structureLow) / range * 100.0).coerceIn(0.0, 100.0) else 50.0

        // OB ที่ใกล้ราคาที่สุด (เฉพาะที่ยังไม่ถูก mitigate)
        val nearestBullOb = result.bullishOBs.filter { !it.mitigated }.minByOrNull { kotlin.math.abs(price - it.top) }
        val nearestBearOb = result.bearishOBs.filter { !it.mitigated }.minByOrNull { kotlin.math.abs(price - it.bottom) }

        // FVG ที่ใกล้ที่สุด
        val nearestFvg = result.fvgs.minByOrNull { fvg ->
            val mid = (fvg.top + fvg.bottom) / 2.0
            kotlin.math.abs(price - mid)
        }

        // Liquidity: EQH เหนือราคาที่ใกล้สุด / EQL ใต้ราคาที่ใกล้สุด
        val liqAbove = result.liquidityZones.filter { it.isHigh && it.price > price }.minByOrNull { it.price }
        val liqBelow = result.liquidityZones.filter { !it.isHigh && it.price < price }.maxByOrNull { it.price }

        logDebug("SmcAlertProvider", "$symbol/$tf: zone=${result.priceZone} pct=%.1f trend=${result.structureDirection}".format(zonePct))

        return buildMap {
            put("symbol", symbol)
            put("timeframe", tf)
            put("source", result.candleSource)
            put("close", "%.4f".format(price))
            // โครงสร้างตลาด
            put("smc_trend", result.structureDirection)
            put("smc_last_event", result.lastStructureEvent.ifBlank { "NONE" })
            put("smc_structure_high", "%.4f".format(result.structureHigh))
            put("smc_structure_low", "%.4f".format(result.structureLow))
            // Premium / Discount
            put("smc_zone", result.priceZone)
            put("smc_zone_pct", "%.1f".format(zonePct))          // >=80 พรีเมียม, <=20 ดิสเคาน์
            put("smc_equilibrium", "%.4f".format(result.equilibrium))
            put("smc_premium_bot", "%.4f".format(result.premiumBot))
            put("smc_discount_top", "%.4f".format(result.discountTop))
            // Order Blocks (ระยะห่างจากราคา — ยิ่งใกล้ 0 ยิ่งถึงโซน)
            nearestBullOb?.let {
                put("bull_ob_top", "%.4f".format(it.top))
                put("bull_ob_bottom", "%.4f".format(it.bottom))
                put("bull_ob_dist", dist(it.top))
            }
            nearestBearOb?.let {
                put("bear_ob_top", "%.4f".format(it.top))
                put("bear_ob_bottom", "%.4f".format(it.bottom))
                put("bear_ob_dist", dist(it.bottom))
            }
            // FVG
            nearestFvg?.let {
                put("fvg_top", "%.4f".format(it.top))
                put("fvg_bottom", "%.4f".format(it.bottom))
                put("fvg_is_bull", if (it.isBullish) "1" else "0")
                put("fvg_dist", dist((it.top + it.bottom) / 2.0))
            }
            // Liquidity zones
            liqAbove?.let {
                put("liq_above", "%.4f".format(it.price))
                put("liq_above_dist", dist(it.price))
                put("liq_above_stars", it.confluenceScore.toString())
            }
            liqBelow?.let {
                put("liq_below", "%.4f".format(it.price))
                put("liq_below_dist", dist(it.price))
                put("liq_below_stars", it.confluenceScore.toString())
            }
            // อื่นๆ
            put("attack_force", if (result.attackForce) "1" else "0")
            put("atr", "%.4f".format(result.atr))
        }
    }
}

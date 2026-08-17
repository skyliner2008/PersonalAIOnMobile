package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * SmcSignalDetector — port จาก mt5-core-server core/SignalDetector.ts
 * ตรวจจังหวะเข้าเทรดแบบ deterministic จาก SmcSnapshot + Indicators
 *
 * เงื่อนไขเข้า (5 แบบ — ตัดตัวที่ผูก PriceMap/MTF ออก):
 *  1. OB_BOUNCE        → SMC_FVG_REVERSAL     ราคาชนขอบ OB ที่ยัง active + FVG confirm
 *  2. LIQ_SWEEP        → SCALPING             liquidity sweep + wick reclaim (single-TF)
 *  3. STRUCTURE_BREAK  → SMC_FVG_REVERSAL / SMC_FVG_CONTINUATION   CHoCH หรือ SMS/BMS
 *  4. FVG_FILL         → SMC_FVG_SCALP        ราคา fill FVG ≥50% ตามทิศ
 *  5. RSI_DIVERGENCE   → SMC_RSI_DIVERGENCE   ราคา LL/HH แต่ RSI สวนทาง
 *
 * กรองผลลัพธ์ด้วย minRRR ≥ 1.2 และ confluence ≥ 2★ (ตาม DEFAULT_CONFIG ต้นฉบับ)
 * TP ใช้ findNearestLevel (swing/liquidity/OB ฝั่งตรงข้าม) — ไม่มี PriceMap บนมือถือ
 *
 * ต้นฉบับใช้ MTF bias จาก analyses H4/H1/M15 — บนมือถือประยุกต์เป็น bias จาก
 * โครงสร้าง + เทรนด์ (close vs SMA20/50) ของ TF เดียวกัน
 */
object SmcSignalDetector {

    data class SmcEntrySignal(
        val side: String,           // BUY | SELL
        val entry: Double,
        val sl: Double,
        val tp: Double,
        val confidence: Int,        // 0-100
        val confluenceStars: Int,   // 0-5
        val strategy: String,       // SMC_FVG_REVERSAL | SMC_FVG_CONTINUATION | SMC_FVG_SCALP | SMC_RSI_DIVERGENCE | SCALPING
        val triggers: List<String>,
        val riskRewardRatio: Double
    ) {
        /** key สำหรับ edge-dedupe — สัญญาณเดิมในแท่งถัดไปถือว่าไม่ใช่สัญญาณใหม่ */
        val key: String get() = "$side|$strategy|${round(sl * 10000) / 10000}|${round(tp * 10000) / 10000}"
    }

    private const val MIN_CONFLUENCE_STARS = 2
    // กลับมาใช้ 1.2 ตามต้นฉบับ MT5 — เคยเข้มเป็น 1.8 เพราะไม่มี PriceMap แต่ผลคือ selection bias:
    // เหลือเฉพาะสัญญาณที่ TP (nearest level) ไกล ≥1.8R → win rate ต่ำมาก + SL structure แคบทำให้
    // commission (0.045%/ข้าง × notional ใหญ่จาก tight SL) กิน ~0.6-0.7R ต่อไม้ → PF ติดลบทุก TF (พิสูจน์จาก backtest 2026-08-17)
    private const val MIN_RRR = 1.2
    // ระยะ SL ขั้นต่ำเทียบ ATR14 — พิสูจน์จาก forensics 2026-08-18: สัญญาณที่ SL ติดจมูก (0.6–3.9 จุด บน 15m
    // ทั้งที่ ATR ~3-5) โดน noise กวาด 100% ก่อน TP เสมอ → เทรดไม่ได้จริง ต้องตัดทิ้ง (ใช้ทั้ง live alert และ backtest)
    private const val MIN_SL_ATR = 0.75
    private const val OB_PROXIMITY_PCT = 0.15   // % tolerance ราคาชน OB
    private const val FVG_FILL_PCT = 0.50       // FVG ต้อง fill ≥50%

    /** ตรวจสัญญาณทั้งหมด ณ แท่งสุดท้ายของ window (caller ส่ง window ย้อนหลัง ≤300 แท่ง จบที่แท่งที่ต้องการ) */
    fun detect(symbol: String, timeframe: String, window: List<Candle>): List<SmcEntrySignal> {
        if (window.size < 60) return emptyList()
        val lastPrice = window.last().close
        val smc = SmcEngine.buildSnapshot(window, symbol, timeframe)
        if (smc.structure.direction == StructureDirection.INIT && smc.activeFvgs.isEmpty()) return emptyList()

        val bias = computeBias(window, smc)
        val signals = mutableListOf<SmcEntrySignal>()

        signals += detectObBounce(lastPrice, smc, bias)
        signals += detectLiquiditySweep(lastPrice, window, smc, bias)
        signals += detectStructureBreak(lastPrice, smc, bias)
        signals += detectFvgFill(lastPrice, smc, bias)
        signals += detectRsiDivergence(lastPrice, window, smc)

        // กรอง 5 ชั้น (ชดเชย Gates/MTF ของต้นฉบับ MT5 ที่ไม่ได้ port):
        //  1) คุณภาพขั้นต่ำ: RRR + confluence stars
        //  2) validity: SL/TP ต้องอยู่ถูกฝั่งกับทิศทาง
        //  3) เทรดตามทิศโครงสร้างเท่านั้น (BUY เมื่อ BULLISH / SELL เมื่อ BEARISH)
        //  4) Premium/Discount: สัญญาณ reversal — BUY เฉพาะโซน DISCOUNT, SELL เฉพาะ PREMIUM
        //     (ยกเว้น CONTINUATION ที่ตามเทรนด์อยู่แล้ว)
        //  5) ระยะ SL ≥ 0.75×ATR14 — SL ติดจมูกโดน noise กวาดก่อน TP เสมอ (forensics 2026-08-18)
        val atr14 = computeAtr(window, 14)
        val minSlDist = atr14 * MIN_SL_ATR
        return signals.filter { sig ->
            if (sig.riskRewardRatio < MIN_RRR || sig.confluenceStars < MIN_CONFLUENCE_STARS) return@filter false
            val valid = (sig.side == "BUY" && sig.sl < sig.entry && sig.tp > sig.entry) ||
                (sig.side == "SELL" && sig.sl > sig.entry && sig.tp < sig.entry)
            if (!valid) return@filter false
            val dirOk = if (sig.side == "BUY") smc.structure.direction == StructureDirection.BULLISH
            else smc.structure.direction == StructureDirection.BEARISH
            if (!dirOk) return@filter false
            if (sig.strategy != "SMC_FVG_CONTINUATION") {
                val pdOk = if (sig.side == "BUY") smc.premiumDiscount.zone == PremiumDiscountZone.DISCOUNT
                else smc.premiumDiscount.zone == PremiumDiscountZone.PREMIUM
                if (!pdOk) return@filter false
            }
            if (minSlDist > 0 && kotlin.math.abs(sig.entry - sig.sl) < minSlDist) return@filter false
            true
        }
    }

    // ── Bias proxy (แทน MTF analyses ของต้นฉบับ) ────────────────────────

    private data class Bias(val bullCount: Int, val bearCount: Int, val dominant: String) // BULL|BEAR|NEUTRAL

    private fun computeBias(candles: List<Candle>, smc: SmcSnapshot): Bias {
        val closes = candles.map { it.close }
        val sma20 = closes.takeLast(20).average()
        val sma50 = closes.takeLast(min(50, closes.size)).average()
        val last = closes.last()
        var bull = 0
        var bear = 0
        if (last > sma20 && sma20 >= sma50) bull++ else if (last < sma20 && sma20 <= sma50) bear++
        when (smc.structure.direction) {
            StructureDirection.BULLISH -> bull++
            StructureDirection.BEARISH -> bear++
            StructureDirection.INIT -> {}
        }
        val dominant = if (bull > bear) "BULL" else if (bear > bull) "BEAR" else "NEUTRAL"
        return Bias(bull, bear, dominant)
    }

    // ── 1. OB Bounce ───────────────────────────────────────────────────

    private fun detectObBounce(lastPrice: Double, smc: SmcSnapshot, bias: Bias): List<SmcEntrySignal> {
        val out = mutableListOf<SmcEntrySignal>()
        val tolerance = lastPrice * (OB_PROXIMITY_PCT / 100.0)

        for (ob in smc.bullObs.filter { !it.mitigated && it.hasFvg }) {
            if (lastPrice >= ob.bottom - tolerance && lastPrice <= ob.top + tolerance && bias.dominant != "BEAR") {
                val sl = ob.bottom - tolerance * 2
                val nearZone = smc.liquidityZones.firstOrNull { z ->
                    !z.isHigh && abs(z.price - ob.bottom) < tolerance * 3
                }
                val stars = nearZone?.confluenceStars ?: 1
                val tp = findNearestLevel(lastPrice, smc, above = true) ?: (lastPrice + (lastPrice - sl) * 2)
                val risk = abs(lastPrice - sl)
                if (risk <= 0) continue
                out += SmcEntrySignal(
                    side = "BUY", entry = lastPrice, sl = sl, tp = tp,
                    confidence = min(90, 50 + stars * 8 + 10 + bias.bullCount * 5),
                    confluenceStars = stars,
                    strategy = "SMC_FVG_REVERSAL",
                    triggers = listOf("OB_BOUNCE ${fmt2(ob.bottom)}-${fmt2(ob.top)}", "FVG confirmed", "bias=${bias.dominant}"),
                    riskRewardRatio = abs(tp - lastPrice) / risk
                )
            }
        }

        for (ob in smc.bearObs.filter { !it.mitigated && it.hasFvg }) {
            if (lastPrice >= ob.bottom - tolerance && lastPrice <= ob.top + tolerance && bias.dominant != "BULL") {
                val sl = ob.top + tolerance * 2
                val nearZone = smc.liquidityZones.firstOrNull { z ->
                    z.isHigh && abs(z.price - ob.top) < tolerance * 3
                }
                val stars = nearZone?.confluenceStars ?: 1
                val tp = findNearestLevel(lastPrice, smc, above = false) ?: (lastPrice - (sl - lastPrice) * 2)
                val risk = abs(sl - lastPrice)
                if (risk <= 0) continue
                out += SmcEntrySignal(
                    side = "SELL", entry = lastPrice, sl = sl, tp = tp,
                    confidence = min(90, 50 + stars * 8 + 10 + bias.bearCount * 5),
                    confluenceStars = stars,
                    strategy = "SMC_FVG_REVERSAL",
                    triggers = listOf("OB_BOUNCE ${fmt2(ob.bottom)}-${fmt2(ob.top)}", "FVG confirmed", "bias=${bias.dominant}"),
                    riskRewardRatio = abs(lastPrice - tp) / risk
                )
            }
        }
        return out
    }

    // ── 2. Liquidity Sweep (single-TF adaptation) ─────────────────────

    private fun detectLiquiditySweep(
        lastPrice: Double, window: List<Candle>, smc: SmcSnapshot, bias: Bias
    ): List<SmcEntrySignal> {
        val (bullSweep, bearSweep) = SweepDetection.detectSweeps(window)
        val out = mutableListOf<SmcEntrySignal>()

        if (bullSweep && bias.dominant != "BEAR") {
            val sl = min(smc.swingLows.takeLast(2).minOrNull() ?: lastPrice * 0.998, lastPrice * 0.998)
            val tp = findNearestLevel(lastPrice, smc, above = true) ?: lastPrice * 1.005
            val risk = abs(lastPrice - sl)
            if (risk > 0) {
                out += SmcEntrySignal(
                    side = "BUY", entry = lastPrice, sl = sl, tp = tp,
                    confidence = 65, confluenceStars = 2,
                    strategy = "SCALPING",
                    triggers = listOf("LIQ_SWEEP bull + wick reclaim"),
                    riskRewardRatio = abs(tp - lastPrice) / risk
                )
            }
        }

        if (bearSweep && bias.dominant != "BULL") {
            val sl = max(smc.swingHighs.takeLast(2).maxOrNull() ?: lastPrice * 1.002, lastPrice * 1.002)
            val tp = findNearestLevel(lastPrice, smc, above = false) ?: lastPrice * 0.995
            val risk = abs(sl - lastPrice)
            if (risk > 0) {
                out += SmcEntrySignal(
                    side = "SELL", entry = lastPrice, sl = sl, tp = tp,
                    confidence = 65, confluenceStars = 2,
                    strategy = "SCALPING",
                    triggers = listOf("LIQ_SWEEP bear + wick reclaim"),
                    riskRewardRatio = abs(lastPrice - tp) / risk
                )
            }
        }
        return out
    }

    // ── 3. Structure Break (CHoCH / SMS / BMS) ─────────────────────────

    private fun detectStructureBreak(lastPrice: Double, smc: SmcSnapshot, bias: Bias): List<SmcEntrySignal> {
        val out = mutableListOf<SmcEntrySignal>()
        val s = smc.structure

        if (s.lastEvent == StructureEvent.CHoCH) {
            if (s.lastEventSide == "UP" && bias.dominant != "BEAR") {
                val nearestOb = smc.bullObs.firstOrNull()
                if (nearestOb != null) {
                    val sl = nearestOb.bottom - lastPrice * 0.001
                    val tp = s.structureHigh
                    val risk = abs(lastPrice - sl)
                    if (risk > 0 && tp > lastPrice) {
                        out += SmcEntrySignal(
                            side = "BUY", entry = lastPrice, sl = sl, tp = tp,
                            confidence = min(85, 60 + if (s.idmLowSwept) 15 else 0),
                            confluenceStars = if (s.idmLowSwept) 4 else 3,
                            strategy = "SMC_FVG_REVERSAL",
                            triggers = listOf("CHoCH_UP", if (s.idmLowSwept) "IDM swept" else "No IDM"),
                            riskRewardRatio = abs(tp - lastPrice) / risk
                        )
                    }
                }
            }
            if (s.lastEventSide == "DOWN" && bias.dominant != "BULL") {
                val nearestOb = smc.bearObs.firstOrNull()
                if (nearestOb != null) {
                    val sl = nearestOb.top + lastPrice * 0.001
                    val tp = s.structureLow
                    val risk = abs(sl - lastPrice)
                    if (risk > 0 && tp < lastPrice) {
                        out += SmcEntrySignal(
                            side = "SELL", entry = lastPrice, sl = sl, tp = tp,
                            confidence = min(85, 60 + if (s.idmHighSwept) 15 else 0),
                            confluenceStars = if (s.idmHighSwept) 4 else 3,
                            strategy = "SMC_FVG_REVERSAL",
                            triggers = listOf("CHoCH_DOWN", if (s.idmHighSwept) "IDM swept" else "No IDM"),
                            riskRewardRatio = abs(lastPrice - tp) / risk
                        )
                    }
                }
            }
        }

        if (s.lastEvent == StructureEvent.SMS || s.lastEvent == StructureEvent.BMS) {
            if (s.direction == StructureDirection.BULLISH && bias.dominant != "BEAR") {
                val sl = s.structureLow - lastPrice * 0.0005
                val tp = findNearestLevel(lastPrice, smc, above = true) ?: lastPrice * 1.003
                val risk = abs(lastPrice - sl)
                if (risk > 0) {
                    out += SmcEntrySignal(
                        side = "BUY", entry = lastPrice, sl = sl, tp = tp,
                        confidence = min(75, 50 + s.continuationCount * 3), confluenceStars = 2,
                        strategy = "SMC_FVG_CONTINUATION",
                        triggers = listOf("${s.lastEvent}_UP", "Continuation #${s.continuationCount}"),
                        riskRewardRatio = abs(tp - lastPrice) / risk
                    )
                }
            }
            if (s.direction == StructureDirection.BEARISH && bias.dominant != "BULL") {
                val sl = s.structureHigh + lastPrice * 0.0005
                val tp = findNearestLevel(lastPrice, smc, above = false) ?: lastPrice * 0.997
                val risk = abs(sl - lastPrice)
                if (risk > 0) {
                    out += SmcEntrySignal(
                        side = "SELL", entry = lastPrice, sl = sl, tp = tp,
                        confidence = min(75, 50 + s.continuationCount * 3), confluenceStars = 2,
                        strategy = "SMC_FVG_CONTINUATION",
                        triggers = listOf("${s.lastEvent}_DOWN", "Continuation #${s.continuationCount}"),
                        riskRewardRatio = abs(lastPrice - tp) / risk
                    )
                }
            }
        }
        return out
    }

    // ── 4. FVG Fill ────────────────────────────────────────────────────

    private fun detectFvgFill(lastPrice: Double, smc: SmcSnapshot, bias: Bias): List<SmcEntrySignal> {
        val out = mutableListOf<SmcEntrySignal>()

        for (fvg in smc.activeFvgs) {
            val size = abs(fvg.top - fvg.bottom)
            if (size <= 0) continue

            if (fvg.isBull && bias.dominant != "BEAR") {
                val fillLevel = fvg.top - size * FVG_FILL_PCT
                if (lastPrice <= fillLevel && lastPrice >= fvg.bottom) {
                    val sl = fvg.bottom - size * 0.5
                    val tp = findNearestLevel(lastPrice, smc, above = true) ?: (lastPrice + size * 2)
                    val risk = abs(lastPrice - sl)
                    if (risk > 0) {
                        out += SmcEntrySignal(
                            side = "BUY", entry = lastPrice, sl = sl, tp = tp,
                            confidence = min(70, 45 + bias.bullCount * 5), confluenceStars = 2,
                            strategy = "SMC_FVG_SCALP",
                            triggers = listOf("FVG_FILL bull ${fmt2(fvg.bottom)}-${fmt2(fvg.top)}", "50% filled"),
                            riskRewardRatio = abs(tp - lastPrice) / risk
                        )
                    }
                }
            }

            if (!fvg.isBull && bias.dominant != "BULL") {
                val fillLevel = fvg.bottom + size * FVG_FILL_PCT
                if (lastPrice >= fillLevel && lastPrice <= fvg.top) {
                    val sl = fvg.top + size * 0.5
                    val tp = findNearestLevel(lastPrice, smc, above = false) ?: (lastPrice - size * 2)
                    val risk = abs(sl - lastPrice)
                    if (risk > 0) {
                        out += SmcEntrySignal(
                            side = "SELL", entry = lastPrice, sl = sl, tp = tp,
                            confidence = min(70, 45 + bias.bearCount * 5), confluenceStars = 2,
                            strategy = "SMC_FVG_SCALP",
                            triggers = listOf("FVG_FILL bear ${fmt2(fvg.bottom)}-${fmt2(fvg.top)}", "50% filled"),
                            riskRewardRatio = abs(lastPrice - tp) / risk
                        )
                    }
                }
            }
        }
        return out
    }

    // ── 5. RSI Divergence ──────────────────────────────────────────────

    private fun detectRsiDivergence(lastPrice: Double, window: List<Candle>, smc: SmcSnapshot): List<SmcEntrySignal> {
        val out = mutableListOf<SmcEntrySignal>()
        if (window.size < 50) return out
        val closes = window.map { it.close }
        val currentRsi = calculateRsi(closes) ?: return out

        // Bullish: ราคา Lower Low แต่ RSI Higher Low (+ RSI < 35)
        val prevLowPrice = smc.swingLows.lastOrNull()
        if (prevLowPrice != null) {
            val candleIdx = window.indexOfFirst { it.low == prevLowPrice }
            if (candleIdx > 0 && candleIdx < window.size - 5) {
                val prevRsi = calculateRsi(closes.subList(0, candleIdx + 1))
                if (prevRsi != null && lastPrice < prevLowPrice && currentRsi > prevRsi && currentRsi < 35) {
                    val sl = lastPrice - (lastPrice - prevLowPrice) * 0.5
                    val tp = findNearestLevel(lastPrice, smc, above = true) ?: lastPrice * 1.005
                    val risk = abs(lastPrice - sl)
                    val rrr = if (risk > 0) abs(tp - lastPrice) / risk else 0.0
                    if (rrr >= MIN_RRR) {
                        out += SmcEntrySignal(
                            side = "BUY", entry = lastPrice, sl = sl, tp = tp,
                            confidence = min(85, (60 + (currentRsi - prevRsi) * 2).toInt()),
                            confluenceStars = 3,
                            strategy = "SMC_RSI_DIVERGENCE",
                            triggers = listOf(
                                "Bullish Divergence (RSI ${fmt1(prevRsi)} → ${fmt1(currentRsi)})",
                                "Price LL (${fmt2(prevLowPrice)} → ${fmt2(lastPrice)})"
                            ),
                            riskRewardRatio = rrr
                        )
                    }
                }
            }
        }

        // Bearish: ราคา Higher High แต่ RSI Lower High (+ RSI > 65)
        val prevHighPrice = smc.swingHighs.lastOrNull()
        if (prevHighPrice != null) {
            val candleIdx = window.indexOfFirst { it.high == prevHighPrice }
            if (candleIdx > 0 && candleIdx < window.size - 5) {
                val prevRsi = calculateRsi(closes.subList(0, candleIdx + 1))
                if (prevRsi != null && lastPrice > prevHighPrice && currentRsi < prevRsi && currentRsi > 65) {
                    val sl = lastPrice + (prevHighPrice - lastPrice) * 0.5
                    val tp = findNearestLevel(lastPrice, smc, above = false) ?: lastPrice * 0.995
                    val risk = abs(sl - lastPrice)
                    val rrr = if (risk > 0) abs(lastPrice - tp) / risk else 0.0
                    if (rrr >= MIN_RRR) {
                        out += SmcEntrySignal(
                            side = "SELL", entry = lastPrice, sl = sl, tp = tp,
                            confidence = min(85, (60 + (prevRsi - currentRsi) * 2).toInt()),
                            confluenceStars = 3,
                            strategy = "SMC_RSI_DIVERGENCE",
                            triggers = listOf(
                                "Bearish Divergence (RSI ${fmt1(prevRsi)} → ${fmt1(currentRsi)})",
                                "Price HH (${fmt2(prevHighPrice)} → ${fmt2(lastPrice)})"
                            ),
                            riskRewardRatio = rrr
                        )
                    }
                }
            }
        }
        return out
    }

    // ── helpers ────────────────────────────────────────────────────────

    /** หาเป้าหมาย TP จาก level ที่ใกล้สุดฝั่งตรงข้าม (swing / liquidity / OB) — fallback แทน PriceMap */
    private fun findNearestLevel(price: Double, smc: SmcSnapshot, above: Boolean): Double? {
        val candidates = mutableListOf<Double>()
        if (above) {
            candidates += smc.swingHighs.filter { it > price }
            candidates += smc.liquidityZones.filter { it.isHigh && it.price > price }.map { it.price }
            candidates += smc.bearObs.filter { it.bottom > price }.map { it.bottom }
        } else {
            candidates += smc.swingLows.filter { it < price }
            candidates += smc.liquidityZones.filter { !it.isHigh && it.price < price }.map { it.price }
            candidates += smc.bullObs.filter { it.top < price }.map { it.top }
        }
        return candidates.minByOrNull { abs(it - price) }
    }

    /** RSI แบบ Wilder (ตรงต้นฉบับ TS) */
    private fun calculateRsi(closes: List<Double>, period: Int = 14): Double? {
        if (closes.size <= period) return null
        var gains = 0.0
        var losses = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d >= 0) gains += d else losses += -d
        }
        var avgGain = gains / period
        var avgLoss = losses / period
        for (i in period + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + max(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-d, 0.0)) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    private fun fmt1(v: Double) = "%.1f".format(v)
    private fun fmt2(v: Double) = if (abs(v) >= 100) "%.2f".format(v) else "%.4f".format(v)
}

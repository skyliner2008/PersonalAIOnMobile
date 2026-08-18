package com.example.personalaibot.automation.backtest

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs

/**
 * TpSlParams — พารามิเตอร์ SL/TP ที่ปรับได้ต่อกลยุทธ์ (หน่วย = เท่าของ ATR หรือเท่าของ risk ตามชนิด)
 * ค่า default ตรงกับสูตรเดิมใน SignalAlertProvider.computeTpSl เป๊ะ (ตรวจแล้ว):
 *  - MOM/TR/E: SL 2×ATR14, TP 3×ATR14
 *  - UT:       SL 2×ATR6,  TP 4×ATR6
 *  - DC:       SL 1.5×ATR14, TP 2.5×ATR14
 *  - REV:      SL 1×ATR14, TP = BB basis (fallback 1.5×ATR14)
 *  - 3BR:      SL = จุดสุดแท่งกลาง (floor 0.5×ATR14 × slMult), TP = tpMult × risk
 *  - 52H:      SL 1.5×ATR14, TP 2×ATR14
 */
data class TpSlParams(val slMult: Double, val tpMult: Double) {
    companion object {
        fun defaultsFor(kind: String): TpSlParams = when (kind) {
            "MOM", "TR", "E" -> TpSlParams(2.0, 3.0)
            "UT" -> TpSlParams(2.0, 4.0)
            "DC" -> TpSlParams(1.5, 2.5)
            "REV" -> TpSlParams(1.0, 1.5)
            "3BR" -> TpSlParams(1.0, 2.0)
            "52H" -> TpSlParams(1.5, 2.0)
            "MIX" -> TpSlParams(1.5, 2.5)
            else -> TpSlParams(1.5, 2.0)
        }

        /** Grid สำหรับ grid search — 25 combos (อ้างอิง optimizer.py: cartesian product) */
        val GRID_SL = listOf(1.0, 1.5, 2.0, 2.5, 3.0)
        val GRID_TP = listOf(1.5, 2.0, 2.5, 3.0, 4.0)
        fun grid(): List<TpSlParams> = GRID_SL.flatMap { s -> GRID_TP.map { t -> TpSlParams(s, t) } }

        /** ปัด params เป็น 2 ตำแหน่ง — กัน float รก เช่น 2.4000000000000004 / 1.2100000000000002 */
        fun round2(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0

        /**
         * Drift limit แบบ moss evolution_guide: float params ห้ามหนีจากค่าเริ่มต้นเกิน ±30%
         * และปรับต่อรอบไม่เกิน ±10% (clampStep)
         */
        fun clampDrift(base: TpSlParams, p: TpSlParams, maxDrift: Double = 0.30): TpSlParams {
            fun c(b: Double, v: Double) = round2(v.coerceIn(b * (1 - maxDrift), b * (1 + maxDrift)))
            return TpSlParams(c(base.slMult, p.slMult), c(base.tpMult, p.tpMult))
        }

        fun clampStep(prev: TpSlParams, next: TpSlParams, maxStep: Double = 0.10): TpSlParams {
            fun c(a: Double, b: Double) = round2(b.coerceIn(a * (1 - maxStep), a * (1 + maxStep)))
            return TpSlParams(c(prev.slMult, next.slMult), c(prev.tpMult, next.tpMult))
        }

        /**
         * RR floor — ห้ามผลลัพธ์สุดท้ายมี RR (tpMult/slMult) ต่ำกว่า minRr สำหรับกลยุทธ์ ATR-multiple
         * (MOM/TR/E/DC/52H/UT/MIX: RR = tp/sl โดยตรง; 3BR: RR = tpMult เพราะ TP = tpMult × risk;
         *  REV ยกเว้น — TP คือ BB basis ไม่ได้ผูก mult โดยตรง)
         * เหตุ: reflection เดิมเบี่ยงไป "ขยาย SL + หด TP" ทุกรอบจน RR < 1 → แพ้โดยโครงสร้าง 8/8 กลยุทธ์ (2026-08-18)
         */
        fun enforceRrFloor(kind: String, p: TpSlParams, minRr: Double = 1.2): TpSlParams = when (kind) {
            "REV" -> p
            "3BR" -> if (p.tpMult < minRr) p.copy(tpMult = minRr) else p
            else -> if (p.slMult > 0 && p.tpMult / p.slMult < minRr) p.copy(tpMult = round2(p.slMult * minRr)) else p
        }
    }
}

/**
 * parameterizedTpSl — สูตรเดียวกับ SignalAlertProvider.computeTpSl แต่รับ mults จากภายนอก
 * (ใช้ใน backtest optimizer/evolution และ live alert เมื่อมี tuned params)
 */
fun parameterizedTpSl(
    kind: String, side: String, candles: List<Candle>, i: Int,
    atr14: Double, atr6: Double, p: TpSlParams
): Pair<Double, Double> {
    val entry = candles[i].close
    val isBuy = side == "BUY"
    fun levels(slDist: Double, tpDist: Double): Pair<Double, Double> =
        if (isBuy) (entry - slDist) to (entry + tpDist) else (entry + slDist) to (entry - tpDist)

    return when (kind) {
        "UT" -> levels(p.slMult * atr6, p.tpMult * atr6)
        "REV" -> {
            // TP = BB basis (SMA20) ถ้าอยู่ฝั่งกำไร ไม่งั้น tpMult×ATR14
            val basis = smaAt(candles, i, 20)
            val tp = if (!basis.isNaN() && (if (isBuy) basis > entry else basis < entry)) {
                basis
            } else if (isBuy) entry + p.tpMult * atr14 else entry - p.tpMult * atr14
            (if (isBuy) entry - p.slMult * atr14 else entry + p.slMult * atr14) to tp
        }
        "3BR" -> {
            val mid = candles[(i - 1).coerceAtLeast(0)]
            val slRaw = if (isBuy) mid.low - 0.2 * atr14 else mid.high + 0.2 * atr14
            val risk = abs(entry - slRaw).coerceAtLeast(p.slMult * 0.5 * atr14)
            (if (isBuy) entry - risk else entry + risk) to
                (if (isBuy) entry + p.tpMult * risk else entry - p.tpMult * risk)
        }
        else -> levels(p.slMult * atr14, p.tpMult * atr14) // MOM/TR/E/DC/52H/default
    }
}

/** SMA ของราคาปิด ณ แท่ง i (ช่วง period แท่งย้อนหลัง) — NaN ถ้าข้อมูลไม่พอ */
internal fun smaAt(candles: List<Candle>, i: Int, period: Int): Double {
    if (i < period - 1) return Double.NaN
    var sum = 0.0
    for (j in i - period + 1..i) sum += candles[j].close
    return sum / period
}

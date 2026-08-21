package com.example.personalaibot.automation.backtest

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs

/**
 * TpSlParams — พารามิเตอร์ SL/TP ที่ปรับได้ต่อกลยุทธ์
 *
 * ★ เปลี่ยนสัจพันธ์ 2026-08-20 (strategy-native SL/TP): SL ไม่ใช่ "slMult × ATR" ตรงๆ อีกต่อไป
 * สำหรับกลยุทธ์ที่มีโครงสร้างราคา — SL ยึด "โครงสร้างของกลยุทธ์นั้น" แล้ว slMult เป็น buffer (×ATR)
 * ส่วน tpMult เป็น RR multiple ของ risk จริง (ไม่ใช่ ×ATR):
 *  - MOM/TR/E/52H/MIX: SL = swing low/high 10 แท่ง + slMult×ATR14 (buffer), TP = tpMult × risk
 *  - DC:               SL = Donchian exit band 10 แท่งฝั่งตรงข้าม + slMult×ATR14 (Turtle 20-in/10-out), TP = tpMult × risk
 *  - UT:               ATR6 multiples เหมือนเดิม — trailing stop ของ UT คือ structure ของมันเอง (native อยู่แล้ว)
 *  - REV:              SL = swing สุดขอบ + slMult×ATR14 (cap 2.5×ATR — mean reversion ไม่ถือขาดทุนลึก), TP = BB basis (fallback tpMult×ATR14)
 *  - 3BR:              SL = จุดสุดแท่งกลาง (floor 0.5×slMult×ATR14), TP = tpMult × risk (structural อยู่แล้ว)
 * risk ถูก clamp ที่ [0.3×ATR, 3.5×ATR] — structure แคบเกิน = noise, ไกลเกิน = เข้าช้าแล้ว ไม่รับ risk นั้น
 *
 * ⚠️ tuned params เก่าใน DB ถูกตีความด้วย semantics ใหม่ (buffer/RR) — ค่าเดิม เช่น 2.0/3.0
 *    ยังปลอดภัย (buffer 2×ATR กว้างหน่อย, RR 3) แต่ควร optimize ใหม่ให้ตรงนิยาม
 */
data class TpSlParams(val slMult: Double, val tpMult: Double) {
    companion object {
        fun defaultsFor(kind: String): TpSlParams = when (kind) {
            "MOM", "TR", "E", "52H", "MIX" -> TpSlParams(0.5, 2.0)  // buffer 0.5×ATR หลัง swing, RR 1:2
            "UT" -> TpSlParams(2.0, 4.0)                            // ATR6 multiples (native trailing stop)
            "DC" -> TpSlParams(0.5, 2.0)                            // buffer หลัง exit band, RR 1:2
            "REV" -> TpSlParams(0.5, 1.5)                           // buffer หลัง swing (cap 2.5 ATR), fallback TP 1.5×ATR
            "3BR" -> TpSlParams(1.0, 2.0)                           // floor factor + RR (structural อยู่แล้ว)
            else -> TpSlParams(1.5, 2.0)
        }

        /** Grid สำหรับ grid search — 25 combos (SL=buffer ×ATR / ATR-mult สำหรับ UT, TP=RR) */
        val GRID_SL = listOf(0.2, 0.5, 1.0, 1.5, 2.0)
        val GRID_TP = listOf(1.2, 1.5, 2.0, 2.5, 3.0)
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
         * RR floor — ห้ามผลลัพธ์สุดท้ายมี RR ต่ำกว่า minRr
         * (structural kinds MOM/TR/E/DC/52H/MIX/3BR: RR = tpMult โดยตรง เพราะ TP = tpMult × risk;
         *  UT: RR = tpMult/slMult เพราะทั้งคู่เป็น ATR6 multiples;
         *  REV ยกเว้น — TP คือ BB basis ไม่ได้ผูก mult โดยตรง)
         * เหตุ: reflection เดิมเบี่ยงไป "ขยาย SL + หด TP" ทุกรอบจน RR < 1 → แพ้โดยโครงสร้าง 8/8 กลยุทธ์ (2026-08-18)
         */
        fun enforceRrFloor(kind: String, p: TpSlParams, minRr: Double = 1.2): TpSlParams = when (kind) {
            "REV" -> p
            "3BR", "MOM", "TR", "E", "DC", "52H", "MIX" -> if (p.tpMult < minRr) p.copy(tpMult = minRr) else p
            else -> if (p.slMult > 0 && p.tpMult / p.slMult < minRr) p.copy(tpMult = round2(p.slMult * minRr)) else p
        }
    }
}

/** swing low ของ look แท่งก่อนแท่ง i (ไม่รวมแท่ง i — point-in-time) — NaN ถ้าข้อมูลไม่พอ */
internal fun swingLow(candles: List<Candle>, i: Int, look: Int): Double {
    val from = (i - look).coerceAtLeast(0)
    if (from >= i) return Double.NaN
    var v = Double.POSITIVE_INFINITY
    for (j in from until i) v = minOf(v, candles[j].low)
    return v
}

/** swing high ของ look แท่งก่อนแท่ง i (ไม่รวมแท่ง i — point-in-time) — NaN ถ้าข้อมูลไม่พอ */
internal fun swingHigh(candles: List<Candle>, i: Int, look: Int): Double {
    val from = (i - look).coerceAtLeast(0)
    if (from >= i) return Double.NaN
    var v = Double.NEGATIVE_INFINITY
    for (j in from until i) v = maxOf(v, candles[j].high)
    return v
}

/**
 * parameterizedTpSl — strategy-native SL/TP (ดูนิยามใน TpSlParams header)
 * ใช้ใน backtest optimizer/evolution และ live alert เมื่อมี tuned params
 * (SignalAlertProvider.computeTpSl delegate มาที่นี่ — live == backtest เสมอ)
 */
fun parameterizedTpSl(
    kind: String, side: String, candles: List<Candle>, i: Int,
    atr14: Double, atr6: Double, p: TpSlParams
): Pair<Double, Double> {
    val entry = candles[i].close
    val isBuy = side == "BUY"
    fun levels(slDist: Double, tpDist: Double): Pair<Double, Double> =
        if (isBuy) (entry - slDist) to (entry + tpDist) else (entry + slDist) to (entry - tpDist)

    // risk จาก structure: |entry − anchor| + buffer(slMult×ATR) แล้ว clamp [0.3, 3.5]×ATR
    // anchor ใช้ไม่ได้ (NaN/อยู่ผิดฝั่ง) → fallback (1.5 หรือ slMult)×ATR แล้วแต่อันไหนกว้างกว่า
    fun structuralRisk(anchor: Double, atr: Double): Double {
        val usable = !anchor.isNaN() && (if (isBuy) anchor < entry else anchor > entry)
        if (!usable) return maxOf(1.5, p.slMult) * atr
        return (abs(entry - anchor) + p.slMult * atr).coerceIn(0.3 * atr, 3.5 * atr)
    }
    // TP แบบ RR: tpMult × risk จริง
    fun rrLevels(risk: Double): Pair<Double, Double> =
        if (isBuy) (entry - risk) to (entry + p.tpMult * risk) else (entry + risk) to (entry - p.tpMult * risk)

    return when (kind) {
        // UT Bot — trailing stop (key×ATR6) คือ structure ของมันเอง ไม่ต้องหา swing
        "UT" -> levels(p.slMult * atr6, p.tpMult * atr6)
        // Mean reversion — TP = BB basis (เส้นกลาง) ถ้าอยู่ฝั่งกำไร; SL = สุดขอบ swing + buffer, cap 2.5×ATR
        "REV" -> {
            val basis = smaAt(candles, i, 20)
            val tp = if (!basis.isNaN() && (if (isBuy) basis > entry else basis < entry)) {
                basis
            } else if (isBuy) entry + p.tpMult * atr14 else entry - p.tpMult * atr14
            val anchor = if (isBuy) swingLow(candles, i, 10) else swingHigh(candles, i, 10)
            val risk = structuralRisk(anchor, atr14).coerceAtMost(2.5 * atr14)
            (if (isBuy) entry - risk else entry + risk) to tp
        }
        // 3-Bar Reversal — SL = จุดสุดของแท่งกลาง pattern (structural อยู่แล้ว), TP = RR × risk
        "3BR" -> {
            val mid = candles[(i - 1).coerceAtLeast(0)]
            val slRaw = if (isBuy) mid.low - 0.2 * atr14 else mid.high + 0.2 * atr14
            val risk = abs(entry - slRaw).coerceAtLeast(p.slMult * 0.5 * atr14)
            (if (isBuy) entry - risk else entry + risk) to
                (if (isBuy) entry + p.tpMult * risk else entry - p.tpMult * risk)
        }
        // Donchian Breakout — Turtle: เข้า 20 แท่ง ออก/SL ที่ band ฝั่งตรงข้าม 10 แท่ง
        "DC" -> {
            val anchor = if (isBuy) swingLow(candles, i, 10) else swingHigh(candles, i, 10)
            rrLevels(structuralRisk(anchor, atr14))
        }
        // ตามเทรนด์/โมเมนตัม/52H/MIX — SL หลัง swing 10 แท่ง, TP = RR × risk
        "MOM", "TR", "E", "52H", "MIX" -> {
            val anchor = if (isBuy) swingLow(candles, i, 10) else swingHigh(candles, i, 10)
            rrLevels(structuralRisk(anchor, atr14))
        }
        else -> levels(p.slMult * atr14, p.tpMult * atr14)
    }
}

/** SMA ของราคาปิด ณ แท่ง i (ช่วง period แท่งย้อนหลัง) — NaN ถ้าข้อมูลไม่พอ */
internal fun smaAt(candles: List<Candle>, i: Int, period: Int): Double {
    if (i < period - 1) return Double.NaN
    var sum = 0.0
    for (j in i - period + 1..i) sum += candles[j].close
    return sum / period
}

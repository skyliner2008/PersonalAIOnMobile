package com.skyliner2008.jarvis.automation.wake.triggers

import com.skyliner2008.jarvis.automation.smc.UnifiedSmcSignals
import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.KEY_LEVEL
import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.LIQUIDITY_SWEEP
import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.STRUCTURE
import com.skyliner2008.jarvis.automation.wake.Series
import com.skyliner2008.jarvis.automation.wake.TriggerEvent
import com.skyliner2008.jarvis.automation.wake.TriggerKind.EVENT
import com.skyliner2008.jarvis.automation.wake.TriggerKind.STATE
import com.skyliner2008.jarvis.automation.wake.WakeContext
import com.skyliner2008.jarvis.automation.wake.WakeTrigger
import com.skyliner2008.jarvis.automation.wake.p
import com.skyliner2008.jarvis.automation.wake.tfLabel
import com.skyliner2008.jarvis.automation.wake.trigger
import com.skyliner2008.jarvis.automation.wake.triggers.WakeMath.firstTouch
import kotlin.math.abs

// ═════════════════════════════════════════════════════════════════════════════
// A. โครงสร้างตลาด (Structure)
// ═════════════════════════════════════════════════════════════════════════════

/** BOS / CHoCH ที่แท่งปิดล่าสุดของ TF นั้น */
private fun structureEvent(ctx: WakeContext, s: Series, choch: Boolean, id: String): TriggerEvent? {
    val st = s.structure ?: return null
    val i = s.n - 1
    val up = if (choch) st.chochUp[i] else st.bosUp[i]
    val dn = if (choch) st.chochDn[i] else st.bosDn[i]
    val label = if (choch) "CHoCH" else "BOS"
    return when {
        up -> TriggerEvent(id, s.tf, "${tfLabel(s.tf)} เกิด $label ขาขึ้น — ปิดเหนือ swing high ล่าสุด", "BUY", s.last?.close)
        dn -> TriggerEvent(id, s.tf, "${tfLabel(s.tf)} เกิด $label ขาลง — ปิดใต้ swing low ล่าสุด", "SELL", s.last?.close)
        else -> null
    }
}

/** swing ล่าสุดที่ยืนยันแล้ว "ก่อน" แท่งล่าสุด (ไม่นับที่เพิ่งยืนยันตอนนี้) */
private fun priorSwingHigh(s: Series): Pair<Int, Double>? = s.swingHighs.lastOrNull { it.first < s.n - 1 }
private fun priorSwingLow(s: Series): Pair<Int, Double>? = s.swingLows.lastOrNull { it.first < s.n - 1 }

internal val STRUCTURE_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("BOS", "Break of Structure", STRUCTURE, EVENT) { structureEvent(it, it.primary, false, "BOS") },
    trigger("CHOCH", "Change of Character", STRUCTURE, EVENT) { structureEvent(it, it.primary, true, "CHOCH") },
    trigger("HTF_CHOCH_H1", "CHoCH บน H1", STRUCTURE, EVENT) { structureEvent(it, it.h1, true, "HTF_CHOCH_H1") },
    trigger("HTF_CHOCH_H4", "CHoCH บน H4", STRUCTURE, EVENT) { structureEvent(it, it.h4, true, "HTF_CHOCH_H4") },

    trigger("SWING_FAILURE", "Swing Failure Pattern", STRUCTURE, EVENT) { ctx ->
        val s = ctx.primary
        val last = s.last ?: return@trigger null
        val sh = priorSwingHigh(s)
        val sl = priorSwingLow(s)
        when {
            sh != null && last.high > sh.second && last.close < sh.second -> TriggerEvent(
                "SWING_FAILURE", s.tf, "ราคาทะลุ swing high ${p(sh.second)} แล้วปิดกลับลงมาใต้ (SFP)", "SELL", sh.second
            )
            sl != null && last.low < sl.second && last.close > sl.second -> TriggerEvent(
                "SWING_FAILURE", s.tf, "ราคาหลุด swing low ${p(sl.second)} แล้วปิดกลับขึ้นมาเหนือ (SFP)", "BUY", sl.second
            )
            else -> null
        }
    },

    trigger("HL_LH_CONFIRMED", "ยืนยัน Higher Low / Lower High", STRUCTURE, EVENT) { ctx ->
        val s = ctx.primary
        if (s.swingLowJustConfirmed && s.swingLows.size >= 2) {
            val a = s.swingLows[s.swingLows.size - 2].second
            val b = s.swingLows.last().second
            if (b > a) return@trigger TriggerEvent("HL_LH_CONFIRMED", s.tf, "ยืนยัน Higher Low ที่ ${p(b)} (สูงกว่า ${p(a)})", "BUY", b)
        }
        if (s.swingHighJustConfirmed && s.swingHighs.size >= 2) {
            val a = s.swingHighs[s.swingHighs.size - 2].second
            val b = s.swingHighs.last().second
            if (b < a) return@trigger TriggerEvent("HL_LH_CONFIRMED", s.tf, "ยืนยัน Lower High ที่ ${p(b)} (ต่ำกว่า ${p(a)})", "SELL", b)
        }
        null
    },

    trigger("INTERNAL_EXTERNAL_MISMATCH", "โครงสร้างย่อยสวนโครงสร้างใหญ่", STRUCTURE, STATE) { ctx ->
        val local = ctx.primary.structure?.trend?.lastOrNull() ?: return@trigger null
        val major = ctx.h1.structure?.trend?.lastOrNull() ?: return@trigger null
        if (local == 0 || major == 0 || local == major) return@trigger null
        TriggerEvent(
            "INTERNAL_EXTERNAL_MISMATCH", ctx.primaryTf,
            "โครงสร้าง ${tfLabel(ctx.primaryTf)} เป็น${if (local > 0) "ขาขึ้น" else "ขาลง"} สวนกับ H1 ที่เป็น${if (major > 0) "ขาขึ้น" else "ขาลง"} (อาจเป็นแค่การย่อตัว)"
        )
    },

    trigger("RANGE_FORMED", "ราคาอยู่ในกรอบแคบ", STRUCTURE, STATE, minBars = 40) { ctx ->
        val s = ctx.primary
        val hi = s.highest(20) ?: return@trigger null
        val lo = s.lowest(20) ?: return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        if (hi - lo > 2.5 * atr) return@trigger null
        TriggerEvent("RANGE_FORMED", s.tf, "ราคาอยู่ในกรอบ ${p(lo)}–${p(hi)} (${"%.1f".format((hi - lo) / atr)}×ATR) มา 20 แท่ง")
    },

    trigger("RANGE_BREAKOUT", "หลุดกรอบราคา", STRUCTURE, EVENT, minBars = 40) { ctx ->
        val s = ctx.primary
        val last = s.last ?: return@trigger null
        val hi = s.highest(20, excludeLast = true) ?: return@trigger null
        val lo = s.lowest(20, excludeLast = true) ?: return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        if (hi - lo > 3.0 * atr) return@trigger null
        when {
            last.close > hi -> TriggerEvent("RANGE_BREAKOUT", s.tf, "ปิดทะลุกรอบบน ${p(hi)} หลังสะสมในกรอบแคบ", "BUY", hi)
            last.close < lo -> TriggerEvent("RANGE_BREAKOUT", s.tf, "ปิดหลุดกรอบล่าง ${p(lo)} หลังสะสมในกรอบแคบ", "SELL", lo)
            else -> null
        }
    },

    trigger("FAILED_BREAKOUT", "Breakout ล้มเหลว (trap)", STRUCTURE, EVENT, minBars = 40) { ctx ->
        val s = ctx.primary
        if (s.n < 25) return@trigger null
        val last = s.bars[s.n - 1]
        val brk = s.bars[s.n - 2]
        val window = s.bars.subList(s.n - 22, s.n - 2)
        val hi = window.maxOf { it.high }
        val lo = window.minOf { it.low }
        when {
            brk.close > hi && last.close < hi -> TriggerEvent("FAILED_BREAKOUT", s.tf, "ทะลุกรอบบน ${p(hi)} แล้วกลับเข้ากรอบทันที (bull trap)", "SELL", hi)
            brk.close < lo && last.close > lo -> TriggerEvent("FAILED_BREAKOUT", s.tf, "หลุดกรอบล่าง ${p(lo)} แล้วกลับเข้ากรอบทันที (bear trap)", "BUY", lo)
            else -> null
        }
    }
)

// ═════════════════════════════════════════════════════════════════════════════
// B. จุดสำคัญ / โซนราคา (Key Levels)
// ═════════════════════════════════════════════════════════════════════════════

/** แตะระดับจาก digest ครั้งแรก (กรองตามชนิด) */
private fun digestLevelTouch(
    ctx: WakeContext, id: String, kindMatch: (String) -> Boolean, describe: String
): TriggerEvent? {
    val d = ctx.digest ?: return null
    val s = ctx.primary
    val levels = (d.levelsAbove + d.levelsBelow).filter { kindMatch(it.kind) }
    val hit = levels.firstOrNull { firstTouch(s, it.price) } ?: return null
    val dir = when {
        hit.kind.contains("Demand", true) || hit.kind.contains("bull", true) || hit.kind.contains("EQL") ||
            hit.kind.startsWith("swing L") -> "BUY"
        hit.kind.contains("Supply", true) || hit.kind.contains("bear", true) || hit.kind.contains("EQH") ||
            hit.kind.startsWith("swing H") -> "SELL"
        else -> "NEUTRAL"
    }
    return TriggerEvent(id, s.tf, "ราคาแตะ$describe ${hit.kind} ที่ ${p(hit.price)}", dir, hit.price)
}

/**
 * High/Low ของวัน-สัปดาห์ก่อนหน้า (ตามขอบวันเทรดของตลาดนั้น)
 *
 * อ้างอิง "วันนี้" จากเวลาปัจจุบัน ไม่ใช่จากแท่งสุดท้าย — เดิมใช้ช่วงรองสุดท้ายในข้อมูล
 * ชั่วโมงแรกของวันใหม่ (ยังไม่มีแท่ง H1 ของวันนี้ปิด) จึงได้ High/Low ของ "เมื่อวานซืน"
 * (สัปดาห์: 4 ชม. แรกของสัปดาห์ได้ของ 2 สัปดาห์ก่อน)
 */
private fun previousPeriod(ctx: WakeContext, weekly: Boolean): WakeMath.Period? {
    val shift = WakeMath.sessionShiftMs(ctx.symbol)
    val keyOf: (Long) -> Long = { if (weekly) WakeMath.weekKey(it, shift) else WakeMath.dayKey(it, shift) }
    val bars = if (weekly) ctx.h4.bars else ctx.h1.bars
    val current = keyOf(ctx.nowMs)
    val ps = WakeMath.periods(bars, keyOf)
    val prev = ps.lastOrNull { it.key < current } ?: return null
    // ต้องมีข้อมูลครบทั้งช่วง — ช่วงแรกสุดในข้อมูลอาจถูกตัดหัว
    return if (ps.first() === prev) null else prev
}

/** ขาล่าสุด (swing low → swing high หรือกลับกัน) สำหรับ Fibonacci */
private fun lastLeg(s: Series): Triple<Double, Double, Boolean>? {
    val h = s.swingHighs.lastOrNull() ?: return null
    val l = s.swingLows.lastOrNull() ?: return null
    // true = ขาขึ้น (low มาก่อน high)
    return Triple(l.second, h.second, l.first < h.first)
}

internal val LEVEL_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("KEY_LEVEL_TOUCH", "ราคาอยู่ที่จุดสำคัญของโครงสร้าง", KEY_LEVEL, STATE) { ctx ->
        val hit = ctx.digest?.keyZoneHit?.takeIf { it.isNotBlank() } ?: return@trigger null
        val dir = when {
            hit.contains("Demand", true) || hit.contains("EQL") || hit.contains("swing L") -> "BUY"
            hit.contains("Supply", true) || hit.contains("EQH") || hit.contains("swing H") -> "SELL"
            else -> "NEUTRAL"
        }
        TriggerEvent("KEY_LEVEL_TOUCH", "15m", hit, dir)
    },

    trigger("OB_TOUCH", "แตะ Order Block", KEY_LEVEL, EVENT) {
        digestLevelTouch(it, "OB_TOUCH", { k -> k.contains("OB") }, " Order Block")
    },

    trigger("OB_MITIGATED", "Order Block ถูกทะลุ (หมดแรง)", KEY_LEVEL, EVENT) { ctx ->
        // เทียบกับ OB ที่เห็นในรอบก่อน (memory) — ถ้าหายไปและราคาผ่านไปแล้ว = ถูกทะลุ
        val prev = ctx.memory["ob_levels"]?.split(",")?.mapNotNull { e ->
            val parts = e.split(":"); if (parts.size != 2) null else parts[0] to (parts[1].toDoubleOrNull() ?: return@mapNotNull null)
        } ?: return@trigger null
        val d = ctx.digest ?: return@trigger null
        val now = (d.levelsAbove + d.levelsBelow).filter { it.kind.contains("OB") }.map { it.price }
        val last = ctx.primary.last ?: return@trigger null
        val gone = prev.firstOrNull { (side, lvl) ->
            now.none { abs(it - lvl) < 1e-6 } &&
                ((side == "D" && last.close < lvl) || (side == "S" && last.close > lvl))
        } ?: return@trigger null
        val isDemand = gone.first == "D"
        TriggerEvent(
            "OB_MITIGATED", ctx.primaryTf,
            "${if (isDemand) "Demand" else "Supply"} OB ที่ ${p(gone.second)} ถูกราคาทะลุผ่าน — โซนหมดความหมาย",
            if (isDemand) "SELL" else "BUY", gone.second
        )
    },

    trigger("FVG_ENTER", "ราคาเข้า Fair Value Gap", KEY_LEVEL, EVENT) { ctx ->
        val s = ctx.primary
        if (s.n < 10) return@trigger null
        val f = UnifiedSmcSignals.fvgTouch(s.bars)
        val i = s.n - 1
        when {
            f.inBull[i] && !f.inBull[i - 1] -> TriggerEvent("FVG_ENTER", s.tf, "ราคาย่อเข้า Bullish FVG (mid ${p(f.bullMid[i])})", "BUY", f.bullMid[i])
            f.inBear[i] && !f.inBear[i - 1] -> TriggerEvent("FVG_ENTER", s.tf, "ราคาดีดเข้า Bearish FVG (mid ${p(f.bearMid[i])})", "SELL", f.bearMid[i])
            else -> null
        }
    },

    trigger("FVG_FILLED", "FVG ถูกเติมเต็ม", KEY_LEVEL, EVENT) { ctx ->
        val s = ctx.primary
        if (s.n < 10) return@trigger null
        val f = UnifiedSmcSignals.fvgTouch(s.bars)
        val i = s.n - 1
        val bullPrev = f.bullMid[i - 1]; val bearPrev = f.bearMid[i - 1]
        when {
            !bullPrev.isNaN() && f.bullMid[i] != bullPrev && s.bars[i].low <= bullPrev ->
                TriggerEvent("FVG_FILLED", s.tf, "Bullish FVG (${p(bullPrev)}) ถูกเติมเต็ม — แนวรับเชิงโครงสร้างหายไป", "NEUTRAL", bullPrev)
            !bearPrev.isNaN() && f.bearMid[i] != bearPrev && s.bars[i].high >= bearPrev ->
                TriggerEvent("FVG_FILLED", s.tf, "Bearish FVG (${p(bearPrev)}) ถูกเติมเต็ม — แนวต้านเชิงโครงสร้างหายไป", "NEUTRAL", bearPrev)
            else -> null
        }
    },

    trigger("BREAKER_RETEST", "Retest ระดับที่ทำให้เกิด CHoCH", KEY_LEVEL, EVENT) { ctx ->
        val s = ctx.primary
        val st = s.structure ?: return@trigger null
        val last = s.last ?: return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val k = (s.n - 2 downTo maxOf(0, s.n - 40)).firstOrNull { st.chochUp[it] || st.chochDn[it] } ?: return@trigger null
        if (s.n - 1 - k < 3) return@trigger null
        if (st.chochUp[k]) {
            val lvl = s.swingHighs.lastOrNull { it.first < k }?.second ?: return@trigger null
            if (last.low <= lvl + 0.2 * atr && last.close > lvl)
                return@trigger TriggerEvent("BREAKER_RETEST", s.tf, "ราคากลับมา retest ระดับ ${p(lvl)} ที่ทำให้เกิด CHoCH ขาขึ้น แล้วยืนได้", "BUY", lvl)
        } else {
            val lvl = s.swingLows.lastOrNull { it.first < k }?.second ?: return@trigger null
            if (last.high >= lvl - 0.2 * atr && last.close < lvl)
                return@trigger TriggerEvent("BREAKER_RETEST", s.tf, "ราคากลับมา retest ระดับ ${p(lvl)} ที่ทำให้เกิด CHoCH ขาลง แล้วถูกกดลง", "SELL", lvl)
        }
        null
    },

    trigger("PDH_PDL_TOUCH", "แตะ High/Low เมื่อวาน", KEY_LEVEL, EVENT) { ctx ->
        val pd = previousPeriod(ctx, weekly = false) ?: return@trigger null
        val s = ctx.primary
        when {
            firstTouch(s, pd.high) -> TriggerEvent("PDH_PDL_TOUCH", s.tf, "ราคาแตะ High เมื่อวาน (PDH ${p(pd.high)})", "NEUTRAL", pd.high)
            firstTouch(s, pd.low) -> TriggerEvent("PDH_PDL_TOUCH", s.tf, "ราคาแตะ Low เมื่อวาน (PDL ${p(pd.low)})", "NEUTRAL", pd.low)
            else -> null
        }
    },

    trigger("PWH_PWL_TOUCH", "แตะ High/Low สัปดาห์ก่อน", KEY_LEVEL, EVENT) { ctx ->
        val pw = previousPeriod(ctx, weekly = true) ?: return@trigger null
        val s = ctx.primary
        when {
            firstTouch(s, pw.high) -> TriggerEvent("PWH_PWL_TOUCH", s.tf, "ราคาแตะ High สัปดาห์ก่อน (PWH ${p(pw.high)})", "NEUTRAL", pw.high)
            firstTouch(s, pw.low) -> TriggerEvent("PWH_PWL_TOUCH", s.tf, "ราคาแตะ Low สัปดาห์ก่อน (PWL ${p(pw.low)})", "NEUTRAL", pw.low)
            else -> null
        }
    },

    trigger("ROUND_NUMBER", "แตะเลขกลม", KEY_LEVEL, EVENT) { ctx ->
        val s = ctx.primary
        val last = s.last ?: return@trigger null
        val step = WakeMath.roundStep(last.close).takeIf { it > 0 } ?: return@trigger null
        val lvl = kotlin.math.round(last.close / step) * step
        if (!firstTouch(s, lvl)) return@trigger null
        TriggerEvent("ROUND_NUMBER", s.tf, "ราคาแตะเลขกลม ${p(lvl)}", "NEUTRAL", lvl)
    },

    trigger("FIB_GOLDEN_POCKET", "ย่อถึง Fibonacci 0.618–0.65", KEY_LEVEL, EVENT) { ctx ->
        val s = ctx.primary
        val (lo, hi, upLeg) = lastLeg(s) ?: return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val rng = hi - lo
        if (rng < 2 * atr) return@trigger null
        val last = s.last ?: return@trigger null
        val prev = s.prev ?: return@trigger null
        if (upLeg) {
            val top = hi - 0.618 * rng; val bot = hi - 0.65 * rng
            val inNow = last.low <= top && last.high >= bot
            val inPrev = prev.low <= top && prev.high >= bot
            if (inNow && !inPrev) return@trigger TriggerEvent("FIB_GOLDEN_POCKET", s.tf, "ราคาย่อเข้า Golden Pocket ${p(bot)}–${p(top)} ของขาขึ้นล่าสุด", "BUY", top)
        } else {
            val bot = lo + 0.618 * rng; val top = lo + 0.65 * rng
            val inNow = last.high >= bot && last.low <= top
            val inPrev = prev.high >= bot && prev.low <= top
            if (inNow && !inPrev) return@trigger TriggerEvent("FIB_GOLDEN_POCKET", s.tf, "ราคาดีดเข้า Golden Pocket ${p(bot)}–${p(top)} ของขาลงล่าสุด", "SELL", bot)
        }
        null
    },

    trigger("FIB_EXTENSION", "ถึงเป้า Fibonacci Extension", KEY_LEVEL, EVENT) { ctx ->
        val s = ctx.primary
        if (s.swingLows.size < 2 || s.swingHighs.isEmpty()) return@trigger null
        val a = s.swingLows[s.swingLows.size - 2]
        val b = s.swingHighs.last()
        val c = s.swingLows.last()
        // ขาขึ้นแบบ A(low) → B(high) → C(higher low)
        if (!(a.first < b.first && b.first < c.first && c.second > a.second && c.second < b.second)) return@trigger null
        val ab = b.second - a.second
        for (r in listOf(1.272, 1.618)) {
            val t = c.second + ab * r
            if (firstTouch(s, t)) return@trigger TriggerEvent("FIB_EXTENSION", s.tf, "ราคาถึงเป้า Fibonacci extension $r ที่ ${p(t)}", "NEUTRAL", t)
        }
        null
    },

    trigger("VOLUME_PROFILE_LEVEL", "แตะ POC / VAH / VAL", KEY_LEVEL, EVENT) { ctx ->
        val d = ctx.digest ?: return@trigger null
        val s = ctx.primary
        listOfNotNull(d.poc?.let { "POC" to it }, d.vah?.let { "VAH" to it }, d.val_?.let { "VAL" to it })
            .firstOrNull { firstTouch(s, it.second) }
            ?.let { TriggerEvent("VOLUME_PROFILE_LEVEL", s.tf, "ราคาแตะ ${it.first} ของ Volume Profile ที่ ${p(it.second)}", "NEUTRAL", it.second) }
    },

    trigger("PREMIUM_DISCOUNT_EXTREME", "ราคาอยู่ขอบสุดของ range H1", KEY_LEVEL, STATE, minBars = 100) { ctx ->
        val h1 = ctx.h1
        val hi = h1.highest(100) ?: return@trigger null
        val lo = h1.lowest(100) ?: return@trigger null
        if (hi <= lo) return@trigger null
        val pos = (ctx.price - lo) / (hi - lo)
        val pct = (pos * 100).toInt()
        // ในเทรนด์ที่ H4/H1/M15 เรียงกัน ราคาอยู่ขอบกรอบตามทิศเทรนด์เป็นเรื่องปกติ ไม่ใช่สัญญาณกลับตัว
        // (เดิมติดป้าย SELL ทุกครั้ง → AI แจ้ง SELL สวนขาขึ้น 9 ครั้ง ชน SL 8/8)
        when {
            pos >= 0.9 && ctx.htfAlignment == 1 -> TriggerEvent("PREMIUM_DISCOUNT_EXTREME", "1h",
                "ราคาอยู่บนสุดของ range H1 ($pct%) ในขาขึ้นที่ H4/H1/M15 เรียงกัน — บริบทของเทรนด์แรง ไม่ใช่สัญญาณขาย", "NEUTRAL")
            pos <= 0.1 && ctx.htfAlignment == -1 -> TriggerEvent("PREMIUM_DISCOUNT_EXTREME", "1h",
                "ราคาอยู่ล่างสุดของ range H1 ($pct%) ในขาลงที่ H4/H1/M15 เรียงกัน — บริบทของเทรนด์แรง ไม่ใช่สัญญาณซื้อ", "NEUTRAL")
            pos >= 0.9 -> TriggerEvent("PREMIUM_DISCOUNT_EXTREME", "1h", "ราคาอยู่โซน Premium สุดขอบ ($pct% ของ range H1)", "SELL")
            pos <= 0.1 -> TriggerEvent("PREMIUM_DISCOUNT_EXTREME", "1h", "ราคาอยู่โซน Discount สุดขอบ ($pct% ของ range H1)", "BUY")
            else -> null
        }
    },

    trigger("DAILY_PIVOT_TOUCH", "แตะ Pivot รายวัน", KEY_LEVEL, EVENT) { ctx ->
        val pd = previousPeriod(ctx, weekly = false) ?: return@trigger null
        val pv = com.skyliner2008.jarvis.tools.trading.TaIndicators.pivotPoints(pd.high, pd.low, pd.close)
        val s = ctx.primary
        listOf("Pivot" to pv.pivot, "R1" to pv.r1, "S1" to pv.s1, "R2" to pv.r2, "S2" to pv.s2)
            .firstOrNull { firstTouch(s, it.second) }
            ?.let { TriggerEvent("DAILY_PIVOT_TOUCH", s.tf, "ราคาแตะ ${it.first} รายวันที่ ${p(it.second)}", "NEUTRAL", it.second) }
    },

    trigger("SR_FLIP_RETEST", "แนวรับ/แนวต้านสลับบทบาทแล้ว retest", KEY_LEVEL, EVENT) { ctx ->
        val s = ctx.primary
        val last = s.last ?: return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        if (s.n < 40) return@trigger null
        val recent = s.bars.subList(s.n - 30, s.n - 3)
        // swing high ที่ถูกปิดทะลุขึ้นใน 30 แท่งล่าสุด → กลายเป็นแนวรับ
        val flippedUp = s.swingHighs.lastOrNull { (idx, lvl) -> idx < s.n - 30 && recent.any { it.close > lvl } }
        if (flippedUp != null) {
            val lvl = flippedUp.second
            if (last.low <= lvl + 0.25 * atr && last.low >= lvl - 0.5 * atr && last.close > lvl)
                return@trigger TriggerEvent("SR_FLIP_RETEST", s.tf, "แนวต้านเดิม ${p(lvl)} กลายเป็นแนวรับ — ราคากลับมาทดสอบแล้วยืนได้", "BUY", lvl)
        }
        val flippedDn = s.swingLows.lastOrNull { (idx, lvl) -> idx < s.n - 30 && recent.any { it.close < lvl } }
        if (flippedDn != null) {
            val lvl = flippedDn.second
            if (last.high >= lvl - 0.25 * atr && last.high <= lvl + 0.5 * atr && last.close < lvl)
                return@trigger TriggerEvent("SR_FLIP_RETEST", s.tf, "แนวรับเดิม ${p(lvl)} กลายเป็นแนวต้าน — ราคากลับมาทดสอบแล้วถูกกดลง", "SELL", lvl)
        }
        null
    }
)

// ═════════════════════════════════════════════════════════════════════════════
// C. สภาพคล่อง (Liquidity)
// ═════════════════════════════════════════════════════════════════════════════

/** คู่ swing ล่าสุดที่เท่ากัน (EQH/EQL) */
private fun equalPool(pts: List<Pair<Int, Double>>, atr: Double): Double? {
    if (pts.size < 2 || atr <= 0) return null
    val a = pts[pts.size - 2].second
    val b = pts.last().second
    return if (abs(a - b) <= 0.15 * atr) (a + b) / 2 else null
}

internal val LIQUIDITY_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("EQ_POOL_FORMED", "เกิด Equal High / Low (สะสมสภาพคล่อง)", LIQUIDITY_SWEEP, STATE) { ctx ->
        val s = ctx.primary
        val atr = ctx.atr
        val eqh = equalPool(s.swingHighs, atr)
        val eql = equalPool(s.swingLows, atr)
        val price = ctx.price
        when {
            eqh != null && eqh > price -> TriggerEvent("EQ_POOL_FORMED", s.tf, "มี Equal Highs ที่ ${p(eqh)} เหนือราคา — แหล่งสภาพคล่องที่ราคาอาจวิ่งไปกวาด", "NEUTRAL", eqh)
            eql != null && eql < price -> TriggerEvent("EQ_POOL_FORMED", s.tf, "มี Equal Lows ที่ ${p(eql)} ใต้ราคา — แหล่งสภาพคล่องที่ราคาอาจวิ่งไปกวาด", "NEUTRAL", eql)
            else -> null
        }
    },

    trigger("EQ_POOL_SWEPT", "กวาด Equal High / Low", LIQUIDITY_SWEEP, EVENT) { ctx ->
        val s = ctx.primary
        val last = s.last ?: return@trigger null
        val prev = s.prev ?: return@trigger null
        val atr = ctx.atr
        val hs = s.swingHighs.filter { it.first < s.n - 1 }
        val ls = s.swingLows.filter { it.first < s.n - 1 }
        val eqh = equalPool(hs, atr)
        val eql = equalPool(ls, atr)
        when {
            eqh != null && last.high > eqh && prev.high <= eqh -> TriggerEvent(
                "EQ_POOL_SWEPT", s.tf, "ราคากวาด Equal Highs ที่ ${p(eqh)}${if (last.close < eqh) " แล้วปิดกลับลงมา" else ""}",
                if (last.close < eqh) "SELL" else "NEUTRAL", eqh
            )
            eql != null && last.low < eql && prev.low >= eql -> TriggerEvent(
                "EQ_POOL_SWEPT", s.tf, "ราคากวาด Equal Lows ที่ ${p(eql)}${if (last.close > eql) " แล้วปิดกลับขึ้นมา" else ""}",
                if (last.close > eql) "BUY" else "NEUTRAL", eql
            )
            else -> null
        }
    },

    trigger("LIQUIDITY_SWEEP_REJECTION", "Sweep สภาพคล่องแล้วถูกปฏิเสธ", LIQUIDITY_SWEEP, EVENT, minBars = 15) { ctx ->
        val s = ctx.primary
        if (s.n < 15 || ctx.atr <= 0.0) return@trigger null
        val last = s.bars[s.n - 1]
        val prior = s.bars.subList(s.n - 11, s.n - 1)
        val priorHigh = prior.maxOf { it.high }
        val priorLow = prior.minOf { it.low }
        val wickFloor = 1.5 * maxOf(WakeMath.body(last), ctx.atr * 0.2)
        when {
            last.low < priorLow && WakeMath.lowerWick(last) >= wickFloor -> TriggerEvent(
                "LIQUIDITY_SWEEP_REJECTION", s.tf, "ราคา sweep หลุด low ${p(priorLow)} แล้วเกิดไส้ล่างปฏิเสธ", "BUY", priorLow
            )
            last.high > priorHigh && WakeMath.upperWick(last) >= wickFloor -> TriggerEvent(
                "LIQUIDITY_SWEEP_REJECTION", s.tf, "ราคา sweep ทะลุ high ${p(priorHigh)} แล้วเกิดไส้บนปฏิเสธ", "SELL", priorHigh
            )
            else -> null
        }
    },

    trigger("STOP_HUNT_SPIKE", "แท่งพุ่งล่า Stop แล้วกลับตัว", LIQUIDITY_SWEEP, EVENT) { ctx ->
        val last = ctx.primary.last ?: return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val rng = WakeMath.range(last)
        if (rng < 2.5 * atr) return@trigger null
        when {
            WakeMath.lowerWick(last) >= 0.6 * rng -> TriggerEvent("STOP_HUNT_SPIKE", ctx.primaryTf, "แท่งดิ่ง ${"%.1f".format(rng / atr)}×ATR แล้วดึงกลับเกือบหมด — ล่า stop ฝั่ง buy", "BUY", last.low)
            WakeMath.upperWick(last) >= 0.6 * rng -> TriggerEvent("STOP_HUNT_SPIKE", ctx.primaryTf, "แท่งพุ่ง ${"%.1f".format(rng / atr)}×ATR แล้วถูกกดกลับเกือบหมด — ล่า stop ฝั่ง sell", "SELL", last.high)
            else -> null
        }
    },

    trigger("LIQUIDITY_VOID", "เกิดช่องว่างราคาขนาดใหญ่", LIQUIDITY_SWEEP, EVENT) { ctx ->
        val s = ctx.primary
        if (s.n < 3) return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val a = s.bars[s.n - 3]; val c = s.bars[s.n - 1]
        when {
            c.low - a.high > atr -> TriggerEvent("LIQUIDITY_VOID", s.tf, "เกิด imbalance ขาขึ้นขนาด ${"%.1f".format((c.low - a.high) / atr)}×ATR (${p(a.high)}–${p(c.low)}) ยังไม่ถูกเติม", "BUY", a.high)
            a.low - c.high > atr -> TriggerEvent("LIQUIDITY_VOID", s.tf, "เกิด imbalance ขาลงขนาด ${"%.1f".format((a.low - c.high) / atr)}×ATR (${p(c.high)}–${p(a.low)}) ยังไม่ถูกเติม", "SELL", a.low)
            else -> null
        }
    },

    trigger("SESSION_LEVEL_SWEEP", "Sweep High/Low ของ Session ก่อนหน้า", LIQUIDITY_SWEEP, EVENT, minBars = 30) { ctx ->
        val c = ctx.primary.bars
        if (c.size < 30) return@trigger null
        fun sessionOf(ts: Long) = when (WakeMath.hourUtc(ts)) { in 0..6 -> 0; in 7..11 -> 1; in 12..20 -> 2; else -> 3 }
        fun name(x: Int) = when (x) { 0 -> "Asia"; 1 -> "London"; 2 -> "New York"; else -> "After-hours" }
        val last = c.last()
        val cur = sessionOf(last.timestamp)
        val prevBars = ArrayList<com.skyliner2008.jarvis.tools.trading.Candle>()
        var seenDifferent = false
        var prevSession = -1
        for (bar in c.dropLast(1).asReversed()) {
            val sId = sessionOf(bar.timestamp)
            if (sId != cur && (prevSession == -1 || sId == prevSession)) {
                seenDifferent = true; prevSession = sId; prevBars += bar
            } else if (seenDifferent) break
        }
        if (prevBars.size < 3) return@trigger null
        val hi = prevBars.maxOf { it.high }; val lo = prevBars.minOf { it.low }
        when {
            last.low < lo && last.close > lo -> TriggerEvent("SESSION_LEVEL_SWEEP", ctx.primaryTf, "กวาดหลุด Low ของ session ${name(prevSession)} (${p(lo)}) แล้วปิดกลับขึ้นมา", "BUY", lo)
            last.high > hi && last.close < hi -> TriggerEvent("SESSION_LEVEL_SWEEP", ctx.primaryTf, "กวาดทะลุ High ของ session ${name(prevSession)} (${p(hi)}) แล้วปิดกลับลงมา", "SELL", hi)
            else -> null
        }
    },

    trigger("ASIA_RANGE_BREAK", "หลุดกรอบ Asia ช่วง London", LIQUIDITY_SWEEP, EVENT) { ctx ->
        val s = ctx.m15
        val last = s.last ?: return@trigger null
        val prev = s.prev ?: return@trigger null
        val h = WakeMath.hourUtc(last.timestamp)
        if (h !in 7..11) return@trigger null
        val day = last.timestamp.floorDiv(WakeMath.DAY)
        val asia = s.bars.filter { it.timestamp.floorDiv(WakeMath.DAY) == day && WakeMath.hourUtc(it.timestamp) in 0..6 }
        if (asia.size < 8) return@trigger null
        val hi = asia.maxOf { it.high }; val lo = asia.minOf { it.low }
        when {
            prev.close <= hi && last.close > hi -> TriggerEvent("ASIA_RANGE_BREAK", "15m", "London ทะลุกรอบบนของ Asia (${p(hi)})", "BUY", hi)
            prev.close >= lo && last.close < lo -> TriggerEvent("ASIA_RANGE_BREAK", "15m", "London หลุดกรอบล่างของ Asia (${p(lo)})", "SELL", lo)
            else -> null
        }
    }
)

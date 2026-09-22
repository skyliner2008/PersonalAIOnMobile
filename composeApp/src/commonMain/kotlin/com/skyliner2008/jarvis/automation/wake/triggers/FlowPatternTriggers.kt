package com.skyliner2008.jarvis.automation.wake.triggers

import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.PATTERN
import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.PRICE_ACTION
import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.VOLUME
import com.skyliner2008.jarvis.automation.wake.Series
import com.skyliner2008.jarvis.automation.wake.TriggerEvent
import com.skyliner2008.jarvis.automation.wake.TriggerKind.EVENT
import com.skyliner2008.jarvis.automation.wake.TriggerKind.STATE
import com.skyliner2008.jarvis.automation.wake.WakeContext
import com.skyliner2008.jarvis.automation.wake.WakeTrigger
import com.skyliner2008.jarvis.automation.wake.p
import com.skyliner2008.jarvis.automation.wake.trigger
import com.skyliner2008.jarvis.automation.wake.triggers.WakeMath.body
import com.skyliner2008.jarvis.automation.wake.triggers.WakeMath.isBear
import com.skyliner2008.jarvis.automation.wake.triggers.WakeMath.isBull
import com.skyliner2008.jarvis.automation.wake.triggers.WakeMath.lineAt
import com.skyliner2008.jarvis.automation.wake.triggers.WakeMath.lowerWick
import com.skyliner2008.jarvis.automation.wake.triggers.WakeMath.range
import com.skyliner2008.jarvis.automation.wake.triggers.WakeMath.upperWick
import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import kotlin.math.abs
import kotlin.math.sqrt

// ═════════════════════════════════════════════════════════════════════════════
// G. ปริมาณ / แรงซื้อขาย
//
// หมายเหตุ: volume ของ FX/ทองจาก TradingView เป็น tick volume ไม่ใช่ volume จริง
// ใช้ได้แต่แม่นน้อยกว่าคริปโต — ปล่อยให้ระบบเรียนรู้ตัดสินว่าใช้ได้กับสินทรัพย์ไหน
// ═════════════════════════════════════════════════════════════════════════════

/** VWAP รายเซสชัน + ส่วนเบี่ยงเบนถ่วงปริมาณ ณ แท่ง n-1-back */
private fun vwapBands(ctx: WakeContext, s: Series, back: Int): Triple<Double, Double, Double>? {
    val bars = s.bars.dropLast(back)
    if (bars.isEmpty()) return null
    val anchor = TaIndicators.lastSessionAnchor(bars, TaIndicators.sessionOffsetHoursFor(ctx.symbol)) ?: return null
    var pv = 0.0; var vol = 0.0
    val sess = bars.filter { it.timestamp >= anchor }
    for (c in sess) { val tp = (c.high + c.low + c.close) / 3; pv += tp * c.volume; vol += c.volume }
    if (vol <= 0 || sess.size < 3) return null
    val vwap = pv / vol
    var varSum = 0.0
    for (c in sess) { val tp = (c.high + c.low + c.close) / 3; varSum += c.volume * (tp - vwap) * (tp - vwap) }
    val sd = sqrt(varSum / vol)
    return Triple(vwap, vwap + 2 * sd, vwap - 2 * sd)
}

private fun deltaOf(c: Candle): Double {
    val r = range(c)
    return if (r <= 0) 0.0 else c.volume * (c.close - c.open) / r
}

internal val VOLUME_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("VOLUME_SPIKE", "Volume พุ่งผิดปกติ", VOLUME, EVENT, minBars = 25) { ctx ->
        val s = ctx.primary
        val last = s.last ?: return@trigger null
        val avg = s.avgVolume() ?: return@trigger null
        val r = last.volume / avg
        if (r < 2.0) return@trigger null
        val dir = if (isBull(last)) "BUY" else if (isBear(last)) "SELL" else "NEUTRAL"
        TriggerEvent("VOLUME_SPIKE", s.tf, "Volume ${"%.1f".format(r)} เท่าของค่าเฉลี่ย บนแท่ง${if (dir == "BUY") "เขียว" else if (dir == "SELL") "แดง" else "doji"}", dir)
    },

    trigger("VOLUME_ABSORPTION", "Volume ดูดซับ (แท่งแคบ ปริมาณสูง)", VOLUME, EVENT, minBars = 25) { ctx ->
        val s = ctx.primary
        val last = s.last ?: return@trigger null
        val avg = s.avgVolume() ?: return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val r = last.volume / avg
        if (r < 1.8 || body(last) > 0.35 * atr) return@trigger null
        val dir = if (last.close <= last.open) "BUY" else "SELL"
        TriggerEvent("VOLUME_ABSORPTION", s.tf, "Volume ${"%.1f".format(r)}x แต่แท่งแคบ — มีการดูดซับแรง${if (dir == "BUY") "ขาย" else "ซื้อ"}", dir, last.close)
    },

    trigger("CLIMAX_VOLUME", "Climax Volume ที่จุดสุดโต่ง", VOLUME, EVENT, minBars = 25) { ctx ->
        val s = ctx.primary
        val last = s.last ?: return@trigger null
        val avg = s.avgVolume() ?: return@trigger null
        if (last.volume < 3 * avg) return@trigger null
        val hi = s.highest(20) ?: return@trigger null; val lo = s.lowest(20) ?: return@trigger null
        when {
            last.high >= hi && upperWick(last) > body(last) -> TriggerEvent("CLIMAX_VOLUME", s.tf, "Buying climax — volume ${"%.1f".format(last.volume / avg)}x ที่ยอด 20 แท่งพร้อมไส้บนยาว", "SELL", last.high)
            last.low <= lo && lowerWick(last) > body(last) -> TriggerEvent("CLIMAX_VOLUME", s.tf, "Selling climax — volume ${"%.1f".format(last.volume / avg)}x ที่ก้น 20 แท่งพร้อมไส้ล่างยาว", "BUY", last.low)
            else -> null
        }
    },

    trigger("VOLUME_DRYUP_AT_LEVEL", "Volume แห้งที่โซนสำคัญ", VOLUME, STATE, minBars = 25) { ctx ->
        val s = ctx.primary
        val d = ctx.digest ?: return@trigger null
        val avg = s.avgVolume(20, excludeLast = false) ?: return@trigger null
        if (s.n < 3 || s.volumes.takeLast(3).any { it >= 0.5 * avg }) return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val lvl = (d.levelsAbove + d.levelsBelow).firstOrNull { abs(it.price - ctx.price) <= 0.3 * atr } ?: return@trigger null
        TriggerEvent("VOLUME_DRYUP_AT_LEVEL", s.tf, "Volume แห้ง 3 แท่งติดขณะอยู่ที่ ${lvl.kind} ${p(lvl.price)} — ไม่มีแรงสู้ที่โซนนี้", "NEUTRAL", lvl.price)
    },

    trigger("OBV_DIVERGENCE", "OBV Divergence", VOLUME, EVENT, minBars = 60) { ctx ->
        val s = ctx.primary
        val obv = TaIndicators.obvSeries(s.closes, s.volumes)
        val dir = WakeMath.divergence(s, obv, hidden = false) ?: return@trigger null
        TriggerEvent("OBV_DIVERGENCE", s.tf, "OBV ${if (dir == "BUY") "ไม่ทำ low ใหม่ตามราคา — มีการสะสม" else "ไม่ทำ high ใหม่ตามราคา — มีการกระจายของ"}", dir)
    },

    trigger("OBV_LEADING_BREAKOUT", "OBV ทำจุดสูง/ต่ำใหม่ก่อนราคา", VOLUME, EVENT, minBars = 40) { ctx ->
        val s = ctx.primary
        if (s.n < 22) return@trigger null
        val obv = TaIndicators.obvSeries(s.closes, s.volumes)
        val win = obv.subList(obv.size - 21, obv.size - 1)
        val o = obv.last(); val op = obv[obv.size - 2]
        val hi = s.highest(20, excludeLast = true) ?: return@trigger null
        val lo = s.lowest(20, excludeLast = true) ?: return@trigger null
        val c = s.last?.close ?: return@trigger null
        when {
            o > win.max() && op <= win.max() && c < hi -> TriggerEvent("OBV_LEADING_BREAKOUT", s.tf, "OBV ทำจุดสูงใหม่ 20 แท่ง แต่ราคายังไม่ทะลุ — เงินเข้าล่วงหน้า", "BUY")
            o < win.min() && op >= win.min() && c > lo -> TriggerEvent("OBV_LEADING_BREAKOUT", s.tf, "OBV ทำจุดต่ำใหม่ 20 แท่ง แต่ราคายังไม่หลุด — เงินออกล่วงหน้า", "SELL")
            else -> null
        }
    },

    trigger("VWAP_RECLAIM", "ราคาตัดผ่าน VWAP", VOLUME, EVENT, minBars = 20) { ctx ->
        val s = ctx.primary
        val now = vwapBands(ctx, s, 0)?.first ?: return@trigger null
        val before = vwapBands(ctx, s, 1)?.first ?: return@trigger null
        val c1 = s.last?.close ?: return@trigger null; val c0 = s.prev?.close ?: return@trigger null
        when {
            WakeMath.crossUp(c0, c1, before, now) -> TriggerEvent("VWAP_RECLAIM", s.tf, "ราคาปิดกลับขึ้นเหนือ VWAP (${p(now)})", "BUY", now)
            WakeMath.crossDown(c0, c1, before, now) -> TriggerEvent("VWAP_RECLAIM", s.tf, "ราคาปิดหลุดใต้ VWAP (${p(now)})", "SELL", now)
            else -> null
        }
    },

    trigger("VWAP_BAND_TOUCH", "แตะ VWAP ±2σ", VOLUME, EVENT, minBars = 20) { ctx ->
        val s = ctx.primary
        val (_, up, dn) = vwapBands(ctx, s, 0) ?: return@trigger null
        when {
            WakeMath.firstTouch(s, up) -> TriggerEvent("VWAP_BAND_TOUCH", s.tf, "ราคาแตะ VWAP +2σ (${p(up)}) — ยืดตัวสุดโต่งจากราคาเฉลี่ย", "SELL", up)
            WakeMath.firstTouch(s, dn) -> TriggerEvent("VWAP_BAND_TOUCH", s.tf, "ราคาแตะ VWAP −2σ (${p(dn)}) — ยืดตัวสุดโต่งจากราคาเฉลี่ย", "BUY", dn)
            else -> null
        }
    },

    trigger("DELTA_SHIFT", "แรงซื้อขายโดยประมาณกลับทิศ", VOLUME, EVENT, minBars = 30) { ctx ->
        val s = ctx.primary
        if (s.n < 22) return@trigger null
        val recent = s.bars.takeLast(10).sumOf { deltaOf(it) }
        val before = s.bars.subList(s.n - 20, s.n - 10).sumOf { deltaOf(it) }
        val recentPrev = s.bars.subList(s.n - 11, s.n - 1).sumOf { deltaOf(it) }
        when {
            recent > 0 && recentPrev <= 0 && before < 0 -> TriggerEvent("DELTA_SHIFT", s.tf, "แรงซื้อสุทธิ 10 แท่งล่าสุดกลับเป็นบวก (ก่อนหน้าเป็นแรงขาย)", "BUY")
            recent < 0 && recentPrev >= 0 && before > 0 -> TriggerEvent("DELTA_SHIFT", s.tf, "แรงขายสุทธิ 10 แท่งล่าสุดกลับเป็นลบ (ก่อนหน้าเป็นแรงซื้อ)", "SELL")
            else -> null
        }
    }
)

// ═════════════════════════════════════════════════════════════════════════════
// H. Price Action / แท่งเทียน
// ═════════════════════════════════════════════════════════════════════════════

/** ใกล้แนวรับ/แนวต้าน (จาก digest ถ้ามี ไม่งั้นใช้ขอบ 20 แท่ง) */
private fun nearSupport(ctx: WakeContext, price: Double): Boolean {
    val atr = ctx.atr
    ctx.digest?.levelsBelow?.let { lv -> if (lv.any { abs(it.price - price) <= 0.5 * atr }) return true }
    val lo = ctx.primary.lowest(20) ?: return false
    return abs(price - lo) <= 0.5 * atr
}

private fun nearResistance(ctx: WakeContext, price: Double): Boolean {
    val atr = ctx.atr
    ctx.digest?.levelsAbove?.let { lv -> if (lv.any { abs(it.price - price) <= 0.5 * atr }) return true }
    val hi = ctx.primary.highest(20) ?: return false
    return abs(price - hi) <= 0.5 * atr
}

internal val PRICE_ACTION_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("ENGULFING_AT_LEVEL", "Engulfing ที่โซนสำคัญ", PRICE_ACTION, EVENT, minBars = 25) { ctx ->
        val s = ctx.primary
        val a = s.prev ?: return@trigger null; val b = s.last ?: return@trigger null
        val bullEng = isBear(a) && isBull(b) && b.close >= a.open && b.open <= a.close
        val bearEng = isBull(a) && isBear(b) && b.close <= a.open && b.open >= a.close
        when {
            bullEng && nearSupport(ctx, b.low) -> TriggerEvent("ENGULFING_AT_LEVEL", s.tf, "Bullish engulfing ที่แนวรับ", "BUY", b.low)
            bearEng && nearResistance(ctx, b.high) -> TriggerEvent("ENGULFING_AT_LEVEL", s.tf, "Bearish engulfing ที่แนวต้าน", "SELL", b.high)
            else -> null
        }
    },

    trigger("PIN_BAR", "Pin bar (Hammer / Shooting star)", PRICE_ACTION, EVENT, minBars = 20) { ctx ->
        val c = ctx.primary.last ?: return@trigger null
        val r = range(c)
        if (r <= 0 || r < 0.5 * ctx.atr) return@trigger null
        when {
            lowerWick(c) >= 2 * body(c) && lowerWick(c) >= 0.6 * r -> TriggerEvent("PIN_BAR", ctx.primaryTf, "Hammer / pin bar ไส้ล่างยาว ${"%.0f".format(lowerWick(c) / r * 100)}% ของแท่ง", "BUY", c.low)
            upperWick(c) >= 2 * body(c) && upperWick(c) >= 0.6 * r -> TriggerEvent("PIN_BAR", ctx.primaryTf, "Shooting star / pin bar ไส้บนยาว ${"%.0f".format(upperWick(c) / r * 100)}% ของแท่ง", "SELL", c.high)
            else -> null
        }
    },

    trigger("INSIDE_BAR_BREAK", "หลุด Inside bar", PRICE_ACTION, EVENT, minBars = 10) { ctx ->
        val s = ctx.primary
        if (s.n < 4) return@trigger null
        val mother = s.bars[s.n - 3]; val inside = s.bars[s.n - 2]; val last = s.bars[s.n - 1]
        if (!(inside.high < mother.high && inside.low > mother.low)) return@trigger null
        when {
            last.close > mother.high -> TriggerEvent("INSIDE_BAR_BREAK", s.tf, "ปิดทะลุ mother bar ของ inside bar ขึ้น (${p(mother.high)})", "BUY", mother.high)
            last.close < mother.low -> TriggerEvent("INSIDE_BAR_BREAK", s.tf, "ปิดหลุด mother bar ของ inside bar ลง (${p(mother.low)})", "SELL", mother.low)
            else -> null
        }
    },

    trigger("STAR_PATTERN", "Morning / Evening star", PRICE_ACTION, EVENT, minBars = 20) { ctx ->
        val s = ctx.primary
        if (s.n < 4) return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val a = s.bars[s.n - 3]; val b = s.bars[s.n - 2]; val c = s.bars[s.n - 1]
        val midA = (a.open + a.close) / 2
        when {
            isBear(a) && body(a) > 0.6 * atr && body(b) < 0.3 * atr && isBull(c) && c.close > midA ->
                TriggerEvent("STAR_PATTERN", s.tf, "Morning star — แท่งแดงใหญ่ ตามด้วยแท่งเล็ก แล้วแท่งเขียวปิดเกินครึ่ง", "BUY", b.low)
            isBull(a) && body(a) > 0.6 * atr && body(b) < 0.3 * atr && isBear(c) && c.close < midA ->
                TriggerEvent("STAR_PATTERN", s.tf, "Evening star — แท่งเขียวใหญ่ ตามด้วยแท่งเล็ก แล้วแท่งแดงปิดเกินครึ่ง", "SELL", b.high)
            else -> null
        }
    },

    trigger("THREE_SOLDIERS_CROWS", "Three white soldiers / black crows", PRICE_ACTION, EVENT, minBars = 20) { ctx ->
        val s = ctx.primary
        if (s.n < 4) return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val t = s.bars.takeLast(3)
        val prior = s.bars[s.n - 4]
        val soldiers = t.all { isBull(it) && body(it) > 0.5 * atr && upperWick(it) < 0.3 * body(it) } &&
            t[1].close > t[0].close && t[2].close > t[1].close
        val crows = t.all { isBear(it) && body(it) > 0.5 * atr && lowerWick(it) < 0.3 * body(it) } &&
            t[1].close < t[0].close && t[2].close < t[1].close
        // ต้องเพิ่งเกิดครบที่แท่งนี้ (แท่งก่อนหน้าชุดไม่ใช่สีเดียวกัน)
        when {
            soldiers && !isBull(prior) -> TriggerEvent("THREE_SOLDIERS_CROWS", s.tf, "Three white soldiers — แท่งเขียวแรง 3 แท่งติด", "BUY")
            crows && !isBear(prior) -> TriggerEvent("THREE_SOLDIERS_CROWS", s.tf, "Three black crows — แท่งแดงแรง 3 แท่งติด", "SELL")
            else -> null
        }
    },

    trigger("IMPULSE_CANDLE", "แท่ง Impulse ขนาดใหญ่", PRICE_ACTION, EVENT, minBars = 20) { ctx ->
        val c = ctx.primary.last ?: return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        if (body(c) < 1.5 * atr) return@trigger null
        val dir = if (isBull(c)) "BUY" else "SELL"
        TriggerEvent("IMPULSE_CANDLE", ctx.primaryTf, "แท่ง${if (dir == "BUY") "เขียว" else "แดง"}ตัวใหญ่ ${"%.1f".format(body(c) / atr)}×ATR — มีแรงจริงเข้ามา", dir)
    },

    trigger("DOJI_AT_EXTREME", "Doji ที่จุดสุดโต่ง", PRICE_ACTION, EVENT, minBars = 25) { ctx ->
        val s = ctx.primary
        val c = s.last ?: return@trigger null
        val r = range(c)
        if (r <= 0 || body(c) > 0.1 * r) return@trigger null
        val hi = s.highest(20) ?: return@trigger null; val lo = s.lowest(20) ?: return@trigger null
        when {
            c.high >= hi -> TriggerEvent("DOJI_AT_EXTREME", s.tf, "Doji ที่ยอด 20 แท่ง — ความลังเลหลังขึ้นมาสุด", "SELL", c.high)
            c.low <= lo -> TriggerEvent("DOJI_AT_EXTREME", s.tf, "Doji ที่ก้น 20 แท่ง — ความลังเลหลังลงมาสุด", "BUY", c.low)
            else -> null
        }
    },

    trigger("GAP", "ราคาเปิดกระโดด", PRICE_ACTION, EVENT, minBars = 20) { ctx ->
        val s = ctx.primary
        val a = s.prev ?: return@trigger null; val b = s.last ?: return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val gap = b.open - a.close
        if (abs(gap) < 0.5 * atr) return@trigger null
        val dir = if (gap > 0) "BUY" else "SELL"
        TriggerEvent("GAP", s.tf, "ราคาเปิด gap ${if (gap > 0) "ขึ้น" else "ลง"} ${"%.1f".format(abs(gap) / atr)}×ATR จาก ${p(a.close)}", dir, a.close)
    }
)

// ═════════════════════════════════════════════════════════════════════════════
// I. รูปแบบกราฟ
// ═════════════════════════════════════════════════════════════════════════════

internal val PATTERN_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("DOUBLE_TOP_BOTTOM", "ยอดคู่ / ก้นคู่ หลุด neckline", PATTERN, EVENT) { ctx ->
        val s = ctx.primary
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val c1 = s.last?.close ?: return@trigger null; val c0 = s.prev?.close ?: return@trigger null
        if (s.swingHighPivots.size >= 2) {
            val (i1, h1) = s.swingHighPivots[s.swingHighPivots.size - 2]; val (i2, h2) = s.swingHighPivots.last()
            if (abs(h1 - h2) <= 0.3 * atr && i2 - i1 >= 5) {
                val neck = s.lows.subList(i1, i2 + 1).min()
                if (c0 >= neck && c1 < neck) return@trigger TriggerEvent("DOUBLE_TOP_BOTTOM", s.tf, "Double top ที่ ${p(h2)} — ปิดหลุด neckline ${p(neck)}", "SELL", neck)
            }
        }
        if (s.swingLowPivots.size >= 2) {
            val (i1, l1) = s.swingLowPivots[s.swingLowPivots.size - 2]; val (i2, l2) = s.swingLowPivots.last()
            if (abs(l1 - l2) <= 0.3 * atr && i2 - i1 >= 5) {
                val neck = s.highs.subList(i1, i2 + 1).max()
                if (c0 <= neck && c1 > neck) return@trigger TriggerEvent("DOUBLE_TOP_BOTTOM", s.tf, "Double bottom ที่ ${p(l2)} — ปิดทะลุ neckline ${p(neck)}", "BUY", neck)
            }
        }
        null
    },

    trigger("HEAD_SHOULDERS", "Head & Shoulders หลุด neckline", PATTERN, EVENT, minBars = 80) { ctx ->
        val s = ctx.primary
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val c1 = s.last?.close ?: return@trigger null; val c0 = s.prev?.close ?: return@trigger null
        if (s.swingHighPivots.size >= 3) {
            val hp = s.swingHighPivots
            val (i1, l) = hp[hp.size - 3]; val (i2, h) = hp[hp.size - 2]; val (i3, r) = hp.last()
            if (h - maxOf(l, r) > 0.5 * atr && abs(l - r) < 0.5 * atr) {
                val neck = (s.lows.subList(i1, i2 + 1).min() + s.lows.subList(i2, i3 + 1).min()) / 2
                if (c0 >= neck && c1 < neck) return@trigger TriggerEvent("HEAD_SHOULDERS", s.tf, "Head & Shoulders (หัว ${p(h)}) — ปิดหลุด neckline ${p(neck)}", "SELL", neck)
            }
        }
        if (s.swingLowPivots.size >= 3) {
            val lp = s.swingLowPivots
            val (i1, l) = lp[lp.size - 3]; val (i2, h) = lp[lp.size - 2]; val (i3, r) = lp.last()
            if (minOf(l, r) - h > 0.5 * atr && abs(l - r) < 0.5 * atr) {
                val neck = (s.highs.subList(i1, i2 + 1).max() + s.highs.subList(i2, i3 + 1).max()) / 2
                if (c0 <= neck && c1 > neck) return@trigger TriggerEvent("HEAD_SHOULDERS", s.tf, "Inverse Head & Shoulders (หัว ${p(h)}) — ปิดทะลุ neckline ${p(neck)}", "BUY", neck)
            }
        }
        null
    },

    trigger("TRIANGLE_WEDGE_BREAK", "หลุดสามเหลี่ยม / ลิ่ม", PATTERN, EVENT) { ctx ->
        val s = ctx.primary
        val hp = s.swingHighPivots; val lp = s.swingLowPivots
        if (hp.size < 2 || lp.size < 2) return@trigger null
        val (hi1, hv1) = hp[hp.size - 2]; val (hi2, hv2) = hp.last()
        val (li1, lv1) = lp[lp.size - 2]; val (li2, lv2) = lp.last()
        if (!(hv2 < hv1 && lv2 > lv1)) return@trigger null // เส้นบนลง เส้นล่างขึ้น = บีบตัว
        val x = s.n - 1
        val up = lineAt(hi1, hv1, hi2, hv2, x); val upPrev = lineAt(hi1, hv1, hi2, hv2, x - 1)
        val dn = lineAt(li1, lv1, li2, lv2, x); val dnPrev = lineAt(li1, lv1, li2, lv2, x - 1)
        if (up <= dn) return@trigger null
        val c1 = s.last?.close ?: return@trigger null; val c0 = s.prev?.close ?: return@trigger null
        when {
            WakeMath.crossUp(c0, c1, upPrev, up) -> TriggerEvent("TRIANGLE_WEDGE_BREAK", s.tf, "ปิดทะลุเส้นบนของสามเหลี่ยมบีบตัว (${p(up)})", "BUY", up)
            WakeMath.crossDown(c0, c1, dnPrev, dn) -> TriggerEvent("TRIANGLE_WEDGE_BREAK", s.tf, "ปิดหลุดเส้นล่างของสามเหลี่ยมบีบตัว (${p(dn)})", "SELL", dn)
            else -> null
        }
    },

    trigger("CHANNEL_TOUCH", "แตะขอบ Channel", PATTERN, EVENT) { ctx ->
        val s = ctx.primary
        val hp = s.swingHighPivots; val lp = s.swingLowPivots
        if (hp.size < 2 || lp.size < 2) return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val (hi1, hv1) = hp[hp.size - 2]; val (hi2, hv2) = hp.last()
        val (li1, lv1) = lp[lp.size - 2]; val (li2, lv2) = lp.last()
        if (hi2 == hi1 || li2 == li1) return@trigger null
        val sh = (hv2 - hv1) / (hi2 - hi1); val sl = (lv2 - lv1) / (li2 - li1)
        // ความชันใกล้กัน (ขนาน) และไม่แบน
        if (sh * sl <= 0 || abs(sh - sl) > 0.3 * maxOf(abs(sh), abs(sl))) return@trigger null
        val x = s.n - 1
        val up = lineAt(hi1, hv1, hi2, hv2, x); val dn = lineAt(li1, lv1, li2, lv2, x)
        val last = s.last ?: return@trigger null; val prev = s.prev ?: return@trigger null
        val upP = lineAt(hi1, hv1, hi2, hv2, x - 1); val dnP = lineAt(li1, lv1, li2, lv2, x - 1)
        val slopeWord = if (sh > 0) "ขาขึ้น" else "ขาลง"
        when {
            last.high >= up - 0.15 * atr && prev.high < upP - 0.15 * atr -> TriggerEvent("CHANNEL_TOUCH", s.tf, "ราคาแตะขอบบนของ channel $slopeWord (${p(up)})", "SELL", up)
            last.low <= dn + 0.15 * atr && prev.low > dnP + 0.15 * atr -> TriggerEvent("CHANNEL_TOUCH", s.tf, "ราคาแตะขอบล่างของ channel $slopeWord (${p(dn)})", "BUY", dn)
            else -> null
        }
    },

    trigger("HARMONIC_COMPLETION", "Harmonic pattern ครบรูป", PATTERN, EVENT) { ctx ->
        val pats = ctx.harmonics
        if (pats.isEmpty()) return@trigger null
        val s = ctx.primary
        val last = s.last ?: return@trigger null; val prev = s.prev ?: return@trigger null
        fun inPrz(c: Candle, h: com.skyliner2008.jarvis.tools.trading.HarmonicPattern) = c.low <= h.przTop && c.high >= h.przBottom
        val hit = pats.firstOrNull { inPrz(last, it) && !inPrz(prev, it) } ?: return@trigger null
        val dir = if (hit.direction.contains("BULL", true)) "BUY" else "SELL"
        TriggerEvent("HARMONIC_COMPLETION", s.tf, "ราคาเข้า PRZ ของ ${hit.type} ${hit.direction} (${p(hit.przBottom)}–${p(hit.przTop)})", dir, (hit.przTop + hit.przBottom) / 2)
    },

    trigger("ELLIOTT_STAGE_CHANGE", "Elliott wave เปลี่ยนช่วง", PATTERN, EVENT) { ctx ->
        val e = ctx.elliott ?: return@trigger null
        val before = ctx.memory["elliott_stage"] ?: return@trigger null
        if (before == e.stage) return@trigger null
        val dir = when { e.momentumBias.contains("BULL", true) -> "BUY"; e.momentumBias.contains("BEAR", true) -> "SELL"; else -> "NEUTRAL" }
        TriggerEvent("ELLIOTT_STAGE_CHANGE", ctx.primaryTf, "Elliott เปลี่ยนจาก $before เป็น ${e.stage} (${e.momentumBias})", dir)
    },

    trigger("TRENDLINE_BREAK", "หลุดเส้นแนวโน้ม", PATTERN, EVENT) { ctx ->
        val s = ctx.primary
        val c1 = s.last?.close ?: return@trigger null; val c0 = s.prev?.close ?: return@trigger null
        val x = s.n - 1
        if (s.swingHighPivots.size >= 2) {
            val (i1, v1) = s.swingHighPivots[s.swingHighPivots.size - 2]; val (i2, v2) = s.swingHighPivots.last()
            if (v2 < v1) {
                val now = lineAt(i1, v1, i2, v2, x); val before = lineAt(i1, v1, i2, v2, x - 1)
                if (WakeMath.crossUp(c0, c1, before, now)) return@trigger TriggerEvent("TRENDLINE_BREAK", s.tf, "ปิดทะลุเส้นแนวโน้มขาลง (${p(now)})", "BUY", now)
            }
        }
        if (s.swingLowPivots.size >= 2) {
            val (i1, v1) = s.swingLowPivots[s.swingLowPivots.size - 2]; val (i2, v2) = s.swingLowPivots.last()
            if (v2 > v1) {
                val now = lineAt(i1, v1, i2, v2, x); val before = lineAt(i1, v1, i2, v2, x - 1)
                if (WakeMath.crossDown(c0, c1, before, now)) return@trigger TriggerEvent("TRENDLINE_BREAK", s.tf, "ปิดหลุดเส้นแนวโน้มขาขึ้น (${p(now)})", "SELL", now)
            }
        }
        null
    }
)

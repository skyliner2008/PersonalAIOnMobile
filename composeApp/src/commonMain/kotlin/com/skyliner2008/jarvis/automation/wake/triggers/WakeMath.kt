package com.skyliner2008.jarvis.automation.wake.triggers

import com.skyliner2008.jarvis.automation.smc.UnifiedSmcSignals
import com.skyliner2008.jarvis.automation.wake.Series
import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * เครื่องมือคำนวณที่ trigger หลายตัวใช้ร่วมกัน
 * ทุกฟังก์ชันเป็น pure function — คืน null เมื่อข้อมูลไม่พอ (fail-closed)
 */
internal object WakeMath {
    const val MINUTE = 60_000L
    const val HOUR = 3_600_000L
    const val DAY = 86_400_000L
    const val WEEK = 7 * DAY

    fun crossUp(prevA: Double, a: Double, prevB: Double, b: Double): Boolean = prevA <= prevB && a > b
    fun crossDown(prevA: Double, a: Double, prevB: Double, b: Double): Boolean = prevA >= prevB && a < b

    fun hourUtc(ts: Long): Int = ((ts / HOUR) % 24).toInt()
    fun minuteUtc(ts: Long): Int = ((ts / MINUTE) % 60).toInt()

    /**
     * เลื่อนเวลาให้ "เริ่มวันเทรด" ตรงกับตลาดนั้น
     * FX/ทองเริ่มวันที่ 22:00 UTC (offset −2) → เลื่อน +2 ชม. แล้วตัดที่เที่ยงคืน
     */
    fun sessionShiftMs(symbol: String): Long = -TaIndicators.sessionOffsetHoursFor(symbol) * HOUR

    fun dayKey(ts: Long, shift: Long): Long = (ts + shift).floorDiv(DAY)

    /** 1970-01-05 เป็นวันจันทร์ — ลบ 4 วันให้สัปดาห์เริ่มวันจันทร์ ไม่ใช่วันพฤหัสแบบ epoch */
    fun weekKey(ts: Long, shift: Long): Long = (ts + shift - 4 * DAY).floorDiv(WEEK)

    /** เดือนแบบ UTC — ใช้ปี*12+เดือน โดยประมาณจากวัน (พอสำหรับหาขอบเดือน) */
    fun monthKey(ts: Long): Long {
        val days = (ts).floorDiv(DAY)
        // civil-from-days (Howard Hinnant) — แปลงวันเป็นปี/เดือนแบบแม่นยำ
        val z = days + 719468
        val era = (z).floorDiv(146097)
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val m = mp + if (mp < 10) 3 else -9
        return (y + if (m <= 2) 1 else 0) * 12 + m
    }

    data class Period(val key: Long, val open: Double, val high: Double, val low: Double, val close: Double, val firstTs: Long)

    /** รวมแท่งเป็นช่วงเวลา (วัน/สัปดาห์/เดือน) ตามลำดับเวลา */
    fun periods(bars: List<Candle>, keyOf: (Long) -> Long): List<Period> {
        val out = ArrayList<Period>()
        var key = Long.MIN_VALUE
        var o = 0.0; var h = 0.0; var l = 0.0; var c = 0.0; var t0 = 0L
        for (b in bars) {
            val k = keyOf(b.timestamp)
            if (k != key) {
                if (key != Long.MIN_VALUE) out += Period(key, o, h, l, c, t0)
                key = k; o = b.open; h = b.high; l = b.low; c = b.close; t0 = b.timestamp
            } else {
                if (b.high > h) h = b.high
                if (b.low < l) l = b.low
                c = b.close
            }
        }
        if (key != Long.MIN_VALUE) out += Period(key, o, h, l, c, t0)
        return out
    }

    fun touches(bar: Candle, level: Double): Boolean = bar.low <= level && bar.high >= level

    /** แตะครั้งแรก — แท่งล่าสุดแตะ แต่แท่งก่อนหน้าไม่แตะ (ทำให้เป็น event ไม่ใช่ state) */
    fun firstTouch(s: Series, level: Double): Boolean {
        val last = s.last ?: return false
        val prev = s.prev ?: return false
        return touches(last, level) && !touches(prev, level)
    }

    /**
     * ขนาดขั้นของ "เลขกลม" ตามระดับราคา
     * ทอง ~4000 → 50, BTC ~60000 → 1000, EURUSD ~1.08 → 0.01 (100 pips), USDJPY ~150 → 1
     */
    fun roundStep(price: Double): Double {
        if (price <= 0) return 0.0
        val target = price * 0.02
        val step = 10.0.pow(kotlin.math.floor(log10(target)))
        return if (target / step >= 5) step * 5 else step
    }

    fun linregSlope(values: List<Double>): Double? {
        val n = values.size
        if (n < 3) return null
        val xm = (n - 1) / 2.0
        val ym = values.average()
        var num = 0.0; var den = 0.0
        for (i in 0 until n) {
            num += (i - xm) * (values[i] - ym)
            den += (i - xm) * (i - xm)
        }
        return if (den == 0.0) null else num / den
    }

    fun returns(closes: List<Double>): List<Double> =
        closes.zipWithNext { a, b -> if (a != 0.0) (b - a) / a else 0.0 }

    fun correlation(a: List<Double>, b: List<Double>): Double? {
        val n = minOf(a.size, b.size)
        if (n < 5) return null
        val x = a.takeLast(n); val y = b.takeLast(n)
        val mx = x.average(); val my = y.average()
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in 0 until n) {
            val dx = x[i] - mx; val dy = y[i] - my
            sxy += dx * dy; sxx += dx * dx; syy += dy * dy
        }
        val d = sqrt(sxx * syy)
        return if (d == 0.0) null else sxy / d
    }

    /**
     * Divergence ที่ swing ซึ่ง **เพิ่งยืนยันที่แท่งล่าสุด** (จึงเป็น event ไม่ยิงซ้ำ)
     *
     * regular bull : ราคาทำ low ต่ำกว่า แต่อินดิเคเตอร์ทำ low สูงกว่า → BUY
     * hidden  bull : ราคาทำ low สูงกว่า แต่อินดิเคเตอร์ทำ low ต่ำกว่า → BUY (ไปต่อ)
     * (ฝั่ง high กลับด้าน → SELL)
     *
     * swing ถูกบันทึกที่แท่งยืนยัน (center + L) จึงต้องอ่านค่าอินดิเคเตอร์ที่ center = idx − L
     */
    fun divergence(s: Series, indicator: List<Double?>, hidden: Boolean): String? {
        val lag = UnifiedSmcSignals.SWING_L
        if (s.swingLowJustConfirmed && s.swingLows.size >= 2) {
            val (i2, p2) = s.swingLows[s.swingLows.size - 1]
            val (i1, p1) = s.swingLows[s.swingLows.size - 2]
            val r2 = indicator.getOrNull(i2 - lag); val r1 = indicator.getOrNull(i1 - lag)
            if (r1 != null && r2 != null) {
                val hit = if (hidden) p2 > p1 && r2 < r1 else p2 < p1 && r2 > r1
                if (hit) return "BUY"
            }
        }
        if (s.swingHighJustConfirmed && s.swingHighs.size >= 2) {
            val (i2, p2) = s.swingHighs[s.swingHighs.size - 1]
            val (i1, p1) = s.swingHighs[s.swingHighs.size - 2]
            val r2 = indicator.getOrNull(i2 - lag); val r1 = indicator.getOrNull(i1 - lag)
            if (r1 != null && r2 != null) {
                val hit = if (hidden) p2 < p1 && r2 > r1 else p2 > p1 && r2 < r1
                if (hit) return "SELL"
            }
        }
        return null
    }

    /** ราคาบนเส้นตรงที่ผ่าน (i1,p1) และ (i2,p2) ที่ index x */
    fun lineAt(i1: Int, p1: Double, i2: Int, p2: Double, x: Int): Double =
        if (i2 == i1) p2 else p1 + (p2 - p1) * (x - i1).toDouble() / (i2 - i1).toDouble()

    fun body(c: Candle): Double = abs(c.close - c.open)
    fun range(c: Candle): Double = c.high - c.low
    fun upperWick(c: Candle): Double = c.high - maxOf(c.open, c.close)
    fun lowerWick(c: Candle): Double = minOf(c.open, c.close) - c.low
    fun isBull(c: Candle): Boolean = c.close > c.open
    fun isBear(c: Candle): Boolean = c.close < c.open

    fun dirWord(dir: String): String = when (dir) { "BUY" -> "ขาขึ้น"; "SELL" -> "ขาลง"; else -> "" }
}

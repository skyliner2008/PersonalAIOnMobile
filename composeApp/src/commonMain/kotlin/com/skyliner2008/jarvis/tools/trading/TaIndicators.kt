package com.skyliner2008.jarvis.tools.trading

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * TaIndicators — Technical Analysis indicator library กลาง (pure Kotlin/KMP)
 *
 * ใช้ร่วมกันระหว่าง SmcApiService, AdvancedTradingEngine, IndicatorAlertProvider
 * รองรับทุก Timeframe: M1, M5, M15, M30, H1, H4, D1, W1 (และ aliases 1m, 5m, 15m, 1h, 4h, 1D, 1W)
 *
 * หมายเหตุ: ฟังก์ชันทั้งหมดเป็น pure function ไม่มี side effect คำนวณตามมาตรฐานสากล (TradingView / MT5)
 */
object TaIndicators {

    // ─── Timeframe Registry (single source of truth) ─────────────────────────

    /**
     * นิยาม timeframe หนึ่งตัว — ทุกการแปลง (canonical / MT5 / TV resolution / millis)
     * มาจากแถวเดียวกันเสมอ จึงไม่มีทางที่ map ชุดใดชุดหนึ่งจะ "ตกหล่น" TF ไปเงียบๆ อีก
     *
     * ก่อนหน้านี้มี map แยกกัน 4 ชุด (TaIndicators x3 + SmcApiService x2) ที่ไม่ตรงกัน:
     * 6h/8h/12h ตกไป "60" ใน tvResolution และ 8h ตกไป 1h ใน intervalToMillis
     * → ผู้ใช้ขอ 8h แล้วได้แท่ง 1h โดยไม่มี error (แก้ 2026-09-18)
     */
    data class TimeframeSpec(
        /** รูปแบบมาตรฐานภายในระบบ เช่น "15m", "4h", "1D" */
        val canonical: String,
        /** MT5 timeframe string เช่น "M15", "H4", "D1" */
        val mt5: String,
        /** TradingView resolution string เช่น "15", "240", "D" */
        val tvResolution: String,
        /** ความยาวหนึ่งแท่งเป็นมิลลิวินาที */
        val millis: Long,
        /** รูปแบบอื่นที่ยอมรับเป็น input (lowercase, ไม่มีช่องว่าง) */
        val aliases: Set<String>
    )

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60L * MINUTE_MS
    private const val DAY_MS = 24L * HOUR_MS

    /** ค่า default เมื่อ input ว่าง/ไม่รู้จัก — คงพฤติกรรมเดิมของระบบไว้ที่ 1h */
    private const val DEFAULT_TF = "1h"

    val TIMEFRAMES: List<TimeframeSpec> = listOf(
        TimeframeSpec("1m",  "M1",  "1",   1 * MINUTE_MS,  setOf("m1", "1m", "1")),
        TimeframeSpec("3m",  "M3",  "3",   3 * MINUTE_MS,  setOf("m3", "3m", "3")),
        TimeframeSpec("5m",  "M5",  "5",   5 * MINUTE_MS,  setOf("m5", "5m", "5")),
        TimeframeSpec("15m", "M15", "15",  15 * MINUTE_MS, setOf("m15", "15m", "15")),
        TimeframeSpec("30m", "M30", "30",  30 * MINUTE_MS, setOf("m30", "30m", "30")),
        TimeframeSpec("1h",  "H1",  "60",  1 * HOUR_MS,    setOf("h1", "1h", "60")),
        TimeframeSpec("2h",  "H2",  "120", 2 * HOUR_MS,    setOf("h2", "2h", "120")),
        TimeframeSpec("4h",  "H4",  "240", 4 * HOUR_MS,    setOf("h4", "4h", "240")),
        TimeframeSpec("6h",  "H6",  "360", 6 * HOUR_MS,    setOf("h6", "6h", "360")),
        TimeframeSpec("8h",  "H8",  "480", 8 * HOUR_MS,    setOf("h8", "8h", "480")),
        TimeframeSpec("12h", "H12", "720", 12 * HOUR_MS,   setOf("h12", "12h", "720")),
        TimeframeSpec("1D",  "D1",  "D",   1 * DAY_MS,     setOf("d1", "1d", "d")),
        TimeframeSpec("1W",  "W1",  "W",   7 * DAY_MS,     setOf("w1", "1w", "w")),
        // เดือนไม่มีความยาวคงที่ — ใช้ 30 วันเป็นค่าประมาณสำหรับ bucket math เท่านั้น
        TimeframeSpec("1M",  "MN1", "M",   30 * DAY_MS,    setOf("mn1", "1mn", "mn", "m"))
    )

    /**
     * แผนที่ alias → spec แบบ **มาก่อนชนะ** (first-wins)
     *
     * สำคัญ: canonical ของรายเดือนคือ "1M" ซึ่ง lowercase แล้วได้ "1m" ชนกับ 1 นาทีพอดี
     * TIMEFRAMES เรียงจากเล็กไปใหญ่ จึงต้องไม่ให้รายการหลังเขียนทับ ไม่งั้น "1m" จะกลายเป็นรายเดือน
     * (พฤติกรรมนี้ตรงกับ `when` ชุดเดิมที่สาขา "1m" อยู่ก่อนสาขา "1M")
     */
    private val tfByAlias: Map<String, TimeframeSpec> = buildMap {
        TIMEFRAMES.forEach { spec ->
            (listOf(spec.canonical.lowercase(), spec.mt5.lowercase()) + spec.aliases)
                .forEach { key -> if (!containsKey(key)) put(key, spec) }
        }
    }

    /** canonical แบบตรงตัวพิมพ์ — ใช้แยก "1M" (รายเดือน) ออกจาก "1m" (1 นาที) */
    private val tfByExactCanonical: Map<String, TimeframeSpec> = TIMEFRAMES.associateBy { it.canonical }

    /**
     * หา spec ของ timeframe — คืน null เมื่อไม่รู้จัก (ให้ผู้เรียกตัดสินใจเองว่าจะ fallback หรือ error)
     *
     * ลอง match canonical แบบตรงตัวพิมพ์ก่อน เพราะ "1M" (รายเดือน) กับ "1m" (1 นาที)
     * ต่างกันแค่ตัวพิมพ์ — ถ้า lowercase ทันทีจะชนกันเสมอ
     * เดิม normalizeTimeframe("mn") = "1M" แล้วป้อน "1M" กลับเข้าไปได้ "1m" (1 นาที) — round-trip พัง
     */
    fun timeframeSpecOrNull(raw: String?): TimeframeSpec? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim().replace(" ", "")
        return tfByExactCanonical[trimmed] ?: tfByAlias[trimmed.lowercase()]
    }

    /** หา spec ของ timeframe — fallback เป็น 1h เมื่อไม่รู้จัก (พฤติกรรมเดิมของระบบ) */
    fun timeframeSpec(raw: String?): TimeframeSpec =
        timeframeSpecOrNull(raw) ?: tfByAlias.getValue(DEFAULT_TF)

    /**
     * แปลง timeframe format ต่างๆ เช่น "m1", "1m", "H4", "4h", "D1", "1D", "W1", "1W"
     * ให้เป็นมาตรฐานสม่ำเสมอ ("1m", "5m", "15m", "30m", "1h", "4h", "1D", "1W")
     */
    fun normalizeTimeframe(raw: String?): String = timeframeSpec(raw).canonical

    /** แปลงเป็น MT5 timeframe string เช่น "M1", "M5", "M15", "H1", "H4", "D1", "W1" */
    fun toMt5Timeframe(raw: String?): String = timeframeSpec(raw).mt5

    /** แปลงเป็น TradingView Resolution string เช่น "1", "5", "15", "60", "240", "D", "W" */
    fun toTvResolution(raw: String?): String = timeframeSpec(raw).tvResolution

    /** ความยาวหนึ่งแท่งเป็นมิลลิวินาที */
    fun timeframeMillis(raw: String?): Long = timeframeSpec(raw).millis

    // ─── Warm-up Requirements ────────────────────────────────────────────────

    /**
     * จำนวนแท่งที่ต้องมี "ก่อน" จะเชื่อค่าอินดิเคเตอร์ได้
     *
     * ตัวที่ใช้ recursive smoothing (Wilder: RSI/ATR/ADX, และ EMA) ให้ค่าออกมาตั้งแต่แท่งที่ `period`
     * แต่ค่านั้นยังเอียงตาม seed อยู่มาก ต้องเดินต่ออีกหลายเท่าของ period จึงจะลู่เข้าค่าจริง
     * (คลาดเคลื่อน < 0.1% ที่ประมาณ 4 เท่า) ส่วน SMA/Donchian/Stoch ใช้หน้าต่างตายตัว จึงต้องการแค่ period พอดี
     *
     * ระบบต้อง **ไม่คำนวณเลย** เมื่อแท่งไม่ถึงเกณฑ์ ดีกว่าคำนวณแล้วส่งค่าที่ยังไม่ลู่เข้าให้ AI
     */
    object Warmup {
        /** ตัวคูณสำหรับอินดิเคเตอร์แบบ recursive smoothing (EMA / Wilder RMA) */
        const val SMOOTHING_FACTOR = 4

        /** period ยาวสุดที่ preset มาตรฐานใช้ (EMA200 / SMA200) */
        const val LONGEST_STANDARD_PERIOD = 200

        /** แท่งขั้นต่ำสำหรับชุด oscillator/volatility พื้นฐาน (RSI14, ATR14, ADX14, BB20, Stoch, CCI) */
        const val FAST_SET = 150

        /** แท่งขั้นต่ำเมื่อมี MACD(12,26,9) ร่วมด้วย */
        const val MACD_SET = 200

        /** แท่งขั้นต่ำสำหรับ Ichimoku (9/26/52 + displacement 26) */
        const val ICHIMOKU_SET = 250

        /** แท่งขั้นต่ำเมื่อชุดมี EMA200/SMA200 — เป็นค่า default ของระบบ */
        const val FULL_SET = SMOOTHING_FACTOR * LONGEST_STANDARD_PERIOD // 800

        /** แท่งขั้นต่ำสำหรับสถิติ 52 สัปดาห์บนกราฟ 1D */
        const val YEARLY_SET = 300

        /** แท่งสำหรับ backtest (ลึกพอให้ครอบคลุมหลาย market regime) */
        const val BACKTEST_SET = 5_000

        /**
         * แท่งที่ต้องมีสำหรับอินดิเคเตอร์แบบหน้าต่างตายตัว (SMA, Donchian, Bollinger, Stochastic)
         * — ต้องการพอดี period ไม่ต้อง warm-up
         */
        fun forWindowed(period: Int): Int = period.coerceAtLeast(1)

        /**
         * แท่งที่ต้องมีสำหรับอินดิเคเตอร์แบบ recursive smoothing (EMA, RSI, ATR, ADX, Supertrend)
         * — ต้องเดินต่อหลัง seed จึงจะลู่เข้า
         */
        fun forSmoothed(period: Int): Int = (period.coerceAtLeast(1) * SMOOTHING_FACTOR)

        /** แท่งที่ต้องมีเมื่อจะคำนวณหลายอินดิเคเตอร์พร้อมกัน — เอาตัวที่หนักสุดเป็นเกณฑ์ */
        fun forPeriods(windowed: List<Int> = emptyList(), smoothed: List<Int> = emptyList()): Int {
            val w = windowed.maxOfOrNull { forWindowed(it) } ?: 0
            val s = smoothed.maxOfOrNull { forSmoothed(it) } ?: 0
            return maxOf(w, s, 1)
        }
    }

    // ─── Moving Averages ─────────────────────────────────────────────────────

    /**
     * Simple Moving Average ของค่าสุดท้าย [period] ค่า
     *
     * คืน null เมื่อข้อมูลไม่ครบ period — **ห้ามคืนค่าประมาณ**
     * เดิมคืนค่าเฉลี่ยเท่าที่มี (เช่น SMA200 จาก 150 แท่ง) ซึ่งเป็นตัวเลขที่ไม่ใช่ SMA200
     * แล้วถูกส่งต่อให้ AI วิเคราะห์เหมือนเป็นค่าจริง
     */
    fun sma(data: List<Double>, period: Int): Double? {
        if (period <= 0 || data.size < period) return null
        return data.takeLast(period).average()
    }

    /** SMA ทั้ง series (index i = ค่า SMA ณ จุดนั้น, null เมื่อข้อมูลไม่พอ) */
    fun smaSeries(data: List<Double>, period: Int): List<Double?> {
        if (period <= 0) return List(data.size) { null }
        val out = ArrayList<Double?>(data.size)
        var sum = 0.0
        for (i in data.indices) {
            sum += data[i]
            if (i >= period) sum -= data[i - period]
            out.add(if (i >= period - 1) sum / period else null)
        }
        return out
    }

    /**
     * Exponential Moving Average (ค่าสุดท้าย) — seeder = SMA(period) แรกตามมาตรฐาน TA
     *
     * คืน null เมื่อข้อมูลไม่ครบ period — **ห้ามคืนราคาปิดแทน**
     * เดิม `return data.lastOrNull() ?: 0.0` ทำให้ EMA200 บน 15m (ที่ระบบดึงมาแค่ 150 แท่ง)
     * เท่ากับราคาปิดพอดีทุกครั้ง แล้ว AI นำไปสรุปว่า "ราคาอยู่ที่ EMA200"
     * ทั้งที่ไม่เคยคำนวณ EMA200 เลย
     */
    fun ema(data: List<Double>, period: Int): Double? {
        if (period <= 0 || data.size < period) return null
        val multiplier = 2.0 / (period + 1)
        var ema = data.take(period).average()
        for (i in period until data.size) {
            ema = (data[i] - ema) * multiplier + ema
        }
        return ema
    }

    /** EMA ทั้ง series — seeder = SMA(period) แรก, ก่อนหน้านั้นเป็น null */
    fun emaSeries(data: List<Double>, period: Int): List<Double?> {
        if (period <= 0 || data.isEmpty()) return emptyList()
        val out = ArrayList<Double?>(data.size)
        if (data.size < period) {
            return List(data.size) { null }
        }
        val multiplier = 2.0 / (period + 1)
        repeat(period - 1) { out.add(null) }
        var ema = data.take(period).average()
        out.add(ema)
        for (i in period until data.size) {
            ema = (data[i] - ema) * multiplier + ema
            out.add(ema)
        }
        return out
    }

    // ─── Momentum & Oscillators ──────────────────────────────────────────────

    /** RSI (Wilder's smoothing) ค่าสุดท้าย — คืน null เมื่อข้อมูลน้อยกว่า period+1 */
    fun rsi(closes: List<Double>, period: Int = 14): Double? {
        if (closes.size < period + 1) return null
        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) gainSum += diff else lossSum += -diff
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        for (i in period + 1 until closes.size) {
            val diff = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + maxOf(diff, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + maxOf(-diff, 0.0)) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    /**
     * RSI ทั้ง series (Wilder) — index i = RSI ณ แท่งนั้น, null ก่อนครบ period+1
     * ค่าตัวสุดท้ายต้องเท่ากับ [rsi] เป๊ะ (ล็อกด้วยเทสต์) — ใช้หา divergence ที่ต้องเทียบหลายจุด
     */
    fun rsiSeries(closes: List<Double>, period: Int = 14): List<Double?> {
        val n = closes.size
        val out = arrayOfNulls<Double>(n)
        if (period <= 0 || n < period + 1) return out.toList()
        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d >= 0) gainSum += d else lossSum += -d
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        fun value(): Double = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        out[period] = value()
        for (i in period + 1 until n) {
            val d = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + maxOf(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + maxOf(-d, 0.0)) / period
            out[i] = value()
        }
        return out.toList()
    }

    /**
     * MACD histogram ทั้ง series — null ก่อนที่ signal line จะมีค่า
     * ค่าตัวสุดท้ายต้องเท่ากับ `macd(...).hist`
     */
    fun macdHistSeries(
        closes: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): List<Double?> {
        val n = closes.size
        val out = arrayOfNulls<Double>(n)
        val fast = emaSeries(closes, fastPeriod)
        val slow = emaSeries(closes, slowPeriod)
        if (fast.size < n || slow.size < n) return out.toList()
        val macdIdx = ArrayList<Int>()
        val macdVal = ArrayList<Double>()
        for (i in 0 until n) {
            val f = fast[i]; val s = slow[i]
            if (f != null && s != null) { macdIdx += i; macdVal += f - s }
        }
        val signal = emaSeries(macdVal, signalPeriod)
        for (k in macdVal.indices) {
            val sg = signal.getOrNull(k) ?: continue
            out[macdIdx[k]] = macdVal[k] - sg
        }
        return out.toList()
    }

    /** MACD (Moving Average Convergence Divergence) */
    data class MacdResult(val macd: Double, val signal: Double, val hist: Double)

    fun macd(
        closes: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MacdResult? {
        if (closes.size < slowPeriod + signalPeriod) return null
        val fastEma = emaSeries(closes, fastPeriod)
        val slowEma = emaSeries(closes, slowPeriod)

        val macdValues = ArrayList<Double>()
        for (i in slowPeriod - 1 until closes.size) {
            val f = fastEma[i]
            val s = slowEma[i]
            if (f != null && s != null) {
                macdValues.add(f - s)
            }
        }
        if (macdValues.size < signalPeriod) return null
        val macdLine = macdValues.last()
        val signalLine = ema(macdValues, signalPeriod) ?: return null
        return MacdResult(macd = macdLine, signal = signalLine, hist = macdLine - signalLine)
    }

    /** Stochastic Oscillator (14, 3, 3) — Slow %K และ Slow %D ตามมาตรฐาน TV / MT5 */
    data class StochResult(val k: Double, val d: Double)

    fun stochastic(
        closes: List<Double>,
        highs: List<Double>,
        lows: List<Double>,
        kPeriod: Int = 14,
        dPeriod: Int = 3,
        smoothK: Int = 3
    ): StochResult? {
        val size = minOf(closes.size, highs.size, lows.size)
        if (size < kPeriod + smoothK + dPeriod - 2) return null

        // 1. Raw Fast %K
        val rawK = ArrayList<Double>()
        for (i in kPeriod - 1 until size) {
            var hh = Double.NEGATIVE_INFINITY
            var ll = Double.POSITIVE_INFINITY
            for (j in i - kPeriod + 1..i) {
                if (highs[j] > hh) hh = highs[j]
                if (lows[j] < ll) ll = lows[j]
            }
            val range = hh - ll
            val kVal = if (range > 0.0) (closes[i] - ll) / range * 100.0 else 50.0
            rawK.add(kVal)
        }

        // 2. Slow %K = SMA(smoothK) of Raw %K
        val slowK = ArrayList<Double>()
        for (i in smoothK - 1 until rawK.size) {
            val sum = (i - smoothK + 1..i).sumOf { rawK[it] }
            slowK.add(sum / smoothK)
        }
        if (slowK.size < dPeriod) return null

        // 3. Slow %D = SMA(dPeriod) of Slow %K
        val kLast = slowK.last()
        val dLast = (slowK.size - dPeriod until slowK.size).sumOf { slowK[it] } / dPeriod
        return StochResult(k = kLast, d = dLast)
    }

    /** Commodity Channel Index (CCI) */
    fun cci(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 20): Double? {
        val size = minOf(highs.size, lows.size, closes.size)
        if (size < period) return null
        val tp = List(size) { i -> (highs[i] + lows[i] + closes[i]) / 3.0 }
        val window = tp.takeLast(period)
        val mean = window.average()
        val meanDev = window.sumOf { abs(it - mean) } / period
        if (meanDev == 0.0) return 0.0
        return (tp.last() - mean) / (0.015 * meanDev)
    }

    // ─── Volatility & Trend ──────────────────────────────────────────────────

    /** Average True Range ค่าสุดท้าย (Wilder's smoothing RMA) */
    fun atr(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): Double? {
        val size = minOf(highs.size, lows.size, closes.size)
        if (size < period + 1) return null
        val trs = ArrayList<Double>(size - 1)
        for (i in 1 until size) {
            val tr = maxOf(
                highs[i] - lows[i],
                maxOf(abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1]))
            )
            trs.add(tr)
        }
        var atr = trs.take(period).average()
        for (i in period until trs.size) {
            atr = (atr * (period - 1) + trs[i]) / period
        }
        return atr
    }

    /** Bollinger Bands (basis = SMA, upper/lower = basis ± mult * stdev) */
    data class BollingerBands(val basis: Double, val upper: Double, val lower: Double, val width: Double, val percentB: Double)

    fun bollingerBands(data: List<Double>, period: Int = 20, mult: Double = 2.0): BollingerBands? {
        if (data.size < period) return null
        val window = data.takeLast(period)
        val basis = window.average()
        val variance = window.fold(0.0) { acc, v -> acc + (v - basis) * (v - basis) } / period
        val stdev = sqrt(variance)
        val upper = basis + mult * stdev
        val lower = basis - mult * stdev
        val width = if (basis != 0.0) (upper - lower) / basis * 100.0 else 0.0
        val lastClose = data.last()
        val percentB = if (upper != lower) (lastClose - lower) / (upper - lower) * 100.0 else 50.0
        return BollingerBands(basis, upper, lower, width, percentB)
    }

    /**
     * Average Directional Index (ADX) — Wilder's smoothing
     *
     * ลำดับตามนิยามของ Wilder:
     *  1. TR / +DM / -DM รายแท่ง (เริ่มที่ index 1)
     *  2. smoothed sum แรกที่แท่ง `period` = ผลรวมของ index 1..period
     *  3. **DX ตัวแรกเกิดที่แท่ง `period`** แล้วไล่ต่อด้วย Wilder smoothing
     *  4. ADX = RMA ของ DX โดย seed = ค่าเฉลี่ย DX `period` ตัวแรก
     *
     * เดิม loop เริ่มที่ `period + 1` ทำให้ **DX ที่แท่ง `period` หายไป 1 ตัว**
     * ค่า ADX ที่ได้จึงเลื่อนไป 1 แท่งเทียบกับ TradingView/MT5
     */
    data class AdxResult(val adx: Double, val diPlus: Double, val diMinus: Double)

    fun adx(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): AdxResult? {
        val size = minOf(highs.size, lows.size, closes.size)
        if (period <= 0 || size < Warmup.forSmoothed(period)) return null
        val tr = DoubleArray(size)
        val pdm = DoubleArray(size)
        val ndm = DoubleArray(size)

        for (i in 1 until size) {
            val h = highs[i]
            val l = lows[i]
            val ph = highs[i - 1]
            val pl = lows[i - 1]
            val pc = closes[i - 1]
            tr[i] = maxOf(h - l, abs(h - pc), abs(l - pc))
            val up = h - ph
            val dn = pl - l
            pdm[i] = if (up > dn && up > 0) up else 0.0
            ndm[i] = if (dn > up && dn > 0) dn else 0.0
        }

        var sTr = (1..period).sumOf { tr[it] }
        var sPdm = (1..period).sumOf { pdm[it] }
        var sNdm = (1..period).sumOf { ndm[it] }

        val dxList = ArrayList<Double>(size - period)
        var lastPdi = 0.0
        var lastNdi = 0.0

        fun recordDx() {
            if (sTr == 0.0) return
            lastPdi = 100.0 * sPdm / sTr
            lastNdi = 100.0 * sNdm / sTr
            val denom = lastPdi + lastNdi
            dxList.add(if (denom == 0.0) 0.0 else 100.0 * abs(lastPdi - lastNdi) / denom)
        }

        // DX ตัวแรกจาก smoothed sum ชุดแรก (แท่งที่ index = period)
        recordDx()

        for (i in period + 1 until size) {
            sTr = sTr - (sTr / period) + tr[i]
            sPdm = sPdm - (sPdm / period) + pdm[i]
            sNdm = sNdm - (sNdm / period) + ndm[i]
            recordDx()
        }
        if (dxList.size < period) return null
        var adxVal = dxList.take(period).average()
        for (k in period until dxList.size) {
            adxVal = (adxVal * (period - 1) + dxList[k]) / period
        }
        return AdxResult(adx = adxVal, diPlus = lastPdi, diMinus = lastNdi)
    }

    /** Supertrend (ATR Band-based trend filter) */
    data class SupertrendResult(val value: Double, val isBullish: Boolean)

    /**
     * Supertrend
     *
     * ใช้ ATR series ที่คำนวณครั้งเดียว (O(n)) — เดิมเรียก atr() ใหม่ทุกรอบ loop
     * บน sublist ที่ยาวขึ้นเรื่อยๆ กลายเป็น O(n²) พร้อม allocation ทุกแท่ง
     *
     * นอกจากนี้ band เริ่มต้นที่ 0.0 ซึ่งบังเอิญใช้ได้กับราคาบวกเท่านั้น
     * ตอนนี้ seed จากแท่งแรกที่คำนวณ ATR ได้จริง จึงถูกต้องกับทุกช่วงราคา
     */
    fun supertrend(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int = 10,
        multiplier: Double = 3.0
    ): SupertrendResult? {
        val size = minOf(highs.size, lows.size, closes.size)
        if (size < Warmup.forSmoothed(period)) return null

        val atrSeries = atrSeries(highs, lows, closes, period)

        var upperBand = Double.NaN
        var lowerBand = Double.NaN
        var isBullish = true

        for (i in period until size) {
            val currAtr = atrSeries.getOrNull(i) ?: continue

            val hl2 = (highs[i] + lows[i]) / 2.0
            val basicUpper = hl2 + (multiplier * currAtr)
            val basicLower = hl2 - (multiplier * currAtr)

            upperBand = if (upperBand.isNaN() || basicUpper < upperBand || closes[i - 1] > upperBand) basicUpper else upperBand
            lowerBand = if (lowerBand.isNaN() || basicLower > lowerBand || closes[i - 1] < lowerBand) basicLower else lowerBand

            isBullish = when {
                closes[i] > upperBand -> true
                closes[i] < lowerBand -> false
                else -> isBullish
            }
        }
        if (upperBand.isNaN() || lowerBand.isNaN()) return null

        return SupertrendResult(value = if (isBullish) lowerBand else upperBand, isBullish = isBullish)
    }

    /**
     * ATR ทั้ง series (Wilder RMA) — index i = ATR ณ แท่งนั้น, null เมื่อยังไม่ถึง period
     * ใช้ร่วมกันโดย supertrend/ATR-based tools แทนการคำนวณซ้ำทีละแท่ง
     */
    fun atrSeries(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int = 14
    ): List<Double?> {
        val size = minOf(highs.size, lows.size, closes.size)
        if (period <= 0 || size < period + 1) return List(size) { null }

        val out = arrayOfNulls<Double>(size)
        val trs = DoubleArray(size)
        for (i in 1 until size) {
            trs[i] = maxOf(
                highs[i] - lows[i],
                maxOf(abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1]))
            )
        }
        var rma = (1..period).sumOf { trs[it] } / period
        out[period] = rma
        for (i in period + 1 until size) {
            rma = (rma * (period - 1) + trs[i]) / period
            out[i] = rma
        }
        return out.toList()
    }

    /** Standard deviation (population) ของค่าสุดท้าย [period] ค่า */
    fun stdev(data: List<Double>, period: Int): Double? {
        if (data.size < period || period <= 0) return null
        val window = data.takeLast(period)
        val mean = window.average()
        val variance = window.fold(0.0) { acc, v -> acc + (v - mean) * (v - mean) } / period
        return sqrt(variance)
    }

    // ─── Volume ──────────────────────────────────────────────────────────────

    /**
     * On-Balance Volume — ค่าสะสมตั้งแต่แท่งแรกของชุดข้อมูล
     *
     * ระดับสัมบูรณ์ของ OBV ไม่มีความหมายในตัวเอง (ขึ้นกับว่าเริ่มนับที่ไหน)
     * สิ่งที่ใช้ได้คือ "ทิศทาง" — ดู [obvSlope] ประกอบเสมอ
     */
    fun obvSeries(closes: List<Double>, volumes: List<Double>): List<Double> {
        val size = minOf(closes.size, volumes.size)
        if (size == 0) return emptyList()
        val out = ArrayList<Double>(size)
        var acc = 0.0
        out.add(acc)
        for (i in 1 until size) {
            acc += when {
                closes[i] > closes[i - 1] -> volumes[i]
                closes[i] < closes[i - 1] -> -volumes[i]
                else -> 0.0
            }
            out.add(acc)
        }
        return out
    }

    fun obv(closes: List<Double>, volumes: List<Double>): Double? =
        obvSeries(closes, volumes).lastOrNull()

    /**
     * ความชันของ OBV ใน [period] แท่งล่าสุด แสดงเป็น % ของช่วง OBV ทั้งชุด
     * — ทำให้เทียบข้าม symbol/TF ได้ ต่างจากค่า OBV ดิบ
     */
    fun obvSlope(closes: List<Double>, volumes: List<Double>, period: Int = 20): Double? {
        val series = obvSeries(closes, volumes)
        if (series.size < period + 1) return null
        val window = series.takeLast(period + 1)
        val delta = window.last() - window.first()
        val range = series.max() - series.min()
        if (range == 0.0) return 0.0
        return delta / range * 100.0
    }

    /** Money Flow Index — "RSI ถ่วงด้วยปริมาณ" */
    fun mfi(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        volumes: List<Double>,
        period: Int = 14
    ): Double? {
        val size = minOf(highs.size, lows.size, closes.size, volumes.size)
        if (size < period + 1) return null
        val tp = List(size) { i -> (highs[i] + lows[i] + closes[i]) / 3.0 }

        var positive = 0.0
        var negative = 0.0
        for (i in (size - period) until size) {
            val flow = tp[i] * volumes[i]
            when {
                tp[i] > tp[i - 1] -> positive += flow
                tp[i] < tp[i - 1] -> negative += flow
            }
        }
        if (negative == 0.0) return if (positive == 0.0) 50.0 else 100.0
        val ratio = positive / negative
        return 100.0 - (100.0 / (1.0 + ratio))
    }

    /**
     * VWAP แบบผูก anchor
     *
     * VWAP ตามนิยามต้องรีเซ็ตทุกเซสชัน — ค่าที่สะสมข้ามหลายวันไม่ใช่ VWAP
     * (เดิมระบบรวมทั้ง 500 แท่ง ซึ่งบน 15m กินเวลา ~5 วัน จึงไม่มีความหมายในเชิงเทรด)
     *
     * @param anchorTs เริ่มนับจากแท่งที่ timestamp >= ค่านี้
     * @return null เมื่อไม่มีแท่งในช่วง หรือปริมาณรวมเป็นศูนย์ (เช่น feed ที่ไม่มี volume)
     */
    fun vwapAnchored(candles: List<Candle>, anchorTs: Long): Double? {
        var pv = 0.0
        var vol = 0.0
        for (c in candles) {
            if (c.timestamp < anchorTs) continue
            val typical = (c.high + c.low + c.close) / 3.0
            pv += typical * c.volume
            vol += c.volume
        }
        if (vol <= 0.0) return null
        return pv / vol
    }

    /**
     * จุดเริ่มเซสชันล่าสุดในชุดข้อมูล สำหรับใช้เป็น anchor ของ VWAP รายวัน
     *
     * @param sessionOffsetHours ชั่วโมงที่เซสชันเริ่ม เทียบกับ 00:00 UTC
     *        (0 = วันแบบ UTC; ตลาด FX/ทองมักเริ่ม 21:00-22:00 UTC → ใช้ -3 หรือ -2)
     */
    fun lastSessionAnchor(candles: List<Candle>, sessionOffsetHours: Int = 0): Long? {
        val lastTs = candles.lastOrNull()?.timestamp ?: return null
        val dayMs = 24L * 60 * 60 * 1000
        val offsetMs = sessionOffsetHours * 60L * 60 * 1000
        val shifted = lastTs - offsetMs
        return (shifted / dayMs) * dayMs + offsetMs
    }

    /** VWAP ของเซสชันล่าสุดในชุดข้อมูล */
    fun vwapSession(candles: List<Candle>, sessionOffsetHours: Int = 0): Double? {
        val anchor = lastSessionAnchor(candles, sessionOffsetHours) ?: return null
        return vwapAnchored(candles, anchor)
    }

    /**
     * ชั่วโมงที่เซสชันเทรดเริ่ม (เทียบ UTC) ตามประเภทสินทรัพย์
     *
     * - **FX / โลหะมีค่า**: เซสชันเริ่มเมื่อซิดนีย์เปิด ≈ 22:00 UTC (คือ -2 ชม.จากเที่ยงคืนวันถัดไป)
     *   ตรงกับที่โบรกเกอร์ปิดแท่ง D1 และเป็นจุดที่ VWAP รายวันควรรีเซ็ต
     * - **คริปโต**: เทรด 24/7 ไม่มีเซสชัน ใช้ขอบวัน UTC ตามที่ exchange ใช้
     * - **หุ้น**: ควรใช้เวลาเปิดตลาดของแต่ละ exchange — ยังไม่รองรับ ใช้ UTC ไปก่อน
     */
    fun sessionOffsetHoursFor(symbol: String?): Int {
        val s = symbol?.uppercase()?.replace("/", "")?.replace("-", "") ?: return 0
        val fiat = setOf("USD", "EUR", "GBP", "JPY", "CHF", "AUD", "CAD", "NZD")
        val isMetal = s.contains("XAU") || s.contains("XAG") || s.contains("GOLD") || s.contains("SILVER")
        val isFx = s.length == 6 && s.take(3) in fiat && s.drop(3) in fiat
        return if (isMetal || isFx) -2 else 0
    }

    /** VWAP รายเซสชันโดยเลือก anchor ให้อัตโนมัติตามประเภทของ symbol */
    fun vwapSessionFor(candles: List<Candle>, symbol: String?): Double? =
        vwapSession(candles, sessionOffsetHoursFor(symbol))

    // ─── Channels & Levels ───────────────────────────────────────────────────

    /** Donchian Channel — upper/lower คือ high/low สูงสุด-ต่ำสุดใน period, mid คือกึ่งกลาง */
    data class DonchianResult(val upper: Double, val mid: Double, val lower: Double)

    fun donchian(highs: List<Double>, lows: List<Double>, period: Int = 20): DonchianResult? {
        val size = minOf(highs.size, lows.size)
        if (period <= 0 || size < period) return null
        val upper = highs.takeLast(period).max()
        val lower = lows.takeLast(period).min()
        return DonchianResult(upper = upper, mid = (upper + lower) / 2.0, lower = lower)
    }

    /** Rate of Change (%) เทียบกับราคาเมื่อ [period] แท่งก่อน */
    fun roc(closes: List<Double>, period: Int = 9): Double? {
        if (period <= 0 || closes.size < period + 1) return null
        val past = closes[closes.size - 1 - period]
        if (past == 0.0) return null
        return (closes.last() - past) / past * 100.0
    }

    /** Williams %R — สเกล -100 (oversold) ถึง 0 (overbought) */
    fun williamsR(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): Double? {
        val size = minOf(highs.size, lows.size, closes.size)
        if (period <= 0 || size < period) return null
        val hh = highs.takeLast(period).max()
        val ll = lows.takeLast(period).min()
        if (hh == ll) return -50.0
        return (hh - closes.last()) / (hh - ll) * -100.0
    }

    /** Ichimoku Kinko Hyo — คำนวณจากแท่งปิดล่าสุด (ไม่ shift ไปข้างหน้า) */
    data class IchimokuResult(
        val tenkan: Double,
        val kijun: Double,
        val senkouA: Double,
        val senkouB: Double,
        val cloudTop: Double,
        val cloudBottom: Double,
        val chikou: Double
    )

    fun ichimoku(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        tenkanPeriod: Int = 9,
        kijunPeriod: Int = 26,
        senkouBPeriod: Int = 52
    ): IchimokuResult? {
        val size = minOf(highs.size, lows.size, closes.size)
        // ต้องมี senkouB + displacement(kijun) เพื่อให้ cloud มีความหมาย
        if (size < senkouBPeriod + kijunPeriod) return null

        fun midOf(period: Int): Double {
            val hh = highs.takeLast(period).max()
            val ll = lows.takeLast(period).min()
            return (hh + ll) / 2.0
        }

        val tenkan = midOf(tenkanPeriod)
        val kijun = midOf(kijunPeriod)
        val senkouA = (tenkan + kijun) / 2.0
        val senkouB = midOf(senkouBPeriod)
        return IchimokuResult(
            tenkan = tenkan,
            kijun = kijun,
            senkouA = senkouA,
            senkouB = senkouB,
            cloudTop = maxOf(senkouA, senkouB),
            cloudBottom = minOf(senkouA, senkouB),
            chikou = closes[size - 1 - kijunPeriod]
        )
    }

    /** Classic floor-trader pivot points จากแท่งก่อนหน้า */
    data class PivotLevels(
        val pivot: Double,
        val r1: Double, val r2: Double, val r3: Double,
        val s1: Double, val s2: Double, val s3: Double
    )

    fun pivotPoints(prevHigh: Double, prevLow: Double, prevClose: Double): PivotLevels {
        val p = (prevHigh + prevLow + prevClose) / 3.0
        val range = prevHigh - prevLow
        return PivotLevels(
            pivot = p,
            r1 = 2 * p - prevLow,
            r2 = p + range,
            r3 = prevHigh + 2 * (p - prevLow),
            s1 = 2 * p - prevHigh,
            s2 = p - range,
            s3 = prevLow - 2 * (prevHigh - p)
        )
    }

    /**
     * ระดับ Fibonacci retracement ระหว่าง swing high/low
     * คืนเป็น map ratio → ราคา เรียงจากระดับต่ำไปสูงของ retracement
     */
    fun fibonacciLevels(swingHigh: Double, swingLow: Double): Map<String, Double>? {
        if (swingHigh <= swingLow) return null
        val range = swingHigh - swingLow
        return listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.705, 0.786, 1.0)
            .associate { ratio ->
                // วัดจาก swing high ลงมา (retracement ของขาขึ้น)
                "fib_${(ratio * 1000).toInt()}" to (swingHigh - range * ratio)
            }
    }
}

package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P2.16: ตรวจว่า implementation ที่ optimize แล้ว **ตรงกับนิยามของอินดิเคเตอร์**
 *
 * วิธี: เขียน reference implementation แบบตรงไปตรงมาตามสูตร (ช้าแต่ชัด ไม่มี optimization)
 * แล้วเทียบกับตัวจริงในทุก timeframe และทุกรูปแบบราคา
 *
 * นี่ไม่ใช่การเทียบกับตัวเลขจาก TradingView โดยตรง (ต้องมี fixture จริงซึ่งยังไม่มี)
 * แต่จับบัคประเภทที่เกิดจริงในโปรเจกต์นี้ได้: off-by-one ของ ADX, seed ผิดของ Wilder,
 * band ที่ไม่ lock ของ Supertrend, และ optimization ที่ทำให้ผลเปลี่ยน
 */
class IndicatorDefinitionParityTest {

    private val tolerance = 1e-9

    // ─── ชุดข้อมูลทดสอบหลายรูปแบบ ────────────────────────────────────────────

    private fun series(count: Int, seed: Int): List<Candle> {
        var price = 2000.0 + seed * 137.0
        return (0 until count).map { i ->
            // ผสมเทรนด์ + คลื่น + ความผันผวนที่เปลี่ยนไป เพื่อให้ครอบคลุมทั้งช่วงเทรนด์และไซด์เวย์
            val drift = when (seed % 3) { 0 -> 0.35; 1 -> -0.28; else -> 0.0 }
            price += drift + sin((i + seed) / (5.0 + seed % 7)) * (3.0 + seed % 4)
            val range = 2.0 + abs(sin((i + seed) / 11.0)) * 6.0
            Candle(
                open = price - range / 3,
                high = price + range,
                low = price - range,
                close = price,
                volume = 500.0 + ((i * 37 + seed * 13) % 900),
                timestamp = 1_700_000_000_000L + i * 3_600_000L,
                isClosed = true
            )
        }
    }

    private fun allSeries(count: Int = 600) = (0 until 6).map { series(count, it) }

    // ─── Reference implementations (ตรงตามสูตร ไม่ optimize) ────────────────

    /** EMA ตามนิยาม: seed = SMA(period) แล้วไล่ทีละแท่ง */
    private fun refEma(data: List<Double>, period: Int): Double? {
        if (data.size < period) return null
        val k = 2.0 / (period + 1)
        var e = data.take(period).sum() / period
        for (i in period until data.size) e = (data[i] - e) * k + e
        return e
    }

    /** RSI (Wilder) ตามนิยาม */
    private fun refRsi(closes: List<Double>, period: Int): Double? {
        if (closes.size < period + 1) return null
        var g = 0.0
        var l = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d > 0) g += d else l += -d
        }
        var ag = g / period
        var al = l / period
        for (i in period + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            ag = (ag * (period - 1) + maxOf(d, 0.0)) / period
            al = (al * (period - 1) + maxOf(-d, 0.0)) / period
        }
        if (al == 0.0) return 100.0
        return 100.0 - 100.0 / (1.0 + ag / al)
    }

    /** ATR (Wilder RMA) ตามนิยาม */
    private fun refAtr(c: List<Candle>, period: Int): Double? {
        if (c.size < period + 1) return null
        val tr = (1 until c.size).map { i ->
            maxOf(c[i].high - c[i].low, abs(c[i].high - c[i - 1].close), abs(c[i].low - c[i - 1].close))
        }
        var a = tr.take(period).sum() / period
        for (i in period until tr.size) a = (a * (period - 1) + tr[i]) / period
        return a
    }

    /**
     * ADX (Wilder) ตามนิยาม — เขียนแบบตรงตัวเพื่อจับ off-by-one
     * DX ตัวแรกต้องเกิดที่แท่ง index = period
     */
    private fun refAdx(c: List<Candle>, period: Int): Triple<Double, Double, Double>? {
        val n = c.size
        if (n < period * 4) return null
        val tr = DoubleArray(n); val pdm = DoubleArray(n); val ndm = DoubleArray(n)
        for (i in 1 until n) {
            tr[i] = maxOf(c[i].high - c[i].low, abs(c[i].high - c[i - 1].close), abs(c[i].low - c[i - 1].close))
            val up = c[i].high - c[i - 1].high
            val dn = c[i - 1].low - c[i].low
            pdm[i] = if (up > dn && up > 0) up else 0.0
            ndm[i] = if (dn > up && dn > 0) dn else 0.0
        }
        var sTr = 0.0; var sP = 0.0; var sN = 0.0
        for (i in 1..period) { sTr += tr[i]; sP += pdm[i]; sN += ndm[i] }

        val dx = ArrayList<Double>()
        var pdi = 0.0; var ndi = 0.0
        for (i in period until n) {
            if (i > period) {
                sTr = sTr - sTr / period + tr[i]
                sP = sP - sP / period + pdm[i]
                sN = sN - sN / period + ndm[i]
            }
            if (sTr == 0.0) continue
            pdi = 100.0 * sP / sTr
            ndi = 100.0 * sN / sTr
            val den = pdi + ndi
            dx.add(if (den == 0.0) 0.0 else 100.0 * abs(pdi - ndi) / den)
        }
        if (dx.size < period) return null
        var adx = dx.take(period).sum() / period
        for (k in period until dx.size) adx = (adx * (period - 1) + dx[k]) / period
        return Triple(adx, pdi, ndi)
    }

    /** Supertrend ตามนิยาม — คำนวณ ATR ใหม่ทุกแท่งแบบ naive */
    private fun refSupertrend(c: List<Candle>, period: Int, mult: Double): Pair<Double, Boolean>? {
        if (c.size < period + 1) return null
        var upper = Double.NaN
        var lower = Double.NaN
        var bull = true
        for (i in period until c.size) {
            val a = refAtr(c.subList(0, i + 1), period) ?: continue
            val hl2 = (c[i].high + c[i].low) / 2.0
            val bu = hl2 + mult * a
            val bl = hl2 - mult * a
            upper = if (upper.isNaN() || bu < upper || c[i - 1].close > upper) bu else upper
            lower = if (lower.isNaN() || bl > lower || c[i - 1].close < lower) bl else lower
            bull = when {
                c[i].close > upper -> true
                c[i].close < lower -> false
                else -> bull
            }
        }
        if (upper.isNaN() || lower.isNaN()) return null
        return (if (bull) lower else upper) to bull
    }

    // ─── Parity tests ────────────────────────────────────────────────────────

    @Test
    fun emaMatchesDefinitionAcrossPeriodsAndSeries() {
        allSeries().forEachIndexed { s, candles ->
            val closes = candles.map { it.close }
            listOf(7, 9, 12, 14, 20, 21, 26, 50, 100, 200).forEach { p ->
                val actual = TaIndicators.ema(closes, p)
                val expected = refEma(closes, p)
                assertNotNull(actual, "EMA$p series$s เป็น null")
                assertEquals(expected!!, actual, tolerance, "EMA$p series$s")
            }
        }
    }

    @Test
    fun rsiMatchesWilderDefinition() {
        allSeries().forEachIndexed { s, candles ->
            val closes = candles.map { it.close }
            listOf(7, 14, 21).forEach { p ->
                val actual = TaIndicators.rsi(closes, p)
                assertNotNull(actual, "RSI$p series$s เป็น null")
                assertEquals(refRsi(closes, p)!!, actual, tolerance, "RSI$p series$s")
                assertTrue(actual in 0.0..100.0)
            }
        }
    }

    @Test
    fun atrAndAtrSeriesAgreeWithWilderDefinition() {
        allSeries().forEachIndexed { s, candles ->
            val h = candles.map { it.high }; val l = candles.map { it.low }; val c = candles.map { it.close }
            listOf(7, 14, 21).forEach { p ->
                val actual = TaIndicators.atr(h, l, c, p)
                assertNotNull(actual, "ATR$p series$s เป็น null")
                assertEquals(refAtr(candles, p)!!, actual, tolerance, "ATR$p series$s")

                // atrSeries ที่เพิ่มเข้ามาเพื่อแก้ O(n²) ต้องให้ค่าสุดท้ายตรงกับ atr()
                val seriesLast = TaIndicators.atrSeries(h, l, c, p).last()
                assertNotNull(seriesLast, "atrSeries$p series$s ค่าสุดท้ายเป็น null")
                assertEquals(actual, seriesLast, tolerance, "atrSeries vs atr (p=$p, series$s)")
            }
        }
    }

    /** จับ off-by-one ที่ DX ตัวแรกหายไป — ค่า ADX จะเลื่อนไป 1 แท่ง */
    @Test
    fun adxMatchesWilderDefinitionIncludingFirstDx() {
        allSeries().forEachIndexed { s, candles ->
            val h = candles.map { it.high }; val l = candles.map { it.low }; val c = candles.map { it.close }
            val actual = TaIndicators.adx(h, l, c, 14)
            val expected = refAdx(candles, 14)
            assertNotNull(actual, "ADX series$s เป็น null")
            assertNotNull(expected)
            assertEquals(expected.first, actual.adx, 1e-9, "ADX series$s")
            assertEquals(expected.second, actual.diPlus, 1e-9, "+DI series$s")
            assertEquals(expected.third, actual.diMinus, 1e-9, "-DI series$s")
        }
    }

    /** Supertrend ที่ optimize เป็น O(n) ต้องให้ผลเท่ากับ naive O(n²) เป๊ะ */
    @Test
    fun supertrendOptimizationPreservesResult() {
        allSeries(400).forEachIndexed { s, candles ->
            val h = candles.map { it.high }; val l = candles.map { it.low }; val c = candles.map { it.close }
            val actual = TaIndicators.supertrend(h, l, c, 10, 3.0)
            val expected = refSupertrend(candles, 10, 3.0)
            assertNotNull(actual, "Supertrend series$s เป็น null")
            assertNotNull(expected)
            assertEquals(expected.first, actual.value, 1e-9, "Supertrend value series$s")
            assertEquals(expected.second, actual.isBullish, "Supertrend direction series$s")
        }
    }

    @Test
    fun macdEqualsFastEmaMinusSlowEmaAndHistIsConsistent() {
        allSeries().forEachIndexed { s, candles ->
            val closes = candles.map { it.close }
            val m = TaIndicators.macd(closes, 12, 26, 9)
            assertNotNull(m, "MACD series$s เป็น null")
            val fast = refEma(closes, 12)!!
            val slow = refEma(closes, 26)!!
            assertEquals(fast - slow, m.macd, 1e-9, "MACD line series$s")
            assertEquals(m.macd - m.signal, m.hist, 1e-12, "MACD hist series$s")
        }
    }

    @Test
    fun bollingerUsesPopulationStdevLikeTradingView() {
        allSeries().forEachIndexed { s, candles ->
            val closes = candles.map { it.close }
            val bb = TaIndicators.bollingerBands(closes, 20, 2.0)
            assertNotNull(bb, "BB series$s เป็น null")
            val w = closes.takeLast(20)
            val mean = w.sum() / 20
            val sd = kotlin.math.sqrt(w.sumOf { (it - mean) * (it - mean) } / 20) // population
            assertEquals(mean, bb.basis, tolerance, "BB basis series$s")
            assertEquals(mean + 2 * sd, bb.upper, tolerance, "BB upper series$s")
            assertEquals(mean - 2 * sd, bb.lower, tolerance, "BB lower series$s")
        }
    }

    // ─── ความสอดคล้องข้าม timeframe ─────────────────────────────────────────

    /**
     * อินดิเคเตอร์เป็น pure function ของลำดับราคา — ต้องไม่ขึ้นกับ timeframe
     * ถ้าให้ OHLCV ชุดเดียวกันแต่ต่าง timestamp spacing ผลต้องเท่ากันเป๊ะ
     * (จับกรณีที่เผลอเอา timestamp ไปใช้ในการคำนวณ)
     */
    @Test
    fun indicatorsAreIndependentOfTimeframeSpacing() {
        val base = series(600, 1)
        TaIndicators.TIMEFRAMES.forEach { spec ->
            val retimed = base.mapIndexed { i, c -> c.copy(timestamp = 1_700_000_000_000L + i * spec.millis) }
            val h = retimed.map { it.high }; val l = retimed.map { it.low }; val c = retimed.map { it.close }
            val hb = base.map { it.high }; val lb = base.map { it.low }; val cb = base.map { it.close }

            assertEquals(TaIndicators.ema(cb, 200)!!, TaIndicators.ema(c, 200)!!, tolerance, "EMA200 @${spec.canonical}")
            assertEquals(TaIndicators.rsi(cb, 14)!!, TaIndicators.rsi(c, 14)!!, tolerance, "RSI14 @${spec.canonical}")
            assertEquals(
                TaIndicators.adx(hb, lb, cb, 14)!!.adx,
                TaIndicators.adx(h, l, c, 14)!!.adx,
                tolerance, "ADX14 @${spec.canonical}"
            )
            assertEquals(
                TaIndicators.supertrend(hb, lb, cb)!!.value,
                TaIndicators.supertrend(h, l, c)!!.value,
                tolerance, "Supertrend @${spec.canonical}"
            )
        }
    }

    // ─── VWAP: ต้องรีเซ็ตรายเซสชันจริง ──────────────────────────────────────

    /**
     * VWAP รายเซสชันต้องไม่รวมแท่งของวันก่อนหน้า
     * (เดิมระบบสะสมทั้งหน้าต่าง 500 แท่ง ซึ่งบน 15m กินเวลา ~5 วัน — ไม่ใช่ VWAP)
     */
    @Test
    fun sessionVwapExcludesPreviousSessions() {
        val tfMs = TaIndicators.timeframeMillis("1h")
        val dayMs = 86_400_000L
        val dayStart = 1_757_030_400_000L / dayMs * dayMs

        // วันก่อนหน้าราคา 1000, วันปัจจุบันราคา 2000 — VWAP ต้องเป็น 2000 ไม่ใช่ค่ากลาง
        val prev = (0 until 24).map {
            Candle(1000.0, 1000.0, 1000.0, 1000.0, 100.0, dayStart - dayMs + it * tfMs)
        }
        val today = (0 until 5).map {
            Candle(2000.0, 2000.0, 2000.0, 2000.0, 100.0, dayStart + it * tfMs)
        }

        val vwap = TaIndicators.vwapSession(prev + today, sessionOffsetHours = 0)
        assertNotNull(vwap)
        assertEquals(2000.0, vwap, 1e-6, "VWAP ต้องรีเซ็ตที่ขอบเซสชัน ไม่รวมวันก่อนหน้า")

        // ถ้าสะสมทั้งชุดจะได้ประมาณ 1172 — ยืนยันว่าไม่ใช่ค่านั้น
        assertTrue(abs(vwap - 1172.0) > 100.0)
    }

    /** FX/ทองต้องใช้ anchor 22:00 UTC ส่วนคริปโตใช้ขอบวัน UTC */
    @Test
    fun sessionAnchorDiffersByInstrumentType() {
        assertEquals(-2, TaIndicators.sessionOffsetHoursFor("XAUUSD"))
        assertEquals(-2, TaIndicators.sessionOffsetHoursFor("EURUSD"))
        assertEquals(-2, TaIndicators.sessionOffsetHoursFor("XAGUSD"))
        assertEquals(0, TaIndicators.sessionOffsetHoursFor("BTCUSDT"))
        assertEquals(0, TaIndicators.sessionOffsetHoursFor(null))
    }

    // ─── ค่าที่ตรวจสอบด้วยมือได้ ────────────────────────────────────────────

    /** ราคาขึ้นทุกแท่ง → RSI = 100 (ไม่มี loss เลย) */
    @Test
    fun knownAnswerMonotonicSeries() {
        val up = (0 until 100).map { 100.0 + it }
        assertEquals(100.0, TaIndicators.rsi(up, 14)!!, 1e-9)

        // ราคาคงที่ → SMA = EMA = ราคานั้น, stdev = 0, BB ทุกเส้นเท่ากัน
        val flat = List(100) { 50.0 }
        assertEquals(50.0, TaIndicators.sma(flat, 20)!!, 1e-12)
        assertEquals(50.0, TaIndicators.ema(flat, 20)!!, 1e-12)
        assertEquals(0.0, TaIndicators.stdev(flat, 20)!!, 1e-12)
        val bb = TaIndicators.bollingerBands(flat, 20, 2.0)!!
        assertEquals(bb.upper, bb.lower, 1e-12)

        // OBV: ขึ้นทุกแท่ง → OBV = ผลรวม volume ตั้งแต่แท่งที่ 2
        val vols = List(100) { 10.0 }
        assertEquals(990.0, TaIndicators.obv(up, vols)!!, 1e-9)

        // Donchian ของ series ที่ขึ้นตลอด (ค่า 100..199)
        val d = TaIndicators.donchian(up, up, 20)!!
        assertEquals(199.0, d.upper, 1e-9)
        assertEquals(180.0, d.lower, 1e-9)
        assertEquals(189.5, d.mid, 1e-9)

        // ROC 9 แท่ง: (199-190)/190*100
        assertEquals(9.0 / 190.0 * 100.0, TaIndicators.roc(up, 9)!!, 1e-9)

        // Williams %R ที่ราคาปิดเท่ากับ high สุด → 0
        assertEquals(0.0, TaIndicators.williamsR(up, up, up, 14)!!, 1e-9)
    }
}

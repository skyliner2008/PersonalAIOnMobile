package com.example.personalaibot.automation

import com.example.personalaibot.logDebug
import com.example.personalaibot.tools.trading.Candle
import com.example.personalaibot.tools.trading.SmcApiService
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * SignalMarkerProvider — คำนวณ "จุดสัญญาณย้อนหลัง" (chart markers) ของทุกกลยุทธ์ที่ส่งสัญญาณได้
 * ใช้วาดลงบนกราฟ (Chart Dashboard / mini-chart ในแชท) ผ่าน overlay "signals"
 *
 * ครอบคลุม:
 *  - Strategy Library (5 ตัว): tsmom flip, trend state change, reversal trigger,
 *    donchian breakout, w52high proximity cross
 *  - SMC Flow System: UT Bot flip, EMA14/60 cross, 3-Bar Reversal
 *  (SMC Confluence ไม่ทำ marker — ต้องเรียก SMC analysis ต่อแท่ง หนักเกินไป)
 *
 * Marker = แท่งที่เกิดเหตุ (edge-triggered: สัญญาณเปลี่ยนเท่านั้น ไม่ซ้ำทุกแท่ง)
 */
class SignalMarkerProvider(private val smcApi: SmcApiService) {

    data class SignalMarker(
        val time: Long,
        val side: String,   // BUY | SELL
        val label: String,  // เช่น "DC▲", "UT▼", "E14/60▲"
        val color: String   // hex
    )

    companion object {
        private const val MAX_MARKERS_PER_KIND = 12
        private const val BUY_COLOR = "#26A69A"
        private const val SELL_COLOR = "#EF5350"

        /**
         * Strategy consolidation (2026-08-27): cross-TF forensics (XAUUSD, MT5, 60/20/20 split)
         * พบว่าเฉพาะ MOM และ REV ที่ expectancy บวกครบทุก phase — TR/DC/52H/E/UT/3BR ถูกตัด
         * ออกจาก pipeline สัญญาณ (โค้ดยังเก็บไว้ re-enable ได้) ส่วน engine หลักคือ UNIFIED_SMC
         * (automation/smc/UnifiedSmcSignals.kt) ที่รวมทุก TF เป็น setup เดียว
         */
        val ENABLED_KINDS = setOf("MOM", "REV")
    }

    /** คำนวณ markers ทั้งหมดของ symbol@tf (เรียงตามเวลา) — คืน emptyList ถ้าข้อมูลไม่พอ */
    suspend fun fetch(rawSymbol: String): List<SignalMarker> {
        val (symbol, tf) = IndicatorAlertProvider.splitSymbolAndTf(rawSymbol)
        val candles = runCatching { smcApi.fetchCandlesWithSource(symbol, tf, 300).candles }
            .getOrElse { return emptyList() }
        // entry params ที่จูนแล้ว — marker บนกราฟต้องตรงกับจุดเข้าที่ live alert ใช้จริง
        val tunedEntry = runCatching {
            com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager()
                .getTunedEntryParams(symbol, tf)
        }.getOrElse { emptyMap() }
        val sorted = compute(candles, entryParams = tunedEntry)
        logDebug("SignalMarkers", "$symbol/$tf markers=${sorted.size}")
        return sorted
    }

    /**
     * คำนวณ markers จากแท่งเทียนที่มีอยู่แล้ว (ใช้ร่วมกับ SignalAlertProvider — ไม่ต้องดึงซ้ำ)
     * entryParams: params จุดเข้าที่จูนแล้วต่อ kind (จาก EntryTuning / grid search) — ว่าง = ใช้ default เดิมทุก kind
     * kindsOverride: null = ใช้ ENABLED_KINDS (เฉพาะตัวที่มี edge); ส่งชุด kind เองเพื่อ forensics/backtest ย้อนหลัง
     */
    fun compute(
        candles: List<Candle>,
        maxPerKind: Int = MAX_MARKERS_PER_KIND,
        entryParams: Map<String, com.example.personalaibot.automation.backtest.EntryParams> = emptyMap(),
        kindsOverride: Set<String>? = null
    ): List<SignalMarker> {
        if (candles.size < 60) return emptyList()

        val closes = candles.map { it.close }
        val n = candles.size
        val out = mutableListOf<SignalMarker>()

        // params จุดเข้าต่อ kind (default = ค่าคงที่เดิมทุกตัว)
        val epMOM = entryParams["MOM"] ?: com.example.personalaibot.automation.backtest.EntryParams()
        val epTR = entryParams["TR"] ?: com.example.personalaibot.automation.backtest.EntryParams()
        val epREV = entryParams["REV"] ?: com.example.personalaibot.automation.backtest.EntryParams()
        val epDC = entryParams["DC"] ?: com.example.personalaibot.automation.backtest.EntryParams()
        val ep52H = entryParams["52H"] ?: com.example.personalaibot.automation.backtest.EntryParams()
        val epE = entryParams["E"] ?: com.example.personalaibot.automation.backtest.EntryParams()
        val epUT = entryParams["UT"] ?: com.example.personalaibot.automation.backtest.EntryParams()

        // ══ Strategy Library ══

        // 1) Time-Series Momentum (ROC flip ข้าม 0)
        run {
            val lb = epMOM.momLookback
            var prevSign = 0
            val marks = mutableListOf<SignalMarker>()
            for (i in lb until n) {
                val roc = closes[i] - closes[i - lb]
                val sign = if (roc > 0) 1 else if (roc < 0) -1 else 0
                if (sign != 0 && prevSign != 0 && sign != prevSign) {
                    marks += SignalMarker(candles[i].timestamp, if (sign > 0) "BUY" else "SELL", "MOM${if (sign > 0) "▲" else "▼"}", if (sign > 0) BUY_COLOR else SELL_COLOR)
                }
                if (sign != 0) prevSign = sign
            }
            out += marks.takeLast(maxPerKind)
        }

        // 2) Trend Following (EMA fast/slow cross)
        run {
            val eFast = ema(closes, epTR.trFast)
            val eSlow = ema(closes, epTR.trSlow)
            val marks = mutableListOf<SignalMarker>()
            for (i in epTR.trSlow + 1 until n) {
                val a = eFast[i]; val b = eSlow[i]; val pa = eFast[i - 1]; val pb = eSlow[i - 1]
                if (a.isNaN() || b.isNaN() || pa.isNaN() || pb.isNaN()) continue
                if (pa <= pb && a > b) marks += SignalMarker(candles[i].timestamp, "BUY", "TR▲", BUY_COLOR)
                else if (pa >= pb && a < b) marks += SignalMarker(candles[i].timestamp, "SELL", "TR▼", SELL_COLOR)
            }
            out += marks.takeLast(maxPerKind)
        }

        // 3) Short-Term Reversal (RSI สุดโต่ง + แตะ BB) — EDGE-TRIGGERED:
        //    นับเฉพาะแท่งแรกที่ "เข้าเงื่อนไข" (แท่งก่อนไม่เข้า) ไม่ใช่ทุกแท่งที่ค้างอยู่ในเงื่อนไข
        run {
            val period = epREV.revRsiPeriod
            val rsiSeries = rsiSeries(closes, period)
            val marks = mutableListOf<SignalMarker>()
            fun bbBand(idx: Int): Pair<Double, Double> {
                val window = closes.subList(idx - epREV.revBbPeriod + 1, idx + 1)
                val mean = window.average()
                val sd = sqrt(window.sumOf { (it - mean).pow(2) } / window.size)
                return (mean + epREV.revBbMult * sd) to (mean - epREV.revBbMult * sd)
            }
            for (i in epREV.revBbPeriod + 6 until n) {
                val (upper, lower) = bbBand(i)
                val (pUpper, pLower) = bbBand(i - 1)
                val bullNow = rsiSeries[i] < epREV.revRsiLow && closes[i] <= lower
                val bullPrev = rsiSeries[i - 1] < epREV.revRsiLow && closes[i - 1] <= pLower
                val bearNow = rsiSeries[i] > epREV.revRsiHigh && closes[i] >= upper
                val bearPrev = rsiSeries[i - 1] > epREV.revRsiHigh && closes[i - 1] >= pUpper
                if (bullNow && !bullPrev) marks += SignalMarker(candles[i].timestamp, "BUY", "REV▲", "#66BB6A")
                else if (bearNow && !bearPrev) marks += SignalMarker(candles[i].timestamp, "SELL", "REV▼", "#E57373")
            }
            out += marks.takeLast(maxPerKind)
        }

        // 4) Donchian Breakout (20 แท่ง ไม่รวมแท่งปัจจุบัน) — EDGE แบบ state-change:
        //    mark แท่งแรกที่เข้าสถานะ breakout (ก่อนหน้าไม่ได้ breakout ฝั่งนั้น)
        //    เดิมใช้ lastSide → breakout ฝั่งเดิมซ้ำ (ราคากลับเข้าช่องแล้วทะลุใหม่) ไม่ถูก mark เลย
        run {
            val p = epDC.dcPeriod
            val marks = mutableListOf<SignalMarker>()
            val sides = IntArray(n)
            for (i in p + 1 until n) {
                var hh = Double.NEGATIVE_INFINITY; var ll = Double.POSITIVE_INFINITY
                for (j in i - p until i) { hh = max(hh, candles[j].high); ll = min(ll, candles[j].low) }
                sides[i] = if (closes[i] > hh) 1 else if (closes[i] < ll) -1 else 0
            }
            for (i in p + 2 until n) {
                val side = sides[i]
                if (side != 0 && sides[i - 1] != side) {
                    marks += SignalMarker(candles[i].timestamp, if (side > 0) "BUY" else "SELL", "DC${if (side > 0) "▲" else "▼"}", if (side > 0) BUY_COLOR else SELL_COLOR)
                }
            }
            out += marks.takeLast(maxPerKind)
        }

        // 5) 52-Weeks High proximity — ROLLING window (w52Lookback แท่ง ไม่รวมแท่งปัจจุบัน)
        //    เดิม runHigh สะสมตั้งแต่แท่งแรกของชุดข้อมูล = "สูงสุดของไฟล์" ไม่ใช่ 52w high จริง
        //    (15m 5,000 แท่ง ≈ 52 วัน) และไม่ tune ได้ — แก้เป็น sliding max (deque) O(n)
        //    EDGE แบบ state-change เหมือนเดิม + warmup 100 แท่งแรก
        run {
            val lb = ep52H.w52Lookback.coerceAtLeast(50)
            val marks = mutableListOf<SignalMarker>()
            val sides = IntArray(n)
            val dq = ArrayDeque<Int>()  // sliding max ของ high บนหน้าต่าง [i-lb, i-1]
            for (i in 0 until n) {
                while (dq.isNotEmpty() && dq.first() < i - lb) dq.removeFirst()
                val winHigh = if (dq.isEmpty()) Double.NaN else candles[dq.first()].high
                val prox = if (winHigh.isNaN() || winHigh <= 0.0) 1.0 else closes[i] / winHigh
                sides[i] = if (i < 100 || dq.isEmpty()) 0
                    else if (prox >= ep52H.w52ProxBuy) 1
                    else if (prox <= ep52H.w52ProxSell) -1
                    else 0
                while (dq.isNotEmpty() && candles[dq.last()].high <= candles[i].high) dq.removeLast()
                dq.addLast(i)
            }
            for (i in 1 until n) {
                val side = sides[i]
                if (side != 0 && sides[i - 1] != side) {
                    marks += SignalMarker(candles[i].timestamp, if (side > 0) "BUY" else "SELL", "52H${if (side > 0) "▲" else "▼"}", if (side > 0) "#9CCC65" else "#FF8A65")
                }
            }
            out += marks.takeLast(maxPerKind)
        }

        // ══ SMC Flow System ══

        // 6) EMA fast/slow cross (default 14/60)
        run {
            val ef = ema(closes, epE.eFast)
            val es = ema(closes, epE.eSlow)
            val marks = mutableListOf<SignalMarker>()
            for (i in epE.eSlow + 1 until n) {
                val a = ef[i]; val b = es[i]; val pa = ef[i - 1]; val pb = es[i - 1]
                if (a.isNaN() || b.isNaN() || pa.isNaN() || pb.isNaN()) continue
                if (pa <= pb && a > b) marks += SignalMarker(candles[i].timestamp, "BUY", "E▲", "#FFD54F")
                else if (pa >= pb && a < b) marks += SignalMarker(candles[i].timestamp, "SELL", "E▼", "#FFB74D")
            }
            out += marks.takeLast(maxPerKind)
        }

        // 7) UT Bot (key × ATR) flip
        run {
            val atrS = atrSeries(candles, epUT.utAtrPeriod)
            val stop = DoubleArray(n)
            for (i in 0 until n) {
                val src = closes[i]
                val srcPrev = closes[max(0, i - 1)]
                val nLoss = epUT.utKey * atrS[i]
                stop[i] = when {
                    src > stop[max(0, i - 1)] && srcPrev > stop[max(0, i - 1)] -> max(stop[max(0, i - 1)], src - nLoss)
                    src < stop[max(0, i - 1)] && srcPrev < stop[max(0, i - 1)] -> min(stop[max(0, i - 1)], src + nLoss)
                    src > stop[max(0, i - 1)] -> src - nLoss
                    else -> src + nLoss
                }
            }
            val marks = mutableListOf<SignalMarker>()
            for (i in 1 until n) {
                if (closes[i] > stop[i] && closes[i - 1] <= stop[i - 1]) marks += SignalMarker(candles[i].timestamp, "BUY", "UT▲", "#4DB6AC")
                else if (closes[i] < stop[i] && closes[i - 1] >= stop[i - 1]) marks += SignalMarker(candles[i].timestamp, "SELL", "UT▼", "#F06292")
            }
            out += marks.takeLast(maxPerKind)
        }

        // 8) 3-Bar Reversal (raw pattern)
        run {
            val marks = mutableListOf<SignalMarker>()
            for (i in 2 until n) {
                val c2 = candles[i - 2]; val c1 = candles[i - 1]; val c0 = candles[i]
                val bull = (c2.close < c2.open) &&
                    (c1.low < c2.low) && (c1.high < c2.high) && (c1.close < c1.open) &&
                    (c0.close > c0.open) && (c0.high > c2.high)
                val bear = (c2.close > c2.open) &&
                    (c1.high > c2.high) && (c1.low > c2.low) && (c1.close > c1.open) &&
                    (c0.close < c0.open) && (c0.low < c2.low)
                if (bull) marks += SignalMarker(c0.timestamp, "BUY", "3BR▲", "#81C784")
                else if (bear) marks += SignalMarker(c0.timestamp, "SELL", "3BR▼", "#BA68C8")
            }
            out += marks.takeLast(maxPerKind)
        }

        // Closed-bar confirmation: strategy markers remain visible only when the
        // signal candle itself shows enough directional intent. This is deliberately
        // applied after edge-trigger detection so we do not alter the strategy state machine.
        val timeToIndex = candles.withIndex().associate { it.value.timestamp to it.index }
        val confirmed = out.filter { marker ->
            val index = timeToIndex[marker.time] ?: return@filter false
            StrategyConfirmationGate.evaluate(candles, index, marker.side, marker.label).accepted
        }
        // ตัด strategy ที่ forensics พิสูจน์ว่าไม่มี edge (ดู ENABLED_KINDS) — กรองหลังสุด
        // เพื่อไม่แตะ state machine ของแต่ละกลยุทธ์ (re-enable ได้โดยแก้ ENABLED_KINDS ที่เดียว)
        // kindsOverride != null → วิเคราะห์ย้อนหลังแบบระบุ kind เอง (forensics/backtest tools)
        val allowedKinds = kindsOverride ?: ENABLED_KINDS
        return confirmed.filter { signalKindOf(it.label) in allowedKinds }.sortedBy { it.time }
    }

    // ─── Math helpers ──────────────────────────────────────────────────────

    private fun ema(values: List<Double>, period: Int): List<Double> {
        if (values.size < period) return List(values.size) { Double.NaN }
        val k = 2.0 / (period + 1)
        val out = ArrayList<Double>(values.size)
        var prev = values.take(period).average()
        repeat(period - 1) { out.add(Double.NaN) }
        out.add(prev)
        for (i in period until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out.add(prev)
        }
        return out
    }

    private fun rsiSeries(closes: List<Double>, period: Int): DoubleArray {
        val out = DoubleArray(closes.size) { 50.0 }
        if (closes.size <= period) return out
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d > 0) gain += d else loss -= d
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        out[period] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        for (i in period + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + max(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-d, 0.0)) / period
            out[i] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        }
        return out
    }

    private fun atrSeries(candles: List<Candle>, period: Int): DoubleArray {
        val out = DoubleArray(candles.size)
        if (candles.size <= period) return out
        val trs = DoubleArray(candles.size)
        for (i in 1 until candles.size) {
            val h = candles[i].high; val l = candles[i].low; val pc = candles[i - 1].close
            trs[i] = maxOf(h - l, abs(h - pc), abs(l - pc))
        }
        var atr = 0.0
        for (i in 1..period) atr += trs[i]
        atr /= period
        out[period] = atr
        for (i in period + 1 until candles.size) {
            atr = (atr * (period - 1) + trs[i]) / period
            out[i] = atr
        }
        for (i in 0 until period) out[i] = out[period]
        return out
    }
}

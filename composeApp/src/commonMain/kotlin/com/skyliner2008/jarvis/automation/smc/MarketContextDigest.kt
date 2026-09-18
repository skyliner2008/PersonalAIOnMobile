package com.skyliner2008.jarvis.automation.smc

import com.skyliner2008.jarvis.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * MarketContextDigest — "โครงสร้างตลาด 5TF" แบบ deterministic (ไม่ใช้ AI)
 * คำนวณจากแท่งเทียนล้วนๆ เพื่อลดภาระ AI: AI ไม่ต้องเดาโครงสร้างเอง แค่อ่าน digest แล้วตัดสิน
 *
 * TF stack (ตามสเปก 2026-08-28):
 *   H4, H1 → Market/Trend/Structure Context
 *   M15    → Structure Setup
 *   M5, M1 → Entry + Confirmation/Reversal
 *
 * ใช้ 2 จุด:
 *  1) แนบใน payload สัญญาณ (signal_mtf_context) ให้ AI Supervisor ตัดสิน APPROVE/VETO/ADJUST
 *  2) ตรวจ "ราคาแตะจุดสำคัญ" (keyzone) — trigger แยกให้ผู้ใช้ตั้ง alert ได้
 *
 * reuse primitives ของ UnifiedSmcSignals (internal) — causal เหมือนกัน (swing ยืนยันแล้วเท่านั้น)
 */
object MarketContextDigest {

    /**
     * บรรทัดสรุปหนึ่ง timeframe — "สิ่งที่ AI เห็นเมื่อมองกราฟ TF นั้น"
     *
     * ขยายจากเดิม (trend/event/rsi/ema50/atr) เพราะงบโทเคนเหลือเฟือ:
     * prompt เดิมใช้ ~765 tokens จากที่ส่งได้ ~16,600 ต่อ request (TPM 250K ÷ RPM 15)
     * → ใช้ไปแค่ 4.6% ทั้งที่ AI มองไม่เห็นกราฟและต้องพึ่งข้อมูลนี้ล้วนๆ
     */
    data class TfLine(
        val tf: String, val trend: Int, val lastEvent: String,
        val close: Double, val rsi: Double, val ema50: Double, val atr: Double,
        val swingHigh: Double?, val swingLow: Double?,
        // ── เพิ่มใหม่ ──
        /** ความแรงของเทรนด์ — แยกเทรนด์จริงออกจากไซด์เวย์ */
        val adx: Double? = null,
        /** ทิศทางโมเมนตัม */
        val macdHist: Double? = null,
        /** ตำแหน่งราคาใน Bollinger (0-100) — บอกว่าอยู่ขอบบน/ล่าง/กลาง */
        val bbPercentB: Double? = null,
        /** ความกว้าง BB เทียบ ATR — ต่ำ = บีบตัวรอ breakout */
        val bbWidthAtr: Double? = null,
        /** ระยะราคาจาก EMA50 เป็นหน่วย ATR — บอก overextension */
        val distEma50Atr: Double? = null,
        /** volume แท่งล่าสุดเทียบค่าเฉลี่ย 20 แท่ง */
        val volumeRatio: Double? = null
    )

    data class Level(val price: Double, val kind: String)

    data class Digest(
        val symbol: String,
        val price: Double,
        val tfLines: List<TfLine>,
        val levelsAbove: List<Level>,
        val levelsBelow: List<Level>,
        val premiumDiscount: String,     // PREMIUM | DISCOUNT | EQUILIBRIUM (จาก H1)
        val poc: Double?, val vah: Double?, val val_: Double?,
        val keyZoneHit: String?,          // คำอธิบายเมื่อราคาอยู่ที่จุดสำคัญ (null = ไม่อยู่)
        val text: String                  // พร้อมใส่ AI prompt
    )

    /** สร้าง digest — ต้องการ m15 ≥ 120 แท่ง (TF อื่นว่างได้ จะข้ามบรรทัดนั้น) */
    /**
     * @param closedAware true = หาแท่งปิดล่าสุดจาก [Candle.isClosed] (ข้อมูลจาก OHLCV store ที่ติดธงแล้ว)
     *   false = สมมติว่าแท่งสุดท้ายยังก่อตัว (พฤติกรรมเดิม — ข้อมูลที่ resample เองไม่มีธง)
     *   เดิมใช้ n-2 ตายตัว: ตอนตลาดปิด (สุดสัปดาห์) แท่งสุดท้ายปิดแล้ว ภาพตลาดจึงช้าไป 1 แท่ง
     * @param tradeRefs ใส่บรรทัด "อ้างอิงโครงสร้าง BUY → SL/TP" หรือไม่
     *   (ระบบปลุก AI ปิด — ไม่ป้อนแผนเทรดสำเร็จรูปให้ AI ก่อนมันวิเคราะห์เอง)
     */
    fun build(
        symbol: String,
        h4: List<Candle>, h1: List<Candle>,
        m15: List<Candle>, m5: List<Candle>, m1: List<Candle>,
        closedAware: Boolean = false,
        tradeRefs: Boolean = true
    ): Digest? {
        fun lastClosed(c: List<Candle>): Int = if (closedAware) c.indexOfLast { it.isClosed } else c.size - 2
        if (m15.size < 120) return null
        val m15i = lastClosed(m15).takeIf { it >= 0 } ?: return null
        val price = m15[m15i].close   // แท่งปิดล่าสุด
        val atrM15 = UnifiedSmcSignals.atr(m15, 14)[m15i]
        if (atrM15.isNaN() || atrM15 <= 0) return null

        // ── per-TF lines ──
        val tfLines = mutableListOf<TfLine>()
        fun addTf(tf: String, candles: List<Candle>) {
            if (candles.size < 60) return
            val st = UnifiedSmcSignals.structure(candles, UnifiedSmcSignals.SWING_L)
            val i = lastClosed(candles)
            if (i < 1) return
            val closes = candles.map { it.close }
            val lastEv = when {
                st.chochUp.sliceArray(max(0, i - 12)..i).any() -> "CHoCH↑"
                st.chochDn.sliceArray(max(0, i - 12)..i).any() -> "CHoCH↓"
                st.bosUp.sliceArray(max(0, i - 12)..i).any() -> "BOS↑"
                st.bosDn.sliceArray(max(0, i - 12)..i).any() -> "BOS↓"
                else -> "–"
            }
            val atrS = UnifiedSmcSignals.atr(candles, 14)
            val lastSh = st.sh.filter { !it.isNaN() }.lastOrNull()
            val lastSl = st.sl.filter { !it.isNaN() }.lastOrNull()

            // ── ตัวชี้วัดเพิ่มเติม คำนวณจากแท่งที่ปิดแล้วเท่านั้น (ถึง index i) ──
            // ใช้ TaIndicators กลาง (fail-closed: แท่งไม่พอจะได้ null ไม่ใช่ค่าปลอม)
            val closedC = candles.subList(0, i + 1)
            val cCloses = closedC.map { it.close }
            val cHighs = closedC.map { it.high }
            val cLows = closedC.map { it.low }
            val cVols = closedC.map { it.volume.coerceAtLeast(0.0) }
            val ta = com.skyliner2008.jarvis.tools.trading.TaIndicators

            val atrNow = atrS[i]
            val ema50Now = ema(closes, 50).let { it[i] }
            val bb = ta.bollingerBands(cCloses, 20, 2.0)
            val volAvg = ta.sma(cVols, 20)

            tfLines += TfLine(
                tf = tf, trend = st.trend[i], lastEvent = lastEv,
                close = candles[i].close,
                rsi = rsi(closes.take(i + 1), 14),
                ema50 = ema50Now,
                atr = atrNow,
                swingHigh = lastSh, swingLow = lastSl,
                adx = ta.adx(cHighs, cLows, cCloses, 14)?.adx,
                macdHist = ta.macd(cCloses)?.hist,
                bbPercentB = bb?.percentB,
                bbWidthAtr = if (bb != null && atrNow > 0 && !atrNow.isNaN()) (bb.upper - bb.lower) / atrNow else null,
                distEma50Atr = if (!ema50Now.isNaN() && atrNow > 0 && !atrNow.isNaN())
                    (candles[i].close - ema50Now) / atrNow else null,
                volumeRatio = if (volAvg != null && volAvg > 0) cVols.last() / volAvg else null
            )
        }
        addTf("H4", h4); addTf("H1", h1); addTf("M15", m15); addTf("M5", m5); addTf("M1", m1)

        // ── key levels ใกล้ราคา (รวมจากทุกแหล่ง) ──
        val above = mutableListOf<Level>()
        val below = mutableListOf<Level>()

        // swing H/L ของ H1 + M15
        for ((tf, candles) in listOf("H1" to h1, "M15" to m15)) {
            if (candles.size < 60) continue
            val st = UnifiedSmcSignals.structure(candles, UnifiedSmcSignals.SWING_L)
            st.sh.filter { !it.isNaN() }.takeLast(3).forEach { if (it > price) above += Level(it, "swing H $tf") else below += Level(it, "swing L $tf") }
            // swing low ที่อยู่เหนือราคา = ถูกหลุดไปแล้ว กลายเป็นแนวต้าน (เดิมติดป้าย "swing H?" ที่อ่านแล้วงง)
            st.sl.filter { !it.isNaN() }.takeLast(3).forEach { if (it > price) above += Level(it, "swing L $tf (หลุดแล้ว)") else below += Level(it, "swing L $tf") }
        }
        // EQH/EQL pools (M15) — sweep แล้วไม่เอา (reuse equalLevels flags ไม่ได้ pool โดยตรง → คำนวณ pool ใหม่แบบย่อ)
        eqPools(m15).forEach { (lvl, isHigh) -> if (isHigh) above += Level(lvl, "EQH") else below += Level(lvl, "EQL") }
        // FVG active บน M15 — mid คือจุด limit entry / magnet
        val fvg = UnifiedSmcSignals.fvgTouch(m15)
        fvg.bullMid.filter { !it.isNaN() }.lastOrNull()?.let { if (it > price) above += Level(it, "FVG bull mid") else below += Level(it, "FVG bull mid") }
        fvg.bearMid.filter { !it.isNaN() }.lastOrNull()?.let { if (it > price) above += Level(it, "FVG bear mid") else below += Level(it, "FVG bear mid") }
        // OB จาก SmcEngine (M15 window) — supply/demand ที่ยัง active
        runCatching {
            val snap = SmcEngine.buildSnapshot(m15.takeLast(300), symbol, "15m")
            snap.bullObs.filter { !it.mitigated }.take(2).forEach { below += Level(0.5 * (it.top + it.bottom), "Demand/OB") }
            snap.bearObs.filter { !it.mitigated }.take(2).forEach { above += Level(0.5 * (it.top + it.bottom), "Supply/OB") }
        }

        // เดิม take(3) — ตัดข้อมูลทิ้งทั้งที่งบโทเคนเหลือเฟือ
        // AI มองไม่เห็นกราฟ ยิ่งรู้ระดับราคารอบตัวมาก ยิ่งวางแผนได้ตรงโครงสร้างจริง
        // รวมระดับที่ซ้อนกันด้วยระยะหน่วย ATR — เดิมใช้ (price*10).toInt() ซึ่งกับ FX (1.0850 / 1.0890 → 10)
        // รวมทุกระดับในช่วง 0.1 ราคาเป็นก้อนเดียว ทำให้คู่เงินเหลือแนวรับ/ต้านไม่กี่ระดับ
        val bucket = 0.1 * atrM15
        val levelsAbove = above.filter { it.price > price }.sortedBy { it.price }
            .distinctBy { kotlin.math.round(it.price / bucket).toLong() }.take(6)
        val levelsBelow = below.filter { it.price < price }.sortedByDescending { it.price }
            .distinctBy { kotlin.math.round(it.price / bucket).toLong() }.take(6)

        // ── Premium/Discount จาก H1 (100 แท่ง) ──
        val pd = if (h1.size >= 100) {
            val win = h1.takeLast(100)
            val hi = win.maxOf { it.high }; val lo = win.minOf { it.low }
            val pos = if (hi > lo) (price - lo) / (hi - lo) else 0.5
            when {
                pos >= 0.70 -> "PREMIUM (${(pos * 100).toInt()}% ของ range H1)"
                pos <= 0.30 -> "DISCOUNT (${(pos * 100).toInt()}% ของ range H1)"
                else -> "EQUILIBRIUM (${(pos * 100).toInt()}% ของ range H1)"
            }
        } else "N/A"

        // ── Volume Profile ย่อ (M15 200 แท่ง, 24 bins) ──
        var poc: Double? = null; var vah: Double? = null; var val_: Double? = null
        if (m15.size >= 220) {
            val win = m15.takeLast(200)
            val lo = win.minOf { it.low }; val hi = win.maxOf { it.high }
            if (hi > lo) {
                val bins = 24
                val vol = DoubleArray(bins)
                for (c in win) {
                    val typ = (c.high + c.low + c.close) / 3.0
                    val bIdx = (((typ - lo) / (hi - lo)) * (bins - 1)).toInt().coerceIn(0, bins - 1)
                    vol[bIdx] += c.volume
                }
                fun binCenter(bIdx: Int) = lo + (bIdx + 0.5) / (bins - 1) * (hi - lo)
                val pocIdx = vol.indices.maxByOrNull { vol[it] } ?: 0
                poc = binCenter(pocIdx)
                val total = vol.sum()
                if (total > 0) {
                    var acc = vol[pocIdx]; var up = pocIdx; var dn = pocIdx
                    while (acc < total * 0.70 && (up < bins - 1 || dn > 0)) {
                        val upV = if (up < bins - 1) vol[up + 1] else -1.0
                        val dnV = if (dn > 0) vol[dn - 1] else -1.0
                        if (upV >= dnV) { up++; acc += vol[up] } else { dn--; acc += vol[dn] }
                    }
                    vah = binCenter(up); val_ = binCenter(dn)
                }
            }
        }

        // ── ราคาอยู่ที่จุดสำคัญไหม (ภายใน 0.3×ATR M15) ──
        val tol = 0.3 * atrM15
        val nearLevels = (levelsAbove + levelsBelow).filter { abs(it.price - price) <= tol }
        val keyZoneHit = if (nearLevels.isNotEmpty())
            "ราคาอยู่ที่จุดสำคัญ: " + nearLevels.joinToString(", ") { "${it.kind} ${fmt(it.price)} (ห่าง ${"%.1f".format(abs(it.price - price) / atrM15)}×ATR)" }
        else null

        // ── อ้างอิงเชิงโครงสร้างสำหรับวาง SL/TP ──
        val refLines = if (!tradeRefs) emptyList() else buildList {
            val nearestBelow = levelsBelow.firstOrNull(); val nearestAbove = levelsAbove.firstOrNull()
            if (nearestBelow != null && nearestAbove != null) {
                add("อ้างอิงโครงสร้าง: BUY → SL ใต้ ${nearestBelow.kind} ${fmt(nearestBelow.price)}, TP ใต้ ${nearestAbove.kind} ${fmt(nearestAbove.price)}")
                add("อ้างอิงโครงสร้าง: SELL → SL เหนือ ${nearestAbove.kind} ${fmt(nearestAbove.price)}, TP ที่ ${nearestBelow.kind} ${fmt(nearestBelow.price)}")
            }
        }

        // ── text digest สำหรับ prompt ──
        val text = buildString {
            appendLine("══ โครงสร้างตลาด 5TF — $symbol @ ${fmt(price)} ══")
            for (t in tfLines) {
                val trendTxt = if (t.trend > 0) "UP" else if (t.trend < 0) "DOWN" else "RANGE"
                val emaTxt = when {
                    t.ema50.isNaN() -> ""
                    t.distEma50Atr != null -> "EMA50 ${if (t.distEma50Atr >= 0) "+" else ""}${"%.1f".format(t.distEma50Atr)}×ATR"
                    t.close > t.ema50 -> "เหนือ EMA50"
                    else -> "ใต้ EMA50"
                }
                append("[${t.tf}] trend=$trendTxt ล่าสุด=${t.lastEvent} | RSI ${"%.0f".format(t.rsi)}")
                t.adx?.let { append(" | ADX ${"%.0f".format(it)}") }
                t.macdHist?.let { append(" | MACD ${if (it >= 0) "+" else ""}${"%.2f".format(it)}") }
                if (emaTxt.isNotBlank()) append(" | $emaTxt")
                t.bbPercentB?.let { append(" | BB%B ${"%.0f".format(it)}") }
                t.bbWidthAtr?.let { append(" | BBw ${"%.1f".format(it)}×ATR") }
                t.volumeRatio?.let { append(" | Vol ${"%.1f".format(it)}x") }
                append(" | ATR ${fmt(t.atr)}")
                appendLine()
            }
            appendLine("Premium/Discount (H1): $pd")
            // แนบระยะห่างเป็นหน่วย ATR — AI ประเมินได้ทันทีว่า "ใกล้พอจะเป็นเป้า/เป็นอุปสรรคไหม"
            // โดยไม่ต้องคำนวณเอง (ซึ่งเป็นสิ่งที่ AI ทำพลาดง่ายที่สุด)
            fun lvl(l: Level): String =
                "${fmt(l.price)} (${l.kind}, ${"%.1f".format(abs(l.price - price) / atrM15)}×ATR)"
            if (levelsAbove.isNotEmpty())
                appendLine("แนวต้านเหนือราคา: " + levelsAbove.joinToString(", ") { lvl(it) })
            if (levelsBelow.isNotEmpty())
                appendLine("แนวรับใต้ราคา: " + levelsBelow.joinToString(", ") { lvl(it) })
            if (poc != null) appendLine("Volume Profile M15/200: POC ${fmt(poc)} | VAH ${fmt(vah!!)} | VAL ${fmt(val_!!)}")
            keyZoneHit?.let { appendLine("⚠️ $it") }
            refLines.forEach { appendLine(it) }
        }.trim()

        return Digest(
            symbol = symbol, price = price, tfLines = tfLines,
            levelsAbove = levelsAbove, levelsBelow = levelsBelow,
            premiumDiscount = pd, poc = poc, vah = vah, val_ = val_,
            keyZoneHit = keyZoneHit, text = text
        )
    }

    /** EQ pools แบบย่อ: swing ยืนยันคู่ล่าสุดที่ห่างกัน ≤0.25×ATR (M15) */
    private fun eqPools(c: List<Candle>): List<Pair<Double, Boolean>> {
        if (c.size < 60) return emptyList()
        val (sh, sl) = UnifiedSmcSignals.confirmedSwings(c, UnifiedSmcSignals.SWING_L)
        val atrS = UnifiedSmcSignals.atr(c, 14)
        val out = mutableListOf<Pair<Double, Boolean>>()
        // explicit loop — หลีกเลี่ยง mapIndexedNotNull + destructuring (K2 inference พังบน commonMain)
        val highs = ArrayList<Pair<Int, Double>>()
        val lows = ArrayList<Pair<Int, Double>>()
        for (i in sh.indices) {
            if (!sh[i].isNaN()) highs.add(i to sh[i])
            if (!sl[i].isNaN()) lows.add(i to sl[i])
        }
        val hs = highs.takeLast(4)
        val ls = lows.takeLast(4)
        if (hs.size >= 2) {
            val a = atrS[hs.last().first]
            val p1 = hs[hs.size - 2].second; val p2 = hs.last().second
            if (!a.isNaN() && abs(p2 - p1) <= 0.25 * a) out += (max(p1, p2) to true)
        }
        if (ls.size >= 2) {
            val a = atrS[ls.last().first]
            val p1 = ls[ls.size - 2].second; val p2 = ls.last().second
            if (!a.isNaN() && abs(p2 - p1) <= 0.25 * a) out += (min(p1, p2) to false)
        }
        return out
    }

    private fun ema(values: List<Double>, period: Int): List<Double> {
        if (values.size < period) return List(values.size) { Double.NaN }
        val k = 2.0 / (period + 1)
        val out = ArrayList<Double>(values.size)
        var prev = values.take(period).average()
        repeat(period - 1) { out.add(Double.NaN) }
        out.add(prev)
        for (i in period until values.size) { prev = values[i] * k + prev * (1 - k); out.add(prev) }
        return out
    }

    private fun rsi(closes: List<Double>, period: Int): Double {
        if (closes.size <= period) return 50.0
        var gain = 0.0; var loss = 0.0
        for (i in 1..period) { val d = closes[i] - closes[i - 1]; if (d > 0) gain += d else loss -= d }
        var avgGain = gain / period; var avgLoss = loss / period
        for (i in period + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + max(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-d, 0.0)) / period
        }
        return if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
    }

    private fun fmt(v: Double) = com.skyliner2008.jarvis.automation.wake.formatPrice(v)
}

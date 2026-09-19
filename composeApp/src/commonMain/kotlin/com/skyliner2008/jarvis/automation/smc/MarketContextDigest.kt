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
        val volumeRatio: Double? = null,
        /** รายละเอียดโครงสร้าง/โมเมนตัม/แท่งเทียนของ TF นี้ */
        val detail: TfDetail? = null
    )

    /**
     * สิ่งที่นักเทรดอ่านจากกราฟ TF หนึ่งนอกเหนือจากค่าอินดิเคเตอร์ — AI มองไม่เห็นกราฟจึงต้องได้ข้อมูลนี้เป็นตัวเลข
     * (เดิมมีแค่ trend/RSI/ADX/MACD/EMA50/BB/ATR ซึ่งไม่บอกว่าราคาอยู่ตรงไหนของขา วิ่งมาแรงแค่ไหน หรือแท่งล่าสุดปฏิเสธราคาไหม)
     */
    data class TfDetail(
        /** ลำดับ swing ล่าสุด เช่น "HH+HL" (ขาขึ้นสมบูรณ์) / "LH+LL" / "HH+LL" (ขยายตัว) — null ถ้า swing ไม่พอ */
        val swingSeq: String?,
        /** ตำแหน่งราคาในกรอบ high–low 50 แท่ง (0–100) */
        val rangePos50: Double?,
        val ema20: Double?, val ema200: Double?,
        /** RSI เมื่อ 3 แท่งก่อน — บอกทิศของโมเมนตัม */
        val rsiPrev3: Double?,
        /** divergence แบบปกติระหว่าง swing 2 จุดล่าสุด (ราคาเทียบ RSI) */
        val divergence: String?,
        /** ราคาเคลื่อนไปกี่ ATR ใน 12 แท่ง (+ ขึ้น / − ลง) */
        val move12Atr: Double?,
        /** จำนวนแท่งสีเดียวกันติดกันจนถึงแท่งล่าสุด (+ เขียว / − แดง) */
        val streak: Int,
        /** ATR ตอนนี้สูงกว่ากี่ % ของ ATR 100 แท่งล่าสุด */
        val atrPercentile: Double?,
        /** ลักษณะแท่งปิดล่าสุด เช่น "แดง 1.3×ATR ไส้บน 62%" */
        val candle: String
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
            val lastEv = lastStructureEvent(st, i)
            // ATR สูตรเดียวกับที่ปัจจัยปลุกใช้ (Wilder, seed = SMA 14 ตัวแรก) — เดิมใช้ UnifiedSmcSignals.atr ที่ seed จากแท่งแรก
            // บน M1 (300 แท่ง)/M5 (500 แท่ง) ค่ายังไม่ลู่เข้า ต่างจากค่าที่ปัจจัยใช้ถึง 0.24%
            val atrS = com.skyliner2008.jarvis.tools.trading.TaIndicators
                .atrSeries(candles.map { it.high }, candles.map { it.low }, candles.map { it.close }, 14)
                .map { it ?: Double.NaN }
            // swing ที่ยืนยันถึงแท่งปิดล่าสุดเท่านั้น
            val lastSh = (0..i).lastOrNull { !st.sh[it].isNaN() }?.let { st.sh[it] }
            val lastSl = (0..i).lastOrNull { !st.sl[it].isNaN() }?.let { st.sl[it] }

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
                volumeRatio = if (volAvg != null && volAvg > 0) cVols.last() / volAvg else null,
                detail = runCatching { tfDetail(closedC, st, atrS, i) }.getOrNull()
            )
        }
        // D1 รวมจาก H4 ตามขอบวันเทรดของตลาด (ทอง/FX เริ่มวัน 22:00 UTC) — เดิมไม่มีภาพรายวันเลย
        val d1 = resampleDaily(h4, com.skyliner2008.jarvis.tools.trading.TaIndicators.sessionOffsetHoursFor(symbol))
        addTf("D1", d1)
        addTf("H4", h4); addTf("H1", h1); addTf("M15", m15); addTf("M5", m5); addTf("M1", m1)

        // ── key levels ใกล้ราคา (รวมจากทุกแหล่ง) ──
        val above = mutableListOf<Level>()
        val below = mutableListOf<Level>()

        // swing H/L ของ H4 + H1 + M15
        for ((tf, candles) in listOf("H4" to h4, "H1" to h1, "M15" to m15)) {
            if (candles.size < 60) continue
            val st = UnifiedSmcSignals.structure(candles, UnifiedSmcSignals.SWING_L)
            // swing high ที่อยู่ใต้ราคา = ถูกทะลุไปแล้ว กลายเป็นแนวรับ (เดิมติดป้ายผิดเป็น "swing L")
            st.sh.filter { !it.isNaN() }.takeLast(3).forEach { if (it > price) above += Level(it, "swing H $tf") else below += Level(it, "swing H $tf (ทะลุแล้ว)") }
            // swing low ที่อยู่เหนือราคา = ถูกหลุดไปแล้ว กลายเป็นแนวต้าน (เดิมติดป้าย "swing H?" ที่อ่านแล้วงง)
            st.sl.filter { !it.isNaN() }.takeLast(3).forEach { if (it > price) above += Level(it, "swing L $tf (หลุดแล้ว)") else below += Level(it, "swing L $tf") }
        }
        // High/Low ของวันก่อน + สัปดาห์ก่อน (จาก D1 ที่รวมจาก H4)
        previousHighLow(d1, weekly = false)?.let { (hi, lo) -> listOf(Level(hi, "PDH"), Level(lo, "PDL")) }
            ?.forEach { if (it.price > price) above += it else below += it }
        previousHighLow(d1, weekly = true)?.let { (hi, lo) -> listOf(Level(hi, "PWH"), Level(lo, "PWL")) }
            ?.forEach { if (it.price > price) above += it else below += it }
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
            appendLine("══ โครงสร้างตลาด D1 + 5TF — $symbol @ ${fmt(price)} ══")
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
                t.detail?.let { d -> detailLines(t, d).forEach { appendLine("    $it") } }
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

    /**
     * เหตุการณ์โครงสร้างที่ "ใหม่ที่สุด" ใน 12 แท่งล่าสุด (ถึงแท่ง [i]) พร้อมอายุเป็นจำนวนแท่ง
     *
     * เดิมใช้ `sliceArray(..).any()` — `any()` ที่ไม่มีเงื่อนไขของ BooleanArray แปลว่า "array ไม่ว่าง"
     * จึงได้ "CHoCH↑" ทุก TF ทุกครั้ง (ตรวจพบจากการเล่นซ้ำ 300 แท่ง: 300/300) และยังเรียงตามชนิดแทนเวลา
     */
    internal fun lastStructureEvent(st: UnifiedSmcSignals.Struct, i: Int, window: Int = 12): String {
        for (k in i downTo max(0, i - window)) {
            val ev = when {
                st.chochUp[k] -> "CHoCH↑"
                st.chochDn[k] -> "CHoCH↓"
                st.bosUp[k] -> "BOS↑"
                st.bosDn[k] -> "BOS↓"
                else -> null
            } ?: continue
            return if (k == i) "$ev (แท่งล่าสุด)" else "$ev (${i - k} แท่งก่อน)"
        }
        return "–"
    }

    /**
     * 2 บรรทัดใต้แต่ละ TF: "โครงสร้าง" (ราคาอยู่ตรงไหนของขา) และ "แรง/แท่ง" (วิ่งมาแรงแค่ไหน แท่งล่าสุดบอกอะไร)
     * ระยะทุกตัวเป็นหน่วย ATR ของ TF นั้น — AI ไม่ต้องคำนวณเอง (จุดที่ AI พลาดง่ายที่สุด)
     */
    internal fun detailLines(t: TfLine, d: TfDetail): List<String> {
        fun atrs(v: Double) = "${if (v >= 0) "+" else "−"}${"%.1f".format(abs(v))}×ATR"
        val ok = t.atr > 0 && !t.atr.isNaN()
        val structure = buildList {
            d.swingSeq?.let { add("swing $it") }
            if (ok) {
                t.swingHigh?.let { add("swing H ${fmt(it)} (${atrs((it - t.close) / t.atr)})") }
                t.swingLow?.let { add("swing L ${fmt(it)} (${atrs((it - t.close) / t.atr)})") }
            }
            d.rangePos50?.let { add("อยู่ ${it.toInt()}% ของกรอบ 50 แท่ง") }
            val e20 = d.ema20; val e50 = t.ema50.takeIf { !it.isNaN() }; val e200 = d.ema200
            if (e20 != null && e50 != null && e200 != null) {
                add(when {
                    e20 > e50 && e50 > e200 -> "EMA 20>50>200 (เรียงขาขึ้น)"
                    e20 < e50 && e50 < e200 -> "EMA 20<50<200 (เรียงขาลง)"
                    else -> "EMA 20/50/200 ไม่เรียง"
                })
                if (ok) add("ห่าง EMA200 ${atrs((t.close - e200) / t.atr)}")
            }
        }
        val momentum = buildList {
            d.rsiPrev3?.let { add("RSI ${"%.0f".format(it)}→${"%.0f".format(t.rsi)} (3 แท่ง)") }
            d.divergence?.let { add(it) }
            d.move12Atr?.let { add("12 แท่งเคลื่อน ${atrs(it)}") }
            if (abs(d.streak) >= 3) add("${if (d.streak > 0) "เขียว" else "แดง"}ติดกัน ${abs(d.streak)} แท่ง")
            d.atrPercentile?.let { add("ATR สูงกว่า ${it.toInt()}% ของ 100 แท่ง") }
            add("แท่งล่าสุด ${d.candle}")
        }
        return listOfNotNull(
            structure.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "โครงสร้าง: "),
            momentum.joinToString(" · ", prefix = "แรง/แท่ง: ")
        )
    }

    /** รายละเอียดของ TF — ใช้แท่งปิดแล้ว [c] (index สุดท้าย = [i]) เท่านั้น */
    private fun tfDetail(c: List<Candle>, st: UnifiedSmcSignals.Struct, atrS: List<Double>, i: Int): TfDetail? {
        val ta = com.skyliner2008.jarvis.tools.trading.TaIndicators
        val atr = atrS[i].takeIf { !it.isNaN() && it > 0 } ?: return null
        val closes = c.map { it.close }
        val swingL = UnifiedSmcSignals.SWING_L

        // swing ที่ยืนยันแล้ว (index ของแท่งยืนยัน, ราคา)
        val highs = (0..i).filter { !st.sh[it].isNaN() }.map { it to st.sh[it] }
        val lows = (0..i).filter { !st.sl[it].isNaN() }.map { it to st.sl[it] }
        val seq = if (highs.size >= 2 && lows.size >= 2)
            (if (highs.last().second > highs[highs.size - 2].second) "HH" else "LH") + "+" +
                (if (lows.last().second > lows[lows.size - 2].second) "HL" else "LL")
        else null

        val win = c.takeLast(50)
        val hi50 = win.maxOf { it.high }
        val lo50 = win.minOf { it.low }
        val pos50 = if (c.size >= 50 && hi50 > lo50) (c[i].close - lo50) / (hi50 - lo50) * 100 else null

        val rsiS = ta.rsiSeries(closes, 14)
        // divergence ปกติ: เทียบ RSI ที่ "จุดยอดจริง" (แท่งยืนยัน − L) ของ swing 2 จุดล่าสุด — จุดล่าสุดต้องไม่เกิน 30 แท่ง
        fun div(sw: List<Pair<Int, Double>>, isHigh: Boolean): Pair<Int, String>? {
            if (sw.size < 2) return null
            val (ia, pa) = sw[sw.size - 2]
            val (ib, pb) = sw.last()
            if (i - ib > 30) return null
            val ra = rsiS.getOrNull(ia - swingL) ?: return null
            val rb = rsiS.getOrNull(ib - swingL) ?: return null
            return when {
                isHigh && pb > pa && rb < ra -> ib to "Bearish div (ราคา HH แต่ RSI ${"%.0f".format(ra)}→${"%.0f".format(rb)})"
                !isHigh && pb < pa && rb > ra -> ib to "Bullish div (ราคา LL แต่ RSI ${"%.0f".format(ra)}→${"%.0f".format(rb)})"
                else -> null
            }
        }
        // ใหม่กว่าขึ้นก่อน — ถ้ายืนยันพร้อมกันทั้งสองแบบ แสดงทั้งคู่ (ตลาดกำลังขยายตัวทั้งบนและล่าง)
        val divergence = listOfNotNull(div(highs, true), div(lows, false))
            .sortedByDescending { it.first }.joinToString(" · ") { it.second }.ifBlank { null }

        var streak = 0
        val dir0 = kotlin.math.sign(c[i].close - c[i].open)
        if (dir0 != 0.0) {
            var k = i
            while (k >= 0 && kotlin.math.sign(c[k].close - c[k].open) == dir0) { streak++; k-- }
            if (dir0 < 0) streak = -streak
        }

        val atrWin = atrS.subList(max(0, i - 99), i + 1).filter { !it.isNaN() }
        val atrPct = if (atrWin.size >= 50) atrWin.count { it <= atr } * 100.0 / atrWin.size else null

        return TfDetail(
            swingSeq = seq, rangePos50 = pos50,
            ema20 = ta.emaSeries(closes, 20).lastOrNull(), ema200 = ta.emaSeries(closes, 200).lastOrNull(),
            rsiPrev3 = rsiS.getOrNull(i - 3), divergence = divergence,
            move12Atr = if (i >= 12) (c[i].close - c[i - 12].close) / atr else null,
            streak = streak, atrPercentile = atrPct,
            candle = describeCandle(c[i], c.getOrNull(i - 1), atr)
        )
    }

    /** ลักษณะแท่งเทียน: สี ขนาด (ATR) ไส้ ตัวตัน/doji และการกลืนกินแท่งก่อน */
    internal fun describeCandle(b: Candle, prev: Candle?, atr: Double): String {
        val range = b.high - b.low
        val body = abs(b.close - b.open)
        val color = when { b.close > b.open -> "เขียว"; b.close < b.open -> "แดง"; else -> "doji" }
        if (range <= 0) return "$color (ไม่มีช่วงราคา)"
        val tags = mutableListOf<String>()
        val uw = (b.high - max(b.open, b.close)) / range
        val lw = (min(b.open, b.close) - b.low) / range
        if (uw >= 0.5) tags += "ไส้บน ${(uw * 100).toInt()}%"
        if (lw >= 0.5) tags += "ไส้ล่าง ${(lw * 100).toInt()}%"
        if (body / range >= 0.7) tags += "ตัวตัน"
        if (body / range <= 0.1 && color != "doji") tags += "doji"
        if (prev != null && body > 0) {
            val pBody = abs(prev.close - prev.open)
            val opposite = (b.close - b.open) * (prev.close - prev.open) < 0
            if (opposite && pBody > 0 && max(b.open, b.close) >= max(prev.open, prev.close) &&
                min(b.open, b.close) <= min(prev.open, prev.close)
            ) tags += "กลืนกินแท่งก่อน"
        }
        return "$color ${"%.1f".format(range / atr)}×ATR" + if (tags.isEmpty()) "" else " " + tags.joinToString(" ")
    }

    private const val DAY_MS = 86_400_000L

    /**
     * รวมแท่ง H4 เป็นแท่งวัน ตามขอบวันเทรด ([offsetHours] จาก TaIndicators.sessionOffsetHoursFor: −2 = วันเริ่ม 22:00 UTC)
     *
     * จัดกลุ่มด้วย "จุดกึ่งกลาง" ของแท่ง H4 ไม่ใช่เวลาเปิด — OANDA วางแท่ง H4 ของทอง/FX ตาม 17:00 นิวยอร์ก
     * (ฤดูร้อน 21:00/01:00/… UTC, ฤดูหนาว 22:00/02:00/… UTC) แท่ง 21:00 UTC คือแท่งแรกของวันเทรดใหม่
     * ถ้าใช้เวลาเปิดกับขอบ 22:00 UTC จะตกไปอยู่วันก่อน; จุดกึ่งกลางถูกทั้งสองฤดูและกับ Binance (00:00/04:00/… UTC)
     * แท่งวันสุดท้ายถือว่า "ยังไม่ปิด" จนกว่าแท่ง H4 ตัวสุดท้ายจะเลยจุดกึ่งกลางช่วงท้ายวัน
     */
    internal fun resampleDaily(h4: List<Candle>, offsetHours: Int): List<Candle> {
        val src = h4.filter { it.isClosed }
        if (src.isEmpty()) return emptyList()
        val shift = -offsetHours * 3_600_000L
        val half = 2 * 3_600_000L
        val groups = src.groupBy { (it.timestamp + half + shift).floorDiv(DAY_MS) }.toSortedMap()
        val lastEnd = src.last().timestamp + 4 * 3_600_000L
        val lastDay = groups.lastKey()
        return groups.map { (day, bars) ->
            val start = day * DAY_MS - shift
            Candle(
                bars.first().open, bars.maxOf { it.high }, bars.minOf { it.low }, bars.last().close,
                bars.sumOf { it.volume }, start, isClosed = day != lastDay || lastEnd + half >= start + DAY_MS
            )
        }
    }

    /** High/Low ของวัน (หรือสัปดาห์ จันทร์–อาทิตย์) ก่อนหน้าที่ปิดครบแล้ว จากแท่งวัน [d1] */
    internal fun previousHighLow(d1: List<Candle>, weekly: Boolean): Pair<Double, Double>? {
        val closed = d1.filter { it.isClosed }
        if (closed.isEmpty()) return null
        if (!weekly) return closed.last().high to closed.last().low
        // สัปดาห์ของแท่งวัน: epoch day 0 = พฤหัส → +3 ให้จันทร์เป็นวันแรก (+12 ชม. กันขอบวันที่เริ่ม 22:00 UTC)
        fun week(c: Candle) = ((c.timestamp + 12 * 3_600_000L).floorDiv(DAY_MS) + 3).floorDiv(7)
        val current = week(d1.last())
        val prev = closed.filter { week(it) < current }
        if (prev.isEmpty()) return null
        val w = week(prev.last())
        val bars = prev.filter { week(it) == w }
        return bars.maxOf { it.high } to bars.minOf { it.low }
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

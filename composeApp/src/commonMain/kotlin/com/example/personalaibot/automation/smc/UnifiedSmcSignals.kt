package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * UnifiedSmcSignals — port 1:1 ของ Unified SMC Multi-TF Strategy V5 (research)
 * ต้นฉบับ: tools/unified_smc_lab.py (XAUUSD, MT5 data, split 60/20/20 + WFO + cost stress)
 *
 * Engine เดียว ไม่ใช่สัญญาณแยกต่อ TF — TF stack มีหน้าที่:
 *   H4, H1  -> Market Context  (structure trend)
 *   M30, H1 -> Trend Context   (structure trend)
 *   M15     -> Setup           (FVG tap / EQL sweep / IDM sweep + BOS/CHoCH displacement)
 *   M5 + M1 -> Confirmation    (BOS/CHoCH + body quality — M1 เป็น additive layer เสริม M5)
 *   entry   -> LIMIT ที่ FVG midpoint (fallback: market ที่ open แท่งถัดไป)
 *
 * กฎเหล็ก: ไม่มี setup (zone tap + displacement ใน setup_win แท่ง) = HOLD เสมอ
 * Regime gate: เทรดเฉพาะ TREND (H1 ADX >= 18 และ H1 trend != 0); CHAOTIC (ATR
 * percentile > 0.90 หรือ < 0.10) = HOLD เสมอ
 *
 * Params = V5 robust basin (ผ่าน 3/5 gates; holdout n=16 < 30 และ permutation p=0.38
 * → สถานะ research/forward-test เท่านั้น ห้ามถือว่าพิสูจน์แล้ว)
 *
 * Causality: แท่ง HTF ใช้ได้เมื่อปิดแล้วเท่านั้น (as-of join: open + duration <= t)
 * หมายเหตุ: H4/M30 resample ด้วย bin เที่ยงคืน UTC (ต้นฉบับใช้ origin="start_day"
 * ของ timezone ไฟล์ข้อมูล — ต่างกันเล็กน้อยที่ขอบวัน ไม่กระทบ structure trend)
 */
object UnifiedSmcSignals {

    // ── V5 robust-basin params (ห้ามจูนบนมือถือ — จูนใน lab เท่านั้น) ──
    const val SWING_L = 5
    const val IDM_L = 2
    const val ADX_TH = 18.0
    const val SCORE_TH = 0.65
    const val MARGIN = 0.20
    const val SETUP_WIN = 4
    const val TP_R = 3.0
    const val SL_BUF = 0.25
    const val MIN_RR = 1.5
    const val BODY_MIN = 0.50
    const val USE_IDM = true
    const val USE_BOS = true
    private const val W_CTX = 0.30
    private const val W_TR = 0.20
    private const val W_SETUP = 0.30
    private const val W_CONF = 0.20
    private const val ATR_N = 14
    private const val VOL_WIN = 500
    private const val VOL_MIN_PERIODS = 100
    private const val CHAOS_HI = 0.90
    private const val CHAOS_LO = 0.10

    data class UnifiedSignal(
        val side: String,           // BUY | SELL
        val entry: Double,          // LIMIT @ FVG mid หรือ MARKET @ next M15 open
        val sl: Double,
        val tp: Double,
        val score: Double,          // คะแนนรวมฝั่งที่ชนะ (0..1)
        val stars: Int,             // 1-5 จาก score
        val entryMode: String,      // LIMIT_FVG_MID | MARKET_NEXT_OPEN
        val regime: String,         // TREND (ผ่าน gate เท่านั้นถึงมีสัญญาณ)
        val triggers: List<String>,
        val riskReward: Double
    ) {
        val key: String get() = "$side|UNIFIED_SMC|${round(sl * 10000) / 10000}|${round(tp * 10000) / 10000}"
    }

    /** ผลการประเมิน 1 แท่ง — signal (ถ้ามี) + เหตุผล (ใช้ heartbeat log ตอน HOLD) */
    data class EvalResult(val signal: UnifiedSignal?, val reason: String)

    /**
     * ประเมินสัญญาณ ณ แท่ง M15 ปิดล่าสุด (m15[size-2]) — edge-triggered:
     * คืนสัญญาณเฉพาะเมื่อแท่งปิดล่าสุด "เพิ่งเข้าเงื่อนไข" (แท่งก่อนไม่เข้า)
     * h1: ≥150 แท่ง (ต้อง resample H4 + ADX/ATR-percentile warmup), m15: ≥120, m5: ≥120
     * m1: (ออปชัน) ≥120 แท่ง — confirmation layer เสริม: merge BOS/CHoCH+body ของ M1
     *     เข้า bin M15 เดียวกับ M5 (OR กัน) ⚠️ M1 ไม่ได้อยู่ใน backtest V5 —
     *     เป็น additive post-research refinement (2026-08-28) น้ำหนัก W_CONF เท่าเดิม
     * HOLD ทุกกรณีคืน reason ไทยสั้นๆ เพื่อ observability (log dedup ต่อแท่งปิด)
     */
    fun evaluate(h1: List<Candle>, m15: List<Candle>, m5: List<Candle>, m1: List<Candle> = emptyList()): EvalResult {
        if (m15.size < 120 || h1.size < 150 || m5.size < 120)
            return EvalResult(null, "ข้อมูลไม่พอ (H1=${h1.size}/M15=${m15.size}/M5=${m5.size})")

        // ── resample: H4 จาก H1, M30 จาก M15 ──
        val h4 = resample(h1, 240)
        val m30 = resample(m15, 30)
        if (h4.size < 30 || m30.size < 30)
            return EvalResult(null, "resample เล็กเกิน (H4=${h4.size}/M30=${m30.size})")

        // ── per-TF structure (confirmed swings → BOS/CHoCH/trend) ──
        val stH4 = structure(h4, SWING_L)
        val stH1 = structure(h1, SWING_L)
        val stM30 = structure(m30, SWING_L)
        val stM15 = structure(m15, SWING_L)
        val stM5 = structure(m5, SWING_L)
        val stM1 = if (m1.size >= 120) structure(m1, SWING_L) else null

        // ── M15 setup features ──
        val atrM15 = atr(m15, ATR_N)
        val (eqlUp, eqhDn) = equalLevels(m15, SWING_L, atrM15)
        val (idmUp, idmDn) = idmSweeps(m15, IDM_L)
        val fvg = fvgTouch(m15)

        // ── regime features จาก H1: ADX(14) + ATR rolling-percentile ──
        val atrH1 = atr(h1, ATR_N)
        val adxH1 = adx(h1, ATR_N)
        val volH1 = rollingRankPct(atrH1, VOL_WIN, VOL_MIN_PERIODS, default = 0.5)

        // ── as-of join HTF → base M15 (แท่ง HTF ใช้ได้เมื่อปิดแล้วเท่านั้น) ──
        val baseTs = LongArray(m15.size) { m15[it].timestamp }
        val h4tr = mapHtf(baseTs, h4, 240, stH4.trend.toDoubleArray(), 0.0)
        val h1tr = mapHtf(baseTs, h1, 60, stH1.trend.toDoubleArray(), 0.0)
        val m30tr = mapHtf(baseTs, m30, 30, stM30.trend.toDoubleArray(), 0.0)
        val h1adx = mapHtf(baseTs, h1, 60, adxH1, 0.0)
        val h1vp = mapHtf(baseTs, h1, 60, volH1, 0.5)

        // ── M5 confirmation: aggregate เข้า bin M15 ──
        //    + M1 (ถ้ามี): merge เข้า map เดียวกัน (OR) — layer เสริมนอก backtest V5
        val m5agg = aggregateM5(m5, stM5)
        if (stM1 != null) aggregateLtfInto(m1, stM1, m5agg)

        // ffilled last confirmed swing บน M15 (อ้างอิง SL/TP)
        val lastSh = ffill(stM15.sh)
        val lastSl = ffill(stM15.sl)

        // ── ประเมินแท่งปิดล่าสุด + edge trigger เทียบแท่งก่อน ──
        val i = m15.size - 2
        val now = scoreAt(i, h4tr, h1tr, m30tr, h1adx, h1vp, stM15, eqlUp, eqhDn, idmUp, idmDn, fvg, m5agg, baseTs)
        if (now.side == 0) return EvalResult(null, now.reason)
        val prev = scoreAt(i - 1, h4tr, h1tr, m30tr, h1adx, h1vp, stM15, eqlUp, eqhDn, idmUp, idmDn, fvg, m5agg, baseTs)
        if (prev.side == now.side)
            return EvalResult(null, "ไม่ใช่ edge ใหม่ (${if (now.side == 1) "BUY" else "SELL"} ค้างจากแท่งก่อน)")

        // ── execution levels (ตาม simulate_v2) ──
        val s = now.side
        val a = atrM15[i]
        if (a.isNaN() || a <= 0) return EvalResult(null, "ATR ยังไม่พร้อม")
        val limitRef = if (s == 1) fvg.bullMid[i] else fvg.bearMid[i]
        val market = limitRef.isNaN()
        val ref = if (market) m15[i + 1].open else limitRef   // open แท่งกำลังวิ่ง = next bar open
        val slStruct = if (s == 1) lastSl[i] - SL_BUF * a else lastSh[i] + SL_BUF * a
        val sl = if (slStruct.isNaN() || (s == 1 && slStruct >= ref) || (s == -1 && slStruct <= ref)) {
            if (s == 1) ref - 1.2 * a else ref + 1.2 * a
        } else slStruct
        val riskDist = if (s == 1) ref - sl else sl - ref
        if (riskDist < 0.3 * a || riskDist > 4.0 * a)
            return EvalResult(null, "risk_dist ${"%.2f".format(riskDist / a)}×ATR นอกกรอบ 0.3–4.0")
        val opp = if (s == 1) lastSh[i] else lastSl[i]
        val oppReward = if (opp.isNaN()) Double.NaN else (if (s == 1) opp - ref else ref - opp)
        val tp = if (!oppReward.isNaN() && oppReward >= MIN_RR * riskDist) opp
        else if (s == 1) ref + TP_R * riskDist else ref - TP_R * riskDist

        val triggers = mutableListOf<String>()
        triggers += "CTX ${if (h4tr[i] > 0) "H4▲" else if (h4tr[i] < 0) "H4▼" else "H4–"}/${if (h1tr[i] > 0) "H1▲" else if (h1tr[i] < 0) "H1▼" else "H1–"}"
        triggers += "setup: ${now.setupDesc} + ${now.structDesc}"
        if (now.confOk) triggers += "M5 confirm: ${now.confDesc}"
        triggers += "regime=TREND(ADX ${"%.0f".format(h1adx[i])})"
        triggers += if (market) "entry=MARKET@next open" else "entry=LIMIT@FVG mid"

        return EvalResult(UnifiedSignal(
            side = if (s == 1) "BUY" else "SELL",
            entry = ref, sl = sl, tp = tp,
            score = now.score,
            stars = (now.score * 5).toInt().coerceIn(1, 5),
            entryMode = if (market) "MARKET_NEXT_OPEN" else "LIMIT_FVG_MID",
            regime = "TREND",
            triggers = triggers,
            riskReward = abs(tp - ref) / riskDist
        ), "SIGNAL")
    }

    // ── scoring (ตรง score_signals ของต้นฉบับ ณ แท่ง i) ─────────────────────

    private class ScoreOut(
        val side: Int, val score: Double,
        val setupDesc: String, val structDesc: String,
        val confOk: Boolean, val confDesc: String,
        val reason: String = ""
    )

    private fun scoreAt(
        i: Int,
        h4tr: DoubleArray, h1tr: DoubleArray, m30tr: DoubleArray,
        h1adx: DoubleArray, h1vp: DoubleArray,
        stM15: Struct,
        eqlUp: BooleanArray, eqhDn: BooleanArray,
        idmUp: BooleanArray, idmDn: BooleanArray,
        fvg: FvgTouch, m5agg: Map<Long, M5Agg>,
        baseTs: LongArray
    ): ScoreOut {
        fun hold(reason: String) = ScoreOut(0, 0.0, "", "", false, "", reason)
        // regime gate (trend_only): CHAOTIC = HOLD เสมอ
        val vp = h1vp[i]
        if (vp > CHAOS_HI || vp < CHAOS_LO) return hold("regime=CHAOTIC (H1 ATR pct ${fmt2(vp)})")
        if (!(h1adx[i] >= ADX_TH && h1tr[i] != 0.0))
            return hold("regime≠TREND (H1 ADX ${fmt2(h1adx[i])} < ${ADX_TH.toInt()} หรือ H1 trend=0)")

        val ctxUp = 0.5 * b(h4tr[i] > 0) + 0.5 * b(h1tr[i] > 0)
        val ctxDn = 0.5 * b(h4tr[i] < 0) + 0.5 * b(h1tr[i] < 0)
        val trUp = 0.5 * b(m30tr[i] > 0) + 0.5 * b(h1tr[i] > 0)
        val trDn = 0.5 * b(m30tr[i] < 0) + 0.5 * b(h1tr[i] < 0)

        // setup: zone tap + displacement ภายใน SETUP_WIN แท่ง
        val structUp = windowAny(stM15.chochUp, i, SETUP_WIN) || (USE_BOS && windowAny(stM15.bosUp, i, SETUP_WIN))
        val structDn = windowAny(stM15.chochDn, i, SETUP_WIN) || (USE_BOS && windowAny(stM15.bosDn, i, SETUP_WIN))
        val zoneFvgUp = windowAny(fvg.inBull, i, SETUP_WIN)
        val zoneEqlUp = windowAny(eqlUp, i, SETUP_WIN)
        val zoneIdmUp = USE_IDM && windowAny(idmUp, i, SETUP_WIN)
        val zoneFvgDn = windowAny(fvg.inBear, i, SETUP_WIN)
        val zoneEqhDn = windowAny(eqhDn, i, SETUP_WIN)
        val zoneIdmDn = USE_IDM && windowAny(idmDn, i, SETUP_WIN)
        val zoneUp = zoneFvgUp || zoneEqlUp || zoneIdmUp
        val zoneDn = zoneFvgDn || zoneEqhDn || zoneIdmDn
        val setupUp = zoneUp && structUp
        val setupDn = zoneDn && structDn
        if (!setupUp && !setupDn)
            return hold("ไม่มี setup (zone tap + displacement ไม่ครบใน $SETUP_WIN แท่ง)")   // HARD RULE: no setup = HOLD

        // confirmation: M5 BOS/CHoCH + body quality ใน bin ของแท่ง M15 นี้
        val agg = m5agg[baseTs[i]]
        val confUp = agg != null && (agg.bosUp || agg.chochUp) && agg.bodyUp >= BODY_MIN
        val confDn = agg != null && (agg.bosDn || agg.chochDn) && agg.bodyDn >= BODY_MIN

        val longS = W_CTX * ctxUp + W_TR * trUp + W_SETUP * b(setupUp) + W_CONF * b(confUp)
        val shortS = W_CTX * ctxDn + W_TR * trDn + W_SETUP * b(setupDn) + W_CONF * b(confDn)

        val goLong = setupUp && longS >= SCORE_TH && longS - shortS >= MARGIN
        val goShort = setupDn && shortS >= SCORE_TH && shortS - longS >= MARGIN
        if (!goLong && !goShort)
            return hold("score ไม่ผ่าน (long=${fmt2(longS)} short=${fmt2(shortS)} ต้อง ≥${SCORE_TH} + margin ${MARGIN})")

        fun zoneDesc(f: Boolean, e: Boolean, idm: Boolean) = buildList {
            if (f) add("FVG tap"); if (e) add("EQL/EQH sweep"); if (idm) add("IDM sweep")
        }.joinToString("+")
        fun structDesc(up: Boolean) = buildList {
            if (windowAny(stM15.chochUp, i, SETUP_WIN) || windowAny(stM15.chochDn, i, SETUP_WIN)) add("CHoCH")
            if (USE_BOS && (windowAny(stM15.bosUp, i, SETUP_WIN) || windowAny(stM15.bosDn, i, SETUP_WIN))) add("BOS")
        }.joinToString("+")

        return if (goLong) ScoreOut(
            1, longS, zoneDesc(zoneFvgUp, zoneEqlUp, zoneIdmUp), structDesc(true),
            confUp, if (confUp) "BOS+body(${fmt2(agg!!.bodyUp)})" else "ไม่มี"
        ) else ScoreOut(
            -1, shortS, zoneDesc(zoneFvgDn, zoneEqhDn, zoneIdmDn), structDesc(false),
            confDn, if (confDn) "BOS+body(${fmt2(agg!!.bodyDn)})" else "ไม่มี"
        )
    }

    // ── SMC primitives (port ตรงจาก unified_smc_lab.py) ─────────────────────

    /** pivot high/low ยืนยันหลัง L แท่ง (causal) — ค่าปรากฏที่แท่งยืนยัน (center+L) */
    internal fun confirmedSwings(c: List<Candle>, L: Int): Pair<DoubleArray, DoubleArray> {
        val n = c.size
        val sh = DoubleArray(n) { Double.NaN }
        val sl = DoubleArray(n) { Double.NaN }
        for (cen in L until n - L) {
            val hc = c[cen].high; val lc = c[cen].low
            var isH = true; var isL = true
            for (j in cen - L..cen + L) {
                if (c[j].high > hc) isH = false
                if (c[j].low < lc) isL = false
            }
            if (isH) sh[cen + L] = hc
            if (isL) sl[cen + L] = lc
        }
        return sh to sl
    }

    internal class Struct(
        val trend: IntArray, val bosUp: BooleanArray, val bosDn: BooleanArray,
        val chochUp: BooleanArray, val chochDn: BooleanArray,
        val sh: DoubleArray, val sl: DoubleArray
    )

    internal fun structure(c: List<Candle>, L: Int): Struct {
        val (sh, sl) = confirmedSwings(c, L)
        val n = c.size
        val trend = IntArray(n)
        val bosUp = BooleanArray(n); val bosDn = BooleanArray(n)
        val chochUp = BooleanArray(n); val chochDn = BooleanArray(n)
        var lastSh = Double.NaN; var lastSl = Double.NaN; var state = 0
        for (i in 0 until n) {
            if (!sh[i].isNaN()) lastSh = sh[i]
            if (!sl[i].isNaN()) lastSl = sl[i]
            val cl = c[i].close
            if (!lastSh.isNaN() && cl > lastSh) {
                if (state == -1) chochUp[i] = true else bosUp[i] = true
                state = 1; lastSh = Double.NaN
            } else if (!lastSl.isNaN() && cl < lastSl) {
                if (state == 1) chochDn[i] = true else bosDn[i] = true
                state = -1; lastSl = Double.NaN
            }
            trend[i] = state
        }
        return Struct(trend, bosUp, bosDn, chochUp, chochDn, sh, sl)
    }

    /** EQH/EQL: swing ยืนยันคู่ก่อนหน้าห่างกัน <= 0.25×ATR → pool; sweep-and-reclaim ที่แท่ง i */
    internal fun equalLevels(c: List<Candle>, L: Int, atr: DoubleArray, tolMult: Double = 0.25): Pair<BooleanArray, BooleanArray> {
        val (sh, sl) = confirmedSwings(c, L)
        val n = c.size
        val eqlUp = BooleanArray(n); val eqhDn = BooleanArray(n)
        // pool: [confirmIdx, level, swept]
        val eqh = mutableListOf<DoubleArray>()
        val eql = mutableListOf<DoubleArray>()
        var prevSh = Double.NaN; var prevSl = Double.NaN
        for (i in 0 until n) {
            val a = atr[i]
            if (!sh[i].isNaN()) {
                if (!prevSh.isNaN() && !a.isNaN() && abs(sh[i] - prevSh) <= tolMult * a) {
                    eqh.add(doubleArrayOf(i.toDouble(), max(sh[i], prevSh), 0.0))
                }
                prevSh = sh[i]
            }
            if (!sl[i].isNaN()) {
                if (!prevSl.isNaN() && !a.isNaN() && abs(sl[i] - prevSl) <= tolMult * a) {
                    eql.add(doubleArrayOf(i.toDouble(), min(sl[i], prevSl), 0.0))
                }
                prevSl = sl[i]
            }
            for (pool in eql) {
                if (pool[2] == 0.0 && pool[0] < i && c[i].low < pool[1] && c[i].close > pool[1]) {
                    pool[2] = 1.0; eqlUp[i] = true
                }
            }
            for (pool in eqh) {
                if (pool[2] == 0.0 && pool[0] < i && c[i].high > pool[1] && c[i].close < pool[1]) {
                    pool[2] = 1.0; eqhDn[i] = true
                }
            }
        }
        return eqlUp to eqhDn
    }

    /** IDM: sweep minor swing (L เล็ก) แล้ว reclaim ด้วย close — level ละครั้ง */
    private fun idmSweeps(c: List<Candle>, L: Int): Pair<BooleanArray, BooleanArray> {
        val (sh, sl) = confirmedSwings(c, L)
        val n = c.size
        val up = BooleanArray(n); val dn = BooleanArray(n)
        var lastSh = Double.NaN; var lastSl = Double.NaN
        var usedSh = Double.NaN; var usedSl = Double.NaN
        for (i in 0 until n) {
            if (!sl[i].isNaN()) lastSl = sl[i]
            if (!sh[i].isNaN()) lastSh = sh[i]
            if (!lastSl.isNaN() && lastSl != usedSl && c[i].low < lastSl && c[i].close > lastSl) {
                up[i] = true; usedSl = lastSl
            }
            if (!lastSh.isNaN() && lastSh != usedSh && c[i].high > lastSh && c[i].close < lastSh) {
                dn[i] = true; usedSh = lastSh
            }
        }
        return up to dn
    }

    internal class FvgTouch(
        val inBull: BooleanArray, val inBear: BooleanArray,
        val bullMid: DoubleArray, val bearMid: DoubleArray
    )

    /** FVG 3 แท่ง — active จนกว่าจะ mitigate; mid ของ zone ใหม่สุดต่อฝั่ง (ใช้ limit entry) */
    internal fun fvgTouch(c: List<Candle>): FvgTouch {
        val n = c.size
        val inBull = BooleanArray(n); val inBear = BooleanArray(n)
        val bullMid = DoubleArray(n) { Double.NaN }; val bearMid = DoubleArray(n) { Double.NaN }
        val bull = mutableListOf<DoubleArray>()  // [bottom, top]
        val bear = mutableListOf<DoubleArray>()
        for (i in 2 until n) {
            if (c[i].low > c[i - 2].high) bull.add(doubleArrayOf(c[i - 2].high, c[i].low))
            if (c[i].high < c[i - 2].low) bear.add(doubleArrayOf(c[i - 2].low, c[i].high))
            bull.removeAll { c[i].low <= it[0] }   // mitigate เมื่อ low ทะลุ bottom
            bear.removeAll { c[i].high >= it[1] }
            inBull[i] = bull.any { it[0] <= c[i].close && c[i].low <= it[1] }
            inBear[i] = bear.any { c[i].close <= it[1] && c[i].high >= it[0] }
            if (bull.isNotEmpty()) bullMid[i] = 0.5 * (bull.last()[0] + bull.last()[1])
            if (bear.isNotEmpty()) bearMid[i] = 0.5 * (bear.last()[0] + bear.last()[1])
        }
        return FvgTouch(inBull, inBear, bullMid, bearMid)
    }

    private class BodyWick(val body: DoubleArray, val upW: DoubleArray, val dnW: DoubleArray)

    private fun bodyWick(c: List<Candle>): BodyWick {
        val n = c.size
        val body = DoubleArray(n); val upW = DoubleArray(n); val dnW = DoubleArray(n)
        for (i in 0 until n) {
            val rng = c[i].high - c[i].low
            if (rng <= 0) continue
            body[i] = abs(c[i].close - c[i].open) / rng
            upW[i] = (c[i].high - max(c[i].close, c[i].open)) / rng
            dnW[i] = (min(c[i].close, c[i].open) - c[i].low) / rng
        }
        return BodyWick(body, upW, dnW)
    }

    // ── indicators (Wilder/EWM alpha=1/n ตรง lab.atr / lab.adx) ─────────────

    internal fun atr(c: List<Candle>, n: Int): DoubleArray {
        val out = DoubleArray(c.size) { Double.NaN }
        if (c.size < n) return out
        var e = 0.0
        for (i in c.indices) {
            val tr = if (i == 0) c[0].high - c[0].low
            else maxOf(c[i].high - c[i].low, abs(c[i].high - c[i - 1].close), abs(c[i].low - c[i - 1].close))
            e = if (i == 0) tr else tr / n + e * (n - 1.0) / n
            if (i >= n - 1) out[i] = e
        }
        return out
    }

    private fun adx(c: List<Candle>, n: Int): DoubleArray {
        val a = atr(c, n)
        val out = DoubleArray(c.size) { Double.NaN }
        var pE = 0.0; var mE = 0.0; var dxE = 0.0; var dxCount = 0
        for (i in 1 until c.size) {
            val up = c[i].high - c[i - 1].high
            val dn = c[i - 1].low - c[i].low
            val plus = if (up > dn && up > 0) up else 0.0
            val minus = if (dn > up && dn > 0) dn else 0.0
            pE = if (i == 1) plus else plus / n + pE * (n - 1.0) / n
            mE = if (i == 1) minus else minus / n + mE * (n - 1.0) / n
            val av = a[i]
            if (!av.isNaN() && av != 0.0) {
                val pdi = 100 * pE / av; val mdi = 100 * mE / av
                if (pdi + mdi != 0.0) {
                    val dx = 100 * abs(pdi - mdi) / (pdi + mdi)
                    dxE = if (dxCount == 0) dx else dx / n + dxE * (n - 1.0) / n
                    dxCount++
                    if (dxCount >= n) out[i] = dxE
                }
            }
        }
        return out
    }

    /** rolling rank percentile (method ~max, NaN ไม่นับ) — default เมื่อข้อมูล < minP */
    private fun rollingRankPct(a: DoubleArray, win: Int, minP: Int, default: Double): DoubleArray {
        val out = DoubleArray(a.size) { default }
        for (i in a.indices) {
            if (a[i].isNaN()) continue
            val from = max(0, i - win + 1)
            var cnt = 0; var le = 0
            for (j in from..i) {
                val v = a[j]
                if (!v.isNaN()) { cnt++; if (v <= a[i]) le++ }
            }
            if (cnt >= minP) out[i] = le.toDouble() / cnt
        }
        return out
    }

    // ── MTF assembly ────────────────────────────────────────────────────────

    /** resample ด้วย bin เวลา (epoch ms, ชนเที่ยงคืน UTC) */
    internal fun resample(c: List<Candle>, minutes: Int): List<Candle> {
        if (c.isEmpty()) return emptyList()
        val bucketMs = minutes * 60_000L
        val out = mutableListOf<Candle>()
        var curBucket = Long.MIN_VALUE
        var o = 0.0; var h = 0.0; var l = 0.0; var cl = 0.0; var v = 0.0; var t0 = 0L
        for (bar in c) {
            val b = bar.timestamp / bucketMs * bucketMs
            if (b != curBucket) {
                if (curBucket != Long.MIN_VALUE) out += Candle(o, h, l, cl, v, t0)
                curBucket = b; o = bar.open; h = bar.high; l = bar.low; cl = bar.close; v = bar.volume; t0 = b
            } else {
                h = max(h, bar.high); l = min(l, bar.low); cl = bar.close; v += bar.volume
            }
        }
        if (curBucket != Long.MIN_VALUE) out += Candle(o, h, l, cl, v, t0)
        return out
    }

    /** as-of join: base ที่เวลา t ใช้แท่ง HTF ที่ open+duration <= t เท่านั้น (ffill NaN; leading = default) */
    private fun mapHtf(baseTs: LongArray, htf: List<Candle>, tfMin: Int, values: DoubleArray, default: Double): DoubleArray {
        val dur = tfMin * 60_000L
        // ffill NaN ในค่า HTF (ตรง dropna+backward ของ pandas)
        val ff = DoubleArray(values.size) { Double.NaN }
        var last = Double.NaN
        for (k in values.indices) { if (!values[k].isNaN()) last = values[k]; ff[k] = last }
        val out = DoubleArray(baseTs.size) { default }
        var p = 0
        for (bIdx in baseTs.indices) {
            val t = baseTs[bIdx]
            while (p < htf.size && htf[p].timestamp + dur <= t) p++
            if (p > 0) {
                val v = ff[p - 1]
                out[bIdx] = if (v.isNaN()) default else v
            }
        }
        return out
    }

    private class M5Agg {
        var bosUp = false; var bosDn = false; var chochUp = false; var chochDn = false
        var bodyUp = 0.0; var bodyDn = 0.0
    }

    /** aggregate M5 เข้า bin M15 (key = M15 open time): max ของ structure events + body ตามทิศ */
    private fun aggregateM5(m5: List<Candle>, stM5: Struct): MutableMap<Long, M5Agg> {
        val bw = bodyWick(m5)
        val binMs = 15 * 60_000L
        val map = HashMap<Long, M5Agg>()
        for (k in m5.indices) {
            val bin = m5[k].timestamp / binMs * binMs
            val agg = map.getOrPut(bin) { M5Agg() }
            if (stM5.bosUp[k]) agg.bosUp = true
            if (stM5.bosDn[k]) agg.bosDn = true
            if (stM5.chochUp[k]) agg.chochUp = true
            if (stM5.chochDn[k]) agg.chochDn = true
            val upBar = m5[k].close > m5[k].open
            if (upBar) agg.bodyUp = max(agg.bodyUp, bw.body[k])
            else agg.bodyDn = max(agg.bodyDn, bw.body[k])
        }
        return map
    }

    /** merge LTF (เช่น M1) เข้า agg map ของ bin M15 — OR ของ events, max ของ body (additive layer) */
    private fun aggregateLtfInto(ltf: List<Candle>, stLtf: Struct, map: MutableMap<Long, M5Agg>) {
        val bw = bodyWick(ltf)
        val binMs = 15 * 60_000L
        for (k in ltf.indices) {
            val bin = ltf[k].timestamp / binMs * binMs
            val agg = map.getOrPut(bin) { M5Agg() }
            if (stLtf.bosUp[k]) agg.bosUp = true
            if (stLtf.bosDn[k]) agg.bosDn = true
            if (stLtf.chochUp[k]) agg.chochUp = true
            if (stLtf.chochDn[k]) agg.chochDn = true
            val upBar = ltf[k].close > ltf[k].open
            if (upBar) agg.bodyUp = max(agg.bodyUp, bw.body[k])
            else agg.bodyDn = max(agg.bodyDn, bw.body[k])
        }
    }

    // ── misc helpers ────────────────────────────────────────────────────────

    private fun windowAny(a: BooleanArray, i: Int, w: Int): Boolean {
        val from = max(0, i - w + 1)
        for (j in from..i) if (a[j]) return true
        return false
    }

    private fun ffill(a: DoubleArray): DoubleArray {
        val out = DoubleArray(a.size) { Double.NaN }
        var last = Double.NaN
        for (i in a.indices) { if (!a[i].isNaN()) last = a[i]; out[i] = last }
        return out
    }

    private fun IntArray.toDoubleArray(): DoubleArray = DoubleArray(size) { this[it].toDouble() }
    private fun b(cond: Boolean): Double = if (cond) 1.0 else 0.0
    private fun fmt2(v: Double) = "%.2f".format(v)
}

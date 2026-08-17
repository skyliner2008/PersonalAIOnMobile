package com.example.personalaibot.automation.backtest

import com.example.personalaibot.automation.SignalMarkerProvider
import com.example.personalaibot.logDebug
import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.round

/**
 * AdaptiveOptimizer — จูน params แบบ "มีความจำ" (Adaptive Optimize)
 *
 * ต่างจาก grid search ล้วน:
 *  1. อ่านประวัติการจูน (OptimizationTrial) ของ symbol/tf/kind นั้น — เรียนรู้ว่า
 *     "ปรับ SL/TP ทิศไหนแล้วผลดีขึ้น/แย่ลง" (direction effect จาก delta_vs_baseline)
 *  2. สร้าง candidates = grid มาตรฐาน + mutation รอบ params ปัจจุบัน + mutation รอบ
 *     top-3 params ในประวัติ (ไม่จำกัด ±30% drift แบบ evolution — ขยายได้ถึง hard bound
 *     [SL 0.3–6×, TP 0.5–10×] เพราะทุก candidate ถูกวัดผลจริงด้วย engine ก่อนเสมอ)
 *  3. วัดผลทุก candidate ด้วย BacktestEngine บนข้อมูลเต็ม — เลือกตัวดีสุด
 *     (ใช้ direction effect จากประวัติเป็น tie-breaker เล็กๆ ไม่ใช่ตัวตัดสินหลัก)
 *  4. บันทึกผลการลองกลับเข้าความจำ (baseline + top-5) → รอบหน้าเรียนรู้ต่อ
 *
 * การ auto-apply (ใช้ค่าใหม่เมื่อดีกว่า) เป็นหน้าที่ของ caller ที่มี overfit grade ประกอบ
 */
object AdaptiveOptimizer {

    data class AdaptiveResult(
        val kind: String,
        val baselineParams: TpSlParams,
        val baselineScore: Double,
        val bestParams: TpSlParams,
        val bestScore: Double,
        val bestTrades: Int,
        val bestWinRate: Double,
        val bestProfitFactor: Double,
        val bestSharpe: Double,
        val bestExpectancyR: Double,
        val bestMaxDdPct: Double,
        val improved: Boolean,          // bestScore > baselineScore × 1.02 และไม้พอ
        val candidatesTried: Int,
        val directionInsight: String,   // สรุปสิ่งที่เรียนรู้จากประวัติ (ภาษาไทย)
        val learnedFromTrials: Int
    )

    // hard sanity bounds — กันค่าที่ไร้ความหมาย แต่กว้างกว่า drift clamp ของ evolution มาก
    private const val SL_MIN = 0.3; private const val SL_MAX = 6.0
    private const val TP_MIN = 0.5; private const val TP_MAX = 10.0

    fun run(
        kind: String,
        symbol: String,
        interval: String,
        candles: List<Candle>,
        markers: List<SignalMarkerProvider.SignalMarker>,
        engine: BacktestEngine,
        kindOf: (String) -> String,
        strategyName: (String) -> String,
        baselineParams: TpSlParams,
        config: BacktestConfig = BacktestConfig(),
        minTrades: Int = 5
    ): AdaptiveResult {
        val mgr = runCatching { com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager() }.getOrNull()
        val history = mgr?.getOptimizationTrials(symbol, interval, kind, 100) ?: emptyList()

        // ── 1) เรียนรู้ direction effect จากประวัติ ──
        // ถามว่า "trial ที่ SL กว้างกว่า default / แคบกว่า default ผลดีขึ้นหรือแย่ลง (เทียบ baseline ของรอบนั้น)"
        val defaults = TpSlParams.defaultsFor(kind)
        data class Acc(var sum: Double = 0.0, var n: Int = 0) { val avg get() = if (n > 0) sum / n else 0.0 }
        val slWider = Acc(); val slTighter = Acc(); val tpWider = Acc(); val tpTighter = Acc()
        var learnedN = 0
        for (t in history) {
            val delta = t.delta_vs_baseline ?: continue
            learnedN++
            if (t.sl_mult > defaults.slMult * 1.05) { slWider.sum += delta; slWider.n++ }
            else if (t.sl_mult < defaults.slMult * 0.95) { slTighter.sum += delta; slTighter.n++ }
            if (t.tp_mult > defaults.tpMult * 1.05) { tpWider.sum += delta; tpWider.n++ }
            else if (t.tp_mult < defaults.tpMult * 0.95) { tpTighter.sum += delta; tpTighter.n++ }
        }

        fun insightOf(): String {
            val parts = mutableListOf<String>()
            fun th(dirW: Acc, dirT: Acc, wText: String, tText: String) {
                if (dirW.n >= 2 && dirT.n >= 2) {
                    if (dirW.avg > 0.05 && dirW.avg > dirT.avg + 0.05) parts += "$wText มักดีกว่า (avg ${"%+.2f".format(dirW.avg)}, ${dirW.n} ครั้ง)"
                    else if (dirT.avg > 0.05 && dirT.avg > dirW.avg + 0.05) parts += "$tText มักดีกว่า (avg ${"%+.2f".format(dirT.avg)}, ${dirT.n} ครั้ง)"
                    if (dirW.avg < -0.05 && dirT.avg < -0.05) parts += "ปรับ $wText/$tText มักแย่ลงทั้งคู่ — ค่าเดิมใกล้จุดดีแล้ว"
                }
            }
            th(slWider, slTighter, "SL กว้าง", "SL แคบ")
            th(tpWider, tpTighter, "TP ไกล", "TP ใกล้")
            return if (parts.isEmpty()) "ยังสรุปทิศทางไม่ได้ (ต้องมี trial ทั้งสองทิศ ≥2 ครั้งต่อทิศ — จะชัดขึ้นเมื่อมีประวัติเพิ่ม)" else parts.joinToString(" ; ")
        }

        // ── 2) สร้าง candidates ──
        fun clampP(p: TpSlParams) = TpSlParams(
            p.slMult.coerceIn(SL_MIN, SL_MAX), p.tpMult.coerceIn(TP_MIN, TP_MAX)
        )
        val candSet = LinkedHashMap<String, TpSlParams>()
        fun add(p: TpSlParams) {
            // round 2 ตำแหน่งที่นี่ด้วย — mutation (×0.8/1.1/1.25) สร้าง float รก เช่น 2.4000000000000004
            val c = clampP(TpSlParams(TpSlParams.round2(p.slMult), TpSlParams.round2(p.tpMult)))
            candSet.putIfAbsent("${round(c.slMult * 1000)}|${round(c.tpMult * 1000)}", c)
        }
        TpSlParams.grid().forEach { add(it) }
        // mutation รอบ baseline (ปัจจุบัน)
        for (sm in listOf(0.8, 0.9, 1.1, 1.25)) for (tm in listOf(0.8, 0.9, 1.1, 1.25)) {
            add(TpSlParams(baselineParams.slMult * sm, baselineParams.tpMult * tm))
        }
        // mutation รอบ top-3 ในประวัติ (เรียนจากจุดที่เคยดี)
        history.sortedByDescending { it.score }.take(3).forEach { t ->
            for (sm in listOf(0.9, 1.1)) for (tm in listOf(0.9, 1.1)) {
                add(TpSlParams(t.sl_mult * sm, t.tp_mult * tm))
            }
        }
        add(baselineParams)

        // ── 3) วัดผลจริงทุก candidate ──
        fun eval(p: TpSlParams): Pair<Double, BacktestResult?> {
            val r = runCatching {
                engine.run(
                    symbol = "", interval = "", source = "",
                    candles = candles, markers = markers, kindFilter = setOf(kind),
                    kindOf = kindOf, strategyName = strategyName,
                    tpSl = { k, s, c, i, a14, a6 -> parameterizedTpSl(k, s, c, i, a14, a6, p) },
                    config = config
                )
            }.getOrNull() ?: return -999.0 to null
            return ParamOptimizer.score(r, minTrades) to r
        }

        val (baselineScore, baselineRes) = eval(baselineParams)

        data class Cand(val p: TpSlParams, val score: Double, val adj: Double, val r: BacktestResult?)
        val evaluated = candSet.values.map { p ->
            val (score, r) = eval(p)
            // tie-breaker เล็กๆ จากประวัติ: ทิศที่เคยดีได้แต้มนิดหน่อย ทิศที่เคยแย่โดนหักนิดหน่อย (ไม่เกิน ±0.1 เท่าของ effect เฉลี่ย)
            val slEff = if (p.slMult > defaults.slMult * 1.05) slWider.avg else if (p.slMult < defaults.slMult * 0.95) slTighter.avg else 0.0
            val tpEff = if (p.tpMult > defaults.tpMult * 1.05) tpWider.avg else if (p.tpMult < defaults.tpMult * 0.95) tpTighter.avg else 0.0
            val adj = score + 0.1 * (slEff + tpEff).coerceIn(-1.0, 1.0)
            Cand(p, score, adj, r)
        }
        val best = evaluated.filter { it.score > -999.0 }.maxByOrNull { it.adj }

        // ── 4) บันทึกกลับเข้าความจำ (baseline + top-5 ที่วัดได้) ──
        if (mgr != null) {
            mgr.saveOptimizationTrial(
                symbol, interval, kind, baselineParams.slMult, baselineParams.tpMult,
                baselineScore, baselineRes?.expectancyR, baselineRes?.profitFactor,
                baselineRes?.totalTrades, 0.0, applied = false, source = "baseline"
            )
            evaluated.filter { it.score > -999.0 }.sortedByDescending { it.score }.take(5).forEach { c ->
                mgr.saveOptimizationTrial(
                    symbol, interval, kind, c.p.slMult, c.p.tpMult, c.score,
                    c.r?.expectancyR, c.r?.profitFactor, c.r?.totalTrades,
                    c.score - baselineScore, applied = false, source = "adaptive"
                )
            }
            mgr.pruneOptimizationTrials(symbol, interval, kind)
        }

        logDebug("AdaptiveOptimizer", "$symbol/$interval/$kind: ${evaluated.size} candidates, baseline=${"%.3f".format(baselineScore)} best=${"%.3f".format(best?.score ?: -999.0)} learned=$learnedN")

        val br = best?.r
        return AdaptiveResult(
            kind = kind,
            baselineParams = baselineParams, baselineScore = baselineScore,
            bestParams = best?.p ?: baselineParams, bestScore = best?.score ?: baselineScore,
            bestTrades = br?.totalTrades ?: 0, bestWinRate = br?.winRate ?: 0.0,
            bestProfitFactor = br?.profitFactor ?: 0.0, bestSharpe = br?.sharpe ?: 0.0,
            bestExpectancyR = br?.expectancyR ?: 0.0, bestMaxDdPct = br?.maxDrawdownPct ?: 0.0,
            improved = best != null && baselineScore > -999.0 && best.score > baselineScore * 1.02 && abs(best.score - baselineScore) > 1e-9,
            candidatesTried = evaluated.size,
            directionInsight = insightOf(),
            learnedFromTrials = learnedN
        )
    }
}

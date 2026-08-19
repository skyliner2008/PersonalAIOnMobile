package com.example.personalaibot.automation.backtest

import com.example.personalaibot.automation.SignalMarkerProvider
import com.example.personalaibot.logDebug
import com.example.personalaibot.tools.trading.Candle

/**
 * BacktestEvolution — "วิวัฒนาการ = backtest เป็นช่วง + สะท้อนผล + ปรับ params"
 * (แนวคิดจาก moss-trade-bot knowledge/evolution_guide.md)
 *
 * กติกา:
 *  - แบ่งข้อมูลเป็น N ช่วงต่อเนื่อง → ช่วงละรอบ: รัน backtest ด้วย params ปัจจุบัน → สะท้อนผล → ปรับ params
 *  - สะท้อนผลด้วย AI (Gemini) เป็นหลัก, ถ้า AI ล้มเหลวใช้กฎ heuristic (7 หลักการของ moss)
 *  - ปรับต่อรอบไม่เกิน ±10% (clampStep) และห้ามหนีค่าเริ่มต้นเกิน ±30% (clampDrift)
 *  - ห้ามนิ่งเกิน 3 รอบติด (บังคับปรับเล็กน้อย ≥2%)
 */
class BacktestEvolution {

    data class RoundResult(
        val round: Int,
        val bars: Int,
        val params: TpSlParams,
        val trades: Int,
        val wins: Int,
        val losses: Int,
        val timeouts: Int,
        val winRate: Double,
        val avgR: Double,
        val totalR: Double,
        val profitFactor: Double,
        val slExits: Int,
        val reflection: String   // เหตุผลจาก AI/กฎ ที่ใช้ปรับรอบถัดไป
    )

    /** หนึ่งการปรับ params → ผลที่ตามมา (บันทึกลง OptimizationTrial เพื่อเป็นความจำข้ามรอบ/ข้ามรัน) */
    data class ReflectionTrial(
        val round: Int,
        val fromSl: Double, val fromTp: Double,
        val toSl: Double, val toTp: Double,
        val totalR: Double, val avgR: Double, val profitFactor: Double,
        val trades: Int, val deltaVsBaseline: Double, val note: String,
        /**
         * causal delta ของ mutation รอบก่อน: params ปัจจุบัน vs params รอบก่อน บน segment เดียวกัน
         * (แก้ปัญหาเดิมที่เทียบกับ initial ทุกรอบ → memory เรียนรู้ผิดทิศ เช่น B ดีกว่า A แต่แย่กว่า default ถูกบันทึกว่า "แย่")
         * null = รอบแรก (ยังไม่มี mutation ก่อนหน้า)
         */
        val deltaVsPrev: Double? = null
    )

    data class EvolutionResult(
        val kind: String,
        val segments: Int,
        val initialParams: TpSlParams,
        val finalParams: TpSlParams,
        val rounds: List<RoundResult>,
        val baselineTotalR: Double,   // ผลรวมถ้าใช้ params เดิมตลอด
        val evolvedTotalR: Double,    // ผลรวมของ params ที่วิวัฒน์
        val usedAiReflection: Boolean,
        val trials: List<ReflectionTrial> = emptyList()  // ความจำการปรับแต่งของ run นี้
    )

    suspend fun evolve(
        kind: String,
        candles: List<Candle>,
        markers: List<SignalMarkerProvider.SignalMarker>,
        engine: BacktestEngine,
        kindOf: (String) -> String,
        strategyName: (String) -> String,
        segments: Int = 8,
        config: BacktestConfig = BacktestConfig(),
        initialOverride: TpSlParams? = null,
        memoryLines: List<String> = emptyList(),
        trainingFraction: Double = 0.70
    ): EvolutionResult {
        val n = candles.size
        val initial = initialOverride ?: TpSlParams.defaultsFor(kind)
        val trainBars = (n * trainingFraction.coerceIn(0.60, 0.80)).toInt().coerceAtLeast(800)
            .coerceAtMost(n)
        val segSize = (trainBars / segments.coerceAtLeast(1)).coerceAtLeast(100)
        val actualSegments = (trainBars / segSize).coerceAtLeast(1).coerceAtMost(segments)

        var params = initial
        val rounds = mutableListOf<RoundResult>()
        var usedAi = false
        var noChangeStreak = 0
        var baselineTotalR = 0.0
        var evolvedTotalR = 0.0
        val trials = mutableListOf<ReflectionTrial>()
        var prevParams: TpSlParams? = null
        // Keep every accepted point. The last point of a walk is NOT necessarily the
        // best point; returning the last mutation was a major source of regressions.
        val visitedParams = linkedSetOf(initial)
        var championParams = initial
        var championFitness = Double.NEGATIVE_INFINITY
        // Segment result cache: params เดิมมักถูกประเมินซ้ำหลายรอบ (rolling window โตขึ้นเรื่อยๆ)
        // และ candidate fan ที่กว้างขึ้นก็แชร์ cache กันได้ — ต้นทุนหลักของ evolve คือ engine.run
        // ผล deterministic ต่อ (params, segment) จึง cache ได้ปลอดภัย ไม่กระทบความถูกต้อง
        val segCache = HashMap<Pair<TpSlParams, Int>, BacktestResult?>()

        fun runSegment(segment: Int, p: TpSlParams): BacktestResult? {
            val start = segment * segSize
            val end = if (segment == actualSegments - 1) trainBars else ((segment + 1) * segSize).coerceAtMost(trainBars)
            if (start >= end || end > candles.size) return null
            val segCandles = candles.subList(0, end)
            val segMarkers = markers.filter { m ->
                m.time >= segCandles[start].timestamp && m.time <= segCandles[end - 1].timestamp
            }
            return runCatching {
                engine.run(
                    symbol = "", interval = "", source = "",
                    candles = segCandles, markers = segMarkers, kindFilter = setOf(kind),
                    kindOf = kindOf, strategyName = strategyName,
                    tpSl = { k, sd, c, i, a14, a6 -> parameterizedTpSl(k, sd, c, i, a14, a6, p) },
                    config = config,
                    startIndex = start
                )
            }.getOrNull()
        }

        fun runSeg(segment: Int, p: TpSlParams): BacktestResult? =
            segCache.getOrPut(p to segment) { runSegment(segment, p) }

        fun fitness(results: List<BacktestResult>): Double {
            if (results.isEmpty()) return Double.NEGATIVE_INFINITY
            val valid = results.filter { it.totalTrades > 0 }
            if (valid.isEmpty()) return Double.NEGATIVE_INFINITY
            val avgR = valid.map { it.expectancyR }.average()
            val avgPf = valid.map { it.profitFactor.coerceIn(0.0, 5.0) }.average()
            val avgDd = valid.map { it.maxDrawdownPct.coerceAtLeast(0.0) }.average()
            val tradePenalty = valid.sumOf { it.totalTrades }.let { if (it < 10) 0.15 else 0.0 }
            // Expectancy เป็นแกนหลัก; PF/Drawdown ใช้กันผลลัพธ์ที่ดีเพราะไม้ไม่กี่ไม้หรือ DD สูงเกินไป
            return avgR + 0.08 * (avgPf - 1.0) - 0.008 * avgDd - tradePenalty
        }

        for (s in 0 until actualSegments) {
            val baseline = runSeg(s, initial)
            val evolved = runSeg(s, params)
            if (baseline != null) baselineTotalR += baseline.trades.sumOf { it.pnlR }
            if (evolved != null) evolvedTotalR += evolved.trades.sumOf { it.pnlR }
            val r = evolved ?: continue

            val slExits = r.trades.count { it.exitReason == "SL" }
            val roundR = r.trades.sumOf { it.pnlR }
            val prevRun = prevParams?.let { runSeg(s, it) }
            val prevRoundR = prevRun?.trades?.sumOf { it.pnlR }
            val deltaVsPrev = if (prevRoundR != null) roundR - prevRoundR else null

            val (reflected, note, fromAi) = reflect(
                kind, s + 1, params, initial, r, slExits, noChangeStreak, memoryLines
            )
            if (fromAi) usedAi = true

            // Candidate search: ไม่พึ่งการเดาเพียงทิศทางเดียวของ heuristic.
            // fan กว้างขึ้น (±5% / ±10% / joint moves) เพื่อกระโดดข้าม local plateau —
            // ทุก candidate ยังผ่าน clampStep ±10%/รอบ และ clampDrift ±30% จาก initial เหมือนเดิม
            // และต้องชนะ rolling validation เท่านั้นจึงถูกรับ (จำนวน candidate มากขึ้น ≠ เสี่ยงขึ้น)
            val rawCandidates = listOf(
                reflected,
                params.copy(slMult = params.slMult * 0.95),
                params.copy(slMult = params.slMult * 1.05),
                params.copy(tpMult = params.tpMult * 0.95),
                params.copy(tpMult = params.tpMult * 1.05),
                params.copy(slMult = params.slMult * 0.90),
                params.copy(slMult = params.slMult * 1.10),
                params.copy(tpMult = params.tpMult * 0.90),
                params.copy(tpMult = params.tpMult * 1.10),
                params.copy(slMult = params.slMult * 0.95, tpMult = params.tpMult * 1.05),
                params.copy(slMult = params.slMult * 1.05, tpMult = params.tpMult * 0.95)
            )
            val candidates = rawCandidates.map {
                TpSlParams.enforceRrFloor(
                    kind,
                    TpSlParams.clampDrift(initial, TpSlParams.clampStep(params, it))
                )
            }.distinct()

            val windowStart = 0
            fun candidateFitness(p: TpSlParams): Double {
                val rs = (windowStart..s).mapNotNull { runSeg(it, p) }
                return fitness(rs)
            }

            val currentFitness = candidateFitness(params)
            val bestCandidate = candidates
                .map { it to candidateFitness(it) }
                .maxByOrNull { it.second }
            var accepted = params
            var finalNote = note
            if (bestCandidate != null && bestCandidate.second > currentFitness + 0.002) {
                accepted = bestCandidate.first
                finalNote = "$note → ✅ รับ candidate ที่ fitness ดีขึ้น ${"%.4f".format(currentFitness)} → ${"%.4f".format(bestCandidate.second)} (rolling ${windowStart + 1}..${s + 1})"
                logDebug("BacktestEvolution", "Round ${s + 1} $kind: ACCEPT ${params.slMult}/${params.tpMult} → ${accepted.slMult}/${accepted.tpMult} fitness ${"%.4f".format(currentFitness)} → ${"%.4f".format(bestCandidate.second)}")
            } else if (candidates.any { it != params }) {
                finalNote = "$note → 🚫 reject candidates: ไม่มีตัวไหนชนะ rolling validation (fitness ${"%.4f".format(currentFitness)})"
                logDebug("BacktestEvolution", "Round ${s + 1} $kind: REJECT candidates (best did not beat rolling fitness)")
            }

            noChangeStreak = if (accepted == params && r.totalTrades > 0) noChangeStreak + 1 else 0
            visitedParams += accepted
            // Track a rolling champion during the walk, but do not trust it blindly:
            // all visited points are re-ranked on the complete training set below.
            val acceptedFitness = candidateFitness(accepted)
            if (acceptedFitness > championFitness) {
                championFitness = acceptedFitness
                championParams = accepted
            }
            val baseR = baseline?.trades?.sumOf { it.pnlR }
            trials += ReflectionTrial(
                round = s + 1,
                fromSl = params.slMult, fromTp = params.tpMult,
                toSl = accepted.slMult, toTp = accepted.tpMult,
                totalR = roundR, avgR = r.expectancyR, profitFactor = r.profitFactor,
                trades = r.totalTrades,
                deltaVsBaseline = if (baseR != null) roundR - baseR else 0.0,
                note = finalNote,
                deltaVsPrev = deltaVsPrev
            )

            rounds += RoundResult(
                round = s + 1,
                bars = (if (s == actualSegments - 1) trainBars else ((s + 1) * segSize)) - (s * segSize),
                params = params,
                trades = r.totalTrades,
                wins = r.wins, losses = r.losses, timeouts = r.timeouts,
                winRate = r.winRate, avgR = r.expectancyR,
                totalR = roundR, profitFactor = r.profitFactor,
                slExits = slExits, reflection = finalNote
            )
            prevParams = params
            params = accepted
        }

        // Final selection is a stable training-set championship, not simply the last
        // accepted mutation. This prevents a good parameter found in round 3 from being
        // overwritten by a worse round-8 mutation.
        fun trainingFitness(p: TpSlParams): Double = (0 until actualSegments)
            .mapNotNull { runSeg(it, p) }
            .let(::fitness)

        var bestTrainFitness = trainingFitness(initial)
        championParams = initial
        for (p in visitedParams) {
            val f = trainingFitness(p)
            if (f > bestTrainFitness + 0.0005) {
                bestTrainFitness = f
                championParams = p
            }
        }
        evolvedTotalR = (0 until actualSegments).sumOf { segment ->
            runSeg(segment, championParams)?.trades?.sumOf { it.pnlR } ?: 0.0
        }
        logDebug(
            "BacktestEvolution",
            "FINAL CHAMPION $kind: ${championParams.slMult}/${championParams.tpMult} " +
                "trainFitness=${"%.4f".format(bestTrainFitness)} visited=${visitedParams.size}"
        )

        return EvolutionResult(
            kind = kind, segments = rounds.size,
            initialParams = initial, finalParams = championParams,
            rounds = rounds,
            baselineTotalR = baselineTotalR, evolvedTotalR = evolvedTotalR,
            usedAiReflection = usedAi,
            trials = trials
        )
    }

    // ─── Reflection: AI ก่อน ล้มเหลวค่อยใช้กฎ ───

    private data class Reflection(val params: TpSlParams, val note: String, val fromAi: Boolean)

    private suspend fun reflect(
        kind: String, round: Int, current: TpSlParams, initial: TpSlParams,
        r: BacktestResult, slExits: Int, noChangeStreak: Int,
        memoryLines: List<String> = emptyList()
    ): Reflection {
        // IMPORTANT: evolution เป็น inner-loop optimizer ไม่ควรเรียก Gemini Chat ทุก round.
        // เดิม 8 rounds × หลาย strategy × หลาย concurrent TF ทำให้ free-tier Chat ถูกเผา
        // ทั้งที่ Live session เป็นผู้ถือ context และ tool call อยู่แล้ว.
        // ใช้ deterministic local reflection เป็น default: ไม่มี network, ไม่มี 429,
        // latency ต่ำ และผล reproduce ได้จากข้อมูลชุดเดียวกัน.
        if (r.totalTrades < 5) {
            return Reflection(
                current,
                "ไม้น้อยเกิน (${r.totalTrades} < 5) — noise ประเมินไม่ได้ → คง params (local)",
                false
            )
        }

        val next = localAdaptiveAdjust(current, r, slExits, noChangeStreak)
        val note = localAdaptiveNote(current, next, r, slExits, noChangeStreak)
        return Reflection(next, note, false)
    }

    /**
     * Local optimizer policy สำหรับ inner loop ของ evolution.
     * ไม่เรียก network/LLM: proposal ถูกพิสูจน์ซ้ำด้วย hill-climbing ใน evolve()
     * ก่อนรับจริง ดังนั้นการปรับ params จะไม่ทำให้ strategy drift เพียงเพราะ LLM ตีความ noise.
     */
    private fun localAdaptiveAdjust(
        current: TpSlParams,
        r: BacktestResult,
        slExits: Int,
        noChangeStreak: Int
    ): TpSlParams {
        if (r.totalTrades <= 0) return current

        val decided = (r.wins + r.losses).coerceAtLeast(1)
        val slRate = slExits.toDouble() / decided.toDouble()
        val timeoutRate = r.timeouts.toDouble() / r.totalTrades.toDouble()
        val rr = if (current.slMult > 0.0) current.tpMult / current.slMult else 0.0

        // ให้ priority กับ risk structure ก่อน: RR ต่ำกว่า floor ให้แก้ด้วย TP ขึ้น
        // หรือ SL ลงเล็กน้อย แทนการปล่อยให้ evolution ไหลไปทาง reward/risk ที่เสียเปรียบ.
        if (rr < 1.20) {
            return current.copy(tpMult = current.tpMult * 1.05)
        }

        return when {
            // SL ชนหนักจริง → ขยาย SL เล็กน้อย แต่ไม่เกิน step ที่ clampDrift บังคับ
            slRate >= 0.70 -> current.copy(slMult = current.slMult * 1.05)
            // timeout สูง → TP ไกลเกินไปสำหรับ regime นี้ ลดเล็กน้อย
            timeoutRate >= 0.50 -> current.copy(tpMult = current.tpMult * 0.95)
            // win-rate ดีแต่ expectancy/PF ยังไม่ดี → reward ยังไม่พอ
            r.winRate >= 0.50 && r.profitFactor < 1.0 -> current.copy(tpMult = current.tpMult * 1.05)
            // PF แข็งแรงแต่มี SL มากพอสมควร → อย่าขยายแรง; ทดสอบลด SL เล็กน้อย
            r.profitFactor >= 1.20 && slRate >= 0.45 -> current.copy(slMult = current.slMult * 0.97)
            // ไม่มี signal direction ชัดเจนหลายรอบ → mutation เล็กมากเพื่อค้นหา local optimum
            noChangeStreak >= 3 -> current.copy(tpMult = current.tpMult * 1.02)
            else -> current
        }
    }

    private fun localAdaptiveNote(
        current: TpSlParams,
        next: TpSlParams,
        r: BacktestResult,
        slExits: Int,
        noChangeStreak: Int
    ): String {
        if (next == current) return "local optimizer: โครงสร้างสุขภาพดี → คง params"
        val rr = if (current.slMult > 0.0) current.tpMult / current.slMult else 0.0
        val nextRr = if (next.slMult > 0.0) next.tpMult / next.slMult else 0.0
        return when {
            rr < 1.20 -> "local optimizer: RR ${"%.2f".format(rr)} ต่ำกว่า 1.20 → เพิ่ม TP 5%"
            slExits.toDouble() / (r.wins + r.losses).coerceAtLeast(1) >= 0.70 -> "local optimizer: SL-exit สูง → ขยาย SL 5%"
            r.timeouts.toDouble() / r.totalTrades >= 0.50 -> "local optimizer: timeout สูง → ลด TP 5%"
            r.winRate >= 0.50 && r.profitFactor < 1.0 -> "local optimizer: win-rate ดีแต่ PF ต่ำ → เพิ่ม TP 5%"
            r.profitFactor >= 1.20 -> "local optimizer: PF แข็งแรงแต่ SL สูง → ลด SL เล็กน้อย"
            noChangeStreak >= 3 -> "local optimizer: plateau → สำรวจ TP +2%"
            else -> "local optimizer: ปรับ ${current.slMult}/${current.tpMult} → ${next.slMult}/${next.tpMult}"
        } + " (RR ใหม่ ${"%.2f".format(nextRr)})"
    }

    private data class Parsed(val params: TpSlParams, val note: String)

    private fun parseReflectionJson(resp: String): Parsed? {
        if (resp.isBlank() || resp.startsWith("⚠️")) return null
        val sl = Regex(""""sl_mult"\s*:\s*([0-9.]+)""").find(resp)?.groupValues?.get(1)?.toDoubleOrNull()
        val tp = Regex(""""tp_mult"\s*:\s*([0-9.]+)""").find(resp)?.groupValues?.get(1)?.toDoubleOrNull()
        val note = Regex(""""note"\s*:\s*"([^"]*)"""").find(resp)?.groupValues?.get(1) ?: "AI ปรับ params"
        if (sl == null || tp == null || sl <= 0 || tp <= 0 || sl > 10 || tp > 15) return null
        return Parsed(TpSlParams(sl, tp), note)
    }

    // ─── กฎ heuristic (7 หลักการ moss — ใช้เมื่อ AI ไม่พร้อม) ───

    private fun ruleBasedAdjust(current: TpSlParams, r: BacktestResult, slExits: Int, noChangeStreak: Int): TpSlParams {
        if (r.totalTrades == 0) return current // 0 ไม้ = ไม่มีข้อมูลประเมิน อย่าขยับ params
        val decided = (r.wins + r.losses).coerceAtLeast(1)
        return when {
            // SL ชน ≥70% ของไม้ที่ตัดสิน → SL แคบเกิน ขยาย 10%
            slExits * 10 >= decided * 7 -> current.copy(slMult = current.slMult * 1.10)
            // ค้างเกินครึ่ง → TP ไกลเกิน หด 5%
            r.timeouts * 2 > r.totalTrades && r.totalTrades > 0 -> current.copy(tpMult = current.tpMult * 0.95)
            // ชนะเยอะแต่ PF < 1 → TP ใกล้เกิน ขยาย 10%
            r.winRate >= 0.5 && r.profitFactor < 1.0 -> current.copy(tpMult = current.tpMult * 1.10)
            // นิ่งเกิน 3 รอบ → บังคับปรับเล็กน้อย 2% รักษา "ความมีชีวิต" ของกลยุทธ์
            noChangeStreak >= 3 -> current.copy(slMult = current.slMult * 1.02)
            else -> current // โครงสร้างสุขภาพดี ไม่แก้ (หลักการ 1: อย่า overreact)
        }
    }

    private fun ruleNote(r: BacktestResult, slExits: Int, noChangeStreak: Int): String {
        if (r.totalTrades == 0) return "ไม่มีไม้ในช่วงนี้ ข้อมูลไม่พอประเมิน → คง params (กฎ)"
        val decided = (r.wins + r.losses).coerceAtLeast(1)
        return when {
            slExits * 10 >= decided * 7 -> "SL ชน $slExits/${r.totalTrades} ไม้ (≥70%) → ขยาย SL +10%"
            r.timeouts * 2 > r.totalTrades && r.totalTrades > 0 -> "ค้าง ${r.timeouts}/${r.totalTrades} ไม้ → หด TP −5%"
            r.winRate >= 0.5 && r.profitFactor < 1.0 -> "ชนะเยอะแต่ PF<1 → ขยาย TP +10%"
            noChangeStreak >= 3 -> "นิ่ง 3 รอบติด → ปรับเล็กน้อย SL +2% (กันกลยุทธ์จำนวน)"
            else -> "โครงสร้างสุขภาพดี → คง params"
        } + " (กฎ)"
    }
}

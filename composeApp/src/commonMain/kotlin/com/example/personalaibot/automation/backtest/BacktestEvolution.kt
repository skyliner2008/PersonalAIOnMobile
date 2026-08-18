package com.example.personalaibot.automation.backtest

import com.example.personalaibot.automation.SignalMarkerProvider
import com.example.personalaibot.data.GeminiService
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
class BacktestEvolution(private val gemini: GeminiService?) {

    /** Circuit breaker: เมื่อเจอ quota/429 ให้หยุดเรียก AI สำหรับรอบที่เหลือของ run นั้น (กันยิงซ้ำรัวๆ จนเปลือง quota) */
    private var aiQuotaDead = false

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
        val trades: Int, val deltaVsBaseline: Double, val note: String
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
        initialOverride: TpSlParams? = null,   // params ล่าสุดที่ optimize/apply ไว้ — ทำให้ evolution ต่อเนื่อง ไม่เริ่มจาก default ทุกครั้ง
        memoryLines: List<String> = emptyList() // ประวัติการปรับย้อนหลัง (OptimizationTrial) — ให้ AI เรียนรู้จากผลการปรับตัวเอง
    ): EvolutionResult {
        // ไม่ reset aiQuotaDead ที่นี่ — instance ถูกสร้างใหม่ทุก task (runEvolveTask) และ evolve() ถูกเรียกต่อกันหลายกลยุทธ์
        // ถ้า quota ตายกลาง task ต้องคงสถานะข้ามกลยุทธ์ไว้ ไม่เช่นนั้นจะกลับไปยิง 429 ซ้ำทุกกลยุทธ์
        val n = candles.size
        val segSize = n / segments
        val initial = initialOverride ?: TpSlParams.defaultsFor(kind)
        var params = initial
        val rounds = mutableListOf<RoundResult>()
        var usedAi = false
        var noChangeStreak = 0
        var baselineTotalR = 0.0
        var evolvedTotalR = 0.0
        val trials = mutableListOf<ReflectionTrial>()

        for (s in 0 until segments) {
            val start = s * segSize
            val end = if (s == segments - 1) n else (s + 1) * segSize
            val segCandles = candles.subList(0, end) // ให้ indicator มีอดีต แต่เทรดเฉพาะช่วงนี้
            val segMarkers = markers.filter { m ->
                val t = m.time
                t >= segCandles[start].timestamp && t <= segCandles[end - 1].timestamp
            }

            fun runSeg(p: TpSlParams): BacktestResult? = runCatching {
                engine.run(
                    symbol = "", interval = "", source = "",
                    candles = segCandles, markers = segMarkers, kindFilter = setOf(kind),
                    kindOf = kindOf, strategyName = strategyName,
                    tpSl = { k, sd, c, i, a14, a6 -> parameterizedTpSl(k, sd, c, i, a14, a6, p) },
                    config = config,
                    startIndex = start   // วัดผลเฉพาะช่วง segment (indicator warm จาก prefix อยู่แล้ว)
                )
            }.getOrNull()

            val baseline = runSeg(initial)
            val evolved = runSeg(params)
            if (baseline != null) baselineTotalR += baseline.trades.sumOf { it.pnlR }
            if (evolved != null) evolvedTotalR += evolved.trades.sumOf { it.pnlR }

            val r = evolved ?: continue
            val slExits = r.trades.count { it.exitReason == "SL" }

            // ── สะท้อนผล → params รอบถัดไป ──
            val (nextParams, note, fromAi) = reflect(kind, s + 1, params, initial, r, slExits, noChangeStreak, memoryLines)
            if (fromAi) usedAi = true
            // RR floor: ห้ามผลลัพธ์สุดท้ายมี RR ต่ำกว่า 1.2 (กันเข็มทิศเสีย "ขยาย SL + หด TP" ที่ทำ evolved แพ้ระบบ 8/8 — forensics 2026-08-18)
            val clamped = TpSlParams.enforceRrFloor(kind, TpSlParams.clampDrift(initial, TpSlParams.clampStep(params, nextParams)))
            // นับ streak "นิ่ง" เฉพาะรอบที่มีไม้จริง — รอบ 0 ไม้ประเมินอะไรไม่ได้ ไม่ควรไปกระตุ้นปรับเล็กน้อย
            noChangeStreak = if (clamped == params && r.totalTrades > 0) noChangeStreak + 1 else 0

            // บันทึก trial (การปรับ → ผลที่ตามมา) — caller persist ลง OptimizationTrial เป็นความจำข้ามรัน
            val roundR = r.trades.sumOf { it.pnlR }
            val baseR = baseline?.trades?.sumOf { it.pnlR }
            trials += ReflectionTrial(
                round = s + 1,
                fromSl = params.slMult, fromTp = params.tpMult,
                toSl = clamped.slMult, toTp = clamped.tpMult,
                totalR = roundR, avgR = r.expectancyR, profitFactor = r.profitFactor,
                trades = r.totalTrades,
                deltaVsBaseline = if (baseR != null) roundR - baseR else 0.0,
                note = note
            )

            rounds += RoundResult(
                round = s + 1,
                bars = end - start,
                params = params,
                trades = r.totalTrades,
                wins = r.wins, losses = r.losses, timeouts = r.timeouts,
                winRate = r.winRate, avgR = r.expectancyR,
                totalR = roundR,
                profitFactor = r.profitFactor,
                slExits = slExits,
                reflection = note
            )
            params = clamped
        }

        return EvolutionResult(
            kind = kind, segments = rounds.size,
            initialParams = initial, finalParams = params,
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
        // รอบที่ไม้น้อยเกิน ประเมินอะไรไม่ได้ (สถิติระดับ 1-4 ไม้คือ noise — การปรับตาม noise = เดินสุ่ม)
        // เดิมกันแค่ 0 ไม้ → AI ถูกบังคับตอบบนสถิติที่ไม่มีนัยสำคัญทุกรอบ (forensics 2026-08-18)
        if (r.totalTrades < 5) {
            return Reflection(current, "ไม้น้อยเกิน (${r.totalTrades} < 5) — noise ประเมินไม่ได้ → คง params (กฎ)", false)
        }
        val g = gemini
        if (g != null && !aiQuotaDead) {
            val curRr = if (current.slMult > 0) current.tpMult / current.slMult else 0.0
            val prompt = buildString {
                appendLine("คุณคือ quant ที่ปรับจูนกลยุทธ์เทรด '$kind' แบบ walk-forward evolution")
                appendLine("กฎเหล็ก:")
                appendLine("1) ปรับทีละนิด ≤±10% ต่อรอบ ห้ามหนีค่าเริ่มต้น ${initial.slMult}/${initial.tpMult} เกิน ±30%")
                appendLine("2) ห้ามทำ RR (tp_mult/sl_mult) ต่ำกว่า 1.2 เด็ดขาด — winrate โดยทั่วไป ~40% ถ้า RR < 1.2 ระบบแพ้โดยโครงสร้าง แม้ winrate จะสูงขึ้น")
                appendLine("3) ถ้า winRate สูงแต่ PF ต่ำ = RR ต่ำเกินไป → ห้ามลด TP ต่อ ให้ขยาย TP หรือหด SL แทน")
                appendLine("4) SL-exits เยอะไม่ได้แปลว่า SL แคบเสมอไป — ถ้ารอบก่อนเพิ่งขยาย SL แล้วผลไม่ดีขึ้น ห้ามขยายซ้ำ ให้ลองหด SL หรือคงค่า")
                appendLine("ผลรอบ $round: trades=${r.totalTrades} ชนะ=${r.wins} แพ้=${r.losses} ค้าง=${r.timeouts} SL-exits=$slExits winRate=${"%.1f".format(r.winRate * 100)}% avgR=${"%+.2f".format(r.expectancyR)} PF=${"%.2f".format(r.profitFactor)}")
                appendLine("params ปัจจุบัน: sl_mult=${current.slMult}, tp_mult=${current.tpMult} (RR ปัจจุบัน=${"%.2f".format(curRr)})")
                if (memoryLines.isNotEmpty()) {
                    appendLine("ประวัติการปรับล่าสุดและผลที่ตามมา (เรียนรู้จากตรงนี้ — ทิศที่เคยปรับแล้วแย่ลง ห้ามทำซ้ำ):")
                    memoryLines.forEach { appendLine("- $it") }
                }
                appendLine("ตอบเป็น JSON บรรทัดเดียวเท่านั้น ห้ามมีข้อความอื่น: {\"sl_mult\": <ตัวเลข>, \"tp_mult\": <ตัวเลข>, \"note\": \"<เหตุผลสั้นๆ ภาษาไทย>\"}")
            }
            val resp = runCatching {
                g.generateResponse(prompt, timeoutMs = 25_000)
            }.getOrNull().orEmpty()
            val parsed = parseReflectionJson(resp)
            // throttle ทุกครั้งหลังเรียก AI (ทั้งสำเร็จ/ล้มเหลว) — free tier จำกัด 15 RPM/key (≈4s/request)
            // เดิม delay เฉพาะตอนสำเร็จและแค่ 400ms → burst ชนลิมิตทุก key ภายในไม่กี่วินาที (log 2026-08-18)
            kotlinx.coroutines.delay(2_000)
            if (parsed != null) {
                // ปฏิเสธคำแนะนำที่ทำ RR ต่ำกว่า 1.2 (AI มักเบี่ยงไปขยาย SL/หด TP ทุกรอบ — forensics 2026-08-18)
                val newRr = parsed.params.tpMult / parsed.params.slMult
                if (newRr < 1.2 && curRr >= 1.2) {
                    logDebug("BacktestEvolution", "❌ ปฏิเสธ AI reflection round $round ($kind): RR ใหม่ ${"%.2f".format(newRr)} < 1.2 — คง params เดิม")
                    return Reflection(current, "AI เสนอ RR ${"%.2f".format(newRr)} ต่ำกว่าพื้น 1.2 → ปฏิเสธ คง params (กฎ RR)", false)
                }
                logDebug("BacktestEvolution", "AI reflection round $round ($kind): $resp")
                return Reflection(parsed.params, parsed.note + " (AI)", true)
            }
            // ล้มเหลว: ถ้าเป็น quota/429 ให้ตัดวงจร — รอบที่เหลือของ run นี้ใช้กฎ heuristic หมด ไม่ยิงซ้ำ
            val low = resp.lowercase()
            if ("429" in resp || "quota" in low || "too many" in low || "rate" in low && "limit" in low) {
                if (!aiQuotaDead) logDebug("BacktestEvolution", "⚠️ AI reflection quota หมด/ถูกจำกัด ($resp) — ปิด AI สำหรับ run นี้ ใช้กฎ heuristic ต่อ")
                aiQuotaDead = true
            }
        }
        return Reflection(ruleBasedAdjust(current, r, slExits, noChangeStreak), ruleNote(r, slExits, noChangeStreak), false)
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

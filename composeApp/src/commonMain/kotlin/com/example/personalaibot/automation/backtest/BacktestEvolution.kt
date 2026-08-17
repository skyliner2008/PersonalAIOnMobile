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
 *  - ห้ามนิ่งเกิน 3 รอบติด (บังคับ微调 ≥2%)
 */
class BacktestEvolution(private val gemini: GeminiService?) {

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

    data class EvolutionResult(
        val kind: String,
        val segments: Int,
        val initialParams: TpSlParams,
        val finalParams: TpSlParams,
        val rounds: List<RoundResult>,
        val baselineTotalR: Double,   // ผลรวมถ้าใช้ params เดิมตลอด
        val evolvedTotalR: Double,    // ผลรวมของ params ที่วิวัฒน์
        val usedAiReflection: Boolean
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
        initialOverride: TpSlParams? = null   // params ล่าสุดที่ optimize/apply ไว้ — ทำให้ evolution ต่อเนื่อง ไม่เริ่มจาก default ทุกครั้ง
    ): EvolutionResult {
        val n = candles.size
        val segSize = n / segments
        val initial = initialOverride ?: TpSlParams.defaultsFor(kind)
        var params = initial
        val rounds = mutableListOf<RoundResult>()
        var usedAi = false
        var noChangeStreak = 0
        var baselineTotalR = 0.0
        var evolvedTotalR = 0.0

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
            val (nextParams, note, fromAi) = reflect(kind, s + 1, params, initial, r, slExits, noChangeStreak)
            if (fromAi) usedAi = true
            val clamped = TpSlParams.clampDrift(initial, TpSlParams.clampStep(params, nextParams))
            // นับ streak "นิ่ง" เฉพาะรอบที่มีไม้จริง — รอบ 0 ไม้ประเมินอะไรไม่ได้ ไม่ควรไปกระตุ้น微调
            noChangeStreak = if (clamped == params && r.totalTrades > 0) noChangeStreak + 1 else 0

            rounds += RoundResult(
                round = s + 1,
                bars = end - start,
                params = params,
                trades = r.totalTrades,
                wins = r.wins, losses = r.losses, timeouts = r.timeouts,
                winRate = r.winRate, avgR = r.expectancyR,
                totalR = r.trades.sumOf { it.pnlR },
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
            usedAiReflection = usedAi
        )
    }

    // ─── Reflection: AI ก่อน ล้มเหลวค่อยใช้กฎ ───

    private data class Reflection(val params: TpSlParams, val note: String, val fromAi: Boolean)

    private suspend fun reflect(
        kind: String, round: Int, current: TpSlParams, initial: TpSlParams,
        r: BacktestResult, slExits: Int, noChangeStreak: Int
    ): Reflection {
        // รอบที่ไม่มีไม้เลย ประเมินอะไรไม่ได้ — คง params และไม่เรียก AI (กัน reflection มั่ว เช่น "สุขภาพดี" ทั้งที่ 0 ไม้)
        if (r.totalTrades == 0) {
            return Reflection(current, "ไม่มีไม้ในช่วงนี้ ข้อมูลไม่พอประเมิน → คง params (กฎ)", false)
        }
        val g = gemini
        if (g != null) {
            val prompt = buildString {
                appendLine("คุณคือ quant ที่ปรับจูนกลยุทธ์เทรด '$kind' แบบ walk-forward evolution (หลักการ: ปรับทีละนิด ไม่ overreact, ห้ามเกิน ±10% ต่อรอบ, ห้ามหนีค่าเริ่มต้น ${initial.slMult}/${initial.tpMult} เกิน ±30%)")
                appendLine("ผลรอบ $round: trades=${r.totalTrades} ชนะ=${r.wins} แพ้=${r.losses} ค้าง=${r.timeouts} SL-exits=$slExits winRate=${"%.1f".format(r.winRate * 100)}% avgR=${"%+.2f".format(r.expectancyR)} PF=${"%.2f".format(r.profitFactor)}")
                appendLine("params ปัจจุบัน: sl_mult=${current.slMult}, tp_mult=${current.tpMult}")
                appendLine("ตีความ: SL เยอะ = SL แคบเกิน; ค้าง(TIMEOUT) เยอะ = TP ไกลเกิน; ชนะเยอะแต่ PF ต่ำ = TP ใกล้เกิน")
                appendLine("ตอบเป็น JSON บรรทัดเดียวเท่านั้น ห้ามมีข้อความอื่น: {\"sl_mult\": <ตัวเลข>, \"tp_mult\": <ตัวเลข>, \"note\": \"<เหตุผลสั้นๆ ภาษาไทย>\"}")
            }
            val resp = runCatching {
                g.generateResponse(prompt, timeoutMs = 25_000)
            }.getOrNull().orEmpty()
            val parsed = parseReflectionJson(resp)
            if (parsed != null) {
                logDebug("BacktestEvolution", "AI reflection round $round ($kind): $resp")
                return Reflection(parsed.params, parsed.note + " (AI)", true)
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
            // นิ่งเกิน 3 รอบ → บังคับ微调 2% รักษา "ความมีชีวิต" ของกลยุทธ์
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
            noChangeStreak >= 3 -> "นิ่ง 3 รอบติด → 微调 SL +2%"
            else -> "โครงสร้างสุขภาพดี → คง params"
        } + " (กฎ)"
    }
}

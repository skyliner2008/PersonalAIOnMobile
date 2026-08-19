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
 * SignalAlertProvider — ตรวจ "สัญญาณที่เพิ่งเกิด" ในแท่งปิดล่าสุด จาก 8 กลยุทธ์คลาสสิก
 * (MOM / TR / REV / DC / 52H / E14-60 / UT Bot / 3BR) + SMC Engine (port จาก mt5-core-server:
 * OB Bounce / CHoCH / SMS-BMS / FVG Fill / Liquidity Sweep / RSI Divergence)
 * สำหรับ background job `trading_signal_alert`
 *
 * จุดต่างจาก SignalMarkerProvider (ที่คำนวณย้อนหลังทั้งชุดเพื่อวาดกราฟ):
 *  - สนใจเฉพาะ edge ที่ "แท่งปิดล่าสุด" (n-2 — แท่งสุดท้ายอาจยังไม่ปิด)
 *  - คำนวณ Entry / SL / TP เฉพาะกลยุทธ์ + เหตุผล (ไทย) + context snapshot
 *    ส่งต่อให้ AI quick-check ก่อนแจ้งผู้ใช้
 *
 * คืน Map<String,String> รูปแบบเดียวกับ provider อื่น (ใช้กับ AutomationEvaluator ได้):
 *  - signal_buy / signal_sell : "1" = มี edge ใหม่ฝั่งนั้นในแท่งปิดล่าสุด (ตั้งเงื่อนไข >= 1)
 *  - signal_buy_id / signal_sell_id : timestamp(ms) ของแท่งที่เกิด edge ฝั่งนั้น ("0" = ไม่มี)
 *    — ใช้กับ operator ">" โดยตั้งค่า = เวลาที่สร้าง alert → ยิงเฉพาะสัญญาณที่เกิด "หลัง" สร้าง alert
 *    (กันเคสสร้าง alert แล้วเด้งทันทีเพราะ marker เก่ายังค้างในแท่งปิดล่าสุด)
 *  - signal_event             : "BUY" / "SELL" / "NONE" (ใช้กับ ==)
 *  - signal_strategy / signal_side / signal_reason / signal_context : ข้อความ payload
 *  - signal_entry / signal_sl / signal_tp / signal_rr : ตัวเลขของสัญญาณหลัก
 */
/**
 * แปลง marker label → kind กลยุทธ์มาตรฐาน
 * ("MOM▲"→MOM, "TR▼"→TR, "REV▲"→REV, "DC▲"→DC, "52H▲"→52H, "E14/60▲"→E, "UT▼"→UT, "3BR▲"→3BR)
 * ห้ามใช้ label.filter{isLetter} — "52H" จะเหลือ "H" และ "3BR" เหลือ "BR" ทำ lookup พังเงียบๆ
 */
internal fun signalKindOf(label: String): String {
    val base = label.trimEnd('▲', '▼')
    return if (base.startsWith("E")) "E" else base
}

class SignalAlertProvider(private val smcApi: SmcApiService) {

    private val markerProvider = SignalMarkerProvider(smcApi)

    suspend fun fetch(rawSymbol: String): Map<String, String> {
        val (symbol, tf) = IndicatorAlertProvider.splitSymbolAndTf(rawSymbol)
        val candles = runCatching { smcApi.fetchCandlesWithSource(symbol, tf, 300).candles }
            .getOrElse { return mapOf("error" to (it.message ?: "fetch failed")) }
        if (candles.size < 62) return mapOf("error" to "bars=${candles.size} < 62")

        val n = candles.size
        val sigIdx = n - 2 // แท่งปิดล่าสุด (แท่ง n-1 อาจกำลังวิ่ง)
        val sigTime = candles[sigIdx].timestamp

        // entry params ที่จูนแล้ว (EntryTuning) — ใช้แทนค่า default ของทุก kind ที่มี tuning
        val tunedEntry = runCatching {
            com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager()
                .getTunedEntryParams(symbol, tf)
        }.getOrElse { emptyMap() }
        if (tunedEntry.isNotEmpty()) {
            logDebug("SignalAlert", "$symbol/$tf ใช้ tuned entry params: ${tunedEntry.keys.joinToString(",")}")
        }
        val edges = markerProvider.compute(candles, entryParams = tunedEntry).filter { it.time == sigTime }.toMutableList()

        // ── SMC (MT5 Engine) — ตรวจสัญญาณใหม่จากโครงสร้างตลาด/OB/FVG/Sweep/RSI divergence ──
        var smcNew = runCatching {
            com.example.personalaibot.automation.smc.SmcSignals.newSignalsAt(candles, sigIdx, symbol, tf)
        }.getOrElse { emptyList() }

        // ── MIX voting — ถ้าผู้ใช้ตั้ง mix config ไว้: โหวตรวม state ของกลยุทธ์ที่เลือก ──
        val mixCfg = runCatching {
            com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager().getMixConfig(symbol, tf)
        }.getOrNull()
        val mixKinds = mixCfg?.let { MixSignalEngine.parseKinds(it.first) } ?: emptyList()
        var mixScore = 0
        var mixVoteDetail = ""
        if (mixCfg != null && mixKinds.size >= 2) {
            val cache = MixSignalEngine.SeriesCache(candles)
            mixScore = MixSignalEngine.scoreAt(cache, mixKinds, sigIdx)
            mixVoteDetail = MixSignalEngine.votesAt(cache, mixKinds, sigIdx)
                .entries.joinToString(", ") { (k, v) -> "$k=${if (v > 0) "+1" else v.toString()}" }
            val mixEdges = MixSignalEngine.mixMarkers(candles, mixKinds, mixCfg.second, cache)
                .filter { it.time == sigTime }
            edges.addAll(mixEdges)
            logDebug("SignalAlert", "$symbol/$tf MIX score=$mixScore/${mixCfg.second} [$mixVoteDetail] edges=${mixEdges.size}")
        }

        // ── Per-TF Strategy Gate (4 ระดับ: BLOCK/WEAK/NORMAL/STRONG) ──
        // บล็อกเฉพาะ BLOCK (backtest ล่าสุดแพ้ระบบชัด: PF<1.0 หรือ expectancy≤0, ไม้ ≥5)
        // WEAK ยังยิงได้ แต่ติดป้ายใน metadata ให้ AI/ผู้ใช้รู้ว่ากลยุทธ์อ่อน
        var gatedKinds = emptyList<String>()
        var weakKinds = emptyList<String>()
        val gateMgr = runCatching { com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager() }.getOrNull()
        if (gateMgr != null && (edges.isNotEmpty() || smcNew.isNotEmpty())) {
            edges.removeAll { e ->
                val k = signalKindOf(e.label)
                val level = gateMgr.strategyGateLevel(symbol, tf, k)
                if (level == com.example.personalaibot.automation.AutomationManager.StrategyGateLevel.WEAK) weakKinds = weakKinds + k
                if (level == com.example.personalaibot.automation.AutomationManager.StrategyGateLevel.BLOCK) { gatedKinds = gatedKinds + k; true } else false
            }
            if (smcNew.isNotEmpty()) {
                val smcLevel = gateMgr.strategyGateLevel(symbol, tf, "SMC")
                if (smcLevel == com.example.personalaibot.automation.AutomationManager.StrategyGateLevel.BLOCK) {
                    gatedKinds = gatedKinds + "SMC"
                    smcNew = emptyList()
                } else if (smcLevel == com.example.personalaibot.automation.AutomationManager.StrategyGateLevel.WEAK) {
                    weakKinds = weakKinds + "SMC"
                }
            }
            if (gatedKinds.isNotEmpty()) {
                logDebug("JarvisVM", "⛔ Gate บล็อก $symbol/$tf: ${gatedKinds.joinToString(",")} (backtest ล่าสุด PF<1/expectancy≤0) — ไม่ยิง alert")
            }
            if (weakKinds.isNotEmpty()) {
                logDebug("JarvisVM", "⚠️ Gate WEAK $symbol/$tf: ${weakKinds.joinToString(",")} (ผ่านแบบหวุดหวิด — PF<1.25/avgR<0.1/WR<35%)")
            }
        }

        val closes = candles.map { it.close }
        val close = closes[sigIdx]
        val atr14 = atrSeries(candles, 14)[sigIdx]
        val atr6 = atrSeries(candles, 6)[sigIdx]

        // ── context snapshot (สั้น กระชับ พอสำหรับ AI quick-check) ──
        val e50 = ema(closes, 50)[sigIdx]
        val e200 = ema(closes, 200)[sigIdx]
        val rsi = rsiSeries(closes, 14)[sigIdx]
        val bbBasis = sma(closes, 20)[sigIdx]
        val bbSd = stdev(closes, 20, sigIdx)
        val trend = when {
            e50.isNaN() || e200.isNaN() -> "N/A"
            e50 > e200 -> "UPTREND(EMA50>200)"
            else -> "DOWNTREND(EMA50<200)"
        }
        var dcU = Double.NEGATIVE_INFINITY; var dcL = Double.POSITIVE_INFINITY
        for (j in max(0, sigIdx - 20) until sigIdx) { dcU = max(dcU, candles[j].high); dcL = min(dcL, candles[j].low) }
        fun fmt(v: Double) = if (abs(v) >= 100) "%.2f".format(v) else "%.4f".format(v)
        val context = "trend=$trend | RSI=${"%.1f".format(rsi)} | vsBB=${if (close > bbBasis + 2 * bbSd) "เหนือUpper" else if (close < bbBasis - 2 * bbSd) "ใต้Lower" else if (close > bbBasis) "โซนบน" else "โซนล่าง"} | ATR14=${fmt(atr14)} | DC20[${fmt(dcL)}-${fmt(dcU)}]"

        if (edges.isEmpty() && smcNew.isEmpty()) {
            return mapOf(
                "signal_buy" to "0",
                "signal_sell" to "0",
                "signal_buy_id" to "0",
                "signal_sell_id" to "0",
                "signal_event" to "NONE",
                "signal_mix_score" to mixScore.toString(),
                "signal_mix_votes" to mixVoteDetail,
                "signal_gated" to gatedKinds.joinToString(","),
                "signal_weak" to weakKinds.joinToString(","),
                "close" to fmt(close),
                "signal_context" to context
            )
        }

        // ── มีสัญญาณใหม่: รวมทุก edge ของแท่งนี้ (คลาสสิก + SMC) ──
        val sides = (edges.map { it.side } + smcNew.map { it.side }).distinct()
        val side = sides.first() // สัญญาณหลัก (ถ้ามีหลายฝั่งพร้อมกัน — หายาก — ใช้ตัวแรก)
        val primaryClassic = edges.firstOrNull { it.side == side }
        val primarySmc = smcNew.firstOrNull { it.side == side }

        val kind: String
        val sl: Double
        val tp: Double
        if (primaryClassic != null) {
            kind = signalKindOf(primaryClassic.label) // MOM/TR/REV/DC/52H/E/UT/3BR

            // ใช้ tuned params จาก backtest (StrategyTuning) ถ้ามีและไม่ overfit — ไม่งั้นใช้สูตร default
            val tuning = runCatching {
                com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager()
                    .getStrategyTuning(symbol, tf, kind)
            }.getOrNull()?.takeIf { it.grade != "overfit" }
            val pair = if (tuning != null) {
                logDebug("SignalAlert", "$symbol/$tf/$kind ใช้ tuned params sl=${tuning.sl_mult} tp=${tuning.tp_mult} (${tuning.source}, grade=${tuning.grade})")
                com.example.personalaibot.automation.backtest.parameterizedTpSl(
                    kind, side, candles, sigIdx, atr14, atr6,
                    com.example.personalaibot.automation.backtest.TpSlParams(tuning.sl_mult, tuning.tp_mult)
                )
            } else {
                computeTpSl(kind, side, candles, sigIdx, atr14, atr6)
            }
            sl = pair.first; tp = pair.second
        } else {
            // สัญญาณ SMC (MT5 Engine) — SL/TP มาจากโครงสร้างตลาดของตัวเอง (ไม่ใช่ ATR multiple)
            kind = "SMC"
            sl = primarySmc!!.sl
            tp = primarySmc.tp
        }
        val risk = abs(close - sl)
        val rr = if (risk > 0) abs(tp - close) / risk else 0.0

        val strategies = (edges.map { strategyName(signalKindOf(it.label)) } +
            smcNew.map { smcStrategyName(it.strategy) }).joinToString(" + ")
        val reasons = (edges.map { reasonFor(signalKindOf(it.label), it.side, tunedEntry[signalKindOf(it.label)]) } +
            smcNew.flatMap { it.triggers }).joinToString(" ; ")

        logDebug("SignalAlert", "$symbol/$tf NEW $side signal: $strategies @ ${fmt(close)} SL=${fmt(sl)} TP=${fmt(tp)}")

        return mapOf(
            "signal_buy" to if (side == "BUY") "1" else "0",
            "signal_sell" to if (side == "SELL") "1" else "0",
            "signal_buy_id" to if (side == "BUY") sigTime.toString() else "0",
            "signal_sell_id" to if (side == "SELL") sigTime.toString() else "0",
            "signal_event" to side,
            "signal_strategy" to strategies,
            "signal_side" to side,
            "signal_entry" to fmt(close),
            "signal_sl" to fmt(sl),
            "signal_tp" to fmt(tp),
            "signal_rr" to "%.2f".format(rr),
            "signal_atr" to fmt(atr14),
            "signal_reason" to reasons,
            "signal_stars" to (primarySmc?.confluenceStars?.toString() ?: "0"),
            "signal_mix_score" to mixScore.toString(),
            "signal_mix_votes" to mixVoteDetail,
            "signal_gated" to gatedKinds.joinToString(","),
            "signal_weak" to weakKinds.joinToString(","),
            "signal_context" to context,
            "signal_bar_time" to sigTime.toString(),
            "close" to fmt(close)
        )
    }

    // ─── TP/SL เฉพาะกลยุทธ์ (ออกแบบตามพฤติกรรมของแต่ละตัว) ─────────────────

    internal fun computeTpSl(
        kind: String, side: String, candles: List<Candle>, i: Int, atr14: Double, atr6: Double
    ): Pair<Double, Double> {
        val entry = candles[i].close
        val isBuy = side == "BUY"
        fun levels(slDist: Double, tpDist: Double): Pair<Double, Double> =
            if (isBuy) (entry - slDist) to (entry + tpDist) else (entry + slDist) to (entry - tpDist)

        return when (kind) {
            // ตามเทรนด์ — ให้เทรนด์วิ่ง RR 1:1.5
            "MOM", "TR", "E" -> levels(2.0 * atr14, 3.0 * atr14)
            // UT Bot — SL = ระยะ trailing stop ของมันเอง (2×ATR6), TP = 2×risk
            "UT" -> levels(2.0 * atr6, 4.0 * atr6)
            // Donchian breakout — กัน false breakout
            "DC" -> levels(1.5 * atr14, 2.5 * atr14)
            // Mean reversion — SL สั้น 1×ATR, TP = BB basis (เส้นกลาง) ถ้าอยู่ฝั่งกำไร ไม่งั้น 1.5×ATR
            "REV" -> {
                val basis = sma(candles.map { it.close }, 20)[i]
                val tp = if (!basis.isNaN() && (if (isBuy) basis > entry else basis < entry)) {
                    basis
                } else if (isBuy) entry + 1.5 * atr14 else entry - 1.5 * atr14
                (if (isBuy) entry - atr14 else entry + atr14) to tp
            }
            // 3-Bar Reversal — SL = จุดสุดของแท่งกลาง pattern, TP = 2×risk
            "3BR" -> {
                val mid = candles[i - 1]
                val slRaw = if (isBuy) mid.low - 0.2 * atr14 else mid.high + 0.2 * atr14
                val risk = abs(entry - slRaw).coerceAtLeast(0.5 * atr14)
                (if (isBuy) entry - risk else entry + risk) to (if (isBuy) entry + 2 * risk else entry - 2 * risk)
            }
            // 52W High — momentum continuation
            "52H" -> levels(1.5 * atr14, 2.0 * atr14)
            // Mix Voting — สัญญาณผสมหลายกลยุทธ์ (ต้องโหวตผ่านเกณฑ์) ให้ห้องหายใจกว้างหน่อย
            "MIX" -> levels(1.5 * atr14, 2.5 * atr14)
            else -> levels(1.5 * atr14, 2.0 * atr14)
        }
    }

    internal fun strategyName(kind: String): String = when (kind) {
        "MOM" -> "Time-Series Momentum"
        "TR" -> "Trend Following (EMA Cross)"
        "REV" -> "Short-Term Reversal"
        "DC" -> "Donchian Breakout"
        "52H" -> "52W High Proximity"
        "E" -> "EMA Cross (fast/slow)"
        "UT" -> "UT Bot"
        "3BR" -> "3-Bar Reversal"
        "SMC" -> "SMC (MT5 Engine)"
        "MIX" -> "Mix Voting (ผสมกลยุทธ์)"
        else -> kind
    }

    /** ชื่อแสดงผลของกลยุทธ์ย่อยฝั่ง SMC (port จาก mt5-core-server SignalDetector) */
    internal fun smcStrategyName(strategy: String): String = when (strategy) {
        "SMC_FVG_REVERSAL" -> "SMC Reversal (OB Bounce/CHoCH)"
        "SMC_FVG_CONTINUATION" -> "SMC Continuation (SMS/BMS)"
        "SMC_FVG_SCALP" -> "SMC FVG Fill Scalp"
        "SMC_RSI_DIVERGENCE" -> "SMC RSI Divergence"
        "SCALPING" -> "SMC Liquidity Sweep"
        else -> strategy
    }

    private fun reasonFor(kind: String, side: String, ep: com.example.personalaibot.automation.backtest.EntryParams? = null): String {
        val up = side == "BUY"
        return when (kind) {
            "MOM" -> if (up) "ROC ${ep?.momLookback ?: 20} แท่งพลิกจากลบเป็นบวก (โมเมนตัมเปลี่ยนขึ้น)" else "ROC ${ep?.momLookback ?: 20} แท่งพลิกจากบวกเป็นลบ (โมเมนตัมเปลี่ยนลง)"
            "TR" -> if (up) "EMA${ep?.trFast ?: 50} ตัดขึ้นเหนือ EMA${ep?.trSlow ?: 200} (Golden Cross)" else "EMA${ep?.trFast ?: 50} ตัดลงใต้ EMA${ep?.trSlow ?: 200} (Death Cross)"
            "REV" -> if (up) "RSI ต่ำกว่า ${(ep?.revRsiLow ?: 30.0).toInt()} + ราคาแตะ Bollinger Lower (ขายมากเกิน คาดเด้ง)" else "RSI สูงกว่า ${(ep?.revRsiHigh ?: 70.0).toInt()} + ราคาแตะ Bollinger Upper (ซื้อมากเกิน คาดย่อ)"
            "DC" -> if (up) "ราคาปิดทะลุ High ${ep?.dcPeriod ?: 20} แท่ง (breakout ขึ้น)" else "ราคาปิดหลุด Low ${ep?.dcPeriod ?: 20} แท่ง (breakout ลง)"
            "52H" -> if (up) "ราคาเข้าใกล้จุดสูงสุดสะสม (≥${((ep?.w52ProxBuy ?: 0.98) * 100).toInt()}% — momentum แรงต่อเนื่อง)" else "ราคาหลุด ${((ep?.w52ProxSell ?: 0.90) * 100).toInt()}% จากจุดสูงสุดสะสม (โมเมนตัมเสีย)"
            "E" -> if (up) "EMA${ep?.eFast ?: 14} ตัดขึ้นเหนือ EMA${ep?.eSlow ?: 60} พร้อมแท่งยืนยัน" else "EMA${ep?.eFast ?: 14} ตัดลงใต้ EMA${ep?.eSlow ?: 60} พร้อมแท่งยืนยัน"
            "UT" -> if (up) "ราคาปิดเหนือ UT Bot trailing stop (ATR${ep?.utAtrPeriod ?: 6}×${ep?.utKey ?: 2.0}) — flip เป็นขาขึ้น" else "ราคาปิดใต้ UT Bot trailing stop (ATR${ep?.utAtrPeriod ?: 6}×${ep?.utKey ?: 2.0}) — flip เป็นขาลง"
            "3BR" -> if (up) "รูปแบบ 3-Bar Reversal ขาขึ้น (แท่ง 3 กลืนกิน high แท่งแรก)" else "รูปแบบ 3-Bar Reversal ขาลง (แท่ง 3 กลืนกิน low แท่งแรก)"
            "MIX" -> if (up) "คะแนนโหวตรวมของหลายกลยุทธ์ข้ามเกณฑ์ฝั่งขึ้น (confluence หลายระบบ)" else "คะแนนโหวตรวมของหลายกลยุทธ์ข้ามเกณฑ์ฝั่งลง (confluence หลายระบบ)"
            else -> "สัญญาณ $kind $side"
        }
    }

    // ─── Backtest: จำลอง TP/SL เดินหน้าจากสัญญาณย้อนหลังทุกจุด ─────────────

    data class KindStats(
        val kind: String, val name: String,
        var signals: Int = 0, var wins: Int = 0, var losses: Int = 0, var timeouts: Int = 0,
        var sumR: Double = 0.0
    ) {
        val decided get() = wins + losses
        val winratePct get() = if (decided > 0) wins * 100.0 / decided else 0.0
        val avgR get() = if (signals > 0) sumR / signals else 0.0
    }

    /**
     * สถิติ win-rate ย้อนหลังของทุกกลยุทธ์ (หรือเลือกเฉพาะตัว) — จำลองทุกสัญญาณในชุดแท่งเทียน:
     * เดินหน้าทีละแท่งจากจุดสัญญาณ ถ้าชน SL ก่อน = แพ้ (-1R), ชน TP ก่อน = ชนะ (+RR ของกลยุทธ์นั้น),
     * ถ้าแท่งเดียวกันชนทั้งคู่ให้ถือว่าแพ้ (conservative), ครบชุดแท่งแล้วไม่ชน = timeout (คิด R จากราคาปิดสุดท้าย)
     */
    suspend fun fetchStats(rawSymbol: String, strategy: String = "all"): String {
        val (symbol, tf) = IndicatorAlertProvider.splitSymbolAndTf(rawSymbol)
        val candles = runCatching { smcApi.fetchCandlesWithSource(symbol, tf, 300).candles }
            .getOrElse { return "❌ ดึงแท่งเทียนไม่ได้: ${it.message}" }
        if (candles.size < 62) return "❌ แท่งเทียนไม่พอ (${candles.size} < 62)"

        val n = candles.size
        val closes = candles.map { it.close }
        val atr14S = atrSeries(candles, 14)
        val atr6S = atrSeries(candles, 6)
        val timeToIdx = HashMap<Long, Int>(n)
        for (i in 0 until n) timeToIdx[candles[i].timestamp] = i

        val kindFilter = when (strategy.lowercase()) {
            "tsmom" -> setOf("MOM"); "trend" -> setOf("TR"); "reversal" -> setOf("REV")
            "donchian" -> setOf("DC"); "w52high" -> setOf("52H"); "ema1460", "ema14_60" -> setOf("E")
            "utbot", "ut" -> setOf("UT"); "threebar", "3br" -> setOf("3BR")
            else -> setOf("MOM", "TR", "REV", "DC", "52H", "E", "UT", "3BR")
        }

        val tunedEntry = runCatching {
            com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager()
                .getTunedEntryParams(symbol, tf)
        }.getOrElse { emptyMap() }
        val markers = markerProvider.compute(candles, maxPerKind = Int.MAX_VALUE, entryParams = tunedEntry)
            .filter { signalKindOf(it.label) in kindFilter }
        if (markers.isEmpty()) return "📭 ไม่พบสัญญาณย้อนหลังของกลยุทธ์ที่เลือกใน $symbol $tf"

        val statsMap = LinkedHashMap<String, KindStats>()
        for (m in markers) {
            val kind = signalKindOf(m.label)
            val i = timeToIdx[m.time] ?: continue
            if (i >= n - 1) continue // แท่งสุดท้ายไม่มีอนาคตให้จำลอง
            val st = statsMap.getOrPut(kind) { KindStats(kind, strategyName(kind)) }
            val entry = closes[i]
            val (sl, tp) = computeTpSl(kind, m.side, candles, i, atr14S[i], atr6S[i])
            val risk = kotlin.math.abs(entry - sl)
            if (risk <= 0) continue
            val isBuy = m.side == "BUY"
            val winR = kotlin.math.abs(tp - entry) / risk
            var r: Double? = null
            for (j in i + 1 until n) {
                val hitSl = if (isBuy) candles[j].low <= sl else candles[j].high >= sl
                val hitTp = if (isBuy) candles[j].high >= tp else candles[j].low <= tp
                if (hitSl) { r = -1.0; break }          // ชน SL ก่อน (ถ้าแท่งเดียวชนทั้งคู่ ให้แพ้ — conservative)
                if (hitTp) { r = winR; break }
            }
            st.signals++
            when {
                r == null -> { // timeout — ปิดที่ราคาปิดแท่งสุดท้าย
                    st.timeouts++
                    st.sumR += (if (isBuy) closes[n - 1] - entry else entry - closes[n - 1]) / risk
                }
                r > 0 -> { st.wins++; st.sumR += r }
                else -> { st.losses++; st.sumR += r }
            }
        }

        val ordered = statsMap.values.sortedByDescending { it.avgR }
        return buildString {
            appendLine("📊 **Signal Win-Rate ย้อนหลัง — $symbol $tf** (จำลองจาก $n แท่ง)")
            appendLine("กติกา: เข้าที่ราคาปิดแท่งสัญญาณ, SL/TP ตามสูตรเฉพาะกลยุทธ์, แท่งชนทั้งคู่ถือว่าแพ้, ค้างถึงแท่งสุดท้าย=timeout")
            appendLine()
            ordered.forEach { st ->
                val wr = if (st.decided > 0) "%.0f%%".format(st.winratePct) else "-"
                appendLine("• **${st.name}**: ${st.signals} สัญญาณ | ชนะ ${st.wins} แพ้ ${st.losses} (win-rate $wr) | timeout ${st.timeouts} | avg ${"%+.2f".format(st.avgR)}R")
            }
            appendLine()
            appendLine("⚠️ สถิติย้อนหลังจากข้อมูลจำกัด ($n แท่ง) ไม่การันตีอนาคต — ใช้ประกอบการตัดสินเท่านั้น")
        }.trim()
    }

    // ─── Math helpers (ตัวเดียวกับ SignalMarkerProvider — คงผลลัพธ์ให้ตรงกัน) ───

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

    private fun sma(values: List<Double>, period: Int): List<Double> {
        val out = ArrayList<Double>(values.size)
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            out.add(if (i >= period - 1) sum / period else Double.NaN)
        }
        return out
    }

    private fun stdev(values: List<Double>, period: Int, idx: Int): Double {
        if (idx < period - 1) return 0.0
        val window = values.subList(idx - period + 1, idx + 1)
        val mean = window.average()
        return sqrt(window.sumOf { (it - mean).pow(2) } / window.size)
    }

    private fun rsiSeries(closes: List<Double>, period: Int): DoubleArray {
        val out = DoubleArray(closes.size) { 50.0 }
        if (closes.size <= period) return out
        var gain = 0.0; var loss = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d > 0) gain += d else loss -= d
        }
        var avgGain = gain / period; var avgLoss = loss / period
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

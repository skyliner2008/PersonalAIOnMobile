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
 * SignalAlertProvider — ตรวจ "สัญญาณที่เพิ่งเกิด" ในแท่งปิดล่าสุด
 * Engine หลัก: Unified SMC Multi-TF V5 (smc/UnifiedSmcSignals.kt — H4/H1 context, M30/H1 trend,
 * M15 setup, M5 confirmation; ประเมินเฉพาะ job 15m; สถานะ research/forward-test)
 * คลาสสิกที่เหลือ: MOM / REV เท่านั้น (TR/DC/52H/E/UT/3BR ถูกตัดตาม cross-TF forensics 2026-08-27
 * — ดู SignalMarkerProvider.ENABLED_KINDS; re-enable/forensics ได้ผ่าน kindsOverride)
 * + SMC Engine (port จาก mt5-core-server: OB Bounce / CHoCH / SMS-BMS / FVG Fill / Liquidity Sweep / RSI Divergence)
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
 *  - signal_keyzone           : "1" = ราคาปิด M15 ล่าสุดอยู่ที่จุดสำคัญ (swing/EQ/FVG/OB ใน 0.3×ATR)
 *  - signal_keyzone_desc      : คำอธิบายจุดสำคัญที่ราคาแตะ (ไทย)
 *  - signal_mtf_context       : โครงสร้างตลาด 5TF (MarketContextDigest) — AI Supervisor ใช้ตัดสิน
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

/** Runtime source policy for signal alerts.
 * DEMO/PAPER always uses TradingView. MT5 candles are permitted only when the
 * confirmed execution mode is LIVE and the MT5 bridge is actually connected.
 */
object TradingSignalMarketDataRouter {
    private var liveModeProvider: (() -> Boolean)? = null
    private var mt5ConnectedProvider: (() -> Boolean)? = null
    private var mt5CandleProvider: (suspend (String, String, Int) -> List<Candle>)? = null

    // Observability dedup: รวบเหลือ 1 บรรทัดต่อ symbol ไม่พ่นซ้ำซากทุก TF ในรอบเดียวกัน
    private val lastLoggedDecisions = mutableMapOf<String, Pair<String, Long>>()

    fun configure(
        liveModeProvider: () -> Boolean,
        mt5ConnectedProvider: () -> Boolean,
        mt5CandleProvider: suspend (String, String, Int) -> List<Candle>
    ) {
        this.liveModeProvider = liveModeProvider
        this.mt5ConnectedProvider = mt5ConnectedProvider
        this.mt5CandleProvider = mt5CandleProvider
    }

    suspend fun fetch(
        symbol: String,
        timeframe: String,
        count: Int,
        tvProvider: suspend () -> List<Candle>
    ): Triple<List<Candle>, String, String> {
        val live = liveModeProvider?.invoke() == true
        val connected = mt5ConnectedProvider?.invoke() == true
        val result: Triple<List<Candle>, String, String>

        if (live && connected) {
            val mt5 = runCatching { mt5CandleProvider?.invoke(symbol, timeframe, count).orEmpty() }.getOrElse { emptyList() }
            if (mt5.size >= 62) {
                result = Triple(mt5, "MT5_LIVE", "MT5_LIVE_CONNECTED")
            } else {
                result = Triple(tvProvider(), "TRADINGVIEW", "MT5_CANDLE_UNAVAILABLE")
            }
        } else {
            val reason = if (live) "MT5_LIVE_OFFLINE" else "DEMO_FORCES_TV"
            result = Triple(tvProvider(), "TRADINGVIEW", reason)
        }

        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val decisionKey = "$symbol:live=$live,conn=$connected,src=${result.second},reason=${result.third}"
        val prev = lastLoggedDecisions[symbol]

        // รวมเหลือ 1 บรรทัด: พ่นเฉพาะเมื่อเริ่มแรก, สถานะ routing เปลี่ยนแปลง, หรือครบ 5 นาที (Heartbeat)
        if (prev == null || prev.first != decisionKey || (now - prev.second) >= 300_000L) {
            lastLoggedDecisions[symbol] = decisionKey to now
            logDebug(
                "SignalDataSource",
                "accountMode=${if (live) "LIVE" else "DEMO"} mt5Connected=$connected selectedSource=${result.second} reason=${result.third} symbol=$symbol"
            )
        }

        return result
    }
}

class SignalAlertProvider(private val smcApi: SmcApiService) {
    // Observability dedup: a closed-bar signal is recomputed on every automation cycle,
    // but should be logged as NEW only when its signal bar timestamp changes.
    private val lastLoggedSignalBar = mutableMapOf<String, Long>()
    private val lastLoggedGateBar = mutableMapOf<String, Long>()
    private val lastLoggedTunedEntryBar = mutableMapOf<String, Long>()

    private val markerProvider = SignalMarkerProvider(smcApi)

    suspend fun fetch(rawSymbol: String): Map<String, String> {
        val (symbol, tf) = IndicatorAlertProvider.splitSymbolAndTf(rawSymbol)
        val (candles, candleSource, candleSourceReason) = runCatching {
            TradingSignalMarketDataRouter.fetch(
                symbol = symbol,
                timeframe = tf,
                count = 300,
                tvProvider = { smcApi.fetchCandlesWithSource(symbol, tf, 300).candles }
            )
        }.getOrElse { return mapOf("error" to (it.message ?: "fetch failed")) }
        if (candles.size < 62) return mapOf(
            "error" to "bars=${candles.size} < 62",
            "signal_data_source" to candleSource,
            "signal_data_source_reason" to candleSourceReason
        )

        // ── Closed-Loop Learning: Evaluate active open signals against current candles ──
        runCatching { SignalOutcomeTracker.evaluateOpenSignals(symbol, candles) }

        val n = candles.size
        val sigIdx = n - 2 // แท่งปิดล่าสุด (แท่ง n-1 อาจกำลังวิ่ง)
        val sigTime = candles[sigIdx].timestamp

        // entry params ที่จูนแล้ว (EntryTuning) — ใช้แทนค่า default ของทุก kind ที่มี tuning
        val tunedEntry = runCatching {
            com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager()
                .getTunedEntryParams(symbol, tf)
        }.getOrElse { emptyMap() }
        if (tunedEntry.isNotEmpty()) {
            val tunedEntryKey = "$symbol/$tf"
            if (lastLoggedTunedEntryBar[tunedEntryKey] != sigTime) {
                lastLoggedTunedEntryBar[tunedEntryKey] = sigTime
                logDebug("SignalAlert", "$symbol/$tf ใช้ tuned entry params: ${tunedEntry.keys.joinToString(",")} bar=$sigTime")
            }
        }
        val edges = markerProvider.compute(candles, entryParams = tunedEntry).filter { it.time == sigTime }.toMutableList()

        // ── SMC (MT5 Engine) — ตรวจสัญญาณใหม่จากโครงสร้างตลาด/OB/FVG/Sweep/RSI divergence ──
        var smcNew = runCatching {
            com.example.personalaibot.automation.smc.SmcSignals.newSignalsAt(candles, sigIdx, symbol, tf)
        }.getOrElse { emptyList() }

        // ── Unified SMC (Multi-TF V5) — engine หลักตัวเดียว: H4/H1 context, M30/H1 trend,
        //    M15 setup (FVG/EQL/IDM + BOS/CHoCH), M5 confirmation — ประเมินเฉพาะ job 15m
        //    (base timeline ของ research) ⚠️ สถานะ research/forward-test (promotion_ready=false)
        var unified: com.example.personalaibot.automation.smc.UnifiedSmcSignals.UnifiedSignal? = null
        var uniBarTime = 0L
        var mtfDigest: com.example.personalaibot.automation.smc.MarketContextDigest.Digest? = null
        // Unified SMC ประเมินทุก job ไม่ว่า TF ไหน (base timeline = M15 เสมอ) — เดิม lock เฉพาะ job 15m
        // ทำให้ engine หลักเงียบสนิทถ้าผู้ใช้ไม่มี job 15m (candle DB cache ทำให้ fetch ซ้ำถูกมาก)
        // ดึง 5TF เสมอ: H1(→resample H4) context, M15 setup, M5+M1 confirmation — ตามสเปก 2026-08-28
        run {
            var m15c: List<Candle> = emptyList()
            val ures = runCatching {
                m15c = if (tf == "15m") candles else TradingSignalMarketDataRouter.fetch(symbol, "15m", 300) {
                    smcApi.fetchCandlesWithSource(symbol, "15m", 300).candles
                }.first
                val h1 = if (tf == "1h") candles else TradingSignalMarketDataRouter.fetch(symbol, "1h", 500) {
                    smcApi.fetchCandlesWithSource(symbol, "1h", 500).candles
                }.first
                val m5 = if (tf == "5m") candles else TradingSignalMarketDataRouter.fetch(symbol, "5m", 500) {
                    smcApi.fetchCandlesWithSource(symbol, "5m", 500).candles
                }.first
                val m1 = if (tf == "1m") candles else TradingSignalMarketDataRouter.fetch(symbol, "1m", 500) {
                    smcApi.fetchCandlesWithSource(symbol, "1m", 500).candles
                }.first
                val r = com.example.personalaibot.automation.smc.UnifiedSmcSignals.evaluate(h1, m15c, m5, m1)
                // โครงสร้างตลาด 5TF (deterministic — AI อ่าน digest แทนการเดาเอง)
                mtfDigest = runCatching {
                    com.example.personalaibot.automation.smc.MarketContextDigest.build(
                        symbol,
                        com.example.personalaibot.automation.smc.UnifiedSmcSignals.resample(h1, 240),
                        h1, m15c, m5, m1
                    )
                }.onFailure { logDebug("SignalAlert", "$symbol/$tf digest error: ${it.message}") }.getOrNull()
                r
            }.onFailure { logDebug("SignalAlert", "$symbol/$tf UnifiedSMC error: ${it.message}") }
                .getOrNull()
            unified = ures?.signal
            uniBarTime = if (m15c.size >= 2) m15c[m15c.size - 2].timestamp else 0L
            // heartbeat: log ครั้งเดียวต่อแท่ง M15 ปิด — พิสูจน์ว่า evaluator มีชีวิต + เห็นเหตุผลที่ HOLD
            val hbKey = "$symbol/UNIFIED_SMC"
            if (ures != null && uniBarTime != 0L && lastLoggedSignalBar[hbKey] != uniBarTime) {
                lastLoggedSignalBar[hbKey] = uniBarTime
                logDebug("SignalAlert", if (unified != null)
                    "$symbol/$tf UNIFIED_SMC ${unified!!.side} score=${"%.2f".format(unified!!.score)} entry=${unified!!.entry} (${unified!!.entryMode}) SL=${unified!!.sl} TP=${unified!!.tp} m15bar=$uniBarTime"
                else
                    "$symbol/$tf UNIFIED_SMC HOLD: ${ures.reason} m15bar=$uniBarTime")
            }
        }

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
        if (gateMgr != null && (edges.isNotEmpty() || smcNew.isNotEmpty() || unified != null)) {
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
            if (unified != null) {
                val uLevel = gateMgr.strategyGateLevel(symbol, tf, "UNIFIED_SMC")
                if (uLevel == com.example.personalaibot.automation.AutomationManager.StrategyGateLevel.BLOCK) {
                    gatedKinds = gatedKinds + "UNIFIED_SMC"
                    unified = null
                } else if (uLevel == com.example.personalaibot.automation.AutomationManager.StrategyGateLevel.WEAK) {
                    weakKinds = weakKinds + "UNIFIED_SMC"
                }
            }
            // Gate evaluation itself remains on every cycle so BLOCK/WEAK state is always current.
            // Only the diagnostic log is deduplicated per closed candle.
            val gateLogKey = "$symbol/$tf/gated=${gatedKinds.distinct().sorted().joinToString(",")}|weak=${weakKinds.distinct().sorted().joinToString(",")}"
            if ((gatedKinds.isNotEmpty() || weakKinds.isNotEmpty()) && lastLoggedGateBar[gateLogKey] != sigTime) {
                lastLoggedGateBar[gateLogKey] = sigTime
                if (gatedKinds.isNotEmpty()) {
                    logDebug("JarvisVM", "⛔ Gate บล็อก $symbol/$tf: ${gatedKinds.joinToString(",")} (backtest ล่าสุด PF<1/expectancy≤0) — ไม่ยิง alert bar=$sigTime")
                }
                if (weakKinds.isNotEmpty()) {
                    logDebug("JarvisVM", "⚠️ Gate WEAK $symbol/$tf: ${weakKinds.joinToString(",")} (ผ่านแบบหวุดหวิด — PF<1.25/avgR<0.1/WR<35%) bar=$sigTime")
                }
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

        // ── EMA 14/60 Detection: คำนวณการตัดกัน (Cross) และการบีบตัวเข้าหากัน (Near Cross) ──
        val ema14Series = ema(closes, 14)
        val ema60Series = ema(closes, 60)
        val e14Sig = ema14Series[sigIdx]
        val e60Sig = ema60Series[sigIdx]
        val e14SigPrev = ema14Series[max(0, sigIdx - 1)]
        val e60SigPrev = ema60Series[max(0, sigIdx - 1)]

        val emaCross = when {
            !e14Sig.isNaN() && !e60Sig.isNaN() && !e14SigPrev.isNaN() && !e60SigPrev.isNaN() -> when {
                e14SigPrev <= e60SigPrev && e14Sig > e60Sig -> "GOLDEN_CROSS"
                e14SigPrev >= e60SigPrev && e14Sig < e60Sig -> "DEATH_CROSS"
                else -> "NONE"
            }
            else -> "NONE"
        }
        val emaSpread = if (!e14Sig.isNaN() && !e60Sig.isNaN()) abs(e14Sig - e60Sig) else 0.0
        val emaSpreadPrev = if (!e14SigPrev.isNaN() && !e60SigPrev.isNaN()) abs(e14SigPrev - e60SigPrev) else emaSpread
        val isEmaConverging = emaSpread < emaSpreadPrev
        val emaNearThreshold = max(atr14 * 0.35, close * 0.0012)
        val isEmaNearCross = emaSpread <= emaNearThreshold && isEmaConverging && emaCross == "NONE"
        val emaNearCrossSide = when {
            isEmaNearCross && e14Sig < e60Sig && e14Sig >= e14SigPrev -> "BUY"
            isEmaNearCross && e14Sig > e60Sig && e14Sig <= e14SigPrev -> "SELL"
            else -> "NONE"
        }
        val emaState = if (e14Sig > e60Sig) "BULLISH" else "BEARISH"

        // ถ้าเกิด EMA 14/60 ตัดกันสดๆ บนแท่งนี้ ให้เพิ่ม edge ยืนยันสัญญาณ
        if (emaCross == "GOLDEN_CROSS" && edges.none { it.side == "BUY" } && unified == null) {
            edges += SignalMarkerProvider.SignalMarker(sigTime, "BUY", "E14/60▲", "#FFD54F")
        } else if (emaCross == "DEATH_CROSS" && edges.none { it.side == "SELL" } && unified == null) {
            edges += SignalMarkerProvider.SignalMarker(sigTime, "SELL", "E14/60▼", "#FFB74D")
        }

        val context = "trend=$trend | EMA14/60=$emaState(spread=${fmt(emaSpread)},cross=$emaCross${if (isEmaNearCross) ",nearCross=$emaNearCrossSide" else ""}) | RSI=${"%.1f".format(rsi)} | vsBB=${if (close > bbBasis + 2 * bbSd) "เหนือUpper" else if (close < bbBasis - 2 * bbSd) "ใต้Lower" else if (close > bbBasis) "โซนบน" else "โซนล่าง"} | ATR14=${fmt(atr14)} | DC20[${fmt(dcL)}-${fmt(dcU)}]"

        if (edges.isEmpty() && smcNew.isEmpty() && unified == null) {
            val anticipation = detectAnticipation(candles, sigIdx, atr14, mtfDigest, symbol)
            val isAnticipation = anticipation != null
            val stage = if (isAnticipation) "ANTICIPATION" else "NONE"
            val liveTime = candles.last().timestamp
            return mapOf(
                "signal_buy" to "0",
                "signal_sell" to "0",
                "signal_buy_id" to "0",
                "signal_sell_id" to "0",
                "signal_event" to "NONE",
                "signal_stage" to stage,
                "signal_anticipation" to if (isAnticipation) "1" else "0",
                "signal_anticipation_side" to (anticipation?.side ?: ""),
                "signal_anticipation_desc" to (anticipation?.reason ?: ""),
                "signal_anticipation_zone" to (anticipation?.zone ?: ""),
                "signal_anticipation_confidence" to (anticipation?.confidence?.toString() ?: "0"),
                "signal_anticipation_id" to if (isAnticipation) liveTime.toString() else "0",
                "signal_mix_score" to mixScore.toString(),
                "signal_mix_votes" to mixVoteDetail,
                "signal_gated" to gatedKinds.joinToString(","),
                "signal_weak" to weakKinds.joinToString(","),
                "signal_keyzone" to if (mtfDigest?.keyZoneHit != null) "1" else "0",
                "signal_keyzone_desc" to (mtfDigest?.keyZoneHit ?: ""),
                "signal_mtf_context" to (mtfDigest?.text ?: ""),
                "ema14" to fmt(e14Sig),
                "ema60" to fmt(e60Sig),
                "ema14_60_spread" to fmt(emaSpread),
                "ema14_60_state" to emaState,
                "ema14_60_cross" to emaCross,
                "ema14_60_near_cross" to if (isEmaNearCross) "1" else "0",
                "ema14_60_near_cross_side" to emaNearCrossSide,
                "close" to fmt(close),
                "signal_context" to context,
                "signal_data_source" to candleSource,
                "signal_data_source_reason" to candleSourceReason
            )
        }

        // ── มีสัญญาณใหม่: รวมทุก edge ของแท่งนี้ (Unified SMC + คลาสสิก + SMC) ──
        //    Unified SMC เป็น engine หลัก — ถ้ามี ให้เป็น primary เสมอ
        val sides = (listOfNotNull(unified?.side) + edges.map { it.side } + smcNew.map { it.side }).distinct()
        val side = sides.first() // สัญญาณหลัก (ถ้ามีหลายฝั่งพร้อมกัน — หายาก — ใช้ตัวแรก)
        val primaryClassic = edges.firstOrNull { it.side == side }
        val primarySmc = smcNew.firstOrNull { it.side == side }

        val kind: String
        val sl: Double
        val tp: Double
        val entryPrice: Double
        if (unified != null && unified!!.side == side) {
            // Unified SMC — entry = LIMIT @ FVG mid (หรือ MARKET @ next open), SL/TP จากโครงสร้าง
            kind = "UNIFIED_SMC"
            sl = unified!!.sl
            tp = unified!!.tp
            entryPrice = unified!!.entry
        } else if (primaryClassic != null) {
            kind = signalKindOf(primaryClassic.label) // MOM/TR/REV/DC/52H/E/UT/3BR

            // ใช้ tuned params จาก backtest (StrategyTuning) ถ้ามีและไม่ overfit — ไม่งั้นใช้สูตร default
            val tuning = runCatching {
                com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager()
                    .getStrategyTuning(symbol, tf, kind)
            }.getOrNull()?.takeIf { it.risk_gate_eligible != 0L && it.source == "evolve" && it.grade != "overfit" }
            val pair = if (tuning != null) {
                val tuningLogKey = "$symbol/$tf/$kind"
                if (lastLoggedTunedEntryBar[tuningLogKey] != sigTime) {
                    lastLoggedTunedEntryBar[tuningLogKey] = sigTime
                    logDebug("SignalAlert", "$symbol/$tf/$kind ใช้ tuned params sl=${tuning.sl_mult} tp=${tuning.tp_mult} (${tuning.source}, grade=${tuning.grade}) bar=$sigTime")
                }
                com.example.personalaibot.automation.backtest.parameterizedTpSl(
                    kind, side, candles, sigIdx, atr14, atr6,
                    com.example.personalaibot.automation.backtest.TpSlParams(tuning.sl_mult, tuning.tp_mult)
                )
            } else {
                computeTpSl(kind, side, candles, sigIdx, atr14, atr6)
            }
            sl = pair.first; tp = pair.second
            entryPrice = close
        } else {
            // สัญญาณ SMC (MT5 Engine) — SL/TP มาจากโครงสร้างตลาดของตัวเอง (ไม่ใช่ ATR multiple)
            kind = "SMC"
            sl = primarySmc!!.sl
            tp = primarySmc.tp
            entryPrice = close
        }
        val risk = abs(entryPrice - sl)
        val rr = if (risk > 0) abs(tp - entryPrice) / risk else 0.0
        // สัญญาณ Unified ผูกกับแท่ง M15 (ไม่ใช่แท่งของ job TF) — signal id ต้องเป็นเวลาแท่ง M15
        val effSigTime = if (kind == "UNIFIED_SMC" && uniBarTime != 0L) uniBarTime else sigTime

        val strategies = (listOfNotNull(unified?.let { strategyName("UNIFIED_SMC") }) +
            edges.map { strategyName(signalKindOf(it.label)) } +
            smcNew.map { smcStrategyName(it.strategy) }).joinToString(" + ")
        val reasons = (listOfNotNull(unified?.triggers?.joinToString(" ; ")) +
            edges.map { reasonFor(signalKindOf(it.label), it.side, tunedEntry[signalKindOf(it.label)]) } +
            smcNew.flatMap { it.triggers }).joinToString(" ; ")

        val signalLogKey = "$symbol/$tf/$side"
        val previousLoggedBar = lastLoggedSignalBar[signalLogKey]
        if (previousLoggedBar != sigTime) {
            lastLoggedSignalBar[signalLogKey] = sigTime
            logDebug("SignalAlert", "$symbol/$tf NEW $side signal: $strategies @ ${fmt(close)} SL=${fmt(sl)} TP=${fmt(tp)} source=$candleSource reason=$candleSourceReason bar=$sigTime")

            // ── Record confirmed signal to persistent SignalOutcomeTracker (บันทึกครั้งเดียวต่อแท่งสัญญาณใหม่) ──
            val signalId = "${symbol}_${tf}_${side}_${effSigTime}"
            runCatching {
                SignalOutcomeTracker.recordSignal(
                    signalId = signalId,
                    symbol = symbol,
                    interval = tf,
                    strategy = kind,
                    side = side,
                    entryPrice = entryPrice,
                    stopLoss = sl,
                    takeProfit = tp,
                    rr = rr,
                    createdAt = effSigTime
                )
            }
        }

        return mapOf(
            "signal_buy" to if (side == "BUY") "1" else "0",
            "signal_sell" to if (side == "SELL") "1" else "0",
            "signal_buy_id" to if (side == "BUY") effSigTime.toString() else "0",
            "signal_sell_id" to if (side == "SELL") effSigTime.toString() else "0",
            "signal_event" to side,
            "signal_stage" to "CONFIRMED",
            "signal_anticipation" to "0",
            "signal_anticipation_side" to "",
            "signal_anticipation_desc" to "",
            "signal_anticipation_zone" to "",
            "signal_anticipation_confidence" to "0",
            "signal_anticipation_id" to "0",
            "signal_strategy" to strategies,
            "signal_side" to side,
            "signal_entry" to fmt(entryPrice),
            "signal_sl" to fmt(sl),
            "signal_tp" to fmt(tp),
            "signal_rr" to "%.2f".format(rr),
            "signal_atr" to fmt(atr14),
            "signal_reason" to reasons,
            "signal_stars" to (unified?.stars?.toString() ?: primarySmc?.confluenceStars?.toString() ?: "0"),
            "signal_mix_score" to mixScore.toString(),
            "signal_mix_votes" to mixVoteDetail,
            "signal_gated" to gatedKinds.joinToString(","),
            "signal_weak" to weakKinds.joinToString(","),
            "signal_keyzone" to if (mtfDigest?.keyZoneHit != null) "1" else "0",
            "signal_keyzone_desc" to (mtfDigest?.keyZoneHit ?: ""),
            "signal_mtf_context" to (mtfDigest?.text ?: ""),
            "signal_context" to context,
            "ema14" to fmt(e14Sig),
            "ema60" to fmt(e60Sig),
            "ema14_60_spread" to fmt(emaSpread),
            "ema14_60_state" to emaState,
            "ema14_60_cross" to emaCross,
            "ema14_60_near_cross" to if (isEmaNearCross) "1" else "0",
            "ema14_60_near_cross_side" to emaNearCrossSide,
            "signal_data_source" to candleSource,
            "signal_data_source_reason" to candleSourceReason,
            "signal_bar_time" to effSigTime.toString(),
            "close" to fmt(close)
        )
    }

    // ─── Signal Anticipation & Pre-Alert Engine ───────────────────────────

    data class AnticipationSignal(
        val side: String,
        val setupType: String,
        val reason: String,
        val zone: String,
        val confidence: Int
    )

    data class AnticipationHit(
        val factorId: String,
        val side: String,
        val setupType: String,
        val reason: String,
        val zone: String,
        val confidence: Int
    )

    internal fun detectAnticipation(
        candles: List<Candle>,
        sigIdx: Int,
        atr14: Double,
        mtfDigest: com.example.personalaibot.automation.smc.MarketContextDigest.Digest? = null,
        symbol: String = ""
    ): AnticipationSignal? {
        if (candles.size < 15 || atr14 <= 0.0) return null
        val activeFactors = AnticipationConfigManager.getActiveFactors(symbol)
        if (activeFactors.isEmpty()) return null

        val liveIdx = candles.size - 1
        val live = candles[liveIdx]
        val closes = candles.map { it.close }
        val rsi14 = rsiSeries(closes, 14)[liveIdx]

        val hits = mutableListOf<AnticipationHit>()

        // 1. KEYZONE_PROXIMITY
        if ("KEYZONE_PROXIMITY" in activeFactors) {
            val kz = mtfDigest?.keyZoneHit
            if (!kz.isNullOrBlank()) {
                val isDemand = kz.contains("Support", ignoreCase = true) ||
                               kz.contains("Demand", ignoreCase = true) ||
                               kz.contains("Bullish", ignoreCase = true) ||
                               kz.contains("EQL", ignoreCase = true) ||
                               kz.contains("Discount", ignoreCase = true)
                val isSupply = kz.contains("Resistance", ignoreCase = true) ||
                               kz.contains("Supply", ignoreCase = true) ||
                               kz.contains("Bearish", ignoreCase = true) ||
                               kz.contains("EQH", ignoreCase = true) ||
                               kz.contains("Premium", ignoreCase = true)
                if (isDemand) {
                    hits.add(AnticipationHit(
                        factorId = "KEYZONE_PROXIMITY",
                        side = "BUY",
                        setupType = "KEYZONE_PROXIMITY",
                        reason = "ราคาลงมาทดสอบ Demand / Bullish Zone ($kz)",
                        zone = kz,
                        confidence = 78
                    ))
                } else if (isSupply) {
                    hits.add(AnticipationHit(
                        factorId = "KEYZONE_PROXIMITY",
                        side = "SELL",
                        setupType = "KEYZONE_PROXIMITY",
                        reason = "ราคาขึ้นมาทดสอบ Supply / Bearish Zone ($kz)",
                        zone = kz,
                        confidence = 78
                    ))
                }
            }
        }

        // 2. WICK_SWEEP_REJECTION
        if ("WICK_SWEEP_REJECTION" in activeFactors) {
            val body = abs(live.close - live.open)
            val lowerWick = min(live.open, live.close) - live.low
            val upperWick = live.high - max(live.open, live.close)
            val lookbackRange = max(0, liveIdx - 10) until liveIdx
            val priorLow = lookbackRange.minOfOrNull { candles[it].low } ?: live.low
            val priorHigh = lookbackRange.maxOfOrNull { candles[it].high } ?: live.high

            if (live.low < priorLow && lowerWick >= 1.5 * max(body, atr14 * 0.2)) {
                hits.add(AnticipationHit(
                    factorId = "WICK_SWEEP_REJECTION",
                    side = "BUY",
                    setupType = "WICK_SWEEP_REJECTION",
                    reason = "ราคา Sweep หลุด Low ย่อย (${"%.2f".format(priorLow)}) เกิดไส้ล่างปฏิเสธราคา (Wick Rejection)",
                    zone = "Low Sweep: ${"%.2f".format(live.low)} - ${"%.2f".format(priorLow)}",
                    confidence = 75
                ))
            }
            if (live.high > priorHigh && upperWick >= 1.5 * max(body, atr14 * 0.2)) {
                hits.add(AnticipationHit(
                    factorId = "WICK_SWEEP_REJECTION",
                    side = "SELL",
                    setupType = "WICK_SWEEP_REJECTION",
                    reason = "ราคา Sweep ทะลุ High ย่อย (${"%.2f".format(priorHigh)}) เกิดไส้บนปฏิเสธราคา (Wick Rejection)",
                    zone = "High Sweep: ${"%.2f".format(priorHigh)} - ${"%.2f".format(live.high)}",
                    confidence = 75
                ))
            }
        }

        // 3. RSI_EXTREME
        if ("RSI_EXTREME" in activeFactors) {
            if (rsi14 <= 28.0) {
                hits.add(AnticipationHit(
                    factorId = "RSI_EXTREME",
                    side = "BUY",
                    setupType = "RSI_EXTREME",
                    reason = "RSI เข้าเขต Oversold (${"%.1f".format(rsi14)}) กำลังสะสมแรงดีดตัวขึ้น",
                    zone = "Oversold Zone (RSI ${"%.1f".format(rsi14)})",
                    confidence = 70
                ))
            }
            if (rsi14 >= 72.0) {
                hits.add(AnticipationHit(
                    factorId = "RSI_EXTREME",
                    side = "SELL",
                    setupType = "RSI_EXTREME",
                    reason = "RSI เข้าเขต Overbought (${"%.1f".format(rsi14)}) กำลังสะสมแรงเทขาย",
                    zone = "Overbought Zone (RSI ${"%.1f".format(rsi14)})",
                    confidence = 70
                ))
            }
        }

        // 4. EMA_NEAR_CROSS
        if ("EMA_NEAR_CROSS" in activeFactors && candles.size >= 60) {
            val ema14Live = ema(closes, 14)
            val ema60Live = ema(closes, 60)
            val efNow = ema14Live[liveIdx]
            val esNow = ema60Live[liveIdx]
            val efPrev = ema14Live[liveIdx - 1]
            val esPrev = ema60Live[liveIdx - 1]
            if (!efNow.isNaN() && !esNow.isNaN() && !efPrev.isNaN() && !esPrev.isNaN()) {
                val spreadNow = abs(efNow - esNow)
                val spreadPrev = abs(efPrev - esPrev)
                val isConverging = spreadNow < spreadPrev
                val nearThresh = max(atr14 * 0.35, live.close * 0.0012)
                fun fmtP(v: Double) = if (abs(v) >= 100) "%.2f".format(v) else "%.4f".format(v)
                if (spreadNow <= nearThresh && isConverging) {
                    if (efNow < esNow && efNow >= efPrev) {
                        hits.add(AnticipationHit(
                            factorId = "EMA_NEAR_CROSS",
                            side = "BUY",
                            setupType = "EMA_NEAR_CROSS",
                            reason = "EMA14 (${fmtP(efNow)}) บีบตัวเข้าหา EMA60 (${fmtP(esNow)}) ระยะห่าง ${fmtP(spreadNow)} กำลังจะเกิด Golden Cross",
                            zone = "EMA Convergence: ${fmtP(efNow)} → ${fmtP(esNow)}",
                            confidence = 76
                        ))
                    } else if (efNow > esNow && efNow <= efPrev) {
                        hits.add(AnticipationHit(
                            factorId = "EMA_NEAR_CROSS",
                            side = "SELL",
                            setupType = "EMA_NEAR_CROSS",
                            reason = "EMA14 (${fmtP(efNow)}) บีบตัวเข้าหา EMA60 (${fmtP(esNow)}) ระยะห่าง ${fmtP(spreadNow)} กำลังจะเกิด Death Cross",
                            zone = "EMA Convergence: ${fmtP(efNow)} → ${fmtP(esNow)}",
                            confidence = 76
                        ))
                    }
                }
            }
        }

        // 5. BOLLINGER_SQUEEZE
        if ("BOLLINGER_SQUEEZE" in activeFactors && candles.size >= 20) {
            val bbPeriod = 20
            val bbSd = stdev(closes, bbPeriod, liveIdx)
            val bbBasis = closes.subList(max(0, liveIdx - bbPeriod + 1), liveIdx + 1).average()
            val bbUpper = bbBasis + 2 * bbSd
            val bbLower = bbBasis - 2 * bbSd
            val bbWidth = bbUpper - bbLower
            if (bbWidth <= 2.2 * atr14 && bbSd > 0) {
                if (live.close >= bbUpper - 0.25 * atr14) {
                    hits.add(AnticipationHit(
                        factorId = "BOLLINGER_SQUEEZE",
                        side = "BUY",
                        setupType = "BOLLINGER_SQUEEZE",
                        reason = "Bollinger Bands บีบตัวแคบ (Bandwidth ${"%.2f".format(bbWidth)}) และราคาดันชิดขอบบนเตรียม Breakout BUY",
                        zone = "Upper Band: ${"%.2f".format(bbUpper)}",
                        confidence = 74
                    ))
                } else if (live.close <= bbLower + 0.25 * atr14) {
                    hits.add(AnticipationHit(
                        factorId = "BOLLINGER_SQUEEZE",
                        side = "SELL",
                        setupType = "BOLLINGER_SQUEEZE",
                        reason = "Bollinger Bands บีบตัวแคบ (Bandwidth ${"%.2f".format(bbWidth)}) และราคาดันชิดขอบล่างเตรียม Breakout SELL",
                        zone = "Lower Band: ${"%.2f".format(bbLower)}",
                        confidence = 74
                    ))
                }
            }
        }

        // 6. MACD_HISTOGRAM_TURN
        if ("MACD_HISTOGRAM_TURN" in activeFactors && candles.size >= 35) {
            val ema12 = ema(closes, 12)
            val ema26 = ema(closes, 26)
            val macdLine = closes.indices.map { if (ema12[it].isNaN() || ema26[it].isNaN()) Double.NaN else ema12[it] - ema26[it] }
            val validMacdIndices = macdLine.mapIndexedNotNull { idx, v -> if (!v.isNaN()) idx to v else null }
            if (validMacdIndices.size >= 10) {
                val signalLine = ema(validMacdIndices.map { it.second }, 9)
                if (signalLine.size >= 2) {
                    val mNow = validMacdIndices.last().second
                    val mPrev = validMacdIndices[validMacdIndices.size - 2].second
                    val sNow = signalLine.last()
                    val sPrev = signalLine[signalLine.size - 2]
                    if (!sNow.isNaN() && !sPrev.isNaN()) {
                        val histNow = mNow - sNow
                        val histPrev = mPrev - sPrev
                        if (histPrev < 0 && histNow > histPrev && histNow > -0.6 * atr14) {
                            hits.add(AnticipationHit(
                                factorId = "MACD_HISTOGRAM_TURN",
                                side = "BUY",
                                setupType = "MACD_HISTOGRAM_TURN",
                                reason = "MACD Histogram หดตัวเงยหัวขึ้นจากแดนลบ (${"%.2f".format(histNow)}) เริ่มต้นรอบ Momentum ขาขึ้น",
                                zone = "MACD Turn (${"%.2f".format(histNow)})",
                                confidence = 72
                            ))
                        } else if (histPrev > 0 && histNow < histPrev && histNow < 0.6 * atr14) {
                            hits.add(AnticipationHit(
                                factorId = "MACD_HISTOGRAM_TURN",
                                side = "SELL",
                                setupType = "MACD_HISTOGRAM_TURN",
                                reason = "MACD Histogram หดตัวปักหัวลงจากแดนบวก (${"%.2f".format(histNow)}) เริ่มต้นรอบ Momentum ขาลง",
                                zone = "MACD Turn (${"%.2f".format(histNow)})",
                                confidence = 72
                            ))
                        }
                    }
                }
            }
        }

        // 7. VOLUME_ABSORPTION
        if ("VOLUME_ABSORPTION" in activeFactors && candles.size >= 25 && live.volume > 0.0) {
            val volWindow = candles.subList(max(0, liveIdx - 20), liveIdx)
            val avgVol = if (volWindow.isNotEmpty()) volWindow.map { it.volume }.average() else 0.0
            val body = abs(live.close - live.open)
            if (avgVol > 0.0 && live.volume >= 1.8 * avgVol && body <= 0.35 * atr14) {
                if (live.close <= live.open) {
                    hits.add(AnticipationHit(
                        factorId = "VOLUME_ABSORPTION",
                        side = "BUY",
                        setupType = "VOLUME_ABSORPTION",
                        reason = "เกิด Volume Absorption สูง ${"%.1f".format(live.volume / avgVol)}x เท่าที่สเปรดแคบ (Smart Money ซุ่มดูดซับแรงขาย)",
                        zone = "Absorption Base: ${"%.2f".format(live.low)}",
                        confidence = 77
                    ))
                } else {
                    hits.add(AnticipationHit(
                        factorId = "VOLUME_ABSORPTION",
                        side = "SELL",
                        setupType = "VOLUME_ABSORPTION",
                        reason = "เกิด Volume Absorption สูง ${"%.1f".format(live.volume / avgVol)}x เท่าที่สเปรดแคบ (Smart Money ระบายของ/ดูดซับแรงซื้อ)",
                        zone = "Absorption Ceiling: ${"%.2f".format(live.high)}",
                        confidence = 77
                    ))
                }
            }
        }

        // 8. FIBONACCI_GOLDEN_POCKET
        if ("FIBONACCI_GOLDEN_POCKET" in activeFactors && candles.size >= 30) {
            val swingCandles = candles.takeLast(30)
            val swingHigh = swingCandles.maxOf { it.high }
            val swingLow = swingCandles.minOf { it.low }
            val swingRange = swingHigh - swingLow
            if (swingRange >= 2.0 * atr14) {
                val fib618Buy = swingHigh - 0.618 * swingRange
                val fib650Buy = swingHigh - 0.650 * swingRange
                if (live.low <= fib618Buy && live.close >= fib650Buy - 0.1 * atr14) {
                    hits.add(AnticipationHit(
                        factorId = "FIBONACCI_GOLDEN_POCKET",
                        side = "BUY",
                        setupType = "FIBONACCI_GOLDEN_POCKET",
                        reason = "ราคาย่อตัวลงมาแตะแนวรับ Fibonacci Golden Pocket 0.618-0.65 (${"%.2f".format(fib650Buy)} - ${"%.2f".format(fib618Buy)})",
                        zone = "Golden Pocket: ${"%.2f".format(fib650Buy)} - ${"%.2f".format(fib618Buy)}",
                        confidence = 75
                    ))
                }
                val fib618Sell = swingLow + 0.618 * swingRange
                val fib650Sell = swingLow + 0.650 * swingRange
                if (live.high >= fib618Sell && live.close <= fib650Sell + 0.1 * atr14) {
                    hits.add(AnticipationHit(
                        factorId = "FIBONACCI_GOLDEN_POCKET",
                        side = "SELL",
                        setupType = "FIBONACCI_GOLDEN_POCKET",
                        reason = "ราคาดีดตัวขึ้นมาแตะแนวต้าน Fibonacci Golden Pocket 0.618-0.65 (${"%.2f".format(fib618Sell)} - ${"%.2f".format(fib650Sell)})",
                        zone = "Golden Pocket: ${"%.2f".format(fib618Sell)} - ${"%.2f".format(fib650Sell)}",
                        confidence = 75
                    ))
                }
            }
        }

        // 9. STOCHASTIC_OVERSOLD_TURN
        if ("STOCHASTIC_OVERSOLD_TURN" in activeFactors && candles.size >= 20) {
            val stochPeriod = 14
            val kValues = mutableListOf<Double>()
            for (idx in (liveIdx - 5)..liveIdx) {
                if (idx < stochPeriod - 1) continue
                val window = candles.subList(idx - stochPeriod + 1, idx + 1)
                val h = window.maxOf { it.high }
                val l = window.minOf { it.low }
                val k = if (h > l) ((candles[idx].close - l) / (h - l)) * 100.0 else 50.0
                kValues.add(k)
            }
            if (kValues.size >= 3) {
                val kNow = kValues.last()
                val kPrev = kValues[kValues.size - 2]
                if (kPrev < 22.0 && kNow > kPrev && kNow >= 20.0) {
                    hits.add(AnticipationHit(
                        factorId = "STOCHASTIC_OVERSOLD_TURN",
                        side = "BUY",
                        setupType = "STOCHASTIC_OVERSOLD_TURN",
                        reason = "Stochastic (%K=${"%.1f".format(kNow)}) ตัดเงยหัวขึ้นจากเขต Oversold (<20)",
                        zone = "Stoch Oversold (${"%.1f".format(kNow)})",
                        confidence = 71
                    ))
                } else if (kPrev > 78.0 && kNow < kPrev && kNow <= 80.0) {
                    hits.add(AnticipationHit(
                        factorId = "STOCHASTIC_OVERSOLD_TURN",
                        side = "SELL",
                        setupType = "STOCHASTIC_OVERSOLD_TURN",
                        reason = "Stochastic (%K=${"%.1f".format(kNow)}) ตัดปักหัวลงจากเขต Overbought (>80)",
                        zone = "Stoch Overbought (${"%.1f".format(kNow)})",
                        confidence = 71
                    ))
                }
            }
        }

        // 10. SESSION_OPEN_SWEEP
        if ("SESSION_OPEN_SWEEP" in activeFactors && candles.size >= 40) {
            val sessionWindow = candles.takeLast(24)
            val sHigh = sessionWindow.dropLast(1).maxOf { it.high }
            val sLow = sessionWindow.dropLast(1).minOf { it.low }
            if (live.low < sLow && live.close > sLow) {
                hits.add(AnticipationHit(
                    factorId = "SESSION_OPEN_SWEEP",
                    side = "BUY",
                    setupType = "SESSION_OPEN_SWEEP",
                    reason = "ราคา Sweep กวาดสภาพคล่องหลุด Session Low (${"%.2f".format(sLow)}) แล้วดีดกลับขึ้นมาอย่างรวดเร็ว",
                    zone = "Session Low Sweep: ${"%.2f".format(sLow)}",
                    confidence = 79
                ))
            } else if (live.high > sHigh && live.close < sHigh) {
                hits.add(AnticipationHit(
                    factorId = "SESSION_OPEN_SWEEP",
                    side = "SELL",
                    setupType = "SESSION_OPEN_SWEEP",
                    reason = "ราคา Sweep กวาดสภาพคล่องทะลุ Session High (${"%.2f".format(sHigh)}) แล้วถูกกดกลับลงมาอย่างรวดเร็ว",
                    zone = "Session High Sweep: ${"%.2f".format(sHigh)}",
                    confidence = 79
                ))
            }
        }

        if (hits.isEmpty()) return null

        // ── Confluence Multi-Factor Synthesis ──
        val buyHits = hits.filter { it.side == "BUY" }
        val sellHits = hits.filter { it.side == "SELL" }

        val dominantHits = when {
            buyHits.size > sellHits.size -> buyHits
            sellHits.size > buyHits.size -> sellHits
            else -> if ((buyHits.maxOfOrNull { it.confidence } ?: 0) >= (sellHits.maxOfOrNull { it.confidence } ?: 0)) buyHits else sellHits
        }

        if (dominantHits.isEmpty()) return null

        val dominantSide = dominantHits.first().side
        val baseConf = dominantHits.maxOf { it.confidence }
        val finalConfidence = when (dominantHits.size) {
            1 -> baseConf
            2 -> max(80, baseConf + 5)
            3 -> max(88, baseConf + 10)
            else -> min(96, baseConf + 15)
        }

        val setupType = if (dominantHits.size > 1) {
            "CONFLUENCE_${dominantHits.size}F"
        } else {
            dominantHits.first().setupType
        }

        val combinedReason = if (dominantHits.size > 1) {
            "⚡ [Confluence ${dominantHits.size} ปัจจัย]: " + dominantHits.joinToString(" ; ") { it.reason }
        } else {
            dominantHits.first().reason
        }

        val primaryZone = dominantHits.first().zone

        return AnticipationSignal(
            side = dominantSide,
            setupType = setupType,
            reason = combinedReason,
            zone = primaryZone,
            confidence = finalConfidence
        )
    }

    // ─── TP/SL เฉพาะกลยุทธ์ (ออกแบบตามพฤติกรรมของแต่ละตัว) ─────────────────

    /**
     * SL/TP เฉพาะกลยุทธ์ — SINGLE SOURCE OF TRUTH คือ parameterizedTpSl (StrategyParams.kt)
     * ที่นี่เป็น wrapper ส่งค่า default เข้าไป (กรณีไม่มี tuned params) เพื่อการันตี live == backtest
     * (2026-08-20: strategy-native SL/TP — swing/Donchian band/BB basis แทน ATR multiple ล้วน)
     */
    internal fun computeTpSl(
        kind: String, side: String, candles: List<Candle>, i: Int, atr14: Double, atr6: Double
    ): Pair<Double, Double> = com.example.personalaibot.automation.backtest.parameterizedTpSl(
        kind, side, candles, i, atr14, atr6,
        com.example.personalaibot.automation.backtest.TpSlParams.defaultsFor(kind)
    )

    internal fun strategyName(kind: String): String = when (kind) {
        "UNIFIED_SMC" -> "Unified SMC (Multi-TF V5 · research)"
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
            "52H" -> if (up) "ราคาเข้าใกล้จุดสูงสุด rolling ${ep?.w52Lookback ?: 2000} แท่ง (≥${((ep?.w52ProxBuy ?: 0.98) * 100).toInt()}% — momentum แรงต่อเนื่อง)" else "ราคาหลุด ${((ep?.w52ProxSell ?: 0.90) * 100).toInt()}% จากจุดสูงสุด rolling ${ep?.w52Lookback ?: 2000} แท่ง (โมเมนตัมเสีย)"
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
            else -> SignalMarkerProvider.ENABLED_KINDS  // default = ตัวที่มี edge เท่านั้น (MOM/REV)
        }

        val tunedEntry = runCatching {
            com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager()
                .getTunedEntryParams(symbol, tf)
        }.getOrElse { emptyMap() }
        val markers = markerProvider.compute(candles, maxPerKind = Int.MAX_VALUE, entryParams = tunedEntry, kindsOverride = kindFilter)
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
        out[period] = if (avgGain == 0.0 && avgLoss == 0.0) 50.0 else if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        for (i in period + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + max(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-d, 0.0)) / period
            out[i] = if (avgGain == 0.0 && avgLoss == 0.0) 50.0 else if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
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

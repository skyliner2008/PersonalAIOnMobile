package com.skyliner2008.jarvis.automation.wake

import com.skyliner2008.jarvis.automation.IndicatorAlertProvider
import com.skyliner2008.jarvis.automation.smc.MarketContextDigest
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.tools.trading.AdvancedTradingEngine
import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.ElliotWaveModern
import com.skyliner2008.jarvis.tools.trading.HarmonicPattern
import com.skyliner2008.jarvis.tools.trading.MarketHours
import com.skyliner2008.jarvis.tools.trading.ModernTechnicalApiService
import com.skyliner2008.jarvis.tools.trading.SmcApiService
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import com.skyliner2008.jarvis.tools.trading.TradingApiService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

/**
 * AnticipationEngine — ระบบปลุก AI (คาดการณ์ล่วงหน้า)
 *
 * **แยกจากระบบแจ้งเตือน Signal (strategies / backtest / UnifiedSMC) โดยสิ้นเชิง**
 * ใช้ร่วมกันเฉพาะชั้นข้อมูล: OHLCV store, TaIndicators, MarketContextDigest
 *
 * เดิมระบบคาดการณ์ฝังอยู่ใน SignalAlertProvider.fetch():
 *  - alert คาดการณ์ 1 job = รันเครื่องยนต์ strategy ทั้งหมดทุกนาที ทั้งที่ไม่ได้ใช้
 *  - ถูก gate ด้วย "ต้องไม่มีสัญญาณยืนยันอยู่" → ตลาดยิ่งคึกคัก ตัวปลุกยิ่งเงียบ
 *  - ใช้ tool_name / ตาราง / payload ร่วมกับระบบ signal จนแยกไม่ออก
 *
 * ขั้นตอนต่อรอบ:
 *  1. ดึง 5TF (M1/M5/M15/H1/H4) + ข้อมูลเสริม (intermarket, ข่าว, Fear&Greed, deep score)
 *  2. สร้างภาพตลาด (MarketContextDigest) — สิ่งที่ AI จะเห็นเมื่อตื่น
 *  3. สแกนปัจจัยทั้งหมดใน [WakeTriggerRegistry]
 *  4. คัด cooldown / transition → บันทึกเข้าการเรียนรู้ (ฟรี ไม่ใช้โทเคน)
 *  5. ตัดสินว่าจะปลุกไหม ตามงบ ([WakeGovernor]) — ปัจจัยที่ถูกลดชั้นไม่นับ
 *  6. ปิดผลการเรียนรู้ของรอบก่อนๆ ที่ครบกรอบเวลาแล้ว
 */
class AnticipationEngine(
    private val smcApi: SmcApiService,
    private val tradingApi: TradingApiService
) {
    companion object {
        const val TOOL_NAME = "trading_anticipation"

        // ── keys ของ payload ──
        const val K_WAKE = "wake"
        const val K_WAKE_ID = "wake_id"
        const val K_SIGNAL_ID = "wake_signal_id"
        const val K_EVENTS = "wake_events"
        const val K_STATES = "wake_states"
        const val K_TRIGGERS = "wake_triggers"
        const val K_EVENT_COUNT = "wake_event_count"
        const val K_BUY = "wake_buy"
        const val K_SELL = "wake_sell"
        const val K_SUPPRESSED = "wake_suppressed"
        const val K_ERRORS = "wake_errors"
        const val K_MTF = "mtf_context"
        /** H4/H1/M15 เรียงทิศเดียวกัน: UP / DOWN / (ว่าง) — prompt ห้ามแจ้งสวนทิศนี้ */
        const val K_HTF_ALIGN = "wake_htf_align"
        const val K_CLOSE = "close"
        const val K_SYMBOL = "symbol"
        const val K_TIMEFRAME = "timeframe"
        const val K_TIME = "wake_time"
        /** "1" = ตลาดปิด — ข้ามงานทั้งหมด (ไม่ดึงแท่งเทียน ไม่สแกน ไม่ปลุก AI) */
        const val K_MARKET_CLOSED = "market_closed"
        /** ATR ของ TF หลัก ณ ตอนตรวจพบ — ใช้บันทึกมุมมองของ AI */
        const val K_ATR = "wake_atr"
        /** มุมมองที่ AI เคยให้บน symbol นี้ + ผลล่าสุด (ส่งเข้า prompt) */
        const val K_PREV_VIEWS = "wake_prev_views"

        /** ไม่ปลุก AI ในช่วงท้ายก่อนตลาดปิดสิ้นสัปดาห์ (สภาพคล่องบาง ตั้งสถานะไม่ทัน) — ยังสแกนและเก็บการเรียนรู้ */
        const val PRE_CLOSE_MINUTES = 60L

        /** สถานะตลาดล่าสุดต่อ series — ใช้ log เฉพาะตอนเปลี่ยน (เปิด↔ปิด) ไม่ให้ log ถี่ทุกนาที */
        private val lastMarketOpen = mutableMapOf<String, Boolean>()

        /** ตลาดที่เกี่ยวข้อง (key → TradingView symbol) — ทอง/FX → DXY + US10Y, คริปโต → BTC + BTC.D */
        fun intermarketSymbolsFor(symbol: String): Map<String, String> = when {
            TaIndicators.sessionOffsetHoursFor(symbol) != 0 -> mapOf("DXY" to "TVC:DXY", "US10Y" to "TVC:US10Y")
            looksCryptoSymbol(symbol) -> mapOf("BTC" to "BTCUSDT", "BTC.D" to "CRYPTOCAP:BTC.D")
            else -> emptyMap()
        }

        private fun looksCryptoSymbol(symbol: String): Boolean {
            val s = symbol.uppercase().substringBefore("@")
            return s.endsWith("USDT") || s.startsWith("BTC") || s.startsWith("ETH")
        }

        /** field ที่ alert job ของระบบนี้ใช้ได้ */
        val SUPPORTED_FIELDS = setOf(K_WAKE, K_WAKE_ID, K_EVENT_COUNT, K_BUY, K_SELL, K_CLOSE)

        private val SUPPORTED_TF = setOf("1m", "5m", "15m", "30m", "1h", "4h")

        /**
         * สแกนทีละรอบทั้ง process — สถานะที่แชร์ (governor, memory, แคช) ถูกอ่าน-แก้-เขียน
         * ระหว่างรอบ ถ้า alert เบื้องหลังกับการสแกนจากแชทรันพร้อมกันจะชนกัน
         */
        private val scanLock = Mutex()
        private const val MAX_EVENT_LINES = 20
        private const val MAX_STATE_LINES = 16

        private const val INTERMARKET_TTL_MS = 5 * 60_000L
        private const val MACRO_TTL_MS = 30 * 60_000L
        private const val FNG_TTL_MS = 60 * 60_000L
        private const val DEEP_TTL_MS = 5 * 60_000L

        // แคชระดับ process (engine อาจถูกสร้างหลาย instance)
        private val interCache = mutableMapOf<String, Pair<Long, List<Candle>>>()
        private var macroCache: Pair<Long, List<TradingApiService.MacroEvent>>? = null
        private var fngCache: Pair<Long, Int?>? = null
        private val deepCache = mutableMapOf<String, Pair<Long, Int?>>()
        private val patternCache = mutableMapOf<String, Triple<Long, List<HarmonicPattern>, ElliotWaveModern?>>()
    }

    private val advanced by lazy { AdvancedTradingEngine(smcApi) }
    private val modern by lazy { ModernTechnicalApiService(smcApi) }

    /**
     * สแกน 1 รอบสำหรับ "SYMBOL@TF" (default TF = 15m)
     *
     * @param preview true = สแกนดูอย่างเดียว (จากแชท) — ไม่บันทึกการเรียนรู้ ไม่ใช้งบ/cooldown
     *   ไม่เขียน memory และไม่ปลุก AI (เดิมการสแกนด้วยมือกิน cooldown และ "การปลุกของแท่งนั้น"
     *   ไปก่อน alert เบื้องหลังจึงเงียบทั้งที่มีเหตุการณ์)
     */
    suspend fun scan(rawSymbol: String, preview: Boolean = false): Map<String, String> =
        scanLock.withLock { scanLocked(rawSymbol, preview) }

    private suspend fun scanLocked(rawSymbol: String, preview: Boolean): Map<String, String> {
        val (symbol, tfRaw) = IndicatorAlertProvider.splitSymbolAndTf(rawSymbol)
        val tfNorm = TaIndicators.normalizeTimeframe(tfRaw)
        val tf = if (tfNorm in SUPPORTED_TF) tfNorm else {
            logDebug("WakeEngine", "$rawSymbol: TF $tfNorm ไม่รองรับ → ใช้ 15m")
            "15m"
        }
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val seriesKey = "$symbol@$tf"

        // ── 0) ตลาดปิด → ข้ามทั้งหมด (ไม่ดึงแท่งเทียน ไม่สแกน ไม่เรียก AI) ─────
        val market = MarketHours.status(symbol, nowMs)
        if (lastMarketOpen.put(seriesKey, market.open) != market.open) {
            logDebug("WakeEngine", if (market.open) "▶ $seriesKey ตลาดเปิด — เริ่มสแกน" else "⏸ $seriesKey ${market.reasonTh} — ข้ามการสแกน")
        }
        if (!market.open) {
            return buildMap {
                put(K_SYMBOL, symbol); put(K_TIMEFRAME, tf)
                put(K_WAKE, "0"); put(K_WAKE_ID, "0")
                put(K_EVENT_COUNT, "0"); put(K_BUY, "0"); put(K_SELL, "0")
                put(K_MARKET_CLOSED, "1")
                put(K_SUPPRESSED, market.reasonTh ?: "ตลาดปิด")
                put(K_TIME, timeLine(nowMs))
                market.nextOpenMs?.let { put("market_next_open", timeLine(it)) }
            }
        }
        val preClose = market.minutesToWeeklyClose?.let { it <= PRE_CLOSE_MINUTES } == true

        // ── 1) ข้อมูล 5TF (+ TF หลักถ้าอยู่นอกชุด) ─────────────────────────
        // ทุก TF ใช้ FULL_SET — ปัจจัยถูกประเมินบนทุก TF แล้ว (เดิม M1 300 / M5 500 / H4 400 แท่ง พอแค่ภาพตลาด)
        val full = TaIndicators.Warmup.FULL_SET
        suspend fun fetch(t: String) =
            runCatching { smcApi.fetchCandlesWithSource(symbol, t, full).candles }.getOrElse { emptyList() }
        val m1 = fetch("1m")
        val m5 = fetch("5m")
        val m15 = fetch("15m")
        val h1 = fetch("1h")
        val h4 = fetch("4h")
        val m30 = if (tf == "30m") fetch("30m") else emptyList()

        val primaryAll = when (tf) { "1m" -> m1; "5m" -> m5; "30m" -> m30; "1h" -> h1; "4h" -> h4; else -> m15 }
        val primaryClosed = primaryAll.filter { it.isClosed }
        if (primaryClosed.size < 60) {
            return mapOf(
                "error" to "แท่งเทียน $tf ไม่พอ (${primaryClosed.size})",
                K_SYMBOL to symbol, K_TIMEFRAME to tf, K_WAKE to "0"
            )
        }
        val barTs = primaryClosed.last().timestamp
        // รหัสการสแกน = นาทีที่สแกน — การปลุกเกิดได้ทุกนาที (เดิมผูกกับแท่ง TF หลัก ปลุกได้ครั้งเดียวต่อ 15 นาที)
        val scanTs = nowMs - nowMs % 60_000L

        // ── 2) ภาพตลาด + ข้อมูลเสริม ──────────────────────────────────────
        val digest = runCatching {
            MarketContextDigest.build(symbol, h4, h1, m15, m5, m1, closedAware = true, tradeRefs = false)
        }.getOrNull()
        val memory = WakeSettings.loadMemory(seriesKey)
        if (!preview) WakeGovernor.seed(seriesKey, memory)
        val (harmonics, elliott) = patterns(seriesKey, barTs, primaryClosed)
        val ctx = WakeContext(
            symbol = symbol, primaryTf = tf,
            m1 = m1, m5 = m5, m15 = m15, h1 = h1, h4 = h4,
            digest = digest,
            intermarket = intermarketFor(symbol, nowMs),
            macroEvents = macroEvents(nowMs),
            fearGreed = if (looksCrypto(symbol)) fearGreed(nowMs) else null,
            deepScore = deepScore(symbol, tf, nowMs),
            harmonics = harmonics,
            elliott = elliott,
            memory = memory,
            nowMs = nowMs,
            extra = if (m30.isNotEmpty()) mapOf("30m" to m30) else emptyMap()
        )

        // ── 3) สแกนทุกปัจจัยบนทุก TF ─────────────────────────────────────────
        val evalTfs = (WakeTfProfile.EVAL_TFS + tf).distinct()
        val scan = WakeTriggerRegistry.scanAllTf(ctx, evalTfs, WakeSettings.enabledIds())
        if (scan.errors.isNotEmpty()) logDebug("WakeEngine", "$seriesKey trigger errors: ${scan.errors.take(5)}")

        // ── 4) คัด + บันทึกการเรียนรู้ (ทุกปัจจัย × ทุก TF — ฟรี ไม่ใช้โทเคน) ─────
        val barTsByTf = evalTfs.associateWith { ctx.series(it).last?.timestamp ?: 0L }.filterValues { it > 0 }
        val freshEvents = WakeGovernor.freshEvents(seriesKey, scan.events, barTsByTf, commit = !preview)
        val prevActive = memory["active_states"]?.split(",")?.filter { it.isNotBlank() }
            // รุ่นก่อน P16 เก็บ "ID:DIR" (เกิดบน TF หลักเสมอ)
            ?.map { if ('@' in it) it else it.substringBefore(':') + "@" + tf + ":" + it.substringAfter(':') }
            ?.toSet().orEmpty()
        val newStates = scan.states.filter { stateKey(it) !in prevActive }

        val signalId = "$symbol|$tf|$scanTs"
        // ราคาอ้างอิง = ราคา ณ ตอนตรวจพบจริง (ไม่ใช่ราคาปิดของแท่ง TF หลักที่ปิดไปแล้ว)
        val refPrice = ctx.price.takeIf { it > 0 } ?: primaryClosed.last().close
        val refAtr = ctx.atr
        if (!preview && refPrice > 0) {
            WakeLearningStore.record((freshEvents + newStates).mapNotNull { e ->
                val c = ctx.forTf(e.tf)
                val atr = c.atr.takeIf { it > 0 } ?: return@mapNotNull null
                WakeLearningStore.Record(
                    signalId = signalId, factorId = e.triggerId, symbol = symbol, interval = tf, factorTf = e.tf,
                    side = e.direction, kind = WakeTriggerRegistry.find(e.triggerId)?.kind?.name ?: "EVENT",
                    refPrice = refPrice, refAtr = atr,
                    context = learningContext(c, e.direction), createdAt = nowMs
                )
            })
        }

        // ── 5) ตัดสินการปลุก ──────────────────────────────────────────────
        // ปลุกได้เฉพาะปัจจัย × TF ที่เหมาะ (ชุดเริ่มต้น + ผลจริง) — ที่เหลือเก็บสถิติอย่างเดียว
        val wakingEvents = freshEvents.filter { WakeLearningStore.tfVerdict(it.triggerId, it.tf, tf).wakes }
        val hasCarried = !preview && WakeGovernor.hasDeferred(seriesKey, nowMs)
        var wake = false
        var suppressed: String? = null
        var carried: List<Pair<TriggerEvent, Long>> = emptyList()
        if (wakingEvents.isNotEmpty() || hasCarried) {
            val d = WakeGovernor.decide(seriesKey, symbol, nowMs)
            when {
                !d.allowed -> {
                    suppressed = d.reason
                    // ไม่ทิ้ง — พกไปแสดงในการปลุกครั้งถัดไป (ไม่เกิน CARRY_MS)
                    if (!preview) WakeGovernor.defer(seriesKey, wakingEvents, nowMs)
                }
                preClose -> suppressed = "อีก ${market.minutesToWeeklyClose} นาทีตลาดปิดสิ้นสัปดาห์ — ไม่ปลุก AI"
                preview -> suppressed = "สแกนดูอย่างเดียว (ไม่ปลุก AI)"
                else -> {
                    wake = true
                    WakeGovernor.register(seriesKey, symbol, nowMs)
                    carried = WakeGovernor.takeDeferred(seriesKey, nowMs).filter { (c, _) ->
                        wakingEvents.none { it.triggerId == c.triggerId && it.tf == c.tf && it.direction == c.direction }
                    }
                    WakeLearningStore.markWoke(signalId, wakingEvents.map { WakeLearningStore.key(it.triggerId, it.tf) })
                }
            }
        }

        if (!preview) {
            // ── 6) ปิดผลการเรียนรู้ที่ครบกรอบ (แต่ละแถววัดด้วยแท่งของ TF ที่มันเกิด) ──
            runCatching { WakeLearningStore.resolvePending(symbol, tf, evalTfs.associateWith { ctx.series(it).bars }) }
            // ── ติดตามผลมุมมองของ AI (ชน TP/SL หรือยัง) ──
            runCatching { AiViewTracker.backfillOnce() }
            runCatching { AiViewTracker.resolve(symbol, tf, primaryClosed, ctx.m1.bars) }
            // ── ความจำสำหรับรอบถัดไป (รวมสถานะ governor ให้รอดการรีสตาร์ท) ──
            WakeSettings.saveMemory(seriesKey, nextMemory(ctx, memory, scan) + WakeGovernor.exportState(seriesKey, nowMs))
        }

        // ── payload ────────────────────────────────────────────────────────
        fun eventLine(e: TriggerEvent, agoMs: Long? = null): String {
            val c = ctx.forTf(e.tf)
            val verdict = WakeLearningStore.tfVerdict(e.triggerId, e.tf, tf)
            val dir = if (e.direction == "NEUTRAL") "" else " → ชี้ ${e.direction}"
            val stat = WakeLearningStore.describeForAi(e.triggerId, e.tf, learningContext(c, e.direction))
            val mark = when (verdict) {
                WakeLearningStore.TfVerdict.DEMOTED -> " · ⬇️ สถิติบน TF นี้ไม่ดี"
                WakeLearningStore.TfVerdict.PROMOTED -> " · ⬆️ สถิติบน TF นี้ดี"
                else -> ""
            }
            val ago = agoMs?.let { " (เกิดเมื่อ ${(it / 60_000L).coerceAtLeast(1)} นาทีก่อน)" } ?: ""
            return "• [${tfLabel(e.tf)}] ${e.what}$dir$ago  (${e.triggerId} · สถิติ ${tfLabel(e.tf)}: $stat$mark)"
        }
        // เรียง TF ใหญ่ก่อน (H4 → M1) — AI อ่านบริบทใหญ่ก่อนจังหวะ
        fun rank(t: String) = -TaIndicators.timeframeMillis(t)
        val lines: List<String> = if (wake) {
            (wakingEvents.map { it to null } + carried.map { (e, at) -> e to (nowMs - at) })
                .sortedBy { rank(it.first.tf) }.take(MAX_EVENT_LINES).map { (e, ago) -> eventLine(e, ago) }
        } else {
            // สแกนดู / ไม่ได้ปลุก: เหตุการณ์ที่ปลุกได้ทั้งหมดบนแท่งล่าสุดของแต่ละ TF
            scan.events.filter { WakeLearningStore.tfVerdict(it.triggerId, it.tf, tf).wakes }
                .sortedBy { rank(it.tf) }.take(MAX_EVENT_LINES).map { eventLine(it) }
        }
        val triggerKeys = if (wake) (wakingEvents + carried.map { it.first }) else
            scan.events.filter { WakeLearningStore.tfVerdict(it.triggerId, it.tf, tf).wakes }
        // ตัวนับที่ใช้เป็นเงื่อนไข alert ได้ ต้องเป็น 0 เมื่อไม่ได้ปลุก
        // (เดิมนับเหตุการณ์ที่ติดงบด้วย → alert แบบ wake_event_count/wake_buy ยิงทุกแท่งและเรียก AI เกินงบ)
        val countedEvents = if (wake) wakingEvents + carried.map { it.first } else emptyList()
        // สภาวะ: เฉพาะ TF ที่เหมาะกับปัจจัยนั้น เรียง TF ใหญ่ก่อน (ทุก TF รวมกันยาวเกินและเป็น noise)
        val shownStates = scan.states.filter { WakeTfProfile.isDefaultWakeTf(it.triggerId, it.tf, tf) }
            .sortedBy { rank(it.tf) }.take(MAX_STATE_LINES)
        return buildMap {
            put(K_SYMBOL, symbol)
            put(K_TIMEFRAME, tf)
            put(K_CLOSE, p(refPrice))
            put(K_ATR, refAtr.toString())
            runCatching { AiViewTracker.promptSection(symbol, refPrice, nowMs) }.getOrNull()?.let { put(K_PREV_VIEWS, it) }
            put(K_TIME, timeLine(nowMs))
            put(K_WAKE, if (wake) "1" else "0")
            put(K_WAKE_ID, if (wake) scanTs.toString() else "0")
            put(K_SIGNAL_ID, signalId)
            put(K_EVENT_COUNT, countedEvents.size.toString())
            put(K_BUY, countedEvents.count { it.direction == "BUY" }.toString())
            put(K_SELL, countedEvents.count { it.direction == "SELL" }.toString())
            put(K_TRIGGERS, triggerKeys.take(MAX_EVENT_LINES).joinToString(",") { "${it.triggerId}@${it.tf}" })
            put(K_EVENTS, lines.joinToString("\n"))
            put(K_STATES, shownStates.joinToString("\n") { s ->
                "• [${tfLabel(s.tf)}] ${s.what}${if (s.direction != "NEUTRAL") " (${s.direction})" else ""}"
            })
            put(K_MTF, digest?.text ?: "")
            put(K_HTF_ALIGN, when (ctx.htfAlignment) { 1 -> "UP"; -1 -> "DOWN"; else -> "" })
            suppressed?.let { put(K_SUPPRESSED, it) }
            if (scan.errors.isNotEmpty()) put(K_ERRORS, scan.errors.joinToString("; ").take(500))
        }
    }

    private fun stateKey(e: TriggerEvent) = "${e.triggerId}@${e.tf}:${e.direction}"

    /** เวลา UTC + session — ให้ AI รู้ว่าอยู่ช่วงไหนของวัน/สัปดาห์ (ตลาดปิด, ช่วง London/NY) */
    private fun timeLine(nowMs: Long): String {
        val t = kotlinx.datetime.Instant.fromEpochMilliseconds(nowMs)
            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
        fun p2(n: Int) = n.toString().padStart(2, '0')
        val session = WakeLearningStore.sessionOf(t.hour)
        return "${t.dayOfWeek.name.take(3)} ${t.year}-${p2(t.monthNumber)}-${p2(t.dayOfMonth)} ${p2(t.hour)}:${p2(t.minute)} UTC · session $session"
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun learningContext(ctx: WakeContext, side: String): WakeLearningStore.Context {
        val s = ctx.primary
        val adx = TaIndicators.adx(s.highs, s.lows, s.closes)?.adx
        val last = s.last
        val atrPct = if (last != null && last.close > 0 && ctx.atr > 0) ctx.atr / last.close * 100 else null
        return WakeLearningStore.Context(
            mtfAlign = WakeLearningStore.mtfAlignOf(side, ctx.trendOf("H4"), ctx.trendOf("H1")),
            adxBucket = WakeLearningStore.adxBucketOf(adx),
            volBucket = WakeLearningStore.volBucketOf(atrPct),
            session = WakeLearningStore.sessionOf(((ctx.nowMs / 3_600_000L) % 24L).toInt())
        )
    }

    private fun nextMemory(ctx: WakeContext, prev: Map<String, String>, scan: WakeTriggerRegistry.ScanResult): Map<String, String> {
        val m = prev.toMutableMap()
        ctx.deepScore?.let { m["deep_score"] = it.toString() }
        ctx.elliott?.let { m["elliott_stage"] = it.stage }
        val trends = listOf("H4", "H1", "M15", "M5").map { ctx.trendOf(it) }
        m["mtf_alignment"] = when {
            trends.all { it == "UP" } -> "UP"
            trends.all { it == "DOWN" } -> "DOWN"
            else -> "MIXED"
        }
        ctx.digest?.let { d ->
            m["ob_levels"] = (d.levelsAbove + d.levelsBelow).filter { it.kind.contains("OB") }
                .joinToString(",") { "${if (it.kind.contains("Demand", true)) "D" else "S"}:${it.price}" }
        }
        m["active_states"] = scan.states.joinToString(",") { stateKey(it) }
        if (scan.events.any { it.triggerId == "HIGH_IMPACT_NEWS_SOON" }) {
            val nowS = ctx.nowMs / 1000
            val soon = ctx.macroEvents.filter { it.isHighImpact && it.epochSeconds in nowS..(nowS + 1800) }
                .map { it.epochSeconds.toString() }
            val warned = (prev["news_warned"]?.split(",").orEmpty() + soon).filter { it.isNotBlank() }.takeLast(20)
            m["news_warned"] = warned.joinToString(",")
        }
        return m
    }

    private fun looksCrypto(symbol: String): Boolean = looksCryptoSymbol(symbol)

    /**
     * ตลาดที่เกี่ยวข้องกับสินทรัพย์นี้ (แท่ง H1)
     * ทอง/FX → DXY + US10Y, คริปโต → BTC + BTC.D
     * ใช้ OHLCV store เดียวกัน (ดึงเฉพาะแท่งใหม่ ไม่ใช่ทั้งชุด)
     */
    private suspend fun intermarketFor(symbol: String, nowMs: Long): Map<String, List<Candle>> {
        return intermarketSymbolsFor(symbol).mapNotNull { (key, tvSym) ->
            val cached = interCache[key]
            val bars = if (cached != null && nowMs - cached.first < INTERMARKET_TTL_MS) cached.second
            else runCatching { smcApi.fetchCandlesWithSource(tvSym, "1h", 200).candles }.getOrElse { emptyList() }
                .also { if (it.isNotEmpty()) interCache[key] = nowMs to it }
            if (bars.isEmpty()) null else key to bars
        }.toMap()
    }

    private suspend fun macroEvents(nowMs: Long): List<TradingApiService.MacroEvent> {
        macroCache?.let { if (nowMs - it.first < MACRO_TTL_MS) return it.second }
        val ev = runCatching { tradingApi.getMacroEvents() }.getOrElse { emptyList() }
        macroCache = nowMs to ev
        return ev
    }

    private suspend fun fearGreed(nowMs: Long): Int? {
        fngCache?.let { if (nowMs - it.first < FNG_TTL_MS) return it.second }
        val v = runCatching { tradingApi.getFearGreedIndex(1)["value"]?.toIntOrNull() }.getOrNull()
        fngCache = nowMs to v
        return v
    }

    private suspend fun deepScore(symbol: String, tf: String, nowMs: Long): Int? {
        val key = "$symbol@$tf"
        deepCache[key]?.let { if (nowMs - it.first < DEEP_TTL_MS) return it.second }
        val v = runCatching { advanced.analyze(symbol, tf)?.summaryScore }.getOrNull()
        deepCache[key] = nowMs to v
        return v
    }

    /** harmonic / elliott คำนวณใหม่เฉพาะเมื่อมีแท่งปิดใหม่ */
    private fun patterns(key: String, barTs: Long, closed: List<Candle>): Pair<List<HarmonicPattern>, ElliotWaveModern?> {
        patternCache[key]?.let { if (it.first == barTs) return it.second to it.third }
        val h = runCatching { modern.detectHarmonics(closed, null) }.getOrElse { emptyList() }
        val e = runCatching { modern.analyzeElliotModern(closed) }.getOrNull()
        patternCache[key] = Triple(barTs, h, e)
        return h to e
    }
}

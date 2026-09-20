package com.skyliner2008.jarvis.automation.wake

import com.skyliner2008.jarvis.db.JarvisDatabaseHolder
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.math.max

/**
 * WakeLearningStore — ระบบเรียนรู้ของ "นาฬิกาปลุก AI"
 *
 * สิ่งที่เรียนรู้คือ **"ปลุกด้วยปัจจัยนี้ ในสภาพแวดล้อมแบบนี้ แล้วเกิดอะไรขึ้นจริง"**
 * ไม่ใช่ "แผนเทรดของปัจจัยชนะไหม" — ปัจจัยไม่มีแผนเทรด มันแค่ปลุก AI
 *
 * - ทุกอย่างอยู่ใน SQLite และคำนวณจากข้อมูลดิบ (ไม่หายตอนปิดแอป, export/import ได้)
 * - 1 รอบสแกนแตกเป็น 1 แถวต่อปัจจัย → ทุกตัวได้เครดิต/ถูกตำหนิตามจริง
 * - แยกตาม **ปัจจัย × TF ที่เกิด** (P16) → รู้ว่า "ปัจจัยไหนเหมาะกับ TF ไหน"
 * - แยกตาม context (mtf_align × adx × session) → รู้ว่า "ใช้ได้เมื่อไหร่"
 * - วัดด้วย forward return หน่วย ATR ของ TF ที่เกิด ที่ [FORWARD_HORIZON_BARS] แท่ง (ของ TF นั้น) ข้างหน้า
 *
 * การเรียนรู้ "ฟรี" (ไม่ใช้โทเคน) — ปัจจัยทุกตัวถูกบันทึกแม้งบการปลุกหมด
 * ส่วนการปลุก AI "มีต้นทุน" — ปัจจัยที่พิสูจน์ว่าไร้ประโยชน์จะถูก **ลดชั้น**
 * (ยังเก็บสถิติต่อ เผื่อสภาพตลาดเปลี่ยนแล้วมันกลับมาใช้ได้)
 */
object WakeLearningStore {

    /** กี่แท่ง (ของ TF ที่เฝ้า) ข้างหน้าที่ใช้วัดผล */
    const val FORWARD_HORIZON_BARS = 12

    /** ตัวอย่างขั้นต่ำก่อนจะบอก AI ว่าสถิติ "เชื่อได้" */
    const val MIN_SAMPLES = 20

    /** ตัวอย่างขั้นต่ำก่อนจะลดชั้น/เลื่อนชั้นปัจจัยบน TF หนึ่ง (กันตัดสินเพราะโชคช่วงแรก) */
    const val DEMOTE_MIN_SAMPLES = 40

    /** เลื่อนชั้น TF ที่อยู่นอกชุดเริ่มต้นให้ปลุก AI ได้ เมื่อผลจริงดีพอ */
    const val PROMOTE_MIN_AVG_R = 0.2
    const val PROMOTE_MIN_WIN = 0.55
    /** ปัจจัยไม่มีทิศ: ขยับแรงกว่าเส้นฐานของ TF นั้นกี่เท่า */
    const val PROMOTE_NEUTRAL_RATIO = 1.25

/**
     * เก็บแถวดิบของการปลุกจริง (woke) ย้อนหลังกี่วัน — ยุบเป็นสถิติสะสมก่อนลบ
     * (ประวัติมุมมองของ AI อยู่ในตาราง AiWakeView ซึ่งเก็บ 365 วันแยกต่างหาก)
     */
    const val RETENTION_DAYS = 120

    /**
     * แถวที่วัดผลแล้วและไม่ได้ปลุก AI ถูกยุบเป็นสถิติสะสม (AnticipationFactorStat) หลังกี่วัน
     *
     * ตั้งแต่ประเมิน 5 TF ทุกนาที มี ~6,000 แถว/วัน/สินทรัพย์ (วัดจากมือถือจริง) — เก็บดิบ 365 วัน = ~2 ล้านแถว
     * สถิติที่ยุบแล้วอยู่ถาวร (การเรียนรู้ไม่หาย) ส่วนแถวดิบเก็บ 3 วันไว้ตรวจย้อนหลัง/ดูรายการล่าสุด
     */
    const val FOLD_DAYS = 3

    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    // ─── Context buckets ─────────────────────────────────────────────────────

    data class Context(
        val mtfAlign: String,   // ALIGNED | PARTIAL | CONFLICT | UNKNOWN
        val adxBucket: String,  // TREND | WEAK | RANGE | UNKNOWN
        val volBucket: String,  // HIGH | NORMAL | LOW | UNKNOWN
        val session: String     // ASIA | LONDON | NY | OFF
    ) {
        companion object {
            val UNKNOWN = Context("UNKNOWN", "UNKNOWN", "UNKNOWN", "OFF")
        }

        fun describeTh(): String = buildString {
            append(when (mtfAlign) {
                "ALIGNED" -> "ทิศตรงกับ H4/H1"; "CONFLICT" -> "สวนทาง H4/H1"; "PARTIAL" -> "H4/H1 ไม่ชัด"; else -> "ไม่ทราบ MTF"
            })
            append(" · ")
            append(when (adxBucket) { "TREND" -> "ตลาดมีเทรนด์"; "RANGE" -> "ตลาดไซด์เวย์"; "WEAK" -> "เทรนด์อ่อน"; else -> "-" })
            append(" · ")
            append(session)
        }
    }

    fun mtfAlignOf(side: String, h4Trend: String?, h1Trend: String?): String {
        if (side == "NEUTRAL") return "UNKNOWN"
        if (h4Trend.isNullOrBlank() || h1Trend.isNullOrBlank()) return "UNKNOWN"
        fun agrees(t: String) = (side == "BUY" && t == "UP") || (side == "SELL" && t == "DOWN")
        fun opposes(t: String) = (side == "BUY" && t == "DOWN") || (side == "SELL" && t == "UP")
        return when {
            agrees(h4Trend) && agrees(h1Trend) -> "ALIGNED"
            opposes(h4Trend) && opposes(h1Trend) -> "CONFLICT"
            else -> "PARTIAL"
        }
    }

    fun adxBucketOf(adx: Double?): String = when {
        adx == null || adx.isNaN() -> "UNKNOWN"
        adx >= 25.0 -> "TREND"
        adx >= 20.0 -> "WEAK"
        else -> "RANGE"
    }

    fun volBucketOf(atrPct: Double?): String = when {
        atrPct == null || atrPct.isNaN() || atrPct <= 0.0 -> "UNKNOWN"
        atrPct >= 1.0 -> "HIGH"
        atrPct >= 0.3 -> "NORMAL"
        else -> "LOW"
    }

    fun sessionOf(hourUtc: Int): String = when (hourUtc) {
        in 0..6 -> "ASIA"
        in 7..11 -> "LONDON"
        in 12..20 -> "NY"
        else -> "OFF"
    }

    // ─── บันทึก ──────────────────────────────────────────────────────────────

    data class Record(
        val signalId: String,
        val factorId: String,
        val symbol: String,
        val interval: String,
        /** TF ที่ปัจจัยเกิด — [refAtr] ต้องเป็น ATR ของ TF นี้ */
        val factorTf: String,
        val side: String,
        val kind: String,
        val refPrice: Double,
        val refAtr: Double,
        val context: Context,
        val createdAt: Long
    )

    fun record(records: List<Record>) {
        if (records.isEmpty()) return
        val db = JarvisDatabaseHolder.database ?: return
        runCatching {
            db.transaction {
                records.forEach { r ->
                    db.jarvisDatabaseQueries.insertFactorOutcome(
                        signal_id = r.signalId, factor_id = r.factorId.uppercase(),
                        symbol = r.symbol.uppercase(), interval = r.interval.lowercase(),
                        side = r.side, kind = r.kind, ref_price = r.refPrice, ref_atr = r.refAtr, woke = 0L,
                        mtf_align = r.context.mtfAlign, adx_bucket = r.context.adxBucket,
                        vol_bucket = r.context.volBucket, session = r.context.session,
                        ai_decision = null, ai_bias = null, status = "PENDING",
                        forward_r = null, mfe_r = null, mae_r = null,
                        created_at = r.createdAt, resolved_at = null,
                        ai_confidence = null, ai_reason = null,
                        factor_tf = TaIndicators.normalizeTimeframe(r.factorTf)
                    )
                }
            }
        }.onFailure { logDebug("WakeLearning", "record failed: ${it.message}") }
    }

    /** @param keys "FACTOR@tf" ของเหตุการณ์ที่ปลุก AI */
    fun markWoke(signalId: String, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val db = JarvisDatabaseHolder.database ?: return
        runCatching { db.jarvisDatabaseQueries.markFactorOutcomesWoke(signalId, keys.map { k -> k.substringBefore('@').uppercase() + "@" + k.substringAfter('@') }) }
    }

    fun key(factorId: String, tf: String) = "${factorId.uppercase()}@${TaIndicators.normalizeTimeframe(tf)}"

    /**
     * บันทึกสิ่งที่ AI ตัดสินหลังถูกปลุก (ทั้ง NOTIFY และ SKIP) — ใช้วัดว่า "AI วิเคราะห์ถูกไหม"
     * และเก็บเหตุผล/ความมั่นใจไว้ตรวจย้อนหลัง (เดิมเหตุผลของ SKIP อยู่แค่ใน logcat แล้วหายไป)
     */
    fun setAiDecision(signalId: String, decision: String, bias: String?, confidence: Int? = null, reason: String? = null) {
        val db = JarvisDatabaseHolder.database ?: return
        runCatching {
            db.jarvisDatabaseQueries.setFactorOutcomeAiDecision(
                decision, bias, confidence?.toLong(), reason?.trim()?.take(1000)?.ifBlank { null }, signalId
            )
        }
    }

    /**
     * ปิดผลของแถวที่ครบกรอบเวลาแล้ว
     *
     * หน้าต่างวัดผล = แท่งของ **TF ที่ปัจจัยเกิด** ที่ **ปิดหลังเวลาที่ตรวจพบ** [FORWARD_HORIZON_BARS] แท่ง
     * (created_at = เวลาที่ตรวจพบ, ref_price = ราคา ณ ตอนนั้น)
     *  - แท่งที่ปิดก่อนตรวจพบไม่ถูกนับ — ราคาที่เกิดก่อนเหตุการณ์ไม่ใช่ผลของมัน
     *  - แถวรุ่นแรก (created_at = เวลาเปิดแท่งพอดี) ใช้กติกาเดิม: แท่งที่เปิดหลัง created_at
     *
     * แถวที่แท่งในมือไม่ครอบคลุมจุดเริ่มวัด (แอปปิดไปนาน ข้อมูลเก่าหลุดหน้าต่าง) ถูกปิดเป็น EXPIRED
     * — เดิมค้าง PENDING ตลอดไปและถูกวัดด้วยแท่งผิดช่วง อีกทั้งดึงทีละ 500 แถวเรียงจากเก่า
     *   แถวค้างจึงบังไม่ให้แถวใหม่ได้วัดผลเลย
     */
    fun resolvePending(symbol: String, interval: String, closedByTf: Map<String, List<Candle>>): Int {
        val db = JarvisDatabaseHolder.database ?: return 0
        val pending = runCatching {
            db.jarvisDatabaseQueries.getPendingFactorOutcomes(symbol.uppercase(), interval.lowercase()).executeAsList()
        }.getOrElse { return 0 }
        if (pending.isEmpty()) return 0
        val now = Clock.System.now().toEpochMilliseconds()
        var resolved = 0
        runCatching {
            db.transaction {
                for (row in pending) {
                    val tf = row.factor_tf.ifBlank { row.interval }
                    // ไม่มีแท่งของ TF นั้นในรอบนี้ (ดึงไม่สำเร็จ) → รอรอบหน้า ไม่ปิดเป็น EXPIRED
                    val closedBars = closedByTf[tf]?.takeIf { it.isNotEmpty() } ?: continue
                    when (val w = forwardWindow(closedBars, row.created_at, TaIndicators.timeframeMillis(tf))) {
                        is Window.Uncovered -> db.jarvisDatabaseQueries.expireFactorOutcomeById(now, row.id)
                        is Window.Ready -> {
                            if (row.ref_atr <= 0.0) {
                                db.jarvisDatabaseQueries.expireFactorOutcomeById(now, row.id)
                                continue
                            }
                            val out = measure(row.side, row.ref_price, row.ref_atr, w.bars)
                            db.jarvisDatabaseQueries.resolveFactorOutcomeById(out.first, out.second, out.third, now, row.id)
                            resolved++
                        }
                        Window.NotYet -> Unit
                    }
                }
            }
        }.onFailure { logDebug("WakeLearning", "resolve failed: ${it.message}") }
        if (resolved > 0) invalidateCache()
        return resolved
    }

    internal sealed interface Window {
        data class Ready(val bars: List<Candle>) : Window
        data object NotYet : Window
        data object Uncovered : Window
    }

    /** เลือกแท่งสำหรับวัดผล (แยกออกมาเพื่อทดสอบได้) */
    internal fun forwardWindow(closedBars: List<Candle>, createdAt: Long, tfMs: Long): Window {
        // แท่งแรกในมือต้องเริ่มก่อน/ตรงจุดตรวจพบ ไม่งั้นช่วงต้นของหน้าต่างหายไป
        if (closedBars.first().timestamp > createdAt) return Window.Uncovered
        // แถวรุ่นแรกเก็บเวลาเปิดแท่งพอดี (เวลาตรวจพบจริงละเอียดระดับ ms แทบไม่มีทางตรงกับเวลาเปิดแท่ง)
        // — ไม่ใช้ createdAt % tfMs เพราะแท่ง H4 ของโบรกเกอร์ FX ไม่ได้เริ่มตรงรอบ epoch
        val legacy = closedBars.binarySearchBy(createdAt) { it.timestamp } >= 0
        val fwd = if (legacy) closedBars.filter { it.timestamp > createdAt }
        else closedBars.filter { it.timestamp + tfMs > createdAt }
        return if (fwd.size >= FORWARD_HORIZON_BARS) Window.Ready(fwd.take(FORWARD_HORIZON_BARS)) else Window.NotYet
    }

    /**
     * forward return หน่วย ATR
     * directional → (return, MFE, MAE) มีเครื่องหมายตามทิศ
     * NEUTRAL     → (การเคลื่อนที่สุทธิ "มีเครื่องหมาย" ขึ้น = บวก, การเคลื่อนที่สูงสุดข้างใดก็ได้, 0)
     *               สถิติของปัจจัยบริบทใช้ค่าสัมบูรณ์ (ขนาด) ส่วนเครื่องหมายใช้วัดว่า AI มองทิศถูกไหม
     */
    internal fun measure(side: String, ref: Double, atr: Double, window: List<Candle>): Triple<Double, Double, Double> {
        val last = window.last().close
        val hi = window.maxOf { it.high }
        val lo = window.minOf { it.low }
        return when (side) {
            "BUY" -> Triple((last - ref) / atr, max(0.0, (hi - ref) / atr), max(0.0, (ref - lo) / atr))
            "SELL" -> Triple((ref - last) / atr, max(0.0, (ref - lo) / atr), max(0.0, (hi - ref) / atr))
            else -> Triple((last - ref) / atr, max(hi - ref, ref - lo) / atr, 0.0)
        }
    }

    // ─── สถิติ (แคช 10 นาที — ไม่ query ทุกรอบสแกน) ─────────────────────────

    data class FactorStat(
        val n: Int,
        /** directional: forward return เฉลี่ย (R) | neutral: ขนาดการเคลื่อนที่เฉลี่ย (ATR) */
        val avgR: Double,
        val winRate: Double,
        val directional: Boolean
    ) {
        val reliable: Boolean get() = n >= MIN_SAMPLES
    }

    /**
     * ยุบแถวที่วัดผลแล้วและไม่ได้ปลุก AI เข้าสถิติสะสม แล้วลบแถวดิบทิ้ง (เรียกจากงานดูแลรายวัน)
     * @return จำนวนแถวดิบที่ถูกยุบ
     */
    fun foldAgedBeforeDelete(nowMs: Long, retentionDays: Int = RETENTION_DAYS) {
        val db = JarvisDatabaseHolder.database ?: return
        runCatching { db.jarvisDatabaseQueries.foldAllResolvedFactorOutcomes(nowMs, nowMs - retentionDays * 86_400_000L) }
            .onFailure { logDebug("WakeLearning", "fold aged failed: ${it.message}") }
    }

    fun foldOldOutcomes(nowMs: Long, foldDays: Int = FOLD_DAYS): Int {
        val db = JarvisDatabaseHolder.database ?: return 0
        val cutoff = nowMs - foldDays * 86_400_000L
        val q = db.jarvisDatabaseQueries
        var folded = 0
        runCatching {
            db.transaction {
                folded = q.countFoldableFactorOutcomes(cutoff).executeAsOne().toInt()
                if (folded > 0) {
                    q.foldResolvedFactorOutcomes(nowMs, cutoff)
                    q.deleteFoldedFactorOutcomes(cutoff)
                }
            }
        }.onFailure { logDebug("WakeLearning", "fold failed: ${it.message}"); return 0 }
        if (folded > 0) {
            invalidateCache()
            logDebug("WakeLearning", "ยุบแถวการเรียนรู้ที่วัดผลแล้ว $folded แถวเป็นสถิติสะสม")
        }
        return folded
    }

    /**
     * สะสมแยกแถวมีทิศกับแถวไม่มีทิศ — ปัจจัยตัวเดียวยิงได้ทั้ง BUY/SELL/NEUTRAL (เช่น EQ_POOL_SWEPT)
     * เดิมรวมกัน: ขนาดการเคลื่อนที่ (บวกเสมอ) ของแถว NEUTRAL ไปปนใน avg R ทำให้ตัวที่แย่ดูดี
     */
    private data class Agg(
        var dirN: Int = 0, var dirSum: Double = 0.0, var dirWins: Int = 0,
        var neuN: Int = 0, var neuAbs: Double = 0.0
    )
    /** key ของ [overall]/[byContext] = "FACTOR@tf" ([key]) */
    private data class Cache(
        val at: Long,
        val overall: Map<String, FactorStat>,
        val byContext: Map<String, FactorStat>,
        /** ขนาดการเคลื่อนที่เฉลี่ยของปัจจัย NEUTRAL ทั้งหมด แยกตาม TF (หน่วย ATR ของ TF นั้น) */
        val baselineAbs: Map<String, Double>
    )

    private var cache: Cache? = null

    fun invalidateCache() { cache = null }

    private fun ctxKey(factorId: String, tf: String, ctx: Context) = "${key(factorId, tf)}|${ctx.mtfAlign}|${ctx.adxBucket}|${ctx.session}"

    private fun load(): Cache {
        val now = Clock.System.now().toEpochMilliseconds()
        cache?.let { if (now - it.at < CACHE_TTL_MS) return it }
        val db = JarvisDatabaseHolder.database ?: return Cache(now, emptyMap(), emptyMap(), emptyMap()).also { cache = it }
        val rows = runCatching { db.jarvisDatabaseQueries.getFactorAggregates().executeAsList() }.getOrElse { emptyList() }
        // สถิติที่ยุบแล้ว (แถวดิบถูกลบไป) — ต้องรวมด้วย ไม่งั้นการเรียนรู้ "ลืม" ทุกครั้งที่ prune
        val folded = runCatching { db.jarvisDatabaseQueries.getFactorStats().executeAsList() }.getOrElse { emptyList() }

        val overall = mutableMapOf<String, Agg>()
        val byCtx = mutableMapOf<String, Agg>()
        val neutralN = mutableMapOf<String, Int>(); val neutralAbs = mutableMapOf<String, Double>()
        for (r in rows) {
            val tf = r.factor_tf.ifBlank { "15m" }
            val n = r.n.toInt()
            val sum = r.sum_r ?: 0.0
            val sumAbs = r.sum_abs ?: 0.0
            val wins = (r.wins ?: 0L).toInt()
            val directional = r.side != "NEUTRAL"
            fun add(a: Agg) {
                if (directional) { a.dirN += n; a.dirSum += sum; a.dirWins += wins }
                else { a.neuN += n; a.neuAbs += sumAbs }
            }
            val k = key(r.factor_id, tf)
            add(overall.getOrPut(k) { Agg() })
            add(byCtx.getOrPut("$k|${r.mtf_align}|${r.adx_bucket}|${r.session}") { Agg() })
            if (!directional) {
                neutralN[tf] = (neutralN[tf] ?: 0) + n
                neutralAbs[tf] = (neutralAbs[tf] ?: 0.0) + sumAbs
            }
        }
        for (r in folded) {
            val n = r.n.toInt()
            val directional = r.side != "NEUTRAL"
            fun add(a: Agg) {
                if (directional) { a.dirN += n; a.dirSum += r.sum_r; a.dirWins += r.wins.toInt() }
                else { a.neuN += n; a.neuAbs += r.sum_abs }
            }
            val k = key(r.factor_id, r.factor_tf)
            add(overall.getOrPut(k) { Agg() })
            add(byCtx.getOrPut("$k|${r.mtf_align}|${r.adx_bucket}|${r.session}") { Agg() })
            if (!directional) {
                neutralN[r.factor_tf] = (neutralN[r.factor_tf] ?: 0) + n
                neutralAbs[r.factor_tf] = (neutralAbs[r.factor_tf] ?: 0.0) + r.sum_abs
            }
        }
        fun toStat(a: Agg): FactorStat {
            val directional = a.dirN > 0 && a.dirN >= a.neuN
            return if (directional) FactorStat(
                n = a.dirN, avgR = a.dirSum / a.dirN, winRate = a.dirWins.toDouble() / a.dirN, directional = true
            ) else FactorStat(
                n = a.neuN, avgR = if (a.neuN > 0) a.neuAbs / a.neuN else 0.0, winRate = 0.0, directional = false
            )
        }
        return Cache(
            now,
            overall.mapValues { toStat(it.value) },
            byCtx.mapValues { toStat(it.value) },
            neutralN.mapNotNull { (tf, n) -> if (n > 0) tf to (neutralAbs[tf] ?: 0.0) / n else null }.toMap()
        ).also { cache = it }
    }

    fun overall(factorId: String, tf: String): FactorStat? = load().overall[key(factorId, tf)]
    fun inContext(factorId: String, tf: String, ctx: Context): FactorStat? = load().byContext[ctxKey(factorId, tf, ctx)]
    /** key = "FACTOR@tf" */
    fun allOverall(): Map<String, FactorStat> = load().overall

    /** ขนาดการเคลื่อนที่เฉลี่ยของปัจจัย NEUTRAL บน TF นั้น — ใช้เป็นเส้นฐานวัดว่าตัวไหน "ไม่ต่างจากสุ่ม" */
    fun neutralBaseline(tf: String): Double? = load().baselineAbs[TaIndicators.normalizeTimeframe(tf)]

    /**
     * ปัจจัย × TF ที่พิสูจน์แล้วว่าไม่คุ้มที่จะปลุก AI
     * directional: forward return เฉลี่ย ≤ 0 | neutral: ขยับน้อยกว่าเส้นฐานของ TF นั้น
     */
    fun isDemoted(factorId: String, tf: String): Boolean {
        val s = overall(factorId, tf) ?: return false
        if (s.n < DEMOTE_MIN_SAMPLES) return false
        return if (s.directional) s.avgR <= 0.0 else {
            val base = neutralBaseline(tf) ?: return false
            s.avgR < base * 0.9
        }
    }

    /** ปัจจัย × TF ที่อยู่นอกชุดเริ่มต้น แต่ผลจริงดีพอให้ปลุก AI ได้ */
    fun isPromoted(factorId: String, tf: String): Boolean {
        val s = overall(factorId, tf) ?: return false
        if (s.n < DEMOTE_MIN_SAMPLES) return false
        return if (s.directional) s.avgR >= PROMOTE_MIN_AVG_R && s.winRate >= PROMOTE_MIN_WIN else {
            val base = neutralBaseline(tf) ?: return false
            s.avgR >= base * PROMOTE_NEUTRAL_RATIO
        }
    }

    enum class TfVerdict(val wakes: Boolean, val labelTh: String) {
        WAKE(true, "ปลุก AI"),
        PROMOTED(true, "⬆️ เลื่อนชั้น (ผลดี)"),
        RECORD(false, "เก็บสถิติ"),
        DEMOTED(false, "⬇️ ลดชั้น (ผลแย่)")
    }

    /** ปัจจัยนี้บน TF นี้ปลุก AI ได้ไหม — ชุดเริ่มต้น ([WakeTfProfile]) ปรับด้วยผลจริง */
    fun tfVerdict(factorId: String, tf: String, jobTf: String): TfVerdict = when {
        isDemoted(factorId, tf) -> TfVerdict.DEMOTED
        WakeTfProfile.isDefaultWakeTf(factorId, tf, jobTf) -> TfVerdict.WAKE
        isPromoted(factorId, tf) -> TfVerdict.PROMOTED
        else -> TfVerdict.RECORD
    }

    /**
     * บรรทัดสถิติสำหรับ AI — บอกว่าปัจจัยนี้บน TF นี้ "เคย" ให้ผลอย่างไร (ข้อมูลจริง ไม่ใช่ความเชื่อมั่นสมมติ)
     */
    fun describeForAi(factorId: String, tf: String, ctx: Context): String {
        val c = inContext(factorId, tf, ctx)
        val o = overall(factorId, tf)
        fun fmt(s: FactorStat, where: String): String =
            if (s.directional) "$where n=${s.n} avg ${"%+.2f".format(s.avgR)}R ไปตามทิศ ${"%.0f".format(s.winRate * 100)}%"
            else "$where n=${s.n} ราคาขยับเฉลี่ย ${"%.1f".format(s.avgR)}×ATR" +
                (neutralBaseline(tf)?.let { " (ปกติ ${"%.1f".format(it)}×ATR)" } ?: "")
        return when {
            c != null && c.reliable -> fmt(c, "สภาพแวดล้อมนี้")
            o != null && o.reliable -> fmt(o, "ภาพรวม") + if (c != null) " (สภาพแวดล้อมนี้ยังน้อย n=${c.n})" else ""
            o != null -> "ข้อมูลยังน้อย (n=${o.n}) — ยังสรุปไม่ได้"
            else -> "ยังไม่มีสถิติ"
        }
    }

    // ─── รายงาน ─────────────────────────────────────────────────────────────

    fun buildReport(limit: Int = 130): String {
        val stats = allOverall()
        if (stats.isEmpty()) {
            return "📭 ยังไม่มีข้อมูลการเรียนรู้ — ระบบจะเริ่มสะสมเมื่อปัจจัยเริ่มยิงและครบกรอบวัดผล ($FORWARD_HORIZON_BARS แท่ง)"
        }
        val db = JarvisDatabaseHolder.database
        val ai = runCatching { db?.jarvisDatabaseQueries?.getAiDecisionAccuracy()?.executeAsList() }.getOrNull().orEmpty()
        val views = AiViewTracker.reportSection()
        val tfs = WakeTfProfile.EVAL_TFS
        fun cell(id: String, tf: String): String {
            val s = stats[key(id, tf)] ?: return "·"
            val v = if (s.directional) "%+.2f".format(s.avgR) else "${"%.1f".format(s.avgR)}×"
            val mark = when {
                s.n < MIN_SAMPLES -> ""
                isDemoted(id, tf) -> "⬇️"
                isPromoted(id, tf) -> "⬆️"
                else -> ""
            }
            return "$v (${s.n})$mark"
        }
        val ids = stats.keys.map { it.substringBefore('@') }.distinct()
        // เรียงตามจำนวนตัวอย่างรวม (มีข้อมูลมากขึ้นก่อน)
        val order = ids.sortedByDescending { id -> tfs.sumOf { stats[key(id, it)]?.n ?: 0 } }
        return buildString {
            appendLine("🧠 **ผลการเรียนรู้ของระบบปลุก AI — แยกตามปัจจัย × TF**")
            appendLine("วัดด้วย forward return $FORWARD_HORIZON_BARS แท่งของ TF ที่เกิด หน่วย ATR ของ TF นั้น")
            appendLine("มีทิศ = avg R (+ ไปตามทิศ) · ไม่มีทิศ = ขยับเฉลี่ยกี่ ATR · (n) = จำนวนตัวอย่าง · " +
                "เชื่อถือได้เมื่อ n ≥ $MIN_SAMPLES · ⬇️ ลดชั้น / ⬆️ เลื่อนชั้น ตัดสินเมื่อ n ≥ $DEMOTE_MIN_SAMPLES")
            appendLine()
            appendLine("| ปัจจัย | " + tfs.joinToString(" | ") { tfLabel(it) } + " |")
            appendLine("|---|" + tfs.joinToString("") { "---|" })
            order.take(limit).forEach { id ->
                appendLine("| $id | " + tfs.joinToString(" | ") { cell(id, it) } + " |")
            }
            val base = tfs.mapNotNull { tf -> neutralBaseline(tf)?.let { "${tfLabel(tf)} ${"%.2f".format(it)}×" } }
            if (base.isNotEmpty()) appendLine("เส้นฐานของปัจจัยไม่มีทิศ: " + base.joinToString(" · "))
            if (ai.isNotEmpty()) {
                appendLine()
                appendLine("### ความแม่นของ AI หลังถูกปลุก (วัดตามทิศที่ AI มอง ต่อ 1 การปลุก)")
                appendLine("นับเฉพาะการปลุกที่มีปัจจัยบน TF ของ job ร่วมด้วย — ผลมุมมองแบบเต็ม (ชน TP/SL) อยู่ท้ายรายงาน")
                appendLine("| AI ตัดสิน | มุมมอง | ครั้ง | ไปตามทิศ AI | avg R | ราคาขยับเฉลี่ย |")
                appendLine("|---|---|---|---|---|---|")
                ai.forEach {
                    val measured = it.measured ?: 0L
                    val hit = if (measured > 0) "${"%.0f".format((it.wins ?: 0L).toDouble() / measured * 100)}%" else "-"
                    val avg = it.avg_r?.let { r -> "%+.2f".format(r) } ?: "-"
                    val move = it.avg_move?.let { m -> "${"%.1f".format(m)}×ATR" } ?: "-"
                    appendLine("| ${it.ai_decision} | ${it.ai_bias ?: "-"} | ${it.n} | $hit | $avg | $move |")
                }
                appendLine("SKIP ที่ดี = ราคาขยับน้อยกว่า NOTIFY (ไม่ได้พลาดโอกาส)")
            }
            views?.let { appendLine(); appendLine(it) }
        }.trim()
    }

}

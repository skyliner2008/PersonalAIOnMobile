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
 * - แยกตาม context (mtf_align × adx × session) → รู้ว่า "ใช้ได้เมื่อไหร่"
 * - วัดด้วย forward return หน่วย ATR ที่ [FORWARD_HORIZON_BARS] แท่งข้างหน้า
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

    /** ตัวอย่างขั้นต่ำก่อนจะลดชั้นปัจจัย (กันตัดทิ้งเพราะโชคร้ายช่วงแรก) */
    const val DEMOTE_MIN_SAMPLES = 40

    /** เก็บประวัติการเรียนรู้ย้อนหลังกี่วัน */
    const val RETENTION_DAYS = 365

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
                        ai_confidence = null, ai_reason = null
                    )
                }
            }
        }.onFailure { logDebug("WakeLearning", "record failed: ${it.message}") }
    }

    fun markWoke(signalId: String, factorIds: Collection<String>) {
        if (factorIds.isEmpty()) return
        val db = JarvisDatabaseHolder.database ?: return
        runCatching { db.jarvisDatabaseQueries.markFactorOutcomesWoke(signalId, factorIds.map { it.uppercase() }) }
    }

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
     * หน้าต่างวัดผล = แท่ง TF หลักที่ **ปิดหลังเวลาที่ตรวจพบ** [FORWARD_HORIZON_BARS] แท่ง
     * (created_at = เวลาที่ตรวจพบ, ref_price = ราคา ณ ตอนนั้น)
     *  - แท่งที่ปิดก่อนตรวจพบไม่ถูกนับ — ราคาที่เกิดก่อนเหตุการณ์ไม่ใช่ผลของมัน
     *  - แถวรุ่นแรก (created_at = เวลาเปิดแท่งพอดี) ใช้กติกาเดิม: แท่งที่เปิดหลัง created_at
     *
     * แถวที่แท่งในมือไม่ครอบคลุมจุดเริ่มวัด (แอปปิดไปนาน ข้อมูลเก่าหลุดหน้าต่าง) ถูกปิดเป็น EXPIRED
     * — เดิมค้าง PENDING ตลอดไปและถูกวัดด้วยแท่งผิดช่วง อีกทั้งดึงทีละ 500 แถวเรียงจากเก่า
     *   แถวค้างจึงบังไม่ให้แถวใหม่ได้วัดผลเลย
     */
    fun resolvePending(symbol: String, interval: String, closedBars: List<Candle>): Int {
        val db = JarvisDatabaseHolder.database ?: return 0
        val pending = runCatching {
            db.jarvisDatabaseQueries.getPendingFactorOutcomes(symbol.uppercase(), interval.lowercase()).executeAsList()
        }.getOrElse { return 0 }
        if (pending.isEmpty() || closedBars.isEmpty()) return 0
        val tfMs = TaIndicators.timeframeMillis(interval)
        val now = Clock.System.now().toEpochMilliseconds()
        var resolved = 0
        runCatching {
            db.transaction {
                for (row in pending) {
                    when (val w = forwardWindow(closedBars, row.created_at, tfMs)) {
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
     * สะสมแยกแถวมีทิศกับแถวไม่มีทิศ — ปัจจัยตัวเดียวยิงได้ทั้ง BUY/SELL/NEUTRAL (เช่น EQ_POOL_SWEPT)
     * เดิมรวมกัน: ขนาดการเคลื่อนที่ (บวกเสมอ) ของแถว NEUTRAL ไปปนใน avg R ทำให้ตัวที่แย่ดูดี
     */
    private data class Agg(
        var dirN: Int = 0, var dirSum: Double = 0.0, var dirWins: Int = 0,
        var neuN: Int = 0, var neuAbs: Double = 0.0
    )
    private data class Cache(
        val at: Long,
        val overall: Map<String, FactorStat>,
        val byContext: Map<String, FactorStat>,
        val baselineAbs: Double?
    )

    private var cache: Cache? = null

    fun invalidateCache() { cache = null }

    private fun ctxKey(factorId: String, ctx: Context) = "${factorId.uppercase()}|${ctx.mtfAlign}|${ctx.adxBucket}|${ctx.session}"

    private fun load(): Cache {
        val now = Clock.System.now().toEpochMilliseconds()
        cache?.let { if (now - it.at < CACHE_TTL_MS) return it }
        val db = JarvisDatabaseHolder.database ?: return Cache(now, emptyMap(), emptyMap(), null).also { cache = it }
        val rows = runCatching { db.jarvisDatabaseQueries.getFactorAggregates().executeAsList() }.getOrElse { emptyList() }

        val overall = mutableMapOf<String, Agg>()
        val byCtx = mutableMapOf<String, Agg>()
        var neutralN = 0; var neutralAbs = 0.0
        for (r in rows) {
            val n = r.n.toInt()
            val sum = r.sum_r ?: 0.0
            val sumAbs = r.sum_abs ?: 0.0
            val wins = (r.wins ?: 0L).toInt()
            val directional = r.side != "NEUTRAL"
            fun add(a: Agg) {
                if (directional) { a.dirN += n; a.dirSum += sum; a.dirWins += wins }
                else { a.neuN += n; a.neuAbs += sumAbs }
            }
            add(overall.getOrPut(r.factor_id) { Agg() })
            add(byCtx.getOrPut("${r.factor_id}|${r.mtf_align}|${r.adx_bucket}|${r.session}") { Agg() })
            if (!directional) { neutralN += n; neutralAbs += sumAbs }
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
            if (neutralN > 0) neutralAbs / neutralN else null
        ).also { cache = it }
    }

    fun overall(factorId: String): FactorStat? = load().overall[factorId.uppercase()]
    fun inContext(factorId: String, ctx: Context): FactorStat? = load().byContext[ctxKey(factorId, ctx)]
    fun allOverall(): Map<String, FactorStat> = load().overall

    /** ขนาดการเคลื่อนที่เฉลี่ยของปัจจัย NEUTRAL ทั้งหมด — ใช้เป็นเส้นฐานวัดว่าตัวไหน "ไม่ต่างจากสุ่ม" */
    fun neutralBaseline(): Double? = load().baselineAbs

    /**
     * ปัจจัยที่พิสูจน์แล้วว่าไม่คุ้มที่จะปลุก AI
     * directional: forward return เฉลี่ย ≤ 0 | neutral: ขยับน้อยกว่าเส้นฐานของปัจจัย neutral ทั้งหมด
     */
    fun isDemoted(factorId: String): Boolean {
        val s = overall(factorId) ?: return false
        if (s.n < DEMOTE_MIN_SAMPLES) return false
        return if (s.directional) s.avgR <= 0.0 else {
            val base = neutralBaseline() ?: return false
            s.avgR < base * 0.9
        }
    }

    /**
     * บรรทัดสถิติสำหรับ AI — บอกว่าปัจจัยนี้ "เคย" ให้ผลอย่างไร (ข้อมูลจริง ไม่ใช่ความเชื่อมั่นสมมติ)
     */
    fun describeForAi(factorId: String, ctx: Context): String {
        val c = inContext(factorId, ctx)
        val o = overall(factorId)
        fun fmt(s: FactorStat, where: String): String =
            if (s.directional) "$where n=${s.n} avg ${"%+.2f".format(s.avgR)}R ไปตามทิศ ${"%.0f".format(s.winRate * 100)}%"
            else "$where n=${s.n} ราคาขยับเฉลี่ย ${"%.1f".format(s.avgR)}×ATR" +
                (neutralBaseline()?.let { " (ปกติ ${"%.1f".format(it)}×ATR)" } ?: "")
        return when {
            c != null && c.reliable -> fmt(c, "สภาพแวดล้อมนี้")
            o != null && o.reliable -> fmt(o, "ภาพรวม") + if (c != null) " (สภาพแวดล้อมนี้ยังน้อย n=${c.n})" else ""
            o != null -> "ข้อมูลยังน้อย (n=${o.n}) — ยังสรุปไม่ได้"
            else -> "ยังไม่มีสถิติ"
        }
    }

    // ─── รายงาน ─────────────────────────────────────────────────────────────

    fun buildReport(limit: Int = 60): String {
        val stats = allOverall()
        if (stats.isEmpty()) {
            return "📭 ยังไม่มีข้อมูลการเรียนรู้ — ระบบจะเริ่มสะสมเมื่อปัจจัยเริ่มยิงและครบกรอบวัดผล ($FORWARD_HORIZON_BARS แท่ง)"
        }
        val base = neutralBaseline()
        val db = JarvisDatabaseHolder.database
        val ai = runCatching { db?.jarvisDatabaseQueries?.getAiDecisionAccuracy()?.executeAsList() }.getOrNull().orEmpty()
        return buildString {
            appendLine("🧠 **ผลการเรียนรู้ของระบบปลุก AI**")
            appendLine("วัดด้วย forward return $FORWARD_HORIZON_BARS แท่ง หน่วย ATR — ไม่ใช้ SL/TP สมมติ")
            appendLine()
            appendLine("### ปัจจัยที่มีทิศทาง")
            appendLine("| ปัจจัย | n | avg R | ไปตามทิศ | สถานะ |")
            appendLine("|---|---|---|---|---|")
            stats.filter { it.value.directional }.entries.sortedByDescending { it.value.avgR }.take(limit).forEach { (id, s) ->
                appendLine("| $id | ${s.n} | ${"%+.2f".format(s.avgR)} | ${"%.0f".format(s.winRate * 100)}% | ${verdict(id, s)} |")
            }
            val neutral = stats.filter { !it.value.directional }
            if (neutral.isNotEmpty()) {
                appendLine()
                appendLine("### ปัจจัยบริบท (ไม่มีทิศ) — วัดว่าหลังยิงแล้วราคาขยับแรงกว่าปกติไหม")
                base?.let { appendLine("เส้นฐาน: ${"%.2f".format(it)}×ATR") }
                appendLine("| ปัจจัย | n | ขยับเฉลี่ย | สถานะ |")
                appendLine("|---|---|---|---|")
                neutral.entries.sortedByDescending { it.value.avgR }.take(limit).forEach { (id, s) ->
                    appendLine("| $id | ${s.n} | ${"%.2f".format(s.avgR)}×ATR | ${verdict(id, s)} |")
                }
            }
            if (ai.isNotEmpty()) {
                appendLine()
                appendLine("### ความแม่นของ AI หลังถูกปลุก (วัดตามทิศที่ AI มอง ต่อ 1 การปลุก)")
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
        }.trim()
    }

    private fun verdict(id: String, s: FactorStat): String = when {
        s.n < MIN_SAMPLES -> "⏳ ข้อมูลยังน้อย"
        isDemoted(id) -> "⬇️ ลดชั้น (ไม่ปลุก AI)"
        s.directional && s.avgR >= 0.3 -> "✅ ใช้ได้ดี"
        s.directional -> "➖ พอใช้"
        else -> "✅ บริบทที่มีประโยชน์"
    }
}

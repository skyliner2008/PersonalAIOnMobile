package com.skyliner2008.jarvis.automation.wake

import com.skyliner2008.jarvis.db.AiWakeView
import com.skyliner2008.jarvis.db.JarvisDatabaseHolder
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import kotlin.math.abs
import kotlin.math.max

/**
 * ติดตามผล "มุมมอง" ของ AI แต่ละครั้งที่ถูกปลุก
 *
 * การเรียนรู้รายปัจจัย ([WakeLearningStore]) ตอบว่า "ปลุกด้วยปัจจัยนี้แล้วราคาไปทางไหน"
 * แต่ไม่ได้ตอบว่า **สิ่งที่ AI บอกผู้ใช้ถูกไหม** — ตัวนี้ตอบคำถามนั้น:
 *  - NOTIFY + BUY/SELL + SL/TP ครบ → ติดตามทีละแท่งว่าชน TP หรือ SL ก่อน (หน่วย R) หรือหมดเวลา
 *  - SKIP / NEUTRAL / ไม่มีระดับ → วัดว่าราคาเคลื่อนไปเท่าไรใน 12 แท่ง (SKIP ที่ดี = ราคาไม่ได้วิ่งแรง)
 *
 * ผลถูกส่งกลับเข้า prompt ของการปลุกครั้งถัดไป — AI รู้ว่าเพิ่งแจ้งอะไรไป ผลตอนนี้เป็นอย่างไร
 * และสถิติมุมมองของตัวเองบน symbol นี้ (เดิมวิเคราะห์ใหม่ทุกครั้งโดยไม่รู้ว่าเพิ่งแจ้งทิศตรงข้ามไป)
 */
object AiViewTracker {
    /** ติดตามมุมมองที่มี SL/TP สูงสุดกี่แท่งของ TF หลัก (15m → 12 ชม.) */
    const val MAX_TRACK_BARS = 48
    /** มุมมองย้อนหลังที่ใส่ใน prompt */
    private const val PROMPT_LOOKBACK_MS = 24 * 3_600_000L
    private const val PROMPT_MAX_VIEWS = 4
    const val RETENTION_DAYS = 365

    /**
     * มุมมอง SKIP เก็บสั้นกว่า — ใช้แค่ในสถิติ 30 วันของ [trackRecord]
     * (ปลุกได้ทุกนาทีตั้งแต่ P16 จึงมี SKIP วันละหลายร้อยแถว ส่วน NOTIFY เก็บเต็ม 1 ปีเพื่อดูผลย้อนหลัง)
     */
    const val SKIP_RETENTION_DAYS = 30

    data class View(
        val signalId: String, val symbol: String, val interval: String,
        val decision: String, val bias: String, val confidence: Int?, val reason: String?,
        val price: Double, val atr: Double, val levelSl: Double?, val levelTp: Double?, val createdAt: Long,
        val status: String = "OPEN", val resultR: Double? = null, val moveAtr: Double? = null,
        val mfeAtr: Double? = null, val maeAtr: Double? = null, val bars: Int? = null
    ) {
        val dir: Int get() = when (bias) { "BUY" -> 1; "SELL" -> -1; else -> 0 }
        /** มุมมองที่ติดตามแบบ SL/TP ได้ */
        val tradable: Boolean get() = decision == "NOTIFY" && dir != 0 && levelSl != null && levelTp != null &&
            abs(price - levelSl) > 0
        val risk: Double get() = levelSl?.let { abs(price - it) } ?: 0.0
        val plannedRr: Double? get() = if (tradable) abs(levelTp!! - price) / risk else null
    }

    data class Outcome(
        val status: String, val resultR: Double?, val moveAtr: Double,
        val mfeAtr: Double, val maeAtr: Double, val bars: Int
    )

    private fun AiWakeView.toView() = View(
        signal_id, symbol, interval, decision, bias, confidence?.toInt(), reason, price, atr, level_sl, level_tp,
        created_at, status, result_r, move_atr, mfe_atr, mae_atr, bars?.toInt()
    )

    // ─── บันทึก ──────────────────────────────────────────────────────────────

    fun record(signalId: String, symbol: String, interval: String, v: WakePrompt.Verdict, price: Double, atr: Double, nowMs: Long) {
        if (signalId.isBlank() || price <= 0 || atr <= 0) return
        val db = JarvisDatabaseHolder.database ?: return
        runCatching {
            db.jarvisDatabaseQueries.upsertAiView(
                signalId, symbol.uppercase(), interval.lowercase(), v.decision, v.bias, v.confidence?.toLong(),
                v.reasonTh.take(1000).ifBlank { null }, price, atr, v.levelSl, v.levelTp, nowMs
            )
        }.onFailure { logDebug("AiViewTracker", "record failed: ${it.message}") }
    }

    // ─── ติดตามผล ─────────────────────────────────────────────────────────────

    /**
     * ประเมินผลของมุมมอง 1 รายการ (pure — ทดสอบได้)
     * @return null = ยังไม่ครบ (ติดตามต่อ)
     *
     * เส้นทางราคาหลังมุมมอง = แท่ง M1 ตั้งแต่เวลาที่ AI ให้มุมมองจนถึงแท่ง TF หลักถัดไป + แท่ง TF หลักที่เปิดหลังจากนั้น
     * (ถ้าใช้แท่ง TF หลักที่กำลังก่อตัวทั้งแท่ง high/low ที่เกิด "ก่อน" มุมมองจะทำให้ชน SL/TP ปลอมได้
     *  — ถ้าไม่มี M1 ครอบคลุม จึงค่อยใช้แท่งนั้นทั้งแท่งแบบอนุรักษ์นิยม)
     * แท่งเดียวชนทั้ง SL และ TP → นับ SL (ไม่รู้ลำดับภายในแท่ง)
     */
    internal fun evaluate(v: View, closedBars: List<Candle>, tfMs: Long, m1Bars: List<Candle> = emptyList()): Outcome? {
        if (v.atr <= 0) return null
        val full = closedBars.filter { it.timestamp >= v.createdAt }
        val nextOpen = full.firstOrNull()?.timestamp ?: Long.MAX_VALUE
        val partial = if (m1Bars.isNotEmpty() && m1Bars.first().timestamp <= v.createdAt)
            m1Bars.filter { it.timestamp >= v.createdAt && it.timestamp < nextOpen }
        else closedBars.filter { it.timestamp < v.createdAt && it.timestamp + tfMs > v.createdAt }
        // (แท่ง, ลำดับแท่ง TF หลักที่ผ่านไป — ช่วงแรกนับเป็นแท่งที่ 1)
        val path = partial.map { it to 1 } + full.mapIndexed { i, b -> b to i + 1 }
        if (path.isEmpty()) return null

        if (v.tradable) {
            val sl = v.levelSl!!; val tp = v.levelTp!!
            var mfe = 0.0; var mae = 0.0
            for ((b, barNo) in path) {
                if (barNo > MAX_TRACK_BARS) break
                val fav = if (v.dir > 0) b.high - v.price else v.price - b.low
                val adv = if (v.dir > 0) v.price - b.low else b.high - v.price
                mfe = max(mfe, fav / v.atr); mae = max(mae, adv / v.atr)
                val hitSl = if (v.dir > 0) b.low <= sl else b.high >= sl
                val hitTp = if (v.dir > 0) b.high >= tp else b.low <= tp
                if (hitSl) return Outcome("SL", -1.0, -v.risk / v.atr, mfe, mae, barNo)
                if (hitTp) return Outcome("TP", v.plannedRr, abs(tp - v.price) / v.atr, mfe, mae, barNo)
            }
            if (full.size < MAX_TRACK_BARS) return null
            val move = v.dir * (full[MAX_TRACK_BARS - 1].close - v.price)
            return Outcome("EXPIRED", move / v.risk, move / v.atr, mfe, mae, MAX_TRACK_BARS)
        }

        val n = WakeLearningStore.FORWARD_HORIZON_BARS
        if (full.size < n) return null
        val w = partial + full.take(n)
        val last = full[n - 1].close
        val hi = w.maxOf { it.high }; val lo = w.minOf { it.low }
        return if (v.dir != 0) {
            val fav = if (v.dir > 0) hi - v.price else v.price - lo
            val adv = if (v.dir > 0) v.price - lo else hi - v.price
            Outcome("CLOSED", null, v.dir * (last - v.price) / v.atr, max(0.0, fav) / v.atr, max(0.0, adv) / v.atr, n)
        } else {
            // SKIP/NEUTRAL: ขนาดการเคลื่อนที่ — ถ้าราคาวิ่งแรงหลัง SKIP แปลว่าอาจพลาดโอกาส
            Outcome("CLOSED", null, abs(last - v.price) / v.atr, max(hi - v.price, v.price - lo) / v.atr, 0.0, n)
        }
    }

    fun resolve(symbol: String, interval: String, closedBars: List<Candle>, m1Bars: List<Candle> = emptyList()): Int {
        val db = JarvisDatabaseHolder.database ?: return 0
        val open = runCatching {
            db.jarvisDatabaseQueries.getOpenAiViews(symbol.uppercase(), interval.lowercase()).executeAsList()
        }.getOrElse { return 0 }
        if (open.isEmpty()) return 0
        val tfMs = TaIndicators.timeframeMillis(interval)
        var closed = 0
        runCatching {
            db.transaction {
                for (row in open) {
                    val o = evaluate(row.toView(), closedBars, tfMs, m1Bars) ?: continue
                    db.jarvisDatabaseQueries.closeAiView(
                        o.status, o.resultR, o.moveAtr, o.mfeAtr, o.maeAtr, o.bars.toLong(), kotlinx.datetime.Clock.System.now().toEpochMilliseconds(), row.signal_id
                    )
                    closed++
                }
            }
        }.onFailure { logDebug("AiViewTracker", "resolve failed: ${it.message}") }
        if (closed > 0) logDebug("AiViewTracker", "$symbol/$interval ปิดผลมุมมอง AI $closed รายการ")
        return closed
    }

    // ─── ส่งกลับเข้า prompt ────────────────────────────────────────────────────

    private fun fmt(v: Double) = formatPrice(v)
    private fun minutesAgo(t: Long, now: Long) = ((now - t) / 60_000).coerceAtLeast(0)

    /** 1 บรรทัดต่อมุมมอง — ผลที่ปิดแล้ว หรือความคืบหน้าล่าสุดถ้ายังเปิด (pure — ทดสอบได้) */
    internal fun describe(v: View, price: Double?, nowMs: Long): String = buildString {
        val ago = minutesAgo(v.createdAt, nowMs)
        append("• ${if (ago < 90) "$ago นาที" else "${ago / 60} ชม."}ที่แล้ว [${tfLabel(v.interval)}] ${v.decision} ${v.bias}")
        v.confidence?.let { append(" $it%") }
        append(" @${fmt(v.price)}")
        if (v.tradable) append(" · SL ${fmt(v.levelSl!!)} · TP ${fmt(v.levelTp!!)} (RR 1:${"%.2f".format(v.plannedRr)})")
        append(" → ")
        when (v.status) {
            "TP" -> append("✅ ชน TP แล้ว (+${"%.2f".format(v.resultR ?: 0.0)}R ใน ${v.bars} แท่ง)")
            "SL" -> append("❌ ชน SL แล้ว (−1R ใน ${v.bars} แท่ง)")
            "EXPIRED" -> append("⌛ หมดเวลาติดตาม ผล ${"%+.2f".format(v.resultR ?: 0.0)}R")
            "CLOSED" -> append(
                if (v.dir != 0) "ครบ ${v.bars} แท่ง ราคาไปตามทิศ ${"%+.1f".format(v.moveAtr ?: 0.0)} ATR"
                else "ครบ ${v.bars} แท่ง ราคาเคลื่อน ${"%.1f".format(v.moveAtr ?: 0.0)} ATR (สูงสุด ${"%.1f".format(v.mfeAtr ?: 0.0)} ATR)"
            )
            else -> {
                if (price == null || price <= 0) append("ยังติดตามอยู่")
                else if (v.tradable) {
                    val r = v.dir * (price - v.price) / v.risk
                    append("ยังเปิดอยู่ ตอนนี้ ${fmt(price)} = ${"%+.2f".format(r)}R ${if (r >= 0) "ไปตามทิศ" else "สวนทิศ"}")
                } else if (v.dir != 0) {
                    append("ตอนนี้ ${fmt(price)} = ${"%+.1f".format(v.dir * (price - v.price) / v.atr)} ATR ตามทิศ")
                } else {
                    append("ตั้งแต่นั้นราคาเคลื่อน ${"%+.1f".format((price - v.price) / v.atr)} ATR")
                }
            }
        }
        if (v.decision == "SKIP" && !v.reason.isNullOrBlank()) append(" — เหตุผลตอนนั้น: ${v.reason.take(120)}")
    }

    /**
     * สถิติมุมมองที่ติดตามจบแล้ว แยกตามทิศ (pure)
     * แยก BUY/SELL เพราะความผิดพลาดมักเอียงข้างเดียว (เช่น SELL สวนเทรนด์ขาขึ้นชน SL ติดกัน)
     */
    internal fun trackRecord(views: List<View>): String? {
        val done = views.filter { it.tradable && it.status in setOf("TP", "SL", "EXPIRED") }
        if (done.isEmpty()) return null
        fun line(label: String, list: List<View>): String {
            val tp = list.count { it.status == "TP" }; val sl = list.count { it.status == "SL" }
            val sum = list.mapNotNull { it.resultR }.sum()
            return "$label ${list.size} ครั้ง: ชน TP $tp · ชน SL $sl · หมดเวลา ${list.size - tp - sl} · รวม ${"%+.2f".format(sum)}R"
        }
        return buildString {
            append("สถิติมุมมองที่ติดตามจบแล้ว (มี SL/TP) — ")
            append(line("ทั้งหมด", done))
            listOf("BUY", "SELL").forEach { b ->
                done.filter { it.bias == b }.takeIf { it.isNotEmpty() }?.let { append(" | ").append(line(b, it)) }
            }
        }
    }

    /** ส่วนของ prompt: มุมมองล่าสุดบน symbol นี้ + สถิติของตัวเอง — null ถ้ายังไม่เคยให้มุมมอง */
    fun promptSection(symbol: String, price: Double?, nowMs: Long): String? {
        val db = JarvisDatabaseHolder.database ?: return null
        val recent = runCatching {
            db.jarvisDatabaseQueries.getRecentAiViews(symbol.uppercase(), nowMs - PROMPT_LOOKBACK_MS, PROMPT_MAX_VIEWS.toLong())
                .executeAsList().map { it.toView() }
        }.getOrElse { return null }
        val history = runCatching {
            db.jarvisDatabaseQueries.getRecentAiViews(symbol.uppercase(), nowMs - 30L * 86_400_000L, 500)
                .executeAsList().map { it.toView() }
        }.getOrDefault(emptyList())
        if (recent.isEmpty() && history.isEmpty()) return null
        return buildString {
            recent.forEach { appendLine(describe(it, price, nowMs)) }
            trackRecord(history)?.let { appendLine(it + " (30 วัน)") }
        }.trim().ifBlank { null }
    }

    // ─── ย้อนเติมมุมมองเก่า (ครั้งเดียว) ───────────────────────────────────────

    private const val KEY_BACKFILLED = "wake.views.backfilled"
    private val LEVELS = Regex("ผิดทาง ([\\d.,]+) · เป้า ([\\d.,]+)")

    /**
     * มุมมองที่ AI ตัดสินไปก่อนมีการติดตาม — สร้างจากคำตัดสินในตารางการเรียนรู้ + ระดับ SL/TP ในการ์ดแชท
     * ทำครั้งเดียวต่อเครื่อง (ติดธงใน AppSetting) แล้วการติดตามปกติจะปิดผลให้เองในรอบสแกนถัดไป
     */
    fun backfillOnce() {
        val db = JarvisDatabaseHolder.database ?: return
        val q = db.jarvisDatabaseQueries
        if (runCatching { q.getSetting(KEY_BACKFILLED).executeAsOneOrNull() }.getOrNull() == "1") return
        runCatching {
            val cards = q.getWakeAiCards().executeAsList()
            var added = 0
            db.transaction {
                for (d in q.getAiDecisionsForBackfill().executeAsList()) {
                    val price = d.price ?: continue
                    val atr = d.atr ?: continue
                    val decision = d.decision ?: continue
                    val createdAt = d.created_at ?: continue
                    // การ์ดของการปลุกนี้: symbol เดียวกัน เข้าแชทภายใน 10 นาทีหลังตรวจพบ
                    val card = cards.firstOrNull {
                        it.timestamp in createdAt..(createdAt + 10 * 60_000L) &&
                            (it.metadata ?: "").contains("\"symbol\":\"${d.symbol}@")
                    }
                    val lv = card?.let { LEVELS.find(it.content) }
                    val sl = lv?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
                    val tp = lv?.groupValues?.get(2)?.replace(",", "")?.toDoubleOrNull()
                    q.insertAiViewIfAbsent(
                        d.signal_id, d.symbol, d.interval, decision, d.bias ?: "NEUTRAL", d.confidence, d.reason,
                        price, atr, sl, tp, createdAt, "OPEN", null, null, null, null, null, null
                    )
                    added++
                }
            }
            q.insertSetting(KEY_BACKFILLED, "1")
            logDebug("AiViewTracker", "ย้อนเติมมุมมอง AI $added รายการ (จากคำตัดสินเดิม + การ์ดแชท)")
        }.onFailure { logDebug("AiViewTracker", "backfill failed: ${it.message}") }
    }

    // ─── รายงาน ──────────────────────────────────────────────────────────────

    fun reportSection(): String? {
        val db = JarvisDatabaseHolder.database ?: return null
        val rows = runCatching { db.jarvisDatabaseQueries.getAiViewStats().executeAsList() }.getOrElse { return null }
        if (rows.isEmpty()) return null
        return buildString {
            appendLine("### ผลของมุมมอง AI (ติดตามจริงว่าชน TP หรือ SL)")
            appendLine("| ตัดสิน | ทิศ | ผล | ครั้ง | เฉลี่ย R | ราคาเคลื่อน (ATR) |")
            appendLine("|---|---|---|---|---|---|")
            rows.sortedWith(compareBy({ it.decision }, { it.bias }, { it.status })).forEach {
                val r = it.avg_r?.let { x -> "%+.2f".format(x) } ?: "-"
                val mv = it.avg_move?.let { x -> "%+.1f".format(x) } ?: "-"
                appendLine("| ${it.decision} | ${it.bias} | ${it.status} | ${it.n} | $r | $mv |")
            }
            appendLine("SKIP ที่ดี = ราคาเคลื่อนน้อย · NOTIFY ที่ดี = ชน TP มากกว่า SL และเฉลี่ย R เป็นบวก")
        }.trim()
    }
}

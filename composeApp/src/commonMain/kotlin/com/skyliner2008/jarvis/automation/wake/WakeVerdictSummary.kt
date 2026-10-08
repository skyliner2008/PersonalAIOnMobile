package com.skyliner2008.jarvis.automation.wake

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * สรุปผลการคาดการณ์ล่วงหน้าให้ผู้ใช้ (`trading_signal_anticipation` action=summary) จากตาราง AiWakeView
 * (1 แถวต่อการปลุก): แจ้งเตือนกี่ครั้ง · SKIP กี่ครั้งเพราะอะไร · คาดการณ์ BUY/SELL กี่ครั้งเพราะอะไร · ผลจริง (ชน TP/SL)
 *
 * ผู้ใช้ถาม: "โหมด live ทุกโหมด สามารถสั่งให้ตรวจสอบผลของคาดการณ์ล่วงหน้าได้หรือไม่ เช่น มีแจ้งเตือนกี่ครั้งแล้ว,
 * skip กี่ครั้ง, skip เพราะอะไร, คาดการณ์ buy/sell กี่ครั้ง, เพราะเหตุผลอะไร" (2026-10-04) — เดิมมีแค่ status (นับการปลุก)
 * และ learning (ตารางผลรายปัจจัย) ไม่มีอันไหนตอบคำถามนี้ ผลเป็นบรรทัดสั้นไม่มีตาราง เพราะ Live อ่านแล้วพูดต่อ
 */
object WakeVerdictSummary {
    /** แถวสูงสุดที่อ่านมาสรุป — ปลุกได้ ~40 ครั้ง/ชม. 30 วันจึงเกินหมื่น */
    const val MAX_ROWS = 20_000L
    /**
     * หลัง SKIP ราคาปิดห่างจากจุดนั้นเกินนี้ (ATR) เมื่อครบกรอบวัดผล = ราคาวิ่งจริง อาจพลาดโอกาส
     * — วัดที่ราคาปิดท้ายกรอบ ไม่ใช่จุดสุดโต่งระหว่างทาง: ใน 12 แท่ง ราคาแกว่งเกิน 2 ATR เป็นเรื่องปกติแม้ตลาดนิ่ง
     */
    const val MISSED_MOVE_ATR = 3.0
    /** แจ้งเตือนที่แสดงทีละรายการ */
    private const val MAX_LISTED = 5
    private const val DAY_MS = 86_400_000L

    data class Period(val startMs: Long, val labelTh: String)

    /** ช่วงเวลาจาก argument `period`: today, 24h (default), 7d, 30d, all หรือ Nh / Nd */
    fun period(arg: String?, nowMs: Long, tz: TimeZone): Period {
        val a = arg?.trim()?.lowercase().orEmpty().replace(" ", "")
        Regex("^(\\d{1,3})(h|ชม|ชั่วโมง)$").find(a)?.let { m ->
            val h = m.groupValues[1].toLong().coerceIn(1, 720)
            return Period(nowMs - h * 3_600_000L, "$h ชม.ล่าสุด")
        }
        Regex("^(\\d{1,3})(d|วัน)$").find(a)?.let { m ->
            val d = m.groupValues[1].toLong().coerceIn(1, 365)
            return Period(nowMs - d * DAY_MS, "$d วันล่าสุด")
        }
        return when (a) {
            "today", "วันนี้" -> Period(
                Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date.atStartOfDayIn(tz).toEpochMilliseconds(),
                "วันนี้"
            )
            "week", "สัปดาห์", "อาทิตย์" -> Period(nowMs - 7 * DAY_MS, "7 วันล่าสุด")
            "month", "เดือน" -> Period(nowMs - 30 * DAY_MS, "30 วันล่าสุด")
            "all", "ทั้งหมด" -> Period(0L, "ทั้งหมดที่เก็บไว้")
            else -> Period(nowMs - DAY_MS, "24 ชม.ล่าสุด")
        }
    }

    /** ชื่อที่ผู้ใช้เรียก → ต้นชื่อ symbol ที่ระบบเก็บ ("ทอง" → XAUUSD, "BTC" → BTCUSDT …) */
    private val ALIASES = mapOf(
        "ทอง" to "XAU", "ทองคำ" to "XAU", "GOLD" to "XAU",
        "เงิน" to "XAG", "SILVER" to "XAG",
        "บิทคอยน์" to "BTC", "BITCOIN" to "BTC",
        "อีเธอเรียม" to "ETH", "ETHEREUM" to "ETH"
    )

    /** ชื่อไทยของสินทรัพย์ — Live พูดชื่อนี้ (ผลที่มีแต่ "BTCUSDT" ถูกพูดเป็น "ทองคำ" — phone 2026-10-04 13:36) */
    fun assetNameTh(symbol: String): String? {
        val s = symbol.uppercase()
        return when {
            s.startsWith("XAU") || s.startsWith("GOLD") -> "ทองคำ"
            s.startsWith("XAG") || s.startsWith("SILVER") -> "เงิน"
            s.startsWith("BTC") -> "บิทคอยน์"
            s.startsWith("ETH") -> "อีเธอเรียม"
            else -> null
        }
    }

    /** "BTCUSDT (บิทคอยน์)" */
    fun label(symbol: String): String = assetNameTh(symbol)?.let { "${symbol.uppercase()} ($it)" } ?: symbol.uppercase()

    fun matchesSymbol(stored: String, asked: String): Boolean {
        val a = asked.trim().uppercase().substringBefore("@")
        if (a.isBlank()) return true
        val prefix = ALIASES[a] ?: a
        return stored.uppercase().startsWith(prefix)
    }

    fun build(
        views: List<AiViewTracker.View>,
        symbol: String?,
        period: Period,
        nowMs: Long,
        tz: TimeZone,
        capped: Boolean = false
    ): String {
        val symbols = views.map { it.symbol.uppercase() }.distinct()
        // the assets by name, so the voice says which one: an answer with only "ทุกสินทรัพย์" was spoken as gold
        val scope = when {
            symbols.size == 1 -> label(symbols.single())
            symbol != null -> label(symbol)
            symbols.isEmpty() -> "ทุกสินทรัพย์"
            else -> "ทุกสินทรัพย์ (${symbols.joinToString(", ") { label(it) }})"
        }
        if (views.isEmpty()) {
            return "📭 ยังไม่มีผลการคาดการณ์ล่วงหน้า — $scope · ${period.labelTh} " +
                "(ยังไม่มีการปลุก AI ในช่วงนี้ หรือยังไม่ได้ตั้งระบบปลุกสำหรับสินทรัพย์นี้)"
        }
        val newestFirst = views.sortedByDescending { it.createdAt }
        val notify = newestFirst.filter { it.decision == "NOTIFY" }
        val skip = newestFirst.filter { it.decision != "NOTIFY" }
        val from = newestFirst.last().createdAt
        return buildString {
            appendLine("📊 ผลการคาดการณ์ล่วงหน้า (ระบบปลุก AI) — $scope · ${period.labelTh} (${time(from, nowMs, tz)} ถึง ${time(newestFirst.first().createdAt, nowMs, tz)})")
            appendLine(
                "• ปลุก AI ${views.size} ครั้ง → แจ้งเตือน (NOTIFY) ${notify.size} ครั้ง · ไม่แจ้ง (SKIP) ${skip.size} ครั้ง" +
                    if (capped) " (สรุปจาก ${views.size} ครั้งล่าสุด)" else ""
            )
            if (symbol == null) {
                val bySymbol = views.groupingBy { it.symbol }.eachCount()
                if (bySymbol.size > 1) {
                    appendLine("• แยกตามสินทรัพย์: " + bySymbol.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key} ${it.value}" })
                }
            }

            if (notify.isEmpty()) {
                appendLine("• ไม่มีการแจ้งเตือนในช่วงนี้")
            } else {
                val buy = notify.count { it.bias == "BUY" }
                val sell = notify.count { it.bias == "SELL" }
                val noDir = notify.size - buy - sell
                val notifySymbols = notify.map { it.symbol.uppercase() }.distinct()
                val which = if (notifySymbols.size == 1) " ทั้งหมดเป็น ${label(notifySymbols.single())}" else ""
                appendLine("• แจ้งเตือน ${notify.size} ครั้ง$which: คาดการณ์ BUY $buy · SELL $sell" + if (noDir > 0) " · ไม่ระบุทิศ $noDir" else "")
                appendLine("  ผลจริง: ${outcome(notify)}")
                notify.take(MAX_LISTED).forEach { appendLine("  - ${notifyLine(it, nowMs, tz)}") }
                if (notify.size > MAX_LISTED) appendLine("  (และอีก ${notify.size - MAX_LISTED} ครั้งก่อนหน้า)")
            }

            if (skip.isNotEmpty()) {
                val leanBuy = skip.count { it.bias == "BUY" }
                val leanSell = skip.count { it.bias == "SELL" }
                val avgConf = skip.mapNotNull { it.confidence }.takeIf { it.isNotEmpty() }?.average()
                appendLine(
                    "• ไม่แจ้ง ${skip.size} ครั้ง — AI มองทิศ: เป็นกลาง ${skip.size - leanBuy - leanSell} · เอียง BUY $leanBuy · เอียง SELL $leanSell" +
                        (avgConf?.let { " · ความมั่นใจเฉลี่ย ${it.toInt()}%" } ?: "")
                )
                val categories = skipCategories(skip.mapNotNull { it.reason })
                if (categories.isNotEmpty()) {
                    appendLine("  เหตุผลที่ไม่แจ้ง (นับจากคำที่ AI ใช้ — ครั้งหนึ่งอาจมีหลายเหตุผล):")
                    categories.forEach { (label, n) -> appendLine("  - $label $n ครั้ง") }
                }
                val common = commonReasons(skip)
                if (common.isNotEmpty()) {
                    appendLine("  ประโยคที่ AI ใช้บ่อยที่สุด:")
                    common.forEach { (text, n) -> appendLine("  - \"$text\"" + if (n > 1) " ×$n" else "") }
                }
                val leaning = skip.filter { it.bias == "BUY" || it.bias == "SELL" }
                if (leaning.isNotEmpty()) {
                    appendLine("  AI เอียงทิศแต่ไม่แจ้ง ${leaning.size} ครั้ง ล่าสุด:")
                    leaning.take(3).forEach {
                        appendLine("  - ${time(it.createdAt, nowMs, tz)} ${it.bias}${it.confidence?.let { c -> " $c%" } ?: ""}: ${it.reason?.take(140) ?: "-"}")
                    }
                }
                // CLOSED SKIP: moveAtr = how far the close at the end of the window is from the price then
                // (toward the lean for BUY/SELL, either way for NEUTRAL)
                val measured = skip.filter { it.status == "CLOSED" && it.moveAtr != null }
                if (measured.isNotEmpty()) {
                    val missed = measured.count { (it.moveAtr ?: 0.0) >= MISSED_MOVE_ATR }
                    appendLine(
                        "  หลังไม่แจ้ง ครบ ${WakeLearningStore.FORWARD_HORIZON_BARS} แท่งแล้วราคาไปไกลเกิน ${MISSED_MOVE_ATR.toInt()} ATR: " +
                            "$missed จาก ${measured.size} ครั้งที่วัดผลแล้ว" + if (missed == 0) " (ไม่พลาดการวิ่งจริง)" else " (อาจพลาดโอกาส)"
                    )
                }
            }
            if (period.startMs == 0L) {
                appendLine("หมายเหตุ: การไม่แจ้งเก็บย้อนหลัง ${AiViewTracker.SKIP_RETENTION_DAYS} วัน · การแจ้งเตือนเก็บ ${AiViewTracker.RETENTION_DAYS} วัน")
            }
        }.trim()
    }

    /** ผลจริงของการแจ้งเตือน: มีระดับ SL/TP = ติดตามว่าชนอะไรก่อน */
    private fun outcome(notify: List<AiViewTracker.View>): String {
        val tracked = notify.filter { it.tradable }
        val untracked = notify.size - tracked.size
        if (tracked.isEmpty()) return "ไม่มีระดับ SL/TP ให้ติดตาม ($untracked ครั้ง)"
        val tp = tracked.count { it.status == "TP" }
        val sl = tracked.count { it.status == "SL" }
        val expired = tracked.count { it.status == "EXPIRED" }
        val open = tracked.count { it.status == "OPEN" }
        val sumR = tracked.filter { it.status == "TP" || it.status == "SL" || it.status == "EXPIRED" }.mapNotNull { it.resultR }.sum()
        return "ชน TP $tp · ชน SL $sl · หมดเวลา $expired · ยังติดตาม $open · รวม ${"%+.2f".format(sumR)}R" +
            if (untracked > 0) " · ไม่มีระดับ SL/TP $untracked" else ""
    }

    private fun notifyLine(v: AiViewTracker.View, nowMs: Long, tz: TimeZone): String = buildString {
        append("${time(v.createdAt, nowMs, tz)} ${label(v.symbol)} ${v.bias}")
        v.confidence?.let { append(" $it%") }
        append(" @${formatPrice(v.price)}")
        if (v.tradable) append(" · SL ${formatPrice(v.levelSl!!)} · TP ${formatPrice(v.levelTp!!)}")
        append(" → ")
        append(
            when (v.status) {
                "TP" -> "✅ ชน TP (+${"%.2f".format(v.resultR ?: 0.0)}R)"
                "SL" -> "❌ ชน SL (−1R)"
                "EXPIRED" -> "⌛ หมดเวลาติดตาม (${"%+.2f".format(v.resultR ?: 0.0)}R)"
                "CLOSED" -> "ครบกรอบวัดผล ราคาไปตามทิศ ${"%+.1f".format(v.moveAtr ?: 0.0)} ATR"
                else -> "⏳ ยังติดตามอยู่"
            }
        )
        v.reason?.takeIf { it.isNotBlank() }?.let { append(" — เหตุผล: ${it.take(160)}") }
    }

    /**
     * หมวดเหตุผลของการไม่แจ้ง จากคำที่ AI ใช้ (เหตุผลเป็นข้อความอิสระ แต่ละครั้งไม่เหมือนกันทุกตัวอักษร)
     * การปลุกที่ระบบตัดเพราะ AI เสนอสวนเทรนด์ ([WakePrompt.enforceTrend]) นับแยกหมวดเดียว
     */
    private val CATEGORIES: List<Pair<String, Regex>> = listOf(
        "โครงสร้างแต่ละไทม์เฟรมขัดแย้งกัน" to Regex("ขัดแย้ง|สวนทาง|สวนกับ|ไม่สอดคล้อง"),
        "ตลาดเงียบ ไร้ทิศทาง หรือยังไม่ยืนยัน" to
            Regex("เงียบ|ไร้ทิศทาง|ไม่มีทิศทาง|ไม่ชัดเจน|ไซด์เวย์|sideway|กรอบแคบ|พักตัว|ยังไม่ยืนยัน|รอ(การ)?ยืนยัน", RegexOption.IGNORE_CASE),
        "สภาพคล่องหรือปริมาณซื้อขายเบาบาง" to Regex("ปริมาณ|สภาพคล่อง|วอลุ่ม|volume|เบาบาง", RegexOption.IGNORE_CASE),
        "ผันผวนผิดปกติ" to Regex("ผันผวน"),
        "Risk:Reward ไม่ถึง 1:1 หรือหาระดับไม่ได้" to Regex("\\bRR\\b|risk|reward|1\\s*:\\s*1|ความเสี่ยง", RegexOption.IGNORE_CASE),
        "มุมมองเดิมยังเปิดอยู่ (ไม่แจ้งซ้ำ)" to Regex("มุมมองเดิม|ยังเปิด|แจ้งไปแล้ว|ไม่แจ้งซ้ำ")
    )
    private const val ENFORCED_PREFIX = "ระบบไม่แจ้ง"
    private const val ENFORCED_LABEL = "ระบบตัดเพราะ AI เสนอสวนเทรนด์ใหญ่ (H4/H1/M15 เรียงทิศเดียวกัน)"
    private const val STOP_LABEL = "ระบบตัดเพราะ SL แคบกว่า 1 ATR และขยายแล้ว RR ไม่ถึง 1:1"

    internal fun skipCategories(reasons: List<String>): List<Pair<String, Int>> {
        val counts = linkedMapOf<String, Int>()
        fun add(label: String) { counts[label] = (counts[label] ?: 0) + 1 }
        reasons.forEach { r ->
            if (r.startsWith(ENFORCED_PREFIX)) {
                // the system's own SKIPs: what it says before the AI's reason ([WakePrompt.enforceTrend] / [WakePrompt.enforceStopDistance])
                add(if ("SL" in r.removePrefix(ENFORCED_PREFIX).substringBefore(" — ")) STOP_LABEL else ENFORCED_LABEL)
                return@forEach
            }
            val hits = CATEGORIES.filter { (_, re) -> re.containsMatchIn(r) }
            if (hits.isEmpty()) add("อื่นๆ") else hits.forEach { add(it.first) }
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    /** ประโยคเหตุผลที่ซ้ำกันบ่อยที่สุด (ตัดช่องว่าง/จุดท้าย) — เท่ากันให้ตัวล่าสุดก่อน */
    internal fun commonReasons(newestFirst: List<AiViewTracker.View>, top: Int = 3): List<Pair<String, Int>> =
        newestFirst.mapNotNull { v -> v.reason?.trim()?.trimEnd('.', ' ')?.take(120)?.takeIf { it.isNotBlank() } }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(top)
            .map { it.key to it.value }

    /** "HH:mm" วันนี้ / "dd/MM HH:mm" วันอื่น (เวลาเครื่อง) */
    internal fun time(ms: Long, nowMs: Long, tz: TimeZone): String {
        val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
        val hm = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
        return if (t.date == today) hm
        else "${t.dayOfMonth.toString().padStart(2, '0')}/${t.monthNumber.toString().padStart(2, '0')} $hm"
    }
}

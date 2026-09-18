package com.skyliner2008.jarvis.automation.wake

import com.skyliner2008.jarvis.db.JarvisDatabaseHolder
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import kotlinx.datetime.Clock

/**
 * action ของ tool `trading_signal_anticipation` — ระบบปลุก AI
 *
 * แยกจาก ToolExecutor เพื่อให้ทดสอบได้และไม่ผูกกับ Android
 * @param createAlert สร้าง alert job ผ่านช่องทางเดียวกับ automation_manage_alerts
 * @param scanner ใช้สแกนทันที (action scan)
 */
class AnticipationToolActions(
    private val createAlert: suspend (Map<String, String>) -> String,
    private val scanner: (suspend (String) -> Map<String, String>)?
) {
    companion object {
        /** TF ที่ระบบรองรับเป็น TF หลักของการเฝ้า */
        val SUPPORTED_TF = listOf("5m", "15m", "30m", "1h", "4h")
        const val DEFAULT_TF = "15m"
        /** "all" ขยายเป็น TF เหล่านี้ (ตัด 30m เพราะไม่อยู่ในชุด 5TF หลัก) */
        val ALL_TF = listOf("5m", "15m", "1h", "4h")
    }

    suspend fun execute(args: Map<String, String>): String {
        val action = args["action"]?.lowercase()?.trim() ?: "create"
        val symbol = (args["symbol"]?.trim()?.uppercase() ?: "XAUUSD").substringBefore("@")
        val tfArg = args["timeframe"]?.trim()?.lowercase().takeUnless { it.isNullOrBlank() } ?: DEFAULT_TF
        val tfs = if (tfArg == "all") ALL_TF
        else tfArg.split(",").map { TaIndicators.normalizeTimeframe(it.trim()) }.distinct()

        return when (action) {
            "create" -> create(symbol, tfs, args)
            "scan", "analyze" -> scan(symbol, tfs.first())
            "config", "enable", "disable" -> config(args)
            "list_factors", "factors" -> listFactors()
            "learning", "performance" -> WakeLearningStore.buildReport()
            "inspect", "history", "records" -> inspect(symbol, args["limit"]?.toLongOrNull() ?: 15L)
            "recommend" -> recommend()
            "status", "budget" -> status(args)
            else -> "❌ ไม่รองรับ action '$action' — ใช้ create, scan, list_factors, config, learning, inspect, recommend หรือ status"
        }
    }

    private suspend fun create(symbol: String, tfs: List<String>, args: Map<String, String>): String {
        val results = tfs.map { tf ->
            val res = createAlert(
                mapOf(
                    "action" to "create",
                    "name" to "ปลุก AI $symbol ${tf.uppercase()}",
                    "symbol" to "$symbol@$tf",
                    "tool_name" to AnticipationEngine.TOOL_NAME,
                    "condition_field" to AnticipationEngine.K_WAKE,
                    "condition_operator" to ">=",
                    "condition_value" to "1",
                    "delivery" to (args["delivery"]?.trim() ?: "ai"),
                    "interval_minutes" to "1",
                    "voice" to (args["voice"]?.trim() ?: "true")
                )
            )
            "${tf.uppercase()}: $res"
        }
        val enabled = WakeSettings.enabledIds().size
        return buildString {
            appendLine("⚡ **ตั้งระบบปลุก AI (คาดการณ์ล่วงหน้า) สำหรับ $symbol แล้ว**")
            appendLine("• TF หลัก: ${tfs.joinToString(", ") { it.uppercase() }} — วิเคราะห์ร่วมกับ M1/M5/M15/H1/H4 เสมอ")
            appendLine("• ปัจจัยที่เฝ้าอยู่: $enabled จาก ${WakeTriggerRegistry.ALL.size} ตัว ใน ${WakeTriggerRegistry.BY_GROUP.size} หมวด")
            appendLine("• งบการปลุก: ${WakeSettings.hourlyBudget} ครั้ง/ชม./สินทรัพย์ · ${WakeSettings.dailyBudget} ครั้ง/วัน")
            appendLine("• ปัจจัยเป็นแค่ตัวปลุก — AI จะดูภาพ 5TF แล้วตัดสินเองว่าควรแจ้งคุณหรือไม่")
            appendLine()
            results.forEach { appendLine("• $it") }
        }.trim()
    }

    private suspend fun scan(symbol: String, tf: String): String {
        val s = scanner ?: return "⚠️ ระบบสแกนยังไม่พร้อม (รอการเชื่อมต่อ)"
        val d = runCatching { s("$symbol@$tf") }.getOrElse { return "❌ สแกนไม่สำเร็จ: ${it.message}" }
        d["error"]?.let { return "❌ $it" }
        return buildString {
            appendLine("⚡ **ผลสแกนระบบปลุก — $symbol ${tf.uppercase()}** · ราคา ${d[AnticipationEngine.K_CLOSE]}")
            val events = d[AnticipationEngine.K_EVENTS].orEmpty()
            if (events.isBlank()) appendLine("ไม่มีเหตุการณ์ใหม่บนแท่งปิดล่าสุด")
            else { appendLine("**เหตุการณ์:**"); appendLine(events) }
            d[AnticipationEngine.K_STATES]?.takeIf { it.isNotBlank() }?.let { appendLine(); appendLine("**สภาวะ (บริบท):**"); appendLine(it) }
            d[AnticipationEngine.K_SUPPRESSED]?.let { appendLine(); appendLine("⏸ ไม่ปลุก: $it") }
            d[AnticipationEngine.K_MTF]?.takeIf { it.isNotBlank() }?.let { appendLine(); appendLine("**ภาพตลาด 5TF:**"); appendLine(it) }
        }.trim()
    }

    private fun config(args: Map<String, String>): String {
        fun ids(key: String) = args[key]?.split(",")?.map { it.trim().uppercase() }?.filter { it.isNotBlank() }.orEmpty()
        val unknown = (ids("add_factors") + ids("remove_factors") + ids("factors")).filter { WakeTriggerRegistry.find(it) == null }
        if (unknown.isNotEmpty()) {
            // ไม่เดาแทนผู้ใช้ — ระบบเดิมเปิดทุกปัจจัยเงียบๆ เมื่อชื่อผิด แล้วบอกว่าสำเร็จ
            return "❌ ไม่รู้จักปัจจัย: ${unknown.joinToString(", ")}\nใช้ action=list_factors เพื่อดูชื่อที่ถูกต้อง"
        }
        var off = WakeSettings.disabled
        ids("factors").takeIf { it.isNotEmpty() }?.let { only -> off = WakeTriggerRegistry.ALL.map { it.id }.filter { it !in only }.toSet() }
        off = off + ids("remove_factors") - ids("add_factors").toSet()
        WakeSettings.disabled = off
        args["hourly_budget"]?.toIntOrNull()?.let { WakeSettings.hourlyBudget = it }
        args["daily_budget"]?.toIntOrNull()?.let { WakeSettings.dailyBudget = it }
        args["cooldown_bars"]?.toIntOrNull()?.let { WakeSettings.cooldownBars = it }
        return buildString {
            appendLine("⚙️ **อัปเดตการตั้งค่าระบบปลุกแล้ว**")
            appendLine("• เปิดใช้ ${WakeSettings.enabledIds().size}/${WakeTriggerRegistry.ALL.size} ปัจจัย")
            if (off.isNotEmpty()) appendLine("• ปิดโดยผู้ใช้: ${off.sorted().joinToString(", ")}")
            appendLine("• งบ: ${WakeSettings.hourlyBudget}/ชม./สินทรัพย์ · ${WakeSettings.dailyBudget}/วัน · cooldown ${WakeSettings.cooldownBars} แท่ง")
        }.trim()
    }

    private fun listFactors(): String {
        val off = WakeSettings.disabled
        return buildString {
            appendLine("📋 **ปัจจัยปลุก AI ทั้งหมด ${WakeTriggerRegistry.ALL.size} ตัว ใน ${WakeTriggerRegistry.BY_GROUP.size} หมวด**")
            appendLine("E = เหตุการณ์ (ปลุก AI ได้) · S = สภาวะ (เป็นบริบท) · ✅ เปิด · ⛔ ปิดโดยผู้ใช้ · ⬇️ ลดชั้นโดยระบบเรียนรู้")
            EvidenceGroup.entries.forEach { g ->
                val list = WakeTriggerRegistry.BY_GROUP[g].orEmpty()
                if (list.isEmpty()) return@forEach
                appendLine()
                appendLine("**${g.labelTh}** (${list.size})")
                list.forEach { t ->
                    val mark = when {
                        t.id in off -> "⛔"
                        WakeLearningStore.isDemoted(t.id) -> "⬇️"
                        else -> "✅"
                    }
                    val st = WakeLearningStore.overall(t.id)?.let { s ->
                        if (s.directional) " · n=${s.n} ${"%+.2f".format(s.avgR)}R" else " · n=${s.n} ${"%.1f".format(s.avgR)}×ATR"
                    } ?: ""
                    appendLine("$mark `${t.id}` [${if (t.kind == TriggerKind.EVENT) "E" else "S"}] ${t.name}$st")
                }
            }
        }.trim()
    }

    private fun inspect(symbol: String, limit: Long): String {
        val db = JarvisDatabaseHolder.database ?: return "❌ ยังเข้าถึงฐานข้อมูลไม่ได้"
        val rows = runCatching { db.jarvisDatabaseQueries.getRecentFactorOutcomes(symbol, limit.coerceIn(1, 100)).executeAsList() }
            .getOrElse { return "❌ อ่านประวัติไม่สำเร็จ: ${it.message}" }
        if (rows.isEmpty()) return "📭 ยังไม่มีประวัติการยิงของปัจจัยสำหรับ $symbol"
        return buildString {
            appendLine("🔍 **ประวัติปัจจัยล่าสุด — $symbol** (${rows.size} รายการ)")
            appendLine("| ปัจจัย | TF | ทิศ | บริบท | ปลุก AI | AI ตัดสิน | ผล |")
            appendLine("|---|---|---|---|---|---|---|")
            rows.forEach { r ->
                val res = when {
                    r.status == "EXPIRED" -> "— วัดผลไม่ได้ (ข้อมูลไม่ครอบคลุม)"
                    r.status != "RESOLVED" -> "⏳ รอครบ ${WakeLearningStore.FORWARD_HORIZON_BARS} แท่ง"
                    r.side == "NEUTRAL" -> "ขยับ ${"%.1f".format(kotlin.math.abs(r.forward_r ?: 0.0))}×ATR"
                    else -> "${"%+.2f".format(r.forward_r ?: 0.0)}R"
                }
                appendLine("| ${r.factor_id} | ${r.interval} | ${r.side} | ${r.mtf_align}/${r.adx_bucket}/${r.session} | ${if (r.woke == 1L) "✅" else "—"} | ${r.ai_decision ?: "—"}${r.ai_bias?.let { "/$it" } ?: ""} | $res |")
            }
        }.trim()
    }

    private fun recommend(): String {
        val stats = WakeLearningStore.allOverall().filter { it.value.reliable }
        if (stats.isEmpty()) {
            return "⏳ ยังไม่มีปัจจัยที่สถิติพอ (ต้องการ n ≥ ${WakeLearningStore.MIN_SAMPLES}) — ปล่อยให้ระบบเก็บข้อมูลต่อก่อน"
        }
        val good = stats.filter { it.value.directional && it.value.avgR > 0.2 }.entries.sortedByDescending { it.value.avgR }.take(10)
        val bad = WakeLearningStore.allOverall().keys.filter { WakeLearningStore.isDemoted(it) }
        return buildString {
            appendLine("💡 **คำแนะนำจากผลการเรียนรู้**")
            if (good.isNotEmpty()) {
                appendLine("ปัจจัยที่ให้ผลดีที่สุด:")
                good.forEach { (id, s) -> appendLine("• `$id` — n=${s.n} avg ${"%+.2f".format(s.avgR)}R ไปตามทิศ ${"%.0f".format(s.winRate * 100)}%") }
            }
            if (bad.isNotEmpty()) {
                appendLine()
                appendLine("ปัจจัยที่ระบบลดชั้นแล้ว (ยังเก็บสถิติ แต่ไม่ปลุก AI): ${bad.joinToString(", ")}")
            }
        }.trim()
    }

    private fun status(args: Map<String, String>): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val u = WakeGovernor.usage(now)
        return buildString {
            appendLine("📊 **สถานะระบบปลุก AI**")
            appendLine("• ปลุกวันนี้: ${u.today}/${u.dailyBudget} ครั้ง")
            if (u.perSymbolLastHour.isNotEmpty()) {
                appendLine("• ชั่วโมงที่ผ่านมา: " + u.perSymbolLastHour.entries.joinToString(", ") { "${it.key} ${it.value}/${WakeSettings.hourlyBudget}" })
            }
            appendLine("• ปัจจัยเปิดใช้: ${WakeSettings.enabledIds().size}/${WakeTriggerRegistry.ALL.size}")
            val demoted = WakeLearningStore.allOverall().keys.count { WakeLearningStore.isDemoted(it) }
            if (demoted > 0) appendLine("• ลดชั้นโดยระบบเรียนรู้: $demoted ตัว")
        }.trim()
    }
}

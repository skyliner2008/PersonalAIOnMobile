package com.skyliner2008.jarvis.service

import com.skyliner2008.jarvis.db.AlertJob
import com.skyliner2008.jarvis.automation.*
import kotlinx.serialization.json.*

/**
 * Pure presentation & text-formatting helper functions extracted from JarvisAutomationService.
 * Stateless object — no Android context or coroutine dependencies.
 */
object AlertPresentationFormatter {

    fun stripCodeFences(text: String): String =
        text.replace(Regex("```[\\s\\S]*?```"), " ")
            .replace("```", " ")
            .replace(Regex("\\s+"), " ")
            .trim()


    fun conditionText(job: AlertJob): String = runCatching {
        val c = com.skyliner2008.jarvis.automation.automationJson
            .decodeFromString<com.skyliner2008.jarvis.automation.AutomationCondition>(job.condition_json)
        val op = when (c.operator) {
            com.skyliner2008.jarvis.automation.ConditionOperator.GT -> ">"
            com.skyliner2008.jarvis.automation.ConditionOperator.GTE -> ">="
            com.skyliner2008.jarvis.automation.ConditionOperator.LT -> "<"
            com.skyliner2008.jarvis.automation.ConditionOperator.LTE -> "<="
            com.skyliner2008.jarvis.automation.ConditionOperator.EQ -> "=="
            com.skyliner2008.jarvis.automation.ConditionOperator.CONTAINS -> "contains"
        }
        "${c.field} $op ${c.value}"
    }.getOrElse { job.condition_json }


    fun signalChatMeta(job: AlertJob, data: Map<String, String>, aiSummary: String?, type: String, voice: String? = null): String =
        kotlinx.serialization.json.buildJsonObject {
            put("type", type)
            put("kind", "signal")
            put("job_id", job.id)
            put("side", data["signal_side"] ?: "-")
            put("symbol", job.symbol)
            put("strategy", data["signal_strategy"] ?: "-")
            put("entry", data["signal_entry"] ?: "-")
            put("tp", data["signal_tp"] ?: "-")
            put("sl", data["signal_sl"] ?: "-")
            put("rr", data["signal_rr"] ?: "-")
            data["signal_atr"]?.let { put("atr", it) }
            data["signal_supervisor"]?.let { put("supervisor", it) }
            put("reason", data["signal_reason"] ?: "-")
            aiSummary?.takeIf { it.isNotBlank() }?.let { put("summary", it) }
            voice?.let { put("voice", it) }
        }.toString()

    // ─── ระบบปลุก AI (คาดการณ์ล่วงหน้า) ─────────────────────────────────────

    private val WAKE = com.skyliner2008.jarvis.automation.wake.AnticipationEngine

    /** บรรทัดเหตุการณ์สำหรับผู้ใช้ — ตัดรหัสปัจจัย/สถิติ (ส่วนนั้นมีไว้ให้ AI อ่าน) */
    internal fun wakeEventLinesForUser(data: Map<String, String>): List<String> =
        (data[WAKE.K_EVENTS] ?: "").lines()
            .map { it.trim().removePrefix("•").trim().replace(Regex("\\s*\\([A-Z0-9_]+ · สถิติ:.*\\)\\s*$"), "") }
            .filter { it.isNotBlank() }

    private fun wakeSide(data: Map<String, String>, verdict: com.skyliner2008.jarvis.automation.wake.WakePrompt.Verdict?): String {
        verdict?.let { return it.bias }
        val buy = data[WAKE.K_BUY]?.toIntOrNull() ?: 0
        val sell = data[WAKE.K_SELL]?.toIntOrNull() ?: 0
        return when { buy > sell -> "BUY"; sell > buy -> "SELL"; else -> "NEUTRAL" }
    }

    private fun fmtLevel(v: Double?): String? = v?.let { if (it >= 100) "%.2f".format(it) else "%.5f".format(it).trimEnd('0').trimEnd('.') }

    /**
     * บรรทัดระดับราคาที่ AI ให้ + Risk:Reward จากราคาปัจจุบัน — null ถ้า AI ไม่ได้ให้ระดับ
     * RR ต่ำกว่า 1:1 ติดคำเตือน (prompt สั่งให้ SKIP แล้ว แต่ถ้า AI ยังแจ้งมา ผู้ใช้ต้องเห็น)
     */
    fun wakeLevelsLine(data: Map<String, String>, verdict: com.skyliner2008.jarvis.automation.wake.WakePrompt.Verdict?): String? {
        verdict ?: return null
        val sl = fmtLevel(verdict.levelSl); val tp = fmtLevel(verdict.levelTp)
        if (sl == null && tp == null) return null
        val rr = verdict.rr(data[WAKE.K_CLOSE]?.replace(",", "")?.toDoubleOrNull())
        return listOfNotNull(
            sl?.let { "ผิดทาง $it" },
            tp?.let { "เป้า $it" },
            rr?.let { "RR 1:${"%.2f".format(it)}" + if (it < com.skyliner2008.jarvis.automation.wake.WakePrompt.MIN_RR) " ⚠️ ต่ำกว่า 1:1" else "" }
        ).joinToString(" · ")
    }

    /** คำเตือนสำหรับเสียงพูด เมื่อ RR ต่ำกว่า 1:1 */
    fun wakeRrWarningTh(data: Map<String, String>, verdict: com.skyliner2008.jarvis.automation.wake.WakePrompt.Verdict?): String? {
        val rr = verdict?.rr(data[WAKE.K_CLOSE]?.replace(",", "")?.toDoubleOrNull()) ?: return null
        return if (rr < com.skyliner2008.jarvis.automation.wake.WakePrompt.MIN_RR)
            "ระวัง ระยะถึงเป้าสั้นกว่าระยะถึงจุดผิดทาง อาร์อาร์ต่ำกว่าหนึ่งต่อหนึ่ง" else null
    }

    /** metadata การ์ดปลุก AI — MessageBubble render ด้วย kind "anticipation" */
    fun wakeChatMeta(
        job: AlertJob, data: Map<String, String>,
        verdict: com.skyliner2008.jarvis.automation.wake.WakePrompt.Verdict?,
        liveSummary: String?, type: String, voice: String? = null
    ): String = kotlinx.serialization.json.buildJsonObject {
        put("type", type)
        put("kind", "anticipation")
        put("job_id", job.id)
        put("name", job.name)
        val (baseSym, tf) = com.skyliner2008.jarvis.automation.IndicatorAlertProvider.splitSymbolAndTf(job.symbol)
        put("symbol", "$baseSym@$tf")
        put("side", wakeSide(data, verdict))
        put("zone", data[WAKE.K_TRIGGERS] ?: "-")
        put("desc", wakeEventLinesForUser(data).joinToString("\n").ifBlank { "-" })
        put("confidence", verdict?.confidence?.toString() ?: "-")
        put("price", data[WAKE.K_CLOSE] ?: "-")
        wakeLevelsLine(data, verdict)?.let { put("levels", it) }
        data[WAKE.K_MTF]?.takeIf { it.isNotBlank() }?.let { put("mtf", it) }
        (liveSummary ?: verdict?.summaryTh)?.takeIf { it.isNotBlank() }?.let { put("summary", it) }
        voice?.let { put("voice", it) }
    }.toString()

        /** metadata โครงสร้างของ keyzone hit — MessageBubble render เป็นการ์ด 3D แจ้งเตือนโซนสำคัญ */
    fun keyzoneChatMeta(job: AlertJob, data: Map<String, String>, aiSummary: String?, type: String, voice: String? = null): String =
        kotlinx.serialization.json.buildJsonObject {
            put("type", type)
            put("kind", "keyzone")
            put("job_id", job.id)
            put("name", job.name)
            put("symbol", job.symbol)
            put("desc", data["signal_keyzone_desc"]?.ifBlank { "ราคาแตะจุดสำคัญของโครงสร้างตลาด" } ?: "ราคาแตะจุดสำคัญของโครงสร้างตลาด")
            put("price", data["close"] ?: "-")
            data["signal_mtf_context"]?.takeIf { it.isNotBlank() }?.let { put("mtf", it) }
            aiSummary?.takeIf { it.isNotBlank() }?.let { put("summary", it) }
            voice?.let { put("voice", it) }
        }.toString()

    /** metadata โครงสร้างของ alert ทั่วไป (ราคา/indicator/ฯลฯ) — MessageBubble render เป็นการ์ด cyan */
    fun alertChatMeta(job: AlertJob, value: String, aiSummary: String?, type: String, voice: String? = null): String =
        kotlinx.serialization.json.buildJsonObject {
            put("type", type)
            put("kind", "alert")
            put("job_id", job.id)
            put("name", job.name)
            put("symbol", job.symbol)
            put("condition", conditionText(job))
            put("current", value)
            aiSummary?.takeIf { it.isNotBlank() }?.let { put("summary", it) }
            voice?.let { put("voice", it) }
        }.toString()


    /** ข้อความ notification ของการปลุก AI */
    fun buildWakeNotification(
        job: AlertJob, data: Map<String, String>,
        verdict: com.skyliner2008.jarvis.automation.wake.WakePrompt.Verdict?
    ): String {
        val events = wakeEventLinesForUser(data)
        return if (verdict != null) {
            "⏰ ${job.symbol} · ${verdict.bias}${verdict.confidence?.let { " $it%" } ?: ""} — ${verdict.summaryTh.ifBlank { verdict.reasonTh }}"
        } else {
            "⏰ ${job.symbol} @ ${data[WAKE.K_CLOSE] ?: "-"} — " + events.take(3).joinToString(" | ").ifBlank { "มีเหตุการณ์ใหม่บนกราฟ" }
        }
    }

    /** การ์ดแชท: เหตุการณ์ที่ปลุก + (โหมด ai) คำวิเคราะห์ของ AI และระดับราคาที่ AI กำหนดเอง */
    fun buildWakeChatCard(
        job: AlertJob, data: Map<String, String>,
        verdict: com.skyliner2008.jarvis.automation.wake.WakePrompt.Verdict?
    ): String {
        val (sym, tf) = com.skyliner2008.jarvis.automation.IndicatorAlertProvider.splitSymbolAndTf(job.symbol)
        val side = wakeSide(data, verdict)
        val badge = when (side) { "BUY" -> "⏰🟢"; "SELL" -> "⏰🔴"; else -> "⏰" }
        return buildString {
            appendLine("$badge **${job.name} — $sym (${tf.uppercase()})** · ราคา ${data[WAKE.K_CLOSE] ?: "-"}")
            appendLine()
            appendLine("**เหตุการณ์ที่ปลุก AI:**")
            wakeEventLinesForUser(data).forEach { appendLine("• $it") }
            if (verdict != null) {
                appendLine()
                appendLine("**มุมมอง AI:** ${verdict.bias}${verdict.confidence?.let { " ($it%)" } ?: ""}")
                wakeLevelsLine(data, verdict)?.let { appendLine("ระดับที่ AI จับตา: $it") }
                if (verdict.summaryTh.isNotBlank()) {
                    appendLine()
                    appendLine("**JARVIS:** ${verdict.summaryTh}")
                }
            }
        }.trim()
    }

    /** เสียง: ใช้สรุปที่ AI เขียนแล้ว (สั้น) — ถ้าไม่มี AI พูดเหตุการณ์แรก */
    fun buildWakeSpeech(
        job: AlertJob, data: Map<String, String>,
        verdict: com.skyliner2008.jarvis.automation.wake.WakePrompt.Verdict?
    ): String {
        val sym = job.symbol.substringBefore("@")
        val symTh = when (sym.uppercase()) {
            "XAUUSD" -> "ทองคำ"; "XAGUSD" -> "เงินแท่ง"
            "BTCUSDT", "BTCUSD" -> "บิทคอยน์"; "ETHUSDT", "ETHUSD" -> "อีเทอเรียม"
            else -> sym
        }
        verdict?.summaryTh?.takeIf { it.isNotBlank() }?.let { summary ->
            val warn = wakeRrWarningTh(data, verdict)?.let { " $it" } ?: ""
            return sanitizeForSpeech("$symTh: $summary$warn")
        }
        val first = wakeEventLinesForUser(data).firstOrNull() ?: "มีเหตุการณ์ใหม่บนกราฟ"
        return sanitizeForSpeech("แจ้งเตือน $symTh $first")
    }

    /**
     * การ์ดสัญญาณสำหรับแชท — header BUY🟢/SELL🔴 + ตาราง Entry/TP/SL/RR/ATR + เหตุผล
     * (MessageBubble รองรับ markdown table + **bold** อยู่แล้ว จึงใช้ format นี้ได้ทันที)
     * @param aiSummary ข้อความ quick-check จาก AI (โหมด ai เท่านั้น) — ใส่ต่อท้ายการ์ดถ้ามี
     */
    fun buildSignalChatCard(job: AlertJob, data: Map<String, String>, aiSummary: String?): String {
        val side = data["signal_side"] ?: "-"
        val badge = if (side == "BUY") "🟢" else "🔴"
        return buildString {
            appendLine("$badge **สัญญาณ $side — ${job.symbol}**")
            appendLine("กลยุทธ์: ${data["signal_strategy"] ?: "-"}")
            appendLine()
            appendLine("| รายการ | ค่า |")
            appendLine("|---|---|")
            appendLine("| Entry | ${data["signal_entry"] ?: "-"} |")
            appendLine("| TP | ${data["signal_tp"] ?: "-"} |")
            appendLine("| SL | ${data["signal_sl"] ?: "-"} |")
            appendLine("| RR | 1:${data["signal_rr"] ?: "-"} |")
            data["signal_atr"]?.let { appendLine("| ATR14 | $it |") }
            appendLine()
            appendLine("**เหตุผล:** ${data["signal_reason"] ?: "-"}")
            if (!aiSummary.isNullOrBlank()) {
                appendLine()
                appendLine("**JARVIS quick-check:** $aiSummary")
            }
        }.trim()
    }

    /**
     * การ์ด alert ทั่วไปสำหรับแชท (ราคา/indicator/SMC/sentiment ฯลฯ) —
     * header + ตารางเงื่อนไข/ค่าปัจจุบัน + ข้อมูลประกอบจาก provider + quick-check (ถ้ามี)
     * แทนข้อความยาวติดกันที่อ่านยาก
     */
    fun buildAlertChatCard(job: AlertJob, value: String, data: Map<String, String>, aiSummary: String?): String {
        val condText = conditionText(job)
        return buildString {
            appendLine("🎯 **Alert: ${job.name}**")
            appendLine(job.symbol)
            appendLine()
            appendLine("| รายการ | ค่า |")
            appendLine("|---|---|")
            appendLine("| เงื่อนไข | $condText |")
            appendLine("| ค่าปัจจุบัน | $value |")
            if (!aiSummary.isNullOrBlank()) {
                appendLine()
                appendLine("**JARVIS quick-check:** $aiSummary")
            }
        }.trim()
    }


    /** ข้อความพูดสั้นๆ สำหรับ signal alert — ลดเวลา synthesize/ฟังของ Gemini TTS (ข้อความยาว = ดีเลย์สูง) */
    fun buildSignalSpeech(job: AlertJob, data: Map<String, String>): String {
        if (data["signal_side"] == null) {   // keyzone watch — ยังไม่มีสัญญาณเทรด
            val symK = job.symbol.substringBefore("@")
            val symThK = when (symK.uppercase()) {
                "XAUUSD" -> "ทองคำ"; "XAGUSD" -> "เงินแท่ง"
                "BTCUSDT", "BTCUSD" -> "บิทคอยน์"; "ETHUSDT", "ETHUSD" -> "อีเทอเรียม"
                else -> symK
            }
            return "ราคา $symThK เคลื่อนไปแตะจุดสำคัญของโครงสร้างตลาด โปรดติดตามกราฟ"
        }
        val sideTh = if (data["signal_side"] == "BUY") "ซื้อ" else "ขาย"
        val sym = job.symbol.substringBefore("@")
        val symTh = when (sym.uppercase()) {
            "XAUUSD" -> "ทองคำ"; "XAGUSD" -> "เงินแท่ง"
            "BTCUSDT", "BTCUSD" -> "บิทคอยน์"; "ETHUSDT", "ETHUSD" -> "อีเทอเรียม"
            else -> sym
        }
        val tfTh = when (job.symbol.substringAfter("@", "").uppercase()) {
            "5M", "M5" -> " 5 นาที"; "15M", "M15" -> " 15 นาที"; "30M", "M30" -> " 30 นาที"
            "1H", "H1" -> " 1 ชั่วโมง"; "4H", "H4" -> " 4 ชั่วโมง"; "1D", "D1" -> " รายวัน"
            else -> ""
        }
        return "สัญญาณ$sideTh $symTh$tfTh จากกลยุทธ์ ${data["signal_strategy"] ?: ""} " +
            "จุดเข้า ${data["signal_entry"] ?: "-"} สต็อปลอส ${data["signal_sl"] ?: "-"} เทคโพรฟิต ${data["signal_tp"] ?: "-"}"
    }

    /** ข้อความพูดสั้นๆ สำหรับ alert ทั่วไป — ประหยัดโควต้า Gemini TTS (คิดค่าตามตัวอักษร/จำกัดปริมาณ)
     *  เดิมพูด aiText เต็ม (~200+ ตัวอักษร) ทุก alert → เปลี่ยนเป็น template สั้น ~40-60 ตัวอักษร */
    fun buildAlertSpeech(job: AlertJob, value: String): String {
        val sym = job.symbol.substringBefore("@")
        val symTh = when (sym.uppercase()) {
            "XAUUSD" -> "ทองคำ"; "XAGUSD" -> "เงินแท่ง"
            "BTCUSDT", "BTCUSD" -> "บิทคอยน์"; "ETHUSDT", "ETHUSD" -> "อีเทอเรียม"
            else -> sym
        }
        return "แจ้งเตือน ${job.name} $symTh เข้าเงื่อนไขแล้ว ค่าปัจจุบัน $value"
    }


    fun buildKeyzoneChatCard(job: AlertJob, data: Map<String, String>, aiSummary: String?): String =
        buildString {
            appendLine("📍 **${job.symbol} แตะจุดสำคัญของโครงสร้างตลาด**")
            appendLine()
            appendLine("**จุดที่แตะ:** ${data["signal_keyzone_desc"]?.ifBlank { "-" } ?: "-"}")
            val mtf = data["signal_mtf_context"]?.takeIf { it.isNotBlank() }
            if (mtf != null) {
                appendLine()
                appendLine(mtf)
            }
            if (!aiSummary.isNullOrBlank()) {
                appendLine()
                appendLine("**JARVIS quick-check:** $aiSummary")
            }
        }.trim()
    // ─── Natural Voice (Gemini TTS) — เสียงแจ้งเตือนแบบคน ไม่ใช่ TTS หุ่นยนต์ ────

    fun sanitizeForSpeech(text: String): String =
        text.replace(Regex("[*_#`|>~]"), " ")
            .replace(Regex("[\\p{So}\\p{Sk}]"), " ") // emoji/symbol อื่น
            .replace(Regex("\\s+"), " ")
            .trim()

}

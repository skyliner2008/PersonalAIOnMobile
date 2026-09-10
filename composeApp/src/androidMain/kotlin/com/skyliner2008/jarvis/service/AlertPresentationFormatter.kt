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

    /** metadata โครงสร้างของ anticipation alert (Pre-signal) — MessageBubble render เป็นการ์ด 3D คาดการณ์ */
    fun anticipationChatMeta(job: AlertJob, data: Map<String, String>, aiSummary: String?, type: String, voice: String? = null): String =
        kotlinx.serialization.json.buildJsonObject {
            put("type", type)
            put("kind", "anticipation")
            put("job_id", job.id)
            put("name", job.name)
            val (baseSym, tf) = com.skyliner2008.jarvis.automation.IndicatorAlertProvider.splitSymbolAndTf(job.symbol)
            put("symbol", "$baseSym@$tf")
            put("side", data["signal_anticipation_side"]?.ifBlank { "BUY" } ?: "BUY")
            put("zone", data["signal_anticipation_zone"]?.ifBlank { "Keyzone" } ?: "Keyzone")
            put("desc", data["signal_anticipation_desc"]?.ifBlank { "เฝ้าระวังการกลับตัวในโซนสำคัญ" } ?: "เฝ้าระวังการกลับตัวในโซนสำคัญ")
            put("confidence", data["signal_anticipation_confidence"]?.ifBlank { "75" } ?: "75")
            put("price", data["close"] ?: "-")
            data["signal_mtf_context"]?.takeIf { it.isNotBlank() }?.let { put("mtf", it) }
            aiSummary?.takeIf { it.isNotBlank() }?.let { put("summary", it) }
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


    /**
     * การ์ดคาดการณ์สัญญาณล่วงหน้าสำหรับแชท — กะทัดรัด แสดงเฉพาะปัจจัยที่เกิด ไม่รกพื้นที่
     */
    fun buildAnticipationChatCard(job: AlertJob, data: Map<String, String>, aiSummary: String?): String {
        val side = data["signal_anticipation_side"]?.ifBlank { "BUY" } ?: "BUY"
        val badge = if (side.equals("BUY", ignoreCase = true)) "⚡🟢" else "⚡🔴"
        val conf = data["signal_anticipation_confidence"]?.ifBlank { "75" } ?: "75"
        val close = data["close"] ?: "-"
        val desc = data["signal_anticipation_desc"]?.ifBlank { "เฝ้าระวังการกลับตัวในโซนสำคัญ" } ?: "เฝ้าระวังการกลับตัวในโซนสำคัญ"
        val (sym, tf) = com.skyliner2008.jarvis.automation.IndicatorAlertProvider.splitSymbolAndTf(job.symbol)
        return buildString {
            appendLine("$badge **คาดการณ์ $side — $sym (${tf.uppercase()})**")
            appendLine("ความเชื่อมั่น: $conf% • ราคา: $close")
            appendLine()
            appendLine("**ปัจจัยที่เกิด:** $desc")
            if (!aiSummary.isNullOrBlank()) {
                appendLine()
                appendLine("**สิ่งที่ต้องจับตามอง:** $aiSummary")
            }
        }.trim()
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


    fun buildAnticipationSpeech(job: AlertJob, data: Map<String, String>): String {
        val isBuy = (data["signal_anticipation_side"] ?: "BUY").equals("BUY", ignoreCase = true)
        val dirTh = if (isBuy) "ฝั่งซื้อเริ่มได้เปรียบ ลุ้นกลับตัวขึ้น" else "ฝั่งขายเริ่มได้เปรียบ ลุ้นทิ้งตัวลง"
        val (sym, tf) = com.skyliner2008.jarvis.automation.IndicatorAlertProvider.splitSymbolAndTf(job.symbol)
        val symTh = when (sym.uppercase()) {
            "XAUUSD" -> "ทองคำ"; "XAGUSD" -> "เงินแท่ง"
            "BTCUSDT", "BTCUSD" -> "บิทคอยน์"; "ETHUSDT", "ETHUSD" -> "อีเทอเรียม"
            else -> sym
        }
        val tfTh = when (tf.lowercase()) {
            "1m" -> "1 นาที"; "5m" -> "5 นาที"; "15m" -> "15 นาที"; "30m" -> "30 นาที"
            "1h" -> "1 ชั่วโมง"; "4h" -> "4 ชั่วโมง"; "1d" -> "รายวัน"; "1w" -> "รายสัปดาห์"
            else -> tf
        }
        val zone = data["signal_anticipation_zone"]?.ifBlank { "โซนสำคัญ" } ?: "โซนสำคัญ"
        return "คาดการณ์ $symTh ไทม์เฟรม $tfTh ที่$zone $dirTh ให้จับตาดูการปิดแท่งเทียนนะคะ"
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

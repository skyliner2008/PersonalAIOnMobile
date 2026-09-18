package com.skyliner2008.jarvis.automation.wake

/**
 * WakePrompt — สิ่งที่ AI เห็นเมื่อถูกปลุก และรูปแบบคำตอบที่คาดหวัง
 *
 * [หลักการ] บอกแค่ "เกิดอะไรขึ้น" + ภาพตลาด 5TF + สถิติจริงของปัจจัยที่ปลุก
 * **ไม่มี Entry/SL/TP สำเร็จรูป ไม่มีทิศทางที่สรุปไว้ก่อน** — AI เป็นคนวิเคราะห์และกำหนดเอง
 * (ระบบเดิมยัด "Execution Rails คาดหมาย" ที่ปัจจัยแต่งขึ้นใส่ prompt ก่อน
 *  ทำให้ AI ถูก anchor ด้วยตัวเลขปลอมแล้วถูกถามแค่ APPROVE/VETO)
 */
object WakePrompt {

    data class Verdict(
        /** NOTIFY = น่าสนใจพอจะรบกวนผู้ใช้ | SKIP = ไม่ต้องแจ้ง */
        val decision: String,
        val bias: String,
        val levelSl: Double?,
        val levelTp: Double?,
        val confidence: Int?,
        val reasonTh: String,
        val summaryTh: String
    ) {
        val notify: Boolean get() = decision == "NOTIFY"
    }

    /**
     * system prompt เฉพาะงานนี้ — แทน system prompt แชทของ JARVIS (ยาวหลายหมื่นตัวอักษร
     * มีกฎ tool/กราฟ/อุปกรณ์ที่ไม่เกี่ยว และขัดกับรูปแบบคำตอบ 7 บรรทัด)
     */
    const val SYSTEM_PROMPT =
        "คุณคือนักวิเคราะห์เทคนิคของ JARVIS ตอบเป็นภาษาไทย วิเคราะห์จากข้อมูลที่ระบบคำนวณให้เท่านั้น " +
            "ห้ามแต่งตัวเลข ราคา ข่าว หรืออินดิเคเตอร์ที่ไม่มีในข้อมูล ห้ามรับประกันผลกำไร " +
            "ตอบตามรูปแบบที่กำหนดเป๊ะๆ เป็นข้อความธรรมดา ไม่ใช้ markdown ไม่ใส่คำอธิบายนอกรูปแบบ"

    /**
     * ตัดระดับราคาที่ AI ให้มาแต่ผิดตรรกะทิ้ง (ไม่ใช่แก้ให้ — เราไม่เดาแทน AI)
     * BUY: SL ต้องต่ำกว่าราคา TP ต้องสูงกว่า / SELL กลับกัน / NEUTRAL ไม่มีทิศ จึงไม่ตรวจ
     * และต้องอยู่ในระยะที่สมเหตุสมผล (≤ 20% จากราคา) — กันเลขพิมพ์ผิดหลักทศนิยม
     */
    fun sanitize(v: Verdict, price: Double?): Verdict {
        if (price == null || price <= 0) return v
        fun sane(x: Double?) = x?.takeIf { kotlin.math.abs(it - price) / price <= 0.20 }
        var sl = sane(v.levelSl)
        var tp = sane(v.levelTp)
        when (v.bias) {
            "BUY" -> { if (sl != null && sl >= price) sl = null; if (tp != null && tp <= price) tp = null }
            "SELL" -> { if (sl != null && sl <= price) sl = null; if (tp != null && tp >= price) tp = null }
        }
        return if (sl == v.levelSl && tp == v.levelTp) v else v.copy(levelSl = sl, levelTp = tp)
    }

    fun build(symbol: String, data: Map<String, String>): String = buildString {
        val tf = data[AnticipationEngine.K_TIMEFRAME] ?: "-"
        appendLine("บทบาท: คุณคือนักวิเคราะห์เทคนิค — ระบบเฝ้าระวังเพิ่งปลุกคุณเพราะตรวจพบเหตุการณ์บนกราฟ")
        appendLine("คุณมองไม่เห็นกราฟ ข้อมูลด้านล่างคือสิ่งที่ระบบคำนวณจากแท่งเทียนจริงให้แล้ว (deterministic)")
        appendLine("หน้าที่ของคุณ: ดูภาพรวมทั้ง 5 ไทม์เฟรม แล้วตัดสินเองว่ามีอะไรน่าสนใจพอจะแจ้งผู้ใช้หรือไม่")
        appendLine()
        appendLine("══ สินทรัพย์ ══")
        appendLine("$symbol · TF หลัก ${tf.uppercase()} · ราคาปัจจุบัน ${data[AnticipationEngine.K_CLOSE] ?: "-"}")
        data[AnticipationEngine.K_TIME]?.let { appendLine("เวลา: $it") }
        appendLine()
        appendLine("══ เหตุการณ์ที่ปลุกคุณ (${data[AnticipationEngine.K_EVENT_COUNT] ?: "?"} รายการ) ══")
        appendLine(data[AnticipationEngine.K_EVENTS]?.ifBlank { "-" } ?: "-")
        val states = data[AnticipationEngine.K_STATES]
        if (!states.isNullOrBlank()) {
            appendLine()
            appendLine("══ สภาวะที่เป็นอยู่ตอนนี้ (บริบท) ══")
            appendLine(states)
        }
        appendLine()
        appendLine("══ โครงสร้างตลาด 5 ไทม์เฟรม ══")
        appendLine(data[AnticipationEngine.K_MTF]?.ifBlank { "-" } ?: "-")
        appendLine()
        appendLine("แนวทางการวิเคราะห์:")
        appendLine("- ทิศทางที่แต่ละเหตุการณ์ชี้เป็นแค่ข้อสังเกตจากอินดิเคเตอร์ ไม่ใช่ข้อสรุป — ชั่งน้ำหนักเองจากโครงสร้าง H4/H1 และการยืนยันของ M5")
        appendLine("- \"สถิติ\" คือผลจริงที่ระบบเคยวัดได้เมื่อปัจจัยนั้นยิงในอดีต — ให้น้ำหนักกับตัวที่สถิติดีและเชื่อถือได้ (n มาก)")
        appendLine("- เหตุการณ์ที่ปลุกอาจไม่ใช่สิ่งสำคัญที่สุด ถ้าเห็นอย่างอื่นที่สำคัญกว่าจากข้อมูล ให้ยึดสิ่งนั้น")
        appendLine("- ถ้าหลักฐานขัดแย้งกันหรือไม่ชัดเจน ให้ตอบ SKIP (ไม่ต้องรบกวนผู้ใช้)")
        appendLine("- ถ้าน่าสนใจ กำหนดระดับราคาเองจากแนวรับ/แนวต้าน/swing/OB ที่ให้มาเท่านั้น")
        appendLine("- ใช้เฉพาะข้อมูลข้างบน ห้ามสมมติข่าว ราคา หรืออินดิเคเตอร์อื่น ห้ามรับประกันผลกำไร")
        appendLine()
        appendLine("ตอบตามรูปแบบนี้เท่านั้น 7 บรรทัด:")
        appendLine("DECISION: NOTIFY หรือ SKIP")
        appendLine("BIAS: BUY หรือ SELL หรือ NEUTRAL")
        appendLine("LEVEL_SL: ราคา หรือ -")
        appendLine("LEVEL_TP: ราคา หรือ -")
        appendLine("CONFIDENCE: 0-100")
        appendLine("REASON_TH: เหตุผลสั้นๆ 1 ประโยค")
        appendLine("SUMMARY_TH: สรุปให้ผู้ใช้ 2-4 ประโยค (เกิดอะไร โครงสร้างเป็นอย่างไร ควรจับตาอะไร)")
    }

    /** แยกคำตอบของ AI — รับได้ทั้งแบบหลายบรรทัดและแบบถูกบีบเป็นบรรทัดเดียว */
    fun parse(raw: String): Verdict? {
        val text = raw.replace("**", "")
        // ขอบเขตของแต่ละ field = จนถึงชื่อ field ถัดไป (ระบุชื่อตรงๆ ไม่ใช้ pattern ทั่วไป
        // เพราะข้อความไทยอาจมีคำอังกฤษตามด้วย ":" เช่น "swing: 4050" แล้วถูกตัดกลางประโยค)
        val next = "(?=\\s*(?:DECISION|BIAS|LEVEL_SL|LEVEL_TP|CONFIDENCE|REASON_TH|SUMMARY_TH)\\s*:|$)"
        fun field(name: String): String? =
            Regex("$name\\s*:\\s*(.+?)$next", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.', ' ')?.takeIf { it.isNotBlank() }

        val decisionRaw = field("DECISION")?.uppercase() ?: return null
        val decision = when {
            decisionRaw.startsWith("NOTIFY") || decisionRaw.startsWith("APPROVE") -> "NOTIFY"
            decisionRaw.startsWith("SKIP") || decisionRaw.startsWith("VETO") -> "SKIP"
            else -> return null
        }
        val bias = field("BIAS")?.uppercase()?.let {
            when { it.startsWith("BUY") -> "BUY"; it.startsWith("SELL") -> "SELL"; else -> "NEUTRAL" }
        } ?: "NEUTRAL"
        fun price(name: String): Double? = field(name)?.replace(",", "")
            ?.let { Regex("-?[0-9]+(\\.[0-9]+)?").find(it)?.value }?.toDoubleOrNull()?.takeIf { it > 0 }
        val conf = field("CONFIDENCE")?.let { Regex("[0-9]{1,3}").find(it)?.value }?.toIntOrNull()?.coerceIn(0, 100)
        val reason = field("REASON_TH") ?: ""
        val summary = field("SUMMARY_TH") ?: reason
        return Verdict(decision, bias, price("LEVEL_SL"), price("LEVEL_TP"), conf, reason, summary)
    }
}

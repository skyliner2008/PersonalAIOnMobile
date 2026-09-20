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

        /**
         * Risk:Reward จากราคาปัจจุบัน = ระยะถึงเป้า ÷ ระยะถึงจุดผิดทาง
         * null ถ้าไม่มีทิศ หรือไม่มีระดับครบทั้งสอง
         */
        fun rr(price: Double?): Double? {
            if (price == null || price <= 0 || bias !in setOf("BUY", "SELL")) return null
            val sl = levelSl ?: return null
            val tp = levelTp ?: return null
            val risk = kotlin.math.abs(price - sl)
            return if (risk > 0) kotlin.math.abs(tp - price) / risk else null
        }
    }

    /** RR ขั้นต่ำที่ยอมให้แจ้งผู้ใช้ — ต่ำกว่านี้ AI ต้องตอบ SKIP และการ์ดจะเตือน */
    const val MIN_RR = 1.0

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

    /**
     * ตาข่ายรองรับกติกา "ห้ามแจ้งสวนเทรนด์ที่ H4/H1/M15 เรียงกัน" — ถ้า AI ยังตอบ NOTIFY สวนทาง เปลี่ยนเป็น SKIP
     * (คงทิศที่ AI เสนอไว้ ให้ [AiViewTracker] วัดได้ว่าถ้าแจ้งไปจะถูกหรือผิด)
     * @param align ค่า [AnticipationEngine.K_HTF_ALIGN]: "UP" / "DOWN" / อื่นๆ = ไม่เรียง
     */
    fun enforceTrend(v: Verdict, align: String?): Verdict {
        val blocked = v.notify && ((align == "UP" && v.bias == "SELL") || (align == "DOWN" && v.bias == "BUY"))
        if (!blocked) return v
        val trend = if (align == "UP") "ขาขึ้น" else "ขาลง"
        return v.copy(
            decision = "SKIP",
            reasonTh = "ระบบไม่แจ้ง: AI เสนอ ${v.bias} สวนเทรนด์ H4/H1/M15 ที่เรียงเป็น$trend — ${v.reasonTh}".take(1000)
        )
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
        data[AnticipationEngine.K_PREV_VIEWS]?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("══ มุมมองที่คุณเคยให้บน $symbol (24 ชม.ล่าสุด) และผลจริง ══")
            appendLine(it)
        }
        appendLine()
        appendLine("══ โครงสร้างตลาด D1 + 5 ไทม์เฟรม (บรรทัดย่อยใต้แต่ละ TF = โครงสร้างของขาและแรง/แท่งเทียนล่าสุด) ══")
        appendLine(data[AnticipationEngine.K_MTF]?.ifBlank { "-" } ?: "-")
        when (data[AnticipationEngine.K_HTF_ALIGN]) {
            "UP" -> { appendLine(); appendLine("⚠️ เทรนด์ H4/H1/M15 เรียงเป็นขาขึ้นทั้งหมด — ห้ามตอบ NOTIFY + SELL") }
            "DOWN" -> { appendLine(); appendLine("⚠️ เทรนด์ H4/H1/M15 เรียงเป็นขาลงทั้งหมด — ห้ามตอบ NOTIFY + BUY") }
        }
        appendLine()
        appendLine("แนวทางการวิเคราะห์:")
        appendLine("- ห้ามแจ้งสวนเทรนด์ที่ H4/H1/M15 เรียงทิศเดียวกัน (ราคาอยู่ Premium/Discount สุดขอบ, overbought/oversold, " +
            "ไส้เทียนหรือแท่งกลับตัว ไม่พอ) — ต้องรอให้ M15 เกิด CHoCH สวนทางก่อน ซึ่งจะทำให้ trend M15 กลับทิศและไม่นับว่าเรียงกันแล้ว " +
            "ระหว่างนั้นให้มองหาจังหวะตามเทรนด์ หรือตอบ SKIP")
        appendLine("- ทิศทางที่แต่ละเหตุการณ์ชี้เป็นแค่ข้อสังเกตจากอินดิเคเตอร์ ไม่ใช่ข้อสรุป — ชั่งน้ำหนักเองจากโครงสร้าง H4/H1 และการยืนยันของ M5")
        appendLine("- เหตุการณ์มาจากหลาย TF (ป้าย [M1]…[H4]): M1/M5 = จังหวะ/การยืนยันระยะสั้น (แตะ/กวาดระดับ, โครงสร้างย่อย) " +
            "น้ำหนักน้อยถ้าสวน H1/H4, M15 = setup, H1/H4 = บริบทใหญ่ — ปัจจัยเดียวกันบน TF ใหญ่มีความหมายมากกว่า " +
            "และ \"สถิติ\" ของแต่ละบรรทัดเป็นของปัจจัยนั้นบน TF นั้น; \"(เกิดเมื่อ N นาทีก่อน)\" = เหตุการณ์ที่รอการปลุกรอบก่อน")
        appendLine("- \"สถิติ\" คือผลจริงที่ระบบเคยวัดได้เมื่อปัจจัยนั้นยิงในอดีต — ให้น้ำหนักกับตัวที่สถิติดีและเชื่อถือได้ (n มาก)")
        appendLine("- เหตุการณ์ที่ปลุกอาจไม่ใช่สิ่งสำคัญที่สุด ถ้าเห็นอย่างอื่นที่สำคัญกว่าจากข้อมูล ให้ยึดสิ่งนั้น")
        appendLine("- ถ้าหลักฐานขัดแย้งกันหรือไม่ชัดเจน ให้ตอบ SKIP (ไม่ต้องรบกวนผู้ใช้)")
        appendLine("- ถ้ามีมุมมองที่ยังเปิดอยู่ (ยังไม่ชน SL/TP) ทิศเดียวกัน และเหตุการณ์ใหม่แค่ยืนยันของเดิม ให้ตอบ SKIP (ไม่แจ้งซ้ำ)")
        appendLine("- ถ้าจะแจ้งทิศตรงข้ามกับมุมมองที่ยังเปิดอยู่ ต้องบอกใน REASON_TH และ SUMMARY_TH ว่าอะไรเปลี่ยนไป และมุมมองเดิมควรถือว่าจบแล้ว")
        appendLine("- ใช้สถิติผลมุมมองของคุณเองประกอบการตั้งความมั่นใจ — ถ้าช่วงนี้ชน SL บ่อย ให้ระวังมากขึ้น")
        appendLine("- ถ้าน่าสนใจ กำหนดระดับราคาเองจากแนวรับ/แนวต้าน/swing/OB ที่ให้มาเท่านั้น")
        appendLine("- ถ้า BIAS เป็น BUY/SELL ต้องมี LEVEL_SL และ LEVEL_TP ที่ให้ Risk:Reward อย่างน้อย 1:1 จากราคาปัจจุบัน " +
            "(ระยะถึง TP ≥ ระยะถึง SL) — ถ้าหาระดับที่ให้ RR ถึง 1:1 ไม่ได้จากข้อมูลที่มี ให้ตอบ SKIP")
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

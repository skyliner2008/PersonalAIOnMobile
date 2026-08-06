package com.example.personalaibot.ai

/**
 * Task types สำหรับ intent routing
 * แต่ละ type จะได้ system prompt addon และ parameter set ที่ต่างกัน
 */
enum class TaskType {
    GENERAL,    // สนทนาทั่วไป
    CODE,       // เขียน/แก้/อธิบายโค้ด
    RESEARCH,   // ค้นหา/อธิบายข้อมูล
    ANALYSIS,   // วิเคราะห์เชิงลึก
    CREATIVE,   // งานสร้างสรรค์ (เขียน/ออกแบบ)
    PLANNING,   // วางแผน/จัดการ/todo
    MEMORY,     // recall บทสนทนาหรือข้อมูลที่เคยคุยไว้
    TOOL_CREATION // สร้าง Tool/Skill ให้ Agent
}

/**
 * ผลการวิเคราะห์ intent ของ task
 */
data class IntentResult(
    val taskType: TaskType,
    val confidence: Float,        // 0.0 – 1.0
    val isComplex: Boolean,       // ต้องการ chain-of-thought หรือไม่
    val detectedKeywords: List<String>
)

/**
 * IntentClassifier — วิเคราะห์ประเภทของงานจากข้อความ user
 *
 * ใช้ keyword matching แบบ weighted scoring
 * สามารถ upgrade เป็น LLM-based classification ในอนาคต
 */
object IntentClassifier {

    // ─── Keyword patterns per TaskType ──────────────────────────────────
    private val patterns: Map<TaskType, List<String>> = mapOf(
        TaskType.CODE to listOf(
            "code", "โค้ด", "function", "class", "bug", "fix", "error", "debug",
            "implement", "programming", "script", "algorithm", "compile", "syntax",
            "ฟังก์ชัน", "เขียนโค้ด", "แก้บัก", "ไฟล์", "method", "variable",
            "kotlin", "java", "python", "javascript", "typescript", "api", "sdk",
            "gradle", "dependency", "import", "package", "library"
        ),
        TaskType.RESEARCH to listOf(
            "search", "ค้นหา", "find", "research", "วิจัย", "ข้อมูล",
            "explain", "อธิบาย", "what is", "คืออะไร", "how does", "ทำงานอย่างไร",
            "tell me", "บอกฉัน", "define", "นิยาม", "meaning", "ความหมาย",
            "history", "ประวัติ", "background", "ที่มา", "เกี่ยวกับ",
            "ล่าสุด", "ราคา", "วันนี้", "หุ้น", "ตลาด", "กราฟ", "overview", "snapshot", "สภาวะ"
        ),
        TaskType.ANALYSIS to listOf(
            "analyze", "วิเคราะห์", "compare", "เปรียบเทียบ", "evaluate",
            "review", "calculate", "คำนวณ", "assess", "summarize", "สรุป",
            "pros", "cons", "ข้อดี", "ข้อเสีย", "difference", "ต่างกัน",
            "performance", "ประสิทธิภาพ", "benchmark", "metric", "statistics",
            "mt5", "xauusd", "xau", "forex", "gold", "ทอง", "confluence",
            "regime", "bias", "indicator", "timeframe", "tf", "h1", "h4", "m15",
            "broker", "โบรก", "เทรด", "trade", "trading", "สถานะตลาด"
        ),
        TaskType.CREATIVE to listOf(
            "write", "เขียน", "create", "สร้าง", "design", "ออกแบบ",
            "story", "นิทาน", "นิยาย", "poem", "กลอน", "essay", "เรียงความ",
            "brainstorm", "ไอเดีย", "idea", "concept", "creative", "สร้างสรรค์",
            "draft", "ร่าง", "content", "คอนเทนต์", "caption", "post"
        ),
        TaskType.PLANNING to listOf(
            "plan", "วางแผน", "schedule", "ตาราง", "organize", "จัดการ",
            "todo", "task", "งาน", "remind", "เตือน", "deadline",
            "goal", "เป้าหมาย", "step", "ขั้นตอน", "priority", "ความสำคัญ",
            "workflow", "process", "routine", "habit", "กิจวัตร"
        ),
        TaskType.MEMORY to listOf(
            "remember", "จำได้ไหม", "recall", "คุยกันไว้", "เมื่อกี้",
            "ก่อนหน้า", "ที่แล้ว", "เดิมทีที่", "ที่คุยกัน", "ลืมหรือยัง",
            "ที่บอกไป", "ที่เคย", "previously", "before", "last time",
            "จากที่คุย", "ตามที่บอก"
        ),
        TaskType.TOOL_CREATION to listOf(
            "สร้าง tool", "create tool", "สร้าง indicator", "สร้าง skill",
            "ทำ tool", "เขียน tool", "agent tool", "สอนตัวเอง", "make tool",
            "ระบบเทรดใหม่", "เครื่องมือใหม่", "สร้างเครื่องมือ"
        )
    )

    // คำที่บ่งบอก task ซับซ้อน → ต้องการ chain-of-thought
    private val complexityMarkers = listOf(
        "complex", "ซับซ้อน", "detailed", "ละเอียด", "comprehensive", "ครอบคลุม",
        "step by step", "ทีละขั้น", "full", "complete", "ทั้งหมด",
        "compare", "เปรียบเทียบ", "multiple", "หลาย", "architecture", "system"
    )

    // ─── Keyword matching helpers ─────────────────────────────────────────
    // Cache compiled regex ของ ASCII keywords (word-boundary matching)
    private val boundaryRegexCache = mutableMapOf<String, Regex>()

    /**
     * ASCII keyword → word-boundary regex match
     * กัน false positive เช่น "import" ⊂ "important", "trade" ⊂ "trademark"
     * ภาษาไทย (ไม่มี space คั่นคำ) → contains ตามเดิม
     */
    private fun matchesKeyword(lowerText: String, keyword: String): Boolean {
        val kw = keyword.lowercase()
        return if (kw.all { it.code < 128 }) {
            boundaryRegexCache.getOrPut(kw) {
                Regex("""\b${Regex.escape(kw)}\b""")
            }.containsMatchIn(lowerText)
        } else {
            lowerText.contains(kw)
        }
    }

    /** keyword ที่ยาว/เฉพาะเจาะจงกว่า ให้น้ำหนักมากกว่า (ลดความกำกวมของคำสั้นทั่วไป) */
    private fun keywordWeight(keyword: String): Int = when {
        keyword.length >= 8 -> 3
        keyword.length >= 5 -> 2
        else -> 1
    }

    /**
     * วิเคราะห์ intent จากข้อความ user
     */
    fun classify(text: String): IntentResult {
        val lowerText = text.lowercase()

        // คำนวณ weighted score ของแต่ละ type
        val scores = TaskType.entries.associateWith { taskType ->
            patterns[taskType]
                ?.filter { matchesKeyword(lowerText, it) }
                ?.sumOf { keywordWeight(it) } ?: 0
        }

        // เก็บ keywords ที่เจอ
        val detected = patterns.values.flatten()
            .filter { matchesKeyword(lowerText, it) }
            .take(5)

        // หา type ที่ score สูงสุด
        // (tie → enum ตัวแรกตามลำดับประกาศ — deterministic เสมอ)
        val best = scores.maxByOrNull { it.value }
        val taskType = if (best != null && best.value > 0) best.key else TaskType.GENERAL
        val maxScore = best?.value ?: 0

        // คำนวณ confidence (0.0-1.0) ตาม weighted score
        val confidence = when {
            maxScore == 0 -> 0.5f
            maxScore <= 2 -> 0.65f
            maxScore <= 5 -> 0.8f
            else          -> 0.95f
        }

        // ตรวจว่า task ซับซ้อนหรือไม่
        val isComplex = complexityMarkers.any { lowerText.contains(it) } ||
            text.length > 200 ||
            taskType in listOf(TaskType.ANALYSIS, TaskType.CODE)

        return IntentResult(
            taskType = taskType,
            confidence = confidence,
            isComplex = isComplex,
            detectedKeywords = detected
        )
    }

    /**
     * System prompt addon ตาม task type
     * ใช้ต่อท้าย JARVIS_SYSTEM_PROMPT หลักใน GeminiService
     */
    fun getSystemPromptAddon(result: IntentResult): String {
        val base = when (result.taskType) {
            TaskType.CODE -> """

Task Type: CODE — งานเขียน/แก้/อธิบายโค้ด
- ตอบพร้อม code block เสมอ ใส่ ```language``` ให้ถูกต้อง
- อธิบายสั้นๆ ก่อน แล้วตามด้วยโค้ด
- ตรวจสอบ syntax ก่อนตอบ อย่า hallucinate API"""

            TaskType.RESEARCH -> """

Task Type: RESEARCH — ค้นหาและอธิบายข้อมูล
- แสดงข้อมูลรายการหรือรายละเอียดที่สำคัญอย่างครบถ้วน
- ยึดข้อมูลที่ถูกต้อง หากมีตัวเลขหรือราคาให้แสดงให้ชัดเจน
- จัดโครงสร้างให้กะทัดรัดและอ่านง่าย"""

            TaskType.ANALYSIS -> """

Task Type: ANALYSIS — วิเคราะห์เชิงลึก
- ใช้ step-by-step reasoning ก่อนสรุป
- แสดงการคิด ไม่ใช่แค่ผลลัพธ์
- เปรียบเทียบ tradeoffs อย่างสมดุล

[MT5/Trading Analysis Protocol]:
เมื่อวิเคราะห์ symbol ใดๆ จาก MT5 broker ให้ทำดังนี้:
1. เรียก trading_mt5_analyze พร้อมกัน 3 TF: H4, H1, M15 ในรอบเดียวกัน (3 function calls)
2. สรุปผลเป็นตารางเปรียบเทียบ: Regime | Bias | RSI | Confluence | Fitness ของทุก TF
3. หา Confluence ข้าม TF — ถ้า Bias ตรงกันทุก TF = สัญญาณแข็ง
4. ระบุแนวรับ-แนวต้าน จากข้อมูล 20-bar High/Low ของแต่ละ TF
5. แนะนำ Entry zone, SL, TP ที่ชัดเจน พร้อม Risk:Reward ratio
6. สรุป Risk Assessment: ปลอดภัย/ระวัง/อันตราย"""

            TaskType.CREATIVE -> """

Task Type: CREATIVE — งานสร้างสรรค์
- แสดงความคิดสร้างสรรค์ได้อิสระ
- ไม่ต้องเป็นทางการ ลื่นไหล
- เสนอ options หลายแบบถ้าเป็นไปได้"""

            TaskType.PLANNING -> """

Task Type: PLANNING — วางแผนและจัดการ
- ตอบแบบเป็นขั้นเป็นตอนหรือเป็นลิสรายการที่ทำได้จริง
- เน้นสิ่งที่ต้องทำ (to-do) หรือเป้าหมายที่สำคัญ"""

            TaskType.MEMORY -> """

Task Type: MEMORY — ดึงข้อมูลจาก history
- อ้างอิง conversation history ที่ได้รับมาอย่างละเอียด
- ถ้าไม่มีในข้อมูล บอกตรงๆ ว่าไม่พบ"""

            TaskType.TOOL_CREATION -> """

Task Type: TOOL_CREATION — งานสร้าง Tool/Skill สำหรับ Agent
- ผู้ใช้ต้องการให้คุณสร้างเครื่องมือ (Tool/Indicator/Skill) ใหม่เพื่อให้คุณใช้งานได้เองในอนาคต
- **คุณต้องเรียกใช้เครื่องมือ `system_create_agent_tool` เสมอ** เพื่อบันทึกเครื่องมือใหม่ลงในระบบ
- ให้คุณคิดค้น `systemPromptAddon` ที่ละเอียด เป็นขั้นตอน เพื่อสอนตัวคุณเองในอนาคตว่าเมื่อถูกเรียกใช้ Tool นี้จะต้องทำอย่างไร (เช่น ดึงข้อมูลอะไร วิเคราะห์อย่างไร)
- ห้ามตอบกลับด้วยโครงสร้าง JSON เปล่าๆ ในแชทเด็ดขาด ให้ใช้ Tool `system_create_agent_tool` ในการทำงานแทน"""

            TaskType.GENERAL -> ""
        }

        // ถ้า task ซับซ้อน เพิ่ม chain-of-thought instruction
        val chainOfThought = if (result.isComplex) """

Complexity: HIGH — ให้คิดทีละขั้นตอนก่อนตอบ (internal reasoning ก่อน summary)""" else ""

        return base + chainOfThought
    }
}

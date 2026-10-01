package com.skyliner2008.jarvis.data

import com.skyliner2008.jarvis.tools.ToolArgParser
import kotlinx.serialization.json.JsonObject

/**
 * Pure helpers ของ Live API wire protocol — แยกออกจาก LiveGeminiService เพื่อให้ unit test ได้
 * (review 2026-09-16)
 */
object LiveProtocol {

    const val ENDPOINT =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    /**
     * สร้าง URL ต่อ attempt — ต้องเรียกใหม่ทุกครั้งที่หมุน key
     * (เดิมสร้างครั้งเดียวก่อน retry loop ทำให้ quota rotation ใช้ key เดิมตลอด)
     */
    fun buildUrl(apiKey: String): String = "$ENDPOINT?key=$apiKey"

    private val keyQueryPattern = Regex("""(?i)([?&]key=)[^&\s"']+""")
    private val googleApiKeyPattern = Regex("""AIza[0-9A-Za-z_\-]{20,}""")

    /** ลบ API key ออกจากข้อความก่อน log/แสดง UI — exception ของ Ktor handshake มักมี URL เต็ม */
    fun redactSecrets(text: String?): String {
        if (text.isNullOrEmpty()) return text ?: ""
        return text
            .replace(keyQueryPattern) { "${it.groupValues[1]}***" }
            .replace(googleApiKeyPattern, "AIza***")
    }

    /**
     * แปลง args ของ function call เป็น Map<String,String> โดยไม่ throw
     * (เดิม `jsonPrimitive.content` throw เมื่อเจอ array/object → ทั้งเฟรม toolCall หาย และ model รอ response ค้าง)
     */
    fun parseToolArgs(args: JsonObject?): Map<String, String> =
        args?.let { ToolArgParser.fromJsonObject(it) } ?: emptyMap()

    /**
     * Live API ส่ง transcription มาเป็น "ชิ้น" หรือบางครั้งเป็นแบบสะสม —
     * ถ้าข้อความใหม่ขึ้นต้นด้วยของเดิม ถือว่าเป็นแบบสะสม
     */
    fun mergeTranscript(previous: String?, chunk: String): String = when {
        previous.isNullOrEmpty() -> chunk
        chunk.startsWith(previous) -> chunk
        else -> previous + chunk
    }

    /** คำตอบทันทีของ tool ที่ส่งต่อให้ลูกน้อง — ไม่ใช่ผลจริง (LiveGeminiService ไม่นับเป็นคำตอบของเทิร์น) */
    const val DELEGATED_PREFIX = "DELEGATED:"

    /**
     * กำหนดประโยครับงานให้ชัด — เดิมบอกแค่ "ตอบรับสั้นๆ 1 ประโยค" โมเดลบางครั้งหยิบประโยคทักทายเปิดเซสชัน
     * (ประโยคสั้นที่ถูกกำหนดให้พูดคำต่อคำใน persona) มาใช้แทน (logcat 2026-09-24 03:45:09, 03:46:25)
     */
    const val DELEGATED_ACK_SENTENCE = "รับทราบ กำลังให้ผู้ช่วยดึงข้อมูลให้"

    fun delegatedAck(toolName: String): String =
        "$DELEGATED_PREFIX ส่งงาน $toolName ให้ผู้ช่วยทำเบื้องหลังแล้ว — " +
            "พูดประโยคนี้ประโยคเดียวแล้วหยุด (เติมหางเสียงตามปกติ): \"$DELEGATED_ACK_SENTENCE\" " +
            "ห้ามทักทาย ห้ามพูดประโยคอื่น ห้ามเดาผลหรือพูดตัวเลขใดๆ เมื่อผู้ช่วยทำเสร็จ ระบบจะส่งผลมาให้รายงานเอง"

    /** ต่อท้ายป้ายของผล tool — จังหวะหลังผล tool คือจุดที่โมเดลพูดคำทักทายเปิดเซสชันซ้ำบ่อยที่สุด */
    const val NO_GREETING_NOTE = " (เริ่มพูดที่เนื้อหาผลลัพธ์ทันที ห้ามขึ้นต้นด้วยคำทักทาย)"

    /**
     * ติดป้ายว่าผลก้อนนี้เป็นคำตอบของคำถามไหน — ถ้าไม่ติด โมเดลมักปิดเทิร์นไปแล้วตอนผลกลับมา แล้วปล่อยผ่านเงียบๆ
     * (log 2026-09-17 00:30: ผล 5 มิติถูกทิ้งจนผู้ใช้ถามซ้ำ) · [askedFor] = ประโยคของผู้ใช้ตอนโมเดลเรียก tool
     *
     * ผลของ tool เปลี่ยนหน้าตา/โหมด ([UI_ONLY_TOOLS]) ไม่ใช่คำตอบของคำถาม — ไม่ติดป้าย: ป้าย "ต้องพูดตอบคำถามนี้ให้ครบทันที"
     * บนผลเปลี่ยนหน้าที่โมเดลเรียกหลังตอบเสร็จ ทำให้มันตอบคำถามเดิมซ้ำ (logcat 2026-10-01 18:19: รายงานอากาศแล้ว
     * เปลี่ยนหน้าเป็นฉากฝน → ถาม "อยู่ที่ไหน" → เรียกอากาศอีกรอบ → รายงานซ้ำ)
     */
    fun tagToolResult(toolName: String, askedFor: String, currentUserText: String, result: String): String = when {
        askedFor.isBlank() || toolName in UI_ONLY_TOOLS -> result
        // รับงานไปให้ผู้ช่วย / call ที่ไม่ได้รัน — ไม่ใช่คำตอบ: ป้าย "ต้องพูดตอบให้ครบทันที" ขัดกับ "พูดประโยครับงานประโยคเดียว"
        result.startsWith(DELEGATED_PREFIX) || result == GREETING_TURN_TOOL_NOTE -> result
        askedFor != currentUserText.trim() ->
            "[PENDING QUESTION] ผลนี้เป็นคำตอบของคำถามก่อนหน้าที่ผู้ใช้ถามว่า: " + askedFor +
                " — คำถามนี้ยังไม่ได้ตอบ ต้องพูดตอบให้ครบด้วย ห้ามข้ามไปตอบเฉพาะคำถามล่าสุด" + NO_GREETING_NOTE + "\n\n" + result
        else ->
            "[ANSWER FOR] ผลนี้เป็นคำตอบของคำถามที่ผู้ใช้ถามว่า: " + askedFor +
                " — ต้องพูดตอบคำถามนี้ให้ครบทันที ห้ามเงียบหรือรอให้ผู้ใช้ถามซ้ำ" + NO_GREETING_NOTE + "\n\n" + result
    }

    // ท้ายประโยคทักทายเปิดเซสชัน "…มีอะไรให้จาวิสช่วยวันนี้ดีคะ" — transcript ของ Live แทรกช่องว่างระหว่างคำได้
    /** คำที่บอกว่าเป็นคำทักทายเปิดเซสชัน (ต่อจาก "สวัสดี…") */
    private val GREETING_WORDS = listOf("พร้อมคุย", "พร้อมเล่น", "พร้อมช่วย", "ยินดีช่วย")

    private val greetingEndPattern = Regex("""ว\s*ั\s*น\s*น\s*ี\s*้\s*ด\s*ี\s*(?:ค\s*ะ|ค\s*่\s*ะ|ค\s*ร\s*ั\s*บ)\s*[?？]?""")

    /**
     * ตัดประโยคทักทายเปิดเซสชันออกจากต้นข้อความของ AI
     * คืน "" ถ้าทั้งข้อความเป็นแค่คำทักทาย และคืนข้อความเดิมถ้าไม่ได้ขึ้นต้นด้วยคำทักทาย
     *
     * คำทักทายเก่าที่ค้างใน history ทำให้โมเดลเห็นแบบแผน "ทักทาย → ตอบ" แล้วพูดทักทายซ้ำหลังผล tool
     * (logcat 2026-09-23 02:37: ถาม SMC ทอง → "สวัสดีค่ะบอส จาวิสพร้อมคุยแล้วค่ะ… ผลวิเคราะห์ทองคำ…")
     */
    fun stripSessionGreeting(text: String): String {
        val trimmed = text.trim()
        val compact = trimmed.filterNot { it.isWhitespace() }
        if (!compact.startsWith("สวัสดี")) return trimmed
        // คำทักทายด้วยคำพูดของโมเดลเอง (persona ไม่มีประโยคคำต่อคำแล้ว) อาจเป็น "พร้อมช่วย" / "ยินดีช่วย"
        if (GREETING_WORDS.none { compact.contains(it) }) return trimmed
        // ประโยคทักทายยาวไม่เกิน ~100 ตัวอักษร — หาท้ายประโยคเฉพาะช่วงต้น กันตัดเนื้อหาจริงที่บังเอิญมีคำเหล่านี้
        val end = greetingEndPattern.find(trimmed)?.takeIf { it.range.first < 120 }
        return when {
            end != null -> trimmed.substring(end.range.last + 1).trim()
            trimmed.length <= 80 -> ""
            else -> trimmed
        }
    }

    /** ขึ้นต้นด้วยประโยคทักทายเปิดเซสชันหรือไม่ (ใช้ตรวจว่าโมเดลทักทายซ้ำกลางบทสนทนา) */
    fun startsWithSessionGreeting(text: String): Boolean = stripSessionGreeting(text) != text.trim()

    /**
     * ทั้งคำตอบเป็นแค่ประโยคทักทายเปิดเซสชัน — ไม่ใช่คำตอบ ผล tool ของเทิร์นนั้นผู้ใช้ยังไม่ได้ยิน
     * (logcat 2026-10-01 16:24 โหมดอวตาร: "ราคา Bitcoin" → "สวัสดีฮับบอส! น้องหุ่นยนต์สัตว์เลี้ยงพร้อมเล่นด้วยแล้วฮับ")
     */
    fun isSessionGreetingOnly(text: String): Boolean = text.isNotBlank() && stripSessionGreeting(text).isBlank()

    /**
     * คำตอบของ tool call ในเทิร์นที่ตอบคำทักทายเปิดเซสชันของระบบ ก่อนผู้ใช้พูดอะไร — ไม่มีใครขอ ไม่รัน
     * (logcat 2026-10-01 16:51: หลัง "สวัสดีจาวิส พร้อมคุยไหม" โมเดลเรียกอากาศ + BTC + เปลี่ยนหน้า + เปิดแอป เอง)
     */
    const val GREETING_TURN_TOOL_NOTE =
        "SKIPPED: นี่เป็นเทิร์นทักทายเปิดเซสชัน ผู้ใช้ยังไม่ได้ขออะไรในเซสชันนี้ — ไม่ได้รัน tool นี้ " +
            "ห้ามทำคำขอจากบทสนทนาเก่า ให้ทักทายสั้นๆ อย่างเดียวแล้วรอผู้ใช้พูด"

    /** tool ที่โมเดลเรียกเพื่อแสดงท่าทางเอง (ไม่ใช่ข้อมูล ไม่ใช่การสั่งงาน) */
    val EXPRESSION_TOOLS = setOf("device_avatar_emotion", "device_custom_prop")

    /**
     * โมเดลพูดตอบในเทิร์นนี้ไปแล้ว แล้วเรียกแค่ tool แสดงท่าทาง — สิ่งที่พูดต่อจากนั้นในเทิร์นนี้คือคำพูดเกิน ไม่ต้องเล่น:
     * ผลของ tool ทุกแบบทำให้โมเดลพูดต่อ (Gemini 3.1 Flash Live ยังไม่มี async function calling — model จะไม่เริ่มตอบ
     * จนได้ tool response) แล้วมันพูดคำทักทาย ("สวัสดีฮับบอส! วันนี้มีอะไรให้น้องช่วย…", logcat 2026-10-01 21:33, 21:38)
     */
    fun isTrailingExpression(callNames: List<String>, modelSpokeThisTurn: Boolean): Boolean =
        modelSpokeThisTurn && callNames.isNotEmpty() && callNames.all { it in EXPRESSION_TOOLS }

    /** tool ที่ผลคือการเปลี่ยน UI/โหมด/หน้าตา — ไม่ใช่ข้อมูลที่ต้องรายงานด้วยเสียง */
    val UI_ONLY_TOOLS = setOf(
        "device_avatar_emotion", "device_custom_prop", "device_pet_care",
        "device_always_live", "vision_activate", "vision_deactivate"
    )

    /**
     * หัวข้อของประวัติใน system instruction — เดิมเป็นแค่ "Recent Conversation History:"
     * โมเดลจึงหยิบคำถามเก่ามาตอบซ้ำแทนคำถามใหม่ (logcat 2026-09-24 00:28: ถามปฏิทินเศรษฐกิจ
     * → เรียก market_snapshot TH ซ้ำแล้วพูดคำตอบเรื่อง SET ของ session ก่อนเกือบคำต่อคำ)
     */
    const val HISTORY_HEADER =
        "Recent Conversation History (บทสนทนาก่อนหน้า — ตอบไปแล้วทั้งหมด ใช้เป็นบริบทเท่านั้น " +
            "ห้ามตอบหรือพูดคำตอบเหล่านี้ซ้ำ ห้ามเรียก tool ให้คำถามเก่าอีก ตอบเฉพาะสิ่งที่ผู้ใช้พูดในเซสชันนี้ " +
            "ราคาและตัวเลขในประวัติเป็นข้อมูลเก่า ห้ามนำมาตอบ — ข้อมูลตลาดต้องเรียก tool ใหม่ทุกครั้ง):\n"

    /**
     * คำตอบของ AI ในประวัติเก็บแค่ต้นประโยค — เดิม 500 ตัวอักษรพอให้โมเดลพูดตัวเลขวิเคราะห์เก่าซ้ำเกือบคำต่อคำ
     * แทนการเรียก tool (logcat 2026-09-24 02:07: "คะแนนความเชื่อมั่นเจ็ดสิบ…Fibo 0.5–0.618" ตรงกับคำตอบ session ก่อน)
     */
    const val MAX_MODEL_TURN_CHARS = 120

    /**
     * ประกอบ "Recent Conversation History" ที่ส่งตอนเปิด session
     * - ตัดคำทักทายเปิดเซสชันทั้งฝั่งระบบ ("สวัสดีจาวิส พร้อมคุยไหม") และที่ AI ตอบ
     * - จำกัดความยาวต่อข้อความ — คำตอบยาวๆ ไม่ควรกิน system instruction
     * - [dropUnanswered]: ตัดคำถามที่ไม่มีคำตอบตามหลัง (คำตอบเป็นแค่คำทักทาย หรือ session จบก่อนตอบ) — session ใหม่
     *   เห็นคำถามค้างแล้วตอบเองทันทีหลังทักทาย เรียก tool ด้วย ทั้งที่หัวข้อบอกว่าตอบไปแล้ว (logcat 2026-10-01 16:51:
     *   กด live ปุ๊บดึง BTC กับอากาศของ session 16:24 มาพูด 2 รอบ) · reconnect กลางบทสนทนาไม่ตัด (คำถามล่าสุดอาจกำลังรอคำตอบ)
     */
    fun buildSessionHistory(
        turns: List<Pair<String, String>>,
        maxTurns: Int = 8,
        maxCharsPerTurn: Int = 500,
        dropUnanswered: Boolean = true
    ): String = turns
        .mapNotNull { (role, text) ->
            val compact = text.filterNot { it.isWhitespace() }
            if (role == "user" && (compact.contains("พร้อมคุยไหม") || compact.contains("พร้อมเล่นแล้ว"))) return@mapNotNull null
            val clean = if (role == "user") text.trim() else stripSessionGreeting(text)
            if (clean.isBlank()) null else role to clean
        }
        .let { kept ->
            if (!dropUnanswered) kept
            else kept.filterIndexed { i, (role, _) -> role != "user" || kept.getOrNull(i + 1)?.first == "model" }
        }
        .takeLast(maxTurns)
        .joinToString("\n") { (role, text) ->
            val cap = if (role == "user") maxCharsPerTurn else minOf(maxCharsPerTurn, MAX_MODEL_TURN_CHARS)
            "${if (role == "user") "ผู้ใช้" else "จาวิส"}: ${if (text.length > cap) text.take(cap) + "…" else text}"
        }

    /**
     * ข้อความในฐานข้อมูลเป็นคำพูดสดของ Live หรือไม่ (`{"mode": "live_voice"}` รวมแบบ interrupted)
     * — ผล tool/รายงาน (`live_voice_tool_result`) ไม่นับ เพราะเป็นเนื้อหาที่ต้องแสดงในแชท
     */
    fun isLiveTranscript(metadata: String?): Boolean =
        metadata?.filterNot { it.isWhitespace() }?.contains("\"mode\":\"live_voice\"") == true

    /** ประกอบ history สำหรับ reconnect ที่ไม่มี resumption handle — จำกัดความยาวกัน setup ใหญ่เกิน */
    fun buildReconnectHistory(
        initialHistory: String,
        recentTurns: List<Pair<String, String>>,
        maxChars: Int = 4000
    ): String {
        val recent = buildSessionHistory(recentTurns, maxTurns = recentTurns.size, maxCharsPerTurn = Int.MAX_VALUE, dropUnanswered = false)
        val combined = listOf(initialHistory, recent).filter { it.isNotBlank() }.joinToString("\n")
        return if (combined.length > maxChars) combined.takeLast(maxChars) else combined
    }
}

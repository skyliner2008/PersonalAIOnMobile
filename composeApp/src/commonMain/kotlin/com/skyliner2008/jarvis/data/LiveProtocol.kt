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

    /** ต่อท้ายป้ายของผล tool — จังหวะหลังผล tool คือจุดที่โมเดลพูดคำทักทายเปิดเซสชันซ้ำบ่อยที่สุด */
    const val NO_GREETING_NOTE = " (เริ่มพูดที่เนื้อหาผลลัพธ์ทันที ห้ามขึ้นต้นด้วยคำทักทาย)"

    // ท้ายประโยคทักทายเปิดเซสชัน "…มีอะไรให้จาวิสช่วยวันนี้ดีคะ" — transcript ของ Live แทรกช่องว่างระหว่างคำได้
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
        if (!compact.contains("พร้อมคุย") && !compact.contains("พร้อมเล่น")) return trimmed
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
     */
    fun buildSessionHistory(
        turns: List<Pair<String, String>>,
        maxTurns: Int = 8,
        maxCharsPerTurn: Int = 500
    ): String = turns
        .mapNotNull { (role, text) ->
            val compact = text.filterNot { it.isWhitespace() }
            if (role == "user" && (compact.contains("พร้อมคุยไหม") || compact.contains("พร้อมเล่นแล้ว"))) return@mapNotNull null
            val clean = if (role == "user") text.trim() else stripSessionGreeting(text)
            if (clean.isBlank()) null else role to clean
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
        val recent = buildSessionHistory(recentTurns, maxTurns = recentTurns.size, maxCharsPerTurn = Int.MAX_VALUE)
        val combined = listOf(initialHistory, recent).filter { it.isNotBlank() }.joinToString("\n")
        return if (combined.length > maxChars) combined.takeLast(maxChars) else combined
    }
}

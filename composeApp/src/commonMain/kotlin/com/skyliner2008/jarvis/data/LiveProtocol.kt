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

    /** ประกอบ history สำหรับ reconnect ที่ไม่มี resumption handle — จำกัดความยาวกัน setup ใหญ่เกิน */
    fun buildReconnectHistory(
        initialHistory: String,
        recentTurns: List<Pair<String, String>>,
        maxChars: Int = 4000
    ): String {
        val recent = recentTurns.joinToString("\n") { (role, text) ->
            "${if (role == "user") "ผู้ใช้" else "จาวิส"}: $text"
        }
        val combined = listOf(initialHistory, recent).filter { it.isNotBlank() }.joinToString("\n")
        return if (combined.length > maxChars) combined.takeLast(maxChars) else combined
    }
}

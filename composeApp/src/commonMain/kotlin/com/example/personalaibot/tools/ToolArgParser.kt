package com.example.personalaibot.tools

import kotlinx.serialization.json.*

/**
 * ToolArgParser — parser กลางตัวเดียวสำหรับ tool-call arguments
 *
 * ใช้ร่วมกันทุก provider path (Gemini / External / Live Bridge)
 * เพื่อให้ robustness เท่ากันทุกช่องทาง
 *
 * พฤติกรรม:
 * - String ว่าง → emptyMap (ไม่มี args เป็นเรื่องปกติ)
 * - JSON เพี้ยน / ไม่ใช่ object → **null** (caller ต้องคืน error เข้า tool loop แทนการ execute ด้วย args ว่าง)
 * - JsonArray → join ด้วย ","
 * - JsonPrimitive → content
 * - nested object → toString() (ไม่ flatten ลึก)
 */
object ToolArgParser {

    /**
     * Parse arguments จาก JSON string ที่ model ส่งมา
     * @return Map ของ args หรือ null ถ้า JSON ไม่ถูกต้อง
     */
    fun parse(arguments: String?): Map<String, String>? {
        if (arguments.isNullOrBlank()) return emptyMap()
        val trimmed = arguments.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        return try {
            val element = Json.parseToJsonElement(trimmed)
            when (element) {
                is JsonObject -> fromJsonObject(element)
                else -> null // array/primitive ไม่ใช่ args ที่ถูกต้อง
            }
        } catch (_: Exception) {
            null
        }
    }

    /** แปลง JsonObject เป็น Map<String, String> (ทุกค่าถูก coerce เป็น string) */
    fun fromJsonObject(obj: JsonObject): Map<String, String> =
        obj.entries.associate { (k, v) -> k to coerceToString(v) }

    private fun coerceToString(v: JsonElement): String = when (v) {
        is JsonArray -> v.joinToString(",") { element ->
            (element as? JsonPrimitive)?.contentOrNull ?: element.toString()
        }
        is JsonPrimitive -> v.contentOrNull ?: v.toString()
        else -> v.toString()
    }
}

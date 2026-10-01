package com.skyliner2008.jarvis.avatar

import com.skyliner2008.jarvis.data.LiveToolCallContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The raw data behind a tool's answer, for the robot's info scenes (pet_godot/scenes/tool_scenes.json:
 * weather card, price with 52-week range, technical gauges, balance-sheet pie …). A tool publishes the
 * map it formatted its text from. It belongs to the live AI tool call it was made in
 * ([LiveToolCallContext.avatarData]): two prices asked at once keep one each (logcat 2026-10-01 21:38:
 * Tesla and Microsoft came back as one card), and LiveToolBridge hands it to Avatar mode with that call's
 * result ([AvatarLiveFeed]). Outside a live call (chat) nothing shows it. Text-only tools publish nothing
 * and go on a markdown card.
 */
object AvatarToolData {
    /** A map of fields (a tool's data map: text / number / boolean values). */
    suspend fun publish(tool: String, data: Map<String, Any?>) = put(tool, toJson(data))

    /** Rows (scanner results: top gainers …). */
    suspend fun publishRows(tool: String, rows: List<Map<String, Any?>>) = put(tool, JsonArray(rows.map { toJson(it) }))

    suspend fun publishJson(tool: String, data: JsonElement) = put(tool, data)

    /** What [tool] published during the live tool call [call] (null: nothing, or another tool's). */
    fun of(call: LiveToolCallContext?, tool: String): JsonElement? =
        call?.avatarData?.takeIf { it.first == tool }?.second

    private suspend fun put(tool: String, data: JsonElement) {
        currentCoroutineContext()[LiveToolCallContext]?.avatarData = tool to data
    }

    private fun toJson(map: Map<String, Any?>): JsonObject = JsonObject(map.mapValues { (_, v) -> value(v) })

    private fun value(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is JsonElement -> v
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to value(x) })
        is List<*> -> JsonArray(v.map { value(it) })
        else -> JsonPrimitive(v.toString())
    }
}

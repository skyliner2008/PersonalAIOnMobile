package com.skyliner2008.jarvis.tools.trading

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

expect suspend fun fetchTvHistoryBars(
    symbol: String,
    resolution: String,
    bars: Int,
    timeoutSec: Int = 12
): List<Candle>

/**
 * สร้างเฟรมคำสั่งของ TradingView websocket ด้วย JSON encoder จริง
 *
 * เดิม native bridge ต่อ string เอง แล้ว payload ของ resolve_symbol (JSON ซ้อนใน string)
 * มีเครื่องหมาย " ที่ไม่ได้ escape → เฟรมเป็น JSON ที่ผิดรูป TradingView ตอบ `protocol_error`
 * แต่ bridge ไม่ได้ฟัง error จึงรอจน timeout 12 วิ ทุกครั้ง ก่อนไปใช้เส้นสำรอง
 * (การดึงแท่งเทียนแต่ละครั้งช้า ~13–14 วิ สแกนระบบปลุก AI 1 รอบใช้ 1–2 นาที)
 */
internal object TvProtocol {
    /** ข้อความที่ TradingView ส่งเมื่อคำสั่งผิด/ดึงข้อมูลไม่ได้ — ต้องเลิกรอทันที */
    val ERROR_METHODS = setOf("protocol_error", "critical_error", "symbol_error", "series_error")

    private val json = Json { ignoreUnknownKeys = true }

    fun wrap(payload: String): String = "~m~${payload.length}~m~$payload"

    fun command(method: String, vararg params: Any): String = wrap(
        buildJsonObject {
            put("m", method)
            put("p", buildJsonArray {
                params.forEach { p ->
                    add(if (p is Number) JsonPrimitive(p) else JsonPrimitive(p.toString()))
                }
            })
        }.toString()
    )

    /** พารามิเตอร์ของ resolve_symbol: "=" ตามด้วย JSON ของ symbol */
    fun resolveSymbolParam(symbol: String): String = "=" + buildJsonObject {
        put("symbol", symbol)
        put("adjustment", "splits")
        put("session", "regular")
    }.toString()

    /** คำสั่งเริ่มต้นทั้งชุดสำหรับขอแท่งเทียนย้อนหลัง */
    fun historyRequest(chartSession: String, symbol: String, resolution: String, bars: Int): List<String> = listOf(
        command("set_data_quality", "low"),
        command("set_auth_token", "unauthorized_user_token"),
        command("chart_create_session", chartSession, ""),
        command("resolve_symbol", chartSession, "symbol_1", resolveSymbolParam(symbol)),
        command("create_series", chartSession, "s1", "s1", "symbol_1", resolution, bars.coerceIn(2, 5000)),
        command("switch_timezone", chartSession, "Etc/UTC")
    )

    /** ชื่อ error ถ้า packet เป็นข้อความ error ของ TradingView ไม่งั้น null */
    fun errorOf(packet: String): String? {
        if (!packet.startsWith("{")) return null
        val m = runCatching { json.parseToJsonElement(packet).jsonObject["m"]?.jsonPrimitive?.content }.getOrNull()
        return m?.takeIf { it in ERROR_METHODS }
    }
}

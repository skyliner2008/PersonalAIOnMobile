package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.tools.trading.TvProtocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * เฟรมคำสั่ง TradingView websocket
 * — เคยต่อ string เองจน resolve_symbol เป็น JSON ผิดรูป → TradingView ตอบ protocol_error
 *   การดึงแท่งเทียนบนมือถือจึงรอ timeout 12 วิ ทุกครั้ง
 */
class TvProtocolTest {

    private fun unwrap(frame: String): String {
        val m = Regex("^~m~(\\d+)~m~(.*)$", RegexOption.DOT_MATCHES_ALL).find(frame)!!
        val body = m.groupValues[2]
        assertEquals(m.groupValues[1].toInt(), body.length, "ความยาวในหัวเฟรมต้องตรงกับ payload")
        return body
    }

    @Test
    fun everyHistoryFrameIsValidJson() {
        val frames = TvProtocol.historyRequest("cs_test", "OANDA:XAUUSD", "15", 800)
        assertEquals(6, frames.size)
        frames.forEach { f ->
            val obj = Json.parseToJsonElement(unwrap(f)).jsonObject   // throw ถ้า JSON ผิดรูป
            assertTrue(obj["m"]!!.jsonPrimitive.content.isNotBlank())
        }
    }

    @Test
    fun resolveSymbolPayloadRoundTrips() {
        val frame = TvProtocol.historyRequest("cs_test", "TVC:DXY", "60", 200)[3]
        val p = Json.parseToJsonElement(unwrap(frame)).jsonObject["p"]!!.jsonArray
        assertEquals("resolve_symbol", Json.parseToJsonElement(unwrap(frame)).jsonObject["m"]!!.jsonPrimitive.content)
        val param = p[2].jsonPrimitive.content
        assertTrue(param.startsWith("="))
        val inner = Json.parseToJsonElement(param.removePrefix("=")).jsonObject
        assertEquals("TVC:DXY", inner["symbol"]!!.jsonPrimitive.content)
        assertEquals("regular", inner["session"]!!.jsonPrimitive.content)
    }

    @Test
    fun barCountIsNumberAndClamped() {
        val frame = TvProtocol.historyRequest("cs", "OANDA:XAUUSD", "1", 99_999)[4]
        val p = Json.parseToJsonElement(unwrap(frame)).jsonObject["p"]!!.jsonArray
        assertEquals("5000", p[5].jsonPrimitive.content)
        assertTrue(!p[5].jsonPrimitive.isString, "จำนวนแท่งต้องเป็นตัวเลข ไม่ใช่ string")
    }

    @Test
    fun detectsTradingViewErrors() {
        assertEquals("protocol_error", TvProtocol.errorOf("""{"m":"protocol_error","p":["wrong data"]}"""))
        assertEquals("symbol_error", TvProtocol.errorOf("""{"m":"symbol_error","p":["cs","symbol_1","invalid symbol"]}"""))
        assertNull(TvProtocol.errorOf("""{"m":"timescale_update","p":[]}"""))
        assertNull(TvProtocol.errorOf("~h~12"))
        assertNull(TvProtocol.errorOf("not json"))
    }
}

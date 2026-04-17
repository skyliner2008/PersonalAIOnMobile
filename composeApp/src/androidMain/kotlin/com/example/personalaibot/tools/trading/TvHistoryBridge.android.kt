package com.example.personalaibot.tools.trading

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private val tvJson by lazy { Json { ignoreUnknownKeys = true; coerceInputValues = true } }

private val tvWsClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()
}

private fun tvWrap(payload: String): String = "~m~${payload.length}~m~$payload"

private fun tvFrom(symbol: String): String = "symbols/${symbol.replace(":", "-")}/"

private fun parseFrames(buffer: String): Pair<List<String>, String> {
    val messages = mutableListOf<String>()
    var pos = 0
    while (true) {
        if (pos >= buffer.length) break
        if (!buffer.startsWith("~m~", pos)) {
            val next = buffer.indexOf("~m~", pos)
            if (next == -1) return messages to buffer.substring(pos)
            pos = next
        }
        val lenStart = pos + 3
        val lenEnd = buffer.indexOf("~m~", lenStart)
        if (lenEnd == -1) break
        val lenValue = buffer.substring(lenStart, lenEnd).toIntOrNull()
        if (lenValue == null) {
            pos = lenEnd + 3
            continue
        }
        val len = lenValue
        val msgStart = lenEnd + 3
        val msgEnd = msgStart + len
        if (msgEnd > buffer.length) break
        messages.add(buffer.substring(msgStart, msgEnd))
        pos = msgEnd
    }
    return messages to buffer.substring(pos)
}

private fun extractBarsFromTimescale(packet: String, seriesName: String): List<Candle> {
    val root = runCatching { tvJson.parseToJsonElement(packet).jsonObject }.getOrNull() ?: return emptyList()
    if (root["m"]?.jsonPrimitive?.content != "timescale_update") return emptyList()
    val payload = root["p"]?.jsonArray ?: return emptyList()
    if (payload.size < 2) return emptyList()

    val body = when (val p1 = payload[1]) {
        is JsonObject -> p1
        is JsonPrimitive -> runCatching { tvJson.parseToJsonElement(p1.content).jsonObject }.getOrNull()
        else -> null
    } ?: return emptyList()

    val series = findSeriesNode(body, seriesName) ?: return emptyList()
    val out = mutableListOf<Candle>()
    fun arrayField(value: kotlinx.serialization.json.JsonElement?): JsonArray? = when (value) {
        is JsonArray -> value
        is JsonPrimitive -> runCatching { tvJson.parseToJsonElement(value.content).jsonArray }.getOrNull()
        else -> null
    }
    fun tsSecOf(p: JsonPrimitive): Long? = p.content.toLongOrNull() ?: p.doubleOrNull?.toLong()
    // Format A: s = [{i, v:[ts,o,h,l,c,v]}]
    val sField = arrayField(series["s"])
    if (sField != null) {
        for (bar in sField) {
            val barObj = bar as? JsonObject ?: continue
            val v = arrayField(barObj["v"]) ?: continue
            if (v.size < 5) continue
            val tsSec = tsSecOf(v[0].jsonPrimitive) ?: continue
            val close = v[4].jsonPrimitive.doubleOrNull ?: continue
            out.add(
                Candle(
                    open = v[1].jsonPrimitive.doubleOrNull ?: close,
                    high = v[2].jsonPrimitive.doubleOrNull ?: close,
                    low = v[3].jsonPrimitive.doubleOrNull ?: close,
                    close = close,
                    volume = v.getOrNull(5)?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    timestamp = tsSec * 1000L
                )
            )
        }
    }
    if (out.isNotEmpty()) return out

    // Format B: t/o/h/l/c/v arrays
    val t = arrayField(series["t"]) ?: return emptyList()
    val o = arrayField(series["o"]) ?: return emptyList()
    val h = arrayField(series["h"]) ?: return emptyList()
    val l = arrayField(series["l"]) ?: return emptyList()
    val c = arrayField(series["c"]) ?: return emptyList()
    val v = arrayField(series["v"])
    val size = listOf(t.size, o.size, h.size, l.size, c.size).minOrNull() ?: 0
    if (size <= 0) return emptyList()

    for (idx in 0 until size) {
        val tsSec = tsSecOf(t[idx].jsonPrimitive) ?: continue
        val close = c[idx].jsonPrimitive.doubleOrNull ?: continue
        out.add(
            Candle(
                open = o[idx].jsonPrimitive.doubleOrNull ?: close,
                high = h[idx].jsonPrimitive.doubleOrNull ?: close,
                low = l[idx].jsonPrimitive.doubleOrNull ?: close,
                close = close,
                volume = v?.getOrNull(idx)?.jsonPrimitive?.doubleOrNull ?: 0.0,
                timestamp = tsSec * 1000L
            )
        )
    }
    return out
}

private fun looksLikeSeriesNode(node: JsonObject): Boolean {
    val s = node["s"]
    if (s is JsonArray) return true
    val t = node["t"]
    val c = node["c"]
    return t is JsonArray && c is JsonArray
}

private fun findSeriesNode(body: JsonObject, preferredKey: String): JsonObject? {
    (body[preferredKey] as? JsonObject)?.let { preferred ->
        if (looksLikeSeriesNode(preferred)) return preferred
    }
    for ((_, value) in body) {
        val node = value as? JsonObject ?: continue
        if (looksLikeSeriesNode(node)) return node
    }
    if (looksLikeSeriesNode(body)) return body
    return null
}

actual suspend fun fetchTvHistoryBars(
    symbol: String,
    resolution: String,
    bars: Int,
    timeoutSec: Int
): List<Candle> = suspendCancellableCoroutine { cont ->
    val seriesName = "s1"
    val symbolAlias = "symbol_1"
    val chartSession = "cs_${System.currentTimeMillis().toString(16).takeLast(8)}"
    val fromParam = tvFrom(symbol)
    val url = "wss://data.tradingview.com/socket.io/websocket?from=$fromParam"

    val request = Request.Builder()
        .url(url)
        .header("Origin", "https://www.tradingview.com")
        .build()

    var wsRef: WebSocket? = null
    val timeoutThread = Thread {
        try {
            Thread.sleep(timeoutSec * 1000L)
            if (cont.isActive) {
                wsRef?.close(1000, "timeout")
                cont.resume(emptyList())
            }
        } catch (_: Exception) {}
    }

    val listener = object : WebSocketListener() {
        private var buffer = ""

        private fun sendCmd(ws: WebSocket, method: String, paramsJson: String) {
            ws.send(tvWrap("{\"m\":\"$method\",\"p\":$paramsJson}"))
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            wsRef = webSocket
            timeoutThread.start()
            webSocket.send(tvWrap("{\"m\":\"set_data_quality\",\"p\":[\"low\"]}"))
            webSocket.send(tvWrap("{\"m\":\"set_auth_token\",\"p\":[\"unauthorized_user_token\"]}"))
            sendCmd(webSocket, "chart_create_session", "[\"$chartSession\",\"\"]")
            val resolve = "={\"symbol\":\"$symbol\",\"adjustment\":\"splits\",\"session\":\"regular\"}"
            sendCmd(webSocket, "resolve_symbol", "[\"$chartSession\",\"$symbolAlias\",\"$resolve\"]")
            sendCmd(webSocket, "create_series", "[\"$chartSession\",\"$seriesName\",\"$seriesName\",\"$symbolAlias\",\"$resolution\",${bars.coerceIn(2, 5000)}]")
            sendCmd(webSocket, "switch_timezone", "[\"$chartSession\",\"Etc/UTC\"]")
        }

        private fun handleText(webSocket: WebSocket, text: String) {
            buffer += text
            val (packets, rem) = parseFrames(buffer)
            buffer = rem
            for (packet in packets) {
                if (packet.startsWith("~h~")) {
                    webSocket.send(tvWrap(packet))
                    continue
                }
                val barsOut = extractBarsFromTimescale(packet, seriesName)
                if (barsOut.isNotEmpty() && cont.isActive) {
                    webSocket.close(1000, "done")
                    cont.resume(barsOut.sortedBy { it.timestamp })
                    return
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleText(webSocket, text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleText(webSocket, bytes.utf8())
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (cont.isActive) cont.resume(emptyList())
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (cont.isActive) cont.resume(emptyList())
        }
    }

    val ws = tvWsClient.newWebSocket(request, listener)
    wsRef = ws
    cont.invokeOnCancellation { ws.close(1000, "cancelled") }
}

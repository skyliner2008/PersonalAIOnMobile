package com.example.personalaibot.tools.trading

import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class Mt5AccountInfo(
    val login: String = "",
    val accountName: String = "",
    val server: String = "",
    val currency: String = "USD",
    val leverage: Int = 0,
    val balance: Double = 0.0,
    val equity: Double = 0.0,
    val margin: Double = 0.0,
    val freeMargin: Double = 0.0,
    val payloadJson: String = "",
    val updatedAt: Long = 0L
)

data class Mt5SymbolInfo(
    val symbol: String,
    val description: String = "",
    val digits: Int = 0,
    val point: Double = 0.0,
    val tradeMode: String = "",
    val bid: Double = 0.0,
    val ask: Double = 0.0,
    val spread: Double = 0.0,
    val payloadJson: String = "",
    val updatedAt: Long = 0L
)

data class Mt5TradeItem(
    val recordType: String,
    val ticket: String,
    val positionTicket: String = "",
    val symbol: String,
    val side: String = "",
    val volume: Double = 0.0,
    val priceOpen: Double = 0.0,
    val priceCurrent: Double = 0.0,
    val profit: Double = 0.0,
    val swap: Double = 0.0,
    val commission: Double = 0.0,
    val sl: Double = 0.0,
    val tp: Double = 0.0,
    val state: String = "",
    val comment: String = "",
    val eventTime: Long = 0L,
    val payloadJson: String = "",
    val syncedAt: Long = 0L,
    // MT5 deal entry type: 0=ENTRY_IN, 1=ENTRY_OUT, 2=INOUT, 3=OUT_BY, -1=unknown/position
    val entry: Int = -1
) {
    /** True when this is an exit deal (has real P&L). Entry deals have profit=0. */
    val isExitDeal: Boolean get() = entry > 0
    /** True when this is an entry deal (open side, profit=0, may have commission). */
    val isEntryDeal: Boolean get() = entry == 0
    /** Net P&L including swap and commission. */
    val netPnl: Double get() = profit + swap + commission
}

data class Mt5TerminalSnapshot(
    val account: Mt5AccountInfo?,
    val symbols: List<Mt5SymbolInfo>,
    val positions: List<Mt5TradeItem>,
    val orders: List<Mt5TradeItem>,
    val deals: List<Mt5TradeItem>,
    val syncedAt: Long
)

data class Mt5SnapshotDelta(
    val changed: Boolean,
    val revision: String,
    val changedSections: Set<String>,
    val snapshot: Mt5TerminalSnapshot?,
    val syncedAt: Long
)

class Mt5TerminalService(private val client: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun fetchSnapshot(
        baseUrl: String,
        authToken: String = "",
        historyLimit: Int = 200
    ): Mt5TerminalSnapshot {
        return fetchSnapshotLegacy(baseUrl, authToken, historyLimit)
    }

    suspend fun fetchSnapshotDelta(
        baseUrl: String,
        authToken: String = "",
        historyLimit: Int = 200,
        sinceRevision: String = "",
        waitMs: Int = 0
    ): Mt5SnapshotDelta {
        val normalized = baseUrl.trim().trimEnd('/')
        val token = authToken.trim()
        val now = nowMs()
        val result = getJson(
            "$normalized/snapshot",
            params = buildMap {
                put("limit", historyLimit.toString())
                if (sinceRevision.isNotBlank()) put("since", sinceRevision)
                if (waitMs > 0) put("waitMs", waitMs.toString())
            },
            authToken = token
        )
        if (result.ok && result.element != null) {
            parseSnapshotDeltaElement(result.element, fallbackNow = now)?.let { return it }
        }

        logDebug("Mt5TerminalService", "snapshot delta endpoint unavailable, fallback to legacy fanout")
        val legacy = fetchSnapshotLegacy(baseUrl = normalized, authToken = token, historyLimit = historyLimit)
        return Mt5SnapshotDelta(
            changed = true,
            revision = "",
            changedSections = setOf("account", "symbols", "positions", "orders", "history"),
            snapshot = legacy,
            syncedAt = legacy.syncedAt
        )
    }

    fun parseSnapshotDeltaElement(
        element: JsonElement,
        fallbackNow: Long = nowMs()
    ): Mt5SnapshotDelta? {
        val root = element as? JsonObject
        val dataObj = when (val data = root?.get("data")) {
            is JsonObject -> data
            else -> root
        } ?: buildEmptyObject()

        val changed = bool(dataObj, "changed", default = true)
        val revision = str(dataObj, "revision")
        val syncedAt = numLong(dataObj, "syncedAt", "synced_at", "checkedAt", "timestamp").takeIf { it > 0L } ?: fallbackNow
        val changedSections = dataObj["changedSections"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.lowercase() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

        if (!changed) {
            return Mt5SnapshotDelta(
                changed = false,
                revision = revision,
                changedSections = emptySet(),
                snapshot = null,
                syncedAt = syncedAt
            )
        }

        val snapshotObj = dataObj["snapshot"] as? JsonObject ?: return null
        val accountEl = snapshotObj["account"]
        val account = accountEl?.let { parseAccount(it, syncedAt) }
        val symbols = parseSymbols(snapshotObj["symbols"], syncedAt)
        val positions = parseTrades(snapshotObj["positions"], "POSITION", syncedAt)
        val orders = parseTrades(snapshotObj["orders"], "ORDER", syncedAt)
        val deals = parseTrades(snapshotObj["history"] ?: snapshotObj["deals"], "DEAL", syncedAt)
        return Mt5SnapshotDelta(
            changed = true,
            revision = revision,
            changedSections = changedSections,
            snapshot = Mt5TerminalSnapshot(
                account = account,
                symbols = symbols,
                positions = positions,
                orders = orders,
                deals = deals,
                syncedAt = syncedAt
            ),
            syncedAt = syncedAt
        )
    }

    private suspend fun fetchSnapshotLegacy(
        baseUrl: String,
        authToken: String = "",
        historyLimit: Int = 200
    ): Mt5TerminalSnapshot {
        val normalized = baseUrl.trim().trimEnd('/')
        val token = authToken.trim()
        val now = nowMs()
        logDebug("Mt5TerminalService", "fetchSnapshot base=$normalized limit=$historyLimit token=${if (token.isBlank()) "none" else "set"}")
        val accountResult = getJson("$normalized/account", authToken = token)
        val symbolsResult = getJson("$normalized/symbols", authToken = token)
        val positionsResult = getJson("$normalized/positions", authToken = token)
        val ordersResult = getJson("$normalized/orders", authToken = token)
        val historyResult = getJson(
            "$normalized/history",
            params = mapOf("limit" to historyLimit.toString()),
            authToken = token
        )

        val successCount = listOf(accountResult.ok, symbolsResult.ok, positionsResult.ok, ordersResult.ok, historyResult.ok).count { it }
        logDebug(
            "Mt5TerminalService",
            "snapshot endpoints ok=$successCount/5 account=${accountResult.ok} symbols=${symbolsResult.ok} positions=${positionsResult.ok} orders=${ordersResult.ok} history=${historyResult.ok}"
        )
        if (successCount == 0) {
            throw IllegalStateException("MT5 endpoints unavailable (all requests failed)")
        }

        val account = accountResult.element?.let { parseAccount(it, now) }
        val symbols = parseSymbols(symbolsResult.element, now)
        val positions = parseTrades(positionsResult.element, "POSITION", now)
        val orders = parseTrades(ordersResult.element, "ORDER", now)
        val deals = parseTrades(historyResult.element, "DEAL", now)

        return Mt5TerminalSnapshot(
            account = account,
            symbols = symbols,
            positions = positions,
            orders = orders,
            deals = deals,
            syncedAt = now
        )
    }

    private suspend fun getJson(
        url: String,
        params: Map<String, String> = emptyMap(),
        authToken: String = ""
    ): FetchResult {
        return try {
            val response = client.get(url) {
                params.forEach { (k, v) -> parameter(k, v) }
                if (authToken.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $authToken")
                }
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                logError(
                    "Mt5TerminalService",
                    "GET $url failed status=${response.status.value} body=${body.take(180)}"
                )
                return FetchResult(null, false)
            }
            logDebug("Mt5TerminalService", "GET $url ok status=${response.status.value}")
            FetchResult(json.parseToJsonElement(body), true)
        } catch (e: Exception) {
            logError("Mt5TerminalService", "GET $url exception=${e.message}", e)
            FetchResult(null, false)
        }
    }

    private data class FetchResult(
        val element: JsonElement?,
        val ok: Boolean
    )

    private fun parseAccount(root: JsonElement, now: Long): Mt5AccountInfo {
        val o = unwrapObject(root)
        return Mt5AccountInfo(
            login = str(o, "login"),
            accountName = str(o, "name", "account_name", "accountName"),
            server = str(o, "server"),
            currency = str(o, "currency").ifBlank { "USD" },
            leverage = numInt(o, "leverage"),
            balance = numDouble(o, "balance"),
            equity = numDouble(o, "equity"),
            margin = numDouble(o, "margin"),
            freeMargin = numDouble(o, "free_margin", "freeMargin"),
            payloadJson = o.toString(),
            updatedAt = now
        )
    }

    private fun parseSymbols(root: JsonElement?, now: Long): List<Mt5SymbolInfo> {
        val arr = unwrapArray(root) ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val symbol = str(o, "symbol", "name").uppercase()
            if (symbol.isBlank()) return@mapNotNull null
            val bid = numDouble(o, "bid")
            val ask = numDouble(o, "ask")
            val spread = if (bid > 0.0 && ask > 0.0) ask - bid else numDouble(o, "spread")
            Mt5SymbolInfo(
                symbol = symbol,
                description = str(o, "description"),
                digits = numInt(o, "digits"),
                point = numDouble(o, "point"),
                tradeMode = str(o, "trade_mode", "tradeMode"),
                bid = bid,
                ask = ask,
                spread = spread,
                payloadJson = o.toString(),
                updatedAt = now
            )
        }
    }

    private fun parseTrades(root: JsonElement?, recordType: String, now: Long): List<Mt5TradeItem> {
        val arr = unwrapArray(root) ?: return emptyList()
        val isDeal = recordType.equals("DEAL", ignoreCase = true)
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val ticket = str(o, "ticket", "order", "deal", "id")
            val symbol = str(o, "symbol", "name").uppercase()
            if (ticket.isBlank()) return@mapNotNull null
            // Skip balance/credit deals (no symbol, type >= 2 for non-trade entries)
            if (isDeal && symbol.isBlank()) return@mapNotNull null

            val entryCode = if (isDeal) numInt(o, "entry") else -1
            // For deals, resolve the ORIGINAL trade side:
            // Exit deal closing a BUY has type=SELL → flip to BUY for display
            val rawSide = resolveSide(o)
            val side = if (isDeal && entryCode > 0) {
                // Exit deal: flip side to show original trade direction
                if (rawSide == "BUY") "SELL" else if (rawSide == "SELL") "BUY" else rawSide
            } else {
                rawSide
            }

            Mt5TradeItem(
                recordType = recordType,
                ticket = ticket,
                positionTicket = str(o, "position", "position_id", "position_ticket"),
                symbol = symbol,
                side = side,
                volume = numDouble(o, "volume", "lots", "qty"),
                priceOpen = numDouble(o, "price_open", "open_price", "price"),
                priceCurrent = numDouble(o, "price_current", "current_price", "price"),
                profit = numDouble(o, "profit"),
                swap = numDouble(o, "swap"),
                commission = numDouble(o, "commission"),
                sl = numDouble(o, "sl", "stop_loss"),
                tp = numDouble(o, "tp", "take_profit"),
                state = str(o, "state", "status"),
                comment = str(o, "comment"),
                eventTime = numLong(o, "time", "time_msc", "timestamp", "event_time"),
                payloadJson = o.toString(),
                syncedAt = now,
                entry = entryCode
            )
        }
    }

    private fun unwrapObject(root: JsonElement): JsonObject {
        val obj = root as? JsonObject ?: return buildEmptyObject()
        val data = obj["data"]
        if (data is JsonObject) return data
        val result = obj["result"]
        if (result is JsonObject) return result
        return obj
    }

    private fun unwrapArray(root: JsonElement?): JsonArray? {
        if (root == null) return null
        if (root is JsonArray) return root
        val obj = root as? JsonObject ?: return null
        val keys = listOf("data", "result", "items", "positions", "orders", "history", "deals", "symbols")
        for (k in keys) {
            val candidate = obj[k]
            if (candidate is JsonArray) return candidate
        }
        return null
    }

    // Map any combination of `side`/`type`/`action` from the bridge into a
    // canonical "BUY" / "SELL" string. Tolerates: readable strings ("BUY",
    // "buy", "buy_limit"), numeric MT5 codes (0/1/2/3/4/5), and stringified
    // numbers ("0", "1"). Returns "" when nothing recognizable is present.
    private fun resolveSide(o: JsonObject): String {
        // 1) Readable side string from backend normalization wins.
        val sideText = str(o, "side").lowercase()
        if (sideText.isNotEmpty()) {
            if (sideText.contains("buy")) return "BUY"
            if (sideText.contains("sell")) return "SELL"
        }
        // 2) `action` alias used by some payloads.
        val actionText = str(o, "action").lowercase()
        if (actionText.isNotEmpty()) {
            if (actionText.contains("buy") || actionText == "long") return "BUY"
            if (actionText.contains("sell") || actionText == "short") return "SELL"
        }
        // 3) Raw MT5 type codes. Positions: 0=BUY, 1=SELL.
        //    Pending orders 2..5 alternate BUY/SELL by parity (even=BUY, odd=SELL).
        val typeRaw = o["type"]
        if (typeRaw != null && typeRaw !is JsonNull) {
            val asInt = typeRaw.jsonPrimitive.intOrNull
                ?: typeRaw.jsonPrimitive.contentOrNull?.trim()?.toIntOrNull()
            if (asInt != null) {
                return if (asInt % 2 == 0) "BUY" else "SELL"
            }
            val typeText = typeRaw.jsonPrimitive.contentOrNull?.trim()?.lowercase().orEmpty()
            if (typeText.contains("buy")) return "BUY"
            if (typeText.contains("sell")) return "SELL"
        }
        return ""
    }

    private fun str(o: JsonObject, vararg keys: String): String {
        for (k in keys) {
            val v = o[k]
            if (v != null && v !is JsonNull) {
                val text = v.jsonPrimitive.contentOrNull?.trim().orEmpty()
                if (text.isNotEmpty()) return text
            }
        }
        return ""
    }

    private fun bool(o: JsonObject, key: String, default: Boolean): Boolean {
        val p = o[key]?.jsonPrimitive ?: return default
        p.contentOrNull?.let { text ->
            return when (text.trim().lowercase()) {
                "true", "1", "yes", "ok" -> true
                "false", "0", "no" -> false
                else -> default
            }
        }
        return default
    }

    private fun numDouble(o: JsonObject, vararg keys: String): Double {
        for (k in keys) {
            val p = o[k]?.jsonPrimitive ?: continue
            p.doubleOrNull?.let { return it }
            p.contentOrNull?.toDoubleOrNull()?.let { return it }
        }
        return 0.0
    }

    private fun numInt(o: JsonObject, vararg keys: String): Int {
        for (k in keys) {
            val p = o[k]?.jsonPrimitive ?: continue
            p.intOrNull?.let { return it }
            p.contentOrNull?.toIntOrNull()?.let { return it }
        }
        return 0
    }

    private fun numLong(o: JsonObject, vararg keys: String): Long {
        for (k in keys) {
            val p = o[k]?.jsonPrimitive ?: continue
            p.longOrNull?.let { return it }
            p.contentOrNull?.toLongOrNull()?.let { return it }
        }
        return 0L
    }

    private fun buildEmptyObject(): JsonObject = JsonObject(emptyMap())

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
}

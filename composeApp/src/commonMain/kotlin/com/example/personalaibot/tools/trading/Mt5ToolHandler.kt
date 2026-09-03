package com.example.personalaibot.tools.trading

import io.ktor.client.*
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.*

/**
 * Mt5ToolHandler: Handles all MT5 broker operations, order execution, position management,
 * market scanner, intelligence radar, and account diagnostics.
 */
internal class Mt5ToolHandler(
    private val client: HttpClient,
    private val api: TradingApiService
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun execute(toolName: String, args: Map<String, String>): String = when (toolName) {
        "trading_mt5_order" -> executeMt5Order(args)
        "trading_mt5_close_position" -> executeMt5ClosePosition(args)
        "trading_mt5_modify_position" -> executeMt5ModifyPosition(args)
        "trading_mt5_account_info" -> executeMt5AccountInfo(args)
        "trading_mt5_list_positions" -> executeMt5ListPositions(args)
        "trading_mt5_list_orders" -> executeMt5ListOrders(args)
        "trading_mt5_list_history" -> executeMt5ListHistory(args)
        "trading_mt5_candles" -> executeMt5Candles(args)
        "trading_mt5_analyze" -> executeMt5Analyze(args)
        "trading_mt5_symbol_info" -> executeMt5SymbolInfo(args)
        "trading_mt5_symbol_search" -> executeMt5SymbolSearch(args)
        "trading_mt5_close_all" -> executeMt5CloseAll(args)
        "trading_mt5_break_even_all" -> executeMt5BreakEvenAll(args)
        "trading_mt5_snapshot" -> executeMt5Snapshot(args)
        "trading_mt5_trade_actions" -> executeMt5TradeActions(args)
        "trading_mt5_market_scanner" -> executeMt5MarketScanner(args)
        "trading_mt5_correlation_radar" -> executeMt5CorrelationRadar(args)
        "trading_mt5_sentiment_gauge" -> executeMt5SentimentGauge(args)
        "trading_mt5_institutional_flow" -> executeMt5InstitutionalFlow(args)
        "trading_mt5_economic_radar" -> executeMt5EconomicRadar(args)
        "trading_mt5_trade_journal" -> executeMt5TradeJournal(args)
        else -> "Unknown MT5 tool: $toolName"
    }

    internal suspend fun executeMt5Order(args: Map<String, String>): String {
        val action = args["action"]?.uppercase() ?: return "Missing required argument: action"
        if (action != "BUY" && action != "SELL") return "Invalid action: $action (allowed: BUY, SELL)"

        val symbol = args["symbol"]?.trim().orEmpty()
        if (symbol.isBlank()) return "Missing required argument: symbol"

        val volume = args["volume"]?.toDoubleOrNull()
            ?: return "Missing or invalid required argument: volume"
        if (volume <= 0.0) return "Invalid volume: must be > 0"

        var riskArgs = args
        if (args["strategy_gate"] != null) {
            val riskEndpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
                ?.substringBeforeLast("/")
                ?: "http://127.0.0.1:8090/api/mt5"
            val provider = com.example.personalaibot.automation.backtest.Mt5LiveAccountProvider(
                terminalService = Mt5TerminalService(client),
                bridgeBaseUrlProvider = { riskEndpoint },
                authTokenProvider = { args["token"]?.trim().orEmpty() },
            )
            val account = provider.getAccount()
                ?: return "❌ Risk Engine: MT5 Live account unavailable. Switch to DEMO for offline simulation."
            riskArgs = args + mapOf(
                "equity" to account.equity.toString(),
                "balance" to account.balance.toString(),
                "free_margin" to account.freeMargin.toString(),
                "today_pnl" to account.todayPnL.toString(),
                "open_positions" to account.openPositions.toString(),
                "current_exposure_pct" to account.currentExposurePct.toString(),
                "correlated_exposure_pct" to account.correlatedExposurePct.toString(),
                "risk_snapshot_source" to "LIVE",
            )
        }
        Mt5RiskGate.validateOrder(riskArgs)?.let { return "❌ $it" }

        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/order"
        val token = args["token"]?.trim().orEmpty()

        val sl = args["sl"]?.toDoubleOrNull()
        val tp = args["tp"]?.toDoubleOrNull()
        val comment = args["comment"]?.trim().orEmpty()

        return try {
            val payload = buildJsonObject {
                put("action", action)
                put("symbol", symbol)
                put("volume", JsonPrimitive(volume))
                put("sl", sl?.let { JsonPrimitive(it) } ?: JsonNull)
                put("tp", tp?.let { JsonPrimitive(it) } ?: JsonNull)
                put("comment", comment)
            }
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            val ok = response.status.value in 200..299
            if (ok) {
                "MT5 order sent successfully ($action $symbol $volume)\nStatus: ${response.status.value}\nResponse: $body"
            } else {
                "MT5 order failed ($action $symbol $volume)\nStatus: ${response.status.value}\nResponse: $body"
            }
        } catch (e: Exception) {
            "MT5 order request error: ${e.message}"
        }
    }

    internal suspend fun executeMt5ClosePosition(args: Map<String, String>): String {
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/close"
        val token = args["token"]?.trim().orEmpty()
        val symbol = args["symbol"]?.trim().orEmpty()
        val ticket = args["ticket"]?.trim().orEmpty()

        if (symbol.isBlank() && ticket.isBlank()) {
            return "Missing required argument: provide at least one of symbol or ticket"
        }

        return try {
            val payload = buildJsonObject {
                put("symbol", symbol.ifBlank { "" })
                put("ticket", ticket.ifBlank { "" })
            }
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            val ok = response.status.value in 200..299
            if (ok) {
                "MT5 close command sent successfully\nStatus: ${response.status.value}\nResponse: $body"
            } else {
                "MT5 close command failed\nStatus: ${response.status.value}\nResponse: $body"
            }
        } catch (e: Exception) {
            "MT5 close request error: ${e.message}"
        }
    }

    internal suspend fun executeMt5ModifyPosition(args: Map<String, String>): String {
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/modify"
        val token = args["token"]?.trim().orEmpty()
        val symbol = args["symbol"]?.trim().orEmpty()
        val ticket = args["ticket"]?.trim().orEmpty()
        val sl = args["sl"]?.trim()?.toDoubleOrNull()
        val tp = args["tp"]?.trim()?.toDoubleOrNull()
        val comment = args["comment"]?.trim().orEmpty()

        if (symbol.isBlank() && ticket.isBlank()) {
            return "Missing required argument: provide at least one of symbol or ticket"
        }
        if (sl == null && tp == null) {
            return "Missing required argument: provide at least one of sl or tp"
        }

        return try {
            val payload = buildJsonObject {
                if (symbol.isNotBlank()) put("symbol", symbol)
                if (ticket.isNotBlank()) put("ticket", ticket)
                if (sl != null) put("sl", JsonPrimitive(sl))
                if (tp != null) put("tp", JsonPrimitive(tp))
                if (comment.isNotBlank()) put("comment", comment)
            }
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            val ok = response.status.value in 200..299
            if (ok) {
                "MT5 modify sent successfully (symbol=$symbol ticket=$ticket sl=$sl tp=$tp)\nStatus: ${response.status.value}\nResponse: $body"
            } else {
                "MT5 modify failed (symbol=$symbol ticket=$ticket)\nStatus: ${response.status.value}\nResponse: $body"
            }
        } catch (e: Exception) {
            "MT5 modify request error: ${e.message}"
        }
    }

    internal suspend fun mt5Get(endpoint: String, token: String = ""): String {
        return try {
            val response = client.get(endpoint) {
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Client-Token", token)
                }
            }
            response.bodyAsText()
        } catch (e: Exception) {
            throw Exception("MT5 bridge connection failed: ${e.message}")
        }
    }

    internal suspend fun mt5GetWithArgs(endpoint: String, args: Map<String, String>): String =
        mt5Get(endpoint, args["token"]?.trim().orEmpty())

    internal fun formatMt5CandlesResponse(symbol: String, timeframe: String, requestedCount: Int, body: String): String {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val outerSuccess = root.booleanField("success") ?: true
            val candles: JsonArray = when (val dataEl = root["data"]) {
                is JsonArray  -> dataEl
                is JsonObject -> dataEl["data"] as? JsonArray ?: JsonArray(emptyList())
                else          -> JsonArray(emptyList())
            }
            val innerSuccess = outerSuccess

            when {
                !outerSuccess || !innerSuccess ->
                    "MT5 Candles ($symbol $timeframe) returned an unsuccessful response from broker."

                candles.isEmpty() ->
                    "MT5 Candles ($symbol $timeframe) returned no candle data from broker."

                else -> {
                    val first = candles.first().jsonObject
                    val last = candles.last().jsonObject
                    val highs = candles.mapNotNull { it.jsonObject.doubleField("h") }
                    val lows = candles.mapNotNull { it.jsonObject.doubleField("l") }
                    val volumes = candles.mapNotNull { it.jsonObject.doubleField("v") }
                    val firstClose = first.doubleField("c")
                    val lastClose = last.doubleField("c")
                    val netChange = if (firstClose != null && lastClose != null) lastClose - firstClose else null
                    val netChangePct = if (firstClose != null && lastClose != null && firstClose != 0.0) {
                        ((lastClose - firstClose) / firstClose) * 100.0
                    } else null

                    buildString {
                        appendLine("MT5 Candles ($symbol $timeframe, ${candles.size} bars) from broker")
                        appendLine("Latest candle:")
                        appendLine("O=${last.stringField("o")} H=${last.stringField("h")} L=${last.stringField("l")} C=${last.stringField("c")} V=${last.stringField("v")}")
                        appendLine("Range summary:")
                        appendLine("High=${highs.maxOrNull()?.format4() ?: "N/A"} Low=${lows.minOrNull()?.format4() ?: "N/A"} AvgVolume=${volumes.averageOrNull()?.format0() ?: "N/A"}")
                        if (netChange != null && netChangePct != null) {
                            appendLine("Net change over ${candles.size} bars: ${netChange.format4()} (${netChangePct.format2()}%)")
                        }
                        appendLine("Requested bars: $requestedCount")
                        append("Structured broker payload kept out of visible chat to avoid dumping raw JSON.")
                    }
                }
            }
        } catch (_: Exception) {
            "MT5 Candles ($symbol $timeframe, $requestedCount bars) from broker.\nStructured payload received, but raw JSON was hidden from chat."
        }
    }

    internal fun JsonObject.booleanField(key: String): Boolean? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.toBooleanStrictOrNull()

    internal fun JsonObject.doubleField(key: String): Double? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.toDoubleOrNull()

    internal fun JsonObject.stringField(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull() ?: "N/A"

    internal fun Double.format4(): String = "%.4f".format(this)
    internal fun Double.format2(): String = "%.2f".format(this)
    internal fun Double.format0(): String = "%.0f".format(this)

    internal fun Iterable<Double>.averageOrNull(): Double? {
        val values = toList()
        return if (values.isEmpty()) null else values.average()
    }

    internal suspend fun executeMt5AccountInfo(args: Map<String, String>): String {
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/account"
        return try {
            val body = mt5GetWithArgs(endpoint, args)
            "MT5 Account Info:\n$body"
        } catch (e: Exception) {
            "MT5 account info error: ${e.message}"
        }
    }

    internal suspend fun executeMt5ListPositions(args: Map<String, String>): String {
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/positions"
        return try {
            val body = mt5GetWithArgs(endpoint, args)
            "MT5 Open Positions:\n$body"
        } catch (e: Exception) {
            "MT5 list positions error: ${e.message}"
        }
    }

    internal suspend fun executeMt5ListOrders(args: Map<String, String>): String {
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/orders"
        return try {
            val body = mt5GetWithArgs(endpoint, args)
            "MT5 Pending Orders:\n$body"
        } catch (e: Exception) {
            "MT5 list orders error: ${e.message}"
        }
    }

    internal suspend fun executeMt5ListHistory(args: Map<String, String>): String {
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/history"
        val days = args["days"]?.toIntOrNull() ?: 7
        val url = if (endpoint.contains("?")) "$endpoint&days=$days" else "$endpoint?days=$days"
        return try {
            val body = mt5GetWithArgs(url, args)
            "MT5 Trade History (last $days days):\n$body"
        } catch (e: Exception) {
            "MT5 history error: ${e.message}"
        }
    }

    internal suspend fun executeMt5Candles(args: Map<String, String>): String {
        val symbol = args["symbol"]?.trim().orEmpty()
        if (symbol.isBlank()) return "Missing required argument: symbol"

        val timeframe = TaIndicators.toMt5Timeframe(args["timeframe"] ?: args["interval"] ?: "1h")
        val count = args["count"]?.toIntOrNull() ?: 50
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/candles"
        val sep = if (endpoint.contains("?")) "&" else "?"
        val url = "$endpoint${sep}symbol=$symbol&timeframe=$timeframe&count=$count"
        return try {
            val body = mt5GetWithArgs(url, args)
            formatMt5CandlesResponse(symbol, timeframe, count, body)
        } catch (e: Exception) {
            "MT5 candles error: ${e.message}"
        }
    }

    internal suspend fun executeMt5Analyze(args: Map<String, String>): String {
        val symbol = args["symbol"]?.trim().orEmpty()
        if (symbol.isBlank()) return "Missing required argument: symbol"

        val timeframe = TaIndicators.toMt5Timeframe(args["timeframe"] ?: args["interval"] ?: "1h")
        val count = (args["count"]?.toIntOrNull() ?: 100).coerceAtLeast(35)
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/candles"
        val sep = if (endpoint.contains("?")) "&" else "?"
        val url = "$endpoint${sep}symbol=$symbol&timeframe=$timeframe&count=$count"

        return try {
            val body = mt5GetWithArgs(url, args)
            val root = json.parseToJsonElement(body).jsonObject
            val candles = root["data"]?.jsonArray ?: JsonArray(emptyList())

            if (candles.isEmpty()) {
                return "MT5 Analyze: ไม่พบข้อมูลแท่งเทียนสำหรับ $symbol ($timeframe)"
            }

            val closes = candles.mapNotNull { it.jsonObject["c"]?.jsonPrimitive?.doubleOrNull }
            val highs  = candles.mapNotNull { it.jsonObject["h"]?.jsonPrimitive?.doubleOrNull }
            val lows   = candles.mapNotNull { it.jsonObject["l"]?.jsonPrimitive?.doubleOrNull }

            if (closes.size < 35) {
                return "MT5 Analyze: แท่งเทียนไม่พอสำหรับคำนวณ indicator (ต้องการอย่างน้อย 35, ได้ ${closes.size})"
            }

            val currentPrice = closes.last()
            val rsi = TaIndicators.rsi(closes, 14) ?: 50.0
            val macd = TaIndicators.macd(closes) ?: TaIndicators.MacdResult(0.0, 0.0, 0.0)
            val macdVal = macd.macd
            val macdSig = macd.signal
            val macdHist = macd.hist
            val stoch = TaIndicators.stochastic(closes, highs, lows, 14, 3, 3) ?: TaIndicators.StochResult(50.0, 50.0)
            val stochK = stoch.k
            val stochD = stoch.d
            val bb = TaIndicators.bollingerBands(closes, 20, 2.0) ?: TaIndicators.BollingerBands(currentPrice, currentPrice, currentPrice, 0.0, 50.0)
            val bbUpper = bb.upper
            val bbBasis = bb.basis
            val bbLower = bb.lower
            val cci = TaIndicators.cci(highs, lows, closes, 20) ?: 0.0
            val adx = TaIndicators.adx(highs, lows, closes, 14) ?: TaIndicators.AdxResult(15.0, 20.0, 20.0)
            val adxVal = adx.adx
            val diPlus = adx.diPlus
            val diMinus = adx.diMinus
            val st = TaIndicators.supertrend(highs, lows, closes, 10, 3.0) ?: TaIndicators.SupertrendResult(currentPrice, true)
            val stVal = st.value
            val stBullish = st.isBullish

            fun ema(src: List<Double>, p: Int): Double {
                val k = 2.0 / (p + 1)
                var e = src.take(p).average()
                for (i in p until src.size) e = src[i] * k + e * (1 - k)
                return e
            }
            val ema20 = ema(closes, 20)
            val ema50 = if (closes.size >= 50) ema(closes, 50) else null
            val ema200 = if (closes.size >= 200) ema(closes, 200) else null

            var score = 0
            if (rsi in 40.0..65.0) score++ else if (rsi < 30.0) score += 2 else if (rsi > 70.0) score -= 2
            if (macdHist > 0) score += 2 else score -= 2
            if (macdVal > macdSig) score++ else score--
            if (currentPrice > bbBasis) score++ else score--
            if (currentPrice > ema20) score++ else score--
            if (ema50 != null && currentPrice > ema50) score++ else if (ema50 != null) score--
            if (stBullish) score += 2 else score -= 2
            if (adxVal > 25.0 && diPlus > diMinus) score += 2 else if (adxVal > 25.0 && diMinus > diPlus) score -= 2

            val signal = when {
                score >= 5 -> "STRONG BUY 🟢🟢"
                score in 2..4 -> "BUY 🟢"
                score in -1..1 -> "NEUTRAL ⚪"
                score in -4..-2 -> "SELL 🔴"
                else -> "STRONG SELL 🔴🔴"
            }

            val rsiStatus = when {
                rsi < 30.0 -> "Oversold ($rsi)"
                rsi > 70.0 -> "Overbought ($rsi)"
                rsi in 45.0..55.0 -> "Neutral ($rsi)"
                rsi > 55.0 -> "Bullish Momentum ($rsi)"
                else -> "Bearish Momentum ($rsi)"
            }

            val bbStatus = when {
                currentPrice >= bbUpper -> "แตะ Upper Band (เสี่ยงย่อ)"
                currentPrice <= bbLower -> "แตะ Lower Band (อาจ Rebound)"
                else -> "อยู่ในกรอบ (Basis: ${"%.4f".format(bbBasis)})"
            }

            val stStatus = if (stBullish) "BULLISH (แนวรับ ${"%.4f".format(stVal)})"
                           else "BEARISH (แนวต้าน ${"%.4f".format(stVal)})"

            val adxStatus = when {
                adxVal > 25.0 && diPlus > diMinus -> "เทรนด์ขาขึ้นแข็งแกร่ง (ADX ${"%.1f".format(adxVal)})"
                adxVal > 25.0 && diMinus > diPlus -> "เทรนด์ขาลงแข็งแกร่ง (ADX ${"%.1f".format(adxVal)})"
                else -> "Sideway / ไม่มีเทรนด์ชัดเจน (ADX ${"%.1f".format(adxVal)})"
            }

            val emaTrend = when {
                ema50 != null && ema200 != null -> when {
                    ema20 > ema50 && ema50 > ema200 -> "Bullish Alignment (EMA20 > 50 > 200)"
                    ema20 < ema50 && ema50 < ema200 -> "Bearish Alignment (EMA20 < 50 < 200)"
                    else -> "Mixed Alignment"
                }
                ema50 != null -> if (ema20 > ema50) "EMA20 > EMA50 (Short-term Bullish)" else "EMA20 < EMA50 (Short-term Bearish)"
                else -> if (currentPrice > ema20) "เหนือ EMA20" else "ใต้ EMA20"
            }

            buildString {
                appendLine("📊 MT5 Real-time Technical Analysis")
                appendLine("Symbol: $symbol | TF: $timeframe | Bars: ${closes.size}")
                appendLine("═".repeat(45))
                appendLine("💰 ราคาปัจจุบัน: ${"%.4f".format(currentPrice)}")
                appendLine("🎯 สัญญาณรวม: $signal (คะแนน: $score)")
                appendLine("═".repeat(45))
                appendLine("📈 INDICATORS:")
                appendLine("  • RSI(14): ${"%.2f".format(rsi)} → $rsiStatus")
                appendLine("  • MACD(12,26,9): ${"%.4f".format(macdVal)} | Signal: ${"%.4f".format(macdSig)} | Hist: ${"%.4f".format(macdHist)}")
                appendLine("  • Stochastic(14,3,3): %K=${"%.2f".format(stochK)} | %D=${"%.2f".format(stochD)}")
                appendLine("  • Bollinger Bands(20,2): [${"%.4f".format(bbLower)} - ${"%.4f".format(bbUpper)}] → $bbStatus")
                appendLine("  • Supertrend(10,3): $stStatus")
                appendLine("  • ADX(14): $adxStatus (+DI: ${"%.1f".format(diPlus)}, -DI: ${"%.1f".format(diMinus)})")
                appendLine("  • CCI(20): ${"%.2f".format(cci)}")
                appendLine("  • EMA Trend: $emaTrend")
                appendLine("    (EMA20: ${"%.4f".format(ema20)}${if (ema50 != null) ", EMA50: ${"%.4f".format(ema50)}" else ""}${if (ema200 != null) ", EMA200: ${"%.4f".format(ema200)}" else ""})")
                appendLine("═".repeat(45))
                append("คำแนะนำ: วิเคราะห์จากข้อมูลแท่งเทียนโบรกเกอร์ MT5 โดยตรง เพื่อความแม่นยำควรตรวจสอบ Price Action และแนวรับแนวต้านร่วมด้วย")
            }
        } catch (e: Exception) {
            "MT5 analyze error: ${e.message}"
        }
    }

    internal suspend fun executeMt5SymbolInfo(args: Map<String, String>): String {
        val symbol = args["symbol"]?.trim().orEmpty()
        if (symbol.isBlank()) return "Missing required argument: symbol"

        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/symbol_info"
        val sep = if (endpoint.contains("?")) "&" else "?"
        val url = "$endpoint${sep}symbol=$symbol"

        return try {
            val body = mt5GetWithArgs(url, args)
            val root = json.parseToJsonElement(body).jsonObject
            val data = root["data"]?.jsonObject ?: root

            val bid = data.doubleField("bid")
            val ask = data.doubleField("ask")
            val spread = data.doubleField("spread")
            val digits = data.doubleField("digits")?.toInt() ?: 2
            val point = data.doubleField("point")
            val tradeMode = data.stringField("trade_mode")
            val volumeMin = data.doubleField("volume_min")
            val volumeMax = data.doubleField("volume_max")
            val volumeStep = data.doubleField("volume_step")
            val contractSize = data.doubleField("trade_contract_size")
            val marginInitial = data.doubleField("margin_initial")

            buildString {
                appendLine("ℹ️ MT5 Symbol Info: $symbol")
                appendLine("═".repeat(35))
                if (bid != null) appendLine("  Bid: $bid | Ask: $ask | Spread: $spread pts")
                appendLine("  Digits: $digits | Point: $point")
                appendLine("  Contract Size: $contractSize")
                appendLine("  Volume: Min $volumeMin | Max $volumeMax | Step $volumeStep")
                if (marginInitial != null && marginInitial > 0) appendLine("  Margin Initial: $marginInitial")
                appendLine("  Trade Mode: $tradeMode")
                append("Raw: $body")
            }
        } catch (e: Exception) {
            "MT5 symbol info error: ${e.message}"
        }
    }

    internal suspend fun executeMt5SymbolSearch(args: Map<String, String>): String {
        val pattern = args["pattern"]?.trim().orEmpty()
        val group = args["group"]?.trim().orEmpty()
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/symbols"
        val params = mutableListOf<String>()
        if (pattern.isNotBlank()) params.add("pattern=$pattern")
        if (group.isNotBlank()) params.add("group=$group")
        val sep = if (endpoint.contains("?")) "&" else "?"
        val url = if (params.isNotEmpty()) "$endpoint$sep${params.joinToString("&")}" else endpoint

        return try {
            val body = mt5GetWithArgs(url, args)
            val root = json.parseToJsonElement(body).jsonObject
            val symbols = root["data"]?.jsonArray ?: JsonArray(emptyList())

            if (symbols.isEmpty()) {
                return "MT5 Symbol Search: ไม่พบ symbol ที่ตรงกับเงื่อนไข (pattern='$pattern', group='$group')"
            }

            buildString {
                appendLine("🔍 MT5 Symbols Found (${symbols.size} รายการ):")
                appendLine("═".repeat(40))
                symbols.take(30).forEach { item ->
                    val obj = item.jsonObject
                    val name = obj.stringField("name")
                    val path = obj.stringField("path")
                    val bid = obj.doubleField("bid")
                    val ask = obj.doubleField("ask")
                    val spread = obj.doubleField("spread")
                    val priceInfo = if (bid != null) " | Bid: $bid Ask: $ask (Spread: $spread)" else ""
                    appendLine("  • $name ($path)$priceInfo")
                }
                if (symbols.size > 30) {
                    appendLine("  ... และอีก ${symbols.size - 30} รายการ (แสดงเฉพาะ 30 แรก)")
                }
            }
        } catch (e: Exception) {
            "MT5 symbol search error: ${e.message}"
        }
    }

    internal suspend fun executeMt5CloseAll(args: Map<String, String>): String {
        val symbol = args["symbol"]?.trim().orEmpty()
        val type = args["type"]?.trim()?.uppercase().orEmpty()
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/close_all"
        val token = args["token"]?.trim().orEmpty()

        return try {
            val payload = buildJsonObject {
                if (symbol.isNotBlank()) put("symbol", symbol)
                if (type.isNotBlank()) put("type", type)
            }
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            val ok = response.status.value in 200..299
            val filterDesc = listOfNotNull(
                if (symbol.isNotBlank()) "symbol=$symbol" else null,
                if (type.isNotBlank()) "type=$type" else null
            ).joinToString(", ").ifBlank { "ทั้งหมด" }

            if (ok) {
                "🚨 MT5 Close All สำเร็จ ($filterDesc)\nStatus: ${response.status.value}\nResponse: $body"
            } else {
                "❌ MT5 Close All ล้มเหลว ($filterDesc)\nStatus: ${response.status.value}\nResponse: $body"
            }
        } catch (e: Exception) {
            "MT5 close all error: ${e.message}"
        }
    }

    internal suspend fun executeMt5BreakEvenAll(args: Map<String, String>): String {
        val symbol = args["symbol"]?.trim().orEmpty()
        val bufferPips = args["buffer_pips"]?.toDoubleOrNull() ?: 2.0
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/break_even_all"
        val token = args["token"]?.trim().orEmpty()

        return try {
            val payload = buildJsonObject {
                if (symbol.isNotBlank()) put("symbol", symbol)
                put("buffer_pips", JsonPrimitive(bufferPips))
            }
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            val ok = response.status.value in 200..299
            val target = if (symbol.isNotBlank()) symbol else "ทุกไม้"

            if (ok) {
                "🛡️ MT5 Break-Even สำเร็จ ($target, buffer: $bufferPips pips)\nStatus: ${response.status.value}\nResponse: $body"
            } else {
                "❌ MT5 Break-Even ล้มเหลว ($target)\nStatus: ${response.status.value}\nResponse: $body"
            }
        } catch (e: Exception) {
            "MT5 break even all error: ${e.message}"
        }
    }

    internal suspend fun executeMt5Snapshot(args: Map<String, String>): String {
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5"
        val token = args["token"]?.trim().orEmpty()
        val accountUrl = "$base/account"
        val positionsUrl = "$base/positions"

        return try {
            val accountBody = mt5Get(accountUrl, token)
            val positionsBody = mt5Get(positionsUrl, token)

            val accRoot = json.parseToJsonElement(accountBody).jsonObject
            val accData = accRoot["data"]?.jsonObject ?: accRoot

            val balance = accData.doubleField("balance") ?: 0.0
            val equity = accData.doubleField("equity") ?: 0.0
            val margin = accData.doubleField("margin") ?: 0.0
            val freeMargin = accData.doubleField("margin_free") ?: 0.0
            val marginLevel = accData.doubleField("margin_level") ?: 0.0
            val profit = accData.doubleField("profit") ?: 0.0
            val server = accData.stringField("server")
            val currency = accData.stringField("currency")

            val posRoot = json.parseToJsonElement(positionsBody).jsonObject
            val positions = posRoot["data"]?.jsonArray ?: JsonArray(emptyList())

            val profitSign = if (profit >= 0) "+" else ""
            val profitEmoji = if (profit >= 0) "🟢" else "🔴"

            buildString {
                appendLine("📸 MT5 Account & Positions Snapshot")
                appendLine("═".repeat(45))
                appendLine("🏦 บัญชี: $server | สกุลเงิน: $currency")
                appendLine("💰 Balance: ${"%,.2f".format(balance)} $currency | Equity: ${"%,.2f".format(equity)} $currency")
                appendLine("$profitEmoji Floating P/L: $profitSign${"%,.2f".format(profit)} $currency")
                appendLine("📊 Margin: ${"%,.2f".format(margin)} | Free: ${"%,.2f".format(freeMargin)} | Level: ${"%.1f".format(marginLevel)}%")
                appendLine("═".repeat(45))

                if (positions.isEmpty()) {
                    appendLine("📭 ไม่มี Position เปิดอยู่")
                } else {
                    appendLine("📋 Open Positions (${positions.size} ไม้):")
                    var totalLots = 0.0
                    positions.forEachIndexed { i, p ->
                        val obj = p.jsonObject
                        val sym = obj.stringField("symbol")
                        val type = obj.stringField("type")
                        val vol = obj.doubleField("volume") ?: 0.0
                        val openPrice = obj.doubleField("price_open") ?: 0.0
                        val currentP = obj.doubleField("price_current") ?: 0.0
                        val sl = obj.doubleField("sl") ?: 0.0
                        val tp = obj.doubleField("tp") ?: 0.0
                        val posProfit = obj.doubleField("profit") ?: 0.0
                        val ticket = obj.stringField("ticket")
                        totalLots += vol

                        val pEmoji = if (posProfit >= 0) "🟢" else "🔴"
                        val pSign = if (posProfit >= 0) "+" else ""
                        val slStr = if (sl > 0) "SL: $sl" else "No SL"
                        val tpStr = if (tp > 0) "TP: $tp" else "No TP"

                        appendLine("  ${i + 1}. $pEmoji $type $sym $vol lots @ $openPrice → $currentP")
                        appendLine("     P/L: $pSign${"%,.2f".format(posProfit)} | $slStr | $tpStr | #$ticket")
                    }
                    appendLine("─".repeat(45))
                    appendLine("รวม Lot ทั้งหมด: ${"%.2f".format(totalLots)} lots")
                }
            }
        } catch (e: Exception) {
            "MT5 snapshot error: ${e.message}"
        }
    }

    internal suspend fun executeMt5TradeActions(args: Map<String, String>): String {
        val endpoint = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5/trade_actions"
        val limit = args["limit"]?.toIntOrNull() ?: 20
        val sep = if (endpoint.contains("?")) "&" else "?"
        val url = "$endpoint${sep}limit=$limit"

        return try {
            val body = mt5GetWithArgs(url, args)
            val root = json.parseToJsonElement(body).jsonObject
            val actions = root["data"]?.jsonArray ?: JsonArray(emptyList())

            if (actions.isEmpty()) {
                return "MT5 Trade Actions: ยังไม่มีบันทึก Trade Actions ในระบบ"
            }

            buildString {
                appendLine("📜 MT5 Trade Actions Audit Log (${actions.size} รายการล่าสุด):")
                appendLine("═".repeat(45))
                actions.forEach { item ->
                    val obj = item.jsonObject
                    val time = obj.stringField("timestamp")
                    val action = obj.stringField("action")
                    val symbol = obj.stringField("symbol")
                    val volume = obj.stringField("volume")
                    val status = obj.stringField("status")
                    val detail = obj.stringField("detail")
                    val emoji = if (status == "SUCCESS" || status == "OK") "✅" else "❌"
                    appendLine("  $emoji [$time] $action $symbol $volume lots → $status")
                    if (detail.isNotBlank() && detail != "N/A") appendLine("     Detail: $detail")
                }
            }
        } catch (e: Exception) {
            "MT5 trade actions error: ${e.message}"
        }
    }

    internal suspend fun executeMt5MarketScanner(args: Map<String, String>): String {
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5"
        val token = args["token"]?.trim().orEmpty()
        val symbolsParam = args["symbols"]?.trim().orEmpty()
        val timeframe = TaIndicators.toMt5Timeframe(args["timeframe"] ?: args["interval"] ?: "1h")

        val symbolsList = if (symbolsParam.isNotBlank()) {
            symbolsParam.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            listOf("XAUUSD", "EURUSD", "GBPUSD", "USDJPY", "BTCUSD", "US30", "NAS100")
        }

        return try {
            buildString {
                appendLine("📡 MT5 Market Scanner ($timeframe)")
                appendLine("═".repeat(50))
                appendLine("%-10s %-10s %-8s %-12s %s".format("Symbol", "Price", "RSI(14)", "MACD Hist", "Signal"))
                appendLine("─".repeat(50))

                for (sym in symbolsList) {
                    try {
                        val url = "$base/candles?symbol=$sym&timeframe=$timeframe&count=50"
                        val body = mt5Get(url, token)
                        val root = json.parseToJsonElement(body).jsonObject
                        val candles = root["data"]?.jsonArray ?: continue
                        if (candles.size < 35) continue

                        val closes = candles.mapNotNull { it.jsonObject["c"]?.jsonPrimitive?.doubleOrNull }
                        if (closes.size < 35) continue

                        val curPrice = closes.last()
                        val rsi = TaIndicators.rsi(closes, 14) ?: 50.0
                        val macdHist = TaIndicators.macd(closes)?.hist ?: 0.0

                        val sig = when {
                            rsi < 30.0 && macdHist > 0 -> "BUY 🟢"
                            rsi > 70.0 && macdHist < 0 -> "SELL 🔴"
                            rsi < 35.0 -> "OVERSOLD 🟡"
                            rsi > 65.0 -> "OVERBOUGHT 🟠"
                            macdHist > 0 -> "BULLISH 🟢"
                            else -> "BEARISH 🔴"
                        }

                        appendLine("%-10s %-10.4f %-8.1f %-12.4f %s".format(sym, curPrice, rsi, macdHist, sig))
                    } catch (_: Exception) {
                        appendLine("%-10s [Error fetching data]".format(sym))
                    }
                }
                appendLine("═".repeat(50))
                append("สแกนจากแท่งเทียนโบรกเกอร์ MT5 โดยตรง เพื่อหาจังหวะเทรดที่ดีที่สุด")
            }
        } catch (e: Exception) {
            "MT5 market scanner error: ${e.message}"
        }
    }

    internal suspend fun executeMt5CorrelationRadar(args: Map<String, String>): String {
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5"
        val token = args["token"]?.trim().orEmpty()
        val symbolsParam = args["symbols"]?.trim().orEmpty()
        val timeframe = TaIndicators.toMt5Timeframe(args["timeframe"] ?: args["interval"] ?: "1h")
        val count = args["count"]?.toIntOrNull() ?: 50

        val symbolsList = if (symbolsParam.isNotBlank()) {
            symbolsParam.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            listOf("XAUUSD", "EURUSD", "GBPUSD", "USDJPY", "BTCUSD")
        }

        if (symbolsList.size < 2) return "MT5 Correlation Radar: ต้องการอย่างน้อย 2 symbols"

        return try {
            val symbolCloses = mutableMapOf<String, List<Double>>()
            for (sym in symbolsList) {
                try {
                    val url = "$base/candles?symbol=$sym&timeframe=$timeframe&count=$count"
                    val body = mt5Get(url, token)
                    val root = json.parseToJsonElement(body).jsonObject
                    val candles = root["data"]?.jsonArray ?: continue
                    val closes = candles.mapNotNull { it.jsonObject["c"]?.jsonPrimitive?.doubleOrNull }
                    if (closes.size >= 20) {
                        symbolCloses[sym] = closes
                    }
                } catch (_: Exception) { /* skip */ }
            }

            if (symbolCloses.size < 2) {
                return "MT5 Correlation Radar: ดึงข้อมูลแท่งเทียนได้ไม่พอสำหรับคำนวณ (สำเร็จ ${symbolCloses.size} symbols)"
            }

            fun pearson(x: List<Double>, y: List<Double>): Double {
                val n = minOf(x.size, y.size)
                if (n < 5) return 0.0
                val xSub = x.takeLast(n)
                val ySub = y.takeLast(n)
                val xMean = xSub.average()
                val yMean = ySub.average()
                var num = 0.0
                var denX = 0.0
                var denY = 0.0
                for (i in 0 until n) {
                    val dx = xSub[i] - xMean
                    val dy = ySub[i] - yMean
                    num += dx * dy
                    denX += dx * dx
                    denY += dy * dy
                }
                val den = kotlin.math.sqrt(denX * denY)
                return if (den == 0.0) 0.0 else (num / den).coerceIn(-1.0, 1.0)
            }

            val validSymbols = symbolCloses.keys.toList()
            buildString {
                appendLine("📡 MT5 Correlation Radar ($timeframe, $count bars)")
                appendLine("═".repeat(45))
                appendLine("ค่า Correlation: +1.0 (วิ่งทิศเดียวกัน), -1.0 (วิ่งสวนทาง), 0.0 (ไม่เกี่ยวข้องกัน)")
                appendLine("─".repeat(45))

                for (i in 0 until validSymbols.size) {
                    for (j in i + 1 until validSymbols.size) {
                        val s1 = validSymbols[i]
                        val s2 = validSymbols[j]
                        val corr = pearson(symbolCloses[s1]!!, symbolCloses[s2]!!)
                        val desc = when {
                            corr >= 0.8  -> "วิ่งตามกันชัดเจนมาก 🟢🟢"
                            corr >= 0.5  -> "วิ่งตามกันปานกลาง 🟢"
                            corr in -0.3..0.3 -> "ไม่สัมพันธ์กัน (กระจายความเสี่ยงได้ดี) ⚪"
                            corr <= -0.8 -> "วิ่งสวนทางกันชัดเจนมาก (Hedge ได้ดี) 🔴🔴"
                            corr <= -0.5 -> "วิ่งสวนทางกันปานกลาง 🔴"
                            else -> "สัมพันธ์ต่ำ"
                        }
                        appendLine("  • %s vs %s: %+.2f → %s".format(s1, s2, corr, desc))
                    }
                }
                appendLine("═".repeat(45))
                append("ประโยชน์: ใช้เลี่ยงการเปิดไม้ซ้ำซ้อนในคู่เงินที่วิ่งตามกัน หรือใช้ Hedging")
            }
        } catch (e: Exception) {
            "MT5 correlation radar error: ${e.message}"
        }
    }

    internal suspend fun executeMt5SentimentGauge(args: Map<String, String>): String {
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5"
        val token = args["token"]?.trim().orEmpty()

        return try {
            val body = mt5Get("$base/positions", token)
            val root = json.parseToJsonElement(body).jsonObject
            val positions = root["data"]?.jsonArray ?: JsonArray(emptyList())

            if (positions.isEmpty()) {
                return "MT5 Sentiment Gauge: ไม่มี position เปิดอยู่ ไม่สามารถคำนวณ sentiment จากพอร์ตได้"
            }

            var buyCount = 0
            var sellCount = 0
            var buyVolume = 0.0
            var sellVolume = 0.0
            val symbolBreakdown = mutableMapOf<String, Pair<Double, Double>>()

            positions.forEach { p ->
                val obj = p.jsonObject
                val type = obj.stringField("type").uppercase()
                val vol = obj.doubleField("volume") ?: 0.0
                val sym = obj.stringField("symbol")

                val current = symbolBreakdown.getOrElse(sym) { Pair(0.0, 0.0) }
                if (type.contains("BUY")) {
                    buyCount++
                    buyVolume += vol
                    symbolBreakdown[sym] = Pair(current.first + vol, current.second)
                } else if (type.contains("SELL")) {
                    sellCount++
                    sellVolume += vol
                    symbolBreakdown[sym] = Pair(current.first, current.second + vol)
                }
            }

            val totalVolume = buyVolume + sellVolume
            val buyPct = if (totalVolume > 0) (buyVolume / totalVolume * 100) else 50.0
            val sellPct = 100.0 - buyPct

            val gaugeEmoji = when {
                buyPct >= 70 -> "🟢🟢 EXTREME BULLISH"
                buyPct >= 55 -> "🟢 BULLISH BIAS"
                buyPct in 45.0..55.0 -> "⚪ BALANCED / NEUTRAL"
                buyPct <= 30 -> "🔴🔴 EXTREME BEARISH"
                else -> "🔴 BEARISH BIAS"
            }

            buildString {
                appendLine("🧭 MT5 Portfolio Sentiment Gauge")
                appendLine("═".repeat(45))
                appendLine("ทิศทางรวมของพอร์ต: $gaugeEmoji")
                appendLine("─".repeat(45))
                appendLine("  BUY Volume : ${"%.2f".format(buyVolume)} lots (${"%.1f".format(buyPct)}%) [$buyCount ไม้]")
                appendLine("  SELL Volume: ${"%.2f".format(sellVolume)} lots (${"%.1f".format(sellPct)}%) [$sellCount ไม้]")
                appendLine("  Volume รวม : ${"%.2f".format(totalVolume)} lots")
                appendLine("─".repeat(45))
                appendLine("แยกตาม Symbol:")
                symbolBreakdown.forEach { (sym, vols) ->
                    val (b, s) = vols
                    val tot = b + s
                    val bP = if (tot > 0) b / tot * 100 else 50.0
                    val sP = 100.0 - bP
                    appendLine("  • %-10s : BUY ${"%.2f".format(b)} (${"%.0f".format(bP)}%) | SELL ${"%.2f".format(s)} (${"%.0f".format(sP)}%)".format(sym))
                }
                appendLine("═".repeat(45))
                append("คำเตือน: นี่คือ Sentiment จากพอร์ตปัจจุบันของคุณ ไม่ใช่ Sentiment ของตลาดโลก")
            }
        } catch (e: Exception) {
            "MT5 sentiment gauge error: ${e.message}"
        }
    }

    internal suspend fun executeMt5InstitutionalFlow(args: Map<String, String>): String {
        val symbol = args["symbol"]?.trim().orEmpty().ifBlank { "XAUUSD" }
        val timeframe = TaIndicators.toMt5Timeframe(args["timeframe"] ?: args["interval"] ?: "1h")
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5"
        val token = args["token"]?.trim().orEmpty()

        return try {
            val url = "$base/candles?symbol=$symbol&timeframe=$timeframe&count=100"
            val body = mt5Get(url, token)
            val root = json.parseToJsonElement(body).jsonObject
            val candles = root["data"]?.jsonArray ?: JsonArray(emptyList())

            if (candles.size < 30) {
                return "MT5 Institutional Flow: ข้อมูลแท่งเทียนไม่เพียงพอ ($symbol $timeframe, ได้ ${candles.size} bars)"
            }

            var buyVolume = 0.0
            var sellVolume = 0.0
            var highVolumeNodes = 0
            val volumes = candles.mapNotNull { it.jsonObject["v"]?.jsonPrimitive?.doubleOrNull }
            val avgVol = if (volumes.isNotEmpty()) volumes.average() else 1.0

            candles.forEach { c ->
                val obj = c.jsonObject
                val o = obj.doubleField("o") ?: 0.0
                val cl = obj.doubleField("c") ?: 0.0
                val v = obj.doubleField("v") ?: 0.0

                if (v > avgVol * 1.5) highVolumeNodes++
                if (cl >= o) buyVolume += v else sellVolume += v
            }

            val totalVol = buyVolume + sellVolume
            val delta = buyVolume - sellVolume
            val deltaPct = if (totalVol > 0) (delta / totalVol * 100) else 0.0

            val flowDesc = when {
                deltaPct > 20.0 -> "สถาบันเข้าซื้อชัดเจน (Strong Institutional Inflow) 🟢🟢"
                deltaPct > 5.0  -> "แรงซื้อได้เปรียบเล็กน้อย (Mild Accumulation) 🟢"
                deltaPct in -5.0..5.0 -> "แรงซื้อ-ขายสมดุล (Absorption / Distribution) ⚪"
                deltaPct < -20.0 -> "สถาบันเทขายชัดเจน (Strong Institutional Outflow) 🔴🔴"
                else -> "แรงขายได้เปรียบเล็กน้อย (Mild Distribution) 🔴"
            }

            buildString {
                appendLine("🏦 MT5 Institutional Volume Flow")
                appendLine("Symbol: $symbol | TF: $timeframe | Bars: ${candles.size}")
                appendLine("═".repeat(45))
                appendLine("สถานะ Flow: $flowDesc")
                appendLine("─".repeat(45))
                appendLine("  • Volume ซื้อสะสม : ${"%,.0f".format(buyVolume)}")
                appendLine("  • Volume ขายสะสม : ${"%,.0f".format(sellVolume)}")
                appendLine("  • Cumulative Delta : %s${"%,.0f".format(delta)} (${"%+.1f".format(deltaPct)}%)".format(if (delta >= 0) "+" else ""))
                appendLine("  • แท่งเทียน Volume ผิดปกติ (>1.5x ค่าเฉลี่ย): $highVolumeNodes bars")
                appendLine("═".repeat(45))
                append("วิเคราะห์จาก Tick Volume ของแท่งเทียน MT5 ย้อนหลัง ${candles.size} bars")
            }
        } catch (e: Exception) {
            "MT5 institutional flow error: ${e.message}"
        }
    }

    internal suspend fun executeMt5TradeJournal(args: Map<String, String>): String {
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5"
        val token = args["token"]?.trim().orEmpty()
        val days = args["days"]?.toIntOrNull() ?: 30

        return try {
            val url = "$base/history?days=$days"
            val body = mt5Get(url, token)
            val root = json.parseToJsonElement(body).jsonObject
            val trades = root["data"]?.jsonArray ?: JsonArray(emptyList())

            if (trades.isEmpty()) {
                return "MT5 Trade Journal: ไม่พบประวัติการเทรดในช่วง $days วันที่ผ่านมา"
            }

            var totalTrades = 0
            var winTrades = 0
            var lossTrades = 0
            var totalProfit = 0.0
            var totalGrossProfit = 0.0
            var totalGrossLoss = 0.0
            val symbolStats = mutableMapOf<String, Triple<Int, Double, Int>>()

            trades.forEach { t ->
                val obj = t.jsonObject
                val profit = obj.doubleField("profit") ?: 0.0
                val sym = obj.stringField("symbol")
                val entry = obj.stringField("entry")
                if (entry == "OUT" || entry == "1" || profit != 0.0) {
                    totalTrades++
                    totalProfit += profit
                    if (profit > 0) {
                        winTrades++
                        totalGrossProfit += profit
                    } else if (profit < 0) {
                        lossTrades++
                        totalGrossLoss += kotlin.math.abs(profit)
                    }
                    val curr = symbolStats.getOrElse(sym) { Triple(0, 0.0, 0) }
                    symbolStats[sym] = Triple(
                        curr.first + 1,
                        curr.second + profit,
                        if (profit > 0) curr.third + 1 else curr.third
                    )
                }
            }

            if (totalTrades == 0) totalTrades = trades.size
            val winRate = if (totalTrades > 0) (winTrades.toDouble() / totalTrades * 100) else 0.0
            val profitFactor = if (totalGrossLoss > 0) (totalGrossProfit / totalGrossLoss) else if (totalGrossProfit > 0) 99.9 else 0.0
            val pSign = if (totalProfit >= 0) "+" else ""

            buildString {
                appendLine("📖 MT5 Trade Journal & Performance")
                appendLine("ช่วงเวลา: $days วันที่ผ่านมา | จำนวนเทรดทั้งหมด: $totalTrades ไม้")
                appendLine("═".repeat(45))
                appendLine("💰 กำไร/ขาดทุนสุทธิ: $pSign${"%,.2f".format(totalProfit)}")
                appendLine("🎯 Win Rate : ${"%.1f".format(winRate)}% (ชนะ $winTrades / แพ้ $lossTrades)")
                appendLine("⚖️ Profit Factor : ${"%.2f".format(profitFactor)}")
                appendLine("  • กำไรรวม : +${"%,.2f".format(totalGrossProfit)}")
                appendLine("  • ขาดทุนรวม: -${"%,.2f".format(totalGrossLoss)}")
                appendLine("─".repeat(45))
                appendLine("ประสิทธิภาพแยกตาม Symbol:")
                symbolStats.forEach { (sym, stat) ->
                    val (cnt, pnl, wins) = stat
                    val wr = if (cnt > 0) (wins.toDouble() / cnt * 100) else 0.0
                    val sP = if (pnl >= 0) "+" else ""
                    appendLine("  • %-10s: %2d trades | P/L: %s%8.2f | WR: %4.1f%%".format(sym, cnt, sP, pnl, wr))
                }
                appendLine("═".repeat(45))
                append("สรุปสถิติจากประวัติ Order จริงในบัญชี MT5")
            }
        } catch (e: Exception) {
            "MT5 trade journal error: ${e.message}"
        }
    }

    internal suspend fun executeMt5EconomicRadar(args: Map<String, String>): String {
        val base = args["endpoint"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: "http://127.0.0.1:8090/api/mt5"
        val token = args["token"]?.trim().orEmpty()
        return try {
            val account = mt5Get("$base/account", token)
            val positions = mt5Get("$base/positions", token)
            buildString {
                appendLine("MT5 Economic Radar")
                appendLine("Account: $account")
                appendLine("Positions: $positions")
            }
        } catch (e: Exception) {
            "Economic radar error: ${e.message}"
        }
    }
}

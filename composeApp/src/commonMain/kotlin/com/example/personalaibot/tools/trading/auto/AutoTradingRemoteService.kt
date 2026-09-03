package com.example.personalaibot.tools.trading.auto

import io.ktor.client.HttpClient
import com.example.personalaibot.tools.trading.Mt5AccountInfo
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull




class AutoTradingRemoteService(
    private val client: HttpClient,
    private val bridgeBaseUrlProvider: () -> String,
    private val authTokenProvider: () -> String,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    data class Snapshot(
        val config: AutoTradingEngine.Config,
        val state: AutoTradingEngine.EngineState,
        val lastDecisions: List<AutoTradingEngine.CycleDecision>,
        val openJournal: List<TradeJournalStore.JournalRow>,
        val learnSummary: AutoTradingEngine.LearnSummary?,
    )

    suspend fun fetchSnapshot(): Snapshot = request("/snapshot")
    suspend fun fetchDecisionFeed(limit: Int = 100): List<AutoTradingEngine.CycleDecision> {
        val token = authTokenProvider().trim()
        val url = "${normalizeAutoBase(bridgeBaseUrlProvider())}/decision-feed?limit=$limit"
        val response = client.get(url) { header("X-Client-Token", token) }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw Exception("Failed to fetch decision feed: $text")
        val root = json.parseToJsonElement(text).jsonObject
        val data = root["data"]?.jsonArray ?: return emptyList()
        return parseDecisions(data)
    }
    suspend fun start(): Snapshot = request("/start", method = "POST")
    suspend fun stop(): Snapshot = request("/stop", method = "POST")
    suspend fun runOnce(): Snapshot = request("/run-once", method = "POST")
    suspend fun learn(): Snapshot = request("/learn", method = "POST")

    suspend fun updateConfig(config: AutoTradingEngine.Config): Snapshot {
        val payload = buildConfigPayload(config)
        return request("/config", method = "POST", body = payload)
    }

    private suspend fun request(path: String, method: String = "GET", body: String? = null): Snapshot {
        val token = authTokenProvider().trim()
        if (token.isBlank()) throw Exception("MT5 auth token is empty")
        val url = "${normalizeAutoBase(bridgeBaseUrlProvider())}$path"

        val response = try {
            when (method) {
                "POST" -> client.post(url) {
                    header("X-Client-Token", token)
                    if (!body.isNullOrBlank()) {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                }
                else -> client.get(url) {
                    header("X-Client-Token", token)
                }
            }
        } catch (e: Exception) {
            throw Exception("Server unreachable: ${e.message}")
        }

        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw Exception("Auto trading request failed (${response.status.value}): $text")
        }
        return parseSnapshot(text)
    }

    private fun parseSnapshot(raw: String): Snapshot {
        val root = json.parseToJsonElement(raw).jsonObject
        val data = root["data"]?.jsonObject ?: error("Missing data in auto trading snapshot")
        return Snapshot(
            config = parseConfig(data["config"]?.jsonObject),
            state = parseState(data["state"]?.jsonObject),
            lastDecisions = parseDecisions(data["lastDecisions"]?.jsonArray ?: JsonArray(emptyList())),
            openJournal = parseOpenJournal(data["openJournal"]?.jsonArray ?: JsonArray(emptyList())),
            learnSummary = parseLearnSummary(data["learnSummary"])
        )
    }

    /**
     * Builds the remote trading configuration as a typed JSON object.
     *
     * Security boundary: AI API keys are local-only credentials and MUST NOT be
     * included in the remote trading configuration payload.
     */
    private fun buildConfigPayload(config: AutoTradingEngine.Config): String {
        val payload = buildJsonObject {
            put("watchlist", buildJsonArray { config.watchlist.forEach { add(JsonPrimitive(it)) } })
            put("timeframe", config.timeframe)
            put("tickIntervalMs", config.tickIntervalMs)
            put("manageIntervalMs", config.manageIntervalMs)
            put("learnIntervalMs", config.learnIntervalMs)
            put("minConfluence", config.minConfluence)
            put("minFitness", config.minFitness)
            put("minRRR", config.minRRR)
            put("enableLiveTrading", config.enableLiveTrading)
            put("enableAiMode", config.enableAiMode)
            put("preferredStrategies", buildJsonArray {
                config.preferredStrategies.forEach { add(JsonPrimitive(it.name)) }
            })
            put("symbolBlacklist", buildJsonArray {
                config.symbolBlacklist.forEach { add(JsonPrimitive(it)) }
            })
            put("breakEvenTriggerR", config.breakEvenTriggerR)
            put("trailAfterR", config.trailAfterR)
            put("autoTune", config.autoTune)
            put("autoBlacklistAfterLosses", config.autoBlacklistAfterLosses)
            put("risk", buildJsonObject {
                put("riskPerTradePct", config.risk.riskPerTradePct)
                put("maxTotalExposurePct", config.risk.maxTotalExposurePct)
                put("maxOpenPositions", config.risk.maxOpenPositions)
                put("maxDailyLossPct", config.risk.maxDailyLossPct)
                put("maxDrawdownPct", config.risk.maxDrawdownPct)
                put("minFreeMarginPct", config.risk.minFreeMarginPct)
                put("correlationCap", config.risk.correlationCap)
                put("minLotStep", config.risk.minLotStep)
                put("maxLot", config.risk.maxLot)
                put("minLot", config.risk.minLot)
                put("pointValueOverride", buildJsonObject {
                    config.risk.pointValueOverride.forEach { (symbol, value) -> put(symbol, value) }
                })
            })
            put("strategy", buildJsonObject {
                put("minRRR", config.strategy.minRRR)
                put("minConfluence", config.strategy.minConfluence)
                put("scalpingRRR", config.strategy.scalpingRRR)
                put("swingRRR", config.strategy.swingRRR)
                put("gridLegs", config.strategy.gridLegs)
                put("gridStepPct", config.strategy.gridStepPct)
                put("trailingAtrMult", config.strategy.trailingAtrMult)
                put("breakoutBufferPct", config.strategy.breakoutBufferPct)
            })
            put("aiModel", config.aiModel)
            // apiKey intentionally omitted: it is a local-only secret.
            put("agentPrompt", config.agentPrompt)
            put("preferFreeOnly", config.preferFreeOnly)
            put("notificationSettings", buildJsonObject {
                put("onAnalysis", config.notificationSettings.onAnalysis)
                put("onOrder", config.notificationSettings.onOrder)
                put("onLoss", config.notificationSettings.onLoss)
                put("onClose", config.notificationSettings.onClose)
            })
        }
        return payload.toString()
    }

    private fun parseConfig(obj: JsonObject?): AutoTradingEngine.Config {
        val defaults = AutoTradingEngine.Config()
        val risk = obj?.get("risk")?.asObject()
        val strategy = obj?.get("strategy")?.asObject()
        return defaults.copy(
            watchlist = obj.stringList("watchlist", defaults.watchlist),
            timeframe = obj.string("timeframe", defaults.timeframe),
            tickIntervalMs = obj.long("tickIntervalMs", defaults.tickIntervalMs),
            manageIntervalMs = obj.long("manageIntervalMs", defaults.manageIntervalMs),
            learnIntervalMs = obj.long("learnIntervalMs", defaults.learnIntervalMs),
            minConfluence = obj.double("minConfluence", defaults.minConfluence),
            minFitness = obj.double("minFitness", defaults.minFitness),
            minRRR = obj.double("minRRR", defaults.minRRR),
            enableLiveTrading = obj.boolean("enableLiveTrading", defaults.enableLiveTrading),
            enableAiMode = obj.boolean("enableAiMode", defaults.enableAiMode),
            preferredStrategies = obj.stringList("preferredStrategies", emptyList()).mapNotNull { enumOrNull<StrategyType>(it) }.toSet(),
            symbolBlacklist = obj.stringList("symbolBlacklist", defaults.symbolBlacklist.toList()).toSet(),
            breakEvenTriggerR = obj.double("breakEvenTriggerR", defaults.breakEvenTriggerR),
            trailAfterR = obj.double("trailAfterR", defaults.trailAfterR),
            autoTune = obj.boolean("autoTune", defaults.autoTune),
            autoBlacklistAfterLosses = obj.int("autoBlacklistAfterLosses", defaults.autoBlacklistAfterLosses),
            risk = AutoTradingEngine.RiskConfig(
                riskPerTradePct = risk.double("riskPerTradePct", defaults.risk.riskPerTradePct),
                maxTotalExposurePct = risk.double("maxTotalExposurePct", defaults.risk.maxTotalExposurePct),
                maxOpenPositions = risk.int("maxOpenPositions", defaults.risk.maxOpenPositions),
                maxDailyLossPct = risk.double("maxDailyLossPct", defaults.risk.maxDailyLossPct),
                maxDrawdownPct = risk.double("maxDrawdownPct", defaults.risk.maxDrawdownPct),
                minFreeMarginPct = risk.double("minFreeMarginPct", defaults.risk.minFreeMarginPct),
                correlationCap = risk.int("correlationCap", defaults.risk.correlationCap),
                minLotStep = risk.double("minLotStep", defaults.risk.minLotStep),
                maxLot = risk.double("maxLot", defaults.risk.maxLot),
                minLot = risk.double("minLot", defaults.risk.minLot),
                pointValueOverride = risk.mapStringDouble("pointValueOverride", defaults.risk.pointValueOverride)
            ),
            strategy = AutoTradingEngine.StrategyConfig(
                minRRR = strategy.double("minRRR", defaults.strategy.minRRR),
                minConfluence = strategy.double("minConfluence", defaults.strategy.minConfluence),
                scalpingRRR = strategy.double("scalpingRRR", defaults.strategy.scalpingRRR),
                swingRRR = strategy.double("swingRRR", defaults.strategy.swingRRR),
                gridLegs = strategy.int("gridLegs", defaults.strategy.gridLegs),
                gridStepPct = strategy.double("gridStepPct", defaults.strategy.gridStepPct),
                trailingAtrMult = strategy.double("trailingAtrMult", defaults.strategy.trailingAtrMult),
                breakoutBufferPct = strategy.double("breakoutBufferPct", defaults.strategy.breakoutBufferPct)
            ),
            aiModel = obj.string("aiModel", defaults.aiModel),
            // API keys are intentionally not read from the remote service.
            apiKey = defaults.apiKey,
            agentPrompt = obj.string("agentPrompt", defaults.agentPrompt),
            preferFreeOnly = obj.boolean("preferFreeOnly", defaults.preferFreeOnly),
            notificationSettings = run {
                val n = obj?.get("notificationSettings")?.asObject()
                AutoTradingEngine.NotificationConfig(
                    onAnalysis = n.boolean("onAnalysis", defaults.notificationSettings.onAnalysis),
                    onOrder = n.boolean("onOrder", defaults.notificationSettings.onOrder),
                    onLoss = n.boolean("onLoss", defaults.notificationSettings.onLoss),
                    onClose = n.boolean("onClose", defaults.notificationSettings.onClose)
                )
            }
        )
    }

    private fun parseState(obj: JsonObject?): AutoTradingEngine.EngineState {
        val defaults = AutoTradingEngine.EngineState()
        return defaults.copy(
            running = obj.boolean("running", defaults.running),
            phase = enumOrNull<AutoTradingEngine.Phase>(obj.string("phase", defaults.phase.name)) ?: defaults.phase,
            lastTickAt = obj.stringOrNull("lastTickAt"),
            cycleCount = obj.int("cycleCount", defaults.cycleCount),
            openTradesTracked = obj.int("openTradesTracked", defaults.openTradesTracked),
            message = obj.string("message", defaults.message)
        )
    }

    private fun parseDecisions(arr: JsonArray): List<AutoTradingEngine.CycleDecision> =
        arr.mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val side = obj.string("side", "SKIP")
            AutoTradingEngine.CycleDecision(
                symbol = obj.string("symbol", ""),
                regime = enumOrNull<MarketRegime>(obj.string("regime", MarketRegime.UNKNOWN.name)) ?: MarketRegime.UNKNOWN,
                overallBias = enumOrNull<Bias>(obj.string("overallBias", Bias.NEUTRAL.name)) ?: Bias.NEUTRAL,
                confluence = obj.double("confluence", 0.0),
                strategy = enumOrNull<StrategyType>(obj.string("strategy", StrategyType.HOLD_CASH.name)) ?: StrategyType.HOLD_CASH,
                side = if (side == "BUY" || side == "SELL") side else "SKIP",
                entry = obj.doubleOrNull("entry"),
                sl = obj.doubleOrNull("sl"),
                tp = obj.doubleOrNull("tp"),
                volume = obj.doubleOrNull("volume"),
                rrr = obj.double("rrr", 0.0),
                rationale = obj.string("rationale", ""),
                analyzersUsed = obj.stringList("analyzersUsed", emptyList()).mapNotNull { enumOrNull<AnalyzerKind>(it) },
                riskGate = obj.string("riskGate", "-"),
                executed = obj.boolean("executed", false),
                mt5Ticket = obj.longOrNull("mt5Ticket"),
                at = obj.string("at", ""),
                decisionId = obj.string("decisionId", ""),
                translatedTh = obj.stringOrNull("translatedTh")
            )
        }

    private fun parseOpenJournal(arr: JsonArray): List<TradeJournalStore.JournalRow> =
        arr.mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            TradeJournalStore.JournalRow(
                id = obj.long("id", 0),
                decisionId = obj.string("decisionId", ""),
                symbol = obj.string("symbol", ""),
                timeframe = obj.string("timeframe", ""),
                side = obj.string("side", ""),
                strategy = obj.string("strategy", ""),
                analyzersUsed = obj.string("analyzersUsed", ""),
                signalsJson = obj.string("signalsJson", "{}"),
                confluenceScore = obj.double("confluenceScore", 0.0),
                entry = obj.doubleOrNull("entry"),
                sl = obj.doubleOrNull("sl"),
                tp = obj.doubleOrNull("tp"),
                volume = obj.doubleOrNull("volume"),
                riskPct = obj.doubleOrNull("riskPct"),
                rrr = obj.doubleOrNull("rrr"),
                regime = obj.stringOrNull("regime"),
                marketSnapshot = obj.stringOrNull("marketSnapshot"),
                wasExecuted = obj.boolean("wasExecuted", false),
                mt5Ticket = obj.longOrNull("mt5Ticket"),
                closeReason = obj.stringOrNull("closeReason"),
                closePrice = obj.doubleOrNull("closePrice"),
                closeAt = obj.longOrNull("closeAt"),
                profit = obj.doubleOrNull("profit"),
                profitR = obj.doubleOrNull("profitR"),
                outcome = obj.string("outcome", ""),
                aiReview = obj.stringOrNull("aiReview"),
                createdAt = obj.long("createdAt", 0),
                updatedAt = obj.long("updatedAt", 0)
            )
        }

    private fun parseLearnSummary(element: JsonElement?): AutoTradingEngine.LearnSummary? {
        val obj = element.asObject() ?: return null
        val strategyStats = obj["strategyStats"]?.jsonArray?.mapNotNull { row ->
            val bag = row.asObject() ?: return@mapNotNull null
            TradeJournalStore.StrategyStat(
                strategy = bag.string("strategy", ""),
                total = bag.int("total", 0),
                wins = bag.int("wins", 0),
                losses = bag.int("losses", 0),
                winRate = bag.double("winRate", 0.0),
                totalProfit = bag.double("totalProfit", 0.0),
                avgR = bag.double("avgR", 0.0)
            )
        } ?: emptyList()
        val analyzerStats = obj["analyzerStats"]?.jsonArray?.mapNotNull { row ->
            val bag = row.asObject() ?: return@mapNotNull null
            TradeJournalStore.AnalyzerStat(
                analyzers = bag.string("analyzers", ""),
                total = bag.int("total", 0),
                wins = bag.int("wins", 0),
                winRate = bag.double("winRate", 0.0),
                totalProfit = bag.double("totalProfit", 0.0)
            )
        } ?: emptyList()
        return AutoTradingEngine.LearnSummary(
            totalDeals = obj.int("totalDeals", 0),
            wins = obj.int("wins", 0),
            losses = obj.int("losses", 0),
            winRate = obj.double("winRate", 0.0),
            totalProfit = obj.double("totalProfit", 0.0),
            bestStrategy = obj.stringOrNull("bestStrategy"),
            worstStrategy = obj.stringOrNull("worstStrategy"),
            bestAnalyzerCombo = obj.stringOrNull("bestAnalyzerCombo"),
            worstAnalyzerCombo = obj.stringOrNull("worstAnalyzerCombo"),
            strategyStats = strategyStats,
            analyzerStats = analyzerStats,
            aiNote = obj.string("aiNote", ""),
            atIso = obj.string("atIso", "")
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(value: String?): T? =
        runCatching { enumValueOf<T>(value?.trim().orEmpty()) }.getOrNull()

    private fun normalizeAutoBase(raw: String): String {
        val mt5Base = normalizeMt5ApiBase(raw)
        return if (mt5Base.endsWith("/auto")) mt5Base else "$mt5Base/auto"
    }

    private fun normalizeMt5ApiBase(raw: String): String {
        val url = raw.trim().trimEnd('/')
        return when {
            url.endsWith("/api/mt5", ignoreCase = true) -> url
            url.endsWith("/api", ignoreCase = true) -> "$url/mt5"
            else -> "$url/api/mt5"
        }
    }

    /** P6.3 — live MT5 account snapshot for the Mobile execution status card. */
    suspend fun fetchMt5Account(): Mt5AccountInfo {
        val token = authTokenProvider().trim()
        if (token.isBlank()) throw Exception("MT5 auth token is empty")
        val url = "${normalizeMt5ApiBase(bridgeBaseUrlProvider())}/account"
        val response = client.get(url) { header("X-Client-Token", token) }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw Exception("Failed to fetch MT5 account: $text")
        val root = json.parseToJsonElement(text).jsonObject
        val data = root["data"]?.jsonObject ?: root
        return Mt5AccountInfo(
            login = data["login"]?.jsonPrimitive?.contentOrNull ?: data["account"]?.jsonPrimitive?.contentOrNull ?: "",
            accountName = data["name"]?.jsonPrimitive?.contentOrNull ?: data["accountName"]?.jsonPrimitive?.contentOrNull ?: "",
            server = data["server"]?.jsonPrimitive?.contentOrNull ?: "",
            currency = data["currency"]?.jsonPrimitive?.contentOrNull ?: "USD",
            leverage = data["leverage"]?.jsonPrimitive?.intOrNull ?: 0,
            balance = data["balance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            equity = data["equity"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            margin = data["margin"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            freeMargin = data["freeMargin"]?.jsonPrimitive?.doubleOrNull ?: data["free_margin"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
        )
    }

    // ─── P4.2 — Quality Metrics fetch (consumes /metrics/quality JSON) ─────
    suspend fun fetchQualityMetrics(): QualityMetrics {
        val token = authTokenProvider().trim()
        val url = "${normalizeAutoBase(bridgeBaseUrlProvider())}/metrics/quality"
        val response = client.get(url) { header("X-Client-Token", token) }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw Exception("Failed to fetch quality metrics: $text")
        val root = json.parseToJsonElement(text).jsonObject
        val data = root["data"]?.jsonObject ?: return QualityMetrics()
        val summary = data["summary"]?.jsonObject
        val breakdown = data["breakdown"]?.jsonObject

        fun JsonObject?.intMap(): Map<String, Int> =
            this?.entries?.associate { (k, v) -> k to ((v as? JsonPrimitive)?.intOrNull ?: 0) } ?: emptyMap()

        return QualityMetrics(
            generatedAt = data["generatedAt"]?.jsonPrimitive?.content ?: "",
            zoneGateBlocks = (summary?.get("zoneGateBlocks") as? JsonPrimitive)?.intOrNull ?: 0,
            slBufferActivations = (summary?.get("slBufferActivations") as? JsonPrimitive)?.intOrNull ?: 0,
            counterTrendBlocks = (summary?.get("counterTrendBlocks") as? JsonPrimitive)?.intOrNull ?: 0,
            managementEvents = (summary?.get("managementEvents") as? JsonPrimitive)?.intOrNull ?: 0,
            entryZoneTotal = (summary?.get("entryZoneTotal") as? JsonPrimitive)?.intOrNull ?: 0,
            tradesPlaced = (summary?.get("tradesPlaced") as? JsonPrimitive)?.intOrNull ?: 0,
            tradesRejected = (summary?.get("tradesRejected") as? JsonPrimitive)?.intOrNull ?: 0,
            zoneByZone = (breakdown?.get("zoneGateByZone") as? JsonObject).intMap(),
            zoneBySide = (breakdown?.get("zoneGateBySide") as? JsonObject).intMap(),
            slBufferByTrigger = (breakdown?.get("slBufferByTrigger") as? JsonObject).intMap(),
            ctBySide = (breakdown?.get("counterTrendBySide") as? JsonObject).intMap(),
            mgmtByEvent = (breakdown?.get("managementByEvent") as? JsonObject).intMap(),
            entryZoneDistribution = (breakdown?.get("entryZoneDistribution") as? JsonObject).intMap(),
        )
    }

}

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonObject?.string(key: String, fallback: String): String =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() } ?: fallback

private fun JsonObject?.stringOrNull(key: String): String? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

private fun JsonObject?.double(key: String, fallback: Double): Double =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() } ?: fallback

private fun JsonObject?.doubleOrNull(key: String): Double? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() }

private fun JsonObject?.long(key: String, fallback: Long): Long =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.longOrNull }.getOrNull() } ?: fallback

private fun JsonObject?.longOrNull(key: String): Long? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.longOrNull }.getOrNull() }

private fun JsonObject?.int(key: String, fallback: Int): Int =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() } ?: fallback

private fun JsonObject?.boolean(key: String, fallback: Boolean): Boolean =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() } ?: fallback

private fun JsonObject?.stringList(key: String, fallback: List<String>): List<String> =
    (this?.get(key) as? JsonArray)?.mapNotNull { element ->
        (element as? JsonPrimitive)?.let { runCatching { it.content }.getOrNull() }
    } ?: fallback

private fun JsonObject?.mapStringDouble(key: String, fallback: Map<String, Double>): Map<String, Double> {
    val obj = this?.get(key) as? JsonObject ?: return fallback
    return obj.entries.associate { (k, v) ->
        k to (runCatching { v.jsonPrimitive.doubleOrNull }.getOrNull() ?: 0.0)
    }
}



data class QualityMetrics(
    val generatedAt: String = "",
    val zoneGateBlocks: Int = 0,
    val slBufferActivations: Int = 0,
    val counterTrendBlocks: Int = 0,
    val managementEvents: Int = 0,
    val entryZoneTotal: Int = 0,
    val tradesPlaced: Int = 0,
    val tradesRejected: Int = 0,
    val zoneByZone: Map<String, Int> = emptyMap(),
    val zoneBySide: Map<String, Int> = emptyMap(),
    val slBufferByTrigger: Map<String, Int> = emptyMap(),
    val ctBySide: Map<String, Int> = emptyMap(),
    val mgmtByEvent: Map<String, Int> = emptyMap(),
    val entryZoneDistribution: Map<String, Int> = emptyMap(),
)

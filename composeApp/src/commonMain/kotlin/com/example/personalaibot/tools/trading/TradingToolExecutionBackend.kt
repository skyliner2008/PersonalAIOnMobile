package com.example.personalaibot.tools.trading

import io.ktor.client.HttpClient
import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.automation.SmcFlowAlertProvider
import com.example.personalaibot.automation.StrategySignalProvider
import com.example.personalaibot.automation.SignalAlertProvider

/**
 * TradingToolExecutionBackend — Coordinates trading tool execution across domain-specific handlers:
 * - Mt5ToolHandler (MT5 broker operations & intelligence)
 * - MarketTechnicalToolHandler (Market snapshots, TA, scans, sentiment, news)
 * - ResearchToolHandler (Backtesting, strategy signals, FRED, correlation, deep analysis)
 * - SmcToolExecutor (SMC analysis & smart money concepts)
 */
class TradingToolExecutionBackend(
    private val client: HttpClient,
    private val geminiService: GeminiService
) {
    private val api = TradingApiService(client)
    private val smcExecutor = SmcToolExecutor(client)
    private val advancedEngine = AdvancedTradingEngine(SmcApiService(client))
    private val smcFlowProvider = SmcFlowAlertProvider(SmcApiService(client))
    private val strategySignalProvider = StrategySignalProvider(SmcApiService(client))
    private val signalAlertProvider = SignalAlertProvider(SmcApiService(client))

    private val backtestHandler = BacktestToolHandler(client, signalAlertProvider)
    private val mt5Handler = Mt5ToolHandler(client, api)
    private val marketTechnicalHandler = MarketTechnicalToolHandler(client, api, geminiService, advancedEngine)
    private val researchHandler = ResearchToolHandler(
        client = client,
        geminiService = geminiService,
        api = api,
        advancedEngine = advancedEngine,
        strategySignalProvider = strategySignalProvider,
        signalAlertProvider = signalAlertProvider,
        backtestHandler = backtestHandler
    )

    /**
     * Single domain-dispatch boundary. Domain handlers own tool execution;
     * this backend coordinates execution and provides fallback/dispatching.
     */
    suspend fun execute(toolName: String, args: Map<String, String>): String {
        val normalizedToolName = toolName.trim()
        if (normalizedToolName.isEmpty()) return "Unknown trading tool: $toolName"
        return when (TradingToolRouter.domainOf(normalizedToolName)) {
            TradingToolRouter.Domain.MARKET,
            TradingToolRouter.Domain.TECHNICAL,
            TradingToolRouter.Domain.SENTIMENT -> marketTechnicalHandler.execute(normalizedToolName, args)

            TradingToolRouter.Domain.MT5,
            TradingToolRouter.Domain.MT5_INTELLIGENCE -> mt5Handler.execute(normalizedToolName, args)

            TradingToolRouter.Domain.RESEARCH -> researchHandler.execute(normalizedToolName, args)

            TradingToolRouter.Domain.SMC -> if (normalizedToolName == "trading_smc_flow") {
                executeSmcFlow(args)
            } else {
                smcExecutor.execute(normalizedToolName, args)
            }

            TradingToolRouter.Domain.UNKNOWN -> "Unknown trading tool: $toolName"
        }
    }

    /** trading_smc_flow — สัญญาณจาก SMC Flow System (คำนวณในเครื่องจากแท่งเทียน TV) */
    internal suspend fun executeSmcFlow(args: Map<String, String>): String {
        val symbol = (args["symbol"] ?: "XAUUSD").trim().uppercase()
        val interval = (args["interval"] ?: "1h").trim().lowercase()
        return runCatching { smcFlowProvider.fetchFormatted("$symbol@$interval") }
            .getOrElse { "❌ SMC Flow error: ${it.message}" }
    }

    // ─── Internal Compatibility Delegators ──────────────────────────────────────
    internal fun executeBacktest(args: Map<String, String>): String = backtestHandler.executeBacktest(args)
    internal fun executeBacktestOptimize(args: Map<String, String>): String = backtestHandler.executeOptimize(args)
    internal fun executeBacktestEvolve(args: Map<String, String>): String = backtestHandler.executeEvolve(args)
    internal fun executeMixConfig(args: Map<String, String>): String = backtestHandler.executeMixConfig(args)

    internal suspend fun executePrice(args: Map<String, String>): String = marketTechnicalHandler.executePrice(args)
    internal suspend fun executeMarketSnapshot(args: Map<String, String>): String = marketTechnicalHandler.executeMarketSnapshot(args)
    internal suspend fun executeTechnicalAnalysis(args: Map<String, String>): String = marketTechnicalHandler.executeTechnicalAnalysis(args)
    internal suspend fun executeMultiTimeframe(args: Map<String, String>): String = marketTechnicalHandler.executeMultiTimeframe(args)
    internal suspend fun executeSentiment(args: Map<String, String>): String = marketTechnicalHandler.executeSentiment(args)
    internal suspend fun executeNews(args: Map<String, String>): String = marketTechnicalHandler.executeNews(args)
    internal suspend fun executeMacroCalendar(args: Map<String, String>): String = marketTechnicalHandler.executeMacroCalendar(args)

    internal suspend fun executeMt5Order(args: Map<String, String>): String = mt5Handler.executeMt5Order(args)
    internal suspend fun executeMt5ClosePosition(args: Map<String, String>): String = mt5Handler.executeMt5ClosePosition(args)
    internal suspend fun executeMt5ModifyPosition(args: Map<String, String>): String = mt5Handler.executeMt5ModifyPosition(args)
    internal suspend fun executeMt5AccountInfo(args: Map<String, String>): String = mt5Handler.executeMt5AccountInfo(args)
    internal suspend fun executeMt5Candles(args: Map<String, String>): String = mt5Handler.executeMt5Candles(args)
    internal suspend fun executeMt5Analyze(args: Map<String, String>): String = mt5Handler.executeMt5Analyze(args)

    internal suspend fun executeStrategySignal(args: Map<String, String>): String = researchHandler.executeStrategySignal(args)
    internal suspend fun executeSignalStats(args: Map<String, String>): String = researchHandler.executeSignalStats(args)
    internal fun executePositionSizing(args: Map<String, String>): String = researchHandler.executePositionSizing(args)
    internal suspend fun executeCorrelationMatrix(args: Map<String, String>): String = researchHandler.executeCorrelationMatrix(args)
    internal suspend fun executeEconomicData(args: Map<String, String>): String = researchHandler.executeEconomicData(args)
    internal suspend fun executeDeepAnalysisSuite(args: Map<String, String>): String = researchHandler.executeDeepAnalysisSuite(args)
    internal suspend fun executeCombined(args: Map<String, String>): String = researchHandler.executeCombined(args)
}

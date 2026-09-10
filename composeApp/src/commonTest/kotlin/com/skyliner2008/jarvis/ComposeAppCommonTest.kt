package com.skyliner2008.jarvis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import com.skyliner2008.jarvis.automation.backtest.BacktestConfig
import com.skyliner2008.jarvis.automation.backtest.BacktestReproducibility
import com.skyliner2008.jarvis.tools.trading.Mt5RiskGate
import com.skyliner2008.jarvis.tools.trading.Candle

class ComposeAppCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }


    @Test
    fun backtestReproducibility_isStableForSameInputs() {
        val candles = listOf(
            Candle(100.0, 101.0, 99.0, 100.5, 10.0, 1_700_000_000_000L),
            Candle(100.5, 102.0, 100.0, 101.5, 12.0, 1_700_000_060_000L)
        )
        val config = BacktestConfig()
        val a = BacktestReproducibility.runId("XAUUSD", "1m", "test", candles, config, BacktestReproducibility.ENGINE_STRATEGY_VERSION)
        val b = BacktestReproducibility.runId("XAUUSD", "1m", "test", candles, config, BacktestReproducibility.ENGINE_STRATEGY_VERSION)
        assertEquals(a, b)
    }


    @Test
    fun backtestEngine_isDeterministicForSameDatasetAndConfig() {
        val candles = (0 until 100).map { i ->
            val base = 100.0 + i * 0.1
            Candle(base, base + 1.0, base - 1.0, base + 0.2, 100.0 + i, 1_700_000_000_000L + i * 60_000L)
        }
        val engine = com.skyliner2008.jarvis.automation.backtest.BacktestEngine()
        val config = BacktestConfig()
        val run = {
            engine.run(
                symbol = "XAUUSD",
                interval = "1m",
                source = "deterministic-test",
                candles = candles,
                markers = emptyList(),
                kindFilter = emptySet(),
                kindOf = { it },
                strategyName = { it },
                tpSl = { _, _, _, _, _, _ -> 99.0 to 101.0 },
                config = config,
                startIndex = 14
            )
        }
        val a = run()
        val b = run()
        assertEquals(a.runId, b.runId)
        assertEquals(a.datasetId, b.datasetId)
        assertEquals(a.parameterHash, b.parameterHash)
        assertEquals(a.totalTrades, b.totalTrades)
        assertEquals(a.finalBalance, b.finalBalance)
        assertEquals(a.maxDrawdownPct, b.maxDrawdownPct)
    }

    @Test
    fun backtestEngine_rejectsNonChronologicalCandles() {
        val candles = (0 until 62).map { i ->
            val base = 100.0 + i
            Candle(base, base + 1.0, base - 1.0, base + 0.5, 100.0, 1_700_000_000_000L + i * 60_000L)
        }.toMutableList().apply {
            this[30] = this[30].copy(timestamp = this[29].timestamp)
        }
        val engine = com.skyliner2008.jarvis.automation.backtest.BacktestEngine()
        val result = runCatching {
            engine.run(
                symbol = "XAUUSD",
                interval = "1m",
                source = "chronology-test",
                candles = candles,
                markers = emptyList(),
                kindFilter = emptySet(),
                kindOf = { it },
                strategyName = { it },
                tpSl = { _, _, _, _, _, _ -> 99.0 to 101.0 }
            )
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun backtestEngine_doesNotTradeSignalOnLastCandle() {
        val candles = (0 until 62).map { i ->
            val base = 100.0 + i * 0.1
            Candle(base, base + 1.0, base - 1.0, base + 0.5, 100.0, 1_700_000_000_000L + i * 60_000L)
        }
        val lastTimestamp = candles.last().timestamp
        val marker = com.skyliner2008.jarvis.automation.SignalMarkerProvider.SignalMarker(
            time = lastTimestamp,
            side = "BUY",
            label = "MOM-test",
            color = "#ffffff"
        )
        val result = com.skyliner2008.jarvis.automation.backtest.BacktestEngine().run(
            symbol = "XAUUSD",
            interval = "1m",
            source = "last-bar-test",
            candles = candles,
            markers = listOf(marker),
            kindFilter = setOf("MOM"),
            kindOf = { it.substringBefore("-") },
            strategyName = { it },
            tpSl = { _, _, _, _, _, _ -> 99.0 to 101.0 }
        )
        assertEquals(0, result.totalTrades)
    }


    @Test
    fun monteCarlo_isSeedDeterministic() {
        val pnls = listOf(100.0, -50.0, 80.0, -20.0, 120.0, -30.0)
        val a = com.skyliner2008.jarvis.automation.backtest.MonteCarlo.run(pnls, nSimulations = 100, seed = 42)
        val b = com.skyliner2008.jarvis.automation.backtest.MonteCarlo.run(pnls, nSimulations = 100, seed = 42)
        assertEquals(a, b)
    }

    @Test
    fun monteCarlo_rejectsInvalidSimulationCount() {
        val result = runCatching {
            com.skyliner2008.jarvis.automation.backtest.MonteCarlo.run(listOf(1.0, -1.0, 2.0, -2.0, 3.0), nSimulations = 0)
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun strategyRanking_isDeterministicAndGateRequiresAllEvidence() {
        val robustness = com.skyliner2008.jarvis.automation.backtest.RobustnessAnalyzer.Report(
            windows = 5, positiveOosWindows = 5, positiveOosRatio = 1.0,
            oosProfitFactorMedian = 1.5, oosToIsSharpeRatio = 1.0,
            parameterStabilityCv = 0.1,
            verdict = com.skyliner2008.jarvis.automation.backtest.RobustnessAnalyzer.Verdict.ROBUST
        )
        val mc = com.skyliner2008.jarvis.automation.backtest.MonteCarlo.MonteCarloResult(100, 11_000.0, 10_000.0, 12_000.0, 0.10, 0.20, 0.01, 0.70)
        val perm = com.skyliner2008.jarvis.automation.backtest.PermutationTest.PermutationResult(1.2, 0.1, 0.2, 0.01, true, 200)
        val evidence = com.skyliner2008.jarvis.automation.backtest.StrategyRanking.Evidence("A", 100.0, robustness, mc, perm)
        val ranked = com.skyliner2008.jarvis.automation.backtest.StrategyRanking.rank(listOf(evidence))
        assertEquals(1, ranked.single().rank)
        assertEquals(com.skyliner2008.jarvis.automation.backtest.StrategyRanking.Verdict.PASS, ranked.single().verdict)
        assertTrue(ranked.single().riskGateEligible)
    }

    @Test
    fun riskGate_blocksHighRuinOrDrawdown() {
        val input = com.skyliner2008.jarvis.automation.backtest.RiskGate.Input(90.0, com.skyliner2008.jarvis.automation.backtest.RobustnessAnalyzer.Verdict.ROBUST, true, 0.06, 0.20, false)
        assertEquals(com.skyliner2008.jarvis.automation.backtest.RiskGate.Decision.BLOCK, com.skyliner2008.jarvis.automation.backtest.RiskGate.evaluate(input).decision)
    }


    @Test
    fun signalDataRouter_demoAlwaysUsesTradingView() = kotlinx.coroutines.runBlocking {
        val tv = List(62) { i -> com.skyliner2008.jarvis.tools.trading.Candle(1.0, 1.1, 0.9, 1.0, 1.0, i.toLong()) }
        com.skyliner2008.jarvis.automation.TradingSignalMarketDataRouter.configure(
            liveModeProvider = { false },
            mt5ConnectedProvider = { true },
            mt5CandleProvider = { _, _, _ -> error("MT5 must not be called in DEMO") }
        )
        val (candles, source, reason) = com.skyliner2008.jarvis.automation.TradingSignalMarketDataRouter.fetch("XAUUSD", "15m", 300) { tv }
        assertEquals("TRADINGVIEW", source)
        assertEquals("DEMO_FORCES_TV", reason)
        assertEquals(62, candles.size)
    }

    @Test
    fun signalDataRouter_liveConnectedUsesMt5() = kotlinx.coroutines.runBlocking {
        val mt5 = List(62) { i -> com.skyliner2008.jarvis.tools.trading.Candle(2.0, 2.1, 1.9, 2.0, 2.0, i.toLong()) }
        com.skyliner2008.jarvis.automation.TradingSignalMarketDataRouter.configure(
            liveModeProvider = { true },
            mt5ConnectedProvider = { true },
            mt5CandleProvider = { _, _, _ -> mt5 }
        )
        val (candles, source, reason) = com.skyliner2008.jarvis.automation.TradingSignalMarketDataRouter.fetch("XAUUSD", "15m", 300) { error("TV must not be selected") }
        assertEquals("MT5_LIVE", source)
        assertEquals("MT5_LIVE_CONNECTED", reason)
        assertEquals(2.0, candles.first().open)
    }

    @Test
    fun signalDataRouter_liveButDisconnectedFallsBackToTradingView() = kotlinx.coroutines.runBlocking {
        val tv = List(62) { i -> com.skyliner2008.jarvis.tools.trading.Candle(3.0, 3.1, 2.9, 3.0, 3.0, i.toLong()) }
        com.skyliner2008.jarvis.automation.TradingSignalMarketDataRouter.configure(
            liveModeProvider = { true },
            mt5ConnectedProvider = { false },
            mt5CandleProvider = { _, _, _ -> error("MT5 must not be called while disconnected") }
        )
        val (candles, source, reason) = com.skyliner2008.jarvis.automation.TradingSignalMarketDataRouter.fetch("XAUUSD", "15m", 300) { tv }
        assertEquals("TRADINGVIEW", source)
        assertEquals("MT5_LIVE_OFFLINE", reason)
        assertEquals(3.0, candles.first().open)
    }

    @Test
    fun p78_demoSignalExecution_rejectsUnreadySignal() = kotlinx.coroutines.runBlocking {
        val engine = com.skyliner2008.jarvis.automation.backtest.DemoTradingEngine()
        val result = com.skyliner2008.jarvis.automation.backtest.DemoSignalExecution.execute(
            engine,
            com.skyliner2008.jarvis.automation.backtest.DemoSignalExecution.Request(
                "EURUSD", true, 0.01, 1.1000, 1.0950, 1.1100,
                signalReady = false, riskGateEligible = true, promotionSource = "P7.6_STRATEGY_RANKING"
            )
        )
        assertEquals("SIGNAL_NOT_READY", result.reason)
        assertTrue(!result.executed)
    }

    @Test
    fun p78_demoSignalExecution_rejectsInvalidPromotion() = kotlinx.coroutines.runBlocking {
        val engine = com.skyliner2008.jarvis.automation.backtest.DemoTradingEngine()
        val result = com.skyliner2008.jarvis.automation.backtest.DemoSignalExecution.execute(
            engine,
            com.skyliner2008.jarvis.automation.backtest.DemoSignalExecution.Request(
                "EURUSD", true, 0.01, 1.1000, 1.0950, 1.1100,
                signalReady = true, riskGateEligible = true, promotionSource = "ANALYSIS_ONLY"
            )
        )
        assertEquals("INVALID_PROMOTION_SOURCE", result.reason)
        assertTrue(!result.executed)
    }


    @Test
    fun p78_demoSignalExecution_executesEligibleSignal() = kotlinx.coroutines.runBlocking {
        val engine = com.skyliner2008.jarvis.automation.backtest.DemoTradingEngine()
        val result = com.skyliner2008.jarvis.automation.backtest.DemoSignalExecution.execute(
            engine,
            com.skyliner2008.jarvis.automation.backtest.DemoSignalExecution.Request(
                "EURUSD", true, 0.01, 1.1000, 1.0950, 1.1100,
                signalReady = true, riskGateEligible = true, promotionSource = "P7.6_STRATEGY_RANKING"
            )
        )
        assertTrue(result.executed)
        assertTrue(result.positionId?.startsWith("demo-") == true)
        assertEquals("DEMO_EXECUTED", result.reason)
    }

    @Test
    fun p77_signalGenerationRequiresP76Pass() {
        val ranked = com.skyliner2008.jarvis.automation.backtest.StrategyRanking.Ranked(
            name = "test",
            score = 80.0,
            rank = 1,
            verdict = com.skyliner2008.jarvis.automation.backtest.StrategyRanking.Verdict.HOLD,
            riskGateEligible = false
        )
        val decision = com.skyliner2008.jarvis.automation.SignalGenerationGate.evaluate(ranked)
        assertTrue(!decision.eligible)
        assertEquals("STRATEGY_RANKING_NOT_PASS", decision.reason)
    }

    @Test
    fun p77_signalGenerationAcceptsOnlyP76Pass() {
        val ranked = com.skyliner2008.jarvis.automation.backtest.StrategyRanking.Ranked(
            name = "test",
            score = 80.0,
            rank = 1,
            verdict = com.skyliner2008.jarvis.automation.backtest.StrategyRanking.Verdict.PASS,
            riskGateEligible = true
        )
        val decision = com.skyliner2008.jarvis.automation.SignalGenerationGate.evaluate(ranked)
        assertTrue(decision.eligible)
        assertEquals("P7.6_PASS", decision.reason)
    }

    @Test
    fun p79_endToEnd_backtestToSignalToDemoExecution() = kotlinx.coroutines.runBlocking {
        val candles: List<com.skyliner2008.jarvis.tools.trading.Candle> = List(70) { i ->
            val p = 100.0 + i * 0.05
            com.skyliner2008.jarvis.tools.trading.Candle(p, p + 0.5, p - 0.5, p + 0.1, 1000.0, 1_700_000_000_000L + i * 60_000L)
        }
        val signalTime = candles[20].timestamp
        val markers = listOf(
            com.skyliner2008.jarvis.automation.SignalMarkerProvider.SignalMarker(signalTime, "BUY", "TEST▲", "#00FF00")
        )
        val engine = com.skyliner2008.jarvis.automation.backtest.BacktestEngine()
        val backtest = engine.run(
            symbol = "EURUSD", interval = "M5", source = "P7.9_FIXTURE",
            candles = candles, markers = markers, kindFilter = setOf("TEST"),
            kindOf = { "TEST" }, strategyName = { "P7.9" },
            tpSl = { _, side, c, i, _, _ ->
                val entry = c[i].close
                if (side == "BUY") entry - 0.5 to entry + 1.0 else entry + 0.5 to entry - 1.0
            }
        )
        assertTrue(backtest.totalTrades >= 1)

        val ranked = com.skyliner2008.jarvis.automation.backtest.StrategyRanking.Ranked(
            name = "P7.9", score = 90.0, rank = 1,
            verdict = com.skyliner2008.jarvis.automation.backtest.StrategyRanking.Verdict.PASS,
            riskGateEligible = true
        )
        val signalGate = com.skyliner2008.jarvis.automation.SignalGenerationGate.evaluate(ranked)
        assertTrue(signalGate.eligible)

        val demo = com.skyliner2008.jarvis.automation.backtest.DemoTradingEngine()
        val execution = com.skyliner2008.jarvis.automation.backtest.DemoSignalExecution.execute(
            demo,
            com.skyliner2008.jarvis.automation.backtest.DemoSignalExecution.Request(
                "EURUSD", true, 0.01, 100.0, 99.99, 100.02,
                signalReady = true, riskGateEligible = true,
                promotionSource = "P7.6_STRATEGY_RANKING"
            )
        )
        assertTrue(execution.executed)
        assertTrue(execution.positionId != null)
    }

    @Test
    fun mt5RiskGate_blocksOversizedOrderAndUnconfirmedCloseAll() {
        val oversized = Mt5RiskGate.validateOrder(mapOf("action" to "BUY", "volume" to "11"))
        val closeAll = Mt5RiskGate.validateCloseAll(emptyMap())
        check(oversized?.contains("volume") == true)
        check(closeAll?.contains("confirm=true") == true)
    }

    @Test
    fun mt5RiskGate_acceptsSafeOrderAndConfirmedCloseAll() {
        val safe = Mt5RiskGate.validateOrder(
            mapOf("action" to "BUY", "volume" to "0.10", "price" to "3000", "sl" to "2990", "tp" to "3020")
        )
        assertEquals(null, safe)
        assertEquals(null, Mt5RiskGate.validateCloseAll(mapOf("confirm" to "true")))
    }


    @Test
    fun tradingToolRouter_classifiesDomainsWithoutChangingToolNames() {
        assertEquals(
            com.skyliner2008.jarvis.tools.trading.TradingToolRouter.Domain.MT5,
            com.skyliner2008.jarvis.tools.trading.TradingToolRouter.domainOf("trading_mt5_order")
        )
        assertEquals(
            com.skyliner2008.jarvis.tools.trading.TradingToolRouter.Domain.RESEARCH,
            com.skyliner2008.jarvis.tools.trading.TradingToolRouter.domainOf("trading_backtest_evolve")
        )
        assertEquals(
            com.skyliner2008.jarvis.tools.trading.TradingToolRouter.Domain.SMC,
            com.skyliner2008.jarvis.tools.trading.TradingToolRouter.domainOf("trading_smc_structure")
        )
        assertEquals(
            com.skyliner2008.jarvis.tools.trading.TradingToolRouter.Domain.UNKNOWN,
            com.skyliner2008.jarvis.tools.trading.TradingToolRouter.domainOf("trading_unknown")
        )
    }
}
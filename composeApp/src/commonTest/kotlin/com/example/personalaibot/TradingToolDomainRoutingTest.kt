package com.example.personalaibot

import com.example.personalaibot.tools.trading.TradingToolRouter
import kotlin.test.Test
import kotlin.test.assertEquals

class TradingToolDomainRoutingTest {
    @Test
    fun mt5Tools_routeToMt5Domain() {
        val mt5Tools = listOf(
            "trading_mt5_order", "trading_mt5_close_position", "trading_mt5_modify_position",
            "trading_mt5_account_info", "trading_mt5_list_positions", "trading_mt5_list_orders",
            "trading_mt5_list_history", "trading_mt5_candles", "trading_mt5_analyze",
            "trading_mt5_symbol_info", "trading_mt5_symbol_search", "trading_mt5_close_all",
            "trading_mt5_break_even_all", "trading_mt5_snapshot", "trading_mt5_trade_actions"
        )
        mt5Tools.forEach { tool ->
            assertEquals(TradingToolRouter.Domain.MT5, TradingToolRouter.domainOf(tool), "Failed on tool: $tool")
        }
    }

    @Test
    fun mt5IntelligenceTools_routeToMt5IntelligenceDomain() {
        val mt5IntelTools = listOf(
            "trading_mt5_market_scanner", "trading_mt5_correlation_radar", "trading_mt5_sentiment_gauge",
            "trading_mt5_institutional_flow", "trading_mt5_economic_radar", "trading_mt5_trade_journal"
        )
        mt5IntelTools.forEach { tool ->
            assertEquals(TradingToolRouter.Domain.MT5_INTELLIGENCE, TradingToolRouter.domainOf(tool), "Failed on tool: $tool")
        }
    }

    @Test
    fun marketAndTechnicalTools_routeCorrectly() {
        val marketTools = listOf("trading_price", "trading_market_snapshot", "trading_top_gainers", "trading_top_losers")
        marketTools.forEach { tool ->
            assertEquals(TradingToolRouter.Domain.MARKET, TradingToolRouter.domainOf(tool), "Failed on tool: $tool")
        }

        val technicalTools = listOf(
            "trading_technical_analysis", "trading_multi_timeframe", "trading_bollinger_scan",
            "trading_oversold_scan", "trading_overbought_scan", "trading_volume_breakout",
            "trading_harmonic_scan", "trading_elliot_modern_analysis"
        )
        technicalTools.forEach { tool ->
            assertEquals(TradingToolRouter.Domain.TECHNICAL, TradingToolRouter.domainOf(tool), "Failed on tool: $tool")
        }

        val sentimentTools = listOf("trading_sentiment", "trading_news", "trading_macro_calendar", "trading_fear_greed")
        sentimentTools.forEach { tool ->
            assertEquals(TradingToolRouter.Domain.SENTIMENT, TradingToolRouter.domainOf(tool), "Failed on tool: $tool")
        }
    }

    @Test
    fun researchTools_routeToResearchDomain() {
        val researchTools = listOf(
            "trading_deep_analysis_suite", "trading_backtest", "trading_backtest_optimize",
            "trading_backtest_evolve", "trading_mix_config", "trading_strategy_signal",
            "trading_signal_stats", "trading_fundamental_analysis", "trading_crypto_overview",
            "trading_position_sizing", "trading_correlation_matrix", "trading_economic_data",
            "trading_combined"
        )
        researchTools.forEach { tool ->
            assertEquals(TradingToolRouter.Domain.RESEARCH, TradingToolRouter.domainOf(tool), "Failed on tool: $tool")
        }
    }

    @Test
    fun smcTools_routeToSmcDomain() {
        val smcTools = listOf(
            "trading_smc_flow", "trading_smc_analysis", "trading_smc_sweeps",
            "trading_smc_liquidity", "trading_smc_orderblocks", "trading_smc_structure"
        )
        smcTools.forEach { tool ->
            assertEquals(TradingToolRouter.Domain.SMC, TradingToolRouter.domainOf(tool), "Failed on tool: $tool")
        }
    }

    @Test
    fun blankOrUnknownToolName_isUnknown() {
        assertEquals(TradingToolRouter.Domain.UNKNOWN, TradingToolRouter.domainOf("   "))
        assertEquals(TradingToolRouter.Domain.UNKNOWN, TradingToolRouter.domainOf("non_existent_tool"))
    }
}

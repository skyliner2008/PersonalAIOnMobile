package com.example.personalaibot.tools.trading

/**
 * Stable routing boundary for trading tools.
 * The executor remains backward-compatible while domain handlers are extracted incrementally.
 */
internal object TradingToolRouter {
    enum class Domain {
        MARKET,
        TECHNICAL,
        SENTIMENT,
        RESEARCH,
        SMC,
        MT5,
        MT5_INTELLIGENCE,
        UNKNOWN
    }

    fun domainOf(toolName: String): Domain = when (toolName) {
        "trading_price", "trading_market_snapshot", "trading_top_gainers", "trading_top_losers" -> Domain.MARKET
        "trading_technical_analysis", "trading_multi_timeframe", "trading_bollinger_scan",
        "trading_oversold_scan", "trading_overbought_scan", "trading_volume_breakout",
        "trading_harmonic_scan", "trading_elliot_modern_analysis" -> Domain.TECHNICAL
        "trading_sentiment", "trading_news", "trading_macro_calendar", "trading_fear_greed" -> Domain.SENTIMENT
        "trading_deep_analysis_suite", "trading_backtest", "trading_backtest_optimize",
        "trading_backtest_evolve", "trading_mix_config", "trading_strategy_signal",
        "trading_signal_stats", "trading_fundamental_analysis", "trading_crypto_overview",
        "trading_position_sizing", "trading_correlation_matrix", "trading_economic_data",
        "trading_combined" -> Domain.RESEARCH
        "trading_smc_flow", "trading_smc_analysis", "trading_smc_sweeps", "trading_smc_liquidity",
        "trading_smc_orderblocks", "trading_smc_structure" -> Domain.SMC
        "trading_mt5_order", "trading_mt5_close_position", "trading_mt5_modify_position",
        "trading_mt5_account_info", "trading_mt5_list_positions", "trading_mt5_list_orders",
        "trading_mt5_list_history", "trading_mt5_candles", "trading_mt5_analyze",
        "trading_mt5_symbol_info", "trading_mt5_symbol_search", "trading_mt5_close_all",
        "trading_mt5_break_even_all", "trading_mt5_snapshot", "trading_mt5_trade_actions" -> Domain.MT5
        "trading_mt5_market_scanner", "trading_mt5_correlation_radar", "trading_mt5_sentiment_gauge",
        "trading_mt5_institutional_flow", "trading_mt5_economic_radar", "trading_mt5_trade_journal" -> Domain.MT5_INTELLIGENCE
        else -> Domain.UNKNOWN
    }
}

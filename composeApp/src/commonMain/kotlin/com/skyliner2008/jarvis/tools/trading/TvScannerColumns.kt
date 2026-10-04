package com.skyliner2008.jarvis.tools.trading

/**
 * TradingView scanner columns (scanner.tradingview.com). A column without a suffix is the **daily** value;
 * "|1" "|5" "|15" "|30" "|60" "|120" "|240" "|1W" "|1M" are the other timeframes, and only those
 * ("|3", "|360", "|480", "|720", "|1D" come back null).
 *
 * Checked on BINANCE:BTCUSDT 2026-10-03: RSI = 63.8 (the daily RSI), RSI|60 = 49.4 (the 1h RSI), RSI|1D = null.
 * The app used to send 1h with no suffix, so the default trading_technical_analysis showed the daily RSI, MACD,
 * ADX, ATR and rating as 1h. It also sent 1D as "|1D", so the daily row of trading_multi_timeframe and the
 * technical pillar of trading_sentiment were always empty.
 */
object TvScannerColumns {

    /** The column suffix for [timeframe] (any form TaIndicators knows), or null: the scanner has no such timeframe. */
    fun suffix(timeframe: String?): String? = when (TaIndicators.timeframeSpec(timeframe).canonical) {
        "1m" -> "|1"
        "5m" -> "|5"
        "15m" -> "|15"
        "30m" -> "|30"
        "1h" -> "|60"
        "2h" -> "|120"
        "4h" -> "|240"
        "1D" -> ""
        "1W" -> "|1W"
        "1M" -> "|1M"
        else -> null
    }

    /** Bollinger width in % of the basis. The scanner has no BB.width column (it comes back null). */
    fun bbWidthPct(upper: Double?, lower: Double?, basis: Double?): Double? =
        if (upper == null || lower == null || basis == null || basis <= 0.0 || upper < lower) null
        else (upper - lower) / basis * 100.0

    /** Stablecoins and fiat quoted in USDT: their tiny moves are noise in a crypto scan. */
    val STABLE_OR_FIAT_BASES = listOf(
        "USDC", "FDUSD", "TUSD", "USDP", "DAI", "USDE", "PYUSD", "RLUSD", "USD1", "BFUSD", "AEUR",
        "XUSD", "U", "USDS", "USDD", "FRAX", "EURC", "GUSD",
        "EUR", "EURI", "GBP", "TRY", "BRL", "JPY", "AUD"
    )
}

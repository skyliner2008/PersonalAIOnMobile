package com.skyliner2008.jarvis.tools.trading

/**
 * The tone of an English headline, judged by whole words. The old test looked for substrings, so "high" matched
 * "highlight", "low" matched "follow" and "slow", "buy" matched "buyback", and "ath" matched "path" and "death".
 */
object NewsTone {
    private val BULL = Regex(
        "\\b(surge[sd]?|surging|bull(s|ish)?|jump(s|ed)?|rall(y|ies|ied)|gain(s|ed)?|highs?|positive|boom(s|ing)?|" +
            "buy(s|ing)?|upward|breakout|ath|soar(s|ed|ing)?|rebound(s|ed)?|inflows?)\\b"
    )
    private val BEAR = Regex(
        "\\b(fall(s|ing)?|fell|drop(s|ped)?|bear(s|ish)?|crash(es|ed)?|loss(es)?|lows?|negative|slump(s|ed)?|" +
            "sell(s|ing)?|sell-?off|plunge[sd]?|plunging|risks?|warning|caution|dip(s|ped)?|tumble[sd]?|slide[sd]?|outflows?)\\b"
    )

    fun bullish(text: String): Boolean = BULL.containsMatchIn(text.lowercase())

    fun bearish(text: String): Boolean = BEAR.containsMatchIn(text.lowercase())
}

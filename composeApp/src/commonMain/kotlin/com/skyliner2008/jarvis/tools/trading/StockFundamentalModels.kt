package com.skyliner2008.jarvis.tools.trading

import kotlinx.serialization.Serializable

/**
 * StockFundamentalData — โครงสร้างข้อมูลปัจจัยพื้นฐาน งบดุล งบการเงิน และมัลติเปิล
 * ถอดแบบจาก TradingView Financials / Company Profile
 */
@Serializable
data class StockFundamentalData(
    val symbol: String,
    val ticker: String,
    val name: String,
    val description: String,
    val exchange: String,
    val currency: String,
    val sector: String,
    val industry: String,
    val country: String,

    // Market & Price
    val closePrice: Double?,
    val changePrice: Double?,
    val changePct: Double?,
    val high52w: Double?,
    val low52w: Double?,
    val beta1y: Double?,
    val perf1y: Double?,
    val perfYtd: Double?,

    // Valuation Multiples
    val marketCap: Double?,
    val peTtm: Double?,
    val psCurrent: Double?,
    val pbFq: Double?,
    val pfcfTtm: Double?,
    val enterpriseValue: Double?,
    val evToRevenueTtm: Double?,

    // Per Share & Dividend
    val epsBasicTtm: Double?,
    val epsDilutedTtm: Double?,
    val epsBasicFy: Double?,
    val dividendYieldCurrent: Double?,
    val dpsFy: Double?,

    // Shares & Ownership
    val totalShares: Double?,
    val floatShares: Double?,
    val floatPct: Double?,
    val closelyHeldShares: Double?,
    val closelyHeldPct: Double?,

    // Capital Structure & Balance Sheet (FQ)
    val totalDebt: Double?,
    val netDebt: Double?,
    val cashAndEquivalents: Double?,
    val totalAssets: Double?,
    val totalLiabilities: Double?,
    val totalEquity: Double?,
    val debtToEquity: Double?,

    // Financials & Operating Performance (TTM, FY, FQ)
    val totalRevenueTtm: Double?,
    val totalRevenueFy: Double?,
    val totalRevenueFq: Double?,
    val netIncomeTtm: Double?,
    val netIncomeFy: Double?,
    val netIncomeFq: Double?,
    val freeCashFlowTtm: Double?,
    val operatingMarginTtm: Double?,
    val netMarginTtm: Double?,
    val returnOnEquity: Double?,
    val returnOnAssets: Double?,
    val returnOnInvestedCapital: Double?,

    // Analyst Consensus & Targets
    val targetPriceAvg: Double?,
    val targetPriceHigh: Double?,
    val targetPriceLow: Double?,
    val upsidePct: Double?,
    val recommendationScore: Double?
) {
    companion object {
        fun formatMoneyCompact(value: Double?, currency: String = ""): String {
            if (value == null) return "N/A"
            val abs = kotlin.math.abs(value)
            val sign = if (value < 0) "-" else ""
            val formatted = when {
                abs >= 1_000_000_000_000.0 -> "${sign}${"%.2f".format(abs / 1_000_000_000_000.0)} T"
                abs >= 1_000_000_000.0 -> "${sign}${"%.2f".format(abs / 1_000_000_000.0)} B"
                abs >= 1_000_000.0 -> "${sign}${"%.2f".format(abs / 1_000_000.0)} M"
                abs >= 1_000.0 -> "${sign}${"%.2f".format(abs / 1_000.0)} K"
                else -> "${sign}${"%.2f".format(abs)}"
            }
            return if (currency.isNotBlank()) "$formatted $currency" else formatted
        }

        fun formatSharesCompact(value: Double?): String {
            if (value == null) return "N/A"
            val abs = kotlin.math.abs(value)
            return when {
                abs >= 1_000_000_000.0 -> "${"%.2f".format(abs / 1_000_000_000.0)}B"
                abs >= 1_000_000.0 -> "${"%.2f".format(abs / 1_000_000.0)}M"
                abs >= 1_000.0 -> "${"%.2f".format(abs / 1_000.0)}K"
                else -> "${"%.0f".format(abs)}"
            }
        }

        fun formatRatio(value: Double?, suffix: String = "x"): String {
            if (value == null) return "N/A"
            return "${"%.2f".format(value)}$suffix"
        }

        fun formatPercent(value: Double?): String {
            if (value == null) return "N/A"
            return "${"%.2f".format(value)}%"
        }
    }
}

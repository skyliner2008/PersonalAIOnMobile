package com.skyliner2008.jarvis.tools.trading

import kotlinx.serialization.Serializable

@Serializable
data class BinancePositioningSentiment(
    val symbol: String,
    val retailLongAccountPct: Double,
    val retailShortAccountPct: Double,
    val retailLongShortRatio: Double,
    val topTraderLongPositionPct: Double,
    val topTraderShortPositionPct: Double,
    val topTraderLongShortRatio: Double,
    val takerBuySellRatio: Double,
    val takerBuyVol: Double = 0.0,
    val takerSellVol: Double = 0.0,
    val divergenceSignal: String = ""
)

@Serializable
data class CnnSubIndicator(
    val name: String,
    val labelThai: String,
    val score: Double,
    val rating: String
)

@Serializable
data class CnnFearAndGreedData(
    val score: Double,
    val rating: String,
    val ratingThai: String,
    val previousClose: Double = 0.0,
    val previous1Week: Double = 0.0,
    val previous1Month: Double = 0.0,
    val previous1Year: Double = 0.0,
    val subIndicators: List<CnnSubIndicator> = emptyList()
)

@Serializable
data class SentimentPillar(
    val name: String,
    val score: Double, // 0.0 ถึง 100.0
    val weightPct: Int, // e.g. 30
    val detail: String
)

@Serializable
data class UnifiedCompositeSentiment(
    val target: String, // Symbol เช่น "BTCUSDT", "AAPL" หรือ "GLOBAL_MACRO"
    val isGlobalMacro: Boolean = false,
    val compositeScore: Double, // 0.0 ถึง 100.0
    val compositeLabel: String, // "Extreme Fear", "Fear", "Neutral", "Greed", "Extreme Greed"
    val labelThai: String,
    val emoji: String,
    val meterBar: String, // e.g. "[████████░░]"
    val pillars: List<SentimentPillar> = emptyList(),
    val binancePositioning: BinancePositioningSentiment? = null,
    val cnnFearGreed: CnnFearAndGreedData? = null,
    val cryptoFearGreed: Map<String, String>? = null,
    val topHeadlines: List<String> = emptyList(),
    val contrarianAlert: String? = null
) {
    companion object {
        fun makeMeterBar(score: Double, length: Int = 10): String {
            val clamped = score.coerceIn(0.0, 100.0)
            val filled = kotlin.math.round((clamped / 100.0) * length).toInt().coerceIn(0, length)
            val empty = length - filled
            return "[${"█".repeat(filled)}${"░".repeat(empty)}]"
        }

        fun classifyScore(score: Double): Triple<String, String, String> {
            val s = score.coerceIn(0.0, 100.0)
            return when {
                s <= 24.0 -> Triple("Extreme Fear", "กลัวสุดขีด (Extreme Fear)", "🥶 🔴")
                s <= 44.0 -> Triple("Fear", "วิตกกังวล (Fear)", "😰 🟠")
                s <= 55.0 -> Triple("Neutral", "เป็นกลาง (Neutral)", "⚖️ 😐")
                s <= 75.0 -> Triple("Greed", "เชื่อมั่น / โลภ (Greed)", "🟢 🚀")
                else -> Triple("Extreme Greed", "โลภสุดขีด (Extreme Greed)", "🔥 🟢")
            }
        }
    }
}

@Serializable
data class AssetSentimentReport(
    val symbol: String,
    val assetType: String, // "CRYPTO", "US_STOCK", "THAI_STOCK", "COMMODITY_FOREX"
    val score: Double, // -100.0 ถึง +100.0
    val label: String, // "Extreme Greed", "Bullish", "Neutral", "Bearish", "Extreme Fear"
    val crowdPsychology: String, // "Euphoria", "Greed", "Complacency", "Skepticism", "Fear", "Capitulation"
    val binancePositioning: BinancePositioningSentiment? = null,
    val cnnFearGreed: CnnFearAndGreedData? = null,
    val cryptoFearGreed: Map<String, String>? = null,
    val technicalRating: String? = null,
    val bullishDrivers: List<String> = emptyList(),
    val bearishDrivers: List<String> = emptyList(),
    val contrarianAlert: String? = null,
    val intelligenceSummary: String = ""
)

package com.example.personalaibot.automation

import com.example.personalaibot.logDebug
import com.example.personalaibot.automation.backtest.EntryParams
import com.example.personalaibot.tools.trading.Candle
import com.example.personalaibot.tools.trading.SmcApiService
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Semantic market-state layer for LLM consumption. Keeps raw indicator values available while exposing grouped conclusions. */
data class IndicatorGroupState(
    val bias: String,
    val score: Int,
    val confidence: Int,
    val evidence: List<String>
)

data class MarketStateSummary(
    val trend: IndicatorGroupState,
    val momentum: IndicatorGroupState,
    val volatility: IndicatorGroupState,
    val flow: IndicatorGroupState,
    val structure: IndicatorGroupState,
    val overallBias: String,
    val overallScore: Int,
    val confidence: Int
)

/** Context classifier used by confluence logic; indicators are interpreted differently by regime. */
fun TradingViewIndicatorSnapshot.Snapshot.marketRegime(): String = when {
    adx14 >= 25.0 && plusDi14 > minusDi14 && bbWidthPct >= 2.0 -> "TRENDING_UP_VOLATILE"
    adx14 >= 25.0 && minusDi14 > plusDi14 && bbWidthPct >= 2.0 -> "TRENDING_DOWN_VOLATILE"
    adx14 >= 25.0 && plusDi14 > minusDi14 -> "TRENDING_UP"
    adx14 >= 25.0 && minusDi14 > plusDi14 -> "TRENDING_DOWN"
    bbWidthPct <= 1.5 -> "LOW_VOLATILITY_RANGE"
    bbWidthPct >= 4.0 -> "HIGH_VOLATILITY"
    else -> "RANGE"
}
/** Input integrity guard shared by AI/query/alert callers before indicator calculation. */
data class MarketDataIntegrity(
    val valid: Boolean,
    val ordered: Boolean,
    val duplicates: Int,
    val gaps: Int,
    val invalidBars: Int,
    val partialLastBar: Boolean,
    val reason: String
)

object TradingMarketDataGuard {
    fun validate(candles: List<Candle>): MarketDataIntegrity {
        if (candles.isEmpty()) return MarketDataIntegrity(false, true, 0, 0, 0, false, "NO_DATA")
        var duplicates = 0
        var gaps = 0
        var invalid = 0
        var ordered = true
        val sorted = candles.zipWithNext()
        for ((a, b) in sorted) {
            if (a.timestamp == b.timestamp) duplicates++
            if (a.timestamp > b.timestamp) ordered = false
            if (a.timestamp > 0L && b.timestamp > a.timestamp) {
                val delta = b.timestamp - a.timestamp
                if (delta > 0L && delta > 7L * 24L * 60L * 60L * 1000L) gaps++
            }
        }
        candles.forEach {
            if (!it.open.isFinite() || !it.high.isFinite() || !it.low.isFinite() || !it.close.isFinite() ||
                it.high < it.low || it.open < it.low || it.open > it.high || it.close < it.low || it.close > it.high) invalid++
        }
        val partial = candles.last().timestamp <= 0L
        val valid = ordered && duplicates == 0 && invalid == 0 && gaps == 0
        val reason = when {
            !ordered -> "OUT_OF_ORDER"
            duplicates > 0 -> "DUPLICATE_CANDLES"
            invalid > 0 -> "INVALID_OHLC"
            gaps > 0 -> "TIMESTAMP_GAP"
            else -> "OK"
        }
        return MarketDataIntegrity(valid, ordered, duplicates, gaps, invalid, partial, reason)
    }
}
/**
 * Indicator governance for the Mobile TradingView pipeline.
 * AI_ANALYSIS is intentionally compact; USER_QUERY exposes the complete registry;
 * ALERTS may use any deterministic field that has a stable calculation.
 */
object TradingIndicatorCatalog {
    enum class Purpose { AI_ANALYSIS, USER_QUERY, ALERTS }
    data class Definition(val key: String, val category: String, val purposes: Set<Purpose>, val description: String)

    val definitions = listOf(
        Definition("ema7", "trend", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "EMA 7"),
        Definition("ema9", "trend", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "EMA 9"),
        Definition("ema14", "trend", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "EMA 14"),
        Definition("ema20", "trend", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "EMA 20"),
        Definition("ema21", "trend", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "EMA 21"),
        Definition("ema50", "trend", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "EMA 50"),
        Definition("ema100", "trend", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "EMA 100"),
        Definition("ema200", "trend", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "EMA 200"),
        Definition("sma20", "trend", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "SMA 20"),
        Definition("sma50", "trend", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "SMA 50"),
        Definition("sma100", "trend", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "SMA 100"),
        Definition("sma200", "trend", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "SMA 200"),
        Definition("ema_alignment", "trend", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "EMA alignment"),
        Definition("adx14", "trend", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "ADX 14"),
        Definition("supertrend", "trend", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "Supertrend"),
        Definition("rsi7", "momentum", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "RSI 7"),
        Definition("rsi14", "momentum", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "RSI 14"),
        Definition("rsi21", "momentum", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "RSI 21"),
        Definition("macd", "momentum", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "MACD / signal / histogram"),
        Definition("stochastic", "momentum", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "Stochastic K/D"),
        Definition("cci20", "momentum", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "CCI 20"),
        Definition("mfi14", "flow", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "MFI 14"),
        Definition("atr7", "volatility", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "ATR 7"),
        Definition("atr14", "volatility", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "ATR 14 / ATR percent"),
        Definition("atr21", "volatility", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "ATR 21"),
        Definition("bollinger", "volatility", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "Bollinger Bands / %B / width"),
        Definition("vwap", "flow", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "VWAP and distance"),
        Definition("volume_ratio20", "flow", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "Volume / 20-period average"),
        Definition("obv", "flow", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "OBV / slope"),
        Definition("ichimoku", "structure", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "Ichimoku lines and cloud"),
        Definition("pivot", "levels", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "Pivot / R1 / R2 / S1 / S2"),
        Definition("support_resistance", "levels", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "Nearest support/resistance levels"),
        Definition("donchian", "levels", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "Donchian upper/mid/lower"),
        Definition("fibonacci", "levels", setOf(Purpose.USER_QUERY, Purpose.ALERTS), "Swing Fibonacci levels"),
        Definition("smc", "structure", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "BOS/CHOCH/OB/FVG/liquidity"),
        Definition("market_regime", "context", setOf(Purpose.AI_ANALYSIS, Purpose.USER_QUERY, Purpose.ALERTS), "Trend/range/volatility regime")
    )

    val aiAnalysis = definitions.filter { Purpose.AI_ANALYSIS in it.purposes }
    val userQuery = definitions.filter { Purpose.USER_QUERY in it.purposes }
    val alerts = definitions.filter { Purpose.ALERTS in it.purposes }

    fun describeUserQuery(): String = userQuery.joinToString(", ") { "${it.key}=${it.description}" }
}
/**
 * Indicator-only market snapshot for AI consumption. Raw OHLCV is intentionally
 * not exposed here; the AI receives derived indicator values/states instead.
 */
object TradingViewIndicatorSnapshot {
/** Point-in-time immutable market snapshot used by live, query, alert and backtest paths. */
data class TradingViewMarketSnapshot(
    val symbol: String,
    val timeframe: String,
    val asOfTimestamp: Long,
    val lastCandleTimestamp: Long,
    val price: Double,
    val indicators: TradingViewIndicatorSnapshot.Snapshot,
    val integrity: MarketDataIntegrity,
    val regime: String
)

data class SupportResistanceZone(
    val type: String,
    val lower: Double,
    val upper: Double,
    val source: String,
    val strength: Int
)

    data class Snapshot(
        val ema20: Double, val ema50: Double, val ema200: Double,
        val emaAlignment: String,
        val rsi7: Double, val rsi14: Double, val rsi21: Double, val rsiState: String,
        val macd: Double, val macdSignal: Double, val macdHistogram: Double, val macdState: String,
        val bbUpper: Double, val bbBasis: Double, val bbLower: Double, val bbPercentB: Double, val bbWidthPct: Double,
        val atr14: Double, val atrPct: Double,
        val adx14: Double, val plusDi14: Double, val minusDi14: Double, val adxState: String,
        val stochasticK: Double, val stochasticD: Double, val stochasticState: String,
        val cci20: Double, val cciState: String,
        val mfi14: Double, val mfiState: String,
        val vwap: Double, val vwapDistancePct: Double,
        val volumeRatio20: Double, val volumeState: String,
        val obvSlope20: Double, val obvState: String,
        val ichimokuTenkan: Double, val ichimokuKijun: Double, val ichimokuCloudTop: Double,
        val ichimokuCloudBottom: Double, val ichimokuState: String,
        val pivot: Double, val resistance1: Double, val resistance2: Double,
        val support1: Double, val support2: Double, val pivotState: String,
        val supertrend: Double, val supertrendState: String,
        val indicatorScore: Int
    )


fun Snapshot.snapshotAt(symbol: String, timeframe: String, asOfTimestamp: Long, lastCandleTimestamp: Long, price: Double, integrity: MarketDataIntegrity): TradingViewMarketSnapshot =
    TradingViewMarketSnapshot(symbol, timeframe, asOfTimestamp, lastCandleTimestamp, price, this, integrity, marketRegime())

fun Snapshot.supportResistanceZones(price: Double): List<SupportResistanceZone> {
    val zones = listOf(
        SupportResistanceZone("RESISTANCE", resistance1, resistance2, "PIVOT", 2),
        SupportResistanceZone("SUPPORT", support2, support1, "PIVOT", 2),
        SupportResistanceZone("RESISTANCE", bbUpper, bbUpper, "BOLLINGER", 1),
        SupportResistanceZone("SUPPORT", bbLower, bbLower, "BOLLINGER", 1),
        SupportResistanceZone(if (price >= pivot) "SUPPORT" else "RESISTANCE", pivot, pivot, "PIVOT", 2)
    )
    return zones.filter { it.lower.isFinite() && it.upper.isFinite() && it.lower > 0.0 && it.upper > 0.0 }
        .sortedBy { kotlin.math.abs(((it.lower + it.upper) / 2.0) - price) }
}

fun Snapshot.toAiContext(): String {
    val s = marketStateSummary()
    return buildString {
        appendLine("MARKET STATE: ${s.overallBias} score=${s.overallScore} confidence=${s.confidence}% regime=${marketRegime()}")
        appendLine("TREND: ${s.trend.bias} score=${s.trend.score} | ${s.trend.evidence.joinToString("; ")}")
        appendLine("MOMENTUM: ${s.momentum.bias} score=${s.momentum.score} | ${s.momentum.evidence.joinToString("; ")}")
        appendLine("VOLATILITY: ${s.volatility.bias} score=${s.volatility.score} | ${s.volatility.evidence.joinToString("; ")}")
        appendLine("FLOW: ${s.flow.bias} score=${s.flow.score} | ${s.flow.evidence.joinToString("; ")}")
        appendLine("STRUCTURE: ${s.structure.bias} score=${s.structure.score} | ${s.structure.evidence.joinToString("; ")}")
        appendLine("RAW INDICATORS: EMA20=$ema20 EMA50=$ema50 EMA200=$ema200 RSI=$rsi14 MACD=$macd Signal=$macdSignal Hist=$macdHistogram ADX=$adx14 DI+=$plusDi14 DI-=$minusDi14 ATR=$atr14 ATR%=$atrPct BB%B=$bbPercentB BBWidth%=$bbWidthPct Stoch=$stochasticK/$stochasticD CCI=$cci20 MFI=$mfi14 VWAP=$vwap VWAPDist%=$vwapDistancePct VolumeRatio=$volumeRatio20 OBVSlope=$obvSlope20 Ichimoku=$ichimokuState Pivot=$pivot S1=$support1 S2=$support2 R1=$resistance1 R2=$resistance2 Supertrend=$supertrendState")
    }
}


fun Snapshot.marketStateSummary(): MarketStateSummary {
    fun state(score: Int, evidence: List<String>): IndicatorGroupState {
        val bias = when {
            score >= 2 -> "BULLISH"
            score <= -2 -> "BEARISH"
            else -> "NEUTRAL"
        }
        return IndicatorGroupState(bias, score, (50 + kotlin.math.abs(score) * 10).coerceAtMost(100), evidence)
    }

    val trend = state(
        listOf(
            emaAlignment.contains("BULLISH") || emaAlignment.contains("UPTREND"),
            adxState.contains("BULLISH") || plusDi14 > minusDi14,
            supertrendState.contains("BULLISH") || supertrendState.contains("UPTREND")
        ).count { it } - listOf(
            emaAlignment.contains("BEARISH") || emaAlignment.contains("DOWNTREND"),
            adxState.contains("BEARISH") || minusDi14 > plusDi14,
            supertrendState.contains("BEARISH") || supertrendState.contains("DOWNTREND")
        ).count { it },
        listOf("EMA20/50/200=$emaAlignment", "ADX=$adx14 DI+=$plusDi14 DI-=$minusDi14", "Supertrend=$supertrendState")
    )
    val momentum = state(
        listOf(rsiState.contains("BULLISH"), macdState.contains("BULLISH"), stochasticState.contains("BULLISH"), cciState.contains("BULLISH"), mfiState.contains("BULLISH"))
            .count { it } - listOf(rsiState.contains("BEARISH"), macdState.contains("BEARISH"), stochasticState.contains("BEARISH"), cciState.contains("BEARISH"), mfiState.contains("BEARISH")).count { it },
        listOf("RSI=$rsi14 $rsiState", "MACD hist=$macdHistogram $macdState", "Stoch=$stochasticK/$stochasticD $stochasticState", "CCI=$cci20 $cciState", "MFI=$mfi14 $mfiState")
    )
    val volatility = state(
        when {
            bbWidthPct > 4.0 && atrPct > 1.0 -> 2
            bbWidthPct < 1.5 && atrPct < 0.5 -> -1
            else -> 0
        }, listOf("ATR=$atr14 (${atrPct}%)", "BB width=${bbWidthPct}% %B=$bbPercentB")
    )
    val flow = state(
        listOf(volumeState.contains("ACCUMULATION"), obvState.contains("ACCUMULATION"), vwapDistancePct > 0).count { it } -
            listOf(volumeState.contains("DISTRIBUTION"), obvState.contains("DISTRIBUTION"), vwapDistancePct < 0).count { it },
        listOf("VWAP distance=${vwapDistancePct}%", "Volume ratio=$volumeRatio20 $volumeState", "OBV slope=$obvSlope20 $obvState")
    )
    val structure = state(
        listOf(ichimokuState.contains("BULLISH"), pivotState.contains("ABOVE"), closeAbove(resistance1)).count { it } -
            listOf(ichimokuState.contains("BEARISH"), pivotState.contains("BELOW"), closeBelow(support1)).count { it },
        listOf("Ichimoku=$ichimokuState", "Pivot=$pivot $pivotState", "S1=$support1 R1=$resistance1")
    )
    val groups = listOf(trend, momentum, volatility, flow, structure)
    val score = groups.sumOf { it.score }
    return MarketStateSummary(trend, momentum, volatility, flow, structure,
        when { score >= 4 -> "BULLISH"; score <= -4 -> "BEARISH"; else -> "NEUTRAL" },
        score, (groups.sumOf { it.confidence } / groups.size).coerceIn(0, 100))
}

private fun Snapshot.closeAbove(level: Double): Boolean = level > 0.0 && ema20 > level
private fun Snapshot.closeBelow(level: Double): Boolean = level > 0.0 && ema20 < level

    fun calculate(candles: List<Candle>): Snapshot? {
        if (candles.size < 220) return null
        if (!TradingMarketDataGuard.validate(candles).valid) return null
        val c = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val volumes = candles.map { it.volume.coerceAtLeast(0.0) }
        val last = c.last()

        fun lastFinite(values: List<Double>): Double = values.lastOrNull { it.isFinite() } ?: Double.NaN
        fun sma(values: List<Double>, period: Int): Double = if (values.size < period) Double.NaN else values.takeLast(period).average()
        fun pct(v: Double) = if (last != 0.0) v / last * 100.0 else 0.0

        val ema20 = lastFinite(ema(c, 20)); val ema50 = lastFinite(ema(c, 50)); val ema200 = lastFinite(ema(c, 200))
        val emaAlignment = when {
            last > ema20 && ema20 > ema50 && ema50 > ema200 -> "BULLISH_STACK"
            last < ema20 && ema20 < ema50 && ema50 < ema200 -> "BEARISH_STACK"
            last > ema50 && ema50 > ema200 -> "BULLISH"
            last < ema50 && ema50 < ema200 -> "BEARISH"
            else -> "MIXED"
        }

        val rsi7 = rsi(c, 7) ?: 50.0
        val rsi14 = rsi(c, 14) ?: 50.0
        val rsi21 = rsi(c, 21) ?: 50.0
        val rsiState = when { rsi14 >= 70 -> "OVERBOUGHT"; rsi14 <= 30 -> "OVERSOLD"; rsi14 >= 55 -> "BULLISH"; rsi14 <= 45 -> "BEARISH"; else -> "NEUTRAL" }

        val ema12 = lastFinite(ema(c, 12)); val ema26 = lastFinite(ema(c, 26)); val macd = ema12 - ema26
        val macdSeries = ema(c, 12).indices.map { i ->
            val a = ema(c, 12)[i]; val b = ema(c, 26)[i]; if (a.isFinite() && b.isFinite()) a - b else Double.NaN
        }
        val macdSignal = lastFinite(ema(macdSeries.map { if (it.isFinite()) it else 0.0 }, 9)); val macdHist = macd - macdSignal
        val macdState = when { macdHist > 0 && macd > 0 -> "BULLISH"; macdHist < 0 && macd < 0 -> "BEARISH"; macdHist > 0 -> "BULLISH_CROSS"; macdHist < 0 -> "BEARISH_CROSS"; else -> "NEUTRAL" }

        val (bbUpper, bbBasis, bbLower) = bollinger(c, 20, 2.0)
        val bbRange = (bbUpper - bbLower).coerceAtLeast(1e-9)
        val bbPercentB = ((last - bbLower) / bbRange * 100.0).coerceIn(-100.0, 200.0)
        val bbWidthPct = pct(bbUpper - bbLower)

        val atrValues = trueRangeAtr(candles, 14); val atr14 = atrValues.lastOrNull() ?: 0.0; val atrPct = pct(atr14)
        val (adx14, plusDi14, minusDi14) = adx(candles, 14)
        val adxState = when { adx14 >= 25 && plusDi14 > minusDi14 -> "STRONG_UPTREND"; adx14 >= 25 && minusDi14 > plusDi14 -> "STRONG_DOWNTREND"; adx14 >= 20 -> "TRENDING_WEAK"; else -> "RANGING" }

        val (stochK, stochD) = stochastic(highs, lows, c, 14, 3)
        val stochasticState = when { stochK >= 80 && stochK < stochD -> "BEARISH_OVERBOUGHT"; stochK <= 20 && stochK > stochD -> "BULLISH_OVERSOLD"; stochK > stochD -> "BULLISH"; stochK < stochD -> "BEARISH"; else -> "NEUTRAL" }

        val cci20 = cci(candles, 20); val cciState = when { cci20 >= 100 -> "BULLISH_MOMENTUM"; cci20 <= -100 -> "BEARISH_MOMENTUM"; cci20 > 0 -> "BULLISH"; cci20 < 0 -> "BEARISH"; else -> "NEUTRAL" }
        val mfi14 = mfi(candles, 14); val mfiState = when { mfi14 >= 80 -> "OVERBOUGHT"; mfi14 <= 20 -> "OVERSOLD"; mfi14 >= 50 -> "BULLISH"; else -> "BEARISH" }

        val typical = candles.map { (it.high + it.low + it.close) / 3.0 }
        val vwapDen = volumes.sum().coerceAtLeast(1e-9); val vwap = typical.zip(volumes).sumOf { it.first * it.second } / vwapDen
        val vwapDistancePct = pct(last - vwap)
        val volumeAvg20 = sma(volumes, 20).coerceAtLeast(1e-9); val volumeRatio20 = volumes.last() / volumeAvg20
        val volumeState = when { volumeRatio20 >= 2.0 -> "VERY_HIGH"; volumeRatio20 >= 1.3 -> "HIGH"; volumeRatio20 <= 0.7 -> "LOW"; else -> "NORMAL" }

        val obv = obv(candles); val obvSlope20 = obv.takeLast(20).let { if (it.size < 2) 0.0 else it.last() - it.first() }
        val obvState = when { obvSlope20 > 0 -> "ACCUMULATION"; obvSlope20 < 0 -> "DISTRIBUTION"; else -> "FLAT" }

        val (tenkan, kijun, cloudTop, cloudBottom) = ichimoku(highs, lows, 9, 26, 52)
        val ichimokuState = when {
            last > cloudTop && tenkan > kijun -> "BULLISH_ABOVE_CLOUD"
            last < cloudBottom && tenkan < kijun -> "BEARISH_BELOW_CLOUD"
            tenkan > kijun -> "BULLISH_IN_CLOUD"
            tenkan < kijun -> "BEARISH_IN_CLOUD"
            else -> "NEUTRAL"
        }

        val prior = candles[candles.size - 2]
        val pivot = (prior.high + prior.low + prior.close) / 3.0
        val resistance1 = 2 * pivot - prior.low; val support1 = 2 * pivot - prior.high
        val resistance2 = pivot + (prior.high - prior.low); val support2 = pivot - (prior.high - prior.low)
        val pivotState = when { last > resistance1 -> "ABOVE_R1"; last > pivot -> "ABOVE_PIVOT"; last < support1 -> "BELOW_S1"; else -> "BELOW_PIVOT" }

        val (supertrend, supertrendState) = supertrend(candles, 10, 3.0)

        val states = listOf(
            emaAlignment, rsiState, macdState, adxState, stochasticState, cciState, mfiState,
            if (vwapDistancePct > 0) "BULLISH" else "BEARISH", obvState, ichimokuState,
            pivotState, supertrendState
        )
        val indicatorScore = states.fold(0) { total: Int, state: String ->
            total + when {
                state.contains("BULLISH") || state.contains("UPTREND") || state == "ABOVE_R1" || state == "ABOVE_PIVOT" || state == "ACCUMULATION" -> 1
                state.contains("BEARISH") || state.contains("DOWNTREND") || state == "BELOW_S1" || state == "BELOW_PIVOT" || state == "DISTRIBUTION" -> -1
                else -> 0
            }
        }

        return Snapshot(ema20, ema50, ema200, emaAlignment, rsi7, rsi14, rsi21, rsiState, macd, macdSignal, macdHist, macdState,
            bbUpper, bbBasis, bbLower, bbPercentB, bbWidthPct, atr14, atrPct, adx14, plusDi14, minusDi14, adxState,
            stochK, stochD, stochasticState, cci20, cciState, mfi14, mfiState, vwap, vwapDistancePct,
            volumeRatio20, volumeState, obvSlope20, obvState, tenkan, kijun, cloudTop, cloudBottom, ichimokuState,
            pivot, resistance1, resistance2, support1, support2, pivotState, supertrend, supertrendState, indicatorScore)
    }

    private fun ema(values: List<Double>, period: Int): List<Double> {
        if (values.size < period) return List(values.size) { Double.NaN }
        val k = 2.0 / (period + 1); val out = ArrayList<Double>(values.size)
        var prev = values.take(period).average(); repeat(period - 1) { out += Double.NaN }; out += prev
        for (i in period until values.size) { prev = values[i] * k + prev * (1.0 - k); out += prev }
        return out
    }

    private fun rsi(values: List<Double>, period: Int): Double? {
        if (values.size <= period) return null
        var gain = 0.0; var loss = 0.0
        for (i in 1..period) { val d = values[i] - values[i - 1]; if (d >= 0) gain += d else loss -= d }
        var avgGain = gain / period; var avgLoss = loss / period
        for (i in period + 1 until values.size) {
            val d = values[i] - values[i - 1]; val g = maxOf(0.0, d); val l = maxOf(0.0, -d)
            avgGain = (avgGain * (period - 1) + g) / period; avgLoss = (avgLoss * (period - 1) + l) / period
        }
        if (avgLoss == 0.0) return 100.0
        return 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
    }

    private fun bollinger(values: List<Double>, period: Int, mult: Double): Triple<Double, Double, Double> {
        if (values.size < period) return Triple(Double.NaN, Double.NaN, Double.NaN)
        val w = values.takeLast(period); val mean = w.average(); val sd = sqrt(w.map { (it - mean).pow(2) }.average())
        return Triple(mean + mult * sd, mean, mean - mult * sd)
    }

    private fun trueRangeAtr(candles: List<Candle>, period: Int): List<Double> {
        val tr = candles.indices.map { i -> if (i == 0) candles[i].high - candles[i].low else maxOf(candles[i].high - candles[i].low, abs(candles[i].high - candles[i - 1].close), abs(candles[i].low - candles[i - 1].close)) }
        return tr.mapIndexed { i, _ -> if (i + 1 < period) Double.NaN else tr.subList(i + 1 - period, i + 1).average() }
    }

    private fun adx(candles: List<Candle>, period: Int): Triple<Double, Double, Double> {
        if (candles.size <= period + 1) return Triple(0.0, 0.0, 0.0)
        val tr = mutableListOf<Double>(); val plus = mutableListOf<Double>(); val minus = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val cur = candles[i]; val prev = candles[i - 1]
            tr += maxOf(cur.high - cur.low, abs(cur.high - prev.close), abs(cur.low - prev.close))
            val up = cur.high - prev.high; val down = prev.low - cur.low
            plus += if (up > down && up > 0) up else 0.0; minus += if (down > up && down > 0) down else 0.0
        }
        val atr = tr.takeLast(period).average().coerceAtLeast(1e-9)
        val pdi = plus.takeLast(period).sum() / atr * 100.0; val mdi = minus.takeLast(period).sum() / atr * 100.0
        val dx = if (pdi + mdi == 0.0) 0.0 else abs(pdi - mdi) / (pdi + mdi) * 100.0
        return Triple(dx, pdi, mdi)
    }

    private fun stochastic(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int, smooth: Int): Pair<Double, Double> {
        val ks = mutableListOf<Double>()
        for (i in period - 1 until closes.size) {
            val hi = highs.subList(i + 1 - period, i + 1).max(); val lo = lows.subList(i + 1 - period, i + 1).min()
            ks += if (hi == lo) 50.0 else (closes[i] - lo) / (hi - lo) * 100.0
        }
        val k = ks.lastOrNull() ?: 50.0; val d = ks.takeLast(smooth).average(); return k to d
    }

    private fun cci(candles: List<Candle>, period: Int): Double {
        val tp = candles.map { (it.high + it.low + it.close) / 3.0 }; val window = tp.takeLast(period); val mean = window.average()
        val dev = window.map { abs(it - mean) }.average().coerceAtLeast(1e-9); return (tp.last() - mean) / (0.015 * dev)
    }

    private fun mfi(candles: List<Candle>, period: Int): Double {
        val tp = candles.map { (it.high + it.low + it.close) / 3.0 }; var pos = 0.0; var neg = 0.0
        val start = maxOf(1, candles.size - period)
        for (i in start until candles.size) { val flow = tp[i] * candles[i].volume.coerceAtLeast(0.0); if (tp[i] > tp[i - 1]) pos += flow else if (tp[i] < tp[i - 1]) neg += flow }
        return if (neg == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + pos / neg)
    }

    private fun obv(candles: List<Candle>): List<Double> {
        var value = 0.0; val out = mutableListOf(0.0)
        for (i in 1 until candles.size) { value += when { candles[i].close > candles[i - 1].close -> candles[i].volume; candles[i].close < candles[i - 1].close -> -candles[i].volume; else -> 0.0 }; out += value }
        return out
    }

    private fun ichimoku(highs: List<Double>, lows: List<Double>, tenkanP: Int, kijunP: Int, spanBP: Int): List<Double> {
        fun mid(p: Int): Double { val hi = highs.takeLast(p).max(); val lo = lows.takeLast(p).min(); return (hi + lo) / 2.0 }
        val tenkan = mid(tenkanP); val kijun = mid(kijunP); val spanB = mid(spanBP); val spanA = (tenkan + kijun) / 2.0
        return listOf(tenkan, kijun, maxOf(spanA, spanB), minOf(spanA, spanB))
    }

    private fun supertrend(candles: List<Candle>, period: Int, multiplier: Double): Pair<Double, String> {
        val atr = trueRangeAtr(candles, period).lastOrNull()?.takeIf { it.isFinite() } ?: 0.0
        val mid = (candles.last().high + candles.last().low) / 2.0; val upper = mid + multiplier * atr; val lower = mid - multiplier * atr
        return if (candles.last().close >= mid) lower to "BULLISH" else upper to "BEARISH"
    }
}

/** P7.7: strategy analysis is never an executable signal by itself. Promotion must come from P7.6. */
object SignalGenerationGate {
    data class Decision(val eligible: Boolean, val reason: String)

    fun evaluate(ranked: com.example.personalaibot.automation.backtest.StrategyRanking.Ranked?): Decision =
        when {
            ranked == null -> Decision(false, "NO_RANKED_STRATEGY")
            ranked.verdict != com.example.personalaibot.automation.backtest.StrategyRanking.Verdict.PASS ->
                Decision(false, "STRATEGY_RANKING_NOT_PASS")
            !ranked.riskGateEligible -> Decision(false, "RISK_GATE_NOT_ELIGIBLE")
            else -> Decision(true, "P7.6_PASS")
        }
}

/**
 * StrategySignalProvider — แปลงกลยุทธ์จาก Strategy Library (Quantpedia/QuantConnect reference)
 * มาคำนวณในเครื่องจากแท่งเทียน (TV) โดยตรง ไม่ต้องพึ่ง QuantConnect
 *
 * เฉพาะกลยุทธ์ที่ลดรูปเป็น "สัญญาณต่อแท่งเทียน" จาก OHLCV ล้วนได้จริง
 * (กลุ่ม cross-sectional/portfolio/fundamental ทำบนแท่งเทียนตัวเดียวไม่ได้ — ไม่รวม)
 *
 * กลยุทธ์ใน v1 (map จากคลัง):
 *  1) tsmom     — Time-Series Momentum (time-series-momentum-effect):
 *                 ROC 20 แท่ง > 0 → BUY, < 0 → SELL (ตามทิศโมเมนตัมของตัวเอง)
 *  2) trend     — Trend Following (asset-class-trend-following):
 *                 ราคา > EMA200 และ EMA50 > EMA200 → BUY (กลับกัน → SELL)
 *  3) reversal  — Short-Term Reversal (short-term-reversal-in-stocks):
 *                 RSI(14) < 30 และแตะ BB Lower → BUY (ดีดกลับ), RSI > 70 + BB Upper → SELL
 *  4) donchian  — Donchian Channel Breakout (trend-following classics):
 *                 ทะลุ high 20 แท่ง → BUY, หลุด low 20 แท่ง → SELL
 *  5) w52high   — 52-Weeks High proximity (52-weeks-high-effect):
 *                 ใกล้จุดสูงสุดของข้อมูลที่มี (>= 98% ของ high) → BUY bias (โมเมนตัมต่อเนื่อง)
 *                 หลุดต่ำกว่า 90% → อ่อนแอ (SELL bias)
 *
 * ใช้ผ่าน tool_name = "trading_strategy_signal" ใน AlertJob — เลือก TF ด้วย suffix @TF (default 1h)
 */
class StrategySignalProvider(private val smcApi: SmcApiService) {

    companion object {
        const val TSMOM_LOOKBACK = 20
        const val DONCHIAN_PERIOD = 20
        const val RSI_PERIOD = 14
        const val BB_PERIOD = 20
        const val BB_MULT = 2.0
        const val W52_PROX_BUY = 0.98
        const val W52_PROX_SELL = 0.90

        val STRATEGY_IDS = listOf("tsmom", "trend", "reversal", "donchian", "w52high")
    }

    suspend fun fetch(rawSymbol: String): Map<String, String> {
        val (symbol, tf) = IndicatorAlertProvider.splitSymbolAndTf(rawSymbol)
        val result = smcApi.fetchTradingViewCandlesOnly(symbol, tf, 300)
        val candles = result.candles
        if (candles.size < 60) {
            return mapOf("error" to "candles ${candles.size} < 60 (${result.source})")
        }
        val closes = candles.map { it.close }
        val n = candles.size
        val last = candles.last()

        // entry params ที่จูนแล้ว (EntryTuning) — ใช้แทนค่า default ของกลยุทธ์ที่มี tuning
        val tuned = runCatching {
            com.example.personalaibot.db.JarvisDatabaseHolder.getAutomationManager()
                .getTunedEntryParams(symbol, tf)
        }.getOrElse { emptyMap() }
        val epMOM = tuned["MOM"] ?: EntryParams()
        val epTR = tuned["TR"] ?: EntryParams()
        val epREV = tuned["REV"] ?: EntryParams()
        val epDC = tuned["DC"] ?: EntryParams()
        val ep52H = tuned["52H"] ?: EntryParams()

        // ── 1) Time-Series Momentum ──
        val roc = (last.close - closes[n - 1 - epMOM.momLookback]) / closes[n - 1 - epMOM.momLookback] * 100.0
        val tsmomSignal = when {
            roc > 0 -> "BUY"
            roc < 0 -> "SELL"
            else -> "NONE"
        }

        // ── 2) Trend Following (EMA fast/slow + price vs EMA slow) ──
        val emaFast = ema(closes, epTR.trFast).last()
        val emaSlow = ema(closes, epTR.trSlow).last()
        val trendState = when {
            last.close > emaSlow && emaFast > emaSlow -> "UPTREND"
            last.close < emaSlow && emaFast < emaSlow -> "DOWNTREND"
            else -> "RANGE"
        }
        val trendSignal = when (trendState) {
            "UPTREND" -> "BUY"
            "DOWNTREND" -> "SELL"
            else -> "NONE"
        }

        // ── 3) Short-Term Reversal (RSI + Bollinger) ──
        val rsiNow = rsi(closes, epREV.revRsiPeriod)
        val (bbUpper, bbBasis, bbLower) = bollinger(closes, epREV.revBbPeriod, epREV.revBbMult)
        val reversalSignal = when {
            rsiNow != null && rsiNow < epREV.revRsiLow && last.close <= bbLower -> "BUY"
            rsiNow != null && rsiNow > epREV.revRsiHigh && last.close >= bbUpper -> "SELL"
            else -> "NONE"
        }

        // ── 4) Donchian Breakout (ไม่รวมแท่งปัจจุบัน) ──
        val look = candles.subList(n - 1 - epDC.dcPeriod, n - 1)
        val dUpper = look.maxOf { it.high }
        val dLower = look.minOf { it.low }
        val dMid = (dUpper + dLower) / 2.0
        val donchianSignal = when {
            last.close > dUpper -> "BUY"
            last.close < dLower -> "SELL"
            else -> "NONE"
        }

        // ── 5) 52-Weeks High proximity — ROLLING window (w52Lookback แท่ง ไม่รวมแท่งปัจจุบัน) ──
        //    เดิม maxOf ทั้งชุดที่ดึงมา (live ~300 แท่ง = ไม่กี่วัน ไม่ใช่ 52 สัปดาห์) — ให้ตรงกับ
        //    SignalMarkerProvider (backtest) เพื่อ live == backtest parity
        val w52From = maxOf(0, n - 1 - ep52H.w52Lookback)
        val periodHigh = candles.subList(w52From, n - 1).maxOf { it.high }
        val proximity = last.close / periodHigh
        val w52Signal = when {
            proximity >= ep52H.w52ProxBuy -> "BUY"
            proximity <= ep52H.w52ProxSell -> "SELL"
            else -> "NONE"
        }

        // Full indicator snapshot for AI: derived values only, never raw OHLCV.
        val indicatorSnapshot = TradingViewIndicatorSnapshot.calculate(candles)

        // ── Consensus: regime-aware + closed-bar confirmation ──
        // A raw strategy state is evidence, not an order. Weak candles are removed
        // before voting, and strategy weights change with market regime.
        fun confirmed(signal: String, label: String): String {
            if (signal == "NONE") return "NONE"
            return if (StrategyConfirmationGate.evaluate(candles, n - 1, signal, label).accepted) signal else "NONE"
        }
        val confirmedMom = confirmed(tsmomSignal, "MOM")
        val confirmedTrend = confirmed(trendSignal, "TR")
        val confirmedRev = confirmed(reversalSignal, "REV")
        val confirmedDc = confirmed(donchianSignal, "DC")
        val confirmed52H = confirmed(w52Signal, "52H")
        val regime = indicatorSnapshot?.marketRegime() ?: when (trendState) {
            "UPTREND" -> "TRENDING_UP"
            "DOWNTREND" -> "TRENDING_DOWN"
            else -> "RANGE"
        }
        val weights = when {
            regime.contains("TRENDING_UP") || regime.contains("TRENDING_DOWN") -> mapOf("MOM" to 1.2, "TR" to 1.3, "REV" to 0.5, "DC" to 1.2, "52H" to 0.9)
            regime == "LOW_VOLATILITY_RANGE" || regime == "RANGE" -> mapOf("MOM" to 0.8, "TR" to 0.6, "REV" to 1.3, "DC" to 0.7, "52H" to 0.8)
            else -> mapOf("MOM" to 1.0, "TR" to 1.0, "REV" to 0.8, "DC" to 1.0, "52H" to 0.9)
        }
        val votes = listOf("MOM" to confirmedMom, "TR" to confirmedTrend, "REV" to confirmedRev, "DC" to confirmedDc, "52H" to confirmed52H)
        val weightedScore = votes.sumOf { (kind, signal) ->
            when (signal) { "BUY" -> weights[kind] ?: 0.0; "SELL" -> -(weights[kind] ?: 0.0); else -> 0.0 }
        }
        val activeVotes = votes.count { it.second != "NONE" }
        val buyVotes = votes.count { it.second == "BUY" }
        val sellVotes = votes.count { it.second == "SELL" }
        val directionalAgreement = max(buyVotes, sellVotes)
        val consensusSignal = when {
            weightedScore >= 2.2 && directionalAgreement >= 2 -> "STRONG_BUY"
            weightedScore >= 1.0 && directionalAgreement >= 1 -> "BUY"
            weightedScore <= -2.2 && directionalAgreement >= 2 -> "STRONG_SELL"
            weightedScore <= -1.0 && directionalAgreement >= 1 -> "SELL"
            else -> "NEUTRAL"
        }

        val nowMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val lastBarAgeSec = if (last.timestamp > 0L) ((nowMs - last.timestamp).coerceAtLeast(0L) / 1000L) else -1L
        val barCoverage = (candles.size / 300.0).coerceIn(0.0, 1.0)
        val agreement = (activeVotes / votes.size.toDouble()).coerceIn(0.0, 1.0)
        val confirmationRate = votes.count { (kind, signal) -> signal != "NONE" && StrategyConfirmationGate.evaluate(candles, n - 1, signal, kind).accepted } / votes.size.toDouble()
        val signalConfidence = ((45.0 + kotlin.math.abs(weightedScore) * 12.0 + agreement * 15.0 + confirmationRate * 10.0 + barCoverage * 8.0).coerceIn(0.0, 95.0)).toInt()
        val dataQuality = (barCoverage * 70.0 + if (lastBarAgeSec >= 0L) 30.0 else 15.0).coerceIn(0.0, 100.0).toInt()

        logDebug("StrategySig", "$symbol/$tf source=${result.source} bars=${candles.size} age=${lastBarAgeSec}s regime=$regime raw=$tsmomSignal/$trendSignal/$reversalSignal/$donchianSignal/$w52Signal confirmed=$confirmedMom/$confirmedTrend/$confirmedRev/$confirmedDc/$confirmed52H weighted=${fmt(weightedScore)} → consensus=$consensusSignal confidence=$signalConfidence%")

        return buildMap {
            put("symbol", symbol)
            put("timeframe", tf)
            put("source", result.source)
            put("tv_data_only", "true")
            put("tv_bars", candles.size.toString())
            put("tv_last_bar_age_sec", lastBarAgeSec.toString())
            put("data_quality_pct", dataQuality.toString())
            put("signal_confidence_pct", signalConfidence.toString())
            // P7.7: this provider produces analysis only. P7.6 promotion is required before execution.
            put("signal_generation_status", "ANALYSIS_ONLY")
            put("signal_ready", "false")
            put("risk_gate_eligible", "false")
            put("promotion_source", "P7.6_STRATEGY_RANKING")
            put("close", fmt(last.close))
            // tsmom
            put("tsmom_signal", tsmomSignal)
            put("tsmom_roc_pct", fmt(roc))
            put("tsmom_lookback", epMOM.momLookback.toString())
            // trend
            put("trend_signal", trendSignal)
            put("trend_state", trendState)
            put("trend_ema50", fmt(emaFast))
            put("trend_ema200", fmt(emaSlow))
            // reversal
            put("reversal_signal", reversalSignal)
            put("reversal_rsi", rsiNow?.let { fmt(it) } ?: "N/A")
            put("reversal_bb_upper", fmt(bbUpper))
            put("reversal_bb_lower", fmt(bbLower))
            // donchian
            put("donchian_signal", donchianSignal)
            put("donchian_upper", fmt(dUpper))
            put("donchian_lower", fmt(dLower))
            put("donchian_mid", fmt(dMid))
            // w52 high
            put("w52_signal", w52Signal)
            put("w52_high", fmt(periodHigh))
            put("w52_proximity_pct", fmt(proximity * 100))
            // Full indicator intelligence — derived values only.
            indicatorSnapshot?.let { s ->
                put("indicator_score", s.indicatorScore.toString())
                put("ema20", fmt(s.ema20)); put("ema50", fmt(s.ema50)); put("ema200", fmt(s.ema200)); put("ema_alignment", s.emaAlignment)
                put("rsi14", fmt(s.rsi14)); put("rsi_state", s.rsiState)
                put("macd", fmt(s.macd)); put("macd_signal", fmt(s.macdSignal)); put("macd_histogram", fmt(s.macdHistogram)); put("macd_state", s.macdState)
                put("bb_upper", fmt(s.bbUpper)); put("bb_basis", fmt(s.bbBasis)); put("bb_lower", fmt(s.bbLower)); put("bb_percent_b", fmt(s.bbPercentB)); put("bb_width_pct", fmt(s.bbWidthPct))
                put("atr14", fmt(s.atr14)); put("atr_pct", fmt(s.atrPct))
                put("adx14", fmt(s.adx14)); put("plus_di14", fmt(s.plusDi14)); put("minus_di14", fmt(s.minusDi14)); put("adx_state", s.adxState)
                put("stochastic_k", fmt(s.stochasticK)); put("stochastic_d", fmt(s.stochasticD)); put("stochastic_state", s.stochasticState)
                put("cci20", fmt(s.cci20)); put("cci_state", s.cciState)
                put("mfi14", fmt(s.mfi14)); put("mfi_state", s.mfiState)
                put("vwap", fmt(s.vwap)); put("vwap_distance_pct", fmt(s.vwapDistancePct))
                put("volume_ratio20", fmt(s.volumeRatio20)); put("volume_state", s.volumeState)
                put("obv_slope20", fmt(s.obvSlope20)); put("obv_state", s.obvState)
                put("ichimoku_tenkan", fmt(s.ichimokuTenkan)); put("ichimoku_kijun", fmt(s.ichimokuKijun)); put("ichimoku_cloud_top", fmt(s.ichimokuCloudTop)); put("ichimoku_cloud_bottom", fmt(s.ichimokuCloudBottom)); put("ichimoku_state", s.ichimokuState)
                put("pivot", fmt(s.pivot)); put("resistance1", fmt(s.resistance1)); put("resistance2", fmt(s.resistance2)); put("support1", fmt(s.support1)); put("support2", fmt(s.support2)); put("pivot_state", s.pivotState)
                put("supertrend", fmt(s.supertrend)); put("supertrend_state", s.supertrendState)
            }
            // consensus
            put("consensus_signal", consensusSignal)
            put("market_regime", regime)
            put("consensus_score", fmt(weightedScore))
            put("consensus_raw_score", votes.fold(0) { acc, (_, signal) -> acc + if (signal == "BUY") 1 else if (signal == "SELL") -1 else 0 }.toString())
            put("consensus_buy_count", buyVotes.toString())
            put("consensus_sell_count", sellVotes.toString())
            put("confirmed_buy_count", votes.count { it.second == "BUY" }.toString())
            put("confirmed_sell_count", votes.count { it.second == "SELL" }.toString())
        }
    }

    /** Multi-timeframe TradingView fusion for mobile BUY/SELL/WAIT analysis. */
    suspend fun fetchMultiTimeframeFormatted(symbol: String, timeframes: List<String> = listOf("15m", "1h", "4h")): String {
        val normalized = symbol.trim().uppercase()
        val frames = timeframes.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val signals = mutableListOf<TradingViewSignalIntelligence.TimeframeSignal>()
        val indicatorSnapshots = mutableMapOf<String, TradingViewIndicatorSnapshot.Snapshot>()
        var smcScore = 0.0
        var smcContributors = 0
        var indicatorScoreSum = 0.0
        var indicatorScoreContributors = 0
        var latestClose = "N/A"
        var source = "TV"
        for (tf in frames) {
            val result = smcApi.fetchTradingViewCandlesOnly(normalized, tf, 300)
            if (result.candles.size < 60) continue
            source = result.source
            latestClose = fmt(result.candles.last().close)
            signals += TradingViewSignalIntelligence.analyze(tf, result.candles)
            val indicatorSnapshot = TradingViewIndicatorSnapshot.calculate(result.candles)
            if (indicatorSnapshot != null) {
                indicatorSnapshots[tf] = indicatorSnapshot
                indicatorScoreSum += (indicatorSnapshot.indicatorScore / 18.0).coerceIn(-1.0, 1.0)
                indicatorScoreContributors++
            }
            val smcSignals = com.example.personalaibot.automation.smc.SmcSignalDetector.detect(normalized, tf, result.candles)
            val smcBuy = smcSignals.count { it.side == "BUY" }
            val smcSell = smcSignals.count { it.side == "SELL" }
            if (smcBuy + smcSell > 0) {
                smcScore += ((smcBuy - smcSell).toDouble() / (smcBuy + smcSell)).coerceIn(-1.0, 1.0)
                smcContributors++
            }
        }
        val normalizedSmcScore = if (smcContributors > 0) smcScore / smcContributors else 0.0
        val normalizedIndicatorScore = if (indicatorScoreContributors > 0) indicatorScoreSum / indicatorScoreContributors else 0.0
        val fusion = TradingViewSignalIntelligence.fuse(signals, normalizedSmcScore, normalizedIndicatorScore)
        val primaryResult = smcApi.fetchTradingViewCandlesOnly(normalized, frames.firstOrNull() ?: "1h", 300)
        if (primaryResult.candles.size >= 60) {
            val key = "$normalized:${frames.joinToString(",")}"
            TradingViewSignalHistory.resolve(key, primaryResult.candles)
            TradingViewSignalHistory.observe(
                key = key,
                timestamp = primaryResult.candles.last().timestamp,
                decision = fusion.decision,
                entry = primaryResult.candles.last().close,
                confidencePct = fusion.confidencePct,
                dataQualityPct = fusion.dataQualityPct,
                gate = fusion.gate
            )
        }
        return buildString {
            appendLine("## 📡 TradingView Signal Intelligence — $normalized [TV-ONLY]")
            appendLine("Decision=${fusion.decision} | score=${fmt(fusion.score)} | confidence=${fusion.confidencePct}% | gate=${fusion.gate}")
            appendLine("Data quality=${fusion.dataQualityPct}% | MTF alignment=${fusion.alignedTimeframes}/${fusion.totalTimeframes} | close=$latestClose | source=$source")
            appendLine()
            appendLine("### Multi-Timeframe")
            signals.forEach { s ->
                appendLine("${s.timeframe}: ${s.decision} score=${fmt(s.score)} | trend=${s.trend} momentum=${s.momentum} reversal=${s.reversal} breakout=${s.breakout} | quality=${s.dataQualityPct}% bars=${s.bars}")
            }
            appendLine()
            appendLine("### Indicator Matrix — derived values only")
            indicatorSnapshots.forEach { (tf, s) ->
                appendLine("[$tf] Trend: EMA20=${fmt(s.ema20)} EMA50=${fmt(s.ema50)} EMA200=${fmt(s.ema200)} ${s.emaAlignment} | ADX=${fmt(s.adx14)} +DI=${fmt(s.plusDi14)} -DI=${fmt(s.minusDi14)} ${s.adxState}")
                appendLine("[$tf] Momentum: RSI14=${fmt(s.rsi14)} ${s.rsiState} | MACD=${fmt(s.macd)} signal=${fmt(s.macdSignal)} hist=${fmt(s.macdHistogram)} ${s.macdState} | Stoch K=${fmt(s.stochasticK)} D=${fmt(s.stochasticD)} ${s.stochasticState} | CCI20=${fmt(s.cci20)} ${s.cciState} | MFI14=${fmt(s.mfi14)} ${s.mfiState}")
                appendLine("[$tf] Volatility: ATR14=${fmt(s.atr14)} (${fmt(s.atrPct)}%) | BB %B=${fmt(s.bbPercentB)} width=${fmt(s.bbWidthPct)}% | Supertrend=${fmt(s.supertrend)} ${s.supertrendState}")
                appendLine("[$tf] Flow/Levels: VWAP=${fmt(s.vwap)} distance=${fmt(s.vwapDistancePct)}% | VolumeRatio20=${fmt(s.volumeRatio20)} ${s.volumeState} | OBV=${s.obvState} | Ichimoku=${s.ichimokuState} Tenkan=${fmt(s.ichimokuTenkan)} Kijun=${fmt(s.ichimokuKijun)} Cloud=${fmt(s.ichimokuCloudBottom)}..${fmt(s.ichimokuCloudTop)}")
                appendLine("[$tf] Levels: Pivot=${fmt(s.pivot)} R1=${fmt(s.resistance1)} R2=${fmt(s.resistance2)} S1=${fmt(s.support1)} S2=${fmt(s.support2)} ${s.pivotState} | indicator-score=${s.indicatorScore}")
            }
            appendLine()
            appendLine("### Confluence")
            val history = TradingViewSignalHistory.stats()
            appendLine("Technical score=${fmt(fusion.technicalScore)} | Indicator score=${fmt(normalizedIndicatorScore)} | SMC score=${fmt(fusion.smcScore)}")
            appendLine("${fusion.reason}")
            appendLine("History: records=" + history["records"] + " resolved=" + history["resolved"] + " win-rate=" + history["win_rate_pct"] + "% avg-return=" + history["avg_return_pct"] + "%")
            appendLine()
            appendLine("### Decision Gate")
            appendLine(if (fusion.gate == "PASS") "PASS — signal has multi-timeframe confluence." else "WAIT — ${fusion.gate}; do not treat this as an actionable BUY/SELL.")
            appendLine("Signal is analytical evidence from TradingView candles, not a guaranteed trade outcome.")
        }
    }

    suspend fun fetchFormatted(rawSymbol: String, strategy: String = "all"): String {
        val d = fetch(rawSymbol)
        d["error"]?.let { return "❌ Strategy Signal: $it" }
        val want = strategy.trim().lowercase()
        return buildString {
            appendLine("## 📐 Strategy Signals — ${d["symbol"]} (${d["timeframe"]}) [${d["source"]}]")
            appendLine("close=${d["close"]} | consensus=${d["consensus_signal"]} (score=${d["consensus_score"]}, buy=${d["consensus_buy_count"]}, sell=${d["consensus_sell_count"]})")
            appendLine("TV data: ${d["tv_bars"]} bars | quality=${d["data_quality_pct"]}% | last-bar-age=${d["tv_last_bar_age_sec"]}s | signal-confidence=${d["signal_confidence_pct"]}%")
            appendLine("P7.7: ${d["signal_generation_status"]} | signal_ready=${d["signal_ready"]} | risk_gate_eligible=${d["risk_gate_eligible"]} | promotion=${d["promotion_source"]}")
            appendLine()
            if (want == "all" || want == "tsmom") {
                appendLine("### 1) Time-Series Momentum (ROC ${d["tsmom_lookback"]} แท่ง)")
                appendLine("tsmom_signal=${d["tsmom_signal"]} | roc=${d["tsmom_roc_pct"]}%")
            }
            if (want == "all" || want == "trend") {
                appendLine("### 2) Trend Following (EMA50/200)")
                appendLine("trend_signal=${d["trend_signal"]} | state=${d["trend_state"]} | ema50=${d["trend_ema50"]} | ema200=${d["trend_ema200"]}")
            }
            if (want == "all" || want == "reversal") {
                appendLine("### 3) Short-Term Reversal (RSI + BB)")
                appendLine("reversal_signal=${d["reversal_signal"]} | rsi=${d["reversal_rsi"]} | bb_upper=${d["reversal_bb_upper"]} | bb_lower=${d["reversal_bb_lower"]}")
            }
            if (want == "all" || want == "donchian") {
                appendLine("### 4) Donchian Breakout (20 แท่ง)")
                appendLine("donchian_signal=${d["donchian_signal"]} | upper=${d["donchian_upper"]} | mid=${d["donchian_mid"]} | lower=${d["donchian_lower"]}")
            }
            if (want == "all" || want == "w52high") {
                appendLine("### 5) 52-Weeks High Proximity")
                appendLine("w52_signal=${d["w52_signal"]} | period_high=${d["w52_high"]} | proximity=${d["w52_proximity_pct"]}%")
            }
            appendLine()
            appendLine("_Consensus: STRONG_BUY/BUY เมื่อ score ≥ +1 (STRONG ≥ +3), SELL/STRONG_SELL เมื่อ ≤ -1 — คำนวณในเครื่องจากแท่งเทียน TradingView เท่านั้น_")
        }
    }

    // ─── Math helpers ──────────────────────────────────────────────────────

    private fun fmt(v: Double): String = "%.4f".format(v).trimEnd('0').trimEnd('.')

    private fun ema(values: List<Double>, period: Int): List<Double> {
        if (values.size < period) return List(values.size) { Double.NaN }
        val k = 2.0 / (period + 1)
        val out = ArrayList<Double>(values.size)
        var prev = values.take(period).average()
        repeat(period - 1) { out.add(Double.NaN) }
        out.add(prev)
        for (i in period until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out.add(prev)
        }
        return out
    }

    private fun rsi(closes: List<Double>, period: Int): Double? {
        if (closes.size <= period) return null
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff > 0) gain += diff else loss -= diff
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        for (i in period + 1 until closes.size) {
            val diff = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + max(diff, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-diff, 0.0)) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun bollinger(closes: List<Double>, period: Int, mult: Double): Triple<Double, Double, Double> {
        val window = closes.takeLast(period)
        val mean = window.average()
        val sd = sqrt(window.sumOf { (it - mean).pow(2) } / period)
        return Triple(mean + mult * sd, mean, mean - mult * sd)
    }

    @Suppress("unused")
    private fun atr(candles: List<Candle>, period: Int): Double? {
        if (candles.size <= period) return null
        var sum = 0.0
        for (i in candles.size - period until candles.size) {
            val h = candles[i].high; val l = candles[i].low; val pc = candles[i - 1].close
            sum += maxOf(h - l, abs(h - pc), abs(l - pc))
        }
        return sum / period
    }

    @Suppress("unused")
    private fun clamp(v: Double, lo: Double, hi: Double) = min(max(v, lo), hi)

}

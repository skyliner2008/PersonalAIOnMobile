package com.example.personalaibot.tools.trading

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.math.*

// ============================================================================
// DATA MODELS
// ============================================================================

data class Candle(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val timestamp: Long = 0L
)

data class SmcOrderBlock(
    val top: Double,
    val bottom: Double,
    val isBullish: Boolean,
    val mitigated: Boolean = false,
    val hasFVG: Boolean = false,
    val timestamp: Long = 0L // Added for visualization
)

data class SmcLiquidityZone(
    val price: Double,
    val isHigh: Boolean,
    val strength: Int,           // Number of equal touches
    val confluenceScore: Int = 0 // Stars (0-5)
)

data class SmcFairValueGap(
    val top: Double,
    val bottom: Double,
    val isBullish: Boolean,
    val size: Double,             // Gap size in price
    val timestamp: Long = 0L      // Added for visualization
)

data class SmcSweepSignal(
    val timeframe: String,
    val direction: String,       // "BULLISH" or "BEARISH"
    val price: Double,
    val obTop: Double,
    val obBottom: Double,
    val barsAgo: Int = 0,
    val timestamp: Long = 0L,
    val dataSource: String = "UNKNOWN"
)

data class SmcAnalysisResult(
    val symbol: String,
    val interval: String,
    val currentPrice: Double,
    // Market Structure
    val structureDirection: String,     // "BULLISH", "BEARISH", "NEUTRAL"
    val structureHigh: Double,
    val structureLow: Double,
    val lastStructureEvent: String,     // "BOS_UP", "BOS_DOWN", "CHOCH_UP", "CHOCH_DOWN", ""
    // Zones
    val bullishOBs: List<SmcOrderBlock>,
    val bearishOBs: List<SmcOrderBlock>,
    val fvgs: List<SmcFairValueGap>,
    val liquidityZones: List<SmcLiquidityZone>,
    // Premium/Discount
    val premiumBot: Double,
    val discountTop: Double,
    val equilibrium: Double,
    val priceZone: String,              // "PREMIUM", "DISCOUNT", "EQUILIBRIUM"
    // Technical
    val atr: Double,
    val attackForce: Boolean,           // High-momentum candle detected
    // Provenance
    val candleSource: String,
    val priceSource: String,
    val overrideAccepted: Boolean,
    val candlesCount: Int
)

data class CandleFetchResult(
    val candles: List<Candle>,
    val source: String
)

data class SmcMtfSweepFrame(
    val timeframe: String,
    val source: String,
    val barsCount: Int,
    val signals: List<SmcSweepSignal>
)

data class SmcMtfLiquidityFrame(
    val timeframe: String,
    val source: String,
    val barsCount: Int,
    val zones: List<SmcLiquidityZone>
)

class StrictSourceMismatchException(message: String) : IllegalStateException(message)

// ============================================================================
// SMC API SERVICE
// ============================================================================

/**
 * SmcApiService — ดึง OHLCV จาก Binance แล้วคำนวณ SMC indicators ใน Kotlin
 * อิงจาก logic ของ indicator "SMC & Multi-TF Order Blocks Sweeps V8.3"
 *
 * Concepts ที่ implement:
 *  - Swing High/Low Detection
 *  - Market Structure (BOS / CHoCH)
 *  - Order Blocks with FVG confirmation
 *  - Fair Value Gaps (FVG)
 *  - Liquidity Zones (Equal Highs/Lows + Swing Liquidity)
 *  - Premium/Discount/Equilibrium Zones
 *  - Multi-Timeframe Sweeps
 *  - Confluence Scoring (Stars)
 */
class SmcApiService(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // Interval maps
    private val binanceIntervalMap = mapOf(
        "1m" to "1m", "3m" to "3m", "5m" to "5m", "15m" to "15m",
        "30m" to "30m", "1h" to "1h", "2h" to "2h", "4h" to "4h",
        "6h" to "6h", "12h" to "12h", "1d" to "1d", "1w" to "1w"
    )

    private val yahooIntervalMap = mapOf(
        "1m" to "1m", "5m" to "5m", "15m" to "15m", "30m" to "30m",
        "1h" to "1h", "4h" to "1h", "1d" to "1d", "1w" to "1wk"
    )

    private data class YahooRequestPlan(
        val baseInterval: String,
        val range: String,
        val aggregateToMillis: Long?
    )

    fun expectedPrimarySource(symbol: String): String {
        val normalized = normalizeSymbol(symbol)
        return if (isPossibleBinanceSymbol(normalized)) "BINANCE" else "YAHOO"
    }

    private fun expectedSourceSet(symbol: String): Set<String> {
        val normalized = normalizeSymbol(symbol)
        return when {
            isPossibleBinanceSymbol(normalized) -> setOf("BINANCE")
            normalized.contains("XAU") || normalized.contains("GOLD") || normalized == "GCF" ->
                setOf("YAHOO", "BINANCE_PAXG")
            else -> setOf("YAHOO")
        }
    }

    // ─── Data Fetching ────────────────────────────────────────────────────────

    suspend fun fetchCandles(symbol: String, interval: String, limit: Int = 300): List<Candle> {
        return fetchCandlesWithSource(symbol, interval, limit).candles
    }

    suspend fun fetchCandlesWithSource(symbol: String, interval: String, limit: Int = 300): CandleFetchResult {
        val minBars = recommendedMinBars(interval)
        val targetBars = max(limit, minBars)
        val sym = normalizeSymbol(symbol)
        val cached = OhlcvCentralStore.getAny(sym, interval, targetBars)
        var bestFallback = if (cached != null) {
            CandleFetchResult(cached.second, cached.first)
        } else {
            CandleFetchResult(emptyList(), "NONE")
        }
        
        // 1. Try Binance first if it looks like a crypto pair (standard Binance format or ends with USDT/BTC/ETH)
        if (isPossibleBinanceSymbol(sym)) {
            val candles = fetchCandlesFromBinance(sym, interval, targetBars)
            if (candles.size >= minBars) {
                val result = CandleFetchResult(candles.takeLast(targetBars), "BINANCE")
                OhlcvCentralStore.put(sym, interval, result.source, result.candles)
                return result
            }
            if (candles.isNotEmpty()) bestFallback = CandleFetchResult(candles, "BINANCE")
        }

        // 2. Fallback to Yahoo Finance (Good for Gold, Forex, Stocks)
        val candles = fetchCandlesFromYahoo(symbol, interval, targetBars)
        if (candles.size >= minBars) {
            val result = CandleFetchResult(candles.takeLast(targetBars), "YAHOO")
            OhlcvCentralStore.put(sym, interval, result.source, result.candles)
            return result
        }
        if (candles.isNotEmpty() && candles.size > bestFallback.candles.size) {
            bestFallback = CandleFetchResult(candles, "YAHOO")
        }

        // 3. Last Resort for Gold: Fallback to Binance (PAXGUSDT) if Yahoo is insufficient
        if (sym.contains("XAU") || sym.contains("GOLD") || sym == "GCF") {
            val paxgCandles = fetchCandlesFromBinance("PAXGUSDT", interval, targetBars)
            if (paxgCandles.size >= minBars) {
                val result = CandleFetchResult(paxgCandles.takeLast(targetBars), "BINANCE_PAXG")
                OhlcvCentralStore.put(sym, interval, result.source, result.candles)
                return result
            }
            if (paxgCandles.isNotEmpty() && paxgCandles.size > bestFallback.candles.size) {
                bestFallback = CandleFetchResult(paxgCandles, "BINANCE_PAXG")
            }
        }
        val fallbackResult = CandleFetchResult(bestFallback.candles.takeLast(targetBars), bestFallback.source)
        if (fallbackResult.candles.isNotEmpty() && fallbackResult.source != "NONE") {
            OhlcvCentralStore.put(sym, interval, fallbackResult.source, fallbackResult.candles)
        }
        return fallbackResult
    }

    private suspend fun fetchCandlesFromBinance(symbol: String, interval: String, limit: Int): List<Candle> {
        return try {
            val tf = binanceIntervalMap[interval] ?: interval
            val response = client.get("https://api.binance.com/api/v3/klines") {
                parameter("symbol", symbol)
                parameter("interval", tf)
                parameter("limit", limit.coerceAtMost(1000))
                timeout { requestTimeoutMillis = 10_000 }
            }
            if (!response.status.isSuccess()) return emptyList()

            json.parseToJsonElement(response.bodyAsText()).jsonArray.map { row ->
                val r = row.jsonArray
                Candle(
                    open  = r[1].jsonPrimitive.content.toDouble(),
                    high  = r[2].jsonPrimitive.content.toDouble(),
                    low   = r[3].jsonPrimitive.content.toDouble(),
                    close = r[4].jsonPrimitive.content.toDouble(),
                    volume = r[5].jsonPrimitive.content.toDouble(),
                    timestamp = r[0].jsonPrimitive.long
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun fetchCandlesFromYahoo(symbol: String, interval: String, targetBars: Int): List<Candle> {
        return try {
            val ySym = normalizeYahooSymbol(symbol)
            val plan = buildYahooRequestPlan(interval, targetBars)
            val tf = plan.baseInterval

            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$ySym"
            val response = client.get(url) {
                parameter("interval", tf)
                parameter("range", plan.range)
                header("User-Agent", "Mozilla/5.0")
                timeout { requestTimeoutMillis = 15_000 }
            }
            if (!response.status.isSuccess()) return emptyList()

            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val result = root["chart"]?.jsonObject?.get("result")?.jsonArray?.get(0)?.jsonObject ?: return emptyList()
            val timestamp = result["timestamp"]?.jsonArray ?: return emptyList()
            val indicators = result["indicators"]?.jsonObject?.get("quote")?.jsonArray?.get(0)?.jsonObject ?: return emptyList()
            
            val o = indicators["open"]?.jsonArray ?: return emptyList()
            val h = indicators["high"]?.jsonArray ?: return emptyList()
            val l = indicators["low"]?.jsonArray ?: return emptyList()
            val c = indicators["close"]?.jsonArray ?: return emptyList()
            val v = indicators["volume"]?.jsonArray ?: return emptyList()

            val rawCandles = mutableListOf<Candle>()
            for (i in 0 until timestamp.size) {
                val closeVal = c[i].jsonPrimitive.doubleOrNull ?: continue
                rawCandles.add(Candle(
                    open  = o[i].jsonPrimitive.doubleOrNull ?: closeVal,
                    high  = h[i].jsonPrimitive.doubleOrNull ?: closeVal,
                    low   = l[i].jsonPrimitive.doubleOrNull ?: closeVal,
                    close = closeVal,
                    volume = v[i].jsonPrimitive.doubleOrNull ?: 0.0,
                    timestamp = timestamp[i].jsonPrimitive.long * 1000
                ))
            }
            val candles = if (plan.aggregateToMillis != null) {
                aggregateCandlesByBucket(rawCandles, plan.aggregateToMillis)
            } else {
                rawCandles
            }
            candles.takeLast(targetBars)
        } catch (e: Exception) { emptyList() }
    }

    private fun buildYahooRequestPlan(interval: String, targetBars: Int): YahooRequestPlan {
        val normalized = interval.lowercase()
        val baseTf = yahooIntervalMap[normalized] ?: "1h"
        val targetMillis = intervalToMillis(normalized)
        val baseMillis = intervalToMillis(baseTf)
        val aggregateToMillis = if (targetMillis > baseMillis) targetMillis else null
        val requiredBaseBars = if (aggregateToMillis != null) {
            val factor = max(1L, aggregateToMillis / baseMillis)
            (targetBars.toLong() * factor).toInt()
        } else {
            targetBars
        }
        val range = chooseYahooRange(baseTf, requiredBaseBars)
        return YahooRequestPlan(baseTf, range, aggregateToMillis)
    }

    private fun chooseYahooRange(baseTf: String, requiredBars: Int): String {
        val minutesPerBar = when (baseTf) {
            "1m" -> 1
            "5m" -> 5
            "15m" -> 15
            "30m" -> 30
            "1h" -> 60
            "1d" -> 1440
            "1wk" -> 10080
            else -> 60
        }
        val requiredDays = ceil((requiredBars * minutesPerBar) / 1440.0).toInt()
        return when {
            requiredDays <= 5 -> "5d"
            requiredDays <= 30 -> "1mo"
            requiredDays <= 90 -> "3mo"
            requiredDays <= 180 -> "6mo"
            requiredDays <= 365 -> "1y"
            requiredDays <= 730 -> "2y"
            requiredDays <= 1825 -> "5y"
            else -> "max"
        }
    }

    private fun aggregateCandlesByBucket(candles: List<Candle>, bucketMillis: Long): List<Candle> {
        if (candles.isEmpty()) return emptyList()
        val grouped = candles.sortedBy { it.timestamp }
            .groupBy { it.timestamp / bucketMillis }
            .toSortedMap()

        val aggregated = mutableListOf<Candle>()
        for (group in grouped.values) {
            if (group.isEmpty()) continue
            aggregated.add(
                Candle(
                    open = group.first().open,
                    high = group.maxOf { it.high },
                    low = group.minOf { it.low },
                    close = group.last().close,
                    volume = group.sumOf { it.volume },
                    timestamp = group.first().timestamp
                )
            )
        }
        return aggregated
    }

    private fun intervalToMillis(interval: String): Long {
        return when (interval.lowercase()) {
            "1m" -> 60_000L
            "3m" -> 180_000L
            "5m" -> 300_000L
            "15m" -> 900_000L
            "30m" -> 1_800_000L
            "1h" -> 3_600_000L
            "2h" -> 7_200_000L
            "4h" -> 14_400_000L
            "6h" -> 21_600_000L
            "12h" -> 43_200_000L
            "1d", "d" -> 86_400_000L
            "1w", "w", "1wk" -> 604_800_000L
            else -> 3_600_000L
        }
    }

    private fun recommendedMinBars(interval: String): Int {
        return when (interval.lowercase()) {
            "4h", "1d", "1w" -> 300
            else -> 150
        }
    }

    private fun isPossibleBinanceSymbol(s: String): Boolean {
        // Strict crypto-like detection only, to avoid routing Forex/Gold to Binance accidentally.
        val upper = s.uppercase()
        if (upper.contains("XAU") || upper.contains("XAG") || upper.contains("GOLD")) return false
        if (upper.length == 6 && upper.all { it.isLetter() }) return false // likely FX pair
        return upper.all { it.isLetterOrDigit() } &&
            (upper.endsWith("USDT") || upper.endsWith("BTC") || upper.endsWith("ETH") || upper.endsWith("BNB"))
    }

    private fun normalizeYahooSymbol(symbol: String): String {
        val s = symbol.uppercase().replace("/", "").replace("-", "")
        return when {
            // 1. Gold/Silver/Commodity (Highest Priority)
            s.contains("XAU") || s.contains("GOLD") || s == "GCF" || s == "GC=F" -> "XAUUSD=X" 
            s.contains("XAG") || s.contains("SILVER") -> "XAGUSD=X"
            s == "USOIL" || s == "WTI" || s == "CL=F" || s == "CLF" -> "CL=F"
            
            // 2. Forex (6 letters) -> Always =X
            s.length == 6 && s.all { it.isLetter() } -> "$s=X"
            
            // 3. Thai Stocks (Usually 3-5 letters) -> .BK
            // We only tag .BK if it's NOT a 6-letter forex pair
            s.length in 3..5 && s.all { it.isLetter() } && !s.contains(".") && !s.contains("=") -> "$s.BK"
            
            else -> s
        }
    }

    private fun normalizeSymbol(symbol: String): String {
        var s = symbol.uppercase().replace("-", "").replace("/", "")
        // If it looks like a Yahoo symbol (contains = or ^), don't touch it
        if (s.contains("=") || s.contains("^")) return s

        // Normalize Gold
        if (s.contains("XAU") || s.contains("GOLD") || s == "GCF" || s == "PAXG") s = "XAUUSD"
        
        // Auto-append USDT if no quote currency detected AND it's not a known commodity
        val commodities = listOf("XAUUSD", "XAGUSD", "GOLD", "SILVER", "CLF", "GCF")
        return if (!s.endsWith("USDT") && !s.endsWith("BTC") &&
                   !s.endsWith("ETH") && !s.endsWith("BNB") && 
                   !commodities.contains(s)) "$s USDT".replace(" ", "")
        else s
    }

    // ─── Core SMC Algorithms ──────────────────────────────────────────────────

    /**
     * ATR (Average True Range) — ใช้ period=14 เหมือน indicator ต้นฉบับ
     */
    fun calcATR(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size < 15) return 0.0
        val trs = candles.zipWithNext { prev, curr ->
            maxOf(
                curr.high - curr.low,
                abs(curr.high - prev.close),
                abs(curr.low - prev.close)
            )
        }
        return trs.takeLast(period).average()
    }

    /**
     * Swing Detection — หาจุด Swing High และ Swing Low
     * @param len  lookback/leadout bars (ตรงกับ `length` input ใน indicator)
     * Returns: List of (index, price) pairs
     */
    fun detectSwings(candles: List<Candle>, len: Int = 5): Pair<List<Pair<Int, Double>>, List<Pair<Int, Double>>> {
        val highs = mutableListOf<Pair<Int, Double>>()
        val lows  = mutableListOf<Pair<Int, Double>>()

        for (i in len until candles.size - len) {
            val h = candles[i].high
            val l = candles[i].low
            var isSwingHigh = true
            var isSwingLow  = true

            for (j in (i - len)..(i + len)) {
                if (j == i) continue
                if (candles[j].high >= h) isSwingHigh = false
                if (candles[j].low  <= l) isSwingLow  = false
            }
            if (isSwingHigh) highs.add(i to h)
            if (isSwingLow)  lows.add(i to l)
        }
        return highs to lows
    }

    /**
     * Market Structure Detection — BOS / CHoCH (ตาม structureDirection ใน indicator)
     * Returns: (direction, structureHigh, structureLow, lastEvent)
     */
    fun detectMarketStructure(candles: List<Candle>, prd: Int = 20): Quad<String, Double, Double, String> {
        if (candles.size < prd * 2) return Quad("NEUTRAL", 0.0, 0.0, "")

        val (swingHighs, swingLows) = detectSwings(candles, prd / 2)
        if (swingHighs.isEmpty() || swingLows.isEmpty()) return Quad("NEUTRAL", 0.0, 0.0, "")

        // Sort by index
        val sortedHighs = swingHighs.sortedBy { it.first }
        val sortedLows  = swingLows.sortedBy { it.first }

        var structureHigh = sortedHighs.last().second
        var structureLow  = sortedLows.last().second
        var direction     = 0  // 0=init, 1=bearish, 2=bullish
        var lastEvent     = ""

        val currentPrice = candles.last().close

        // Walk through swing points to determine structure
        if (sortedHighs.size >= 2 && sortedLows.size >= 2) {
            val prevHigh = sortedHighs[sortedHighs.size - 2].second
            val prevLow  = sortedLows[sortedLows.size - 2].second
            val lastHigh = sortedHighs.last().second
            val lastLow  = sortedLows.last().second
            val lastHighIdx = sortedHighs.last().first
            val lastLowIdx  = sortedLows.last().first

            // Higher Highs + Higher Lows = Bullish structure
            if (lastHigh > prevHigh && lastLow > prevLow) {
                direction = 2
                // BOS up if price is above previous high
                if (currentPrice > prevHigh) lastEvent = "BOS_UP"
            }
            // Lower Highs + Lower Lows = Bearish structure
            else if (lastHigh < prevHigh && lastLow < prevLow) {
                direction = 1
                if (currentPrice < prevLow) lastEvent = "BOS_DOWN"
            }
            // Structure flip detection (CHoCH)
            else if (lastLowIdx > lastHighIdx && currentPrice < lastLow) {
                direction = 1
                lastEvent = "CHOCH_DOWN"
            } else if (lastHighIdx > lastLowIdx && currentPrice > lastHigh) {
                direction = 2
                lastEvent = "CHOCH_UP"
            }
        }

        val dirStr = when (direction) {
            1 -> "BEARISH"
            2 -> "BULLISH"
            else -> "NEUTRAL"
        }

        return Quad(dirStr, structureHigh, structureLow, lastEvent)
    }

    /**
     * Fair Value Gap Detection
     * Bullish FVG: candle[i-2].high < candle[i].low (gap up)
     * Bearish FVG: candle[i-2].low > candle[i].high (gap down)
     */
    fun detectFVGs(candles: List<Candle>, atr: Double, atrMulti: Double = 0.25): List<SmcFairValueGap> {
        val fvgs = mutableListOf<SmcFairValueGap>()
        val threshold = atr * atrMulti

        for (i in 2 until candles.size) {
            val c0 = candles[i]
            val c1 = candles[i - 1]
            val c2 = candles[i - 2]

            // Bullish FVG: low[i] > high[i-2] and close[i-1] > high[i-2]
            val bullGap = c0.low - c2.high
            if (bullGap > threshold && c1.close > c2.high) {
                fvgs.add(SmcFairValueGap(c0.low, c2.high, true, bullGap, c1.timestamp))
            }

            // Bearish FVG: high[i] < low[i-2] and close[i-1] < low[i-2]
            val bearGap = c2.low - c0.high
            if (bearGap > threshold && c1.close < c2.low) {
                fvgs.add(SmcFairValueGap(c2.low, c0.high, false, bearGap, c1.timestamp))
            }
        }
        return fvgs.takeLast(10)
    }

    /**
     * Order Block Detection — ตาม SMC strict logic ใน indicator
     * - Bullish OB: Last bearish candle before a bullish structural break (+ FVG filter)
     * - Bearish OB: Last bullish candle before a bearish structural break (+ FVG filter)
     */
    fun detectOrderBlocks(
        candles: List<Candle>,
        swingLen: Int = 5,
        useBody: Boolean = true,
        structureDir: String,
        fvgs: List<SmcFairValueGap>
    ): Pair<List<SmcOrderBlock>, List<SmcOrderBlock>> {
        val bullishOBs = mutableListOf<SmcOrderBlock>()
        val bearishOBs = mutableListOf<SmcOrderBlock>()
        val (swingHighs, swingLows) = detectSwings(candles, swingLen)

        fun max(c: Candle) = if (useBody) maxOf(c.open, c.close) else c.high
        fun min(c: Candle) = if (useBody) minOf(c.open, c.close) else c.low

        // ── Bullish OBs: price breaks above swing high ────────────────────────
        for ((swingIdx, swingPrice) in swingHighs) {
            val breakIdx = (swingIdx + 1 until candles.size).firstOrNull {
                candles[it].close > swingPrice
            } ?: continue

            // Find lowest low between swing and break
            var lowestLow = Double.MAX_VALUE
            var lowestIdx = swingIdx
            for (i in swingIdx until breakIdx) {
                if (min(candles[i]) < lowestLow) {
                    lowestLow = min(candles[i])
                    lowestIdx = i
                }
            }

            // Find last bearish candle at or after the lowest (strict SMC)
            var obIdx = lowestIdx
            var foundBearish = false
            for (k in lowestIdx until minOf(lowestIdx + 50, breakIdx)) {
                if (candles[k].close < candles[k].open) {
                    obIdx = k
                    foundBearish = true
                    break
                }
            }
            if (!foundBearish) continue

            val obTop = max(candles[obIdx])
            val obBtm = min(candles[obIdx])

            // FVG confirmation near OB
            val hasFVG = fvgs.any { it.isBullish && it.bottom >= obBtm * 0.999 && it.top <= obTop * 1.001 }

            // Trend filter + FVG required
            val filterOk = structureDir == "BULLISH" || structureDir == "NEUTRAL"
            if (filterOk && hasFVG) {
                val mitigated = candles.drop(obIdx + 1).any { c -> c.low <= obTop && c.high >= obBtm }
                if (!mitigated) {
                    bullishOBs.add(SmcOrderBlock(obTop, obBtm, true, false, true, candles[obIdx].timestamp))
                }
            }
        }

        // ── Bearish OBs: price breaks below swing low ─────────────────────────
        for ((swingIdx, swingPrice) in swingLows) {
            val breakIdx = (swingIdx + 1 until candles.size).firstOrNull {
                candles[it].close < swingPrice
            } ?: continue

            var highestHigh = -Double.MAX_VALUE
            var highestIdx = swingIdx
            for (i in swingIdx until breakIdx) {
                if (max(candles[i]) > highestHigh) {
                    highestHigh = max(candles[i])
                    highestIdx = i
                }
            }

            var obIdx = highestIdx
            var foundBullish = false
            for (k in highestIdx until minOf(highestIdx + 50, breakIdx)) {
                if (candles[k].close > candles[k].open) {
                    obIdx = k
                    foundBullish = true
                    break
                }
            }
            if (!foundBullish) continue

            val obTop = max(candles[obIdx])
            val obBtm = min(candles[obIdx])

            val hasFVG = fvgs.any { !it.isBullish && it.bottom >= obBtm * 0.999 && it.top <= obTop * 1.001 }

            val filterOk = structureDir == "BEARISH" || structureDir == "NEUTRAL"
            if (filterOk && hasFVG) {
                val mitigated = candles.drop(obIdx + 1).any { c -> c.high >= obBtm && c.low <= obTop }
                if (!mitigated) {
                    bearishOBs.add(SmcOrderBlock(obTop, obBtm, false, false, true, candles[obIdx].timestamp))
                }
            }
        }

        return bullishOBs.takeLast(5) to bearishOBs.takeLast(5)
    }

    /**
     * Liquidity Zone Detection — Equal Highs / Equal Lows + Swing Liquidity
     * ใช้ threshold 0.1% เหมือน indicator ต้นฉบับ
     */
    fun detectLiquidityZones(
        candles: List<Candle>,
        lookback: Int = 10,
        threshold: Double = 0.1,
        structureHigh: Double = 0.0,
        structureLow: Double = 0.0,
        atr: Double = 0.0,
        bullishOBs: List<SmcOrderBlock> = emptyList(),
        bearishOBs: List<SmcOrderBlock> = emptyList(),
        premiumBot: Double = 0.0,
        discountTop: Double = 0.0,
        structureDir: String = "NEUTRAL"
    ): List<SmcLiquidityZone> {
        val zones = mutableListOf<SmcLiquidityZone>()
        val recent = candles.takeLast(lookback + 1)
        val lastClose = candles.last().close

        for (i in 1 until recent.size) {
            val curHigh = recent[i].high
            val curLow  = recent[i].low
            val thrH = curHigh * (threshold / 100.0)
            val thrL = curLow  * (threshold / 100.0)

            // Count equal highs
            var eqH = 0
            for (j in i + 1 until recent.size) {
                if (abs(recent[j].high - curHigh) <= thrH) eqH++
            }
            if (eqH >= 1 && curHigh > lastClose) {
                val dup = zones.any { it.isHigh && abs(it.price - curHigh) <= thrH }
                if (!dup) {
                    val score = calcConfluenceScore(curHigh, true, structureHigh, structureLow,
                        atr, bullishOBs, bearishOBs, premiumBot, discountTop, structureDir)
                    zones.add(SmcLiquidityZone(curHigh, true, eqH, score))
                }
            }

            // Count equal lows
            var eqL = 0
            for (j in i + 1 until recent.size) {
                if (abs(recent[j].low - curLow) <= thrL) eqL++
            }
            if (eqL >= 1 && curLow < lastClose) {
                val dup = zones.any { !it.isHigh && abs(it.price - curLow) <= thrL }
                if (!dup) {
                    val score = calcConfluenceScore(curLow, false, structureHigh, structureLow,
                        atr, bullishOBs, bearishOBs, premiumBot, discountTop, structureDir)
                    zones.add(SmcLiquidityZone(curLow, false, eqL, score))
                }
            }
        }

        // Add Swing Liquidity (major pivots)
        val (swingHighs, swingLows) = detectSwings(candles, 10)
        swingHighs.lastOrNull()?.let { (_, h) ->
            if (h > lastClose && zones.none { it.isHigh && abs(it.price - h) < h * 0.001 }) {
                val score = calcConfluenceScore(h, true, structureHigh, structureLow,
                    atr, bullishOBs, bearishOBs, premiumBot, discountTop, structureDir)
                zones.add(SmcLiquidityZone(h, true, 2, score))
            }
        }
        swingLows.lastOrNull()?.let { (_, l) ->
            if (l < lastClose && zones.none { !it.isHigh && abs(it.price - l) < l * 0.001 }) {
                val score = calcConfluenceScore(l, false, structureHigh, structureLow,
                    atr, bullishOBs, bearishOBs, premiumBot, discountTop, structureDir)
                zones.add(SmcLiquidityZone(l, false, 2, score))
            }
        }

        return zones.sortedByDescending { it.confluenceScore }.take(10)
    }

    /**
     * Confluence Score (Stars) — คำนวณคะแนน zone ตาม 5 ปัจจัย
     * เหมือน Zone Confidence Stars ใน indicator
     */
    private fun calcConfluenceScore(
        price: Double, isHigh: Boolean,
        structureHigh: Double, structureLow: Double,
        atr: Double,
        bullishOBs: List<SmcOrderBlock>, bearishOBs: List<SmcOrderBlock>,
        premiumBot: Double, discountTop: Double,
        structureDir: String
    ): Int {
        var score = 1  // Base

        // 1. OB Confluence
        val obOverlap = if (isHigh) {
            bearishOBs.any { price <= it.top * 1.001 && price >= it.bottom * 0.999 }
        } else {
            bullishOBs.any { price <= it.top * 1.001 && price >= it.bottom * 0.999 }
        }
        if (obOverlap) score += 2

        // 2. Structure Confluence
        if (atr > 0) {
            val nearStructHigh = abs(price - structureHigh) < atr * 0.5
            val nearStructLow  = abs(price - structureLow)  < atr * 0.5
            if (nearStructHigh || nearStructLow) score += 1
        }

        // 3. Premium/Discount Confluence
        if (isHigh && premiumBot > 0 && price > premiumBot) score += 1
        if (!isHigh && discountTop > 0 && price < discountTop) score += 1

        // 4. Trend Alignment
        if (isHigh && structureDir == "BEARISH") score += 1
        if (!isHigh && structureDir == "BULLISH") score += 1

        return score.coerceAtMost(5)
    }

    /**
     * MTF Sweep Detection — ตรวจจับ Sweep signals ข้าม Timeframe
     * Logic จาก f_sweepSignals() ใน indicator
     */
    fun detectSweeps(
        candles: List<Candle>,
        timeframeLabel: String,
        reclaimFactor: Double = 0.50,
        minWickATRMult: Double = 0.30,
        dataSource: String = "UNKNOWN",
        strictRecent: Boolean = true,
        maxBarsAgo: Int = 5
    ): List<SmcSweepSignal> {
        if (candles.size < 10) return emptyList()
        val sweeps = mutableListOf<SmcSweepSignal>()
        val atr = calcATR(candles)
        if (atr <= 0) return emptyList()

        // Check last 20 candles for sweep signals
        val lookback = minOf(20, candles.size - 1)
        for (i in candles.size - lookback until candles.size) {
            val c = candles[i]

            // ── Bullish Sweep: wick ลงใต้ bearish OB แล้ว reclaim กลับ ────────
            val recentBearIdx = (i - 1 downTo maxOf(0, i - 50))
                .firstOrNull { candles[it].close < candles[it].open }
            if (recentBearIdx != null) {
                val bearCandle = candles[recentBearIdx]
                val bullTop = bearCandle.high
                val bullBtm = minOf(bearCandle.open, bearCandle.close)
                val bullWidth = bullTop - bullBtm
                if (bullWidth > 0) {
                    val bullReclaim = bullBtm + reclaimFactor * bullWidth
                    val wickBull = bullBtm - c.low
                    if (c.low < bullBtm && c.close > bullReclaim && wickBull > minWickATRMult * atr) {
                        sweeps.add(
                            SmcSweepSignal(
                                timeframe = timeframeLabel,
                                direction = "BULLISH",
                                price = c.close,
                                obTop = bullTop,
                                obBottom = bullBtm,
                                barsAgo = candles.lastIndex - i,
                                timestamp = c.timestamp,
                                dataSource = dataSource
                            )
                        )
                    }
                }
            }

            // ── Bearish Sweep: wick ขึ้นเหนือ bullish OB แล้ว reclaim ลง ───────
            val recentBullIdx = (i - 1 downTo maxOf(0, i - 50))
                .firstOrNull { candles[it].close > candles[it].open }
            if (recentBullIdx != null) {
                val bullCandle = candles[recentBullIdx]
                val bearTop = maxOf(bullCandle.open, bullCandle.close)
                val bearBtm = bullCandle.low
                val bearWidth = bearTop - bearBtm
                if (bearWidth > 0) {
                    val bearReclaim = bearTop - reclaimFactor * bearWidth
                    val wickBear = c.high - bearTop
                    if (c.high > bearTop && c.close < bearReclaim && wickBear > minWickATRMult * atr) {
                        sweeps.add(
                            SmcSweepSignal(
                                timeframe = timeframeLabel,
                                direction = "BEARISH",
                                price = c.close,
                                obTop = bearTop,
                                obBottom = bearBtm,
                                barsAgo = candles.lastIndex - i,
                                timestamp = c.timestamp,
                                dataSource = dataSource
                            )
                        )
                    }
                }
            }
        }
        val filtered = if (strictRecent) sweeps.filter { it.barsAgo <= maxBarsAgo } else sweeps
        return filtered.takeLast(3)
    }

    // ─── High-Level API ───────────────────────────────────────────────────────

    /**
     * Full SMC Analysis for a single symbol + interval
     */
    suspend fun getSmcAnalysis(
        symbol: String, 
        interval: String, 
        limit: Int = 300, 
        overridePrice: Double? = null,
        strictSource: Boolean = false
    ): SmcAnalysisResult? {
        val expectedSource = expectedPrimarySource(symbol)
        val expectedSources = expectedSourceSet(symbol)
        val fetch = fetchCandlesWithSource(symbol, interval, limit)
        if (strictSource && fetch.source !in expectedSources) {
            throw StrictSourceMismatchException(
                "Strict source violation: expected=${expectedSources.joinToString("|")}, actual=${fetch.source}, symbol=${symbol.uppercase()}, tf=$interval"
            )
        }
        val candles = fetch.candles
        if (candles.size < 150) return null

        val atr = calcATR(candles)
        val lastClose = candles.last().close
        val maxAllowedDrift = max(lastClose * 0.004, atr * 1.5) // 0.4% or 1.5 ATR
        val overrideAccepted = overridePrice != null && abs(overridePrice - lastClose) <= maxAllowedDrift
        val currentPrice = if (overrideAccepted) {
            overridePrice!!
        } else {
            lastClose
        }
        
        val result = executeSmcCalculation(
            symbol = symbol,
            interval = interval,
            candles = candles,
            currentPrice = currentPrice,
            atr = atr,
            candleSource = fetch.source,
            priceSource = if (overrideAccepted) "OVERRIDE_PRICE" else fetch.source,
            overrideAccepted = overrideAccepted
        )
        
        // Auto-update Chart State for Visualization (V15.0)
        result?.let {
            ChartStateManager.updateData(symbol, candles, it)
        }
        
        return result
    }

    private fun executeSmcCalculation(
        symbol: String,
        interval: String,
        candles: List<Candle>,
        currentPrice: Double,
        atr: Double,
        candleSource: String,
        priceSource: String,
        overrideAccepted: Boolean
    ): SmcAnalysisResult? {
        val (dir, sHigh, sLow, event) = detectMarketStructure(candles)
        val fvgs                      = detectFVGs(candles, atr)
        val (bullOBs, bearOBs)        = detectOrderBlocks(candles, structureDir = dir, fvgs = fvgs)

        // Premium/Discount
        val range       = if (sHigh > sLow) sHigh - sLow else atr * 20
        val premiumBot  = sHigh - range * 0.25
        val discountTop = sLow  + range * 0.25
        val equilibrium = sLow  + range * 0.50

        val liqZones = detectLiquidityZones(
            candles, structureHigh = sHigh, structureLow = sLow,
            atr = atr, bullishOBs = bullOBs, bearishOBs = bearOBs,
            premiumBot = premiumBot, discountTop = discountTop, structureDir = dir
        )

        val priceZone = when {
            currentPrice >= premiumBot  -> "PREMIUM"
            currentPrice <= discountTop -> "DISCOUNT"
            else                        -> "EQUILIBRIUM"
        }

        // Attack Force: last candle body > 2x ATR
        val lastCandle = candles.last()
        val finalPrice = currentPrice
        val attackForce = abs(finalPrice - lastCandle.open) > atr * 2.0

        return SmcAnalysisResult(
            symbol           = normalizeSymbol(symbol),
            interval         = interval,
            currentPrice     = finalPrice,
            structureDirection = dir,
            structureHigh    = sHigh,
            structureLow     = sLow,
            lastStructureEvent = event,
            bullishOBs       = bullOBs,
            bearishOBs       = bearOBs,
            fvgs             = fvgs.takeLast(5),
            liquidityZones   = liqZones,
            premiumBot       = premiumBot,
            discountTop      = discountTop,
            equilibrium      = equilibrium,
            priceZone        = priceZone,
            atr              = atr,
            attackForce      = attackForce,
            candleSource     = candleSource,
            priceSource      = priceSource,
            overrideAccepted = overrideAccepted,
            candlesCount     = candles.size
        )
    }

    /**
     * MTF Sweeps Analysis — ตรวจ sweep ข้ามทุก timeframe (M1, M5, M15, M30, H1, H4)
     */
    suspend fun getMTFSweepsDetailed(
        symbol: String,
        strictRecent: Boolean = true,
        maxBarsAgo: Int = 5,
        strictSource: Boolean = false
    ): List<SmcMtfSweepFrame> {
        val expectedSource = expectedPrimarySource(symbol)
        val expectedSources = expectedSourceSet(symbol)
        val timeframes = mapOf(
            "M1"  to "1m",
            "M5"  to "5m",
            "M15" to "15m",
            "M30" to "30m",
            "H1"  to "1h",
            "H4"  to "4h"
        )
        val results = mutableListOf<SmcMtfSweepFrame>()
        for ((label, tf) in timeframes) {
            val fetch = fetchCandlesWithSource(symbol, tf, 100)
            val candles = fetch.candles
            if (strictSource && fetch.source !in expectedSources) {
                continue
            }
            if (candles.isNotEmpty()) {
                val sweeps = detectSweeps(
                    candles = candles,
                    timeframeLabel = label,
                    dataSource = fetch.source,
                    strictRecent = strictRecent,
                    maxBarsAgo = maxBarsAgo
                )
                if (sweeps.isNotEmpty()) {
                    results.add(
                        SmcMtfSweepFrame(
                            timeframe = label,
                            source = fetch.source,
                            barsCount = candles.size,
                            signals = sweeps
                        )
                    )
                }
            }
        }
        return results.sortedBy { tfOrder(it.timeframe) }
    }

    suspend fun getMTFSweeps(
        symbol: String,
        strictSource: Boolean = false
    ): Map<String, List<SmcSweepSignal>> {
        return getMTFSweepsDetailed(symbol = symbol, strictSource = strictSource)
            .associate { it.timeframe to it.signals }
    }

    /**
     * MTF Liquidity Levels — Equal Highs/Lows จากหลาย timeframe (M5, M15, M30, H1, H4)
     */
    suspend fun getMTFLiquidityDetailed(
        symbol: String,
        strictSource: Boolean = false
    ): List<SmcMtfLiquidityFrame> {
        val expectedSource = expectedPrimarySource(symbol)
        val expectedSources = expectedSourceSet(symbol)
        val timeframes = mapOf(
            "M5"  to "5m",
            "M15" to "15m",
            "M30" to "30m",
            "H1"  to "1h",
            "H4"  to "4h"
        )
        val results = mutableListOf<SmcMtfLiquidityFrame>()
        for ((label, tf) in timeframes) {
            val fetch = fetchCandlesWithSource(symbol, tf, 150)
            val candles = fetch.candles
            if (strictSource && fetch.source !in expectedSources) {
                continue
            }
            if (candles.isNotEmpty()) {
                val zones = detectLiquidityZones(candles, lookback = 10)
                if (zones.isNotEmpty()) {
                    results.add(
                        SmcMtfLiquidityFrame(
                            timeframe = label,
                            source = fetch.source,
                            barsCount = candles.size,
                            zones = zones
                        )
                    )
                }
            }
        }
        return results.sortedBy { tfOrder(it.timeframe) }
    }

    suspend fun getMTFLiquidity(
        symbol: String,
        strictSource: Boolean = false
    ): Map<String, List<SmcLiquidityZone>> {
        return getMTFLiquidityDetailed(symbol)
            .associate { it.timeframe to it.zones }
    }

    private fun tfOrder(tf: String): Int = when (tf.uppercase()) {
        "M1" -> 0
        "M5" -> 1
        "M15" -> 2
        "M30" -> 3
        "H1" -> 4
        "H4" -> 5
        else -> 99
    }
}

// ─── Helper ───────────────────────────────────────────────────────────────────

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

fun formatPrice(price: Double): String =
    if (price >= 1000) "%.2f".format(price)
    else if (price >= 1) "%.4f".format(price)
    else "%.6f".format(price)

fun starsStr(score: Int): String = "★".repeat(score.coerceIn(0, 5))

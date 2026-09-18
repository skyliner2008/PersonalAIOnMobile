package com.skyliner2008.jarvis.tools.trading

import com.skyliner2008.jarvis.db.JarvisDatabaseHolder
import com.skyliner2008.jarvis.logDebug
import io.ktor.client.plugins.websocket.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
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
    val timestamp: Long = 0L,
    /**
     * แท่งนี้ปิดแล้วหรือยัง
     *
     * แท่งที่ยังก่อตัวอยู่ (แท่งล่าสุดที่ TV stream มา) มี high/low/close ที่ยังเปลี่ยนได้
     * ถ้านำไปคำนวณอินดิเคเตอร์จะได้ค่าที่ "ขยับ" ทุกครั้งที่เรียก (repainting)
     * และถ้าบันทึกลง DB แล้วอ่านกลับมารอบหน้าจะกลายเป็นแท่งสมบูรณ์ทั้งที่ไม่ใช่
     *
     * default = true เพื่อความเข้ากันได้กับ source ที่คืนเฉพาะแท่งปิด (เช่น Binance REST)
     */
    val isClosed: Boolean = true
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

    companion object {
        /** คีย์ symbol ที่ใช้เก็บใน OHLCV store (TvCandle.symbol) — ใช้ร่วมกับงานดูแลฐานข้อมูล */
        fun normalizeSymbolKey(symbol: String): String {
            var s = symbol.uppercase().replace("-", "").replace("/", "")
            // If it looks like a Yahoo symbol (contains = or ^), don't touch it
            if (s.contains("=") || s.contains("^")) return s
            // มี exchange prefix แล้ว (เช่น TVC:DXY, TVC:US10Y) — ผู้เรียกระบุ instrument ชัดเจน ห้ามแก้
            // เดิมหลุดไปถึงบรรทัดท้ายที่ต่อ "USDT" ให้ทุกตัว → "TVC:DXY" กลายเป็น "TVC:DXYUSDT"
            if (s.contains(":")) return s

            // Normalize Gold
            if (s.contains("XAU") || s.contains("GOLD") || s == "GCF" || s == "PAXG") s = "XAUUSD"

            // Forex pairs (exactly 6 letters, both halves are known currencies) — don't append USDT
            val forexCurrencies = setOf("USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "NZD", "CNY", "HKD", "SGD", "SEK", "NOK", "MXN", "ZAR", "TRY", "INR", "THB")
            if (s.length == 6 && s.all { it.isLetter() }) {
                val base = s.substring(0, 3)
                val quote = s.substring(3, 6)
                if (base in forexCurrencies && quote in forexCurrencies) return s
            }

            // Handle Yahoo-style crypto: BTCUSD → BTCUSDT (replace trailing USD with USDT)
            // Must NOT be a commodity and must end with exactly "USD" (not "USDT")
            val commodities = setOf("XAUUSD", "XAGUSD", "GOLD", "SILVER", "CLF", "GCF")
            if (commodities.contains(s)) return s

            if (s.endsWith("USDT") || s.endsWith("BTC") || s.endsWith("ETH") || s.endsWith("BNB")) return s

            // If ends with "USD" but not "USDT" → replace "USD" with "USDT" (e.g. BTCUSD → BTCUSDT)
            if (s.endsWith("USD") && s.length > 3) {
                return s.removeSuffix("USD") + "USDT"
            }

            // Auto-append USDT for remaining crypto-like symbols
            return "${s}USDT"
        }

        // กันดึงซ้ำตอนตลาดปิด (เสาร์-อาทิตย์/วันหยุด): estimateMissingBars เทียบกับ "เวลาปัจจุบัน" เสมอ
        // ทำให้ช่วงตลาดปิดดูเหมือน "ขาดแท่ง" ตลอด — ถ้ารีเฟรชแล้วไม่ได้แท่งใหม่กว่า DB เลย
        // จำไว้แล้วข้ามการดึงตาม streak (1→4 buckets) จนกว่าจะมีแท่งใหม่จริง
        // อยู่ระดับ process (companion) เพราะ SmcApiService มีหลาย instance (service/UI/tester/trading tools)
        private val tvNoNewDataStreak = mutableMapOf<String, Int>()
        private val tvNoNewDataSkipUntil = mutableMapOf<String, Long>()
        private val tvNetworkFailureSkipUntil = mutableMapOf<String, Long>()
        private var tvHostFailureSkipUntil: Long = 0L


        // Backtest dataset cache (ระดับ process — SmcApiService มีหลาย instance):
        // ชุดข้อมูล 5,000 แท่งต้องคง snapshot เดิมระหว่าง backtest → evolve → backtest
        // เพื่อไม่ให้ ranking เปลี่ยนเพราะ rolling window เลื่อนระหว่างงาน evolution ที่ใช้เวลานาน
        // 2 ชั่วโมงยังสดพอสำหรับงาน backtest แต่ยาวพอให้ long-task workflow ใช้ dataset เดียวกัน
        const val BACKTEST_BARS = TaIndicators.Warmup.BACKTEST_SET
        private const val BACKTEST_CACHE_TTL_MS = 2 * 60 * 60 * 1000L
        private val backtestCandleCache = mutableMapOf<String, Pair<Long, CandleFetchResult>>()

        /**
         * ความลึกของประวัติที่เก็บใน DB ต่อหนึ่งซีรีส์ (symbol × interval × source)
         *
         * ตั้งให้ครอบคลุม backtest (5,000 แท่ง) เผื่อช่วงตลาดปิดที่ทำให้ "แท่งต่อเวลาจริง" น้อยลง
         * ประเมินขนาด: 6,000 แท่ง × 8 TF × 20 symbol × ~60 bytes ≈ 58 MB — รับได้บนมือถือ
         *
         * เดิมตั้งไว้ 10,000 แต่ไม่เคยมีผล เพราะ trimTvCandlesByWindow ลบเหลือ 300 แท่งทุกรอบ
         */
        private const val RETENTION_BARS = 6_000L

        /** กัน backfill ชุดใหญ่ยิงซ้ำถี่ๆ ต่อซีรีส์เดียวกัน */
        private const val BACKFILL_RETRY_MS = 30 * 60 * 1000L
        private val backfillAttemptAt = mutableMapOf<String, Long>()

        /**
         * ความยาวหนึ่งแท่งเป็น ms — delegate ไป TaIndicators.TIMEFRAMES (single source of truth)
         *
         * เดิมเป็น map แยกที่ **ไม่มี 8h** → intervalToMillis("8h") คืน 1 ชม.
         * ทำให้ estimateMissingBars คิดจำนวนแท่งที่ขาดผิดไป 8 เท่า
         */
        fun intervalToMillis(interval: String): Long =
            TaIndicators.timeframeSpecOrNull(interval)?.millis
                ?: TaIndicators.timeframeSpecOrNull(interval.removeSuffix("k"))?.millis // "1wk" (Yahoo)
                ?: TaIndicators.timeframeMillis(null)
    }

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val priceApi by lazy { TradingApiService(client) }

    // Interval maps
    private val binanceIntervalMap = mapOf(
        "1m" to "1m", "3m" to "3m", "5m" to "5m", "15m" to "15m",
        "30m" to "30m", "1h" to "1h", "2h" to "2h", "4h" to "4h",
        "6h" to "6h", "12h" to "12h", "1d" to "1d", "1w" to "1w"
    )

    /**
     * source ที่คาดว่าจะได้สำหรับ symbol นี้
     *
     * Yahoo ถูกถอดออกจากเส้นทาง OHLCV แล้ว (ยังใช้กับราคา/fundamental ที่ TradingApiService)
     * — TradingView เป็นแหล่งหลักเสมอ มี Binance เป็น fallback เฉพาะคู่คริปโตแท้
     */
    fun expectedPrimarySource(symbol: String): String {
        val normalized = normalizeSymbol(symbol)
        return tvSymbolsFor(normalized).firstOrNull()?.second
            ?: if (isPossibleBinanceSymbol(normalized)) "BINANCE" else "TV:OANDA"
    }

    private fun expectedSourceSet(symbol: String): Set<String> {
        val normalized = normalizeSymbol(symbol)
        val tvSources = tvSymbolsFor(normalized).map { it.second }.toSet()
        return if (isPossibleBinanceSymbol(normalized)) tvSources + "BINANCE" else tvSources
    }

    private fun isTvSource(source: String): Boolean = source.startsWith("TV:")

    // ─── Data Fetching ────────────────────────────────────────────────────────

    suspend fun fetchCandles(symbol: String, interval: String, limit: Int = 300): List<Candle> {
        return fetchCandlesWithSource(symbol, interval, limit).candles
    }

    /**
     * ดึงแท่งเทียนสำหรับการวิเคราะห์ — **TradingView เป็นแหล่งเดียว** สำหรับ non-crypto
     *
     * ลำดับ:
     *  0) อ่านจาก DB (ซีรีส์ของ source เดียวตามลำดับความน่าเชื่อถือ) — ไม่แตะเน็ตถ้าข้อมูลสดพอ
     *  1) เติมส่วนที่ขาดจาก TV แบบ incremental
     *  2) ถ้าประวัติยังไม่ลึกพอ → backfill ย้อนหลังจาก TV แล้ว persist
     *  3) Binance เป็น fallback เฉพาะคู่คริปโตแท้เท่านั้น (TV มี rate limit)
     *
     * Yahoo ถูกถอดออกจากเส้นทาง OHLCV โดยเจตนา (ยังใช้กับราคา/fundamental ที่อื่นอยู่):
     * interval map ของ Yahoo ต้อง aggregate 4h จาก 1h เอง และ timestamp/สเกลไม่ตรงกับ TV widget
     * ที่ผู้ใช้เห็นบนจอ ทำให้อินดิเคเตอร์ที่คำนวณได้ไม่ตรงกับกราฟ
     */
    suspend fun fetchCandlesWithSource(symbol: String, interval: String, limit: Int = 300): CandleFetchResult {
        val targetBars = max(limit, recommendedMinBars(interval))
        val usableBars = usableMinBars(interval)
        val sym = normalizeSymbol(symbol)
        val noNewKey = "$sym|$interval"

        // ── 0) ประวัติที่สะสมไว้ใน DB (source เดียว ไม่ปนกัน) ──────────────────
        val dbSeries = preferredDbSeries(sym, interval, targetBars)
        val dbCandles = dbSeries?.second.orEmpty()
        val dbSource = dbSeries?.first

        if (dbCandles.size >= usableBars) {
            val missingBars = estimateMissingBars(dbCandles, interval)
            if (missingBars <= 0 && dbCandles.size >= targetBars) {
                return publish(sym, interval, dbSource ?: "TV:DB", dbCandles.takeLast(targetBars))
            }

            val nowMs = Clock.System.now().toEpochMilliseconds()
            val netFailUntil = tvNetworkFailureSkipUntil[noNewKey]
            val skipUntilMs = tvNoNewDataSkipUntil[noNewKey]
            val backoffActive = (skipUntilMs != null && nowMs < skipUntilMs) ||
                (netFailUntil != null && nowMs < netFailUntil) ||
                (nowMs < tvHostFailureSkipUntil)

            if (backoffActive) {
                return publish(sym, interval, dbSource ?: "TV:DB", dbCandles.takeLast(targetBars))
            }

            // ── 1) เติมแท่งใหม่แบบ incremental ─────────────────────────────────
            if (missingBars > 0) {
                val deltaBars = computeDeltaFetchBars(interval, missingBars, targetBars)
                val tvDelta = fetchCandlesFromTradingView(sym, interval, deltaBars)
                if (tvDelta.candles.isNotEmpty()) {
                    tvHostFailureSkipUntil = 0L
                    tvNetworkFailureSkipUntil.remove(noNewKey)
                    val latestBefore = dbCandles.maxOf { it.timestamp }
                    if (tvDelta.candles.maxOf { it.timestamp } <= latestBefore) {
                        // TV ตอบแต่ไม่มีแท่งใหม่กว่าที่มี — ตลาดน่าจะปิด: ถอยตาม streak (เพดาน 4 แท่ง)
                        val streak = (tvNoNewDataStreak[noNewKey] ?: 0) + 1
                        tvNoNewDataStreak[noNewKey] = streak
                        val tfMs = intervalToMillis(interval).coerceAtLeast(60_000L)
                        tvNoNewDataSkipUntil[noNewKey] = nowMs + tfMs * streak.coerceAtMost(4)
                    } else {
                        tvNoNewDataStreak.remove(noNewKey)
                        tvNoNewDataSkipUntil.remove(noNewKey)
                        // เขียนเฉพาะแท่งที่ดึงมาใหม่ — ไม่ rewrite ทั้งหน้าต่างทุกรอบ
                        // และ **ไม่ trim ตามหน้าต่างที่คืนให้ผู้เรียก** (เดิม trimTvCandlesByWindow
                        // ลบทุกแท่งที่เก่ากว่า 300 แท่งล่าสุด ทับ retention ในทรานแซกชันเดียวกัน
                        // → DB ไม่เคยสะสมประวัติเลย)
                        saveTvCandlesToDb(sym, interval, tvDelta.source, tvDelta.candles)
                        logDebug(
                            "SmcApiService",
                            "TV delta $sym/$interval: +${tvDelta.candles.count { it.timestamp > latestBefore }} bar(s)"
                        )
                    }
                } else {
                    // TV ล่ม แต่ DB ยังใช้ได้ — ถอย 60 วิ กัน background loop ค้างที่ timeout
                    tvNetworkFailureSkipUntil[noNewKey] = nowMs + 60_000L
                }
            }

            // ── 2) ประวัติยังไม่ลึกพอ → backfill ย้อนหลัง ─────────────────────
            val afterDelta = preferredDbSeries(sym, interval, targetBars)?.second.orEmpty()
            if (afterDelta.size < targetBars) {
                backfillHistory(sym, interval, targetBars, afterDelta.size)
            }

            val finalSeries = preferredDbSeries(sym, interval, targetBars)
            if (finalSeries != null && finalSeries.second.isNotEmpty()) {
                return publish(sym, interval, finalSeries.first, finalSeries.second.takeLast(targetBars))
            }
            return publish(sym, interval, dbSource ?: "TV:DB", dbCandles.takeLast(targetBars))
        }

        // ── ยังไม่มีประวัติพอใน DB: ดึงชุดเต็มจาก TV ─────────────────────────
        val tvResult = fetchCandlesFromTradingView(sym, interval, targetBars)
        if (tvResult.candles.isNotEmpty()) {
            saveTvCandlesToDb(sym, interval, tvResult.source, tvResult.candles)
            logDebug("SmcApiService", "TV cold fetch $sym/$interval: ${tvResult.candles.size} แท่ง (${tvResult.source})")
            if (tvResult.candles.size >= usableBars) {
                return publish(sym, interval, tvResult.source, tvResult.candles.takeLast(targetBars))
            }
        }

        // ── 3) Binance fallback — เฉพาะคู่คริปโตแท้ ──────────────────────────
        if (isPossibleBinanceSymbol(sym)) {
            val candles = fetchCandlesFromBinance(sym, interval, targetBars)
            if (candles.size >= usableBars) {
                return publish(sym, interval, "BINANCE", candles.takeLast(targetBars))
            }
        }

        // ไม่มี source ไหนให้ข้อมูลพอ — คืนชุดที่ดีที่สุดเท่าที่มี พร้อม source ตามจริง
        // (ผู้เรียกดู candles.size เทียบ warm-up เองได้ และอินดิเคเตอร์จะคืน null เมื่อไม่พอ)
        val best = listOf(
            dbSource?.let { it to dbCandles } ,
            tvResult.source.takeIf { tvResult.candles.isNotEmpty() }?.let { it to tvResult.candles }
        ).filterNotNull().maxByOrNull { it.second.size }
            ?: return CandleFetchResult(emptyList(), "NONE")

        return publish(sym, interval, best.first, best.second.takeLast(targetBars))
    }

    /** บันทึกลง in-memory store แล้วคืนผล — รวมไว้จุดเดียวกัน source/หน้าต่างจะได้ไม่หลุด */
    private fun publish(symbol: String, interval: String, source: String, candles: List<Candle>): CandleFetchResult {
        if (candles.isNotEmpty()) OhlcvCentralStore.put(symbol, interval, source, candles)
        return CandleFetchResult(candles, source)
    }

    /**
     * เติมประวัติย้อนหลังให้ลึกถึง [targetBars]
     *
     * TV ส่งแท่งย้อนหลังตามจำนวนที่ขอจากแท่งล่าสุด จึงขอชุดใหญ่ทีเดียวแล้ว upsert
     * ทับซ้อนกับที่มีอยู่ — แท่งที่ซ้ำจะถูก REPLACE ด้วยค่าเดิม ไม่เสียหาย
     * ทำครั้งเดียวต่อซีรีส์ (มี guard) เพราะเป็น request ที่หนักที่สุด
     */
    private suspend fun backfillHistory(symbol: String, interval: String, targetBars: Int, currentBars: Int) {
        val key = "$symbol|$interval|$targetBars"
        val now = Clock.System.now().toEpochMilliseconds()
        val lastAttempt = backfillAttemptAt[key]
        if (lastAttempt != null && now - lastAttempt < BACKFILL_RETRY_MS) return
        backfillAttemptAt[key] = now

        val deep = fetchCandlesFromTradingView(symbol, interval, targetBars)
        if (deep.candles.size > currentBars) {
            saveTvCandlesToDb(symbol, interval, deep.source, deep.candles)
            logDebug(
                "SmcApiService",
                "Backfill $symbol/$interval: $currentBars → ${deep.candles.size} แท่ง (เป้า $targetBars, ${deep.source})"
            )
        }
    }

    /**
     * โหลดซีรีส์ของ **source เดียว** จาก DB
     *
     * ก่อนหน้านี้ query ไม่ filter source ทำให้แท่งจาก OANDA / FX_IDC / TVC
     * (ที่ราคาต่างกัน 2-3 จุด) ปนกันอยู่ในซีรีส์เดียวแล้วถูกนำไปคำนวณอินดิเคเตอร์
     *
     * @param closedOnly true = คืนเฉพาะแท่งที่ปิดแล้ว (input มาตรฐานของการคำนวณ)
     */
    private fun loadTvCandlesFromDb(
        symbol: String,
        interval: String,
        source: String,
        limit: Int,
        closedOnly: Boolean = true
    ): List<Candle> {
        val db = JarvisDatabaseHolder.database ?: return emptyList()
        if (limit <= 0) return emptyList()
        return try {
            val q = db.jarvisDatabaseQueries
            val rows = if (closedOnly) {
                q.getRecentClosedTvCandles(symbol, interval, source, limit.toLong()).executeAsList()
                    .map { Candle(it.open_, it.high, it.low, it.close, it.volume, it.ts, it.is_closed != 0L) }
            } else {
                q.getRecentTvCandles(symbol, interval, source, limit.toLong()).executeAsList()
                    .map { Candle(it.open_, it.high, it.low, it.close, it.volume, it.ts, it.is_closed != 0L) }
            }
            rows.asReversed()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * เลือกซีรีส์ที่ดีที่สุดใน DB สำหรับ symbol/interval นี้
     *
     * ไล่ตามลำดับความน่าเชื่อถือของ source ที่ [tvSymbolsFor] กำหนดไว้ (OANDA → FX_IDC → TVC)
     * แล้วจึงค่อยพิจารณา source อื่นที่เคยเก็บไว้ — ไม่เลือกจาก "ซีรีส์ไหนยาวสุด"
     * เพราะจะทำให้ข้อมูล PAXG (คนละ instrument) ชนะข้อมูลทองจริงได้
     */
    private fun preferredDbSeries(symbol: String, interval: String, limit: Int): Pair<String, List<Candle>>? {
        val db = JarvisDatabaseHolder.database ?: return null
        val available = runCatching {
            db.jarvisDatabaseQueries.summarizeTvCandleSources(symbol, interval).executeAsList()
        }.getOrNull().orEmpty()
        if (available.isEmpty()) return null

        val priority = tvSymbolsFor(symbol).map { it.second }
        val ordered = available
            .map { row ->
                val idx = priority.indexOf(row.source)
                Triple(if (idx >= 0) idx else priority.size, -(row.bars), row.source)
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
        for ((_, _, source) in ordered) {
            val candles = loadTvCandlesFromDb(symbol, interval, source, limit)
            if (candles.isNotEmpty()) {
                OhlcvMaintenance.touch(symbol, interval, source)
                return source to candles
            }
        }
        return null
    }

    private fun saveTvCandlesToDb(symbol: String, interval: String, source: String, candles: List<Candle>) {
        val db = JarvisDatabaseHolder.database ?: return
        if (candles.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        try {
            db.transaction {
                candles.forEach { c ->
                    db.jarvisDatabaseQueries.upsertTvCandle(
                        symbol = symbol,
                        interval = interval,
                        source = source,
                        ts = c.timestamp,
                        open_ = c.open,
                        high = c.high,
                        low = c.low,
                        close = c.close,
                        volume = c.volume,
                        is_closed = if (c.isClosed) 1L else 0L,
                        updated_at = now
                    )
                }
                // Retention เป็น "จำนวนแท่ง" ของ TF นั้น ไม่ใช่หน้าต่างที่คืนให้ผู้เรียก
                // ลึกพอสำหรับ backtest (BACKTEST_BARS) และ warm-up ของ EMA200
                val retentionMs = intervalToMillis(interval) * RETENTION_BARS
                db.jarvisDatabaseQueries.deleteTvCandlesBefore(symbol, interval, source, now - retentionMs)
            }
        } catch (_: Exception) {
            // Non-fatal: SMC can continue using network/in-memory data.
        }
    }

    /**
     * ประเมินว่าขาดแท่งไปกี่แท่งนับจากแท่งล่าสุดที่มี
     *
     * คำนวณจาก **ระยะห่างจากแท่งล่าสุดที่มีจริง** ไม่ใช่ bucket ที่ align กับ epoch
     *
     * เดิมใช้ `(now / tfMs) * tfMs` ซึ่งถือว่ากริดของแท่งเริ่มนับจาก epoch พอดี — ไม่จริงเลย:
     * แท่ง D1 ของทองเปิด 21:00 UTC ไม่ใช่ 00:00 UTC ส่วน epoch week เริ่ม "วันพฤหัส"
     * (1 ม.ค. 1970 เป็นวันพฤหัส) แต่แท่ง W ของ TV เริ่มวันอาทิตย์/จันทร์
     * → 1D/1W จึงดู "ขาดแท่ง" ตลอดเวลา ยิง network ทุกรอบแล้วติด backoff streak ถาวร
     *
     * วิธีใหม่ไม่ผูกกับกริด จึงถูกต้องกับทุก instrument และทุก session
     */
    internal fun estimateMissingBars(
        existing: List<Candle>,
        interval: String,
        nowMs: Long = Clock.System.now().toEpochMilliseconds()
    ): Int {
        val latestTs = existing.maxOfOrNull { it.timestamp } ?: return Int.MAX_VALUE
        val tfMs = intervalToMillis(interval).coerceAtLeast(60_000L)
        val elapsed = nowMs - latestTs
        if (elapsed < tfMs) return 0
        return (elapsed / tfMs).toInt().coerceAtLeast(0)
    }

    private fun computeDeltaFetchBars(interval: String, missingBars: Int, targetBars: Int): Int {
        val base = when (interval.lowercase()) {
            "1m" -> 20
            "3m" -> 16
            "5m" -> 12
            "15m" -> 10
            "30m" -> 8
            "1h" -> 5
            "2h" -> 5
            "4h" -> 3
            "1d" -> 2
            "1w" -> 2
            else -> 12
        }
        val needed = (missingBars + 1).coerceAtLeast(1)
        val withBuffer = needed + (base / 2)
        return max(base, withBuffer).coerceIn(2, targetBars.coerceAtLeast(2))
    }

    private fun mergeCandlesByTimestamp(base: List<Candle>, incoming: List<Candle>): List<Candle> {
        if (base.isEmpty()) return incoming.sortedBy { it.timestamp }
        if (incoming.isEmpty()) return base.sortedBy { it.timestamp }
        val merged = linkedMapOf<Long, Candle>()
        base.sortedBy { it.timestamp }.forEach { merged[it.timestamp] = it }
        incoming.sortedBy { it.timestamp }.forEach { merged[it.timestamp] = it }
        return merged.values.sortedBy { it.timestamp }
    }

    /**
     * ดึงแท่งเทียนย้อนหลังชุดใหญ่สำหรับ Backtest (BACKTEST_BARS = 5,000 แท่ง)
     *
     * ตอนนี้ **อ่านจาก TvCandle DB ก่อน** แล้วค่อยเติมจากเน็ตเฉพาะส่วนที่ขาด
     * (เดิมเลี่ยง DB เพราะ trimTvCandlesByWindow ลบเหลือ 300 แท่งทุกรอบ — แก้แล้วใน Phase 1
     *  retention ตอนนี้คือ RETENTION_BARS = 6,000 แท่งต่อซีรีส์ ครอบคลุม backtest ได้เต็ม)
     *
     * ผลคือ backtest ไม่ต้องโหลด 5,000 แท่งใหม่ทุกครั้งที่รีสตาร์ทแอปอีก
     * in-memory cache ยังอยู่เพื่อคง snapshot เดิมระหว่าง backtest → evolve → backtest
     * ที่ต้องการ dataset นิ่งตลอดงาน (reproducibility)
     *
     * ความลึกโดยประมาณ: 15m ≈ 52 วัน, 1h ≈ 7 เดือน, 4h ≈ 2.3 ปี, 1D ≈ 13 ปี
     */
    suspend fun fetchBacktestCandles(symbol: String, interval: String): CandleFetchResult {
        val sym = normalizeSymbol(symbol)
        val key = "$sym|${interval.lowercase()}"
        backtestCandleCache[key]?.let { (cachedAt, cached) ->
            if (Clock.System.now().toEpochMilliseconds() - cachedAt < BACKTEST_CACHE_TTL_MS && cached.candles.isNotEmpty()) {
                logDebug("SmcApiService", "Backtest cache hit $key: ${cached.candles.size} แท่ง (${cached.source}) ${cached.candles.first().timestamp} → ${cached.candles.last().timestamp} — REPRODUCIBLE SNAPSHOT")
                return cached
            }
        }

        fun snapshot(candles: List<Candle>, source: String): CandleFetchResult {
            val result = CandleFetchResult(candles.sortedBy { it.timestamp }, source)
            backtestCandleCache[key] = Clock.System.now().toEpochMilliseconds() to result
            return result
        }

        // 1) ประวัติที่สะสมไว้ใน DB — ใช้ได้ทันทีถ้าลึกพอและไม่ค้างเกิน 1 แท่ง
        preferredDbSeries(sym, interval, BACKTEST_BARS)?.let { (dbSource, dbCandles) ->
            if (dbCandles.size >= BACKTEST_BARS && estimateMissingBars(dbCandles, interval) <= 0) {
                logDebug("SmcApiService", "Backtest จาก DB $key: ${dbCandles.size} แท่ง ($dbSource)")
                return snapshot(dbCandles, dbSource)
            }
        }

        // 2) ดึงจาก TV แล้ว persist ลง DB (รอบหน้าจะได้ไม่ต้องโหลดซ้ำ)
        val resolution = tvResolution(interval)
        for ((tvSymbol, source) in tvSymbolsFor(sym)) {
            val (raw, isConnError) = fetchTvCandlesViaWebSocket(tvSymbol, resolution, BACKTEST_BARS)
            if (raw.size >= TaIndicators.Warmup.YEARLY_SET) {
                val candles = markClosedState(raw, interval)
                saveTvCandlesToDb(sym, interval, source, candles)
                logDebug(
                    "SmcApiService",
                    "Backtest candles $key: ${candles.size} แท่ง (${candles.first().timestamp} → ${candles.last().timestamp}) จาก $source → persisted"
                )
                return snapshot(candles, source)
            }
            if (isConnError) break
        }

        // 3) fallback: Binance — เฉพาะคู่คริปโตแท้ (สูงสุด 1,000 แท่ง)
        if (isPossibleBinanceSymbol(sym)) {
            val candles = runCatching { fetchCandlesFromBinance(sym, interval.lowercase(), 1000) }.getOrElse { emptyList() }
            if (candles.isNotEmpty()) {
                saveTvCandlesToDb(sym, interval, "BINANCE", candles)
                logDebug("SmcApiService", "Backtest candles $key: ${candles.size} แท่ง (fallback BINANCE)")
                return snapshot(candles, "BINANCE")
            }
        }

        // 4) ดึงไม่ได้ — ใช้ประวัติเท่าที่มีใน DB ดีกว่าคืนค่าว่าง
        preferredDbSeries(sym, interval, BACKTEST_BARS)?.let { (dbSource, dbCandles) ->
            if (dbCandles.size >= TaIndicators.Warmup.YEARLY_SET) {
                logDebug("SmcApiService", "Backtest fallback DB $key: ${dbCandles.size} แท่ง ($dbSource)")
                return snapshot(dbCandles, dbSource)
            }
        }

        logDebug("SmcApiService", "Backtest candles $key: ดึงไม่ได้จากทุก source")
        return CandleFetchResult(emptyList(), "NONE")
    }

    suspend fun fetchTradingViewCandlesOnly(symbol: String, interval: String, limit: Int = 300): CandleFetchResult {
        return fetchCandlesFromTradingView(symbol, interval, limit)
    }

    private suspend fun fetchCandlesFromTradingView(symbol: String, interval: String, limit: Int): CandleFetchResult {
        val now = Clock.System.now().toEpochMilliseconds()
        if (now < tvHostFailureSkipUntil) {
            return CandleFetchResult(emptyList(), "NONE")
        }
        val resolution = tvResolution(interval)
        val symbolsToTry = tvSymbolsFor(symbol)
        for ((tvSymbol, source) in symbolsToTry) {
            val (candles, isConnError) = fetchTvCandlesViaWebSocket(tvSymbol, resolution, limit)
            if (candles.isNotEmpty()) {
                tvHostFailureSkipUntil = 0L
                return CandleFetchResult(markClosedState(candles.takeLast(limit), interval), source)
            }
            if (isConnError) {
                // Host data.tradingview.com มีปัญหาการเชื่อมต่อ — พัก 60 วินาทีทั่วทั้ง host
                // เพื่อไม่ให้ TF อื่นๆ (15m, 5m, 1m) ต้องมาเสียเวลารอ timeout 7s ซ้ำๆ ในรอบเดียวกัน
                tvHostFailureSkipUntil = Clock.System.now().toEpochMilliseconds() + 60_000L
                break
            }
        }
        return CandleFetchResult(emptyList(), "NONE")
    }

    /**
     * ติดธงว่าแท่งไหน "ปิดแล้ว" — จุดเดียวที่ตัดสินเรื่องนี้สำหรับข้อมูลจาก TV
     *
     * TV stream แท่งล่าสุดแบบ real-time: high/low/close ยังขยับได้จนกว่าจะหมดช่วงเวลา
     * ถือว่าแท่งปิดเมื่อ `now >= ts + tfMs` เท่านั้น — วัดจาก timestamp ของแท่งเอง
     * ไม่ต้องรู้ session boundary ของ instrument จึงถูกต้องกับทุกตลาด
     */
    internal fun markClosedState(
        candles: List<Candle>,
        interval: String,
        nowMs: Long = Clock.System.now().toEpochMilliseconds()
    ): List<Candle> {
        if (candles.isEmpty()) return candles
        val tfMs = intervalToMillis(interval).coerceAtLeast(60_000L)
        return candles.map { c -> c.copy(isClosed = nowMs >= c.timestamp + tfMs) }
    }

    private fun tvSymbolsFor(symbol: String): List<Pair<String, String>> {
        val s = symbol.uppercase()
        return when {
            s.contains("XAU") || s.contains("GOLD") || s == "GCF" || s == "GC=F" -> listOf(
                "OANDA:XAUUSD" to "TV:OANDA",
                "FX_IDC:XAUUSD" to "TV:FX_IDC",
                "TVC:GOLD" to "TV:TVC"
            )
            s.contains("XAG") || s.contains("SILVER") -> listOf(
                "OANDA:XAGUSD" to "TV:OANDA",
                "FX_IDC:XAGUSD" to "TV:FX_IDC",
                "TVC:SILVER" to "TV:TVC"
            )
            s.length == 6 && s.all { it.isLetter() } -> listOf(
                "OANDA:$s" to "TV:OANDA",
                "FX_IDC:$s" to "TV:FX_IDC"
            )
            s.endsWith("=X") -> listOf(
                "FX_IDC:${s.removeSuffix("=X")}" to "TV:FX_IDC",
                "OANDA:${s.removeSuffix("=X")}" to "TV:OANDA"
            )
            ":" in s -> listOf(s to "TV:${s.substringBefore(':')}")
            else -> listOf(
                "BINANCE:$s" to "TV:BINANCE",
                "NASDAQ:$s" to "TV:NASDAQ",
                "NYSE:$s" to "TV:NYSE"
            )
        }
    }

    /**
     * TradingView resolution — delegate ไป TaIndicators (single source of truth)
     *
     * เดิมเป็น map แยกที่ **ไม่มี 6h/8h/12h** → ขอ 6h ได้ resolution "60" (1 ชม.) เงียบๆ
     */
    private fun tvResolution(interval: String): String = TaIndicators.toTvResolution(interval)

    private fun tvFromParam(tvSymbol: String): String {
        // Example: OANDA:XAUUSD -> symbols/OANDA-XAUUSD/
        val sanitized = tvSymbol.replace(":", "-")
        return "symbols/$sanitized/"
    }

    private fun tvWrapMessage(payload: String): String = "~m~${payload.length}~m~$payload"

    private fun randomSession(prefix: String): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val suffix = (1..12).joinToString("") { chars.random().toString() }
        return "${prefix}_$suffix"
    }

    private fun extractTvFrames(buffer: String): Pair<List<String>, String> {
        val messages = mutableListOf<String>()
        var pos = 0

        while (true) {
            if (pos >= buffer.length) break
            if (!buffer.startsWith("~m~", pos)) {
                val next = buffer.indexOf("~m~", pos)
                if (next == -1) {
                    return messages to buffer.substring(pos)
                }
                pos = next
            }

            val lenStart = pos + 3
            val lenEnd = buffer.indexOf("~m~", lenStart)
            if (lenEnd == -1) break
            val lenValue = buffer.substring(lenStart, lenEnd).toIntOrNull()
            if (lenValue == null) {
                pos = lenEnd + 3
                continue
            }
            val len = lenValue
            val msgStart = lenEnd + 3
            val msgEnd = msgStart + len
            if (msgEnd > buffer.length) break

            messages.add(buffer.substring(msgStart, msgEnd))
            pos = msgEnd
        }

        return messages to buffer.substring(pos)
    }

    private fun extractTvBarsFromSeriesNode(seriesNode: JsonObject): List<Candle> {
        val out = mutableListOf<Candle>()
        fun tsSecOf(p: JsonPrimitive): Long? = p.longOrNull ?: p.doubleOrNull?.toLong()
        fun arrayField(value: JsonElement?): JsonArray? = when (value) {
            is JsonArray -> value
            is JsonPrimitive -> runCatching { json.parseToJsonElement(value.content).jsonArray }.getOrNull()
            else -> null
        }

        // Format A: s = [{ i:..., v:[ts,o,h,l,c,v] }, ...]
        val sField = arrayField(seriesNode["s"])
        if (sField != null) {
            for (bar in sField) {
                val barObj = bar as? JsonObject ?: continue
                val values = arrayField(barObj["v"]) ?: continue
                if (values.size < 5) continue
                val tsSec = tsSecOf(values[0].jsonPrimitive) ?: continue
                val close = values[4].jsonPrimitive.doubleOrNull ?: continue
                out.add(
                    Candle(
                        open = values[1].jsonPrimitive.doubleOrNull ?: close,
                        high = values[2].jsonPrimitive.doubleOrNull ?: close,
                        low = values[3].jsonPrimitive.doubleOrNull ?: close,
                        close = close,
                        volume = values.getOrNull(5)?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        timestamp = tsSec * 1000L
                    )
                )
            }
        }
        if (out.isNotEmpty()) return out

        // Format B: t/o/h/l/c/v arrays
        val t = arrayField(seriesNode["t"]) ?: return emptyList()
        val o = arrayField(seriesNode["o"]) ?: return emptyList()
        val h = arrayField(seriesNode["h"]) ?: return emptyList()
        val l = arrayField(seriesNode["l"]) ?: return emptyList()
        val c = arrayField(seriesNode["c"]) ?: return emptyList()
        val v = arrayField(seriesNode["v"])
        val size = listOf(t.size, o.size, h.size, l.size, c.size).minOrNull() ?: 0
        if (size <= 0) return emptyList()

        for (idx in 0 until size) {
            val tsSec = tsSecOf(t[idx].jsonPrimitive) ?: continue
            val close = c[idx].jsonPrimitive.doubleOrNull ?: continue
            out.add(
                Candle(
                    open = o[idx].jsonPrimitive.doubleOrNull ?: close,
                    high = h[idx].jsonPrimitive.doubleOrNull ?: close,
                    low = l[idx].jsonPrimitive.doubleOrNull ?: close,
                    close = close,
                    volume = v?.getOrNull(idx)?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    timestamp = tsSec * 1000L
                )
            )
        }
        return out
    }

    private fun looksLikeTvSeriesNode(node: JsonObject): Boolean {
        val s = node["s"]
        if (s is JsonArray) return true
        val t = node["t"]
        val c = node["c"]
        return t is JsonArray && c is JsonArray
    }

    private fun findTvSeriesNode(body: JsonObject, preferredKey: String): JsonObject? {
        (body[preferredKey] as? JsonObject)?.let { preferred ->
            if (looksLikeTvSeriesNode(preferred)) return preferred
        }
        for ((_, value) in body) {
            val node = value as? JsonObject ?: continue
            if (looksLikeTvSeriesNode(node)) return node
        }
        if (looksLikeTvSeriesNode(body)) return body
        return null
    }

    private suspend fun fetchTvCandlesViaWebSocket(tvSymbol: String, resolution: String, limit: Int): Pair<List<Candle>, Boolean> {
        val nativeBars = runCatching {
            fetchTvHistoryBars(symbol = tvSymbol, resolution = resolution, bars = limit.coerceIn(2, 5000), timeoutSec = 12)
        }.getOrElse { emptyList() }
        if (nativeBars.isNotEmpty()) {
            logDebug("SmcApiService", "TV native bridge bars loaded: ${nativeBars.size} for $tvSymbol/$resolution")
            return Pair(nativeBars.sortedBy { it.timestamp }.takeLast(limit), false)
        }

        val chartSession = randomSession("cs")
        val seriesName = "s1"
        val symbolAlias = "symbol_1"

        return try {
            val collected = mutableListOf<Candle>()
            kotlinx.coroutines.withTimeout(7_000L) {
                client.webSocket(
                    request = {
                        val fromParam = tvFromParam(tvSymbol)
                        url("wss://data.tradingview.com/socket.io/websocket?from=$fromParam")
                        header("Origin", "https://www.tradingview.com")
                        header("Referer", "https://www.tradingview.com/")
                        header("User-Agent", "Mozilla/5.0")
                    }
                ) {
                    suspend fun sendCommand(methodName: String, params: JsonArray) {
                        val payload = buildJsonObject {
                            put("m", methodName)
                            put("p", params)
                        }.toString()
                        send(Frame.Text(tvWrapMessage(payload)))
                    }

                    send(Frame.Text(tvWrapMessage(buildJsonObject {
                        put("m", "set_data_quality")
                        put("p", buildJsonArray { add("low") })
                    }.toString())))
                    send(Frame.Text(tvWrapMessage(buildJsonObject {
                        put("m", "set_auth_token")
                        put("p", buildJsonArray { add("unauthorized_user_token") })
                    }.toString())))
                    sendCommand("chart_create_session", buildJsonArray { add(chartSession); add("") })

                    val resolvePayload = buildJsonObject {
                        put("symbol", tvSymbol)
                        put("adjustment", "splits")
                        put("session", "regular")
                    }.toString()
                    sendCommand("resolve_symbol", buildJsonArray {
                        add(chartSession)
                        add(symbolAlias)
                        add("=$resolvePayload")
                    })
                    sendCommand("create_series", buildJsonArray {
                        add(chartSession)
                        add(seriesName)
                        add(seriesName)
                        add(symbolAlias)
                        add(resolution)
                        add(limit.coerceIn(2, 5000))
                    })
                    sendCommand("switch_timezone", buildJsonArray { add(chartSession); add("Etc/UTC") })

                    val deadline = Clock.System.now().toEpochMilliseconds() + 9_000L
                    var incomingBuffer = ""
                    while (Clock.System.now().toEpochMilliseconds() < deadline) {
                        val frame = incoming.receive()
                        val text = when (frame) {
                            is Frame.Text -> frame.readText()
                            is Frame.Binary -> frame.readBytes().decodeToString()
                            else -> continue
                        }
                        incomingBuffer += text
                        val extracted = extractTvFrames(incomingBuffer)
                        val packets = extracted.first
                        incomingBuffer = extracted.second
                        for (packet in packets) {
                            if (packet.startsWith("~h~")) {
                                send(Frame.Text(tvWrapMessage(packet)))
                                continue
                            }
                            val root = runCatching { json.parseToJsonElement(packet).jsonObject }.getOrNull() ?: continue
                            val methodName = root["m"]?.jsonPrimitive?.contentOrNull ?: continue
                            if (methodName != "timescale_update") continue
                            val payload = root["p"]?.jsonArray ?: continue
                            if (payload.size < 2) continue
                            val body = when (val p1 = payload[1]) {
                                is JsonObject -> p1
                                is JsonPrimitive -> {
                                    val textBody = p1.contentOrNull ?: ""
                                    runCatching { json.parseToJsonElement(textBody).jsonObject }.getOrNull() ?: continue
                                }
                                else -> continue
                            }
                            val seriesNode = findTvSeriesNode(body, seriesName)
                            if (seriesNode == null) {
                                continue
                            }
                            collected.clear()
                            collected.addAll(extractTvBarsFromSeriesNode(seriesNode))
                            if (collected.isNotEmpty()) {
                                return@webSocket
                            }
                        }
                    }
                }
            }
            if (collected.isEmpty()) {
                logDebug("SmcApiService", "TV websocket returned no bars for $tvSymbol/$resolution")
            }
            Pair(collected.sortedBy { it.timestamp }.takeLast(limit), false)
        } catch (e: Throwable) {
            val isConnError = isConnectionError(e)
            logDebug("SmcApiService", "TV websocket fetch failed for $tvSymbol/$resolution: ${e.message}")
            Pair(emptyList(), isConnError)
        }
    }

    private fun isConnectionError(e: Throwable): Boolean {
        if (e is kotlinx.coroutines.TimeoutCancellationException) return true
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("failed to connect") ||
               msg.contains("connect timed out") ||
               msg.contains("connection refused") ||
               msg.contains("unreachable") ||
               e::class.simpleName?.contains("Timeout", ignoreCase = true) == true ||
               e::class.simpleName?.contains("Connect", ignoreCase = true) == true
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


    /**
     * แท่งขั้นต่ำที่ต้องมีก่อนจะถือว่าซีรีส์ "พร้อมวิเคราะห์"
     *
     * เดิมใช้ 150 (TF ≤ 1h) / 300 (4h+) ซึ่งต่ำกว่าที่ EMA200 กับ SMA200 ต้องการมาก
     * ผลคือบน 15m ระบบดึงมา 150 แท่งแล้ว EMA200 คำนวณไม่ได้ —
     * และเพราะ TaIndicators.ema เดิม fallback เป็นราคาปิด จึงได้ "EMA200 = close" ทุกครั้ง
     *
     * ตอนนี้อิงตาราง warm-up กลาง: ชุดเต็มที่มี EMA200 ต้องการ 200 × 4 = 800 แท่ง
     */
    private fun recommendedMinBars(interval: String): Int = TaIndicators.Warmup.FULL_SET

    /**
     * แท่งขั้นต่ำที่ยัง "ใช้งานได้" แม้ยังไม่ครบ warm-up เต็ม
     * — ใช้ตัดสินว่าจะยอมคืนผลบางส่วนหรือถือว่าดึงไม่สำเร็จ
     * อินดิเคเตอร์ที่ warm-up ไม่ถึงจะคืน null เองอยู่แล้ว (fail-closed)
     */
    private fun usableMinBars(interval: String): Int = when (TaIndicators.normalizeTimeframe(interval)) {
        "1D", "1W", "1M" -> TaIndicators.Warmup.YEARLY_SET
        else -> TaIndicators.Warmup.MACD_SET
    }

    private fun isPossibleBinanceSymbol(s: String): Boolean {
        // Strict crypto-like detection only, to avoid routing Forex/Gold to Binance accidentally.
        val upper = s.uppercase()
        if (upper.contains("XAU") || upper.contains("XAG") || upper.contains("GOLD")) return false
        // Forex pairs are exactly 6 letters with no USDT/USD suffix → skip them
        if (upper.length == 6 && upper.all { it.isLetter() } && !upper.endsWith("USD")) return false
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

    private fun normalizeSymbol(symbol: String): String = normalizeSymbolKey(symbol)

    // ─── Core SMC Algorithms ──────────────────────────────────────────────────

    /**
     * ATR (Average True Range) — ใช้ period=14 เหมือน indicator ต้นฉบับ
     */
    fun calcATR(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size < period + 1) return 0.0
        val trs = ArrayList<Double>(candles.size - 1)
        for (i in 1 until candles.size) {
            val h = candles[i].high
            val l = candles[i].low
            val pc = candles[i - 1].close
            trs.add(maxOf(h - l, abs(h - pc), abs(l - pc)))
        }
        var atr = trs.take(period).average()
        for (i in period until trs.size) {
            atr = (atr * (period - 1) + trs[i]) / period
        }
        return atr
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
    ): List<SmcLiquidityZone> = detectPineLiquidityZones(candles, lookback, threshold).map { z ->
        z.copy(confluenceScore = calcConfluenceScore(z.price, z.isHigh, structureHigh, structureLow, atr, bullishOBs, bearishOBs, premiumBot, discountTop, structureDir))
    }

    /** Deterministic replay of Pine V11.29 tfLegacyLiqSnapshot lifecycle. */
    fun detectPineLiquidityZones(candles: List<Candle>, lookback: Int = 10, threshold: Double = 0.1, swingLen: Int = 10, maxZones: Int = 50, maxN: Int = 5): List<SmcLiquidityZone> {
        if (candles.size < 3 || lookback < 2) return emptyList()
        data class Z(var price: Double, var bar: Int, var high: Boolean, var swept: Boolean, var strength: Int)
        val zones = mutableListOf<Z>()
        fun pivotH(i: Int): Boolean { if (i-swingLen < 0 || i+swingLen >= candles.size) return false; val p=candles[i].high; return (i-swingLen..i+swingLen).all { it==i || candles[it].high<=p } }
        fun pivotL(i: Int): Boolean { if (i-swingLen < 0 || i+swingLen >= candles.size) return false; val p=candles[i].low; return (i-swingLen..i+swingLen).all { it==i || candles[it].low>=p } }
        for (bar in candles.indices) {
            val lb=minOf(lookback,bar)
            if (lb>1) for (i in 1..lb) {
                val idx=bar-i; val h=candles[idx].high; val th=h*threshold/100.0; val end=minOf(idx+lb,bar); var eq=0
                if(end>=idx+1) for(j in idx+1..end) if(abs(candles[j].high-h)<=th){eq++;break}
                if(eq>=1 && zones.none{it.high && abs(it.price-h)<=th}) zones.add(0,Z(h,idx,true,false,eq))
            }
            if (lb>1) for (i in 1..lb) {
                val idx=bar-i; val l=candles[idx].low; val th=l*threshold/100.0; val end=minOf(idx+lb,bar); var eq=0
                if(end>=idx+1) for(j in idx+1..end) if(abs(candles[j].low-l)<=th){eq++;break}
                if(eq>=1 && zones.none{!it.high && abs(it.price-l)<=th}) zones.add(0,Z(l,idx,false,false,eq))
            }
            val pi=bar-swingLen
            if(pi>=0){ if(pivotH(pi)) zones.add(0,Z(candles[pi].high,pi,true,false,2)); if(pivotL(pi)) zones.add(0,Z(candles[pi].low,pi,false,false,2)) }
            for(z in zones){ if(!z.swept && z.high && candles[bar].high>z.price){z.swept=true;z.bar=bar}; if(!z.swept && !z.high && candles[bar].low<z.price){z.swept=true;z.bar=bar} }
            zones.removeAll{it.swept && bar-it.bar>lookback*2}; while(zones.size>maxZones) zones.removeAt(zones.lastIndex)
        }
        val out=mutableListOf<SmcLiquidityZone>(); var hc=0; var lc=0
        for(z in zones) { if(z.swept) continue; if(z.high && hc<maxN){out+=SmcLiquidityZone(z.price,true,z.strength);hc++} else if(!z.high && lc<maxN){out+=SmcLiquidityZone(z.price,false,z.strength);lc++}; if(hc>=maxN&&lc>=maxN) break }
        return out
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
        strictSource: Boolean = false,
        strictTvSource: Boolean = true
    ): SmcAnalysisResult? {
        val expectedSource = expectedPrimarySource(symbol)
        val expectedSources = expectedSourceSet(symbol)
        val fetch = fetchCandlesWithSource(symbol, interval, limit)
        if (strictSource && fetch.source !in expectedSources) {
            throw StrictSourceMismatchException(
                "Strict source violation: expected=${expectedSources.joinToString("|")}, actual=${fetch.source}, symbol=${symbol.uppercase()}, tf=$interval"
            )
        }
        if (strictTvSource && !isTvSource(fetch.source)) {
            throw StrictSourceMismatchException(
                "Strict TV candle source violation: expected=TV:*, actual=${fetch.source}, symbol=${symbol.uppercase()}, tf=$interval"
            )
        }
        val candles = fetch.candles
        if (candles.size < 150) return null

        val atr = calcATR(candles)
        val lastClose = candles.last().close
        val autoLive = if (overridePrice == null) {
            try {
                priceApi.getBestEffortPrice(symbol)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        val autoLivePrice = autoLive?.get("price")?.toDoubleOrNull()
        val autoLiveSource = autoLive?.get("source")

        if (strictTvSource) {
            val source = autoLiveSource ?: "NONE"
            if (!isTvSource(source)) {
                throw StrictSourceMismatchException(
                    "Strict TV source violation: expected=TV:*, actual=$source, symbol=${symbol.uppercase()}, tf=$interval"
                )
            }
        }

        val effectiveOverride = overridePrice ?: autoLivePrice
        val maxAllowedDrift = max(lastClose * 0.004, atr * 1.5) // 0.4% or 1.5 ATR
        val overrideAccepted = effectiveOverride != null && abs(effectiveOverride - lastClose) <= maxAllowedDrift
        val currentPrice = if (overrideAccepted) {
            effectiveOverride!!
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
            priceSource = when {
                !overrideAccepted -> fetch.source
                overridePrice != null -> "OVERRIDE_PRICE"
                else -> "UNIFIED_PRICE:${autoLiveSource ?: "UNKNOWN"}"
            },
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
        strictSource: Boolean = false,
        strictTvSource: Boolean = true
    ): List<SmcMtfSweepFrame> {
        val expectedSources = expectedSourceSet(symbol)
        val timeframes = mapOf(
            "M1"  to "1m",
            "M5"  to "5m",
            "M15" to "15m",
            "M30" to "30m",
            "H1"  to "1h",
            "H4"  to "4h"
        )
        val results = coroutineScope {
            timeframes.map { (label, tf) ->
                async {
                    val startedAt = Clock.System.now().toEpochMilliseconds()
                    logDebug("SmcApiService", "MTF sweeps[$label/$tf] start")
                    val fetch = fetchCandlesWithSource(symbol, tf, 100)
                    val candles = fetch.candles
                    if (strictSource && fetch.source !in expectedSources) {
                        val duration = Clock.System.now().toEpochMilliseconds() - startedAt
                        logDebug("SmcApiService", "MTF sweeps[$label/$tf] skip(strict_source) source=${fetch.source} duration=${duration}ms")
                        return@async null
                    }
                    if (strictTvSource && !isTvSource(fetch.source)) {
                        val duration = Clock.System.now().toEpochMilliseconds() - startedAt
                        logDebug("SmcApiService", "MTF sweeps[$label/$tf] fail(strict_tv) source=${fetch.source} duration=${duration}ms")
                        throw StrictSourceMismatchException(
                            "Strict TV candle source violation: expected=TV:*, actual=${fetch.source}, symbol=${symbol.uppercase()}, tf=$tf"
                        )
                    }
                    if (candles.isEmpty()) {
                        val duration = Clock.System.now().toEpochMilliseconds() - startedAt
                        logDebug("SmcApiService", "MTF sweeps[$label/$tf] done(empty) source=${fetch.source} duration=${duration}ms")
                        return@async null
                    }
                    val sweeps = detectSweeps(
                        candles = candles,
                        timeframeLabel = label,
                        dataSource = fetch.source,
                        strictRecent = strictRecent,
                        maxBarsAgo = maxBarsAgo
                    )
                    val duration = Clock.System.now().toEpochMilliseconds() - startedAt
                    if (sweeps.isEmpty()) {
                        logDebug("SmcApiService", "MTF sweeps[$label/$tf] done(no_signal) bars=${candles.size} source=${fetch.source} duration=${duration}ms")
                        return@async null
                    }
                    logDebug("SmcApiService", "MTF sweeps[$label/$tf] done(signals=${sweeps.size}) bars=${candles.size} source=${fetch.source} duration=${duration}ms")
                    SmcMtfSweepFrame(
                        timeframe = label,
                        source = fetch.source,
                        barsCount = candles.size,
                        signals = sweeps
                    )
                }
            }.awaitAll().filterNotNull()
        }
        return results.sortedBy { tfOrder(it.timeframe) }
    }

    suspend fun getMTFSweeps(
        symbol: String,
        strictSource: Boolean = false,
        strictTvSource: Boolean = true
    ): Map<String, List<SmcSweepSignal>> {
        return getMTFSweepsDetailed(symbol = symbol, strictSource = strictSource, strictTvSource = strictTvSource)
            .associate { it.timeframe to it.signals }
    }

    /**
     * MTF Liquidity Levels — Equal Highs/Lows จากหลาย timeframe (M5, M15, M30, H1, H4)
     */
    suspend fun getMTFLiquidityDetailed(
        symbol: String, strictSource: Boolean = false, strictTvSource: Boolean = true
    ): List<SmcMtfLiquidityFrame> {
        val expectedSources=expectedSourceSet(symbol)
        val timeframes=linkedMapOf("M1" to "1m","M5" to "5m","M15" to "15m","M30" to "30m","H1" to "1h","H4" to "4h","D1" to "1d","W1" to "1w")
        return coroutineScope { timeframes.map { (label,tf) -> async {
            val fetch=fetchCandlesWithSource(symbol,tf,150); if(strictSource && fetch.source !in expectedSources) return@async null
            if(strictTvSource && !isTvSource(fetch.source)) throw StrictSourceMismatchException("Strict TV candle source violation: expected=TV:*, actual=${fetch.source}, symbol=${symbol.uppercase()}, tf=$tf")
            if(fetch.candles.isEmpty()) return@async null
            val zones=detectPineLiquidityZones(fetch.candles)
            SmcMtfLiquidityFrame(label,fetch.source,fetch.candles.size,zones)
        }}.awaitAll().filterNotNull().sortedBy{tfOrder(it.timeframe)} }
    }

    suspend fun getMTFLiquidity(
        symbol: String,
        strictSource: Boolean = false,
        strictTvSource: Boolean = true
    ): Map<String, List<SmcLiquidityZone>> {
        return getMTFLiquidityDetailed(symbol = symbol, strictSource = strictSource, strictTvSource = strictTvSource)
            .associate { it.timeframe to it.zones }
    }

    private fun tfOrder(tf: String): Int = when (tf.uppercase()) {
        "M1" -> 0; "M5" -> 1; "M15" -> 2; "M30" -> 3; "H1" -> 4; "H4" -> 5; "D1" -> 6; "W1" -> 7; else -> 99
    }

    /** Pine f_mergeLevels-style clustering with nearest-to-price cap semantics. */
    fun mergeMtfLiquidityWall(frames: List<SmcMtfLiquidityFrame>, currentPrice: Double, maxOutPerSide: Int = 30): List<SmcLiquidityZone> {
        fun merge(sideHigh: Boolean): List<SmcLiquidityZone> {
            val input=frames.flatMap{f->f.zones.filter{it.isHigh==sideHigh}.map{Triple(it.price,it.strength,f.timeframe)}}.sortedBy{it.first}
            val clusters=mutableListOf<MutableList<Triple<Double,Int,String>>>(); var anchor=Double.NaN
            for(c in input){ val tol=c.first*(if(c.third=="D1"||c.third=="W1")0.05 else 0.02)/100.0; if(clusters.isEmpty()||abs(c.first-anchor)>tol){clusters.add(mutableListOf(c));anchor=c.first}else clusters.last().add(c)}
            val out=clusters.map{cl->SmcLiquidityZone(cl.map{it.first}.average(),sideHigh,cl.maxOf{it.second},cl.map{it.third}.distinct().size.coerceIn(0,5))}.toMutableList()
            while(out.size>maxOutPerSide){ val idx=out.indices.maxByOrNull{abs(out[it].price-currentPrice)}?:break;out.removeAt(idx) }; return out
        }
        return (merge(true).filter{it.price>currentPrice}+merge(false).filter{it.price<currentPrice}).sortedBy{it.price}
    }
}

// ─── Helper ───────────────────────────────────────────────────────────────────

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

fun formatPrice(price: Double): String =
    if (price >= 1000) "%.2f".format(price)
    else if (price >= 1) "%.4f".format(price)
    else "%.6f".format(price)

fun starsStr(score: Int): String = "★".repeat(score.coerceIn(0, 5))

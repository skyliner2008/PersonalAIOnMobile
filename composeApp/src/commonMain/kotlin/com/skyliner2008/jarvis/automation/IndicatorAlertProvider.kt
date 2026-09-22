package com.skyliner2008.jarvis.automation

import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.SmcApiService
import com.skyliner2008.jarvis.tools.trading.TaIndicators

/**
 * IndicatorAlertProvider — คำนวณอินดิเคเตอร์จากแท่งเทียนจริง
 *
 * เหตุผลที่มี tool นี้: TradingView scanner endpoint (/symbol) ให้ค่าจำกัด —
 * EMA/Stoch/CCI/AO/BB upper/lower/BB width คืน null หมด (พิสูจน์แล้ว 2026-08-04)
 * แต่เรามีแท่งเทียนครบจาก SmcApiService จึงคำนวณเองได้ทุกค่า แม่นกว่าและไม่โดน rate limit
 *
 * [หลักการ] ระบบคำนวณค่าจริงแล้วส่งให้ AI วิเคราะห์ — **ห้ามส่งค่าประมาณหรือค่า default**
 * ทุกอินดิเคเตอร์คำนวณผ่าน TaIndicators ซึ่งคืน null เมื่อแท่งไม่พอ
 * ค่าที่เป็น null จะไม่ถูกใส่ลง map เลย (AI เห็นว่า "ไม่มี" ไม่ใช่ "เป็นศูนย์")
 *
 * ก่อนหน้านี้ไฟล์นี้มีสำเนา EMA/RSI/SMA/Stoch/CCI/BB/ATR/ADX ของตัวเอง
 * ซึ่ง fail คนละแบบกับ TaIndicators (NaN vs ราคาปิด) — ตอนนี้เหลืออิมพลีเมนต์เดียว
 *
 * ใช้ผ่าน tool_name = "trading_indicators" ใน AlertJob
 * เลือก timeframe ได้ด้วย suffix @TF ในสัญลักษณ์ เช่น "XAUUSD@15m" (default 1h)
 */
class IndicatorAlertProvider(private val smcApi: SmcApiService) {

    companion object {
        val SUPPORTED_TF = setOf("1m", "5m", "15m", "30m", "1h", "4h", "1D", "1W")

        fun normalizeTf(tf: String): String = TaIndicators.normalizeTimeframe(tf)

        /** แยก "XAUUSD@15m" หรือ "XAUUSD@H4" → ("XAUUSD", "4h"); ไม่มี suffix → default "1h" */
        fun splitSymbolAndTf(raw: String): Pair<String, String> {
            val parts = raw.split("@")
            val sym = parts[0].uppercase().trim()
            val tf = parts.getOrNull(1)?.let { normalizeTf(it) } ?: "1h"
            return sym to tf
        }

        /**
         * รายชื่อ field ทั้งหมดที่ provider นี้ "สามารถ" ส่งออกได้
         *
         * เป็น single source of truth — tool description ที่ส่งให้ AI และตัว validator
         * ของ alert ต้องอ้างรายการนี้ ไม่ใช่เขียนมือแยกกัน
         * (เดิม tool definition โฆษณา adx14 / resistance1 / donchian_upper / obv / mfi14 / ichimoku_*
         *  ซึ่ง provider ไม่เคยส่งออกเลย → AutomationEvaluator หา field ไม่เจอแล้ว `return false`
         *  เงียบๆ ผู้ใช้ตั้ง alert แล้วรอตลอดชาติโดยไม่มีใครบอก)
         */
        val SUPPORTED_FIELDS: Set<String> = linkedSetOf(
            // context
            "symbol", "timeframe", "source", "bars_used", "close", "open", "high", "low", "volume",
            // moving averages
            "ema7", "ema9", "ema14", "ema20", "ema21", "ema50", "ema100", "ema200",
            "sma20", "sma50", "sma100", "sma200",
            "ema_cross_state", "ema50_200_spread", "ema20_50_spread",
            // momentum
            "rsi7", "rsi14", "rsi21", "rsi14_prev",
            "macd", "macd_signal", "macd_hist",
            "stoch_k", "stoch_d", "cci20", "ao", "roc", "williams_r", "mfi14",
            // trend / volatility
            "adx14", "di_plus", "di_minus",
            "atr7", "atr14", "atr21", "atr_pct",
            "bb_upper", "bb_basis", "bb_lower", "bb_width", "bb_percent_b",
            "supertrend", "supertrend_direction",
            // volume
            "vwap", "vwap_distance_pct", "volume_sma20", "volume_ratio20", "obv", "obv_slope20",
            // levels
            "pivot", "resistance1", "resistance2", "resistance3",
            "support1", "support2", "support3",
            "donchian_upper", "donchian_mid", "donchian_lower",
            // ichimoku
            "ichimoku_tenkan", "ichimoku_kijun", "ichimoku_cloud_top", "ichimoku_cloud_bottom",
            "ichimoku_chikou",
            // fibonacci (จาก swing ของหน้าต่างที่ดึงมา)
            "fib_236", "fib_382", "fib_500", "fib_618", "fib_786",
            "swing_high", "swing_low"
        )

        /** จำนวนแท่งที่ขอจากแหล่งข้อมูล — ครอบคลุม warm-up ของ EMA200 */
        private const val REQUEST_BARS = TaIndicators.Warmup.FULL_SET

        /** ต่ำกว่านี้ถือว่าไม่พอจะคำนวณอะไรที่เชื่อถือได้เลย */
        private const val ABSOLUTE_MIN_BARS = 60

        /** หน้าต่างสำหรับหา swing high/low ที่ใช้เป็นฐานของ Fibonacci */
        private const val SWING_LOOKBACK = 100
    }

    suspend fun fetch(rawSymbol: String): Map<String, String> {
        val (symbol, tf) = splitSymbolAndTf(rawSymbol)

        // ใช้เส้นทางเดียวกับ tool อื่น (DB-backed, source-aware) แทนการยิง network ตรงทุกครั้ง
        val result = smcApi.fetchCandlesWithSource(symbol, tf, REQUEST_BARS)
        // คำนวณจากแท่งที่ปิดแล้วเท่านั้น — แท่งที่ยังก่อตัวทำให้ค่าขยับทุกครั้งที่เรียก (repainting)
        val candles = result.candles.filter { it.isClosed }

        if (candles.size < ABSOLUTE_MIN_BARS) {
            return mapOf(
                "error" to "แท่งเทียนไม่พอ: ${candles.size} แท่ง (ต้องการอย่างน้อย $ABSOLUTE_MIN_BARS) จาก ${result.source}",
                "symbol" to symbol,
                "timeframe" to tf,
                "source" to result.source,
                "bars_used" to candles.size.toString()
            )
        }

        logDebug("IndicatorAlertProvider", "$symbol@$tf: ${candles.size} แท่งปิด (${result.source})")
        return computeIndicators(symbol, tf, result.source, candles)
    }

    /**
     * คำนวณอินดิเคเตอร์ทั้งชุดจากแท่งที่ปิดแล้ว
     *
     * แยกออกจาก [fetch] เพื่อให้เทสต์ป้อนแท่งเองได้โดยไม่ต้องแตะเน็ตเวิร์ก
     */
    internal fun computeIndicators(
        symbol: String,
        tf: String,
        source: String,
        candles: List<Candle>
    ): Map<String, String> {
        val closes = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val volumes = candles.map { it.volume.coerceAtLeast(0.0) }
        val last = candles.last()

        val out = LinkedHashMap<String, String>()

        fun put(key: String, value: Double?) {
            if (value != null && value.isFinite()) out[key] = fmt(value)
        }

        // ── context ────────────────────────────────────────────────────────
        out["symbol"] = symbol
        out["timeframe"] = tf
        out["source"] = source
        out["bars_used"] = candles.size.toString()
        put("close", last.close)
        put("open", last.open)
        put("high", last.high)
        put("low", last.low)
        put("volume", last.volume)

        // ── moving averages ────────────────────────────────────────────────
        val ema50 = TaIndicators.ema(closes, 50)
        val ema200 = TaIndicators.ema(closes, 200)
        val ema20 = TaIndicators.ema(closes, 20)
        listOf(
            "ema7" to 7, "ema9" to 9, "ema14" to 14, "ema20" to 20, "ema21" to 21,
            "ema50" to 50, "ema100" to 100, "ema200" to 200
        ).forEach { (key, period) -> put(key, TaIndicators.ema(closes, period)) }
        listOf("sma20" to 20, "sma50" to 50, "sma100" to 100, "sma200" to 200)
            .forEach { (key, period) -> put(key, TaIndicators.sma(closes, period)) }

        // spread เป็น % ของราคา — เทียบข้าม symbol ได้
        if (ema50 != null && ema200 != null && last.close != 0.0) {
            put("ema50_200_spread", (ema50 - ema200) / last.close * 100.0)
        }
        if (ema20 != null && ema50 != null && last.close != 0.0) {
            put("ema20_50_spread", (ema20 - ema50) / last.close * 100.0)
        }
        out["ema_cross_state"] = crossState(closes, ema50, ema200)

        // ── momentum ───────────────────────────────────────────────────────
        put("rsi7", TaIndicators.rsi(closes, 7))
        put("rsi14", TaIndicators.rsi(closes, 14))
        put("rsi21", TaIndicators.rsi(closes, 21))
        put("rsi14_prev", TaIndicators.rsi(closes.dropLast(1), 14))

        TaIndicators.macd(closes)?.let {
            put("macd", it.macd); put("macd_signal", it.signal); put("macd_hist", it.hist)
        }
        TaIndicators.stochastic(closes, highs, lows)?.let {
            put("stoch_k", it.k); put("stoch_d", it.d)
        }
        put("cci20", TaIndicators.cci(highs, lows, closes, 20))
        put("roc", TaIndicators.roc(closes, 9))
        put("williams_r", TaIndicators.williamsR(highs, lows, closes, 14))
        put("mfi14", TaIndicators.mfi(highs, lows, closes, volumes, 14))

        // Awesome Oscillator = SMA5 - SMA34 ของ median price
        val medians = candles.map { (it.high + it.low) / 2.0 }
        val aoFast = TaIndicators.sma(medians, 5)
        val aoSlow = TaIndicators.sma(medians, 34)
        if (aoFast != null && aoSlow != null) put("ao", aoFast - aoSlow)

        // ── trend / volatility ─────────────────────────────────────────────
        TaIndicators.adx(highs, lows, closes, 14)?.let {
            put("adx14", it.adx); put("di_plus", it.diPlus); put("di_minus", it.diMinus)
        }
        val atr14 = TaIndicators.atr(highs, lows, closes, 14)
        put("atr7", TaIndicators.atr(highs, lows, closes, 7))
        put("atr14", atr14)
        put("atr21", TaIndicators.atr(highs, lows, closes, 21))
        if (atr14 != null && last.close != 0.0) put("atr_pct", atr14 / last.close * 100.0)

        TaIndicators.bollingerBands(closes, 20, 2.0)?.let {
            put("bb_basis", it.basis); put("bb_upper", it.upper); put("bb_lower", it.lower)
            put("bb_width", it.width); put("bb_percent_b", it.percentB)
        }
        TaIndicators.supertrend(highs, lows, closes)?.let {
            put("supertrend", it.value)
            out["supertrend_direction"] = if (it.isBullish) "BULLISH" else "BEARISH"
        }

        // ── volume ─────────────────────────────────────────────────────────
        // VWAP ผูก anchor รายเซสชันตามนิยาม — ไม่ใช่ค่าสะสมทั้งหน้าต่าง
        // FX/ทองรีเซ็ตที่ 22:00 UTC (ซิดนีย์เปิด) ส่วนคริปโตใช้ขอบวัน UTC
        val vwap = TaIndicators.vwapSessionFor(candles, symbol)
        put("vwap", vwap)
        if (vwap != null && last.close != 0.0) {
            put("vwap_distance_pct", (last.close - vwap) / last.close * 100.0)
        }
        val volSma20 = TaIndicators.sma(volumes, 20)
        put("volume_sma20", volSma20)
        if (volSma20 != null && volSma20 > 0.0) put("volume_ratio20", volumes.last() / volSma20)
        put("obv", TaIndicators.obv(closes, volumes))
        put("obv_slope20", TaIndicators.obvSlope(closes, volumes, 20))

        // ── levels ─────────────────────────────────────────────────────────
        val prior = candles[candles.lastIndex - 1]
        val pivots = TaIndicators.pivotPoints(prior.high, prior.low, prior.close)
        put("pivot", pivots.pivot)
        put("resistance1", pivots.r1); put("resistance2", pivots.r2); put("resistance3", pivots.r3)
        put("support1", pivots.s1); put("support2", pivots.s2); put("support3", pivots.s3)

        TaIndicators.donchian(highs, lows, 20)?.let {
            put("donchian_upper", it.upper); put("donchian_mid", it.mid); put("donchian_lower", it.lower)
        }

        // ── ichimoku ───────────────────────────────────────────────────────
        TaIndicators.ichimoku(highs, lows, closes)?.let {
            put("ichimoku_tenkan", it.tenkan); put("ichimoku_kijun", it.kijun)
            put("ichimoku_cloud_top", it.cloudTop); put("ichimoku_cloud_bottom", it.cloudBottom)
            put("ichimoku_chikou", it.chikou)
        }

        // ── fibonacci จาก swing ล่าสุด ─────────────────────────────────────
        val lookback = minOf(SWING_LOOKBACK, candles.size)
        val swingHigh = highs.takeLast(lookback).max()
        val swingLow = lows.takeLast(lookback).min()
        put("swing_high", swingHigh)
        put("swing_low", swingLow)
        TaIndicators.fibonacciLevels(swingHigh, swingLow)?.let { levels ->
            listOf("fib_236", "fib_382", "fib_500", "fib_618", "fib_786").forEach { key ->
                put(key, levels[key])
            }
        }

        return out
    }

    /**
     * สถานะการตัดกันของ EMA50/EMA200
     * คืน "N/A" เมื่อแท่งไม่พอคำนวณ EMA200 — ไม่เดาว่าเป็น BULLISH/BEARISH
     */
    private fun crossState(closes: List<Double>, ema50: Double?, ema200: Double?): String {
        if (ema50 == null || ema200 == null) return "N/A"
        val prevCloses = closes.dropLast(1)
        val prev50 = TaIndicators.ema(prevCloses, 50)
        val prev200 = TaIndicators.ema(prevCloses, 200)
        return when {
            prev50 != null && prev200 != null && prev50 <= prev200 && ema50 > ema200 -> "GOLDEN_CROSS"
            prev50 != null && prev200 != null && prev50 >= prev200 && ema50 < ema200 -> "DEATH_CROSS"
            ema50 > ema200 -> "BULLISH"
            else -> "BEARISH"
        }
    }

    private fun fmt(v: Double): String = "%.4f".format(v).trimEnd('0').trimEnd('.')
}
